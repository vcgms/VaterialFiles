package dev.vcgms.aapp.vaterialfiles.imageedit

import androidx.exifinterface.media.ExifInterface
import java.lang.reflect.Modifier
import java8.nio.file.Path
import dev.vcgms.aapp.vaterialfiles.provider.common.newInputStream

// All EXIF tag names exposed by androidx ExifInterface (public static final String TAG_* fields),
// discovered via reflection so the list stays complete across library versions.
internal val ALL_EXIF_TAGS: List<String> by lazy {
    ExifInterface::class.java.fields
        .filter {
            Modifier.isStatic(it.modifiers) && it.type == String::class.java
                && it.name.startsWith("TAG_")
        }
        .mapNotNull { field ->
            try {
                field.get(null) as? String
            } catch (e: Exception) {
                null
            }
        }
}

// Tags that must not be copied verbatim: the orientation is renormalized to 1 (the pixels are
// already baked upright by the re-encode), and the thumbnail interchange offsets are structural
// values that ExifInterface recomputes on save.
private val EXCLUDED_EXIF_TAGS = setOf(
    ExifInterface.TAG_ORIENTATION,
    ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT,
    ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT_LENGTH,
)

// Reads every non-blank EXIF attribute from the source image (excluding the tags above).
internal fun readAllExifTags(source: Path): Map<String, String> {
    val tags = LinkedHashMap<String, String>()
    try {
        source.newInputStream().buffered().use {
            val exif = ExifInterface(it)
            for (tag in ALL_EXIF_TAGS) {
                if (tag in EXCLUDED_EXIF_TAGS) {
                    continue
                }
                val value = exif.getAttribute(tag)
                if (!value.isNullOrEmpty()) {
                    tags[tag] = value
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return tags
}
