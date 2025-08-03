/*
 * Copyright (C) 2022 The Android Open Source Project
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

import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.net.wifi.aware.PeerHandle;
import android.net.wifi.rtt.RangingResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;
import com.android.cts.verifier.presence.nan.WifiAwarePeer;
import com.android.cts.verifier.presence.nan.WifiAwarePeer.WifiAwarePeerListener;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;

/** Tests Wi-Fi Neighbor Awareness Networking (Wi-Fi NAN) Presence calibration requirements. */
public class NanAccuracyActivity extends PassFailButtons.Activity {
    private static final String TAG = NanAccuracyActivity.class.getName();
    private static final int MAX_ACCEPTABLE_RANGE = 2;

    // Report log schema
    private static final String KEY_REFERENCE_DEVICE = "reference_device";

    private HashMap<PeerHandle, ArrayList<Double>> mReceivedSamples;
    private WifiAwarePeer mWifiAwarePeer;
    private WifiManager mWifiManager;
    private TestResult mTestResult;
    private TestDistance mCurrentTestDistance;
    private Button mStartTestButton;
    private Button mStartPublishingButton;
    private CheckBox mReferenceDeviceCheckbox;
    private CheckBox mIsManualPassCheckbox;
    private LinearLayout mDutModeLayout;
    private LinearLayout mRefModeLayout;
    private TextView mDeviceFoundTextView;
    private TextView mTestStatusTextView;
    private TextView mServiceIdInfoTextView;
    private EditText mServiceIdInputEditText;
    private RadioGroup mTestDistanceRadioGroup;
    private String mReferenceDeviceName = "";
    private boolean mIsManualPass;
    private final WifiAwarePeerListener mWifiAwarePeerListener = new WifiAwarePeerListener() {
        @Override
        public void onDeviceFound(PeerHandle peerHandle) {
            Log.i(TAG, "Discovered NAN Peer");
            mReceivedSamples.put(peerHandle, new ArrayList<>());
            mDeviceFoundTextView.setText(getString(R.string.device_found_presence,
                    mReceivedSamples.get(peerHandle).size(), 100));
            mDeviceFoundTextView.setVisibility(View.VISIBLE);
            updateTestStatus(TestStatus.IN_PROGRESS);
        }

        @Override
        public void onReferenceDeviceNameReceived(String referenceDeviceName) {
            Log.i(TAG, "Reference device name received");
            makeToast("Reference device name received: " + referenceDeviceName);
            mReferenceDeviceName = referenceDeviceName;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.nan_accuracy);
        setPassFailButtonClickListeners();
        getPassButton().setEnabled(false);
        mReferenceDeviceCheckbox = findViewById(R.id.is_reference_device);
        mIsManualPassCheckbox = findViewById(R.id.is_manual_pass);
        mStartTestButton = findViewById(R.id.start_test);
        Button stopTestButton = findViewById(R.id.stop_test);
        mStartPublishingButton = findViewById(R.id.start_publishing);
        Button stopPublishingButton = findViewById(R.id.stop_publishing);
        mDutModeLayout = findViewById(R.id.dut_mode_layout);
        mRefModeLayout = findViewById(R.id.ref_mode_layout);
        mDeviceFoundTextView = findViewById(R.id.device_found_info);
        mTestStatusTextView = findViewById(R.id.test_status_info);
        mServiceIdInfoTextView = findViewById(R.id.service_id_info);
        mServiceIdInputEditText = findViewById(R.id.service_id_input);
        mTestDistanceRadioGroup = findViewById(R.id.test_distance_radio_group);
        mTestResult = new TestResult();
        DeviceFeatureChecker.checkFeatureSupported(this, getPassButton(),
                PackageManager.FEATURE_WIFI_AWARE);
        DeviceFeatureChecker.checkFeatureSupported(this, getPassButton(),
                PackageManager.FEATURE_WIFI_RTT);
        mWifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);

