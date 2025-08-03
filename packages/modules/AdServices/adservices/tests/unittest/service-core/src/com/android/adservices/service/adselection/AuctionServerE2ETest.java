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

import static android.adservices.common.AdServicesStatusUtils.STATUS_INVALID_ARGUMENT;
import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;
import static android.adservices.common.KeyedFrequencyCapFixture.ONE_DAY_DURATION;
import static android.adservices.customaudience.CustomAudience.FLAG_AUCTION_SERVER_REQUEST_OMIT_ADS;

import static com.android.adservices.common.DBAdDataFixture.getValidDbAdDataNoFiltersBuilder;
import static com.android.adservices.data.adselection.EncryptionKeyConstants.EncryptionKeyType.ENCRYPTION_KEY_TYPE_AUCTION;
import static com.android.adservices.service.adselection.AdSelectionFromOutcomesE2ETest.BID_FLOOR_SELECTION_SIGNAL_TEMPLATE;
import static com.android.adservices.service.adselection.AdSelectionFromOutcomesE2ETest.SELECTION_WATERFALL_LOGIC_JS;
import static com.android.adservices.service.adselection.AdSelectionFromOutcomesE2ETest.SELECTION_WATERFALL_LOGIC_JS_PATH;
import static com.android.adservices.service.adselection.AdSelectionServiceImpl.AUCTION_SERVER_API_IS_NOT_AVAILABLE;
import static com.android.adservices.service.adselection.GetAdSelectionDataRunner.REVOKED_CONSENT_RANDOM_DATA_SIZE;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTest.sCallerMetadata;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyInt;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyLong;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.when;

import static com.google.common.util.concurrent.Futures.immediateFuture;
import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.adservices.adid.AdId;
import android.adservices.adselection.AdSelectionCallback;
import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionConfigFixture;
import android.adservices.adselection.AdSelectionFromOutcomesConfigFixture;
import android.adservices.adselection.AdSelectionFromOutcomesInput;
import android.adservices.adselection.AdSelectionResponse;
import android.adservices.adselection.AdSelectionService;
import android.adservices.adselection.AuctionEncryptionKeyFixture;
import android.adservices.adselection.GetAdSelectionDataCallback;
import android.adservices.adselection.GetAdSelectionDataInput;
import android.adservices.adselection.GetAdSelectionDataResponse;
import android.adservices.adselection.ObliviousHttpEncryptorWithSeedImpl;
import android.adservices.adselection.PersistAdSelectionResultCallback;
import android.adservices.adselection.PersistAdSelectionResultInput;
import android.adservices.adselection.PersistAdSelectionResultResponse;
import android.adservices.adselection.ReportEventRequest;
import android.adservices.adselection.ReportImpressionCallback;
import android.adservices.adselection.ReportImpressionInput;
import android.adservices.adselection.ReportInteractionCallback;
import android.adservices.adselection.ReportInteractionInput;
import android.adservices.adselection.UpdateAdCounterHistogramCallback;
import android.adservices.adselection.UpdateAdCounterHistogramInput;
import android.adservices.common.AdFilters;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CallingAppUidSupplierProcessImpl;
import android.adservices.common.CommonFixture;
import android.adservices.common.FledgeErrorResponse;
import android.adservices.common.FrequencyCapFilters;
import android.adservices.common.KeyedFrequencyCap;
import android.adservices.http.MockWebServerRule;
import android.content.Context;
import android.net.Uri;
import android.os.Process;
import android.os.RemoteException;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.FlakyTest;

import com.android.adservices.MockWebServerRuleFactory;
import com.android.adservices.common.AdServicesDeviceSupportedRule;
import com.android.adservices.common.DBAdDataFixture;
import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.common.WebViewSupportUtil;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.customaudience.DBCustomAudienceFixture;
import com.android.adservices.data.adselection.AdSelectionDatabase;
import com.android.adservices.data.adselection.AdSelectionDebugReportDao;
import com.android.adservices.data.adselection.AdSelectionDebugReportingDatabase;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.adselection.AdSelectionServerDatabase;
import com.android.adservices.data.adselection.AppInstallDao;
import com.android.adservices.data.adselection.DBEncryptionKey;
import com.android.adservices.data.adselection.EncryptionContextDao;
import com.android.adservices.data.adselection.EncryptionKeyDao;
import com.android.adservices.data.adselection.FrequencyCapDao;
import com.android.adservices.data.adselection.ProtectedServersEncryptionConfigDao;
import com.android.adservices.data.adselection.SharedStorageDatabase;
import com.android.adservices.data.adselection.datahandlers.ReportingData;
import com.android.adservices.data.common.DBAdData;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.CustomAudienceDatabase;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.signals.EncodedPayloadDao;
import com.android.adservices.data.signals.ProtectedSignalsDatabase;
import com.android.adservices.ohttp.ObliviousHttpGateway;
import com.android.adservices.ohttp.OhttpGatewayPrivateKey;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.adid.AdIdCacheManager;
import com.android.adservices.service.adselection.encryption.AdSelectionEncryptionKeyManager;
import com.android.adservices.service.adselection.encryption.ObliviousHttpEncryptor;
import com.android.adservices.service.adselection.encryption.ObliviousHttpEncryptorImpl;
import com.android.adservices.service.adselection.encryption.ProtectedServersEncryptionConfigManager;
import com.android.adservices.service.common.AdSelectionServiceFilter;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.common.RetryStrategyFactory;
import com.android.adservices.service.common.Throttler;
import com.android.adservices.service.common.cache.CacheProviderFactory;
import com.android.adservices.service.common.httpclient.AdServicesHttpClientResponse;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.devapi.DevContextFilter;
import com.android.adservices.service.exception.FilterException;
import com.android.adservices.service.js.JSScriptEngine;
import com.android.adservices.service.kanon.KAnonSignJoinFactory;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.AuctionResult;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.BuyerInput;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.ProtectedAuctionInput;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.WinReportingUrls;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.WinReportingUrls.ReportingUrls;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.AdServicesStatsLog;
import com.android.adservices.service.stats.GetAdSelectionDataApiCalledStats;
import com.android.adservices.service.stats.GetAdSelectionDataBuyerInputGeneratedStats;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.BaseEncoding;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mockwebserver.Dispatcher;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.RecordedRequest;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

public class AuctionServerE2ETest {
    private static final int COUNTDOWN_LATCH_LIMIT_SECONDS = 10;
    private static final int CALLER_UID = Process.myUid();
    private static final String CALLER_PACKAGE_NAME = CommonFixture.TEST_PACKAGE_NAME;
    private static final AdTechIdentifier SELLER = AdSelectionConfigFixture.SELLER;
    private static final AdTechIdentifier WINNER_BUYER = AdSelectionConfigFixture.BUYER;
    private static final AdTechIdentifier DIFFERENT_BUYER = AdSelectionConfigFixture.BUYER_2;
    private static final DBAdData WINNER_AD =
            DBAdDataFixture.getValidDbAdDataListByBuyerWithAdRenderId(WINNER_BUYER).get(0);
    private static final Uri WINNER_AD_RENDER_URI = WINNER_AD.getRenderUri();
    private static final Set<Integer> WINNER_AD_COUNTERS = WINNER_AD.getAdCounterKeys();
    private static final String BUYER_REPORTING_URI =
            CommonFixture.getUri(WINNER_BUYER, "/reporting").toString();
    private static final String SELLER_REPORTING_URI =
            CommonFixture.getUri(SELLER, "/reporting").toString();
    private static final String BUYER_INTERACTION_KEY = "buyer-interaction-key";
    private static final String BUYER_INTERACTION_URI =
            CommonFixture.getUri(WINNER_BUYER, "/interaction").toString();
    private static final String SELLER_INTERACTION_KEY = "seller-interaction-key";
    private static final String SELLER_INTERACTION_URI =
            CommonFixture.getUri(SELLER, "/interaction").toString();

    private static final String COORDINATOR_URL = "https://example.com/keys";
    private static final String COORDINATOR_HOST = "https://example.com";
    private static final String DEFAULT_FETCH_URI = "https://default-example.com/keys";
    private static final String DEFAULT_FETCH_HOST = "https://default-example.com";

    private static final String COORDINATOR_ALLOWLIST = COORDINATOR_URL + "," + DEFAULT_FETCH_URI;

    private static final WinReportingUrls WIN_REPORTING_URLS =
            WinReportingUrls.newBuilder()
                    .setBuyerReportingUrls(
                            ReportingUrls.newBuilder()
                                    .setReportingUrl(BUYER_REPORTING_URI)
                                    .putInteractionReportingUrls(
                                            BUYER_INTERACTION_KEY, BUYER_INTERACTION_URI)
                                    .build())
                    .setTopLevelSellerReportingUrls(
                            ReportingUrls.newBuilder()
                                    .setReportingUrl(SELLER_REPORTING_URI)
                                    .putInteractionReportingUrls(
                                            SELLER_INTERACTION_KEY, SELLER_INTERACTION_URI)
                                    .build())
                    .build();
    private static final String WINNING_CUSTOM_AUDIENCE_NAME = "test-name";
    private static final String WINNING_CUSTOM_AUDIENCE_OWNER = "test-owner";
    private static final float BID = 5;
    private static final float SCORE = 5;
    private static final AuctionResult AUCTION_RESULT =
            AuctionResult.newBuilder()
                    .setAdType(AuctionResult.AdType.REMARKETING_AD)
                    .setAdRenderUrl(WINNER_AD_RENDER_URI.toString())
                    .setCustomAudienceName(WINNING_CUSTOM_AUDIENCE_NAME)
                    .setCustomAudienceOwner(WINNING_CUSTOM_AUDIENCE_OWNER)
                    .setBuyer(WINNER_BUYER.toString())
                    .setBid(BID)
                    .setScore(SCORE)
                    .setIsChaff(false)
                    .setWinReportingUrls(WIN_REPORTING_URLS)
                    .build();
    private static final int NUM_BUYERS = 2;

    private static final long AUCTION_SERVER_AD_ID_FETCHER_TIMEOUT_MS = 20;
    private ExecutorService mLightweightExecutorService;
    private ExecutorService mBackgroundExecutorService;
    private ScheduledThreadPoolExecutor mScheduledExecutor;
    private AdServicesHttpsClient mAdServicesHttpsClientSpy;
    private AdServicesLogger mAdServicesLoggerMock;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Rule(order = 1)
    public final AdServicesDeviceSupportedRule deviceSupportRule =
            new AdServicesDeviceSupportedRule();

    @Rule(order = 2)
    public final MockWebServerRule mockWebServerRule = MockWebServerRuleFactory.createForHttps();

    // This object access some system APIs
    @Mock public DevContextFilter mDevContextFilterMock;
    @Mock public AppImportanceFilter mAppImportanceFilterMock;
    private Context mContext;
    private Flags mFlags;
    @Mock private FledgeAuthorizationFilter mFledgeAuthorizationFilterMock;
    private AdFilteringFeatureFactory mAdFilteringFeatureFactory;
    private MockitoSession mStaticMockSession = null;
    @Mock private ConsentManager mConsentManagerMock;
    private CustomAudienceDao mCustomAudienceDaoSpy;
    private EncodedPayloadDao mEncodedPayloadDaoSpy;
    private AdSelectionEntryDao mAdSelectionEntryDao;
    private AppInstallDao mAppInstallDao;
    private FrequencyCapDao mFrequencyCapDaoSpy;
    private com.android.adservices.data.encryptionkey.EncryptionKeyDao mEncryptionKeyDao;
    private EncryptionKeyDao mAuctionServerEncryptionKeyDao;
    private ProtectedServersEncryptionConfigDao mProtectedServersEncryptionConfigDao;
    private EnrollmentDao mEnrollmentDao;
    private EncryptionContextDao mEncryptionContextDao;
    @Mock private ObliviousHttpEncryptor mObliviousHttpEncryptorMock;
    @Mock private AdSelectionServiceFilter mAdSelectionServiceFilterMock;
    private AdSelectionService mAdSelectionService;
    private AuctionServerPayloadFormatter mPayloadFormatter;
    private AuctionServerPayloadExtractor mPayloadExtractor;
    private AuctionServerDataCompressor mDataCompressor;
    private AdSelectionDebugReportDao mAdSelectionDebugReportDaoSpy;
    private AdIdFetcher mAdIdFetcher;
    private MockAdIdWorker mMockAdIdWorker;
    private MultiCloudSupportStrategy mMultiCloudSupportStrategy;
    @Mock private KAnonSignJoinFactory mUnusedKAnonSignJoinFactory;
    @Mock private AdServicesHttpsClient mMockHttpClient;
    private RetryStrategyFactory mRetryStrategyFactory;

    @Before
    public void setUp() {
        mLightweightExecutorService = AdServicesExecutors.getLightWeightExecutor();
        mBackgroundExecutorService = AdServicesExecutors.getBackgroundExecutor();
        mScheduledExecutor = AdServicesExecutors.getScheduler();
        mContext = ApplicationProvider.getApplicationContext();
        mFlags = new AuctionServerE2ETestFlags();
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(JSScriptEngine.class)
                        .strictness(Strictness.LENIENT)
                        .initMocks(this)
                        .mockStatic(ConsentManager.class)
                        .mockStatic(AppImportanceFilter.class)
                        .mockStatic(FlagsFactory.class)
                        .startMocking();
        mAdServicesLoggerMock = ExtendedMockito.mock(AdServicesLoggerImpl.class);
        mCustomAudienceDaoSpy =
                spy(
                        Room.inMemoryDatabaseBuilder(mContext, CustomAudienceDatabase.class)
                                .addTypeConverter(new DBCustomAudience.Converters(true, true))
                                .build()
                                .customAudienceDao());
        mEncodedPayloadDaoSpy =
                spy(
                        Room.inMemoryDatabaseBuilder(mContext, ProtectedSignalsDatabase.class)
                                .build()
                                .getEncodedPayloadDao());
        mAdSelectionEntryDao =
                Room.inMemoryDatabaseBuilder(mContext, AdSelectionDatabase.class)
                        .build()
                        .adSelectionEntryDao();
        SharedStorageDatabase sharedDb =
                Room.inMemoryDatabaseBuilder(mContext, SharedStorageDatabase.class).build();

        doReturn(mFlags).when(FlagsFactory::getFlags);
        mAppInstallDao = sharedDb.appInstallDao();
        mFrequencyCapDaoSpy = spy(sharedDb.frequencyCapDao());
        AdSelectionServerDatabase serverDb =
                Room.inMemoryDatabaseBuilder(mContext, AdSelectionServerDatabase.class).build();
        mEncryptionKeyDao =
                com.android.adservices.data.encryptionkey.EncryptionKeyDao.getInstance(mContext);
        mEnrollmentDao = EnrollmentDao.getInstance(mContext);
        mAuctionServerEncryptionKeyDao = serverDb.encryptionKeyDao();
        mProtectedServersEncryptionConfigDao = serverDb.protectedServersEncryptionConfigDao();
        mEncryptionContextDao = serverDb.encryptionContextDao();
        mAdFilteringFeatureFactory =
                new AdFilteringFeatureFactory(mAppInstallDao, mFrequencyCapDaoSpy, mFlags);
        when(ConsentManager.getInstance()).thenReturn(mConsentManagerMock);
        when(AppImportanceFilter.create(any(), anyInt(), any()))
                .thenReturn(mAppImportanceFilterMock);
        doNothing()
                .when(mAppImportanceFilterMock)
                .assertCallerIsInForeground(anyInt(), anyInt(), any());
        mAdServicesHttpsClientSpy =
                spy(
                        new AdServicesHttpsClient(
                                AdServicesExecutors.getBlockingExecutor(),
                                CacheProviderFactory.createNoOpCache()));
        AdSelectionDebugReportingDatabase adSelectionDebugReportingDatabase =
                Room.inMemoryDatabaseBuilder(mContext, AdSelectionDebugReportingDatabase.class)
                        .build();
        mAdSelectionDebugReportDaoSpy =
                spy(adSelectionDebugReportingDatabase.getAdSelectionDebugReportDao());
        mMockAdIdWorker = new MockAdIdWorker(new AdIdCacheManager(mContext));
        mAdIdFetcher =
                new AdIdFetcher(mMockAdIdWorker, mLightweightExecutorService, mScheduledExecutor);
        mMultiCloudSupportStrategy =
                MultiCloudTestStrategyFactory.getDisabledTestStrategy(mObliviousHttpEncryptorMock);
        mRetryStrategyFactory = RetryStrategyFactory.createInstanceForTesting();

        mAdSelectionService = createAdSelectionService();

        mPayloadFormatter =
                AuctionServerPayloadFormatterFactory.createPayloadFormatter(
                        mFlags.getFledgeAuctionServerPayloadFormatVersion(),
                        mFlags.getFledgeAuctionServerPayloadBucketSizes());
        mPayloadExtractor =
                AuctionServerPayloadFormatterFactory.createPayloadExtractor(
                        mFlags.getFledgeAuctionServerPayloadFormatVersion(), mAdServicesLoggerMock);

        mDataCompressor =
                AuctionServerDataCompressorFactory.getDataCompressor(
                        mFlags.getFledgeAuctionServerCompressionAlgorithmVersion());

        doReturn(DevContext.createForDevOptionsDisabled())
                .when(mDevContextFilterMock)
                .createDevContext();
        mMockAdIdWorker.setResult(AdId.ZERO_OUT, true);
    }

