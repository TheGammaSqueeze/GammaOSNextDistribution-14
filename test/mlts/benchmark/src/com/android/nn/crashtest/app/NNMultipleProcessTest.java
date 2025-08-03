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

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@RunWith(Parameterized.class)
public abstract class NNMultipleProcessTest
        extends ActivityInstrumentationTestCase2<NNMultiProcessTestActivity>
        implements AcceleratorSpecificTestSupport {
    private static final String TAG = "NNMultipleProcessTest";

    private final ExecutorService mDriverLivenessValidationExecutor =
            Executors.newSingleThreadExecutor();
    protected Optional<TestModels.TestModelEntry> mModelForLivenessTest;
    protected final String mAcceleratorName;
    protected final int mProcessCount;
    protected final int mThreadCount;
    protected final Duration mDuration;
    protected final int mFailureRatePercent;

    @Rule
    public TestName mTestName = new TestName();

    public NNMultipleProcessTest(int processCount, int threadCount, Duration duration,
            int failureRatePercent, String acceleratorName) {
        super(NNMultiProcessTestActivity.class);
        mProcessCount = processCount;
        mThreadCount = threadCount;
        mDuration = duration;
        mAcceleratorName = acceleratorName;
        mFailureRatePercent = failureRatePercent;
    }

    @Before
    @Override
    public void setUp() {
        injectInstrumentation(InstrumentationRegistry.getInstrumentation());
        BenchmarkTestBase.waitUntilCharged(getInstrumentation().getTargetContext(), 90);
        setActivityIntent(getRunModelsInMultipleProcessesConfigIntent());
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
        final NNMultiProcessTestActivity activity = getActivity();

        Optional<TestModels.TestModelEntry> modelForLivenessTest = findModelForLivenessTest();
        assertTrue("No model available to be run on accelerator " + mAcceleratorName,
                modelForLivenessTest.isPresent());

        final DriverLivenessChecker driverLivenessChecker =
                new DriverLivenessChecker(activity, mAcceleratorName, modelForLivenessTest.get());
        Future<Boolean> driverDidNotCrash =
                mDriverLivenessValidationExecutor.submit(driverLivenessChecker);

        assertEquals(SUCCESS, activity.testResult());
        driverLivenessChecker.stop();
        assertTrue("Driver shouldn't crash if used by multiple threads and processes",
                driverDidNotCrash.get());
    }

    /**
     * @return the intent to use to initialise the RunModelsInMultipleProcesses test class
     */
    protected abstract Intent getRunModelsInMultipleProcessesConfigIntent();
}