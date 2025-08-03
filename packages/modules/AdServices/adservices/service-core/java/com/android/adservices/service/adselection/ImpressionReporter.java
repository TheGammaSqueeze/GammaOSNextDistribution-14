/*
 * Copyright (C) 2022 The Android Open Source Project
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
import static android.adservices.common.AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
import static android.adservices.common.AdServicesStatusUtils.STATUS_INVALID_ARGUMENT;
import static android.adservices.common.AdServicesStatusUtils.STATUS_IO_ERROR;
import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION;

import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.ReportEventRequest;
import android.adservices.adselection.ReportImpressionCallback;
import android.adservices.adselection.ReportImpressionInput;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.FledgeErrorResponse;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.RemoteException;
import android.os.Trace;
import android.util.Pair;

import androidx.annotation.RequiresApi;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.adselection.CustomAudienceSignals;
import com.android.adservices.data.adselection.DBRegisteredAdInteraction;
import com.android.adservices.data.adselection.datahandlers.ReportingComputationData;
import com.android.adservices.data.adselection.datahandlers.ReportingData;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.AdSelectionServiceFilter;
import com.android.adservices.service.common.AdTechUriValidator;
import com.android.adservices.service.common.BinderFlagReader;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.common.FrequencyCapAdDataValidator;
import com.android.adservices.service.common.RetryStrategy;
import com.android.adservices.service.common.Throttler;
import com.android.adservices.service.common.ValidatorUtil;
import com.android.adservices.service.common.httpclient.AdServicesHttpClientRequest;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.AdSelectionDevOverridesHelper;
import com.android.adservices.service.devapi.CustomAudienceDevOverridesHelper;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.exception.FilterException;
import com.android.adservices.service.profiling.Tracing;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.internal.util.Preconditions;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import org.json.JSONException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** Encapsulates the Impression Reporting logic */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public class ImpressionReporter {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    public static final String UNABLE_TO_FIND_AD_SELECTION_WITH_GIVEN_ID =
            "Unable to find ad selection with given ID";
    public static final String CALLER_PACKAGE_NAME_MISMATCH =
            "Caller package name does not match name used in ad selection";

    private static final String REPORTING_URI_FIELD_NAME = "reporting URI";
    private static final String EVENT_URI_FIELD_NAME = "event URI";

    @NonNull private final AdSelectionEntryDao mAdSelectionEntryDao;
    @NonNull private final CustomAudienceDao mCustomAudienceDao;
    @NonNull private final AdServicesHttpsClient mAdServicesHttpsClient;
    @NonNull private final ListeningExecutorService mLightweightExecutorService;
    @NonNull private final ListeningExecutorService mBackgroundExecutorService;
    @NonNull private final ScheduledThreadPoolExecutor mScheduledExecutor;
    @NonNull private final ReportImpressionScriptEngine mJsEngine;
    @NonNull private final AdSelectionDevOverridesHelper mAdSelectionDevOverridesHelper;
    @NonNull private final CustomAudienceDevOverridesHelper mCustomAudienceDevOverridesHelper;
    @NonNull private final AdServicesLogger mAdServicesLogger;
    @NonNull private final Flags mFlags;
    @NonNull private final RegisterAdBeaconSupportHelper mRegisterAdBeaconSupportHelper;
    @NonNull private final AdSelectionServiceFilter mAdSelectionServiceFilter;
    @NonNull private final JsFetcher mJsFetcher;
    @NonNull private final PrebuiltLogicGenerator mPrebuiltLogicGenerator;
    @NonNull private final FledgeAuthorizationFilter mFledgeAuthorizationFilter;
    @NonNull private final FrequencyCapAdDataValidator mFrequencyCapAdDataValidator;
    @NonNull private final DevContext mDevContext;
    private int mCallerUid;
    @NonNull private String mCallerAppPackageName;

    public ImpressionReporter(
            @NonNull Context context,
            @NonNull ExecutorService lightweightExecutor,
            @NonNull ExecutorService backgroundExecutor,
            @NonNull ScheduledThreadPoolExecutor scheduledExecutor,
            @NonNull AdSelectionEntryDao adSelectionEntryDao,
            @NonNull CustomAudienceDao customAudienceDao,
            @NonNull AdServicesHttpsClient adServicesHttpsClient,
            @NonNull DevContext devContext,
            @NonNull AdServicesLogger adServicesLogger,
            @NonNull final Flags flags,
            @NonNull final AdSelectionServiceFilter adSelectionServiceFilter,
            @NonNull final FledgeAuthorizationFilter fledgeAuthorizationFilter,
            @NonNull final FrequencyCapAdDataValidator frequencyCapAdDataValidator,
            final int callerUid,
            @NonNull final RetryStrategy retryStrategy) {
        Objects.requireNonNull(context);
        Objects.requireNonNull(lightweightExecutor);
        Objects.requireNonNull(backgroundExecutor);
        Objects.requireNonNull(scheduledExecutor);
        Objects.requireNonNull(adSelectionEntryDao);
        Objects.requireNonNull(customAudienceDao);
        Objects.requireNonNull(adServicesHttpsClient);
        Objects.requireNonNull(devContext);
        Objects.requireNonNull(adServicesLogger);
        Objects.requireNonNull(flags);
        Objects.requireNonNull(adSelectionServiceFilter);
        Objects.requireNonNull(frequencyCapAdDataValidator);
        Objects.requireNonNull(devContext);
        Objects.requireNonNull(retryStrategy);

        mLightweightExecutorService = MoreExecutors.listeningDecorator(lightweightExecutor);
        mBackgroundExecutorService = MoreExecutors.listeningDecorator(backgroundExecutor);
        mScheduledExecutor = scheduledExecutor;
        mAdSelectionEntryDao = adSelectionEntryDao;
        mCustomAudienceDao = customAudienceDao;
        mAdServicesHttpsClient = adServicesHttpsClient;
        mDevContext = devContext;
        boolean isRegisterAdBeaconEnabled =
                BinderFlagReader.readFlag(flags::getFledgeRegisterAdBeaconEnabled);

        ReportImpressionScriptEngine.RegisterAdBeaconScriptEngineHelper
                registerAdBeaconScriptEngineHelper;
        if (isRegisterAdBeaconEnabled) {
            mRegisterAdBeaconSupportHelper = new RegisterAdBeaconSupportHelperEnabled();
            long maxInteractionReportingUrisSize =
                    BinderFlagReader.readFlag(
                            flags::getFledgeReportImpressionMaxRegisteredAdBeaconsPerAdTechCount);
            registerAdBeaconScriptEngineHelper =
                    new ReportImpressionScriptEngine.RegisterAdBeaconScriptEngineHelperEnabled(
                            maxInteractionReportingUrisSize);
        } else {
            mRegisterAdBeaconSupportHelper = new RegisterAdBeaconSupportHelperDisabled();
            registerAdBeaconScriptEngineHelper =
                    new ReportImpressionScriptEngine.RegisterAdBeaconScriptEngineHelperDisabled();
        }
        mJsEngine =
                new ReportImpressionScriptEngine(
                        context,
                        () -> flags.getEnforceIsolateMaxHeapSize(),
                        () -> flags.getIsolateMaxHeapSizeBytes(),
                        registerAdBeaconScriptEngineHelper,
                        retryStrategy);

        mAdSelectionDevOverridesHelper =
                new AdSelectionDevOverridesHelper(devContext, mAdSelectionEntryDao);
        mCustomAudienceDevOverridesHelper =
                new CustomAudienceDevOverridesHelper(devContext, mCustomAudienceDao);
        mAdServicesLogger = adServicesLogger;
        mFlags = flags;
        mAdSelectionServiceFilter = adSelectionServiceFilter;
        mFrequencyCapAdDataValidator = frequencyCapAdDataValidator;
        mCallerUid = callerUid;
        mJsFetcher =
                new JsFetcher(
                        mBackgroundExecutorService,
                        mLightweightExecutorService,
                        mAdServicesHttpsClient,
                        mFlags,
                        mDevContext);
        mPrebuiltLogicGenerator = new PrebuiltLogicGenerator(mFlags);
        mFledgeAuthorizationFilter = fledgeAuthorizationFilter;
    }

    /**
     * Run the impression report logic asynchronously. Invoked seller's reportResult() as well as
     * the buyer's reportWin() in the case of a remarketing ad.
     *
     * <p>After invoking the javascript functions, invokes the onSuccess function of the callback
     * and reports URIs resulting from the javascript functions.
     *
     * @param requestParams request parameters containing the {@code adSelectionId}, {@code
     *     adSelectionConfig}, and {@code callerPackageName}
     * @param callback callback function to be called in case of success or failure
     */
    public void reportImpression(
            @NonNull ReportImpressionInput requestParams,
            @NonNull ReportImpressionCallback callback) {
        sLogger.v("Executing reportImpression API");
        mCallerAppPackageName = requestParams.getCallerPackageName();
        long adSelectionId = requestParams.getAdSelectionId();
        long timeoutMs = BinderFlagReader.readFlag(mFlags::getReportImpressionOverallTimeoutMs);
        AdSelectionConfig adSelectionConfig = requestParams.getAdSelectionConfig();
        ListenableFuture<Void> filterAndValidateRequestFuture =
                Futures.submit(
                        () -> {
                            try {
                                Trace.beginSection(Tracing.VALIDATE_REQUEST);
                                sLogger.v("Starting filtering and validation.");
                                mAdSelectionServiceFilter.filterRequest(
                                        null,
                                        requestParams.getCallerPackageName(),
                                        mFlags
                                                .getEnforceForegroundStatusForFledgeReportImpression(),
                                        true,
                                        mCallerUid,
                                        AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION,
                                        Throttler.ApiKey.FLEDGE_API_REPORT_IMPRESSIONS,
                                        mDevContext);
                                if (!adSelectionConfig.equals(AdSelectionConfig.EMPTY)) {
                                    validateAdSelectionConfig(adSelectionConfig);
                                }
                                Preconditions.checkArgument(
                                        mAdSelectionEntryDao
                                                .doesAdSelectionIdAndCallerPackageNameExists(
                                                        adSelectionId,
                                                        requestParams.getCallerPackageName()),
                                        CALLER_PACKAGE_NAME_MISMATCH);
                            } finally {
                                sLogger.v("Completed filtering and validation.");
                                Trace.endSection();
                            }
                        },
                        mLightweightExecutorService);

        FluentFuture.from(filterAndValidateRequestFuture)
                .transformAsync(
                        ignored -> fetchReportingData(adSelectionId), mLightweightExecutorService)
                .transformAsync(
                        reportingData -> {
                            if (isReportingUrisPresent(reportingData)) {
                                return Futures.immediateFuture(
                                        new ReportingUris(
                                                reportingData.getBuyerWinReportingUri(),
                                                reportingData.getSellerWinReportingUri()));
                            } else {
                                // TODO(b/291957376): Move computation into selectAds in phase 2 of
                                //  go/rb-rm-unified-flow-reporting
                                return computeReportingUris(
                                                adSelectionId, reportingData, adSelectionConfig)
                                        .transform(
                                                this::validateReportingUris,
                                                mLightweightExecutorService);
                            }
                        },
                        mLightweightExecutorService)
                .withTimeout(
                        timeoutMs,
                        TimeUnit.MILLISECONDS,
                        // TODO(b/237103033): Comply with thread usage policy for AdServices;
                        //  use a global scheduled executor
                        mScheduledExecutor)
                .addCallback(
                        new FutureCallback<>() {
                            @Override
                            public void onSuccess(ReportingUris result) {
                                sLogger.v("Notify success for caller");
                                notifySuccessToCaller(callback);
                                sLogger.d("Perform reporting!");
                                performReporting(result);
                            }

                            @Override
                            public void onFailure(Throwable t) {
                                sLogger.e(t, "Report Impression invocation failed!");
                                if (t instanceof FilterException
                                        && t.getCause()
                                                instanceof ConsentManager.RevokedConsentException) {
                                    // Skip logging if a FilterException occurs.
                                    // AdSelectionServiceFilter ensures the failing assertion is
                                    // logged internally.

                                    // Fail Silently by notifying success to caller
                                    notifySuccessToCaller(callback);
                                } else {
                                    notifyFailureToCaller(callback, t);
                                }
                            }
                        },
                        mLightweightExecutorService);
    }

    private FluentFuture<ReportingData> fetchReportingData(long adSelectionId) {
        sLogger.v("Fetching ad selection entry ID %d", adSelectionId);
        return FluentFuture.from(
                mBackgroundExecutorService.submit(
                        () -> {
                            ReportingData reportingData =
                                    mAdSelectionEntryDao.getReportingDataForId(adSelectionId);
                            Preconditions.checkArgument(
                                    !Objects.isNull(reportingData),
                                    UNABLE_TO_FIND_AD_SELECTION_WITH_GIVEN_ID);
                            return reportingData;
                        }));
    }

    /**
     * If reporting URIs exists then computation can be skipped.
     *
     * @return true if at least on of the URIs exist otherwise false.
     */
    private boolean isReportingUrisPresent(ReportingData reportingData) {
        return !Objects.isNull(reportingData.getBuyerWinReportingUri())
                || !Objects.isNull(reportingData.getSellerWinReportingUri());
    }

    private FluentFuture<Pair<ReportingUris, ReportingContext>> computeReportingUris(
            long adSelectionId, ReportingData reportingData, AdSelectionConfig adSelectionConfig) {
        sLogger.v("Computing reporting uris");
        ReportingComputationData computationData = reportingData.getReportingComputationData();
        sLogger.v("DecisionLogicJs from db entry: " + computationData.getBuyerDecisionLogicJs());
        sLogger.v(
                "DecisionLogicUri from db entry: "
                        + computationData.getBuyerDecisionLogicUri().toString());
        ReportingContext ctx = new ReportingContext();
        ctx.mAdSelectionId = adSelectionId;
        ctx.mComputationData = computationData;
        ctx.mAdSelectionConfig = adSelectionConfig;
        return fetchSellerDecisionLogic(ctx)
                .transformAsync(
                        decisionLogicJsAndCtx ->
                                invokeSellerScript(
                                        decisionLogicJsAndCtx.first, decisionLogicJsAndCtx.second),
                        mLightweightExecutorService)
                .transformAsync(
                        sellerResultAndCtx ->
                                mRegisterAdBeaconSupportHelper.commitSellerRegisteredEvents(
                                        sellerResultAndCtx.first, sellerResultAndCtx.second),
                        mLightweightExecutorService)
                .transformAsync(
                        sellerResultAndCtx ->
                                invokeBuyerScript(
                                        sellerResultAndCtx.first, sellerResultAndCtx.second),
                        mLightweightExecutorService)
                .transformAsync(
                        reportingResultsAndCtx ->
                                mRegisterAdBeaconSupportHelper.commitBuyerRegisteredEvents(
                                        reportingResultsAndCtx.first,
                                        reportingResultsAndCtx.second),
                        mLightweightExecutorService);
    }

    /** Validates seller and buyer URLs and removes them if invalid */
    private ReportingUris validateReportingUris(
            Pair<ImpressionReporter.ReportingUris, ImpressionReporter.ReportingContext>
                    reportingUriWithContext) {
        sLogger.v("Validating reporting URIs");
        ReportingUris reportingUris = reportingUriWithContext.first;
        AdTechIdentifier buyer =
                reportingUriWithContext
                        .second
                        .mComputationData
                        .getWinningCustomAudienceSignals()
                        .getBuyer();
        AdTechIdentifier seller = reportingUriWithContext.second.mAdSelectionConfig.getSeller();

        // Validate seller uri before reporting
        try {
            AdTechUriValidator sellerValidator =
                    new AdTechUriValidator(
                            ValidatorUtil.AD_TECH_ROLE_SELLER,
                            seller.toString(),
                            this.getClass().getSimpleName(),
                            REPORTING_URI_FIELD_NAME);
            sellerValidator.validate(reportingUris.sellerReportingUri);
        } catch (IllegalArgumentException e) {
            sLogger.v("Seller reporting URI is not valid");
            reportingUris.sellerReportingUri = Uri.EMPTY;
        }

        // Validate buyer uri before reporting
        try {
            AdTechUriValidator buyerValidator =
                    new AdTechUriValidator(
                            ValidatorUtil.AD_TECH_ROLE_BUYER,
                            buyer.toString(),
                            this.getClass().getSimpleName(),
                            REPORTING_URI_FIELD_NAME);
            buyerValidator.validate(reportingUris.buyerReportingUri);
        } catch (IllegalArgumentException e) {
            sLogger.v("Buyer reporting URI is not valid");
            reportingUris.buyerReportingUri = Uri.EMPTY;
        }
        return reportingUris;
    }

    private void performReporting(ReportingUris reportingUris) {
        doReport(reportingUris)
                .addCallback(
                        new FutureCallback<>() {
                            @Override
                            public void onSuccess(List<Void> result) {
                                sLogger.d("Reporting finished successfully!");
                                mAdServicesLogger.logFledgeApiCallStats(
                                        AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION,
                                        mCallerAppPackageName,
                                        STATUS_SUCCESS,
                                        /*latencyMs=*/ 0);
                            }

                            @Override
                            public void onFailure(Throwable t) {
                                sLogger.e(
                                        t,
                                        "Report Impression failure encountered during reporting!");
                                if (t instanceof IOException) {
                                    mAdServicesLogger.logFledgeApiCallStats(
                                            AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION,
                                            mCallerAppPackageName,
                                            STATUS_IO_ERROR,
                                            /*latencyMs=*/ 0);
                                }
                                mAdServicesLogger.logFledgeApiCallStats(
                                        AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION,
                                        mCallerAppPackageName,
                                        STATUS_INTERNAL_ERROR,
                                        /*latencyMs=*/ 0);
                            }
                        },
                        mLightweightExecutorService);
    }

    @NonNull
    private FluentFuture<List<Void>> doReport(ReportingUris reportingUris) {
        sLogger.v("Do report.");
        Uri sellerReportingUri = reportingUris.sellerReportingUri;
        Uri buyerReportingUri = reportingUris.buyerReportingUri;

        // We don't need to verify enrollment since that is done during request filtering
        // Perform reporting if no exception was thrown
        ListenableFuture<Void> sellerFuture = bestEffortReporting(sellerReportingUri);

        ListenableFuture<Void> buyerFuture;
        try {
            if (!mFlags.getDisableFledgeEnrollmentCheck()) {
                mFledgeAuthorizationFilter.assertAdTechEnrolled(
                        AdTechIdentifier.fromString(buyerReportingUri.getHost()),
                        AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION);
            }
            buyerFuture = bestEffortReporting(buyerReportingUri);
        } catch (FledgeAuthorizationFilter.AdTechNotAllowedException e) {
            buyerFuture = Futures.immediateFuture(null);
        }

        return FluentFuture.from(Futures.allAsList(sellerFuture, buyerFuture));
    }

    private ListenableFuture<Void> bestEffortReporting(Uri reportingUri) {
        sLogger.v("Best effort reporting for: '%s'", reportingUri);
        return FluentFuture.from(
                        mAdServicesHttpsClient.getAndReadNothing(reportingUri, mDevContext))
                .catching(
                        Exception.class,
                        e -> {
                            sLogger.d(e, "GET failed for reporting URL '%s'!", reportingUri);
                            return null;
                        },
                        mLightweightExecutorService);
    }

    private FluentFuture<Pair<String, ReportingContext>> fetchSellerDecisionLogic(
            ReportingContext ctx) {
        sLogger.v("Fetching seller reporting script");
        AdServicesHttpClientRequest request =
                AdServicesHttpClientRequest.builder()
                        .setUri(ctx.mAdSelectionConfig.getDecisionLogicUri())
                        .setUseCache(mFlags.getFledgeHttpJsCachingEnabled())
                        .setDevContext(mDevContext)
                        .build();

        return mJsFetcher
                .getSellerReportingLogic(
                        request, mAdSelectionDevOverridesHelper, ctx.mAdSelectionConfig)
                .transform(
                        stringResult -> {
                            sLogger.v(
                                    "Seller script from uri: %s: %s",
                                    request.getUri(), stringResult);
                            return Pair.create(stringResult, ctx);
                        },
                        mLightweightExecutorService);
    }

    private FluentFuture<String> fetchBuyerDecisionLogic(
            ReportingContext ctx, CustomAudienceSignals customAudienceSignals) {
        if (!ctx.mComputationData.getBuyerDecisionLogicJs().isEmpty()) {
            sLogger.v(
                    "Buyer decision logic fetched during ad selection. No need to fetch it again.");
            return FluentFuture.from(
                    Futures.immediateFuture(ctx.mComputationData.getBuyerDecisionLogicJs()));
        }
        sLogger.v("Fetching buyer script");
        AdServicesHttpClientRequest request =
                AdServicesHttpClientRequest.builder()
                        .setUri(ctx.mComputationData.getBuyerDecisionLogicUri())
                        .setUseCache(mFlags.getFledgeHttpJsCachingEnabled())
                        .setDevContext(mDevContext)
                        .build();

        return mJsFetcher.getBuyerReportingLogic(
                request,
                mCustomAudienceDevOverridesHelper,
                customAudienceSignals.getOwner(),
                customAudienceSignals.getBuyer(),
                customAudienceSignals.getName());
    }

    private FluentFuture<Pair<ReportImpressionScriptEngine.SellerReportingResult, ReportingContext>>
            invokeSellerScript(String decisionLogicJs, ReportingContext ctx) {
        sLogger.v("Invoking seller script");
        try {
            return FluentFuture.from(
                            mJsEngine.reportResult(
                                    decisionLogicJs,
                                    ctx.mAdSelectionConfig,
                                    ctx.mComputationData.getWinningRenderUri(),
                                    ctx.mComputationData.getWinningBid(),
                                    ctx.mComputationData.getSellerContextualSignals()))
                    .transform(
                            sellerResult -> Pair.create(sellerResult, ctx),
                            mLightweightExecutorService);
        } catch (JSONException e) {
            throw new IllegalArgumentException("Invalid JSON data", e);
        }
    }

    private FluentFuture<Pair<ReportingResults, ReportingContext>> invokeBuyerScript(
            ReportImpressionScriptEngine.SellerReportingResult sellerReportingResult,
            ReportingContext ctx) {
        sLogger.v("Invoking buyer script");
        sLogger.v("buyer JS: " + ctx.mComputationData.getBuyerDecisionLogicJs());
        sLogger.v("Buyer JS Uri: " + ctx.mComputationData.getBuyerDecisionLogicUri());

        final CustomAudienceSignals customAudienceSignals =
                Objects.requireNonNull(ctx.mComputationData.getWinningCustomAudienceSignals());

        AdSelectionSignals signals =
                Optional.ofNullable(
                                ctx.mAdSelectionConfig
                                        .getPerBuyerSignals()
                                        .get(customAudienceSignals.getBuyer()))
                        .orElse(AdSelectionSignals.EMPTY);

        try {
            // TODO(b/233239475) : Validate Buyer signals in Ad Selection Config
            return FluentFuture.from(
                            mJsEngine.reportWin(
                                    fetchBuyerDecisionLogic(ctx, customAudienceSignals).get(),
                                    ctx.mAdSelectionConfig.getAdSelectionSignals(),
                                    signals,
                                    sellerReportingResult.getSignalsForBuyer(),
                                    ctx.mComputationData.getBuyerContextualSignals(),
                                    customAudienceSignals))
                    .transform(
                            buyerReportingResult ->
                                    Pair.create(
                                            new ReportingResults(
                                                    buyerReportingResult, sellerReportingResult),
                                            ctx),
                            mLightweightExecutorService);
        } catch (JSONException e) {
            throw new IllegalArgumentException("Invalid JSON args", e);
        } catch (InterruptedException | ExecutionException e) {
            throw new IllegalStateException(
                    "Error while fetching buyer script from uri: "
                            + ctx.mComputationData.getBuyerDecisionLogicUri());
        }
    }

    /**
     * Validates the {@code adSelectionConfig} from the request.
     *
     * @param adSelectionConfig the adSelectionConfig to be validated
     * @throws IllegalArgumentException if the provided {@code adSelectionConfig} is not valid
     */
    private void validateAdSelectionConfig(AdSelectionConfig adSelectionConfig)
            throws IllegalArgumentException {
        AdSelectionConfigValidator adSelectionConfigValidator =
                new AdSelectionConfigValidator(
                        mPrebuiltLogicGenerator, mFrequencyCapAdDataValidator);
        adSelectionConfigValidator.validate(adSelectionConfig);
    }

    /** Invokes the onSuccess function from the callback and handles the exception. */
    private void notifySuccessToCaller(ReportImpressionCallback callback) {
        try {
            callback.onSuccess();
        } catch (RemoteException e) {
            sLogger.e(e, "Unable to send successful result to the callback");
            throw e.rethrowFromSystemServer();
        }
    }

    /** Invokes the onFailure function from the callback and handles the exception. */
    private void notifyFailureToCaller(
            @NonNull ReportImpressionCallback callback, @NonNull Throwable t) {
        int resultCode;

        boolean isFilterException = t instanceof FilterException;

        if (isFilterException) {
            resultCode = FilterException.getResultCode(t);
        } else if (t instanceof IllegalArgumentException) {
            resultCode = STATUS_INVALID_ARGUMENT;
        } else {
            resultCode = STATUS_INTERNAL_ERROR;
        }

        // Skip logging if a FilterException occurs.
        // AdSelectionServiceFilter ensures the failing assertion is logged internally.
        // Note: Failure is logged before the callback to ensure deterministic testing.
        if (!isFilterException) {
            mAdServicesLogger.logFledgeApiCallStats(
                    AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION,
                    mCallerAppPackageName,
                    resultCode,
                    /*latencyMs=*/ 0);
        }

        try {
            callback.onFailure(
                    new FledgeErrorResponse.Builder()
                            .setStatusCode(resultCode)
                            .setErrorMessage(t.getMessage())
                            .build());
        } catch (RemoteException e) {
            sLogger.e(e, "Unable to send failed result to the callback");
            throw e.rethrowFromSystemServer();
        }
    }

    private static class ReportingContext {
        long mAdSelectionId;
        @NonNull AdSelectionConfig mAdSelectionConfig;
        @NonNull ReportingComputationData mComputationData;
    }

    private static final class ReportingUris {
        @Nullable public Uri buyerReportingUri;
        @NonNull public Uri sellerReportingUri;

        private ReportingUris(@Nullable Uri buyerReportingUri, @NonNull Uri sellerReportingUri) {
            /* buyer could be empty in case of contextual ad */
            Objects.requireNonNull(sellerReportingUri);

            this.buyerReportingUri = buyerReportingUri;
            this.sellerReportingUri = sellerReportingUri;
        }
    }

    private static final class ReportingResults {
        @Nullable
        public final ReportImpressionScriptEngine.BuyerReportingResult mBuyerReportingResult;

        @NonNull
        public final ReportImpressionScriptEngine.SellerReportingResult mSellerReportingResult;

        private ReportingResults(
                @Nullable ReportImpressionScriptEngine.BuyerReportingResult buyerReportingResult,
                @NonNull ReportImpressionScriptEngine.SellerReportingResult sellerReportingResult) {
            Objects.requireNonNull(sellerReportingResult);

            mBuyerReportingResult = buyerReportingResult;
            mSellerReportingResult = sellerReportingResult;
        }
    }

    private interface RegisterAdBeaconSupportHelper {
        FluentFuture<Pair<ReportImpressionScriptEngine.SellerReportingResult, ReportingContext>>
                commitSellerRegisteredEvents(
                        ReportImpressionScriptEngine.SellerReportingResult sellerReportingResult,
                        ReportingContext ctx);

        FluentFuture<Pair<ReportingUris, ReportingContext>> commitBuyerRegisteredEvents(
                ReportingResults reportingResults, ReportingContext ctx);
    }

    private class RegisterAdBeaconSupportHelperEnabled implements RegisterAdBeaconSupportHelper {

        @Override
        public FluentFuture<
                        Pair<ReportImpressionScriptEngine.SellerReportingResult, ReportingContext>>
                commitSellerRegisteredEvents(
                        ReportImpressionScriptEngine.SellerReportingResult sellerReportingResult,
                        ReportingContext ctx) {
            // Validate seller uri before reporting
            AdTechUriValidator sellerValidator =
                    new AdTechUriValidator(
                            ValidatorUtil.AD_TECH_ROLE_SELLER,
                            ctx.mAdSelectionConfig.getSeller().toString(),
                            this.getClass().getSimpleName(),
                            EVENT_URI_FIELD_NAME);

            return FluentFuture.from(
                    mBackgroundExecutorService.submit(
                            () -> {
                                commitRegisteredAdInteractionsToDatabase(
                                        sellerReportingResult.getInteractionReportingUris(),
                                        sellerValidator,
                                        ctx.mAdSelectionId,
                                        FLAG_REPORTING_DESTINATION_SELLER);
                                return Pair.create(sellerReportingResult, ctx);
                            }));
        }

        @Override
        public FluentFuture<Pair<ReportingUris, ReportingContext>> commitBuyerRegisteredEvents(
                ReportingResults reportingResults, ReportingContext ctx) {
            if (Objects.isNull(reportingResults.mBuyerReportingResult)) {
                return FluentFuture.from(
                        Futures.immediateFuture(
                                Pair.create(
                                        new ReportingUris(
                                                null,
                                                reportingResults.mSellerReportingResult
                                                        .getReportingUri()),
                                        ctx)));
            }

            CustomAudienceSignals customAudienceSignals =
                    Objects.requireNonNull(ctx.mComputationData.getWinningCustomAudienceSignals());

            AdTechUriValidator buyerValidator =
                    new AdTechUriValidator(
                            ValidatorUtil.AD_TECH_ROLE_BUYER,
                            customAudienceSignals.getBuyer().toString(),
                            this.getClass().getSimpleName(),
                            REPORTING_URI_FIELD_NAME);

            return FluentFuture.from(
                    mBackgroundExecutorService.submit(
                            () -> {
                                commitRegisteredAdInteractionsToDatabase(
                                        reportingResults.mBuyerReportingResult
                                                .getInteractionReportingUris(),
                                        buyerValidator,
                                        ctx.mAdSelectionId,
                                        FLAG_REPORTING_DESTINATION_BUYER);
                                return Pair.create(
                                        new ReportingUris(
                                                reportingResults.mBuyerReportingResult
                                                        .getReportingUri(),
                                                reportingResults.mSellerReportingResult
                                                        .getReportingUri()),
                                        ctx);
                            }));
        }

        /**
         * Iterates through each {@link InteractionUriRegistrationInfo}, validates each {@link
         * InteractionUriRegistrationInfo#getInteractionReportingUri()}, and commits it to the
         * {@code registered_ad_interactions} table if it's valid.
         *
         * <p>Note: For system health purposes, we will enforce these limitations: 1. We only commit
         * up to a maximum of {@link
         * ImpressionReporter#mFlags#getReportImpressionMaxRegisteredAdBeaconsTotalCount()} entries
         * to the database. 2. We will not commit an entry to the database if {@link
         * InteractionUriRegistrationInfo#getInteractionKey()} is larger than {@link
         * ImpressionReporter#mFlags#getFledgeReportImpressionRegisteredAdBeaconsMaxInteractionKeySize()}
         * or if {@link InteractionUriRegistrationInfo#getInteractionReportingUri()} is larger than
         * {@link
         * ImpressionReporter#mFlags#getFledgeReportImpressionMaxInteractionReportingUriSizeB()}
         */
        private void commitRegisteredAdInteractionsToDatabase(
                @NonNull List<InteractionUriRegistrationInfo> interactionUriRegistrationInfos,
                @NonNull AdTechUriValidator validator,
                long adSelectionId,
                @ReportEventRequest.ReportingDestination int reportingDestination) {

            long maxTableSize = mFlags.getFledgeReportImpressionMaxRegisteredAdBeaconsTotalCount();
            long maxInteractionKeySize =
                    mFlags.getFledgeReportImpressionRegisteredAdBeaconsMaxInteractionKeySizeB();
            long maxInteractionReportingUriSize =
                    mFlags.getFledgeReportImpressionMaxInteractionReportingUriSizeB();
            long maxNumRowsPerDestination =
                    mFlags.getFledgeReportImpressionMaxRegisteredAdBeaconsPerAdTechCount();

            List<DBRegisteredAdInteraction> adInteractionsToRegister = new ArrayList<>();

            for (InteractionUriRegistrationInfo uriRegistrationInfo :
                    interactionUriRegistrationInfos) {
                if (uriRegistrationInfo.getInteractionKey().getBytes(StandardCharsets.UTF_8).length
                        > maxInteractionKeySize) {
                    sLogger.v(
                            "InteractionKey size exceeds the maximum allowed! Skipping this entry");
                    continue;
                }

                if (uriRegistrationInfo
                                .getInteractionReportingUri()
                                .toString()
                                .getBytes(StandardCharsets.UTF_8)
                                .length
                        > maxInteractionReportingUriSize) {
                    sLogger.v(
                            "Interaction reporting uri size exceeds the maximum allowed! Skipping"
                                    + " this entry");
                    continue;
                }

                Uri uriToValidate = uriRegistrationInfo.getInteractionReportingUri();
                try {
                    validator.validate(uriToValidate);
                    DBRegisteredAdInteraction dbRegisteredAdInteraction =
                            DBRegisteredAdInteraction.builder()
                                    .setAdSelectionId(adSelectionId)
                                    .setInteractionKey(uriRegistrationInfo.getInteractionKey())
                                    .setInteractionReportingUri(uriToValidate)
                                    .setDestination(reportingDestination)
                                    .build();
                    adInteractionsToRegister.add(dbRegisteredAdInteraction);
                } catch (IllegalArgumentException e) {
                    sLogger.v(
                            "Uri %s failed validation! Skipping persistence of this interaction URI"
                                    + " pair.",
                            uriToValidate);
                }
            }
            mAdSelectionEntryDao.safelyInsertRegisteredAdInteractions(
                    adSelectionId,
                    adInteractionsToRegister,
                    maxTableSize,
                    maxNumRowsPerDestination,
                    reportingDestination);
        }
    }

    private class RegisterAdBeaconSupportHelperDisabled implements RegisterAdBeaconSupportHelper {

        @Override
        public FluentFuture<
                        Pair<ReportImpressionScriptEngine.SellerReportingResult, ReportingContext>>
                commitSellerRegisteredEvents(
                        ReportImpressionScriptEngine.SellerReportingResult sellerReportingResult,
                        ReportingContext ctx) {
            // Return immediately since registerAdBeacon is disabled
            return FluentFuture.from(
                    Futures.immediateFuture(Pair.create(sellerReportingResult, ctx)));
        }

        @Override
        public FluentFuture<Pair<ReportingUris, ReportingContext>> commitBuyerRegisteredEvents(
                ReportingResults reportingResults, ReportingContext ctx) {
            if (Objects.isNull(reportingResults.mBuyerReportingResult)) {
                return FluentFuture.from(
                        Futures.immediateFuture(
                                Pair.create(
                                        new ReportingUris(
                                                null,
                                                reportingResults.mSellerReportingResult
                                                        .getReportingUri()),
                                        ctx)));
            }

            // Return immediately since registerAdBeacon is disabled
            return FluentFuture.from(
                    Futures.immediateFuture(
                            Pair.create(
                                    new ReportingUris(
                                            reportingResults.mBuyerReportingResult
                                                    .getReportingUri(),
                                            reportingResults.mSellerReportingResult
                                                    .getReportingUri()),
                                    ctx)));
        }
    }
}
