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

import static android.car.media.CarAudioManager.PRIMARY_AUDIO_ZONE;
import static android.util.Log.DEBUG;

import static com.android.car.CarLog.TAG_AUDIO;

import android.car.builtin.util.Slogf;
import android.media.AudioManager;

import java.util.Objects;
import java.util.concurrent.Executor;

final class CoreAudioVolumeGroupCallback extends AudioManager.VolumeGroupCallback {
    static final String TAG = TAG_AUDIO + ".CoreAudioVolumeGroupCallback";

    private final CarVolumeInfoWrapper mCarVolumeInfoWrapper;
    private final AudioManager mAudioManager;

    CoreAudioVolumeGroupCallback(CarVolumeInfoWrapper carVolumeInfoWrapper,
            AudioManager audioManager) {
        mCarVolumeInfoWrapper = Objects.requireNonNull(carVolumeInfoWrapper,
                "CarVolumeInfoWrapper cannot be null");
        mAudioManager = Objects.requireNonNull(audioManager, "AudioManager cannot be null");
    }

    public void init(Executor executor) {
        mAudioManager.registerVolumeGroupCallback(executor, this);
        if (Slogf.isLoggable(TAG_AUDIO, DEBUG)) {
            Slogf.d(TAG, "Registered car audio volume group callback");
        }
    }

    public void release() {
        if (Slogf.isLoggable(TAG_AUDIO, DEBUG)) {
            Slogf.d(TAG, "Unregistering car audio volume group callback");
        }
        mAudioManager.unregisterVolumeGroupCallback(this);
    }

    @Override
    public void onAudioVolumeGroupChanged(int groupId, int flags) {
        int zoneId = PRIMARY_AUDIO_ZONE;
        if (Slogf.isLoggable(TAG_AUDIO, DEBUG)) {
            Slogf.d(TAG, "onAudioVolumeGroupChanged: volume group: %d", groupId);
        }
        String groupName = CoreAudioHelper.getVolumeGroupNameFromCoreId(groupId);
        if (groupName == null) {
            Slogf.w(TAG, "onAudioVolumeGroupChanged no group for id(%d)", groupId);
            return;
        }
        mCarVolumeInfoWrapper.onAudioVolumeGroupChanged(zoneId, groupName, flags);
    }
}
