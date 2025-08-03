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

package com.android.permissioncontroller.safetycenter.ui;

import static android.os.Build.VERSION_CODES.TIRAMISU;

import androidx.annotation.RequiresApi;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager.SimplePreferenceComparisonCallback;

/** A {@link PreferenceComparisonCallback} to identify changed preferences of Safety Center. */
@RequiresApi(TIRAMISU)
class SafetyPreferenceComparisonCallback extends SimplePreferenceComparisonCallback {

    @Override
    public boolean arePreferenceItemsTheSame(Preference oldPreference, Preference newPreference) {
        if (oldPreference instanceof ComparablePreference) {
            return ((ComparablePreference) oldPreference).isSameItem(newPreference);
        }
        return super.arePreferenceItemsTheSame(oldPreference, newPreference);
    }

    @Override
    public boolean arePreferenceContentsTheSame(
            Preference oldPreference, Preference newPreference) {
        if (oldPreference instanceof ComparablePreference) {
            return ((ComparablePreference) oldPreference).hasSameContents(newPreference);
        }
        return super.arePreferenceContentsTheSame(oldPreference, newPreference);
    }
}
