/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.ondevicepersonalization.testing.sampleservice;

import android.adservices.ondevicepersonalization.EventUrlProvider;
import android.adservices.ondevicepersonalization.ExecuteInput;
import android.adservices.ondevicepersonalization.ExecuteOutput;
import android.adservices.ondevicepersonalization.InferenceInput;
import android.adservices.ondevicepersonalization.InferenceOutput;
import android.adservices.ondevicepersonalization.IsolatedServiceException;
import android.adservices.ondevicepersonalization.IsolatedWorker;
import android.adservices.ondevicepersonalization.ModelManager;
import android.adservices.ondevicepersonalization.MutableKeyValueStore;
import android.adservices.ondevicepersonalization.RenderInput;
import android.adservices.ondevicepersonalization.RenderOutput;
import android.adservices.ondevicepersonalization.RenderingConfig;
import android.adservices.ondevicepersonalization.RequestLogRecord;
import android.annotation.NonNull;
import android.content.ContentValues;
import android.net.Uri;
import android.os.OutcomeReceiver;
import android.os.PersistableBundle;
import android.util.Base64;
import android.util.Log;

import com.android.ondevicepersonalization.testing.sampleserviceapi.SampleServiceApi;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

class SampleWorker implements IsolatedWorker {
    private static final String TAG = "OdpTestingSampleService";

    private static final int ERROR_SAMPLE_SERVICE_FAILED = 1;

    private static final String TRANSPARENT_PNG_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAA"
                    + "AAXNSR0IArs4c6QAAAAtJREFUGFdjYAACAAAFAAGq1chRAAAAAElFTkSuQmCC";
    private static final byte[] TRANSPARENT_PNG_BYTES = Base64.decode(TRANSPARENT_PNG_BASE64, 0);

    private final MutableKeyValueStore mLocalData;
    private final EventUrlProvider mEventUrlProvider;
    private final ModelManager mModelManager;

    private final ExecutorService mExecutor = Executors.newCachedThreadPool();

    SampleWorker(
            MutableKeyValueStore localData,
            EventUrlProvider eventUrlProvider,
            ModelManager modelManager) {
        mLocalData = localData;
        mEventUrlProvider = eventUrlProvider;
        mModelManager = modelManager;
    }

    @Override
    public void onExecute(
            ExecuteInput input, OutcomeReceiver<ExecuteOutput, IsolatedServiceException> receiver) {
        PersistableBundle appParams = Objects.requireNonNull(input.getAppParams());
        if (appParams == null
                || appParams.equals(PersistableBundle.EMPTY)
                || appParams.getString(SampleServiceApi.KEY_OPCODE) == null) {
            receiver.onResult(new ExecuteOutput.Builder().build());
            return;
        }

        String op = Objects.requireNonNull(appParams.getString(SampleServiceApi.KEY_OPCODE));
        if (op.equals(SampleServiceApi.OPCODE_THROW_EXCEPTION)) {
            throw createException(appParams);
        }

        mExecutor.submit(() -> handleOnExecute(appParams, receiver));
    }

    private void handleOnExecute(
            PersistableBundle appParams,
            OutcomeReceiver<ExecuteOutput, IsolatedServiceException> receiver) {
        Log.i(TAG, "handleOnExecute()");
        ExecuteOutput result = null;
        int errorCode = ERROR_SAMPLE_SERVICE_FAILED;

        try {
            String op = Objects.requireNonNull(appParams.getString(SampleServiceApi.KEY_OPCODE));
            if (op.equals(SampleServiceApi.OPCODE_RENDER_AND_LOG)) {
                result = handleRenderAndLog(appParams);
            } else if (op.equals(SampleServiceApi.OPCODE_FAIL_WITH_ERROR_CODE)) {
                errorCode =
                        appParams.getInt(
                                SampleServiceApi.KEY_ERROR_CODE, ERROR_SAMPLE_SERVICE_FAILED);
            } else if (op.equals(SampleServiceApi.OPCODE_WRITE_LOCAL_DATA)) {
                result = handleWriteLocalData(appParams);
            } else if (op.equals(SampleServiceApi.OPCODE_READ_LOCAL_DATA)) {
                result = handleReadLocalData(appParams);
            } else if (op.equals(SampleServiceApi.OPCODE_CHECK_VALUE_LENGTH)) {
                result = handleCheckValueLength(appParams);
            } else if (op.equals(SampleServiceApi.OPCODE_RUN_MODEL_INFERENCE)) {
                result = handleRunModelInference(appParams);
            }

        } catch (Exception e) {
            Log.e(TAG, "Service error", e);
        }

        if (result != null) {
            receiver.onResult(result);
        } else {
            receiver.onError(new IsolatedServiceException(errorCode));
        }
    }

