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

import android.car.builtin.util.Slogf;
import android.os.IBinder;

import com.android.car.CarLog;
import com.android.internal.annotations.VisibleForTesting;

/**
 * Factory for constructing wrappers around IAudioControl HAL instances.
 */
public final class AudioControlFactory {

    @VisibleForTesting
    static final String TAG = CarLog.tagFor(AudioControlFactory.class);

    private AudioControlFactory() {
    }

    /**
     * Generates {@link AudioControlWrapper} for interacting with IAudioControl HAL service. The HAL
     * version priority is: Current AIDL, HIDL V2, HIDL V1. The wrapper will try to fetch the
     * highest priority service, and then fall back to older versions if it's not available. The
     * wrapper will throw if none is registered on the manifest.
     *
     * @return {@link AudioControlWrapper} for registered IAudioControl service.
     */
    public static AudioControlWrapper newAudioControl() {
        IBinder binder = AudioControlWrapperAidl.getService();
        if (binder != null) {
            return new AudioControlWrapperAidl(binder);
        }
        Slogf.i(TAG, "AIDL AudioControl HAL not in the manifest");

        android.hardware.automotive.audiocontrol.V2_0.IAudioControl audioControlV2 =
                AudioControlWrapperV2.getService();
        if (audioControlV2 != null) {
            return new AudioControlWrapperV2(audioControlV2);
        }
        Slogf.i(TAG, "HIDL AudioControl@V2.0 not in the manifest");

        android.hardware.automotive.audiocontrol.V1_0.IAudioControl audioControlV1 =
                AudioControlWrapperV1.getService();
        if (audioControlV1 != null) {
            Slogf.w(TAG, "HIDL AudioControl V1.0 is deprecated. Consider upgrading to AIDL");
            return new AudioControlWrapperV1(audioControlV1);
        }

        throw new IllegalStateException("No version of AudioControl HAL in the manifest");
    }
}
