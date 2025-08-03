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

package com.android.federatedcompute.services.training;

import android.federatedcompute.aidl.IExampleStoreIterator;

import com.android.federatedcompute.services.examplestore.ExampleConsumptionRecorder;
import com.android.federatedcompute.services.examplestore.FederatedExampleIterator;
import com.android.federatedcompute.services.training.jni.FlRunnerWrapper;
import com.android.federatedcompute.services.training.util.ListenableSupplier;

import com.google.intelligence.fcp.client.FLRunnerResult;
import com.google.internal.federated.plan.ClientOnlyPlan;
import com.google.internal.federated.plan.ExampleSelector;

/**
 * Centralized class for running a single computation session. It calls to native fcp client to
 * start federated ananlytic and federated training jobs.
 */
public class ComputationRunner {

    public ComputationRunner() {}

    /** Run a single round of federated computation. */
    public FLRunnerResult runTaskWithNativeRunner(
            String taskName,
            String populationName,
            String inputCheckpointFd,
            String outputCheckpointFd,
            ClientOnlyPlan clientOnlyPlan,
            ExampleSelector exampleSelector,
            ExampleConsumptionRecorder recorder,
            IExampleStoreIterator exampleStoreIterator,
            ListenableSupplier<Boolean> interruptState) {
        byte[] resumptionToken = exampleSelector.getResumptionToken().toByteArray();
        FederatedExampleIterator federatedExampleIterator =
                new FederatedExampleIterator(
                        exampleStoreIterator,
                        resumptionToken,
                        recorder.createRecorderForTracking(taskName, resumptionToken));

        FlRunnerWrapper flRunnerWrapper =
                new FlRunnerWrapper(interruptState, populationName, federatedExampleIterator);

        FLRunnerResult runResult =
                flRunnerWrapper.run(
                        taskName,
                        populationName,
                        clientOnlyPlan,
                        inputCheckpointFd,
                        outputCheckpointFd);

        return runResult;
    }
}
