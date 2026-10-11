package dev.vcgms.aapp.vaterialfiles.coil

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.annotation.ColorInt
import androidx.annotation.IntRange
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.DecodeUtils
import coil3.asImage
import coil3.fetch.ImageFetchResult
import coil3.fetch.Fetcher
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.size.Precision
import coil3.Extras
import kotlin.math.roundToInt

fun ImageRequest.Builder.pdfBackgroundColor(@ColorInt backgroundColor: Int): ImageRequest.Builder =
    apply { extras[PdfPageFetcher.PDF_BACKGROUND_COLOR_KEY] = backgroundColor }

fun ImageRequest.Builder.pdfPageIndex(@IntRange(from = 0) pageIndex: Int): ImageRequest.Builder {
    require(pageIndex >= 0) { "pageIndex must be >= 0." }
    return apply { extras[PdfPageFetcher.PDF_PAGE_INDEX_KEY] = pageIndex }
}

@ColorInt
fun Extras.pdfBackgroundColor(): Int =
    get(PdfPageFetcher.PDF_BACKGROUND_COLOR_KEY) ?: PdfPageFetcher.PDF_BACKGROUND_COLOR_KEY.default

@IntRange(from = 0)
fun Extras.pdfPageIndex(): Int =
    get(PdfPageFetcher.PDF_PAGE_INDEX_KEY) ?: PdfPageFetcher.PDF_PAGE_INDEX_KEY.default

class PdfPageFetcher(
    private val options: Options,
    private val openParcelFileDescriptor: () -> ParcelFileDescriptor
) : Fetcher {
    override suspend fun fetch(): ImageFetchResult =
        openParcelFileDescriptor().use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val pageIndex = options.extras.pdfPageIndex()
                renderer.openPage(pageIndex).use { page ->
                    val srcWidth = page.width
                    check(srcWidth > 0) {
                        "PDF page $pageIndex width $srcWidth isn't greater than 0"
                    }
                    val srcHeight = page.height
                    check(srcWidth > 0) {
                        "PDF page $pageIndex height $srcHeight isn't greater than 0"
                    }
                    val dstWidth = options.size.widthPx(options.scale) { srcWidth }
                    val dstHeight = options.size.heightPx(options.scale) { srcHeight }
                    val rawScale = DecodeUtils.computeSizeMultiplier(
                        srcWidth = srcWidth,
                        srcHeight = srcHeight,
                        dstWidth = dstWidth,
                        dstHeight = dstHeight,
                        scale = options.scale
                    )
                    val scale = if (options.precision == Precision.INEXACT) {
                        rawScale.coerceAtMost(1.0)
                    } else {
                        rawScale
                    }
                    val width = (scale * srcWidth).roundToInt()
                    val height = (scale * srcHeight).roundToInt()
                    val config = Bitmap.Config.ARGB_8888
                    val bitmap = createBitmap(width, height, config)
                    val backgroundColor = options.extras.pdfBackgroundColor()
                    bitmap.eraseColor(backgroundColor)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    ImageFetchResult(
                        bitmap.toDrawable(options.context.resources).asImage(),
                        isSampled = scale < 1.0,
                        dataSource = DataSource.DISK
                    )
                }
            }
        }

    companion object {
        val PDF_BACKGROUND_COLOR_KEY = Extras.Key(Color.WHITE)
        val PDF_PAGE_INDEX_KEY = Extras.Key(0)
    }

    abstract class Factory<T : Any> : Fetcher.Factory<T> {
        override fun create(data: T, options: Options, imageLoader: ImageLoader): Fetcher =
            PdfPageFetcher(options) { openParcelFileDescriptor(data) }

        protected abstract fun openParcelFileDescriptor(data: T): ParcelFileDescriptor
    }
}
