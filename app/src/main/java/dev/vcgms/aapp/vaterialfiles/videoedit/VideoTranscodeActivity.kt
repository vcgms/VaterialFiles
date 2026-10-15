package dev.vcgms.aapp.vaterialfiles.videoedit

import android.content.Context
import android.content.Intent
import android.media.MediaCodecInfo
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MimeTypes
import java8.nio.file.Path
import java.util.Random
import kotlin.math.min
import kotlin.math.roundToInt
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
import dev.vcgms.aapp.vaterialfiles.provider.linux.isLinuxPath
import dev.vcgms.aapp.vaterialfiles.util.OutputNames
import dev.vcgms.aapp.vaterialfiles.util.OutputNamingMode
import dev.vcgms.aapp.vaterialfiles.util.asFileName
import dev.vcgms.aapp.vaterialfiles.util.getColorByAttr
import dev.vcgms.aapp.vaterialfiles.util.layoutInflater
import dev.vcgms.aapp.vaterialfiles.util.setEnabledWithChildren
import dev.vcgms.aapp.vaterialfiles.util.showToast
import dev.vcgms.aapp.vaterialfiles.videoinfo.VideoInfo
import dev.vcgms.aapp.vaterialfiles.videoinfo.VideoInfoLoader

// The re-encode page: sets the save mode, output naming, codec, resolution, bit rate, frame rate,
// HDR handling and advanced encoder settings, and runs the export with in-page progress backed by
// a foreground service job.
class VideoTranscodeActivity : AppActivity() {
    private lateinit var binding: VideoTranscodeActivityBinding

    private var file: FileItem? = null

    private var sourceVideoInfo: VideoInfo? = null

    private var transcodeJobId: Int? = null

    private var progressJob: Job? = null

    private var isTranscoding = false

