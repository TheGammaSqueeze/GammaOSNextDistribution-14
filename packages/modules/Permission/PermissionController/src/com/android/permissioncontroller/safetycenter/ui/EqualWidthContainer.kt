package com.android.permissioncontroller.safetycenter.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View.MeasureSpec.EXACTLY
import android.widget.LinearLayout
import android.widget.Space
import androidx.core.view.children

/**
 * A [LinearLayout] that requires all its children (except [Space]s) to be the same width. The
 * layout is horizontal, unless the buttons don't fit in which case it moves to vertical.
 *
 * Assumes its children are all WRAP_CONTENT width and that it is some fixed width (either
 * MATCH_PARENT or sized by constraint - not WRAP_CONTENT itself).
 */
class EqualWidthContainer @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    LinearLayout(context, attrs) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // super.onMeasure will cause all children to be measured in the default way
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        // Separate children into spaces and non-spaces
        val (spaces, nonSpaceItems) = children.partition { it is Space }
        val neededWidthPerNonSpaceItem =
            try {
                nonSpaceItems.maxOf { it.measuredWidth }
            } catch (e: NoSuchElementException) {
                0
            }
        val neededWidth = neededWidthPerNonSpaceItem * nonSpaceItems.count()

        // Switch between horizontal or vertical layout as needed
        val availableWidth =
            MeasureSpec.getSize(widthMeasureSpec) - spaces.sumOf { it.measuredWidth }
        orientation = if (neededWidth <= availableWidth) HORIZONTAL else VERTICAL

        // Measure again now orientation has changed
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        // Re-measure all the children with EXACT width (using previously calculated height)
        nonSpaceItems.forEach {
            it.measure(
                MeasureSpec.makeMeasureSpec(neededWidthPerNonSpaceItem, EXACTLY),
                MeasureSpec.makeMeasureSpec(it.measuredHeight, EXACTLY)
            )
        }
    }
}
