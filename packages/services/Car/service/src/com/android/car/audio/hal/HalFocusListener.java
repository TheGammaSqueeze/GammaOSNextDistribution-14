/*
 * Copyright (C) 2020 The Android Open Source Project
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

import android.hardware.audio.common.PlaybackTrackMetadata;

/**
 * Framework focus listener interface to abstract away the specific HAL version
 */
public interface HalFocusListener {
    /**
     * Requests audio focus for the specified {@code metadata}, {@code zoneId},
     * and {@code focusGain}.
     *
     * <p>The result will be communicated separately through
     * {@link AudioControlWrapper#onAudioFocusChange}.
     */
    void requestAudioFocus(PlaybackTrackMetadata metadata, int zoneId, int focusGain);

    /**
     * Abandons audio focus for the specified {@code metadata} and {@code zoneId}.
     *
     * <p>The result will be communicated separately through
     * {@link AudioControlWrapper#onAudioFocusChange}.
     */
    void abandonAudioFocus(PlaybackTrackMetadata metadata, int zoneId);
}
