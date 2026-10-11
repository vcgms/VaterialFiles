package dev.vcgms.aapp.vaterialfiles.imageedit

import android.graphics.Bitmap
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java8.nio.file.Path

// Writes EXIF into a HEIC (HEIF/ISOBMFF) file, which androidx.exifinterface cannot do (its
// saveAttributes() only handles JPEG/PNG/WebP). The EXIF TIFF blob is produced by saving the tags
// into a throwaway 1x1 JPEG with ExifInterface (so the TIFF structure is always valid), then
// injected into the HEIF as an 'Exif' item: a new item is added to iinf/iloc (and a 'cdsc'
// reference to iref), and the item payload is appended in a new trailing mdat. The result is
// verified by reading it back with ExifInterface; on any uncertainty the file is left unchanged.
object HeicExifWriter {

    private val PROBE_TAGS = listOf(
        ExifInterface.TAG_DATETIME_ORIGINAL,
        ExifInterface.TAG_DATETIME,
        ExifInterface.TAG_MAKE,
        ExifInterface.TAG_MODEL,
    )

    // Copies all of the source's EXIF tags into the given HEIC file. Best-effort: returns without
    // modifying the file when there is nothing to copy or the structure is not understood.
    fun writeExif(source: Path, heicFile: File, cacheDir: File) {
        try {
            val tags = readAllExifTags(source)
            if (tags.isEmpty()) {
                return
            }
            val blob = buildExifBlob(tags, cacheDir) ?: return
            val original = heicFile.readBytes()
            val injected = inject(original, blob) ?: return
            // Verify by reading the tags back before replacing the original file.
            val candidate = File(cacheDir, "heic_exif_${System.nanoTime()}.heic")
            try {
                FileOutputStream(candidate).use { it.write(injected) }
                if (!verify(candidate, tags)) {
                    return
                }
                FileOutputStream(heicFile).use { it.write(injected) }
            } finally {
                candidate.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun buildExifBlob(tags: Map<String, String>, cacheDir: File): ByteArray? {
        val tempJpeg = File(cacheDir, "heic_exif_src_${System.nanoTime()}.jpg")
        try {
            val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            try {
                FileOutputStream(tempJpeg).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            } finally {
                bitmap.recycle()
            }
            val exif = ExifInterface(tempJpeg.absolutePath)
            for ((tag, value) in tags) {
                exif.setAttribute(tag, value)
            }
            // Pixels are already upright after re-encoding, so the orientation must be normal.
            exif.setAttribute(ExifInterface.TAG_ORIENTATION, "1")
            exif.saveAttributes()
            return extractExifBlob(tempJpeg.readBytes())
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        } finally {
            tempJpeg.delete()
        }
    }

    // Extracts the TIFF/EXIF blob (the bytes after the "Exif\0\0" identifier) from a JPEG's APP1.
    private fun extractExifBlob(jpeg: ByteArray): ByteArray? {
        if (jpeg.size < 4 || jpeg[0] != 0xFF.toByte() || jpeg[1] != 0xD8.toByte()) {
            return null
        }
        var i = 2
        while (i + 4 <= jpeg.size) {
            if (jpeg[i] != 0xFF.toByte()) {
                i++
                continue
            }
            val marker = jpeg[i + 1].toInt() and 0xFF
            if (marker == 0xD8 || marker == 0x01 || (marker in 0xD0..0xD7)) {
                i += 2
                continue
            }
            if (marker == 0xDA) {
                break
            }
            val segLen = ((jpeg[i + 2].toInt() and 0xFF) shl 8) or (jpeg[i + 3].toInt() and 0xFF)
            val segStart = i + 4
            val segEnd = i + 2 + segLen
            if (segEnd > jpeg.size) {
                return null
            }
            if (marker == 0xE1 && segEnd - segStart >= 6 && jpeg[segStart] == 'E'.code.toByte()
                && jpeg[segStart + 1] == 'x'.code.toByte() && jpeg[segStart + 2] == 'i'.code.toByte()
                && jpeg[segStart + 3] == 'f'.code.toByte() && jpeg[segStart + 4] == 0.toByte()
                && jpeg[segStart + 5] == 0.toByte()
            ) {
                return jpeg.copyOfRange(segStart + 6, segEnd)
            }
            i = segEnd
        }
        return null
    }

    private fun verify(file: File, tags: Map<String, String>): Boolean = try {
        val exif = ExifInterface(file.absolutePath)
        // A representative tag reading back correctly proves the injected Exif item is structurally
        // valid and locatable; requiring every tag would reject files where ExifInterface simply
        // normalizes or drops an unsupported tag.
        val probe = PROBE_TAGS.firstOrNull { tags.containsKey(it) }
        if (probe != null) {
            exif.getAttribute(probe) == tags[probe]
        } else {
            tags.any { (tag, value) -> exif.getAttribute(tag) == value }
        }
    } catch (e: Exception) {
        false
    }

    // ---- ISOBMFF injection ----

    private fun inject(heic: ByteArray, blob: ByteArray): ByteArray? {
        // Exif item payload: 4-byte big-endian offset to the TIFF header (which follows immediately),
        // then the TIFF/EXIF blob itself.
        val payload = ByteArray(4 + blob.size)
        payload[3] = 4
        blob.copyInto(payload, 4)
        return try {
            injectInternal(heic, payload)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private class Box(val type: String, val start: Int, val headerLen: Int, val end: Int)

    private fun parseBoxes(data: ByteArray, from: Int, to: Int): List<Box>? {
        val boxes = mutableListOf<Box>()
        var pos = from
        while (pos + 8 <= to) {
            val size = be32(data, pos)
            val type = String(data, pos + 4, 4, Charsets.ISO_8859_1)
            val headerLen: Int
            val end: Int
            when {
                size == 1L -> {
                    val large = be64(data, pos + 8)
                    if (large < 16 || large > Int.MAX_VALUE) return null
                    headerLen = 16
                    end = pos + large.toInt()
                }
                size == 0L -> {
                    headerLen = 8
                    end = to
                }
                size < 8 -> return null
                else -> {
                    headerLen = 8
                    end = pos + size.toInt()
                }
            }
            if (end > to) return null
            boxes.add(Box(type, pos, headerLen, end))
            pos = end
        }
        if (pos != to) return null
        return boxes
    }

    private fun injectInternal(heic: ByteArray, payload: ByteArray): ByteArray? {
        val topBoxes = parseBoxes(heic, 0, heic.size) ?: return null
        val meta = topBoxes.find { it.type == "meta" } ?: return null
        if (meta.headerLen != 8) return null
        val metaVersionFlags = heic.copyOfRange(meta.start + 8, meta.start + 12)
        val childrenStart = meta.start + 12
        val children = parseBoxes(heic, childrenStart, meta.end) ?: return null
        val pitm = children.find { it.type == "pitm" } ?: return null
        val iloc = children.find { it.type == "iloc" } ?: return null
        val iinf = children.find { it.type == "iinf" } ?: return null
        val iref = children.find { it.type == "iref" }

        // Primary item id.
        val pitmVersion = heic[pitm.start + pitm.headerLen].toInt() and 0xFF
        val primaryId = if (pitmVersion == 0) {
            be16(heic, pitm.start + pitm.headerLen + 4)
        } else {
            be32(heic, pitm.start + pitm.headerLen + 4).toInt()
        }

        // Parse iloc.
        val ilocFull = iloc.start + iloc.headerLen
        val ilocVersion = heic[ilocFull].toInt() and 0xFF
        val ilocVersionFlags = heic.copyOfRange(ilocFull, ilocFull + 4)
        var p = ilocFull + 4
        val b0 = heic[p].toInt() and 0xFF
        val offsetSize = b0 ushr 4
        val lengthSize = b0 and 0x0F
        p++
        val b1 = heic[p].toInt() and 0xFF
        val baseOffsetSize = b1 ushr 4
        val indexSize = if (ilocVersion == 1 || ilocVersion == 2) b1 and 0x0F else 0
        p++
        val idWidth = if (ilocVersion < 2) 2 else 4
        val itemCount = if (ilocVersion < 2) {
            val v = be16(heic, p); p += 2; v
        } else {
            val v = be32(heic, p).toInt(); p += 4; v
        }
        if (offsetSize == 0 || lengthSize == 0) return null

        class Extent(val index: Long, val offset: Long, val length: Long)
        class Item(
            val constructionMethod: Int, val itemId: Int, val dataRefIndex: Int,
            val baseOffset: Long, val extents: List<Extent>
        )
        val items = mutableListOf<Item>()
        var maxItemId = 0
        repeat(itemCount) {
            var constructionMethod = 0
            if (ilocVersion == 1 || ilocVersion == 2) {
                constructionMethod = be16(heic, p) and 0x0F
                p += 2
            }
            val itemId = if (ilocVersion < 2) {
                val v = be16(heic, p); p += 2; v
            } else {
                val v = be32(heic, p).toInt(); p += 4; v
            }
            if (itemId > maxItemId) maxItemId = itemId
            val dri = be16(heic, p); p += 2
            val baseOffset = readUint(heic, p, baseOffsetSize); p += baseOffsetSize
            val extentCount = be16(heic, p); p += 2
            val extents = mutableListOf<Extent>()
            repeat(extentCount) {
                var index = 0L
                if (indexSize > 0) {
                    index = readUint(heic, p, indexSize); p += indexSize
                }
                val off = readUint(heic, p, offsetSize); p += offsetSize
                val len = readUint(heic, p, lengthSize); p += lengthSize
                extents.add(Extent(index, off, len))
            }
            // Only file-relative (construction method 0) items are safe to shift.
            if (constructionMethod != 0) return null
            items.add(Item(constructionMethod, itemId, dri, baseOffset, extents))
        }
        if (p != iloc.end) return null

        val newItemId = maxItemId + 1
        if (ilocVersion < 2 && newItemId > 0xFFFF) return null
        val ilocSizeNibbles = byteArrayOf(b0.toByte(), b1.toByte())

        // Parse iinf for entry_count width and the infe version to mirror.
        val iinfFull = iinf.start + iinf.headerLen
        val iinfVersion = heic[iinfFull].toInt() and 0xFF
        val iinfVersionFlags = heic.copyOfRange(iinfFull, iinfFull + 4)
        val iinfCountWidth = if (iinfVersion == 0) 2 else 4
        val iinfEntriesStart = iinf.start + iinf.headerLen + 4 + iinfCountWidth
        val infeBoxes = parseBoxes(heic, iinfEntriesStart, iinf.end) ?: return null
        val infeVersion = infeBoxes.firstOrNull()?.let { heic[it.start + it.headerLen].toInt() and 0xFF } ?: 2
        if (infeVersion < 2) return null

        val irefVersion = iref?.let { heic[it.start + it.headerLen].toInt() and 0xFF }
        val irefVersionFlags = iref?.let { heic.copyOfRange(it.start + it.headerLen, it.start + it.headerLen + 4) }
        val irefChildren = iref?.let { heic.copyOfRange(it.start + it.headerLen + 4, it.end) }

        fun fits(value: Long, size: Int): Boolean =
            size >= 8 || value < (1L shl (size * 8))

        fun buildIloc(delta: Long, exifOffset: Long): ByteArray {
            val w = ByteWriter()
            w.be32(0) // size placeholder
            w.ascii("iloc")
            w.bytes(ilocVersionFlags)
            w.byte(ilocSizeNibbles[0].toInt())
            w.byte(ilocSizeNibbles[1].toInt())
            if (ilocVersion < 2) w.be16((itemCount + 1).toLong()) else w.be32((itemCount + 1).toLong())
            for (item in items) {
                if (ilocVersion == 1 || ilocVersion == 2) w.be16(item.constructionMethod.toLong())
                if (ilocVersion < 2) w.be16(item.itemId.toLong()) else w.be32(item.itemId.toLong())
                w.be16(item.dataRefIndex.toLong())
                var base = item.baseOffset
                if (base != 0L && base >= meta.end) base += delta
                writeUint(w, base, baseOffsetSize)
                w.be16(item.extents.size.toLong())
                for (e in item.extents) {
                    var off = e.offset
                    if (item.baseOffset == 0L && off >= meta.end) off += delta
                    if (indexSize > 0) writeUint(w, e.index, indexSize)
                    writeUint(w, off, offsetSize)
                    writeUint(w, e.length, lengthSize)
                }
            }
            // New Exif item entry.
            if (ilocVersion == 1 || ilocVersion == 2) w.be16(0)
            if (ilocVersion < 2) w.be16(newItemId.toLong()) else w.be32(newItemId.toLong())
            w.be16(0) // data_reference_index
            writeUint(w, 0, baseOffsetSize)
            w.be16(1) // extent_count
            if (indexSize > 0) writeUint(w, 0, indexSize)
            writeUint(w, exifOffset, offsetSize)
            writeUint(w, payload.size.toLong(), lengthSize)
            return w.fixSize()
        }

        fun buildIinf(): ByteArray {
            val w = ByteWriter()
            w.be32(0)
            w.ascii("iinf")
            w.bytes(iinfVersionFlags)
            val newCount = infeBoxes.size + 1
            if (iinfVersion == 0) w.be16(newCount.toLong()) else w.be32(newCount.toLong())
            w.bytes(heic.copyOfRange(iinfEntriesStart, iinf.end))
            // New infe for the Exif item.
            val iw = ByteWriter()
            iw.be32(0)
            iw.ascii("infe")
            iw.byte(infeVersion)
            iw.byte(0); iw.byte(0); iw.byte(0) // flags
            if (infeVersion == 2) iw.be16(newItemId.toLong()) else iw.be32(newItemId.toLong())
            iw.be16(0) // item_protection_index
            iw.ascii("Exif")
            iw.byte(0) // empty item_name (null terminator)
            w.bytes(iw.fixSize())
            return w.fixSize()
        }

        fun buildIref(): ByteArray? {
            if (iref == null || irefVersion == null) return null
            val idWidthIref = if (irefVersion == 0) 2 else 4
            val w = ByteWriter()
            w.be32(0)
            w.ascii("iref")
            w.bytes(irefVersionFlags!!)
            w.bytes(irefChildren!!)
            // 'cdsc' reference: the Exif item describes the primary image item.
            val cw = ByteWriter()
            cw.be32(0)
            cw.ascii("cdsc")
            writeUint(cw, newItemId.toLong(), idWidthIref)
            cw.be16(1)
            writeUint(cw, primaryId.toLong(), idWidthIref)
            w.bytes(cw.fixSize())
            return w.fixSize()
        }

        val irefBuilt = buildIref()

        fun buildMeta(delta: Long, exifOffset: Long): ByteArray {
            val w = ByteWriter()
            w.be32(0)
            w.ascii("meta")
            w.bytes(metaVersionFlags)
            for (child in children) {
                when (child.type) {
                    "iloc" -> w.bytes(buildIloc(delta, exifOffset))
                    "iinf" -> w.bytes(buildIinf())
                    "iref" -> if (irefBuilt != null) w.bytes(irefBuilt) else w.bytes(
                        heic.copyOfRange(child.start, child.end)
                    )
                    else -> w.bytes(heic.copyOfRange(child.start, child.end))
                }
            }
            return w.fixSize()
        }

        // Pass 1 to measure the new meta length (values do not affect field widths).
        val metaProbe = buildMeta(0, 0)
        val oldMetaLen = (meta.end - meta.start).toLong()
        val delta = metaProbe.size.toLong() - oldMetaLen
        // Absolute file offset of the appended mdat's payload.
        val newMdatStart = meta.start.toLong() + metaProbe.size.toLong() + (heic.size - meta.end)
        val exifOffset = newMdatStart + 8
        if (!fits(exifOffset, offsetSize) || !fits(payload.size.toLong(), lengthSize)) return null
        val newMeta = buildMeta(delta, exifOffset)
        if (newMeta.size != metaProbe.size) return null

        val out = ByteWriter()
        out.bytes(heic.copyOfRange(0, meta.start))
        out.bytes(newMeta)
        out.bytes(heic.copyOfRange(meta.end, heic.size))
        out.be32((8 + payload.size).toLong())
        out.ascii("mdat")
        out.bytes(payload)
        return out.toByteArray()
    }

    // ---- binary helpers ----

    private class ByteWriter {
        private val out = ByteArrayOutputStream()
        fun byte(v: Int) { out.write(v and 0xFF) }
        fun bytes(b: ByteArray) { out.write(b) }
        fun ascii(s: String) { out.write(s.toByteArray(Charsets.US_ASCII)) }
        fun be16(v: Long) {
            out.write(((v ushr 8) and 0xFF).toInt())
            out.write((v and 0xFF).toInt())
        }
        fun be32(v: Long) {
            out.write(((v ushr 24) and 0xFF).toInt())
            out.write(((v ushr 16) and 0xFF).toInt())
            out.write(((v ushr 8) and 0xFF).toInt())
            out.write((v and 0xFF).toInt())
        }
        fun toByteArray(): ByteArray = out.toByteArray()
        // Patches the leading 4-byte size field with the total length.
        fun fixSize(): ByteArray {
            val bytes = out.toByteArray()
            val size = bytes.size.toLong()
            bytes[0] = ((size ushr 24) and 0xFF).toByte()
            bytes[1] = ((size ushr 16) and 0xFF).toByte()
            bytes[2] = ((size ushr 8) and 0xFF).toByte()
            bytes[3] = (size and 0xFF).toByte()
            return bytes
        }
    }

    private fun writeUint(w: ByteWriter, value: Long, size: Int) {
        for (i in size - 1 downTo 0) {
            w.byte(((value ushr (8 * i)) and 0xFF).toInt())
        }
    }

    private fun be16(data: ByteArray, pos: Int): Int =
        ((data[pos].toInt() and 0xFF) shl 8) or (data[pos + 1].toInt() and 0xFF)

    private fun be32(data: ByteArray, pos: Int): Long =
        ((data[pos].toLong() and 0xFF) shl 24) or ((data[pos + 1].toLong() and 0xFF) shl 16) or
                ((data[pos + 2].toLong() and 0xFF) shl 8) or (data[pos + 3].toLong() and 0xFF)

    private fun be64(data: ByteArray, pos: Int): Long {
        var v = 0L
        for (i in 0 until 8) {
            v = (v shl 8) or (data[pos + i].toLong() and 0xFF)
        }
        return v
    }

    private fun readUint(data: ByteArray, pos: Int, size: Int): Long {
        if (size == 0) return 0L
        var v = 0L
        for (i in 0 until size) {
            v = (v shl 8) or (data[pos + i].toLong() and 0xFF)
        }
        return v
    }
}
