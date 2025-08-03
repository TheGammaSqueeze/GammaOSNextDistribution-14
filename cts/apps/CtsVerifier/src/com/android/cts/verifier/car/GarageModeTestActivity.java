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

package com.android.cts.verifier.car;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import java.text.DateFormat;

/**
 * Tests that Garage Mode runs at the end of a drive
 */
public final class GarageModeTestActivity extends PassFailButtons.Activity {
    // Requirements from Android 11 Compatibility Definition
    // 2.5.4. Performance and Power
    // Automotive device implementations:
    //
    // [8.3/A-1-3] MUST support Garage Mode
    // [8.3/A-1-4] SHOULD be in Garage Mode for at least 15 minutes unless:
    //                 The battery is drained.
    //                 No idle jobs are scheduled.
    //                 The driver exits Garage Mode.
    //
    // I understand that Google intends to require that Garage Mode is occasionally
    // allowed to run at least 30 seconds. This code tests for that minimum time.

    private static final String TAG = GarageModeTestActivity.class.getSimpleName();

    // The recommendation is for Garage Mode to run for 15 minutes. To allow
    // for some variation, run the test for 14 minutes and verify that it
    // ran at least 13 minutes.
    private static final int NUM_SECONDS_DURATION = 14 * 60;
    private static final long RECOMMENDED_DURATION_MS = (NUM_SECONDS_DURATION - 60) * 1000L;

    // The hard requirement is for Garage Mode to run for 30 seconds. Fail if
    // the test doesn't run at least this long.
    private static final long REQUIRED_DURATION_MS = 30 * 1000L;

    // Once a test is started, Garage Mode is expected to start within 10 minutes. If it doesn't,
    // the test is marked as fail.
    private static final long MAX_WAIT_UNTIL_GARAGE_MODE = 10 * 60 * 1000L;

    private TextView mStatusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        View view = getLayoutInflater().inflate(R.layout.garage_test_main, null);
        setContentView(view);

