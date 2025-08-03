/*
 * Copyright 2021 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class StackEventTest {
    private Context mTargetContext = null;
    private BluetoothAdapter mAdapter = null;
    private BluetoothDevice mDevice = null;
    private static final String TEST_ADDRESS = "11:11:11:11:11:11";

    @Before
    public void setUp() throws Exception {
        mTargetContext = InstrumentationRegistry.getTargetContext();
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        assertThat(mAdapter).isNotNull();
        mDevice = mAdapter.getRemoteDevice(TEST_ADDRESS);
    }

    @After
    public void tearDown() throws Exception {
        mTargetContext = null;
        mAdapter = null;
        mDevice = null;
    }

    @Test
    public void testCreateConnectionStateChangedDisconnectedEvent() {
        testConnectionStateChangedBase(StackEvent.CONNECTION_STATE_DISCONNECTED);
    }

    @Test
    public void testCreateConnectionStateChangedConnectingEvent() {
        testConnectionStateChangedBase(StackEvent.CONNECTION_STATE_CONNECTING);
    }

    @Test
    public void testCreateConnectionStateChangedConnectedEvent() {
        testConnectionStateChangedBase(StackEvent.CONNECTION_STATE_CONNECTED);
    }

    @Test
    public void testCreateConnectionStateChangedDisconnectingEvent() {
        testConnectionStateChangedBase(StackEvent.CONNECTION_STATE_DISCONNECTING);
    }

    private void testConnectionStateChangedBase(int state) {
        StackEvent event = StackEvent.connectionStateChanged(mDevice, state);
        assertThat(event).isNotNull();
        assertThat(event.mType).isEqualTo(StackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        assertThat(event.mDevice).isEqualTo(mDevice);
        assertThat(event.mState).isEqualTo(state);
        assertThat(event.toString()).isNotNull();
    }

    @Test
    public void testCreateAudioStateStoppedEvent() {
        testAudioStateChangedBase(StackEvent.AUDIO_STATE_STOPPED);
    }

    @Test
    public void testCreateAudioStateStartedvent() {
        testAudioStateChangedBase(StackEvent.AUDIO_STATE_STARTED);
    }

    @Test
    public void testCreateAudioStateRemoteSuspendEvent() {
        testAudioStateChangedBase(StackEvent.AUDIO_STATE_REMOTE_SUSPEND);
    }

    private void testAudioStateChangedBase(int state) {
        StackEvent event = StackEvent.audioStateChanged(mDevice, state);
        assertThat(event).isNotNull();
        assertThat(event.mType).isEqualTo(StackEvent.EVENT_TYPE_AUDIO_STATE_CHANGED);
        assertThat(event.mDevice).isEqualTo(mDevice);
        assertThat(event.mState).isEqualTo(state);
        assertThat(event.toString()).isNotNull();
    }

    @Test
    public void testCreateAudioConfigurationChangedEvent() {
        int sampleRate = 44000;
        int channelCount = 1;
        StackEvent event = StackEvent.audioConfigChanged(mDevice, sampleRate, channelCount);
        assertThat(event).isNotNull();
        assertThat(event.mType).isEqualTo(StackEvent.EVENT_TYPE_AUDIO_CONFIG_CHANGED);
        assertThat(event.mDevice).isEqualTo(mDevice);
        assertThat(event.mSampleRate).isEqualTo(sampleRate);
        assertThat(event.mChannelCount).isEqualTo(channelCount);
        assertThat(event.toString()).isNotNull();
    }
}
