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
import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;
import static android.adservices.common.AdServicesStatusUtils.STATUS_UNSET;

import android.adservices.adselection.PersistAdSelectionResultCallback;
import android.adservices.adselection.PersistAdSelectionResultInput;
import android.adservices.adselection.PersistAdSelectionResultResponse;
import android.adservices.adselection.ReportEventRequest;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.FledgeErrorResponse;
import android.adservices.exceptions.AdServicesException;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresApi;
import android.net.Uri;
import android.os.Build;
import android.os.RemoteException;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.adselection.datahandlers.AdSelectionInitialization;
import com.android.adservices.data.adselection.datahandlers.AdSelectionResultBidAndUri;
import com.android.adservices.data.adselection.datahandlers.RegisteredAdInteraction;
import com.android.adservices.data.adselection.datahandlers.ReportingData;
import com.android.adservices.data.adselection.datahandlers.WinningCustomAudience;
import com.android.adservices.data.common.DBAdData;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.service.Flags;
import com.android.adservices.service.adselection.encryption.ObliviousHttpEncryptor;
import com.android.adservices.service.common.AdSelectionServiceFilter;
import com.android.adservices.service.common.AdTechUriValidator;
import com.android.adservices.service.common.Throttler;
import com.android.adservices.service.common.ValidatorUtil;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.exception.FilterException;
import com.android.adservices.service.kanon.KAnonMessageEntity;
import com.android.adservices.service.kanon.KAnonSignJoinFactory;
import com.android.adservices.service.kanon.KAnonSignJoinManager;
import com.android.adservices.service.profiling.Tracing;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.AuctionResult;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.WinReportingUrls;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerUtil;
import com.android.adservices.service.stats.AdServicesStatsLog;
import com.android.adservices.service.stats.AdsRelevanceExecutionLogger;
import com.android.adservices.service.stats.DestinationRegisteredBeaconsReportedStats;
import com.android.internal.annotations.VisibleForTesting;

import com.google.auto.value.AutoValue;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.google.protobuf.InvalidProtocolBufferException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/** Runner class for ProcessAdSelectionResultRunner service */
// TODO(b/269798827): Enable for R.
@RequiresApi(Build.VERSION_CODES.S)
public class PersistAdSelectionResultRunner {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    @VisibleForTesting
    static final String PERSIST_AD_SELECTION_RESULT_TIMED_OUT =
            "PersistAdSelectionResult exceeded allowed time limit";

    @VisibleForTesting
    static final String BUYER_WIN_REPORTING_URI_FIELD_NAME = "buyer win reporting uri";

    @VisibleForTesting
    static final String SELLER_WIN_REPORTING_URI_FIELD_NAME = "seller win reporting uri";

    private static final String BUYER_INTERACTION_REPORTING_URI_FIELD_NAME =
            "buyer interaction reporting uri";
    private static final String SELLER_INTERACTION_REPORTING_URI_FIELD_NAME =
            "seller interaction reporting uri";
    private static final String SHA256 = "SHA-256";

    @NonNull private final ObliviousHttpEncryptor mObliviousHttpEncryptor;
    @NonNull private final AdSelectionEntryDao mAdSelectionEntryDao;
    @NonNull private final CustomAudienceDao mCustomAudienceDao;
    @NonNull private final AdSelectionServiceFilter mAdSelectionServiceFilter;
    @NonNull private final ListeningExecutorService mBackgroundExecutorService;
    @NonNull private final ListeningExecutorService mLightweightExecutorService;
    private final int mCallerUid;
    @NonNull private final ScheduledThreadPoolExecutor mScheduledExecutorService;
    @NonNull private final DevContext mDevContext;
    private final long mOverallTimeout;
    // TODO(b/291680065): Remove when owner field is returned from B&A
    private final boolean mForceSearchOnAbsentOwner;
    @NonNull private final Flags mFlags;
    @NonNull private final AdServicesLogger mAdServicesLogger;

    private ReportingRegistrationLimits mReportingLimits;
    @NonNull private AuctionServerDataCompressor mDataCompressor;
    @NonNull private AuctionServerPayloadExtractor mPayloadExtractor;
    @NonNull private AdCounterHistogramUpdater mAdCounterHistogramUpdater;

    @NonNull private AuctionResultValidator mAuctionResultValidator;


    @NonNull private final AdsRelevanceExecutionLogger mAdsRelevanceExecutionLogger;
    @NonNull KAnonSignJoinFactory mKAnonSignJoinFactory;

