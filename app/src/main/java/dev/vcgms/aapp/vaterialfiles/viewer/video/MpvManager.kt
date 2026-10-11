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
        // The video surface is SDR: tell mpv the output colorspace explicitly so HDR10/HLG/
        // Dolby Vision content is tone mapped down instead of being output raw (which shows
        // washed-out or wrong colors on an SDR surface). A fixed target peak and metadata-based
        // scene peak avoid overexposure caused by auto peak computation.
        mpv.setOptionString("target-colorspace-hint", "bt.709")
        mpv.setOptionString("target-peak", "100")
        mpv.setOptionString("hdr-compute-peak", "no")
        mpv.setOptionString("tone-mapping", "bt.2446a")
        mpv.init()
        isCreated = true
    }

    fun destroy() {
        if (!isCreated) {
            return
        }
        mpv.destroy()
        isCreated = false
    }
}
