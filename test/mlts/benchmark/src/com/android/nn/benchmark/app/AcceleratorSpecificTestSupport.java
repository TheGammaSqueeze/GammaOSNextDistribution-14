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

package com.android.nn.benchmark.app;

import android.content.Context;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

import com.android.nn.benchmark.core.BenchmarkException;
import com.android.nn.benchmark.core.BenchmarkResult;
import com.android.nn.benchmark.core.NNTestBase;
import com.android.nn.benchmark.core.NnApiDelegationFailure;
import com.android.nn.benchmark.core.Processor;
import com.android.nn.benchmark.core.TestModels;
import com.android.nn.benchmark.core.TfLiteBackend;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public interface AcceleratorSpecificTestSupport {
    String TAG = "AcceleratorTest";

    static Optional<TestModels.TestModelEntry> findTestModelRunningOnAccelerator(
            Context context, String acceleratorName) throws NnApiDelegationFailure {
        for (TestModels.TestModelEntry model : TestModels.modelsList()) {
            if (Processor.isTestModelSupportedByAccelerator(context, model, acceleratorName)) {
                return Optional.of(model);
            }
        }
        return Optional.empty();
    }

    static List<TestModels.TestModelEntry> findAllTestModelsRunningOnAccelerator(
            Context context, String acceleratorName) throws NnApiDelegationFailure {
        List<TestModels.TestModelEntry> result = new ArrayList<>();
        for (TestModels.TestModelEntry model : TestModels.modelsList()) {
            if (Processor.isTestModelSupportedByAccelerator(context, model, acceleratorName)) {
                result.add(model);
            }
        }
        return result;
    }

    default long ramdomInRange(long min, long max) {
        return min + (long) (Math.random() * (max - min));
    }

    static String getTestParameter(String key, String defaultValue) {
        return InstrumentationRegistry.getArguments().getString(key, defaultValue);
    }

    static boolean getBooleanTestParameter(String key, boolean defaultValue) {
        // All instrumentation arguments are passed as String so I have to convert the value here.
        return Boolean.parseBoolean(
                InstrumentationRegistry.getArguments().getString(key, "" + defaultValue));
    }

    static final String ACCELERATOR_FILTER_PROPERTY = "nnCrashtestDeviceFilter";
    static final String INCLUDE_NNAPI_SELECTED_ACCELERATOR_PROPERTY =
            "nnCrashtestIncludeNnapiReference";

    static List<String> getTargetAcceleratorNames() {
        List<String> accelerators = new ArrayList<>();
        String acceleratorFilter = getTestParameter(ACCELERATOR_FILTER_PROPERTY, ".+");
        accelerators.addAll(NNTestBase.availableAcceleratorNames().stream().filter(
                name -> name.matches(acceleratorFilter)).collect(
                Collectors.toList()));
        if (getBooleanTestParameter(INCLUDE_NNAPI_SELECTED_ACCELERATOR_PROPERTY, false)) {
            accelerators.add(null); // running tests with no specified target accelerator too
        }
        return accelerators;
    }

    // This method returns an empty list if no accelerator name has been specified.
    static List<String> getOptionalTargetAcceleratorNames() {
        List<String> accelerators = new ArrayList<>();
        String acceleratorFilter = getTestParameter(ACCELERATOR_FILTER_PROPERTY, "");
        if (acceleratorFilter.isEmpty()) {
            return Collections.emptyList();
        }
        accelerators.addAll(NNTestBase.availableAcceleratorNames().stream().filter(
            name -> name.matches(acceleratorFilter)).collect(
            Collectors.toList()));
        if (getBooleanTestParameter(INCLUDE_NNAPI_SELECTED_ACCELERATOR_PROPERTY, false)) {
            accelerators.add(null); // running tests with no specified target accelerator too
        }
        return accelerators;
    }

    static List<Object[]> perAcceleratorTestConfig(List<Object[]> testConfig) {
        return testConfig.stream()
                .flatMap(currConfigurationParams -> getTargetAcceleratorNames().stream().map(
                        accelerator -> {
                            Object[] result =
                                    Arrays.copyOf(currConfigurationParams,
                                            currConfigurationParams.length + 1);
                            result[currConfigurationParams.length] = accelerator;
                            return result;
                        }))
                .collect(Collectors.toList());
    }

    // Generates a per-accelerator list of test configurations if an accelerator filter has been
    // specified. Will return the origin list with an extra `null` parameter for the accelerator
    // name if not.
    static List<Object[]> maybeAddAcceleratorsToTestConfig(List<Object[]> testConfig) {
        return testConfig.stream()
            .flatMap(currConfigurationParams -> {
                List<String> accelerators = getOptionalTargetAcceleratorNames();
                if (accelerators.isEmpty()) {
                    accelerators = Collections.singletonList((String)null);
                }
                return accelerators.stream().map(
                    accelerator -> {
                        Object[] result =
                            Arrays.copyOf(currConfigurationParams,
                                currConfigurationParams.length + 1);
                        result[currConfigurationParams.length] = accelerator;
                        return result;
                    });
            })
            .collect(Collectors.toList());
    }

    class DriverLivenessChecker implements Callable<Boolean> {
        final Processor mProcessor;
        private final AtomicBoolean mRun = new AtomicBoolean(true);
        private final TestModels.TestModelEntry mTestModelEntry;

        public DriverLivenessChecker(Context context, String acceleratorName,
                TestModels.TestModelEntry testModelEntry) {
            mProcessor = new Processor(context,
                    new Processor.Callback() {
                        @Override
                        public void onBenchmarkFinish(boolean ok) {
                        }

                        @Override
                        public void onStatusUpdate(int testNumber, int numTests, String modelName) {
                        }
                    }, new int[0]);
            mProcessor.setTfLiteBackend(TfLiteBackend.NNAPI);
            mProcessor.setCompleteInputSet(false);
            mProcessor.setNnApiAcceleratorName(acceleratorName);
            mProcessor.setUseNnApiSupportLibrary(NNTestBase.shouldUseNnApiSupportLibrary());
            mProcessor.setExtractNnApiSupportLibrary(NNTestBase.shouldExtractNnApiSupportLibrary());
            mProcessor.setNnApiSupportLibraryVendor(NNTestBase.getNnApiSupportLibraryVendor());
            mTestModelEntry = testModelEntry;
        }

        public void stop() {
            mRun.set(false);
        }

        @Override
        public Boolean call() throws Exception {
            while (mRun.get()) {
                try {
                    BenchmarkResult modelExecutionResult = mProcessor.getInstrumentationResult(
                            mTestModelEntry, 0, 3);
                    if (modelExecutionResult.hasBenchmarkError()) {
                        Log.e(TAG, String.format("Benchmark failed with message %s",
                                modelExecutionResult.getBenchmarkError()));
                        return false;
                    }
                } catch (IOException | BenchmarkException e) {
                    Log.e(TAG, String.format("Error running model %s", mTestModelEntry.mModelName), e);
                    return false;
                }
            }

            return true;
        }
    }
}
