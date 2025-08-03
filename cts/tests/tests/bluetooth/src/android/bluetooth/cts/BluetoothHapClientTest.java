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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHapClient;
import android.bluetooth.BluetoothHapPresetInfo;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothStatusCodes;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.CddTest;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class BluetoothHapClientTest {
    private static final String TAG = BluetoothHapClientTest.class.getSimpleName();

    private static final int PROXY_CONNECTION_TIMEOUT_MS = 500;  // ms timeout for Proxy Connect

    private Context mContext;
    private BluetoothAdapter mAdapter;

    private BluetoothHapClient mBluetoothHapClient;
    private boolean mIsProfileReady;
    private Condition mConditionProfileConnection;
    private ReentrantLock mProfileConnectionlock;

    private boolean mOnPresetSelected = false;
    private boolean mOnPresetSelectionFailed = false;
    private boolean mOnPresetSelectionForGroupFailed = false;
    private boolean mOnPresetInfoChanged = false;
    private boolean mOnSetPresetNameFailed = false;
    private boolean mOnSetPresetNameForGroupFailed = false;

    private CountDownLatch mCallbackCountDownLatch;
    private List<BluetoothHapPresetInfo> mPresetInfoList = new ArrayList();

    private static final int TEST_REASON_CODE = BluetoothStatusCodes.REASON_LOCAL_STACK_REQUEST;
    private static final int TEST_PRESET_INDEX = 13;
    private static final int TEST_STATUS_CODE = BluetoothStatusCodes.ERROR_HAP_INVALID_PRESET_INDEX;
    private static final int TEST_HAP_GROUP_ID = 65;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();

        Assume.assumeTrue(ApiLevelUtil.isAtLeast(Build.VERSION_CODES.TIRAMISU));
        Assume.assumeTrue(TestUtils.isBleSupported(mContext));
        Assume.assumeTrue(TestUtils.isProfileEnabled(BluetoothProfile.HAP_CLIENT));

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
        mAdapter = TestUtils.getBluetoothAdapterOrDie();
        assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));

        mProfileConnectionlock = new ReentrantLock();
        mConditionProfileConnection = mProfileConnectionlock.newCondition();
        mIsProfileReady = false;
        mBluetoothHapClient = null;

        mAdapter.getProfileProxy(mContext, new BluetoothHapClientServiceListener(),
                BluetoothProfile.HAP_CLIENT);
    }

    @After
    public void tearDown() throws Exception {
        if (mBluetoothHapClient != null) {
            mBluetoothHapClient.close();
            mBluetoothHapClient = null;
            mIsProfileReady = false;
        }
        mAdapter = null;
        TestUtils.dropPermissionAsShellUid();
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void closeProfileProxy() {
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHapClient);
        assertTrue(mIsProfileReady);

        mAdapter.closeProfileProxy(BluetoothProfile.HAP_CLIENT, mBluetoothHapClient);
        assertTrue(waitForProfileDisconnect());
        assertFalse(mIsProfileReady);
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getConnectedDevices() {
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHapClient);

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns empty list if bluetooth is not enabled
        List<BluetoothDevice> connectedDevices = mBluetoothHapClient.getConnectedDevices();
        assertTrue(connectedDevices.isEmpty());
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getDevicesMatchingConnectionStates() {
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHapClient);

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns empty list if bluetooth is not enabled
        List<BluetoothDevice> connectedDevices =
                mBluetoothHapClient.getDevicesMatchingConnectionStates(null);
        assertTrue(connectedDevices.isEmpty());
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getConnectionState() {
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHapClient);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        // Verify returns STATE_DISCONNECTED when invalid input is given
        assertEquals(BluetoothProfile.STATE_DISCONNECTED,
                mBluetoothHapClient.getConnectionState(null));

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns STATE_DISCONNECTED if bluetooth is not enabled
        assertEquals(BluetoothProfile.STATE_DISCONNECTED,
                mBluetoothHapClient.getConnectionState(testDevice));
    }

    /**
     * Verify getHapGroup() return -1 if Bluetooth is disabled.
     */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getHapGroup() {
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHapClient);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        assertEquals(-1, mBluetoothHapClient.getHapGroup(testDevice));
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getActivePresetIndex() {
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHapClient);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns 0 if bluetooth is not enabled
        assertEquals(0, mBluetoothHapClient.getActivePresetIndex(testDevice));
    }

    /**
     * Verify getActivePresetInfo() return null if Bluetooth is disabled.
     */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getActivePresetInfo() {
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHapClient);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        assertNull(mBluetoothHapClient.getActivePresetInfo(testDevice));
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void selectPreset() {
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHapClient);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        mBluetoothHapClient.selectPreset(testDevice, 1);
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void selectPresetForGroup() {
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHapClient);

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        mBluetoothHapClient.selectPresetForGroup(1, 1);
    }

    /**
     * Verify switchToNextPreset() will not cause exception when Bluetooth is disabled.
     */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void switchToNextPreset() {
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHapClient);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        mBluetoothHapClient.switchToNextPreset(testDevice);
    }

    /**
     * Verify switchToNextPresetForGroup() will not cause exception when Bluetooth is disabled.
     */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void switchToNextPresetForGroup() {
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHapClient);

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        mBluetoothHapClient.switchToNextPresetForGroup(1);
    }

    /**
     * Verify switchToPreviousPreset() doesn't cause exception when Bluetooth is disabled.
     */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void switchToPreviousPreset() {
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHapClient);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        mBluetoothHapClient.switchToPreviousPreset(testDevice);
    }

    /**
     * Verify switchToPreviousPresetForGroup() doesn't cause exception when Bluetooth is disabled.
     */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void switchToPreviousPresetForGroup() {
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHapClient);

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        mBluetoothHapClient.switchToPreviousPresetForGroup(1);
    }

    /**
     * Verify switchToNextPresetForGroup() doesn't cause exception when Bluetooth is disabled.
     */
    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getPresetInfo() {
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHapClient);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns null if bluetooth is not enabled
        assertNull(mBluetoothHapClient.getPresetInfo(testDevice, 1));
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getAllPresetInfo() {
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHapClient);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns empty list if bluetooth is not enabled
        List<BluetoothHapPresetInfo> presets = mBluetoothHapClient.getAllPresetInfo(testDevice);
        assertTrue(presets.isEmpty());
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void setPresetName() {
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHapClient);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        mBluetoothHapClient.setPresetName(testDevice, 1 , "New Name");
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void setPresetNameForGroup() {
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHapClient);

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        mBluetoothHapClient.setPresetNameForGroup(1, 1 , "New Name");
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void setGetConnectionPolicy() {
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHapClient);

        assertThrows(NullPointerException.class,
                () -> mBluetoothHapClient.setConnectionPolicy(null, 0));
        assertEquals(BluetoothProfile.CONNECTION_POLICY_FORBIDDEN,
                mBluetoothHapClient.getConnectionPolicy(null));

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");
        assertTrue(mBluetoothHapClient.setConnectionPolicy(testDevice,
                BluetoothProfile.CONNECTION_POLICY_FORBIDDEN));

        TestUtils.dropPermissionAsShellUid();
        assertThrows(SecurityException.class, () -> mBluetoothHapClient
                .setConnectionPolicy(testDevice, BluetoothProfile.CONNECTION_POLICY_FORBIDDEN));
        assertThrows(SecurityException.class,
                () -> mBluetoothHapClient.getConnectionPolicy(testDevice));

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void registerUnregisterCallback() {
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHapClient);

        Executor executor = mContext.getMainExecutor();

        BluetoothHapClient.Callback callback = new BluetoothHapClient.Callback() {
            @Override
            public void onPresetSelected(BluetoothDevice device, int presetIndex, int reasonCode) {}

            @Override
            public void onPresetSelectionFailed(BluetoothDevice device, int statusCode) {}

            @Override
            public void onPresetSelectionForGroupFailed(int hapGroupId, int statusCode) {}

            @Override
            public void onPresetInfoChanged(BluetoothDevice device,
                    List<BluetoothHapPresetInfo> presetInfoList, int statusCode) {}

            @Override
            public void onSetPresetNameFailed(BluetoothDevice device, int status) {}

            @Override
            public void onSetPresetNameForGroupFailed(int hapGroupId, int status) {}
        };

        // Verify parameter
        assertThrows(NullPointerException.class, () ->
                mBluetoothHapClient.registerCallback(null, callback));
        assertThrows(NullPointerException.class, () ->
                mBluetoothHapClient.registerCallback(executor, null));
        assertThrows(NullPointerException.class, () ->
                mBluetoothHapClient.unregisterCallback(null));

        // Verify valid parameters
        mBluetoothHapClient.registerCallback(executor, callback);
        mBluetoothHapClient.unregisterCallback(callback);

        TestUtils.dropPermissionAsShellUid();
        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT);

        // Verify throws SecurityException without permission.BLUETOOTH_PRIVILEGED
        assertThrows(SecurityException.class,
                () -> mBluetoothHapClient.registerCallback(executor, callback));
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void registerCallbackNoPermission() {
        TestUtils.dropPermissionAsShellUid();
        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT);

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHapClient);

        Executor executor = mContext.getMainExecutor();

        BluetoothHapClient.Callback callback = new BluetoothHapClient.Callback() {
            @Override
            public void onPresetSelected(BluetoothDevice device, int presetIndex, int reasonCode) {}

            @Override
            public void onPresetSelectionFailed(BluetoothDevice device, int statusCode) {}

            @Override
            public void onPresetSelectionForGroupFailed(int hapGroupId, int statusCode) {}

            @Override
            public void onPresetInfoChanged(BluetoothDevice device,
                    List<BluetoothHapPresetInfo> presetInfoList, int statusCode) {}

            @Override
            public void onSetPresetNameFailed(BluetoothDevice device, int status) {}

            @Override
            public void onSetPresetNameForGroupFailed(int hapGroupId, int status) {}
        };

        // Verify throws SecurityException without permission.BLUETOOTH_PRIVILEGED
        assertThrows(SecurityException.class,
                () -> mBluetoothHapClient.registerCallback(executor, callback));

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void callbackCalls() {
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHapClient);

        BluetoothHapClient.Callback callback = new BluetoothHapClient.Callback() {
            @Override
            public void onPresetSelected(BluetoothDevice device, int presetIndex, int reasonCode) {
                mOnPresetSelected = true;
                mCallbackCountDownLatch.countDown();
            }

            @Override
            public void onPresetSelectionFailed(BluetoothDevice device, int statusCode) {
                mOnPresetSelectionFailed = true;
                mCallbackCountDownLatch.countDown();
            }

            @Override
            public void onPresetSelectionForGroupFailed(int hapGroupId, int statusCode) {
                mOnPresetSelectionForGroupFailed = true;
                mCallbackCountDownLatch.countDown();
            }

            @Override
            public void onPresetInfoChanged(BluetoothDevice device,
                    List<BluetoothHapPresetInfo> presetInfoList, int statusCode) {
                mOnPresetInfoChanged = true;
                mCallbackCountDownLatch.countDown();
            }

            @Override
            public void onSetPresetNameFailed(BluetoothDevice device, int status) {
                mOnSetPresetNameFailed = true;
                mCallbackCountDownLatch.countDown();
            }

            @Override
            public void onSetPresetNameForGroupFailed(int hapGroupId, int status) {
                mOnSetPresetNameForGroupFailed = true;
                mCallbackCountDownLatch.countDown();
            }
        };

        mCallbackCountDownLatch = new CountDownLatch(6);
        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");
        try {
            callback.onPresetSelected(testDevice, TEST_PRESET_INDEX, TEST_REASON_CODE);
            callback.onPresetSelectionFailed(testDevice, TEST_STATUS_CODE);
            callback.onPresetSelectionForGroupFailed(TEST_HAP_GROUP_ID, TEST_STATUS_CODE);
            callback.onPresetInfoChanged(testDevice, mPresetInfoList, TEST_STATUS_CODE);
            callback.onSetPresetNameFailed(testDevice, TEST_STATUS_CODE);
            callback.onSetPresetNameForGroupFailed(TEST_HAP_GROUP_ID, TEST_STATUS_CODE);

            // Wait for all the callback calls or 5 seconds to verify
            mCallbackCountDownLatch.await(5, TimeUnit.SECONDS);
            assertTrue(mOnPresetSelected);
            assertTrue(mOnPresetSelectionFailed);
            assertTrue(mOnPresetSelectionForGroupFailed);
            assertTrue(mOnPresetInfoChanged);
            assertTrue(mOnSetPresetNameFailed);
            assertTrue(mOnSetPresetNameForGroupFailed);
        } catch (InterruptedException e) {
            fail("Failed to register callback call: " + e.toString());
        }
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void getHearingAidType() {
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHapClient);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns 0x00 if bluetooth is not enabled
        assertEquals(0x00, mBluetoothHapClient.getHearingAidType(testDevice));
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void supportsSynchronizedPresets() {
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHapClient);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns false if bluetooth is not enabled
        assertFalse(mBluetoothHapClient.supportsSynchronizedPresets(testDevice));
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void supportsIndependentPresets() {
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHapClient);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns false if bluetooth is not enabled
        assertFalse(mBluetoothHapClient.supportsIndependentPresets(testDevice));
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void supportsDynamicPresets() {
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHapClient);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns false if bluetooth is not enabled
        assertFalse(mBluetoothHapClient.supportsDynamicPresets(testDevice));
    }

    @CddTest(requirements = {"7.4.3/C-2-1", "7.4.3/C-3-2"})
    @Test
    public void supportsWritablePresets() {
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHapClient);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns false if bluetooth is not enabled
        assertFalse(mBluetoothHapClient.supportsWritablePresets(testDevice));
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

    private final class BluetoothHapClientServiceListener implements
            BluetoothProfile.ServiceListener {

        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            mProfileConnectionlock.lock();
            mBluetoothHapClient = (BluetoothHapClient) proxy;
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
