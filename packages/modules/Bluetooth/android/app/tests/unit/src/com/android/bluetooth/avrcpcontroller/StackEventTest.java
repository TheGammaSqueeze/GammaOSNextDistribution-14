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

package com.android.bluetooth.avrcpcontroller;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class StackEventTest {

    @Test
    public void connectionStateChanged() {
        boolean remoteControlConnected = true;
        boolean browsingConnected = true;

        StackEvent stackEvent = StackEvent.connectionStateChanged(remoteControlConnected,
                browsingConnected);

        assertThat(stackEvent.mType).isEqualTo(StackEvent.EVENT_TYPE_CONNECTION_STATE_CHANGED);
        assertThat(stackEvent.mRemoteControlConnected).isTrue();
        assertThat(stackEvent.mBrowsingConnected).isTrue();
        assertThat(stackEvent.toString()).isEqualTo(
                "EVENT_TYPE_CONNECTION_STATE_CHANGED " + remoteControlConnected);
    }

    @Test
    public void rcFeatures() {
        int features = 3;

        StackEvent stackEvent = StackEvent.rcFeatures(features);

        assertThat(stackEvent.mType).isEqualTo(StackEvent.EVENT_TYPE_RC_FEATURES);
        assertThat(stackEvent.mFeatures).isEqualTo(features);
        assertThat(stackEvent.toString()).isEqualTo("EVENT_TYPE_RC_FEATURES");
    }

    @Test
    public void toString_whenEventTypeNone() {
        StackEvent stackEvent = StackEvent.rcFeatures(1);

        stackEvent.mType = StackEvent.EVENT_TYPE_NONE;

        assertThat(stackEvent.toString()).isEqualTo("Unknown");
    }
}