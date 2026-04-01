/*
 * GammaOS Gamepad Calibration Activity
 * Standalone launcher for the calibration dialog, usable from SetupWizard or other apps.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.lineageparts.input;

import android.app.Activity;
import android.os.Bundle;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

import androidx.fragment.app.FragmentActivity;

public class GamepadCalibrationActivity extends FragmentActivity
        implements GamepadCalibrationDialogFragment.OnCalibrationDismissListener {

    private static final String TAG_CALIBRATION = "calibration_dialog";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        try {
            getWindow().setDecorFitsSystemWindows(false);
        } catch (Throwable ignored) {}
        applyImmersive();

        if (savedInstanceState == null) {
            GamepadCalibrationDialogFragment fragment =
                    new GamepadCalibrationDialogFragment();
            fragment.show(getSupportFragmentManager(), TAG_CALIBRATION);
        }
    }

    @Override
    public void onCalibrationDismissed(boolean completed) {
        setResult(completed ? Activity.RESULT_OK : Activity.RESULT_CANCELED);
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyImmersive();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) applyImmersive();
    }

    private void applyImmersive() {
        try {
            WindowInsetsController ic =
                    getWindow().getDecorView().getWindowInsetsController();
            if (ic != null) {
                ic.hide(WindowInsets.Type.statusBars()
                        | WindowInsets.Type.navigationBars());
                ic.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } catch (Throwable ignored) {}
    }
}
