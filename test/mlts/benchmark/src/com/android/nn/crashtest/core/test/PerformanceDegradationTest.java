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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.android.nn.benchmark.core.BenchmarkException;
import com.android.nn.benchmark.core.BenchmarkResult;
import com.android.nn.benchmark.core.Processor;
import com.android.nn.benchmark.core.TestModels;
import com.android.nn.benchmark.core.TfLiteBackend;
import com.android.nn.benchmark.app.AcceleratorSpecificTestSupport;
import com.android.nn.crashtest.core.CrashTest;
import com.android.nn.crashtest.core.CrashTestCoordinator;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;

public class PerformanceDegradationTest implements CrashTest {
    public static final String TAG = "NN_PERF_DEG";

    private static final Processor.Callback mNoOpCallback = new Processor.Callback() {
        @Override
        public void onBenchmarkFinish(boolean ok) {
        }

        @Override
        public void onStatusUpdate(int testNumber, int numTests, String modelName) {
        }
    };

    public static final String WARMUP_SECONDS = "warmup_seconds";
    public static final String RUN_TIME_SECONDS = "run_time_seconds";
    public static final String ACCELERATOR_NAME = "accelerator_name";
    public static final float DEFAULT_WARMUP_SECONDS = 3.0f;
    public static final float DEFAULT_RUN_TIME_SECONDS = 10.0f;
    public static final String THREAD_COUNT = "thread_count";
    public static final int DEFAULT_THREAD_COUNT = 5;
    public static final String MAX_PERFORMANCE_DEGRADATION = "max_performance_degradation";
    public static final int DEFAULT_MAX_PERFORMANCE_DEGRADATION_PERCENTAGE = 100;
    public static final String TEST_NAME = "test_name";
    private static final long INTERVAL_BETWEEN_PERFORMANCE_MEASUREMENTS_MS = 500;

    static public CrashTestCoordinator.CrashTestIntentInitializer intentInitializer(
            float warmupTimeSeconds, float runTimeSeconds, String acceleratorName, int threadCount,
            int maxPerformanceDegradationPercent, String testName) {
        return intent -> {
            intent.putExtra(WARMUP_SECONDS, warmupTimeSeconds);
            intent.putExtra(RUN_TIME_SECONDS, runTimeSeconds);
            intent.putExtra(ACCELERATOR_NAME, acceleratorName);
            intent.putExtra(THREAD_COUNT, threadCount);
            intent.putExtra(MAX_PERFORMANCE_DEGRADATION, maxPerformanceDegradationPercent);
            intent.putExtra(TEST_NAME, testName);
        };
    }

    static public CrashTestCoordinator.CrashTestIntentInitializer intentInitializer(
            Intent copyFrom) {
        return intentInitializer(
                copyFrom.getFloatExtra(WARMUP_SECONDS, DEFAULT_WARMUP_SECONDS),
                copyFrom.getFloatExtra(RUN_TIME_SECONDS, DEFAULT_RUN_TIME_SECONDS),
                copyFrom.getStringExtra(ACCELERATOR_NAME),
                copyFrom.getIntExtra(THREAD_COUNT, DEFAULT_THREAD_COUNT),
                copyFrom.getIntExtra(MAX_PERFORMANCE_DEGRADATION,
                        DEFAULT_MAX_PERFORMANCE_DEGRADATION_PERCENTAGE),
                copyFrom.getStringExtra(TEST_NAME));
    }

    private Context mContext;
    private float mWarmupTimeSeconds;
    private float mRunTimeSeconds;
    private String mAcceleratorName;
    private int mThreadCount;
    private int mMaxPerformanceDegradationPercent;
    private String mTestName;

    @Override
    public void init(Context context, Intent configParams,
            Optional<ProgressListener> progressListener) {
        mContext = context;

        mWarmupTimeSeconds = configParams.getFloatExtra(WARMUP_SECONDS, DEFAULT_WARMUP_SECONDS);
        mRunTimeSeconds = configParams.getFloatExtra(RUN_TIME_SECONDS, DEFAULT_RUN_TIME_SECONDS);
        mAcceleratorName = configParams.getStringExtra(ACCELERATOR_NAME);
        mThreadCount = configParams.getIntExtra(THREAD_COUNT, DEFAULT_THREAD_COUNT);
        mMaxPerformanceDegradationPercent = configParams.getIntExtra(MAX_PERFORMANCE_DEGRADATION,
                DEFAULT_MAX_PERFORMANCE_DEGRADATION_PERCENTAGE);
        mTestName = configParams.getStringExtra(TEST_NAME);
    }

    @SuppressLint("DefaultLocale")
    @Override
    public Optional<String> call() throws Exception {
        List<TestModels.TestModelEntry> modelsForAccelerator =
                AcceleratorSpecificTestSupport.findAllTestModelsRunningOnAccelerator(mContext,
                        mAcceleratorName);

        if (modelsForAccelerator.isEmpty()) {
            return failure("Cannot find any model to use for testing");
        }

        Log.i(TAG, String.format("Checking performance degradation using %d models",
                modelsForAccelerator.size()));

        TestModels.TestModelEntry modelForInference = modelsForAccelerator.get(0);
        // The performance degradation is strongly dependent on the model used to compile
        // so we check all the available ones.
        for (TestModels.TestModelEntry modelForCompilation : modelsForAccelerator) {
            Optional<String> currTestResult = testDegradationForModels(modelForInference,
                    modelForCompilation);
            if (isFailure(currTestResult)) {
                return currTestResult;
            }
        }

        return success();
    }

