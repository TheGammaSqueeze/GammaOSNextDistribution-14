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

import android.car.builtin.util.Slogf;
import android.car.media.CarVolumeGroupEvent;
import android.util.SparseArray;

import com.android.car.CarLog;
import com.android.car.audio.hal.AudioControlWrapper;
import com.android.car.audio.hal.HalAudioDeviceInfo;
import com.android.car.audio.hal.HalAudioModuleChangeCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Helper class to set, clear and handle audio hardware module callbacks
 */
final class CarAudioModuleChangeMonitor {

    private final AudioControlWrapper mAudioControlWrapper;
    private final CarVolumeInfoWrapper mCarVolumeInfoWrapper;
    // [key, value] -> [zone id, CarAudioZone]
    private final SparseArray<CarAudioZone> mCarAudioZones;

    CarAudioModuleChangeMonitor(AudioControlWrapper audioControlWrapper,
            CarVolumeInfoWrapper carVolumeInfoWrapper, SparseArray<CarAudioZone> carAudioZones) {
        mAudioControlWrapper =
                Objects.requireNonNull(
                        audioControlWrapper, "Audio control wrapper can not be null");
        mCarVolumeInfoWrapper = Objects.requireNonNull(carVolumeInfoWrapper,
                "Car volume info wrapper can not be null");
        mCarAudioZones = Objects.requireNonNull(carAudioZones, "Car audio zones can not be null");
    }

    /**
     * Sets {@code HalAudioModuleChangeCallback} on {@code AudioControlWrapper} to receive
     * callbacks for audio hardware changes.
     */
    void setModuleChangeCallback(HalAudioModuleChangeCallback callback) {
        Objects.requireNonNull(callback, "Hal audio module change callback can not be null");
        mAudioControlWrapper.setModuleChangeCallback(callback);
    }

    /**
     * Clears (any) {@code HalAudioModuleChangeCallback} from {@code AudioControlWrapper}
     */
    void clearModuleChangeCallback() {
        mAudioControlWrapper.clearModuleChangeCallback();
    }

    /**
     * Handles incoming list of updated {@code HalAudioDeviceInfo}.
     * If the changes result in volume group info updates (min/max/current),
     * trigger volume group event(s) callback on the listeners.
     */
    void handleAudioPortsChanged(List<HalAudioDeviceInfo> deviceInfos) {
        List<CarVolumeGroupEvent> events = new ArrayList<>();
        for (int i = 0; i < mCarAudioZones.size(); i++) {
            CarAudioZone zone = mCarAudioZones.valueAt(i);
            events.addAll(zone.onAudioPortsChanged(deviceInfos));
        }

        // its possible we received redundant callbacks from hal. In such cases,
        // do not call listeners with empty events.
        if (events.isEmpty()) {
            Slogf.w(CarLog.TAG_AUDIO, "Audio ports changed callback resulted in no events!");
            return;
        }
        mCarVolumeInfoWrapper.onVolumeGroupEvent(events);
    }
}
