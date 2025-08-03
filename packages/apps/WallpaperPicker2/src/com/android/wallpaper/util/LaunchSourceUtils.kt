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
 *
 */

package com.android.wallpaper.util

/** Constants for launch source */
object LaunchSourceUtils {
    const val WALLPAPER_LAUNCH_SOURCE = "com.android.wallpaper.LAUNCH_SOURCE"
    const val LAUNCH_SOURCE_LAUNCHER = "app_launched_launcher"
    const val LAUNCH_SOURCE_SETTINGS = "app_launched_settings"
    const val LAUNCH_SOURCE_SUW = "app_launched_suw"
    const val LAUNCH_SOURCE_TIPS = "app_launched_tips"
    const val LAUNCH_SOURCE_DEEP_LINK = "app_launched_deeplink"
    const val LAUNCH_SETTINGS_SEARCH = ":settings:fragment_args_key"
    const val LAUNCH_SOURCE_SETTINGS_HOMEPAGE = "is_from_settings_homepage"
    const val LAUNCH_SOURCE_KEYGUARD = "app_launched_keyguard"
}