    @SuppressLint("DefaultLocale")
    public Optional<String> testDegradationForModels(
            TestModels.TestModelEntry inferenceModelEntry,
            TestModels.TestModelEntry compilationModelEntry) throws Exception {
        Log.i(TAG, String.format(
                "Testing degradation in inference of model %s when running %d threads compliing "
                        + "model %s",
                inferenceModelEntry.mModelName, mThreadCount, compilationModelEntry.mModelName));

        Log.d(TAG, String.format("%s: Calculating baseline", mTestName));
        // first let's measure a baseline performance
        final BenchmarkResult baseline = modelPerformanceCollector(inferenceModelEntry,
                /*start=*/ null).call();
        if (baseline.hasBenchmarkError()) {
            return failure(String.format("%s: Baseline has benchmark error '%s'",
                    mTestName, baseline.getBenchmarkError()));
        }
        Log.d(TAG, String.format("%s: Baseline mean time is %f seconds", mTestName,
                baseline.getMeanTimeSec()));

        Log.d(TAG, String.format("%s: Sleeping for %d millis", mTestName,
                INTERVAL_BETWEEN_PERFORMANCE_MEASUREMENTS_MS));
        Thread.sleep(INTERVAL_BETWEEN_PERFORMANCE_MEASUREMENTS_MS);

        Log.d(TAG, String.format("%s: Calculating performance with %d threads", mTestName,
                mThreadCount));
        final int totalThreadCount = mThreadCount + 1;
        final CountDownLatch start = new CountDownLatch(totalThreadCount);
        ModelCompiler[] compilers = Stream.generate(
                () -> new ModelCompiler(start, mContext, mAcceleratorName,
                        compilationModelEntry)).limit(
                mThreadCount).toArray(
                ModelCompiler[]::new);

        Callable<BenchmarkResult> performanceWithOtherCompilingThreadCollector =
                modelPerformanceCollector(inferenceModelEntry, start);

        ExecutorService testExecutor = Executors.newFixedThreadPool(totalThreadCount);
        Future<?>[] compilerFutures = Arrays.stream(compilers).map(testExecutor::submit).toArray(
                Future[]::new);
        BenchmarkResult benchmarkWithOtherCompilingThread = testExecutor.submit(
                performanceWithOtherCompilingThreadCollector).get();

        Arrays.stream(compilers).forEach(ModelCompiler::stop);
        Arrays.stream(compilerFutures).forEach(future -> {
            try {
                future.get();
            } catch (InterruptedException | ExecutionException e) {
                Log.e(TAG, "Error waiting for compiler process completion", e);
            }
        });

        if (benchmarkWithOtherCompilingThread.hasBenchmarkError()) {
            return failure(
                    String.format(
                            "%s: Test with parallel compiling thrads has benchmark error '%s'",
                            mTestName, benchmarkWithOtherCompilingThread.getBenchmarkError()));
        }

        Log.d(TAG, String.format("%s: Multithreaded mean time is %f seconds",
                mTestName, benchmarkWithOtherCompilingThread.getMeanTimeSec()));

        int performanceDegradation = (int) (((benchmarkWithOtherCompilingThread.getMeanTimeSec()
                / baseline.getMeanTimeSec()) - 1.0) * 100);

        Log.i(TAG, String.format(
                "%s: Performance degradation for accelerator %s, with %d threads is %d%%. "
                        + "Threshold "
                        + "is %d%%",
                mTestName, mAcceleratorName, mThreadCount, performanceDegradation,
                mMaxPerformanceDegradationPercent));

        if (performanceDegradation > mMaxPerformanceDegradationPercent) {
            return failure(String.format("Performance degradation is %d%%. Max acceptable is %d%%",
                    performanceDegradation, mMaxPerformanceDegradationPercent));
        }

        return success();
    }


    private Callable<BenchmarkResult> modelPerformanceCollector(
            final TestModels.TestModelEntry inferenceModelEntry, final CountDownLatch start) {
        return () -> {
            Processor benchmarkProcessor = new Processor(mContext, mNoOpCallback, new int[0]);
            benchmarkProcessor.setTfLiteBackend(TfLiteBackend.NNAPI);
            benchmarkProcessor.setNnApiAcceleratorName(mAcceleratorName);
            if (start != null) {
                start.countDown();
                start.await();
            }
            final BenchmarkResult result =
                    benchmarkProcessor.getInstrumentationResult(
                            inferenceModelEntry, mWarmupTimeSeconds, mRunTimeSeconds);

            return result;
        };
    }

    private static class ModelCompiler implements Callable<Void> {
        private static final long SLEEP_BETWEEN_COMPILATION_INTERVAL_MS = 20;
        private final CountDownLatch mStart;
        private final Processor mProcessor;
        private final TestModels.TestModelEntry mTestModelEntry;
        private volatile boolean mRun;

        ModelCompiler(final CountDownLatch start, final Context context,
                final String acceleratorName, TestModels.TestModelEntry testModelEntry) {
            mStart = start;
            mTestModelEntry = testModelEntry;
            mProcessor = new Processor(context, mNoOpCallback, new int[0]);
            mProcessor.setTfLiteBackend(TfLiteBackend.NNAPI);
            mProcessor.setNnApiAcceleratorName(acceleratorName);
            mProcessor.setRunModelCompilationOnly(true);
            mRun = true;
        }

        @Override
        public Void call() throws IOException, BenchmarkException {
            if (mStart != null) {
                try {
                    mStart.countDown();
                    mStart.await();
                } catch (InterruptedException e) {
                    Thread.interrupted();
                    Log.i(TAG, "Interrupted, stopping processing");
                    return null;
                }
            }
            while (mRun) {
                mProcessor.getInstrumentationResult(mTestModelEntry, 0, 0);
                try {
                    Thread.sleep(SLEEP_BETWEEN_COMPILATION_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.interrupted();
                    return null;
                }
            }
            return null;
        }

        public void stop() {
            mRun = false;
        }
    }
}