    private ExecuteOutput handleRenderAndLog(PersistableBundle appParams) {
        var builder = new ExecuteOutput.Builder();
        String renderingConfigIdList =
                appParams.getString(SampleServiceApi.KEY_RENDERING_CONFIG_IDS);
        if (renderingConfigIdList != null && !renderingConfigIdList.isBlank()) {
            List<String> renderingConfigIds = List.of(renderingConfigIdList.split(","));
            if (!renderingConfigIds.isEmpty()) {
                builder.setRenderingConfig(
                        new RenderingConfig.Builder().setKeys(renderingConfigIds).build());
            }
        }
        // TODO(b/273826477): Support multiple rows.
        PersistableBundle logDataBundle =
                appParams.getPersistableBundle(SampleServiceApi.KEY_LOG_DATA);
        if (logDataBundle != null && !logDataBundle.isEmpty()) {
            ContentValues logData = new ContentValues();
            for (String key : logDataBundle.keySet()) {
                putObject(logData, key, logDataBundle.get(key));
            }
            if (!logData.isEmpty()) {
                builder.setRequestLogRecord(new RequestLogRecord.Builder().addRow(logData).build());
            }
        }

        return builder.build();
    }

    private ExecuteOutput handleWriteLocalData(PersistableBundle appParams) {
        String key = Objects.requireNonNull(appParams.getString(SampleServiceApi.KEY_TABLE_KEY));
        String encodedValue = appParams.getString(SampleServiceApi.KEY_BASE64_VALUE);
        byte[] value = (encodedValue != null) ? Base64.decode(encodedValue, 0) : null;
        if (value != null) {
            int repeatCount = appParams.getInt(SampleServiceApi.KEY_TABLE_VALUE_REPEAT_COUNT, 1);
            byte[] writtenValue = expandByteArray(value, repeatCount);
            var unused = mLocalData.put(key, writtenValue);
        } else {
            var unused = mLocalData.remove(key);
        }
        return new ExecuteOutput.Builder().build();
    }

    private ExecuteOutput handleRunModelInference(PersistableBundle appParams)
            throws InterruptedException, ExecutionException {
        String tableKey =
                Objects.requireNonNull(appParams.getString(SampleServiceApi.KEY_TABLE_KEY));
        InferenceInput.Params params =
                new InferenceInput.Params.Builder(mLocalData, tableKey).build();
        InferenceInput input = buildInferenceInput(params);
        CompletableFuture<InferenceOutput> future = new CompletableFuture<>();
        OutcomeReceiver<InferenceOutput, Exception> callback =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(InferenceOutput result) {
                        Log.i(TAG, "run model inference success");
                        future.complete(result);
                    }

                    @Override
                    public void onError(@NonNull Exception error) {
                        Log.e(TAG, "Run model inference resulted in an error!", error);
                        future.complete(null);
                    }
                };

