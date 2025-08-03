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

package com.android.wallpaper.picker.customization.ui.viewmodel

import android.stats.style.StyleEnums.SET_WALLPAPER_ENTRY_POINT_WALLPAPER_QUICK_SWITCHER
import com.android.wallpaper.R
import com.android.wallpaper.picker.common.text.ui.viewmodel.Text
import com.android.wallpaper.picker.customization.domain.interactor.WallpaperInteractor
import com.android.wallpaper.picker.customization.shared.model.WallpaperDestination
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

/** Models UI state for views that can render wallpaper quick switching. */
class WallpaperQuickSwitchViewModel
constructor(
    private val interactor: WallpaperInteractor,
    private val destination: WallpaperDestination,
    private val coroutineScope: CoroutineScope,
    maxOptions: Int = interactor.maxOptions,
) {

    private val selectedWallpaperId: Flow<String> =
        interactor
            .selectedWallpaperId(destination)
            .shareIn(
                scope = coroutineScope,
                started = SharingStarted.WhileSubscribed(),
                replay = 1,
            )
    private val selectingWallpaperId: Flow<String?> =
        interactor
            .selectingWallpaperId(destination)
            .shareIn(
                scope = coroutineScope,
                started = SharingStarted.WhileSubscribed(),
                replay = 1,
            )

    val options: Flow<List<WallpaperQuickSwitchOptionViewModel>> =
        interactor
            .previews(
                destination = destination,
                maxResults = maxOptions,
            )
            .distinctUntilChangedBy { previews ->
                // Produce a key that's the same if the same set of wallpapers is available,
                // even if in a different order. This is so that the view can keep from
                // moving the wallpaper options around when the sort order changes as the
                // user selects different wallpapers.
                previews
                    .map { preview -> preview.wallpaperId + preview.lastUpdated }
                    .sorted()
                    .joinToString(",")
            }
            .map { previews ->
                // True if any option is becoming selected following user click.
                val isSomethingBecomingSelectedFlow: Flow<Boolean> =
                    selectingWallpaperId.distinctUntilChanged().map { it != null }

                previews.map { preview ->
                    // True if this option is currently selected.
                    val isSelectedFlow: Flow<Boolean> =
                        selectedWallpaperId.distinctUntilChanged().map { it == preview.wallpaperId }
                    // True if this option is becoming the selected one following user click.
                    val isBecomingSelectedFlow: Flow<Boolean> =
                        selectingWallpaperId.distinctUntilChanged().map {
                            it == preview.wallpaperId
                        }

                    WallpaperQuickSwitchOptionViewModel(
                        wallpaperId = preview.wallpaperId,
                        placeholderColor = preview.placeholderColor,
                        thumbnail = {
                            interactor.loadThumbnail(
                                wallpaperId = preview.wallpaperId,
                                lastUpdatedTimestamp = preview.lastUpdated,
                                destination = destination
                            )
                        },
                        isLarge =
                            combine(
                                isSelectedFlow,
                                isBecomingSelectedFlow,
                                isSomethingBecomingSelectedFlow,
                            ) { isSelected, isBecomingSelected, isSomethingBecomingSelected,
                                ->
                                // The large option is the one that's currently selected or
                                // the one that is becoming the selected one following user
                                // click.
                                (isSelected && !isSomethingBecomingSelected) || isBecomingSelected
                            },
                        isSelectionIndicatorVisible =
                            combine(
                                isSelectedFlow,
                                isBecomingSelectedFlow,
                                isSomethingBecomingSelectedFlow,
                            ) { isSelected, isBeingSelected, isSomethingBecomingSelected ->
                                // The selection border is shown for the option that is the
                                // one that's currently selected or the one that is becoming
                                // the selected one following user click.
                                (isSelected && !isSomethingBecomingSelected) || isBeingSelected
                            },
                        title = preview.title,
                        onSelected =
                            combine(
                                    isSelectedFlow,
                                    isBecomingSelectedFlow,
                                    isSomethingBecomingSelectedFlow,
                                ) { isSelected, _, isSomethingBecomingSelected ->
                                    // An option is selectable if either something is not becoming
                                    // selected and that item is itself not selected.
                                    !isSomethingBecomingSelected && !isSelected
                                }
                                .distinctUntilChanged()
                                .map { isSelectable ->
                                    if (isSelectable) {
                                        {
                                            // A selectable option can become selected.
                                            coroutineScope.launch {
                                                interactor.setRecentWallpaper(
                                                    setWallpaperEntryPoint =
                                                        SET_WALLPAPER_ENTRY_POINT_WALLPAPER_QUICK_SWITCHER,
                                                    destination = destination,
                                                    wallpaperId = preview.wallpaperId,
                                                )
                                            }
                                        }
                                    } else {
                                        // A non-selectable option cannot become selected.
                                        null
                                    }
                                }
                    )
                }
            }
            .shareIn(
                scope = coroutineScope,
                started = SharingStarted.Lazily,
                replay = 1,
            )

    /** Whether recent wallpapers are available */
    val areRecentsAvailable: Boolean = interactor.areRecentsAvailable

    /** Text to show to prompt the user to browse more wallpapers */
    val actionText: Text =
        if (areRecentsAvailable) {
            Text.Resource(R.string.more_wallpapers)
        } else {
            Text.Resource(R.string.wallpaper_picker_entry_title)
        }

    companion object {
        /** The maximum number of options to show, including the currently-selected one. */
        private const val MAX_OPTIONS = 5
    }
}
