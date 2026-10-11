package dev.vcgms.aapp.vaterialfiles.videoedit

import android.media.MediaCodecList

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
}
