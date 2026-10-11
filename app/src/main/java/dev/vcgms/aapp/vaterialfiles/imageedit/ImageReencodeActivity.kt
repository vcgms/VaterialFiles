package dev.vcgms.aapp.vaterialfiles.imageedit

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import java.util.Random
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.vcgms.aapp.vaterialfiles.R
import dev.vcgms.aapp.vaterialfiles.app.AppActivity
import dev.vcgms.aapp.vaterialfiles.databinding.ImageReencodeActivityBinding
import dev.vcgms.aapp.vaterialfiles.file.FileItem
import dev.vcgms.aapp.vaterialfiles.filejob.FileJobService
import dev.vcgms.aapp.vaterialfiles.filelist.name
import dev.vcgms.aapp.vaterialfiles.provider.common.exists
import dev.vcgms.aapp.vaterialfiles.util.asFileName
import dev.vcgms.aapp.vaterialfiles.util.layoutInflater
import dev.vcgms.aapp.vaterialfiles.util.setForceShowIcons
import dev.vcgms.aapp.vaterialfiles.util.showToast
import dev.vcgms.aapp.vaterialfiles.videoedit.MediaCodecUtils

// The image re-encode page: sets the target format (PNG lossless, or JPEG/HEIC with a quality
// slider), the output naming mode (keep the original name, or append a suffix), and the
// "delete source" / "preserve timestamps" options, then runs the export for one or many selected
// images with in-page progress backed by foreground service jobs.
class ImageReencodeActivity : AppActivity() {
    private lateinit var binding: ImageReencodeActivityBinding

    private var files: List<FileItem> = emptyList()

    // Upright source dimensions (single-file mode) used to preview the output resolution.
    private var sourceSize: Pair<Int, Int>? = null

    private var reencodeJobIds: List<Int> = emptyList()

    private var progressJob: Job? = null

