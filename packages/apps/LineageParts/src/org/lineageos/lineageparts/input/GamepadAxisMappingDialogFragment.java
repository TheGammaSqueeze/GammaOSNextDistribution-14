/*
 * GammaOS Gamepad Axis Role Mapping Dialog
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.lineageparts.input;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.SystemProperties;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import org.lineageos.lineageparts.R;

import java.util.HashMap;
import java.util.Map;

/**
 * Full-screen dialog for assigning physical axes to logical roles
 * (Left Stick X/Y, Right Stick X/Y, Left/Right Trigger).
 *
 * The user taps a role row, then moves the corresponding physical input.
 * The dialog detects which axis moved most and assigns it to that role.
 *
 * Properties stored: persist.gammaos.gamepad.role_lx, role_ly, role_rx, role_ry,
 * role_lt, role_rt. Each stores the .kl-mapped ABS code that should output as
 * the role's target ABS code. Empty = auto (use .kl mapping as-is).
 */
public class GamepadAxisMappingDialogFragment extends DialogFragment {

    private static final String PROP_CONFIG_VERSION = "persist.gammaos.gamepad.config_version";

    // Role definitions: [0]=property key, [1]=target ABS code, [2]=display name
    private static final String[][] ROLES = {
        { "persist.gammaos.gamepad.role_lx", "0", "Left Stick \u2194 (left/right)" },
        { "persist.gammaos.gamepad.role_ly", "1", "Left Stick \u2195 (up/down)" },
        { "persist.gammaos.gamepad.role_rx", "3", "Right Stick \u2194 (left/right)" },
        { "persist.gammaos.gamepad.role_ry", "4", "Right Stick \u2195 (up/down)" },
        { "persist.gammaos.gamepad.role_lt", "2", "Left Trigger" },
        { "persist.gammaos.gamepad.role_rt", "5", "Right Trigger" },
    };

    // Linux ABS code -> display name
    private static final Map<Integer, String> ABS_CODE_NAMES = new HashMap<>();
    static {
        ABS_CODE_NAMES.put(0, "ABS_X");
        ABS_CODE_NAMES.put(1, "ABS_Y");
        ABS_CODE_NAMES.put(2, "ABS_Z");
        ABS_CODE_NAMES.put(3, "ABS_RX");
        ABS_CODE_NAMES.put(4, "ABS_RY");
        ABS_CODE_NAMES.put(5, "ABS_RZ");
        ABS_CODE_NAMES.put(9, "ABS_GAS");
        ABS_CODE_NAMES.put(10, "ABS_BRAKE");
        ABS_CODE_NAMES.put(16, "ABS_HAT0X");
        ABS_CODE_NAMES.put(17, "ABS_HAT0Y");
    }

    // MotionEvent axes to poll during detection
    private static final int[] DETECT_AXES = {
        MotionEvent.AXIS_X, MotionEvent.AXIS_Y,
        MotionEvent.AXIS_Z, MotionEvent.AXIS_RX,
        MotionEvent.AXIS_RY, MotionEvent.AXIS_RZ,
        MotionEvent.AXIS_GAS, MotionEvent.AXIS_BRAKE,
    };

    // Android MotionEvent axis constant -> Linux ABS code
    private static final Map<Integer, Integer> MOTION_TO_ABS = new HashMap<>();
    static {
        MOTION_TO_ABS.put(MotionEvent.AXIS_X, 0);
        MOTION_TO_ABS.put(MotionEvent.AXIS_Y, 1);
        MOTION_TO_ABS.put(MotionEvent.AXIS_Z, 2);
        MOTION_TO_ABS.put(MotionEvent.AXIS_RX, 3);
        MOTION_TO_ABS.put(MotionEvent.AXIS_RY, 4);
        MOTION_TO_ABS.put(MotionEvent.AXIS_RZ, 5);
        MOTION_TO_ABS.put(MotionEvent.AXIS_GAS, 9);
        MOTION_TO_ABS.put(MotionEvent.AXIS_BRAKE, 10);
    }

    private static final float DETECTION_THRESHOLD = 0.4f;

