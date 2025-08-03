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
package com.android.wallpaper.picker.di.modules

import android.text.TextUtils
import com.android.customization.model.color.ColorCustomizationManager
import com.android.customization.model.color.ColorOptionsProvider.COLOR_SOURCE_PRESET
import com.android.wallpaper.picker.customization.data.repository.WallpaperRepository
import com.android.wallpaper.picker.customization.domain.interactor.WallpaperInteractor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** This class provides the singleton scoped interactors for theme picker. */
@InstallIn(SingletonComponent::class)
@Module
internal object InteractorModule {

    @Provides
    @Singleton
    fun provideWallpaperInteractor(
        wallpaperRepository: WallpaperRepository,
        colorCustomizationManager: ColorCustomizationManager,
    ): WallpaperInteractor {
        return WallpaperInteractor(wallpaperRepository) {
            TextUtils.equals(colorCustomizationManager.currentColorSource, COLOR_SOURCE_PRESET)
        }
    }
}
