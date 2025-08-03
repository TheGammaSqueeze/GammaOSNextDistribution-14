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
package com.android.bluetooth.mapclient;


import static org.mockito.Mockito.verify;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.net.Uri;

import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.x.com.android.modules.utils.SynchronousResultReceiver;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class MapClientServiceBinderTest {
    private static final String REMOTE_DEVICE_ADDRESS = "00:00:00:00:00:00";

    @Mock
    private MapClientService mService;

    BluetoothDevice mRemoteDevice;

    MapClientService.Binder mBinder;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mRemoteDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(REMOTE_DEVICE_ADDRESS);
        mBinder = new MapClientService.Binder(mService);
    }

    @Test
    public void connect_callsServiceMethod() {
        mBinder.connect(mRemoteDevice, null, SynchronousResultReceiver.get());

        verify(mService).connect(mRemoteDevice);
    }

    @Test
    public void disconnect_callsServiceMethod() {
        mBinder.disconnect(mRemoteDevice, null, SynchronousResultReceiver.get());

        verify(mService).disconnect(mRemoteDevice);
    }

    @Test
    public void getConnectedDevices_callsServiceMethod() {
        mBinder.getConnectedDevices(null, SynchronousResultReceiver.get());

        verify(mService).getConnectedDevices();
    }

    @Test
    public void getDevicesMatchingConnectionStates_callsServiceMethod() {
        int[] states = new int[] {BluetoothProfile.STATE_CONNECTED};
        mBinder.getDevicesMatchingConnectionStates(states, null, SynchronousResultReceiver.get());

        verify(mService).getDevicesMatchingConnectionStates(states);
    }

    @Test
    public void getConnectionState_callsServiceMethod() {
        mBinder.getConnectionState(mRemoteDevice, null, SynchronousResultReceiver.get());

        verify(mService).getConnectionState(mRemoteDevice);
    }

    @Test
    public void setConnectionPolicy_callsServiceMethod() {
        int connectionPolicy = BluetoothProfile.CONNECTION_POLICY_ALLOWED;
        mBinder.setConnectionPolicy(mRemoteDevice, connectionPolicy,
                null, SynchronousResultReceiver.get());

        verify(mService).setConnectionPolicy(mRemoteDevice, connectionPolicy);
    }

    @Test
    public void getConnectionPolicy_callsServiceMethod() {
        mBinder.getConnectionPolicy(mRemoteDevice, null, SynchronousResultReceiver.get());

        verify(mService).getConnectionPolicy(mRemoteDevice);
    }

    @Test
    public void sendMessage_callsServiceMethod() {
        Uri[] contacts = new Uri[] {};
        String message = "test_message";
        mBinder.sendMessage(mRemoteDevice, contacts, message, null, null, null,
                SynchronousResultReceiver.get());

        verify(mService).sendMessage(mRemoteDevice, contacts, message, null, null);
    }

    @Test
    public void getUnreadMessages_callsServiceMethod() {
        mBinder.getUnreadMessages(mRemoteDevice, null, SynchronousResultReceiver.get());

        verify(mService).getUnreadMessages(mRemoteDevice);
    }

    @Test
    public void getSupportedFeatures_callsServiceMethod() {
        mBinder.getSupportedFeatures(mRemoteDevice, null, SynchronousResultReceiver.get());

        verify(mService).getSupportedFeatures(mRemoteDevice);
    }

    @Test
    public void setMessageStatus_callsServiceMethod() {
        String handle = "FFAB";
        int status = 1234;
        mBinder.setMessageStatus(mRemoteDevice, handle, status, null,
                SynchronousResultReceiver.get());

        verify(mService).setMessageStatus(mRemoteDevice, handle, status);
    }

    @Test
    public void cleanUp_doesNotCrash() {
        mBinder.cleanup();
    }
}