    private int mDetectingRoleIndex = -1;
    private final float[] mBaseline = new float[DETECT_AXES.length];
    private final int[] mRoleValues = new int[ROLES.length]; // -1 = auto
    private TextView[] mRoleValueViews;
    private LinearLayout[] mRoleRows;
    private TextView mStatusText;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NO_FRAME, android.R.style.Theme_DeviceDefault_NoActionBar);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Context ctx = requireContext();

        // Load current role properties
        for (int i = 0; i < ROLES.length; i++) {
            String val = SystemProperties.get(ROLES[i][0], "");
            if (val.isEmpty()) {
                mRoleValues[i] = -1;
            } else {
                try {
                    mRoleValues[i] = Integer.parseInt(val);
                } catch (NumberFormatException e) {
                    mRoleValues[i] = -1;
                }
            }
        }

        ScrollView scroll = new ScrollView(ctx);
        scroll.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(64, 32, 64, 32);
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        // Title
        TextView title = new TextView(ctx);
        title.setTextSize(24);
        title.setText(R.string.gamepad_axis_roles_title);
        title.setTextColor(0xFFFFFFFF);
        root.addView(title, matchWrap());

        // Subtitle
        TextView subtitle = new TextView(ctx);
        subtitle.setTextSize(14);
        subtitle.setText(R.string.gamepad_axis_roles_subtitle);
        subtitle.setTextColor(0xFFAAAAAA);
        LinearLayout.LayoutParams subtitleParams = matchWrap();
        subtitleParams.topMargin = 8;
        root.addView(subtitle, subtitleParams);

        // Role entries
        mRoleValueViews = new TextView[ROLES.length];
        mRoleRows = new LinearLayout[ROLES.length];
        for (int i = 0; i < ROLES.length; i++) {
            final int idx = i;

            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(24, 16, 24, 16);
            row.setBackgroundColor(0x20FFFFFF);
            row.setClickable(true);
            row.setFocusable(true);
            row.setOnClickListener(v -> startDetection(idx));
            LinearLayout.LayoutParams rowParams = matchWrap();
            rowParams.topMargin = 8;
            root.addView(row, rowParams);
            mRoleRows[i] = row;

            // Role name
            TextView roleName = new TextView(ctx);
            roleName.setTextSize(18);
            roleName.setText(ROLES[i][2]);
            roleName.setTextColor(0xFFFFFFFF);
            LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            row.addView(roleName, nameParams);

            // Arrow
            TextView arrow = new TextView(ctx);
            arrow.setTextSize(18);
            arrow.setText("\u2192");
            arrow.setTextColor(0xFF888888);
            arrow.setPadding(16, 0, 16, 0);
            row.addView(arrow);

            // Current value
            TextView valueView = new TextView(ctx);
            valueView.setTextSize(18);
            row.addView(valueView);
            mRoleValueViews[i] = valueView;
            updateRoleDisplay(i);
        }

        // Status text
        mStatusText = new TextView(ctx);
        mStatusText.setTextSize(16);
        mStatusText.setTextColor(0xFFCCCCCC);
        mStatusText.setText(R.string.gamepad_axis_roles_ready);
        LinearLayout.LayoutParams statusParams = matchWrap();
        statusParams.topMargin = 16;
        root.addView(mStatusText, statusParams);

        // Button row
        LinearLayout buttonRow = new LinearLayout(ctx);
        buttonRow.setOrientation(LinearLayout.HORIZONTAL);
        buttonRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams btnRowParams = matchWrap();
        btnRowParams.topMargin = 16;
        root.addView(buttonRow, btnRowParams);

        Button clearBtn = new Button(ctx);
        clearBtn.setText(R.string.gamepad_axis_roles_clear);
        clearBtn.setOnClickListener(v -> clearAll());
        LinearLayout.LayoutParams clearParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        clearParams.rightMargin = 16;
        buttonRow.addView(clearBtn, clearParams);

        Button cancelBtn = new Button(ctx);
        cancelBtn.setText(android.R.string.cancel);
        cancelBtn.setOnClickListener(v -> dismiss());
        LinearLayout.LayoutParams cancelParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        cancelParams.rightMargin = 16;
        buttonRow.addView(cancelBtn, cancelParams);

        Button saveBtn = new Button(ctx);
        saveBtn.setText(R.string.gamepad_axis_roles_save);
        saveBtn.setOnClickListener(v -> saveAndDismiss());
        buttonRow.addView(saveBtn);

        scroll.setFocusable(true);
        scroll.setFocusableInTouchMode(true);
        scroll.requestFocus();

        return scroll;
    }

    private void updateRoleDisplay(int index) {
        if (mRoleValueViews == null || mRoleValueViews[index] == null) return;
        int value = mRoleValues[index];
        if (value < 0) {
            mRoleValueViews[index].setText(R.string.gamepad_axis_roles_auto);
            mRoleValueViews[index].setTextColor(0xFF888888);
        } else {
            String name = ABS_CODE_NAMES.getOrDefault(value, String.valueOf(value));
            mRoleValueViews[index].setText(name);
            int targetCode = Integer.parseInt(ROLES[index][1]);
            if (value != targetCode) {
                // Non-default assignment highlighted in accent color
                mRoleValueViews[index].setTextColor(0xFF4CAF50);
            } else {
                mRoleValueViews[index].setTextColor(0xFFCCCCCC);
            }
        }
    }

    private void startDetection(int roleIndex) {
        mDetectingRoleIndex = roleIndex;
        mStatusText.setText(getString(R.string.gamepad_axis_roles_detecting,
                ROLES[roleIndex][2]));
        mStatusText.setTextColor(0xFFFF9800);

        // Highlight detecting row
        for (int i = 0; i < mRoleRows.length; i++) {
            mRoleRows[i].setBackgroundColor(
                    i == roleIndex ? 0x40FF9800 : 0x20FFFFFF);
        }

        // Reset baseline; will be captured on first motion event
        for (int i = 0; i < mBaseline.length; i++) {
            mBaseline[i] = Float.NaN;
        }
    }

    private void onJoystickMotion(MotionEvent event) {
        if (mDetectingRoleIndex < 0) return;

        boolean firstEvent = Float.isNaN(mBaseline[0]);
        float maxDelta = 0;
        int bestAxisIdx = -1;

        for (int i = 0; i < DETECT_AXES.length; i++) {
            float val = event.getAxisValue(DETECT_AXES[i]);
            if (firstEvent) {
                mBaseline[i] = val;
            } else {
                float delta = Math.abs(val - mBaseline[i]);
                if (delta > maxDelta) {
                    maxDelta = delta;
                    bestAxisIdx = i;
                }
            }
        }

        if (firstEvent) return;

        if (maxDelta >= DETECTION_THRESHOLD && bestAxisIdx >= 0) {
            int motionAxis = DETECT_AXES[bestAxisIdx];
            Integer absCode = MOTION_TO_ABS.get(motionAxis);
            if (absCode == null) return;

            // Map through inverse of current role overrides to get
            // the .kl-mapped code (what the daemon uses pre-role-mapping)
            int klMappedCode = getKlMappedCode(absCode);

            mRoleValues[mDetectingRoleIndex] = klMappedCode;
            updateRoleDisplay(mDetectingRoleIndex);

            // Clear any other role that claims the same .kl-mapped source
            for (int i = 0; i < ROLES.length; i++) {
                if (i == mDetectingRoleIndex) continue;
                if (mRoleValues[i] == klMappedCode) {
                    mRoleValues[i] = -1;
                    updateRoleDisplay(i);
                }
            }

            // Reset highlight
            for (LinearLayout row : mRoleRows) {
                row.setBackgroundColor(0x20FFFFFF);
            }

            mDetectingRoleIndex = -1;
            mStatusText.setText(R.string.gamepad_axis_roles_detected);
            mStatusText.setTextColor(0xFF4CAF50);
        }
    }

    /**
     * Reverse the current role overrides to find the .kl-mapped ABS code
     * that produces the detected output code on the virtual device.
     *
     * If role_rx=9 is active (maps .kl-code 9 to output ABS_RX=3),
     * and we detect ABS_RX=3, this returns 9.
     */
    private int getKlMappedCode(int detectedAbsCode) {
        for (String[] role : ROLES) {
            int targetCode = Integer.parseInt(role[1]);
            String val = SystemProperties.get(role[0], "");
            if (!val.isEmpty()) {
                try {
                    int sourceCode = Integer.parseInt(val);
                    if (targetCode == detectedAbsCode && sourceCode != targetCode) {
                        return sourceCode;
                    }
                } catch (NumberFormatException e) {
                    // skip
                }
            }
        }
        return detectedAbsCode;
    }

    private void clearAll() {
        for (int i = 0; i < ROLES.length; i++) {
            mRoleValues[i] = -1;
            updateRoleDisplay(i);
        }
        mDetectingRoleIndex = -1;
        for (LinearLayout row : mRoleRows) {
            row.setBackgroundColor(0x20FFFFFF);
        }
        mStatusText.setText(R.string.gamepad_axis_roles_cleared);
        mStatusText.setTextColor(0xFF4CAF50);
    }

    private void saveAndDismiss() {
        for (int i = 0; i < ROLES.length; i++) {
            if (mRoleValues[i] >= 0) {
                SystemProperties.set(ROLES[i][0], String.valueOf(mRoleValues[i]));
            } else {
                SystemProperties.set(ROLES[i][0], "");
            }
        }
        bumpConfigVersion();
        Toast.makeText(requireContext(), R.string.gamepad_axis_roles_saved,
                Toast.LENGTH_SHORT).show();
        getParentFragmentManager().setFragmentResult("remap_changed", new Bundle());
        dismiss();
    }

    private void bumpConfigVersion() {
        SystemProperties.set("persist.gammaos.gamepad.full_reload", "1");
        int version = SystemProperties.getInt(PROP_CONFIG_VERSION, 0);
        SystemProperties.set(PROP_CONFIG_VERSION, String.valueOf(version + 1));
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }
}
