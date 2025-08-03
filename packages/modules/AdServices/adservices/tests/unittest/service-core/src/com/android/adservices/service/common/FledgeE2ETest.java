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

package com.android.adservices.service.common;

import static android.adservices.adselection.CustomAudienceBiddingInfoFixture.DATA_VERSION_1;
import static android.adservices.adselection.CustomAudienceBiddingInfoFixture.DATA_VERSION_2;
import static android.adservices.customaudience.CustomAudienceFixture.VALID_OWNER;

import static com.android.adservices.data.adselection.AdSelectionDatabase.DATABASE_NAME;
import static com.android.adservices.service.adselection.AdSelectionScriptEngine.NUM_BITS_STOCHASTIC_ROUNDING;
import static com.android.adservices.service.adselection.DataVersionFetcher.DATA_VERSION_HEADER_BIDDING_KEY;
import static com.android.adservices.service.adselection.DataVersionFetcher.DATA_VERSION_HEADER_SCORING_KEY;
import static com.android.adservices.service.adselection.ImpressionReporterLegacy.CALLER_PACKAGE_NAME_MISMATCH;
import static com.android.adservices.service.common.Throttler.ApiKey.FLEDGE_API_FETCH_CUSTOM_AUDIENCE;
import static com.android.adservices.service.common.Throttler.ApiKey.FLEDGE_API_JOIN_CUSTOM_AUDIENCE;
import static com.android.adservices.service.common.Throttler.ApiKey.FLEDGE_API_LEAVE_CUSTOM_AUDIENCE;
import static com.android.adservices.service.common.Throttler.ApiKey.FLEDGE_API_REPORT_IMPRESSIONS;
import static com.android.adservices.service.common.Throttler.ApiKey.FLEDGE_API_SELECT_ADS;
import static com.android.adservices.service.customaudience.FetchCustomAudienceImplTest.FetchCustomAudienceTestCallback;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTest.DB_AD_SELECTION_FILE_SIZE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyBoolean;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.eq;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mock;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.times;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.when;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;

import android.adservices.adid.AdId;
import android.adservices.adselection.AdSelectionCallback;
import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionConfigFixture;
import android.adservices.adselection.AdSelectionInput;
import android.adservices.adselection.AdSelectionOverrideCallback;
import android.adservices.adselection.AdSelectionResponse;
import android.adservices.adselection.DecisionLogic;
import android.adservices.adselection.PerBuyerDecisionLogic;
import android.adservices.adselection.ReportEventRequest;
import android.adservices.adselection.ReportImpressionCallback;
import android.adservices.adselection.ReportImpressionInput;
import android.adservices.adselection.ReportInteractionCallback;
import android.adservices.adselection.ReportInteractionInput;
import android.adservices.adselection.SetAppInstallAdvertisersCallback;
import android.adservices.adselection.SetAppInstallAdvertisersInput;
import android.adservices.adselection.SignedContextualAds;
import android.adservices.adselection.SignedContextualAdsFixture;
import android.adservices.adselection.UpdateAdCounterHistogramInput;
import android.adservices.common.AdData;
import android.adservices.common.AdDataFixture;
import android.adservices.common.AdFilters;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.AppInstallFilters;
import android.adservices.common.CallerMetadata;
import android.adservices.common.CallingAppUidSupplierProcessImpl;
import android.adservices.common.CommonFixture;
import android.adservices.common.FledgeErrorResponse;
import android.adservices.common.FrequencyCapFilters;
import android.adservices.common.KeyedFrequencyCap;
import android.adservices.common.KeyedFrequencyCapFixture;
import android.adservices.customaudience.CustomAudience;
import android.adservices.customaudience.CustomAudienceFixture;
import android.adservices.customaudience.CustomAudienceOverrideCallback;
import android.adservices.customaudience.FetchAndJoinCustomAudienceInput;
import android.adservices.customaudience.ICustomAudienceCallback;
import android.adservices.customaudience.TrustedBiddingData;
import android.adservices.customaudience.TrustedBiddingDataFixture;
import android.adservices.http.MockWebServerRule;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.FlakyTest;

