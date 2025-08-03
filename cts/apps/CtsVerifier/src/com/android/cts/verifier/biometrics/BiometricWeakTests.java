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

import static android.hardware.biometrics.BiometricManager.Authenticators;

import android.hardware.biometrics.BiometricManager;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;

import com.android.cts.verifier.R;

/**
 * On devices without a weak biometric, ensure that the
 * {@link BiometricManager#canAuthenticate(int)} returns
 * {@link BiometricManager#BIOMETRIC_ERROR_NO_HARDWARE}
 *
 * Ensure that this result is consistent with the configuration in core/res/res/values/config.xml
 *
 * Ensure that invoking {@link Settings.ACTION_BIOMETRIC_ENROLL} with its corresponding
 * {@link Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED} enrolls a biometric that meets or
 * exceeds {@link BiometricManager.Authenticators.BIOMETRIC_WEAK}.
 *
 * Ensure that the BiometricPrompt UI displays all fields in the public API surface.
 */

public class BiometricWeakTests extends AbstractBaseTest {
    private static final String TAG = "BiometricWeakTests";

    private Button mEnrollButton;

    private boolean mEnrollFinished;

    @Override
    protected String getTag() {
        return TAG;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.biometric_test_weak_tests);
        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);

        mEnrollButton = findViewById(R.id.biometric_test_weak_enroll_button);

        mEnrollButton.setOnClickListener((view) -> {
            checkAndEnroll(mEnrollButton, Authenticators.BIOMETRIC_WEAK);
        });
    }

    @Override
    protected boolean isOnPauseAllowed() {
        // Test hasn't started yet, user may need to go to Settings to remove enrollments
        if (mEnrollButton.isEnabled()) {
            return true;
        }

        if (mCurrentlyEnrolling) {
            return true;
        }

        return false;
    }

    private void updatePassButton() {
        if (mEnrollFinished) {
            showToastAndLog("All tests passed");
            getPassButton().setEnabled(true);
        }
    }

    @Override
    protected void onBiometricEnrollFinished() {
        final int biometricStatus =
                mBiometricManager.canAuthenticate(Authenticators.BIOMETRIC_WEAK);
        if (biometricStatus == BiometricManager.BIOMETRIC_SUCCESS) {
            showToastAndLog("Successfully enrolled, please continue the test");
            mEnrollButton.setEnabled(false);
            mEnrollFinished = true;
            updatePassButton();
        } else {
            showToastAndLog("Unexpected result after enrollment: " + biometricStatus);
        }
    }
}
