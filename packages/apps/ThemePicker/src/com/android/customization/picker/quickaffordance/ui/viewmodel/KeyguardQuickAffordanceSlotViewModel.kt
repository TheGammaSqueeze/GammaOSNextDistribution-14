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

package com.android.customization.picker.quickaffordance.ui.viewmodel

import com.android.wallpaper.picker.common.icon.ui.viewmodel.Icon
import com.android.wallpaper.picker.option.ui.viewmodel.OptionItemViewModel

/** Models UI state for a single lock screen quick affordance slot in a picker experience. */
data class KeyguardQuickAffordanceSlotViewModel(
    /** User-visible name for the slot. */
    val name: String,

    /** Whether this is the currently-selected slot in the picker. */
    val isSelected: Boolean,

    /**
     * The list of quick affordances selected for this slot.
     *
     * Useful for preview.
     */
    val selectedQuickAffordances: List<OptionItemViewModel<Icon>>,

    /**
     * The maximum number of quick affordances that can be selected for this slot.
     *
     * Useful for picker and preview.
     */
    val maxSelectedQuickAffordances: Int,

    /** Notifies that the slot has been clicked by the user. */
    val onClicked: (() -> Unit)?,
)
