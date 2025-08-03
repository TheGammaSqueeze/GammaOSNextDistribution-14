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

import java.io.PrintWriter;

/**
 * This code will be running as part of the OEM Service. This implements
 * {@code IOemCarAudioVolumeService} as hidden class. It is exposed as IBinder from {@link
 * OemCarService#getOemAudioVolumeService()}
 *
 * @hide
 */
final class OemCarAudioVolumeServiceImpl extends IOemCarAudioVolumeService.Stub
        implements OemCarServiceComponent {

    private final OemCarAudioVolumeService mOemCarAudioVolumeService;

    OemCarAudioVolumeServiceImpl(
            @NonNull OemCarAudioVolumeService oemCarAudioVolumeService) {
        mOemCarAudioVolumeService = oemCarAudioVolumeService;
    }

    @Override
    @NonNull
    public OemCarVolumeChangeInfo getSuggestedGroupForVolumeChange(
            @NonNull OemCarAudioVolumeRequest requestInfo, int volumeAdjustment) {
        return mOemCarAudioVolumeService.getSuggestedGroupForVolumeChange(requestInfo,
                volumeAdjustment);
    }

    @Override
    public void init() {
        mOemCarAudioVolumeService.init();
    }

    @Override
    public void release() {
        mOemCarAudioVolumeService.release();
    }

    @Override
    public void dump(PrintWriter writer, String[] args) {
        mOemCarAudioVolumeService.dump(writer, args);
    }

    @Override
    public void onCarServiceReady() {
        mOemCarAudioVolumeService.onCarServiceReady();
    }
}
