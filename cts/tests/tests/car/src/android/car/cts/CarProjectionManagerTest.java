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

package android.car.cts;

import static android.bluetooth.BluetoothProfile.A2DP_SINK;
import static android.car.cts.utils.ShellPermissionUtils.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.car.Car;
import android.car.CarProjectionManager;
import android.car.feature.Flags;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresDevice;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.util.Log;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.FeatureUtil;
import com.android.compatibility.common.util.ShellIdentityUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Contains the tests to prove compliance with android automotive specific projection requirements.
 */
@SmallTest
@RequiresDevice
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "Instant Apps cannot get Bluetooth related permissions")
public final class CarProjectionManagerTest extends AbstractCarTestCase {

    private static final String TAG = "CarProjectionMgrTest";
    private static final boolean DBG = Log.isLoggable(TAG, Log.DEBUG);

    // Timeout for waiting for an adapter state change
    private static final int BT_ADAPTER_TIMEOUT_MS = 8000; // ms

    // Configurable timeout for waiting for profile proxies to connect
    private static final int PROXY_CONNECTION_TIMEOUT_MS = 3000; // ms

    private static final int STATE_INVALID = -1;

    private static final String BT_DEVICE_ADDRESS = "00:11:22:33:AA:BB";

    private CarProjectionManager mCarProjectionManager;

    // Bluetooth Core objects
    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;

    // Objects to block until the adapter has reached a desired state
    private ReentrantLock mBluetoothAdapterLock;
    private Condition mConditionAdapterStateReached;
    private int mDesiredState;

    // Objects to block until all profile proxy connections have finished, or the timeout occurs
    private Condition mConditionA2dpProfileConnected;
    private ReentrantLock mProfileConnectedLock;
    private BluetoothDevice mBluetoothDevice;

    /**
     * Handles BluetoothAdapter state changes and signals when we've reached a desired state
     */
    private class BluetoothAdapterReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {

            // Decode the intent
            String action = intent.getAction();

            // Watch for BluetoothAdapter intents only
            if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
                int newState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, STATE_INVALID);
                if (DBG) {
                    Log.d(TAG, "Bluetooth adapter state changed: " + newState);
                }

