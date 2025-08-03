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

package com.android.ondevicepersonalization.services.process;

import android.adservices.ondevicepersonalization.IsolatedService;
import android.adservices.ondevicepersonalization.aidl.IIsolatedService;
import android.adservices.ondevicepersonalization.aidl.IIsolatedServiceCallback;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.os.Bundle;
import android.os.RemoteException;


import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.libraries.plugin.FailureType;
import com.android.ondevicepersonalization.libraries.plugin.Plugin;
import com.android.ondevicepersonalization.libraries.plugin.PluginCallback;
import com.android.ondevicepersonalization.libraries.plugin.PluginContext;

/** Plugin that runs in an isolated process. */
public class OnDevicePersonalizationPlugin implements Plugin {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String TAG = "OnDevicePersonalizationPlugin";
    private Bundle mInput;
    private PluginCallback mPluginCallback;
    private PluginContext mPluginContext;
    private ClassLoader mClassLoader;

    @Override
    public void setClassLoader(ClassLoader classLoader) {
        mClassLoader = classLoader;
    }

    @Override
    public void onExecute(
            @NonNull Bundle input,
            @NonNull PluginCallback callback,
            @Nullable PluginContext pluginContext) {
        sLogger.d(TAG + ": Executing plugin: " + input.toString());
        mInput = input;
        mPluginCallback = callback;
        mPluginContext = pluginContext;

        try {
            String className = input.getString(PluginProcessRunner.PARAM_CLASS_NAME_KEY);
            if (className == null || className.isEmpty()) {
                sLogger.e(TAG + ": className missing.");
                sendErrorResult(FailureType.ERROR_EXECUTING_PLUGIN);
                return;
            }

            int operation = input.getInt(PluginProcessRunner.PARAM_OPERATION_KEY);
            if (operation == 0) {
                sLogger.e(TAG + ": operation missing or invalid.");
                sendErrorResult(FailureType.ERROR_EXECUTING_PLUGIN);
                return;
            }

            Bundle serviceParams = input.getParcelable(PluginProcessRunner.PARAM_SERVICE_INPUT,
                    Bundle.class);
            if (serviceParams == null) {
                sLogger.e(TAG + ": Missing service input.");
                sendErrorResult(FailureType.ERROR_EXECUTING_PLUGIN);
                return;
            }

            Class<?> clazz = Class.forName(className, true, mClassLoader);
            IsolatedService service =
                    (IsolatedService) clazz.getDeclaredConstructor().newInstance();
            // TODO(b/249345663): Set the 'Context' for the service.
            service.onCreate();
            IIsolatedService binder =
                    (IIsolatedService) service.onBind(null);

            binder.onRequest(operation, serviceParams,
                    new IIsolatedServiceCallback.Stub() {
                        @Override public void onSuccess(Bundle result) {
                            try {
                                mPluginCallback.onSuccess(result);
                            } catch (RemoteException e) {
                                sLogger.e(TAG + ": Callback error.", e);
                            }
                        }
                        @Override public void onError(
                                int errorCode, int isolatedServiceErrorCode) {
                            try {
                                mPluginCallback.onFailure(FailureType.ERROR_EXECUTING_PLUGIN);
                            } catch (RemoteException e) {
                                sLogger.e(TAG + ": Callback error.", e);
                            }
                        }
                    }
            );

        } catch (Exception e) {
            sLogger.e(TAG + ": Plugin failed. ", e);
            sendErrorResult(FailureType.ERROR_EXECUTING_PLUGIN);
        }
    }

    private void sendErrorResult(FailureType failure) {
        try {
            mPluginCallback.onFailure(failure);
        } catch (RemoteException e) {
            sLogger.e(TAG + ": Callback error.", e);
        }
    }
}
