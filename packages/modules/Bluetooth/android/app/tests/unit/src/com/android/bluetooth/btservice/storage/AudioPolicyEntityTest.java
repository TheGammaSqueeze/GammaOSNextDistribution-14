/*
 * Copyright 2023 The Android Open Source Project
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

package com.android.bluetooth.btservice.storage;

import static com.google.common.truth.Truth.assertThat;

import android.bluetooth.BluetoothSinkAudioPolicy;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class AudioPolicyEntityTest {
    @Test
    public void constructor() {
        AudioPolicyEntity entity = new AudioPolicyEntity();
        assertThat(entity.callEstablishAudioPolicy)
                .isEqualTo(BluetoothSinkAudioPolicy.POLICY_UNCONFIGURED);
        assertThat(entity.connectingTimeAudioPolicy)
                .isEqualTo(BluetoothSinkAudioPolicy.POLICY_UNCONFIGURED);
        assertThat(entity.inBandRingtoneAudioPolicy)
                .isEqualTo(BluetoothSinkAudioPolicy.POLICY_UNCONFIGURED);
    }

    @Test
    public void toString_shouldNotEmpty() {
        AudioPolicyEntity entity = new AudioPolicyEntity();
        assertThat(entity.toString()).isNotEmpty();
    }
}