    @After
    public void tearDown() {
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
        if (mAdServicesHttpsClientSpy != null) {
            reset(mAdServicesHttpsClientSpy);
        }
    }

    @Test
    public void testAuctionServer_killSwitchDisabled_throwsException() {
        mFlags =
                new AuctionServerE2ETestFlags(
                        true, false, AUCTION_SERVER_AD_ID_FETCHER_TIMEOUT_MS, false);
        mAdSelectionService = createAdSelectionService(); // create the service again with new flags

        GetAdSelectionDataInput getAdSelectionDataInput =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        ThrowingRunnable getAdSelectionDataRunnable =
                () -> invokeGetAdSelectionData(mAdSelectionService, getAdSelectionDataInput);

        PersistAdSelectionResultInput persistAdSelectionResultInput =
                new PersistAdSelectionResultInput.Builder()
                        .setAdSelectionId(123456L)
                        .setSeller(SELLER)
                        .setAdSelectionResult(new byte[42])
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();
        ThrowingRunnable persistAdSelectionResultRunnable =
                () ->
                        invokePersistAdSelectionResult(
                                mAdSelectionService, persistAdSelectionResultInput);

        Assert.assertThrows(
                AUCTION_SERVER_API_IS_NOT_AVAILABLE,
                IllegalStateException.class,
                getAdSelectionDataRunnable);
        Assert.assertThrows(
                AUCTION_SERVER_API_IS_NOT_AVAILABLE,
                IllegalStateException.class,
                persistAdSelectionResultRunnable);
    }

    @Test
    public void testAuctionServer_consentDisabled_throwsException()
            throws RemoteException, InterruptedException {
        doThrow(new FilterException(new ConsentManager.RevokedConsentException()))
                .when(mAdSelectionServiceFilterMock)
                .filterRequest(
                        eq(SELLER),
                        eq(CALLER_PACKAGE_NAME),
                        eq(false),
                        eq(true),
                        eq(CALLER_UID),
                        eq(AdServicesStatsLog
                                .AD_SERVICES_API_CALLED__API_NAME__GET_AD_SELECTION_DATA),
                        eq(Throttler.ApiKey.FLEDGE_API_GET_AD_SELECTION_DATA),
                        eq(DevContext.createForDevOptionsDisabled()));
        doThrow(new FilterException(new ConsentManager.RevokedConsentException()))
                .when(mAdSelectionServiceFilterMock)
                .filterRequest(
                        eq(SELLER),
                        eq(CALLER_PACKAGE_NAME),
                        eq(false),
                        eq(true),
                        eq(CALLER_UID),
                        eq(AdServicesStatsLog
                                .AD_SERVICES_API_CALLED__API_NAME__PERSIST_AD_SELECTION_RESULT),
                        eq(Throttler.ApiKey.FLEDGE_API_PERSIST_AD_SELECTION_RESULT),
                        eq(DevContext.createForDevOptionsDisabled()));

        mAdSelectionService = createAdSelectionService(); // create the service again with new flags

        GetAdSelectionDataInput getAdSelectionDataInput =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        GetAdSelectionDataTestCallback callback1 =
                invokeGetAdSelectionData(mAdSelectionService, getAdSelectionDataInput);
        long adSelectionId = callback1.mGetAdSelectionDataResponse.getAdSelectionId();

        Assert.assertTrue(callback1.mIsSuccess);
        Assert.assertNotNull(callback1.mGetAdSelectionDataResponse.getAdSelectionData());
        Assert.assertEquals(
                REVOKED_CONSENT_RANDOM_DATA_SIZE,
                callback1.mGetAdSelectionDataResponse.getAdSelectionData().length);

        PersistAdSelectionResultInput persistAdSelectionResultInput =
                new PersistAdSelectionResultInput.Builder()
                        .setAdSelectionId(adSelectionId)
                        .setSeller(SELLER)
                        .setAdSelectionResult(new byte[42])
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();
        PersistAdSelectionResultTestCallback callback2 =
                invokePersistAdSelectionResult(mAdSelectionService, persistAdSelectionResultInput);
        Assert.assertTrue(callback2.mIsSuccess);
        Assert.assertEquals(
                adSelectionId, callback2.mPersistAdSelectionResultResponse.getAdSelectionId());
        Assert.assertNotNull(callback2.mPersistAdSelectionResultResponse.getAdRenderUri());
        Assert.assertEquals(
                Uri.EMPTY, callback2.mPersistAdSelectionResultResponse.getAdRenderUri());
    }

    @Test
    public void testGetAdSelectionData_withoutEncrypt_validRequest_success() throws Exception {
        doReturn(mFlags).when(FlagsFactory::getFlags);

        Map<String, AdTechIdentifier> nameAndBuyersMap =
                Map.of(
                        "Shoes CA of Buyer 1", WINNER_BUYER,
                        "Shirts CA of Buyer 1", WINNER_BUYER,
                        "Shoes CA Of Buyer 2", DIFFERENT_BUYER);
        Set<AdTechIdentifier> buyers = new HashSet<>(nameAndBuyersMap.values());
        Map<String, DBCustomAudience> namesAndCustomAudiences =
                createAndPersistDBCustomAudiences(nameAndBuyersMap);

        when(mObliviousHttpEncryptorMock.encryptBytes(
                        any(byte[].class), anyLong(), anyLong(), any()))
                .thenAnswer(
                        invocation ->
                                FluentFuture.from(immediateFuture(invocation.getArgument(0))));

        GetAdSelectionDataInput input =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        GetAdSelectionDataTestCallback callback =
                invokeGetAdSelectionData(mAdSelectionService, input);

        Assert.assertTrue(callback.mIsSuccess);
        Assert.assertNotNull(callback.mGetAdSelectionDataResponse);
        Assert.assertNotNull(callback.mGetAdSelectionDataResponse.getAdSelectionData());

        byte[] encryptedBytes = callback.mGetAdSelectionDataResponse.getAdSelectionData();
        // Since encryption is mocked to do nothing then just passing encrypted byte[]
        Map<AdTechIdentifier, BuyerInput> buyerInputMap =
                getBuyerInputMapFromDecryptedBytes(encryptedBytes);
        Assert.assertEquals(buyers, buyerInputMap.keySet());
        for (AdTechIdentifier buyer : buyerInputMap.keySet()) {
            BuyerInput buyerInput = buyerInputMap.get(buyer);
            for (BuyerInput.CustomAudience buyerInputsCA : buyerInput.getCustomAudiencesList()) {
                String buyerInputsCAName = buyerInputsCA.getName();
                Assert.assertTrue(namesAndCustomAudiences.containsKey(buyerInputsCAName));
                DBCustomAudience deviceCA = namesAndCustomAudiences.get(buyerInputsCAName);
                Assert.assertEquals(deviceCA.getName(), buyerInputsCAName);
                Assert.assertEquals(deviceCA.getBuyer(), buyer);
                assertEquals(buyerInputsCA, deviceCA);
            }
        }
    }

    @Test
    public void testGetAdSelectionData_withoutEncrypt_validRequest_successPayloadMetricsEnabled()
            throws Exception {
        ArgumentCaptor<GetAdSelectionDataApiCalledStats> argumentCaptorApiCalledStats =
                ArgumentCaptor.forClass(GetAdSelectionDataApiCalledStats.class);

        ArgumentCaptor<GetAdSelectionDataBuyerInputGeneratedStats> argumentCaptorBuyerInputStats =
                ArgumentCaptor.forClass(GetAdSelectionDataBuyerInputGeneratedStats.class);

        mFlags =
                new AuctionServerE2ETestFlags() {
                    @Override
                    public boolean getFledgeAuctionServerGetAdSelectionDataPayloadMetricsEnabled() {
                        return true;
                    }
                };

        doReturn(mFlags).when(FlagsFactory::getFlags);

        mAdSelectionService = createAdSelectionService(); // create the service again with new flags

        Map<String, AdTechIdentifier> nameAndBuyersMap =
                Map.of(
                        "Shoes CA of Buyer 1", WINNER_BUYER,
                        "Shirts CA of Buyer 1", WINNER_BUYER,
                        "Shoes CA Of Buyer 2", DIFFERENT_BUYER);
        Set<AdTechIdentifier> buyers = new HashSet<>(nameAndBuyersMap.values());
        Map<String, DBCustomAudience> namesAndCustomAudiences =
                createAndPersistDBCustomAudiences(nameAndBuyersMap);

        when(mObliviousHttpEncryptorMock.encryptBytes(
                        any(byte[].class), anyLong(), anyLong(), any()))
                .thenAnswer(
                        invocation ->
                                FluentFuture.from(immediateFuture(invocation.getArgument(0))));

        GetAdSelectionDataInput input =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        GetAdSelectionDataTestCallback callback =
                invokeGetAdSelectionData(mAdSelectionService, input);

        Assert.assertTrue(callback.mIsSuccess);
        Assert.assertNotNull(callback.mGetAdSelectionDataResponse);
        Assert.assertNotNull(callback.mGetAdSelectionDataResponse.getAdSelectionData());

        byte[] encryptedBytes = callback.mGetAdSelectionDataResponse.getAdSelectionData();
        // Since encryption is mocked to do nothing then just passing encrypted byte[]
        Map<AdTechIdentifier, BuyerInput> buyerInputMap =
                getBuyerInputMapFromDecryptedBytes(encryptedBytes);
        Assert.assertEquals(buyers, buyerInputMap.keySet());
        for (AdTechIdentifier buyer : buyerInputMap.keySet()) {
            BuyerInput buyerInput = buyerInputMap.get(buyer);
            for (BuyerInput.CustomAudience buyerInputsCA : buyerInput.getCustomAudiencesList()) {
                String buyerInputsCAName = buyerInputsCA.getName();
                Assert.assertTrue(namesAndCustomAudiences.containsKey(buyerInputsCAName));
                DBCustomAudience deviceCA = namesAndCustomAudiences.get(buyerInputsCAName);
                Assert.assertEquals(deviceCA.getName(), buyerInputsCAName);
                Assert.assertEquals(deviceCA.getBuyer(), buyer);
                assertEquals(buyerInputsCA, deviceCA);
            }
        }

        // Verify GetAdSelectionDataBuyerInputGeneratedStats metrics
        verify(mAdServicesLoggerMock, times(2))
                .logGetAdSelectionDataBuyerInputGeneratedStats(
                        argumentCaptorBuyerInputStats.capture());
        List<GetAdSelectionDataBuyerInputGeneratedStats> stats =
                argumentCaptorBuyerInputStats.getAllValues();

        GetAdSelectionDataBuyerInputGeneratedStats stats1 = stats.get(0);
        assertThat(stats1.getNumCustomAudiences()).isEqualTo(1);
        assertThat(stats1.getNumCustomAudiencesOmitAds()).isEqualTo(0);

        GetAdSelectionDataBuyerInputGeneratedStats stats2 = stats.get(1);
        assertThat(stats2.getNumCustomAudiences()).isEqualTo(2);
        assertThat(stats2.getNumCustomAudiencesOmitAds()).isEqualTo(0);

        // Verify GetAdSelectionDataApiCalledStats metrics
        verify(mAdServicesLoggerMock, times(1))
                .logGetAdSelectionDataApiCalledStats(argumentCaptorApiCalledStats.capture());
        assertThat(argumentCaptorApiCalledStats.getValue().getStatusCode())
                .isEqualTo(STATUS_SUCCESS);
        assertThat(argumentCaptorApiCalledStats.getValue().getPayloadSizeKb())
                .isEqualTo(encryptedBytes.length / 1000);
        assertThat(argumentCaptorApiCalledStats.getValue().getNumBuyers()).isEqualTo(NUM_BUYERS);
    }

    @Test
    public void testGetAdSelectionData_withoutEncrypt_validRequest_successPayloadMetricsDisabled()
            throws Exception {
        mFlags =
                new AuctionServerE2ETestFlags() {
                    @Override
                    public boolean getFledgeAuctionServerGetAdSelectionDataPayloadMetricsEnabled() {
                        return false;
                    }
                };

        doReturn(mFlags).when(FlagsFactory::getFlags);

        mAdSelectionService = createAdSelectionService(); // create the service again with new flags

        Map<String, AdTechIdentifier> nameAndBuyersMap =
                Map.of(
                        "Shoes CA of Buyer 1", WINNER_BUYER,
                        "Shirts CA of Buyer 1", WINNER_BUYER,
                        "Shoes CA Of Buyer 2", DIFFERENT_BUYER);
        Set<AdTechIdentifier> buyers = new HashSet<>(nameAndBuyersMap.values());
        Map<String, DBCustomAudience> namesAndCustomAudiences =
                createAndPersistDBCustomAudiences(nameAndBuyersMap);

        when(mObliviousHttpEncryptorMock.encryptBytes(
                        any(byte[].class), anyLong(), anyLong(), any()))
                .thenAnswer(
                        invocation ->
                                FluentFuture.from(immediateFuture(invocation.getArgument(0))));

        GetAdSelectionDataInput input =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        GetAdSelectionDataTestCallback callback =
                invokeGetAdSelectionData(mAdSelectionService, input);

        Assert.assertTrue(callback.mIsSuccess);
        Assert.assertNotNull(callback.mGetAdSelectionDataResponse);
        Assert.assertNotNull(callback.mGetAdSelectionDataResponse.getAdSelectionData());

        byte[] encryptedBytes = callback.mGetAdSelectionDataResponse.getAdSelectionData();
        // Since encryption is mocked to do nothing then just passing encrypted byte[]
        Map<AdTechIdentifier, BuyerInput> buyerInputMap =
                getBuyerInputMapFromDecryptedBytes(encryptedBytes);
        Assert.assertEquals(buyers, buyerInputMap.keySet());
        for (AdTechIdentifier buyer : buyerInputMap.keySet()) {
            BuyerInput buyerInput = buyerInputMap.get(buyer);
            for (BuyerInput.CustomAudience buyerInputsCA : buyerInput.getCustomAudiencesList()) {
                String buyerInputsCAName = buyerInputsCA.getName();
                Assert.assertTrue(namesAndCustomAudiences.containsKey(buyerInputsCAName));
                DBCustomAudience deviceCA = namesAndCustomAudiences.get(buyerInputsCAName);
                Assert.assertEquals(deviceCA.getName(), buyerInputsCAName);
                Assert.assertEquals(deviceCA.getBuyer(), buyer);
                assertEquals(buyerInputsCA, deviceCA);
            }
        }

        verify(mAdServicesLoggerMock, never()).logGetAdSelectionDataApiCalledStats(any());
        verify(mAdServicesLoggerMock, never()).logGetAdSelectionDataBuyerInputGeneratedStats(any());
    }

