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
import com.android.adservices.service.common.PermissionHelper;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.consent.AdServicesApiConsent;
import com.android.adservices.service.consent.AdServicesApiType;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.exception.FilterException;
import com.android.adservices.service.measurement.MeasurementImpl;
import com.android.adservices.service.measurement.access.AppPackageAccessResolver;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.ReportInteractionApiCalledStats;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

/** Implements an {@link EventReporter} that reports and registers an event. */
@RequiresApi(Build.VERSION_CODES.S)
class ReportAndRegisterEventImpl extends EventReporter {
    @NonNull private final MeasurementImpl mMeasurementService;
    @NonNull final ConsentManager mConsentManager;
    @NonNull final Context mContext;

    ReportAndRegisterEventImpl(
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
                shouldUseUnifiedTables);

        Objects.requireNonNull(measurementService);
        Objects.requireNonNull(context);
        Objects.requireNonNull(consentManager);

        mMeasurementService = measurementService;
        mConsentManager = consentManager;
        mContext = context;
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
        reportingUrisFuture
                .transformAsync(
                        reportingUris -> {
                            if (mFlags.getFledgeBeaconReportingMetricsEnabled()) {
                                mAdServicesLogger.logReportInteractionApiCalledStats(
                                        ReportInteractionApiCalledStats.builder()
                                                .setBeaconReportingDestinationType(
                                                        input.getReportingDestinations())
                                                .setNumMatchingUris(reportingUris.size())
                                                .build());
                            }
                            if (canMeasurementRegisterAndReport(input)) {
                                return reportAndRegisterUris(reportingUris, input);
                            } else {
                                return reportUris(reportingUris, input);
                            }
                        },
                        mLightweightExecutorService)
                .addCallback(
                        new FutureCallback<List<Void>>() {
                            @Override
                            public void onSuccess(List<Void> result) {
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

    boolean canMeasurementRegisterAndReport(ReportInteractionInput input) {
        sLogger.v("Checking if measurement can register and report the event.");

        // Checking if ARA killswitch is enabled.
        if (mFlags.getMeasurementApiRegisterSourceKillSwitch()) {
            sLogger.v("Skipping event registration: Measurement killswitch is enabled.");
            return false;
        }

        // Checking if app is allowlisted to use ARA.
        AppPackageAccessResolver appPackageAccessResolver =
                new AppPackageAccessResolver(
                        mFlags.getMsmtApiAppAllowList(),
                        mFlags.getMsmtApiAppBlockList(),
                        input.getCallerPackageName());
        if (!appPackageAccessResolver.getAccessInfo(mContext).isAllowedAccess()) {
            sLogger.v("Skipping event registration: App is not allowlisted to use ARA.");
            return false;
        }

        // Check if user consent is granted.
        AdServicesApiConsent userConsent =
                mConsentManager.getConsent(AdServicesApiType.MEASUREMENTS);

        if (!userConsent.isGiven()) {
            sLogger.v("Skipping event registration: User consent is revoked.");
            return false;
        }

        // Check if ARA access permission is granted.
        if (!PermissionHelper.hasAttributionPermission(mContext, input.getCallerPackageName())) {
            sLogger.v("Skipping event registration: Caller lacks permission to use ARA.");
            return false;
        }

        sLogger.v("Confirmed measurement can register and report the event.");
        return true;
    }

    ListenableFuture<List<Void>> reportAndRegisterUris(
            List<Uri> reportingUris, ReportInteractionInput input) {
        List<ListenableFuture<Void>> reportingFuturesList = new ArrayList<>();
        String eventData = input.getInteractionData();

        for (Uri uri : reportingUris) {
            sLogger.v("Uri to report and register the event: %s.", uri);
            reportingFuturesList.add(
                    FluentFuture.from(
                            mLightweightExecutorService.submit(
                                    () -> {
                                        try {
                                            mMeasurementService.registerEvent(
                                                    uri,
                                                    input.getCallerPackageName(),
                                                    input.getCallerSdkName(),
                                                    input.getAdId() != null,
                                                    eventData,
                                                    input.getInputEvent(),
                                                    input.getAdId());
                                        } catch (Exception e) {
                                            sLogger.d(e, "registerEvent() call failed.");
                                            throw e;
                                        }
                                        return null;
                                    })));
        }
        return Futures.allAsList(reportingFuturesList);
    }
}
