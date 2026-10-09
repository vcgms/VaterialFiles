package dev.vcgms.aapp.vaterialfiles.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

// A full-height horizontal bar that fills from the start according to fraction (0..1), drawn
// behind file list items to visualize an item's share of its parent directory's total size.
class StorageAnalysisBar : View {
    var fraction: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidate()
        }

    var barColor: Int = 0
        set(value) {
            field = value
            invalidate()
        }

    private val paint = Paint()

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (fraction > 0f && barColor != 0) {
            paint.color = barColor
            canvas.drawRect(0f, 0f, width * fraction, height.toFloat(), paint)
        }
    }
}
