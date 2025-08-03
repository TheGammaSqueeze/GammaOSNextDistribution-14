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

package com.android.car.developeroptions;

import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
import static android.content.pm.PackageManager.MATCH_ALL;
import static android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;

import androidx.preference.Preference;
import androidx.preference.SwitchPreference;

/**
 * Preference controller for Car UI library plugin enablement.
 */
public class CarDevelopmentCarUiLibController extends CarDevelopmentPreferenceController {
    private static final String CAR_UI_PLUGIN_ENABLED_KEY = "car_ui_plugin_enabled";

    public CarDevelopmentCarUiLibController(Context context) {
        super(context);
    }

    @Override
    public String getPreferenceKey() {
        return CAR_UI_PLUGIN_ENABLED_KEY;
    }

    @Override
    public String getPreferenceTitle() {
        return mContext.getString(R.string.car_ui_plugin_enabled_pref_title);
    }

    @Override
    String getPreferenceSummary() {
        if (getCarUiPluginProviderInfo() == null) {
            return mContext.getString(R.string.car_ui_plugin_not_found_text);
        }

        return null;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        Boolean isPluginEnabled = (Boolean) newValue;
        ProviderInfo providerInfo = getCarUiPluginProviderInfo();
        if (providerInfo == null) {
            throw new IllegalStateException("Car UI plugin not found");
        }

        ComponentName componentName = new ComponentName(providerInfo.packageName,
                providerInfo.name);
        int state = isPluginEnabled ? COMPONENT_ENABLED_STATE_ENABLED
                : COMPONENT_ENABLED_STATE_DISABLED;
        mContext.getPackageManager().setComponentEnabledSetting(componentName,
                state, 0 /* no optional flags */);
        return true;
    }

    @Override
    public void updateState(Preference preference) {
        if (getCarUiPluginProviderInfo() == null) {
            ((SwitchPreference) mPreference).setChecked(false);
            mPreference.setEnabled(false);
            return;
        }

        ((SwitchPreference) mPreference).setChecked(isCarUiPluginEnabled());
    }

    private ProviderInfo getCarUiPluginProviderInfo() {
        String authority = mContext.getString(
                R.string.car_ui_plugin_package_provider_authority_name);
        return mContext.getPackageManager().resolveContentProvider(authority,
                MATCH_ALL | MATCH_DISABLED_COMPONENTS);
    }

    private boolean isCarUiPluginEnabled() {
        ProviderInfo providerInfo = getCarUiPluginProviderInfo();
        if (providerInfo == null) {
            return false;
        }

        ComponentName componentName = new ComponentName(providerInfo.packageName,
                providerInfo.name);
        int state = mContext.getPackageManager().getComponentEnabledSetting(componentName);
        if (state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) {
            return providerInfo.enabled;
        }

        return state == COMPONENT_ENABLED_STATE_ENABLED;
    }
}
