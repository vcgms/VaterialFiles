package dev.vcgms.aapp.vaterialfiles.viewer.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Gainmap
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.os.Build
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.ViewConfiguration
import android.widget.ImageView
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java8.nio.file.Path
import kotlin.math.abs
import kotlin.math.min

// Full-bitmap view for HDR (Ultra HDR) images: decodes the whole image through ImageDecoder so
// the gain map is preserved (API 34+) and the hardware canvas renders the HDR intent on HDR
// displays. Pinch zoom, pan and double-tap zoom are implemented with a display matrix, because
// tiled decoders (SubsamplingScaleImageView) would strip the gain map.
class HdrImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ImageView(context, attrs) {

    private var bitmap: Bitmap? = null

    private val displayMatrix = Matrix()

    private var baseScale = 1f

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    // A single-finger horizontal drag must clearly exceed this before the gesture is handed back
    // to the pager. It is intentionally much larger than the touch slop so that the small
    // horizontal drift of a pinch's first finger (before the second finger lands) does not get the
    // gesture stolen by the pager, which would intermittently break pinch zoom.
    private val pageSwipeThreshold = ViewConfiguration.get(context).scaledTouchSlop * 4

    private var downX = 0f

    private var downY = 0f

    private var multiTouch = false

    private var onReadyListener: (() -> Unit)? = null

    private var onErrorListener: ((Throwable) -> Unit)? = null

    // Invoked from the decode thread when hardware decoding is unavailable and the image had to
    // be decoded in software instead.
    var onSoftwareFallbackListener: ((String) -> Unit)? = null

