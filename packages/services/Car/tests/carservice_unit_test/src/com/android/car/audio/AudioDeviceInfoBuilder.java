/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.car.audio;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.media.AudioDeviceInfo;
import android.media.AudioDevicePort;
import android.media.AudioGain;

public final class AudioDeviceInfoBuilder {

    private String mAddressName;
    private AudioGain[] mAudioGains;
    private int mDeviceType = AudioDeviceInfo.TYPE_BUS;
    private boolean mIsSource;

    public AudioDeviceInfoBuilder setAudioGains(AudioGain ... audioGains) {
        mAudioGains = audioGains;
        return this;
    }

    public AudioDeviceInfoBuilder setAddressName(String addressName) {
        mAddressName = addressName;
        return this;
    }

    public AudioDeviceInfoBuilder setType(int deviceType) {
        mDeviceType = deviceType;
        return this;
    }

    public AudioDeviceInfoBuilder setIsSource(boolean isSource) {
        mIsSource = isSource;
        return this;
    }

    public AudioDeviceInfo build() {
        AudioDevicePort port = mock(AudioDevicePort.class);
        when(port.gains()).thenReturn(mAudioGains);

        AudioDeviceInfo audioDeviceInfo = mock(AudioDeviceInfo.class);
        when(audioDeviceInfo.getAddress()).thenReturn(mAddressName);
        when(audioDeviceInfo.getType()).thenReturn(mDeviceType);
        when(audioDeviceInfo.isSource()).thenReturn(mIsSource);
        when(audioDeviceInfo.isSink()).thenReturn(!mIsSource);
        when(audioDeviceInfo.getInternalType())
                .thenReturn(AudioDeviceInfo.convertDeviceTypeToInternalInputDevice(mDeviceType));
        when(audioDeviceInfo.getPort()).thenReturn(port);
        return audioDeviceInfo;
    }
}
