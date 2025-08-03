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

package com.android.bluetooth.hap;

import static androidx.test.espresso.intent.matcher.IntentMatchers.hasAction;
import static androidx.test.espresso.intent.matcher.IntentMatchers.hasExtra;

import static org.mockito.Mockito.after;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.hamcrest.MockitoHamcrest.argThat;
import static org.hamcrest.core.AllOf.allOf;

import static android.bluetooth.BluetoothHapClient.ACTION_HAP_CONNECTION_STATE_CHANGED;
import static android.bluetooth.BluetoothHapClient.ACTION_HAP_DEVICE_AVAILABLE;

import static android.bluetooth.BluetoothProfile.EXTRA_STATE;
import static android.bluetooth.BluetoothProfile.EXTRA_PREVIOUS_STATE;
import static android.bluetooth.BluetoothProfile.STATE_CONNECTED;
import static android.bluetooth.BluetoothProfile.STATE_CONNECTING;
import static android.bluetooth.BluetoothProfile.STATE_DISCONNECTED;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHapClient;
import android.bluetooth.BluetoothHapPresetInfo;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothStatusCodes;
import android.bluetooth.BluetoothUuid;
import android.bluetooth.IBluetoothHapClientCallback;
import android.content.AttributionSource;
import android.os.Binder;
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
import com.android.bluetooth.csip.CsipSetCoordinatorService;
import com.android.bluetooth.x.com.android.modules.utils.SynchronousResultReceiver;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeoutException;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class HapClientTest {
    private final String mFlagDexmarker = System.getProperty("dexmaker.share_classloader", "false");

    private static final int TIMEOUT_MS = 1000;
    private BluetoothAdapter mAdapter;
    private BluetoothDevice mDevice;
    private BluetoothDevice mDevice2;
    private BluetoothDevice mDevice3;
    private HapClientService mService;
    private HapClientService.BluetoothHapClientBinder mServiceBinder;
    private AttributionSource mAttributionSource;

    @Mock private AdapterService mAdapterService;
    @Mock private DatabaseManager mDatabaseManager;
    @Mock private HapClientNativeInterface mNativeInterface;
    @Mock private ServiceFactory mServiceFactory;
    @Mock private CsipSetCoordinatorService mCsipService;
    @Mock private IBluetoothHapClientCallback mCallback;
    @Mock private Binder mBinder;

    @Before
    public void setUp() throws Exception {
        if (!mFlagDexmarker.equals("true")) {
            System.setProperty("dexmaker.share_classloader", "true");
        }

        // Set up mocks and test assets
        MockitoAnnotations.initMocks(this);

        HapClientStateMachine.sConnectTimeoutMs = TIMEOUT_MS;

        doReturn(mDatabaseManager).when(mAdapterService).getDatabase();

        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mAttributionSource = mAdapter.getAttributionSource();

        HapClientNativeInterface.setInstance(mNativeInterface);
        startService();
        mService.mFactory = mServiceFactory;
        doReturn(mCsipService).when(mServiceFactory).getCsipSetCoordinatorService();
        mServiceBinder = (HapClientService.BluetoothHapClientBinder) mService.initBinder();
        mServiceBinder.mIsTesting = true;

        when(mCallback.asBinder()).thenReturn(mBinder);
        mService.mCallbacks.register(mCallback);

        mDevice = TestUtils.getTestDevice(mAdapter, 0);
        when(mNativeInterface.getDevice(getByteAddress(mDevice))).thenReturn(mDevice);
        mDevice2 = TestUtils.getTestDevice(mAdapter, 1);
        when(mNativeInterface.getDevice(getByteAddress(mDevice2))).thenReturn(mDevice2);
        mDevice3 = TestUtils.getTestDevice(mAdapter, 2);
        when(mNativeInterface.getDevice(getByteAddress(mDevice3))).thenReturn(mDevice3);

        doCallRealMethod().when(mNativeInterface)
                .sendMessageToService(any(HapClientStackEvent.class));
        doCallRealMethod().when(mNativeInterface).onFeaturesUpdate(any(byte[].class), anyInt());
        doCallRealMethod().when(mNativeInterface).onDeviceAvailable(any(byte[].class), anyInt());
        doCallRealMethod().when(mNativeInterface)
                .onActivePresetSelected(any(byte[].class), anyInt());
        doCallRealMethod().when(mNativeInterface)
                .onActivePresetSelectError(any(byte[].class), anyInt());
        doCallRealMethod().when(mNativeInterface)
                .onPresetNameSetError(any(byte[].class), anyInt(), anyInt());
        doCallRealMethod().when(mNativeInterface)
                .onPresetInfo(any(byte[].class), anyInt(), any(BluetoothHapPresetInfo[].class));
        doCallRealMethod().when(mNativeInterface)
                .onGroupPresetNameSetError(anyInt(), anyInt(), anyInt());

        /* Prepare CAS groups */
        doReturn(Arrays.asList(0x02, 0x03)).when(mCsipService).getAllGroupIds(BluetoothUuid.CAP);

        int groupId2 = 0x02;
        Map groups2 = new HashMap<Integer, ParcelUuid>();
        groups2.put(groupId2, ParcelUuid.fromString("00001853-0000-1000-8000-00805F9B34FB"));

        int groupId3 = 0x03;
        Map groups3 = new HashMap<Integer, ParcelUuid>();
        groups3.put(groupId3,
                ParcelUuid.fromString("00001853-0000-1000-8000-00805F9B34FB"));

        doReturn(Arrays.asList(mDevice, mDevice2)).when(mCsipService)
                        .getGroupDevicesOrdered(groupId2);
        doReturn(groups2).when(mCsipService).getGroupUuidMapByDevice(mDevice);
        doReturn(groups2).when(mCsipService).getGroupUuidMapByDevice(mDevice2);

        doReturn(Arrays.asList(mDevice3)).when(mCsipService).getGroupDevicesOrdered(0x03);
        doReturn(groups3).when(mCsipService).getGroupUuidMapByDevice(mDevice3);

        doReturn(Arrays.asList(mDevice)).when(mCsipService).getGroupDevicesOrdered(0x01);

        doReturn(BluetoothDevice.BOND_BONDED).when(mAdapterService)
                .getBondState(any(BluetoothDevice.class));
        doReturn(new ParcelUuid[]{BluetoothUuid.HAS}).when(mAdapterService)
                .getRemoteUuids(any(BluetoothDevice.class));
        doReturn(mDatabaseManager).when(mAdapterService).getDatabase();
    }

    @After
    public void tearDown() throws Exception {
        if (!mFlagDexmarker.equals("true")) {
            System.setProperty("dexmaker.share_classloader", mFlagDexmarker);
        }

        if (mService == null) {
            return;
        }

        mService.mCallbacks.unregister(mCallback);

        stopService();
        HapClientNativeInterface.setInstance(null);

        mAdapter = null;
    }

    private void startService() throws TimeoutException {
        mService = new HapClientService(mAdapterService);
        mService.start();
        mService.setAvailable(true);
    }

    private void stopService() throws TimeoutException {
        mService.stop();
        mService = HapClientService.getHapClientService();
        Assert.assertNull(mService);
    }

    /**
     * Test getting HA Service Client
     */
    @Test
    public void testGetHapService() {
        Assert.assertEquals(mService, HapClientService.getHapClientService());
    }

    /**
     * Test stop HA Service Client
     */
    @Test
    public void testStopHapService() {
        Assert.assertEquals(mService, HapClientService.getHapClientService());

        InstrumentationRegistry.getInstrumentation().runOnMainSync(mService::stop);
        InstrumentationRegistry.getInstrumentation().runOnMainSync(mService::start);
    }

    /** Test get/set policy for BluetoothDevice */
    @Test
    public void testGetSetPolicy() throws Exception {
        when(mDatabaseManager
                .getProfileConnectionPolicy(mDevice, BluetoothProfile.HAP_CLIENT))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_UNKNOWN);
        Assert.assertEquals("Initial device policy",
                BluetoothProfile.CONNECTION_POLICY_UNKNOWN,
                mService.getConnectionPolicy(mDevice));

        when(mDatabaseManager
                .getProfileConnectionPolicy(mDevice, BluetoothProfile.HAP_CLIENT))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_FORBIDDEN);
        Assert.assertEquals("Setting device policy to POLICY_FORBIDDEN",
                BluetoothProfile.CONNECTION_POLICY_FORBIDDEN,
                mService.getConnectionPolicy(mDevice));

        when(mDatabaseManager
                .getProfileConnectionPolicy(mDevice, BluetoothProfile.HAP_CLIENT))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        // call getConnectionPolicy via binder
        final SynchronousResultReceiver<Integer> recv = SynchronousResultReceiver.get();
        int defaultRecvValue = -1000;
        mServiceBinder.getConnectionPolicy(mDevice, mAttributionSource, recv);
        int policy = recv.awaitResultNoInterrupt(Duration.ofMillis(TIMEOUT_MS))
                .getValue(defaultRecvValue);
        Assert.assertEquals("Setting device policy to POLICY_ALLOWED",
                BluetoothProfile.CONNECTION_POLICY_ALLOWED, policy);
    }

    /**
     * Test if getProfileConnectionPolicy works after the service is stopped.
     */
    @Test
    public void testGetPolicyAfterStopped() {
        mService.stop();
        when(mDatabaseManager
                .getProfileConnectionPolicy(mDevice, BluetoothProfile.HAP_CLIENT))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_UNKNOWN);
        Assert.assertEquals("Initial device policy",
                BluetoothProfile.CONNECTION_POLICY_UNKNOWN,
                mService.getConnectionPolicy(mDevice));
    }

    /**
     * Test okToConnect method using various test cases
     */
    @Test
    public void testOkToConnect() {
        int badPolicyValue = 1024;
        int badBondState = 42;
        testOkToConnectCase(mDevice,
                BluetoothDevice.BOND_NONE, BluetoothProfile.CONNECTION_POLICY_UNKNOWN, false);
        testOkToConnectCase(mDevice,
                BluetoothDevice.BOND_NONE, BluetoothProfile.CONNECTION_POLICY_FORBIDDEN, false);
        testOkToConnectCase(mDevice,
                BluetoothDevice.BOND_NONE, BluetoothProfile.CONNECTION_POLICY_ALLOWED, false);
        testOkToConnectCase(mDevice,
                BluetoothDevice.BOND_NONE, badPolicyValue, false);
        testOkToConnectCase(mDevice,
                BluetoothDevice.BOND_BONDING, BluetoothProfile.CONNECTION_POLICY_UNKNOWN, false);
        testOkToConnectCase(mDevice,
                BluetoothDevice.BOND_BONDING, BluetoothProfile.CONNECTION_POLICY_FORBIDDEN, false);
        testOkToConnectCase(mDevice,
                BluetoothDevice.BOND_BONDING, BluetoothProfile.CONNECTION_POLICY_ALLOWED, false);
        testOkToConnectCase(mDevice,
                BluetoothDevice.BOND_BONDING, badPolicyValue, false);
        testOkToConnectCase(mDevice,
                BluetoothDevice.BOND_BONDED, BluetoothProfile.CONNECTION_POLICY_UNKNOWN, true);
        testOkToConnectCase(mDevice,
                BluetoothDevice.BOND_BONDED, BluetoothProfile.CONNECTION_POLICY_FORBIDDEN, false);
        testOkToConnectCase(mDevice,
                BluetoothDevice.BOND_BONDED, BluetoothProfile.CONNECTION_POLICY_ALLOWED, true);
        testOkToConnectCase(mDevice,
                BluetoothDevice.BOND_BONDED, badPolicyValue, false);
        testOkToConnectCase(mDevice,
                badBondState, BluetoothProfile.CONNECTION_POLICY_UNKNOWN, false);
        testOkToConnectCase(mDevice,
                badBondState, BluetoothProfile.CONNECTION_POLICY_FORBIDDEN, false);
        testOkToConnectCase(mDevice,
                badBondState, BluetoothProfile.CONNECTION_POLICY_ALLOWED, false);
        testOkToConnectCase(mDevice,
                badBondState, badPolicyValue, false);
    }

    /**
     * Test that an outgoing connection to device that does not have HAS UUID is rejected
     */
    @Test
    public void testOutgoingConnectMissingHasUuid() {
        // Update the device policy so okToConnect() returns true
        when(mDatabaseManager
                .getProfileConnectionPolicy(mDevice, BluetoothProfile.HAP_CLIENT))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        doReturn(true).when(mNativeInterface).connectHapClient(any(BluetoothDevice.class));
        doReturn(true).when(mNativeInterface).disconnectHapClient(any(BluetoothDevice.class));

        // Return No UUID
        doReturn(new ParcelUuid[]{}).when(mAdapterService)
                .getRemoteUuids(any(BluetoothDevice.class));

        // Send a connect request
        Assert.assertFalse("Connect expected to fail", mService.connect(mDevice));
    }

    /**
     * Test that an outgoing connection to device that have HAS UUID is successful
     */
    @Test
    public void testOutgoingConnectExistingHasUuid() {
        // Update the device policy so okToConnect() returns true
        when(mDatabaseManager
                .getProfileConnectionPolicy(mDevice, BluetoothProfile.HAP_CLIENT))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        doReturn(true).when(mNativeInterface).connectHapClient(any(BluetoothDevice.class));
        doReturn(true).when(mNativeInterface).disconnectHapClient(any(BluetoothDevice.class));

        doReturn(new ParcelUuid[]{BluetoothUuid.HAS}).when(mAdapterService)
                .getRemoteUuids(any(BluetoothDevice.class));

        // Send a connect request
        Assert.assertTrue("Connect expected to succeed", mService.connect(mDevice));

        verify(mAdapterService, timeout(TIMEOUT_MS)).sendBroadcast(any(), any());
    }

    /**
     * Test that an outgoing connection to device with POLICY_FORBIDDEN is rejected
     */
    @Test
    public void testOutgoingConnectPolicyForbidden() {
        doReturn(true).when(mNativeInterface).connectHapClient(any(BluetoothDevice.class));
        doReturn(true).when(mNativeInterface).disconnectHapClient(any(BluetoothDevice.class));

        // Set the device policy to POLICY_FORBIDDEN so connect() should fail
        when(mDatabaseManager
                .getProfileConnectionPolicy(mDevice, BluetoothProfile.HAP_CLIENT))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_FORBIDDEN);

        // Send a connect request
        Assert.assertFalse("Connect expected to fail", mService.connect(mDevice));
    }

    /**
     * Test that an outgoing connection times out
     */
    @Test
    public void testOutgoingConnectTimeout() throws Exception {
        InOrder order = inOrder(mAdapterService);

        // Update the device policy so okToConnect() returns true
        when(mDatabaseManager
                .getProfileConnectionPolicy(mDevice, BluetoothProfile.HAP_CLIENT))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        doReturn(true).when(mNativeInterface).connectHapClient(any(BluetoothDevice.class));
        doReturn(true).when(mNativeInterface).disconnectHapClient(any(BluetoothDevice.class));

        // Send a connect request
        Assert.assertTrue("Connect failed", mService.connect(mDevice));

        order.verify(mAdapterService, timeout(TIMEOUT_MS))
                .sendBroadcast(
                        argThat(
                                allOf(
                                        hasAction(ACTION_HAP_CONNECTION_STATE_CHANGED),
                                        hasExtra(BluetoothDevice.EXTRA_DEVICE, mDevice),
                                        hasExtra(EXTRA_STATE, STATE_CONNECTING),
                                        hasExtra(EXTRA_PREVIOUS_STATE, STATE_DISCONNECTED))),
                        any());

        Assert.assertEquals(BluetoothProfile.STATE_CONNECTING,
                mService.getConnectionState(mDevice));

        // Verify the connection state broadcast, and that we are in Disconnected state via binder
        order.verify(mAdapterService, timeout(HapClientStateMachine.sConnectTimeoutMs * 2))
                .sendBroadcast(
                        argThat(
                                allOf(
                                        hasAction(ACTION_HAP_CONNECTION_STATE_CHANGED),
                                        hasExtra(BluetoothDevice.EXTRA_DEVICE, mDevice),
                                        hasExtra(EXTRA_STATE, STATE_DISCONNECTED),
                                        hasExtra(EXTRA_PREVIOUS_STATE, STATE_CONNECTING))),
                        any());

        final SynchronousResultReceiver<Integer> recv = SynchronousResultReceiver.get();
        int defaultRecvValue = -1000;
        mServiceBinder.getConnectionState(mDevice, mAttributionSource, recv);
        int state = recv.awaitResultNoInterrupt(Duration.ofMillis(TIMEOUT_MS))
                .getValue(defaultRecvValue);
        Assert.assertEquals(BluetoothProfile.STATE_DISCONNECTED, state);
    }

    /**
     * Test that an outgoing connection to two device that have HAS UUID is successful
     */
    @Test
    public void testConnectTwo() throws Exception {
        InOrder order = inOrder(mAdapterService);
        doReturn(new ParcelUuid[]{BluetoothUuid.HAS}).when(mAdapterService)
                .getRemoteUuids(any(BluetoothDevice.class));

        // Send a connect request for the 1st device
        testConnectingDevice(order, mDevice);

        // Send a connect request for the 2nd device
        BluetoothDevice Device2 = TestUtils.getTestDevice(mAdapter, 1);
        testConnectingDevice(order, Device2);

        // indirect call of mService.getConnectedDevices to test BluetoothHearingAidBinder
        final SynchronousResultReceiver<List<BluetoothDevice>> recv =
                SynchronousResultReceiver.get();
        mServiceBinder.getConnectedDevices(mAttributionSource, recv);
        List<BluetoothDevice> devices = recv.awaitResultNoInterrupt(Duration.ofMillis(TIMEOUT_MS))
                .getValue(null);
        Assert.assertTrue(devices.contains(mDevice));
        Assert.assertTrue(devices.contains(Device2));
        Assert.assertNotEquals(mDevice, Device2);
    }

    /**
     * Test that for the unknown device the API calls are not forwarded down the stack to native.
     */
    @Test
    public void testCallsForNotConnectedDevice() {
        Assert.assertEquals(BluetoothHapClient.PRESET_INDEX_UNAVAILABLE,
                mService.getActivePresetIndex(mDevice));
    }

    /**
     * Test getting HAS coordinated sets.
     */
    @Test
    public void testGetHapGroupCoordinatedOps() throws Exception {
        InOrder order = inOrder(mAdapterService);
        doReturn(new ParcelUuid[]{BluetoothUuid.HAS}).when(mAdapterService)
                .getRemoteUuids(any(BluetoothDevice.class));
        testConnectingDevice(order, mDevice);
        testConnectingDevice(order, mDevice2);
        testConnectingDevice(order, mDevice3);

        int flags = 0x04;
        mNativeInterface.onFeaturesUpdate(getByteAddress(mDevice), flags);

        int flags3 = 0x04;
        mNativeInterface.onFeaturesUpdate(getByteAddress(mDevice3), flags);

        /* This one has no coordinated operation support but is part of a coordinated set with
         * mDevice, which supports it, thus mDevice will forward the operation to mDevice2.
         * This device should also be rocognised as grouped one.
         */
        int flags2 = 0;
        mNativeInterface.onFeaturesUpdate(getByteAddress(mDevice2), flags2);

        /* Two devices support coordinated operations thus shall report valid group ID */
        Assert.assertEquals(2, mService.getHapGroup(mDevice));
        Assert.assertEquals(3, mService.getHapGroup(mDevice3));

        /* Third one has no coordinated operations support but is part of the group */
        final SynchronousResultReceiver<Integer> recv = SynchronousResultReceiver.get();
        int defaultRecvValue = -1000;
        mServiceBinder.getHapGroup(mDevice2, mAttributionSource, recv);
        int hapGroup = recv.awaitResultNoInterrupt(Duration.ofMillis(TIMEOUT_MS))
                .getValue(defaultRecvValue);
        Assert.assertEquals(2, hapGroup);
    }

    /**
     * Test that selectPreset properly calls the native method.
     */
    @Test
    public void testSelectPresetNative() {
        InOrder order = inOrder(mAdapterService);
        doReturn(new ParcelUuid[]{BluetoothUuid.HAS}).when(mAdapterService)
                .getRemoteUuids(any(BluetoothDevice.class));
        testConnectingDevice(order, mDevice);

        // Verify Native Interface call
        mService.selectPreset(mDevice, 0x00);
        verify(mNativeInterface, times(0))
                .selectActivePreset(eq(mDevice), eq(0x00));
        try {
            verify(mCallback, after(TIMEOUT_MS).times(1)).onPresetSelectionFailed(eq(mDevice),
                    eq(BluetoothStatusCodes.ERROR_HAP_INVALID_PRESET_INDEX));
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }

        mServiceBinder.selectPreset(mDevice, 0x01, mAttributionSource);
        verify(mNativeInterface, times(1))
                .selectActivePreset(eq(mDevice), eq(0x01));
    }

    /**
     * Test that groupSelectActivePreset properly calls the native method.
     */
    @Test
    public void testGroupSelectActivePresetNative() {
        InOrder order = inOrder(mAdapterService);
        doReturn(new ParcelUuid[]{BluetoothUuid.HAS}).when(mAdapterService)
                .getRemoteUuids(any(BluetoothDevice.class));
        testConnectingDevice(order, mDevice3);

        int flags = 0x01;
        mNativeInterface.onFeaturesUpdate(getByteAddress(mDevice3), flags);

        // Verify Native Interface call
        mService.selectPresetForGroup(0x03, 0x00);
        try {
            verify(mCallback, after(TIMEOUT_MS).times(1)).onPresetSelectionForGroupFailed(
                    eq(0x03), eq(BluetoothStatusCodes.ERROR_HAP_INVALID_PRESET_INDEX));
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }

        mServiceBinder.selectPresetForGroup(0x03, 0x01, mAttributionSource);
        verify(mNativeInterface, times(1))
                .groupSelectActivePreset(eq(0x03), eq(0x01));
    }

    /**
     * Test that nextActivePreset properly calls the native method.
     */
    @Test
    public void testSwitchToNextPreset() {
        InOrder order = inOrder(mAdapterService);
        doReturn(new ParcelUuid[]{BluetoothUuid.HAS}).when(mAdapterService)
                .getRemoteUuids(any(BluetoothDevice.class));
        testConnectingDevice(order, mDevice);

        // Verify Native Interface call
        mServiceBinder.switchToNextPreset(mDevice, mAttributionSource);
        verify(mNativeInterface, times(1))
                .nextActivePreset(eq(mDevice));
    }

    /**
     * Test that groupNextActivePreset properly calls the native method.
     */
    @Test
    public void testSwitchToNextPresetForGroup() {
        InOrder order = inOrder(mAdapterService);
        doReturn(new ParcelUuid[]{BluetoothUuid.HAS}).when(mAdapterService)
                .getRemoteUuids(any(BluetoothDevice.class));
        testConnectingDevice(order, mDevice3);
        int flags = 0x01;
        mNativeInterface.onFeaturesUpdate(getByteAddress(mDevice3), flags);

        // Verify Native Interface call
        mServiceBinder.switchToNextPresetForGroup(0x03, mAttributionSource);
        verify(mNativeInterface, times(1)).groupNextActivePreset(eq(0x03));
    }

    /**
     * Test that previousActivePreset properly calls the native method.
     */
    @Test
    public void testSwitchToPreviousPreset() {
        InOrder order = inOrder(mAdapterService);
        doReturn(new ParcelUuid[]{BluetoothUuid.HAS}).when(mAdapterService)
                .getRemoteUuids(any(BluetoothDevice.class));
        testConnectingDevice(order, mDevice);

        // Verify Native Interface call
        mServiceBinder.switchToPreviousPreset(mDevice, mAttributionSource);
        verify(mNativeInterface, times(1))
                .previousActivePreset(eq(mDevice));
    }

    /**
     * Test that groupPreviousActivePreset properly calls the native method.
     */
    @Test
    public void testSwitchToPreviousPresetForGroup() {
        InOrder order = inOrder(mAdapterService);
        doReturn(new ParcelUuid[]{BluetoothUuid.HAS}).when(mAdapterService)
                .getRemoteUuids(any(BluetoothDevice.class));
        testConnectingDevice(order, mDevice);
        testConnectingDevice(order, mDevice2);

        int flags = 0x01;
        mNativeInterface.onFeaturesUpdate(getByteAddress(mDevice), flags);

        // Verify Native Interface call
        mServiceBinder.switchToPreviousPresetForGroup(0x02, mAttributionSource);
        verify(mNativeInterface, times(1)).groupPreviousActivePreset(eq(0x02));
    }

    /**
     * Test that getActivePresetIndex returns cached value.
     */
    @Test
    public void testGetActivePresetIndex() throws Exception {
        InOrder order = inOrder(mAdapterService);
        doReturn(new ParcelUuid[]{BluetoothUuid.HAS}).when(mAdapterService)
                .getRemoteUuids(any(BluetoothDevice.class));
        testConnectingDevice(order, mDevice);
        testOnPresetSelected(mDevice, 0x01);

        // Verify cached value via binder
        final SynchronousResultReceiver<Integer> recv = SynchronousResultReceiver.get();
        int defaultRecvValue = -1000;
        mServiceBinder.getActivePresetIndex(mDevice, mAttributionSource, recv);
        int presetIndex = recv.awaitResultNoInterrupt(Duration.ofMillis(TIMEOUT_MS))
                .getValue(defaultRecvValue);
        Assert.assertEquals(0x01, presetIndex);
    }

    /**
     * Test that getActivePresetInfo returns cached value for valid parameters.
     */
    @Test
    public void testGetPresetInfoAndActivePresetInfo() throws Exception {
        InOrder order = inOrder(mAdapterService);
        doReturn(new ParcelUuid[]{BluetoothUuid.HAS}).when(mAdapterService)
                .getRemoteUuids(any(BluetoothDevice.class));
        testConnectingDevice(order, mDevice2);

        // Check when active preset is not known yet
        final SynchronousResultReceiver<List<BluetoothHapPresetInfo>> presetListRecv =
                SynchronousResultReceiver.get();
        mServiceBinder.getAllPresetInfo(mDevice2, mAttributionSource, presetListRecv);
        List<BluetoothHapPresetInfo> presetList = presetListRecv.awaitResultNoInterrupt(
                Duration.ofMillis(TIMEOUT_MS)).getValue(null);

        final SynchronousResultReceiver<BluetoothHapPresetInfo> presetRecv =
                SynchronousResultReceiver.get();
        mServiceBinder.getPresetInfo(mDevice2, 0x01, mAttributionSource, presetRecv);
        BluetoothHapPresetInfo presetInfo = presetRecv.awaitResultNoInterrupt(
                Duration.ofMillis(TIMEOUT_MS)).getValue(null);
        Assert.assertTrue(presetList.contains(presetInfo));
        Assert.assertEquals(0x01, presetInfo.getIndex());

        Assert.assertEquals(BluetoothHapClient.PRESET_INDEX_UNAVAILABLE,
                mService.getActivePresetIndex(mDevice2));
        Assert.assertEquals(null, mService.getActivePresetInfo(mDevice2));

        // Inject active preset change event
        testOnPresetSelected(mDevice2, 0x01);

        // Check when active preset is known
        Assert.assertEquals(0x01, mService.getActivePresetIndex(mDevice2));
        final SynchronousResultReceiver<BluetoothHapPresetInfo> recv =
                SynchronousResultReceiver.get();
        mServiceBinder.getActivePresetInfo(mDevice2, mAttributionSource, recv);
        BluetoothHapPresetInfo info = recv.awaitResultNoInterrupt(Duration.ofMillis(TIMEOUT_MS))
                .getValue(null);
        Assert.assertNotNull(info);
        Assert.assertEquals("One", info.getName());
    }

    /**
     * Test that setPresetName properly calls the native method for the valid parameters.
     */
    @Test
    public void testSetPresetNameNative() {
        InOrder order = inOrder(mAdapterService);
        doReturn(new ParcelUuid[]{BluetoothUuid.HAS}).when(mAdapterService)
                .getRemoteUuids(any(BluetoothDevice.class));
        testConnectingDevice(order, mDevice);

        mServiceBinder.setPresetName(mDevice, 0x00, "ExamplePresetName", mAttributionSource);
        verify(mNativeInterface, times(0))
                .setPresetName(eq(mDevice), eq(0x00), eq("ExamplePresetName"));
        try {
            verify(mCallback, after(TIMEOUT_MS).times(1)).onSetPresetNameFailed(eq(mDevice),
                    eq(BluetoothStatusCodes.ERROR_HAP_INVALID_PRESET_INDEX));
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }

        // Verify Native Interface call
        mService.setPresetName(mDevice, 0x01, "ExamplePresetName");
        verify(mNativeInterface, times(1))
                .setPresetName(eq(mDevice), eq(0x01), eq("ExamplePresetName"));
    }

    /**
     * Test that setPresetNameForGroup properly calls the native method for the valid parameters.
     */
    @Test
    public void testSetPresetNameForGroup() {
        InOrder order = inOrder(mAdapterService);
        doReturn(new ParcelUuid[]{BluetoothUuid.HAS}).when(mAdapterService)
                .getRemoteUuids(any(BluetoothDevice.class));
        int test_group = 0x02;
        for (BluetoothDevice device : mCsipService.getGroupDevicesOrdered(test_group)) {
            testConnectingDevice(order, device);
        }

        int flags = 0x21;
        mNativeInterface.onFeaturesUpdate(getByteAddress(mDevice), flags);

        mServiceBinder.setPresetNameForGroup(
                test_group, 0x00, "ExamplePresetName", mAttributionSource);
        try {
            verify(mCallback, after(TIMEOUT_MS).times(1)).onSetPresetNameForGroupFailed(eq(test_group),
                    eq(BluetoothStatusCodes.ERROR_HAP_INVALID_PRESET_INDEX));
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }

        mService.setPresetNameForGroup(-1, 0x01, "ExamplePresetName");
        try {
            verify(mCallback, after(TIMEOUT_MS).times(1)).onSetPresetNameForGroupFailed(eq(-1),
                    eq(BluetoothStatusCodes.ERROR_CSIP_INVALID_GROUP_ID));
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }

        // Verify Native Interface call
        mService.setPresetNameForGroup(test_group, 0x01, "ExamplePresetName");
        verify(mNativeInterface, times(1))
                .groupSetPresetName(eq(test_group), eq(0x01), eq("ExamplePresetName"));
    }

    /**
     * Test that native callback generates proper intent.
     */
    @Test
    public void testStackEventDeviceAvailable() {
        doReturn(new ParcelUuid[]{BluetoothUuid.HAS}).when(mAdapterService)
                .getRemoteUuids(any(BluetoothDevice.class));

        doCallRealMethod()
                .when(mNativeInterface)
                .onDeviceAvailable(any(byte[].class), anyInt());
        mNativeInterface.onDeviceAvailable(getByteAddress(mDevice), 0x03);

        verify(mAdapterService, timeout(TIMEOUT_MS))
                .sendBroadcast(
                        argThat(
                                allOf(
                                        hasAction(ACTION_HAP_DEVICE_AVAILABLE),
                                        hasExtra(BluetoothDevice.EXTRA_DEVICE, mDevice),
                                        hasExtra(BluetoothHapClient.EXTRA_HAP_FEATURES, 0x03))),
                        any());
    }

    /**
     * Test that native callback generates proper callback call.
     */
    @Test
    public void testStackEventOnPresetSelected() {
        doReturn(new ParcelUuid[]{BluetoothUuid.HAS}).when(mAdapterService)
                .getRemoteUuids(any(BluetoothDevice.class));

        doCallRealMethod()
                .when(mNativeInterface)
                .onActivePresetSelected(any(byte[].class), anyInt());
        mNativeInterface.onActivePresetSelected(getByteAddress(mDevice), 0x01);

        try {
            verify(mCallback, after(TIMEOUT_MS).times(1)).onPresetSelected(eq(mDevice),
                    eq(0x01), eq(BluetoothStatusCodes.REASON_LOCAL_STACK_REQUEST));
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }

        // Verify that getting current preset returns a proper value now
        Assert.assertEquals(0x01, mService.getActivePresetIndex(mDevice));
    }

    /**
     * Test that native callback generates proper callback call.
     */
    @Test
    public void testStackEventOnActivePresetSelectError() {
        doReturn(new ParcelUuid[]{BluetoothUuid.HAS}).when(mAdapterService)
                .getRemoteUuids(any(BluetoothDevice.class));


        doCallRealMethod()
                .when(mNativeInterface)
                .onActivePresetSelectError(any(byte[].class), anyInt());
        /* Send INVALID_PRESET_INDEX error */
        mNativeInterface.onActivePresetSelectError(getByteAddress(mDevice), 0x05);

        try {
            verify(mCallback, after(TIMEOUT_MS).times(1)).onPresetSelectionFailed(eq(mDevice),
                    eq(BluetoothStatusCodes.ERROR_HAP_INVALID_PRESET_INDEX));
        } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Test that native callback generates proper callback call.
     */
    @Test
    public void testStackEventOnPresetInfo() {
        InOrder order = inOrder(mAdapterService);
        doReturn(new ParcelUuid[]{BluetoothUuid.HAS}).when(mAdapterService)
                .getRemoteUuids(any(BluetoothDevice.class));

        // Connect and inject initial presets
        testConnectingDevice(order, mDevice);

        int info_reason = HapClientStackEvent.PRESET_INFO_REASON_PRESET_INFO_UPDATE;
        BluetoothHapPresetInfo[] info =
                {new BluetoothHapPresetInfo.Builder(0x01, "OneChangedToUnavailable")
                        .setWritable(true)
                        .setAvailable(false)
                        .build()};

        doCallRealMethod()
                .when(mNativeInterface)
                .onPresetInfo(any(byte[].class), anyInt(), any());
        mNativeInterface.onPresetInfo(getByteAddress(mDevice), info_reason, info);

        ArgumentCaptor<List<BluetoothHapPresetInfo>> presetsCaptor =
                ArgumentCaptor.forClass(List.class);
        try {
            verify(mCallback, after(TIMEOUT_MS).times(1)).onPresetInfoChanged(eq(mDevice),
                    presetsCaptor.capture(), eq(BluetoothStatusCodes.REASON_REMOTE_REQUEST));
        } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
        }

        List<BluetoothHapPresetInfo> presets = presetsCaptor.getValue();
        Assert.assertEquals(3, presets.size());

        Optional<BluetoothHapPresetInfo> preset = presetsCaptor.getValue()
                                    .stream()
                                    .filter(p -> 0x01 == p.getIndex())
                                    .findFirst();
        Assert.assertEquals("OneChangedToUnavailable", preset.get().getName());
        Assert.assertFalse(preset.get().isAvailable());
        Assert.assertTrue(preset.get().isWritable());
    }

    /**
     * Test that native callback generates proper callback call.
     */
    @Test
    public void testStackEventOnPresetNameSetError() {
        doReturn(new ParcelUuid[]{BluetoothUuid.HAS}).when(mAdapterService)
                .getRemoteUuids(any(BluetoothDevice.class));

        doCallRealMethod()
                .when(mNativeInterface)
                .onPresetNameSetError(any(byte[].class), anyInt(), anyInt());
        /* Not a valid name length */
        mNativeInterface.onPresetNameSetError(getByteAddress(mDevice), 0x01,
                HapClientStackEvent.STATUS_INVALID_PRESET_NAME_LENGTH);
        try {
            verify(mCallback, after(TIMEOUT_MS).times(1)).onSetPresetNameFailed(eq(mDevice),
                    eq(BluetoothStatusCodes.ERROR_HAP_PRESET_NAME_TOO_LONG));
        } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
        }

        /* Invalid preset index provided */
        mNativeInterface.onPresetNameSetError(getByteAddress(mDevice), 0x01,
                HapClientStackEvent.STATUS_INVALID_PRESET_INDEX);
        try {
            verify(mCallback, after(TIMEOUT_MS).times(1)).onSetPresetNameFailed(eq(mDevice),
                    eq(BluetoothStatusCodes.ERROR_HAP_INVALID_PRESET_INDEX));
        } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
        }

        /* Not allowed on this particular preset */
        mNativeInterface.onPresetNameSetError(getByteAddress(mDevice), 0x01,
                HapClientStackEvent.STATUS_SET_NAME_NOT_ALLOWED);
        try {
            verify(mCallback, after(TIMEOUT_MS).times(1)).onSetPresetNameFailed(eq(mDevice),
                    eq(BluetoothStatusCodes.ERROR_REMOTE_OPERATION_REJECTED));
        } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
        }

        /* Not allowed on this particular preset at this time, might be possible later on */
        mNativeInterface.onPresetNameSetError(getByteAddress(mDevice), 0x01,
                HapClientStackEvent.STATUS_OPERATION_NOT_POSSIBLE);
        try {
            verify(mCallback, after(TIMEOUT_MS).times(2)).onSetPresetNameFailed(eq(mDevice),
                    eq(BluetoothStatusCodes.ERROR_REMOTE_OPERATION_REJECTED));
        } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
        }

        /* Not allowed on all presets - for example missing characteristic */
        mNativeInterface.onPresetNameSetError(getByteAddress(mDevice), 0x01,
                HapClientStackEvent.STATUS_OPERATION_NOT_SUPPORTED);
        try {
            verify(mCallback, after(TIMEOUT_MS).times(1)).onSetPresetNameFailed(eq(mDevice),
                    eq(BluetoothStatusCodes.ERROR_REMOTE_OPERATION_NOT_SUPPORTED));
        } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Test that native callback generates proper callback call.
     */
    @Test
    public void testStackEventOnGroupPresetNameSetError() {
        doReturn(new ParcelUuid[]{BluetoothUuid.HAS}).when(mAdapterService)
                .getRemoteUuids(any(BluetoothDevice.class));

        doCallRealMethod()
                .when(mNativeInterface)
                .onGroupPresetNameSetError(anyInt(), anyInt(), anyInt());

        /* Not a valid name length */
        mNativeInterface.onGroupPresetNameSetError(0x01, 0x01,
                HapClientStackEvent.STATUS_INVALID_PRESET_NAME_LENGTH);
        try {
            verify(mCallback, after(TIMEOUT_MS).times(1)).onSetPresetNameForGroupFailed(0x01,
                    BluetoothStatusCodes.ERROR_HAP_PRESET_NAME_TOO_LONG);
        } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
        }

        /* Invalid preset index provided */
        mNativeInterface.onGroupPresetNameSetError(0x01, 0x01,
                HapClientStackEvent.STATUS_INVALID_PRESET_INDEX);
        try {
            verify(mCallback, after(TIMEOUT_MS).times(1)).onSetPresetNameForGroupFailed(0x01,
                    BluetoothStatusCodes.ERROR_HAP_INVALID_PRESET_INDEX);
        } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
        }

        /* Not allowed on this particular preset */
        mNativeInterface.onGroupPresetNameSetError(0x01, 0x01,
                HapClientStackEvent.STATUS_SET_NAME_NOT_ALLOWED);
        try {
            verify(mCallback, after(TIMEOUT_MS).times(1)).onSetPresetNameForGroupFailed(0x01,
                    BluetoothStatusCodes.ERROR_REMOTE_OPERATION_REJECTED);
        } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
        }

        /* Not allowed on this particular preset at this time, might be possible later on */
        mNativeInterface.onGroupPresetNameSetError(0x01, 0x01,
                HapClientStackEvent.STATUS_OPERATION_NOT_POSSIBLE);
        try {
            verify(mCallback, after(TIMEOUT_MS).times(2)).onSetPresetNameForGroupFailed(0x01,
                    BluetoothStatusCodes.ERROR_REMOTE_OPERATION_REJECTED);
        } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
        }

        /* Not allowed on all presets - for example if peer is missing optional CP characteristic */
        mNativeInterface.onGroupPresetNameSetError(0x01, 0x01,
                HapClientStackEvent.STATUS_OPERATION_NOT_SUPPORTED);
        try {
            verify(mCallback, after(TIMEOUT_MS).times(1)).onSetPresetNameForGroupFailed(0x01,
                    BluetoothStatusCodes.ERROR_REMOTE_OPERATION_NOT_SUPPORTED);
        } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
        }
    }

    @Test
    public void testServiceBinderGetDevicesMatchingConnectionStates() throws Exception {
        final SynchronousResultReceiver<List<BluetoothDevice>> recv =
                SynchronousResultReceiver.get();
        mServiceBinder.getDevicesMatchingConnectionStates(null, mAttributionSource, recv);
        List<BluetoothDevice> devices = recv.awaitResultNoInterrupt(Duration.ofMillis(TIMEOUT_MS))
                .getValue(null);
        Assert.assertEquals(0, devices.size());
    }

    @Test
    public void testServiceBinderSetConnectionPolicy() throws Exception {
        final SynchronousResultReceiver<Boolean> recv = SynchronousResultReceiver.get();
        boolean defaultRecvValue = false;
        mServiceBinder.setConnectionPolicy(
                mDevice, BluetoothProfile.CONNECTION_POLICY_UNKNOWN, mAttributionSource, recv);
        Assert.assertTrue(recv.awaitResultNoInterrupt(Duration.ofMillis(TIMEOUT_MS))
                .getValue(defaultRecvValue));
        verify(mDatabaseManager).setProfileConnectionPolicy(
                mDevice, BluetoothProfile.HAP_CLIENT, BluetoothProfile.CONNECTION_POLICY_UNKNOWN);
    }

    @Test
    public void testServiceBinderGetFeatures() throws Exception {
        final SynchronousResultReceiver<Integer> recv = SynchronousResultReceiver.get();
        int defaultRecvValue = -1000;
        mServiceBinder.getFeatures(mDevice, mAttributionSource, recv);
        int features = recv.awaitResultNoInterrupt(Duration.ofMillis(TIMEOUT_MS))
                .getValue(defaultRecvValue);
        Assert.assertEquals(0x00, features);
    }

    @Test
    public void testServiceBinderRegisterUnregisterCallback() throws Exception {
        IBluetoothHapClientCallback callback = Mockito.mock(IBluetoothHapClientCallback.class);
        Binder binder = Mockito.mock(Binder.class);
        when(callback.asBinder()).thenReturn(binder);

        int size = mService.mCallbacks.getRegisteredCallbackCount();
        SynchronousResultReceiver<Void> recv = SynchronousResultReceiver.get();
        mServiceBinder.registerCallback(callback, mAttributionSource, recv);
        recv.awaitResultNoInterrupt(Duration.ofMillis(TIMEOUT_MS)).getValue(null);
        Assert.assertEquals(size + 1, mService.mCallbacks.getRegisteredCallbackCount());

        recv = SynchronousResultReceiver.get();
        mServiceBinder.unregisterCallback(callback, mAttributionSource, recv);
        recv.awaitResultNoInterrupt(Duration.ofMillis(TIMEOUT_MS)).getValue(null);
        Assert.assertEquals(size, mService.mCallbacks.getRegisteredCallbackCount());

    }

    @Test
    public void testDumpDoesNotCrash() {
        // Update the device policy so okToConnect() returns true
        when(mDatabaseManager
                .getProfileConnectionPolicy(mDevice, BluetoothProfile.HAP_CLIENT))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        doReturn(true).when(mNativeInterface).connectHapClient(any(BluetoothDevice.class));
        doReturn(true).when(mNativeInterface).disconnectHapClient(any(BluetoothDevice.class));

        doReturn(new ParcelUuid[]{BluetoothUuid.HAS}).when(mAdapterService)
                .getRemoteUuids(any(BluetoothDevice.class));

        // Add state machine for testing dump()
        mService.connect(mDevice);

        verify(mAdapterService, timeout(TIMEOUT_MS)).sendBroadcast(any(), any());

        mService.dump(new StringBuilder());
    }

    /**
     * Helper function to test device connecting
     */
    private void prepareConnectingDevice(BluetoothDevice device) {
        // Prepare intent queue and all the mocks
        when(mNativeInterface.getDevice(getByteAddress(device))).thenReturn(device);
        when(mDatabaseManager
                .getProfileConnectionPolicy(device, BluetoothProfile.HAP_CLIENT))
                .thenReturn(BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        doReturn(true).when(mNativeInterface).connectHapClient(any(BluetoothDevice.class));
        doReturn(true).when(mNativeInterface).disconnectHapClient(any(BluetoothDevice.class));
    }

    /** Helper function to test device connecting */
    private void testConnectingDevice(InOrder order, BluetoothDevice device) {
        prepareConnectingDevice(device);
        // Send a connect request
        Assert.assertTrue("Connect expected to succeed", mService.connect(device));
        verifyConnectingDevice(order, device);
    }

    /** Helper function to test device connecting */
    private void verifyConnectingDevice(InOrder order, BluetoothDevice device) {
        // Verify the connection state broadcast, and that we are in Connecting state
        order.verify(mAdapterService, timeout(TIMEOUT_MS))
                .sendBroadcast(
                        argThat(
                                allOf(
                                        hasAction(ACTION_HAP_CONNECTION_STATE_CHANGED),
                                        hasExtra(BluetoothDevice.EXTRA_DEVICE, device),
                                        hasExtra(EXTRA_STATE, STATE_CONNECTING),
                                        hasExtra(EXTRA_PREVIOUS_STATE, STATE_DISCONNECTED))),
                        any());
        Assert.assertEquals(BluetoothProfile.STATE_CONNECTING, mService.getConnectionState(device));

        // Send a message to trigger connection completed
        HapClientStackEvent evt =
                new HapClientStackEvent(HapClientStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        evt.device = device;
        evt.valueInt1 = HapClientStackEvent.CONNECTION_STATE_CONNECTED;
        mService.messageFromNative(evt);

        // Verify the connection state broadcast, and that we are in Connected state
        order.verify(mAdapterService, timeout(TIMEOUT_MS))
                .sendBroadcast(
                        argThat(
                                allOf(
                                        hasAction(ACTION_HAP_CONNECTION_STATE_CHANGED),
                                        hasExtra(BluetoothDevice.EXTRA_DEVICE, device),
                                        hasExtra(EXTRA_STATE, STATE_CONNECTED),
                                        hasExtra(EXTRA_PREVIOUS_STATE, STATE_CONNECTING))),
                        any());
        Assert.assertEquals(BluetoothProfile.STATE_CONNECTED, mService.getConnectionState(device));

        evt = new HapClientStackEvent(HapClientStackEvent.EVENT_TYPE_DEVICE_AVAILABLE);
        evt.device = device;
        evt.valueInt1 = 0x01;   // features
        mService.messageFromNative(evt);

        order.verify(mAdapterService, timeout(TIMEOUT_MS))
                .sendBroadcast(
                        argThat(
                                allOf(
                                        hasAction(ACTION_HAP_DEVICE_AVAILABLE),
                                        hasExtra(BluetoothDevice.EXTRA_DEVICE, device),
                                        hasExtra(BluetoothHapClient.EXTRA_HAP_FEATURES, 0x01))),
                        any());

        evt = new HapClientStackEvent(HapClientStackEvent.EVENT_TYPE_DEVICE_FEATURES);
        evt.device = device;
        evt.valueInt1 = 0x01; // features
        mService.messageFromNative(evt);

        // Inject some initial presets
        List<BluetoothHapPresetInfo> presets =
                new ArrayList<BluetoothHapPresetInfo>(Arrays.asList(
                        new BluetoothHapPresetInfo.Builder(0x01, "One")
                                .setAvailable(true)
                                .setWritable(false)
                                .build(),
                        new BluetoothHapPresetInfo.Builder(0x02, "Two")
                                .setAvailable(true)
                                .setWritable(true)
                                .build(),
                        new BluetoothHapPresetInfo.Builder(0x03, "Three")
                                .setAvailable(false)
                                .setWritable(false)
                                .build()));
        mService.updateDevicePresetsCache(device,
                HapClientStackEvent.PRESET_INFO_REASON_ALL_PRESET_INFO, presets);
    }

    private void testOnPresetSelected(BluetoothDevice device, int index) {
        HapClientStackEvent evt =
                new HapClientStackEvent(HapClientStackEvent.EVENT_TYPE_ON_ACTIVE_PRESET_SELECTED);
        evt.device = device;
        evt.valueInt1 = index;
        mService.messageFromNative(evt);

        try {
            verify(mCallback, after(TIMEOUT_MS).times(1)).onPresetSelected(eq(device),
                    eq(evt.valueInt1), eq(BluetoothStatusCodes.REASON_LOCAL_STACK_REQUEST));
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Helper function to test okToConnect() method
     */
    private void testOkToConnectCase(BluetoothDevice device, int bondState, int policy,
                                     boolean expected) {
        doReturn(bondState).when(mAdapterService).getBondState(device);
        when(mDatabaseManager.getProfileConnectionPolicy(device, BluetoothProfile.HAP_CLIENT))
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
}
