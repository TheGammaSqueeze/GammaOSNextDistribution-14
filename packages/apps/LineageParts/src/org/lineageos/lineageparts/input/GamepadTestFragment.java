/*
 * GammaOS Gamepad Input Test Screen
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.lineageparts.input;

import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.lineageos.lineageparts.R;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Combined button + analog test screen showing live press indicators.
 */
public class GamepadTestFragment extends Fragment {

    // Gamepad buttons: keycode -> display name
    private static final Map<Integer, String> BUTTON_NAMES = new LinkedHashMap<>();
    static {
        BUTTON_NAMES.put(KeyEvent.KEYCODE_BUTTON_A, "A");
        BUTTON_NAMES.put(KeyEvent.KEYCODE_BUTTON_B, "B");
        BUTTON_NAMES.put(KeyEvent.KEYCODE_BUTTON_C, "C");
        BUTTON_NAMES.put(KeyEvent.KEYCODE_BUTTON_X, "X");
        BUTTON_NAMES.put(KeyEvent.KEYCODE_BUTTON_Y, "Y");
        BUTTON_NAMES.put(KeyEvent.KEYCODE_BUTTON_L1, "LB");
        BUTTON_NAMES.put(KeyEvent.KEYCODE_BUTTON_R1, "RB");
        BUTTON_NAMES.put(KeyEvent.KEYCODE_BUTTON_L2, "LT");
        BUTTON_NAMES.put(KeyEvent.KEYCODE_BUTTON_R2, "RT");
        BUTTON_NAMES.put(KeyEvent.KEYCODE_BUTTON_SELECT, "Select");
        BUTTON_NAMES.put(KeyEvent.KEYCODE_BUTTON_START, "Start");
        BUTTON_NAMES.put(KeyEvent.KEYCODE_BUTTON_MODE, "Guide");
        BUTTON_NAMES.put(KeyEvent.KEYCODE_BUTTON_THUMBL, "L3");
        BUTTON_NAMES.put(KeyEvent.KEYCODE_BUTTON_THUMBR, "R3");
        BUTTON_NAMES.put(KeyEvent.KEYCODE_DPAD_UP, "D-Up");
        BUTTON_NAMES.put(KeyEvent.KEYCODE_DPAD_DOWN, "D-Down");
        BUTTON_NAMES.put(KeyEvent.KEYCODE_DPAD_LEFT, "D-Left");
        BUTTON_NAMES.put(KeyEvent.KEYCODE_DPAD_RIGHT, "D-Right");
        // Virtual pad passthrough buttons (scan codes 0x2F0-0x2F2)
        BUTTON_NAMES.put(752, "Ext1");
        BUTTON_NAMES.put(753, "Ext2");
        BUTTON_NAMES.put(754, "Ext3");
    }

    // Map keycode -> indicator text view, label, and container
    private final Map<Integer, TextView> mButtonIndicators = new HashMap<>();
    private final Map<Integer, View> mButtonContainers = new HashMap<>();

    // Track which DPAD keys are active via HAT axes, to avoid conflicts with KeyEvent handler
    private final Set<Integer> mHatActiveKeys = new HashSet<>();

    private AnalogStickView mLeftStickView;
    private AnalogStickView mRightStickView;
    private TriggerBarView mLeftTriggerView;
    private TriggerBarView mRightTriggerView;
    private TextView mLeftStickCoords;
    private TextView mRightStickCoords;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.gamepad_test, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        GridLayout grid = view.findViewById(R.id.button_grid);
        mLeftStickView = view.findViewById(R.id.left_stick_view);
        mRightStickView = view.findViewById(R.id.right_stick_view);
        mLeftTriggerView = view.findViewById(R.id.left_trigger_view);
        mRightTriggerView = view.findViewById(R.id.right_trigger_view);
        mLeftStickCoords = view.findViewById(R.id.left_stick_coords);
        mRightStickCoords = view.findViewById(R.id.right_stick_coords);

        // Build button grid: 3 columns, each cell is a container with label + indicator
        int pair = 0;
        for (Map.Entry<Integer, String> entry : BUTTON_NAMES.entrySet()) {
            int keycode = entry.getKey();
            String name = entry.getValue();
            int col = pair % 3;

            // Container for label + indicator
            LinearLayout container = new LinearLayout(requireContext());
            container.setOrientation(LinearLayout.HORIZONTAL);
            container.setPadding(12, 6, 12, 6);

            // Rounded background shape (transparent by default)
            GradientDrawable bg = new GradientDrawable();
            bg.setCornerRadius(8f);
            bg.setColor(0x00000000);
            container.setBackground(bg);

            // Button label
            TextView label = new TextView(requireContext());
            label.setText(name);
            label.setTextSize(13);
            LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            container.addView(label, textLp);

            // State indicator
            TextView indicator = new TextView(requireContext());
            indicator.setText("\u25cb"); // hollow circle
            indicator.setTextSize(16);
            container.addView(indicator);

            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.columnSpec = GridLayout.spec(col, 1f);
            lp.setMargins(2, 2, 2, 2);
            grid.addView(container, lp);

            mButtonIndicators.put(keycode, indicator);
            mButtonContainers.put(keycode, container);

            pair++;
        }

