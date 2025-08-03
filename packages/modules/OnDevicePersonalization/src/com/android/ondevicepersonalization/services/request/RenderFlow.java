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

package com.android.ondevicepersonalization.services.request;

import static com.android.ondevicepersonalization.services.statsd.ApiCallStats.API_SERVICE_ON_RENDER;

import android.adservices.ondevicepersonalization.Constants;
import android.adservices.ondevicepersonalization.RenderInputParcel;
import android.adservices.ondevicepersonalization.RenderOutputParcel;
import android.adservices.ondevicepersonalization.RenderingConfig;
import android.adservices.ondevicepersonalization.RequestLogRecord;
import android.adservices.ondevicepersonalization.aidl.IRequestSurfacePackageCallback;
import android.annotation.NonNull;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.view.SurfaceControlViewHost.SurfacePackage;

import com.android.internal.annotations.VisibleForTesting;
import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.services.Flags;
import com.android.ondevicepersonalization.services.FlagsFactory;
import com.android.ondevicepersonalization.services.OdpServiceException;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;
import com.android.ondevicepersonalization.services.data.DataAccessServiceImpl;
import com.android.ondevicepersonalization.services.data.user.UserPrivacyStatus;
import com.android.ondevicepersonalization.services.display.DisplayHelper;
import com.android.ondevicepersonalization.services.manifest.AppManifestConfigHelper;
import com.android.ondevicepersonalization.services.serviceflow.ServiceFlow;
import com.android.ondevicepersonalization.services.util.Clock;
import com.android.ondevicepersonalization.services.util.CryptUtils;
import com.android.ondevicepersonalization.services.util.DebugUtils;
import com.android.ondevicepersonalization.services.util.MonotonicClock;
import com.android.ondevicepersonalization.services.util.StatsUtils;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Handles a surface package request from an app or SDK.
 */
public class RenderFlow implements ServiceFlow<SurfacePackage> {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String TAG = "RenderFlow";

    @VisibleForTesting
    static class Injector {
        ListeningExecutorService getExecutor() {
            return OnDevicePersonalizationExecutors.getBackgroundExecutor();
        }

