package dev.vcgms.aapp.vaterialfiles.viewer.image

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import com.github.chrisbanes.photoview.PhotoView
import kotlin.math.abs

/**
 * A [PhotoView] that coordinates touch interception with the enclosing ViewPager2.
 *
 * PhotoViewAttacher claims the gesture on ACTION_DOWN but hands it back to the parent
 * (`requestDisallowInterceptTouchEvent(false)`) on every drag while the image sits at a scroll
 * edge, which at fit scale is always the case. As a result a pinch whose first finger drifts
 * horizontally before the second finger lands is stolen by the pager and becomes a page drag.
 *
 * This subclass re-claims the gesture right after the attacher hands it back, unless the motion
 * is clearly a single-finger horizontal page swipe on a fit image, so that both pinch zoom and
 * swiping between pages keep working.
 */
class SaveStatePhotoView : PhotoView {
    private val touchSlop: Int
        get() = ViewConfiguration.get(context).scaledTouchSlop

    // See HdrImageView: hand a single-finger horizontal drag back to the pager only when it
    // clearly exceeds the slop, so a pinch's first-finger drift is not mistaken for a page swipe.
    private val pageSwipeThreshold: Int
        get() = touchSlop * 4

    private var downX = 0f

    private var downY = 0f

    private var multiTouch = false

    constructor(context: Context) : super(context)

    constructor(context: Context, attr: AttributeSet?) : super(context, attr)

    constructor(context: Context, attr: AttributeSet?, defStyle: Int) : super(context, attr, defStyle)

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                multiTouch = false
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                multiTouch = true
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                multiTouch = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        val result = super.dispatchTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_MOVE) {
            // The attacher hands the gesture back to the pager on every drag at the scroll edge.
            // Re-claim it while pinching, and for a single finger on a fit image unless the
            // motion is clearly a horizontal page swipe.
            if (multiTouch) {
                parent?.requestDisallowInterceptTouchEvent(true)
            } else if (scale <= minimumScale * 1.05f) {
                val dx = abs(event.x - downX)
                val dy = abs(event.y - downY)
                if (!(dx > dy && dx > pageSwipeThreshold)) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
            }
        }
        return result
    }
}
