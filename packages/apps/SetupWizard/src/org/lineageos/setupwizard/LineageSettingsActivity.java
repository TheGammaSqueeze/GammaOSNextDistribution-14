package org.lineageos.setupwizard;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemProperties;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.setupcompat.util.WizardManagerHelper;

import java.io.File;
import java.io.RandomAccessFile;

import android.os.Build;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

public class LineageSettingsActivity extends Activity {

    private static final String PROP_RUN = "persist.gammaos.setupwizard_run";
    private static final String PROP_DONE = "persist.gammaos.setupwizard_done";
    private static final String PROP_EXIT_CODE = "persist.gammaos.setupwizard_exit_code";
    private static final long LOG_TAIL_INTERVAL_MS = 250;

    // New GammaPad daemon properties
    private static final String PROP_ENABLE = "persist.gammaos.gamepad.enable";
    private static final String PROP_INVERT_LEFT = "persist.gammaos.gamepad.invert_left";
    private static final String PROP_INVERT_RIGHT = "persist.gammaos.gamepad.invert_right";
    private static final String PROP_DEVICES = "persist.gammaos.gamepad.devices";
    private static final String PROP_CONFIG_VERSION = "persist.gammaos.gamepad.config_version";
    private static final String PROP_CAL_PREFIX = "persist.gammaos.gamepad.cal_axis";

    // ABS codes matching daemon
    private static final int ABS_X = 0x00;
    private static final int ABS_Y = 0x01;
    private static final int ABS_Z = 0x02;
    private static final int ABS_RX = 0x03;
    private static final int ABS_RY = 0x04;
    private static final int ABS_RZ = 0x05;
    private static final int ABS_GAS = 0x09;
    private static final int ABS_BRAKE = 0x0a;

    private static final int[] CAL_AXES = {
            ABS_X, ABS_Y, ABS_RX, ABS_RY, ABS_Z, ABS_RZ, ABS_GAS, ABS_BRAKE
    };

    private enum CalState {
        SETUP,
        CAL_CENTER,
        CAL_LEFT_RANGE,
        CAL_LEFT_ADJUST,
        CAL_RIGHT_RANGE,
        CAL_RIGHT_ADJUST
    }

    private CalState mState = CalState.SETUP;

    private StickTestView leftStickView, rightStickView;
    private ScrollView controlScroll, scrollView;
    private TextView outputTextView, headingTextView;
    private Button continueButton;

    // Calibration UI
    private LinearLayout calibrationArea;
    private TextView calibrationInstruction;
    private TextView calDeadzoneLabel, calSensLabel;
    private SeekBar calDeadzoneSeekbar, calSensSeekbar;
    private Button btnCalContinue, btnCalCancel;

    // Controller chooser
    private LinearLayout controllerList;
    private int mSelectedControllerBtnId = -1;

    // Button groups for highlight
    private final int[] invLGroup = { R.id.btn_invert_left_off, R.id.btn_invert_left_on };
    private final int[] invRGroup = { R.id.btn_invert_right_off, R.id.btn_invert_right_on };

    // Calibration state
    private final String[] mOriginalCalStrings = new String[8];
    private boolean mCalibrationCompleted = false;
    private boolean mDaemonReady = false;
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    // Calibration data
    private int mCenterX, mCenterY, mCenterRX, mCenterRY;
    private int mMinX = Integer.MAX_VALUE, mMaxX = Integer.MIN_VALUE;
    private int mMinY = Integer.MAX_VALUE, mMaxY = Integer.MIN_VALUE;
    private int mMinRX = Integer.MAX_VALUE, mMaxRX = Integer.MIN_VALUE;
    private int mMinRY = Integer.MAX_VALUE, mMaxRY = Integer.MIN_VALUE;
    private int mLeftDeadzone = 4096, mRightDeadzone = 4096;
    private int mLeftSensitivity = 100, mRightSensitivity = 100;
    private int mCurrentDeadzone = 4096, mCurrentSensitivity = 100;

