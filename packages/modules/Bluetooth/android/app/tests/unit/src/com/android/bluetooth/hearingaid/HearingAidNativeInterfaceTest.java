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

package com.android.bluetooth.hearingaid;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;

import com.android.bluetooth.TestUtils;
import com.android.bluetooth.Utils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class HearingAidNativeInterfaceTest {

    @Mock private HearingAidService mService;

    private HearingAidNativeInterface mNativeInterface;
    private BluetoothAdapter mAdapter;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mService.isAvailable()).thenReturn(true);
        HearingAidService.setHearingAidService(mService);
        mNativeInterface = HearingAidNativeInterface.getInstance();
        mAdapter = BluetoothAdapter.getDefaultAdapter();
    }

    @After
    public void tearDown() {
        HearingAidService.setHearingAidService(null);
    }

    @Test
    public void getByteAddress() {
        assertThat(mNativeInterface.getByteAddress(null))
                .isEqualTo(Utils.getBytesFromAddress("00:00:00:00:00:00"));

        BluetoothDevice device = TestUtils.getTestDevice(mAdapter, 0);
        assertThat(mNativeInterface.getByteAddress(device))
                .isEqualTo(Utils.getBytesFromAddress(device.getAddress()));
    }

    @Test
    public void onConnectionStateChanged() {
        BluetoothDevice device = TestUtils.getTestDevice(mAdapter, 0);
        mNativeInterface.onConnectionStateChanged(BluetoothProfile.STATE_CONNECTED,
                mNativeInterface.getByteAddress(device));

        ArgumentCaptor<HearingAidStackEvent> event =
                ArgumentCaptor.forClass(HearingAidStackEvent.class);
        verify(mService).messageFromNative(event.capture());
        assertThat(event.getValue().type).isEqualTo(
                HearingAidStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        assertThat(event.getValue().valueInt1).isEqualTo(BluetoothProfile.STATE_CONNECTED);

        Mockito.clearInvocations(mService);
        HearingAidService.setHearingAidService(null);
        mNativeInterface.onConnectionStateChanged(BluetoothProfile.STATE_CONNECTED,
                mNativeInterface.getByteAddress(device));
        verify(mService, never()).messageFromNative(any());
    }

    @Test
    public void onDeviceAvailable() {
        BluetoothDevice device = TestUtils.getTestDevice(mAdapter, 0);
        byte capabilities = 0;
        long hiSyncId = 100;
        mNativeInterface.onDeviceAvailable(capabilities, hiSyncId,
                mNativeInterface.getByteAddress(device));

        ArgumentCaptor<HearingAidStackEvent> event =
                ArgumentCaptor.forClass(HearingAidStackEvent.class);
        verify(mService).messageFromNative(event.capture());
        assertThat(event.getValue().type).isEqualTo(
                HearingAidStackEvent.EVENT_TYPE_DEVICE_AVAILABLE);
        assertThat(event.getValue().valueInt1).isEqualTo(capabilities);
        assertThat(event.getValue().valueLong2).isEqualTo(hiSyncId);

        Mockito.clearInvocations(mService);
        HearingAidService.setHearingAidService(null);
        mNativeInterface.onDeviceAvailable(capabilities, hiSyncId,
                mNativeInterface.getByteAddress(device));
        verify(mService, never()).messageFromNative(any());
    }
}
