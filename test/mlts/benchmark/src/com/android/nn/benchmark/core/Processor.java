/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.nn.benchmark.core;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.content.Context;
import android.os.Trace;
import android.util.Log;
import android.util.Pair;

import com.android.nn.benchmark.core.TestModels.TestModelEntry;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/** Processor is a helper thread for running the work without blocking the UI thread. */
public class Processor implements Runnable {


    public interface Callback {
        void onBenchmarkFinish(boolean ok);

        void onStatusUpdate(int testNumber, int numTests, String modelName);
    }

    protected static final String TAG = "NN_BENCHMARK";
    private Context mContext;

    private final AtomicBoolean mRun = new AtomicBoolean(true);

    volatile boolean mHasBeenStarted = false;
    // You cannot restart a thread, so the completion flag is final
    private final CountDownLatch mCompleted = new CountDownLatch(1);
    private NNTestBase mTest;
    private int mTestList[];
    private BenchmarkResult mTestResults[];

    private Processor.Callback mCallback;

    private TfLiteBackend mBackend;
    private boolean mMmapModel;
    private boolean mCompleteInputSet;
    private boolean mToggleLong;
    private boolean mTogglePause;
    private String mAcceleratorName;
    private boolean mIgnoreUnsupportedModels;
    private boolean mRunModelCompilationOnly;
    // Max number of benchmark iterations to do in run method.
    // Less or equal to 0 means unlimited
    private int mMaxRunIterations;

    private boolean mBenchmarkCompilationCaching;
    private float mCompilationBenchmarkWarmupTimeSeconds;
    private float mCompilationBenchmarkRunTimeSeconds;
    private int mCompilationBenchmarkMaxIterations;

    // Used to avoid accessing the Instrumentation Arguments when the crash tests are spawning
    // a separate process.
    private String mModelFilterRegex;

    private boolean mUseNnApiSupportLibrary;
    private boolean mExtractNnApiSupportLibrary;
    private String mNnApiSupportLibraryVendor;

    public Processor(Context context, Processor.Callback callback, int[] testList) {
        mContext = context;
        mCallback = callback;
        mTestList = testList;
        if (mTestList != null) {
            mTestResults = new BenchmarkResult[mTestList.length];
        }
        mAcceleratorName = null;
        mIgnoreUnsupportedModels = false;
        mRunModelCompilationOnly = false;
        mMaxRunIterations = 0;
        mBenchmarkCompilationCaching = false;
        mBackend = TfLiteBackend.CPU;
        mModelFilterRegex = null;
        mUseNnApiSupportLibrary = false;
        mExtractNnApiSupportLibrary = false;
        mNnApiSupportLibraryVendor = "";
    }

    public void setUseNNApi(boolean useNNApi) {
        setTfLiteBackend(useNNApi ? TfLiteBackend.NNAPI : TfLiteBackend.CPU);
    }

    public void setTfLiteBackend(TfLiteBackend backend) {
        mBackend = backend;
    }

    public void setCompleteInputSet(boolean completeInputSet) {
        mCompleteInputSet = completeInputSet;
    }

    public void setToggleLong(boolean toggleLong) {
        mToggleLong = toggleLong;
    }

    public void setTogglePause(boolean togglePause) {
        mTogglePause = togglePause;
    }

    public void setNnApiAcceleratorName(String acceleratorName) {
        mAcceleratorName = acceleratorName;
    }

    public void setIgnoreUnsupportedModels(boolean value) {
        mIgnoreUnsupportedModels = value;
    }

    public void setRunModelCompilationOnly(boolean value) {
        mRunModelCompilationOnly = value;
    }

    public void setMmapModel(boolean value) {
        mMmapModel = value;
    }

    public void setMaxRunIterations(int value) {
        mMaxRunIterations = value;
    }

    public void setModelFilterRegex(String value) {
        this.mModelFilterRegex = value;
    }

    public void setUseNnApiSupportLibrary(boolean value) { mUseNnApiSupportLibrary = value; }
    public void setExtractNnApiSupportLibrary(boolean value) { mExtractNnApiSupportLibrary = value; }
    public void setNnApiSupportLibraryVendor(String value) { mNnApiSupportLibraryVendor = value; }

    public void enableCompilationCachingBenchmarks(
            float warmupTimeSeconds, float runTimeSeconds, int maxIterations) {
        mBenchmarkCompilationCaching = true;
        mCompilationBenchmarkWarmupTimeSeconds = warmupTimeSeconds;
        mCompilationBenchmarkRunTimeSeconds = runTimeSeconds;
        mCompilationBenchmarkMaxIterations = maxIterations;
    }

    public BenchmarkResult getInstrumentationResult(
            TestModels.TestModelEntry t, float warmupTimeSeconds, float runTimeSeconds)
            throws IOException, BenchmarkException {
        return getInstrumentationResult(t, warmupTimeSeconds, runTimeSeconds, false);
    }

