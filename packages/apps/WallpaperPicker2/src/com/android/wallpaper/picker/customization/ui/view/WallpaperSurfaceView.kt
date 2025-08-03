package com.android.wallpaper.picker.customization.ui.view

import android.content.Context
import android.util.AttributeSet
import android.view.SurfaceView
import android.view.View
import android.view.ViewParent
import androidx.core.view.isVisible
import com.android.wallpaper.R

/**
 * A SurfaceView to handle a few things specific to live wallpaper previews.
 *
 * <p>Visibility updates: this is an interim solution until b/287618705 is fixed.
 *
 * <p>We need to notify the wallpaper engine when its surface changes visibility. ViewTreeObserver
 * changes aren't reliable for tab switches (why?) so we need to override onVisibilityChanged.
 * onVisibilityChanged happens when visibility changes, but doesn't always reflect tab visibility,
 * so we look up the tree until we find the owning scroll container and reports its visibility
 * instead.
 */
class WallpaperSurfaceView(context: Context, attrs: AttributeSet? = null) :
    SurfaceView(context, attrs) {

    var visibilityCallback: (visible: Boolean) -> Unit = {}
    val visibilityViewIds = listOf(R.id.lock_scroll_container, R.id.home_scroll_container)
    var scrollContainer: View? = null

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        val visible = scrollContainer?.isVisible ?: false
        visibilityCallback(visible)
    }

    init {
        addOnAttachStateChangeListener(
            object : OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) {
                    var parent: ViewParent? = v.parent
                    while (parent != null && scrollContainer == null) {
                        parent = parent.parent
                        val view = parent as? View
                        if (view?.id in visibilityViewIds) {
                            scrollContainer = view
                        }
                    }
                }

                override fun onViewDetachedFromWindow(v: View) {
                    // Do nothing
                }
            }
        )
    }
}
