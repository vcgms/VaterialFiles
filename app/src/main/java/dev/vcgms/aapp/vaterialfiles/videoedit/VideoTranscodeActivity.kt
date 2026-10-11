package dev.vcgms.aapp.vaterialfiles.videoedit

import android.content.Context
import android.content.Intent
import android.media.MediaCodecInfo
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MimeTypes
import java8.nio.file.Path
import java.util.Random
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.vcgms.aapp.vaterialfiles.R
import dev.vcgms.aapp.vaterialfiles.app.AppActivity
import dev.vcgms.aapp.vaterialfiles.databinding.VideoTranscodeActivityBinding
import dev.vcgms.aapp.vaterialfiles.file.FileItem
import dev.vcgms.aapp.vaterialfiles.filejob.FileJobService
import dev.vcgms.aapp.vaterialfiles.filelist.name
import dev.vcgms.aapp.vaterialfiles.provider.common.exists
import dev.vcgms.aapp.vaterialfiles.provider.linux.isLinuxPath
import dev.vcgms.aapp.vaterialfiles.util.asFileName
import dev.vcgms.aapp.vaterialfiles.util.layoutInflater
import dev.vcgms.aapp.vaterialfiles.util.setForceShowIcons
import dev.vcgms.aapp.vaterialfiles.util.showToast
import dev.vcgms.aapp.vaterialfiles.videoinfo.VideoInfo
import dev.vcgms.aapp.vaterialfiles.videoinfo.VideoInfoLoader

// The re-encode page: sets output file name, container/format-independent video encoder settings
// (codec, resolution, bit rate, frame rate, rate control and AVC profile) and runs the export
// with in-page progress backed by a foreground service job.
class VideoTranscodeActivity : AppActivity() {
    private lateinit var binding: VideoTranscodeActivityBinding

    private var file: FileItem? = null

    private var sourceVideoInfo: VideoInfo? = null

    private var transcodeJobId: Int? = null

    private var progressJob: Job? = null

    private var isTranscoding = false

