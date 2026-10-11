package dev.vcgms.aapp.vaterialfiles.viewer.video

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.SeekBar
import androidx.appcompat.widget.ListPopupWindow
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import dev.chrisbanes.insetter.applySystemWindowInsetsToPadding
import java8.nio.file.Path
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.parcelize.Parcelize
import `is`.xyz.mpv.MPV
import `is`.xyz.mpv.MPVNode
import dev.vcgms.aapp.vaterialfiles.R
import dev.vcgms.aapp.vaterialfiles.databinding.VideoViewerFragmentBinding
import dev.vcgms.aapp.vaterialfiles.filelist.name
import dev.vcgms.aapp.vaterialfiles.provider.linux.isLinuxPath
import dev.vcgms.aapp.vaterialfiles.util.ParcelableArgs
import dev.vcgms.aapp.vaterialfiles.util.applyDescriptionAsTooltip
import dev.vcgms.aapp.vaterialfiles.util.args
import dev.vcgms.aapp.vaterialfiles.util.extraPathList
import dev.vcgms.aapp.vaterialfiles.util.finish
import dev.vcgms.aapp.vaterialfiles.util.showToast

class VideoViewerFragment : Fragment() {
    private val args by args<Args>()

    private lateinit var paths: List<Path>

    private var position: Int = 0

    private lateinit var binding: VideoViewerFragmentBinding

    private val mainHandler = Handler(Looper.getMainLooper())

    private var isPaused = true

    private var duration = 0.0

    private var timePos = 0.0

    // Ascending so the speed menu reads naturally; the default is 1x.
    private val speeds = doubleArrayOf(0.5, 0.75, 1.0, 1.25, 1.5, 2.0)

    private var speedIndex = DEFAULT_SPEED_INDEX

    private var hwdec = "mediacodec-copy"

    private var isUserSeeking = false

    // A seek position to restore after the file reload that a hwdec switch triggers.
    private var pendingSeekSeconds: Double? = null

    private lateinit var gestureDetector: GestureDetector

    private lateinit var audioManager: AudioManager

    private var touchSlop = 0

    private var gestureMode = GestureMode.NONE

    private var gestureStartY = 0f

    private var gestureStartBrightness = 0f

    private var gestureStartVolumeIndex = 0

    private var gestureStartTimePos = 0.0

    private var gestureSeekTarget = -1.0

    private val hideControlsRunnable = Runnable { setControlsVisible(false) }

    private val hideHudRunnable = Runnable { binding.gestureHud.isVisible = false }

    private val clearUserSeekingRunnable = Runnable {
        isUserSeeking = false
        updateProgress()
    }

    // Polls time-pos/duration at a low frequency instead of observing them (per-frame property
    // change callbacks are the main source of playback stutter); the UI is only updated while
    // the controls are visible.
    private val progressPollRunnable = object : Runnable {
        override fun run() {
            if (!isUserSeeking) {
                MpvManager.mpv.getPropertyDouble("time-pos")?.let { timePos = it }
                MpvManager.mpv.getPropertyDouble("duration")?.let { duration = it }
                if (binding.topBar.isVisible) {
                    updateProgress()
                }
            }
            mainHandler.postDelayed(this, PROGRESS_POLL_INTERVAL_MILLIS)
        }
    }

