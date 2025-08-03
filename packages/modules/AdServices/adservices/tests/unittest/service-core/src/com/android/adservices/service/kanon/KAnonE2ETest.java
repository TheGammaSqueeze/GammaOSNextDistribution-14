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

package com.android.adservices.service.kanon;

import static android.adservices.common.KeyedFrequencyCapFixture.ONE_DAY_DURATION;

import static com.android.adservices.common.DBAdDataFixture.getValidDbAdDataNoFiltersBuilder;
import static com.android.adservices.service.common.httpclient.AdServicesHttpUtil.OHTTP_CONTENT_TYPE;
import static com.android.adservices.service.common.httpclient.AdServicesHttpUtil.PROTOBUF_CONTENT_TYPE;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTest.sCallerMetadata;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyInt;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyLong;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.when;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateFuture;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

import android.adservices.adid.AdId;
import android.adservices.adselection.AdSelectionConfigFixture;
import android.adservices.adselection.AdSelectionService;
import android.adservices.adselection.GetAdSelectionDataCallback;
import android.adservices.adselection.GetAdSelectionDataInput;
import android.adservices.adselection.GetAdSelectionDataResponse;
import android.adservices.adselection.PersistAdSelectionResultCallback;
import android.adservices.adselection.PersistAdSelectionResultInput;
import android.adservices.adselection.PersistAdSelectionResultResponse;
import android.adservices.common.AdFilters;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CallingAppUidSupplierProcessImpl;
import android.adservices.common.CommonFixture;
import android.adservices.common.FledgeErrorResponse;
import android.adservices.common.FrequencyCapFilters;
import android.adservices.common.KeyedFrequencyCap;
import android.adservices.http.MockWebServerRule;
import android.content.Context;
import android.net.Uri;
import android.os.RemoteException;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.MockWebServerRuleFactory;
import com.android.adservices.common.AdServicesDeviceSupportedRule;
import com.android.adservices.common.DBAdDataFixture;
import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.customaudience.DBCustomAudienceFixture;
import com.android.adservices.data.adselection.AdSelectionDatabase;
import com.android.adservices.data.adselection.AdSelectionDebugReportDao;
import com.android.adservices.data.adselection.AdSelectionDebugReportingDatabase;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.adselection.AdSelectionServerDatabase;
import com.android.adservices.data.adselection.AppInstallDao;
import com.android.adservices.data.adselection.FrequencyCapDao;
import com.android.adservices.data.adselection.SharedStorageDatabase;
import com.android.adservices.data.common.DBAdData;
import com.android.adservices.data.common.UserProfileIdDao;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.CustomAudienceDatabase;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.kanon.ClientParametersDao;
import com.android.adservices.data.kanon.DBClientParameters;
import com.android.adservices.data.kanon.DBServerParameters;
import com.android.adservices.data.kanon.KAnonDatabase;
import com.android.adservices.data.kanon.KAnonMessageDao;
import com.android.adservices.data.kanon.ServerParametersDao;
import com.android.adservices.data.signals.EncodedPayloadDao;
import com.android.adservices.data.signals.ProtectedSignalsDatabase;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.adid.AdIdCacheManager;
import com.android.adservices.service.adselection.AdFilteringFeatureFactory;
import com.android.adservices.service.adselection.AdIdFetcher;
import com.android.adservices.service.adselection.AdSelectionServiceImpl;
import com.android.adservices.service.adselection.AuctionServerDataCompressor;
import com.android.adservices.service.adselection.AuctionServerDataCompressorFactory;
import com.android.adservices.service.adselection.AuctionServerDataCompressorGzip;
import com.android.adservices.service.adselection.AuctionServerPayloadExtractor;
import com.android.adservices.service.adselection.AuctionServerPayloadFormattedData;
import com.android.adservices.service.adselection.AuctionServerPayloadFormatter;
import com.android.adservices.service.adselection.AuctionServerPayloadFormatterFactory;
import com.android.adservices.service.adselection.AuctionServerPayloadUnformattedData;
import com.android.adservices.service.adselection.MockAdIdWorker;
import com.android.adservices.service.adselection.MultiCloudSupportStrategy;
import com.android.adservices.service.adselection.MultiCloudTestStrategyFactory;
import com.android.adservices.service.adselection.encryption.KAnonObliviousHttpEncryptorImpl;
import com.android.adservices.service.adselection.encryption.ObliviousHttpEncryptor;
import com.android.adservices.service.common.AdSelectionServiceFilter;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.common.RetryStrategyFactory;
import com.android.adservices.service.common.UserProfileIdManager;
import com.android.adservices.service.common.bhttp.BinaryHttpMessage;
import com.android.adservices.service.common.bhttp.BinaryHttpMessageDeserializer;
import com.android.adservices.service.common.bhttp.Fields;
import com.android.adservices.service.common.bhttp.RequestControlData;
import com.android.adservices.service.common.bhttp.ResponseControlData;
import com.android.adservices.service.common.cache.CacheProviderFactory;
import com.android.adservices.service.common.httpclient.AdServicesHttpUtil;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.devapi.DevContextFilter;
import com.android.adservices.service.js.JSScriptEngine;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.AuctionResult;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.BuyerInput;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.ProtectedAuctionInput;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.WinReportingUrls;
import com.android.adservices.service.proto.bidding_auction_servers.BiddingAuctionServers.WinReportingUrls.ReportingUrls;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.kanon.KAnonGetChallengeStatusStats;
import com.android.adservices.service.stats.kanon.KAnonInitializeStatusStats;
import com.android.adservices.service.stats.kanon.KAnonJoinStatusStats;
import com.android.adservices.service.stats.kanon.KAnonSignJoinStatsConstants;
import com.android.adservices.service.stats.kanon.KAnonSignStatusStats;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.FluentFuture;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;
import com.google.mockwebserver.RecordedRequest;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.function.Function;
import java.util.stream.Collectors;

import private_join_and_compute.anonymous_counting_tokens.ClientParameters;
import private_join_and_compute.anonymous_counting_tokens.GeneratedTokensRequestProto;
import private_join_and_compute.anonymous_counting_tokens.GetKeyAttestationChallengeResponse;
import private_join_and_compute.anonymous_counting_tokens.GetServerPublicParamsResponse;
import private_join_and_compute.anonymous_counting_tokens.GetTokensRequest;
import private_join_and_compute.anonymous_counting_tokens.GetTokensResponse;
import private_join_and_compute.anonymous_counting_tokens.RegisterClientRequest;
import private_join_and_compute.anonymous_counting_tokens.RegisterClientResponse;
import private_join_and_compute.anonymous_counting_tokens.ServerPublicParameters;
import private_join_and_compute.anonymous_counting_tokens.TokensSet;
import private_join_and_compute.anonymous_counting_tokens.Transcript;

public class KAnonE2ETest {
    private static final String CALLER_PACKAGE_NAME = CommonFixture.TEST_PACKAGE_NAME;
    private static final AdTechIdentifier SELLER = AdSelectionConfigFixture.SELLER;
    private static final AdTechIdentifier WINNER_BUYER = AdSelectionConfigFixture.BUYER;
    private static final DBAdData WINNER_AD =
            DBAdDataFixture.getValidDbAdDataListByBuyerWithAdRenderId(WINNER_BUYER).get(0);
    private static final Uri WINNER_AD_RENDER_URI = WINNER_AD.getRenderUri();
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
    private static final String GET_SERVER_PARAM_PATH = "/getServerParam";
    private static final String GET_CHALLENGE = "/getChallenge";
    private static final String REGISTER_CLIENT_PARAMETERS_PATH = "/registerClientParams";
    private static final String GET_TOKENS_PATH = "/getTokens";
    private static final String JOIN_PATH = "/join";
    private static final String SERVER_PARAM_VERSION = "serverParamVersion";
    private static final String CLIENT_PARAMS_VERSION = "clientParamsVersion";
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
    private EnrollmentDao mEnrollmentDao;
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

    private ClientParametersDao mClientParametersDao;
    private ServerParametersDao mServerParametersDao;
    private KAnonMessageDao mKAnonMessageDao;
    private KAnonMessageManager mKAnonMessageManager;

    private String mServerParamVersion;
    private ServerPublicParameters mServerPublicParameters;
    private Transcript mTranscript;

    @Mock private Clock mockClock;
    @Mock private com.android.adservices.shared.util.Clock mAdServicesClock;
    @Mock private UserProfileIdDao mockUserProfileIdDao;
    @Mock private KAnonObliviousHttpEncryptorImpl mockKAnonOblivivousHttpEncryptorImpl;
    @Captor private ArgumentCaptor<KAnonInitializeStatusStats> argumentCaptorInitializeStats;
    @Captor private ArgumentCaptor<KAnonGetChallengeStatusStats> argumentCaptorGetChallenge;
    @Captor private ArgumentCaptor<KAnonSignStatusStats> argumentCaptorSignStats;
    @Captor private ArgumentCaptor<KAnonJoinStatusStats> argumentCaptorJoinStats;
    @Mock private KeyAttestation mockKeyAttestation;
    @Mock private KeyAttestationCertificateChainRecord mockKeyAttestationCertificate;
    private UserProfileIdManager mUserProfileIdManager;
    private AnonymousCountingTokens mAnonymousCountingTokensSpy;

    private final Context CONTEXT = ApplicationProvider.getApplicationContext();
    private static final String GOLDEN_TRANSCRIPT_PATH = "act/golden_transcript_1";
    private KAnonSignJoinFactory mKAnonSignJoinFactory;

    private Instant FIXED_INSTANT = Instant.now();
    private RetryStrategyFactory mRetryStrategyFactory;

