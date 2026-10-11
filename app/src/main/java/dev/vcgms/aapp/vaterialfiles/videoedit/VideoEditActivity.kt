package dev.vcgms.aapp.vaterialfiles.videoedit

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.Crop
import androidx.media3.exoplayer.ExoPlayer
import com.google.android.material.slider.Slider
import java8.nio.file.Path
import kotlin.math.abs
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.vcgms.aapp.vaterialfiles.R
import dev.vcgms.aapp.vaterialfiles.app.AppActivity
import dev.vcgms.aapp.vaterialfiles.databinding.VideoEditActivityBinding
import dev.vcgms.aapp.vaterialfiles.file.FileItem
import dev.vcgms.aapp.vaterialfiles.provider.linux.isLinuxPath
import dev.vcgms.aapp.vaterialfiles.util.layoutInflater
import dev.vcgms.aapp.vaterialfiles.util.setDataSource
import dev.vcgms.aapp.vaterialfiles.util.showToast
import dev.vcgms.aapp.vaterialfiles.videoinfo.VideoInfoLoader

// The video edit page: a preview on top (no built-in player controls) and two bottom pages —
// the Trim page with a scrollable timeline under a playhead fixed at the center (plus a
// scissors button splitting at the playhead), and the Adjust page with per-clip brightness,
// contrast and crop sliders. Adjusted parameters are previewed in real time through
// ExoPlayer.setVideoEffects. Undo/redo uses snapshot stacks of the clip list.
class VideoEditActivity : AppActivity() {
    private lateinit var binding: VideoEditActivityBinding

    private var file: FileItem? = null

    private val clips = mutableListOf<ClipEdit>()

    private var durationUs = 0L

    private var playheadUs = 0L

    private var selectedClipIndex = -1

    private var updatingSliders = false

    private var player: ExoPlayer? = null

    private var retriever: MediaMetadataRetriever? = null

    private var overlayJob: Job? = null

    private val undoStack = ArrayDeque<List<ClipEdit>>()

    private val redoStack = ArrayDeque<List<ClipEdit>>()

    private var menu: Menu? = null

    private var viewportWidth = 0

    private var isSyncingScroll = false

    private var isTouchingTimeline = false

    private var lastEffectClipIndex = -2

    private val handler = Handler(Looper.getMainLooper())

    private lateinit var playheadRunnable: Runnable

    private val seekRunnable = Runnable { seekPlayerToPlayhead() }

