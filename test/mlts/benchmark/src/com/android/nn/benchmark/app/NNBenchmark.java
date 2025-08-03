/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;
import android.widget.TextView;
import com.android.nn.benchmark.core.BenchmarkException;
import com.android.nn.benchmark.core.BenchmarkResult;
import com.android.nn.benchmark.core.Processor;
import com.android.nn.benchmark.core.TestModels.TestModelEntry;
import com.android.nn.benchmark.core.TfLiteBackend;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NNBenchmark extends Activity implements Processor.Callback {
    public static final String TAG = "NN_BENCHMARK";

    public static final String EXTRA_ENABLE_LONG = "enable long";
    public static final String EXTRA_ENABLE_PAUSE = "enable pause";
    public static final String EXTRA_DISABLE_NNAPI = "disable NNAPI";
    public static final String EXTRA_TESTS = "tests";

    public static final String EXTRA_RESULTS_TESTS = "tests";
    public static final String EXTRA_RESULTS_RESULTS = "results";
    public static final long PROCESSOR_TERMINATION_TIMEOUT_MS = Duration.ofSeconds(20).toMillis();
    public static final String EXTRA_MAX_ITERATIONS = "max_iterations";

    private int mTestList[];

    private boolean mUseNnApiSupportLibrary = false;
    private boolean mExtractNnApiSupportLibrary = false;
    private String mNnApiSupportLibraryVendor = "";

    private Processor mProcessor;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private TextView mTextView;

    // Initialize the parameters for Instrumentation tests.
    protected void prepareInstrumentationTest() {
        mTestList = new int[1];
        mProcessor = new Processor(this, this, mTestList);
    }

    public void setUseNNApi(boolean useNNApi) {
        mProcessor.setTfLiteBackend(useNNApi ? TfLiteBackend.NNAPI : TfLiteBackend.CPU);
    }

    public void setNnApiAcceleratorName(String acceleratorName) {
        mProcessor.setNnApiAcceleratorName(acceleratorName);
    }

    public void setCompleteInputSet(boolean completeInputSet) {
        mProcessor.setCompleteInputSet(completeInputSet);
    }

    public void enableCompilationCachingBenchmarks(
            float warmupTimeSeconds, float runTimeSeconds, int maxIterations) {
        mProcessor.enableCompilationCachingBenchmarks(
                warmupTimeSeconds, runTimeSeconds, maxIterations);
    }

    public void setUseNnApiSupportLibrary(boolean value) {
        mUseNnApiSupportLibrary = value;
        mProcessor.setUseNnApiSupportLibrary(mUseNnApiSupportLibrary);
    }

    public void setNnApiSupportLibraryVendor(String value) {
        mNnApiSupportLibraryVendor = value;
        mProcessor.setNnApiSupportLibraryVendor(mNnApiSupportLibraryVendor);
    }

    public void setExtractNnApiSupportLibrary(boolean value) {
        mExtractNnApiSupportLibrary = value;
        mProcessor.setExtractNnApiSupportLibrary(value);
    }

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mTextView = new TextView(this);
        mTextView.setTextSize(20);
        mTextView.setText("Running NN benchmark...");
        setContentView(mTextView);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mProcessor != null) {
            mProcessor.exitWithTimeout(PROCESSOR_TERMINATION_TIMEOUT_MS);
            mProcessor = null;
        }
    }

    public void onBenchmarkFinish(boolean ok) {
        if (ok) {
            Intent intent = new Intent();
            intent.putExtra(EXTRA_RESULTS_TESTS, mTestList);
            intent.putExtra(EXTRA_RESULTS_RESULTS, mProcessor.getTestResults());
            setResult(RESULT_OK, intent);
        } else {
            setResult(RESULT_CANCELED);
        }
        finish();
    }

    @SuppressLint("DefaultLocale")
    public void onStatusUpdate(int testNumber, int numTests, String modelName) {
        runOnUiThread(
                () -> {
                    mTextView.setText(
                            String.format(
                                    "Running test %d of %d: %s", testNumber, numTests, modelName));
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
        Intent i = getIntent();
        mTestList = i.getIntArrayExtra(EXTRA_TESTS);
        if (mTestList != null && mTestList.length > 0) {
            Log.v(TAG, String.format("Starting benchmark with %d test", mTestList.length));
            mProcessor = new Processor(this, this, mTestList);
            mProcessor.setToggleLong(i.getBooleanExtra(EXTRA_ENABLE_LONG, false));
            mProcessor.setTogglePause(i.getBooleanExtra(EXTRA_ENABLE_PAUSE, false));
            mProcessor.setTfLiteBackend(!i.getBooleanExtra(EXTRA_DISABLE_NNAPI, false) ? TfLiteBackend.NNAPI : TfLiteBackend.CPU);
            mProcessor.setMaxRunIterations(i.getIntExtra(EXTRA_MAX_ITERATIONS, 0));
            mProcessor.setUseNnApiSupportLibrary(mUseNnApiSupportLibrary);
            mProcessor.setNnApiSupportLibraryVendor(mNnApiSupportLibraryVendor);
            mProcessor.setExtractNnApiSupportLibrary(mExtractNnApiSupportLibrary);
            executorService.submit(mProcessor);
        } else {
            Log.v(TAG, "No test to run, doing nothing");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    public BenchmarkResult runSynchronously(TestModelEntry testModel,
        float warmupTimeSeconds, float runTimeSeconds, boolean sampleResults) throws IOException, BenchmarkException {
        return mProcessor.getInstrumentationResult(testModel, warmupTimeSeconds, runTimeSeconds, sampleResults);
    }
}
