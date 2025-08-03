package com.android.wallpaper.util

import android.content.Context
import android.view.View

object RtlUtils {

    /**
     * Returns whether layout direction is RTL (or false for LTR). Since native RTL layout support
     * was added in API 17, returns false for versions lower than 17.
     */
    @JvmStatic
    fun isRtl(context: Context): Boolean {
        return (context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL)
    }
}
