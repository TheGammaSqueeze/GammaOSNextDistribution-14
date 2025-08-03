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


import static android.media.AudioAttributes.USAGE_MEDIA;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioPlaybackConfiguration;

final class AudioPlaybackConfigurationBuilder {
    private @AudioAttributes.AttributeUsage int mUsage = USAGE_MEDIA;
    private boolean mIsActive = true;
    private String mDeviceAddress = "";

    AudioPlaybackConfigurationBuilder setUsage(@AudioAttributes.AttributeUsage int usage) {
        mUsage = usage;
        return this;
    }

    AudioPlaybackConfigurationBuilder setDeviceAddress(String deviceAddress) {
        mDeviceAddress = deviceAddress;
        return this;
    }

    AudioPlaybackConfigurationBuilder setInactive() {
        mIsActive = false;
        return this;
    }

    AudioPlaybackConfiguration build() {
        AudioPlaybackConfiguration configuration = mock(AudioPlaybackConfiguration.class);
        AudioAttributes attributes = new AudioAttributes.Builder().setUsage(mUsage).build();
        AudioDeviceInfo outputDevice = generateOutAudioDeviceInfo(mDeviceAddress);
        when(configuration.getAudioAttributes()).thenReturn(attributes);
        when(configuration.getAudioDeviceInfo()).thenReturn(outputDevice);
        when(configuration.isActive()).thenReturn(mIsActive);
        return configuration;
    }

    private AudioDeviceInfo generateOutAudioDeviceInfo(String address) {
        AudioDeviceInfo audioDeviceInfo = mock(AudioDeviceInfo.class);
        when(audioDeviceInfo.getAddress()).thenReturn(address);
        when(audioDeviceInfo.getType()).thenReturn(AudioDeviceInfo.TYPE_BUS);
        when(audioDeviceInfo.isSource()).thenReturn(false);
        when(audioDeviceInfo.isSink()).thenReturn(true);
        when(audioDeviceInfo.getInternalType()).thenReturn(AudioDeviceInfo
                .convertDeviceTypeToInternalInputDevice(AudioDeviceInfo.TYPE_BUS));
        return audioDeviceInfo;
    }
}
