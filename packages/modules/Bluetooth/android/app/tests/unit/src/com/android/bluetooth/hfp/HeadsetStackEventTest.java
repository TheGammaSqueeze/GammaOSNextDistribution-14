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

import static com.google.common.truth.Truth.assertThat;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class HeadsetStackEventTest {

    @Test
    public void getTypeString() {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        BluetoothDevice device = adapter.getRemoteDevice("00:01:02:03:04:05");

        HeadsetStackEvent event = new HeadsetStackEvent(HeadsetStackEvent.EVENT_TYPE_NONE, device);
        assertThat(event.getTypeString()).isEqualTo("EVENT_TYPE_NONE");

        event = new HeadsetStackEvent(HeadsetStackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED,
                device);
        assertThat(event.getTypeString()).isEqualTo("EVENT_TYPE_CONNECTION_STATE_CHANGED");

        event = new HeadsetStackEvent(HeadsetStackEvent.EVENT_TYPE_AUDIO_STATE_CHANGED, device);
        assertThat(event.getTypeString()).isEqualTo("EVENT_TYPE_AUDIO_STATE_CHANGED");

        event = new HeadsetStackEvent(HeadsetStackEvent.EVENT_TYPE_VR_STATE_CHANGED, device);
        assertThat(event.getTypeString()).isEqualTo("EVENT_TYPE_VR_STATE_CHANGED");

        event = new HeadsetStackEvent(HeadsetStackEvent.EVENT_TYPE_ANSWER_CALL, device);
        assertThat(event.getTypeString()).isEqualTo("EVENT_TYPE_ANSWER_CALL");

        event = new HeadsetStackEvent(HeadsetStackEvent.EVENT_TYPE_HANGUP_CALL, device);
        assertThat(event.getTypeString()).isEqualTo("EVENT_TYPE_HANGUP_CALL");

        event = new HeadsetStackEvent(HeadsetStackEvent.EVENT_TYPE_VOLUME_CHANGED, device);
        assertThat(event.getTypeString()).isEqualTo("EVENT_TYPE_VOLUME_CHANGED");

        event = new HeadsetStackEvent(HeadsetStackEvent.EVENT_TYPE_DIAL_CALL, device);
        assertThat(event.getTypeString()).isEqualTo("EVENT_TYPE_DIAL_CALL");

        event = new HeadsetStackEvent(HeadsetStackEvent.EVENT_TYPE_SEND_DTMF, device);
        assertThat(event.getTypeString()).isEqualTo("EVENT_TYPE_SEND_DTMF");

        event = new HeadsetStackEvent(HeadsetStackEvent.EVENT_TYPE_NOISE_REDUCTION, device);
        assertThat(event.getTypeString()).isEqualTo("EVENT_TYPE_NOISE_REDUCTION");

        event = new HeadsetStackEvent(HeadsetStackEvent.EVENT_TYPE_AT_CHLD, device);
        assertThat(event.getTypeString()).isEqualTo("EVENT_TYPE_AT_CHLD");

        event = new HeadsetStackEvent(HeadsetStackEvent.EVENT_TYPE_SUBSCRIBER_NUMBER_REQUEST,
                device);
        assertThat(event.getTypeString()).isEqualTo("EVENT_TYPE_SUBSCRIBER_NUMBER_REQUEST");

        event = new HeadsetStackEvent(HeadsetStackEvent.EVENT_TYPE_AT_CIND, device);
        assertThat(event.getTypeString()).isEqualTo("EVENT_TYPE_AT_CIND");

        event = new HeadsetStackEvent(HeadsetStackEvent.EVENT_TYPE_AT_COPS, device);
        assertThat(event.getTypeString()).isEqualTo("EVENT_TYPE_AT_COPS");

        event = new HeadsetStackEvent(HeadsetStackEvent.EVENT_TYPE_AT_CLCC, device);
        assertThat(event.getTypeString()).isEqualTo("EVENT_TYPE_AT_CLCC");

        event = new HeadsetStackEvent(HeadsetStackEvent.EVENT_TYPE_UNKNOWN_AT, device);
        assertThat(event.getTypeString()).isEqualTo("EVENT_TYPE_UNKNOWN_AT");

        event = new HeadsetStackEvent(HeadsetStackEvent.EVENT_TYPE_KEY_PRESSED, device);
        assertThat(event.getTypeString()).isEqualTo("EVENT_TYPE_KEY_PRESSED");

        event = new HeadsetStackEvent(HeadsetStackEvent.EVENT_TYPE_WBS, device);
        assertThat(event.getTypeString()).isEqualTo("EVENT_TYPE_WBS");

        event = new HeadsetStackEvent(HeadsetStackEvent.EVENT_TYPE_BIND, device);
        assertThat(event.getTypeString()).isEqualTo("EVENT_TYPE_BIND");

        event = new HeadsetStackEvent(HeadsetStackEvent.EVENT_TYPE_BIEV, device);
        assertThat(event.getTypeString()).isEqualTo("EVENT_TYPE_BIEV");

        event = new HeadsetStackEvent(HeadsetStackEvent.EVENT_TYPE_BIA, device);
        assertThat(event.getTypeString()).isEqualTo("EVENT_TYPE_BIA");

        event = new HeadsetStackEvent(HeadsetStackEvent.EVENT_TYPE_SWB, device);
        assertThat(event.getTypeString()).isEqualTo("EVENT_TYPE_SWB");
    }
}
