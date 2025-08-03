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

import static android.adservices.customaudience.CustomAudience.FLAG_AUCTION_SERVER_REQUEST_OMIT_ADS;

import android.adservices.common.AdTechIdentifier;
import android.annotation.NonNull;
import android.util.Pair;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.data.signals.DBEncodedPayload;
import com.android.adservices.data.signals.EncodedPayloadDao;
import com.android.adservices.service.profiling.Tracing;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.BuyerInput;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.ProtectedAppSignals;
import com.android.adservices.service.stats.BuyerInputGeneratorIntermediateStats;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.protobuf.ByteString;

import java.time.Clock;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Generates {@link BuyerInput} proto from device custom audience info. */
public class BuyerInputGenerator {

    private static final String EMPTY_USER_BIDDING_SIGNALS = "{}";
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    @NonNull private final CustomAudienceDao mCustomAudienceDao;
    @NonNull private final EncodedPayloadDao mEncodedSignalsDao;
    @NonNull private final AdFilterer mAdFilterer;
    @NonNull private final Clock mClock;
    @NonNull private final ListeningExecutorService mLightweightExecutorService;
    @NonNull private final ListeningExecutorService mBackgroundExecutorService;
    private final long mCustomAudienceActiveTimeWindowInMs;
    private final boolean mEnableAdFilter;
    private final boolean mEnableProtectedSignals;
    private final boolean mEnableOmitAds;

    @NonNull private final AuctionServerDataCompressor mDataCompressor;
    @NonNull private final AuctionServerPayloadMetricsStrategy mAuctionServerPayloadMetricsStrategy;

    public BuyerInputGenerator(
            @NonNull final CustomAudienceDao customAudienceDao,
            @NonNull final EncodedPayloadDao encodedSignalsDaoDao,
            @NonNull final AdFilterer adFilterer,
            @NonNull final ExecutorService lightweightExecutorService,
            @NonNull final ExecutorService backgroundExecutorService,
            long customAudienceActiveTimeWindowInMs,
            boolean enableAdFilter,
            boolean enableProtectedSignals,
            @NonNull AuctionServerDataCompressor dataCompressor,
            boolean enableOmitAds,
            @NonNull AuctionServerPayloadMetricsStrategy auctionServerPayloadMetricsStrategy) {
        Objects.requireNonNull(customAudienceDao);
        Objects.requireNonNull(encodedSignalsDaoDao);
        Objects.requireNonNull(adFilterer);
        Objects.requireNonNull(lightweightExecutorService);
        Objects.requireNonNull(backgroundExecutorService);
        Objects.requireNonNull(dataCompressor);
        Objects.requireNonNull(auctionServerPayloadMetricsStrategy);

        mCustomAudienceDao = customAudienceDao;
        mEncodedSignalsDao = encodedSignalsDaoDao;
        mAdFilterer = adFilterer;
        mClock = Clock.systemUTC();
        mLightweightExecutorService = MoreExecutors.listeningDecorator(lightweightExecutorService);
        mBackgroundExecutorService = MoreExecutors.listeningDecorator(backgroundExecutorService);

        mDataCompressor = dataCompressor;
        mCustomAudienceActiveTimeWindowInMs = customAudienceActiveTimeWindowInMs;
        mEnableAdFilter = enableAdFilter;
        mEnableProtectedSignals = enableProtectedSignals;
        mEnableOmitAds = enableOmitAds;
        mAuctionServerPayloadMetricsStrategy = auctionServerPayloadMetricsStrategy;
    }

    /**
     * Creates {@link BuyerInput} class from {@link DBCustomAudience} entries to be sent to bidding
     * and auction servers.
     *
     * @return a map of buyer name and {@link BuyerInput}
     */
    public FluentFuture<Map<AdTechIdentifier, AuctionServerDataCompressor.CompressedData>>
            createCompressedBuyerInputs() {
        int traceCookie = Tracing.beginAsyncSection(Tracing.CREATE_BUYER_INPUTS);
        sLogger.v("Starting create buyer input");

        ListenableFuture<List<DBCustomAudience>> filteredCAs =
                FluentFuture.from(getBuyersCustomAudience())
                        .transform(this::getFilteredCustomAudiences, mLightweightExecutorService);
        ListenableFuture<Map<AdTechIdentifier, DBEncodedPayload>> allSignals =
                getAllEncodedProtectedSignals();
        ListenableFuture<Pair<List<DBCustomAudience>, Map<AdTechIdentifier, DBEncodedPayload>>>
                combinedCAsAndSignals =
                        Futures.whenAllSucceed(filteredCAs, allSignals)
                                .call(
                                        () -> {
                                            return new Pair<>(
                                                    Futures.getDone(filteredCAs),
                                                    Futures.getDone(allSignals));
                                        },
                                        mBackgroundExecutorService);

        return FluentFuture.from(combinedCAsAndSignals)
                .transform(
                        combined -> {
                            Map<AdTechIdentifier, AuctionServerDataCompressor.CompressedData>
                                    buyerInputFromCustomAudience =
                                            generateCompressedBuyerInputFromDBCAsAndEncodedSignals(
                                                    combined.first, combined.second);
                            Tracing.endAsyncSection(Tracing.CREATE_BUYER_INPUTS, traceCookie);
                            return buyerInputFromCustomAudience;
                        },
                        mLightweightExecutorService);
    }

