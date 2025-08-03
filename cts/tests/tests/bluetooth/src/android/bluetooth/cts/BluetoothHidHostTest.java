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
import static android.Manifest.permission.BLUETOOTH_PRIVILEGED;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.UiAutomation;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHidHost;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.sysprop.BluetoothProperties;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bluetooth.flags.Flags;
import com.android.compatibility.common.util.ApiLevelUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;


@RunWith(AndroidJUnit4.class)
@LargeTest
public class BluetoothHidHostTest {
    private static final String TAG = BluetoothHidHostTest.class.getSimpleName();

    private static final String[] DEFAULT_PERMISSIONS = {BLUETOOTH_CONNECT};
    private static final int PROXY_CONNECTION_TIMEOUT_MS = 500; // ms timeout for Proxy Connect
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();
    private Context mContext;
    private boolean mHasBluetooth;
    private boolean mIsHidHostSupported;
    private BluetoothAdapter mAdapter;
    private BluetoothHidHost mHidHost;
    private UiAutomation mUiAutomation;
    private boolean mIsProfileReady;
    private Condition mConditionProfileConnection;
    private ReentrantLock mProfileConnectionlock;

    @Before
    public void setUp() throws Exception {
        if (!ApiLevelUtil.isAtLeast(Build.VERSION_CODES.TIRAMISU)) return;

        mContext = InstrumentationRegistry.getInstrumentation().getContext();

        mHasBluetooth =
                mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH);
        if (!mHasBluetooth) return;

        mIsHidHostSupported = BluetoothProperties.isProfileHidHostEnabled().orElse(false);
        if (!mIsHidHostSupported) return;

        mUiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        mUiAutomation.adoptShellPermissionIdentity(DEFAULT_PERMISSIONS);

