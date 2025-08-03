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

package com.android.tv.settings.device.displaysound;

import static android.hardware.display.HdrConversionMode.HDR_CONVERSION_FORCE;
import static android.view.Display.HdrCapabilities.HDR_TYPE_INVALID;

import static com.android.tv.settings.device.displaysound.DisplaySoundUtils.isHdrFormatSupported;
import static com.android.tv.settings.overlay.FlavorUtils.FLAVOR_CLASSIC;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.hardware.display.HdrConversionMode;
import android.os.Bundle;
import android.view.Display;

import androidx.annotation.Keep;
import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;

import com.android.tv.settings.R;
import com.android.tv.settings.RadioPreference;
import com.android.tv.settings.SettingsPreferenceFragment;
import com.android.tv.settings.overlay.FlavorUtils;


/**
 * This Fragment is responsible for selecting the dynamic range or HDR conversion preference.
 */
@Keep
public class PreferredDynamicRangeFragment  extends SettingsPreferenceFragment {
    static final String PREFERRED_DYNAMIC_RANGE_FORCE_FRAGMENT =
            "com.android.tv.settings.device.displaysound.PreferredDynamicRangeForceFragment";
    static final String KEY_DYNAMIC_RANGE_SELECTION = "preferred_dynamic_range_selection_option";
    static final String KEY_DYNAMIC_RANGE_SELECTION_SYSTEM =
            "preferred_dynamic_range_selection_system";
    static final String KEY_DYNAMIC_RANGE_SELECTION_PASSTHROUGH =
            "preferred_dynamic_range_selection_passthrough";
    static final String KEY_DYNAMIC_RANGE_SELECTION_FORCE =
            "preferred_dynamic_range_selection_force";

    private static final String TAG = ResolutionSelectionFragment.class.getSimpleName();
    private DisplayManager mDisplayManager;

    private HdrConversionMode mHdrConversionMode;

    /** @return the new instance of the class */
    public static PreferredDynamicRangeFragment newInstance() {
        return new PreferredDynamicRangeFragment();
    }

    @Override
    public void onAttach(Context context) {
        mDisplayManager = getDisplayManager();
        mHdrConversionMode = mDisplayManager.getHdrConversionModeSetting();
        super.onAttach(context);
    }

    @Override
    public void onResume() {
        mHdrConversionMode = mDisplayManager.getHdrConversionModeSetting();
        super.onResume();
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferred_dynamic_range_selection, null);

        String preferenceFromMode = getPreferenceFromMode(mHdrConversionMode.getConversionMode());
        if (preferenceFromMode != null && findPreference(preferenceFromMode) != null) {
            Preference pref = findPreference(preferenceFromMode);
            selectRadioPreference(pref);
        }
        showPreferredDynamicRangeRadioPreference(
                mHdrConversionMode.getConversionMode() == HDR_CONVERSION_FORCE);

        // Do not show sidebar info texts in case of 1 panel settings.
        if (FlavorUtils.getFlavor(getContext()) != FLAVOR_CLASSIC) {
            createInfoFragments();
        }
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        String key = preference.getKey();

        if (key == null) {
            return super.onPreferenceTreeClick(preference);
        }