    private Map<AdTechIdentifier, AuctionServerDataCompressor.CompressedData>
            generateCompressedBuyerInputFromDBCAsAndEncodedSignals(
                    @NonNull final List<DBCustomAudience> dbCustomAudiences,
                    @NonNull final Map<AdTechIdentifier, DBEncodedPayload> encodedPayloadMap) {
        int traceCookie = Tracing.beginAsyncSection(Tracing.GET_COMPRESSED_BUYERS_INPUTS);

        final Map<AdTechIdentifier, BuyerInput.Builder> buyerInputs = new HashMap<>();
        final Map<AdTechIdentifier, BuyerInputGeneratorIntermediateStats> perBuyerStats =
                new HashMap<>();
        for (DBCustomAudience dBcustomAudience : dbCustomAudiences) {
            final AdTechIdentifier buyerName = dBcustomAudience.getBuyer();
            if (!buyerInputs.containsKey(buyerName)) {
                buyerInputs.put(buyerName, BuyerInput.newBuilder());
            }
            BuyerInput.CustomAudience customAudience =
                    buildCustomAudienceProtoFrom(dBcustomAudience);

            buyerInputs.get(buyerName).addCustomAudiences(customAudience);

            mAuctionServerPayloadMetricsStrategy.addToBuyerIntermediateStats(
                    perBuyerStats, dBcustomAudience, customAudience);
        }

        // Log per buyer stats if feature is enabled
        mAuctionServerPayloadMetricsStrategy.logGetAdSelectionDataBuyerInputGeneratedStats(
                perBuyerStats);

        // Creating a distinct loop over signals as buyers with CAs and Signals could be mutually
        // exclusive
        for (Map.Entry<AdTechIdentifier, DBEncodedPayload> entry : encodedPayloadMap.entrySet()) {
            final AdTechIdentifier buyerName = entry.getKey();
            if (!buyerInputs.containsKey(buyerName)) {
                buyerInputs.put(buyerName, BuyerInput.newBuilder());
            }

            BuyerInput.Builder builderWithSignals = buyerInputs.get(buyerName);
            builderWithSignals.setProtectedAppSignals(
                    buildProtectedSignalsProtoFrom(entry.getValue()));

            buyerInputs.put(buyerName, builderWithSignals);
        }

        sLogger.v(String.format("Created BuyerInput proto for %s buyers", buyerInputs.size()));
        Map<AdTechIdentifier, AuctionServerDataCompressor.CompressedData> compressedInputs =
                buyerInputs.entrySet().stream()
                        .collect(
                                Collectors.toMap(
                                        e -> e.getKey(),
                                        e ->
                                                mDataCompressor.compress(
                                                        AuctionServerDataCompressor.UncompressedData
                                                                .create(
                                                                        e.getValue()
                                                                                .build()
                                                                                .toByteArray()))));
        Tracing.endAsyncSection(Tracing.GET_COMPRESSED_BUYERS_INPUTS, traceCookie);
        return compressedInputs;
    }

    private ListenableFuture<List<DBCustomAudience>> getBuyersCustomAudience() {
        int traceCookie = Tracing.beginAsyncSection(Tracing.GET_BUYERS_CA);
        return mBackgroundExecutorService.submit(
                () -> {
                    List<DBCustomAudience> allActiveCAs =
                            mCustomAudienceDao.getAllActiveCustomAudienceForServerSideAuction(
                                    mClock.instant(), mCustomAudienceActiveTimeWindowInMs);
                    int numberOfCAsCollected =
                            (Objects.isNull(allActiveCAs) ? 0 : allActiveCAs.size());
                    sLogger.v(
                            String.format(
                                    "Collected %s active CAs from device", numberOfCAsCollected));
                    Tracing.endAsyncSection(Tracing.GET_BUYERS_CA, traceCookie);
                    return allActiveCAs;
                });
    }