    private val eventObserver = object : MPV.EventObserver {
        override fun eventProperty(property: String) {}

        override fun eventProperty(property: String, value: Long) {}

        override fun eventProperty(property: String, value: Boolean) {
            mainHandler.post {
                when (property) {
                    "pause" -> {
                        isPaused = value
                        updatePlayPauseIcon()
                        if (!value) {
                            scheduleHideControls()
                        }
                    }
                    "eof-reached" -> {
                        if (value) {
                            playNext()
                        }
                    }
                }
            }
        }

        override fun eventProperty(property: String, value: String) {}

        override fun eventProperty(property: String, value: Double) {
            // time-pos / duration are polled instead of observed: they change on every frame
            // and observing them floods the main thread with JNI callbacks and UI updates,
            // which noticeably stutters playback.
        }

        override fun eventProperty(property: String, value: MPVNode) {}

        override fun event(eventId: Int, node: MPVNode) {
            mainHandler.post {
                when (eventId) {
                    MPV_EVENT_FILE_LOADED -> onFileLoaded()
                    // Do NOT switch to the next file on END_FILE here: replacing a file with
                    // loadfile also emits END_FILE (reason=stop/redirect), which previously
                    // chained into endless "play next" while toggling hwdec. Automatic
                    // advance is driven solely by the eof-reached property, which only fires
                    // when the file really plays to its end.
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        paths = args.intent.extraPathList
        position = savedInstanceState?.getInt(STATE_POSITION) ?: args.position
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View =
        VideoViewerFragmentBinding.inflate(inflater, container, false)
            .also { binding = it }
            .root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // vo/hwdec options are set once in MpvManager before init(); they are ignored after
        // mpv initialization, so they must not be set here.
        MpvManager.mpv.observeProperty("pause", MPV_FORMAT_FLAG)
        MpvManager.mpv.observeProperty("eof-reached", MPV_FORMAT_FLAG)
        MpvManager.mpv.addObserver(eventObserver)

        binding.backButton.setOnClickListener { requireActivity().finish() }
        // The icon-only control buttons reveal their function on long press, like toolbar actions.
        binding.root.applyDescriptionAsTooltip()
        // The whole video area is a gesture surface: single tap toggles the controls, double tap
        // pauses/resumes, vertical drags adjust brightness (left half) or volume (right half) and
        // horizontal drags scrub the playback position.
        touchSlop = ViewConfiguration.get(requireContext()).scaledTouchSlop
        gestureDetector = GestureDetector(requireContext(), gestureListener)
        audioManager =
            requireContext().getSystemService(Context.AUDIO_SERVICE) as AudioManager
        @Suppress("ClickableViewAccessibility")
        binding.tapTarget.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> onGestureEnd()
            }
            true
        }
        // The immersive theme leaves the window insets for us; keep the top bar clear of the
        // status bar and the bottom controls clear of the navigation bar.
        binding.topBar.applySystemWindowInsetsToPadding(top = true)
        binding.bottomControls.applySystemWindowInsetsToPadding(bottom = true)
        binding.playPauseButton.setOnClickListener {
            MpvManager.mpv.setPropertyBoolean("pause", !isPaused)
        }
        binding.prevButton.setOnClickListener { playPrevious() }
        binding.nextButton.setOnClickListener { playNext() }
        binding.stepBackButton.setOnClickListener { stepFrame(forward = false) }
        binding.stepForwardButton.setOnClickListener { stepFrame(forward = true) }
        binding.screenshotButton.setOnClickListener { takeScreenshot() }
        binding.speedButton.setOnClickListener { showSpeedMenu(it) }
        binding.hwdecButton.setOnClickListener {
            hwdec = if (hwdec == "mediacodec-copy") "no" else "mediacodec-copy"
            MpvManager.mpv.setPropertyString("hwdec", hwdec)
            binding.hwdecButton.text = if (hwdec == "mediacodec-copy") "HW" else "SW"
            // hwdec only takes effect when the file is (re)loaded.
            pendingSeekSeconds = timePos
            reloadCurrentFile()
        }
        binding.progressBar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        isUserSeeking = true
                        val seconds = progress / PROGRESS_MAX.toDouble() * duration
                        binding.timeText.text =
                            "${formatTime(seconds)} / ${formatTime(duration)}"
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) {
                    isUserSeeking = true
                }

                override fun onStopTrackingTouch(seekBar: SeekBar) {
                    val seconds = seekBar.progress / PROGRESS_MAX.toDouble() * duration
                    MpvManager.mpv.command("seek", seconds.toString(), "absolute")
                    // The seek is asynchronous: keep ignoring time-pos updates until the new
                    // position has arrived, otherwise the bar jumps back to the old position
                    // and then forward again.
                    mainHandler.removeCallbacks(clearUserSeekingRunnable)
                    mainHandler.postDelayed(clearUserSeekingRunnable, 500)
                }
            }
        )
        binding.speedButton.text = formatSpeed(speeds[speedIndex])
        binding.hwdecButton.text = if (hwdec == "mediacodec-copy") "HW" else "SW"
        updatePlayPauseIcon()
        // The first loadfile must wait until the surface exists, otherwise the video output
        // initializes without a surface and shows a black screen until the file is reloaded.
        var hasLoadedInitialFile = false
        val loadInitialFile = {
            if (!hasLoadedInitialFile) {
                hasLoadedInitialFile = true
                loadFile(position)
            }
        }
        binding.mpvVideoView.onSurfaceReady = { loadInitialFile() }
        if (binding.mpvVideoView.isSurfaceReady) {
            loadInitialFile()
        }
        mainHandler.post(progressPollRunnable)
    }

    override fun onPause() {
        super.onPause()
        mainHandler.removeCallbacks(hideControlsRunnable)
        mainHandler.removeCallbacks(clearUserSeekingRunnable)
        mainHandler.removeCallbacks(progressPollRunnable)
        mainHandler.removeCallbacks(hideHudRunnable)
        MpvManager.mpv.setPropertyBoolean("pause", true)
    }

    override fun onResume() {
        super.onResume()
        mainHandler.removeCallbacks(progressPollRunnable)
        mainHandler.post(progressPollRunnable)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mainHandler.removeCallbacks(hideControlsRunnable)
        mainHandler.removeCallbacks(clearUserSeekingRunnable)
        mainHandler.removeCallbacks(progressPollRunnable)
        mainHandler.removeCallbacks(hideHudRunnable)
        MpvManager.mpv.removeObserver(eventObserver)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_POSITION, position)
    }

    private fun loadFile(index: Int) {
        val path = paths.getOrNull(index) ?: return
        position = index
        binding.titleText.text = path.name
        isPaused = false
        updatePlayPauseIcon()
        MpvManager.applyColorOptions(isHdrSource(path))
        MpvManager.mpv.command("loadfile", path.toString())
    }

    private fun reloadCurrentFile() {
        val path = paths.getOrNull(position) ?: return
        isPaused = false
        updatePlayPauseIcon()
        MpvManager.mpv.command("loadfile", path.toString())
    }

    private fun onFileLoaded() {
        val seek = pendingSeekSeconds
        pendingSeekSeconds = null
        if (seek != null && seek > 0) {
            MpvManager.mpv.command("seek", seek.toString(), "absolute")
        }
        scheduleHideControls()
    }

    private fun playPrevious() {
        if (position > 0) {
            loadFile(position - 1)
        } else {
            showToast(R.string.video_viewer_no_previous)
        }
    }

    private fun playNext() {
        if (position < paths.size - 1) {
            loadFile(position + 1)
        } else {
            setControlsVisible(true)
            mainHandler.removeCallbacks(hideControlsRunnable)
        }
    }

    private fun stepFrame(forward: Boolean) {
        if (!isPaused) {
            MpvManager.mpv.setPropertyBoolean("pause", true)
        }
        MpvManager.mpv.command(if (forward) "frame-step" else "frame-back-step")
    }

    private fun takeScreenshot() {
        val path = paths[position]
        val fileName = path.name.substringBeforeLast('.') + "_" + formatTimestamp(timePos) + ".png"
        // Prefer the video's own directory; fall back to the public pictures directory when the
        // source is not a plain file path or its directory is not writable.
        val sourceDirectory = if (path.isLinuxPath) path.parent?.toFile() else null
        val directory = if (sourceDirectory != null && sourceDirectory.canWrite()) {
            sourceDirectory
        } else {
            File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "VaterialFiles"
            )
        }
        directory.mkdirs()
        val file = File(directory, fileName)
        MpvManager.mpv.command("screenshot-to-file", file.absolutePath)
        showToast(getString(R.string.video_viewer_screenshot_saved, file.absolutePath))
    }

    // The playback position rendered into a file-name safe HH-MM-SS (or MM-SS under one hour).
    private fun formatTimestamp(seconds: Double): String {
        val total = seconds.roundToInt()
        val hours = total / 3600
        val minutes = total % 3600 / 60
        val rest = total % 60
        return if (hours > 0) {
            String.format(Locale.US, "%02d-%02d-%02d", hours, minutes, rest)
        } else {
            String.format(Locale.US, "%02d-%02d", minutes, rest)
        }
    }

    // Whether the file carries HDR transfer characteristics (PQ/HLG) or a Dolby Vision track, in
    // which case mpv must tone map it down for the SDR surface; SDR files keep mpv defaults.
    private fun isHdrSource(path: Path): Boolean {
        if (!path.isLinuxPath) {
            return false
        }
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(path.toFile().absolutePath)
            val transfer = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_COLOR_TRANSFER
            )?.toIntOrNull()
            val mime = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            transfer == MediaFormat.COLOR_TRANSFER_ST2084
                || transfer == MediaFormat.COLOR_TRANSFER_HLG
                || mime?.contains("dolby", ignoreCase = true) == true
        } catch (e: Exception) {
            false
        } finally {
            retriever.release()
        }
    }

    private fun toggleControls() {
        setControlsVisible(!binding.topBar.isVisible)
    }

    private fun setControlsVisible(visible: Boolean) {
        binding.topBar.isVisible = visible
        binding.bottomControls.isVisible = visible
        if (visible && !isPaused) {
            scheduleHideControls()
        }
    }

    private fun scheduleHideControls() {
        mainHandler.removeCallbacks(hideControlsRunnable)
        mainHandler.postDelayed(hideControlsRunnable, CONTROLS_HIDE_DELAY_MILLIS)
    }

    private fun updatePlayPauseIcon() {
        binding.playPauseButton.setImageResource(
            if (isPaused) {
                R.drawable.play_arrow_icon_control_normal_24dp
            } else {
                R.drawable.pause_icon_control_normal_24dp
            }
        )
    }

    private fun updateProgress() {
        binding.progressBar.progress =
            if (duration > 0) (timePos / duration * PROGRESS_MAX).toInt() else 0
        binding.timeText.text = "${formatTime(timePos)} / ${formatTime(duration)}"
    }

    // Shows the playback speed picker as a popup menu anchored to the speed button instead of
    // cycling through the values on every tap.
    private fun showSpeedMenu(anchor: View) {
        val popup = ListPopupWindow(requireContext())
        popup.setAnchorView(anchor)
        // A narrow, text-only list. The current speed is already shown on the button, so the radio
        // column the old PopupMenu reserved (the main source of its width) is dropped.
        popup.width = (SPEED_MENU_WIDTH_DP * resources.displayMetrics.density).roundToInt()
        popup.isModal = true
        popup.setAdapter(
            ArrayAdapter(
                requireContext(), android.R.layout.simple_list_item_1,
                speeds.map { formatSpeed(it) }
            )
        )
        popup.setOnItemClickListener { _, _, position, _ ->
            speedIndex = position
            val speed = speeds[position]
            MpvManager.mpv.setPropertyString("speed", speed.toString())
            binding.speedButton.text = formatSpeed(speed)
            popup.dismiss()
        }
        popup.show()
    }

    private val gestureListener = object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            gestureMode = GestureMode.NONE
            gestureStartY = e.y
            gestureStartBrightness = currentScreenBrightness()
            gestureStartVolumeIndex = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            gestureStartTimePos = timePos
            gestureSeekTarget = -1.0
            return true
        }

        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            toggleControls()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            MpvManager.mpv.setPropertyBoolean("pause", !isPaused)
            return true
        }

        override fun onScroll(
            e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float
        ): Boolean {
            if (e1 == null) {
                return false
            }
            val width = binding.tapTarget.width.coerceAtLeast(1)
            val height = binding.tapTarget.height.coerceAtLeast(1)
            val dx = e2.x - e1.x
            val dy = e2.y - e1.y
            if (gestureMode == GestureMode.NONE) {
                gestureMode = when {
                    abs(dy) > abs(dx) && abs(dy) > touchSlop ->
                        if (e1.x < width / 2f) GestureMode.BRIGHTNESS else GestureMode.VOLUME
                    abs(dx) > abs(dy) && abs(dx) > touchSlop -> {
                        // Stop the progress poll from fighting the scrub preview.
                        isUserSeeking = true
                        GestureMode.SEEK
                    }
                    else -> GestureMode.NONE
                }
            }
            when (gestureMode) {
                GestureMode.BRIGHTNESS -> {
                    val brightness = (gestureStartBrightness + (gestureStartY - e2.y) / height)
                        .coerceIn(0.01f, 1f)
                    setScreenBrightness(brightness)
                    showValueHud(
                        R.drawable.brightness_icon_white_24dp,
                        (brightness * 100).roundToInt(), brightness
                    )
                }
                GestureMode.VOLUME -> {
                    // Adjust the system media volume; the player volume stays pinned at 100 so the
                    // two never compound.
                    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    val target = (gestureStartVolumeIndex +
                            (gestureStartY - e2.y) / height * maxVolume)
                        .roundToInt().coerceIn(0, maxVolume)
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
                    val fraction = if (maxVolume > 0) target.toFloat() / maxVolume else 0f
                    showValueHud(
                        R.drawable.volume_up_icon_white_24dp,
                        (fraction * 100).roundToInt(), fraction
                    )
                }
                GestureMode.SEEK -> {
                    if (duration > 0) {
                        val target = (gestureStartTimePos + dx / width * duration)
                            .coerceIn(0.0, duration)
                        gestureSeekTarget = target
                        showSeekHud(target)
                        binding.progressBar.progress = (target / duration * PROGRESS_MAX).toInt()
                        binding.timeText.text =
                            "${formatTime(target)} / ${formatTime(duration)}"
                    }
                }
                GestureMode.NONE -> Unit
            }
            return true
        }
    }

    private fun onGestureEnd() {
        if (gestureMode == GestureMode.SEEK && gestureSeekTarget >= 0) {
            MpvManager.mpv.command("seek", gestureSeekTarget.toString(), "absolute")
            // The seek is asynchronous; keep ignoring time-pos until the new position arrives.
            mainHandler.removeCallbacks(clearUserSeekingRunnable)
            mainHandler.postDelayed(clearUserSeekingRunnable, 500)
        }
        gestureMode = GestureMode.NONE
        hideHudDelayed()
    }

    private fun showValueHud(iconRes: Int, percent: Int, fraction: Float) {
        mainHandler.removeCallbacks(hideHudRunnable)
        binding.gestureIcon.isVisible = true
        binding.gestureProgress.isVisible = true
        binding.gestureIcon.setImageResource(iconRes)
        binding.gestureProgress.progress = (fraction * 100).roundToInt()
        binding.gestureText.text = "$percent%"
        binding.gestureHud.isVisible = true
    }

    private fun showSeekHud(target: Double) {
        mainHandler.removeCallbacks(hideHudRunnable)
        binding.gestureIcon.isVisible = false
        binding.gestureProgress.isVisible = false
        binding.gestureText.text = "${formatTime(target)} / ${formatTime(duration)}"
        binding.gestureHud.isVisible = true
    }

    private fun hideHudDelayed() {
        mainHandler.removeCallbacks(hideHudRunnable)
        mainHandler.postDelayed(hideHudRunnable, HUD_HIDE_DELAY_MILLIS)
    }

    // The per-window brightness override, or the system brightness while the window still
    // follows the system setting (screenBrightness < 0), so the first drag starts smoothly.
    private fun currentScreenBrightness(): Float {
        val windowBrightness = requireActivity().window.attributes.screenBrightness
        if (windowBrightness >= 0f) {
            return windowBrightness
        }
        return try {
            Settings.System.getInt(
                requireContext().contentResolver, Settings.System.SCREEN_BRIGHTNESS
            ) / 255f
        } catch (e: Exception) {
            0.5f
        }
    }

    private fun setScreenBrightness(brightness: Float) {
        val window = requireActivity().window
        val attributes = window.attributes
        attributes.screenBrightness = brightness.coerceIn(0.01f, 1f)
        window.attributes = attributes
    }

    private fun formatSpeed(speed: Double): String {
        val text = if (speed == speed.toLong().toDouble()) {
            speed.toLong().toString()
        } else {
            speed.toString()
        }
        return "${text}x"
    }

    private fun formatTime(seconds: Double): String {
        val totalSeconds = seconds.toLong()
        val hours = totalSeconds / 3600
        val minutes = totalSeconds % 3600 / 60
        val secs = totalSeconds % 60
        return if (hours > 0) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, secs)
        }
    }

    private enum class GestureMode { NONE, BRIGHTNESS, VOLUME, SEEK }

    @Parcelize
    class Args(val intent: Intent, val position: Int) : ParcelableArgs

    companion object {
        private const val STATE_POSITION = "VideoViewerFragment.position"
        private const val PROGRESS_MAX = 1000
        private const val CONTROLS_HIDE_DELAY_MILLIS = 3000L
        private const val HUD_HIDE_DELAY_MILLIS = 800L
        private const val DEFAULT_SPEED_INDEX = 2
        private const val SPEED_MENU_WIDTH_DP = 80

        private const val MPV_FORMAT_FLAG = 3
        private const val MPV_EVENT_FILE_LOADED = 8
        private const val PROGRESS_POLL_INTERVAL_MILLIS = 250L
    }
}
