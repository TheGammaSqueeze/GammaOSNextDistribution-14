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
import android.os.Bundle;
import android.os.SystemProperties;
import android.text.InputType;
import android.text.TextUtils;
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

import java.util.HashMap;
import java.util.Map;

@Keep
public class GammaOSToolboxFragment extends SettingsPreferenceFragment {

    private static final Map<String, String> DEFAULTS = new HashMap<>();
    static {
        DEFAULTS.put("persist.gammaos.immersive", "0");
        DEFAULTS.put("persist.gammaos.refresh.lock", "false");
        DEFAULTS.put("persist.gammaos.refresh.rate", "0");
        DEFAULTS.put("persist.gammaos.display.tweaks", "false");
        DEFAULTS.put("persist.gammaos.force_client_comp", "false");
        DEFAULTS.put("persist.gammaos.rotation_cooldown", "0");
        DEFAULTS.put("persist.gammaos.desktop.fullscreen", "false");
        DEFAULTS.put("persist.gammaos.display.unique_names", "true");
        DEFAULTS.put("persist.gammaos.sf.keep_underlay_on_shade", "true");
        DEFAULTS.put("persist.gammaos.renderengine.backend", "");
        DEFAULTS.put("persist.gammaos.vsync_period_ns", "0");
        DEFAULTS.put("persist.gammaos.square.sticky_ms", "1200");
        DEFAULTS.put("persist.gammaos.bfi.enable", "false");
        DEFAULTS.put("persist.gammaos.bfi.mode", "ctm");
        DEFAULTS.put("persist.gammaos.bfi.preset", "");
        DEFAULTS.put("persist.gammaos.bfi.pattern", "");
        DEFAULTS.put("persist.gammaos.bfi.black_floor", "0.0");
        DEFAULTS.put("persist.gammaos.bfi.polarity_period_ms", "1000");
        DEFAULTS.put("persist.gammaos.bfi.subframe.enable", "false");
        DEFAULTS.put("persist.gammaos.bfi.subframe.phase_step", "0.5");
        DEFAULTS.put("persist.gammaos.bfi.subframe.cadence_min", "1.0");
        DEFAULTS.put("persist.gammaos.bfi.seam_brightness", "");
        DEFAULTS.put("persist.gammaos.bfi.seam_follow_brightness", "");
        DEFAULTS.put("persist.gammaos.bfi.flip.out_frames", "10");
        DEFAULTS.put("persist.gammaos.bfi.flip.in_frames", "10");
        DEFAULTS.put("persist.gammaos.bfi.flip.sat", "1.0");
        DEFAULTS.put("persist.gammaos.bfi.flip.gamma", "1.0");
        DEFAULTS.put("persist.gammaos.bfi.flip.use_auto", "true");
        DEFAULTS.put("persist.gammaos.bfi.flip.auto_dip", "0.92");
        DEFAULTS.put("persist.gammaos.bfi.flip.contrast.enable", "false");
        DEFAULTS.put("persist.gammaos.bfi.flip.contrast", "1.0");
        DEFAULTS.put("persist.gammaos.bfi.flip.contrast_pivot", "0.5");
        DEFAULTS.put("persist.gammaos.bfi.flip.contrast.hw", "false");
        DEFAULTS.put("persist.gammaos.bfi.flip.zero_eps", "true");
        DEFAULTS.put("persist.gammaos.bfi.flip.rgb.r", "1.0");
        DEFAULTS.put("persist.gammaos.bfi.flip.rgb.g", "1.0");
        DEFAULTS.put("persist.gammaos.bfi.flip.rgb.b", "1.0");
        DEFAULTS.put("persist.gammaos.shader.enable", "false");
        DEFAULTS.put("persist.gammaos.shader.type", "");
        DEFAULTS.put("persist.gammaos.shader.bp_grace_frames", "6");
        DEFAULTS.put("persist.gammaos.shader.custom.preset", "");
        DEFAULTS.put("persist.gammaos.shader.custom.res_scale", "");
        DEFAULTS.put("persist.gammaos.shader.crt-simple.scan_px", "4");
        DEFAULTS.put("persist.gammaos.shader.crt-simple.scan_strength", "0.4");
        DEFAULTS.put("persist.gammaos.shader.crt-simple.curv", "0.03");
        DEFAULTS.put("persist.gammaos.shader.crt-simple.vignette", "0.01");
        DEFAULTS.put("persist.gammaos.shader.crt-simple.edge_soft_px", "4");
        DEFAULTS.put("persist.gammaos.shader.crt-simple.blur_intensity", "0");
        DEFAULTS.put("persist.gammaos.shader.crt-simple.half_res", "false");
        DEFAULTS.put("persist.gammaos.shader.lcd3x.half_res", "false");
        DEFAULTS.put("persist.gammaos.shader.lcd3x.brighten_scanlines", "4.0");
        DEFAULTS.put("persist.gammaos.shader.lcd3x.brighten_lcd", "4.0");
        DEFAULTS.put("persist.gammaos.shader.lcd3x.grid_px_x", "4.0");
        DEFAULTS.put("persist.gammaos.shader.lcd3x.grid_px_y", "4.0");
        DEFAULTS.put("persist.gammaos.shader.lcd.half_res", "false");
        DEFAULTS.put("persist.gammaos.shader.lcd.response_time", "0");
        DEFAULTS.put("persist.gammaos.shader.lcd.scan_strength", "0.20");
        DEFAULTS.put("persist.gammaos.shader.lcd.subpixel_strength", "0.40");
        DEFAULTS.put("persist.gammaos.shader.lcd.gap_strength", "0.10");
        DEFAULTS.put("persist.gammaos.shader.lcd.gap_px", "0.05");
        DEFAULTS.put("persist.gammaos.shader.blurfill.sigma", "12.0");
        DEFAULTS.put("persist.gammaos.shader.blurfill.strength", "1.0");
        DEFAULTS.put("persist.gammaos.shader.blurfill.edge_px", "160.0");
        DEFAULTS.put("persist.gammaos.shader.blurfill.feather_px", "40.0");
        DEFAULTS.put("persist.gammaos.shader.blurfill.res_scale", "0.5");
        DEFAULTS.put("persist.gammaos.shader.blurfill.orientation", "auto");
        DEFAULTS.put("persist.gammaos.dualstack.enabled", "false");
        DEFAULTS.put("persist.gammaos.dualstack.swap", "false");
        DEFAULTS.put("persist.gammaos.dualstack.pkgs", "");
        DEFAULTS.put("persist.gammaos.dualstack.killpackages.enabled", "false");
        DEFAULTS.put("persist.gammaos.dualstack.sf.nearest_neighbor", "true");
        DEFAULTS.put("persist.gammaos.dualstack.sf.surfaceview_only", "true");
        DEFAULTS.put("persist.gammaos.dualstack.sf.surfaceview_only.keep_systemui", "true");
        DEFAULTS.put("persist.gammaos.dualstack.sf.max_fb_acquired_buffers", "3");
        DEFAULTS.put("persist.gammaos.dualstack.sf.disable_gl_backpressure", "true");
        DEFAULTS.put("persist.gammaos.dualstack.blast.tune", "true");
        DEFAULTS.put("persist.gammaos.dualstack.blast.async", "true");
        DEFAULTS.put("persist.gammaos.ext.primary", "false");
        DEFAULTS.put("persist.gammaos.ext.force_mirror", "false");
        DEFAULTS.put("persist.gammaos.ext.mirror_resize", "false");
        DEFAULTS.put("persist.gammaos.ext.half_4k", "false");
        DEFAULTS.put("persist.gammaos.sec_force_on", "false");
        DEFAULTS.put("persist.gammaos.secondary_home", "");
        DEFAULTS.put("persist.gammaos.secondary_display.enabled", "false");
        DEFAULTS.put("persist.gammaos.secondary_display.packages", "");
        DEFAULTS.put("persist.gammaos.display.delay.primary_frames", "0");
        DEFAULTS.put("persist.gammaos.display.delay.external_frames", "0");
        DEFAULTS.put("persist.gammaos.multidisplay.dual_focus", "false");
        DEFAULTS.put("persist.gammaos.multidisplay.split_brightness", "false");
        DEFAULTS.put("persist.gammaos.ime.pin.enabled", "false");
        DEFAULTS.put("persist.gammaos.ime.pin.display_id", "0");
        DEFAULTS.put("persist.gammaos.ime.pin.swap", "false");
        DEFAULTS.put("persist.gammaos.audio.multivolume", "false");
        DEFAULTS.put("persist.gammaos.unisoc.hdmi.enable", "false");
        DEFAULTS.put("persist.gammaos.allwinner.hdmi.enable", "false");
        DEFAULTS.put("persist.gammaos.gamepad.enable", "false");
        DEFAULTS.put("persist.gammaos.gamepad.merge", "1");
        DEFAULTS.put("persist.gammaos.gamepad.hide_source", "1");
        DEFAULTS.put("persist.gammaos.gamepad.devices", "");
        DEFAULTS.put("persist.gammaos.gamepad.abxy_swap", "0");
        DEFAULTS.put("persist.gammaos.gamepad.invert_left", "0");
        DEFAULTS.put("persist.gammaos.gamepad.invert_right", "0");
        DEFAULTS.put("persist.gammaos.gamepad.analog_to_dpad", "0");
        DEFAULTS.put("persist.gammaos.gamepad.dpad_to_analog", "0");
        DEFAULTS.put("persist.gammaos.gamepad.dpad_threshold", "50");
        DEFAULTS.put("persist.gammaos.gamepad.global_sensitivity", "0");
        DEFAULTS.put("persist.gammaos.gamepad.pwm_enable", "1");
        DEFAULTS.put("persist.gammaos.gamepad.pwm_intensity", "255");
        DEFAULTS.put("persist.gammaos.gamepad.device_name", "Xbox Wireless Controller");
        DEFAULTS.put("persist.gammaos.gamepad.remap_btn", "");
        DEFAULTS.put("persist.gammaos.gamepad.remap_axis", "");
        DEFAULTS.put("persist.gammaos.gamepad.combo_map", "");
        DEFAULTS.put("persist.gammaos.gamepad.axis_btn", "");
        DEFAULTS.put("persist.gammaos.gamepad.ff_vibrate_device", "");
        DEFAULTS.put("persist.gammaos.gamepad.blacklist_pass", "");
        DEFAULTS.put("persist.gammaos.screenmap.enabled", "0");
        DEFAULTS.put("persist.gammaos.gamepad.mouse_stick_speed", "12");
        DEFAULTS.put("persist.gammaos.gamepad.mouse_dpad_speed", "6");
        DEFAULTS.put("persist.gammaos.gamepad.mouse_boost", "20");
        DEFAULTS.put("persist.gammaos.gamepad.mouse_scroll_speed", "4");
        DEFAULTS.put("persist.gammaos.rgb.enable", "false");
        DEFAULTS.put("persist.gammaos.rgb.fps", "6");
        DEFAULTS.put("persist.gammaos.rgb.led_brightness", "255");
        DEFAULTS.put("persist.gammaos.rgb.scale_with_brightness", "false");
        DEFAULTS.put("persist.gammaos.rgb.fade.enable", "true");
        DEFAULTS.put("persist.gammaos.rgb.fade.fps", "60");
        DEFAULTS.put("persist.gammaos.rgb.sample.pre_fx", "true");
        DEFAULTS.put("persist.gammaos.rgb.effect", "");
        DEFAULTS.put("persist.gammaos.rgb.split", "false");
        DEFAULTS.put("persist.gammaos.rgb.saturation_boost", "1.4");
        DEFAULTS.put("persist.gammaos.launch.guard.enabled", "false");
        DEFAULTS.put("persist.gammaos.launch.guard.callers", "");
        DEFAULTS.put("persist.gammaos.launch.guard.targets", "");
        DEFAULTS.put("persist.gammaos.qs.blacklist", "");
        DEFAULTS.put("persist.gammaos.bg_process_limit", "");
        DEFAULTS.put("persist.gammaos.gesture_wake_ignore", "");
        DEFAULTS.put("persist.gammaos.performance_mode", "stock");
        DEFAULTS.put("persist.gammaos.qs.override_default_tiles", "");
        DEFAULTS.put("persist.gammaos.fan_mode", "");
        DEFAULTS.put("persist.gammaos.ultra_low_power_saving_mode", "false");
        DEFAULTS.put("persist.gammaos.ultra_low_power_saving_freeze_exclude_packages", "");
        DEFAULTS.put("persist.gammaos.retroarchoverride.backbutton", "0");
        DEFAULTS.put("persist.gammaos.startselectled", "0");
        DEFAULTS.put("persist.gammaos.usbcontrollerswitch", "false");
        DEFAULTS.put("persist.gammaos.dcdimmingemulation", "false");
        DEFAULTS.put("persist.gammaos.taskbar.phone", "true");
        DEFAULTS.put("persist.gammaos.taskbar.dual", "false");
        DEFAULTS.put("persist.gammaos.wallpaper.force_multidisplay", "false");
    }

