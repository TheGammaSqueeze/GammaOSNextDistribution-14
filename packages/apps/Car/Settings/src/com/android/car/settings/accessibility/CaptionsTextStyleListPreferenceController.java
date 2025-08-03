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
 * Preference controller for setting the captions text style. This is achieved through the settings
 * secure constant {@link Settings.Secure#ACCESSIBILITY_CAPTIONING_PRESET}.
 */
public class CaptionsTextStyleListPreferenceController extends
        PreferenceController<ListPreference> {

    private static final int DEFAULT_SELECTOR_INDEX = 0;
    private static final int DEFAULT_STYLE_PRESET = 4;

    @VisibleForTesting
    final String[] mFontStyleTitles;
    private final String[] mFontStyleStringValues;
    private final int[] mFontStyleIntValues;

    public CaptionsTextStyleListPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mFontStyleTitles = new String[]{
                context.getString(R.string.captions_settings_text_style_by_app),
                context.getString(R.string.captions_settings_text_style_white_on_black),
                context.getString(R.string.captions_settings_text_style_black_on_white),
                context.getString(R.string.captions_settings_text_style_yellow_on_black),
                context.getString(R.string.captions_settings_text_style_yellow_on_blue)
        };
        mFontStyleStringValues = new String[]{
                "4",
                "0",
                "1",
                "2",
                "3"
        };
        mFontStyleIntValues = new int[mFontStyleStringValues.length];
        for (int i = 0; i < mFontStyleStringValues.length; i++) {
            mFontStyleIntValues[i] = Integer.parseInt(mFontStyleStringValues[i]);
        }
    }

    @Override
    protected Class<ListPreference> getPreferenceType() {
        return ListPreference.class;
    }

    @Override
    protected void updateState(ListPreference preference) {
        preference.setEntries(mFontStyleTitles);
        preference.setEntryValues(mFontStyleStringValues);
        int currentFontStyleIndex = getCurrentSelectedFontStyleIndex();
        preference.setValueIndex(currentFontStyleIndex);
        preference.setSummary(getSummary(currentFontStyleIndex));
    }

    @Override
    public boolean handlePreferenceChanged(ListPreference preference, Object newValue) {
        int newFontValue = Integer.parseInt((String) newValue);
        Settings.Secure.putInt(getContext().getContentResolver(),
                Settings.Secure.ACCESSIBILITY_CAPTIONING_PRESET, newFontValue);
        return true;
    }

    private CharSequence getSummary(int currentFontStyleIndex) {
        return mFontStyleTitles[currentFontStyleIndex];
    }

    private int getCurrentSelectedFontStyleIndex() {
        int currentFontStyle = Settings.Secure.getInt(getContext().getContentResolver(),
                Settings.Secure.ACCESSIBILITY_CAPTIONING_PRESET, DEFAULT_STYLE_PRESET);

        int selectorIndex = DEFAULT_SELECTOR_INDEX;
        for (int i = 0; i < mFontStyleIntValues.length; i++) {
            if (mFontStyleIntValues[i] == currentFontStyle) {
                selectorIndex = i;
                break;
            }
        }

        return selectorIndex;
    }
}
