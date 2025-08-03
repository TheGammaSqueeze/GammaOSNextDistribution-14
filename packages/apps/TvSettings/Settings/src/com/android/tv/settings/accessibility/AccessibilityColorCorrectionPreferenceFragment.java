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

package com.android.tv.settings.accessibility;

import static com.android.tv.settings.util.InstrumentationUtils.logEntrySelected;
import static com.android.tv.settings.util.InstrumentationUtils.logToggleInteracted;

import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.app.tvsettings.TvSettingsEnums;

import androidx.annotation.Keep;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.TwoStatePreference;

import com.android.tv.settings.R;
import com.android.tv.settings.RadioPreference;
import com.android.tv.settings.SettingsPreferenceFragment;

/**
 * Fragment for configuring the accessibility color correction
 */
@Keep
public class AccessibilityColorCorrectionPreferenceFragment extends SettingsPreferenceFragment {
    private static final String TOGGLE_COLOR_CORRECTION_KEY = "toggle_color_correction";
    private static final String COLOR_MODE_GROUP_KEY = "color_correction_mode";
    private static final String COLOR_MODE_DEUTERANOMALY_KEY
        = "color_correction_mode_deuteranomaly";
    private static final String COLOR_MODE_PROTANOMALY_KEY = "color_correction_mode_protanomaly";
    private static final String COLOR_MODE_TRITANOMALY_KEY = "color_correction_mode_tritanomaly";
    private static final String COLOR_MODE_GRAYSCALE_KEY = "color_correction_mode_grayscale";

    private static final int DALTONIZER_ENABLED = 1;
    private static final int DALTONIZER_DISABLED = 0;

    // These constant values are defined in the arrays.xml of the Settings app.
    private static final int COLOR_CORRECTION_GRAYSCALE = 0;
    private static final int COLOR_CORRECTION_PROTANOMALY = 11;
    private static final int COLOR_CORRECTION_DEUTERANOMALY = 12;
    private static final int COLOR_CORRECTION_TRITANOMALY = 13;

    private PreferenceGroup mColorModeGroup;
    private RadioPreference mColorModeDeuteranomaly;
    private RadioPreference mColorModeProtanomaly;
    private RadioPreference mColorModeTritanomaly;
    private RadioPreference mColorModeGrayscale;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.accessibility_color_correction, rootKey);

        final TwoStatePreference enableColorCorrectionPreference =
            (TwoStatePreference) findPreference(TOGGLE_COLOR_CORRECTION_KEY);

        enableColorCorrectionPreference.setChecked(
            Settings.Secure.getInt(getContext().getContentResolver(),
                Settings.Secure.ACCESSIBILITY_DISPLAY_DALTONIZER_ENABLED, 0) == DALTONIZER_ENABLED);

        mColorModeGroup = (PreferenceGroup) findPreference(COLOR_MODE_GROUP_KEY);
        mColorModeDeuteranomaly = (RadioPreference) findPreference(COLOR_MODE_DEUTERANOMALY_KEY);
        mColorModeProtanomaly = (RadioPreference) findPreference(COLOR_MODE_PROTANOMALY_KEY);
        mColorModeTritanomaly = (RadioPreference) findPreference(COLOR_MODE_TRITANOMALY_KEY);
        mColorModeGrayscale = (RadioPreference) findPreference(COLOR_MODE_GRAYSCALE_KEY);
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        final String key = preference.getKey();
        if (TextUtils.isEmpty(key)) {
            return super.onPreferenceTreeClick(preference);
        }
        switch (key) {
            case TOGGLE_COLOR_CORRECTION_KEY:
                logToggleInteracted(
                    TvSettingsEnums.SYSTEM_A11Y_COLOR_CORRECTION_ON_OFF,
                    ((TwoStatePreference) preference).isChecked());
                setColorCorrectionStatus(((TwoStatePreference) preference).isChecked());
                return true;
            case COLOR_MODE_DEUTERANOMALY_KEY:
                logEntrySelected(TvSettingsEnums.SYSTEM_A11Y_COLOR_CORRECTION_DEUTERANOMALY);
                setColorCorrectionMode(COLOR_CORRECTION_DEUTERANOMALY);
                mColorModeDeuteranomaly.setChecked(true);
                mColorModeDeuteranomaly.clearOtherRadioPreferences(mColorModeGroup);
                return true;
            case COLOR_MODE_PROTANOMALY_KEY:
                logEntrySelected(TvSettingsEnums.SYSTEM_A11Y_COLOR_CORRECTION_PROTANOMALY);
                setColorCorrectionMode(COLOR_CORRECTION_PROTANOMALY);
                mColorModeProtanomaly.setChecked(true);
                mColorModeProtanomaly.clearOtherRadioPreferences(mColorModeGroup);
                return true;
            case COLOR_MODE_TRITANOMALY_KEY:
                logEntrySelected(TvSettingsEnums.SYSTEM_A11Y_COLOR_CORRECTION_TRITANOMALY);
                setColorCorrectionMode(COLOR_CORRECTION_TRITANOMALY);
                mColorModeTritanomaly.setChecked(true);
                mColorModeTritanomaly.clearOtherRadioPreferences(mColorModeGroup);
                return true;
            case COLOR_MODE_GRAYSCALE_KEY:
                logEntrySelected(TvSettingsEnums.SYSTEM_A11Y_COLOR_CORRECTION_GRAYSCALE);
                setColorCorrectionMode(COLOR_CORRECTION_GRAYSCALE);
                mColorModeGrayscale.setChecked(true);
                mColorModeGrayscale.clearOtherRadioPreferences(mColorModeGroup);
                return true;
        }
        return super.onPreferenceTreeClick(preference);
    }

    private void setColorCorrectionStatus(boolean enabled) {
        Settings.Secure.putInt(getContext().getContentResolver(),
            Settings.Secure.ACCESSIBILITY_DISPLAY_DALTONIZER_ENABLED, enabled
                ? DALTONIZER_ENABLED : DALTONIZER_DISABLED);
    }

    private void setColorCorrectionMode(int mode) {
        Settings.Secure.putInt(getContext().getContentResolver(),
            Settings.Secure.ACCESSIBILITY_DISPLAY_DALTONIZER, mode);
    }
}