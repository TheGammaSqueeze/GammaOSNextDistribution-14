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

package com.android.bluetooth.hid;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHidDeviceAppQosSettings;
import android.bluetooth.BluetoothHidDeviceAppSdpSettings;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.IBluetoothHidDeviceCallback;
import android.content.AttributionSource;

import com.android.bluetooth.x.com.android.modules.utils.SynchronousResultReceiver;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class BluetoothHidDeviceBinderTest {

    private static final String TEST_DEVICE_ADDRESS = "00:00:00:00:00:00";

    @Mock
    private HidDeviceService mService;
    private AttributionSource mAttributionSource;
    private BluetoothDevice mTestDevice;
    private HidDeviceService.BluetoothHidDeviceBinder mBinder;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mService.isAvailable()).thenReturn(true);
        mBinder = new HidDeviceService.BluetoothHidDeviceBinder(mService);
        mAttributionSource = new AttributionSource.Builder(1).build();
        mTestDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(TEST_DEVICE_ADDRESS);
    }

    @Test
    public void cleanup() {
        mBinder.cleanup();
        assertThat(mBinder.getServiceForTesting()).isNull();
    }

    @Test
    public void registerApp() {
        String name = "test-name";
        String description = "test-description";
        String provider = "test-provider";
        byte subclass = 1;
        byte[] descriptors = new byte[] {10};
        BluetoothHidDeviceAppSdpSettings sdp = new BluetoothHidDeviceAppSdpSettings(
                name, description, provider, subclass, descriptors);

        int tokenRate = 800;
        int tokenBucketSize = 9;
        int peakBandwidth = 10;
        int latency = 11250;
        int delayVariation = BluetoothHidDeviceAppQosSettings.MAX;
        BluetoothHidDeviceAppQosSettings inQos = new BluetoothHidDeviceAppQosSettings(
                BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT, tokenRate,
                tokenBucketSize, peakBandwidth, latency, delayVariation);
        BluetoothHidDeviceAppQosSettings outQos = new BluetoothHidDeviceAppQosSettings(
                BluetoothHidDeviceAppQosSettings.SERVICE_BEST_EFFORT, tokenRate,
                tokenBucketSize, peakBandwidth, latency, delayVariation);
        IBluetoothHidDeviceCallback cb = mock(IBluetoothHidDeviceCallback.class);

        mBinder.registerApp(sdp, inQos, outQos, cb, mAttributionSource,
                SynchronousResultReceiver.get());
        verify(mService).registerApp(sdp, inQos, outQos, cb);
    }

    @Test
    public void unregisterApp() {
        mBinder.unregisterApp(mAttributionSource, SynchronousResultReceiver.get());
        verify(mService).unregisterApp();
    }

    @Test
    public void sendReport() {
        int id = 100;
        byte[] data = new byte[] { 0x00,  0x01 };
        mBinder.sendReport(mTestDevice, id, data, mAttributionSource,
                SynchronousResultReceiver.get());
        verify(mService).sendReport(mTestDevice, id, data);
    }

    @Test
    public void replyReport() {
        byte type = 0;
        byte id = 100;
        byte[] data = new byte[] { 0x00,  0x01 };
        mBinder.replyReport(mTestDevice, type, id, data, mAttributionSource,
                SynchronousResultReceiver.get());
        verify(mService).replyReport(mTestDevice, type, id, data);
    }

    @Test
    public void unplug() {
        mBinder.unplug(mTestDevice, mAttributionSource, SynchronousResultReceiver.get());
        verify(mService).unplug(mTestDevice);
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
    public void setConnectionPolicy() {
        int connectionPolicy = BluetoothProfile.CONNECTION_POLICY_ALLOWED;
        mBinder.setConnectionPolicy(mTestDevice, connectionPolicy, mAttributionSource,
                SynchronousResultReceiver.get());
        verify(mService).setConnectionPolicy(mTestDevice, connectionPolicy);
    }

    @Test
    public void reportError() {
        byte error = -1;
        mBinder.reportError(mTestDevice, error, mAttributionSource,
                SynchronousResultReceiver.get());
        verify(mService).reportError(mTestDevice, error);
    }

    @Test
    public void getConnectionState() {
        mBinder.getConnectionState(mTestDevice, mAttributionSource,
                SynchronousResultReceiver.get());
        verify(mService).getConnectionState(mTestDevice);
    }

    @Test
    public void getConnectedDevices() {
        mBinder.getConnectedDevices(mAttributionSource, SynchronousResultReceiver.get());
        verify(mService).getDevicesMatchingConnectionStates(any(int[].class));
    }

    @Test
    public void getDevicesMatchingConnectionStates() {
        int[] states = new int[] { BluetoothProfile.STATE_CONNECTED };
        mBinder.getDevicesMatchingConnectionStates(states, mAttributionSource,
                SynchronousResultReceiver.get());
        verify(mService).getDevicesMatchingConnectionStates(states);
    }

    @Test
    public void getUserAppName() {
        mBinder.getUserAppName(mAttributionSource, SynchronousResultReceiver.get());
        verify(mService).getUserAppName();
    }
}
