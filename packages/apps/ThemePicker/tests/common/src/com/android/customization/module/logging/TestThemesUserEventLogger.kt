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
import com.android.customization.model.grid.GridOption
import com.android.customization.module.logging.ThemesUserEventLogger.ClockSize
import com.android.customization.module.logging.ThemesUserEventLogger.ColorSource
import com.android.wallpaper.module.logging.TestUserEventLogger
import javax.inject.Inject
import javax.inject.Singleton

/** Test implementation of [ThemesUserEventLogger]. */
@Singleton
class TestThemesUserEventLogger @Inject constructor() :
    TestUserEventLogger(), ThemesUserEventLogger {
    @ClockSize private var clockSize: Int = StyleEnums.CLOCK_SIZE_UNSPECIFIED
    @ColorSource
    var themeColorSource: Int = StyleEnums.COLOR_SOURCE_UNSPECIFIED
        private set
    var themeColorStyle: Int = -1
        private set
    var themeSeedColor: Int = -1
        private set

    override fun logThemeColorApplied(@ColorSource source: Int, style: Int, seedColor: Int) {
        this.themeColorSource = source
        this.themeColorStyle = style
        this.themeSeedColor = seedColor
    }

    override fun logGridApplied(grid: GridOption) {}

    override fun logClockApplied(clockId: String) {}

    override fun logClockColorApplied(seedColor: Int) {}

    override fun logClockSizeApplied(@ClockSize clockSize: Int) {
        this.clockSize = clockSize
    }

    override fun logThemedIconApplied(useThemeIcon: Boolean) {}

    override fun logLockScreenNotificationApplied(showLockScreenNotifications: Boolean) {}

    override fun logShortcutApplied(shortcut: String, shortcutSlotId: String) {}

    override fun logDarkThemeApplied(useDarkTheme: Boolean) {}

    @ClockSize
    fun getLoggedClockSize(): Int {
        return clockSize
    }
}