    // Whether this device can re-encode keeping HDR; when false Media3 would silently tone-map the
    // output to SDR, so the "keep HDR" option is only offered when this is true.
    private var deviceCanKeepHdr = false

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
        if (!MediaCodecUtils.hasEncoder(MimeTypes.VIDEO_H265)) {
            binding.videoCodecHevc.isEnabled = false
        }
        // HEVC is the default codec; fall back to AVC when HEVC cannot be encoded at all.
        binding.videoCodecHevc.isChecked = binding.videoCodecHevc.isEnabled
        binding.videoCodecAvc.isChecked = !binding.videoCodecHevc.isChecked
        binding.saveKeep.isChecked = true
        binding.nameKeep.isChecked = true
        binding.resolutionOriginal.isChecked = true
        binding.bitRatePreset.isChecked = true
        binding.frameRateOriginal.isChecked = true
        binding.bitrateModeVbr.isChecked = true
        binding.profileMain.isChecked = true
        binding.hdrKeep.isChecked = true
        binding.suffixEdit.setText(DEFAULT_SUFFIX)
        // Prefill the custom-name field with the original file name so the user only edits it.
        binding.outputNameEdit.setText(file.name)
        // Preset resolutions and frame rates need the source parameters; hide them until known.
        setSourceDependentChipsVisible(false)
        binding.videoCodecGroup.setOnCheckedStateChangeListener { _, _ ->
            updateProfileGroupVisibility()
            updateHdrState()
            updateEncoderSubtitle()
        }
        binding.hdrGroup.setOnCheckedStateChangeListener { _, _ ->
            updateHdrState()
            updateEncoderSubtitle()
        }
        binding.saveModeGroup.setOnCheckedStateChangeListener { _, _ -> updateNameUi() }
        binding.fileNameGroup.setOnCheckedStateChangeListener { _, _ -> updateNameUi() }
        binding.suffixEdit.doAfterTextChanged { updateNameUi() }
        binding.outputNameEdit.doAfterTextChanged { updateNameUi() }
        binding.resolutionGroup.setOnCheckedStateChangeListener { _, _ -> updateResolutionValue() }
        binding.bitRateGroup.setOnCheckedStateChangeListener { _, _ -> updateBitRateUi() }
        binding.bitRateSlider.addOnChangeListener { _, value, _ -> updateBitRateSliderText(value) }
        updateProfileGroupVisibility()
        updateNameUi()
        updateBitRateUi()
        updateBitRateSliderText(binding.bitRateSlider.value)
        updateEncoderSubtitle()
        loadSourceVideoInfo(file.path)
    }

    override fun onDestroy() {
        super.onDestroy()
        progressJob?.cancel()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.video_transcode, menu)
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
            updateSourceDependentUi()
        }
    }

    private val sourceTrack get() = sourceVideoInfo?.videoTracks?.firstOrNull()

    private val sourceShortSide: Int?
        get() {
            val track = sourceTrack ?: return null
            val width = track.width ?: return null
            val height = track.height ?: return null
            if (width <= 0 || height <= 0) {
                return null
            }
            return min(width, height)
        }

    private val sourceIsHdr: Boolean
        get() {
            val track = sourceTrack ?: return false
            val transfer = track.colorTransfer
            return transfer == "PQ (HDR10)" || transfer == "HLG"
                || track.mime.contains("dolby", ignoreCase = true)
        }

    // HLG sources are kept as HLG (HEVC Main10); PQ and Dolby Vision sources are kept as HDR10
    // (HEVC Main10 HDR10). Dolby Vision is never re-emitted: Media3 encodes to HEVC and keeps only
    // the base-layer transfer, dropping the Dolby Vision RPU dynamic metadata.
    private val sourceIsHlg: Boolean
        get() = sourceTrack?.colorTransfer == "HLG"

    private fun updateSourceDependentUi() {
        val info = sourceVideoInfo
        val shortSide = sourceShortSide
        setSourceDependentChipsVisible(info != null)
        if (info != null) {
            // Never offer presets above the source resolution or frame rate.
            if (shortSide != null) {
                binding.resolution2160p.isVisible = PRESET_2160P <= shortSide
                binding.resolution1440p.isVisible = PRESET_1440P <= shortSide
                binding.resolution1080p.isVisible = PRESET_1080P <= shortSide
                binding.resolution720p.isVisible = PRESET_720P <= shortSide
                binding.resolution480p.isVisible = PRESET_480P <= shortSide
            }
            val frameRate = sourceTrack?.frameRate
            if (frameRate != null && frameRate > 0f) {
                binding.frameRate60.isVisible = 60 <= frameRate + 0.5f
                binding.frameRate30.isVisible = 30 <= frameRate + 0.5f
                binding.frameRate25.isVisible = 25 <= frameRate + 0.5f
                binding.frameRate24.isVisible = 24 <= frameRate + 0.5f
                binding.frameRateValueText.text = getString(
                    R.string.video_transcode_frame_rate_source, formatFrameRate(frameRate)
                )
            }
            val bitRate = sourceTrack?.bitRate ?: info.overallBitRate
            if (bitRate != null && bitRate > 0) {
                binding.bitRateValueText.isVisible = true
                binding.bitRateValueText.text = getString(
                    R.string.video_transcode_bitrate_source, formatBitRate(bitRate)
                )
            }
        }
        updateResolutionValue()
        updateBitRateUi()
        // Probe HDR-encoding support once the source transfer is known; it decides whether "keep
        // HDR" is offered (Media3 silently outputs SDR when the device cannot encode HDR).
        deviceCanKeepHdr = info != null && sourceIsHdr &&
            MediaCodecUtils.canKeepHdr(MimeTypes.VIDEO_H265, sourceIsHlg)
        updateHdrState()
        updateNameUi()
    }

    private fun setSourceDependentChipsVisible(visible: Boolean) {
        binding.resolution2160p.isVisible = visible
        binding.resolution1440p.isVisible = visible
        binding.resolution1080p.isVisible = visible
        binding.resolution720p.isVisible = visible
        binding.resolution480p.isVisible = visible
    }

    // The output pixel size for the selected resolution preset, preserving the source aspect
    // ratio; null when the source dimensions are not known yet.
    private fun computeOutputResolution(): Pair<Int, Int>? {
        val track = sourceTrack ?: return null
        val width = track.width ?: return null
        val height = track.height ?: return null
        if (width <= 0 || height <= 0) {
            return null
        }
        val preset = selectedPresetShortSide() ?: return width to height
        return if (width >= height) {
            toEven(width.toFloat() / height * preset) to preset
        } else {
            preset to toEven(height.toFloat() / width * preset)
        }
    }

    private fun updateResolutionValue() {
        val resolution = computeOutputResolution()
        if (resolution == null) {
            binding.resolutionValueText.isVisible = false
            return
        }
        binding.resolutionValueText.isVisible = true
        binding.resolutionValueText.text = getString(
            R.string.video_transcode_resolution_value, resolution.first, resolution.second
        )
    }

    private fun toEven(value: Float): Int = (value.roundToInt() + 1) / 2 * 2

    private fun updateBitRateUi() {
        val isPreset = binding.bitRatePreset.isChecked
        // The source bit rate stays visible in every mode as a reference.
        binding.bitRateSliderContainer.isVisible = isPreset
        binding.bitRateCustomLayout.isVisible = binding.bitRateCustom.isChecked
    }

    private fun updateBitRateSliderText(value: Float) {
        binding.bitRateSliderText.text = getString(
            R.string.video_transcode_bitrate_slider_format, value.roundToInt()
        )
    }

    private fun updateProfileGroupVisibility() {
        val isAvc = binding.videoCodecAvc.isChecked
        binding.profileTitle.isVisible = isAvc
        binding.profileGroup.isVisible = isAvc
    }

    // The HDR section is always shown once the source is known: an HDR source offers a two-way
    // HDR/SDR choice (HDR by default) plus the source HDR parameters, while an SDR source only
    // shows a note. Keeping HDR needs a 10-bit capable codec, which AVC is not.
    private fun updateHdrState() {
        val loaded = sourceVideoInfo != null
        val isHdr = sourceIsHdr
        binding.hdrTitle.isVisible = loaded
        binding.hdrGroup.isVisible = loaded && isHdr
        binding.hdrInfoText.isVisible = loaded
        if (isHdr) {
            // Keeping HDR needs a 10-bit capable codec (not AVC) and a device whose encoder
            // supports HDR editing; otherwise Media3 silently tone-maps the output to SDR.
            binding.hdrKeep.isEnabled = !binding.videoCodecAvc.isChecked && deviceCanKeepHdr
            if (!binding.hdrKeep.isEnabled && binding.hdrKeep.isChecked) {
                binding.hdrSdr.isChecked = true
            }
            binding.hdrInfoText.text = if (deviceCanKeepHdr) {
                getString(R.string.video_transcode_hdr_info, outputColorInfoString())
            } else {
                getString(R.string.video_transcode_hdr_unsupported)
            }
        } else {
            binding.hdrInfoText.setText(R.string.video_transcode_hdr_sdr_source)
        }
    }

    // Describes the OUTPUT color/HDR format implied by the current codec and HDR choice, so the
    // user sees what will be written rather than the source parameters. A Dolby Vision source kept
    // as HDR is written as standard HDR10/HLG (HEVC Main10), never as Dolby Vision.
    private fun outputColorInfoString(): String {
        val keepingHdr =
            sourceIsHdr && binding.hdrKeep.isChecked && !binding.videoCodecAvc.isChecked
        return if (keepingHdr) {
            val transfer = if (sourceIsHlg) "HLG" else "HDR10"
            "$transfer \u00b7 BT.2020 \u00b7 HEVC Main10"
        } else {
            val codec = if (binding.videoCodecAvc.isChecked) "H.264" else "HEVC"
            "SDR \u00b7 BT.709 \u00b7 $codec"
        }
    }

    private fun updateEncoderSubtitle() {
        val mime = selectedVideoMime()
        val hardware = MediaCodecUtils.hasHardwareEncoder(mime)
        supportActionBar?.subtitle = getString(
            if (hardware) {
                R.string.video_transcode_encoder_hw
            } else {
                R.string.video_transcode_encoder_sw
            }
        )
        binding.toolbar.setSubtitleTextColor(
            if (hardware) {
                getColorByAttr(android.R.attr.textColorSecondary)
            } else {
                getColor(R.color.encoder_software_warning)
            }
        )
    }

    private fun namingMode(): OutputNamingMode = when {
        binding.nameSuffix.isChecked -> OutputNamingMode.SUFFIX
        binding.nameCustom.isChecked -> OutputNamingMode.CUSTOM
        else -> OutputNamingMode.KEEP
    }

    // The transformer always writes an MP4 container, so the output extension is fixed to .mp4
    // regardless of the source container.
    private fun outputExtension(): String = ".mp4"

    private fun updateNameUi() {
        val mode = namingMode()
        binding.suffixLayout.isVisible = mode == OutputNamingMode.SUFFIX
        binding.outputNameLayout.isVisible = mode == OutputNamingMode.CUSTOM
        binding.resolvedNameText.isVisible = mode != OutputNamingMode.CUSTOM
        if (mode != OutputNamingMode.CUSTOM) {
            binding.resolvedNameText.text = getString(
                R.string.video_transcode_resolved_name, resolvedOutputName()
            )
        }
        val conflictsWithSource = resolvedOutputName() == file?.name
        binding.conflictHintText.isVisible = conflictsWithSource && binding.saveKeep.isChecked
    }

    private fun resolvedOutputName(): String {
        val file = file ?: return ""
        return OutputNames.resolvedName(
            baseName = file.name.asFileName().baseName,
            extension = outputExtension(),
            mode = namingMode(),
            suffix = binding.suffixEdit.text?.toString()?.trim().orEmpty(),
            customName = binding.outputNameEdit.text?.toString()?.trim().orEmpty()
        )
    }

    private fun startTranscode() {
        val file = file ?: return
        val mode = namingMode()
        val suffix = binding.suffixEdit.text?.toString()?.trim().orEmpty()
        val customName = binding.outputNameEdit.text?.toString()?.trim().orEmpty()
        if (mode == OutputNamingMode.SUFFIX && suffix.isEmpty()) {
            showToast(R.string.video_transcode_suffix_empty)
            return
        }
        if (mode == OutputNamingMode.CUSTOM && customName.isEmpty()) {
            showToast(R.string.video_transcode_output_name_empty)
            return
        }
        val deleteSource = binding.saveDelete.isChecked
        val outputName = OutputNames.resolvedName(
            file.name.asFileName().baseName, outputExtension(), mode, suffix, customName
        )
        // In delete-source mode an identical name means an in-place overwrite; otherwise avoid
        // colliding with any existing file by appending _1, _2, ... as needed.
        val outputPath = OutputNames.resolveUnique(
            file.path.parent, outputName, if (deleteSource) file.path else null
        )
        val hdrKeep = sourceIsHdr && binding.hdrKeep.isChecked
        val params = VideoTranscodeParams(
            sourcePath = file.path,
            outputPath = outputPath,
            videoMime = selectedVideoMime(),
            shortSide = selectedShortSide(),
            bitRate = selectedBitRate(),
            frameRate = selectedFrameRate(),
            bitrateMode = if (binding.bitrateModeCbr.isChecked) {
                VIDEO_BITRATE_MODE_CBR
            } else {
                null
            },
            profileLevel = selectedProfileLevel(hdrKeep),
            sourceIsHdr = sourceIsHdr,
            hdrKeep = hdrKeep,
            deleteSource = deleteSource,
        )
        val jobId = Random().nextInt()
        transcodeJobId = jobId
        // Clear any stale Finished/Failed state for this path BEFORE the collector subscribes below;
        // otherwise the collector replays it on subscription and tears the transcoding UI back down,
        // leaving only the progress bar (re-shown by the job's Running emissions) with no summary and
        // the action stuck on "start" instead of "cancel".
        VideoTranscodeRegistry.start(file.path)
        // Show what is being written above the progress bar while the (locked) form is dimmed.
        binding.transcodeSummaryText.text =
            buildTranscodeSummary(outputPath.fileName.toString())
        setTranscodingUi(true)
        progressJob = lifecycleScope.launch {
            VideoTranscodeRegistry.progressFlow(file.path).collect { progress ->
                when (progress) {
                    TranscodeProgress.NotStarted -> Unit
                    is TranscodeProgress.Running -> {
                        binding.progressContainer.isVisible = true
                        binding.progressIndicator.setProgressCompat(progress.progress, true)
                        binding.progressPercentText.text = getString(
                            R.string.video_transcode_progress_format, progress.progress
                        )
                    }
                    TranscodeProgress.Finished -> onTranscodeFinished()
                    is TranscodeProgress.Failed -> onTranscodeFailed(progress.message)
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

    // The export failed: reset the page (unlock the form, hide progress) and tell the user, so a
    // failure no longer leaves the bar hanging at 0% with the options locked.
    private fun onTranscodeFailed(message: String?) {
        progressJob?.cancel()
        progressJob = null
        transcodeJobId = null
        setTranscodingUi(false)
        binding.progressIndicator.setProgressCompat(0, false)
        binding.progressPercentText.text = null
        if (message.isNullOrBlank()) {
            showToast(R.string.video_transcode_failed)
        } else {
            showToast(getString(R.string.video_transcode_failed_with_reason, message))
        }
    }

    // A short summary of the output file name and the main encoding parameters, shown above the
    // progress bar while re-encoding (the form itself is locked and dimmed at that point).
    private fun buildTranscodeSummary(outputName: String): String {
        val parts = mutableListOf<String>()
        parts += if (binding.videoCodecAvc.isChecked) "H.264" else "H.265"
        val resolution = computeOutputResolution()
        parts += resolution?.let { "${it.first}\u00d7${it.second}" }
            ?: getString(R.string.video_transcode_original)
        selectedBitRate()?.let {
            parts += String.format(java.util.Locale.US, "%.1f Mbps", it / 1_000_000.0)
        }
        selectedFrameRate()?.let { parts += "$it fps" }
        val keepingHdr =
            sourceIsHdr && binding.hdrKeep.isChecked && !binding.videoCodecAvc.isChecked
        parts += if (keepingHdr) {
            if (sourceIsHlg) "HLG" else "HDR10"
        } else {
            "SDR"
        }
        return outputName + "\n" + parts.joinToString(" \u00b7 ")
    }

    private fun cancelTranscode() {
        val jobId = transcodeJobId ?: return
        FileJobService.cancelJob(jobId)
        onTranscodeFinished()
    }

    private fun setTranscodingUi(transcoding: Boolean) {
        isTranscoding = transcoding
        val enabled = !transcoding
        // ChipGroup does not propagate setEnabled to its chips, so toggle the chips explicitly;
        // TextInputLayout and Slider disable their own content. Only the toolbar (back / stop) and
        // the progress row stay interactive while re-encoding.
        binding.saveModeGroup.setEnabledWithChildren(enabled)
        binding.fileNameGroup.setEnabledWithChildren(enabled)
        binding.videoCodecGroup.setEnabledWithChildren(enabled)
        binding.resolutionGroup.setEnabledWithChildren(enabled)
        binding.bitRateGroup.setEnabledWithChildren(enabled)
        binding.frameRateGroup.setEnabledWithChildren(enabled)
        binding.hdrGroup.setEnabledWithChildren(enabled)
        binding.bitrateModeGroup.setEnabledWithChildren(enabled)
        binding.profileGroup.setEnabledWithChildren(enabled)
        binding.suffixLayout.isEnabled = enabled
        binding.outputNameLayout.isEnabled = enabled
        binding.bitRateCustomLayout.isEnabled = enabled
        binding.bitRateSlider.isEnabled = enabled
        if (enabled) {
            // The blanket re-enable also revived the conditionally disabled chips; restore them.
            binding.videoCodecHevc.isEnabled = MediaCodecUtils.hasEncoder(MimeTypes.VIDEO_H265)
            updateHdrState()
        }
        binding.progressContainer.isVisible = transcoding
        binding.transcodeSummaryText.isVisible = transcoding
        invalidateOptionsMenu()
    }

    private fun selectedVideoMime(): String = if (binding.videoCodecAvc.isChecked) {
        MimeTypes.VIDEO_H264
    } else {
        MimeTypes.VIDEO_H265
    }

    private fun selectedPresetShortSide(): Int? = when {
        binding.resolution2160p.isChecked -> PRESET_2160P
        binding.resolution1440p.isChecked -> PRESET_1440P
        binding.resolution1080p.isChecked -> PRESET_1080P
        binding.resolution720p.isChecked -> PRESET_720P
        binding.resolution480p.isChecked -> PRESET_480P
        else -> null
    }

    private fun selectedShortSide(): Int? {
        val preset = selectedPresetShortSide() ?: return null
        val shortSide = sourceShortSide ?: return null
        // Never upscale; keep the source resolution.
        return if (shortSide > preset) preset else null
    }

    private fun selectedBitRate(): Int? = when {
        binding.bitRateOriginal.isChecked -> {
            val bitRate = sourceTrack?.bitRate ?: sourceVideoInfo?.overallBitRate ?: return null
            if (bitRate > 0) bitRate.toInt() else null
        }
        binding.bitRatePreset.isChecked -> binding.bitRateSlider.value.roundToInt() * 1_000_000
        else -> {
            val kbps = binding.bitRateCustomEdit.text?.toString()?.trim()?.toIntOrNull()
            if (kbps != null && kbps > 0) kbps * 1000 else null
        }
    }

    private fun selectedFrameRate(): Int? = when {
        binding.frameRate60.isChecked -> 60
        binding.frameRate30.isChecked -> 30
        binding.frameRate25.isChecked -> 25
        binding.frameRate24.isChecked -> 24
        else -> null
    }

    private fun selectedProfileLevel(hdrKeep: Boolean): Int? = when {
        binding.videoCodecAvc.isChecked -> when {
            binding.profileHigh.isChecked -> MediaCodecInfo.CodecProfileLevel.AVCProfileHigh
            binding.profileBaseline.isChecked ->
                MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline
            else -> MediaCodecInfo.CodecProfileLevel.AVCProfileMain
        }
        hdrKeep ->
            if (sourceIsHlg) {
                MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10
            } else {
                MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10
            }
        else -> null
    }

    private fun formatFrameRate(frameRate: Float): String =
        String.format(java.util.Locale.US, "%.2f fps", frameRate)

    private fun formatBitRate(bitRate: Long): String {
        val mbps = bitRate / 1_000_000.0
        return String.format(java.util.Locale.US, "%.2f Mbps", mbps)
    }

    companion object {
        private const val DEFAULT_SUFFIX = "_reencoded"
        private const val PRESET_2160P = 2160
        private const val PRESET_1440P = 1440
        private const val PRESET_1080P = 1080
        private const val PRESET_720P = 720
        private const val PRESET_480P = 480
    }
}

private const val EXTRA_FILE = "dev.vcgms.aapp.vaterialfiles.videoedit.extra.FILE"

fun startVideoTranscodeActivity(context: Context, file: FileItem) {
    val intent = Intent(context, VideoTranscodeActivity::class.java)
    intent.putExtra(EXTRA_FILE, file)
    context.startActivity(intent)
}