        Log.i(TAG, "call ModelManager.run()");
        mModelManager.run(input, mExecutor, callback);
        InferenceOutput result = future.get();
        if (result == null) {
            return null;
        }
        float[] outputData = (float[]) result.getDataOutputs().get(0);
        double expectedResult = appParams.getDouble(SampleServiceApi.KEY_INFERENCE_RESULT);
        if (Math.abs(expectedResult - outputData[0]) > 0.01) {
            return null;
        }
        return new ExecuteOutput.Builder().build();
    }

    private InferenceInput buildInferenceInput(InferenceInput.Params params) {
        float[][] inputData = new float[1][100];
        for (int j = 0; j < 100; j++) {
            inputData[0][j] = 1;
        }
        float[] output0 = new float[1];
        HashMap<Integer, Object> outputMap = new HashMap<>();
        outputMap.put(0, output0);
        InferenceInput input =
                new InferenceInput.Builder(
                                params,
                                new Object[] {inputData},
                                new InferenceOutput.Builder().setDataOutputs(outputMap).build())
                        .setBatchSize(1)
                        .build();
        return input;
    }

    private ExecuteOutput handleReadLocalData(PersistableBundle appParams) {
        String key = Objects.requireNonNull(appParams.getString(SampleServiceApi.KEY_TABLE_KEY));
        String encodedValue = appParams.getString(SampleServiceApi.KEY_BASE64_VALUE);
        byte[] value = (encodedValue != null) ? Base64.decode(encodedValue, 0) : null;
        boolean success = false;
        if (value != null) {
            int repeatCount = appParams.getInt(SampleServiceApi.KEY_TABLE_VALUE_REPEAT_COUNT, 1);
            byte[] expectedValue = expandByteArray(value, repeatCount);
            byte[] actualValue = mLocalData.get(key);
            success = Arrays.equals(expectedValue, actualValue);
        } else {
            success = mLocalData.get(key) == null;
        }

        if (success) {
            return new ExecuteOutput.Builder().build();
        } else {
            return null;
        }
    }

    private ExecuteOutput handleCheckValueLength(PersistableBundle appParams) {
        String encodedValue = appParams.getString(SampleServiceApi.KEY_BASE64_VALUE);
        byte[] value = (encodedValue != null) ? Base64.decode(encodedValue, 0) : null;
        int expectedLength = appParams.getInt(SampleServiceApi.KEY_VALUE_LENGTH);

        if (expectedLength == value.length) {
            return new ExecuteOutput.Builder().build();
        } else {
            return null;
        }
    }

    private byte[] expandByteArray(byte[] input, int count) {
        byte[] output = new byte[input.length * count];
        for (int i = 0; i < count; ++i) {
            System.arraycopy(input, 0, output, i * input.length, input.length);
        }
        return output;
    }

    private RuntimeException createException(PersistableBundle appParams) {
        try {
            String exceptionClass =
                    appParams.getString(
                            SampleServiceApi.KEY_EXCEPTION_CLASS, "IllegalStateException");
            var clazz = Class.forName(exceptionClass);
            return (RuntimeException) clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            Log.e(TAG, "Error creating exception", e);
            throw new IllegalStateException(e);
        }
    }

    private void putObject(ContentValues cv, String key, Object value) {
        if (value instanceof String) {
            cv.put(key, (String) value);
        } else if (value instanceof Double) {
            cv.put(key, (Double) value);
        }
    }

    @Override
    public void onRender(
            RenderInput input, OutcomeReceiver<RenderOutput, IsolatedServiceException> receiver) {
        Log.i(TAG, "onRender()");
        mExecutor.submit(() -> handleOnRender(input, receiver));
    }

    private void handleOnRender(
            RenderInput input, OutcomeReceiver<RenderOutput, IsolatedServiceException> receiver) {
        Log.i(TAG, "handleOnRender()");
        var keys = input.getRenderingConfig().getKeys();
        if (keys.size() <= 0) {
            receiver.onError(new IsolatedServiceException(ERROR_SAMPLE_SERVICE_FAILED));
            return;
        }
        PersistableBundle eventParams = new PersistableBundle();
        eventParams.putInt(SampleServiceApi.KEY_EVENT_TYPE, SampleServiceApi.EVENT_TYPE_VIEW);
        String viewUrl =
                mEventUrlProvider
                        .createEventTrackingUrlWithResponse(
                                eventParams, TRANSPARENT_PNG_BYTES, "image/png")
                        .toString();
        eventParams.putInt(SampleServiceApi.KEY_EVENT_TYPE, SampleServiceApi.EVENT_TYPE_CLICK);
        String clickUrl =
                mEventUrlProvider
                        .createEventTrackingUrlWithRedirect(
                                eventParams, Uri.parse(SampleServiceApi.DESTINATION_URL))
                        .toString();
        String html =
                "<body><img src=\""
                        + viewUrl
                        + "\">\n"
                        + "<a href=\""
                        + clickUrl
                        + "\">"
                        + SampleServiceApi.LINK_TEXT
                        + "</a></body>";
        Log.i(TAG, "HTML output: " + html);
        receiver.onResult(new RenderOutput.Builder().setContent(html).build());
    }
}
