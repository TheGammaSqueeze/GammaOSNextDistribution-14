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

package com.android.car.settings.testutils;

import static junit.framework.Assert.fail;

import android.annotation.StringRes;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import com.android.car.settings.R;
import com.android.settingslib.bluetooth.LocalBluetoothProfile;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Collection of helper methods for testing Bluetooth.
 */
public class BluetoothTestUtils {

    private static final int BLUETOOTH_TOGGLE_TIMEOUT = 30; // in seconds
    private static final IntentFilter STATE_INTENT_FILTER = new IntentFilter(
            BluetoothAdapter.ACTION_STATE_CHANGED);
    private static final int TEST_RES_ID = R.string.settings_label; // placeholder

    /**
     * Set the default bluetooth adapter state.
     *
     * Because the adapter isn't enabled/disabled immediately after calling enable or disable,
     * this helper function will listen and wait for the state to change before returning (or will
     * fail upon timeout).
     */
    public static void setBluetoothState(Context context, boolean enable) {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter.isEnabled() == enable) {
            // state is already correct
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);
        LatchBroadcastReceiver receiver = new LatchBroadcastReceiver(latch, enable);
        context.registerReceiver(receiver, STATE_INTENT_FILTER);
        if (enable) {
            adapter.enable();
        } else {
            adapter.disable();
        }
        try {
            latch.await(BLUETOOTH_TOGGLE_TIMEOUT, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            fail("Unable to toggle bluetooth");
        } finally {
            context.unregisterReceiver(receiver);
        }
        if (adapter.isEnabled() != enable) {
            // failed to toggle
            fail("Unable to toggle bluetooth");
        }
    }

    private static class LatchBroadcastReceiver extends BroadcastReceiver {
        private final CountDownLatch mLatch;
        private final boolean mEnable;

        private LatchBroadcastReceiver(CountDownLatch latch, boolean enable) {
            mLatch = latch;
            mEnable = enable;
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
            if (mEnable && state == BluetoothAdapter.STATE_ON) {
                mLatch.countDown();
            } else if (!mEnable && state == BluetoothAdapter.STATE_OFF) {
                mLatch.countDown();
            }
        }
    }

    public static class TestLocalBluetoothProfile implements LocalBluetoothProfile {
        private final int mProfileId;
        private boolean mIsEnabled;

        public TestLocalBluetoothProfile(int profileId) {
            mProfileId = profileId;
        }

        @Override
        public boolean accessProfileEnabled() {
            return false;
        }

        @Override
        public boolean isAutoConnectable() {
            return false;
        }

        @Override
        public int getConnectionStatus(BluetoothDevice device) {
            return 0;
        }

        @Override
        public boolean isEnabled(BluetoothDevice device) {
            return mIsEnabled;
        }

        @Override
        public int getConnectionPolicy(BluetoothDevice device) {
            return 0;
        }

        @Override
        public boolean setEnabled(BluetoothDevice device, boolean enabled) {
            return mIsEnabled = enabled;
        }

        @Override
        public boolean isProfileReady() {
            return false;
        }

        @Override
        public int getProfileId() {
            return mProfileId;
        }

        @Override
        public int getOrdinal() {
            return 0;
        }

        @Override
        @StringRes
        public int getNameResource(BluetoothDevice device) {
            return TEST_RES_ID;
        }

        @Override
        public int getSummaryResourceForDevice(BluetoothDevice device) {
            return 0;
        }

        @Override
        public int getDrawableResource(BluetoothClass btClass) {
            return 0;
        }
    }
}
