package com.android.wallpaper.picker.customization.shared.model

import android.app.WallpaperColors

sealed class WallpaperColorsModel {
    /** State to represent that wallpaper colors has not been populated */
    object Loading : WallpaperColorsModel()
    /** State to represent that wallpaper colors has been populated */
    data class Loaded(val colors: WallpaperColors?) : WallpaperColorsModel()
}
