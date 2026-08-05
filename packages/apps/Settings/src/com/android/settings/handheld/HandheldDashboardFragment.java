/*
 * Copyright (C) 2024 GammaOS
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
package com.android.settings.handheld;

import android.app.settings.SettingsEnums;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;

import androidx.preference.Preference;

import com.android.settings.R;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

@SearchIndexable
public class HandheldDashboardFragment extends DashboardFragment {

    private static final String TAG = "HandheldDashboardFrag";

    // Shortcut preference key -> external control-app component. These apps are installed on the
    // GammaOS builds that ship the corresponding hardware feature; where the package is absent the
    // shortcut is hidden (see wireToolShortcuts).
    private static final String[][] TOOL_SHORTCUTS = {
        {"gtool_shaders",     "com.gammaos.shadercontrol",           "com.gammaos.shadercontrol.MainActivity"},
        {"gtool_dualstack",   "com.gammaos.dualstackcontrol",        "com.gammaos.dualstackcontrol.MainActivity"},
        {"gtool_secondary",   "com.gammaos.secondarydisplaycontrol", "com.gammaos.secondarydisplaycontrol.MainActivity"},
        {"gtool_led",         "com.gammaos.joystickled",             "com.gammaos.joystickled.MainActivity"},
        {"gtool_eq",          "com.gammaos.gammaeq",                 "com.gammaos.gammaeq.MainActivity"},
        {"gtool_launchguard", "com.gammaos.launchguardcontrol",      "com.gammaos.launchguardcontrol.MainActivity"},
    };

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.SETTINGS_SYSTEM_CATEGORY;
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.handheld_dashboard_fragment;
    }

    @Override
    public void onResume() {
        super.onResume();
        wireToolShortcuts();
    }

    /**
     * Bind each control-app shortcut to launch its activity by explicit component, and hide the
     * ones whose target app is not installed. Idempotent, so it is safe to run on every resume.
     */
    private void wireToolShortcuts() {
        if (getContext() == null) {
            return;
        }
        final PackageManager pm = getContext().getPackageManager();
        boolean anyVisible = false;
        for (String[] s : TOOL_SHORTCUTS) {
            final Preference pref = findPreference(s[0]);
            if (pref == null) {
                continue;
            }
            final Intent intent = new Intent(Intent.ACTION_MAIN)
                    .setComponent(new ComponentName(s[1], s[2]))
                    .setPackage(s[1]);
            if (pm.resolveActivity(intent, 0) != null) {
                pref.setVisible(true);
                pref.setOnPreferenceClickListener(p -> {
                    try {
                        startActivity(intent);
                    } catch (Exception ignored) {
                    }
                    return true;
                });
                anyVisible = true;
            } else {
                pref.setVisible(false);
            }
        }
        // If none of the control apps are installed, hide the whole category and its caveat.
        final Preference caveat = findPreference("gtools_caveat");
        if (caveat != null) {
            caveat.setVisible(anyVisible);
        }
        final Preference category = findPreference("gtools_category");
        if (category != null) {
            category.setVisible(anyVisible);
        }
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.handheld_dashboard_fragment);
}
