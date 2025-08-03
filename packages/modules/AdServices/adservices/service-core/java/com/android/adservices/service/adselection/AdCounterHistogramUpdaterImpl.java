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

import android.adservices.common.AdTechIdentifier;
import android.adservices.common.FrequencyCapFilters;
import android.annotation.NonNull;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.adselection.DBAdSelection;
import com.android.adservices.data.adselection.DBAdSelectionHistogramInfo;
import com.android.adservices.data.adselection.FrequencyCapDao;
import com.android.adservices.data.adselection.datahandlers.AdSelectionInitialization;
import com.android.adservices.data.adselection.datahandlers.WinningCustomAudience;

import com.google.common.base.Preconditions;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Implementation for an {@link AdCounterHistogramUpdater} which actually updates the histograms for
 * given ad events.
 */
public class AdCounterHistogramUpdaterImpl implements AdCounterHistogramUpdater {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    private final AdSelectionEntryDao mAdSelectionEntryDao;
    private final FrequencyCapDao mFrequencyCapDao;
    private final int mAbsoluteMaxTotalHistogramEventCount;
    private final int mLowerMaxTotalHistogramEventCount;
    private final int mAbsoluteMaxPerBuyerHistogramEventCount;
    private final int mLowerMaxPerBuyerHistogramEventCount;
    private final boolean mAuctionServerEnabledForUpdateHistogram;
    private final boolean mShouldUseUnifiedTables;

    public AdCounterHistogramUpdaterImpl(
            @NonNull AdSelectionEntryDao adSelectionEntryDao,
            @NonNull FrequencyCapDao frequencyCapDao,
            int absoluteMaxTotalHistogramEventCount,
            int lowerMaxTotalHistogramEventCount,
            int absoluteMaxPerBuyerHistogramEventCount,
            int lowerMaxPerBuyerHistogramEventCount,
            boolean auctionServerEnabledForUpdateHistogram,
            boolean shouldUseUnifiedTables) {
        Objects.requireNonNull(adSelectionEntryDao);
        Objects.requireNonNull(frequencyCapDao);
        Preconditions.checkArgument(absoluteMaxTotalHistogramEventCount > 0);
        Preconditions.checkArgument(lowerMaxTotalHistogramEventCount > 0);
        Preconditions.checkArgument(absoluteMaxPerBuyerHistogramEventCount > 0);
        Preconditions.checkArgument(lowerMaxPerBuyerHistogramEventCount > 0);
        Preconditions.checkArgument(
                absoluteMaxTotalHistogramEventCount > lowerMaxTotalHistogramEventCount);
        Preconditions.checkArgument(
                absoluteMaxPerBuyerHistogramEventCount > lowerMaxPerBuyerHistogramEventCount);

        mAdSelectionEntryDao = adSelectionEntryDao;
        mFrequencyCapDao = frequencyCapDao;
        mAbsoluteMaxTotalHistogramEventCount = absoluteMaxTotalHistogramEventCount;
        mLowerMaxTotalHistogramEventCount = lowerMaxTotalHistogramEventCount;
        mAbsoluteMaxPerBuyerHistogramEventCount = absoluteMaxPerBuyerHistogramEventCount;
        mLowerMaxPerBuyerHistogramEventCount = lowerMaxPerBuyerHistogramEventCount;
        mAuctionServerEnabledForUpdateHistogram = auctionServerEnabledForUpdateHistogram;
        mShouldUseUnifiedTables = shouldUseUnifiedTables;
    }

    @Override
    public void updateWinHistogram(@NonNull DBAdSelection dbAdSelection) {
        Objects.requireNonNull(dbAdSelection);

        if (dbAdSelection.getCustomAudienceSignals() == null) {
            sLogger.v("Winning ad has no associated custom audience signals");
            return;
        }

        if (dbAdSelection.getAdCounterIntKeys() == null
                || dbAdSelection.getAdCounterIntKeys().isEmpty()) {
            sLogger.v("Winning ad has no associated ad counter keys to update histogram");
            return;
        }

        HistogramEvent.Builder eventBuilder =
                HistogramEvent.builder()
                        .setAdEventType(FrequencyCapFilters.AD_EVENT_TYPE_WIN)
                        .setBuyer(dbAdSelection.getCustomAudienceSignals().getBuyer())
                        .setCustomAudienceOwner(dbAdSelection.getCustomAudienceSignals().getOwner())
                        .setCustomAudienceName(dbAdSelection.getCustomAudienceSignals().getName())
                        .setTimestamp(dbAdSelection.getCreationTimestamp())
                        .setSourceApp(dbAdSelection.getCallerPackageName());

        sLogger.v("Inserting %d histogram events", dbAdSelection.getAdCounterIntKeys().size());
        for (Integer key : dbAdSelection.getAdCounterIntKeys()) {
            // TODO(b/276528814): Insert in bulk instead of in multiple transactions
            //  and handle eviction only once
            mFrequencyCapDao.insertHistogramEvent(
                    eventBuilder.setAdCounterKey(key).build(),
                    mAbsoluteMaxTotalHistogramEventCount,
                    mLowerMaxTotalHistogramEventCount,
                    mAbsoluteMaxPerBuyerHistogramEventCount,
                    mLowerMaxPerBuyerHistogramEventCount);
        }
    }

