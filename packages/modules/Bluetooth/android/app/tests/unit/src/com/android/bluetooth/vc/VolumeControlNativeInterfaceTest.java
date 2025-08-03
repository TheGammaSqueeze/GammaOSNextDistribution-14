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

package com.android.bluetooth.vc;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class VolumeControlNativeInterfaceTest {
    @Mock
    private VolumeControlService mService;

    private VolumeControlNativeInterface mNativeInterface;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mService.isAvailable()).thenReturn(true);
        VolumeControlService.setVolumeControlService(mService);
        mNativeInterface = VolumeControlNativeInterface.getInstance();
    }

    @After
    public void tearDown() {
        VolumeControlService.setVolumeControlService(null);
    }

    @Test
    public void onConnectionStateChanged() {
        int state = VolumeControlStackEvent.CONNECTION_STATE_CONNECTED;
        byte[] address = new byte[] { 0x00, 0x01, 0x02, 0x03, 0x04, 0x05 };

        mNativeInterface.onConnectionStateChanged(state, address);

        ArgumentCaptor<VolumeControlStackEvent> event =
                ArgumentCaptor.forClass(VolumeControlStackEvent.class);
        verify(mService).messageFromNative(event.capture());
        assertThat(event.getValue().type).isEqualTo(
                VolumeControlStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
    }

    @Test
    public void onVolumeStateChanged() {
        int volume = 3;
        boolean mute = false;
        byte[] address = new byte[] { 0x00, 0x01, 0x02, 0x03, 0x04, 0x05 };
        boolean isAutonomous = false;

        mNativeInterface.onVolumeStateChanged(volume, mute, address, isAutonomous);

        ArgumentCaptor<VolumeControlStackEvent> event =
                ArgumentCaptor.forClass(VolumeControlStackEvent.class);
        verify(mService).messageFromNative(event.capture());
        assertThat(event.getValue().type).isEqualTo(
                VolumeControlStackEvent.EVENT_TYPE_VOLUME_STATE_CHANGED);
    }

    @Test
    public void onGroupVolumeStateChanged() {
        int volume = 3;
        boolean mute = false;
        int groupId = 1;
        boolean isAutonomous = false;

        mNativeInterface.onGroupVolumeStateChanged(volume, mute, groupId, isAutonomous);

        ArgumentCaptor<VolumeControlStackEvent> event =
                ArgumentCaptor.forClass(VolumeControlStackEvent.class);
        verify(mService).messageFromNative(event.capture());
        assertThat(event.getValue().type).isEqualTo(
                VolumeControlStackEvent.EVENT_TYPE_VOLUME_STATE_CHANGED);
        assertThat(event.getValue().valueInt1).isEqualTo(groupId);
    }

    @Test
    public void onDeviceAvailable() {
        int numOfExternalOutputs = 3;
        byte[] address = new byte[] { 0x00, 0x01, 0x02, 0x03, 0x04, 0x05 };

        mNativeInterface.onDeviceAvailable(numOfExternalOutputs, address);

        ArgumentCaptor<VolumeControlStackEvent> event =
                ArgumentCaptor.forClass(VolumeControlStackEvent.class);
        verify(mService).messageFromNative(event.capture());
        assertThat(event.getValue().type).isEqualTo(
                VolumeControlStackEvent.EVENT_TYPE_DEVICE_AVAILABLE);
    }
    @Test
    public void onExtAudioOutVolumeOffsetChanged() {
        int externalOutputId = 2;
        int offset = 0;
        byte[] address = new byte[] { 0x00, 0x01, 0x02, 0x03, 0x04, 0x05 };

        mNativeInterface.onExtAudioOutVolumeOffsetChanged(externalOutputId, offset, address);

        ArgumentCaptor<VolumeControlStackEvent> event =
                ArgumentCaptor.forClass(VolumeControlStackEvent.class);
        verify(mService).messageFromNative(event.capture());
        assertThat(event.getValue().type).isEqualTo(
                VolumeControlStackEvent.EVENT_TYPE_EXT_AUDIO_OUT_VOL_OFFSET_CHANGED);
    }

    @Test
    public void onExtAudioOutLocationChanged() {
        int externalOutputId = 2;
        int location = 100;
        byte[] address = new byte[] { 0x00, 0x01, 0x02, 0x03, 0x04, 0x05 };

        mNativeInterface.onExtAudioOutLocationChanged(externalOutputId, location, address);

        ArgumentCaptor<VolumeControlStackEvent> event =
                ArgumentCaptor.forClass(VolumeControlStackEvent.class);
        verify(mService).messageFromNative(event.capture());
        assertThat(event.getValue().type).isEqualTo(
                VolumeControlStackEvent.EVENT_TYPE_EXT_AUDIO_OUT_LOCATION_CHANGED);
    }

    @Test
    public void onExtAudioOutDescriptionChanged() {
        int externalOutputId = 2;
        String descr = "test-descr";
        byte[] address = new byte[] { 0x00, 0x01, 0x02, 0x03, 0x04, 0x05 };

        mNativeInterface.onExtAudioOutDescriptionChanged(externalOutputId, descr, address);

        ArgumentCaptor<VolumeControlStackEvent> event =
                ArgumentCaptor.forClass(VolumeControlStackEvent.class);
        verify(mService).messageFromNative(event.capture());
        assertThat(event.getValue().type).isEqualTo(
                VolumeControlStackEvent.EVENT_TYPE_EXT_AUDIO_OUT_DESCRIPTION_CHANGED);
    }
}
