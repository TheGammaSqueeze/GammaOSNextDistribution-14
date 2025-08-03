package com.android.intentresolver.widget

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.Gravity
import android.widget.TextView

/**
 * A TextView that supports a badge at the end of the text. If the text, when centered in the view,
 * leaves enough room for the badge, the badge is just displayed at the end of the view. Otherwise,
 * the necessary amount of space for the badge is reserved and the text gets centered in the
 * remaining free space.
 */
class BadgeTextView : TextView {
    constructor(context: Context) : this(context, null)

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int
    ) : this(context, attrs, defStyleAttr, 0)

    constructor(
        context: Context?,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int
    ) : super(context, attrs, defStyleAttr, defStyleRes) {
        super.setGravity(Gravity.CENTER)
        defaultPaddingLeft = paddingLeft
        defaultPaddingRight = paddingRight
    }

    private var defaultPaddingLeft = 0
    private var defaultPaddingRight = 0

    override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
        super.setPadding(left, top, right, bottom)
        defaultPaddingLeft = paddingLeft
        defaultPaddingRight = paddingRight
    }

    override fun setPaddingRelative(start: Int, top: Int, end: Int, bottom: Int) {
        super.setPaddingRelative(start, top, end, bottom)
        defaultPaddingLeft = paddingLeft
        defaultPaddingRight = paddingRight
    }

    /** Sets end-sided badge. */
    var badgeDrawable: Drawable? = null
        set(value) {
            if (field !== value) {
                field = value
                super.setBackground(value)
            }
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.setPadding(defaultPaddingLeft, paddingTop, defaultPaddingRight, paddingBottom)
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val badge = badgeDrawable ?: return
        if (badge.intrinsicWidth <= paddingEnd) return
        var maxLineWidth = 0f
        for (i in 0 until layout.lineCount) {
            maxLineWidth = maxOf(maxLineWidth, layout.getLineWidth(i))
        }
        val sideSpace = (measuredWidth - maxLineWidth) / 2
        if (sideSpace < badge.intrinsicWidth) {
            super.setPaddingRelative(
                paddingStart,
                paddingTop,
                paddingEnd + badge.intrinsicWidth - sideSpace.toInt(),
                paddingBottom
            )
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    override fun setBackground(background: Drawable?) {
        badgeDrawable = null
        super.setBackground(background)
    }

    override fun setGravity(gravity: Int): Unit = error("Not supported")
}
