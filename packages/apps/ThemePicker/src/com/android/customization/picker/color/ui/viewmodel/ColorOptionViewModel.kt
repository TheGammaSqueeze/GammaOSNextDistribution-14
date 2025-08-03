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

package com.android.customization.picker.color.ui.viewmodel

import android.annotation.ColorInt

/**
 * Models UI state for a color options in a picker experience.
 *
 * TODO (b/272109171): Remove after clock settings is refactored to use OptionItemAdapter
 */
data class ColorOptionViewModel(
    /** Colors for the color option. */
    @ColorInt val color0: Int,
    @ColorInt val color1: Int,
    @ColorInt val color2: Int,
    @ColorInt val color3: Int,

    /** A content description for the color. */
    val contentDescription: String,

    /** Nullable option title. Null by default. */
    val title: String? = null,

    /** Whether this color is selected. */
    val isSelected: Boolean,

    /** Notifies that the color has been clicked by the user. */
    val onClick: (() -> Unit)?,
)
