package dev.vcgms.aapp.vaterialfiles.viewer.image

import java.io.File
import java8.nio.file.Path
import dev.vcgms.aapp.vaterialfiles.provider.common.newInputStream
import java.io.RandomAccessFile
import java.io.FileOutputStream

// Lightweight detection and extraction for Google Motion Photos and Ultra HDR gain maps, based
// on the XMP packet stored in the JPEG APP1 segment at the start of the file (no native
// dependency; google/libmotionphoto is not required for the read-only use cases here).

private const val XMP_SEARCH_SIZE = 256 * 1024

// Whether the file is a Google Motion Photo (still image with an embedded MP4).
fun Path.isMotionPhoto(): Boolean {
    val xmp = readHeadXmp() ?: return false
    return xmp.contains("GCamera:MotionPhoto")
}

// Whether the file carries an Ultra HDR gain map (XMP namespace hdrgm).
fun Path.hasHdrGainmap(): Boolean {
    val xmp = readHeadXmp() ?: return false
    return xmp.contains("hdrgm:Version")
}

// Extracts the embedded MP4 of a Motion Photo into the given cache directory, or returns null
// when the file is not a Motion Photo or parsing fails.
fun Path.extractMotionPhotoMp4(cacheDir: File): File? {
    val file = toFile()
    val xmp = readHeadXmp() ?: return null
    if (!xmp.contains("GCamera:MotionPhoto")) {
        return null
    }
    val item = Regex("<Container:Item[^>]*Mime=\"video/mp4\"[^>]*>").find(xmp)
        ?: return null
    val length = Regex("Length=\"(\\d+)\"").find(item.value)?.groupValues?.get(1)?.toLong()
        ?: return null
    val padding = Regex("Padding=\"(\\d+)\"").find(item.value)?.groupValues?.get(1)?.toLong()
        ?: 0
    val offset = file.length() - padding - length
    if (offset < 0 || length <= 0 || offset + length > file.length()) {
        return null
    }
    val outFile = File(cacheDir, "motion_photo_${file.name.hashCode()}.mp4")
    RandomAccessFile(file, "r").use { input ->
        input.seek(offset)
        FileOutputStream(outFile).use { output ->
            val buffer = ByteArray(64 * 1024)
            var remaining = length
            while (remaining > 0) {
                val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if (read <= 0) {
                    break
                }
                output.write(buffer, 0, read)
                remaining -= read
            }
        }
    }
    return outFile
}

// Reads the XMP packet from the start of the file (JPEG APP1 segment), if present.
private fun Path.readHeadXmp(): String? {
    return try {
        val bytes = ByteArray(XMP_SEARCH_SIZE)
        val count = newInputStream().use { input ->
            var total = 0
            while (total < bytes.size) {
                val read = input.read(bytes, total, bytes.size - total)
                if (read <= 0) {
                    break
                }
                total += read
            }
            total
        }
        String(bytes, 0, count, Charsets.ISO_8859_1)
    } catch (e: Exception) {
        null
    }
}
