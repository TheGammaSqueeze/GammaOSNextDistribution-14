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


import static com.android.nn.crashtest.app.CrashTestStatus.TestResult.HANG;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import com.android.nn.benchmark.app.R;
import com.android.nn.benchmark.core.TestModels;
import com.android.nn.crashtest.core.CrashTestCoordinator;
import com.android.nn.crashtest.core.test.RunModelsInParallel;

import java.time.Duration;


public class NNParallelTestActivity extends Activity {
    public static final int SHUTDOWN_TIMEOUT = 20000;

    private static String TAG = "NNParallelTestActivity";

    public static final String EXTRA_TEST_DURATION_MILLIS = "duration";
    public static final String EXTRA_THREAD_COUNT = "thread_count";
    public static final String EXTRA_TEST_LIST = "test_list";
    public static final String EXTRA_RUN_IN_SEPARATE_PROCESS = "run_in_separate_process";
    public static final String EXTRA_TEST_NAME = "test_name";
    public static final String EXTRA_ACCELERATOR_NAME = "accelerator_name";
    public static final String EXTRA_IGNORE_UNSUPPORTED_MODELS = "ignore_unsupported_models";
    public static final String EXTRA_RUN_MODEL_COMPILATION_ONLY = "run_model_compilation_only";
    public static final String EXTRA_MEMORY_MAP_MODEL = "memory_map_model";
    public static final String EXTRA_USE_NNAPI_SL = "use_nnapi_sl";
    public static final String EXTRA_EXTRACT_NNAPI_SL = "extract_nnapi_sl";

    // Not using AtomicBoolean to have the concept of unset status
    private CrashTestCoordinator mCoordinator;
    private TextView mTestResultView;
    private Button mStopTestButton;
    private String mTestName;

    private final CrashTestStatus mTestStatus = new CrashTestStatus(this::showMessage);

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.interruptable_test);
        mTestResultView = findViewById(R.id.parallel_test_result);
        mStopTestButton = findViewById(R.id.stop_test);
        mStopTestButton.setEnabled(false);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    protected void showMessage(String msg) {
        runOnUiThread(() -> mTestResultView.append(msg));
    }


    @Override
    protected void onResume() {
        super.onResume();

        if (mTestStatus.isTestCompleted()) {
            // test was completed before resuming
            return;
        }
        if (mCoordinator != null) {
            // test is already running.
            return;
        }

        final Intent intent = getIntent();

        final int[] testList = intent.getIntArrayExtra(EXTRA_TEST_LIST);

        final int threadCount = intent.getIntExtra(EXTRA_THREAD_COUNT, 10);
        final long testDurationMillis = intent.getLongExtra(EXTRA_TEST_DURATION_MILLIS,
                1000 * 60 * 10);
        final boolean runInSeparateProcess = intent.getBooleanExtra(EXTRA_RUN_IN_SEPARATE_PROCESS,
                true);
        mTestName = intent.getStringExtra(EXTRA_TEST_NAME) != null
                ? intent.getStringExtra(EXTRA_TEST_NAME) : "no-name";

        mCoordinator = new CrashTestCoordinator(getApplicationContext());

        String acceleratorName = intent.getStringExtra(EXTRA_ACCELERATOR_NAME);
        boolean ignoreUnsupportedModels = intent.getBooleanExtra(EXTRA_IGNORE_UNSUPPORTED_MODELS,
                false);
        boolean mmapModel = intent.getBooleanExtra(EXTRA_MEMORY_MAP_MODEL, false);
        boolean useNnapiSl = intent.getBooleanExtra(EXTRA_USE_NNAPI_SL, false);
        boolean extractNnapiSl = intent.getBooleanExtra(EXTRA_EXTRACT_NNAPI_SL, false);

        final boolean runModelCompilationOnly = intent.getBooleanExtra(
                EXTRA_RUN_MODEL_COMPILATION_ONLY, false);

        mCoordinator.startTest(RunModelsInParallel.class,
            RunModelsInParallel.intentInitializer(testList, threadCount,
                Duration.ofMillis(testDurationMillis), mTestName, acceleratorName,
                ignoreUnsupportedModels, runModelCompilationOnly, mmapModel,
                TestModels.getModelFilterRegex(), useNnapiSl, extractNnapiSl),
            mTestStatus, runInSeparateProcess, mTestName);

        mStopTestButton.setEnabled(true);
    }

    private void endTests() {
        mCoordinator.shutdown();
        mCoordinator = null;
    }

    // This method blocks until the tests complete and returns true if all tests completed
    // successfully
    @SuppressLint("DefaultLocale")
    public CrashTestStatus.TestResult testResult() {
        try {
            final Intent intent = getIntent();
            final long testDurationMillis = intent.getLongExtra(EXTRA_TEST_DURATION_MILLIS,
                    60 * 10);
            // Giving the test a bit of time to wrap up
            final long testResultTimeout = testDurationMillis + SHUTDOWN_TIMEOUT;
            boolean completed = mTestStatus.waitForCompletion(testResultTimeout, MILLISECONDS);
            if (!completed) {
                showMessage(String.format(
                        "Ending test '%s' since test result collection timeout of %d "
                                + "millis is expired",
                        mTestName, testResultTimeout));
                endTests();
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }

        // If no result is available, assuming HANG
        mTestStatus.compareAndSetResult(null, HANG);
        return mTestStatus.result();
    }

    public void onStopTestClicked(View view) {
        showMessage("Stopping tests");
        endTests();
    }

    /**
     * Kills the process running the tests.
     *
     * @throws IllegalStateException if the method is called for an in-process test.
     * @throws RemoteException       if the test service is not reachable
     */
    public void killTestProcess() throws RemoteException {
        final Intent intent = getIntent();

        final boolean runInSeparateProcess = intent.getBooleanExtra(EXTRA_RUN_IN_SEPARATE_PROCESS,
                true);

        if (!runInSeparateProcess) {
            throw new IllegalStateException("Cannot kill the test process in an in-process test!");
        }

        Log.i(TAG, "Asking coordinator to kill test process");
        mCoordinator.killCrashTestService();
    }
}
