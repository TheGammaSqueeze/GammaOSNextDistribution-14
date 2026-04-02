/*
 * GammaOS Gamepad Stick Calibration Dialog
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.lineageparts.input;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemProperties;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.view.Gravity;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;

import org.lineageos.lineageparts.R;

public class GamepadCalibrationDialogFragment extends DialogFragment {

    private static final String PROP_CAL_PREFIX = "persist.gammaos.gamepad.cal_axis";
    private static final String PROP_CONFIG_VERSION = "persist.gammaos.gamepad.config_version";

    private static final int ABS_X = 0x00;
    private static final int ABS_Y = 0x01;
    private static final int ABS_Z = 0x02;
    private static final int ABS_RX = 0x03;
    private static final int ABS_RY = 0x04;
    private static final int ABS_RZ = 0x05;
    private static final int ABS_GAS = 0x09;
    private static final int ABS_BRAKE = 0x0a;

    private enum Step {
        CENTER,
        LEFT_RANGE,
        LEFT_ADJUST,
        RIGHT_RANGE,
        RIGHT_ADJUST,
        TRIGGERS,
        DONE
    }

    private Step mCurrentStep = Step.CENTER;
    private TextView mTitleText;
    private TextView mInstructionText;
    private Button mContinueButton;
    private Button mCancelButton;

    // Stick views
    private AnalogStickView mStickView;
    private LinearLayout mStickControlsContainer;

    // Trigger views
    private LinearLayout mTriggerContainer;
    private TriggerBarView mLeftTriggerBar;
    private TriggerBarView mRightTriggerBar;
    private TextView mLeftTriggerLabel;
    private TextView mRightTriggerLabel;

    // Deadzone + sensitivity controls
    private TextView mDeadzoneLabel;
    private SeekBar mDeadzoneSeekbar;
    private TextView mSensLabel;
    private SeekBar mSensSeekbar;

    // Trigger deadzone controls
    private TextView mTriggerDeadzoneLabel;
    private SeekBar mTriggerDeadzoneSeekbar;

    // Calibration values
    private int mCenterX, mCenterY, mCenterRX, mCenterRY;
    private int mMinX = Integer.MAX_VALUE, mMaxX = Integer.MIN_VALUE;
    private int mMinY = Integer.MAX_VALUE, mMaxY = Integer.MIN_VALUE;
    private int mMinRX = Integer.MAX_VALUE, mMaxRX = Integer.MIN_VALUE;
    private int mMinRY = Integer.MAX_VALUE, mMaxRY = Integer.MIN_VALUE;
    private int mLeftDeadzone = 4096;
    private int mRightDeadzone = 4096;
    private int mTriggerDeadzone = 1024;
    private int mLeftSensitivity = 100;
    private int mRightSensitivity = 100;

    // Trigger range capture (tracked during TRIGGERS step)
    private int mMinLT = Integer.MAX_VALUE, mMaxLT = Integer.MIN_VALUE;
    private int mMinRT = Integer.MAX_VALUE, mMaxRT = Integer.MIN_VALUE;

    // Current stick's working values
    private int mCurrentDeadzone = 4096;
    private int mCurrentSensitivity = 100;

    private float mLastX, mLastY, mLastRX, mLastRY;
    private float mLastLT, mLastRT;

    // Right stick auto-detection: read from multiple Android AXIS candidate pairs.
    // Regardless of which AXIS pair shows movement, calibration is always saved
    // to the daemon's fixed ABS_RX/ABS_RY codes since the daemon normalizes
    // all right stick data to those codes before outputting.
    private float mLastRX_std, mLastRY_std;  // from AXIS_RX/AXIS_RY
    private float mLastRX_alt, mLastRY_alt;  // from AXIS_GAS/AXIS_BRAKE
    private float mLastRX_z, mLastRY_z;      // from AXIS_Z/AXIS_RZ (Xbox .kl puts right stick here)
    private int mCenterRX_std, mCenterRY_std;
    private int mCenterRX_alt, mCenterRY_alt;
    private int mCenterRX_z, mCenterRY_z;
    // Which Android AXIS pair has the right stick data (depends on .kl applied to virtual pad)
    private int mDetectedAndroidAxisRX = -1;
    private boolean mRightStickDetected = false;

    // Saved original calibration strings for restore on cancel
    private final String[] mOriginalCalStrings = new String[8];
    private static final int[] CAL_AXES = {
            ABS_X, ABS_Y, ABS_RX, ABS_RY, ABS_Z, ABS_RZ, ABS_GAS, ABS_BRAKE
    };
    private boolean mCalibrationCompleted = false;
    private boolean mDaemonReady = false;
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NO_FRAME, android.R.style.Theme_DeviceDefault_NoActionBar);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Use custom Dialog to intercept joystick events at the Dialog level.
        // This ensures motion events are captured regardless of which child
        // view has focus (e.g., after DPAD-navigating to a button).
        Dialog dialog = new Dialog(requireContext(), getTheme()) {
            @Override
            public boolean dispatchGenericMotionEvent(MotionEvent event) {
                if ((event.getSource() & InputDevice.SOURCE_JOYSTICK) != 0) {
                    onJoystickMotion(event);
                    return true;
                }
                return super.dispatchGenericMotionEvent(event);
            }
        };
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null && dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT);
            dialog.getWindow().setBackgroundDrawableResource(
                    android.R.color.background_dark);
        }
    }

    @Override
    public View onCreateView(@NonNull android.view.LayoutInflater inflater,
            android.view.ViewGroup container, Bundle savedInstanceState) {
        Context ctx = requireContext();

        // Load existing calibration values
        loadExistingCalibration();

        // Root layout - full screen vertical
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        root.setPadding(64, 48, 64, 48);

        // Title
        mTitleText = new TextView(ctx);
        mTitleText.setTextSize(24);
        mTitleText.setText(R.string.gamepad_calibration_title);
        mTitleText.setTextColor(0xFFFFFFFF);
        root.addView(mTitleText, matchWrap());

        // Instruction text
        mInstructionText = new TextView(ctx);
        mInstructionText.setTextSize(16);
        mInstructionText.setText(R.string.gamepad_calibration_step_center);
        mInstructionText.setTextColor(0xFFCCCCCC);
        LinearLayout.LayoutParams instrParams = matchWrap();
        instrParams.topMargin = 16;
        root.addView(mInstructionText, instrParams);

        // Middle area: stick view + controls (weight=1 to fill space)
        LinearLayout middleArea = new LinearLayout(ctx);
        middleArea.setOrientation(LinearLayout.HORIZONTAL);
        middleArea.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams middleParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        middleParams.topMargin = 16;
        root.addView(middleArea, middleParams);

        // Left side: analog stick view (square, as large as possible)
        mStickView = new AnalogStickView(ctx);
        mStickView.setVisibility(View.VISIBLE);
        LinearLayout.LayoutParams stickParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        stickParams.rightMargin = 32;
        middleArea.addView(mStickView, stickParams);

        // Right side: controls column (hidden during range capture)
        mStickControlsContainer = new LinearLayout(ctx);
        mStickControlsContainer.setOrientation(LinearLayout.VERTICAL);
        mStickControlsContainer.setGravity(Gravity.CENTER_VERTICAL);
        mStickControlsContainer.setVisibility(View.GONE);
        LinearLayout.LayoutParams controlsParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        middleArea.addView(mStickControlsContainer, controlsParams);

        // Deadzone controls
        mDeadzoneLabel = new TextView(ctx);
        mDeadzoneLabel.setTextSize(14);
        mDeadzoneLabel.setTextColor(0xFFCCCCCC);
        mStickControlsContainer.addView(mDeadzoneLabel, matchWrap());

        mDeadzoneSeekbar = new SeekBar(ctx);
        mDeadzoneSeekbar.setMax(50); // 0-50%
        mDeadzoneSeekbar.setProgress(deadzoneToPercent(mCurrentDeadzone));
        LinearLayout.LayoutParams seekParams = matchWrap();
        seekParams.topMargin = 8;
        mStickControlsContainer.addView(mDeadzoneSeekbar, seekParams);

        mDeadzoneSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mCurrentDeadzone = percentToDeadzone(progress);
                mDeadzoneLabel.setText(getString(
                        R.string.gamepad_calibration_deadzone_label, progress));
                if (mStickView.getVisibility() == View.VISIBLE) {
                    mStickView.setDeadzone(mCurrentDeadzone / 32767f);
                }
                if (fromUser) {
                    applyDeadzoneImmediately();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Sensitivity controls
        mSensLabel = new TextView(ctx);
        mSensLabel.setTextSize(14);
        mSensLabel.setTextColor(0xFFCCCCCC);
        LinearLayout.LayoutParams sensLabelParams = matchWrap();
        sensLabelParams.topMargin = 24;
        mStickControlsContainer.addView(mSensLabel, sensLabelParams);

        mSensSeekbar = new SeekBar(ctx);
        mSensSeekbar.setMax(200);
        mSensSeekbar.setProgress(100);
        LinearLayout.LayoutParams sensSeekParams = matchWrap();
        sensSeekParams.topMargin = 8;
        mStickControlsContainer.addView(mSensSeekbar, sensSeekParams);

        mSensSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mCurrentSensitivity = progress;
                mSensLabel.setText(getString(
                        R.string.gamepad_calibration_sensitivity_label, progress));
                if (fromUser) {
                    applySensitivityImmediately();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Trigger container (hidden until trigger step)
        mTriggerContainer = new LinearLayout(ctx);
        mTriggerContainer.setOrientation(LinearLayout.VERTICAL);
        mTriggerContainer.setVisibility(View.GONE);
        mTriggerContainer.setGravity(Gravity.CENTER);
        middleArea.addView(mTriggerContainer, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT));

        // LT bar
        mLeftTriggerLabel = new TextView(ctx);
        mLeftTriggerLabel.setText(R.string.gamepad_test_left_trigger);
        mLeftTriggerLabel.setTextSize(16);
        mLeftTriggerLabel.setTextColor(0xFFCCCCCC);
        mTriggerContainer.addView(mLeftTriggerLabel, matchWrap());

        mLeftTriggerBar = new TriggerBarView(ctx);
        LinearLayout.LayoutParams ltBarParams = matchWrap();
        ltBarParams.topMargin = 8;
        mTriggerContainer.addView(mLeftTriggerBar, ltBarParams);

        // RT bar
        mRightTriggerLabel = new TextView(ctx);
        mRightTriggerLabel.setText(R.string.gamepad_test_right_trigger);
        mRightTriggerLabel.setTextSize(16);
        mRightTriggerLabel.setTextColor(0xFFCCCCCC);
        LinearLayout.LayoutParams rtLabelParams = matchWrap();
        rtLabelParams.topMargin = 24;
        mTriggerContainer.addView(mRightTriggerLabel, rtLabelParams);

        mRightTriggerBar = new TriggerBarView(ctx);
        LinearLayout.LayoutParams rtBarParams = matchWrap();
        rtBarParams.topMargin = 8;
        mTriggerContainer.addView(mRightTriggerBar, rtBarParams);

        // Trigger deadzone
        mTriggerDeadzoneLabel = new TextView(ctx);
        mTriggerDeadzoneLabel.setTextSize(14);
        mTriggerDeadzoneLabel.setTextColor(0xFFCCCCCC);
        LinearLayout.LayoutParams trigDzLabelParams = matchWrap();
        trigDzLabelParams.topMargin = 32;
        mTriggerContainer.addView(mTriggerDeadzoneLabel, trigDzLabelParams);

        mTriggerDeadzoneSeekbar = new SeekBar(ctx);
        mTriggerDeadzoneSeekbar.setMax(50); // 0-50%
        mTriggerDeadzoneSeekbar.setProgress(deadzoneToPercent(mTriggerDeadzone));
        LinearLayout.LayoutParams trigDzSeekParams = matchWrap();
        trigDzSeekParams.topMargin = 8;
        mTriggerContainer.addView(mTriggerDeadzoneSeekbar, trigDzSeekParams);

        mTriggerDeadzoneSeekbar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                mTriggerDeadzone = percentToDeadzone(progress);
                mTriggerDeadzoneLabel.setText(getString(
                        R.string.gamepad_calibration_deadzone_label, progress));
                if (fromUser) {
                    applyTriggerDeadzoneImmediately();
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Bottom button row
        LinearLayout buttonRow = new LinearLayout(ctx);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams buttonRowParams = matchWrap();
        buttonRowParams.topMargin = 24;
        root.addView(buttonRow, buttonRowParams);

        mCancelButton = new Button(ctx);
        mCancelButton.setText(R.string.cancel);
        mCancelButton.setOnClickListener(v -> dismiss());
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cancelParams.rightMargin = 32;
        buttonRow.addView(mCancelButton, cancelParams);

        mContinueButton = new Button(ctx);
        mContinueButton.setText(R.string.gamepad_calibration_continue);
        mContinueButton.setOnClickListener(v -> advanceStep());
        mContinueButton.setEnabled(mDaemonReady);
        buttonRow.addView(mContinueButton);

        // Keep focusable for DPAD key navigation
        root.setFocusable(true);
        root.setFocusableInTouchMode(true);
        root.requestFocus();

        return root;
    }

    private static float clamp(float v) {
        return Math.max(-1f, Math.min(1f, v));
    }

    /** Convert Android's normalized [-1,1] float back to daemon's signed 16-bit range. */
    private static int floatToInt16(float value) {
        return Math.round(((value + 1f) / 2f) * 65535f - 32768f);
    }

    /** Called from Dialog-level dispatchGenericMotionEvent for joystick events. */
    private void onJoystickMotion(MotionEvent event) {
        mLastX = event.getAxisValue(MotionEvent.AXIS_X);
        mLastY = event.getAxisValue(MotionEvent.AXIS_Y);

        // Read all right stick candidates for auto-detection
        mLastRX_std = event.getAxisValue(MotionEvent.AXIS_RX);
        mLastRY_std = event.getAxisValue(MotionEvent.AXIS_RY);
        mLastRX_alt = event.getAxisValue(MotionEvent.AXIS_GAS);
        mLastRY_alt = event.getAxisValue(MotionEvent.AXIS_BRAKE);
        mLastRX_z = event.getAxisValue(MotionEvent.AXIS_Z);
        mLastRY_z = event.getAxisValue(MotionEvent.AXIS_RZ);

        // Use detected right stick pair (default: AXIS_RX/AXIS_RY)
        if (mRightStickDetected && mDetectedAndroidAxisRX == 1) {
            mLastRX = mLastRX_alt; // GAS/BRAKE
            mLastRY = mLastRY_alt;
        } else if (mRightStickDetected && mDetectedAndroidAxisRX == 0) {
            mLastRX = mLastRX_z; // Z/RZ
            mLastRY = mLastRY_z;
        } else {
            mLastRX = mLastRX_std; // RX/RY
            mLastRY = mLastRY_std;
        }

        // Read triggers from all possible Android AXIS sources.
        // The .kl applied to the virtual pad determines which AXIS has trigger data:
        //   Xbox .kl: AXIS_LTRIGGER/AXIS_RTRIGGER
        //   Generic .kl: AXIS_Z/AXIS_RZ
        //   Other: AXIS_GAS/AXIS_BRAKE
        // We take the max from all non-right-stick sources, using readTriggerAxis()
        // to correctly handle both unipolar and bipolar axis ranges.
        float ltrigger = readTriggerAxis(event, MotionEvent.AXIS_LTRIGGER);
        float rtrigger = readTriggerAxis(event, MotionEvent.AXIS_RTRIGGER);
        mLastLT = ltrigger;
        mLastRT = rtrigger;

        // Also check AXIS_Z/RZ unless they're detected as the right stick
        if (mDetectedAndroidAxisRX != 0) {
            float axisZ = readTriggerAxis(event, MotionEvent.AXIS_Z);
            float axisRZ = readTriggerAxis(event, MotionEvent.AXIS_RZ);
            mLastLT = Math.max(mLastLT, axisZ);
            mLastRT = Math.max(mLastRT, axisRZ);
        }

        // Also check AXIS_GAS/BRAKE unless they're detected as the right stick
        if (mDetectedAndroidAxisRX != 1) {
            float axisBrake = readTriggerAxis(event, MotionEvent.AXIS_BRAKE);
            float axisGas = readTriggerAxis(event, MotionEvent.AXIS_GAS);
            mLastLT = Math.max(mLastLT, axisBrake);
            mLastRT = Math.max(mLastRT, axisGas);
        }

        switch (mCurrentStep) {
            case CENTER:
                // Show stick position for visual confirmation events are flowing
                if (mStickView != null) {
                    mStickView.setPosition(mLastX, mLastY);
                }
                break;
            case LEFT_RANGE:
                trackRange(mLastX, mLastY, true);
                mStickView.setPosition(mLastX, mLastY);
                break;
            case LEFT_ADJUST: {
                // Daemon has center+range+sensitivity but deadzone=0 during ADJUST.
                // UI applies circular deadzone: zero inside, pass through outside.
                // The dot snaps to center inside the red circle and appears at
                // the circle edge when the stick escapes the deadzone.
                float lDz = mCurrentDeadzone / 32767f;
                float lMag = (float) Math.sqrt(mLastX * mLastX + mLastY * mLastY);
                if (lMag < lDz) {
                    mStickView.setPosition(0f, 0f);
                } else {
                    mStickView.setPosition(clamp(mLastX), clamp(mLastY));
                }
                break;
            }
            case RIGHT_RANGE:
                detectRightStick();
                trackRange(mLastRX, mLastRY, false);
                mStickView.setPosition(mLastRX, mLastRY);
                break;
            case RIGHT_ADJUST: {
                float rDz = mCurrentDeadzone / 32767f;
                float rMag = (float) Math.sqrt(mLastRX * mLastRX + mLastRY * mLastRY);
                if (rMag < rDz) {
                    mStickView.setPosition(0f, 0f);
                } else {
                    mStickView.setPosition(clamp(mLastRX), clamp(mLastRY));
                }
                break;
            }
            case TRIGGERS: {
                mLeftTriggerBar.setValue(mLastLT);
                mRightTriggerBar.setValue(mLastRT);
                // Track trigger range during capture
                int ltVal = Math.round(mLastLT * 32767f);
                int rtVal = Math.round(mLastRT * 32767f);
                mMinLT = Math.min(mMinLT, ltVal);
                mMaxLT = Math.max(mMaxLT, ltVal);
                mMinRT = Math.min(mMinRT, rtVal);
                mMaxRT = Math.max(mMaxRT, rtVal);
                break;
            }
        }
    }

    /**
     * Auto-detect which Android AXIS pair carries the right stick during RIGHT_RANGE.
     * The detected AXIS is used only for reading values; calibration is always saved
     * to ABS_RX/ABS_RY (the daemon's fixed right stick codes).
     */
    private void detectRightStick() {
        if (mRightStickDetected) return;

        float magStd = mLastRX_std * mLastRX_std + mLastRY_std * mLastRY_std;
        float magAlt = mLastRX_alt * mLastRX_alt + mLastRY_alt * mLastRY_alt;
        float magZ = mLastRX_z * mLastRX_z + mLastRY_z * mLastRY_z;

        // Find the candidate with the highest magnitude
        float maxMag = Math.max(magStd, Math.max(magAlt, magZ));
        if (maxMag < 0.04f) return; // no significant movement yet

        if (maxMag == magZ && magZ > 0.04f) {
            mDetectedAndroidAxisRX = 0; // AXIS_Z/AXIS_RZ pair
            mCenterRX = mCenterRX_z;
            mCenterRY = mCenterRY_z;
            mRightStickDetected = true;
            mLastRX = mLastRX_z;
            mLastRY = mLastRY_z;
        } else if (maxMag == magAlt && magAlt > 0.04f) {
            mDetectedAndroidAxisRX = 1; // AXIS_GAS/AXIS_BRAKE pair
            mCenterRX = mCenterRX_alt;
            mCenterRY = mCenterRY_alt;
            mRightStickDetected = true;
            mLastRX = mLastRX_alt;
            mLastRY = mLastRY_alt;
        } else if (magStd > 0.04f) {
            mDetectedAndroidAxisRX = 2; // AXIS_RX/AXIS_RY pair
            mCenterRX = mCenterRX_std;
            mCenterRY = mCenterRY_std;
            mRightStickDetected = true;
            mLastRX = mLastRX_std;
            mLastRY = mLastRY_std;
        }
    }

    /**
     * Read a trigger axis value, handling both unipolar [0,1] and bipolar [-1,1] sources.
     * For unipolar axes (min >= 0), the raw value is already in [0,1] range.
     * For bipolar axes (min < 0), remap [-1,1] → [0,1]: (val + 1) / 2.
     * Returns the value as a float in [0,1].
     */
    private static float readTriggerAxis(MotionEvent event, int axis) {
        float val = event.getAxisValue(axis);
        InputDevice.MotionRange range = event.getDevice() != null
                ? event.getDevice().getMotionRange(axis) : null;
        if (range != null && range.getMin() >= 0f) {
            // Unipolar: value is already 0..1
            return Math.max(0f, Math.min(1f, val));
        } else if (range != null && range.getMin() < 0f) {
            // Bipolar: remap [-1,1] → [0,1]
            return Math.max(0f, Math.min(1f, (val + 1f) / 2f));
        }
        // No range info: treat as unipolar
        return Math.max(0f, Math.min(1f, val));
    }

    /** Apply deadzone to a display value so the dot stops at the red circle edge. */
    private static float applyDisplayDeadzone(float value, float deadzone) {
        if (deadzone <= 0f) return value;
        float absVal = Math.abs(value);
        if (absVal < deadzone) return 0f;
        float sign = value > 0 ? 1f : -1f;
        return sign * (absVal - deadzone) / (1f - deadzone);
    }

    /** Convert raw deadzone (0-32767) to percentage (0-50). */
    private static int deadzoneToPercent(int raw) {
        return Math.round(raw * 100f / 32767f);
    }

    /** Convert percentage (0-50) to raw deadzone (0-16383). */
    private static int percentToDeadzone(int pct) {
        return Math.round(pct * 32767f / 100f);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private void loadExistingCalibration() {
        // Save original calibration strings for restore on cancel
        for (int i = 0; i < CAL_AXES.length; i++) {
            mOriginalCalStrings[i] = SystemProperties.get(
                    PROP_CAL_PREFIX + CAL_AXES[i], "");
        }

        // Load existing deadzone/sensitivity as UI defaults
        String calX = mOriginalCalStrings[0]; // ABS_X
        if (!calX.isEmpty()) {
            String[] parts = calX.split(",");
            if (parts.length >= 5) {
                try {
                    mLeftDeadzone = Integer.parseInt(parts[3]);
                    mLeftSensitivity = Math.round(
                            Float.parseFloat(parts[4]) * 100f);
                } catch (NumberFormatException ignored) {}
            }
        }
        String calRX = mOriginalCalStrings[2]; // ABS_RX
        if (calRX.isEmpty()) calRX = mOriginalCalStrings[6]; // ABS_GAS fallback
        if (!calRX.isEmpty()) {
            String[] parts = calRX.split(",");
            if (parts.length >= 5) {
                try {
                    mRightDeadzone = Integer.parseInt(parts[3]);
                    mRightSensitivity = Math.round(
                            Float.parseFloat(parts[4]) * 100f);
                } catch (NumberFormatException ignored) {}
            }
        }
        // Try loading trigger deadzone from ABS_Z first, then ABS_GAS as fallback
        String calZ = mOriginalCalStrings[4]; // ABS_Z
        String calGas = mOriginalCalStrings[6]; // ABS_GAS
        String triggerCal = !calZ.isEmpty() ? calZ : calGas;
        if (!triggerCal.isEmpty()) {
            String[] parts = triggerCal.split(",");
            if (parts.length >= 4) {
                try {
                    mTriggerDeadzone = Integer.parseInt(parts[3]);
                } catch (NumberFormatException ignored) {}
            }
        }

        // Clear all calibration so daemon passes raw untransformed values
        for (int axis : CAL_AXES) {
            SystemProperties.set(PROP_CAL_PREFIX + axis, "");
        }
        bumpConfigVersion();

        // Daemon checks config every ~1s; wait for it to reload before capturing
        mDaemonReady = false;
        mHandler.postDelayed(() -> {
            mDaemonReady = true;
            if (mContinueButton != null) {
                mContinueButton.setEnabled(true);
            }
        }, 1500);
    }

    private void trackRange(float x, float y, boolean isLeft) {
        // Convert Android's normalized [-1,1] float back to the daemon's
        // signed 16-bit range [-32768, 32767] matching virtual device absinfo
        int ix = Math.round(((x + 1f) / 2f) * 65535f - 32768f);
        int iy = Math.round(((y + 1f) / 2f) * 65535f - 32768f);

        if (isLeft) {
            mMinX = Math.min(mMinX, ix);
            mMaxX = Math.max(mMaxX, ix);
            mMinY = Math.min(mMinY, iy);
            mMaxY = Math.max(mMaxY, iy);
        } else {
            mMinRX = Math.min(mMinRX, ix);
            mMaxRX = Math.max(mMaxRX, ix);
            mMinRY = Math.min(mMinRY, iy);
            mMaxRY = Math.max(mMaxRY, iy);
        }
    }

    /**
     * Show stick view only (no sliders) for range capture phase.
     */
    private void showStickRangeCapture() {
        mStickView.setVisibility(View.VISIBLE);
        mStickView.setPosition(0f, 0f);
        mStickView.setDeadzone(0f);
        mStickControlsContainer.setVisibility(View.GONE);
        mTriggerContainer.setVisibility(View.GONE);
    }

    /**
     * Show stick view + deadzone/sensitivity sliders for adjustment phase.
     */
    private void showStickAdjustControls() {
        mStickView.setVisibility(View.VISIBLE);
        mStickView.setPosition(0f, 0f);
        mStickView.setDeadzone(mCurrentDeadzone / 32767f);
        mStickControlsContainer.setVisibility(View.VISIBLE);
        mTriggerContainer.setVisibility(View.GONE);

        int dzPct = deadzoneToPercent(mCurrentDeadzone);
        mDeadzoneLabel.setText(getString(
                R.string.gamepad_calibration_deadzone_label, dzPct));
        mDeadzoneSeekbar.setProgress(dzPct);

        mSensLabel.setText(getString(
                R.string.gamepad_calibration_sensitivity_label, mCurrentSensitivity));
        mSensSeekbar.setProgress(mCurrentSensitivity);
    }

    private void showTriggerControls() {
        mStickView.setVisibility(View.GONE);
        mStickControlsContainer.setVisibility(View.GONE);
        mTriggerContainer.setVisibility(View.VISIBLE);

        mLeftTriggerBar.setValue(0f);
        mRightTriggerBar.setValue(0f);
        int trigDzPct = deadzoneToPercent(mTriggerDeadzone);
        mTriggerDeadzoneLabel.setText(getString(
                R.string.gamepad_calibration_deadzone_label, trigDzPct));
        mTriggerDeadzoneSeekbar.setProgress(trigDzPct);
    }

    private void advanceStep() {
        switch (mCurrentStep) {
            case CENTER:
                if (!mDaemonReady) {
                    Toast.makeText(requireContext(),
                            "Waiting for daemon to reload calibration...",
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                // Capture resting center position
                // Convert Android's normalized [-1,1] back to daemon's signed 16-bit range
                mCenterX = floatToInt16(mLastX);
                mCenterY = floatToInt16(mLastY);
                // Save centers for all right stick candidates
                mCenterRX_std = floatToInt16(mLastRX_std);
                mCenterRY_std = floatToInt16(mLastRY_std);
                mCenterRX_alt = floatToInt16(mLastRX_alt);
                mCenterRY_alt = floatToInt16(mLastRY_alt);
                mCenterRX_z = floatToInt16(mLastRX_z);
                mCenterRY_z = floatToInt16(mLastRY_z);
                // Default to standard (AXIS_RX/AXIS_RY) until detection
                mCenterRX = mCenterRX_std;
                mCenterRY = mCenterRY_std;
                // Move to left stick range capture
                mCurrentStep = Step.LEFT_RANGE;
                mInstructionText.setText(
                        R.string.gamepad_calibration_step_left_range);
                showStickRangeCapture();
                break;

            case LEFT_RANGE:
                // Range capture done, apply defaults if not moved
                if (mMinX == Integer.MAX_VALUE) {
                    mMinX = -32768; mMaxX = 32767;
                    mMinY = -32768; mMaxY = 32767;
                }
                // Save center+range with deadzone=0 so the daemon passes raw
                // centered values for the ADJUST step's display deadzone.
                float leftSens = mLeftSensitivity / 100f;
                saveAxisCalibration(ABS_X, mCenterX, mMinX, mMaxX, 0, leftSens);
                saveAxisCalibration(ABS_Y, mCenterY, mMinY, mMaxY, 0, leftSens);
                bumpConfigVersion();
                // Move to left stick adjustment
                mCurrentStep = Step.LEFT_ADJUST;
                mInstructionText.setText(
                        R.string.gamepad_calibration_step_left_adjust);
                mCurrentDeadzone = mLeftDeadzone;
                mCurrentSensitivity = mLeftSensitivity;
                showStickAdjustControls();
                break;

            case LEFT_ADJUST:
                // Save left stick adjustments
                mLeftDeadzone = mCurrentDeadzone;
                mLeftSensitivity = mCurrentSensitivity;
                // Move to right stick range capture
                mCurrentStep = Step.RIGHT_RANGE;
                mInstructionText.setText(
                        R.string.gamepad_calibration_step_right_range);
                showStickRangeCapture();
                break;

            case RIGHT_RANGE:
                // Range capture done
                if (mMinRX == Integer.MAX_VALUE) {
                    mMinRX = -32768; mMaxRX = 32767;
                    mMinRY = -32768; mMaxRY = 32767;
                }
                // Save right stick calibration using the daemon's ABS codes for the
                // active preset (varies by controller type).
                float rightSens = mRightSensitivity / 100f;
                saveAxisCalibration(getRightStickAbsX(), mCenterRX, mMinRX, mMaxRX,
                        mRightDeadzone, rightSens);
                saveAxisCalibration(getRightStickAbsY(), mCenterRY, mMinRY, mMaxRY,
                        mRightDeadzone, rightSens);
                bumpConfigVersion();
                // Move to right stick adjustment
                mCurrentStep = Step.RIGHT_ADJUST;
                mInstructionText.setText(
                        R.string.gamepad_calibration_step_right_adjust);
                mCurrentDeadzone = mRightDeadzone;
                mCurrentSensitivity = mRightSensitivity;
                showStickAdjustControls();
                break;

            case RIGHT_ADJUST:
                mRightDeadzone = mCurrentDeadzone;
                mRightSensitivity = mCurrentSensitivity;
                // Move to triggers
                mCurrentStep = Step.TRIGGERS;
                mInstructionText.setText(
                        R.string.gamepad_calibration_step_triggers);
                showTriggerControls();
                mContinueButton.setText(R.string.finish);
                break;

            case TRIGGERS:
                saveCalibration();
                mCalibrationCompleted = true;
                mCurrentStep = Step.DONE;
                Toast.makeText(requireContext(),
                        R.string.gamepad_calibration_complete,
                        Toast.LENGTH_SHORT).show();
                dismiss();
                break;
        }
    }

    /**
     * Apply deadzone immediately to the current stick axes.
     */
    private void applyDeadzoneImmediately() {
        if (mCurrentStep == Step.LEFT_ADJUST) {
            mLeftDeadzone = mCurrentDeadzone;
        } else if (mCurrentStep == Step.RIGHT_ADJUST) {
            mRightDeadzone = mCurrentDeadzone;
        }
        // During ADJUST, save to daemon with deadzone=0 so raw centered
        // values reach the UI. The UI applies its own display deadzone
        // for visual feedback. The actual deadzone is saved when the user
        // advances to the next step via saveCalibration().
        pushLiveCalibration();
    }

    private void applySensitivityImmediately() {
        if (mCurrentStep == Step.LEFT_ADJUST) {
            mLeftSensitivity = mCurrentSensitivity;
        } else if (mCurrentStep == Step.RIGHT_ADJUST) {
            mRightSensitivity = mCurrentSensitivity;
        }
        pushLiveCalibration();
    }

    /** Push calibration to daemon with deadzone=0 for live preview. */
    private void pushLiveCalibration() {
        if (mCurrentStep == Step.LEFT_ADJUST) {
            float sens = mLeftSensitivity / 100f;
            saveAxisCalibration(ABS_X, mCenterX, mMinX, mMaxX, 0, sens);
            saveAxisCalibration(ABS_Y, mCenterY, mMinY, mMaxY, 0, sens);
        } else if (mCurrentStep == Step.RIGHT_ADJUST) {
            float sens = mRightSensitivity / 100f;
            saveAxisCalibration(getRightStickAbsX(), mCenterRX, mMinRX, mMaxRX, 0, sens);
            saveAxisCalibration(getRightStickAbsY(), mCenterRY, mMinRY, mMaxRY, 0, sens);
        }
        bumpConfigVersion();
    }

    private void applyTriggerDeadzoneImmediately() {
        saveTriggerCalibration();
        bumpConfigVersion();
    }

    private void saveCalibration() {
        float leftSens = mLeftSensitivity / 100f;
        float rightSens = mRightSensitivity / 100f;

        // Save using daemon's ABS codes which depend on the active preset.
        saveAxisCalibration(ABS_X, mCenterX, mMinX, mMaxX,
                mLeftDeadzone, leftSens);
        saveAxisCalibration(ABS_Y, mCenterY, mMinY, mMaxY,
                mLeftDeadzone, leftSens);
        saveAxisCalibration(getRightStickAbsX(), mCenterRX, mMinRX, mMaxRX,
                mRightDeadzone, rightSens);
        saveAxisCalibration(getRightStickAbsY(), mCenterRY, mMinRY, mMaxRY,
                mRightDeadzone, rightSens);
        saveTriggerCalibration();

        bumpConfigVersion();
    }

    /** Save trigger calibration to the daemon's trigger codes for the active preset. */
    private void saveTriggerCalibration() {
        // Use captured trigger range if available, otherwise default to 0..32767
        int ltMin = (mMinLT != Integer.MAX_VALUE) ? mMinLT : 0;
        int ltMax = (mMaxLT != Integer.MIN_VALUE) ? mMaxLT : 32767;
        int rtMin = (mMinRT != Integer.MAX_VALUE) ? mMinRT : 0;
        int rtMax = (mMaxRT != Integer.MIN_VALUE) ? mMaxRT : 32767;
        saveAxisCalibration(getTriggerAbsLT(), ltMin, ltMin, ltMax, mTriggerDeadzone, 1.0f);
        saveAxisCalibration(getTriggerAbsRT(), rtMin, rtMin, rtMax, mTriggerDeadzone, 1.0f);
    }

    /** Get the daemon's ABS code for right stick X based on the active preset. */
    private int getRightStickAbsX() {
        return usesStandardLayout() ? ABS_RX : ABS_Z;
    }

    /** Get the daemon's ABS code for right stick Y based on the active preset. */
    private int getRightStickAbsY() {
        return usesStandardLayout() ? ABS_RY : ABS_RZ;
    }

    /** Get the daemon's ABS code for left trigger based on the active preset. */
    private int getTriggerAbsLT() {
        return usesStandardLayout() ? ABS_Z : ABS_BRAKE;
    }

    /** Get the daemon's ABS code for right trigger based on the active preset. */
    private int getTriggerAbsRT() {
        return usesStandardLayout() ? ABS_RZ : ABS_GAS;
    }

    /**
     * Check if the daemon applies heuristic remapping to standard layout.
     * PID 0x02fd uses standard layout (right stick=RX/RY, triggers=Z/RZ).
     * All other PIDs use native layout (right stick=Z/RZ, triggers=GAS/BRAKE).
     */
    private boolean usesStandardLayout() {
        String pid = SystemProperties.get("persist.gammaos.gamepad.device_pid", "0x0b13")
                .replace("0x", "").toLowerCase();
        return "02fd".equals(pid) || "2fd".equals(pid);
    }

    private void bumpConfigVersion() {
        int version = SystemProperties.getInt(PROP_CONFIG_VERSION, 0);
        SystemProperties.set(PROP_CONFIG_VERSION, String.valueOf(version + 1));
    }

    /** Callback for hosts that need to know when the dialog is dismissed. */
    public interface OnCalibrationDismissListener {
        void onCalibrationDismissed(boolean completed);
    }

    @Override
    public void onDismiss(@NonNull android.content.DialogInterface dialog) {
        super.onDismiss(dialog);
        if (!mCalibrationCompleted) {
            // Restore original calibration since user cancelled
            for (int i = 0; i < CAL_AXES.length; i++) {
                SystemProperties.set(PROP_CAL_PREFIX + CAL_AXES[i],
                        mOriginalCalStrings[i]);
            }
            bumpConfigVersion();
        }
        // Notify host activity if it implements the listener
        android.app.Activity activity = getActivity();
        if (activity instanceof OnCalibrationDismissListener) {
            ((OnCalibrationDismissListener) activity)
                    .onCalibrationDismissed(mCalibrationCompleted);
        }
    }

    private void saveAxisCalibration(int axis, int center, int min, int max,
            int deadzone, float sensitivity) {
        String value = center + "," + min + "," + max + ","
                + deadzone + "," + sensitivity + ",0";
        SystemProperties.set(PROP_CAL_PREFIX + axis, value);
    }
}
