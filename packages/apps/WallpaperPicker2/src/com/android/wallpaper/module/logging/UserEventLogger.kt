/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.wallpaper.module.logging

import android.content.Intent
import android.stats.style.StyleEnums
import androidx.annotation.IntDef
import com.android.wallpaper.module.WallpaperPersister
import com.android.wallpaper.module.WallpaperPersister.Destination

/** Interface for logging user events in the wallpaper picker. */
interface UserEventLogger {

    /** Logs the current snapshot data, e.g. the currently-set home and lock screen wallpapers. */
    fun logSnapshot()

    /** Logs when the app is launched */
    fun logAppLaunched(launchSource: Intent)

    /** Logs the event when applying a wallpaper. */
    fun logWallpaperApplied(
        collectionId: String?,
        wallpaperId: String?,
        effects: String?,
        @SetWallpaperEntryPoint setWallpaperEntryPoint: Int,
        @WallpaperDestination destination: Int,
    )

    /** Logs the action related to effect. */
    fun logEffectApply(
        effect: String,
        @EffectStatus status: Int,
        timeElapsedMillis: Long,
        resultCode: Int,
    )

    /** Logs the effect probe result. */
    fun logEffectProbe(effect: String, @EffectStatus status: Int)

    /** Logs the effect foreground download event. */
    fun logEffectForegroundDownload(
        effect: String,
        @EffectStatus status: Int,
        timeElapsedMillis: Long,
    )

    /** Logs the event when reset is applied. */
    fun logResetApplied()

    /** Logs when clicking the explore button in the wallpaper information dialog. */
    fun logWallpaperExploreButtonClicked()

    /**
     * Possible actions for cinematic effect. These actions would be used for effect apply, effect
     * probe, effect download.
     */
    @IntDef(
        StyleEnums.EFFECT_PREFERENCE_UNSPECIFIED,
        StyleEnums.EFFECT_APPLIED_ON_SUCCESS,
        StyleEnums.EFFECT_APPLIED_ON_FAILED,
        StyleEnums.EFFECT_APPLIED_OFF,
        StyleEnums.EFFECT_APPLIED_ABORTED,
        StyleEnums.EFFECT_APPLIED_STARTED
    )
    @Retention(AnnotationRetention.SOURCE)
    annotation class EffectStatus

    /**
     * Possible actions for cinematic effect. These actions would be used for effect apply, effect
     * probe, effect download.
     */
    @IntDef(
        StyleEnums.SET_WALLPAPER_ENTRY_POINT_UNSPECIFIED,
        StyleEnums.SET_WALLPAPER_ENTRY_POINT_WALLPAPER_PREVIEW,
        StyleEnums.SET_WALLPAPER_ENTRY_POINT_WALLPAPER_QUICK_SWITCHER,
        StyleEnums.SET_WALLPAPER_ENTRY_POINT_LAUNCHER_WALLPAPER_QUICK_SWITCHER,
        StyleEnums.SET_WALLPAPER_ENTRY_POINT_ROTATION_WALLPAPER,
        StyleEnums.SET_WALLPAPER_ENTRY_POINT_RESET,
        StyleEnums.SET_WALLPAPER_ENTRY_POINT_RESTORE,
    )
    @Retention(AnnotationRetention.SOURCE)
    annotation class SetWallpaperEntryPoint

    @IntDef(
        StyleEnums.WALLPAPER_DESTINATION_UNSPECIFIED,
        StyleEnums.WALLPAPER_DESTINATION_HOME_SCREEN,
        StyleEnums.WALLPAPER_DESTINATION_LOCK_SCREEN,
        StyleEnums.WALLPAPER_DESTINATION_HOME_AND_LOCK_SCREEN,
    )
    @Retention(AnnotationRetention.SOURCE)
    annotation class WallpaperDestination

    companion object {
        @WallpaperDestination
        fun toWallpaperDestinationForLogging(@Destination destination: Int): Int {
            return when (destination) {
                WallpaperPersister.DEST_HOME_SCREEN -> StyleEnums.WALLPAPER_DESTINATION_HOME_SCREEN
                WallpaperPersister.DEST_LOCK_SCREEN -> StyleEnums.WALLPAPER_DESTINATION_LOCK_SCREEN
                WallpaperPersister.DEST_BOTH ->
                    StyleEnums.WALLPAPER_DESTINATION_HOME_AND_LOCK_SCREEN
                else -> StyleEnums.WALLPAPER_DESTINATION_UNSPECIFIED
            }
        }
    }
}