    @Test
    public void testGetAdSelectionData_withoutEncrypt_validRequestWithOmitAdsInOneCA_success()
            throws Exception {
        mFlags =
                new AuctionServerE2ETestFlags(
                        /* omitAdsEnabled = */ true); // create flags with omit ads enabled
        doReturn(mFlags).when(FlagsFactory::getFlags);

        mAdSelectionService = createAdSelectionService(); // create the service again with new flags

        Map<String, AdTechIdentifier> nameAndBuyersMap =
                Map.of(
                        "Shoes CA of Buyer 1", WINNER_BUYER,
                        "Shirts CA of Buyer 1", WINNER_BUYER,
                        "Shoes CA Of Buyer 2", DIFFERENT_BUYER);
        Set<AdTechIdentifier> buyers = new HashSet<>(nameAndBuyersMap.values());
        Map<String, DBCustomAudience> namesAndCustomAudiences =
                createAndPersistDBCustomAudiences(nameAndBuyersMap);

        String buyer2ShirtsName = "Shirts CA of Buyer 2";
        // Insert a CA with omit ads enabled
        DBCustomAudience dbCustomAudienceOmitAdsEnabled =
                createAndPersistDBCustomAudienceWithOmitAdsEnabled(
                        buyer2ShirtsName, DIFFERENT_BUYER);
        namesAndCustomAudiences.put(buyer2ShirtsName, dbCustomAudienceOmitAdsEnabled);

        when(mObliviousHttpEncryptorMock.encryptBytes(
                        any(byte[].class), anyLong(), anyLong(), any()))
                .thenAnswer(
                        invocation ->
                                FluentFuture.from(immediateFuture(invocation.getArgument(0))));

        GetAdSelectionDataInput input =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        GetAdSelectionDataTestCallback callback =
                invokeGetAdSelectionData(mAdSelectionService, input);

        Assert.assertTrue(callback.mIsSuccess);
        Assert.assertNotNull(callback.mGetAdSelectionDataResponse);
        Assert.assertNotNull(callback.mGetAdSelectionDataResponse.getAdSelectionData());

        byte[] encryptedBytes = callback.mGetAdSelectionDataResponse.getAdSelectionData();
        // Since encryption is mocked to do nothing then just passing encrypted byte[]
        Map<AdTechIdentifier, BuyerInput> buyerInputMap =
                getBuyerInputMapFromDecryptedBytes(encryptedBytes);
        Assert.assertEquals(buyers, buyerInputMap.keySet());
        for (AdTechIdentifier buyer : buyerInputMap.keySet()) {
            BuyerInput buyerInput = buyerInputMap.get(buyer);
            for (BuyerInput.CustomAudience buyerInputsCA : buyerInput.getCustomAudiencesList()) {
                String buyerInputsCAName = buyerInputsCA.getName();
                Assert.assertTrue(namesAndCustomAudiences.containsKey(buyerInputsCAName));
                DBCustomAudience deviceCA = namesAndCustomAudiences.get(buyerInputsCAName);
                Assert.assertEquals(deviceCA.getName(), buyerInputsCAName);
                Assert.assertEquals(deviceCA.getBuyer(), buyer);
                assertEquals(buyerInputsCA, deviceCA);

                // Buyer 2 shirts ca should not have ad render ids list
                if (deviceCA.getBuyer().equals(DIFFERENT_BUYER)
                        && deviceCA.getName().equals(buyer2ShirtsName)) {
                    assertThat(buyerInputsCA.getAdRenderIdsList()).isEmpty();
                } else {
                    // All other cas should have ads
                    assertThat(buyerInputsCA.getAdRenderIdsList()).isNotEmpty();
                }
            }
        }
    }

