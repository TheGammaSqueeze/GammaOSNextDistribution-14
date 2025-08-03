/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.voiceinteraction.cts.services;

import android.media.AudioFormat;
import android.os.ParcelFileDescriptor;
import android.os.PersistableBundle;
import android.os.SharedMemory;
import android.service.voice.AlwaysOnHotwordDetector;
import android.service.voice.HotwordDetectionService;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.function.IntConsumer;

/**
 * {@link HotwordDetectionService} implementation that just calls its super methods.
 */
public class NoOpHotwordDetectionService extends HotwordDetectionService {

    @Override
    public void onDetect(
            @NonNull AlwaysOnHotwordDetector.EventPayload eventPayload,
            long timeoutMillis,
            @NonNull Callback callback) {
        super.onDetect(eventPayload, timeoutMillis, callback);
    }

    @Override
    public void onDetect(
            @NonNull ParcelFileDescriptor audioStream,
            @NonNull AudioFormat audioFormat,
            @Nullable PersistableBundle options,
            @NonNull Callback callback) {
        super.onDetect(audioStream, audioFormat, options, callback);
    }

    @Override
    public void onDetect(@NonNull Callback callback) {
        super.onDetect(callback);
    }

    @Override
    public void onStopDetection() {
        super.onStopDetection();
    }

    @Override
    public void onUpdateState(
            @Nullable PersistableBundle options,
            @Nullable SharedMemory sharedMemory,
            long callbackTimeoutMillis,
            @Nullable IntConsumer statusCallback) {
        super.onUpdateState(options, sharedMemory, callbackTimeoutMillis, statusCallback);
    }
}
