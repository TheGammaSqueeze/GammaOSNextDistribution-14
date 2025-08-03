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

package com.android.nn.crashtest.core.test;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.android.nn.crashtest.core.CrashTest;
import com.android.nn.crashtest.core.CrashTestCoordinator;

import java.util.Optional;

public class NoOpCrashTest implements CrashTest {
    private static final String SLEEP_TIME = "sleep-time";

    static public CrashTestCoordinator.CrashTestIntentInitializer intentInitializer(
            long sleepTime) {
        return intent -> {
            intent.putExtra(SLEEP_TIME, sleepTime);
        };
    }

    private long sleepTime = 0;

    @Override
    public void init(Context context, Intent configParams,
            Optional<ProgressListener> progressListener) {
        sleepTime = configParams.getLongExtra(SLEEP_TIME, 100);
    }

    @Override
    public Optional<String> call() {
        try {
            Thread.sleep(sleepTime);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        Log.i(CrashTest.TAG, "Completing test");

        return success();
    }
}
