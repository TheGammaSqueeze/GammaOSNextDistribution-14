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

package com.android.cts.verifier.bluetooth;

import static android.bluetooth.le.AdvertisingSetCallback.ADVERTISE_SUCCESS;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertisingSet;
import android.bluetooth.le.AdvertisingSetCallback;
import android.bluetooth.le.AdvertisingSetParameters;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.bluetooth.le.PeriodicAdvertisingParameters;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tests {@link AdvertisingSet} and {@link AdvertisingSetCallback}.
 */
public class BleAdvertisingSetTestActivity extends PassFailButtons.Activity {

    private static final String TAG = "BleAdvertisingSetTestActivity";
    private static final int TIMEOUT_MS = 5000;

    private static final int PASS_FLAG_START = 0x1;
    private static final int PASS_FLAG_ENABLE_DISABLE = 0x2;
    private static final int PASS_FLAG_SET_ADVERTISING_DATA = 0x4;
    private static final int PASS_FLAG_SET_ADVERTISING_PARAMS = 0x8;
    private static final int PASS_FLAG_SET_PERIODIC_ADVERTISING_DATA = 0x10;
    private static final int PASS_FLAG_SET_PERIODIC_ADVERTISING_ENABLED_DISABLED = 0x20;
    private static final int PASS_FLAG_SET_PERIODIC_ADVERTISING_PARAMS = 0x40;
    private static final int PASS_FLAG_SET_SCAN_RESPONSE_DATA = 0x80;
    private static final int PASS_FLAG_STOP = 0x100;
    private static final int PASS_FLAG_ALL = 0x1FF;

    private static final int TEST_ADAPTER_INDEX_START = 0;
    private static final int TEST_ADAPTER_INDEX_ENABLE_DISABLE = 1;
    private static final int TEST_ADAPTER_INDEX_SET_ADVERTISING_DATA = 2;
    private static final int TEST_ADAPTER_INDEX_SET_ADVERTISING_PARAMS = 3;
    private static final int TEST_ADAPTER_INDEX_SET_PERIODIC_ADVERTISING_DATA = 4;
    private static final int TEST_ADAPTER_INDEX_SET_PERIODIC_ADVERTISING_ENABLED_DISABLED = 5;
    private static final int TEST_ADAPTER_INDEX_SET_PERIODIC_ADVERTISING_PARAMS = 6;
    private static final int TEST_ADAPTER_INDEX_SET_SCAN_RESPONSE_DATA = 7;
    private static final int TEST_ADAPTER_INDEX_STOP = 8;

    private static final AdvertisingSetParameters ADVERTISING_SET_PARAMETERS =
            new AdvertisingSetParameters.Builder().setLegacyMode(true).build();

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeAdvertiser mAdvertiser;
    private TestAdvertisingSetCallback mCallback;

    private TestAdapter mTestAdapter;
    private Button mStartTestButton;