                // Signal if the state is set to the one we're waiting on. If it's not and we got a
                // STATE_OFF event then handle the unexpected off event. Note that we could
                // proactively turn the adapter back on to continue testing. For now we'll just
                // log it
                mBluetoothAdapterLock.lock();
                try {
                    if (mDesiredState == newState) {
                        mConditionAdapterStateReached.signal();
                    } else if (newState == BluetoothAdapter.STATE_OFF) {
                        Log.w(TAG, "Bluetooth turned off unexpectedly while test was running.");
                    }
                } finally {
                    mBluetoothAdapterLock.unlock();
                }
            }
        }
    }

    private BluetoothAdapterReceiver mBluetoothAdapterReceiver;

    private void waitForAdapterOn() {
        if (DBG) {
            Log.d(TAG, "Waiting for adapter to be on...");
        }
        waitForAdapterState(BluetoothAdapter.STATE_ON);
    }

    private void waitForAdapterOff() {
        if (DBG) {
            Log.d(TAG, "Waiting for adapter to be off...");
        }
        waitForAdapterState(BluetoothAdapter.STATE_OFF);
    }

    // Wait for the bluetooth adapter to be in a given state
    private void waitForAdapterState(int desiredState) {
        if (DBG) {
            Log.d(TAG, "Waiting for adapter state " + desiredState);
        }
        mBluetoothAdapterLock.lock();
        try {
            // Update the desired state so that we'll signal when we get there
            mDesiredState = desiredState;
            if (desiredState == BluetoothAdapter.STATE_ON) {
                ShellIdentityUtils.invokeWithShellPermissions(() -> mBluetoothAdapter.enable());
            } else {
                ShellIdentityUtils.invokeWithShellPermissions(() -> mBluetoothAdapter.disable());
            }

            // Wait until we're reached that desired state
            while (desiredState != mBluetoothAdapter.getState()) {
                if (!mConditionAdapterStateReached.await(
                        BT_ADAPTER_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                    Log.e(TAG, "Timeout while waiting for Bluetooth adapter state " + desiredState);
                    break;
                }
            }
        } catch (InterruptedException e) {
            Log.w(TAG, "waitForAdapterState(" + desiredState + "): interrupted", e);
        } finally {
            mBluetoothAdapterLock.unlock();
        }
    }

    // Capture profile proxy connection events
    private final class ProfileServiceListener implements BluetoothProfile.ServiceListener {

        boolean mProfileConnected = false;

        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            if (DBG) {
                Log.d(TAG, "Profile '" + profile + "' has connected");
            }
            mProfileConnected = true;
            mProfileConnectedLock.lock();
            try {
                mConditionA2dpProfileConnected.signal();
            } finally {
                mProfileConnectedLock.unlock();
            }
        }

        @Override
        public void onServiceDisconnected(int profile) {
            mProfileConnected = false;
            if (DBG) {
                Log.d(TAG, "Profile '" + profile + "' has disconnected");
            }
        }
    }

    // Wait until A2DP is connected, or time out
    private void waitForProfileConnected() {
        ProfileServiceListener profileServiceListener = new ProfileServiceListener();
        mBluetoothAdapter.getProfileProxy(mContext, profileServiceListener, A2DP_SINK);
        mProfileConnectedLock.lock();
        try {
            while (!profileServiceListener.mProfileConnected) {
                // Wait for the profile condition
                if (!mConditionA2dpProfileConnected.await(PROXY_CONNECTION_TIMEOUT_MS,
                        TimeUnit.MILLISECONDS)) {
                    Log.e(TAG, "Timeout while waiting for A2DP profile connection");
                    break;
                }
            }
        } catch (InterruptedException e) {
            Log.w(TAG, "waitForProfileConnected: interrupted", e);
        } finally {
            mProfileConnectedLock.unlock();
        }
        if (DBG) {
            Log.d(TAG, "Proxy connection attempts complete. Connected A2DP_SINK");
        }
    }

    @Before
    public void setUp() throws Exception {
        if (DBG) {
            Log.d(TAG, "Setting up Automotive Bluetooth test. Device is "
                    + (FeatureUtil.isAutomotive() ? "" : "not ") + "automotive");
        }

        // Get bluetooth core objects so we can get proxies/check for profile existence
        mBluetoothManager = mContext.getSystemService(BluetoothManager.class);
        mBluetoothAdapter = mBluetoothManager.getAdapter();
        mBluetoothDevice = mBluetoothAdapter.getRemoteDevice(BT_DEVICE_ADDRESS);

        // Initialize all the profile connection variables
        mProfileConnectedLock = new ReentrantLock();
        mConditionA2dpProfileConnected = mProfileConnectedLock.newCondition();

        // Register the adapter receiver and initialize adapter state wait objects
        mDesiredState = -1; // Set and checked by waitForAdapterState()
        mBluetoothAdapterLock = new ReentrantLock();
        mConditionAdapterStateReached = mBluetoothAdapterLock.newCondition();
        mBluetoothAdapterReceiver = new BluetoothAdapterReceiver();
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
        mContext.registerReceiver(mBluetoothAdapterReceiver, filter);

        mCarProjectionManager = (CarProjectionManager) getCar().getCarManager(
                Car.PROJECTION_SERVICE);

        // Make sure Bluetooth is enabled before the test
        waitForAdapterOn();
        assertTrue(mBluetoothAdapter.isEnabled());
    }

    @After
    public void tearDown() {
        runWithShellPermissionIdentity(() ->
                mCarProjectionManager.releaseBluetoothProfileInhibit(mBluetoothDevice, A2DP_SINK));
        waitForAdapterOff();
        mContext.unregisterReceiver(mBluetoothAdapterReceiver);
    }

    @ApiTest(apis = {
            "android.car.CarProjectionManager#isBluetoothProfileInhibited"})
    @RequiresFlagsEnabled(Flags.FLAG_PROJECTION_QUERY_BT_PROFILE_INHIBIT)
    @Test
    public void testIsBluetoothProfileInhibited_default_isNotInhibited() {
        assertNotNull(mBluetoothAdapter);
        waitForProfileConnected();

        runWithShellPermissionIdentity(() ->
                assertThat(mCarProjectionManager.isBluetoothProfileInhibited(mBluetoothDevice,
                        A2DP_SINK)).isFalse());
    }

    @ApiTest(apis = {
            "android.car.CarProjectionManager#requestBluetoothProfileInhibit",
            "android.car.CarProjectionManager#isBluetoothProfileInhibited"})
    @RequiresFlagsEnabled(Flags.FLAG_PROJECTION_QUERY_BT_PROFILE_INHIBIT)
    @Test
    public void testIsBluetoothProfileInhibited_inhibitRequested_isInhibited() {
        assertNotNull(mBluetoothAdapter);
        waitForProfileConnected();

        runWithShellPermissionIdentity(() -> {
            assertThat(mCarProjectionManager.requestBluetoothProfileInhibit(mBluetoothDevice,
                    A2DP_SINK)).isTrue();

            assertThat(mCarProjectionManager.isBluetoothProfileInhibited(mBluetoothDevice,
                    A2DP_SINK)).isTrue();
        });
    }

    @ApiTest(apis = {
            "android.car.CarProjectionManager#requestBluetoothProfileInhibit",
            "android.car.CarProjectionManager#releaseBluetoothProfileInhibit",
            "android.car.CarProjectionManager#isBluetoothProfileInhibited"})
    @RequiresFlagsEnabled(Flags.FLAG_PROJECTION_QUERY_BT_PROFILE_INHIBIT)
    @Test
    public void testIsBluetoothProfileInhibited_inhibitReleased_isNotInhibited() {
        assertNotNull(mBluetoothAdapter);
        waitForProfileConnected();
        runWithShellPermissionIdentity(() -> {
            assertThat(mCarProjectionManager.requestBluetoothProfileInhibit(mBluetoothDevice,
                    A2DP_SINK)).isTrue();
            assertThat(mCarProjectionManager.releaseBluetoothProfileInhibit(mBluetoothDevice,
                    A2DP_SINK)).isTrue();

            assertThat(mCarProjectionManager.isBluetoothProfileInhibited(mBluetoothDevice,
                    A2DP_SINK)).isFalse();
        });
    }
}
