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

import android.app.AlertDialog;
import android.app.tvsettings.TvSettingsEnums;
import android.hardware.input.InputManager;
import android.os.Bundle;
import android.os.SystemProperties;
import android.text.InputType;
import android.text.TextUtils;
import android.view.InputDevice;
import android.widget.EditText;
import android.widget.FrameLayout;

import androidx.annotation.Keep;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.SwitchPreference;

import com.android.tv.settings.R;
import com.android.tv.settings.SettingsPreferenceFragment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Settings for the hardware "slide" (swivel) input: which input device and button to watch, and
 * what to do on the slide DOWN and UP events. All values are plain persist.* system properties
 * that the framework (PhoneWindowManager.interceptGammaRotateKey) and GammaOS Nano read directly,
 * so this screen is pure prop configuration - no device-specific code. The current F12 / gpio-keys
 * values are the shipped defaults.
 */
@Keep
public class SlideBehaviorFragment extends SettingsPreferenceFragment {

    private static final Map<String, String> DEFAULTS = new HashMap<>();
    static {
        // Slide detection.
        DEFAULTS.put("persist.gammaos.rotate.enabled", "0");
        DEFAULTS.put("persist.gammaos.rotate.key_type", "1");
        DEFAULTS.put("persist.gammaos.rotate.dev_name", "gpio-keys");
        DEFAULTS.put("persist.gammaos.rotate.key_code", "88");
        DEFAULTS.put("persist.gammaos.rotate.key_active", "1");
        // Slide down / up behaviour.
        DEFAULTS.put("persist.gammaos.rotate.down_action", "rotate");
        DEFAULTS.put("persist.gammaos.rotate.up_action", "natural");
        DEFAULTS.put("persist.gammaos.rotate.degrees", "90");
        DEFAULTS.put("persist.gammaos.rotate.launch_target", "");
        // PSP slide clock overlay (an independent overlay that can show alongside the rotate
        // action, so it lives here as its own toggle rather than as an exclusive slide action).
        DEFAULTS.put("persist.gammaos.nano.pspclock", "0");
        DEFAULTS.put("persist.gammaos.nano.pspclock.liveapp", "0");
        DEFAULTS.put("persist.gammaos.nano.pspclock.tilt.cal", "0.06,1,-1,-1");
    }

