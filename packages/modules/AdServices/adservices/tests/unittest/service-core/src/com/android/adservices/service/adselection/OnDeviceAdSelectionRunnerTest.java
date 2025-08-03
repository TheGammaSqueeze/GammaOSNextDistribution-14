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

import static android.adservices.adselection.CustomAudienceBiddingInfoFixture.DATA_VERSION_1;
import static android.adservices.adselection.CustomAudienceBiddingInfoFixture.DATA_VERSION_2;
import static android.adservices.adselection.SignedContextualAdsFixture.signContextualAds;
import static android.adservices.common.AdServicesStatusUtils.ILLEGAL_STATE_BACKGROUND_CALLER_ERROR_MESSAGE;
import static android.adservices.common.AdServicesStatusUtils.RATE_LIMIT_REACHED_ERROR_MESSAGE;
import static android.adservices.common.AdServicesStatusUtils.STATUS_BACKGROUND_CALLER;
import static android.adservices.common.AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
import static android.adservices.common.AdServicesStatusUtils.STATUS_RATE_LIMIT_REACHED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;
import static android.adservices.common.AdServicesStatusUtils.STATUS_TIMEOUT;
import static android.adservices.common.AdServicesStatusUtils.STATUS_UNSET;
import static android.adservices.common.AdServicesStatusUtils.STATUS_USER_CONSENT_REVOKED;
import static android.adservices.common.CommonFixture.TEST_PACKAGE_NAME;

import static com.android.adservices.data.adselection.AdSelectionDatabase.DATABASE_NAME;
import static com.android.adservices.common.CommonFlagsValues.EXTENDED_FLEDGE_AD_SELECTION_BIDDING_TIMEOUT_PER_CA_MS;
import static com.android.adservices.common.CommonFlagsValues.EXTENDED_FLEDGE_AD_SELECTION_FROM_OUTCOMES_OVERALL_TIMEOUT_MS;
import static com.android.adservices.common.CommonFlagsValues.EXTENDED_FLEDGE_AD_SELECTION_OVERALL_TIMEOUT_MS;
import static com.android.adservices.common.CommonFlagsValues.EXTENDED_FLEDGE_AD_SELECTION_SCORING_TIMEOUT_MS;
import static com.android.adservices.common.CommonFlagsValues.EXTENDED_FLEDGE_AD_SELECTION_SELECTING_OUTCOME_TIMEOUT_MS;
import static com.android.adservices.common.CommonFlagsValues.EXTENDED_FLEDGE_REPORT_IMPRESSION_OVERALL_TIMEOUT_MS;
import static com.android.adservices.service.adselection.AdSelectionRunner.AD_SELECTION_ERROR_PATTERN;
import static com.android.adservices.service.adselection.AdSelectionRunner.AD_SELECTION_TIMED_OUT;
import static com.android.adservices.service.adselection.AdSelectionRunner.ERROR_AD_SELECTION_FAILURE;
import static com.android.adservices.service.adselection.AdSelectionRunner.ERROR_NO_BUYERS_OR_CONTEXTUAL_ADS_AVAILABLE;
import static com.android.adservices.service.adselection.AdSelectionRunner.ERROR_NO_CA_AND_CONTEXTUAL_ADS_AVAILABLE;
import static com.android.adservices.service.adselection.AdSelectionRunner.ERROR_NO_VALID_BIDS_OR_CONTEXTUAL_ADS_FOR_SCORING;
import static com.android.adservices.service.adselection.AdSelectionRunner.ERROR_NO_WINNING_AD_FOUND;
import static com.android.adservices.service.adselection.AdSelectionRunner.ON_DEVICE_AUCTION_KILL_SWITCH_ENABLED;
import static com.android.adservices.service.adselection.AdSelectionScriptEngine.NUM_BITS_STOCHASTIC_ROUNDING;
import static com.android.adservices.service.adselection.signature.ProtectedAudienceSignatureManager.PUBLIC_TEST_KEY_STRING;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTest.BIDDING_STAGE_END_TIMESTAMP;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTest.BIDDING_STAGE_START_TIMESTAMP;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTest.DB_AD_SELECTION_FILE_SIZE;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTest.GET_BUYERS_CUSTOM_AUDIENCE_END_TIMESTAMP;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTest.GET_BUYERS_CUSTOM_AUDIENCE_LATENCY_MS;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTest.IS_RMKT_ADS_WON;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTest.IS_RMKT_ADS_WON_UNSET;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTest.PERSIST_AD_SELECTION_END_TIMESTAMP;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTest.PERSIST_AD_SELECTION_START_TIMESTAMP;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTest.RUN_AD_BIDDING_END_TIMESTAMP;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTest.RUN_AD_BIDDING_LATENCY_MS;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTest.RUN_AD_BIDDING_START_TIMESTAMP;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTest.RUN_AD_SELECTION_INTERNAL_FINAL_LATENCY_MS;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTest.RUN_AD_SELECTION_OVERALL_LATENCY_MS;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTest.START_ELAPSED_TIMESTAMP;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTest.STOP_ELAPSED_TIMESTAMP;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTest.TOTAL_BIDDING_STAGE_LATENCY_IN_MS;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTest.sCallerMetadata;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyLong;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.eq;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.never;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.times;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verifyZeroInteractions;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.when;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;

import android.adservices.adselection.AdBiddingOutcomeFixture;
import android.adservices.adselection.AdSelectionCallback;
import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionConfigFixture;
import android.adservices.adselection.AdSelectionInput;
import android.adservices.adselection.AdSelectionResponse;
import android.adservices.adselection.SignedContextualAds;
import android.adservices.adselection.SignedContextualAdsFixture;
import android.adservices.common.AdDataFixture;
import android.adservices.common.AdFilters;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.AppInstallFilters;
import android.adservices.common.CommonFixture;
import android.adservices.common.FledgeErrorResponse;
import android.adservices.customaudience.CustomAudienceFixture;
import android.adservices.exceptions.AdServicesException;
import android.content.Context;
import android.net.Uri;
import android.os.LimitExceededException;
import android.os.Process;
import android.os.RemoteException;
import android.webkit.WebView;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.common.DBAdDataFixture;
import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.common.SupportedByConditionRule;
import com.android.adservices.common.WebViewSupportUtil;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.customaudience.DBCustomAudienceFixture;
import com.android.adservices.data.adselection.AdSelectionDatabase;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.adselection.DBAdSelection;
import com.android.adservices.data.adselection.DBAdSelectionEntry;
import com.android.adservices.data.adselection.DBAdSelectionHistogramInfo;
import com.android.adservices.data.adselection.DBReportingComputationInfo;
import com.android.adservices.data.adselection.datahandlers.AdSelectionInitialization;
import com.android.adservices.data.adselection.datahandlers.WinningCustomAudience;
import com.android.adservices.data.common.DBAdData;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.CustomAudienceDatabase;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.data.encryptionkey.EncryptionKeyDao;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.AdSelectionServiceFilter;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.common.FrequencyCapAdDataValidator;
import com.android.adservices.service.common.FrequencyCapAdDataValidatorNoOpImpl;
import com.android.adservices.service.common.Throttler;
import com.android.adservices.service.common.cache.CacheProviderFactory;
import com.android.adservices.service.common.httpclient.AdServicesHttpClientRequest;
import com.android.adservices.service.common.httpclient.AdServicesHttpClientResponse;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.encryptionkey.EncryptionKey;
import com.android.adservices.service.enrollment.EnrollmentData;
import com.android.adservices.service.exception.FilterException;
import com.android.adservices.service.stats.AdSelectionExecutionLogger;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.AdServicesStatsLog;
import com.android.adservices.service.stats.RunAdBiddingProcessReportedStats;
import com.android.adservices.service.stats.RunAdSelectionProcessReportedStats;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.Spy;
import org.mockito.internal.stubbing.answers.AnswersWithDelay;
import org.mockito.internal.stubbing.answers.Returns;
import org.mockito.quality.Strictness;

import java.io.File;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * This test covers strictly the unit of {@link AdSelectionRunner} The dependencies in this test are
 * mocked and provide expected mock responses when invoked with desired input
 */
public class OnDeviceAdSelectionRunnerTest {
    private static final AdTechIdentifier BUYER_1 = AdSelectionConfigFixture.BUYER_1;
    private static final AdTechIdentifier BUYER_2 = AdSelectionConfigFixture.BUYER_2;
    private static final Long AD_SELECTION_ID = 1234L;
    private static final String ERROR_INVALID_JSON = "Invalid Json Exception";
    private static final int CALLER_UID = Process.myUid();
    private static final String MY_APP_PACKAGE_NAME = TEST_PACKAGE_NAME;

    private static final AdTechIdentifier SELLER_VALID =
            AdTechIdentifier.fromString("developer.android.com");
    private static final String BUYER_BIDDING_LOGIC_URI_PATH = "/buyer/bidding/logic/";
    private static final String DECISION_LOGIC_PATH = "/test/decisions_logic_uris";
    private static final Uri DECISION_LOGIC_URI =
            CommonFixture.getUri(SELLER_VALID, DECISION_LOGIC_PATH);
    private static final String TRUSTED_SIGNALS_PATH = "/test/trusted_signals_uri";
    private static final Uri TRUSTED_SIGNALS_URI =
            CommonFixture.getUri(SELLER_VALID, TRUSTED_SIGNALS_PATH);
    private static final int PERSIST_AD_SELECTION_LATENCY_MS =
            (int) (PERSIST_AD_SELECTION_END_TIMESTAMP - PERSIST_AD_SELECTION_START_TIMESTAMP);
    private static final String BUYER_DECISION_LOGIC_JS =
            "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,"
                    + " contextual_signals, custom_audience_signals) { \n"
                    + " return {'status': 0, 'results': {'reporting_uri': '"
                    + " buyerReportingUri "
                    + "' } };\n"
                    + "}";
    private MockitoSession mStaticMockSession = null;
    @Mock private AdsScoreGenerator mMockAdsScoreGenerator;
    @Mock private AdSelectionIdGenerator mMockAdSelectionIdGenerator;
    @Spy private Clock mClockSpy = Clock.systemUTC();
    @Mock private com.android.adservices.shared.util.Clock mAdSelectionExecutionLoggerClockMock;
    @Mock private File mMockDBAdSelectionFile;
    @Mock private AdFilterer mMockAdFilterer;
    @Mock private AdServicesHttpsClient mMockHttpClient;
    @Mock private AdCounterKeyCopier mAdCounterKeyCopierMock;
    @Mock private FrequencyCapAdDataValidator mFrequencyCapAdDataValidatorMock;
    @Mock private AdCounterHistogramUpdater mAdCounterHistogramUpdaterMock;
    @Mock private DebugReporting mDebugReportingMock;
    @Mock private DebugReportSenderStrategy mDebugReportSenderMock;
    @Mock private EnrollmentDao mEnrollmentDaoMock;
    @Mock private EncryptionKeyDao mEncryptionKeyDaoMock;

    @Captor
    ArgumentCaptor<RunAdSelectionProcessReportedStats>
            mRunAdSelectionProcessReportedStatsArgumentCaptor;

    @Captor
    ArgumentCaptor<RunAdBiddingProcessReportedStats>
            mRunAdBiddingProcessReportedStatsArgumentCaptor;

    @Captor ArgumentCaptor<AdSelectionConfig> mAdSelectionConfigArgumentCaptor;

    @Mock private PerBuyerBiddingRunner mPerBuyerBiddingRunnerMock;

    private Flags mFlags = new OnDeviceAdSelectionRunnerTestFlags();
    @Spy private Context mContextSpy = ApplicationProvider.getApplicationContext();
    private AdServicesHttpsClient mAdServicesHttpsClient;
    private ExecutorService mLightweightExecutorService;
    private ExecutorService mBackgroundExecutorService;
    private ScheduledThreadPoolExecutor mScheduledExecutor;
    private CustomAudienceDao mCustomAudienceDao;
    @Spy private AdSelectionEntryDao mAdSelectionEntryDaoSpy;
    private AdServicesLogger mAdServicesLoggerMock =
            ExtendedMockito.mock(AdServicesLoggerImpl.class);
    private List<AdTechIdentifier> mCustomAudienceBuyers;
    private AdSelectionConfig.Builder mAdSelectionConfigBuilder;

    private DBCustomAudience mDBCustomAudienceForBuyer1;
    private DBCustomAudience mDBCustomAudienceForBuyer2;
    private List<DBCustomAudience> mBuyerCustomAudienceList;

    private AdBiddingOutcome mAdBiddingOutcomeForBuyer1;
    private AdBiddingOutcome mAdBiddingOutcomeForBuyer2;
    private List<AdBiddingOutcome> mAdBiddingOutcomeList;

    private AdScoringOutcome mAdScoringOutcomeForBuyer1;
    private AdScoringOutcome mAdScoringOutcomeForBuyer2;
    private List<AdScoringOutcome> mAdScoringOutcomeList;

    private AdSelectionRunner mAdSelectionRunner;
    private AdSelectionExecutionLogger mAdSelectionExecutionLogger;

    // Use no-op implementations and test specific cases with the mocked objects
    private final AdFilterer mAdFilterer = new AdFiltererNoOpImpl();
    private final AdCounterKeyCopier mAdCounterKeyCopier = new AdCounterKeyCopierNoOpImpl();
    private final FrequencyCapAdDataValidator mFrequencyCapAdDataValidator =
            new FrequencyCapAdDataValidatorNoOpImpl();
    private final AdCounterHistogramUpdater mAdCounterHistogramUpdater =
            new AdCounterHistogramUpdaterNoOpImpl();

    @Mock AdSelectionServiceFilter mAdSelectionServiceFilterMock;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    // Every test in this class requires that the JS Sandbox be available. The JS Sandbox
    // availability depends on an external component (the system webview) being higher than a
    // certain minimum version.
    @Rule(order = 1)
    public final SupportedByConditionRule webViewSupportsJSSandbox =
            WebViewSupportUtil.createJSSandboxAvailableRule(
                    ApplicationProvider.getApplicationContext());

    @Before
    public void setUp() {
        // Initializing up here so object is spied
        mAdSelectionEntryDaoSpy =
                Room.inMemoryDatabaseBuilder(mContextSpy, AdSelectionDatabase.class)
                        .build()
                        .adSelectionEntryDao();

        // Test applications don't have the required permissions to read config P/H flags, and
        // injecting mocked flags everywhere is annoying and non-trivial for static methods
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(FlagsFactory.class)
                        .spyStatic(WebView.class)
                        .strictness(Strictness.LENIENT)
                        .initMocks(this)
                        .startMocking();

        mLightweightExecutorService = AdServicesExecutors.getLightWeightExecutor();
        mBackgroundExecutorService = AdServicesExecutors.getBackgroundExecutor();
        mScheduledExecutor = AdServicesExecutors.getScheduler();
        mAdServicesHttpsClient =
                new AdServicesHttpsClient(
                        AdServicesExecutors.getBlockingExecutor(),
                        CacheProviderFactory.createNoOpCache());
        mCustomAudienceDao =
                Room.inMemoryDatabaseBuilder(mContextSpy, CustomAudienceDatabase.class)
                        .addTypeConverter(new DBCustomAudience.Converters(true, true))
                        .build()
                        .customAudienceDao();

        mCustomAudienceBuyers = Arrays.asList(BUYER_1, BUYER_2);
        mAdSelectionConfigBuilder =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setSeller(SELLER_VALID)
                        .setCustomAudienceBuyers(mCustomAudienceBuyers)
                        .setDecisionLogicUri(DECISION_LOGIC_URI)
                        .setTrustedScoringSignalsUri(TRUSTED_SIGNALS_URI);

        mDBCustomAudienceForBuyer1 = createDBCustomAudience(BUYER_1);
        mDBCustomAudienceForBuyer2 = createDBCustomAudience(BUYER_2);
        mBuyerCustomAudienceList =
                Arrays.asList(mDBCustomAudienceForBuyer1, mDBCustomAudienceForBuyer2);

        mAdBiddingOutcomeForBuyer1 =
                AdBiddingOutcomeFixture.anAdBiddingOutcomeBuilder(BUYER_1, 1.0).build();
        mAdBiddingOutcomeForBuyer2 =
                AdBiddingOutcomeFixture.anAdBiddingOutcomeBuilder(BUYER_2, 2.0).build();
        mAdBiddingOutcomeList =
                Arrays.asList(mAdBiddingOutcomeForBuyer1, mAdBiddingOutcomeForBuyer2);

        mAdScoringOutcomeForBuyer1 =
                AdScoringOutcomeFixture.anAdScoringBuilder(BUYER_1, 2.0).build();
        mAdScoringOutcomeForBuyer2 =
                AdScoringOutcomeFixture.anAdScoringBuilder(BUYER_2, 3.0).build();
        mAdScoringOutcomeList =
                Arrays.asList(mAdScoringOutcomeForBuyer1, mAdScoringOutcomeForBuyer2);

        when(mContextSpy.getDatabasePath(DATABASE_NAME)).thenReturn(mMockDBAdSelectionFile);
        when(mMockDBAdSelectionFile.length()).thenReturn(DB_AD_SELECTION_FILE_SIZE);
        when(mDebugReportingMock.isEnabled()).thenReturn(false);
        when(mDebugReportingMock.getSenderStrategy()).thenReturn(mDebugReportSenderMock);

        doNothing()
                .when(mAdSelectionServiceFilterMock)
                .filterRequest(
                        SELLER_VALID,
                        MY_APP_PACKAGE_NAME,
                        true,
                        true,
                        CALLER_UID,
                        AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS,
                        Throttler.ApiKey.FLEDGE_API_SELECT_ADS,
                        DevContext.createForDevOptionsDisabled());
    }

