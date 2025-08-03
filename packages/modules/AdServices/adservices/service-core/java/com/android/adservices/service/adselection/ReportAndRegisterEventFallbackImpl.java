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

package com.android.adservices.service.adselection;

import static android.adservices.common.AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
import static android.adservices.common.AdServicesStatusUtils.STATUS_IO_ERROR;
import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;

import android.adservices.adselection.ReportInteractionCallback;
import android.adservices.adselection.ReportInteractionInput;
import android.annotation.NonNull;
import android.annotation.RequiresApi;
import android.content.Context;
import android.net.Uri;
import android.os.Build;

import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.AdSelectionServiceFilter;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.exception.FilterException;
import com.android.adservices.service.measurement.MeasurementImpl;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.ReportInteractionApiCalledStats;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;

/** Implements an {@link EventReporter} that reports and registers an event with a fallback. */
@RequiresApi(Build.VERSION_CODES.S)
class ReportAndRegisterEventFallbackImpl extends ReportAndRegisterEventImpl {
    ReportAndRegisterEventFallbackImpl(
            @NonNull AdSelectionEntryDao adSelectionEntryDao,
            @NonNull AdServicesHttpsClient adServicesHttpsClient,
            @NonNull ExecutorService lightweightExecutorService,
            @NonNull ExecutorService backgroundExecutorService,
            @NonNull AdServicesLogger adServicesLogger,
            @NonNull Flags flags,
            @NonNull AdSelectionServiceFilter adSelectionServiceFilter,
            int callerUid,
            @NonNull FledgeAuthorizationFilter fledgeAuthorizationFilter,
            @NonNull DevContext devContext,
            @NonNull MeasurementImpl measurementService,
            @NonNull ConsentManager consentManager,
            @NonNull Context context,
            boolean shouldUseUnifiedTables) {
        super(
                adSelectionEntryDao,
                adServicesHttpsClient,
                lightweightExecutorService,
                backgroundExecutorService,
                adServicesLogger,
                flags,
                adSelectionServiceFilter,
                callerUid,
                fledgeAuthorizationFilter,
                devContext,
                measurementService,
                consentManager,
                context,
                shouldUseUnifiedTables);
    }

    @Override
    public void reportInteraction(
            @NonNull ReportInteractionInput input, @NonNull ReportInteractionCallback callback) {
        FluentFuture<Void> filterAndValidateRequestFuture =
                FluentFuture.from(
                        Futures.submit(
                                () -> filterAndValidateRequest(input),
                                mLightweightExecutorService));
        filterAndValidateRequestFuture.addCallback(
                new FutureCallback<Void>() {
                    @Override
                    public void onSuccess(Void result) {
                        sLogger.v("reportEvent() was notified as successful.");
                        notifySuccessToCaller(callback);
                        performReporting(input);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        sLogger.e(t, "reportEvent() failed!");
                        if (t instanceof FilterException
                                && t.getCause() instanceof ConsentManager.RevokedConsentException) {
                            // Skip logging if a FilterException occurs.
                            // AdSelectionServiceFilter ensures the failing assertion is logged
                            // internally.

                            // Fail Silently by notifying success to caller
                            notifySuccessToCaller(callback);
                        } else {
                            notifyFailureToCaller(input.getCallerPackageName(), callback, t);
                        }
                    }
                },
                mLightweightExecutorService);
    }

    void performReporting(@NonNull ReportInteractionInput input) {
        FluentFuture<List<Uri>> reportingUrisFuture = getReportingUris(input);

        ListenableFuture<List<Void>> reportingFuture =
                reportingUrisFuture.transformAsync(
                        reportingUris -> reportUris(reportingUris, input),
                        mLightweightExecutorService);

        ListenableFuture<List<Void>> reportingAndRegisteringFuture =
                reportingUrisFuture.transformAsync(
                        reportingUris -> {
                            if (canMeasurementRegisterAndReport(input)) {
                                if (mFlags.getFledgeBeaconReportingMetricsEnabled()) {
                                    mAdServicesLogger.logReportInteractionApiCalledStats(
                                            ReportInteractionApiCalledStats.builder()
                                                    .setBeaconReportingDestinationType(
                                                            input.getReportingDestinations())
                                                    .setNumMatchingUris(reportingUris.size())
                                                    .build()
                                    );
                                }
                                return reportAndRegisterUris(reportingUris, input);
                            }
                            return Futures.immediateFuture(null);
                        },
                        mLightweightExecutorService);

        FluentFuture.from(Futures.allAsList(reportingFuture, reportingAndRegisteringFuture))
                .addCallback(
                        new FutureCallback<>() {
                            @Override
                            public void onSuccess(List<List<Void>> result) {
                                sLogger.d("reportEvent() completed successfully.");
                                mAdServicesLogger.logFledgeApiCallStats(
                                        LOGGING_API_NAME,
                                        input.getCallerPackageName(),
                                        STATUS_SUCCESS,
                                        /*latencyMs=*/ 0);
                            }

                            @Override
                            public void onFailure(Throwable t) {
                                sLogger.e(t, "reportEvent() encountered failure!");
                                if (t instanceof IOException) {
                                    mAdServicesLogger.logFledgeApiCallStats(
                                            LOGGING_API_NAME,
                                            input.getCallerPackageName(),
                                            STATUS_IO_ERROR,
                                            /*latencyMs=*/ 0);
                                } else {
                                    mAdServicesLogger.logFledgeApiCallStats(
                                            LOGGING_API_NAME,
                                            input.getCallerPackageName(),
                                            STATUS_INTERNAL_ERROR,
                                            /*latencyMs=*/ 0);
                                }
                            }
                        },
                        mLightweightExecutorService);
    }
}