    // Right stick auto-detection
    private float mLastX, mLastY, mLastRX, mLastRY;
    private float mLastRX_std, mLastRY_std;
    private float mLastRX_alt, mLastRY_alt;
    private float mLastRX_z, mLastRY_z;
    private int mCenterRX_std, mCenterRY_std;
    private int mCenterRX_alt, mCenterRY_alt;
    private int mCenterRX_z, mCenterRY_z;
    private int mDetectedAndroidAxisRX = -1;
    private boolean mRightStickDetected = false;

    // Prevent predictive back on Android 13+ (T+) and gesture/virtual back.
    private OnBackInvokedCallback mNoOpBackCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Ensure content extends behind system bars.
        try {
            getWindow().setDecorFitsSystemWindows(false);
        } catch (Throwable ignored) {}
        applyImmersiveLocal();

        // Block BACK (gesture & button) on Android 13+.
        if (Build.VERSION.SDK_INT >= 33) {
            mNoOpBackCallback = () -> { /* no-op */ };
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    OnBackInvokedDispatcher.PRIORITY_DEFAULT, mNoOpBackCallback);
        }

        // Enable gamepad daemon on entry
        SystemProperties.set(PROP_ENABLE, "1");
        bumpConfigVersion();

        setContentView(R.layout.setup_lineage_settings);

        leftStickView = findViewById(R.id.left_stick_view);
        rightStickView = findViewById(R.id.right_stick_view);
        controlScroll = findViewById(R.id.control_scroll);
        scrollView = findViewById(R.id.scrollView);
        outputTextView = findViewById(R.id.script_output_text_view);
        headingTextView = findViewById(R.id.headingTextView);
        continueButton = findViewById(R.id.continue_button);

        // Calibration UI
        calibrationArea = findViewById(R.id.calibration_area);
        calibrationInstruction = findViewById(R.id.calibration_instruction);
        calDeadzoneLabel = findViewById(R.id.calibration_deadzone_label);
        calDeadzoneSeekbar = findViewById(R.id.calibration_deadzone_seekbar);
        calSensLabel = findViewById(R.id.calibration_sensitivity_label);
        calSensSeekbar = findViewById(R.id.calibration_sensitivity_seekbar);
        btnCalContinue = findViewById(R.id.btn_cal_continue);
        btnCalCancel = findViewById(R.id.btn_cal_cancel);

        // Controller chooser
        controllerList = findViewById(R.id.controller_list);
        populateControllerList();

        // Invert sticks
        bindPropToggle(R.id.btn_invert_left_off,  PROP_INVERT_LEFT,  "0", invLGroup);
        bindPropToggle(R.id.btn_invert_left_on,   PROP_INVERT_LEFT,  "1", invLGroup);
        bindPropToggle(R.id.btn_invert_right_off, PROP_INVERT_RIGHT, "0", invRGroup);
        bindPropToggle(R.id.btn_invert_right_on,  PROP_INVERT_RIGHT, "1", invRGroup);

        // Highlight current values from props
        highlightCurrentValues();

        // Calibrate button
        findViewById(R.id.btn_calibrate).setOnClickListener(v -> startCalibration());

        // Calibration controls
        btnCalContinue.setOnClickListener(v -> advanceCalibrationStep());
        btnCalCancel.setOnClickListener(v -> cancelCalibration());

        calDeadzoneSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mCurrentDeadzone = progress;
                calDeadzoneLabel.setText(getString(R.string.gs_deadzone_label, progress));
                if (fromUser) applyDeadzoneImmediately();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        calSensSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mCurrentSensitivity = progress;
                calSensLabel.setText(getString(R.string.gs_sensitivity_label, progress));
                if (fromUser) applySensitivityImmediately();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Continue -> run setup.sh with root, stream output, then advance wizard
        continueButton.setOnClickListener(v -> {
            headingTextView.setText("Configuring GammaOS Next...");
            controlScroll.setVisibility(View.GONE);
            continueButton.setVisibility(View.GONE);
            scrollView.setVisibility(View.VISIBLE);

            outputTextView.setText("");
            File logFile = new File(getFilesDir(), "gammaos_setup.log");
            if (logFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                logFile.delete();
            }
            new RunSetupViaInitTask(logFile).execute();
        });
    }

    private void populateControllerList() {
        controllerList.removeAllViews();

        // "All" button
        Button allBtn = new Button(this);
        allBtn.setText(R.string.gs_all_controllers);
        allBtn.setId(View.generateViewId());
        int allBtnId = allBtn.getId();
        allBtn.setOnClickListener(v -> {
            SystemProperties.set(PROP_DEVICES, "");
            bumpConfigVersion();
            highlightControllerSelection(allBtnId);
        });
        controllerList.addView(allBtn);

        // Enumerate connected gamepads (skip daemon's virtual device)
        String daemonDevName = SystemProperties.get(
                "persist.gammaos.gamepad.device_name", "Xbox Wireless Controller");
        int[] deviceIds = InputDevice.getDeviceIds();
        for (int id : deviceIds) {
            InputDevice dev = InputDevice.getDevice(id);
            if (dev == null) continue;
            if ((dev.getSources() & InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK) {
                continue;
            }
            String name = dev.getName();
            if (name == null || name.isEmpty()) continue;
            // Skip the daemon's virtual gamepad to avoid recursion
            if (name.equals(daemonDevName)) continue;

            Button btn = new Button(this);
            btn.setText(name);
            btn.setId(View.generateViewId());
            int btnId = btn.getId();
            btn.setOnClickListener(v -> {
                SystemProperties.set(PROP_DEVICES, name);
                bumpConfigVersion();
                highlightControllerSelection(btnId);
            });
            controllerList.addView(btn);
        }

        // Highlight based on current prop
        String currentDevices = SystemProperties.get(PROP_DEVICES, "");
        if (currentDevices.isEmpty()) {
            highlightControllerSelection(allBtnId);
        } else {
            // Try to find matching button
            for (int i = 1; i < controllerList.getChildCount(); i++) {
                Button btn = (Button) controllerList.getChildAt(i);
                if (currentDevices.equals(btn.getText().toString())) {
                    highlightControllerSelection(btn.getId());
                    break;
                }
            }
        }
    }

    private void highlightControllerSelection(int selectedId) {
        mSelectedControllerBtnId = selectedId;
        for (int i = 0; i < controllerList.getChildCount(); i++) {
            View child = controllerList.getChildAt(i);
            child.setAlpha(child.getId() == selectedId ? 1f : 0.5f);
        }
    }

    private void highlightCurrentValues() {
        // Invert left
        int invL = SystemProperties.getInt(PROP_INVERT_LEFT, 0);
        highlightSelection(invLGroup, invL == 1 ? R.id.btn_invert_left_on : R.id.btn_invert_left_off);

        // Invert right
        int invR = SystemProperties.getInt(PROP_INVERT_RIGHT, 0);
        highlightSelection(invRGroup, invR == 1 ? R.id.btn_invert_right_on : R.id.btn_invert_right_off);
    }

    // --- Calibration ---

    private void startCalibration() {
        mState = CalState.CAL_CENTER;
        mCalibrationCompleted = false;

        // Save original calibration for restore on cancel
        for (int i = 0; i < CAL_AXES.length; i++) {
            mOriginalCalStrings[i] = SystemProperties.get(
                    PROP_CAL_PREFIX + CAL_AXES[i], "");
        }

        // Load existing deadzone/sensitivity as defaults
        loadExistingCalibrationDefaults();

        // Clear all calibration so daemon passes raw values
        for (int axis : CAL_AXES) {
            SystemProperties.set(PROP_CAL_PREFIX + axis, "");
        }
        bumpConfigVersion();

        // Reset range tracking
        mMinX = Integer.MAX_VALUE; mMaxX = Integer.MIN_VALUE;
        mMinY = Integer.MAX_VALUE; mMaxY = Integer.MIN_VALUE;
        mMinRX = Integer.MAX_VALUE; mMaxRX = Integer.MIN_VALUE;
        mMinRY = Integer.MAX_VALUE; mMaxRY = Integer.MIN_VALUE;
        mRightStickDetected = false;
        mDetectedAndroidAxisRX = -1;

        // Switch UI to calibration mode
        controlScroll.setVisibility(View.GONE);
        continueButton.setVisibility(View.GONE);
        calibrationArea.setVisibility(View.VISIBLE);
        calibrationInstruction.setText(R.string.gs_cal_center);
        hideCalibrationSliders();

        // Wait for daemon to reload with cleared calibration
        mDaemonReady = false;
        btnCalContinue.setEnabled(false);
        mHandler.postDelayed(() -> {
            mDaemonReady = true;
            if (btnCalContinue != null) {
                btnCalContinue.setEnabled(true);
            }
        }, 1500);
    }

    private void loadExistingCalibrationDefaults() {
        String calX = mOriginalCalStrings[0]; // ABS_X
        if (!calX.isEmpty()) {
            String[] parts = calX.split(",");
            if (parts.length >= 5) {
                try {
                    mLeftDeadzone = Integer.parseInt(parts[3]);
                    mLeftSensitivity = Math.round(Float.parseFloat(parts[4]) * 100f);
                } catch (NumberFormatException ignored) {}
            }
        }
        String calRX = mOriginalCalStrings[2]; // ABS_RX
        if (calRX.isEmpty()) calRX = mOriginalCalStrings[4]; // ABS_Z fallback
        if (!calRX.isEmpty()) {
            String[] parts = calRX.split(",");
            if (parts.length >= 5) {
                try {
                    mRightDeadzone = Integer.parseInt(parts[3]);
                    mRightSensitivity = Math.round(Float.parseFloat(parts[4]) * 100f);
                } catch (NumberFormatException ignored) {}
            }
        }
    }

    private void advanceCalibrationStep() {
        switch (mState) {
            case CAL_CENTER:
                if (!mDaemonReady) {
                    Toast.makeText(this, "Waiting for daemon to reload...",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                // Capture resting center
                mCenterX = floatToInt16(mLastX);
                mCenterY = floatToInt16(mLastY);
                mCenterRX_std = floatToInt16(mLastRX_std);
                mCenterRY_std = floatToInt16(mLastRY_std);
                mCenterRX_alt = floatToInt16(mLastRX_alt);
                mCenterRY_alt = floatToInt16(mLastRY_alt);
                mCenterRX_z = floatToInt16(mLastRX_z);
                mCenterRY_z = floatToInt16(mLastRY_z);
                mCenterRX = mCenterRX_std;
                mCenterRY = mCenterRY_std;

                mState = CalState.CAL_LEFT_RANGE;
                calibrationInstruction.setText(R.string.gs_cal_left_range);
                hideCalibrationSliders();
                break;

            case CAL_LEFT_RANGE:
                if (mMinX == Integer.MAX_VALUE) {
                    mMinX = -32768; mMaxX = 32767;
                    mMinY = -32768; mMaxY = 32767;
                }
                float leftSens = mLeftSensitivity / 100f;
                saveAxisCalibration(ABS_X, mCenterX, mMinX, mMaxX, mLeftDeadzone, leftSens);
                saveAxisCalibration(ABS_Y, mCenterY, mMinY, mMaxY, mLeftDeadzone, leftSens);
                bumpConfigVersion();

                mState = CalState.CAL_LEFT_ADJUST;
                calibrationInstruction.setText(R.string.gs_cal_left_adjust);
                mCurrentDeadzone = mLeftDeadzone;
                mCurrentSensitivity = mLeftSensitivity;
                showCalibrationSliders();
                break;

            case CAL_LEFT_ADJUST:
                mLeftDeadzone = mCurrentDeadzone;
                mLeftSensitivity = mCurrentSensitivity;
                // Save final left stick cal
                float lSens = mLeftSensitivity / 100f;
                saveAxisCalibration(ABS_X, mCenterX, mMinX, mMaxX, mLeftDeadzone, lSens);
                saveAxisCalibration(ABS_Y, mCenterY, mMinY, mMaxY, mLeftDeadzone, lSens);
                bumpConfigVersion();

                mState = CalState.CAL_RIGHT_RANGE;
                calibrationInstruction.setText(R.string.gs_cal_right_range);
                hideCalibrationSliders();
                break;

            case CAL_RIGHT_RANGE:
                if (mMinRX == Integer.MAX_VALUE) {
                    mMinRX = -32768; mMaxRX = 32767;
                    mMinRY = -32768; mMaxRY = 32767;
                }
                float rightSens = mRightSensitivity / 100f;
                saveAxisCalibration(getRightStickAbsX(), mCenterRX, mMinRX, mMaxRX,
                        mRightDeadzone, rightSens);
                saveAxisCalibration(getRightStickAbsY(), mCenterRY, mMinRY, mMaxRY,
                        mRightDeadzone, rightSens);
                bumpConfigVersion();

                mState = CalState.CAL_RIGHT_ADJUST;
                calibrationInstruction.setText(R.string.gs_cal_right_adjust);
                mCurrentDeadzone = mRightDeadzone;
                mCurrentSensitivity = mRightSensitivity;
                showCalibrationSliders();
                break;

            case CAL_RIGHT_ADJUST:
                mRightDeadzone = mCurrentDeadzone;
                mRightSensitivity = mCurrentSensitivity;
                // Save final right stick cal
                float rSens = mRightSensitivity / 100f;
                saveAxisCalibration(getRightStickAbsX(), mCenterRX, mMinRX, mMaxRX,
                        mRightDeadzone, rSens);
                saveAxisCalibration(getRightStickAbsY(), mCenterRY, mMinRY, mMaxRY,
                        mRightDeadzone, rSens);
                bumpConfigVersion();

                // Done
                mCalibrationCompleted = true;
                mState = CalState.SETUP;
                Toast.makeText(this, R.string.gs_cal_done, Toast.LENGTH_SHORT).show();
                calibrationArea.setVisibility(View.GONE);
                controlScroll.setVisibility(View.VISIBLE);
                continueButton.setVisibility(View.VISIBLE);
                break;
        }
    }

    private void cancelCalibration() {
        // Restore original calibration
        for (int i = 0; i < CAL_AXES.length; i++) {
            SystemProperties.set(PROP_CAL_PREFIX + CAL_AXES[i], mOriginalCalStrings[i]);
        }
        bumpConfigVersion();

        mState = CalState.SETUP;
        calibrationArea.setVisibility(View.GONE);
        controlScroll.setVisibility(View.VISIBLE);
        continueButton.setVisibility(View.VISIBLE);
    }

    private void showCalibrationSliders() {
        calDeadzoneLabel.setVisibility(View.VISIBLE);
        calDeadzoneSeekbar.setVisibility(View.VISIBLE);
        calSensLabel.setVisibility(View.VISIBLE);
        calSensSeekbar.setVisibility(View.VISIBLE);

        calDeadzoneSeekbar.setProgress(mCurrentDeadzone);
        calDeadzoneLabel.setText(getString(R.string.gs_deadzone_label, mCurrentDeadzone));
        calSensSeekbar.setProgress(mCurrentSensitivity);
        calSensLabel.setText(getString(R.string.gs_sensitivity_label, mCurrentSensitivity));
    }

    private void hideCalibrationSliders() {
        calDeadzoneLabel.setVisibility(View.GONE);
        calDeadzoneSeekbar.setVisibility(View.GONE);
        calSensLabel.setVisibility(View.GONE);
        calSensSeekbar.setVisibility(View.GONE);
    }

    private void applyDeadzoneImmediately() {
        if (mState == CalState.CAL_LEFT_ADJUST) {
            mLeftDeadzone = mCurrentDeadzone;
            float sens = mLeftSensitivity / 100f;
            saveAxisCalibration(ABS_X, mCenterX, mMinX, mMaxX, mLeftDeadzone, sens);
            saveAxisCalibration(ABS_Y, mCenterY, mMinY, mMaxY, mLeftDeadzone, sens);
        } else if (mState == CalState.CAL_RIGHT_ADJUST) {
            mRightDeadzone = mCurrentDeadzone;
            float sens = mRightSensitivity / 100f;
            saveAxisCalibration(getRightStickAbsX(), mCenterRX, mMinRX, mMaxRX,
                    mRightDeadzone, sens);
            saveAxisCalibration(getRightStickAbsY(), mCenterRY, mMinRY, mMaxRY,
                    mRightDeadzone, sens);
        }
        bumpConfigVersion();
    }

    private void applySensitivityImmediately() {
        if (mState == CalState.CAL_LEFT_ADJUST) {
            mLeftSensitivity = mCurrentSensitivity;
            float sens = mLeftSensitivity / 100f;
            saveAxisCalibration(ABS_X, mCenterX, mMinX, mMaxX, mLeftDeadzone, sens);
            saveAxisCalibration(ABS_Y, mCenterY, mMinY, mMaxY, mLeftDeadzone, sens);
        } else if (mState == CalState.CAL_RIGHT_ADJUST) {
            mRightSensitivity = mCurrentSensitivity;
            float sens = mRightSensitivity / 100f;
            saveAxisCalibration(getRightStickAbsX(), mCenterRX, mMinRX, mMaxRX,
                    mRightDeadzone, sens);
            saveAxisCalibration(getRightStickAbsY(), mCenterRY, mMinRY, mMaxRY,
                    mRightDeadzone, sens);
        }
        bumpConfigVersion();
    }

    // --- Right stick detection ---

    private void detectRightStick() {
        if (mRightStickDetected) return;

        float magStd = mLastRX_std * mLastRX_std + mLastRY_std * mLastRY_std;
        float magAlt = mLastRX_alt * mLastRX_alt + mLastRY_alt * mLastRY_alt;
        float magZ = mLastRX_z * mLastRX_z + mLastRY_z * mLastRY_z;

        float maxMag = Math.max(magStd, Math.max(magAlt, magZ));
        if (maxMag < 0.04f) return;

        if (maxMag == magZ && magZ > 0.04f) {
            mDetectedAndroidAxisRX = 0;
            mCenterRX = mCenterRX_z;
            mCenterRY = mCenterRY_z;
            mRightStickDetected = true;
            mLastRX = mLastRX_z;
            mLastRY = mLastRY_z;
        } else if (maxMag == magAlt && magAlt > 0.04f) {
            mDetectedAndroidAxisRX = 1;
            mCenterRX = mCenterRX_alt;
            mCenterRY = mCenterRY_alt;
            mRightStickDetected = true;
            mLastRX = mLastRX_alt;
            mLastRY = mLastRY_alt;
        } else if (magStd > 0.04f) {
            mDetectedAndroidAxisRX = 2;
            mCenterRX = mCenterRX_std;
            mCenterRY = mCenterRY_std;
            mRightStickDetected = true;
            mLastRX = mLastRX_std;
            mLastRY = mLastRY_std;
        }
    }

    private int getRightStickAbsX() {
        return isXboxWirelessPreset() ? ABS_Z : ABS_RX;
    }

    private int getRightStickAbsY() {
        return isXboxWirelessPreset() ? ABS_RZ : ABS_RY;
    }

    private boolean isXboxWirelessPreset() {
        String pid = SystemProperties.get("persist.gammaos.gamepad.device_pid", "0x02fd")
                .replace("0x", "").toLowerCase();
        return "02fd".equals(pid) || "2fd".equals(pid);
    }

    // --- Utility ---

    private static int floatToInt16(float value) {
        return Math.round(((value + 1f) / 2f) * 65535f - 32768f);
    }

    private void trackRange(float x, float y, boolean isLeft) {
        int ix = floatToInt16(x);
        int iy = floatToInt16(y);
        if (isLeft) {
            mMinX = Math.min(mMinX, ix); mMaxX = Math.max(mMaxX, ix);
            mMinY = Math.min(mMinY, iy); mMaxY = Math.max(mMaxY, iy);
        } else {
            mMinRX = Math.min(mMinRX, ix); mMaxRX = Math.max(mMaxRX, ix);
            mMinRY = Math.min(mMinRY, iy); mMaxRY = Math.max(mMaxRY, iy);
        }
    }

    private void saveAxisCalibration(int axis, int center, int min, int max,
            int deadzone, float sensitivity) {
        String value = center + "," + min + "," + max + ","
                + deadzone + "," + sensitivity + ",0";
        SystemProperties.set(PROP_CAL_PREFIX + axis, value);
    }

    private void bumpConfigVersion() {
        int version = SystemProperties.getInt(PROP_CONFIG_VERSION, 0);
        SystemProperties.set(PROP_CONFIG_VERSION, String.valueOf(version + 1));
    }

    private void bindPropToggle(int btnId, String prop, String value, int[] group) {
        Button b = findViewById(btnId);
        b.setOnClickListener(v -> {
            SystemProperties.set(prop, value);
            bumpConfigVersion();
            highlightSelection(group, btnId);
        });
    }

    private void highlightSelection(int[] group, int selId) {
        for (int id : group) {
            View v = findViewById(id);
            if (v != null) v.setAlpha(id == selId ? 1f : 0.5f);
        }
    }

    private static float applyDisplayDeadzone(float value, float deadzone) {
        if (deadzone <= 0f) return value;
        float absVal = Math.abs(value);
        if (absVal < deadzone) return 0f;
        float sign = value > 0 ? 1f : -1f;
        return sign * (absVal - deadzone) / (1f - deadzone);
    }

    private static float clamp(float v) {
        return Math.max(-1f, Math.min(1f, v));
    }

    // --- Lifecycle ---

    @Override
    protected void onDestroy() {
        if (Build.VERSION.SDK_INT >= 33 && mNoOpBackCallback != null) {
            try {
                getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(mNoOpBackCallback);
            } catch (Throwable ignored) {}
        }
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyImmersiveLocal();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyImmersiveLocal();
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        applyImmersiveLocal();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        final int key = event.getKeyCode();
        if (key == KeyEvent.KEYCODE_BACK || key == KeyEvent.KEYCODE_BUTTON_B) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        // Block programmatic/system back as well.
    }

    private void applyImmersiveLocal() {
        final android.view.View decor = getWindow().getDecorView();
        try {
            final WindowInsetsController ic = decor.getWindowInsetsController();
            if (ic != null) {
                ic.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                ic.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } catch (Throwable ignored) {}
        final int flags = android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                | android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        decor.setSystemUiVisibility(flags);
    }

    // --- Motion events ---

    @Override
    public boolean onGenericMotionEvent(MotionEvent ev) {
        if ((ev.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
                && ev.getAction() == MotionEvent.ACTION_MOVE) {

            mLastX = ev.getAxisValue(MotionEvent.AXIS_X);
            mLastY = ev.getAxisValue(MotionEvent.AXIS_Y);

            // Read all right stick candidates
            mLastRX_std = ev.getAxisValue(MotionEvent.AXIS_RX);
            mLastRY_std = ev.getAxisValue(MotionEvent.AXIS_RY);
            mLastRX_alt = ev.getAxisValue(MotionEvent.AXIS_GAS);
            mLastRY_alt = ev.getAxisValue(MotionEvent.AXIS_BRAKE);
            mLastRX_z = ev.getAxisValue(MotionEvent.AXIS_Z);
            mLastRY_z = ev.getAxisValue(MotionEvent.AXIS_RZ);

            // Use detected pair or default
            if (mRightStickDetected && mDetectedAndroidAxisRX == 0) {
                mLastRX = mLastRX_z; mLastRY = mLastRY_z;
            } else if (mRightStickDetected && mDetectedAndroidAxisRX == 1) {
                mLastRX = mLastRX_alt; mLastRY = mLastRY_alt;
            } else {
                mLastRX = mLastRX_std; mLastRY = mLastRY_std;
            }

            // Update stick views
            if (leftStickView != null) {
                if (mState == CalState.CAL_LEFT_ADJUST) {
                    float lx = applyDisplayDeadzone(mLastX, mCurrentDeadzone / 32767f);
                    float ly = applyDisplayDeadzone(mLastY, mCurrentDeadzone / 32767f);
                    float ls = mCurrentSensitivity / 100f;
                    leftStickView.updateAxes(clamp(lx * ls), clamp(ly * ls));
                } else {
                    leftStickView.updateAxes(mLastX, mLastY);
                }
            }
            if (rightStickView != null) {
                if (mState == CalState.CAL_RIGHT_ADJUST) {
                    float rx = applyDisplayDeadzone(mLastRX, mCurrentDeadzone / 32767f);
                    float ry = applyDisplayDeadzone(mLastRY, mCurrentDeadzone / 32767f);
                    float rs = mCurrentSensitivity / 100f;
                    rightStickView.updateAxes(clamp(rx * rs), clamp(ry * rs));
                } else {
                    rightStickView.updateAxes(mLastRX, mLastRY);
                }
            }

            // Calibration-specific tracking
            switch (mState) {
                case CAL_LEFT_RANGE:
                    trackRange(mLastX, mLastY, true);
                    break;
                case CAL_RIGHT_RANGE:
                    detectRightStick();
                    trackRange(mLastRX, mLastRY, false);
                    break;
            }

            return true;
        }
        return super.onGenericMotionEvent(ev);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent ev) {
        if (keyCode == KeyEvent.KEYCODE_BUTTON_THUMBL ||
            keyCode == KeyEvent.KEYCODE_BUTTON_THUMBR) {
            return true;
        }
        return super.onKeyDown(keyCode, ev);
    }

    // --- Setup script ---

    private class RunSetupViaInitTask extends AsyncTask<Void, String, Integer> {
        private final File logFile;

        RunSetupViaInitTask(File logFile) {
            this.logFile = logFile;
        }

        @Override
        protected void onPreExecute() {
            SystemProperties.set(PROP_DONE, "0");
            SystemProperties.set(PROP_EXIT_CODE, "0");
            SystemProperties.set(PROP_RUN, "0");
            SystemProperties.set(PROP_RUN, "1");
        }

        @Override
        protected Integer doInBackground(Void... ignored) {
            long pos = 0;
            try {
                while (true) {
                    if (logFile.exists()) {
                        long len = logFile.length();
                        if (len < pos) pos = 0;

                        if (len > pos) {
                            RandomAccessFile raf = new RandomAccessFile(logFile, "r");
                            raf.seek(pos);
                            String line;
                            while ((line = raf.readLine()) != null) {
                                publishProgress(line + "\n");
                            }
                            pos = raf.getFilePointer();
                            raf.close();
                        }
                    }

                    if ("1".equals(SystemProperties.get(PROP_DONE, "0"))) break;

                    try {
                        Thread.sleep(LOG_TAIL_INTERVAL_MS);
                    } catch (InterruptedException ignoredSleep) {}
                }
            } catch (Exception e) {
                publishProgress("Error: " + e.getMessage() + "\n");
            }

            String exitStr = SystemProperties.get(PROP_EXIT_CODE, "0");
            try {
                return Integer.parseInt(exitStr);
            } catch (NumberFormatException ignoredNum) {
                return 0;
            }
        }

        @Override
        protected void onProgressUpdate(String... vals) {
            outputTextView.append(vals[0]);
            scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
        }

        @Override
        protected void onPostExecute(Integer exitCode) {
            outputTextView.append("Script completed (exit " + exitCode + ").\n");
            outputTextView.postDelayed(() -> {
                Intent intent = WizardManagerHelper.getNextIntent(getIntent(), Activity.RESULT_OK);
                startActivity(intent);
                finish();
            }, 5000);
        }
    }
}
