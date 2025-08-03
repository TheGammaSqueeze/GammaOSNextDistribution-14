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

package com.android.ondevicepersonalization.services.inference;

import android.adservices.ondevicepersonalization.Constants;
import android.adservices.ondevicepersonalization.InferenceInputParcel;
import android.adservices.ondevicepersonalization.InferenceOutput;
import android.adservices.ondevicepersonalization.InferenceOutputParcel;
import android.adservices.ondevicepersonalization.ModelId;
import android.adservices.ondevicepersonalization.aidl.IDataAccessService;
import android.adservices.ondevicepersonalization.aidl.IDataAccessServiceCallback;
import android.adservices.ondevicepersonalization.aidl.IIsolatedModelService;
import android.adservices.ondevicepersonalization.aidl.IIsolatedModelServiceCallback;
import android.annotation.NonNull;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.os.Trace;

import com.android.internal.annotations.VisibleForTesting;
import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;
import com.android.ondevicepersonalization.services.util.IoUtils;

import com.google.common.util.concurrent.ListeningExecutorService;

import org.tensorflow.lite.InterpreterApi;
import org.tensorflow.lite.Tensor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/** The implementation of {@link IsolatedModelService}. */
public class IsolatedModelServiceImpl extends IIsolatedModelService.Stub {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String TAG = IsolatedModelServiceImpl.class.getSimpleName();
    @NonNull private final Injector mInjector;

    static {
        System.loadLibrary("fcp_cpp_dep_jni");
    }

    @VisibleForTesting
    public IsolatedModelServiceImpl(@NonNull Injector injector) {
        this.mInjector = injector;
    }

    public IsolatedModelServiceImpl() {
        this(new Injector());
    }

    @Override
    public void runInference(Bundle params, IIsolatedModelServiceCallback callback) {
        InferenceInputParcel inputParcel =
                Objects.requireNonNull(
                        params.getParcelable(
                                Constants.EXTRA_INFERENCE_INPUT, InferenceInputParcel.class));
        IDataAccessService binder =
                IDataAccessService.Stub.asInterface(
                        Objects.requireNonNull(
                                params.getBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER)));
        InferenceOutputParcel outputParcel =
                Objects.requireNonNull(inputParcel.getExpectedOutputStructure());
        mInjector
                .getExecutor()
                .execute(() -> runTfliteInterpreter(inputParcel, outputParcel, binder, callback));
    }

    private void runTfliteInterpreter(
            InferenceInputParcel inputParcel,
            InferenceOutputParcel outputParcel,
            IDataAccessService binder,
            IIsolatedModelServiceCallback callback) {
        try {
            Trace.beginSection("IsolatedModelService#RunInference");
            Object[] inputs = convertToObjArray(inputParcel.getInputData().getList());
            if (inputs.length == 0) {
                sendError(callback);
            }

            ModelId modelId = inputParcel.getModelId();
            ParcelFileDescriptor modelFd = fetchModel(binder, modelId);
            ByteBuffer byteBuffer = IoUtils.getByteBufferFromFd(modelFd);
            if (byteBuffer == null) {
                closeFd(modelFd);
                sendError(callback);
            }
            InterpreterApi interpreter =
                    InterpreterApi.create(
                            byteBuffer,
                            new InterpreterApi.Options()
                                    .setNumThreads(inputParcel.getCpuNumThread()));
            Map<Integer, Object> outputs = outputParcel.getData();
            if (outputs.isEmpty() || inputs.length == 0) {
                closeFd(modelFd);
                sendError(callback);
            }

            // TODO(b/323469981): handle batch size better. Currently TFLite will throws error if
            // batchSize doesn't match input data size.
            int batchSize = inputParcel.getBatchSize();
            for (int i = 0; i < interpreter.getInputTensorCount(); i++) {
                Tensor tensor = interpreter.getInputTensor(i);
                int[] shape = tensor.shape();
                shape[0] = batchSize;
                interpreter.resizeInput(i, shape);
            }
            interpreter.runForMultipleInputsOutputs(inputs, outputs);
            interpreter.close();

            closeFd(modelFd);
            Bundle bundle = new Bundle();
            InferenceOutput result = new InferenceOutput.Builder().setDataOutputs(outputs).build();
            bundle.putParcelable(Constants.EXTRA_RESULT, new InferenceOutputParcel(result));
            sendResult(bundle, callback);
            Trace.endSection();
        } catch (Exception e) {
            // Catch all exceptions including TFLite errors.
            sLogger.e(e, TAG + ": Failed to run inference job.");
            sendError(callback);
        }
    }

    private Object[] convertToObjArray(List<byte[]> input) {
        Object[] output = new Object[input.size()];
        for (int i = 0; i < input.size(); i++) {
            ByteArrayInputStream bais = new ByteArrayInputStream(input.get(i));
            try {
                ObjectInputStream ois = new ObjectInputStream(bais);
                output[i] = ois.readObject();
            } catch (IOException | ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
        return output;
    }

    private void closeFd(ParcelFileDescriptor fd) {
        try {
            fd.close();
        } catch (IOException e) {
            sLogger.e(e, "Failed to close model file descriptor");
        }
    }

    private ParcelFileDescriptor fetchModel(IDataAccessService dataAccessService, ModelId modelId) {
        try {
            sLogger.d(TAG + ": Start fetch model %s %d", modelId.getKey(), modelId.getTableId());
            BlockingQueue<Bundle> asyncResult = new ArrayBlockingQueue<>(1);
            Bundle params = new Bundle();
            params.putParcelable(Constants.EXTRA_MODEL_ID, modelId);
            dataAccessService.onRequest(
                    Constants.DATA_ACCESS_OP_GET_MODEL,
                    params,
                    new IDataAccessServiceCallback.Stub() {
                        @Override
                        public void onSuccess(Bundle result) {
                            if (result != null) {
                                asyncResult.add(result);
                            } else {
                                asyncResult.add(Bundle.EMPTY);
                            }
                        }

                        @Override
                        public void onError(int errorCode) {
                            asyncResult.add(Bundle.EMPTY);
                        }
                    });
            Bundle result = asyncResult.take();
            ParcelFileDescriptor modelFd =
                    result.getParcelable(Constants.EXTRA_RESULT, ParcelFileDescriptor.class);
            Objects.requireNonNull(modelFd);
            return modelFd;
        } catch (InterruptedException | RemoteException e) {
            sLogger.e(TAG + ": Failed to fetch model from DataAccessService", e);
            throw new IllegalStateException(e);
        }
    }

    private void sendError(@NonNull IIsolatedModelServiceCallback callback) {
        try {
            callback.onError(Constants.STATUS_INTERNAL_ERROR);
        } catch (RemoteException e) {
            sLogger.e(TAG + ": Callback error", e);
        }
    }

    private void sendResult(
            @NonNull Bundle result, @NonNull IIsolatedModelServiceCallback callback) {
        try {
            callback.onSuccess(result);
        } catch (RemoteException e) {
            sLogger.e(e, TAG + ": Callback error");
        }
    }

    @VisibleForTesting
    static class Injector {
        ListeningExecutorService getExecutor() {
            return OnDevicePersonalizationExecutors.getBackgroundExecutor();
        }
    }
}
