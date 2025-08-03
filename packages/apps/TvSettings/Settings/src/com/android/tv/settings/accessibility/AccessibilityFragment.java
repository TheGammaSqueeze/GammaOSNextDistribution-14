/*
 * Copyright (C) 2017 The Android Open Source Project
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

import static android.content.Context.ACCESSIBILITY_SERVICE;

import static com.android.tv.settings.util.InstrumentationUtils.logToggleInteracted;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.admin.DevicePolicyManager;
import android.app.tvsettings.TvSettingsEnums;
import android.content.ComponentName;
import android.content.pm.ServiceInfo;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityManager;
import android.util.ArrayMap;

import androidx.annotation.Keep;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceCategory;
import androidx.preference.SwitchPreference;
import androidx.preference.TwoStatePreference;

import com.android.settingslib.RestrictedLockUtils.EnforcedAdmin;
import com.android.settingslib.RestrictedLockUtilsInternal;
import com.android.settingslib.RestrictedPreference;
import com.android.settingslib.accessibility.AccessibilityUtils;
import com.android.tv.settings.R;
import com.android.tv.settings.SettingsPreferenceFragment;
import com.android.tv.settings.overlay.FlavorUtils;

import java.util.List;
import java.util.Set;
import java.util.Map;

/**
 * Fragment for Accessibility settings
 */
@Keep
public class AccessibilityFragment extends SettingsPreferenceFragment {
    private static final String TOGGLE_HIGH_TEXT_CONTRAST_KEY = "toggle_high_text_contrast";
    private static final String TOGGLE_AUDIO_DESCRIPTION_KEY = "toggle_audio_description";
    private static final String TOGGLE_BOLD_TEXT_KEY = "toggle_bold_text";
    private static final String COLOR_CORRECTION_TWOPANEL_KEY = "color_correction_only_twopanel";
    private static final String COLOR_CORRECTION_CLASSIC_KEY = "color_correction_only_classic";
    private static final String ACCESSIBILITY_SHORTCUT_KEY = "accessibility_shortcut";
    private static final int BOLD_TEXT_ADJUSTMENT = 500;
    private static final int FIRST_PREFERENCE_IN_CATEGORY_INDEX = -1;

    PreferenceCategory mServicesPrefCategory;
    PreferenceCategory mControlsPrefCategory;

    private final Map<ComponentName, PreferenceCategory>
            mServiceComponentNameToPreferenceCategoryMap = new ArrayMap<>();

    private enum AccessibilityCategory {
        SCREEN_READERS("accessibility_screen_readers_category",
                R.array.config_preinstalled_screen_reader_services),
        DISPLAY("accessibility_display_category",
                R.array.config_preinstalled_display_services),
        INTERACTION_CONTROLS("accessibility_interaction_controls_category",
                R.array.config_preinstalled_interaction_control_services),
        AUDIO_AND_ONSCREEN_TEXT("accessibility_audio_and_onscreen_text_category",
                R.array.config_preinstalled_audio_and_onscreen_text_services),
        EXPERIMENTAL("accessibility_experimental_category",
                R.array.config_preinstalled_experimental_services),
        SERVICES("accessibility_services_category",
                R.array.config_preinstalled_additional_services);

        final String key;
        final int servicesArrayId;

        AccessibilityCategory(String key, int servicesArrayId) {
            this.key = key;
            this.servicesArrayId = servicesArrayId;
        }

        String getKey() {
            return this.key;
        }

        int getServicesArrayId() {
            return this.servicesArrayId;
        }
    }

    private AccessibilityManager.AccessibilityStateChangeListener
            mAccessibilityStateChangeListener = enabled -> refreshServices();

    /**
     * Create a new instance of the fragment
     * @return New fragment instance
     */
    public static AccessibilityFragment newInstance() {
        return new AccessibilityFragment();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshServices();
    }

    @Override
    public void onStop() {
        super.onStop();
        AccessibilityManager am = (AccessibilityManager)
                getContext().getSystemService(ACCESSIBILITY_SERVICE);
        if (am != null) {
            am.removeAccessibilityStateChangeListener(mAccessibilityStateChangeListener);
        }
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.accessibility, null);

