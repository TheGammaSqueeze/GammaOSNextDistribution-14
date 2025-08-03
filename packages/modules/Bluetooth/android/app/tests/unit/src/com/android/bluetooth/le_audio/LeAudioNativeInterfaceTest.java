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

package com.android.bluetooth.le_audio;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothLeAudio;
import android.bluetooth.BluetoothLeAudioCodecConfig;

import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class LeAudioNativeInterfaceTest {
    @Mock
    private LeAudioService mMockService;

    private LeAudioNativeInterface mNativeInterface;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mMockService.isAvailable()).thenReturn(true);
        LeAudioService.setLeAudioService(mMockService);
        mNativeInterface = LeAudioNativeInterface.getInstance();
    }

    @After
    public void tearDown() {
        LeAudioService.setLeAudioService(null);
    }

    @Test
    public void onConnectionStateChanged() {
        int state = LeAudioStackEvent.CONNECTION_STATE_CONNECTED;
        byte[] address = new byte[] { 0x00, 0x01, 0x02, 0x03, 0x04, 0x05 };

        mNativeInterface.onConnectionStateChanged(state, address);

        ArgumentCaptor<LeAudioStackEvent> event =
                ArgumentCaptor.forClass(LeAudioStackEvent.class);
        verify(mMockService).messageFromNative(event.capture());
        assertThat(event.getValue().type).isEqualTo(
                LeAudioStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
    }

    @Test
    public void onGroupNodeStatus() {
        byte[] address = new byte[] { 0x00, 0x01, 0x02, 0x03, 0x04, 0x05 };
        int groupId = 1;
        int nodeStatus = LeAudioStackEvent.GROUP_NODE_ADDED;

        mNativeInterface.onGroupNodeStatus(address, groupId, nodeStatus);

        ArgumentCaptor<LeAudioStackEvent> event =
                ArgumentCaptor.forClass(LeAudioStackEvent.class);
        verify(mMockService).messageFromNative(event.capture());
        assertThat(event.getValue().type).isEqualTo(
                LeAudioStackEvent.EVENT_TYPE_GROUP_NODE_STATUS_CHANGED);
    }

    @Test
    public void onAudioConf() {
        int direction = 0;
        int groupId = 1;
        int sinkAudioLocation = BluetoothLeAudio.AUDIO_LOCATION_INVALID;
        int sourceAudioLocation = BluetoothLeAudio.AUDIO_LOCATION_INVALID;
        int availableContexts = 2;

        mNativeInterface.onAudioConf(
                direction, groupId, sinkAudioLocation, sourceAudioLocation, availableContexts);

        ArgumentCaptor<LeAudioStackEvent> event =
                ArgumentCaptor.forClass(LeAudioStackEvent.class);
        verify(mMockService).messageFromNative(event.capture());
        assertThat(event.getValue().type).isEqualTo(
                LeAudioStackEvent.EVENT_TYPE_AUDIO_CONF_CHANGED);
    }

    @Test
    public void onSinkAudioLocationAvailable() {
        byte[] address = new byte[] { 0x00, 0x01, 0x02, 0x03, 0x04, 0x05 };
        int sinkAudioLocation = BluetoothLeAudio.AUDIO_LOCATION_INVALID;

        mNativeInterface.onSinkAudioLocationAvailable(address, sinkAudioLocation);

        ArgumentCaptor<LeAudioStackEvent> event =
                ArgumentCaptor.forClass(LeAudioStackEvent.class);
        verify(mMockService).messageFromNative(event.capture());
        assertThat(event.getValue().type).isEqualTo(
                LeAudioStackEvent.EVENT_TYPE_SINK_AUDIO_LOCATION_AVAILABLE);
    }

    @Test
    public void onAudioLocalCodecCapabilities() {
        BluetoothLeAudioCodecConfig emptyConfig =
                new BluetoothLeAudioCodecConfig.Builder().build();
        BluetoothLeAudioCodecConfig[] localInputCodecCapabilities =
                new BluetoothLeAudioCodecConfig[] { emptyConfig };
        BluetoothLeAudioCodecConfig[] localOutputCodecCapabilities =
                new BluetoothLeAudioCodecConfig[] { emptyConfig };

        mNativeInterface.onAudioLocalCodecCapabilities(
                localInputCodecCapabilities, localOutputCodecCapabilities);

        ArgumentCaptor<LeAudioStackEvent> event =
                ArgumentCaptor.forClass(LeAudioStackEvent.class);
        verify(mMockService).messageFromNative(event.capture());
        assertThat(event.getValue().type).isEqualTo(
                LeAudioStackEvent.EVENT_TYPE_AUDIO_LOCAL_CODEC_CONFIG_CAPA_CHANGED);
    }

    @Test
    public void onAudioGroupCurrentCodecConf() {
        int groupId = 1;
        BluetoothLeAudioCodecConfig inputConfig = new BluetoothLeAudioCodecConfig.Builder().build();
        BluetoothLeAudioCodecConfig outputConfig =
                new BluetoothLeAudioCodecConfig.Builder().build();

        mNativeInterface.onAudioGroupCurrentCodecConf(groupId, inputConfig, outputConfig);

        ArgumentCaptor<LeAudioStackEvent> event = ArgumentCaptor.forClass(LeAudioStackEvent.class);
        verify(mMockService).messageFromNative(event.capture());
        assertThat(event.getValue().type)
                .isEqualTo(LeAudioStackEvent.EVENT_TYPE_AUDIO_GROUP_CURRENT_CODEC_CONFIG_CHANGED);
    }

    @Test
    public void onAudioGroupSelectableCodecConf() {
        int groupId = 1;
        BluetoothLeAudioCodecConfig inputConfig =
                new BluetoothLeAudioCodecConfig.Builder().build();
        BluetoothLeAudioCodecConfig outputConfig =
                new BluetoothLeAudioCodecConfig.Builder().build();
        BluetoothLeAudioCodecConfig[] inputSelectableCodecConfig =
                new BluetoothLeAudioCodecConfig[] { inputConfig };
        BluetoothLeAudioCodecConfig[] outputSelectableCodecConfig =
                new BluetoothLeAudioCodecConfig[] { outputConfig };

        mNativeInterface.onAudioGroupSelectableCodecConf(
                groupId, inputSelectableCodecConfig, outputSelectableCodecConfig);

        ArgumentCaptor<LeAudioStackEvent> event =
                ArgumentCaptor.forClass(LeAudioStackEvent.class);
        verify(mMockService).messageFromNative(event.capture());
        assertThat(event.getValue().type)
                .isEqualTo(
                        LeAudioStackEvent.EVENT_TYPE_AUDIO_GROUP_SELECTABLE_CODEC_CONFIG_CHANGED);
    }
}
