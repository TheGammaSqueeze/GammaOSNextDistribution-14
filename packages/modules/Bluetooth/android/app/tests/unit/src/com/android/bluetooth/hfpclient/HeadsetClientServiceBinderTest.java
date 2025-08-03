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

package com.android.bluetooth.hfpclient;

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
public class HeadsetClientServiceBinderTest {
    private static final String REMOTE_DEVICE_ADDRESS = "00:00:00:00:00:00";

    @Mock
    private HeadsetClientService mService;

    BluetoothDevice mRemoteDevice;

    HeadsetClientService.BluetoothHeadsetClientBinder mBinder;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mRemoteDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(REMOTE_DEVICE_ADDRESS);
        mBinder = new HeadsetClientService.BluetoothHeadsetClientBinder(mService);
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
    public void startVoiceRecognition_callsServiceMethod() {
        mBinder.startVoiceRecognition(mRemoteDevice, null, SynchronousResultReceiver.get());

        verify(mService).startVoiceRecognition(mRemoteDevice);
    }

    @Test
    public void stopVoiceRecognition_callsServiceMethod() {
        mBinder.stopVoiceRecognition(mRemoteDevice, null, SynchronousResultReceiver.get());

        verify(mService).stopVoiceRecognition(mRemoteDevice);
    }

    @Test
    public void getAudioState_callsServiceMethod() {
        mBinder.getAudioState(mRemoteDevice, null, SynchronousResultReceiver.get());

        verify(mService).getAudioState(mRemoteDevice);
    }

    @Test
    public void setAudioRouteAllowed_callsServiceMethod() {
        boolean allowed = true;
        mBinder.setAudioRouteAllowed(mRemoteDevice, allowed, null,
                SynchronousResultReceiver.get());

        verify(mService).setAudioRouteAllowed(mRemoteDevice, allowed);
    }

    @Test
    public void getAudioRouteAllowed_callsServiceMethod() {
        boolean allowed = true;
        mBinder.getAudioRouteAllowed(mRemoteDevice, null,
                SynchronousResultReceiver.get());

        verify(mService).getAudioRouteAllowed(mRemoteDevice);
    }

    @Test
    public void connectAudio_callsServiceMethod() {
        mBinder.connectAudio(mRemoteDevice, null, SynchronousResultReceiver.get());

        verify(mService).connectAudio(mRemoteDevice);
    }

    @Test
    public void disconnectAudio_callsServiceMethod() {
        mBinder.disconnectAudio(mRemoteDevice, null, SynchronousResultReceiver.get());

        verify(mService).disconnectAudio(mRemoteDevice);
    }

    @Test
    public void acceptCall_callsServiceMethod() {
        int flag = 2;
        mBinder.acceptCall(mRemoteDevice, flag, null, SynchronousResultReceiver.get());

        verify(mService).acceptCall(mRemoteDevice, flag);
    }

    @Test
    public void rejectCall_callsServiceMethod() {
        mBinder.rejectCall(mRemoteDevice, null, SynchronousResultReceiver.get());

        verify(mService).rejectCall(mRemoteDevice);
    }

    @Test
    public void holdCall_callsServiceMethod() {
        mBinder.holdCall(mRemoteDevice, null, SynchronousResultReceiver.get());

        verify(mService).holdCall(mRemoteDevice);
    }

    @Test
    public void terminateCall_callsServiceMethod() {
        mBinder.terminateCall(mRemoteDevice, null, null, SynchronousResultReceiver.get());

        verify(mService).terminateCall(mRemoteDevice, null);
    }

    @Test
    public void explicitCallTransfer_callsServiceMethod() {
        mBinder.explicitCallTransfer(mRemoteDevice, null, SynchronousResultReceiver.get());

        verify(mService).explicitCallTransfer(mRemoteDevice);
    }

    @Test
    public void enterPrivateMode_callsServiceMethod() {
        int index = 1;
        mBinder.enterPrivateMode(mRemoteDevice, index, null,
                SynchronousResultReceiver.get());

        verify(mService).enterPrivateMode(mRemoteDevice, index);
    }

    @Test
    public void dial_callsServiceMethod() {
        String number = "12532523";
        mBinder.dial(mRemoteDevice, number, null,
                SynchronousResultReceiver.get());

        verify(mService).dial(mRemoteDevice, number);
    }

    @Test
    public void getCurrentCalls_callsServiceMethod() {
        mBinder.getCurrentCalls(mRemoteDevice, null, SynchronousResultReceiver.get());

        verify(mService).getCurrentCalls(mRemoteDevice);
    }

    @Test
    public void sendDTMF_callsServiceMethod() {
        byte code = 21;
        mBinder.sendDTMF(mRemoteDevice, code, null, SynchronousResultReceiver.get());

        verify(mService).sendDTMF(mRemoteDevice, code);
    }

    @Test
    public void getLastVoiceTagNumber_callsServiceMethod() {
        mBinder.getLastVoiceTagNumber(mRemoteDevice, null, SynchronousResultReceiver.get());

        verify(mService).getLastVoiceTagNumber(mRemoteDevice);
    }

    @Test
    public void getCurrentAgEvents_callsServiceMethod() {
        mBinder.getCurrentAgEvents(mRemoteDevice, null, SynchronousResultReceiver.get());

        verify(mService).getCurrentAgEvents(mRemoteDevice);
    }

    @Test
    public void sendVendorAtCommand_callsServiceMethod() {
        int vendorId = 5;
        String cmd = "test_command";

        mBinder.sendVendorAtCommand(mRemoteDevice, vendorId, cmd,
                null, SynchronousResultReceiver.get());

        verify(mService).sendVendorAtCommand(mRemoteDevice, vendorId, cmd);
    }

    @Test
    public void getCurrentAgFeatures_callsServiceMethod() {
        mBinder.getCurrentAgFeatures(mRemoteDevice, null, SynchronousResultReceiver.get());

        verify(mService).getCurrentAgFeaturesBundle(mRemoteDevice);
    }

    @Test
    public void cleanUp_doesNotCrash() {
        mBinder.cleanup();
    }
}
