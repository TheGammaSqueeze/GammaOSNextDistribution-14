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

package com.android.car.settings.accessibility;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.provider.Settings;

import androidx.annotation.VisibleForTesting;
import androidx.preference.ListPreference;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;

/**
 * Preference controller for setting the captions text size. This is achieved through the settings
 * secure constant {@link Settings.Secure#ACCESSIBILITY_CAPTIONING_FONT_SCALE}.
 */
public class CaptionsTextSizeListPreferenceController extends PreferenceController<ListPreference> {

    private static final int DEFAULT_SELECTOR_INDEX = 2;
    private static final float DEFAULT_TEXT_SIZE = 1.0F;

    @VisibleForTesting
    final String[] mFontSizeTitles;
    private final String[] mFontSizeStringValues;
    private final float[] mFontSizeFloatValues;

    public CaptionsTextSizeListPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mFontSizeTitles = new String[]{
                context.getString(R.string.captions_settings_text_size_very_small),
                context.getString(R.string.captions_settings_text_size_small),
                context.getString(R.string.captions_settings_text_size_default),
                context.getString(R.string.captions_settings_text_size_large),
                context.getString(R.string.captions_settings_text_size_very_large)
        };
        mFontSizeStringValues = new String[]{
                "0.25",
                "0.5",
                "1.0",
                "1.5",
                "2.0"
        };
        mFontSizeFloatValues = new float[mFontSizeStringValues.length];
        for (int i = 0; i < mFontSizeStringValues.length; i++) {
            mFontSizeFloatValues[i] = Float.parseFloat(mFontSizeStringValues[i]);
        }
    }

    @Override
    protected Class<ListPreference> getPreferenceType() {
        return ListPreference.class;
    }

    @Override
    protected void updateState(ListPreference preference) {
        preference.setEntries(mFontSizeTitles);
        preference.setEntryValues(mFontSizeStringValues);
        int currentFontSizeIndex = getCurrentSelectedFontSizeIndex();
        preference.setValueIndex(currentFontSizeIndex);
        preference.setSummary(getSummary(currentFontSizeIndex));
    }

    @Override
    public boolean handlePreferenceChanged(ListPreference preference, Object newValue) {
        float newFontValue = Float.parseFloat((String) newValue);
        Settings.Secure.putFloat(getContext().getContentResolver(),
                Settings.Secure.ACCESSIBILITY_CAPTIONING_FONT_SCALE, newFontValue);
        return true;
    }

    private CharSequence getSummary(int currentFontSizeIndex) {
        return mFontSizeTitles[currentFontSizeIndex];
    }

    private int getCurrentSelectedFontSizeIndex() {
        float currentFontScale = Settings.Secure.getFloat(getContext().getContentResolver(),
                Settings.Secure.ACCESSIBILITY_CAPTIONING_FONT_SCALE, DEFAULT_TEXT_SIZE);

        int selectorIndex = DEFAULT_SELECTOR_INDEX;
        for (int i = 0; i < mFontSizeFloatValues.length; i++) {
            if (mFontSizeFloatValues[i] == currentFontScale) {
                selectorIndex = i;
                break;
            }
        }

        return selectorIndex;
    }
}
