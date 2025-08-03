/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.tools.flicker.config

import android.tools.flicker.config.appclose.AppClose
import android.tools.flicker.config.applaunch.AppLaunch
import android.tools.flicker.config.foldables.Foldables
import android.tools.flicker.config.gesturenav.GestureNav
import android.tools.flicker.config.ime.Ime
import android.tools.flicker.config.launcher.Launcher
import android.tools.flicker.config.lockscreen.Lockscreen
import android.tools.flicker.config.notification.Notification
import android.tools.flicker.config.others.Others
import android.tools.flicker.config.pip.Pip
import android.tools.flicker.config.settings.Settings
import android.tools.flicker.config.splashscreen.Splashscreen
import android.tools.flicker.config.splitscreen.SplitScreen
import android.tools.flicker.config.suw.Suw
import android.tools.flicker.config.taskbar.Taskbar
import android.tools.flicker.config.wallpaper.Wallpaper

object FlickerServiceConfig {
    val IME_DEFAULT = Ime.SCENARIOS

    val DEFAULT =
        listOf(
                AppClose.SCENARIOS,
                AppLaunch.SCENARIOS,
                Foldables.SCENARIOS,
                GestureNav.SCENARIOS,
                Ime.SCENARIOS,
                Launcher.SCENARIOS,
                Lockscreen.SCENARIOS,
                Notification.SCENARIOS,
                Others.SCENARIOS,
                Pip.SCENARIOS,
                Settings.SCENARIOS,
                Splashscreen.SCENARIOS,
                SplitScreen.SCENARIOS,
                Suw.SCENARIOS,
                Taskbar.SCENARIOS,
                Wallpaper.SCENARIOS
            )
            .flatten()
}