    @Test
    public void testGetAdSelectionData_fCap_success() throws Exception {
        doReturn(mFlags).when(FlagsFactory::getFlags);

        when(mObliviousHttpEncryptorMock.encryptBytes(
                        any(byte[].class), anyLong(), anyLong(), any()))
                .thenAnswer(
                        invocation ->
                                FluentFuture.from(immediateFuture(invocation.getArgument(0))));
        when(mObliviousHttpEncryptorMock.decryptBytes(any(byte[].class), anyLong()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        int sequenceNumber1 = 1;
        int sequenceNumber2 = 2;
        int filterMaxCount = 1;
        List<DBAdData> filterableAds =
                List.of(
                        getFilterableAndServerEligibleAd(sequenceNumber1, filterMaxCount),
                        getFilterableAndServerEligibleAd(sequenceNumber2, filterMaxCount));

        DBCustomAudience winningCustomAudience =
                DBCustomAudienceFixture.getValidBuilderByBuyerWithAdRenderId(
                                WINNER_BUYER,
                                WINNING_CUSTOM_AUDIENCE_NAME,
                                WINNING_CUSTOM_AUDIENCE_OWNER)
                        .setAds(filterableAds)
                        .build();
        Assert.assertNotNull(winningCustomAudience.getAds());
        mCustomAudienceDaoSpy.insertOrOverwriteCustomAudience(
                winningCustomAudience, Uri.EMPTY, false);

        GetAdSelectionDataInput input =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        GetAdSelectionDataTestCallback getAdSelectionDataTestCallback =
                invokeGetAdSelectionData(mAdSelectionService, input);
        long adSelectionId =
                getAdSelectionDataTestCallback.mGetAdSelectionDataResponse.getAdSelectionId();
        Assert.assertTrue(getAdSelectionDataTestCallback.mIsSuccess);

        // Since encryption is mocked to do nothing then just passing encrypted byte[]
        List<String> adRenderIdsFromBuyerInput =
                extractCAAdRenderIdListFromBuyerInput(
                        getAdSelectionDataTestCallback,
                        winningCustomAudience.getBuyer(),
                        winningCustomAudience.getName(),
                        winningCustomAudience.getOwner());
        Assert.assertEquals(filterableAds.size(), adRenderIdsFromBuyerInput.size());

        PersistAdSelectionResultInput persistAdSelectionResultInput =
                new PersistAdSelectionResultInput.Builder()
                        .setAdSelectionId(adSelectionId)
                        .setSeller(SELLER)
                        .setAdSelectionResult(prepareAuctionResultBytes())
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        PersistAdSelectionResultTestCallback persistAdSelectionResultTestCallback =
                invokePersistAdSelectionResult(mAdSelectionService, persistAdSelectionResultInput);

        Assert.assertTrue(persistAdSelectionResultTestCallback.mIsSuccess);

        // FCap non-win reporting
        UpdateAdCounterHistogramInput updateHistogramInput =
                new UpdateAdCounterHistogramInput.Builder(
                                adSelectionId,
                                FrequencyCapFilters.AD_EVENT_TYPE_CLICK,
                                SELLER,
                                CALLER_PACKAGE_NAME)
                        .build();
        UpdateAdCounterHistogramTestCallback updateHistogramCallback =
                invokeUpdateAdCounterHistogram(mAdSelectionService, updateHistogramInput);
        Assert.assertTrue(updateHistogramCallback.mIsSuccess);

        // Collect device data again and expect one less ads due to FCap filter
        GetAdSelectionDataInput input2 =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        GetAdSelectionDataTestCallback getAdSelectionDataTestCallback2 =
                invokeGetAdSelectionData(mAdSelectionService, input2);

        // Since encryption is mocked to do nothing then just passing encrypted byte[]
        List<String> adRenderIdsFromBuyerInput2 =
                extractCAAdRenderIdListFromBuyerInput(
                        getAdSelectionDataTestCallback2,
                        winningCustomAudience.getBuyer(),
                        winningCustomAudience.getName(),
                        winningCustomAudience.getOwner());
        // No ads collected for the same CA bc they are filtered out
        Assert.assertEquals(filterableAds.size() - 1, adRenderIdsFromBuyerInput2.size());
    }

    @Test
    public void testGetAdSelectionData_withEncrypt_validRequest_success() throws Exception {
        testGetAdSelectionData_withEncryptHelper(mFlags);
    }

    @Test
    public void testGetAdSelectionData_withEncrypt_validRequest_DebugReportingFlagEnabled()
            throws Exception {
        Flags flags =
                new AuctionServerE2ETestFlags(
                        false, true, AUCTION_SERVER_AD_ID_FETCHER_TIMEOUT_MS, false);

        testGetAdSelectionData_withEncryptHelper(flags);
    }

    @Test
    public void testGetAdSelectionData_withEncrypt_validRequest_LatDisabled() throws Exception {
        Flags flags =
                new AuctionServerE2ETestFlags(
                        false, true, AUCTION_SERVER_AD_ID_FETCHER_TIMEOUT_MS, false);
        mMockAdIdWorker.setResult(MockAdIdWorker.MOCK_AD_ID, false);

        testGetAdSelectionData_withEncryptHelper(flags);
    }

    @Test
    public void testGetAdSelectionData_withEncrypt_validRequest_GetAdIdTimeoutException()
            throws Exception {
        Flags flags =
                new AuctionServerE2ETestFlags(
                        false, true, AUCTION_SERVER_AD_ID_FETCHER_TIMEOUT_MS, false);
        mMockAdIdWorker.setResult(MockAdIdWorker.MOCK_AD_ID, false);
        mMockAdIdWorker.setDelay(AUCTION_SERVER_AD_ID_FETCHER_TIMEOUT_MS * 2);

        testGetAdSelectionData_withEncryptHelper(flags);
    }

    @Test
    public void testPersistAdSelectionResult_withoutDecrypt_validRequest_success()
            throws Exception {
        doReturn(mFlags).when(FlagsFactory::getFlags);

        when(mObliviousHttpEncryptorMock.encryptBytes(
                        any(byte[].class), anyLong(), anyLong(), any()))
                .thenAnswer(
                        invocation ->
                                FluentFuture.from(immediateFuture(invocation.getArgument(0))));
        when(mObliviousHttpEncryptorMock.decryptBytes(any(byte[].class), anyLong()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mCustomAudienceDaoSpy.insertOrOverwriteCustomAudience(
                DBCustomAudienceFixture.getValidBuilderByBuyerWithAdRenderId(
                                WINNER_BUYER,
                                WINNING_CUSTOM_AUDIENCE_NAME,
                                WINNING_CUSTOM_AUDIENCE_OWNER)
                        .setAds(
                                DBAdDataFixture.getValidDbAdDataListByBuyerWithAdRenderId(
                                        WINNER_BUYER))
                        .build(),
                Uri.EMPTY,
                false);

        GetAdSelectionDataInput input =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        GetAdSelectionDataTestCallback getAdSelectionDataTestCallback =
                invokeGetAdSelectionData(mAdSelectionService, input);
        long adSelectionId =
                getAdSelectionDataTestCallback.mGetAdSelectionDataResponse.getAdSelectionId();

        PersistAdSelectionResultInput persistAdSelectionResultInput =
                new PersistAdSelectionResultInput.Builder()
                        .setAdSelectionId(adSelectionId)
                        .setSeller(SELLER)
                        .setAdSelectionResult(prepareAuctionResultBytes())
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        PersistAdSelectionResultTestCallback persistAdSelectionResultTestCallback =
                invokePersistAdSelectionResult(mAdSelectionService, persistAdSelectionResultInput);

        Assert.assertTrue(persistAdSelectionResultTestCallback.mIsSuccess);
        Assert.assertEquals(
                WINNER_AD_RENDER_URI,
                persistAdSelectionResultTestCallback.mPersistAdSelectionResultResponse
                        .getAdRenderUri());
        Assert.assertEquals(
                adSelectionId,
                persistAdSelectionResultTestCallback.mPersistAdSelectionResultResponse
                        .getAdSelectionId());
        ReportingData reportingData = mAdSelectionEntryDao.getReportingDataForId(adSelectionId);
        Assert.assertEquals(
                BUYER_REPORTING_URI, reportingData.getBuyerWinReportingUri().toString());
        Assert.assertEquals(
                SELLER_REPORTING_URI, reportingData.getSellerWinReportingUri().toString());
    }

    @Test
    public void testPersistAdSelectionResult_withoutDecrypt_validRequest_successOmitAdsEnabled()
            throws Exception {
        Flags flagWithOmitAdsEnabled =
                new AuctionServerE2ETestFlags(
                        /* omitAdsEnabled = */ true); // create flags with omit ads enabled
        doReturn(flagWithOmitAdsEnabled).when(FlagsFactory::getFlags);

        when(mObliviousHttpEncryptorMock.encryptBytes(
                        any(byte[].class), anyLong(), anyLong(), any()))
                .thenAnswer(
                        invocation ->
                                FluentFuture.from(immediateFuture(invocation.getArgument(0))));
        when(mObliviousHttpEncryptorMock.decryptBytes(any(byte[].class), anyLong()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AdSelectionService adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDaoSpy,
                        mEncodedPayloadDaoSpy,
                        mFrequencyCapDaoSpy,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mAdServicesHttpsClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mContext,
                        mAdServicesLoggerMock,
                        flagWithOmitAdsEnabled,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterMock,
                        mAdSelectionServiceFilterMock,
                        mAdFilteringFeatureFactory,
                        mConsentManagerMock,
                        mMultiCloudSupportStrategy,
                        mAdSelectionDebugReportDaoSpy,
                        mAdIdFetcher,
                        mUnusedKAnonSignJoinFactory,
                        false,
                        mRetryStrategyFactory);

        mCustomAudienceDaoSpy.insertOrOverwriteCustomAudience(
                DBCustomAudienceFixture.getValidBuilderByBuyerWithAdRenderId(
                                WINNER_BUYER,
                                WINNING_CUSTOM_AUDIENCE_NAME,
                                WINNING_CUSTOM_AUDIENCE_OWNER)
                        .setAds(
                                DBAdDataFixture.getValidDbAdDataListByBuyerWithAdRenderId(
                                        WINNER_BUYER))
                        .setAuctionServerRequestFlags(FLAG_AUCTION_SERVER_REQUEST_OMIT_ADS)
                        .build(),
                Uri.EMPTY,
                false);

        GetAdSelectionDataInput input =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        GetAdSelectionDataTestCallback getAdSelectionDataTestCallback =
                invokeGetAdSelectionData(adSelectionService, input);

        byte[] encryptedBytes =
                getAdSelectionDataTestCallback.mGetAdSelectionDataResponse.getAdSelectionData();

        // Since encryption is mocked to do nothing then just passing encrypted byte[]
        Map<AdTechIdentifier, BuyerInput> buyerInputMap =
                getBuyerInputMapFromDecryptedBytes(encryptedBytes);

        // Assert that ads were omitted in buyer input
        BuyerInput buyerInput = buyerInputMap.get(WINNER_BUYER);
        assertThat(buyerInput.getCustomAudiences(0).getAdRenderIdsCount()).isEqualTo(0);

        long adSelectionId =
                getAdSelectionDataTestCallback.mGetAdSelectionDataResponse.getAdSelectionId();

        PersistAdSelectionResultInput persistAdSelectionResultInput =
                new PersistAdSelectionResultInput.Builder()
                        .setAdSelectionId(adSelectionId)
                        .setSeller(SELLER)
                        .setAdSelectionResult(prepareAuctionResultBytes())
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        PersistAdSelectionResultTestCallback persistAdSelectionResultTestCallback =
                invokePersistAdSelectionResult(adSelectionService, persistAdSelectionResultInput);

        Assert.assertTrue(persistAdSelectionResultTestCallback.mIsSuccess);
        Assert.assertEquals(
                WINNER_AD_RENDER_URI,
                persistAdSelectionResultTestCallback.mPersistAdSelectionResultResponse
                        .getAdRenderUri());
        Assert.assertEquals(
                adSelectionId,
                persistAdSelectionResultTestCallback.mPersistAdSelectionResultResponse
                        .getAdSelectionId());
        ReportingData reportingData = mAdSelectionEntryDao.getReportingDataForId(adSelectionId);
        Assert.assertEquals(
                BUYER_REPORTING_URI, reportingData.getBuyerWinReportingUri().toString());
        Assert.assertEquals(
                SELLER_REPORTING_URI, reportingData.getSellerWinReportingUri().toString());
    }

    @Test
    @FlakyTest(bugId = 303119299)
    public void testAuctionServerResult_usedInWaterfallMediation_success() throws Exception {
        Assume.assumeTrue(WebViewSupportUtil.isJSSandboxAvailable(mContext));
        doReturn(mFlags).when(FlagsFactory::getFlags);

        Dispatcher dispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        if (request.getPath().equals(SELECTION_WATERFALL_LOGIC_JS_PATH)) {
                            return new MockResponse().setBody(SELECTION_WATERFALL_LOGIC_JS);
                        }
                        return new MockResponse().setResponseCode(404);
                    }
                };
        mockWebServerRule.startMockWebServer(dispatcher);
        final String selectionLogicPath = SELECTION_WATERFALL_LOGIC_JS_PATH;

        when(mObliviousHttpEncryptorMock.encryptBytes(
                        any(byte[].class), anyLong(), anyLong(), any()))
                .thenAnswer(
                        invocation ->
                                FluentFuture.from(immediateFuture(invocation.getArgument(0))));
        when(mObliviousHttpEncryptorMock.decryptBytes(any(byte[].class), anyLong()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mCustomAudienceDaoSpy.insertOrOverwriteCustomAudience(
                DBCustomAudienceFixture.getValidBuilderByBuyerWithAdRenderId(
                                WINNER_BUYER,
                                WINNING_CUSTOM_AUDIENCE_NAME,
                                WINNING_CUSTOM_AUDIENCE_OWNER)
                        .setAds(
                                DBAdDataFixture.getValidDbAdDataListByBuyerWithAdRenderId(
                                        WINNER_BUYER))
                        .build(),
                Uri.EMPTY,
                false);

        GetAdSelectionDataInput input =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        GetAdSelectionDataTestCallback getAdSelectionDataTestCallback =
                invokeGetAdSelectionData(mAdSelectionService, input);
        long adSelectionId =
                getAdSelectionDataTestCallback.mGetAdSelectionDataResponse.getAdSelectionId();

        PersistAdSelectionResultInput persistAdSelectionResultInput =
                new PersistAdSelectionResultInput.Builder()
                        .setAdSelectionId(adSelectionId)
                        .setSeller(SELLER)
                        .setAdSelectionResult(prepareAuctionResultBytes())
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        PersistAdSelectionResultTestCallback persistAdSelectionResultTestCallback =
                invokePersistAdSelectionResult(mAdSelectionService, persistAdSelectionResultInput);

        Assert.assertTrue(persistAdSelectionResultTestCallback.mIsSuccess);
        Assert.assertEquals(
                WINNER_AD_RENDER_URI,
                persistAdSelectionResultTestCallback.mPersistAdSelectionResultResponse
                        .getAdRenderUri());
        Assert.assertEquals(
                adSelectionId,
                persistAdSelectionResultTestCallback.mPersistAdSelectionResultResponse
                        .getAdSelectionId());

        AdSelectionSignals bidFloorSignalsBelowBid =
                AdSelectionSignals.fromString(
                        String.format(BID_FLOOR_SELECTION_SIGNAL_TEMPLATE, BID - 1));
        AdSelectionFromOutcomesInput waterfallReturnsAdSelectionIdInput =
                new AdSelectionFromOutcomesInput.Builder()
                        .setAdSelectionFromOutcomesConfig(
                                AdSelectionFromOutcomesConfigFixture
                                        .anAdSelectionFromOutcomesConfig(
                                                Collections.singletonList(adSelectionId),
                                                bidFloorSignalsBelowBid,
                                                mockWebServerRule.uriForPath(selectionLogicPath)))
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();
        AdSelectionFromOutcomesTestCallback waterfallReturnsAdSelectionIdCallback =
                invokeAdSelectionFromOutcomes(
                        mAdSelectionService, waterfallReturnsAdSelectionIdInput);
        Assert.assertTrue(waterfallReturnsAdSelectionIdCallback.mIsSuccess);
        Assert.assertNotNull(waterfallReturnsAdSelectionIdCallback.mAdSelectionResponse);
        Assert.assertEquals(
                adSelectionId,
                waterfallReturnsAdSelectionIdCallback.mAdSelectionResponse.getAdSelectionId());

        AdSelectionSignals bidFloorSignalsAboveBid =
                AdSelectionSignals.fromString(
                        String.format(BID_FLOOR_SELECTION_SIGNAL_TEMPLATE, BID + 1));
        AdSelectionFromOutcomesInput waterfallInputReturnNull =
                new AdSelectionFromOutcomesInput.Builder()
                        .setAdSelectionFromOutcomesConfig(
                                AdSelectionFromOutcomesConfigFixture
                                        .anAdSelectionFromOutcomesConfig(
                                                Collections.singletonList(adSelectionId),
                                                bidFloorSignalsAboveBid,
                                                mockWebServerRule.uriForPath(selectionLogicPath)))
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();
        AdSelectionFromOutcomesTestCallback waterfallReturnsNullCallback =
                invokeAdSelectionFromOutcomes(mAdSelectionService, waterfallInputReturnNull);
        Assert.assertTrue(waterfallReturnsNullCallback.mIsSuccess);
        Assert.assertNull(waterfallReturnsNullCallback.mAdSelectionResponse);
    }

    @Test
    public void testPersistAdSelectionResult_withDecrypt_validRequest_successEmptyUri()
            throws Exception {
        doReturn(mFlags).when(FlagsFactory::getFlags);

        DBEncryptionKey dbEncryptionKey =
                DBEncryptionKey.builder()
                        .setPublicKey("bSHP4J++pRIvnrwusqafzE8GQIzVSqyTTwEudvzc72I=")
                        .setKeyIdentifier("050bed24-c62f-46e0-a1ad-211361ad771a")
                        .setEncryptionKeyType(ENCRYPTION_KEY_TYPE_AUCTION)
                        .setExpiryTtlSeconds(TimeUnit.DAYS.toSeconds(7))
                        .build();
        mAuctionServerEncryptionKeyDao.insertAllKeys(ImmutableList.of(dbEncryptionKey));
        String seed = "wwwwwwwwwwwwwwwwwwwwwwwwwwwwwwww";
        byte[] seedBytes = seed.getBytes(StandardCharsets.US_ASCII);

        AdSelectionService service =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDaoSpy,
                        mEncodedPayloadDaoSpy,
                        mFrequencyCapDaoSpy,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mAdServicesHttpsClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mContext,
                        mAdServicesLoggerMock,
                        mFlags,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterMock,
                        mAdSelectionServiceFilterMock,
                        mAdFilteringFeatureFactory,
                        mConsentManagerMock,
                        MultiCloudTestStrategyFactory.getDisabledTestStrategy(
                                new ObliviousHttpEncryptorWithSeedImpl(
                                        new AdSelectionEncryptionKeyManager(
                                                mAuctionServerEncryptionKeyDao,
                                                mFlags,
                                                mAdServicesHttpsClientSpy,
                                                mLightweightExecutorService),
                                        mEncryptionContextDao,
                                        seedBytes,
                                        mLightweightExecutorService)),
                        mAdSelectionDebugReportDaoSpy,
                        mAdIdFetcher,
                        mUnusedKAnonSignJoinFactory,
                        false,
                        mRetryStrategyFactory);

        GetAdSelectionDataInput input =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        GetAdSelectionDataTestCallback getAdSelectionDataTestCallback =
                invokeGetAdSelectionData(service, input);

        long adSelectionId =
                getAdSelectionDataTestCallback.mGetAdSelectionDataResponse.getAdSelectionId();
        byte[] encryptedBytes =
                getAdSelectionDataTestCallback.mGetAdSelectionDataResponse.getAdSelectionData();

        Assert.assertNotNull(encryptedBytes);
        Assert.assertNotNull(
                mEncryptionContextDao.getEncryptionContext(
                        adSelectionId, ENCRYPTION_KEY_TYPE_AUCTION));

        String cipherText =
                "Lu9TKo4rvstPJt98F1IrLiVUeczFzKuBEJ8jFe1BNXfNImu/lQR0CB8/B1Kur0n1Fxcz"
                        + "ZQs28dZO2b3jwOaKk5qJgIlcY8Zd1n0Tb/M9vQXcs+d2QbeykmoffEb9kf76zebKDd1"
                        + "Slb0psgEFtATuqaxaPd9ErumVWXdvD9QuvB6p+URWN+uIv2VhFwmjtf+QE/HZBD6EE+"
                        + "Ft8ipPiNkNysa7TyL3FLgXO3HGZ2FlQX4GvE5R3br3hPkceY+cplv7ZZDSmc/vfO+7N"
                        + "4S1XkZ/y0KYuQHXF24ejJ4xmwrJ5L22V3LhTm5euppXerNtUkIqaaYRE3lQ+Glh1rph"
                        + "dFYZqyoXLhFp6ABzk72lnjMzqdL2hYAVc7agowS29jz6Wo6Tw/pglfls8l1yLntocNE"
                        + "hEUUvCDl+MQJqrY9gwmbEzrvhwgfl3MbEcShXib3qny+b8/cGEJdQ8sDft1xglbe0a1"
                        + "rGHZbNgLiprEtVYKyD4dGMcNT7L/RqmygoLRgYzmCBBD7dLgEdYMpRrYh5kmopx4lZJ"
                        + "6HkltqP0f+OzDLzgA7JCiPsCgiZG7Sx4iRR8p2iwfhKBVZPX1fPORdkRhzjIbhdWxCA"
                        + "2+GuafjfdY5FBX2F719z0SbkJeaxxrrjKMmpXLzgVT12vVMsDbuFDFhi4i4buI3gMns"
                        + "g0r4+eeQ+KX1UOMaM6OsGkdt5/aTSsBYTTv8Ikp2ufUEFDnAK4nuoTJlp+gEN3l0K07"
                        + "/U3b7R4TI=";

        byte[] responseBytes = BaseEncoding.base64().decode(cipherText);

        PersistAdSelectionResultInput persistAdSelectionResultInput =
                new PersistAdSelectionResultInput.Builder()
                        .setSeller(SELLER)
                        .setAdSelectionId(adSelectionId)
                        .setAdSelectionResult(responseBytes)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        PersistAdSelectionResultTestCallback persistAdSelectionResultTestCallback =
                invokePersistAdSelectionResult(service, persistAdSelectionResultInput);

        Assert.assertTrue(persistAdSelectionResultTestCallback.mIsSuccess);
        Assert.assertEquals(
                Uri.EMPTY,
                persistAdSelectionResultTestCallback.mPersistAdSelectionResultResponse
                        .getAdRenderUri());
    }

    @Test
    public void testReportImpression_serverAuction_impressionAndInteractionReporting()
            throws Exception {
        Assume.assumeTrue(WebViewSupportUtil.isJSSandboxAvailable(mContext));
        doReturn(mFlags).when(FlagsFactory::getFlags);

        CountDownLatch reportImpressionCountDownLatch = new CountDownLatch(4);
        Answer<ListenableFuture<Void>> successReportImpressionGetAnswer =
                invocation -> {
                    reportImpressionCountDownLatch.countDown();
                    return Futures.immediateFuture(null);
                };
        doAnswer(successReportImpressionGetAnswer)
                .when(mAdServicesHttpsClientSpy)
                .getAndReadNothing(any(Uri.class), any(DevContext.class));
        doAnswer(successReportImpressionGetAnswer)
                .when(mAdServicesHttpsClientSpy)
                .postPlainText(any(Uri.class), any(String.class), any(DevContext.class));

        when(mObliviousHttpEncryptorMock.encryptBytes(
                        any(byte[].class), anyLong(), anyLong(), any()))
                .thenAnswer(
                        invocation ->
                                FluentFuture.from(immediateFuture(invocation.getArgument(0))));
        when(mObliviousHttpEncryptorMock.decryptBytes(any(byte[].class), anyLong()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mCustomAudienceDaoSpy.insertOrOverwriteCustomAudience(
                DBCustomAudienceFixture.getValidBuilderByBuyerWithAdRenderId(
                                WINNER_BUYER,
                                WINNING_CUSTOM_AUDIENCE_NAME,
                                WINNING_CUSTOM_AUDIENCE_OWNER)
                        .setAds(
                                DBAdDataFixture.getValidDbAdDataListByBuyerWithAdRenderId(
                                        WINNER_BUYER))
                        .build(),
                Uri.EMPTY,
                false);

        GetAdSelectionDataInput input =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        GetAdSelectionDataTestCallback getAdSelectionDataTestCallback =
                invokeGetAdSelectionData(mAdSelectionService, input);
        long adSelectionId =
                getAdSelectionDataTestCallback.mGetAdSelectionDataResponse.getAdSelectionId();

        PersistAdSelectionResultInput persistAdSelectionResultInput =
                new PersistAdSelectionResultInput.Builder()
                        .setAdSelectionId(adSelectionId)
                        .setSeller(SELLER)
                        .setAdSelectionResult(prepareAuctionResultBytes())
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        PersistAdSelectionResultTestCallback persistAdSelectionResultTestCallback =
                invokePersistAdSelectionResult(mAdSelectionService, persistAdSelectionResultInput);
        Uri adRenderUriFromPersistAdSelectionResult =
                persistAdSelectionResultTestCallback.mPersistAdSelectionResultResponse
                        .getAdRenderUri();
        Assert.assertTrue(persistAdSelectionResultTestCallback.mIsSuccess);
        Assert.assertEquals(WINNER_AD_RENDER_URI, adRenderUriFromPersistAdSelectionResult);
        Assert.assertEquals(
                adSelectionId,
                persistAdSelectionResultTestCallback.mPersistAdSelectionResultResponse
                        .getAdSelectionId());
        Assert.assertEquals(
                BUYER_REPORTING_URI,
                mAdSelectionEntryDao
                        .getReportingUris(adSelectionId)
                        .getBuyerWinReportingUri()
                        .toString());
        Assert.assertEquals(
                SELLER_REPORTING_URI,
                mAdSelectionEntryDao
                        .getReportingUris(adSelectionId)
                        .getSellerWinReportingUri()
                        .toString());

        // Invoke report impression
        ReportImpressionInput reportImpressionInput =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(adSelectionId)
                        .setAdSelectionConfig(AdSelectionConfig.EMPTY)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();
        ReportImpressionTestCallback reportImpressionCallback =
                invokeReportImpression(mAdSelectionService, reportImpressionInput);

        // Invoke report interaction for buyer
        String buyerInteractionData = "buyer-interaction-data";
        ReportInteractionInput reportBuyerInteractionInput =
                new ReportInteractionInput.Builder()
                        .setAdSelectionId(adSelectionId)
                        .setInteractionKey(BUYER_INTERACTION_KEY)
                        .setInteractionData(buyerInteractionData)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .setReportingDestinations(
                                ReportEventRequest.FLAG_REPORTING_DESTINATION_BUYER)
                        .build();
        ReportInteractionsTestCallback reportBuyerInteractionsCallback =
                invokeReportInteractions(mAdSelectionService, reportBuyerInteractionInput);

        // Invoke report interaction for seller
        String sellerInteractionData = "seller-interaction-data";
        ReportInteractionInput reportSellerInteractionInput =
                new ReportInteractionInput.Builder()
                        .setAdSelectionId(adSelectionId)
                        .setInteractionKey(SELLER_INTERACTION_KEY)
                        .setInteractionData(sellerInteractionData)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .setReportingDestinations(
                                ReportEventRequest.FLAG_REPORTING_DESTINATION_SELLER)
                        .build();
        ReportInteractionsTestCallback reportSellerInteractionsCallback =
                invokeReportInteractions(mAdSelectionService, reportSellerInteractionInput);

        // Wait for countdown latch
        boolean isCountdownDone =
                reportImpressionCountDownLatch.await(
                        COUNTDOWN_LATCH_LIMIT_SECONDS, TimeUnit.SECONDS);
        Assert.assertTrue(isCountdownDone);

        // Assert report impression
        Assert.assertTrue(reportImpressionCallback.mIsSuccess);
        verify(mAdServicesHttpsClientSpy, times(1))
                .getAndReadNothing(eq(Uri.parse(SELLER_REPORTING_URI)), any());
        verify(mAdServicesHttpsClientSpy, times(1))
                .getAndReadNothing(eq(Uri.parse(BUYER_REPORTING_URI)), any());

        // Assert report interaction for buyer
        Assert.assertTrue(reportBuyerInteractionsCallback.mIsSuccess);
        verify(mAdServicesHttpsClientSpy, times(1))
                .postPlainText(
                        eq(Uri.parse(BUYER_INTERACTION_URI)), eq(buyerInteractionData), any());

        // Assert report interaction for seller
        Assert.assertTrue(reportSellerInteractionsCallback.mIsSuccess);
        verify(mAdServicesHttpsClientSpy, times(1))
                .postPlainText(
                        eq(Uri.parse(SELLER_INTERACTION_URI)), eq(sellerInteractionData), any());
    }

    @Test
    public void testReportImpression_serverAuction_sellerReportingFailure_noExceptionThrown()
            throws Exception {
        Assume.assumeTrue(WebViewSupportUtil.isJSSandboxAvailable(mContext));
        doReturn(mFlags).when(FlagsFactory::getFlags);

        CountDownLatch reportImpressionCountDownLatch = new CountDownLatch(2);
        Answer<ListenableFuture<Void>> failedReportImpressionGetAnswer =
                invocation -> {
                    reportImpressionCountDownLatch.countDown();
                    return Futures.immediateFailedFuture(
                            new IllegalStateException("Exception for test!"));
                };
        Answer<ListenableFuture<Void>> successReportImpressionGetAnswer =
                invocation -> {
                    reportImpressionCountDownLatch.countDown();
                    return Futures.immediateFuture(null);
                };
        doAnswer(successReportImpressionGetAnswer)
                .when(mAdServicesHttpsClientSpy)
                .getAndReadNothing(eq(Uri.parse(BUYER_REPORTING_URI)), any(DevContext.class));
        doAnswer(failedReportImpressionGetAnswer)
                .when(mAdServicesHttpsClientSpy)
                .getAndReadNothing(eq(Uri.parse(SELLER_REPORTING_URI)), any(DevContext.class));

        when(mObliviousHttpEncryptorMock.encryptBytes(
                        any(byte[].class), anyLong(), anyLong(), any()))
                .thenAnswer(
                        invocation ->
                                FluentFuture.from(immediateFuture(invocation.getArgument(0))));
        when(mObliviousHttpEncryptorMock.decryptBytes(any(byte[].class), anyLong()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mCustomAudienceDaoSpy.insertOrOverwriteCustomAudience(
                DBCustomAudienceFixture.getValidBuilderByBuyerWithAdRenderId(
                                WINNER_BUYER,
                                WINNING_CUSTOM_AUDIENCE_NAME,
                                WINNING_CUSTOM_AUDIENCE_OWNER)
                        .setAds(
                                DBAdDataFixture.getValidDbAdDataListByBuyerWithAdRenderId(
                                        WINNER_BUYER))
                        .build(),
                Uri.EMPTY,
                false);

        GetAdSelectionDataInput input =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        GetAdSelectionDataTestCallback getAdSelectionDataTestCallback =
                invokeGetAdSelectionData(mAdSelectionService, input);
        long adSelectionId =
                getAdSelectionDataTestCallback.mGetAdSelectionDataResponse.getAdSelectionId();

        PersistAdSelectionResultInput persistAdSelectionResultInput =
                new PersistAdSelectionResultInput.Builder()
                        .setAdSelectionId(adSelectionId)
                        .setSeller(SELLER)
                        .setAdSelectionResult(prepareAuctionResultBytes())
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        PersistAdSelectionResultTestCallback persistAdSelectionResultTestCallback =
                invokePersistAdSelectionResult(mAdSelectionService, persistAdSelectionResultInput);

        Uri adRenderUriFromPersistAdSelectionResult =
                persistAdSelectionResultTestCallback.mPersistAdSelectionResultResponse
                        .getAdRenderUri();
        Assert.assertTrue(persistAdSelectionResultTestCallback.mIsSuccess);
        Assert.assertEquals(WINNER_AD_RENDER_URI, adRenderUriFromPersistAdSelectionResult);
        Assert.assertEquals(
                adSelectionId,
                persistAdSelectionResultTestCallback.mPersistAdSelectionResultResponse
                        .getAdSelectionId());
        Assert.assertEquals(
                BUYER_REPORTING_URI,
                mAdSelectionEntryDao
                        .getReportingUris(adSelectionId)
                        .getBuyerWinReportingUri()
                        .toString());
        Assert.assertEquals(
                SELLER_REPORTING_URI,
                mAdSelectionEntryDao
                        .getReportingUris(adSelectionId)
                        .getSellerWinReportingUri()
                        .toString());

        ReportImpressionInput reportImpressionInput =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(adSelectionId)
                        .setAdSelectionConfig(AdSelectionConfig.EMPTY)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();
        ReportImpressionTestCallback callback =
                invokeReportImpression(mAdSelectionService, reportImpressionInput);
        boolean isCountdownDone =
                reportImpressionCountDownLatch.await(
                        COUNTDOWN_LATCH_LIMIT_SECONDS, TimeUnit.SECONDS);
        Assert.assertTrue(isCountdownDone);
        Assert.assertTrue(callback.mIsSuccess);
        verify(mAdServicesHttpsClientSpy, times(1))
                .getAndReadNothing(eq(Uri.parse(SELLER_REPORTING_URI)), any());
        verify(mAdServicesHttpsClientSpy, times(1))
                .getAndReadNothing(eq(Uri.parse(BUYER_REPORTING_URI)), any());
    }

    @Test
    public void testReportImpression_serverAuction_buyerReportingFailure_noExceptionThrown()
            throws Exception {
        Assume.assumeTrue(WebViewSupportUtil.isJSSandboxAvailable(mContext));
        doReturn(mFlags).when(FlagsFactory::getFlags);

        CountDownLatch reportImpressionCountDownLatch = new CountDownLatch(2);
        Answer<ListenableFuture<Void>> failedReportImpressionGetAnswer =
                invocation -> {
                    reportImpressionCountDownLatch.countDown();
                    return Futures.immediateFailedFuture(
                            new IllegalStateException("Exception for test!"));
                };
        Answer<ListenableFuture<Void>> successReportImpressionGetAnswer =
                invocation -> {
                    reportImpressionCountDownLatch.countDown();
                    return Futures.immediateFuture(null);
                };
        doAnswer(successReportImpressionGetAnswer)
                .when(mAdServicesHttpsClientSpy)
                .getAndReadNothing(eq(Uri.parse(SELLER_REPORTING_URI)), any(DevContext.class));
        doAnswer(failedReportImpressionGetAnswer)
                .when(mAdServicesHttpsClientSpy)
                .getAndReadNothing(eq(Uri.parse(BUYER_REPORTING_URI)), any(DevContext.class));

        when(mObliviousHttpEncryptorMock.encryptBytes(
                        any(byte[].class), anyLong(), anyLong(), any()))
                .thenAnswer(
                        invocation ->
                                FluentFuture.from(immediateFuture(invocation.getArgument(0))));
        when(mObliviousHttpEncryptorMock.decryptBytes(any(byte[].class), anyLong()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mCustomAudienceDaoSpy.insertOrOverwriteCustomAudience(
                DBCustomAudienceFixture.getValidBuilderByBuyerWithAdRenderId(
                                WINNER_BUYER,
                                WINNING_CUSTOM_AUDIENCE_NAME,
                                WINNING_CUSTOM_AUDIENCE_OWNER)
                        .setAds(
                                DBAdDataFixture.getValidDbAdDataListByBuyerWithAdRenderId(
                                        WINNER_BUYER))
                        .build(),
                Uri.EMPTY,
                false);

        GetAdSelectionDataInput input =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        GetAdSelectionDataTestCallback getAdSelectionDataTestCallback =
                invokeGetAdSelectionData(mAdSelectionService, input);
        long adSelectionId =
                getAdSelectionDataTestCallback.mGetAdSelectionDataResponse.getAdSelectionId();

        PersistAdSelectionResultInput persistAdSelectionResultInput =
                new PersistAdSelectionResultInput.Builder()
                        .setAdSelectionId(adSelectionId)
                        .setSeller(SELLER)
                        .setAdSelectionResult(prepareAuctionResultBytes())
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        PersistAdSelectionResultTestCallback persistAdSelectionResultTestCallback =
                invokePersistAdSelectionResult(mAdSelectionService, persistAdSelectionResultInput);

        Uri adRenderUriFromPersistAdSelectionResult =
                persistAdSelectionResultTestCallback.mPersistAdSelectionResultResponse
                        .getAdRenderUri();
        Assert.assertTrue(persistAdSelectionResultTestCallback.mIsSuccess);
        Assert.assertEquals(WINNER_AD_RENDER_URI, adRenderUriFromPersistAdSelectionResult);
        Assert.assertEquals(
                adSelectionId,
                persistAdSelectionResultTestCallback.mPersistAdSelectionResultResponse
                        .getAdSelectionId());
        Assert.assertEquals(
                BUYER_REPORTING_URI,
                mAdSelectionEntryDao
                        .getReportingUris(adSelectionId)
                        .getBuyerWinReportingUri()
                        .toString());
        Assert.assertEquals(
                SELLER_REPORTING_URI,
                mAdSelectionEntryDao
                        .getReportingUris(adSelectionId)
                        .getSellerWinReportingUri()
                        .toString());

        ReportImpressionInput reportImpressionInput =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(adSelectionId)
                        .setAdSelectionConfig(AdSelectionConfig.EMPTY)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        ReportImpressionTestCallback callback =
                invokeReportImpression(mAdSelectionService, reportImpressionInput);
        Assert.assertTrue(callback.mIsSuccess);
        boolean isCountdownDone =
                reportImpressionCountDownLatch.await(
                        COUNTDOWN_LATCH_LIMIT_SECONDS, TimeUnit.SECONDS);
        Assert.assertTrue(isCountdownDone);
        verify(mAdServicesHttpsClientSpy, times(1))
                .getAndReadNothing(eq(Uri.parse(SELLER_REPORTING_URI)), any());
        verify(mAdServicesHttpsClientSpy, times(1))
                .getAndReadNothing(eq(Uri.parse(BUYER_REPORTING_URI)), any());
    }

    @Test
    public void testPersistAdSelectionResult_withoutDecrypt_savesWinEventsSuccess()
            throws Exception {
        doReturn(mFlags).when(FlagsFactory::getFlags);

        mAdFilteringFeatureFactory =
                new AdFilteringFeatureFactory(mAppInstallDao, mFrequencyCapDaoSpy, mFlags);
        mAdSelectionService = createAdSelectionService();

        when(mObliviousHttpEncryptorMock.encryptBytes(
                        any(byte[].class), anyLong(), anyLong(), any()))
                .thenAnswer(
                        invocation ->
                                FluentFuture.from(immediateFuture(invocation.getArgument(0))));
        when(mObliviousHttpEncryptorMock.decryptBytes(any(byte[].class), anyLong()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mCustomAudienceDaoSpy.insertOrOverwriteCustomAudience(
                DBCustomAudienceFixture.getValidBuilderByBuyerWithAdRenderId(
                                WINNER_BUYER,
                                WINNING_CUSTOM_AUDIENCE_NAME,
                                WINNING_CUSTOM_AUDIENCE_OWNER)
                        .setAds(
                                DBAdDataFixture.getValidDbAdDataListByBuyerWithAdRenderId(
                                        WINNER_BUYER))
                        .build(),
                Uri.EMPTY,
                false);

        GetAdSelectionDataInput input =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        GetAdSelectionDataTestCallback getAdSelectionDataTestCallback =
                invokeGetAdSelectionData(mAdSelectionService, input);
        Assert.assertTrue(getAdSelectionDataTestCallback.mIsSuccess);
        long adSelectionId =
                getAdSelectionDataTestCallback.mGetAdSelectionDataResponse.getAdSelectionId();

        PersistAdSelectionResultInput persistAdSelectionResultInput =
                new PersistAdSelectionResultInput.Builder()
                        .setAdSelectionId(adSelectionId)
                        .setSeller(SELLER)
                        .setAdSelectionResult(prepareAuctionResultBytes())
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        PersistAdSelectionResultTestCallback persistAdSelectionResultTestCallback =
                invokePersistAdSelectionResult(mAdSelectionService, persistAdSelectionResultInput);
        Assert.assertTrue(persistAdSelectionResultTestCallback.mIsSuccess);

        // Assert fcap win reporting
        ArgumentCaptor<HistogramEvent> histogramEventArgumentCaptor =
                ArgumentCaptor.forClass(HistogramEvent.class);
        verify(mFrequencyCapDaoSpy, times(WINNER_AD_COUNTERS.size()))
                .insertHistogramEvent(
                        histogramEventArgumentCaptor.capture(),
                        anyInt(),
                        anyInt(),
                        anyInt(),
                        anyInt());
        List<HistogramEvent> capturedHistogramEventList =
                histogramEventArgumentCaptor.getAllValues();
        Assert.assertEquals(
                FrequencyCapFilters.AD_EVENT_TYPE_WIN,
                capturedHistogramEventList.get(0).getAdEventType());
        Assert.assertEquals(
                WINNER_AD_COUNTERS,
                capturedHistogramEventList.stream()
                        .map(HistogramEvent::getAdCounterKey)
                        .collect(Collectors.toSet()));
    }

    @Test
    public void testPersistAdSelectionResult_withoutDecrypt_savesNonWinEventsSuccess()
            throws Exception {
        doReturn(mFlags).when(FlagsFactory::getFlags);

        mAdFilteringFeatureFactory =
                new AdFilteringFeatureFactory(mAppInstallDao, mFrequencyCapDaoSpy, mFlags);
        mAdSelectionService = createAdSelectionService();

        when(mObliviousHttpEncryptorMock.encryptBytes(
                        any(byte[].class), anyLong(), anyLong(), any()))
                .thenAnswer(
                        invocation ->
                                FluentFuture.from(immediateFuture(invocation.getArgument(0))));
        when(mObliviousHttpEncryptorMock.decryptBytes(any(byte[].class), anyLong()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mCustomAudienceDaoSpy.insertOrOverwriteCustomAudience(
                DBCustomAudienceFixture.getValidBuilderByBuyerWithAdRenderId(
                                WINNER_BUYER,
                                WINNING_CUSTOM_AUDIENCE_NAME,
                                WINNING_CUSTOM_AUDIENCE_OWNER)
                        .setAds(
                                DBAdDataFixture.getValidDbAdDataListByBuyerWithAdRenderId(
                                        WINNER_BUYER))
                        .build(),
                Uri.EMPTY,
                false);

        GetAdSelectionDataInput input =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        GetAdSelectionDataTestCallback getAdSelectionDataTestCallback =
                invokeGetAdSelectionData(mAdSelectionService, input);
        Assert.assertTrue(getAdSelectionDataTestCallback.mIsSuccess);
        long adSelectionId =
                getAdSelectionDataTestCallback.mGetAdSelectionDataResponse.getAdSelectionId();

        PersistAdSelectionResultInput persistAdSelectionResultInput =
                new PersistAdSelectionResultInput.Builder()
                        .setAdSelectionId(adSelectionId)
                        .setSeller(SELLER)
                        .setAdSelectionResult(prepareAuctionResultBytes())
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        PersistAdSelectionResultTestCallback persistAdSelectionResultTestCallback =
                invokePersistAdSelectionResult(mAdSelectionService, persistAdSelectionResultInput);
        Assert.assertTrue(persistAdSelectionResultTestCallback.mIsSuccess);

        // Assert fcap non-win reporting
        UpdateAdCounterHistogramInput updateHistogramInput =
                new UpdateAdCounterHistogramInput.Builder(
                                adSelectionId,
                                FrequencyCapFilters.AD_EVENT_TYPE_VIEW,
                                SELLER,
                                CALLER_PACKAGE_NAME)
                        .build();
        UpdateAdCounterHistogramTestCallback updateHistogramCallback =
                invokeUpdateAdCounterHistogram(mAdSelectionService, updateHistogramInput);

        int numOfKeys = WINNER_AD_COUNTERS.size();
        ArgumentCaptor<HistogramEvent> histogramEventArgumentCaptor =
                ArgumentCaptor.forClass(HistogramEvent.class);
        Assert.assertTrue(updateHistogramCallback.mIsSuccess);
        verify(
                        mFrequencyCapDaoSpy,
                        // Each key is reported twice; WIN and VIEW events
                        times(2 * numOfKeys))
                .insertHistogramEvent(
                        histogramEventArgumentCaptor.capture(),
                        anyInt(),
                        anyInt(),
                        anyInt(),
                        anyInt());
        List<HistogramEvent> capturedHistogramEventList =
                histogramEventArgumentCaptor.getAllValues();
        Assert.assertEquals(
                FrequencyCapFilters.AD_EVENT_TYPE_WIN,
                capturedHistogramEventList.get(0).getAdEventType());
        Assert.assertEquals(
                FrequencyCapFilters.AD_EVENT_TYPE_VIEW,
                capturedHistogramEventList.get(numOfKeys).getAdEventType());
        Assert.assertEquals(
                WINNER_AD_COUNTERS,
                capturedHistogramEventList.subList(numOfKeys, 2 * numOfKeys).stream()
                        .map(HistogramEvent::getAdCounterKey)
                        .collect(Collectors.toSet()));
    }

    @Test
    public void testGetAdSelectionData_withOhttpGatewayDecryption() throws Exception {
        doReturn(mFlags).when(FlagsFactory::getFlags);

        String winnerBuyerCaOneName = "Shoes CA of Buyer 1";
        String winnerBuyerCaTwoName = "Shirts CA of Buyer 1";
        String differentBuyerCaOneName = "Shoes CA Of Buyer 2";

        Map<String, AdTechIdentifier> nameAndBuyersMap =
                Map.of(
                        winnerBuyerCaOneName, WINNER_BUYER,
                        winnerBuyerCaTwoName, WINNER_BUYER,
                        differentBuyerCaOneName, DIFFERENT_BUYER);
        createAndPersistDBCustomAudiences(nameAndBuyersMap);

        String privateKeyHex = "e7b292f49df28b8065992cdeadbc9d032a0e09e8476cb6d8d507212e7be3b9b4";
        OhttpGatewayPrivateKey privKey =
                OhttpGatewayPrivateKey.create(
                        BaseEncoding.base16().lowerCase().decode(privateKeyHex));
        DBEncryptionKey dbEncryptionKey =
                DBEncryptionKey.builder()
                        .setPublicKey("87ey8XZPXAd+/+ytKv2GFUWW5j9zdepSJ2G4gebDwyM=")
                        .setKeyIdentifier("400bed24-c62f-46e0-a1ad-211361ad771a")
                        .setEncryptionKeyType(ENCRYPTION_KEY_TYPE_AUCTION)
                        .setExpiryTtlSeconds(TimeUnit.DAYS.toSeconds(7))
                        .build();
        mAuctionServerEncryptionKeyDao.insertAllKeys(ImmutableList.of(dbEncryptionKey));

        String seed = "wwwwwwwwwwwwwwwwwwwwwwwwwwwwwwww";
        byte[] seedBytes = seed.getBytes(StandardCharsets.US_ASCII);
        AdSelectionService service =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDaoSpy,
                        mEncodedPayloadDaoSpy,
                        mFrequencyCapDaoSpy,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mAdServicesHttpsClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mContext,
                        mAdServicesLoggerMock,
                        mFlags,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterMock,
                        mAdSelectionServiceFilterMock,
                        mAdFilteringFeatureFactory,
                        mConsentManagerMock,
                        MultiCloudTestStrategyFactory.getDisabledTestStrategy(
                                new ObliviousHttpEncryptorWithSeedImpl(
                                        new AdSelectionEncryptionKeyManager(
                                                mAuctionServerEncryptionKeyDao,
                                                mFlags,
                                                mAdServicesHttpsClientSpy,
                                                mLightweightExecutorService),
                                        mEncryptionContextDao,
                                        seedBytes,
                                        mLightweightExecutorService)),
                        mAdSelectionDebugReportDaoSpy,
                        mAdIdFetcher,
                        mUnusedKAnonSignJoinFactory,
                        false,
                        mRetryStrategyFactory);

        GetAdSelectionDataInput input =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        GetAdSelectionDataTestCallback callback = invokeGetAdSelectionData(service, input);
        Assert.assertTrue(callback.mIsSuccess);
        byte[] adSelectionResponse = callback.mGetAdSelectionDataResponse.getAdSelectionData();

        ProtectedAuctionInput protectedAuctionInput =
                getProtectedAuctionInputFromCipherText(adSelectionResponse, privKey);

        Map<String, BuyerInput> buyerInputs = getDecompressedBuyerInputs(protectedAuctionInput);

        Assert.assertEquals(CALLER_PACKAGE_NAME, protectedAuctionInput.getPublisherName());
        Assert.assertEquals(2, buyerInputs.size());
        Assert.assertTrue(buyerInputs.containsKey(DIFFERENT_BUYER.toString()));
        Assert.assertTrue(buyerInputs.containsKey(WINNER_BUYER.toString()));
        Assert.assertEquals(
                1, buyerInputs.get(DIFFERENT_BUYER.toString()).getCustomAudiencesList().size());
        Assert.assertEquals(
                2, buyerInputs.get(WINNER_BUYER.toString()).getCustomAudiencesList().size());

        List<String> actual =
                Arrays.asList(
                        buyerInputs.get(WINNER_BUYER.toString()).getCustomAudiences(0).getName(),
                        buyerInputs.get(WINNER_BUYER.toString()).getCustomAudiences(1).getName());
        List<String> expected = Arrays.asList(winnerBuyerCaOneName, winnerBuyerCaTwoName);
        Assert.assertTrue(expected.containsAll(actual));
    }

    @Test
    public void testGetAdSelectionData_multiCloudOn_success() throws Exception {
        mFlags =
                new AuctionServerE2ETestFlags(
                        false,
                        false,
                        AUCTION_SERVER_AD_ID_FETCHER_TIMEOUT_MS,
                        true,
                        COORDINATOR_ALLOWLIST,
                        false);
        doReturn(mFlags).when(FlagsFactory::getFlags);

        String privateKeyHex = "e7b292f49df28b8065992cdeadbc9d032a0e09e8476cb6d8d507212e7be3b9b4";
        OhttpGatewayPrivateKey privKey =
                OhttpGatewayPrivateKey.create(
                        BaseEncoding.base16().lowerCase().decode(privateKeyHex));
        AuctionEncryptionKeyFixture.AuctionKey auctionKey =
                AuctionEncryptionKeyFixture.AuctionKey.builder()
                        .setKeyId("400bed24-c62f-46e0-a1ad-211361ad771a")
                        .setPublicKey("87ey8XZPXAd+/+ytKv2GFUWW5j9zdepSJ2G4gebDwyM=")
                        .build();

        AdServicesHttpClientResponse httpClientResponse =
                AuctionEncryptionKeyFixture.mockAuctionKeyFetchResponseWithGivenKey(auctionKey);
        when(mMockHttpClient.fetchPayload(
                        Uri.parse(COORDINATOR_URL), DevContext.createForDevOptionsDisabled()))
                .thenReturn(Futures.immediateFuture(httpClientResponse));

        doReturn(mFlags).when(FlagsFactory::getFlags);

        mCustomAudienceDaoSpy.insertOrOverwriteCustomAudience(
                DBCustomAudienceFixture.getValidBuilderByBuyerWithAdRenderId(
                                WINNER_BUYER,
                                WINNING_CUSTOM_AUDIENCE_NAME,
                                WINNING_CUSTOM_AUDIENCE_OWNER)
                        .setAds(
                                DBAdDataFixture.getValidDbAdDataListByBuyerWithAdRenderId(
                                        WINNER_BUYER))
                        .build(),
                Uri.EMPTY,
                false);

        AdSelectionService service =
                getService(
                        MultiCloudTestStrategyFactory.getEnabledTestStrategy(
                                new ObliviousHttpEncryptorImpl(
                                        new ProtectedServersEncryptionConfigManager(
                                                mProtectedServersEncryptionConfigDao,
                                                mFlags,
                                                mMockHttpClient,
                                                mLightweightExecutorService),
                                        mEncryptionContextDao,
                                        mLightweightExecutorService),
                                COORDINATOR_ALLOWLIST));

        GetAdSelectionDataInput input =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .setCoordinatorOriginUri(Uri.parse(COORDINATOR_HOST))
                        .build();

        GetAdSelectionDataTestCallback callback = invokeGetAdSelectionData(service, input);

        Assert.assertTrue(callback.mIsSuccess);
        long adSelectionId = callback.mGetAdSelectionDataResponse.getAdSelectionId();
        Assert.assertNotNull(
                mEncryptionContextDao.getEncryptionContext(
                        adSelectionId, ENCRYPTION_KEY_TYPE_AUCTION));


        ProtectedAuctionInput protectedAuctionInput =
                getProtectedAuctionInputFromCipherText(
                        callback.mGetAdSelectionDataResponse.getAdSelectionData(), privKey);

        Map<String, BuyerInput> buyerInputs = getDecompressedBuyerInputs(protectedAuctionInput);

        Assert.assertEquals(CALLER_PACKAGE_NAME, protectedAuctionInput.getPublisherName());
        Assert.assertEquals(1, buyerInputs.size());
        Assert.assertTrue(buyerInputs.containsKey(WINNER_BUYER.toString()));
        Assert.assertEquals(
                1, buyerInputs.get(WINNER_BUYER.toString()).getCustomAudiencesList().size());
        Assert.assertEquals(
                WINNING_CUSTOM_AUDIENCE_NAME,
                buyerInputs.get(WINNER_BUYER.toString()).getCustomAudiences(0).getName());

        // assert that we can decrypt server's response as well even when using non-default
        // coordinator
        byte[] encryptedServerResponse =
                ObliviousHttpGateway.encrypt(
                        privKey,
                        callback.mGetAdSelectionDataResponse.getAdSelectionData(),
                        prepareAuctionResultBytes());
        PersistAdSelectionResultInput persistAdSelectionResultInput =
                new PersistAdSelectionResultInput.Builder()
                        .setAdSelectionId(adSelectionId)
                        .setSeller(SELLER)
                        .setAdSelectionResult(encryptedServerResponse)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();
        PersistAdSelectionResultTestCallback persistAdSelectionResultTestCallback =
                invokePersistAdSelectionResult(service, persistAdSelectionResultInput);

        Assert.assertTrue(persistAdSelectionResultTestCallback.mIsSuccess);
        Assert.assertEquals(
                adSelectionId,
                persistAdSelectionResultTestCallback.mPersistAdSelectionResultResponse
                        .getAdSelectionId());
        Assert.assertEquals(
                WINNER_AD_RENDER_URI,
                persistAdSelectionResultTestCallback.mPersistAdSelectionResultResponse
                        .getAdRenderUri());
    }

    @Test
    public void testGetAdSelectionData_multiCloudOn_nullCoordinator_success() throws Exception {
        mFlags =
                new AuctionServerE2ETestFlags(
                        false,
                        false,
                        AUCTION_SERVER_AD_ID_FETCHER_TIMEOUT_MS,
                        true,
                        COORDINATOR_ALLOWLIST,
                        false);
        doReturn(mFlags).when(FlagsFactory::getFlags);

        AuctionEncryptionKeyFixture.AuctionKey auctionKey =
                AuctionEncryptionKeyFixture.AuctionKey.builder()
                        .setKeyId("400bed24-c62f-46e0-a1ad-211361ad771a")
                        .setPublicKey("87ey8XZPXAd+/+ytKv2GFUWW5j9zdepSJ2G4gebDwyM=")
                        .build();

        AdServicesHttpClientResponse httpClientResponse =
                AuctionEncryptionKeyFixture.mockAuctionKeyFetchResponseWithGivenKey(auctionKey);
        when(mMockHttpClient.fetchPayload(
                        Uri.parse(DEFAULT_FETCH_URI), DevContext.createForDevOptionsDisabled()))
                .thenReturn(Futures.immediateFuture(httpClientResponse));

        doReturn(mFlags).when(FlagsFactory::getFlags);

        Map<String, AdTechIdentifier> nameAndBuyersMap =
                Map.of(
                        "Shoes CA of Buyer 1", WINNER_BUYER,
                        "Shirts CA of Buyer 1", WINNER_BUYER,
                        "Shoes CA Of Buyer 2", DIFFERENT_BUYER);
        createAndPersistDBCustomAudiences(nameAndBuyersMap);

        AdSelectionService service =
                getService(
                        MultiCloudTestStrategyFactory.getEnabledTestStrategy(
                                new ObliviousHttpEncryptorImpl(
                                        new ProtectedServersEncryptionConfigManager(
                                                mProtectedServersEncryptionConfigDao,
                                                mFlags,
                                                mMockHttpClient,
                                                mLightweightExecutorService),
                                        mEncryptionContextDao,
                                        mLightweightExecutorService),
                                COORDINATOR_ALLOWLIST));

        GetAdSelectionDataInput input =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        GetAdSelectionDataTestCallback callback = invokeGetAdSelectionData(service, input);

        Assert.assertTrue(callback.mIsSuccess);
        Assert.assertNotNull(callback.mGetAdSelectionDataResponse);
        Assert.assertNotNull(callback.mGetAdSelectionDataResponse.getAdSelectionData());
        long adSelectionId = callback.mGetAdSelectionDataResponse.getAdSelectionId();
        byte[] encryptedBytes = callback.mGetAdSelectionDataResponse.getAdSelectionData();
        Assert.assertNotNull(encryptedBytes);
        Assert.assertNotNull(
                mEncryptionContextDao.getEncryptionContext(
                        adSelectionId, ENCRYPTION_KEY_TYPE_AUCTION));
    }

    @Test
    public void testGetAdSelectionData_multiCloudOn_inValidCoordinator_fails() throws Exception {
        mFlags =
                new AuctionServerE2ETestFlags(
                        false,
                        false,
                        AUCTION_SERVER_AD_ID_FETCHER_TIMEOUT_MS,
                        true,
                        COORDINATOR_ALLOWLIST,
                        false);
        doReturn(mFlags).when(FlagsFactory::getFlags);

        AuctionEncryptionKeyFixture.AuctionKey auctionKey =
                AuctionEncryptionKeyFixture.AuctionKey.builder()
                        .setKeyId("400bed24-c62f-46e0-a1ad-211361ad771a")
                        .setPublicKey("87ey8XZPXAd+/+ytKv2GFUWW5j9zdepSJ2G4gebDwyM=")
                        .build();

        AdServicesHttpClientResponse httpClientResponse =
                AuctionEncryptionKeyFixture.mockAuctionKeyFetchResponseWithGivenKey(auctionKey);
        when(mMockHttpClient.fetchPayload(
                        Uri.parse(COORDINATOR_URL), DevContext.createForDevOptionsDisabled()))
                .thenReturn(Futures.immediateFuture(httpClientResponse));

        doReturn(mFlags).when(FlagsFactory::getFlags);

        Map<String, AdTechIdentifier> nameAndBuyersMap =
                Map.of(
                        "Shoes CA of Buyer 1", WINNER_BUYER,
                        "Shirts CA of Buyer 1", WINNER_BUYER,
                        "Shoes CA Of Buyer 2", DIFFERENT_BUYER);
        createAndPersistDBCustomAudiences(nameAndBuyersMap);

        AdSelectionService service =
                getService(
                        MultiCloudTestStrategyFactory.getEnabledTestStrategy(
                                new ObliviousHttpEncryptorImpl(
                                        new ProtectedServersEncryptionConfigManager(
                                                mProtectedServersEncryptionConfigDao,
                                                mFlags,
                                                mMockHttpClient,
                                                mLightweightExecutorService),
                                        mEncryptionContextDao,
                                        mLightweightExecutorService),
                                COORDINATOR_ALLOWLIST));

        GetAdSelectionDataInput input =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .setCoordinatorOriginUri(Uri.parse("a/b"))
                        .build();

        GetAdSelectionDataTestCallback callback = invokeGetAdSelectionData(service, input);

        Assert.assertFalse(callback.mIsSuccess);
        Assert.assertEquals(STATUS_INVALID_ARGUMENT, callback.mFledgeErrorResponse.getStatusCode());
    }

    @Test
    public void testGetAdSelectionData_multiCloudOff_nullCoordinator_success() throws Exception {
        mFlags =
                new AuctionServerE2ETestFlags(
                        false,
                        false,
                        AUCTION_SERVER_AD_ID_FETCHER_TIMEOUT_MS,
                        false,
                        COORDINATOR_ALLOWLIST,
                        false);
        doReturn(mFlags).when(FlagsFactory::getFlags);

        AuctionEncryptionKeyFixture.AuctionKey auctionKey =
                AuctionEncryptionKeyFixture.AuctionKey.builder()
                        .setKeyId("400bed24-c62f-46e0-a1ad-211361ad771a")
                        .setPublicKey("87ey8XZPXAd+/+ytKv2GFUWW5j9zdepSJ2G4gebDwyM=")
                        .build();

        AdServicesHttpClientResponse httpClientResponse =
                AuctionEncryptionKeyFixture.mockAuctionKeyFetchResponseWithGivenKey(auctionKey);
        when(mMockHttpClient.fetchPayload(
                        Uri.parse(DEFAULT_FETCH_URI), DevContext.createForDevOptionsDisabled()))
                .thenReturn(Futures.immediateFuture(httpClientResponse));

        doReturn(mFlags).when(FlagsFactory::getFlags);

        Map<String, AdTechIdentifier> nameAndBuyersMap =
                Map.of(
                        "Shoes CA of Buyer 1", WINNER_BUYER,
                        "Shirts CA of Buyer 1", WINNER_BUYER,
                        "Shoes CA Of Buyer 2", DIFFERENT_BUYER);
        createAndPersistDBCustomAudiences(nameAndBuyersMap);

        AdSelectionService service =
                getService(
                        MultiCloudTestStrategyFactory.getDisabledTestStrategy(
                                (new ObliviousHttpEncryptorImpl(
                                        new AdSelectionEncryptionKeyManager(
                                                mAuctionServerEncryptionKeyDao,
                                                mFlags,
                                                mMockHttpClient,
                                                mLightweightExecutorService),
                                        mEncryptionContextDao,
                                        mLightweightExecutorService))));

        GetAdSelectionDataInput input =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .setCoordinatorOriginUri(Uri.parse(COORDINATOR_HOST))
                        .build();

        GetAdSelectionDataTestCallback callback = invokeGetAdSelectionData(service, input);

        Assert.assertTrue(callback.mIsSuccess);
        Assert.assertNotNull(callback.mGetAdSelectionDataResponse);
        Assert.assertNotNull(callback.mGetAdSelectionDataResponse.getAdSelectionData());
        long adSelectionId = callback.mGetAdSelectionDataResponse.getAdSelectionId();
        byte[] encryptedBytes = callback.mGetAdSelectionDataResponse.getAdSelectionData();
        Assert.assertNotNull(encryptedBytes);
        Assert.assertNotNull(
                mEncryptionContextDao.getEncryptionContext(
                        adSelectionId, ENCRYPTION_KEY_TYPE_AUCTION));

    }

    private AdSelectionServiceImpl getService(MultiCloudSupportStrategy multiCloudSupportStrategy) {
        return new AdSelectionServiceImpl(
                mAdSelectionEntryDao,
                mAppInstallDao,
                mCustomAudienceDaoSpy,
                mEncodedPayloadDaoSpy,
                mFrequencyCapDaoSpy,
                mEncryptionKeyDao,
                mEnrollmentDao,
                mAdServicesHttpsClientSpy,
                mDevContextFilterMock,
                mLightweightExecutorService,
                mBackgroundExecutorService,
                mScheduledExecutor,
                mContext,
                mAdServicesLoggerMock,
                mFlags,
                CallingAppUidSupplierProcessImpl.create(),
                mFledgeAuthorizationFilterMock,
                mAdSelectionServiceFilterMock,
                mAdFilteringFeatureFactory,
                mConsentManagerMock,
                multiCloudSupportStrategy,
                mAdSelectionDebugReportDaoSpy,
                mAdIdFetcher,
                mUnusedKAnonSignJoinFactory,
                false,
                mRetryStrategyFactory);
    }

    private ProtectedAuctionInput getProtectedAuctionInputFromCipherText(
            byte[] adSelectionResponse, OhttpGatewayPrivateKey privKey) throws Exception {
        byte[] decrypted = ObliviousHttpGateway.decrypt(privKey, adSelectionResponse);
        AuctionServerPayloadExtractor extractor =
                AuctionServerPayloadFormatterFactory.createPayloadExtractor(
                        AuctionServerPayloadFormatterV0.VERSION, mAdServicesLoggerMock);
        AuctionServerPayloadUnformattedData unformatted =
                extractor.extract(AuctionServerPayloadFormattedData.create(decrypted));
        return ProtectedAuctionInput.parseFrom(unformatted.getData());
    }

    private Map<String, BuyerInput> getDecompressedBuyerInputs(
            ProtectedAuctionInput protectedAuctionInput) throws Exception {
        Map<String, BuyerInput> decompressedBuyerInputs = new HashMap<>();
        for (Map.Entry<String, ByteString> entry :
                protectedAuctionInput.getBuyerInputMap().entrySet()) {
            byte[] buyerInputBytes = entry.getValue().toByteArray();
            AuctionServerDataCompressor compressor =
                    AuctionServerDataCompressorFactory.getDataCompressor(
                            AuctionServerDataCompressorGzip.VERSION);
            byte[] decompressed =
                    compressor
                            .decompress(
                                    AuctionServerDataCompressor.CompressedData.create(
                                            buyerInputBytes))
                            .getData();
            decompressedBuyerInputs.put(entry.getKey(), BuyerInput.parseFrom(decompressed));
        }
        return decompressedBuyerInputs;
    }

    private void testGetAdSelectionData_withEncryptHelper(Flags flags) throws Exception {
        doReturn(flags).when(FlagsFactory::getFlags);

        Map<String, AdTechIdentifier> nameAndBuyersMap =
                Map.of(
                        "Shoes CA of Buyer 1", WINNER_BUYER,
                        "Shirts CA of Buyer 1", WINNER_BUYER,
                        "Shoes CA Of Buyer 2", DIFFERENT_BUYER);
        createAndPersistDBCustomAudiences(nameAndBuyersMap);

        DBEncryptionKey dbEncryptionKey =
                DBEncryptionKey.builder()
                        .setPublicKey("bSHP4J++pRIvnrwusqafzE8GQIzVSqyTTwEudvzc72I=")
                        .setKeyIdentifier("050bed24-c62f-46e0-a1ad-211361ad771a")
                        .setEncryptionKeyType(ENCRYPTION_KEY_TYPE_AUCTION)
                        .setExpiryTtlSeconds(TimeUnit.DAYS.toSeconds(7))
                        .build();
        mAuctionServerEncryptionKeyDao.insertAllKeys(ImmutableList.of(dbEncryptionKey));

        String seed = "wwwwwwwwwwwwwwwwwwwwwwwwwwwwwwww";
        byte[] seedBytes = seed.getBytes(StandardCharsets.US_ASCII);
        AdSelectionService service =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDaoSpy,
                        mEncodedPayloadDaoSpy,
                        mFrequencyCapDaoSpy,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mAdServicesHttpsClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mContext,
                        mAdServicesLoggerMock,
                        flags,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterMock,
                        mAdSelectionServiceFilterMock,
                        mAdFilteringFeatureFactory,
                        mConsentManagerMock,
                        MultiCloudTestStrategyFactory.getDisabledTestStrategy(
                                new ObliviousHttpEncryptorWithSeedImpl(
                                        new AdSelectionEncryptionKeyManager(
                                                mAuctionServerEncryptionKeyDao,
                                                mFlags,
                                                mAdServicesHttpsClientSpy,
                                                mLightweightExecutorService),
                                        mEncryptionContextDao,
                                        seedBytes,
                                        mLightweightExecutorService)),
                        mAdSelectionDebugReportDaoSpy,
                        mAdIdFetcher,
                        mUnusedKAnonSignJoinFactory,
                        false,
                        mRetryStrategyFactory);

        GetAdSelectionDataInput input =
                new GetAdSelectionDataInput.Builder()
                        .setSeller(SELLER)
                        .setCallerPackageName(CALLER_PACKAGE_NAME)
                        .build();

        GetAdSelectionDataTestCallback callback = invokeGetAdSelectionData(service, input);

        Assert.assertTrue(callback.mIsSuccess);
        Assert.assertNotNull(callback.mGetAdSelectionDataResponse);
        Assert.assertNotNull(callback.mGetAdSelectionDataResponse.getAdSelectionData());
        long adSelectionId = callback.mGetAdSelectionDataResponse.getAdSelectionId();
        byte[] encryptedBytes = callback.mGetAdSelectionDataResponse.getAdSelectionData();
        Assert.assertNotNull(encryptedBytes);
        Assert.assertNotNull(
                mEncryptionContextDao.getEncryptionContext(
                        adSelectionId, ENCRYPTION_KEY_TYPE_AUCTION));
    }

    /**
     * Asserts if a {@link BuyerInput.CustomAudience} and {@link DBCustomAudience} objects are
     * equal.
     */
    private void assertEquals(
            BuyerInput.CustomAudience buyerInputCA, DBCustomAudience dbCustomAudience) {
        Assert.assertEquals(buyerInputCA.getName(), dbCustomAudience.getName());
        Assert.assertNotNull(dbCustomAudience.getTrustedBiddingData());
        Assert.assertEquals(
                buyerInputCA.getBiddingSignalsKeysList(),
                dbCustomAudience.getTrustedBiddingData().getKeys());
        Assert.assertNotNull(dbCustomAudience.getUserBiddingSignals());
        Assert.assertEquals(
                buyerInputCA.getUserBiddingSignals(),
                dbCustomAudience.getUserBiddingSignals().toString());
    }

    private AdSelectionService createAdSelectionService() {
        return new AdSelectionServiceImpl(
                mAdSelectionEntryDao,
                mAppInstallDao,
                mCustomAudienceDaoSpy,
                mEncodedPayloadDaoSpy,
                mFrequencyCapDaoSpy,
                mEncryptionKeyDao,
                mEnrollmentDao,
                mAdServicesHttpsClientSpy,
                mDevContextFilterMock,
                mLightweightExecutorService,
                mBackgroundExecutorService,
                mScheduledExecutor,
                mContext,
                mAdServicesLoggerMock,
                mFlags,
                CallingAppUidSupplierProcessImpl.create(),
                mFledgeAuthorizationFilterMock,
                mAdSelectionServiceFilterMock,
                mAdFilteringFeatureFactory,
                mConsentManagerMock,
                mMultiCloudSupportStrategy,
                mAdSelectionDebugReportDaoSpy,
                mAdIdFetcher,
                mUnusedKAnonSignJoinFactory,
                false,
                mRetryStrategyFactory);
    }

    private Map<AdTechIdentifier, BuyerInput> getBuyerInputMapFromDecryptedBytes(
            byte[] decryptedBytes) {
        try {
            byte[] unformatted =
                    mPayloadExtractor
                            .extract(AuctionServerPayloadFormattedData.create(decryptedBytes))
                            .getData();
            ProtectedAuctionInput protectedAuctionInput =
                    ProtectedAuctionInput.parseFrom(unformatted);
            Map<String, ByteString> buyerInputBytesMap = protectedAuctionInput.getBuyerInputMap();
            Function<Map.Entry<String, ByteString>, AdTechIdentifier> entryToAdTechIdentifier =
                    entry -> AdTechIdentifier.fromString(entry.getKey());
            Function<Map.Entry<String, ByteString>, BuyerInput> entryToBuyerInput =
                    entry -> {
                        try {
                            byte[] compressedBytes = entry.getValue().toByteArray();
                            byte[] decompressedBytes =
                                    mDataCompressor
                                            .decompress(
                                                    AuctionServerDataCompressor.CompressedData
                                                            .create(compressedBytes))
                                            .getData();
                            return BuyerInput.parseFrom(decompressedBytes);
                        } catch (InvalidProtocolBufferException e) {
                            throw new UncheckedIOException(e);
                        }
                    };
            return buyerInputBytesMap.entrySet().stream()
                    .collect(Collectors.toMap(entryToAdTechIdentifier, entryToBuyerInput));
        } catch (InvalidProtocolBufferException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Map<String, DBCustomAudience> createAndPersistDBCustomAudiences(
            Map<String, AdTechIdentifier> nameAndBuyers) {
        Map<String, DBCustomAudience> customAudiences = new HashMap<>();
        for (Map.Entry<String, AdTechIdentifier> entry : nameAndBuyers.entrySet()) {
            AdTechIdentifier buyer = entry.getValue();
            String name = entry.getKey();
            DBCustomAudience thisCustomAudience =
                    DBCustomAudienceFixture.getValidBuilderByBuyerWithAdRenderId(buyer, name)
                            .build();
            customAudiences.put(name, thisCustomAudience);
            mCustomAudienceDaoSpy.insertOrOverwriteCustomAudience(
                    thisCustomAudience, Uri.EMPTY, false);
        }
        return customAudiences;
    }

    private DBCustomAudience createAndPersistDBCustomAudienceWithOmitAdsEnabled(
            String name, AdTechIdentifier buyer) {
        DBCustomAudience thisCustomAudience =
                DBCustomAudienceFixture.getValidBuilderByBuyerWithOmitAdsEnabled(buyer, name)
                        .build();
        mCustomAudienceDaoSpy.insertOrOverwriteCustomAudience(thisCustomAudience, Uri.EMPTY, false);
        return thisCustomAudience;
    }

    private byte[] prepareAuctionResultBytes() {
        byte[] auctionResultBytes = AUCTION_RESULT.toByteArray();
        AuctionServerDataCompressor.CompressedData compressedData =
                mDataCompressor.compress(
                        AuctionServerDataCompressor.UncompressedData.create(auctionResultBytes));
        AuctionServerPayloadFormattedData formattedData =
                mPayloadFormatter.apply(
                        AuctionServerPayloadUnformattedData.create(compressedData.getData()),
                        AuctionServerDataCompressorGzip.VERSION);
        return formattedData.getData();
    }

    private List<String> extractCAAdRenderIdListFromBuyerInput(
            GetAdSelectionDataTestCallback callback,
            AdTechIdentifier buyer,
            String name,
            String owner) {
        List<BuyerInput.CustomAudience> customAudienceList =
                getBuyerInputMapFromDecryptedBytes(
                                callback.mGetAdSelectionDataResponse.getAdSelectionData())
                        .get(buyer)
                        .getCustomAudiencesList();
        Optional<BuyerInput.CustomAudience> winningCustomAudienceFromBuyerInputOption =
                customAudienceList.stream()
                        .filter(ca -> ca.getName().equals(name) && ca.getOwner().equals(owner))
                        .findFirst();
        Assert.assertTrue(winningCustomAudienceFromBuyerInputOption.isPresent());
        return winningCustomAudienceFromBuyerInputOption.get().getAdRenderIdsList();
    }

    private DBAdData getFilterableAndServerEligibleAd(int sequenceNumber, int filterMaxCount) {
        KeyedFrequencyCap fCap =
                new KeyedFrequencyCap.Builder(sequenceNumber, filterMaxCount, ONE_DAY_DURATION)
                        .build();
        FrequencyCapFilters clickEventFilter =
                new FrequencyCapFilters.Builder()
                        .setKeyedFrequencyCapsForClickEvents(ImmutableList.of(fCap))
                        .build();
        return getValidDbAdDataNoFiltersBuilder(WINNER_BUYER, sequenceNumber)
                .setAdCounterKeys(ImmutableSet.<Integer>builder().add(sequenceNumber).build())
                .setAdFilters(
                        new AdFilters.Builder().setFrequencyCapFilters(clickEventFilter).build())
                .setAdRenderId(String.valueOf(sequenceNumber))
                .build();
    }

    public GetAdSelectionDataTestCallback invokeGetAdSelectionData(
            AdSelectionService service, GetAdSelectionDataInput input)
            throws RemoteException, InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        GetAdSelectionDataTestCallback callback =
                new GetAdSelectionDataTestCallback(countDownLatch);
        service.getAdSelectionData(input, sCallerMetadata, callback);
        callback.mCountDownLatch.await();
        return callback;
    }

    public PersistAdSelectionResultTestCallback invokePersistAdSelectionResult(
            AdSelectionService service, PersistAdSelectionResultInput input)
            throws RemoteException, InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        PersistAdSelectionResultTestCallback callback =
                new PersistAdSelectionResultTestCallback(countDownLatch);
        service.persistAdSelectionResult(input, sCallerMetadata, callback);
        callback.mCountDownLatch.await();
        return callback;
    }

    public AdSelectionFromOutcomesTestCallback invokeAdSelectionFromOutcomes(
            AdSelectionService service, AdSelectionFromOutcomesInput input)
            throws RemoteException, InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        AdSelectionFromOutcomesTestCallback callback =
                new AdSelectionFromOutcomesTestCallback(countDownLatch);
        service.selectAdsFromOutcomes(input, null, callback);
        callback.mCountDownLatch.await();
        return callback;
    }

    public ReportImpressionTestCallback invokeReportImpression(
            AdSelectionService service, ReportImpressionInput input)
            throws RemoteException, InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        ReportImpressionTestCallback callback = new ReportImpressionTestCallback(countDownLatch);
        service.reportImpression(input, callback);
        callback.mCountDownLatch.await();
        return callback;
    }

    public ReportInteractionsTestCallback invokeReportInteractions(
            AdSelectionService service, ReportInteractionInput input)
            throws RemoteException, InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        ReportInteractionsTestCallback callback =
                new ReportInteractionsTestCallback(countDownLatch);
        service.reportInteraction(input, callback);
        callback.mCountDownLatch.await();
        return callback;
    }

    public UpdateAdCounterHistogramTestCallback invokeUpdateAdCounterHistogram(
            AdSelectionService service, UpdateAdCounterHistogramInput input)
            throws RemoteException, InterruptedException {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        UpdateAdCounterHistogramTestCallback callback =
                new UpdateAdCounterHistogramTestCallback(countDownLatch);
        service.updateAdCounterHistogram(input, callback);
        callback.mCountDownLatch.await();
        return callback;
    }

    static class GetAdSelectionDataTestCallback extends GetAdSelectionDataCallback.Stub {
        final CountDownLatch mCountDownLatch;
        boolean mIsSuccess = false;
        GetAdSelectionDataResponse mGetAdSelectionDataResponse;
        FledgeErrorResponse mFledgeErrorResponse;

        GetAdSelectionDataTestCallback(CountDownLatch countDownLatch) {
            mCountDownLatch = countDownLatch;
            mGetAdSelectionDataResponse = null;
            mFledgeErrorResponse = null;
        }

        @Override
        public void onSuccess(GetAdSelectionDataResponse getAdSelectionDataResponse)
                throws RemoteException {
            mIsSuccess = true;
            mGetAdSelectionDataResponse = getAdSelectionDataResponse;
            mCountDownLatch.countDown();
        }

        @Override
        public void onFailure(FledgeErrorResponse fledgeErrorResponse) throws RemoteException {
            mIsSuccess = false;
            mFledgeErrorResponse = fledgeErrorResponse;
            mCountDownLatch.countDown();
        }
    }

    static class PersistAdSelectionResultTestCallback
            extends PersistAdSelectionResultCallback.Stub {
        final CountDownLatch mCountDownLatch;
        boolean mIsSuccess = false;
        PersistAdSelectionResultResponse mPersistAdSelectionResultResponse;
        FledgeErrorResponse mFledgeErrorResponse;

        PersistAdSelectionResultTestCallback(CountDownLatch countDownLatch) {
            mCountDownLatch = countDownLatch;
            mPersistAdSelectionResultResponse = null;
            mFledgeErrorResponse = null;
        }

        @Override
        public void onSuccess(PersistAdSelectionResultResponse persistAdSelectionResultResponse)
                throws RemoteException {
            mIsSuccess = true;
            mPersistAdSelectionResultResponse = persistAdSelectionResultResponse;
            mCountDownLatch.countDown();
        }

        @Override
        public void onFailure(FledgeErrorResponse fledgeErrorResponse) throws RemoteException {
            mIsSuccess = false;
            mFledgeErrorResponse = fledgeErrorResponse;
            mCountDownLatch.countDown();
        }
    }

    static class AdSelectionFromOutcomesTestCallback extends AdSelectionCallback.Stub {

        final CountDownLatch mCountDownLatch;
        boolean mIsSuccess = false;
        AdSelectionResponse mAdSelectionResponse;
        FledgeErrorResponse mFledgeErrorResponse;

        AdSelectionFromOutcomesTestCallback(CountDownLatch countDownLatch) {
            mCountDownLatch = countDownLatch;
            mAdSelectionResponse = null;
            mFledgeErrorResponse = null;
        }

        @Override
        public void onSuccess(AdSelectionResponse adSelectionResponse) throws RemoteException {
            mIsSuccess = true;
            mAdSelectionResponse = adSelectionResponse;
            mCountDownLatch.countDown();
        }

        @Override
        public void onFailure(FledgeErrorResponse fledgeErrorResponse) throws RemoteException {
            mIsSuccess = false;
            mFledgeErrorResponse = fledgeErrorResponse;
            mCountDownLatch.countDown();
        }
    }

    static class ReportImpressionTestCallback extends ReportImpressionCallback.Stub {
        final CountDownLatch mCountDownLatch;
        boolean mIsSuccess = false;
        FledgeErrorResponse mFledgeErrorResponse;

        ReportImpressionTestCallback(CountDownLatch countDownLatch) {
            mCountDownLatch = countDownLatch;
            mFledgeErrorResponse = null;
        }

        @Override
        public void onSuccess() throws RemoteException {
            mIsSuccess = true;
            mCountDownLatch.countDown();
        }

        @Override
        public void onFailure(FledgeErrorResponse fledgeErrorResponse) throws RemoteException {
            mIsSuccess = false;
            mFledgeErrorResponse = fledgeErrorResponse;
            mCountDownLatch.countDown();
        }
    }

    static class ReportInteractionsTestCallback extends ReportInteractionCallback.Stub {
        final CountDownLatch mCountDownLatch;
        boolean mIsSuccess = false;
        FledgeErrorResponse mFledgeErrorResponse;

        ReportInteractionsTestCallback(CountDownLatch countDownLatch) {
            mCountDownLatch = countDownLatch;
            mFledgeErrorResponse = null;
        }

        @Override
        public void onSuccess() throws RemoteException {
            mIsSuccess = true;
            mCountDownLatch.countDown();
        }

        @Override
        public void onFailure(FledgeErrorResponse fledgeErrorResponse) throws RemoteException {
            mIsSuccess = false;
            mFledgeErrorResponse = fledgeErrorResponse;
            mCountDownLatch.countDown();
        }
    }

    static class UpdateAdCounterHistogramTestCallback
            extends UpdateAdCounterHistogramCallback.Stub {
        final CountDownLatch mCountDownLatch;
        boolean mIsSuccess = false;
        FledgeErrorResponse mFledgeErrorResponse;

        UpdateAdCounterHistogramTestCallback(CountDownLatch countDownLatch) {
            mCountDownLatch = countDownLatch;
            mFledgeErrorResponse = null;
        }

        @Override
        public void onSuccess() throws RemoteException {
            mIsSuccess = true;
            mCountDownLatch.countDown();
        }

        @Override
        public void onFailure(FledgeErrorResponse fledgeErrorResponse) throws RemoteException {
            mIsSuccess = false;
            mFledgeErrorResponse = fledgeErrorResponse;
            mCountDownLatch.countDown();
        }
    }

    static class AuctionServerE2ETestFlags implements Flags {
        private final boolean mFledgeAuctionServerKillSwitch;

        private final boolean mDebugReportingEnabled;

        private final long mAdIdFetcherTimeoutMs;

        private final boolean mMultiCloudEnabled;
        private final String mCoordinatorAllowlist;
        private final boolean mOmitAdsEnabled;

        AuctionServerE2ETestFlags() {
            this(false, false, 20, false);
        }

        AuctionServerE2ETestFlags(boolean omitAdsEnabled) {
            this(false, false, 20, omitAdsEnabled);
        }

        AuctionServerE2ETestFlags(
                boolean fledgeAuctionServerKillSwitch,
                boolean debugReportingEnabled,
                long adIdFetcherTimeoutMs,
                boolean omitAdsEnabled) {
            mFledgeAuctionServerKillSwitch = fledgeAuctionServerKillSwitch;
            mDebugReportingEnabled = debugReportingEnabled;
            mAdIdFetcherTimeoutMs = adIdFetcherTimeoutMs;
            mMultiCloudEnabled = false;
            mCoordinatorAllowlist = COORDINATOR_ALLOWLIST;
            mOmitAdsEnabled = omitAdsEnabled;
        }

        AuctionServerE2ETestFlags(
                boolean fledgeAuctionServerKillSwitch,
                boolean debugReportingEnabled,
                long adIdFetcherTimeoutMs,
                boolean multiCloudEnabled,
                String allowList,
                boolean omitAdsEnabled) {
            mFledgeAuctionServerKillSwitch = fledgeAuctionServerKillSwitch;
            mDebugReportingEnabled = debugReportingEnabled;
            mAdIdFetcherTimeoutMs = adIdFetcherTimeoutMs;
            mMultiCloudEnabled = multiCloudEnabled;
            mCoordinatorAllowlist = COORDINATOR_ALLOWLIST;
            mOmitAdsEnabled = omitAdsEnabled;
        }

        @Override
        public boolean getFledgeAdSelectionFilteringEnabled() {
            return true;
        }

        @Override
        public boolean getFledgeAuctionServerEnabledForUpdateHistogram() {
            return true;
        }

        @Override
        public boolean getFledgeAuctionServerEnabledForReportEvent() {
            return true;
        }

        @Override
        public boolean getFledgeAuctionServerEnabledForSelectAdsMediation() {
            return true;
        }

        @Override
        public boolean getFledgeRegisterAdBeaconEnabled() {
            return true;
        }

        @Override
        public boolean getFledgeAuctionServerKillSwitch() {
            return mFledgeAuctionServerKillSwitch;
        }

        @Override
        public boolean getFledgeAuctionServerEnabledForReportImpression() {
            return true;
        }

        @Override
        public boolean getFledgeAuctionServerEnableDebugReporting() {
            return mDebugReportingEnabled;
        }

        @Override
        public long getFledgeAuctionServerAdIdFetcherTimeoutMs() {
            return mAdIdFetcherTimeoutMs;
        }

        @Override
        public boolean getFledgeAuctionServerMultiCloudEnabled() {
            return mMultiCloudEnabled;
        }

        @Override
        public String getFledgeAuctionServerCoordinatorUrlAllowlist() {
            return mCoordinatorAllowlist;
        }

        @Override
        public String getFledgeAuctionServerAuctionKeyFetchUri() {
            return DEFAULT_FETCH_URI;
        }

        @Override
        public boolean getFledgeAuctionServerOmitAdsEnabled() {
            return mOmitAdsEnabled;
        }
    }
}
