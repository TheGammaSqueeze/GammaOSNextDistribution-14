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

import java.util.List;

/**
 * Interface to listen to events impacting volume groups.
 *
 * @hide
 */
@SystemApi
public interface CarVolumeGroupEventCallback {
    /**
     * This is called when audio framework acts on events that causes changes to
     * {@link android.car.media.CarVolumeGroupInfo}.
     * The callback includes a list of {@link android.car.media.CarVolumeGroupEvent}.
     * Each event contains event-types (what has changed), list of volume group infos
     * and additional information (why it has changed).
     */
    void onVolumeGroupEvent(@NonNull List<CarVolumeGroupEvent> volumeGroupEvents);
}
