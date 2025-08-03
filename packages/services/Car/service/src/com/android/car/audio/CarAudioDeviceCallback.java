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

import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;

import java.util.Objects;

/**
 * Class to allow for car audio service to listen to audio device info updates.
 * This can be used to monitor changes to dynamic devices as seen by the audio service.
 */
final class CarAudioDeviceCallback extends AudioDeviceCallback {

    private final CarAudioService mCarAudioService;

    CarAudioDeviceCallback(CarAudioService carAudioService) {
        mCarAudioService = Objects.requireNonNull(carAudioService,
                "Car audio service can not be null");
    }

    /**
     * see {@link AudioDeviceCallback#onAudioDevicesAdded(AudioDeviceInfo[])}
     */
    @Override
    public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {
        mCarAudioService.audioDevicesAdded(addedDevices);
    }

    /**
     * see {@link AudioDeviceCallback#onAudioDevicesRemoved(AudioDeviceInfo[])}
     */
    @Override
    public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {
        mCarAudioService.audioDevicesRemoved(removedDevices);
    }
}
