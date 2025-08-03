/*
 * Copyright (C) 2019 The Android Open Source Project
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
import static android.Manifest.permission.BLUETOOTH_SCAN;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.app.UiAutomation;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHearingAid;
import android.bluetooth.BluetoothHearingAid.AdvertisementServiceData;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Parcel;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.MediumTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.CddTest;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Unit test cases for {@link BluetoothHearingAid}.
 * <p>
 * To run the test, use adb shell am instrument -e class 'android.bluetooth.HearingAidProfileTest'
 * -w 'com.android.bluetooth.tests/android.bluetooth.BluetoothTestRunner'
 */
@RunWith(AndroidJUnit4.class)
public class HearingAidProfileTest {
    private static final String TAG = "HearingAidProfileTest";

    private static final int WAIT_FOR_INTENT_TIMEOUT_MS = 10000; // ms to wait for intent callback
    private static final int PROXY_CONNECTION_TIMEOUT_MS = 500;  // ms timeout for Proxy Connect
    // ADAPTER_ENABLE_TIMEOUT_MS = AdapterState.BLE_START_TIMEOUT_DELAY +
    //                              AdapterState.BREDR_START_TIMEOUT_DELAY
    private static final int ADAPTER_ENABLE_TIMEOUT_MS = 8000;
    // ADAPTER_DISABLE_TIMEOUT_MS = AdapterState.BLE_STOP_TIMEOUT_DELAY +
    //                                  AdapterState.BREDR_STOP_TIMEOUT_DELAY
    private static final int ADAPTER_DISABLE_TIMEOUT_MS = 5000;
    private static final String FAKE_REMOTE_ADDRESS = "00:11:22:AA:BB:CC";

    private Context mContext;
    private BluetoothHearingAid mService;
    private BluetoothAdapter mBluetoothAdapter;
    private BroadcastReceiver mIntentReceiver;
    private UiAutomation mUiAutomation;;

    private Condition mConditionProfileConnection;
    private ReentrantLock mProfileConnectionlock;
    private boolean mIsProfileReady;
    private AdvertisementServiceData mAdvertisementData;

    private static List<Integer> mValidConnectionStates = new ArrayList<Integer>(
        Arrays.asList(BluetoothProfile.STATE_CONNECTING, BluetoothProfile.STATE_CONNECTED,
                      BluetoothProfile.STATE_DISCONNECTED, BluetoothProfile.STATE_DISCONNECTING));

    private List<BluetoothDevice> mIntentCallbackDeviceList;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();

        Assume.assumeTrue(TestUtils.isBleSupported(mContext));
        Assume.assumeTrue(TestUtils.isProfileEnabled(BluetoothProfile.HEARING_AID));

        mUiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);

        BluetoothManager manager = (BluetoothManager) mContext.getSystemService(
                Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = manager.getAdapter();

        assertTrue(BTAdapterUtils.enableAdapter(mBluetoothAdapter, mContext));
        mProfileConnectionlock = new ReentrantLock();
        mConditionProfileConnection = mProfileConnectionlock.newCondition();
        mIsProfileReady = false;
        mService = null;
        mBluetoothAdapter.getProfileProxy(mContext, new HearingAidsServiceListener(),
                BluetoothProfile.HEARING_AID);

        Parcel parcel = Parcel.obtain();
        parcel.writeInt(0b110); // CSIP supported, MODE_BINAURAL, SIDE_LEFT
        parcel.writeInt(1);
        parcel.setDataPosition(0);
        mAdvertisementData = AdvertisementServiceData.CREATOR.createFromParcel(parcel);
        assertNotNull(mAdvertisementData);
    }

    @After
    public void tearDown() {
        if (mUiAutomation != null) {
            mUiAutomation.dropShellPermissionIdentity();
        }
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void closeProfileProxy() {
        assertTrue(waitForProfileConnect());
        assertNotNull(mService);
        assertTrue(mIsProfileReady);

        mBluetoothAdapter.closeProfileProxy(BluetoothProfile.HEARING_AID, mService);
        assertTrue(waitForProfileDisconnect());
        assertFalse(mIsProfileReady);
    }

    /**
     * Basic test case to make sure that Hearing Aid Profile Proxy can connect.
     */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @MediumTest
    @Test
    public void getProxyServiceConnect() {
        waitForProfileConnect();
        assertTrue(mIsProfileReady);
        assertNotNull(mService);
    }

    /**
     * Basic test case to make sure that a fictional device is disconnected.
     */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @MediumTest
    @Test
    public void getConnectionState() {
        waitForProfileConnect();
        assertTrue(mIsProfileReady);
        assertNotNull(mService);

        // Create a fake device
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(FAKE_REMOTE_ADDRESS);
        assertNotNull(device);

        int connectionState = mService.getConnectionState(device);
        // Fake device should be disconnected
        assertEquals(connectionState, BluetoothProfile.STATE_DISCONNECTED);
    }

    /**
     * Basic test case to make sure that a fictional device throw a SecurityException when setting
     * volume.
     */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @MediumTest
    @Test
    public void setVolume() {
        waitForProfileConnect();
        assertTrue(mIsProfileReady);
        assertNotNull(mService);

        // This should throw a SecurityException because no BLUETOOTH_PRIVILEGED permission
        assertThrows(SecurityException.class, () -> mService.setVolume(42));
    }

    /**
     * Basic test case to make sure that a fictional device is unknown side.
     */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @MediumTest
    @Test
    public void getDeviceSide() {
        waitForProfileConnect();
        assertTrue(mIsProfileReady);
        assertNotNull(mService);

        // Create a fake device
        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(FAKE_REMOTE_ADDRESS);
        assertNotNull(device);

        final int side = mService.getDeviceSide(device);
        // Fake device should be no value, unknown side
        assertEquals(BluetoothHearingAid.SIDE_UNKNOWN, side);
    }

    /**
     * Basic test case to make sure that a fictional device is unknown mode.
     */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @MediumTest
    @Test
    public void getDeviceMode() {
        waitForProfileConnect();
        assertTrue(mIsProfileReady);
        assertNotNull(mService);

        // Create a fake device
        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(FAKE_REMOTE_ADDRESS);
        assertNotNull(device);

        final int mode = mService.getDeviceMode(device);
        // Fake device should be no value, unknown mode
        assertEquals(BluetoothHearingAid.MODE_UNKNOWN, mode);
    }

    /**
     * Basic test case to make sure that a fictional device's ASHA Advertisement Service Data
     * is null.
     */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @MediumTest
    @Test
    public void getAdvertisementServiceData() {
        waitForProfileConnect();
        assertTrue(mIsProfileReady);
        assertNotNull(mService);

        // Create a fake device
        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(FAKE_REMOTE_ADDRESS);
        assertNotNull(device);

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_SCAN, BLUETOOTH_PRIVILEGED);

        // Fake device should have no service data
        assertNull(mService.getAdvertisementServiceData(device));
    }

    /**
     * Basic test case to make sure that a fictional device's ASHA Advertisement Service Data's mode
     * is unknown.
     */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @MediumTest
    @Test
    public void getAdvertisementDeviceMode() {
        waitForProfileConnect();
        assertTrue(mIsProfileReady);
        assertNotNull(mService);

        // Create a fake advertisement data
        AdvertisementServiceData data = mAdvertisementData;

        final int mode = data.getDeviceMode();
        // Fake device should be MODE_BINAURAL
        assertEquals(BluetoothHearingAid.MODE_BINAURAL, mode);
    }

    /**
     * Basic test case to make sure that a fictional device's ASHA Advertisement Service Data's side
     * is unknown.
     */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @MediumTest
    @Test
    public void getAdvertisementDeviceSide() {
        waitForProfileConnect();
        assertTrue(mIsProfileReady);
        assertNotNull(mService);

        // Create a fake advertisement data
        AdvertisementServiceData data  = mAdvertisementData;

        final int side = data.getDeviceSide();
        // Fake device should be SIDE_LEFT
        assertEquals(BluetoothHearingAid.SIDE_LEFT, side);
    }

    /**
     * Basic test case to make sure that a fictional device's truncated HiSyncId is the
     * expected value.
     */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @MediumTest
    @Test
    public void getTruncatedHiSyncId() {
        waitForProfileConnect();
        assertTrue(mIsProfileReady);
        assertNotNull(mService);

        // Create a fake advertisement data
        AdvertisementServiceData data = mAdvertisementData;
        assertNotNull(data);

        final int id = data.getTruncatedHiSyncId();
        // Fake device should be supported
        assertEquals(1, id);
    }

    /**
     * Basic test case to make sure that a fictional device's ASHA Advertisement Service Data's CSIP
     * is not supported.
     */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @MediumTest
    @Test
    public void isCsipSupported() {
        waitForProfileConnect();
        assertTrue(mIsProfileReady);
        assertNotNull(mService);

        // Create a fake advertisement data
        AdvertisementServiceData data  = mAdvertisementData;
        assertNotNull(data);

        final boolean supported = data.isCsipSupported();
        // Fake device should be supported
        assertEquals(true, supported);
    }

    /**
     * Basic test case to make sure that a fictional device's ASHA Advertisement Service Data's CSIP
     * is not supported.
     */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @MediumTest
    @Test
    public void isLikelyPairOfBluetoothHearingAid() {
        waitForProfileConnect();
        assertTrue(mIsProfileReady);
        assertNotNull(mService);

        // Create a fake advertisement data
        final AdvertisementServiceData data  = mAdvertisementData;
        assertNotNull(data);

        // Create another fake advertisement data
        Parcel parcel = Parcel.obtain();
        parcel.writeInt(0b111); // CSIP supported, MODE_BINAURAL, SIDE_RIGHT
        parcel.writeInt(1);
        parcel.setDataPosition(0);
        AdvertisementServiceData dataOtherSide =
                AdvertisementServiceData.CREATOR.createFromParcel(parcel);
        assertNotNull(dataOtherSide);

        final boolean isLikelyPair = data.isInPairWith(dataOtherSide);
        // two devices should be a pair
        assertEquals(true, isLikelyPair);
    }

    /**
     * Basic test case to get the list of connected Hearing Aid devices.
     */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @MediumTest
    @Test
    public void getConnectedDevices() {
        waitForProfileConnect();
        assertTrue(mIsProfileReady);
        assertNotNull(mService);

        List<BluetoothDevice> deviceList;

        deviceList = mService.getConnectedDevices();
        Log.d(TAG, "getConnectedDevices(): size=" + deviceList.size());
        for (BluetoothDevice device : deviceList) {
            int connectionState = mService.getConnectionState(device);
            checkValidConnectionState(connectionState);
        }
    }

    /**
     * Basic test case to get the list of matching Hearing Aid devices for each of the 4 connection
     * states.
     */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @MediumTest
    @Test
    public void getDevicesMatchingConnectionStates() {
        waitForProfileConnect();
        assertTrue(mIsProfileReady);
        assertNotNull(mService);

        for (int connectionState : mValidConnectionStates) {
            List<BluetoothDevice> deviceList;

            deviceList = mService.getDevicesMatchingConnectionStates(new int[]{connectionState});
            assertNotNull(deviceList);
            Log.d(TAG, "getDevicesMatchingConnectionStates(" + connectionState + "): size="
                  + deviceList.size());
            checkDeviceListAndStates(deviceList, connectionState);
        }
    }

    /**
     * Test case to make sure that if the connection changed intent is called, the parameters and
     * device are correct.
     */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @MediumTest
    @Test
    public void getConnectionStateChangedIntent() {
        waitForProfileConnect();
        assertTrue(mIsProfileReady);
        assertNotNull(mService);

        // Find out how many Hearing Aid bonded devices
        List<BluetoothDevice> bondedDeviceList = new ArrayList();
        int numDevices = 0;
        for (int connectionState : mValidConnectionStates) {
            List<BluetoothDevice> deviceList;

            deviceList = mService.getDevicesMatchingConnectionStates(new int[]{connectionState});
            bondedDeviceList.addAll(deviceList);
            numDevices += deviceList.size();
        }

        if (numDevices <= 0) return;
        Log.d(TAG, "Number Hearing Aids devices bonded=" + numDevices);

        mIntentCallbackDeviceList = new ArrayList();

        // Set up the Connection State Changed receiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothHearingAid.ACTION_CONNECTION_STATE_CHANGED);
        mIntentReceiver = new HearingAidIntentReceiver();
        mContext.registerReceiver(mIntentReceiver, filter);

        Log.d(TAG, "getConnectionStateChangedIntent: disable adapter and wait");
        assertTrue(BTAdapterUtils.disableAdapter(mBluetoothAdapter, mContext));

        Log.d(TAG, "getConnectionStateChangedIntent: enable adapter and wait");
        assertTrue(BTAdapterUtils.enableAdapter(mBluetoothAdapter, mContext));

        int sanityCount = WAIT_FOR_INTENT_TIMEOUT_MS;
        while ((numDevices != mIntentCallbackDeviceList.size()) && (sanityCount > 0)) {
            final int SLEEP_QUANTUM_MS = 100;
            sleep(SLEEP_QUANTUM_MS);
            sanityCount -= SLEEP_QUANTUM_MS;
        }

        // Tear down
        mContext.unregisterReceiver(mIntentReceiver);

        Log.d(TAG, "getConnectionStateChangedIntent: number of bonded device="
              + numDevices + ", mIntentCallbackDeviceList.size()="
              + mIntentCallbackDeviceList.size());
        for (BluetoothDevice device : mIntentCallbackDeviceList) {
            assertTrue(bondedDeviceList.contains(device));
        }
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
        } catch(InterruptedException e) {
            Log.e(TAG, "waitForProfileConnect: interrrupted");
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
            Log.e(TAG, "waitForProfileDisconnect: interrrupted");
        } finally {
            mProfileConnectionlock.unlock();
        }
        return !mIsProfileReady;
    }

    private final class HearingAidsServiceListener
            implements BluetoothProfile.ServiceListener {

        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            mProfileConnectionlock.lock();
            mService = (BluetoothHearingAid) proxy;
            mIsProfileReady = true;
            try {
                mConditionProfileConnection.signal();
            } finally {
                mProfileConnectionlock.unlock();
            }
        }

        public void onServiceDisconnected(int profile) {
            mProfileConnectionlock.lock();
            mIsProfileReady = false;
            mService = null;
            try {
                mConditionProfileConnection.signal();
            } finally {
                mProfileConnectionlock.unlock();
            }
        }
    }

    private class HearingAidIntentReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (BluetoothHearingAid.ACTION_CONNECTION_STATE_CHANGED.equals(intent.getAction())) {
                int state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1);
                int previousState = intent.getIntExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, -1);
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                Log.d(TAG,"HearingAidIntentReceiver.onReceive: device=" + device
                      + ", state=" + state + ", previousState=" + previousState);

                checkValidConnectionState(state);
                checkValidConnectionState(previousState);

                mIntentCallbackDeviceList.add(device);
            }
        }
    }

    private void checkDeviceListAndStates(List<BluetoothDevice> deviceList, int connectionState) {
        Log.d(TAG, "checkDeviceListAndStates(): size=" + deviceList.size()
              + ", connectionState=" + connectionState);
        for (BluetoothDevice device : deviceList) {
            int deviceConnectionState = mService.getConnectionState(device);
            assertEquals("Mismatched connection state for " + device,
                         connectionState, deviceConnectionState);
        }
    }

    private void checkValidConnectionState(int connectionState) {
        assertTrue(mValidConnectionStates.contains(connectionState));
    }

    private static void sleep(long t) {
        try {
            Thread.sleep(t);
        } catch (InterruptedException e) {}
    }
}