        if (preference instanceof RadioPreference) {
            selectRadioPreference(preference);
            // Enable auto option in HDR types if hdr conversion mode to SDR was selected. This
            // is done because when SDR is chosen, we disable all HDR types.
            switch (key) {
                case KEY_DYNAMIC_RANGE_SELECTION_SYSTEM: {
                    selectSystemPreferredConversion();
                    showPreferredDynamicRangeRadioPreference(false);
                    break;
                }
                case KEY_DYNAMIC_RANGE_SELECTION_PASSTHROUGH: {
                    if (mDisplayManager.getHdrConversionModeSetting().equals(new HdrConversionMode(
                            HdrConversionMode.HDR_CONVERSION_FORCE, HDR_TYPE_INVALID))) {
                        mDisplayManager.setAreUserDisabledHdrTypesAllowed(true);
                    }
                    mHdrConversionMode =
                            new HdrConversionMode(HdrConversionMode.HDR_CONVERSION_PASSTHROUGH);
                    mDisplayManager.setHdrConversionMode(mHdrConversionMode);
                    showPreferredDynamicRangeRadioPreference(false);
                    break;
                }
                case KEY_DYNAMIC_RANGE_SELECTION_FORCE: {
                    if (mHdrConversionMode.getConversionMode()
                            != HdrConversionMode.HDR_CONVERSION_FORCE) {
                        selectSystemPreferredConversion();
                        selectForceHdrConversion(mDisplayManager);
                        mHdrConversionMode = mDisplayManager.getHdrConversionModeSetting();
                        showPreferredDynamicRangeRadioPreference(true);
                    }
                    break;
                }
                default:
                    throw new IllegalArgumentException("Unknown dynamic range selection pref value"
                            + ": " + key);
            }
        }
        return super.onPreferenceTreeClick(preference);
    }

    private void selectSystemPreferredConversion() {
        if (mDisplayManager.getHdrConversionModeSetting().equals(new HdrConversionMode(
                HdrConversionMode.HDR_CONVERSION_FORCE, HDR_TYPE_INVALID))) {
            mDisplayManager.setAreUserDisabledHdrTypesAllowed(true);
        }
        mHdrConversionMode = new HdrConversionMode(
                HdrConversionMode.HDR_CONVERSION_SYSTEM);
        mDisplayManager.setHdrConversionMode(mHdrConversionMode);
    }

    @VisibleForTesting
    DisplayManager getDisplayManager() {
        return getContext().getSystemService(DisplayManager.class);
    }

    private void selectRadioPreference(Preference preference) {
        final RadioPreference radioPreference = (RadioPreference) preference;
        radioPreference.setChecked(true);
        radioPreference.clearOtherRadioPreferences(getPreferenceGroup());
    }

    private PreferenceGroup getPreferenceGroup() {
        return (PreferenceGroup) findPreference(KEY_DYNAMIC_RANGE_SELECTION);
    }

    private void createInfoFragments() {
        Preference dynamicRangeSystemPref = findPreference(KEY_DYNAMIC_RANGE_SELECTION_SYSTEM);
        if (dynamicRangeSystemPref != null) {
            dynamicRangeSystemPref.setFragment(
                    PreferredDynamicRangeInfo
                            .PreferredDynamicRangeSystemInfoFragment.class.getName());
        }
    }

    private String getPreferenceFromMode(int hdrConversionMode) {
        switch (hdrConversionMode) {
            case HdrConversionMode.HDR_CONVERSION_PASSTHROUGH:
                return KEY_DYNAMIC_RANGE_SELECTION_PASSTHROUGH;
            case  HdrConversionMode.HDR_CONVERSION_SYSTEM:
                return KEY_DYNAMIC_RANGE_SELECTION_SYSTEM;
            case HdrConversionMode.HDR_CONVERSION_FORCE:
                return KEY_DYNAMIC_RANGE_SELECTION_FORCE;
            default:
                return null;
        }
    }

    static void selectForceHdrConversion(DisplayManager displayManager) {
        Display display = displayManager.getDisplay(Display.DEFAULT_DISPLAY);
        int systemPreferredType = displayManager.getHdrConversionMode()
                .getPreferredHdrOutputType();
        if (!isHdrFormatSupported(display.getMode(), systemPreferredType)) {
            displayManager.setHdrConversionMode(
                    new HdrConversionMode(HdrConversionMode.HDR_CONVERSION_FORCE));
            return;
        }
        HdrConversionMode hdrConversionMode = new HdrConversionMode(
                HDR_CONVERSION_FORCE, systemPreferredType);
        displayManager.setHdrConversionMode(hdrConversionMode);
    }

    private void showPreferredDynamicRangeRadioPreference(boolean shouldShow) {
        Preference pref = findPreference(KEY_DYNAMIC_RANGE_SELECTION_FORCE);

        if (pref != null) {
            pref.setFragment(shouldShow ? PREFERRED_DYNAMIC_RANGE_FORCE_FRAGMENT : null);
        }
    }
}
