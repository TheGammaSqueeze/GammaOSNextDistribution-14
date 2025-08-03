package com.android.wallpaper.testing

import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import com.android.wallpaper.module.DrawableLayerResolver

/** Test implementation of [DrawableLayerResolver] */
class TestDrawableLayerResolver : DrawableLayerResolver {
    override fun resolveLayer(layerDrawable: LayerDrawable?): Drawable {
        return layerDrawable!!.getDrawable(0)
    }
}
