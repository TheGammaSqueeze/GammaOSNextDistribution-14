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

package com.android.bluetooth.bass_client;

import static android.bluetooth.BluetoothProfile.STATE_DISCONNECTED;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.IBluetoothLeBroadcastAssistantCallback;
import android.bluetooth.le.ScanFilter;

import com.android.bluetooth.TestUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;

@RunWith(JUnit4.class)
public class BleBroadcastAssistantBinderTest {

    @Mock private BassClientService mService;

    private BassClientService.BluetoothLeBroadcastAssistantBinder mBinder;
    private BluetoothAdapter mAdapter;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mBinder = new BassClientService.BluetoothLeBroadcastAssistantBinder(mService);
        mAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    @Test
    public void cleanUp() {
        mBinder.cleanup();
        assertThat(mBinder.mService).isNull();
    }

    @Test
    public void getConnectionState() {
        BluetoothDevice device = TestUtils.getTestDevice(mAdapter, 0);
        mBinder.getConnectionState(device);
        verify(mService).getConnectionState(device);

        doThrow(new RuntimeException()).when(mService).getConnectionState(device);
        assertThat(mBinder.getConnectionState(device)).isEqualTo(STATE_DISCONNECTED);

        mBinder.cleanup();
        assertThat(mBinder.getConnectionState(device)).isEqualTo(STATE_DISCONNECTED);
    }

    @Test
    public void getDevicesMatchingConnectionStates() {
        int[] states = new int[] { STATE_DISCONNECTED };
        mBinder.getDevicesMatchingConnectionStates(states);
        verify(mService).getDevicesMatchingConnectionStates(states);

        doThrow(new RuntimeException()).when(mService).getDevicesMatchingConnectionStates(states);
        assertThat(mBinder.getDevicesMatchingConnectionStates(states)).isEqualTo(
                Collections.emptyList());

        mBinder.cleanup();
        assertThat(mBinder.getDevicesMatchingConnectionStates(states)).isEqualTo(
                Collections.emptyList());
    }

    @Test
    public void getConnectedDevices() {
        mBinder.getConnectedDevices();
        verify(mService).getConnectedDevices();

        doThrow(new RuntimeException()).when(mService).getConnectedDevices();
        assertThat(mBinder.getConnectedDevices()).isEqualTo(Collections.emptyList());

        mBinder.cleanup();
        assertThat(mBinder.getConnectedDevices()).isEqualTo(Collections.emptyList());
    }

