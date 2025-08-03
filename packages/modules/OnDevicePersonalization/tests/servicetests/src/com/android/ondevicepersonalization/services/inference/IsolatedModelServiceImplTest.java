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

package com.android.ondevicepersonalization.services.inference;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import android.adservices.ondevicepersonalization.Constants;
import android.adservices.ondevicepersonalization.InferenceInput;
import android.adservices.ondevicepersonalization.InferenceInputParcel;
import android.adservices.ondevicepersonalization.InferenceOutput;
import android.adservices.ondevicepersonalization.InferenceOutputParcel;
import android.adservices.ondevicepersonalization.ModelId;
import android.adservices.ondevicepersonalization.RemoteDataImpl;
import android.adservices.ondevicepersonalization.aidl.IDataAccessService;
import android.adservices.ondevicepersonalization.aidl.IDataAccessServiceCallback;
import android.adservices.ondevicepersonalization.aidl.IIsolatedModelServiceCallback;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;

import androidx.test.core.app.ApplicationProvider;

import com.android.ondevicepersonalization.internal.util.LoggerFactory;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

public class IsolatedModelServiceImplTest {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String TAG = IsolatedModelServiceImplTest.class.getSimpleName();

    private static final String MODEL_KEY = "modelKey";
    private final Random mRandom = new Random();

    private Bundle mCallbackResult;
    private InferenceInput.Params mParams;
    private RemoteDataImpl mRemoteData;

    @Before
    public void setup() {
        mRemoteData =
                new RemoteDataImpl(
                        IDataAccessService.Stub.asInterface(new TestDataAccessService()));
        mParams = new InferenceInput.Params.Builder(mRemoteData, MODEL_KEY).build();
    }

    @Test
    public void runModelInference_singleExample_success() throws Exception {
        // Set up inference context.
        InferenceInput inferenceInput =
                new InferenceInput.Builder(
                                mParams, generateInferenceInput(1), generateInferenceOutput(1))
                        .build();

        Bundle bundle = new Bundle();
        bundle.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        bundle.putParcelable(
                Constants.EXTRA_INFERENCE_INPUT, new InferenceInputParcel(inferenceInput));

        IsolatedModelServiceImpl modelService = new IsolatedModelServiceImpl();
        var callback = new TestServiceCallback();
        modelService.runInference(bundle, callback);
        callback.mLatch.await();

        assertFalse(callback.mError);
        InferenceOutputParcel result =
                mCallbackResult.getParcelable(Constants.EXTRA_RESULT, InferenceOutputParcel.class);
        Map<Integer, Object> outputs = result.getData();
        float[] output1 = (float[]) outputs.get(0);
        assertThat(output1.length).isEqualTo(1);
    }

    @Test
    public void runModelInference_setBatchSizeNotMatch_success() throws Exception {
        // Set up inference context.
        int numExample = 50;
        InferenceInput inferenceInput =
                new InferenceInput.Builder(
                                mParams,
                                generateInferenceInput(numExample),
                                generateInferenceOutput(numExample))
                        .setBatchSize(numExample - 10)
                        .build();

        Bundle bundle = new Bundle();
        bundle.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        bundle.putParcelable(
                Constants.EXTRA_INFERENCE_INPUT, new InferenceInputParcel(inferenceInput));

        IsolatedModelServiceImpl modelService = new IsolatedModelServiceImpl();
        var callback = new TestServiceCallback();
        modelService.runInference(bundle, callback);
        callback.mLatch.await();

        assertFalse(callback.mError);
        InferenceOutputParcel result =
                mCallbackResult.getParcelable(Constants.EXTRA_RESULT, InferenceOutputParcel.class);
        Map<Integer, Object> outputs = result.getData();
        float[] output1 = (float[]) outputs.get(0);
        assertThat(output1.length).isEqualTo(numExample);
    }

