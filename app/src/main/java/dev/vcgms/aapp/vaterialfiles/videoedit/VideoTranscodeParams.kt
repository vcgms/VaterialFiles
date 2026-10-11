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
    // Encoder profile level (AVC profiles, or an HEVC Main 10 profile when keeping HDR); null
    // lets the encoder pick its default.
    val profileLevel: Int?,
    // Whether the source carries HDR; only an HDR source that is not kept as HDR needs tone
    // mapping, so an SDR source never goes through the HDR-to-SDR (OpenGL) pipeline.
    val sourceIsHdr: Boolean,
    // Keep the source's HDR transfer characteristics instead of tone mapping to SDR.
    val hdrKeep: Boolean,
    // Delete the source file once the re-encoded output has been written successfully.
    val deleteSource: Boolean,
)
