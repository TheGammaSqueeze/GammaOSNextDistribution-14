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

import android.stats.style.StyleEnums
import androidx.annotation.IntDef
import com.android.customization.model.grid.GridOption
import com.android.wallpaper.module.logging.UserEventLogger

/** Extension of [UserEventLogger] that adds ThemePicker specific events. */
interface ThemesUserEventLogger : UserEventLogger {

    fun logThemeColorApplied(@ColorSource source: Int, style: Int, seedColor: Int)

    fun logGridApplied(grid: GridOption)

    fun logClockApplied(clockId: String)

    fun logClockColorApplied(seedColor: Int)

    fun logClockSizeApplied(@ClockSize clockSize: Int)

    fun logThemedIconApplied(useThemeIcon: Boolean)

    fun logLockScreenNotificationApplied(showLockScreenNotifications: Boolean)

    fun logShortcutApplied(shortcut: String, shortcutSlotId: String)

    fun logDarkThemeApplied(useDarkTheme: Boolean)

    @IntDef(
        StyleEnums.COLOR_SOURCE_UNSPECIFIED,
        StyleEnums.COLOR_SOURCE_HOME_SCREEN_WALLPAPER,
        StyleEnums.COLOR_SOURCE_LOCK_SCREEN_WALLPAPER,
        StyleEnums.COLOR_SOURCE_PRESET_COLOR,
    )
    @Retention(AnnotationRetention.SOURCE)
    annotation class ColorSource

    @IntDef(
        StyleEnums.CLOCK_SIZE_UNSPECIFIED,
        StyleEnums.CLOCK_SIZE_DYNAMIC,
        StyleEnums.CLOCK_SIZE_SMALL,
    )
    @Retention(AnnotationRetention.SOURCE)
    annotation class ClockSize

    companion object {
        const val NULL_SEED_COLOR = 0
    }
}