    private DBCustomAudience createDBCustomAudience(final AdTechIdentifier buyer) {
        return DBCustomAudienceFixture.getValidBuilderByBuyer(buyer)
                .setOwner(buyer.toString() + CustomAudienceFixture.VALID_OWNER)
                .setName(buyer + CustomAudienceFixture.VALID_NAME)
                .setCreationTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                .setLastAdsAndBiddingDataUpdatedTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                .build();
    }

    private DBCustomAudience createDBCustomAudienceNoFilters(final AdTechIdentifier buyer) {
        return DBCustomAudienceFixture.getValidBuilderByBuyerNoFilters(buyer)
                .setOwner(buyer.toString() + CustomAudienceFixture.VALID_OWNER)
                .setName(buyer + CustomAudienceFixture.VALID_NAME)
                .setCreationTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                .setLastAdsAndBiddingDataUpdatedTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                .build();
    }

    @Test
    public void testRunAdSelectionSuccess() throws AdServicesException {
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();
        verifyAndSetupCommonSuccessScenario(adSelectionConfig);
        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mContextSpy,
                        mCustomAudienceDao,
                        mAdSelectionEntryDaoSpy,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClockSpy,
                        mAdServicesLoggerMock,
                        mFlags,
                        CALLER_UID,
                        mAdSelectionServiceFilterMock,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunnerMock,
                        mAdFilterer,
                        mAdCounterKeyCopier,
                        mAdCounterHistogramUpdater,
                        mFrequencyCapAdDataValidator,
                        mDebugReportingMock,
                        false);
        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);
        Instant adSelectionCreationTs = Clock.systemUTC().instant().truncatedTo(ChronoUnit.MILLIS);
        when(mClockSpy.instant()).thenReturn(adSelectionCreationTs);
        DBAdSelectionEntry expectedAdSelectionResult =
                new DBAdSelectionEntry.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setBiddingLogicUri(mDBCustomAudienceForBuyer2.getBiddingLogicUri())
                        .setWinningAdBid(
                                mAdScoringOutcomeForBuyer2.getAdWithScore().getAdWithBid().getBid())
                        .setCustomAudienceSignals(
                                mAdScoringOutcomeForBuyer2.getCustomAudienceSignals())
                        .setWinningAdRenderUri(
                                mAdScoringOutcomeForBuyer2
                                        .getAdWithScore()
                                        .getAdWithBid()
                                        .getAdData()
                                        .getRenderUri())
                        .setBuyerDecisionLogicJs(
                                mAdBiddingOutcomeForBuyer1
                                        .getCustomAudienceBiddingInfo()
                                        .getBuyerDecisionLogicJs())
                        // TODO(b/230569187) add contextual signals once supported in the main logic
                        .setBuyerContextualSignals("{}")
                        .setSellerContextualSignals("{}")
                        .setCreationTimestamp(adSelectionCreationTs)
                        .build();

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);

        verify(mMockAdsScoreGenerator).runAdScoring(mAdBiddingOutcomeList, adSelectionConfig);

        assertTrue(resultsCallback.mIsSuccess);
        assertEquals(
                expectedAdSelectionResult.getAdSelectionId(),
                resultsCallback.mAdSelectionResponse.getAdSelectionId());
        assertEquals(
                expectedAdSelectionResult.getWinningAdRenderUri(),
                resultsCallback.mAdSelectionResponse.getRenderUri());
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(AD_SELECTION_ID));
        assertEquals(
                expectedAdSelectionResult,
                mAdSelectionEntryDaoSpy.getAdSelectionEntityById(AD_SELECTION_ID));
        verifyLogForSuccessfulBiddingProcess(mAdBiddingOutcomeList);
        verifyLogForSuccessfulAdSelectionProcess();
        verifyDebugReportingWasNotCalled();
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        eq(RUN_AD_SELECTION_OVERALL_LATENCY_MS));
    }

    @Test
    public void testRunAdSelectionSuccessWithShouldUseUnifiedTablesFlag()
            throws AdServicesException {
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();
        verifyAndSetupCommonSuccessScenario(adSelectionConfig);

        // Init runner with unified tables boolean true
        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mContextSpy,
                        mCustomAudienceDao,
                        mAdSelectionEntryDaoSpy,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClockSpy,
                        mAdServicesLoggerMock,
                        mFlags,
                        CALLER_UID,
                        mAdSelectionServiceFilterMock,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunnerMock,
                        mAdFilterer,
                        mAdCounterKeyCopier,
                        mAdCounterHistogramUpdater,
                        mFrequencyCapAdDataValidator,
                        mDebugReportingMock,
                        true);
        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);
        Instant adSelectionCreationTs = Clock.systemUTC().instant().truncatedTo(ChronoUnit.MILLIS);
        when(mClockSpy.instant()).thenReturn(adSelectionCreationTs);

        DBReportingComputationInfo expectedDBComputationInfo =
                DBReportingComputationInfo.builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setBiddingLogicUri(mDBCustomAudienceForBuyer2.getBiddingLogicUri())
                        .setWinningAdBid(
                                mAdScoringOutcomeForBuyer2.getAdWithScore().getAdWithBid().getBid())
                        .setCustomAudienceSignals(
                                mAdScoringOutcomeForBuyer2.getCustomAudienceSignals())
                        .setWinningAdRenderUri(
                                mAdScoringOutcomeForBuyer2
                                        .getAdWithScore()
                                        .getAdWithBid()
                                        .getAdData()
                                        .getRenderUri())
                        .setBuyerDecisionLogicJs(
                                mAdBiddingOutcomeForBuyer1
                                        .getCustomAudienceBiddingInfo()
                                        .getBuyerDecisionLogicJs())
                        // TODO(b/230569187) add contextual signals once supported in the main logic
                        .setBuyerContextualSignals("{}")
                        .setSellerContextualSignals("{}")
                        .build();

        AdSelectionInitialization expectedAdSelectionInitialization =
                AdSelectionInitialization.builder()
                        .setCreationInstant(adSelectionCreationTs)
                        .setCallerPackageName(MY_APP_PACKAGE_NAME)
                        .setSeller(SELLER_VALID)
                        .build();

        WinningCustomAudience expectedWinningCustomAudience =
                WinningCustomAudience.builder()
                        .setName(mAdScoringOutcomeForBuyer2.getCustomAudienceSignals().getName())
                        .setOwner(mAdScoringOutcomeForBuyer2.getCustomAudienceSignals().getOwner())
                        .build();

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);

        verify(mMockAdsScoreGenerator).runAdScoring(mAdBiddingOutcomeList, adSelectionConfig);

        assertTrue(resultsCallback.mIsSuccess);
        assertEquals(
                expectedDBComputationInfo.getAdSelectionId(),
                resultsCallback.mAdSelectionResponse.getAdSelectionId());
        assertEquals(
                expectedDBComputationInfo.getWinningAdRenderUri(),
                resultsCallback.mAdSelectionResponse.getRenderUri());
        assertTrue(mAdSelectionEntryDaoSpy.doesReportingComputationInfoExist(AD_SELECTION_ID));
        verify(mAdSelectionEntryDaoSpy).insertDBReportingComputationInfo(expectedDBComputationInfo);
        verify(mAdSelectionEntryDaoSpy)
                .persistAdSelectionInitialization(
                        AD_SELECTION_ID, expectedAdSelectionInitialization);
        verify(mAdSelectionEntryDaoSpy)
                .persistAdSelectionResultForCustomAudience(anyLong(), any(), any(), any());
        assertEquals(
                expectedDBComputationInfo,
                mAdSelectionEntryDaoSpy.getReportingComputationInfoById((AD_SELECTION_ID)));
        assertEquals(
                expectedAdSelectionInitialization,
                mAdSelectionEntryDaoSpy.getAdSelectionInitializationForId(AD_SELECTION_ID));
        assertEquals(
                expectedWinningCustomAudience,
                mAdSelectionEntryDaoSpy.getWinningCustomAudienceDataForId(AD_SELECTION_ID));

        // Verify old dao methods were not called
        verify(mAdSelectionEntryDaoSpy, never()).persistAdSelection(any());
        verify(mAdSelectionEntryDaoSpy, never()).persistBuyerDecisionLogic(any());
        assertFalse(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(AD_SELECTION_ID));
        assertNull(mAdSelectionEntryDaoSpy.getAdSelectionEntityById(AD_SELECTION_ID));

        verifyLogForSuccessfulBiddingProcess(mAdBiddingOutcomeList);
        verifyLogForSuccessfulAdSelectionProcess();
        verifyDebugReportingWasNotCalled();
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        eq(RUN_AD_SELECTION_OVERALL_LATENCY_MS));
    }

    @Test
    public void testRunAdSelectionSuccessWithBuyerContextualSignals() throws AdServicesException {
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();
        when(mClockSpy.instant()).thenReturn(Clock.systemUTC().instant());
        doReturn(mFlags).when(FlagsFactory::getFlags);
        AdCost adCost1 = new AdCost(1.5, NUM_BITS_STOCHASTIC_ROUNDING);
        BuyerContextualSignals buyerContextualSignals1 =
                BuyerContextualSignals.builder()
                        .setAdCost(adCost1)
                        .setDataVersion(DATA_VERSION_1)
                        .build();

        AdCost adCost2 = new AdCost(3.0, NUM_BITS_STOCHASTIC_ROUNDING);
        BuyerContextualSignals buyerContextualSignals2 =
                BuyerContextualSignals.builder()
                        .setAdCost(adCost2)
                        .setDataVersion(DATA_VERSION_2)
                        .build();

        mAdBiddingOutcomeForBuyer1 =
                AdBiddingOutcomeFixture.anAdBiddingOutcomeBuilderWithBuyerContextualSignals(
                                BUYER_1, 1.0, buyerContextualSignals1)
                        .build();
        mAdBiddingOutcomeForBuyer2 =
                AdBiddingOutcomeFixture.anAdBiddingOutcomeBuilderWithBuyerContextualSignals(
                                BUYER_2, 2.0, buyerContextualSignals2)
                        .build();
        mAdBiddingOutcomeList =
                Arrays.asList(mAdBiddingOutcomeForBuyer1, mAdBiddingOutcomeForBuyer2);

        mAdScoringOutcomeForBuyer1 =
                AdScoringOutcomeFixture.anAdScoringBuilderWithBuyerContextualSignals(
                                BUYER_1, 2.0, buyerContextualSignals1)
                        .build();
        mAdScoringOutcomeForBuyer2 =
                AdScoringOutcomeFixture.anAdScoringBuilderWithBuyerContextualSignals(
                                BUYER_2, 3.0, buyerContextualSignals2)
                        .build();
        mAdScoringOutcomeList =
                Arrays.asList(mAdScoringOutcomeForBuyer1, mAdScoringOutcomeForBuyer2);

        verifyAndSetupCommonSuccessScenario(adSelectionConfig);
        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mContextSpy,
                        mCustomAudienceDao,
                        mAdSelectionEntryDaoSpy,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClockSpy,
                        mAdServicesLoggerMock,
                        mFlags,
                        CALLER_UID,
                        mAdSelectionServiceFilterMock,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunnerMock,
                        mAdFilterer,
                        mAdCounterKeyCopier,
                        mAdCounterHistogramUpdater,
                        mFrequencyCapAdDataValidator,
                        mDebugReportingMock,
                        false);
        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);
        Instant adSelectionCreationTs = Clock.systemUTC().instant().truncatedTo(ChronoUnit.MILLIS);
        when(mClockSpy.instant()).thenReturn(adSelectionCreationTs);
        DBAdSelectionEntry expectedAdSelectionResult =
                new DBAdSelectionEntry.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setBiddingLogicUri(mDBCustomAudienceForBuyer2.getBiddingLogicUri())
                        .setWinningAdBid(
                                mAdScoringOutcomeForBuyer2.getAdWithScore().getAdWithBid().getBid())
                        .setCustomAudienceSignals(
                                mAdScoringOutcomeForBuyer2.getCustomAudienceSignals())
                        .setWinningAdRenderUri(
                                mAdScoringOutcomeForBuyer2
                                        .getAdWithScore()
                                        .getAdWithBid()
                                        .getAdData()
                                        .getRenderUri())
                        .setBuyerDecisionLogicJs(
                                mAdBiddingOutcomeForBuyer1
                                        .getCustomAudienceBiddingInfo()
                                        .getBuyerDecisionLogicJs())
                        // TODO(b/230569187) add contextual signals once supported in the main logic
                        .setBuyerContextualSignals(buyerContextualSignals2.toString())
                        .setSellerContextualSignals("{}")
                        .setCreationTimestamp(adSelectionCreationTs)
                        .build();

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);

        verify(mMockAdsScoreGenerator).runAdScoring(mAdBiddingOutcomeList, adSelectionConfig);

        assertTrue(resultsCallback.mIsSuccess);
        assertEquals(
                expectedAdSelectionResult.getAdSelectionId(),
                resultsCallback.mAdSelectionResponse.getAdSelectionId());
        assertEquals(
                expectedAdSelectionResult.getWinningAdRenderUri(),
                resultsCallback.mAdSelectionResponse.getRenderUri());
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(AD_SELECTION_ID));
        assertEquals(
                expectedAdSelectionResult,
                mAdSelectionEntryDaoSpy.getAdSelectionEntityById(AD_SELECTION_ID));
        verifyLogForSuccessfulBiddingProcess(mAdBiddingOutcomeList);
        verifyLogForSuccessfulAdSelectionProcess();
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        eq(RUN_AD_SELECTION_OVERALL_LATENCY_MS));
    }

    @Test
    public void testRunAdSelectionSuccessWithSellerDataVersionHeader() throws AdServicesException {
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();
        when(mClockSpy.instant()).thenReturn(Clock.systemUTC().instant());
        doReturn(mFlags).when(FlagsFactory::getFlags);

        mAdBiddingOutcomeForBuyer1 =
                AdBiddingOutcomeFixture.anAdBiddingOutcomeBuilder(BUYER_1, 1.0).build();
        mAdBiddingOutcomeForBuyer2 =
                AdBiddingOutcomeFixture.anAdBiddingOutcomeBuilder(BUYER_2, 2.0).build();
        mAdBiddingOutcomeList =
                Arrays.asList(mAdBiddingOutcomeForBuyer1, mAdBiddingOutcomeForBuyer2);

        SellerContextualSignals sellerContextualSignals =
                SellerContextualSignals.builder().setDataVersion(DATA_VERSION_1).build();

        mAdScoringOutcomeForBuyer1 =
                AdScoringOutcomeFixture.anAdScoringBuilderWithSellerContextualSignals(
                                BUYER_1, 2.0, sellerContextualSignals)
                        .build();
        mAdScoringOutcomeForBuyer2 =
                AdScoringOutcomeFixture.anAdScoringBuilderWithSellerContextualSignals(
                                BUYER_2, 3.0, sellerContextualSignals)
                        .build();
        mAdScoringOutcomeList =
                Arrays.asList(mAdScoringOutcomeForBuyer1, mAdScoringOutcomeForBuyer2);

        verifyAndSetupCommonSuccessScenario(adSelectionConfig);
        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mContextSpy,
                        mCustomAudienceDao,
                        mAdSelectionEntryDaoSpy,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClockSpy,
                        mAdServicesLoggerMock,
                        mFlags,
                        CALLER_UID,
                        mAdSelectionServiceFilterMock,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunnerMock,
                        mAdFilterer,
                        mAdCounterKeyCopier,
                        mAdCounterHistogramUpdater,
                        mFrequencyCapAdDataValidator,
                        mDebugReportingMock,
                        false);
        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);
        Instant adSelectionCreationTs = Clock.systemUTC().instant().truncatedTo(ChronoUnit.MILLIS);
        when(mClockSpy.instant()).thenReturn(adSelectionCreationTs);
        DBAdSelectionEntry expectedAdSelectionResult =
                new DBAdSelectionEntry.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setBiddingLogicUri(mDBCustomAudienceForBuyer2.getBiddingLogicUri())
                        .setWinningAdBid(
                                mAdScoringOutcomeForBuyer2.getAdWithScore().getAdWithBid().getBid())
                        .setCustomAudienceSignals(
                                mAdScoringOutcomeForBuyer2.getCustomAudienceSignals())
                        .setWinningAdRenderUri(
                                mAdScoringOutcomeForBuyer2
                                        .getAdWithScore()
                                        .getAdWithBid()
                                        .getAdData()
                                        .getRenderUri())
                        .setBuyerDecisionLogicJs(
                                mAdBiddingOutcomeForBuyer1
                                        .getCustomAudienceBiddingInfo()
                                        .getBuyerDecisionLogicJs())
                        // TODO(b/230569187) add contextual signals once supported in the main logic
                        .setBuyerContextualSignals("{}")
                        .setSellerContextualSignals(sellerContextualSignals.toString())
                        .setCreationTimestamp(adSelectionCreationTs)
                        .build();

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);

        verify(mMockAdsScoreGenerator).runAdScoring(mAdBiddingOutcomeList, adSelectionConfig);

        assertTrue(resultsCallback.mIsSuccess);
        assertEquals(
                expectedAdSelectionResult.getAdSelectionId(),
                resultsCallback.mAdSelectionResponse.getAdSelectionId());
        assertEquals(
                expectedAdSelectionResult.getWinningAdRenderUri(),
                resultsCallback.mAdSelectionResponse.getRenderUri());
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(AD_SELECTION_ID));
        assertEquals(
                expectedAdSelectionResult,
                mAdSelectionEntryDaoSpy.getAdSelectionEntityById(AD_SELECTION_ID));
        verifyLogForSuccessfulBiddingProcess(mAdBiddingOutcomeList);
        verifyLogForSuccessfulAdSelectionProcess();
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        eq(RUN_AD_SELECTION_OVERALL_LATENCY_MS));
    }

    @Test
    public void testRunAdSelectionSuccessFilteringDisabled() throws AdServicesException {
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();
        when(mClockSpy.instant()).thenReturn(Clock.systemUTC().instant());
        Flags flagsWithFilteringDisabled =
                new OnDeviceAdSelectionRunnerTestFlags() {
                    @Override
                    public long getAdSelectionOverallTimeoutMs() {
                        return 300;
                    }

                    @Override
                    public boolean getDisableFledgeEnrollmentCheck() {
                        return true;
                    }

                    @Override
                    public boolean getFledgeAdSelectionFilteringEnabled() {
                        return false;
                    }
                };
        doReturn(flagsWithFilteringDisabled).when(FlagsFactory::getFlags);

        DBCustomAudience dbCustomAudienceNoFilterBuyer1 = createDBCustomAudienceNoFilters(BUYER_1);
        DBCustomAudience dbCustomAudienceNoFilterBuyer2 = createDBCustomAudienceNoFilters(BUYER_2);

        // Getting BiddingOutcome-forBuyerX corresponding to each CA-forBuyerX
        doReturn(ImmutableList.of(Futures.immediateFuture(mAdBiddingOutcomeForBuyer1)))
                .when(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_1,
                        Collections.singletonList(dbCustomAudienceNoFilterBuyer1),
                        flagsWithFilteringDisabled.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        doReturn(ImmutableList.of(Futures.immediateFuture(mAdBiddingOutcomeForBuyer2)))
                .when(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_2,
                        Collections.singletonList(dbCustomAudienceNoFilterBuyer2),
                        flagsWithFilteringDisabled.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);

        // Getting ScoringOutcome-ForBuyerX corresponding to each BiddingOutcome-forBuyerX
        when(mMockAdsScoreGenerator.runAdScoring(mAdBiddingOutcomeList, adSelectionConfig))
                .thenReturn((FluentFuture.from(Futures.immediateFuture(mAdScoringOutcomeList))));

        mockAdSelectionExecutionLoggerSpyWithSuccessAdSelection();

        when(mMockAdSelectionIdGenerator.generateId()).thenReturn(AD_SELECTION_ID);
        assertFalse(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(AD_SELECTION_ID));
        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mContextSpy,
                        mCustomAudienceDao,
                        mAdSelectionEntryDaoSpy,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClockSpy,
                        mAdServicesLoggerMock,
                        flagsWithFilteringDisabled,
                        CALLER_UID,
                        mAdSelectionServiceFilterMock,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunnerMock,
                        new AdFiltererNoOpImpl(),
                        new AdCounterKeyCopierNoOpImpl(),
                        mAdCounterHistogramUpdater,
                        mFrequencyCapAdDataValidator,
                        mDebugReportingMock,
                        false);
        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dbCustomAudienceNoFilterBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dbCustomAudienceNoFilterBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);
        Instant adSelectionCreationTs = Clock.systemUTC().instant().truncatedTo(ChronoUnit.MILLIS);
        when(mClockSpy.instant()).thenReturn(adSelectionCreationTs);
        DBAdSelectionEntry expectedAdSelectionResult =
                new DBAdSelectionEntry.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setBiddingLogicUri(dbCustomAudienceNoFilterBuyer2.getBiddingLogicUri())
                        .setWinningAdBid(
                                mAdScoringOutcomeForBuyer2.getAdWithScore().getAdWithBid().getBid())
                        .setCustomAudienceSignals(
                                mAdScoringOutcomeForBuyer2.getCustomAudienceSignals())
                        .setWinningAdRenderUri(
                                mAdScoringOutcomeForBuyer2
                                        .getAdWithScore()
                                        .getAdWithBid()
                                        .getAdData()
                                        .getRenderUri())
                        .setBuyerDecisionLogicJs(
                                mAdBiddingOutcomeForBuyer1
                                        .getCustomAudienceBiddingInfo()
                                        .getBuyerDecisionLogicJs())
                        // TODO(b/230569187) add contextual signals once supported in the main logic
                        .setBuyerContextualSignals("{}")
                        .setSellerContextualSignals("{}")
                        .setCreationTimestamp(adSelectionCreationTs)
                        .build();

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_1,
                        Collections.singletonList(dbCustomAudienceNoFilterBuyer1),
                        flagsWithFilteringDisabled.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_2,
                        Collections.singletonList(dbCustomAudienceNoFilterBuyer2),
                        flagsWithFilteringDisabled.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);

        verify(mMockAdsScoreGenerator).runAdScoring(mAdBiddingOutcomeList, adSelectionConfig);

        assertTrue(resultsCallback.mIsSuccess);
        assertEquals(
                expectedAdSelectionResult.getAdSelectionId(),
                resultsCallback.mAdSelectionResponse.getAdSelectionId());
        assertEquals(
                expectedAdSelectionResult.getWinningAdRenderUri(),
                resultsCallback.mAdSelectionResponse.getRenderUri());
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(AD_SELECTION_ID));
        assertEquals(
                expectedAdSelectionResult,
                mAdSelectionEntryDaoSpy.getAdSelectionEntityById(AD_SELECTION_ID));
        DBAdSelectionHistogramInfo histogramInfo =
                mAdSelectionEntryDaoSpy.getAdSelectionHistogramInfoInOnDeviceTable(
                        resultsCallback.mAdSelectionResponse.getAdSelectionId(),
                        MY_APP_PACKAGE_NAME);
        assertThat(histogramInfo).isNotNull();
        assertThat(histogramInfo.getAdCounterKeys()).isNull();
        verifyLogForSuccessfulBiddingProcess(mAdBiddingOutcomeList);
        verifyLogForSuccessfulAdSelectionProcess();
        verifyDebugReportingWasNotCalled();
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        eq(RUN_AD_SELECTION_OVERALL_LATENCY_MS));
    }

    @Test
    public void testRunAdSelectionRetriesAdSelectionIdGenerationAfterCollision()
            throws AdServicesException {
        when(mClockSpy.instant()).thenReturn(Clock.systemUTC().instant());
        doReturn(mFlags).when(FlagsFactory::getFlags);
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        long existingAdSelectionId = 2345L;

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        // Getting BiddingOutcome-forBuyerX corresponding to each CA-forBuyerX
        doReturn(ImmutableList.of(Futures.immediateFuture(mAdBiddingOutcomeForBuyer1)))
                .when(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        doReturn(ImmutableList.of(Futures.immediateFuture(mAdBiddingOutcomeForBuyer2)))
                .when(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);

        // Getting ScoringOutcome-ForBuyerX corresponding to each BiddingOutcome-forBuyerX
        when(mMockAdsScoreGenerator.runAdScoring(mAdBiddingOutcomeList, adSelectionConfig))
                .thenReturn((FluentFuture.from(Futures.immediateFuture(mAdScoringOutcomeList))));

        Instant adSelectionCreationTs = Clock.systemUTC().instant().truncatedTo(ChronoUnit.MILLIS);
        when(mClockSpy.instant()).thenReturn(adSelectionCreationTs);

        DBAdSelectionEntry expectedAdSelectionResult =
                new DBAdSelectionEntry.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setBiddingLogicUri(mDBCustomAudienceForBuyer2.getBiddingLogicUri())
                        .setWinningAdBid(
                                mAdScoringOutcomeForBuyer2.getAdWithScore().getAdWithBid().getBid())
                        .setCustomAudienceSignals(
                                mAdScoringOutcomeForBuyer2.getCustomAudienceSignals())
                        .setWinningAdRenderUri(
                                mAdScoringOutcomeForBuyer2
                                        .getAdWithScore()
                                        .getAdWithBid()
                                        .getAdData()
                                        .getRenderUri())
                        .setBuyerDecisionLogicJs(
                                mAdBiddingOutcomeForBuyer1
                                        .getCustomAudienceBiddingInfo()
                                        .getBuyerDecisionLogicJs())
                        // TODO(b/230569187) add contextual signals once supported in the main logic
                        .setBuyerContextualSignals("{}")
                        .setSellerContextualSignals("{}")
                        .setCreationTimestamp(adSelectionCreationTs)
                        .build();

        DBAdSelection existingAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(existingAdSelectionId)
                        .setWinningAdBid(
                                mAdScoringOutcomeForBuyer2.getAdWithScore().getAdWithBid().getBid())
                        .setCustomAudienceSignals(
                                mAdScoringOutcomeForBuyer2.getCustomAudienceSignals())
                        .setWinningAdRenderUri(
                                mAdScoringOutcomeForBuyer2
                                        .getAdWithScore()
                                        .getAdWithBid()
                                        .getAdData()
                                        .getRenderUri())
                        // TODO(b/230569187) add contextual signals once supported in the main logic
                        .setBuyerContextualSignals("{}")
                        .setSellerContextualSignals("{}")
                        .setCreationTimestamp(adSelectionCreationTs)
                        .setCallerPackageName(MY_APP_PACKAGE_NAME)
                        .setBiddingLogicUri(mAdScoringOutcomeForBuyer2.getBiddingLogicUri())
                        .build();

        // Persist existing ad selection entry with existingAdSelectionId
        mAdSelectionEntryDaoSpy.persistAdSelection(existingAdSelection);

        mockAdSelectionExecutionLoggerSpyWithSuccessAdSelection();

        // Mock generator to return a collision on the first generation
        when(mMockAdSelectionIdGenerator.generateId())
                .thenReturn(existingAdSelectionId, existingAdSelectionId, AD_SELECTION_ID);

        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mContextSpy,
                        mCustomAudienceDao,
                        mAdSelectionEntryDaoSpy,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClockSpy,
                        mAdServicesLoggerMock,
                        mFlags,
                        CALLER_UID,
                        mAdSelectionServiceFilterMock,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunnerMock,
                        mAdFilterer,
                        mAdCounterKeyCopier,
                        mAdCounterHistogramUpdater,
                        mFrequencyCapAdDataValidator,
                        mDebugReportingMock,
                        false);

        assertFalse(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(AD_SELECTION_ID));
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(existingAdSelectionId));

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);

        verify(mMockAdsScoreGenerator).runAdScoring(mAdBiddingOutcomeList, adSelectionConfig);

        assertTrue(resultsCallback.mIsSuccess);
        assertEquals(
                expectedAdSelectionResult.getAdSelectionId(),
                resultsCallback.mAdSelectionResponse.getAdSelectionId());
        assertEquals(
                expectedAdSelectionResult.getWinningAdRenderUri(),
                resultsCallback.mAdSelectionResponse.getRenderUri());
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(AD_SELECTION_ID));
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(existingAdSelectionId));
        assertEquals(
                expectedAdSelectionResult,
                mAdSelectionEntryDaoSpy.getAdSelectionEntityById(AD_SELECTION_ID));
        verifyLogForSuccessfulBiddingProcess(mAdBiddingOutcomeList);
        verifyLogForSuccessfulAdSelectionProcess();
        verifyDebugReportingWasNotCalled();
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        eq(RUN_AD_SELECTION_OVERALL_LATENCY_MS));
    }

    @Test
    public void testRunAdSelectionWithRevokedUserConsentSuccess() throws AdServicesException {
        doReturn(mFlags).when(FlagsFactory::getFlags);
        doThrow(new FilterException(new ConsentManager.RevokedConsentException()))
                .when(mAdSelectionServiceFilterMock)
                .filterRequest(
                        SELLER_VALID,
                        MY_APP_PACKAGE_NAME,
                        true,
                        true,
                        CALLER_UID,
                        AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS,
                        Throttler.ApiKey.FLEDGE_API_SELECT_ADS,
                        DevContext.createForDevOptionsDisabled());

        // Creating ad selection config for happy case with all the buyers in place
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        when(mMockAdSelectionIdGenerator.generateId()).thenReturn(AD_SELECTION_ID);

        mockAdSelectionExecutionLoggerSpyWithFailedAdSelectionByValidateRequest();

        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mContextSpy,
                        mCustomAudienceDao,
                        mAdSelectionEntryDaoSpy,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClockSpy,
                        mAdServicesLoggerMock,
                        mFlags,
                        CALLER_UID,
                        mAdSelectionServiceFilterMock,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunnerMock,
                        mAdFilterer,
                        mAdCounterKeyCopier,
                        mAdCounterHistogramUpdater,
                        mFrequencyCapAdDataValidator,
                        mDebugReportingMock,
                        false);

        assertFalse(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(AD_SELECTION_ID));

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        verify(mPerBuyerBiddingRunnerMock, never()).runBidding(any(), any(), anyLong(), any());
        verify(mMockAdsScoreGenerator, never()).runAdScoring(any(), any());

        assertTrue(resultsCallback.mIsSuccess);
        assertFalse(
                mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(
                        resultsCallback.mAdSelectionResponse.getAdSelectionId()));
        assertEquals(Uri.EMPTY, resultsCallback.mAdSelectionResponse.getRenderUri());
        assertFalse(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(AD_SELECTION_ID));

        verifyLogForFailurePriorPersistAdSelection(STATUS_USER_CONSENT_REVOKED);

        // Confirm a duplicate log entry does not exist.
        // AdSelectionServiceFilter ensures the failing assertion is logged internally.
        verify(mAdServicesLoggerMock, never())
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(MY_APP_PACKAGE_NAME),
                        eq(STATUS_USER_CONSENT_REVOKED),
                        eq(RUN_AD_SELECTION_OVERALL_LATENCY_MS));
    }

    @Test
    public void testRunAdSelectionMissingBuyerSignals() throws AdServicesException {
        when(mClockSpy.instant()).thenReturn(Clock.systemUTC().instant());
        doReturn(mFlags).when(FlagsFactory::getFlags);

        // Creating ad selection config with missing Buyer signals to test the fallback
        AdSelectionConfig adSelectionConfig =
                mAdSelectionConfigBuilder.setPerBuyerSignals(Collections.EMPTY_MAP).build();

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        // Getting BiddingOutcome-forBuyerX corresponding to each CA-forBuyerX
        doReturn(ImmutableList.of(Futures.immediateFuture(mAdBiddingOutcomeForBuyer1)))
                .when(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        doReturn(ImmutableList.of(Futures.immediateFuture(mAdBiddingOutcomeForBuyer2)))
                .when(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);

        // Getting ScoringOutcome-ForBuyerX corresponding to each BiddingOutcome-forBuyerX
        when(mMockAdsScoreGenerator.runAdScoring(mAdBiddingOutcomeList, adSelectionConfig))
                .thenReturn((FluentFuture.from(Futures.immediateFuture(mAdScoringOutcomeList))));

        DBAdSelection expectedDBAdSelectionResult =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCreationTimestamp(Calendar.getInstance().toInstant())
                        .setWinningAdBid(
                                mAdScoringOutcomeForBuyer2.getAdWithScore().getAdWithBid().getBid())
                        .setCustomAudienceSignals(
                                mAdScoringOutcomeForBuyer2.getCustomAudienceSignals())
                        .setWinningAdRenderUri(
                                mAdScoringOutcomeForBuyer2
                                        .getAdWithScore()
                                        .getAdWithBid()
                                        .getAdData()
                                        .getRenderUri())
                        .setBiddingLogicUri(mAdScoringOutcomeForBuyer2.getBiddingLogicUri())
                        .setBuyerContextualSignals("{}")
                        .setSellerContextualSignals("{}")
                        .setCallerPackageName(MY_APP_PACKAGE_NAME)
                        .build();

        when(mMockAdSelectionIdGenerator.generateId()).thenReturn(AD_SELECTION_ID);

        mockAdSelectionExecutionLoggerSpyWithSuccessAdSelection();

        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mContextSpy,
                        mCustomAudienceDao,
                        mAdSelectionEntryDaoSpy,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClockSpy,
                        mAdServicesLoggerMock,
                        mFlags,
                        CALLER_UID,
                        mAdSelectionServiceFilterMock,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunnerMock,
                        mAdFilterer,
                        mAdCounterKeyCopier,
                        mAdCounterHistogramUpdater,
                        mFrequencyCapAdDataValidator,
                        mDebugReportingMock,
                        false);

        assertFalse(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(AD_SELECTION_ID));

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        verify(mMockAdsScoreGenerator).runAdScoring(mAdBiddingOutcomeList, adSelectionConfig);

        assertTrue(resultsCallback.mIsSuccess);
        assertEquals(
                expectedDBAdSelectionResult.getAdSelectionId(),
                resultsCallback.mAdSelectionResponse.getAdSelectionId());
        assertEquals(
                expectedDBAdSelectionResult.getWinningAdRenderUri(),
                resultsCallback.mAdSelectionResponse.getRenderUri());
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(AD_SELECTION_ID));

        verifyLogForSuccessfulBiddingProcess(mAdBiddingOutcomeList);
        verifyLogForSuccessfulAdSelectionProcess();
        verifyDebugReportingWasNotCalled();

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        eq(RUN_AD_SELECTION_OVERALL_LATENCY_MS));
    }

    @Test
    public void testRunAdSelectionNoCAs() {
        when(mClockSpy.instant()).thenReturn(Clock.systemUTC().instant());

        // Creating ad selection config for happy case with all the buyers in place
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        // Do not populate CustomAudience DAO

        mockAdSelectionExecutionLoggerSpyWithFailedAdSelectionByNoCAs();

        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mContextSpy,
                        mCustomAudienceDao,
                        mAdSelectionEntryDaoSpy,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClockSpy,
                        mAdServicesLoggerMock,
                        mFlags,
                        CALLER_UID,
                        mAdSelectionServiceFilterMock,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunnerMock,
                        mAdFilterer,
                        mAdCounterKeyCopier,
                        mAdCounterHistogramUpdater,
                        mFrequencyCapAdDataValidator,
                        mDebugReportingMock,
                        false);
        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        assertFalse(resultsCallback.mIsSuccess);
        verifyErrorMessageIsCorrect(
                resultsCallback.mFledgeErrorResponse.getErrorMessage(),
                ERROR_NO_CA_AND_CONTEXTUAL_ADS_AVAILABLE);

        verifyLogForFailedBiddingStageDuringFetchBuyersCustomAudience(STATUS_INTERNAL_ERROR);
        verifyLogForFailurePriorPersistAdSelection(STATUS_INTERNAL_ERROR);

        // If there are no corresponding CAs we should not even attempt bidding
        verifyZeroInteractions(mPerBuyerBiddingRunnerMock);
        // If there was no bidding then we should not even attempt to run scoring
        verifyZeroInteractions(mMockAdsScoreGenerator);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(MY_APP_PACKAGE_NAME),
                        eq(STATUS_INTERNAL_ERROR),
                        eq(RUN_AD_SELECTION_OVERALL_LATENCY_MS));
    }

    @Test
    public void testRunAdSelectionCallerNotInForeground_fails() {
        // Creating ad selection config for happy case with all the buyers in place
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        doThrow(
                        new FilterException(
                                new AppImportanceFilter.WrongCallingApplicationStateException()))
                .when(mAdSelectionServiceFilterMock)
                .filterRequest(
                        SELLER_VALID,
                        MY_APP_PACKAGE_NAME,
                        true,
                        true,
                        CALLER_UID,
                        AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS,
                        Throttler.ApiKey.FLEDGE_API_SELECT_ADS,
                        DevContext.createForDevOptionsDisabled());
        mockAdSelectionExecutionLoggerSpyWithFailedAdSelectionByValidateRequest();
        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mContextSpy,
                        mCustomAudienceDao,
                        mAdSelectionEntryDaoSpy,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClockSpy,
                        mAdServicesLoggerMock,
                        mFlags,
                        CALLER_UID,
                        mAdSelectionServiceFilterMock,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunnerMock,
                        mAdFilterer,
                        mAdCounterKeyCopier,
                        mAdCounterHistogramUpdater,
                        mFrequencyCapAdDataValidator,
                        mDebugReportingMock,
                        false);
        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        assertFalse(resultsCallback.mIsSuccess);
        verifyErrorMessageIsCorrect(
                resultsCallback.mFledgeErrorResponse.getErrorMessage(),
                ILLEGAL_STATE_BACKGROUND_CALLER_ERROR_MESSAGE);

        verifyLogForFailurePriorPersistAdSelection(STATUS_BACKGROUND_CALLER);

        // Confirm a duplicate log entry does not exist.
        // AdSelectionServiceFilter ensures the failing assertion is logged internally.
        verify(mAdServicesLoggerMock, never())
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(MY_APP_PACKAGE_NAME),
                        eq(STATUS_BACKGROUND_CALLER),
                        eq(RUN_AD_SELECTION_OVERALL_LATENCY_MS));
    }

    @Test
    public void testRunAdSelectionCallerNotInForegroundFlagDisabled_doesNotFailValidation() {
        Flags flags =
                new OnDeviceAdSelectionRunnerTestFlags() {
                    @Override
                    public boolean getEnforceForegroundStatusForFledgeRunAdSelection() {
                        return false;
                    }

                    @Override
                    public boolean getDisableFledgeEnrollmentCheck() {
                        return true;
                    }
                };

        // Creating ad selection config for happy case with all the buyers in place
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        mockAdSelectionExecutionLoggerSpyWithFailedAdSelectionByNoCAs();

        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mContextSpy,
                        mCustomAudienceDao,
                        mAdSelectionEntryDaoSpy,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClockSpy,
                        mAdServicesLoggerMock,
                        flags,
                        CALLER_UID,
                        mAdSelectionServiceFilterMock,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunnerMock,
                        mAdFilterer,
                        mAdCounterKeyCopier,
                        mAdCounterHistogramUpdater,
                        mFrequencyCapAdDataValidator,
                        mDebugReportingMock,
                        false);
        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);
        // This ad selection fails because there are no CAs but the foreground status validation
        // is not blocking the rest of the process
        assertFalse(resultsCallback.mIsSuccess);
        verifyErrorMessageIsCorrect(
                resultsCallback.mFledgeErrorResponse.getErrorMessage(),
                ERROR_NO_CA_AND_CONTEXTUAL_ADS_AVAILABLE);
        verifyLogForFailedBiddingStageDuringFetchBuyersCustomAudience(STATUS_INTERNAL_ERROR);
        verifyLogForFailurePriorPersistAdSelection(STATUS_INTERNAL_ERROR);
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(MY_APP_PACKAGE_NAME),
                        eq(STATUS_INTERNAL_ERROR),
                        eq(RUN_AD_SELECTION_OVERALL_LATENCY_MS));
    }

    @Test
    public void testRunAdSelectionWithValidSubdomainsSuccess() throws AdServicesException {
        AdSelectionConfig adSelectionConfigWithValidSubdomains =
                mAdSelectionConfigBuilder
                        .setDecisionLogicUri(
                                CommonFixture.getUriWithValidSubdomain(
                                        SELLER_VALID.toString(), DECISION_LOGIC_PATH))
                        .setTrustedScoringSignalsUri(
                                CommonFixture.getUriWithValidSubdomain(
                                        SELLER_VALID.toString(), TRUSTED_SIGNALS_PATH))
                        .build();

        verifyAndSetupCommonSuccessScenario(adSelectionConfigWithValidSubdomains);
        AdSelectionRunner runner =
                new OnDeviceAdSelectionRunner(
                        mContextSpy,
                        mCustomAudienceDao,
                        mAdSelectionEntryDaoSpy,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClockSpy,
                        mAdServicesLoggerMock,
                        mFlags,
                        CALLER_UID,
                        mAdSelectionServiceFilterMock,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunnerMock,
                        mAdFilterer,
                        mAdCounterKeyCopier,
                        mAdCounterHistogramUpdater,
                        mFrequencyCapAdDataValidator,
                        mDebugReportingMock,
                        false);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        Instant adSelectionCreationTs = Clock.systemUTC().instant().truncatedTo(ChronoUnit.MILLIS);
        when(mClockSpy.instant()).thenReturn(adSelectionCreationTs);
        DBAdSelectionEntry expectedAdSelectionResult =
                new DBAdSelectionEntry.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setBiddingLogicUri(mDBCustomAudienceForBuyer2.getBiddingLogicUri())
                        .setWinningAdBid(
                                mAdScoringOutcomeForBuyer2.getAdWithScore().getAdWithBid().getBid())
                        .setCustomAudienceSignals(
                                mAdScoringOutcomeForBuyer2.getCustomAudienceSignals())
                        .setWinningAdRenderUri(
                                mAdScoringOutcomeForBuyer2
                                        .getAdWithScore()
                                        .getAdWithBid()
                                        .getAdData()
                                        .getRenderUri())
                        .setBuyerDecisionLogicJs(
                                mAdBiddingOutcomeForBuyer1
                                        .getCustomAudienceBiddingInfo()
                                        .getBuyerDecisionLogicJs())
                        // TODO(b/230569187) add contextual signals once supported in the main logic
                        .setBuyerContextualSignals("{}")
                        .setSellerContextualSignals("{}")
                        .setCreationTimestamp(adSelectionCreationTs)
                        .build();

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(
                        runner, adSelectionConfigWithValidSubdomains, MY_APP_PACKAGE_NAME);

        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfigWithValidSubdomains);
        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfigWithValidSubdomains);

        verify(mMockAdsScoreGenerator)
                .runAdScoring(mAdBiddingOutcomeList, adSelectionConfigWithValidSubdomains);

        assertTrue(resultsCallback.mIsSuccess);
        assertEquals(
                expectedAdSelectionResult.getAdSelectionId(),
                resultsCallback.mAdSelectionResponse.getAdSelectionId());
        assertEquals(
                expectedAdSelectionResult.getWinningAdRenderUri(),
                resultsCallback.mAdSelectionResponse.getRenderUri());
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(AD_SELECTION_ID));
        assertEquals(
                expectedAdSelectionResult,
                mAdSelectionEntryDaoSpy.getAdSelectionEntityById(AD_SELECTION_ID));
        verifyLogForSuccessfulBiddingProcess(mAdBiddingOutcomeList);
        verifyLogForSuccessfulAdSelectionProcess();
        verifyDebugReportingWasNotCalled();
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        eq(RUN_AD_SELECTION_OVERALL_LATENCY_MS));
    }

    @Test
    public void testRunAdSelectionPartialBidding() throws AdServicesException {
        when(mClockSpy.instant()).thenReturn(Clock.systemUTC().instant());
        doReturn(mFlags).when(FlagsFactory::getFlags);

        // Creating ad selection config for happy case with all the buyers in place
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        // Getting BiddingOutcome-forBuyerX corresponding to each CA-forBuyerX
        // In this case assuming bidding fails for one of ads and return partial result
        doReturn(ImmutableList.of(Futures.immediateFuture(mAdBiddingOutcomeForBuyer1)))
                .when(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        doReturn(ImmutableList.of(Futures.immediateFuture(null)))
                .when(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);

        // Getting ScoringOutcome-ForBuyerX corresponding to each BiddingOutcome-forBuyerX
        // In this case we are only expected to get score for the first bidding,
        // as second one is null
        List<AdBiddingOutcome> partialBiddingOutcome = Arrays.asList(mAdBiddingOutcomeForBuyer1);
        when(mMockAdsScoreGenerator.runAdScoring(partialBiddingOutcome, adSelectionConfig))
                .thenReturn(
                        (FluentFuture.from(
                                Futures.immediateFuture(mAdScoringOutcomeList.subList(0, 1)))));

        when(mMockAdSelectionIdGenerator.generateId()).thenReturn(AD_SELECTION_ID);

        mockAdSelectionExecutionLoggerSpyWithSuccessAdSelection();

        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mContextSpy,
                        mCustomAudienceDao,
                        mAdSelectionEntryDaoSpy,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClockSpy,
                        mAdServicesLoggerMock,
                        mFlags,
                        CALLER_UID,
                        mAdSelectionServiceFilterMock,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunnerMock,
                        mAdFilterer,
                        mAdCounterKeyCopier,
                        mAdCounterHistogramUpdater,
                        mFrequencyCapAdDataValidator,
                        mDebugReportingMock,
                        false);

        assertFalse(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(AD_SELECTION_ID));

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        DBAdSelection expectedDBAdSelectionResult =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCreationTimestamp(Calendar.getInstance().toInstant())
                        .setWinningAdBid(
                                mAdScoringOutcomeForBuyer1.getAdWithScore().getAdWithBid().getBid())
                        .setCustomAudienceSignals(
                                mAdScoringOutcomeForBuyer1.getCustomAudienceSignals())
                        .setWinningAdRenderUri(
                                mAdScoringOutcomeForBuyer1
                                        .getAdWithScore()
                                        .getAdWithBid()
                                        .getAdData()
                                        .getRenderUri())
                        .setBiddingLogicUri(mAdScoringOutcomeForBuyer1.getBiddingLogicUri())
                        .setBuyerContextualSignals("{}")
                        .setSellerContextualSignals("{}")
                        .setCallerPackageName(MY_APP_PACKAGE_NAME)
                        .build();

        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        verify(mMockAdsScoreGenerator).runAdScoring(partialBiddingOutcome, adSelectionConfig);

        assertTrue(resultsCallback.mIsSuccess);
        assertEquals(
                expectedDBAdSelectionResult.getAdSelectionId(),
                resultsCallback.mAdSelectionResponse.getAdSelectionId());
        assertEquals(
                expectedDBAdSelectionResult.getWinningAdRenderUri(),
                resultsCallback.mAdSelectionResponse.getRenderUri());
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(AD_SELECTION_ID));
        verifyLogForSuccessfulBiddingProcess(partialBiddingOutcome);
        verifyLogForSuccessfulAdSelectionProcess();
        verifyDebugReportingWasNotCalled();
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        eq(RUN_AD_SELECTION_OVERALL_LATENCY_MS));
    }

    @Test
    public void testRunAdSelectionBiddingFailure() {
        when(mClockSpy.instant()).thenReturn(Clock.systemUTC().instant());
        doReturn(mFlags).when(FlagsFactory::getFlags);

        // Creating ad selection config for happy case with all the buyers in place
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        // Getting BiddingOutcome-forBuyerX corresponding to each CA-forBuyerX
        // In this case assuming bidding fails and returns null
        doReturn(ImmutableList.of(Futures.immediateFuture(null)))
                .when(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        doReturn(ImmutableList.of(Futures.immediateFuture(null)))
                .when(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);

        mockAdSelectionExecutionLoggerSpyWithFailedAdSelectionByNoBiddingOutcomes();

        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mContextSpy,
                        mCustomAudienceDao,
                        mAdSelectionEntryDaoSpy,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClockSpy,
                        mAdServicesLoggerMock,
                        mFlags,
                        CALLER_UID,
                        mAdSelectionServiceFilterMock,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunnerMock,
                        mAdFilterer,
                        mAdCounterKeyCopier,
                        mAdCounterHistogramUpdater,
                        mFrequencyCapAdDataValidator,
                        mDebugReportingMock,
                        false);

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        // If the result of bidding is empty, then we should not even attempt to run scoring
        verifyZeroInteractions(mMockAdsScoreGenerator);

        assertFalse(resultsCallback.mIsSuccess);
        verifyErrorMessageIsCorrect(
                resultsCallback.mFledgeErrorResponse.getErrorMessage(),
                ERROR_NO_VALID_BIDS_OR_CONTEXTUAL_ADS_FOR_SCORING);
        verifyLogForSuccessfulBiddingProcess(Arrays.asList(null, null));
        verifyLogForFailurePriorPersistAdSelection(STATUS_INTERNAL_ERROR);
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(MY_APP_PACKAGE_NAME),
                        eq(STATUS_INTERNAL_ERROR),
                        eq(RUN_AD_SELECTION_OVERALL_LATENCY_MS));
    }

    @Test
    public void testRunAdSelectionScoringFailure() throws AdServicesException {
        when(mClockSpy.instant()).thenReturn(Clock.systemUTC().instant());
        doReturn(mFlags).when(FlagsFactory::getFlags);

        // Creating ad selection config for happy case with all the buyers in place
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        // Getting BiddingOutcome-forBuyerX corresponding to each CA-forBuyerX
        doReturn(ImmutableList.of(Futures.immediateFuture(mAdBiddingOutcomeForBuyer1)))
                .when(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        doReturn(ImmutableList.of(Futures.immediateFuture(mAdBiddingOutcomeForBuyer2)))
                .when(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);

        // Getting ScoringOutcome-ForBuyerX corresponding to each BiddingOutcome-forBuyerX
        // In this case assuming we get an empty result
        when(mMockAdsScoreGenerator.runAdScoring(mAdBiddingOutcomeList, adSelectionConfig))
                .thenReturn((FluentFuture.from(Futures.immediateFuture(Collections.emptyList()))));

        mockAdSelectionExecutionLoggerSpyWithFailedAdSelectionDuringScoring();

        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mContextSpy,
                        mCustomAudienceDao,
                        mAdSelectionEntryDaoSpy,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClockSpy,
                        mAdServicesLoggerMock,
                        mFlags,
                        CALLER_UID,
                        mAdSelectionServiceFilterMock,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunnerMock,
                        mAdFilterer,
                        mAdCounterKeyCopier,
                        mAdCounterHistogramUpdater,
                        mFrequencyCapAdDataValidator,
                        mDebugReportingMock,
                        false);

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        verify(mMockAdsScoreGenerator).runAdScoring(mAdBiddingOutcomeList, adSelectionConfig);

        assertFalse(resultsCallback.mIsSuccess);
        verifyErrorMessageIsCorrect(
                resultsCallback.mFledgeErrorResponse.getErrorMessage(), ERROR_NO_WINNING_AD_FOUND);
        verifyLogForSuccessfulBiddingProcess(mAdBiddingOutcomeList);
        verifyLogForFailurePriorPersistAdSelection(STATUS_INTERNAL_ERROR);
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(MY_APP_PACKAGE_NAME),
                        eq(STATUS_INTERNAL_ERROR),
                        eq(RUN_AD_SELECTION_OVERALL_LATENCY_MS));
    }

    @Test
    public void testRunAdSelectionNegativeScoring() throws AdServicesException {
        when(mClockSpy.instant()).thenReturn(Clock.systemUTC().instant());
        doReturn(mFlags).when(FlagsFactory::getFlags);

        // Creating ad selection config for happy case with all the buyers in place
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        // Getting BiddingOutcome-forBuyerX corresponding to each CA-forBuyerX
        doReturn(ImmutableList.of(Futures.immediateFuture(mAdBiddingOutcomeForBuyer1)))
                .when(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        doReturn(ImmutableList.of(Futures.immediateFuture(mAdBiddingOutcomeForBuyer2)))
                .when(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);

        AdScoringOutcome adScoringNegativeOutcomeForBuyer1 =
                AdScoringOutcomeFixture.anAdScoringBuilder(BUYER_1, -2.0).build();
        AdScoringOutcome adScoringNegativeOutcomeForBuyer2 =
                AdScoringOutcomeFixture.anAdScoringBuilder(BUYER_2, -3.0).build();
        List<AdScoringOutcome> negativeScoreOutcome =
                Arrays.asList(adScoringNegativeOutcomeForBuyer1, adScoringNegativeOutcomeForBuyer2);

        // Getting ScoringOutcome-ForBuyerX corresponding to each BiddingOutcome-forBuyerX
        // In this case assuming we get a result with negative scores
        when(mMockAdsScoreGenerator.runAdScoring(mAdBiddingOutcomeList, adSelectionConfig))
                .thenReturn((FluentFuture.from(Futures.immediateFuture(negativeScoreOutcome))));

        mockAdSelectionExecutionLoggerSpyWithFailedAdSelectionDuringScoring();

        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mContextSpy,
                        mCustomAudienceDao,
                        mAdSelectionEntryDaoSpy,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClockSpy,
                        mAdServicesLoggerMock,
                        mFlags,
                        CALLER_UID,
                        mAdSelectionServiceFilterMock,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunnerMock,
                        mAdFilterer,
                        mAdCounterKeyCopier,
                        mAdCounterHistogramUpdater,
                        mFrequencyCapAdDataValidator,
                        mDebugReportingMock,
                        false);

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        verify(mMockAdsScoreGenerator).runAdScoring(mAdBiddingOutcomeList, adSelectionConfig);

        assertFalse(resultsCallback.mIsSuccess);
        verifyErrorMessageIsCorrect(
                resultsCallback.mFledgeErrorResponse.getErrorMessage(), ERROR_NO_WINNING_AD_FOUND);
        verifyLogForSuccessfulBiddingProcess(mAdBiddingOutcomeList);
        verifyLogForFailurePriorPersistAdSelection(STATUS_INTERNAL_ERROR);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(MY_APP_PACKAGE_NAME),
                        eq(STATUS_INTERNAL_ERROR),
                        eq(RUN_AD_SELECTION_OVERALL_LATENCY_MS));
    }

    @Test
    public void testRunAdSelectionPartialNegativeScoring() throws AdServicesException {
        when(mClockSpy.instant()).thenReturn(Clock.systemUTC().instant());
        doReturn(mFlags).when(FlagsFactory::getFlags);

        // Creating ad selection config for happy case with all the buyers in place
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        // Getting BiddingOutcome-forBuyerX corresponding to each CA-forBuyerX
        doReturn(ImmutableList.of(Futures.immediateFuture(mAdBiddingOutcomeForBuyer1)))
                .when(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        doReturn(ImmutableList.of(Futures.immediateFuture(mAdBiddingOutcomeForBuyer2)))
                .when(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);

        AdScoringOutcome adScoringNegativeOutcomeForBuyer1 =
                AdScoringOutcomeFixture.anAdScoringBuilder(BUYER_1, 2.0).build();
        AdScoringOutcome adScoringNegativeOutcomeForBuyer2 =
                AdScoringOutcomeFixture.anAdScoringBuilder(BUYER_2, -3.0).build();
        List<AdScoringOutcome> negativeScoreOutcome =
                Arrays.asList(adScoringNegativeOutcomeForBuyer1, adScoringNegativeOutcomeForBuyer2);

        // Getting ScoringOutcome-ForBuyerX corresponding to each BiddingOutcome-forBuyerX
        // In this case assuming we get a result with partially negative scores
        when(mMockAdsScoreGenerator.runAdScoring(mAdBiddingOutcomeList, adSelectionConfig))
                .thenReturn((FluentFuture.from(Futures.immediateFuture(negativeScoreOutcome))));

        when(mMockAdSelectionIdGenerator.generateId()).thenReturn(AD_SELECTION_ID);

        mockAdSelectionExecutionLoggerSpyWithSuccessAdSelection();

        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mContextSpy,
                        mCustomAudienceDao,
                        mAdSelectionEntryDaoSpy,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClockSpy,
                        mAdServicesLoggerMock,
                        mFlags,
                        CALLER_UID,
                        mAdSelectionServiceFilterMock,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunnerMock,
                        mAdFilterer,
                        mAdCounterKeyCopier,
                        mAdCounterHistogramUpdater,
                        mFrequencyCapAdDataValidator,
                        mDebugReportingMock,
                        false);

        assertFalse(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(AD_SELECTION_ID));

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        verify(mMockAdsScoreGenerator).runAdScoring(mAdBiddingOutcomeList, adSelectionConfig);

        DBAdSelection expectedDBAdSelectionResult =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCreationTimestamp(Calendar.getInstance().toInstant())
                        .setWinningAdBid(
                                mAdScoringOutcomeForBuyer1.getAdWithScore().getAdWithBid().getBid())
                        .setCustomAudienceSignals(
                                mAdScoringOutcomeForBuyer1.getCustomAudienceSignals())
                        .setWinningAdRenderUri(
                                mAdScoringOutcomeForBuyer1
                                        .getAdWithScore()
                                        .getAdWithBid()
                                        .getAdData()
                                        .getRenderUri())
                        .setBiddingLogicUri(mAdScoringOutcomeForBuyer1.getBiddingLogicUri())
                        .setBuyerContextualSignals("{}")
                        .setSellerContextualSignals("{}")
                        .setCallerPackageName(MY_APP_PACKAGE_NAME)
                        .build();

        assertTrue(resultsCallback.mIsSuccess);
        assertEquals(
                expectedDBAdSelectionResult.getAdSelectionId(),
                resultsCallback.mAdSelectionResponse.getAdSelectionId());
        assertEquals(
                expectedDBAdSelectionResult.getWinningAdRenderUri(),
                resultsCallback.mAdSelectionResponse.getRenderUri());
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(AD_SELECTION_ID));
        verifyLogForSuccessfulBiddingProcess(mAdBiddingOutcomeList);
        verifyLogForSuccessfulAdSelectionProcess();
        verifyDebugReportingWasNotCalled();

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        eq(RUN_AD_SELECTION_OVERALL_LATENCY_MS));
    }

    @Test
    public void testRunAdSelectionScoringException() throws AdServicesException {
        when(mClockSpy.instant()).thenReturn(Clock.systemUTC().instant());
        doReturn(mFlags).when(FlagsFactory::getFlags);

        // Creating ad selection config for happy case with all the buyers in place
        AdSelectionConfig adSelectionConfig =
                mAdSelectionConfigBuilder
                        .setAdSelectionSignals(AdSelectionSignals.fromString("{/}"))
                        .build();

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        // Getting BiddingOutcome-forBuyerX corresponding to each CA-forBuyerX
        doReturn(ImmutableList.of(Futures.immediateFuture(mAdBiddingOutcomeForBuyer1)))
                .when(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        doReturn(ImmutableList.of(Futures.immediateFuture(mAdBiddingOutcomeForBuyer2)))
                .when(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);

        // Getting ScoringOutcome-ForBuyerX corresponding to each BiddingOutcome-forBuyerX
        // In this case we expect a JSON validation exception
        when(mMockAdsScoreGenerator.runAdScoring(mAdBiddingOutcomeList, adSelectionConfig))
                .thenThrow(new AdServicesException(ERROR_INVALID_JSON));

        mockAdSelectionExecutionLoggerSpyWithFailedAdSelectionDuringScoring();

        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mContextSpy,
                        mCustomAudienceDao,
                        mAdSelectionEntryDaoSpy,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClockSpy,
                        mAdServicesLoggerMock,
                        mFlags,
                        CALLER_UID,
                        mAdSelectionServiceFilterMock,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunnerMock,
                        mAdFilterer,
                        mAdCounterKeyCopier,
                        mAdCounterHistogramUpdater,
                        mFrequencyCapAdDataValidator,
                        mDebugReportingMock,
                        false);

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);

        assertFalse(resultsCallback.mIsSuccess);
        verifyErrorMessageIsCorrect(
                resultsCallback.mFledgeErrorResponse.getErrorMessage(), ERROR_INVALID_JSON);

        verifyLogForSuccessfulBiddingProcess(mAdBiddingOutcomeList);
        verifyLogForFailurePriorPersistAdSelection(STATUS_INTERNAL_ERROR);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(MY_APP_PACKAGE_NAME),
                        eq(STATUS_INTERNAL_ERROR),
                        eq(RUN_AD_SELECTION_OVERALL_LATENCY_MS));
    }

    @Test
    public void testRunAdSelection_withOnDeviceAuctionDisabled_throwsIllegalStateException() {
        Flags flagsWithOnDeviceAuctionDisabled =
                new OnDeviceAdSelectionRunnerTestFlags() {
                    @Override
                    public boolean getFledgeOnDeviceAuctionKillSwitch() {
                        return true;
                    }
                };
        mAdSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata,
                        mAdSelectionExecutionLoggerClockMock,
                        mContextSpy,
                        mAdServicesLoggerMock);
        AdSelectionRunner runner =
                new OnDeviceAdSelectionRunner(
                        mContextSpy,
                        mCustomAudienceDao,
                        mAdSelectionEntryDaoSpy,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClockSpy,
                        mAdServicesLoggerMock,
                        flagsWithOnDeviceAuctionDisabled,
                        CALLER_UID,
                        mAdSelectionServiceFilterMock,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunnerMock,
                        mAdFilterer,
                        mAdCounterKeyCopier,
                        mAdCounterHistogramUpdater,
                        mFrequencyCapAdDataValidator,
                        mDebugReportingMock,
                        false);
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        AdSelectionTestCallback callback =
                invokeRunAdSelection(runner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        verifyErrorMessageIsCorrect(
                callback.mFledgeErrorResponse.getErrorMessage(),
                ON_DEVICE_AUCTION_KILL_SWITCH_ENABLED);
    }

    @Test
    public void testmMockDBAdSeleciton() throws AdServicesException {
        when(mClockSpy.instant()).thenReturn(Clock.systemUTC().instant());
        doReturn(mFlags).when(FlagsFactory::getFlags);

        Flags flagsWithSmallerLimits =
                new OnDeviceAdSelectionRunnerTestFlags() {
                    @Override
                    public long getAdSelectionOverallTimeoutMs() {
                        return 1000;
                    }

                    @Override
                    public boolean getDisableFledgeEnrollmentCheck() {
                        return true;
                    }
                };

        // Creating ad selection config for happy case with all the buyers in place
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        // Getting BiddingOutcome-forBuyerX corresponding to each CA-forBuyerX
        doReturn(ImmutableList.of(Futures.immediateFuture(mAdBiddingOutcomeForBuyer1)))
                .when(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        flagsWithSmallerLimits.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        doReturn(ImmutableList.of(Futures.immediateFuture(mAdBiddingOutcomeForBuyer2)))
                .when(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        flagsWithSmallerLimits.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);

        // Getting ScoringOutcome-ForBuyerX corresponding to each BiddingOutcome-forBuyerX
        when(mMockAdsScoreGenerator.runAdScoring(mAdBiddingOutcomeList, adSelectionConfig))
                .thenReturn((FluentFuture.from(Futures.immediateFuture(mAdScoringOutcomeList))));

        Instant adSelectionCreationTs = Clock.systemUTC().instant().truncatedTo(ChronoUnit.MILLIS);
        when(mClockSpy.instant()).thenReturn(adSelectionCreationTs);

        when(mMockAdSelectionIdGenerator.generateId())
                .thenAnswer(
                        new AnswersWithDelay(
                                2 * mFlags.getAdSelectionOverallTimeoutMs(),
                                new Returns(AD_SELECTION_ID)));
        mockAdSelectionExecutionLoggerSpyWithFailedAdSelectionBeforePersistAdSelection();

        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mContextSpy,
                        mCustomAudienceDao,
                        mAdSelectionEntryDaoSpy,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClockSpy,
                        mAdServicesLoggerMock,
                        flagsWithSmallerLimits,
                        CALLER_UID,
                        mAdSelectionServiceFilterMock,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunnerMock,
                        mAdFilterer,
                        mAdCounterKeyCopier,
                        mAdCounterHistogramUpdater,
                        mFrequencyCapAdDataValidator,
                        mDebugReportingMock,
                        false);

        assertFalse(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(AD_SELECTION_ID));

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        assertFalse(resultsCallback.mIsSuccess);
        FledgeErrorResponse response = resultsCallback.mFledgeErrorResponse;
        verifyErrorMessageIsCorrect(response.getErrorMessage(), AD_SELECTION_TIMED_OUT);
        Assert.assertEquals(
                "Error response code mismatch",
                AdServicesStatusUtils.STATUS_TIMEOUT,
                response.getStatusCode());

        verifyLogForSuccessfulBiddingProcess(mAdBiddingOutcomeList);
        verifyLogForFailureByRunAdSelectionOrchestrationTimesOut(STATUS_TIMEOUT);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(MY_APP_PACKAGE_NAME),
                        eq(STATUS_TIMEOUT),
                        eq(RUN_AD_SELECTION_OVERALL_LATENCY_MS));
    }

    @Test
    public void testRunAdSelectionPerBuyerTimeout() throws AdServicesException {
        Flags flagsWithSmallPerBuyerTimeout =
                new OnDeviceAdSelectionRunnerTestFlags() {
                    @Override
                    public long getAdSelectionOverallTimeoutMs() {
                        return 5000;
                    }

                    @Override
                    public long getAdSelectionBiddingTimeoutPerBuyerMs() {
                        return 100;
                    }

                    @Override
                    public boolean getDisableFledgeEnrollmentCheck() {
                        return true;
                    }
                };
        when(mClockSpy.instant()).thenReturn(Clock.systemUTC().instant());
        doReturn(flagsWithSmallPerBuyerTimeout).when(FlagsFactory::getFlags);
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        Callable<AdBiddingOutcome> delayedBiddingOutcomeForBuyer1 =
                () -> {
                    TimeUnit.MILLISECONDS.sleep(
                            10
                                    * flagsWithSmallPerBuyerTimeout
                                            .getAdSelectionBiddingTimeoutPerBuyerMs());
                    return mAdBiddingOutcomeForBuyer1;
                };

        doReturn(ImmutableList.of())
                .when(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        flagsWithSmallPerBuyerTimeout.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        doReturn(ImmutableList.of(Futures.immediateFuture(mAdBiddingOutcomeForBuyer2)))
                .when(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        flagsWithSmallPerBuyerTimeout.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);

        // bidding Outcome List should only have one bidding outcome
        List<AdBiddingOutcome> adBiddingOutcomeList = ImmutableList.of(mAdBiddingOutcomeForBuyer2);

        // Getting ScoringOutcome-ForBuyerX corresponding to each BiddingOutcome-forBuyerX
        when(mMockAdsScoreGenerator.runAdScoring(adBiddingOutcomeList, adSelectionConfig))
                .thenReturn(
                        (FluentFuture.from(
                                Futures.immediateFuture(
                                        ImmutableList.of(mAdScoringOutcomeForBuyer2)))));

        Instant adSelectionCreationTs = Clock.systemUTC().instant().truncatedTo(ChronoUnit.MILLIS);
        when(mClockSpy.instant()).thenReturn(adSelectionCreationTs);

        DBAdSelectionEntry expectedAdSelectionResult =
                new DBAdSelectionEntry.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setBiddingLogicUri(mDBCustomAudienceForBuyer2.getBiddingLogicUri())
                        .setWinningAdBid(
                                mAdScoringOutcomeForBuyer2.getAdWithScore().getAdWithBid().getBid())
                        .setCustomAudienceSignals(
                                mAdScoringOutcomeForBuyer2.getCustomAudienceSignals())
                        .setWinningAdRenderUri(
                                mAdScoringOutcomeForBuyer2
                                        .getAdWithScore()
                                        .getAdWithBid()
                                        .getAdData()
                                        .getRenderUri())
                        .setBuyerDecisionLogicJs(
                                mAdBiddingOutcomeForBuyer1
                                        .getCustomAudienceBiddingInfo()
                                        .getBuyerDecisionLogicJs())
                        // TODO(b/230569187) add contextual signals once supported in the main logic
                        .setBuyerContextualSignals("{}")
                        .setSellerContextualSignals("{}")
                        .setCreationTimestamp(adSelectionCreationTs)
                        .build();

        when(mMockAdSelectionIdGenerator.generateId()).thenReturn(AD_SELECTION_ID);

        mockAdSelectionExecutionLoggerSpyWithSuccessAdSelection();

        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mContextSpy,
                        mCustomAudienceDao,
                        mAdSelectionEntryDaoSpy,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClockSpy,
                        mAdServicesLoggerMock,
                        flagsWithSmallPerBuyerTimeout,
                        CALLER_UID,
                        mAdSelectionServiceFilterMock,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunnerMock,
                        mAdFilterer,
                        mAdCounterKeyCopier,
                        mAdCounterHistogramUpdater,
                        mFrequencyCapAdDataValidator,
                        mDebugReportingMock,
                        false);

        assertFalse(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(AD_SELECTION_ID));

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        flagsWithSmallPerBuyerTimeout.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        flagsWithSmallPerBuyerTimeout.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        verify(mMockAdsScoreGenerator).runAdScoring(adBiddingOutcomeList, adSelectionConfig);

        assertTrue(resultsCallback.mIsSuccess);
        assertEquals(
                expectedAdSelectionResult.getAdSelectionId(),
                resultsCallback.mAdSelectionResponse.getAdSelectionId());
        assertEquals(
                expectedAdSelectionResult.getWinningAdRenderUri(),
                resultsCallback.mAdSelectionResponse.getRenderUri());
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(AD_SELECTION_ID));
        assertEquals(
                expectedAdSelectionResult,
                mAdSelectionEntryDaoSpy.getAdSelectionEntityById(AD_SELECTION_ID));
        verifyLogForSuccessfulBiddingProcess(adBiddingOutcomeList);
        verifyLogForSuccessfulAdSelectionProcess();
        verifyDebugReportingWasNotCalled();
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        eq(RUN_AD_SELECTION_OVERALL_LATENCY_MS));
    }

    @Test
    public void testRunAdSelectionThrottledFailure() throws AdServicesException {
        when(mClockSpy.instant()).thenReturn(Clock.systemUTC().instant());
        doReturn(mFlags).when(FlagsFactory::getFlags);
        // Creating ad selection config for happy case with all the buyers in place
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        // Throttle Ad Selection request
        doThrow(new FilterException(new LimitExceededException(RATE_LIMIT_REACHED_ERROR_MESSAGE)))
                .when(mAdSelectionServiceFilterMock)
                .filterRequest(
                        SELLER_VALID,
                        MY_APP_PACKAGE_NAME,
                        true,
                        true,
                        CALLER_UID,
                        AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS,
                        Throttler.ApiKey.FLEDGE_API_SELECT_ADS,
                        DevContext.createForDevOptionsDisabled());

        mockAdSelectionExecutionLoggerSpyWithFailedAdSelectionByValidateRequest();

        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mContextSpy,
                        mCustomAudienceDao,
                        mAdSelectionEntryDaoSpy,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClockSpy,
                        mAdServicesLoggerMock,
                        mFlags,
                        CALLER_UID,
                        mAdSelectionServiceFilterMock,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunnerMock,
                        mAdFilterer,
                        mAdCounterKeyCopier,
                        mAdCounterHistogramUpdater,
                        mFrequencyCapAdDataValidator,
                        mDebugReportingMock,
                        false);

        AdSelectionTestCallback resultsCallback =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        assertFalse(resultsCallback.mIsSuccess);
        FledgeErrorResponse response = resultsCallback.mFledgeErrorResponse;
        verifyErrorMessageIsCorrect(response.getErrorMessage(), RATE_LIMIT_REACHED_ERROR_MESSAGE);
        Assert.assertEquals(
                "Error response code mismatch",
                STATUS_RATE_LIMIT_REACHED,
                response.getStatusCode());
        verifyLogForFailurePriorPersistAdSelection(STATUS_RATE_LIMIT_REACHED);

        // Confirm a duplicate log entry does not exist.
        // AdSelectionServiceFilter ensures the failing assertion is logged internally.
        verify(mAdServicesLoggerMock, never())
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(MY_APP_PACKAGE_NAME),
                        eq(STATUS_RATE_LIMIT_REACHED),
                        eq(RUN_AD_SELECTION_OVERALL_LATENCY_MS));
    }

    @Test
    public void testFilterOneAd() throws AdServicesException {
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();
        verifyAndSetupCommonSuccessScenario(adSelectionConfig);
        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mContextSpy,
                        mCustomAudienceDao,
                        mAdSelectionEntryDaoSpy,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClockSpy,
                        mAdServicesLoggerMock,
                        mFlags,
                        CALLER_UID,
                        mAdSelectionServiceFilterMock,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunnerMock,
                        mMockAdFilterer,
                        mAdCounterKeyCopier,
                        mAdCounterHistogramUpdater,
                        mFrequencyCapAdDataValidator,
                        mDebugReportingMock,
                        false);
        List<DBAdData> adsToNotFilter =
                DBAdDataFixture.getValidDbAdDataListByBuyer(mDBCustomAudienceForBuyer1.getBuyer());
        AppInstallFilters appFilters =
                new AppInstallFilters.Builder()
                        .setPackageNames(Collections.singleton(CommonFixture.TEST_PACKAGE_NAME_1))
                        .build();
        DBAdData adToFilter =
                DBAdDataFixture.getValidDbAdDataBuilder()
                        .setAdFilters(
                                new AdFilters.Builder().setAppInstallFilters(appFilters).build())
                        .build();

        List<DBAdData> ads = new ArrayList<>(adsToNotFilter);
        ads.add(adToFilter);

        DBCustomAudience caWithFilterAd =
                new DBCustomAudience.Builder(mDBCustomAudienceForBuyer1).setAds(ads).build();

        DBCustomAudience caWithoutFilterAd =
                new DBCustomAudience.Builder(mDBCustomAudienceForBuyer1)
                        .setAds(adsToNotFilter)
                        .build();

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                caWithFilterAd,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        when(mMockAdFilterer.filterCustomAudiences(
                        Arrays.asList(caWithFilterAd, mDBCustomAudienceForBuyer2)))
                .thenReturn((Arrays.asList(caWithoutFilterAd, mDBCustomAudienceForBuyer2)));

        invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(caWithoutFilterAd),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);

        verify(mMockAdFilterer, times(1))
                .filterCustomAudiences(Arrays.asList(caWithFilterAd, mDBCustomAudienceForBuyer2));
    }

    @Test
    public void testFilterWholeCa() throws AdServicesException {
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();
        verifyAndSetupCommonSuccessScenario(adSelectionConfig);
        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mContextSpy,
                        mCustomAudienceDao,
                        mAdSelectionEntryDaoSpy,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClockSpy,
                        mAdServicesLoggerMock,
                        mFlags,
                        CALLER_UID,
                        mAdSelectionServiceFilterMock,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunnerMock,
                        mMockAdFilterer,
                        mAdCounterKeyCopier,
                        mAdCounterHistogramUpdater,
                        mFrequencyCapAdDataValidator,
                        mDebugReportingMock,
                        false);
        AppInstallFilters appFilters =
                new AppInstallFilters.Builder()
                        .setPackageNames(Collections.singleton(CommonFixture.TEST_PACKAGE_NAME_1))
                        .build();
        DBAdData adToFilter =
                DBAdDataFixture.getValidDbAdDataBuilder()
                        .setAdFilters(
                                new AdFilters.Builder().setAppInstallFilters(appFilters).build())
                        .build();
        List<DBAdData> ads = Collections.singletonList(adToFilter);

        DBCustomAudience caWithFilterAd =
                new DBCustomAudience.Builder(mDBCustomAudienceForBuyer1).setAds(ads).build();

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                caWithFilterAd,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        when(mMockAdFilterer.filterCustomAudiences(
                        Arrays.asList(caWithFilterAd, mDBCustomAudienceForBuyer2)))
                .thenReturn((Arrays.asList(mDBCustomAudienceForBuyer2)));

        invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        verify(mPerBuyerBiddingRunnerMock, times(1))
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        verify(mPerBuyerBiddingRunnerMock, times(0))
                .runBidding(eq(BUYER_1), any(), anyLong(), any());

        verify(mMockAdFilterer, times(1))
                .filterCustomAudiences(Arrays.asList(caWithFilterAd, mDBCustomAudienceForBuyer2));
    }

    @Test
    public void testGetDecisionLogic_PreDownloaded()
            throws ExecutionException, InterruptedException, TimeoutException, AdServicesException {
        mAdScoringOutcomeForBuyer1 =
                AdScoringOutcomeFixture.anAdScoringBuilder(BUYER_1, 1.0)
                        .setBiddingLogicJsDownloaded(true)
                        .build();

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();
        verifyAndSetupCommonSuccessScenario(adSelectionConfig);
        OnDeviceAdSelectionRunner adSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mContextSpy,
                        mCustomAudienceDao,
                        mAdSelectionEntryDaoSpy,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClockSpy,
                        mAdServicesLoggerMock,
                        mFlags,
                        CALLER_UID,
                        mAdSelectionServiceFilterMock,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunnerMock,
                        mAdFilterer,
                        mAdCounterKeyCopier,
                        mAdCounterHistogramUpdater,
                        mFrequencyCapAdDataValidator,
                        mDebugReportingMock,
                        false);

        assertEquals(
                mAdScoringOutcomeForBuyer1.getBiddingLogicJs(),
                adSelectionRunner
                        .getWinnerBiddingLogicJs(mAdScoringOutcomeForBuyer1)
                        .get(500, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testGetDecisionLogic_NotPreDownloaded()
            throws ExecutionException, InterruptedException, TimeoutException, AdServicesException {
        mAdScoringOutcomeForBuyer1 =
                AdScoringOutcomeFixture.anAdScoringBuilder(BUYER_1, 1.0)
                        .setBiddingLogicUri(DECISION_LOGIC_URI)
                        .setBiddingLogicJsDownloaded(false)
                        .build();

        when(mMockHttpClient.fetchPayload(
                        AdServicesHttpClientRequest.builder()
                                .setUri(DECISION_LOGIC_URI)
                                .setUseCache(mFlags.getFledgeHttpJsCachingEnabled())
                                .setDevContext(DevContext.createForDevOptionsDisabled())
                                .build()))
                .thenReturn(
                        Futures.immediateFuture(
                                AdServicesHttpClientResponse.builder()
                                        .setResponseBody(BUYER_DECISION_LOGIC_JS)
                                        .build()));
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();
        verifyAndSetupCommonSuccessScenario(adSelectionConfig);

        OnDeviceAdSelectionRunner adSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mContextSpy,
                        mCustomAudienceDao,
                        mAdSelectionEntryDaoSpy,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        mMockHttpClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClockSpy,
                        mAdServicesLoggerMock,
                        mFlags,
                        CALLER_UID,
                        mAdSelectionServiceFilterMock,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunnerMock,
                        mAdFilterer,
                        mAdCounterKeyCopier,
                        mAdCounterHistogramUpdater,
                        mFrequencyCapAdDataValidator,
                        mDebugReportingMock,
                        false);

        String downloadingDecisionLogic =
                adSelectionRunner
                        .getWinnerBiddingLogicJs(mAdScoringOutcomeForBuyer1)
                        .get(500, TimeUnit.MILLISECONDS);
        assertEquals("", downloadingDecisionLogic);
        verify(mMockHttpClient, times(0))
                .fetchPayload(
                        AdServicesHttpClientRequest.builder()
                                .setUri(DECISION_LOGIC_URI)
                                .setUseCache(true)
                                .setDevContext(DevContext.createForDevOptionsDisabled())
                                .build());
    }

    @Test
    public void testCreateAdSelectionResult_Contextual_Enabled() throws AdServicesException {
        Map<AdTechIdentifier, SignedContextualAds> signedContextualAdsMap = createContextualAds();
        AdSelectionConfig adSelectionConfig =
                mAdSelectionConfigBuilder
                        .build()
                        .cloneToBuilder()
                        .setCustomAudienceBuyers(Collections.emptyList())
                        .setPerBuyerSignedContextualAds(signedContextualAdsMap)
                        .build();

        final Flags flags =
                new OnDeviceAdSelectionRunnerTestFlags() {
                    @Override
                    public long getAdSelectionOverallTimeoutMs() {
                        return 300;
                    }

                    @Override
                    public boolean getDisableFledgeEnrollmentCheck() {
                        return true;
                    }

                    @Override
                    public boolean getFledgeAdSelectionFilteringEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getFledgeAdSelectionContextualAdsEnabled() {
                        return true;
                    }
                };

        mockAdSelectionExecutionLoggerSpyWithFailedAdSelectionByNoBiddingOutcomes();
        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mContextSpy,
                        mCustomAudienceDao,
                        mAdSelectionEntryDaoSpy,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClockSpy,
                        mAdServicesLoggerMock,
                        flags,
                        CALLER_UID,
                        mAdSelectionServiceFilterMock,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunnerMock,
                        mAdFilterer,
                        mAdCounterKeyCopier,
                        mAdCounterHistogramUpdater,
                        mFrequencyCapAdDataValidator,
                        mDebugReportingMock,
                        false);

        invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        verify(mMockAdsScoreGenerator)
                .runAdScoring(
                        eq(Collections.EMPTY_LIST), mAdSelectionConfigArgumentCaptor.capture());
        assertEquals(
                "The contextual ads should have reached scoring as is",
                signedContextualAdsMap,
                mAdSelectionConfigArgumentCaptor.getValue().getPerBuyerSignedContextualAds());
    }

    @Test
    public void testCreateAdSelectionResult_Contextual_DisabledAndSkipped() {
        when(mClockSpy.instant()).thenReturn(Clock.systemUTC().instant());
        AdSelectionConfig adSelectionConfig =
                mAdSelectionConfigBuilder
                        .build()
                        .cloneToBuilder()
                        .setCustomAudienceBuyers(Collections.emptyList())
                        // Despite populating Contextual Ads, they will be removed
                        .setPerBuyerSignedContextualAds(createContextualAds())
                        .build();
        final Flags flags =
                new OnDeviceAdSelectionRunnerTestFlags() {
                    @Override
                    public boolean getDisableFledgeEnrollmentCheck() {
                        return true;
                    }

                    @Override
                    public boolean getFledgeAdSelectionFilteringEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getFledgeAdSelectionContextualAdsEnabled() {
                        return false;
                    }
                };

        mockAdSelectionExecutionLoggerSpyWithFailedAdSelectionByNoCAs();
        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mContextSpy,
                        mCustomAudienceDao,
                        mAdSelectionEntryDaoSpy,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClockSpy,
                        mAdServicesLoggerMock,
                        flags,
                        CALLER_UID,
                        mAdSelectionServiceFilterMock,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunnerMock,
                        mAdFilterer,
                        mAdCounterKeyCopier,
                        mAdCounterHistogramUpdater,
                        mFrequencyCapAdDataValidator,
                        mDebugReportingMock,
                        false);

        AdSelectionTestCallback result =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        assertFalse(result.mIsSuccess);
        assertEquals(
                "Contextual Ads should have been flushed and Ad Selection resulted in error",
                result.mFledgeErrorResponse.getErrorMessage(),
                String.format(
                        AD_SELECTION_ERROR_PATTERN,
                        ERROR_AD_SELECTION_FAILURE,
                        ERROR_NO_BUYERS_OR_CONTEXTUAL_ADS_AVAILABLE));
    }

    @Test
    public void testCreateAdSelectionResult_Contextual_AppInstallFiltered()
            throws AdServicesException {
        Map<AdTechIdentifier, SignedContextualAds> contextualAdsMap = createContextualAds();

        AdSelectionConfig adSelectionConfig =
                mAdSelectionConfigBuilder
                        .build()
                        .cloneToBuilder()
                        .setCustomAudienceBuyers(Collections.emptyList())
                        .setPerBuyerSignedContextualAds(contextualAdsMap)
                        .build();

        final Flags flags =
                new OnDeviceAdSelectionRunnerTestFlags() {
                    @Override
                    public boolean getDisableFledgeEnrollmentCheck() {
                        return true;
                    }

                    @Override
                    public boolean getFledgeAdSelectionFilteringEnabled() {
                        return true;
                    }

                    @Override
                    public boolean getFledgeAdSelectionContextualAdsEnabled() {
                        return true;
                    }
                };

        mockAdSelectionExecutionLoggerSpyWithFailedAdSelectionByNoCAs();
        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mContextSpy,
                        mCustomAudienceDao,
                        mAdSelectionEntryDaoSpy,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClockSpy,
                        mAdServicesLoggerMock,
                        flags,
                        CALLER_UID,
                        mAdSelectionServiceFilterMock,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunnerMock,
                        mMockAdFilterer,
                        mAdCounterKeyCopier,
                        mAdCounterHistogramUpdater,
                        mFrequencyCapAdDataValidator,
                        mDebugReportingMock,
                        false);

        when(mMockAdFilterer.filterContextualAds(contextualAdsMap.get(CommonFixture.VALID_BUYER_1)))
                .thenReturn(contextualAdsMap.get(CommonFixture.VALID_BUYER_1));
        when(mMockAdFilterer.filterContextualAds(contextualAdsMap.get(CommonFixture.VALID_BUYER_2)))
                .thenReturn(
                        signContextualAds(
                                SignedContextualAdsFixture.aContextualAdsWithEmptySignatureBuilder()
                                        .setBuyer(CommonFixture.VALID_BUYER_2)
                                        .setDecisionLogicUri(
                                                contextualAdsMap
                                                        .get(CommonFixture.VALID_BUYER_2)
                                                        .getDecisionLogicUri())
                                        .setAdsWithBid(Collections.emptyList())));
        invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);
        verify(mMockAdsScoreGenerator)
                .runAdScoring(
                        eq(Collections.EMPTY_LIST), mAdSelectionConfigArgumentCaptor.capture());
        assertEquals(
                "The contextual ads should have remained same for Buyer 1",
                contextualAdsMap.get(CommonFixture.VALID_BUYER_1).getAdsWithBid(),
                mAdSelectionConfigArgumentCaptor
                        .getValue()
                        .getPerBuyerSignedContextualAds()
                        .get(CommonFixture.VALID_BUYER_1)
                        .getAdsWithBid());
        assertEquals(
                "The contextual ads should have been filtered for Buyer 2",
                Collections.emptyList(),
                mAdSelectionConfigArgumentCaptor
                        .getValue()
                        .getPerBuyerSignedContextualAds()
                        .get(CommonFixture.VALID_BUYER_2)
                        .getAdsWithBid());
    }

    @Test
    public void testCopiedAdCounterKeysArePersisted() throws AdServicesException {
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();
        verifyAndSetupCommonSuccessScenario(adSelectionConfig);
        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mContextSpy,
                        mCustomAudienceDao,
                        mAdSelectionEntryDaoSpy,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClockSpy,
                        mAdServicesLoggerMock,
                        mFlags,
                        CALLER_UID,
                        mAdSelectionServiceFilterMock,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunnerMock,
                        mAdFilterer,
                        mAdCounterKeyCopierMock,
                        mAdCounterHistogramUpdater,
                        mFrequencyCapAdDataValidatorMock,
                        mDebugReportingMock,
                        false);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        DBAdSelection.Builder dbAdSelectionBuilder =
                new DBAdSelection.Builder()
                        .setWinningAdBid(
                                mAdScoringOutcomeForBuyer1.getAdWithScore().getAdWithBid().getBid())
                        .setCustomAudienceSignals(
                                mAdScoringOutcomeForBuyer1.getCustomAudienceSignals())
                        .setWinningAdRenderUri(
                                mAdScoringOutcomeForBuyer1
                                        .getAdWithScore()
                                        .getAdWithBid()
                                        .getAdData()
                                        .getRenderUri())
                        .setBiddingLogicUri(mAdScoringOutcomeForBuyer1.getBiddingLogicUri())
                        .setBuyerContextualSignals("{}")
                        .setSellerContextualSignals("{}")
                        .setAdCounterIntKeys(AdDataFixture.getAdCounterKeys());

        doReturn(dbAdSelectionBuilder)
                .when(mAdCounterKeyCopierMock)
                .copyAdCounterKeys(any(DBAdSelection.Builder.class), any(AdScoringOutcome.class));

        AdSelectionTestCallback callback =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        assertThat(callback.mIsSuccess).isTrue();

        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);

        verify(mAdCounterKeyCopierMock)
                .copyAdCounterKeys(any(DBAdSelection.Builder.class), any(AdScoringOutcome.class));

        assertTrue(
                mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(
                        callback.mAdSelectionResponse.getAdSelectionId()));

        DBAdSelectionHistogramInfo histogramInfo =
                mAdSelectionEntryDaoSpy.getAdSelectionHistogramInfoInOnDeviceTable(
                        callback.mAdSelectionResponse.getAdSelectionId(), MY_APP_PACKAGE_NAME);
        assertThat(histogramInfo).isNotNull();
        assertThat(histogramInfo.getBuyer()).isEqualTo(BUYER_1);
        assertThat(histogramInfo.getAdCounterKeys()).isNotNull();
        assertThat(histogramInfo.getAdCounterKeys())
                .containsExactlyElementsIn(AdDataFixture.getAdCounterKeys());
    }

    @Test
    public void testAdCounterHistogramIsUpdatedWithWinEvent() throws Exception {
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();
        verifyAndSetupCommonSuccessScenario(adSelectionConfig);
        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mContextSpy,
                        mCustomAudienceDao,
                        mAdSelectionEntryDaoSpy,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClockSpy,
                        mAdServicesLoggerMock,
                        mFlags,
                        CALLER_UID,
                        mAdSelectionServiceFilterMock,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunnerMock,
                        mAdFilterer,
                        mAdCounterKeyCopierMock,
                        mAdCounterHistogramUpdaterMock,
                        mFrequencyCapAdDataValidator,
                        mDebugReportingMock,
                        false);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        DBAdSelection.Builder dbAdSelectionBuilder =
                new DBAdSelection.Builder()
                        .setWinningAdBid(
                                mAdScoringOutcomeForBuyer1.getAdWithScore().getAdWithBid().getBid())
                        .setCustomAudienceSignals(
                                mAdScoringOutcomeForBuyer1.getCustomAudienceSignals())
                        .setWinningAdRenderUri(
                                mAdScoringOutcomeForBuyer1
                                        .getAdWithScore()
                                        .getAdWithBid()
                                        .getAdData()
                                        .getRenderUri())
                        .setBiddingLogicUri(mAdScoringOutcomeForBuyer1.getBiddingLogicUri())
                        .setBuyerContextualSignals("{}")
                        .setAdCounterIntKeys(AdDataFixture.getAdCounterKeys());

        // Note that regardless of the input, this copier stubs the actual output of the auction
        doReturn(dbAdSelectionBuilder)
                .when(mAdCounterKeyCopierMock)
                .copyAdCounterKeys(any(DBAdSelection.Builder.class), any(AdScoringOutcome.class));

        AdSelectionTestCallback callback =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        assertThat(callback.mIsSuccess).isTrue();

        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);

        verify(mAdCounterKeyCopierMock)
                .copyAdCounterKeys(any(DBAdSelection.Builder.class), any(AdScoringOutcome.class));

        verify(mAdCounterHistogramUpdaterMock).updateWinHistogram(eq(dbAdSelectionBuilder.build()));

        assertTrue(
                mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(
                        callback.mAdSelectionResponse.getAdSelectionId()));
    }

    @Test
    public void testFailedAdCounterHistogramWinUpdateDoesNotStopAdSelection() throws Exception {
        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();
        verifyAndSetupCommonSuccessScenario(adSelectionConfig);
        mAdSelectionRunner =
                new OnDeviceAdSelectionRunner(
                        mContextSpy,
                        mCustomAudienceDao,
                        mAdSelectionEntryDaoSpy,
                        mEncryptionKeyDaoMock,
                        mEnrollmentDaoMock,
                        mAdServicesHttpsClient,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mMockAdsScoreGenerator,
                        mMockAdSelectionIdGenerator,
                        mClockSpy,
                        mAdServicesLoggerMock,
                        mFlags,
                        CALLER_UID,
                        mAdSelectionServiceFilterMock,
                        mAdSelectionExecutionLogger,
                        mPerBuyerBiddingRunnerMock,
                        mAdFilterer,
                        mAdCounterKeyCopier,
                        mAdCounterHistogramUpdaterMock,
                        mFrequencyCapAdDataValidator,
                        mDebugReportingMock,
                        false);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                mDBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        doThrow(new RuntimeException("Failing ad counter histogram update for test"))
                .when(mAdCounterHistogramUpdaterMock)
                .updateWinHistogram(any());

        AdSelectionTestCallback callback =
                invokeRunAdSelection(mAdSelectionRunner, adSelectionConfig, MY_APP_PACKAGE_NAME);

        assertThat(callback.mIsSuccess).isTrue();

        verify(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);

        verify(mAdCounterHistogramUpdaterMock).updateWinHistogram(any());

        assertTrue(
                mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(
                        callback.mAdSelectionResponse.getAdSelectionId()));
    }

    private void verifyErrorMessageIsCorrect(
            final String actualErrorMassage, final String expectedErrorReason) {
        Assert.assertTrue(
                String.format(
                        "Actual error [%s] does not begin with [%s]",
                        actualErrorMassage, ERROR_AD_SELECTION_FAILURE),
                actualErrorMassage.startsWith(ERROR_AD_SELECTION_FAILURE));
        Assert.assertTrue(
                String.format(
                        "Actual error [%s] does not contain expected message: [%s]",
                        actualErrorMassage, expectedErrorReason),
                actualErrorMassage.contains(expectedErrorReason));
    }

    private AdSelectionTestCallback invokeRunAdSelection(
            AdSelectionRunner adSelectionRunner,
            AdSelectionConfig adSelectionConfig,
            String callerPackageName) {

        // Counted down in 1) callback and 2) logApiCall
        CountDownLatch countDownLatch = new CountDownLatch(1);
        AdSelectionTestCallback adSelectionTestCallback =
                new AdSelectionTestCallback(countDownLatch);

        AdSelectionInput input =
                new AdSelectionInput.Builder()
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(callerPackageName)
                        .build();

        adSelectionRunner.runAdSelection(
                input, adSelectionTestCallback, DevContext.createForDevOptionsDisabled(), null);
        try {
            adSelectionTestCallback.mCountDownLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return adSelectionTestCallback;
    }

    @After
    public void tearDown() {
        if (mAdSelectionEntryDaoSpy != null) {
            mAdSelectionEntryDaoSpy.removeAdSelectionEntriesByIds(Arrays.asList(AD_SELECTION_ID));
        }

        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
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

    private void mockAdSelectionExecutionLoggerSpyWithSuccessAdSelection() {
        when(mAdSelectionExecutionLoggerClockMock.elapsedRealtime())
                .thenReturn(
                        START_ELAPSED_TIMESTAMP,
                        BIDDING_STAGE_START_TIMESTAMP,
                        GET_BUYERS_CUSTOM_AUDIENCE_END_TIMESTAMP,
                        RUN_AD_BIDDING_START_TIMESTAMP,
                        RUN_AD_BIDDING_END_TIMESTAMP,
                        PERSIST_AD_SELECTION_START_TIMESTAMP,
                        PERSIST_AD_SELECTION_END_TIMESTAMP,
                        STOP_ELAPSED_TIMESTAMP);
        mAdSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata,
                        mAdSelectionExecutionLoggerClockMock,
                        mContextSpy,
                        mAdServicesLoggerMock);
    }

    private void mockAdSelectionExecutionLoggerSpyWithFailedAdSelectionByValidateRequest() {
        when(mAdSelectionExecutionLoggerClockMock.elapsedRealtime())
                .thenReturn(START_ELAPSED_TIMESTAMP, STOP_ELAPSED_TIMESTAMP);
        mAdSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata,
                        mAdSelectionExecutionLoggerClockMock,
                        mContextSpy,
                        mAdServicesLoggerMock);
    }

    private void mockAdSelectionExecutionLoggerSpyWithFailedAdSelectionByNoCAs() {
        when(mAdSelectionExecutionLoggerClockMock.elapsedRealtime())
                .thenReturn(
                        START_ELAPSED_TIMESTAMP,
                        BIDDING_STAGE_START_TIMESTAMP,
                        BIDDING_STAGE_END_TIMESTAMP,
                        STOP_ELAPSED_TIMESTAMP);
        mAdSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata,
                        mAdSelectionExecutionLoggerClockMock,
                        mContextSpy,
                        mAdServicesLoggerMock);
    }

    private void mockAdSelectionExecutionLoggerSpyWithFailedAdSelectionByNoBiddingOutcomes() {
        when(mAdSelectionExecutionLoggerClockMock.elapsedRealtime())
                .thenReturn(
                        START_ELAPSED_TIMESTAMP,
                        BIDDING_STAGE_START_TIMESTAMP,
                        GET_BUYERS_CUSTOM_AUDIENCE_END_TIMESTAMP,
                        RUN_AD_BIDDING_START_TIMESTAMP,
                        RUN_AD_BIDDING_END_TIMESTAMP,
                        STOP_ELAPSED_TIMESTAMP);
        mAdSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata,
                        mAdSelectionExecutionLoggerClockMock,
                        mContextSpy,
                        mAdServicesLoggerMock);
    }

    // TODO(b/221861861): add SCORING TIMESTAMP.
    private void mockAdSelectionExecutionLoggerSpyWithFailedAdSelectionDuringScoring() {
        when(mAdSelectionExecutionLoggerClockMock.elapsedRealtime())
                .thenReturn(
                        START_ELAPSED_TIMESTAMP,
                        BIDDING_STAGE_START_TIMESTAMP,
                        GET_BUYERS_CUSTOM_AUDIENCE_END_TIMESTAMP,
                        RUN_AD_BIDDING_START_TIMESTAMP,
                        RUN_AD_BIDDING_END_TIMESTAMP,
                        STOP_ELAPSED_TIMESTAMP);
        mAdSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata,
                        mAdSelectionExecutionLoggerClockMock,
                        mContextSpy,
                        mAdServicesLoggerMock);
    }

    private void mockAdSelectionExecutionLoggerSpyWithFailedAdSelectionBeforePersistAdSelection() {
        when(mAdSelectionExecutionLoggerClockMock.elapsedRealtime())
                .thenReturn(
                        START_ELAPSED_TIMESTAMP,
                        BIDDING_STAGE_START_TIMESTAMP,
                        GET_BUYERS_CUSTOM_AUDIENCE_END_TIMESTAMP,
                        RUN_AD_BIDDING_START_TIMESTAMP,
                        RUN_AD_BIDDING_END_TIMESTAMP,
                        STOP_ELAPSED_TIMESTAMP);
        mAdSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata,
                        mAdSelectionExecutionLoggerClockMock,
                        mContextSpy,
                        mAdServicesLoggerMock);
    }

    // Verify bidding process.
    private void verifyLogForSuccessfulBiddingProcess(List<AdBiddingOutcome> adBiddingOutcomeList) {
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(
                        mRunAdBiddingProcessReportedStatsArgumentCaptor.capture());
        RunAdBiddingProcessReportedStats runAdBiddingProcessReportedStats =
                mRunAdBiddingProcessReportedStatsArgumentCaptor.getValue();

        assertThat(runAdBiddingProcessReportedStats.getGetBuyersCustomAudienceLatencyInMills())
                .isEqualTo(GET_BUYERS_CUSTOM_AUDIENCE_LATENCY_MS);
        assertThat(runAdBiddingProcessReportedStats.getGetBuyersCustomAudienceResultCode())
                .isEqualTo(STATUS_SUCCESS);
        assertThat(runAdBiddingProcessReportedStats.getNumBuyersRequested())
                .isEqualTo(mCustomAudienceBuyers.size());
        assertThat(runAdBiddingProcessReportedStats.getNumBuyersFetched())
                .isEqualTo(
                        mBuyerCustomAudienceList.stream()
                                .filter(a -> !Objects.isNull(a))
                                .map(a -> a.getBuyer())
                                .collect(Collectors.toSet())
                                .size());
        assertThat(runAdBiddingProcessReportedStats.getNumOfAdsEnteringBidding())
                .isEqualTo(
                        mBuyerCustomAudienceList.stream()
                                .filter(a -> !Objects.isNull(a))
                                .map(a -> a.getAds().size())
                                .reduce(0, (a, b) -> (a + b)));
        int numOfCAsEnteringBidding = mBuyerCustomAudienceList.size();
        assertThat(runAdBiddingProcessReportedStats.getNumOfCasEnteringBidding())
                .isEqualTo(numOfCAsEnteringBidding);
        int numOfCAsPostBidding =
                adBiddingOutcomeList.stream()
                        .filter(a -> !Objects.isNull(a))
                        .map(
                                a ->
                                        a.getCustomAudienceBiddingInfo()
                                                .getCustomAudienceSignals()
                                                .hashCode())
                        .collect(Collectors.toSet())
                        .size();
        assertThat(runAdBiddingProcessReportedStats.getNumOfCasPostBidding())
                .isEqualTo(numOfCAsPostBidding);
        assertThat(runAdBiddingProcessReportedStats.getRatioOfCasSelectingRmktAds())
                .isEqualTo(((float) numOfCAsPostBidding) / numOfCAsEnteringBidding);
        assertThat(runAdBiddingProcessReportedStats.getRunAdBiddingLatencyInMillis())
                .isEqualTo(RUN_AD_BIDDING_LATENCY_MS);
        assertThat(runAdBiddingProcessReportedStats.getRunAdBiddingResultCode())
                .isEqualTo(STATUS_SUCCESS);
        assertThat(runAdBiddingProcessReportedStats.getTotalAdBiddingStageLatencyInMillis())
                .isEqualTo(TOTAL_BIDDING_STAGE_LATENCY_IN_MS);
    }

    private void verifyLogForFailedBiddingStageDuringFetchBuyersCustomAudience(int resultCode) {
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(
                        mRunAdBiddingProcessReportedStatsArgumentCaptor.capture());
        RunAdBiddingProcessReportedStats runAdBiddingProcessReportedStats =
                mRunAdBiddingProcessReportedStatsArgumentCaptor.getValue();

        assertThat(runAdBiddingProcessReportedStats.getGetBuyersCustomAudienceLatencyInMills())
                .isEqualTo(TOTAL_BIDDING_STAGE_LATENCY_IN_MS);
        assertThat(runAdBiddingProcessReportedStats.getGetBuyersCustomAudienceResultCode())
                .isEqualTo(resultCode);
        assertThat(runAdBiddingProcessReportedStats.getNumBuyersRequested())
                .isEqualTo(mCustomAudienceBuyers.size());
        assertThat(runAdBiddingProcessReportedStats.getNumBuyersFetched()).isEqualTo(STATUS_UNSET);
        assertThat(runAdBiddingProcessReportedStats.getNumOfAdsEnteringBidding())
                .isEqualTo(STATUS_UNSET);
        assertThat(runAdBiddingProcessReportedStats.getNumOfCasEnteringBidding())
                .isEqualTo(STATUS_UNSET);
        assertThat(runAdBiddingProcessReportedStats.getNumOfCasPostBidding())
                .isEqualTo(STATUS_UNSET);
        assertThat(runAdBiddingProcessReportedStats.getRatioOfCasSelectingRmktAds())
                .isEqualTo(-1.0f);
        assertThat(runAdBiddingProcessReportedStats.getRunAdBiddingLatencyInMillis())
                .isEqualTo(STATUS_UNSET);
        assertThat(runAdBiddingProcessReportedStats.getRunAdBiddingResultCode())
                .isEqualTo(STATUS_UNSET);
        assertThat(runAdBiddingProcessReportedStats.getTotalAdBiddingStageLatencyInMillis())
                .isEqualTo(TOTAL_BIDDING_STAGE_LATENCY_IN_MS);
    }

    // Verify Ad selection process.
    private void verifyLogForSuccessfulAdSelectionProcess() {
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        mRunAdSelectionProcessReportedStatsArgumentCaptor.capture());
        RunAdSelectionProcessReportedStats runAdSelectionProcessReportedStats =
                mRunAdSelectionProcessReportedStatsArgumentCaptor.getValue();

        assertThat(runAdSelectionProcessReportedStats.getIsRemarketingAdsWon())
                .isEqualTo(IS_RMKT_ADS_WON);
        assertThat(runAdSelectionProcessReportedStats.getDBAdSelectionSizeInBytes())
                .isEqualTo((int) DB_AD_SELECTION_FILE_SIZE);
        assertThat(runAdSelectionProcessReportedStats.getPersistAdSelectionLatencyInMillis())
                .isEqualTo(PERSIST_AD_SELECTION_LATENCY_MS);
        assertThat(runAdSelectionProcessReportedStats.getPersistAdSelectionResultCode())
                .isEqualTo(STATUS_SUCCESS);
        assertThat(runAdSelectionProcessReportedStats.getRunAdSelectionLatencyInMillis())
                .isEqualTo(RUN_AD_SELECTION_INTERNAL_FINAL_LATENCY_MS);
        assertThat(runAdSelectionProcessReportedStats.getRunAdSelectionResultCode())
                .isEqualTo(STATUS_SUCCESS);
    }

    private void verifyLogForFailurePriorPersistAdSelection(int resultCode) {
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        mRunAdSelectionProcessReportedStatsArgumentCaptor.capture());
        RunAdSelectionProcessReportedStats runAdSelectionProcessReportedStats =
                mRunAdSelectionProcessReportedStatsArgumentCaptor.getValue();

        assertThat(runAdSelectionProcessReportedStats.getIsRemarketingAdsWon())
                .isEqualTo(IS_RMKT_ADS_WON_UNSET);
        assertThat(runAdSelectionProcessReportedStats.getDBAdSelectionSizeInBytes())
                .isEqualTo(STATUS_UNSET);
        assertThat(runAdSelectionProcessReportedStats.getPersistAdSelectionLatencyInMillis())
                .isEqualTo(STATUS_UNSET);
        assertThat(runAdSelectionProcessReportedStats.getPersistAdSelectionResultCode())
                .isEqualTo(STATUS_UNSET);
        assertThat(runAdSelectionProcessReportedStats.getRunAdSelectionLatencyInMillis())
                .isEqualTo(RUN_AD_SELECTION_INTERNAL_FINAL_LATENCY_MS);
        assertThat(runAdSelectionProcessReportedStats.getRunAdSelectionResultCode())
                .isEqualTo(resultCode);
    }

    private void verifyLogForFailureByRunAdSelectionOrchestrationTimesOut(int resultCode) {
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        mRunAdSelectionProcessReportedStatsArgumentCaptor.capture());
        RunAdSelectionProcessReportedStats runAdSelectionProcessReportedStats =
                mRunAdSelectionProcessReportedStatsArgumentCaptor.getValue();
        assertThat(runAdSelectionProcessReportedStats.getRunAdSelectionLatencyInMillis())
                .isEqualTo(RUN_AD_SELECTION_INTERNAL_FINAL_LATENCY_MS);
        assertThat(runAdSelectionProcessReportedStats.getRunAdSelectionResultCode())
                .isEqualTo(resultCode);
    }

    private void verifyAndSetupCommonSuccessScenario(AdSelectionConfig adSelectionConfig)
            throws AdServicesException {
        when(mClockSpy.instant()).thenReturn(Clock.systemUTC().instant());
        doReturn(mFlags).when(FlagsFactory::getFlags);

        // Getting BiddingOutcome-forBuyerX corresponding to each CA-forBuyerX
        doReturn(ImmutableList.of(Futures.immediateFuture(mAdBiddingOutcomeForBuyer1)))
                .when(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_1,
                        ImmutableList.of(mDBCustomAudienceForBuyer1),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);
        doReturn(ImmutableList.of(Futures.immediateFuture(mAdBiddingOutcomeForBuyer2)))
                .when(mPerBuyerBiddingRunnerMock)
                .runBidding(
                        BUYER_2,
                        ImmutableList.of(mDBCustomAudienceForBuyer2),
                        mFlags.getAdSelectionBiddingTimeoutPerBuyerMs(),
                        adSelectionConfig);

        // Getting ScoringOutcome-ForBuyerX corresponding to each BiddingOutcome-forBuyerX
        when(mMockAdsScoreGenerator.runAdScoring(mAdBiddingOutcomeList, adSelectionConfig))
                .thenReturn((FluentFuture.from(Futures.immediateFuture(mAdScoringOutcomeList))));

        mockAdSelectionExecutionLoggerSpyWithSuccessAdSelection();

        when(mMockAdSelectionIdGenerator.generateId()).thenReturn(AD_SELECTION_ID);
        assertFalse(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(AD_SELECTION_ID));
    }

    private void verifyDebugReportingWasNotCalled() {
        assertThat(mFlags.getFledgeEventLevelDebugReportingEnabled()).isFalse();
        verify(mDebugReportingMock, times(0)).getSenderStrategy();
        verify(mDebugReportSenderMock, times(0)).batchEnqueue(anyList());
        verify(mDebugReportSenderMock, times(0)).flush();
    }

    private static class OnDeviceAdSelectionRunnerTestFlags implements Flags {
        @Override
        public boolean getDisableFledgeEnrollmentCheck() {
            return true;
        }

        @Override
        public boolean getFledgeAdSelectionFilteringEnabled() {
            return true;
        }

        @Override
        public long getAdSelectionBiddingTimeoutPerCaMs() {
            return EXTENDED_FLEDGE_AD_SELECTION_BIDDING_TIMEOUT_PER_CA_MS;
        }

        @Override
        public long getAdSelectionScoringTimeoutMs() {
            return EXTENDED_FLEDGE_AD_SELECTION_SCORING_TIMEOUT_MS;
        }

        @Override
        public long getAdSelectionSelectingOutcomeTimeoutMs() {
            return EXTENDED_FLEDGE_AD_SELECTION_SELECTING_OUTCOME_TIMEOUT_MS;
        }

        @Override
        public long getAdSelectionOverallTimeoutMs() {
            return EXTENDED_FLEDGE_AD_SELECTION_OVERALL_TIMEOUT_MS;
        }

        @Override
        public long getAdSelectionFromOutcomesOverallTimeoutMs() {
            return EXTENDED_FLEDGE_AD_SELECTION_FROM_OUTCOMES_OVERALL_TIMEOUT_MS;
        }

        @Override
        public long getReportImpressionOverallTimeoutMs() {
            return EXTENDED_FLEDGE_REPORT_IMPRESSION_OVERALL_TIMEOUT_MS;
        }

        @Override
        public boolean getFledgeOnDeviceAuctionKillSwitch() {
            return false;
        }
    }

    private Map<AdTechIdentifier, SignedContextualAds> createContextualAds() {
        Map<AdTechIdentifier, SignedContextualAds> buyerContextualAds = new HashMap<>();

        AdTechIdentifier buyer1 = CommonFixture.VALID_BUYER_1;
        SignedContextualAds contextualAds1 =
                signContextualAds(
                        SignedContextualAdsFixture.aContextualAdsWithEmptySignatureBuilder(
                                        buyer1, ImmutableList.of(100.0, 200.0, 300.0))
                                .setDecisionLogicUri(
                                        CommonFixture.getUri(
                                                BUYER_1, BUYER_BIDDING_LOGIC_URI_PATH)));

        AdTechIdentifier buyer2 = CommonFixture.VALID_BUYER_2;
        SignedContextualAds contextualAds2 =
                signContextualAds(
                        SignedContextualAdsFixture.aContextualAdsWithEmptySignatureBuilder(
                                        buyer2, ImmutableList.of(400.0, 500.0))
                                .setDecisionLogicUri(
                                        CommonFixture.getUri(
                                                BUYER_2, BUYER_BIDDING_LOGIC_URI_PATH)));

        buyerContextualAds.put(buyer1, contextualAds1);
        buyerContextualAds.put(buyer2, contextualAds2);

        for (AdTechIdentifier adTech : buyerContextualAds.keySet()) {
            doReturn(new EnrollmentData.Builder().setEnrollmentId(adTech.toString()).build())
                    .when(mEnrollmentDaoMock)
                    .getEnrollmentDataForFledgeByAdTechIdentifier(adTech);
            doReturn(
                            Collections.singletonList(
                                    new EncryptionKey.Builder()
                                            .setBody(PUBLIC_TEST_KEY_STRING)
                                            .build()))
                    .when(mEncryptionKeyDaoMock)
                    .getEncryptionKeyFromEnrollmentIdAndKeyType(
                            adTech.toString(), EncryptionKey.KeyType.SIGNING);
        }

        return buyerContextualAds;
    }
}
