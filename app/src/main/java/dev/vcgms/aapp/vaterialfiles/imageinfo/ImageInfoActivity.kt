package dev.vcgms.aapp.vaterialfiles.imageinfo

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.vcgms.aapp.vaterialfiles.R
import dev.vcgms.aapp.vaterialfiles.app.AppActivity
import dev.vcgms.aapp.vaterialfiles.databinding.ImageInfoActivityBinding
import dev.vcgms.aapp.vaterialfiles.file.FileItem
import dev.vcgms.aapp.vaterialfiles.file.fileSize
import dev.vcgms.aapp.vaterialfiles.file.formatLong
import dev.vcgms.aapp.vaterialfiles.filelist.name
import dev.vcgms.aapp.vaterialfiles.imageedit.startImageReencodeActivity
import dev.vcgms.aapp.vaterialfiles.util.createViewLocation
import dev.vcgms.aapp.vaterialfiles.util.getColorByAttr
import dev.vcgms.aapp.vaterialfiles.util.layoutInflater
import dev.vcgms.aapp.vaterialfiles.util.setForceShowIcons
import dev.vcgms.aapp.vaterialfiles.util.startActivitySafe

// Shows encoding-level information (format, dimensions, color space) and EXIF details of an image
// file, with a re-encode action at the bottom.
class ImageInfoActivity : AppActivity() {
    private lateinit var binding: ImageInfoActivityBinding

    private var file: FileItem? = null

