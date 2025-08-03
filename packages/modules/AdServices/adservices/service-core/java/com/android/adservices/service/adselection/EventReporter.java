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

import static android.adservices.adselection.ReportEventRequest.FLAG_REPORTING_DESTINATION_BUYER;
import static android.adservices.adselection.ReportEventRequest.FLAG_REPORTING_DESTINATION_SELLER;
import static android.adservices.adselection.ReportEventRequest.REPORT_EVENT_MAX_INTERACTION_DATA_SIZE_B;

import static com.android.adservices.service.common.FledgeAuthorizationFilter.AdTechNotAllowedException;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION;

import static java.util.Locale.ENGLISH;

import android.adservices.adselection.ReportEventRequest;
import android.adservices.adselection.ReportInteractionCallback;
import android.adservices.adselection.ReportInteractionInput;
import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.FledgeErrorResponse;
import android.annotation.NonNull;
import android.net.Uri;
import android.os.Build;
import android.os.RemoteException;
import android.os.Trace;

import androidx.annotation.RequiresApi;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.AdSelectionServiceFilter;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.common.Throttler;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.exception.FilterException;
import com.android.adservices.service.profiling.Tracing;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.internal.util.Preconditions;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

/** Encapsulates the Event Reporting logic */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public abstract class EventReporter {
    public static final String NO_MATCH_FOUND_IN_AD_SELECTION_DB =
            "Could not find a match in the database for this adSelectionId and callerPackageName!";
    public static final String INTERACTION_DATA_SIZE_MAX_EXCEEDED = "Event data max size exceeded!";
    public static final String INTERACTION_KEY_SIZE_MAX_EXCEEDED = "Event key max size exceeded!";
    static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    static final int LOGGING_API_NAME = AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION;

    @ReportEventRequest.ReportingDestination
    private static final int[] POSSIBLE_DESTINATIONS =
            new int[] {FLAG_REPORTING_DESTINATION_SELLER, FLAG_REPORTING_DESTINATION_BUYER};

    @NonNull private final AdSelectionEntryDao mAdSelectionEntryDao;
    @NonNull private final AdServicesHttpsClient mAdServicesHttpsClient;
    @NonNull final ListeningExecutorService mLightweightExecutorService;
    @NonNull private final ListeningExecutorService mBackgroundExecutorService;
    @NonNull final AdServicesLogger mAdServicesLogger;
    @NonNull final Flags mFlags;
    @NonNull private final AdSelectionServiceFilter mAdSelectionServiceFilter;
    private int mCallerUid;
    @NonNull private final FledgeAuthorizationFilter mFledgeAuthorizationFilter;
    @NonNull private final DevContext mDevContext;
    private final boolean mShouldUseUnifiedTables;

    public EventReporter(
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
            boolean shouldUseUnifiedTables) {
        Objects.requireNonNull(adSelectionEntryDao);
        Objects.requireNonNull(adServicesHttpsClient);
        Objects.requireNonNull(lightweightExecutorService);
        Objects.requireNonNull(backgroundExecutorService);
        Objects.requireNonNull(adServicesLogger);
        Objects.requireNonNull(flags);
        Objects.requireNonNull(adSelectionServiceFilter);
        Objects.requireNonNull(fledgeAuthorizationFilter);
        Objects.requireNonNull(devContext);

        mAdSelectionEntryDao = adSelectionEntryDao;
        mAdServicesHttpsClient = adServicesHttpsClient;
        mLightweightExecutorService = MoreExecutors.listeningDecorator(lightweightExecutorService);
        mBackgroundExecutorService = MoreExecutors.listeningDecorator(backgroundExecutorService);
        mAdServicesLogger = adServicesLogger;
        mFlags = flags;
        mAdSelectionServiceFilter = adSelectionServiceFilter;
        mCallerUid = callerUid;
        mFledgeAuthorizationFilter = fledgeAuthorizationFilter;
        mDevContext = devContext;
        mShouldUseUnifiedTables = shouldUseUnifiedTables;
    }

    /**
     * Run the interaction report logic asynchronously. Searches the {@code
     * registered_ad_interactions} database for matches based on the provided {@code adSelectionId},
     * {@code interactionKey}, {@code destinations} that we get from {@link ReportInteractionInput}
     * Then, attaches {@code interactionData} to each found Uri and performs a POST request.
     *
     * <p>After validating the inputParams and request context, invokes {@link
     * ReportInteractionCallback#onSuccess()} before continuing with reporting. If we encounter a
     * failure during request validation, we invoke {@link
     * ReportInteractionCallback#onFailure(FledgeErrorResponse)} and exit early.
     */
    public abstract void reportInteraction(
            @NonNull ReportInteractionInput input, @NonNull ReportInteractionCallback callback);

    void filterAndValidateRequest(ReportInteractionInput input) {
        long registeredAdBeaconsMaxInteractionKeySizeB =
                mFlags.getFledgeReportImpressionRegisteredAdBeaconsMaxInteractionKeySizeB();

        try {
            Trace.beginSection(Tracing.VALIDATE_REQUEST);
            sLogger.v("Starting filtering and validation.");
            mAdSelectionServiceFilter.filterRequest(
                    null,
                    input.getCallerPackageName(),
                    mFlags.getEnforceForegroundStatusForFledgeReportInteraction(),
                    true,
                    mCallerUid,
                    LOGGING_API_NAME,
                    Throttler.ApiKey.FLEDGE_API_REPORT_INTERACTION,
                    mDevContext);
            validateAdSelectionIdAndCallerPackageNameExistence(
                    input.getAdSelectionId(), input.getCallerPackageName());
            Preconditions.checkArgument(
                    input.getInteractionKey().getBytes(StandardCharsets.UTF_8).length
                            <= registeredAdBeaconsMaxInteractionKeySizeB,
                    INTERACTION_KEY_SIZE_MAX_EXCEEDED);
            Preconditions.checkArgument(
                    input.getInteractionData().getBytes(StandardCharsets.UTF_8).length
                            <= REPORT_EVENT_MAX_INTERACTION_DATA_SIZE_B,
                    INTERACTION_DATA_SIZE_MAX_EXCEEDED);
        } finally {
            sLogger.v("Completed filtering and validation.");
            Trace.endSection();
        }
    }

    FluentFuture<List<Uri>> getReportingUris(ReportInteractionInput input) {
        sLogger.v(
                "Fetching ad selection entry ID %d for caller \"%s\"",
                input.getAdSelectionId(), input.getCallerPackageName());
        long adSelectionId = input.getAdSelectionId();
        int destinationsBitField = input.getReportingDestinations();
        String interactionKey = input.getInteractionKey();

        return FluentFuture.from(
                        mBackgroundExecutorService.submit(
                                () -> {
                                    List<Uri> resultingReportingUris = new ArrayList<>();
                                    for (int destination : POSSIBLE_DESTINATIONS) {
                                        if (bitExists(destination, destinationsBitField)) {
                                            if (mAdSelectionEntryDao
                                                    .doesRegisteredAdInteractionExist(
                                                            adSelectionId,
                                                            interactionKey,
                                                            destination)) {
                                                resultingReportingUris.add(
                                                        mAdSelectionEntryDao
                                                                .getRegisteredAdInteractionUri(
                                                                        adSelectionId,
                                                                        interactionKey,
                                                                        destination));
                                            }
                                        }
                                    }
                                    return resultingReportingUris;
                                }))
                .transformAsync(this::filterReportingUris, mLightweightExecutorService);
    }

    private FluentFuture<List<Uri>> filterReportingUris(List<Uri> reportingUris) {
        return FluentFuture.from(
                mLightweightExecutorService.submit(
                        () -> {
                            if (mFlags.getDisableFledgeEnrollmentCheck()) {
                                return reportingUris;
                            } else {
                                // Do enrollment check and only add Uris that pass enrollment
                                ArrayList<Uri> validatedUris = new ArrayList<>();

                                for (Uri uri : reportingUris) {
                                    try {
                                        mFledgeAuthorizationFilter.assertAdTechEnrolled(
                                                AdTechIdentifier.fromString(uri.getHost()),
                                                LOGGING_API_NAME);
                                        validatedUris.add(uri);
                                    } catch (AdTechNotAllowedException exception) {
                                        sLogger.d(
                                                String.format(
                                                        ENGLISH,
                                                        "Enrollment check failed! Skipping"
                                                                + " reporting for %s:",
                                                        uri));
                                    }
                                }
                                return validatedUris;
                            }
                        }));
    }

    ListenableFuture<List<Void>> reportUris(List<Uri> reportingUris, ReportInteractionInput input) {
        List<ListenableFuture<Void>> reportingFuturesList = new ArrayList<>();
        String eventData = input.getInteractionData();

        for (Uri uri : reportingUris) {
            sLogger.v("Uri to report the event: %s.", uri);
            reportingFuturesList.add(
                    mAdServicesHttpsClient.postPlainText(uri, eventData, mDevContext));
        }
        return Futures.allAsList(reportingFuturesList);
    }

    void notifySuccessToCaller(@NonNull ReportInteractionCallback callback) {
        try {
            callback.onSuccess();
        } catch (RemoteException e) {
            sLogger.e(e, "Unable to send successful result to the callback");
        }
    }

    void notifyFailureToCaller(
            @NonNull String callerAppPackageName,
            @NonNull ReportInteractionCallback callback,
            @NonNull Throwable t) {
        int resultCode;

        boolean isFilterException = t instanceof FilterException;

        if (isFilterException) {
            resultCode = FilterException.getResultCode(t);
        } else if (t instanceof IllegalArgumentException) {
            resultCode = AdServicesStatusUtils.STATUS_INVALID_ARGUMENT;
        } else {
            resultCode = AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
        }

        // Skip logging if a FilterException occurs.
        // AdSelectionServiceFilter ensures the failing assertion is logged internally.
        // Note: Failure is logged before the callback to ensure deterministic testing.
        if (!isFilterException) {
            mAdServicesLogger.logFledgeApiCallStats(
                    LOGGING_API_NAME, callerAppPackageName, resultCode, /*latencyMs=*/ 0);
        }

        try {
            callback.onFailure(
                    new FledgeErrorResponse.Builder()
                            .setStatusCode(resultCode)
                            .setErrorMessage(t.getMessage())
                            .build());
        } catch (RemoteException e) {
            sLogger.e(e, "Unable to send failed result to the callback");
        }
    }

    private void validateAdSelectionIdAndCallerPackageNameExistence(
            long adSelectionId, String callerPackageName) {
        if (mFlags.getFledgeAuctionServerEnabledForReportEvent() || mShouldUseUnifiedTables) {
            Preconditions.checkArgument(
                    mAdSelectionEntryDao.doesAdSelectionIdAndCallerPackageNameExists(
                            adSelectionId, callerPackageName),
                    NO_MATCH_FOUND_IN_AD_SELECTION_DB);

        } else {
            Preconditions.checkArgument(
                    mAdSelectionEntryDao
                            .doesAdSelectionMatchingCallerPackageNameExistInOnDeviceTable(
                                    adSelectionId, callerPackageName),
                    NO_MATCH_FOUND_IN_AD_SELECTION_DB);
        }
    }

    private boolean bitExists(
            @ReportEventRequest.ReportingDestination int bit,
            @ReportEventRequest.ReportingDestination int bitSet) {
        return (bit & bitSet) != 0;
    }
}
