/*
 * Copyright (C) 2021 The Android Open Source Project
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
import android.bluetooth.BluetoothLeAudio;
import android.bluetooth.BluetoothLeAudioCodecConfig;
import android.bluetooth.BluetoothLeAudioCodecStatus;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothStatusCodes;
import android.content.Context;
import android.os.Build;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bluetooth.flags.Flags;
import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.CddTest;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

@RunWith(AndroidJUnit4.class)
public class BluetoothLeAudioTest {
    private static final String TAG = BluetoothLeAudioTest.class.getSimpleName();

    private static final int PROXY_CONNECTION_TIMEOUT_MS = 500;  // ms timeout for Proxy Connect

    private Context mContext;
    private BluetoothAdapter mAdapter;

    private BluetoothLeAudio mBluetoothLeAudio;
    private boolean mIsProfileReady;
    private Condition mConditionProfileConnection;
    private ReentrantLock mProfileConnectionlock;
    private Executor mTestExecutor;
    private TestCallback mTestCallback;
    private boolean mCodecConfigChangedCalled;
    private boolean mGroupNodeAddedCalled;
    private boolean mGroupNodeRemovedCalled;
    private boolean mGroupStatusChangedCalled;
    private boolean mGroupStreamStatusChangedCalled;
    private BluetoothDevice mTestDevice;
    private int mTestGroupId;
    private int mTestGroupStatus;
    private int mTestGroupStreamStatus;

    private static final BluetoothLeAudioCodecConfig LC3_16KHZ_CONFIG =
            new BluetoothLeAudioCodecConfig.Builder()
                    .setCodecType(BluetoothLeAudioCodecConfig.SOURCE_CODEC_TYPE_LC3)
                    .setSampleRate(BluetoothLeAudioCodecConfig.SAMPLE_RATE_16000)
                    .build();

    private static final List<BluetoothLeAudioCodecConfig> TEST_CODEC_CAPA_CONFIG = List.of(
            LC3_16KHZ_CONFIG);

    private static final BluetoothLeAudioCodecStatus TEST_CODEC_STATUS =
            new BluetoothLeAudioCodecStatus(LC3_16KHZ_CONFIG, LC3_16KHZ_CONFIG,
                    TEST_CODEC_CAPA_CONFIG, TEST_CODEC_CAPA_CONFIG,
                    TEST_CODEC_CAPA_CONFIG, TEST_CODEC_CAPA_CONFIG);

    class TestCallback implements BluetoothLeAudio.Callback {
        @Override
        public void onCodecConfigChanged(int groupId,
                                         BluetoothLeAudioCodecStatus status) {
            mCodecConfigChangedCalled = true;
            assertTrue(groupId == mTestGroupId);
            assertTrue(status == TEST_CODEC_STATUS);
        }
        @Override
        public void onGroupNodeAdded(BluetoothDevice device, int groupId) {
            mGroupNodeAddedCalled = true;
            assertTrue(groupId == mTestGroupId);
            assertTrue(device == mTestDevice);
        }
        @Override
        public void onGroupNodeRemoved(BluetoothDevice device, int groupId) {
            mGroupNodeRemovedCalled = true;
            assertTrue(groupId == mTestGroupId);
            assertTrue(device == mTestDevice);
        }
        @Override
        public void onGroupStatusChanged(int groupId, int groupStatus) {
            mGroupStatusChangedCalled = true;
            assertTrue(groupId == mTestGroupId);
            assertTrue(groupStatus == mTestGroupStatus);
        }
        @Override
        public void onGroupStreamStatusChanged(int groupId, int groupStreamStatus) {
            mGroupStreamStatusChangedCalled = true;
            assertTrue(groupId == mTestGroupId);
            assertTrue(groupStreamStatus == mTestGroupStreamStatus);
        }
    };

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();

        Assume.assumeTrue(ApiLevelUtil.isAtLeast(Build.VERSION_CODES.TIRAMISU));
        Assume.assumeTrue(TestUtils.isBleSupported(mContext));

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);

        BluetoothManager manager = mContext.getSystemService(BluetoothManager.class);
        mAdapter = manager.getAdapter();
        assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));

        mProfileConnectionlock = new ReentrantLock();
        mConditionProfileConnection = mProfileConnectionlock.newCondition();
        mIsProfileReady = false;
        mBluetoothLeAudio = null;

        Assume.assumeTrue(mAdapter.isLeAudioSupported() == BluetoothStatusCodes.FEATURE_SUPPORTED);

        mAdapter.getProfileProxy(mContext, new BluetoothLeAudioServiceListener(),
                BluetoothProfile.LE_AUDIO);

        mTestExecutor = mContext.getMainExecutor();
        mTestCallback = new TestCallback();
    }

    @After
    public void tearDown() throws Exception {
        if (mBluetoothLeAudio != null) {
            mBluetoothLeAudio.close();
            mBluetoothLeAudio = null;
            mIsProfileReady = false;
        }
        TestUtils.dropPermissionAsShellUid();
        mAdapter = null;
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void closeProfileProxy() {
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothLeAudio);
        assertTrue(mIsProfileReady);

        mAdapter.closeProfileProxy(BluetoothProfile.LE_AUDIO, mBluetoothLeAudio);
        assertTrue(waitForProfileDisconnect());
        assertFalse(mIsProfileReady);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void getConnectedDevices() {
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothLeAudio);

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns empty list if bluetooth is not enabled
        List<BluetoothDevice> connectedDevices = mBluetoothLeAudio.getConnectedDevices();
        assertTrue(connectedDevices.isEmpty());
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void getDevicesMatchingConnectionStates() {
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothLeAudio);

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns empty list if bluetooth is not enabled
        List<BluetoothDevice> connectedDevices =
                mBluetoothLeAudio.getDevicesMatchingConnectionStates(null);
        assertTrue(connectedDevices.isEmpty());
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void getConnectionState() {
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothLeAudio);

        mTestDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        // Verify returns false when invalid input is given
        assertEquals(BluetoothProfile.STATE_DISCONNECTED,
                mBluetoothLeAudio.getConnectionState(null));

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns false if bluetooth is not enabled
        assertEquals(BluetoothProfile.STATE_DISCONNECTED,
                mBluetoothLeAudio.getConnectionState(mTestDevice));
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void getAudioLocation() {
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothLeAudio);

        mTestDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns false if bluetooth is not enabled
        assertEquals(BluetoothLeAudio.AUDIO_LOCATION_INVALID,
                mBluetoothLeAudio.getAudioLocation(mTestDevice));
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void isInbandRingtoneEnabled() {
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothLeAudio);

        mTestDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns false if bluetooth is not enabled
        assertEquals(false, mBluetoothLeAudio.isInbandRingtoneEnabled(
                        BluetoothLeAudio.GROUP_ID_INVALID));
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void setgetConnectionPolicy() {
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothLeAudio);

        assertFalse(mBluetoothLeAudio.setConnectionPolicy(null, 0));
        assertEquals(BluetoothProfile.CONNECTION_POLICY_FORBIDDEN,
                mBluetoothLeAudio.getConnectionPolicy(null));
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void registerCallbackNoPermission() {
        TestUtils.dropPermissionAsShellUid();
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothLeAudio);

        // Verify throws SecurityException without permission.BLUETOOTH_PRIVILEGED
        assertThrows(SecurityException.class,
                () -> mBluetoothLeAudio.registerCallback(mTestExecutor, mTestCallback));

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void registerUnregisterCallback() {
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothLeAudio);

        // Verify parameter
        assertThrows(NullPointerException.class, () ->
                mBluetoothLeAudio.registerCallback(null, mTestCallback));
        assertThrows(NullPointerException.class, () ->
                mBluetoothLeAudio.registerCallback(mTestExecutor, null));
        assertThrows(NullPointerException.class, () ->
                mBluetoothLeAudio.unregisterCallback(null));

        // Test success register unregister
        try {
            mBluetoothLeAudio.registerCallback(mTestExecutor, mTestCallback);
        } catch (Exception e) {
            fail("Exception caught from register(): " + e.toString());
        }

        try {
            mBluetoothLeAudio.unregisterCallback(mTestCallback);
        } catch (Exception e) {
            fail("Exception caught from unregister(): " + e.toString());
        }
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void callback() {
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothLeAudio);

        mTestGroupId = 1;
        mTestDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");
        mTestGroupStatus = 0;

        mCodecConfigChangedCalled = false;
        mGroupNodeAddedCalled = false;
        mGroupStatusChangedCalled = false;
        mGroupNodeRemovedCalled = false;

        mTestCallback.onCodecConfigChanged(mTestGroupId, TEST_CODEC_STATUS);
        mTestCallback.onGroupNodeAdded(mTestDevice, mTestGroupId);
        mTestCallback.onGroupNodeRemoved(mTestDevice, mTestGroupId);
        mTestCallback.onGroupStatusChanged(mTestGroupId, mTestGroupStatus);

        assertTrue(mCodecConfigChangedCalled);
        assertTrue(mGroupNodeAddedCalled);
        assertTrue(mGroupNodeRemovedCalled);
        assertTrue(mGroupStatusChangedCalled);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @RequiresFlagsEnabled(Flags.FLAG_LEAUDIO_CALLBACK_ON_GROUP_STREAM_STATUS)
    @Test
    public void streamStatusCallback() {
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothLeAudio);

        mTestGroupId = 1;
        mTestDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");
        mTestGroupStreamStatus = 1;

        mGroupStreamStatusChangedCalled = false;

        mTestCallback.onGroupStreamStatusChanged(mTestGroupId, mTestGroupStreamStatus);

        assertTrue(mGroupStreamStatusChangedCalled);
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void getConnectedGroupLeadDevice() {
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothLeAudio);

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        int groupId = 1;

        // Verify returns null for unknown group id
        assertEquals(null, mBluetoothLeAudio.getConnectedGroupLeadDevice(groupId));
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void setVolume() {
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothLeAudio);

        try {
            mBluetoothLeAudio.setVolume(42);
        } catch (Exception e) {
            fail("Exception caught from setVolume(): " + e.toString());
        }
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void getCodecStatus() {
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothLeAudio);

        assertNull(mBluetoothLeAudio.getCodecStatus(0));
    }

    @CddTest(requirements = {"7.4.3/C-2-1"})
    @Test
    public void setCodecConfigPreference() {
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothLeAudio);

        BluetoothLeAudioCodecConfig codecConfig =
                new BluetoothLeAudioCodecConfig.Builder()
                        .setCodecType(BluetoothLeAudioCodecConfig.SOURCE_CODEC_TYPE_LC3)
                        .setCodecPriority(0)
                        .build();

        assertThrows(
                NullPointerException.class,
                () -> {
                    mBluetoothLeAudio.setCodecConfigPreference(0, null, null);
                });

        try {
            mBluetoothLeAudio.setCodecConfigPreference(0, codecConfig, codecConfig);
        } catch (Exception e) {
            fail("Exception caught from setCodecConfigPreference(): " + e.toString());
        }
    }

    @CddTest(requirements = {"3.5/C-0-9", "7.4.3/C-2-1"})
    @Test
    public void getGroupId() {
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothLeAudio);

        BluetoothDevice device = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");
        try {
            TestUtils.dropPermissionAsShellUid();
            TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT);
            mBluetoothLeAudio.getGroupId(device);
        } catch (Exception e) {
            fail("Exception caught from getGroupId(): " + e.toString());
        } finally {
            TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
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

    private final class BluetoothLeAudioServiceListener implements
            BluetoothProfile.ServiceListener {

        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            mProfileConnectionlock.lock();
            mBluetoothLeAudio = (BluetoothLeAudio) proxy;
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