    public static SlideBehaviorFragment newInstance() {
        return new SlideBehaviorFragment();
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.slide_behavior, null);
        populateDeviceList();
        bindAllPreferences(getPreferenceScreen());
    }

    /**
     * Fill the "Slide device" list from the currently attached input devices. The first choice is
     * always "Any device" (empty value = match anything); the rest are the live device names from
     * InputManager. Whatever value is currently persisted is added too if it is not among the live
     * names, so a value pointing at an unplugged / boot-time device (e.g. "gpio-keys") still shows
     * up and stays selected. Entries/values must be set here, before bindAllPreferences() runs, so
     * that bindList() can resolve and pre-select the current value.
     */
    private void populateDeviceList() {
        ListPreference lp = findPreference("persist.gammaos.rotate.dev_name");
        if (lp == null) return;

        List<CharSequence> entries = new ArrayList<>();
        List<CharSequence> values = new ArrayList<>();
        entries.add(getString(R.string.slide_behavior_dev_name_any));
        values.add("");

        String current = SystemProperties.get(
                "persist.gammaos.rotate.dev_name",
                DEFAULTS.getOrDefault("persist.gammaos.rotate.dev_name", ""));

        InputManager im = getContext().getSystemService(InputManager.class);
        if (im != null) {
            for (int id : im.getInputDeviceIds()) {
                InputDevice dev = im.getInputDevice(id);
                if (dev == null) continue;
                String name = dev.getName();
                if (TextUtils.isEmpty(name)) continue;
                if (!values.contains(name)) {
                    entries.add(name);
                    values.add(name);
                }
            }
        }

        // Make sure the current selection is representable so it stays selected.
        if (!TextUtils.isEmpty(current) && !values.contains(current)) {
            entries.add(current);
            values.add(current);
        }

        lp.setEntries(entries.toArray(new CharSequence[0]));
        lp.setEntryValues(values.toArray(new CharSequence[0]));
    }

    @Override
    protected int getPageId() {
        return TvSettingsEnums.PAGE_CLASSIC_DEFAULT;
    }

    @Override
    public void onDisplayPreferenceDialog(Preference preference) {
        if (preference instanceof EditTextPreference) {
            String key = preference.getKey();
            if (key != null && key.startsWith("persist.gammaos.")) {
                String def = DEFAULTS.getOrDefault(key, "");
                showEditDialog((EditTextPreference) preference, key, def);
                return;
            }
        }
        super.onDisplayPreferenceDialog(preference);
    }

    private void bindAllPreferences(PreferenceGroup group) {
        for (int i = 0; i < group.getPreferenceCount(); i++) {
            Preference pref = group.getPreference(i);
            if (pref instanceof PreferenceGroup) {
                bindAllPreferences((PreferenceGroup) pref);
                continue;
            }
            String key = pref.getKey();
            if (key == null || !key.startsWith("persist.gammaos.")) continue;

            if (pref instanceof SwitchPreference) {
                bindSwitch((SwitchPreference) pref, key);
            } else if (pref instanceof ListPreference) {
                bindList((ListPreference) pref, key);
            } else if (pref instanceof EditTextPreference) {
                bindEditText((EditTextPreference) pref, key);
            }
        }
    }

    private void bindSwitch(SwitchPreference sw, String key) {
        String def = DEFAULTS.getOrDefault(key, "false");
        String raw = SystemProperties.get(key, def);
        boolean current = "true".equalsIgnoreCase(raw) || "1".equals(raw);
        sw.setChecked(current);

        boolean usesIntStyle = "0".equals(def) || "1".equals(def);
        sw.setOnPreferenceChangeListener((p, newValue) -> {
            boolean val = (Boolean) newValue;
            SystemProperties.set(key, usesIntStyle ? (val ? "1" : "0") : String.valueOf(val));
            return true;
        });
    }

    private void bindList(ListPreference lp, String key) {
        String def = DEFAULTS.getOrDefault(key, "");
        String current = SystemProperties.get(key, def);
        lp.setValue(current);
        updateListSummary(lp, current);

        lp.setOnPreferenceChangeListener((p, newValue) -> {
            String val = (String) newValue;
            SystemProperties.set(key, val);
            updateListSummary(lp, val);
            return true;
        });
    }

    private void updateListSummary(ListPreference lp, String value) {
        int idx = lp.findIndexOfValue(value);
        if (idx >= 0) {
            lp.setSummary(lp.getEntries()[idx]);
        }
    }

    private void bindEditText(EditTextPreference etp, String key) {
        String def = DEFAULTS.getOrDefault(key, "");
        String current = SystemProperties.get(key, def);
        etp.setText(current);
        updateEditTextSummary(etp, current);
    }

    private void showEditDialog(EditTextPreference etp, String key, String def) {
        String current = SystemProperties.get(key, def);

        final EditText input = new EditText(getContext());
        input.setText(current);
        input.setSelectAllOnFocus(true);
        // key_code is a pure integer; everything else (device name, launch target, the
        // "gain,rot,sx,sy" parallax calibration) is free text.
        if (isIntegerField(def)) {
            input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        } else {
            input.setInputType(InputType.TYPE_CLASS_TEXT);
        }

        FrameLayout container = new FrameLayout(getContext());
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        container.setPadding(pad, 0, pad, 0);
        container.addView(input);

        new AlertDialog.Builder(getContext())
                .setTitle(etp.getTitle())
                .setView(container)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String val = sanitize(def, input.getText().toString());
                    if (val != null) {
                        SystemProperties.set(key, val);
                        etp.setText(val);
                        updateEditTextSummary(etp, val);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void updateEditTextSummary(EditTextPreference etp, String value) {
        if (TextUtils.isEmpty(value)) {
            etp.setSummary(getString(R.string.gammaos_toolbox_value_not_set));
        } else {
            etp.setSummary(value);
        }
    }

    private String sanitize(String def, String raw) {
        if (raw == null) raw = "";
        raw = raw.trim();
        if (raw.isEmpty()) return def;
        if (isIntegerField(def)) {
            try {
                Integer.parseInt(raw);
                return raw;
            } catch (NumberFormatException e) {
                return null;
            }
        }
        if (raw.length() > 91) {
            raw = raw.substring(0, 91);
        }
        return raw;
    }

    // A field is integer only if its default parses as a plain integer with no dot and no comma
    // (so "0.06,1,-1,-1" and "gpio-keys" are text, "88" is integer).
    private boolean isIntegerField(String def) {
        if (def.isEmpty() || def.contains(".") || def.contains(",")) return false;
        try {
            Integer.parseInt(def);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