    // Method to retrieve benchmark results for instrumentation tests.
    // Returns null if the processor is configured to run compilation only
    public BenchmarkResult getInstrumentationResult(
            TestModels.TestModelEntry t, float warmupTimeSeconds, float runTimeSeconds,
            boolean sampleResults)
            throws IOException, BenchmarkException {
        mTest = changeTest(mTest, t);
        mTest.setSampleResult(sampleResults);
        try {
            BenchmarkResult result = mRunModelCompilationOnly ? null : getBenchmark(
                    warmupTimeSeconds,
                    runTimeSeconds);
            return result;
        } finally {
            mTest.destroy();
            mTest = null;
        }
    }

    public static boolean isTestModelSupportedByAccelerator(Context context,
            TestModels.TestModelEntry testModelEntry, String acceleratorName)
            throws NnApiDelegationFailure {
        try (NNTestBase tb = testModelEntry.createNNTestBase(TfLiteBackend.NNAPI,
                /*enableIntermediateTensorsDump=*/false,
                /*mmapModel=*/ false,
                NNTestBase.shouldUseNnApiSupportLibrary(),
                NNTestBase.shouldExtractNnApiSupportLibrary(),
                NNTestBase.getNnApiSupportLibraryVendor()
            )) {
            tb.setNNApiDeviceName(acceleratorName);
            return tb.setupModel(context);
        } catch (IOException e) {
            Log.w(TAG,
                    String.format("Error trying to check support for model %s on accelerator %s",
                            testModelEntry.mModelName, acceleratorName), e);
            return false;
        } catch (NnApiDelegationFailure nnApiDelegationFailure) {
            if (nnApiDelegationFailure.getNnApiErrno() == 4 /*ANEURALNETWORKS_BAD_DATA*/) {
                // Compilation will fail with ANEURALNETWORKS_BAD_DATA if the device is not
                // supporting all operation in the model
                return false;
            }

            throw nnApiDelegationFailure;
        }
    }

    private NNTestBase changeTest(NNTestBase oldTestBase, TestModels.TestModelEntry t)
            throws IOException, UnsupportedModelException, NnApiDelegationFailure {
        if (oldTestBase != null) {
            // Make sure we don't leak memory.
            oldTestBase.destroy();
        }
        NNTestBase tb = t.createNNTestBase(mBackend, /*enableIntermediateTensorsDump=*/false,
            mMmapModel, mUseNnApiSupportLibrary, mExtractNnApiSupportLibrary, mNnApiSupportLibraryVendor);
        if (mBackend == TfLiteBackend.NNAPI) {
            tb.setNNApiDeviceName(mAcceleratorName);
        }
        if (!tb.setupModel(mContext)) {
            throw new UnsupportedModelException("Cannot initialise model");
        }
        return tb;
    }

    // Run one loop of kernels for at most the specified minimum time.
    // The function returns the average time in ms for the test run
    private BenchmarkResult runBenchmarkLoop(float maxTime, boolean completeInputSet)
            throws IOException {
        try {
            // Run the kernel
            Pair<List<InferenceInOutSequence>, List<InferenceResult>> results;
            if (maxTime > 0.f) {
                if (completeInputSet) {
                    results = mTest.runBenchmarkCompleteInputSet(1, maxTime);
                } else {
                    results = mTest.runBenchmark(maxTime);
                }
            } else {
                results = mTest.runInferenceOnce();
            }
            return BenchmarkResult.fromInferenceResults(
                    mTest.getTestInfo(),
                    mBackend.toString(),
                    results.first,
                    results.second,
                    mTest.getEvaluator());
        } catch (BenchmarkException e) {
            return new BenchmarkResult(e.getMessage());
        }
    }

    // Run one loop of compilations for at least the specified minimum time.
    // The function will set the compilation results into the provided benchmark result object.
    private void runCompilationBenchmarkLoop(float warmupMinTime, float runMinTime,
            int maxIterations, BenchmarkResult benchmarkResult) throws IOException {
        try {
            CompilationBenchmarkResult result =
                    mTest.runCompilationBenchmark(warmupMinTime, runMinTime, maxIterations);
            benchmarkResult.setCompilationBenchmarkResult(result);
        } catch (BenchmarkException e) {
            benchmarkResult.setBenchmarkError(e.getMessage());
        }
    }

    public BenchmarkResult[] getTestResults() {
        return mTestResults;
    }

