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

public class CrashingCrashTest implements CrashTest {

    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("nnbenchmark_jni");
    }

    private static final String SLEEP_TIME = "sleep-time";
    private static final String SEGV = "cause-segv";

    static public CrashTestCoordinator.CrashTestIntentInitializer intentInitializer(long sleepTime,
            boolean causeSegv) {
        return intent -> {
            intent.putExtra(SLEEP_TIME, sleepTime);
            intent.putExtra(SEGV, causeSegv);
        };
    }

    long sleepTime = 0;
    boolean segv = false;

    @Override
    public void init(Context context, Intent configParams,
            Optional<ProgressListener> progressListener) {
        sleepTime = configParams.getLongExtra(SLEEP_TIME, 100);
        segv = configParams.getBooleanExtra(SEGV, false);
    }

    @Override
    public Optional<String> call() {
        try {
            Thread.sleep(sleepTime);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        if (segv) {
            Log.i(CrashTest.TAG, "Causing NATIVE crash of test");
            nativeSegViolation();
        } else {
            Log.i(CrashTest.TAG, "Causing crash of test");
            System.exit(-1);
        }

        Log.e(CrashTest.TAG, "Test was supposed to crash but run until the end");
        return failure("Test was supposed to crash but run until the end");
    }

    native void nativeSegViolation();
}
