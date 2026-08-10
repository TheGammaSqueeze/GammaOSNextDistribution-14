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

import android.app.AlertDialog;
import android.app.settings.SettingsEnums;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.hardware.input.InputManager;
import android.os.Bundle;
import android.os.SystemProperties;
import android.text.InputType;
import android.text.TextUtils;
import android.view.InputDevice;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * GammaOS Toolbox — surfaces all persist.gammaos.* system properties as
 * user-editable preferences.  Boolean properties are rendered as switches,
 * enumerated properties as dropdown lists, and everything else as text
 * fields with appropriate input-type filtering.
 *
 * Every property key doubles as the Preference key so lookups are
 * straightforward.  The fragment reads the live value from
 * {@link SystemProperties} on creation and writes changes back
 * immediately.
 */
@SearchIndexable
public class GammaOSToolboxFragment extends SettingsPreferenceFragment {

    private static final String TAG = "GammaOSToolboxFrag";

    /* Default values mirroring what the native consumers use. */
    private static final Map<String, String> DEFAULTS = new HashMap<>();
    static {
        // Display
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

        // Screen Rotation (hardware rotation key)
        DEFAULTS.put("persist.gammaos.rotate.enabled", "false");
        DEFAULTS.put("persist.gammaos.rotate.dev_name", "");
        DEFAULTS.put("persist.gammaos.rotate.key_code", "88");
        DEFAULTS.put("persist.gammaos.rotate.key_type", "1");
        DEFAULTS.put("persist.gammaos.rotate.key_active", "1");
        DEFAULTS.put("persist.gammaos.rotate.down_action", "rotate");
        DEFAULTS.put("persist.gammaos.rotate.up_action", "natural");
        DEFAULTS.put("persist.gammaos.rotate.degrees", "90");
        DEFAULTS.put("persist.gammaos.rotate.sleep_delay", "0");
        DEFAULTS.put("persist.gammaos.rotate.launch_target", "");

        // BFI
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

        // BFI Flip
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

        // Shader
        DEFAULTS.put("persist.gammaos.shader.enable", "false");
        DEFAULTS.put("persist.gammaos.shader.type", "");
        DEFAULTS.put("persist.gammaos.shader.bp_grace_frames", "6");
        DEFAULTS.put("persist.gammaos.shader.custom.preset", "");
        DEFAULTS.put("persist.gammaos.shader.custom.res_scale", "");

        // CRT Simple shader
        DEFAULTS.put("persist.gammaos.shader.crt-simple.scan_px", "4");
        DEFAULTS.put("persist.gammaos.shader.crt-simple.scan_strength", "0.4");
        DEFAULTS.put("persist.gammaos.shader.crt-simple.curv", "0.03");
        DEFAULTS.put("persist.gammaos.shader.crt-simple.vignette", "0.01");
        DEFAULTS.put("persist.gammaos.shader.crt-simple.edge_soft_px", "4");
        DEFAULTS.put("persist.gammaos.shader.crt-simple.blur_intensity", "0");
        DEFAULTS.put("persist.gammaos.shader.crt-simple.half_res", "false");

        // LCD3x shader
        DEFAULTS.put("persist.gammaos.shader.lcd3x.half_res", "false");
        DEFAULTS.put("persist.gammaos.shader.lcd3x.brighten_scanlines", "4.0");
        DEFAULTS.put("persist.gammaos.shader.lcd3x.brighten_lcd", "4.0");
        DEFAULTS.put("persist.gammaos.shader.lcd3x.grid_px_x", "4.0");
        DEFAULTS.put("persist.gammaos.shader.lcd3x.grid_px_y", "4.0");

        // LCD shader
        DEFAULTS.put("persist.gammaos.shader.lcd.half_res", "false");
        DEFAULTS.put("persist.gammaos.shader.lcd.response_time", "0");
        DEFAULTS.put("persist.gammaos.shader.lcd.scan_strength", "0.20");
        DEFAULTS.put("persist.gammaos.shader.lcd.subpixel_strength", "0.40");
        DEFAULTS.put("persist.gammaos.shader.lcd.gap_strength", "0.10");
        DEFAULTS.put("persist.gammaos.shader.lcd.gap_px", "0.05");

        // Blur fill shader
        DEFAULTS.put("persist.gammaos.shader.blurfill.sigma", "12.0");
        DEFAULTS.put("persist.gammaos.shader.blurfill.strength", "1.0");
        DEFAULTS.put("persist.gammaos.shader.blurfill.edge_px", "160.0");
        DEFAULTS.put("persist.gammaos.shader.blurfill.feather_px", "40.0");
        DEFAULTS.put("persist.gammaos.shader.blurfill.res_scale", "0.5");
        DEFAULTS.put("persist.gammaos.shader.blurfill.orientation", "auto");

        // Dual-stack
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

        // External display
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

        // Multi-display
        DEFAULTS.put("persist.gammaos.multidisplay.dual_focus", "false");
        DEFAULTS.put("persist.gammaos.multidisplay.split_brightness", "false");

        // IME
        DEFAULTS.put("persist.gammaos.ime.pin.enabled", "false");
        DEFAULTS.put("persist.gammaos.ime.pin.display_id", "0");
        DEFAULTS.put("persist.gammaos.ime.pin.swap", "false");

        // Audio
        DEFAULTS.put("persist.gammaos.audio.multivolume", "false");
        DEFAULTS.put("persist.gammaos.unisoc.hdmi.enable", "false");
        DEFAULTS.put("persist.gammaos.allwinner.hdmi.enable", "false");

        // Gamepad
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

        // Mouse mode
        DEFAULTS.put("persist.gammaos.gamepad.mouse_stick_speed", "12");
        DEFAULTS.put("persist.gammaos.gamepad.mouse_dpad_speed", "6");
        DEFAULTS.put("persist.gammaos.gamepad.mouse_boost", "20");
        DEFAULTS.put("persist.gammaos.gamepad.mouse_scroll_speed", "4");

        // RGB
        // "1" (int style), not "false": the vendor init.gammargb.rc only starts/stops gammargb on
        // persist.gammaos.rgb.enable=1 / =0, so a "true"/"false" value never turns the LEDs off.
        DEFAULTS.put("persist.gammaos.rgb.enable", "1");
        DEFAULTS.put("persist.gammaos.rgb.fps", "6");
        DEFAULTS.put("persist.gammaos.rgb.led_brightness", "255");
        DEFAULTS.put("persist.gammaos.rgb.scale_with_brightness", "false");
        DEFAULTS.put("persist.gammaos.rgb.fade.enable", "true");
        DEFAULTS.put("persist.gammaos.rgb.fade.fps", "60");
        DEFAULTS.put("persist.gammaos.rgb.sample.pre_fx", "true");
        DEFAULTS.put("persist.gammaos.rgb.effect", "");
        DEFAULTS.put("persist.gammaos.rgb.split", "false");
        DEFAULTS.put("persist.gammaos.rgb.saturation_boost", "1.4");

        // Launch guard
        DEFAULTS.put("persist.gammaos.launch.guard.enabled", "false");
        DEFAULTS.put("persist.gammaos.launch.guard.callers", "");
        DEFAULTS.put("persist.gammaos.launch.guard.targets", "");

        // System
        DEFAULTS.put("persist.gammaos.drm.force_l3", "true");
        DEFAULTS.put("persist.gammaos.qs.blacklist", "");
        DEFAULTS.put("persist.gammaos.bg_process_limit", "");
        DEFAULTS.put("persist.gammaos.gesture_wake_ignore", "");
        DEFAULTS.put("persist.gammaos.performance_mode", "stock");
        DEFAULTS.put("persist.gammaos.qs.override_default_tiles", "");

        // Power & Performance
        DEFAULTS.put("persist.gammaos.fan_mode", "");
        // 0/1, not true/false: the vendor init.gammaos_power.rc force_sleep trigger does
        // an exact "=1" match; a boolean value here never matched and left deep sleep off.
        DEFAULTS.put("persist.gammaos.ultra_low_power_saving_mode", "0");
        DEFAULTS.put("persist.gammaos.ultra_low_power_saving_freeze_exclude_packages", "");
        // Virtual memory (swap) size in MB; 0 = off. Integer default so a custom
        // value is validated numerically. gammaos-swap.sh applies it at boot.
        DEFAULTS.put("persist.gammaos.swap.size_mb", "0");

        // RetroArch
        DEFAULTS.put("persist.gammaos.retroarchoverride.backbutton", "0");
        DEFAULTS.put("persist.gammaos.startselectled", "0");

        // USB & Docking
        DEFAULTS.put("persist.gammaos.usbcontrollerswitch", "false");
        DEFAULTS.put("persist.gammaos.dcdimmingemulation", "false");

        // Desktop extras
        DEFAULTS.put("persist.gammaos.taskbar.phone", "true");
        DEFAULTS.put("persist.gammaos.taskbar.dual", "false");
        DEFAULTS.put("persist.gammaos.wallpaper.force_multidisplay", "false");
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.SETTINGS_SYSTEM_CATEGORY;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.gammaos_toolbox);
        // Populate the dynamic input-device list before the generic binder runs,
        // so bindList() can preselect the current value and show its summary.
        populateRotateDeviceList();
        bindAllPreferences(getPreferenceScreen());
        // Swap size needs custom handling (a "Custom..." entry that types any size),
        // so bind it after the generic binder to override its listener.
        bindSwapSize();
        // Launch target is a two-level app -> activity picker, not a free-text field.
        bindLaunchTarget();
    }

    /* ------------------------------------------------------------------ */
    /*  Virtual memory (swap): preset list + a "Custom..." numeric entry  */
    /* ------------------------------------------------------------------ */

    private static final String SWAP_KEY = "persist.gammaos.swap.size_mb";
    // Never let a typo request a swap file bigger than the storage can hold.
    private static final int SWAP_MAX_MB = 16384;

    private void bindSwapSize() {
        ListPreference lp = (ListPreference) findPreference(SWAP_KEY);
        if (lp == null) return;
        String current = SystemProperties.get(SWAP_KEY, "0");
        addCustomSwapEntryIfNeeded(lp, current);
        lp.setValue(current);
        updateListSummary(lp, current);
        lp.setOnPreferenceChangeListener((p, newValue) -> {
            String val = (String) newValue;
            if ("custom".equals(val)) {
                showSwapCustomDialog(lp);
                return false;   // the dialog persists the chosen size itself
            }
            SystemProperties.set(SWAP_KEY, val);
            updateListSummary(lp, val);
            return true;
        });
    }

    /**
     * If the persisted swap size is a positive number that is not one of the
     * presets, splice it into the list (just before the trailing "Custom..." row)
     * so the picker can preselect it and show its label.
     */
    private void addCustomSwapEntryIfNeeded(ListPreference lp, String value) {
        if (TextUtils.isEmpty(value)) return;
        int n;
        try {
            n = Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return;
        }
        if (n <= 0) return;
        CharSequence[] curVals = lp.getEntryValues();
        CharSequence[] curEntries = lp.getEntries();
        if (curVals == null || curEntries == null) return;
        for (CharSequence v : curVals) {
            if (value.equals(v.toString())) return;   // already a preset (or already added)
        }
        List<CharSequence> entries = new ArrayList<>();
        List<CharSequence> values = new ArrayList<>();
        for (int i = 0; i < curVals.length && i < curEntries.length; i++) {
            if ("custom".equals(curVals[i].toString())) {
                entries.add(getString(R.string.gammaos_toolbox_swap_size_mb_fmt, n));
                values.add(value);
            }
            entries.add(curEntries[i]);
            values.add(curVals[i]);
        }
        lp.setEntries(entries.toArray(new CharSequence[0]));
        lp.setEntryValues(values.toArray(new CharSequence[0]));
    }

    private void showSwapCustomDialog(final ListPreference lp) {
        Context ctx = getContext();
        if (ctx == null) return;
        final EditText input = new EditText(ctx);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint(R.string.gammaos_toolbox_swap_size_custom_hint);
        String cur = SystemProperties.get(SWAP_KEY, "0");
        try {
            if (Integer.parseInt(cur) > 0) input.setText(cur);
        } catch (NumberFormatException ignored) {
        }
        new AlertDialog.Builder(ctx)
                .setTitle(R.string.gammaos_toolbox_swap_size_custom_title)
                .setMessage(R.string.gammaos_toolbox_swap_size_custom_msg)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    int n;
                    try {
                        n = Integer.parseInt(input.getText().toString().trim());
                    } catch (NumberFormatException e) {
                        n = 0;
                    }
                    if (n < 0) n = 0;
                    if (n > SWAP_MAX_MB) n = SWAP_MAX_MB;
                    String val = String.valueOf(n);
                    SystemProperties.set(SWAP_KEY, val);
                    addCustomSwapEntryIfNeeded(lp, val);
                    lp.setValue(val);
                    updateListSummary(lp, val);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /* ------------------------------------------------------------------ */
    /*  Launch target: a two-level app -> activity picker                 */
    /* ------------------------------------------------------------------ */

    private static final String LAUNCH_TARGET_KEY = "persist.gammaos.rotate.launch_target";

    private void bindLaunchTarget() {
        Preference pref = findPreference(LAUNCH_TARGET_KEY);
        if (pref == null) return;
        updateLaunchTargetSummary(pref, SystemProperties.get(LAUNCH_TARGET_KEY, ""));
        pref.setOnPreferenceClickListener(p -> { showLaunchAppPicker(pref); return true; });
    }

    /** Human-readable summary for the stored value ("", "pkg", "pkg/Component", or nano:&lt;mode&gt;). */
    private void updateLaunchTargetSummary(Preference pref, String value) {
        if (TextUtils.isEmpty(value)) {
            pref.setSummary(getString(R.string.gammaos_rotate_launch_not_set));
            return;
        }
        if (value.startsWith("nano:")) { pref.setSummary(value); return; }
        Context ctx = getContext();
        PackageManager pm = (ctx != null) ? ctx.getPackageManager() : null;
        String pkg = value, cls = null;
        int slash = value.indexOf('/');
        if (slash >= 0) { pkg = value.substring(0, slash); cls = value.substring(slash + 1); }
        String appLabel = pkg;
        if (pm != null) {
            try { appLabel = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString(); }
            catch (Exception ignored) {}
        }
        if (cls == null) { pref.setSummary(appLabel); return; }
        String actLabel = cls.substring(cls.lastIndexOf('.') + 1);
        if (pm != null) {
            try {
                ComponentName cn = new ComponentName(pkg, cls.startsWith(".") ? pkg + cls : cls);
                CharSequence l = pm.getActivityInfo(cn, 0).loadLabel(pm);
                if (!TextUtils.isEmpty(l)) actLabel = l.toString();
            } catch (Exception ignored) {}
        }
        pref.setSummary(appLabel + " / " + actLabel);
    }

    /** Step 1: an icon+label list of every launchable app, plus a "Custom..." row. */
    private void showLaunchAppPicker(final Preference pref) {
        final Context ctx = getContext();
        if (ctx == null) return;
        final PackageManager pm = ctx.getPackageManager();
        Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        final List<ResolveInfo> apps = pm.queryIntentActivities(main, 0);
        apps.sort(new ResolveInfo.DisplayNameComparator(pm));

        final int n = apps.size();
        final CharSequence[] labels = new CharSequence[n + 1];
        final Drawable[] icons = new Drawable[n + 1];
        for (int i = 0; i < n; i++) {
            labels[i] = apps.get(i).loadLabel(pm);
            try { icons[i] = apps.get(i).loadIcon(pm); } catch (Exception e) { icons[i] = null; }
        }
        labels[n] = getString(R.string.gammaos_rotate_launch_custom_entry);

        new AlertDialog.Builder(ctx)
                .setTitle(R.string.gammaos_rotate_launch_pick_app)
                .setAdapter(new IconTextAdapter(ctx, labels, icons), (d, which) -> {
                    if (which == n) { showLaunchCustomDialog(pref); return; }
                    showLaunchActivityPicker(pref, apps.get(which).activityInfo.packageName);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Step 2: the chosen app's activities, with "Default activity" pinned first. */
    private void showLaunchActivityPicker(final Preference pref, final String pkg) {
        final Context ctx = getContext();
        if (ctx == null) return;
        final PackageManager pm = ctx.getPackageManager();

        // The default launcher component for this package is stored as the bare "pkg".
        String defCls = null;
        Intent li = pm.getLaunchIntentForPackage(pkg);
        if (li != null && li.getComponent() != null) defCls = li.getComponent().getClassName();

        final List<String> classes = new ArrayList<>();     // null = default (bare pkg)
        final List<CharSequence> labels = new ArrayList<>();
        final List<Drawable> icons = new ArrayList<>();
        classes.add(null);
        labels.add(getString(R.string.gammaos_rotate_launch_default_activity));
        try { icons.add(pm.getApplicationIcon(pkg)); } catch (Exception e) { icons.add(null); }

        try {
            PackageInfo pi = pm.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES);
            if (pi.activities != null) {
                for (ActivityInfo ai : pi.activities) {
                    if (ai.name.equals(defCls)) continue;   // already the default row
                    classes.add(ai.name);
                    CharSequence l = ai.loadLabel(pm);
                    labels.add(!TextUtils.isEmpty(l) ? l : ai.name.substring(ai.name.lastIndexOf('.') + 1));
                    try { icons.add(ai.loadIcon(pm)); } catch (Exception e) { icons.add(null); }
                }
            }
        } catch (Exception ignored) {}

        new AlertDialog.Builder(ctx)
                .setTitle(R.string.gammaos_rotate_launch_pick_activity)
                .setAdapter(new IconTextAdapter(ctx,
                        labels.toArray(new CharSequence[0]), icons.toArray(new Drawable[0])), (d, which) -> {
                    String cls = classes.get(which);
                    setLaunchTarget(pref, (cls == null) ? pkg : (pkg + "/" + cls));
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Fallback text entry for arbitrary values (nano:&lt;mode&gt; or a hand-typed component). */
    private void showLaunchCustomDialog(final Preference pref) {
        Context ctx = getContext();
        if (ctx == null) return;
        final EditText input = new EditText(ctx);
        input.setSingleLine(true);
        input.setHint(R.string.gammaos_rotate_launch_custom_hint);
        input.setText(SystemProperties.get(LAUNCH_TARGET_KEY, ""));
        new AlertDialog.Builder(ctx)
                .setTitle(R.string.gammaos_rotate_launch_custom_title)
                .setView(input)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String v = input.getText().toString().trim();
                    if (v.length() > 91) v = v.substring(0, 91);   // prop value limit
                    setLaunchTarget(pref, v);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void setLaunchTarget(Preference pref, String value) {
        SystemProperties.set(LAUNCH_TARGET_KEY, value);
        updateLaunchTargetSummary(pref, value);
    }

    /** Icon + single-line-label rows for the app / activity chooser dialogs. */
    private static final class IconTextAdapter extends BaseAdapter {
        private final Context mCtx;
        private final CharSequence[] mLabels;
        private final Drawable[] mIcons;
        IconTextAdapter(Context ctx, CharSequence[] labels, Drawable[] icons) {
            mCtx = ctx; mLabels = labels; mIcons = icons;
        }
        @Override public int getCount() { return mLabels.length; }
        @Override public Object getItem(int position) { return mLabels[position]; }
        @Override public long getItemId(int position) { return position; }
        @Override public View getView(int position, View convertView, ViewGroup parent) {
            TextView tv = (convertView instanceof TextView) ? (TextView) convertView
                    : (TextView) android.view.LayoutInflater.from(mCtx)
                            .inflate(android.R.layout.simple_list_item_1, parent, false);
            tv.setText(mLabels[position]);
            Drawable d = (position < mIcons.length) ? mIcons[position] : null;
            int sz = (int) (tv.getTextSize() * 1.6f);
            if (d != null) d.setBounds(0, 0, sz, sz);
            tv.setCompoundDrawables(d, null, null, null);
            tv.setCompoundDrawablePadding(sz / 2);
            return tv;
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Dynamic input-device list for the rotation trigger                */
    /* ------------------------------------------------------------------ */

    /**
     * Fill the {@code persist.gammaos.rotate.dev_name} ListPreference with the
     * names of every currently connected input device.  The first entry is
     * "Any device" (empty value), matching the native consumer's "blank =
     * match any device" behaviour.  The current persisted value is always
     * included even if that device is not connected right now, so the picker
     * can preselect it and render its summary.
     */
    private void populateRotateDeviceList() {
        ListPreference lp = (ListPreference) findPreference("persist.gammaos.rotate.dev_name");
        if (lp == null) return;

        String current = SystemProperties.get("persist.gammaos.rotate.dev_name", "");

        List<CharSequence> entries = new ArrayList<>();
        List<CharSequence> values = new ArrayList<>();

        // "Any device" always first, empty value.
        entries.add(getString(R.string.gammaos_toolbox_rotate_dev_name_any));
        values.add("");

        Context ctx = getContext();
        InputManager im = (ctx != null) ? ctx.getSystemService(InputManager.class) : null;
        if (im != null) {
            int[] ids = im.getInputDeviceIds();
            if (ids != null) {
                for (int id : ids) {
                    InputDevice dev = im.getInputDevice(id);
                    if (dev == null) continue;
                    String name = dev.getName();
                    if (TextUtils.isEmpty(name)) continue;
                    if (values.contains(name)) continue;   // de-dupe identical names
                    entries.add(name);
                    values.add(name);
                }
            }
        }

        // Ensure the persisted value is selectable even when its device is offline.
        if (!TextUtils.isEmpty(current) && !values.contains(current)) {
            entries.add(current);
            values.add(current);
        }

        lp.setEntries(entries.toArray(new CharSequence[0]));
        lp.setEntryValues(values.toArray(new CharSequence[0]));
    }

    /* ------------------------------------------------------------------ */
    /*  Recursive preference binder                                       */
    /* ------------------------------------------------------------------ */

    private void bindAllPreferences(PreferenceGroup group) {
        for (int i = 0; i < group.getPreferenceCount(); i++) {
            Preference pref = group.getPreference(i);
            if (pref instanceof PreferenceGroup) {
                bindAllPreferences((PreferenceGroup) pref);
                continue;
            }
            String key = pref.getKey();
            if (key == null || !key.startsWith("persist.gammaos.")) continue;
            if (LAUNCH_TARGET_KEY.equals(key)) continue;   // handled by bindLaunchTarget (app/activity picker)

            if (pref instanceof SwitchPreference) {
                bindSwitch((SwitchPreference) pref, key);
            } else if (pref instanceof MultiSelectListPreference) {
                bindMultiSelectList((MultiSelectListPreference) pref, key);
            } else if (pref instanceof ListPreference) {
                bindList((ListPreference) pref, key);
            } else if (pref instanceof EditTextPreference) {
                bindEditText((EditTextPreference) pref, key);
            }
        }
    }

    /* ------------------------------------------------------------------ */
    /*  SwitchPreference  →  boolean / int-as-boolean property            */
    /* ------------------------------------------------------------------ */

    private void bindSwitch(SwitchPreference sw, String key) {
        String def = DEFAULTS.getOrDefault(key, "false");
        // Some native consumers use "1"/"0" instead of "true"/"false".
        boolean defBool = "true".equals(def) || "1".equals(def);
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
            // GammaOS: phone-taskbar toggle needs a Secure setting poke to
            // notify Trebuchet (ContentObserver) in addition to the prop.
            if ("persist.gammaos.taskbar.phone".equals(key)) {
                android.provider.Settings.Secure.putInt(
                        getActivity().getContentResolver(),
                        "gamma_phone_taskbar_toggle", val ? 1 : 0);
            }
            return true;
        });
    }

    /* ------------------------------------------------------------------ */
    /*  ListPreference  →  string property with fixed values              */
    /* ------------------------------------------------------------------ */

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

    /* ------------------------------------------------------------------ */
    /*  MultiSelectListPreference  →  comma-separated string property     */
    /* ------------------------------------------------------------------ */

    /**
     * Bind a MultiSelectListPreference to a comma-separated system property (e.g. the slide
     * down/up actions, which the framework reads as a comma list). The prop is read as "a,b,c",
     * split into the checked value set; on change the selected set is re-joined with a plain comma
     * (no spaces) in entryValues order and written back. Order does not matter to the framework;
     * entryValues order just gives a stable, clean list.
     */
    private void bindMultiSelectList(MultiSelectListPreference mp, String key) {
        String def = DEFAULTS.getOrDefault(key, "");
        String current = SystemProperties.get(key, def);
        Set<String> selected = splitToSet(current);
        mp.setValues(selected);
        updateMultiSelectSummary(mp, selected);

        mp.setOnPreferenceChangeListener((p, newValue) -> {
            @SuppressWarnings("unchecked")
            Set<String> values = (Set<String>) newValue;
            String joined = joinInEntryOrder(mp, values);
            SystemProperties.set(key, joined);
            updateMultiSelectSummary(mp, values);
            return true;
        });
    }

    /** Split a comma-separated prop value into a set of non-empty trimmed tokens. */
    private Set<String> splitToSet(String value) {
        Set<String> set = new LinkedHashSet<>();
        if (value == null) return set;
        for (String part : value.split(",")) {
            String t = part.trim();
            if (!t.isEmpty()) set.add(t);
        }
        return set;
    }

    /** Join the selected values in entryValues order into a clean comma list with no spaces. */
    private String joinInEntryOrder(MultiSelectListPreference mp, Set<String> values) {
        StringBuilder sb = new StringBuilder();
        CharSequence[] order = mp.getEntryValues();
        if (order != null) {
            for (CharSequence ev : order) {
                if (values.contains(ev.toString())) {
                    if (sb.length() > 0) sb.append(',');
                    sb.append(ev);
                }
            }
        } else {
            for (String v : values) {
                if (sb.length() > 0) sb.append(',');
                sb.append(v);
            }
        }
        return sb.toString();
    }

    /** Summarise a multi-select as the joined human labels, in entryValues order. */
    private void updateMultiSelectSummary(MultiSelectListPreference mp, Set<String> values) {
        if (values == null || values.isEmpty()) {
            mp.setSummary(getString(R.string.gammaos_toolbox_value_not_set));
            return;
        }
        CharSequence[] entries = mp.getEntries();
        CharSequence[] entryValues = mp.getEntryValues();
        StringBuilder sb = new StringBuilder();
        if (entries != null && entryValues != null) {
            for (int i = 0; i < entryValues.length && i < entries.length; i++) {
                if (values.contains(entryValues[i].toString())) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(entries[i]);
                }
            }
        }
        mp.setSummary(sb.length() > 0
                ? sb.toString()
                : getString(R.string.gammaos_toolbox_value_not_set));
    }

    /* ------------------------------------------------------------------ */
    /*  EditTextPreference  →  string / int / float property              */
    /* ------------------------------------------------------------------ */

    private void bindEditText(EditTextPreference etp, String key) {
        String def = DEFAULTS.getOrDefault(key, "");

        // Package-list properties are stored across a base prop plus _1, _2, ... continuation
        // segments (because a single Android prop caps at ~92 bytes), exactly as the dedicated
        // DualStackControl / SecondaryDisplayControl apps write them. Reading only the base prop
        // showed a truncated, "not live" whitelist; read and write the full multi-segment value.
        if (isMultipart(key)) {
            String current = getMultiSegment(key);
            etp.setText(current);
            updateEditTextSummary(etp, current);
            etp.setOnPreferenceChangeListener((p, newValue) -> {
                String val = newValue == null ? "" : ((String) newValue).trim();
                setMultiSegment(key, val);
                etp.setText(val);
                updateEditTextSummary(etp, val);
                return false;
            });
            return;
        }

        String current = SystemProperties.get(key, def);
        etp.setText(current);
        updateEditTextSummary(etp, current);

        etp.setOnPreferenceChangeListener((p, newValue) -> {
            String val = sanitize(etp, (String) newValue, def);
            if (val == null) return false;        // rejected
            SystemProperties.set(key, val);
            etp.setText(val);
            updateEditTextSummary(etp, val);
            return false;                         // we already called setText
        });
    }

    /* ------------------------------------------------------------------ */
    /*  Multi-segment (base + _1.._N) package-list properties             */
    /* ------------------------------------------------------------------ */

    private static boolean isMultipart(String key) {
        return "persist.gammaos.dualstack.pkgs".equals(key)
                || "persist.gammaos.secondary_display.packages".equals(key)
                || "persist.gammaos.ultra_low_power_saving_freeze_exclude_packages".equals(key);
    }

    /** Join the base prop with its _1, _2, ... continuations into one comma-separated list. */
    private static String getMultiSegment(String baseKey) {
        StringBuilder out = new StringBuilder();
        appendSeg(out, SystemProperties.get(baseKey, ""));
        for (int idx = 1; ; idx++) {
            String seg = SystemProperties.get(baseKey + "_" + idx, "");
            if (TextUtils.isEmpty(seg) || seg.trim().isEmpty()) break;
            appendSeg(out, seg);
        }
        return out.toString();
    }

    private static void appendSeg(StringBuilder out, String seg) {
        if (seg == null) return;
        seg = seg.trim();
        if (seg.isEmpty()) return;
        if (out.length() > 0) out.append(',');
        out.append(seg);
    }

    /** Split a comma/space list back into <=90-char segments across base + _1.._N, clearing stale. */
    private static void setMultiSegment(String baseKey, String value) {
        List<String> tokens = new ArrayList<>();
        if (value != null) {
            for (String t : value.split("[,\\s]+")) {
                String s = t.trim();
                if (!s.isEmpty()) tokens.add(s);
            }
        }
        List<String> segments = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String tok : tokens) {
            int extra = (cur.length() == 0) ? tok.length() : (1 + tok.length());
            if (cur.length() > 0 && cur.length() + extra > 90) {
                segments.add(cur.toString());
                cur.setLength(0);
            }
            if (cur.length() > 0) cur.append(',');
            cur.append(tok);
        }
        if (cur.length() > 0) segments.add(cur.toString());

        SystemProperties.set(baseKey, segments.isEmpty() ? "" : segments.get(0));
        for (int idx = 1; idx < segments.size(); idx++) {
            SystemProperties.set(baseKey + "_" + idx, segments.get(idx));
        }
        for (int idx = Math.max(1, segments.size()); ; idx++) {
            String k = baseKey + "_" + idx;
            String old = SystemProperties.get(k, "");
            if (TextUtils.isEmpty(old) || old.trim().isEmpty()) break;
            SystemProperties.set(k, "");
        }
    }

    private void updateEditTextSummary(EditTextPreference etp, String value) {
        if (TextUtils.isEmpty(value)) {
            etp.setSummary(getString(R.string.gammaos_toolbox_value_not_set));
        } else {
            etp.setSummary(value);
        }
    }

    /**
     * Sanitise the user-provided value based on the EditTextPreference's
     * declared {@code inputType}.
     *
     *  - {@code number}         → non-negative integer
     *  - {@code numberSigned}   → signed integer
     *  - {@code numberDecimal}  → floating-point
     *  - anything else          → trimmed string (max 91 chars, the prop limit)
     *
     * Returns {@code null} when the value is invalid.
     */
    private String sanitize(EditTextPreference etp, String raw, String def) {
        if (raw == null) raw = "";
        raw = raw.trim();

        // Allow clearing back to default
        if (raw.isEmpty()) return def;

        int inputType = 0;
        if (etp.getExtras() != null) {
            inputType = etp.getExtras().getInt("inputType", 0);
        }
        // EditTextPreference stores inputType in its own field; use the XML hint
        // by checking the preference's key against what we know.
        // A simpler heuristic: try parsing.

        // Try integer
        if (isIntegerField(etp)) {
            try {
                Integer.parseInt(raw);
                return raw;
            } catch (NumberFormatException e) {
                return null;
            }
        }

        // Try decimal
        if (isDecimalField(etp)) {
            try {
                Float.parseFloat(raw);
                return raw;
            } catch (NumberFormatException e) {
                return null;
            }
        }

        // String: enforce property value limit (91 chars)
        if (raw.length() > 91) {
            raw = raw.substring(0, 91);
        }
        return raw;
    }

    private boolean isIntegerField(EditTextPreference etp) {
        // Check the XML-declared inputType via the preference's OnBindEditText
        // We look at our known defaults: if the default parses as int, treat as int.
        String key = etp.getKey();
        String def = DEFAULTS.getOrDefault(key, "");
        if (def.isEmpty()) return false;
        try {
            Integer.parseInt(def);
            // Verify it's not a float default
            return !def.contains(".");
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isDecimalField(EditTextPreference etp) {
        String key = etp.getKey();
        String def = DEFAULTS.getOrDefault(key, "");
        if (def.isEmpty()) return false;
        return def.contains(".");
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.gammaos_toolbox);
}
