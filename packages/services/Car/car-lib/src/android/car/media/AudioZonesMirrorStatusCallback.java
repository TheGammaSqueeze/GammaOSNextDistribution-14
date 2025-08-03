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
 * Callback for car audio zones mirror playback status
 *
 * @hide
 */
@SystemApi
public interface AudioZonesMirrorStatusCallback {

    /**
     * Called on audio zones mirror status changes
     *
     * @param mirroredAudioZones Audio zones where audio is being mirrored
     * @param status New status of the request, can be any of:
     *                {@link CarAudioManager#AUDIO_REQUEST_STATUS_APPROVED},
     *                {@link CarAudioManager#AUDIO_REQUEST_STATUS_CANCELLED},
     *                {@link CarAudioManager#AUDIO_REQUEST_STATUS_STOPPED}
     */
    void onAudioZonesMirrorStatusChanged(@NonNull List<Integer> mirroredAudioZones,
            @CarAudioManager.MediaAudioRequestStatus int status);
}
