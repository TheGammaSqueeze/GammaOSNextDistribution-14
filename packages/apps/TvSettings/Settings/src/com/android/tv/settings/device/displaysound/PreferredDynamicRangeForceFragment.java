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

import static android.view.Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION;
import static android.view.Display.HdrCapabilities.HDR_TYPE_HDR10;
import static android.view.Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS;
import static android.view.Display.HdrCapabilities.HDR_TYPE_HLG;

import static com.android.tv.settings.device.displaysound.DisplaySoundUtils.createAlertDialog;
import static com.android.tv.settings.device.displaysound.DisplaySoundUtils.doesCurrentModeNotSupportDvBecauseLimitedTo4k30;
import static com.android.tv.settings.device.displaysound.DisplaySoundUtils.enableHdrType;
import static com.android.tv.settings.device.displaysound.DisplaySoundUtils.findMode1080p60;
import static com.android.tv.settings.overlay.FlavorUtils.FLAVOR_CLASSIC;

import android.content.Context;
import android.content.DialogInterface;
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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * This Fragment is responsible for selecting the dynamic range preference in case
 * {@link HdrConversionMode#HDR_CONVERSION_FORCE} is selected.
 */
@Keep
public class PreferredDynamicRangeForceFragment extends SettingsPreferenceFragment {

    static final String KEY_HDR_FORMAT_PREFIX = "hdr_format_";

    static final String KEY_DYNAMIC_RANGE_SELECTION_SDR =
            "preferred_dynamic_range_selection_force_sdr";

    static final String KEY_DYNAMIC_RANGE_SELECTION_FORCE =
            "preferred_dynamic_range_selection_force_screen_option";
    private DisplayManager mDisplayManager;
    private int[] mHdrTypes;

    /** @return the new instance of the class */
    public static PreferredDynamicRangeFragment newInstance() {
        return new PreferredDynamicRangeFragment();
    }

    @Override
    public void onAttach(Context context) {
        mDisplayManager = getDisplayManager();
        mHdrTypes = getIntersectingDeviceAndDisplayHdrTypes();
        super.onAttach(context);
    }

    private int[] getIntersectingDeviceAndDisplayHdrTypes() {
        Set<Integer> displaySupportedHdrTypes = new HashSet<>();
        Display display = mDisplayManager.getDisplay(Display.DEFAULT_DISPLAY);
        Arrays.stream(display.getSupportedModes()).forEach(mode -> Arrays.stream(
                mode.getSupportedHdrTypes()).forEach(displaySupportedHdrTypes::add));

        int[] deviceSupportedHdrOutputTypes = mDisplayManager.getSupportedHdrOutputTypes();
        Set<Integer> intersectingHdrTypes = new HashSet<>();
        for (Integer hdrType : deviceSupportedHdrOutputTypes) {
            if (displaySupportedHdrTypes.contains(hdrType)) {
                intersectingHdrTypes.add(hdrType);
            }
        }

        int[] mergedArray = new int[intersectingHdrTypes.size()];
        int index = 0;
        for (Integer hdrType : intersectingHdrTypes) {
            mergedArray[index++] = hdrType;
        }
        return mergedArray;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.preferred_dynamic_range_force, null);
        createHdrPreference();
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        String key = preference.getKey();

        if (key == null) {
            return super.onPreferenceTreeClick(preference);
        }

        if (preference instanceof RadioPreference) {
            if (key.equals(KEY_DYNAMIC_RANGE_SELECTION_SDR)) {
                mDisplayManager.setHdrConversionMode(
                        new HdrConversionMode(HdrConversionMode.HDR_CONVERSION_FORCE));
                mDisplayManager.setAreUserDisabledHdrTypesAllowed(false);
                mDisplayManager.setUserDisabledHdrTypes(getDeviceSupportedHdrTypes());
                selectRadioPreference(preference);
            } else if (key.contains(KEY_HDR_FORMAT_PREFIX)) {
                String hdrType = key.substring(KEY_HDR_FORMAT_PREFIX.length());
                int preferredHdrOutputType = Integer.parseInt(hdrType);

                final Display display = mDisplayManager.getDisplay(Display.DEFAULT_DISPLAY);
                if (preferredHdrOutputType == HDR_TYPE_DOLBY_VISION
                        && doesCurrentModeNotSupportDvBecauseLimitedTo4k30(display)) {
                    askUserBeforeForcingToDvAndChangingTo1080p60(display, preference);
                } else {
                    mDisplayManager.setHdrConversionMode(new HdrConversionMode(
                            HdrConversionMode.HDR_CONVERSION_FORCE, preferredHdrOutputType));
                    enableHdrType(mDisplayManager, preferredHdrOutputType);
                    selectRadioPreference(preference);
                }
            }
        }
        return super.onPreferenceTreeClick(preference);
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

    private void createHdrPreference() {
        Set<Integer> hdrTypesSet = new HashSet<>();
        for (int i = 0; i < mHdrTypes.length; i++) {
            hdrTypesSet.add(mHdrTypes[i]);
            RadioPreference pref = new RadioPreference(getContext());
            pref.setTitle(getContext().getString(
                    R.string.preferred_dynamic_range_selection_force_hdr_title,
                    getContext().getString(getFormatPreferenceTitleId(mHdrTypes[i]))));
            pref.setKey(KEY_HDR_FORMAT_PREFIX + mHdrTypes[i]);
            getPreferenceGroup().addPreference(pref);

            if (FlavorUtils.getFlavor(getContext()) != FLAVOR_CLASSIC) {
                pref.setFragment(getInfoClassName(mHdrTypes[i]));
            }
        }
        RadioPreference pref = new RadioPreference(getContext());
        pref.setTitle(
                getContext().getString(R.string.preferred_dynamic_range_selection_force_sdr_title));
        pref.setKey(KEY_DYNAMIC_RANGE_SELECTION_SDR);
        getPreferenceGroup().addPreference(pref);
        if (FlavorUtils.getFlavor(getContext()) != FLAVOR_CLASSIC) {
            pref.setFragment(PreferredDynamicRangeInfo.ForceSdrInfoFragment.class.getName());
        }

        final int selectedHdrType =
                mDisplayManager.getHdrConversionModeSetting().getPreferredHdrOutputType();
        if (selectedHdrType != Display.HdrCapabilities.HDR_TYPE_INVALID
                && hdrTypesSet.contains(selectedHdrType)) {
            pref = findPreference(KEY_HDR_FORMAT_PREFIX + selectedHdrType);
        }
        selectRadioPreference(pref);
    }

    private void askUserBeforeForcingToDvAndChangingTo1080p60(Display display,
            Preference preference) {
        String title = getResources().getString(
                R.string.preferred_dynamic_range_force_dialog_title);
        String dialogDescription = getResources().getString(
                R.string.preferred_dynamic_range_force_dialog_desc_4k30_issue);
        DialogInterface.OnClickListener onOkClicked = (dialog, which) -> {
            mDisplayManager.setGlobalUserPreferredDisplayMode(findMode1080p60(display));
            mDisplayManager.setHdrConversionMode(new HdrConversionMode(
                    HdrConversionMode.HDR_CONVERSION_FORCE, HDR_TYPE_DOLBY_VISION));
            selectRadioPreference(preference);
            enableHdrType(mDisplayManager, HDR_TYPE_DOLBY_VISION);
            dialog.dismiss();
        };

        DialogInterface.OnClickListener onCancelClicked = (dialog, which) -> {
            ((RadioPreference) preference).setChecked(false);
            dialog.dismiss();
        };
        createAlertDialog(getContext(), title, dialogDescription, onOkClicked, onCancelClicked)
                .show();
    }

    /**
     * @return the display id for each hdr type.
     */
    private int getFormatPreferenceTitleId(int hdrType) {
        switch (hdrType) {
            case HDR_TYPE_DOLBY_VISION:
                return R.string.hdr_format_dolby_vision;
            case HDR_TYPE_HDR10:
                return R.string.hdr_format_hdr10;
            case HDR_TYPE_HLG:
                return R.string.hdr_format_hlg;
            case HDR_TYPE_HDR10_PLUS:
                return R.string.hdr_format_hdr10plus;
            default:
                return -1;
        }
    }

    private PreferenceGroup getPreferenceGroup() {
        return (PreferenceGroup) findPreference(KEY_DYNAMIC_RANGE_SELECTION_FORCE);
    }

    @VisibleForTesting
    int[] getDeviceSupportedHdrTypes() {
        return getContext().getResources().getIntArray(R.array.config_deviceSupportedHdrFormats);
    }

    private String getInfoClassName(int hdrType) {
        switch(hdrType){
            case HDR_TYPE_DOLBY_VISION: {
                return PreferredDynamicRangeInfo.ForceDVInfoFragment.class.getName();
            }
            case HDR_TYPE_HDR10: {
                return PreferredDynamicRangeInfo.ForceHdrInfoFragment.class.getName();
            }
            case HDR_TYPE_HLG: {
                return PreferredDynamicRangeInfo.ForceHlgInfoFragment.class.getName();
            }
            case HDR_TYPE_HDR10_PLUS: {
                return PreferredDynamicRangeInfo.ForceHdr10PlusInfoFragment.class.getName();
            }
            default: {
                return "";
            }
        }
    }
}
