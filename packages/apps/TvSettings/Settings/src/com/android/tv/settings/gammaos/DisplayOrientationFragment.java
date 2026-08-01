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
package com.android.tv.settings.gammaos;

import android.app.tvsettings.TvSettingsEnums;
import android.os.Bundle;
import android.os.SystemProperties;
import android.provider.Settings;

import androidx.annotation.Keep;
import androidx.preference.ListPreference;
import androidx.preference.Preference;

import com.android.tv.settings.R;
import com.android.tv.settings.SettingsPreferenceFragment;

/**
 * Screen orientation for NORMAL Android mode - the counterpart to nano's Settings > Display >
 * Screen Orientation. Both write the SAME props so the two surfaces stay in sync:
 *
 *   persist.gammaos.nano.orientation  - Auto / Landscape / Portrait (+ reverse). This is the single
 *       owner of Settings.System.accelerometer_rotation: "auto" turns auto-rotate ON (the sensor
 *       drives rotation, only meaningful on a device that HAS an accelerometer), any fixed choice
 *       turns it OFF so WindowManagerService.mapOrientationRequest forces that orientation.
 *   persist.gammaos.nano.orient_mode  - Force Landscape / Force Portrait / Off, clamps EVERY app.
 *
 * Rotation support itself is config_supportAutoRotation (true on GammaOS); this screen only exposes
 * the user controls. Auto-rotate defaults OFF because the persisted orientation defaults to a fixed
 * "landscape", so an ATV device stays landscape until the user opts into Auto here.
 */
@Keep
public class DisplayOrientationFragment extends SettingsPreferenceFragment {

    private static final String KEY_ORIENTATION = "persist.gammaos.nano.orientation";
    private static final String KEY_FORCE       = "persist.gammaos.nano.orient_mode";

    public static DisplayOrientationFragment newInstance() {
        return new DisplayOrientationFragment();
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.display_orientation, null);
        bindList(KEY_ORIENTATION, "landscape");
        bindList(KEY_FORCE, "normal");
    }

    @Override
    protected int getPageId() {
        return TvSettingsEnums.PAGE_CLASSIC_DEFAULT;
    }

    private void bindList(String key, String def) {
        ListPreference lp = findPreference(key);
        if (lp == null) return;
        String current = SystemProperties.get(key, def);
        // Keep the stored value representable so it stays selected even if a value was set outside
        // this UI (e.g. via nano or a build.prop) that is not one of the offered entries.
        if (lp.findIndexOfValue(current) < 0) current = def;
        lp.setValue(current);
        lp.setOnPreferenceChangeListener((pref, newValue) -> {
            final String v = String.valueOf(newValue);
            SystemProperties.set(key, v);
            if (KEY_ORIENTATION.equals(key)) {
                // Screen Orientation is the single owner of accelerometer_rotation: "auto" = sensor
                // rotation on, any fixed orientation = off so the forced orientation takes effect.
                // Writing accelerometer_rotation is observed by DisplayRotation and applies live; the
                // forced orientation is then read by mapOrientationRequest on the next layout.
                Settings.System.putInt(getContext().getContentResolver(),
                        Settings.System.ACCELEROMETER_ROTATION, "auto".equals(v) ? 1 : 0);
            }
            return true;   // ListPreference persists the value into the SummaryProvider display
        });
    }
}
