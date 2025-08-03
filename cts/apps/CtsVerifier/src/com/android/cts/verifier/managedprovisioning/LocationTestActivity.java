/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.cts.verifier.managedprovisioning;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import com.android.cts.verifier.R;

/**
 * Tests whether location is available in primary and work profiles under various settings.
 */
public class LocationTestActivity extends ByodTestActivityWithPrepare {
    private static final String TAG = LocationTestActivity.class.getSimpleName();

    public static final String ACTION_TEST_LOCATION_ENABLED =
            "com.android.cts.verifier.managedprovisioning.TEST_LOCATION_ENABLED";
    public static final String ACTION_TEST_LOCATION_DISABLED =
            "com.android.cts.verifier.managedprovisioning.TEST_LOCATION_DISABLED";
    public static final String ACTION_TEST_WORK_LOCATION_DISABLED =
            "com.android.cts.verifier.managedprovisioning.TEST_WORK_LOCATION_DISABLED";
    public static final String ACTION_TEST_WORK_LOCATION_DISABLED_PRIMARY =
            "com.android.cts.verifier.managedprovisioning.TEST_WORK_LOCATION_DISABLED_PRIMARY";

    public static final String TEST_ID_LOCATION_ENABLED = "BYOD_LocationModeEnableTest";
    public static final String TEST_ID_LOCATION_DISABLED = "BYOD_LocationModeDisableMainTest";
    public static final String TEST_ID_WORK_LOCATION_DISABLED = "BYOD_LocationModeDisableWorkTest";
    public static final String TEST_ID_WORK_LOCATION_DISABLED_PRIMARY =
            "BYOD_PrimaryLocationWhenWorkDisabled";

    private static final int REQUEST_LOCATION_CHECK = 123;
    private static final int REQUEST_LOCATION_SETTING = 124;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getInstructionsView().setText(getInstructions());
        getGoButton().setOnClickListener(v -> runTest());
        getPrepareButton().setOnClickListener(v -> runPrepare());
    }

    @Override
    public String getTestId() {
        switch (getIntent().getAction()) {
            case ACTION_TEST_LOCATION_ENABLED:
                return TEST_ID_LOCATION_ENABLED;
            case ACTION_TEST_LOCATION_DISABLED:
                return TEST_ID_LOCATION_DISABLED;
            case ACTION_TEST_WORK_LOCATION_DISABLED:
                return TEST_ID_WORK_LOCATION_DISABLED;
            case ACTION_TEST_WORK_LOCATION_DISABLED_PRIMARY:
                return TEST_ID_WORK_LOCATION_DISABLED_PRIMARY;
            default:
                throw new IllegalArgumentException("unknown test specified");
        }
    }

    private void runPrepare() {
        Intent locationSettingsIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        if (locationSettingsIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(locationSettingsIntent, REQUEST_LOCATION_SETTING);
        } else {
            Log.e(TAG, "Settings.ACTION_LOCATION_SOURCE_SETTINGS could not be resolved");
        }
    }

    private void runTest() {
        Intent intent = new Intent(getCheckerIntentAction());
        startActivityForResult(intent, LocationTestActivity.REQUEST_LOCATION_CHECK);
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private int getInstructions() {
        switch (getIntent().getAction()) {
            case ACTION_TEST_LOCATION_ENABLED:
                return R.string.provisioning_byod_location_mode_enable_instruction;
            case ACTION_TEST_LOCATION_DISABLED:
                return R.string.provisioning_byod_location_mode_disable_instruction;
            case ACTION_TEST_WORK_LOCATION_DISABLED:
                return R.string.provisioning_byod_work_location_mode_disable_instruction;
            case ACTION_TEST_WORK_LOCATION_DISABLED_PRIMARY:
                return R.string.provisioning_byod_primary_location_when_work_disabled_instruction;
            default:
                throw new IllegalArgumentException("unknown test specified");
        }
    }

    private String getCheckerIntentAction() {
        switch (getIntent().getAction()) {
            case ACTION_TEST_LOCATION_ENABLED:
            case ACTION_TEST_LOCATION_DISABLED:
            case ACTION_TEST_WORK_LOCATION_DISABLED:
                return LocationCheckerActivity.ACTION_CHECK_LOCATION_WORK;
            case ACTION_TEST_WORK_LOCATION_DISABLED_PRIMARY:
                return LocationCheckerActivity.ACTION_CHECK_LOCATION_PRIMARY;
            default:
                throw new IllegalArgumentException("unknown test specified");
        }
    }

    private boolean locationExpected() {
        switch (getIntent().getAction()) {
            case ACTION_TEST_LOCATION_ENABLED:
            case ACTION_TEST_WORK_LOCATION_DISABLED_PRIMARY:
                return true;
            case ACTION_TEST_LOCATION_DISABLED:
            case ACTION_TEST_WORK_LOCATION_DISABLED:
                return false;
            default:
                throw new IllegalArgumentException("unknown test specified");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_LOCATION_CHECK && resultCode == Activity.RESULT_OK) {
            int errorId = data.getIntExtra(LocationCheckerActivity.EXTRA_ERROR_ID, -1);

            String message;
            if (errorId != -1) {
                message = getString(errorId);
            } else {
                message = getString(R.string.provisioning_byod_location_obtained);
            }

            boolean locationObtained = errorId == -1;
            if (locationObtained == locationExpected()) {
                message = getString(R.string.provisioning_byod_pass_message, message);
                getPassButton().setEnabled(true);
            } else {
                message = getString(R.string.provisioning_byod_fail_message, message);
            }
            getLogView().setText(message);
        } else if (requestCode == REQUEST_LOCATION_SETTING) {
            getGoButton().setEnabled(true);
        }
    }
}
