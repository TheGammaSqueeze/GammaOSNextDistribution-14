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

package com.android.ondevicepersonalization.services.display;

import static com.android.ondevicepersonalization.services.statsd.ApiCallStats.API_SERVICE_ON_EVENT;

import android.adservices.ondevicepersonalization.Constants;
import android.adservices.ondevicepersonalization.EventInputParcel;
import android.adservices.ondevicepersonalization.EventLogRecord;
import android.adservices.ondevicepersonalization.EventOutputParcel;
import android.adservices.ondevicepersonalization.RequestLogRecord;
import android.adservices.ondevicepersonalization.aidl.IIsolatedModelService;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;

import com.android.internal.annotations.VisibleForTesting;
import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.services.Flags;
import com.android.ondevicepersonalization.services.FlagsFactory;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;
import com.android.ondevicepersonalization.services.data.DataAccessServiceImpl;
import com.android.ondevicepersonalization.services.data.events.Event;
import com.android.ondevicepersonalization.services.data.events.EventUrlPayload;
import com.android.ondevicepersonalization.services.data.events.EventsDao;
import com.android.ondevicepersonalization.services.inference.IsolatedModelServiceProvider;
import com.android.ondevicepersonalization.services.policyengine.UserDataAccessor;
import com.android.ondevicepersonalization.services.serviceflow.ServiceFlow;
import com.android.ondevicepersonalization.services.util.Clock;
import com.android.ondevicepersonalization.services.util.MonotonicClock;
import com.android.ondevicepersonalization.services.util.OnDevicePersonalizationFlatbufferUtils;
import com.android.ondevicepersonalization.services.util.StatsUtils;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/** Implementation of common web view client logic. */
public class WebViewFlow implements ServiceFlow<EventOutputParcel> {

    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();

    private static final String TAG = WebViewFlow.class.getSimpleName();

    public static final String TASK_NAME = "ComputeEventMetrics";

    @NonNull
    private final Context mContext;
    @NonNull
    private final ComponentName mService;
    @Nullable
    private final RequestLogRecord mLogRecord;
    @NonNull
    private final Injector mInjector;
    @NonNull
    private IsolatedModelServiceProvider mModelServiceProvider;
    long mQueryId;
    private final EventUrlPayload mPayload;
    private long mStartServiceTimeMillis;
    private final FutureCallback<EventOutputParcel> mCallback;

    public WebViewFlow(Context context, ComponentName service, long queryId,
            RequestLogRecord logRecord, FutureCallback<EventOutputParcel> callback,
            EventUrlPayload payLoad) {
        mContext = context;
        mService = service;
        mQueryId = queryId;
        mLogRecord = logRecord;
        mCallback = callback;
        mPayload = payLoad;
        mInjector = new Injector();
    }

    @VisibleForTesting
    public static class Injector {
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

    @Override
    public boolean isServiceFlowReady() {
        try {
            mStartServiceTimeMillis = mInjector.getClock().elapsedRealtime();

            Objects.requireNonNull(mPayload);

            return true;
        } catch (Exception e) {
            sLogger.d(TAG + "isServiceFlowReady() call failed: " + e.getMessage());
            mCallback.onFailure(e);
            return false;
        }
    }

    @Override
    public ComponentName getService() {
        return mService;
    }

    @Override
    public Bundle getServiceParams() {
        Bundle serviceParams = new Bundle();

        serviceParams.putBinder(Constants.EXTRA_DATA_ACCESS_SERVICE_BINDER,
                new DataAccessServiceImpl(
                        mService, mContext,
                        /* includeLocalData */ true, /* includeEventData */ true));
        serviceParams.putParcelable(Constants.EXTRA_INPUT,
                new EventInputParcel.Builder()
                        .setParameters(mPayload.getEventParams())
                        .setRequestLogRecord(mLogRecord)
                        .build());
        serviceParams.putParcelable(Constants.EXTRA_USER_DATA,
                new UserDataAccessor().getUserData());

        mModelServiceProvider = new IsolatedModelServiceProvider();
        IIsolatedModelService modelService = mModelServiceProvider.getModelService(mContext);
        serviceParams.putBinder(Constants.EXTRA_MODEL_SERVICE_BINDER, modelService.asBinder());

        return serviceParams;
    }

