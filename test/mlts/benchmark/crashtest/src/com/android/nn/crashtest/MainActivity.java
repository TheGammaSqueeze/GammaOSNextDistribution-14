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

package com.android.nn.crashtest;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.NumberPicker;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.android.nn.benchmark.core.NNTestBase;
import com.android.nn.benchmark.core.NnApiDelegationFailure;
import com.android.nn.benchmark.core.Processor;
import com.android.nn.benchmark.core.TestModels;
import com.android.nn.benchmark.core.TestModelsListLoader;
import com.android.nn.crashtest.core.CrashTestCoordinator;
import com.android.nn.crashtest.core.test.RunModelsInParallel;
import com.android.nn.benchmark.util.TestExternalStorageActivity;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class MainActivity extends AppCompatActivity {

    private static final String ALL_AVAILABLE_ACCELERATORS = "All available";
    private static final String TAG = "NN_STRESS_TEST";
    private static final int JOB_FREQUENCY_MILLIS = 15 * 60 * 1000; // 15 minutes
    private final AtomicInteger mSelectedModelIndex = new AtomicInteger(-1);
    private final AtomicBoolean mUseSeparateProcess = new AtomicBoolean(true);
    private final AtomicReference<String> mAcceleratorName = new AtomicReference<>(null);
    AtomicBoolean mTestRunning = new AtomicBoolean(false);
    private Button mStartTestButton;
    private TextView mMessage;
    private NumberPicker mThreadCount;
    private NumberPicker mTestDurationMinutes;
    private ArrayAdapter<String> mModelsAdapter;
    private List<String> mAllTestModels;
    private CheckBox mMmapModel;
    private CheckBox mCompileModelsOnly;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        TestExternalStorageActivity.testWriteExternalStorage(this, true);

        mStartTestButton = (Button) findViewById(R.id.start_button);
        mMessage = (TextView) findViewById(R.id.message);

        try {
            TestModelsListLoader.parseFromAssets(getAssets());
        } catch (IOException e) {
            Log.e(TAG, "Could not load models", e);
        }

        mAllTestModels = TestModels.modelsList().stream().map(
                TestModels.TestModelEntry::getTestName).collect(
                Collectors.toList());

        final List<String> modelNames = new ArrayList<>();
        modelNames.add("All Models");
        modelNames.addAll(modelsForAccelerator(ALL_AVAILABLE_ACCELERATORS));
        mModelsAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item,
                modelNames);
        mModelsAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        final Spinner testModelSpinner = (Spinner) findViewById(R.id.test_model);
        testModelSpinner.setAdapter(mModelsAdapter);
        testModelSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                mSelectedModelIndex.set((int) id - 1);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                mSelectedModelIndex.set(-1);
            }
        });

        final List<String> acceleratorNames = new ArrayList<>();
        acceleratorNames.add(ALL_AVAILABLE_ACCELERATORS);
        acceleratorNames.addAll(NNTestBase.availableAcceleratorNames());
        final ArrayAdapter<String> acceleratorNamesAdapter = new ArrayAdapter<String>(
                this,
                android.R.layout.simple_spinner_item, acceleratorNames);
        final Spinner acceleratorNameSpinner = (Spinner) findViewById(R.id.accelerator_name);
        acceleratorNameSpinner.setAdapter(acceleratorNamesAdapter);
        acceleratorNameSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                mAcceleratorName.set(position == 0 ? null : acceleratorNames.get(position));
                mModelsAdapter.clear();
                mModelsAdapter.addAll(modelsForAccelerator(mAcceleratorName.get()));
                mModelsAdapter.notifyDataSetChanged();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                onItemSelected(parent, null, 0, 0);
            }
        });

        final ArrayAdapter<String> testTypeAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_spinner_item,
                new String[]{"Separate process", "In process"});
        testTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        final Spinner testTypeSpinner = (Spinner) findViewById(R.id.test_type);
        testTypeSpinner.setAdapter(testTypeAdapter);
        testTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                mUseSeparateProcess.set(position == 0);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                mUseSeparateProcess.set(true);
            }
        });


        mThreadCount = (NumberPicker) findViewById(R.id.thread_count);
        mTestDurationMinutes = (NumberPicker) findViewById(R.id.duration_minutes);

        mThreadCount.setMinValue(1);
        mThreadCount.setMaxValue(20);

        mTestDurationMinutes.setMinValue(1);
        mTestDurationMinutes.setMaxValue(60);

        mMmapModel = (CheckBox) findViewById(R.id.mmap_model);
        mCompileModelsOnly = (CheckBox) findViewById(R.id.compile_only);
    }

    private List<String> modelsForAccelerator(String acceleratorName) {
        List<String> result = new ArrayList<>();
        if (acceleratorName == null || acceleratorName.equals(ALL_AVAILABLE_ACCELERATORS)) {
            result.add("All models");
            result.addAll(mAllTestModels);
        } else {
            result.add("All supported models");
            result.addAll(TestModels.modelsList().stream()
                    .map(TestModels.TestModelEntry::withDisabledEvaluation).filter(
                            model -> {
                                try {
                                    return Processor.isTestModelSupportedByAccelerator(
                                            this,
                                            model, acceleratorName);
                                } catch (NnApiDelegationFailure nnApiDelegationFailure) {
                                    runOnUiThread(() -> {
                                        mMessage.append(String.format(
                                                "Driver %s failed when trying to check support "
                                                        + "for model %s!!\n",
                                                acceleratorName, model.mModelName));
                                        ByteArrayOutputStream stsackTraceByteOS =
                                                new ByteArrayOutputStream();
                                        try (PrintStream stackTracePrintStream = new PrintStream(
                                                stsackTraceByteOS)) {
                                            nnApiDelegationFailure.printStackTrace(
                                                    stackTracePrintStream);
                                            mMessage.append(
                                                    stackTracePrintStream.toString() + "\n");
                                        }
                                        mStartTestButton.setEnabled(true);
                                    });
                                    return false;
                                }
                            }).map(
                            TestModels.TestModelEntry::getTestName).collect(
                            Collectors.toList()));
        }

        return result;
    }

    void testStopped(String msg) {
        Log.i(TAG, "Test stopped " + msg);
        mTestRunning.set(false);
        runOnUiThread(() -> {
            mMessage.append(msg + "\n");
            mStartTestButton.setEnabled(true);
        });
    }

    public void startTestClicked(View v) {
        Log.i(TAG, "Starting test");

        if (mTestRunning.getAndSet(true)) {
            return;
        }

        mStartTestButton.setEnabled(false);

        startInferenceTest();
    }

    @SuppressLint("DefaultLocale")
    private void startInferenceTest() {
        CrashTestCoordinator coordinator = new CrashTestCoordinator(this);

        int threadCount = mThreadCount.getValue();
        int testDurationMinutes = mTestDurationMinutes.getValue();

        int[] testList;
        if (mSelectedModelIndex.get() < 0) {
            testList = new int[mModelsAdapter.getCount()];
            // The first item is the 'all models' or 'all supported models' entry
            for (int i = 1; i < mModelsAdapter.getCount(); i++) {
                String modelName = mModelsAdapter.getItem(i);
                testList[i] = mAllTestModels.indexOf(modelName);
            }
        } else {
            testList = new int[]{mSelectedModelIndex.get()};
        }

        CrashTestCoordinator.CrashTestCompletionListener testCompletionListener =
                new CrashTestCoordinator.CrashTestCompletionListener() {
                    @Override
                    public void testCrashed() {
                        testStopped("Test crashed");
                    }

                    @Override
                    public void testSucceeded() {
                        testStopped("Test succeeded");
                    }

                    @Override
                    public void testFailed(String reason) {
                        testStopped("Test failed with reason " + reason);
                    }

                    @Override
                    public void testProgressing(Optional<String> description) {
                        Log.i(TAG, "> " + description.orElse("Test progressing.."));
                        // Ignoring message to avoid cluttering the test Text Area
                        runOnUiThread(() -> mMessage.append("."));
                    }
                };

        final int testTimeoutMillis = testDurationMinutes * 1500;
        final String testName = "in-app-test@" + System.currentTimeMillis();
        final String acceleratorName = mAcceleratorName.get();
        final boolean mmapModel = mMmapModel.isChecked();
        final boolean runModelCompilationOnly = mCompileModelsOnly.isChecked();
        final boolean useNnapiSl = NNTestBase.shouldUseNnApiSupportLibrary();
        final boolean extractNnapiSl = NNTestBase.shouldExtractNnApiSupportLibrary();
        coordinator.startTest(RunModelsInParallel.class,
            RunModelsInParallel.intentInitializer(testList, threadCount,
                Duration.ofMinutes(testDurationMinutes),
                testName, acceleratorName, false,
                runModelCompilationOnly, mmapModel, TestModels.getModelFilterRegex(), useNnapiSl,
                extractNnapiSl),
            testCompletionListener,
            mUseSeparateProcess.get(), testName);

        mMessage.setText(
                String.format(
                        "%s test started with %d threads for %d minutes on %s\n",
                        runModelCompilationOnly ? "Compilation" : "Inference",
                        threadCount,
                        testDurationMinutes,
                        acceleratorName != null ? "accelerator " + acceleratorName
                                : "NNAPI-selected accelerator"));

    }

}