    // Invoked on a confirmed single tap, so the host can toggle the viewer chrome like the other
    // image views do.
    var onTapListener: ((android.view.View) -> Unit)? = null

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                displayMatrix.postScale(
                    detector.scaleFactor, detector.scaleFactor, detector.focusX, detector.focusY
                )
                clampMatrix()
                imageMatrix = displayMatrix
                return true
            }
        }
    )

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                // Only pan while zoomed in; a drag on a fitted image should do nothing (and is
                // handed to the pager separately when it is a clear horizontal swipe).
                if (getCurrentScale() <= baseScale * 1.05f) {
                    return false
                }
                displayMatrix.postTranslate(-distanceX, -distanceY)
                clampMatrix()
                imageMatrix = displayMatrix
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                val current = getCurrentScale()
                val target = if (current > baseScale * 1.1f) baseScale else baseScale * 2f
                val scale = target / current
                displayMatrix.postScale(scale, scale, e.x, e.y)
                clampMatrix()
                imageMatrix = displayMatrix
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                onTapListener?.invoke(this@HdrImageView)
                return true
            }
        }
    )

    init {
        scaleType = ScaleType.MATRIX
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val parent = parent
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                multiTouch = false
                // Claim the gesture up front so the pager cannot steal a pinch half-way through;
                // a single-finger horizontal swipe is handed back in ACTION_MOVE so that paging
                // still works when the image is not zoomed.
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                multiTouch = true
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                val zoomed = getCurrentScale() > baseScale * 1.05f
                if (!multiTouch && !zoomed) {
                    val dx = abs(event.x - downX)
                    val dy = abs(event.y - downY)
                    // Single-finger horizontal drag on a fit image: let the pager take over.
                    val handBack = dx > dy && dx > pageSwipeThreshold
                    parent?.requestDisallowInterceptTouchEvent(!handBack)
                } else {
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                multiTouch = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        scaleDetector.onTouchEvent(event)
        // Do not feed the gesture detector while scaling, otherwise two-finger movement also
        // pans the image, which makes pinch zoom feel jittery and unresponsive.
        if (!scaleDetector.isInProgress) {
            gestureDetector.onTouchEvent(event)
        }
        return true
    }

    // Loads the image at the given path, preserving the gain map, and reports completion.
    fun setImagePath(path: Path, onReady: () -> Unit, onError: (Throwable) -> Unit) {
        onReadyListener = onReady
        onErrorListener = onError
        Thread {
            try {
                val decoded = decode(path)
                post {
                    bitmap = decoded
                    setImageBitmap(decoded)
                    resetBaseScale()
                    onReadyListener?.invoke()
                }
            } catch (e: Throwable) {
                post { onErrorListener?.invoke(e) }
            }
        }.start()
    }

    private fun decode(path: Path): Bitmap {
        val file = path.toFile()
        val orientation = readExifOrientation(file)
        val needsTransform = matrixForOrientation(orientation) != null
        var mimeType: String? = null
        var sourceWidth = 0
        var sourceHeight = 0
        // Prefer hardware decoding; only fall back to software when hardware is unavailable, and
        // report the downgrade to the listener.
        var hardwareFailure: String? = null
        var decoded = try {
            decodeWithAllocator(file, ImageDecoder.ALLOCATOR_HARDWARE) { mime, width, height ->
                mimeType = mime
                sourceWidth = width
                sourceHeight = height
            }
        } catch (e: Exception) {
            hardwareFailure = e.toString()
            null
        }
        if (decoded == null) {
            decoded = decodeWithAllocator(
                file, ImageDecoder.ALLOCATOR_SOFTWARE
            ) { mime, width, height ->
                mimeType = mime
                sourceWidth = width
                sourceHeight = height
            }
            hardwareFailure?.let { onSoftwareFallbackListener?.invoke(it) }
        }
        if (decoded.config == Bitmap.Config.HARDWARE) {
            // HARDWARE bitmaps do not render with a display matrix reliably, which breaks
            // fit/center and pinch zoom; convert to software for display, keeping the gain map.
            val software = decoded.copy(Bitmap.Config.ARGB_8888, false)
                ?: throw IllegalStateException("Cannot convert hardware bitmap to software")
            transferGainmap(decoded, software, null)
            decoded.recycle()
            decoded = software
        }
        // ImageDecoder applies EXIF Orientation for JPEG/WebP but only the container transform
        // (irot) for HEIF, so whether the pixels are already upright depends on the format. Detect
        // it rather than guess: rotate only when the decoder did not already apply the EXIF
        // orientation, avoiding both a missed rotation and a double rotation.
        val matrix = if (needsTransform && !decoderAppliedOrientation(
                orientation, decoded.width, decoded.height, sourceWidth, sourceHeight, mimeType
            )
        ) {
            matrixForOrientation(orientation)
        } else {
            null
        }
        return if (matrix == null) {
            decoded
        } else {
            applyOrientation(decoded, matrix)
        }
    }

    private fun decodeWithAllocator(
        file: File,
        allocator: Int,
        onHeader: (mime: String?, width: Int, height: Int) -> Unit
    ): Bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
        onHeader(info.mimeType, info.size.width, info.size.height)
        decoder.setAllocator(allocator)
    }

    // Whether ImageDecoder already baked the EXIF orientation into the decoded pixels. For
    // orientations that swap width/height, compare the decoded size against the header size; this
    // is exact and format-agnostic. Otherwise fall back to the mime rule (ImageDecoder orients
    // JPEG/WebP but not HEIF).
    private fun decoderAppliedOrientation(
        orientation: Int, decodedWidth: Int, decodedHeight: Int,
        sourceWidth: Int, sourceHeight: Int, mime: String?
    ): Boolean {
        val swapsDimensions = orientation == ExifInterface.ORIENTATION_ROTATE_90
            || orientation == ExifInterface.ORIENTATION_ROTATE_270
            || orientation == ExifInterface.ORIENTATION_TRANSPOSE
            || orientation == ExifInterface.ORIENTATION_TRANSVERSE
        if (swapsDimensions && sourceWidth > 0 && sourceHeight > 0 && sourceWidth != sourceHeight) {
            return decodedWidth == sourceHeight && decodedHeight == sourceWidth
        }
        return !isHeifMime(mime)
    }

    private fun isHeifMime(mime: String?): Boolean {
        val value = mime?.lowercase() ?: return false
        return value.contains("heic") || value.contains("heif")
    }

    private fun readExifOrientation(file: File): Int = try {
        ExifInterface(file).getAttributeInt(
            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
        )
    } catch (e: Exception) {
        ExifInterface.ORIENTATION_NORMAL
    }

    // Returns the transform for the given EXIF orientation, or null when the pixels are already
    // upright and no transform is required.
    private fun matrixForOrientation(orientation: Int): Matrix? {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return null
        }
        return matrix
    }

    private fun applyOrientation(source: Bitmap, matrix: Matrix): Bitmap {
        val rotated = Bitmap.createBitmap(
            source, 0, 0, source.width, source.height, matrix, true
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            transferGainmap(source, rotated, matrix)
        }
        if (rotated != source) {
            source.recycle()
        }
        return rotated
    }

    // Rotates the gain map alongside the base image so the Ultra HDR intent survives the transform.
    // A null matrix keeps the contents as-is (identity transform).
    private fun transferGainmap(source: Bitmap, target: Bitmap, matrix: Matrix?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return
        }
        try {
            val gainmap = source.gainmap ?: return
            val contents = gainmap.gainmapContents
            val softwareContents = if (contents.config == Bitmap.Config.HARDWARE) {
                contents.copy(Bitmap.Config.ARGB_8888, false) ?: return
            } else {
                contents
            }
            val transformedContents = if (matrix != null) {
                Bitmap.createBitmap(
                    softwareContents, 0, 0, softwareContents.width, softwareContents.height,
                    matrix, true
                )
            } else {
                // Copy even for the identity case so recycling the source bitmap afterwards cannot
                // free the contents that the new gain map still references.
                softwareContents.copy(Bitmap.Config.ARGB_8888, false) ?: softwareContents
            }
            // The (Gainmap, Bitmap) constructor copies all gain map parameters from the source and
            // swaps in the transformed contents, so the Ultra HDR intent is preserved exactly.
            target.gainmap = Gainmap(gainmap, transformedContents)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun resetBaseScale() {
        val drawable = drawable ?: return
        // Wait until the view is actually laid out; otherwise the base scale would be computed
        // against a zero size and clamp zoom into a degenerate range. onSizeChanged retries.
        if (width == 0 || height == 0) {
            return
        }
        val viewWidth = (width - paddingLeft - paddingRight).coerceAtLeast(1)
        val viewHeight = (height - paddingTop - paddingBottom).coerceAtLeast(1)
        val imageWidth = drawable.intrinsicWidth.coerceAtLeast(1)
        val imageHeight = drawable.intrinsicHeight.coerceAtLeast(1)
        // Fit inside (letterbox), matching PhotoView, so a fitted image has no cropped overflow
        // that would otherwise be mistaken for a zoomed state.
        baseScale = min(viewWidth.toFloat() / imageWidth, viewHeight.toFloat() / imageHeight)
        displayMatrix.reset()
        displayMatrix.setScale(baseScale, baseScale)
        // Center the fitted image within the view.
        displayMatrix.postTranslate(
            (viewWidth - imageWidth * baseScale) / 2f,
            (viewHeight - imageHeight * baseScale) / 2f
        )
        clampMatrix()
        imageMatrix = displayMatrix
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // Recompute the fit scale once the real size is known (covers the case where the bitmap
        // was set before the view was laid out).
        if (drawable != null) {
            resetBaseScale()
        }
    }

    private fun getCurrentScale(): Float {
        val values = FloatArray(9)
        displayMatrix.getValues(values)
        return values[Matrix.MSCALE_X]
    }

    private fun clampMatrix() {
        val values = FloatArray(9)
        displayMatrix.getValues(values)
        val scale = values[Matrix.MSCALE_X].coerceIn(baseScale, baseScale * 8f)
        values[Matrix.MSCALE_X] = scale
        values[Matrix.MSCALE_Y] = scale
        displayMatrix.setValues(values)
    }
}