    // Get a benchmark result for a specific test
    private BenchmarkResult getBenchmark(float warmupTimeSeconds, float runTimeSeconds)
            throws IOException {
        try {
            mTest.checkSdkVersion();
        } catch (UnsupportedSdkException e) {
            BenchmarkResult r = new BenchmarkResult(e.getMessage());
            Log.w(TAG, "Unsupported SDK for test: " + r.toString());
            return r;
        }

        // We run a short bit of work before starting the actual test
        // this is to let any power management do its job and respond.
        // For NNAPI systrace usage documentation, see
        // frameworks/ml/nn/common/include/Tracing.h.
        try {
            final String traceName = "[NN_LA_PWU]runBenchmarkLoop";
            Trace.beginSection(traceName);
            runBenchmarkLoop(warmupTimeSeconds, false);
        } finally {
            Trace.endSection();
        }

        // Run the actual benchmark
        BenchmarkResult r;
        try {
            final String traceName = "[NN_LA_PBM]runBenchmarkLoop";
            Trace.beginSection(traceName);
            r = runBenchmarkLoop(runTimeSeconds, mCompleteInputSet);
        } finally {
            Trace.endSection();
        }

        // Compilation benchmark
        if (mBenchmarkCompilationCaching) {
            runCompilationBenchmarkLoop(mCompilationBenchmarkWarmupTimeSeconds,
                    mCompilationBenchmarkRunTimeSeconds, mCompilationBenchmarkMaxIterations, r);
        }

        return r;
    }

    @Override
    public void run() {
        mHasBeenStarted = true;
        Log.d(TAG, "Processor starting");
        boolean success = true;
        int benchmarkIterationsCount = 0;
        try {
            while (mRun.get()) {
                if (mMaxRunIterations > 0 && benchmarkIterationsCount >= mMaxRunIterations) {
                    break;
                }
                benchmarkIterationsCount++;
                try {
                    benchmarkAllModels();
                } catch (IOException | BenchmarkException e) {
                    Log.e(TAG, "Exception during benchmark run", e);
                    success = false;
                    break;
                } catch (Throwable e) {
                    Log.e(TAG, "Error during execution", e);
                    throw e;
                }
            }
            Log.d(TAG, "Processor completed work");
            mCallback.onBenchmarkFinish(success);
        } finally {
            if (mTest != null) {
                // Make sure we don't leak memory.
                mTest.destroy();
                mTest = null;
            }
            mCompleted.countDown();
        }
    }

    private void benchmarkAllModels() throws IOException, BenchmarkException {
        final List<TestModelEntry> modelsList = TestModels.modelsList(mModelFilterRegex);
        // Loop over the tests we want to benchmark
        for (int ct = 0; ct < mTestList.length; ct++) {
            if (!mRun.get()) {
                Log.v(TAG, String.format("Asked to stop execution at model #%d", ct));
                break;
            }
            // For reproducibility we wait a short time for any sporadic work
            // created by the user touching the screen to launch the test to pass.
            // Also allows for things to settle after the test changes.
            try {
                Thread.sleep(250);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                break;
            }

            TestModels.TestModelEntry testModel =
                    modelsList.get(mTestList[ct]);

            int testNumber = ct + 1;
            mCallback.onStatusUpdate(testNumber, mTestList.length,
                    testModel.toString());

            // Select the next test
            try {
                mTest = changeTest(mTest, testModel);
            } catch (UnsupportedModelException e) {
                if (mIgnoreUnsupportedModels) {
                    Log.d(TAG, String.format(
                            "Cannot initialise test %d: '%s' on accelerator %s, skipping", ct,
                            testModel.mTestName, mAcceleratorName));
                } else {
                    Log.e(TAG,
                            String.format("Cannot initialise test %d: '%s'  on accelerator %s.", ct,
                                    testModel.mTestName, mAcceleratorName), e);
                    throw e;
                }
            }

            // If the user selected the "long pause" option, wait
            if (mTogglePause) {
                for (int i = 0; (i < 100) && mRun.get(); i++) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            if (mRunModelCompilationOnly) {
                mTestResults[ct] = BenchmarkResult.fromInferenceResults(testModel.mTestName,
                        mBackend.toString(),
                        Collections.emptyList(),
                        Collections.emptyList(), null);
            } else {
                // Run the test
                float warmupTime = 0.3f;
                float runTime = 1.f;
                if (mToggleLong) {
                    warmupTime = 2.f;
                    runTime = 10.f;
                }
                mTestResults[ct] = getBenchmark(warmupTime, runTime);
            }
        }
    }

    public void exit() {
        exitWithTimeout(-1l);
    }

    public void exitWithTimeout(long timeoutMs) {
        mRun.set(false);

        if (mHasBeenStarted) {
            Log.d(TAG, String.format("Terminating, timeout is %d ms", timeoutMs));
            try {
                if (timeoutMs > 0) {
                    boolean hasCompleted = mCompleted.await(timeoutMs, MILLISECONDS);
                    if (!hasCompleted) {
                        Log.w(TAG, "Exiting before execution actually completed");
                    }
                } else {
                    mCompleted.await();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.w(TAG, "Interrupted while waiting for Processor to complete", e);
            }
        }

        Log.d(TAG, "Done, cleaning up");

        if (mTest != null) {
            mTest.destroy();
            mTest = null;
        }
    }
}
