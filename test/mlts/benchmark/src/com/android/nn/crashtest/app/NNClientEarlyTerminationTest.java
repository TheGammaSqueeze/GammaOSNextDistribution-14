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
import com.android.nn.benchmark.core.NNTestBase;
import com.android.nn.benchmark.core.NnApiDelegationFailure;
import com.android.nn.benchmark.core.TestModels;

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
public class NNClientEarlyTerminationTest extends
        ActivityInstrumentationTestCase2<NNParallelTestActivity> implements
    AcceleratorSpecificTestSupport {

    private static final String TAG = "NNClientEarlyTermination";
    private static final Duration MAX_SEPARATE_PROCESS_EXECUTION_TIME = Duration.ofSeconds(70);
    public static final int NNAPI_CLIENTS_COUNT = 4;

    private final ExecutorService mDriverLivenessValidationExecutor =
            Executors.newSingleThreadExecutor();
    private final String mAcceleratorName;

    @Rule
    public TestName mTestName = new TestName();

    public NNClientEarlyTerminationTest(String acceleratorName) {
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
        try {
            final Intent runSomeInferencesInASeparateProcess = compileSupportedModelsOnNThreadsFor(
                    NNAPI_CLIENTS_COUNT, MAX_SEPARATE_PROCESS_EXECUTION_TIME);
            setActivityIntent(runSomeInferencesInASeparateProcess);
        } catch (NnApiDelegationFailure nnApiDelegationFailure) {
            throw new RuntimeException(
                    "Cannot initialize test, failure looking for supported models, please check "
                            + "the driver status",
                    nnApiDelegationFailure);
        }
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

        final DriverLivenessChecker driverLivenessChecker = new DriverLivenessChecker(activity,
                mAcceleratorName, modelForLivenessTest.get());
        Future<Boolean> driverDidNotCrash = mDriverLivenessValidationExecutor.submit(
                driverLivenessChecker);

        // Causing failure before tests would end on their own.
        final long maxTerminationTime = MAX_SEPARATE_PROCESS_EXECUTION_TIME.toMillis() / 2;
        final long minTerminationTime = MAX_SEPARATE_PROCESS_EXECUTION_TIME.toMillis() / 4;
        Thread.sleep(ramdomInRange(minTerminationTime, maxTerminationTime));

        try {
            activity.killTestProcess();
        } catch (RemoteException e) {
            driverLivenessChecker.stop();
            throw e;
        }

        CrashTestStatus.TestResult testResult = activity.testResult();
        driverLivenessChecker.stop();

        assertEquals("Remote process is expected to be killed",
                CrashTestStatus.TestResult.CRASH,
                testResult);

        assertTrue("Driver shouldn't crash if a client process is terminated",
                driverDidNotCrash.get());
    }

    private Intent compileSupportedModelsOnNThreadsFor(int threadCount, Duration testDuration)
        throws NnApiDelegationFailure {
        Intent intent = new Intent();
        intent.putExtra(
            NNParallelTestActivity.EXTRA_TEST_LIST, IntStream.range(0,
                TestModels.modelsList().size()).toArray());
        intent.putExtra(NNParallelTestActivity.EXTRA_THREAD_COUNT, threadCount);
        intent.putExtra(NNParallelTestActivity.EXTRA_TEST_DURATION_MILLIS, testDuration.toMillis());
        intent.putExtra(NNParallelTestActivity.EXTRA_RUN_IN_SEPARATE_PROCESS, true);
        intent.putExtra(NNParallelTestActivity.EXTRA_TEST_NAME, mTestName.getMethodName());
        intent.putExtra(NNParallelTestActivity.EXTRA_ACCELERATOR_NAME, mAcceleratorName);
        intent.putExtra(NNParallelTestActivity.EXTRA_IGNORE_UNSUPPORTED_MODELS, true);
        intent.putExtra(NNParallelTestActivity.EXTRA_RUN_MODEL_COMPILATION_ONLY, true);
        intent.putExtra(NNParallelTestActivity.EXTRA_USE_NNAPI_SL,
            NNTestBase.shouldUseNnApiSupportLibrary());
        intent.putExtra(NNParallelTestActivity.EXTRA_EXTRACT_NNAPI_SL,
            NNTestBase.shouldExtractNnApiSupportLibrary());
        return intent;
    }
}
