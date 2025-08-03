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

package com.android.devicelockcontroller.activities;

import static com.google.common.base.Preconditions.checkNotNull;

import android.os.Bundle;
import android.util.Pair;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;

import com.android.devicelockcontroller.R;

/**
 * The screen which provides info about Device Lock in Settings' style.
 */
public final class DeviceInfoSettingsFragment extends PreferenceFragmentCompat {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.device_info_settings, rootKey);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        DeviceInfoSettingsViewModel viewModel = new ViewModelProvider(this).get(
                DeviceInfoSettingsViewModel.class);
        PreferenceManager preferenceManager = getPreferenceManager();
        LifecycleOwner lifecycleOwner = getViewLifecycleOwner();
        viewModel.getProviderNameLiveData().observe(lifecycleOwner, providerName -> {
            requireActivity().setTitle(
                    getString(R.string.device_provided_by_provider, providerName));

            hideIconView(preferenceManager.getPreferenceScreen());
            for (Pair<Integer, Integer> keyTitlePair :
                    viewModel.getPreferenceWithProviderNameKeyTitlePairs()) {
                Preference preference = preferenceManager.findPreference(
                        getString(keyTitlePair.first));
                checkNotNull(preference);
                preference.setTitle(getString(keyTitlePair.second, providerName));
            }
            Preference supportUrlPreference = preferenceManager.findPreference(
                    getString(R.string.settings_contact_provider_preference_key));
            checkNotNull(supportUrlPreference);
            supportUrlPreference.setTitle(
                    String.format(getString(R.string.settings_contact_provider), providerName,
                            viewModel.getSupportUrl()));
        });
        viewModel.getInstallFromUnknownSourcesDisallowedLiveData().observe(lifecycleOwner,
                disallowed -> {
                    Preference preference = preferenceManager.findPreference(
                            getString(R.string.settings_install_apps_preference_key));
                    checkNotNull(preference);
                    preference.setVisible(disallowed);
                });
    }

    /**
     * Hide the unused icon view of the given {@code preference} and its child preference if any.
     */
    private static void hideIconView(Preference preference) {
        preference.setIconSpaceReserved(false);
        if (preference instanceof PreferenceGroup preferenceGroup) {
            for (int i = 0; i < preferenceGroup.getPreferenceCount(); i++) {
                hideIconView(preferenceGroup.getPreference(i));
            }
        }
    }
}