    @Override
    public void uploadServiceFlowMetrics(ListenableFuture<Bundle> runServiceFuture) {
        var unused = FluentFuture.from(runServiceFuture)
                .transform(
                        result -> {
                            StatsUtils.writeServiceRequestMetrics(
                                    API_SERVICE_ON_EVENT,
                                    result, mInjector.getClock(),
                                    Constants.STATUS_SUCCESS,
                                    mStartServiceTimeMillis);
                            return null;
                        },
                        mInjector.getExecutor())
                .catchingAsync(
                        Exception.class,
                        e -> {
                            StatsUtils.writeServiceRequestMetrics(
                                    API_SERVICE_ON_EVENT,
                                    /* result= */ null, mInjector.getClock(),
                                    Constants.STATUS_INTERNAL_ERROR,
                                    mStartServiceTimeMillis);
                            return Futures.immediateFailedFuture(e);
                        },
                        mInjector.getExecutor());
    }

    @Override
    public ListenableFuture<EventOutputParcel> getServiceFlowResultFuture(
            ListenableFuture<Bundle> runServiceFuture) {
        return FluentFuture.from(runServiceFuture)
                .transform(
                        result -> result.getParcelable(
                                Constants.EXTRA_RESULT, EventOutputParcel.class),
                        mInjector.getExecutor())
                .transform(
                        result -> {
                            var unused = writeEvent(result);
                            return result;
                        },
                        mInjector.getExecutor())
                .withTimeout(
                        mInjector.getFlags().getIsolatedServiceDeadlineSeconds(),
                        TimeUnit.SECONDS,
                        mInjector.getScheduledExecutor());
    }

    @Override
    public void returnResultThroughCallback(
            ListenableFuture<EventOutputParcel> serviceFlowResultFuture) {
        Futures.addCallback(
                serviceFlowResultFuture,
                new FutureCallback<>() {
                    @Override
                    public void onSuccess(EventOutputParcel result) {
                        mCallback.onSuccess(result);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        mCallback.onFailure(t);
                    }
                },
                mInjector.getExecutor());
    }

    @Override
    public void cleanUpServiceParams() {
        mModelServiceProvider.unBindFromModelService();
    }

    // TO-DO: Add errors and propagate back to caller through callback.
    private ListenableFuture<Void> writeEvent(EventOutputParcel result) {
        try {
            sLogger.d(TAG + ": writeEvent() called. EventOutputParcel: " + result.toString());
            if (result == null || result.getEventLogRecord() == null
                    || mLogRecord == null || mLogRecord.getRows() == null) {
                sLogger.d(TAG + "no EventLogRecord or RequestLogRecord");
                return Futures.immediateFuture(null);
            }
            EventLogRecord eventData = result.getEventLogRecord();
            int rowCount = mLogRecord.getRows().size();
            if (eventData.getType() <= 0 || eventData.getRowIndex() < 0
                    || eventData.getRowIndex() >= rowCount) {
                sLogger.w(TAG + ": rowOffset out of range");
                return Futures.immediateFuture(null);
            }
            byte[] data = OnDevicePersonalizationFlatbufferUtils.createEventData(
                    eventData.getData());
            Event event = new Event.Builder()
                    .setType(eventData.getType())
                    .setQueryId(mQueryId)
                    .setService(mService)
                    .setTimeMillis(mInjector.getClock().currentTimeMillis())
                    .setRowIndex(eventData.getRowIndex())
                    .setEventData(data)
                    .build();
            if (-1 == EventsDao.getInstance(mContext).insertEvent(event)) {
                sLogger.e(TAG + ": Failed to insert event: " + event);
            }
            return Futures.immediateFuture(null);
        } catch (Exception e) {
            sLogger.e(TAG + ": writeEvent() failed", e);
            return Futures.immediateFailedFuture(e);
        }
    }
}
