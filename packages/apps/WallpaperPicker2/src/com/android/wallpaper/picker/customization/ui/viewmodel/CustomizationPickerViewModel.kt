/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.wallpaper.picker.customization.ui.viewmodel

import android.os.Bundle
import androidx.annotation.VisibleForTesting
import androidx.lifecycle.AbstractSavedStateViewModelFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.savedstate.SavedStateRegistryOwner
import com.android.wallpaper.module.CustomizationSections
import com.android.wallpaper.module.logging.UserEventLogger
import com.android.wallpaper.picker.customization.domain.interactor.WallpaperInteractor
import com.android.wallpaper.picker.customization.shared.model.WallpaperDestination
import com.android.wallpaper.picker.undo.domain.interactor.UndoInteractor
import com.android.wallpaper.picker.undo.ui.viewmodel.UndoViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map

/** Models UI state for the customization picker. */
class CustomizationPickerViewModel
@VisibleForTesting
constructor(
    undoInteractor: UndoInteractor,
    wallpaperInteractor: WallpaperInteractor,
    private val savedStateHandle: SavedStateHandle,
    private val logger: UserEventLogger,
) : AnimationStateViewModel() {

    val undo: UndoViewModel =
        UndoViewModel(
            interactor = undoInteractor,
            logger = logger,
        )

    private val homeWallpaperQuickSwitchViewModel: WallpaperQuickSwitchViewModel =
        WallpaperQuickSwitchViewModel(
            interactor = wallpaperInteractor,
            destination = WallpaperDestination.HOME,
            coroutineScope = viewModelScope,
        )

    private val lockWallpaperQuickSwitchViewModel: WallpaperQuickSwitchViewModel =
        WallpaperQuickSwitchViewModel(
            interactor = wallpaperInteractor,
            destination = WallpaperDestination.LOCK,
            coroutineScope = viewModelScope,
        )

    fun getWallpaperQuickSwitchViewModel(
        screen: CustomizationSections.Screen
    ): WallpaperQuickSwitchViewModel {
        return if (screen == CustomizationSections.Screen.LOCK_SCREEN)
            lockWallpaperQuickSwitchViewModel
        else homeWallpaperQuickSwitchViewModel
    }

    private val _isOnLockScreen = MutableStateFlow(true)
    /** Whether we are on the lock screen. If `false`, we are on the home screen. */
    val isOnLockScreen: Flow<Boolean> = _isOnLockScreen.asStateFlow()

    /** A view-model for the "lock screen" tab. */
    val lockScreenTab: Flow<CustomizationPickerTabViewModel> =
        isOnLockScreen.map { onLockScreen ->
            CustomizationPickerTabViewModel(
                isSelected = onLockScreen,
                onClicked =
                    if (!onLockScreen) {
                        {
                            _isOnLockScreen.value = true
                            savedStateHandle[KEY_SAVED_STATE_IS_ON_LOCK_SCREEN] = true
                        }
                    } else {
                        null
                    }
            )
        }
    /** A view-model for the "home screen" tab. */
    val homeScreenTab: Flow<CustomizationPickerTabViewModel> =
        isOnLockScreen.map { onLockScreen ->
            CustomizationPickerTabViewModel(
                isSelected = !onLockScreen,
                onClicked =
                    if (onLockScreen) {
                        {
                            _isOnLockScreen.value = false
                            savedStateHandle[KEY_SAVED_STATE_IS_ON_LOCK_SCREEN] = false
                        }
                    } else {
                        null
                    }
            )
        }

    init {
        savedStateHandle.get<Boolean>(KEY_SAVED_STATE_IS_ON_LOCK_SCREEN)?.let {
            _isOnLockScreen.value = it
        }
    }

    /**
     * Sets the initial screen we should be on, unless there's already a selected screen from a
     * previous saved state, in which case we ignore the passed-in one.
     */
    fun setInitialScreen(onLockScreen: Boolean) {
        _isOnLockScreen.value =
            savedStateHandle[KEY_SAVED_STATE_IS_ON_LOCK_SCREEN]
                ?: run {
                    savedStateHandle[KEY_SAVED_STATE_IS_ON_LOCK_SCREEN] = onLockScreen
                    onLockScreen
                }
    }

    companion object {
        @JvmStatic
        fun newFactory(
            owner: SavedStateRegistryOwner,
            defaultArgs: Bundle? = null,
            undoInteractor: UndoInteractor,
            wallpaperInteractor: WallpaperInteractor,
            logger: UserEventLogger,
        ): AbstractSavedStateViewModelFactory =
            object : AbstractSavedStateViewModelFactory(owner, defaultArgs) {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(
                    key: String,
                    modelClass: Class<T>,
                    handle: SavedStateHandle,
                ): T {
                    return CustomizationPickerViewModel(
                        undoInteractor = undoInteractor,
                        wallpaperInteractor = wallpaperInteractor,
                        savedStateHandle = handle,
                        logger = logger,
                    )
                        as T
                }
            }

        private const val KEY_SAVED_STATE_IS_ON_LOCK_SCREEN = "is_on_lock_screen"
    }
}
