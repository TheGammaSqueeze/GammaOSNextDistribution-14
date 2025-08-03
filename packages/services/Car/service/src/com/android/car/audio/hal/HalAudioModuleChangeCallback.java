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

package com.android.car.audio.hal;

import java.util.List;

/**
 * Audio Module Change Callback interface to abstract away the specific HAL version
 */
public interface HalAudioModuleChangeCallback {

    /**
     * Notifies changes to Audio Ports for the given {@code audioPorts}
     * @param deviceInfos list of updated {@link HalAudioDeviceInfo}
     */
    void onAudioPortsChanged(List<HalAudioDeviceInfo> deviceInfos);
}
