package com.android.wallpaper.picker.data

import android.app.WallpaperManager.FLAG_LOCK
import android.app.WallpaperManager.FLAG_SYSTEM

enum class Destination(val value: Int) {
    /** Depicts when the current wallpaperModel object is not a currently applied wallpaper */
    NOT_APPLIED(0),

    /**
     * Depicts when the current wallpaperModel object is equivalent to FLAG_SYSTEM#WallpaperManager
     * and the wallpaperModel describes a wallpaper that's only applied on the homescreen
     */
    APPLIED_TO_SYSTEM(FLAG_SYSTEM),

    /**
     * Depicts when the current wallpaperModel object is equivalent to FLAG_LOCK#WallpaperManager
     * and wallpapermodel describes a wallpaper that's only applied on the lockscreen
     */
    APPLIED_TO_LOCK(FLAG_LOCK),

    /**
     * Depicts when the current wallpaperModel object is equivalent to FLAG_LOCK+FLAG_SYSTEM and
     * wallpapermodel describes a wallpaper that's applied on both lockscreen and homescreen
     */
    APPLIED_TO_SYSTEM_LOCK(FLAG_SYSTEM or FLAG_LOCK)
}
