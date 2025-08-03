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

package com.android.ondevicepersonalization.services.process;

import static com.android.ondevicepersonalization.services.PhFlags.KEY_IS_ART_IMAGE_LOADING_OPTIMIZATION_ENABLED;
import static com.android.ondevicepersonalization.services.PhFlags.KEY_TRUSTED_PARTNER_APPS_LIST;

import android.adservices.ondevicepersonalization.Constants;
import android.adservices.ondevicepersonalization.IsolatedServiceException;
import android.adservices.ondevicepersonalization.aidl.IIsolatedService;
import android.adservices.ondevicepersonalization.aidl.IIsolatedServiceCallback;
import android.annotation.NonNull;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Bundle;

import androidx.concurrent.futures.CallbackToFutureAdapter;

import com.android.federatedcompute.internal.util.AbstractServiceBinder;
import com.android.modules.utils.build.SdkLevel;
import com.android.ondevicepersonalization.services.FlagsFactory;
import com.android.ondevicepersonalization.services.OdpServiceException;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationApplication;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;
import com.android.ondevicepersonalization.services.util.AllowListUtils;
import com.android.ondevicepersonalization.services.util.Clock;
import com.android.ondevicepersonalization.services.util.MonotonicClock;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;

import java.util.Objects;

/** Utilities for running remote isolated services in a shared isolated process (SIP). Note that
 *  this runner is only selected when the shared_isolated_process_feature_enabled flag is enabled.
 */
public class SharedIsolatedProcessRunner implements ProcessRunner  {

    // SIP that hosts services from all trusted partners, as well as internal isolated services.
    public static final String TRUSTED_PARTNER_APPS_SIP = "trusted_partner_apps_sip";

    // SIP that hosts unknown remote services.
    public static final String UNKNOWN_APPS_SIP = "unknown_apps_sip";

    private final Context mApplicationContext;
    private final Injector mInjector;

    static class Injector {
        Clock getClock() {
            return MonotonicClock.getInstance();
        }

        ListeningExecutorService getExecutor() {
            return OnDevicePersonalizationExecutors.getBackgroundExecutor();
        }
    }
    SharedIsolatedProcessRunner(
            @NonNull Context applicationContext,
            @NonNull Injector injector) {
        mApplicationContext = Objects.requireNonNull(applicationContext);
        mInjector = Objects.requireNonNull(injector);
    }

    private static class LazyInstanceHolder {
        static final SharedIsolatedProcessRunner LAZY_INSTANCE =
                new SharedIsolatedProcessRunner(
                        OnDevicePersonalizationApplication.getAppContext(),
                        new Injector());
    }

    /** Returns the global ProcessRunner. */
    @NonNull
    public static SharedIsolatedProcessRunner getInstance() {
        return SharedIsolatedProcessRunner.LazyInstanceHolder.LAZY_INSTANCE;
    }

    /** Binds to a service and put it in one of ODP's shared isolated process. */
    @Override
    @NonNull public ListenableFuture<IsolatedServiceInfo> loadIsolatedService(
            @NonNull String taskName, @NonNull ComponentName componentName) {
        try {
            ListenableFuture<AbstractServiceBinder<IIsolatedService>> isolatedServiceFuture =
                    mInjector.getExecutor().submit(
                            () -> getIsolatedServiceBinder(componentName));

            return FluentFuture.from(isolatedServiceFuture)
                    .transformAsync(
                            (isolatedService) -> {
                                try {
                                    return Futures.immediateFuture(new IsolatedServiceInfo(
                                            mInjector.getClock().elapsedRealtime(), componentName,
                                            /* pluginController= */ null, isolatedService));
                                } catch (Exception e) {
                                    return Futures.immediateFailedFuture(e);
                                }
                            }, mInjector.getExecutor())
                    .catchingAsync(
                            Exception.class,
                            Futures::immediateFailedFuture,
                            mInjector.getExecutor());
        } catch (Exception e) {
            return Futures.immediateFailedFuture(e);
        }
    }

    /** Runs the remote isolated service in the shared isolated process. */
    @NonNull
    @Override
    public ListenableFuture<Bundle> runIsolatedService(
            @NonNull IsolatedServiceInfo isolatedProcessInfo, int operationCode,
            @NonNull Bundle serviceParams) {
        return CallbackToFutureAdapter.getFuture(
                completer -> {
                    isolatedProcessInfo.getIsolatedServiceBinder()
                            .getService(Runnable::run)
                            .onRequest(
                                    operationCode, serviceParams,
                                    new IIsolatedServiceCallback.Stub() {
                                        @Override public void onSuccess(Bundle result) {
                                            completer.set(result);
                                        }

                                        // TO-DO (323882182): Granular isolated servce failures.
                                        @Override public void onError(
                                                int errorCode, int isolatedServiceErrorCode) {
                                            if (isolatedServiceErrorCode > 0
                                                        && isolatedServiceErrorCode < 128) {
                                                completer.setException(
                                                        new OdpServiceException(
                                                                Constants.STATUS_SERVICE_FAILED,
                                                                new IsolatedServiceException(
                                                                    isolatedServiceErrorCode)));
                                            } else {
                                                completer.setException(
                                                        new OdpServiceException(
                                                                Constants.STATUS_SERVICE_FAILED));
                                            }
                                        }
                                    });
                    return null;
                });
    }

    /** Unbinds from the remote isolated service. */
    @NonNull
    @Override
    public ListenableFuture<Void> unloadIsolatedService(
            @NonNull IsolatedServiceInfo isolatedServiceInfo) {
        try {
            return (ListenableFuture<Void>) mInjector.getExecutor().submit(
                    () -> isolatedServiceInfo.getIsolatedServiceBinder().unbindFromService());
        } catch (Exception e) {
            return Futures.immediateFailedFuture(e);
        }
    }

    private AbstractServiceBinder<IIsolatedService> getIsolatedServiceBinder(
            @NonNull ComponentName service) throws Exception {
        boolean isSipRequested = isSharedIsolatedProcessRequested(service);

        // null instance name results in regular isolated service being created.
        String instanceName = isSipRequested ? getSipInstanceName(service.getPackageName()) : null;
        int bindFlag = isSipRequested
                ? Context.BIND_SHARED_ISOLATED_PROCESS
                : Context.BIND_AUTO_CREATE;

        return AbstractServiceBinder.getIsolatedServiceBinderByServiceName(
                mApplicationContext,
                service.getClassName(), service.getPackageName(),
                instanceName, bindFlag, IIsolatedService.Stub::asInterface);
    }

    String getSipInstanceName(String packageName) {
        String partnerAppsList =
                (String) FlagsFactory.getFlags().getStableFlag(KEY_TRUSTED_PARTNER_APPS_LIST);
        boolean isPartnerApp = AllowListUtils.isAllowListed(packageName, partnerAppsList);
        String sipInstanceName = isPartnerApp ? TRUSTED_PARTNER_APPS_SIP : UNKNOWN_APPS_SIP;
        return (boolean) FlagsFactory.getFlags()
                .getStableFlag(KEY_IS_ART_IMAGE_LOADING_OPTIMIZATION_ENABLED)
                    ? sipInstanceName + "_disable_art_image_" : sipInstanceName;
    }

    boolean isSharedIsolatedProcessRequested(ComponentName service) throws Exception {
        if (!SdkLevel.isAtLeastU()) {
            return false;
        }
        PackageManager pm = mApplicationContext.getPackageManager();
        ServiceInfo si = pm.getServiceInfo(service, PackageManager.GET_META_DATA);
        return (si.flags & si.FLAG_ALLOW_SHARED_ISOLATED_PROCESS) != 0;
    }
}
