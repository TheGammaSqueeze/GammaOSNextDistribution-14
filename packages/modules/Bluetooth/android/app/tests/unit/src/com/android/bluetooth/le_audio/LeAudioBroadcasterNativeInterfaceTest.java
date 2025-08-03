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

import android.bluetooth.BluetoothLeBroadcastMetadata;

import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class LeAudioBroadcasterNativeInterfaceTest {
    @Mock
    private LeAudioService mMockService;

    private LeAudioBroadcasterNativeInterface mNativeInterface;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mMockService.isAvailable()).thenReturn(true);
        LeAudioService.setLeAudioService(mMockService);
        mNativeInterface = LeAudioBroadcasterNativeInterface.getInstance();
    }

    @After
    public void tearDown() {
        LeAudioService.setLeAudioService(null);
    }

    @Test
    public void onBroadcastCreated() {
        int broadcastId = 1;
        boolean success = true;

        mNativeInterface.onBroadcastCreated(broadcastId, success);

        ArgumentCaptor<LeAudioStackEvent> event =
                ArgumentCaptor.forClass(LeAudioStackEvent.class);
        verify(mMockService).messageFromNative(event.capture());
        assertThat(event.getValue().type).isEqualTo(
                LeAudioStackEvent.EVENT_TYPE_BROADCAST_CREATED);
    }

    @Test
    public void onBroadcastDestroyed() {
        int broadcastId = 1;

        mNativeInterface.onBroadcastDestroyed(broadcastId);

        ArgumentCaptor<LeAudioStackEvent> event =
                ArgumentCaptor.forClass(LeAudioStackEvent.class);
        verify(mMockService).messageFromNative(event.capture());
        assertThat(event.getValue().type).isEqualTo(
                LeAudioStackEvent.EVENT_TYPE_BROADCAST_DESTROYED);
    }

    @Test
    public void onBroadcastStateChanged() {
        int broadcastId = 1;
        int state = 0;

        mNativeInterface.onBroadcastStateChanged(broadcastId, state);

        ArgumentCaptor<LeAudioStackEvent> event =
                ArgumentCaptor.forClass(LeAudioStackEvent.class);
        verify(mMockService).messageFromNative(event.capture());
        assertThat(event.getValue().type).isEqualTo(
                LeAudioStackEvent.EVENT_TYPE_BROADCAST_STATE);
    }

    @Test
    public void onBroadcastMetadataChanged() {
        int broadcastId = 1;
        BluetoothLeBroadcastMetadata metadata = null;

        mNativeInterface.onBroadcastMetadataChanged(broadcastId, metadata);

        ArgumentCaptor<LeAudioStackEvent> event =
                ArgumentCaptor.forClass(LeAudioStackEvent.class);
        verify(mMockService).messageFromNative(event.capture());
        assertThat(event.getValue().type).isEqualTo(
                LeAudioStackEvent.EVENT_TYPE_BROADCAST_METADATA_CHANGED);
    }
}
