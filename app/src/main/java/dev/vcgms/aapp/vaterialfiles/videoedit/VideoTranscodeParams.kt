package dev.vcgms.aapp.vaterialfiles.videoedit

import java8.nio.file.Path

// Bitrate mode values matching MediaFormat.BITRATE_MODE_* (the SDK no longer exposes those
// constants at compileSdk 37).
const val VIDEO_BITRATE_MODE_VBR = 1
const val VIDEO_BITRATE_MODE_CBR = 2

// Parameters of a re-encoding job; null fields mean "keep the default" (e.g. the source value).
data class VideoTranscodeParams(
    val sourcePath: Path,
    val outputPath: Path,
    val videoMime: String,
    // Target short side in pixels (aspect ratio is preserved); null keeps the source resolution.
    val shortSide: Int?,
    val bitRate: Int?,
    val frameRate: Int?,
    val bitrateMode: Int?,
    val avcProfile: Int?,
    // Timeline clips; when non-null and non-empty the export concatenates the clips in order.
    val clips: List<ClipEdit>? = null,
)
