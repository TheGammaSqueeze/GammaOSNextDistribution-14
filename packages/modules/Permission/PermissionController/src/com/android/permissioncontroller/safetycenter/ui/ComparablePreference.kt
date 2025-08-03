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
 */
package com.android.permissioncontroller.safetycenter.ui

import androidx.preference.Preference

/**
 * Allows comparison with a [Preference] to determine if it has been changed.
 *
 * @see SafetyPreferenceComparisonCallback
 */
internal interface ComparablePreference {
    /** Returns true if given Preference represents an item of the same kind. */
    fun isSameItem(preference: Preference): Boolean

    /** Returns true if given Preference contains the same data. */
    fun hasSameContents(preference: Preference): Boolean
}
