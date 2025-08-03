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
import android.media.AudioFocusInfo;

import java.io.PrintWriter;
import java.util.List;

/**
 * This code will be running as part of the OEM Service. This implements
 * {@code IOemCarAudioFocusService} as hidden class. It is exposed as IBinder from {@link
 * OemCarService#getOemAudioFocusService()}
 *
 * @hide
 */
final class OemCarAudioFocusServiceImpl extends IOemCarAudioFocusService.Stub
        implements OemCarServiceComponent {

    private final OemCarAudioFocusService mOemCarAudioFocusService;

    OemCarAudioFocusServiceImpl(
            @NonNull OemCarAudioFocusService oemCarAudioFocusService) {
        mOemCarAudioFocusService = oemCarAudioFocusService;
    }

    @Override
    public void notifyAudioFocusChange(@NonNull List<AudioFocusInfo> currentFocusHolders,
            @NonNull List<AudioFocusInfo> currentFocusLosers, int zoneId) {
        mOemCarAudioFocusService.notifyAudioFocusChange(currentFocusHolders, currentFocusLosers,
                zoneId);
    }

    @Override
    @NonNull
    public OemCarAudioFocusResult evaluateAudioFocusRequest(
            @NonNull OemCarAudioFocusEvaluationRequest request) {
        return mOemCarAudioFocusService.evaluateAudioFocusRequest(request);
    }

    @Override
    public void init() {
        mOemCarAudioFocusService.init();
    }

    @Override
    public void release() {
        mOemCarAudioFocusService.release();
    }

    @Override
    public void dump(PrintWriter writer, String[] args) {
        mOemCarAudioFocusService.dump(writer, args);
    }

    @Override
    public void onCarServiceReady() {
        mOemCarAudioFocusService.onCarServiceReady();
    }
}
