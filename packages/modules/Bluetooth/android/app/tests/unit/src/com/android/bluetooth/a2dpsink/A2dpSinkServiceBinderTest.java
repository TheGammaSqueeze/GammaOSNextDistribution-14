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

package com.android.bluetooth.a2dpsink;

import static org.mockito.Mockito.verify;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothAudioConfig;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.AttributionSource;

import com.android.bluetooth.TestUtils;
import com.android.bluetooth.x.com.android.modules.utils.SynchronousResultReceiver;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

public class A2dpSinkServiceBinderTest {
    @Mock private A2dpSinkService mService;
    private A2dpSinkService.A2dpSinkServiceBinder mBinder;
    private BluetoothAdapter mAdapter;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        mBinder = new A2dpSinkService.A2dpSinkServiceBinder(mService);
    }

    @After
    public void cleaUp() {
        mBinder.cleanup();
    }

    @Test
    public void connect() {
        BluetoothDevice device = TestUtils.getTestDevice(mAdapter, 0);
        AttributionSource source = new AttributionSource.Builder(0).build();
        final SynchronousResultReceiver<Boolean> recv = SynchronousResultReceiver.get();

        mBinder.connect(device, source, recv);
        verify(mService).connect(device);
    }

    @Test
    public void disconnect() {
        BluetoothDevice device = TestUtils.getTestDevice(mAdapter, 0);
        AttributionSource source = new AttributionSource.Builder(0).build();
        final SynchronousResultReceiver<Boolean> recv = SynchronousResultReceiver.get();

        mBinder.disconnect(device, source, recv);
        verify(mService).disconnect(device);
    }

    @Test
    public void getConnectedDevices() {
        AttributionSource source = new AttributionSource.Builder(0).build();
        final SynchronousResultReceiver<List<BluetoothDevice>> recv =
                SynchronousResultReceiver.get();
        mBinder.getConnectedDevices(source, recv);
        verify(mService).getConnectedDevices();
    }

    @Test
    public void getDevicesMatchingConnectionStates() {
        int[] states = new int[] {BluetoothProfile.STATE_CONNECTED };
        AttributionSource source = new AttributionSource.Builder(0).build();
        final SynchronousResultReceiver<List<BluetoothDevice>> recv =
                SynchronousResultReceiver.get();

        mBinder.getDevicesMatchingConnectionStates(states, source, recv);
        verify(mService).getDevicesMatchingConnectionStates(states);
    }

    @Test
    public void getConnectionState() {
        BluetoothDevice device = TestUtils.getTestDevice(mAdapter, 0);
        AttributionSource source = new AttributionSource.Builder(0).build();
        final SynchronousResultReceiver<List<BluetoothDevice>> recv =
                SynchronousResultReceiver.get();

        mBinder.getConnectionState(device, source, recv);
        verify(mService).getConnectionState(device);
    }

    @Test
    public void setConnectionPolicy() {
        BluetoothDevice device = TestUtils.getTestDevice(mAdapter, 0);
        int connectionPolicy = BluetoothProfile.CONNECTION_POLICY_ALLOWED;
        AttributionSource source = new AttributionSource.Builder(0).build();
        final SynchronousResultReceiver<Boolean> recv = SynchronousResultReceiver.get();

        mBinder.setConnectionPolicy(device, connectionPolicy, source, recv);
        verify(mService).setConnectionPolicy(device, connectionPolicy);
    }

    @Test
    public void getConnectionPolicy() {
        BluetoothDevice device = TestUtils.getTestDevice(mAdapter, 0);
        AttributionSource source = new AttributionSource.Builder(0).build();
        final SynchronousResultReceiver<Integer> recv = SynchronousResultReceiver.get();

        mBinder.getConnectionPolicy(device, source, recv);
        verify(mService).getConnectionPolicy(device);
    }

    @Test
    public void isA2dpPlaying() {
        BluetoothDevice device = TestUtils.getTestDevice(mAdapter, 0);
        AttributionSource source = new AttributionSource.Builder(0).build();
        final SynchronousResultReceiver<Boolean> recv = SynchronousResultReceiver.get();

        mBinder.isA2dpPlaying(device, source, recv);
        verify(mService).isA2dpPlaying(device);
    }

    @Test
    public void getAudioConfig() {
        BluetoothDevice device = TestUtils.getTestDevice(mAdapter, 0);
        AttributionSource source = new AttributionSource.Builder(0).build();
        final SynchronousResultReceiver<BluetoothAudioConfig> recv =
                SynchronousResultReceiver.get();

        mBinder.getAudioConfig(device, source, recv);
        verify(mService).getAudioConfig(device);
    }
}
