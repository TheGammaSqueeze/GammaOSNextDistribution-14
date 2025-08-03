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

import android.car.media.CarVolumeGroupEvent;

/**
 * Binder interface to callback the volume group info changes.
 *
 * @hide
 */
oneway interface ICarVolumeEventCallback {
    /**
     * This is called when audio framework acts on events that causes changes to
     * {@link android.car.media.CarVolumeGroupInfo}.
     * The callback includes a list of {@link android.car.media.CarVolumeGroupEvent}.
     * Each event contains event-types (what has changed), list of volume group infos
     * and additional information (why it has changed).
     */
    void onVolumeGroupEvent(in List<CarVolumeGroupEvent> volumeGroupEvents);

    /**
     * This is called whenever the master mute state is changed.
     * The changed-to master mute state is not included, the caller is encouraged to
     * get the current master mute state via {@link android.media.AudioManager}.
     */
    void onMasterMuteChanged(int zoneId, int flags);
}
