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

package com.android.bluetooth.avrcpcontroller;

import static org.mockito.Mockito.verify;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.x.com.android.modules.utils.SynchronousResultReceiver;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class AvrcpControllerServiceBinderTest {
    private static final String REMOTE_DEVICE_ADDRESS = "00:00:00:00:00:00";

    @Mock
    private AvrcpControllerService mService;

    BluetoothDevice mRemoteDevice;

    AvrcpControllerService.AvrcpControllerServiceBinder mBinder;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mRemoteDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(REMOTE_DEVICE_ADDRESS);
        mBinder = new AvrcpControllerService.AvrcpControllerServiceBinder(mService);
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
    public void sendGroupNavigationCmd_notImplemented_doesNothing() {
        mBinder.sendGroupNavigationCmd(mRemoteDevice, 1, 2,
                null, SynchronousResultReceiver.get());
    }

    @Test
    public void getPlayerSettings_notImplemented_doesNothing() {
        mBinder.getPlayerSettings(mRemoteDevice, null, SynchronousResultReceiver.get());
    }

    @Test
    public void cleanUp_doesNotCrash() {
        mBinder.cleanup();
    }
}
