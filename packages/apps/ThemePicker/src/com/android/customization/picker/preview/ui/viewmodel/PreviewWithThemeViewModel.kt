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

package com.android.customization.picker.preview.ui.viewmodel

import android.app.WallpaperColors
import android.os.Bundle
import com.android.customization.model.themedicon.domain.interactor.ThemedIconInteractor
import com.android.customization.picker.color.domain.interactor.ColorPickerInteractor
import com.android.wallpaper.model.WallpaperInfo
import com.android.wallpaper.module.CustomizationSections
import com.android.wallpaper.picker.customization.domain.interactor.WallpaperInteractor
import com.android.wallpaper.picker.customization.ui.viewmodel.ScreenPreviewViewModel
import com.android.wallpaper.util.PreviewUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** A ThemePicker version of the [ScreenPreviewViewModel] */
class PreviewWithThemeViewModel(
    previewUtils: PreviewUtils,
    initialExtrasProvider: () -> Bundle? = { null },
    wallpaperInfoProvider: suspend (forceReload: Boolean) -> WallpaperInfo?,
    onWallpaperColorChanged: (WallpaperColors?) -> Unit = {},
    wallpaperInteractor: WallpaperInteractor,
    private val themedIconInteractor: ThemedIconInteractor? = null,
    colorPickerInteractor: ColorPickerInteractor? = null,
    screen: CustomizationSections.Screen,
) :
    ScreenPreviewViewModel(
        previewUtils,
        initialExtrasProvider,
        wallpaperInfoProvider,
        onWallpaperColorChanged,
        wallpaperInteractor,
        screen,
    ) {
    override fun workspaceUpdateEvents(): Flow<Boolean>? = themedIconInteractor?.isActivated

    private val wallpaperIsLoading = super.isLoading

    override val isLoading: Flow<Boolean> =
        colorPickerInteractor?.let {
            combine(wallpaperIsLoading, colorPickerInteractor.isApplyingSystemColor) {
                wallpaperIsLoading,
                colorIsLoading ->
                wallpaperIsLoading || colorIsLoading
            }
        }
            ?: wallpaperIsLoading
}
