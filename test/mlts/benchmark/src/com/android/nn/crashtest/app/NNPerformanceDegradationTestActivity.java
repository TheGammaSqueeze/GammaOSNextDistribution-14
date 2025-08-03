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

package com.android.nn.crashtest.app;

import android.content.Intent;

import com.android.nn.benchmark.core.TestModels;
import com.android.nn.crashtest.core.CrashTest;
import com.android.nn.crashtest.core.CrashTestCoordinator;
import com.android.nn.crashtest.core.test.PerformanceDegradationTest;

public class NNPerformanceDegradationTestActivity extends NNCrashTestActivity {
    private static final String TAG = NNPerformanceDegradationTest.TAG;

    @Override
    protected String getTag() {
        return TAG;
    }

    @Override
    protected String getTestName(Intent intent) {
        return intent.getStringExtra(PerformanceDegradationTest.TEST_NAME);
    }

    @Override
    protected long getTestDurationMillis(Intent intent) {
        final float testBenchmarkRuntimeSeconds = intent.getFloatExtra(
                PerformanceDegradationTest.RUN_TIME_SECONDS,
                PerformanceDegradationTest.DEFAULT_RUN_TIME_SECONDS);
        final float testBenchmarkWarmupTimeSeconds = intent.getFloatExtra(
                PerformanceDegradationTest.WARMUP_SECONDS,
                PerformanceDegradationTest.DEFAULT_WARMUP_SECONDS);
        // Two cycles of performance measurement are taken, single and multi-threaded,
        // with a pause in the middle repeated for every available model. We are assuming all are
        // available
        long oneModelTestDuration =
                (long) (testBenchmarkRuntimeSeconds + testBenchmarkWarmupTimeSeconds) * 1000 * 3;
        return oneModelTestDuration * TestModels.modelsList().size();
    }

    @Override
    protected CrashTestCoordinator.CrashTestIntentInitializer getIntentInitializer(Intent intent) {
        return PerformanceDegradationTest.intentInitializer(intent);
    }

    @Override
    protected Class<? extends CrashTest> getTestClass() {
        return PerformanceDegradationTest.class;
    }
}