    public PersistAdSelectionResultRunner(
            @NonNull final ObliviousHttpEncryptor obliviousHttpEncryptor,
            @NonNull final AdSelectionEntryDao adSelectionEntryDao,
            @NonNull final CustomAudienceDao customAudienceDao,
            @NonNull final AdSelectionServiceFilter adSelectionServiceFilter,
            @NonNull final ExecutorService backgroundExecutorService,
            @NonNull final ExecutorService lightweightExecutorService,
            @NonNull final ScheduledThreadPoolExecutor scheduledExecutorService,
            final int callerUid,
            @NonNull final DevContext devContext,
            final long overallTimeout,
            final boolean forceContinueOnAbsentOwner,
            @NonNull final ReportingRegistrationLimits reportingLimits,
            @NonNull final AdCounterHistogramUpdater adCounterHistogramUpdater,
            @NonNull final AuctionResultValidator auctionResultValidator,
            @NonNull final Flags flags,
            @NonNull final AdServicesLogger adServicesLogger,
            @NonNull final AdsRelevanceExecutionLogger adsRelevanceExecutionLogger,
            @NonNull final KAnonSignJoinFactory kAnonSignJoinFactory) {
        Objects.requireNonNull(obliviousHttpEncryptor);
        Objects.requireNonNull(adSelectionEntryDao);
        Objects.requireNonNull(customAudienceDao);
        Objects.requireNonNull(adSelectionServiceFilter);
        Objects.requireNonNull(backgroundExecutorService);
        Objects.requireNonNull(lightweightExecutorService);
        Objects.requireNonNull(scheduledExecutorService);
        Objects.requireNonNull(devContext);
        Objects.requireNonNull(reportingLimits);
        Objects.requireNonNull(adCounterHistogramUpdater);
        Objects.requireNonNull(auctionResultValidator);
        Objects.requireNonNull(flags);
        Objects.requireNonNull(adServicesLogger);
        Objects.requireNonNull(adsRelevanceExecutionLogger);
        Objects.requireNonNull(kAnonSignJoinFactory);

        mObliviousHttpEncryptor = obliviousHttpEncryptor;
        mAdSelectionEntryDao = adSelectionEntryDao;
        mCustomAudienceDao = customAudienceDao;
        mAdSelectionServiceFilter = adSelectionServiceFilter;
        mBackgroundExecutorService = MoreExecutors.listeningDecorator(backgroundExecutorService);
        mLightweightExecutorService = MoreExecutors.listeningDecorator(lightweightExecutorService);
        mScheduledExecutorService = scheduledExecutorService;
        mCallerUid = callerUid;
        mDevContext = devContext;
        mOverallTimeout = overallTimeout;
        mForceSearchOnAbsentOwner = forceContinueOnAbsentOwner;
        mReportingLimits = reportingLimits;
        mAdCounterHistogramUpdater = adCounterHistogramUpdater;
        mAuctionResultValidator = auctionResultValidator;
        mFlags = flags;
        mAdServicesLogger = adServicesLogger;
        mAdsRelevanceExecutionLogger = adsRelevanceExecutionLogger;
        mKAnonSignJoinFactory = kAnonSignJoinFactory;
    }

