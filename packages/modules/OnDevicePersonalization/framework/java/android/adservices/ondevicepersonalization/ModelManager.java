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

package android.adservices.ondevicepersonalization;

import android.adservices.ondevicepersonalization.aidl.IDataAccessService;
import android.adservices.ondevicepersonalization.aidl.IIsolatedModelService;
import android.adservices.ondevicepersonalization.aidl.IIsolatedModelServiceCallback;
import android.annotation.CallbackExecutor;
import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.annotation.WorkerThread;
import android.os.Bundle;
import android.os.OutcomeReceiver;
import android.os.RemoteException;

import com.android.adservices.ondevicepersonalization.flags.Flags;
import com.android.ondevicepersonalization.internal.util.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * Handles model inference and only support TFLite model inference now. See {@link
 * IsolatedService#getModelManager}.
 */
@FlaggedApi(Flags.FLAG_ON_DEVICE_PERSONALIZATION_APIS_ENABLED)
public class ModelManager {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String TAG = ModelManager.class.getSimpleName();
    @NonNull private final IDataAccessService mDataService;

    @NonNull private final IIsolatedModelService mModelService;

    /** @hide */
    public ModelManager(
            @NonNull IDataAccessService dataService, @NonNull IIsolatedModelService modelService) {
        mDataService = dataService;
        mModelService = modelService;
    }

    /**
     * Run a single model inference. Only supports TFLite model inference now.
     *
     * @param input contains all the information needed for a run of model inference.
     * @param executor the {@link Executor} on which to invoke the callback.
     * @param receiver this returns a {@link InferenceOutput} which contains model inference result
     *     or {@link Exception} on failure.
     */
    @WorkerThread
    public void run(
            @NonNull InferenceInput input,
            @NonNull @CallbackExecutor Executor executor,
            @NonNull OutcomeReceiver<InferenceOutput, Exception> receiver) {
        Objects.requireNonNull(input);
        Bundle bundle = new Bundle();
        bundle.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, mDataService.asBinder());
        bundle.putParcelable(Constants.EXTRA_INFERENCE_INPUT, new InferenceInputParcel(input));
        try {
            mModelService.runInference(
                    bundle,
                    new IIsolatedModelServiceCallback.Stub() {
                        @Override
                        public void onSuccess(Bundle result) {
                            executor.execute(
                                    () -> {
                                        try {
                                            InferenceOutputParcel outputParcel =
                                                    Objects.requireNonNull(
                                                            result.getParcelable(
                                                                    Constants.EXTRA_RESULT,
                                                                    InferenceOutputParcel.class));
                                            receiver.onResult(
                                                    new InferenceOutput(outputParcel.getData()));
                                        } catch (Exception e) {
                                            receiver.onError(e);
                                        }
                                    });
                        }

                        @Override
                        public void onError(int errorCode) {
                            receiver.onError(new IllegalStateException("Error: " + errorCode));
                        }
                    });
        } catch (RemoteException e) {
            receiver.onError(new IllegalStateException(e));
        }
    }
}
