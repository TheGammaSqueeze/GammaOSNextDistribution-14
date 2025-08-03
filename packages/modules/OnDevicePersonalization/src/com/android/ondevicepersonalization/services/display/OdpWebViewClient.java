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

package com.android.ondevicepersonalization.services.display;

import android.adservices.ondevicepersonalization.EventOutputParcel;
import android.adservices.ondevicepersonalization.RequestLogRecord;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.android.internal.annotations.VisibleForTesting;
import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationExecutors;
import com.android.ondevicepersonalization.services.data.events.EventUrlHelper;
import com.android.ondevicepersonalization.services.data.events.EventUrlPayload;
import com.android.ondevicepersonalization.services.serviceflow.ServiceFlowOrchestrator;
import com.android.ondevicepersonalization.services.serviceflow.ServiceFlowType;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListeningExecutorService;

import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.Collections;
import java.util.Objects;

public class OdpWebViewClient extends WebViewClient {
    private static final String TAG = "OdpWebViewClient";

    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final ServiceFlowOrchestrator sSfo = ServiceFlowOrchestrator.getInstance();

    long mQueryId;
    private final Injector mInjector;
    @NonNull
    private final Context mContext;
    @NonNull
    private final ComponentName mService;
    @Nullable
    private final RequestLogRecord mLogRecord;

    private static final WebResourceResponse EMPTY_RESPONSE = new WebResourceResponse(
            /* MimeType= */ null, /* Encoding= */ null,
            HttpURLConnection.HTTP_NO_CONTENT, "No Content",
            Collections.emptyMap(), InputStream.nullInputStream());

    public OdpWebViewClient(Context context, ComponentName service, long queryId,
            RequestLogRecord logRecord) {
        this(context, service, queryId, logRecord, new Injector());
    }

    @VisibleForTesting
    public OdpWebViewClient(Context context, ComponentName service, long queryId,
            RequestLogRecord logRecord, Injector injector) {
        mContext = context;
        mService = service;
        mQueryId = queryId;
        mLogRecord = logRecord;
        mInjector = injector;
    }

    @VisibleForTesting
    public static class Injector {
        ListeningExecutorService getExecutor() {
            return OnDevicePersonalizationExecutors.getBackgroundExecutor();
        }

        FutureCallback<EventOutputParcel> getFutureCallback() {
            return new FutureCallback<>() {
                @Override
                public void onSuccess(EventOutputParcel result) {}

                @Override
                public void onFailure(@NotNull Throwable t) {}
            };
        }

        void openUrl(String landingPage, Context context) {
            if (landingPage != null) {
                sLogger.d(TAG + ": Sending intent to open landingPage: " + landingPage);
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(landingPage));
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
            }
        }
    }

    @Override
    public WebResourceResponse shouldInterceptRequest(
            @NonNull WebView webView, @NonNull WebResourceRequest request) {
        FutureCallback<EventOutputParcel> callback = mInjector.getFutureCallback();

        try {
            if (!EventUrlHelper.isOdpUrl(request.getUrl().toString())) {
                return null;
            }

            EventUrlPayload payload = getPayLoadFromRequest(webView, request);

            try {
                sSfo.schedule(ServiceFlowType.WEB_VIEW_FLOW, mContext,
                        mService, mQueryId, mLogRecord, callback, payload);
            } catch (Exception e) {
                sLogger.e(e, TAG + ": shouldInterceptRequest: WebViewFlow failed.");
                callback.onFailure(e);
            }

            byte[] responseData = payload.getResponseData();
            if (responseData == null || responseData.length == 0) {
                return EMPTY_RESPONSE;
            } else {
                return new WebResourceResponse(
                        payload.getMimeType(), /* Encoding= */ null,
                        HttpURLConnection.HTTP_OK, "OK",
                        Collections.emptyMap(), new ByteArrayInputStream(responseData));
            }
        } catch (Exception e) {
            sLogger.e(e, TAG + ": shouldInterceptRequest failed.");
            return null;
        }
    }

    @Override
    public boolean shouldOverrideUrlLoading(
            @NonNull WebView webView, @NonNull WebResourceRequest request) {
        FutureCallback<EventOutputParcel> callback = mInjector.getFutureCallback();

        try {
            if (!EventUrlHelper.isOdpUrl(request.getUrl().toString())) {
                return false;
            }

            EventUrlPayload payload = getPayLoadFromRequest(webView, request);

            try {
                sSfo.schedule(ServiceFlowType.WEB_VIEW_FLOW, mContext,
                        mService, mQueryId, mLogRecord, mInjector.getFutureCallback(), payload);
            } catch (Exception e) {
                sLogger.e(e, TAG + ": shouldOverrideUrlLoading: WebViewFlow failed.");
                callback.onFailure(e);
            }

            String landingPage = request.getUrl().getQueryParameter(
                        EventUrlHelper.URL_LANDING_PAGE_EVENT_KEY);
            mInjector.openUrl(landingPage, webView.getContext());

            return true;
        } catch (Exception e) {
            sLogger.e(e, TAG + ": shouldOverrideUrlLoading failed.");
            return false;
        }
    }

    private EventUrlPayload getPayLoadFromRequest(
            @NonNull WebView webView, @NonNull  WebResourceRequest request) throws Exception {
        Objects.requireNonNull(webView);
        Objects.requireNonNull(request);
        Objects.requireNonNull(request.getUrl());

        String url = request.getUrl().toString();

        if (!EventUrlHelper.isOdpUrl(url)) {
            throw new IllegalArgumentException("Input request does not contain a valid ODP URL.");
        }

        return EventUrlHelper.getEventFromOdpEventUrl(url);
    }
}
