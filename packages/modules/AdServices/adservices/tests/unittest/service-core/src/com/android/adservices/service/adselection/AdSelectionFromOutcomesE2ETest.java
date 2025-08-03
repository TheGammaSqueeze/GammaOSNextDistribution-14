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

import static android.adservices.adselection.AdSelectionFromOutcomesConfigFixture.SAMPLE_SELLER;
import static android.adservices.common.AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
import static android.adservices.common.AdServicesStatusUtils.STATUS_INVALID_ARGUMENT;

import static com.android.adservices.data.adselection.AdSelectionDatabase.DATABASE_NAME;
import static com.android.adservices.common.CommonFlagsValues.EXTENDED_FLEDGE_AD_SELECTION_FROM_OUTCOMES_OVERALL_TIMEOUT_MS;
import static com.android.adservices.common.CommonFlagsValues.EXTENDED_FLEDGE_AD_SELECTION_SELECTING_OUTCOME_TIMEOUT_MS;
import static com.android.adservices.service.adselection.AdOutcomeSelectorImpl.OUTCOME_SELECTION_JS_RETURNED_UNEXPECTED_RESULT;
import static com.android.adservices.service.adselection.OutcomeSelectionRunner.SELECTED_OUTCOME_MUST_BE_ONE_OF_THE_INPUTS;
import static com.android.adservices.service.adselection.PrebuiltLogicGenerator.AD_OUTCOME_SELECTION_WATERFALL_MEDIATION_TRUNCATION;
import static com.android.adservices.service.adselection.PrebuiltLogicGenerator.AD_SELECTION_FROM_OUTCOMES_USE_CASE;
import static com.android.adservices.service.adselection.PrebuiltLogicGenerator.AD_SELECTION_PREBUILT_SCHEMA;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTest.DB_AD_SELECTION_FILE_SIZE;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.times;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.when;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;

import android.adservices.adselection.AdSelectionCallback;
import android.adservices.adselection.AdSelectionFromOutcomesConfig;
import android.adservices.adselection.AdSelectionFromOutcomesConfigFixture;
import android.adservices.adselection.AdSelectionFromOutcomesInput;
import android.adservices.adselection.AdSelectionResponse;
import android.adservices.adselection.AdSelectionService;
import android.adservices.adselection.CustomAudienceSignalsFixture;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CallerMetadata;
import android.adservices.common.CallingAppUidSupplierProcessImpl;
import android.adservices.common.CommonFixture;
import android.adservices.common.FledgeErrorResponse;
import android.adservices.http.MockWebServerRule;
import android.content.Context;
import android.net.Uri;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.FlakyTest;

import com.android.adservices.MockWebServerRuleFactory;
import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.common.SupportedByConditionRule;
import com.android.adservices.common.WebViewSupportUtil;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.DbTestUtil;
import com.android.adservices.data.adselection.AdSelectionDatabase;
import com.android.adservices.data.adselection.AdSelectionDebugReportDao;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.adselection.AdSelectionServerDatabase;
import com.android.adservices.data.adselection.AppInstallDao;
import com.android.adservices.data.adselection.CustomAudienceSignals;
import com.android.adservices.data.adselection.DBAdSelection;
import com.android.adservices.data.adselection.FrequencyCapDao;
import com.android.adservices.data.adselection.SharedStorageDatabase;
import com.android.adservices.data.adselection.datahandlers.AdSelectionInitialization;
import com.android.adservices.data.adselection.datahandlers.AdSelectionResultBidAndUri;
import com.android.adservices.data.adselection.datahandlers.WinningCustomAudience;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.CustomAudienceDatabase;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.data.encryptionkey.EncryptionKeyDao;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.signals.EncodedPayloadDao;
import com.android.adservices.data.signals.ProtectedSignalsDatabase;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.adselection.encryption.ObliviousHttpEncryptor;
import com.android.adservices.service.common.AdSelectionServiceFilter;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.common.RetryStrategyFactory;
import com.android.adservices.service.common.Throttler;
import com.android.adservices.service.common.cache.CacheProviderFactory;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.devapi.DevContextFilter;
import com.android.adservices.service.kanon.KAnonSignJoinFactory;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.AdServicesStatsLog;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import com.google.mockwebserver.Dispatcher;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;
import com.google.mockwebserver.RecordedRequest;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.Spy;
import org.mockito.quality.Strictness;

import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

