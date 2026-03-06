/*
 * GammaOS Gamepad Settings
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.lineageparts.input;

import android.content.Context;
import android.hardware.input.InputManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemProperties;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.app.AlertDialog;
import android.text.InputType;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
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
    private static final String KEY_TEST_VIBRATION = "gamepad_test_vibration";
    private static final String KEY_CLEAR_CALIBRATION = "gamepad_clear_calibration";
    private static final String KEY_BLACKLIST_VPAD = "gamepad_blacklist_vpad";
    private static final String KEY_BLACKLIST_PASS = "gamepad_blacklist_pass";
    private static final String KEY_ABXY_SWAP = "gamepad_abxy_swap";
    private static final String KEY_INVERT_LEFT = "gamepad_invert_left";
    private static final String KEY_INVERT_RIGHT = "gamepad_invert_right";
    private static final String KEY_GLOBAL_SENSITIVITY = "gamepad_global_sensitivity";
    private static final String KEY_TEST = "gamepad_test";

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
        DEVICE_PRESETS.put("xbox_wireless", new String[]{"Xbox Wireless Controller", "045e", "02fd"});
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
    private SwitchPreferenceCompat mAbxySwapPref;
    private SwitchPreferenceCompat mInvertLeftPref;
    private SwitchPreferenceCompat mInvertRightPref;
    private ListPreference mGlobalSensitivityPref;

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

        Preference calibPref = findPreference(KEY_CALIBRATION);
        if (calibPref != null) {
            calibPref.setOnPreferenceClickListener(this);
        }

        Preference clearCalibPref = findPreference(KEY_CLEAR_CALIBRATION);
        if (clearCalibPref != null) {
            clearCalibPref.setOnPreferenceClickListener(this);
        }

        Preference testVibPref = findPreference(KEY_TEST_VIBRATION);
        if (testVibPref != null) {
            testVibPref.setOnPreferenceClickListener(this);
        }

        Preference testPref = findPreference(KEY_TEST);
        if (testPref != null) {
            testPref.setOnPreferenceClickListener(this);
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
            String pid = SystemProperties.get(PROP_DEVICE_PID, "0x02fd")
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
        pidInput.setText(SystemProperties.get(PROP_DEVICE_PID, "0x02fd")
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
            boolean isGamepad =
                    (sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                    || (sources & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK;
            if (!isGamepad) continue;

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
        }

        return false;
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        String key = preference.getKey();

        if (KEY_REMAP_BUTTONS.equals(key)) {
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
        }

        return false;
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

    private void bumpConfigVersion() {
        int version = SystemProperties.getInt(PROP_CONFIG_VERSION, 0);
        SystemProperties.set(PROP_CONFIG_VERSION, String.valueOf(version + 1));
    }
}
