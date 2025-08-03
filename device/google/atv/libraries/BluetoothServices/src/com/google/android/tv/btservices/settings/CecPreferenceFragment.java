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

package com.google.android.tv.btservices.settings;

import android.content.Context;
import android.os.Bundle;
import androidx.leanback.preference.LeanbackPreferenceFragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;
import com.google.android.tv.btservices.R;
import com.google.android.tv.btservices.PowerUtils;

public class CecPreferenceFragment extends LeanbackPreferenceFragment {

    private static final String TAG = "Atom.CecPrefFragment";
    private static final String KEY_CEC_ENABLED = "cec-enabled";

    private PreferenceGroup mPrefGroup;

    public static CecPreferenceFragment newInstance() {
        return new CecPreferenceFragment();
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        final Context preferenceContext = getPreferenceManager().getContext();
        mPrefGroup = getPreferenceManager().createPreferenceScreen(preferenceContext);
        mPrefGroup.setTitle(R.string.settings_hdmi_cec);
        mPrefGroup.setOrderingAsAdded(true);

        SwitchPreference cecTogglePref = new SwitchPreference(preferenceContext);
        cecTogglePref.setTitle(R.string.settings_enable_hdmi_cec);
        final boolean isEnabled = PowerUtils.isCecControlEnabled(preferenceContext);
        cecTogglePref.setChecked(isEnabled);
        cecTogglePref.setOnPreferenceChangeListener((preference, newValue) -> {
            PowerUtils.enableCecControl(preferenceContext, ((Boolean) newValue).booleanValue());
            return true;
        });
        mPrefGroup.addPreference(cecTogglePref);

        Preference explain1Pref = new Preference(preferenceContext);
        explain1Pref.setTitle(R.string.settings_cec_explain);
        mPrefGroup.addPreference(explain1Pref);
        explain1Pref.setLayoutResource(R.layout.pref_wall_of_text);
        explain1Pref.setSelectable(false);

        Preference explain2Pref = new Preference(preferenceContext);
        explain2Pref.setTitle(R.string.settings_cec_feature_names);
        mPrefGroup.addPreference(explain2Pref);
        explain2Pref.setLayoutResource(R.layout.pref_wall_of_text);
        explain2Pref.setSelectable(false);

        setPreferenceScreen((PreferenceScreen) mPrefGroup);
    }
}
