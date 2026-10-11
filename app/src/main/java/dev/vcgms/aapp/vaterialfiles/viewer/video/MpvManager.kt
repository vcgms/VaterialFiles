package dev.vcgms.aapp.vaterialfiles.viewer.video

import android.content.Context
import `is`.xyz.mpv.MPV

// Tracks the process-wide libmpv lifecycle so that activity recreation (e.g. rotation) reuses the
// single mpv instance instead of re-creating it.
object MpvManager {
    // 0.1.12's MPV is a plain class; the process-wide instance lives here.
    val mpv = MPV()

    private var isCreated = false

    fun ensureCreated(context: Context) {
        if (isCreated) {
            return
        }
        mpv.create(context)
        // Options must be set before init(): mpv_set_option_string is ignored once mpv is
        // initialized. mediacodec-copy is the mpv-android-recommended hardware decoding mode:
        // it keeps frames readable (seek, frame-step and screenshot all work) and avoids the
        // black-screen issues of direct mediacodec output.
        mpv.setOptionString("vo", "gpu")
        mpv.setOptionString("hwdec", "mediacodec-copy")
        // Allow the video output to drop frames to stay in sync, which avoids the stutter
        // otherwise caused by the display refresh rate.
        mpv.setOptionString("framedrop", "vo")
        // Pin the player volume at 100: loudness is driven by the system media volume (the
        // right-half swipe in the viewer), so the player never attenuates on top of it.
        mpv.setOptionString("volume", "100")
        mpv.init()
        isCreated = true
    }

    // Applies the output color options for the file about to be loaded. The video surface is SDR
    // and the display hint is disabled so HDR-capable panels are never switched into a mode that
    // mis-renders SDR levels. HDR10/HLG/Dolby sources are tone mapped down with a fixed target
    // peak and metadata-based scene peak (auto peak computation overexposes bright or
    // dynamic-metadata sources); SDR sources keep mpv's own defaults so their levels stay
    // untouched.
    fun applyColorOptions(hdr: Boolean) {
        mpv.setPropertyString("target-colorspace-hint", "no")
        if (hdr) {
            mpv.setPropertyString("target-peak", "100")
            mpv.setPropertyString("hdr-compute-peak", "no")
            mpv.setPropertyString("tone-mapping", "bt.2446a")
        } else {
            mpv.setPropertyString("target-peak", "auto")
            mpv.setPropertyString("hdr-compute-peak", "auto")
            mpv.setPropertyString("tone-mapping", "auto")
        }
    }

    fun destroy() {
        if (!isCreated) {
            return
        }
        mpv.destroy()
        isCreated = false
    }
}
