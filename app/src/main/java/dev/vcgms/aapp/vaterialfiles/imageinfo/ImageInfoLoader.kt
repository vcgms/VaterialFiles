package dev.vcgms.aapp.vaterialfiles.imageinfo

import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import androidx.exifinterface.media.ExifInterface
import java8.nio.file.Path
import kotlin.math.max
import dev.vcgms.aapp.vaterialfiles.fileproperties.image.ExifInfo
import dev.vcgms.aapp.vaterialfiles.fileproperties.image.getAttributeNotBlank
import dev.vcgms.aapp.vaterialfiles.fileproperties.image.getAttributeDoubleOrNull
import dev.vcgms.aapp.vaterialfiles.fileproperties.image.getAttributeIntOrNull
import dev.vcgms.aapp.vaterialfiles.fileproperties.image.gpsAltitude
import dev.vcgms.aapp.vaterialfiles.fileproperties.image.inferDateTimeOriginal
import dev.vcgms.aapp.vaterialfiles.provider.common.getLastModifiedTime
import dev.vcgms.aapp.vaterialfiles.provider.common.newInputStream
import dev.vcgms.aapp.vaterialfiles.provider.linux.isLinuxPath

// Encoding-level and EXIF information of an image file. The container header (format, dimensions
// and color space) is read via ImageDecoder; EXIF tags are read separately with ExifInterface.
data class ImageInfo(
    val format: String?,
    val width: Int?,
    val height: Int?,
    val colorSpace: String?,
    val exifInfo: ExifInfo?,
)

object ImageInfoLoader {

    fun load(path: Path): ImageInfo {
        var format: String? = null
        var width: Int? = null
        var height: Int? = null
        var colorSpace: String? = null
        // For local files, read the header (and color space) through ImageDecoder while forcing a
        // tiny sample size so the pixel decode stays cheap; the bitmap is discarded immediately.
        if (path.isLinuxPath) {
            try {
                val source = ImageDecoder.createSource(path.toFile())
                val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    format = info.mimeType
                    val size = info.size
                    if (size.width > 0 && size.height > 0) {
                        width = size.width
                        height = size.height
                    }
                    colorSpace = info.colorSpace?.name
                    val maxEdge = max(size.width, size.height)
                    if (maxEdge > HEADER_SAMPLE_EDGE) {
                        decoder.setTargetSampleSize(maxEdge / HEADER_SAMPLE_EDGE)
                    }
                }
                bitmap.recycle()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (format == null && width == null) {
            // Fall back to a bounds-only BitmapFactory decode, which is streaming (no full-file
            // buffering) and yields the dimensions and declared mime for any path type.
            try {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                path.newInputStream().buffered().use {
                    BitmapFactory.decodeStream(it, null, options)
                }
                format = options.outMimeType
                if (options.outWidth > 0 && options.outHeight > 0) {
                    width = options.outWidth
                    height = options.outHeight
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return ImageInfo(format, width, height, colorSpace, loadExifInfo(path))
    }

    private fun loadExifInfo(path: Path): ExifInfo? = try {
        val lastModifiedTime = path.getLastModifiedTime().toInstant()
        path.newInputStream().buffered().use {
            val exifInterface = ExifInterface(it)
            val dateTimeOriginal = exifInterface.inferDateTimeOriginal(lastModifiedTime)
            val gpsCoordinates = exifInterface.latLong?.let { it[0] to it[1] }
            val gpsAltitude = exifInterface.gpsAltitude
            val make = exifInterface.getAttributeNotBlank(ExifInterface.TAG_MAKE)
            val model = exifInterface.getAttributeNotBlank(ExifInterface.TAG_MODEL)
            val fNumber = exifInterface.getAttributeDoubleOrNull(ExifInterface.TAG_F_NUMBER)
            val shutterSpeedValue =
                exifInterface.getAttributeDoubleOrNull(ExifInterface.TAG_SHUTTER_SPEED_VALUE)
            val focalLength = exifInterface.getAttributeDoubleOrNull(ExifInterface.TAG_FOCAL_LENGTH)
            val photographicSensitivity =
                exifInterface.getAttributeIntOrNull(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY)
            val software = exifInterface.getAttributeNotBlank(ExifInterface.TAG_SOFTWARE)
            val description =
                exifInterface.getAttributeNotBlank(ExifInterface.TAG_IMAGE_DESCRIPTION)
                    ?: exifInterface.getAttributeNotBlank(ExifInterface.TAG_USER_COMMENT)
            val artist = exifInterface.getAttributeNotBlank(ExifInterface.TAG_ARTIST)
            val copyright = exifInterface.getAttributeNotBlank(ExifInterface.TAG_COPYRIGHT)
            ExifInfo(
                dateTimeOriginal, gpsCoordinates, gpsAltitude, make, model, fNumber,
                shutterSpeedValue, focalLength, photographicSensitivity, software, description,
                artist, copyright
            )
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }

    // Target edge (in pixels) used when decoding just to read the header.
    private const val HEADER_SAMPLE_EDGE = 8
}