    private long mAllTestsPassed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.ble_advertising_set);
        setPassFailButtonClickListeners();
        setInfoResources(R.string.ble_advertising_set_test_name,
                R.string.ble_advertising_set_test_info, -1);
        getPassButton().setEnabled(false);

        mTestAdapter = new TestAdapter(this, setupTestList());
        ListView listView = findViewById(R.id.ble_advertising_set_tests);
        listView.setAdapter(mTestAdapter);

        mStartTestButton = findViewById(R.id.ble_advertising_set_start_test);
        mStartTestButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mStartTestButton.setEnabled(false);
                mStartTestButton.setText(R.string.ble_advertising_set_running_test);

                HandlerThread testHandlerThread = new HandlerThread("TestHandlerThread");
                testHandlerThread.start();
                Handler handler = new Handler(testHandlerThread.getLooper());
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!mBluetoothAdapter.isEnabled()) {
                            // If BluetoothAdapter was previously not enabled, we need to get the
                            // BluetoothLeAdvertiser instance again.
                            mBluetoothAdapter.enable();
                            mAdvertiser = mBluetoothAdapter.getBluetoothLeAdvertiser();
                        }

                        try {
                            startAdvertisingSet();
                            testEnableAndDisableAdvertising();
                            testSetAdvertisingData();
                            testSetAdvertisingParameters();
                            testPeriodicAdvertising();
                            testSetScanResponseData();
                            stopAdvertisingSet();
                        } catch (InterruptedException e) {
                            Log.e(TAG, "Interrupted while running tests", e);
                        } catch (AssertionError e) {
                            Log.e(TAG, "Test failed", e);
                        }

                        // Disable bluetooth adapter
                        mBluetoothAdapter.disable();

                        BleAdvertisingSetTestActivity.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                mStartTestButton.setText(
                                        R.string.ble_advertising_set_finished_test);

                                // Update test list to reflect whether the tests passed or not.
                                mTestAdapter.notifyDataSetChanged();

                                if (mAllTestsPassed == PASS_FLAG_ALL) {
                                    getPassButton().setEnabled(true);
                                }
                            }
                        });
                    }
                });
            }
        });

        mAllTestsPassed = 0;

        mBluetoothManager = getSystemService(BluetoothManager.class);
        mBluetoothAdapter = mBluetoothManager.getAdapter();
        mAdvertiser = mBluetoothAdapter.getBluetoothLeAdvertiser();
        mCallback = new TestAdvertisingSetCallback();
    }

    private void startAdvertisingSet() throws InterruptedException {
        mAdvertiser.startAdvertisingSet(ADVERTISING_SET_PARAMETERS,
                /* advertiseData= */ null, /* scanResponse= */ null,
                /* periodicParameters= */ null, /* periodicData= */ null,
                mCallback);
        assertTrue(mCallback.mAdvertisingSetStartedLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertEquals(ADVERTISE_SUCCESS, mCallback.mAdvertisingSetStartedStatus.get());
        assertNotNull(mCallback.mAdvertisingSet);

        mAllTestsPassed |= PASS_FLAG_START;
        mTestAdapter.setTestPass(TEST_ADAPTER_INDEX_START);
    }

    private void testEnableAndDisableAdvertising() throws InterruptedException {
        mCallback.reset();

        mCallback.mAdvertisingSet.get().enableAdvertising(/* enable= */ true, /* duration= */ 0,
                /* maxExtendedAdvertisingEvents= */ 0);
        assertTrue(mCallback.mAdvertisingEnabledLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertEquals(ADVERTISE_SUCCESS, mCallback.mAdvertisingEnabledStatus.get());

        mCallback.mAdvertisingSet.get().enableAdvertising(/* enable= */ false, /* duration= */ 0,
                /* maxExtendedAdvertisingEvents= */ 0);
        assertTrue(mCallback.mAdvertisingDisabledLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertEquals(ADVERTISE_SUCCESS, mCallback.mAdvertisingDisabledStatus.get());


        mAllTestsPassed |= PASS_FLAG_ENABLE_DISABLE;
        mTestAdapter.setTestPass(TEST_ADAPTER_INDEX_ENABLE_DISABLE);
    }

    private void testSetAdvertisingData() throws InterruptedException {
        mCallback.reset();

        mCallback.mAdvertisingSet.get().setAdvertisingData(new AdvertiseData.Builder().build());
        assertTrue(mCallback.mAdvertisingDataSetLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertEquals(ADVERTISE_SUCCESS, mCallback.mAdvertisingDataSetStatus.get());

        mAllTestsPassed |= PASS_FLAG_SET_ADVERTISING_DATA;
        mTestAdapter.setTestPass(TEST_ADAPTER_INDEX_SET_ADVERTISING_DATA);
    }

    private void testSetScanResponseData() throws InterruptedException {
        mCallback.reset();

        mCallback.mAdvertisingSet.get().setScanResponseData(new AdvertiseData.Builder().build());
        assertTrue(mCallback.mScanResponseDataSetLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertEquals(ADVERTISE_SUCCESS, mCallback.mScanResponseDataSetStatus.get());

        mAllTestsPassed |= PASS_FLAG_SET_SCAN_RESPONSE_DATA;
        mTestAdapter.setTestPass(TEST_ADAPTER_INDEX_SET_SCAN_RESPONSE_DATA);
    }

    private void testSetAdvertisingParameters() throws InterruptedException {
        mCallback.reset();

        mCallback.mAdvertisingSet.get().enableAdvertising(false, /* duration= */ 0,
                /* maxExtendedAdvertisingEvents= */ 0);
        assertTrue(mCallback.mAdvertisingDisabledLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertEquals(ADVERTISE_SUCCESS, mCallback.mAdvertisingDisabledStatus.get());

        mCallback.mAdvertisingSet.get().setAdvertisingParameters(
                new AdvertisingSetParameters.Builder()
                        .setLegacyMode(true)
                        .setScannable(false)
                        .build());
        assertTrue(mCallback.mAdvertisingParametersUpdatedLatch.await(TIMEOUT_MS,
                TimeUnit.MILLISECONDS));
        assertEquals(ADVERTISE_SUCCESS, mCallback.mAdvertisingParametersUpdatedStatus.get());

        mAllTestsPassed |= PASS_FLAG_SET_ADVERTISING_PARAMS;
        mTestAdapter.setTestPass(TEST_ADAPTER_INDEX_SET_ADVERTISING_PARAMS);
    }

    // The following order of commands follows the diagram of Bluetooth Core Specification,
    // Version 5.3, Vol 6, Part D, Figure 3.7: Periodic advertising.
    private void testPeriodicAdvertising() throws InterruptedException {
        if (!mBluetoothAdapter.isLePeriodicAdvertisingSupported()) {
            mAllTestsPassed |= PASS_FLAG_SET_PERIODIC_ADVERTISING_PARAMS
                    | PASS_FLAG_SET_PERIODIC_ADVERTISING_DATA
                    | PASS_FLAG_SET_PERIODIC_ADVERTISING_ENABLED_DISABLED;
            mTestAdapter.setTestPass(TEST_ADAPTER_INDEX_SET_PERIODIC_ADVERTISING_PARAMS);
            mTestAdapter.setTestPass(TEST_ADAPTER_INDEX_SET_PERIODIC_ADVERTISING_DATA);
            mTestAdapter.setTestPass(TEST_ADAPTER_INDEX_SET_PERIODIC_ADVERTISING_ENABLED_DISABLED);
            return;
        }

        mCallback.reset();

        mCallback.mAdvertisingSet.get().setAdvertisingParameters(
                new AdvertisingSetParameters.Builder().build());

        assertTrue(mCallback.mAdvertisingParametersUpdatedLatch.await(TIMEOUT_MS,
                TimeUnit.MILLISECONDS));
        assertEquals(ADVERTISE_SUCCESS, mCallback.mAdvertisingParametersUpdatedStatus.get());

        mCallback.mAdvertisingSet.get().setPeriodicAdvertisingParameters(
                new PeriodicAdvertisingParameters.Builder().build());
        assertTrue(mCallback.mPeriodicAdvertisingParamsUpdatedLatch.await(TIMEOUT_MS,
                TimeUnit.MILLISECONDS));
        assertEquals(ADVERTISE_SUCCESS, mCallback.mPeriodicAdvertisingParamsUpdatedStatus.get());

        mAllTestsPassed |= PASS_FLAG_SET_PERIODIC_ADVERTISING_PARAMS;
        mTestAdapter.setTestPass(TEST_ADAPTER_INDEX_SET_PERIODIC_ADVERTISING_PARAMS);

        // Enable advertising before periodicAdvertising
        // If the advertising set is not currently enabled (see the
        // HCI_LE_Set_Extended_Advertising_Enable command), the periodic
        // advertising is not started until the advertising set is enabled.
        mCallback.mAdvertisingSet.get().enableAdvertising(true, /* duration= */ 0,
                /* maxExtendedAdvertisingEvents= */ 0);

        mCallback.mAdvertisingSet.get().setPeriodicAdvertisingEnabled(true);
        assertTrue(mCallback.mPeriodicAdvertisingEnabledLatch.await(TIMEOUT_MS,
                TimeUnit.MILLISECONDS));
        assertEquals(ADVERTISE_SUCCESS, mCallback.mPeriodicAdvertisingEnabledStatus.get());

        mCallback.mAdvertisingSet.get().setPeriodicAdvertisingData(new AdvertiseData.Builder()
                .build());
        assertTrue(mCallback.mPeriodicAdvertisingDataSetLatch.await(TIMEOUT_MS,
                TimeUnit.MILLISECONDS));
        assertEquals(ADVERTISE_SUCCESS, mCallback.mPeriodicAdvertisingDataSetStatus.get());

        mAllTestsPassed |= PASS_FLAG_SET_PERIODIC_ADVERTISING_DATA;
        mTestAdapter.setTestPass(TEST_ADAPTER_INDEX_SET_PERIODIC_ADVERTISING_DATA);

        mCallback.mAdvertisingSet.get().setPeriodicAdvertisingEnabled(false);
        // Disable advertising after periodicAdvertising
        mCallback.mAdvertisingSet.get().enableAdvertising(false, /* duration= */ 0,
                /* maxExtendedAdvertisingEvents= */ 0);
        assertTrue(mCallback.mPeriodicAdvertisingDisabledLatch.await(TIMEOUT_MS,
                TimeUnit.MILLISECONDS));
        assertEquals(ADVERTISE_SUCCESS, mCallback.mPeriodicAdvertisingDisabledStatus.get());

        mAllTestsPassed |= PASS_FLAG_SET_PERIODIC_ADVERTISING_ENABLED_DISABLED;

        mTestAdapter.setTestPass(TEST_ADAPTER_INDEX_SET_PERIODIC_ADVERTISING_ENABLED_DISABLED);
    }

    private void stopAdvertisingSet() throws InterruptedException {
        mAdvertiser.stopAdvertisingSet(mCallback);
        assertTrue(mCallback.mAdvertisingSetStoppedLatch.await(TIMEOUT_MS,
                TimeUnit.MILLISECONDS));

        mAllTestsPassed |= PASS_FLAG_STOP;
        mTestAdapter.setTestPass(TEST_ADAPTER_INDEX_STOP);
    }

    private List<Integer> setupTestList() {
        List<Integer> testList = new ArrayList<>();
        testList.add(R.string.ble_advertising_set_start);
        testList.add(R.string.ble_advertising_set_enable_disable);
        testList.add(R.string.ble_advertising_set_advertising_data);
        testList.add(R.string.ble_advertising_set_advertising_params);
        testList.add(R.string.ble_advertising_set_periodic_advertising_data);
        testList.add(R.string.ble_advertising_set_periodic_advertising_enabled_disabled);
        testList.add(R.string.ble_advertising_set_periodic_advertising_params);
        testList.add(R.string.ble_advertising_set_scan_response_data);
        testList.add(R.string.ble_advertising_set_stop);
        return testList;
    }

    private static class TestAdvertisingSetCallback extends AdvertisingSetCallback {
        public CountDownLatch mAdvertisingSetStartedLatch = new CountDownLatch(1);
        public CountDownLatch mAdvertisingEnabledLatch = new CountDownLatch(1);
        public CountDownLatch mAdvertisingDisabledLatch = new CountDownLatch(1);
        public CountDownLatch mAdvertisingParametersUpdatedLatch = new CountDownLatch(1);
        public CountDownLatch mAdvertisingDataSetLatch = new CountDownLatch(1);
        public CountDownLatch mScanResponseDataSetLatch = new CountDownLatch(1);
        public CountDownLatch mAdvertisingSetStoppedLatch = new CountDownLatch(1);
        public CountDownLatch mPeriodicAdvertisingEnabledLatch = new CountDownLatch(1);
        public CountDownLatch mPeriodicAdvertisingDisabledLatch = new CountDownLatch(1);
        public CountDownLatch mPeriodicAdvertisingParamsUpdatedLatch = new CountDownLatch(1);
        public CountDownLatch mPeriodicAdvertisingDataSetLatch = new CountDownLatch(1);

        public AtomicInteger mAdvertisingSetStartedStatus = new AtomicInteger();
        public AtomicInteger mAdvertisingEnabledStatus = new AtomicInteger();
        public AtomicInteger mAdvertisingDisabledStatus = new AtomicInteger();
        public AtomicInteger mAdvertisingParametersUpdatedStatus = new AtomicInteger();
        public AtomicInteger mAdvertisingDataSetStatus = new AtomicInteger();
        public AtomicInteger mScanResponseDataSetStatus = new AtomicInteger();
        public AtomicInteger mPeriodicAdvertisingEnabledStatus = new AtomicInteger();
        public AtomicInteger mPeriodicAdvertisingDisabledStatus = new AtomicInteger();
        public AtomicInteger mPeriodicAdvertisingParamsUpdatedStatus = new AtomicInteger();
        public AtomicInteger mPeriodicAdvertisingDataSetStatus = new AtomicInteger();

        public AtomicReference<AdvertisingSet> mAdvertisingSet = new AtomicReference();

        @Override
        public void onAdvertisingSetStarted(AdvertisingSet advertisingSet, int txPower,
                int status) {
            super.onAdvertisingSetStarted(advertisingSet, txPower, status);
            mAdvertisingSetStartedStatus.set(status);
            mAdvertisingSet.set(advertisingSet);
            mAdvertisingSetStartedLatch.countDown();
        }

        @Override
        public void onAdvertisingEnabled(AdvertisingSet advertisingSet, boolean enable,
                int status) {
            super.onAdvertisingEnabled(advertisingSet, enable, status);
            if (enable) {
                mAdvertisingEnabledStatus.set(status);
                mAdvertisingEnabledLatch.countDown();
            } else {
                mAdvertisingDisabledStatus.set(status);
                mAdvertisingDisabledLatch.countDown();
            }
        }

        @Override
        public void onAdvertisingParametersUpdated(AdvertisingSet advertisingSet, int txPower,
                int status) {
            super.onAdvertisingParametersUpdated(advertisingSet, txPower, status);
            mAdvertisingParametersUpdatedStatus.set(status);
            mAdvertisingParametersUpdatedLatch.countDown();
        }

        @Override
        public void onAdvertisingDataSet(AdvertisingSet advertisingSet, int status) {
            super.onAdvertisingDataSet(advertisingSet, status);
            mAdvertisingDataSetStatus.set(status);
            mAdvertisingDataSetLatch.countDown();
        }

        @Override
        public void onPeriodicAdvertisingParametersUpdated(AdvertisingSet advertisingSet,
                int status) {
            super.onPeriodicAdvertisingParametersUpdated(advertisingSet, status);
            mPeriodicAdvertisingParamsUpdatedStatus.set(status);
            mPeriodicAdvertisingParamsUpdatedLatch.countDown();
        }

        @Override
        public void onPeriodicAdvertisingDataSet(AdvertisingSet advertisingSet, int status) {
            super.onPeriodicAdvertisingDataSet(advertisingSet, status);
            mPeriodicAdvertisingDataSetStatus.set(status);
            mPeriodicAdvertisingDataSetLatch.countDown();
        }

        @Override
        public void onPeriodicAdvertisingEnabled(AdvertisingSet advertisingSet, boolean enable,
                int status) {
            super.onPeriodicAdvertisingEnabled(advertisingSet, enable, status);
            if (enable) {
                mPeriodicAdvertisingEnabledStatus.set(status);
                mPeriodicAdvertisingEnabledLatch.countDown();
            } else {
                mPeriodicAdvertisingDisabledStatus.set(status);
                mPeriodicAdvertisingDisabledLatch.countDown();
            }
        }

        @Override
        public void onScanResponseDataSet(AdvertisingSet advertisingSet, int status) {
            super.onScanResponseDataSet(advertisingSet, status);
            mScanResponseDataSetStatus.set(status);
            mScanResponseDataSetLatch.countDown();
        }

        @Override
        public void onAdvertisingSetStopped(AdvertisingSet advertisingSet) {
            super.onAdvertisingSetStopped(advertisingSet);
            mAdvertisingSetStoppedLatch.countDown();
        }

        public void reset() {
            mAdvertisingSetStartedLatch = new CountDownLatch(1);
            mAdvertisingEnabledLatch = new CountDownLatch(1);
            mAdvertisingParametersUpdatedLatch = new CountDownLatch(1);
            mAdvertisingDataSetLatch = new CountDownLatch(1);
            mPeriodicAdvertisingParamsUpdatedLatch = new CountDownLatch(1);
            mPeriodicAdvertisingDataSetLatch = new CountDownLatch(1);
            mPeriodicAdvertisingEnabledLatch = new CountDownLatch(1);
            mPeriodicAdvertisingDisabledLatch = new CountDownLatch(1);
            mScanResponseDataSetLatch = new CountDownLatch(1);

            mAdvertisingSetStartedStatus = new AtomicInteger();
            mAdvertisingEnabledStatus = new AtomicInteger();
            mAdvertisingDisabledStatus = new AtomicInteger();
            mAdvertisingParametersUpdatedStatus = new AtomicInteger();
            mAdvertisingDataSetStatus = new AtomicInteger();
            mPeriodicAdvertisingParamsUpdatedStatus = new AtomicInteger();
            mPeriodicAdvertisingDataSetStatus = new AtomicInteger();
            mPeriodicAdvertisingEnabledStatus = new AtomicInteger();
            mPeriodicAdvertisingDisabledStatus = new AtomicInteger();
            mScanResponseDataSetStatus = new AtomicInteger();
        }
    }
}
