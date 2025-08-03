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

import android.graphics.Bitmap
import kotlinx.coroutines.flow.Flow

/** Models the UI state for an option in the wallpaper quick switcher. */
data class WallpaperQuickSwitchOptionViewModel(
    /** The ID of the wallpaper this option is associated with. */
    val wallpaperId: String,
    /** A placeholder color to show in the option while we load the preview thumbnail. */
    val placeholderColor: Int,
    /** A function to invoke to get the preview thumbnail for the option. */
    val thumbnail: suspend () -> Bitmap?,
    /** The title of the wallpaper or wallpaper category */
    val title: String?,
    /**
     * Whether the option should be rendered as large. If `false`, the option should be rendered
     * smaller.
     */
    val isLarge: Flow<Boolean>,
    /** Whether the selection border should be visible. */
    val isSelectionIndicatorVisible: Flow<Boolean>,
    /**
     * A function to invoke when the option is clicked by the user. If `null`, the option is not
     * clickable.
     */
    val onSelected: Flow<(() -> Unit)?>,
)
