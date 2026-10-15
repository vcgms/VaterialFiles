package dev.vcgms.aapp.vaterialfiles.imageedit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java8.nio.file.Path
import kotlin.math.max
import kotlin.math.roundToInt
import dev.vcgms.aapp.vaterialfiles.provider.common.delete
import dev.vcgms.aapp.vaterialfiles.provider.common.deleteIfExists
import dev.vcgms.aapp.vaterialfiles.provider.common.exists
import dev.vcgms.aapp.vaterialfiles.provider.common.getLastModifiedTime
import dev.vcgms.aapp.vaterialfiles.provider.common.newInputStream
import dev.vcgms.aapp.vaterialfiles.provider.common.newOutputStream
import dev.vcgms.aapp.vaterialfiles.provider.common.setLastModifiedTime

// Re-encodes a still image into PNG (lossless), JPEG or HEIC (both lossy with a quality setting).
// The source is decoded to a software bitmap and its EXIF orientation is baked in. The encoded
// bytes are written to a cache file first (MediaMuxer for HEIC needs a real file, and writing EXIF
// back also needs one), then copied to the target path so that document/SAF destinations work too.
object ImageReencoder {

    // Only one image is decoded/encoded at a time to bound peak memory when a batch is submitted;
    // acquire() is interruptible so a cancelled job does not stay blocked.
    private val encodeSemaphore = java.util.concurrent.Semaphore(1)

