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

package com.google.android.chre.test.setting;

import android.app.Instrumentation;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.location.NanoAppBinary;
import android.util.Log;

import com.google.android.chre.nanoapp.proto.ChreSettingsTest;
import com.google.android.utils.chre.SettingsUtil;

import org.junit.Assert;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * A test to check for behavior when Bluetooth settings are changed.
 */
public class ContextHubBleSettingsTestExecutor {
    private static final String TAG = "ContextHubBleSettingsTestExecutor";
    private final ContextHubSettingsTestExecutor mExecutor;

    private final Instrumentation mInstrumentation =
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation();

    private final Context mContext = mInstrumentation.getTargetContext();

    private final SettingsUtil mSettingsUtil;

    private boolean mInitialBluetoothEnabled;

    private boolean mInitialAirplaneMode;

    private boolean mInitialBluetoothScanningEnabled;

    public static class BluetoothUpdateListener {
        public BluetoothUpdateListener(int state) {
            mExpectedState = state;
        }

        // Expected state of the BT Adapter
        private final int mExpectedState;

        public CountDownLatch mBluetoothLatch = new CountDownLatch(1);

        public BroadcastReceiver mBluetoothUpdateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())
                        || BluetoothAdapter.ACTION_BLE_STATE_CHANGED.equals(
                                intent.getAction())) {
                    if (mExpectedState == intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)) {
                        mBluetoothLatch.countDown();
                    }
                }
            }
        };
    }

    public ContextHubBleSettingsTestExecutor(NanoAppBinary binary) {
        mExecutor = new ContextHubSettingsTestExecutor(binary);
        mSettingsUtil = new SettingsUtil(mContext);
    }

    /**
     * Should be called in a @Before method.
     */
    public void setUp() throws InterruptedException {
        mInitialBluetoothEnabled = mSettingsUtil.isBluetoothEnabled();
        mInitialBluetoothScanningEnabled = mSettingsUtil.isBluetoothScanningAlwaysEnabled();
        mInitialAirplaneMode = mSettingsUtil.isAirplaneModeOn();
        Log.d(TAG, "isBluetoothEnabled=" + mInitialBluetoothEnabled
                    + "; isBluetoothScanningEnabled=" + mInitialBluetoothScanningEnabled
                    + "; isAirplaneModeOn=" + mInitialAirplaneMode);
        mSettingsUtil.setAirplaneMode(false /* enable */);
        mExecutor.init();
    }

    public void runBleScanningTest() throws InterruptedException {
        runBleScanningTest(false /* enableBluetooth */, false /* enableBluetoothScanning */);
        runBleScanningTest(true /* enableBluetooth */, false /* enableBluetoothScanning */);
        runBleScanningTest(false /* enableBluetooth */, true /* enableBluetoothScanning */);
        runBleScanningTest(true /* enableBluetooth */, true /* enableBluetoothScanning */);
    }

    /**
     * Should be called in an @After method.
     */
    public void tearDown() throws InterruptedException {
        mExecutor.deinit();
        mSettingsUtil.setBluetooth(mInitialBluetoothEnabled);
        mSettingsUtil.setBluetoothScanningSettings(mInitialBluetoothScanningEnabled);
        mSettingsUtil.setAirplaneMode(mInitialAirplaneMode);
    }

    /**
     * Sets the BLE scanning settings on the device.
     * @param enable                    true to enable Bluetooth settings, false to disable it.
     * @param enableBluetoothScanning   if true, enable BLE scanning; false, otherwise
     */
    private void setBluetoothSettings(boolean enable, boolean enableBluetoothScanning) {
        // Check if already in the desired state
        if ((enable == mSettingsUtil.isBluetoothEnabled())
                 && (enableBluetoothScanning == mSettingsUtil.isBluetoothScanningAlwaysEnabled())) {
            return;
        }

        int state = BluetoothAdapter.STATE_OFF;
        if (enable) {
            state = BluetoothAdapter.STATE_ON;
        } else if (enableBluetoothScanning) {
            state = BluetoothAdapter.STATE_BLE_ON;
        }

        BluetoothUpdateListener bluetoothUpdateListener = new BluetoothUpdateListener(state);
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothAdapter.ACTION_BLE_STATE_CHANGED);
        mContext.registerReceiver(bluetoothUpdateListener.mBluetoothUpdateReceiver, filter);

        mSettingsUtil.setBluetooth(enable);
        mSettingsUtil.setBluetoothScanningSettings(enableBluetoothScanning);
        if (!enable && enableBluetoothScanning) {
            BluetoothManager bluetoothManager = mContext.getSystemService(BluetoothManager.class);
            Assert.assertTrue(bluetoothManager != null);
            BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
            Assert.assertTrue(bluetoothAdapter != null);
            Assert.assertTrue(bluetoothAdapter.enableBLE());
        }
        try {
            boolean success = bluetoothUpdateListener.mBluetoothLatch.await(10, TimeUnit.SECONDS);
            Assert.assertTrue("Timeout waiting for signal: bluetooth update listener", success);
            Assert.assertTrue(enable == mSettingsUtil.isBluetoothEnabled());
            Assert.assertTrue(enableBluetoothScanning
                    == mSettingsUtil.isBluetoothScanningAlwaysEnabled());

            // Wait a few seconds to ensure setting is propagated to CHRE path
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Assert.fail(e.getMessage());
        }

        mContext.unregisterReceiver(bluetoothUpdateListener.mBluetoothUpdateReceiver);
    }

    /**
     * Helper function to run the test
     *
     * @param enableBluetooth         if bluetooth is enabled
     * @param enableBluetoothScanning if bluetooth scanning is always enabled
     */
    private void runBleScanningTest(boolean enableBluetooth,
            boolean enableBluetoothScanning) throws InterruptedException {
        setBluetoothSettings(enableBluetooth, enableBluetoothScanning);

        boolean enableFeature = enableBluetooth || enableBluetoothScanning;
        ChreSettingsTest.TestCommand.State state = enableFeature
                ? ChreSettingsTest.TestCommand.State.ENABLED
                : ChreSettingsTest.TestCommand.State.DISABLED;
        mExecutor.startTestAssertSuccess(
                ChreSettingsTest.TestCommand.Feature.BLE_SCANNING, state);
    }
}
