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
package com.android.wallpaper.picker.preview.ui.viewmodel

import android.graphics.Point
import android.graphics.Rect
import android.stats.style.StyleEnums
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.wallpaper.model.wallpaper.FoldableDisplay
import com.android.wallpaper.module.CustomizationSections
import com.android.wallpaper.module.CustomizationSections.Screen
import com.android.wallpaper.picker.customization.shared.model.WallpaperColorsModel
import com.android.wallpaper.picker.customization.shared.model.WallpaperDestination
import com.android.wallpaper.picker.data.WallpaperModel
import com.android.wallpaper.picker.data.WallpaperModel.LiveWallpaperModel
import com.android.wallpaper.picker.data.WallpaperModel.StaticWallpaperModel
import com.android.wallpaper.picker.di.modules.PreviewUtilsModule.HomeScreenPreviewUtils
import com.android.wallpaper.picker.di.modules.PreviewUtilsModule.LockScreenPreviewUtils
import com.android.wallpaper.picker.preview.domain.interactor.WallpaperPreviewInteractor
import com.android.wallpaper.picker.preview.shared.model.FullPreviewCropModel
import com.android.wallpaper.picker.preview.ui.WallpaperPreviewActivity
import com.android.wallpaper.util.DisplayUtils
import com.android.wallpaper.util.PreviewUtils
import com.android.wallpaper.util.WallpaperConnection.WhichPreview
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.EnumSet
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/** Top level [ViewModel] for [WallpaperPreviewActivity] and its fragments */
@HiltViewModel
class WallpaperPreviewViewModel
@Inject
constructor(
    private val interactor: WallpaperPreviewInteractor,
    staticWallpaperPreviewViewModelFactory: StaticWallpaperPreviewViewModel.Factory,
    val previewActionsViewModel: PreviewActionsViewModel,
    private val displayUtils: DisplayUtils,
    @HomeScreenPreviewUtils private val homePreviewUtils: PreviewUtils,
    @LockScreenPreviewUtils private val lockPreviewUtils: PreviewUtils,
) : ViewModel() {

    val staticWallpaperPreviewViewModel =
        staticWallpaperPreviewViewModelFactory.create(viewModelScope)
    val smallerDisplaySize = displayUtils.getRealSize(displayUtils.getSmallerDisplay())
    val wallpaperDisplaySize = displayUtils.getRealSize(displayUtils.getWallpaperDisplay())
    var isViewAsHome = false
    var isNewTask = false

    val wallpaper: StateFlow<WallpaperModel?> = interactor.wallpaperModel

    fun shouldShowTooltip(): Flow<Boolean> =
        combine(interactor.wallpaperModel.filterNotNull(), interactor.hasTooltipBeenShown) {
            wallpaper,
            hasTooltipBeenShown ->
            // Only show tooltip for non-downloadable static wallpapers. Hide tooltip for live
            // wallpaper and downloadable wallpaper as their crop is not adjustable.
            if (wallpaper is StaticWallpaperModel && !wallpaper.isDownloadableWallpaper()) {
                // Only show tooltip if it has not been shown before.
                !hasTooltipBeenShown
            } else {
                false
            }
        }

    fun dismissTooltip() = interactor.dismissTooltip()

    private val _whichPreview = MutableStateFlow<WhichPreview?>(null)
    private val whichPreview: Flow<WhichPreview> = _whichPreview.asStateFlow().filterNotNull()

    fun setWhichPreview(whichPreview: WhichPreview) {
        _whichPreview.value = whichPreview
    }

    fun setCropHints(cropHints: Map<Point, Rect>) {
        wallpaper.value?.let { model ->
            if (model is StaticWallpaperModel && !model.isDownloadableWallpaper()) {
                staticWallpaperPreviewViewModel.updateCropHintsInfo(
                    cropHints.mapValues {
                        FullPreviewCropModel(
                            cropHint = it.value,
                            cropSizeModel = null,
                        )
                    }
                )
            }
        }
    }

    private val _wallpaperConnectionColors: MutableStateFlow<WallpaperColorsModel> =
        MutableStateFlow(WallpaperColorsModel.Loading as WallpaperColorsModel).apply {
            viewModelScope.launch {
                delay(1000)
                if (value == WallpaperColorsModel.Loading) {
                    emit(WallpaperColorsModel.Loaded(null))
                }
            }
        }
    private val liveWallpaperColors: Flow<WallpaperColorsModel> =
        wallpaper
            .filter { it is LiveWallpaperModel }
            .combine(_wallpaperConnectionColors) { _, wallpaperConnectionColors ->
                wallpaperConnectionColors
            }
    val wallpaperColorsModel: Flow<WallpaperColorsModel> =
        merge(liveWallpaperColors, staticWallpaperPreviewViewModel.wallpaperColors)

    // This is only used for the full screen wallpaper preview.
    private val fullWallpaperPreviewConfigViewModel:
        MutableStateFlow<WallpaperPreviewConfigViewModel?> =
        MutableStateFlow(null)

    // This is only used for the small screen wallpaper preview.
    val smallWallpaper: Flow<Pair<WallpaperModel, WhichPreview>> =
        combine(wallpaper.filterNotNull(), whichPreview) { wallpaper, whichPreview ->
            Pair(wallpaper, whichPreview)
        }

    // This is only used for the full screen wallpaper preview.
    val fullWallpaper: Flow<FullWallpaperPreviewViewModel> =
        combine(
            wallpaper.filterNotNull(),
            fullWallpaperPreviewConfigViewModel.filterNotNull(),
            whichPreview,
        ) { wallpaper, config, whichPreview ->
            FullWallpaperPreviewViewModel(
                wallpaper = wallpaper,
                config = config,
                allowUserCropping =
                    wallpaper is StaticWallpaperModel && !wallpaper.isDownloadableWallpaper(),
                whichPreview = whichPreview,
            )
        }

    // This is only used for the full screen wallpaper preview.
    private val _fullWorkspacePreviewConfigViewModel:
        MutableStateFlow<WorkspacePreviewConfigViewModel?> =
        MutableStateFlow(null)

    // This is only used for the full screen wallpaper preview.
    val fullWorkspacePreviewConfigViewModel: Flow<WorkspacePreviewConfigViewModel> =
        _fullWorkspacePreviewConfigViewModel.filterNotNull()

    val onCropButtonClick: Flow<(() -> Unit)?> =
        combine(wallpaper, fullWallpaperPreviewConfigViewModel.filterNotNull()) { wallpaper, _ ->
            if (wallpaper is StaticWallpaperModel && !wallpaper.isDownloadableWallpaper()) {
                {
                    staticWallpaperPreviewViewModel.run {
                        updateCropHintsInfo(fullPreviewCropModels)
                    }
                }
            } else {
                null
            }
        }

    // Set wallpaper button and set wallpaper dialog
    val isSetWallpaperButtonVisible: Flow<Boolean> =
        combine(
            wallpaper,
            staticWallpaperPreviewViewModel.fullResWallpaperViewModel,
        ) { wallpaper, fullResWallpaperViewModel ->
            wallpaper != null &&
                !wallpaper.isDownloadableWallpaper() &&
                !(wallpaper is StaticWallpaperModel && fullResWallpaperViewModel == null)
        }
    val onSetWallpaperButtonClicked: Flow<(() -> Unit)?> =
        isSetWallpaperButtonVisible.map { visible ->
            if (visible) {
                { _showSetWallpaperDialog.value = true }
            } else null
        }

    private val _showSetWallpaperDialog = MutableStateFlow(false)
    val showSetWallpaperDialog = _showSetWallpaperDialog.asStateFlow()

    private val _setWallpaperDialogSelectedScreens: MutableStateFlow<Set<Screen>> =
        MutableStateFlow(EnumSet.allOf(Screen::class.java))
    val setWallpaperDialogSelectedScreens: StateFlow<Set<Screen>> =
        _setWallpaperDialogSelectedScreens.asStateFlow()

    fun onSetWallpaperDialogScreenSelected(screen: Screen) {
        val previousSelection = _setWallpaperDialogSelectedScreens.value
        _setWallpaperDialogSelectedScreens.value =
            if (previousSelection.contains(screen) && previousSelection.size > 1) {
                previousSelection.minus(screen)
            } else {
                previousSelection.plus(screen)
            }
    }

    private val _isSetWallpaperProgressBarVisible = MutableStateFlow(false)
    val isSetWallpaperProgressBarVisible: Flow<Boolean> =
        _isSetWallpaperProgressBarVisible.asStateFlow()

    val setWallpaperDialogOnConfirmButtonClicked: Flow<suspend () -> Unit> =
        combine(
            wallpaper.filterNotNull(),
            staticWallpaperPreviewViewModel.fullResWallpaperViewModel,
            setWallpaperDialogSelectedScreens,
        ) { wallpaper, fullResWallpaperViewModel, selectedScreens ->
            {
                _isSetWallpaperProgressBarVisible.value = true
                val destination = selectedScreens.getDestination()
                _showSetWallpaperDialog.value = false
                when (wallpaper) {
                    is StaticWallpaperModel ->
                        fullResWallpaperViewModel?.let {
                            interactor.setStaticWallpaper(
                                setWallpaperEntryPoint =
                                    StyleEnums.SET_WALLPAPER_ENTRY_POINT_WALLPAPER_PREVIEW,
                                destination = destination,
                                wallpaperModel = wallpaper,
                                inputStream = it.stream,
                                bitmap = it.rawWallpaperBitmap,
                                wallpaperSize = it.rawWallpaperSize,
                                fullPreviewCropModels = it.fullPreviewCropModels,
                            )
                        }
                    is LiveWallpaperModel -> {
                        interactor.setLiveWallpaper(
                            setWallpaperEntryPoint =
                                StyleEnums.SET_WALLPAPER_ENTRY_POINT_WALLPAPER_PREVIEW,
                            destination = destination,
                            wallpaperModel = wallpaper,
                        )
                    }
                }
            }
        }

    private fun Set<Screen>.getDestination(): WallpaperDestination {
        return if (containsAll(CustomizationSections.Screen.entries)) {
            WallpaperDestination.BOTH
        } else if (contains(Screen.HOME_SCREEN)) {
            WallpaperDestination.HOME
        } else if (contains(Screen.LOCK_SCREEN)) {
            WallpaperDestination.LOCK
        } else {
            throw IllegalArgumentException("Unknown screens selected: $this")
        }
    }

    fun dismissSetWallpaperDialog() {
        _showSetWallpaperDialog.value = false
    }

    fun setWallpaperConnectionColors(wallpaperColors: WallpaperColorsModel) {
        _wallpaperConnectionColors.value = wallpaperColors
    }

    fun getWorkspacePreviewConfig(
        screen: Screen,
        foldableDisplay: FoldableDisplay?,
    ): WorkspacePreviewConfigViewModel {
        val previewUtils =
            when (screen) {
                Screen.HOME_SCREEN -> {
                    homePreviewUtils
                }
                Screen.LOCK_SCREEN -> {
                    lockPreviewUtils
                }
            }
        val displayId =
            when (foldableDisplay) {
                FoldableDisplay.FOLDED -> {
                    displayUtils.getSmallerDisplay().displayId
                }
                FoldableDisplay.UNFOLDED -> {
                    displayUtils.getWallpaperDisplay().displayId
                }
                null -> {
                    displayUtils.getWallpaperDisplay().displayId
                }
            }
        return WorkspacePreviewConfigViewModel(
            previewUtils = previewUtils,
            displayId = displayId,
        )
    }

    fun onSmallPreviewClicked(
        screen: Screen,
        foldableDisplay: FoldableDisplay?,
    ) {
        fullWallpaperPreviewConfigViewModel.value =
            getWallpaperPreviewConfig(screen, foldableDisplay)
        _fullWorkspacePreviewConfigViewModel.value =
            getWorkspacePreviewConfig(screen, foldableDisplay)
    }

    fun setDefaultWallpaperPreviewConfigViewModel(displaySize: Point) {
        fullWallpaperPreviewConfigViewModel.value =
            WallpaperPreviewConfigViewModel(
                Screen.HOME_SCREEN,
                displaySize,
            )
    }

    private fun getWallpaperPreviewConfig(
        screen: Screen,
        foldableDisplay: FoldableDisplay?,
    ): WallpaperPreviewConfigViewModel {
        val displaySize =
            when (foldableDisplay) {
                FoldableDisplay.FOLDED -> {
                    smallerDisplaySize
                }
                FoldableDisplay.UNFOLDED -> {
                    wallpaperDisplaySize
                }
                null -> {
                    wallpaperDisplaySize
                }
            }
        return WallpaperPreviewConfigViewModel(
            screen = screen,
            displaySize = displaySize,
        )
    }

    companion object {
        private fun WallpaperModel.isDownloadableWallpaper(): Boolean {
            return this is StaticWallpaperModel && this.downloadableWallpaperData != null
        }
    }
}
