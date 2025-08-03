/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tv.settings.device;

import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.preference.PreferenceCategory;

import com.android.tv.twopanelsettings.slices.EmbeddedSlicePreference;
import com.android.tv.twopanelsettings.slices.EmbeddedSlicePreferenceHelper;

/** The "Storage" screen in TV settings that can be utilized by res overlay package. */
@Keep
public class StorageSummaryFragmentX extends StorageSummaryFragment implements
        EmbeddedSlicePreferenceHelper.SlicePreferenceListener {
    private static final String KEY_AMBIENT_STORAGE = "ambient_storage";
    private static final String KEY_CATEGORY_SUGGESTIONS = "suggestions";
    private EmbeddedSlicePreference mAmbientPreference;
    private PreferenceCategory mSuggestionCategory;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        mAmbientPreference = findPreference(KEY_AMBIENT_STORAGE);
        mSuggestionCategory = findPreference(KEY_CATEGORY_SUGGESTIONS);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Remove and add preference to trigger onDetached()/onAttached() for the preference
        // So when the user come back from ambient settings, the data gets updated.
        if (mSuggestionCategory != null) {
            mSuggestionCategory.removePreference(mAmbientPreference);
            mSuggestionCategory.addPreference(mAmbientPreference);
        }
        if (mAmbientPreference != null) {
            mAmbientPreference.addListener(this);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mAmbientPreference != null) {
            mAmbientPreference.removeListener(this);
        }
    }

    @Override
    public void onChangeVisibility() {
        if (mSuggestionCategory != null) {
            mSuggestionCategory.setVisible(mAmbientPreference.isVisible());
        }
    }
}
