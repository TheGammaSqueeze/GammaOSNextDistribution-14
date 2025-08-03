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

import static android.media.audio.common.AudioDeviceDescription.CONNECTION_BUS;
import static android.media.audio.common.AudioDeviceType.OUT_DEVICE;
import static android.media.audio.common.AudioGainMode.JOINT;

import static com.android.car.audio.GainBuilder.DEFAULT_GAIN;
import static com.android.car.audio.GainBuilder.MAX_GAIN;
import static com.android.car.audio.GainBuilder.MIN_GAIN;
import static com.android.car.audio.GainBuilder.STEP_SIZE;

import android.media.audio.common.AudioDevice;
import android.media.audio.common.AudioDeviceAddress;
import android.media.audio.common.AudioDeviceDescription;
import android.media.audio.common.AudioPort;
import android.media.audio.common.AudioPortDeviceExt;
import android.media.audio.common.AudioPortExt;

import com.android.car.audio.hal.HalAudioDeviceInfo;

public final class HalAudioDeviceInfoBuilder {
    private String mAddress;
    private String mName = " ";
    private int mType = OUT_DEVICE;
    private int mId;
    private int mMaxValue = MAX_GAIN;
    private int mMinValue = MIN_GAIN;
    private int mDefaultValue = DEFAULT_GAIN;
    private int mStepValue = STEP_SIZE;

    HalAudioDeviceInfoBuilder setAddress(String address) {
        mAddress = address;
        return this;
    }

    HalAudioDeviceInfoBuilder setName(String name) {
        mName = name;
        return this;
    }

    HalAudioDeviceInfoBuilder setType(int type) {
        mType = type;
        return this;
    }

    HalAudioDeviceInfoBuilder setId(int id) {
        mId = id;
        return this;
    }

    HalAudioDeviceInfoBuilder setMinValue(int minVal) {
        mMinValue = minVal;
        return this;
    }

    HalAudioDeviceInfoBuilder setMaxValue(int maxVal) {
        mMaxValue = maxVal;
        return this;
    }

    HalAudioDeviceInfoBuilder setDefaultValue(int defaultVal) {
        mDefaultValue = defaultVal;
        return this;
    }

    HalAudioDeviceInfoBuilder setStepValue(int stepVal) {
        mStepValue = stepVal;
        return this;
    }

    HalAudioDeviceInfo build() {
        AudioPortDeviceExt deviceExt = new AudioPortDeviceExt();
        deviceExt.device = new AudioDevice();
        deviceExt.device.type = new AudioDeviceDescription();
        deviceExt.device.type.type = mType;
        deviceExt.device.type.connection = CONNECTION_BUS;
        deviceExt.device.address = AudioDeviceAddress.id(mAddress);
        AudioPort audioPort = new AudioPort();
        audioPort.id = mId;
        audioPort.name = mName;
        audioPort.gains = new android.media.audio.common.AudioGain[] {
                new android.media.audio.common.AudioGain() {{
                    mode = JOINT;
                    minValue = mMinValue;
                    maxValue = mMaxValue;
                    defaultValue = mDefaultValue;
                    stepValue = mStepValue;
                }}
        };
        audioPort.ext = AudioPortExt.device(deviceExt);
        return new HalAudioDeviceInfo(audioPort);
    }
}
