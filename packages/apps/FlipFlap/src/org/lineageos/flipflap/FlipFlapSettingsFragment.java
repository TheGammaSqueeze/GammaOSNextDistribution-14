/*
 * Copyright (C) 2017-2019 The LineageOS Project
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 * Also add information on how to contact you by electronic and paper mail.
 *
 */

package org.lineageos.flipflap;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.CompoundButton;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragment;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;

import com.android.settingslib.widget.MainSwitchPreference;

import org.lineageos.flipflap.R;

public class FlipFlapSettingsFragment extends PreferenceFragment
        implements Preference.OnPreferenceChangeListener, CompoundButton.OnCheckedChangeListener {

    public final String TAG = "FlipFlapSettings";

    private final String KEY_ENABLE = "flipflap_enable";
    private final String KEY_BEHAVIOUR_CATEGORY = "category_behaviour";
    private final String KEY_DESIGN_CATEGORY = "category_design";
    private final String KEY_TOUCH_SENSITIVITY = "use_high_touch_sensitivity";

    private MainSwitchPreference mSwitchBar;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.flipflapsettings_panel);

        mSwitchBar = (MainSwitchPreference) findPreference(KEY_ENABLE);
        mSwitchBar.addOnSwitchChangeListener(this);

        setupTimeoutPreference(FlipFlapUtils.KEY_TIMEOUT_PLUGGED);
        setupTimeoutPreference(FlipFlapUtils.KEY_TIMEOUT_UNPLUGGED);

        int cover = FlipFlapUtils.getCoverStyle(getActivity());
        if (!FlipFlapUtils.showsChargingStatus(cover)) {
            PreferenceCategory designCategory =
                    getPreferenceScreen().findPreference(KEY_DESIGN_CATEGORY);
            getPreferenceScreen().removePreference(designCategory);
        }
        if (!FlipFlapUtils.getHighTouchSensitivitySupported(getContext())) {
            PreferenceCategory behaviourCategory =
                    getPreferenceScreen().findPreference(KEY_BEHAVIOUR_CATEGORY);
            SwitchPreference touchSensitivityPref = findPreference(KEY_TOUCH_SENSITIVITY);
            behaviourCategory.removePreference(touchSensitivityPref);
        }

        mSwitchBar.setChecked(isEventReceiverEnabled());
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String value = (String) newValue;
        String key = preference.getKey();
        Log.d(TAG, "Preference changed: " + key + ": " + value);

        switch (key) {
            case FlipFlapUtils.KEY_TIMEOUT_PLUGGED:
            case FlipFlapUtils.KEY_TIMEOUT_UNPLUGGED:
                setTimeoutSummary(preference, Integer.parseInt(value));
                return true;

            default:
                return true;

        }
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        ComponentName cn = new ComponentName(getContext(), EventReceiver.class);
        int state = isChecked
                ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                : PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
        getContext().getPackageManager().setComponentEnabledSetting(cn, state,
                PackageManager.DONT_KILL_APP);

        mSwitchBar.setChecked(isChecked);
    }

    private void setupTimeoutPreference(String key) {
        ListPreference list = (ListPreference) findPreference(key);
        list.setOnPreferenceChangeListener(this);
        setTimeoutSummary(list, FlipFlapUtils.getTimeout(getActivity(), key));
    }

    private boolean isEventReceiverEnabled() {
        ComponentName cn = new ComponentName(getContext(), EventReceiver.class);
        int state = getContext().getPackageManager().getComponentEnabledSetting(cn);
        return state != PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
    }

    private void setTimeoutSummary(Preference pref, int timeOut) {
        pref.setSummary(timeOut < 0
                ? R.string.timeout_summary_never
                : timeOut == 0
                    ? R.string.timeout_summary_immediately
                    : R.string.timeout_summary);
    }
}
