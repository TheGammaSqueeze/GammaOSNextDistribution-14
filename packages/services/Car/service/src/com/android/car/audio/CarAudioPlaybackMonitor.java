/*
 * Copyright (C) 2024 The Android Open Source Project
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

import android.media.AudioAttributes;

import java.util.List;
import java.util.Objects;

public final class CarAudioPlaybackMonitor {

    private final CarAudioService mCarAudioService;

    CarAudioPlaybackMonitor(CarAudioService carAudioService) {
        mCarAudioService = Objects.requireNonNull(carAudioService,
                "Car audio service can not be null");
    }

    /**
     * Informs {@link CarAudioService} that newly active playbacks are received and min/max
     * activation volume should be applied if needed.
     *
     * @param newActivePlaybackAttributes List of {@link AudioAttributes} of the newly active
     *                                    {@link android.media.AudioPlaybackConfiguration}s
     * @param zoneId Zone Id of thr newly active playbacks
     */
    public void onActiveAudioPlaybackAttributesAdded(
            List<AudioAttributes> newActivePlaybackAttributes, int zoneId) {
        if (newActivePlaybackAttributes == null || newActivePlaybackAttributes.isEmpty()) {
            return;
        }
        mCarAudioService.handleActivationVolumeWithAudioAttributes(newActivePlaybackAttributes,
                zoneId);
    }
}