        final TwoStatePreference highContrastPreference =
                (TwoStatePreference) findPreference(TOGGLE_HIGH_TEXT_CONTRAST_KEY);
        highContrastPreference.setChecked(Settings.Secure.getInt(getContext().getContentResolver(),
                Settings.Secure.ACCESSIBILITY_HIGH_TEXT_CONTRAST_ENABLED, 0) == 1);

        final TwoStatePreference audioDescriptionPreference =
                (TwoStatePreference) findPreference(TOGGLE_AUDIO_DESCRIPTION_KEY);
        audioDescriptionPreference.setChecked(Settings.Secure.getInt(
                getContext().getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_AUDIO_DESCRIPTION_BY_DEFAULT, 0) == 1);

        final TwoStatePreference boldTextPreference =
                (TwoStatePreference) findPreference(TOGGLE_BOLD_TEXT_KEY);
        boldTextPreference.setChecked(Settings.Secure.getInt(
                getContext().getContentResolver(),
                Settings.Secure.FONT_WEIGHT_ADJUSTMENT, 0) == BOLD_TEXT_ADJUSTMENT);

        Preference colorCorrectionPreferenceToSetVisible = FlavorUtils.isTwoPanel(getContext())
                ? (Preference) findPreference(COLOR_CORRECTION_TWOPANEL_KEY)
                : (Preference) findPreference(COLOR_CORRECTION_CLASSIC_KEY);
        colorCorrectionPreferenceToSetVisible.setVisible(true);

