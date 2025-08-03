/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static org.junit.Assume.assumeTrue;

import com.android.nn.benchmark.core.NNTestBase;
import com.android.nn.benchmark.core.NnApiDelegationFailure;
import com.android.nn.benchmark.core.Processor;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Trace;
import android.test.ActivityInstrumentationTestCase2;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

import com.android.nn.benchmark.core.BenchmarkException;
import com.android.nn.benchmark.core.BenchmarkResult;
import com.android.nn.benchmark.core.TestModels;
import com.android.nn.benchmark.core.TestModels.TestModelEntry;

import java.util.stream.Collectors;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * Benchmark test-case super-class.
 *
 * Helper code for managing NNAPI/NNAPI-less benchamarks.
 */
@RunWith(Parameterized.class)
public class BenchmarkTestBase extends ActivityInstrumentationTestCase2<NNBenchmark> {

    // Only run 1 iteration now to fit the MediumTest time requirement.
    // One iteration means running the tests continuous for 1s.
    private NNBenchmark mActivity;
    protected final TestModelEntry mModel;
    protected final String mAcceleratorName;

    // The default 0.3s warmup and 1.0s runtime give reasonably repeatable results (run-to-run
    // variability of ~20%) when run under performance settings (fixed CPU cores enabled and at
    // fixed frequency). The continuous build is not allowed to take much more than 1s so we
    // can't change the defaults for @MediumTest.
    protected static final float WARMUP_SHORT_SECONDS = 0.3f;
    protected static final float RUNTIME_SHORT_SECONDS = 1.f;

    // For running like a normal user-initiated app, the variability for 0.3s/1.0s is easily 3x.
    // With 2s/10s it's 20-50%. This @LargeTest allows running with these timings.
    protected static final float WARMUP_REPEATABLE_SECONDS = 2.f;
    protected static final float RUNTIME_REPEATABLE_SECONDS = 10.f;

    // For running a complete dataset, the run should complete under 5 minutes.
    protected static final float COMPLETE_SET_TIMEOUT_SECOND = 300.f;

    // For running compilation benchmarks.
    protected static final float COMPILATION_WARMUP_SECONDS = 2.f;
    protected static final float COMPILATION_RUNTIME_SECONDS = 10.f;
    protected static final int COMPILATION_MAX_ITERATIONS = 100;

    public BenchmarkTestBase(TestModelEntry model, String acceleratorName) {
        super(NNBenchmark.class);
        mModel = model;
        mAcceleratorName = acceleratorName;
    }

    protected void setUseNNApi(boolean useNNApi) {
        mActivity.setUseNNApi(useNNApi);
        if (useNNApi) {
            final boolean useNnApiSupportLibrary = NNTestBase.shouldUseNnApiSupportLibrary();
            final boolean extractNnApiSupportLibrary = NNTestBase.shouldExtractNnApiSupportLibrary();
            final String nnApiSupportLibraryVendor = NNTestBase.getNnApiSupportLibraryVendor();
            Log.i(NNBenchmark.TAG, "Configuring usage of NNAPI SL to " + useNnApiSupportLibrary);
            mActivity.setUseNnApiSupportLibrary(useNnApiSupportLibrary);
            mActivity.setExtractNnApiSupportLibrary(extractNnApiSupportLibrary);
            mActivity.setNnApiSupportLibraryVendor(nnApiSupportLibraryVendor);
        }
    }

    protected void setNnApiAcceleratorName(String acceleratorName) {
        mActivity.setNnApiAcceleratorName(acceleratorName);
    }

    protected void setCompleteInputSet(boolean completeInputSet) {
        mActivity.setCompleteInputSet(completeInputSet);
    }

    protected void enableCompilationCachingBenchmarks() {
        mActivity.enableCompilationCachingBenchmarks(COMPILATION_WARMUP_SECONDS,
                COMPILATION_RUNTIME_SECONDS, COMPILATION_MAX_ITERATIONS);
    }

    private boolean isModelSupported() {
        try {
            return Processor.isTestModelSupportedByAccelerator(mActivity, mModel, mAcceleratorName);
        } catch (NnApiDelegationFailure delegationFailure) {
            throw new Error(
                String.format("Failure checking if model %s is supported on accelerator %s ",
                    mModel.mModelName, mAcceleratorName), delegationFailure);
        }
    }

    // Initialize the parameter for ImageProcessingActivityJB.
    protected void prepareTest() {
        injectInstrumentation(InstrumentationRegistry.getInstrumentation());
        mActivity = getActivity();
        mActivity.prepareInstrumentationTest();
        if (mAcceleratorName != null) {
            assumeTrue(isModelSupported());
            mActivity.setNnApiAcceleratorName(mAcceleratorName);
        }
        setUseNNApi(true);
    }

    public void waitUntilCharged() {
        BenchmarkTestBase.waitUntilCharged(mActivity, -1);
    }

