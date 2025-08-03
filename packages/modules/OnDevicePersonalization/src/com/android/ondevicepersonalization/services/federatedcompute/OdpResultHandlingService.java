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

package com.android.ondevicepersonalization.services.federatedcompute;

import static android.federatedcompute.common.ClientConstants.STATUS_SUCCESS;

import android.content.ComponentName;
import android.federatedcompute.ResultHandlingService;
import android.federatedcompute.common.ClientConstants;
import android.federatedcompute.common.ExampleConsumption;
import android.os.Bundle;

import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;
import com.android.ondevicepersonalization.services.data.events.EventState;
import com.android.ondevicepersonalization.services.data.events.EventsDao;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/** Implementation of ResultHandlingService for OnDevicePersonalization */
public class OdpResultHandlingService extends ResultHandlingService {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String TAG = "OdpResultHandlingService";

    @Override
    public void handleResult(Bundle params, Consumer<Integer> callback) {
        try {
            ContextData contextData =
                    ContextData.fromByteArray(
                            Objects.requireNonNull(
                                    params.getByteArray(ClientConstants.EXTRA_CONTEXT_DATA)));
            ComponentName service =
                    ComponentName.createRelative(
                            contextData.getPackageName(), contextData.getClassName());
            String populationName =
                    Objects.requireNonNull(params.getString(ClientConstants.EXTRA_POPULATION_NAME));
            String taskId = Objects.requireNonNull(params.getString(ClientConstants.EXTRA_TASK_ID));
            int computationResult = params.getInt(ClientConstants.EXTRA_COMPUTATION_RESULT);
            ArrayList<ExampleConsumption> consumptionList =
                    Objects.requireNonNull(
                            params.getParcelableArrayList(
                                    ClientConstants.EXTRA_EXAMPLE_CONSUMPTION_LIST,
                                    ExampleConsumption.class));

            // Just return if training failed. Next query will retry the failed examples.
            if (computationResult != STATUS_SUCCESS) {
                callback.accept(ClientConstants.STATUS_SUCCESS);
                return;
            }

            ListenableFuture<Boolean> result =
                    Futures.submit(
                            () ->
                                    processExampleConsumptions(
                                            consumptionList, populationName, taskId, service),
                            OnDevicePersonalizationExecutors.getBackgroundExecutor());
            Futures.addCallback(
                    result,
                    new FutureCallback<Boolean>() {
                        @Override
                        public void onSuccess(Boolean result) {
                            if (result) {
                                callback.accept(STATUS_SUCCESS);
                            } else {
                                callback.accept(ClientConstants.STATUS_INTERNAL_ERROR);
                            }
                        }

                        @Override
                        public void onFailure(Throwable t) {
                            sLogger.w(TAG + ": handleResult failed.", t);
                            callback.accept(ClientConstants.STATUS_INTERNAL_ERROR);
                        }
                    },
                    OnDevicePersonalizationExecutors.getBackgroundExecutor());

        } catch (Exception e) {
            sLogger.w(TAG + ": handleResult failed.", e);
            callback.accept(ClientConstants.STATUS_INTERNAL_ERROR);
        }
    }

    private Boolean processExampleConsumptions(
            List<ExampleConsumption> exampleConsumptions,
            String populationName,
            String taskId,
            ComponentName service) {
        List<EventState> eventStates = new ArrayList<>();
        for (ExampleConsumption consumption : exampleConsumptions) {
            String taskIdentifier =
                    OdpExampleStoreService.getTaskIdentifier(populationName, taskId);
            byte[] resumptionToken = consumption.getResumptionToken();
            if (resumptionToken != null) {
                eventStates.add(
                        new EventState.Builder()
                                .setService(service)
                                .setTaskIdentifier(taskIdentifier)
                                .setToken(resumptionToken)
                                .build());
            }
        }
        EventsDao eventsDao = EventsDao.getInstance(this);
        return eventsDao.updateOrInsertEventStatesTransaction(eventStates);
    }
}
