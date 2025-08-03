/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.test;

import android.adservices.ondevicepersonalization.DownloadCompletedInput;
import android.adservices.ondevicepersonalization.DownloadCompletedOutput;
import android.adservices.ondevicepersonalization.EventInput;
import android.adservices.ondevicepersonalization.EventLogRecord;
import android.adservices.ondevicepersonalization.EventOutput;
import android.adservices.ondevicepersonalization.ExecuteInput;
import android.adservices.ondevicepersonalization.ExecuteOutput;
import android.adservices.ondevicepersonalization.IsolatedServiceException;
import android.adservices.ondevicepersonalization.IsolatedWorker;
import android.adservices.ondevicepersonalization.KeyValueStore;
import android.adservices.ondevicepersonalization.RenderInput;
import android.adservices.ondevicepersonalization.RenderOutput;
import android.adservices.ondevicepersonalization.RenderingConfig;
import android.adservices.ondevicepersonalization.RequestLogRecord;
import android.adservices.ondevicepersonalization.TrainingExampleRecord;
import android.adservices.ondevicepersonalization.TrainingExamplesInput;
import android.adservices.ondevicepersonalization.TrainingExamplesOutput;
import android.adservices.ondevicepersonalization.WebTriggerInput;
import android.adservices.ondevicepersonalization.WebTriggerOutput;
import android.annotation.NonNull;
import android.content.ContentValues;
import android.os.OutcomeReceiver;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;

// TODO(b/249345663) Move this class and related manifest to separate APK for more realistic testing
public class TestPersonalizationHandler implements IsolatedWorker {
    public final String TAG = "TestPersonalizationHandler";
    private final KeyValueStore mRemoteData;

    TestPersonalizationHandler(KeyValueStore remoteData) {
        mRemoteData = remoteData;
    }

    @Override
    public void onDownloadCompleted(
            DownloadCompletedInput input,
            OutcomeReceiver<DownloadCompletedOutput, IsolatedServiceException> receiver) {
        try {
            Log.d(TAG, "Starting filterData.");
            Log.d(TAG, "Data keys: " + input.getDownloadedContents().keySet());

            Log.d(TAG, "Existing keyExtra: " + Arrays.toString(mRemoteData.get("keyExtra")));
            Log.d(TAG, "Existing keySet: " + mRemoteData.keySet());

            List<String> keysToRetain = getFilteredKeys(input.getDownloadedContents());
            keysToRetain.add("keyExtra");
            // Get the keys to keep from the downloaded data
            DownloadCompletedOutput result =
                    new DownloadCompletedOutput.Builder().setRetainedKeys(keysToRetain).build();
            receiver.onResult(result);
        } catch (Exception e) {
            Log.e(TAG, "Error occurred in onDownload", e);
        }
    }

    @Override
    public void onExecute(
            @NonNull ExecuteInput input,
            @NonNull OutcomeReceiver<ExecuteOutput, IsolatedServiceException> receiver) {
        Log.d(TAG, "onExecute() started.");
        ContentValues logData = new ContentValues();
        logData.put("id", "bid1");
        logData.put("pr", 5.0);
        ExecuteOutput result =
                new ExecuteOutput.Builder()
                        .setRequestLogRecord(new RequestLogRecord.Builder().addRow(logData).build())
                        .setRenderingConfig(new RenderingConfig.Builder().addKey("bid1").build())
                        .addEventLogRecord(
                                new EventLogRecord.Builder()
                                        .setData(logData)
                                        .setRequestLogRecord(
                                                new RequestLogRecord.Builder()
                                                        .addRow(logData)
                                                        .addRow(logData)
                                                        .setRequestId(1)
                                                        .build())
                                        .setType(1)
                                        .setRowIndex(1)
                                        .build())
                        .setOutputData(new byte[] {1, 2, 3})
                        .build();
        receiver.onResult(result);
    }

    @Override
    public void onRender(
            @NonNull RenderInput input,
            @NonNull OutcomeReceiver<RenderOutput, IsolatedServiceException> receiver) {
        Log.d(TAG, "onRender() started.");
        RenderOutput result =
                new RenderOutput.Builder()
                        .setContent(
                                "<p>RenderResult: "
                                        + String.join(",", input.getRenderingConfig().getKeys())
                                        + "<p>")
                        .build();
        receiver.onResult(result);
    }

    @Override
    public void onEvent(
            @NonNull EventInput input,
            @NonNull OutcomeReceiver<EventOutput, IsolatedServiceException> receiver) {
        Log.d(TAG, "onEvent() started.");
        long longValue = 0;
        if (input.getParameters() != null) {
            longValue = input.getParameters().getLong("x");
        }
        ContentValues logData = new ContentValues();
        logData.put("x", longValue);
        EventOutput result =
                new EventOutput.Builder()
                        .setEventLogRecord(
                                new EventLogRecord.Builder()
                                        .setType(1)
                                        .setRowIndex(0)
                                        .setData(logData)
                                        .build())
                        .build();
        Log.d(TAG, "onEvent() result: " + result.toString());
        receiver.onResult(result);
    }

    private List<String> getFilteredKeys(KeyValueStore data) {
        Set<String> filteredKeys = data.keySet();
        Log.d(TAG, "key3 size: " + Objects.requireNonNull(data.get("key3")).length);
        filteredKeys.remove("key3");
        return new ArrayList<>(filteredKeys);
    }

    @Override
    public void onTrainingExamples(
            @NonNull TrainingExamplesInput input,
            @NonNull OutcomeReceiver<TrainingExamplesOutput, IsolatedServiceException> receiver) {
        Log.d(TAG, "onTrainingExamples() started.");
        Log.d(TAG, "Population name: " + input.getPopulationName());
        Log.d(TAG, "Task name: " + input.getTaskName());

        List<TrainingExampleRecord> exampleRecordList = new ArrayList<>();
        TrainingExampleRecord record1 =
                new TrainingExampleRecord.Builder()
                        .setTrainingExample(new byte[] {10})
                        .setResumptionToken("token1".getBytes())
                        .build();
        TrainingExampleRecord record2 =
                new TrainingExampleRecord.Builder()
                        .setTrainingExample(new byte[] {20})
                        .setResumptionToken("token2".getBytes())
                        .build();
        exampleRecordList.add(record1);
        exampleRecordList.add(record2);

        TrainingExamplesOutput output =
                new TrainingExamplesOutput.Builder()
                        .setTrainingExampleRecords(exampleRecordList)
                        .build();
        receiver.onResult(output);
    }

    @Override
    public void onWebTrigger(
            @NonNull WebTriggerInput input,
            @NonNull OutcomeReceiver<WebTriggerOutput, IsolatedServiceException> receiver) {
        Log.d(TAG, "onWebTrigger() started.");
        ContentValues logData = new ContentValues();
        logData.put("id", "trig1");
        logData.put("val", 10.0);
        WebTriggerOutput output =
                new WebTriggerOutput.Builder()
                        .addEventLogRecord(
                                new EventLogRecord.Builder()
                                        .setData(logData)
                                        .setRequestLogRecord(
                                                new RequestLogRecord.Builder()
                                                        .addRow(logData)
                                                        .addRow(logData)
                                                        .setRequestId(1)
                                                        .build())
                                        .setType(10)
                                        .setRowIndex(1)
                                        .build())
                        .build();
        receiver.onResult(output);
    }
}
