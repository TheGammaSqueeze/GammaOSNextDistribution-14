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

package com.android.tv.settings.inputmethod;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.hardware.input.InputDeviceIdentifier;
import android.hardware.input.InputManager;
import android.hardware.input.KeyboardLayout;
import android.os.Bundle;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.tv.settings.RadioPreference;
import com.android.tv.settings.SettingsPreferenceFragment;
import com.android.tv.twopanelsettings.TwoPanelSettingsFragment;
import com.android.tv.twopanelsettings.slices.SliceFragment;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Presents the user a list of all available keyboard layouts / languages and switches to
 * the selected layout.
 */
public class KeyboardLayoutSelectionFragment extends SettingsPreferenceFragment implements
        InputManager.InputDeviceListener {
    private static final String LAYOUT_RADIO_GROUP = "layout";
    private static final String ARG_DEVICE_NAME = "deviceName";
    private static final String ARG_LAYOUT_IDENTIFIER = "layoutIdentifier";
    private static final String ARG_DEVICE_ID = "deviceId";
    private static final String ARG_INPUT_DEVICE_IDENTIFIER = "inputDeviceIdentifier";

    private InputDeviceIdentifier mInputDeviceIdentifier;
    private int mDeviceId;
    private InputManager mIm;
    private final Map<String, KeyboardLayout> mKeyboardLayoutMap = new HashMap<>();

    /**
     * Prepares the args with the provided parameters.
     */
    public static void prepareArgs(@NonNull Bundle args,
            @NonNull InputDeviceIdentifier inputDeviceIdentifier,
            @NonNull String deviceName, int deviceId, @Nullable String currentLayoutIdentifier) {
        args.putObject(ARG_INPUT_DEVICE_IDENTIFIER, inputDeviceIdentifier);
        args.putString(ARG_DEVICE_NAME, deviceName);
        args.putInt(ARG_DEVICE_ID, deviceId);
        if (currentLayoutIdentifier != null) {
            args.putString(ARG_LAYOUT_IDENTIFIER, currentLayoutIdentifier);
        }
    }

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
        final Context themedContext = getPreferenceManager().getContext();
        mInputDeviceIdentifier = getArguments().getParcelable(ARG_INPUT_DEVICE_IDENTIFIER);
        String mDeviceName = getArguments().getString(ARG_DEVICE_NAME);
        mDeviceId = getArguments().getInt(ARG_DEVICE_ID);
        String currentLayoutIdentifier = getArguments().getString(ARG_LAYOUT_IDENTIFIER);
        final PreferenceScreen screen =
                getPreferenceManager().createPreferenceScreen(themedContext);
        screen.setTitle(themedContext.getString(
                com.android.settingslib.R.string.keyboard_layout_dialog_title));
        screen.setSummary(mDeviceName);
        mIm = Objects.requireNonNull(themedContext.getSystemService(InputManager.class));
        mIm.registerInputDeviceListener(this, null);

        KeyboardLayout[] keyboardLayouts = mIm.getKeyboardLayoutsForInputDevice(
                mInputDeviceIdentifier);
        Arrays.sort(keyboardLayouts);
        RadioPreference activePreference = null;
        for (KeyboardLayout kl : keyboardLayouts) {
            final RadioPreference radioPreference = new RadioPreference(themedContext);
            radioPreference.setKey(kl.getDescriptor());
            radioPreference.setPersistent(false);
            radioPreference.setTitle(kl.getLabel());
            radioPreference.setRadioGroup(LAYOUT_RADIO_GROUP);
            radioPreference.setLayoutResource(
                    com.android.tv.settings.R.layout.preference_reversed_widget);
            if (kl.getDescriptor().equals(currentLayoutIdentifier)) {
                radioPreference.setChecked(true);
                activePreference = radioPreference;
            }
            screen.addPreference(radioPreference);
            mKeyboardLayoutMap.put(kl.getDescriptor(), kl);
        }

        if (activePreference != null) {
            scrollToPreference(activePreference);
        }

        setPreferenceScreen(screen);
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference instanceof RadioPreference) {
            final RadioPreference radioPreference = (RadioPreference) preference;
            radioPreference.clearOtherRadioPreferences(getPreferenceScreen());
            if (radioPreference.isChecked()) {
                mIm.setCurrentKeyboardLayoutForInputDevice(mInputDeviceIdentifier,
                        mKeyboardLayoutMap.get(radioPreference.getKey()).getDescriptor());
            } else {
                radioPreference.setChecked(true);
            }
        }
        return super.onPreferenceTreeClick(preference);
    }

    @Override
    protected int getPageId() {
        return super.getPageId();
    }

    @Override
    public void onInputDeviceAdded(int deviceId) {
        // ignore
    }

    @Override
    public void onInputDeviceRemoved(int deviceId) {
        if (deviceId == mDeviceId) {
            back();
        }
    }

    @Override
    public void onInputDeviceChanged(int deviceId) {
        // ignore
    }

    private void back() {
        if (getCallbackFragment() instanceof TwoPanelSettingsFragment) {
            TwoPanelSettingsFragment parentFragment =
                    (TwoPanelSettingsFragment) getCallbackFragment();
            if (parentFragment.isFragmentInTheMainPanel(this)) {
                parentFragment.navigateBack();
            }
        } else if (getCallbackFragment() instanceof SliceFragment.OnePanelSliceFragmentContainer) {
            ((SliceFragment.OnePanelSliceFragmentContainer) getCallbackFragment()).navigateBack();
        }
    }
}
