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
import android.os.RemoteException;
import android.test.ActivityInstrumentationTestCase2;
import android.test.UiThreadTest;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.LargeTest;

import com.android.nn.benchmark.app.AcceleratorSpecificTestSupport;
import com.android.nn.benchmark.core.NnApiDelegationFailure;
import com.android.nn.benchmark.core.TestModels;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

@RunWith(Parameterized.class)
public class NNMemoryMappedModelCompilationTest extends
        ActivityInstrumentationTestCase2<NNParallelTestActivity> implements
    AcceleratorSpecificTestSupport {

    private static final String TAG = "NNMemoryMappedModelCompilation";
    private static final Duration MAX_SEPARATE_PROCESS_EXECUTION_TIME = Duration.ofSeconds(70);
    public static final int NNAPI_CLIENTS_COUNT = 4;

    private final ExecutorService mDriverLivenessValidationExecutor =
            Executors.newSingleThreadExecutor();
    private final String mAcceleratorName;

    @Rule
    public TestName mTestName = new TestName();

    public NNMemoryMappedModelCompilationTest(String acceleratorName) {
        super(NNParallelTestActivity.class);
        mAcceleratorName = acceleratorName;
    }

    @Parameters(name = "Accelerator({0})")
    public static Iterable<String> targetAccelerators() {
        return AcceleratorSpecificTestSupport.getTargetAcceleratorNames();
    }

    @Before
    @Override
    public void setUp() {
        injectInstrumentation(InstrumentationRegistry.getInstrumentation());
        final Intent runSomeInferencesInASeparateProcess;
        try {
            runSomeInferencesInASeparateProcess = compileSupportedModelsMemoryMappedOnNThreadsFor(
                    NNAPI_CLIENTS_COUNT,
                    MAX_SEPARATE_PROCESS_EXECUTION_TIME);
        } catch (NnApiDelegationFailure nnApiDelegationFailure) {
            throw new RuntimeException(
                    "Cannot initialize test, failure looking for supported models, please check "
                            + "the driver status",
                    nnApiDelegationFailure);
        }
        setActivityIntent(runSomeInferencesInASeparateProcess);
    }

    @Test
    @LargeTest
    @UiThreadTest
    public void testDriverDoesNotFailWithParallelThreads()
            throws ExecutionException, InterruptedException, RemoteException,
            NnApiDelegationFailure {
        final NNParallelTestActivity activity = getActivity();

        Optional<TestModels.TestModelEntry> modelForLivenessTest =
                AcceleratorSpecificTestSupport.findTestModelRunningOnAccelerator(activity, mAcceleratorName);
        assertTrue("No model available to be run on accelerator " + mAcceleratorName,
                modelForLivenessTest.isPresent());

        // This should not be necessary
        final DriverLivenessChecker driverLivenessChecker = new DriverLivenessChecker(activity,
                mAcceleratorName, modelForLivenessTest.get());
        Future<Boolean> driverDidNotCrash = mDriverLivenessValidationExecutor.submit(
                driverLivenessChecker);

        Assert.assertEquals(CrashTestStatus.TestResult.SUCCESS, activity.testResult());

        driverLivenessChecker.stop();
        assertTrue("Driver shouldn't have crashed", driverDidNotCrash.get());
    }

    private Intent compileSupportedModelsMemoryMappedOnNThreadsFor(int threadCount,
            Duration testDuration) throws NnApiDelegationFailure {
        Intent intent = new Intent();
        int modelsCount = TestModels.modelsList().size();
        intent.putExtra(NNParallelTestActivity.EXTRA_TEST_LIST, IntStream.range(0, modelsCount).toArray());
        intent.putExtra(NNParallelTestActivity.EXTRA_THREAD_COUNT, threadCount);
        intent.putExtra(NNParallelTestActivity.EXTRA_TEST_DURATION_MILLIS, testDuration.toMillis());
        intent.putExtra(NNParallelTestActivity.EXTRA_RUN_IN_SEPARATE_PROCESS, true);
        intent.putExtra(NNParallelTestActivity.EXTRA_TEST_NAME, mTestName.getMethodName());
        intent.putExtra(NNParallelTestActivity.EXTRA_ACCELERATOR_NAME, mAcceleratorName);
        intent.putExtra(NNParallelTestActivity.EXTRA_RUN_MODEL_COMPILATION_ONLY, true);
        intent.putExtra(NNParallelTestActivity.EXTRA_MEMORY_MAP_MODEL, true);
        intent.putExtra(NNParallelTestActivity.EXTRA_IGNORE_UNSUPPORTED_MODELS, true);
        return intent;
    }
}
