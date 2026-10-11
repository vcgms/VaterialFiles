package dev.vcgms.aapp.vaterialfiles.viewer.video

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.RecyclerView
import dev.chrisbanes.insetter.applySystemWindowInsetsToPadding
import java8.nio.file.Path
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.parcelize.Parcelize
import `is`.xyz.mpv.MPV
import `is`.xyz.mpv.MPVNode
import dev.vcgms.aapp.vaterialfiles.R
import dev.vcgms.aapp.vaterialfiles.databinding.VideoViewerFragmentBinding
import dev.vcgms.aapp.vaterialfiles.filelist.name
import dev.vcgms.aapp.vaterialfiles.util.ParcelableArgs
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

    private var speedIndex = 0

    private val speeds = doubleArrayOf(1.0, 1.25, 1.5, 2.0, 0.5, 0.75)

    private var hwdec = "mediacodec-copy"

    private var isUserSeeking = false

    // A seek position to restore after the file reload that a hwdec switch triggers.
    private var pendingSeekSeconds: Double? = null

    private val hideControlsRunnable = Runnable { setControlsVisible(false) }

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
        binding.tapTarget.setOnClickListener { toggleControls() }
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
        binding.speedButton.setOnClickListener {
            speedIndex = (speedIndex + 1) % speeds.size
            val speed = speeds[speedIndex]
            MpvManager.mpv.setPropertyString("speed", speed.toString())
            binding.speedButton.text = formatSpeed(speed)
        }
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
        val directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "VaterialFiles"
        )
        directory.mkdirs()
        val fileName = "Screenshot_" + SimpleDateFormat(
            "yyyyMMdd_HHmmss", Locale.US
        ).format(Date()) + ".png"
        val file = File(directory, fileName)
        MpvManager.mpv.command("screenshot-to-file", file.absolutePath)
        showToast(getString(R.string.video_viewer_screenshot_saved, file.absolutePath))
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

    @Parcelize
    class Args(val intent: Intent, val position: Int) : ParcelableArgs

    companion object {
        private const val STATE_POSITION = "VideoViewerFragment.position"
        private const val PROGRESS_MAX = 1000
        private const val CONTROLS_HIDE_DELAY_MILLIS = 3000L

        private const val MPV_FORMAT_FLAG = 3
        private const val MPV_EVENT_FILE_LOADED = 8
        private const val PROGRESS_POLL_INTERVAL_MILLIS = 250L
    }
}
