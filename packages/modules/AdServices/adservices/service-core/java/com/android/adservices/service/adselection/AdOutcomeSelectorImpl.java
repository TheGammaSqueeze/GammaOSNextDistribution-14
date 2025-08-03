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

import android.adservices.adselection.AdSelectionFromOutcomesConfig;
import android.annotation.NonNull;
import android.annotation.Nullable;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.adselection.datahandlers.AdSelectionResultBidAndUri;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.httpclient.AdServicesHttpClientRequest;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.devapi.AdSelectionDevOverridesHelper;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.profiling.Tracing;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.UncheckedTimeoutException;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Selects ad outcome based on previous winners of Remarketing Ads.
 *
 * <p>A new instance is assumed to be created for every call.
 */
public class AdOutcomeSelectorImpl implements AdOutcomeSelector {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    @VisibleForTesting
    static final String OUTCOME_SELECTION_TIMED_OUT =
            "Outcome selection exceeded allowed time limit";

    @VisibleForTesting
    static final String OUTCOME_SELECTION_JS_RETURNED_UNEXPECTED_RESULT =
            "Outcome selection Js execution returned either a failed status or more than one ad";

    @NonNull private final AdSelectionScriptEngine mAdSelectionScriptEngine;
    @NonNull private final ListeningExecutorService mLightweightExecutorService;
    @NonNull private final ListeningExecutorService mBackgroundExecutorService;
    @NonNull private final ScheduledThreadPoolExecutor mScheduledExecutor;
    @NonNull private final AdServicesHttpsClient mAdServicesHttpsClient;
    @NonNull private final AdSelectionDevOverridesHelper mAdSelectionDevOverridesHelper;
    @NonNull private final PrebuiltLogicGenerator mPrebuiltLogicGenerator;
    @NonNull private final Flags mFlags;
    @NonNull private final JsFetcher mJsFetcher;
    @NonNull private final DevContext mDevContext;

    public AdOutcomeSelectorImpl(
            @NonNull AdSelectionScriptEngine adSelectionScriptEngine,
            @NonNull ListeningExecutorService lightweightExecutor,
            @NonNull ListeningExecutorService backgroundExecutor,
            @NonNull ScheduledThreadPoolExecutor scheduledExecutor,
            @NonNull AdServicesHttpsClient adServicesHttpsClient,
            @NonNull AdSelectionDevOverridesHelper adSelectionDevOverridesHelper,
            @NonNull Flags flags,
            @NonNull DevContext devContext) {
        Objects.requireNonNull(adSelectionScriptEngine);
        Objects.requireNonNull(lightweightExecutor);
        Objects.requireNonNull(backgroundExecutor);
        Objects.requireNonNull(scheduledExecutor);
        Objects.requireNonNull(adServicesHttpsClient);
        Objects.requireNonNull(flags);
        Objects.requireNonNull(devContext);

        mAdSelectionScriptEngine = adSelectionScriptEngine;
        mAdServicesHttpsClient = adServicesHttpsClient;
        mLightweightExecutorService = lightweightExecutor;
        mBackgroundExecutorService = backgroundExecutor;
        mScheduledExecutor = scheduledExecutor;
        mAdSelectionDevOverridesHelper = adSelectionDevOverridesHelper;
        mPrebuiltLogicGenerator = new PrebuiltLogicGenerator(flags);
        mFlags = flags;
        mDevContext = devContext;
        mJsFetcher =
                new JsFetcher(
                        mBackgroundExecutorService,
                        mLightweightExecutorService,
                        mAdServicesHttpsClient,
                        mFlags,
                        mDevContext);
    }

    /**
     * Compares ads based on their bids and selection signals.
     *
     * @param adSelectionIdWithBidAndRenderUris list of ad selection id and bid pairs
     * @param config {@link AdSelectionFromOutcomesConfig} instance
     * @return a Future of {@code Long} {code @AdSelectionId} of the winner. If no winner then
     *     returns null
     */
    @Override
    public FluentFuture<Long> runAdOutcomeSelector(
            @NonNull List<AdSelectionResultBidAndUri> adSelectionIdWithBidAndRenderUris,
            @NonNull AdSelectionFromOutcomesConfig config) {
        Objects.requireNonNull(adSelectionIdWithBidAndRenderUris);
        Objects.requireNonNull(config);

        AdServicesHttpClientRequest outcomeSelectorLogicUriHttpRequest =
                AdServicesHttpClientRequest.builder()
                        .setUri(config.getSelectionLogicUri())
                        .setUseCache(mFlags.getFledgeHttpJsCachingEnabled())
                        .setDevContext(mDevContext)
                        .build();

        FluentFuture<String> selectionLogicJsFuture =
                mJsFetcher.getOutcomeSelectionLogic(
                        outcomeSelectorLogicUriHttpRequest, mAdSelectionDevOverridesHelper, config);

        FluentFuture<Long> selectedOutcomeFuture =
                selectionLogicJsFuture.transformAsync(
                        selectionLogic ->
                                mAdSelectionScriptEngine.selectOutcome(
                                        selectionLogic,
                                        adSelectionIdWithBidAndRenderUris,
                                        config.getSelectionSignals()),
                        mLightweightExecutorService);

        int traceCookie = Tracing.beginAsyncSection(Tracing.RUN_OUTCOME_SELECTION);
        return selectedOutcomeFuture
                .withTimeout(
                        mFlags.getAdSelectionSelectingOutcomeTimeoutMs(),
                        TimeUnit.MILLISECONDS,
                        mScheduledExecutor)
                .catching(
                        TimeoutException.class,
                        e -> {
                            Tracing.endAsyncSection(Tracing.RUN_OUTCOME_SELECTION, traceCookie);
                            return handleTimeoutError(e);
                        },
                        mLightweightExecutorService)
                .catching(
                        IllegalStateException.class,
                        e -> {
                            Tracing.endAsyncSection(Tracing.RUN_OUTCOME_SELECTION, traceCookie);
                            return handleIllegalStateException(e);
                        },
                        mLightweightExecutorService);
    }

    @Nullable
    private Long handleTimeoutError(TimeoutException e) {
        sLogger.e(e, OUTCOME_SELECTION_TIMED_OUT);
        throw new UncheckedTimeoutException(OUTCOME_SELECTION_TIMED_OUT);
    }

    /**
     * Handles {@link IllegalStateException} that can be thrown in {@link
     * AdSelectionScriptEngine#selectOutcome} if the result's status is failure or results contains
     * more than one item.
     */
    @Nullable
    private Long handleIllegalStateException(IllegalStateException e) {
        sLogger.e(e, OUTCOME_SELECTION_JS_RETURNED_UNEXPECTED_RESULT);
        throw new IllegalStateException(OUTCOME_SELECTION_JS_RETURNED_UNEXPECTED_RESULT);
    }
}
