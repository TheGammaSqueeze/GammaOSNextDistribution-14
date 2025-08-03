/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.tv.settings.util;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.leanback.preference.LeanbackPreferenceFragmentCompat;
import androidx.preference.Preference;

import com.android.tv.settings.R;

public class SettingsPreferenceUtil {
    private static final String DELIMITER = ":";

    /**
     * Return the compound key of the preference. Format: Title:Key
     *
     * @param fragment fragment which preference belongs to.
     * @return compound key of the preference
     */
    public static String getCompoundKey(@NonNull LeanbackPreferenceFragmentCompat fragment,
            @NonNull Preference preference) {
        String title = "";
        View fragmentView = fragment.getView();
        if (fragmentView != null && fragmentView.findViewById(R.id.decor_title) != null) {
            title = ((TextView) (fragmentView.findViewById(R.id.decor_title))).getText().toString();
        }
        return title + DELIMITER + preference.getKey();
    }
}