    private var isReencoding = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ImageReencodeActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = getString(R.string.image_reencode_title)
            setDisplayHomeAsUpEnabled(true)
        }
        val files = intent.getParcelableArrayListExtra(EXTRA_FILES, FileItem::class.java)?.toList()
        if (files.isNullOrEmpty()) {
            finish()
            return
        }
        this.files = files
        if (files.size > 1) {
            binding.summaryText.isVisible = true
            binding.summaryText.text = getString(R.string.image_reencode_batch_summary, files.size)
        }
        if (!MediaCodecUtils.hasEncoder(MIME_HEIC)) {
            binding.formatHeic.isEnabled = false
        }
        binding.formatPng.isChecked = true
        binding.rotation0.isChecked = true
        binding.namingAddSuffix.isChecked = true
        binding.suffixEdit.setText(DEFAULT_SUFFIX)
        updateVisibility()
        binding.qualitySlider.addOnChangeListener { _, value, _ ->
            binding.qualityValueText.text = getString(
                R.string.image_reencode_quality_format, value.roundToInt()
            )
        }
        binding.qualityValueText.text = getString(
            R.string.image_reencode_quality_format, binding.qualitySlider.value.roundToInt()
        )
        binding.formatGroup.setOnCheckedStateChangeListener { _, _ -> updateVisibility() }
        binding.namingGroup.setOnCheckedStateChangeListener { _, _ -> updateVisibility() }
        binding.resolutionSlider.addOnChangeListener { _, value, _ ->
            binding.resolutionValueText.text = getString(
                R.string.image_reencode_quality_format, value.roundToInt()
            )
            updateResolutionHint()
        }
        binding.resolutionValueText.text = getString(
            R.string.image_reencode_quality_format, binding.resolutionSlider.value.roundToInt()
        )
        binding.rotationGroup.setOnCheckedStateChangeListener { _, _ -> updateResolutionHint() }
        updateResolutionHint()
        loadSourceSize()
    }

    override fun onDestroy() {
        super.onDestroy()
        progressJob?.cancel()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.image_reencode, menu)
        menu.setForceShowIcons()
        return super.onCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.action_start).apply {
            if (isReencoding) {
                setTitle(R.string.image_reencode_cancel)
                setIcon(R.drawable.close_icon_control_normal_24dp)
            } else {
                setTitle(R.string.image_reencode_start)
                setIcon(R.drawable.play_arrow_icon_control_normal_24dp)
            }
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_start -> {
            if (isReencoding) {
                cancelReencode()
            } else {
                startReencode()
            }
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun selectedFormat(): ImageReencodeFormat = when {
        binding.formatJpeg.isChecked -> ImageReencodeFormat.JPEG
        binding.formatHeic.isChecked -> ImageReencodeFormat.HEIC
        else -> ImageReencodeFormat.PNG
    }

    private fun selectedRotation(): Int = when {
        binding.rotation90.isChecked -> 90
        binding.rotation180.isChecked -> 180
        binding.rotation270.isChecked -> 270
        else -> 0
    }

    // Shows the output resolution the current percentage maps to. For a single file the exact
    // dimensions are computed from the upright source size (accounting for the selected rotation);
    // for a batch each file has its own size, so only the percentage is described.
    private fun updateResolutionHint() {
        val percent = binding.resolutionSlider.value.roundToInt()
        if (files.size != 1) {
            binding.resolutionHintText.text = getString(
                R.string.image_reencode_resolution_batch_hint, percent
            )
            return
        }
        val base = sourceSize
        if (base == null) {
            binding.resolutionHintText.setText(R.string.loading)
            return
        }
        val rotation = selectedRotation()
        var width = base.first
        var height = base.second
        if (rotation == 90 || rotation == 270) {
            val temp = width
            width = height
            height = temp
        }
        val outWidth = max(1, (width * percent / 100f).roundToInt())
        val outHeight = max(1, (height * percent / 100f).roundToInt())
        binding.resolutionHintText.text = getString(
            R.string.image_reencode_resolution_output_format, outWidth, outHeight
        )
    }

    private fun loadSourceSize() {
        if (files.size != 1) {
            return
        }
        val path = files[0].path
        lifecycleScope.launch {
            val size = withContext(Dispatchers.IO) { ImageReencoder.uprightSize(path) }
            sourceSize = size
            updateResolutionHint()
        }
    }

    private fun updateVisibility() {
        val format = selectedFormat()
        val isPng = format == ImageReencodeFormat.PNG
        binding.qualityTitle.isVisible = !isPng
        binding.qualityContainer.isVisible = !isPng
        binding.qualityNoteText.isVisible = isPng
        binding.heicNoteText.isVisible = format == ImageReencodeFormat.HEIC
        binding.suffixLayout.isVisible = binding.namingAddSuffix.isChecked
    }

    private fun startReencode() {
        val format = selectedFormat()
        if (format == ImageReencodeFormat.HEIC && !MediaCodecUtils.hasEncoder(MIME_HEIC)) {
            showToast(R.string.image_reencode_heic_unsupported)
            return
        }
        val keepOriginal = binding.namingKeepOriginal.isChecked
        val suffix = binding.suffixEdit.text?.toString()?.trim().orEmpty()
        if (!keepOriginal && suffix.isEmpty()) {
            showToast(R.string.image_reencode_suffix_empty)
            return
        }
        val quality = binding.qualitySlider.value.roundToInt()
        val rotationDegrees = selectedRotation()
        val resolutionPercent = binding.resolutionSlider.value.roundToInt()
        val deleteSource = binding.deleteSourceSwitch.isChecked
        val preserveExif = binding.preserveTimestampsSwitch.isChecked
        val extension = extensionForFormat(format)

        val targets = files.map { file ->
            val baseName = file.name.asFileName().baseName
            val outputName = if (keepOriginal) {
                baseName + extension
            } else {
                baseName + suffix + extension
            }
            file to file.path.parent.resolve(outputName)
        }
        // For a single file, surface an existing-output conflict immediately (an in-place
        // re-encode, where the output equals the source, is allowed and handled by the job).
        if (targets.size == 1) {
            val (file, outputPath) = targets[0]
            if (outputPath != file.path && outputPath.exists()) {
                showToast(R.string.image_reencode_output_exists)
                return
            }
        }

        val total = targets.size
        val jobIds = ArrayList<Int>(total)
        reencodeJobIds = jobIds
        setReencodingUi(true)
        updateProgressText(0, total)
        progressJob = lifecycleScope.launch {
            var done = 0
            var failed = 0
            coroutineScope {
                targets.map { (file, _) ->
                    launch {
                        // Reset to Running before collecting so a stale terminal state from a
                        // previous run on the same source path is not replayed.
                        ImageReencodeRegistry.setRunning(file.path)
                        val state = ImageReencodeRegistry.stateFlow(file.path)
                            .first {
                                it is ImageReencodeState.Finished || it is ImageReencodeState.Failed
                            }
                        if (state is ImageReencodeState.Finished) {
                            done++
                        } else {
                            failed++
                        }
                        updateProgressText(done + failed, total)
                    }
                }
            }
            onAllFinished(done, failed, total)
        }
        for ((file, outputPath) in targets) {
            val params = ImageReencodeParams(
                sourcePath = file.path,
                outputPath = outputPath,
                format = format,
                quality = quality,
                rotationDegrees = rotationDegrees,
                resolutionPercent = resolutionPercent,
                deleteSource = deleteSource,
                preserveExif = preserveExif,
            )
            val jobId = Random().nextInt()
            jobIds.add(jobId)
            FileJobService.reencodeImage(params, jobId, this)
        }
    }

    private fun updateProgressText(completed: Int, total: Int) {
        binding.progressIndicator.isVisible = true
        binding.statusText.isVisible = true
        binding.statusText.text = if (total == 1) {
            getString(R.string.image_reencode_running)
        } else {
            getString(R.string.image_reencode_progress_format, completed, total)
        }
    }

    private fun onAllFinished(done: Int, failed: Int, total: Int) {
        progressJob?.cancel()
        progressJob = null
        reencodeJobIds = emptyList()
        setReencodingUi(false)
        showToast(
            when {
                total == 1 && failed == 0 -> getString(R.string.image_reencode_finished)
                failed == 0 -> getString(R.string.image_reencode_batch_finished, done)
                done == 0 -> getString(R.string.image_reencode_batch_all_failed, failed)
                else -> getString(R.string.image_reencode_batch_partial, done, failed)
            }
        )
    }

    private fun cancelReencode() {
        for (jobId in reencodeJobIds) {
            FileJobService.cancelJob(jobId)
        }
        reencodeJobIds = emptyList()
        progressJob?.cancel()
        progressJob = null
        setReencodingUi(false)
    }

    private fun setReencodingUi(reencoding: Boolean) {
        isReencoding = reencoding
        binding.namingGroup.isEnabled = !reencoding
        binding.suffixLayout.isEnabled = !reencoding
        binding.formatGroup.isEnabled = !reencoding
        binding.rotationGroup.isEnabled = !reencoding
        binding.resolutionSlider.isEnabled = !reencoding
        binding.qualitySlider.isEnabled = !reencoding
        binding.deleteSourceSwitch.isEnabled = !reencoding
        binding.preserveTimestampsSwitch.isEnabled = !reencoding
        invalidateOptionsMenu()
        if (!reencoding) {
            binding.progressIndicator.isVisible = false
            binding.statusText.isVisible = false
        }
    }

    companion object {
        private const val DEFAULT_SUFFIX = "_vfile"
        private const val MIME_HEIC = android.media.MediaFormat.MIMETYPE_IMAGE_ANDROID_HEIC

        private fun extensionForFormat(format: ImageReencodeFormat): String = when (format) {
            ImageReencodeFormat.PNG -> ".png"
            ImageReencodeFormat.JPEG -> ".jpg"
            ImageReencodeFormat.HEIC -> ".heic"
        }
    }
}

private const val EXTRA_FILES = "dev.vcgms.aapp.vaterialfiles.imageedit.extra.FILES"

fun startImageReencodeActivity(context: Context, files: List<FileItem>) {
    val intent = Intent(context, ImageReencodeActivity::class.java)
    intent.putParcelableArrayListExtra(EXTRA_FILES, ArrayList(files))
    context.startActivity(intent)
}

fun startImageReencodeActivity(context: Context, file: FileItem) {
    startImageReencodeActivity(context, listOf(file))
}
