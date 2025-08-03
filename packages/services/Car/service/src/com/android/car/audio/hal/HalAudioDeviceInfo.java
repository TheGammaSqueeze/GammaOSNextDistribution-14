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

package com.android.car.audio.hal;

import static android.media.audio.common.AudioDeviceDescription.CONNECTION_BUS;
import static android.media.audio.common.AudioDeviceType.IN_DEVICE;
import static android.media.audio.common.AudioDeviceType.OUT_DEVICE;
import static android.media.audio.common.AudioGainMode.JOINT;

import android.annotation.NonNull;
import android.media.audio.common.AudioDevice;
import android.media.audio.common.AudioGain;
import android.media.audio.common.AudioPort;

import com.android.internal.util.Preconditions;

import java.util.Objects;

/**
 * Audio Device info received from HAL as part of dynamic gain stage configration
 */
public final class HalAudioDeviceInfo {
    private final int mId;
    private final String mName;
    private final AudioGain mAudioGain;
    private final int mType;
    private final String mConnection;
    private final String mAddress;
    private static final int AUDIO_PORT_EXT_DEVICE = 1;

    public HalAudioDeviceInfo(AudioPort port) {
        Objects.requireNonNull(port, "Audio port can not be null");

        Preconditions.checkArgument(port.ext.getTag() == AUDIO_PORT_EXT_DEVICE,
                "Invalid audio port ext setting: %d", port.ext.getTag());
        AudioDevice device = Objects.requireNonNull(port.ext.getDevice().device,
                "Audio device can not be null");
        checkIfAudioDeviceIsValidOutputBus(device);

        mId = port.id;
        mName = port.name;
        mAudioGain = getAudioGain(port.gains);
        mType = device.type.type;
        mConnection = device.type.connection;
        mAddress = device.address.getId();
    }

    public int getId() {
        return mId;
    }

    public String getName() {
        return mName;
    }

    public int getGainMinValue() {
        return mAudioGain.minValue;
    }

    public int getGainMaxValue() {
        return mAudioGain.maxValue;
    }

    public int getGainDefaultValue() {
        return mAudioGain.defaultValue;
    }

    public int getGainStepValue() {
        return mAudioGain.stepValue;
    }

    public int getType() {
        return mType;
    }

    public String getConnection() {
        return mConnection;
    }

    public String getAddress() {
        return mAddress;
    }

    public boolean isOutputDevice() {
        return mType == OUT_DEVICE;
    }

    public boolean isInputDevice() {
        return mType == IN_DEVICE;
    }

    @Override
    public String toString() {
        return new StringBuilder()
                .append("{mId: ").append(mId).append(", mName: ").append(mName)
                .append(", mAudioGain: ").append(Objects.toString(mAudioGain))
                .append(", mType: ").append(mType).append(", mConnection: ").append(mConnection)
                .append(", mAddress: ").append(mAddress).append("}").toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof HalAudioDeviceInfo)) {
            return false;
        }

        HalAudioDeviceInfo rhs = (HalAudioDeviceInfo) o;

        // mId is not reliable until Audio HAL migrates to AIDL
        return mType == rhs.mType && mName.equals(rhs.mName) && mConnection.equals(rhs.mConnection)
                && mAddress.equals(rhs.mAddress) && Objects.equals(mAudioGain, rhs.mAudioGain);
    }

    @Override
    public int hashCode() {
        // mId is not reliable until Audio HAL migrates to AIDL
        return Objects.hash(mName, mAudioGain, mType, mConnection, mAddress);
    }

    private void checkIfAudioDeviceIsValidOutputBus(AudioDevice device) {
        Preconditions.checkArgument((device.type.type == OUT_DEVICE)
                        || (device.type.type == IN_DEVICE),
                "Invalid audio device type (expecting IN/OUT_DEVICE): %d", device.type.type);

        Preconditions.checkArgument(device.type.connection.equals(CONNECTION_BUS),
                "Invalid audio device connection (expecting CONNECTION_BUS): %s",
                device.type.connection);

        Preconditions.checkStringNotEmpty(device.address.getId(),
                "Audio device address cannot be empty");
    }

    private static AudioGain getAudioGain(@NonNull AudioGain[] gains) {
        Objects.requireNonNull(gains, "Audio gains can not be null");
        Preconditions.checkArgument(gains.length > 0, "Audio port must have gains defined");
        for (int index = 0; index < gains.length; index++) {
            AudioGain gain = Objects.requireNonNull(gains[index], "Audio gain can not be null");
            if (gain.mode == JOINT) {
                return checkAudioGainConfiguration(gain);
            }
        }
        throw new IllegalStateException("Audio port does not have a valid audio gain");
    }

    private static AudioGain checkAudioGainConfiguration(AudioGain gain) {
        Preconditions.checkArgument(gain.maxValue >= gain.minValue,
                "Max gain %d is lower than min gain %d",
                gain.maxValue, gain.minValue);
        Preconditions.checkArgument((gain.defaultValue >= gain.minValue)
                        && (gain.defaultValue <= gain.maxValue),
                "Default gain %d not in range (%d,%d)", gain.defaultValue,
                gain.minValue, gain.maxValue);
        Preconditions.checkArgument(gain.stepValue > 0,
                "Gain step value must be greater than zero: %d", gain.stepValue);
        Preconditions.checkArgument(
                ((gain.maxValue - gain.minValue) % gain.stepValue) == 0,
                "Gain step value %d greater than min gain to max gain range %d",
                gain.stepValue, gain.maxValue - gain.minValue);
        Preconditions.checkArgument(
                ((gain.defaultValue - gain.minValue) % gain.stepValue) == 0,
                "Gain step value %d greater than min gain to default gain range %d",
                gain.stepValue, gain.defaultValue - gain.minValue);
        return gain;
    }
}
