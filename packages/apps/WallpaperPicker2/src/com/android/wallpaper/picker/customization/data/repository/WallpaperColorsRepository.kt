/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.wallpaper.picker.customization.data.repository

import android.app.WallpaperColors
import com.android.wallpaper.picker.customization.shared.model.WallpaperColorsModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** ViewModel class to keep track of WallpaperColors for the current wallpaper */
class WallpaperColorsRepository {

    private val _homeWallpaperColors =
        MutableStateFlow<WallpaperColorsModel>(WallpaperColorsModel.Loading)
    /** WallpaperColors for the currently set home wallpaper */
    val homeWallpaperColors: StateFlow<WallpaperColorsModel> = _homeWallpaperColors.asStateFlow()

    private val _lockWallpaperColors =
        MutableStateFlow<WallpaperColorsModel>(WallpaperColorsModel.Loading)
    /** WallpaperColors for the currently set lock wallpaper */
    val lockWallpaperColors: StateFlow<WallpaperColorsModel> = _lockWallpaperColors.asStateFlow()

    fun setHomeWallpaperColors(colors: WallpaperColors?) {
        _homeWallpaperColors.value = WallpaperColorsModel.Loaded(colors)
    }

    fun setLockWallpaperColors(colors: WallpaperColors?) {
        _lockWallpaperColors.value = WallpaperColorsModel.Loaded(colors)
    }
}