        Handler handler = new Handler(Looper.getMainLooper());
        mWifiAwarePeer = new WifiAwarePeer(this, handler);
        mReceivedSamples = new HashMap<>();
        setUpActivity();
        mIsManualPass = mIsManualPassCheckbox.isChecked();
        mReferenceDeviceCheckbox.setOnCheckedChangeListener(
                (buttonView, isChecked) -> setUpActivity());
        mIsManualPassCheckbox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mIsManualPass = isChecked;
        });
        mStartTestButton.setOnClickListener((view) -> startTest());
        stopTestButton.setOnClickListener((view) -> stopTest());
        mDeviceFoundTextView.setVisibility(View.GONE);
        mTestStatusTextView.setText(mTestResult.getTestStatus());
        mStartPublishingButton.setOnClickListener((view) -> startPublishing());
        stopPublishingButton.setOnClickListener((view) -> stopPublishing());
        mTestDistanceRadioGroup.setOnCheckedChangeListener(
                (group, checkedId) -> updateCurrentTestDistance());
        mServiceIdInfoTextView.setVisibility(View.GONE);
        updateCurrentTestDistance();
    }

    @Override
    public boolean requiresReportLog() {
        return true;
    }

    private void setUpActivity() {
        if (mReferenceDeviceCheckbox.isChecked()) {
            mDutModeLayout.setVisibility(View.GONE);
            mRefModeLayout.setVisibility(View.VISIBLE);
        } else {
            mDutModeLayout.setVisibility(View.VISIBLE);
            mRefModeLayout.setVisibility(View.GONE);
        }
    }

    private void startTest() {
        if (!checkWifiEnabled()) {
            return;
        }
        if (mServiceIdInputEditText.getText().toString().isEmpty()) {
            makeToast(
                    "Input the service ID shown on the publishing device before commencing test");
            return;
        }
        mReceivedSamples.clear();
        mWifiAwarePeer.subscribe(mWifiAwarePeerListener, this::nanResultListener,
                mServiceIdInputEditText.getText().toString());
        mStartTestButton.setEnabled(false);
    }

    private void stopTest() {
        mWifiAwarePeer.stop();
        mStartTestButton.setEnabled(true);
    }

    private void startPublishing() {
        if (!checkWifiEnabled()) {
            return;
        }
        byte randomServiceId = getRandomServiceId();
        String serviceIdInfoText = getString(R.string.service_id_info_presence,
                randomServiceId);
        mServiceIdInfoTextView.setText(serviceIdInfoText);
        mServiceIdInfoTextView.setVisibility(View.VISIBLE);
        mWifiAwarePeer.publish(randomServiceId);
        mStartPublishingButton.setEnabled(false);
    }

    private void stopPublishing() {
        mWifiAwarePeer.stop();
        mStartPublishingButton.setEnabled(true);
        mServiceIdInfoTextView.setVisibility(View.GONE);
    }

    private boolean checkWifiEnabled() {
        if (!mWifiManager.isWifiEnabled()) {
            makeToast("Turn on wifi to start test. You do not need to be connected to a network");
            Log.w(TAG, "Could not start test because Wifi is not enabled");
            return false;
        }
        return true;
    }

    private void nanResultListener(RangingResult result) {
        Log.i(TAG, "Got range result");
        if (mReceivedSamples.containsKey(result.getPeerHandle())) {
            Double distanceRangeMeters = result.getDistanceMm() / 1000.0;
            mReceivedSamples.get(result.getPeerHandle()).add(distanceRangeMeters);
            mDeviceFoundTextView.setText(getString(R.string.device_found_presence,
                    mReceivedSamples.get(result.getPeerHandle()).size(), 100));
            checkDataCollectionStatus(result.getPeerHandle());
        }
    }

    private void checkDataCollectionStatus(PeerHandle peerHandle) {
        if (mReceivedSamples.get(peerHandle).size() >= 100) {
            stopTest();
            computeTestResults(mReceivedSamples.get(peerHandle));
        }
    }

    private void computeTestResults(ArrayList<Double> data) {
        data.removeIf(
                measurementValue -> Math.abs(measurementValue - mCurrentTestDistance.getValue())
                        > MAX_ACCEPTABLE_RANGE);

        // Calculate range at 68th percentile
        if (data.size() >= 68) {
            updateTestStatus(TestStatus.PASSED);
            makeToast("Test passed for " + mCurrentTestDistance
                    + "\r\nPercentage of results in range: "
                    + new DecimalFormat("#.##").format((data.size() / (double) 100) * 100) + "%");
        } else {
            updateTestStatus(TestStatus.FAILED);
            makeToast("Test failed for " + mCurrentTestDistance
                    + "\r\nPercentage of results in range: "
                    + new DecimalFormat("#.##").format((data.size() / (double) 100) * 100) + "%");
        }
        if (mTestResult.isAllPassed()) {
            if (mIsManualPass) {
                getPassButton().setEnabled(true);
            } else {
                getPassButton().performClick();
            }
        }
        mReceivedSamples.clear();
    }

    private void updateCurrentTestDistance() {
        int checkedId = mTestDistanceRadioGroup.getCheckedRadioButtonId();
        if (checkedId == R.id.distance_10cm) {
            mCurrentTestDistance = TestDistance.TEN_CM;
        } else if (checkedId == R.id.distance_1m) {
            mCurrentTestDistance = TestDistance.ONE_METER;
        } else if (checkedId == R.id.distance_3m) {
            mCurrentTestDistance = TestDistance.THREE_METERS;
        } else if (checkedId == R.id.distance_5m) {
            mCurrentTestDistance = TestDistance.FIVE_METERS;
        }
    }

    private void updateTestStatus(TestStatus status) {
        mTestResult.setTestResult(mCurrentTestDistance, status);
        mTestStatusTextView.setText(mTestResult.getTestStatus());
    }

    private static byte getRandomServiceId() {
        Random random = new Random();
        byte[] randomDeviceIdArray = new byte[1];
        random.nextBytes(randomDeviceIdArray);
        return randomDeviceIdArray[0];
    }

    private void makeToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override
    public void recordTestResults() {
        if (mTestResult.isAllPassed()) {
            getReportLog().addValue(KEY_REFERENCE_DEVICE, mReferenceDeviceName,
                    ResultType.NEUTRAL, ResultUnit.NONE);
            getReportLog().submit();
        }
    }

    enum TestDistance {
        TEN_CM(0.1),
        ONE_METER(1.0),
        THREE_METERS(3.0),
        FIVE_METERS(5.0);

        private final double value;

        TestDistance(final double newValue) {
            value = newValue;
        }

        public double getValue() {
            return value;
        }
    }

    enum TestStatus {
        NOT_YET_RUN,
        IN_PROGRESS,
        FAILED,
        PASSED
    }

    private static class TestResult {
        private final HashMap<TestDistance, TestStatus> testResults;

        TestResult() {
            testResults = new HashMap<>();
            for (TestDistance distance : TestDistance.values()) {
                testResults.put(distance, TestStatus.NOT_YET_RUN);
            }
        }

        void setTestResult(TestDistance distance, TestStatus status) {
            testResults.put(distance, status);
        }

        String getTestStatus() {
            return "Test at 10cm: " + testResults.get(TestDistance.TEN_CM) + ", Test at 1m: "
                    + testResults.get(TestDistance.ONE_METER) + ", Test at 3m: " + testResults.get(
                    TestDistance.THREE_METERS) + ", Test at 5m: " + testResults.get(
                    TestDistance.FIVE_METERS);
        }

        boolean isAllPassed() {
            for (TestDistance distance : testResults.keySet()) {
                if (testResults.get(distance) != TestStatus.PASSED) {
                    return false;
                }
            }
            return true;
        }
    }
}