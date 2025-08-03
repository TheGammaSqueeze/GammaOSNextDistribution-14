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

import android.adservices.adselection.AdSelectionConfig;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.annotation.NonNull;
import android.net.Uri;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.service.Flags;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.RunAdBiddingPerCAExecutionLogger;
import com.android.adservices.shared.util.Clock;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.Lists;
import com.google.common.util.concurrent.ExecutionSequencer;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Runs bidding for a buyer and its associated Custom Audience. The bidding for every buyer is time
 * capped, where the incomplete CAs are dropped from bidding when timed out while preserving the
 * ones that were already completed
 */
public class PerBuyerBiddingRunner {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    @NonNull private final AdBidGenerator mAdBidGenerator;
    @NonNull private final TrustedBiddingDataFetcher mTrustedBiddingDataFetcher;
    @NonNull private final ScheduledThreadPoolExecutor mScheduledExecutor;
    @NonNull private final ExecutorService mBackgroundExecutorService;
    @NonNull private final Flags mFlags;

    public PerBuyerBiddingRunner(
            @NonNull AdBidGenerator adBidGenerator,
            @NonNull TrustedBiddingDataFetcher trustedBiddingDataFetcher,
            @NonNull ScheduledThreadPoolExecutor scheduledExecutor,
            @NonNull ExecutorService backgroundExecutor,
            @NonNull Flags flags) {
        mAdBidGenerator = adBidGenerator;
        mTrustedBiddingDataFetcher = trustedBiddingDataFetcher;
        mScheduledExecutor = scheduledExecutor;
        mBackgroundExecutorService = backgroundExecutor;
        mFlags = flags;
    }
    /**
     * This method executes bidding in chunks on a list of CustomAudience for a buyer. Using the
     * configurable flag we divide the custom audience list into sub-lists. These lists are bid in
     * parallel with each other. Whereas each item in the list is bid sequentially. This leads to
     * significant saving of resources as without sequence, all the CAs begin bidding async and
     * start downloading JS and consuming other resources. This ensures that at any point, only one
     * bidding would be in progress.
     *
     * @param buyerTimeoutMs timeout value, post which incomplete CA bids are cancelled
     * @param adSelectionConfig for the current Ad Selection
     * @return list of futures with bidding outcomes
     */
    public List<ListenableFuture<AdBiddingOutcome>> runBidding(
            final AdTechIdentifier buyer,
            final List<DBCustomAudience> customAudienceList,
            final long buyerTimeoutMs,
            final AdSelectionConfig adSelectionConfig) {
        sLogger.v(
                "Running bid for #%d Custom Audiences for buyer: %s",
                customAudienceList.size(), buyer);

        List<List<DBCustomAudience>> biddingWorkPartitions =
                partitionList(customAudienceList, getMaxConcurrentBiddingCount());

        sLogger.v("Fetching trusted bidding data for buyer: %s", buyer);
        FluentFuture<Map<Uri, TrustedBiddingResponse>> trustedBiddingDataMap =
                mTrustedBiddingDataFetcher.getTrustedBiddingDataForBuyer(customAudienceList);

        List<ListenableFuture<AdBiddingOutcome>> buyerBiddingOutcomes =
                biddingWorkPartitions.stream()
                        .map(
                                (customAudience) ->
                                        runBidPerCAWorkPartition(
                                                customAudience,
                                                adSelectionConfig,
                                                trustedBiddingDataMap))
                        .flatMap(List::stream)
                        .collect(Collectors.toList());
        eventuallyTimeoutIncompleteTasks(buyerTimeoutMs, buyerBiddingOutcomes);
        return buyerBiddingOutcomes;
    }

