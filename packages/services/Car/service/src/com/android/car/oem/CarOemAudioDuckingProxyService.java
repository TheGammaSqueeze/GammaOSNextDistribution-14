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
package com.android.car.oem;

import android.annotation.NonNull;
import android.car.builtin.util.Slogf;
import android.car.oem.IOemCarAudioDuckingService;
import android.car.oem.OemCarAudioVolumeRequest;
import android.media.AudioAttributes;
import android.os.RemoteException;

import com.android.car.CarLog;
import com.android.internal.util.Preconditions;

import java.util.Collections;
import java.util.List;

/**
 * Provides functionality of the OEM Audio Ducking Service.
 */
public final class CarOemAudioDuckingProxyService {

    private static final String TAG = CarLog.tagFor(CarOemAudioDuckingProxyService.class);
    private static final String CALLER_TAG = CarLog.tagFor(CarOemAudioDuckingProxyService.class);

    private final CarOemProxyServiceHelper mHelper;
    private final IOemCarAudioDuckingService mOemCarAudioDuckingService;

    public CarOemAudioDuckingProxyService(CarOemProxyServiceHelper helper,
            IOemCarAudioDuckingService oemAudioDuckingService) {
        mHelper = helper;
        mOemCarAudioDuckingService = oemAudioDuckingService;
    }

    /**
     * Call to evaluate a ducking change.
     */
    @NonNull
    public List<AudioAttributes> evaluateAttributesToDuck(
            @NonNull OemCarAudioVolumeRequest requestInfo) {
        Preconditions.checkArgument(requestInfo != null,
                "Audio ducking evaluation request can not be null");
        return mHelper.doBinderTimedCallWithDefaultValue(CALLER_TAG,
                () -> {
                    try {
                        return mOemCarAudioDuckingService.evaluateAttributesToDuck(requestInfo);
                    } catch (RemoteException e) {
                        Slogf.e(TAG, e, "Ducking evaluation request " + requestInfo);
                    }
                    return Collections.EMPTY_LIST;
                }, Collections.EMPTY_LIST);
    }
}
