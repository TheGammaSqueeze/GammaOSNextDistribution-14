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

import static com.android.federatedcompute.services.common.Constants.TRACE_ISOLATED_PROCESS_RUN_FL_TRAINING;

import android.annotation.NonNull;
import android.federatedcompute.aidl.IExampleStoreIterator;
import android.federatedcompute.common.ClientConstants;
import android.federatedcompute.common.ExampleConsumption;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.os.Trace;

import com.android.federatedcompute.internal.util.LogUtil;
import com.android.federatedcompute.services.common.Constants;
import com.android.federatedcompute.services.common.FederatedComputeExecutors;
import com.android.federatedcompute.services.common.FileUtils;
import com.android.federatedcompute.services.examplestore.ExampleConsumptionRecorder;
import com.android.federatedcompute.services.training.aidl.IIsolatedTrainingService;
import com.android.federatedcompute.services.training.aidl.ITrainingResultCallback;
import com.android.federatedcompute.services.training.util.ListenableSupplier;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.intelligence.fcp.client.FLRunnerResult;
import com.google.intelligence.fcp.client.FLRunnerResult.ContributionResult;
import com.google.intelligence.fcp.client.RetryInfo;
import com.google.internal.federated.plan.ClientOnlyPlan;
import com.google.internal.federated.plan.ExampleSelector;
import com.google.protobuf.Duration;
import com.google.protobuf.InvalidProtocolBufferException;

import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** The implementation of {@link IsolatedTrainingService}. */
public class IsolatedTrainingServiceImpl extends IIsolatedTrainingService.Stub {
    private static final String TAG = IsolatedTrainingServiceImpl.class.getSimpleName();
    private final AtomicBoolean mInterruptFlag = new AtomicBoolean(false);

    @VisibleForTesting
    ListenableSupplier<Boolean> mInterruptState = new ListenableSupplier<>(mInterruptFlag::get);

    private final ComputationRunner mComputationRunner;

    public IsolatedTrainingServiceImpl() {
        this(new ComputationRunner());
    }

    @VisibleForTesting
    IsolatedTrainingServiceImpl(ComputationRunner computationRunner) {
        this.mComputationRunner = computationRunner;
    }

    @Override
    public void runFlTraining(@NonNull Bundle params, @NonNull ITrainingResultCallback callback) {
        Objects.requireNonNull(params);
        Objects.requireNonNull(callback);
        FederatedComputeExecutors.getBackgroundExecutor()
                .execute(() -> runTraining(params, callback));
    }

