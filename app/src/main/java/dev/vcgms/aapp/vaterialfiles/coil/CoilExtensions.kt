package dev.vcgms.aapp.vaterialfiles.coil

import android.widget.ImageView
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.request.Disposable
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.placeholder
import coil3.request.target
import dev.vcgms.aapp.vaterialfiles.R

// Coil 3 removed the ImageView.load/dispose extensions; these keep the call sites unchanged.
fun ImageView.load(
    data: Any?,
    imageLoader: ImageLoader = SingletonImageLoader.get(context),
    builder: ImageRequest.Builder.() -> Unit = {}
): Disposable {
    val request = ImageRequest.Builder(context)
        .data(data)
        .target(this)
        .apply(builder)
        .build()
    val disposable = imageLoader.enqueue(request)
    // Keep the disposable so dispose() can cancel the in-flight request.
    setTag(R.id.coil_disposable, disposable)
    return disposable
}

fun ImageView.dispose() {
    (getTag(R.id.coil_disposable) as? Disposable)?.dispose()
    setTag(R.id.coil_disposable, null)
}

fun ImageRequest.Builder.fadeIn(durationMillis: Int): ImageRequest.Builder =
    apply {
        placeholder(android.R.color.transparent)
        crossfade(durationMillis)
    }
