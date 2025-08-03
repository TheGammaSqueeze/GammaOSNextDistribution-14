/*
 * Copyright 2018 The Android Open Source Project
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
package com.android.bluetooth.pbapclient;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.provider.CallLog;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.BluetoothMethodProxy;
import com.android.bluetooth.TestUtils;
import com.android.bluetooth.btservice.AdapterService;
import com.android.bluetooth.btservice.storage.DatabaseManager;
import com.android.bluetooth.x.com.android.modules.utils.SynchronousResultReceiver;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class PbapClientServiceTest {
    private static final String REMOTE_DEVICE_ADDRESS = "00:00:00:00:00:00";

    private PbapClientService mService = null;
    private BluetoothAdapter mAdapter = null;
    private Context mTargetContext;
    private BluetoothDevice mRemoteDevice;
    boolean mIsAdapterServiceSet;
    boolean mIsPbapClientServiceStarted;

    @Mock private AdapterService mAdapterService;

    @Mock private DatabaseManager mDatabaseManager;

    @Before
    public void setUp() throws Exception {
        mTargetContext = InstrumentationRegistry.getTargetContext();
        MockitoAnnotations.initMocks(this);
        TestUtils.setAdapterService(mAdapterService);
        mIsAdapterServiceSet = true;
        doReturn(mDatabaseManager).when(mAdapterService).getDatabase();
        mService = new PbapClientService(mTargetContext);
        mService.start();
        mService.setAvailable(true);
        mIsPbapClientServiceStarted = true;
        // Try getting the Bluetooth adapter
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        Assert.assertNotNull(mAdapter);
        mRemoteDevice = mAdapter.getRemoteDevice(REMOTE_DEVICE_ADDRESS);
    }

    @After
    public void tearDown() throws Exception {
        if (!mIsAdapterServiceSet) {
            return;
        }
        if (mIsPbapClientServiceStarted) {
            mService.stop();
            mService = PbapClientService.getPbapClientService();
            Assert.assertNull(mService);
        }
        TestUtils.clearAdapterService(mAdapterService);
        BluetoothMethodProxy.setInstanceForTesting(null);
    }

    @Test
    public void testInitialize() {
        Assert.assertNotNull(PbapClientService.getPbapClientService());
    }

    @Test
    public void testSetPbapClientService_withNull() {
        PbapClientService.setPbapClientService(null);

        assertThat(PbapClientService.getPbapClientService()).isNull();
    }

    @Test
    public void dump_callsStateMachineDump() {
        PbapClientStateMachine sm = mock(PbapClientStateMachine.class);
        mService.mPbapClientStateMachineMap.put(mRemoteDevice, sm);
        StringBuilder builder = new StringBuilder();

        mService.dump(builder);

        verify(sm).dump(builder);
    }

    @Test
    public void testSetConnectionPolicy_withNullDevice_throwsIAE() {
        assertThrows(IllegalArgumentException.class, () -> mService.setConnectionPolicy(
                null, BluetoothProfile.CONNECTION_POLICY_ALLOWED));
    }

    @Test
    public void testSetConnectionPolicy() {
        int connectionPolicy = BluetoothProfile.CONNECTION_POLICY_UNKNOWN;
        when(mDatabaseManager.setProfileConnectionPolicy(
                mRemoteDevice, BluetoothProfile.PBAP_CLIENT, connectionPolicy)).thenReturn(true);

        assertThat(mService.setConnectionPolicy(mRemoteDevice, connectionPolicy)).isTrue();
    }

    @Test
    public void testGetConnectionPolicy_withNullDevice_throwsIAE() {
        assertThrows(IllegalArgumentException.class, () -> mService.getConnectionPolicy(null));
    }

    @Test
    public void testGetConnectionPolicy() {
        int connectionPolicy = BluetoothProfile.CONNECTION_POLICY_ALLOWED;
        when(mDatabaseManager.getProfileConnectionPolicy(
                mRemoteDevice, BluetoothProfile.PBAP_CLIENT)).thenReturn(connectionPolicy);

        assertThat(mService.getConnectionPolicy(mRemoteDevice)).isEqualTo(connectionPolicy);
    }

    @Test
    public void testConnect_withNullDevice_throwsIAE() {
        assertThrows(IllegalArgumentException.class, () -> mService.connect(null));
    }

    @Test
    public void testConnect_whenPolicyIsForbidden_returnsFalse() {
        int connectionPolicy = BluetoothProfile.CONNECTION_POLICY_FORBIDDEN;
        when(mDatabaseManager.getProfileConnectionPolicy(
                mRemoteDevice, BluetoothProfile.PBAP_CLIENT)).thenReturn(connectionPolicy);

        assertThat(mService.connect(mRemoteDevice)).isFalse();
    }

    @Test
    public void testConnect_whenPolicyIsAllowed_returnsTrue() {
        int connectionPolicy = BluetoothProfile.CONNECTION_POLICY_ALLOWED;
        when(mDatabaseManager.getProfileConnectionPolicy(
                mRemoteDevice, BluetoothProfile.PBAP_CLIENT)).thenReturn(connectionPolicy);

        assertThat(mService.connect(mRemoteDevice)).isTrue();
    }

    @Test
    public void testDisconnect_withNullDevice_throwsIAE() {
        assertThrows(IllegalArgumentException.class, () -> mService.disconnect(null));
    }

    @Test
    public void testDisconnect_whenNotConnected_returnsFalse() {
        assertThat(mService.disconnect(mRemoteDevice)).isFalse();
    }

    @Test
    public void testDisconnect_whenConnected_returnsTrue() {
        PbapClientStateMachine sm = mock(PbapClientStateMachine.class);
        mService.mPbapClientStateMachineMap.put(mRemoteDevice, sm);

        assertThat(mService.disconnect(mRemoteDevice)).isTrue();

        verify(sm).disconnect(mRemoteDevice);
    }

    @Test
    public void testGetConnectionState_whenNotConnected() {
        assertThat(mService.getConnectionState(mRemoteDevice))
                .isEqualTo(BluetoothProfile.STATE_DISCONNECTED);
    }

    @Test
    public void cleanUpDevice() {
        PbapClientStateMachine sm = mock(PbapClientStateMachine.class);
        mService.mPbapClientStateMachineMap.put(mRemoteDevice, sm);

        mService.cleanupDevice(mRemoteDevice);

        assertThat(mService.mPbapClientStateMachineMap).doesNotContainKey(mRemoteDevice);
    }

    @Test
    public void getConnectedDevices() {
        int connectionState = BluetoothProfile.STATE_CONNECTED;
        PbapClientStateMachine sm = mock(PbapClientStateMachine.class);
        mService.mPbapClientStateMachineMap.put(mRemoteDevice, sm);
        when(sm.getConnectionState()).thenReturn(connectionState);

        assertThat(mService.getConnectedDevices()).contains(mRemoteDevice);
    }

    @Test
    public void binder_connect_callsServiceMethod() {
        PbapClientService mockService = mock(PbapClientService.class);
        PbapClientService.BluetoothPbapClientBinder binder =
                new PbapClientService.BluetoothPbapClientBinder(mockService);

        binder.connect(mRemoteDevice, null, SynchronousResultReceiver.get());

        verify(mockService).connect(mRemoteDevice);
    }

    @Test
    public void binder_disconnect_callsServiceMethod() {
        PbapClientService mockService = mock(PbapClientService.class);
        PbapClientService.BluetoothPbapClientBinder binder =
                new PbapClientService.BluetoothPbapClientBinder(mockService);

        binder.disconnect(mRemoteDevice, null, SynchronousResultReceiver.get());

        verify(mockService).disconnect(mRemoteDevice);
    }

    @Test
    public void binder_getConnectedDevices_callsServiceMethod() {
        PbapClientService mockService = mock(PbapClientService.class);
        PbapClientService.BluetoothPbapClientBinder binder =
                new PbapClientService.BluetoothPbapClientBinder(mockService);

        binder.getConnectedDevices(null, SynchronousResultReceiver.get());

        verify(mockService).getConnectedDevices();
    }

    @Test
    public void binder_getDevicesMatchingConnectionStates_callsServiceMethod() {
        PbapClientService mockService = mock(PbapClientService.class);
        PbapClientService.BluetoothPbapClientBinder binder =
                new PbapClientService.BluetoothPbapClientBinder(mockService);

        int[] states = new int[] {BluetoothProfile.STATE_CONNECTED};
        binder.getDevicesMatchingConnectionStates(states, null, SynchronousResultReceiver.get());

        verify(mockService).getDevicesMatchingConnectionStates(states);
    }

    @Test
    public void binder_getConnectionState_callsServiceMethod() {
        PbapClientService mockService = mock(PbapClientService.class);
        PbapClientService.BluetoothPbapClientBinder binder =
                new PbapClientService.BluetoothPbapClientBinder(mockService);

        binder.getConnectionState(mRemoteDevice, null, SynchronousResultReceiver.get());

        verify(mockService).getConnectionState(mRemoteDevice);
    }

    @Test
    public void binder_setConnectionPolicy_callsServiceMethod() {
        PbapClientService mockService = mock(PbapClientService.class);
        PbapClientService.BluetoothPbapClientBinder binder =
                new PbapClientService.BluetoothPbapClientBinder(mockService);

        int connectionPolicy = BluetoothProfile.CONNECTION_POLICY_ALLOWED;
        binder.setConnectionPolicy(mRemoteDevice, connectionPolicy,
                null, SynchronousResultReceiver.get());

        verify(mockService).setConnectionPolicy(mRemoteDevice, connectionPolicy);
    }

    @Test
    public void binder_getConnectionPolicy_callsServiceMethod() {
        PbapClientService mockService = mock(PbapClientService.class);
        PbapClientService.BluetoothPbapClientBinder binder =
                new PbapClientService.BluetoothPbapClientBinder(mockService);

        binder.getConnectionPolicy(mRemoteDevice, null, SynchronousResultReceiver.get());

        verify(mockService).getConnectionPolicy(mRemoteDevice);
    }

    @Test
    public void binder_cleanUp_doesNotCrash() {
        PbapClientService mockService = mock(PbapClientService.class);
        PbapClientService.BluetoothPbapClientBinder binder =
                new PbapClientService.BluetoothPbapClientBinder(mockService);

        binder.cleanup();
    }

    @Test
    public void broadcastReceiver_withActionAclDisconnectedLeTransport_doesNotCallDisconnect() {
        int connectionState = BluetoothProfile.STATE_CONNECTED;
        PbapClientStateMachine sm = mock(PbapClientStateMachine.class);
        mService.mPbapClientStateMachineMap.put(mRemoteDevice, sm);
        when(sm.getConnectionState(mRemoteDevice)).thenReturn(connectionState);

        mService.aclDisconnected(mRemoteDevice, BluetoothDevice.TRANSPORT_LE);
        TestUtils.waitForLooperToFinishScheduledTask(Looper.getMainLooper());

        verify(sm, never()).disconnect(mRemoteDevice);
    }

    @Test
    public void broadcastReceiver_withActionAclDisconnectedBrEdrTransport_callsDisconnect() {
        int connectionState = BluetoothProfile.STATE_CONNECTED;
        PbapClientStateMachine sm = mock(PbapClientStateMachine.class);
        mService.mPbapClientStateMachineMap.put(mRemoteDevice, sm);
        when(sm.getConnectionState(mRemoteDevice)).thenReturn(connectionState);

        mService.aclDisconnected(mRemoteDevice, BluetoothDevice.TRANSPORT_BREDR);
        TestUtils.waitForLooperToFinishScheduledTask(Looper.getMainLooper());

        verify(sm).disconnect(mRemoteDevice);
    }

    @Test
    public void broadcastReceiver_withActionUserUnlocked_callsTryDownloadIfConnected() {
        PbapClientStateMachine sm = mock(PbapClientStateMachine.class);
        mService.mPbapClientStateMachineMap.put(mRemoteDevice, sm);

        Intent intent = new Intent(Intent.ACTION_USER_UNLOCKED);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, mRemoteDevice);
        mService.mPbapBroadcastReceiver.onReceive(mService, intent);

        verify(sm).tryDownloadIfConnected();
    }

    @Test
    public void headsetClientConnectionStateChanged_hfpCallLogIsRemoved() {
        BluetoothMethodProxy methodProxy = spy(BluetoothMethodProxy.getInstance());
        BluetoothMethodProxy.setInstanceForTesting(methodProxy);

        mService.handleHeadsetClientConnectionStateChanged(
                mRemoteDevice,
                BluetoothProfile.STATE_CONNECTED,
                BluetoothProfile.STATE_DISCONNECTED);

        ArgumentCaptor<Object> selectionArgsCaptor = ArgumentCaptor.forClass(Object.class);
        verify(methodProxy).contentResolverDelete(any(), eq(CallLog.Calls.CONTENT_URI), any(),
                (String[]) selectionArgsCaptor.capture());

        assertThat(((String[]) selectionArgsCaptor.getValue())[0])
                .isEqualTo(mRemoteDevice.getAddress());
    }
}