    // Timeline clips passed in from the video edit page; null means export the whole video.
    private var clips: List<ClipEdit>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = VideoTranscodeActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = getString(R.string.video_transcode_title)
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
        if (!MediaCodecUtils.hasHardwareEncoder(MimeTypes.VIDEO_H265)) {
            binding.videoCodecHevc.isEnabled = false
        }
        binding.outputNameEdit.setText("${file.name.asFileName().baseName}_reencoded.mp4")
        binding.videoCodecAvc.isChecked = true
        binding.resolutionOriginal.isChecked = true
        binding.bitRateOriginal.isChecked = true
        binding.frameRateOriginal.isChecked = true
        binding.bitrateModeVbr.isChecked = true
        binding.profileMain.isChecked = true
        // Preset resolutions need the source dimensions; disable them until they are known.
        setPresetResolutionsEnabled(false)
        binding.videoCodecGroup.setOnCheckedStateChangeListener { _, _ ->
            updateProfileGroupVisibility()
        }
        updateProfileGroupVisibility()
        clips = intent.getParcelableArrayListExtra(EXTRA_CLIPS, ClipEdit::class.java)?.toList()
        updateTimelineSummary()
        loadSourceVideoInfo(file.path)
    }

    override fun onDestroy() {
        super.onDestroy()
        progressJob?.cancel()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.video_transcode, menu)
        menu.setForceShowIcons()
        return super.onCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_start).apply {
            if (isTranscoding) {
                setTitle(R.string.video_transcode_cancel)
                setIcon(R.drawable.close_icon_control_normal_24dp)
            } else {
                setTitle(R.string.video_transcode_start)
                setIcon(R.drawable.play_arrow_icon_control_normal_24dp)
            }
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_start -> {
            if (isTranscoding) {
                cancelTranscode()
            } else {
                startTranscode()
            }
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun loadSourceVideoInfo(path: Path) {
        lifecycleScope.launch {
            val info = try {
                withContext(Dispatchers.IO) { VideoInfoLoader.load(path) }
            } catch (e: Exception) {
                null
            }
            sourceVideoInfo = info
            setPresetResolutionsEnabled(info != null)
        }
    }

    private fun updateTimelineSummary() {
        val clips = clips
        if (clips.isNullOrEmpty()) {
            binding.timelineSummaryText.isVisible = false
        } else {
            binding.timelineSummaryText.isVisible = true
            binding.timelineSummaryText.text = getString(
                R.string.video_transcode_timeline_summary, clips.size
            )
        }
    }

    private fun setPresetResolutionsEnabled(enabled: Boolean) {
        binding.resolution1080p.isEnabled = enabled
        binding.resolution720p.isEnabled = enabled
        binding.resolution480p.isEnabled = enabled
    }

    private fun updateProfileGroupVisibility() {
        val isAvc = binding.videoCodecAvc.isChecked
        binding.profileTitle.isVisible = isAvc
        binding.profileGroup.isVisible = isAvc
    }

    private fun startTranscode() {
        val file = file ?: return
        val outputName = binding.outputNameEdit.text?.toString()?.trim().orEmpty()
        if (outputName.isEmpty()) {
            showToast(R.string.video_transcode_output_name_empty)
            return
        }
        val outputPath = file.path.parent.resolve(outputName)
        if (outputPath.exists()) {
            showToast(R.string.video_transcode_output_exists)
            return
        }
        val params = VideoTranscodeParams(
            sourcePath = file.path,
            outputPath = outputPath,
            videoMime = if (binding.videoCodecAvc.isChecked) {
                MimeTypes.VIDEO_H264
            } else {
                MimeTypes.VIDEO_H265
            },
            shortSide = selectedShortSide(),
            bitRate = selectedBitRate(),
            frameRate = selectedFrameRate(),
            bitrateMode = if (binding.bitrateModeCbr.isChecked) {
                VIDEO_BITRATE_MODE_CBR
            } else {
                null
            },
            avcProfile = if (binding.videoCodecAvc.isChecked) selectedAvcProfile() else null,
            clips = clips,
        )
        val jobId = Random().nextInt()
        transcodeJobId = jobId
        setTranscodingUi(true)
        progressJob = lifecycleScope.launch {
            VideoTranscodeRegistry.progressFlow(file.path).collect { progress ->
                when (progress) {
                    TranscodeProgress.NotStarted -> Unit
                    is TranscodeProgress.Running -> {
                        binding.progressIndicator.isVisible = true
                        binding.progressIndicator.setProgressCompat(progress.progress, true)
                        binding.statusText.isVisible = true
                        binding.statusText.text = getString(
                            R.string.video_transcode_progress_format, progress.progress
                        )
                    }
                    TranscodeProgress.Finished -> onTranscodeFinished()
                }
            }
        }
        FileJobService.transcode(params, jobId, this)
    }

    private fun onTranscodeFinished() {
        progressJob?.cancel()
        progressJob = null
        transcodeJobId = null
        setTranscodingUi(false)
        showToast(R.string.video_transcode_finished)
    }

    private fun cancelTranscode() {
        val jobId = transcodeJobId ?: return
        FileJobService.cancelJob(jobId)
        onTranscodeFinished()
    }

    private fun setTranscodingUi(transcoding: Boolean) {
        isTranscoding = transcoding
        binding.outputNameLayout.isEnabled = !transcoding
        binding.videoCodecGroup.isEnabled = !transcoding
        binding.resolutionGroup.isEnabled = !transcoding
        binding.bitRateGroup.isEnabled = !transcoding
        binding.frameRateGroup.isEnabled = !transcoding
        binding.bitrateModeGroup.isEnabled = !transcoding
        binding.profileGroup.isEnabled = !transcoding
        invalidateOptionsMenu()
        if (!transcoding) {
            binding.progressIndicator.isVisible = false
            binding.statusText.isVisible = false
        }
    }

    private fun selectedShortSide(): Int? {
        val info = sourceVideoInfo ?: return null
        val track = info.videoTracks.firstOrNull() ?: return null
        val width = track.width ?: return null
        val height = track.height ?: return null
        if (width <= 0 || height <= 0) {
            return null
        }
        val preset = when {
            binding.resolution1080p.isChecked -> PRESET_1080P
            binding.resolution720p.isChecked -> PRESET_720P
            binding.resolution480p.isChecked -> PRESET_480P
            else -> return null
        }
        // Never upscale; keep the source resolution.
        val shortSide = min(width, height)
        return if (shortSide > preset) preset else null
    }

    private fun selectedBitRate(): Int? = when {
        binding.bitRate2m.isChecked -> 2_000_000
        binding.bitRate4m.isChecked -> 4_000_000
        binding.bitRate8m.isChecked -> 8_000_000
        else -> null
    }

    private fun selectedFrameRate(): Int? = when {
        binding.frameRate60.isChecked -> 60
        binding.frameRate30.isChecked -> 30
        binding.frameRate25.isChecked -> 25
        binding.frameRate24.isChecked -> 24
        else -> null
    }

    private fun selectedAvcProfile(): Int? = when {
        binding.profileHigh.isChecked -> MediaCodecInfo.CodecProfileLevel.AVCProfileHigh
        binding.profileBaseline.isChecked -> MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline
        else -> MediaCodecInfo.CodecProfileLevel.AVCProfileMain
    }

    companion object {
        private const val PRESET_1080P = 1080
        private const val PRESET_720P = 720
        private const val PRESET_480P = 480
    }
}

private const val EXTRA_FILE = "dev.vcgms.aapp.vaterialfiles.videoedit.extra.FILE"

private const val EXTRA_CLIPS = "dev.vcgms.aapp.vaterialfiles.videoedit.extra.CLIPS"

fun startVideoTranscodeActivity(context: Context, file: FileItem, clips: List<ClipEdit>?) {
    val intent = Intent(context, VideoTranscodeActivity::class.java)
    intent.putExtra(EXTRA_FILE, file)
    if (!clips.isNullOrEmpty()) {
        intent.putParcelableArrayListExtra(EXTRA_CLIPS, ArrayList(clips))
    }
    context.startActivity(intent)
}
