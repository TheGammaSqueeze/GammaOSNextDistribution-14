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
import android.text.TextUtils;
import android.util.Log;


import com.android.nn.crashtest.core.CrashTest;
import com.android.nn.crashtest.core.CrashTestCoordinator;

import java.io.File;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

public class RandomGraphTest implements CrashTest {
    private static final String TAG = "NN_RAND_MODEL";

    private static final boolean ENABLE_NNAPI_LOGS = false;

    private String getGeneratorOutFilePath(String fileExtension) {
        return mContext.getExternalFilesDir(null).getAbsolutePath() + "/"
                + mTestName.hashCode() + "." + fileExtension;
    }

    private String getNnapiLogFilePath() {
        if (ENABLE_NNAPI_LOGS) {
            String logFile = getGeneratorOutFilePath("model.py");
            Log.d(TAG, String.format("Writing NNAPI Fuzzer logs to %s", logFile));
            return logFile;
        } else {
            return "";
        }
    }

    private String getFailedModelDumpPath() {
        return getGeneratorOutFilePath("log");
    }

    static {
        System.loadLibrary("random_graph_test_jni");
    }

    private enum RandomModelExecutionResult {
        // This is the java translation of the RandomModelExecutionResult c++ enum in
        // random_graph_test_jni.cpp
        kSuccess(0, ""),
        kFailedCompilation(1, "Compilation failed"),
        kFailedExecution(2, "Execution failed"),
        kFailedOtherNnApiCall(3,
                "Failure trying to interact with the driver"),
        kInvalidModelGenerated(4, "Unable to generate a valid model"),
        kUnsupportedModelGenerated(5, "Unable to generate a model supported by the driver");


        private final int mValue;
        private final String mDescription;

        RandomModelExecutionResult(int value, String description) {
            mValue = value;
            mDescription = description;
        }

        public static RandomModelExecutionResult fromNativeResult(int nativeResult) {
            for (RandomModelExecutionResult currValue : RandomModelExecutionResult.values()) {
                if (currValue.mValue == nativeResult) {
                    return currValue;
                }
            }
            throw new IllegalArgumentException(
                    String.format("Invalid native result value %d", nativeResult));
        }
    }

    public static final String MAX_TEST_DURATION = "max_test_duration";
    public static final String GRAPH_SIZE = "graph_size";
    public static final String DIMENSIONS_RANGE = "dimensions_range";
    public static final String MODELS_COUNT = "models_count";
    public static final String PAUSE_BETWEEN_MODELS_MS = "pause_between_models_ms";
    public static final String COMPILATION_ONLY = "compilation_only";
    public static final String DEVICE_NAME = "device_name";
    public static final String TEST_NAME = "test_name";

    public static final int DEFAULT_GRAPH_SIZE = 100;
    public static final int DEFAULT_DIMENSIONS_RANGE = 100;
    public static final int DEFAULT_MODELS_COUNT = 100;
    public static final long DEFAULT_PAUSE_BETWEEN_MODELS_MILLIS = 300;
    public static final boolean DEFAULT_COMPILATION_ONLY = false;
    public static final long DEFAULT_MAX_TEST_DURATION_MILLIS = Duration.ofMinutes(2).toMillis();
    private static final long MAX_TIME_TO_LOOK_FOR_SUITABLE_MODEL_SECONDS = 30;

    static public CrashTestCoordinator.CrashTestIntentInitializer intentInitializer(int graphSize,
            int dimensionsRange, int modelsCount, long pauseBetweenModelsMillis,
            boolean compilationOnly, String deviceName, long maxTestDurationMillis,
            String testName) {
        return intent -> {
            intent.putExtra(GRAPH_SIZE, graphSize);
            intent.putExtra(DIMENSIONS_RANGE, dimensionsRange);
            intent.putExtra(MODELS_COUNT, modelsCount);
            intent.putExtra(PAUSE_BETWEEN_MODELS_MS, pauseBetweenModelsMillis);
            intent.putExtra(COMPILATION_ONLY, compilationOnly);
            intent.putExtra(DEVICE_NAME, deviceName);
            intent.putExtra(MAX_TEST_DURATION, maxTestDurationMillis);
            intent.putExtra(TEST_NAME, testName);
        };
    }

    static public CrashTestCoordinator.CrashTestIntentInitializer intentInitializer(
            Intent copyFrom) {
        return intentInitializer(
                copyFrom.getIntExtra(RandomGraphTest.GRAPH_SIZE,
                        RandomGraphTest.DEFAULT_GRAPH_SIZE),
                copyFrom.getIntExtra(
                        RandomGraphTest.DIMENSIONS_RANGE, RandomGraphTest.DEFAULT_DIMENSIONS_RANGE),
                copyFrom.getIntExtra(RandomGraphTest.MODELS_COUNT,
                        RandomGraphTest.DEFAULT_MODELS_COUNT),
                copyFrom.getLongExtra(RandomGraphTest.PAUSE_BETWEEN_MODELS_MS,
                        RandomGraphTest.DEFAULT_PAUSE_BETWEEN_MODELS_MILLIS),
                copyFrom.getBooleanExtra(
                        RandomGraphTest.COMPILATION_ONLY, RandomGraphTest.DEFAULT_COMPILATION_ONLY),
                copyFrom.getStringExtra(RandomGraphTest.DEVICE_NAME),
                copyFrom.getLongExtra(MAX_TEST_DURATION,
                        DEFAULT_MAX_TEST_DURATION_MILLIS),
                copyFrom.getStringExtra(RandomGraphTest.TEST_NAME));
    }

