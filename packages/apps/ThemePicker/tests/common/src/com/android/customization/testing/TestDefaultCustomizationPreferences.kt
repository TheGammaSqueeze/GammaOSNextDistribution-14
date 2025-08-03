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
package com.android.customization.testing

import com.android.customization.module.CustomizationPreferences
import com.android.wallpaper.testing.TestWallpaperPreferences
import javax.inject.Inject
import javax.inject.Singleton

/** Test implementation of [CustomizationPreferences]. */
@Singleton
open class TestDefaultCustomizationPreferences @Inject constructor() :
    TestWallpaperPreferences(), CustomizationPreferences {

    private var customThemes: String? = null
    private val tabVisited: MutableSet<String> = HashSet()
    private var themedIconEnabled = false

    override fun getSerializedCustomThemes(): String? {
        return customThemes
    }

    override fun storeCustomThemes(serializedCustomThemes: String) {
        customThemes = serializedCustomThemes
    }

    override fun getTabVisited(id: String): Boolean {
        return tabVisited.contains(id)
    }

    override fun setTabVisited(id: String) {
        tabVisited.add(id)
    }

    override fun getThemedIconEnabled(): Boolean {
        return themedIconEnabled
    }

    override fun setThemedIconEnabled(enabled: Boolean) {
        themedIconEnabled = enabled
    }
}
