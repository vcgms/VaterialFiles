package dev.vcgms.aapp.vaterialfiles.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Parcelable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.ImageViewState
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import kotlin.math.abs
import kotlinx.parcelize.Parcelize
import dev.vcgms.aapp.vaterialfiles.util.ParcelableState

class SaveStateSubsamplingScaleImageView : SubsamplingScaleImageView {
    private var pendingState: ImageViewState? = null

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

    fun setImageRestoringSavedState(imageSource: ImageSource) {
        setImage(imageSource, pendingState)
        pendingState = null
    }

    override fun onSaveInstanceState(): Parcelable = State(super.onSaveInstanceState(), state)

    override fun onRestoreInstanceState(state: Parcelable) {
        state as State
        super.onRestoreInstanceState(state.superState)
        pendingState = state.state
    }

    // SubsamplingScaleImageView does not itself call requestDisallowInterceptTouchEvent, so inside
    // a ViewPager2 the pager steals the horizontal component of pinch zoom and the small movement
    // between double taps. Coordinate it here based on zoom state: claim the gesture up front (so
    // double tap and pinch reach us), keep it while zoomed or multi-touch (so the image pans/zooms
    // instead of flipping the page), and hand it back to the pager only for a single-finger
    // horizontal drag while not zoomed (so swiping between pages still works).
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val parent = parent
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
            MotionEvent.ACTION_MOVE -> {
                val zoomed = scale > minScale * 1.05f
                if (multiTouch || zoomed) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                } else {
                    val dx = abs(event.x - downX)
                    val dy = abs(event.y - downY)
                    // Single-finger horizontal drag on a fit image: let the pager take over.
                    val handBack = dx > dy && dx > pageSwipeThreshold
                    parent?.requestDisallowInterceptTouchEvent(!handBack)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                multiTouch = false
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return super.onTouchEvent(event)
    }

    @Parcelize
    private class State(val superState: Parcelable?, val state: ImageViewState?) : ParcelableState
}
