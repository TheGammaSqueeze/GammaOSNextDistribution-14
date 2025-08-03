/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.customization.module.logging

import android.app.WallpaperManager
import android.content.Intent
import android.stats.style.StyleEnums.APP_LAUNCHED
import android.stats.style.StyleEnums.CLOCK_APPLIED
import android.stats.style.StyleEnums.CLOCK_COLOR_APPLIED
import android.stats.style.StyleEnums.CLOCK_SIZE_APPLIED
import android.stats.style.StyleEnums.DARK_THEME_APPLIED
import android.stats.style.StyleEnums.GRID_APPLIED
import android.stats.style.StyleEnums.LAUNCHED_CROP_AND_SET_ACTION
import android.stats.style.StyleEnums.LAUNCHED_DEEP_LINK
import android.stats.style.StyleEnums.LAUNCHED_KEYGUARD
import android.stats.style.StyleEnums.LAUNCHED_LAUNCHER
import android.stats.style.StyleEnums.LAUNCHED_LAUNCH_ICON
import android.stats.style.StyleEnums.LAUNCHED_PREFERENCE_UNSPECIFIED
import android.stats.style.StyleEnums.LAUNCHED_SETTINGS
import android.stats.style.StyleEnums.LAUNCHED_SETTINGS_SEARCH
import android.stats.style.StyleEnums.LAUNCHED_SUW
import android.stats.style.StyleEnums.LAUNCHED_TIPS
import android.stats.style.StyleEnums.LOCK_SCREEN_NOTIFICATION_APPLIED
import android.stats.style.StyleEnums.RESET_APPLIED
import android.stats.style.StyleEnums.SHORTCUT_APPLIED
import android.stats.style.StyleEnums.SNAPSHOT
import android.stats.style.StyleEnums.THEMED_ICON_APPLIED
import android.stats.style.StyleEnums.THEME_COLOR_APPLIED
import android.stats.style.StyleEnums.WALLPAPER_APPLIED
import android.stats.style.StyleEnums.WALLPAPER_DESTINATION_HOME_AND_LOCK_SCREEN
import android.stats.style.StyleEnums.WALLPAPER_DESTINATION_HOME_SCREEN
import android.stats.style.StyleEnums.WALLPAPER_DESTINATION_LOCK_SCREEN
import android.stats.style.StyleEnums.WALLPAPER_EFFECT_APPLIED
import android.stats.style.StyleEnums.WALLPAPER_EFFECT_FG_DOWNLOAD
import android.stats.style.StyleEnums.WALLPAPER_EFFECT_PROBE
import android.stats.style.StyleEnums.WALLPAPER_EXPLORE
import android.text.TextUtils
import com.android.customization.model.color.ColorCustomizationManager
import com.android.customization.model.grid.GridOption
import com.android.customization.module.logging.ThemesUserEventLogger.ClockSize
import com.android.customization.module.logging.ThemesUserEventLogger.ColorSource
import com.android.wallpaper.module.WallpaperPreferences
import com.android.wallpaper.module.logging.UserEventLogger.EffectStatus
import com.android.wallpaper.module.logging.UserEventLogger.SetWallpaperEntryPoint
import com.android.wallpaper.module.logging.UserEventLogger.WallpaperDestination
import com.android.wallpaper.util.ActivityUtils
import com.android.wallpaper.util.LaunchSourceUtils
import javax.inject.Inject
import javax.inject.Singleton

