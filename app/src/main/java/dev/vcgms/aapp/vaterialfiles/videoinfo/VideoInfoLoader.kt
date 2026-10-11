package dev.vcgms.aapp.vaterialfiles.videoinfo

import android.media.MediaCodecInfo
import android.media.MediaDataSource
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import java.io.IOException
import java.nio.ByteBuffer
import java8.nio.channels.SeekableByteChannel
import java8.nio.file.Path
import dev.vcgms.aapp.vaterialfiles.fileproperties.extractMetadataNotBlank
import dev.vcgms.aapp.vaterialfiles.provider.common.newByteChannel
import dev.vcgms.aapp.vaterialfiles.provider.document.isDocumentPath
import dev.vcgms.aapp.vaterialfiles.provider.document.resolver.DocumentResolver
import dev.vcgms.aapp.vaterialfiles.provider.linux.isLinuxPath
import dev.vcgms.aapp.vaterialfiles.util.setDataSource

// Media information of a video file, with per-track details read from the container via
// MediaExtractor and container-level metadata from MediaMetadataRetriever.
data class VideoInfo(
    val containerMime: String?,
    val durationMs: Long?,
    val overallBitRate: Long?,
    val videoTracks: List<VideoTrackInfo>,
    val audioTracks: List<AudioTrackInfo>,
)

data class VideoTrackInfo(
    val mime: String,
    val width: Int?,
    val height: Int?,
    val frameRate: Float?,
    val bitRate: Long?,
    val profile: String?,
    val rotation: Int?,
    val colorTransfer: String?,
    val colorStandard: String?,
    val iFrameInterval: Int?,
)

data class AudioTrackInfo(
    val mime: String,
    val sampleRate: Int?,
    val channelCount: Int?,
    val bitRate: Long?,
    val profile: String?,
)

object VideoInfoLoader {

