/*
 * Copyright 2023 The Android Open Source Project
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

package com.android.federatedcompute.services;

import static com.android.federatedcompute.services.common.Constants.ISOLATED_TRAINING_SERVICE_NAME;

import static org.junit.Assert.assertNotNull;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.federatedcompute.internal.util.AbstractServiceBinder;
import com.android.federatedcompute.services.training.aidl.IIsolatedTrainingService;

import org.junit.Test;

public class IsolatedServiceBinderTest {

    private final Context mContext = ApplicationProvider.getApplicationContext();

    @Test
    public void testFcpServiceBindingByName() {
        AbstractServiceBinder<IIsolatedTrainingService> serviceBinder =
                AbstractServiceBinder.getServiceBinderByServiceName(
                        mContext,
                        ISOLATED_TRAINING_SERVICE_NAME,
                        mContext.getPackageName(),
                        IIsolatedTrainingService.Stub::asInterface);

        final IIsolatedTrainingService service = serviceBinder.getService(Runnable::run);
        assertNotNull(service);
    }

    /**
     * Test the isolated service binding implementation, specifically, we bind to services through
     * the bindIsolatedService() API but with the shared isolated process flag omitted.
     */
    @Test
    public void testIsolatedProcessBinding() {
        AbstractServiceBinder<IIsolatedTrainingService> serviceBinder =
                AbstractServiceBinder.getIsolatedServiceBinderByServiceName(
                        mContext,
                        ISOLATED_TRAINING_SERVICE_NAME,
                        mContext.getPackageName(),
                        "testSharedIsolatedProcessBinding",
                        0,
                        IIsolatedTrainingService.Stub::asInterface);

        final IIsolatedTrainingService service = serviceBinder.getService(Runnable::run);
        assertNotNull(service);
    }

    /**
     * Test the isolated service binding implementation, specifically, we bind to services through
     * the bindIsolatedService() API but with the shared isolated process flag included.
     */
    @Test
    public void testSharedIsolatedProcessBinding() {
        AbstractServiceBinder<IIsolatedTrainingService> serviceBinder =
                AbstractServiceBinder.getIsolatedServiceBinderByServiceName(
                        mContext,
                        ISOLATED_TRAINING_SERVICE_NAME,
                        mContext.getPackageName(),
                        "testSharedIsolatedProcessBinding",
                        Context.BIND_SHARED_ISOLATED_PROCESS,
                        IIsolatedTrainingService.Stub::asInterface);

        final IIsolatedTrainingService service = serviceBinder.getService(Runnable::run);
        assertNotNull(service);
    }
}
