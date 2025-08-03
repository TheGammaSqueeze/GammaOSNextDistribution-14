/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static android.media.AudioDeviceInfo.TYPE_BUS;

import static org.mockito.Mockito.when;

import android.media.AudioDeviceAttributes;

import org.mockito.Mockito;

public final class TestCarAudioDeviceInfoBuilder {
    public static final int STEP_VALUE = 2;
    public static final int MIN_GAIN = 3;
    public static final int MAX_GAIN = 20;
    public static final int DEFAULT_GAIN = 9;

    private final String mAddress;

    private int mStepValue = STEP_VALUE;
    private int mDefaultGain = DEFAULT_GAIN;
    private int mMinGain = MIN_GAIN;
    private int mMaxGain = MAX_GAIN;
    private boolean mIsActive = true;

    private int mType = TYPE_BUS;

    TestCarAudioDeviceInfoBuilder(String address) {
        mAddress = address;
    }

    TestCarAudioDeviceInfoBuilder setStepValue(int stepValue) {
        mStepValue = stepValue;
        return this;
    }

    TestCarAudioDeviceInfoBuilder setDefaultGain(int defaultGain) {
        mDefaultGain = defaultGain;
        return this;
    }

    TestCarAudioDeviceInfoBuilder setMinGain(int minGain) {
        mMinGain = minGain;
        return this;
    }

    TestCarAudioDeviceInfoBuilder setMaxGain(int maxGain) {
        mMaxGain = maxGain;
        return this;
    }

    TestCarAudioDeviceInfoBuilder setIsActive(boolean isActive) {
        mIsActive = isActive;
        return this;
    }

    TestCarAudioDeviceInfoBuilder setType(int type) {
        mType = type;
        return this;
    }

    CarAudioDeviceInfo build() {
        AudioDeviceAttributes attributeMock = Mockito.mock(AudioDeviceAttributes.class);
        when(attributeMock.getAddress()).thenReturn(mAddress);
        when(attributeMock.getType()).thenReturn(mType);
        CarAudioDeviceInfo infoMock = Mockito.mock(CarAudioDeviceInfo.class);
        when(infoMock.getStepValue()).thenReturn(mStepValue);
        when(infoMock.getDefaultGain()).thenReturn(mDefaultGain);
        when(infoMock.getMaxGain()).thenReturn(mMaxGain);
        when(infoMock.getMinGain()).thenReturn(mMinGain);
        when(infoMock.getAddress()).thenReturn(mAddress);
        when(infoMock.getAudioDevice()).thenReturn(attributeMock);
        when(infoMock.isActive()).thenReturn(mIsActive);
        return infoMock;
    }
}
