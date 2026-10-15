package dev.vcgms.aapp.vaterialfiles.videoedit

import android.media.MediaCodecList
import android.media.MediaFormat
import androidx.media3.common.ColorInfo
import androidx.media3.transformer.EncoderUtil

object MediaCodecUtils {

    // Whether a hardware-accelerated encoder for the given MIME type is available on this device.
    fun hasHardwareEncoder(mime: String): Boolean {
        val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        for (codecInfo in codecList.codecInfos) {
            if (!codecInfo.isEncoder || !codecInfo.isHardwareAccelerated) {
                continue
            }
            if (mime in codecInfo.supportedTypes) {
                return true
            }
        }
        return false
    }

    // Whether any encoder (hardware or software) for the given MIME type is available. Used to
    // gate formats like HEIC whose platform encoder is not present on every device.
    fun hasEncoder(mime: String): Boolean {
        val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        for (codecInfo in codecList.codecInfos) {
            if (!codecInfo.isEncoder) {
                continue
            }
            if (codecInfo.supportedTypes.any { it.equals(mime, ignoreCase = true) }) {
                return true
            }
        }
        return false
    }

    // Whether this device can re-encode the given HDR transfer while KEEPING HDR. Media3 Transformer
    // keeps HDR only when an encoder passes its own EncoderUtil.isHdrEditingSupported check (the
    // "hdr-editing" codec feature plus a 10-bit HDR profile); otherwise, per the Transformer docs, it
    // silently falls back to HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL and writes an SDR file.
    // Mirroring that exact check lets the transcode page tell the user up front instead of producing
    // an unexpected SDR output. isHlg selects the HLG vs PQ (HDR10) source transfer.
    fun canKeepHdr(mime: String, isHlg: Boolean): Boolean {
        val colorInfo = ColorInfo.SDR_BT709_LIMITED.buildUpon()
            .setColorTransfer(
                if (isHlg) MediaFormat.COLOR_TRANSFER_HLG else MediaFormat.COLOR_TRANSFER_ST2084
            )
            .build()
        val codecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        for (codecInfo in codecList.codecInfos) {
            if (!codecInfo.isEncoder) {
                continue
            }
            if (codecInfo.supportedTypes.none { it.equals(mime, ignoreCase = true) }) {
                continue
            }
            if (EncoderUtil.isHdrEditingSupported(codecInfo, mime, colorInfo)) {
                return true
            }
        }
        return false
    }
}
