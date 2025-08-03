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

package android.bluetooth.cts;

import static android.Manifest.permission.BLUETOOTH_CONNECT;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.PendingIntent;
import android.app.UiAutomation;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothMapClient;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.sysprop.BluetoothProperties;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiLevelUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class BluetoothMapClientTest {
    private static final String TAG = BluetoothMapClientTest.class.getSimpleName();

    private static final int PROXY_CONNECTION_TIMEOUT_MS = 500;  // ms timeout for Proxy Connect

    private static final String ACTION_MESSAGE_SENT_SUCCESSFULLY =
            "android.bluetooth.cts.BluetoothMapClientTest.MESSAGE_SENT_SUCCESSFULLY";
    private static final String ACTION_MESSAGE_DELIVERED_SUCCESSFULLY =
            "android.bluetooth.cts.BluetoothMapClientTest"
            + ".MESSAGE_DELIVERED_SUCCESSFULLY";

    private Context mContext;
    private boolean mHasBluetooth;
    private BluetoothAdapter mAdapter;
    private UiAutomation mUiAutomation;

    private BluetoothMapClient mBluetoothMapClient;
    private boolean mIsMapClientSupported;
    private boolean mIsProfileReady;
    private Condition mConditionProfileConnection;
    private ReentrantLock mProfileConnectionlock;

    @Before
    public void setUp() throws Exception {
        if (!ApiLevelUtil.isAtLeast(Build.VERSION_CODES.TIRAMISU)) return;

        mContext = InstrumentationRegistry.getInstrumentation().getContext();

        mHasBluetooth = mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_BLUETOOTH);
        if (!mHasBluetooth) return;

        mIsMapClientSupported = BluetoothProperties.isProfileMapClientEnabled().orElse(false);
        if (!mIsMapClientSupported) return;

        mUiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);

        BluetoothManager manager = mContext.getSystemService(BluetoothManager.class);
        mAdapter = manager.getAdapter();
        assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));

        mProfileConnectionlock = new ReentrantLock();
        mConditionProfileConnection = mProfileConnectionlock.newCondition();
        mIsProfileReady = false;
        mBluetoothMapClient = null;

        mAdapter.getProfileProxy(mContext, new BluetoothMapClientServiceListener(),
                BluetoothProfile.MAP_CLIENT);
    }

    @After
    public void tearDown() throws Exception {
        if (!(mHasBluetooth && mIsMapClientSupported)) {
            return;
        }
        if (mAdapter != null && mBluetoothMapClient != null) {
            mAdapter.closeProfileProxy(BluetoothProfile.MAP_CLIENT,
                    mBluetoothMapClient);
            mBluetoothMapClient = null;
            mIsProfileReady = false;
        }
        mAdapter = null;
    }

    @Test
    public void closeProfileProxy() {
        assumeTrue(mHasBluetooth && mIsMapClientSupported);
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothMapClient);
        assertTrue(mIsProfileReady);

        mAdapter.closeProfileProxy(BluetoothProfile.MAP_CLIENT, mBluetoothMapClient);
        assertTrue(waitForProfileDisconnect());
        assertFalse(mIsProfileReady);
    }

    @Test
    public void getConnectedDevices() {
        assumeTrue(mHasBluetooth && mIsMapClientSupported);
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothMapClient);

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns empty list if bluetooth is not enabled
        List<BluetoothDevice> connectedDevices = mBluetoothMapClient.getConnectedDevices();
        assertTrue(connectedDevices.isEmpty());
    }

    @Test
    public void getConnectionPolicy() {
        assumeTrue(mHasBluetooth && mIsMapClientSupported);
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothMapClient);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        // Verify returns false when invalid input is given
        assertEquals(BluetoothProfile.CONNECTION_POLICY_FORBIDDEN,
                mBluetoothMapClient.getConnectionPolicy(null));

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns false if bluetooth is not enabled
        assertEquals(BluetoothProfile.CONNECTION_POLICY_FORBIDDEN,
                mBluetoothMapClient.getConnectionPolicy(testDevice));
    }

    @Test
    public void getConnectionState() {
        assumeTrue(mHasBluetooth && mIsMapClientSupported);
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothMapClient);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        // Verify returns false when invalid input is given
        assertEquals(BluetoothProfile.STATE_DISCONNECTED,
                mBluetoothMapClient.getConnectionState(null));

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns false if bluetooth is not enabled
        assertEquals(BluetoothProfile.STATE_DISCONNECTED,
                mBluetoothMapClient.getConnectionState(testDevice));
    }

    @Test
    public void getDevicesMatchingConnectionStates() {
        assumeTrue(mHasBluetooth && mIsMapClientSupported);
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothMapClient);

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns empty list if bluetooth is not enabled
        List<BluetoothDevice> connectedDevices =
                mBluetoothMapClient.getDevicesMatchingConnectionStates(new int[] {});
        assertTrue(connectedDevices.isEmpty());
    }

    @Test
    public void sendMessage() {
        assumeTrue(mHasBluetooth && mIsMapClientSupported);
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothMapClient);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");
        Collection<Uri> contacts = new HashSet<Uri>();
        String message = "";
        PendingIntent sentIntent = PendingIntent.getBroadcast(mContext, 0,
                new Intent(ACTION_MESSAGE_SENT_SUCCESSFULLY),
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent deliveredIntent = PendingIntent.getBroadcast(mContext, 0,
                new Intent(ACTION_MESSAGE_DELIVERED_SUCCESSFULLY),
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        // Verify returns false when invalid input device is given
        assertFalse(mBluetoothMapClient.sendMessage(
                /* BluetoothDevice */ null, contacts, message, sentIntent, deliveredIntent));

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns false if bluetooth is not enabled
        assertFalse(mBluetoothMapClient.sendMessage(
                testDevice, contacts, message, sentIntent, deliveredIntent));
    }

    @Test
    public void setConnectionPolicy() {
        assumeTrue(mHasBluetooth && mIsMapClientSupported);
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothMapClient);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        // Verify returns false when invalid input is given
        assertFalse(mBluetoothMapClient.setConnectionPolicy(
                testDevice, BluetoothProfile.CONNECTION_POLICY_UNKNOWN));
        assertFalse(mBluetoothMapClient.setConnectionPolicy(
                null, BluetoothProfile.CONNECTION_POLICY_ALLOWED));

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns false if bluetooth is not enabled
        assertFalse(mBluetoothMapClient.setConnectionPolicy(
                testDevice, BluetoothProfile.CONNECTION_POLICY_FORBIDDEN));
    }

    private boolean waitForProfileConnect() {
        mProfileConnectionlock.lock();
        try {
            // Wait for the Adapter to be disabled
            while (!mIsProfileReady) {
                if (!mConditionProfileConnection.await(
                        PROXY_CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                    // Timeout
                    Log.e(TAG, "Timeout while waiting for Profile Connect");
                    break;
                } // else spurious wakeups
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "waitForProfileConnect: interrupted");
        } finally {
            mProfileConnectionlock.unlock();
        }
        return mIsProfileReady;
    }

    private boolean waitForProfileDisconnect() {
        mConditionProfileConnection = mProfileConnectionlock.newCondition();
        mProfileConnectionlock.lock();
        try {
            while (mIsProfileReady) {
                if (!mConditionProfileConnection.await(
                        PROXY_CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                    // Timeout
                    Log.e(TAG, "Timeout while waiting for Profile Disconnect");
                    break;
                } // else spurious wakeups
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "waitForProfileDisconnect: interrupted");
        } finally {
            mProfileConnectionlock.unlock();
        }
        return !mIsProfileReady;
    }

    private final class BluetoothMapClientServiceListener implements
            BluetoothProfile.ServiceListener {

        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            mProfileConnectionlock.lock();
            mBluetoothMapClient = (BluetoothMapClient) proxy;
            mIsProfileReady = true;
            try {
                mConditionProfileConnection.signal();
            } finally {
                mProfileConnectionlock.unlock();
            }
        }

        @Override
        public void onServiceDisconnected(int profile) {
            mProfileConnectionlock.lock();
            mIsProfileReady = false;
            try {
                mConditionProfileConnection.signal();
            } finally {
                mProfileConnectionlock.unlock();
            }
        }
    }
}
