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

package com.android.ondevicepersonalization.services.federatedcompute;

import android.adservices.ondevicepersonalization.aidl.IFederatedComputeCallback;
import android.adservices.ondevicepersonalization.aidl.IFederatedComputeService;
import android.annotation.NonNull;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.federatedcompute.FederatedComputeManager;
import android.federatedcompute.common.ClientConstants;
import android.federatedcompute.common.ScheduleFederatedComputeRequest;
import android.federatedcompute.common.TrainingOptions;
import android.os.Binder;
import android.os.OutcomeReceiver;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.provider.DeviceConfig;

import com.android.internal.annotations.VisibleForTesting;
import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;
import com.android.ondevicepersonalization.services.data.events.EventState;
import com.android.ondevicepersonalization.services.data.events.EventsDao;
import com.android.ondevicepersonalization.services.manifest.AppManifestConfigHelper;
import com.android.ondevicepersonalization.services.util.PackageUtils;

import com.google.common.util.concurrent.ListeningExecutorService;

import java.io.IOException;
import java.util.Objects;

/**
 * A class that exports methods that plugin code in the isolated process
 * can use to schedule federatedCompute jobs.
 */
public class FederatedComputeServiceImpl extends IFederatedComputeService.Stub {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String TAG = "FederatedComputeServiceImpl";

    private static final String OVERRIDE_FC_SERVER_URL_PACKAGE =
            "debug.ondevicepersonalization.override_fc_server_url_package";
    private static final String OVERRIDE_FC_SERVER_URL =
            "debug.ondevicepersonalization.override_fc_server_url";


    @NonNull
    private final Context mApplicationContext;
    @NonNull
    private ComponentName mCallingService;
    @NonNull
    private final Injector mInjector;

    @NonNull
    private final FederatedComputeManager mFederatedComputeManager;

    @VisibleForTesting
    public FederatedComputeServiceImpl(
            @NonNull ComponentName service,
            @NonNull Context applicationContext,
            @NonNull Injector injector) {
        this.mApplicationContext = Objects.requireNonNull(applicationContext);
        this.mCallingService = Objects.requireNonNull(service);
        this.mInjector = Objects.requireNonNull(injector);
        this.mFederatedComputeManager = Objects.requireNonNull(
                injector.getFederatedComputeManager(mApplicationContext));
    }

    public FederatedComputeServiceImpl(
            @NonNull ComponentName service,
            @NonNull Context applicationContext) {
        this(service, applicationContext, new Injector());
    }

    @Override
    public void schedule(TrainingOptions trainingOptions,
            IFederatedComputeCallback callback) {
        try {
            String url = AppManifestConfigHelper.getFcRemoteServerUrlFromOdpSettings(
                    mApplicationContext, mCallingService.getPackageName());

            // Check for override manifest url property, if package is debuggable
            if (PackageUtils.isPackageDebuggable(
                    mApplicationContext, mCallingService.getPackageName())) {
                if (SystemProperties.get(OVERRIDE_FC_SERVER_URL_PACKAGE, "").equals(
                        mCallingService.getPackageName())) {
                    String overrideManifestUrl = SystemProperties.get(OVERRIDE_FC_SERVER_URL, "");
                    if (!overrideManifestUrl.isEmpty()) {
                        sLogger.d(TAG + ": Overriding fc server URL for package "
                                + mCallingService.getPackageName() + " to " + overrideManifestUrl);
                        url = overrideManifestUrl;
                    }
                    final long originalCallingIdentity = Binder.clearCallingIdentity();
                    try {
                        String deviceConfigOverrideUrl = DeviceConfig.getString(
                                /* namespace= */ "on_device_personalization",
                                /* name= */ OVERRIDE_FC_SERVER_URL,
                                /* defaultValue= */ "");
                        if (!deviceConfigOverrideUrl.isEmpty()) {
                            sLogger.d(TAG + ": Overriding fc server URL for package "
                                    + mCallingService.getPackageName() + " to "
                                    + deviceConfigOverrideUrl);
                            url = deviceConfigOverrideUrl;
                        }
                    } finally {
                        Binder.restoreCallingIdentity(originalCallingIdentity);
                    }
                }
            }

            if (url == null) {
                sLogger.d(
                        "Missing remote server URL for package: "
                                + mCallingService.getPackageName());
                sendError(callback);
                return;
            }

            ContextData contextData =
                    new ContextData(
                            mCallingService.getPackageName(), mCallingService.getClassName());
            TrainingOptions trainingOptionsWithContext =
                    new TrainingOptions.Builder()
                            .setContextData(ContextData.toByteArray(contextData))
                            .setTrainingInterval(trainingOptions.getTrainingInterval())
                            .setPopulationName(trainingOptions.getPopulationName())
                            .setServerAddress(url)
                            .setOwnerComponentName(mCallingService)
                            .build();
            ScheduleFederatedComputeRequest request =
                    new ScheduleFederatedComputeRequest.Builder()
                            .setTrainingOptions(trainingOptionsWithContext)
                            .build();
            mFederatedComputeManager.schedule(
                    request,
                    mInjector.getExecutor(),
                    new OutcomeReceiver<>() {
                        @Override
                        public void onResult(Object result) {
                            mInjector.getEventsDao(mApplicationContext).updateOrInsertEventState(
                                    new EventState.Builder()
                                            .setService(mCallingService)
                                            .setTaskIdentifier(trainingOptions.getPopulationName())
                                            .setToken(new byte[]{})
                                            .build());
                            sendSuccess(callback);
                        }

                        @Override
                        public void onError(Exception e) {
                            sLogger.e(TAG + ": Error while scheduling federatedCompute", e);
                            sendError(callback);
                        }
                    });
        } catch (IOException | PackageManager.NameNotFoundException e) {
            sLogger.e(TAG + ": Error while scheduling federatedCompute", e);
            sendError(callback);
        }
    }

    @Override
    public void cancel(String populationName,
            IFederatedComputeCallback callback) {
        EventState eventState = mInjector.getEventsDao(mApplicationContext).getEventState(
                populationName, mCallingService);
        if (eventState == null) {
            sLogger.d("No population registered for package: " + mCallingService.getPackageName());
            sendSuccess(callback);
            return;
        }
        mFederatedComputeManager.cancel(
                mCallingService,
                populationName,
                mInjector.getExecutor(),
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(Object result) {
                        sendSuccess(callback);
                    }

                    @Override
                    public void onError(Exception e) {
                        sLogger.e(TAG + ": Error while cancelling federatedCompute", e);
                        sendError(callback);
                    }
                });
    }

    private void sendSuccess(
            @NonNull IFederatedComputeCallback callback) {
        try {
            callback.onSuccess();
        } catch (RemoteException e) {
            sLogger.e(TAG + ": Callback error", e);
        }
    }

    private void sendError(@NonNull IFederatedComputeCallback callback) {
        try {
            callback.onFailure(ClientConstants.STATUS_INTERNAL_ERROR);
        } catch (RemoteException e) {
            sLogger.e(TAG + ": Callback error", e);
        }
    }

    @VisibleForTesting
    static class Injector {
        ListeningExecutorService getExecutor() {
            return OnDevicePersonalizationExecutors.getBackgroundExecutor();
        }

        FederatedComputeManager getFederatedComputeManager(Context context) {
            return context.getSystemService(FederatedComputeManager.class);
        }

        EventsDao getEventsDao(
                Context context
        ) {
            return EventsDao.getInstance(context);
        }
    }
}
