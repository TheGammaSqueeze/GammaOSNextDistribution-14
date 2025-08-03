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

import android.car.media.CarAudioZoneConfigInfo;

/**
 * Binder interface to callback when audio zone configuration changes occur
 *
 * @hide
 */
oneway interface IAudioZoneConfigurationsChangeCallback {
    /**
     * Called on audio configuration updates
     *
     * <p>Can be used to determine when audio configurations for dynamic devices have become active,
     * inactive, due to a dynamic devices changing connected status.
     *
     * <p>Configuration changes relating to selections via the
     * {@link CarAudioManager#switchAudioZoneToConfig} API will not be reported via this callback.
     * Instead, {@link ISwitchAudioZoneConfigCallback} will be used.
     *
     * @param configs List of configuration whose status has changed
     * @param status Status that has changed, can be any of
     *   {@link CarAudioManager#CONFIG_STATUS_CHANGED}
     *   or {@link CarAudioManager#CONFIG_STATUS_AUTO_SWITCHED}
     */
    void onAudioZoneConfigurationsChanged(in List<CarAudioZoneConfigInfo> configs, int status);
}