import com.android.adservices.MockWebServerRuleFactory;
import com.android.adservices.common.AdServicesDeviceSupportedRule;
import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.common.SupportedByConditionRule;
import com.android.adservices.common.WebViewSupportUtil;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.adselection.AdSelectionDatabase;
import com.android.adservices.data.adselection.AdSelectionDebugReportDao;
import com.android.adservices.data.adselection.AdSelectionDebugReportingDatabase;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.adselection.AdSelectionServerDatabase;
import com.android.adservices.data.adselection.AppInstallDao;
import com.android.adservices.data.adselection.DBAdSelectionDebugReport;
import com.android.adservices.data.adselection.EncryptionContextDao;
import com.android.adservices.data.adselection.FrequencyCapDao;
import com.android.adservices.data.adselection.SharedStorageDatabase;
import com.android.adservices.data.common.DBAdData;
import com.android.adservices.data.customaudience.AdDataConversionStrategy;
import com.android.adservices.data.customaudience.AdDataConversionStrategyFactory;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.CustomAudienceDatabase;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.data.customaudience.DBTrustedBiddingData;
import com.android.adservices.data.encryptionkey.EncryptionKeyDao;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.signals.EncodedPayloadDao;
import com.android.adservices.data.signals.ProtectedSignalsDatabase;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.adid.AdIdCacheManager;
import com.android.adservices.service.adselection.AdCost;
import com.android.adservices.service.adselection.AdFilteringFeatureFactory;
import com.android.adservices.service.adselection.AdIdFetcher;
import com.android.adservices.service.adselection.AdSelectionServiceImpl;
import com.android.adservices.service.adselection.DebugReportSenderJobService;
import com.android.adservices.service.adselection.EventReporter;
import com.android.adservices.service.adselection.JsVersionHelper;
import com.android.adservices.service.adselection.JsVersionRegister;
import com.android.adservices.service.adselection.MockAdIdWorker;
import com.android.adservices.service.adselection.MultiCloudSupportStrategy;
import com.android.adservices.service.adselection.MultiCloudTestStrategyFactory;
import com.android.adservices.service.adselection.UpdateAdCounterHistogramWorkerTest;
import com.android.adservices.service.adselection.encryption.ObliviousHttpEncryptor;
import com.android.adservices.service.common.cache.CacheProviderFactory;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.consent.AdServicesApiConsent;
import com.android.adservices.service.consent.AdServicesApiType;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.customaudience.BackgroundFetchJobService;
import com.android.adservices.service.customaudience.CustomAudienceBlobFixture;
import com.android.adservices.service.customaudience.CustomAudienceImpl;
import com.android.adservices.service.customaudience.CustomAudienceQuantityChecker;
import com.android.adservices.service.customaudience.CustomAudienceServiceImpl;
import com.android.adservices.service.customaudience.CustomAudienceValidator;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.devapi.DevContextFilter;
import com.android.adservices.service.exception.FilterException;
import com.android.adservices.service.kanon.KAnonSignJoinFactory;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.Spy;
import org.mockito.quality.Strictness;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class FledgeE2ETest {
    public static final String CUSTOM_AUDIENCE_SEQ_1 = "/ca1";
    public static final String CUSTOM_AUDIENCE_SEQ_2 = "/ca2";
    private static final String FETCH_CA_PATH = "/fetch";
    public static final AppInstallFilters CURRENT_APP_FILTER =
            new AppInstallFilters.Builder()
                    .setPackageNames(new HashSet<>(Arrays.asList(CommonFixture.TEST_PACKAGE_NAME)))
                    .build();
    public static final FrequencyCapFilters CLICK_ONCE_PER_DAY_KEY1 =
            new FrequencyCapFilters.Builder()
                    .setKeyedFrequencyCapsForClickEvents(
                            ImmutableList.of(
                                    new KeyedFrequencyCap.Builder(
                                                    KeyedFrequencyCapFixture.KEY1,
                                                    1,
                                                    Duration.ofDays(1))
                                            .build()))
                    .build();
    @Spy private static final Context CONTEXT_SPY = ApplicationProvider.getApplicationContext();

    private static final AdTechIdentifier LOCALHOST_BUYER =
            AdTechIdentifier.fromString("localhost");
    private static final Uri BUYER_DOMAIN_1 =
            CommonFixture.getUri(AdSelectionConfigFixture.BUYER_1, "");
    private static final Uri BUYER_DOMAIN_2 =
            CommonFixture.getUri(AdSelectionConfigFixture.BUYER_2, "");
    private static final String AD_URI_PREFIX = "/adverts/123";
    private static final String BUYER_BIDDING_LOGIC_URI_PATH = "/buyer/bidding/logic/";
    private static final String BUYER_TRUSTED_SIGNAL_URI_PATH = "/kv/buyer/signals/";
    private static final String BUYER_TRUSTED_SIGNAL_URI_PATH_WITH_DATA_VERSION =
            "/kv/buyer/data/version/signals/";
    private static final String SELLER_DECISION_LOGIC_URI_PATH = "/ssp/decision/logic/";
    private static final String SELLER_TRUSTED_SIGNAL_URI_PATH = "/kv/seller/signals/";
    private static final String SELLER_TRUSTED_SIGNAL_URI_PATH_WITH_DATA_VERSION =
            "/kv/seller/data/version/signals/";
    private static final String SELLER_TRUSTED_SIGNAL_PARAMS = "?renderuris=";
    private static final String SELLER_REPORTING_PATH = "/reporting/seller";
    private static final String SELLER_DEBUG_REPORT_WIN_PATH = "/seller/reportWin";
    private static final String SELLER_DEBUG_REPORT_LOSS_PATH = "/seller/reportLoss";
    private static final String DEBUG_REPORT_WINNING_BID_PARAM = "?wb=${winningBid}";
    private static final String DEBUG_REPORT_WINNING_BID_RESPONSE = "?wb=10.0";
    private static final String BUYER_REPORTING_PATH = "/reporting/buyer";
    private static final String BUYER_DEBUG_REPORT_WIN_PATH = "/buyer/reportWin";
    private static final String BUYER_DEBUG_REPORT_LOSS_PATH = "/buyer/reportLoss";
    private static final AdSelectionSignals TRUSTED_BIDDING_SIGNALS =
            AdSelectionSignals.fromString(
                    "{\n"
                            + "\t\"example\": \"example\",\n"
                            + "\t\"valid\": \"Also valid\",\n"
                            + "\t\"list\": \"list\",\n"
                            + "\t\"of\": \"of\",\n"
                            + "\t\"keys\": \"trusted bidding signal Values\"\n"
                            + "}");
    private static final AdSelectionSignals TRUSTED_SCORING_SIGNALS =
            AdSelectionSignals.fromString(
                    "{\n"
                            + "\t\"render_uri_1\": \"signals_for_1\",\n"
                            + "\t\"render_uri_2\": \"signals_for_2\"\n"
                            + "}");
    private static final PerBuyerDecisionLogic BUYERS_DECISION_LOGIC =
            new PerBuyerDecisionLogic(
                    ImmutableMap.of(
                            CommonFixture.VALID_BUYER_1, new DecisionLogic("reportWin()"),
                            CommonFixture.VALID_BUYER_2, new DecisionLogic("reportWin()")));

    // Interaction reporting contestants
    private static final String CLICK_INTERACTION = "click";

    private static final String CLICK_SELLER_PATH = "/click/seller";
    private static final String HOVER_SELLER_PATH = "/hover/seller";

    private static final String CLICK_BUYER_PATH = "/click/buyer";
    private static final String HOVER_BUYER_PATH = "/hover/buyer";

    private static final String INTERACTION_DATA = "{\"key\":\"value\"}";

    private static final int BUYER_DESTINATION =
            ReportEventRequest.FLAG_REPORTING_DESTINATION_BUYER;
    private static final int SELLER_DESTINATION =
            ReportEventRequest.FLAG_REPORTING_DESTINATION_SELLER;

    private static final long BINDER_ELAPSED_TIMESTAMP = 100L;
    private static final List<Double> BIDS_FOR_BUYER_1 = ImmutableList.of(1.1, 2.2);
    private static final List<Double> BIDS_FOR_BUYER_2 = ImmutableList.of(4.5, 6.7, 10.0);
    // A list of empty ad counter keys to apply to ads for buyer when not doing fcap filtering.
    private static final List<Set<Integer>> EMPTY_AD_COUNTER_KEYS_FOR_BUYER_2 =
            Arrays.asList(new HashSet[BIDS_FOR_BUYER_2.size()]);
    private static final List<Double> INVALID_BIDS = ImmutableList.of(0.0, -1.0, -2.0);

    private static final AdCost AD_COST_1 = new AdCost(1.2, NUM_BITS_STOCHASTIC_ROUNDING);
    private static final AdCost AD_COST_2 = new AdCost(2.2, NUM_BITS_STOCHASTIC_ROUNDING);

    @Mock private AdServicesLogger mAdServicesLoggerMock;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Rule(order = 1)
    public final AdServicesDeviceSupportedRule deviceSupportRule =
            new AdServicesDeviceSupportedRule();

    // Every test in this class requires that the JS Sandbox be available. The JS Sandbox
    // availability depends on an external component (the system webview) being higher than a
    // certain minimum version.
    @Rule(order = 2)
    public final SupportedByConditionRule webViewSupportsJSSandbox =
            WebViewSupportUtil.createJSSandboxAvailableRule(
                    ApplicationProvider.getApplicationContext());

    @Rule(order = 3)
    public final MockWebServerRule mockWebServerRule = MockWebServerRuleFactory.createForHttps();

    @Mock private ConsentManager mConsentManagerMock;
    private MockitoSession mStaticMockSession = null;
    // This object access some system APIs
    @Mock private DevContextFilter mDevContextFilterMock;
    @Mock private Throttler mMockThrottler;
    private AdSelectionConfig mAdSelectionConfig;
    private AdServicesHttpsClient mAdServicesHttpsClient;
    private CustomAudienceDao mCustomAudienceDao;
    private EncodedPayloadDao mEncodedPayloadDao;
    private AdSelectionEntryDao mAdSelectionEntryDao;
    private AppInstallDao mAppInstallDao;
    private FrequencyCapDao mFrequencyCapDao;
    private EncryptionKeyDao mEncryptionKeyDao;
    private EnrollmentDao mEnrollmentDao;
    private EncryptionContextDao mEncryptionContextDao;
    private ExecutorService mLightweightExecutorService;
    private ExecutorService mBackgroundExecutorService;
    private ScheduledThreadPoolExecutor mScheduledExecutor;
    private CustomAudienceServiceImpl mCustomAudienceService;
    private AdSelectionServiceImpl mAdSelectionService;

    private static final Flags DEFAULT_FLAGS =
            new FledgeE2ETestFlags(
                    false, true, true, true, false, false, false, false, false, false);
    private MockWebServerRule.RequestMatcher<String> mRequestMatcherPrefixMatch;
    private Uri mLocalhostBuyerDomain;
    private AdFilteringFeatureFactory mAdFilteringFeatureFactory;
    private FledgeAllowListsFilter mFledgeAllowListsFilterSpy;
    @Mock FledgeAuthorizationFilter mFledgeAuthorizationFilterMock;
    @Mock private File mMockDBAdSelectionFile;
    @Mock private AdSelectionServiceFilter mAdSelectionServiceFilterMock;
    @Mock private AppImportanceFilter mAppImportanceFilterMock;
    @Mock private ObliviousHttpEncryptor mObliviousHttpEncryptor;
    private MultiCloudSupportStrategy mMultiCloudSupportStrategy =
            MultiCloudTestStrategyFactory.getDisabledTestStrategy(mObliviousHttpEncryptor);
    private AdSelectionDebugReportDao mAdSelectionDebugReportDao;
    private MockAdIdWorker mMockAdIdWorker;
    private AdIdFetcher mAdIdFetcher;
    @Mock private KAnonSignJoinFactory mUnusedKAnonSignJoinFactory;
    private RetryStrategyFactory mRetryStrategyFactory;

    @Before
    public void setUp() throws Exception {
        // Test applications don't have the required permissions to read config P/H flags, and
        // injecting mocked flags everywhere is annoying and non-trivial for static methods
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(FlagsFactory.class)
                        .mockStatic(BackgroundFetchJobService.class)
                        .mockStatic(ConsentManager.class)
                        .mockStatic(AppImportanceFilter.class)
                        .mockStatic(DebugReportSenderJobService.class)
                        .strictness(Strictness.LENIENT)
                        .initMocks(this)
                        .startMocking();

        doReturn(DEFAULT_FLAGS).when(FlagsFactory::getFlags);

        mFledgeAllowListsFilterSpy =
                spy(new FledgeAllowListsFilter(DEFAULT_FLAGS, mAdServicesLoggerMock));

        mCustomAudienceDao =
                Room.inMemoryDatabaseBuilder(CONTEXT_SPY, CustomAudienceDatabase.class)
                        .addTypeConverter(new DBCustomAudience.Converters(true, true))
                        .build()
                        .customAudienceDao();
        mEncodedPayloadDao =
                Room.inMemoryDatabaseBuilder(CONTEXT_SPY, ProtectedSignalsDatabase.class)
                        .build()
                        .getEncodedPayloadDao();

        mAdSelectionEntryDao =
                Room.inMemoryDatabaseBuilder(CONTEXT_SPY, AdSelectionDatabase.class)
                        .build()
                        .adSelectionEntryDao();
        SharedStorageDatabase sharedDb =
                Room.inMemoryDatabaseBuilder(CONTEXT_SPY, SharedStorageDatabase.class).build();
        mAppInstallDao = sharedDb.appInstallDao();
        mFrequencyCapDao = sharedDb.frequencyCapDao();
        AdSelectionServerDatabase serverDb =
                Room.inMemoryDatabaseBuilder(CONTEXT_SPY, AdSelectionServerDatabase.class).build();
        mEncryptionKeyDao = EncryptionKeyDao.getInstance(CONTEXT_SPY);
        mEnrollmentDao = EnrollmentDao.getInstance(CONTEXT_SPY);
        mAdFilteringFeatureFactory =
                new AdFilteringFeatureFactory(mAppInstallDao, mFrequencyCapDao, DEFAULT_FLAGS);

        mLightweightExecutorService = AdServicesExecutors.getLightWeightExecutor();
        mBackgroundExecutorService = AdServicesExecutors.getBackgroundExecutor();
        mScheduledExecutor = AdServicesExecutors.getScheduler();

        mAdServicesHttpsClient =
                new AdServicesHttpsClient(
                        AdServicesExecutors.getBlockingExecutor(),
                        CacheProviderFactory.createNoOpCache());
        mAdSelectionDebugReportDao =
                Room.inMemoryDatabaseBuilder(CONTEXT_SPY, AdSelectionDebugReportingDatabase.class)
                        .build()
                        .getAdSelectionDebugReportDao();
        mMockAdIdWorker = new MockAdIdWorker(new AdIdCacheManager(CONTEXT_SPY));
        mAdIdFetcher =
                new AdIdFetcher(mMockAdIdWorker, mLightweightExecutorService, mScheduledExecutor);
        mRetryStrategyFactory = RetryStrategyFactory.createInstanceForTesting();
        initClients(false, true, false, false, false);

        mRequestMatcherPrefixMatch = (a, b) -> !b.isEmpty() && a.startsWith(b);

        when(mMockThrottler.tryAcquire(eq(FLEDGE_API_SELECT_ADS), anyString())).thenReturn(true);
        when(mMockThrottler.tryAcquire(eq(FLEDGE_API_REPORT_IMPRESSIONS), anyString()))
                .thenReturn(true);
        when(mMockThrottler.tryAcquire(eq(FLEDGE_API_JOIN_CUSTOM_AUDIENCE), anyString()))
                .thenReturn(true);
        when(mMockThrottler.tryAcquire(eq(FLEDGE_API_FETCH_CUSTOM_AUDIENCE), anyString()))
                .thenReturn(true);
        when(mMockThrottler.tryAcquire(eq(FLEDGE_API_LEAVE_CUSTOM_AUDIENCE), anyString()))
                .thenReturn(true);

        mLocalhostBuyerDomain = Uri.parse(mockWebServerRule.getServerBaseAddress());
        when(CONTEXT_SPY.getDatabasePath(DATABASE_NAME)).thenReturn(mMockDBAdSelectionFile);
        when(mMockDBAdSelectionFile.length()).thenReturn(DB_AD_SELECTION_FILE_SIZE);
        doNothing()
                .when(mAdSelectionServiceFilterMock)
                .filterRequest(
                        any(),
                        anyString(),
                        anyBoolean(),
                        anyBoolean(),
                        anyInt(),
                        anyInt(),
                        any(),
                        any());
        when(ConsentManager.getInstance()).thenReturn(mConsentManagerMock);
        when(AppImportanceFilter.create(any(), anyInt(), any()))
                .thenReturn(mAppImportanceFilterMock);
        doNothing()
                .when(mAppImportanceFilterMock)
                .assertCallerIsInForeground(anyInt(), anyInt(), any());
        mMockAdIdWorker.setResult(AdId.ZERO_OUT, true);
    }

    @After
    public void teardown() {
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
    }

    @Test
    public void testFledgeFlowSuccessWithDevOverridesRegisterAdBeaconDisabled() throws Exception {
        // Re init clients with registerAdBeacon false
        initClients(false, false, false, false, false);

        setupConsentGivenStubs();

        setupAdSelectionConfig();

        String decisionLogicJs = getDecisionLogicJs();
        String biddingLogicJs = getBiddingLogicJs();

        MockWebServer server =
                mockWebServerRule.startMockWebServer(
                        request -> new MockResponse().setResponseCode(404));

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(CommonFixture.TEST_PACKAGE_NAME)
                                .build());

        doNothing()
                .when(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), anyBoolean()));

        CustomAudience customAudience1 =
                createCustomAudience(
                        mLocalhostBuyerDomain, CUSTOM_AUDIENCE_SEQ_1, BIDS_FOR_BUYER_1);

        CustomAudience customAudience2 =
                createCustomAudience(
                        mLocalhostBuyerDomain, CUSTOM_AUDIENCE_SEQ_2, BIDS_FOR_BUYER_2);

        joinCustomAudienceAndAssertSuccess(customAudience1);
        joinCustomAudienceAndAssertSuccess(customAudience2);

        verify(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), eq(false)), times(2));

        setupOverridesAndAssertSuccess(
                customAudience1, customAudience2, biddingLogicJs, decisionLogicJs);

        // Run Ad Selection
        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(
                        mAdSelectionService, mAdSelectionConfig, CommonFixture.TEST_PACKAGE_NAME);

        assertTrue(resultsCallback.mIsSuccess);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDao.doesAdSelectionIdExist(resultSelectionId));
        assertEquals(
                CommonFixture.getUri(
                        mLocalhostBuyerDomain.getAuthority(),
                        AD_URI_PREFIX + CUSTOM_AUDIENCE_SEQ_2 + "/ad3"),
                resultsCallback.mAdSelectionResponse.getRenderUri());

        reportImpressionAndAssertSuccess(resultsCallback.mAdSelectionResponse.getAdSelectionId());
        mockWebServerRule.verifyMockServerRequests(
                server, 0, ImmutableList.of(), mRequestMatcherPrefixMatch);
    }

    @Test
    public void testFledgeFlowSuccessWithDevOverridesWithAdCostCpcBillingEnabled()
            throws Exception {
        // Re init with cpc billing enabled
        initClients(false, false, true, false, false);

        setupConsentGivenStubs();

        setupAdSelectionConfig();

        String decisionLogicJs = getDecisionLogicJs();
        String biddingLogicJs = getBiddingLogicJsWithAdCost();

        MockWebServer server =
                mockWebServerRule.startMockWebServer(
                        request -> new MockResponse().setResponseCode(404));

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(CommonFixture.TEST_PACKAGE_NAME)
                                .build());

        doNothing()
                .when(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), anyBoolean()));

        CustomAudience customAudience1 =
                createCustomAudienceWithAdCost(
                        mLocalhostBuyerDomain,
                        CUSTOM_AUDIENCE_SEQ_1,
                        BIDS_FOR_BUYER_1,
                        AD_COST_1.getAdCost());

        CustomAudience customAudience2 =
                createCustomAudienceWithAdCost(
                        mLocalhostBuyerDomain,
                        CUSTOM_AUDIENCE_SEQ_2,
                        BIDS_FOR_BUYER_2,
                        AD_COST_2.getAdCost());

        joinCustomAudienceAndAssertSuccess(customAudience1);
        joinCustomAudienceAndAssertSuccess(customAudience2);

        verify(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), eq(false)), times(2));

        setupOverridesAndAssertSuccess(
                customAudience1, customAudience2, biddingLogicJs, decisionLogicJs);

        // Run Ad Selection
        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(
                        mAdSelectionService, mAdSelectionConfig, CommonFixture.TEST_PACKAGE_NAME);

        assertTrue(resultsCallback.mIsSuccess);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDao.doesAdSelectionIdExist(resultSelectionId));
        assertEquals(
                CommonFixture.getUri(
                        mLocalhostBuyerDomain.getAuthority(),
                        AD_URI_PREFIX + CUSTOM_AUDIENCE_SEQ_2 + "/ad3"),
                resultsCallback.mAdSelectionResponse.getRenderUri());

        reportImpressionAndAssertSuccess(resultsCallback.mAdSelectionResponse.getAdSelectionId());
        mockWebServerRule.verifyMockServerRequests(
                server, 0, ImmutableList.of(), mRequestMatcherPrefixMatch);
    }

    @Test
    public void testFledgeFlowSuccessWithDevOverridesWithAdCostCpcBillingDisabled()
            throws Exception {
        // Re init with cpc billing enabled
        initClients(false, false, false, false, false);

        setupConsentGivenStubs();

        setupAdSelectionConfig();

        String decisionLogicJs = getDecisionLogicJs();
        String biddingLogicJs = getBiddingLogicJsWithAdCost();

        MockWebServer server =
                mockWebServerRule.startMockWebServer(
                        request -> new MockResponse().setResponseCode(404));

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(CommonFixture.TEST_PACKAGE_NAME)
                                .build());

        doNothing()
                .when(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), anyBoolean()));

        CustomAudience customAudience1 =
                createCustomAudienceWithAdCost(
                        mLocalhostBuyerDomain,
                        CUSTOM_AUDIENCE_SEQ_1,
                        BIDS_FOR_BUYER_1,
                        AD_COST_1.getAdCost());

        CustomAudience customAudience2 =
                createCustomAudienceWithAdCost(
                        mLocalhostBuyerDomain,
                        CUSTOM_AUDIENCE_SEQ_2,
                        BIDS_FOR_BUYER_2,
                        AD_COST_2.getAdCost());

        joinCustomAudienceAndAssertSuccess(customAudience1);
        joinCustomAudienceAndAssertSuccess(customAudience2);

        verify(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), eq(false)), times(2));

        setupOverridesAndAssertSuccess(
                customAudience1, customAudience2, biddingLogicJs, decisionLogicJs);

        // Run Ad Selection
        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(
                        mAdSelectionService, mAdSelectionConfig, CommonFixture.TEST_PACKAGE_NAME);

        assertTrue(resultsCallback.mIsSuccess);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDao.doesAdSelectionIdExist(resultSelectionId));
        assertEquals(
                CommonFixture.getUri(
                        mLocalhostBuyerDomain.getAuthority(),
                        AD_URI_PREFIX + CUSTOM_AUDIENCE_SEQ_2 + "/ad3"),
                resultsCallback.mAdSelectionResponse.getRenderUri());

        reportImpressionAndAssertSuccess(resultsCallback.mAdSelectionResponse.getAdSelectionId());
        mockWebServerRule.verifyMockServerRequests(
                server, 0, ImmutableList.of(), mRequestMatcherPrefixMatch);
    }

    @Test
    public void testFledgeFlowSuccessWithMockServerReportsAdCostCpcBillingEnabled()
            throws Exception {
        // Re init with cpc billing enabled
        initClients(false, true, true, false, false);
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        doReturn(false)
                .when(mConsentManagerMock)
                .isFledgeConsentRevokedForAppAfterSettingFledgeUse(any());

        setupAdSelectionConfig();
        String decisionLogicJs = getDecisionLogicWithBeacons();
        String biddingLogicJs = getBiddingLogicWithBeaconsWithAdCost();

        CustomAudience customAudience1 =
                createCustomAudienceWithAdCost(
                        mLocalhostBuyerDomain,
                        CUSTOM_AUDIENCE_SEQ_1,
                        BIDS_FOR_BUYER_1,
                        AD_COST_1.getAdCost());

        CustomAudience customAudience2 =
                createCustomAudienceWithAdCost(
                        mLocalhostBuyerDomain,
                        CUSTOM_AUDIENCE_SEQ_2,
                        BIDS_FOR_BUYER_2,
                        AD_COST_2.getAdCost());

        // We add permits to the semaphores when the MWS is called and remove them in the asserts
        Semaphore impressionReportingSemaphore = new Semaphore(0);
        Semaphore interactionReportingSemaphore = new Semaphore(0);

        MockWebServer server =
                getMockWebServer(
                        decisionLogicJs,
                        biddingLogicJs,
                        impressionReportingSemaphore,
                        interactionReportingSemaphore,
                        false);

        doNothing()
                .when(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), anyBoolean()));

        joinCustomAudienceAndAssertSuccess(customAudience1);
        joinCustomAudienceAndAssertSuccess(customAudience2);

        verify(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), eq(false)), times(2));

        selectAdsAndReport(
                CommonFixture.getUri(
                        mLocalhostBuyerDomain.getAuthority(),
                        AD_URI_PREFIX + CUSTOM_AUDIENCE_SEQ_2 + "/ad3"),
                impressionReportingSemaphore,
                interactionReportingSemaphore);
        verifyStandardServerRequests(server);
    }

    @Test
    public void testFledgeFlowSuccessWithMockServerWithDataVersionHeaderEnabled() throws Exception {
        // Re init with data version header enabled
        initClients(false, true, false, true, false);
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        doReturn(false)
                .when(mConsentManagerMock)
                .isFledgeConsentRevokedForAppAfterSettingFledgeUse(any());

        setupAdSelectionConfigWithDataVersionHeader();

        String decisionLogicJs = getDecisionLogicWithDataVersion();
        String biddingLogicJs = getBiddingLogicWithDataVersion();

        CustomAudience customAudience1 =
                createCustomAudienceWithDataVersion(
                        mLocalhostBuyerDomain, CUSTOM_AUDIENCE_SEQ_1, BIDS_FOR_BUYER_1);

        CustomAudience customAudience2 =
                createCustomAudienceWithDataVersion(
                        mLocalhostBuyerDomain, CUSTOM_AUDIENCE_SEQ_2, BIDS_FOR_BUYER_2);

        // We add permits to the semaphores when the MWS is called and remove them in the asserts
        Semaphore impressionReportingSemaphore = new Semaphore(0);
        Semaphore interactionReportingSemaphore = new Semaphore(0);

        MockWebServer server =
                getMockWebServer(
                        decisionLogicJs,
                        biddingLogicJs,
                        impressionReportingSemaphore,
                        interactionReportingSemaphore,
                        false);

        doNothing()
                .when(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), anyBoolean()));

        joinCustomAudienceAndAssertSuccess(customAudience1);
        joinCustomAudienceAndAssertSuccess(customAudience2);

        verify(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), eq(false)), times(2));

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(
                        mAdSelectionService, mAdSelectionConfig, CommonFixture.TEST_PACKAGE_NAME);

        Uri expectedWinningrUi =
                CommonFixture.getUri(
                        mLocalhostBuyerDomain.getAuthority(),
                        AD_URI_PREFIX + CUSTOM_AUDIENCE_SEQ_2 + "/ad3");

        assertTrue(resultsCallback.mIsSuccess);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDao.doesAdSelectionIdExist(resultSelectionId));
        assertEquals(expectedWinningrUi, resultsCallback.mAdSelectionResponse.getRenderUri());

        reportImpressionAndAssertSuccess(resultSelectionId);

        assertTrue(impressionReportingSemaphore.tryAcquire(2, 10, TimeUnit.SECONDS));
        assertEquals(
                "Extra calls made to MockWebServer",
                0,
                impressionReportingSemaphore.availablePermits());

        // interaction should not happen, so there should be no permits
        assertEquals(
                "Extra calls made to MockWebServer",
                0,
                interactionReportingSemaphore.availablePermits());

        mockWebServerRule.verifyMockServerRequests(
                server,
                8,
                ImmutableList.of(
                        SELLER_DECISION_LOGIC_URI_PATH,
                        BUYER_BIDDING_LOGIC_URI_PATH + CUSTOM_AUDIENCE_SEQ_1,
                        BUYER_BIDDING_LOGIC_URI_PATH + CUSTOM_AUDIENCE_SEQ_2,
                        BUYER_TRUSTED_SIGNAL_URI_PATH_WITH_DATA_VERSION,
                        SELLER_TRUSTED_SIGNAL_URI_PATH_WITH_DATA_VERSION
                                + SELLER_TRUSTED_SIGNAL_PARAMS,
                        SELLER_REPORTING_PATH + "?dataVersion=" + DATA_VERSION_2,
                        BUYER_REPORTING_PATH + "?dataVersion=" + DATA_VERSION_1),
                mRequestMatcherPrefixMatch);
    }

    @Test
    public void testFledgeFlowSuccessWithMockServerWithDataVersionHeaderDisabled()
            throws Exception {
        // Re init with data version header disabled
        initClients(false, true, false, false, false);
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        doReturn(false)
                .when(mConsentManagerMock)
                .isFledgeConsentRevokedForAppAfterSettingFledgeUse(any());

        setupAdSelectionConfigWithDataVersionHeader();

        String decisionLogicJs = getDecisionLogicJs();
        String biddingLogicJs = getBiddingLogicJs();

        CustomAudience customAudience1 =
                createCustomAudienceWithDataVersion(
                        mLocalhostBuyerDomain, CUSTOM_AUDIENCE_SEQ_1, BIDS_FOR_BUYER_1);

        CustomAudience customAudience2 =
                createCustomAudienceWithDataVersion(
                        mLocalhostBuyerDomain, CUSTOM_AUDIENCE_SEQ_2, BIDS_FOR_BUYER_2);

        // We add permits to the semaphores when the MWS is called and remove them in the asserts
        Semaphore impressionReportingSemaphore = new Semaphore(0);
        Semaphore interactionReportingSemaphore = new Semaphore(0);

        MockWebServer server =
                getMockWebServer(
                        decisionLogicJs,
                        biddingLogicJs,
                        impressionReportingSemaphore,
                        interactionReportingSemaphore,
                        false);

        doNothing()
                .when(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), anyBoolean()));

        joinCustomAudienceAndAssertSuccess(customAudience1);
        joinCustomAudienceAndAssertSuccess(customAudience2);

        verify(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), eq(false)), times(2));

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(
                        mAdSelectionService, mAdSelectionConfig, CommonFixture.TEST_PACKAGE_NAME);

        Uri expectedWinningrUi =
                CommonFixture.getUri(
                        mLocalhostBuyerDomain.getAuthority(),
                        AD_URI_PREFIX + CUSTOM_AUDIENCE_SEQ_2 + "/ad3");

        assertTrue(resultsCallback.mIsSuccess);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDao.doesAdSelectionIdExist(resultSelectionId));
        assertEquals(expectedWinningrUi, resultsCallback.mAdSelectionResponse.getRenderUri());

        reportImpressionAndAssertSuccess(resultSelectionId);

        assertTrue(impressionReportingSemaphore.tryAcquire(2, 10, TimeUnit.SECONDS));
        assertEquals(
                "Extra calls made to MockWebServer",
                0,
                impressionReportingSemaphore.availablePermits());

        // interaction should not happen, so there should be no permits
        assertEquals(
                "Extra calls made to MockWebServer",
                0,
                interactionReportingSemaphore.availablePermits());

        // Verify requests without data version header are made
        mockWebServerRule.verifyMockServerRequests(
                server,
                8,
                ImmutableList.of(
                        SELLER_DECISION_LOGIC_URI_PATH,
                        BUYER_BIDDING_LOGIC_URI_PATH + CUSTOM_AUDIENCE_SEQ_1,
                        BUYER_BIDDING_LOGIC_URI_PATH + CUSTOM_AUDIENCE_SEQ_2,
                        BUYER_TRUSTED_SIGNAL_URI_PATH_WITH_DATA_VERSION,
                        SELLER_TRUSTED_SIGNAL_URI_PATH_WITH_DATA_VERSION
                                + SELLER_TRUSTED_SIGNAL_PARAMS,
                        SELLER_REPORTING_PATH,
                        BUYER_REPORTING_PATH),
                mRequestMatcherPrefixMatch);
    }

    @Test
    public void testFledgeFlowSuccessWithMockServerReportsAdCostCpcBillingDisabled()
            throws Exception {
        // Re init with cpc billing enabled
        initClients(false, true, false, false, false);
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        doReturn(false)
                .when(mConsentManagerMock)
                .isFledgeConsentRevokedForAppAfterSettingFledgeUse(any());

        setupAdSelectionConfig();
        String decisionLogicJs = getDecisionLogicWithBeacons();
        String biddingLogicJs = getBiddingLogicWithBeaconsWithAdCost();

        CustomAudience customAudience1 =
                createCustomAudienceWithAdCost(
                        mLocalhostBuyerDomain,
                        CUSTOM_AUDIENCE_SEQ_1,
                        BIDS_FOR_BUYER_1,
                        AD_COST_1.getAdCost());

        CustomAudience customAudience2 =
                createCustomAudienceWithAdCost(
                        mLocalhostBuyerDomain,
                        CUSTOM_AUDIENCE_SEQ_2,
                        BIDS_FOR_BUYER_2,
                        AD_COST_2.getAdCost());

        // We add permits to the semaphores when the MWS is called and remove them in the asserts
        Semaphore impressionReportingSemaphore = new Semaphore(0);
        Semaphore interactionReportingSemaphore = new Semaphore(0);

        MockWebServer server =
                getMockWebServer(
                        decisionLogicJs,
                        biddingLogicJs,
                        impressionReportingSemaphore,
                        interactionReportingSemaphore,
                        false);

        doNothing()
                .when(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), anyBoolean()));

        joinCustomAudienceAndAssertSuccess(customAudience1);
        joinCustomAudienceAndAssertSuccess(customAudience2);

        verify(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), eq(false)), times(2));

        selectAdsAndReport(
                CommonFixture.getUri(
                        mLocalhostBuyerDomain.getAuthority(),
                        AD_URI_PREFIX + CUSTOM_AUDIENCE_SEQ_2 + "/ad3"),
                impressionReportingSemaphore,
                interactionReportingSemaphore);
        verifyStandardServerRequests(server);
    }

    @FlakyTest(bugId = 301009903)
    @Test
    public void testFledgeFlowSuccessWithDevOverridesRegisterAdBeaconEnabled() throws Exception {
        setupConsentGivenStubs();

        setupAdSelectionConfig();
        String decisionLogicJs = getDecisionLogicWithBeacons();
        String biddingLogicJs = getBiddingLogicWithBeacons();

        MockWebServer server =
                mockWebServerRule.startMockWebServer(
                        request -> new MockResponse().setResponseCode(404));

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(CommonFixture.TEST_PACKAGE_NAME)
                                .build());

        doNothing()
                .when(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), anyBoolean()));

        CustomAudience customAudience1 =
                createCustomAudience(
                        mLocalhostBuyerDomain, CUSTOM_AUDIENCE_SEQ_1, BIDS_FOR_BUYER_1);

        CustomAudience customAudience2 =
                createCustomAudience(
                        mLocalhostBuyerDomain, CUSTOM_AUDIENCE_SEQ_2, BIDS_FOR_BUYER_2);

        joinCustomAudienceAndAssertSuccess(customAudience1);
        joinCustomAudienceAndAssertSuccess(customAudience2);

        verify(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), eq(false)), times(2));

        setupOverridesAndAssertSuccess(
                customAudience1, customAudience2, biddingLogicJs, decisionLogicJs);

        // Run Ad Selection
        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(
                        mAdSelectionService, mAdSelectionConfig, CommonFixture.TEST_PACKAGE_NAME);

        assertTrue(resultsCallback.mIsSuccess);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDao.doesAdSelectionIdExist(resultSelectionId));
        assertEquals(
                CommonFixture.getUri(
                        mLocalhostBuyerDomain.getAuthority(),
                        AD_URI_PREFIX + CUSTOM_AUDIENCE_SEQ_2 + "/ad3"),
                resultsCallback.mAdSelectionResponse.getRenderUri());

        reportImpressionAndAssertSuccess(resultsCallback.mAdSelectionResponse.getAdSelectionId());

        reportInteractionAndAssertSuccess(resultsCallback);

        mockWebServerRule.verifyMockServerRequests(
                server, 0, ImmutableList.of(), mRequestMatcherPrefixMatch);
    }

    @Test
    public void testFledgeFlowSuccessWithDevOverridesGaUxEnabled() throws Exception {
        initClients(true, true, false, false, false);
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        doReturn(false).when(mConsentManagerMock).isFledgeConsentRevokedForApp(any());
        doReturn(false)
                .when(mConsentManagerMock)
                .isFledgeConsentRevokedForAppAfterSettingFledgeUse(any());

        setupAdSelectionConfig();
        String decisionLogicJs = getDecisionLogicWithBeacons();
        String biddingLogicJs = getBiddingLogicWithBeacons();

        MockWebServer server =
                mockWebServerRule.startMockWebServer(
                        request -> new MockResponse().setResponseCode(404));

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(CommonFixture.TEST_PACKAGE_NAME)
                                .build());

        doNothing()
                .when(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), anyBoolean()));

        CustomAudience customAudience1 =
                createCustomAudience(
                        mLocalhostBuyerDomain, CUSTOM_AUDIENCE_SEQ_1, BIDS_FOR_BUYER_1);

        CustomAudience customAudience2 =
                createCustomAudience(
                        mLocalhostBuyerDomain, CUSTOM_AUDIENCE_SEQ_2, BIDS_FOR_BUYER_2);

        // Join first custom audience
        joinCustomAudienceAndAssertSuccess(customAudience1);

        // Join second custom audience
        joinCustomAudienceAndAssertSuccess(customAudience2);

        verify(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), eq(false)), times(2));

        setupOverridesAndAssertSuccess(
                customAudience1, customAudience2, biddingLogicJs, decisionLogicJs);

        // Run Ad Selection
        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(
                        mAdSelectionService, mAdSelectionConfig, CommonFixture.TEST_PACKAGE_NAME);

        assertTrue(resultsCallback.mIsSuccess);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDao.doesAdSelectionIdExist(resultSelectionId));
        assertEquals(
                CommonFixture.getUri(
                        mLocalhostBuyerDomain.getAuthority(),
                        AD_URI_PREFIX + CUSTOM_AUDIENCE_SEQ_2 + "/ad3"),
                resultsCallback.mAdSelectionResponse.getRenderUri());

        reportImpressionAndAssertSuccess(resultsCallback.mAdSelectionResponse.getAdSelectionId());

        reportInteractionAndAssertSuccess(resultsCallback);

        mockWebServerRule.verifyMockServerRequests(
                server, 0, ImmutableList.of(), mRequestMatcherPrefixMatch);
    }

    @FlakyTest(bugId = 301016443)
    @Test
    public void testFledgeFlowSuccessWithDevOverridesWithRevokedUserConsentForApp()
            throws Exception {
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        // Allow the first calls to succeed so that we can verify the rest of the flow works
        when(mConsentManagerMock.isFledgeConsentRevokedForApp(any()))
                .thenReturn(false)
                .thenReturn(true);
        when(mConsentManagerMock.isFledgeConsentRevokedForAppAfterSettingFledgeUse(any()))
                .thenReturn(false)
                .thenReturn(true);

        setupAdSelectionConfig();
        String decisionLogicJs = getDecisionLogicWithBeacons();
        String biddingLogicJs = getBiddingLogicWithBeacons();

        MockWebServer server =
                mockWebServerRule.startMockWebServer(
                        request -> new MockResponse().setResponseCode(404));

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(CommonFixture.TEST_PACKAGE_NAME)
                                .build());

        CustomAudience customAudience1 =
                createCustomAudience(
                        mLocalhostBuyerDomain, CUSTOM_AUDIENCE_SEQ_1, BIDS_FOR_BUYER_1);

        CustomAudience customAudience2 =
                createCustomAudience(
                        mLocalhostBuyerDomain, CUSTOM_AUDIENCE_SEQ_2, BIDS_FOR_BUYER_2);

        joinCustomAudienceAndAssertSuccess(customAudience1);
        joinCustomAudienceAndAssertSuccess(customAudience2);

        setupOverridesAndAssertSuccess(
                customAudience1, customAudience2, biddingLogicJs, decisionLogicJs);

        // Run Ad Selection
        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(
                        mAdSelectionService, mAdSelectionConfig, CommonFixture.TEST_PACKAGE_NAME);

        // Verify that CA1/ad2 won because CA2 was not joined due to user consent
        assertTrue(resultsCallback.mIsSuccess);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDao.doesAdSelectionIdExist(resultSelectionId));
        assertEquals(
                CommonFixture.getUri(
                        mLocalhostBuyerDomain.getAuthority(),
                        AD_URI_PREFIX + CUSTOM_AUDIENCE_SEQ_1 + "/ad2"),
                resultsCallback.mAdSelectionResponse.getRenderUri());

        reportImpressionAndAssertSuccess(resultsCallback.mAdSelectionResponse.getAdSelectionId());

        reportInteractionAndAssertSuccess(resultsCallback);

        mockWebServerRule.verifyMockServerRequests(
                server, 0, ImmutableList.of(), mRequestMatcherPrefixMatch);
    }

    @Test
    public void testFledgeFlowSuccessWithDevOverridesWithRevokedUserConsentForAppGaUxEnabled()
            throws Exception {
        initClients(true, true, false, false, false);
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        // Allow the first calls to succeed so that we can verify the rest of the flow works
        when(mConsentManagerMock.isFledgeConsentRevokedForApp(any()))
                .thenReturn(false)
                .thenReturn(true);
        when(mConsentManagerMock.isFledgeConsentRevokedForAppAfterSettingFledgeUse(any()))
                .thenReturn(false)
                .thenReturn(true);

        setupAdSelectionConfig();
        String decisionLogicJs = getDecisionLogicWithBeacons();
        String biddingLogicJs = getBiddingLogicWithBeacons();

        MockWebServer server =
                mockWebServerRule.startMockWebServer(
                        request -> new MockResponse().setResponseCode(404));

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(CommonFixture.TEST_PACKAGE_NAME)
                                .build());

        CustomAudience customAudience1 =
                createCustomAudience(
                        mLocalhostBuyerDomain, CUSTOM_AUDIENCE_SEQ_1, BIDS_FOR_BUYER_1);

        CustomAudience customAudience2 =
                createCustomAudience(
                        mLocalhostBuyerDomain, CUSTOM_AUDIENCE_SEQ_2, BIDS_FOR_BUYER_2);

        joinCustomAudienceAndAssertSuccess(customAudience1);
        joinCustomAudienceAndAssertSuccess(customAudience2);

        setupOverridesAndAssertSuccess(
                customAudience1, customAudience2, biddingLogicJs, decisionLogicJs);

        // Run Ad Selection
        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(
                        mAdSelectionService, mAdSelectionConfig, CommonFixture.TEST_PACKAGE_NAME);

        // Verify that CA1/ad2 won because CA2 was not joined due to user consent
        assertTrue(resultsCallback.mIsSuccess);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDao.doesAdSelectionIdExist(resultSelectionId));
        assertEquals(
                CommonFixture.getUri(
                        mLocalhostBuyerDomain.getAuthority(),
                        AD_URI_PREFIX + CUSTOM_AUDIENCE_SEQ_1 + "/ad2"),
                resultsCallback.mAdSelectionResponse.getRenderUri());

        reportImpressionAndAssertSuccess(resultsCallback.mAdSelectionResponse.getAdSelectionId());

        reportInteractionAndAssertSuccess(resultsCallback);

        mockWebServerRule.verifyMockServerRequests(
                server, 0, ImmutableList.of(), mRequestMatcherPrefixMatch);
    }

    @Test
    public void testFledgeFlowFailsWithMismatchedPackageNamesReportImpression() throws Exception {
        setupConsentGivenStubs();

        String otherPackageName = CommonFixture.TEST_PACKAGE_NAME + "different_package";

        // Mocking PackageManager so it passes package name validation, but fails impression
        // reporting
        // due to package mismatch
        PackageManager packageManagerMock = mock(PackageManager.class);
        when(CONTEXT_SPY.getPackageManager()).thenReturn(packageManagerMock);
        when(packageManagerMock.getPackagesForUid(Process.myUid()))
                .thenReturn(new String[] {CommonFixture.TEST_PACKAGE_NAME, otherPackageName});

        // Reinitializing service so mocking takes effect
        // Create an instance of AdSelection Service with real dependencies
        mAdSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mAdServicesHttpsClient,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT_SPY,
                        mAdServicesLoggerMock,
                        DEFAULT_FLAGS,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterMock,
                        mAdSelectionServiceFilterMock,
                        mAdFilteringFeatureFactory,
                        mConsentManagerMock,
                        mMultiCloudSupportStrategy,
                        mAdSelectionDebugReportDao,
                        mAdIdFetcher,
                        mUnusedKAnonSignJoinFactory,
                        false,
                        mRetryStrategyFactory);

        mAdSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setCustomAudienceBuyers(
                                ImmutableList.of(
                                        AdTechIdentifier.fromString(BUYER_DOMAIN_1.getHost()),
                                        AdTechIdentifier.fromString(BUYER_DOMAIN_2.getHost())))
                        .setSeller(
                                AdTechIdentifier.fromString(
                                        mockWebServerRule
                                                .uriForPath(SELLER_DECISION_LOGIC_URI_PATH)
                                                .getHost()))
                        .setDecisionLogicUri(
                                mockWebServerRule.uriForPath(SELLER_DECISION_LOGIC_URI_PATH))
                        .setTrustedScoringSignalsUri(
                                mockWebServerRule.uriForPath(SELLER_TRUSTED_SIGNAL_URI_PATH))
                        .build();

        AdSelectionConfig adSelectionConfigWithDifferentCallerPackageName =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setCustomAudienceBuyers(
                                ImmutableList.of(
                                        AdTechIdentifier.fromString(BUYER_DOMAIN_1.getHost()),
                                        AdTechIdentifier.fromString(BUYER_DOMAIN_2.getHost())))
                        .setSeller(
                                AdTechIdentifier.fromString(
                                        mockWebServerRule
                                                .uriForPath(SELLER_DECISION_LOGIC_URI_PATH)
                                                .getHost()))
                        .setDecisionLogicUri(
                                mockWebServerRule.uriForPath(SELLER_DECISION_LOGIC_URI_PATH))
                        .setTrustedScoringSignalsUri(
                                mockWebServerRule.uriForPath(SELLER_TRUSTED_SIGNAL_URI_PATH))
                        .build();

        String decisionLogicJs =
                "function scoreAd(ad, bid, auction_config, seller_signals,"
                        + " trusted_scoring_signals, contextual_signal, user_signal,"
                        + " custom_audience_signal) { \n"
                        + "  return {'status': 0, 'score': bid };\n"
                        + "}\n"
                        + "function reportResult(ad_selection_config, render_uri, bid,"
                        + " contextual_signals) { \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + SELLER_REPORTING_PATH
                        + "' } };\n"
                        + "}";
        String biddingLogicJs =
                "function generateBid(ad, auction_signals, per_buyer_signals,"
                    + " trusted_bidding_signals, contextual_signals, custom_audience_signals) { \n"
                    + "  return {'status': 0, 'ad': ad, 'bid': ad.metadata.result };\n"
                    + "}\n"
                    + "function reportWin(ad_selection_signals, per_buyer_signals,"
                    + " signals_for_buyer, contextual_signals, custom_audience_signals) { \n"
                    + " return {'status': 0, 'results': {'reporting_uri': '"
                        + BUYER_REPORTING_PATH
                        + "' } };\n"
                        + "}";

        mockWebServerRule.startMockWebServer(request -> new MockResponse().setResponseCode(404));

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(CommonFixture.TEST_PACKAGE_NAME)
                                .build());

        doNothing()
                .when(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), anyBoolean()));

        CustomAudience customAudience1 = createCustomAudience(BUYER_DOMAIN_1, BIDS_FOR_BUYER_1);

        CustomAudience customAudience2 = createCustomAudience(BUYER_DOMAIN_2, BIDS_FOR_BUYER_2);

        joinCustomAudienceAndAssertSuccess(customAudience1);
        joinCustomAudienceAndAssertSuccess(customAudience2);

        verify(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), eq(false)), times(2));

        setupOverridesAndAssertSuccess(
                customAudience1, customAudience2, biddingLogicJs, decisionLogicJs);

        // Run Ad Selection
        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(
                        mAdSelectionService, mAdSelectionConfig, CommonFixture.TEST_PACKAGE_NAME);

        assertTrue(resultsCallback.mIsSuccess);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDao.doesAdSelectionIdExist(resultSelectionId));
        assertEquals(
                CommonFixture.getUri(BUYER_DOMAIN_2.getHost(), AD_URI_PREFIX + "/ad3"),
                resultsCallback.mAdSelectionResponse.getRenderUri());

        // Run Report Impression with different package name
        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionConfig(adSelectionConfigWithDifferentCallerPackageName)
                        .setAdSelectionId(resultsCallback.mAdSelectionResponse.getAdSelectionId())
                        .setCallerPackageName(otherPackageName)
                        .build();

        ReportImpressionTestCallback reportImpressionTestCallback =
                callReportImpression(mAdSelectionService, input);

        assertFalse(reportImpressionTestCallback.mIsSuccess);
        assertEquals(
                reportImpressionTestCallback.mFledgeErrorResponse.getStatusCode(),
                AdServicesStatusUtils.STATUS_INVALID_ARGUMENT);
        assertEquals(
                reportImpressionTestCallback.mFledgeErrorResponse.getErrorMessage(),
                CALLER_PACKAGE_NAME_MISMATCH);

        // Run Report Interaction, should fail silently due to no registered beacons
        reportInteractionAndAssertSuccess(resultsCallback);
    }

    @Test
    public void testFledgeFlowFailsWithWrongPackageNameReportInteraction() throws Exception {
        setupConsentGivenStubs();

        String otherPackageName = CommonFixture.TEST_PACKAGE_NAME + "different_package";

        // Mocking PackageManager so it passes package name validation, but fails in interaction
        // reporting
        // due to package mismatch
        PackageManager packageManagerMock = mock(PackageManager.class);
        when(CONTEXT_SPY.getPackageManager()).thenReturn(packageManagerMock);
        when(packageManagerMock.getPackagesForUid(Process.myUid()))
                .thenReturn(new String[] {CommonFixture.TEST_PACKAGE_NAME, otherPackageName});

        // Reinitializing service so mocking takes effect
        // Create an instance of AdSelection Service with real dependencies
        mAdSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mAdServicesHttpsClient,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT_SPY,
                        mAdServicesLoggerMock,
                        DEFAULT_FLAGS,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterMock,
                        mAdSelectionServiceFilterMock,
                        mAdFilteringFeatureFactory,
                        mConsentManagerMock,
                        mMultiCloudSupportStrategy,
                        mAdSelectionDebugReportDao,
                        mAdIdFetcher,
                        mUnusedKAnonSignJoinFactory,
                        false,
                        mRetryStrategyFactory);

        mAdSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setCustomAudienceBuyers(
                                ImmutableList.of(
                                        AdTechIdentifier.fromString(BUYER_DOMAIN_1.getHost()),
                                        AdTechIdentifier.fromString(BUYER_DOMAIN_2.getHost())))
                        .setSeller(
                                AdTechIdentifier.fromString(
                                        mockWebServerRule
                                                .uriForPath(SELLER_DECISION_LOGIC_URI_PATH)
                                                .getHost()))
                        .setDecisionLogicUri(
                                mockWebServerRule.uriForPath(SELLER_DECISION_LOGIC_URI_PATH))
                        .setTrustedScoringSignalsUri(
                                mockWebServerRule.uriForPath(SELLER_TRUSTED_SIGNAL_URI_PATH))
                        .build();
        String decisionLogicJs = getDecisionLogicWithBeacons();
        String biddingLogicJs = getBiddingLogicWithBeacons();

        mockWebServerRule.startMockWebServer(request -> new MockResponse().setResponseCode(404));

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(CommonFixture.TEST_PACKAGE_NAME)
                                .build());

        doNothing()
                .when(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), anyBoolean()));

        CustomAudience customAudience1 = createCustomAudience(BUYER_DOMAIN_1, BIDS_FOR_BUYER_1);

        CustomAudience customAudience2 = createCustomAudience(BUYER_DOMAIN_2, BIDS_FOR_BUYER_2);

        joinCustomAudienceAndAssertSuccess(customAudience1);
        joinCustomAudienceAndAssertSuccess(customAudience2);

        verify(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), eq(false)), times(2));

        setupOverridesAndAssertSuccess(
                customAudience1, customAudience2, biddingLogicJs, decisionLogicJs);

        // Run Ad Selection
        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(
                        mAdSelectionService, mAdSelectionConfig, CommonFixture.TEST_PACKAGE_NAME);

        assertTrue(resultsCallback.mIsSuccess);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDao.doesAdSelectionIdExist(resultSelectionId));
        assertEquals(
                CommonFixture.getUri(BUYER_DOMAIN_2.getHost(), AD_URI_PREFIX + "/ad3"),
                resultsCallback.mAdSelectionResponse.getRenderUri());

        reportImpressionAndAssertSuccess(resultsCallback.mAdSelectionResponse.getAdSelectionId());

        // Run Report Interaction with different package name
        ReportInteractionInput reportInteractionInput =
                new ReportInteractionInput.Builder()
                        .setAdSelectionId(resultsCallback.mAdSelectionResponse.getAdSelectionId())
                        .setInteractionKey(CLICK_INTERACTION)
                        .setInteractionData(INTERACTION_DATA)
                        .setCallerPackageName(otherPackageName)
                        .setReportingDestinations(BUYER_DESTINATION | SELLER_DESTINATION)
                        .build();

        ReportInteractionTestCallback reportInteractionTestCallback =
                callReportInteraction(mAdSelectionService, reportInteractionInput);
        assertFalse(reportInteractionTestCallback.mIsSuccess);

        assertEquals(
                reportInteractionTestCallback.mFledgeErrorResponse.getStatusCode(),
                AdServicesStatusUtils.STATUS_INVALID_ARGUMENT);
        assertEquals(
                reportInteractionTestCallback.mFledgeErrorResponse.getErrorMessage(),
                EventReporter.NO_MATCH_FOUND_IN_AD_SELECTION_DB);
    }

    @Test
    @FlakyTest(bugId = 298130832)
    public void testFledgeFlowSuccessWithOneCAWithNegativeBidsWithDevOverrides() throws Exception {
        setupConsentGivenStubs();

        setupAdSelectionConfig();
        String decisionLogicJs = getDecisionLogicWithBeacons();
        String biddingLogicJs = getBiddingLogicWithBeacons();

        MockWebServer server =
                mockWebServerRule.startMockWebServer(
                        request -> {
                            // With overrides the server should not be called
                            return new MockResponse().setResponseCode(404);
                        });

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(CommonFixture.TEST_PACKAGE_NAME)
                                .build());

        doNothing()
                .when(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), anyBoolean()));

        CustomAudience customAudience1 =
                createCustomAudience(
                        mLocalhostBuyerDomain, CUSTOM_AUDIENCE_SEQ_1, BIDS_FOR_BUYER_1);

        CustomAudience customAudience2 =
                createCustomAudience(mLocalhostBuyerDomain, CUSTOM_AUDIENCE_SEQ_2, INVALID_BIDS);
        joinCustomAudienceAndAssertSuccess(customAudience1);
        joinCustomAudienceAndAssertSuccess(customAudience2);

        verify(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), eq(false)), times(2));

        setupOverridesAndAssertSuccess(
                customAudience1, customAudience2, biddingLogicJs, decisionLogicJs);

        // Run Ad Selection
        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(
                        mAdSelectionService, mAdSelectionConfig, CommonFixture.TEST_PACKAGE_NAME);

        assertTrue(resultsCallback.mIsSuccess);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDao.doesAdSelectionIdExist(resultSelectionId));
        // Expect that ad from buyer 1 won since buyer 2 had negative bids
        assertEquals(
                CommonFixture.getUri(
                        mLocalhostBuyerDomain.getAuthority(),
                        AD_URI_PREFIX + CUSTOM_AUDIENCE_SEQ_1 + "/ad2"),
                resultsCallback.mAdSelectionResponse.getRenderUri());

        reportImpressionAndAssertSuccess(resultsCallback.mAdSelectionResponse.getAdSelectionId());
        reportInteractionAndAssertSuccess(resultsCallback);

        mockWebServerRule.verifyMockServerRequests(
                server, 0, ImmutableList.of(), mRequestMatcherPrefixMatch);
    }

    @Test
    public void testFledgeFlowSuccessWithOneCAWithNegativeBidsWithDevOverrides_v3BiddingLogic()
            throws Exception {
        setupConsentGivenStubs();

        setupAdSelectionConfig();
        String decisionLogicJs = getDecisionLogicWithBeacons();
        String biddingLogicJs =
                "function generateBid(custom_audience, auction_signals, per_buyer_signals,\n"
                        + "    trusted_bidding_signals, contextual_signals) {\n"
                        + "    const ads = custom_audience.ads;\n"
                        + "    let result = null;\n"
                        + "    for (const ad of ads) {\n"
                        + "        if (!result || ad.metadata.result > result.metadata.result) {\n"
                        + "            result = ad;\n"
                        + "        }\n"
                        + "    }\n"
                        + "    return { 'status': 0, 'ad': result, 'bid': result.metadata.result, "
                        + "'render': result.render_uri };\n"
                        + "}\n"
                        + "function reportWin(ad_selection_signals, per_buyer_signals,"
                        + " signals_for_buyer ,contextual_signals, custom_audience_signals) {\n"
                        + "const beacons = {'click': '"
                        + mockWebServerRule.uriForPath(CLICK_BUYER_PATH)
                        + "', 'hover': '"
                        + mockWebServerRule.uriForPath(HOVER_BUYER_PATH)
                        + "'};\n"
                        + "registerAdBeacon(beacons);"
                        + " return {'status': 0, 'results': {'reporting_uri': '"
                        + mockWebServerRule.uriForPath(BUYER_REPORTING_PATH)
                        + "' } };\n"
                        + "}";

        MockWebServer server =
                mockWebServerRule.startMockWebServer(
                        request -> {
                            // With overrides the server should not be called
                            return new MockResponse().setResponseCode(404);
                        });

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(CommonFixture.TEST_PACKAGE_NAME)
                                .build());

        doNothing()
                .when(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), anyBoolean()));

        CustomAudience customAudience1 =
                createCustomAudience(
                        mLocalhostBuyerDomain, CUSTOM_AUDIENCE_SEQ_1, BIDS_FOR_BUYER_1);

        CustomAudience customAudience2 =
                createCustomAudience(mLocalhostBuyerDomain, CUSTOM_AUDIENCE_SEQ_2, INVALID_BIDS);
        joinCustomAudienceAndAssertSuccess(customAudience1);
        joinCustomAudienceAndAssertSuccess(customAudience2);

        verify(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), eq(false)), times(2));

        // Add AdSelection Override
        AdSelectionOverrideTestCallback adSelectionOverrideTestCallback =
                callAddAdSelectionOverride(
                        mAdSelectionService,
                        mAdSelectionConfig,
                        decisionLogicJs,
                        TRUSTED_SCORING_SIGNALS,
                        BUYERS_DECISION_LOGIC);

        assertTrue(adSelectionOverrideTestCallback.mIsSuccess);

        // Add Custom Audience Overrides
        CustomAudienceOverrideTestCallback customAudienceOverrideTestCallback1 =
                callAddCustomAudienceOverride(
                        CommonFixture.TEST_PACKAGE_NAME,
                        customAudience1.getBuyer(),
                        customAudience1.getName(),
                        biddingLogicJs,
                        JsVersionRegister.BUYER_BIDDING_LOGIC_VERSION_VERSION_3,
                        TRUSTED_BIDDING_SIGNALS,
                        mCustomAudienceService);

        assertTrue(customAudienceOverrideTestCallback1.mIsSuccess);

        CustomAudienceOverrideTestCallback customAudienceOverrideTestCallback2 =
                callAddCustomAudienceOverride(
                        CommonFixture.TEST_PACKAGE_NAME,
                        customAudience2.getBuyer(),
                        customAudience2.getName(),
                        biddingLogicJs,
                        JsVersionRegister.BUYER_BIDDING_LOGIC_VERSION_VERSION_3,
                        TRUSTED_BIDDING_SIGNALS,
                        mCustomAudienceService);

        assertTrue(customAudienceOverrideTestCallback2.mIsSuccess);

        // Run Ad Selection
        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(
                        mAdSelectionService, mAdSelectionConfig, CommonFixture.TEST_PACKAGE_NAME);

        assertTrue(resultsCallback.mIsSuccess);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDao.doesAdSelectionIdExist(resultSelectionId));
        // Expect that ad from buyer 1 won since buyer 2 had negative bids
        assertEquals(
                CommonFixture.getUri(
                        mLocalhostBuyerDomain.getAuthority(),
                        AD_URI_PREFIX + CUSTOM_AUDIENCE_SEQ_1 + "/ad2"),
                resultsCallback.mAdSelectionResponse.getRenderUri());

        reportImpressionAndAssertSuccess(resultsCallback.mAdSelectionResponse.getAdSelectionId());

        reportInteractionAndAssertSuccess(resultsCallback);

        mockWebServerRule.verifyMockServerRequests(
                server, 0, ImmutableList.of(), mRequestMatcherPrefixMatch);
    }

    @Test
    public void testFledgeFlowFailsWithBothCANegativeBidsWithDevOverrides() throws Exception {
        setupConsentGivenStubs();

        mAdSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setCustomAudienceBuyers(
                                ImmutableList.of(
                                        AdTechIdentifier.fromString(BUYER_DOMAIN_1.getHost()),
                                        AdTechIdentifier.fromString(BUYER_DOMAIN_2.getHost())))
                        .setSeller(
                                AdTechIdentifier.fromString(
                                        mockWebServerRule
                                                .uriForPath(SELLER_DECISION_LOGIC_URI_PATH)
                                                .getHost()))
                        .setDecisionLogicUri(
                                mockWebServerRule.uriForPath(SELLER_DECISION_LOGIC_URI_PATH))
                        .setTrustedScoringSignalsUri(
                                mockWebServerRule.uriForPath(SELLER_TRUSTED_SIGNAL_URI_PATH))
                        .build();

        String decisionLogicJs =
                "function scoreAd(ad, bid, auction_config, seller_signals,"
                        + " trusted_scoring_signals, contextual_signal, user_signal,"
                        + " custom_audience_signal) { \n"
                        + "  return {'status': 0, 'score': bid };\n"
                        + "}\n"
                        + "function reportResult(ad_selection_config, render_uri, bid,"
                        + " contextual_signals) { \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + SELLER_REPORTING_PATH
                        + "' } };\n"
                        + "}";
        String biddingLogicJs =
                "function generateBid(ad, auction_signals, per_buyer_signals,"
                    + " trusted_bidding_signals, contextual_signals, custom_audience_signals) { \n"
                    + "  return {'status': 0, 'ad': ad, 'bid': ad.metadata.result };\n"
                    + "}\n"
                    + "function reportWin(ad_selection_signals, per_buyer_signals,"
                    + " signals_for_buyer, contextual_signals, custom_audience_signals) { \n"
                    + " return {'status': 0, 'results': {'reporting_uri': '"
                        + BUYER_REPORTING_PATH
                        + "' } };\n"
                        + "}";

        MockWebServer server =
                mockWebServerRule.startMockWebServer(
                        request -> {
                            // with overrides the server should not be invoked
                            return new MockResponse().setResponseCode(404);
                        });

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(CommonFixture.TEST_PACKAGE_NAME)
                                .build());

        CustomAudience customAudience1 = createCustomAudience(BUYER_DOMAIN_1, INVALID_BIDS);

        CustomAudience customAudience2 = createCustomAudience(BUYER_DOMAIN_2, INVALID_BIDS);

        joinCustomAudienceAndAssertSuccess(customAudience1);
        joinCustomAudienceAndAssertSuccess(customAudience2);

        // Add AdSelection Override
        AdSelectionOverrideTestCallback adSelectionOverrideTestCallback =
                callAddAdSelectionOverride(
                        mAdSelectionService,
                        mAdSelectionConfig,
                        decisionLogicJs,
                        TRUSTED_SCORING_SIGNALS,
                        BUYERS_DECISION_LOGIC);

        assertTrue(adSelectionOverrideTestCallback.mIsSuccess);

        // Add Custom Audience Overrides
        CustomAudienceOverrideTestCallback customAudienceOverrideTestCallback1 =
                callAddCustomAudienceOverride(
                        CommonFixture.TEST_PACKAGE_NAME,
                        customAudience1.getBuyer(),
                        customAudience1.getName(),
                        biddingLogicJs,
                        null,
                        AdSelectionSignals.EMPTY,
                        mCustomAudienceService);

        assertTrue(customAudienceOverrideTestCallback1.mIsSuccess);

        CustomAudienceOverrideTestCallback customAudienceOverrideTestCallback2 =
                callAddCustomAudienceOverride(
                        CommonFixture.TEST_PACKAGE_NAME,
                        customAudience2.getBuyer(),
                        customAudience2.getName(),
                        biddingLogicJs,
                        null,
                        AdSelectionSignals.EMPTY,
                        mCustomAudienceService);

        assertTrue(customAudienceOverrideTestCallback2.mIsSuccess);

        // Run Ad Selection
        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(
                        mAdSelectionService, mAdSelectionConfig, CommonFixture.TEST_PACKAGE_NAME);

        // Assert that ad selection fails since both Custom Audiences have invalid bids
        assertFalse(resultsCallback.mIsSuccess);
        assertNull(resultsCallback.mAdSelectionResponse);

        // Run Report Impression with random ad selection id
        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionConfig(mAdSelectionConfig)
                        .setAdSelectionId(1)
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        ReportImpressionTestCallback reportImpressionTestCallback =
                callReportImpression(mAdSelectionService, input);

        assertFalse(reportImpressionTestCallback.mIsSuccess);

        // Run Report Interaction with random ad selection id
        ReportInteractionInput reportInteractionInput =
                new ReportInteractionInput.Builder()
                        .setAdSelectionId(1)
                        .setInteractionKey(CLICK_INTERACTION)
                        .setInteractionData(INTERACTION_DATA)
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .setReportingDestinations(BUYER_DESTINATION | SELLER_DESTINATION)
                        .build();

        ReportInteractionTestCallback reportInteractionTestCallback =
                callReportInteraction(mAdSelectionService, reportInteractionInput);
        assertFalse(reportInteractionTestCallback.mIsSuccess);

        mockWebServerRule.verifyMockServerRequests(
                server, 0, Collections.emptyList(), mRequestMatcherPrefixMatch);
    }

    @Test
    public void testFledgeFlowSuccessWithMockServer_preV3BiddingLogic() throws Exception {
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        doReturn(false)
                .when(mConsentManagerMock)
                .isFledgeConsentRevokedForAppAfterSettingFledgeUse(any());

        setupAdSelectionConfig();
        String decisionLogicJs = getDecisionLogicWithBeacons();
        String biddingLogicJs = getBiddingLogicWithBeacons();

        CustomAudience customAudience1 =
                createCustomAudience(
                        mLocalhostBuyerDomain, CUSTOM_AUDIENCE_SEQ_1, BIDS_FOR_BUYER_1);

        CustomAudience customAudience2 =
                createCustomAudience(
                        mLocalhostBuyerDomain, CUSTOM_AUDIENCE_SEQ_2, BIDS_FOR_BUYER_2);

        // We add permits to the semaphores when the MWS is called and remove them in the asserts
        Semaphore impressionReportingSemaphore = new Semaphore(0);
        Semaphore interactionReportingSemaphore = new Semaphore(0);

        MockWebServer server =
                getMockWebServer(
                        decisionLogicJs,
                        biddingLogicJs,
                        impressionReportingSemaphore,
                        interactionReportingSemaphore,
                        false);

        doNothing()
                .when(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), anyBoolean()));

        joinCustomAudienceAndAssertSuccess(customAudience1);
        joinCustomAudienceAndAssertSuccess(customAudience2);

        verify(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), eq(false)), times(2));

        selectAdsAndReport(
                CommonFixture.getUri(
                        mLocalhostBuyerDomain.getAuthority(),
                        AD_URI_PREFIX + CUSTOM_AUDIENCE_SEQ_2 + "/ad3"),
                impressionReportingSemaphore,
                interactionReportingSemaphore);
        verifyStandardServerRequests(server);
    }

    @Test
    public void testFledgeFlowSuccessWithMockServer_v3BiddingLogic() throws Exception {
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        doReturn(false)
                .when(mConsentManagerMock)
                .isFledgeConsentRevokedForAppAfterSettingFledgeUse(any());

        setupAdSelectionConfig();

        CustomAudience customAudience1 =
                createCustomAudience(
                        mLocalhostBuyerDomain, CUSTOM_AUDIENCE_SEQ_1, BIDS_FOR_BUYER_1);

        CustomAudience customAudience2 =
                createCustomAudience(
                        mLocalhostBuyerDomain, CUSTOM_AUDIENCE_SEQ_2, BIDS_FOR_BUYER_2);

        // We add permits to the semaphores when the MWS is called and remove them in the asserts
        Semaphore impressionReportingSemaphore = new Semaphore(0);
        Semaphore interactionReportingSemaphore = new Semaphore(0);

        MockWebServer server =
                getMockWebServer(
                        getDecisionLogicWithBeacons(),
                        getV3BiddingLogicJs(),
                        impressionReportingSemaphore,
                        interactionReportingSemaphore,
                        true);

        doNothing()
                .when(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), anyBoolean()));

        joinCustomAudienceAndAssertSuccess(customAudience1);
        joinCustomAudienceAndAssertSuccess(customAudience2);

        verify(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), eq(false)), times(2));

        // Run Ad Selection
        selectAdsAndReport(
                CommonFixture.getUri(
                        mLocalhostBuyerDomain.getAuthority(),
                        AD_URI_PREFIX + CUSTOM_AUDIENCE_SEQ_2 + "/ad3"),
                impressionReportingSemaphore,
                interactionReportingSemaphore);
        verifyStandardServerRequests(server);
    }

    @Test
    public void testFledgeFlowSuccessWithDebugReportingSentImmediately() throws Exception {
        initClients(false, false, true, false, false, true, false, true, false, false);
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        setupAdSelectionConfig();
        joinCustomAudienceAndAssertSuccess(
                createCustomAudience(
                        mLocalhostBuyerDomain, CUSTOM_AUDIENCE_SEQ_1, BIDS_FOR_BUYER_1));
        joinCustomAudienceAndAssertSuccess(
                createCustomAudience(
                        mLocalhostBuyerDomain, CUSTOM_AUDIENCE_SEQ_2, BIDS_FOR_BUYER_2));
        CountDownLatch debugReportingLatch = new CountDownLatch(4);
        MockWebServer server =
                getMockWebServer(
                        getDecisionLogicJsWithDebugReporting(
                                mLocalhostBuyerDomain
                                                .buildUpon()
                                                .path(SELLER_DEBUG_REPORT_WIN_PATH)
                                                .build()
                                        + DEBUG_REPORT_WINNING_BID_PARAM,
                                mLocalhostBuyerDomain
                                                .buildUpon()
                                                .path(SELLER_DEBUG_REPORT_LOSS_PATH)
                                                .build()
                                        + DEBUG_REPORT_WINNING_BID_PARAM),
                        getBiddingLogicWithDebugReporting(
                                mLocalhostBuyerDomain
                                                .buildUpon()
                                                .path(BUYER_DEBUG_REPORT_WIN_PATH)
                                                .build()
                                        + DEBUG_REPORT_WINNING_BID_PARAM,
                                mLocalhostBuyerDomain
                                                .buildUpon()
                                                .path(BUYER_DEBUG_REPORT_LOSS_PATH)
                                                .build()
                                        + DEBUG_REPORT_WINNING_BID_PARAM),
                        /* debugReportingLatch= */ debugReportingLatch);
        doNothing()
                .when(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), anyBoolean()));
        mMockAdIdWorker.setResult(MockAdIdWorker.MOCK_AD_ID, false);

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(
                        mAdSelectionService, mAdSelectionConfig, CommonFixture.TEST_PACKAGE_NAME);

        assertTrue(resultsCallback.mIsSuccess);
        debugReportingLatch.await(10, TimeUnit.SECONDS);
        mockWebServerRule.verifyMockServerRequests(
                server,
                9,
                ImmutableList.of(
                        SELLER_DECISION_LOGIC_URI_PATH,
                        BUYER_BIDDING_LOGIC_URI_PATH + CUSTOM_AUDIENCE_SEQ_1,
                        BUYER_BIDDING_LOGIC_URI_PATH + CUSTOM_AUDIENCE_SEQ_2,
                        BUYER_TRUSTED_SIGNAL_URI_PATH,
                        SELLER_TRUSTED_SIGNAL_URI_PATH + SELLER_TRUSTED_SIGNAL_PARAMS,
                        BUYER_DEBUG_REPORT_WIN_PATH + DEBUG_REPORT_WINNING_BID_RESPONSE,
                        BUYER_DEBUG_REPORT_LOSS_PATH + DEBUG_REPORT_WINNING_BID_RESPONSE,
                        SELLER_DEBUG_REPORT_WIN_PATH + DEBUG_REPORT_WINNING_BID_RESPONSE,
                        SELLER_DEBUG_REPORT_LOSS_PATH + DEBUG_REPORT_WINNING_BID_RESPONSE),
                mRequestMatcherPrefixMatch);
    }

    @Test
    public void testFledgeFlowSuccessWithDebugReportingSentInBatch() throws Exception {
        initClients(false, false, true, false, false, true, false, false, false, false);
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        setupAdSelectionConfig();
        joinCustomAudienceAndAssertSuccess(
                createCustomAudience(
                        mLocalhostBuyerDomain, CUSTOM_AUDIENCE_SEQ_1, BIDS_FOR_BUYER_1));
        joinCustomAudienceAndAssertSuccess(
                createCustomAudience(
                        mLocalhostBuyerDomain, CUSTOM_AUDIENCE_SEQ_2, BIDS_FOR_BUYER_2));
        MockWebServer server =
                getMockWebServer(
                        getDecisionLogicJsWithDebugReporting(
                                mLocalhostBuyerDomain
                                                .buildUpon()
                                                .path(SELLER_DEBUG_REPORT_WIN_PATH)
                                                .build()
                                        + DEBUG_REPORT_WINNING_BID_PARAM,
                                mLocalhostBuyerDomain
                                                .buildUpon()
                                                .path(SELLER_DEBUG_REPORT_LOSS_PATH)
                                                .build()
                                        + DEBUG_REPORT_WINNING_BID_PARAM),
                        getBiddingLogicWithDebugReporting(
                                mLocalhostBuyerDomain
                                                .buildUpon()
                                                .path(BUYER_DEBUG_REPORT_WIN_PATH)
                                                .build()
                                        + DEBUG_REPORT_WINNING_BID_PARAM,
                                mLocalhostBuyerDomain
                                                .buildUpon()
                                                .path(BUYER_DEBUG_REPORT_LOSS_PATH)
                                                .build()
                                        + DEBUG_REPORT_WINNING_BID_PARAM),
                        /* debugReportingLatch= */ null);
        doNothing()
                .when(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), anyBoolean()));
        doNothing().when(() -> DebugReportSenderJobService.scheduleIfNeeded(any(), anyBoolean()));
        mMockAdIdWorker.setResult(MockAdIdWorker.MOCK_AD_ID, false);

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelectionAndWaitForFullCallback(
                        mAdSelectionService, mAdSelectionConfig, CommonFixture.TEST_PACKAGE_NAME);

        assertTrue(resultsCallback.mIsSuccess);
        mockWebServerRule.verifyMockServerRequests(
                server,
                5,
                ImmutableList.of(
                        SELLER_DECISION_LOGIC_URI_PATH,
                        BUYER_BIDDING_LOGIC_URI_PATH + CUSTOM_AUDIENCE_SEQ_1,
                        BUYER_BIDDING_LOGIC_URI_PATH + CUSTOM_AUDIENCE_SEQ_2,
                        BUYER_TRUSTED_SIGNAL_URI_PATH,
                        SELLER_TRUSTED_SIGNAL_URI_PATH + SELLER_TRUSTED_SIGNAL_PARAMS),
                mRequestMatcherPrefixMatch);
        List<DBAdSelectionDebugReport> debugReports =
                mAdSelectionDebugReportDao.getDebugReportsBeforeTime(
                        CommonFixture.FIXED_NEXT_ONE_DAY, 1000);
        assertNotNull(debugReports);
        assertFalse(debugReports.isEmpty());
        assertEquals(4, debugReports.size());
        String buyerReportWinUrl =
                mLocalhostBuyerDomain.buildUpon().path(BUYER_DEBUG_REPORT_WIN_PATH).build()
                        + DEBUG_REPORT_WINNING_BID_RESPONSE;
        String buyerReportLossUrl =
                mLocalhostBuyerDomain.buildUpon().path(BUYER_DEBUG_REPORT_LOSS_PATH).build()
                        + DEBUG_REPORT_WINNING_BID_RESPONSE;
        String sellerReportWinUrl =
                mLocalhostBuyerDomain.buildUpon().path(SELLER_DEBUG_REPORT_WIN_PATH).build()
                        + DEBUG_REPORT_WINNING_BID_RESPONSE;
        String sellerReportLossUrl =
                mLocalhostBuyerDomain.buildUpon().path(SELLER_DEBUG_REPORT_LOSS_PATH).build()
                        + DEBUG_REPORT_WINNING_BID_RESPONSE;
        Set<String> expectedDebugUris =
                ImmutableSet.of(
                        buyerReportWinUrl,
                        buyerReportLossUrl,
                        sellerReportWinUrl,
                        sellerReportLossUrl);
        Set<String> actualDebugUris =
                debugReports.stream()
                        .map(
                                debugReport -> {
                                    return debugReport.getDebugReportUri().toString();
                                })
                        .collect(Collectors.toSet());
        assertEquals(expectedDebugUris, actualDebugUris);
        verify(
                () -> DebugReportSenderJobService.scheduleIfNeeded(any(Context.class), eq(false)),
                times(1));
    }

    @Test
    public void testFledgeFlowSuccessWithDebugReportingDisabledWhenLatEnabled() throws Exception {
        initClients(false, false, true, false, false, true, false, false, false, false);
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        setupAdSelectionConfig();
        joinCustomAudienceAndAssertSuccess(
                createCustomAudience(
                        mLocalhostBuyerDomain, CUSTOM_AUDIENCE_SEQ_1, BIDS_FOR_BUYER_1));
        joinCustomAudienceAndAssertSuccess(
                createCustomAudience(
                        mLocalhostBuyerDomain, CUSTOM_AUDIENCE_SEQ_2, BIDS_FOR_BUYER_2));
        MockWebServer server =
                getMockWebServer(
                        getDecisionLogicJsWithDebugReporting(
                                mLocalhostBuyerDomain
                                                .buildUpon()
                                                .path(SELLER_DEBUG_REPORT_WIN_PATH)
                                                .build()
                                        + DEBUG_REPORT_WINNING_BID_PARAM,
                                mLocalhostBuyerDomain
                                                .buildUpon()
                                                .path(SELLER_DEBUG_REPORT_LOSS_PATH)
                                                .build()
                                        + DEBUG_REPORT_WINNING_BID_PARAM),
                        getBiddingLogicWithDebugReporting(
                                mLocalhostBuyerDomain
                                                .buildUpon()
                                                .path(BUYER_DEBUG_REPORT_WIN_PATH)
                                                .build()
                                        + DEBUG_REPORT_WINNING_BID_PARAM,
                                mLocalhostBuyerDomain
                                                .buildUpon()
                                                .path(BUYER_DEBUG_REPORT_LOSS_PATH)
                                                .build()
                                        + DEBUG_REPORT_WINNING_BID_PARAM),
                        /* debugReportingLatch= */ null);
        doNothing()
                .when(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), anyBoolean()));
        doNothing().when(() -> DebugReportSenderJobService.scheduleIfNeeded(any(), anyBoolean()));
        mMockAdIdWorker.setResult(MockAdIdWorker.MOCK_AD_ID, true);

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelectionAndWaitForFullCallback(
                        mAdSelectionService, mAdSelectionConfig, CommonFixture.TEST_PACKAGE_NAME);

        assertTrue(resultsCallback.mIsSuccess);
        mockWebServerRule.verifyMockServerRequests(
                server,
                5,
                ImmutableList.of(
                        SELLER_DECISION_LOGIC_URI_PATH,
                        BUYER_BIDDING_LOGIC_URI_PATH + CUSTOM_AUDIENCE_SEQ_1,
                        BUYER_BIDDING_LOGIC_URI_PATH + CUSTOM_AUDIENCE_SEQ_2,
                        BUYER_TRUSTED_SIGNAL_URI_PATH,
                        SELLER_TRUSTED_SIGNAL_URI_PATH + SELLER_TRUSTED_SIGNAL_PARAMS),
                mRequestMatcherPrefixMatch);
        List<DBAdSelectionDebugReport> debugReports =
                mAdSelectionDebugReportDao.getDebugReportsBeforeTime(
                        CommonFixture.FIXED_NEXT_ONE_DAY, 1000);
        assertNotNull(debugReports);
        assertTrue(debugReports.isEmpty());
        verify(
                () -> DebugReportSenderJobService.scheduleIfNeeded(any(Context.class), eq(false)),
                never());
    }

    @Test
    public void testFledgeFlowSuccessWithDebugReportingDisabledWhenAdIdServiceDisabled()
            throws Exception {
        initClients(false, false, true, false, false, true, false, false, true, false);
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        setupAdSelectionConfig();
        joinCustomAudienceAndAssertSuccess(
                createCustomAudience(
                        mLocalhostBuyerDomain, CUSTOM_AUDIENCE_SEQ_1, BIDS_FOR_BUYER_1));
        joinCustomAudienceAndAssertSuccess(
                createCustomAudience(
                        mLocalhostBuyerDomain, CUSTOM_AUDIENCE_SEQ_2, BIDS_FOR_BUYER_2));
        MockWebServer server =
                getMockWebServer(
                        getDecisionLogicJsWithDebugReporting(
                                mLocalhostBuyerDomain
                                                .buildUpon()
                                                .path(SELLER_DEBUG_REPORT_WIN_PATH)
                                                .build()
                                        + DEBUG_REPORT_WINNING_BID_PARAM,
                                mLocalhostBuyerDomain
                                                .buildUpon()
                                                .path(SELLER_DEBUG_REPORT_LOSS_PATH)
                                                .build()
                                        + DEBUG_REPORT_WINNING_BID_PARAM),
                        getBiddingLogicWithDebugReporting(
                                mLocalhostBuyerDomain
                                                .buildUpon()
                                                .path(BUYER_DEBUG_REPORT_WIN_PATH)
                                                .build()
                                        + DEBUG_REPORT_WINNING_BID_PARAM,
                                mLocalhostBuyerDomain
                                                .buildUpon()
                                                .path(BUYER_DEBUG_REPORT_LOSS_PATH)
                                                .build()
                                        + DEBUG_REPORT_WINNING_BID_PARAM),
                        /* debugReportingLatch= */ null);
        doNothing()
                .when(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), anyBoolean()));
        doNothing().when(() -> DebugReportSenderJobService.scheduleIfNeeded(any(), anyBoolean()));
        mMockAdIdWorker.setResult(MockAdIdWorker.MOCK_AD_ID, false);

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelectionAndWaitForFullCallback(
                        mAdSelectionService, mAdSelectionConfig, CommonFixture.TEST_PACKAGE_NAME);

        assertTrue(resultsCallback.mIsSuccess);
        mockWebServerRule.verifyMockServerRequests(
                server,
                5,
                ImmutableList.of(
                        SELLER_DECISION_LOGIC_URI_PATH,
                        BUYER_BIDDING_LOGIC_URI_PATH + CUSTOM_AUDIENCE_SEQ_1,
                        BUYER_BIDDING_LOGIC_URI_PATH + CUSTOM_AUDIENCE_SEQ_2,
                        BUYER_TRUSTED_SIGNAL_URI_PATH,
                        SELLER_TRUSTED_SIGNAL_URI_PATH + SELLER_TRUSTED_SIGNAL_PARAMS),
                mRequestMatcherPrefixMatch);
        List<DBAdSelectionDebugReport> debugReports =
                mAdSelectionDebugReportDao.getDebugReportsBeforeTime(
                        CommonFixture.FIXED_NEXT_ONE_DAY, 1000);
        assertNotNull(debugReports);
        assertTrue(debugReports.isEmpty());
        verify(
                () -> DebugReportSenderJobService.scheduleIfNeeded(any(Context.class), eq(false)),
                never());
    }

    @Test
    public void testFledgeFlowSuccessWithMockServer_DoesNotReportToBuyerWhenEnrollmentFails()
            throws Exception {
        initClients(false, true, true, false, false, false, false, false, false, false);
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        doReturn(false)
                .when(mConsentManagerMock)
                .isFledgeConsentRevokedForAppAfterSettingFledgeUse(any());

        setupAdSelectionConfig();

        CustomAudience customAudience1 =
                createCustomAudience(
                        mLocalhostBuyerDomain, CUSTOM_AUDIENCE_SEQ_1, BIDS_FOR_BUYER_1);

        CustomAudience customAudience2 =
                createCustomAudience(
                        mLocalhostBuyerDomain, CUSTOM_AUDIENCE_SEQ_2, BIDS_FOR_BUYER_2);

        // We add permits to the semaphores when the MWS is called and remove them in the asserts
        Semaphore impressionReportingSemaphore = new Semaphore(0);
        Semaphore interactionReportingSemaphore = new Semaphore(0);

        MockWebServer server =
                getMockWebServer(
                        getDecisionLogicWithBeacons(),
                        getV3BiddingLogicJs(),
                        impressionReportingSemaphore,
                        interactionReportingSemaphore,
                        true);

        // Make buyer impression reporting fail enrollment check
        doThrow(new FledgeAuthorizationFilter.AdTechNotAllowedException())
                .when(mFledgeAuthorizationFilterMock)
                .assertAdTechEnrolled(
                        AdTechIdentifier.fromString(
                                mockWebServerRule.uriForPath(BUYER_REPORTING_PATH).getHost()),
                        AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION);

        doThrow(new FledgeAuthorizationFilter.AdTechNotAllowedException())
                .when(mFledgeAuthorizationFilterMock)
                .assertAdTechEnrolled(
                        AdTechIdentifier.fromString(
                                mockWebServerRule.uriForPath(BUYER_REPORTING_PATH).getHost()),
                        AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION);

        doNothing()
                .when(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), anyBoolean()));

        joinCustomAudienceAndAssertSuccess(customAudience1);
        joinCustomAudienceAndAssertSuccess(customAudience2);

        verify(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), eq(false)), times(2));

        // Run Ad Selection
        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(
                        mAdSelectionService, mAdSelectionConfig, CommonFixture.TEST_PACKAGE_NAME);

        assertTrue(resultsCallback.mIsSuccess);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDao.doesAdSelectionIdExist(resultSelectionId));
        assertEquals(
                CommonFixture.getUri(
                        mLocalhostBuyerDomain.getAuthority(),
                        AD_URI_PREFIX + CUSTOM_AUDIENCE_SEQ_2 + "/ad3"),
                resultsCallback.mAdSelectionResponse.getRenderUri());

        reportImpressionAndAssertSuccess(resultSelectionId);
        reportOnlyBuyerInteractionAndAssertSuccess(resultsCallback);

        // Assert only seller impression reporting happened since buyer enrollment check fails
        assertTrue(impressionReportingSemaphore.tryAcquire(1, 10, TimeUnit.SECONDS));

        // Assert buyer interaction reporting did not happen
        assertTrue(interactionReportingSemaphore.tryAcquire(0, 10, TimeUnit.SECONDS));

        assertEquals(
                "Extra calls made to MockWebServer",
                0,
                impressionReportingSemaphore.availablePermits());
        assertEquals(
                "Extra calls made to MockWebServer",
                0,
                interactionReportingSemaphore.availablePermits());

        // Verify 3 less requests than normal since only seller impression reporting happens
        mockWebServerRule.verifyMockServerRequests(
                server,
                7,
                ImmutableList.of(
                        SELLER_DECISION_LOGIC_URI_PATH,
                        BUYER_BIDDING_LOGIC_URI_PATH + CUSTOM_AUDIENCE_SEQ_1,
                        BUYER_BIDDING_LOGIC_URI_PATH + CUSTOM_AUDIENCE_SEQ_2,
                        BUYER_TRUSTED_SIGNAL_URI_PATH,
                        SELLER_TRUSTED_SIGNAL_URI_PATH + SELLER_TRUSTED_SIGNAL_PARAMS),
                mRequestMatcherPrefixMatch);
    }

    @Test
    public void testFledgeFlowSuccessWithMockServer_allFilters() throws Exception {
        testFledgeFlowSuccessAllFilters(false);
    }

    @Test
    public void testFledgeFlowSuccessWithMockServer_allFiltersWithUnifiedTables() throws Exception {
        testFledgeFlowSuccessAllFilters(true);
    }

    @Test
    public void testFledgeFlowSuccessWithMockServer_fetchAndJoinCustomAudienceFlow_noFilters()
            throws Exception {
        initClients(true, true, false, false, false);
        doReturn(LOCALHOST_BUYER)
                .when(mFledgeAuthorizationFilterMock)
                .getAndAssertAdTechFromUriAllowed(any(), any(), any(), anyInt(), anyInt());
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        doReturn(false)
                .when(mConsentManagerMock)
                .isFledgeConsentRevokedForAppAfterSettingFledgeUse(any());

        setupAdSelectionConfig();
        CustomAudience customAudience1 =
                createCustomAudience(
                        mLocalhostBuyerDomain, CUSTOM_AUDIENCE_SEQ_1, BIDS_FOR_BUYER_1);

        // Using the same generic key across all ads in the CA
        List<Set<Integer>> adCounterKeysForCa2 =
                Arrays.asList(
                        Collections.singleton(KeyedFrequencyCapFixture.KEY1),
                        Collections.singleton(KeyedFrequencyCapFixture.KEY1),
                        Collections.singleton(KeyedFrequencyCapFixture.KEY1));
        /* The final ad with the highest bid has both fcap and app install filters, the second ad
         * with the middle bid has only an app install filter and the first ad with the lowest bid
         * in this ca has only a fcap filter.
         */
        List<AdFilters> adFiltersForCa2 =
                Arrays.asList(
                        new AdFilters.Builder()
                                .setFrequencyCapFilters(CLICK_ONCE_PER_DAY_KEY1)
                                .build(),
                        new AdFilters.Builder().setAppInstallFilters(CURRENT_APP_FILTER).build(),
                        new AdFilters.Builder()
                                .setAppInstallFilters(CURRENT_APP_FILTER)
                                .setFrequencyCapFilters(CLICK_ONCE_PER_DAY_KEY1)
                                .build());
        CustomAudience customAudience2 =
                createCustomAudience(
                        mLocalhostBuyerDomain,
                        CUSTOM_AUDIENCE_SEQ_2,
                        BIDS_FOR_BUYER_2,
                        adCounterKeysForCa2,
                        adFiltersForCa2,
                        false);

        // We add permits to the semaphores when the MWS is called and remove them in the asserts
        Semaphore impressionReportingSemaphore = new Semaphore(0);
        Semaphore interactionReportingSemaphore = new Semaphore(0);

        // Prepare the custom audiences as json responses we expect from the server.
        AdDataConversionStrategy adDataConversionStrategy =
                AdDataConversionStrategyFactory.getAdDataConversionStrategy(false, true);
        List<DBAdData> ads1 = new ArrayList<>();
        for (AdData ad : customAudience1.getAds()) {
            ads1.add(
                    new DBAdData.Builder()
                            .setRenderUri(ad.getRenderUri())
                            .setMetadata(ad.getMetadata())
                            .setAdCounterKeys(ad.getAdCounterKeys())
                            .setAdFilters(ad.getAdFilters())
                            .build());
        }
        String customAudience1JsonString =
                CustomAudienceBlobFixture.asJSONObjectString(
                        null,
                        null,
                        customAudience1.getName(),
                        customAudience1.getActivationTime(),
                        customAudience1.getExpirationTime(),
                        customAudience1.getDailyUpdateUri(),
                        customAudience1.getBiddingLogicUri(),
                        customAudience1.getUserBiddingSignals().toString(),
                        DBTrustedBiddingData.fromServiceObject(
                                customAudience1.getTrustedBiddingData()),
                        ads1);
        List<DBAdData> ads2 = new ArrayList<>();
        for (AdData ad : customAudience2.getAds()) {
            ads2.add(
                    new DBAdData.Builder()
                            .setRenderUri(ad.getRenderUri())
                            .setMetadata(ad.getMetadata())
                            .setAdCounterKeys(ad.getAdCounterKeys())
                            .setAdFilters(ad.getAdFilters())
                            .build());
        }
        String customAudience2JsonString =
                CustomAudienceBlobFixture.asJSONObjectString(
                        null,
                        null,
                        customAudience2.getName(),
                        customAudience2.getActivationTime(),
                        customAudience2.getExpirationTime(),
                        customAudience2.getDailyUpdateUri(),
                        customAudience2.getBiddingLogicUri(),
                        customAudience2.getUserBiddingSignals().toString(),
                        DBTrustedBiddingData.fromServiceObject(
                                customAudience2.getTrustedBiddingData()),
                        ads2);
        HashMap<String, String> remoteCustomAudiencesMap = new HashMap<>();
        String customAudience1Id = String.valueOf(customAudience1.hashCode());
        String customAudience2Id = String.valueOf(customAudience2.hashCode());
        remoteCustomAudiencesMap.put(customAudience1Id, customAudience1JsonString);
        remoteCustomAudiencesMap.put(customAudience2Id, customAudience2JsonString);

        MockWebServer server =
                getMockWebServer(
                        remoteCustomAudiencesMap,
                        getDecisionLogicWithBeacons(),
                        getV3BiddingLogicJs(),
                        impressionReportingSemaphore,
                        interactionReportingSemaphore,
                        /* debugReportingLatch= */ null,
                        true);

        // TODO(b/289276159): Schedule background fetch if needed once added to fetchCA.
        // doNothing()
        //      .when(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), anyBoolean()));

        fetchAndJoinCustomAudienceAndAssertSuccess(
                new FetchAndJoinCustomAudienceInput.Builder(
                                CommonFixture.getUri(
                                        mLocalhostBuyerDomain.getAuthority(),
                                        FETCH_CA_PATH + "/" + customAudience1Id),
                                VALID_OWNER)
                        .build());
        fetchAndJoinCustomAudienceAndAssertSuccess(
                new FetchAndJoinCustomAudienceInput.Builder(
                                CommonFixture.getUri(
                                        mLocalhostBuyerDomain.getAuthority(),
                                        FETCH_CA_PATH + "/" + customAudience2Id),
                                VALID_OWNER)
                        .build());

        // TODO(b/289276159): Verify background fetch is scheduled if needed once added to fetchCA.
        // verify(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), eq(false)))

        // Run Ad Selection no filters active
        selectAdsAndReport(
                CommonFixture.getUri(
                        mLocalhostBuyerDomain.getAuthority(),
                        AD_URI_PREFIX + CUSTOM_AUDIENCE_SEQ_2 + "/ad3"),
                impressionReportingSemaphore,
                interactionReportingSemaphore);

        // 2 fetch CA requests and 10 requests for the auction with both CAs
        mockWebServerRule.verifyMockServerRequests(
                server,
                12,
                ImmutableList.of(
                        FETCH_CA_PATH + "/" + customAudience1Id,
                        FETCH_CA_PATH + "/" + customAudience2Id,
                        SELLER_DECISION_LOGIC_URI_PATH,
                        BUYER_BIDDING_LOGIC_URI_PATH + CUSTOM_AUDIENCE_SEQ_1,
                        BUYER_BIDDING_LOGIC_URI_PATH + CUSTOM_AUDIENCE_SEQ_2,
                        BUYER_TRUSTED_SIGNAL_URI_PATH,
                        SELLER_TRUSTED_SIGNAL_URI_PATH + SELLER_TRUSTED_SIGNAL_PARAMS),
                mRequestMatcherPrefixMatch);
    }

    @Test
    public void testFledgeFlowSuccessWithMockServer_fetchAndJoinCustomAudience_appInstallFilters()
            throws Exception {
        initClients(true, true, false, false, false);
        doReturn(LOCALHOST_BUYER)
                .when(mFledgeAuthorizationFilterMock)
                .getAndAssertAdTechFromUriAllowed(any(), any(), any(), anyInt(), anyInt());
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        doReturn(false)
                .when(mConsentManagerMock)
                .isFledgeConsentRevokedForAppAfterSettingFledgeUse(any());

        setupAdSelectionConfig();
        CustomAudience customAudience1 =
                createCustomAudience(
                        mLocalhostBuyerDomain, CUSTOM_AUDIENCE_SEQ_1, BIDS_FOR_BUYER_1);

        // Using the same generic key across all ads in the CA
        List<Set<Integer>> adCounterKeysForCa2 =
                Arrays.asList(
                        Collections.singleton(KeyedFrequencyCapFixture.KEY1),
                        Collections.singleton(KeyedFrequencyCapFixture.KEY1),
                        Collections.singleton(KeyedFrequencyCapFixture.KEY1));
        /* The final ad with the highest bid has both fcap and app install filters, the second ad
         * with the middle bid has only an app install filter and the first ad with the lowest bid
         * in this ca has only a fcap filter.
         */
        List<AdFilters> adFiltersForCa2 =
                Arrays.asList(
                        new AdFilters.Builder()
                                .setFrequencyCapFilters(CLICK_ONCE_PER_DAY_KEY1)
                                .build(),
                        new AdFilters.Builder().setAppInstallFilters(CURRENT_APP_FILTER).build(),
                        new AdFilters.Builder()
                                .setAppInstallFilters(CURRENT_APP_FILTER)
                                .setFrequencyCapFilters(CLICK_ONCE_PER_DAY_KEY1)
                                .build());
        CustomAudience customAudience2 =
                createCustomAudience(
                        mLocalhostBuyerDomain,
                        CUSTOM_AUDIENCE_SEQ_2,
                        BIDS_FOR_BUYER_2,
                        adCounterKeysForCa2,
                        adFiltersForCa2,
                        false);

        // We add permits to the semaphores when the MWS is called and remove them in the asserts
        Semaphore impressionReportingSemaphore = new Semaphore(0);
        Semaphore interactionReportingSemaphore = new Semaphore(0);

        // Prepare the custom audiences as json responses we expect from the server.
        AdDataConversionStrategy adDataConversionStrategy =
                AdDataConversionStrategyFactory.getAdDataConversionStrategy(true, true);
        List<DBAdData> ads1 = new ArrayList<>();
        for (AdData ad : customAudience1.getAds()) {
            ads1.add(
                    new DBAdData.Builder()
                            .setRenderUri(ad.getRenderUri())
                            .setMetadata(ad.getMetadata())
                            .setAdCounterKeys(ad.getAdCounterKeys())
                            .setAdFilters(ad.getAdFilters())
                            .build());
        }
        String customAudience1JsonString =
                CustomAudienceBlobFixture.asJSONObjectString(
                        null,
                        null,
                        customAudience1.getName(),
                        customAudience1.getActivationTime(),
                        customAudience1.getExpirationTime(),
                        customAudience1.getDailyUpdateUri(),
                        customAudience1.getBiddingLogicUri(),
                        customAudience1.getUserBiddingSignals().toString(),
                        DBTrustedBiddingData.fromServiceObject(
                                customAudience1.getTrustedBiddingData()),
                        ads1);
        List<DBAdData> ads2 = new ArrayList<>();
        for (AdData ad : customAudience2.getAds()) {
            ads2.add(
                    new DBAdData.Builder()
                            .setRenderUri(ad.getRenderUri())
                            .setMetadata(ad.getMetadata())
                            .setAdCounterKeys(ad.getAdCounterKeys())
                            .setAdFilters(ad.getAdFilters())
                            .build());
        }
        String customAudience2JsonString =
                CustomAudienceBlobFixture.asJSONObjectString(
                        null,
                        null,
                        customAudience2.getName(),
                        customAudience2.getActivationTime(),
                        customAudience2.getExpirationTime(),
                        customAudience2.getDailyUpdateUri(),
                        customAudience2.getBiddingLogicUri(),
                        customAudience2.getUserBiddingSignals().toString(),
                        DBTrustedBiddingData.fromServiceObject(
                                customAudience2.getTrustedBiddingData()),
                        ads2);
        HashMap<String, String> remoteCustomAudiencesMap = new HashMap<>();
        String customAudience1Id = String.valueOf(customAudience1.hashCode());
        String customAudience2Id = String.valueOf(customAudience2.hashCode());
        remoteCustomAudiencesMap.put(customAudience1Id, customAudience1JsonString);
        remoteCustomAudiencesMap.put(customAudience2Id, customAudience2JsonString);

        MockWebServer server =
                getMockWebServer(
                        remoteCustomAudiencesMap,
                        getDecisionLogicWithBeacons(),
                        getV3BiddingLogicJs(),
                        impressionReportingSemaphore,
                        interactionReportingSemaphore,
                        /* debugReportingLatch= */ null,
                        true);

        // TODO(b/289276159): Schedule background fetch if needed once added to fetchCA.
        // doNothing()
        //      .when(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), anyBoolean()));

        fetchAndJoinCustomAudienceAndAssertSuccess(
                new FetchAndJoinCustomAudienceInput.Builder(
                                CommonFixture.getUri(
                                        mLocalhostBuyerDomain.getAuthority(),
                                        FETCH_CA_PATH + "/" + customAudience1Id),
                                VALID_OWNER)
                        .build());
        fetchAndJoinCustomAudienceAndAssertSuccess(
                new FetchAndJoinCustomAudienceInput.Builder(
                                CommonFixture.getUri(
                                        mLocalhostBuyerDomain.getAuthority(),
                                        FETCH_CA_PATH + "/" + customAudience2Id),
                                VALID_OWNER)
                        .build());

        // TODO(b/289276159): Verify background fetch is scheduled if needed once added to fetchCA.
        // verify(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), eq(false)))

        // Run Ad Selection with app install filtering
        registerForAppInstallFiltering();
        long adSelectionId =
                selectAdsAndReport(
                        CommonFixture.getUri(
                                mLocalhostBuyerDomain.getAuthority(),
                                AD_URI_PREFIX + CUSTOM_AUDIENCE_SEQ_2 + "/ad1"),
                        impressionReportingSemaphore,
                        interactionReportingSemaphore);

        // 2 fetch CA requests and 10 requests for the auctions with both CAs.
        mockWebServerRule.verifyMockServerRequests(
                server,
                12,
                ImmutableList.of(
                        FETCH_CA_PATH + "/" + customAudience1Id,
                        FETCH_CA_PATH + "/" + customAudience2Id,
                        SELLER_DECISION_LOGIC_URI_PATH,
                        BUYER_BIDDING_LOGIC_URI_PATH + CUSTOM_AUDIENCE_SEQ_1,
                        BUYER_BIDDING_LOGIC_URI_PATH + CUSTOM_AUDIENCE_SEQ_2,
                        BUYER_TRUSTED_SIGNAL_URI_PATH,
                        SELLER_TRUSTED_SIGNAL_URI_PATH + SELLER_TRUSTED_SIGNAL_PARAMS),
                mRequestMatcherPrefixMatch);
    }

    @Test
    public void testFledgeFlowSuccessWithMockServer_fetchAndJoinCustomAudienceFlow_bothFilters()
            throws Exception {
        initClients(true, true, false, false, false);
        doReturn(LOCALHOST_BUYER)
                .when(mFledgeAuthorizationFilterMock)
                .getAndAssertAdTechFromUriAllowed(any(), any(), any(), anyInt(), anyInt());
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        doReturn(false)
                .when(mConsentManagerMock)
                .isFledgeConsentRevokedForAppAfterSettingFledgeUse(any());

        setupAdSelectionConfig();
        CustomAudience customAudience1 =
                createCustomAudience(
                        mLocalhostBuyerDomain, CUSTOM_AUDIENCE_SEQ_1, BIDS_FOR_BUYER_1);

        // Using the same generic key across all ads in the CA
        List<Set<Integer>> adCounterKeysForCa2 =
                Arrays.asList(
                        Collections.singleton(KeyedFrequencyCapFixture.KEY1),
                        Collections.singleton(KeyedFrequencyCapFixture.KEY1),
                        Collections.singleton(KeyedFrequencyCapFixture.KEY1));
        /* The final ad with the highest bid has both fcap and app install filters, the second ad
         * with the middle bid has only an app install filter and the first ad with the lowest bid
         * in this ca has only a fcap filter.
         */
        List<AdFilters> adFiltersForCa2 =
                Arrays.asList(
                        new AdFilters.Builder()
                                .setFrequencyCapFilters(CLICK_ONCE_PER_DAY_KEY1)
                                .build(),
                        new AdFilters.Builder().setAppInstallFilters(CURRENT_APP_FILTER).build(),
                        new AdFilters.Builder()
                                .setAppInstallFilters(CURRENT_APP_FILTER)
                                .setFrequencyCapFilters(CLICK_ONCE_PER_DAY_KEY1)
                                .build());
        CustomAudience customAudience2 =
                createCustomAudience(
                        mLocalhostBuyerDomain,
                        CUSTOM_AUDIENCE_SEQ_2,
                        BIDS_FOR_BUYER_2,
                        adCounterKeysForCa2,
                        adFiltersForCa2,
                        false);

        // We add permits to the semaphores when the MWS is called and remove them in the asserts
        Semaphore impressionReportingSemaphore = new Semaphore(0);
        Semaphore interactionReportingSemaphore = new Semaphore(0);

        // Prepare the custom audiences as json responses we expect from the server.
        AdDataConversionStrategy adDataConversionStrategy =
                AdDataConversionStrategyFactory.getAdDataConversionStrategy(true, true);
        List<DBAdData> ads1 = new ArrayList<>();
        for (AdData ad : customAudience1.getAds()) {
            ads1.add(
                    new DBAdData.Builder()
                            .setRenderUri(ad.getRenderUri())
                            .setMetadata(ad.getMetadata())
                            .setAdCounterKeys(ad.getAdCounterKeys())
                            .setAdFilters(ad.getAdFilters())
                            .build());
        }
        String customAudience1JsonString =
                CustomAudienceBlobFixture.asJSONObjectString(
                        null,
                        null,
                        customAudience1.getName(),
                        customAudience1.getActivationTime(),
                        customAudience1.getExpirationTime(),
                        customAudience1.getDailyUpdateUri(),
                        customAudience1.getBiddingLogicUri(),
                        customAudience1.getUserBiddingSignals().toString(),
                        DBTrustedBiddingData.fromServiceObject(
                                customAudience1.getTrustedBiddingData()),
                        ads1);
        List<DBAdData> ads2 = new ArrayList<>();
        for (AdData ad : customAudience2.getAds()) {
            ads2.add(
                    new DBAdData.Builder()
                            .setRenderUri(ad.getRenderUri())
                            .setMetadata(ad.getMetadata())
                            .setAdCounterKeys(ad.getAdCounterKeys())
                            .setAdFilters(ad.getAdFilters())
                            .build());
        }
        String customAudience2JsonString =
                CustomAudienceBlobFixture.asJSONObjectString(
                        null,
                        null,
                        customAudience2.getName(),
                        customAudience2.getActivationTime(),
                        customAudience2.getExpirationTime(),
                        customAudience2.getDailyUpdateUri(),
                        customAudience2.getBiddingLogicUri(),
                        customAudience2.getUserBiddingSignals().toString(),
                        DBTrustedBiddingData.fromServiceObject(
                                customAudience2.getTrustedBiddingData()),
                        ads2);
        HashMap<String, String> remoteCustomAudiencesMap = new HashMap<>();
        String customAudience1Id = String.valueOf(customAudience1.hashCode());
        String customAudience2Id = String.valueOf(customAudience2.hashCode());
        remoteCustomAudiencesMap.put(customAudience1Id, customAudience1JsonString);
        remoteCustomAudiencesMap.put(customAudience2Id, customAudience2JsonString);

        MockWebServer server =
                getMockWebServer(
                        remoteCustomAudiencesMap,
                        getDecisionLogicWithBeacons(),
                        getV3BiddingLogicJs(),
                        impressionReportingSemaphore,
                        interactionReportingSemaphore,
                        /* debugReportingLatch= */ null,
                        true);

        // TODO(b/289276159): Schedule background fetch if needed once added to fetchCA.
        // doNothing()
        //      .when(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), anyBoolean()));

        fetchAndJoinCustomAudienceAndAssertSuccess(
                new FetchAndJoinCustomAudienceInput.Builder(
                                CommonFixture.getUri(
                                        mLocalhostBuyerDomain.getAuthority(),
                                        FETCH_CA_PATH + "/" + customAudience1Id),
                                VALID_OWNER)
                        .build());
        fetchAndJoinCustomAudienceAndAssertSuccess(
                new FetchAndJoinCustomAudienceInput.Builder(
                                CommonFixture.getUri(
                                        mLocalhostBuyerDomain.getAuthority(),
                                        FETCH_CA_PATH + "/" + customAudience2Id),
                                VALID_OWNER)
                        .build());

        // TODO(b/289276159): Verify background fetch is scheduled if needed once added to fetchCA.
        // verify(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), eq(false)))

        // Run Ad Selection with app install filtering
        registerForAppInstallFiltering();
        long adSelectionId =
                selectAdsAndReport(
                        CommonFixture.getUri(
                                mLocalhostBuyerDomain.getAuthority(),
                                AD_URI_PREFIX + CUSTOM_AUDIENCE_SEQ_2 + "/ad1"),
                        impressionReportingSemaphore,
                        interactionReportingSemaphore);

        // Run Ad Selection with both filters
        updateHistogramAndAssertSuccess(adSelectionId, FrequencyCapFilters.AD_EVENT_TYPE_CLICK);
        selectAdsAndReport(
                CommonFixture.getUri(
                        mLocalhostBuyerDomain.getAuthority(),
                        AD_URI_PREFIX + CUSTOM_AUDIENCE_SEQ_1 + "/ad2"),
                impressionReportingSemaphore,
                interactionReportingSemaphore);

        // 2 fetch CA requests, 10 requests for the auction with both CAs and 9 requests for the
        // auctions with one CA.
        mockWebServerRule.verifyMockServerRequests(
                server,
                21,
                ImmutableList.of(
                        FETCH_CA_PATH + "/" + customAudience1Id,
                        FETCH_CA_PATH + "/" + customAudience2Id,
                        SELLER_DECISION_LOGIC_URI_PATH,
                        BUYER_BIDDING_LOGIC_URI_PATH + CUSTOM_AUDIENCE_SEQ_1,
                        BUYER_BIDDING_LOGIC_URI_PATH + CUSTOM_AUDIENCE_SEQ_2,
                        BUYER_TRUSTED_SIGNAL_URI_PATH,
                        SELLER_TRUSTED_SIGNAL_URI_PATH + SELLER_TRUSTED_SIGNAL_PARAMS),
                mRequestMatcherPrefixMatch);
    }

    @Test
    public void testFledgeFlowSuccessWithMockServer_fetchAndJoinCustomAudienceFlow_fcapFilters()
            throws Exception {
        initClients(true, true, false, false, false);
        doReturn(LOCALHOST_BUYER)
                .when(mFledgeAuthorizationFilterMock)
                .getAndAssertAdTechFromUriAllowed(any(), any(), any(), anyInt(), anyInt());
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        doReturn(false)
                .when(mConsentManagerMock)
                .isFledgeConsentRevokedForAppAfterSettingFledgeUse(any());

        setupAdSelectionConfig();
        CustomAudience customAudience1 =
                createCustomAudience(
                        mLocalhostBuyerDomain, CUSTOM_AUDIENCE_SEQ_1, BIDS_FOR_BUYER_1);

        // Using the same generic key across all ads in the CA
        List<Set<Integer>> adCounterKeysForCa2 =
                Arrays.asList(
                        Collections.singleton(KeyedFrequencyCapFixture.KEY1),
                        Collections.singleton(KeyedFrequencyCapFixture.KEY1),
                        Collections.singleton(KeyedFrequencyCapFixture.KEY1));
        /* The final ad with the highest bid has both fcap and app install filters, the second ad
         * with the middle bid has only an app install filter and the first ad with the lowest bid
         * in this ca has only a fcap filter.
         */
        List<AdFilters> adFiltersForCa2 =
                Arrays.asList(
                        new AdFilters.Builder()
                                .setFrequencyCapFilters(CLICK_ONCE_PER_DAY_KEY1)
                                .build(),
                        new AdFilters.Builder().setAppInstallFilters(CURRENT_APP_FILTER).build(),
                        new AdFilters.Builder()
                                .setAppInstallFilters(CURRENT_APP_FILTER)
                                .setFrequencyCapFilters(CLICK_ONCE_PER_DAY_KEY1)
                                .build());
        CustomAudience customAudience2 =
                createCustomAudience(
                        mLocalhostBuyerDomain,
                        CUSTOM_AUDIENCE_SEQ_2,
                        BIDS_FOR_BUYER_2,
                        adCounterKeysForCa2,
                        adFiltersForCa2,
                        false);

        // We add permits to the semaphores when the MWS is called and remove them in the asserts
        Semaphore impressionReportingSemaphore = new Semaphore(0);
        Semaphore interactionReportingSemaphore = new Semaphore(0);

        // Prepare the custom audiences as json responses we expect from the server.
        AdDataConversionStrategy adDataConversionStrategy =
                AdDataConversionStrategyFactory.getAdDataConversionStrategy(true, true);
        List<DBAdData> ads1 = new ArrayList<>();
        for (AdData ad : customAudience1.getAds()) {
            ads1.add(
                    new DBAdData.Builder()
                            .setRenderUri(ad.getRenderUri())
                            .setMetadata(ad.getMetadata())
                            .setAdCounterKeys(ad.getAdCounterKeys())
                            .setAdFilters(ad.getAdFilters())
                            .build());
        }
        String customAudience1JsonString =
                CustomAudienceBlobFixture.asJSONObjectString(
                        null,
                        null,
                        customAudience1.getName(),
                        customAudience1.getActivationTime(),
                        customAudience1.getExpirationTime(),
                        customAudience1.getDailyUpdateUri(),
                        customAudience1.getBiddingLogicUri(),
                        customAudience1.getUserBiddingSignals().toString(),
                        DBTrustedBiddingData.fromServiceObject(
                                customAudience1.getTrustedBiddingData()),
                        ads1);
        List<DBAdData> ads2 = new ArrayList<>();
        for (AdData ad : customAudience2.getAds()) {
            ads2.add(
                    new DBAdData.Builder()
                            .setRenderUri(ad.getRenderUri())
                            .setMetadata(ad.getMetadata())
                            .setAdCounterKeys(ad.getAdCounterKeys())
                            .setAdFilters(ad.getAdFilters())
                            .build());
        }
        String customAudience2JsonString =
                CustomAudienceBlobFixture.asJSONObjectString(
                        null,
                        null,
                        customAudience2.getName(),
                        customAudience2.getActivationTime(),
                        customAudience2.getExpirationTime(),
                        customAudience2.getDailyUpdateUri(),
                        customAudience2.getBiddingLogicUri(),
                        customAudience2.getUserBiddingSignals().toString(),
                        DBTrustedBiddingData.fromServiceObject(
                                customAudience2.getTrustedBiddingData()),
                        ads2);
        HashMap<String, String> remoteCustomAudiencesMap = new HashMap<>();
        String customAudience1Id = String.valueOf(customAudience1.hashCode());
        String customAudience2Id = String.valueOf(customAudience2.hashCode());
        remoteCustomAudiencesMap.put(customAudience1Id, customAudience1JsonString);
        remoteCustomAudiencesMap.put(customAudience2Id, customAudience2JsonString);

        MockWebServer server =
                getMockWebServer(
                        remoteCustomAudiencesMap,
                        getDecisionLogicWithBeacons(),
                        getV3BiddingLogicJs(),
                        impressionReportingSemaphore,
                        interactionReportingSemaphore,
                        /* debugReportingLatch= */ null,
                        true);

        // TODO(b/289276159): Schedule background fetch if needed once added to fetchCA.
        // doNothing()
        //      .when(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), anyBoolean()));

        fetchAndJoinCustomAudienceAndAssertSuccess(
                new FetchAndJoinCustomAudienceInput.Builder(
                                CommonFixture.getUri(
                                        mLocalhostBuyerDomain.getAuthority(),
                                        FETCH_CA_PATH + "/" + customAudience1Id),
                                VALID_OWNER)
                        .build());
        fetchAndJoinCustomAudienceAndAssertSuccess(
                new FetchAndJoinCustomAudienceInput.Builder(
                                CommonFixture.getUri(
                                        mLocalhostBuyerDomain.getAuthority(),
                                        FETCH_CA_PATH + "/" + customAudience2Id),
                                VALID_OWNER)
                        .build());

        // TODO(b/289276159): Verify background fetch is scheduled if needed once added to fetchCA.
        // verify(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), eq(false)))

        // Run Ad Selection no filters active
        long adSelectionId =
                selectAdsAndReport(
                        CommonFixture.getUri(
                                mLocalhostBuyerDomain.getAuthority(),
                                AD_URI_PREFIX + CUSTOM_AUDIENCE_SEQ_2 + "/ad3"),
                        impressionReportingSemaphore,
                        interactionReportingSemaphore);

        // Run Ad Selection with just fcap filtering
        updateHistogramAndAssertSuccess(adSelectionId, FrequencyCapFilters.AD_EVENT_TYPE_CLICK);
        selectAdsAndReport(
                CommonFixture.getUri(
                        mLocalhostBuyerDomain.getAuthority(),
                        AD_URI_PREFIX + CUSTOM_AUDIENCE_SEQ_2 + "/ad2"),
                impressionReportingSemaphore,
                interactionReportingSemaphore);

        // 2 fetch CA requests and 20 requests for the 2 auctions with both CAs.
        mockWebServerRule.verifyMockServerRequests(
                server,
                22,
                ImmutableList.of(
                        FETCH_CA_PATH + "/" + customAudience1Id,
                        FETCH_CA_PATH + "/" + customAudience2Id,
                        SELLER_DECISION_LOGIC_URI_PATH,
                        BUYER_BIDDING_LOGIC_URI_PATH + CUSTOM_AUDIENCE_SEQ_1,
                        BUYER_BIDDING_LOGIC_URI_PATH + CUSTOM_AUDIENCE_SEQ_2,
                        BUYER_TRUSTED_SIGNAL_URI_PATH,
                        SELLER_TRUSTED_SIGNAL_URI_PATH + SELLER_TRUSTED_SIGNAL_PARAMS),
                mRequestMatcherPrefixMatch);
    }

    @Test
    public void testFledgeFlowSuccessWithMockServer_ContextualAdsFlow() throws Exception {
        // Reinitializing service so default flags are used
        // Create an instance of AdSelection Service with real dependencies
        mAdSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mAdServicesHttpsClient,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT_SPY,
                        mAdServicesLoggerMock,
                        DEFAULT_FLAGS,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterMock,
                        mAdSelectionServiceFilterMock,
                        mAdFilteringFeatureFactory,
                        mConsentManagerMock,
                        mMultiCloudSupportStrategy,
                        mAdSelectionDebugReportDao,
                        mAdIdFetcher,
                        mUnusedKAnonSignJoinFactory,
                        false,
                        mRetryStrategyFactory);

        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        doReturn(false)
                .when(mConsentManagerMock)
                .isFledgeConsentRevokedForAppAfterSettingFledgeUse(any());

        Uri sellerReportingUri = mockWebServerRule.uriForPath(SELLER_REPORTING_PATH);
        Uri buyerReportingUri = mockWebServerRule.uriForPath(BUYER_REPORTING_PATH);

        mAdSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        // Setting empty buyers
                        .setCustomAudienceBuyers(ImmutableList.of())
                        .setSeller(
                                AdTechIdentifier.fromString(
                                        mockWebServerRule
                                                .uriForPath(SELLER_DECISION_LOGIC_URI_PATH)
                                                .getHost()))
                        .setDecisionLogicUri(
                                mockWebServerRule.uriForPath(SELLER_DECISION_LOGIC_URI_PATH))
                        .setTrustedScoringSignalsUri(
                                mockWebServerRule.uriForPath(SELLER_TRUSTED_SIGNAL_URI_PATH))
                        .setPerBuyerSignals(
                                ImmutableMap.of(
                                        AdTechIdentifier.fromString(
                                                mLocalhostBuyerDomain.getHost()),
                                        AdSelectionSignals.fromString("{\"buyer_signals\":0}")))
                        .setPerBuyerSignedContextualAds(createContextualAds())
                        .build();

        String decisionLogicJs =
                "function scoreAd(ad, bid, auction_config, seller_signals,"
                        + " trusted_scoring_signals, contextual_signal, user_signal,"
                        + " custom_audience_signal) { \n"
                        + "  return {'status': 0, 'score': bid };\n"
                        + "}\n"
                        + "function reportResult(ad_selection_config, render_uri, bid,"
                        + " contextual_signals) { \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + sellerReportingUri
                        + "' } };\n"
                        + "}";

        String buyerDecisionLogic =
                "function reportWin(ad_selection_signals, per_buyer_signals,"
                        + " signals_for_buyer, contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'results': {'reporting_uri': '"
                        + buyerReportingUri
                        + "' } };\n"
                        + "}";

        CountDownLatch reportingResponseLatch = new CountDownLatch(2);

        MockWebServer server =
                mockWebServerRule.startMockWebServer(
                        request -> {
                            String versionHeaderName =
                                    JsVersionHelper.getVersionHeaderName(
                                            JsVersionHelper.JS_PAYLOAD_TYPE_BUYER_BIDDING_LOGIC_JS);
                            long jsVersion =
                                    JsVersionRegister.BUYER_BIDDING_LOGIC_VERSION_VERSION_3;
                            switch (request.getPath()) {
                                case SELLER_DECISION_LOGIC_URI_PATH:
                                    return new MockResponse().setBody(decisionLogicJs);
                                case BUYER_BIDDING_LOGIC_URI_PATH:
                                    return new MockResponse().setBody(buyerDecisionLogic);
                                case SELLER_REPORTING_PATH: // Intentional fallthrough
                                case BUYER_REPORTING_PATH:
                                    reportingResponseLatch.countDown();
                                    return new MockResponse().setResponseCode(200);
                            }

                            // The seller params vary based on runtime, so we are returning trusted
                            // signals based on correct path prefix
                            if (request.getPath()
                                    .startsWith(
                                            SELLER_TRUSTED_SIGNAL_URI_PATH
                                                    + SELLER_TRUSTED_SIGNAL_PARAMS)) {
                                return new MockResponse()
                                        .setBody(TRUSTED_SCORING_SIGNALS.toString());
                            }
                            if (request.getPath().startsWith(BUYER_TRUSTED_SIGNAL_URI_PATH)) {
                                return new MockResponse()
                                        .setBody(TRUSTED_BIDDING_SIGNALS.toString());
                            }
                            return new MockResponse().setResponseCode(404);
                        });

        doNothing()
                .when(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), anyBoolean()));

        // Run Ad Selection
        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(
                        mAdSelectionService, mAdSelectionConfig, CommonFixture.TEST_PACKAGE_NAME);

        assertTrue(resultsCallback.mIsSuccess);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDao.doesAdSelectionIdExist(resultSelectionId));
        assertEquals(
                AdDataFixture.getValidRenderUriByBuyer(
                        AdTechIdentifier.fromString(
                                mockWebServerRule
                                        .uriForPath(
                                                BUYER_BIDDING_LOGIC_URI_PATH
                                                        + CommonFixture.VALID_BUYER_1)
                                        .getHost()),
                        500),
                resultsCallback.mAdSelectionResponse.getRenderUri());

        // Run Report Impression
        ReportImpressionInput reportImpressioninput =
                new ReportImpressionInput.Builder()
                        .setAdSelectionConfig(mAdSelectionConfig)
                        .setAdSelectionId(resultSelectionId)
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        ReportImpressionTestCallback reportImpressionTestCallback =
                callReportImpression(mAdSelectionService, reportImpressioninput);

        assertTrue(reportImpressionTestCallback.mIsSuccess);
        reportingResponseLatch.await();
        mockWebServerRule.verifyMockServerRequests(
                server,
                6,
                ImmutableList.of(
                        SELLER_DECISION_LOGIC_URI_PATH,
                        BUYER_BIDDING_LOGIC_URI_PATH,
                        BUYER_REPORTING_PATH,
                        SELLER_REPORTING_PATH,
                        SELLER_TRUSTED_SIGNAL_URI_PATH + SELLER_TRUSTED_SIGNAL_PARAMS),
                mRequestMatcherPrefixMatch);
    }

    @Test
    public void testFledgeFlowSuccessWithAppInstallWithMockServer() throws Exception {
        initClients(true, true, false, false, false);
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        doReturn(false)
                .when(mConsentManagerMock)
                .isFledgeConsentRevokedForAppAfterSettingFledgeUse(any());

        setupAdSelectionConfig();
        String decisionLogicJs = getDecisionLogicWithBeacons();
        String biddingLogicJs =
                "function generateBid(ad, auction_signals, per_buyer_signals,"
                        + " trusted_bidding_signals, contextual_signals, custom_audience_signals) "
                        + "{ \n"
                        + "  return {'status': 0, 'ad': ad, 'bid': ad.metadata.result };\n"
                        + "}\n"
                        + "function reportWin(ad_selection_signals, per_buyer_signals,"
                        + " signals_for_buyer ,contextual_signals, custom_audience_signals) {\n"
                        + "const beacons = {'click': '"
                        + mockWebServerRule.uriForPath(CLICK_BUYER_PATH)
                        + "', 'hover': '"
                        + mockWebServerRule.uriForPath(HOVER_BUYER_PATH)
                        + "'};\n"
                        + "registerAdBeacon(beacons);"
                        + " return {'status': 0, 'results': {'reporting_uri': '"
                        + mockWebServerRule.uriForPath(BUYER_REPORTING_PATH)
                        + "' } };\n"
                        + "}";

        CustomAudience customAudience1 =
                createCustomAudience(
                        mLocalhostBuyerDomain, CUSTOM_AUDIENCE_SEQ_1, BIDS_FOR_BUYER_1);

        List<AdFilters> adFiltersForCa2 =
                Arrays.asList(
                        null,
                        null,
                        new AdFilters.Builder().setAppInstallFilters(CURRENT_APP_FILTER).build());
        CustomAudience customAudience2 =
                createCustomAudience(
                        mLocalhostBuyerDomain,
                        CUSTOM_AUDIENCE_SEQ_2,
                        BIDS_FOR_BUYER_2,
                        EMPTY_AD_COUNTER_KEYS_FOR_BUYER_2,
                        adFiltersForCa2,
                        false);

        // We add permits to the semaphores when the MWS is called and remove them in the asserts
        Semaphore impressionReportingSemaphore = new Semaphore(0);
        Semaphore interactionReportingSemaphore = new Semaphore(0);

        MockWebServer server =
                getMockWebServer(
                        decisionLogicJs,
                        biddingLogicJs,
                        impressionReportingSemaphore,
                        interactionReportingSemaphore,
                        false);

        doNothing()
                .when(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), anyBoolean()));

        registerForAppInstallFiltering();

        joinCustomAudienceAndAssertSuccess(customAudience1);
        joinCustomAudienceAndAssertSuccess(customAudience2);

        verify(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), eq(false)), times(2));

        // Run Ad Selection
        selectAdsAndReport(
                CommonFixture.getUri(
                        mLocalhostBuyerDomain.getAuthority(),
                        AD_URI_PREFIX + CUSTOM_AUDIENCE_SEQ_2 + "/ad2"),
                impressionReportingSemaphore,
                interactionReportingSemaphore);
        verifyStandardServerRequests(server);
    }

    @Test
    public void testFledgeFlowSuccessWithAppInstallFlagOffWithMockServer() throws Exception {
        initClients(false, true, false, true, false, false, false, false, false, false);
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        doReturn(false)
                .when(mConsentManagerMock)
                .isFledgeConsentRevokedForAppAfterSettingFledgeUse(any());

        setupAdSelectionConfig();
        String decisionLogicJs = getDecisionLogicWithBeacons();
        String biddingLogicJs =
                "function generateBid(ad, auction_signals, per_buyer_signals,"
                        + " trusted_bidding_signals, contextual_signals, custom_audience_signals) "
                        + "{ \n"
                        + "  return {'status': 0, 'ad': ad, 'bid': ad.metadata.result };\n"
                        + "}\n"
                        + "function reportWin(ad_selection_signals, per_buyer_signals,"
                        + " signals_for_buyer ,contextual_signals, custom_audience_signals) {\n"
                        + "const beacons = {'click': '"
                        + mockWebServerRule.uriForPath(CLICK_BUYER_PATH)
                        + "', 'hover': '"
                        + mockWebServerRule.uriForPath(HOVER_BUYER_PATH)
                        + "'};\n"
                        + "registerAdBeacon(beacons);"
                        + " return {'status': 0, 'results': {'reporting_uri': '"
                        + mockWebServerRule.uriForPath(BUYER_REPORTING_PATH)
                        + "' } };\n"
                        + "}";

        CustomAudience customAudience1 =
                createCustomAudience(
                        mLocalhostBuyerDomain, CUSTOM_AUDIENCE_SEQ_1, BIDS_FOR_BUYER_1);

        List<AdFilters> filtersForCa2 =
                Arrays.asList(
                        null,
                        null,
                        new AdFilters.Builder().setAppInstallFilters(CURRENT_APP_FILTER).build());
        CustomAudience customAudience2 =
                createCustomAudience(
                        mLocalhostBuyerDomain,
                        CUSTOM_AUDIENCE_SEQ_2,
                        BIDS_FOR_BUYER_2,
                        EMPTY_AD_COUNTER_KEYS_FOR_BUYER_2,
                        filtersForCa2,
                        false);

        // We add permits to the semaphores when the MWS is called and remove them in the asserts
        Semaphore impressionReportingSemaphore = new Semaphore(0);
        Semaphore interactionReportingSemaphore = new Semaphore(0);

        MockWebServer server =
                getMockWebServer(
                        decisionLogicJs,
                        biddingLogicJs,
                        impressionReportingSemaphore,
                        interactionReportingSemaphore,
                        false);

        doNothing()
                .when(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), anyBoolean()));

        // Registers the test app for app install filtering
        // Should fail since flag is turned off
        // Everything else should pass
        AppInstallResultCapturingCallback callback = invokeSetAppInstallAdvertisers();
        assertFalse(callback.isSuccess());
        assertTrue(callback.getException() instanceof IllegalStateException);

        joinCustomAudienceAndAssertSuccess(customAudience1);
        joinCustomAudienceAndAssertSuccess(customAudience2);

        verify(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), eq(false)), times(2));

        // CA 2's ad3 should win even though it tried to filter itself
        selectAdsAndReport(
                CommonFixture.getUri(
                        mLocalhostBuyerDomain.getAuthority(),
                        AD_URI_PREFIX + CUSTOM_AUDIENCE_SEQ_2 + "/ad3"),
                impressionReportingSemaphore,
                interactionReportingSemaphore);
        verifyStandardServerRequests(server);
    }

    @Test
    public void testFledgeFlowSuccessWithRevokedUserConsentForApp() throws Exception {
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        // Allow the first join call to succeed so that we can verify the rest of the flow works
        when(mConsentManagerMock.isFledgeConsentRevokedForAppAfterSettingFledgeUse(any()))
                .thenReturn(false)
                .thenReturn(true);

        setupAdSelectionConfig();
        String decisionLogicJs = getDecisionLogicWithBeacons();
        String biddingLogicJs = getBiddingLogicWithBeacons();

        CustomAudience customAudience1 =
                createCustomAudience(
                        mLocalhostBuyerDomain, CUSTOM_AUDIENCE_SEQ_1, BIDS_FOR_BUYER_1);

        CustomAudience customAudience2 =
                createCustomAudience(
                        mLocalhostBuyerDomain, CUSTOM_AUDIENCE_SEQ_2, BIDS_FOR_BUYER_2);

        // We add permits to the semaphores when the MWS is called and remove them in the asserts
        Semaphore impressionReportingSemaphore = new Semaphore(0);
        Semaphore interactionReportingSemaphore = new Semaphore(0);

        MockWebServer server =
                mockWebServerRule.startMockWebServer(
                        request -> {
                            switch (request.getPath()) {
                                case SELLER_DECISION_LOGIC_URI_PATH:
                                    return new MockResponse().setBody(decisionLogicJs);
                                case BUYER_BIDDING_LOGIC_URI_PATH + CUSTOM_AUDIENCE_SEQ_1:
                                    return new MockResponse().setBody(biddingLogicJs);
                                case BUYER_BIDDING_LOGIC_URI_PATH + CUSTOM_AUDIENCE_SEQ_2:
                                    throw new IllegalStateException(
                                            "This should not be called without user consent");
                                case CLICK_SELLER_PATH: // Intentional fallthrough
                                case CLICK_BUYER_PATH:
                                    interactionReportingSemaphore.release();
                                    return new MockResponse().setResponseCode(200);
                                case SELLER_REPORTING_PATH: // Intentional fallthrough
                                case BUYER_REPORTING_PATH:
                                    impressionReportingSemaphore.release();
                                    return new MockResponse().setResponseCode(200);
                            }

                            // The seller params vary based on runtime, so we are returning trusted
                            // signals based on correct path prefix
                            if (request.getPath()
                                    .startsWith(
                                            SELLER_TRUSTED_SIGNAL_URI_PATH
                                                    + SELLER_TRUSTED_SIGNAL_PARAMS)) {
                                return new MockResponse()
                                        .setBody(TRUSTED_SCORING_SIGNALS.toString());
                            }
                            if (request.getPath().startsWith(BUYER_TRUSTED_SIGNAL_URI_PATH)) {
                                return new MockResponse()
                                        .setBody(TRUSTED_BIDDING_SIGNALS.toString());
                            }
                            return new MockResponse().setResponseCode(404);
                        });

        joinCustomAudienceAndAssertSuccess(customAudience1);
        joinCustomAudienceAndAssertSuccess(customAudience2);

        // Run Ad Selection
        selectAdsAndReport(
                CommonFixture.getUri(
                        mLocalhostBuyerDomain.getAuthority(),
                        AD_URI_PREFIX + CUSTOM_AUDIENCE_SEQ_1 + "/ad2"),
                impressionReportingSemaphore,
                interactionReportingSemaphore);
        mockWebServerRule.verifyMockServerRequests(
                server,
                9,
                ImmutableList.of(
                        SELLER_DECISION_LOGIC_URI_PATH,
                        BUYER_BIDDING_LOGIC_URI_PATH + CUSTOM_AUDIENCE_SEQ_1,
                        BUYER_TRUSTED_SIGNAL_URI_PATH,
                        SELLER_TRUSTED_SIGNAL_URI_PATH + SELLER_TRUSTED_SIGNAL_PARAMS),
                mRequestMatcherPrefixMatch);
    }

    @Test
    public void testFledgeFlowSuccessWithRevokedUserConsentForFledge() throws Exception {
        doReturn(AdServicesApiConsent.REVOKED).when(mConsentManagerMock).getConsent();
        doReturn(true)
                .when(mConsentManagerMock)
                .isFledgeConsentRevokedForAppAfterSettingFledgeUse(any());
        doThrow(new FilterException(new ConsentManager.RevokedConsentException()))
                .when(mAdSelectionServiceFilterMock)
                .filterRequest(
                        any(),
                        anyString(),
                        anyBoolean(),
                        anyBoolean(),
                        anyInt(),
                        anyInt(),
                        any(),
                        any());

        setupAdSelectionConfig();

        CustomAudience customAudience1 =
                createCustomAudience(
                        mLocalhostBuyerDomain, CUSTOM_AUDIENCE_SEQ_1, BIDS_FOR_BUYER_1);

        CustomAudience customAudience2 =
                createCustomAudience(
                        mLocalhostBuyerDomain, CUSTOM_AUDIENCE_SEQ_2, BIDS_FOR_BUYER_2);

        MockWebServer server =
                mockWebServerRule.startMockWebServer(
                        request -> {
                            throw new IllegalStateException(
                                    "No calls should be made without user consent");
                        });

        joinCustomAudienceAndAssertSuccess(customAudience1);
        joinCustomAudienceAndAssertSuccess(customAudience2);

        // Run Ad Selection
        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(
                        mAdSelectionService, mAdSelectionConfig, CommonFixture.TEST_PACKAGE_NAME);

        assertTrue(resultsCallback.mIsSuccess);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertFalse(mAdSelectionEntryDao.doesAdSelectionIdExist(resultSelectionId));
        assertEquals(Uri.EMPTY, resultsCallback.mAdSelectionResponse.getRenderUri());

        reportImpressionAndAssertSuccess(resultSelectionId);
        reportInteractionAndAssertSuccess(resultsCallback);

        mockWebServerRule.verifyMockServerRequests(
                server, 0, ImmutableList.of(), mRequestMatcherPrefixMatch);
    }

    @Test
    public void testFledgeFlowSuccessWithOneCAWithNegativeBidsWithMockServer() throws Exception {
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        doReturn(false)
                .when(mConsentManagerMock)
                .isFledgeConsentRevokedForAppAfterSettingFledgeUse(any());

        setupAdSelectionConfig();
        String decisionLogicJs = getDecisionLogicWithBeacons();
        String biddingLogicJs = getBiddingLogicWithBeacons();

        CustomAudience customAudience1 =
                createCustomAudience(
                        mLocalhostBuyerDomain, CUSTOM_AUDIENCE_SEQ_1, BIDS_FOR_BUYER_1);

        CustomAudience customAudience2 =
                createCustomAudience(mLocalhostBuyerDomain, CUSTOM_AUDIENCE_SEQ_2, INVALID_BIDS);

        // We add permits to the semaphores when the MWS is called and remove them in the asserts
        Semaphore impressionReportingSemaphore = new Semaphore(0);
        Semaphore interactionReportingSemaphore = new Semaphore(0);

        MockWebServer server =
                getMockWebServer(
                        decisionLogicJs,
                        biddingLogicJs,
                        impressionReportingSemaphore,
                        interactionReportingSemaphore,
                        false);

        doNothing()
                .when(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), anyBoolean()));

        joinCustomAudienceAndAssertSuccess(customAudience1);
        joinCustomAudienceAndAssertSuccess(customAudience2);

        verify(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), eq(false)), times(2));

        // Run Ad Selection
        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(
                        mAdSelectionService, mAdSelectionConfig, CommonFixture.TEST_PACKAGE_NAME);

        assertTrue(resultsCallback.mIsSuccess);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDao.doesAdSelectionIdExist(resultSelectionId));

        // Expect that ad from buyer 1 won since buyer 2 had negative bids
        assertEquals(
                CommonFixture.getUri(
                        mLocalhostBuyerDomain.getAuthority(),
                        AD_URI_PREFIX + CUSTOM_AUDIENCE_SEQ_1 + "/ad2"),
                resultsCallback.mAdSelectionResponse.getRenderUri());

        reportImpressionAndAssertSuccess(resultsCallback.mAdSelectionResponse.getAdSelectionId());

        reportInteractionAndAssertSuccess(resultsCallback);

        /* We add a permit on every call received by the MockWebServer and remove them in the
         * tryAcquires below. If there are any left over it means that there were extra calls to the
         * mockserver.
         */
        assertTrue(impressionReportingSemaphore.tryAcquire(2, 10, TimeUnit.SECONDS));
        assertTrue(interactionReportingSemaphore.tryAcquire(2, 10, TimeUnit.SECONDS));
        assertEquals(
                "Extra calls made to MockWebServer",
                0,
                impressionReportingSemaphore.availablePermits());
        assertEquals(
                "Extra calls made to MockWebServer",
                0,
                interactionReportingSemaphore.availablePermits());

        mockWebServerRule.verifyMockServerRequests(
                server,
                10,
                ImmutableList.of(
                        SELLER_DECISION_LOGIC_URI_PATH,
                        BUYER_BIDDING_LOGIC_URI_PATH,
                        BUYER_TRUSTED_SIGNAL_URI_PATH,
                        SELLER_TRUSTED_SIGNAL_URI_PATH + SELLER_TRUSTED_SIGNAL_PARAMS),
                mRequestMatcherPrefixMatch);
    }

    @Test
    public void testFledgeFlowFailsWithOnlyCANegativeBidsWithMockServer() throws Exception {
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        doReturn(false)
                .when(mConsentManagerMock)
                .isFledgeConsentRevokedForAppAfterSettingFledgeUse(any());

        setupAdSelectionConfig();
        String decisionLogicJs = getDecisionLogicWithBeacons();
        String biddingLogicJs = getBiddingLogicWithBeacons();

        CustomAudience customAudience1 = createCustomAudience(mLocalhostBuyerDomain, INVALID_BIDS);

        MockWebServer server =
                mockWebServerRule.startMockWebServer(
                        request -> {
                            switch (request.getPath()) {
                                case SELLER_DECISION_LOGIC_URI_PATH:
                                    return new MockResponse().setBody(decisionLogicJs);
                                case BUYER_BIDDING_LOGIC_URI_PATH:
                                    return new MockResponse().setBody(biddingLogicJs);
                            }

                            // The seller params vary based on runtime, so we are returning trusted
                            // signals based on correct path prefix
                            if (request.getPath()
                                    .startsWith(
                                            SELLER_TRUSTED_SIGNAL_URI_PATH
                                                    + SELLER_TRUSTED_SIGNAL_PARAMS)) {
                                return new MockResponse()
                                        .setBody(TRUSTED_SCORING_SIGNALS.toString());
                            }
                            if (request.getPath().startsWith(BUYER_TRUSTED_SIGNAL_URI_PATH)) {
                                return new MockResponse()
                                        .setBody(TRUSTED_BIDDING_SIGNALS.toString());
                            }
                            return new MockResponse().setResponseCode(404);
                        });

        // Join custom audience
        joinCustomAudienceAndAssertSuccess(customAudience1);

        // Run Ad Selection
        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(
                        mAdSelectionService, mAdSelectionConfig, CommonFixture.TEST_PACKAGE_NAME);

        assertFalse(resultsCallback.mIsSuccess);
        assertNull(resultsCallback.mAdSelectionResponse);

        // Run Report Impression with random ad selection id
        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionConfig(mAdSelectionConfig)
                        .setAdSelectionId(1)
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        ReportImpressionTestCallback reportImpressionTestCallback =
                callReportImpression(mAdSelectionService, input);

        assertFalse(reportImpressionTestCallback.mIsSuccess);

        // Run Report Interaction with random ad selection id
        ReportInteractionInput reportInteractionInput =
                new ReportInteractionInput.Builder()
                        .setAdSelectionId(1)
                        .setInteractionKey(CLICK_INTERACTION)
                        .setInteractionData(INTERACTION_DATA)
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .setReportingDestinations(BUYER_DESTINATION | SELLER_DESTINATION)
                        .build();

        ReportInteractionTestCallback reportInteractionTestCallback =
                callReportInteraction(mAdSelectionService, reportInteractionInput);
        assertFalse(reportInteractionTestCallback.mIsSuccess);

        mockWebServerRule.verifyMockServerRequests(
                server,
                2,
                ImmutableList.of(BUYER_BIDDING_LOGIC_URI_PATH, BUYER_TRUSTED_SIGNAL_URI_PATH),
                mRequestMatcherPrefixMatch);
    }

    @Test
    public void testSelectAdsWithFilterExceptionFromNullPointerExceptionDoesNotCrash()
            throws Exception {
        initClients(false, false, false, false, false);
        setupConsentGivenStubs();
        setupAdSelectionConfig();

        doThrow(new FilterException(new NullPointerException("Intentional test failure")))
                .when(mAdSelectionServiceFilterMock)
                .filterRequest(
                        any(),
                        anyString(),
                        anyBoolean(),
                        anyBoolean(),
                        anyInt(),
                        anyInt(),
                        any(),
                        any());

        // Run Ad Selection
        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(
                        mAdSelectionService, mAdSelectionConfig, CommonFixture.TEST_PACKAGE_NAME);

        assertWithMessage("Callback success").that(resultsCallback.mIsSuccess).isFalse();
        assertWithMessage("Callback AdSelectionResponse")
                .that(resultsCallback.mAdSelectionResponse)
                .isNull();
        assertWithMessage("Callback FledgeErrorResponse")
                .that(resultsCallback.mFledgeErrorResponse)
                .isNotNull();
        assertWithMessage("Callback FledgeErrorResponse")
                .that(resultsCallback.mFledgeErrorResponse.getStatusCode())
                .isEqualTo(AdServicesStatusUtils.STATUS_INTERNAL_ERROR);
    }

    private void updateHistogramAndAssertSuccess(long adSelectionId, int adEventType)
            throws InterruptedException {
        UpdateAdCounterHistogramInput inputParams =
                new UpdateAdCounterHistogramInput.Builder(
                                adSelectionId,
                                adEventType,
                                AdTechIdentifier.fromString(mLocalhostBuyerDomain.getHost()),
                                CommonFixture.TEST_PACKAGE_NAME)
                        .build();
        CountDownLatch callbackLatch = new CountDownLatch(1);
        UpdateAdCounterHistogramWorkerTest.UpdateAdCounterHistogramTestCallback callback =
                new UpdateAdCounterHistogramWorkerTest.UpdateAdCounterHistogramTestCallback(
                        callbackLatch);

        mAdSelectionService.updateAdCounterHistogram(inputParams, callback);

        assertThat(callbackLatch.await(5, TimeUnit.SECONDS)).isTrue();

        assertWithMessage("Callback failed, was: %s", callback).that(callback.isSuccess()).isTrue();
    }

    private long selectAdsAndReport(
            Uri winningRenderUri,
            Semaphore impressionReportingSemaphore,
            Semaphore interactionReportingSemaphore)
            throws Exception {
        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(
                        mAdSelectionService, mAdSelectionConfig, CommonFixture.TEST_PACKAGE_NAME);

        assertTrue(resultsCallback.mIsSuccess);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDao.doesAdSelectionIdExist(resultSelectionId));
        assertEquals(winningRenderUri, resultsCallback.mAdSelectionResponse.getRenderUri());

        reportImpressionAndAssertSuccess(resultSelectionId);
        reportInteractionAndAssertSuccess(resultsCallback);

        /* We add a permit on every call received by the MockWebServer and remove them in the
         * tryAcquires below. If there are any left over it means that there were extra calls to the
         * mockserver.
         */
        assertTrue(impressionReportingSemaphore.tryAcquire(2, 10, TimeUnit.SECONDS));
        assertTrue(interactionReportingSemaphore.tryAcquire(2, 10, TimeUnit.SECONDS));
        assertEquals(
                "Extra calls made to MockWebServer",
                0,
                impressionReportingSemaphore.availablePermits());
        assertEquals(
                "Extra calls made to MockWebServer",
                0,
                interactionReportingSemaphore.availablePermits());
        return resultSelectionId;
    }

    void verifyStandardServerRequests(MockWebServer server) {
        /*
         * We expect ten requests:
         * 2 bidding logic requests (one for each CA)
         * 2 decision logic requests (scoring and reporting)
         * 1 trusted bidding signals requests
         * 1 trusted seller signals request
         * 1 reportWin
         * 1 reportResult
         * 1 buyer click interaction report
         * 1 seller click interaction report
         */
        mockWebServerRule.verifyMockServerRequests(
                server,
                10,
                ImmutableList.of(
                        SELLER_DECISION_LOGIC_URI_PATH,
                        BUYER_BIDDING_LOGIC_URI_PATH + CUSTOM_AUDIENCE_SEQ_1,
                        BUYER_BIDDING_LOGIC_URI_PATH + CUSTOM_AUDIENCE_SEQ_2,
                        BUYER_TRUSTED_SIGNAL_URI_PATH,
                        SELLER_TRUSTED_SIGNAL_URI_PATH + SELLER_TRUSTED_SIGNAL_PARAMS),
                mRequestMatcherPrefixMatch);
    }

    private void registerForAppInstallFiltering() throws RemoteException, InterruptedException {
        setAppInstallAdvertisers(
                Collections.singleton(
                        AdTechIdentifier.fromString(mLocalhostBuyerDomain.getHost())));
    }

    private void deregisterForAppInstallFiltering() throws RemoteException, InterruptedException {
        setAppInstallAdvertisers(Collections.EMPTY_SET);
    }

    private void setAppInstallAdvertisers(Set<AdTechIdentifier> advertisers)
            throws RemoteException, InterruptedException {
        SetAppInstallAdvertisersInput setAppInstallAdvertisersInput =
                new SetAppInstallAdvertisersInput.Builder()
                        .setAdvertisers(advertisers)
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();
        AppInstallResultCapturingCallback appInstallCallback =
                invokeSetAppInstallAdvertisers(setAppInstallAdvertisersInput);
        assertTrue(
                "App Install call failed with: " + appInstallCallback.getException(),
                appInstallCallback.isSuccess());
    }

    private AppInstallResultCapturingCallback invokeSetAppInstallAdvertisers()
            throws RemoteException, InterruptedException {
        Set<AdTechIdentifier> advertisers =
                Collections.singleton(AdTechIdentifier.fromString(mLocalhostBuyerDomain.getHost()));
        SetAppInstallAdvertisersInput setAppInstallAdvertisersInput =
                new SetAppInstallAdvertisersInput.Builder()
                        .setAdvertisers(advertisers)
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();
        return invokeSetAppInstallAdvertisers(setAppInstallAdvertisersInput);
    }

    private AppInstallResultCapturingCallback invokeSetAppInstallAdvertisers(
            SetAppInstallAdvertisersInput input) throws RemoteException, InterruptedException {
        CountDownLatch appInstallDone = new CountDownLatch(1);
        AppInstallResultCapturingCallback appInstallCallback =
                new AppInstallResultCapturingCallback(appInstallDone);
        mAdSelectionService.setAppInstallAdvertisers(input, appInstallCallback);
        assertTrue(appInstallDone.await(5, TimeUnit.SECONDS));
        return appInstallCallback;
    }

    private String getV3BiddingLogicJs() {
        return "function generateBid(custom_audience, auction_signals, per_buyer_signals,\n"
                + "    trusted_bidding_signals, contextual_signals) {\n"
                + "    const ads = custom_audience.ads;\n"
                + "    let result = null;\n"
                + "    for (const ad of ads) {\n"
                + "        if (!result || ad.metadata.result > result.metadata.result) {\n"
                + "            result = ad;\n"
                + "        }\n"
                + "    }\n"
                + "    return { 'status': 0, 'ad': result, 'bid': result.metadata.result, "
                + "'render': result.render_uri };\n"
                + "}\n"
                + "function reportWin(ad_selection_signals, per_buyer_signals,"
                + " signals_for_buyer ,contextual_signals, custom_audience_signals) {\n"
                + "const beacons = {'click': '"
                + mockWebServerRule.uriForPath(CLICK_BUYER_PATH)
                + "', 'hover': '"
                + mockWebServerRule.uriForPath(HOVER_BUYER_PATH)
                + "'};\n"
                + "registerAdBeacon(beacons);"
                + " return {'status': 0, 'results': {'reporting_uri': '"
                + mockWebServerRule.uriForPath(BUYER_REPORTING_PATH)
                + "' } };\n"
                + "}";
    }

    private String getBiddingLogicJs() {
        return "function generateBid(ad, auction_signals, per_buyer_signals,"
                + " trusted_bidding_signals, contextual_signals, custom_audience_signals) { \n"
                + "  return {'status': 0, 'ad': ad, 'bid': ad.metadata.result };\n"
                + "}\n"
                + "function reportWin(ad_selection_signals, per_buyer_signals,"
                + " signals_for_buyer, contextual_signals, custom_audience_signals) { \n"
                + " return {'status': 0, 'results': {'reporting_uri': '"
                + mockWebServerRule.uriForPath(BUYER_REPORTING_PATH)
                + "' } };\n"
                + "}";
    }

    private String getBiddingLogicJsWithAdCost() {
        return "function generateBid(ad, auction_signals, per_buyer_signals,"
                + " trusted_bidding_signals, contextual_signals, custom_audience_signals) { \n"
                + "  return {'status': 0, 'ad': ad, 'bid': ad.metadata.result, 'adCost':"
                + " ad.metadata.adCost };\n"
                + "}\n"
                + "\n"
                + "function reportWin(ad_selection_signals, per_buyer_signals,"
                + " signals_for_buyer,\n"
                + "    contextual_signals, custom_audience_reporting_signals) {\n"
                + "    let reporting_address = '"
                + mockWebServerRule.uriForPath(BUYER_REPORTING_PATH)
                + "';\n"
                + "    return {'status': 0, 'results': {'reporting_uri':\n"
                + "                reporting_address + '?adCost=' + contextual_signals.adCost} };\n"
                + "}";
    }

    private String getDecisionLogicJs() {
        return "function scoreAd(ad, bid, auction_config, seller_signals,"
                + " trusted_scoring_signals, contextual_signal, user_signal,"
                + " custom_audience_signal) { \n"
                + "  return {'status': 0, 'score': bid };\n"
                + "}\n"
                + "function reportResult(ad_selection_config, render_uri, bid,"
                + " contextual_signals) { \n"
                + " return {'status': 0, 'results': {'signals_for_buyer':"
                + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                + mockWebServerRule.uriForPath(SELLER_REPORTING_PATH)
                + "' } };\n"
                + "}";
    }

    private String getDecisionLogicJsWithDebugReporting(String reportWinUrl, String reportLossUrl) {
        return "function scoreAd(ad, bid, auction_config, seller_signals,"
                + " trusted_scoring_signals, contextual_signal, user_signal,"
                + " custom_audience_signal) { \n"
                + "  forDebuggingOnly.reportAdAuctionWin('"
                + reportWinUrl
                + "');"
                + "  forDebuggingOnly.reportAdAuctionLoss('"
                + reportLossUrl
                + "');"
                + "  return {'status': 0, 'score': bid };\n"
                + "}";
    }

    private String getBiddingLogicWithBeacons() {
        return "function generateBid(ad, auction_signals, per_buyer_signals,"
                + " trusted_bidding_signals, contextual_signals, custom_audience_signals) { \n"
                + "  return {'status': 0, 'ad': ad, 'bid': ad.metadata.result };\n"
                + "}\n"
                + "function reportWin(ad_selection_signals, per_buyer_signals,"
                + " signals_for_buyer ,contextual_signals, custom_audience_signals) {\n"
                + "const beacons = {'click': '"
                + mockWebServerRule.uriForPath(CLICK_BUYER_PATH)
                + "', 'hover': '"
                + mockWebServerRule.uriForPath(HOVER_BUYER_PATH)
                + "'};\n"
                + "registerAdBeacon(beacons);"
                + " return {'status': 0, 'results': {'reporting_uri': '"
                + mockWebServerRule.uriForPath(BUYER_REPORTING_PATH)
                + "' } };\n"
                + "}";
    }

    private String getBiddingLogicWithDebugReporting(String reportWinUrl, String reportLossUrl) {
        return "function generateBid(custom_audience, auction_signals, per_buyer_signals,\n"
                + "    trusted_bidding_signals, contextual_signals) {\n"
                + "    const ads = custom_audience.ads;\n"
                + "    let result = null;\n"
                + "    for (const ad of ads) {\n"
                + "        if (!result || ad.metadata.result > result.metadata.result) {\n"
                + "            result = ad;\n"
                + "            forDebuggingOnly.reportAdAuctionWin('"
                + reportWinUrl
                + "');"
                + "            forDebuggingOnly.reportAdAuctionLoss('"
                + reportLossUrl
                + "');"
                + "        }\n"
                + "    }\n"
                + "    return { 'status': 0, 'ad': result, 'bid': result.metadata.result, "
                + "'render': result.render_uri };\n"
                + "}";
    }

    private String getDecisionLogicWithBeacons() {
        return "function scoreAd(ad, bid, auction_config, seller_signals,"
                + " trusted_scoring_signals, contextual_signal, user_signal,"
                + " custom_audience_signal) { \n"
                + "  return {'status': 0, 'score': bid };\n"
                + "}\n"
                + "function reportResult(ad_selection_config, render_uri, bid,"
                + " contextual_signals) {\n"
                + "const beacons = {'click': '"
                + mockWebServerRule.uriForPath(CLICK_SELLER_PATH)
                + "', 'hover': '"
                + mockWebServerRule.uriForPath(HOVER_SELLER_PATH)
                + "'};\n"
                + "registerAdBeacon(beacons);"
                + " return {'status': 0, 'results': {'signals_for_buyer':"
                + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                + mockWebServerRule.uriForPath(SELLER_REPORTING_PATH)
                + "' } };\n"
                + "}";
    }

    private String getBiddingLogicWithBeaconsWithAdCost() {
        return "function generateBid(ad, auction_signals, per_buyer_signals,"
                + " trusted_bidding_signals, contextual_signals, custom_audience_signals) { \n"
                + "  return {'status': 0, 'ad': ad, 'bid': ad.metadata.result, 'adCost':"
                + " ad.metadata.adCost };\n"
                + "}\n"
                + "function reportWin(ad_selection_signals, per_buyer_signals,"
                + " signals_for_buyer, contextual_signals, custom_audience_signals) {\n"
                + "const beacons = {'click': '"
                + mockWebServerRule.uriForPath(CLICK_BUYER_PATH)
                + "', 'hover': '"
                + mockWebServerRule.uriForPath(HOVER_BUYER_PATH)
                + "'};\n"
                + "registerAdBeacon(beacons);"
                + "    let reporting_address = '"
                + mockWebServerRule.uriForPath(BUYER_REPORTING_PATH)
                + "';\n"
                + "    return {'status': 0, 'results': {'reporting_uri':\n"
                + "                reporting_address + '?adCost=' + contextual_signals.adCost} };\n"
                + "}";
    }

    private String getBiddingLogicWithDataVersion() {
        return "function generateBid(ad, auction_signals, per_buyer_signals,"
                + " trusted_bidding_signals, contextual_signals, custom_audience_signals) { \n"
                + "  return {'status': 0, 'ad': ad, 'bid': ad.metadata.result, 'adCost':"
                + " ad.metadata.adCost };\n"
                + "}\n"
                + "function reportWin(ad_selection_signals, per_buyer_signals,"
                + " signals_for_buyer, contextual_signals, custom_audience_signals) {\n"
                + "    let reporting_address = '"
                + mockWebServerRule.uriForPath(BUYER_REPORTING_PATH)
                + "';\n"
                + "    return {'status': 0, 'results': {'reporting_uri':\n"
                + "                reporting_address + '?dataVersion=' +"
                + " contextual_signals.dataVersion} };\n"
                + "}";
    }

    private String getDecisionLogicWithDataVersion() {
        return "function scoreAd(ad, bid, auction_config, seller_signals,"
                + " trusted_scoring_signals, contextual_signal, user_signal,"
                + " custom_audience_signal) { \n"
                + "  return {'status': 0, 'score': bid };\n"
                + "}\n"
                + "function reportResult(ad_selection_config, render_uri, bid,"
                + " contextual_signals) {\n"
                + "    let reporting_address = '"
                + mockWebServerRule.uriForPath(SELLER_REPORTING_PATH)
                + "';\n"
                + " return {'status': 0, 'results': {'signals_for_buyer':"
                + " '{\"signals_for_buyer\":1}', 'reporting_uri':\n"
                + "                reporting_address + '?dataVersion=' +"
                + " contextual_signals.dataVersion} };\n"
                + "}";
    }

    private MockWebServer getMockWebServer(
            String decisionLogicJs,
            String biddingLogicJs,
            Semaphore impressionReportingSemaphore,
            Semaphore interactionReportingSemaphore,
            boolean jsVersioning)
            throws Exception {
        return getMockWebServer(
                new HashMap<>(),
                decisionLogicJs,
                biddingLogicJs,
                impressionReportingSemaphore,
                interactionReportingSemaphore,
                null,
                jsVersioning);
    }

    private MockWebServer getMockWebServer(
            String decisionLogicJs, String biddingLogicJs, CountDownLatch debugReportingLatch)
            throws Exception {
        return getMockWebServer(
                new HashMap<>(),
                decisionLogicJs,
                biddingLogicJs,
                null,
                null,
                debugReportingLatch,
                true);
    }

    private MockWebServer getMockWebServer(
            HashMap<String, String> remoteCustomAudiencesMap,
            String decisionLogicJs,
            String biddingLogicJs,
            Semaphore impressionReportingSemaphore,
            Semaphore interactionReportingSemaphore,
            CountDownLatch debugReportingLatch,
            boolean jsVersioning)
            throws Exception {
        String versionHeaderName =
                JsVersionHelper.getVersionHeaderName(
                        JsVersionHelper.JS_PAYLOAD_TYPE_BUYER_BIDDING_LOGIC_JS);
        long jsVersion = JsVersionRegister.BUYER_BIDDING_LOGIC_VERSION_VERSION_3;
        return mockWebServerRule.startMockWebServer(
                request -> {
                    String requestPath = request.getPath();
                    switch (requestPath) {
                        case SELLER_DECISION_LOGIC_URI_PATH:
                            return new MockResponse().setBody(decisionLogicJs);
                        case BUYER_BIDDING_LOGIC_URI_PATH + CUSTOM_AUDIENCE_SEQ_1:
                        case BUYER_BIDDING_LOGIC_URI_PATH + CUSTOM_AUDIENCE_SEQ_2:
                            if (jsVersioning) {
                                if (Objects.equals(
                                        request.getHeader(versionHeaderName),
                                        Long.toString(jsVersion))) {
                                    return new MockResponse()
                                            .setBody(biddingLogicJs)
                                            .setHeader(versionHeaderName, jsVersion);
                                }
                                break;
                            } else {
                                return new MockResponse().setBody(biddingLogicJs);
                            }
                        case BUYER_DEBUG_REPORT_WIN_PATH + DEBUG_REPORT_WINNING_BID_RESPONSE:
                        case BUYER_DEBUG_REPORT_LOSS_PATH + DEBUG_REPORT_WINNING_BID_RESPONSE:
                        case SELLER_DEBUG_REPORT_WIN_PATH + DEBUG_REPORT_WINNING_BID_RESPONSE:
                        case SELLER_DEBUG_REPORT_LOSS_PATH + DEBUG_REPORT_WINNING_BID_RESPONSE:
                            if (Objects.nonNull(debugReportingLatch)) {
                                debugReportingLatch.countDown();
                            }
                            return new MockResponse().setResponseCode(200);
                        case CLICK_SELLER_PATH: // Intentional fallthrough
                        case CLICK_BUYER_PATH:
                            interactionReportingSemaphore.release();
                            return new MockResponse().setResponseCode(200);
                        case SELLER_REPORTING_PATH: // Intentional fallthrough
                        case BUYER_REPORTING_PATH:
                            impressionReportingSemaphore.release();
                            return new MockResponse().setResponseCode(200);
                    }

                    if (requestPath.contains(FETCH_CA_PATH)) {
                        String[] pathSegments = requestPath.split("/");
                        String customAudienceId = pathSegments[pathSegments.length - 1];
                        if (remoteCustomAudiencesMap.containsKey(customAudienceId)) {
                            return new MockResponse()
                                    .setBody(remoteCustomAudiencesMap.get(customAudienceId));
                        } else {
                            return new MockResponse().setResponseCode(404);
                        }
                    }

                    // Case where adCost / data version is reported in the Uri
                    if (request.getPath().startsWith(BUYER_REPORTING_PATH)) {
                        impressionReportingSemaphore.release();
                        return new MockResponse().setResponseCode(200);
                    }

                    // Case where data version is reported in the Uri
                    if (request.getPath().startsWith(SELLER_REPORTING_PATH)) {
                        impressionReportingSemaphore.release();
                        return new MockResponse().setResponseCode(200);
                    }

                    // The seller params vary based on runtime, so we are returning trusted
                    // signals based on correct path prefix
                    if (request.getPath()
                            .startsWith(
                                    SELLER_TRUSTED_SIGNAL_URI_PATH
                                            + SELLER_TRUSTED_SIGNAL_PARAMS)) {
                        return new MockResponse().setBody(TRUSTED_SCORING_SIGNALS.toString());
                    }

                    // Add seller trusted scoring uri path with data version header
                    if (request.getPath()
                            .startsWith(
                                    SELLER_TRUSTED_SIGNAL_URI_PATH_WITH_DATA_VERSION
                                            + SELLER_TRUSTED_SIGNAL_PARAMS)) {
                        return new MockResponse()
                                .setBody(TRUSTED_SCORING_SIGNALS.toString())
                                .addHeader(DATA_VERSION_HEADER_SCORING_KEY, DATA_VERSION_2);
                    }

                    if (request.getPath().startsWith(BUYER_TRUSTED_SIGNAL_URI_PATH)) {
                        return new MockResponse().setBody(TRUSTED_BIDDING_SIGNALS.toString());
                    }

                    // Add seller trusted scoring uri path with data version header
                    if (request.getPath()
                            .startsWith(BUYER_TRUSTED_SIGNAL_URI_PATH_WITH_DATA_VERSION)) {
                        return new MockResponse()
                                .setBody(TRUSTED_BIDDING_SIGNALS.toString())
                                .addHeader(DATA_VERSION_HEADER_BIDDING_KEY, DATA_VERSION_1);
                    }
                    return new MockResponse().setResponseCode(404);
                });
    }

    private void reportInteractionAndAssertSuccess(AdSelectionTestCallback resultsCallback)
            throws Exception {
        ReportInteractionInput reportInteractionInput =
                new ReportInteractionInput.Builder()
                        .setAdSelectionId(resultsCallback.mAdSelectionResponse.getAdSelectionId())
                        .setInteractionKey(CLICK_INTERACTION)
                        .setInteractionData(INTERACTION_DATA)
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .setReportingDestinations(BUYER_DESTINATION | SELLER_DESTINATION)
                        .build();

        ReportInteractionTestCallback reportInteractionTestCallback =
                callReportInteraction(mAdSelectionService, reportInteractionInput);
        assertTrue(reportInteractionTestCallback.mIsSuccess);
    }

    private void reportOnlyBuyerInteractionAndAssertSuccess(AdSelectionTestCallback resultsCallback)
            throws Exception {
        ReportInteractionInput reportInteractionInput =
                new ReportInteractionInput.Builder()
                        .setAdSelectionId(resultsCallback.mAdSelectionResponse.getAdSelectionId())
                        .setInteractionKey(CLICK_INTERACTION)
                        .setInteractionData(INTERACTION_DATA)
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .setReportingDestinations(BUYER_DESTINATION)
                        .build();

        ReportInteractionTestCallback reportInteractionTestCallback =
                callReportInteraction(mAdSelectionService, reportInteractionInput);
        assertTrue(reportInteractionTestCallback.mIsSuccess);
    }

    private void reportImpressionAndAssertSuccess(long adSelectionId) throws Exception {
        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionConfig(mAdSelectionConfig)
                        .setAdSelectionId(adSelectionId)
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        ReportImpressionTestCallback reportImpressionTestCallback =
                callReportImpression(mAdSelectionService, input);

        assertTrue(reportImpressionTestCallback.mIsSuccess);
    }

    private void setupOverridesAndAssertSuccess(
            CustomAudience customAudience1,
            CustomAudience customAudience2,
            String biddingLogicJs,
            String decisionLogicJs)
            throws Exception {
        // Add AdSelection Override
        AdSelectionOverrideTestCallback adSelectionOverrideTestCallback =
                callAddAdSelectionOverride(
                        mAdSelectionService,
                        mAdSelectionConfig,
                        decisionLogicJs,
                        TRUSTED_SCORING_SIGNALS,
                        BUYERS_DECISION_LOGIC);

        assertTrue(adSelectionOverrideTestCallback.mIsSuccess);

        // Add Custom Audience Overrides
        CustomAudienceOverrideTestCallback customAudienceOverrideTestCallback1 =
                callAddCustomAudienceOverride(
                        CommonFixture.TEST_PACKAGE_NAME,
                        customAudience1.getBuyer(),
                        customAudience1.getName(),
                        biddingLogicJs,
                        null,
                        TRUSTED_BIDDING_SIGNALS,
                        mCustomAudienceService);

        assertTrue(customAudienceOverrideTestCallback1.mIsSuccess);

        CustomAudienceOverrideTestCallback customAudienceOverrideTestCallback2 =
                callAddCustomAudienceOverride(
                        CommonFixture.TEST_PACKAGE_NAME,
                        customAudience2.getBuyer(),
                        customAudience2.getName(),
                        biddingLogicJs,
                        null,
                        TRUSTED_BIDDING_SIGNALS,
                        mCustomAudienceService);

        assertTrue(customAudienceOverrideTestCallback2.mIsSuccess);
    }

    private void setupAdSelectionConfig() {
        mAdSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setCustomAudienceBuyers(
                                ImmutableList.of(
                                        AdTechIdentifier.fromString(
                                                mLocalhostBuyerDomain.getHost())))
                        .setSeller(
                                AdTechIdentifier.fromString(
                                        mockWebServerRule
                                                .uriForPath(SELLER_DECISION_LOGIC_URI_PATH)
                                                .getHost()))
                        .setDecisionLogicUri(
                                mockWebServerRule.uriForPath(SELLER_DECISION_LOGIC_URI_PATH))
                        .setTrustedScoringSignalsUri(
                                mockWebServerRule.uriForPath(SELLER_TRUSTED_SIGNAL_URI_PATH))
                        .setPerBuyerSignals(
                                ImmutableMap.of(
                                        AdTechIdentifier.fromString(
                                                mLocalhostBuyerDomain.getHost()),
                                        AdSelectionSignals.fromString("{\"buyer_signals\":0}")))
                        .build();
    }

    private void setupAdSelectionConfigWithDataVersionHeader() {
        mAdSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setCustomAudienceBuyers(
                                ImmutableList.of(
                                        AdTechIdentifier.fromString(
                                                mLocalhostBuyerDomain.getHost())))
                        .setSeller(
                                AdTechIdentifier.fromString(
                                        mockWebServerRule
                                                .uriForPath(SELLER_DECISION_LOGIC_URI_PATH)
                                                .getHost()))
                        .setDecisionLogicUri(
                                mockWebServerRule.uriForPath(SELLER_DECISION_LOGIC_URI_PATH))
                        .setTrustedScoringSignalsUri(
                                mockWebServerRule.uriForPath(
                                        SELLER_TRUSTED_SIGNAL_URI_PATH_WITH_DATA_VERSION))
                        .setPerBuyerSignals(
                                ImmutableMap.of(
                                        AdTechIdentifier.fromString(
                                                mLocalhostBuyerDomain.getHost()),
                                        AdSelectionSignals.fromString("{\"buyer_signals\":0}")))
                        .build();
    }

    private void setupConsentGivenStubs() {
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        doReturn(false).when(mConsentManagerMock).isFledgeConsentRevokedForApp(any());
        doReturn(false)
                .when(mConsentManagerMock)
                .isFledgeConsentRevokedForAppAfterSettingFledgeUse(any());
    }

    private void joinCustomAudienceAndAssertSuccess(CustomAudience ca) {
        ResultCapturingCallback joinCallback = new ResultCapturingCallback();
        mCustomAudienceService.joinCustomAudience(
                ca, CommonFixture.TEST_PACKAGE_NAME, joinCallback);
        assertTrue(joinCallback.isSuccess());
    }

    private void fetchAndJoinCustomAudienceAndAssertSuccess(FetchAndJoinCustomAudienceInput request)
            throws InterruptedException {
        CountDownLatch resultLatch = new CountDownLatch(1);
        FetchCustomAudienceTestCallback callback = new FetchCustomAudienceTestCallback(resultLatch);
        mCustomAudienceService.fetchAndJoinCustomAudience(request, callback);
        resultLatch.await();
        assertTrue(callback.isSuccess());
    }

    private void initClients(
            boolean gaUXEnabled,
            boolean registerAdBeaconEnabled,
            boolean cpcBillingEnabled,
            boolean dataVersionHeaderEnabled,
            boolean shouldUseUnifiedTables) {
        initClients(
                gaUXEnabled,
                registerAdBeaconEnabled,
                true,
                true,
                cpcBillingEnabled,
                false,
                dataVersionHeaderEnabled,
                false,
                false,
                shouldUseUnifiedTables);
    }

    private void initClients(
            boolean gaUXEnabled,
            boolean registerAdBeaconEnabled,
            boolean filtersEnabled,
            boolean enrollmentCheckDisabled,
            boolean cpcBillingEnabled,
            boolean debugReportingEnabled,
            boolean dataVersionHeaderEnabled,
            boolean debugReportSendImmediately,
            boolean adIdKillSwitch,
            boolean shouldUseUnifiedTables) {
        Flags flags =
                new FledgeE2ETestFlags(
                        gaUXEnabled,
                        registerAdBeaconEnabled,
                        filtersEnabled,
                        enrollmentCheckDisabled,
                        cpcBillingEnabled,
                        debugReportingEnabled,
                        dataVersionHeaderEnabled,
                        debugReportSendImmediately,
                        adIdKillSwitch,
                        shouldUseUnifiedTables);

        mCustomAudienceService =
                new CustomAudienceServiceImpl(
                        CONTEXT_SPY,
                        new CustomAudienceImpl(
                                mCustomAudienceDao,
                                new CustomAudienceQuantityChecker(mCustomAudienceDao, flags),
                                new CustomAudienceValidator(
                                        CommonFixture.FIXED_CLOCK_TRUNCATED_TO_MILLI,
                                        flags,
                                        flags.getFledgeAdSelectionFilteringEnabled()
                                                ? new FrequencyCapAdDataValidatorImpl()
                                                : new FrequencyCapAdDataValidatorNoOpImpl(),
                                        AdRenderIdValidator.createInstance(flags)),
                                CommonFixture.FIXED_CLOCK_TRUNCATED_TO_MILLI,
                                flags),
                        mFledgeAuthorizationFilterMock,
                        mConsentManagerMock,
                        mDevContextFilterMock,
                        MoreExecutors.newDirectExecutorService(),
                        mAdServicesLoggerMock,
                        mAppImportanceFilterMock,
                        flags,
                        CallingAppUidSupplierProcessImpl.create(),
                        new CustomAudienceServiceFilter(
                                CONTEXT_SPY,
                                mConsentManagerMock,
                                flags,
                                mAppImportanceFilterMock,
                                mFledgeAuthorizationFilterMock,
                                mFledgeAllowListsFilterSpy,
                                mMockThrottler),
                        new AdFilteringFeatureFactory(mAppInstallDao, mFrequencyCapDao, flags));

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());
        mAdFilteringFeatureFactory =
                new AdFilteringFeatureFactory(mAppInstallDao, mFrequencyCapDao, flags);
        mAdIdFetcher =
                new AdIdFetcher(mMockAdIdWorker, mLightweightExecutorService, mScheduledExecutor);
        // Create an instance of AdSelection Service with real dependencies
        mAdSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mAdServicesHttpsClient,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT_SPY,
                        mAdServicesLoggerMock,
                        flags,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterMock,
                        mAdSelectionServiceFilterMock,
                        mAdFilteringFeatureFactory,
                        mConsentManagerMock,
                        mMultiCloudSupportStrategy,
                        mAdSelectionDebugReportDao,
                        mAdIdFetcher,
                        mUnusedKAnonSignJoinFactory,
                        false,
                        mRetryStrategyFactory);
    }

    private AdSelectionTestCallback invokeRunAdSelection(
            AdSelectionServiceImpl adSelectionService,
            AdSelectionConfig adSelectionConfig,
            String callerPackageName)
            throws InterruptedException {

        CountDownLatch countdownLatch = new CountDownLatch(1);
        AdSelectionTestCallback adSelectionTestCallback =
                new AdSelectionTestCallback(countdownLatch);

        AdSelectionInput input =
                new AdSelectionInput.Builder()
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(callerPackageName)
                        .build();
        CallerMetadata callerMetadata =
                new CallerMetadata.Builder()
                        .setBinderElapsedTimestamp(BINDER_ELAPSED_TIMESTAMP)
                        .build();
        adSelectionService.selectAds(input, callerMetadata, adSelectionTestCallback);
        adSelectionTestCallback.mCountDownLatch.await();
        return adSelectionTestCallback;
    }

    private AdSelectionTestCallback invokeRunAdSelectionAndWaitForFullCallback(
            AdSelectionServiceImpl adSelectionService,
            AdSelectionConfig adSelectionConfig,
            String callerPackageName)
            throws InterruptedException {

        CountDownLatch countdownLatch = new CountDownLatch(1);
        AdSelectionTestCallback adSelectionTestCallback =
                new AdSelectionTestCallback(countdownLatch);
        CountDownLatch countdownLatchForFullCallback = new CountDownLatch(1);
        AdSelectionTestCallback adSelectionTestFullCallback =
                new AdSelectionTestCallback(countdownLatchForFullCallback);

        AdSelectionInput input =
                new AdSelectionInput.Builder()
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(callerPackageName)
                        .build();
        CallerMetadata callerMetadata =
                new CallerMetadata.Builder()
                        .setBinderElapsedTimestamp(BINDER_ELAPSED_TIMESTAMP)
                        .build();
        adSelectionService.selectAds(
                input, callerMetadata, adSelectionTestCallback, adSelectionTestFullCallback);
        adSelectionTestCallback.mCountDownLatch.await();
        adSelectionTestFullCallback.mCountDownLatch.await();
        return adSelectionTestCallback;
    }

    private AdSelectionOverrideTestCallback callAddAdSelectionOverride(
            AdSelectionServiceImpl adSelectionService,
            AdSelectionConfig adSelectionConfig,
            String decisionLogicJS,
            AdSelectionSignals trustedScoringSignals,
            PerBuyerDecisionLogic buyerDecisionLogicMap)
            throws Exception {
        // Counted down in 1) callback
        CountDownLatch resultLatch = new CountDownLatch(1);
        AdSelectionOverrideTestCallback callback = new AdSelectionOverrideTestCallback(resultLatch);

        adSelectionService.overrideAdSelectionConfigRemoteInfo(
                adSelectionConfig,
                decisionLogicJS,
                trustedScoringSignals,
                buyerDecisionLogicMap,
                callback);
        resultLatch.await();
        return callback;
    }

    private CustomAudienceOverrideTestCallback callAddCustomAudienceOverride(
            String owner,
            AdTechIdentifier buyer,
            String name,
            String biddingLogicJs,
            Long biddingLogicJsVersion,
            AdSelectionSignals trustedBiddingData,
            CustomAudienceServiceImpl customAudienceService)
            throws Exception {
        CountDownLatch resultLatch = new CountDownLatch(1);
        CustomAudienceOverrideTestCallback callback =
                new CustomAudienceOverrideTestCallback(resultLatch);

        customAudienceService.overrideCustomAudienceRemoteInfo(
                owner,
                buyer,
                name,
                biddingLogicJs,
                Optional.ofNullable(biddingLogicJsVersion).orElse(0L),
                trustedBiddingData,
                callback);
        resultLatch.await();
        return callback;
    }

    private ReportImpressionTestCallback callReportImpression(
            AdSelectionServiceImpl adSelectionService, ReportImpressionInput requestParams)
            throws Exception {
        // Counted down in 1) callback
        CountDownLatch resultLatch = new CountDownLatch(1);
        ReportImpressionTestCallback callback = new ReportImpressionTestCallback(resultLatch);

        adSelectionService.reportImpression(requestParams, callback);
        resultLatch.await();
        return callback;
    }

    private ReportInteractionTestCallback callReportInteraction(
            AdSelectionServiceImpl adSelectionService, ReportInteractionInput inputParams)
            throws Exception {
        // Counted down in 1) callback
        CountDownLatch resultLatch = new CountDownLatch(1);
        ReportInteractionTestCallback callback = new ReportInteractionTestCallback(resultLatch);

        adSelectionService.reportInteraction(inputParams, callback);
        resultLatch.await();
        return callback;
    }

    /** See {@link #createCustomAudience(Uri, String, List)}. */
    private CustomAudience createCustomAudience(final Uri buyerDomain, List<Double> bids) {
        return createCustomAudience(buyerDomain, "", bids);
    }

    private CustomAudience createCustomAudience(
            final Uri buyerDomain, final String customAudienceSeq, List<Double> bids) {
        return createCustomAudience(buyerDomain, customAudienceSeq, bids, null, null, false);
    }

    private CustomAudience createCustomAudienceWithDataVersion(
            final Uri buyerDomain, final String customAudienceSeq, List<Double> bids) {
        return createCustomAudience(buyerDomain, customAudienceSeq, bids, null, null, true);
    }

    private CustomAudience createCustomAudienceWithAdCost(
            final Uri buyerDomain,
            final String customAudienceSeq,
            List<Double> bids,
            double adCost) {
        return createCustomAudienceWithAdCost(
                buyerDomain, customAudienceSeq, bids, null, null, adCost);
    }

    /**
     * @param buyerDomain The name of the buyer for this Custom Audience
     * @param customAudienceSeq optional numbering for ca name. Should start with slash.
     * @param bids these bids, are added to its metadata. Our JS logic then picks this value and
     *     creates ad with the provided value as bid
     * @param filtersForBids A parallel list to bids with the filter that should be added to each
     *     Ad. Can be left null.
     * @param adCounterKeysForBids A parallel list to bids with the adCounterKeys that should be
     *     added to each Ad. Can be left null.
     * @param withDataVersion Whether or not to fetch data version in trusted bidding signals
     * @return a real Custom Audience object that can be persisted and used in bidding and scoring
     */
    private CustomAudience createCustomAudience(
            final Uri buyerDomain,
            final String customAudienceSeq,
            List<Double> bids,
            List<Set<Integer>> adCounterKeysForBids,
            List<AdFilters> filtersForBids,
            boolean withDataVersion) {

        // Generate ads for with bids provided
        List<AdData> ads = new ArrayList<>();

        // Create ads with the buyer name and bid number as the ad URI
        // Add the bid value to the metadata
        for (int i = 0; i < bids.size(); i++) {
            AdData.Builder builder =
                    new AdData.Builder()
                            .setRenderUri(
                                    CommonFixture.getUri(
                                            buyerDomain.getAuthority(),
                                            AD_URI_PREFIX + customAudienceSeq + "/ad" + (i + 1)))
                            .setMetadata("{\"result\":" + bids.get(i) + "}");
            if (adCounterKeysForBids != null && adCounterKeysForBids.get(i) != null) {
                builder.setAdCounterKeys(adCounterKeysForBids.get(i));
            }
            if (filtersForBids != null) {
                builder.setAdFilters(filtersForBids.get(i));
            }
            ads.add(builder.build());
        }

        String trustedBiddingUriPath;
        if (withDataVersion) {
            trustedBiddingUriPath = BUYER_TRUSTED_SIGNAL_URI_PATH_WITH_DATA_VERSION;
        } else {
            trustedBiddingUriPath = BUYER_TRUSTED_SIGNAL_URI_PATH;
        }

        return new CustomAudience.Builder()
                .setBuyer(AdTechIdentifier.fromString(buyerDomain.getHost()))
                .setName(
                        buyerDomain.getHost()
                                + customAudienceSeq
                                + CustomAudienceFixture.VALID_NAME)
                .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                .setDailyUpdateUri(
                        CustomAudienceFixture.getValidDailyUpdateUriByBuyer(
                                AdTechIdentifier.fromString(buyerDomain.getAuthority())))
                .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                .setTrustedBiddingData(
                        new TrustedBiddingData.Builder()
                                .setTrustedBiddingUri(
                                        CommonFixture.getUri(
                                                buyerDomain.getAuthority(), trustedBiddingUriPath))
                                .setTrustedBiddingKeys(
                                        TrustedBiddingDataFixture.getValidTrustedBiddingKeys())
                                .build())
                .setBiddingLogicUri(
                        CommonFixture.getUri(
                                buyerDomain.getAuthority(),
                                BUYER_BIDDING_LOGIC_URI_PATH + customAudienceSeq))
                .setAds(ads)
                .build();
    }

    /**
     * @param buyerDomain The name of the buyer for this Custom Audience
     * @param customAudienceSeq optional numbering for ca name. Should start with slash.
     * @param bids these bids, are added to its metadata. Our JS logic then picks this value and
     *     creates ad with the provided value as bid
     * @param filtersForBids A parallel list to bids with the filter that should be added to each
     *     Ad. Can be left null.
     * @param adCounterKeysForBids A parallel list to bids with the adCounterKeys that should be
     *     added to each Ad. Can be left null.
     * @param adCost The cost to click on an ad
     * @return a real Custom Audience object that can be persisted and used in bidding and scoring
     */
    private CustomAudience createCustomAudienceWithAdCost(
            final Uri buyerDomain,
            final String customAudienceSeq,
            List<Double> bids,
            List<Set<Integer>> adCounterKeysForBids,
            List<AdFilters> filtersForBids,
            double adCost) {

        // Generate ads for with bids provided
        List<AdData> ads = new ArrayList<>();

        // Create ads with the buyer name and bid number as the ad URI
        // Add the bid value to the metadata
        for (int i = 0; i < bids.size(); i++) {
            AdData.Builder builder =
                    new AdData.Builder()
                            .setRenderUri(
                                    CommonFixture.getUri(
                                            buyerDomain.getAuthority(),
                                            AD_URI_PREFIX + customAudienceSeq + "/ad" + (i + 1)))
                            .setMetadata(
                                    "{\"result\":" + bids.get(i) + ",\"adCost\":" + adCost + "}");
            if (adCounterKeysForBids != null && adCounterKeysForBids.get(i) != null) {
                builder.setAdCounterKeys(adCounterKeysForBids.get(i));
            }
            if (filtersForBids != null) {
                builder.setAdFilters(filtersForBids.get(i));
            }
            ads.add(builder.build());
        }

        return new CustomAudience.Builder()
                .setBuyer(AdTechIdentifier.fromString(buyerDomain.getHost()))
                .setName(
                        buyerDomain.getHost()
                                + customAudienceSeq
                                + CustomAudienceFixture.VALID_NAME)
                .setActivationTime(CustomAudienceFixture.VALID_ACTIVATION_TIME)
                .setExpirationTime(CustomAudienceFixture.VALID_EXPIRATION_TIME)
                .setDailyUpdateUri(
                        CustomAudienceFixture.getValidDailyUpdateUriByBuyer(
                                AdTechIdentifier.fromString(buyerDomain.getAuthority())))
                .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                .setTrustedBiddingData(
                        new TrustedBiddingData.Builder()
                                .setTrustedBiddingUri(
                                        CommonFixture.getUri(
                                                buyerDomain.getAuthority(),
                                                BUYER_TRUSTED_SIGNAL_URI_PATH))
                                .setTrustedBiddingKeys(
                                        TrustedBiddingDataFixture.getValidTrustedBiddingKeys())
                                .build())
                .setBiddingLogicUri(
                        CommonFixture.getUri(
                                buyerDomain.getAuthority(),
                                BUYER_BIDDING_LOGIC_URI_PATH + customAudienceSeq))
                .setAds(ads)
                .build();
    }

    private void testFledgeFlowSuccessAllFilters(boolean shouldUseUnifiedTables) throws Exception {
        initClients(true, true, false, false, shouldUseUnifiedTables);
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        doReturn(false)
                .when(mConsentManagerMock)
                .isFledgeConsentRevokedForAppAfterSettingFledgeUse(any());

        setupAdSelectionConfig();
        CustomAudience customAudience1 =
                createCustomAudience(
                        mLocalhostBuyerDomain, CUSTOM_AUDIENCE_SEQ_1, BIDS_FOR_BUYER_1);

        // Using the same generic key across all ads in the CA
        List<Set<Integer>> adCounterKeysForCa2 =
                Arrays.asList(
                        Collections.singleton(KeyedFrequencyCapFixture.KEY1),
                        Collections.singleton(KeyedFrequencyCapFixture.KEY1),
                        Collections.singleton(KeyedFrequencyCapFixture.KEY1));
        /* The final ad with the highest bid has both fcap and app install filters, the second ad
         * with the middle bid has only an app install filter and the first ad with the lowest bid
         * in this ca has only a fcap filter.
         */
        List<AdFilters> adFiltersForCa2 =
                Arrays.asList(
                        new AdFilters.Builder()
                                .setFrequencyCapFilters(CLICK_ONCE_PER_DAY_KEY1)
                                .build(),
                        new AdFilters.Builder().setAppInstallFilters(CURRENT_APP_FILTER).build(),
                        new AdFilters.Builder()
                                .setAppInstallFilters(CURRENT_APP_FILTER)
                                .setFrequencyCapFilters(CLICK_ONCE_PER_DAY_KEY1)
                                .build());
        CustomAudience customAudience2 =
                createCustomAudience(
                        mLocalhostBuyerDomain,
                        CUSTOM_AUDIENCE_SEQ_2,
                        BIDS_FOR_BUYER_2,
                        adCounterKeysForCa2,
                        adFiltersForCa2,
                        false);

        // We add permits to the semaphores when the MWS is called and remove them in the asserts
        Semaphore impressionReportingSemaphore = new Semaphore(0);
        Semaphore interactionReportingSemaphore = new Semaphore(0);

        MockWebServer server =
                getMockWebServer(
                        getDecisionLogicWithBeacons(),
                        getV3BiddingLogicJs(),
                        impressionReportingSemaphore,
                        interactionReportingSemaphore,
                        true);

        doNothing()
                .when(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), anyBoolean()));

        joinCustomAudienceAndAssertSuccess(customAudience1);
        joinCustomAudienceAndAssertSuccess(customAudience2);

        verify(() -> BackgroundFetchJobService.scheduleIfNeeded(any(), any(), eq(false)), times(2));

        // Run Ad Selection no filters active
        selectAdsAndReport(
                CommonFixture.getUri(
                        mLocalhostBuyerDomain.getAuthority(),
                        AD_URI_PREFIX + CUSTOM_AUDIENCE_SEQ_2 + "/ad3"),
                impressionReportingSemaphore,
                interactionReportingSemaphore);

        // Run Ad Selection with app install filtering
        registerForAppInstallFiltering();
        long adSelectionId =
                selectAdsAndReport(
                        CommonFixture.getUri(
                                mLocalhostBuyerDomain.getAuthority(),
                                AD_URI_PREFIX + CUSTOM_AUDIENCE_SEQ_2 + "/ad1"),
                        impressionReportingSemaphore,
                        interactionReportingSemaphore);

        // Run Ad Selection with both filters
        updateHistogramAndAssertSuccess(adSelectionId, FrequencyCapFilters.AD_EVENT_TYPE_CLICK);
        selectAdsAndReport(
                CommonFixture.getUri(
                        mLocalhostBuyerDomain.getAuthority(),
                        AD_URI_PREFIX + CUSTOM_AUDIENCE_SEQ_1 + "/ad2"),
                impressionReportingSemaphore,
                interactionReportingSemaphore);

        // Run Ad Selection with just fcap filtering
        deregisterForAppInstallFiltering();
        selectAdsAndReport(
                CommonFixture.getUri(
                        mLocalhostBuyerDomain.getAuthority(),
                        AD_URI_PREFIX + CUSTOM_AUDIENCE_SEQ_2 + "/ad2"),
                impressionReportingSemaphore,
                interactionReportingSemaphore);

        // 30 requests for the 3 auctions with both CAs and 9 requests for the auctions with one CA
        mockWebServerRule.verifyMockServerRequests(
                server,
                39,
                ImmutableList.of(
                        SELLER_DECISION_LOGIC_URI_PATH,
                        BUYER_BIDDING_LOGIC_URI_PATH + CUSTOM_AUDIENCE_SEQ_1,
                        BUYER_BIDDING_LOGIC_URI_PATH + CUSTOM_AUDIENCE_SEQ_2,
                        BUYER_TRUSTED_SIGNAL_URI_PATH,
                        SELLER_TRUSTED_SIGNAL_URI_PATH + SELLER_TRUSTED_SIGNAL_PARAMS),
                mRequestMatcherPrefixMatch);
    }

    private static class ResultCapturingCallback implements ICustomAudienceCallback {
        private boolean mIsSuccess;
        private Exception mException;

        public boolean isSuccess() {
            return mIsSuccess;
        }

        public Exception getException() {
            return mException;
        }

        @Override
        public void onSuccess() throws RemoteException {
            mIsSuccess = true;
        }

        @Override
        public void onFailure(FledgeErrorResponse responseParcel) throws RemoteException {
            mIsSuccess = false;
            mException = AdServicesStatusUtils.asException(responseParcel);
        }

        @Override
        public IBinder asBinder() {
            throw new RuntimeException("Should not be called.");
        }
    }

    private static class AppInstallResultCapturingCallback
            implements SetAppInstallAdvertisersCallback {
        private boolean mIsSuccess;
        private Exception mException;
        private final CountDownLatch mCountDownLatch;

        public boolean isSuccess() {
            return mIsSuccess;
        }

        public Exception getException() {
            return mException;
        }

        AppInstallResultCapturingCallback(CountDownLatch countDownLatch) {
            mCountDownLatch = countDownLatch;
        }

        @Override
        public void onSuccess() throws RemoteException {
            mIsSuccess = true;
            mCountDownLatch.countDown();
        }

        @Override
        public void onFailure(FledgeErrorResponse responseParcel) throws RemoteException {
            mIsSuccess = false;
            mException = AdServicesStatusUtils.asException(responseParcel);
            mCountDownLatch.countDown();
        }

        @Override
        public IBinder asBinder() {
            throw new RuntimeException("Should not be called.");
        }
    }

    static class AdSelectionTestCallback extends AdSelectionCallback.Stub {

        final CountDownLatch mCountDownLatch;
        boolean mIsSuccess = false;
        AdSelectionResponse mAdSelectionResponse;
        FledgeErrorResponse mFledgeErrorResponse;

        AdSelectionTestCallback(CountDownLatch countDownLatch) {
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

    public static class AdSelectionOverrideTestCallback extends AdSelectionOverrideCallback.Stub {
        private final CountDownLatch mCountDownLatch;
        boolean mIsSuccess = false;
        FledgeErrorResponse mFledgeErrorResponse;

        public AdSelectionOverrideTestCallback(CountDownLatch countDownLatch) {
            mCountDownLatch = countDownLatch;
        }

        @Override
        public void onSuccess() throws RemoteException {
            mIsSuccess = true;
            mCountDownLatch.countDown();
        }

        @Override
        public void onFailure(FledgeErrorResponse fledgeErrorResponse) throws RemoteException {
            mFledgeErrorResponse = fledgeErrorResponse;
            mCountDownLatch.countDown();
        }
    }

    public static class CustomAudienceOverrideTestCallback
            extends CustomAudienceOverrideCallback.Stub {
        private final CountDownLatch mCountDownLatch;
        boolean mIsSuccess = false;
        FledgeErrorResponse mFledgeErrorResponse;

        public CustomAudienceOverrideTestCallback(CountDownLatch countDownLatch) {
            mCountDownLatch = countDownLatch;
        }

        @Override
        public void onSuccess() throws RemoteException {
            mIsSuccess = true;
            mCountDownLatch.countDown();
        }

        @Override
        public void onFailure(FledgeErrorResponse fledgeErrorResponse) throws RemoteException {
            mFledgeErrorResponse = fledgeErrorResponse;
            mCountDownLatch.countDown();
        }
    }

    public static class ReportImpressionTestCallback extends ReportImpressionCallback.Stub {
        private final CountDownLatch mCountDownLatch;
        boolean mIsSuccess = false;
        FledgeErrorResponse mFledgeErrorResponse;

        public ReportImpressionTestCallback(CountDownLatch countDownLatch) {
            mCountDownLatch = countDownLatch;
        }

        @Override
        public void onSuccess() throws RemoteException {
            mIsSuccess = true;
            mCountDownLatch.countDown();
        }

        @Override
        public void onFailure(FledgeErrorResponse fledgeErrorResponse) throws RemoteException {
            mFledgeErrorResponse = fledgeErrorResponse;
            mCountDownLatch.countDown();
        }
    }

    static class ReportInteractionTestCallback extends ReportInteractionCallback.Stub {
        private final CountDownLatch mCountDownLatch;
        boolean mIsSuccess = false;
        FledgeErrorResponse mFledgeErrorResponse;

        ReportInteractionTestCallback(CountDownLatch countDownLatch) {
            mCountDownLatch = countDownLatch;
        }

        @Override
        public void onSuccess() throws RemoteException {
            mIsSuccess = true;
            mCountDownLatch.countDown();
        }

        @Override
        public void onFailure(FledgeErrorResponse fledgeErrorResponse) throws RemoteException {
            mFledgeErrorResponse = fledgeErrorResponse;
            mCountDownLatch.countDown();
        }
    }

    private Map<AdTechIdentifier, SignedContextualAds> createContextualAds() {
        Map<AdTechIdentifier, SignedContextualAds> buyerContextualAds = new HashMap<>();

        // In order to meet ETLd+1 requirements creating Contextual ads with MockWebserver's host
        AdTechIdentifier buyer =
                AdTechIdentifier.fromString(
                        mockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH).getHost());
        SignedContextualAds contextualAds =
                SignedContextualAdsFixture.signContextualAds(
                        SignedContextualAdsFixture.aContextualAdsWithEmptySignatureBuilder(
                                        buyer, ImmutableList.of(100.0, 200.0, 300.0, 400.0, 500.0))
                                .setDecisionLogicUri(
                                        mockWebServerRule.uriForPath(
                                                BUYER_BIDDING_LOGIC_URI_PATH)));
        buyerContextualAds.put(buyer, contextualAds);
        return buyerContextualAds;
    }

    private static class FledgeE2ETestFlags implements Flags {
        private final boolean mIsGaUxEnabled;
        private final boolean mRegisterAdBeaconEnabled;
        private final boolean mFiltersEnabled;
        private final boolean mEnrollmentCheckDisabled;
        private final boolean mCpcBillingEnabled;
        private final boolean mDebugReportingEnabled;
        private final boolean mDataVersionHeaderEnabled;
        private final boolean mDebugReportsSendImmediately;
        private final boolean mAdIdKillSwitch;
        private final boolean mShouldUseUnifiedTables;

        FledgeE2ETestFlags(
                boolean isGaUxEnabled,
                boolean registerAdBeaconEnabled,
                boolean filtersEnabled,
                boolean enrollmentCheckDisabled,
                boolean cpcBillingEnabled,
                boolean debugReportingEnabled,
                boolean dataVersionHeaderEnabled,
                boolean debugReportsSendImmediately,
                boolean adIdKillSwitch,
                boolean shouldUseUnifiedTables) {
            mIsGaUxEnabled = isGaUxEnabled;
            mRegisterAdBeaconEnabled = registerAdBeaconEnabled;
            mFiltersEnabled = filtersEnabled;
            mEnrollmentCheckDisabled = enrollmentCheckDisabled;
            mCpcBillingEnabled = cpcBillingEnabled;
            mDebugReportingEnabled = debugReportingEnabled;
            mDataVersionHeaderEnabled = dataVersionHeaderEnabled;
            mDebugReportsSendImmediately = debugReportsSendImmediately;
            mAdIdKillSwitch = adIdKillSwitch;
            mShouldUseUnifiedTables = shouldUseUnifiedTables;
        }

        @Override
        public long getAdSelectionBiddingTimeoutPerCaMs() {
            return 10000;
        }

        @Override
        public long getAdSelectionScoringTimeoutMs() {
            return 10000;
        }

        @Override
        public long getAdSelectionOverallTimeoutMs() {
            return 300000;
        }

        @Override
        public boolean getEnforceIsolateMaxHeapSize() {
            return false;
        }

        @Override
        public boolean getGaUxFeatureEnabled() {
            return mIsGaUxEnabled;
        }

        @Override
        public float getSdkRequestPermitsPerSecond() {
            // Unlimited rate for unit tests to avoid flake in tests due to rate limiting
            return -1;
        }

        @Override
        public boolean getDisableFledgeEnrollmentCheck() {
            return mEnrollmentCheckDisabled;
        }

        @Override
        public boolean getFledgeRegisterAdBeaconEnabled() {
            return mRegisterAdBeaconEnabled;
        }

        @Override
        public boolean getFledgeAdSelectionFilteringEnabled() {
            return mFiltersEnabled;
        }

        @Override
        public boolean getFledgeAdSelectionContextualAdsEnabled() {
            return true;
        }

        @Override
        public long getFledgeAdSelectionBiddingLogicJsVersion() {
            return JsVersionRegister.BUYER_BIDDING_LOGIC_VERSION_VERSION_3;
        }

        @Override
        public boolean getFledgeCpcBillingEnabled() {
            return mCpcBillingEnabled;
        }

        @Override
        public boolean getFledgeFetchCustomAudienceEnabled() {
            return true;
        }

        @Override
        public boolean getFledgeEventLevelDebugReportingEnabled() {
            return mDebugReportingEnabled;
        }

        @Override
        public boolean getFledgeDataVersionHeaderEnabled() {
            return mDataVersionHeaderEnabled;
        }

        @Override
        public boolean getFledgeOnDeviceAuctionKillSwitch() {
            return false;
        }

        @Override
        public boolean getFledgeEventLevelDebugReportSendImmediately() {
            return mDebugReportsSendImmediately;
        }

        @Override
        public boolean getAdIdKillSwitch() {
            return mAdIdKillSwitch;
        }

        @Override
        public boolean getFledgeOnDeviceAuctionShouldUseUnifiedTables() {
            return mShouldUseUnifiedTables;
        }

        @Override
        public boolean getFledgeAuctionServerEnabledForReportEvent() {
            return false;
        }
    }
}
