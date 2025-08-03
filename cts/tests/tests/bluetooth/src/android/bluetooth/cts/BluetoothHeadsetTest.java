/*
 * Copyright 2022 The Android Open Source Project
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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.app.UiAutomation;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothStatusCodes;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class BluetoothHeadsetTest {
    private static final String TAG = BluetoothHeadsetTest.class.getSimpleName();

    private static final int PROXY_CONNECTION_TIMEOUT_MS = 500;  // ms timeout for Proxy Connect

    private Context mContext;
    private boolean mHasBluetooth;
    private BluetoothAdapter mAdapter;
    private UiAutomation mUiAutomation;;

    private BluetoothHeadset mBluetoothHeadset;
    private boolean mIsHeadsetSupported;
    private boolean mIsProfileReady;
    private Condition mConditionProfileConnection;
    private ReentrantLock mProfileConnectionlock;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();

        mHasBluetooth = mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_BLUETOOTH);
        if (!mHasBluetooth) return;

        mIsHeadsetSupported = TestUtils.isProfileEnabled(BluetoothProfile.HEADSET);
        if (!mIsHeadsetSupported) return;

        mUiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);

        BluetoothManager manager = mContext.getSystemService(BluetoothManager.class);
        mAdapter = manager.getAdapter();
        assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));

        mProfileConnectionlock = new ReentrantLock();
        mConditionProfileConnection = mProfileConnectionlock.newCondition();
        mIsProfileReady = false;
        mBluetoothHeadset = null;

        mAdapter.getProfileProxy(mContext, new BluetoothHeadsetServiceListener(),
                BluetoothProfile.HEADSET);
    }

    @After
    public void tearDown() throws Exception {
        if (!(mHasBluetooth && mIsHeadsetSupported)) {
            return;
        }
        if (mAdapter != null && mBluetoothHeadset != null) {
            mAdapter.closeProfileProxy(BluetoothProfile.HEADSET, mBluetoothHeadset);
            mBluetoothHeadset = null;
            mIsProfileReady = false;
        }
        mAdapter = null;
        mUiAutomation.dropShellPermissionIdentity();
    }

    @Test
    public void closeProfileProxy() {
        assumeTrue(mHasBluetooth && mIsHeadsetSupported);
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHeadset);
        assertTrue(mIsProfileReady);

        mAdapter.closeProfileProxy(BluetoothProfile.HEADSET, mBluetoothHeadset);
        assertTrue(waitForProfileDisconnect());
        assertFalse(mIsProfileReady);
    }

    @Test
    public void getConnectedDevices() {
        assumeTrue(mHasBluetooth && mIsHeadsetSupported);
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHeadset);

        assertEquals(mBluetoothHeadset.getConnectedDevices(),
                new ArrayList<BluetoothDevice>());
    }

    @Test
    public void getDevicesMatchingConnectionStates() {
        assumeTrue(mHasBluetooth && mIsHeadsetSupported);
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHeadset);

        assertEquals(mBluetoothHeadset.getDevicesMatchingConnectionStates(
                new int[]{BluetoothProfile.STATE_CONNECTED}),
                new ArrayList<BluetoothDevice>());
    }

    @Test
    public void getConnectionState() {
        assumeTrue(mHasBluetooth && mIsHeadsetSupported);
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHeadset);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        assertEquals(mBluetoothHeadset.getConnectionState(testDevice),
                BluetoothProfile.STATE_DISCONNECTED);
    }

    @Test
    public void isAudioConnected() {
        assumeTrue(mHasBluetooth && mIsHeadsetSupported);
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHeadset);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        assertFalse(mBluetoothHeadset.isAudioConnected(testDevice));
        assertFalse(mBluetoothHeadset.isAudioConnected(null));

        // Verify the method returns false when Bluetooth is off and you supply a valid device
        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));
        assertFalse(mBluetoothHeadset.isAudioConnected(testDevice));
    }

    @Test
    public void isNoiseReductionSupported() {
        assumeTrue(mHasBluetooth && mIsHeadsetSupported);
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHeadset);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        assertFalse(mBluetoothHeadset.isNoiseReductionSupported(testDevice));
        assertFalse(mBluetoothHeadset.isNoiseReductionSupported(null));

        // Verify the method returns false when Bluetooth is off and you supply a valid device
        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));
        assertFalse(mBluetoothHeadset.isNoiseReductionSupported(testDevice));
    }

    @Test
    public void isVoiceRecognitionSupported() {
        assumeTrue(mHasBluetooth && mIsHeadsetSupported);
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHeadset);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        assertFalse(mBluetoothHeadset.isVoiceRecognitionSupported(testDevice));
        assertFalse(mBluetoothHeadset.isVoiceRecognitionSupported(null));

        // Verify the method returns false when Bluetooth is off and you supply a valid device
        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));
        assertFalse(mBluetoothHeadset.isVoiceRecognitionSupported(testDevice));
    }

    @Test
    public void sendVendorSpecificResultCode() {
        assumeTrue(mHasBluetooth && mIsHeadsetSupported);
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHeadset);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        try {
            mBluetoothHeadset.sendVendorSpecificResultCode(testDevice, null, null);
            fail("sendVendorSpecificResultCode did not throw an IllegalArgumentException when the "
                    + "command was null");
        } catch (IllegalArgumentException ignored) {
        }

        assertFalse(mBluetoothHeadset.sendVendorSpecificResultCode(testDevice, "", ""));
        assertFalse(mBluetoothHeadset.sendVendorSpecificResultCode(null, "", ""));

        // Verify the method returns false when Bluetooth is off and you supply a valid device
        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));
        assertFalse(mBluetoothHeadset.sendVendorSpecificResultCode(testDevice, "", ""));
    }

    @Test
    public void connect() {
        assumeTrue(mHasBluetooth && mIsHeadsetSupported);
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHeadset);

        // Verify returns false when invalid input is given
        assertFalse(mBluetoothHeadset.connect(null));

        // Verify it returns false for a device that has CONNECTION_POLICY_FORBIDDEN
        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");
        assertFalse(mBluetoothHeadset.connect(testDevice));

        // Verify returns false if bluetooth is not enabled
        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));
        assertFalse(mBluetoothHeadset.connect(testDevice));
    }

    @Test
    public void disconnect() {
        assumeTrue(mHasBluetooth && mIsHeadsetSupported);
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHeadset);

        // Verify returns false when invalid input is given
        assertFalse(mBluetoothHeadset.disconnect(null));

        // Verify it returns false for a device that has CONNECTION_POLICY_FORBIDDEN
        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");
        assertFalse(mBluetoothHeadset.disconnect(testDevice));

        // Verify returns false if bluetooth is not enabled
        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));
        assertFalse(mBluetoothHeadset.disconnect(testDevice));
    }

    @Test
    public void getConnectionPolicy() {
        assumeTrue(mHasBluetooth && mIsHeadsetSupported);
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHeadset);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        // Verify returns false when invalid input is given
        assertEquals(
                BluetoothProfile.CONNECTION_POLICY_FORBIDDEN,
                mBluetoothHeadset.getConnectionPolicy(null));

        // Verify returns CONNECTION_POLICY_FORBIDDEN if bluetooth is not enabled
        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));
        assertEquals(
                BluetoothProfile.CONNECTION_POLICY_FORBIDDEN,
                mBluetoothHeadset.getConnectionPolicy(testDevice));
    }

    @Test
    public void setConnectionPolicy() {
        assumeTrue(mHasBluetooth && mIsHeadsetSupported);
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHeadset);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        // Verify returns false when invalid input is given
        assertFalse(
                mBluetoothHeadset.setConnectionPolicy(
                        testDevice, BluetoothProfile.CONNECTION_POLICY_UNKNOWN));
        assertFalse(
                mBluetoothHeadset.setConnectionPolicy(
                        null, BluetoothProfile.CONNECTION_POLICY_ALLOWED));

        // Verify returns false if bluetooth is not enabled
        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));
        assertFalse(
                mBluetoothHeadset.setConnectionPolicy(
                        testDevice, BluetoothProfile.CONNECTION_POLICY_FORBIDDEN));
    }

    @Test
    public void getAudioState() {
        assumeTrue(mHasBluetooth && mIsHeadsetSupported);
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHeadset);

        try {
            mBluetoothHeadset.getAudioState(null);
            fail("Calling getAudioState on a null device should throw a NullPointerException");
        } catch (NullPointerException ignored) {
        }

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");
        assertEquals(
                BluetoothHeadset.STATE_AUDIO_DISCONNECTED,
                mBluetoothHeadset.getAudioState(testDevice));
    }

    @Test
    public void connectAudio() {
        assumeTrue(mHasBluetooth && mIsHeadsetSupported);
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHeadset);

        assertEquals(
                BluetoothStatusCodes.ERROR_NO_ACTIVE_DEVICES, mBluetoothHeadset.connectAudio());

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));
        assertEquals(
                BluetoothStatusCodes.ERROR_PROFILE_SERVICE_NOT_BOUND,
                mBluetoothHeadset.connectAudio());
    }

    @Test
    public void disconnectAudio() {
        assumeTrue(mHasBluetooth && mIsHeadsetSupported);
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHeadset);

        assertEquals(
                BluetoothStatusCodes.ERROR_NO_ACTIVE_DEVICES, mBluetoothHeadset.disconnectAudio());

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));
        assertEquals(
                BluetoothStatusCodes.ERROR_PROFILE_SERVICE_NOT_BOUND,
                mBluetoothHeadset.disconnectAudio());
    }

    @Test
    public void startScoUsingVirtualVoiceCall() {
        assumeTrue(mHasBluetooth && mIsHeadsetSupported);
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHeadset);

        assertFalse(mBluetoothHeadset.startScoUsingVirtualVoiceCall());
    }

    @Test
    public void stopScoUsingVirtualVoiceCall() {
        assumeTrue(mHasBluetooth && mIsHeadsetSupported);
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHeadset);

        assertFalse(mBluetoothHeadset.stopScoUsingVirtualVoiceCall());
    }

    @Test
    public void isInbandRingingEnabled() {
        assumeTrue(mHasBluetooth && mIsHeadsetSupported);
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHeadset);

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));
        assertFalse(mBluetoothHeadset.isInbandRingingEnabled());
    }

    @Test
    public void setGetAudioRouteAllowed() {
        assumeTrue(mHasBluetooth && mIsHeadsetSupported);
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHeadset);

        assertEquals(BluetoothStatusCodes.SUCCESS, mBluetoothHeadset.setAudioRouteAllowed(true));
        assertEquals(BluetoothStatusCodes.ALLOWED, mBluetoothHeadset.getAudioRouteAllowed());

        assertEquals(BluetoothStatusCodes.SUCCESS, mBluetoothHeadset.setAudioRouteAllowed(false));
        assertEquals(BluetoothStatusCodes.NOT_ALLOWED, mBluetoothHeadset.getAudioRouteAllowed());
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

    private final class BluetoothHeadsetServiceListener implements
            BluetoothProfile.ServiceListener {

        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            mProfileConnectionlock.lock();
            mBluetoothHeadset = (BluetoothHeadset) proxy;
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
