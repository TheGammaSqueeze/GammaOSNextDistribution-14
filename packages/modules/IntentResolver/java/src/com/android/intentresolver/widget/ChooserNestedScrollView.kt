package com.android.intentresolver.widget

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import androidx.core.view.ScrollingView
import androidx.core.view.marginBottom
import androidx.core.view.marginLeft
import androidx.core.view.marginRight
import androidx.core.view.marginTop
import androidx.core.widget.NestedScrollView

/**
 * A narrowly tailored [NestedScrollView] to be used inside [ResolverDrawerLayout] and help to
 * orchestrate content preview scrolling. It expects one [LinearLayout] child with
 * [LinearLayout.VERTICAL] orientation. If the child has more than one child, the first its child
 * will be made scrollable (it is expected to be a content preview view).
 */
class ChooserNestedScrollView : NestedScrollView {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int
    ) : super(context, attrs, defStyleAttr)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val content =
            getChildAt(0) as? LinearLayout ?: error("Exactly one child, LinerLayout, is expected")
        require(content.orientation == LinearLayout.VERTICAL) { "VERTICAL orientation is expected" }
        require(MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY) {
            "Expected to have an exact width"
        }

        val lp = content.layoutParams ?: error("LayoutParams is missing")
        val contentWidthSpec =
            getChildMeasureSpec(
                widthMeasureSpec,
                paddingLeft + content.marginLeft + content.marginRight + paddingRight,
                lp.width
            )
        val contentHeightSpec =
            getChildMeasureSpec(
                heightMeasureSpec,
                paddingTop + content.marginTop + content.marginBottom + paddingBottom,
                lp.height
            )
        content.measure(contentWidthSpec, contentHeightSpec)

        if (content.childCount > 1) {
            // We expect that the first child should be scrollable up
            val child = content.getChildAt(0)
            val height =
                MeasureSpec.getSize(heightMeasureSpec) +
                    child.measuredHeight +
                    child.marginTop +
                    child.marginBottom

            content.measure(
                contentWidthSpec,
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.getMode(heightMeasureSpec))
            )
        }
        setMeasuredDimension(
            MeasureSpec.getSize(widthMeasureSpec),
            minOf(
                MeasureSpec.getSize(heightMeasureSpec),
                paddingTop +
                    content.marginTop +
                    content.measuredHeight +
                    content.marginBottom +
                    paddingBottom
            )
        )
    }

    override fun onNestedPreScroll(target: View, dx: Int, dy: Int, consumed: IntArray, type: Int) {
        // let the parent scroll
        super.onNestedPreScroll(target, dx, dy, consumed, type)
        // scroll ourselves, if recycler has not scrolled
        val delta = dy - consumed[1]
        if (delta > 0 && target is ScrollingView && !target.canScrollVertically(-1)) {
            val preScrollY = scrollY
            scrollBy(0, delta)
            consumed[1] += scrollY - preScrollY
        }
    }
}
