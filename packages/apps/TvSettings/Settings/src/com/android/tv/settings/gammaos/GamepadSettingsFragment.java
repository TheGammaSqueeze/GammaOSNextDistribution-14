/*
 * GammaOS Gamepad Settings - TVSettings port
 * SPDX-License-Identifier: Apache-2.0
 */
package com.android.tv.settings.gammaos;

import android.app.AlertDialog;
import android.app.tvsettings.TvSettingsEnums;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.hardware.input.InputManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemProperties;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Keep;
import androidx.preference.CheckBoxPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.SwitchPreference;

import com.android.tv.settings.R;
import com.android.tv.settings.SettingsPreferenceFragment;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Keep
public class GamepadSettingsFragment extends SettingsPreferenceFragment
        implements Preference.OnPreferenceChangeListener,
                   Preference.OnPreferenceClickListener,
                   InputManager.InputDeviceListener {

    private static final String TAG = "GamepadSettings";

    // Preference keys
    private static final String KEY_ENABLE = "gamepad_enable";
    private static final String KEY_MERGE = "gamepad_merge";
    private static final String KEY_HIDE_SOURCE = "gamepad_hide_source";
    private static final String KEY_DEVICES_CATEGORY = "gamepad_devices_category";
    private static final String KEY_DEVICE_PRESET = "gamepad_device_preset";
    private static final String KEY_REMAP_BUTTONS = "gamepad_remap_buttons";
    private static final String KEY_REMAP_AXES = "gamepad_remap_axes";
    private static final String KEY_AXIS_ROLES = "gamepad_axis_roles";
    private static final String KEY_AXIS_TO_BUTTON = "gamepad_axis_to_button";
    private static final String KEY_CALIBRATION = "gamepad_calibration";
    private static final String KEY_ANALOG_TO_DPAD = "gamepad_analog_to_dpad";
    private static final String KEY_DPAD_TO_ANALOG = "gamepad_dpad_to_analog";
    private static final String KEY_DPAD_THRESHOLD = "gamepad_dpad_threshold";
    private static final String KEY_PWM_ENABLE = "gamepad_pwm_enable";
    private static final String KEY_PWM_INTENSITY = "gamepad_pwm_intensity";
    private static final String KEY_FF_DEVICE = "gamepad_ff_device";
    private static final String KEY_TEST_VIBRATION = "gamepad_test_vibration";
    private static final String KEY_CLEAR_CALIBRATION = "gamepad_clear_calibration";
    private static final String KEY_BLACKLIST_VPAD = "gamepad_blacklist_vpad";
    private static final String KEY_BLACKLIST_PASS = "gamepad_blacklist_pass";
    private static final String KEY_ABXY_SWAP = "gamepad_abxy_swap";
    private static final String KEY_INVERT_LEFT = "gamepad_invert_left";
    private static final String KEY_INVERT_RIGHT = "gamepad_invert_right";
    private static final String KEY_GLOBAL_SENSITIVITY = "gamepad_global_sensitivity";
    private static final String KEY_TEST = "gamepad_test";
    private static final String KEY_COMBO_MAP = "gamepad_combo_map";
    private static final String KEY_CUSTOM_ACTIONS = "gamepad_custom_actions";
    private static final String PROP_ACT_COUNT = "persist.gammaos.gamepad.act_count";
    private static final String KEY_PERAPP_CATEGORY = "gamepad_perapp_category";
    private static final String KEY_PERAPP_ADD = "gamepad_perapp_add";
    private static final String KEY_MOUSE_ENABLE = "gamepad_mouse_enable";
    private static final String KEY_MOUSE_COMBO = "gamepad_mouse_combo";
    private static final String KEY_MOUSE_HOLD_TIME = "gamepad_mouse_hold_time";
    private static final String KEY_MOUSE_BUTTONS = "gamepad_mouse_buttons";
    private static final String KEY_MOUSE_STICK_SPEED = "gamepad_mouse_stick_speed";
    private static final String KEY_MOUSE_DPAD_SPEED = "gamepad_mouse_dpad_speed";
    private static final String KEY_MOUSE_BOOST = "gamepad_mouse_boost";
    private static final String KEY_MOUSE_SCROLL_SPEED = "gamepad_mouse_scroll_speed";

    // System properties
    private static final String PROP_ENABLE = "persist.gammaos.gamepad.enable";
    private static final String PROP_MERGE = "persist.gammaos.gamepad.merge";
    private static final String PROP_HIDE_SOURCE = "persist.gammaos.gamepad.hide_source";
    private static final String PROP_DEVICES = "persist.gammaos.gamepad.devices";
    private static final String PROP_REMAP_BTN = "persist.gammaos.gamepad.remap_btn";
    private static final String PROP_REMAP_AXIS = "persist.gammaos.gamepad.remap_axis";
    private static final String PROP_AXIS_BTN = "persist.gammaos.gamepad.axis_btn";
    private static final String PROP_ANALOG_TO_DPAD = "persist.gammaos.gamepad.analog_to_dpad";
    private static final String PROP_DPAD_TO_ANALOG = "persist.gammaos.gamepad.dpad_to_analog";
    private static final String PROP_DPAD_THRESHOLD = "persist.gammaos.gamepad.dpad_threshold";
    private static final String PROP_PWM_ENABLE = "persist.gammaos.gamepad.pwm_enable";
    private static final String PROP_PWM_INTENSITY = "persist.gammaos.gamepad.pwm_intensity";
    private static final String PROP_FF_DEVICE = "persist.gammaos.gamepad.ff_vibrate_device";
    private static final String PROP_BLACKLIST_VPAD = "persist.gammaos.gamepad.blacklist_vpad";
    private static final String PROP_BLACKLIST_PASS = "persist.gammaos.gamepad.blacklist_pass";
    private static final String PROP_ABXY_SWAP = "persist.gammaos.gamepad.abxy_swap";
    private static final String PROP_INVERT_LEFT = "persist.gammaos.gamepad.invert_left";
    private static final String PROP_INVERT_RIGHT = "persist.gammaos.gamepad.invert_right";
    private static final String PROP_GLOBAL_SENSITIVITY = "persist.gammaos.gamepad.global_sensitivity";
    private static final String PROP_CONFIG_VERSION = "persist.gammaos.gamepad.config_version";
    private static final String PROP_DEVICE_NAME = "persist.gammaos.gamepad.device_name";
    private static final String PROP_DEVICE_VID = "persist.gammaos.gamepad.device_vid";
    private static final String PROP_DEVICE_PID = "persist.gammaos.gamepad.device_pid";
    private static final String PROP_COMBO_MAP = "persist.gammaos.gamepad.combo_map";
    private static final String PROP_PA_COUNT = "persist.gammaos.gamepad.pa_count";
    private static final String PROP_MOUSE_COMBO1 = "persist.gammaos.gamepad.mouse_combo1";
    private static final String PROP_MOUSE_COMBO2 = "persist.gammaos.gamepad.mouse_combo2";
    private static final String PROP_MOUSE_HOLD_MS = "persist.gammaos.gamepad.mouse_hold_ms";
    private static final String PROP_MOUSE_STICK_SPEED = "persist.gammaos.gamepad.mouse_stick_speed";
    private static final String PROP_MOUSE_DPAD_SPEED = "persist.gammaos.gamepad.mouse_dpad_speed";
    private static final String PROP_MOUSE_BOOST = "persist.gammaos.gamepad.mouse_boost";
    private static final String PROP_MOUSE_SCROLL_SPEED = "persist.gammaos.gamepad.mouse_scroll_speed";
    private static final String PROP_MOUSE_BTN_CLICK = "persist.gammaos.gamepad.mouse_btn_click";
    private static final String PROP_MOUSE_BTN_BACK = "persist.gammaos.gamepad.mouse_btn_back";
    private static final String PROP_MOUSE_BTN_RCLICK = "persist.gammaos.gamepad.mouse_btn_rclick";
    private static final String PROP_MOUSE_BTN_BOOST = "persist.gammaos.gamepad.mouse_btn_boost";

    // Button code to name mapping
    private static final Map<Integer, String> BTN_NAMES = new HashMap<>();
    static {
        BTN_NAMES.put(0x130, "A"); BTN_NAMES.put(0x131, "B");
        BTN_NAMES.put(0x133, "X"); BTN_NAMES.put(0x134, "Y");
        BTN_NAMES.put(0x136, "LB"); BTN_NAMES.put(0x137, "RB");
        BTN_NAMES.put(0x138, "L2"); BTN_NAMES.put(0x139, "R2");
        BTN_NAMES.put(0x13a, "Select"); BTN_NAMES.put(0x13b, "Start");
        BTN_NAMES.put(0x13c, "Guide"); BTN_NAMES.put(0x13d, "L3");
        BTN_NAMES.put(0x13e, "R3");
    }

    // Common keyboard / media / system key targets (evdev) for the custom-action
    // key picker. The daemon routes non-gamepad KEY_* through its virtual keyboard.
    private static final java.util.LinkedHashMap<Integer, String> ACTION_KEYS =
            new java.util.LinkedHashMap<>();
    static {
        ACTION_KEYS.put(158, "Back"); ACTION_KEYS.put(172, "Home");
        ACTION_KEYS.put(139, "Menu"); ACTION_KEYS.put(217, "Search");
        ACTION_KEYS.put(115, "Volume Up"); ACTION_KEYS.put(114, "Volume Down");
        ACTION_KEYS.put(113, "Mute"); ACTION_KEYS.put(164, "Play / Pause");
        ACTION_KEYS.put(163, "Next Track"); ACTION_KEYS.put(165, "Previous Track");
        ACTION_KEYS.put(116, "Power"); ACTION_KEYS.put(142, "Sleep");
        ACTION_KEYS.put(212, "Camera"); ACTION_KEYS.put(225, "Brightness Up");
        ACTION_KEYS.put(224, "Brightness Down");
        ACTION_KEYS.put(28, "Enter"); ACTION_KEYS.put(1, "Escape");
        ACTION_KEYS.put(15, "Tab"); ACTION_KEYS.put(57, "Space"); ACTION_KEYS.put(14, "Backspace");
        ACTION_KEYS.put(103, "Up"); ACTION_KEYS.put(108, "Down");
        ACTION_KEYS.put(105, "Left"); ACTION_KEYS.put(106, "Right");
    }

    // Axis code to name mapping (Linux ABS codes)
    private static final Map<Integer, String> AXIS_NAMES = new HashMap<>();
    static {
        AXIS_NAMES.put(0x00, "LX"); AXIS_NAMES.put(0x01, "LY");
        AXIS_NAMES.put(0x02, "LT"); AXIS_NAMES.put(0x03, "RX");
        AXIS_NAMES.put(0x04, "RY"); AXIS_NAMES.put(0x05, "RT");
        AXIS_NAMES.put(0x09, "GAS"); AXIS_NAMES.put(0x0a, "BRAKE");
        AXIS_NAMES.put(0x10, "DpadX"); AXIS_NAMES.put(0x11, "DpadY");
    }

    // Linux ABS code -> Android MotionEvent AXIS constant
    private static final Map<Integer, Integer> ABS_TO_ANDROID_AXIS = new HashMap<>();
    static {
        ABS_TO_ANDROID_AXIS.put(0x00, MotionEvent.AXIS_X);
        ABS_TO_ANDROID_AXIS.put(0x01, MotionEvent.AXIS_Y);
        ABS_TO_ANDROID_AXIS.put(0x02, MotionEvent.AXIS_Z);
        ABS_TO_ANDROID_AXIS.put(0x03, MotionEvent.AXIS_RX);
        ABS_TO_ANDROID_AXIS.put(0x04, MotionEvent.AXIS_RY);
        ABS_TO_ANDROID_AXIS.put(0x05, MotionEvent.AXIS_RZ);
        ABS_TO_ANDROID_AXIS.put(0x09, MotionEvent.AXIS_GAS);
        ABS_TO_ANDROID_AXIS.put(0x0a, MotionEvent.AXIS_BRAKE);
        ABS_TO_ANDROID_AXIS.put(0x10, MotionEvent.AXIS_HAT_X);
        ABS_TO_ANDROID_AXIS.put(0x11, MotionEvent.AXIS_HAT_Y);
    }

    // Device identity presets: value -> {name, vid, pid}
    private static final Map<String, String[]> DEVICE_PRESETS = new HashMap<>();
    static {
        DEVICE_PRESETS.put("xbox_wireless", new String[]{"Xbox Wireless Controller", "045e", "0b13"});
        DEVICE_PRESETS.put("xbox_360", new String[]{"Xbox 360 Controller", "045e", "028e"});
        DEVICE_PRESETS.put("xbox_one", new String[]{"Xbox One Controller", "045e", "02ea"});
        DEVICE_PRESETS.put("ps4", new String[]{"Sony DualShock 4", "054c", "05c4"});
        DEVICE_PRESETS.put("ps5", new String[]{"DualSense Wireless Controller", "054c", "0ce6"});
        DEVICE_PRESETS.put("switch_pro", new String[]{"Nintendo Switch Pro Controller", "057e", "2009"});
    }

    private static final long REMAP_POLL_INTERVAL_MS = 2000;

    private SwitchPreference mEnablePref;
    private SwitchPreference mMergePref;
    private SwitchPreference mHideSourcePref;
    private PreferenceCategory mDevicesCategory;
    private ListPreference mDevicePresetPref;
    private SwitchPreference mAnalogToDpadPref;
    private SwitchPreference mDpadToAnalogPref;
    private SwitchPreference mPwmEnablePref;
    private Preference mFFDevicePref;
    private SwitchPreference mAbxySwapPref;
    private SwitchPreference mInvertLeftPref;
    private SwitchPreference mInvertRightPref;
    private ListPreference mGlobalSensitivityPref;
    private SwitchPreference mMouseEnablePref;
    private ListPreference mMouseBoostPref;

    private final List<String> mSelectedDevices = new ArrayList<>();

    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Runnable mRemapPollRunnable = new Runnable() {
        @Override
        public void run() {
            refreshRemapSummaries();
            mHandler.postDelayed(this, REMAP_POLL_INTERVAL_MS);
        }
    };

    private InputManager mInputManager;

    public static GamepadSettingsFragment newInstance() {
        return new GamepadSettingsFragment();
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.gamepad_settings, null);

        mEnablePref = findPreference(KEY_ENABLE);
        mMergePref = findPreference(KEY_MERGE);
        mHideSourcePref = findPreference(KEY_HIDE_SOURCE);
        mDevicesCategory = findPreference(KEY_DEVICES_CATEGORY);
        mDevicePresetPref = findPreference(KEY_DEVICE_PRESET);
        mAnalogToDpadPref = findPreference(KEY_ANALOG_TO_DPAD);
        mDpadToAnalogPref = findPreference(KEY_DPAD_TO_ANALOG);
        mPwmEnablePref = findPreference(KEY_PWM_ENABLE);
        mFFDevicePref = findPreference(KEY_FF_DEVICE);
        mAbxySwapPref = findPreference(KEY_ABXY_SWAP);
        mInvertLeftPref = findPreference(KEY_INVERT_LEFT);
        mInvertRightPref = findPreference(KEY_INVERT_RIGHT);
        mGlobalSensitivityPref = findPreference(KEY_GLOBAL_SENSITIVITY);
        mMouseEnablePref = findPreference(KEY_MOUSE_ENABLE);
        mMouseBoostPref = findPreference(KEY_MOUSE_BOOST);

        // Load current values
        mEnablePref.setChecked(SystemProperties.getInt(PROP_ENABLE, 0) != 0);
        mMergePref.setChecked(SystemProperties.getInt(PROP_MERGE, 1) != 0);
        mHideSourcePref.setChecked(SystemProperties.getInt(PROP_HIDE_SOURCE, 1) != 0);
        mAnalogToDpadPref.setChecked(SystemProperties.getInt(PROP_ANALOG_TO_DPAD, 0) != 0);
        mDpadToAnalogPref.setChecked(SystemProperties.getInt(PROP_DPAD_TO_ANALOG, 0) != 0);
        mPwmEnablePref.setChecked(SystemProperties.getInt(PROP_PWM_ENABLE, 1) != 0);
        mAbxySwapPref.setChecked(SystemProperties.getInt(PROP_ABXY_SWAP, 0) != 0);
        mInvertLeftPref.setChecked(SystemProperties.getInt(PROP_INVERT_LEFT, 0) != 0);
        mInvertRightPref.setChecked(SystemProperties.getInt(PROP_INVERT_RIGHT, 0) != 0);
        updateFFDeviceSummary();

        if (mGlobalSensitivityPref != null) {
            mGlobalSensitivityPref.setValue(
                    String.valueOf(SystemProperties.getInt(PROP_GLOBAL_SENSITIVITY, 0)));
            mGlobalSensitivityPref.setOnPreferenceChangeListener(this);
            updateGlobalSensitivitySummary();
        }

        if (mMouseEnablePref != null) {
            mMouseEnablePref.setChecked(
                    !SystemProperties.get(PROP_MOUSE_COMBO1, "").isEmpty());
            mMouseEnablePref.setOnPreferenceChangeListener(this);
        }

        if (mMouseBoostPref != null) {
            mMouseBoostPref.setValue(
                    String.valueOf(SystemProperties.getInt(PROP_MOUSE_BOOST, 20)));
            mMouseBoostPref.setOnPreferenceChangeListener(this);
        }

        // Initialize device preset
        initDevicePreset();

        // Set change listeners
        mEnablePref.setOnPreferenceChangeListener(this);
        mMergePref.setOnPreferenceChangeListener(this);
        mHideSourcePref.setOnPreferenceChangeListener(this);
        mAnalogToDpadPref.setOnPreferenceChangeListener(this);
        mDpadToAnalogPref.setOnPreferenceChangeListener(this);
        mPwmEnablePref.setOnPreferenceChangeListener(this);
        mAbxySwapPref.setOnPreferenceChangeListener(this);
        mInvertLeftPref.setOnPreferenceChangeListener(this);
        mInvertRightPref.setOnPreferenceChangeListener(this);

        if (mDevicePresetPref != null) {
            mDevicePresetPref.setOnPreferenceChangeListener(this);
        }

        // Set click listeners for Preference items (dialogs)
        setClickListener(KEY_REMAP_BUTTONS);
        setClickListener(KEY_REMAP_AXES);
        setClickListener(KEY_AXIS_ROLES);
        setClickListener(KEY_AXIS_TO_BUTTON);
        setClickListener(KEY_BLACKLIST_VPAD);
        setClickListener(KEY_BLACKLIST_PASS);
        setClickListener(KEY_COMBO_MAP);
        // Custom Button Actions: add a preference into the remap category
        // programmatically (avoids a new string resource) and wire its click.
        PreferenceCategory remapCat = findPreference("gamepad_remap_category");
        if (remapCat != null && findPreference(KEY_CUSTOM_ACTIONS) == null) {
            Preference caPref = new Preference(remapCat.getContext());
            caPref.setKey(KEY_CUSTOM_ACTIONS);
            caPref.setTitle("Custom Button Actions");
            caPref.setSummary("Bind short / long press to a key, app, activity, prop or command");
            caPref.setOnPreferenceClickListener(this);
            remapCat.addPreference(caPref);
        }
        setClickListener(KEY_CALIBRATION);
        setClickListener(KEY_CLEAR_CALIBRATION);
        setClickListener(KEY_FF_DEVICE);
        setClickListener(KEY_TEST_VIBRATION);
        setClickListener(KEY_TEST);
        setClickListener(KEY_PERAPP_ADD);
        setClickListener(KEY_MOUSE_COMBO);
        setClickListener(KEY_MOUSE_BUTTONS);
        setClickListener(KEY_MOUSE_HOLD_TIME);
        setClickListener(KEY_MOUSE_STICK_SPEED);
        setClickListener(KEY_MOUSE_DPAD_SPEED);
        setClickListener(KEY_MOUSE_SCROLL_SPEED);
        setClickListener(KEY_DPAD_THRESHOLD);
        setClickListener(KEY_PWM_INTENSITY);

        // Initialize slider summaries
        updateSliderSummary(KEY_DPAD_THRESHOLD, PROP_DPAD_THRESHOLD, 50);
        updateSliderSummary(KEY_PWM_INTENSITY, PROP_PWM_INTENSITY, 200);
        updateSliderSummary(KEY_MOUSE_HOLD_TIME, PROP_MOUSE_HOLD_MS, 2000);
        updateSliderSummary(KEY_MOUSE_STICK_SPEED, PROP_MOUSE_STICK_SPEED, 12);
        updateSliderSummary(KEY_MOUSE_DPAD_SPEED, PROP_MOUSE_DPAD_SPEED, 6);
        updateSliderSummary(KEY_MOUSE_SCROLL_SPEED, PROP_MOUSE_SCROLL_SPEED, 4);

        // Initialize remap summaries
        updateRemapSummary(findPreference(KEY_REMAP_BUTTONS), PROP_REMAP_BTN, BTN_NAMES);
        updateRemapSummary(findPreference(KEY_REMAP_AXES), PROP_REMAP_AXIS, AXIS_NAMES);
        updateAxisRolesSummary(findPreference(KEY_AXIS_ROLES));
        updateAxisButtonSummary(findPreference(KEY_AXIS_TO_BUTTON));
        updateBlacklistSummary(findPreference(KEY_BLACKLIST_VPAD), PROP_BLACKLIST_VPAD);
        updateBlacklistSummary(findPreference(KEY_BLACKLIST_PASS), PROP_BLACKLIST_PASS);
        updateComboMapSummary(findPreference(KEY_COMBO_MAP));
        updateMouseComboSummary(findPreference(KEY_MOUSE_COMBO));
        updateMouseButtonsSummary(findPreference(KEY_MOUSE_BUTTONS));

        // Load selected devices
        String devicesStr = SystemProperties.get(PROP_DEVICES, "");
        if (!devicesStr.isEmpty()) {
            for (String name : devicesStr.split(";")) {
                if (!name.isEmpty()) {
                    mSelectedDevices.add(name);
                }
            }
        }

        populateControllerList();
        populatePerAppProfiles();

        mInputManager = getContext().getSystemService(InputManager.class);

        updateDependentVisibility();
    }

    private void updateDependentVisibility() {
        boolean enabled = mEnablePref != null && mEnablePref.isChecked();
        boolean pwmOn = mPwmEnablePref != null && mPwmEnablePref.isChecked();
        boolean mouseOn = mMouseEnablePref != null && mMouseEnablePref.isChecked();

        String[] enableDeps = {
            KEY_MERGE, KEY_HIDE_SOURCE, KEY_DEVICES_CATEGORY,
            "gamepad_identity_category", "gamepad_remap_category",
            "gamepad_calibration_category", "gamepad_conversion_category",
            "gamepad_rumble_category", "gamepad_mouse_category",
            "gamepad_perapp_category", "gamepad_testing_category"
        };
        for (String key : enableDeps) {
            Preference p = findPreference(key);
            if (p != null) p.setVisible(enabled);
        }

        String[] pwmDeps = {KEY_PWM_INTENSITY, KEY_FF_DEVICE};
        for (String key : pwmDeps) {
            Preference p = findPreference(key);
            if (p != null) p.setVisible(enabled && pwmOn);
        }

        String[] mouseDeps = {
            KEY_MOUSE_COMBO, KEY_MOUSE_HOLD_TIME, KEY_MOUSE_BUTTONS,
            KEY_MOUSE_STICK_SPEED, KEY_MOUSE_DPAD_SPEED,
            KEY_MOUSE_BOOST, KEY_MOUSE_SCROLL_SPEED
        };
        for (String key : mouseDeps) {
            Preference p = findPreference(key);
            if (p != null) p.setVisible(enabled && mouseOn);
        }
    }

    @Override
    protected int getPageId() {
        return TvSettingsEnums.PAGE_CLASSIC_DEFAULT;
    }

    private void setClickListener(String key) {
        Preference pref = findPreference(key);
        if (pref != null) {
            pref.setOnPreferenceClickListener(this);
        }
    }

    private void updateSliderSummary(String key, String prop, int defaultVal) {
        Preference pref = findPreference(key);
        if (pref != null) {
            int val = SystemProperties.getInt(prop, defaultVal);
            pref.setSummary(String.valueOf(val));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        populateControllerList();
        refreshRemapSummaries();
        refreshToggleStates();
        populatePerAppProfiles();
        mHandler.postDelayed(mRemapPollRunnable, REMAP_POLL_INTERVAL_MS);
        if (mInputManager != null) {
            mInputManager.registerInputDeviceListener(this, mHandler);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        mHandler.removeCallbacks(mRemapPollRunnable);
        if (mInputManager != null) {
            mInputManager.unregisterInputDeviceListener(this);
        }
    }

    private void refreshToggleStates() {
        if (mEnablePref != null)
            mEnablePref.setChecked(SystemProperties.getInt(PROP_ENABLE, 0) != 0);
        if (mAnalogToDpadPref != null)
            mAnalogToDpadPref.setChecked(SystemProperties.getInt(PROP_ANALOG_TO_DPAD, 0) != 0);
        if (mDpadToAnalogPref != null)
            mDpadToAnalogPref.setChecked(SystemProperties.getInt(PROP_DPAD_TO_ANALOG, 0) != 0);
        if (mAbxySwapPref != null)
            mAbxySwapPref.setChecked(SystemProperties.getInt(PROP_ABXY_SWAP, 0) != 0);
        if (mInvertLeftPref != null)
            mInvertLeftPref.setChecked(SystemProperties.getInt(PROP_INVERT_LEFT, 0) != 0);
        if (mInvertRightPref != null)
            mInvertRightPref.setChecked(SystemProperties.getInt(PROP_INVERT_RIGHT, 0) != 0);
        if (mGlobalSensitivityPref != null) {
            mGlobalSensitivityPref.setValue(
                    String.valueOf(SystemProperties.getInt(PROP_GLOBAL_SENSITIVITY, 0)));
            updateGlobalSensitivitySummary();
        }
        if (mPwmEnablePref != null)
            mPwmEnablePref.setChecked(SystemProperties.getInt(PROP_PWM_ENABLE, 1) != 0);
        updateFFDeviceSummary();
    }

    // --- InputDeviceListener ---

    @Override
    public void onInputDeviceAdded(int deviceId) {
        populateControllerList();
    }

    @Override
    public void onInputDeviceRemoved(int deviceId) {
        populateControllerList();
    }

    @Override
    public void onInputDeviceChanged(int deviceId) {
        populateControllerList();
    }

    // --- Device preset ---

    private void initDevicePreset() {
        if (mDevicePresetPref == null) return;

        String currentVid = SystemProperties.get(PROP_DEVICE_VID, "");
        String currentPid = SystemProperties.get(PROP_DEVICE_PID, "");

        String matchedPreset = "xbox_wireless";
        if (!currentVid.isEmpty() && !currentPid.isEmpty()) {
            String vid = currentVid.replace("0x", "").toLowerCase();
            String pid = currentPid.replace("0x", "").toLowerCase();

            boolean found = false;
            for (Map.Entry<String, String[]> entry : DEVICE_PRESETS.entrySet()) {
                if (entry.getValue()[1].equals(vid) && entry.getValue()[2].equals(pid)) {
                    matchedPreset = entry.getKey();
                    found = true;
                    break;
                }
            }
            if (!found) {
                matchedPreset = "custom";
            }
        }

        mDevicePresetPref.setValue(matchedPreset);
        updateDevicePresetSummary(matchedPreset);
    }

    private void updateDevicePresetSummary(String value) {
        if (mDevicePresetPref == null) return;
        String[] preset = DEVICE_PRESETS.get(value);
        if (preset != null) {
            mDevicePresetPref.setSummary(
                    mDevicePresetPref.getEntry() + " (VID: 0x" + preset[1]
                    + ", PID: 0x" + preset[2] + ")");
        } else if ("custom".equals(value)) {
            String vid = SystemProperties.get(PROP_DEVICE_VID, "0x045e")
                    .replace("0x", "");
            String pid = SystemProperties.get(PROP_DEVICE_PID, "0x0b13")
                    .replace("0x", "");
            mDevicePresetPref.setSummary(
                    getString(R.string.gamepad_device_preset_custom)
                    + " (VID: 0x" + vid + ", PID: 0x" + pid + ")");
        }
    }

    private void applyDevicePreset(String value) {
        String[] preset = DEVICE_PRESETS.get(value);
        if (preset != null) {
            mDevicePresetPref.setValue(value);
            SystemProperties.set(PROP_DEVICE_NAME, preset[0]);
            SystemProperties.set(PROP_DEVICE_VID, "0x" + preset[1]);
            SystemProperties.set(PROP_DEVICE_PID, "0x" + preset[2]);
            bumpConfigVersion();
            updateDevicePresetSummary(value);
        } else if ("custom".equals(value)) {
            mDevicePresetPref.setValue(value);
            showCustomDeviceIdentityDialog();
        }
    }

    private void showCustomDeviceIdentityDialog() {
        Context context = getContext();
        if (context == null) return;

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        layout.setPadding(pad, pad, pad, 0);

        EditText nameInput = new EditText(context);
        nameInput.setHint(getString(R.string.gamepad_device_custom_name_title));
        nameInput.setText(SystemProperties.get(PROP_DEVICE_NAME, "GammaOS Virtual Gamepad"));
        layout.addView(nameInput);

        EditText vidInput = new EditText(context);
        vidInput.setHint(getString(R.string.gamepad_device_custom_vid_title));
        vidInput.setInputType(InputType.TYPE_CLASS_TEXT);
        vidInput.setText(SystemProperties.get(PROP_DEVICE_VID, "0x045e").replace("0x", ""));
        layout.addView(vidInput);

        EditText pidInput = new EditText(context);
        pidInput.setHint(getString(R.string.gamepad_device_custom_pid_title));
        pidInput.setInputType(InputType.TYPE_CLASS_TEXT);
        pidInput.setText(SystemProperties.get(PROP_DEVICE_PID, "0x0b13").replace("0x", ""));
        layout.addView(pidInput);

        new AlertDialog.Builder(context)
                .setTitle(getString(R.string.gamepad_device_preset_custom))
                .setView(layout)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String name = nameInput.getText().toString().trim();
                    String vid = vidInput.getText().toString().trim();
                    String pid = pidInput.getText().toString().trim();
                    if (!name.isEmpty()) SystemProperties.set(PROP_DEVICE_NAME, name);
                    if (!vid.isEmpty()) {
                        vid = vid.replace("0x", "");
                        SystemProperties.set(PROP_DEVICE_VID, "0x" + vid);
                    }
                    if (!pid.isEmpty()) {
                        pid = pid.replace("0x", "");
                        SystemProperties.set(PROP_DEVICE_PID, "0x" + pid);
                    }
                    bumpConfigVersion();
                    updateDevicePresetSummary("custom");
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // --- Controller list (sysfs scanning) ---

    private String readKernelDeviceName(String eventNode) {
        try {
            File nameFile = new File("/sys/class/input/" + eventNode + "/device/name");
            if (nameFile.exists()) {
                BufferedReader reader = new BufferedReader(new FileReader(nameFile));
                String name = reader.readLine();
                reader.close();
                return name != null ? name.trim() : null;
            }
        } catch (IOException e) {
            // Fall through
        }
        return null;
    }

    private String readKernelDevicePhys(String eventNode) {
        try {
            File physFile = new File("/sys/class/input/" + eventNode + "/device/phys");
            if (physFile.exists()) {
                BufferedReader reader = new BufferedReader(new FileReader(physFile));
                String phys = reader.readLine();
                reader.close();
                return phys != null ? phys.trim() : null;
            }
        } catch (IOException e) {
            // Fall through
        }
        return null;
    }

    private String getGammaPadVirtualName(Map<String, String> kernelNameMap) {
        for (Map.Entry<String, String> entry : kernelNameMap.entrySet()) {
            String phys = readKernelDevicePhys(entry.getValue());
            if (phys != null && phys.contains("gammapad-virtual")) {
                return entry.getKey();
            }
        }
        return null;
    }

    private Map<String, String> buildKernelNameMap() {
        Map<String, String> kernelNames = new HashMap<>();
        File inputDir = new File("/sys/class/input");
        File[] eventDirs = inputDir.listFiles((dir, name) -> name.startsWith("event"));
        if (eventDirs != null) {
            for (File eventDir : eventDirs) {
                String kernelName = readKernelDeviceName(eventDir.getName());
                if (kernelName != null) {
                    kernelNames.put(kernelName, eventDir.getName());
                }
            }
        }
        return kernelNames;
    }

    private void populateControllerList() {
        if (mDevicesCategory == null) return;
        mDevicesCategory.removeAll();

        InputManager im = getContext().getSystemService(InputManager.class);
        if (im == null) return;

        Map<String, String> kernelNameMap = buildKernelNameMap();
        String gammaPadName = getGammaPadVirtualName(kernelNameMap);

        int[] ids = im.getInputDeviceIds();
        boolean found = false;
        Set<String> connectedKernelNames = new HashSet<>();

        for (int id : ids) {
            InputDevice device = im.getInputDevice(id);
            if (device == null) continue;

            int sources = device.getSources();
            if ((sources & InputDevice.SOURCE_TOUCHSCREEN) != 0) continue;
            if ((sources & InputDevice.SOURCE_MOUSE) != 0
                    && (sources & InputDevice.SOURCE_GAMEPAD) == 0) continue;
            boolean hasKeys = (sources & InputDevice.SOURCE_GAMEPAD) != 0
                    || (sources & InputDevice.SOURCE_JOYSTICK) != 0
                    || (sources & InputDevice.SOURCE_KEYBOARD) != 0
                    || (sources & InputDevice.SOURCE_DPAD) != 0;
            if (!hasKeys) continue;
            if (device.isVirtual()) continue;

            String deviceName = device.getName();
            if (gammaPadName != null && gammaPadName.equals(deviceName)) continue;
            if (deviceName.contains("GammaOS Virtual Gamepad")) continue;

            found = true;

            String kernelName = findKernelName(device, kernelNameMap);
            final String storedName = kernelName != null ? kernelName : deviceName;
            connectedKernelNames.add(storedName);

            CheckBoxPreference pref = new CheckBoxPreference(getContext());
            pref.setKey("gamepad_device_" + id);
            pref.setTitle(deviceName);
            if (kernelName != null && !kernelName.equals(deviceName)) {
                pref.setSummary("Kernel: " + kernelName);
            } else {
                pref.setSummary("ID: " + id);
            }
            pref.setChecked(mSelectedDevices.contains(storedName)
                    || mSelectedDevices.contains(deviceName));
            pref.setOnPreferenceChangeListener((p, newVal) -> {
                boolean checked = (Boolean) newVal;
                if (checked && !mSelectedDevices.contains(storedName)) {
                    mSelectedDevices.add(storedName);
                } else if (!checked) {
                    mSelectedDevices.remove(storedName);
                    mSelectedDevices.remove(deviceName);
                }
                saveDeviceList();
                return true;
            });
            mDevicesCategory.addPreference(pref);
        }

        // Show disconnected but selected controllers
        boolean hideSourceActive =
                SystemProperties.getInt(PROP_ENABLE, 0) != 0
                && SystemProperties.getInt(PROP_HIDE_SOURCE, 1) != 0;

        List<String> disconnected = new ArrayList<>();
        for (String selectedName : mSelectedDevices) {
            if (!connectedKernelNames.contains(selectedName) && !hideSourceActive) {
                disconnected.add(selectedName);
            }
        }
        for (final String selectedName : disconnected) {
            CheckBoxPreference pref = new CheckBoxPreference(getContext());
            pref.setKey("gamepad_device_disconnected_" + selectedName.hashCode());
            pref.setTitle(getString(R.string.gamepad_device_disconnected, selectedName));
            pref.setChecked(true);
            pref.setEnabled(true);
            pref.setOnPreferenceChangeListener((p, newVal) -> {
                boolean checked = (Boolean) newVal;
                if (!checked) {
                    mSelectedDevices.remove(selectedName);
                    saveDeviceList();
                    populateControllerList();
                }
                return true;
            });
            mDevicesCategory.addPreference(pref);
            found = true;
        }

        if (!found) {
            Preference empty = new Preference(getContext());
            empty.setTitle(R.string.gamepad_no_devices);
            empty.setSelectable(false);
            mDevicesCategory.addPreference(empty);
        }
    }

    private String findKernelName(InputDevice device, Map<String, String> kernelNameMap) {
        if (kernelNameMap.containsKey(device.getName())) {
            return device.getName();
        }
        for (Map.Entry<String, String> entry : kernelNameMap.entrySet()) {
            String name = entry.getKey();
            String eventNode = entry.getValue();
            String phys = readKernelDevicePhys(eventNode);
            if (phys != null && phys.contains("gammapad-virtual")) continue;
            if (name.equals("mtk-kpd") || name.equals("ACCDET")
                    || name.contains("_ts") || name.contains("-tpd")) continue;
            return name;
        }
        return null;
    }

    private void saveDeviceList() {
        String joined = String.join(";", mSelectedDevices);
        SystemProperties.set(PROP_DEVICES, joined);
        bumpConfigVersion();
    }

    // --- Summary helpers ---

    private void refreshRemapSummaries() {
        updateRemapSummary(findPreference(KEY_REMAP_BUTTONS), PROP_REMAP_BTN, BTN_NAMES);
        updateRemapSummary(findPreference(KEY_REMAP_AXES), PROP_REMAP_AXIS, AXIS_NAMES);
        updateAxisRolesSummary(findPreference(KEY_AXIS_ROLES));
        updateAxisButtonSummary(findPreference(KEY_AXIS_TO_BUTTON));
        updateBlacklistSummary(findPreference(KEY_BLACKLIST_VPAD), PROP_BLACKLIST_VPAD);
        updateBlacklistSummary(findPreference(KEY_BLACKLIST_PASS), PROP_BLACKLIST_PASS);
        updateComboMapSummary(findPreference(KEY_COMBO_MAP));
    }

    private void updateRemapSummary(Preference pref, String propKey,
                                     Map<Integer, String> codeNames) {
        if (pref == null) return;
        String remapStr = SystemProperties.get(propKey, "");
        if (remapStr.isEmpty()) {
            pref.setSummary(R.string.gamepad_remap_none);
            return;
        }
        StringBuilder sb = new StringBuilder();
        String[] pairs = remapStr.split(",");
        for (String pair : pairs) {
            String[] parts = pair.split(":");
            if (parts.length != 2) continue;
            try {
                int from = Integer.parseInt(parts[0]);
                int to = Integer.parseInt(parts[1]);
                String fromName = codeNames.getOrDefault(from,
                        "0x" + Integer.toHexString(from));
                String toName = codeNames.getOrDefault(to,
                        "0x" + Integer.toHexString(to));
                if (sb.length() > 0) sb.append(", ");
                sb.append(fromName).append(" -> ").append(toName);
            } catch (NumberFormatException e) {
                // skip
            }
        }
        pref.setSummary(sb.length() > 0 ? sb.toString()
                : getString(R.string.gamepad_remap_none));
    }

    private void updateAxisRolesSummary(Preference pref) {
        if (pref == null) return;
        String[][] roles = {
            { "persist.gammaos.gamepad.role_lx", "LX" },
            { "persist.gammaos.gamepad.role_ly", "LY" },
            { "persist.gammaos.gamepad.role_rx", "RX" },
            { "persist.gammaos.gamepad.role_ry", "RY" },
            { "persist.gammaos.gamepad.role_lt", "LT" },
            { "persist.gammaos.gamepad.role_rt", "RT" },
        };
        StringBuilder sb = new StringBuilder();
        for (String[] role : roles) {
            String val = SystemProperties.get(role[0], "");
            if (!val.isEmpty()) {
                try {
                    int code = Integer.parseInt(val);
                    String name = AXIS_NAMES.getOrDefault(code,
                            "0x" + Integer.toHexString(code));
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(role[1]).append("=").append(name);
                } catch (NumberFormatException e) {
                    // skip
                }
            }
        }
        pref.setSummary(sb.length() > 0 ? sb.toString()
                : getString(R.string.gamepad_axis_roles_none));
    }

    private void updateAxisButtonSummary(Preference pref) {
        if (pref == null) return;
        String axisBtnStr = SystemProperties.get(PROP_AXIS_BTN, "");
        if (axisBtnStr.isEmpty()) {
            pref.setSummary(R.string.gamepad_axis_to_button_none);
            return;
        }
        StringBuilder sb = new StringBuilder();
        String[] entries = axisBtnStr.split(",");
        for (String entry : entries) {
            String[] parts = entry.split(":");
            if (parts.length < 4) continue;
            try {
                int axis = Integer.parseInt(parts[0]);
                int btn = Integer.parseInt(parts[1]);
                int onPct = Integer.parseInt(parts[2]);
                int offPct = Integer.parseInt(parts[3]);
                String mode = parts.length >= 5 ? parts[4] : "b";
                String axisName = AXIS_NAMES.getOrDefault(axis,
                        "0x" + Integer.toHexString(axis));
                String btnName = BTN_NAMES.getOrDefault(btn,
                        "0x" + Integer.toHexString(btn));
                if (sb.length() > 0) sb.append(", ");
                sb.append(axisName).append(" -> ").append(btnName)
                        .append(" (").append(onPct).append("%/").append(offPct).append("%")
                        .append("h".equals(mode) ? " hijack" : " both").append(")");
            } catch (NumberFormatException e) {
                // skip
            }
        }
        pref.setSummary(sb.length() > 0 ? sb.toString()
                : getString(R.string.gamepad_axis_to_button_none));
    }

    private void updateBlacklistSummary(Preference pref, String prop) {
        if (pref == null) return;
        String val = SystemProperties.get(prop, "");
        if (val.isEmpty()) {
            pref.setSummary(R.string.gamepad_blacklist_none);
            return;
        }
        String[] codes = val.split(",");
        int count = 0;
        for (String code : codes) {
            if (!code.trim().isEmpty()) count++;
        }
        if (count == 0) {
            pref.setSummary(R.string.gamepad_blacklist_none);
        } else {
            pref.setSummary(getString(R.string.gamepad_blacklist_count, count));
        }
    }

    private void updateComboMapSummary(Preference pref) {
        if (pref == null) return;
        String comboStr = SystemProperties.get(PROP_COMBO_MAP, "");
        if (comboStr.isEmpty()) {
            pref.setSummary(R.string.gamepad_combo_map_none);
            return;
        }
        StringBuilder sb = new StringBuilder();
        String[] entries = comboStr.split(",");
        for (String entry : entries) {
            int plus = entry.indexOf('+');
            int eq = entry.indexOf('=');
            if (plus < 0 || eq < 0 || plus >= eq) continue;
            try {
                int btn1 = Integer.parseInt(entry.substring(0, plus));
                int btn2 = Integer.parseInt(entry.substring(plus + 1, eq));
                int emit = Integer.parseInt(entry.substring(eq + 1));
                String n1 = BTN_NAMES.getOrDefault(btn1, "0x" + Integer.toHexString(btn1));
                String n2 = BTN_NAMES.getOrDefault(btn2, "0x" + Integer.toHexString(btn2));
                String ne = BTN_NAMES.getOrDefault(emit, "0x" + Integer.toHexString(emit));
                if (sb.length() > 0) sb.append(", ");
                sb.append(n1).append("+").append(n2).append(" -> ").append(ne);
            } catch (NumberFormatException e) {
                // skip
            }
        }
        pref.setSummary(sb.length() > 0 ? sb.toString()
                : getString(R.string.gamepad_combo_map_none));
    }

    private void updateGlobalSensitivitySummary() {
        if (mGlobalSensitivityPref == null) return;
        CharSequence entry = mGlobalSensitivityPref.getEntry();
        if (entry != null) {
            mGlobalSensitivityPref.setSummary(entry);
        }
    }

    private void updateFFDeviceSummary() {
        if (mFFDevicePref == null) return;
        String path = SystemProperties.get(PROP_FF_DEVICE, "");
        if (path.isEmpty()) {
            mFFDevicePref.setSummary(R.string.gamepad_ff_device_summary_none);
        } else {
            mFFDevicePref.setSummary(
                    getString(R.string.gamepad_ff_device_summary_set, path));
        }
    }

    private void updateMouseComboSummary(Preference pref) {
        if (pref == null) return;
        int btn1 = SystemProperties.getInt(PROP_MOUSE_COMBO1, 0x13a);
        int btn2 = SystemProperties.getInt(PROP_MOUSE_COMBO2, 0x137);
        String name1 = BTN_NAMES.getOrDefault(btn1, "0x" + Integer.toHexString(btn1));
        String name2 = BTN_NAMES.getOrDefault(btn2, "0x" + Integer.toHexString(btn2));
        pref.setSummary(getString(R.string.gamepad_mouse_combo_current, name1, name2));
    }

    private void updateMouseButtonsSummary(Preference pref) {
        if (pref == null) return;
        int click = SystemProperties.getInt(PROP_MOUSE_BTN_CLICK, 0x130);
        int back = SystemProperties.getInt(PROP_MOUSE_BTN_BACK, 0x131);
        int rclick = SystemProperties.getInt(PROP_MOUSE_BTN_RCLICK, 0x134);
        int boost = SystemProperties.getInt(PROP_MOUSE_BTN_BOOST, 0x133);
        String clickN = BTN_NAMES.getOrDefault(click, "0x" + Integer.toHexString(click));
        String backN = BTN_NAMES.getOrDefault(back, "0x" + Integer.toHexString(back));
        String rclickN = BTN_NAMES.getOrDefault(rclick, "0x" + Integer.toHexString(rclick));
        String boostN = BTN_NAMES.getOrDefault(boost, "0x" + Integer.toHexString(boost));
        pref.setSummary("Click=" + clickN + ", Back=" + backN
                + ", RClick=" + rclickN + ", Boost=" + boostN);
    }

    // --- Preference change handler ---

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();

        switch (key) {
            case KEY_ENABLE:
                SystemProperties.set(PROP_ENABLE, (Boolean) newValue ? "1" : "0");
                mHandler.post(this::updateDependentVisibility);
                return true;
            case KEY_MERGE:
                SystemProperties.set(PROP_MERGE, (Boolean) newValue ? "1" : "0");
                bumpConfigVersion();
                return true;
            case KEY_HIDE_SOURCE:
                SystemProperties.set(PROP_HIDE_SOURCE, (Boolean) newValue ? "1" : "0");
                bumpConfigVersion();
                return true;
            case KEY_DEVICE_PRESET:
                applyDevicePreset((String) newValue);
                return false;
            case KEY_ANALOG_TO_DPAD:
                SystemProperties.set(PROP_ANALOG_TO_DPAD, (Boolean) newValue ? "1" : "0");
                bumpConfigVersion();
                return true;
            case KEY_DPAD_TO_ANALOG:
                SystemProperties.set(PROP_DPAD_TO_ANALOG, (Boolean) newValue ? "1" : "0");
                bumpConfigVersion();
                return true;
            case KEY_PWM_ENABLE:
                SystemProperties.set(PROP_PWM_ENABLE, (Boolean) newValue ? "1" : "0");
                bumpConfigVersion();
                mHandler.post(this::updateDependentVisibility);
                return true;
            case KEY_ABXY_SWAP:
                SystemProperties.set(PROP_ABXY_SWAP, (Boolean) newValue ? "1" : "0");
                bumpConfigVersion();
                return true;
            case KEY_INVERT_LEFT:
                SystemProperties.set(PROP_INVERT_LEFT, (Boolean) newValue ? "1" : "0");
                bumpConfigVersion();
                return true;
            case KEY_INVERT_RIGHT:
                SystemProperties.set(PROP_INVERT_RIGHT, (Boolean) newValue ? "1" : "0");
                bumpConfigVersion();
                return true;
            case KEY_GLOBAL_SENSITIVITY:
                SystemProperties.set(PROP_GLOBAL_SENSITIVITY, (String) newValue);
                bumpConfigVersion();
                updateGlobalSensitivitySummary();
                return true;
            case KEY_MOUSE_ENABLE:
                boolean mouseEnabled = (Boolean) newValue;
                if (mouseEnabled) {
                    if (SystemProperties.get(PROP_MOUSE_COMBO1, "").isEmpty()) {
                        SystemProperties.set(PROP_MOUSE_COMBO1, "314");
                        SystemProperties.set(PROP_MOUSE_COMBO2, "311");
                    }
                } else {
                    SystemProperties.set(PROP_MOUSE_COMBO1, "");
                    SystemProperties.set(PROP_MOUSE_COMBO2, "");
                }
                bumpConfigVersion();
                mHandler.post(this::updateDependentVisibility);
                return true;
            case KEY_MOUSE_BOOST:
                SystemProperties.set(PROP_MOUSE_BOOST, (String) newValue);
                bumpConfigVersion();
                return true;
        }
        return false;
    }

    // --- Preference click handler ---

    @Override
    public boolean onPreferenceClick(Preference preference) {
        String key = preference.getKey();
        if (key == null) return false;

        switch (key) {
            case KEY_REMAP_BUTTONS:
                showRemapDialog(false);
                return true;
            case KEY_REMAP_AXES:
                showRemapDialog(true);
                return true;
            case KEY_AXIS_ROLES:
                showAxisRolesDialog();
                return true;
            case KEY_AXIS_TO_BUTTON:
                showAxisToButtonDialog();
                return true;
            case KEY_BLACKLIST_VPAD:
                showBlacklistDialog(PROP_BLACKLIST_VPAD, R.string.gamepad_blacklist_vpad_title);
                return true;
            case KEY_BLACKLIST_PASS:
                showBlacklistDialog(PROP_BLACKLIST_PASS, R.string.gamepad_blacklist_pass_title);
                return true;
            case KEY_COMBO_MAP:
                showComboMapDialog();
                return true;
            case KEY_CUSTOM_ACTIONS:
                showActionListDialog();
                return true;
            case KEY_CALIBRATION:
                showCalibrationDialog();
                return true;
            case KEY_CLEAR_CALIBRATION:
                clearCalibration();
                return true;
            case KEY_FF_DEVICE:
                showFFDeviceDialog();
                return true;
            case KEY_TEST_VIBRATION:
                showVibrationTestDialog();
                return true;
            case KEY_TEST:
                showGamepadTestDialog();
                return true;
            case KEY_PERAPP_ADD:
                showPerAppPickerDialog();
                return true;
            case KEY_MOUSE_COMBO:
                showMouseComboDialog();
                return true;
            case KEY_MOUSE_BUTTONS:
                showMouseButtonsDialog();
                return true;
            case KEY_MOUSE_HOLD_TIME:
                showSliderDialog(preference, PROP_MOUSE_HOLD_MS, 2000, 500, 5000);
                return true;
            case KEY_MOUSE_STICK_SPEED:
                showSliderDialog(preference, PROP_MOUSE_STICK_SPEED, 12, 1, 30);
                return true;
            case KEY_MOUSE_DPAD_SPEED:
                showSliderDialog(preference, PROP_MOUSE_DPAD_SPEED, 6, 1, 20);
                return true;
            case KEY_MOUSE_SCROLL_SPEED:
                showSliderDialog(preference, PROP_MOUSE_SCROLL_SPEED, 4, 1, 30);
                return true;
            case KEY_DPAD_THRESHOLD:
                showSliderDialog(preference, PROP_DPAD_THRESHOLD, 50, 0, 100);
                return true;
            case KEY_PWM_INTENSITY:
                showSliderDialog(preference, PROP_PWM_INTENSITY, 200, 0, 255);
                return true;
        }

        // Per-app profile click
        if (key.startsWith("gamepad_perapp_profile_")) {
            int idx = Integer.parseInt(key.substring("gamepad_perapp_profile_".length()));
            showPerAppEditDialog(idx);
            return true;
        }

        return false;
    }

    // --- Slider dialog (replaces all SeekBarPreferences) ---

    private void showSliderDialog(Preference pref, String prop, int defaultVal,
                                   int min, int max) {
        Context context = getContext();
        if (context == null) return;

        int cur = SystemProperties.getInt(prop, defaultVal);

        final TextView label = new TextView(context);
        label.setText(String.valueOf(cur));
        label.setTextSize(18);
        label.setGravity(Gravity.CENTER);

        final SeekBar seekBar = new SeekBar(context);
        seekBar.setMax(max - min);
        seekBar.setProgress(cur - min);
        seekBar.setFocusable(true);
        seekBar.setFocusableInTouchMode(true);
        // D-pad left/right adjusts the seekbar in dialog mode
        seekBar.setKeyProgressIncrement(1);

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                int val = progress + min;
                label.setText(String.valueOf(val));
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });

        int pad = dp(20);
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(pad, pad, pad, 0);
        layout.addView(seekBar);
        layout.addView(label);

        new AlertDialog.Builder(context)
                .setTitle(pref.getTitle())
                .setView(layout)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    int val = seekBar.getProgress() + min;
                    SystemProperties.set(prop, String.valueOf(val));
                    pref.setSummary(String.valueOf(val));
                    bumpConfigVersion();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // --- Remap dialog (button or axis) ---

    private void showRemapDialog(boolean isAxis) {
        Context context = getContext();
        if (context == null) return;

        String prop = isAxis ? PROP_REMAP_AXIS : PROP_REMAP_BTN;
        Map<Integer, String> codeNames = isAxis ? AXIS_NAMES : BTN_NAMES;
        String current = SystemProperties.get(prop, "");

        List<String> items = new ArrayList<>();

        // Show current remaps
        String[] pairs = current.isEmpty() ? new String[0] : current.split(",");
        for (String pair : pairs) {
            String[] parts = pair.split(":");
            if (parts.length == 2) {
                try {
                    int from = Integer.parseInt(parts[0]);
                    int to = Integer.parseInt(parts[1]);
                    String fn = codeNames.getOrDefault(from, "0x" + Integer.toHexString(from));
                    String tn = codeNames.getOrDefault(to, "0x" + Integer.toHexString(to));
                    items.add(fn + " -> " + tn);
                } catch (NumberFormatException e) {
                    items.add(pair);
                }
            }
        }

        int currentCount = items.size();
        items.add(getString(R.string.gamepad_remap_add_new));
        if (currentCount > 0) {
            items.add(getString(R.string.gamepad_remap_clear_all));
        }

        new AlertDialog.Builder(context)
                .setTitle(isAxis ? R.string.gamepad_remap_axes_title
                        : R.string.gamepad_remap_buttons_title)
                .setItems(items.toArray(new String[0]), (d, which) -> {
                    if (which < currentCount) {
                        // Remove this remap
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < pairs.length; i++) {
                            if (i == which) continue;
                            if (sb.length() > 0) sb.append(",");
                            sb.append(pairs[i]);
                        }
                        SystemProperties.set(prop, sb.toString());
                        bumpConfigVersion();
                        refreshRemapSummaries();
                    } else if (which == currentCount) {
                        showAddRemapDialog(isAxis);
                    } else {
                        SystemProperties.set(prop, "");
                        bumpConfigVersion();
                        refreshRemapSummaries();
                        Toast.makeText(context, R.string.gamepad_remap_cleared,
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // ===================== Custom Button Actions =====================
    // Short/long-press action editor. Rules are stored as act_count + actN_*
    // (name=value specs: key=<code>, app=<pkg>, act=<pkg/comp>, prop=<n=v>,
    // sh=<cmd>) and consumed by the gammapad daemon. AlertDialog chain to match
    // the other Leanback pickers here.

    private static class ActRule { int code; int hold; String s = ""; String l = ""; }

    private List<ActRule> actLoad() {
        List<ActRule> out = new ArrayList<>();
        int n = SystemProperties.getInt(PROP_ACT_COUNT, 0);
        for (int i = 0; i < n && i < 64; i++) {
            String p = "persist.gammaos.gamepad.act" + i;
            int code = SystemProperties.getInt(p + "_code", 0);
            if (code <= 0) continue;
            ActRule r = new ActRule();
            r.code = code;
            r.hold = SystemProperties.getInt(p + "_hold", 0);
            r.s = SystemProperties.get(p + "_s", "");
            r.l = SystemProperties.get(p + "_l", "");
            out.add(r);
        }
        return out;
    }

    private void actStore(List<ActRule> rules) {
        int n = Math.min(rules.size(), 64);
        for (int i = 0; i < n; i++) {
            String p = "persist.gammaos.gamepad.act" + i;
            ActRule r = rules.get(i);
            SystemProperties.set(p + "_code", String.valueOf(r.code));
            SystemProperties.set(p + "_hold", String.valueOf(r.hold));
            SystemProperties.set(p + "_s", r.s == null ? "" : r.s);
            SystemProperties.set(p + "_l", r.l == null ? "" : r.l);
        }
        SystemProperties.set(PROP_ACT_COUNT, String.valueOf(n));
        bumpConfigVersion();
    }

    private ActRule actFind(List<ActRule> rules, int code) {
        for (ActRule r : rules) if (r.code == code) return r;
        return null;
    }

    private void actSetSlot(int code, int slot, String spec) {
        List<ActRule> rules = actLoad();
        ActRule r = actFind(rules, code);
        if (r == null) { r = new ActRule(); r.code = code; r.hold = 500; rules.add(r); }
        if (slot == 0) r.s = spec; else r.l = spec;
        boolean se = (r.s == null || r.s.isEmpty());
        boolean le = (r.l == null || r.l.isEmpty());
        if (se && le) rules.remove(r);
        actStore(rules);
    }

    private void actSetHold(int code, int hold) {
        List<ActRule> rules = actLoad();
        ActRule r = actFind(rules, code);
        if (r == null) { r = new ActRule(); r.code = code; rules.add(r); }
        r.hold = hold;
        actStore(rules);
    }

    private void actRemove(int code) {
        List<ActRule> rules = actLoad();
        ActRule r = actFind(rules, code);
        if (r != null) rules.remove(r);
        actStore(rules);
    }

    private String actKeyName(int code) {
        String n = BTN_NAMES.get(code);
        if (n != null) return n;
        n = ACTION_KEYS.get(code);
        if (n != null) return n;
        return "0x" + Integer.toHexString(code);
    }

    private String actSummary(String spec) {
        if (spec == null || spec.isEmpty()) return "Not set";
        int eq = spec.indexOf('=');
        String t = eq < 0 ? spec : spec.substring(0, eq);
        String a = eq < 0 ? "" : spec.substring(eq + 1);
        switch (t) {
            case "key":
                try { return "Key: " + actKeyName(Integer.parseInt(a.trim())); }
                catch (Exception e) { return "Key"; }
            case "app": return "Launch " + a;
            case "act": return "Open " + a;
            case "prop": { int e2 = a.indexOf('='); return "Set " + (e2 < 0 ? a : a.substring(0, e2)); }
            case "sh": return "Run: " + (a.length() > 20 ? a.substring(0, 18) + ".." : a);
            default: return "Not set";
        }
    }

    private void showActionListDialog() {
        Context context = getContext();
        if (context == null) return;
        List<ActRule> rules = actLoad();
        final List<ActRule> shown = new ArrayList<>();
        for (ActRule r : rules) {
            if (!(r.s == null || r.s.isEmpty()) || !(r.l == null || r.l.isEmpty())) shown.add(r);
        }
        List<String> labels = new ArrayList<>();
        for (ActRule r : shown) {
            String lbl = actKeyName(r.code) + "  -  " + actSummary(r.s);
            if (!(r.l == null || r.l.isEmpty())) lbl += "  /  hold: " + actSummary(r.l);
            labels.add(lbl);
        }
        labels.add("+ Add mapping...");
        new AlertDialog.Builder(context)
                .setTitle("Custom Button Actions")
                .setItems(labels.toArray(new String[0]), (d, which) -> {
                    if (which < shown.size()) showActionEditDialog(shown.get(which).code);
                    else showActionSourcePick();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showActionSourcePick() {
        Context context = getContext();
        if (context == null) return;
        final List<Integer> codes = new ArrayList<>(BTN_NAMES.keySet());
        Collections.sort(codes);
        String[] labels = new String[codes.size()];
        for (int i = 0; i < codes.size(); i++) labels[i] = BTN_NAMES.get(codes.get(i));
        new AlertDialog.Builder(context)
                .setTitle("Pick the button to map")
                .setItems(labels, (d, which) -> showActionEditDialog(codes.get(which)))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showActionEditDialog(int code) {
        Context context = getContext();
        if (context == null) return;
        List<ActRule> rules = actLoad();
        ActRule r = actFind(rules, code);
        String s = r != null ? r.s : "";
        String l = r != null ? r.l : "";
        final int hold = (r != null && r.hold > 0) ? r.hold : 500;
        String[] items = {
            "Short Press:  " + actSummary(s),
            "Long Press:  " + actSummary(l),
            "Hold Time:  " + hold + " ms",
            "Remove Mapping",
        };
        new AlertDialog.Builder(context)
                .setTitle("Map: " + actKeyName(code))
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0: showActionTypeDialog(code, 0); break;
                        case 1: showActionTypeDialog(code, 1); break;
                        case 2: {
                            int nh = (hold <= 300) ? 500 : (hold <= 500) ? 750 : (hold <= 750) ? 1000 : 300;
                            actSetHold(code, nh);
                            showActionEditDialog(code);
                            break;
                        }
                        case 3: actRemove(code); showActionListDialog(); break;
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showActionTypeDialog(int code, int slot) {
        Context context = getContext();
        if (context == null) return;
        String[] items = {
            "Button / Key", "Launch App", "Launch Activity",
            "Set Property", "Run Shell Command", "None (clear)",
        };
        new AlertDialog.Builder(context)
                .setTitle((slot == 0 ? "Short" : "Long") + " Press Action")
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0: showActionKeyDialog(code, slot); break;
                        case 1: showActionAppDialog(code, slot, false); break;
                        case 2: showActionAppDialog(code, slot, true); break;
                        case 3: showActionTextDialog(code, slot, true); break;
                        case 4: showActionTextDialog(code, slot, false); break;
                        case 5: actSetSlot(code, slot, ""); showActionEditDialog(code); break;
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showActionKeyDialog(int code, int slot) {
        Context context = getContext();
        if (context == null) return;
        final List<Integer> codes = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        List<Integer> btns = new ArrayList<>(BTN_NAMES.keySet());
        Collections.sort(btns);
        for (int c : btns) { codes.add(c); labels.add("Button " + BTN_NAMES.get(c)); }
        for (Map.Entry<Integer, String> e : ACTION_KEYS.entrySet()) { codes.add(e.getKey()); labels.add(e.getValue()); }
        new AlertDialog.Builder(context)
                .setTitle("Choose Key / Button")
                .setItems(labels.toArray(new String[0]), (d, which) -> {
                    actSetSlot(code, slot, "key=" + codes.get(which));
                    showActionEditDialog(code);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showActionAppDialog(int code, int slot, boolean perActivity) {
        Context context = getContext();
        if (context == null) return;
        PackageManager pm = context.getPackageManager();
        Intent probe = new Intent(Intent.ACTION_MAIN);
        probe.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> ris = pm.queryIntentActivities(probe, PackageManager.MATCH_ALL);
        final List<String> labels = new ArrayList<>();
        final List<String> values = new ArrayList<>();
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        if (ris != null) {
            ris.sort((a, b) -> String.valueOf(a.loadLabel(pm))
                    .compareToIgnoreCase(String.valueOf(b.loadLabel(pm))));
            for (ResolveInfo ri : ris) {
                if (ri.activityInfo == null) continue;
                String pkg = ri.activityInfo.packageName;
                String cls = ri.activityInfo.name;
                if (pkg == null || cls == null) continue;
                String label = String.valueOf(ri.loadLabel(pm));
                if (perActivity) {
                    labels.add(label);
                    values.add(pkg + "/" + cls);
                } else {
                    if (!seen.add(pkg)) continue;
                    labels.add(label);
                    values.add(pkg);
                }
            }
        }
        if (labels.isEmpty()) {
            Toast.makeText(context, "No apps found", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(context)
                .setTitle(perActivity ? "Launch Activity" : "Launch App")
                .setItems(labels.toArray(new String[0]), (d, which) -> {
                    actSetSlot(code, slot, (perActivity ? "act=" : "app=") + values.get(which));
                    showActionEditDialog(code);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showActionTextDialog(int code, int slot, boolean isProp) {
        Context context = getContext();
        if (context == null) return;
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        layout.setPadding(pad, pad, pad, 0);
        final EditText input = new EditText(context);
        input.setHint(isProp ? "name=value" : "shell command");
        layout.addView(input);
        new AlertDialog.Builder(context)
                .setTitle(isProp ? "Set Property" : "Run Shell Command")
                .setView(layout)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String v = input.getText().toString().trim();
                    if (!v.isEmpty()) actSetSlot(code, slot, (isProp ? "prop=" : "sh=") + v);
                    showActionEditDialog(code);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showAddRemapDialog(boolean isAxis) {
        Context context = getContext();
        if (context == null) return;

        Map<Integer, String> codeNames = isAxis ? AXIS_NAMES : BTN_NAMES;
        String prop = isAxis ? PROP_REMAP_AXIS : PROP_REMAP_BTN;

        List<Integer> codes = new ArrayList<>(codeNames.keySet());
        Collections.sort(codes);
        String[] labels = new String[codes.size()];
        for (int i = 0; i < codes.size(); i++) {
            labels[i] = codeNames.get(codes.get(i))
                    + " (0x" + Integer.toHexString(codes.get(i)) + ")";
        }

        // Also offer custom text entry
        String[] labelsWithCustom = new String[labels.length + 1];
        System.arraycopy(labels, 0, labelsWithCustom, 0, labels.length);
        labelsWithCustom[labels.length] = getString(R.string.gamepad_remap_custom);

        new AlertDialog.Builder(context)
                .setTitle(R.string.gamepad_remap_dialog_title)
                .setItems(labelsWithCustom, (d, which) -> {
                    if (which < codes.size()) {
                        int fromCode = codes.get(which);
                        showRemapTargetDialog(isAxis, fromCode);
                    } else {
                        showCustomRemapInputDialog(isAxis);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showRemapTargetDialog(boolean isAxis, int fromCode) {
        Context context = getContext();
        if (context == null) return;

        Map<Integer, String> codeNames = isAxis ? AXIS_NAMES : BTN_NAMES;
        String prop = isAxis ? PROP_REMAP_AXIS : PROP_REMAP_BTN;

        List<Integer> codes = new ArrayList<>(codeNames.keySet());
        Collections.sort(codes);
        String[] labels = new String[codes.size()];
        for (int i = 0; i < codes.size(); i++) {
            labels[i] = codeNames.get(codes.get(i))
                    + " (0x" + Integer.toHexString(codes.get(i)) + ")";
        }

        String fromName = codeNames.getOrDefault(fromCode,
                "0x" + Integer.toHexString(fromCode));

        new AlertDialog.Builder(context)
                .setTitle(getString(R.string.gamepad_remap_choose_target, fromName))
                .setItems(labels, (d, which) -> {
                    int toCode = codes.get(which);
                    String current = SystemProperties.get(prop, "");
                    String rule = fromCode + ":" + toCode;
                    String newVal = current.isEmpty() ? rule : current + "," + rule;
                    SystemProperties.set(prop, newVal);
                    bumpConfigVersion();
                    refreshRemapSummaries();
                    Toast.makeText(context, R.string.gamepad_remap_saved,
                            Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showCustomRemapInputDialog(boolean isAxis) {
        Context context = getContext();
        if (context == null) return;

        String prop = isAxis ? PROP_REMAP_AXIS : PROP_REMAP_BTN;

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        layout.setPadding(pad, pad, pad, 0);

        TextView fromLabel = new TextView(context);
        fromLabel.setText("Source code (decimal or 0xHEX):");
        layout.addView(fromLabel);

        EditText fromInput = new EditText(context);
        fromInput.setInputType(InputType.TYPE_CLASS_TEXT);
        layout.addView(fromInput);

        TextView toLabel = new TextView(context);
        toLabel.setText("Target code (decimal or 0xHEX):");
        layout.addView(toLabel);

        EditText toInput = new EditText(context);
        toInput.setInputType(InputType.TYPE_CLASS_TEXT);
        layout.addView(toInput);

        new AlertDialog.Builder(context)
                .setTitle(R.string.gamepad_remap_dialog_title)
                .setView(layout)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String fromStr = fromInput.getText().toString().trim();
                    String toStr = toInput.getText().toString().trim();
                    try {
                        int from = parseIntOrHex(fromStr);
                        int to = parseIntOrHex(toStr);
                        String current = SystemProperties.get(prop, "");
                        String rule = from + ":" + to;
                        String newVal = current.isEmpty() ? rule : current + "," + rule;
                        SystemProperties.set(prop, newVal);
                        bumpConfigVersion();
                        refreshRemapSummaries();
                        Toast.makeText(context, R.string.gamepad_remap_saved,
                                Toast.LENGTH_SHORT).show();
                    } catch (NumberFormatException e) {
                        Toast.makeText(context, "Invalid code format",
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // --- Axis roles dialog ---

    private void showAxisRolesDialog() {
        Context context = getContext();
        if (context == null) return;

        String[][] roles = {
            { "persist.gammaos.gamepad.role_lx", "LX" },
            { "persist.gammaos.gamepad.role_ly", "LY" },
            { "persist.gammaos.gamepad.role_rx", "RX" },
            { "persist.gammaos.gamepad.role_ry", "RY" },
            { "persist.gammaos.gamepad.role_lt", "LT" },
            { "persist.gammaos.gamepad.role_rt", "RT" },
        };

        String[] items = new String[roles.length + 2];
        for (int i = 0; i < roles.length; i++) {
            String val = SystemProperties.get(roles[i][0], "");
            String assigned = "Auto";
            if (!val.isEmpty()) {
                try {
                    int code = Integer.parseInt(val);
                    assigned = AXIS_NAMES.getOrDefault(code, "0x" + Integer.toHexString(code));
                } catch (NumberFormatException e) {
                    // skip
                }
            }
            items[i] = roles[i][1] + ": " + assigned;
        }
        items[roles.length] = getString(R.string.gamepad_axis_roles_clear);
        items[roles.length + 1] = getString(R.string.gamepad_axis_roles_save);

        new AlertDialog.Builder(context)
                .setTitle(R.string.gamepad_axis_roles_title)
                .setItems(items, (d, which) -> {
                    if (which < roles.length) {
                        showAxisRolePickerDialog(roles[which][0], roles[which][1]);
                    } else if (which == roles.length) {
                        // Clear all
                        for (String[] role : roles) {
                            SystemProperties.set(role[0], "");
                        }
                        bumpConfigVersion();
                        refreshRemapSummaries();
                        Toast.makeText(context, R.string.gamepad_axis_roles_cleared,
                                Toast.LENGTH_SHORT).show();
                    }
                    // Save is implicit - changes are already written per-pick
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showAxisRolePickerDialog(String prop, String roleName) {
        Context context = getContext();
        if (context == null) return;

        List<Integer> axisCodes = new ArrayList<>(AXIS_NAMES.keySet());
        Collections.sort(axisCodes);
        String[] labels = new String[axisCodes.size() + 1];
        labels[0] = getString(R.string.gamepad_axis_roles_auto);
        for (int i = 0; i < axisCodes.size(); i++) {
            labels[i + 1] = AXIS_NAMES.get(axisCodes.get(i))
                    + " (0x" + Integer.toHexString(axisCodes.get(i)) + ")";
        }

        new AlertDialog.Builder(context)
                .setTitle(getString(R.string.gamepad_axis_roles_detecting, roleName))
                .setItems(labels, (d, which) -> {
                    if (which == 0) {
                        SystemProperties.set(prop, "");
                    } else {
                        SystemProperties.set(prop,
                                String.valueOf(axisCodes.get(which - 1)));
                    }
                    bumpConfigVersion();
                    refreshRemapSummaries();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // --- Axis to button dialog ---

    private void showAxisToButtonDialog() {
        Context context = getContext();
        if (context == null) return;

        String current = SystemProperties.get(PROP_AXIS_BTN, "");

        List<String> items = new ArrayList<>();

        if (!current.isEmpty()) {
            String[] entries = current.split(",");
            for (String entry : entries) {
                String[] parts = entry.split(":");
                if (parts.length >= 4) {
                    try {
                        int axis = Integer.parseInt(parts[0]);
                        int btn = Integer.parseInt(parts[1]);
                        String mode = parts.length >= 5 ? parts[4] : "b";
                        String axisName = AXIS_NAMES.getOrDefault(axis,
                                "0x" + Integer.toHexString(axis));
                        String btnName = BTN_NAMES.getOrDefault(btn,
                                "0x" + Integer.toHexString(btn));
                        items.add(axisName + " -> " + btnName + " (" + parts[2] + "%/"
                                + parts[3] + "%" + ("h".equals(mode) ? " hijack" : " both")
                                + ")");
                    } catch (NumberFormatException e) {
                        items.add(entry);
                    }
                }
            }
        }

        int currentCount = items.size();
        items.add(getString(R.string.gamepad_axis_to_button_add));
        if (!current.isEmpty()) {
            items.add(getString(R.string.gamepad_axis_to_button_test));
            items.add(getString(R.string.gamepad_axis_to_button_clear_all));
        }

        new AlertDialog.Builder(context)
                .setTitle(R.string.gamepad_axis_to_button_title)
                .setItems(items.toArray(new String[0]), (d, which) -> {
                    if (which < currentCount) {
                        // Remove this rule
                        String[] entries = current.split(",");
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < entries.length; i++) {
                            if (i == which) continue;
                            if (sb.length() > 0) sb.append(",");
                            sb.append(entries[i]);
                        }
                        SystemProperties.set(PROP_AXIS_BTN, sb.toString());
                        bumpConfigVersion();
                        refreshRemapSummaries();
                    } else {
                        int idx = which - currentCount;
                        if (idx == 0) {
                            showAddAxisButtonRuleDialog();
                        } else if (idx == 1) {
                            showAxisButtonTestDialog();
                        } else if (idx == 2) {
                            SystemProperties.set(PROP_AXIS_BTN, "");
                            bumpConfigVersion();
                            refreshRemapSummaries();
                            Toast.makeText(context, R.string.gamepad_axis_to_button_cleared,
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showAddAxisButtonRuleDialog() {
        Context context = getContext();
        if (context == null) return;

        String[] axisItems = {"LX (0)", "LY (1)", "LT (2)", "RX (3)", "RY (4)", "RT (5)",
                              "GAS (9)", "BRAKE (10)"};
        int[] axisCodes = {0, 1, 2, 3, 4, 5, 9, 10};

        new AlertDialog.Builder(context)
                .setTitle(R.string.gamepad_axis_to_button_axis)
                .setItems(axisItems, (d, axisIdx) -> {
                    int axisCode = axisCodes[axisIdx];
                    showSelectButtonForAxisDialog(axisCode);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showSelectButtonForAxisDialog(int axisCode) {
        Context context = getContext();
        if (context == null) return;

        String[] btnItems = {"A (304)", "B (305)", "X (307)", "Y (308)",
                             "LB (310)", "RB (311)", "L2 (312)", "R2 (313)",
                             "Select (314)", "Start (315)", "Guide (316)",
                             "L3 (317)", "R3 (318)"};
        int[] btnCodes = {304, 305, 307, 308, 310, 311, 312, 313, 314, 315, 316, 317, 318};

        new AlertDialog.Builder(context)
                .setTitle(R.string.gamepad_axis_to_button_button)
                .setItems(btnItems, (d, btnIdx) -> {
                    int btnCode = btnCodes[btnIdx];
                    showThresholdDialog(axisCode, btnCode);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showThresholdDialog(int axisCode, int btnCode) {
        Context context = getContext();
        if (context == null) return;

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        layout.setPadding(pad, pad, pad, 0);

        TextView onLabel = new TextView(context);
        onLabel.setText(getString(R.string.gamepad_axis_to_button_on));
        layout.addView(onLabel);

        EditText onInput = new EditText(context);
        onInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        onInput.setText("80");
        layout.addView(onInput);

        TextView offLabel = new TextView(context);
        offLabel.setText(getString(R.string.gamepad_axis_to_button_off));
        layout.addView(offLabel);

        EditText offInput = new EditText(context);
        offInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        offInput.setText("60");
        layout.addView(offInput);

        // Mode selection
        final String[] modeChoice = {"b"};
        TextView modeLabel = new TextView(context);
        modeLabel.setText("Mode:");
        LinearLayout.LayoutParams modeLabelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        modeLabelParams.topMargin = dp(12);
        layout.addView(modeLabel, modeLabelParams);

        RadioGroup modeGroup = new RadioGroup(context);
        modeGroup.setOrientation(RadioGroup.VERTICAL);
        RadioButton broadcastBtn = new RadioButton(context);
        broadcastBtn.setText("Both (emit BTN and keep ABS value)");
        broadcastBtn.setId(View.generateViewId());
        modeGroup.addView(broadcastBtn);
        RadioButton hijackBtn = new RadioButton(context);
        hijackBtn.setText("Hijack (replace ABS with BTN, no ABS output)");
        hijackBtn.setId(View.generateViewId());
        modeGroup.addView(hijackBtn);
        modeGroup.check(broadcastBtn.getId());
        modeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            modeChoice[0] = (checkedId == hijackBtn.getId()) ? "h" : "b";
        });
        layout.addView(modeGroup);

        new AlertDialog.Builder(context)
                .setTitle(R.string.gamepad_axis_to_button_title)
                .setView(layout)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    int onPct = 80;
                    int offPct = 60;
                    try {
                        onPct = Integer.parseInt(onInput.getText().toString().trim());
                        offPct = Integer.parseInt(offInput.getText().toString().trim());
                    } catch (NumberFormatException e) {
                        // Use defaults
                    }
                    onPct = Math.max(0, Math.min(100, onPct));
                    offPct = Math.max(0, Math.min(100, offPct));

                    String current = SystemProperties.get(PROP_AXIS_BTN, "");
                    String rule = axisCode + ":" + btnCode + ":" + onPct + ":" + offPct
                            + ":" + modeChoice[0];
                    String newVal = current.isEmpty() ? rule : current + "," + rule;
                    SystemProperties.set(PROP_AXIS_BTN, newVal);
                    bumpConfigVersion();
                    refreshRemapSummaries();
                    Toast.makeText(context, R.string.gamepad_axis_to_button_saved,
                            Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showAxisButtonTestDialog() {
        Context context = getContext();
        if (context == null) return;

        String current = SystemProperties.get(PROP_AXIS_BTN, "");
        if (current.isEmpty()) return;

        String[] entries = current.split(",");

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad);

        final TextView[] valueLabels = new TextView[entries.length];
        final ProgressBar[] bars = new ProgressBar[entries.length];
        final TextView[] stateLabels = new TextView[entries.length];
        final int[] axisCodes = new int[entries.length];
        final int[] onThresholds = new int[entries.length];
        final int[] offThresholds = new int[entries.length];

        for (int i = 0; i < entries.length; i++) {
            String[] parts = entries[i].split(":");
            if (parts.length < 4) continue;

            int axis = Integer.parseInt(parts[0]);
            int btn = Integer.parseInt(parts[1]);
            int onPct = Integer.parseInt(parts[2]);
            int offPct = Integer.parseInt(parts[3]);
            String mode = parts.length >= 5 ? parts[4] : "b";

            axisCodes[i] = axis;
            onThresholds[i] = onPct;
            offThresholds[i] = offPct;

            String axisName = AXIS_NAMES.getOrDefault(axis,
                    "0x" + Integer.toHexString(axis));
            String btnName = BTN_NAMES.getOrDefault(btn,
                    "0x" + Integer.toHexString(btn));

            TextView header = new TextView(context);
            header.setText(axisName + " -> " + btnName
                    + " (on:" + onPct + "% off:" + offPct + "%"
                    + ("h".equals(mode) ? " hijack" : "") + ")");
            header.setTextSize(14);
            header.setTextColor(0xFFFFFFFF);
            if (i > 0) {
                LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                hp.topMargin = pad;
                root.addView(header, hp);
            } else {
                root.addView(header);
            }

            bars[i] = new ProgressBar(context, null,
                    android.R.attr.progressBarStyleHorizontal);
            bars[i].setMax(100);
            bars[i].setProgress(0);
            root.addView(bars[i], new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));

            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);

            valueLabels[i] = new TextView(context);
            valueLabels[i].setText("0%");
            valueLabels[i].setTextSize(13);
            valueLabels[i].setTextColor(0xFFAAAAAA);
            row.addView(valueLabels[i], new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            stateLabels[i] = new TextView(context);
            stateLabels[i].setText("RELEASED");
            stateLabels[i].setTextSize(13);
            stateLabels[i].setTextColor(0xFF888888);
            stateLabels[i].setGravity(Gravity.END);
            row.addView(stateLabels[i], new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            root.addView(row);
        }

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.gamepad_axis_to_button_test)
                .setView(root)
                .setPositiveButton(android.R.string.ok, null)
                .create();

        final boolean[] pressed = new boolean[entries.length];

        dialog.show();

        dialog.getWindow().getDecorView().setOnGenericMotionListener((v, event) -> {
            if ((event.getSource() & InputDevice.SOURCE_JOYSTICK) == 0) return false;

            for (int i = 0; i < entries.length; i++) {
                if (bars[i] == null) continue;

                int androidAxis = ABS_TO_ANDROID_AXIS.getOrDefault(
                        axisCodes[i], axisCodes[i]);
                float raw = event.getAxisValue(androidAxis);

                InputDevice.MotionRange range = event.getDevice() != null
                        ? event.getDevice().getMotionRange(androidAxis) : null;
                int pct;
                if (range != null && range.getMin() >= 0f) {
                    pct = Math.round(Math.max(0f, Math.min(1f, raw)) * 100f);
                } else {
                    pct = Math.round(Math.max(0f, Math.min(1f, Math.abs(raw))) * 100f);
                }

                bars[i].setProgress(pct);
                valueLabels[i].setText(pct + "%");

                if (pct >= onThresholds[i]) {
                    pressed[i] = true;
                } else if (pct <= offThresholds[i]) {
                    pressed[i] = false;
                }

                if (pressed[i]) {
                    stateLabels[i].setText("PRESSED");
                    stateLabels[i].setTextColor(0xFF4CAF50);
                } else {
                    stateLabels[i].setText("RELEASED");
                    stateLabels[i].setTextColor(0xFF888888);
                }
            }
            return true;
        });
    }

    // --- Blacklist dialog ---

    private void showBlacklistDialog(String prop, int titleRes) {
        Context context = getContext();
        if (context == null) return;

        List<Integer> btnCodes = new ArrayList<>(BTN_NAMES.keySet());
        Collections.sort(btnCodes);
        String[] labels = new String[btnCodes.size()];
        for (int i = 0; i < btnCodes.size(); i++) {
            int code = btnCodes.get(i);
            labels[i] = BTN_NAMES.get(code) + " (0x" + Integer.toHexString(code) + ")";
        }

        Set<Integer> currentSet = new HashSet<>();
        String currentVal = SystemProperties.get(prop, "");
        if (!currentVal.isEmpty()) {
            for (String tok : currentVal.split(",")) {
                tok = tok.trim();
                if (!tok.isEmpty()) {
                    try {
                        currentSet.add((int) Long.parseLong(
                                tok.startsWith("0x") ? tok.substring(2) : tok, 16));
                    } catch (NumberFormatException e) {
                        // skip
                    }
                }
            }
        }

        boolean[] checked = new boolean[btnCodes.size()];
        for (int i = 0; i < btnCodes.size(); i++) {
            checked[i] = currentSet.contains(btnCodes.get(i));
        }

        new AlertDialog.Builder(context)
                .setTitle(titleRes)
                .setMultiChoiceItems(labels, checked, (dialog, which, isChecked) -> {
                    checked[which] = isChecked;
                })
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < btnCodes.size(); i++) {
                        if (checked[i]) {
                            if (sb.length() > 0) sb.append(",");
                            sb.append("0x").append(Integer.toHexString(btnCodes.get(i)));
                        }
                    }
                    SystemProperties.set(prop, sb.toString());
                    bumpConfigVersion();
                    refreshRemapSummaries();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // --- Combo map dialog ---

    private void showComboMapDialog() {
        Context context = getContext();
        if (context == null) return;

        String current = SystemProperties.get(PROP_COMBO_MAP, "");
        List<String> items = new ArrayList<>();

        String[] entries = current.isEmpty() ? new String[0] : current.split(",");
        for (String entry : entries) {
            int plus = entry.indexOf('+');
            int eq = entry.indexOf('=');
            if (plus < 0 || eq < 0) continue;
            try {
                int btn1 = Integer.parseInt(entry.substring(0, plus));
                int btn2 = Integer.parseInt(entry.substring(plus + 1, eq));
                int emit = Integer.parseInt(entry.substring(eq + 1));
                String n1 = BTN_NAMES.getOrDefault(btn1, "0x" + Integer.toHexString(btn1));
                String n2 = BTN_NAMES.getOrDefault(btn2, "0x" + Integer.toHexString(btn2));
                String ne = BTN_NAMES.getOrDefault(emit, "0x" + Integer.toHexString(emit));
                items.add(n1 + " + " + n2 + " -> " + ne);
            } catch (NumberFormatException e) {
                items.add(entry);
            }
        }

        int currentCount = items.size();
        items.add(getString(R.string.gamepad_combo_map_add));
        if (currentCount > 0) {
            items.add(getString(R.string.gamepad_combo_map_clear_all));
        }

        new AlertDialog.Builder(context)
                .setTitle(R.string.gamepad_combo_map_title)
                .setItems(items.toArray(new String[0]), (d, which) -> {
                    if (which < currentCount) {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < entries.length; i++) {
                            if (i == which) continue;
                            if (sb.length() > 0) sb.append(",");
                            sb.append(entries[i]);
                        }
                        SystemProperties.set(PROP_COMBO_MAP, sb.toString());
                        bumpConfigVersion();
                        refreshRemapSummaries();
                    } else if (which == currentCount) {
                        showAddComboDialog();
                    } else {
                        SystemProperties.set(PROP_COMBO_MAP, "");
                        bumpConfigVersion();
                        refreshRemapSummaries();
                        Toast.makeText(context, R.string.gamepad_combo_map_cleared,
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showAddComboDialog() {
        Context context = getContext();
        if (context == null) return;

        List<Integer> btnCodes = new ArrayList<>(BTN_NAMES.keySet());
        Collections.sort(btnCodes);
        String[] labels = new String[btnCodes.size()];
        for (int i = 0; i < btnCodes.size(); i++) {
            labels[i] = BTN_NAMES.get(btnCodes.get(i));
        }

        new AlertDialog.Builder(context)
                .setTitle(R.string.gamepad_combo_map_btn1)
                .setItems(labels, (d, which1) -> {
                    int btn1 = btnCodes.get(which1);
                    new AlertDialog.Builder(context)
                            .setTitle(R.string.gamepad_combo_map_btn2)
                            .setItems(labels, (d2, which2) -> {
                                int btn2 = btnCodes.get(which2);
                                new AlertDialog.Builder(context)
                                        .setTitle(R.string.gamepad_combo_map_target)
                                        .setItems(labels, (d3, which3) -> {
                                            int emit = btnCodes.get(which3);
                                            String current = SystemProperties.get(
                                                    PROP_COMBO_MAP, "");
                                            String rule = btn1 + "+" + btn2 + "=" + emit;
                                            String newVal = current.isEmpty()
                                                    ? rule : current + "," + rule;
                                            SystemProperties.set(PROP_COMBO_MAP, newVal);
                                            bumpConfigVersion();
                                            refreshRemapSummaries();
                                            Toast.makeText(context,
                                                    R.string.gamepad_combo_map_saved,
                                                    Toast.LENGTH_SHORT).show();
                                        })
                                        .setNegativeButton(android.R.string.cancel, null)
                                        .show();
                            })
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // --- FF device dialog ---

    private void showFFDeviceDialog() {
        Context context = getContext();
        if (context == null) return;

        String current = SystemProperties.get(PROP_FF_DEVICE, "");

        List<String> values = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        values.add("");
        labels.add("None (use vibration bridge)");
        for (int i = 0; i < 20; i++) {
            String sysPath = "/sys/class/input/event" + i + "/device/name";
            File nameFile = new File(sysPath);
            if (!nameFile.exists()) continue;
            try {
                BufferedReader br = new BufferedReader(new FileReader(nameFile));
                String devName = br.readLine();
                br.close();
                if (devName != null && !devName.isEmpty()) {
                    devName = devName.trim();
                    values.add(devName);
                    labels.add(devName + " (event" + i + ")");
                }
            } catch (IOException ignored) {}
        }

        int selected = values.indexOf(current);
        if (selected < 0) selected = 0;

        new AlertDialog.Builder(context)
                .setTitle(R.string.gamepad_ff_device_dialog_title)
                .setSingleChoiceItems(
                        labels.toArray(new String[0]), selected,
                        (dialog, which) -> {
                            String chosen = values.get(which);
                            SystemProperties.set(PROP_FF_DEVICE, chosen);
                            updateFFDeviceSummary();
                            bumpConfigVersion();
                            dialog.dismiss();
                        })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // --- Vibration test dialog ---

    private void showVibrationTestDialog() {
        Context context = getContext();
        if (context == null) return;

        String[] items = {
            "Short pulse (200ms)",
            "Medium pulse (500ms)",
            "Long pulse (1000ms)",
            "Strong rumble (2000ms)",
            "Pulsing pattern",
            "Ramp up"
        };

        new AlertDialog.Builder(context)
                .setTitle(R.string.gamepad_test_vibration_title)
                .setItems(items, (d, which) -> {
                    Vibrator v = findGamepadVibrator();
                    if (v == null) {
                        Toast.makeText(context, "No vibration device found",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    switch (which) {
                        case 0:
                            v.vibrate(VibrationEffect.createOneShot(200, 180));
                            break;
                        case 1:
                            v.vibrate(VibrationEffect.createOneShot(500, 200));
                            break;
                        case 2:
                            v.vibrate(VibrationEffect.createOneShot(1000, 220));
                            break;
                        case 3:
                            v.vibrate(VibrationEffect.createOneShot(2000, 255));
                            break;
                        case 4:
                            v.vibrate(VibrationEffect.createWaveform(
                                    new long[]{0, 150, 100, 150, 100, 150, 100, 150},
                                    new int[]{0, 200, 0, 200, 0, 200, 0, 200}, -1));
                            break;
                        case 5:
                            v.vibrate(VibrationEffect.createWaveform(
                                    new long[]{0, 300, 0, 300, 0, 300, 0, 400},
                                    new int[]{0, 60, 0, 120, 0, 180, 0, 255}, -1));
                            break;
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private Vibrator findGamepadVibrator() {
        InputManager im = getContext().getSystemService(InputManager.class);
        if (im != null) {
            Map<String, String> kernelNameMap = buildKernelNameMap();
            String gammaPadName = getGammaPadVirtualName(kernelNameMap);

            for (int id : im.getInputDeviceIds()) {
                InputDevice dev = im.getInputDevice(id);
                if (dev == null) continue;
                String name = dev.getName();
                if ((gammaPadName != null && gammaPadName.equals(name))
                        || name.contains("GammaOS Virtual Gamepad")) {
                    Vibrator v = dev.getVibratorManager().getDefaultVibrator();
                    if (v != null && v.hasVibrator()) return v;
                }
            }
        }
        Vibrator v = getContext().getSystemService(Vibrator.class);
        if (v != null && v.hasVibrator()) return v;
        return null;
    }

    // --- Calibration ---

    private void showCalibrationDialog() {
        Context context = getContext();
        if (context == null) return;

        // D-pad friendly calibration: show live axis values in a dialog.
        // User moves sticks and presses buttons; we capture min/max/center.

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad);

        TextView instructions = new TextView(context);
        instructions.setText(getString(R.string.gamepad_calibration_step_center));
        instructions.setTextSize(16);
        instructions.setTextColor(0xFFFFFFFF);
        root.addView(instructions);

        // Live axis readout
        String[] axisLabels = {"LX", "LY", "RX", "RY", "LT", "RT"};
        int[] absCodesList = {0, 1, 3, 4, 2, 5};
        TextView[] axisValues = new TextView[axisLabels.length];

        for (int i = 0; i < axisLabels.length; i++) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);

            TextView label = new TextView(context);
            label.setText(axisLabels[i] + ": ");
            label.setTextSize(14);
            label.setTextColor(0xFFCCCCCC);
            label.setMinWidth(dp(60));
            row.addView(label);

            axisValues[i] = new TextView(context);
            axisValues[i].setText("0.000");
            axisValues[i].setTextSize(14);
            axisValues[i].setTextColor(0xFF4CAF50);
            row.addView(axisValues[i]);

            root.addView(row);
        }

        // Track min/max/center per axis
        final float[][] minMax = new float[absCodesList.length][3]; // [center, min, max]
        final boolean[] centerCaptured = {false};
        for (int i = 0; i < absCodesList.length; i++) {
            minMax[i][0] = 0f; // center
            minMax[i][1] = Float.MAX_VALUE; // min
            minMax[i][2] = Float.MIN_VALUE; // max
        }

        final int[] step = {0}; // 0=center, 1=range, 2=done

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.gamepad_calibration_title)
                .setView(root)
                .setPositiveButton(getString(R.string.gamepad_calibration_continue), null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        dialog.show();

        // Override positive button to step through calibration phases
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (step[0] == 0) {
                // Capture center values
                centerCaptured[0] = true;
                step[0] = 1;
                instructions.setText("Move all sticks to extremes, then press Continue to save");
            } else if (step[0] == 1) {
                // Save calibration
                for (int i = 0; i < absCodesList.length; i++) {
                    int absCode = absCodesList[i];
                    float center = minMax[i][0];
                    float min = minMax[i][1];
                    float max = minMax[i][2];
                    if (min == Float.MAX_VALUE) min = -1f;
                    if (max == Float.MIN_VALUE) max = 1f;
                    // Format: center:min:max:deadzone:sensitivity
                    // Use 5% deadzone and 100% sensitivity as defaults
                    String calVal = String.format("%.4f:%.4f:%.4f:5:100",
                            center, min, max);
                    SystemProperties.set("persist.gammaos.gamepad.cal_axis" + absCode,
                            calVal);
                }
                bumpConfigVersion();
                Toast.makeText(context, R.string.gamepad_calibration_complete,
                        Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            }
        });

        // Listen for joystick motion
        dialog.getWindow().getDecorView().setOnGenericMotionListener((view, event) -> {
            if ((event.getSource() & InputDevice.SOURCE_JOYSTICK) == 0) return false;

            for (int i = 0; i < absCodesList.length; i++) {
                int androidAxis = ABS_TO_ANDROID_AXIS.getOrDefault(
                        absCodesList[i], absCodesList[i]);
                float raw = event.getAxisValue(androidAxis);

                axisValues[i].setText(String.format("%.3f", raw));

                if (step[0] == 0 && !centerCaptured[0]) {
                    // Continuously update center before capture
                    minMax[i][0] = raw;
                } else if (step[0] == 1) {
                    // Track min/max
                    if (raw < minMax[i][1]) minMax[i][1] = raw;
                    if (raw > minMax[i][2]) minMax[i][2] = raw;
                }
            }
            return true;
        });
    }

    private void clearCalibration() {
        int[] axes = { 0, 1, 2, 3, 4, 5, 9, 10 };
        for (int axis : axes) {
            SystemProperties.set("persist.gammaos.gamepad.cal_axis" + axis, "");
        }
        bumpConfigVersion();
        Toast.makeText(getContext(), R.string.gamepad_calibration_cleared,
                Toast.LENGTH_SHORT).show();
    }

    // --- Mouse mode dialogs ---

    private void showMouseComboDialog() {
        Context context = getContext();
        if (context == null) return;

        List<Integer> btnCodes = new ArrayList<>(BTN_NAMES.keySet());
        Collections.sort(btnCodes);
        String[] labels = new String[btnCodes.size()];
        for (int i = 0; i < btnCodes.size(); i++) {
            labels[i] = BTN_NAMES.get(btnCodes.get(i));
        }

        new AlertDialog.Builder(context)
                .setTitle(R.string.gamepad_mouse_combo_btn1)
                .setItems(labels, (d, which) -> {
                    int btn1 = btnCodes.get(which);
                    new AlertDialog.Builder(context)
                            .setTitle(R.string.gamepad_mouse_combo_btn2)
                            .setItems(labels, (d2, which2) -> {
                                int btn2 = btnCodes.get(which2);
                                SystemProperties.set(PROP_MOUSE_COMBO1,
                                        String.valueOf(btn1));
                                SystemProperties.set(PROP_MOUSE_COMBO2,
                                        String.valueOf(btn2));
                                bumpConfigVersion();
                                Preference comboPref = findPreference(KEY_MOUSE_COMBO);
                                if (comboPref != null) updateMouseComboSummary(comboPref);
                            })
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showMouseButtonsDialog() {
        Context context = getContext();
        if (context == null) return;

        String[] roles = {
            getString(R.string.gamepad_mouse_btn_click),
            getString(R.string.gamepad_mouse_btn_back),
            getString(R.string.gamepad_mouse_btn_rclick),
            getString(R.string.gamepad_mouse_btn_boost),
        };
        String[] props = {
            PROP_MOUSE_BTN_CLICK, PROP_MOUSE_BTN_BACK,
            PROP_MOUSE_BTN_RCLICK, PROP_MOUSE_BTN_BOOST
        };
        int[] defaults = { 0x130, 0x131, 0x134, 0x133 };

        String[] items = new String[roles.length];
        for (int i = 0; i < roles.length; i++) {
            int code = SystemProperties.getInt(props[i], defaults[i]);
            String name = BTN_NAMES.getOrDefault(code, "0x" + Integer.toHexString(code));
            items[i] = roles[i] + ": " + name;
        }

        new AlertDialog.Builder(context)
                .setTitle(R.string.gamepad_mouse_buttons_title)
                .setItems(items, (d, which) -> {
                    showMouseButtonPicker(props[which], defaults[which], roles[which]);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showMouseButtonPicker(String prop, int defaultCode, String roleName) {
        Context context = getContext();
        if (context == null) return;

        List<Integer> btnCodes = new ArrayList<>(BTN_NAMES.keySet());
        Collections.sort(btnCodes);
        String[] labels = new String[btnCodes.size()];
        for (int i = 0; i < btnCodes.size(); i++) {
            labels[i] = BTN_NAMES.get(btnCodes.get(i));
        }

        new AlertDialog.Builder(context)
                .setTitle(roleName)
                .setItems(labels, (d, which) -> {
                    int code = btnCodes.get(which);
                    SystemProperties.set(prop, String.valueOf(code));
                    bumpConfigVersion();
                    Preference btnPref = findPreference(KEY_MOUSE_BUTTONS);
                    if (btnPref != null) updateMouseButtonsSummary(btnPref);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // --- Per-app profiles ---

    private void populatePerAppProfiles() {
        PreferenceCategory perappCat = findPreference(KEY_PERAPP_CATEGORY);
        if (perappCat == null) return;

        for (int i = perappCat.getPreferenceCount() - 1; i >= 0; i--) {
            Preference p = perappCat.getPreference(i);
            if (p.getKey() != null && p.getKey().startsWith("gamepad_perapp_profile_")) {
                perappCat.removePreference(p);
            }
        }

        int count = SystemProperties.getInt(PROP_PA_COUNT, 0);
        PackageManager pm = getContext().getPackageManager();

        for (int i = 0; i < count && i < 20; i++) {
            String prefix = "persist.gammaos.gamepad.pa" + i;
            String pkg = SystemProperties.get(prefix + "_pkg", "");
            if (pkg.isEmpty()) continue;

            String btnRemap = SystemProperties.get(prefix + "_btn", "");
            String comboMap = SystemProperties.get(prefix + "_combo", "");

            String appLabel = pkg;
            try {
                ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                appLabel = pm.getApplicationLabel(ai).toString();
            } catch (PackageManager.NameNotFoundException e) {
                // fallback
            }

            StringBuilder summary = new StringBuilder();
            if (!btnRemap.isEmpty()) {
                int remapCount = btnRemap.split(",").length;
                summary.append(remapCount).append(" remap(s)");
            }
            if (!comboMap.isEmpty()) {
                int comboCount = comboMap.split(",").length;
                if (summary.length() > 0) summary.append(", ");
                summary.append(comboCount).append(" combo(s)");
            }
            if (summary.length() == 0) {
                summary.append(getString(R.string.gamepad_perapp_no_remaps));
            }

            Preference pref = new Preference(getContext());
            pref.setKey("gamepad_perapp_profile_" + i);
            pref.setTitle(appLabel);
            pref.setSummary(summary.toString());
            pref.setOnPreferenceClickListener(this);

            Preference addPref = findPreference(KEY_PERAPP_ADD);
            int addIdx = addPref != null ? perappCat.getPreferenceCount() - 1 : -1;
            if (addIdx >= 0) {
                pref.setOrder(addIdx);
            }
            perappCat.addPreference(pref);
        }
    }

    private void showPerAppPickerDialog() {
        Context context = getContext();
        if (context == null) return;

        PackageManager pm = context.getPackageManager();

        Intent launchIntent = new Intent(Intent.ACTION_MAIN);
        launchIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> resolveList = pm.queryIntentActivities(launchIntent, 0);

        List<ApplicationInfo> apps = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ResolveInfo ri : resolveList) {
            String pkg = ri.activityInfo.packageName;
            if (!seen.add(pkg)) continue;
            try {
                apps.add(pm.getApplicationInfo(pkg, 0));
            } catch (PackageManager.NameNotFoundException e) {
                // skip
            }
        }

        Collections.sort(apps, (a, b) -> {
            String la = pm.getApplicationLabel(a).toString();
            String lb = pm.getApplicationLabel(b).toString();
            return la.compareToIgnoreCase(lb);
        });

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, 0);

        EditText searchBox = new EditText(context);
        searchBox.setHint("Search apps...");
        searchBox.setSingleLine(true);
        searchBox.setInputType(InputType.TYPE_CLASS_TEXT);
        root.addView(searchBox);

        ListView listView = new ListView(context);
        root.addView(listView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        AppPickerAdapter adapter = new AppPickerAdapter(context, pm, apps);
        listView.setAdapter(adapter);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.gamepad_perapp_pick_app)
                .setView(root)
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        listView.setOnItemClickListener((parent, view, position, id) -> {
            ApplicationInfo ai = adapter.getItem(position);
            if (ai == null) return;
            String pkg = ai.packageName;

            int count = SystemProperties.getInt(PROP_PA_COUNT, 0);
            for (int i = 0; i < count; i++) {
                String existing = SystemProperties.get(
                        "persist.gammaos.gamepad.pa" + i + "_pkg", "");
                if (pkg.equals(existing)) {
                    Toast.makeText(context, R.string.gamepad_perapp_exists,
                            Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            String prefix = "persist.gammaos.gamepad.pa" + count;
            SystemProperties.set(prefix + "_pkg", pkg);
            SystemProperties.set(prefix + "_btn", "");
            SystemProperties.set(prefix + "_combo", "");
            SystemProperties.set(PROP_PA_COUNT, String.valueOf(count + 1));
            bumpConfigVersion();
            populatePerAppProfiles();
            dialog.dismiss();

            showPerAppEditDialog(count);
        });

        searchBox.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                adapter.getFilter().filter(s);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        dialog.show();
    }

    private static class AppPickerAdapter extends BaseAdapter implements Filterable {
        private final Context mContext;
        private final PackageManager mPm;
        private final List<ApplicationInfo> mAllApps;
        private List<ApplicationInfo> mFiltered;
        private final AppFilter mFilter;

        AppPickerAdapter(Context context, PackageManager pm, List<ApplicationInfo> apps) {
            mContext = context;
            mPm = pm;
            mAllApps = apps;
            mFiltered = new ArrayList<>(apps);
            mFilter = new AppFilter();
        }

        @Override public int getCount() { return mFiltered.size(); }
        @Override public ApplicationInfo getItem(int pos) { return mFiltered.get(pos); }
        @Override public long getItemId(int pos) { return pos; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row;
            if (convertView instanceof LinearLayout) {
                row = (LinearLayout) convertView;
            } else {
                row = new LinearLayout(mContext);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                int dp8 = (int) (8 * mContext.getResources().getDisplayMetrics().density);
                row.setPadding(dp8, dp8, dp8, dp8);

                ImageView icon = new ImageView(mContext);
                int iconSize = (int) (40 * mContext.getResources().getDisplayMetrics().density);
                LinearLayout.LayoutParams iconLp =
                        new LinearLayout.LayoutParams(iconSize, iconSize);
                iconLp.setMarginEnd(dp8 * 2);
                icon.setLayoutParams(iconLp);
                icon.setTag("icon");
                row.addView(icon);

                LinearLayout textCol = new LinearLayout(mContext);
                textCol.setOrientation(LinearLayout.VERTICAL);
                TextView title = new TextView(mContext);
                title.setTextSize(16);
                title.setTag("title");
                textCol.addView(title);
                TextView sub = new TextView(mContext);
                sub.setTextSize(12);
                sub.setAlpha(0.7f);
                sub.setTag("sub");
                textCol.addView(sub);
                row.addView(textCol);
            }

            ApplicationInfo ai = mFiltered.get(position);
            ((ImageView) row.findViewWithTag("icon")).setImageDrawable(ai.loadIcon(mPm));
            ((TextView) row.findViewWithTag("title")).setText(mPm.getApplicationLabel(ai));
            ((TextView) row.findViewWithTag("sub")).setText(ai.packageName);
            return row;
        }

        @Override public Filter getFilter() { return mFilter; }

        private class AppFilter extends Filter {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                FilterResults results = new FilterResults();
                if (constraint == null || constraint.length() == 0) {
                    results.values = new ArrayList<>(mAllApps);
                    results.count = mAllApps.size();
                } else {
                    String query = constraint.toString().toLowerCase();
                    List<ApplicationInfo> filtered = new ArrayList<>();
                    for (ApplicationInfo ai : mAllApps) {
                        String label = mPm.getApplicationLabel(ai).toString().toLowerCase();
                        if (label.contains(query)
                                || ai.packageName.toLowerCase().contains(query)) {
                            filtered.add(ai);
                        }
                    }
                    results.values = filtered;
                    results.count = filtered.size();
                }
                return results;
            }

            @Override
            @SuppressWarnings("unchecked")
            protected void publishResults(CharSequence constraint, FilterResults results) {
                mFiltered = (List<ApplicationInfo>) results.values;
                notifyDataSetChanged();
            }
        }
    }

    private void showPerAppEditDialog(int profileIdx) {
        Context context = getContext();
        if (context == null) return;

        String prefix = "persist.gammaos.gamepad.pa" + profileIdx;
        String pkg = SystemProperties.get(prefix + "_pkg", "");
        if (pkg.isEmpty()) return;

        String btnRemap = SystemProperties.get(prefix + "_btn", "");
        String comboMap = SystemProperties.get(prefix + "_combo", "");

        PackageManager pm = context.getPackageManager();
        String appLabel = pkg;
        try {
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            appLabel = pm.getApplicationLabel(ai).toString();
        } catch (PackageManager.NameNotFoundException e) {
            // fallback
        }

        List<String> items = new ArrayList<>();

        if (!btnRemap.isEmpty()) {
            String[] pairs = btnRemap.split(",");
            for (String pair : pairs) {
                String[] parts = pair.split(":");
                if (parts.length == 2) {
                    try {
                        int from = Integer.parseInt(parts[0]);
                        int to = Integer.parseInt(parts[1]);
                        String fn = BTN_NAMES.getOrDefault(from,
                                "0x" + Integer.toHexString(from));
                        String tn = BTN_NAMES.getOrDefault(to,
                                "0x" + Integer.toHexString(to));
                        items.add("Remap: " + fn + " -> " + tn);
                    } catch (NumberFormatException e) {
                        items.add("Remap: " + pair);
                    }
                }
            }
        }

        if (!comboMap.isEmpty()) {
            String[] combos = comboMap.split(",");
            for (String entry : combos) {
                int plus = entry.indexOf('+');
                int eq = entry.indexOf('=');
                if (plus >= 0 && eq > plus) {
                    try {
                        int b1 = Integer.parseInt(entry.substring(0, plus));
                        int b2 = Integer.parseInt(entry.substring(plus + 1, eq));
                        int em = Integer.parseInt(entry.substring(eq + 1));
                        String n1 = BTN_NAMES.getOrDefault(b1,
                                "0x" + Integer.toHexString(b1));
                        String n2 = BTN_NAMES.getOrDefault(b2,
                                "0x" + Integer.toHexString(b2));
                        String ne = BTN_NAMES.getOrDefault(em,
                                "0x" + Integer.toHexString(em));
                        items.add("Combo: " + n1 + "+" + n2 + " -> " + ne);
                    } catch (NumberFormatException e) {
                        items.add("Combo: " + entry);
                    }
                }
            }
        }

        int existingCount = items.size();

        items.add(getString(R.string.gamepad_perapp_add_btn_remap));
        items.add(getString(R.string.gamepad_perapp_add_combo));
        if (!btnRemap.isEmpty()) {
            items.add(getString(R.string.gamepad_perapp_clear_btn));
        }
        if (!comboMap.isEmpty()) {
            items.add(getString(R.string.gamepad_perapp_clear_combo));
        }
        items.add(getString(R.string.gamepad_perapp_remove));

        new AlertDialog.Builder(context)
                .setTitle(appLabel)
                .setItems(items.toArray(new String[0]), (d, which) -> {
                    if (which < existingCount) {
                        removePerAppEntry(profileIdx, which, btnRemap, comboMap);
                    } else {
                        int actionIdx = which - existingCount;

                        if (actionIdx == 0) {
                            showPerAppAddRemapDialog(profileIdx);
                        } else if (actionIdx == 1) {
                            showPerAppAddComboDialog(profileIdx);
                        } else if (actionIdx == 2 && !btnRemap.isEmpty()) {
                            SystemProperties.set(prefix + "_btn", "");
                            bumpConfigVersion();
                            populatePerAppProfiles();
                        } else if ((actionIdx == 2 && btnRemap.isEmpty()
                                    && !comboMap.isEmpty())
                                || (actionIdx == 3 && !btnRemap.isEmpty()
                                    && !comboMap.isEmpty())) {
                            SystemProperties.set(prefix + "_combo", "");
                            bumpConfigVersion();
                            populatePerAppProfiles();
                        } else {
                            removePerAppProfile(profileIdx);
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void removePerAppEntry(int profileIdx, int entryIdx,
                                    String btnRemap, String comboMap) {
        String prefix = "persist.gammaos.gamepad.pa" + profileIdx;
        int btnCount = btnRemap.isEmpty() ? 0 : btnRemap.split(",").length;

        if (entryIdx < btnCount) {
            String[] parts = btnRemap.split(",");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < parts.length; i++) {
                if (i == entryIdx) continue;
                if (sb.length() > 0) sb.append(",");
                sb.append(parts[i]);
            }
            SystemProperties.set(prefix + "_btn", sb.toString());
        } else {
            int comboIdx = entryIdx - btnCount;
            String[] parts = comboMap.split(",");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < parts.length; i++) {
                if (i == comboIdx) continue;
                if (sb.length() > 0) sb.append(",");
                sb.append(parts[i]);
            }
            SystemProperties.set(prefix + "_combo", sb.toString());
        }
        bumpConfigVersion();
        populatePerAppProfiles();
    }

    private void removePerAppProfile(int profileIdx) {
        int count = SystemProperties.getInt(PROP_PA_COUNT, 0);
        if (profileIdx >= count) return;

        for (int i = profileIdx; i < count - 1; i++) {
            String srcPrefix = "persist.gammaos.gamepad.pa" + (i + 1);
            String dstPrefix = "persist.gammaos.gamepad.pa" + i;
            SystemProperties.set(dstPrefix + "_pkg",
                    SystemProperties.get(srcPrefix + "_pkg", ""));
            SystemProperties.set(dstPrefix + "_btn",
                    SystemProperties.get(srcPrefix + "_btn", ""));
            SystemProperties.set(dstPrefix + "_combo",
                    SystemProperties.get(srcPrefix + "_combo", ""));
        }

        String lastPrefix = "persist.gammaos.gamepad.pa" + (count - 1);
        SystemProperties.set(lastPrefix + "_pkg", "");
        SystemProperties.set(lastPrefix + "_btn", "");
        SystemProperties.set(lastPrefix + "_combo", "");

        SystemProperties.set(PROP_PA_COUNT, String.valueOf(count - 1));
        bumpConfigVersion();
        populatePerAppProfiles();

        Toast.makeText(getContext(), R.string.gamepad_perapp_removed,
                Toast.LENGTH_SHORT).show();
    }

    private void showPerAppAddRemapDialog(int profileIdx) {
        Context context = getContext();
        if (context == null) return;

        List<Integer> btnCodes = new ArrayList<>(BTN_NAMES.keySet());
        Collections.sort(btnCodes);
        String[] labels = new String[btnCodes.size()];
        for (int i = 0; i < btnCodes.size(); i++) {
            labels[i] = BTN_NAMES.get(btnCodes.get(i));
        }

        String prefix = "persist.gammaos.gamepad.pa" + profileIdx;

        new AlertDialog.Builder(context)
                .setTitle(R.string.gamepad_remap_dialog_title)
                .setItems(labels, (d, which) -> {
                    int fromCode = btnCodes.get(which);
                    new AlertDialog.Builder(context)
                            .setTitle(getString(R.string.gamepad_remap_choose_target,
                                    BTN_NAMES.getOrDefault(fromCode,
                                            "0x" + Integer.toHexString(fromCode))))
                            .setItems(labels, (d2, which2) -> {
                                int toCode = btnCodes.get(which2);
                                String current = SystemProperties.get(
                                        prefix + "_btn", "");
                                String rule = fromCode + ":" + toCode;
                                String newVal = current.isEmpty()
                                        ? rule : current + "," + rule;
                                SystemProperties.set(prefix + "_btn", newVal);
                                bumpConfigVersion();
                                populatePerAppProfiles();
                                Toast.makeText(context, R.string.gamepad_perapp_saved,
                                        Toast.LENGTH_SHORT).show();
                            })
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showPerAppAddComboDialog(int profileIdx) {
        Context context = getContext();
        if (context == null) return;

        List<Integer> btnCodes = new ArrayList<>(BTN_NAMES.keySet());
        Collections.sort(btnCodes);
        String[] labels = new String[btnCodes.size()];
        for (int i = 0; i < btnCodes.size(); i++) {
            labels[i] = BTN_NAMES.get(btnCodes.get(i));
        }

        String prefix = "persist.gammaos.gamepad.pa" + profileIdx;

        new AlertDialog.Builder(context)
                .setTitle(R.string.gamepad_combo_map_btn1)
                .setItems(labels, (d, w1) -> {
                    int btn1 = btnCodes.get(w1);
                    new AlertDialog.Builder(context)
                            .setTitle(R.string.gamepad_combo_map_btn2)
                            .setItems(labels, (d2, w2) -> {
                                int btn2 = btnCodes.get(w2);
                                new AlertDialog.Builder(context)
                                        .setTitle(R.string.gamepad_combo_map_target)
                                        .setItems(labels, (d3, w3) -> {
                                            int emit = btnCodes.get(w3);
                                            String current = SystemProperties.get(
                                                    prefix + "_combo", "");
                                            String rule = btn1 + "+" + btn2 + "=" + emit;
                                            String newVal = current.isEmpty()
                                                    ? rule : current + "," + rule;
                                            SystemProperties.set(
                                                    prefix + "_combo", newVal);
                                            bumpConfigVersion();
                                            populatePerAppProfiles();
                                            Toast.makeText(context,
                                                    R.string.gamepad_perapp_saved,
                                                    Toast.LENGTH_SHORT).show();
                                        })
                                        .setNegativeButton(android.R.string.cancel, null)
                                        .show();
                            })
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    // --- Gamepad test dialog ---

    private void showGamepadTestDialog() {
        Context context = getContext();
        if (context == null) return;

        ScrollView scrollView = new ScrollView(context);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad);
        scrollView.addView(root);

        // Buttons section
        TextView btnHeader = new TextView(context);
        btnHeader.setText(getString(R.string.gamepad_test_buttons_header));
        btnHeader.setTextSize(16);
        btnHeader.setTextColor(0xFFFFFFFF);
        root.addView(btnHeader);

        Map<Integer, TextView> btnLabels = new HashMap<>();
        int[][] btnDefs = {
            {0x130, 0}, {0x131, 0}, {0x133, 0}, {0x134, 0},
            {0x136, 0}, {0x137, 0}, {0x138, 0}, {0x139, 0},
            {0x13a, 0}, {0x13b, 0}, {0x13c, 0}, {0x13d, 0}, {0x13e, 0}
        };
        for (int[] def : btnDefs) {
            int code = def[0];
            String name = BTN_NAMES.getOrDefault(code, "0x" + Integer.toHexString(code));
            TextView tv = new TextView(context);
            tv.setText(name + ": " + getString(R.string.gamepad_test_released));
            tv.setTextSize(14);
            tv.setTextColor(0xFF888888);
            root.addView(tv);
            btnLabels.put(code, tv);
        }

        // Sticks section
        TextView stickHeader = new TextView(context);
        stickHeader.setText(getString(R.string.gamepad_test_sticks_header));
        stickHeader.setTextSize(16);
        stickHeader.setTextColor(0xFFFFFFFF);
        LinearLayout.LayoutParams shParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        shParams.topMargin = pad;
        root.addView(stickHeader, shParams);

        TextView leftStickLabel = new TextView(context);
        leftStickLabel.setText(getString(R.string.gamepad_test_left_stick) + ": 0.000, 0.000");
        leftStickLabel.setTextSize(14);
        leftStickLabel.setTextColor(0xFF4CAF50);
        root.addView(leftStickLabel);

        TextView rightStickLabel = new TextView(context);
        rightStickLabel.setText(getString(R.string.gamepad_test_right_stick) + ": 0.000, 0.000");
        rightStickLabel.setTextSize(14);
        rightStickLabel.setTextColor(0xFF4CAF50);
        root.addView(rightStickLabel);

        // Triggers section
        TextView trigHeader = new TextView(context);
        trigHeader.setText(getString(R.string.gamepad_test_triggers_header));
        trigHeader.setTextSize(16);
        trigHeader.setTextColor(0xFFFFFFFF);
        LinearLayout.LayoutParams thParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        thParams.topMargin = pad;
        root.addView(trigHeader, thParams);

        TextView ltLabel = new TextView(context);
        ltLabel.setText(getString(R.string.gamepad_test_left_trigger) + ": 0.000");
        ltLabel.setTextSize(14);
        ltLabel.setTextColor(0xFF4CAF50);
        root.addView(ltLabel);

        TextView rtLabel = new TextView(context);
        rtLabel.setText(getString(R.string.gamepad_test_right_trigger) + ": 0.000");
        rtLabel.setTextSize(14);
        rtLabel.setTextColor(0xFF4CAF50);
        root.addView(rtLabel);

        // D-pad
        TextView dpadLabel = new TextView(context);
        dpadLabel.setText("D-Pad: 0.000, 0.000");
        dpadLabel.setTextSize(14);
        dpadLabel.setTextColor(0xFF4CAF50);
        root.addView(dpadLabel);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.gamepad_test_title)
                .setView(scrollView)
                .setPositiveButton(android.R.string.ok, null)
                .create();

        dialog.show();

        // Button events
        dialog.setOnKeyListener((dlg, keyCode, event) -> {
            // Map Android keycode to Linux button code
            int linuxCode = androidKeyToLinuxBtn(keyCode);
            if (linuxCode != 0 && btnLabels.containsKey(linuxCode)) {
                TextView tv = btnLabels.get(linuxCode);
                String name = BTN_NAMES.getOrDefault(linuxCode,
                        "0x" + Integer.toHexString(linuxCode));
                if (event.getAction() == android.view.KeyEvent.ACTION_DOWN) {
                    tv.setText(name + ": " + getString(R.string.gamepad_test_pressed));
                    tv.setTextColor(0xFF4CAF50);
                } else if (event.getAction() == android.view.KeyEvent.ACTION_UP) {
                    tv.setText(name + ": " + getString(R.string.gamepad_test_released));
                    tv.setTextColor(0xFF888888);
                }
                return true;
            }
            return false;
        });

        // Axis events
        dialog.getWindow().getDecorView().setOnGenericMotionListener((v, event) -> {
            if ((event.getSource() & InputDevice.SOURCE_JOYSTICK) == 0) return false;

            float lx = event.getAxisValue(MotionEvent.AXIS_X);
            float ly = event.getAxisValue(MotionEvent.AXIS_Y);
            leftStickLabel.setText(getString(R.string.gamepad_test_left_stick)
                    + ": " + String.format("%.3f, %.3f", lx, ly));

            float rx = event.getAxisValue(MotionEvent.AXIS_RX);
            // Fall back to AXIS_Z for RX if RX is zero (some controllers)
            if (rx == 0f) rx = event.getAxisValue(MotionEvent.AXIS_Z);
            float ry = event.getAxisValue(MotionEvent.AXIS_RY);
            // Fall back to AXIS_RZ for RY if RY is zero
            if (ry == 0f) ry = event.getAxisValue(MotionEvent.AXIS_RZ);
            rightStickLabel.setText(getString(R.string.gamepad_test_right_stick)
                    + ": " + String.format("%.3f, %.3f", rx, ry));

            float lt = event.getAxisValue(MotionEvent.AXIS_LTRIGGER);
            if (lt == 0f) lt = event.getAxisValue(MotionEvent.AXIS_BRAKE);
            ltLabel.setText(getString(R.string.gamepad_test_left_trigger)
                    + ": " + String.format("%.3f", lt));

            float rt = event.getAxisValue(MotionEvent.AXIS_RTRIGGER);
            if (rt == 0f) rt = event.getAxisValue(MotionEvent.AXIS_GAS);
            rtLabel.setText(getString(R.string.gamepad_test_right_trigger)
                    + ": " + String.format("%.3f", rt));

            float hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X);
            float hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y);
            dpadLabel.setText("D-Pad: " + String.format("%.3f, %.3f", hatX, hatY));

            return true;
        });
    }

    private int androidKeyToLinuxBtn(int keyCode) {
        switch (keyCode) {
            case android.view.KeyEvent.KEYCODE_BUTTON_A: return 0x130;
            case android.view.KeyEvent.KEYCODE_BUTTON_B: return 0x131;
            case android.view.KeyEvent.KEYCODE_BUTTON_X: return 0x133;
            case android.view.KeyEvent.KEYCODE_BUTTON_Y: return 0x134;
            case android.view.KeyEvent.KEYCODE_BUTTON_L1: return 0x136;
            case android.view.KeyEvent.KEYCODE_BUTTON_R1: return 0x137;
            case android.view.KeyEvent.KEYCODE_BUTTON_L2: return 0x138;
            case android.view.KeyEvent.KEYCODE_BUTTON_R2: return 0x139;
            case android.view.KeyEvent.KEYCODE_BUTTON_SELECT: return 0x13a;
            case android.view.KeyEvent.KEYCODE_BUTTON_START: return 0x13b;
            case android.view.KeyEvent.KEYCODE_BUTTON_MODE: return 0x13c;
            case android.view.KeyEvent.KEYCODE_BUTTON_THUMBL: return 0x13d;
            case android.view.KeyEvent.KEYCODE_BUTTON_THUMBR: return 0x13e;
            default: return 0;
        }
    }

    // --- Utility ---

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    private int parseIntOrHex(String s) {
        s = s.trim();
        if (s.startsWith("0x") || s.startsWith("0X")) {
            return (int) Long.parseLong(s.substring(2), 16);
        }
        return Integer.parseInt(s);
    }

    private void bumpConfigVersion() {
        SystemProperties.set("persist.gammaos.gamepad.full_reload", "1");
        int version = SystemProperties.getInt(PROP_CONFIG_VERSION, 0);
        SystemProperties.set(PROP_CONFIG_VERSION, String.valueOf(version + 1));
    }
}