    @Test
    public void runModelInferenceBatch_setBatchSize_success() throws Exception {
        int numExample = 50;
        InferenceInput inferenceInput =
                new InferenceInput.Builder(
                                mParams,
                                generateInferenceInput(numExample),
                                generateInferenceOutput(numExample))
                        .setBatchSize(numExample)
                        .build();

        Bundle bundle = new Bundle();
        bundle.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        bundle.putParcelable(
                Constants.EXTRA_INFERENCE_INPUT, new InferenceInputParcel(inferenceInput));

        IsolatedModelServiceImpl modelService = new IsolatedModelServiceImpl();
        var callback = new TestServiceCallback();
        modelService.runInference(bundle, callback);
        callback.mLatch.await();

        assertFalse(callback.mError);
        InferenceOutputParcel result =
                mCallbackResult.getParcelable(Constants.EXTRA_RESULT, InferenceOutputParcel.class);
        Map<Integer, Object> outputs = result.getData();
        float[] output1 = (float[]) outputs.get(0);
        assertThat(output1.length).isEqualTo(numExample);
    }

    @Test
    public void runModelInferenceBatch_notSetBatchSize_success() throws Exception {
        int numExample = 50;
        InferenceInput inferenceInput =
                new InferenceInput.Builder(
                                mParams,
                                generateInferenceInput(numExample),
                                generateInferenceOutput(numExample))
                        .build();

        Bundle bundle = new Bundle();
        bundle.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        bundle.putParcelable(
                Constants.EXTRA_INFERENCE_INPUT, new InferenceInputParcel(inferenceInput));

        IsolatedModelServiceImpl modelService = new IsolatedModelServiceImpl();
        var callback = new TestServiceCallback();
        modelService.runInference(bundle, callback);
        callback.mLatch.await();

        assertFalse(callback.mError);
        InferenceOutputParcel result =
                mCallbackResult.getParcelable(Constants.EXTRA_RESULT, InferenceOutputParcel.class);
        Map<Integer, Object> outputs = result.getData();
        float[] output1 = (float[]) outputs.get(0);
        assertThat(output1.length).isEqualTo(numExample);
    }

    @Test
    public void runModelInference_invalidInputFormat() throws Exception {
        // Misconfigured inputs.
        float[] input0 = {1.23f};
        float[] input1 = {2.43f};
        Object[] invalidInput = {input0, input1, input0};

        InferenceInput inferenceInput =
                new InferenceInput.Builder(mParams, invalidInput, generateInferenceOutput(1))
                        .build();

        Bundle bundle = new Bundle();
        bundle.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        bundle.putParcelable(
                Constants.EXTRA_INFERENCE_INPUT, new InferenceInputParcel(inferenceInput));

        IsolatedModelServiceImpl modelService = new IsolatedModelServiceImpl();
        var callback = new TestServiceCallback();
        modelService.runInference(bundle, callback);
        callback.mLatch.await();

        assertTrue(callback.mError);
        assertThat(callback.mErrorCode).isEqualTo(Constants.STATUS_INTERNAL_ERROR);
    }