    public static GammaOSToolboxFragment newInstance() {
        return new GammaOSToolboxFragment();
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.gammaos_toolbox, null);
        bindAllPreferences(getPreferenceScreen());
    }

    @Override
    protected int getPageId() {
        return TvSettingsEnums.PAGE_CLASSIC_DEFAULT;
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
            if (usesIntStyle) {
                SystemProperties.set(key, val ? "1" : "0");
            } else {
                SystemProperties.set(key, String.valueOf(val));
            }
            if ("persist.gammaos.taskbar.phone".equals(key)) {
                android.provider.Settings.Secure.putInt(
                        getActivity().getContentResolver(),
                        "gamma_phone_taskbar_toggle", val ? 1 : 0);
            }
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

        etp.setOnPreferenceClickListener(pref -> {
            showEditDialog(etp, key, def);
            return true;
        });
    }

    private void showEditDialog(EditTextPreference etp, String key, String def) {
        String current = SystemProperties.get(key, def);

        final EditText input = new EditText(getContext());
        input.setText(current);
        input.setSelectAllOnFocus(true);

        if (isIntegerField(key, def)) {
            input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        } else if (isDecimalField(def)) {
            input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                    | InputType.TYPE_NUMBER_FLAG_SIGNED);
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
                    String raw = input.getText().toString();
                    String val = sanitize(key, def, raw);
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

    private String sanitize(String key, String def, String raw) {
        if (raw == null) raw = "";
        raw = raw.trim();
        if (raw.isEmpty()) return def;

        if (isIntegerField(key, def)) {
            try {
                Integer.parseInt(raw);
                return raw;
            } catch (NumberFormatException e) {
                return null;
            }
        }

        if (isDecimalField(def)) {
            try {
                Float.parseFloat(raw);
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

    private boolean isIntegerField(String key, String def) {
        if (def.isEmpty()) return false;
        try {
            Integer.parseInt(def);
            return !def.contains(".");
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isDecimalField(String def) {
        if (def.isEmpty()) return false;
        return def.contains(".");
    }
}