    private void runTraining(Bundle params, ITrainingResultCallback callback) {
        Trace.beginAsyncSection(TRACE_ISOLATED_PROCESS_RUN_FL_TRAINING, 0);
        try {
            IExampleStoreIterator exampleStoreIteratorBinder =
                    IExampleStoreIterator.Stub.asInterface(
                            Objects.requireNonNull(
                                    params.getBinder(
                                            Constants.EXTRA_EXAMPLE_STORE_ITERATOR_BINDER)));
            Objects.requireNonNull(exampleStoreIteratorBinder);

            byte[] exampleSelectorBytes =
                    Objects.requireNonNull(params.getByteArray(Constants.EXTRA_EXAMPLE_SELECTOR));
            ExampleSelector exampleSelector;
            try {
                exampleSelector = ExampleSelector.parseFrom(exampleSelectorBytes);
            } catch (InvalidProtocolBufferException e) {
                LogUtil.e(TAG, e, "ExampleSelector proto is invalid");
                sendResult(createFailedResult(), callback);
                return;
            }
            ExampleConsumptionRecorder recorder = new ExampleConsumptionRecorder();
            String populationName =
                    Objects.requireNonNull(params.getString(ClientConstants.EXTRA_POPULATION_NAME));
            String taskId = Objects.requireNonNull(params.getString(ClientConstants.EXTRA_TASK_ID));

            ParcelFileDescriptor inputCheckpointFd =
                    Objects.requireNonNull(
                            params.getParcelable(
                                    Constants.EXTRA_INPUT_CHECKPOINT_FD,
                                    ParcelFileDescriptor.class));
            ParcelFileDescriptor outputCheckpointFd =
                    Objects.requireNonNull(
                            params.getParcelable(
                                    Constants.EXTRA_OUTPUT_CHECKPOINT_FD,
                                    ParcelFileDescriptor.class));
            ParcelFileDescriptor clientPlanFd =
                    Objects.requireNonNull(
                            params.getParcelable(
                                    Constants.EXTRA_CLIENT_ONLY_PLAN_FD,
                                    ParcelFileDescriptor.class));

            byte[] clientPlanBytes = FileUtils.readFileDescriptorAsByteArray(clientPlanFd);
            ClientOnlyPlan clientPlan;
            try {
                clientPlan = ClientOnlyPlan.parseFrom(clientPlanBytes);
            } catch (InvalidProtocolBufferException e) {
                LogUtil.e(TAG, e, "ClientOnlyPlan proto is invalid");
                sendResult(createFailedResult(), callback);
                return;
            }

            ListenableFuture<FLRunnerResult> resultFuture =
                    Futures.submit(
                            () ->
                                    mComputationRunner.runTaskWithNativeRunner(
                                            taskId,
                                            populationName,
                                            getFileDescriptorForTensorflow(inputCheckpointFd),
                                            getFileDescriptorForTensorflow(outputCheckpointFd),
                                            clientPlan,
                                            exampleSelector,
                                            recorder,
                                            exampleStoreIteratorBinder,
                                            mInterruptState),
                            FederatedComputeExecutors.getBackgroundExecutor());

            Futures.addCallback(
                    resultFuture,
                    new FutureCallback<FLRunnerResult>() {
                        @Override
                        public void onSuccess(FLRunnerResult result) {
                            Bundle bundle = new Bundle();

                            ArrayList<ExampleConsumption> exampleConsumptionArrayList =
                                    recorder.finishRecordingAndGet();
                            int numExamples = 0;
                            for (ExampleConsumption exampleConsumption :
                                    exampleConsumptionArrayList) {
                                numExamples += exampleConsumption.getExampleCount();
                            }
                            if (result.getContributionResult() == ContributionResult.SUCCESS) {
                                LogUtil.i(
                                        TAG,
                                        "training task %s: result %s, used %d examples",
                                        populationName,
                                        result.getContributionResult(),
                                        numExamples);
                            } else {
                                LogUtil.i(
                                        TAG,
                                        "training task %s: result %s, error message %s",
                                        populationName,
                                        result.getContributionResult(),
                                        result.getErrorMessage());
                                result =
                                        result.toBuilder()
                                                .setRetryInfo(
                                                        RetryInfo.newBuilder()
                                                                .setMinimumDelay(
                                                                        Duration.newBuilder()
                                                                                // Set retry to 24
                                                                                // hours
                                                                                // in case TF failed
                                                                                // to
                                                                                // do computation
                                                                                .setSeconds(86400)))
                                                .build();
                            }
                            bundle.putByteArray(
                                    Constants.EXTRA_FL_RUNNER_RESULT, result.toByteArray());
                            bundle.putParcelableArrayList(
                                    ClientConstants.EXTRA_EXAMPLE_CONSUMPTION_LIST,
                                    exampleConsumptionArrayList);
                            sendResult(bundle, callback);
                            Trace.endAsyncSection(TRACE_ISOLATED_PROCESS_RUN_FL_TRAINING, 0);
                        }

                        @Override
                        public void onFailure(Throwable t) {
                            LogUtil.e(TAG, t, "Failed to runTaskWithNativeRunner");
                            sendResult(createFailedResult(), callback);
                        }
                    },
                    FederatedComputeExecutors.getLightweightExecutor());
        } catch (Exception e) {
            LogUtil.e(TAG, e, "Got exception when run FL training");
            sendResult(createFailedResult(), callback);
        }
    }

    private Bundle createFailedResult() {
        Bundle bundle = new Bundle();
        FLRunnerResult result =
                FLRunnerResult.newBuilder()
                        .setContributionResult(ContributionResult.FAIL)
                        .setRetryInfo(
                                RetryInfo.newBuilder()
                                        .setMinimumDelay(
                                                Duration.newBuilder()
                                                        // Set retry to 24 hours in case TF failed
                                                        // to do computation
                                                        .setSeconds(86400)))
                        .build();
        bundle.putByteArray(Constants.EXTRA_FL_RUNNER_RESULT, result.toByteArray());
        return bundle;
    }

    // We implement a customized tensorflow filesystem which support file descriptor for read and
    // write. The file format is "fd:///${fd_number}".
    private String getFileDescriptorForTensorflow(ParcelFileDescriptor parcelFileDescriptor) {
        return "fd:///" + parcelFileDescriptor.getFd();
    }

    private void sendResult(Bundle result, ITrainingResultCallback callback) {
        try {
            callback.onResult(result);
        } catch (RemoteException e) {
            LogUtil.w(TAG, e, ": Callback failed ");
        }
    }

    @Override
    public void cancelTraining(long runId) {
        mInterruptFlag.set(true);
        mInterruptState.runListeners();
    }
}
