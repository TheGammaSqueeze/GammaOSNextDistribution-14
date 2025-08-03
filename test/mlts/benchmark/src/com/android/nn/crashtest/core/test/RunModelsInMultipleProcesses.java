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
import com.android.nn.benchmark.core.InferenceInOut;
import com.android.nn.benchmark.core.InferenceInOutSequence;
import com.android.nn.benchmark.core.NNTestBase;
import com.android.nn.benchmark.core.TestModels;
import com.android.nn.crashtest.core.CrashTest;
import com.android.nn.crashtest.core.CrashTestCoordinator;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class RunModelsInMultipleProcesses implements CrashTest {
  private static final String TAG = "NN_MPROC_STRESS";
  private static final String NATIVE_PROCESS_CMD = "nn_stress_test";

  public static final String THREADS = "thread_counts";
  public static final String PROCESSES = "process_counts";
  public static final String TEST_NAME = "test_name";
  public static final String MODEL_NAME = "model_name";
  public static final String TEST_DURATION = "test_duration";
  public static final String NNAPI_DEVICE_NAME = "nnapi_device_name";
  public static final String JUST_COMPILE = "just_compile";
  public static final String CLIENT_FAILURE_RATE_PERCENT = "client_failure_rate_percent";
  public static final long DEFAULT_TEST_DURATION = Duration.ofSeconds(60).toMillis();
  public static final int DEFAULT_PROCESSES = 3;
  public static final int DEFAULT_THREADS = 1;
  public static final boolean DEFAULT_JUST_COMPILE = false;
  public static final int DEFAULT_CLIENT_FAILURE_RATE_PERCENT = 0;

  private Context mContext;
  private int mThreadCount;
  private int mProcessCount;
  private String mTestName;
  private TestModels.TestModelEntry mTestModelEntry;
  private Duration mTestDuration;
  private String mNnApiDeviceName;
  private boolean mJustCompileModel;
  private int mClientFailureRatePercent;

  static public CrashTestCoordinator.CrashTestIntentInitializer intentInitializer(String testName,
      String modelName, int processCount, int threadCount, Duration duration,
      String nnApiDeviceName, boolean justCompileModel, int clientFailureRatePercent) {
    return intent -> {
      intent.putExtra(TEST_NAME, testName);
      intent.putExtra(MODEL_NAME, modelName);
      intent.putExtra(PROCESSES, processCount);
      intent.putExtra(THREADS, threadCount);
      intent.putExtra(TEST_DURATION, duration.toMillis());
      intent.putExtra(NNAPI_DEVICE_NAME, nnApiDeviceName);
      intent.putExtra(JUST_COMPILE, justCompileModel);
      intent.putExtra(CLIENT_FAILURE_RATE_PERCENT, clientFailureRatePercent);
    };
  }
  static public CrashTestCoordinator.CrashTestIntentInitializer intentInitializer(
            Intent copyFrom) {
    return intentInitializer(
            copyFrom.getStringExtra(RunModelsInMultipleProcesses.TEST_NAME),
            copyFrom.getStringExtra(RunModelsInMultipleProcesses.MODEL_NAME),
            copyFrom.getIntExtra(RunModelsInMultipleProcesses.PROCESSES, DEFAULT_PROCESSES),
            copyFrom.getIntExtra(RunModelsInMultipleProcesses.THREADS, DEFAULT_THREADS),
            Duration.ofMillis(copyFrom.getLongExtra(TEST_DURATION, DEFAULT_TEST_DURATION)),
            copyFrom.getStringExtra(RunModelsInMultipleProcesses.NNAPI_DEVICE_NAME),
            copyFrom.getBooleanExtra(RunModelsInMultipleProcesses.JUST_COMPILE,
                    DEFAULT_JUST_COMPILE),
            copyFrom.getIntExtra(RunModelsInMultipleProcesses.CLIENT_FAILURE_RATE_PERCENT,
                    DEFAULT_CLIENT_FAILURE_RATE_PERCENT));
  }

  @Override
  public void init(
      Context context, Intent configParams, Optional<ProgressListener> progressListener) {
    mContext = context;
    mTestName = configParams.getStringExtra(TEST_NAME);
    mTestModelEntry = TestModels.getModelByName(configParams.getStringExtra(MODEL_NAME));
    mProcessCount = configParams.getIntExtra(PROCESSES, DEFAULT_PROCESSES);
    mThreadCount = configParams.getIntExtra(THREADS, DEFAULT_THREADS);
    mTestDuration = Duration.ofMillis(
        configParams.getLongExtra(TEST_DURATION, DEFAULT_TEST_DURATION));
    mNnApiDeviceName = configParams.getStringExtra(NNAPI_DEVICE_NAME);
    mJustCompileModel = configParams.getBooleanExtra(JUST_COMPILE, DEFAULT_JUST_COMPILE);
    mClientFailureRatePercent = configParams.getIntExtra(CLIENT_FAILURE_RATE_PERCENT,
            DEFAULT_CLIENT_FAILURE_RATE_PERCENT);
  }

  private void deleteOrWarn(File fileToDelete) {
    if (fileToDelete.exists()) {
      if (!fileToDelete.delete()) {
        Log.w(TAG, String.format("Unable to delete file %s", fileToDelete.getAbsolutePath()));
      }
    }
  }

  @SuppressLint("DefaultLocale")
  @Override
  public Optional<String> call() throws Exception {
    File targetModelFile =
        new File(mContext.getExternalFilesDir(null), mTestModelEntry.mModelFile + ".tflite");
    File targetInputFile =
        new File(mContext.getExternalFilesDir(null), mTestModelEntry.mModelFile + ".input");
    try {
      Log.i(TAG,
          String.format("Trying to create model path '%s'", targetModelFile.getAbsolutePath()));
      if (!NNTestBase.copyModelToFile(
              mContext, mTestModelEntry.mModelFile + ".tflite", targetModelFile)) {
        return failure(String.format("Unable to copy model to target %s file %s",
            mTestModelEntry.mModelFile, targetModelFile.getAbsolutePath()));
      }
      List<InferenceInOutSequence> inputOutputAssets = NNTestBase.getInputOutputAssets(
          mContext, mTestModelEntry.mInOutAssets, mTestModelEntry.mInOutDatasets);

      if (!mJustCompileModel) {
        if (!writeModelInput(targetInputFile, inputOutputAssets, mTestModelEntry.mInputShape,
                mTestModelEntry.mInDataSize)) {
          return failure(String.format("Cannot write test input data file %s for model %s",
              targetInputFile.getAbsolutePath(), mTestModelEntry.mModelName));
        }
      }

      String inputShapeAsString = Arrays.toString(mTestModelEntry.mInputShape);
      inputShapeAsString = inputShapeAsString.substring(1, inputShapeAsString.length() - 1);
      ProcessBuilder multiProcessTestBuilder = new ProcessBuilder();
      multiProcessTestBuilder.command(NATIVE_PROCESS_CMD, targetModelFile.getAbsolutePath(),
          mJustCompileModel ? "no-file" : targetInputFile.getAbsolutePath(), inputShapeAsString,
          "" + mTestModelEntry.mInDataSize, "" + mProcessCount, "" + mThreadCount,
          "" + mTestDuration.getSeconds(), mTestName, ("" + mJustCompileModel).toLowerCase(),
          mClientFailureRatePercent + "", mNnApiDeviceName != null ? mNnApiDeviceName : "");

      Process multiProcessTest = multiProcessTestBuilder.start();

      int testResult = multiProcessTest.waitFor();
      Log.i(TAG, String.format("Test process returned %d", testResult));
      if (testResult == 0) {
        return success();
      } else {
        return failure(String.format("Test failed with return code %d", testResult));
      }
    } finally {
      deleteOrWarn(targetModelFile);
      deleteOrWarn(targetInputFile);
    }
  }

  private boolean writeModelInput(File targetInputFile,
      List<InferenceInOutSequence> inputOutputAssets, int[] inputShape, int dataByteSize)
      throws IOException {
    if (!targetInputFile.exists() && !targetInputFile.createNewFile()) {
      Log.w(TAG,
          String.format(
              "Cannot create test input data file %s", targetInputFile.getAbsolutePath()));
      return false;
    }

    boolean hasContent = false;
    try (FileOutputStream inputDataWriter = new FileOutputStream(targetInputFile)) {
      for (InferenceInOutSequence inferenceInOutSequence : inputOutputAssets) {
        for (int i = 0; i < inferenceInOutSequence.size(); i++) {
          byte[] input = inferenceInOutSequence.get(i).mInput;
          final InferenceInOut.InputCreatorInterface creator =
              inferenceInOutSequence.get(i).mInputCreator;
          if (input == null && creator != null) {
            int byteSize = dataByteSize;
            for (int dimensionSize : inputShape) {
              byteSize *= dimensionSize;
            }
            input = new byte[byteSize];
            ByteBuffer buffer = ByteBuffer.wrap(input);
            creator.createInput(buffer);
          }
          if (input != null) {
            hasContent = true;
            inputDataWriter.write(input);
          }
        }
      }
    } catch (IOException writeException) {
      Log.w(TAG, String.format("Cannot write to target file %s", targetInputFile.getAbsolutePath()),
          writeException);
      return false;
    }

    if (!hasContent) {
      Log.w(TAG,
          String.format("No content in inference input sequence to write for file %s",
              targetInputFile.getAbsolutePath()));
    }

    return hasContent;
  }
}