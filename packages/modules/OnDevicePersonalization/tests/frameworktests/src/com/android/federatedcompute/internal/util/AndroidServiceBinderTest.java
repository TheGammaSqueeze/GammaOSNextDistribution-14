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

package com.android.federatedcompute.internal.util;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.adservices.ondevicepersonalization.aidl.IOnDevicePersonalizationManagingService;
import android.content.Context;
import android.federatedcompute.aidl.IFederatedComputeService;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.List;

@RunWith(JUnit4.class)
public class AndroidServiceBinderTest {
    public static final String ODP_MANAGING_SERVICE_INTENT_ACTION =
            "android.OnDevicePersonalizationService";
    public static final String ODP_MANAGING_SERVICE_PACKAGE =
            "com.android.ondevicepersonalization.services";
    public static final String ALT_ODP_MANAGING_SERVICE_PACKAGE =
            "com.google.android.ondevicepersonalization.services";
    public static final String INCORRECT_PACKAGE =
            "NOT.android.ondevicepersonalization.or.federatedcompute.services";
    private static final String FEDERATED_COMPUTATION_SERVICE_INTENT_ACTION =
            "android.federatedcompute.FederatedComputeService";
    private static final String FEDERATED_COMPUTATION_SERVICE_PACKAGE =
            "com.android.federatedcompute.services";
    private static final String GOOGLE_RENAMED_FEDERATED_COMPUTATION_SERVICE_PACKAGE =
            "com.google.android.federatedcompute";
    private final Context mSpyContext = spy(ApplicationProvider.getApplicationContext());

    @Test
    public void testOdpServiceBinding() {
        AbstractServiceBinder<IOnDevicePersonalizationManagingService> serviceBinder =
                AbstractServiceBinder.getServiceBinderByIntent(
                        mSpyContext,
                        ODP_MANAGING_SERVICE_INTENT_ACTION,
                        List.of(
                                ODP_MANAGING_SERVICE_PACKAGE,
                                ALT_ODP_MANAGING_SERVICE_PACKAGE),
                        IOnDevicePersonalizationManagingService.Stub::asInterface);

        final IOnDevicePersonalizationManagingService service =
                serviceBinder.getService(Runnable::run);
        assertNotNull(service);
    }

    @Test
    public void testServiceBindingWithFlags() {
        AbstractServiceBinder<IOnDevicePersonalizationManagingService> serviceBinder =
                AbstractServiceBinder.getServiceBinderByIntent(
                        mSpyContext,
                        ODP_MANAGING_SERVICE_INTENT_ACTION,
                        List.of(
                                ODP_MANAGING_SERVICE_PACKAGE,
                                ALT_ODP_MANAGING_SERVICE_PACKAGE),
                        Context.BIND_ALLOW_ACTIVITY_STARTS,
                        IOnDevicePersonalizationManagingService.Stub::asInterface);

        final IOnDevicePersonalizationManagingService service =
                serviceBinder.getService(Runnable::run);
        verify(mSpyContext)
                .bindService(
                        any(),
                        eq(Context.BIND_ALLOW_ACTIVITY_STARTS | Context.BIND_AUTO_CREATE),
                        any(),
                        any());
        assertNotNull(service);
    }

    @Test
    public void testFcpServiceBinding() {
        AbstractServiceBinder<IFederatedComputeService> serviceBinder =
                AbstractServiceBinder.getServiceBinderByIntent(
                        mSpyContext,
                        FEDERATED_COMPUTATION_SERVICE_INTENT_ACTION,
                        List.of(
                                FEDERATED_COMPUTATION_SERVICE_PACKAGE,
                                GOOGLE_RENAMED_FEDERATED_COMPUTATION_SERVICE_PACKAGE),
                        IFederatedComputeService.Stub::asInterface);

        final IFederatedComputeService service = serviceBinder.getService(Runnable::run);
        assertNotNull(service);
    }

    @Test
    public void testOdpServiceBindingWrongPackage() {
        AbstractServiceBinder<IOnDevicePersonalizationManagingService> serviceBinder =
                AbstractServiceBinder.getServiceBinderByIntent(
                        mSpyContext,
                        ODP_MANAGING_SERVICE_INTENT_ACTION,
                        INCORRECT_PACKAGE,
                        IOnDevicePersonalizationManagingService.Stub::asInterface);

        assertThrows(IllegalStateException.class, () -> serviceBinder.getService(Runnable::run));
    }

    @Test
    public void testFcpServiceBindingWrongPackage() {
        AbstractServiceBinder<IFederatedComputeService> serviceBinder =
                AbstractServiceBinder.getServiceBinderByIntent(
                        mSpyContext,
                        FEDERATED_COMPUTATION_SERVICE_INTENT_ACTION,
                        INCORRECT_PACKAGE,
                        IFederatedComputeService.Stub::asInterface);

        assertThrows(IllegalStateException.class, () -> serviceBinder.getService(Runnable::run));
    }
}