        mServicesPrefCategory = findPreference(AccessibilityCategory.SERVICES.getKey());
        mControlsPrefCategory = findPreference(AccessibilityCategory.INTERACTION_CONTROLS.getKey());
        populateServiceToPreferenceCategoryMaps();
        refreshServices();
        AccessibilityManager am = (AccessibilityManager)
                getContext().getSystemService(ACCESSIBILITY_SERVICE);
        if (am != null) {
            am.addAccessibilityStateChangeListener(mAccessibilityStateChangeListener);
        }
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (TextUtils.equals(preference.getKey(), TOGGLE_HIGH_TEXT_CONTRAST_KEY)) {
            logToggleInteracted(
                    TvSettingsEnums.SYSTEM_A11Y_HIGH_CONTRAST_TEXT,
                    ((SwitchPreference) preference).isChecked());
            Settings.Secure.putInt(getActivity().getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_HIGH_TEXT_CONTRAST_ENABLED,
                    (((SwitchPreference) preference).isChecked() ? 1 : 0));
            return true;
        } else if (TextUtils.equals(preference.getKey(), TOGGLE_AUDIO_DESCRIPTION_KEY)) {
            logToggleInteracted(
                    TvSettingsEnums.SYSTEM_A11Y_AUDIO_DESCRIPTION,
                    ((SwitchPreference) preference).isChecked());
            Settings.Secure.putInt(getActivity().getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_AUDIO_DESCRIPTION_BY_DEFAULT,
                    (((SwitchPreference) preference).isChecked() ? 1 : 0));
            return true;
        } else if (TextUtils.equals(preference.getKey(), TOGGLE_BOLD_TEXT_KEY)) {
            logToggleInteracted(
                    TvSettingsEnums.SYSTEM_A11Y_BOLD_TEXT,
                    ((SwitchPreference) preference).isChecked());
            Settings.Secure.putInt(getActivity().getContentResolver(),
                    Settings.Secure.FONT_WEIGHT_ADJUSTMENT,
                    (((SwitchPreference) preference).isChecked() ? BOLD_TEXT_ADJUSTMENT : 0));
            return true;
        } else {
            return super.onPreferenceTreeClick(preference);
        }
    }

    private void populateServiceToPreferenceCategoryMaps() {
        for (AccessibilityCategory accessibilityCategory : AccessibilityCategory.values()) {
            String[] services = getResources().getStringArray(
                    accessibilityCategory.getServicesArrayId());
            PreferenceCategory prefCategory = findPreference(accessibilityCategory.getKey());
            for (int i = 0; i < services.length; i++) {
                ComponentName component = ComponentName.unflattenFromString(services[i]);
                mServiceComponentNameToPreferenceCategoryMap.put(component, prefCategory);
            }
        }
    }

    private void refreshServices() {
        DevicePolicyManager dpm = getContext().getSystemService(DevicePolicyManager.class);
        final List<AccessibilityServiceInfo> installedServiceInfos =
                getActivity().getSystemService(AccessibilityManager.class)
                        .getInstalledAccessibilityServiceList();
        final Set<ComponentName> enabledServices =
                AccessibilityUtils.getEnabledServicesFromSettings(getActivity());
        final List<String> permittedServices = dpm.getPermittedAccessibilityServices(
                UserHandle.myUserId());

        if (installedServiceInfos.size() == 0) {
            Preference pref = mControlsPrefCategory.findPreference(ACCESSIBILITY_SHORTCUT_KEY);
            if (pref != null) {
                mControlsPrefCategory.removePreference(pref);
            }
        }

        final boolean accessibilityEnabled = Settings.Secure.getInt(
                getActivity().getContentResolver(),
                Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1;

        for (final AccessibilityServiceInfo accInfo : installedServiceInfos) {
            final ServiceInfo serviceInfo = accInfo.getResolveInfo().serviceInfo;
            final ComponentName componentName = new ComponentName(serviceInfo.packageName,
                    serviceInfo.name);
            final boolean serviceEnabled = accessibilityEnabled
                    && enabledServices.contains(componentName);
            // permittedServices null means all accessibility services are allowed.
            final boolean serviceAllowed = permittedServices == null
                    || permittedServices.contains(serviceInfo.packageName);

            final String title = accInfo.getResolveInfo()
                    .loadLabel(getActivity().getPackageManager()).toString();

            final String key = "ServicePref:" + componentName.flattenToString();
            RestrictedPreference servicePref = findPreference(key);
            if (servicePref == null) {
                servicePref = new RestrictedPreference(getContext());
                servicePref.setKey(key);
            }
            servicePref.setTitle(title);
            servicePref.setSummary(serviceEnabled ? R.string.settings_on : R.string.settings_off);
            AccessibilityServiceFragment.prepareArgs(servicePref.getExtras(),
                    serviceInfo.packageName,
                    serviceInfo.name,
                    accInfo.getSettingsActivityName(),
                    title);

            if (serviceAllowed || serviceEnabled) {
                servicePref.setEnabled(true);
                servicePref.setFragment(AccessibilityServiceFragment.class.getName());
            } else {
                // Disable accessibility service that are not permitted.
                final EnforcedAdmin admin =
                        RestrictedLockUtilsInternal.checkIfAccessibilityServiceDisallowed(
                                getContext(), serviceInfo.packageName, UserHandle.myUserId());
                if (admin != null) {
                    servicePref.setDisabledByAdmin(admin);
                } else {
                    servicePref.setEnabled(false);
                }
                servicePref.setFragment(null);
            }

            // Make the screen reader component be the first preference in its preference category.
            final String screenReaderFlattenedComponentName = getResources().getString(
                    R.string.accessibility_screen_reader_flattened_component_name);
            if (componentName.flattenToString().equals(screenReaderFlattenedComponentName)) {
                servicePref.setOrder(FIRST_PREFERENCE_IN_CATEGORY_INDEX);
            }

            PreferenceCategory prefCategory = mServicesPrefCategory;
            if (mServiceComponentNameToPreferenceCategoryMap.containsKey(componentName)) {
                prefCategory = mServiceComponentNameToPreferenceCategoryMap.get(componentName);
            }
            // The method "addPreference" only adds the preference if it is not there already.
            prefCategory.addPreference(servicePref);
        }
        mServicesPrefCategory.setVisible(mServicesPrefCategory.getPreferenceCount() != 0);
        mControlsPrefCategory.setVisible(mControlsPrefCategory.getPreferenceCount() != 0);
    }

    @Override
    protected int getPageId() {
        return TvSettingsEnums.SYSTEM_A11Y;
    }
}
