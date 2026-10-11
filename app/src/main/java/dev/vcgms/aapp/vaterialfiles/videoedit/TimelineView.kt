package dev.vcgms.aapp.vaterialfiles.videoedit

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import java.util.Locale
import kotlin.math.max
import dev.vcgms.aapp.vaterialfiles.util.asColor
import dev.vcgms.aapp.vaterialfiles.util.getColorByAttr
import dev.vcgms.aapp.vaterialfiles.util.withModulatedAlpha

// A self-drawn video timeline that lays out clips sequentially (the timeline time is the
// concatenation of all clips, independent of the source video timeline). The playhead is NOT
// drawn here: it is a fixed line overlaid at the center of the enclosing container by the
// activity, and the timeline content scrolls under it. The view exposes contentX/us conversions
// plus a tap hit-test so the activity can map scroll offsets and taps.
class TimelineView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    var clips: List<ClipEdit> = emptyList()
        set(value) {
            field = value
            totalDurationUs = value.sumOf { it.endUs - it.startUs }
            requestLayout()
            invalidate()
        }

    var selectedClipIndex: Int = -1
        set(value) {
            field = value
            invalidate()
        }

    // Left/right blank space so the content can scroll the playhead (centered in the container)
    // across its whole range. Set by the activity to half of the container width.
    var leadingPaddingPx: Int = 0
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
                invalidate()
            }
        }

    private var totalDurationUs = 0L

    fun totalDurationUs(): Long = totalDurationUs

    // X of a timeline position (playhead-relative combined time), including the leading padding.
    fun contentXForUs(us: Long): Float = leadingPaddingPx + us * pxPerSecond / 1_000_000f

    // Timeline position for a content x, clamped to the timeline range.
    fun usForContentX(x: Float): Long =
        ((x - leadingPaddingPx) * 1_000_000f / pxPerSecond).toLong()
            .coerceIn(0L, totalDurationUs)

    // The clip index whose block contains the point, or -1.
    fun clipIndexAt(x: Float, y: Float): Int {
        val clipTop = paddingPx + rulerHeightPx
        if (y < clipTop || y > clipTop + clipTrackHeightPx) {
            return -1
        }
        val us = usForContentX(x)
        var accumulated = 0L
        clips.forEachIndexed { index, clip ->
            val clipDuration = clip.endUs - clip.startUs
            if (us >= accumulated && us < accumulated + clipDuration) {
                return index
            }
            accumulated += clipDuration
        }
        return -1
    }

    private val density: Float
        get() = resources.displayMetrics.density

    private val paddingPx: Float
        get() = PADDING_DP * density

    private val rulerHeightPx: Float
        get() = RULER_HEIGHT_DP * density

    private val clipTrackHeightPx: Float
        get() = CLIP_TRACK_HEIGHT_DP * density

    private val pxPerSecond: Float
        get() = PX_PER_SECOND_DP * density

    private val primaryColor: Int
        get() = context.getColorByAttr(android.R.attr.colorPrimary)

    private val secondaryTextColor: Int
        get() = context.getColorByAttr(android.R.attr.textColorSecondary)

    private val surfaceColor: Int
        get() = context.getColorByAttr(com.google.android.material.R.attr.colorSurface)

    private val rulerPaint = Paint().apply { isAntiAlias = true }

    private val textPaint = Paint().apply {
        isAntiAlias = true
        textSize = 10f * resources.displayMetrics.scaledDensity
    }

    private val clipPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    private val clipStrokePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
    }

    private val clipRect = RectF()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val contentWidth =
            (leadingPaddingPx * 2 + totalDurationUs * pxPerSecond / 1_000_000f).toInt()
        val width = max(contentWidth, MeasureSpec.getSize(widthMeasureSpec))
        // The height is always self-computed: the enclosing scroll view's wrap_content +
        // fillViewport combination would otherwise pass an EXACTLY full-screen height.
        val height = (paddingPx * 2 + rulerHeightPx + clipTrackHeightPx).toInt()
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(surfaceColor)
        val rulerBottom = paddingPx + rulerHeightPx
        // Ruler ticks over the whole combined timeline.
        textPaint.color = secondaryTextColor
        rulerPaint.color = secondaryTextColor
        val secondsTotal = totalDurationUs / 1_000_000f
        var second = 0
        while (second <= secondsTotal) {
            val x = contentXForUs(second * 1_000_000L)
            val isMajor = second % 5 == 0
            val tickTop = if (isMajor) paddingPx else paddingPx + rulerHeightPx * 0.4f
            rulerPaint.strokeWidth = if (isMajor) 2f * density else density
            canvas.drawLine(x, tickTop, x, rulerBottom, rulerPaint)
            if (isMajor && second > 0) {
                canvas.drawText(
                    formatTick(second), x + 4f * density, paddingPx + rulerHeightPx * 0.7f,
                    textPaint
                )
            }
            second++
        }
        // Clip blocks, laid out sequentially.
        val clipTop = rulerBottom
        val clipBottom = clipTop + clipTrackHeightPx
        var accumulated = 0L
        clips.forEachIndexed { index, clip ->
            val left = contentXForUs(accumulated)
            accumulated += clip.endUs - clip.startUs
            val right = contentXForUs(accumulated)
            val selected = index == selectedClipIndex
            clipRect.set(left, clipTop + 4f * density, right, clipBottom - 4f * density)
            clipPaint.color =
                primaryColor.asColor().withModulatedAlpha(if (selected) 0.28f else 0.14f).value
            canvas.drawRoundRect(clipRect, 6f * density, 6f * density, clipPaint)
            clipStrokePaint.color = primaryColor
            clipStrokePaint.strokeWidth = if (selected) 2.5f * density else 1.5f * density
            canvas.drawRoundRect(clipRect, 6f * density, 6f * density, clipStrokePaint)
            if (right - left > 24f * density) {
                val label = (index + 1).toString()
                textPaint.color = primaryColor
                val textWidth = textPaint.measureText(label)
                canvas.drawText(
                    label, (left + right) / 2 - textWidth / 2,
                    clipTop + clipTrackHeightPx * 0.65f, textPaint
                )
            }
        }
    }

    private fun formatTick(seconds: Int): String =
        if (seconds < 60) {
            "${seconds}s"
        } else {
            String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60)
        }

    companion object {
        private const val PADDING_DP = 8f
        private const val RULER_HEIGHT_DP = 22f
        private const val CLIP_TRACK_HEIGHT_DP = 40f
        private const val PX_PER_SECOND_DP = 40f
    }
}
