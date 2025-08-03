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

package android.voiceinteraction.cts.services;

import android.os.PersistableBundle;
import android.os.SharedMemory;
import android.service.voice.VisualQueryDetectionService;

import androidx.annotation.Nullable;

import java.util.function.IntConsumer;

/**
 * {@link VisualQueryDetectionService} implementation that just calls its super methods.
 */
public class NoOpVisualQueryDetectionService extends VisualQueryDetectionService {
    static final String TAG = "NoOpVisualQueryDetectionService";

    @Override
    public void onStartDetection() {
        super.onStartDetection();
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
