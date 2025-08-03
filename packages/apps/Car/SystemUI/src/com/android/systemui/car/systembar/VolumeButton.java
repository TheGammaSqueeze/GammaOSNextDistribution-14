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

package com.android.systemui.car.systembar;

import static android.media.AudioAttributes.USAGE_MEDIA;
import static android.media.AudioManager.FLAG_SHOW_UI;

import android.car.Car;
import android.car.media.CarAudioManager;
import android.content.Context;
import android.util.AttributeSet;

public class VolumeButton extends CarSystemBarButton {
    public VolumeButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        Car car = Car.createCar(context);
        CarAudioManager carAudioManager = car.getCarManager(CarAudioManager.class);

        setOnClickListener(v -> {
            if (carAudioManager != null) {
                // todo(b/304797002): Use highest priority active group instead of USAGE_MEDIA
                int groupId = carAudioManager.getVolumeGroupIdForUsage(USAGE_MEDIA);
                carAudioManager.setGroupVolume(groupId, carAudioManager.getGroupVolume(groupId),
                        FLAG_SHOW_UI);
            }
        });
    }
}
