/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.cts.deviceowner;

import static com.google.common.truth.Truth.assertWithMessage;

import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.os.UserManager;
import android.util.DebugUtils;
import android.util.Log;

import com.android.bedstead.nene.TestApis;
import com.android.internal.util.ArrayUtils;

/**
 * Test interaction between {@link UserManager#DISALLOW_BLUETOOTH} user restriction and the state
 * of Bluetooth.
 */
public class BluetoothRestrictionTest extends BaseDeviceOwnerTest {

    private static final String TAG = BluetoothRestrictionTest.class.getSimpleName();
    private static final boolean VERBOSE = false;

    private static final int DISABLE_TIMEOUT_MS = 8000;   // ms timeout for BT disable
    private static final int ENABLE_TIMEOUT_MS = 20_000;  // ms timeout for BT enable
    private static final int POLL_TIME_MS = 400;          // ms to poll BT state
    private static final int CHECK_WAIT_TIME_MS = 1_000;  // ms to wait before enable/disable
    private BluetoothAdapter mBluetoothAdapter;
    private PackageManager mPackageManager;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            Log.w(TAG, "No Bluetooth adapter");
        } else {
            int state = mBluetoothAdapter.getConnectionState();
            Log.d(TAG, "BluetoothAdapter: " + mBluetoothAdapter
                    + " enabled: " + mBluetoothAdapter.isEnabled()
                    + " state: "  + state + " (" + btStateToString(state) + ")");
        }
        mPackageManager = mContext.getPackageManager();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();

        clearBluetoothRestriction();
        enable();
    }

    public void testEnableBluetoothFailsWhenDisallowed() throws Exception {
        if (mBluetoothAdapter == null) {
            return;
        }

        // Make sure Bluetooth is initially disabled.
        disable();

        // Add the user restriction disallowing Bluetooth.
        addBluetoothRestriction();

        // Check that enabling Bluetooth fails.
        assertBluetoothAdapterDisabled();
    }

    public void testBluetoothGetsDisabledAfterRestrictionSet() throws Exception {
        if (mBluetoothAdapter == null) {
            return;
        }

        // Make sure Bluetooth is enabled first.
        enable();

        // Add the user restriction to disallow Bluetooth.
        addBluetoothRestriction();

        // Check that Bluetooth gets disabled as a result.
        assertDisabledAfterTimeout();
    }

    public void testEnableBluetoothSucceedsAfterRestrictionRemoved() throws Exception {
        if (mBluetoothAdapter == null) {
            return;
        }

        // Add the user restriction.
        addBluetoothRestriction();

        // Make sure Bluetooth is disabled.
        assertDisabledAfterTimeout();

        // Remove the user restriction.
        clearBluetoothRestriction();

        // Check that it is possible to enable Bluetooth again once the restriction has been
        // removed.
        enable();
    }

    /** Helper to turn BT off.
     * This method will either fail on an assert, or return with BT turned off.
     * Behavior of getState() and isEnabled() are validated along the way.
     */
    private void disable() {
        // Can't disable a bluetooth adapter that does not exist.
        if (mBluetoothAdapter == null) {
            Log.v(TAG, "disable(): ignoring as there is no BT adapter");
            return;
        }

        sleep(CHECK_WAIT_TIME_MS);
        int state = mBluetoothAdapter.getState();
        Log.v(TAG, "disable(): Current state: " + btStateToString(state));
        if (state == BluetoothAdapter.STATE_OFF) {
            assertBluetoothAdapterDisabled();
            return;
        }

        assertBluetoothAdapterState(BluetoothAdapter.STATE_ON);
        assertBluetoothAdapterEnabled();
        Log.i(TAG, "Disabling BT");
        boolean result = mBluetoothAdapter.disable();
        Log.v(TAG, "Result: " + result);
        assertDisabledAfterTimeout();
    }

    /**
     * Helper method which waits for Bluetooth to be disabled. Fails if it doesn't happen in a
     * given time.
     */
    private void assertDisabledAfterTimeout() {
        boolean turningOff = false;
        long timeout = SystemClock.elapsedRealtime() + DISABLE_TIMEOUT_MS;
        Log.d(TAG, "Waiting up to " + timeout + " ms for STATE_OFF and disabled");
        int state = Integer.MIN_VALUE;
        while (SystemClock.elapsedRealtime() < timeout) {
            state = mBluetoothAdapter.getState();
            Log.v(TAG, "State: " + btStateToString(state) + " turningOff: " + turningOff);
            switch (state) {
                case BluetoothAdapter.STATE_OFF:
                    Log.d(TAG, "STATE_OFF received, check that adapter is disabled");
                    assertBluetoothAdapterDisabled();
                    return;
                default:
                    if (state != BluetoothAdapter.STATE_ON || turningOff) {
                        assertBluetoothAdapterState(BluetoothAdapter.STATE_TURNING_OFF);
                        turningOff = true;
                    }
                    break;
            }
            sleep(POLL_TIME_MS);
        }
        fail("disable() timeout - BT adapter state is " + btStateToString(state)
                + " instead of STATE_OFF");
    }

    /** Helper to turn BT on.
     * This method will either fail on an assert, or return with BT turned on.
     * Behavior of getState() and isEnabled() are validated along the way.
     */
    private void enable() {
        // Can't enable a bluetooth adapter that does not exist.
        if (mBluetoothAdapter == null) {
            Log.v(TAG, "enable(): ignoring as there is no BT adapter");
            return;
        }

        sleep(CHECK_WAIT_TIME_MS);
        int state = mBluetoothAdapter.getState();
        Log.v(TAG, "enable(): Current state: " + btStateToString(state));

        if (state == BluetoothAdapter.STATE_ON) {
            assertBluetoothAdapterEnabled();
            return;
        }

        assertBluetoothAdapterState(BluetoothAdapter.STATE_OFF);
        assertBluetoothAdapterDisabled();
        Log.i(TAG, "Enabling BT");
        boolean result = mBluetoothAdapter.enable();
        Log.v(TAG, "Result: " + result);
        assertEnabledAfterTimeout();
    }

    /**
     * Helper method which waits for Bluetooth to be enabled. Fails if it doesn't happen in a given
     * time.
     */
    private void assertEnabledAfterTimeout() {
        boolean turningOn = false;
        long timeout = SystemClock.elapsedRealtime() + ENABLE_TIMEOUT_MS;
        Log.d(TAG, "Waiting up to " + timeout + " ms for STATE_ON and enabled");
        int state = Integer.MIN_VALUE;
        while (SystemClock.elapsedRealtime() < timeout) {
            state = mBluetoothAdapter.getState();
            Log.v(TAG, "State: " + btStateToString(state) + " turningOn: " + turningOn);
            switch (state) {
                case BluetoothAdapter.STATE_ON:
                    Log.d(TAG, "STATE_ON received, check that adapter is enabled");
                    assertBluetoothAdapterEnabled();
                    return;
                default:
                    if (state != BluetoothAdapter.STATE_OFF || turningOn) {
                        assertBluetoothAdapterState(BluetoothAdapter.STATE_TURNING_ON);
                        turningOn = true;
                    }
                    break;
            }
            sleep(POLL_TIME_MS);
        }
        fail("enable() timeout - BT adapter state is " + btStateToString(state)
                + " instead of STATE_ON");
    }

    private void assertBluetoothAdapterEnabled() {
        assertWithMessage("mBluetoothAdapter.isEnabled()").that(mBluetoothAdapter.isEnabled())
                .isTrue();
    }

    private void assertBluetoothAdapterDisabled() {
        assertWithMessage("mBluetoothAdapter.isEnabled()").that(mBluetoothAdapter.isEnabled())
                .isFalse();
    }

    private void assertBluetoothAdapterState(int expectedState) {
        int actualState = mBluetoothAdapter.getState();
        assertWithMessage("mBluetoothAdapter.getState() (where %s is %s and %s is %s)",
                expectedState, btStateToString(expectedState),
                actualState, btStateToString(actualState))
                        .that(actualState).isEqualTo(expectedState);
    }

    private void addBluetoothRestriction() {
        addUserRestriction(UserManager.DISALLOW_BLUETOOTH);
    }

    private void clearBluetoothRestriction() {
        clearUserRestriction(UserManager.DISALLOW_BLUETOOTH);
    }

    private void addUserRestriction(String restriction) {
        Log.d(TAG, "Adding " + restriction + " using " + mDevicePolicyManager);
        mDevicePolicyManager.addUserRestriction(getWho(), restriction);
    }

    private void clearUserRestriction(String restriction) {
        Log.d(TAG, "Clearing " + restriction + " using " + mDevicePolicyManager);
        mDevicePolicyManager.clearUserRestriction(getWho(), restriction);
    }

    private static String btStateToString(int state) {
        return DebugUtils.constantToString(BluetoothAdapter.class, "STATE_", state);
    }

    private static void sleep(long t) {
        if (VERBOSE) {
            Log.v(TAG, "Sleeping for " + t + "ms");
        }
        SystemClock.sleep(t);
    }
}
