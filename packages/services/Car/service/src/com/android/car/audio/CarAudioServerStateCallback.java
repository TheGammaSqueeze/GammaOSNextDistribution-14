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
import android.media.AudioManager;

import com.android.car.CarLog;

import java.util.Objects;

final class CarAudioServerStateCallback extends AudioManager.AudioServerStateCallback {

    private static final String TAG = CarLog.TAG_AUDIO;
    private final CarAudioService mCarAudioService;

    CarAudioServerStateCallback(CarAudioService carAudioService) {
        mCarAudioService = Objects.requireNonNull(carAudioService,
                "Car audio service can not be null");
    }

    @Override
    public void onAudioServerDown() {
        Slogf.w(TAG, "Audio server died, setting audio as disabled");
        mCarAudioService.setAudioEnabled(false);
    }

    @Override
    public void onAudioServerUp() {
        Slogf.w(TAG, "Audio server up");
        mCarAudioService.release();
        // No need to re-enable audio as initialization will query the power policy
        // and enable as required.
        mCarAudioService.init();
    }
}
