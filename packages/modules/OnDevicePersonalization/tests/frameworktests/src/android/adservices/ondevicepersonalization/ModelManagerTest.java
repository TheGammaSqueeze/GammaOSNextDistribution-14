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

package android.adservices.ondevicepersonalization;

import static junit.framework.Assert.assertEquals;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.adservices.ondevicepersonalization.aidl.IDataAccessService;
import android.adservices.ondevicepersonalization.aidl.IDataAccessServiceCallback;
import android.adservices.ondevicepersonalization.aidl.IIsolatedModelService;
import android.adservices.ondevicepersonalization.aidl.IIsolatedModelServiceCallback;
import android.os.Bundle;
import android.os.OutcomeReceiver;
import android.os.RemoteException;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;

import com.google.common.util.concurrent.MoreExecutors;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.concurrent.CountDownLatch;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class ModelManagerTest {
    ModelManager mModelManager =
            new ModelManager(
                    IDataAccessService.Stub.asInterface(new TestDataAccessService()),
                    IIsolatedModelService.Stub.asInterface(new TestIsolatedModelService()));

    private static final String INVALID_MODEL_KEY = "invalid_key";
    private static final String MODEL_KEY = "model_key";
    private static final String MISSING_OUTPUT_KEY = "missing-output-key";
    private boolean mRunInferenceCalled = false;
    private RemoteDataImpl mRemoteData;

    @Before
    public void setup() {
        mRemoteData =
                new RemoteDataImpl(
                        IDataAccessService.Stub.asInterface(new TestDataAccessService()));
    }

    @Test
    public void runInference_success() throws Exception {
        HashMap<Integer, Object> outputData = new HashMap<>();
        outputData.put(0, new float[1]);
        Object[] input = new Object[1];
        input[0] = new float[] {1.2f};
        InferenceInput inferenceContext =
                new InferenceInput.Builder(
                                new InferenceInput.Params.Builder(mRemoteData, MODEL_KEY).build(),
                                input,
                                new InferenceOutput.Builder().setDataOutputs(outputData).build())
                        .build();

        var callback = new MyTestCallback();
        mModelManager.run(inferenceContext, MoreExecutors.directExecutor(), callback);

        callback.mLatch.await();
        assertTrue(mRunInferenceCalled);
        assertNotNull(callback.mInferenceOutput);
        float[] value = (float[]) callback.mInferenceOutput.getDataOutputs().get(0);
        assertEquals(value[0], 5.0f, 0.01f);
    }

    @Test
    public void runInference_error() throws Exception {
        HashMap<Integer, Object> outputData = new HashMap<>();
        outputData.put(0, new float[1]);
        Object[] input = new Object[1];
        input[0] = new float[] {1.2f};
        InferenceInput inferenceContext =
                new InferenceInput.Builder(
                                new InferenceInput.Params.Builder(mRemoteData, INVALID_MODEL_KEY)
                                        .build(),
                                input,
                                new InferenceOutput.Builder().setDataOutputs(outputData).build())
                        .build();

        var callback = new MyTestCallback();
        mModelManager.run(inferenceContext, MoreExecutors.directExecutor(), callback);

        callback.mLatch.await();
        assertTrue(callback.mError);
    }

    @Test
    public void runInference_contextNull_throw() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mModelManager.run(
                                null, MoreExecutors.directExecutor(), new MyTestCallback()));
    }

    @Test
    public void runInference_resultMissingInferenceOutput() throws Exception {
        HashMap<Integer, Object> outputData = new HashMap<>();
        outputData.put(0, new float[1]);
        Object[] inputData = new Object[1];
        inputData[0] = new float[] {1.2f};
        InferenceInput inferenceContext =
                new InferenceInput.Builder(
                                new InferenceInput.Params.Builder(mRemoteData, MISSING_OUTPUT_KEY)
                                        .build(),
                                inputData,
                                new InferenceOutput.Builder().setDataOutputs(outputData).build())
                        .build();

        var callback = new MyTestCallback();
        mModelManager.run(inferenceContext, MoreExecutors.directExecutor(), callback);

        callback.mLatch.await();
        assertTrue(callback.mError);
    }

    public class MyTestCallback implements OutcomeReceiver<InferenceOutput, Exception> {
        public boolean mError = false;
        public InferenceOutput mInferenceOutput = null;
        private final CountDownLatch mLatch = new CountDownLatch(1);

        @Override
        public void onResult(InferenceOutput result) {
            mInferenceOutput = result;
            mLatch.countDown();
        }

        @Override
        public void onError(Exception error) {
            mError = true;
            mLatch.countDown();
        }
    }

    class TestIsolatedModelService extends IIsolatedModelService.Stub {
        @Override
        public void runInference(Bundle params, IIsolatedModelServiceCallback callback)
                throws RemoteException {
            mRunInferenceCalled = true;
            InferenceInputParcel inputParcel =
                    params.getParcelable(
                            Constants.EXTRA_INFERENCE_INPUT, InferenceInputParcel.class);
            if (inputParcel.getModelId().getKey().equals(INVALID_MODEL_KEY)) {
                callback.onError(Constants.STATUS_INTERNAL_ERROR);
                return;
            }
            if (inputParcel.getModelId().getKey().equals(MISSING_OUTPUT_KEY)) {
                callback.onSuccess(new Bundle());
                return;
            }
            HashMap<Integer, Object> result = new HashMap<>();
            result.put(0, new float[] {5.0f});
            Bundle bundle = new Bundle();
            bundle.putParcelable(
                    Constants.EXTRA_RESULT,
                    new InferenceOutputParcel(
                            new InferenceOutput.Builder().setDataOutputs(result).build()));
            callback.onSuccess(bundle);
        }
    }

    static class TestDataAccessService extends IDataAccessService.Stub {
        @Override
        public void onRequest(int operation, Bundle params, IDataAccessServiceCallback callback) {}
    }
}
