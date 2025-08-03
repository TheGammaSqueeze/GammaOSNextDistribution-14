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

package android.car.media;

import android.annotation.NonNull;
import android.annotation.SystemApi;

/**
 * Callback to informed about car audio zone configuration request results
 *
 * @hide
 */
@SystemApi
public interface SwitchAudioZoneConfigCallback {
    /**
     * Called when the car audio zone configuration is switched
     *
     * @param zoneConfig Car audio zone configuration to switch to
     * @param isSuccessful {@code true} if the audio config change is successful, {@code false}
     * otherwise (i.e. failed to register an audio policy to the audio service)
     */
    void onAudioZoneConfigSwitched(@NonNull CarAudioZoneConfigInfo zoneConfig,
            boolean isSuccessful);
}
