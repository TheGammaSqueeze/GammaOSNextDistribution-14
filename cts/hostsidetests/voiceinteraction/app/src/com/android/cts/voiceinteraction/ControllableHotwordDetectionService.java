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

package com.android.cts.voiceinteraction;

import static java.lang.annotation.RetentionPolicy.SOURCE;

import android.os.PersistableBundle;
import android.os.SharedMemory;
import android.service.voice.AlwaysOnHotwordDetector;
import android.service.voice.HotwordDetectedResult;
import android.service.voice.HotwordDetectionService;
import android.util.Log;

import androidx.annotation.StringDef;

import java.lang.annotation.Retention;
import java.util.function.IntConsumer;

/**
 * This {@link HotwordDetectionService} implementation is intended to have controllable behavior
 * through the {@link #onUpdateState} API.
 *
 * All controllable behaviors are under the {@link ServiceControlApis} definition.
 */
public class ControllableHotwordDetectionService extends HotwordDetectionService {
    private static final String TAG = ControllableHotwordDetectionService.class.getSimpleName();

    /**
     * Bundle keys for {@link #onUpdateState} which have special meaning to control the behavior
     * of the service.
     */
    @Retention(SOURCE)
    @StringDef({
            KEY_FORCE_HOTWORD_RESULT_PHRASE_ID,
    })
    public @interface ServiceControlApis {
    }

    // Key used to force the returned phrase ID for all onDetected events
    public static final String KEY_FORCE_HOTWORD_RESULT_PHRASE_ID =
            "FORCE_HOTWORD_RESULT_PHRASE_ID";

    // Current onUpdateState Options
    private int mHotwordResultPhraseId = 0;

    @Override
    public void onDetect(AlwaysOnHotwordDetector.EventPayload eventPayload, long timeoutMillis,
            Callback callback) {
        Log.d(TAG, "onDetect: eventPayload=" + eventPayload
                + ", timeoutMillis=" + timeoutMillis
                + ", callback=" + callback);
        callback.onDetected(new HotwordDetectedResult.Builder()
                .setHotwordPhraseId(mHotwordResultPhraseId)
                .build());
    }

    @Override
    public void onUpdateState(PersistableBundle options, SharedMemory sharedMemory,
            long callbackTimeoutMillis, IntConsumer statusCallback) {
        Log.d(TAG, "onUpdateState: options=" + options
                + ", sharedMemory=" + sharedMemory
                + ", callbackTimeoutMillis=" + callbackTimeoutMillis
                + ", statusCallback=" + statusCallback);
        mHotwordResultPhraseId = options.getInt(KEY_FORCE_HOTWORD_RESULT_PHRASE_ID);
        if (statusCallback != null) {
            statusCallback.accept(HotwordDetectionService.INITIALIZATION_STATUS_SUCCESS);
        }
    }
}
