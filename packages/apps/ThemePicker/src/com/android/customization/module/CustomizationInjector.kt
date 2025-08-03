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
import android.content.res.Resources
import androidx.activity.ComponentActivity
import com.android.customization.picker.clock.domain.interactor.ClockPickerInteractor
import com.android.customization.picker.clock.ui.view.ClockViewFactory
import com.android.customization.picker.clock.ui.viewmodel.ClockCarouselViewModel
import com.android.customization.picker.clock.ui.viewmodel.ClockSettingsViewModel
import com.android.customization.picker.color.domain.interactor.ColorPickerInteractor
import com.android.customization.picker.color.ui.viewmodel.ColorPickerViewModel
import com.android.customization.picker.quickaffordance.domain.interactor.KeyguardQuickAffordancePickerInteractor
import com.android.systemui.shared.clocks.ClockRegistry
import com.android.wallpaper.module.Injector
import com.android.wallpaper.picker.customization.data.repository.WallpaperColorsRepository

interface CustomizationInjector : Injector {
    fun getCustomizationPreferences(context: Context): CustomizationPreferences

    fun getKeyguardQuickAffordancePickerInteractor(
        context: Context,
    ): KeyguardQuickAffordancePickerInteractor

    fun getClockRegistry(context: Context): ClockRegistry?

    fun getClockPickerInteractor(context: Context): ClockPickerInteractor

    fun getColorPickerInteractor(
        context: Context,
        wallpaperColorsRepository: WallpaperColorsRepository,
    ): ColorPickerInteractor

    fun getColorPickerViewModelFactory(
        context: Context,
        wallpaperColorsRepository: WallpaperColorsRepository,
    ): ColorPickerViewModel.Factory

    fun getClockCarouselViewModelFactory(
        interactor: ClockPickerInteractor,
        clockViewFactory: ClockViewFactory,
        resources: Resources,
    ): ClockCarouselViewModel.Factory

    fun getClockViewFactory(activity: ComponentActivity): ClockViewFactory

    fun getClockSettingsViewModelFactory(
        context: Context,
        wallpaperColorsRepository: WallpaperColorsRepository,
        clockViewFactory: ClockViewFactory,
    ): ClockSettingsViewModel.Factory
}
