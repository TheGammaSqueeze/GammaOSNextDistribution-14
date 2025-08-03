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

package com.android.nn.crashtest.core;

import android.content.Context;
import android.content.Intent;

import java.util.Optional;
import java.util.concurrent.Callable;

public interface CrashTest extends Callable<Optional<String>> {
    String TAG = "CrashTest";

    interface ProgressListener {
        // Notifies the listener that the crash test progressed for another step providing an
        // optional description.
        void testProgress(Optional<String> message);
    }

    void init(Context context, Intent configParams, Optional<ProgressListener> progressListener);

    default Optional<String> success() { return Optional.empty(); }
    default Optional<String> failure(String reason) { return Optional.of(reason); }
    default boolean isFailure(Optional<String> result) { return result.isPresent(); }
}
