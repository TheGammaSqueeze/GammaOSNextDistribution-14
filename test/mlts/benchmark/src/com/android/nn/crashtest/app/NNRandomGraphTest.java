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

import static com.android.nn.crashtest.app.CrashTestStatus.TestResult.SUCCESS;

import android.content.Intent;
import android.test.ActivityInstrumentationTestCase2;
import android.test.UiThreadTest;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.LargeTest;

import com.android.nn.benchmark.app.AcceleratorSpecificTestSupport;
import com.android.nn.benchmark.app.BenchmarkTestBase;
import com.android.nn.benchmark.core.NnApiDelegationFailure;
import com.android.nn.benchmark.core.TestModels;
import com.android.nn.crashtest.core.test.RandomGraphTest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

@RunWith(Parameterized.class)
public abstract class NNRandomGraphTest
        extends ActivityInstrumentationTestCase2<NNRandomGraphTestActivity>
        implements AcceleratorSpecificTestSupport {
    private static final String TAG = "NN_RAND_MODEL";

    protected final String mAcceleratorName;
    private final int mModelCount;
    private final int mGraphSize;
    private final Duration mDuration;
    private final int mDimensionRange;

    private final static int SMALL_MODEL_SIZE = 10;
    private final static int LARGE_MODEL_SIZE = 600;
    private final static int NARROW_DIMENSIONS_RANGE = 10;
    private final static int WIDE_DIMENSIONS_RANGE = 1000;
    private final static Duration MAX_TEST_DURATION = Duration.ofMinutes(15);
    private final static int NUMBER_OF_MODELS = 600;
    private final boolean mRunModelCompilationOnly;

    @Parameters(name = "{0} models of size {1} and dimensions range {2} for max duration of {3} "
            + "on accelerator {4}")
    public static Iterable<Object[]> testConfiguration() {
        return AcceleratorSpecificTestSupport.perAcceleratorTestConfig(
                Arrays.asList(
                        new Object[]{NUMBER_OF_MODELS, SMALL_MODEL_SIZE, WIDE_DIMENSIONS_RANGE,
                                MAX_TEST_DURATION},
                        new Object[]{NUMBER_OF_MODELS, LARGE_MODEL_SIZE, NARROW_DIMENSIONS_RANGE,
                                MAX_TEST_DURATION}));
    }

    @Rule
    public TestName mTestName = new TestName();

    public NNRandomGraphTest(int modelCount, int graphSize, int dimensionRange,
            Duration duration, String acceleratorName, boolean runModelCompilationOnly) {
        super(NNRandomGraphTestActivity.class);
        mModelCount = modelCount;
        mGraphSize = graphSize;
        mDuration = duration;
        mAcceleratorName = acceleratorName;
        mDimensionRange = dimensionRange;
        mRunModelCompilationOnly = runModelCompilationOnly;
    }

    @Before
    @Override
    public void setUp() {
        injectInstrumentation(InstrumentationRegistry.getInstrumentation());
        BenchmarkTestBase.waitUntilCharged(getInstrumentation().getTargetContext(), 60);
        setActivityIntent(getTestModelsOfSizeAndRangeForMaxTimeIntent(mGraphSize, mDimensionRange,
                mModelCount, mAcceleratorName, mDuration, mTestName.getMethodName()));
    }

    protected Optional<TestModels.TestModelEntry> findModelForLivenessTest()
            throws NnApiDelegationFailure {
        return AcceleratorSpecificTestSupport.findTestModelRunningOnAccelerator(
                getInstrumentation().getTargetContext(), mAcceleratorName);
    }

    @Test
    @LargeTest
    @UiThreadTest
    public void testDriverDoesNotFailWithParallelWorkload()
            throws ExecutionException, InterruptedException, NnApiDelegationFailure {
        final NNRandomGraphTestActivity activity = getActivity();

        assertEquals(SUCCESS, activity.testResult());
    }

    /**
     * @return the intent to use to initialise the RandomGraphTest test class
     */
    protected Intent getTestModelsOfSizeAndRangeForMaxTimeIntent(int graphSize, int dimensionsRange,
            int modelsCount, String deviceName, Duration maxTestDuration, String testName) {
        Intent result = new Intent();
        RandomGraphTest
                .intentInitializer(graphSize, dimensionsRange, modelsCount,
                        RandomGraphTest.DEFAULT_PAUSE_BETWEEN_MODELS_MILLIS,
                        mRunModelCompilationOnly, deviceName, maxTestDuration.toMillis(), testName)
                .addIntentParams(result);
        return result;
    }
}