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
package com.android.bluetooth.pbap;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Message;
import android.os.test.TestLooper;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.BluetoothMethodProxy;
import com.android.bluetooth.TestUtils;
import com.android.bluetooth.btservice.AdapterService;
import com.android.bluetooth.btservice.storage.DatabaseManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class BluetoothPbapServiceTest {
    private static final String REMOTE_DEVICE_ADDRESS = "00:00:00:00:00:00";

    private BluetoothPbapService mService;
    private BluetoothAdapter mAdapter = null;
    private BluetoothDevice mRemoteDevice;
    private boolean mIsAdapterServiceSet;
    private boolean mIsBluetoothPabpServiceStarted;
    private TestLooper mTestLooper;


    @Mock private AdapterService mAdapterService;
    @Mock private DatabaseManager mDatabaseManager;
    @Spy private BluetoothMethodProxy mMethodProxy = BluetoothMethodProxy.getInstance();

    @Before
    public void setUp() throws Exception {
        Context targetContext = InstrumentationRegistry.getTargetContext();
        MockitoAnnotations.initMocks(this);
        mTestLooper = new TestLooper();
        BluetoothMethodProxy.setInstanceForTesting(mMethodProxy);
        doReturn(mTestLooper.getLooper()).when(mMethodProxy).handlerThreadGetLooper(any());
        doNothing().when(mMethodProxy).threadStart(any());
        mTestLooper.startAutoDispatch();
        TestUtils.setAdapterService(mAdapterService);
        mIsAdapterServiceSet = true;
        doReturn(mDatabaseManager).when(mAdapterService).getDatabase();
        mService = new BluetoothPbapService(targetContext);
        mService.start();
        mService.setAvailable(true);
        mIsBluetoothPabpServiceStarted = true;
        // Try getting the Bluetooth adapter
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        assertThat(mAdapter).isNotNull();
        mRemoteDevice = mAdapter.getRemoteDevice(REMOTE_DEVICE_ADDRESS);
    }

    @After
    public void tearDown() throws Exception {
        mTestLooper.stopAutoDispatchAndIgnoreExceptions();
        BluetoothMethodProxy.setInstanceForTesting(null);
        if (!mIsAdapterServiceSet) {
            return;
        }
        if (mIsBluetoothPabpServiceStarted) {
            mService.stop();
            mService = BluetoothPbapService.getBluetoothPbapService();
            assertThat(mService).isNull();
        }
        TestUtils.clearAdapterService(mAdapterService);
    }

    @Test
    public void initialize() {
        assertThat(BluetoothPbapService.getBluetoothPbapService()).isNotNull();
    }

    @Test
    public void disconnect() {
        PbapStateMachine sm = mock(PbapStateMachine.class);
        mService.mPbapStateMachineMap.put(mRemoteDevice, sm);

        mService.disconnect(mRemoteDevice);

        verify(sm).sendMessage(PbapStateMachine.DISCONNECT);
    }

    @Test
    public void getConnectedDevices() {
        PbapStateMachine sm = mock(PbapStateMachine.class);
        mService.mPbapStateMachineMap.put(mRemoteDevice, sm);

        assertThat(mService.getConnectedDevices()).contains(mRemoteDevice);
    }

    @Test
    public void getConnectionPolicy_withDeviceIsNull_throwsNPE() {
        assertThrows(IllegalArgumentException.class, () -> mService.getConnectionPolicy(null));
    }

    @Test
    public void getConnectionPolicy() {
        mService.getConnectionPolicy(mRemoteDevice);

        verify(mDatabaseManager).getProfileConnectionPolicy(mRemoteDevice, BluetoothProfile.PBAP);
    }

    @Test
    public void getDevicesMatchingConnectionStates_whenStatesIsNull_returnsEmptyList() {
        assertThat(mService.getDevicesMatchingConnectionStates(null)).isEmpty();
    }

    @Test
    public void getDevicesMatchingConnectionStates() {
        PbapStateMachine sm = mock(PbapStateMachine.class);
        mService.mPbapStateMachineMap.put(mRemoteDevice, sm);
        when(sm.getConnectionState()).thenReturn(BluetoothProfile.STATE_CONNECTED);

        int[] states = new int[] {BluetoothProfile.STATE_CONNECTED};
        assertThat(mService.getDevicesMatchingConnectionStates(states)).contains(mRemoteDevice);
    }

    @Test
    public void onAcceptFailed() {
        mTestLooper.stopAutoDispatchAndIgnoreExceptions();
        PbapStateMachine sm = mock(PbapStateMachine.class);
        mService.mPbapStateMachineMap.put(mRemoteDevice, sm);

        mService.onAcceptFailed();

        assertThat(mService.mPbapStateMachineMap).isEmpty();
    }

    @Test
    public void broadcastReceiver_onReceive_withActionConnectionAccessReply() {
        Intent intent = new Intent(BluetoothDevice.ACTION_CONNECTION_ACCESS_REPLY);
        intent.putExtra(BluetoothDevice.EXTRA_ACCESS_REQUEST_TYPE,
                BluetoothDevice.REQUEST_TYPE_PHONEBOOK_ACCESS);
        intent.putExtra(BluetoothDevice.EXTRA_CONNECTION_ACCESS_RESULT,
                BluetoothDevice.CONNECTION_ACCESS_YES);
        intent.putExtra(BluetoothDevice.EXTRA_ALWAYS_ALLOWED, true);
        intent.putExtra(BluetoothDevice.EXTRA_DEVICE, mRemoteDevice);
        PbapStateMachine sm = mock(PbapStateMachine.class);
        mService.mPbapStateMachineMap.put(mRemoteDevice, sm);

        mService.mPbapReceiver.onReceive(null, intent);

        verify(sm).sendMessage(PbapStateMachine.AUTHORIZED);
    }

    @Test
    public void broadcastReceiver_onReceive_withActionAuthResponse() {
        Intent intent = new Intent(BluetoothPbapService.AUTH_RESPONSE_ACTION);
        String sessionKey = "test_session_key";
        intent.putExtra(BluetoothPbapService.EXTRA_SESSION_KEY, sessionKey);
        intent.putExtra(BluetoothPbapService.EXTRA_DEVICE, mRemoteDevice);
        PbapStateMachine sm = mock(PbapStateMachine.class);
        mService.mPbapStateMachineMap.put(mRemoteDevice, sm);

        mService.mPbapReceiver.onReceive(null, intent);

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(sm).sendMessage(captor.capture());
        Message msg = captor.getValue();
        assertThat(msg.what).isEqualTo(PbapStateMachine.AUTH_KEY_INPUT);
        assertThat(msg.obj).isEqualTo(sessionKey);
        msg.recycle();
    }

    @Test
    public void broadcastReceiver_onReceive_withActionAuthCancelled() {
        Intent intent = new Intent(BluetoothPbapService.AUTH_CANCELLED_ACTION);
        intent.putExtra(BluetoothPbapService.EXTRA_DEVICE, mRemoteDevice);
        PbapStateMachine sm = mock(PbapStateMachine.class);
        mService.mPbapStateMachineMap.put(mRemoteDevice, sm);

        mService.mPbapReceiver.onReceive(null, intent);

        verify(sm).sendMessage(PbapStateMachine.AUTH_CANCELLED);
    }

    @Test
    public void broadcastReceiver_onReceive_withIllegalAction_doesNothing() {
        Intent intent = new Intent("test_random_action");

        mService.mPbapReceiver.onReceive(null, intent);
    }
}
