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

package android.bluetooth.test_utils;

import static com.android.compatibility.common.util.SystemUtil.runShellCommandOrThrow;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.BeforeAfterRule;

import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * This is a test rule that, when used in a test, will enable Bluetooth before the test starts. When
 * the test is done, Bluetooth will be disabled if and only if it was disabled before the test
 * started. If setTestMode is set to true, the Bluetooth scanner will return a hardcoded set of
 * Bluetooth scan results while the test runs .
 */
public class EnableBluetoothRule extends BeforeAfterRule {
    private final Context mContext = InstrumentationRegistry.getInstrumentation().getContext();
    private final BluetoothAdapter mBluetoothAdapter =
            mContext.getSystemService(BluetoothManager.class).getAdapter();
    private final boolean mEnableTestMode;
    private final boolean mToggleBluetooth;

    private boolean mWasBluetoothAdapterEnabled = true;

    /** Empty constructor */
    public EnableBluetoothRule() {
        mEnableTestMode = false;
        mToggleBluetooth = false;
    }

    /**
     * Constructor that allows test mode
     *
     * @param enableTestMode whether test mode is enabled
     * @param toggleBluetooth whether to toggle Bluetooth at the beginning of the test if it is
     *     already enabled
     */
    public EnableBluetoothRule(boolean enableTestMode, boolean toggleBluetooth) {
        mEnableTestMode = enableTestMode;
        mToggleBluetooth = toggleBluetooth;
    }

    /**
     * Constructor that allows test mode
     *
     * @param enableTestMode whether test mode is enabled
     */
    public EnableBluetoothRule(boolean enableTestMode) {
        mEnableTestMode = enableTestMode;
        mToggleBluetooth = false;
    }

    private void enableBluetoothAdapter() {
        assertThat(BluetoothAdapterUtils.enableAdapter(mBluetoothAdapter, mContext)).isTrue();
    }

    private void disableBluetoothAdapter() {
        assertThat(BluetoothAdapterUtils.disableAdapter(mBluetoothAdapter, mContext)).isTrue();
    }

    private void enableBluetoothTestMode() {
        runShellCommandOrThrow(
                "dumpsys activity service"
                        + " com.android.bluetooth.btservice.AdapterService set-test-mode enabled");
    }

    private void disableBluetoothTestMode() {
        runShellCommandOrThrow(
                "dumpsys activity service"
                        + " com.android.bluetooth.btservice.AdapterService set-test-mode disabled");
    }

    @Override
    protected void onBefore(Statement base, Description description) {
        assumeTrue(TestUtils.hasBluetooth());
        mWasBluetoothAdapterEnabled = mBluetoothAdapter.isEnabled();
        if (!mWasBluetoothAdapterEnabled) {
            enableBluetoothAdapter();
        } else if (mToggleBluetooth) {
            disableBluetoothAdapter();
            enableBluetoothAdapter();
        }
        if (mEnableTestMode) {
            enableBluetoothTestMode();
        }
    }

    @Override
    protected void onAfter(Statement base, Description description) {
        assumeTrue(TestUtils.hasBluetooth());
        disableBluetoothTestMode();
        if (!mWasBluetoothAdapterEnabled) {
            disableBluetoothAdapter();
        }
    }
}
