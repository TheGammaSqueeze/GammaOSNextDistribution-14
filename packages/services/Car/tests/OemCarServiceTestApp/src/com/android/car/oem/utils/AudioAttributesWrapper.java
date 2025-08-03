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

/**
 * Class wraps an audio attributes object. This can be used for comparing audio attributes.
 *
 * <p>Currently the audio attributes class compares all the attributes in the two objects.
 * In automotive only the audio attribute usage is currently used, thus this class can be used
 * to compare that audio attribute usage.
 */
public final class AudioAttributesWrapper {

    private final AudioAttributes mAudioAttributes;

    public AudioAttributesWrapper(AudioAttributes audioAttributes) {
        mAudioAttributes = audioAttributes;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (!(object instanceof AudioAttributesWrapper)) {
            return false;
        }

        AudioAttributesWrapper that = (AudioAttributesWrapper) object;

        return mAudioAttributes.getSystemUsage() == that.mAudioAttributes.getSystemUsage();
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(mAudioAttributes.getSystemUsage());
    }

    @Override
    public String toString() {
        return mAudioAttributes.toString();
    }

    /**
     * Returns the audio attributes for the wrapper
     */
    public AudioAttributes getAudioAttributes() {
        return mAudioAttributes;
    }
}