        SlotWrapper decryptToken(String slotResultToken) throws Exception {
            return (SlotWrapper) CryptUtils.decrypt(slotResultToken);
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

    @NonNull
    private final String mSlotResultToken;
    @NonNull
    private final IBinder mHostToken;
    @NonNull private final int mDisplayId;
    @NonNull private final int mWidth;
    @NonNull private final int mHeight;
    @NonNull
    private final IRequestSurfacePackageCallback mCallback;
    @NonNull
    private final Context mContext;
    private final long mStartTimeMillis;
    @NonNull
    private final Injector mInjector;
    @NonNull
    private final DisplayHelper mDisplayHelper;
    @NonNull
    private ComponentName mService;
    private SlotWrapper mSlotWrapper;
    private long mStartServiceTimeMillis;

    public RenderFlow(
            @NonNull String slotResultToken,
            @NonNull IBinder hostToken,
            int displayId,
            int width,
            int height,
            @NonNull IRequestSurfacePackageCallback callback,
            @NonNull Context context,
            long startTimeMillis) {
        this(slotResultToken, hostToken, displayId, width, height,
                callback, context, startTimeMillis,
                new Injector(),
                new DisplayHelper(context));
    }

    @VisibleForTesting
    RenderFlow(
            @NonNull String slotResultToken,
            @NonNull IBinder hostToken,
            int displayId,
            int width,
            int height,
            @NonNull IRequestSurfacePackageCallback callback,
            @NonNull Context context,
            long startTimeMillis,
            @NonNull Injector injector,
            @NonNull DisplayHelper displayHelper) {
        sLogger.d(TAG + ": RenderFlow created.");
        mSlotResultToken = Objects.requireNonNull(slotResultToken);
        mHostToken = Objects.requireNonNull(hostToken);
        mDisplayId = displayId;
        mWidth = width;
        mHeight = height;
        mCallback = Objects.requireNonNull(callback);
        mStartTimeMillis = startTimeMillis;
        mInjector = Objects.requireNonNull(injector);
        mContext = Objects.requireNonNull(context);
        mDisplayHelper = Objects.requireNonNull(displayHelper);
    }

    @Override
    public boolean isServiceFlowReady() {
        mStartServiceTimeMillis = mInjector.getClock().elapsedRealtime();

        try {
            if (!isPersonalizationStatusEnabled()) {
                sLogger.d(TAG + ": Personalization is disabled.");
                sendErrorResult(Constants.STATUS_PERSONALIZATION_DISABLED, 0);
                return false;
            }

            mSlotWrapper = Objects.requireNonNull(
                    mInjector.decryptToken(mSlotResultToken));
            String servicePackageName = Objects.requireNonNull(
                    mSlotWrapper.getServicePackageName());
            String serviceClassName = Objects.requireNonNull(
                    AppManifestConfigHelper.getServiceNameFromOdpSettings(
                            mContext, servicePackageName));
            mService = ComponentName.createRelative(servicePackageName, serviceClassName);
        } catch (Exception e) {
            sendErrorResult(Constants.STATUS_INTERNAL_ERROR, 0);
            return false;
        }
        return true;
    }

    @Override
    public ComponentName getService() {
        return mService;
    }

    @Override
    public Bundle getServiceParams() {
        RenderingConfig renderingConfig =
                Objects.requireNonNull(mSlotWrapper.getRenderingConfig());

        Bundle serviceParams = new Bundle();

        serviceParams.putParcelable(
                Constants.EXTRA_INPUT, new RenderInputParcel.Builder()
                        .setHeight(mHeight)
                        .setWidth(mWidth)
                        .setRenderingConfig(renderingConfig)
                        .build());
        serviceParams.putBinder(
                Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER, new DataAccessServiceImpl(
                        mService, mContext, /* includeLocalData */ false,
                        /* includeEventData */ false));

        return serviceParams;
    }

    @Override
    public void uploadServiceFlowMetrics(ListenableFuture<Bundle> runServiceFuture) {
        var unused = FluentFuture.from(runServiceFuture)
                .transform(
                        val -> {
                            StatsUtils.writeServiceRequestMetrics(
                                    API_SERVICE_ON_RENDER, val, mInjector.getClock(),
                                    Constants.STATUS_SUCCESS, mStartServiceTimeMillis);
                            return val;
                        },
                        mInjector.getExecutor()
                )
                .catchingAsync(
                        Exception.class,
                        e -> {
                            StatsUtils.writeServiceRequestMetrics(
                                    API_SERVICE_ON_RENDER, /* result= */ null,
                                    mInjector.getClock(),
                                    Constants.STATUS_INTERNAL_ERROR, mStartServiceTimeMillis);
                            return Futures.immediateFailedFuture(e);
                        },
                        mInjector.getExecutor()
                );
    }

    @Override
    public ListenableFuture<SurfacePackage> getServiceFlowResultFuture(
            ListenableFuture<Bundle> runServiceFuture) {
        RequestLogRecord logRecord = mSlotWrapper.getLogRecord();
        long queryId = mSlotWrapper.getQueryId();

        return FluentFuture.from(runServiceFuture)
                .transform(
                        result ->
                                result.getParcelable(
                                        Constants.EXTRA_RESULT, RenderOutputParcel.class),
                        mInjector.getExecutor())
                .transform(
                        result -> mDisplayHelper.generateHtml(
                                result, mService),
                        mInjector.getExecutor())
                .transformAsync(
                        result -> mDisplayHelper.displayHtml(
                                result,
                                logRecord,
                                queryId,
                                mService,
                                mHostToken,
                                mDisplayId,
                                mWidth,
                                mHeight),
                        mInjector.getExecutor())
                .withTimeout(
                        mInjector.getFlags().getIsolatedServiceDeadlineSeconds(),
                        TimeUnit.SECONDS,
                        mInjector.getScheduledExecutor()
                );
    }

    @Override
    public  void returnResultThroughCallback(
            ListenableFuture<SurfacePackage> serviceFlowResultFuture) {
        Futures.addCallback(
                serviceFlowResultFuture,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(SurfacePackage surfacePackage) {
                        sendDisplayResult(surfacePackage);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        sLogger.w(TAG + ": Request failed.", t);
                        if (t instanceof OdpServiceException) {
                            OdpServiceException e = (OdpServiceException) t;
                            sendErrorResult(
                                    e.getErrorCode(),
                                    DebugUtils.getIsolatedServiceExceptionCode(
                                            mContext, mService, e));
                        } else {
                            sendErrorResult(Constants.STATUS_INTERNAL_ERROR, 0);
                        }
                    }
                },
                mInjector.getExecutor());
    }

    @Override
    public void cleanUpServiceParams() {}

    private void sendDisplayResult(SurfacePackage surfacePackage) {
        if (surfacePackage != null) {
            sendSuccessResult(surfacePackage);
        } else {
            sLogger.w(TAG + ": surfacePackages is null or empty");
            sendErrorResult(Constants.STATUS_INTERNAL_ERROR, 0);
        }
    }

    private void sendSuccessResult(SurfacePackage surfacePackage) {
        int responseCode = Constants.STATUS_SUCCESS;
        try {
            mCallback.onSuccess(surfacePackage);
        } catch (RemoteException e) {
            responseCode = Constants.STATUS_INTERNAL_ERROR;
            sLogger.w(TAG + ": Callback error", e);
        } finally {
            StatsUtils.writeAppRequestMetrics(mInjector.getClock(), responseCode, mStartTimeMillis);
        }
    }

    private void sendErrorResult(int errorCode, int isolatedServiceErrorCode) {
        try {
            mCallback.onError(errorCode, isolatedServiceErrorCode);
        } catch (RemoteException e) {
            sLogger.w(TAG + ": Callback error", e);
        } finally {
            StatsUtils.writeAppRequestMetrics(mInjector.getClock(), errorCode, mStartTimeMillis);
        }
    }

    private boolean isPersonalizationStatusEnabled() {
        UserPrivacyStatus privacyStatus = UserPrivacyStatus.getInstance();
        return privacyStatus.isPersonalizationStatusEnabled();
    }
}