        // Set up key and motion listeners
        view.setFocusable(true);
        view.setFocusableInTouchMode(true);
        view.requestFocus();

        view.setOnKeyListener((v, keyCode, event) -> {
            if (!BUTTON_NAMES.containsKey(keyCode)) return false;

            TextView ind = mButtonIndicators.get(keyCode);
            View ctr = mButtonContainers.get(keyCode);
            if (ind != null) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    ind.setText("\u25cf"); // filled circle
                    ind.setTextColor(0xFF4CAF50);
                    if (ctr != null) {
                        GradientDrawable bg = (GradientDrawable) ctr.getBackground();
                        bg.setColor(0x334CAF50);
                    }
                } else if (event.getAction() == KeyEvent.ACTION_UP) {
                    ind.setText("\u25cb"); // hollow circle
                    ind.setTextColor(0xFFFFFFFF);
                    if (ctr != null) {
                        GradientDrawable bg = (GradientDrawable) ctr.getBackground();
                        bg.setColor(0x00000000);
                    }
                }
            }
            return true;
        });

        view.setOnGenericMotionListener((v, event) -> {
            if ((event.getSource() & InputDevice.SOURCE_JOYSTICK) == 0) {
                return false;
            }

            // Left stick
            float lx = event.getAxisValue(MotionEvent.AXIS_X);
            float ly = event.getAxisValue(MotionEvent.AXIS_Y);
            mLeftStickView.setPosition(lx, ly);
            mLeftStickCoords.setText(String.format(Locale.US, "X: %.2f  Y: %.2f", lx, ly));

            // Right stick - try RX/RY and Z/RZ, pick whichever has more displacement
            float rx = event.getAxisValue(MotionEvent.AXIS_RX);
            float ry = event.getAxisValue(MotionEvent.AXIS_RY);
            float bestDisp = rx * rx + ry * ry;

            float zx = event.getAxisValue(MotionEvent.AXIS_Z);
            float zy = event.getAxisValue(MotionEvent.AXIS_RZ);
            float zDisp = zx * zx + zy * zy;
            if (zDisp > bestDisp) {
                rx = zx;
                ry = zy;
            }

            mRightStickView.setPosition(rx, ry);
            mRightStickCoords.setText(String.format(Locale.US, "X: %.2f  Y: %.2f", rx, ry));

            // Triggers - KL maps ABS_GAS→RTRIGGER, ABS_BRAKE→LTRIGGER (unipolar)
            float lt = event.getAxisValue(MotionEvent.AXIS_LTRIGGER);
            float rt = event.getAxisValue(MotionEvent.AXIS_RTRIGGER);
            mLeftTriggerView.setValue(lt);
            mRightTriggerView.setValue(rt);

            // HAT axes → DPAD indicators
            float hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X);
            float hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y);
            updateDpadFromHat(KeyEvent.KEYCODE_DPAD_LEFT, hatX < -0.5f);
            updateDpadFromHat(KeyEvent.KEYCODE_DPAD_RIGHT, hatX > 0.5f);
            updateDpadFromHat(KeyEvent.KEYCODE_DPAD_UP, hatY < -0.5f);
            updateDpadFromHat(KeyEvent.KEYCODE_DPAD_DOWN, hatY > 0.5f);

            return true;
        });
    }

    private void updateDpadFromHat(int keyCode, boolean active) {
        boolean wasActive = mHatActiveKeys.contains(keyCode);
        if (active == wasActive) return;

        if (active) {
            mHatActiveKeys.add(keyCode);
        } else {
            mHatActiveKeys.remove(keyCode);
        }

        TextView ind = mButtonIndicators.get(keyCode);
        View ctr = mButtonContainers.get(keyCode);
        if (ind != null) {
            if (active) {
                ind.setText("\u25cf");
                ind.setTextColor(0xFF4CAF50);
                if (ctr != null) {
                    GradientDrawable bg = (GradientDrawable) ctr.getBackground();
                    bg.setColor(0x334CAF50);
                }
            } else {
                ind.setText("\u25cb");
                ind.setTextColor(0xFFFFFFFF);
                if (ctr != null) {
                    GradientDrawable bg = (GradientDrawable) ctr.getBackground();
                    bg.setColor(0x00000000);
                }
            }
        }
    }
}