        BluetoothManager manager = mContext.getSystemService(BluetoothManager.class);
        mAdapter = manager.getAdapter();
        assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));

        mProfileConnectionlock = new ReentrantLock();
        mConditionProfileConnection = mProfileConnectionlock.newCondition();
        mIsProfileReady = false;
        mHidHost = null;

        mAdapter.getProfileProxy(
                mContext, new BluetoothHidHostListener(), BluetoothProfile.HID_HOST);
    }

    @After
    public void tearDown() throws Exception {
        if (!(mHasBluetooth && mIsHidHostSupported)) {
            return;
        }
        if (mAdapter != null && mHidHost != null) {
            mAdapter.closeProfileProxy(BluetoothProfile.HID_HOST, mHidHost);
            mHidHost = null;
            mIsProfileReady = false;
        }

        if (mUiAutomation != null) {
            mUiAutomation.dropShellPermissionIdentity();
        }
        mAdapter = null;
    }

    @Test
    public void closeProfileProxy() {
        assumeTrue(mHasBluetooth && mIsHidHostSupported);
        assertTrue(waitForProfileConnect());
        assertNotNull(mHidHost);
        assertTrue(mIsProfileReady);

        mAdapter.closeProfileProxy(BluetoothProfile.HID_HOST, mHidHost);
        assertTrue(waitForProfileDisconnect());
        assertFalse(mIsProfileReady);
    }

    @Test
    public void getConnectedDevices() {
        assumeTrue(mHasBluetooth && mIsHidHostSupported);
        assertTrue(waitForProfileConnect());
        assertNotNull(mHidHost);

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns empty list if bluetooth is not enabled
        List<BluetoothDevice> connectedDevices = mHidHost.getConnectedDevices();
        assertTrue(connectedDevices.isEmpty());
    }

    @Test
    public void getDevicesMatchingConnectionStates() {
        assumeTrue(mHasBluetooth && mIsHidHostSupported);
        assertTrue(waitForProfileConnect());
        assertNotNull(mHidHost);

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns empty list if bluetooth is not enabled
        List<BluetoothDevice> connectedDevices = mHidHost.getDevicesMatchingConnectionStates(null);
        assertTrue(connectedDevices.isEmpty());
    }

    @Test
    public void getConnectionState() {
        assumeTrue(mHasBluetooth && mIsHidHostSupported);
        assertTrue(waitForProfileConnect());
        assertNotNull(mHidHost);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        // Verify returns IllegalArgumentException when invalid input is given
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    mHidHost.getConnectionState(null);
                });
        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns STATE_DISCONNECTED if bluetooth is not enabled
        assertEquals(BluetoothProfile.STATE_DISCONNECTED, mHidHost.getConnectionState(testDevice));
    }

    @Test
    public void getConnectionPolicy() {
        assumeTrue(mHasBluetooth && mIsHidHostSupported);
        assertTrue(waitForProfileConnect());
        assertNotNull(mHidHost);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        // Verify returns IllegalArgumentException when invalid input is given
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    mHidHost.getConnectionPolicy(null);
                });
        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns false if bluetooth is not enabled
        assertEquals(
                BluetoothProfile.CONNECTION_POLICY_FORBIDDEN,
                mHidHost.getConnectionPolicy(testDevice));
    }

    @Test
    public void setConnectionPolicy() {
        assumeTrue(mHasBluetooth && mIsHidHostSupported);
        assertTrue(waitForProfileConnect());
        assertNotNull(mHidHost);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        // Verify returns false when invalid input is given
        assertFalse(
                mHidHost.setConnectionPolicy(
                        testDevice, BluetoothProfile.CONNECTION_POLICY_UNKNOWN));
        // Verify returns IllegalArgumentException when invalid input is given
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    mHidHost.setConnectionPolicy(null, BluetoothProfile.CONNECTION_POLICY_UNKNOWN);
                });

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns false if bluetooth is not enabled
        assertFalse(
                mHidHost.setConnectionPolicy(
                        testDevice, BluetoothProfile.CONNECTION_POLICY_FORBIDDEN));
    }

    @RequiresFlagsEnabled(Flags.FLAG_ALLOW_SWITCHING_HID_AND_HOGP)
    @Test
    public void getPreferredTransportTest() {
        assumeTrue(mHasBluetooth && mIsHidHostSupported);
        assertTrue(waitForProfileConnect());
        assertNotNull(mHidHost);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        // Verify returns IllegalArgumentException when invalid input is given
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    mHidHost.getPreferredTransport(null);
                });
        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns false if bluetooth is not enabled
        assertEquals(
                BluetoothDevice.TRANSPORT_AUTO,
                mHidHost.getPreferredTransport(testDevice));
    }

    @RequiresFlagsEnabled(Flags.FLAG_ALLOW_SWITCHING_HID_AND_HOGP)
    @Test
    public void setPreferredTransportTest() {
        assumeTrue(mHasBluetooth && mIsHidHostSupported);
        assertTrue(waitForProfileConnect());
        assertNotNull(mHidHost);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        // Verify that BLUETOOTH_PRIVILEGED permission is enforced
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);
        assertThrows("BLUETOOTH_PRIVILEGED permission not enforced",
                SecurityException.class,
                () -> {
                    mHidHost.setPreferredTransport(testDevice, BluetoothDevice.TRANSPORT_AUTO);
                });

        // Verify that BLUETOOTH_CONNECT permission is enforced
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_PRIVILEGED);
        assertThrows("BLUETOOTH_CONNECT permission not enforced", SecurityException.class,
                () -> {
                    mHidHost.setPreferredTransport(testDevice, BluetoothDevice.TRANSPORT_AUTO);
                });

        // Get required permissions
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);

        // Verify returns false when invalid input is given
        assertFalse(
                mHidHost.setPreferredTransport(
                        testDevice, BluetoothDevice.TRANSPORT_AUTO));
        // Verify returns IllegalArgumentException when invalid input is given
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    mHidHost.setPreferredTransport(null, BluetoothDevice.TRANSPORT_AUTO);
                });

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns false if bluetooth is not enabled
        assertFalse(
                mHidHost.setPreferredTransport(
                        testDevice, BluetoothDevice.TRANSPORT_AUTO));
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
                } // else spurious wake ups
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

    private final class BluetoothHidHostListener implements BluetoothProfile.ServiceListener {

        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            mProfileConnectionlock.lock();
            try {
                mHidHost = (BluetoothHidHost) proxy;
                mIsProfileReady = true;
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
