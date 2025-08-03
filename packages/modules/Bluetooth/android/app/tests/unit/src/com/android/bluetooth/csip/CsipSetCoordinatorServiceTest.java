/*
 * Copyright 2021 HIMSA II K/S - www.himsa.com.
 * Represented by EHIMA - www.ehima.com
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

package com.android.bluetooth.csip;

import static org.mockito.Mockito.*;

import android.bluetooth.*;
import android.bluetooth.BluetoothUuid;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Looper;
import android.os.ParcelUuid;
import android.os.RemoteException;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.TestUtils;
import com.android.bluetooth.Utils;
import com.android.bluetooth.btservice.AdapterService;
import com.android.bluetooth.btservice.ServiceFactory;
import com.android.bluetooth.btservice.storage.DatabaseManager;
import com.android.bluetooth.le_audio.LeAudioService;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeoutException;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class CsipSetCoordinatorServiceTest {
    private final String mFlagDexmarker = System.getProperty("dexmaker.share_classloader", "false");

    private Context mTargetContext;
    private BluetoothAdapter mAdapter;
    private BluetoothDevice mTestDevice;
    private BluetoothDevice mTestDevice2;
    private BluetoothDevice mTestDevice3;
    private CsipSetCoordinatorService mService;
    private HashMap<BluetoothDevice, LinkedBlockingQueue<Intent>> mTestDeviceQueueMap;
    private HashMap<BluetoothDevice, LinkedBlockingQueue<Intent>> mIntentQueue;
    private BroadcastReceiver mCsipSetCoordinatorIntentReceiver;
    private CsipSetCoordinatorStateMachine mCsipSetCoordinatorStateMachine;
    private static final int TIMEOUT_MS = 1000;

    @Mock private AdapterService mAdapterService;
    @Mock private LeAudioService mLeAudioService;
    @Spy
    private ServiceFactory mServiceFactory = new ServiceFactory();
    @Mock private DatabaseManager mDatabaseManager;
    @Mock private CsipSetCoordinatorNativeInterface mCsipSetCoordinatorNativeInterface;
    @Mock private IBluetoothCsipSetCoordinatorLockCallback mCsipSetCoordinatorLockCallback;

    @Before
    public void setUp() throws Exception {
        if (!mFlagDexmarker.equals("true")) {
            System.setProperty("dexmaker.share_classloader", "true");
        }

        mTargetContext = InstrumentationRegistry.getTargetContext();
        if (Looper.myLooper() == null) {
            Looper.prepare();
        }
        Assert.assertNotNull(Looper.myLooper());

        // Set up mocks and test assets
        MockitoAnnotations.initMocks(this);

        TestUtils.setAdapterService(mAdapterService);
        doReturn(mDatabaseManager).when(mAdapterService).getDatabase();

        mAdapter = BluetoothAdapter.getDefaultAdapter();

        CsipSetCoordinatorNativeInterface.setInstance(mCsipSetCoordinatorNativeInterface);
        startService();
        mService.mServiceFactory = mServiceFactory;
        when(mServiceFactory.getLeAudioService()).thenReturn(mLeAudioService);

        // Override the timeout value to speed up the test
        CsipSetCoordinatorStateMachine.sConnectTimeoutMs = TIMEOUT_MS; // 1s

        IntentFilter filter = new IntentFilter();
        filter.setPriority(IntentFilter.SYSTEM_HIGH_PRIORITY);
        filter.addAction(BluetoothCsipSetCoordinator.ACTION_CSIS_CONNECTION_STATE_CHANGED);
        filter.addAction(BluetoothCsipSetCoordinator.ACTION_CSIS_DEVICE_AVAILABLE);
        filter.addAction(BluetoothCsipSetCoordinator.ACTION_CSIS_SET_MEMBER_AVAILABLE);

        mCsipSetCoordinatorIntentReceiver = new CsipSetCoordinatorIntentReceiver();
        mTargetContext.registerReceiver(mCsipSetCoordinatorIntentReceiver, filter);

        mTestDevice = TestUtils.getTestDevice(mAdapter, 0);
        when(mCsipSetCoordinatorNativeInterface.getDevice(getByteAddress(mTestDevice)))
                .thenReturn(mTestDevice);
        mTestDevice2 = TestUtils.getTestDevice(mAdapter, 1);
        when(mCsipSetCoordinatorNativeInterface.getDevice(getByteAddress(mTestDevice2)))
                .thenReturn(mTestDevice2);
        mTestDevice3 = TestUtils.getTestDevice(mAdapter, 2);
        when(mCsipSetCoordinatorNativeInterface.getDevice(getByteAddress(mTestDevice3)))
                .thenReturn(mTestDevice3);

        doReturn(BluetoothDevice.BOND_BONDED)
                .when(mAdapterService)
                .getBondState(any(BluetoothDevice.class));
        doReturn(new ParcelUuid[] {BluetoothUuid.COORDINATED_SET})
                .when(mAdapterService)
                .getRemoteUuids(any(BluetoothDevice.class));

        mIntentQueue = new HashMap<>();
        mIntentQueue.put(mTestDevice, new LinkedBlockingQueue<>());
        mIntentQueue.put(mTestDevice2, new LinkedBlockingQueue<>());
        mIntentQueue.put(mTestDevice3, new LinkedBlockingQueue<>());
    }

    @After
    public void tearDown() throws Exception {
        if (!mFlagDexmarker.equals("true")) {
            System.setProperty("dexmaker.share_classloader", mFlagDexmarker);
        }

        if (Looper.myLooper() == null) {
            return;
        }

        if (mService == null) {
            return;
        }

        stopService();
        CsipSetCoordinatorNativeInterface.setInstance(null);
        mTargetContext.unregisterReceiver(mCsipSetCoordinatorIntentReceiver);
        TestUtils.clearAdapterService(mAdapterService);
        mIntentQueue.clear();
    }

    private void startService() throws TimeoutException {
        mService = new CsipSetCoordinatorService(mTargetContext);
        mService.start();
        mService.setAvailable(true);
    }

    private void stopService() throws TimeoutException {
        mService.stop();
        mService = CsipSetCoordinatorService.getCsipSetCoordinatorService();
        Assert.assertNull(mService);
    }

    /**
     * Test getting CsipSetCoordinator Service
     */
    @Test
    public void testGetService() {
        Assert.assertEquals(mService, CsipSetCoordinatorService.getCsipSetCoordinatorService());
    }

    /**
     * Test stop CsipSetCoordinator Service
     */
    @Test
    public void testStopService() {
        Assert.assertEquals(mService, CsipSetCoordinatorService.getCsipSetCoordinatorService());

        InstrumentationRegistry.getInstrumentation().runOnMainSync(mService::stop);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(mService::start);
    }

    /** Test get/set policy for BluetoothDevice */
    @Test
    public void testGetSetPolicy() {
        when(mDatabaseManager.getProfileConnectionPolicy(
                     mTestDevice, BluetoothProfile.CSIP_SET_COORDINATOR))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_UNKNOWN);
        Assert.assertEquals("Initial device policy", BluetoothProfile.CONNECTION_POLICY_UNKNOWN,
                mService.getConnectionPolicy(mTestDevice));

        when(mDatabaseManager.getProfileConnectionPolicy(
                     mTestDevice, BluetoothProfile.CSIP_SET_COORDINATOR))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_FORBIDDEN);
        Assert.assertEquals("Setting device policy to POLICY_FORBIDDEN",
                BluetoothProfile.CONNECTION_POLICY_FORBIDDEN,
                mService.getConnectionPolicy(mTestDevice));

        when(mDatabaseManager.getProfileConnectionPolicy(
                     mTestDevice, BluetoothProfile.CSIP_SET_COORDINATOR))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        Assert.assertEquals("Setting device policy to POLICY_ALLOWED",
                BluetoothProfile.CONNECTION_POLICY_ALLOWED,
                mService.getConnectionPolicy(mTestDevice));
    }

    /**
     * Test if getProfileConnectionPolicy works after the service is stopped.
     */
    @Test
    public void testGetPolicyAfterStopped() {
        mService.stop();
        when(mDatabaseManager
                .getProfileConnectionPolicy(mTestDevice, BluetoothProfile.CSIP_SET_COORDINATOR))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_UNKNOWN);
        Assert.assertEquals("Initial device policy",
                BluetoothProfile.CONNECTION_POLICY_UNKNOWN,
                mService.getConnectionPolicy(mTestDevice));
    }

    /**
     * Test okToConnect method using various test cases
     */
    @Test
    public void testOkToConnect() {
        int badPolicyValue = 1024;
        int badBondState = 42;
        testOkToConnectCase(mTestDevice, BluetoothDevice.BOND_NONE,
                BluetoothProfile.CONNECTION_POLICY_UNKNOWN, false);
        testOkToConnectCase(mTestDevice, BluetoothDevice.BOND_NONE,
                BluetoothProfile.CONNECTION_POLICY_FORBIDDEN, false);
        testOkToConnectCase(mTestDevice, BluetoothDevice.BOND_NONE,
                BluetoothProfile.CONNECTION_POLICY_ALLOWED, false);
        testOkToConnectCase(mTestDevice, BluetoothDevice.BOND_NONE, badPolicyValue, false);
        testOkToConnectCase(mTestDevice, BluetoothDevice.BOND_BONDING,
                BluetoothProfile.CONNECTION_POLICY_UNKNOWN, false);
        testOkToConnectCase(mTestDevice, BluetoothDevice.BOND_BONDING,
                BluetoothProfile.CONNECTION_POLICY_FORBIDDEN, false);
        testOkToConnectCase(mTestDevice, BluetoothDevice.BOND_BONDING,
                BluetoothProfile.CONNECTION_POLICY_ALLOWED, false);
        testOkToConnectCase(mTestDevice, BluetoothDevice.BOND_BONDING, badPolicyValue, false);
        testOkToConnectCase(mTestDevice, BluetoothDevice.BOND_BONDED,
                BluetoothProfile.CONNECTION_POLICY_UNKNOWN, true);
        testOkToConnectCase(mTestDevice, BluetoothDevice.BOND_BONDED,
                BluetoothProfile.CONNECTION_POLICY_FORBIDDEN, false);
        testOkToConnectCase(mTestDevice, BluetoothDevice.BOND_BONDED,
                BluetoothProfile.CONNECTION_POLICY_ALLOWED, true);
        testOkToConnectCase(mTestDevice, BluetoothDevice.BOND_BONDED, badPolicyValue, false);
        testOkToConnectCase(
                mTestDevice, badBondState, BluetoothProfile.CONNECTION_POLICY_UNKNOWN, false);
        testOkToConnectCase(
                mTestDevice, badBondState, BluetoothProfile.CONNECTION_POLICY_FORBIDDEN, false);
        testOkToConnectCase(
                mTestDevice, badBondState, BluetoothProfile.CONNECTION_POLICY_ALLOWED, false);
        testOkToConnectCase(mTestDevice, badBondState, badPolicyValue, false);
    }

    /**
     * Test that call to groupLockSet method calls corresponding native interface
     * method
     */
    @Test
    public void testGroupLockSetNative() {
        int group_id = 0x01;
        int group_size = 0x01;
        long uuidLsb = 0x01;
        long uuidMsb = 0x01;
        UUID uuid = new UUID(uuidMsb, uuidLsb);

        doCallRealMethod()
                .when(mCsipSetCoordinatorNativeInterface)
                .onDeviceAvailable(any(byte[].class), anyInt(), anyInt(), anyInt(), anyLong(),
                        anyLong());
        mCsipSetCoordinatorNativeInterface.onDeviceAvailable(
                getByteAddress(mTestDevice), group_id, group_size, 1, uuidLsb, uuidMsb);
        Assert.assertFalse(mService.isGroupLocked(group_id));

        UUID lock_uuid = mService.lockGroup(group_id, mCsipSetCoordinatorLockCallback);
        Assert.assertNotNull(lock_uuid);
        verify(mCsipSetCoordinatorNativeInterface, times(1)).groupLockSet(eq(group_id), eq(true));
        Assert.assertTrue(mService.isGroupLocked(group_id));

        doCallRealMethod()
                .when(mCsipSetCoordinatorNativeInterface)
                .onGroupLockChanged(anyInt(), anyBoolean(), anyInt());
        mCsipSetCoordinatorNativeInterface.onGroupLockChanged(
                group_id, true, IBluetoothCsipSetCoordinator.CSIS_GROUP_LOCK_SUCCESS);

        try {
            verify(mCsipSetCoordinatorLockCallback, times(1))
                    .onGroupLockSet(group_id, BluetoothStatusCodes.SUCCESS,
                        true);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }

        mService.unlockGroup(lock_uuid);
        verify(mCsipSetCoordinatorNativeInterface, times(1)).groupLockSet(eq(group_id), eq(false));

        mCsipSetCoordinatorNativeInterface.onGroupLockChanged(
                group_id, false, IBluetoothCsipSetCoordinator.CSIS_GROUP_LOCK_SUCCESS);
        Assert.assertFalse(mService.isGroupLocked(group_id));

        try {
            verify(mCsipSetCoordinatorLockCallback, times(1))
                    .onGroupLockSet(group_id, BluetoothStatusCodes.SUCCESS,
                        false);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Test that call to groupLockSet method calls corresponding native interface
     * method
     */
    @Test
    public void testGroupExclusiveLockSet() {
        int group_id = 0x01;
        int group_size = 0x01;
        long uuidLsb = 0x01;
        long uuidMsb = 0x01;
        UUID uuid = new UUID(uuidMsb, uuidLsb);

        doCallRealMethod()
                .when(mCsipSetCoordinatorNativeInterface)
                .onDeviceAvailable(any(byte[].class), anyInt(), anyInt(), anyInt(), anyLong(),
                        anyLong());
        mCsipSetCoordinatorNativeInterface.onDeviceAvailable(
                getByteAddress(mTestDevice), group_id, group_size, 1, uuidLsb, uuidMsb);
        Assert.assertFalse(mService.isGroupLocked(group_id));

        UUID lock_uuid = mService.lockGroup(group_id, mCsipSetCoordinatorLockCallback);
        verify(mCsipSetCoordinatorNativeInterface, times(1)).groupLockSet(eq(group_id), eq(true));
        Assert.assertNotNull(lock_uuid);
        Assert.assertTrue(mService.isGroupLocked(group_id));

        lock_uuid = mService.lockGroup(group_id, mCsipSetCoordinatorLockCallback);
        verify(mCsipSetCoordinatorNativeInterface, times(1)).groupLockSet(eq(group_id), eq(true));

        doCallRealMethod()
                .when(mCsipSetCoordinatorNativeInterface)
                .onGroupLockChanged(anyInt(), anyBoolean(), anyInt());

        try {
            verify(mCsipSetCoordinatorLockCallback, times(1))
                    .onGroupLockSet(group_id,
                    BluetoothStatusCodes.ERROR_CSIP_GROUP_LOCKED_BY_OTHER, true);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
        Assert.assertNull(lock_uuid);
    }

    /**
     * Test that an outgoing connection to device that does not have MICS UUID is
     * rejected
     */
    @Test
    public void testOutgoingConnectMissingUuid() {
        // Update the device policy so okToConnect() returns true
        when(mDatabaseManager.getProfileConnectionPolicy(
                     mTestDevice, BluetoothProfile.CSIP_SET_COORDINATOR))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        doReturn(true).when(mCsipSetCoordinatorNativeInterface).connect(any(BluetoothDevice.class));
        doReturn(true).when(mCsipSetCoordinatorNativeInterface).connect(any(BluetoothDevice.class));

        // Return No UUID
        doReturn(new ParcelUuid[] {})
                .when(mAdapterService)
                .getRemoteUuids(any(BluetoothDevice.class));

        // Send a connect request
        Assert.assertFalse("Connect expected to fail", mService.connect(mTestDevice));
    }

    /**
     * Test that an outgoing connection to device that have MICS UUID is successful
     */
    @Test
    public void testOutgoingConnectExistingUuid() {
        // Update the device policy so okToConnect() returns true
        when(mDatabaseManager.getProfileConnectionPolicy(
                     mTestDevice, BluetoothProfile.CSIP_SET_COORDINATOR))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        doReturn(true).when(mCsipSetCoordinatorNativeInterface).connect(any(BluetoothDevice.class));
        doReturn(true)
                .when(mCsipSetCoordinatorNativeInterface)
                .disconnect(any(BluetoothDevice.class));

        doReturn(new ParcelUuid[] {BluetoothUuid.COORDINATED_SET})
                .when(mAdapterService)
                .getRemoteUuids(any(BluetoothDevice.class));

        // Send a connect request
        Assert.assertTrue("Connect expected to succeed", mService.connect(mTestDevice));

        TestUtils.waitForIntent(TIMEOUT_MS, mIntentQueue.get(mTestDevice));
    }

    /**
     * Test that an outgoing connection to device with POLICY_FORBIDDEN is rejected
     */
    @Test
    public void testOutgoingConnectPolicyForbidden() {
        doReturn(true).when(mCsipSetCoordinatorNativeInterface).connect(any(BluetoothDevice.class));
        doReturn(true)
                .when(mCsipSetCoordinatorNativeInterface)
                .disconnect(any(BluetoothDevice.class));

        // Set the device policy to POLICY_FORBIDDEN so connect() should fail
        when(mDatabaseManager.getProfileConnectionPolicy(
                     mTestDevice, BluetoothProfile.CSIP_SET_COORDINATOR))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_FORBIDDEN);

        // Send a connect request
        Assert.assertFalse("Connect expected to fail", mService.connect(mTestDevice));
    }

    /**
     * Test that an outgoing connection times out
     */
    @Test
    public void testOutgoingConnectTimeout() {
        // Update the device policy so okToConnect() returns true
        when(mAdapterService.getDatabase()).thenReturn(mDatabaseManager);
        when(mDatabaseManager.getProfileConnectionPolicy(
                     mTestDevice, BluetoothProfile.CSIP_SET_COORDINATOR))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        doReturn(true).when(mCsipSetCoordinatorNativeInterface).connect(any(BluetoothDevice.class));
        doReturn(true)
                .when(mCsipSetCoordinatorNativeInterface)
                .disconnect(any(BluetoothDevice.class));

        // Send a connect request
        Assert.assertTrue("Connect failed", mService.connect(mTestDevice));

        // Verify the connection state broadcast, and that we are in Connecting state
        verifyConnectionStateIntent(TIMEOUT_MS, mTestDevice, BluetoothProfile.STATE_CONNECTING,
                BluetoothProfile.STATE_DISCONNECTED);
        Assert.assertEquals(
                BluetoothProfile.STATE_CONNECTING, mService.getConnectionState(mTestDevice));

        // Verify the connection state broadcast, and that we are in Disconnected state
        verifyConnectionStateIntent(CsipSetCoordinatorStateMachine.sConnectTimeoutMs * 2,
                mTestDevice, BluetoothProfile.STATE_DISCONNECTED,
                BluetoothProfile.STATE_CONNECTING);
        Assert.assertEquals(
                BluetoothProfile.STATE_DISCONNECTED, mService.getConnectionState(mTestDevice));
    }

    /**
     * Test that native callback generates proper intent.
     */
    @Test
    public void testStackEventDeviceAvailable() {
        int group_id = 0x01;
        int group_size = 0x03;
        long uuidLsb = 0x01;
        long uuidMsb = 0x01;
        UUID uuid = new UUID(uuidMsb, uuidLsb);

        doCallRealMethod()
                .when(mCsipSetCoordinatorNativeInterface)
                .onDeviceAvailable(any(byte[].class), anyInt(), anyInt(), anyInt(), anyLong(),
                        anyLong());
        mCsipSetCoordinatorNativeInterface.onDeviceAvailable(
                getByteAddress(mTestDevice), group_id, group_size, 0x02, uuidLsb, uuidMsb);

        Intent intent = TestUtils.waitForIntent(TIMEOUT_MS, mIntentQueue.get(mTestDevice));
        Assert.assertNotNull(intent);
        Assert.assertEquals(
                BluetoothCsipSetCoordinator.ACTION_CSIS_DEVICE_AVAILABLE, intent.getAction());
        Assert.assertEquals(mTestDevice, intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE));
        Assert.assertEquals(
                group_id, intent.getIntExtra(BluetoothCsipSetCoordinator.EXTRA_CSIS_GROUP_ID, -1));
        Assert.assertEquals(group_size,
                intent.getIntExtra(BluetoothCsipSetCoordinator.EXTRA_CSIS_GROUP_SIZE, -1));
        Assert.assertEquals(uuid,
                intent.getSerializableExtra(
                        BluetoothCsipSetCoordinator.EXTRA_CSIS_GROUP_TYPE_UUID));

        // Another device with the highest rank
        mCsipSetCoordinatorNativeInterface.onDeviceAvailable(
                getByteAddress(mTestDevice2), group_id, group_size, 0x01, uuidLsb, uuidMsb);

        // Yet another device with the lowest rank
        mCsipSetCoordinatorNativeInterface.onDeviceAvailable(
                getByteAddress(mTestDevice3), group_id, group_size, 0x03, uuidLsb, uuidMsb);

        // Verify if the list of devices is sorted, with the lowest rank value devices first
        List<BluetoothDevice> devices = mService.getGroupDevicesOrdered(group_id);
        Assert.assertEquals(0, devices.indexOf(mTestDevice2));
        Assert.assertEquals(1, devices.indexOf(mTestDevice));
        Assert.assertEquals(2, devices.indexOf(mTestDevice3));
    }

    /**
     * Test that native callback generates proper intent after group connected.
     */
    @Test
    public void testStackEventSetMemberAvailableAfterGroupConnected() {
        int group_id = 0x01;
        int group_size = 0x02;
        long uuidLsb = BluetoothUuid.CAP.getUuid().getLeastSignificantBits();
        long uuidMsb = BluetoothUuid.CAP.getUuid().getMostSignificantBits();

        // Make sure to use real methods when needed below
        doCallRealMethod()
                .when(mCsipSetCoordinatorNativeInterface)
                .onDeviceAvailable(any(byte[].class), anyInt(), anyInt(), anyInt(), anyLong(),
                        anyLong());
        doCallRealMethod()
                .when(mCsipSetCoordinatorNativeInterface)
                .onConnectionStateChanged(any(byte[].class), anyInt());
        doCallRealMethod()
                .when(mCsipSetCoordinatorNativeInterface)
                .onSetMemberAvailable(any(byte[].class), anyInt());

        mCsipSetCoordinatorNativeInterface.onDeviceAvailable(
                getByteAddress(mTestDevice), group_id, group_size, 0x02, uuidLsb, uuidMsb);

        mCsipSetCoordinatorNativeInterface.onConnectionStateChanged(
                getByteAddress(mTestDevice), BluetoothProfile.STATE_CONNECTED);

        // Comes from state machine
        mService.connectionStateChanged(mTestDevice, BluetoothProfile.STATE_CONNECTING,
                BluetoothProfile.STATE_CONNECTED);

        mCsipSetCoordinatorNativeInterface.onSetMemberAvailable(
                getByteAddress(mTestDevice2), group_id);

        Intent intent = TestUtils.waitForIntent(TIMEOUT_MS, mIntentQueue.get(mTestDevice2));
        Assert.assertNotNull(intent);
        Assert.assertEquals(
                BluetoothCsipSetCoordinator.ACTION_CSIS_SET_MEMBER_AVAILABLE, intent.getAction());
        Assert.assertEquals(mTestDevice2, intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE));
        Assert.assertEquals(
                group_id, intent.getIntExtra(BluetoothCsipSetCoordinator.EXTRA_CSIS_GROUP_ID, -1));
    }

    /**
     * Test that native callback generates proper intent before group connected.
     */
    @Test
    public void testStackEventSetMemberAvailableBeforeGroupConnected() {
        int group_id = 0x01;
        int group_size = 0x02;
        long uuidLsb = BluetoothUuid.CAP.getUuid().getLeastSignificantBits();
        long uuidMsb = BluetoothUuid.CAP.getUuid().getMostSignificantBits();

        // Make sure to use real methods when needed below
        doCallRealMethod()
                .when(mCsipSetCoordinatorNativeInterface)
                .onDeviceAvailable(any(byte[].class), anyInt(), anyInt(), anyInt(), anyLong(),
                        anyLong());
        doCallRealMethod()
                .when(mCsipSetCoordinatorNativeInterface)
                .onSetMemberAvailable(any(byte[].class), anyInt());
        doCallRealMethod()
                .when(mCsipSetCoordinatorNativeInterface)
                .onConnectionStateChanged(any(byte[].class), anyInt());

        mCsipSetCoordinatorNativeInterface.onDeviceAvailable(
                getByteAddress(mTestDevice), group_id, group_size, 0x02, uuidLsb, uuidMsb);

        mCsipSetCoordinatorNativeInterface.onConnectionStateChanged(
                getByteAddress(mTestDevice), BluetoothProfile.STATE_CONNECTED);

        mCsipSetCoordinatorNativeInterface.onSetMemberAvailable(
                getByteAddress(mTestDevice2), group_id);

        Intent intent = TestUtils.waitForNoIntent(TIMEOUT_MS, mIntentQueue.get(mTestDevice2));
        Assert.assertNull(intent);

          // Comes from state machine
        mService.connectionStateChanged(mTestDevice, BluetoothProfile.STATE_CONNECTING,
                BluetoothProfile.STATE_CONNECTED);

        intent = TestUtils.waitForIntent(TIMEOUT_MS, mIntentQueue.get(mTestDevice2));
        Assert.assertNotNull(intent);

        Assert.assertEquals(
                BluetoothCsipSetCoordinator.ACTION_CSIS_SET_MEMBER_AVAILABLE, intent.getAction());
        Assert.assertEquals(mTestDevice2, intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE));
        Assert.assertEquals(
                group_id, intent.getIntExtra(BluetoothCsipSetCoordinator.EXTRA_CSIS_GROUP_ID, -1));
    }

    /**
     * Test that we make CSIP FORBIDDEN after all set members are paired if the LE Audio connection
     * policy is FORBIDDEN.
     */
    @Test
    public void testDisableCsipAfterConnectingIfLeAudioDisabled() {
        int group_id = 0x01;
        int group_size = 0x02;
        long uuidLsb = BluetoothUuid.CAP.getUuid().getLeastSignificantBits();
        long uuidMsb = BluetoothUuid.CAP.getUuid().getMostSignificantBits();

        doCallRealMethod()
                .when(mCsipSetCoordinatorNativeInterface)
                .onDeviceAvailable(any(byte[].class), anyInt(), anyInt(), anyInt(), anyLong(),
                        anyLong());
        when(mLeAudioService.getConnectionPolicy(any())).thenReturn(
                BluetoothProfile.CONNECTION_POLICY_FORBIDDEN);

        // Make first set device available and connected
        mCsipSetCoordinatorNativeInterface.onDeviceAvailable(
                getByteAddress(mTestDevice), group_id, group_size, 0x02, uuidLsb, uuidMsb);
        mService.connectionStateChanged(mTestDevice, BluetoothProfile.STATE_CONNECTING,
                BluetoothProfile.STATE_CONNECTED);

        // Another device with the highest rank
        mCsipSetCoordinatorNativeInterface.onDeviceAvailable(
                getByteAddress(mTestDevice2), group_id, group_size, 0x01, uuidLsb, uuidMsb);

        // When LEA is FORBIDDEN, verify we don't disable CSIP until all set devices are available
        verify(mDatabaseManager, never()).setProfileConnectionPolicy(mTestDevice,
                BluetoothProfile.CSIP_SET_COORDINATOR,
                BluetoothProfile.CONNECTION_POLICY_FORBIDDEN);
        verify(mDatabaseManager, never()).setProfileConnectionPolicy(mTestDevice2,
                BluetoothProfile.CSIP_SET_COORDINATOR,
                BluetoothProfile.CONNECTION_POLICY_FORBIDDEN);

        // Mark the second device as connected
        mService.connectionStateChanged(mTestDevice2, BluetoothProfile.STATE_CONNECTING,
                BluetoothProfile.STATE_CONNECTED);

        // When LEA is FORBIDDEN, verify we disable CSIP once all set devices are available
        verify(mDatabaseManager, times(1)).setProfileConnectionPolicy(mTestDevice,
                BluetoothProfile.CSIP_SET_COORDINATOR,
                BluetoothProfile.CONNECTION_POLICY_FORBIDDEN);
        verify(mDatabaseManager, times(1)).setProfileConnectionPolicy(mTestDevice2,
                BluetoothProfile.CSIP_SET_COORDINATOR,
                BluetoothProfile.CONNECTION_POLICY_FORBIDDEN);
    }

    @Test
    public void testDump_doesNotCrash() {
        // Update the device policy so okToConnect() returns true
        when(mDatabaseManager.getProfileConnectionPolicy(
                mTestDevice, BluetoothProfile.CSIP_SET_COORDINATOR))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        doReturn(true).when(mCsipSetCoordinatorNativeInterface).connect(any(BluetoothDevice.class));
        doReturn(true)
                .when(mCsipSetCoordinatorNativeInterface)
                .disconnect(any(BluetoothDevice.class));
        doReturn(new ParcelUuid[] {BluetoothUuid.COORDINATED_SET})
                .when(mAdapterService)
                .getRemoteUuids(any(BluetoothDevice.class));
        // add state machines for testing dump()
        mService.connect(mTestDevice);

        TestUtils.waitForIntent(TIMEOUT_MS, mIntentQueue.get(mTestDevice));

        mService.dump(new StringBuilder());
    }

    /**
     * Helper function to test ConnectionStateIntent() method
     */
    private void verifyConnectionStateIntent(
            int timeoutMs, BluetoothDevice device, int newState, int prevState) {
        Intent intent = TestUtils.waitForIntent(timeoutMs, mIntentQueue.get(device));
        Assert.assertNotNull(intent);
        Assert.assertEquals(BluetoothCsipSetCoordinator.ACTION_CSIS_CONNECTION_STATE_CHANGED,
                intent.getAction());
        Assert.assertEquals(device, intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE));
        Assert.assertEquals(newState, intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1));
        Assert.assertEquals(
                prevState, intent.getIntExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, -1));
    }

    /**
     * Helper function to test okToConnect() method
     */
    private void testOkToConnectCase(
            BluetoothDevice device, int bondState, int policy, boolean expected) {
        doReturn(bondState).when(mAdapterService).getBondState(device);
        when(mDatabaseManager.getProfileConnectionPolicy(
                     device, BluetoothProfile.CSIP_SET_COORDINATOR))
                .thenReturn(policy);
        Assert.assertEquals(expected, mService.okToConnect(device));
    }

    /**
     * Helper function to get byte array for a device address
     */
    private byte[] getByteAddress(BluetoothDevice device) {
        if (device == null) {
            return Utils.getBytesFromAddress("00:00:00:00:00:00");
        }
        return Utils.getBytesFromAddress(device.getAddress());
    }

    private class CsipSetCoordinatorIntentReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                /* Ignore intent when service is inactive */
                if (mService == null) {
                    return;
                }

                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                // Use first device's queue in case of no device in the intent
                if (device == null) {
                    device = mTestDevice;
                }
                LinkedBlockingQueue<Intent> queue = mIntentQueue.get(device);
                Assert.assertNotNull(queue);
                queue.put(intent);
            } catch (InterruptedException e) {
                Assert.fail("Cannot add Intent to the queue: " + e.getMessage());
            }
        }
    }
}
