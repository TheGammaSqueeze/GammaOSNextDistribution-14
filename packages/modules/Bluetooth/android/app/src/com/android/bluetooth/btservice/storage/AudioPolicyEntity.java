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

package com.android.bluetooth.btservice.storage;

import android.bluetooth.BluetoothSinkAudioPolicy;

import androidx.room.ColumnInfo;
import androidx.room.Entity;

@Entity
class AudioPolicyEntity {
    @ColumnInfo(name = "call_establish_audio_policy")
    public int callEstablishAudioPolicy;
    @ColumnInfo(name = "connecting_time_audio_policy")
    public int connectingTimeAudioPolicy;
    @ColumnInfo(name = "in_band_ringtone_audio_policy")
    public int inBandRingtoneAudioPolicy;

    AudioPolicyEntity() {
        callEstablishAudioPolicy = BluetoothSinkAudioPolicy.POLICY_UNCONFIGURED;
        connectingTimeAudioPolicy = BluetoothSinkAudioPolicy.POLICY_UNCONFIGURED;
        inBandRingtoneAudioPolicy = BluetoothSinkAudioPolicy.POLICY_UNCONFIGURED;
    }

    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("callEstablishAudioPolicy=")
                .append(metadataToString(callEstablishAudioPolicy))
                .append("|connectingTimeAudioPolicy=")
                .append(metadataToString(connectingTimeAudioPolicy))
                .append("|inBandRingtoneAudioPolicy=")
                .append(metadataToString(inBandRingtoneAudioPolicy));

        return builder.toString();
    }

    private String metadataToString(int metadata) {
        return String.valueOf(metadata);
    }
}