    @Before
    public void setUp() throws IOException {
        mLightweightExecutorService = AdServicesExecutors.getLightWeightExecutor();
        mBackgroundExecutorService = AdServicesExecutors.getBackgroundExecutor();
        mScheduledExecutor = AdServicesExecutors.getScheduler();
        mContext = ApplicationProvider.getApplicationContext();
        mFlags = new KAnonE2ETestFlags(false, 20, true, 100);
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

        mLightweightExecutorService = AdServicesExecutors.getLightWeightExecutor();
        KAnonDatabase kAnonDatabase =
                Room.inMemoryDatabaseBuilder(CONTEXT, KAnonDatabase.class).build();
        mClientParametersDao = kAnonDatabase.clientParametersDao();
        mServerParametersDao = kAnonDatabase.serverParametersDao();
        mUserProfileIdManager = new UserProfileIdManager(mockUserProfileIdDao, mAdServicesClock);
        mKAnonMessageDao = kAnonDatabase.kAnonMessageDao();
        mAnonymousCountingTokensSpy = spy(new AnonymousCountingTokensImpl());

        when(mockClock.instant()).thenReturn(FIXED_INSTANT);

        InputStream inputStream = CONTEXT.getAssets().open(GOLDEN_TRANSCRIPT_PATH);
        mTranscript = Transcript.parseDelimitedFrom(inputStream);
        mRetryStrategyFactory = RetryStrategyFactory.createInstanceForTesting();
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
    public void persistAdSelectionData_withKAnonImmediateValueZero_savesTheMessageInDB()
            throws Exception {
        Flags flagsWithKAnonEnabledAndImmediateSignValueZero =
                new KAnonE2ETestFlags(false, 20, true, 0);
        CountDownLatch countDownLatch = new CountDownLatch(1);
        doReturn(flagsWithKAnonEnabledAndImmediateSignValueZero).when(FlagsFactory::getFlags);
        mFlags = flagsWithKAnonEnabledAndImmediateSignValueZero;
        PersistAdSelectionResultInput persistAdSelectionResultInput =
                setupTestForPersistAdSelectionResult(countDownLatch);
        mAdSelectionService = createAdSelectionService();

        PersistAdSelectionResultTestCallback persistAdSelectionResultTestCallback =
                invokePersistAdSelectionResult(mAdSelectionService, persistAdSelectionResultInput);
        countDownLatch.await();

        assertThat(persistAdSelectionResultTestCallback.mIsSuccess).isTrue();
        List<KAnonMessageEntity> kAnonMessageEntityListNotProcessed =
                mKAnonMessageManager.fetchNKAnonMessagesWithStatus(
                        10, KAnonMessageEntity.KanonMessageEntityStatus.NOT_PROCESSED);
        assertThat(kAnonMessageEntityListNotProcessed.size()).isEqualTo(1);
        assertThat(kAnonMessageEntityListNotProcessed.get(0).getAdSelectionId())
                .isEqualTo(
                        persistAdSelectionResultTestCallback.mPersistAdSelectionResultResponse
                                .getAdSelectionId());
    }

    @Test
    public void persistAdSelectionData_withKAnonFeatureFlagDisabled_doesNothing() throws Exception {
        Flags flagsWithKAnonDisabled = new KAnonE2ETestFlags(false, 20, false, 0);
        CountDownLatch countDownLatch = new CountDownLatch(1);
        doReturn(flagsWithKAnonDisabled).when(FlagsFactory::getFlags);
        mFlags = flagsWithKAnonDisabled;
        PersistAdSelectionResultInput persistAdSelectionResultInput =
                setupTestForPersistAdSelectionResult(countDownLatch);
        mAdSelectionService = createAdSelectionService();

        PersistAdSelectionResultTestCallback persistAdSelectionResultTestCallback =
                invokePersistAdSelectionResult(mAdSelectionService, persistAdSelectionResultInput);
        countDownLatch.await();

        assertThat(persistAdSelectionResultTestCallback.mIsSuccess).isTrue();
        List<KAnonMessageEntity> kAnonMessageEntityListNotProcessed =
                mKAnonMessageManager.fetchNKAnonMessagesWithStatus(
                        10, KAnonMessageEntity.KanonMessageEntityStatus.NOT_PROCESSED);
        List<KAnonMessageEntity> kAnonMessageEntityListSigned =
                mKAnonMessageManager.fetchNKAnonMessagesWithStatus(
                        10, KAnonMessageEntity.KanonMessageEntityStatus.SIGNED);
        List<KAnonMessageEntity> kAnonMessageEntityListNotJoined =
                mKAnonMessageManager.fetchNKAnonMessagesWithStatus(
                        10, KAnonMessageEntity.KanonMessageEntityStatus.JOINED);
        List<KAnonMessageEntity> kAnonMessageEntityListNotFailed =
                mKAnonMessageManager.fetchNKAnonMessagesWithStatus(
                        10, KAnonMessageEntity.KanonMessageEntityStatus.FAILED);

        assertThat(kAnonMessageEntityListNotProcessed).isEmpty();
        assertThat(kAnonMessageEntityListSigned).isEmpty();
        assertThat(kAnonMessageEntityListNotJoined).isEmpty();
        assertThat(kAnonMessageEntityListNotFailed).isEmpty();
    }

    @Test
    public void persistAdSelectionData_withImmediateJoinValueHundred_signsAndJoinsMessage()
            throws Exception {
        GetServerPublicParamsResponse getServerPublicParamsResponse =
                GetServerPublicParamsResponse.newBuilder()
                        .setServerParamsVersion(SERVER_PARAM_VERSION)
                        .setServerPublicParams(
                                mTranscript.getServerParameters().getPublicParameters())
                        .build();
        MockResponse serverPublicParamResponse =
                new MockResponse()
                        .setResponseCode(200)
                        .setBody(getServerPublicParamsResponse.toByteArray());
        RegisterClientResponse registerClientResponse =
                RegisterClientResponse.newBuilder()
                        .setClientParamsVersion(CLIENT_PARAMS_VERSION)
                        .build();
        MockResponse registerClientParamsResponse =
                new MockResponse()
                        .setResponseCode(200)
                        .setBody(registerClientResponse.toByteArray());
        GetTokensResponse getTokensResponse =
                GetTokensResponse.newBuilder()
                        .setTokensResponse(mTranscript.getTokensResponse())
                        .build();
        MockResponse getTokensResponseHttp =
                new MockResponse().setResponseCode(200).setBody(getTokensResponse.toByteArray());
        GeneratedTokensRequestProto generatedTokensRequestProto =
                GeneratedTokensRequestProto.newBuilder()
                        .addAllFingerprintsBytes(mTranscript.getFingerprintsList())
                        .setTokenRequest(mTranscript.getTokensRequest())
                        .setTokensRequestPrivateState(mTranscript.getTokensRequestPrivateState())
                        .build();
        BinaryHttpMessage binaryHttpMessage =
                BinaryHttpMessage.knownLengthResponseBuilder(
                                ResponseControlData.builder().setFinalStatusCode(200).build())
                        .setHeaderFields(Fields.builder().appendField("Server", "Apache").build())
                        .setContent("Hello, world!\r\n".getBytes())
                        .build();
        MockResponse joinResponse =
                new MockResponse().setResponseCode(200).setBody(binaryHttpMessage.serialize());
        doReturn(mTranscript.getClientParameters())
                .when(mAnonymousCountingTokensSpy)
                .generateClientParameters(any(), any());
        doReturn(generatedTokensRequestProto)
                .when(mAnonymousCountingTokensSpy)
                .generateTokensRequest(any(), any(), any(), any(), any());

        MockWebServer server =
                mockWebServerRule.startMockWebServer(
                        ImmutableList.of(
                                serverPublicParamResponse,
                                registerClientParamsResponse,
                                getTokensResponseHttp,
                                joinResponse));
        URL fetchServerParamUrl = server.getUrl(GET_SERVER_PARAM_PATH);
        URL registerClientUrl = server.getUrl(REGISTER_CLIENT_PARAMETERS_PATH);
        URL getTokensResponseUrl = server.getUrl(GET_TOKENS_PATH);
        URL joinUrl = server.getUrl(JOIN_PATH);
        final class FlagsWithUrls extends KAnonE2ETestFlags implements Flags {

            FlagsWithUrls() {
                super(false, 20, true, 100);
            }

            @Override
            public String getFledgeKAnonRegisterClientParametersUrl() {
                return registerClientUrl.toString();
            }

            @Override
            public String getFledgeKAnonFetchServerParamsUrl() {
                return fetchServerParamUrl.toString();
            }

            @Override
            public String getFledgeKAnonGetTokensUrl() {
                return getTokensResponseUrl.toString();
            }

            @Override
            public String getFledgeKAnonJoinUrl() {
                return joinUrl.toString();
            }
        }

        Flags flagsWithCustomUrlsAndImmediateSignJoinValue100 = new FlagsWithUrls();

        doReturn(flagsWithCustomUrlsAndImmediateSignJoinValue100).when(FlagsFactory::getFlags);
        mFlags = flagsWithCustomUrlsAndImmediateSignJoinValue100;
        CountDownLatch countDownLatch = new CountDownLatch(1);
        PersistAdSelectionResultInput persistAdSelectionResultInput =
                setupTestForPersistAdSelectionResult(countDownLatch);
        mAdSelectionService = createAdSelectionService();
        mClientParametersDao.deleteAllClientParameters();
        mServerParametersDao.deleteAllServerParameters();

        PersistAdSelectionResultTestCallback persistAdSelectionResultTestCallback =
                invokePersistAdSelectionResult(mAdSelectionService, persistAdSelectionResultInput);
        countDownLatch.await();

        RecordedRequest recordedRequestGetServerParams = server.takeRequest();
        RecordedRequest recordedRequestRegisterClientParams = server.takeRequest();
        RecordedRequest recordedGetTokensRequest = server.takeRequest();
        RecordedRequest recordedJoinRequest = server.takeRequest();

        assertGetServerParametersRequest(recordedRequestGetServerParams);
        assertRegisterClientParametersRequest(recordedRequestRegisterClientParams);
        assertGetTokensRequest(recordedGetTokensRequest);
        assertJoinRequest(recordedJoinRequest);
        Assert.assertTrue(persistAdSelectionResultTestCallback.mIsSuccess);
        List<KAnonMessageEntity> kAnonMessageEntityList =
                mKAnonMessageManager.fetchNKAnonMessagesWithStatus(
                        10, KAnonMessageEntity.KanonMessageEntityStatus.JOINED);
        assertThat(kAnonMessageEntityList.size()).isEqualTo(1);
        assertThat(kAnonMessageEntityList.get(0).getAdSelectionId())
                .isEqualTo(
                        persistAdSelectionResultTestCallback.mPersistAdSelectionResultResponse
                                .getAdSelectionId());
        assertThat(kAnonMessageEntityList.get(0).getStatus())
                .isEqualTo(KAnonMessageEntity.KanonMessageEntityStatus.JOINED);
        verifyZeroInteractions(mockKeyAttestation);
        verifyZeroInteractions(mockKeyAttestationCertificate);
    }

    @Test
    public void persistAdSelectionData_actGenerateParamsFails_shouldNotUpdateStatusInDB()
            throws Exception {
        GetServerPublicParamsResponse getServerPublicParamsResponse =
                GetServerPublicParamsResponse.newBuilder()
                        .setServerParamsVersion(SERVER_PARAM_VERSION)
                        .setServerPublicParams(
                                mTranscript.getServerParameters().getPublicParameters())
                        .build();
        MockResponse serverPublicParamResponse =
                new MockResponse()
                        .setResponseCode(200)
                        .setBody(getServerPublicParamsResponse.toByteArray());
        MockWebServer server =
                mockWebServerRule.startMockWebServer(ImmutableList.of(serverPublicParamResponse));
        URL fetchServerParamUrl = server.getUrl(GET_SERVER_PARAM_PATH);
        final class FlagsWithUrls extends KAnonE2ETestFlags implements Flags {

            FlagsWithUrls() {
                super(false, 20, true, 100);
            }

            @Override
            public String getFledgeKAnonFetchServerParamsUrl() {
                return fetchServerParamUrl.toString();
            }
        }
        Flags flagsWithCustomUrlsAndImmediateSignJoinValue100 = new FlagsWithUrls();
        doReturn(flagsWithCustomUrlsAndImmediateSignJoinValue100).when(FlagsFactory::getFlags);
        mFlags = flagsWithCustomUrlsAndImmediateSignJoinValue100;
        CountDownLatch countDownLatch = new CountDownLatch(1);
        PersistAdSelectionResultInput persistAdSelectionResultInput =
                setupTestForPersistAdSelectionResult(countDownLatch);
        mAdSelectionService = createAdSelectionService();
        mClientParametersDao.deleteAllClientParameters();
        mServerParametersDao.deleteAllServerParameters();
        doThrow(new InvalidProtocolBufferException("Some error"))
                .when(mAnonymousCountingTokensSpy)
                .generateClientParameters(any(), any());

        PersistAdSelectionResultTestCallback persistAdSelectionResultTestCallback =
                invokePersistAdSelectionResult(mAdSelectionService, persistAdSelectionResultInput);
        countDownLatch.await();

        RecordedRequest recordedRequestGetServerParams = server.takeRequest();
        assertGetServerParametersRequest(recordedRequestGetServerParams);
        Assert.assertTrue(persistAdSelectionResultTestCallback.mIsSuccess);
        List<KAnonMessageEntity> kAnonMessageEntityList =
                mKAnonMessageManager.fetchNKAnonMessagesWithStatus(
                        10, KAnonMessageEntity.KanonMessageEntityStatus.NOT_PROCESSED);
        assertThat(kAnonMessageEntityList.size()).isEqualTo(1);
        assertThat(kAnonMessageEntityList.get(0).getAdSelectionId())
                .isEqualTo(
                        persistAdSelectionResultTestCallback.mPersistAdSelectionResultResponse
                                .getAdSelectionId());
        assertThat(kAnonMessageEntityList.get(0).getStatus())
                .isEqualTo(KAnonMessageEntity.KanonMessageEntityStatus.NOT_PROCESSED);
    }

    @Test
    public void persistAdSelectionData_httpRegisterClientFails_shouldNotUpdateStatusInDB()
            throws Exception {
        GetServerPublicParamsResponse getServerPublicParamsResponse =
                GetServerPublicParamsResponse.newBuilder()
                        .setServerParamsVersion("serverParamVersion")
                        .setServerPublicParams(
                                mTranscript.getServerParameters().getPublicParameters())
                        .build();
        MockResponse serverPublicParamResponse =
                new MockResponse()
                        .setResponseCode(200)
                        .setBody(getServerPublicParamsResponse.toByteArray());
        MockResponse failedRegisterClientResponse = new MockResponse().setResponseCode(429);
        MockWebServer server =
                mockWebServerRule.startMockWebServer(
                        ImmutableList.of(serverPublicParamResponse, failedRegisterClientResponse));
        URL fetchServerParamUrl = server.getUrl(GET_SERVER_PARAM_PATH);
        URL registerClientUrl = server.getUrl(REGISTER_CLIENT_PARAMETERS_PATH);
        final class FlagsWithUrls extends KAnonE2ETestFlags implements Flags {

            FlagsWithUrls() {
                super(false, 20, true, 100);
            }

            @Override
            public String getFledgeKAnonFetchServerParamsUrl() {
                return fetchServerParamUrl.toString();
            }

            @Override
            public String getFledgeKAnonRegisterClientParametersUrl() {
                return registerClientUrl.toString();
            }
        }
        Flags flagsWithCustomUrlsAndImmediateSignJoinValue100 = new FlagsWithUrls();
        doReturn(flagsWithCustomUrlsAndImmediateSignJoinValue100).when(FlagsFactory::getFlags);
        mFlags = flagsWithCustomUrlsAndImmediateSignJoinValue100;

        CountDownLatch countDownLatch = new CountDownLatch(1);
        PersistAdSelectionResultInput persistAdSelectionResultInput =
                setupTestForPersistAdSelectionResult(countDownLatch);
        mAdSelectionService = createAdSelectionService();
        mClientParametersDao.deleteAllClientParameters();
        mServerParametersDao.deleteAllServerParameters();

        PersistAdSelectionResultTestCallback persistAdSelectionResultTestCallback =
                invokePersistAdSelectionResult(mAdSelectionService, persistAdSelectionResultInput);
        countDownLatch.await();

        Assert.assertTrue(persistAdSelectionResultTestCallback.mIsSuccess);
        List<KAnonMessageEntity> kAnonMessageEntityList =
                mKAnonMessageManager.fetchNKAnonMessagesWithStatus(
                        10, KAnonMessageEntity.KanonMessageEntityStatus.NOT_PROCESSED);
        assertThat(kAnonMessageEntityList.size()).isEqualTo(1);
        assertThat(kAnonMessageEntityList.get(0).getAdSelectionId())
                .isEqualTo(
                        persistAdSelectionResultTestCallback.mPersistAdSelectionResultResponse
                                .getAdSelectionId());
        assertThat(kAnonMessageEntityList.get(0).getStatus())
                .isEqualTo(KAnonMessageEntity.KanonMessageEntityStatus.NOT_PROCESSED);
    }

    @Test
    public void persistAdSelectionData_httpFetchServerParamFails_shouldNotUpdateStatusInDB()
            throws Exception {
        MockResponse response = new MockResponse().setResponseCode(429);
        MockWebServer server = mockWebServerRule.startMockWebServer(ImmutableList.of(response));
        URL fetchServerUrl = server.getUrl(GET_SERVER_PARAM_PATH);
        final class FlagsWithFetchServerParams extends KAnonE2ETestFlags implements Flags {
            FlagsWithFetchServerParams() {
                super(false, 20, true, 100);
            }

            @Override
            public String getFledgeKAnonFetchServerParamsUrl() {
                return fetchServerUrl.toString();
            }
        }
        Flags flagsWithCustomServerParamUrl = new FlagsWithFetchServerParams();
        doReturn(flagsWithCustomServerParamUrl).when(FlagsFactory::getFlags);
        mFlags = flagsWithCustomServerParamUrl;
        CountDownLatch countDownLatch = new CountDownLatch(1);
        PersistAdSelectionResultInput persistAdSelectionResultInput =
                setupTestForPersistAdSelectionResult(countDownLatch);
        mAdSelectionService = createAdSelectionService();
        mClientParametersDao.deleteAllClientParameters();

        PersistAdSelectionResultTestCallback persistAdSelectionResultTestCallback =
                invokePersistAdSelectionResult(mAdSelectionService, persistAdSelectionResultInput);
        countDownLatch.await();

        Assert.assertTrue(persistAdSelectionResultTestCallback.mIsSuccess);
        List<KAnonMessageEntity> kAnonMessageEntityList =
                mKAnonMessageManager.fetchNKAnonMessagesWithStatus(
                        10, KAnonMessageEntity.KanonMessageEntityStatus.NOT_PROCESSED);
        assertThat(kAnonMessageEntityList.size()).isEqualTo(1);
        assertThat(kAnonMessageEntityList.get(0).getAdSelectionId())
                .isEqualTo(
                        persistAdSelectionResultTestCallback.mPersistAdSelectionResultResponse
                                .getAdSelectionId());
        assertThat(kAnonMessageEntityList.get(0).getStatus())
                .isEqualTo(KAnonMessageEntity.KanonMessageEntityStatus.NOT_PROCESSED);
    }

    @Test
    public void persistAdSelectionData_getChallengeHttpFails_shouldNotUpdateStatusInDB()
            throws Exception {
        GetServerPublicParamsResponse getServerPublicParamsResponse =
                GetServerPublicParamsResponse.newBuilder()
                        .setServerParamsVersion("serverParamVersion")
                        .setServerPublicParams(
                                mTranscript.getServerParameters().getPublicParameters())
                        .build();
        MockResponse serverPublicParamResponse =
                new MockResponse()
                        .setResponseCode(200)
                        .setBody(getServerPublicParamsResponse.toByteArray());
        MockResponse response = new MockResponse().setResponseCode(429);
        MockWebServer server =
                mockWebServerRule.startMockWebServer(
                        ImmutableList.of(serverPublicParamResponse, response));
        URL getChallengeUrl = server.getUrl(GET_CHALLENGE);
        URL fetchServerUrl = server.getUrl(GET_SERVER_PARAM_PATH);
        final class FlagsWithGetChallengeUrl extends KAnonE2ETestFlags implements Flags {
            FlagsWithGetChallengeUrl() {
                super(false, 20, true, 100);
            }

            @Override
            public String getFledgeKAnonFetchServerParamsUrl() {
                return fetchServerUrl.toString();
            }

            @Override
            public String getFledgeKAnonGetChallengeUrl() {
                return getChallengeUrl.toString();
            }

            @Override
            public boolean getFledgeKAnonKeyAttestationEnabled() {
                return true;
            }
        }
        Flags flagsWithGetChallengeUrl = new FlagsWithGetChallengeUrl();
        doReturn(flagsWithGetChallengeUrl).when(FlagsFactory::getFlags);
        mFlags = flagsWithGetChallengeUrl;
        CountDownLatch countDownLatch = new CountDownLatch(1);
        PersistAdSelectionResultInput persistAdSelectionResultInput =
                setupTestForPersistAdSelectionResult(countDownLatch);
        mAdSelectionService = createAdSelectionService();
        mClientParametersDao.deleteAllClientParameters();
        mServerParametersDao.deleteAllServerParameters();

        PersistAdSelectionResultTestCallback persistAdSelectionResultTestCallback =
                invokePersistAdSelectionResult(mAdSelectionService, persistAdSelectionResultInput);
        countDownLatch.await();

        Assert.assertTrue(persistAdSelectionResultTestCallback.mIsSuccess);
        List<KAnonMessageEntity> kAnonMessageEntityList =
                mKAnonMessageManager.fetchNKAnonMessagesWithStatus(
                        10, KAnonMessageEntity.KanonMessageEntityStatus.NOT_PROCESSED);
        assertThat(kAnonMessageEntityList.size()).isEqualTo(1);
        assertThat(kAnonMessageEntityList.get(0).getAdSelectionId())
                .isEqualTo(
                        persistAdSelectionResultTestCallback.mPersistAdSelectionResultResponse
                                .getAdSelectionId());
        assertThat(kAnonMessageEntityList.get(0).getStatus())
                .isEqualTo(KAnonMessageEntity.KanonMessageEntityStatus.NOT_PROCESSED);
    }

    @Test
    public void
            persistAdSelectionData_attestationCertificateGenerationFails_shouldLogStatsProperly()
                    throws Exception {
        GetKeyAttestationChallengeResponse getKeyAttestationChallengeResponse =
                GetKeyAttestationChallengeResponse.newBuilder().build();
        MockResponse getChallengeServerResponse =
                new MockResponse()
                        .setResponseCode(200)
                        .setBody(getKeyAttestationChallengeResponse.toByteArray());
        GetServerPublicParamsResponse getServerPublicParamsResponse =
                GetServerPublicParamsResponse.newBuilder()
                        .setServerParamsVersion("serverParamVersion")
                        .setServerPublicParams(
                                mTranscript.getServerParameters().getPublicParameters())
                        .build();
        MockResponse serverPublicParamResponse =
                new MockResponse()
                        .setResponseCode(200)
                        .setBody(getServerPublicParamsResponse.toByteArray());
        MockWebServer server =
                mockWebServerRule.startMockWebServer(
                        ImmutableList.of(serverPublicParamResponse, getChallengeServerResponse));
        URL getChallengeUrl = server.getUrl(GET_CHALLENGE);
        URL fetchServerUrl = server.getUrl(GET_SERVER_PARAM_PATH);

        final class FlagsWithGetChallengeUrl extends KAnonE2ETestFlags implements Flags {
            FlagsWithGetChallengeUrl() {
                super(false, 20, true, 100);
            }

            @Override
            public String getFledgeKAnonGetChallengeUrl() {
                return getChallengeUrl.toString();
            }

            @Override
            public boolean getFledgeKAnonKeyAttestationEnabled() {
                return true;
            }

            @Override
            public String getFledgeKAnonFetchServerParamsUrl() {
                return fetchServerUrl.toString();
            }
        }
        Flags flagsWithGetChallengeUrl = new FlagsWithGetChallengeUrl();
        doReturn(flagsWithGetChallengeUrl).when(FlagsFactory::getFlags);
        mFlags = flagsWithGetChallengeUrl;
        CountDownLatch countDownLatch = new CountDownLatch(1);
        PersistAdSelectionResultInput persistAdSelectionResultInput =
                setupTestForPersistAdSelectionResult(countDownLatch);
        mAdSelectionService = createAdSelectionService();
        mClientParametersDao.deleteAllClientParameters();
        mServerParametersDao.deleteAllServerParameters();
        doThrow(new IllegalStateException())
                .when(mockKeyAttestation)
                .generateAttestationRecord(any());

        PersistAdSelectionResultTestCallback persistAdSelectionResultTestCallback =
                invokePersistAdSelectionResult(mAdSelectionService, persistAdSelectionResultInput);
        countDownLatch.await();

        Assert.assertTrue(persistAdSelectionResultTestCallback.mIsSuccess);
        verify(mAdServicesLoggerMock, times(1))
                .logKAnonGetChallengeJobStats(argumentCaptorGetChallenge.capture());
        KAnonGetChallengeStatusStats capturedGetChallengeStats =
                argumentCaptorGetChallenge.getValue();
        assertThat(capturedGetChallengeStats.getResultCode())
                .isEqualTo(
                        KAnonSignJoinStatsConstants.KEY_ATTESTATION_RESULT_ILLEGAL_STATE_EXCEPTION);
    }

    @Test
    public void persistAdSelectionData_withAttestationEnabled_signsAndJoinsMessage()
            throws Exception {
        GetKeyAttestationChallengeResponse getKeyAttestationChallengeResponse =
                GetKeyAttestationChallengeResponse.newBuilder().build();
        MockResponse getChallengeServerResponse =
                new MockResponse()
                        .setResponseCode(200)
                        .setBody(getKeyAttestationChallengeResponse.toByteArray());
        GetServerPublicParamsResponse getServerPublicParamsResponse =
                GetServerPublicParamsResponse.newBuilder()
                        .setServerParamsVersion(SERVER_PARAM_VERSION)
                        .setServerPublicParams(
                                mTranscript.getServerParameters().getPublicParameters())
                        .build();
        MockResponse serverPublicParamResponse =
                new MockResponse()
                        .setResponseCode(200)
                        .setBody(getServerPublicParamsResponse.toByteArray());
        RegisterClientResponse registerClientResponse =
                RegisterClientResponse.newBuilder()
                        .setClientParamsVersion(CLIENT_PARAMS_VERSION)
                        .build();
        MockResponse registerClientParamsResponse =
                new MockResponse()
                        .setResponseCode(200)
                        .setBody(registerClientResponse.toByteArray());
        GetTokensResponse getTokensResponse =
                GetTokensResponse.newBuilder()
                        .setTokensResponse(mTranscript.getTokensResponse())
                        .build();
        MockResponse getTokensResponseHttpResponse =
                new MockResponse().setResponseCode(200).setBody(getTokensResponse.toByteArray());
        GeneratedTokensRequestProto generatedTokensRequestProto =
                GeneratedTokensRequestProto.newBuilder()
                        .addAllFingerprintsBytes(mTranscript.getFingerprintsList())
                        .setTokenRequest(mTranscript.getTokensRequest())
                        .setTokensRequestPrivateState(mTranscript.getTokensRequestPrivateState())
                        .build();
        BinaryHttpMessage binaryHttpMessage =
                BinaryHttpMessage.knownLengthResponseBuilder(
                                ResponseControlData.builder().setFinalStatusCode(200).build())
                        .setHeaderFields(Fields.builder().appendField("Server", "Apache").build())
                        .setContent("Hello, world!\r\n".getBytes())
                        .build();
        MockResponse joinResponse =
                new MockResponse().setResponseCode(200).setBody(binaryHttpMessage.serialize());
        doReturn(mTranscript.getClientParameters())
                .when(mAnonymousCountingTokensSpy)
                .generateClientParameters(any(), any());
        doReturn(generatedTokensRequestProto)
                .when(mAnonymousCountingTokensSpy)
                .generateTokensRequest(any(), any(), any(), any(), any());
        doReturn(true)
                .when(mAnonymousCountingTokensSpy)
                .verifyTokensResponse(any(), any(), any(), any(), any(), any(), any(), any());
        TokensSet tokensSet =
                TokensSet.newBuilder().addAllTokens(mTranscript.getTokensList()).build();
        doReturn(tokensSet)
                .when(mAnonymousCountingTokensSpy)
                .recoverTokens(any(), any(), any(), any(), any(), any(), any(), any());

        MockWebServer server =
                mockWebServerRule.startMockWebServer(
                        ImmutableList.of(
                                serverPublicParamResponse,
                                getChallengeServerResponse,
                                registerClientParamsResponse,
                                getTokensResponseHttpResponse,
                                joinResponse));
        URL getChallengeUrl = server.getUrl(GET_CHALLENGE);
        URL fetchServerParamUrl = server.getUrl(GET_SERVER_PARAM_PATH);
        URL registerClientUrl = server.getUrl(REGISTER_CLIENT_PARAMETERS_PATH);
        URL getTokensResponseUrl = server.getUrl(GET_TOKENS_PATH);
        URL joinUrl = server.getUrl(JOIN_PATH);
        final class FlagsWithUrls extends KAnonE2ETestFlags implements Flags {

            FlagsWithUrls() {
                super(false, 20, true, 100);
            }

            @Override
            public String getFledgeKAnonGetChallengeUrl() {
                return getChallengeUrl.toString();
            }

            @Override
            public boolean getFledgeKAnonKeyAttestationEnabled() {
                return true;
            }

            @Override
            public String getFledgeKAnonRegisterClientParametersUrl() {
                return registerClientUrl.toString();
            }

            @Override
            public String getFledgeKAnonFetchServerParamsUrl() {
                return fetchServerParamUrl.toString();
            }

            @Override
            public String getFledgeKAnonGetTokensUrl() {
                return getTokensResponseUrl.toString();
            }

            @Override
            public String getFledgeKAnonJoinUrl() {
                return joinUrl.toString();
            }
        }

        Flags flagsWithCustomUrlsAndImmediateSignJoinValue100 = new FlagsWithUrls();

        doReturn(flagsWithCustomUrlsAndImmediateSignJoinValue100).when(FlagsFactory::getFlags);
        mFlags = flagsWithCustomUrlsAndImmediateSignJoinValue100;
        CountDownLatch countDownLatch = new CountDownLatch(1);
        PersistAdSelectionResultInput persistAdSelectionResultInput =
                setupTestForPersistAdSelectionResult(countDownLatch);
        mAdSelectionService = createAdSelectionService();
        mClientParametersDao.deleteAllClientParameters();
        mServerParametersDao.deleteAllServerParameters();

        PersistAdSelectionResultTestCallback persistAdSelectionResultTestCallback =
                invokePersistAdSelectionResult(mAdSelectionService, persistAdSelectionResultInput);
        countDownLatch.await();

        RecordedRequest recordedRequestGetServerParams = server.takeRequest();
        RecordedRequest getChallengeRequest = server.takeRequest();
        RecordedRequest recordedRequestRegisterClientParams = server.takeRequest();
        RecordedRequest recordedGetTokensRequest = server.takeRequest();
        RecordedRequest recordedJoinRequest = server.takeRequest();

        assertGetChallengeRequest(getChallengeRequest);
        assertGetServerParametersRequest(recordedRequestGetServerParams);
        assertRegisterClientParametersRequest(recordedRequestRegisterClientParams);
        assertGetTokensRequest(recordedGetTokensRequest);
        assertJoinRequest(recordedJoinRequest);
        Assert.assertTrue(persistAdSelectionResultTestCallback.mIsSuccess);
        List<KAnonMessageEntity> kAnonMessageEntityList =
                mKAnonMessageManager.fetchNKAnonMessagesWithStatus(
                        10, KAnonMessageEntity.KanonMessageEntityStatus.JOINED);
        assertThat(kAnonMessageEntityList.size()).isEqualTo(1);
        assertThat(kAnonMessageEntityList.get(0).getAdSelectionId())
                .isEqualTo(
                        persistAdSelectionResultTestCallback.mPersistAdSelectionResultResponse
                                .getAdSelectionId());
        assertThat(kAnonMessageEntityList.get(0).getStatus())
                .isEqualTo(KAnonMessageEntity.KanonMessageEntityStatus.JOINED);
    }

    @Test
    public void persistAdSelectionData_joinHttpRequestFails_shouldMarkMessageAsFailed()
            throws Exception {
        MockResponse response = new MockResponse().setResponseCode(429);
        MockWebServer server = mockWebServerRule.startMockWebServer(ImmutableList.of(response));
        URL joinUrl = server.getUrl(JOIN_PATH);
        final class FlagsWithCustomJoinUrl extends KAnonE2ETestFlags implements Flags {
            FlagsWithCustomJoinUrl() {
                super(false, 20, true, 100);
            }

            @Override
            public String getFledgeKAnonJoinUrl() {
                return joinUrl.toString();
            }
        }
        Flags flagsWithCustomJoinUrl = new FlagsWithCustomJoinUrl();
        doReturn(flagsWithCustomJoinUrl).when(FlagsFactory::getFlags);
        mFlags = flagsWithCustomJoinUrl;
        CountDownLatch countDownLatch = new CountDownLatch(1);
        PersistAdSelectionResultInput persistAdSelectionResultInput =
                setupTestForPersistAdSelectionResult(countDownLatch);
        mAdSelectionService = createAdSelectionService();

        PersistAdSelectionResultTestCallback persistAdSelectionResultTestCallback =
                invokePersistAdSelectionResult(mAdSelectionService, persistAdSelectionResultInput);
        countDownLatch.await();

        Assert.assertTrue(persistAdSelectionResultTestCallback.mIsSuccess);
        List<KAnonMessageEntity> kAnonMessageEntityList =
                mKAnonMessageManager.fetchNKAnonMessagesWithStatus(
                        10, KAnonMessageEntity.KanonMessageEntityStatus.FAILED);
        assertThat(kAnonMessageEntityList.size()).isEqualTo(1);
        assertThat(kAnonMessageEntityList.get(0).getAdSelectionId())
                .isEqualTo(
                        persistAdSelectionResultTestCallback.mPersistAdSelectionResultResponse
                                .getAdSelectionId());
        assertThat(kAnonMessageEntityList.get(0).getStatus())
                .isEqualTo(KAnonMessageEntity.KanonMessageEntityStatus.FAILED);
    }

    @Test
    public void persistAdSelectionData_getTokensHttpRequestFails_shouldMarkMessagesAsFailed()
            throws Exception {
        // In this test, the KAnonCaller will not fetch server and client params because those
        // parameters already exists in the database.
        MockResponse response = new MockResponse().setResponseCode(429);
        MockWebServer server = mockWebServerRule.startMockWebServer(ImmutableList.of(response));
        URL getTokensUrl = server.getUrl(GET_TOKENS_PATH);
        final class FlagsWithGetTokensUrl extends KAnonE2ETestFlags implements Flags {
            FlagsWithGetTokensUrl() {
                super(false, 20, true, 100);
            }

            @Override
            public String getFledgeKAnonGetTokensUrl() {
                return getTokensUrl.toString();
            }
        }
        Flags flagsWithGetTokensUrl = new FlagsWithGetTokensUrl();
        doReturn(flagsWithGetTokensUrl).when(FlagsFactory::getFlags);
        mFlags = flagsWithGetTokensUrl;
        CountDownLatch countDownLatch = new CountDownLatch(1);
        PersistAdSelectionResultInput persistAdSelectionResultInput =
                setupTestForPersistAdSelectionResult(countDownLatch);
        mAdSelectionService = createAdSelectionService();

        PersistAdSelectionResultTestCallback persistAdSelectionResultTestCallback =
                invokePersistAdSelectionResult(mAdSelectionService, persistAdSelectionResultInput);
        countDownLatch.await();

        Assert.assertTrue(persistAdSelectionResultTestCallback.mIsSuccess);
        List<KAnonMessageEntity> kAnonMessageEntityList =
                mKAnonMessageManager.fetchNKAnonMessagesWithStatus(
                        10, KAnonMessageEntity.KanonMessageEntityStatus.FAILED);
        assertThat(kAnonMessageEntityList.size()).isEqualTo(1);
        assertThat(kAnonMessageEntityList.get(0).getAdSelectionId())
                .isEqualTo(
                        persistAdSelectionResultTestCallback.mPersistAdSelectionResultResponse
                                .getAdSelectionId());
        assertThat(kAnonMessageEntityList.get(0).getStatus())
                .isEqualTo(KAnonMessageEntity.KanonMessageEntityStatus.FAILED);
    }

    @Test
    public void persistAdSelectionData_actRecoverTokensFails_shouldMarkMessagesAsFailed()
            throws Exception {
        Flags flagsWithFeatureEnabledImmediateJoinHundred =
                new KAnonE2ETestFlags(false, 20, true, 100);
        doReturn(flagsWithFeatureEnabledImmediateJoinHundred).when(FlagsFactory::getFlags);
        mFlags = flagsWithFeatureEnabledImmediateJoinHundred;
        CountDownLatch countDownLatch = new CountDownLatch(1);
        PersistAdSelectionResultInput persistAdSelectionResultInput =
                setupTestForPersistAdSelectionResult(countDownLatch);
        mAdSelectionService = createAdSelectionService();

        doThrow(new InvalidProtocolBufferException("error while recovering tokesn"))
                .when(mAnonymousCountingTokensSpy)
                .recoverTokens(any(), any(), any(), any(), any(), any(), any(), any());

        PersistAdSelectionResultTestCallback persistAdSelectionResultTestCallback =
                invokePersistAdSelectionResult(mAdSelectionService, persistAdSelectionResultInput);
        countDownLatch.await();

        Assert.assertTrue(persistAdSelectionResultTestCallback.mIsSuccess);
        List<KAnonMessageEntity> kAnonMessageEntityList =
                mKAnonMessageManager.fetchNKAnonMessagesWithStatus(
                        10, KAnonMessageEntity.KanonMessageEntityStatus.FAILED);
        assertThat(kAnonMessageEntityList.size()).isEqualTo(1);
        assertThat(kAnonMessageEntityList.get(0).getAdSelectionId())
                .isEqualTo(
                        persistAdSelectionResultTestCallback.mPersistAdSelectionResultResponse
                                .getAdSelectionId());
        assertThat(kAnonMessageEntityList.get(0).getStatus())
                .isEqualTo(KAnonMessageEntity.KanonMessageEntityStatus.FAILED);
    }

    @Test
    public void persistAdSelectionData_actVerifyTokensFailed_shouldMarkMessagesAsFailed()
            throws Exception {
        // KAnonCaller will not fetch server and client parameters because they already exists in
        // the database.
        // Incorrect get tokens response will result in failure for ACT#VerifyTokens method.
        GetTokensResponse incorrectGetTokensResponse =
                GetTokensResponse.newBuilder()
                        .setTokensResponse(mTranscript.getTokensResponse())
                        .build();
        MockResponse mockGetTokensResponse =
                new MockResponse()
                        .setResponseCode(200)
                        .setBody(incorrectGetTokensResponse.toByteArray());
        GeneratedTokensRequestProto generatedTokensRequestProto =
                GeneratedTokensRequestProto.newBuilder()
                        .addAllFingerprintsBytes(mTranscript.getFingerprintsList())
                        .setTokenRequest(mTranscript.getTokensRequest())
                        .setTokensRequestPrivateState(mTranscript.getTokensRequestPrivateState())
                        .build();
        doReturn(generatedTokensRequestProto)
                .when(mAnonymousCountingTokensSpy)
                .generateTokensRequest(any(), any(), any(), any(), any());
        doReturn(false)
                .when(mAnonymousCountingTokensSpy)
                .verifyTokensResponse(any(), any(), any(), any(), any(), any(), any(), any());

        MockWebServer server =
                mockWebServerRule.startMockWebServer(ImmutableList.of(mockGetTokensResponse));
        URL getTokensResponseUrl = server.getUrl(GET_TOKENS_PATH);
        final class FlagsWithUrls extends KAnonE2ETestFlags implements Flags {

            FlagsWithUrls() {
                super(false, 20, true, 100);
            }

            @Override
            public String getFledgeKAnonGetTokensUrl() {
                return getTokensResponseUrl.toString();
            }
        }

        Flags flagsWithUrlsAndFeatureEnabled = new FlagsWithUrls();
        doReturn(flagsWithUrlsAndFeatureEnabled).when(FlagsFactory::getFlags);
        mFlags = flagsWithUrlsAndFeatureEnabled;
        CountDownLatch countDownLatch = new CountDownLatch(1);
        PersistAdSelectionResultInput persistAdSelectionResultInput =
                setupTestForPersistAdSelectionResult(countDownLatch);
        mAdSelectionService = createAdSelectionService();

        PersistAdSelectionResultTestCallback persistAdSelectionResultTestCallback =
                invokePersistAdSelectionResult(mAdSelectionService, persistAdSelectionResultInput);
        countDownLatch.await();

        Assert.assertTrue(persistAdSelectionResultTestCallback.mIsSuccess);
        List<KAnonMessageEntity> kAnonMessageEntityList =
                mKAnonMessageManager.fetchNKAnonMessagesWithStatus(
                        10, KAnonMessageEntity.KanonMessageEntityStatus.FAILED);
        assertThat(kAnonMessageEntityList.size()).isEqualTo(1);
        assertThat(kAnonMessageEntityList.get(0).getAdSelectionId())
                .isEqualTo(
                        persistAdSelectionResultTestCallback.mPersistAdSelectionResultResponse
                                .getAdSelectionId());
        assertThat(kAnonMessageEntityList.get(0).getStatus())
                .isEqualTo(KAnonMessageEntity.KanonMessageEntityStatus.FAILED);
    }

    @Test
    public void persistAdSelectionData_actGenerateParamsFails_shouldLogStatsCorrectly()
            throws Exception {
        GetServerPublicParamsResponse getServerPublicParamsResponse =
                GetServerPublicParamsResponse.newBuilder()
                        .setServerParamsVersion(SERVER_PARAM_VERSION)
                        .setServerPublicParams(
                                mTranscript.getServerParameters().getPublicParameters())
                        .build();
        MockResponse serverPublicParamResponse =
                new MockResponse()
                        .setResponseCode(200)
                        .setBody(getServerPublicParamsResponse.toByteArray());
        MockWebServer server =
                mockWebServerRule.startMockWebServer(ImmutableList.of(serverPublicParamResponse));
        URL fetchServerParamUrl = server.getUrl(GET_SERVER_PARAM_PATH);
        final class FlagsWithUrls extends KAnonE2ETestFlags implements Flags {

            FlagsWithUrls() {
                super(false, 20, true, 100);
            }

            @Override
            public String getFledgeKAnonFetchServerParamsUrl() {
                return fetchServerParamUrl.toString();
            }
        }
        Flags flagsWithCustomUrlsAndImmediateSignJoinValue100 = new FlagsWithUrls();
        doReturn(flagsWithCustomUrlsAndImmediateSignJoinValue100).when(FlagsFactory::getFlags);
        mFlags = flagsWithCustomUrlsAndImmediateSignJoinValue100;
        CountDownLatch countDownLatch = new CountDownLatch(1);
        PersistAdSelectionResultInput persistAdSelectionResultInput =
                setupTestForPersistAdSelectionResult(countDownLatch);
        mAdSelectionService = createAdSelectionService();
        mClientParametersDao.deleteAllClientParameters();
        mServerParametersDao.deleteAllServerParameters();
        doThrow(new InvalidProtocolBufferException("Some error"))
                .when(mAnonymousCountingTokensSpy)
                .generateClientParameters(any(), any());

        PersistAdSelectionResultTestCallback persistAdSelectionResultTestCallback =
                invokePersistAdSelectionResult(mAdSelectionService, persistAdSelectionResultInput);
        countDownLatch.await();

        verify(mAdServicesLoggerMock, times(1))
                .logKAnonInitializeStats(argumentCaptorInitializeStats.capture());
        KAnonInitializeStatusStats capturedInitializeStats =
                argumentCaptorInitializeStats.getValue();
        assertThat(capturedInitializeStats.getWasSuccessful()).isFalse();
        assertThat(capturedInitializeStats.getKAnonAction())
                .isEqualTo(KAnonSignJoinStatsConstants.KANON_ACTION_GENERATE_CLIENT_PARAM_ACT);
        assertThat(capturedInitializeStats.getKAnonActionFailureReason())
                .isEqualTo(
                        KAnonSignJoinStatsConstants
                                .KANON_ACTION_FAILURE_REASON_PROTO_PARSE_EXCEPTION);
    }

    @Test
    public void persistAdSelectionData_getTokensHttpRequestFails_shouldLogStatsCorrectly()
            throws Exception {
        // In this test, the KAnonCaller will not fetch server and client params because those
        // parameters already exists in the database.
        MockResponse response = new MockResponse().setResponseCode(429);
        MockWebServer server = mockWebServerRule.startMockWebServer(ImmutableList.of(response));
        URL getTokensUrl = server.getUrl(GET_TOKENS_PATH);
        final class FlagsWithGetTokensUrl extends KAnonE2ETestFlags implements Flags {
            FlagsWithGetTokensUrl() {
                super(false, 20, true, 100);
            }

            @Override
            public String getFledgeKAnonGetTokensUrl() {
                return getTokensUrl.toString();
            }
        }
        Flags flagsWithGetTokensUrl = new FlagsWithGetTokensUrl();
        doReturn(flagsWithGetTokensUrl).when(FlagsFactory::getFlags);
        mFlags = flagsWithGetTokensUrl;
        CountDownLatch countDownLatch = new CountDownLatch(1);
        PersistAdSelectionResultInput persistAdSelectionResultInput =
                setupTestForPersistAdSelectionResult(countDownLatch);
        mAdSelectionService = createAdSelectionService();

        PersistAdSelectionResultTestCallback persistAdSelectionResultTestCallback =
                invokePersistAdSelectionResult(mAdSelectionService, persistAdSelectionResultInput);
        countDownLatch.await();

        verify(mAdServicesLoggerMock, times(1))
                .logKAnonInitializeStats(argumentCaptorInitializeStats.capture());
        KAnonInitializeStatusStats kAnonInitializeStatusStats =
                argumentCaptorInitializeStats.getValue();
        assertThat(kAnonInitializeStatusStats.getWasSuccessful()).isTrue();
        verify(mAdServicesLoggerMock, times(1))
                .logKAnonSignStats(argumentCaptorSignStats.capture());
        KAnonSignStatusStats kAnonSignStatusStats = argumentCaptorSignStats.getValue();
        assertThat(kAnonSignStatusStats.getWasSuccessful()).isFalse();
        assertThat(kAnonSignStatusStats.getKAnonAction())
                .isEqualTo(KAnonSignJoinStatsConstants.KANON_ACTION_GET_TOKENS_REQUEST_HTTP_CALL);
        assertThat(kAnonSignStatusStats.getKAnonActionFailureReason())
                .isEqualTo(
                        KAnonSignJoinStatsConstants.KANON_ACTION_FAILURE_REASON_NETWORK_EXCEPTION);
    }

    @Test
    public void persistAdSelectionData_httpFetchServerParamFails_shouldLogStatsCorrectly()
            throws Exception {
        MockResponse response = new MockResponse().setResponseCode(429);
        MockWebServer server = mockWebServerRule.startMockWebServer(ImmutableList.of(response));
        URL fetchServerUrl = server.getUrl(GET_SERVER_PARAM_PATH);
        final class FlagsWithFetchServerParams extends KAnonE2ETestFlags implements Flags {
            FlagsWithFetchServerParams() {
                super(false, 20, true, 100);
            }

            @Override
            public String getFledgeKAnonFetchServerParamsUrl() {
                return fetchServerUrl.toString();
            }
        }
        Flags flagsWithCustomServerParamUrl = new FlagsWithFetchServerParams();
        doReturn(flagsWithCustomServerParamUrl).when(FlagsFactory::getFlags);
        mFlags = flagsWithCustomServerParamUrl;
        CountDownLatch countDownLatch = new CountDownLatch(1);
        PersistAdSelectionResultInput persistAdSelectionResultInput =
                setupTestForPersistAdSelectionResult(countDownLatch);
        mAdSelectionService = createAdSelectionService();
        mClientParametersDao.deleteAllClientParameters();

        PersistAdSelectionResultTestCallback persistAdSelectionResultTestCallback =
                invokePersistAdSelectionResult(mAdSelectionService, persistAdSelectionResultInput);
        countDownLatch.await();

        Assert.assertTrue(persistAdSelectionResultTestCallback.mIsSuccess);
        List<KAnonMessageEntity> kAnonMessageEntityList =
                mKAnonMessageManager.fetchNKAnonMessagesWithStatus(
                        10, KAnonMessageEntity.KanonMessageEntityStatus.NOT_PROCESSED);
        assertThat(kAnonMessageEntityList.size()).isEqualTo(1);
        assertThat(kAnonMessageEntityList.get(0).getAdSelectionId())
                .isEqualTo(
                        persistAdSelectionResultTestCallback.mPersistAdSelectionResultResponse
                                .getAdSelectionId());
        assertThat(kAnonMessageEntityList.get(0).getStatus())
                .isEqualTo(KAnonMessageEntity.KanonMessageEntityStatus.NOT_PROCESSED);
        verify(mAdServicesLoggerMock, times(1))
                .logKAnonInitializeStats(argumentCaptorInitializeStats.capture());
        KAnonInitializeStatusStats capturedInitializeStats =
                argumentCaptorInitializeStats.getValue();
        assertThat(capturedInitializeStats.getWasSuccessful()).isFalse();
        assertThat(capturedInitializeStats.getKAnonAction())
                .isEqualTo(KAnonSignJoinStatsConstants.KANON_ACTION_SERVER_PARAM_HTTP_CALL);
        assertThat(capturedInitializeStats.getKAnonActionFailureReason())
                .isEqualTo(
                        KAnonSignJoinStatsConstants.KANON_ACTION_FAILURE_REASON_NETWORK_EXCEPTION);
    }

    @Test
    public void persistAdSelectionData_httpRegisterClientFails_shouldLogStatsCorrectly()
            throws Exception {
        GetServerPublicParamsResponse getServerPublicParamsResponse =
                GetServerPublicParamsResponse.newBuilder()
                        .setServerParamsVersion("serverParamVersion")
                        .setServerPublicParams(
                                mTranscript.getServerParameters().getPublicParameters())
                        .build();
        MockResponse serverPublicParamResponse =
                new MockResponse()
                        .setResponseCode(200)
                        .setBody(getServerPublicParamsResponse.toByteArray());
        MockResponse failedRegisterClientResponse = new MockResponse().setResponseCode(429);
        MockWebServer server =
                mockWebServerRule.startMockWebServer(
                        ImmutableList.of(serverPublicParamResponse, failedRegisterClientResponse));
        URL fetchServerParamUrl = server.getUrl(GET_SERVER_PARAM_PATH);
        URL registerClientUrl = server.getUrl(REGISTER_CLIENT_PARAMETERS_PATH);
        final class FlagsWithUrls extends KAnonE2ETestFlags implements Flags {

            FlagsWithUrls() {
                super(false, 20, true, 100);
            }

            @Override
            public String getFledgeKAnonFetchServerParamsUrl() {
                return fetchServerParamUrl.toString();
            }

            @Override
            public String getFledgeKAnonRegisterClientParametersUrl() {
                return registerClientUrl.toString();
            }
        }
        Flags flagsWithCustomUrlsAndImmediateSignJoinValue100 = new FlagsWithUrls();
        doReturn(flagsWithCustomUrlsAndImmediateSignJoinValue100).when(FlagsFactory::getFlags);
        mFlags = flagsWithCustomUrlsAndImmediateSignJoinValue100;

        CountDownLatch countDownLatch = new CountDownLatch(1);
        PersistAdSelectionResultInput persistAdSelectionResultInput =
                setupTestForPersistAdSelectionResult(countDownLatch);
        mAdSelectionService = createAdSelectionService();
        mClientParametersDao.deleteAllClientParameters();
        mServerParametersDao.deleteAllServerParameters();

        PersistAdSelectionResultTestCallback persistAdSelectionResultTestCallback =
                invokePersistAdSelectionResult(mAdSelectionService, persistAdSelectionResultInput);
        countDownLatch.await();

        Assert.assertTrue(persistAdSelectionResultTestCallback.mIsSuccess);
        List<KAnonMessageEntity> kAnonMessageEntityList =
                mKAnonMessageManager.fetchNKAnonMessagesWithStatus(
                        10, KAnonMessageEntity.KanonMessageEntityStatus.NOT_PROCESSED);
        assertThat(kAnonMessageEntityList.size()).isEqualTo(1);
        assertThat(kAnonMessageEntityList.get(0).getAdSelectionId())
                .isEqualTo(
                        persistAdSelectionResultTestCallback.mPersistAdSelectionResultResponse
                                .getAdSelectionId());
        assertThat(kAnonMessageEntityList.get(0).getStatus())
                .isEqualTo(KAnonMessageEntity.KanonMessageEntityStatus.NOT_PROCESSED);
        verify(mAdServicesLoggerMock, times(1))
                .logKAnonInitializeStats(argumentCaptorInitializeStats.capture());
        KAnonInitializeStatusStats capturedInitializeStats =
                argumentCaptorInitializeStats.getValue();
        assertThat(capturedInitializeStats.getWasSuccessful()).isFalse();
        assertThat(capturedInitializeStats.getKAnonAction())
                .isEqualTo(KAnonSignJoinStatsConstants.KANON_ACTION_REGISTER_CLIENT_HTTP_CALL);
        assertThat(capturedInitializeStats.getKAnonActionFailureReason())
                .isEqualTo(
                        KAnonSignJoinStatsConstants.KANON_ACTION_FAILURE_REASON_NETWORK_EXCEPTION);
    }

    @Test
    public void persistAdSelectionData_withImmediateJoinValueHundred_shouldLogStatsCorrectly()
            throws Exception {
        GetServerPublicParamsResponse getServerPublicParamsResponse =
                GetServerPublicParamsResponse.newBuilder()
                        .setServerParamsVersion(SERVER_PARAM_VERSION)
                        .setServerPublicParams(
                                mTranscript.getServerParameters().getPublicParameters())
                        .build();
        MockResponse serverPublicParamResponse =
                new MockResponse()
                        .setResponseCode(200)
                        .setBody(getServerPublicParamsResponse.toByteArray());
        RegisterClientResponse registerClientResponse =
                RegisterClientResponse.newBuilder()
                        .setClientParamsVersion(CLIENT_PARAMS_VERSION)
                        .build();
        MockResponse registerClientParamsResponse =
                new MockResponse()
                        .setResponseCode(200)
                        .setBody(registerClientResponse.toByteArray());
        GetTokensResponse getTokensResponse =
                GetTokensResponse.newBuilder()
                        .setTokensResponse(mTranscript.getTokensResponse())
                        .build();
        MockResponse getTokensResponseHttp =
                new MockResponse().setResponseCode(200).setBody(getTokensResponse.toByteArray());
        GeneratedTokensRequestProto generatedTokensRequestProto =
                GeneratedTokensRequestProto.newBuilder()
                        .addAllFingerprintsBytes(mTranscript.getFingerprintsList())
                        .setTokenRequest(mTranscript.getTokensRequest())
                        .setTokensRequestPrivateState(mTranscript.getTokensRequestPrivateState())
                        .build();
        BinaryHttpMessage binaryHttpMessage =
                BinaryHttpMessage.knownLengthResponseBuilder(
                                ResponseControlData.builder().setFinalStatusCode(200).build())
                        .setHeaderFields(Fields.builder().appendField("Server", "Apache").build())
                        .setContent("Hello, world!\r\n".getBytes())
                        .build();
        MockResponse joinResponse =
                new MockResponse().setResponseCode(200).setBody(binaryHttpMessage.serialize());
        doReturn(mTranscript.getClientParameters())
                .when(mAnonymousCountingTokensSpy)
                .generateClientParameters(any(), any());
        doReturn(generatedTokensRequestProto)
                .when(mAnonymousCountingTokensSpy)
                .generateTokensRequest(any(), any(), any(), any(), any());

        MockWebServer server =
                mockWebServerRule.startMockWebServer(
                        ImmutableList.of(
                                serverPublicParamResponse,
                                registerClientParamsResponse,
                                getTokensResponseHttp,
                                joinResponse));
        URL fetchServerParamUrl = server.getUrl(GET_SERVER_PARAM_PATH);
        URL registerClientUrl = server.getUrl(REGISTER_CLIENT_PARAMETERS_PATH);
        URL getTokensResponseUrl = server.getUrl(GET_TOKENS_PATH);
        URL joinUrl = server.getUrl(JOIN_PATH);
        final class FlagsWithUrls extends KAnonE2ETestFlags implements Flags {

            FlagsWithUrls() {
                super(false, 20, true, 100);
            }

            @Override
            public String getFledgeKAnonRegisterClientParametersUrl() {
                return registerClientUrl.toString();
            }

            @Override
            public String getFledgeKAnonFetchServerParamsUrl() {
                return fetchServerParamUrl.toString();
            }

            @Override
            public String getFledgeKAnonGetTokensUrl() {
                return getTokensResponseUrl.toString();
            }

            @Override
            public String getFledgeKAnonJoinUrl() {
                return joinUrl.toString();
            }
        }

        Flags flagsWithCustomUrlsAndImmediateSignJoinValue100 = new FlagsWithUrls();

        doReturn(flagsWithCustomUrlsAndImmediateSignJoinValue100).when(FlagsFactory::getFlags);
        mFlags = flagsWithCustomUrlsAndImmediateSignJoinValue100;
        CountDownLatch countDownLatch = new CountDownLatch(1);
        PersistAdSelectionResultInput persistAdSelectionResultInput =
                setupTestForPersistAdSelectionResult(countDownLatch);
        mAdSelectionService = createAdSelectionService();
        mClientParametersDao.deleteAllClientParameters();
        mServerParametersDao.deleteAllServerParameters();

        PersistAdSelectionResultTestCallback persistAdSelectionResultTestCallback =
                invokePersistAdSelectionResult(mAdSelectionService, persistAdSelectionResultInput);
        countDownLatch.await();

        verify(mAdServicesLoggerMock, times(1))
                .logKAnonInitializeStats(argumentCaptorInitializeStats.capture());
        KAnonInitializeStatusStats capturedInitializeStats =
                argumentCaptorInitializeStats.getValue();
        assertThat(capturedInitializeStats.getWasSuccessful()).isTrue();
        verify(mAdServicesLoggerMock, times(1))
                .logKAnonSignStats(argumentCaptorSignStats.capture());
        KAnonSignStatusStats capturedSignStats = argumentCaptorSignStats.getValue();
        assertThat(capturedSignStats.getWasSuccessful()).isTrue();
        verify(mAdServicesLoggerMock, times(1))
                .logKAnonJoinStats(argumentCaptorJoinStats.capture());
        KAnonJoinStatusStats kAnonJoinStatusStats = argumentCaptorJoinStats.getValue();
        assertThat(kAnonJoinStatusStats.getWasSuccessful()).isTrue();
    }

    @Test
    public void persistAdSelectionData_withAttestationEnabled_shouldLogStatsCorrectly()
            throws Exception {
        GetKeyAttestationChallengeResponse getKeyAttestationChallengeResponse =
                GetKeyAttestationChallengeResponse.newBuilder().build();
        MockResponse getChallengeServerResponse =
                new MockResponse()
                        .setResponseCode(200)
                        .setBody(getKeyAttestationChallengeResponse.toByteArray());
        GetServerPublicParamsResponse getServerPublicParamsResponse =
                GetServerPublicParamsResponse.newBuilder()
                        .setServerParamsVersion(SERVER_PARAM_VERSION)
                        .setServerPublicParams(
                                mTranscript.getServerParameters().getPublicParameters())
                        .build();
        MockResponse serverPublicParamResponse =
                new MockResponse()
                        .setResponseCode(200)
                        .setBody(getServerPublicParamsResponse.toByteArray());
        RegisterClientResponse registerClientResponse =
                RegisterClientResponse.newBuilder()
                        .setClientParamsVersion(CLIENT_PARAMS_VERSION)
                        .build();
        MockResponse registerClientParamsResponse =
                new MockResponse()
                        .setResponseCode(200)
                        .setBody(registerClientResponse.toByteArray());
        GetTokensResponse getTokensResponse =
                GetTokensResponse.newBuilder()
                        .setTokensResponse(mTranscript.getTokensResponse())
                        .build();
        MockResponse getTokensResponseHttpResponse =
                new MockResponse().setResponseCode(200).setBody(getTokensResponse.toByteArray());
        GeneratedTokensRequestProto generatedTokensRequestProto =
                GeneratedTokensRequestProto.newBuilder()
                        .addAllFingerprintsBytes(mTranscript.getFingerprintsList())
                        .setTokenRequest(mTranscript.getTokensRequest())
                        .setTokensRequestPrivateState(mTranscript.getTokensRequestPrivateState())
                        .build();
        BinaryHttpMessage binaryHttpMessage =
                BinaryHttpMessage.knownLengthResponseBuilder(
                                ResponseControlData.builder().setFinalStatusCode(200).build())
                        .setHeaderFields(Fields.builder().appendField("Server", "Apache").build())
                        .setContent("Hello, world!\r\n".getBytes())
                        .build();
        MockResponse joinResponse =
                new MockResponse().setResponseCode(200).setBody(binaryHttpMessage.serialize());
        doReturn(mTranscript.getClientParameters())
                .when(mAnonymousCountingTokensSpy)
                .generateClientParameters(any(), any());
        doReturn(generatedTokensRequestProto)
                .when(mAnonymousCountingTokensSpy)
                .generateTokensRequest(any(), any(), any(), any(), any());
        doReturn(true)
                .when(mAnonymousCountingTokensSpy)
                .verifyTokensResponse(any(), any(), any(), any(), any(), any(), any(), any());
        TokensSet tokensSet =
                TokensSet.newBuilder().addAllTokens(mTranscript.getTokensList()).build();
        doReturn(tokensSet)
                .when(mAnonymousCountingTokensSpy)
                .recoverTokens(any(), any(), any(), any(), any(), any(), any(), any());

        MockWebServer server =
                mockWebServerRule.startMockWebServer(
                        ImmutableList.of(
                                serverPublicParamResponse,
                                getChallengeServerResponse,
                                registerClientParamsResponse,
                                getTokensResponseHttpResponse,
                                joinResponse));
        URL getChallengeUrl = server.getUrl(GET_CHALLENGE);
        URL fetchServerParamUrl = server.getUrl(GET_SERVER_PARAM_PATH);
        URL registerClientUrl = server.getUrl(REGISTER_CLIENT_PARAMETERS_PATH);
        URL getTokensResponseUrl = server.getUrl(GET_TOKENS_PATH);
        URL joinUrl = server.getUrl(JOIN_PATH);
        final class FlagsWithUrls extends KAnonE2ETestFlags implements Flags {

            FlagsWithUrls() {
                super(false, 20, true, 100);
            }

            @Override
            public String getFledgeKAnonGetChallengeUrl() {
                return getChallengeUrl.toString();
            }

            @Override
            public boolean getFledgeKAnonKeyAttestationEnabled() {
                return true;
            }

            @Override
            public String getFledgeKAnonRegisterClientParametersUrl() {
                return registerClientUrl.toString();
            }

            @Override
            public String getFledgeKAnonFetchServerParamsUrl() {
                return fetchServerParamUrl.toString();
            }

            @Override
            public String getFledgeKAnonGetTokensUrl() {
                return getTokensResponseUrl.toString();
            }

            @Override
            public String getFledgeKAnonJoinUrl() {
                return joinUrl.toString();
            }
        }

        Flags flagsWithCustomUrlsAndImmediateSignJoinValue100 = new FlagsWithUrls();

        doReturn(flagsWithCustomUrlsAndImmediateSignJoinValue100).when(FlagsFactory::getFlags);
        mFlags = flagsWithCustomUrlsAndImmediateSignJoinValue100;
        CountDownLatch countDownLatch = new CountDownLatch(1);
        PersistAdSelectionResultInput persistAdSelectionResultInput =
                setupTestForPersistAdSelectionResult(countDownLatch);
        mAdSelectionService = createAdSelectionService();
        mClientParametersDao.deleteAllClientParameters();
        mServerParametersDao.deleteAllServerParameters();

        PersistAdSelectionResultTestCallback persistAdSelectionResultTestCallback =
                invokePersistAdSelectionResult(mAdSelectionService, persistAdSelectionResultInput);
        countDownLatch.await();

        verify(mAdServicesLoggerMock, times(1))
                .logKAnonInitializeStats(argumentCaptorInitializeStats.capture());
        KAnonInitializeStatusStats capturedInitializeStats =
                argumentCaptorInitializeStats.getValue();
        assertThat(capturedInitializeStats.getWasSuccessful()).isTrue();

        verify(mAdServicesLoggerMock, times(1))
                .logKAnonGetChallengeJobStats(argumentCaptorGetChallenge.capture());
        KAnonGetChallengeStatusStats kAnonGetChallengeStatusStats =
                argumentCaptorGetChallenge.getValue();
        assertThat(kAnonGetChallengeStatusStats.getResultCode())
                .isEqualTo(KAnonSignJoinStatsConstants.KEY_ATTESTATION_RESULT_SUCCESS);

        verify(mAdServicesLoggerMock, times(1))
                .logKAnonSignStats(argumentCaptorSignStats.capture());
        KAnonSignStatusStats capturedSignStats = argumentCaptorSignStats.getValue();
        assertThat(capturedSignStats.getWasSuccessful()).isTrue();

        verify(mAdServicesLoggerMock, times(1))
                .logKAnonJoinStats(argumentCaptorJoinStats.capture());
        KAnonJoinStatusStats kAnonJoinStatusStats = argumentCaptorJoinStats.getValue();
        assertThat(kAnonJoinStatusStats.getWasSuccessful()).isTrue();
    }

    @Test
    public void persistAdSelectionData_withLoggingDisabled_shouldNotLogStats() throws Exception {
        GetServerPublicParamsResponse getServerPublicParamsResponse =
                GetServerPublicParamsResponse.newBuilder()
                        .setServerParamsVersion(SERVER_PARAM_VERSION)
                        .setServerPublicParams(
                                mTranscript.getServerParameters().getPublicParameters())
                        .build();
        MockResponse serverPublicParamResponse =
                new MockResponse()
                        .setResponseCode(200)
                        .setBody(getServerPublicParamsResponse.toByteArray());
        RegisterClientResponse registerClientResponse =
                RegisterClientResponse.newBuilder()
                        .setClientParamsVersion(CLIENT_PARAMS_VERSION)
                        .build();
        MockResponse registerClientParamsResponse =
                new MockResponse()
                        .setResponseCode(200)
                        .setBody(registerClientResponse.toByteArray());
        GetTokensResponse getTokensResponse =
                GetTokensResponse.newBuilder()
                        .setTokensResponse(mTranscript.getTokensResponse())
                        .build();
        MockResponse getTokensResponseHttp =
                new MockResponse().setResponseCode(200).setBody(getTokensResponse.toByteArray());
        GeneratedTokensRequestProto generatedTokensRequestProto =
                GeneratedTokensRequestProto.newBuilder()
                        .addAllFingerprintsBytes(mTranscript.getFingerprintsList())
                        .setTokenRequest(mTranscript.getTokensRequest())
                        .setTokensRequestPrivateState(mTranscript.getTokensRequestPrivateState())
                        .build();
        BinaryHttpMessage binaryHttpMessage =
                BinaryHttpMessage.knownLengthResponseBuilder(
                                ResponseControlData.builder().setFinalStatusCode(200).build())
                        .setHeaderFields(Fields.builder().appendField("Server", "Apache").build())
                        .setContent("Hello, world!\r\n".getBytes())
                        .build();
        MockResponse joinResponse =
                new MockResponse().setResponseCode(200).setBody(binaryHttpMessage.serialize());
        doReturn(mTranscript.getClientParameters())
                .when(mAnonymousCountingTokensSpy)
                .generateClientParameters(any(), any());
        doReturn(generatedTokensRequestProto)
                .when(mAnonymousCountingTokensSpy)
                .generateTokensRequest(any(), any(), any(), any(), any());

        MockWebServer server =
                mockWebServerRule.startMockWebServer(
                        ImmutableList.of(
                                serverPublicParamResponse,
                                registerClientParamsResponse,
                                getTokensResponseHttp,
                                joinResponse));
        URL fetchServerParamUrl = server.getUrl(GET_SERVER_PARAM_PATH);
        URL registerClientUrl = server.getUrl(REGISTER_CLIENT_PARAMETERS_PATH);
        URL getTokensResponseUrl = server.getUrl(GET_TOKENS_PATH);
        URL joinUrl = server.getUrl(JOIN_PATH);
        final class FlagsWithLogginDisabled extends KAnonE2ETestFlags implements Flags {

            FlagsWithLogginDisabled() {
                super(false, 20, true, 100);
            }

            @Override
            public String getFledgeKAnonRegisterClientParametersUrl() {
                return registerClientUrl.toString();
            }

            @Override
            public String getFledgeKAnonFetchServerParamsUrl() {
                return fetchServerParamUrl.toString();
            }

            @Override
            public String getFledgeKAnonGetTokensUrl() {
                return getTokensResponseUrl.toString();
            }

            @Override
            public String getFledgeKAnonJoinUrl() {
                return joinUrl.toString();
            }

            @Override
            public boolean getFledgeKAnonLoggingEnabled() {
                return false;
            }
        }

        Flags flagsWithLogginDisabled = new FlagsWithLogginDisabled();

        doReturn(flagsWithLogginDisabled).when(FlagsFactory::getFlags);
        mFlags = flagsWithLogginDisabled;
        CountDownLatch countDownLatch = new CountDownLatch(1);
        PersistAdSelectionResultInput persistAdSelectionResultInput =
                setupTestForPersistAdSelectionResult(countDownLatch);
        mAdSelectionService = createAdSelectionService();
        mClientParametersDao.deleteAllClientParameters();
        mServerParametersDao.deleteAllServerParameters();

        PersistAdSelectionResultTestCallback persistAdSelectionResultTestCallback =
                invokePersistAdSelectionResult(mAdSelectionService, persistAdSelectionResultInput);
        countDownLatch.await();

        verify(mAdServicesLoggerMock, times(0)).logKAnonInitializeStats(any());
        verify(mAdServicesLoggerMock, times(0)).logKAnonSignStats(any());
        verify(mAdServicesLoggerMock, times(0)).logKAnonJoinStats(any());
    }

    private void assertGetChallengeRequest(RecordedRequest recordedRequest) {
        assertThat(recordedRequest.getMethod())
                .isEqualTo(AdServicesHttpUtil.HttpMethodType.GET.name());
        assertThat(recordedRequest.getPath()).isEqualTo(GET_CHALLENGE);
        assertThat(recordedRequest.getHeader(AdServicesHttpUtil.CONTENT_TYPE_HDR))
                .isEqualTo(PROTOBUF_CONTENT_TYPE);
        assertThat(recordedRequest.getBody()).isEmpty();
    }

    private void assertGetServerParametersRequest(RecordedRequest recordedRequest) {
        assertThat(recordedRequest.getMethod())
                .isEqualTo(AdServicesHttpUtil.HttpMethodType.GET.name());
        assertThat(recordedRequest.getPath()).isEqualTo(GET_SERVER_PARAM_PATH);
        assertThat(recordedRequest.getHeader(AdServicesHttpUtil.CONTENT_TYPE_HDR))
                .isEqualTo(PROTOBUF_CONTENT_TYPE);
        assertThat(recordedRequest.getBody()).isEmpty();
    }

    private void assertRegisterClientParametersRequest(RecordedRequest recordedRequest)
            throws InvalidProtocolBufferException {
        assertThat(recordedRequest.getMethod())
                .isEqualTo(AdServicesHttpUtil.HttpMethodType.POST.name());
        assertThat(recordedRequest.getPath()).isEqualTo(REGISTER_CLIENT_PARAMETERS_PATH);
        assertThat(recordedRequest.getHeader(AdServicesHttpUtil.CONTENT_TYPE_HDR))
                .isEqualTo(PROTOBUF_CONTENT_TYPE);
        assertThat(recordedRequest.getBody()).isNotEmpty();

        RegisterClientRequest registerClientRequest =
                RegisterClientRequest.parseFrom(recordedRequest.getBody());

        assertThat(registerClientRequest.getServerParamsVersion()).isEqualTo(SERVER_PARAM_VERSION);
        assertThat(registerClientRequest.getClientPublicParams().toByteArray()).isNotEmpty();
        assertThat(registerClientRequest.getClientPublicParams().toByteArray())
                .isEqualTo(mTranscript.getClientParameters().getPublicParameters().toByteArray());
    }

    private void assertGetTokensRequest(RecordedRequest recordedRequest)
            throws InvalidProtocolBufferException {
        assertThat(recordedRequest.getMethod())
                .isEqualTo(AdServicesHttpUtil.HttpMethodType.POST.name());
        assertThat(recordedRequest.getPath()).isEqualTo(GET_TOKENS_PATH);
        assertThat(recordedRequest.getHeader(AdServicesHttpUtil.CONTENT_TYPE_HDR))
                .isEqualTo(PROTOBUF_CONTENT_TYPE);
        assertThat(recordedRequest.getBody()).isNotEmpty();

        GetTokensRequest getTokensRequest = GetTokensRequest.parseFrom(recordedRequest.getBody());

        assertThat(getTokensRequest.getClientParamsVersion()).isEqualTo(CLIENT_PARAMS_VERSION);
    }

    private void assertJoinRequest(RecordedRequest recordedRequest) throws JSONException {
        assertThat(recordedRequest.getMethod())
                .isEqualTo(AdServicesHttpUtil.HttpMethodType.POST.name());
        assertThat(recordedRequest.getPath()).isEqualTo(JOIN_PATH);
        assertThat(recordedRequest.getHeader(AdServicesHttpUtil.CONTENT_TYPE_HDR))
                .isEqualTo(OHTTP_CONTENT_TYPE);
        assertThat(recordedRequest.getBody()).isNotEmpty();

        BinaryHttpMessageDeserializer binaryHttpMessageDeserializer =
                new BinaryHttpMessageDeserializer();
        BinaryHttpMessage binaryHttpMessage =
                binaryHttpMessageDeserializer.deserialize(recordedRequest.getBody());

        assertThat(binaryHttpMessage.isRequest()).isTrue();
        assertThat(binaryHttpMessage.getContent()).isNotEmpty();
        JSONObject jsonBody = new JSONObject(new String(binaryHttpMessage.getContent()));

        assertThat(jsonBody.get("act")).isNotNull();

        RequestControlData requestControlData = binaryHttpMessage.getRequestControlData();

        assertThat(requestControlData.getMethod())
                .isEqualTo(AdServicesHttpUtil.HttpMethodType.POST.name());
        assertThat(requestControlData.getPath()).startsWith("/v2/");
        assertThat(requestControlData.getPath()).endsWith(":join");
    }

    private PersistAdSelectionResultInput setupTestForPersistAdSelectionResult(
            CountDownLatch countDownLatch)
            throws RemoteException, InterruptedException, IOException {
        setupMocksForKAnonWithCountdownlatch(countDownLatch);
        mAdSelectionService = createAdSelectionService();

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
        return persistAdSelectionResultInput;
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
                mKAnonSignJoinFactory,
                false,
                mRetryStrategyFactory);
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

    private void setupMocksForKAnonWithCountdownlatch(CountDownLatch countDownLatch)
            throws IOException {
        mKAnonMessageManager = new KAnonMessageManager(mKAnonMessageDao, mFlags, mockClock);
        UUID userId = UUID.randomUUID();
        when(mockUserProfileIdDao.getUserProfileId()).thenReturn(userId);
        when(mockKAnonOblivivousHttpEncryptorImpl.encryptBytes(
                        any(byte[].class), anyLong(), anyLong(), any()))
                .thenAnswer(
                        invocation ->
                                FluentFuture.from(immediateFuture(invocation.getArgument(0))));
        when(mockKAnonOblivivousHttpEncryptorImpl.decryptBytes(any(byte[].class), anyLong()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        KeyAttestationFactory keyAttestationFactory = new KeyAttestationFactory(mockKeyAttestation);
        when(mockKeyAttestation.generateAttestationRecord(any()))
                .thenReturn(mockKeyAttestationCertificate);
        when(mockKeyAttestationCertificate.encode()).thenReturn(new byte[0]);
        KAnonCallerImpl kAnonCaller =
                new KAnonCallerImpl(
                        AdServicesExecutors.getLightWeightExecutor(),
                        mAnonymousCountingTokensSpy,
                        mAdServicesHttpsClientSpy,
                        mClientParametersDao,
                        mServerParametersDao,
                        mUserProfileIdManager,
                        new BinaryHttpMessageDeserializer(),
                        mFlags,
                        mockKAnonOblivivousHttpEncryptorImpl,
                        mKAnonMessageManager,
                        mAdServicesLoggerMock,
                        keyAttestationFactory);
        KAnonSignJoinManager mKAnonSignJoinManager =
                new KAnonSignJoinManager(
                        mContext,
                        kAnonCaller,
                        mKAnonMessageManager,
                        mFlags,
                        mockClock,
                        mAdServicesLoggerMock);
        mKAnonSignJoinFactory = new KAnonSignJoinFactory(mKAnonSignJoinManager);
        doAnswer(
                        (unused) -> {
                            countDownLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logKAnonSignJoinStatus();
        persistClientParametersInDB();
        persistServerParametersInDB();
    }

    private void persistClientParametersInDB() {
        ClientParameters clientParameters = mTranscript.getClientParameters();
        String clientParamsVersion = CLIENT_PARAMS_VERSION;
        long clientParamsId = 123;
        DBClientParameters dbClientParameters =
                DBClientParameters.builder()
                        .setClientParametersId(clientParamsId)
                        .setClientPrivateParameters(
                                clientParameters.getPrivateParameters().toByteArray())
                        .setClientPublicParameters(
                                clientParameters.getPublicParameters().toByteArray())
                        .setClientId(mUserProfileIdManager.getOrCreateId())
                        .setClientParametersExpiryInstant(Instant.now().plusSeconds(36000))
                        .setClientParamsVersion(clientParamsVersion)
                        .build();
        mClientParametersDao.insertClientParameters(dbClientParameters);
    }

    private void persistServerParametersInDB() {
        mServerParamVersion = SERVER_PARAM_VERSION;
        mServerPublicParameters = mTranscript.getServerParameters().getPublicParameters();
        DBServerParameters serverParametersToSave =
                DBServerParameters.builder()
                        .setServerPublicParameters(mServerPublicParameters.toByteArray())
                        .setCreationInstant(Instant.now())
                        .setServerParamsJoinExpiryInstant(Instant.now().plusSeconds(3600))
                        .setServerParamsSignExpiryInstant(Instant.now().plusSeconds(3600))
                        .setServerParamsVersion(mServerParamVersion)
                        .build();
        mServerParametersDao.insertServerParameters(serverParametersToSave);
    }

    static class KAnonE2ETestFlags implements Flags {
        private final boolean mFledgeAuctionServerKillSwitch;

        private final long mAdIdFetcherTimeoutMs;

        private final boolean mKAnonSignJoinEnabled;

        private final int mKanonImmediateJoinValue;

        KAnonE2ETestFlags(
                boolean fledgeAuctionServerKillSwitch,
                long adIdFetcherTimeoutMs,
                boolean kAnonSignJoinFeatureEnabled,
                int kAnonImmediateJoinValue) {
            mFledgeAuctionServerKillSwitch = fledgeAuctionServerKillSwitch;
            mAdIdFetcherTimeoutMs = adIdFetcherTimeoutMs;
            mKAnonSignJoinEnabled = kAnonSignJoinFeatureEnabled;
            mKanonImmediateJoinValue = kAnonImmediateJoinValue;
        }

        @Override
        public int getFledgeKAnonPercentageImmediateSignJoinCalls() {
            return mKanonImmediateJoinValue;
        }

        @Override
        public boolean getFledgeKAnonLoggingEnabled() {
            return true;
        }

        public boolean getFledgeKAnonKeyAttestationEnabled() {
            return false;
        }

        @Override
        public boolean getFledgeKAnonBackgroundProcessEnabled() {
            return false;
        }

        @Override
        public boolean getFledgeKAnonSignJoinFeatureEnabled() {
            return mKAnonSignJoinEnabled;
        }

        @Override
        public boolean getFledgeAuctionServerKillSwitch() {
            return mFledgeAuctionServerKillSwitch;
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
        public boolean getFledgeAuctionServerEnabledForReportImpression() {
            return true;
        }
    }
}
