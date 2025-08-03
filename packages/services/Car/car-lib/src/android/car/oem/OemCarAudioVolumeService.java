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

package android.car.oem;

import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.media.AudioManager;

/*
 * OemCarAudioVolumeService would expose all the method from IOemCarAudioVolumeService. It
 * should always be in sync with IOemCarAudioVolumeService. Oem will implement
 * OemCarAudioVolumeServiceInterface which would be used by OemCarAudioVolumeService.
 */

/**
 * Interface for audio volume for OEM Service.
 *
 * @hide
 */
@SystemApi
public interface OemCarAudioVolumeService extends OemCarServiceComponent {

    /**
     * Call to evaluate a volume change.
     *
     * <p>The adjustment request is one of {@link AudioManager#ADJUST_RAISE},
     * {@link AudioManager#ADJUST_LOWER}, {@link AudioManager#ADJUST_SAME},
     * {@link AudioManager#ADJUST_MUTE}, {@link AudioManager#ADJUST_UNMUTE},
     * {@link AudioManager#ADJUST_TOGGLE_MUTE}. The request info contains information to make a
     * decision about which volume should be selected for change.
     *
     * <p>The corresponding change will be determined by comparing the corresponding state
     * of the volume group info and the value returned here. For example, for a request to evaluate
     * {@code AudioManager#ADJUST_UNMUTE} the volume group info's mute state should be unmuted.
     *
     * @param requestInfo the current state of the audio service.
     * @param volumeAdjustment the current volume adjustment to evaluate.
     *
     * @return the selected volume group which should change.
     */
    @NonNull
    OemCarVolumeChangeInfo getSuggestedGroupForVolumeChange(
            @NonNull OemCarAudioVolumeRequest requestInfo, int volumeAdjustment);
}
