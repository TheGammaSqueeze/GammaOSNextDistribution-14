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
import android.media.AudioAttributes;

import java.io.PrintWriter;
import java.util.List;

/**
 * This code will be running as part of the OEM Service. This implements
 * {@code IOemCarAudioDuckingService} as hidden class. It is exposed as IBinder from {@link
 * OemCarService#getOemAudioDuckingService()}
 *
 * @hide
 */
final class OemCarAudioDuckingServiceImpl extends IOemCarAudioDuckingService.Stub
        implements OemCarServiceComponent {

    private final OemCarAudioDuckingService mOemCarAudioDuckingService;

    OemCarAudioDuckingServiceImpl(@NonNull OemCarAudioDuckingService oemCarAudioDuckingService) {
        mOemCarAudioDuckingService = oemCarAudioDuckingService;
    }

    @Override
    @NonNull
    public List<AudioAttributes> evaluateAttributesToDuck(
            @NonNull OemCarAudioVolumeRequest request) {
        return mOemCarAudioDuckingService.evaluateAttributesToDuck(request);
    }

    @Override
    public void init() {
        mOemCarAudioDuckingService.init();
    }

    @Override
    public void release() {
        mOemCarAudioDuckingService.release();
    }

    @Override
    public void dump(PrintWriter writer, String[] args) {
        mOemCarAudioDuckingService.dump(writer, args);
    }

    @Override
    public void onCarServiceReady() {
        mOemCarAudioDuckingService.onCarServiceReady();
    }
}
