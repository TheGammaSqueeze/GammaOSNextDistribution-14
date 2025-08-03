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
package com.android.customization.picker.color.data.repository

import android.util.Log
import com.android.customization.model.CustomizationManager
import com.android.customization.model.color.ColorCustomizationManager
import com.android.customization.model.color.ColorOption
import com.android.customization.model.color.ColorOptionImpl
import com.android.customization.picker.color.shared.model.ColorOptionModel
import com.android.customization.picker.color.shared.model.ColorType
import com.android.systemui.monet.Style
import com.android.wallpaper.picker.customization.data.repository.WallpaperColorsRepository
import com.android.wallpaper.picker.customization.shared.model.WallpaperColorsModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.suspendCancellableCoroutine

// TODO (b/262924623): refactor to remove dependency on ColorCustomizationManager & ColorOption
// TODO (b/268203200): Create test for ColorPickerRepositoryImpl
class ColorPickerRepositoryImpl(
    wallpaperColorsRepository: WallpaperColorsRepository,
    private val colorManager: ColorCustomizationManager,
) : ColorPickerRepository {

    private val homeWallpaperColors: StateFlow<WallpaperColorsModel?> =
        wallpaperColorsRepository.homeWallpaperColors
    private val lockWallpaperColors: StateFlow<WallpaperColorsModel?> =
        wallpaperColorsRepository.lockWallpaperColors
    private var selectedColorOption: MutableStateFlow<ColorOptionModel> =
        MutableStateFlow(getCurrentColorOption())

    private val _isApplyingSystemColor = MutableStateFlow(false)
    override val isApplyingSystemColor = _isApplyingSystemColor.asStateFlow()

    // TODO (b/299510645): update color options on selected option change after restart is disabled
    override val colorOptions: Flow<Map<ColorType, List<ColorOptionModel>>> =
        combine(homeWallpaperColors, lockWallpaperColors) { homeColors, lockColors ->
                homeColors to lockColors
            }
            .map { (homeColors, lockColors) ->
                suspendCancellableCoroutine { continuation ->
                    if (
                        homeColors is WallpaperColorsModel.Loading ||
                            lockColors is WallpaperColorsModel.Loading
                    ) {
                        continuation.resumeWith(
                            Result.success(
                                mapOf(
                                    ColorType.WALLPAPER_COLOR to listOf(),
                                    ColorType.PRESET_COLOR to listOf()
                                )
                            )
                        )
                        return@suspendCancellableCoroutine
                    }
                    val homeColorsLoaded = homeColors as WallpaperColorsModel.Loaded
                    val lockColorsLoaded = lockColors as WallpaperColorsModel.Loaded
                    colorManager.setWallpaperColors(
                        homeColorsLoaded.colors,
                        lockColorsLoaded.colors
                    )
                    colorManager.fetchOptions(
                        object : CustomizationManager.OptionsFetchedListener<ColorOption?> {
                            override fun onOptionsLoaded(options: MutableList<ColorOption?>?) {
                                val wallpaperColorOptions: MutableList<ColorOptionModel> =
                                    mutableListOf()
                                val presetColorOptions: MutableList<ColorOptionModel> =
                                    mutableListOf()
                                options?.forEach { option ->
                                    when ((option as ColorOptionImpl).type) {
                                        ColorType.WALLPAPER_COLOR ->
                                            wallpaperColorOptions.add(option.toModel())
                                        ColorType.PRESET_COLOR ->
                                            presetColorOptions.add(option.toModel())
                                    }
                                }
                                continuation.resumeWith(
                                    Result.success(
                                        mapOf(
                                            ColorType.WALLPAPER_COLOR to wallpaperColorOptions,
                                            ColorType.PRESET_COLOR to presetColorOptions
                                        )
                                    )
                                )
                            }

                            override fun onError(throwable: Throwable?) {
                                Log.e(TAG, "Error loading theme bundles", throwable)
                                continuation.resumeWith(
                                    Result.failure(
                                        throwable ?: Throwable("Error loading theme bundles")
                                    )
                                )
                            }
                        },
                        /* reload= */ false
                    )
                }
            }

    override suspend fun select(colorOptionModel: ColorOptionModel) {
        _isApplyingSystemColor.value = true
        suspendCancellableCoroutine { continuation ->
            colorManager.apply(
                colorOptionModel.colorOption,
                object : CustomizationManager.Callback {
                    override fun onSuccess() {
                        _isApplyingSystemColor.value = false
                        selectedColorOption.value = colorOptionModel
                        continuation.resumeWith(Result.success(Unit))
                    }

                    override fun onError(throwable: Throwable?) {
                        Log.w(TAG, "Apply theme with error", throwable)
                        _isApplyingSystemColor.value = false
                        continuation.resumeWith(
                            Result.failure(throwable ?: Throwable("Error loading theme bundles"))
                        )
                    }
                }
            )
        }
    }

    override fun getCurrentColorOption(): ColorOptionModel {
        val overlays = colorManager.currentOverlays
        val styleOrNull = colorManager.currentStyle
        val style = styleOrNull?.let { Style.valueOf(it) } ?: Style.TONAL_SPOT
        val source = colorManager.currentColorSource
        val colorOptionBuilder = ColorOptionImpl.Builder()
        colorOptionBuilder.source = source
        colorOptionBuilder.style = style
        for (overlay in overlays) {
            colorOptionBuilder.addOverlayPackage(overlay.key, overlay.value)
        }
        val colorOption = colorOptionBuilder.build()
        return ColorOptionModel(
            key = "",
            colorOption = colorOption,
            isSelected = false,
        )
    }

    override fun getCurrentColorSource(): String? {
        return colorManager.currentColorSource
    }

    private fun ColorOptionImpl.toModel(): ColorOptionModel {
        return ColorOptionModel(
            key = "${this.type}::${this.style}::${this.serializedPackages}",
            colorOption = this,
            isSelected = isActive(colorManager),
        )
    }

    companion object {
        private const val TAG = "ColorPickerRepositoryImpl"
    }
}
