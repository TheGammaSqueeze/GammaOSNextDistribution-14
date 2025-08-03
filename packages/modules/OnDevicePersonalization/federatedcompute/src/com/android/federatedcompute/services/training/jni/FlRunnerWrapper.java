/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.federatedcompute.services.training.jni;

import static com.android.federatedcompute.services.common.Constants.TRACE_NATIVE_RUN_FEDERATED_COMPUTATION;

import android.os.Trace;

import com.android.federatedcompute.internal.util.LogUtil;
import com.android.federatedcompute.services.examplestore.ExampleIterator;
import com.android.federatedcompute.services.training.util.ListenableSupplier;

import com.google.intelligence.fcp.client.FLRunnerResult;
import com.google.internal.federated.plan.ClientOnlyPlan;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.Closeable;

import javax.annotation.Nullable;

/** Runs a federated computation using C++ code. */
public final class FlRunnerWrapper implements Closeable {
    private static final String TAG = FlRunnerWrapper.class.getSimpleName();

    static {
        System.loadLibrary("fcp_cpp_dep_jni");
    }

    private final LogManager mLogManager;

    private final ListenableSupplier<Boolean> mInterruptionFlag;
    private final ExampleIterator mExampleIterator;

    public FlRunnerWrapper(
            ListenableSupplier<Boolean> interruptionFlag,
            String populationName,
            ExampleIterator exampleIterator) {
        this.mLogManager = new LogManagerImpl(populationName);
        this.mInterruptionFlag = interruptionFlag;
        this.mExampleIterator = exampleIterator;
    }

    /** Starts run a federated computation job. */
    public FLRunnerResult run(
            String taskName,
            String populationName,
            ClientOnlyPlan clientOnlyPlan,
            String checkpointInputFileName,
            String checkpointOutputFileName) {
        Trace.beginAsyncSection(TRACE_NATIVE_RUN_FEDERATED_COMPUTATION, 0);
        SimpleTaskEnvironmentImpl simpleTaskEnv =
                new SimpleTaskEnvironmentImpl(mInterruptionFlag, mExampleIterator);
        byte[] flRunnerResultSerialized =
                runNativeFederatedComputation(
                        simpleTaskEnv,
                        populationName,
                        // Session name is optional and mainly used by legacy customers
                        "",
                        taskName,
                        mLogManager,
                        clientOnlyPlan.toByteArray(),
                        checkpointInputFileName,
                        checkpointOutputFileName);
        try {
            return FLRunnerResult.parseFrom(flRunnerResultSerialized);
        } catch (InvalidProtocolBufferException e) {
            // Promote to a RuntimeException, this should never happen and if it does, we shouldn't
            // recover from it.
            LogUtil.e(TAG, "Cannot parse FLRunnerResult", e);
            throw new IllegalArgumentException(e);
        } finally {
            Trace.endAsyncSection(TRACE_NATIVE_RUN_FEDERATED_COMPUTATION, 0);
        }
    }

    @Override
    public void close() {}

    @Nullable
    static native byte[] runNativeFederatedComputation(
            SimpleTaskEnvironment simpleTaskEnvironment,
            String populationName,
            String sessionName,
            String taskName,
            LogManager logManager,
            byte[] clientOnlyPlan,
            String checkpointInputFileName,
            String checkpointOutputFileName);
}
