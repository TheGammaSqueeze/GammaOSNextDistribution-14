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

package com.android.nn.benchmark.app;

import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.LargeTest;

import com.android.nn.benchmark.core.TestModels;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;

/**
 * NNAPI benchmark test.
 * To run the test, please use command
 *
 * adb shell am instrument
 * -e class "com.android.nn.benchmark.app.NNCrystalBallTest
 * -w com.android.nn.benchmark.app/androidx.test.runner.AndroidJUnitRunner
 *
 * To run only one model, please run:
 * adb shell am instrument
 * -e class "com.android.nn.benchmark.app.NNCrystalBallTest#testNNAPI[MODEL_NAME]"
 * -w com.android.nn.benchmark.app/androidx.test.runner.AndroidJUnitRunner
 *
 */
@RunWith(Parameterized.class)
public class NNCrystalBallTest extends BenchmarkTestBase {

    public NNCrystalBallTest(TestModels.TestModelEntry model, String acceleratorName) {
        super(model, acceleratorName);
    }

    private void test(boolean useNnapi, boolean useCompleteInputSet) throws IOException {
        setUseNNApi(useNnapi);
        setCompleteInputSet(useCompleteInputSet);
        if (useNnapi && mAcceleratorName != null) {
            Log.i(NNBenchmark.TAG, "Using accelerator " + mAcceleratorName);
            setNnApiAcceleratorName(mAcceleratorName);
        }
        enableCompilationCachingBenchmarks();
        TestAction ta = new TestAction(mModel, WARMUP_REPEATABLE_SECONDS,
            useCompleteInputSet ? COMPLETE_SET_TIMEOUT_SECOND : RUNTIME_REPEATABLE_SECONDS);
        runTest(ta, mModel.getTestName());

        // Sends metric results to the instrumentation status output.
        InstrumentationRegistry.getInstrumentation().sendStatus(
                0, ta.getBenchmark().toBundle(mModel.getTestName()));
    }

    @Test
    @LargeTest
    public void testTFLite() throws IOException {
        test(false, false);
    }

    @Test
    @LargeTest
    public void testNNAPI() throws IOException {
        test(true, true);
    }

}