    fun load(path: Path): VideoInfo {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(path)
        val containerMime =
            retriever.extractMetadataNotBlank(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
        val durationMs = retriever.extractMetadataNotBlank(
            MediaMetadataRetriever.METADATA_KEY_DURATION
        )?.toLongOrNull()
        val overallBitRate = retriever.extractMetadataNotBlank(
            MediaMetadataRetriever.METADATA_KEY_BITRATE
        )?.toLongOrNull()
        retriever.release()

        val extractor = MediaExtractor()
        extractor.setDataSource(path)
        try {
            val videoTracks = mutableListOf<VideoTrackInfo>()
            val audioTracks = mutableListOf<AudioTrackInfo>()
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME)?.lowercase() ?: continue
                when {
                    mime.startsWith("video/") -> videoTracks.add(
                        buildVideoTrack(format, mime, readFrameRate(format, extractor, index))
                    )
                    mime.startsWith("audio/") -> audioTracks.add(buildAudioTrack(format, mime))
                }
            }
            return VideoInfo(
                containerMime, durationMs, overallBitRate,
                videoTracks.toList(), audioTracks.toList()
            )
        } finally {
            extractor.release()
        }
    }

    private fun buildVideoTrack(
        format: MediaFormat, mime: String, frameRate: Float?
    ): VideoTrackInfo = VideoTrackInfo(
        mime = mime,
        width = format.numberOrNull(MediaFormat.KEY_WIDTH)?.toInt(),
        height = format.numberOrNull(MediaFormat.KEY_HEIGHT)?.toInt(),
        frameRate = frameRate,
        bitRate = format.numberOrNull(MediaFormat.KEY_BIT_RATE)?.toLong(),
        profile = profileDisplayName(
            mime, format.numberOrNull(MediaFormat.KEY_PROFILE)?.toInt(),
            format.numberOrNull(MediaFormat.KEY_LEVEL)?.toInt()
        ),
        rotation = format.numberOrNull(MediaFormat.KEY_ROTATION)?.toInt(),
        colorTransfer = format.numberOrNull(MediaFormat.KEY_COLOR_TRANSFER)?.toInt()
            ?.let { colorTransferDisplayName(it) },
        colorStandard = format.numberOrNull(MediaFormat.KEY_COLOR_STANDARD)?.toInt()
            ?.let { colorStandardDisplayName(it) },
        iFrameInterval = format.numberOrNull(MediaFormat.KEY_I_FRAME_INTERVAL)?.toInt(),
    )

    private fun buildAudioTrack(format: MediaFormat, mime: String): AudioTrackInfo =
        AudioTrackInfo(
            mime = mime,
            sampleRate = format.numberOrNull(MediaFormat.KEY_SAMPLE_RATE)?.toInt(),
            channelCount = format.numberOrNull(MediaFormat.KEY_CHANNEL_COUNT)?.toInt(),
            bitRate = format.numberOrNull(MediaFormat.KEY_BIT_RATE)?.toLong(),
            profile = format.numberOrNull(MediaFormat.KEY_AAC_PROFILE)?.toInt()
                ?.let { aacProfileDisplayName(it) },
        )

    // The declared frame rate is missing or 0 in some containers; measure it from the median
    // presentation timestamp delta of the first samples in that case.
    private fun readFrameRate(
        format: MediaFormat, extractor: MediaExtractor, trackIndex: Int
    ): Float? {
        val declared = format.numberOrNull(MediaFormat.KEY_FRAME_RATE)?.toFloat()
        if (declared != null && declared > 0) {
            return declared
        }
        return measureFrameRate(extractor, trackIndex)
    }

    private fun measureFrameRate(extractor: MediaExtractor, trackIndex: Int): Float? {
        extractor.selectTrack(trackIndex)
        try {
            val deltas = mutableListOf<Long>()
            var lastPtsUs = -1L
            var sampleCount = 0
            while (sampleCount < FRAME_RATE_SAMPLE_COUNT) {
                val ptsUs = extractor.sampleTime
                if (ptsUs >= 0) {
                    if (lastPtsUs >= 0) {
                        deltas.add(ptsUs - lastPtsUs)
                    }
                    lastPtsUs = ptsUs
                }
                sampleCount++
                if (!extractor.advance()) {
                    break
                }
            }
            if (deltas.isEmpty()) {
                return null
            }
            val medianDeltaUs = deltas.sorted()[deltas.size / 2]
            return if (medianDeltaUs > 0) 1_000_000f / medianDeltaUs else null
        } finally {
            extractor.unselectTrack(trackIndex)
        }
    }

    private fun profileDisplayName(mime: String, profile: Int?, level: Int?): String? {
        if (profile == null) {
            return null
        }
        val profileString = when (mime) {
            "video/avc" -> avcProfileDisplayName(profile)
            "video/hevc" -> hevcProfileDisplayName(profile)
            else -> null
        } ?: return null
        if (mime == "video/avc" && level != null) {
            val levelString = if (level % 10 == 0) {
                "${level / 10}"
            } else {
                "${level / 10}.${level % 10}"
            }
            return "$profileString@$levelString"
        }
        return profileString
    }

    private fun avcProfileDisplayName(profile: Int): String? = when (profile) {
        MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline -> "Baseline"
        MediaCodecInfo.CodecProfileLevel.AVCProfileConstrainedBaseline -> "Constrained Baseline"
        MediaCodecInfo.CodecProfileLevel.AVCProfileMain -> "Main"
        MediaCodecInfo.CodecProfileLevel.AVCProfileExtended -> "Extended"
        MediaCodecInfo.CodecProfileLevel.AVCProfileHigh -> "High"
        MediaCodecInfo.CodecProfileLevel.AVCProfileConstrainedHigh -> "Constrained High"
        MediaCodecInfo.CodecProfileLevel.AVCProfileHigh10 -> "High 10"
        MediaCodecInfo.CodecProfileLevel.AVCProfileHigh422 -> "High 4:2:2"
        MediaCodecInfo.CodecProfileLevel.AVCProfileHigh444 -> "High 4:4:4"
        else -> null
    }

    private fun hevcProfileDisplayName(profile: Int): String? = when (profile) {
        MediaCodecInfo.CodecProfileLevel.HEVCProfileMain -> "Main"
        MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10 -> "Main 10"
        MediaCodecInfo.CodecProfileLevel.HEVCProfileMainStill -> "Main Still"
        MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10 -> "Main 10 HDR10"
        MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10Plus -> "Main 10 HDR10+"
        else -> null
    }

    private fun aacProfileDisplayName(profile: Int): String? = when (profile) {
        MediaCodecInfo.CodecProfileLevel.AACObjectMain -> "AAC Main"
        MediaCodecInfo.CodecProfileLevel.AACObjectLC -> "AAC LC"
        MediaCodecInfo.CodecProfileLevel.AACObjectSSR -> "AAC SSR"
        MediaCodecInfo.CodecProfileLevel.AACObjectLTP -> "AAC LTP"
        MediaCodecInfo.CodecProfileLevel.AACObjectHE -> "HE-AAC"
        MediaCodecInfo.CodecProfileLevel.AACObjectHE_PS -> "HE-AAC v2"
        MediaCodecInfo.CodecProfileLevel.AACObjectELD -> "AAC ELD"
        MediaCodecInfo.CodecProfileLevel.AACObjectLD -> "AAC LD"
        else -> null
    }

    private fun colorTransferDisplayName(value: Int): String? = when (value) {
        MediaFormat.COLOR_TRANSFER_ST2084 -> "PQ (HDR10)"
        MediaFormat.COLOR_TRANSFER_HLG -> "HLG"
        MediaFormat.COLOR_TRANSFER_SDR_VIDEO -> "SDR"
        else -> null
    }

    private fun colorStandardDisplayName(value: Int): String? = when (value) {
        MediaFormat.COLOR_STANDARD_BT2020 -> "BT.2020"
        MediaFormat.COLOR_STANDARD_BT709 -> "BT.709"
        else -> null
    }

    private fun MediaFormat.numberOrNull(key: String): Number? = try {
        if (containsKey(key)) {
            getNumber(key)
        } else {
            null
        }
    } catch (e: Exception) {
        null
    }

    private const val FRAME_RATE_SAMPLE_COUNT = 30
}