    @Test
    public void missModelId_throws() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new InferenceInput.Builder(
                                        new InferenceInput.Params.Builder(mRemoteData, null)
                                                .build(),
                                        generateInferenceInput(1),
                                        generateInferenceOutput(1))
                                .build());
    }

    @Test
    public void missingInputData_buildThrows() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new InferenceInput.Builder(mParams, null, generateInferenceOutput(1))
                                .build());
    }

    @Test
    public void negativeThreadNum_buildThrows() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        new InferenceInput.Params.Builder(mRemoteData, MODEL_KEY)
                                .setRecommendedNumThreads(-1)
                                .build());
    }

    @Test
    public void runModelInference_missingModelOutput() throws Exception {
        InferenceInput inferenceInput =
                // Not set output structure in InferenceOutput.
                new InferenceInput.Builder(
                                mParams,
                                generateInferenceInput(1),
                                new InferenceOutput.Builder().build())
                        .build();

        Bundle bundle = new Bundle();
        bundle.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new TestDataAccessService());
        bundle.putParcelable(
                Constants.EXTRA_INFERENCE_INPUT, new InferenceInputParcel(inferenceInput));

        IsolatedModelServiceImpl modelService = new IsolatedModelServiceImpl();
        var callback = new TestServiceCallback();
        modelService.runInference(bundle, callback);

        callback.mLatch.await();
        assertTrue(callback.mError);
        assertThat(callback.mErrorCode).isEqualTo(Constants.STATUS_INTERNAL_ERROR);
    }

    @Test
    public void runModelInference_missingDataAccessServiceBinder() {
        // Set up inference context.
        InferenceInput inferenceInput =
                new InferenceInput.Builder(
                                mParams, generateInferenceInput(1), generateInferenceOutput(1))
                        .build();

        Bundle bundle = new Bundle();
        bundle.putParcelable(
                Constants.EXTRA_INFERENCE_INPUT, new InferenceInputParcel(inferenceInput));

        IsolatedModelServiceImpl modelService = new IsolatedModelServiceImpl();
        assertThrows(
                NullPointerException.class,
                () -> modelService.runInference(bundle, new TestServiceCallback()));
    }

    private Object[] generateInferenceInput(int numExample) {
        float[][] input0 = new float[numExample][100];
        for (int i = 0; i < numExample; i++) {
            input0[i][0] = mRandom.nextFloat();
        }
        return new Object[] {input0};
    }

    private InferenceOutput generateInferenceOutput(int numExample) {
        float[] output0 = new float[numExample];
        HashMap<Integer, Object> outputMap = new HashMap<>();
        outputMap.put(0, output0);
        return new InferenceOutput.Builder().setDataOutputs(outputMap).build();
    }

    class TestServiceCallback extends IIsolatedModelServiceCallback.Stub {
        public boolean mError = false;
        public int mErrorCode = 0;
        private final CountDownLatch mLatch = new CountDownLatch(1);

        @Override
        public void onSuccess(Bundle result) {
            mCallbackResult = result;
            mLatch.countDown();
        }

        @Override
        public void onError(int errorCode) {
            mError = true;
            mErrorCode = errorCode;
            mLatch.countDown();
        }
    }

    static class TestDataAccessService extends IDataAccessService.Stub {
        @Override
        public void onRequest(int operation, Bundle params, IDataAccessServiceCallback callback) {
            if (operation == Constants.DATA_ACCESS_OP_GET_MODEL) {
                try {
                    ModelId modelId = params.getParcelable(Constants.EXTRA_MODEL_ID, ModelId.class);
                    sLogger.d(
                            TAG
                                    + " TestDataAccessService onRequest model id %s"
                                    + modelId.getKey());
                    assertThat(modelId.getKey()).isEqualTo(MODEL_KEY);
                    assertThat(modelId.getTableId()).isEqualTo(ModelId.TABLE_ID_REMOTE_DATA);
                    Context context = ApplicationProvider.getApplicationContext();
                    Uri modelUri =
                            Uri.parse(
                                    "android.resource://com.android.ondevicepersonalization.servicetests/raw/model");
                    File modelFile = File.createTempFile("model", ".tflite");
                    InputStream in = context.getContentResolver().openInputStream(modelUri);
                    Files.copy(in, modelFile.toPath(), REPLACE_EXISTING);
                    in.close();
                    ParcelFileDescriptor inputModelFd =
                            ParcelFileDescriptor.open(
                                    modelFile, ParcelFileDescriptor.MODE_READ_ONLY);

                    Bundle bundle = new Bundle();
                    bundle.putParcelable(Constants.EXTRA_RESULT, inputModelFd);
                    callback.onSuccess(bundle);
                } catch (Exception e) {
                    // Ignore
                }
            }
        }
    }
}
