/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.car.Car;
import android.car.hardware.power.CarPowerManager;
import android.car.hardware.power.CarPowerPolicy;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import javax.annotation.concurrent.GuardedBy;

/**
 * Test Power Component Behavior with respect to power policy
 */
public final class PowerPolicyTestActivity extends PassFailButtons.Activity {
    private static final String TAG = PowerPolicyTestActivity.class.getSimpleName();
    private static final String POWER_OFF_POLICY_ID = "cts_verifier_off";
    private static final String POWER_ON_POLICY_ID = "cts_verifier_on";

    private final Object mLock = new Object();
    private ComponentTestState mOffPolicyCheckState = ComponentTestState.NOT_TESTED;
    private ComponentTestState mOnPolicyCheckState = ComponentTestState.NOT_TESTED;

    private Car mCarApi;
    @GuardedBy("mLock")
    private CarPowerManager mPowerManager;
    private TextView mStatusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        initCarApi();

        View view = getLayoutInflater().inflate(R.layout.car_power_policy_test_main, null);
        setContentView(view);

        setInfoResources(R.string.car_power_policy_test, R.string.car_power_policy_test_desc,
                /* viewid= */ -1);
        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);
        mStatusText = findViewById(R.id.car_power_policy_results);

        Button offCheckButton =
                (Button) view.findViewById(R.id.car_component_power_off_policy_check);
        Button onCheckButton =
                (Button) view.findViewById(R.id.car_component_power_on_policy_check);

        onCheckButton.setEnabled(false);

        // location settings disable
        Button locationDisableButton = view.findViewById(R.id.car_location_settings_disable);
        locationDisableButton.setOnClickListener(v -> {
            startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            onCheckButton.setEnabled(true);
            locationDisableButton.setEnabled(false);
        });
        locationDisableButton.setEnabled(false);

        // bluetooth settings disable
        Button bluetoothDisableButton = view.findViewById(R.id.car_bluetooth_settings_disable);
        bluetoothDisableButton.setOnClickListener(v -> {
            startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
            locationDisableButton.setEnabled(true);
            bluetoothDisableButton.setEnabled(false);
        });
        bluetoothDisableButton.setEnabled(false);

        // Wifi settings disable
        Button wifiDisableButton = view.findViewById(R.id.car_wifi_settings_disable);
        wifiDisableButton.setOnClickListener(v -> {
            startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
            bluetoothDisableButton.setEnabled(true);
            wifiDisableButton.setEnabled(false);
        });
        wifiDisableButton.setEnabled(false);

        offCheckButton.setOnClickListener((buttonView) -> {
            if (checkCarPowerManager()) {
                synchronized (mLock) {
                    CarPowerPolicy curPolicy = mPowerManager.getCurrentPowerPolicy();
                    if (curPolicy != null && curPolicy.getPolicyId().equals(POWER_OFF_POLICY_ID)) {
                        mOffPolicyCheckState = ComponentTestState.PASS;
                        offCheckButton.setEnabled(false);
                        wifiDisableButton.setEnabled(true);
                        mStatusText.setText("Status: In Progress");
                    } else {
                        mOffPolicyCheckState = ComponentTestState.FAIL;
                        Log.e(TAG, "the cts_verifier_off policy is not the current policy");
                        mStatusText.setText("Status: error - not cts_verifier_off policy. "
                                + "Apply the policy first");
                    }
                }
            } else {
                offCheckButton.setEnabled(false);
                Log.e(TAG, "Cannot get CarPowerManager");
                mStatusText.setText("Status: Cannot get CarPowerManager. "
                        + "Click FAIL to complete the test.");
            }
            Log.d(TAG, "cts_verifier_off policy check is done");
        });

        // location settings enable
        Button locationEnableButton = view.findViewById(R.id.car_location_settings_enable);
        locationEnableButton.setOnClickListener(v -> {
            startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
            locationEnableButton.setEnabled(false);
            verifyStatus();
        });
        locationEnableButton.setEnabled(false);

        // bluetooth settings enable
        Button bluetoothEnableButton = view.findViewById(R.id.car_bluetooth_settings_enable);
        bluetoothEnableButton.setOnClickListener(v -> {
            startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
            locationEnableButton.setEnabled(true);
            bluetoothEnableButton.setEnabled(false);
        });
        bluetoothEnableButton.setEnabled(false);

        // Wifi settings enable
        Button wifiEnableButton = view.findViewById(R.id.car_wifi_settings_enable);
        wifiEnableButton.setOnClickListener(v -> {
            startActivity(new Intent(Settings.ACTION_WIRELESS_SETTINGS));
            bluetoothEnableButton.setEnabled(true);
            wifiEnableButton.setEnabled(false);
        });
        wifiEnableButton.setEnabled(false);

        onCheckButton.setOnClickListener((buttonView) -> {
            if (checkCarPowerManager()) {
                synchronized (mLock) {
                    CarPowerPolicy curPolicy = mPowerManager.getCurrentPowerPolicy();
                    if (curPolicy != null && curPolicy.getPolicyId().equals(POWER_ON_POLICY_ID)) {
                        mOnPolicyCheckState = ComponentTestState.PASS;
                        onCheckButton.setEnabled(false);
                        wifiEnableButton.setEnabled(true);
                        mStatusText.setText("Status: In Progress.");
                    } else {
                        mOnPolicyCheckState = ComponentTestState.FAIL;
                        Log.e(TAG, "cts_verifier_on is not the current power policy");
                        mStatusText.setText("Status: error - not cts_verifier_on policy. "
                                + "Apply the policy first");
                    }
                }
            } else {
                Log.e(TAG, "not able to connect to CarPowerManagementService");
                onCheckButton.setEnabled(false);
                mStatusText.setText("Status: not able to connect CarPowerManagementService. "
                        + "Click FAIL to complete the test");
            }
            Log.d(TAG, "Power On Policy Check is done");
        });

        mStatusText.setText("Status: In Progress");
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy");
        if (mCarApi != null) {
            mCarApi.disconnect();
        }
        super.onDestroy();
    }

    private void verifyStatus() {
        boolean testPassed = true;
        boolean testCompleted = true;
        ComponentTestState[] states = {
            mOffPolicyCheckState,
            mOnPolicyCheckState
        };
        String[] stateName = {
            "power_off policy verify",
            "power_on policy verify "
        };
        String statusStr = "Status: ";

        for (int i = 0; i < states.length; i++) {
            switch (states[i]) {
                case NOT_TESTED:
                    testCompleted &= false;
                    statusStr += stateName[i] + " NOT_TESTED. ";
                    break;
                case PASS:
                    statusStr += stateName[i] + " PASS. ";
                    break;
                case FAIL:
                    statusStr += stateName[i] + " FAIL. ";
                    testPassed &= false;
                    break;
                default:
                    testCompleted &= false;
                    testPassed &= false;
                    statusStr += stateName[i] + " unknown. ";
            }
        }

        mStatusText.setText(statusStr);
        getPassButton().setEnabled(testPassed);
        Log.d(TAG, "car UI power policy behavior verification: \n" + statusStr);
    }

    private boolean checkCarPowerManager() {
        if (mPowerManager == null) {
            Log.e(TAG, "not able to connect to the CarPowerManagementSeervice");
            mOffPolicyCheckState = ComponentTestState.FAIL;
            mOnPolicyCheckState = ComponentTestState.FAIL;
            return false;
        }
        return true;
    }

    private void initCarApi() {
        if (mCarApi != null && mCarApi.isConnected()) {
            mCarApi.disconnect();
            mCarApi = null;
        }
        mCarApi = Car.createCar(this, null, Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER,
                (Car car, boolean ready) -> {
                    initManagers(car, ready);
                });
    }

    private void initManagers(Car car, boolean ready) {
        synchronized (mLock) {
            if (ready) {
                mPowerManager = (CarPowerManager) car.getCarManager(
                        android.car.Car.POWER_SERVICE);
                Log.d(TAG, "initManagers() completed");
            } else {
                mPowerManager = null;
                Log.wtf(TAG, "initManagers() set to be null");
            }
        }
    }

    private enum ComponentTestState {
        NOT_TESTED,
        FAIL,
        PASS
    }
}