fun mimeDisplayName(mime: String): String = when (mime) {
    "video/avc" -> "H.264 (AVC)"
    "video/hevc" -> "H.265 (HEVC)"
    "video/vp9" -> "VP9"
    "video/av01" -> "AV1"
    "video/mp4v-es" -> "MPEG-4 Part 2"
    "video/x-vnd.on2.vp8" -> "VP8"
    "video/3gpp" -> "H.263"
    "audio/mp4a-latm" -> "AAC"
    "audio/opus" -> "Opus"
    "audio/mpeg" -> "MP3"
    "audio/vorbis" -> "Vorbis"
    "audio/flac" -> "FLAC"
    "audio/ac3" -> "AC-3"
    "audio/eac3" -> "E-AC-3"
    "audio/raw" -> "PCM"
    "audio/amr-wb" -> "AMR-WB"
    "audio/amr-nb" -> "AMR-NB"
    "audio/g711-alaw" -> "G.711 A-law"
    "audio/g711-mlaw" -> "G.711 µ-law"
    else -> mime
}

fun MediaExtractor.setDataSource(path: Path) {
    when {
        path.isLinuxPath -> setDataSource(path.toFile().path)
        path.isDocumentPath ->
            DocumentResolver.openParcelFileDescriptor(path as DocumentResolver.Path, "r")
                .use { pfd -> setDataSource(pfd.fileDescriptor) }
        else -> {
            val channel = try {
                path.newByteChannel()
            } catch (e: IOException) {
                throw IllegalArgumentException(e)
            }
            setDataSource(PathMediaDataSource(channel))
        }
    }
}

private class PathMediaDataSource(private val channel: SeekableByteChannel) : MediaDataSource() {
    @Throws(IOException::class)
    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        channel.position(position)
        return channel.read(ByteBuffer.wrap(buffer, offset, size))
    }

    override fun getSize(): Long {
        return channel.size()
    }

    override fun close() {
        channel.close()
    }
}
