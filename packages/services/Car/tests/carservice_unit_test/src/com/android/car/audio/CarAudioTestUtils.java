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
import android.media.AudioFocusInfo;
import android.media.AudioManager;
import android.os.Build;


public final class CarAudioTestUtils {

    private static final String PACKAGE_NAME = "com.android.car.audio";
    private static final int AUDIOFOCUS_FLAG = 0;

    private CarAudioTestUtils() {
        throw new UnsupportedOperationException();
    }

    static AudioFocusInfo getInfo(AudioAttributes audioAttributes, String clientId, int gainType,
            boolean acceptsDelayedFocus, boolean pauseInsteadOfDucking, int uid) {
        int flags = AUDIOFOCUS_FLAG;
        if (acceptsDelayedFocus) {
            flags |= AudioManager.AUDIOFOCUS_FLAG_DELAY_OK;
        }
        if (pauseInsteadOfDucking) {
            flags |= AudioManager.AUDIOFOCUS_FLAG_PAUSES_ON_DUCKABLE_LOSS;
        }
        return new AudioFocusInfo(audioAttributes, uid, clientId, PACKAGE_NAME,
                gainType, AudioManager.AUDIOFOCUS_NONE,
                flags, Build.VERSION.SDK_INT);
    }
}
