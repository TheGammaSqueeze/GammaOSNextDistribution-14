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
package com.android.customization.module

import android.content.Context
import com.android.wallpaper.module.DefaultWallpaperPreferences

open class DefaultCustomizationPreferences(context: Context) :
    DefaultWallpaperPreferences(context), CustomizationPreferences {

    override fun getSerializedCustomThemes(): String? {
        return sharedPrefs.getString(CustomizationPreferences.KEY_CUSTOM_THEME, null)
    }

    override fun storeCustomThemes(serializedCustomThemes: String) {
        sharedPrefs
            .edit()
            .putString(CustomizationPreferences.KEY_CUSTOM_THEME, serializedCustomThemes)
            .apply()
    }

    override fun getTabVisited(id: String): Boolean {
        return sharedPrefs.getBoolean(CustomizationPreferences.KEY_VISITED_PREFIX + id, false)
    }

    override fun setTabVisited(id: String) {
        sharedPrefs
            .edit()
            .putBoolean(CustomizationPreferences.KEY_VISITED_PREFIX + id, true)
            .apply()
    }

    override fun getThemedIconEnabled(): Boolean {
        return sharedPrefs.getBoolean(CustomizationPreferences.KEY_THEMED_ICON_ENABLED, false)
    }

    override fun setThemedIconEnabled(enabled: Boolean) {
        sharedPrefs
            .edit()
            .putBoolean(CustomizationPreferences.KEY_THEMED_ICON_ENABLED, enabled)
            .apply()
    }
}
