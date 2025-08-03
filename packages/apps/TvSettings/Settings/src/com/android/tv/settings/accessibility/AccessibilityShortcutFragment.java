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

import static com.android.tv.settings.util.InstrumentationUtils.logToggleInteracted;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.tvsettings.TvSettingsEnums;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityManager;

import androidx.annotation.Keep;
import androidx.preference.Preference;
import androidx.preference.TwoStatePreference;

import com.android.settingslib.accessibility.AccessibilityUtils;
import com.android.tv.settings.R;
import com.android.tv.settings.SettingsPreferenceFragment;

import java.util.List;

/**
 * Fragment for configuring the accessibility shortcut
 */
@Keep
public class AccessibilityShortcutFragment extends SettingsPreferenceFragment {
    private static final String KEY_ENABLE = "enable";
    private static final String KEY_SERVICE = "service";
    private static final String ACCESSIBILITY_SHORTCUT_STORE = "accessibility_shortcut";
    private static final String LAST_SHORTCUT_SERVICE = "last_shortcut_service";

    private SharedPreferences mSharedPref;
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.accessibility_shortcut, null);

        final TwoStatePreference enablePref = findPreference(KEY_ENABLE);
        final String currentService = getCurrentService(getContext());
        enablePref.setOnPreferenceChangeListener((preference, newValue) -> {
            logToggleInteracted(TvSettingsEnums.SYSTEM_A11Y_SHORTCUT_ON_OFF, (Boolean) newValue);
            setAccessibilityShortcutEnabled((Boolean) newValue);
            return true;
        });
        String enabledComponents = Settings.Secure.getString(getContext().getContentResolver(),
                Settings.Secure.ACCESSIBILITY_SHORTCUT_TARGET_SERVICE);
        mSharedPref = getContext().getSharedPreferences(
                ACCESSIBILITY_SHORTCUT_STORE, Context.MODE_PRIVATE);
        boolean shortcutEnabled = !TextUtils.isEmpty(enabledComponents)
                || TextUtils.isEmpty(getLastShortcutService());
        enablePref.setChecked(shortcutEnabled);
        setAccessibilityShortcutEnabled(shortcutEnabled);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateServicePrefSummary();
    }

    private void updateServicePrefSummary() {
        final Preference servicePref = findPreference(KEY_SERVICE);
        final List<AccessibilityServiceInfo> installedServices = getContext()
                .getSystemService(AccessibilityManager.class)
                .getInstalledAccessibilityServiceList();
        final PackageManager packageManager = getContext().getPackageManager();
        final String currentService = getCurrentService(getContext());
        for (AccessibilityServiceInfo service : installedServices) {
            final String serviceString = service.getComponentName().flattenToString();
            if (TextUtils.equals(currentService, serviceString)) {
                if (servicePref != null) {
                    servicePref.setSummary(service.getResolveInfo().loadLabel(packageManager));
                }
                putLastShortcutService(currentService);
            }
        }
    }
    private void setAccessibilityShortcutEnabled(boolean enabled) {
        if (enabled) {
            String updatedComponent = getCurrentService(getContext());
            if (!TextUtils.isEmpty(updatedComponent)) {
                Settings.Secure.putString(getContext().getContentResolver(),
                        Settings.Secure.ACCESSIBILITY_SHORTCUT_TARGET_SERVICE, updatedComponent);
                updateServicePrefSummary();
            }
        } else {
            Settings.Secure.putString(getContext().getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_SHORTCUT_TARGET_SERVICE, "");
            final Preference servicePref = findPreference(KEY_SERVICE);
            servicePref.setSummary(null);
        }
        final Preference servicePref = findPreference(KEY_SERVICE);
        servicePref.setEnabled(enabled);
    }

    static String getCurrentService(Context context) {
        String shortcutServiceString = AccessibilityUtils
                .getShortcutTargetServiceComponentNameString(context, UserHandle.myUserId());
        if (shortcutServiceString != null) {
            ComponentName shortcutName = ComponentName.unflattenFromString(shortcutServiceString);
            if (shortcutName != null) {
                return shortcutName.flattenToString();
            }
        }
        return null;
    }

    @Override
    protected int getPageId() {
        return TvSettingsEnums.SYSTEM_A11Y_SHORTCUT;
    }

    private String getLastShortcutService() {
        return mSharedPref.getString(LAST_SHORTCUT_SERVICE, "");
    }

    private void putLastShortcutService(String s) {
        mSharedPref.edit().putString(LAST_SHORTCUT_SERVICE, s).apply();
    }
}
