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

package com.android.wallpaper.picker.option.ui.viewmodel

import com.android.wallpaper.picker.common.text.ui.viewmodel.Text
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** Models UI state for an item in a list of selectable options. */
data class OptionItemViewModel<Payload>(
    /**
     * A stable key that uniquely identifies this option amongst all other options in the same list
     * of options.
     */
    val key: StateFlow<String>,

    /**
     * The view model representing additional details needed for binding the icon of an option item
     */
    val payload: Payload? = null,

    /**
     * A text to show to the user (or attach as content description on the icon, if there's no
     * dedicated view for it).
     */
    val text: Text,

    /** Hides text and places the provided text in the content description instead */
    val isTextUserVisible: Boolean = true,

    /** Whether this option is selected. */
    val isSelected: StateFlow<Boolean>,

    /** Whether this option is enabled. */
    val isEnabled: Boolean = true,

    /** Notifies that the option has been clicked by the user. */
    val onClicked: Flow<(() -> Unit)?>,

    /** Notifies that the option has been long-clicked by the user. */
    val onLongClicked: (() -> Unit)? = null,
) {
    override fun equals(other: Any?): Boolean {
        val otherItem = other as? OptionItemViewModel<*> ?: return false
        // skipping comparison of onClicked because it is correlated with
        // changes on isSelected
        return this.payload == otherItem.payload &&
            this.text == otherItem.text &&
            this.isSelected.value == otherItem.isSelected.value &&
            this.isEnabled == otherItem.isEnabled &&
            this.onLongClicked == otherItem.onLongClicked
    }
}
