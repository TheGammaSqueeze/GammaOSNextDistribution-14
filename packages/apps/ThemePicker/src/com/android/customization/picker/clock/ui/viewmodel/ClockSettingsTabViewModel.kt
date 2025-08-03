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
package com.android.customization.picker.clock.ui.viewmodel

/** View model for the tabs on the clock settings screen. */
class ClockSettingsTabViewModel(
    /** User-visible name for the tab. */
    val name: String,

    /** Whether this is the currently-selected tab in the picker. */
    val isSelected: Boolean,

    /** Notifies that the tab has been clicked by the user. */
    val onClicked: (() -> Unit)?,
)