    private var loadJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ImageInfoActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = getString(R.string.image_info_title)
            setDisplayHomeAsUpEnabled(true)
        }
        val file = intent.getParcelableExtra(EXTRA_FILE, FileItem::class.java)
        if (file == null) {
            finish()
            return
        }
        this.file = file
        loadImageInfo(file)
    }

    override fun onDestroy() {
        super.onDestroy()
        loadJob?.cancel()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.image_info, menu)
        menu.setForceShowIcons()
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_reencode -> {
            file?.let { startImageReencodeActivity(this, it) }
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun loadImageInfo(file: FileItem) {
        binding.progress.isVisible = true
        binding.errorText.isVisible = false
        binding.scrollView.isVisible = false
        binding.container.removeAllViews()
        loadJob = lifecycleScope.launch {
            val info = try {
                withContext(Dispatchers.IO) { ImageInfoLoader.load(file.path) }
            } catch (e: Exception) {
                binding.progress.isVisible = false
                binding.errorText.isVisible = true
                return@launch
            }
            binding.progress.isVisible = false
            binding.scrollView.isVisible = true
            renderInfo(file, info)
        }
    }

    private fun renderInfo(file: FileItem, info: ImageInfo) {
        val container = binding.container
        container.removeAllViews()
        addSectionTitle(R.string.image_info_section_file)
        addItem(R.string.image_info_name, file.name)
        val attributes = file.attributes
        if (!attributes.isDirectory) {
            addItem(R.string.image_info_size, attributes.fileSize.formatHumanReadable(this))
        }
        info.format?.let { addItem(R.string.image_info_format, formatDisplayName(it)) }
        if (info.width != null && info.height != null) {
            addItem(
                R.string.file_properties_media_dimensions, getString(
                    R.string.file_properties_media_dimensions_format, info.width, info.height
                )
            )
        }
        info.colorSpace?.let { addItem(R.string.image_info_color_space, it) }

        val exifInfo = info.exifInfo ?: return
        var hasExifItem = false
        fun ensureExifSection() {
            if (!hasExifItem) {
                addSectionTitle(R.string.image_info_section_details)
                hasExifItem = true
            }
        }
        if (exifInfo.dateTimeOriginal != null) {
            ensureExifSection()
            addItem(
                R.string.file_properties_media_date_time, exifInfo.dateTimeOriginal.formatLong()
            )
        }
        if (exifInfo.gpsCoordinates != null) {
            ensureExifSection()
            addItem(
                R.string.file_properties_media_coordinates, getString(
                    R.string.file_properties_media_coordinates_format,
                    exifInfo.gpsCoordinates.first, exifInfo.gpsCoordinates.second
                )
            ) {
                startActivitySafe(
                    Intent::class.createViewLocation(
                        exifInfo.gpsCoordinates.first.toFloat(),
                        exifInfo.gpsCoordinates.second.toFloat(), file.name
                    )
                )
            }
        }
        if (exifInfo.gpsAltitude != null) {
            ensureExifSection()
            addItem(
                R.string.file_properties_image_gps_altitude, getString(
                    R.string.file_properties_image_gps_altitude_format, exifInfo.gpsAltitude
                )
            )
        }
        val equipment = getEquipment(exifInfo.make, exifInfo.model)
        if (equipment != null) {
            ensureExifSection()
            addItem(R.string.file_properties_image_equipment, equipment)
        }
        if (exifInfo.fNumber != null) {
            ensureExifSection()
            addItem(
                R.string.file_properties_image_f_number, getString(
                    R.string.file_properties_image_f_number_format, exifInfo.fNumber
                )
            )
        }
        if (exifInfo.shutterSpeedValue != null) {
            ensureExifSection()
            addItem(
                R.string.file_properties_image_shutter_speed,
                getShutterSpeedText(exifInfo.shutterSpeedValue)
            )
        }
        if (exifInfo.focalLength != null) {
            ensureExifSection()
            addItem(
                R.string.file_properties_image_focal_length, getString(
                    R.string.file_properties_image_focal_length_format, exifInfo.focalLength
                )
            )
        }
        if (exifInfo.photographicSensitivity != null) {
            ensureExifSection()
            addItem(
                R.string.file_properties_image_photographic_sensitivity, getString(
                    R.string.file_properties_image_photographic_sensitivity_format,
                    exifInfo.photographicSensitivity
                )
            )
        }
        if (exifInfo.software != null) {
            ensureExifSection()
            addItem(R.string.file_properties_image_software, exifInfo.software)
        }
        if (exifInfo.description != null) {
            ensureExifSection()
            addItem(R.string.file_properties_image_description, exifInfo.description)
        }
        if (exifInfo.artist != null) {
            ensureExifSection()
            addItem(R.string.file_properties_image_artist, exifInfo.artist)
        }
        if (exifInfo.copyright != null) {
            ensureExifSection()
            addItem(R.string.file_properties_image_copyright, exifInfo.copyright)
        }
    }

    private fun getEquipment(make: String?, model: String?): String? = when {
        make != null && model != null -> {
            if (model.startsWith(make, true)) {
                model
            } else {
                getString(R.string.file_properties_image_equipment_format, make, model)
            }
        }
        make != null -> make
        model != null -> model
        else -> null
    }

    // @see com.android.documentsui.inspector.MediaView.formatShutterSpeed
    private fun getShutterSpeedText(value: Double): String = if (value <= 0) {
        val shutterSpeed = 2.0.pow(-1 * value)
        ((shutterSpeed * 10.0).roundToInt() / 10.0).toString()
    } else {
        val approximateDenominator = 2.0.pow(value).toInt() + 1
        getString(
            R.string.file_properties_image_shutter_speed_with_denominator_format,
            approximateDenominator
        )
    }

    private fun addSectionTitle(@StringRes titleRes: Int) {
        val titleView = TextView(this).apply {
            text = getString(titleRes)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(getColorByAttr(android.R.attr.colorPrimary))
            setPadding(
                0, (16 * resources.displayMetrics.density).toInt(), 0,
                (4 * resources.displayMetrics.density).toInt()
            )
        }
        binding.container.addView(titleView)
    }

    private fun addItem(@StringRes labelRes: Int, value: String, onClick: (() -> Unit)? = null) {
        val density = resources.displayMetrics.density
        val labelView = TextView(this).apply {
            text = getString(labelRes)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(getColorByAttr(android.R.attr.textColorSecondary))
        }
        val valueView = TextView(this).apply {
            text = value
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(getColorByAttr(android.R.attr.textColorPrimary))
            setTextIsSelectable(true)
            if (onClick != null) {
                setTextIsSelectable(false)
                setOnClickListener { onClick() }
            }
        }
        binding.container.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, (8 * density).toInt(), 0, (8 * density).toInt())
                addView(labelView)
                addView(valueView)
            }
        )
    }

    private fun formatDisplayName(mime: String): String = when (mime.lowercase()) {
        "image/jpeg" -> "JPEG"
        "image/jpg" -> "JPEG"
        "image/png" -> "PNG"
        "image/webp" -> "WebP"
        "image/heic" -> "HEIC"
        "image/heif" -> "HEIF"
        "image/gif" -> "GIF"
        "image/bmp" -> "BMP"
        "image/x-ms-bmp" -> "BMP"
        "image/tiff" -> "TIFF"
        "image/x-adobe-dng" -> "DNG"
        "image/jpeg-xl" -> "JPEG XL"
        "image/jxl" -> "JPEG XL"
        "image/svg+xml" -> "SVG"
        else -> mime
    }
}

private const val EXTRA_FILE = "dev.vcgms.aapp.vaterialfiles.imageinfo.extra.FILE"

fun startImageInfoActivity(context: Context, file: FileItem) {
    val intent = Intent(context, ImageInfoActivity::class.java)
    intent.putExtra(EXTRA_FILE, file)
    context.startActivity(intent)
}
