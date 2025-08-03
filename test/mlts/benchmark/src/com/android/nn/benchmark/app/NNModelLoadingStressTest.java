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

package com.android.nn.benchmark.app;

import androidx.test.filters.LargeTest;

import com.android.nn.benchmark.core.TestModels;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Stopwatch;
import org.junit.runners.Parameterized.Parameters;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Tests that ensure stability of NNAPI by loading models for a prolonged
 * period of time.
 */
public class NNModelLoadingStressTest extends BenchmarkTestBase {
    private static final String TAG = NNModelLoadingStressTest.class.getSimpleName();

    private static final float WARMUP_SECONDS = 0; // No warmup.
    private static final float INFERENCE_SECONDS = 0; // No inference.
    private static final float RUNTIME_SECONDS = 30 * 60;

    @Rule public Stopwatch stopwatch = new Stopwatch() {};

    public NNModelLoadingStressTest(TestModels.TestModelEntry model, String acceleratorName) {
        super(model, acceleratorName);
    }

    @Parameters(name = "{0} model on accelerator {1}")
    public static List<Object[]> modelsList() {
        return BenchmarkTestBase.modelsOnAccelerators().stream()
            .map( modelAndAccelerator -> {
                TestModels.TestModelEntry modelEntry =
                    (TestModels.TestModelEntry)modelAndAccelerator[0];
                return new Object[] { modelEntry.withDisabledEvaluation(), modelAndAccelerator[1] };
            })
            .collect(Collectors.collectingAndThen(
                Collectors.toList(),
                Collections::unmodifiableList));
    }

    @Test
    @LargeTest
    public void stressTestNNAPI() throws IOException {
        waitUntilCharged();
        setUseNNApi(true);
        setCompleteInputSet(true);
        float endTime = stopwatch.runtime(TimeUnit.SECONDS) + RUNTIME_SECONDS;
        TestAction ta = new TestAction(mModel, WARMUP_SECONDS, INFERENCE_SECONDS);
        while (stopwatch.runtime(TimeUnit.SECONDS) < endTime) {
            runTest(ta, mModel.getTestName());
        }
    }
}
