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

import static com.android.ondevicepersonalization.services.process.SharedIsolatedProcessRunner.TRUSTED_PARTNER_APPS_SIP;

import android.adservices.ondevicepersonalization.aidl.IIsolatedModelService;
import android.content.Context;

import com.android.federatedcompute.internal.util.AbstractServiceBinder;
import com.android.modules.utils.build.SdkLevel;

/**
 * Provides {@link IsolatedModelService}.
 */
public class IsolatedModelServiceProvider {
    public static final String ISOLATED_MODEL_SERVICE_NAME =
            "com.android.ondevicepersonalization.services.inference.IsolatedModelService";
    private AbstractServiceBinder<IIsolatedModelService> mModelService;


    /**
     * Returns {@link IIsolatedModelService}.
     */
    public IIsolatedModelService getModelService(Context context) {
        // Inference service should always run in the trusted SIP.
        String instanceName = SdkLevel.isAtLeastU() ? TRUSTED_PARTNER_APPS_SIP : null;
        int bindFlag = SdkLevel.isAtLeastU()
                ? Context.BIND_SHARED_ISOLATED_PROCESS
                : Context.BIND_AUTO_CREATE;
        mModelService =
                AbstractServiceBinder.getIsolatedServiceBinderByServiceName(
                        context,
                        ISOLATED_MODEL_SERVICE_NAME,
                        context.getPackageName(),
                        instanceName, bindFlag, IIsolatedModelService.Stub::asInterface);
        return mModelService.getService(Runnable::run);
    }

    /**
     * Unbind {@link IsolatedModelService}.
     */
    public void unBindFromModelService() {
        mModelService.unbindFromService();
    }
}
