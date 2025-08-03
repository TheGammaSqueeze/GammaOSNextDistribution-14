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

import android.media.AudioFocusInfo;

import java.util.List;
import java.util.Objects;

/**
 * Class used to store information about the currently active and inactive foci
 */
final class AudioFocusStack {

    private final List<AudioFocusInfo> mActiveFocusList;
    private final List<AudioFocusInfo> mInactiveFocusList;

    AudioFocusStack(List<AudioFocusInfo> activeFocusList, List<AudioFocusInfo> inactiveFocusList) {
        Objects.requireNonNull(activeFocusList, "Active foci info must not be null");
        Objects.requireNonNull(inactiveFocusList, "Inactive foci info must not be null");
        mActiveFocusList = List.copyOf(activeFocusList);
        mInactiveFocusList = List.copyOf(inactiveFocusList);
    }

    List<AudioFocusInfo> getActiveFocusList() {
        return mActiveFocusList;
    }

    List<AudioFocusInfo> getInactiveFocusList() {
        return mInactiveFocusList;
    }
}