    @Throws(IOException::class, InterruptedException::class)
    fun encode(context: Context, params: ImageReencodeParams) {
        val inPlace = params.sourcePath == params.outputPath
        if (!inPlace && params.outputPath.exists()) {
            throw IOException("Output file already exists: ${params.outputPath.fileName}")
        }
        encodeSemaphore.acquire()
        try {
            encodeInternal(context, params, inPlace)
        } finally {
            encodeSemaphore.release()
        }
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun encodeInternal(context: Context, params: ImageReencodeParams, inPlace: Boolean) {
        val source = params.sourcePath
        // Capture the source modification time up front: for an in-place re-encode the source is
        // overwritten before we would otherwise read it.
        val sourceLastModifiedTime = if (params.preserveExif) {
            try {
                source.getLastModifiedTime()
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        } else {
            null
        }
        val decoded = decodeBitmap(source) ?: throw IOException("Failed to decode source image")
        val oriented = applyExifOrientation(decoded, source)
        val rotated = if (params.rotationDegrees % 360 != 0) {
            rotateBitmap(oriented, params.rotationDegrees)
        } else {
            oriented
        }
        val bitmap = if (params.resolutionPercent in 1..99) {
            scaleBitmap(rotated, params.resolutionPercent)
        } else {
            rotated
        }
        val tempFile = File(context.cacheDir, "image_reencode_${System.nanoTime()}${extension(params.format)}")
        var outputComplete = false
        try {
            if (Thread.interrupted()) {
                throw InterruptedException()
            }
            when (params.format) {
                ImageReencodeFormat.PNG ->
                    FileOutputStream(tempFile).use {
                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                    }
                ImageReencodeFormat.JPEG ->
                    FileOutputStream(tempFile).use {
                        bitmap.compress(
                            Bitmap.CompressFormat.JPEG, params.quality.coerceIn(1, 100), it
                        )
                    }
                ImageReencodeFormat.HEIC ->
                    HeicEncoder.encode(bitmap, params.quality.coerceIn(1, 100), tempFile)
            }
            if (params.preserveExif) {
                if (params.format == ImageReencodeFormat.HEIC) {
                    // androidx ExifInterface cannot write HEIF; inject the Exif item directly.
                    HeicExifWriter.writeExif(source, tempFile, context.cacheDir)
                } else {
                    copyExif(source, tempFile)
                }
            }
            if (Thread.interrupted()) {
                throw InterruptedException()
            }
            tempFile.inputStream().use { input ->
                params.outputPath.newOutputStream().buffered().use { output ->
                    input.copyTo(output)
                }
            }
            outputComplete = true
            if (params.preserveExif && sourceLastModifiedTime != null) {
                try {
                    params.outputPath.setLastModifiedTime(sourceLastModifiedTime)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            // An in-place re-encode overwrites the source itself, so there is nothing to delete.
            if (params.deleteSource && !inPlace) {
                source.delete()
            }
        } catch (e: InterruptedException) {
            tempFile.delete()
            throw e
        } catch (e: IOException) {
            e.printStackTrace()
            tempFile.delete()
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            tempFile.delete()
            throw IOException(e)
        } finally {
            for (candidate in setOf(decoded, oriented, rotated, bitmap)) {
                if (!candidate.isRecycled) {
                    candidate.recycle()
                }
            }
            tempFile.delete()
            // A failed or cancelled re-encode must not leave a half-written output file behind. The
            // source is never deleted here: an in-place re-encode writes over it, so only clean up a
            // separate output file.
            if (!outputComplete && !inPlace) {
                params.outputPath.deleteIfExists()
            }
        }
    }

    // Copies all of the source's EXIF tags into the freshly encoded file (Orientation is reset to
    // normal because the pixels are already baked upright). Best-effort: failures are non-fatal.
    private fun copyExif(source: Path, target: File) {
        try {
            val tags = readAllExifTags(source)
            if (tags.isEmpty()) {
                return
            }
            val targetExif = ExifInterface(target.absolutePath)
            for ((tag, value) in tags) {
                targetExif.setAttribute(tag, value)
            }
            targetExif.setAttribute(ExifInterface.TAG_ORIENTATION, "1")
            targetExif.saveAttributes()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Decodes the full-resolution image. BitmapFactory handles the common formats and yields a
    // software bitmap; ImageDecoder is the fallback for formats it cannot parse.
    private fun decodeBitmap(path: Path): Bitmap? {
        try {
            path.newInputStream().buffered().use {
                BitmapFactory.decodeStream(it)?.let { bitmap -> return bitmap }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return try {
            val bytes = path.newInputStream().buffered().use { it.readBytes() }
            val source = ImageDecoder.createSource(bytes)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun applyExifOrientation(bitmap: Bitmap, path: Path): Bitmap {
        val orientation = try {
            path.newInputStream().buffered().use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                )
            }
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return try {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: Exception) {
            e.printStackTrace()
            bitmap
        }
    }

    private fun rotateBitmap(source: Bitmap, degrees: Int): Bitmap {
        val matrix = Matrix()
        matrix.postRotate((((degrees % 360) + 360) % 360).toFloat())
        return try {
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        } catch (e: Exception) {
            e.printStackTrace()
            source
        }
    }

    private fun scaleBitmap(source: Bitmap, percent: Int): Bitmap {
        val width = max(1, (source.width * percent / 100f).roundToInt())
        val height = max(1, (source.height * percent / 100f).roundToInt())
        if (width == source.width && height == source.height) {
            return source
        }
        return try {
            Bitmap.createScaledBitmap(source, width, height, true)
        } catch (e: Exception) {
            e.printStackTrace()
            source
        }
    }

    // The upright (post-EXIF-orientation) pixel dimensions of the source, matching what the encoder
    // produces before any user rotation/scaling; used to preview the output resolution. Returns
    // null when the bounds cannot be read.
    fun uprightSize(path: Path): Pair<Int, Int>? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            path.newInputStream().buffered().use {
                BitmapFactory.decodeStream(it, null, options)
            }
            var width = options.outWidth
            var height = options.outHeight
            if (width <= 0 || height <= 0) {
                return null
            }
            val orientation = path.newInputStream().buffered().use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                )
            }
            if (orientation == ExifInterface.ORIENTATION_ROTATE_90
                || orientation == ExifInterface.ORIENTATION_ROTATE_270
                || orientation == ExifInterface.ORIENTATION_TRANSPOSE
                || orientation == ExifInterface.ORIENTATION_TRANSVERSE
            ) {
                val temp = width
                width = height
                height = temp
            }
            width to height
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun extension(format: ImageReencodeFormat): String = when (format) {
        ImageReencodeFormat.PNG -> ".png"
        ImageReencodeFormat.JPEG -> ".jpg"
        ImageReencodeFormat.HEIC -> ".heic"
    }
}