public class AdSelectionFromOutcomesE2ETest {
    private static final int CALLER_UID = Process.myUid();
    private static final String SELECTION_PICK_HIGHEST_LOGIC_JS_PATH = "/selectionPickHighestJS/";
    private static final String SELECTION_PICK_NONE_LOGIC_JS_PATH = "/selectionPickNoneJS/";
    static final String SELECTION_WATERFALL_LOGIC_JS_PATH = "/selectionWaterfallJS/";
    private static final String SELECTION_FAULTY_LOGIC_JS_PATH = "/selectionFaultyJS/";
    private static final String SELECTION_PICK_HIGHEST_LOGIC_JS =
            "function selectOutcome(outcomes, selection_signals) {\n"
                    + "    let max_bid = 0;\n"
                    + "    let winner_outcome = null;\n"
                    + "    for (let outcome of outcomes) {\n"
                    + "        if (outcome.bid > max_bid) {\n"
                    + "            max_bid = outcome.bid;\n"
                    + "            winner_outcome = outcome;\n"
                    + "        }\n"
                    + "    }\n"
                    + "    return {'status': 0, 'result': winner_outcome};\n"
                    + "}";
    private static final String SELECTION_PICK_NONE_LOGIC_JS =
            "function selectOutcome(outcomes, selection_signals) {\n"
                    + "    return {'status': 0, 'result': null};\n"
                    + "}";
    static final String SELECTION_WATERFALL_LOGIC_JS =
            "function selectOutcome(outcomes, selection_signals) {\n"
                    + "    if (outcomes.length != 1 || selection_signals.bid_floor =="
                    + " undefined) return null;\n"
                    + "\n"
                    + "    const outcome_1p = outcomes[0];\n"
                    + "    return {'status': 0, 'result': (outcome_1p.bid >"
                    + " selection_signals.bid_floor) ? outcome_1p : null};\n"
                    + "}";
    private static final String SELECTION_FAULTY_LOGIC_JS =
            "function selectOutcome(outcomes, selection_signals) {\n"
                    + "    return {'status': 0, 'result': {\"id\": outcomes[0].id + 1, \"bid\": "
                    + "outcomes[0].bid}};\n"
                    + "}";
    static final String BID_FLOOR_SELECTION_SIGNAL_TEMPLATE = "{\"bid_floor\":%s}";

    private static final AdTechIdentifier SELLER_INCONSISTENT_WITH_SELECTION_URI =
            AdTechIdentifier.fromString("inconsistent.developer.android.com");
    private static final long BINDER_ELAPSED_TIME_MS = 100L;
    private static final String CALLER_PACKAGE_NAME = CommonFixture.TEST_PACKAGE_NAME;
    private static final long AD_SELECTION_ID_1 = 12345L;
    private static final long AD_SELECTION_ID_2 = 123456L;
    private static final long AD_SELECTION_ID_3 = 1234567L;
    private static final long AD_SELECTION_ID_4 = 12345678L;

    private final AdServicesLogger mAdServicesLoggerMock =
            ExtendedMockito.mock(AdServicesLoggerImpl.class);
    private final Flags mFlags = new AdSelectionFromOutcomesE2ETest.TestFlags();

    @Spy private Context mContextSpy = ApplicationProvider.getApplicationContext();

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    // Every test in this class requires that the JS Sandbox be available. The JS Sandbox
    // availability depends on an external component (the system webview) being higher than a
    // certain minimum version.
    @Rule(order = 1)
    public final SupportedByConditionRule webViewSupportsJSSandbox =
            WebViewSupportUtil.createJSSandboxAvailableRule(
                    ApplicationProvider.getApplicationContext());

    @Rule(order = 2)
    public MockWebServerRule mMockWebServerRule = MockWebServerRuleFactory.createForHttps();

    // Mocking DevContextFilter to test behavior with and without override api authorization
    @Mock DevContextFilter mDevContextFilter;
    @Mock CallerMetadata mMockCallerMetadata;
    @Mock private File mMockDBAdSelectionFile;
    @Mock private ConsentManager mConsentManagerMock;
    @Mock private KAnonSignJoinFactory mUnusedKAnonSignJoinFactory;

    FledgeAuthorizationFilter mFledgeAuthorizationFilter =
            new FledgeAuthorizationFilter(
                    mContextSpy.getPackageManager(),
                    new EnrollmentDao(mContextSpy, DbTestUtil.getSharedDbHelperForTest(), mFlags),
                    mAdServicesLoggerMock);

    private MockitoSession mStaticMockSession = null;
    private ExecutorService mLightweightExecutorService;
    private ExecutorService mBackgroundExecutorService;
    private ScheduledThreadPoolExecutor mScheduledExecutor;
    private CustomAudienceDao mCustomAudienceDao;
    private EncodedPayloadDao mEncodedPayloadDao;
    private AppInstallDao mAppInstallDao;
    private FrequencyCapDao mFrequencyCapDao;
    @Spy private AdSelectionEntryDao mAdSelectionEntryDaoSpy;
    private EnrollmentDao mEnrollmentDao;
    private EncryptionKeyDao mEncryptionKeyDao;
    private AdServicesHttpsClient mAdServicesHttpsClient;
    private AdSelectionServiceImpl mAdSelectionService;
    private Dispatcher mDispatcher;
    private AdFilteringFeatureFactory mAdFilteringFeatureFactory;
    @Mock private AdSelectionServiceFilter mAdSelectionServiceFilter;
    @Mock private ObliviousHttpEncryptor mObliviousHttpEncryptor;
    private MultiCloudSupportStrategy mMultiCloudSupportStrategy;
    @Mock private AdSelectionDebugReportDao mAdSelectionDebugReportDao;
    @Mock private AdIdFetcher mAdIdFetcher;
    private RetryStrategyFactory mRetryStrategyFactory;

