/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.cts.verifier.biometrics;

import android.content.Intent;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricManager.Authenticators;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import com.android.cts.verifier.PassFailButtons;

import java.util.concurrent.Executor;

/**
 * Abstract base class for tests in this directory.
 */
public abstract class AbstractBaseTest extends PassFailButtons.Activity {

    private static final int REQUEST_ENROLL_WHEN_NONE_ENROLLED = 1;

    abstract protected String getTag();
    abstract protected boolean isOnPauseAllowed();

    protected final Handler mHandler = new Handler(Looper.getMainLooper());
    protected final Executor mExecutor = mHandler::post;

    protected boolean mCurrentlyEnrolling;

    BiometricManager mBiometricManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBiometricManager = getSystemService(BiometricManager.class);
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Assume we only enable the pass button when all tests pass. There actually  isn't a way
        // to easily do something like `this.isTestPassed()`
        if (!getPassButton().isEnabled() && !isOnPauseAllowed()) {
            showToastAndLog("This test must be completed without pausing the app");
            // Do not allow the test to continue if it loses foreground. Testers must start over.
            // 1) This is to avoid any potential change to the current enrollment / biometric state.
            // 2) The authentication UI must not affect the caller's activity lifecycle.
            finish();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        mCurrentlyEnrolling = false;

        if (requestCode == REQUEST_ENROLL_WHEN_NONE_ENROLLED) {
            onBiometricEnrollFinished();
        }
    }

    void showToastAndLog(String s) {
        Log.d(getTag(), s);
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    void showToastAndLog(String s, Exception e) {
        Log.d(getTag(), s, e);
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    protected void onBiometricEnrollFinished() {
    }

    void checkAndEnroll(Button enrollButton, int requestedStrength) {
        // Check that no biometrics (of any strength) are enrolled
        int result = mBiometricManager.canAuthenticate(Authenticators.BIOMETRIC_WEAK);
        if (result == BiometricManager.BIOMETRIC_SUCCESS) {
            showToastAndLog("Please ensure that all biometrics are removed before starting"
                    + " this test");
            return;
        }

        result = mBiometricManager.canAuthenticate(requestedStrength);
        if (result == BiometricManager.BIOMETRIC_SUCCESS) {
            showToastAndLog("Please ensure that all biometrics are removed before starting"
                    + " this test");
        } else if (result == BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE) {
            // Multi-sensor cases are more thoroughly tested in regular CTS (not CTS-V), this
            // should be fine for the purposes of CTS-V.
            showToastAndLog("This device does not have a sensor meeting the requested strength,"
                    + " you may pass this test");
            enrollButton.setEnabled(false);
            getPassButton().setEnabled(true);
        } else if (result == BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED) {
            startBiometricEnroll(REQUEST_ENROLL_WHEN_NONE_ENROLLED, requestedStrength);
        } else {
            showToastAndLog("Unexpected result: " + result + ". Please ensure you have removed"
                    + "all biometric enrollments.");
        }
    }

    private void startBiometricEnroll(int requestCode, int requestedStrength) {
        mCurrentlyEnrolling = true;
        final Intent enrollIntent = new Intent(Settings.ACTION_BIOMETRIC_ENROLL);
        enrollIntent.putExtra(Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                requestedStrength);

        startActivityForResult(enrollIntent, requestCode);
    }
}