    private val effectsRunnable = Runnable { applyVideoEffects(selectedClipIndex) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = VideoEditActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = getString(R.string.video_edit_title)
            setDisplayHomeAsUpEnabled(true)
        }
        val file = intent.getParcelableExtra(EXTRA_FILE, FileItem::class.java)
        if (file == null) {
            finish()
            return
        }
        this.file = file
        if (!file.path.isLinuxPath) {
            showToast(R.string.video_transcode_unsupported_path)
            finish()
            return
        }
        val initialClips = intent.getParcelableArrayListExtra(EXTRA_CLIPS, ClipEdit::class.java)
        if (initialClips.isNullOrEmpty()) {
            clips.add(ClipEdit(0, DEFAULT_CLIP_DURATION_US))
        } else {
            clips.addAll(initialClips)
        }
        setUpPlayer(file.path)
        setUpTimeline()
        setUpBottomPages()
        bindSliders()
        binding.splitButton.setOnClickListener { splitAtPlayhead() }
        binding.deleteClipButton.setOnClickListener { deleteClip() }
        binding.addClipButton.setOnClickListener { addClip() }
        selectClip(-1)
        loadDuration(file.path)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        overlayJob?.cancel()
        retriever?.release()
        retriever = null
        player?.release()
        player = null
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.video_edit_menu, menu)
        this.menu = menu
        updateUndoRedoButtons()
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_undo -> {
            undo()
            true
        }
        R.id.action_redo -> {
            redo()
            true
        }
        R.id.action_export -> {
            export()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun setUpPlayer(path: Path) {
        val player = ExoPlayer.Builder(this).build()
        this.player = player
        binding.playerView.player = player
        binding.playerView.useController = false
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                binding.playPauseButton.isVisible = !isPlaying
                updatePreviewOverlay()
            }
        })
        val playPauseBackground = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0x66000000)
        }
        binding.playPauseButton.background = playPauseBackground
        binding.playPauseButton.setOnClickListener {
            if (player.isPlaying) {
                player.pause()
            } else {
                player.play()
            }
        }
        player.setMediaItems(buildMediaItems())
        player.prepare()
        retriever = MediaMetadataRetriever().apply { setDataSource(path) }
        playheadRunnable = object : Runnable {
            override fun run() {
                val totalDuration = timelineTotalDuration()
                if (!isTouchingTimeline && (
                    player.playbackState == Player.STATE_READY ||
                        player.playbackState == Player.STATE_ENDED
                    )
                ) {
                    playheadUs = playerPositionToPlayheadUs(
                        player.currentMediaItemIndex, player.currentPosition
                    ).coerceIn(0L, totalDuration)
                    syncScrollToPlayhead()
                    val effectIndex = clipIndexAtPlayhead()
                    if (effectIndex != lastEffectClipIndex) {
                        lastEffectClipIndex = effectIndex
                        applyVideoEffects(effectIndex)
                    }
                }
                handler.postDelayed(this, PLAYHEAD_POLL_MS)
            }
        }
        handler.post(playheadRunnable)
    }

    private fun setUpTimeline() {
        binding.timelineContainer.post {
            viewportWidth = binding.timelineContainer.width
            binding.timelineView.leadingPaddingPx = viewportWidth / 2
            binding.timelineView.clips = clips.toList()
            // The playhead line spans exactly the timeline height; a match_parent height in the
            // wrap_content FrameLayout would expand the whole chain to full screen and squeeze
            // the preview container to zero height.
            binding.timelineScroll.post {
                binding.playheadLine.layoutParams = binding.playheadLine.layoutParams.apply {
                    height = binding.timelineScroll.height
                }
            }
            syncScrollToPlayhead()
        }
        binding.timelineScroll.viewTreeObserver.addOnScrollChangedListener {
            if (isSyncingScroll) {
                return@addOnScrollChangedListener
            }
            playheadUs = binding.timelineView.usForContentX(
                binding.timelineScroll.scrollX + viewportWidth / 2f
            )
            player?.pause()
            handler.removeCallbacks(seekRunnable)
            handler.postDelayed(seekRunnable, SEEK_DEBOUNCE_MS)
        }
        var tapDownX = 0f
        var tapDownY = 0f
        var tapScrolled = false
        binding.timelineScroll.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    isTouchingTimeline = true
                    tapDownX = event.x
                    tapDownY = event.y
                    tapScrolled = false
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (abs(event.x - tapDownX) > 16f || abs(event.y - tapDownY) > 16f) {
                        tapScrolled = true
                    }
                    false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isTouchingTimeline = false
                    if (!tapScrolled && event.actionMasked == MotionEvent.ACTION_UP) {
                        val index = binding.timelineView.clipIndexAt(
                            binding.timelineScroll.scrollX + event.x, event.y
                        )
                        if (index != -1) {
                            selectClip(index)
                            true
                        } else {
                            false
                        }
                    } else {
                        false
                    }
                }
                else -> false
            }
        }
    }

    private fun setUpBottomPages() {
        binding.tabTrim.isChecked = true
        binding.pageTabs.setOnCheckedStateChangeListener { _, checkedIds ->
            val isTrim = checkedIds.contains(R.id.tabTrim)
            binding.trimPage.isVisible = isTrim
            binding.adjustPage.isVisible = !isTrim
        }
    }

    private fun loadDuration(path: Path) {
        lifecycleScope.launch {
            val durationMs = try {
                withContext(Dispatchers.IO) { VideoInfoLoader.load(path) }.durationMs
            } catch (e: Exception) {
                null
            }
            if (durationMs == null || durationMs <= 0) {
                showToast(R.string.video_info_load_failed)
                finish()
                return@launch
            }
            durationUs = durationMs * 1000
            for (i in clips.indices) {
                val clip = clips[i]
                if (clip.endUs > durationUs) {
                    clips[i] = clip.copy(
                        endUs = durationUs,
                        startUs = min(clip.startUs, durationUs - MIN_CLIP_DURATION_US)
                            .coerceAtLeast(0)
                    )
                }
            }
            rebuildPlaylistAfterDataChange()
            selectClip(0)
        }
    }

    private fun bindSliders() {
        bindSliderWithUndo(binding.brightnessSlider) { value ->
            updateClip { it.copy(brightness = value) }
            scheduleEffectsApplication()
        }
        bindSliderWithUndo(binding.contrastSlider) { value ->
            updateClip { it.copy(contrast = value) }
            scheduleEffectsApplication()
        }
        bindSliderWithUndo(binding.cropSlider) { value ->
            updateClip { it.copy(cropFraction = value) }
            scheduleEffectsApplication()
        }
    }

    private fun bindSliderWithUndo(slider: Slider, onChange: (Float) -> Unit) {
        slider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
            override fun onStartTrackingTouch(slider: Slider) {
                pushUndo()
            }

            override fun onStopTrackingTouch(slider: Slider) {}
        })
        slider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                onChange(value)
            }
        }
    }

    private fun updateClip(transform: (ClipEdit) -> ClipEdit) {
        val index = selectedClipIndex
        if (index < 0 || index >= clips.size || updatingSliders) {
            return
        }
        clips[index] = transform(clips[index])
        binding.timelineView.clips = clips.toList()
    }

    private fun selectClip(index: Int) {
        selectedClipIndex = index
        binding.timelineView.selectedClipIndex = index
        binding.adjustControls.isVisible = index != -1
        binding.selectClipHint.isVisible = index == -1
        binding.deleteClipButton.isEnabled = index != -1
        if (index in clips.indices) {
            refreshSliders(clips[index])
            // Move the playhead to the clip start so the preview always shows the selected clip.
            playheadUs = clipStartUsOnTimeline(index)
            syncScrollToPlayhead()
            seekPlayerToPlayhead()
            lastEffectClipIndex = index
            applyVideoEffects(index)
        }
    }

    private fun refreshSliders(clip: ClipEdit) {
        updatingSliders = true
        binding.brightnessSlider.value = clip.brightness
        binding.contrastSlider.value = clip.contrast
        binding.cropSlider.value = clip.cropFraction
        updatingSliders = false
    }

    private fun addClip() {
        val endUs = min(DEFAULT_CLIP_DURATION_US, durationUs.coerceAtLeast(MIN_CLIP_DURATION_US))
        pushUndo()
        clips.add(ClipEdit(0, endUs))
        rebuildPlaylistAfterDataChange()
        selectClip(clips.size - 1)
    }

    private fun deleteClip() {
        val index = selectedClipIndex
        if (index < 0 || index >= clips.size) {
            return
        }
        pushUndo()
        clips.removeAt(index)
        playheadUs = playheadUs.coerceIn(0L, timelineTotalDuration())
        rebuildPlaylistAfterDataChange()
        selectClip(-1)
    }

    // Splits the clip containing the playhead into two at the playhead position; both halves keep
    // the original clip's parameters.
    private fun splitAtPlayhead() {
        var accumulated = 0L
        clips.forEachIndexed { index, clip ->
            val clipDuration = clip.endUs - clip.startUs
            val local = playheadUs - accumulated
            if (local > MIN_CLIP_DURATION_US && local < clipDuration - MIN_CLIP_DURATION_US) {
                val sourceSplitUs = clip.startUs + local
                pushUndo()
                clips.add(index + 1, clip.copy(startUs = sourceSplitUs))
                clips[index] = clip.copy(endUs = sourceSplitUs)
                rebuildPlaylistAfterDataChange()
                selectClip(index)
                return
            }
            accumulated += clipDuration
        }
    }

    private fun undo() {
        if (undoStack.isEmpty()) {
            return
        }
        redoStack.addLast(clips.map { it.copy() })
        clips.clear()
        clips.addAll(undoStack.removeLast())
        refreshAfterHistoryChange()
    }

    private fun redo() {
        if (redoStack.isEmpty()) {
            return
        }
        undoStack.addLast(clips.map { it.copy() })
        clips.clear()
        clips.addAll(redoStack.removeLast())
        refreshAfterHistoryChange()
    }

    private fun pushUndo() {
        undoStack.addLast(clips.map { it.copy() })
        if (undoStack.size > MAX_UNDO_STACK) {
            undoStack.removeFirst()
        }
        redoStack.clear()
        updateUndoRedoButtons()
    }

    private fun refreshAfterHistoryChange() {
        playheadUs = playheadUs.coerceIn(0L, timelineTotalDuration())
        rebuildPlaylistAfterDataChange()
        selectClip(-1)
        updateUndoRedoButtons()
    }

    private fun updateUndoRedoButtons() {
        menu?.apply {
            findItem(R.id.action_undo)?.isEnabled = undoStack.isNotEmpty()
            findItem(R.id.action_redo)?.isEnabled = redoStack.isNotEmpty()
        }
    }

    private fun export() {
        if (clips.isEmpty()) {
            showToast(R.string.video_timeline_empty)
            return
        }
        for (clip in clips) {
            if (clip.startUs >= clip.endUs) {
                showToast(R.string.video_timeline_invalid_range)
                return
            }
        }
        file?.let { startVideoTranscodeActivity(this, it, clips.toList()) }
        finish()
    }

    // --- Timeline <-> player mapping ---

    private fun timelineTotalDuration(): Long = clips.sumOf { it.endUs - it.startUs }

    private fun clipStartUsOnTimeline(index: Int): Long {
        var accumulated = 0L
        for (i in 0 until index) {
            accumulated += clips[i].endUs - clips[i].startUs
        }
        return accumulated
    }

    private fun clipIndexAtPlayhead(): Int {
        var accumulated = 0L
        clips.forEachIndexed { index, clip ->
            val clipDuration = clip.endUs - clip.startUs
            if (playheadUs < accumulated + clipDuration) {
                return index
            }
            accumulated += clipDuration
        }
        return clips.lastIndex.takeIf { it >= 0 } ?: -1
    }

    private fun buildMediaItems(): List<MediaItem> {
        val uri = Uri.fromFile(file!!.path.toFile())
        return clips.map { clip ->
            MediaItem.Builder()
                .setUri(uri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionUs(clip.startUs)
                        .setEndPositionUs(clip.endUs)
                        .build()
                )
                .build()
        }
    }

    private fun rebuildPlaylistAfterDataChange() {
        val player = player ?: return
        player.setMediaItems(buildMediaItems())
        binding.timelineView.clips = clips.toList()
        seekPlayerToPlayhead()
        syncScrollToPlayhead()
        lastEffectClipIndex = -2
    }

    private fun seekPlayerToPlayhead() {
        val player = player ?: return
        var accumulated = 0L
        clips.forEachIndexed { index, clip ->
            val clipDuration = clip.endUs - clip.startUs
            if (playheadUs < accumulated + clipDuration || index == clips.lastIndex) {
                player.seekTo(
                    index, (clip.startUs + (playheadUs - accumulated).coerceIn(0L, clipDuration))
                        / 1000
                )
                updatePreviewOverlay()
                return
            }
            accumulated += clipDuration
        }
    }

    private fun playerPositionToPlayheadUs(mediaItemIndex: Int, positionMs: Long): Long {
        var accumulated = 0L
        for (i in 0 until mediaItemIndex.coerceIn(0, clips.size)) {
            accumulated += clips[i].endUs - clips[i].startUs
        }
        return accumulated + positionMs * 1000L
    }

    private fun syncScrollToPlayhead() {
        if (viewportWidth <= 0) {
            return
        }
        isSyncingScroll = true
        binding.timelineScroll.scrollTo(
            (binding.timelineView.contentXForUs(playheadUs) - viewportWidth / 2f).toInt(), 0
        )
        isSyncingScroll = false
    }

    // --- Preview effects ---

    private fun scheduleEffectsApplication() {
        handler.removeCallbacks(effectsRunnable)
        handler.postDelayed(effectsRunnable, EFFECTS_DEBOUNCE_MS)
    }

    private fun applyVideoEffects(index: Int) {
        val clip = clips.getOrNull(index)
        val effects = mutableListOf<Effect>()
        clip?.let {
            if (it.cropFraction > 0f) {
                val fraction = it.cropFraction.coerceIn(0f, 0.5f)
                effects.add(Crop(fraction, fraction, 1f - fraction, 1f - fraction))
            }
            if (it.brightness != 0f) {
                effects.add(Brightness(it.brightness.coerceIn(-1f, 1f)))
            }
            if (it.contrast != 0f) {
                effects.add(Contrast(it.contrast.coerceIn(-1f, 1f)))
            }
        }
        player?.setVideoEffects(effects)
        updatePreviewOverlay()
    }

    // ExoPlayer does not render frames through the video effects pipeline while paused, so the
    // overlay shows the frame at the playhead with the selected clip's adjustments applied as a
    // static preview whenever the player is paused.
    private fun updatePreviewOverlay() {
        val player = player ?: return
        if (player.isPlaying) {
            binding.previewOverlay.isVisible = false
            return
        }
        binding.previewOverlay.isVisible = true
        overlayJob?.cancel()
        overlayJob = lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) { loadOverlayBitmap() } ?: return@launch
            if (isActive) {
                binding.previewOverlay.setImageBitmap(bitmap)
            }
        }
    }

    private fun loadOverlayBitmap(): Bitmap? {
        val clip = clips.getOrNull(clipIndexAtPlayhead()) ?: return null
        val retriever = retriever ?: return null
        val frame = try {
            retriever.getFrameAtTime(sourceUsAtPlayhead(), MediaMetadataRetriever.OPTION_CLOSEST)
        } catch (e: Exception) {
            null
        } ?: return null
        return applyAdjustmentsToBitmap(frame, clip)
    }

    private fun sourceUsAtPlayhead(): Long {
        var accumulated = 0L
        clips.forEachIndexed { index, clip ->
            val clipDuration = clip.endUs - clip.startUs
            if (playheadUs < accumulated + clipDuration || index == clips.lastIndex) {
                return clip.startUs + (playheadUs - accumulated).coerceIn(0L, clipDuration)
            }
            accumulated += clipDuration
        }
        return 0L
    }

    private fun applyAdjustmentsToBitmap(source: Bitmap, clip: ClipEdit): Bitmap {
        var bitmap = source
        if (clip.cropFraction > 0f) {
            val fraction = clip.cropFraction.coerceIn(0f, 0.5f)
            val width = (bitmap.width * (1 - 2 * fraction)).toInt().coerceAtLeast(1)
            val height = (bitmap.height * (1 - 2 * fraction)).toInt().coerceAtLeast(1)
            bitmap = Bitmap.createBitmap(
                bitmap, ((bitmap.width - width) / 2), ((bitmap.height - height) / 2), width, height
            )
        }
        if (clip.brightness != 0f || clip.contrast != 0f) {
            val out = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config!!)
            val brightness = (1 + clip.brightness).coerceIn(0f, 4f)
            val contrast = (1 + clip.contrast).coerceIn(0f, 4f)
            val translate = -128f * clip.contrast
            val colorMatrix = ColorMatrix()
            colorMatrix.setScale(brightness, brightness, brightness, 1f)
            colorMatrix.postConcat(
                ColorMatrix(
                    floatArrayOf(
                        contrast, 0f, 0f, 0f, translate,
                        0f, contrast, 0f, 0f, translate,
                        0f, 0f, contrast, 0f, translate,
                        0f, 0f, 0f, 1f, 0f,
                    )
                )
            )
            Canvas(out).drawBitmap(
                bitmap, 0f, 0f, Paint().apply {
                    colorFilter = ColorMatrixColorFilter(colorMatrix)
                }
            )
            bitmap = out
        }
        return bitmap
    }

    companion object {
        private const val MAX_UNDO_STACK = 50

        private const val PLAYHEAD_POLL_MS = 500L

        private const val SEEK_DEBOUNCE_MS = 100L

        private const val EFFECTS_DEBOUNCE_MS = 150L
    }
}

private const val EXTRA_FILE = "dev.vcgms.aapp.vaterialfiles.videoedit.edit.FILE"

private const val EXTRA_CLIPS = "dev.vcgms.aapp.vaterialfiles.videoedit.edit.CLIPS"

private const val DEFAULT_CLIP_DURATION_US = 10_000_000L

private const val MIN_CLIP_DURATION_US = 500_000L

fun createVideoEditIntent(
    context: Context,
    file: FileItem,
    clips: List<ClipEdit>?,
): Intent {
    val intent = Intent(context, VideoEditActivity::class.java)
    intent.putExtra(EXTRA_FILE, file)
    if (!clips.isNullOrEmpty()) {
        intent.putParcelableArrayListExtra(EXTRA_CLIPS, ArrayList(clips))
    }
    return intent
}