    @Test
    public void setConnectionPolicy() {
        BluetoothDevice device = TestUtils.getTestDevice(mAdapter, 0);
        mBinder.setConnectionPolicy(device, BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        verify(mService).setConnectionPolicy(device, BluetoothProfile.CONNECTION_POLICY_ALLOWED);

        doThrow(new RuntimeException()).when(mService)
                .setConnectionPolicy(device, BluetoothProfile.CONNECTION_POLICY_ALLOWED);
        assertThat(mBinder.setConnectionPolicy(device, BluetoothProfile.CONNECTION_POLICY_ALLOWED))
                .isFalse();

        mBinder.cleanup();
        assertThat(mBinder.setConnectionPolicy(device, BluetoothProfile.CONNECTION_POLICY_ALLOWED))
                .isFalse();
    }

    @Test
    public void getConnectionPolicy() {
        BluetoothDevice device = TestUtils.getTestDevice(mAdapter, 0);
        mBinder.getConnectionPolicy(device);
        verify(mService).getConnectionPolicy(device);

        doThrow(new RuntimeException()).when(mService).getConnectionPolicy(device);
        assertThat(mBinder.getConnectionPolicy(device))
                .isEqualTo(BluetoothProfile.CONNECTION_POLICY_FORBIDDEN);

        mBinder.cleanup();
        assertThat(mBinder.getConnectionPolicy(device))
                .isEqualTo(BluetoothProfile.CONNECTION_POLICY_FORBIDDEN);
    }

    @Test
    public void registerCallback() {
        IBluetoothLeBroadcastAssistantCallback cb =
                Mockito.mock(IBluetoothLeBroadcastAssistantCallback.class);
        mBinder.registerCallback(cb);
        verify(mService).registerCallback(cb);

        Mockito.clearInvocations(mService);
        doThrow(new RuntimeException()).when(mService).enforceCallingOrSelfPermission(any(), any());
        mBinder.registerCallback(cb);
        verify(mService, never()).registerCallback(cb);

        mBinder.cleanup();
        mBinder.registerCallback(cb);
        verify(mService, never()).registerCallback(cb);
    }

    @Test
    public void unregisterCallback() {
        IBluetoothLeBroadcastAssistantCallback cb =
                Mockito.mock(IBluetoothLeBroadcastAssistantCallback.class);
        mBinder.unregisterCallback(cb);
        verify(mService).unregisterCallback(cb);

        Mockito.clearInvocations(mService);
        doThrow(new RuntimeException()).when(mService).enforceCallingOrSelfPermission(any(), any());
        mBinder.unregisterCallback(cb);
        verify(mService, never()).unregisterCallback(cb);

        mBinder.cleanup();
        mBinder.unregisterCallback(cb);
        verify(mService, never()).unregisterCallback(cb);
    }

    @Test
    public void startSearchingForSources() {
        List<ScanFilter> filters =  Collections.EMPTY_LIST;
        mBinder.startSearchingForSources(filters);
        verify(mService).startSearchingForSources(filters);

        Mockito.clearInvocations(mService);
        doThrow(new RuntimeException()).when(mService).enforceCallingOrSelfPermission(any(), any());
        mBinder.startSearchingForSources(filters);
        verify(mService, never()).startSearchingForSources(filters);

        mBinder.cleanup();
        mBinder.startSearchingForSources(filters);
        verify(mService, never()).startSearchingForSources(filters);
    }

    @Test
    public void stopSearchingForSources() {
        mBinder.stopSearchingForSources();
        verify(mService).stopSearchingForSources();

        Mockito.clearInvocations(mService);
        doThrow(new RuntimeException()).when(mService).enforceCallingOrSelfPermission(any(), any());
        mBinder.stopSearchingForSources();
        verify(mService, never()).stopSearchingForSources();

        mBinder.cleanup();
        mBinder.stopSearchingForSources();
        verify(mService, never()).stopSearchingForSources();
    }

    @Test
    public void isSearchInProgress() {
        mBinder.isSearchInProgress();
        verify(mService).isSearchInProgress();

        doThrow(new RuntimeException()).when(mService).enforceCallingOrSelfPermission(any(), any());
        assertThat(mBinder.isSearchInProgress()).isFalse();

        mBinder.cleanup();
        assertThat(mBinder.isSearchInProgress()).isFalse();
    }

    @Test
    public void addSource() {
        BluetoothDevice device = TestUtils.getTestDevice(mAdapter, 0);
        mBinder.addSource(device, null, false);
        verify(mService).addSource(device, null, false);

        Mockito.clearInvocations(mService);
        doThrow(new RuntimeException()).when(mService).enforceCallingOrSelfPermission(any(), any());
        mBinder.addSource(device, null, false);
        verify(mService, never()).addSource(device, null, false);

        mBinder.cleanup();
        mBinder.addSource(device, null, false);
        verify(mService, never()).addSource(device, null, false);
    }

    @Test
    public void modifySource() {
        BluetoothDevice device = TestUtils.getTestDevice(mAdapter, 0);
        mBinder.modifySource(device, 0, null);
        verify(mService).modifySource(device, 0, null);

        Mockito.clearInvocations(mService);
        doThrow(new RuntimeException()).when(mService).enforceCallingOrSelfPermission(any(), any());
        mBinder.modifySource(device, 0, null);
        verify(mService, never()).modifySource(device, 0, null);

        mBinder.cleanup();
        mBinder.modifySource(device, 0, null);
        verify(mService, never()).modifySource(device, 0, null);
    }

    @Test
    public void removeSource() {
        BluetoothDevice device = TestUtils.getTestDevice(mAdapter, 0);
        mBinder.removeSource(device, 0);
        verify(mService).removeSource(device, 0);

        Mockito.clearInvocations(mService);
        doThrow(new RuntimeException()).when(mService).enforceCallingOrSelfPermission(any(), any());
        mBinder.removeSource(device, 0);
        verify(mService, never()).removeSource(device, 0);

        mBinder.cleanup();
        mBinder.removeSource(device, 0);
        verify(mService, never()).removeSource(device, 0);
    }

    @Test
    public void getAllSources() {
        BluetoothDevice device = TestUtils.getTestDevice(mAdapter, 0);
        mBinder.getAllSources(device);
        verify(mService).getAllSources(device);

        doThrow(new RuntimeException()).when(mService).getConnectionPolicy(device);
        assertThat(mBinder.getAllSources(device)).isEqualTo(Collections.emptyList());

        mBinder.cleanup();
        assertThat(mBinder.getAllSources(device)).isEqualTo(Collections.emptyList());
    }

    @Test
    public void getMaximumSourceCapacity() {
        BluetoothDevice device = TestUtils.getTestDevice(mAdapter, 0);
        mBinder.getMaximumSourceCapacity(device);
        verify(mService).getMaximumSourceCapacity(device);

        doThrow(new RuntimeException()).when(mService).getMaximumSourceCapacity(device);
        assertThat(mBinder.getMaximumSourceCapacity(device)).isEqualTo(0);

        mBinder.cleanup();
        assertThat(mBinder.getMaximumSourceCapacity(device)).isEqualTo(0);
    }
}