    /** Orchestrates PersistAdSelectionResultRunner process. */
    public void run(
            @NonNull PersistAdSelectionResultInput inputParams,
            @NonNull PersistAdSelectionResultCallback callback) {
        Objects.requireNonNull(inputParams);
        Objects.requireNonNull(callback);

        int apiName =
                AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__PERSIST_AD_SELECTION_RESULT;
        long adSelectionId = inputParams.getAdSelectionId();

        try {
            ListenableFuture<Void> filteredRequest =
                    Futures.submit(
                            () -> {
                                try {
                                    sLogger.v(
                                            "Starting filtering for PersistAdSelectionResultRunner"
                                                    + " API.");
                                    mAdSelectionServiceFilter.filterRequest(
                                            inputParams.getSeller(),
                                            inputParams.getCallerPackageName(),
                                            false,
                                            true,
                                            mCallerUid,
                                            apiName,
                                            Throttler.ApiKey.FLEDGE_API_PERSIST_AD_SELECTION_RESULT,
                                            mDevContext);
                                    validateSellerAndCallerPackageName(inputParams, adSelectionId);
                                } finally {
                                    sLogger.v("Completed filtering.");
                                }
                            },
                            mLightweightExecutorService);

            ListenableFuture<AuctionResult> getAdSelectionDataResult =
                    FluentFuture.from(filteredRequest)
                            .transformAsync(
                                    ignoredVoid ->
                                            orchestratePersistAdSelectionResultRunner(inputParams),
                                    mLightweightExecutorService);

            Futures.addCallback(
                    getAdSelectionDataResult,
                    new FutureCallback<>() {
                        @Override
                        public void onSuccess(AuctionResult result) {
                            Uri adRenderUri =
                                    (result.getIsChaff())
                                            ? Uri.EMPTY
                                            : Uri.parse(result.getAdRenderUrl());
                            notifySuccessToCaller(adRenderUri, adSelectionId, callback);
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
                                notifyEmptySuccessToCaller(callback, adSelectionId);
                            } else {
                                if (t.getCause() instanceof AdServicesException) {
                                    notifyFailureToCaller(t.getCause(), callback);
                                } else {
                                    notifyFailureToCaller(t, callback);
                                }
                            }
                        }
                    },
                    mLightweightExecutorService);

        } catch (Throwable t) {
            sLogger.v("PersistAdSelectionResult fails fast with exception %s.", t.toString());
            notifyFailureToCaller(t, callback);
        }
    }

    private void makeKAnonSignJoin(AuctionResult auctionResult, long adSelectionId) {
        if (mFlags.getFledgeKAnonSignJoinFeatureEnabled()) {
            ListenableFuture<Void> signJoinFuture =
                    Futures.submitAsync(
                            () -> {
                                try {
                                    List<KAnonMessageEntity> messageEntities =
                                            getKAnonEntitiesFromAuctionResult(
                                                    auctionResult, adSelectionId);
                                    KAnonSignJoinManager kAnonSignJoinManager =
                                            mKAnonSignJoinFactory.getKAnonSignJoinManager();
                                    kAnonSignJoinManager.processNewMessages(messageEntities);
                                } catch (Throwable t) {
                                    sLogger.d("Error while processing new messages for KAnon");
                                }
                                return Futures.immediateVoidFuture();
                            },
                            mBackgroundExecutorService);
        } else {
            sLogger.d("KAnon Sign Join feature is disabled");
            mAdServicesLogger.logKAnonSignJoinStatus();
        }
    }

    private ListenableFuture<AuctionResult> orchestratePersistAdSelectionResultRunner(
            PersistAdSelectionResultInput request) {
        int orchestrationCookie =
                Tracing.beginAsyncSection(Tracing.ORCHESTRATE_PERSIST_AD_SELECTION_RESULT);
        long adSelectionId = request.getAdSelectionId();
        AdTechIdentifier seller = request.getSeller();
        return decryptBytes(request)
                .transform(this::parseAdSelectionResult, mLightweightExecutorService)
                .transform(
                        auctionResult -> {
                            if (auctionResult.getError().getCode() != 0) {
                                String err =
                                        String.format(
                                                Locale.ENGLISH,
                                                "AuctionResult has an error: %s",
                                                auctionResult.getError().getMessage());
                                sLogger.e(err);
                                throw new IllegalArgumentException(err);
                            } else if (auctionResult.getIsChaff()) {
                                sLogger.v("Result is chaff, truncating persistAdSelectionResult");
                            } else if (auctionResult.getAdType() == AuctionResult.AdType.UNKNOWN) {
                                String err = "AuctionResult type is unknown";
                                sLogger.e(err);
                                throw new IllegalArgumentException(err);
                            } else {
                                makeKAnonSignJoin(auctionResult, adSelectionId);
                                validateAuctionResult(auctionResult);
                                DBAdData winningAd = fetchWinningAd(auctionResult);
                                int persistingCookie =
                                        Tracing.beginAsyncSection(Tracing.PERSIST_AUCTION_RESULTS);
                                persistAuctionResults(
                                        auctionResult, winningAd, adSelectionId, seller);
                                persistAdInteractionKeysAndUrls(
                                        auctionResult, adSelectionId, seller);
                                Tracing.endAsyncSection(
                                        Tracing.PERSIST_AUCTION_RESULTS, persistingCookie);
                            }
                            return auctionResult;
                        },
                        mBackgroundExecutorService)
                .transform(
                        validResult -> {
                            Tracing.endAsyncSection(
                                    Tracing.ORCHESTRATE_PERSIST_AD_SELECTION_RESULT,
                                    orchestrationCookie);
                            return validResult;
                        },
                        mLightweightExecutorService)
                .withTimeout(mOverallTimeout, TimeUnit.MILLISECONDS, mScheduledExecutorService)
                .catching(
                        TimeoutException.class,
                        this::handleTimeoutError,
                        mLightweightExecutorService);
    }

    @NonNull
    private DBAdData fetchWinningAd(AuctionResult auctionResult) {
        DBAdData winningAd;
        if (auctionResult.getAdType() == AuctionResult.AdType.REMARKETING_AD) {
            winningAd = fetchRemarketingAd(auctionResult);
        } else if (auctionResult.getAdType() == AuctionResult.AdType.APP_INSTALL_AD) {
            winningAd = fetchAppInstallAd(auctionResult);
        } else {
            String err =
                    String.format(
                            Locale.ENGLISH,
                            "The value: '%s' is not defined in AdType proto!",
                            auctionResult.getAdType().getNumber());
            sLogger.e(err);
            throw new IllegalArgumentException(err);
        }
        return winningAd;
    }

    private List<KAnonMessageEntity> getKAnonEntitiesFromAuctionResult(
            AuctionResult auctionResult, long adSelectionId) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance(SHA256);
        String adRenderUrl = auctionResult.getAdRenderUrl();
        byte[] digestedBytes = md.digest(adRenderUrl.getBytes(StandardCharsets.UTF_8));
        KAnonMessageEntity kAnonMessageEntity =
                KAnonMessageEntity.builder()
                        .setStatus(KAnonMessageEntity.KanonMessageEntityStatus.NOT_PROCESSED)
                        .setAdSelectionId(adSelectionId)
                        .setHashSet(
                                Base64.getUrlEncoder()
                                        .withoutPadding()
                                        .encodeToString(digestedBytes))
                        .build();
        return List.of(kAnonMessageEntity);
    }

    @NonNull
    private DBAdData fetchRemarketingAd(AuctionResult auctionResult) {
        Uri adRenderUri = Uri.parse(auctionResult.getAdRenderUrl());
        AdTechIdentifier buyer = AdTechIdentifier.fromString(auctionResult.getBuyer());
        String name = auctionResult.getCustomAudienceName();
        String owner = auctionResult.getCustomAudienceOwner();
        sLogger.v(
                "Fetching winning CA with buyer='%s', name='%s', owner='%s', render uri='%s'",
                buyer, name, owner, adRenderUri);

        DBAdData winningAd;
        if (!owner.isEmpty()) {
            DBCustomAudience winningCustomAudience =
                    mCustomAudienceDao.getCustomAudienceByPrimaryKey(owner, buyer, name);

            if (Objects.isNull(winningCustomAudience)) {
                String err =
                        String.format(
                                Locale.ENGLISH,
                                "Custom Audience is not found by given owner='%s', "
                                        + "buyer='%s', name='%s'",
                                owner,
                                buyer,
                                name);
                sLogger.e(err);
                throw new IllegalArgumentException(err);
            }

            if (Objects.isNull(winningCustomAudience.getAds())
                    || winningCustomAudience.getAds().isEmpty()) {
                String err = "Custom Audience has a null or empty list of ads";
                sLogger.v(err);
                throw new IllegalArgumentException(err);
            }

            winningAd =
                    winningCustomAudience.getAds().stream()
                            .filter(ad -> ad.getRenderUri().equals(adRenderUri))
                            .findFirst()
                            .orElse(null);
        } else {
            // TODO(b/291680065): Remove this search logic across CAs when B&A returns 'owner' field
            sLogger.v("Owner is not present in the AuctionResult.");
            if (mForceSearchOnAbsentOwner) {
                sLogger.v("forceSearchOnAbsentOwner is true. Searching using ad render uri.");
                winningAd =
                        mCustomAudienceDao.getCustomAudiencesForBuyerAndName(buyer, name).stream()
                                .filter(e -> e.getAds() != null && !e.getAds().isEmpty())
                                .flatMap(e -> e.getAds().stream())
                                .filter(ad -> ad.getRenderUri().equals(adRenderUri))
                                .findFirst()
                                .orElse(null);
                sLogger.v("Winning ad found: %s", winningAd);
            } else {
                sLogger.v("Return a placeholder AdData");
                winningAd =
                        new DBAdData.Builder()
                                .setMetadata("")
                                .setRenderUri(adRenderUri)
                                .setAdCounterKeys(Collections.emptySet())
                                .build();
            }
        }

        if (Objects.isNull(winningAd)) {
            String err = "Winning ad is not found in custom audience's list of ads";
            sLogger.v(err);
            throw new IllegalArgumentException(err);
        }

        return winningAd;
    }

    @NonNull
    private DBAdData fetchAppInstallAd(AuctionResult auctionResult) {
        Uri adRenderUri = Uri.parse(auctionResult.getAdRenderUrl());
        return new DBAdData.Builder()
                .setMetadata("")
                .setRenderUri(adRenderUri)
                .setAdCounterKeys(Collections.emptySet())
                .build();
    }

    private void validateAuctionResult(AuctionResult auctionResult) {
        mAuctionResultValidator.validate(auctionResult);
    }

    @Nullable
    private AuctionResult handleTimeoutError(TimeoutException e) {
        sLogger.e(e, PERSIST_AD_SELECTION_RESULT_TIMED_OUT);
        throw new UncheckedTimeoutException(PERSIST_AD_SELECTION_RESULT_TIMED_OUT);
    }

    private FluentFuture<byte[]> decryptBytes(PersistAdSelectionResultInput request) {
        int traceCookie = Tracing.beginAsyncSection(Tracing.OHTTP_DECRYPT_BYTES);
        byte[] encryptedAuctionResult = request.getAdSelectionResult();
        long adSelectionId = request.getAdSelectionId();

        return FluentFuture.from(
                mLightweightExecutorService.submit(
                        () -> {
                            sLogger.v("Decrypting auction result data for :" + adSelectionId);
                            byte[] decryptedBytes =
                                    mObliviousHttpEncryptor.decryptBytes(
                                            encryptedAuctionResult, adSelectionId);
                            Tracing.endAsyncSection(Tracing.OHTTP_DECRYPT_BYTES, traceCookie);
                            return decryptedBytes;
                        }));
    }

    private AuctionResult parseAdSelectionResult(byte[] resultBytes) {
        int traceCookie = Tracing.beginAsyncSection(Tracing.PARSE_AD_SELECTION_RESULT);
        initializeDataCompressor(resultBytes);
        initializePayloadExtractor(resultBytes);

        sLogger.v("Applying formatter on AuctionResult bytes");
        AuctionServerPayloadUnformattedData unformattedResult =
                mPayloadExtractor.extract(AuctionServerPayloadFormattedData.create(resultBytes));

        sLogger.v("Applying decompression on AuctionResult bytes");
        AuctionServerDataCompressor.UncompressedData uncompressedResult =
                mDataCompressor.decompress(
                        AuctionServerDataCompressor.CompressedData.create(
                                unformattedResult.getData()));

        AuctionResult auctionResult = composeAuctionResult(uncompressedResult);
        Tracing.endAsyncSection(Tracing.PARSE_AD_SELECTION_RESULT, traceCookie);
        return auctionResult;
    }

    private void initializeDataCompressor(@NonNull byte[] resultBytes) {
        Objects.requireNonNull(resultBytes, "AdSelectionResult bytes cannot be null");

        byte metaInfoByte = resultBytes[0];
        int version = AuctionServerPayloadFormattingUtil.extractCompressionVersion(metaInfoByte);
        mDataCompressor = AuctionServerDataCompressorFactory.getDataCompressor(version);
    }

    private void initializePayloadExtractor(byte[] resultBytes) {
        Objects.requireNonNull(resultBytes, "AdSelectionResult bytes cannot be null");

        byte metaInfoByte = resultBytes[0];
        int version = AuctionServerPayloadFormattingUtil.extractFormatterVersion(metaInfoByte);
        mPayloadExtractor =
                AuctionServerPayloadFormatterFactory.createPayloadExtractor(
                        version, mAdServicesLogger);
    }

    private AuctionResult composeAuctionResult(
            AuctionServerDataCompressor.UncompressedData uncompressedData) {
        try {
            AuctionResult result = AuctionResult.parseFrom(uncompressedData.getData());
            logAuctionResult(result);
            return result;
        } catch (InvalidProtocolBufferException ex) {
            sLogger.e("Error during parsing AuctionResult proto from byte[]");
            throw new RuntimeException(ex);
        }
    }

    private void persistAuctionResults(
            AuctionResult auctionResult,
            DBAdData winningAd,
            long adSelectionId,
            AdTechIdentifier seller) {
        final WinReportingUrls winReportingUrls = auctionResult.getWinReportingUrls();
        final Uri buyerReportingUrl =
                validateAdTechUriAndReturnEmptyIfInvalid(
                        ValidatorUtil.AD_TECH_ROLE_BUYER,
                        auctionResult.getBuyer(),
                        BUYER_WIN_REPORTING_URI_FIELD_NAME,
                        Uri.parse(winReportingUrls.getBuyerReportingUrls().getReportingUrl()));
        final Uri sellerReportingUrl =
                validateAdTechUriAndReturnEmptyIfInvalid(
                        ValidatorUtil.AD_TECH_ROLE_SELLER,
                        seller.toString(),
                        SELLER_WIN_REPORTING_URI_FIELD_NAME,
                        Uri.parse(
                                winReportingUrls
                                        .getTopLevelSellerReportingUrls()
                                        .getReportingUrl()));
        AdSelectionInitialization adSelectionInitialization =
                mAdSelectionEntryDao.getAdSelectionInitializationForId(adSelectionId);
        AdTechIdentifier buyer = AdTechIdentifier.fromString(auctionResult.getBuyer());
        WinningCustomAudience winningCustomAudience =
                WinningCustomAudience.builder()
                        .setOwner(auctionResult.getCustomAudienceOwner())
                        .setName(auctionResult.getCustomAudienceName())
                        .setAdCounterKeys(winningAd.getAdCounterKeys())
                        .build();
        ReportingData reportingData =
                ReportingData.builder()
                        .setBuyerWinReportingUri(buyerReportingUrl)
                        .setSellerWinReportingUri(sellerReportingUrl)
                        .build();
        AdSelectionResultBidAndUri resultBidAndUri =
                AdSelectionResultBidAndUri.builder()
                        .setAdSelectionId(adSelectionId)
                        .setWinningAdBid(auctionResult.getBid())
                        .setWinningAdRenderUri(Uri.parse(auctionResult.getAdRenderUrl()))
                        .build();
        sLogger.v("Persisting ad selection results for id: %s", adSelectionId);
        sLogger.v("AdSelectionResultBidAndUri: %s", resultBidAndUri);
        sLogger.v("WinningCustomAudience: %s", winningCustomAudience);
        sLogger.v("ReportingData: %s", reportingData);
        mAdSelectionEntryDao.persistAdSelectionResultForCustomAudience(
                adSelectionId, resultBidAndUri, buyer, winningCustomAudience);
        mAdSelectionEntryDao.persistReportingData(adSelectionId, reportingData);

        try {
            mAdCounterHistogramUpdater.updateWinHistogram(
                    buyer, adSelectionInitialization, winningCustomAudience);
        } catch (Exception exception) {
            // Frequency capping is not crucial enough to crash the entire process
            sLogger.w(
                    exception,
                    "Error encountered updating ad counter histogram with win event; "
                            + "continuing ad selection persistence");
        }
    }

    /**
     * Iterates through each interaction keys and commits it to the {@code
     * registered_ad_interactions} table
     *
     * <p>Note: For system health purposes, we will enforce these limitations: 1. We only commit up
     * to a maximum of {@link com.android.adservices.service.Flags
     * #getReportImpressionMaxRegisteredAdBeaconsTotalCount()} entries to the database. 2. We will
     * not commit an entry to the database if {@link
     * InteractionUriRegistrationInfo#getInteractionKey()} is larger than {@link
     * com.android.adservices.service.Flags
     * #getFledgeReportImpressionRegisteredAdBeaconsMaxInteractionKeySizeB()} or if {@link
     * InteractionUriRegistrationInfo#getInteractionReportingUri()} is larger than {@link
     * com.android.adservices.service.Flags
     * #getFledgeReportImpressionMaxInteractionReportingUriSizeB()}
     */
    private void persistAdInteractionKeysAndUrls(
            AuctionResult auctionResult, long adSelectionId, AdTechIdentifier seller) {
        final WinReportingUrls winReportingUrls = auctionResult.getWinReportingUrls();
        final Map<String, String> attemptedBuyerInteractionReportingUrls =
                winReportingUrls.getBuyerReportingUrls().getInteractionReportingUrls();
        final Map<String, String> attemptedSellerInteractionReportingUrls =
                winReportingUrls.getTopLevelSellerReportingUrls().getInteractionReportingUrls();

        final Map<String, Uri> buyerInteractionReportingUrls =
                filterInvalidInteractionUri(
                        ValidatorUtil.AD_TECH_ROLE_BUYER,
                        auctionResult.getBuyer(),
                        BUYER_INTERACTION_REPORTING_URI_FIELD_NAME,
                        attemptedBuyerInteractionReportingUrls);
        final Map<String, Uri> sellerInteractionReportingUrls =
                filterInvalidInteractionUri(
                        ValidatorUtil.AD_TECH_ROLE_SELLER,
                        seller.toString(),
                        SELLER_INTERACTION_REPORTING_URI_FIELD_NAME,
                        attemptedSellerInteractionReportingUrls);
        sLogger.v("Valid buyer interaction urls: %s", buyerInteractionReportingUrls);
        persistAdInteractionKeysAndUrls(
                buyerInteractionReportingUrls,
                adSelectionId,
                ReportEventRequest.FLAG_REPORTING_DESTINATION_BUYER);
        sLogger.v("Valid seller interaction urls: %s", sellerInteractionReportingUrls);
        persistAdInteractionKeysAndUrls(
                sellerInteractionReportingUrls,
                adSelectionId,
                ReportEventRequest.FLAG_REPORTING_DESTINATION_SELLER);

        if (mFlags.getFledgeBeaconReportingMetricsEnabled()) {
            int totalNumRegisteredAdInteractions =
                    (int) mAdSelectionEntryDao.getTotalNumRegisteredAdInteractions();
            long maxInteractionKeySize = mReportingLimits.getMaxInteractionKeySize();

            mAdServicesLogger.logDestinationRegisteredBeaconsReportedStats(
                    DestinationRegisteredBeaconsReportedStats.builder()
                            .setBeaconReportingDestinationType(
                                    ReportEventRequest.FLAG_REPORTING_DESTINATION_BUYER)
                            .setAttemptedRegisteredBeacons(
                                    attemptedBuyerInteractionReportingUrls.size())
                            .setAttemptedKeySizesRangeType(
                                    DestinationRegisteredBeaconsReportedStats
                                            .getInteractionKeySizeRangeTypeList(
                                                    attemptedBuyerInteractionReportingUrls.keySet(),
                                                    maxInteractionKeySize))
                            .setTableNumRows(totalNumRegisteredAdInteractions)
                            .build());

            mAdServicesLogger.logDestinationRegisteredBeaconsReportedStats(
                    DestinationRegisteredBeaconsReportedStats.builder()
                            .setBeaconReportingDestinationType(
                                    ReportEventRequest.FLAG_REPORTING_DESTINATION_SELLER)
                            .setAttemptedRegisteredBeacons(
                                    attemptedSellerInteractionReportingUrls.size())
                            .setAttemptedKeySizesRangeType(
                                    DestinationRegisteredBeaconsReportedStats
                                            .getInteractionKeySizeRangeTypeList(
                                                    attemptedSellerInteractionReportingUrls
                                                            .keySet(),
                                                    maxInteractionKeySize))
                            .setTableNumRows(totalNumRegisteredAdInteractions)
                            .build());
        }
    }

    private void persistAdInteractionKeysAndUrls(
            @NonNull Map<String, Uri> validInteractionKeyUriMap,
            long adSelectionId,
            @ReportEventRequest.ReportingDestination int reportingDestination) {
        final long maxTableSize = mReportingLimits.getMaxRegisteredAdBeaconsTotalCount();
        final long maxNumRowsPerDestination =
                mReportingLimits.getMaxRegisteredAdBeaconsPerAdTechCount();

        List<RegisteredAdInteraction> adInteractionsToRegister = new ArrayList<>();

        for (Map.Entry<String, Uri> entry : validInteractionKeyUriMap.entrySet()) {
            String interactionKey = entry.getKey();
            Uri interactionUri = entry.getValue();

            RegisteredAdInteraction registeredAdInteraction =
                    RegisteredAdInteraction.builder()
                            .setInteractionKey(interactionKey)
                            .setInteractionReportingUri(interactionUri)
                            .build();
            sLogger.v(
                    "Adding %s into the list of interaction data to be persisted for destination:"
                            + " %s.",
                    registeredAdInteraction, reportingDestination);
            adInteractionsToRegister.add(registeredAdInteraction);
        }

        if (adInteractionsToRegister.isEmpty()) {
            sLogger.v(
                    "No interaction reporting data to persist for destination: %s.",
                    reportingDestination);
            return;
        }

        mAdSelectionEntryDao.safelyInsertRegisteredAdInteractionsForDestination(
                adSelectionId,
                reportingDestination,
                adInteractionsToRegister,
                maxTableSize,
                maxNumRowsPerDestination);
    }

    private Uri validateAdTechUriAndReturnEmptyIfInvalid(
            @NonNull String adTechRole,
            @NonNull String adTechIdentifier,
            @NonNull String fieldName,
            @NonNull Uri adTechUri) {
        final String className = AuctionResult.class.getName();
        final AdTechUriValidator adTechUriValidator =
                new AdTechUriValidator(adTechRole, adTechIdentifier, className, fieldName);
        try {
            adTechUriValidator.validate(adTechUri);
            return adTechUri;
        } catch (IllegalArgumentException illegalArgumentException) {
            sLogger.w(illegalArgumentException.getMessage());
            return Uri.EMPTY;
        }
    }

    private Map<String, Uri> filterInvalidInteractionUri(
            @NonNull String adTechRole,
            @NonNull String adTechIdentifier,
            @NonNull String fieldName,
            @NonNull Map<String, String> interactionReportingKeyUriMap) {
        final long maxInteractionKeySize = mReportingLimits.getMaxInteractionKeySize();
        final long maxInteractionReportingUriSize =
                mReportingLimits.getMaxInteractionReportingUriSize();
        final String className = AuctionResult.class.getName();
        final AdTechUriValidator adTechUriValidator =
                new AdTechUriValidator(adTechRole, adTechIdentifier, className, fieldName);
        return interactionReportingKeyUriMap.entrySet().stream()
                .map(
                        entry -> {
                            String keyToValidate = entry.getKey();
                            Uri uriToValidate = Uri.parse(entry.getValue());
                            try {
                                adTechUriValidator.validate(uriToValidate);
                            } catch (IllegalArgumentException e) {
                                sLogger.v("Interaction data %s is invalid: %s", entry, e);
                                return null;
                            }
                            if (keyToValidate.getBytes(StandardCharsets.UTF_8).length
                                    > maxInteractionKeySize) {
                                sLogger.e(
                                        "InteractionKey %s size exceeds the maximum allowed! "
                                                + "Skipping this entry",
                                        keyToValidate);
                                return null;
                            }
                            if (uriToValidate.toString().getBytes(StandardCharsets.UTF_8).length
                                    > maxInteractionReportingUriSize) {
                                sLogger.e(
                                        "Interaction reporting uri %s size exceeds the "
                                                + "maximum allowed! Skipping this entry",
                                        uriToValidate);
                                return null;
                            }
                            return new AbstractMap.SimpleEntry<>(entry.getKey(), uriToValidate);
                        })
                .filter(Objects::nonNull) // Exclude null entries (invalid)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private void validateSellerAndCallerPackageName(
            PersistAdSelectionResultInput inputParams, long adSelectionId) {
        AdSelectionInitialization initializationData =
                mAdSelectionEntryDao.getAdSelectionInitializationForId(adSelectionId);
        if (Objects.isNull(initializationData)) {
            String err =
                    String.format(
                            Locale.ENGLISH,
                            "Initialization info cannot be found for the given ad selection id: %s",
                            adSelectionId);
            sLogger.e(err);
            throw new IllegalArgumentException(err);
        }

        AdTechIdentifier sellerInDB = initializationData.getSeller();
        AdTechIdentifier sellerInRequest = inputParams.getSeller();
        String callerInDB = initializationData.getCallerPackageName();
        String callerInRequest = inputParams.getCallerPackageName();

        if (!sellerInDB.equals(sellerInRequest) || !callerInDB.equals(callerInRequest)) {
            String err =
                    String.format(
                            Locale.ENGLISH,
                            "Initialization info in db (seller=%s, callerPackageName=%s) doesn't "
                                    + "match the request (seller=%s, callerPackageName=%s)",
                            sellerInDB,
                            callerInDB,
                            sellerInRequest,
                            callerInRequest);
            sLogger.e(err);
            throw new IllegalArgumentException(err);
        }
    }

    private void notifySuccessToCaller(
            Uri renderUri, long adSelectionId, PersistAdSelectionResultCallback callback) {
        int resultCode = STATUS_SUCCESS;
        try {
            callback.onSuccess(
                    new PersistAdSelectionResultResponse.Builder()
                            .setAdSelectionId(adSelectionId)
                            .setAdRenderUri(renderUri)
                            .build());
        } catch (RemoteException e) {
            sLogger.e(e, "Encountered exception during notifying PersistAdSelectionResultCallback");
            resultCode = STATUS_INTERNAL_ERROR;
        } finally {
            sLogger.v("Attempted notifying success");
            mAdsRelevanceExecutionLogger.endAdsRelevanceApi(resultCode);

        }
    }

    private void notifyEmptySuccessToCaller(
            @NonNull PersistAdSelectionResultCallback callback, long adSelectionId) {
        int resultCode = STATUS_SUCCESS;
        try {
            // TODO(b/288368908): Determine what is an appropriate empty response for revoked
            //  consent
            callback.onSuccess(
                    new PersistAdSelectionResultResponse.Builder()
                            .setAdSelectionId(adSelectionId)
                            .setAdRenderUri(Uri.EMPTY)
                            .build());
        } catch (RemoteException e) {
            sLogger.e(e, "Encountered exception during notifying PersistAdSelectionResultCallback");
            resultCode = STATUS_INTERNAL_ERROR;
        } finally {
            sLogger.v(
                    "Persist Ad Selection Result completed, attempted notifying success for a"
                            + " silent failure");
        }
    }

    private void notifyFailureToCaller(Throwable t, PersistAdSelectionResultCallback callback) {
        int resultCode = STATUS_UNSET;
        try {
            sLogger.e("Notify caller of error: " + t);
            resultCode = AdServicesLoggerUtil.getResultCodeFromException(t);

            FledgeErrorResponse selectionFailureResponse =
                    new FledgeErrorResponse.Builder()
                            .setErrorMessage("Error while persisting ad selection result")
                            .setStatusCode(resultCode)
                            .build();
            sLogger.e(t, "Ad Selection failure: ");
            callback.onFailure(selectionFailureResponse);
        } catch (RemoteException e) {
            sLogger.e(e, "Encountered exception during notifying PersistAdSelectionResultCallback");
            resultCode = STATUS_INTERNAL_ERROR;
        } finally {
            sLogger.v("Persist Ad Selection Result failed");
            mAdsRelevanceExecutionLogger.endAdsRelevanceApi(resultCode);
        }
    }

    private void logAuctionResult(AuctionResult auctionResult) {
        sLogger.v(
                "Decrypted AuctionResult proto: "
                        + "\nadRenderUrl: %s"
                        + "\ncustom audience name: %s"
                        + "\nbuyer: %s"
                        + "\nscore: %s"
                        + "\nbid: %s"
                        + "\nis_chaff: %s"
                        + "\nbuyer reporting url: %s"
                        + "\nseller reporting url: %s",
                auctionResult.getAdRenderUrl(),
                auctionResult.getCustomAudienceName(),
                auctionResult.getBuyer(),
                auctionResult.getScore(),
                auctionResult.getBid(),
                auctionResult.getIsChaff(),
                auctionResult.getWinReportingUrls().getBuyerReportingUrls().getReportingUrl(),
                auctionResult
                        .getWinReportingUrls()
                        .getTopLevelSellerReportingUrls()
                        .getReportingUrl());
    }

    @AutoValue
    abstract static class ReportingRegistrationLimits {
        /** MaxRegisteredAdBeaconsTotalCount */
        public abstract long getMaxRegisteredAdBeaconsTotalCount();

        /** MaxInteractionKeySize */
        public abstract long getMaxInteractionKeySize();

        /** MaxInteractionReportingUriSize */
        public abstract long getMaxInteractionReportingUriSize();

        /** MaxRegisteredAdBeaconsPerAdTechCount */
        public abstract long getMaxRegisteredAdBeaconsPerAdTechCount();

        @NonNull
        public static Builder builder() {
            return new AutoValue_PersistAdSelectionResultRunner_ReportingRegistrationLimits
                    .Builder();
        }

        @AutoValue.Builder
        abstract static class Builder {
            /** Sets MaxRegisteredAdBeaconsTotalCount */
            public abstract Builder setMaxRegisteredAdBeaconsTotalCount(
                    long maxRegisteredAdBeaconsTotalCount);

            /** Sets MaxInteractionKeySize */
            public abstract Builder setMaxInteractionKeySize(long maxInteractionKeySize);

            /** Sets MaxInteractionReportingUriSize */
            public abstract Builder setMaxInteractionReportingUriSize(
                    long maxInteractionReportingUriSize);

            /** Sets MaxRegisteredAdBeaconsPerAdTechCount */
            public abstract Builder setMaxRegisteredAdBeaconsPerAdTechCount(
                    long maxRegisteredAdBeaconsPerAdTechCount);

            /** Builds a {@link ReportingRegistrationLimits} */
            public abstract ReportingRegistrationLimits build();
        }
    }
}
