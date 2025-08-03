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

import static android.federatedcompute.common.ClientConstants.RESULT_HANDLING_SERVICE_ACTION;
import static android.federatedcompute.common.ClientConstants.STATUS_SUCCESS;
import static android.federatedcompute.common.ClientConstants.STATUS_TRAINING_FAILED;

import android.content.Context;
import android.federatedcompute.aidl.IFederatedComputeCallback;
import android.federatedcompute.aidl.IResultHandlingService;
import android.federatedcompute.common.ClientConstants;
import android.os.Bundle;

import com.android.federatedcompute.internal.util.AbstractServiceBinder;
import com.android.federatedcompute.internal.util.LogUtil;
import com.android.federatedcompute.services.data.FederatedTrainingTask;
import com.android.federatedcompute.services.training.util.ComputationResult;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * A helper class for binding to client implemented ResultHandlingService and trigger handleResult.
 */
public class ResultCallbackHelper {
    private static final String TAG = ResultCallbackHelper.class.getSimpleName();
    private static final long RESULT_HANDLING_SERVICE_CALLBACK_TIMEOUT_SECS = 10;

    /** The outcome of the result handling. */
    public enum CallbackResult {
        // Result handling succeeded, and the task completed.
        SUCCESS,
        // Result handling failed.
        FAIL,
        // Result handling succeeded, but the task needs to resume.
        NEEDS_RESUME,
    }

    private final Context mContext;
    private AbstractServiceBinder<IResultHandlingService> mResultHandlingServiceBinder;

    public ResultCallbackHelper(Context context) {
        this.mContext = context.getApplicationContext();
    }

    /**
     * Publishes the training result and example list to client implemented ResultHandlingService.
     */
    public ListenableFuture<CallbackResult> callHandleResult(
            String taskId, FederatedTrainingTask task, ComputationResult result) {
        Bundle input = new Bundle();
        input.putString(ClientConstants.EXTRA_POPULATION_NAME, task.populationName());
        input.putString(ClientConstants.EXTRA_TASK_ID, taskId);
        input.putByteArray(ClientConstants.EXTRA_CONTEXT_DATA, task.contextData());
        input.putInt(
                ClientConstants.EXTRA_COMPUTATION_RESULT,
                result.isResultSuccess() ? STATUS_SUCCESS : STATUS_TRAINING_FAILED);
        input.putParcelableArrayList(
                ClientConstants.EXTRA_EXAMPLE_CONSUMPTION_LIST, result.getExampleConsumptionList());

        try {
            IResultHandlingService resultHandlingService =
                    getResultHandlingService(task.appPackageName());
            if (resultHandlingService == null) {
                LogUtil.e(
                        TAG,
                        "ResultHandlingService binding died. population name: "
                                + task.populationName());
                return Futures.immediateFuture(CallbackResult.FAIL);
            }

            BlockingQueue<Integer> asyncResult = new ArrayBlockingQueue<>(1);
            resultHandlingService.handleResult(
                    input,
                    new IFederatedComputeCallback.Stub() {
                        @Override
                        public void onSuccess() {
                            asyncResult.add(STATUS_SUCCESS);
                        }

                        @Override
                        public void onFailure(int errorCode) {
                            asyncResult.add(errorCode);
                        }
                    });
            int statusCode =
                    asyncResult.poll(
                            RESULT_HANDLING_SERVICE_CALLBACK_TIMEOUT_SECS, TimeUnit.SECONDS);
            CallbackResult callbackResult =
                    statusCode == STATUS_SUCCESS ? CallbackResult.SUCCESS : CallbackResult.FAIL;
            return Futures.immediateFuture(callbackResult);
        } catch (Exception e) {
            LogUtil.e(
                    TAG,
                    e,
                    "ResultHandlingService binding died. population name: %s",
                    task.populationName());
            // We publish result to client app with best effort and should not crash flow.
            return Futures.immediateFuture(CallbackResult.FAIL);
        } finally {
            unbindFromResultHandlingService();
        }
    }

    @VisibleForTesting
    IResultHandlingService getResultHandlingService(String appPackageName) {
        mResultHandlingServiceBinder =
                AbstractServiceBinder.getServiceBinderByIntent(
                        this.mContext,
                        RESULT_HANDLING_SERVICE_ACTION,
                        appPackageName,
                        IResultHandlingService.Stub::asInterface);
        return mResultHandlingServiceBinder.getService(Runnable::run);
    }

    @VisibleForTesting
    void unbindFromResultHandlingService() {
        mResultHandlingServiceBinder.unbindFromService();
    }
}
