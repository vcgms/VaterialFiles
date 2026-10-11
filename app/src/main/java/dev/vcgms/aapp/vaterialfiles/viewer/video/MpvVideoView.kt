package dev.vcgms.aapp.vaterialfiles.viewer.video

import android.content.Context
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView

// SurfaceView that hands its surface to libmpv, mirroring mpv-android's approach.
class MpvVideoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : SurfaceView(context, attrs), SurfaceHolder.Callback {

    // Invoked on the main thread when the surface is ready for libmpv output; the first
    // loadfile must wait for this, otherwise the video output has no surface and stays black.
    var onSurfaceReady: (() -> Unit)? = null

    val isSurfaceReady: Boolean
        get() = holder.surface?.isValid == true

    init {
        holder.addCallback(this)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        MpvManager.mpv.attachSurface(holder.surface)
        onSurfaceReady?.invoke()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        MpvManager.mpv.detachSurface()
    }
}
