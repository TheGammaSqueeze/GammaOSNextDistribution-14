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

package com.android.car.oem.utils;

import android.media.AudioAttributes;

public final class AudioUtils {

    private AudioUtils() {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns an audio attribute for a given usage
     *
     * @param usage input usage, can be an audio attribute system usage
     */
    public static AudioAttributes getAudioAttributeFromUsage(int usage) {
        AudioAttributes.Builder builder = new AudioAttributes.Builder();
        if (AudioAttributes.isSystemUsage(usage)) {
            builder.setSystemUsage(usage);
        } else {
            builder.setUsage(usage);
        }
        return builder.build();
    }

    public static String getUsageString(AudioAttributes incoming) {
        return AudioAttributes.usageToString(incoming.getSystemUsage()).replace(
                /* target= */ "USAGE_", /* replacement= */ "");
    }
}