    private Context mContext;
    private String mDeviceName;
    private boolean mCompilationOnly;
    private int mGraphSize;
    private int mDimensionsRange;
    private int mModelsCount;
    private long mPauseBetweenModelsMillis;
    private Duration mMaxTestDuration;
    private String mTestName;

    public static native long createRandomGraphGenerator(String nnApiDeviceName, int numOperations,
            int dimensionRange,
            String testName, String nnapiLogPath, String failedModelDumpPath);

    public static native long destroyRandomGraphGenerator(long generatorHandle);

    private static native int runRandomModel(long generatorHandle,
            boolean compilationOnly, long maxModelSearchTimeSeconds);

    @Override
    public void init(Context context, Intent configParams,
            Optional<ProgressListener> progressListener) {
        mContext = context;
        mDeviceName = configParams.getStringExtra(DEVICE_NAME);
        mCompilationOnly = configParams.getBooleanExtra(COMPILATION_ONLY, DEFAULT_COMPILATION_ONLY);
        mGraphSize = configParams.getIntExtra(GRAPH_SIZE, DEFAULT_GRAPH_SIZE);
        mDimensionsRange = configParams.getIntExtra(DIMENSIONS_RANGE, DEFAULT_DIMENSIONS_RANGE);
        mModelsCount = configParams.getIntExtra(MODELS_COUNT, DEFAULT_MODELS_COUNT);
        mPauseBetweenModelsMillis =
                configParams.getLongExtra(PAUSE_BETWEEN_MODELS_MS,
                        DEFAULT_PAUSE_BETWEEN_MODELS_MILLIS);
        mMaxTestDuration =
                Duration.ofMillis(configParams.getLongExtra(MAX_TEST_DURATION,
                        DEFAULT_MAX_TEST_DURATION_MILLIS));
        mTestName = configParams.getStringExtra(TEST_NAME) != null
                ? configParams.getStringExtra(TEST_NAME)
                : "no-name";
    }

    @SuppressLint("DefaultLocale")
    @Override
    public Optional<String> call() throws Exception {
        LocalDateTime testStart = LocalDateTime.now();
        Log.i(TAG,
                String.format(String.format(
                        "Starting test '%s', testing %d models of size %d and dimension range %d "
                                + "for a max duration of %s on device %s.",
                        mTestName, mModelsCount, mGraphSize, mDimensionsRange, mMaxTestDuration,
                        mDeviceName != null ? mDeviceName : "no-device")));

        final long generatorHandle = RandomGraphTest.createRandomGraphGenerator(mDeviceName,
                mGraphSize, mDimensionsRange, mTestName, getNnapiLogFilePath(),
                getFailedModelDumpPath());
        if (generatorHandle == 0) {
            Log.e(TAG, "Unable to initialize random graph generator, failing test");
            return failure("Unable to initialize random graph generator");
        }
        try {
            for (int i = 0; i < mModelsCount; i++) {
                if (Duration.between(testStart, LocalDateTime.now()).plus(
                        Duration.ofSeconds(MAX_TIME_TO_LOOK_FOR_SUITABLE_MODEL_SECONDS)).compareTo(
                        mMaxTestDuration)
                        >= 0) {
                    Log.d(TAG, "Max test duration reached, ending test");
                    break;
                }

                int nativeExecutionResult = runRandomModel(generatorHandle,
                        mCompilationOnly, MAX_TIME_TO_LOOK_FOR_SUITABLE_MODEL_SECONDS);

                RandomModelExecutionResult executionResult =
                        RandomModelExecutionResult.fromNativeResult(nativeExecutionResult);

                if (executionResult != RandomModelExecutionResult.kSuccess) {
                    Log.w(TAG, String.format(
                            "Received failure result '%s' at iteration %d, failing",
                            executionResult.mDescription, i));
                    if (executionResult == RandomModelExecutionResult.kFailedExecution ||
                            executionResult == RandomModelExecutionResult.kFailedCompilation) {
                        Log.i(TAG, String.format("Model has been dumped at path '%s'",
                                getFailedModelDumpPath()));
                    } else if (
                            executionResult == RandomModelExecutionResult.kUnsupportedModelGenerated
                                    || executionResult
                                    == RandomModelExecutionResult.kInvalidModelGenerated) {
                        Log.w(TAG, String.format(
                                "Unable to find a valid model for test '%s', returning success "
                                        + "anyway",
                                mTestName));

                        return success();
                    }

                    return failure(executionResult.mDescription);
                } else if (!TextUtils.isEmpty(getNnapiLogFilePath())) {
                    (new File(getNnapiLogFilePath())).delete();
                }

                Thread.sleep(mPauseBetweenModelsMillis);
            }

            return success();
        } finally {
            RandomGraphTest.destroyRandomGraphGenerator(generatorHandle);
        }
    }
}