    private List<DBCustomAudience> getFilteredCustomAudiences(
            @NonNull final List<DBCustomAudience> dbCustomAudiences) {
        int tracingCookie = Tracing.beginAsyncSection(Tracing.GET_FILTERED_BUYERS_CA);
        List<DBCustomAudience> filteredCustomAudiences =
                dbCustomAudiences.stream()
                        .filter(
                                AuctionServerCustomAudienceFilterer
                                        ::isValidCustomAudienceForServerSideAuction)
                        .collect(Collectors.toList());
        sLogger.v(
                String.format(
                        "After auction server filtering : %s active CAs from device",
                        filteredCustomAudiences.size()));
        if (mEnableAdFilter) {
            filteredCustomAudiences = mAdFilterer.filterCustomAudiences(filteredCustomAudiences);
            sLogger.v(
                    String.format(
                            "After ad filtering : %s active CAs from device",
                            filteredCustomAudiences.size()));
        }
        Tracing.endAsyncSection(Tracing.GET_FILTERED_BUYERS_CA, tracingCookie);
        return filteredCustomAudiences;
    }

    private BuyerInput.CustomAudience buildCustomAudienceProtoFrom(
            DBCustomAudience customAudience) {
        BuyerInput.CustomAudience.Builder customAudienceBuilder =
                BuyerInput.CustomAudience.newBuilder();

        customAudienceBuilder
                .setName(customAudience.getName())
                .setOwner(customAudience.getOwner())
                .setUserBiddingSignals(getUserBiddingSignals(customAudience))
                .addAllBiddingSignalsKeys(getTrustedBiddingSignalKeys(customAudience));

        if (shouldIncludeAds(customAudience)) {
            customAudienceBuilder.addAllAdRenderIds(getAdRenderIds(customAudience));
        }
        return customAudienceBuilder.build();
    }

    private boolean shouldIncludeAds(DBCustomAudience customAudience) {
        return !(mEnableOmitAds
                && ((customAudience.getAuctionServerRequestFlags()
                                & FLAG_AUCTION_SERVER_REQUEST_OMIT_ADS)
                        != 0));
    }

    private ListenableFuture<Map<AdTechIdentifier, DBEncodedPayload>>
            getAllEncodedProtectedSignals() {
        // If the feature flag is turned off we short circuit, and return empty signals
        if (!mEnableProtectedSignals) {
            return Futures.immediateFuture(Collections.emptyMap());
        }
        int traceCookie = Tracing.beginAsyncSection(Tracing.GET_BUYERS_PS);
        return mBackgroundExecutorService.submit(
                () -> {
                    List<DBEncodedPayload> allBuyerSignals =
                            mEncodedSignalsDao.getAllEncodedPayloads();
                    int numberOfSignalsCollected =
                            (Objects.isNull(allBuyerSignals) ? 0 : allBuyerSignals.size());
                    sLogger.v(
                            String.format(
                                    "Collected %s signals from device", numberOfSignalsCollected));
                    Tracing.endAsyncSection(Tracing.GET_BUYERS_PS, traceCookie);
                    return allBuyerSignals.stream()
                            .collect(
                                    Collectors.toMap(
                                            DBEncodedPayload::getBuyer, Function.identity()));
                });
    }

    private ProtectedAppSignals buildProtectedSignalsProtoFrom(
            DBEncodedPayload dbEncodedSignalsPayload) {
        ProtectedAppSignals.Builder protectedSignalsBuilder = ProtectedAppSignals.newBuilder();

        return protectedSignalsBuilder
                .setAppInstallSignals(
                        ByteString.copyFrom(dbEncodedSignalsPayload.getEncodedPayload()))
                .setEncodingVersion(dbEncodedSignalsPayload.getVersion())
                .build();
    }

    private List<String> getTrustedBiddingSignalKeys(@NonNull DBCustomAudience customAudience) {
        List<String> biddingSignalKeys = customAudience.getTrustedBiddingData().getKeys();
        // If the bidding signal keys is just the CA name, we don't need to pass it to the server.
        if (biddingSignalKeys.size() == 1
                && customAudience.getName().equals(biddingSignalKeys.get(0))) {
            return ImmutableList.of();
        }

        // Remove the CA name from the bidding signal keys list to save space.
        biddingSignalKeys.remove(customAudience.getName());
        return biddingSignalKeys;
    }

    private String getUserBiddingSignals(DBCustomAudience customAudience) {
        return customAudience.getUserBiddingSignals() == null
                ? EMPTY_USER_BIDDING_SIGNALS
                : customAudience.getUserBiddingSignals().toString();
    }

    private List<String> getAdRenderIds(DBCustomAudience dbCustomAudience) {
        return dbCustomAudience.getAds().stream()
                .filter(ad -> !Strings.isNullOrEmpty(ad.getAdRenderId()))
                .map(ad -> ad.getAdRenderId())
                .collect(Collectors.toList());
    }
}