    public static void waitUntilCharged(Context context, int minChargeLevel) {
        Log.v(NNBenchmark.TAG, "Waiting for the device to charge");

        final CountDownLatch chargedLatch = new CountDownLatch(1);
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                int percentage = level * 100 / scale;
                if (minChargeLevel > 0 && minChargeLevel < 100) {
                    if (percentage >= minChargeLevel) {
                        Log.v(NNBenchmark.TAG,
                                String.format(
                                        "Battery level: %d%% is greater than requested %d%%. "
                                                + "Considering the device ready.",
                                        percentage, minChargeLevel));

                        chargedLatch.countDown();
                        return;
                    }
                }

                Log.v(NNBenchmark.TAG, String.format("Battery level: %d%%", percentage));

                int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                if (status == BatteryManager.BATTERY_STATUS_FULL) {
                    chargedLatch.countDown();
                } else if (status != BatteryManager.BATTERY_STATUS_CHARGING) {
                    Log.e(NNBenchmark.TAG,
                            String.format("Device is not charging, status is %d", status));
                }
            }
        };

        context.registerReceiver(receiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        try {
            chargedLatch.await();
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        context.unregisterReceiver(receiver);
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        prepareTest();
        setActivityInitialTouchMode(false);
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    interface Joinable extends Runnable {
        // Syncrhonises the caller with the completion of the current action
        void join();
    }

    class TestAction implements Joinable {

        private final TestModelEntry mTestModel;
        private final float mMaxWarmupTimeSeconds;
        private final float mMaxRunTimeSeconds;
        private final CountDownLatch actionComplete;
        private final boolean mSampleResults;

        BenchmarkResult mResult;
        Throwable mException;

        public TestAction(TestModelEntry testName, float maxWarmupTimeSeconds,
                float maxRunTimeSeconds) {
            this(testName, maxWarmupTimeSeconds, maxRunTimeSeconds, false);
        }

        public TestAction(TestModelEntry testName, float maxWarmupTimeSeconds,
                float maxRunTimeSeconds, boolean sampleResults) {
            mTestModel = testName;
            mMaxWarmupTimeSeconds = maxWarmupTimeSeconds;
            mMaxRunTimeSeconds = maxRunTimeSeconds;
            mSampleResults = sampleResults;
            actionComplete = new CountDownLatch(1);
        }

        public void run() {
            Log.v(NNBenchmark.TAG, String.format(
                    "Starting benchmark for test '%s' running for max %f seconds",
                    mTestModel.mTestName,
                    mMaxRunTimeSeconds));
            try {
                mResult = mActivity.runSynchronously(
                        mTestModel, mMaxWarmupTimeSeconds, mMaxRunTimeSeconds, mSampleResults);
            } catch (BenchmarkException | IOException e) {
                mException = e;
                Log.e(NNBenchmark.TAG,
                        String.format("Error running Benchmark for test '%s'", mTestModel), e);
            } catch (Throwable e) {
                mException = e;
                Log.e(NNBenchmark.TAG,
                        String.format("Failure running Benchmark for test '%s'!!", mTestModel), e);
                throw e;
            } finally {
                actionComplete.countDown();
            }
        }

        public BenchmarkResult getBenchmark() {
            if (mException != null) {
                throw new Error("run failed", mException);
            }
            return mResult;
        }

        @Override
        public void join() {
            try {
                actionComplete.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.v(NNBenchmark.TAG, "Interrupted while waiting for action running", e);
            }
        }
    }

    // Set the benchmark thread to run on ui thread
    // Synchronized the thread such that the test will wait for the benchmark thread to finish
    public void runOnUiThread(Joinable action) {
        mActivity.runOnUiThread(action);
        action.join();
    }

    public void runTest(TestAction ta, String testName) {
        float sum = 0;
        // For NNAPI systrace usage documentation, see
        // frameworks/ml/nn/common/include/Tracing.h.
        final String traceName = "[NN_LA_PO]" + testName;
        try {
            Trace.beginSection(traceName);
            Log.i(NNBenchmark.TAG, "Starting test " + testName);
            runOnUiThread(ta);
            Log.i(NNBenchmark.TAG, "Test " + testName + " completed");
        } finally {
            Trace.endSection();
        }
        BenchmarkResult bmValue = ta.getBenchmark();

        // post result to INSTRUMENTATION_STATUS
        getInstrumentation().sendStatus(Activity.RESULT_OK, bmValue.toBundle(testName));
    }

    @Parameters(name = "{0} model on accelerator {1}")
    public static List<Object[]> modelsOnAccelerators() {
        Log.d(NNBenchmark.TAG, "Calculating list of models");
        List<Object[]> result = AcceleratorSpecificTestSupport.maybeAddAcceleratorsToTestConfig(
            TestModels.modelsList().stream().map(model -> new Object[] {model}).collect(Collectors.toList())
        );
        Log.d(NNBenchmark.TAG, "Returning list of models of size " + result.size());
        return result;
    }
}