    @Override
    public void updateWinHistogram(
            @NonNull AdTechIdentifier buyer,
            @NonNull AdSelectionInitialization adSelectionInitialization,
            @NonNull WinningCustomAudience winningCustomAudience) {
        Objects.requireNonNull(adSelectionInitialization);
        Objects.requireNonNull(winningCustomAudience);

        Set<Integer> adCounterKeys = winningCustomAudience.getAdCounterKeys();
        if (adCounterKeys == null || adCounterKeys.isEmpty()) {
            sLogger.v("Winning ad has no associated ad counter keys to update histogram");
            return;
        }

        HistogramEvent.Builder eventBuilder =
                HistogramEvent.builder()
                        .setAdEventType(FrequencyCapFilters.AD_EVENT_TYPE_WIN)
                        .setBuyer(buyer)
                        .setCustomAudienceOwner(winningCustomAudience.getOwner())
                        .setCustomAudienceName(winningCustomAudience.getName())
                        .setTimestamp(adSelectionInitialization.getCreationInstant())
                        .setSourceApp(adSelectionInitialization.getCallerPackageName());

        sLogger.v("Inserting %d histogram events", adCounterKeys.size());
        for (Integer key : adCounterKeys) {
            // TODO(b/276528814): Insert in bulk instead of in multiple transactions
            //  and handle eviction only once
            mFrequencyCapDao.insertHistogramEvent(
                    eventBuilder.setAdCounterKey(key).build(),
                    mAbsoluteMaxTotalHistogramEventCount,
                    mLowerMaxTotalHistogramEventCount,
                    mAbsoluteMaxPerBuyerHistogramEventCount,
                    mLowerMaxPerBuyerHistogramEventCount);
        }
    }

    @Override
    public void updateNonWinHistogram(
            long adSelectionId,
            @NonNull String callerPackageName,
            @FrequencyCapFilters.AdEventType int adEventType,
            @NonNull Instant eventTimestamp) {
        Objects.requireNonNull(callerPackageName);
        Preconditions.checkArgument(
                adEventType != FrequencyCapFilters.AD_EVENT_TYPE_WIN
                        && adEventType != FrequencyCapFilters.AD_EVENT_TYPE_INVALID);
        Objects.requireNonNull(eventTimestamp);

        DBAdSelectionHistogramInfo histogramInfo;
        if (mShouldUseUnifiedTables) {
            sLogger.v("Should use unified tables flag is on, reading only from new tables.");
            histogramInfo =
                    mAdSelectionEntryDao.getAdSelectionHistogramInfoFromUnifiedTable(
                            adSelectionId, callerPackageName);
        } else if (!mAuctionServerEnabledForUpdateHistogram) {
            sLogger.v("Reading from legacy tables.");
            histogramInfo =
                    mAdSelectionEntryDao.getAdSelectionHistogramInfoInOnDeviceTable(
                            adSelectionId, callerPackageName);
        } else {
            sLogger.v("Server auction is enabled, reading from all tables.");
            histogramInfo =
                    mAdSelectionEntryDao.getAdSelectionHistogramInfo(
                            adSelectionId, callerPackageName);
        }

        if (histogramInfo == null) {
            sLogger.v(
                    "No ad selection with ID %d and caller package name %s found",
                    adSelectionId, callerPackageName);
            return;
        }

        Set<Integer> adCounterKeys = histogramInfo.getAdCounterKeys();
        if (adCounterKeys == null || adCounterKeys.isEmpty()) {
            sLogger.v(
                    "No ad counter keys associated with ad selection with ID %d and caller package"
                            + " name %s",
                    adSelectionId, callerPackageName);
            return;
        }

        HistogramEvent.Builder eventBuilder =
                HistogramEvent.builder()
                        .setAdEventType(adEventType)
                        .setBuyer(histogramInfo.getBuyer())
                        .setTimestamp(eventTimestamp)
                        .setSourceApp(callerPackageName);

        sLogger.v("Inserting %d histogram events", adCounterKeys.size());
        for (Integer key : adCounterKeys) {
            // TODO(b/276528814): Insert in bulk instead of in multiple transactions
            //  and handle eviction only once
            mFrequencyCapDao.insertHistogramEvent(
                    eventBuilder.setAdCounterKey(key).build(),
                    mAbsoluteMaxTotalHistogramEventCount,
                    mLowerMaxTotalHistogramEventCount,
                    mAbsoluteMaxPerBuyerHistogramEventCount,
                    mLowerMaxPerBuyerHistogramEventCount);
        }
    }
}
