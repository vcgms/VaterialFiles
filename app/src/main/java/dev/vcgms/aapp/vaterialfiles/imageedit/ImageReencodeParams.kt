package dev.vcgms.aapp.vaterialfiles.imageedit

import java8.nio.file.Path

// Target output formats for image re-encoding. PNG is lossless; JPEG and HEIC are lossy and honor
// the quality setting.
enum class ImageReencodeFormat {
    PNG, JPEG, HEIC
}

data class ImageReencodeParams(
    val sourcePath: Path,
    val outputPath: Path,
    val format: ImageReencodeFormat,
    // 1..100, ignored for the lossless PNG format.
    val quality: Int,
    // Extra clockwise rotation (0/90/180/270) applied on top of the baked-in EXIF orientation.
    val rotationDegrees: Int,
    // Output resolution as a percentage (1..100) of the upright, post-rotation source dimensions;
    // 100 keeps the original resolution.
    val resolutionPercent: Int,
    // Delete the source file once the re-encoded output has been written successfully.
    val deleteSource: Boolean,
    // Copy all of the source's EXIF tags into the output (with Orientation renormalized to 1,
    // since the pixels are already baked upright) and keep the source file's modification time on
    // the output file. When false, no EXIF is written.
    val preserveExif: Boolean,
)
