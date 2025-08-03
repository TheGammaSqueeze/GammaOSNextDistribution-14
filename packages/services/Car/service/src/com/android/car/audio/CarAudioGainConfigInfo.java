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

import android.hardware.automotive.audiocontrol.AudioGainConfigInfo;

import java.util.Objects;

/**
 * Audio Gain Config Information for a given Device based on its address.
 */
public class CarAudioGainConfigInfo {
    private final AudioGainConfigInfo mAudioGainConfigInfo;

    /**
     * Constructor of the car audio gain info configuration based on the {@link
     * AudioGainConfigInfo}.
     *
     * @param audioGainConfigInfo {@link AudioGainConfigInfo} to convert.
     * @return new car audio gain info
     */
    public CarAudioGainConfigInfo(AudioGainConfigInfo audioGainConfigInfo) {
        mAudioGainConfigInfo = audioGainConfigInfo;
    }

    public AudioGainConfigInfo getAudioGainConfigInfo() {
        return mAudioGainConfigInfo;
    }

    public int getZoneId() {
        return mAudioGainConfigInfo.zoneId;
    }

    public String getDeviceAddress() {
        return mAudioGainConfigInfo.devicePortAddress;
    }

    public int getVolumeIndex() {
        return mAudioGainConfigInfo.volumeIndex;
    }

    /** Returns the string representation of the car audio gain configuration */
    public String toString() {
        return "zone: "
                + getZoneId()
                + ", address: "
                + getDeviceAddress()
                + ", Volume Index: "
                + getVolumeIndex();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CarAudioGainConfigInfo)) {
            return false;
        }
        CarAudioGainConfigInfo other = (CarAudioGainConfigInfo) o;
        return getZoneId() == other.getZoneId()
                && getDeviceAddress().equals(other.getDeviceAddress())
                && getVolumeIndex() == other.getVolumeIndex();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getZoneId(), getDeviceAddress(), getVolumeIndex());
    }
}
