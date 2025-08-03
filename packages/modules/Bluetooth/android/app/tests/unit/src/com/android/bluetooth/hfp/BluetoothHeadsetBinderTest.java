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

package com.android.bluetooth.hfp;

import static org.mockito.Mockito.verify;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.AttributionSource;

import com.android.bluetooth.x.com.android.modules.utils.SynchronousResultReceiver;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class BluetoothHeadsetBinderTest {
    private static final String TEST_DEVICE_ADDRESS = "00:00:00:00:00:00";

    @Mock
    private HeadsetService mService;

    private AttributionSource mAttributionSource;
    private BluetoothDevice mTestDevice;

    private HeadsetService.BluetoothHeadsetBinder mBinder;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mBinder = new HeadsetService.BluetoothHeadsetBinder(mService);
        mAttributionSource = new AttributionSource.Builder(1).build();
        mTestDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(TEST_DEVICE_ADDRESS);
    }

    @Test
    public void connect() {
        mBinder.connect(mTestDevice, mAttributionSource, SynchronousResultReceiver.get());
        verify(mService).connect(mTestDevice);
    }

    @Test
    public void disconnect() {
        mBinder.disconnect(mTestDevice, mAttributionSource, SynchronousResultReceiver.get());
        verify(mService).disconnect(mTestDevice);
    }

    @Test
    public void getConnectedDevices() {
        mBinder.getConnectedDevices(mAttributionSource, SynchronousResultReceiver.get());
        verify(mService).getConnectedDevices();
    }

    @Test
    public void getDevicesMatchingConnectionStates() {
        int[] states = new int[] { BluetoothProfile.STATE_CONNECTED };
        mBinder.getDevicesMatchingConnectionStates(states, mAttributionSource,
                SynchronousResultReceiver.get());
        verify(mService).getDevicesMatchingConnectionStates(states);
    }

    @Test
    public void getConnectionState() {
        mBinder.getConnectionState(mTestDevice, mAttributionSource,
                SynchronousResultReceiver.get());
        verify(mService).getConnectionState(mTestDevice);
    }

    @Test
    public void setConnectionPolicy() {
        int connectionPolicy = BluetoothProfile.CONNECTION_POLICY_ALLOWED;
        mBinder.setConnectionPolicy(mTestDevice, connectionPolicy, mAttributionSource,
                SynchronousResultReceiver.get());
        verify(mService).setConnectionPolicy(mTestDevice, connectionPolicy);
    }

    @Test
    public void getConnectionPolicy() {
        mBinder.getConnectionPolicy(mTestDevice, mAttributionSource,
                SynchronousResultReceiver.get());
        verify(mService).getConnectionPolicy(mTestDevice);
    }

    @Test
    public void isNoiseReductionSupported() {
        mBinder.isNoiseReductionSupported(mTestDevice, mAttributionSource,
                SynchronousResultReceiver.get());
        verify(mService).isNoiseReductionSupported(mTestDevice);
    }

    @Test
    public void isVoiceRecognitionSupported() {
        mBinder.isVoiceRecognitionSupported(mTestDevice, mAttributionSource,
                SynchronousResultReceiver.get());
        verify(mService).isVoiceRecognitionSupported(mTestDevice);
    }

    @Test
    public void startVoiceRecognition() {
        mBinder.startVoiceRecognition(mTestDevice, mAttributionSource,
                SynchronousResultReceiver.get());
        verify(mService).startVoiceRecognition(mTestDevice);
    }

    @Test
    public void stopVoiceRecognition() {
        mBinder.stopVoiceRecognition(mTestDevice, mAttributionSource,
                SynchronousResultReceiver.get());
        verify(mService).stopVoiceRecognition(mTestDevice);
    }

    @Test
    public void isAudioOn() {
        mBinder.isAudioOn(mAttributionSource, SynchronousResultReceiver.get());
        verify(mService).isAudioOn();
    }

    @Test
    public void isAudioConnected() {
        mBinder.isAudioConnected(mTestDevice, mAttributionSource, SynchronousResultReceiver.get());
        verify(mService).isAudioConnected(mTestDevice);
    }

    @Test
    public void getAudioState() {
        mBinder.getAudioState(mTestDevice, mAttributionSource, SynchronousResultReceiver.get());
        verify(mService).getAudioState(mTestDevice);
    }

    @Test
    public void connectAudio() {
        mBinder.connectAudio(mAttributionSource, SynchronousResultReceiver.get());
        verify(mService).connectAudio();
    }

    @Test
    public void disconnectAudio() {
        mBinder.disconnectAudio(mAttributionSource, SynchronousResultReceiver.get());
        verify(mService).disconnectAudio();
    }

    @Test
    public void setAudioRouteAllowed() {
        boolean allowed = true;
        mBinder.setAudioRouteAllowed(allowed, mAttributionSource, SynchronousResultReceiver.get());
        verify(mService).setAudioRouteAllowed(allowed);
    }

    @Test
    public void getAudioRouteAllowed() {
        mBinder.getAudioRouteAllowed(mAttributionSource, SynchronousResultReceiver.get());
        verify(mService).getAudioRouteAllowed();
    }

    @Test
    public void setForceScoAudio() {
        boolean forced = true;
        mBinder.setForceScoAudio(forced, mAttributionSource, SynchronousResultReceiver.get());
        verify(mService).setForceScoAudio(forced);
    }

    @Test
    public void startScoUsingVirtualVoiceCall() {
        mBinder.startScoUsingVirtualVoiceCall(mAttributionSource, SynchronousResultReceiver.get());
        verify(mService).startScoUsingVirtualVoiceCall();
    }

    @Test
    public void stopScoUsingVirtualVoiceCall() {
        mBinder.stopScoUsingVirtualVoiceCall(mAttributionSource, SynchronousResultReceiver.get());
        verify(mService).stopScoUsingVirtualVoiceCall();
    }

    @Test
    public void phoneStateChanged() {
        int numActive = 2;
        int numHeld = 5;
        int callState = HeadsetHalConstants.CALL_STATE_IDLE;
        String number = "000-000-0000";
        int type = 0;
        String name = "Unknown";
        mBinder.phoneStateChanged(
                numActive, numHeld, callState, number, type, name, mAttributionSource);
        verify(mService).phoneStateChanged(
                numActive, numHeld, callState, number, type, name, false);
    }
}
