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

package com.android.ondevicepersonalization.services.webtrigger;

import android.adservices.ondevicepersonalization.Constants;
import android.adservices.ondevicepersonalization.MeasurementWebTriggerEventParamsParcel;
import android.adservices.ondevicepersonalization.OnDevicePersonalizationPermissions;
import android.adservices.ondevicepersonalization.WebTriggerInputParcel;
import android.adservices.ondevicepersonalization.WebTriggerOutputParcel;
import android.adservices.ondevicepersonalization.aidl.IIsolatedModelService;
import android.adservices.ondevicepersonalization.aidl.IRegisterMeasurementEventCallback;
import android.annotation.NonNull;
import android.content.ComponentName;
import android.content.Context;
import android.os.Binder;
import android.os.Bundle;
import android.os.RemoteException;

import com.android.internal.annotations.VisibleForTesting;
import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.services.Flags;
import com.android.ondevicepersonalization.services.FlagsFactory;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;
import com.android.ondevicepersonalization.services.data.DataAccessServiceImpl;
import com.android.ondevicepersonalization.services.inference.IsolatedModelServiceProvider;
import com.android.ondevicepersonalization.services.manifest.AppManifestConfig;
import com.android.ondevicepersonalization.services.manifest.AppManifestConfigHelper;
import com.android.ondevicepersonalization.services.policyengine.UserDataAccessor;
import com.android.ondevicepersonalization.services.serviceflow.ServiceFlow;
import com.android.ondevicepersonalization.services.util.Clock;
import com.android.ondevicepersonalization.services.util.LogUtils;
import com.android.ondevicepersonalization.services.util.MonotonicClock;
import com.android.ondevicepersonalization.services.util.PackageUtils;
import com.android.ondevicepersonalization.services.util.StatsUtils;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Handles a Web Trigger Registration.
 */
public class WebTriggerFlow implements ServiceFlow<WebTriggerOutputParcel> {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String TAG = "WebTriggerFlow";
    private final IRegisterMeasurementEventCallback mCallback;
    private final long mStartTimeMillis;
    private long mStartServiceTimeMillis;

    @VisibleForTesting
    static class Injector {
        ListeningExecutorService getExecutor() {
            return OnDevicePersonalizationExecutors.getBackgroundExecutor();
        }

        Clock getClock() {
            return MonotonicClock.getInstance();
        }

        Flags getFlags() {
            return FlagsFactory.getFlags();
        }

        ListeningScheduledExecutorService getScheduledExecutor() {
            return OnDevicePersonalizationExecutors.getScheduledExecutor();
        }

    }

    @NonNull private final Bundle mParams;
    @NonNull private final Context mContext;
    @NonNull private final Injector mInjector;
    @NonNull private IsolatedModelServiceProvider mModelServiceProvider;
    private MeasurementWebTriggerEventParamsParcel mServiceParcel;

    public WebTriggerFlow(
            @NonNull Bundle params,
            @NonNull Context context,
            @NonNull IRegisterMeasurementEventCallback callback,
            long startTimeMillis) {
        this(params, context, callback, startTimeMillis, new Injector());
    }

    @VisibleForTesting
    WebTriggerFlow(
            @NonNull Bundle params,
            @NonNull Context context,
            @NonNull IRegisterMeasurementEventCallback callback,
            long startTimeMillis,
            @NonNull Injector injector) {
        mParams = params;
        mContext = Objects.requireNonNull(context);
        mCallback = callback;
        mStartTimeMillis = startTimeMillis;
        mInjector = Objects.requireNonNull(injector);
    }

    // TO-DO: Add web trigger error codes.
    @Override
    public boolean isServiceFlowReady() {
        mStartServiceTimeMillis = mInjector.getClock().elapsedRealtime();

        try {
            if (getGlobalKillSwitch()) {
                sendErrorResult(Constants.STATUS_INTERNAL_ERROR);
                return false;
            }

            OnDevicePersonalizationPermissions.enforceCallingPermission(
                        mContext, OnDevicePersonalizationPermissions.NOTIFY_MEASUREMENT_EVENT);

            mServiceParcel = Objects.requireNonNull(
                    mParams.getParcelable(
                        Constants.EXTRA_MEASUREMENT_WEB_TRIGGER_PARAMS,
                        MeasurementWebTriggerEventParamsParcel.class));

            Objects.requireNonNull(mServiceParcel.getDestinationUrl());
            Objects.requireNonNull(mServiceParcel.getAppPackageName());
            Objects.requireNonNull(mServiceParcel.getIsolatedService());
            Objects.requireNonNull(mServiceParcel.getIsolatedService().getPackageName());
            Objects.requireNonNull(mServiceParcel.getIsolatedService().getClassName());

            if (mServiceParcel.getDestinationUrl().toString().isBlank()
                    || mServiceParcel.getAppPackageName().isBlank()
                    || mServiceParcel.getIsolatedService().getPackageName().isBlank()
                    || mServiceParcel.getIsolatedService().getClassName().isBlank()) {
                sendErrorResult(Constants.STATUS_INTERNAL_ERROR);
                return false;
            }

            if (mServiceParcel.getCertDigest() != null
                    && !mServiceParcel.getCertDigest().isBlank()) {
                String installedPackageCert = PackageUtils.getCertDigest(
                        mContext, mServiceParcel.getIsolatedService().getPackageName());
                if (!mServiceParcel.getCertDigest().equals(installedPackageCert)) {
                    sLogger.i(TAG + ": Dropping trigger event due to cert mismatch");
                    sendErrorResult(Constants.STATUS_INTERNAL_ERROR);
                    return false;
                }
            }

            AppManifestConfig config = Objects.requireNonNull(
                    AppManifestConfigHelper.getAppManifestConfig(
                            mContext, mServiceParcel.getIsolatedService().getPackageName()));
            if (!mServiceParcel.getIsolatedService()
                    .getClassName()
                    .equals(config.getServiceName())) {
                sLogger.d(TAG + ": service class not found");
                sendErrorResult(Constants.STATUS_CLASS_NOT_FOUND);
                return false;
            }

            return true;
        } catch (Exception e) {
            sendErrorResult(Constants.STATUS_INTERNAL_ERROR);
            return false;
        }
    }