/** StatsLog-backed implementation of [ThemesUserEventLogger]. */
@Singleton
class ThemesUserEventLoggerImpl
@Inject
constructor(
    private val preferences: WallpaperPreferences,
    private val colorManager: ColorCustomizationManager,
    private val appSessionId: AppSessionId,
) : ThemesUserEventLogger {

    override fun logSnapshot() {
        SysUiStatsLogger(SNAPSHOT)
            .setWallpaperCategoryHash(preferences.getHomeCategoryHash())
            .setWallpaperIdHash(preferences.getHomeWallpaperIdHash())
            .setEffectIdHash(preferences.getHomeWallpaperEffectsIdHash())
            .setLockWallpaperCategoryHash(preferences.getLockCategoryHash())
            .setLockWallpaperIdHash(preferences.getLockWallpaperIdHash())
            .setLockEffectIdHash(preferences.getLockWallpaperEffectsIdHash())
            .setColorSource(colorManager.currentColorSourceForLogging)
            .setColorVariant(colorManager.currentStyleForLogging)
            .setSeedColor(colorManager.currentSeedColorForLogging)
            .log()
    }

    override fun logAppLaunched(launchSource: Intent) {
        SysUiStatsLogger(APP_LAUNCHED)
            .setAppSessionId(appSessionId.createNewId().getId())
            .setLaunchedPreference(launchSource.getAppLaunchSource())
            .log()
    }

    override fun logWallpaperApplied(
        collectionId: String?,
        wallpaperId: String?,
        effects: String?,
        @SetWallpaperEntryPoint setWallpaperEntryPoint: Int,
        @WallpaperDestination destination: Int,
    ) {
        val categoryHash = getIdHashCode(collectionId)
        val wallpaperIdHash = getIdHashCode(wallpaperId)
        val isHomeWallpaperSet =
            destination == WALLPAPER_DESTINATION_HOME_SCREEN ||
                destination == WALLPAPER_DESTINATION_HOME_AND_LOCK_SCREEN
        val isLockWallpaperSet =
            destination == WALLPAPER_DESTINATION_LOCK_SCREEN ||
                destination == WALLPAPER_DESTINATION_HOME_AND_LOCK_SCREEN
        SysUiStatsLogger(WALLPAPER_APPLIED)
            .setAppSessionId(appSessionId.getId())
            .setWallpaperCategoryHash(if (isHomeWallpaperSet) categoryHash else 0)
            .setWallpaperIdHash(if (isHomeWallpaperSet) wallpaperIdHash else 0)
            .setEffectIdHash(if (isHomeWallpaperSet) getIdHashCode(effects) else 0)
            .setLockWallpaperCategoryHash(if (isLockWallpaperSet) categoryHash else 0)
            .setLockWallpaperIdHash(if (isLockWallpaperSet) wallpaperIdHash else 0)
            .setLockEffectIdHash(if (isLockWallpaperSet) getIdHashCode(effects) else 0)
            .setSetWallpaperEntryPoint(setWallpaperEntryPoint)
            .setWallpaperDestination(destination)
            .log()
    }

    override fun logEffectApply(
        effect: String,
        @EffectStatus status: Int,
        timeElapsedMillis: Long,
        resultCode: Int
    ) {
        SysUiStatsLogger(WALLPAPER_EFFECT_APPLIED)
            .setAppSessionId(appSessionId.getId())
            .setEffectPreference(status)
            .setEffectIdHash(getIdHashCode(effect))
            .setTimeElapsed(timeElapsedMillis)
            .setEffectResultCode(resultCode)
            .log()
    }

    override fun logEffectProbe(effect: String, @EffectStatus status: Int) {
        SysUiStatsLogger(WALLPAPER_EFFECT_PROBE)
            .setAppSessionId(appSessionId.getId())
            .setEffectPreference(status)
            .setEffectIdHash(getIdHashCode(effect))
            .log()
    }

    override fun logEffectForegroundDownload(
        effect: String,
        @EffectStatus status: Int,
        timeElapsedMillis: Long
    ) {
        SysUiStatsLogger(WALLPAPER_EFFECT_FG_DOWNLOAD)
            .setAppSessionId(appSessionId.getId())
            .setEffectPreference(status)
            .setEffectIdHash(getIdHashCode(effect))
            .setTimeElapsed(timeElapsedMillis)
            .log()
    }

    override fun logResetApplied() {
        SysUiStatsLogger(RESET_APPLIED).setAppSessionId(appSessionId.getId()).log()
    }

    override fun logWallpaperExploreButtonClicked() {
        SysUiStatsLogger(WALLPAPER_EXPLORE).setAppSessionId(appSessionId.getId()).log()
    }

    override fun logThemeColorApplied(
        @ColorSource source: Int,
        style: Int,
        seedColor: Int,
    ) {
        SysUiStatsLogger(THEME_COLOR_APPLIED)
            .setAppSessionId(appSessionId.getId())
            .setColorSource(source)
            .setColorVariant(style)
            .setSeedColor(seedColor)
            .log()
    }

    override fun logGridApplied(grid: GridOption) {
        SysUiStatsLogger(GRID_APPLIED)
            .setAppSessionId(appSessionId.getId())
            .setLauncherGrid(grid.getLauncherGridInt())
            .log()
    }

    override fun logClockApplied(clockId: String) {
        SysUiStatsLogger(CLOCK_APPLIED)
            .setAppSessionId(appSessionId.getId())
            .setClockPackageHash(getIdHashCode(clockId))
            .log()
    }

    override fun logClockColorApplied(seedColor: Int) {
        SysUiStatsLogger(CLOCK_COLOR_APPLIED)
            .setAppSessionId(appSessionId.getId())
            .setSeedColor(seedColor)
            .log()
    }

    override fun logClockSizeApplied(@ClockSize clockSize: Int) {
        SysUiStatsLogger(CLOCK_SIZE_APPLIED)
            .setAppSessionId(appSessionId.getId())
            .setClockSize(clockSize)
            .log()
    }

    override fun logThemedIconApplied(useThemeIcon: Boolean) {
        SysUiStatsLogger(THEMED_ICON_APPLIED)
            .setAppSessionId(appSessionId.getId())
            .setToggleOn(useThemeIcon)
            .log()
    }

    override fun logLockScreenNotificationApplied(showLockScreenNotifications: Boolean) {
        SysUiStatsLogger(LOCK_SCREEN_NOTIFICATION_APPLIED)
            .setAppSessionId(appSessionId.getId())
            .setToggleOn(showLockScreenNotifications)
            .log()
    }

    override fun logShortcutApplied(shortcut: String, shortcutSlotId: String) {
        SysUiStatsLogger(SHORTCUT_APPLIED)
            .setAppSessionId(appSessionId.getId())
            .setShortcut(shortcut)
            .setShortcutSlotId(shortcutSlotId)
            .log()
    }

    override fun logDarkThemeApplied(useDarkTheme: Boolean) {
        SysUiStatsLogger(DARK_THEME_APPLIED)
            .setAppSessionId(appSessionId.getId())
            .setToggleOn(useDarkTheme)
            .log()
    }

    /**
     * The grid integer depends on the column and row numbers. For example: 4x5 is 405 13x37 is 1337
     * The upper limit for the column / row count is 99.
     */
    private fun GridOption.getLauncherGridInt(): Int {
        return cols * 100 + rows
    }

    private fun Intent.getAppLaunchSource(): Int {
        return if (hasExtra(LaunchSourceUtils.WALLPAPER_LAUNCH_SOURCE)) {
            when (getStringExtra(LaunchSourceUtils.WALLPAPER_LAUNCH_SOURCE)) {
                LaunchSourceUtils.LAUNCH_SOURCE_LAUNCHER -> LAUNCHED_LAUNCHER
                LaunchSourceUtils.LAUNCH_SOURCE_SETTINGS -> LAUNCHED_SETTINGS
                LaunchSourceUtils.LAUNCH_SOURCE_SUW -> LAUNCHED_SUW
                LaunchSourceUtils.LAUNCH_SOURCE_TIPS -> LAUNCHED_TIPS
                LaunchSourceUtils.LAUNCH_SOURCE_DEEP_LINK -> LAUNCHED_DEEP_LINK
                LaunchSourceUtils.LAUNCH_SOURCE_KEYGUARD -> LAUNCHED_KEYGUARD
                else -> LAUNCHED_PREFERENCE_UNSPECIFIED
            }
        } else if (ActivityUtils.isLaunchedFromSettingsSearch(this)) {
            LAUNCHED_SETTINGS_SEARCH
        } else if (action != null && action == WallpaperManager.ACTION_CROP_AND_SET_WALLPAPER) {
            LAUNCHED_CROP_AND_SET_ACTION
        } else if (categories != null && categories.contains(Intent.CATEGORY_LAUNCHER)) {
            LAUNCHED_LAUNCH_ICON
        } else {
            LAUNCHED_PREFERENCE_UNSPECIFIED
        }
    }

    /** If not set, the output hash is 0. */
    private fun WallpaperPreferences.getHomeCategoryHash(): Int {
        return getIdHashCode(getHomeWallpaperCollectionId())
    }

    /** If not set, the output hash is 0. */
    private fun WallpaperPreferences.getHomeWallpaperIdHash(): Int {
        val remoteId = getHomeWallpaperRemoteId()
        val wallpaperId =
            if (!TextUtils.isEmpty(remoteId)) remoteId else getHomeWallpaperServiceName()
        return getIdHashCode(wallpaperId)
    }

    /** If not set, the output hash is 0. */
    private fun WallpaperPreferences.getLockCategoryHash(): Int {
        return getIdHashCode(getLockWallpaperCollectionId())
    }

    /** If not set, the output hash is 0. */
    private fun WallpaperPreferences.getLockWallpaperIdHash(): Int {
        val remoteId = getLockWallpaperRemoteId()
        val wallpaperId =
            if (!TextUtils.isEmpty(remoteId)) remoteId else getLockWallpaperServiceName()
        return getIdHashCode(wallpaperId)
    }

    /** If not set, the output hash is 0. */
    private fun WallpaperPreferences.getHomeWallpaperEffectsIdHash(): Int {
        return getIdHashCode(getHomeWallpaperEffects())
    }

    /** If not set, the output hash is 0. */
    private fun WallpaperPreferences.getLockWallpaperEffectsIdHash(): Int {
        return getIdHashCode(getLockWallpaperEffects())
    }

    private fun getIdHashCode(id: String?): Int {
        return id?.hashCode() ?: 0
    }
}
