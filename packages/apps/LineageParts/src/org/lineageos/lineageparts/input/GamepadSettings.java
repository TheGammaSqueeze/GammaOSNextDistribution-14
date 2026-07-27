/*
 * GammaOS Gamepad Settings
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.lineageparts.input;

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
import android.app.AlertDialog;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.NumberPicker;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.RecyclerView;

import androidx.preference.CheckBoxPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.SeekBarPreference;
import androidx.preference.SwitchPreferenceCompat;

import org.lineageos.lineageparts.R;
import org.lineageos.lineageparts.SettingsPreferenceFragment;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class GamepadSettings extends SettingsPreferenceFragment
        implements Preference.OnPreferenceChangeListener,
                   Preference.OnPreferenceClickListener,
                   InputManager.InputDeviceListener {

    private static final String TAG = "GamepadSettings";

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

    // Combo mapping keys
    private static final String KEY_COMBO_MAP = "gamepad_combo_map";

    // Per-app profile keys
    private static final String KEY_PERAPP_CATEGORY = "gamepad_perapp_category";
    private static final String KEY_PERAPP_ADD = "gamepad_perapp_add";

    // Mouse mode keys
    private static final String KEY_MOUSE_ENABLE = "gamepad_mouse_enable";
    private static final String KEY_MOUSE_COMBO = "gamepad_mouse_combo";
    private static final String KEY_MOUSE_HOLD_TIME = "gamepad_mouse_hold_time";
    private static final String KEY_MOUSE_BUTTONS = "gamepad_mouse_buttons";
    private static final String KEY_MOUSE_STICK_SPEED = "gamepad_mouse_stick_speed";
    private static final String KEY_MOUSE_DPAD_SPEED = "gamepad_mouse_dpad_speed";
    private static final String KEY_MOUSE_BOOST = "gamepad_mouse_boost";
    private static final String KEY_MOUSE_SCROLL_SPEED = "gamepad_mouse_scroll_speed";

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
    private static final String KEY_CUSTOM_ACTIONS = "gamepad_custom_actions";
    private static final String PROP_ACT_COUNT = "persist.gammaos.gamepad.act_count";

    // Gamepad button and common key/media targets for the custom-action editor.
    private static final java.util.LinkedHashMap<Integer, String> ACT_BTN_NAMES =
            new java.util.LinkedHashMap<>();
    private static final java.util.LinkedHashMap<Integer, String> ACT_KEY_NAMES =
            new java.util.LinkedHashMap<>();
    static {
        ACT_BTN_NAMES.put(0x130, "A"); ACT_BTN_NAMES.put(0x131, "B");
        ACT_BTN_NAMES.put(0x133, "X"); ACT_BTN_NAMES.put(0x134, "Y");
        ACT_BTN_NAMES.put(0x136, "LB"); ACT_BTN_NAMES.put(0x137, "RB");
        ACT_BTN_NAMES.put(0x138, "L2"); ACT_BTN_NAMES.put(0x139, "R2");
        ACT_BTN_NAMES.put(0x13a, "Select"); ACT_BTN_NAMES.put(0x13b, "Start");
        ACT_BTN_NAMES.put(0x13c, "Guide"); ACT_BTN_NAMES.put(0x13d, "L3");
        ACT_BTN_NAMES.put(0x13e, "R3");
        ACT_BTN_NAMES.put(0x220, "D-Pad Up"); ACT_BTN_NAMES.put(0x221, "D-Pad Down");
        ACT_BTN_NAMES.put(0x222, "D-Pad Left"); ACT_BTN_NAMES.put(0x223, "D-Pad Right");
        ACT_KEY_NAMES.put(158, "Back"); ACT_KEY_NAMES.put(172, "Home");
        ACT_KEY_NAMES.put(139, "Menu"); ACT_KEY_NAMES.put(217, "Search"); ACT_KEY_NAMES.put(171, "Settings");
        ACT_KEY_NAMES.put(116, "Power"); ACT_KEY_NAMES.put(142, "Sleep"); ACT_KEY_NAMES.put(143, "Wake");
        ACT_KEY_NAMES.put(212, "Camera"); ACT_KEY_NAMES.put(582, "Voice Command"); ACT_KEY_NAMES.put(583, "Assistant");
        ACT_KEY_NAMES.put(226, "Media Key");
        ACT_KEY_NAMES.put(115, "Volume Up"); ACT_KEY_NAMES.put(114, "Volume Down"); ACT_KEY_NAMES.put(113, "Mute");
        ACT_KEY_NAMES.put(225, "Brightness Up"); ACT_KEY_NAMES.put(224, "Brightness Down");
        ACT_KEY_NAMES.put(164, "Play / Pause"); ACT_KEY_NAMES.put(207, "Play"); ACT_KEY_NAMES.put(119, "Pause");
        ACT_KEY_NAMES.put(128, "Stop"); ACT_KEY_NAMES.put(163, "Next Track"); ACT_KEY_NAMES.put(165, "Previous Track");
        ACT_KEY_NAMES.put(168, "Rewind"); ACT_KEY_NAMES.put(208, "Fast Forward"); ACT_KEY_NAMES.put(167, "Record");
        ACT_KEY_NAMES.put(161, "Eject");
        ACT_KEY_NAMES.put(238, "Wi-Fi"); ACT_KEY_NAMES.put(237, "Bluetooth"); ACT_KEY_NAMES.put(247, "Airplane Mode");
        ACT_KEY_NAMES.put(28, "Enter"); ACT_KEY_NAMES.put(1, "Escape"); ACT_KEY_NAMES.put(15, "Tab");
        ACT_KEY_NAMES.put(57, "Space"); ACT_KEY_NAMES.put(14, "Backspace"); ACT_KEY_NAMES.put(111, "Delete");
        ACT_KEY_NAMES.put(103, "Up"); ACT_KEY_NAMES.put(108, "Down"); ACT_KEY_NAMES.put(105, "Left"); ACT_KEY_NAMES.put(106, "Right");
        ACT_KEY_NAMES.put(104, "Page Up"); ACT_KEY_NAMES.put(109, "Page Down"); ACT_KEY_NAMES.put(102, "Home Key");
        ACT_KEY_NAMES.put(107, "End"); ACT_KEY_NAMES.put(110, "Insert");
        ACT_KEY_NAMES.put(133, "Copy"); ACT_KEY_NAMES.put(135, "Paste"); ACT_KEY_NAMES.put(137, "Cut");
        ACT_KEY_NAMES.put(59, "F1"); ACT_KEY_NAMES.put(60, "F2"); ACT_KEY_NAMES.put(61, "F3"); ACT_KEY_NAMES.put(62, "F4");
        ACT_KEY_NAMES.put(63, "F5"); ACT_KEY_NAMES.put(64, "F6"); ACT_KEY_NAMES.put(65, "F7"); ACT_KEY_NAMES.put(66, "F8");
        ACT_KEY_NAMES.put(67, "F9"); ACT_KEY_NAMES.put(68, "F10"); ACT_KEY_NAMES.put(87, "F11"); ACT_KEY_NAMES.put(88, "F12");
        ACT_KEY_NAMES.put(155, "Email"); ACT_KEY_NAMES.put(140, "Calculator"); ACT_KEY_NAMES.put(144, "Files");
        ACT_KEY_NAMES.put(11, "0"); ACT_KEY_NAMES.put(2, "1"); ACT_KEY_NAMES.put(3, "2"); ACT_KEY_NAMES.put(4, "3");
        ACT_KEY_NAMES.put(5, "4"); ACT_KEY_NAMES.put(6, "5"); ACT_KEY_NAMES.put(7, "6"); ACT_KEY_NAMES.put(8, "7");
        ACT_KEY_NAMES.put(9, "8"); ACT_KEY_NAMES.put(10, "9");
        ACT_KEY_NAMES.put(30, "A"); ACT_KEY_NAMES.put(48, "B"); ACT_KEY_NAMES.put(46, "C"); ACT_KEY_NAMES.put(32, "D");
        ACT_KEY_NAMES.put(18, "E"); ACT_KEY_NAMES.put(33, "F"); ACT_KEY_NAMES.put(34, "G"); ACT_KEY_NAMES.put(35, "H");
        ACT_KEY_NAMES.put(23, "I"); ACT_KEY_NAMES.put(36, "J"); ACT_KEY_NAMES.put(37, "K"); ACT_KEY_NAMES.put(38, "L");
        ACT_KEY_NAMES.put(50, "M"); ACT_KEY_NAMES.put(49, "N"); ACT_KEY_NAMES.put(24, "O"); ACT_KEY_NAMES.put(25, "P");
        ACT_KEY_NAMES.put(16, "Q"); ACT_KEY_NAMES.put(19, "R"); ACT_KEY_NAMES.put(31, "S"); ACT_KEY_NAMES.put(20, "T");
        ACT_KEY_NAMES.put(22, "U"); ACT_KEY_NAMES.put(47, "V"); ACT_KEY_NAMES.put(17, "W"); ACT_KEY_NAMES.put(45, "X");
        ACT_KEY_NAMES.put(21, "Y"); ACT_KEY_NAMES.put(44, "Z");
    }
    private static final String PROP_DEVICE_NAME = "persist.gammaos.gamepad.device_name";
    private static final String PROP_DEVICE_VID = "persist.gammaos.gamepad.device_vid";
    private static final String PROP_DEVICE_PID = "persist.gammaos.gamepad.device_pid";

    // Combo mapping properties
    private static final String PROP_COMBO_MAP = "persist.gammaos.gamepad.combo_map";

    // Per-app profile properties
    private static final String PROP_PA_COUNT = "persist.gammaos.gamepad.pa_count";
    // Per profile: persist.gammaos.gamepad.paN_pkg, paN_btn, paN_combo

    // Mouse mode properties
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

    // Standard button code to name mapping
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

    // Standard axis code to name mapping (Linux ABS codes)
    private static final Map<Integer, String> AXIS_NAMES = new HashMap<>();
    static {
        AXIS_NAMES.put(0x00, "LX"); AXIS_NAMES.put(0x01, "LY");
        AXIS_NAMES.put(0x02, "LT"); AXIS_NAMES.put(0x03, "RX");
        AXIS_NAMES.put(0x04, "RY"); AXIS_NAMES.put(0x05, "RT");
        AXIS_NAMES.put(0x09, "GAS"); AXIS_NAMES.put(0x0a, "BRAKE");
        AXIS_NAMES.put(0x10, "DpadX"); AXIS_NAMES.put(0x11, "DpadY");
    }

    // Linux ABS code → Android MotionEvent AXIS constant.
    // These differ for axes beyond X/Y. The daemon works with Linux codes,
    // but MotionEvent.getAxisValue() requires Android constants.
    private static final Map<Integer, Integer> ABS_TO_ANDROID_AXIS = new HashMap<>();
    static {
        ABS_TO_ANDROID_AXIS.put(0x00, MotionEvent.AXIS_X);       // ABS_X = 0
        ABS_TO_ANDROID_AXIS.put(0x01, MotionEvent.AXIS_Y);       // ABS_Y = 1
        ABS_TO_ANDROID_AXIS.put(0x02, MotionEvent.AXIS_Z);       // ABS_Z = 2 → AXIS_Z = 11
        ABS_TO_ANDROID_AXIS.put(0x03, MotionEvent.AXIS_RX);      // ABS_RX = 3 → AXIS_RX = 12
        ABS_TO_ANDROID_AXIS.put(0x04, MotionEvent.AXIS_RY);      // ABS_RY = 4 → AXIS_RY = 13
        ABS_TO_ANDROID_AXIS.put(0x05, MotionEvent.AXIS_RZ);      // ABS_RZ = 5 → AXIS_RZ = 14
        ABS_TO_ANDROID_AXIS.put(0x09, MotionEvent.AXIS_GAS);     // ABS_GAS = 9 → AXIS_GAS = 22
        ABS_TO_ANDROID_AXIS.put(0x0a, MotionEvent.AXIS_BRAKE);   // ABS_BRAKE = 10 → AXIS_BRAKE = 23
        ABS_TO_ANDROID_AXIS.put(0x10, MotionEvent.AXIS_HAT_X);   // ABS_HAT0X = 16 → AXIS_HAT_X = 15
        ABS_TO_ANDROID_AXIS.put(0x11, MotionEvent.AXIS_HAT_Y);   // ABS_HAT0Y = 17 → AXIS_HAT_Y = 16
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

    private SwitchPreferenceCompat mEnablePref;
    private SwitchPreferenceCompat mMergePref;
    private SwitchPreferenceCompat mHideSourcePref;
    private PreferenceCategory mDevicesCategory;
    private ListPreference mDevicePresetPref;
    private SwitchPreferenceCompat mAnalogToDpadPref;
    private SwitchPreferenceCompat mDpadToAnalogPref;
    private SeekBarPreference mDpadThresholdPref;
    private SwitchPreferenceCompat mPwmEnablePref;
    private SeekBarPreference mPwmIntensityPref;
    private Preference mFFDevicePref;
    private SwitchPreferenceCompat mAbxySwapPref;
    private SwitchPreferenceCompat mInvertLeftPref;
    private SwitchPreferenceCompat mInvertRightPref;
    private ListPreference mGlobalSensitivityPref;

    // Mouse mode preferences
    private SwitchPreferenceCompat mMouseEnablePref;
    private SeekBarPreference mMouseHoldTimePref;
    private SeekBarPreference mMouseStickSpeedPref;
    private SeekBarPreference mMouseDpadSpeedPref;
    private ListPreference mMouseBoostPref;
    private SeekBarPreference mMouseScrollSpeedPref;

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

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.gamepad_settings);

        mEnablePref = findPreference(KEY_ENABLE);
        mMergePref = findPreference(KEY_MERGE);
        mHideSourcePref = findPreference(KEY_HIDE_SOURCE);
        mDevicesCategory = findPreference(KEY_DEVICES_CATEGORY);
        mDevicePresetPref = findPreference(KEY_DEVICE_PRESET);
        mAnalogToDpadPref = findPreference(KEY_ANALOG_TO_DPAD);
        mDpadToAnalogPref = findPreference(KEY_DPAD_TO_ANALOG);
        mDpadThresholdPref = findPreference(KEY_DPAD_THRESHOLD);
        mPwmEnablePref = findPreference(KEY_PWM_ENABLE);
        mPwmIntensityPref = findPreference(KEY_PWM_INTENSITY);
        mFFDevicePref = findPreference(KEY_FF_DEVICE);

        // Load current values from properties
        mEnablePref.setChecked(
                SystemProperties.getInt(PROP_ENABLE, 0) != 0);
        mMergePref.setChecked(
                SystemProperties.getInt(PROP_MERGE, 1) != 0);
        mHideSourcePref.setChecked(
                SystemProperties.getInt(PROP_HIDE_SOURCE, 1) != 0);
        mAnalogToDpadPref.setChecked(
                SystemProperties.getInt(PROP_ANALOG_TO_DPAD, 0) != 0);
        mDpadToAnalogPref.setChecked(
                SystemProperties.getInt(PROP_DPAD_TO_ANALOG, 0) != 0);
        mDpadThresholdPref.setValue(
                SystemProperties.getInt(PROP_DPAD_THRESHOLD, 50));
        mPwmEnablePref.setChecked(
                SystemProperties.getInt(PROP_PWM_ENABLE, 1) != 0);
        mPwmIntensityPref.setValue(
                SystemProperties.getInt(PROP_PWM_INTENSITY, 200));
        updateFFDeviceSummary();

        // Global quick-access toggles
        mAbxySwapPref = findPreference(KEY_ABXY_SWAP);
        mInvertLeftPref = findPreference(KEY_INVERT_LEFT);
        mInvertRightPref = findPreference(KEY_INVERT_RIGHT);
        mGlobalSensitivityPref = findPreference(KEY_GLOBAL_SENSITIVITY);

        if (mAbxySwapPref != null) {
            mAbxySwapPref.setChecked(
                    SystemProperties.getInt(PROP_ABXY_SWAP, 0) != 0);
            mAbxySwapPref.setOnPreferenceChangeListener(this);
        }
        if (mInvertLeftPref != null) {
            mInvertLeftPref.setChecked(
                    SystemProperties.getInt(PROP_INVERT_LEFT, 0) != 0);
            mInvertLeftPref.setOnPreferenceChangeListener(this);
        }
        if (mInvertRightPref != null) {
            mInvertRightPref.setChecked(
                    SystemProperties.getInt(PROP_INVERT_RIGHT, 0) != 0);
            mInvertRightPref.setOnPreferenceChangeListener(this);
        }
        if (mGlobalSensitivityPref != null) {
            mGlobalSensitivityPref.setValue(
                    String.valueOf(SystemProperties.getInt(PROP_GLOBAL_SENSITIVITY, 0)));
            mGlobalSensitivityPref.setOnPreferenceChangeListener(this);
            updateGlobalSensitivitySummary();
        }

        // Initialize device preset from current VID/PID properties
        initDevicePreset();

        // Set listeners
        mEnablePref.setOnPreferenceChangeListener(this);
        mMergePref.setOnPreferenceChangeListener(this);
        mHideSourcePref.setOnPreferenceChangeListener(this);
        mAnalogToDpadPref.setOnPreferenceChangeListener(this);
        mDpadToAnalogPref.setOnPreferenceChangeListener(this);
        mDpadThresholdPref.setOnPreferenceChangeListener(this);
        mPwmEnablePref.setOnPreferenceChangeListener(this);
        mPwmIntensityPref.setOnPreferenceChangeListener(this);

        if (mDevicePresetPref != null) {
            mDevicePresetPref.setOnPreferenceChangeListener(this);
        }

        Preference remapBtnPref = findPreference(KEY_REMAP_BUTTONS);
        if (remapBtnPref != null) {
            remapBtnPref.setOnPreferenceClickListener(this);
            updateRemapSummary(remapBtnPref, PROP_REMAP_BTN, BTN_NAMES);
        }

        Preference remapAxisPref = findPreference(KEY_REMAP_AXES);
        if (remapAxisPref != null) {
            remapAxisPref.setOnPreferenceClickListener(this);
            updateRemapSummary(remapAxisPref, PROP_REMAP_AXIS, AXIS_NAMES);
        }

        Preference axisRolesPref = findPreference(KEY_AXIS_ROLES);
        if (axisRolesPref != null) {
            axisRolesPref.setOnPreferenceClickListener(this);
            updateAxisRolesSummary(axisRolesPref);
        }

        Preference axisBtnPref = findPreference(KEY_AXIS_TO_BUTTON);
        if (axisBtnPref != null) {
            axisBtnPref.setOnPreferenceClickListener(this);
            updateAxisButtonSummary(axisBtnPref);
        }

        Preference blacklistVpadPref = findPreference(KEY_BLACKLIST_VPAD);
        if (blacklistVpadPref != null) {
            blacklistVpadPref.setOnPreferenceClickListener(this);
            updateBlacklistSummary(blacklistVpadPref, PROP_BLACKLIST_VPAD);
        }

        Preference blacklistPassPref = findPreference(KEY_BLACKLIST_PASS);
        if (blacklistPassPref != null) {
            blacklistPassPref.setOnPreferenceClickListener(this);
            updateBlacklistSummary(blacklistPassPref, PROP_BLACKLIST_PASS);
        }

        Preference comboMapPref = findPreference(KEY_COMBO_MAP);
        if (comboMapPref != null) {
            comboMapPref.setOnPreferenceClickListener(this);
            updateComboMapSummary(comboMapPref);
        }

        // Custom Button Actions: add into the remap category programmatically.
        PreferenceCategory remapCat = findPreference("gamepad_remap_category");
        if (remapCat != null && findPreference(KEY_CUSTOM_ACTIONS) == null) {
            Preference caPref = new Preference(remapCat.getContext());
            caPref.setKey(KEY_CUSTOM_ACTIONS);
            caPref.setTitle("Custom Button Actions");
            caPref.setSummary("Bind short / long press to a key, app, activity, prop or command");
            caPref.setOnPreferenceClickListener(this);
            remapCat.addPreference(caPref);
        }

        Preference perappAddPref = findPreference(KEY_PERAPP_ADD);
        if (perappAddPref != null) {
            perappAddPref.setOnPreferenceClickListener(this);
        }
        populatePerAppProfiles();

        Preference calibPref = findPreference(KEY_CALIBRATION);
        if (calibPref != null) {
            calibPref.setOnPreferenceClickListener(this);
        }

        Preference clearCalibPref = findPreference(KEY_CLEAR_CALIBRATION);
        if (clearCalibPref != null) {
            clearCalibPref.setOnPreferenceClickListener(this);
        }

        if (mFFDevicePref != null) {
            mFFDevicePref.setOnPreferenceClickListener(this);
        }

        Preference testVibPref = findPreference(KEY_TEST_VIBRATION);
        if (testVibPref != null) {
            testVibPref.setOnPreferenceClickListener(this);
        }

        Preference testPref = findPreference(KEY_TEST);
        if (testPref != null) {
            testPref.setOnPreferenceClickListener(this);
        }

        // Mouse mode preferences
        mMouseEnablePref = findPreference(KEY_MOUSE_ENABLE);
        mMouseHoldTimePref = findPreference(KEY_MOUSE_HOLD_TIME);
        mMouseStickSpeedPref = findPreference(KEY_MOUSE_STICK_SPEED);
        mMouseDpadSpeedPref = findPreference(KEY_MOUSE_DPAD_SPEED);
        mMouseBoostPref = findPreference(KEY_MOUSE_BOOST);
        mMouseScrollSpeedPref = findPreference(KEY_MOUSE_SCROLL_SPEED);

        if (mMouseEnablePref != null) {
            // Mouse mode is always available; use combo1 prop existence as proxy
            mMouseEnablePref.setChecked(
                    !SystemProperties.get(PROP_MOUSE_COMBO1, "").isEmpty());
            mMouseEnablePref.setOnPreferenceChangeListener(this);
        }
        if (mMouseHoldTimePref != null) {
            mMouseHoldTimePref.setMin(500);
            mMouseHoldTimePref.setValue(
                    SystemProperties.getInt(PROP_MOUSE_HOLD_MS, 2000));
            mMouseHoldTimePref.setOnPreferenceChangeListener(this);
        }
        if (mMouseStickSpeedPref != null) {
            mMouseStickSpeedPref.setMin(1);
            mMouseStickSpeedPref.setValue(
                    SystemProperties.getInt(PROP_MOUSE_STICK_SPEED, 12));
            mMouseStickSpeedPref.setOnPreferenceChangeListener(this);
        }
        if (mMouseDpadSpeedPref != null) {
            mMouseDpadSpeedPref.setMin(1);
            mMouseDpadSpeedPref.setValue(
                    SystemProperties.getInt(PROP_MOUSE_DPAD_SPEED, 6));
            mMouseDpadSpeedPref.setOnPreferenceChangeListener(this);
        }
        if (mMouseBoostPref != null) {
            mMouseBoostPref.setValue(
                    String.valueOf(SystemProperties.getInt(PROP_MOUSE_BOOST, 20)));
            mMouseBoostPref.setOnPreferenceChangeListener(this);
        }
        if (mMouseScrollSpeedPref != null) {
            mMouseScrollSpeedPref.setMin(1);
            mMouseScrollSpeedPref.setValue(
                    SystemProperties.getInt(PROP_MOUSE_SCROLL_SPEED, 4));
            mMouseScrollSpeedPref.setOnPreferenceChangeListener(this);
        }

        Preference mouseComboPref = findPreference(KEY_MOUSE_COMBO);
        if (mouseComboPref != null) {
            mouseComboPref.setOnPreferenceClickListener(this);
            updateMouseComboSummary(mouseComboPref);
        }

        Preference mouseButtonsPref = findPreference(KEY_MOUSE_BUTTONS);
        if (mouseButtonsPref != null) {
            mouseButtonsPref.setOnPreferenceClickListener(this);
            updateMouseButtonsSummary(mouseButtonsPref);
        }

        // Load selected devices (kernel names)
        String devicesStr = SystemProperties.get(PROP_DEVICES, "");
        if (!devicesStr.isEmpty()) {
            for (String name : devicesStr.split(";")) {
                if (!name.isEmpty()) {
                    mSelectedDevices.add(name);
                }
            }
        }

        populateControllerList();

        getParentFragmentManager().setFragmentResultListener(
                "remap_changed", this, (requestKey, result) -> {
                    refreshRemapSummaries();
                });

        mInputManager = getContext().getSystemService(InputManager.class);

        // Scroll to and highlight a specific preference when launched from QS tile
        Bundle args = getArguments();
        if (args != null) {
            String targetKey = args.getString(":settings:fragment_args_key");
            if (targetKey != null && !targetKey.isEmpty()) {
                scrollToAndHighlight(targetKey);
            }
        }
    }

    private void scrollToAndHighlight(String key) {
        // Delay to allow RecyclerView to layout, then scroll
        mHandler.postDelayed(() -> {
            scrollToPreference(key);
            // Start flashing after scroll settles
            mHandler.postDelayed(() -> flashPreference(key, 0), 400);
        }, 300);
    }

    private void flashPreference(String key, int count) {
        if (count >= 6) return; // 3 full cycles (dim + bright)
        Preference pref = findPreference(key);
        if (pref == null) return;

        RecyclerView listView = getListView();
        if (listView == null) return;

        float alpha = (count % 2 == 0) ? 0.3f : 1.0f;

        // Find the view for this preference by checking visible children
        RecyclerView.Adapter<?> adapter = listView.getAdapter();
        for (int i = 0; i < listView.getChildCount(); i++) {
            View child = listView.getChildAt(i);
            RecyclerView.ViewHolder vh = listView.getChildViewHolder(child);
            if (vh != null) {
                int pos = vh.getAdapterPosition();
                if (pos >= 0 && adapter != null && pos < adapter.getItemCount()) {
                    // Match by checking if scrollToPreference brought this into center
                    // Use the preference's own adapter position via the list
                    View prefView = listView.getLayoutManager().findViewByPosition(pos);
                    if (prefView == child) {
                        // Check tag or text match
                        TextView titleView = child.findViewById(android.R.id.title);
                        if (titleView != null && pref.getTitle() != null
                                && pref.getTitle().equals(titleView.getText())) {
                            child.setAlpha(alpha);
                            break;
                        }
                    }
                }
            }
        }
        mHandler.postDelayed(() -> flashPreference(key, count + 1), 300);
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

    /** Re-read all toggle props so Settings UI matches QS tile changes. */
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

    @Override
    public void onPause() {
        super.onPause();
        mHandler.removeCallbacks(mRemapPollRunnable);

        if (mInputManager != null) {
            mInputManager.unregisterInputDeviceListener(this);
        }
    }

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

    private void initDevicePreset() {
        if (mDevicePresetPref == null) return;

        String currentVid = SystemProperties.get(PROP_DEVICE_VID, "");
        String currentPid = SystemProperties.get(PROP_DEVICE_PID, "");

        // Try to match current VID/PID to a preset
        String matchedPreset = "xbox_wireless"; // default
        if (!currentVid.isEmpty() && !currentPid.isEmpty()) {
            // Normalize to hex without 0x prefix
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
            // Set value first so getEntry() works when updating summary
            mDevicePresetPref.setValue(value);
            SystemProperties.set(PROP_DEVICE_NAME, preset[0]);
            // Store VID/PID with 0x prefix so GetIntProperty (strtol base 0) parses hex
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
        int pad = (int) (16 * context.getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, 0);

        EditText nameInput = new EditText(context);
        nameInput.setHint(getString(R.string.gamepad_device_custom_name_title));
        nameInput.setText(SystemProperties.get(PROP_DEVICE_NAME, "GammaOS Virtual Gamepad"));
        layout.addView(nameInput);

        EditText vidInput = new EditText(context);
        vidInput.setHint(getString(R.string.gamepad_device_custom_vid_title));
        vidInput.setInputType(InputType.TYPE_CLASS_TEXT);
        vidInput.setText(SystemProperties.get(PROP_DEVICE_VID, "0x045e")
                .replace("0x", ""));
        layout.addView(vidInput);

        EditText pidInput = new EditText(context);
        pidInput.setHint(getString(R.string.gamepad_device_custom_pid_title));
        pidInput.setInputType(InputType.TYPE_CLASS_TEXT);
        pidInput.setText(SystemProperties.get(PROP_DEVICE_PID, "0x0b13")
                .replace("0x", ""));
        layout.addView(pidInput);

        new AlertDialog.Builder(context)
                .setTitle(getString(R.string.gamepad_device_preset_custom))
                .setView(layout)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String name = nameInput.getText().toString().trim();
                    String vid = vidInput.getText().toString().trim();
                    String pid = pidInput.getText().toString().trim();

                    if (!name.isEmpty()) {
                        SystemProperties.set(PROP_DEVICE_NAME, name);
                    }
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

    private void refreshRemapSummaries() {
        Preference comboMapPref = findPreference(KEY_COMBO_MAP);
        if (comboMapPref != null) {
            updateComboMapSummary(comboMapPref);
        }
        Preference axisRolesPref = findPreference(KEY_AXIS_ROLES);
        if (axisRolesPref != null) {
            updateAxisRolesSummary(axisRolesPref);
        }
        Preference remapBtnPref = findPreference(KEY_REMAP_BUTTONS);
        if (remapBtnPref != null) {
            updateRemapSummary(remapBtnPref, PROP_REMAP_BTN, BTN_NAMES);
        }
        Preference remapAxisPref = findPreference(KEY_REMAP_AXES);
        if (remapAxisPref != null) {
            updateRemapSummary(remapAxisPref, PROP_REMAP_AXIS, AXIS_NAMES);
        }
        Preference axisBtnPref = findPreference(KEY_AXIS_TO_BUTTON);
        if (axisBtnPref != null) {
            updateAxisButtonSummary(axisBtnPref);
        }
        Preference blacklistVpadPref = findPreference(KEY_BLACKLIST_VPAD);
        if (blacklistVpadPref != null) {
            updateBlacklistSummary(blacklistVpadPref, PROP_BLACKLIST_VPAD);
        }
        Preference blacklistPassPref = findPreference(KEY_BLACKLIST_PASS);
        if (blacklistPassPref != null) {
            updateBlacklistSummary(blacklistPassPref, PROP_BLACKLIST_PASS);
        }
    }

    private void updateAxisRolesSummary(Preference pref) {
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

        if (sb.length() > 0) {
            pref.setSummary(sb.toString());
        } else {
            pref.setSummary(R.string.gamepad_axis_roles_none);
        }
    }

    private void updateAxisButtonSummary(Preference pref) {
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
                sb.append(axisName).append(" \u2192 ").append(btnName)
                        .append(" (").append(onPct).append("%/").append(offPct).append("%")
                        .append("h".equals(mode) ? " hijack" : " both").append(")");
            } catch (NumberFormatException e) {
                // Skip
            }
        }
        pref.setSummary(sb.length() > 0 ? sb.toString()
                : getString(R.string.gamepad_axis_to_button_none));
    }

    private void showAxisToButtonDialog() {
        Context context = getContext();
        if (context == null) return;

        String current = SystemProperties.get(PROP_AXIS_BTN, "");

        // Build items list: current rules + presets + add/clear
        List<String> items = new ArrayList<>();

        // Show current rules
        if (!current.isEmpty()) {
            String[] entries = current.split(",");
            for (String entry : entries) {
                String[] parts = entry.split(":");
                if (parts.length >= 4) {
                    int axis = Integer.parseInt(parts[0]);
                    int btn = Integer.parseInt(parts[1]);
                    String mode = parts.length >= 5 ? parts[4] : "b";
                    String axisName = AXIS_NAMES.getOrDefault(axis,
                            "0x" + Integer.toHexString(axis));
                    String btnName = BTN_NAMES.getOrDefault(btn,
                            "0x" + Integer.toHexString(btn));
                    items.add(axisName + " \u2192 " + btnName + " (" + parts[2] + "%/" + parts[3] + "%"
                            + ("h".equals(mode) ? " hijack" : " both") + ")");
                }
            }
        }

        items.add(getString(R.string.gamepad_axis_to_button_add));
        if (!current.isEmpty()) {
            items.add(getString(R.string.gamepad_axis_to_button_test));
            items.add(getString(R.string.gamepad_axis_to_button_clear_all));
        }

        int currentCount = current.isEmpty() ? 0 : current.split(",").length;

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
                            // Add custom rule
                            showAddAxisButtonRuleDialog();
                        } else if (idx == 1) {
                            // Test axis-to-button
                            showAxisButtonTestDialog();
                        } else if (idx == 2) {
                            // Clear all
                            SystemProperties.set(PROP_AXIS_BTN, "");
                            bumpConfigVersion();
                            refreshRemapSummaries();
                            Toast.makeText(context,
                                    R.string.gamepad_axis_to_button_cleared,
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void addAxisButtonRule(String current, String rule) {
        String newVal = current.isEmpty() ? rule : current + "," + rule;
        SystemProperties.set(PROP_AXIS_BTN, newVal);
        bumpConfigVersion();
        refreshRemapSummaries();
        Toast.makeText(getContext(), R.string.gamepad_axis_to_button_saved,
                Toast.LENGTH_SHORT).show();
    }

    /**
     * Show a live test dialog for axis-to-button rules.
     * Displays each configured rule with a live axis value bar,
     * threshold markers, and press/release indicator.
     */
    private void showAxisButtonTestDialog() {
        Context context = getContext();
        if (context == null) return;

        String current = SystemProperties.get(PROP_AXIS_BTN, "");
        if (current.isEmpty()) return;

        String[] entries = current.split(",");

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * context.getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        // Build UI for each rule
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

            // Rule header
            TextView header = new TextView(context);
            header.setText(axisName + " \u2192 " + btnName
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

            // Progress bar (0-100)
            bars[i] = new ProgressBar(context, null,
                    android.R.attr.progressBarStyleHorizontal);
            bars[i].setMax(100);
            bars[i].setProgress(0);
            root.addView(bars[i], new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));

            // Value + state row
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

        // Track press state per rule
        final boolean[] pressed = new boolean[entries.length];

        dialog.show();

        // Attach joystick listener to the dialog's decor view
        dialog.getWindow().getDecorView().setOnGenericMotionListener((v, event) -> {
            if ((event.getSource() & InputDevice.SOURCE_JOYSTICK) == 0) return false;

            for (int i = 0; i < entries.length; i++) {
                if (bars[i] == null) continue;

                // Convert Linux ABS code to Android AXIS constant for MotionEvent
                int androidAxis = ABS_TO_ANDROID_AXIS.getOrDefault(
                        axisCodes[i], axisCodes[i]);
                float raw = event.getAxisValue(androidAxis);

                // Determine if this is a trigger axis (unipolar 0..1) or stick (-1..1)
                InputDevice.MotionRange range = event.getDevice() != null
                        ? event.getDevice().getMotionRange(androidAxis) : null;
                int pct;
                if (range != null && range.getMin() >= 0f) {
                    // Unipolar trigger: raw is 0..1
                    pct = Math.round(Math.max(0f, Math.min(1f, raw)) * 100f);
                } else {
                    // Bipolar stick or bipolar trigger: use absolute value
                    pct = Math.round(Math.max(0f, Math.min(1f, Math.abs(raw))) * 100f);
                }

                bars[i].setProgress(pct);
                valueLabels[i].setText(pct + "%");

                // Hysteresis: press at onThreshold, release at offThreshold
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

    private void showAddAxisButtonRuleDialog() {
        Context context = getContext();
        if (context == null) return;

        // Axis selection
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
        int pad = (int) (16 * context.getResources().getDisplayMetrics().density);
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

        // Mode selection: hijack (replace ABS with BTN) or broadcast (emit both)
        final String[] modeChoice = {"b"}; // default: broadcast
        TextView modeLabel = new TextView(context);
        modeLabel.setText("Mode:");
        LinearLayout.LayoutParams modeLabelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        modeLabelParams.topMargin = (int) (12 * context.getResources().getDisplayMetrics().density);
        layout.addView(modeLabel, modeLabelParams);

        android.widget.RadioGroup modeGroup = new android.widget.RadioGroup(context);
        modeGroup.setOrientation(android.widget.RadioGroup.VERTICAL);
        android.widget.RadioButton broadcastBtn = new android.widget.RadioButton(context);
        broadcastBtn.setText("Both (emit BTN and keep ABS value)");
        broadcastBtn.setId(android.view.View.generateViewId());
        modeGroup.addView(broadcastBtn);
        android.widget.RadioButton hijackBtn = new android.widget.RadioButton(context);
        hijackBtn.setText("Hijack (replace ABS with BTN, no ABS output)");
        hijackBtn.setId(android.view.View.generateViewId());
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
                    addAxisButtonRule(current, rule);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

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

    private boolean isGammaPadVirtualDevice(Map<String, String> kernelNameMap) {
        for (Map.Entry<String, String> entry : kernelNameMap.entrySet()) {
            String phys = readKernelDevicePhys(entry.getValue());
            if (phys != null && phys.contains("gammapad-virtual")) {
                return true;
            }
        }
        return false;
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

        // Identify our virtual device name via sysfs phys
        String gammaPadName = getGammaPadVirtualName(kernelNameMap);

        int[] ids = im.getInputDeviceIds();
        boolean found = false;

        Set<String> connectedKernelNames = new HashSet<>();

        for (int id : ids) {
            InputDevice device = im.getInputDevice(id);
            if (device == null) continue;

            int sources = device.getSources();
            // Skip touchscreens and mice — only show key/button input devices
            if ((sources & InputDevice.SOURCE_TOUCHSCREEN) != 0) continue;
            if ((sources & InputDevice.SOURCE_MOUSE) != 0
                    && (sources & InputDevice.SOURCE_GAMEPAD) == 0) continue;
            // Must have at least keyboard, gamepad, joystick, or DPAD keys
            boolean hasKeys = (sources & InputDevice.SOURCE_GAMEPAD) != 0
                    || (sources & InputDevice.SOURCE_JOYSTICK) != 0
                    || (sources & InputDevice.SOURCE_KEYBOARD) != 0
                    || (sources & InputDevice.SOURCE_DPAD) != 0;
            if (!hasKeys) continue;
            // Skip purely internal virtual keyboards (no physical keys)
            if (device.isVirtual()) continue;

            // Skip our own virtual device (match by sysfs phys or name)
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

        // Show disconnected but selected controllers — enabled so they can be removed.
        // When hide_source is active, the daemon unlinks physical device nodes so they
        // won't appear in InputManager. Treat those as "hidden" not "disconnected".
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
            // Skip GammaPad virtual device by checking sysfs phys
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

    private void updateRemapSummary(Preference pref, String propKey,
                                     Map<Integer, String> codeNames) {
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
                sb.append(fromName).append(" \u2192 ").append(toName);
            } catch (NumberFormatException e) {
                // Skip malformed entries
            }
        }

        pref.setSummary(sb.length() > 0 ? sb.toString()
                : getString(R.string.gamepad_remap_none));
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String key = preference.getKey();

        switch (key) {
            case KEY_ENABLE:
                SystemProperties.set(PROP_ENABLE,
                        (Boolean) newValue ? "1" : "0");
                return true;
            case KEY_MERGE:
                SystemProperties.set(PROP_MERGE,
                        (Boolean) newValue ? "1" : "0");
                bumpConfigVersion();
                return true;
            case KEY_HIDE_SOURCE:
                SystemProperties.set(PROP_HIDE_SOURCE,
                        (Boolean) newValue ? "1" : "0");
                bumpConfigVersion();
                return true;
            case KEY_DEVICE_PRESET:
                applyDevicePreset((String) newValue);
                return false; // we set the value in applyDevicePreset
            case KEY_ANALOG_TO_DPAD:
                SystemProperties.set(PROP_ANALOG_TO_DPAD,
                        (Boolean) newValue ? "1" : "0");
                bumpConfigVersion();
                return true;
            case KEY_DPAD_TO_ANALOG:
                SystemProperties.set(PROP_DPAD_TO_ANALOG,
                        (Boolean) newValue ? "1" : "0");
                bumpConfigVersion();
                return true;
            case KEY_DPAD_THRESHOLD:
                SystemProperties.set(PROP_DPAD_THRESHOLD,
                        String.valueOf((int) newValue));
                bumpConfigVersion();
                return true;
            case KEY_PWM_ENABLE:
                SystemProperties.set(PROP_PWM_ENABLE,
                        (Boolean) newValue ? "1" : "0");
                bumpConfigVersion();
                return true;
            case KEY_PWM_INTENSITY:
                SystemProperties.set(PROP_PWM_INTENSITY,
                        String.valueOf((int) newValue));
                bumpConfigVersion();
                return true;
            case KEY_ABXY_SWAP:
                SystemProperties.set(PROP_ABXY_SWAP,
                        (Boolean) newValue ? "1" : "0");
                bumpConfigVersion();
                return true;
            case KEY_INVERT_LEFT:
                SystemProperties.set(PROP_INVERT_LEFT,
                        (Boolean) newValue ? "1" : "0");
                bumpConfigVersion();
                return true;
            case KEY_INVERT_RIGHT:
                SystemProperties.set(PROP_INVERT_RIGHT,
                        (Boolean) newValue ? "1" : "0");
                bumpConfigVersion();
                return true;
            case KEY_GLOBAL_SENSITIVITY:
                SystemProperties.set(PROP_GLOBAL_SENSITIVITY, (String) newValue);
                bumpConfigVersion();
                updateGlobalSensitivitySummary();
                return true;

            // Mouse mode settings
            case KEY_MOUSE_ENABLE:
                boolean mouseEnabled = (Boolean) newValue;
                if (mouseEnabled) {
                    // Set default combo if not configured yet
                    if (SystemProperties.get(PROP_MOUSE_COMBO1, "").isEmpty()) {
                        SystemProperties.set(PROP_MOUSE_COMBO1, "314"); // BTN_SELECT
                        SystemProperties.set(PROP_MOUSE_COMBO2, "311"); // BTN_TR
                    }
                } else {
                    // Clear combo props to disable
                    SystemProperties.set(PROP_MOUSE_COMBO1, "");
                    SystemProperties.set(PROP_MOUSE_COMBO2, "");
                }
                bumpConfigVersion();
                return true;
            case KEY_MOUSE_HOLD_TIME:
                SystemProperties.set(PROP_MOUSE_HOLD_MS,
                        String.valueOf((int) newValue));
                bumpConfigVersion();
                return true;
            case KEY_MOUSE_STICK_SPEED:
                SystemProperties.set(PROP_MOUSE_STICK_SPEED,
                        String.valueOf((int) newValue));
                bumpConfigVersion();
                return true;
            case KEY_MOUSE_DPAD_SPEED:
                SystemProperties.set(PROP_MOUSE_DPAD_SPEED,
                        String.valueOf((int) newValue));
                bumpConfigVersion();
                return true;
            case KEY_MOUSE_BOOST:
                SystemProperties.set(PROP_MOUSE_BOOST, (String) newValue);
                bumpConfigVersion();
                return true;
            case KEY_MOUSE_SCROLL_SPEED:
                SystemProperties.set(PROP_MOUSE_SCROLL_SPEED,
                        String.valueOf((int) newValue));
                bumpConfigVersion();
                return true;
        }

        return false;
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        String key = preference.getKey();

        if (KEY_CUSTOM_ACTIONS.equals(key)) {
            showActionListDialog();
            return true;
        } else if (KEY_REMAP_BUTTONS.equals(key)) {
            GamepadRemapDialogFragment dialog = new GamepadRemapDialogFragment();
            dialog.show(getParentFragmentManager(), "gamepad_remap");
            return true;
        } else if (KEY_REMAP_AXES.equals(key)) {
            GamepadRemapDialogFragment dialog =
                    GamepadRemapDialogFragment.newInstance(true);
            dialog.show(getParentFragmentManager(), "gamepad_remap_axis");
            return true;
        } else if (KEY_AXIS_ROLES.equals(key)) {
            GamepadAxisMappingDialogFragment dialog =
                    new GamepadAxisMappingDialogFragment();
            dialog.show(getParentFragmentManager(), "gamepad_axis_mapping");
            return true;
        } else if (KEY_AXIS_TO_BUTTON.equals(key)) {
            showAxisToButtonDialog();
            return true;
        } else if (KEY_BLACKLIST_VPAD.equals(key)) {
            showBlacklistDialog(PROP_BLACKLIST_VPAD,
                    R.string.gamepad_blacklist_vpad_title);
            return true;
        } else if (KEY_BLACKLIST_PASS.equals(key)) {
            showBlacklistDialog(PROP_BLACKLIST_PASS,
                    R.string.gamepad_blacklist_pass_title);
            return true;
        } else if (KEY_CALIBRATION.equals(key)) {
            GamepadCalibrationDialogFragment dialog =
                    new GamepadCalibrationDialogFragment();
            dialog.show(getParentFragmentManager(), "gamepad_calibration");
            return true;
        } else if (KEY_CLEAR_CALIBRATION.equals(key)) {
            clearCalibration();
            return true;
        } else if (KEY_FF_DEVICE.equals(key)) {
            showFFDeviceDialog();
            return true;
        } else if (KEY_TEST_VIBRATION.equals(key)) {
            showVibrationTestDialog();
            return true;
        } else if (KEY_TEST.equals(key)) {
            GamepadTestFragment testFragment = new GamepadTestFragment();
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.main_content, testFragment)
                    .addToBackStack(null)
                    .commit();
            return true;
        } else if (KEY_COMBO_MAP.equals(key)) {
            showComboMapDialog();
            return true;
        } else if (KEY_PERAPP_ADD.equals(key)) {
            showPerAppPickerDialog();
            return true;
        } else if (KEY_MOUSE_COMBO.equals(key)) {
            showMouseComboDialog();
            return true;
        } else if (KEY_MOUSE_BUTTONS.equals(key)) {
            showMouseButtonsDialog();
            return true;
        } else if (key != null && key.startsWith("gamepad_perapp_profile_")) {
            int idx = Integer.parseInt(key.substring("gamepad_perapp_profile_".length()));
            showPerAppEditDialog(idx);
            return true;
        }

        return false;
    }

    private void showFFDeviceDialog() {
        String current = SystemProperties.get(PROP_FF_DEVICE, "");

        // Scan /sys/class/input/event*/device/name for input devices
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

        new AlertDialog.Builder(getContext())
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

    private void showVibrationTestDialog() {
        String[] items = {
            "Short pulse (200ms)",
            "Medium pulse (500ms)",
            "Long pulse (1000ms)",
            "Strong rumble (2000ms)",
            "Pulsing pattern",
            "Ramp up"
        };

        new AlertDialog.Builder(getContext())
                .setTitle(R.string.gamepad_test_vibration_title)
                .setItems(items, (d, which) -> {
                    Vibrator v = findGamepadVibrator();
                    if (v == null) return;
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
                                    new int[]{0, 200, 0, 200, 0, 200, 0, 200},
                                    -1));
                            break;
                        case 5:
                            v.vibrate(VibrationEffect.createWaveform(
                                    new long[]{0, 300, 0, 300, 0, 300, 0, 400},
                                    new int[]{0, 60, 0, 120, 0, 180, 0, 255},
                                    -1));
                            break;
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private Vibrator findGamepadVibrator() {
        InputManager im = getContext().getSystemService(InputManager.class);
        if (im != null) {
            // Find GammaPad virtual device name via sysfs phys
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

    private void clearCalibration() {
        int[] axes = { 0, 1, 2, 3, 4, 5, 9, 10 }; // ABS_X..ABS_RZ, ABS_GAS, ABS_BRAKE
        for (int axis : axes) {
            SystemProperties.set("persist.gammaos.gamepad.cal_axis" + axis, "");
        }
        bumpConfigVersion();
        Toast.makeText(getContext(), R.string.gamepad_calibration_cleared,
                Toast.LENGTH_SHORT).show();
    }

    private void updateBlacklistSummary(Preference pref, String prop) {
        String val = SystemProperties.get(prop, "");
        if (val.isEmpty()) {
            pref.setSummary(R.string.gamepad_blacklist_none);
            return;
        }
        // Count comma-separated entries
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

    private void showBlacklistDialog(String prop, int titleRes) {
        Context context = getContext();
        if (context == null) return;

        // Build sorted list of known button names
        List<Integer> btnCodes = new ArrayList<>(BTN_NAMES.keySet());
        java.util.Collections.sort(btnCodes);
        String[] labels = new String[btnCodes.size()];
        for (int i = 0; i < btnCodes.size(); i++) {
            int code = btnCodes.get(i);
            labels[i] = BTN_NAMES.get(code) + " (0x" + Integer.toHexString(code) + ")";
        }

        // Parse current blacklist
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

    private void updateGlobalSensitivitySummary() {
        if (mGlobalSensitivityPref == null) return;
        CharSequence entry = mGlobalSensitivityPref.getEntry();
        if (entry != null) {
            mGlobalSensitivityPref.setSummary(entry);
        }
    }

    // --- Mouse mode helpers ---

    private void updateMouseComboSummary(Preference pref) {
        int btn1 = SystemProperties.getInt(PROP_MOUSE_COMBO1, 0x13a);
        int btn2 = SystemProperties.getInt(PROP_MOUSE_COMBO2, 0x137);
        String name1 = BTN_NAMES.getOrDefault(btn1, "0x" + Integer.toHexString(btn1));
        String name2 = BTN_NAMES.getOrDefault(btn2, "0x" + Integer.toHexString(btn2));
        pref.setSummary(getString(R.string.gamepad_mouse_combo_current, name1, name2));
    }

    private void updateMouseButtonsSummary(Preference pref) {
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

    private void showMouseComboDialog() {
        Context context = getContext();
        if (context == null) return;

        // Button selection list
        List<Integer> btnCodes = new ArrayList<>(BTN_NAMES.keySet());
        java.util.Collections.sort(btnCodes);
        String[] labels = new String[btnCodes.size()];
        for (int i = 0; i < btnCodes.size(); i++) {
            labels[i] = BTN_NAMES.get(btnCodes.get(i));
        }

        int currentBtn1 = SystemProperties.getInt(PROP_MOUSE_COMBO1, 0x13a);

        new AlertDialog.Builder(context)
                .setTitle(R.string.gamepad_mouse_combo_btn1)
                .setItems(labels, (d, which) -> {
                    int btn1 = btnCodes.get(which);
                    // Now pick second button
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

        // Show dialog to configure each mouse button action
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
        int[] defaults = { 0x130, 0x131, 0x134, 0x133 }; // A, B, Y, X

        // Build current values summary
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
        java.util.Collections.sort(btnCodes);
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

    // --- Combo mapping helpers ---

    private void updateComboMapSummary(Preference pref) {
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
                sb.append(n1).append("+").append(n2).append(" \u2192 ").append(ne);
            } catch (NumberFormatException e) {
                // skip
            }
        }
        pref.setSummary(sb.length() > 0 ? sb.toString()
                : getString(R.string.gamepad_combo_map_none));
    }

    private void showComboMapDialog() {
        Context context = getContext();
        if (context == null) return;

        String current = SystemProperties.get(PROP_COMBO_MAP, "");
        List<String> items = new ArrayList<>();

        // Show existing combos
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
                items.add(n1 + " + " + n2 + " \u2192 " + ne);
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
                        // Remove this combo
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
        java.util.Collections.sort(btnCodes);
        String[] labels = new String[btnCodes.size()];
        for (int i = 0; i < btnCodes.size(); i++) {
            labels[i] = BTN_NAMES.get(btnCodes.get(i));
        }

        // Step 1: Pick first button
        new AlertDialog.Builder(context)
                .setTitle(R.string.gamepad_combo_map_btn1)
                .setItems(labels, (d, which1) -> {
                    int btn1 = btnCodes.get(which1);
                    // Step 2: Pick second button
                    new AlertDialog.Builder(context)
                            .setTitle(R.string.gamepad_combo_map_btn2)
                            .setItems(labels, (d2, which2) -> {
                                int btn2 = btnCodes.get(which2);
                                // Step 3: Pick target button
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

    // --- Per-app profile helpers ---

    private void populatePerAppProfiles() {
        PreferenceCategory perappCat = findPreference(KEY_PERAPP_CATEGORY);
        if (perappCat == null) return;

        // Remove all dynamically added profile preferences (keep the "add" button)
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

            // Get app label
            String appLabel = pkg;
            try {
                ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                appLabel = pm.getApplicationLabel(ai).toString();
            } catch (PackageManager.NameNotFoundException e) {
                // Use package name as fallback
            }

            // Build summary
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
            final int idx = i;
            pref.setKey("gamepad_perapp_profile_" + i);
            pref.setTitle(appLabel);
            pref.setSummary(summary.toString());
            pref.setOnPreferenceClickListener(this);

            // Insert before the "add" button
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

        // Get launchable apps only (excludes system services without a launcher icon)
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

        // Sort by label
        java.util.Collections.sort(apps, (a, b) -> {
            String la = pm.getApplicationLabel(a).toString();
            String lb = pm.getApplicationLabel(b).toString();
            return la.compareToIgnoreCase(lb);
        });

        // Build dialog with search bar and list
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * context.getResources().getDisplayMetrics().density);
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

            // Check if profile already exists
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

            // Create new profile
            String prefix = "persist.gammaos.gamepad.pa" + count;
            SystemProperties.set(prefix + "_pkg", pkg);
            SystemProperties.set(prefix + "_btn", "");
            SystemProperties.set(prefix + "_combo", "");
            clearPerAppActions(prefix);   // don't inherit a removed profile's stale actions
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

    /** Adapter for the app picker with icon, label, package name, and filtering. */
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
                LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconSize, iconSize);
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
            ((ImageView) row.findViewWithTag("icon")).setImageDrawable(
                    ai.loadIcon(mPm));
            ((TextView) row.findViewWithTag("title")).setText(
                    mPm.getApplicationLabel(ai));
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
                        if (label.contains(query) || ai.packageName.toLowerCase().contains(query)) {
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

        // Show current button remaps
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
                        items.add("Remap: " + fn + " \u2192 " + tn);
                    } catch (NumberFormatException e) {
                        items.add("Remap: " + pair);
                    }
                }
            }
        }

        // Show current combos
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
                        items.add("Combo: " + n1 + "+" + n2 + " \u2192 " + ne);
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
                        // Tap on existing entry to remove it
                        removePerAppEntry(profileIdx, which, btnRemap, comboMap);
                    } else {
                        int actionIdx = which - existingCount;
                        int btnRemapCount = btnRemap.isEmpty() ? 0
                                : btnRemap.split(",").length;
                        int comboCount = comboMap.isEmpty() ? 0
                                : comboMap.split(",").length;

                        if (actionIdx == 0) {
                            // Add button remap
                            showPerAppAddRemapDialog(profileIdx);
                        } else if (actionIdx == 1) {
                            // Add combo
                            showPerAppAddComboDialog(profileIdx);
                        } else if (actionIdx == 2 && !btnRemap.isEmpty()) {
                            // Clear button remaps
                            SystemProperties.set(prefix + "_btn", "");
                            bumpConfigVersion();
                            populatePerAppProfiles();
                        } else if ((actionIdx == 2 && btnRemap.isEmpty()
                                    && !comboMap.isEmpty())
                                || (actionIdx == 3 && !btnRemap.isEmpty()
                                    && !comboMap.isEmpty())) {
                            // Clear combos
                            SystemProperties.set(prefix + "_combo", "");
                            bumpConfigVersion();
                            populatePerAppProfiles();
                        } else {
                            // Remove profile
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
            // Remove button remap at entryIdx
            String[] parts = btnRemap.split(",");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < parts.length; i++) {
                if (i == entryIdx) continue;
                if (sb.length() > 0) sb.append(",");
                sb.append(parts[i]);
            }
            SystemProperties.set(prefix + "_btn", sb.toString());
        } else {
            // Remove combo at (entryIdx - btnCount)
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

        // Shift profiles down to fill the gap
        for (int i = profileIdx; i < count - 1; i++) {
            String srcPrefix = "persist.gammaos.gamepad.pa" + (i + 1);
            String dstPrefix = "persist.gammaos.gamepad.pa" + i;
            SystemProperties.set(dstPrefix + "_pkg",
                    SystemProperties.get(srcPrefix + "_pkg", ""));
            SystemProperties.set(dstPrefix + "_btn",
                    SystemProperties.get(srcPrefix + "_btn", ""));
            SystemProperties.set(dstPrefix + "_combo",
                    SystemProperties.get(srcPrefix + "_combo", ""));
            // The nano editor also stores per-app ACTION rules under this profile
            // (paN_act_count + paN_actM_*). Migrate them alongside the remap/combo,
            // or removing an earlier profile silently strands the moved app's actions.
            movePerAppActions(srcPrefix, dstPrefix);
        }

        // Clear the last slot
        String lastPrefix = "persist.gammaos.gamepad.pa" + (count - 1);
        SystemProperties.set(lastPrefix + "_pkg", "");
        SystemProperties.set(lastPrefix + "_btn", "");
        SystemProperties.set(lastPrefix + "_combo", "");
        clearPerAppActions(lastPrefix);

        SystemProperties.set(PROP_PA_COUNT, String.valueOf(count - 1));
        bumpConfigVersion();
        populatePerAppProfiles();

        Toast.makeText(getContext(), R.string.gamepad_perapp_removed,
                Toast.LENGTH_SHORT).show();
    }

    // Move a profile's per-app action rules (paN_act_count + paN_actM_code/hold/s/l)
    // from srcPrefix to dstPrefix during a profile-list shift, clearing any dst rules
    // left over beyond the moved count so no stale rule lingers. The daemon reads only
    // up to _act_count, so the count must always match the rules actually present.
    private void movePerAppActions(String srcPrefix, String dstPrefix) {
        int srcCount = SystemProperties.getInt(srcPrefix + "_act_count", 0);
        int dstCount = SystemProperties.getInt(dstPrefix + "_act_count", 0);
        for (int m = 0; m < srcCount; m++) {
            String s = srcPrefix + "_act" + m;
            String d = dstPrefix + "_act" + m;
            SystemProperties.set(d + "_code", SystemProperties.get(s + "_code", ""));
            SystemProperties.set(d + "_hold", SystemProperties.get(s + "_hold", ""));
            SystemProperties.set(d + "_s", SystemProperties.get(s + "_s", ""));
            SystemProperties.set(d + "_l", SystemProperties.get(s + "_l", ""));
        }
        for (int m = srcCount; m < dstCount; m++) {
            String d = dstPrefix + "_act" + m;
            SystemProperties.set(d + "_code", "");
            SystemProperties.set(d + "_hold", "");
            SystemProperties.set(d + "_s", "");
            SystemProperties.set(d + "_l", "");
        }
        SystemProperties.set(dstPrefix + "_act_count",
                srcCount > 0 ? String.valueOf(srcCount) : "");
    }

    // Clear all per-app action rules on a profile slot (a freed last slot after a shift,
    // or a slot being reused for a newly added profile).
    private void clearPerAppActions(String prefix) {
        int count = SystemProperties.getInt(prefix + "_act_count", 0);
        for (int m = 0; m < count; m++) {
            String d = prefix + "_act" + m;
            SystemProperties.set(d + "_code", "");
            SystemProperties.set(d + "_hold", "");
            SystemProperties.set(d + "_s", "");
            SystemProperties.set(d + "_l", "");
        }
        SystemProperties.set(prefix + "_act_count", "");
    }

    private void showPerAppAddRemapDialog(int profileIdx) {
        Context context = getContext();
        if (context == null) return;

        List<Integer> btnCodes = new ArrayList<>(BTN_NAMES.keySet());
        java.util.Collections.sort(btnCodes);
        String[] labels = new String[btnCodes.size()];
        for (int i = 0; i < btnCodes.size(); i++) {
            labels[i] = BTN_NAMES.get(btnCodes.get(i));
        }

        String prefix = "persist.gammaos.gamepad.pa" + profileIdx;

        // Pick source button
        new AlertDialog.Builder(context)
                .setTitle(R.string.gamepad_remap_dialog_title)
                .setItems(labels, (d, which) -> {
                    int fromCode = btnCodes.get(which);
                    // Pick target button
                    new AlertDialog.Builder(context)
                            .setTitle(R.string.gamepad_remap_choose_target)
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
                                Toast.makeText(context,
                                        R.string.gamepad_perapp_saved,
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
        java.util.Collections.sort(btnCodes);
        String[] labels = new String[btnCodes.size()];
        for (int i = 0; i < btnCodes.size(); i++) {
            labels[i] = BTN_NAMES.get(btnCodes.get(i));
        }

        String prefix = "persist.gammaos.gamepad.pa" + profileIdx;

        // Step 1: First button
        new AlertDialog.Builder(context)
                .setTitle(R.string.gamepad_combo_map_btn1)
                .setItems(labels, (d, w1) -> {
                    int btn1 = btnCodes.get(w1);
                    // Step 2: Second button
                    new AlertDialog.Builder(context)
                            .setTitle(R.string.gamepad_combo_map_btn2)
                            .setItems(labels, (d2, w2) -> {
                                int btn2 = btnCodes.get(w2);
                                // Step 3: Target button
                                new AlertDialog.Builder(context)
                                        .setTitle(R.string.gamepad_combo_map_target)
                                        .setItems(labels, (d3, w3) -> {
                                            int emit = btnCodes.get(w3);
                                            String current = SystemProperties.get(
                                                    prefix + "_combo", "");
                                            String rule = btn1 + "+" + btn2
                                                    + "=" + emit;
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
                                        .setNegativeButton(
                                                android.R.string.cancel, null)
                                        .show();
                            })
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void bumpConfigVersion() {
        // Full reload: release/re-grab devices, recreate virtual gamepad
        SystemProperties.set("persist.gammaos.gamepad.full_reload", "1");
        int version = SystemProperties.getInt(PROP_CONFIG_VERSION, 0);
        SystemProperties.set(PROP_CONFIG_VERSION, String.valueOf(version + 1));
    }

    // ===================== Custom Button Actions =====================
    // Short/long-press action editor. Rules are act_count + actN_* (name=value:
    // key=<code>, app=<pkg>, act=<pkg/comp>, prop=<n=v>, sh=<cmd>), consumed by
    // the gammapad daemon. AlertDialog chain to match the other pickers here.

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

    // SystemProperties values cap at ~91 bytes; a longer set() throws
    // IllegalArgumentException, which would crash Settings.
    private static final int ACT_SPEC_MAX = 91;

    private void actStore(List<ActRule> rules) {
        int oldN = SystemProperties.getInt(PROP_ACT_COUNT, 0);
        int n = Math.min(rules.size(), 64);
        try {
            for (int i = 0; i < n; i++) {
                String p = "persist.gammaos.gamepad.act" + i;
                ActRule r = rules.get(i);
                SystemProperties.set(p + "_code", String.valueOf(r.code));
                SystemProperties.set(p + "_hold", String.valueOf(r.hold));
                SystemProperties.set(p + "_s", r.s == null ? "" : r.s);
                SystemProperties.set(p + "_l", r.l == null ? "" : r.l);
            }
            for (int i = n; i < oldN && i < 64; i++) {
                String p = "persist.gammaos.gamepad.act" + i;
                SystemProperties.set(p + "_code", "");
                SystemProperties.set(p + "_hold", "");
                SystemProperties.set(p + "_s", "");
                SystemProperties.set(p + "_l", "");
            }
            SystemProperties.set(PROP_ACT_COUNT, String.valueOf(n));
        } catch (RuntimeException e) {
            // Never crash Settings on a property write.
        }
        bumpConfigVersion();
    }

    private ActRule actFind(List<ActRule> rules, int code) {
        for (ActRule r : rules) if (r.code == code) return r;
        return null;
    }

    // Returns false (without storing) if the spec is too long for a system property.
    private boolean actSetSlot(int code, int slot, String spec) {
        if (spec != null && spec.getBytes().length > ACT_SPEC_MAX) return false;
        List<ActRule> rules = actLoad();
        ActRule r = actFind(rules, code);
        if (r == null) { r = new ActRule(); r.code = code; r.hold = 500; rules.add(r); }
        if (slot == 0) r.s = spec; else r.l = spec;
        boolean se = (r.s == null || r.s.isEmpty());
        boolean le = (r.l == null || r.l.isEmpty());
        if (se && le) rules.remove(r);
        actStore(rules);
        return true;
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
        String n = ACT_BTN_NAMES.get(code);
        if (n != null) return n;
        n = ACT_KEY_NAMES.get(code);
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
        final List<Integer> codes = new ArrayList<>(ACT_BTN_NAMES.keySet());
        String[] labels = new String[codes.size()];
        for (int i = 0; i < codes.size(); i++) labels[i] = ACT_BTN_NAMES.get(codes.get(i));
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
        for (Map.Entry<Integer, String> e : ACT_BTN_NAMES.entrySet()) {
            codes.add(e.getKey()); labels.add("Button " + e.getValue());
        }
        for (Map.Entry<Integer, String> e : ACT_KEY_NAMES.entrySet()) {
            codes.add(e.getKey()); labels.add(e.getValue());
        }
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
        HashSet<String> seen = new HashSet<>();
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
                    if (!actSetSlot(code, slot, (perActivity ? "act=" : "app=") + values.get(which))) {
                        Toast.makeText(context, "Component name too long",
                                Toast.LENGTH_SHORT).show();
                    }
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
        int pad = (int) (16 * context.getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, 0);
        final EditText input = new EditText(context);
        input.setHint(isProp ? "name=value" : "shell command");
        layout.addView(input);
        new AlertDialog.Builder(context)
                .setTitle(isProp ? "Set Property" : "Run Shell Command")
                .setView(layout)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String v = input.getText().toString().trim();
                    if (!v.isEmpty() && !actSetSlot(code, slot, (isProp ? "prop=" : "sh=") + v)) {
                        Toast.makeText(context, "Too long (max ~88 characters)",
                                Toast.LENGTH_SHORT).show();
                    }
                    showActionEditDialog(code);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