    @Override
    public ComponentName getService() {
        return mServiceParcel.getIsolatedService();
    }

    @Override
    public Bundle getServiceParams() {
        Bundle serviceParams = new Bundle();
        serviceParams.putParcelable(Constants.EXTRA_INPUT,
                new WebTriggerInputParcel.Builder(
                    mServiceParcel.getDestinationUrl(), mServiceParcel.getAppPackageName(),
                    mServiceParcel.getEventData())
                    .build());
        serviceParams.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER,
                new DataAccessServiceImpl(
                    mServiceParcel.getIsolatedService(),
                    mContext, /* includeLocalData */ true,
                    /* includeEventData */ true));
        serviceParams.putParcelable(Constants.EXTRA_USER_DATA,
                new UserDataAccessor().getUserData());

        mModelServiceProvider = new IsolatedModelServiceProvider();
        IIsolatedModelService modelService = mModelServiceProvider.getModelService(mContext);
        serviceParams.putBinder(Constants.EXTRA_MODEL_SERVICE_BINDER, modelService.asBinder());

        return serviceParams;
    }

    @Override
    public void uploadServiceFlowMetrics(ListenableFuture<Bundle> runServiceFuture) {}

    @Override
    public ListenableFuture<WebTriggerOutputParcel> getServiceFlowResultFuture(
            ListenableFuture<Bundle> runServiceFuture) {
        return FluentFuture.from(runServiceFuture)
                .transform(
                    result -> result.getParcelable(
                        Constants.EXTRA_RESULT, WebTriggerOutputParcel.class),
                    mInjector.getExecutor())
                .transform(
                        result -> {
                            writeToLog(mServiceParcel, result);
                            return result;
                        },
                        mInjector.getExecutor())
                .withTimeout(
                        mInjector.getFlags().getIsolatedServiceDeadlineSeconds(),
                        TimeUnit.SECONDS,
                        mInjector.getScheduledExecutor()
                );
    }

    @Override
    public void returnResultThroughCallback(
            ListenableFuture<WebTriggerOutputParcel>  serviceFlowResultFuture) {
        Futures.addCallback(
                serviceFlowResultFuture,
                new FutureCallback<WebTriggerOutputParcel>() {
                    @Override
                    public void onSuccess(WebTriggerOutputParcel result) {
                        sendSuccessResult(result);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        sLogger.w(TAG + ": Request failed.", t);
                        sendErrorResult(Constants.STATUS_INTERNAL_ERROR);
                    }
                },
                mInjector.getExecutor());
    }

    @Override
    public void cleanUpServiceParams() {
        mModelServiceProvider.unBindFromModelService();
    }

    private void writeToLog(
            MeasurementWebTriggerEventParamsParcel wtparams,
            WebTriggerOutputParcel result) {
        sLogger.d(TAG + ": writeToLog() started.");
        var unused = FluentFuture.from(
                        LogUtils.writeLogRecords(
                                mContext,
                                wtparams.getIsolatedService(),
                                result.getRequestLogRecord(),
                                result.getEventLogRecords()))
                .transform(v -> null, MoreExecutors.newDirectExecutorService());
    }

    private boolean getGlobalKillSwitch() {
        long origId = Binder.clearCallingIdentity();
        boolean globalKillSwitch = mInjector.getFlags().getGlobalKillSwitch();
        Binder.restoreCallingIdentity(origId);
        return globalKillSwitch;
    }

    private void sendSuccessResult(WebTriggerOutputParcel result) {
        int responseCode = Constants.STATUS_SUCCESS;
        try {
            mCallback.onSuccess();
        } catch (RemoteException e) {
            responseCode = Constants.STATUS_INTERNAL_ERROR;
            sLogger.w(TAG + ": Callback error", e);
        } finally {
            StatsUtils.writeAppRequestMetrics(mInjector.getClock(), responseCode, mStartTimeMillis);
        }
    }

    private void sendErrorResult(int errorCode) {
        try {
            mCallback.onError(errorCode);
        } catch (RemoteException e) {
            sLogger.w(TAG + ": Callback error", e);
        } finally {
            StatsUtils.writeAppRequestMetrics(mInjector.getClock(), errorCode, mStartTimeMillis);
        }
    }
}
