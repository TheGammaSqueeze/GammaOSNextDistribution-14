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
import static android.bluetooth.BluetoothA2dp.DYNAMIC_BUFFER_SUPPORT_A2DP_SOFTWARE_ENCODING;
import static android.bluetooth.BluetoothA2dp.DYNAMIC_BUFFER_SUPPORT_NONE;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.app.UiAutomation;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothCodecConfig;
import android.bluetooth.BluetoothCodecType;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.util.Log;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

@RunWith(AndroidJUnit4.class)
@LargeTest
public class BluetoothA2dpTest {
    private static final String TAG = BluetoothA2dpTest.class.getSimpleName();

    private static final int PROXY_CONNECTION_TIMEOUT_MS = 1000;  // ms timeout for Proxy Connect

    private Context mContext;
    private boolean mHasBluetooth;
    private BluetoothAdapter mAdapter;
    private UiAutomation mUiAutomation;

    private BluetoothA2dp mBluetoothA2dp;
    private boolean mIsA2dpSupported;
    private boolean mIsProfileReady;
    private ReentrantLock mProfileConnectionlock;
    private Condition mConditionProfileConnection;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mHasBluetooth = TestUtils.hasBluetooth();
        if (!mHasBluetooth) return;

        mIsA2dpSupported = TestUtils.isProfileEnabled(BluetoothProfile.A2DP);
        if (!mIsA2dpSupported) return;

        mUiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);

        BluetoothManager manager = mContext.getSystemService(BluetoothManager.class);
        mAdapter = manager.getAdapter();
        assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));

        mProfileConnectionlock = new ReentrantLock();
        mConditionProfileConnection = mProfileConnectionlock.newCondition();

        mIsProfileReady = false;
        mBluetoothA2dp = null;

        mAdapter.getProfileProxy(mContext, new BluetoothA2dpServiceListener(),
                BluetoothProfile.A2DP);
    }

    @After
    public void tearDown() throws Exception {
        if (!(mHasBluetooth && mIsA2dpSupported)) {
            return;
        }
        if (mAdapter != null && mBluetoothA2dp != null) {
            mAdapter.closeProfileProxy(BluetoothProfile.A2DP, mBluetoothA2dp);
            mBluetoothA2dp = null;
            mIsProfileReady = false;
        }
        mAdapter = null;
        mUiAutomation.dropShellPermissionIdentity();
    }

    @Test
    public void closeProfileProxy() {
        assumeTrue(mHasBluetooth && mIsA2dpSupported);
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothA2dp);
        assertTrue(mIsProfileReady);

        mAdapter.closeProfileProxy(BluetoothProfile.A2DP, mBluetoothA2dp);
        assertTrue(waitForProfileDisconnect());
        assertFalse(mIsProfileReady);
    }

    @Test
    public void closeProfileProxy_onDifferentAdapter() {
        if (!(mHasBluetooth && mIsA2dpSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothA2dp);
        assertTrue(mIsProfileReady);


        Context context = mContext.createAttributionContext("test");
        BluetoothManager manager = context.getSystemService(BluetoothManager.class);
        BluetoothAdapter adapter = manager.getAdapter();

        assertNotEquals(mAdapter, adapter);

        adapter.closeProfileProxy(BluetoothProfile.A2DP, mBluetoothA2dp);
        assertTrue(waitForProfileDisconnect());
        assertFalse(mIsProfileReady);
    }

    @Test
    public void getConnectedDevices() {
        assumeTrue(mHasBluetooth && mIsA2dpSupported);
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothA2dp);

        assertEquals(mBluetoothA2dp.getConnectedDevices(),
                new ArrayList<BluetoothDevice>());
    }

    @Test
    public void getDevicesMatchingConnectionStates() {
        assumeTrue(mHasBluetooth && mIsA2dpSupported);
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothA2dp);

        assertEquals(mBluetoothA2dp.getDevicesMatchingConnectionStates(
                new int[]{BluetoothProfile.STATE_CONNECTED}),
                new ArrayList<BluetoothDevice>());
    }

    @Test
    public void getConnectionState() {
        assumeTrue(mHasBluetooth && mIsA2dpSupported);
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothA2dp);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        assertEquals(mBluetoothA2dp.getConnectionState(testDevice),
                BluetoothProfile.STATE_DISCONNECTED);
    }

    @Test
    public void isA2dpPlaying() {
        assumeTrue(mHasBluetooth && mIsA2dpSupported);
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothA2dp);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        assertFalse(mBluetoothA2dp.isA2dpPlaying(testDevice));
    }

    @Test
    public void getSupportedCodecTypes() {
        assumeTrue(mHasBluetooth && mIsA2dpSupported);
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothA2dp);

        assertThrows(SecurityException.class, () -> mBluetoothA2dp
                .getSupportedCodecTypes());

        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
        List<BluetoothCodecType> supportedCodecTypes =
                mBluetoothA2dp.getSupportedCodecTypes();
        assertNotNull(supportedCodecTypes);

        // getSupportedCodecTypes must not return null objects.
        for (BluetoothCodecType codecType : supportedCodecTypes) {
            assertNotNull(codecType);
        }

        // Supported codecs must contain at least the
        // mandatory SBC codec.
        assertTrue(supportedCodecTypes.contains(
                BluetoothCodecType.createFromType(
                        BluetoothCodecConfig.SOURCE_CODEC_TYPE_SBC)));
    }

    @Test
    public void getCodecStatus() {
        assumeTrue(mHasBluetooth && mIsA2dpSupported);
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothA2dp);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        assertNull(mBluetoothA2dp.getCodecStatus(testDevice));
        assertThrows(IllegalArgumentException.class, () -> {
            mBluetoothA2dp.getCodecStatus(null);
        });
    }

    @Test
    public void setCodecConfigPreference() {
        assumeTrue(mHasBluetooth && mIsA2dpSupported);
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothA2dp);

        assertThrows(IllegalArgumentException.class, () -> {
            mBluetoothA2dp.setCodecConfigPreference(null, null);
        });
    }

    @Test
    public void setOptionalCodecsEnabled() {
        assumeTrue(mHasBluetooth && mIsA2dpSupported);
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothA2dp);

        assertThrows(IllegalArgumentException.class,
                () -> mBluetoothA2dp.setOptionalCodecsEnabled(null, 0));
        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        mUiAutomation.dropShellPermissionIdentity();
        mBluetoothA2dp
                .setOptionalCodecsEnabled(testDevice, BluetoothA2dp.OPTIONAL_CODECS_PREF_UNKNOWN);
        mBluetoothA2dp
                .setOptionalCodecsEnabled(testDevice, BluetoothA2dp.OPTIONAL_CODECS_PREF_DISABLED);
        mBluetoothA2dp
                .setOptionalCodecsEnabled(testDevice, BluetoothA2dp.OPTIONAL_CODECS_PREF_ENABLED);
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);
    }

    @Test
    public void getConnectionPolicy() {
        assumeTrue(mHasBluetooth && mIsA2dpSupported);
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothA2dp);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        // Verify returns false when invalid input is given
        assertEquals(BluetoothProfile.CONNECTION_POLICY_FORBIDDEN,
                mBluetoothA2dp.getConnectionPolicy(null));

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns false if bluetooth is not enabled
        assertEquals(BluetoothProfile.CONNECTION_POLICY_FORBIDDEN,
                mBluetoothA2dp.getConnectionPolicy(testDevice));
    }

    @Test
    public void setConnectionPolicy() {
        assumeTrue(mHasBluetooth && mIsA2dpSupported);
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothA2dp);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        // Verify returns false when invalid input is given
        assertFalse(mBluetoothA2dp.setConnectionPolicy(
                testDevice, BluetoothProfile.CONNECTION_POLICY_UNKNOWN));
        assertFalse(mBluetoothA2dp.setConnectionPolicy(
                null, BluetoothProfile.CONNECTION_POLICY_ALLOWED));

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns false if bluetooth is not enabled
        assertFalse(mBluetoothA2dp.setConnectionPolicy(
                testDevice, BluetoothProfile.CONNECTION_POLICY_FORBIDDEN));
    }

    @Test
    public void getDynamicBufferSupport() {
        assumeTrue(mHasBluetooth && mIsA2dpSupported);
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothA2dp);

        int dynamicBufferSupport = mBluetoothA2dp.getDynamicBufferSupport();
        assertTrue(dynamicBufferSupport >= DYNAMIC_BUFFER_SUPPORT_NONE
                && dynamicBufferSupport <= DYNAMIC_BUFFER_SUPPORT_A2DP_SOFTWARE_ENCODING);

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns DYNAMIC_BUFFER_SUPPORT_NONE if bluetooth is not enabled
        assertEquals(DYNAMIC_BUFFER_SUPPORT_NONE, mBluetoothA2dp.getDynamicBufferSupport());
    }

    @Test
    public void getBufferConstraints() {
        assumeTrue(mHasBluetooth && mIsA2dpSupported);
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothA2dp);

        assertNotNull(mBluetoothA2dp.getBufferConstraints());
        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));
        // Verify returns null if bluetooth is not enabled
        assertNull(mBluetoothA2dp.getBufferConstraints());
    }

    @Test
    public void setBufferLengthMillis() {
        assumeTrue(mHasBluetooth && mIsA2dpSupported);
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothA2dp);

        int sourceCodecTypeAAC = 1;

        assertTrue(mBluetoothA2dp.setBufferLengthMillis(sourceCodecTypeAAC, 0));
        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));
        // Verify returns null if bluetooth is not enabled
        assertFalse(mBluetoothA2dp.setBufferLengthMillis(sourceCodecTypeAAC, 0));
    }

    @Test
    public void optionalCodecs() {
        assumeTrue(mHasBluetooth && mIsA2dpSupported);
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothA2dp);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        assertEquals(-1, mBluetoothA2dp.isOptionalCodecsEnabled(testDevice));
        assertEquals(-1, mBluetoothA2dp.isOptionalCodecsSupported(testDevice));

        mBluetoothA2dp.enableOptionalCodecs(testDevice);
        // Device is not in state machine so should not be enabled
        assertEquals(-1, mBluetoothA2dp.isOptionalCodecsEnabled(testDevice));

        mBluetoothA2dp.disableOptionalCodecs(testDevice);
        assertEquals(-1, mBluetoothA2dp.isOptionalCodecsEnabled(testDevice));

        assertThrows(IllegalArgumentException.class, () -> {
            mBluetoothA2dp.isOptionalCodecsEnabled(null);
        });
        assertThrows(IllegalArgumentException.class, () -> {
            mBluetoothA2dp.isOptionalCodecsSupported(null);
        });
        assertThrows(IllegalArgumentException.class, () -> {
            mBluetoothA2dp.enableOptionalCodecs(null);
        });
        assertThrows(IllegalArgumentException.class, () -> {
            mBluetoothA2dp.disableOptionalCodecs(null);
        });
    }

    @Test
    public void setAvrcpAbsoluteVolume() {
        assumeTrue(mHasBluetooth && mIsA2dpSupported);
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothA2dp);

        // Only check if no crash occurs
        try {
            mBluetoothA2dp.setAvrcpAbsoluteVolume(0);
        } catch (Exception e) {
            fail("setAvrcpAbsoluteVolume(0) should not fail. " + e.getMessage());
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

    private final class BluetoothA2dpServiceListener implements
            BluetoothProfile.ServiceListener {

        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            mProfileConnectionlock.lock();
            mBluetoothA2dp = (BluetoothA2dp) proxy;
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
