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

import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN;

import android.adservices.adselection.AdSelectionCallback;
import android.adservices.adselection.AdSelectionFromOutcomesConfig;
import android.adservices.adselection.AdSelectionFromOutcomesInput;
import android.adservices.adselection.AdSelectionOutcome;
import android.adservices.adselection.AdSelectionResponse;
import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.FledgeErrorResponse;
import android.adservices.exceptions.AdServicesException;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.os.Build;
import android.os.RemoteException;
import android.os.Trace;
import android.util.Pair;

import androidx.annotation.RequiresApi;

import com.android.adservices.LoggerFactory;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.adselection.datahandlers.AdSelectionResultBidAndUri;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.AdSelectionServiceFilter;
import com.android.adservices.service.common.BinderFlagReader;
import com.android.adservices.service.common.RetryStrategy;
import com.android.adservices.service.common.Throttler;
import com.android.adservices.service.common.cache.CacheProviderFactory;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.AdSelectionDevOverridesHelper;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.exception.FilterException;
import com.android.adservices.service.profiling.Tracing;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerUtil;
import com.android.adservices.service.stats.AdServicesStatsLog;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.UncheckedTimeoutException;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Orchestrator that runs the logic retrieved on a list of outcomes and signals.
 *
 * <p>Class takes in an executor on which it runs the OutcomeSelection logic
 */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public class OutcomeSelectionRunner {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    @VisibleForTesting static final String AD_SELECTION_FROM_OUTCOMES_ERROR_PATTERN = "%s: %s";

    @VisibleForTesting
    static final String ERROR_AD_SELECTION_FROM_OUTCOMES_FAILURE =
            "Encountered failure during Ad Selection";

    @VisibleForTesting
    static final String SELECTED_OUTCOME_MUST_BE_ONE_OF_THE_INPUTS =
            "Outcome selection must return a valid ad selection id";

    @VisibleForTesting
    static final String AD_SELECTION_TIMED_OUT = "Ad selection exceeded allowed time limit";

    @NonNull private final AdSelectionEntryDao mAdSelectionEntryDao;
    @NonNull private final ListeningExecutorService mBackgroundExecutorService;
    @NonNull private final ListeningExecutorService mLightweightExecutorService;
    @NonNull private final ScheduledThreadPoolExecutor mScheduledExecutor;
    @NonNull private final AdServicesHttpsClient mAdServicesHttpsClient;
    @NonNull private final AdServicesLogger mAdServicesLogger;
    @NonNull private final Context mContext;
    @NonNull private final Flags mFlags;

    @NonNull private final AdOutcomeSelector mAdOutcomeSelector;
    @NonNull private final AdSelectionServiceFilter mAdSelectionServiceFilter;
    private final int mCallerUid;
    @NonNull private final PrebuiltLogicGenerator mPrebuiltLogicGenerator;
    @NonNull private final DevContext mDevContext;
    private final boolean mShouldUseUnifiedTables;

    /**
     * @param adSelectionEntryDao DAO to access ad selection storage
     * @param backgroundExecutorService executor for longer running tasks (ex. network calls)
     * @param lightweightExecutorService executor for running short tasks
     * @param scheduledExecutor executor for tasks to be run with a delay or timed executions
     * @param adServicesHttpsClient HTTPS client to use when fetch JS logics
     * @param adServicesLogger logger for logging calls to PPAPI
     * @param context service context
     * @param flags for accessing feature flags
     * @param adSelectionServiceFilter to validate the request
     */
    public OutcomeSelectionRunner(
            @NonNull final AdSelectionEntryDao adSelectionEntryDao,
            @NonNull final ExecutorService backgroundExecutorService,
            @NonNull final ExecutorService lightweightExecutorService,
            @NonNull final ScheduledThreadPoolExecutor scheduledExecutor,
            @NonNull final AdServicesHttpsClient adServicesHttpsClient,
            @NonNull final AdServicesLogger adServicesLogger,
            @NonNull final DevContext devContext,
            @NonNull final Context context,
            @NonNull final Flags flags,
            @NonNull final AdSelectionServiceFilter adSelectionServiceFilter,
            @NonNull final AdCounterKeyCopier adCounterKeyCopier,
            final int callerUid,
            boolean shouldUseUnifiedTables,
            @NonNull final RetryStrategy retryStrategy) {
        Objects.requireNonNull(adSelectionEntryDao);
        Objects.requireNonNull(backgroundExecutorService);
        Objects.requireNonNull(lightweightExecutorService);
        Objects.requireNonNull(scheduledExecutor);
        Objects.requireNonNull(adServicesHttpsClient);
        Objects.requireNonNull(adServicesLogger);
        Objects.requireNonNull(devContext);
        Objects.requireNonNull(context);
        Objects.requireNonNull(flags);
        Objects.requireNonNull(adCounterKeyCopier);
        Objects.requireNonNull(retryStrategy);

        mAdSelectionEntryDao = adSelectionEntryDao;
        mBackgroundExecutorService = MoreExecutors.listeningDecorator(backgroundExecutorService);
        mLightweightExecutorService = MoreExecutors.listeningDecorator(lightweightExecutorService);
        mScheduledExecutor = scheduledExecutor;
        mAdServicesHttpsClient = adServicesHttpsClient;
        mAdServicesLogger = adServicesLogger;
        mContext = context;
        mFlags = flags;
        mDevContext = devContext;

        boolean cpcBillingEnabled = BinderFlagReader.readFlag(mFlags::getFledgeCpcBillingEnabled);
        mAdOutcomeSelector =
                new AdOutcomeSelectorImpl(
                        new AdSelectionScriptEngine(
                                mContext,
                                flags::getEnforceIsolateMaxHeapSize,
                                flags::getIsolateMaxHeapSizeBytes,
                                adCounterKeyCopier,
                                new DebugReportingScriptDisabledStrategy(),
                                cpcBillingEnabled,
                                retryStrategy),
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mAdServicesHttpsClient,
                        new AdSelectionDevOverridesHelper(devContext, adSelectionEntryDao),
                        mFlags,
                        mDevContext);
        mAdSelectionServiceFilter = adSelectionServiceFilter;
        mCallerUid = callerUid;
        mPrebuiltLogicGenerator = new PrebuiltLogicGenerator(mFlags);
        mShouldUseUnifiedTables = shouldUseUnifiedTables;
    }

    @VisibleForTesting
    public OutcomeSelectionRunner(
            int callerUid,
            @NonNull final AdOutcomeSelector adOutcomeSelector,
            @NonNull final AdSelectionEntryDao adSelectionEntryDao,
            @NonNull final ExecutorService backgroundExecutorService,
            @NonNull final ExecutorService lightweightExecutorService,
            @NonNull final ScheduledThreadPoolExecutor scheduledExecutor,
            @NonNull final AdServicesLogger adServicesLogger,
            @NonNull final Context context,
            @NonNull final Flags flags,
            @NonNull final AdSelectionServiceFilter adSelectionServiceFilter,
            @NonNull final DevContext devContext,
            boolean shouldUseUnifiedTables) {
        Objects.requireNonNull(adOutcomeSelector);
        Objects.requireNonNull(adSelectionEntryDao);
        Objects.requireNonNull(backgroundExecutorService);
        Objects.requireNonNull(lightweightExecutorService);
        Objects.requireNonNull(scheduledExecutor);
        Objects.requireNonNull(adServicesLogger);
        Objects.requireNonNull(context);
        Objects.requireNonNull(flags);
        Objects.requireNonNull(adSelectionServiceFilter);
        Objects.requireNonNull(devContext);

        mAdSelectionEntryDao = adSelectionEntryDao;
        mBackgroundExecutorService = MoreExecutors.listeningDecorator(backgroundExecutorService);
        mLightweightExecutorService = MoreExecutors.listeningDecorator(lightweightExecutorService);
        mScheduledExecutor = scheduledExecutor;
        mAdServicesHttpsClient =
                new AdServicesHttpsClient(
                        AdServicesExecutors.getBlockingExecutor(),
                        CacheProviderFactory.create(context, flags));
        mAdServicesLogger = adServicesLogger;
        mContext = context;
        mFlags = flags;

        mAdOutcomeSelector = adOutcomeSelector;
        mAdSelectionServiceFilter = adSelectionServiceFilter;
        mCallerUid = callerUid;
        mPrebuiltLogicGenerator = new PrebuiltLogicGenerator(mFlags);
        mDevContext = devContext;
        mShouldUseUnifiedTables = shouldUseUnifiedTables;
    }

    /**
     * Runs outcome selection logic on given list of outcomes and signals.
     *
     * @param inputParams includes list of outcomes, selection signals and URI to download the logic
     * @param callback is used to notify the results to the caller
     */
    public void runOutcomeSelection(
            @NonNull AdSelectionFromOutcomesInput inputParams,
            @NonNull AdSelectionCallback callback) {
        Objects.requireNonNull(inputParams);
        Objects.requireNonNull(callback);
        AdSelectionFromOutcomesConfig adSelectionFromOutcomesConfig =
                inputParams.getAdSelectionFromOutcomesConfig();
        try {
            ListenableFuture<Void> filterAndValidateRequestFuture =
                    Futures.submit(
                            () -> {
                                try {
                                    Trace.beginSection(Tracing.VALIDATE_REQUEST);
                                    sLogger.v("Starting filtering and validation.");
                                    mAdSelectionServiceFilter.filterRequest(
                                            adSelectionFromOutcomesConfig.getSeller(),
                                            inputParams.getCallerPackageName(),
                                            mFlags
                                                    .getEnforceForegroundStatusForFledgeRunAdSelection(),
                                            true,
                                            mCallerUid,
                                            AdServicesStatsLog
                                                    .AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN,
                                            Throttler.ApiKey.FLEDGE_API_SELECT_ADS,
                                            mDevContext);
                                    validateAdSelectionFromOutcomesConfig(inputParams);
                                } finally {
                                    sLogger.v("Completed filtering and validation.");
                                    Trace.endSection();
                                }
                            },
                            mLightweightExecutorService);

            ListenableFuture<AdSelectionOutcome> adSelectionOutcomeFuture =
                    FluentFuture.from(filterAndValidateRequestFuture)
                            .transformAsync(
                                    ignoredVoid ->
                                            orchestrateOutcomeSelection(
                                                    inputParams.getAdSelectionFromOutcomesConfig(),
                                                    inputParams.getCallerPackageName()),
                                    mLightweightExecutorService);
            Futures.addCallback(
                    adSelectionOutcomeFuture,
                    new FutureCallback<>() {
                        @Override
                        public void onSuccess(AdSelectionOutcome result) {
                            notifySuccessToCaller(
                                    inputParams.getCallerPackageName(), result, callback);
                        }

                        @Override
                        public void onFailure(Throwable t) {
                            if (t instanceof FilterException
                                    && t.getCause()
                                            instanceof ConsentManager.RevokedConsentException) {
                                // Skip logging if a FilterException occurs.
                                // AdSelectionServiceFilter ensures the failing assertion is logged
                                // internally.

                                // Fail Silently by notifying success to caller
                                notifyEmptySuccessToCaller(callback);
                            } else {
                                if (t.getCause() instanceof AdServicesException) {
                                    notifyFailureToCaller(
                                            inputParams.getCallerPackageName(),
                                            t.getCause(),
                                            callback);
                                } else {
                                    notifyFailureToCaller(
                                            inputParams.getCallerPackageName(), t, callback);
                                }
                            }
                        }
                    },
                    mLightweightExecutorService);

        } catch (Throwable t) {
            sLogger.v("runOutcomeSelection fails fast with exception %s.", t.toString());
            notifyFailureToCaller(inputParams.getCallerPackageName(), t, callback);
        }
    }

    private ListenableFuture<AdSelectionOutcome> orchestrateOutcomeSelection(
            AdSelectionFromOutcomesConfig config, String callerPackageName) {
        validateExistenceOfAdSelectionIds(config, callerPackageName);
        return retrieveAdSelectionIdWithBidList(config.getAdSelectionIds(), callerPackageName)
                .transform(
                        outcomes -> {
                            FluentFuture<Long> selectedIdFuture =
                                    mAdOutcomeSelector.runAdOutcomeSelector(outcomes, config);
                            return Pair.create(outcomes, selectedIdFuture);
                        },
                        mLightweightExecutorService)
                .transformAsync(
                        outcomeAndSelectedIdPair ->
                                convertAdSelectionIdToAdSelectionOutcome(
                                        outcomeAndSelectedIdPair.first,
                                        outcomeAndSelectedIdPair.second),
                        mLightweightExecutorService)
                .withTimeout(
                        mFlags.getAdSelectionFromOutcomesOverallTimeoutMs(),
                        TimeUnit.MILLISECONDS,
                        mScheduledExecutor)
                .catching(
                        TimeoutException.class,
                        this::handleTimeoutError,
                        mLightweightExecutorService);
    }

    @Nullable
    private AdSelectionOutcome handleTimeoutError(TimeoutException e) {
        sLogger.e(e, "Ad Selection exceeded time limit");
        throw new UncheckedTimeoutException(AD_SELECTION_TIMED_OUT);
    }

    private void notifySuccessToCaller(
            String callerAppPackageName, AdSelectionOutcome result, AdSelectionCallback callback) {
        int resultCode = AdServicesStatusUtils.STATUS_UNSET;
        try {
            // Note: Success is logged before the callback to ensure deterministic testing.
            mAdServicesLogger.logFledgeApiCallStats(
                    AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN,
                    callerAppPackageName,
                    STATUS_SUCCESS,
                    /*latencyMs=*/ 0);
            if (result == null) {
                callback.onSuccess(null);
            } else {
                callback.onSuccess(
                        new AdSelectionResponse.Builder()
                                .setAdSelectionId(result.getAdSelectionId())
                                .setRenderUri(result.getRenderUri())
                                .build());
            }
        } catch (RemoteException e) {
            sLogger.e(e, "Encountered exception during notifying AdSelectionCallback");
        } finally {
            sLogger.v("Ad Selection from outcomes completed and attempted notifying success");
        }
    }

    /** Sends a successful response to the caller that represents a silent failure. */
    private void notifyEmptySuccessToCaller(@NonNull AdSelectionCallback callback) {
        try {
            // TODO(b/259522822): Determine what is an appropriate empty response for revoked
            //  consent for selectAdsFromOutcomes
            callback.onSuccess(null);
        } catch (RemoteException e) {
            sLogger.e(e, "Encountered exception during notifying AdSelectionCallback");
        } finally {
            sLogger.v(
                    "Ad Selection from outcomes completed, attempted notifying success for a"
                            + " silent failure");
        }
    }

    /** Sends a failure notification to the caller */
    private void notifyFailureToCaller(
            String callerAppPackageName, Throwable t, AdSelectionCallback callback) {
        try {
            sLogger.e("Notify caller of error: " + t);
            int resultCode = AdServicesLoggerUtil.getResultCodeFromException(t);

            // Skip logging if a FilterException occurs.
            // AdSelectionServiceFilter ensures the failing assertion is logged internally.
            // Note: Failure is logged before the callback to ensure deterministic testing.
            if (!(t instanceof FilterException)) {
                mAdServicesLogger.logFledgeApiCallStats(
                        AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN,
                        callerAppPackageName,
                        resultCode,
                        /*latencyMs=*/ 0);
            }

            FledgeErrorResponse selectionFailureResponse =
                    new FledgeErrorResponse.Builder()
                            .setErrorMessage(
                                    String.format(
                                            AD_SELECTION_FROM_OUTCOMES_ERROR_PATTERN,
                                            ERROR_AD_SELECTION_FROM_OUTCOMES_FAILURE,
                                            t.getMessage()))
                            .setStatusCode(resultCode)
                            .build();
            sLogger.e(t, "Ad Selection failure: ");
            callback.onFailure(selectionFailureResponse);
        } catch (RemoteException e) {
            sLogger.e(e, "Encountered exception during notifying AdSelectionCallback");
        } finally {
            sLogger.v("Ad Selection From Outcomes failed");
        }
    }

    /** Retrieves winner ad bids using ad selection ids of already run ad selections' outcomes. */
    private FluentFuture<List<AdSelectionResultBidAndUri>> retrieveAdSelectionIdWithBidList(
            List<Long> adOutcomeIds, String callerPackageName) {
        return FluentFuture.from(
                mBackgroundExecutorService.submit(
                        () -> {
                            if (mShouldUseUnifiedTables) {
                                return mAdSelectionEntryDao.getWinningBidAndUriForIdsUnifiedTables(
                                        adOutcomeIds);
                            } else if (mFlags
                                    .getFledgeAuctionServerEnabledForSelectAdsMediation()) {
                                return mAdSelectionEntryDao.getWinningBidAndUriForIds(adOutcomeIds);
                            } else {
                                return mAdSelectionEntryDao
                                        .getAdSelectionEntities(adOutcomeIds, callerPackageName)
                                        .parallelStream()
                                        .map(
                                                e ->
                                                        AdSelectionResultBidAndUri.builder()
                                                                .setAdSelectionId(
                                                                        e.getAdSelectionId())
                                                                .setWinningAdBid(
                                                                        e.getWinningAdBid())
                                                                .setWinningAdRenderUri(
                                                                        e.getWinningAdRenderUri())
                                                                .build())
                                        .collect(Collectors.toList());
                            }
                        }));
    }

    private void validateExistenceOfAdSelectionIds(
            AdSelectionFromOutcomesConfig config, String callerPackageName) {
        Objects.requireNonNull(config.getAdSelectionIds());

        ImmutableList.Builder<Long> notExistingIds = new ImmutableList.Builder<>();
        List<Long> existingIds;
        if (mShouldUseUnifiedTables) {
            existingIds =
                    mAdSelectionEntryDao.getAdSelectionIdsWithCallerPackageNameFromUnifiedTable(
                            config.getAdSelectionIds(), callerPackageName);
        } else if (mFlags.getFledgeAuctionServerEnabledForSelectAdsMediation()) {
            existingIds =
                    mAdSelectionEntryDao.getAdSelectionIdsWithCallerPackageName(
                            config.getAdSelectionIds(), callerPackageName);
        } else {
            existingIds =
                    mAdSelectionEntryDao.getAdSelectionIdsWithCallerPackageNameInOnDeviceTable(
                            config.getAdSelectionIds(), callerPackageName);
        }
        config.getAdSelectionIds().stream()
                .filter(e -> !existingIds.contains(e))
                .forEach(notExistingIds::add);

        // TODO(b/258912806): Current behavior is to fail if any ad selection ids are absent in the
        //  db or owned by another caller package. Investigate if this behavior needs changing due
        //  to security reasons.
        if (!notExistingIds.build().isEmpty()) {
            String err =
                    String.format(
                            "Ad selection ids: %s don't exists or owned by the calling "
                                    + "package",
                            notExistingIds.build());
            sLogger.e(err);
            throw new IllegalArgumentException(err);
        }
    }

    /** Retrieves winner ad bids using ad selection ids of already run ad selections' outcomes. */
    private ListenableFuture<AdSelectionOutcome> convertAdSelectionIdToAdSelectionOutcome(
            List<AdSelectionResultBidAndUri> outcomes, FluentFuture<Long> adSelectionIdFutures) {
        return adSelectionIdFutures.transformAsync(
                selectedId -> {
                    if (Objects.isNull(selectedId)) {
                        sLogger.v("No id is selected. Returning null");
                        return Futures.immediateFuture(null);
                    }
                    sLogger.v(
                            "Converting ad selection id: <%s> to AdSelectionOutcome.", selectedId);
                    return outcomes.stream()
                            .filter(e -> Objects.equals(e.getAdSelectionId(), selectedId))
                            .findFirst()
                            .map(
                                    e ->
                                            Futures.immediateFuture(
                                                    new AdSelectionOutcome.Builder()
                                                            .setAdSelectionId(e.getAdSelectionId())
                                                            .setRenderUri(e.getWinningAdRenderUri())
                                                            .build()))
                            .orElse(
                                    Futures.immediateFailedFuture(
                                            new IllegalStateException(
                                                    SELECTED_OUTCOME_MUST_BE_ONE_OF_THE_INPUTS)));
                },
                mLightweightExecutorService);
    }
    /**
     * Validates the {@link AdSelectionFromOutcomesInput} from the request.
     *
     * @param inputParams the adSelectionConfig to be validated
     * @throws IllegalArgumentException if the provided {@code adSelectionConfig} is not valid
     */
    private void validateAdSelectionFromOutcomesConfig(
            @NonNull AdSelectionFromOutcomesInput inputParams) throws IllegalArgumentException {
        Objects.requireNonNull(inputParams);

        AdSelectionFromOutcomesConfigValidator validator =
                new AdSelectionFromOutcomesConfigValidator(
                        inputParams.getCallerPackageName(),
                        mPrebuiltLogicGenerator);
        validator.validate(inputParams.getAdSelectionFromOutcomesConfig());
    }
}