    @Before
    public void setUp() throws Exception {
        mAdSelectionEntryDaoSpy =
                Room.inMemoryDatabaseBuilder(mContextSpy, AdSelectionDatabase.class)
                        .build()
                        .adSelectionEntryDao();

        // Test applications don't have the required permissions to read config P/H flags, and
        // injecting mocked flags everywhere is annoying and non-trivial for static methods
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(FlagsFactory.class)
                        .initMocks(this)
                        .strictness(Strictness.LENIENT)
                        .startMocking();

        // Initialize dependencies for the AdSelectionService
        mLightweightExecutorService = AdServicesExecutors.getLightWeightExecutor();
        mBackgroundExecutorService = AdServicesExecutors.getBackgroundExecutor();
        mScheduledExecutor = AdServicesExecutors.getScheduler();
        mCustomAudienceDao =
                Room.inMemoryDatabaseBuilder(mContextSpy, CustomAudienceDatabase.class)
                        .addTypeConverter(new DBCustomAudience.Converters(true, true))
                        .build()
                        .customAudienceDao();
        mEncodedPayloadDao =
                Room.inMemoryDatabaseBuilder(mContextSpy, ProtectedSignalsDatabase.class)
                        .build()
                        .getEncodedPayloadDao();
        SharedStorageDatabase sharedDb =
                Room.inMemoryDatabaseBuilder(mContextSpy, SharedStorageDatabase.class).build();
        mAppInstallDao = sharedDb.appInstallDao();
        mFrequencyCapDao = sharedDb.frequencyCapDao();
        AdSelectionServerDatabase serverDb =
                Room.inMemoryDatabaseBuilder(mContextSpy, AdSelectionServerDatabase.class).build();
        mEnrollmentDao = EnrollmentDao.getInstance(mContextSpy);
        mEncryptionKeyDao = EncryptionKeyDao.getInstance(mContextSpy);
        mMultiCloudSupportStrategy =
                MultiCloudTestStrategyFactory.getDisabledTestStrategy(mObliviousHttpEncryptor);
        mAdFilteringFeatureFactory =
                new AdFilteringFeatureFactory(mAppInstallDao, mFrequencyCapDao, mFlags);
        mAdServicesHttpsClient =
                new AdServicesHttpsClient(
                        AdServicesExecutors.getBlockingExecutor(),
                        CacheProviderFactory.createNoOpCache());
        mRetryStrategyFactory = RetryStrategyFactory.createInstanceForTesting();

        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());
        when(mMockCallerMetadata.getBinderElapsedTimestamp())
                .thenReturn(SystemClock.elapsedRealtime() - BINDER_ELAPSED_TIME_MS);
        // Create an instance of AdSelection Service with real dependencies
        mAdSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDaoSpy,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mAdServicesHttpsClient,
                        mDevContextFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mContextSpy,
                        mAdServicesLoggerMock,
                        mFlags,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilter,
                        mAdSelectionServiceFilter,
                        mAdFilteringFeatureFactory,
                        mConsentManagerMock,
                        mMultiCloudSupportStrategy,
                        mAdSelectionDebugReportDao,
                        mAdIdFetcher,
                        mUnusedKAnonSignJoinFactory,
                        false,
                        mRetryStrategyFactory);

        // Create a dispatcher that helps map a request -> response in mockWebServer
        mDispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        switch (request.getPath()) {
                            case SELECTION_PICK_HIGHEST_LOGIC_JS_PATH:
                                return new MockResponse().setBody(SELECTION_PICK_HIGHEST_LOGIC_JS);
                            case SELECTION_PICK_NONE_LOGIC_JS_PATH:
                                return new MockResponse().setBody(SELECTION_PICK_NONE_LOGIC_JS);
                            case SELECTION_WATERFALL_LOGIC_JS_PATH:
                                return new MockResponse().setBody(SELECTION_WATERFALL_LOGIC_JS);
                            case SELECTION_FAULTY_LOGIC_JS_PATH:
                                return new MockResponse().setBody(SELECTION_FAULTY_LOGIC_JS);
                            default:
                                return new MockResponse().setResponseCode(404);
                        }
                    }
                };

        when(mContextSpy.getDatabasePath(DATABASE_NAME)).thenReturn(mMockDBAdSelectionFile);
        when(mMockDBAdSelectionFile.length()).thenReturn(DB_AD_SELECTION_FILE_SIZE);
        doNothing()
                .when(mAdSelectionServiceFilter)
                .filterRequest(
                        SAMPLE_SELLER,
                        CALLER_PACKAGE_NAME,
                        false,
                        true,
                        CALLER_UID,
                        AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS,
                        Throttler.ApiKey.FLEDGE_API_SELECT_ADS,
                        DevContext.createForDevOptionsDisabled());
    }

    @After
    public void tearDown() {
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
    }

    @Test
    public void testSelectAdsFromOutcomesPickHighestSuccess() throws Exception {
        doReturn(new AdSelectionFromOutcomesE2ETest.TestFlags()).when(FlagsFactory::getFlags);
        MockWebServer server = mMockWebServerRule.startMockWebServer(mDispatcher);
        final String selectionLogicPath = SELECTION_PICK_HIGHEST_LOGIC_JS_PATH;

        Map<Long, Double> adSelectionIdToBidMap =
                Map.of(
                        AD_SELECTION_ID_1, 10.0,
                        AD_SELECTION_ID_2, 20.0,
                        AD_SELECTION_ID_3, 30.0);
        persistAdSelectionEntryDaoResults(adSelectionIdToBidMap);

        AdSelectionFromOutcomesConfig config =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig(
                        List.of(AD_SELECTION_ID_1, AD_SELECTION_ID_2, AD_SELECTION_ID_3),
                        AdSelectionSignals.EMPTY,
                        mMockWebServerRule.uriForPath(selectionLogicPath));

        AdSelectionFromOutcomesE2ETest.AdSelectionFromOutcomesTestCallback resultsCallback =
                invokeSelectAdsFromOutcomes(mAdSelectionService, config, CALLER_PACKAGE_NAME);

        assertThat(resultsCallback.mIsSuccess).isTrue();
        assertThat(resultsCallback.mAdSelectionResponse).isNotNull();
        assertEquals(resultsCallback.mAdSelectionResponse.getAdSelectionId(), AD_SELECTION_ID_3);
        mMockWebServerRule.verifyMockServerRequests(
                server, 1, Collections.singletonList(selectionLogicPath), String::equals);
    }

    @Test
    public void testSelectAdsFromOutcomesPickHighestSuccessDifferentTables() throws Exception {
        doReturn(new AdSelectionFromOutcomesE2ETest.TestFlags()).when(FlagsFactory::getFlags);

        Flags auctionServerEnabledFlags =
                new TestFlags() {
                    @Override
                    public boolean getFledgeAuctionServerEnabledForSelectAdsMediation() {
                        return true;
                    }
                };

        MockWebServer server = mMockWebServerRule.startMockWebServer(mDispatcher);
        final String selectionLogicPath = SELECTION_PICK_HIGHEST_LOGIC_JS_PATH;

        // On-device ids
        Map<Long, Double> onDeviceAdSelectionIdToBid =
                Map.of(
                        AD_SELECTION_ID_1, 10.0,
                        AD_SELECTION_ID_2, 20.0,
                        AD_SELECTION_ID_3, 30.0);
        persistAdSelectionEntryDaoResults(onDeviceAdSelectionIdToBid);
        Map<Long, Double> serverAdSelectionIdToBid = Map.of(AD_SELECTION_ID_4, 40.0);
        persistAdSelectionEntryInServerAuctionTable(serverAdSelectionIdToBid);

        List<Long> adSelectionIds = new ArrayList<>(onDeviceAdSelectionIdToBid.keySet());
        adSelectionIds.addAll(serverAdSelectionIdToBid.keySet());
        AdSelectionFromOutcomesConfig config =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig(
                        adSelectionIds,
                        AdSelectionSignals.EMPTY,
                        mMockWebServerRule.uriForPath(selectionLogicPath));

        AdSelectionService adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDaoSpy,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mAdServicesHttpsClient,
                        mDevContextFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mContextSpy,
                        mAdServicesLoggerMock,
                        auctionServerEnabledFlags,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilter,
                        mAdSelectionServiceFilter,
                        mAdFilteringFeatureFactory,
                        mConsentManagerMock,
                        mMultiCloudSupportStrategy,
                        mAdSelectionDebugReportDao,
                        mAdIdFetcher,
                        mUnusedKAnonSignJoinFactory,
                        false,
                        mRetryStrategyFactory);

        AdSelectionFromOutcomesE2ETest.AdSelectionFromOutcomesTestCallback resultsCallback =
                invokeSelectAdsFromOutcomes(adSelectionService, config, CALLER_PACKAGE_NAME);

        assertThat(resultsCallback.mIsSuccess).isTrue();
        assertThat(resultsCallback.mAdSelectionResponse).isNotNull();
        assertEquals(AD_SELECTION_ID_3, resultsCallback.mAdSelectionResponse.getAdSelectionId());
        mMockWebServerRule.verifyMockServerRequests(
                server, 1, Collections.singletonList(selectionLogicPath), String::equals);
    }

    @Test
    @FlakyTest(bugId = 315521295)
    public void testSelectAdsFromOutcomesPickHighestSuccessUnifiedTables() throws Exception {
        doReturn(new AdSelectionFromOutcomesE2ETest.TestFlags()).when(FlagsFactory::getFlags);
        MockWebServer server = mMockWebServerRule.startMockWebServer(mDispatcher);
        final String selectionLogicPath = SELECTION_PICK_HIGHEST_LOGIC_JS_PATH;

        Map<Long, Double> adSelectionIdToBidMap =
                Map.of(
                        AD_SELECTION_ID_1, 10.0,
                        AD_SELECTION_ID_2, 20.0,
                        AD_SELECTION_ID_3, 30.0);
        persistAdSelectionEntryInUnifiedTable(adSelectionIdToBidMap);

        AdSelectionFromOutcomesConfig config =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig(
                        List.of(AD_SELECTION_ID_1, AD_SELECTION_ID_2, AD_SELECTION_ID_3),
                        AdSelectionSignals.EMPTY,
                        mMockWebServerRule.uriForPath(selectionLogicPath));

        mAdSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDaoSpy,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mAdServicesHttpsClient,
                        mDevContextFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mContextSpy,
                        mAdServicesLoggerMock,
                        mFlags,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilter,
                        mAdSelectionServiceFilter,
                        mAdFilteringFeatureFactory,
                        mConsentManagerMock,
                        mMultiCloudSupportStrategy,
                        mAdSelectionDebugReportDao,
                        mAdIdFetcher,
                        mUnusedKAnonSignJoinFactory,
                        true,
                        mRetryStrategyFactory);

        AdSelectionFromOutcomesE2ETest.AdSelectionFromOutcomesTestCallback resultsCallback =
                invokeSelectAdsFromOutcomes(mAdSelectionService, config, CALLER_PACKAGE_NAME);

        assertThat(resultsCallback.mIsSuccess).isTrue();
        assertThat(resultsCallback.mAdSelectionResponse).isNotNull();
        assertEquals(AD_SELECTION_ID_3, resultsCallback.mAdSelectionResponse.getAdSelectionId());
        verify(mAdSelectionEntryDaoSpy, times(1)).getWinningBidAndUriForIdsUnifiedTables(any());
        verify(mAdSelectionEntryDaoSpy, times(1))
                .getAdSelectionIdsWithCallerPackageNameFromUnifiedTable(any(), any());
        mMockWebServerRule.verifyMockServerRequests(
                server, 1, Collections.singletonList(selectionLogicPath), String::equals);
    }

    @Test
    public void testSelectAdsFromOutcomesWaterfallMediationAdBidHigherThanBidFloorSuccess()
            throws Exception {
        doReturn(new AdSelectionFromOutcomesE2ETest.TestFlags()).when(FlagsFactory::getFlags);
        MockWebServer server = mMockWebServerRule.startMockWebServer(mDispatcher);
        final String selectionLogicPath = SELECTION_WATERFALL_LOGIC_JS_PATH;

        Map<Long, Double> adSelectionIdToBidMap = Map.of(AD_SELECTION_ID_1, 10.0);
        persistAdSelectionEntryDaoResults(adSelectionIdToBidMap);

        AdSelectionFromOutcomesConfig config =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig(
                        Collections.singletonList(AD_SELECTION_ID_1),
                        AdSelectionSignals.fromString(
                                String.format(BID_FLOOR_SELECTION_SIGNAL_TEMPLATE, 9)),
                        mMockWebServerRule.uriForPath(selectionLogicPath));

        AdSelectionFromOutcomesE2ETest.AdSelectionFromOutcomesTestCallback resultsCallback =
                invokeSelectAdsFromOutcomes(mAdSelectionService, config, CALLER_PACKAGE_NAME);

        assertThat(resultsCallback.mIsSuccess).isTrue();
        assertThat(resultsCallback.mAdSelectionResponse).isNotNull();
        assertEquals(resultsCallback.mAdSelectionResponse.getAdSelectionId(), AD_SELECTION_ID_1);
        mMockWebServerRule.verifyMockServerRequests(
                server, 1, Collections.singletonList(selectionLogicPath), String::equals);
    }

    @Test
    public void testSelectAdsFromOutcomesWaterfallMediationPrebuiltUriSuccess() throws Exception {
        doReturn(new AdSelectionFromOutcomesE2ETest.TestFlags()).when(FlagsFactory::getFlags);
        MockWebServer server = mMockWebServerRule.startMockWebServer(mDispatcher);

        Map<Long, Double> adSelectionIdToBidMap = Map.of(AD_SELECTION_ID_1, 10.0);
        persistAdSelectionEntryDaoResults(adSelectionIdToBidMap);

        String paramKey = "bidFloor";
        String paramValue = "bid_floor";
        Uri prebuiltUri =
                Uri.parse(
                        String.format(
                                "%s://%s/%s/?%s=%s",
                                AD_SELECTION_PREBUILT_SCHEMA,
                                AD_SELECTION_FROM_OUTCOMES_USE_CASE,
                                AD_OUTCOME_SELECTION_WATERFALL_MEDIATION_TRUNCATION,
                                paramKey,
                                paramValue));

        AdSelectionFromOutcomesConfig config =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig(
                        Collections.singletonList(AD_SELECTION_ID_1),
                        AdSelectionSignals.fromString(
                                String.format(BID_FLOOR_SELECTION_SIGNAL_TEMPLATE, 9)),
                        prebuiltUri);

        AdSelectionFromOutcomesE2ETest.AdSelectionFromOutcomesTestCallback resultsCallback =
                invokeSelectAdsFromOutcomes(mAdSelectionService, config, CALLER_PACKAGE_NAME);

        assertThat(resultsCallback.mIsSuccess).isTrue();
        assertThat(resultsCallback.mAdSelectionResponse).isNotNull();
        assertEquals(resultsCallback.mAdSelectionResponse.getAdSelectionId(), AD_SELECTION_ID_1);
        mMockWebServerRule.verifyMockServerRequests(
                server, 0, Collections.emptyList(), String::equals);
    }

    @Test
    public void testSelectAdsFromOutcomesWaterfallMediationAdBidLowerThanBidFloorSuccess()
            throws Exception {
        doReturn(new AdSelectionFromOutcomesE2ETest.TestFlags()).when(FlagsFactory::getFlags);
        MockWebServer server = mMockWebServerRule.startMockWebServer(mDispatcher);
        final String selectionLogicPath = SELECTION_WATERFALL_LOGIC_JS_PATH;

        Map<Long, Double> adSelectionIdToBidMap = Map.of(AD_SELECTION_ID_1, 10.0);
        persistAdSelectionEntryDaoResults(adSelectionIdToBidMap);

        AdSelectionFromOutcomesConfig config =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig(
                        Collections.singletonList(AD_SELECTION_ID_1),
                        AdSelectionSignals.fromString(
                                String.format(BID_FLOOR_SELECTION_SIGNAL_TEMPLATE, 11)),
                        mMockWebServerRule.uriForPath(selectionLogicPath));

        AdSelectionFromOutcomesE2ETest.AdSelectionFromOutcomesTestCallback resultsCallback =
                invokeSelectAdsFromOutcomes(mAdSelectionService, config, CALLER_PACKAGE_NAME);

        assertThat(resultsCallback.mIsSuccess).isTrue();
        assertThat(resultsCallback.mAdSelectionResponse).isNull();
        mMockWebServerRule.verifyMockServerRequests(
                server, 1, Collections.singletonList(selectionLogicPath), String::equals);
    }

    @Test
    public void testSelectAdsFromOutcomesReturnsNullSuccess() throws Exception {
        doReturn(new AdSelectionFromOutcomesE2ETest.TestFlags()).when(FlagsFactory::getFlags);
        MockWebServer server = mMockWebServerRule.startMockWebServer(mDispatcher);
        final String selectionLogicPath = SELECTION_PICK_NONE_LOGIC_JS_PATH;

        Map<Long, Double> adSelectionIdToBidMap =
                Map.of(
                        AD_SELECTION_ID_1, 10.0,
                        AD_SELECTION_ID_2, 20.0,
                        AD_SELECTION_ID_3, 30.0);
        persistAdSelectionEntryDaoResults(adSelectionIdToBidMap);

        AdSelectionFromOutcomesConfig config =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig(
                        List.of(AD_SELECTION_ID_1, AD_SELECTION_ID_2, AD_SELECTION_ID_3),
                        AdSelectionSignals.EMPTY,
                        mMockWebServerRule.uriForPath(selectionLogicPath));

        AdSelectionFromOutcomesE2ETest.AdSelectionFromOutcomesTestCallback resultsCallback =
                invokeSelectAdsFromOutcomes(mAdSelectionService, config, CALLER_PACKAGE_NAME);

        assertThat(resultsCallback.mIsSuccess).isTrue();
        assertThat(resultsCallback.mAdSelectionResponse).isNull();
        mMockWebServerRule.verifyMockServerRequests(
                server, 1, Collections.singletonList(selectionLogicPath), String::equals);
    }

    @Test
    public void testSelectAdsFromOutcomesInvalidAdSelectionConfigFromOutcomes() throws Exception {
        doReturn(new AdSelectionFromOutcomesE2ETest.TestFlags()).when(FlagsFactory::getFlags);
        MockWebServer server = mMockWebServerRule.startMockWebServer(mDispatcher);
        final String selectionLogicPath = "/unreachableLogicJS/";

        long adSelectionId1 = 12345L;
        long adSelectionId2 = 123456L;
        long adSelectionId3 = 1234567L;

        Map<Long, Double> adSelectionIdToBidMap =
                Map.of(
                        adSelectionId1, 10.0,
                        adSelectionId2, 20.0,
                        adSelectionId3, 30.0);
        persistAdSelectionEntryDaoResults(adSelectionIdToBidMap);

        AdSelectionFromOutcomesConfig config =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig(
                        SELLER_INCONSISTENT_WITH_SELECTION_URI,
                        List.of(adSelectionId1, adSelectionId2, adSelectionId3),
                        AdSelectionSignals.EMPTY,
                        mMockWebServerRule.uriForPath(selectionLogicPath));

        AdSelectionFromOutcomesE2ETest.AdSelectionFromOutcomesTestCallback resultsCallback =
                invokeSelectAdsFromOutcomes(mAdSelectionService, config, CALLER_PACKAGE_NAME);

        assertThat(resultsCallback.mIsSuccess).isFalse();
        assertThat(resultsCallback.mFledgeErrorResponse).isNotNull();
        assertThat(resultsCallback.mFledgeErrorResponse.getStatusCode())
                .isEqualTo(STATUS_INVALID_ARGUMENT);
        mMockWebServerRule.verifyMockServerRequests(
                server, 0, Collections.emptyList(), String::equals);
    }

    @Test
    public void testSelectAdsFromOutcomesJsReturnsFaultyAdSelectionIdFailure() throws Exception {
        doReturn(new AdSelectionFromOutcomesE2ETest.TestFlags()).when(FlagsFactory::getFlags);
        MockWebServer server = mMockWebServerRule.startMockWebServer(mDispatcher);
        final String selectionLogicPath = SELECTION_FAULTY_LOGIC_JS_PATH;

        Map<Long, Double> adSelectionIdToBidMap =
                Map.of(
                        AD_SELECTION_ID_1, 10.0,
                        AD_SELECTION_ID_2, 20.0,
                        AD_SELECTION_ID_3, 30.0);
        persistAdSelectionEntryDaoResults(adSelectionIdToBidMap);

        AdSelectionFromOutcomesConfig config =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig(
                        List.of(AD_SELECTION_ID_1, AD_SELECTION_ID_2, AD_SELECTION_ID_3),
                        AdSelectionSignals.EMPTY,
                        mMockWebServerRule.uriForPath(selectionLogicPath));

        AdSelectionFromOutcomesE2ETest.AdSelectionFromOutcomesTestCallback resultsCallback =
                invokeSelectAdsFromOutcomes(mAdSelectionService, config, CALLER_PACKAGE_NAME);

        assertThat(resultsCallback.mIsSuccess).isFalse();
        assertThat(resultsCallback.mFledgeErrorResponse).isNotNull();
        assertThat(resultsCallback.mFledgeErrorResponse.getStatusCode())
                .isEqualTo(STATUS_INTERNAL_ERROR);
        assertThat(resultsCallback.mFledgeErrorResponse.getErrorMessage())
                .contains(SELECTED_OUTCOME_MUST_BE_ONE_OF_THE_INPUTS);
        mMockWebServerRule.verifyMockServerRequests(
                server, 1, Collections.singletonList(selectionLogicPath), String::equals);
    }

    @Test
    @FlakyTest(bugId = 315521295)
    public void testSelectAdsFromOutcomesWaterfallMalformedPrebuiltUriFailed() throws Exception {
        doReturn(new AdSelectionFromOutcomesE2ETest.TestFlags()).when(FlagsFactory::getFlags);
        MockWebServer server = mMockWebServerRule.startMockWebServer(mDispatcher);

        Map<Long, Double> adSelectionIdToBidMap = Map.of(AD_SELECTION_ID_1, 10.0);
        persistAdSelectionEntryDaoResults(adSelectionIdToBidMap);

        String unknownUseCase = "unknown-usecase";
        Uri prebuiltUri =
                Uri.parse(String.format("%s://%s/", AD_SELECTION_PREBUILT_SCHEMA, unknownUseCase));

        AdSelectionFromOutcomesConfig config =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig(
                        Collections.singletonList(AD_SELECTION_ID_1),
                        AdSelectionSignals.EMPTY,
                        prebuiltUri);
        AdSelectionFromOutcomesE2ETest.AdSelectionFromOutcomesTestCallback resultsCallback =
                invokeSelectAdsFromOutcomes(mAdSelectionService, config, CALLER_PACKAGE_NAME);

        assertThat(resultsCallback.mIsSuccess).isFalse();
        assertThat(resultsCallback.mFledgeErrorResponse).isNotNull();
        assertThat(resultsCallback.mFledgeErrorResponse.getStatusCode())
                .isEqualTo(STATUS_INTERNAL_ERROR);
        assertThat(resultsCallback.mFledgeErrorResponse.getErrorMessage())
                .contains(OUTCOME_SELECTION_JS_RETURNED_UNEXPECTED_RESULT);
        mMockWebServerRule.verifyMockServerRequests(
                server, 0, Collections.emptyList(), String::equals);
    }

    private AdSelectionFromOutcomesE2ETest.AdSelectionFromOutcomesTestCallback
            invokeSelectAdsFromOutcomes(
                    AdSelectionService adSelectionService,
                    AdSelectionFromOutcomesConfig adSelectionFromOutcomesConfig,
                    String callerPackageName)
                    throws InterruptedException, RemoteException {

        CountDownLatch countdownLatch = new CountDownLatch(1);
        AdSelectionFromOutcomesE2ETest.AdSelectionFromOutcomesTestCallback adSelectionTestCallback =
                new AdSelectionFromOutcomesE2ETest.AdSelectionFromOutcomesTestCallback(
                        countdownLatch);

        AdSelectionFromOutcomesInput input =
                new AdSelectionFromOutcomesInput.Builder()
                        .setAdSelectionFromOutcomesConfig(adSelectionFromOutcomesConfig)
                        .setCallerPackageName(callerPackageName)
                        .build();

        adSelectionService.selectAdsFromOutcomes(
                input, mMockCallerMetadata, adSelectionTestCallback);
        adSelectionTestCallback.mCountDownLatch.await();
        return adSelectionTestCallback;
    }

    private void persistAdSelectionEntryDaoResults(Map<Long, Double> adSelectionIdToBidMap) {
        final Uri biddingLogicUri1 = Uri.parse("https://www.domain.com/logic/1");
        final Uri renderUri = Uri.parse("https://www.domain.com/advert/");
        final Instant activationTime = Instant.now();
        final String contextualSignals = "contextual_signals";
        final CustomAudienceSignals customAudienceSignals =
                CustomAudienceSignalsFixture.aCustomAudienceSignals();

        for (Map.Entry<Long, Double> entry : adSelectionIdToBidMap.entrySet()) {
            final DBAdSelection dbAdSelectionEntry =
                    new DBAdSelection.Builder()
                            .setAdSelectionId(entry.getKey())
                            .setCustomAudienceSignals(customAudienceSignals)
                            .setBuyerContextualSignals(contextualSignals)
                            .setBiddingLogicUri(biddingLogicUri1)
                            .setWinningAdRenderUri(renderUri)
                            .setWinningAdBid(entry.getValue())
                            .setCreationTimestamp(activationTime)
                            .setCallerPackageName(CALLER_PACKAGE_NAME)
                            .build();
            mAdSelectionEntryDaoSpy.persistAdSelection(dbAdSelectionEntry);
        }
    }

    private void persistAdSelectionEntryInServerAuctionTable(
            Map<Long, Double> adSelectionIdToBidMap) {
        final Uri renderUri = Uri.parse("https://www.domain.com/advert/");
        for (Map.Entry<Long, Double> entry : adSelectionIdToBidMap.entrySet()) {
            final AdSelectionInitialization adSelectionInitialization =
                    AdSelectionInitialization.builder()
                            .setSeller(SAMPLE_SELLER)
                            .setCallerPackageName(CALLER_PACKAGE_NAME)
                            .setCreationInstant(Instant.now())
                            .build();
            final AdSelectionResultBidAndUri idWithBidAndRenderUri =
                    AdSelectionResultBidAndUri.builder()
                            .setAdSelectionId(entry.getKey())
                            .setWinningAdBid(entry.getValue())
                            .setWinningAdRenderUri(renderUri)
                            .build();
            mAdSelectionEntryDaoSpy.persistAdSelectionInitialization(
                    idWithBidAndRenderUri.getAdSelectionId(), adSelectionInitialization);
        }
    }

    private void persistAdSelectionEntryInUnifiedTable(Map<Long, Double> adSelectionIdToBidMap) {
        final Uri renderUri = Uri.parse("https://www.domain.com/advert/");
        final CustomAudienceSignals customAudienceSignals =
                CustomAudienceSignalsFixture.aCustomAudienceSignals();
        for (Map.Entry<Long, Double> entry : adSelectionIdToBidMap.entrySet()) {
            final AdSelectionInitialization adSelectionInitialization =
                    AdSelectionInitialization.builder()
                            .setSeller(SAMPLE_SELLER)
                            .setCallerPackageName(CALLER_PACKAGE_NAME)
                            .setCreationInstant(Instant.now())
                            .build();
            final AdSelectionResultBidAndUri idWithBidAndRenderUri =
                    AdSelectionResultBidAndUri.builder()
                            .setAdSelectionId(entry.getKey())
                            .setWinningAdBid(entry.getValue())
                            .setWinningAdRenderUri(renderUri)
                            .build();
            final WinningCustomAudience winningCustomAudience =
                    WinningCustomAudience.builder()
                            .setOwner(customAudienceSignals.getOwner())
                            .setName(customAudienceSignals.getName())
                            .build();
            mAdSelectionEntryDaoSpy.persistAdSelectionInitialization(
                    idWithBidAndRenderUri.getAdSelectionId(), adSelectionInitialization);
            mAdSelectionEntryDaoSpy.persistAdSelectionResultForCustomAudience(
                    entry.getKey(),
                    idWithBidAndRenderUri,
                    customAudienceSignals.getBuyer(),
                    winningCustomAudience);
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

    private static class TestFlags implements Flags {
        @Override
        public boolean getEnforceIsolateMaxHeapSize() {
            return false;
        }

        @Override
        public boolean getEnforceForegroundStatusForFledgeRunAdSelection() {
            return true;
        }

        @Override
        public boolean getEnforceForegroundStatusForFledgeReportImpression() {
            return true;
        }

        @Override
        public boolean getEnforceForegroundStatusForFledgeOverrides() {
            return true;
        }

        @Override
        public boolean getDisableFledgeEnrollmentCheck() {
            return true;
        }

        @Override
        public long getAdSelectionSelectingOutcomeTimeoutMs() {
            return EXTENDED_FLEDGE_AD_SELECTION_SELECTING_OUTCOME_TIMEOUT_MS;
        }

        @Override
        public long getAdSelectionFromOutcomesOverallTimeoutMs() {
            return EXTENDED_FLEDGE_AD_SELECTION_FROM_OUTCOMES_OVERALL_TIMEOUT_MS;
        }

        @Override
        public float getSdkRequestPermitsPerSecond() {
            // Unlimited rate for unit tests to avoid flake in tests due to rate
            // limiting
            return -1;
        }

        @Override
        public boolean getFledgeAdSelectionFilteringEnabled() {
            return false;
        }

        @Override
        public boolean getFledgeAdSelectionPrebuiltUriEnabled() {
            return true;
        }
    }
}
