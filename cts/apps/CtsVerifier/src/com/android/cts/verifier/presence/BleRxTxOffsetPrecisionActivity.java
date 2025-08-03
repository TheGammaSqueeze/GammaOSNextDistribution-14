/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.cts.verifier.presence;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertisingSetParameters;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.presence.ble.BleAdvertisingPacket;
import com.android.cts.verifier.presence.ble.BleScanner;
import com.android.cts.verifier.presence.ble.BleAdvertiser;
import com.android.cts.verifier.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Random;

public class BleRxTxOffsetPrecisionActivity extends PassFailButtons.Activity {
    private static final String TAG = BleRxTxOffsetPrecisionActivity.class.getName();
    private static final String DEVICE_NAME = Build.MODEL;

    // Report log schema
    private static final String KEY_REFERENCE_DEVICE = "reference_device";
    private static final String KEY_RSSI_MEDIAN_DUT = "rssi_median_dut";
    private static final String KEY_RSSI_MEDIAN_REFERENCE = "rssi_median_ref";

    // Thresholds
    private static final int MIN_RSSI_MEDIAN_DBM = -65;
    private static final int MAX_RSSI_MEDIAN_DBM = -35;

    private boolean isReferenceDevice;
    private BleScanner mBleScanner;
    private BleAdvertiser mBleAdvertiser;
    private BluetoothAdapter mBluetoothAdapter;
    private HashMap<String, ArrayList<Integer>> mRssiResultMap;
    private Button mStartTestButton;
    private Button mStopTestButton;
    private Button mStartAdvertisingButton;
    private Button mStopAdvertisingButton;
    private LinearLayout mDutModeLayout;
    private LinearLayout mRefModeLayout;
    private TextView mDeviceIdInfoTextView;
    private TextView mDeviceFoundTextView;
    private TextView mDutTestInfoTextView;
    private TextView mRefTestInfoTextView;
    private EditText mReferenceDeviceIdInput;
    private String mReferenceDeviceName;
    private CheckBox mIsReferenceDeviceCheckbox;
    private CheckBox mIsManualPassCheckbox;
    private CheckBox mUseExtendedAdvertisementCheckbox;
    private EditText mTxPowerInput;
    private boolean mIsManualPass;
    private byte mCurrentReferenceDeviceId = 0;
    private byte mRssiMedianFromReferenceDevice = 0;
    private int mRssiMedianOnDut = 0;
    private boolean mTestCompleted;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ble_rx_tx_offset_precision);
        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);
        mStartTestButton = findViewById(R.id.start_test);
        mStopTestButton = findViewById(R.id.stop_test);
        mStartAdvertisingButton = findViewById(R.id.start_advertising);
        mStopAdvertisingButton = findViewById(R.id.stop_advertising);
        mDeviceIdInfoTextView = findViewById(R.id.device_id_info);
        mDeviceFoundTextView = findViewById(R.id.device_found_info);
        mReferenceDeviceIdInput = findViewById(R.id.ref_device_id_input);
        mIsReferenceDeviceCheckbox = findViewById(R.id.is_reference_device);
        mUseExtendedAdvertisementCheckbox = findViewById(R.id.use_extended_advertisement);
        mTxPowerInput = findViewById(R.id.tx_power_input);
        mIsManualPassCheckbox = findViewById(R.id.is_manual_pass);
        mDutModeLayout = findViewById(R.id.dut_mode_layout);
        mRefModeLayout = findViewById(R.id.ref_mode_layout);
        mDutTestInfoTextView = findViewById(R.id.dut_test_result_info);
        mRefTestInfoTextView = findViewById(R.id.ref_test_result_info);
        DeviceFeatureChecker.checkFeatureSupported(this, getPassButton(),
                PackageManager.FEATURE_BLUETOOTH_LE);
        BluetoothManager bluetoothManager = getSystemService(BluetoothManager.class);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        mBleAdvertiser = new BleAdvertiser();
        mBleScanner = new BleScanner();
        mRssiResultMap = new HashMap<>();
        mStopTestButton.setEnabled(false);
        mStopAdvertisingButton.setEnabled(false);
        mDeviceIdInfoTextView.setVisibility(View.GONE);
        mDeviceFoundTextView.setVisibility(View.GONE);
        mRefTestInfoTextView.setVisibility(View.GONE);
        mDutTestInfoTextView.setVisibility(View.GONE);
        isReferenceDevice = mIsReferenceDeviceCheckbox.isChecked();
        mIsManualPass = mIsManualPassCheckbox.isChecked();
        checkUiMode();
        mIsReferenceDeviceCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isReferenceDevice = isChecked;
            checkUiMode();
        });
        mIsManualPassCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mIsManualPass = isChecked;
        });
        mUseExtendedAdvertisementCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                mTxPowerInput.setVisibility(View.VISIBLE);
            } else {
                mTxPowerInput.setVisibility(View.GONE);
            }
        });
        mStartTestButton.setOnClickListener(v -> startTestAsDut());
        mStopTestButton.setOnClickListener(v -> stopTest());
        mStartAdvertisingButton.setOnClickListener(v -> startTestAsReferenceDevice());
        mStopAdvertisingButton.setOnClickListener(v -> stopTest());
    }

    @Override
    public boolean requiresReportLog() {
        return true;
    }

    private void startTestAsDut() {
        mTestCompleted = false;
        mRssiMedianFromReferenceDevice = 0;
        mRssiMedianOnDut = 0;
        if (!checkBluetoothEnabled()) {
            return;
        }
        if (mReferenceDeviceIdInput.getText().toString().isEmpty()) {
            makeToast("Input the device ID shown on the advertising device before commencing test");
            return;
        }
        mStartTestButton.setEnabled(false);
        mStopTestButton.setEnabled(true);
        mDutTestInfoTextView.setVisibility(View.GONE);
        mCurrentReferenceDeviceId = Byte.parseByte(mReferenceDeviceIdInput.getText().toString());
        mIsReferenceDeviceCheckbox.setEnabled(false);
        startAdvertising();
        mBleScanner.startScanning((uuids,
                macAddress,
                deviceName,
                referenceDeviceName,
                deviceId,
                rssiMedian,
                rawRssi) -> {

            if (deviceId != mCurrentReferenceDeviceId) {
                //reference device does not match discovered device and scan should be discarded
                Log.i(TAG, "Reference device does not match discovered device. Skipping");
                return;
            }
            mRssiResultMap.computeIfAbsent(deviceName, k -> new ArrayList<>());
            ArrayList<Integer> resultList = mRssiResultMap.get(deviceName);
            resultList.add(rawRssi);
            mDeviceFoundTextView.setVisibility(View.VISIBLE);
            mReferenceDeviceName = referenceDeviceName;
            mRssiMedianFromReferenceDevice = rssiMedian;
            if (resultList.size() >= 1000) {
                mDeviceFoundTextView.setText(getString(R.string.result_pending_presence));
                Log.i(TAG, "Data collection complete");
                if (mRssiMedianFromReferenceDevice == 0) {
                    Log.i(TAG, "Awaiting rssi median from reference device");
                    return;
                }
                mBleScanner.stopScanning();
                computeTestResults(resultList);
            }
            String deviceFoundText = getString(R.string.device_found_presence,
                    resultList.size(), 1000);
            mDeviceFoundTextView.setText(deviceFoundText);
        });
    }

    private void startTestAsReferenceDevice() {
        if (!checkBluetoothEnabled()) {
            return;
        }
        mRefTestInfoTextView.setVisibility(View.GONE);
        startAdvertising();
        mBleScanner.startScanning((uuids,
                macAddress,
                deviceName,
                referenceDeviceName,
                deviceId,
                rssiMedian,
                rawRssi) -> {

            if (deviceId != mCurrentReferenceDeviceId) {
                //reference device does not match discovered device and scan should be discarded
                Log.i(TAG, "Reference device does not match discovered device. Skipping");
                return;
            }
            mRssiResultMap.computeIfAbsent(deviceName, k -> new ArrayList<>());
            ArrayList<Integer> resultList = mRssiResultMap.get(deviceName);
            resultList.add(rawRssi);
            if (resultList.size() >= 1000) {
                Log.i(TAG, "Data collection complete");
                mBleScanner.stopScanning();
                computeTestResults(resultList);
            }
        });
        mIsReferenceDeviceCheckbox.setEnabled(false);
    }

    private void computeTestResults(ArrayList<Integer> data) {
        Collections.sort(data);
        // Calculate median. Must be within -65dBm and -45dBm percentile
        int rssiMedian = data.get(500);
        if (isReferenceDevice) {
            mRssiMedianFromReferenceDevice = (byte) rssiMedian;
            String refDeviceTestInfo = getString(R.string.ref_test_result_info_presence,
                    rssiMedian);
            mRefTestInfoTextView.setText(refDeviceTestInfo);
            mRefTestInfoTextView.setVisibility(View.VISIBLE);
            mBleAdvertiser.stopAdvertising();
            // Starts advertising with the median test result
            startAdvertising();
            return;
        }
        mRssiMedianOnDut = rssiMedian;
        mTestCompleted = true;
        String dutDeviceTestInfo = getString(R.string.dut_test_result_info_presence,
                rssiMedian, mRssiMedianFromReferenceDevice);
        mDutTestInfoTextView.setVisibility(View.VISIBLE);
        mDutTestInfoTextView.setText(dutDeviceTestInfo);
        if (rssiMedian >= MIN_RSSI_MEDIAN_DBM && rssiMedian <= MAX_RSSI_MEDIAN_DBM
                && mRssiMedianFromReferenceDevice >= MIN_RSSI_MEDIAN_DBM
                && mRssiMedianFromReferenceDevice <= MAX_RSSI_MEDIAN_DBM) {
            makeToast("Test passed! TX Rssi median is: " + rssiMedian + ". Rx Rssi median is: "
                    + mRssiMedianFromReferenceDevice);
            if (mIsManualPass) {
                getPassButton().setEnabled(true);
            } else {
                getPassButton().performClick();
            }
        } else {
            makeToast("Test failed! TX Rssi median is: " + rssiMedian + ". Rx Rssi median is: "
                    + mRssiMedianFromReferenceDevice);
        }
        stopTest();
    }

    private void stopTest() {
        stopAdvertising();
        mBleScanner.stopScanning();
        for (String device : mRssiResultMap.keySet()) {
            mRssiResultMap.get(device).clear();
        }
        mStopTestButton.setEnabled(false);
        mStartTestButton.setEnabled(true);
        mIsReferenceDeviceCheckbox.setEnabled(true);
        mCurrentReferenceDeviceId = 0;
        mDeviceFoundTextView.setVisibility(View.GONE);
    }

    private void startAdvertising() {
        if (!checkBluetoothEnabled()) {
            return;
        }
        byte randomAdvertiserDeviceId = getAdvertiserDeviceId();
        String deviceIdInfoText = getString(R.string.device_id_info_presence,
                randomAdvertiserDeviceId);
        mDeviceIdInfoTextView.setText(deviceIdInfoText);
        mDeviceIdInfoTextView.setVisibility(View.VISIBLE);
        String packetDeviceName = "";
        if (DEVICE_NAME.length() > BleAdvertisingPacket.MAX_REFERENCE_DEVICE_NAME_LENGTH) {
            packetDeviceName = DEVICE_NAME.substring(0,
                    BleAdvertisingPacket.MAX_REFERENCE_DEVICE_NAME_LENGTH - 1);
        } else {
            packetDeviceName = DEVICE_NAME;
        }
        int advertiseTxPower = mTxPowerInput.getText().toString().isEmpty() ?
                AdvertisingSetParameters.TX_POWER_HIGH : Integer.parseInt(
                mTxPowerInput.getText().toString());
        mBleAdvertiser.startAdvertising(
                new BleAdvertisingPacket(packetDeviceName, randomAdvertiserDeviceId,
                        mRssiMedianFromReferenceDevice).toBytes(),
                mUseExtendedAdvertisementCheckbox.isChecked(), advertiseTxPower);
        mStartAdvertisingButton.setEnabled(false);
        mStopAdvertisingButton.setEnabled(true);
    }

    private void stopAdvertising() {
        mBleAdvertiser.stopAdvertising();
        mStopAdvertisingButton.setEnabled(false);
        mStartAdvertisingButton.setEnabled(true);
        mCurrentReferenceDeviceId = 0;
        mRssiMedianFromReferenceDevice = 0;
        mDeviceIdInfoTextView.setVisibility(View.GONE);
    }

    private boolean checkBluetoothEnabled() {
        if (!mBluetoothAdapter.isEnabled()) {
            makeToast("Bluetooth is not enabled, turn on bluetooth before starting test");
            Log.w(TAG, "Could not start test because Bluetooth is not enabled");
            return false;
        }
        return true;
    }

    private void checkUiMode() {
        if (isReferenceDevice) {
            mDutModeLayout.setVisibility(View.GONE);
            mRefModeLayout.setVisibility(View.VISIBLE);
        } else {
            mRefModeLayout.setVisibility(View.GONE);
            mDutModeLayout.setVisibility(View.VISIBLE);
        }
    }

    private byte getAdvertiserDeviceId() {
        if (mCurrentReferenceDeviceId != 0) {
            return mCurrentReferenceDeviceId;
        }
        Random random = new Random();
        byte[] randomDeviceIdArray = new byte[1];
        random.nextBytes(randomDeviceIdArray);
        mCurrentReferenceDeviceId = randomDeviceIdArray[0];
        return mCurrentReferenceDeviceId;
    }

    private void makeToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void recordTestResults() {
        if (mTestCompleted) {
            getReportLog().addValue(KEY_REFERENCE_DEVICE, mReferenceDeviceName,
                    ResultType.NEUTRAL, ResultUnit.NONE);
            getReportLog().addValue(KEY_RSSI_MEDIAN_DUT, mRssiMedianOnDut, ResultType.NEUTRAL,
                    ResultUnit.NONE);
            getReportLog().addValue(KEY_RSSI_MEDIAN_REFERENCE, mRssiMedianFromReferenceDevice,
                    ResultType.NEUTRAL, ResultUnit.NONE);
            getReportLog().submit();
        }
    }
}