    /**
     * Runs bidding for custom Audience in a strict sequence. By leveraging the sequential executor,
     * the bidding for subsequent Custom Audience is not even started until the previous bidding
     * completes.
     *
     * @param customAudienceSubList only a part of Custom Audience List
     * @param adSelectionConfig for the current Ad Selection
     * @return list of futures with bidding outcomes
     */
    private List<ListenableFuture<AdBiddingOutcome>> runBidPerCAWorkPartition(
            List<DBCustomAudience> customAudienceSubList,
            AdSelectionConfig adSelectionConfig,
            FluentFuture<Map<Uri, TrustedBiddingResponse>> trustedBiddingDataMap) {
        ExecutionSequencer sequencer = ExecutionSequencer.create();
        sLogger.v("Bidding partition chunk size: %d", customAudienceSubList.size());
        return customAudienceSubList.stream()
                .map(
                        (customAudience) ->
                                sequencer.submitAsync(
                                        () ->
                                                trustedBiddingDataMap.transformAsync(
                                                        map ->
                                                                runBiddingPerCA(
                                                                        customAudience,
                                                                        adSelectionConfig,
                                                                        map),
                                                        mBackgroundExecutorService),
                                        mBackgroundExecutorService))
                .collect(Collectors.toList());
    }

    private ListenableFuture<AdBiddingOutcome> runBiddingPerCA(
            @NonNull final DBCustomAudience customAudience,
            @NonNull final AdSelectionConfig adSelectionConfig,
            @NonNull final Map<Uri, TrustedBiddingResponse> trustedBiddingDataByBaseUri) {
        sLogger.v(String.format("Invoking bidding for CA: %s", customAudience.getName()));

        // TODO(b/233239475) : Validate Buyer signals in Ad Selection Config
        AdSelectionSignals buyerSignal =
                Optional.ofNullable(
                                adSelectionConfig
                                        .getPerBuyerSignals()
                                        .get(customAudience.getBuyer()))
                        .orElse(AdSelectionSignals.EMPTY);
        return mAdBidGenerator.runAdBiddingPerCA(
                customAudience,
                trustedBiddingDataByBaseUri,
                adSelectionConfig.getAdSelectionSignals(),
                buyerSignal,
                new RunAdBiddingPerCAExecutionLogger(
                        Clock.getInstance(), AdServicesLoggerImpl.getInstance()));
    }

    /**
     * Instead of timing out entire list of future, we only cancel the ones which are not done. This
     * helps preserve tasks that are already completed while freeing up resources from the tasks
     * which maybe in progress or are yet to be scheduled by cancelling them.
     *
     * @param timeoutMs delay after which these tasks should be cancelled
     * @param runningTasks potentially ongoing tasks, that need to be timed-out
     */
    private <T> void eventuallyTimeoutIncompleteTasks(
            final long timeoutMs, List<ListenableFuture<T>> runningTasks) {
        Runnable cancelOngoingTasks =
                () -> {
                    int incompleteTaskCount = 0;
                    for (ListenableFuture<T> runningTask : runningTasks) {
                        if (runningTask.cancel(true)) {
                            incompleteTaskCount++;
                        }
                    }
                    sLogger.v(
                            "Total tasks: #%d, cancelled incomplete tasks: #%d",
                            runningTasks.size(), incompleteTaskCount);
                };
        mScheduledExecutor.schedule(cancelOngoingTasks, timeoutMs, TimeUnit.MILLISECONDS);
    }

    @VisibleForTesting
    <T> List<List<T>> partitionList(final List<T> list, int numPartitions) {
        // Negative partitions not possible
        numPartitions = Math.abs(numPartitions);
        // 0 partition is equivalent to the original list itself, so 1 partition
        if (numPartitions == 0) {
            numPartitions = 1;
        }
        int chunkSize =
                (list.size() / numPartitions) + (((list.size() % numPartitions) == 0) ? 0 : 1);
        return Lists.partition(list, chunkSize);
    }

    private int getMaxConcurrentBiddingCount() {
        return mFlags.getAdSelectionMaxConcurrentBiddingCount();
    }
}