        setInfoResources(R.string.car_garage_mode_test, R.string.car_garage_mode_test_desc, -1);
        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);
        mStatusText = findViewById(R.id.car_garage_mode_results);

        // Garage Mode Monitor
        Button monitorButton = (Button) view.findViewById(R.id.launch_garage_monitor);
        monitorButton.setOnClickListener((buttonView) -> {
            Context context = GarageModeTestActivity.this;
            SharedPreferences prefs = context.getSharedPreferences(
                    GarageModeChecker.PREFS_FILE_NAME, Context.MODE_PRIVATE);
            long now = System.currentTimeMillis();
            SharedPreferences.Editor editor = prefs.edit();
            editor.putLong(GarageModeChecker.PREFS_INITIATION, now);
            editor.putLong(GarageModeChecker.PREFS_GARAGE_MODE_START, 0);
            editor.putLong(GarageModeChecker.PREFS_GARAGE_MODE_END, 0);
            editor.putLong(GarageModeChecker.PREFS_TERMINATION, 0);
            editor.putBoolean(GarageModeChecker.PREFS_HAD_CONNECTIVITY, false);
            editor.putLong(GarageModeChecker.PREFS_START_BOOT_COUNT,
                    GarageModeChecker.getBootCount(context));
            editor.commit();

            GarageModeChecker.scheduleAnIdleJob(context, NUM_SECONDS_DURATION);
            verifyStatus();
            Log.v(TAG, "Scheduled GarageModeChecker to run when idle");
        });

        // Wifi settings
        Button wifiButton = view.findViewById(R.id.car_wifi_settings);
        wifiButton.setOnClickListener(
                v -> startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        verifyStatus();
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        super.onWindowFocusChanged(hasWindowFocus);
        if (hasWindowFocus) {
            verifyStatus();
        }
    }

    private void verifyStatus() {
        Context context = GarageModeTestActivity.this;
        SharedPreferences prefs = context.getSharedPreferences(
                GarageModeChecker.PREFS_FILE_NAME, Context.MODE_PRIVATE);
        String resultsString;
        DateFormat dateTime = DateFormat.getDateTimeInstance();

        long now = System.currentTimeMillis();
        long initiateTime = prefs.getLong(GarageModeChecker.PREFS_INITIATION, 0);
        long garageModeStart = prefs.getLong(GarageModeChecker.PREFS_GARAGE_MODE_START, 0);
        long garageModeEnd = prefs.getLong(GarageModeChecker.PREFS_GARAGE_MODE_END, 0);
        long termination = prefs.getLong(GarageModeChecker.PREFS_TERMINATION, 0);
        long jobUpdate = prefs.getLong(GarageModeChecker.PREFS_JOB_UPDATE, 0);
        boolean hadConnectivity = prefs.getBoolean(GarageModeChecker.PREFS_HAD_CONNECTIVITY, false);
        long startBootCount = prefs.getLong(GarageModeChecker.PREFS_START_BOOT_COUNT, 0);
        long currentBootCount = GarageModeChecker.getBootCount(context);

        boolean testPassed = false;
        if (initiateTime == 0) {
            resultsString = "No results are available.\n\n"
                    + "Perform the indicated steps to run the test.";
        } else if (garageModeStart == 0) {
            if (now < initiateTime + MAX_WAIT_UNTIL_GARAGE_MODE) {
                resultsString = String.format("Waitng for Garage Mode to start.\n\n"
                                + "%s -- Test was enabled",
                        dateTime.format(initiateTime));
            } else {
                resultsString = String.format("Test failed.\n"
                                + "Garage Mode did not run.\n\n"
                                + "%s -- Test was enabled",
                        dateTime.format(initiateTime));
            }
        } else if (garageModeEnd > (garageModeStart + RECOMMENDED_DURATION_MS)) {
            testPassed = hadConnectivity;
            resultsString = String.format("Test %s.\n"
                            + "Garage Mode ran as required and for the recommended time.\n"
                            + "Connectivity was %savailable.\n\n"
                            + "%s -- Test was enabled\n"
                            + "%s -- Garage mode started\n"
                            + "%s -- Garage mode completed",
                    (testPassed ? "Passed" : "Failed"),
                    (hadConnectivity ? "" : "not "),
                    dateTime.format(initiateTime), dateTime.format(garageModeStart),
                    dateTime.format(garageModeEnd));
        } else if (termination > (garageModeStart + REQUIRED_DURATION_MS)) {
            testPassed = hadConnectivity;
            resultsString = String.format("Test %s.\n"
                            + "Garage Mode ran as required, "
                            + "but for less time than is recommended.\n"
                            + "  The CDD recommends that Garage Mode runs for 15 minutes.\n"
                            + "Connectivity was %savailable.\n\n"
                            + "%s -- Test was enabled\n"
                            + "%s -- Garage mode started\n"
                            + "%s -- Garage mode was terminated",
                    (testPassed ? "Passed" : "Failed"),
                    (hadConnectivity ? "" : "not "),
                    dateTime.format(initiateTime), dateTime.format(garageModeStart),
                    dateTime.format(termination));
        } else if (termination > 0) {
            resultsString = String.format("Test Failed.\n"
                            + "Garage Mode ran, but for less than the required time.\n"
                            + "  The minimum requirement is that Garage Mode runs for 30 seconds.\n"
                            + "  The CDD recommends that Garage Mode runs for 15 minutes.\n"
                            + "Connectivity was %savailable.\n\n"
                            + "%s -- Test was enabled\n"
                            + "%s -- Garage mode started\n"
                            + "%s -- Garage mode was terminated",
                    (hadConnectivity ? "" : "not "),
                    dateTime.format(initiateTime), dateTime.format(garageModeStart),
                    dateTime.format(termination));
        }  else if ((garageModeStart > 0 && garageModeEnd == 0)
                                        && (currentBootCount - startBootCount > 0)) {
            resultsString = "Test failed.\n\n"
                    + "Garage Mode started, but system restarted before it completed.\n\n"
                    + dateTime.format(initiateTime) + " -- Test was enabled\n"
                    + dateTime.format(garageModeStart) + " -- Garage mode started";
        } else if (now < jobUpdate + GarageModeChecker.MS_PER_ITERATION * 2) {
            resultsString = "Garage Mode started and test is running.\n\n"
                    + dateTime.format(initiateTime) + " -- Test was enabled\n"
                    + dateTime.format(garageModeStart) + " -- Garage mode started\n"
                    + dateTime.format(jobUpdate) + " -- Last job updated";
        } else {
            resultsString = "Test failed.\n\n"
                    + "Garage Mode started, but terminated unexpectedly.\n\n"
                    + dateTime.format(initiateTime) + " -- Test was enabled\n"
                    + dateTime.format(garageModeStart) + " -- Garage mode started";
        }
        mStatusText.setText(resultsString);
        getPassButton().setEnabled(testPassed);
    }
}
