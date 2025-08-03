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
import static android.adservices.adselection.ReportEventRequest.FLAG_REPORTING_DESTINATION_BUYER;
import static android.adservices.adselection.ReportEventRequest.FLAG_REPORTING_DESTINATION_SELLER;
import static android.adservices.common.AdServicesStatusUtils.RATE_LIMIT_REACHED_ERROR_MESSAGE;
import static android.adservices.common.AdServicesStatusUtils.STATUS_CALLER_NOT_ALLOWED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
import static android.adservices.common.AdServicesStatusUtils.STATUS_INVALID_ARGUMENT;
import static android.adservices.common.AdServicesStatusUtils.STATUS_RATE_LIMIT_REACHED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;
import static android.adservices.common.AdServicesStatusUtils.STATUS_UNAUTHORIZED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_USER_CONSENT_REVOKED;
import static android.adservices.common.CommonFixture.TEST_PACKAGE_NAME;

import static com.android.adservices.common.CommonFlagsValues.EXTENDED_FLEDGE_AD_SELECTION_BIDDING_TIMEOUT_PER_CA_MS;
import static com.android.adservices.common.CommonFlagsValues.EXTENDED_FLEDGE_AD_SELECTION_FROM_OUTCOMES_OVERALL_TIMEOUT_MS;
import static com.android.adservices.common.CommonFlagsValues.EXTENDED_FLEDGE_AD_SELECTION_OVERALL_TIMEOUT_MS;
import static com.android.adservices.common.CommonFlagsValues.EXTENDED_FLEDGE_AD_SELECTION_SCORING_TIMEOUT_MS;
import static com.android.adservices.common.CommonFlagsValues.EXTENDED_FLEDGE_REPORT_IMPRESSION_OVERALL_TIMEOUT_MS;
import static com.android.adservices.service.adselection.AdSelectionScriptEngine.NUM_BITS_STOCHASTIC_ROUNDING;
import static com.android.adservices.service.adselection.ImpressionReporterLegacy.CALLER_PACKAGE_NAME_MISMATCH;
import static com.android.adservices.service.adselection.ImpressionReporterLegacy.UNABLE_TO_FIND_AD_SELECTION_WITH_GIVEN_ID;
import static com.android.adservices.service.adselection.ReportEventDisabledImpl.API_DISABLED_MESSAGE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__OVERRIDE_AD_SELECTION_CONFIG_REMOTE_INFO;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REMOVE_AD_SELECTION_CONFIG_REMOTE_INFO_OVERRIDE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__RESET_ALL_AD_SELECTION_CONFIG_REMOTE_OVERRIDES;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__SET_APP_INSTALL_ADVERTISERS;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__UPDATE_AD_COUNTER_HISTOGRAM;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.anyInt;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.eq;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mock;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.when;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;

import android.adservices.adselection.AdSelectionCallback;
import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionConfigFixture;
import android.adservices.adselection.AdSelectionFromOutcomesConfig;
import android.adservices.adselection.AdSelectionFromOutcomesConfigFixture;
import android.adservices.adselection.AdSelectionFromOutcomesInput;
import android.adservices.adselection.AdSelectionOverrideCallback;
import android.adservices.adselection.AdSelectionResponse;
import android.adservices.adselection.CustomAudienceSignalsFixture;
import android.adservices.adselection.DecisionLogic;
import android.adservices.adselection.PerBuyerDecisionLogic;
import android.adservices.adselection.RemoveAdCounterHistogramOverrideInput;
import android.adservices.adselection.ReportImpressionCallback;
import android.adservices.adselection.ReportImpressionInput;
import android.adservices.adselection.ReportInteractionCallback;
import android.adservices.adselection.ReportInteractionInput;
import android.adservices.adselection.SetAdCounterHistogramOverrideInput;
import android.adservices.adselection.SetAppInstallAdvertisersInput;
import android.adservices.adselection.UpdateAdCounterHistogramInput;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CallingAppUidSupplierProcessImpl;
import android.adservices.common.CommonFixture;
import android.adservices.common.FledgeErrorResponse;
import android.adservices.common.FrequencyCapFilters;
import android.adservices.common.KeyedFrequencyCapFixture;
import android.adservices.http.MockWebServerRule;
import android.content.Context;
import android.net.Uri;
import android.os.LimitExceededException;
import android.os.Process;
import android.os.RemoteException;
import android.webkit.WebView;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.FlakyTest;

import com.android.adservices.LoggerFactory;
import com.android.adservices.MockWebServerRuleFactory;
import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.adselection.AdSelectionDatabase;
import com.android.adservices.data.adselection.AdSelectionDebugReportDao;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.adselection.AppInstallDao;
import com.android.adservices.data.adselection.CustomAudienceSignals;
import com.android.adservices.data.adselection.DBAdSelection;
import com.android.adservices.data.adselection.DBAdSelectionFromOutcomesOverride;
import com.android.adservices.data.adselection.DBAdSelectionOverride;
import com.android.adservices.data.adselection.DBBuyerDecisionLogic;
import com.android.adservices.data.adselection.DBReportingComputationInfo;
import com.android.adservices.data.adselection.FrequencyCapDao;
import com.android.adservices.data.adselection.SharedStorageDatabase;
import com.android.adservices.data.adselection.datahandlers.AdSelectionInitialization;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.CustomAudienceDatabase;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.data.encryptionkey.EncryptionKeyDao;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.signals.EncodedPayloadDao;
import com.android.adservices.data.signals.ProtectedSignalsDatabase;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.adselection.AppInstallAdvertisersSetterTest.SetAppInstallAdvertisersTestCallback;
import com.android.adservices.service.adselection.encryption.ObliviousHttpEncryptor;
import com.android.adservices.service.common.AdSelectionServiceFilter;
import com.android.adservices.service.common.AppImportanceFilter;
import com.android.adservices.service.common.AppImportanceFilter.WrongCallingApplicationStateException;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.common.RetryStrategyFactory;
import com.android.adservices.service.common.Throttler;
import com.android.adservices.service.common.cache.CacheProviderFactory;
import com.android.adservices.service.common.httpclient.AdServicesHttpClientRequest;
import com.android.adservices.service.common.httpclient.AdServicesHttpClientResponse;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.consent.AdServicesApiConsent;
import com.android.adservices.service.consent.AdServicesApiType;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.AdSelectionDevOverridesHelper;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.devapi.DevContextFilter;
import com.android.adservices.service.exception.FilterException;
import com.android.adservices.service.js.JSSandboxIsNotAvailableException;
import com.android.adservices.service.js.JSScriptEngine;
import com.android.adservices.service.kanon.KAnonSignJoinFactory;
import com.android.adservices.service.measurement.MeasurementImpl;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.AdServicesStatsLog;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mockwebserver.Dispatcher;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;
import com.google.mockwebserver.RecordedRequest;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.mockito.Spy;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class AdSelectionServiceImplTest {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    private static final int CALLER_UID = Process.myUid();
    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    private static final Clock CLOCK = Clock.fixed(Instant.now(), ZoneOffset.UTC);
    private static final Uri RENDER_URI = Uri.parse("https://test.com/advert/");
    private static final Instant ACTIVATION_TIME = CLOCK.instant().truncatedTo(ChronoUnit.MILLIS);
    private static final Uri BUYER_BIDDING_LOGIC_URI = Uri.parse("https://test.com");
    private static final long AD_SELECTION_ID = 1;
    private static final long INCORRECT_AD_SELECTION_ID = 2;
    private static final double BID = 5.0;
    private static final AdTechIdentifier SELLER_VALID = AdTechIdentifier.fromString("test.com");
    private static final Uri DECISION_LOGIC_URI_INCONSISTENT =
            Uri.parse("https://testinconsistent.com/test/decisions_logic_uris");
    private static final String DUMMY_DECISION_LOGIC_JS =
            "function test() { return \"hello world\"; }";
    private static final String DUMMY_SELECTION_LOGIC_JS =
            "function selection() { return \"hello world\"; }";
    private static final AdSelectionSignals DUMMY_TRUSTED_SCORING_SIGNALS =
            AdSelectionSignals.fromString(
                    "{\n"
                            + "\t\"render_uri_1\": \"signals_for_1\",\n"
                            + "\t\"render_uri_2\": \"signals_for_2\"\n"
                            + "}");
    private static final AdSelectionSignals DUMMY_SELECTION_SIGNALS =
            AdSelectionSignals.fromString("{\"selection\": \"signal_1\"}");
    private static final PerBuyerDecisionLogic BUYERS_DECISION_LOGIC =
            new PerBuyerDecisionLogic(
                    ImmutableMap.of(
                            CommonFixture.VALID_BUYER_1, new DecisionLogic("reportWin()"),
                            CommonFixture.VALID_BUYER_2, new DecisionLogic("reportWin()")));

    private static final long AD_SELECTION_ID_1 = 12345L;
    private static final long AD_SELECTION_ID_2 = 123456L;
    private static final long AD_SELECTION_ID_3 = 1234567L;

    // Auto-generated variable names are too long for lint check
    private static final int SHORT_API_NAME_OVERRIDE =
            AD_SERVICES_API_CALLED__API_NAME__OVERRIDE_AD_SELECTION_CONFIG_REMOTE_INFO;
    private static final int SHORT_API_NAME_REMOVE_OVERRIDE =
            AD_SERVICES_API_CALLED__API_NAME__REMOVE_AD_SELECTION_CONFIG_REMOTE_INFO_OVERRIDE;
    private static final int SHORT_API_NAME_RESET_ALL_OVERRIDES =
            AD_SERVICES_API_CALLED__API_NAME__RESET_ALL_AD_SELECTION_CONFIG_REMOTE_OVERRIDES;
    private static final String TIMEOUT_MESSAGE = "Timed out";

    // Interaction reporting contestants
    private static final String CLICK_EVENT_SELLER = "click_seller";
    private static final String HOVER_EVENT_SELLER = "hover_seller";

    private static final String CLICK_SELLER_PATH = "/click/seller";
    private static final String HOVER_SELLER_PATH = "/hover/seller";

    private static final String CLICK_EVENT_BUYER = "click_buyer";
    private static final String HOVER_EVENT_BUYER = "hover_buyer";

    private static final String CLICK_BUYER_PATH = "/click/buyer";
    private static final String HOVER_BUYER_PATH = "/hover/buyer";

    private static final String INTERACTION_DATA = "{\"key\":\"value\"}";

    private final ExecutorService mLightweightExecutorService =
            AdServicesExecutors.getLightWeightExecutor();
    private final ExecutorService mBackgroundExecutorService =
            AdServicesExecutors.getBackgroundExecutor();
    private final ScheduledThreadPoolExecutor mScheduledExecutor =
            AdServicesExecutors.getScheduler();
    private final String mSellerReportingPath = "/reporting/seller";
    private final String mBuyerReportingPath = "/reporting/buyer";
    private final String mFetchJavaScriptPathSeller = "/fetchJavascript/seller";
    private final String mFetchJavaScriptPathBuyer = "/fetchJavascript/buyer";
    private final String mFetchTrustedScoringSignalsPath = "/fetchTrustedSignals/";
    private final AdTechIdentifier mContextualSignals =
            AdTechIdentifier.fromString("{\"contextual_signals\":1}");
    private final int mBytesPerPeriod = 1;

    private final DevContext mDevContext = DevContext.createForDevOptionsDisabled();

    @Spy
    private final AdServicesHttpsClient mClientSpy =
            new AdServicesHttpsClient(
                    AdServicesExecutors.getBlockingExecutor(),
                    CacheProviderFactory.createNoOpCache());

    private final AdServicesLogger mAdServicesLoggerMock =
            ExtendedMockito.mock(AdServicesLoggerImpl.class);
    @Rule public MockWebServerRule mMockWebServerRule = MockWebServerRuleFactory.createForHttps();
    // This object access some system APIs
    @Mock public DevContextFilter mDevContextFilterMock;
    @Mock public AppImportanceFilter mAppImportanceFilterMock;

    private Flags mFlags;

    @Mock FledgeAuthorizationFilter mFledgeAuthorizationFilterMock;

    private MockitoSession mStaticMockSession = null;
    @Mock private ConsentManager mConsentManagerMock;
    private CustomAudienceDao mCustomAudienceDao;
    private EncodedPayloadDao mEncodedPayloadDao;
    private AdSelectionEntryDao mAdSelectionEntryDao;
    private AppInstallDao mAppInstallDao;
    private FrequencyCapDao mFrequencyCapDao;
    private EncryptionKeyDao mEncryptionKeyDao;
    private EnrollmentDao mEnrollmentDao;
    private AdSelectionConfig.Builder mAdSelectionConfigBuilder;
    private Uri mBiddingLogicUri;
    private CustomAudienceSignals mCustomAudienceSignals;
    private AdTechIdentifier mSeller;
    private AdFilteringFeatureFactory mAdFilteringFeatureFactory;

    @Mock private AdSelectionServiceFilter mAdSelectionServiceFilterMock;
    @Mock private ObliviousHttpEncryptor mObliviousHttpEncryptor;
    private MultiCloudSupportStrategy mMultiCloudSupportStrategy;
    @Mock private MeasurementImpl mMeasurementServiceMock;
    @Mock private AdSelectionDebugReportDao mAdSelectionDebugReportDao;
    @Mock private AdIdFetcher mAdIdFetcher;
    @Mock private KAnonSignJoinFactory mUnusedKAnonSignJoinFactory;

    private RetryStrategyFactory mRetryStrategyFactory;

    public AdSelectionServiceImplTest() {}

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setUp() {
        mFlags = new AdSelectionServicesTestsFlags(false);
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(JSScriptEngine.class)
                        .spyStatic(WebView.class)
                        .mockStatic(ConsentManager.class)
                        .mockStatic(FlagsFactory.class)
                        .mockStatic(MeasurementImpl.class)
                        .mockStatic(AppImportanceFilter.class)
                        // mAdServicesLoggerMock is not referenced in many tests
                        .strictness(Strictness.LENIENT)
                        .initMocks(this)
                        .startMocking();
        doReturn(mFlags).when(FlagsFactory::getFlags);
        mCustomAudienceDao =
                Room.inMemoryDatabaseBuilder(CONTEXT, CustomAudienceDatabase.class)
                        .addTypeConverter(new DBCustomAudience.Converters(true, true))
                        .build()
                        .customAudienceDao();
        mEncodedPayloadDao =
                Room.inMemoryDatabaseBuilder(CONTEXT, ProtectedSignalsDatabase.class)
                        .build()
                        .getEncodedPayloadDao();

        mAdSelectionEntryDao =
                Room.inMemoryDatabaseBuilder(
                                ApplicationProvider.getApplicationContext(),
                                AdSelectionDatabase.class)
                        .build()
                        .adSelectionEntryDao();
        SharedStorageDatabase sharedDb =
                Room.inMemoryDatabaseBuilder(
                                ApplicationProvider.getApplicationContext(),
                                SharedStorageDatabase.class)
                        .build();

        mAppInstallDao = sharedDb.appInstallDao();
        mFrequencyCapDao = sharedDb.frequencyCapDao();
        mEncryptionKeyDao = EncryptionKeyDao.getInstance(CONTEXT);
        mEnrollmentDao = EnrollmentDao.getInstance(CONTEXT);
        mAdFilteringFeatureFactory =
                new AdFilteringFeatureFactory(mAppInstallDao, mFrequencyCapDao, mFlags);

        mBiddingLogicUri = (mMockWebServerRule.uriForPath(mFetchJavaScriptPathBuyer));

        mCustomAudienceSignals =
                CustomAudienceSignalsFixture.aCustomAudienceSignalsBuilder()
                        .setBuyer(AdTechIdentifier.fromString(mBiddingLogicUri.getHost()))
                        .build();

        Map<AdTechIdentifier, AdSelectionSignals> perBuyerSignals =
                Map.of(
                        AdTechIdentifier.fromString("test.com"),
                        AdSelectionSignals.fromString("{\"buyer_signals\":1}"),
                        AdTechIdentifier.fromString("test2.com"),
                        AdSelectionSignals.fromString("{\"buyer_signals\":2}"),
                        AdTechIdentifier.fromString("test3.com"),
                        AdSelectionSignals.fromString("{\"buyer_signals\":3}"),
                        AdTechIdentifier.fromString(mBiddingLogicUri.getHost()),
                        AdSelectionSignals.fromString("{\"buyer_signals\":0}"));

        mSeller =
                AdTechIdentifier.fromString(
                        mMockWebServerRule.uriForPath(mFetchJavaScriptPathSeller).getHost());

        mAdSelectionConfigBuilder =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setSeller(mSeller)
                        .setDecisionLogicUri(
                                mMockWebServerRule.uriForPath(mFetchJavaScriptPathSeller))
                        .setTrustedScoringSignalsUri(
                                mMockWebServerRule.uriForPath(mFetchTrustedScoringSignalsPath))
                        .setPerBuyerSignals(perBuyerSignals);

        doNothing()
                .when(mAdSelectionServiceFilterMock)
                .filterRequest(
                        mSeller,
                        TEST_PACKAGE_NAME,
                        true,
                        true,
                        CALLER_UID,
                        AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION,
                        Throttler.ApiKey.FLEDGE_API_REPORT_IMPRESSIONS,
                        DevContext.createForDevOptionsDisabled());

        when(ConsentManager.getInstance()).thenReturn(mConsentManagerMock);
        when(AppImportanceFilter.create(any(), anyInt(), any()))
                .thenReturn(mAppImportanceFilterMock);
        doNothing()
                .when(mAppImportanceFilterMock)
                .assertCallerIsInForeground(anyInt(), anyInt(), any());
        when(mDevContextFilterMock.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());
        mMultiCloudSupportStrategy =
                MultiCloudTestStrategyFactory.getDisabledTestStrategy(mObliviousHttpEncryptor);
        mRetryStrategyFactory = RetryStrategyFactory.createInstanceForTesting();
    }

    @After
    public void tearDown() {
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
    }

    @Test
    public void testReportImpressionSuccessWithRegisterAdBeaconDisabled() throws Exception {
        Assume.assumeTrue(JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());
        // Re init flags with registerAdBeaconDisabled
        boolean enrollmentCheckDisabled = false;
        mFlags =
                new AdSelectionServicesTestsFlags(enrollmentCheckDisabled) {
                    @Override
                    public boolean getFledgeRegisterAdBeaconEnabled() {
                        return false;
                    }
                };
        Uri sellerReportingUri = mMockWebServerRule.uriForPath(mSellerReportingPath);
        Uri buyerReportingUri = mMockWebServerRule.uriForPath(mBuyerReportingPath);

        Uri biddingLogicUri = (mMockWebServerRule.uriForPath(mFetchJavaScriptPathBuyer));

        String sellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) {"
                        + " \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + sellerReportingUri
                        + "' } };\n"
                        + "}";

        String buyerDecisionLogicJs =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,"
                        + " contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'results': {'reporting_uri': '"
                        + buyerReportingUri
                        + "' } };\n"
                        + "}";

        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        List.of(
                                new MockResponse().setBody(sellerDecisionLogicJs),
                                new MockResponse(),
                                new MockResponse()));

        DBBuyerDecisionLogic dbBuyerDecisionLogic =
                new DBBuyerDecisionLogic.Builder()
                        .setBiddingLogicUri(biddingLogicUri)
                        .setBuyerDecisionLogicJs(buyerDecisionLogicJs)
                        .build();

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(mCustomAudienceSignals)
                        .setBuyerContextualSignals(mContextualSignals.toString())
                        .setBiddingLogicUri(biddingLogicUri)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        mFlags,
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

        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();

        // Count down callback + log interaction.
        ReportImpressionTestCallback callback =
                callReportImpression(adSelectionService, input, true);

        assertTrue(callback.mIsSuccess);
        RecordedRequest fetchRequest = server.takeRequest();
        assertEquals(mFetchJavaScriptPathSeller, fetchRequest.getPath());

        List<String> notifications =
                ImmutableList.of(server.takeRequest().getPath(), server.takeRequest().getPath());

        assertThat(notifications).containsExactly(mSellerReportingPath, mBuyerReportingPath);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        anyInt());
    }

    @Test
    public void testReportImpressionSuccessfullyReportsAdCost() throws Exception {
        Assume.assumeTrue(JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());

        Uri sellerReportingUri = mMockWebServerRule.uriForPath(mSellerReportingPath);
        Uri buyerReportingUri = mMockWebServerRule.uriForPath(mBuyerReportingPath);

        Uri biddingLogicUri = (mMockWebServerRule.uriForPath(mFetchJavaScriptPathBuyer));

        String sellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) {"
                        + " \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + sellerReportingUri
                        + "' } };\n"
                        + "}";

        String buyerDecisionLogicJs =
                "function reportWin(ad_selection_signals, per_buyer_signals, "
                        + "signals_for_buyer,\n"
                        + "    contextual_signals, custom_audience_reporting_signals) {\n"
                        + "    let reporting_address = '"
                        + buyerReportingUri
                        + "';\n"
                        + "    return {'status': 0, 'results': {'reporting_uri':\n"
                        + "                reporting_address + '?adCost=' +"
                        + " contextual_signals.adCost} };\n"
                        + "}";

        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        List.of(
                                new MockResponse().setBody(sellerDecisionLogicJs),
                                new MockResponse(),
                                new MockResponse()));

        DBBuyerDecisionLogic dbBuyerDecisionLogic =
                new DBBuyerDecisionLogic.Builder()
                        .setBiddingLogicUri(biddingLogicUri)
                        .setBuyerDecisionLogicJs(buyerDecisionLogicJs)
                        .build();

        AdCost adCost = new AdCost(1.1, NUM_BITS_STOCHASTIC_ROUNDING);
        BuyerContextualSignals buyerContextualSignals =
                BuyerContextualSignals.builder().setAdCost(adCost).build();

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(mCustomAudienceSignals)
                        .setBuyerContextualSignals(buyerContextualSignals.toString())
                        .setBiddingLogicUri(biddingLogicUri)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        mFlags,
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

        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();

        // Count down callback + log interaction.
        ReportImpressionTestCallback callback =
                callReportImpression(adSelectionService, input, true);

        assertTrue(callback.mIsSuccess);
        RecordedRequest fetchRequest = server.takeRequest();
        assertEquals(mFetchJavaScriptPathSeller, fetchRequest.getPath());

        List<String> notifications =
                ImmutableList.of(server.takeRequest().getPath(), server.takeRequest().getPath());

        String buyerReportingPathWithAdCost =
                mBuyerReportingPath + "?adCost=" + buyerContextualSignals.getAdCost().toString();

        assertThat(notifications)
                .containsExactly(mSellerReportingPath, buyerReportingPathWithAdCost);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        anyInt());
    }

    @Test
    @FlakyTest(bugId = 315521295)
    public void testReportImpressionSuccessfullyReportsDataVersionHeader() throws Exception {
        Assume.assumeTrue(JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());

        Uri sellerReportingUri = mMockWebServerRule.uriForPath(mSellerReportingPath);
        Uri buyerReportingUri = mMockWebServerRule.uriForPath(mBuyerReportingPath);

        Uri biddingLogicUri = (mMockWebServerRule.uriForPath(mFetchJavaScriptPathBuyer));

        String sellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_uri, bid,"
                        + " contextual_signals) {\n"
                        + "    let reporting_address = '"
                        + sellerReportingUri
                        + "';\n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri':\n"
                        + "                reporting_address + '?dataVersion=' +"
                        + " contextual_signals.dataVersion} };\n"
                        + "}";

        String buyerDecisionLogicJs =
                "function reportWin(ad_selection_signals, per_buyer_signals,"
                        + " signals_for_buyer, contextual_signals, custom_audience_signals) {\n"
                        + "    let reporting_address = '"
                        + buyerReportingUri
                        + "';\n"
                        + "    return {'status': 0, 'results': {'reporting_uri':\n"
                        + "                reporting_address + '?dataVersion=' +"
                        + " contextual_signals.dataVersion} };\n"
                        + "}";

        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        List.of(
                                new MockResponse().setBody(sellerDecisionLogicJs),
                                new MockResponse(),
                                new MockResponse()));

        DBBuyerDecisionLogic dbBuyerDecisionLogic =
                new DBBuyerDecisionLogic.Builder()
                        .setBiddingLogicUri(biddingLogicUri)
                        .setBuyerDecisionLogicJs(buyerDecisionLogicJs)
                        .build();

        BuyerContextualSignals buyerContextualSignals =
                BuyerContextualSignals.builder().setDataVersion(DATA_VERSION_1).build();

        SellerContextualSignals sellerContextualSignals =
                SellerContextualSignals.builder().setDataVersion(DATA_VERSION_2).build();

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(mCustomAudienceSignals)
                        .setBuyerContextualSignals(buyerContextualSignals.toString())
                        .setSellerContextualSignals(sellerContextualSignals.toString())
                        .setBiddingLogicUri(biddingLogicUri)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        mFlags,
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

        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();

        // Count down callback + log interaction.
        ReportImpressionTestCallback callback =
                callReportImpression(adSelectionService, input, true);

        assertTrue(callback.mIsSuccess);
        RecordedRequest fetchRequest = server.takeRequest();
        assertEquals(mFetchJavaScriptPathSeller, fetchRequest.getPath());

        List<String> notifications =
                ImmutableList.of(server.takeRequest().getPath(), server.takeRequest().getPath());

        String buyerReportingPathWithDataVersion =
                mBuyerReportingPath
                        + "?dataVersion="
                        + buyerContextualSignals.getDataVersion().toString();

        String sellerReportingPathWithDataVersion =
                mSellerReportingPath
                        + "?dataVersion="
                        + sellerContextualSignals.getDataVersion().toString();

        assertThat(notifications)
                .containsExactly(
                        sellerReportingPathWithDataVersion, buyerReportingPathWithDataVersion);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        anyInt());
    }

    @Test
    @FlakyTest(bugId = 315521295)
    public void testReportImpressionSuccessfullyReportsSellerDataVersionHeader() throws Exception {
        Assume.assumeTrue(JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());

        Uri sellerReportingUri = mMockWebServerRule.uriForPath(mSellerReportingPath);
        Uri buyerReportingUri = mMockWebServerRule.uriForPath(mBuyerReportingPath);

        Uri biddingLogicUri = (mMockWebServerRule.uriForPath(mFetchJavaScriptPathBuyer));

        String sellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) "
                        + "{\n"
                        + "    let reporting_address = '"
                        + sellerReportingUri
                        + "';\n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':\n"
                        + "  '{\"signals_for_buyer\":1}', 'reporting_uri': reporting_address + "
                        + "\"?dataVersion=\" + contextual_signals.dataVersion} };\n"
                        + " }";

        String buyerDecisionLogicJs =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,"
                        + " contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'results': {'reporting_uri': '"
                        + buyerReportingUri
                        + "' } };\n"
                        + "}";

        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        List.of(
                                new MockResponse().setBody(sellerDecisionLogicJs),
                                new MockResponse(),
                                new MockResponse()));

        DBBuyerDecisionLogic dbBuyerDecisionLogic =
                new DBBuyerDecisionLogic.Builder()
                        .setBiddingLogicUri(biddingLogicUri)
                        .setBuyerDecisionLogicJs(buyerDecisionLogicJs)
                        .build();

        SellerContextualSignals sellerContextualSignals =
                SellerContextualSignals.builder().setDataVersion(DATA_VERSION_1).build();

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(mCustomAudienceSignals)
                        .setSellerContextualSignals(sellerContextualSignals.toString())
                        .setBuyerContextualSignals("{}")
                        .setBiddingLogicUri(biddingLogicUri)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        mFlags,
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

        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();

        // Count down callback + log interaction.
        ReportImpressionTestCallback callback =
                callReportImpression(adSelectionService, input, true);

        assertTrue(callback.mIsSuccess);
        RecordedRequest fetchRequest = server.takeRequest();
        assertEquals(mFetchJavaScriptPathSeller, fetchRequest.getPath());

        List<String> notifications =
                ImmutableList.of(server.takeRequest().getPath(), server.takeRequest().getPath());

        String sellerReportingPathWithDataVersion =
                mSellerReportingPath
                        + "?dataVersion="
                        + sellerContextualSignals.getDataVersion().toString();

        assertThat(notifications)
                .containsExactly(mBuyerReportingPath, sellerReportingPathWithDataVersion);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        anyInt());
    }

    @Test
    @FlakyTest(bugId = 315521295)
    public void testReportImpressionSuccessWithRegisterAdBeaconEnabled() throws Exception {
        Assume.assumeTrue(JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());

        Uri sellerReportingUri = mMockWebServerRule.uriForPath(mSellerReportingPath);
        Uri buyerReportingUri = mMockWebServerRule.uriForPath(mBuyerReportingPath);

        Uri biddingLogicUri = (mMockWebServerRule.uriForPath(mFetchJavaScriptPathBuyer));

        String sellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) {"
                        + " \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + sellerReportingUri
                        + "' } };\n"
                        + "}";

        String buyerDecisionLogicJs =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,"
                        + " contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'results': {'reporting_uri': '"
                        + buyerReportingUri
                        + "' } };\n"
                        + "}";

        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        List.of(
                                new MockResponse().setBody(sellerDecisionLogicJs),
                                new MockResponse(),
                                new MockResponse()));

        DBBuyerDecisionLogic dbBuyerDecisionLogic =
                new DBBuyerDecisionLogic.Builder()
                        .setBiddingLogicUri(biddingLogicUri)
                        .setBuyerDecisionLogicJs(buyerDecisionLogicJs)
                        .build();

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(mCustomAudienceSignals)
                        .setBuyerContextualSignals(mContextualSignals.toString())
                        .setBiddingLogicUri(biddingLogicUri)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        mFlags,
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

        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();

        // Count down callback + log interaction.
        ReportImpressionTestCallback callback =
                callReportImpression(adSelectionService, input, true);

        assertTrue(callback.mIsSuccess);
        RecordedRequest fetchRequest = server.takeRequest();
        assertEquals(mFetchJavaScriptPathSeller, fetchRequest.getPath());

        List<String> notifications =
                ImmutableList.of(server.takeRequest().getPath(), server.takeRequest().getPath());

        assertThat(notifications).containsExactly(mSellerReportingPath, mBuyerReportingPath);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        anyInt());
    }

    @Test
    public void testReportImpressionSuccessWithUnifiedTablesEnabled() throws Exception {
        Assume.assumeTrue(JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());

        Uri sellerReportingUri = mMockWebServerRule.uriForPath(mSellerReportingPath);
        Uri buyerReportingUri = mMockWebServerRule.uriForPath(mBuyerReportingPath);

        Uri biddingLogicUri = (mMockWebServerRule.uriForPath(mFetchJavaScriptPathBuyer));

        String sellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) {"
                        + " \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + sellerReportingUri
                        + "' } };\n"
                        + "}";

        String buyerDecisionLogicJs =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,"
                        + " contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'results': {'reporting_uri': '"
                        + buyerReportingUri
                        + "' } };\n"
                        + "}";

        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        List.of(
                                new MockResponse().setBody(sellerDecisionLogicJs),
                                new MockResponse(),
                                new MockResponse()));

        BuyerContextualSignals buyerContextualSignals =
                BuyerContextualSignals.builder().setDataVersion(DATA_VERSION_1).build();

        SellerContextualSignals sellerContextualSignals =
                SellerContextualSignals.builder().setDataVersion(DATA_VERSION_2).build();

        AdSelectionInitialization adSelectionInitialization =
                AdSelectionInitialization.builder()
                        .setSeller(mSeller)
                        .setCreationInstant(ACTIVATION_TIME)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();

        mAdSelectionEntryDao.persistAdSelectionInitialization(
                AD_SELECTION_ID, adSelectionInitialization);

        DBReportingComputationInfo dbReportingComputationInfo =
                DBReportingComputationInfo.builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(mCustomAudienceSignals)
                        .setBuyerContextualSignals(buyerContextualSignals.toString())
                        .setSellerContextualSignals(sellerContextualSignals.toString())
                        .setBiddingLogicUri(biddingLogicUri)
                        .setBuyerDecisionLogicJs(buyerDecisionLogicJs)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .build();

        mAdSelectionEntryDao.insertDBReportingComputationInfo(dbReportingComputationInfo);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        // Init client with true for shouldUseUnifiedTables
        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        mFlags,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterMock,
                        mAdSelectionServiceFilterMock,
                        mAdFilteringFeatureFactory,
                        mConsentManagerMock,
                        mMultiCloudSupportStrategy,
                        mAdSelectionDebugReportDao,
                        mAdIdFetcher,
                        mUnusedKAnonSignJoinFactory,
                        true,
                        mRetryStrategyFactory);

        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();

        // Count down callback + log interaction.
        ReportImpressionTestCallback callback =
                callReportImpression(adSelectionService, input, true);

        assertTrue(callback.mIsSuccess);
        RecordedRequest fetchRequest = server.takeRequest();
        assertEquals(mFetchJavaScriptPathSeller, fetchRequest.getPath());

        List<String> notifications =
                ImmutableList.of(server.takeRequest().getPath(), server.takeRequest().getPath());

        assertThat(notifications).containsExactly(mSellerReportingPath, mBuyerReportingPath);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        anyInt());
    }

    @Test
    public void testReportImpressionFailsWithIncorrectPackageNameUnifiedTablesEnabled()
            throws Exception {
        Assume.assumeTrue(JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());
        String otherPackageName = CommonFixture.TEST_PACKAGE_NAME + "incorrectPackage";
        Uri sellerReportingUri = mMockWebServerRule.uriForPath(mSellerReportingPath);
        Uri buyerReportingUri = mMockWebServerRule.uriForPath(mBuyerReportingPath);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        String sellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) {"
                        + " \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + sellerReportingUri
                        + "' } };\n"
                        + "}";

        String buyerDecisionLogicJs =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,"
                        + " contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'results': {'reporting_uri': '"
                        + buyerReportingUri
                        + "' } };\n"
                        + "}";

        mMockWebServerRule.startMockWebServer(
                List.of(
                        new MockResponse().setBody(sellerDecisionLogicJs),
                        new MockResponse(),
                        new MockResponse()));

        AdSelectionInitialization adSelectionInitialization =
                AdSelectionInitialization.builder()
                        .setSeller(mSeller)
                        .setCreationInstant(ACTIVATION_TIME)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();

        mAdSelectionEntryDao.persistAdSelectionInitialization(
                AD_SELECTION_ID, adSelectionInitialization);

        DBReportingComputationInfo dbReportingComputationInfo =
                DBReportingComputationInfo.builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(mCustomAudienceSignals)
                        .setBuyerContextualSignals(mContextualSignals.toString())
                        .setSellerContextualSignals(mContextualSignals.toString())
                        .setBiddingLogicUri(buyerReportingUri)
                        .setBuyerDecisionLogicJs(buyerDecisionLogicJs)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .build();

        mAdSelectionEntryDao.insertDBReportingComputationInfo(dbReportingComputationInfo);

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        // Init client with true for shouldUseUnifiedTables
        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        mFlags,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterMock,
                        mAdSelectionServiceFilterMock,
                        mAdFilteringFeatureFactory,
                        mConsentManagerMock,
                        mMultiCloudSupportStrategy,
                        mAdSelectionDebugReportDao,
                        mAdIdFetcher,
                        mUnusedKAnonSignJoinFactory,
                        true,
                        mRetryStrategyFactory);

        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(otherPackageName)
                        .build();

        ReportImpressionTestCallback callback = callReportImpression(adSelectionService, input);

        assertFalse(callback.mIsSuccess);
        assertEquals(STATUS_INVALID_ARGUMENT, callback.mFledgeErrorResponse.getStatusCode());
        assertEquals(CALLER_PACKAGE_NAME_MISMATCH, callback.mFledgeErrorResponse.getErrorMessage());
    }

    @Test
    public void testReportImpressionFailsWhenDataIsInOldTablesUnifiedTablesEnabled()
            throws Exception {
        Assume.assumeTrue(JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());

        Uri sellerReportingUri = mMockWebServerRule.uriForPath(mSellerReportingPath);
        Uri buyerReportingUri = mMockWebServerRule.uriForPath(mBuyerReportingPath);

        Uri biddingLogicUri = (mMockWebServerRule.uriForPath(mFetchJavaScriptPathBuyer));

        String sellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) {"
                        + " \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + sellerReportingUri
                        + "' } };\n"
                        + "}";

        String buyerDecisionLogicJs =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,"
                        + " contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'results': {'reporting_uri': '"
                        + buyerReportingUri
                        + "' } };\n"
                        + "}";

        mMockWebServerRule.startMockWebServer(
                List.of(
                        new MockResponse().setBody(sellerDecisionLogicJs),
                        new MockResponse(),
                        new MockResponse()));

        DBBuyerDecisionLogic dbBuyerDecisionLogic =
                new DBBuyerDecisionLogic.Builder()
                        .setBiddingLogicUri(biddingLogicUri)
                        .setBuyerDecisionLogicJs(buyerDecisionLogicJs)
                        .build();

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(mCustomAudienceSignals)
                        .setBuyerContextualSignals(mContextualSignals.toString())
                        .setBiddingLogicUri(biddingLogicUri)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        // Init client with true for shouldUseUnifiedTables
        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        mFlags,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterMock,
                        mAdSelectionServiceFilterMock,
                        mAdFilteringFeatureFactory,
                        mConsentManagerMock,
                        mMultiCloudSupportStrategy,
                        mAdSelectionDebugReportDao,
                        mAdIdFetcher,
                        mUnusedKAnonSignJoinFactory,
                        true,
                        mRetryStrategyFactory);

        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();

        // Count down callback + log interaction.
        ReportImpressionTestCallback callback =
                callReportImpression(adSelectionService, input, true);

        assertFalse(callback.mIsSuccess);
        assertEquals(STATUS_INVALID_ARGUMENT, callback.mFledgeErrorResponse.getStatusCode());
        assertEquals(
                UNABLE_TO_FIND_AD_SELECTION_WITH_GIVEN_ID,
                callback.mFledgeErrorResponse.getErrorMessage());
    }

    @Test
    public void testReportImpressionSuccessWithEmptyBuyerSignals() throws Exception {
        Assume.assumeTrue(JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());

        Uri sellerReportingUri = mMockWebServerRule.uriForPath(mSellerReportingPath);
        Uri buyerReportingUri = mMockWebServerRule.uriForPath(mBuyerReportingPath);

        Uri biddingLogicUri = (mMockWebServerRule.uriForPath(mFetchJavaScriptPathBuyer));

        String sellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) {"
                        + " \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + sellerReportingUri
                        + "' } };\n"
                        + "}";

        String buyerDecisionLogicJs =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,"
                        + " contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'results': {'reporting_uri': '"
                        + buyerReportingUri
                        + "' } };\n"
                        + "}";

        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        List.of(
                                new MockResponse().setBody(sellerDecisionLogicJs),
                                new MockResponse(),
                                new MockResponse()));

        DBBuyerDecisionLogic dbBuyerDecisionLogic =
                new DBBuyerDecisionLogic.Builder()
                        .setBiddingLogicUri(biddingLogicUri)
                        .setBuyerDecisionLogicJs(buyerDecisionLogicJs)
                        .build();

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(mCustomAudienceSignals)
                        .setBuyerContextualSignals(mContextualSignals.toString())
                        .setBiddingLogicUri(biddingLogicUri)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        // Create empty per buyer signals
        Map<AdTechIdentifier, AdSelectionSignals> emptyPerBuyerSignals = new HashMap<>();

        AdSelectionConfig adSelectionConfig =
                mAdSelectionConfigBuilder.setPerBuyerSignals(emptyPerBuyerSignals).build();

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        mFlags,
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

        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();

        // Count down callback + log interaction.
        ReportImpressionTestCallback callback =
                callReportImpression(adSelectionService, input, true);

        assertTrue(callback.mIsSuccess);
        RecordedRequest fetchRequest = server.takeRequest();
        assertEquals(mFetchJavaScriptPathSeller, fetchRequest.getPath());

        List<String> notifications =
                ImmutableList.of(server.takeRequest().getPath(), server.takeRequest().getPath());

        assertThat(notifications).containsExactly(mSellerReportingPath, mBuyerReportingPath);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        anyInt());
    }

    @Test
    public void testReportImpressionSuccessAndDoesNotCrashAfterSellerThrowsAnException()
            throws Exception {
        Assume.assumeTrue(JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());
        Uri sellerReportingUri = mMockWebServerRule.uriForPath(mSellerReportingPath);
        Uri buyerReportingUri = mMockWebServerRule.uriForPath(mBuyerReportingPath);

        ListenableFuture<Void> failedFuture =
                Futures.submit(
                        () -> {
                            throw new IllegalStateException("Exception for test!");
                        },
                        mLightweightExecutorService);

        doReturn(failedFuture).when(mClientSpy).getAndReadNothing(sellerReportingUri, mDevContext);

        Uri biddingLogicUri = (mMockWebServerRule.uriForPath(mFetchJavaScriptPathBuyer));

        String sellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) {"
                        + " \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + sellerReportingUri
                        + "' } };\n"
                        + "}";

        String buyerDecisionLogicJs =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,"
                        + " contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'results': {'reporting_uri': '"
                        + buyerReportingUri
                        + "' } };\n"
                        + "}";

        // Start mockserver with throttling for seller reporting
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        List.of(
                                new MockResponse().setBody(sellerDecisionLogicJs),
                                new MockResponse(),
                                new MockResponse()));

        DBBuyerDecisionLogic dbBuyerDecisionLogic =
                new DBBuyerDecisionLogic.Builder()
                        .setBiddingLogicUri(biddingLogicUri)
                        .setBuyerDecisionLogicJs(buyerDecisionLogicJs)
                        .build();

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(mCustomAudienceSignals)
                        .setBuyerContextualSignals(mContextualSignals.toString())
                        .setBiddingLogicUri(biddingLogicUri)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        mFlags,
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

        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();

        // Count down callback + log interaction.
        ReportImpressionTestCallback callback =
                callReportImpression(adSelectionService, input, true);

        assertTrue(callback.mIsSuccess);
        RecordedRequest fetchRequest = server.takeRequest();
        assertEquals(mFetchJavaScriptPathSeller, fetchRequest.getPath());

        assertEquals(server.takeRequest().getPath(), mBuyerReportingPath);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        anyInt());
    }

    @Test
    @FlakyTest(bugId = 315521295)
    public void testReportImpressionSuccessAndDoesNotCrashAfterBuyerReportThrowsAnException()
            throws Exception {
        Assume.assumeTrue(JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());
        Uri sellerReportingUri = mMockWebServerRule.uriForPath(mSellerReportingPath);
        Uri buyerReportingUri = mMockWebServerRule.uriForPath(mBuyerReportingPath);

        ListenableFuture<Void> failedFuture =
                Futures.submit(
                        () -> {
                            throw new IllegalStateException("Exception for test!");
                        },
                        mLightweightExecutorService);

        doReturn(failedFuture).when(mClientSpy).getAndReadNothing(buyerReportingUri, mDevContext);

        Uri biddingLogicUri = (mMockWebServerRule.uriForPath(mFetchJavaScriptPathBuyer));

        String sellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) {"
                        + " \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + sellerReportingUri
                        + "' } };\n"
                        + "}";

        String buyerDecisionLogicJs =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,"
                        + " contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'results': {'reporting_uri': '"
                        + buyerReportingUri
                        + "' } };\n"
                        + "}";

        // Start mockserver with throttling for buyer reporting
        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        List.of(
                                new MockResponse().setBody(sellerDecisionLogicJs),
                                new MockResponse(),
                                new MockResponse()));

        DBBuyerDecisionLogic dbBuyerDecisionLogic =
                new DBBuyerDecisionLogic.Builder()
                        .setBiddingLogicUri(biddingLogicUri)
                        .setBuyerDecisionLogicJs(buyerDecisionLogicJs)
                        .build();

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(mCustomAudienceSignals)
                        .setBuyerContextualSignals(mContextualSignals.toString())
                        .setBiddingLogicUri(biddingLogicUri)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        mFlags,
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

        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();

        // Count down callback + log interaction.
        ReportImpressionTestCallback callback =
                callReportImpression(adSelectionService, input, true);

        assertTrue(callback.mIsSuccess);
        RecordedRequest fetchRequest = server.takeRequest();
        assertEquals(mFetchJavaScriptPathSeller, fetchRequest.getPath());

        assertEquals(server.takeRequest().getPath(), mSellerReportingPath);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        anyInt());
    }

    @Test
    public void testReportImpressionSuccessfullyRegistersEventUris() throws Exception {
        Assume.assumeTrue(JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());
        Uri sellerReportingUri = mMockWebServerRule.uriForPath(mSellerReportingPath);
        Uri buyerReportingUri = mMockWebServerRule.uriForPath(mBuyerReportingPath);

        Uri biddingLogicUri = (mMockWebServerRule.uriForPath(mFetchJavaScriptPathBuyer));

        Uri clickUriSeller = mMockWebServerRule.uriForPath(CLICK_SELLER_PATH);
        Uri hoverUriSeller = mMockWebServerRule.uriForPath(HOVER_SELLER_PATH);
        Uri clickUriBuyer = mMockWebServerRule.uriForPath(CLICK_BUYER_PATH);
        Uri hoverUriBuyer = mMockWebServerRule.uriForPath(HOVER_BUYER_PATH);

        String sellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) "
                        + "{\n"
                        + "const beacons = {'click_seller': '"
                        + clickUriSeller
                        + "', 'hover_seller': '"
                        + hoverUriSeller
                        + "'};\n"
                        + "registerAdBeacon(beacons);"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + sellerReportingUri
                        + "' } };\n"
                        + "}";

        String buyerDecisionLogicJs =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer ,"
                        + "contextual_signals, custom_audience_signals) {\n"
                        + "const beacons = {'click_buyer': '"
                        + clickUriBuyer
                        + "', 'hover_buyer': '"
                        + hoverUriBuyer
                        + "'};\n"
                        + "registerAdBeacon(beacons);"
                        + " return {'status': 0, 'results': {'reporting_uri': '"
                        + buyerReportingUri
                        + "' } };\n"
                        + "}";

        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        List.of(
                                new MockResponse().setBody(sellerDecisionLogicJs),
                                new MockResponse(),
                                new MockResponse()));

        DBBuyerDecisionLogic dbBuyerDecisionLogic =
                new DBBuyerDecisionLogic.Builder()
                        .setBiddingLogicUri(biddingLogicUri)
                        .setBuyerDecisionLogicJs(buyerDecisionLogicJs)
                        .build();

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(mCustomAudienceSignals)
                        .setBuyerContextualSignals(mContextualSignals.toString())
                        .setBiddingLogicUri(biddingLogicUri)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        mFlags,
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

        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();

        // Count down callback + log interaction.
        ReportImpressionTestCallback callback =
                callReportImpression(adSelectionService, input, true);

        assertTrue(callback.mIsSuccess);

        // Check that database has correct seller registered events
        assertTrue(
                mAdSelectionEntryDao.doesRegisteredAdInteractionExist(
                        AD_SELECTION_ID, CLICK_EVENT_SELLER, FLAG_REPORTING_DESTINATION_SELLER));
        assertEquals(
                clickUriSeller,
                mAdSelectionEntryDao.getRegisteredAdInteractionUri(
                        AD_SELECTION_ID, CLICK_EVENT_SELLER, FLAG_REPORTING_DESTINATION_SELLER));

        assertTrue(
                mAdSelectionEntryDao.doesRegisteredAdInteractionExist(
                        AD_SELECTION_ID, HOVER_EVENT_SELLER, FLAG_REPORTING_DESTINATION_SELLER));
        assertEquals(
                hoverUriSeller,
                mAdSelectionEntryDao.getRegisteredAdInteractionUri(
                        AD_SELECTION_ID, HOVER_EVENT_SELLER, FLAG_REPORTING_DESTINATION_SELLER));

        // Check that database has correct buyer registered events
        assertTrue(
                mAdSelectionEntryDao.doesRegisteredAdInteractionExist(
                        AD_SELECTION_ID, CLICK_EVENT_BUYER, FLAG_REPORTING_DESTINATION_BUYER));
        assertEquals(
                clickUriBuyer,
                mAdSelectionEntryDao.getRegisteredAdInteractionUri(
                        AD_SELECTION_ID, CLICK_EVENT_BUYER, FLAG_REPORTING_DESTINATION_BUYER));

        assertTrue(
                mAdSelectionEntryDao.doesRegisteredAdInteractionExist(
                        AD_SELECTION_ID, HOVER_EVENT_BUYER, FLAG_REPORTING_DESTINATION_BUYER));
        assertEquals(
                hoverUriBuyer,
                mAdSelectionEntryDao.getRegisteredAdInteractionUri(
                        AD_SELECTION_ID, HOVER_EVENT_BUYER, FLAG_REPORTING_DESTINATION_BUYER));

        RecordedRequest fetchRequest = server.takeRequest();
        assertEquals(mFetchJavaScriptPathSeller, fetchRequest.getPath());

        List<String> notifications =
                ImmutableList.of(server.takeRequest().getPath(), server.takeRequest().getPath());

        assertThat(notifications).containsExactly(mSellerReportingPath, mBuyerReportingPath);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        anyInt());
    }

    @Test
    public void testReportImpressionFailsWithRegisterAdBeaconDisabled() throws Exception {
        Assume.assumeTrue(JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());
        // Re init flags with registerAdBeaconDisabled
        boolean enrollmentCheckDisabled = false;
        mFlags =
                new AdSelectionServicesTestsFlags(enrollmentCheckDisabled) {
                    @Override
                    public boolean getFledgeRegisterAdBeaconEnabled() {
                        return false;
                    }
                };

        Uri sellerReportingUri = mMockWebServerRule.uriForPath(mSellerReportingPath);
        Uri buyerReportingUri = mMockWebServerRule.uriForPath(mBuyerReportingPath);

        Uri biddingLogicUri = (mMockWebServerRule.uriForPath(mFetchJavaScriptPathBuyer));

        Uri clickUriSeller = mMockWebServerRule.uriForPath(CLICK_SELLER_PATH);
        Uri hoverUriSeller = mMockWebServerRule.uriForPath(HOVER_SELLER_PATH);
        Uri clickUriBuyer = mMockWebServerRule.uriForPath(CLICK_BUYER_PATH);
        Uri hoverUriBuyer = mMockWebServerRule.uriForPath(HOVER_BUYER_PATH);

        String sellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) "
                        + "{\n"
                        + "const beacons = {'click_seller': '"
                        + clickUriSeller
                        + "', 'hover_seller': '"
                        + hoverUriSeller
                        + "'};\n"
                        + "registerAdBeacon(beacons);"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + sellerReportingUri
                        + "' } };\n"
                        + "}";

        String buyerDecisionLogicJs =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer ,"
                        + "contextual_signals, custom_audience_signals) {\n"
                        + "const beacons = {'click_buyer': '"
                        + clickUriBuyer
                        + "', 'hover_buyer': '"
                        + hoverUriBuyer
                        + "'};\n"
                        + "registerAdBeacon(beacons);"
                        + " return {'status': 0, 'results': {'reporting_uri': '"
                        + buyerReportingUri
                        + "' } };\n"
                        + "}";

        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        List.of(
                                new MockResponse().setBody(sellerDecisionLogicJs),
                                new MockResponse(),
                                new MockResponse()));

        DBBuyerDecisionLogic dbBuyerDecisionLogic =
                new DBBuyerDecisionLogic.Builder()
                        .setBiddingLogicUri(biddingLogicUri)
                        .setBuyerDecisionLogicJs(buyerDecisionLogicJs)
                        .build();

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(mCustomAudienceSignals)
                        .setBuyerContextualSignals(mContextualSignals.toString())
                        .setBiddingLogicUri(biddingLogicUri)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        mFlags,
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

        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();

        ReportImpressionTestCallback callback = callReportImpression(adSelectionService, input);

        assertFalse(callback.mIsSuccess);
        assertEquals(STATUS_INTERNAL_ERROR, callback.mFledgeErrorResponse.getStatusCode());

        // Check that no events are registered
        assertFalse(
                mAdSelectionEntryDao.doesRegisteredAdInteractionExist(
                        AD_SELECTION_ID, CLICK_EVENT_SELLER, FLAG_REPORTING_DESTINATION_SELLER));

        assertFalse(
                mAdSelectionEntryDao.doesRegisteredAdInteractionExist(
                        AD_SELECTION_ID, HOVER_EVENT_SELLER, FLAG_REPORTING_DESTINATION_SELLER));

        assertFalse(
                mAdSelectionEntryDao.doesRegisteredAdInteractionExist(
                        AD_SELECTION_ID, CLICK_EVENT_BUYER, FLAG_REPORTING_DESTINATION_BUYER));

        assertFalse(
                mAdSelectionEntryDao.doesRegisteredAdInteractionExist(
                        AD_SELECTION_ID, HOVER_EVENT_BUYER, FLAG_REPORTING_DESTINATION_BUYER));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_INTERNAL_ERROR),
                        anyInt());
    }

    @Test
    public void testReportImpressionSucceedsButDesNotRegisterUrisThatFailDomainValidation()
            throws Exception {
        Assume.assumeTrue(JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());
        Uri sellerReportingUri = mMockWebServerRule.uriForPath(mSellerReportingPath);
        Uri buyerReportingUri = mMockWebServerRule.uriForPath(mBuyerReportingPath);

        Uri biddingLogicUri = (mMockWebServerRule.uriForPath(mFetchJavaScriptPathBuyer));

        // Register uris with valid domains
        Uri hoverUriSeller = mMockWebServerRule.uriForPath(HOVER_SELLER_PATH);
        Uri clickUriBuyer = mMockWebServerRule.uriForPath(CLICK_BUYER_PATH);

        // Register uris with invalid domains by instantiating another mock server
        MockWebServer differentServer = new MockWebServer();
        differentServer.play();
        Uri clickUriSeller = Uri.parse(differentServer.getUrl(CLICK_SELLER_PATH).toString());
        Uri hoverUriBuyer = Uri.parse(differentServer.getUrl(HOVER_BUYER_PATH).toString());

        String sellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) "
                        + "{\n"
                        + "const beacons = {'click_seller': '"
                        + clickUriSeller
                        + "', 'hover_seller': '"
                        + hoverUriSeller
                        + "'};\n"
                        + "registerAdBeacon(beacons);"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + sellerReportingUri
                        + "' } };\n"
                        + "}";

        String buyerDecisionLogicJs =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer ,"
                        + "contextual_signals, custom_audience_signals) {\n"
                        + "const beacons = {'click_buyer': '"
                        + clickUriBuyer
                        + "', 'hover_buyer': '"
                        + hoverUriBuyer
                        + "'};\n"
                        + "registerAdBeacon(beacons);"
                        + " return {'status': 0, 'results': {'reporting_uri': '"
                        + buyerReportingUri
                        + "' } };\n"
                        + "}";

        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        List.of(
                                new MockResponse().setBody(sellerDecisionLogicJs),
                                new MockResponse(),
                                new MockResponse()));

        DBBuyerDecisionLogic dbBuyerDecisionLogic =
                new DBBuyerDecisionLogic.Builder()
                        .setBiddingLogicUri(biddingLogicUri)
                        .setBuyerDecisionLogicJs(buyerDecisionLogicJs)
                        .build();

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(mCustomAudienceSignals)
                        .setBuyerContextualSignals(mContextualSignals.toString())
                        .setBiddingLogicUri(biddingLogicUri)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        mFlags,
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

        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();

        // Count down callback + log interaction.
        ReportImpressionTestCallback callback =
                callReportImpression(adSelectionService, input, true);

        assertTrue(callback.mIsSuccess);

        // Check that seller click uri was not registered
        assertFalse(
                mAdSelectionEntryDao.doesRegisteredAdInteractionExist(
                        AD_SELECTION_ID, CLICK_EVENT_SELLER, FLAG_REPORTING_DESTINATION_SELLER));

        // Check that seller hover uri was registered
        assertTrue(
                mAdSelectionEntryDao.doesRegisteredAdInteractionExist(
                        AD_SELECTION_ID, HOVER_EVENT_SELLER, FLAG_REPORTING_DESTINATION_SELLER));
        assertEquals(
                hoverUriSeller,
                mAdSelectionEntryDao.getRegisteredAdInteractionUri(
                        AD_SELECTION_ID, HOVER_EVENT_SELLER, FLAG_REPORTING_DESTINATION_SELLER));

        // Check that buyer click uri was registered
        assertTrue(
                mAdSelectionEntryDao.doesRegisteredAdInteractionExist(
                        AD_SELECTION_ID, CLICK_EVENT_BUYER, FLAG_REPORTING_DESTINATION_BUYER));
        assertEquals(
                clickUriBuyer,
                mAdSelectionEntryDao.getRegisteredAdInteractionUri(
                        AD_SELECTION_ID, CLICK_EVENT_BUYER, FLAG_REPORTING_DESTINATION_BUYER));

        // Check that buyer hover uri was not registered
        assertFalse(
                mAdSelectionEntryDao.doesRegisteredAdInteractionExist(
                        AD_SELECTION_ID, HOVER_EVENT_BUYER, FLAG_REPORTING_DESTINATION_BUYER));

        RecordedRequest fetchRequest = server.takeRequest();
        assertEquals(mFetchJavaScriptPathSeller, fetchRequest.getPath());

        List<String> notifications =
                ImmutableList.of(server.takeRequest().getPath(), server.takeRequest().getPath());

        assertThat(notifications).containsExactly(mSellerReportingPath, mBuyerReportingPath);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        anyInt());
    }

    @Test
    public void testReportImpressionSucceedsAndRegistersUrisWithSubdomains() throws Exception {
        Assume.assumeTrue(JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());
        Uri sellerReportingUri = mMockWebServerRule.uriForPath(mSellerReportingPath);
        Uri buyerReportingUri = mMockWebServerRule.uriForPath(mBuyerReportingPath);

        Uri biddingLogicUri = mMockWebServerRule.uriForPath(mFetchJavaScriptPathBuyer);

        Uri clickUriSellerWithSubdomain =
                mMockWebServerRule.unreachableUriWithSubdomainForPath(CLICK_SELLER_PATH);
        Uri hoverUriSellerWithSubdomain =
                mMockWebServerRule.unreachableUriWithSubdomainForPath(HOVER_SELLER_PATH);
        Uri clickUriBuyerWithSubdomain =
                mMockWebServerRule.unreachableUriWithSubdomainForPath(CLICK_BUYER_PATH);
        Uri hoverUriBuyerWithSubdomain =
                mMockWebServerRule.unreachableUriWithSubdomainForPath(HOVER_BUYER_PATH);

        String sellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) "
                        + "{\n"
                        + "const beacons = {'click_seller': '"
                        + clickUriSellerWithSubdomain
                        + "', 'hover_seller': '"
                        + hoverUriSellerWithSubdomain
                        + "'};\n"
                        + "registerAdBeacon(beacons);"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + sellerReportingUri
                        + "' } };\n"
                        + "}";

        String buyerDecisionLogicJs =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer ,"
                        + "contextual_signals, custom_audience_signals) {\n"
                        + "const beacons = {'click_buyer': '"
                        + clickUriBuyerWithSubdomain
                        + "', 'hover_buyer': '"
                        + hoverUriBuyerWithSubdomain
                        + "'};\n"
                        + "registerAdBeacon(beacons);"
                        + " return {'status': 0, 'results': {'reporting_uri': '"
                        + buyerReportingUri
                        + "' } };\n"
                        + "}";

        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        List.of(
                                new MockResponse().setBody(sellerDecisionLogicJs),
                                new MockResponse(),
                                new MockResponse()));

        DBBuyerDecisionLogic dbBuyerDecisionLogic =
                new DBBuyerDecisionLogic.Builder()
                        .setBiddingLogicUri(biddingLogicUri)
                        .setBuyerDecisionLogicJs(buyerDecisionLogicJs)
                        .build();

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(mCustomAudienceSignals)
                        .setBuyerContextualSignals(mContextualSignals.toString())
                        .setBiddingLogicUri(biddingLogicUri)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        mFlags,
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

        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();

        // Count down callback + log interaction.
        ReportImpressionTestCallback callback =
                callReportImpression(adSelectionService, input, true);

        assertTrue(callback.mIsSuccess);

        // Check that database has correct seller registered events
        assertTrue(
                mAdSelectionEntryDao.doesRegisteredAdInteractionExist(
                        AD_SELECTION_ID, CLICK_EVENT_SELLER, FLAG_REPORTING_DESTINATION_SELLER));
        assertEquals(
                clickUriSellerWithSubdomain,
                mAdSelectionEntryDao.getRegisteredAdInteractionUri(
                        AD_SELECTION_ID, CLICK_EVENT_SELLER, FLAG_REPORTING_DESTINATION_SELLER));

        assertTrue(
                mAdSelectionEntryDao.doesRegisteredAdInteractionExist(
                        AD_SELECTION_ID, HOVER_EVENT_SELLER, FLAG_REPORTING_DESTINATION_SELLER));
        assertEquals(
                hoverUriSellerWithSubdomain,
                mAdSelectionEntryDao.getRegisteredAdInteractionUri(
                        AD_SELECTION_ID, HOVER_EVENT_SELLER, FLAG_REPORTING_DESTINATION_SELLER));

        // Check that database has correct buyer registered events
        assertTrue(
                mAdSelectionEntryDao.doesRegisteredAdInteractionExist(
                        AD_SELECTION_ID, CLICK_EVENT_BUYER, FLAG_REPORTING_DESTINATION_BUYER));
        assertEquals(
                clickUriBuyerWithSubdomain,
                mAdSelectionEntryDao.getRegisteredAdInteractionUri(
                        AD_SELECTION_ID, CLICK_EVENT_BUYER, FLAG_REPORTING_DESTINATION_BUYER));

        assertTrue(
                mAdSelectionEntryDao.doesRegisteredAdInteractionExist(
                        AD_SELECTION_ID, HOVER_EVENT_BUYER, FLAG_REPORTING_DESTINATION_BUYER));
        assertEquals(
                hoverUriBuyerWithSubdomain,
                mAdSelectionEntryDao.getRegisteredAdInteractionUri(
                        AD_SELECTION_ID, HOVER_EVENT_BUYER, FLAG_REPORTING_DESTINATION_BUYER));

        RecordedRequest fetchRequest = server.takeRequest();
        assertEquals(mFetchJavaScriptPathSeller, fetchRequest.getPath());

        List<String> notifications =
                ImmutableList.of(server.takeRequest().getPath(), server.takeRequest().getPath());

        assertThat(notifications).containsExactly(mSellerReportingPath, mBuyerReportingPath);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        anyInt());
    }

    @Test
    public void testReportImpressionSucceedsButDesNotRegisterMalformedUris() throws Exception {
        Assume.assumeTrue(JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());

        Uri sellerReportingUri = mMockWebServerRule.uriForPath(mSellerReportingPath);
        Uri buyerReportingUri = mMockWebServerRule.uriForPath(mBuyerReportingPath);

        Uri biddingLogicUri = (mMockWebServerRule.uriForPath(mFetchJavaScriptPathBuyer));

        // Register valid uris
        Uri hoverUriSeller = mMockWebServerRule.uriForPath(HOVER_SELLER_PATH);
        Uri clickUriBuyer = mMockWebServerRule.uriForPath(CLICK_BUYER_PATH);

        // Register malformed uris
        Uri clickUriSeller = Uri.parse(CLICK_SELLER_PATH);
        Uri hoverUriBuyer = Uri.parse(HOVER_BUYER_PATH);

        String sellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) "
                        + "{\n"
                        + "const beacons = {'click_seller': '"
                        + clickUriSeller
                        + "', 'hover_seller': '"
                        + hoverUriSeller
                        + "'};\n"
                        + "registerAdBeacon(beacons);"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + sellerReportingUri
                        + "' } };\n"
                        + "}";

        String buyerDecisionLogicJs =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer ,"
                        + "contextual_signals, custom_audience_signals) {\n"
                        + "const beacons = {'click_buyer': '"
                        + clickUriBuyer
                        + "', 'hover_buyer': '"
                        + hoverUriBuyer
                        + "'};\n"
                        + "registerAdBeacon(beacons);"
                        + " return {'status': 0, 'results': {'reporting_uri': '"
                        + buyerReportingUri
                        + "' } };\n"
                        + "}";

        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        List.of(
                                new MockResponse().setBody(sellerDecisionLogicJs),
                                new MockResponse(),
                                new MockResponse()));

        DBBuyerDecisionLogic dbBuyerDecisionLogic =
                new DBBuyerDecisionLogic.Builder()
                        .setBiddingLogicUri(biddingLogicUri)
                        .setBuyerDecisionLogicJs(buyerDecisionLogicJs)
                        .build();

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(mCustomAudienceSignals)
                        .setBuyerContextualSignals(mContextualSignals.toString())
                        .setBiddingLogicUri(biddingLogicUri)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        mFlags,
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

        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();

        // Count down callback + log interaction.
        ReportImpressionTestCallback callback =
                callReportImpression(adSelectionService, input, true);

        assertTrue(callback.mIsSuccess);

        // Check that seller click uri was not registered
        assertFalse(
                mAdSelectionEntryDao.doesRegisteredAdInteractionExist(
                        AD_SELECTION_ID, CLICK_EVENT_SELLER, FLAG_REPORTING_DESTINATION_SELLER));

        // Check that seller hover uri was registered
        assertTrue(
                mAdSelectionEntryDao.doesRegisteredAdInteractionExist(
                        AD_SELECTION_ID, HOVER_EVENT_SELLER, FLAG_REPORTING_DESTINATION_SELLER));
        assertEquals(
                hoverUriSeller,
                mAdSelectionEntryDao.getRegisteredAdInteractionUri(
                        AD_SELECTION_ID, HOVER_EVENT_SELLER, FLAG_REPORTING_DESTINATION_SELLER));

        // Check that buyer click uri was registered
        assertTrue(
                mAdSelectionEntryDao.doesRegisteredAdInteractionExist(
                        AD_SELECTION_ID, CLICK_EVENT_BUYER, FLAG_REPORTING_DESTINATION_BUYER));
        assertEquals(
                clickUriBuyer,
                mAdSelectionEntryDao.getRegisteredAdInteractionUri(
                        AD_SELECTION_ID, CLICK_EVENT_BUYER, FLAG_REPORTING_DESTINATION_BUYER));

        // Check that buyer hover uri was not registered
        assertFalse(
                mAdSelectionEntryDao.doesRegisteredAdInteractionExist(
                        AD_SELECTION_ID, HOVER_EVENT_BUYER, FLAG_REPORTING_DESTINATION_BUYER));

        RecordedRequest fetchRequest = server.takeRequest();
        assertEquals(mFetchJavaScriptPathSeller, fetchRequest.getPath());

        List<String> notifications =
                ImmutableList.of(server.takeRequest().getPath(), server.takeRequest().getPath());

        assertThat(notifications).containsExactly(mSellerReportingPath, mBuyerReportingPath);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        anyInt());
    }

    @Test
    public void testReportImpressionOnlyRegisterSellerUrisWhenBuyerJSFails() throws Exception {
        Assume.assumeTrue(JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());

        Uri sellerReportingUri = mMockWebServerRule.uriForPath(mSellerReportingPath);
        Uri buyerReportingUri = mMockWebServerRule.uriForPath(mBuyerReportingPath);

        Uri biddingLogicUri = (mMockWebServerRule.uriForPath(mFetchJavaScriptPathBuyer));

        Uri clickUriSeller = mMockWebServerRule.uriForPath(CLICK_SELLER_PATH);
        Uri hoverUriSeller = mMockWebServerRule.uriForPath(HOVER_SELLER_PATH);
        Uri clickUriBuyer = mMockWebServerRule.uriForPath(CLICK_BUYER_PATH);
        Uri hoverUriBuyer = mMockWebServerRule.uriForPath(HOVER_BUYER_PATH);

        String sellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) "
                        + "{\n"
                        + "const beacons = {'click_seller': '"
                        + clickUriSeller
                        + "', 'hover_seller': '"
                        + hoverUriSeller
                        + "'};\n"
                        + "registerAdBeacon(beacons);"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + sellerReportingUri
                        + "' } };\n"
                        + "}";

        String buyerDecisionLogicJs =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer ,"
                        + "contextual_signals, custom_audience_signals) {\n"
                        + "const beacons = {'click_buyer': '"
                        + clickUriBuyer
                        + "', 'hover_buyer': '"
                        + hoverUriBuyer
                        + "'};\n"
                        + "registerAdBeacon(beacons);"
                        + " return {'status': -1, 'results': {'reporting_uri': '"
                        + buyerReportingUri
                        + "' } };\n"
                        + "}";

        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        List.of(
                                new MockResponse().setBody(sellerDecisionLogicJs),
                                new MockResponse(),
                                new MockResponse()));

        DBBuyerDecisionLogic dbBuyerDecisionLogic =
                new DBBuyerDecisionLogic.Builder()
                        .setBiddingLogicUri(biddingLogicUri)
                        .setBuyerDecisionLogicJs(buyerDecisionLogicJs)
                        .build();

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(mCustomAudienceSignals)
                        .setBuyerContextualSignals(mContextualSignals.toString())
                        .setBiddingLogicUri(biddingLogicUri)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        mFlags,
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

        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();
        ReportImpressionTestCallback callback = callReportImpression(adSelectionService, input);

        assertFalse(callback.mIsSuccess);

        // Check that database has correct seller registered events
        assertTrue(
                mAdSelectionEntryDao.doesRegisteredAdInteractionExist(
                        AD_SELECTION_ID, CLICK_EVENT_SELLER, FLAG_REPORTING_DESTINATION_SELLER));
        assertEquals(
                clickUriSeller,
                mAdSelectionEntryDao.getRegisteredAdInteractionUri(
                        AD_SELECTION_ID, CLICK_EVENT_SELLER, FLAG_REPORTING_DESTINATION_SELLER));

        assertTrue(
                mAdSelectionEntryDao.doesRegisteredAdInteractionExist(
                        AD_SELECTION_ID, HOVER_EVENT_SELLER, FLAG_REPORTING_DESTINATION_SELLER));
        assertEquals(
                hoverUriSeller,
                mAdSelectionEntryDao.getRegisteredAdInteractionUri(
                        AD_SELECTION_ID, HOVER_EVENT_SELLER, FLAG_REPORTING_DESTINATION_SELLER));

        // Check that buyer events were not registered
        assertFalse(
                mAdSelectionEntryDao.doesRegisteredAdInteractionExist(
                        AD_SELECTION_ID, CLICK_EVENT_BUYER, FLAG_REPORTING_DESTINATION_BUYER));

        assertFalse(
                mAdSelectionEntryDao.doesRegisteredAdInteractionExist(
                        AD_SELECTION_ID, HOVER_EVENT_BUYER, FLAG_REPORTING_DESTINATION_BUYER));

        assertEquals(callback.mFledgeErrorResponse.getStatusCode(), STATUS_INTERNAL_ERROR);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_INTERNAL_ERROR),
                        anyInt());
    }

    @Test
    public void testReportImpressionDoesNotRegisterUrisWhenSellerJSFails() throws Exception {
        Assume.assumeTrue(JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());

        Uri sellerReportingUri = mMockWebServerRule.uriForPath(mSellerReportingPath);
        Uri buyerReportingUri = mMockWebServerRule.uriForPath(mBuyerReportingPath);

        Uri biddingLogicUri = (mMockWebServerRule.uriForPath(mFetchJavaScriptPathBuyer));

        Uri clickUriSeller = mMockWebServerRule.uriForPath(CLICK_SELLER_PATH);
        Uri hoverUriSeller = mMockWebServerRule.uriForPath(HOVER_SELLER_PATH);
        Uri clickUriBuyer = mMockWebServerRule.uriForPath(CLICK_BUYER_PATH);
        Uri hoverUriBuyer = mMockWebServerRule.uriForPath(HOVER_BUYER_PATH);

        String sellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) "
                        + "{\n"
                        + "const beacons = {'click_seller': '"
                        + clickUriSeller
                        + "', 'hover_seller': '"
                        + hoverUriSeller
                        + "'};\n"
                        + "registerAdBeacon(beacons);"
                        + " return {'status': -1, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + sellerReportingUri
                        + "' } };\n"
                        + "}";

        String buyerDecisionLogicJs =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer ,"
                        + "contextual_signals, custom_audience_signals) {\n"
                        + "const beacons = {'click_buyer': '"
                        + clickUriBuyer
                        + "', 'hover_buyer': '"
                        + hoverUriBuyer
                        + "'};\n"
                        + "registerAdBeacon(beacons);"
                        + " return {'status': 0, 'results': {'reporting_uri': '"
                        + buyerReportingUri
                        + "' } };\n"
                        + "}";

        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        List.of(
                                new MockResponse().setBody(sellerDecisionLogicJs),
                                new MockResponse(),
                                new MockResponse()));

        DBBuyerDecisionLogic dbBuyerDecisionLogic =
                new DBBuyerDecisionLogic.Builder()
                        .setBiddingLogicUri(biddingLogicUri)
                        .setBuyerDecisionLogicJs(buyerDecisionLogicJs)
                        .build();

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(mCustomAudienceSignals)
                        .setBuyerContextualSignals(mContextualSignals.toString())
                        .setBiddingLogicUri(biddingLogicUri)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        mFlags,
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

        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();
        ReportImpressionTestCallback callback = callReportImpression(adSelectionService, input);

        assertFalse(callback.mIsSuccess);

        // Check that no events were registered
        assertFalse(
                mAdSelectionEntryDao.doesRegisteredAdInteractionExist(
                        AD_SELECTION_ID, CLICK_EVENT_SELLER, FLAG_REPORTING_DESTINATION_SELLER));

        assertFalse(
                mAdSelectionEntryDao.doesRegisteredAdInteractionExist(
                        AD_SELECTION_ID, HOVER_EVENT_SELLER, FLAG_REPORTING_DESTINATION_SELLER));

        assertFalse(
                mAdSelectionEntryDao.doesRegisteredAdInteractionExist(
                        AD_SELECTION_ID, CLICK_EVENT_BUYER, FLAG_REPORTING_DESTINATION_BUYER));

        assertFalse(
                mAdSelectionEntryDao.doesRegisteredAdInteractionExist(
                        AD_SELECTION_ID, HOVER_EVENT_BUYER, FLAG_REPORTING_DESTINATION_BUYER));

        assertEquals(callback.mFledgeErrorResponse.getStatusCode(), STATUS_INTERNAL_ERROR);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_INTERNAL_ERROR),
                        anyInt());
    }

    @Test
    public void testReportImpressionDoesNotRegisterMoreThanMaxInteractionUrisFromPhFlag()
            throws Exception {
        Assume.assumeTrue(JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());

        Uri sellerReportingUri = mMockWebServerRule.uriForPath(mSellerReportingPath);
        Uri buyerReportingUri = mMockWebServerRule.uriForPath(mBuyerReportingPath);

        Uri biddingLogicUri = (mMockWebServerRule.uriForPath(mFetchJavaScriptPathBuyer));

        Uri clickUriSeller = mMockWebServerRule.uriForPath(CLICK_SELLER_PATH);
        Uri hoverUriSeller = mMockWebServerRule.uriForPath(HOVER_SELLER_PATH);
        Uri clickUriBuyer = mMockWebServerRule.uriForPath(CLICK_BUYER_PATH);
        Uri hoverUriBuyer = mMockWebServerRule.uriForPath(HOVER_BUYER_PATH);

        String sellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) "
                        + "{\n"
                        + "const beacons = {'click_seller': '"
                        + clickUriSeller
                        + "', 'hover_seller': '"
                        + hoverUriSeller
                        + "'};\n"
                        + "registerAdBeacon(beacons);"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + sellerReportingUri
                        + "' } };\n"
                        + "}";

        String buyerDecisionLogicJs =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer ,"
                        + "contextual_signals, custom_audience_signals) {\n"
                        + "const beacons = {'click_buyer': '"
                        + clickUriBuyer
                        + "', 'hover_buyer': '"
                        + hoverUriBuyer
                        + "'};\n"
                        + "registerAdBeacon(beacons);"
                        + " return {'status': 0, 'results': {'reporting_uri': '"
                        + buyerReportingUri
                        + "' } };\n"
                        + "}";

        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        List.of(
                                new MockResponse().setBody(sellerDecisionLogicJs),
                                new MockResponse(),
                                new MockResponse()));

        DBBuyerDecisionLogic dbBuyerDecisionLogic =
                new DBBuyerDecisionLogic.Builder()
                        .setBiddingLogicUri(biddingLogicUri)
                        .setBuyerDecisionLogicJs(buyerDecisionLogicJs)
                        .build();

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(mCustomAudienceSignals)
                        .setBuyerContextualSignals(mContextualSignals.toString())
                        .setBiddingLogicUri(biddingLogicUri)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        long maxRegisteredAdBeacons = 3;

        // Create new flag with overridden value so that only 3 entries can be registered
        boolean enrollmentCheckDisabled = false;
        Flags flagsWithSmallerMaxEventUris =
                new AdSelectionServicesTestsFlags(enrollmentCheckDisabled) {
                    @Override
                    public long getFledgeReportImpressionMaxRegisteredAdBeaconsTotalCount() {
                        return maxRegisteredAdBeacons;
                    }
                };

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        flagsWithSmallerMaxEventUris,
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

        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();

        // Check that table starts as empty
        assertEquals(0, mAdSelectionEntryDao.getTotalNumRegisteredAdInteractions());

        // Count down callback + log interaction.
        ReportImpressionTestCallback callback =
                callReportImpression(adSelectionService, input, true);

        assertTrue(callback.mIsSuccess);

        // Check that only the first seller event uri pairing was registered
        assertTrue(
                mAdSelectionEntryDao.doesRegisteredAdInteractionExist(
                        AD_SELECTION_ID, CLICK_EVENT_SELLER, FLAG_REPORTING_DESTINATION_SELLER));
        assertEquals(
                clickUriSeller,
                mAdSelectionEntryDao.getRegisteredAdInteractionUri(
                        AD_SELECTION_ID, CLICK_EVENT_SELLER, FLAG_REPORTING_DESTINATION_SELLER));

        // Assert that only 3 entries were registered
        assertEquals(
                maxRegisteredAdBeacons, mAdSelectionEntryDao.getTotalNumRegisteredAdInteractions());

        RecordedRequest fetchRequest = server.takeRequest();
        assertEquals(mFetchJavaScriptPathSeller, fetchRequest.getPath());

        List<String> notifications =
                ImmutableList.of(server.takeRequest().getPath(), server.takeRequest().getPath());

        assertThat(notifications).containsExactly(mSellerReportingPath, mBuyerReportingPath);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        anyInt());
    }

    @Test
    @FlakyTest(bugId = 315521295)
    public void
            testReportImpressionSucceedsButDesNotRegisterUrisWithInteractionKeySizeThatExceedsMax()
                    throws Exception {
        Assume.assumeTrue(JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());
        Uri sellerReportingUri = mMockWebServerRule.uriForPath(mSellerReportingPath);
        Uri buyerReportingUri = mMockWebServerRule.uriForPath(mBuyerReportingPath);

        Uri biddingLogicUri = (mMockWebServerRule.uriForPath(mFetchJavaScriptPathBuyer));

        Uri clickUriSeller = mMockWebServerRule.uriForPath(CLICK_SELLER_PATH);
        Uri hoverUriSeller = mMockWebServerRule.uriForPath(HOVER_SELLER_PATH);
        Uri clickUriBuyer = mMockWebServerRule.uriForPath(CLICK_BUYER_PATH);
        Uri hoverUriBuyer = mMockWebServerRule.uriForPath(HOVER_BUYER_PATH);

        // Override flags to return a smaller max interaction key size
        boolean enrollmentCheckDisabled = false;
        Flags flagsWithSmallerMaxInteractionKeySize =
                new AdSelectionServicesTestsFlags(enrollmentCheckDisabled) {
                    @Override
                    public long
                            getFledgeReportImpressionRegisteredAdBeaconsMaxInteractionKeySizeB() {
                        return 15;
                    }
                };

        String longerClickEventSeller = "click_seller_12345";
        String longerHoverEventBuyer = "hover_buyer_12345";

        // Instantiate JS with seller interaction longer than max size set in flags
        String sellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) "
                        + "{\n"
                        + "const beacons = {'"
                        + longerClickEventSeller
                        + "': '"
                        + clickUriSeller
                        + "', 'hover_seller': '"
                        + hoverUriSeller
                        + "'};\n"
                        + "registerAdBeacon(beacons);"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + sellerReportingUri
                        + "' } };\n"
                        + "}";

        String buyerDecisionLogicJs =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer ,"
                        + "contextual_signals, custom_audience_signals) {\n"
                        + "const beacons = {'click_buyer': '"
                        + clickUriBuyer
                        + "', '"
                        + longerHoverEventBuyer
                        + "': '"
                        + hoverUriBuyer
                        + "'};\n"
                        + "registerAdBeacon(beacons);"
                        + " return {'status': 0, 'results': {'reporting_uri': '"
                        + buyerReportingUri
                        + "' } };\n"
                        + "}";

        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        List.of(
                                new MockResponse().setBody(sellerDecisionLogicJs),
                                new MockResponse(),
                                new MockResponse()));

        DBBuyerDecisionLogic dbBuyerDecisionLogic =
                new DBBuyerDecisionLogic.Builder()
                        .setBiddingLogicUri(biddingLogicUri)
                        .setBuyerDecisionLogicJs(buyerDecisionLogicJs)
                        .build();

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(mCustomAudienceSignals)
                        .setBuyerContextualSignals(mContextualSignals.toString())
                        .setBiddingLogicUri(biddingLogicUri)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        flagsWithSmallerMaxInteractionKeySize,
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

        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();
        // Count down callback + log interaction.
        ReportImpressionTestCallback callback =
                callReportImpression(adSelectionService, input, true);

        assertTrue(callback.mIsSuccess);

        // Check that seller click uri was not registered
        assertFalse(
                mAdSelectionEntryDao.doesRegisteredAdInteractionExist(
                        AD_SELECTION_ID,
                        longerClickEventSeller,
                        FLAG_REPORTING_DESTINATION_SELLER));

        // Check that seller hover uri was registered
        assertTrue(
                mAdSelectionEntryDao.doesRegisteredAdInteractionExist(
                        AD_SELECTION_ID, HOVER_EVENT_SELLER, FLAG_REPORTING_DESTINATION_SELLER));
        assertEquals(
                hoverUriSeller,
                mAdSelectionEntryDao.getRegisteredAdInteractionUri(
                        AD_SELECTION_ID, HOVER_EVENT_SELLER, FLAG_REPORTING_DESTINATION_SELLER));

        // Check that buyer click uri was registered
        assertTrue(
                mAdSelectionEntryDao.doesRegisteredAdInteractionExist(
                        AD_SELECTION_ID, CLICK_EVENT_BUYER, FLAG_REPORTING_DESTINATION_BUYER));
        assertEquals(
                clickUriBuyer,
                mAdSelectionEntryDao.getRegisteredAdInteractionUri(
                        AD_SELECTION_ID, CLICK_EVENT_BUYER, FLAG_REPORTING_DESTINATION_BUYER));

        // Check that buyer hover uri was not registered
        assertFalse(
                mAdSelectionEntryDao.doesRegisteredAdInteractionExist(
                        AD_SELECTION_ID, longerHoverEventBuyer, FLAG_REPORTING_DESTINATION_BUYER));

        RecordedRequest fetchRequest = server.takeRequest();
        assertEquals(mFetchJavaScriptPathSeller, fetchRequest.getPath());

        List<String> notifications =
                ImmutableList.of(server.takeRequest().getPath(), server.takeRequest().getPath());

        assertThat(notifications).containsExactly(mSellerReportingPath, mBuyerReportingPath);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        anyInt());
    }

    @Test
    public void testReportImpressionSucceedsButDesNotRegisterUrisWithUriSizeThatExceedsMax()
            throws Exception {
        Assume.assumeTrue(JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());
        Uri sellerReportingUri = mMockWebServerRule.uriForPath(mSellerReportingPath);
        Uri buyerReportingUri = mMockWebServerRule.uriForPath(mBuyerReportingPath);

        Uri biddingLogicUri = (mMockWebServerRule.uriForPath(mFetchJavaScriptPathBuyer));

        int maxSize = 100;
        String longUriSuffix = getSaltString(maxSize);

        Uri clickUriSellerShouldNotBePersisted =
                mMockWebServerRule.uriForPath(CLICK_SELLER_PATH + longUriSuffix);
        Uri hoverUriSeller = mMockWebServerRule.uriForPath(HOVER_SELLER_PATH);
        Uri clickUriBuyer = mMockWebServerRule.uriForPath(CLICK_BUYER_PATH);
        Uri hoverUriBuyerShouldNotBePersisted =
                mMockWebServerRule.uriForPath(HOVER_BUYER_PATH + longUriSuffix);

        // Override flags to return a smaller max size for reporting uris
        boolean enrollmentCheckDisabled = false;
        Flags flagsWithSmallerMaxInteractionReportingUriSize =
                new AdSelectionServicesTestsFlags(enrollmentCheckDisabled) {
                    @Override
                    public long getFledgeReportImpressionMaxInteractionReportingUriSizeB() {
                        return maxSize;
                    }
                };

        String sellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) "
                        + "{\n"
                        + "const beacons = {'click_seller': '"
                        + clickUriSellerShouldNotBePersisted
                        + "', 'hover_seller': '"
                        + hoverUriSeller
                        + "'};\n"
                        + "registerAdBeacon(beacons);"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + sellerReportingUri
                        + "' } };\n"
                        + "}";

        String buyerDecisionLogicJs =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer ,"
                        + "contextual_signals, custom_audience_signals) {\n"
                        + "const beacons = {'click_buyer': '"
                        + clickUriBuyer
                        + "', 'hover_buyer': '"
                        + hoverUriBuyerShouldNotBePersisted
                        + "'};\n"
                        + "registerAdBeacon(beacons);"
                        + " return {'status': 0, 'results': {'reporting_uri': '"
                        + buyerReportingUri
                        + "' } };\n"
                        + "}";

        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        List.of(
                                new MockResponse().setBody(sellerDecisionLogicJs),
                                new MockResponse(),
                                new MockResponse()));

        DBBuyerDecisionLogic dbBuyerDecisionLogic =
                new DBBuyerDecisionLogic.Builder()
                        .setBiddingLogicUri(biddingLogicUri)
                        .setBuyerDecisionLogicJs(buyerDecisionLogicJs)
                        .build();

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(mCustomAudienceSignals)
                        .setBuyerContextualSignals(mContextualSignals.toString())
                        .setBiddingLogicUri(biddingLogicUri)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        flagsWithSmallerMaxInteractionReportingUriSize,
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

        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();
        // Count down callback + log interaction.
        ReportImpressionTestCallback callback =
                callReportImpression(adSelectionService, input, true);

        assertTrue(callback.mIsSuccess);

        // Check that seller click uri was not registered
        assertFalse(
                mAdSelectionEntryDao.doesRegisteredAdInteractionExist(
                        AD_SELECTION_ID, CLICK_EVENT_SELLER, FLAG_REPORTING_DESTINATION_SELLER));

        // Check that seller hover uri was registered
        assertTrue(
                mAdSelectionEntryDao.doesRegisteredAdInteractionExist(
                        AD_SELECTION_ID, HOVER_EVENT_SELLER, FLAG_REPORTING_DESTINATION_SELLER));
        assertEquals(
                hoverUriSeller,
                mAdSelectionEntryDao.getRegisteredAdInteractionUri(
                        AD_SELECTION_ID, HOVER_EVENT_SELLER, FLAG_REPORTING_DESTINATION_SELLER));

        // Check that buyer click uri was registered
        assertTrue(
                mAdSelectionEntryDao.doesRegisteredAdInteractionExist(
                        AD_SELECTION_ID, CLICK_EVENT_BUYER, FLAG_REPORTING_DESTINATION_BUYER));
        assertEquals(
                clickUriBuyer,
                mAdSelectionEntryDao.getRegisteredAdInteractionUri(
                        AD_SELECTION_ID, CLICK_EVENT_BUYER, FLAG_REPORTING_DESTINATION_BUYER));

        // Check that buyer hover uri was not registered
        assertFalse(
                mAdSelectionEntryDao.doesRegisteredAdInteractionExist(
                        AD_SELECTION_ID, HOVER_EVENT_BUYER, FLAG_REPORTING_DESTINATION_BUYER));

        RecordedRequest fetchRequest = server.takeRequest();
        assertEquals(mFetchJavaScriptPathSeller, fetchRequest.getPath());

        List<String> notifications =
                ImmutableList.of(server.takeRequest().getPath(), server.takeRequest().getPath());

        assertThat(notifications).containsExactly(mSellerReportingPath, mBuyerReportingPath);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        anyInt());
    }

    @Test
    public void testReportImpressionWithRevokedUserConsentSuccess() throws Exception {
        Assume.assumeTrue(JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());

        Uri buyerReportingUri = mMockWebServerRule.uriForPath(mBuyerReportingPath);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        doThrow(new FilterException(new ConsentManager.RevokedConsentException()))
                .when(mAdSelectionServiceFilterMock)
                .filterRequest(
                        mSeller,
                        TEST_PACKAGE_NAME,
                        true,
                        true,
                        CALLER_UID,
                        AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION,
                        Throttler.ApiKey.FLEDGE_API_REPORT_IMPRESSIONS,
                        DevContext.createForDevOptionsDisabled());

        String buyerDecisionLogicJs =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,"
                        + " contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'results': {'reporting_uri': '"
                        + buyerReportingUri
                        + "' } };\n"
                        + "}";

        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        new Dispatcher() {
                            @Override
                            public MockResponse dispatch(RecordedRequest request) {
                                throw new IllegalStateException(
                                        "No calls should be made without user consent");
                            }
                        });

        DBBuyerDecisionLogic dbBuyerDecisionLogic =
                new DBBuyerDecisionLogic.Builder()
                        .setBiddingLogicUri(BUYER_BIDDING_LOGIC_URI)
                        .setBuyerDecisionLogicJs(buyerDecisionLogicJs)
                        .build();

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(mCustomAudienceSignals)
                        .setBuyerContextualSignals(mContextualSignals.toString())
                        .setBiddingLogicUri(BUYER_BIDDING_LOGIC_URI)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        mFlags,
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

        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();
        ReportImpressionTestCallback callback = callReportImpression(adSelectionService, input);

        assertTrue(callback.mIsSuccess);
        assertEquals(0, server.getRequestCount());

        // Confirm a duplicate log entry does not exist.
        // AdSelectionServiceFilter ensures the failing assertion is logged internally.
        verify(mAdServicesLoggerMock, never())
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_USER_CONSENT_REVOKED),
                        anyInt());
    }

    @Test
    public void testReportImpressionFailsWhenReportResultTakesTooLong() throws Exception {
        Assume.assumeTrue(JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());

        Uri sellerReportingUri = mMockWebServerRule.uriForPath(mSellerReportingPath);
        Uri buyerReportingUri = mMockWebServerRule.uriForPath(mBuyerReportingPath);

        String jsWaitMoreThanAllowed =
                insertJsWait(2 * mFlags.getReportImpressionOverallTimeoutMs());

        String sellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) {"
                        + " \n"
                        + jsWaitMoreThanAllowed
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + sellerReportingUri
                        + "' } };\n"
                        + "}";

        String buyerDecisionLogicJs =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,"
                        + " contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'results': {'reporting_uri': '"
                        + buyerReportingUri
                        + "' } };\n"
                        + "}";

        mMockWebServerRule.startMockWebServer(
                List.of(
                        new MockResponse().setBody(sellerDecisionLogicJs),
                        new MockResponse(),
                        new MockResponse()));

        DBBuyerDecisionLogic dbBuyerDecisionLogic =
                new DBBuyerDecisionLogic.Builder()
                        .setBiddingLogicUri(BUYER_BIDDING_LOGIC_URI)
                        .setBuyerDecisionLogicJs(buyerDecisionLogicJs)
                        .build();

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(mCustomAudienceSignals)
                        .setBuyerContextualSignals(mContextualSignals.toString())
                        .setBiddingLogicUri(BUYER_BIDDING_LOGIC_URI)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        mFlags,
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

        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();
        ReportImpressionTestCallback callback = callReportImpression(adSelectionService, input);

        assertFalse(callback.mIsSuccess);
        assertEquals(callback.mFledgeErrorResponse.getStatusCode(), STATUS_INTERNAL_ERROR);
        assertTrue(callback.mFledgeErrorResponse.getErrorMessage().contains(TIMEOUT_MESSAGE));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_INTERNAL_ERROR),
                        anyInt());
    }

    @Test
    public void testReportImpressionFailsWhenReportWinTakesTooLong() throws Exception {
        Assume.assumeTrue(JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());

        Uri sellerReportingUri = mMockWebServerRule.uriForPath(mSellerReportingPath);
        Uri buyerReportingUri = mMockWebServerRule.uriForPath(mBuyerReportingPath);

        String jsWaitMoreThanAllowed =
                insertJsWait(2 * mFlags.getReportImpressionOverallTimeoutMs());

        String sellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) {"
                        + " \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + sellerReportingUri
                        + "' } };\n"
                        + "}";

        String buyerDecisionLogicJs =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,"
                        + " contextual_signals, custom_audience_signals) { \n"
                        + jsWaitMoreThanAllowed
                        + " return {'status': 0, 'results': {'reporting_uri': '"
                        + buyerReportingUri
                        + "' } };\n"
                        + "}";

        mMockWebServerRule.startMockWebServer(
                List.of(
                        new MockResponse().setBody(sellerDecisionLogicJs),
                        new MockResponse(),
                        new MockResponse()));

        DBBuyerDecisionLogic dbBuyerDecisionLogic =
                new DBBuyerDecisionLogic.Builder()
                        .setBiddingLogicUri(BUYER_BIDDING_LOGIC_URI)
                        .setBuyerDecisionLogicJs(buyerDecisionLogicJs)
                        .build();

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(mCustomAudienceSignals)
                        .setBuyerContextualSignals(mContextualSignals.toString())
                        .setBiddingLogicUri(BUYER_BIDDING_LOGIC_URI)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        mFlags,
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

        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();
        ReportImpressionTestCallback callback = callReportImpression(adSelectionService, input);

        assertFalse(callback.mIsSuccess);
        assertEquals(callback.mFledgeErrorResponse.getStatusCode(), STATUS_INTERNAL_ERROR);
        assertTrue(callback.mFledgeErrorResponse.getErrorMessage().contains(TIMEOUT_MESSAGE));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_INTERNAL_ERROR),
                        anyInt());
    }

    @Test
    public void testReportImpressionFailsWhenOverallJSTimesOut() throws Exception {
        Assume.assumeTrue(JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());

        Uri sellerReportingUri = mMockWebServerRule.uriForPath(mSellerReportingPath);
        Uri buyerReportingUri = mMockWebServerRule.uriForPath(mBuyerReportingPath);

        String jsWaitMoreThanAllowed =
                insertJsWait((long) (.25 * mFlags.getReportImpressionOverallTimeoutMs()));

        String sellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) {"
                        + " \n"
                        + jsWaitMoreThanAllowed
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + sellerReportingUri
                        + "' } };\n"
                        + "}";

        String buyerDecisionLogicJs =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,"
                        + " contextual_signals, custom_audience_signals) { \n"
                        + jsWaitMoreThanAllowed
                        + " return {'status': 0, 'results': {'reporting_uri': '"
                        + buyerReportingUri
                        + "' } };\n"
                        + "}";

        mMockWebServerRule.startMockWebServer(
                List.of(
                        new MockResponse()
                                .setBody(sellerDecisionLogicJs)
                                .throttleBody(
                                        mBytesPerPeriod,
                                        (long) (.5 * mFlags.getReportImpressionOverallTimeoutMs()),
                                        TimeUnit.MILLISECONDS),
                        new MockResponse(),
                        new MockResponse()));

        DBBuyerDecisionLogic dbBuyerDecisionLogic =
                new DBBuyerDecisionLogic.Builder()
                        .setBiddingLogicUri(BUYER_BIDDING_LOGIC_URI)
                        .setBuyerDecisionLogicJs(buyerDecisionLogicJs)
                        .build();

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(mCustomAudienceSignals)
                        .setBuyerContextualSignals(mContextualSignals.toString())
                        .setBiddingLogicUri(BUYER_BIDDING_LOGIC_URI)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        mFlags,
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

        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();
        ReportImpressionTestCallback callback = callReportImpression(adSelectionService, input);

        assertFalse(callback.mIsSuccess);
        assertEquals(callback.mFledgeErrorResponse.getStatusCode(), STATUS_INTERNAL_ERROR);
        assertTrue(callback.mFledgeErrorResponse.getErrorMessage().contains(TIMEOUT_MESSAGE));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_INTERNAL_ERROR),
                        anyInt());
    }

    @Test
    public void testReportImpressionFailsWhenJSFetchTakesTooLong() throws Exception {
        Assume.assumeTrue(JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());

        Uri sellerReportingUri = mMockWebServerRule.uriForPath(mSellerReportingPath);
        Uri buyerReportingUri = mMockWebServerRule.uriForPath(mBuyerReportingPath);

        String sellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) {"
                        + " \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + sellerReportingUri
                        + "' } };\n"
                        + "}";

        String buyerDecisionLogicJs =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,"
                        + " contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'results': {'reporting_uri': '"
                        + buyerReportingUri
                        + "' } };\n"
                        + "}";

        mMockWebServerRule.startMockWebServer(
                List.of(
                        new MockResponse()
                                .setBody(sellerDecisionLogicJs)
                                .throttleBody(
                                        mBytesPerPeriod,
                                        mFlags.getReportImpressionOverallTimeoutMs(),
                                        TimeUnit.MILLISECONDS),
                        new MockResponse(),
                        new MockResponse()));

        DBBuyerDecisionLogic dbBuyerDecisionLogic =
                new DBBuyerDecisionLogic.Builder()
                        .setBiddingLogicUri(BUYER_BIDDING_LOGIC_URI)
                        .setBuyerDecisionLogicJs(buyerDecisionLogicJs)
                        .build();

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(mCustomAudienceSignals)
                        .setBuyerContextualSignals(mContextualSignals.toString())
                        .setBiddingLogicUri(BUYER_BIDDING_LOGIC_URI)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        mFlags,
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

        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();
        ReportImpressionTestCallback callback = callReportImpression(adSelectionService, input);

        assertFalse(callback.mIsSuccess);
        assertEquals(callback.mFledgeErrorResponse.getStatusCode(), STATUS_INTERNAL_ERROR);
        assertTrue(callback.mFledgeErrorResponse.getErrorMessage().contains(TIMEOUT_MESSAGE));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_INTERNAL_ERROR),
                        anyInt());
    }

    @Test
    public void testReportImpressionFailsWithInvalidAdSelectionId() throws Exception {
        Assume.assumeTrue(JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());

        Uri sellerReportingUri = mMockWebServerRule.uriForPath(mSellerReportingPath);
        Uri buyerReportingUri = mMockWebServerRule.uriForPath(mBuyerReportingPath);

        String sellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) {"
                        + " \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + sellerReportingUri
                        + "' } };\n"
                        + "}";

        String buyerDecisionLogicJs =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,"
                        + " contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'results': {'reporting_uri': '"
                        + buyerReportingUri
                        + "' } };\n"
                        + "}";

        mMockWebServerRule.startMockWebServer(
                List.of(
                        new MockResponse().setBody(sellerDecisionLogicJs),
                        new MockResponse(),
                        new MockResponse()));

        DBBuyerDecisionLogic dbBuyerDecisionLogic =
                new DBBuyerDecisionLogic.Builder()
                        .setBiddingLogicUri(BUYER_BIDDING_LOGIC_URI)
                        .setBuyerDecisionLogicJs(buyerDecisionLogicJs)
                        .build();

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(mCustomAudienceSignals)
                        .setBuyerContextualSignals(mContextualSignals.toString())
                        .setBiddingLogicUri(BUYER_BIDDING_LOGIC_URI)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        mFlags,
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

        ReportImpressionInput request =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(INCORRECT_AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();

        ReportImpressionTestCallback callback = callReportImpression(adSelectionService, request);

        assertFalse(callback.mIsSuccess);
        assertEquals(callback.mFledgeErrorResponse.getStatusCode(), STATUS_INVALID_ARGUMENT);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_INVALID_ARGUMENT),
                        anyInt());
    }

    @Test
    public void testReportImpressionBadSellerJavascriptFailsWithInternalError() throws Exception {
        Assume.assumeTrue(JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());

        Uri sellerReportingUri = mMockWebServerRule.uriForPath(mSellerReportingPath);
        Uri buyerReportingUri = mMockWebServerRule.uriForPath(mBuyerReportingPath);

        String invalidSellerDecisionLogicJsMissingCurlyBracket =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) {"
                        + " \n"
                        + " return {'status': 0, 'results': 'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + sellerReportingUri
                        + "' } };\n"
                        + "}";

        String buyerDecisionLogicJs =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,"
                        + " contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'results': {'reporting_uri': '"
                        + buyerReportingUri
                        + "' } };\n"
                        + "}";

        mMockWebServerRule.startMockWebServer(
                List.of(
                        new MockResponse().setBody(invalidSellerDecisionLogicJsMissingCurlyBracket),
                        new MockResponse(),
                        new MockResponse()));

        DBBuyerDecisionLogic dbBuyerDecisionLogic =
                new DBBuyerDecisionLogic.Builder()
                        .setBiddingLogicUri(BUYER_BIDDING_LOGIC_URI)
                        .setBuyerDecisionLogicJs(buyerDecisionLogicJs)
                        .build();

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(mCustomAudienceSignals)
                        .setBuyerContextualSignals(mContextualSignals.toString())
                        .setBiddingLogicUri(BUYER_BIDDING_LOGIC_URI)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();
        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        mFlags,
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

        ReportImpressionInput request =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();

        ReportImpressionTestCallback callback = callReportImpression(adSelectionService, request);

        assertFalse(callback.mIsSuccess);
        assertEquals(callback.mFledgeErrorResponse.getStatusCode(), STATUS_INTERNAL_ERROR);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_INTERNAL_ERROR),
                        anyInt());
    }

    @Test
    public void testReportImpressionBadBuyerJavascriptFailsWithInternalError() throws Exception {
        Assume.assumeTrue(JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());

        Uri sellerReportingUri = mMockWebServerRule.uriForPath(mSellerReportingPath);
        Uri buyerReportingUri = mMockWebServerRule.uriForPath(mBuyerReportingPath);

        String sellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) {"
                        + " \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + sellerReportingUri
                        + "' } };\n"
                        + "}";

        String inValidBuyerDecisionLogicJsMissingCurlyBracket =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,"
                        + " contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'results': 'reporting_uri': '"
                        + buyerReportingUri
                        + "' } };\n"
                        + "}";

        mMockWebServerRule.startMockWebServer(
                List.of(
                        new MockResponse().setBody(sellerDecisionLogicJs),
                        new MockResponse(),
                        new MockResponse()));

        DBBuyerDecisionLogic dbBuyerDecisionLogic =
                new DBBuyerDecisionLogic.Builder()
                        .setBiddingLogicUri(BUYER_BIDDING_LOGIC_URI)
                        .setBuyerDecisionLogicJs(inValidBuyerDecisionLogicJsMissingCurlyBracket)
                        .build();

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(mCustomAudienceSignals)
                        .setBuyerContextualSignals(mContextualSignals.toString())
                        .setBiddingLogicUri(BUYER_BIDDING_LOGIC_URI)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        mFlags,
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

        ReportImpressionInput request =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();

        ReportImpressionTestCallback callback = callReportImpression(adSelectionService, request);

        assertFalse(callback.mIsSuccess);
        assertEquals(callback.mFledgeErrorResponse.getStatusCode(), STATUS_INTERNAL_ERROR);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_INTERNAL_ERROR),
                        anyInt());
    }

    @Test
    public void testReportImpressionUseDevOverrideForSellerJS() throws Exception {
        Assume.assumeTrue(JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());

        Uri sellerReportingUri = mMockWebServerRule.uriForPath(mSellerReportingPath);
        Uri buyerReportingUri = mMockWebServerRule.uriForPath(mBuyerReportingPath);

        String sellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) {"
                        + " \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + sellerReportingUri
                        + "' } };\n"
                        + "}";

        String buyerDecisionLogicJs =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,"
                        + " contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'results': {'reporting_uri': '"
                        + buyerReportingUri
                        + "' } };\n"
                        + "}";

        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        List.of(
                                // There is no need to fetch JS
                                new MockResponse(), new MockResponse()));

        DBBuyerDecisionLogic dbBuyerDecisionLogic =
                new DBBuyerDecisionLogic.Builder()
                        .setBiddingLogicUri(BUYER_BIDDING_LOGIC_URI)
                        .setBuyerDecisionLogicJs(buyerDecisionLogicJs)
                        .build();

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(mCustomAudienceSignals)
                        .setBuyerContextualSignals(mContextualSignals.toString())
                        .setBiddingLogicUri(BUYER_BIDDING_LOGIC_URI)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        // Set dev override for this AdSelection

        DBAdSelectionOverride adSelectionOverride =
                DBAdSelectionOverride.builder()
                        .setAdSelectionConfigId(
                                AdSelectionDevOverridesHelper.calculateAdSelectionConfigId(
                                        adSelectionConfig))
                        .setAppPackageName(TEST_PACKAGE_NAME)
                        .setDecisionLogicJS(sellerDecisionLogicJs)
                        .setTrustedScoringSignals(DUMMY_TRUSTED_SCORING_SIGNALS.toString())
                        .build();
        mAdSelectionEntryDao.persistAdSelectionOverride(adSelectionOverride);

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(TEST_PACKAGE_NAME)
                                .build());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        mFlags,
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
        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();

        // Count down callback + log interaction.
        ReportImpressionTestCallback callback =
                callReportImpression(adSelectionService, input, true);

        assertTrue(callback.mIsSuccess);
        List<String> notifications =
                ImmutableList.of(server.takeRequest().getPath(), server.takeRequest().getPath());

        assertThat(notifications).containsExactly(mSellerReportingPath, mBuyerReportingPath);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        anyInt());
    }

    @Test
    public void testReportImpressionUseDevOverrideForSellerJSSuccessfullyRegistersEventUris()
            throws Exception {
        Assume.assumeTrue(JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);

        Uri sellerReportingUri = mMockWebServerRule.uriForPath(mSellerReportingPath);
        Uri buyerReportingUri = mMockWebServerRule.uriForPath(mBuyerReportingPath);

        Uri clickUriSeller = mMockWebServerRule.uriForPath(CLICK_SELLER_PATH);
        Uri hoverUriSeller = mMockWebServerRule.uriForPath(HOVER_SELLER_PATH);
        Uri clickUriBuyer = mMockWebServerRule.uriForPath(CLICK_BUYER_PATH);
        Uri hoverUriBuyer = mMockWebServerRule.uriForPath(HOVER_BUYER_PATH);

        String sellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) "
                        + "{\n"
                        + "const beacons = {'click_seller': '"
                        + clickUriSeller
                        + "', 'hover_seller': '"
                        + hoverUriSeller
                        + "'};\n"
                        + "registerAdBeacon(beacons);"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + sellerReportingUri
                        + "' } };\n"
                        + "}";

        String buyerDecisionLogicJs =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer ,"
                        + "contextual_signals, custom_audience_signals) {\n"
                        + "const beacons = {'click_buyer': '"
                        + clickUriBuyer
                        + "', 'hover_buyer': '"
                        + hoverUriBuyer
                        + "'};\n"
                        + "registerAdBeacon(beacons);"
                        + " return {'status': 0, 'results': {'reporting_uri': '"
                        + buyerReportingUri
                        + "' } };\n"
                        + "}";

        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        List.of(
                                // There is no need to fetch JS
                                new MockResponse(), new MockResponse()));

        DBBuyerDecisionLogic dbBuyerDecisionLogic =
                new DBBuyerDecisionLogic.Builder()
                        .setBiddingLogicUri(BUYER_BIDDING_LOGIC_URI)
                        .setBuyerDecisionLogicJs(buyerDecisionLogicJs)
                        .build();

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(mCustomAudienceSignals)
                        .setBuyerContextualSignals(mContextualSignals.toString())
                        .setBiddingLogicUri(BUYER_BIDDING_LOGIC_URI)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        // Set dev override for this AdSelection

        DBAdSelectionOverride adSelectionOverride =
                DBAdSelectionOverride.builder()
                        .setAdSelectionConfigId(
                                AdSelectionDevOverridesHelper.calculateAdSelectionConfigId(
                                        adSelectionConfig))
                        .setAppPackageName(TEST_PACKAGE_NAME)
                        .setDecisionLogicJS(sellerDecisionLogicJs)
                        .setTrustedScoringSignals(DUMMY_TRUSTED_SCORING_SIGNALS.toString())
                        .build();
        mAdSelectionEntryDao.persistAdSelectionOverride(adSelectionOverride);

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(TEST_PACKAGE_NAME)
                                .build());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        mFlags,
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
        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();

        // Count down callback + log interaction.
        ReportImpressionTestCallback callback =
                callReportImpression(adSelectionService, input, true);

        assertTrue(callback.mIsSuccess);

        // Check that database has correct seller registered events
        assertTrue(
                mAdSelectionEntryDao.doesRegisteredAdInteractionExist(
                        AD_SELECTION_ID, CLICK_EVENT_SELLER, FLAG_REPORTING_DESTINATION_SELLER));
        assertEquals(
                clickUriSeller,
                mAdSelectionEntryDao.getRegisteredAdInteractionUri(
                        AD_SELECTION_ID, CLICK_EVENT_SELLER, FLAG_REPORTING_DESTINATION_SELLER));

        assertTrue(
                mAdSelectionEntryDao.doesRegisteredAdInteractionExist(
                        AD_SELECTION_ID, HOVER_EVENT_SELLER, FLAG_REPORTING_DESTINATION_SELLER));
        assertEquals(
                hoverUriSeller,
                mAdSelectionEntryDao.getRegisteredAdInteractionUri(
                        AD_SELECTION_ID, HOVER_EVENT_SELLER, FLAG_REPORTING_DESTINATION_SELLER));

        // Check that database has correct buyer registered events
        assertTrue(
                mAdSelectionEntryDao.doesRegisteredAdInteractionExist(
                        AD_SELECTION_ID, CLICK_EVENT_BUYER, FLAG_REPORTING_DESTINATION_BUYER));
        assertEquals(
                clickUriBuyer,
                mAdSelectionEntryDao.getRegisteredAdInteractionUri(
                        AD_SELECTION_ID, CLICK_EVENT_BUYER, FLAG_REPORTING_DESTINATION_BUYER));

        assertTrue(
                mAdSelectionEntryDao.doesRegisteredAdInteractionExist(
                        AD_SELECTION_ID, HOVER_EVENT_BUYER, FLAG_REPORTING_DESTINATION_BUYER));
        assertEquals(
                hoverUriBuyer,
                mAdSelectionEntryDao.getRegisteredAdInteractionUri(
                        AD_SELECTION_ID, HOVER_EVENT_BUYER, FLAG_REPORTING_DESTINATION_BUYER));

        List<String> notifications =
                ImmutableList.of(server.takeRequest().getPath(), server.takeRequest().getPath());

        assertThat(notifications).containsExactly(mSellerReportingPath, mBuyerReportingPath);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        anyInt());
    }

    @Test
    public void testOverrideAdSelectionConfigRemoteInfoSuccess() throws Exception {
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        when(mDevContextFilterMock.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(TEST_PACKAGE_NAME)
                                .build());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        mFlags,
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

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        AdSelectionOverrideTestCallback callback =
                callAddOverrideForSelectAds(
                        adSelectionService,
                        adSelectionConfig,
                        DUMMY_DECISION_LOGIC_JS,
                        DUMMY_TRUSTED_SCORING_SIGNALS,
                        BUYERS_DECISION_LOGIC);

        assertTrue(callback.mIsSuccess);
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        AdSelectionDevOverridesHelper.calculateAdSelectionConfigId(
                                adSelectionConfig),
                        TEST_PACKAGE_NAME));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(SHORT_API_NAME_OVERRIDE),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        anyInt());
    }

    @Test
    public void testOverrideAdSelectionConfigRemoteInfoWithRevokedUserConsentSuccess()
            throws Exception {
        doReturn(AdServicesApiConsent.REVOKED)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(TEST_PACKAGE_NAME)
                                .build());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        mFlags,
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

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        AdSelectionOverrideTestCallback callback =
                callAddOverrideForSelectAds(
                        adSelectionService,
                        adSelectionConfig,
                        DUMMY_DECISION_LOGIC_JS,
                        DUMMY_TRUSTED_SCORING_SIGNALS,
                        BUYERS_DECISION_LOGIC);

        assertTrue(callback.mIsSuccess);
        assertFalse(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        AdSelectionDevOverridesHelper.calculateAdSelectionConfigId(
                                adSelectionConfig),
                        TEST_PACKAGE_NAME));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(SHORT_API_NAME_OVERRIDE),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_USER_CONSENT_REVOKED),
                        anyInt());
    }

    @Test
    public void testOverrideAdSelectionConfigRemoteInfoFailsWithDevOptionsDisabled() {
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        when(mDevContextFilterMock.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        mFlags,
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

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        assertThrows(
                SecurityException.class,
                () ->
                        callAddOverrideForSelectAds(
                                adSelectionService,
                                adSelectionConfig,
                                DUMMY_DECISION_LOGIC_JS,
                                DUMMY_TRUSTED_SCORING_SIGNALS,
                                BUYERS_DECISION_LOGIC));

        assertFalse(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        AdSelectionDevOverridesHelper.calculateAdSelectionConfigId(
                                adSelectionConfig),
                        TEST_PACKAGE_NAME));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(SHORT_API_NAME_OVERRIDE),
                        eq(null), // Actually logged as ""
                        eq(STATUS_INTERNAL_ERROR),
                        anyInt());
    }

    @Test
    public void testRemoveAdSelectionConfigRemoteInfoOverrideSuccess() throws Exception {
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        when(mDevContextFilterMock.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(TEST_PACKAGE_NAME)
                                .build());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        mFlags,
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

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        String adSelectionConfigId =
                AdSelectionDevOverridesHelper.calculateAdSelectionConfigId(adSelectionConfig);

        DBAdSelectionOverride dbAdSelectionOverride =
                DBAdSelectionOverride.builder()
                        .setAdSelectionConfigId(adSelectionConfigId)
                        .setAppPackageName(TEST_PACKAGE_NAME)
                        .setDecisionLogicJS(DUMMY_DECISION_LOGIC_JS)
                        .setTrustedScoringSignals(DUMMY_TRUSTED_SCORING_SIGNALS.toString())
                        .build();

        mAdSelectionEntryDao.persistAdSelectionOverride(dbAdSelectionOverride);

        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId, TEST_PACKAGE_NAME));

        AdSelectionOverrideTestCallback callback =
                callRemoveOverride(adSelectionService, adSelectionConfig);

        assertTrue(callback.mIsSuccess);
        assertFalse(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId, TEST_PACKAGE_NAME));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(SHORT_API_NAME_REMOVE_OVERRIDE),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        anyInt());
    }

    @Test
    public void testRemoveAdSelectionConfigRemoteInfoOverrideWithRevokedUserConsentSuccess()
            throws Exception {
        doReturn(AdServicesApiConsent.REVOKED)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(TEST_PACKAGE_NAME)
                                .build());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        mFlags,
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

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        String adSelectionConfigId =
                AdSelectionDevOverridesHelper.calculateAdSelectionConfigId(adSelectionConfig);

        DBAdSelectionOverride dbAdSelectionOverride =
                DBAdSelectionOverride.builder()
                        .setAdSelectionConfigId(adSelectionConfigId)
                        .setAppPackageName(TEST_PACKAGE_NAME)
                        .setDecisionLogicJS(DUMMY_DECISION_LOGIC_JS)
                        .setTrustedScoringSignals(DUMMY_TRUSTED_SCORING_SIGNALS.toString())
                        .build();

        mAdSelectionEntryDao.persistAdSelectionOverride(dbAdSelectionOverride);

        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId, TEST_PACKAGE_NAME));

        AdSelectionOverrideTestCallback callback =
                callRemoveOverride(adSelectionService, adSelectionConfig);

        assertTrue(callback.mIsSuccess);
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId, TEST_PACKAGE_NAME));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(SHORT_API_NAME_REMOVE_OVERRIDE),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_USER_CONSENT_REVOKED),
                        anyInt());
    }

    @Test
    public void testRemoveAdSelectionConfigRemoteInfoOverrideFailsWithDevOptionsDisabled() {
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        when(mDevContextFilterMock.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        mFlags,
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

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        String adSelectionConfigId =
                AdSelectionDevOverridesHelper.calculateAdSelectionConfigId(adSelectionConfig);

        DBAdSelectionOverride dbAdSelectionOverride =
                DBAdSelectionOverride.builder()
                        .setAdSelectionConfigId(adSelectionConfigId)
                        .setAppPackageName(TEST_PACKAGE_NAME)
                        .setDecisionLogicJS(DUMMY_DECISION_LOGIC_JS)
                        .setTrustedScoringSignals(DUMMY_TRUSTED_SCORING_SIGNALS.toString())
                        .build();

        mAdSelectionEntryDao.persistAdSelectionOverride(dbAdSelectionOverride);

        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId, TEST_PACKAGE_NAME));

        assertThrows(
                SecurityException.class,
                () -> callRemoveOverride(adSelectionService, adSelectionConfig));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId, TEST_PACKAGE_NAME));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(SHORT_API_NAME_REMOVE_OVERRIDE),
                        eq(null), // Actually logged as ""
                        eq(STATUS_INTERNAL_ERROR),
                        anyInt());
    }

    @Test
    public void testRemoveAdSelectionConfigRemoteInfoOverrideDoesNotDeleteWithIncorrectPackageName()
            throws Exception {
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        String incorrectPackageName = "com.google.ppapi.test.incorrect";

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(incorrectPackageName)
                                .build());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        mFlags,
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

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        String adSelectionConfigId =
                AdSelectionDevOverridesHelper.calculateAdSelectionConfigId(adSelectionConfig);

        DBAdSelectionOverride dbAdSelectionOverride =
                DBAdSelectionOverride.builder()
                        .setAdSelectionConfigId(adSelectionConfigId)
                        .setAppPackageName(TEST_PACKAGE_NAME)
                        .setDecisionLogicJS(DUMMY_DECISION_LOGIC_JS)
                        .setTrustedScoringSignals(DUMMY_TRUSTED_SCORING_SIGNALS.toString())
                        .build();

        mAdSelectionEntryDao.persistAdSelectionOverride(dbAdSelectionOverride);

        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId, TEST_PACKAGE_NAME));

        AdSelectionOverrideTestCallback callback =
                callRemoveOverride(adSelectionService, adSelectionConfig);

        assertTrue(callback.mIsSuccess);
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId, TEST_PACKAGE_NAME));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(SHORT_API_NAME_REMOVE_OVERRIDE),
                        eq(incorrectPackageName),
                        eq(STATUS_SUCCESS),
                        anyInt());
    }

    @Test
    public void testResetAllAdSelectionConfigRemoteOverridesDoesNotDeleteWithIncorrectPackageName()
            throws Exception {
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        String incorrectPackageName = "com.google.ppapi.test.incorrect";

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(incorrectPackageName)
                                .build());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        mFlags,
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

        AdSelectionConfig adSelectionConfig1 = mAdSelectionConfigBuilder.build();
        AdSelectionConfig adSelectionConfig2 =
                mAdSelectionConfigBuilder
                        .setSeller(AdTechIdentifier.fromString("adidas.com"))
                        .setDecisionLogicUri(Uri.parse("https://adidas.com/decisoin_logic_uri"))
                        .build();
        AdSelectionConfig adSelectionConfig3 =
                mAdSelectionConfigBuilder
                        .setSeller(AdTechIdentifier.fromString("nike.com"))
                        .setDecisionLogicUri(Uri.parse("https://nike.com/decisoin_logic_uri"))
                        .build();

        String adSelectionConfigId1 =
                AdSelectionDevOverridesHelper.calculateAdSelectionConfigId(adSelectionConfig1);
        String adSelectionConfigId2 =
                AdSelectionDevOverridesHelper.calculateAdSelectionConfigId(adSelectionConfig2);
        String adSelectionConfigId3 =
                AdSelectionDevOverridesHelper.calculateAdSelectionConfigId(adSelectionConfig3);

        DBAdSelectionOverride dbAdSelectionOverride1 =
                DBAdSelectionOverride.builder()
                        .setAdSelectionConfigId(adSelectionConfigId1)
                        .setAppPackageName(TEST_PACKAGE_NAME)
                        .setDecisionLogicJS(DUMMY_DECISION_LOGIC_JS)
                        .setTrustedScoringSignals(DUMMY_TRUSTED_SCORING_SIGNALS.toString())
                        .build();

        DBAdSelectionOverride dbAdSelectionOverride2 =
                DBAdSelectionOverride.builder()
                        .setAdSelectionConfigId(adSelectionConfigId2)
                        .setAppPackageName(TEST_PACKAGE_NAME)
                        .setDecisionLogicJS(DUMMY_DECISION_LOGIC_JS)
                        .setTrustedScoringSignals(DUMMY_TRUSTED_SCORING_SIGNALS.toString())
                        .build();

        DBAdSelectionOverride dbAdSelectionOverride3 =
                DBAdSelectionOverride.builder()
                        .setAdSelectionConfigId(adSelectionConfigId3)
                        .setAppPackageName(TEST_PACKAGE_NAME)
                        .setDecisionLogicJS(DUMMY_DECISION_LOGIC_JS)
                        .setTrustedScoringSignals(DUMMY_TRUSTED_SCORING_SIGNALS.toString())
                        .build();

        mAdSelectionEntryDao.persistAdSelectionOverride(dbAdSelectionOverride1);
        mAdSelectionEntryDao.persistAdSelectionOverride(dbAdSelectionOverride2);
        mAdSelectionEntryDao.persistAdSelectionOverride(dbAdSelectionOverride3);

        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId1, TEST_PACKAGE_NAME));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId2, TEST_PACKAGE_NAME));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId3, TEST_PACKAGE_NAME));

        AdSelectionOverrideTestCallback callback = callResetAllOverrides(adSelectionService);

        assertTrue(callback.mIsSuccess);

        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId1, TEST_PACKAGE_NAME));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId2, TEST_PACKAGE_NAME));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId3, TEST_PACKAGE_NAME));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(SHORT_API_NAME_RESET_ALL_OVERRIDES),
                        eq(incorrectPackageName),
                        eq(STATUS_SUCCESS),
                        anyInt());
    }

    @Test
    public void testResetAllAdSelectionConfigRemoteOverridesSuccess() throws Exception {
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        when(mDevContextFilterMock.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(TEST_PACKAGE_NAME)
                                .build());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        mFlags,
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

        AdSelectionConfig adSelectionConfig1 = mAdSelectionConfigBuilder.build();
        AdSelectionConfig adSelectionConfig2 =
                mAdSelectionConfigBuilder
                        .setSeller(AdTechIdentifier.fromString("adidas.com"))
                        .setDecisionLogicUri(Uri.parse("https://adidas.com/decisoin_logic_uri"))
                        .build();
        AdSelectionConfig adSelectionConfig3 =
                mAdSelectionConfigBuilder
                        .setSeller(AdTechIdentifier.fromString("nike.com"))
                        .setDecisionLogicUri(Uri.parse("https://nike.com/decisoin_logic_uri"))
                        .build();

        String adSelectionConfigId1 =
                AdSelectionDevOverridesHelper.calculateAdSelectionConfigId(adSelectionConfig1);
        String adSelectionConfigId2 =
                AdSelectionDevOverridesHelper.calculateAdSelectionConfigId(adSelectionConfig2);
        String adSelectionConfigId3 =
                AdSelectionDevOverridesHelper.calculateAdSelectionConfigId(adSelectionConfig3);

        DBAdSelectionOverride dbAdSelectionOverride1 =
                DBAdSelectionOverride.builder()
                        .setAdSelectionConfigId(adSelectionConfigId1)
                        .setAppPackageName(TEST_PACKAGE_NAME)
                        .setDecisionLogicJS(DUMMY_DECISION_LOGIC_JS)
                        .setTrustedScoringSignals(DUMMY_TRUSTED_SCORING_SIGNALS.toString())
                        .build();

        DBAdSelectionOverride dbAdSelectionOverride2 =
                DBAdSelectionOverride.builder()
                        .setAdSelectionConfigId(adSelectionConfigId2)
                        .setAppPackageName(TEST_PACKAGE_NAME)
                        .setDecisionLogicJS(DUMMY_DECISION_LOGIC_JS)
                        .setTrustedScoringSignals(DUMMY_TRUSTED_SCORING_SIGNALS.toString())
                        .build();

        DBAdSelectionOverride dbAdSelectionOverride3 =
                DBAdSelectionOverride.builder()
                        .setAdSelectionConfigId(adSelectionConfigId3)
                        .setAppPackageName(TEST_PACKAGE_NAME)
                        .setDecisionLogicJS(DUMMY_DECISION_LOGIC_JS)
                        .setTrustedScoringSignals(DUMMY_TRUSTED_SCORING_SIGNALS.toString())
                        .build();

        mAdSelectionEntryDao.persistAdSelectionOverride(dbAdSelectionOverride1);
        mAdSelectionEntryDao.persistAdSelectionOverride(dbAdSelectionOverride2);
        mAdSelectionEntryDao.persistAdSelectionOverride(dbAdSelectionOverride3);

        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId1, TEST_PACKAGE_NAME));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId2, TEST_PACKAGE_NAME));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId3, TEST_PACKAGE_NAME));

        AdSelectionOverrideTestCallback callback = callResetAllOverrides(adSelectionService);

        assertTrue(callback.mIsSuccess);

        assertFalse(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId1, TEST_PACKAGE_NAME));
        assertFalse(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId2, TEST_PACKAGE_NAME));
        assertFalse(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId3, TEST_PACKAGE_NAME));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(SHORT_API_NAME_RESET_ALL_OVERRIDES),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        anyInt());
    }

    @Test
    public void testResetAllAdSelectionConfigRemoteOverridesWithRevokedUserConsentSuccess()
            throws Exception {
        doReturn(AdServicesApiConsent.REVOKED)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(TEST_PACKAGE_NAME)
                                .build());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        mFlags,
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

        AdSelectionConfig adSelectionConfig1 = mAdSelectionConfigBuilder.build();
        AdSelectionConfig adSelectionConfig2 =
                mAdSelectionConfigBuilder
                        .setSeller(AdTechIdentifier.fromString("adidas.com"))
                        .setDecisionLogicUri(Uri.parse("https://adidas.com/decisoin_logic_uri"))
                        .build();
        AdSelectionConfig adSelectionConfig3 =
                mAdSelectionConfigBuilder
                        .setSeller(AdTechIdentifier.fromString("nike.com"))
                        .setDecisionLogicUri(Uri.parse("https://nike.com/decisoin_logic_uri"))
                        .build();

        String adSelectionConfigId1 =
                AdSelectionDevOverridesHelper.calculateAdSelectionConfigId(adSelectionConfig1);
        String adSelectionConfigId2 =
                AdSelectionDevOverridesHelper.calculateAdSelectionConfigId(adSelectionConfig2);
        String adSelectionConfigId3 =
                AdSelectionDevOverridesHelper.calculateAdSelectionConfigId(adSelectionConfig3);

        DBAdSelectionOverride dbAdSelectionOverride1 =
                DBAdSelectionOverride.builder()
                        .setAdSelectionConfigId(adSelectionConfigId1)
                        .setAppPackageName(TEST_PACKAGE_NAME)
                        .setDecisionLogicJS(DUMMY_DECISION_LOGIC_JS)
                        .setTrustedScoringSignals(DUMMY_TRUSTED_SCORING_SIGNALS.toString())
                        .build();

        DBAdSelectionOverride dbAdSelectionOverride2 =
                DBAdSelectionOverride.builder()
                        .setAdSelectionConfigId(adSelectionConfigId2)
                        .setAppPackageName(TEST_PACKAGE_NAME)
                        .setDecisionLogicJS(DUMMY_DECISION_LOGIC_JS)
                        .setTrustedScoringSignals(DUMMY_TRUSTED_SCORING_SIGNALS.toString())
                        .build();

        DBAdSelectionOverride dbAdSelectionOverride3 =
                DBAdSelectionOverride.builder()
                        .setAdSelectionConfigId(adSelectionConfigId3)
                        .setAppPackageName(TEST_PACKAGE_NAME)
                        .setDecisionLogicJS(DUMMY_DECISION_LOGIC_JS)
                        .setTrustedScoringSignals(DUMMY_TRUSTED_SCORING_SIGNALS.toString())
                        .build();

        mAdSelectionEntryDao.persistAdSelectionOverride(dbAdSelectionOverride1);
        mAdSelectionEntryDao.persistAdSelectionOverride(dbAdSelectionOverride2);
        mAdSelectionEntryDao.persistAdSelectionOverride(dbAdSelectionOverride3);

        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId1, TEST_PACKAGE_NAME));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId2, TEST_PACKAGE_NAME));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId3, TEST_PACKAGE_NAME));

        AdSelectionOverrideTestCallback callback = callResetAllOverrides(adSelectionService);

        assertTrue(callback.mIsSuccess);

        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId1, TEST_PACKAGE_NAME));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId2, TEST_PACKAGE_NAME));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId3, TEST_PACKAGE_NAME));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(SHORT_API_NAME_RESET_ALL_OVERRIDES),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_USER_CONSENT_REVOKED),
                        anyInt());
    }

    @Test
    public void testResetAllAdSelectionConfigRemoteOverridesFailsWithDevOptionsDisabled() {
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        when(mDevContextFilterMock.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        mFlags,
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

        AdSelectionConfig adSelectionConfig1 = mAdSelectionConfigBuilder.build();
        AdSelectionConfig adSelectionConfig2 =
                mAdSelectionConfigBuilder
                        .setSeller(AdTechIdentifier.fromString("adidas.com"))
                        .setDecisionLogicUri(Uri.parse("https://adidas.com/decisoin_logic_uri"))
                        .build();
        AdSelectionConfig adSelectionConfig3 =
                mAdSelectionConfigBuilder
                        .setSeller(AdTechIdentifier.fromString("nike.com"))
                        .setDecisionLogicUri(Uri.parse("https://nike.com/decisoin_logic_uri"))
                        .build();

        String adSelectionConfigId1 =
                AdSelectionDevOverridesHelper.calculateAdSelectionConfigId(adSelectionConfig1);
        String adSelectionConfigId2 =
                AdSelectionDevOverridesHelper.calculateAdSelectionConfigId(adSelectionConfig2);
        String adSelectionConfigId3 =
                AdSelectionDevOverridesHelper.calculateAdSelectionConfigId(adSelectionConfig3);

        DBAdSelectionOverride dbAdSelectionOverride1 =
                DBAdSelectionOverride.builder()
                        .setAdSelectionConfigId(adSelectionConfigId1)
                        .setAppPackageName(TEST_PACKAGE_NAME)
                        .setDecisionLogicJS(DUMMY_DECISION_LOGIC_JS)
                        .setTrustedScoringSignals(DUMMY_TRUSTED_SCORING_SIGNALS.toString())
                        .build();

        DBAdSelectionOverride dbAdSelectionOverride2 =
                DBAdSelectionOverride.builder()
                        .setAdSelectionConfigId(adSelectionConfigId2)
                        .setAppPackageName(TEST_PACKAGE_NAME)
                        .setDecisionLogicJS(DUMMY_DECISION_LOGIC_JS)
                        .setTrustedScoringSignals(DUMMY_TRUSTED_SCORING_SIGNALS.toString())
                        .build();

        DBAdSelectionOverride dbAdSelectionOverride3 =
                DBAdSelectionOverride.builder()
                        .setAdSelectionConfigId(adSelectionConfigId3)
                        .setAppPackageName(TEST_PACKAGE_NAME)
                        .setDecisionLogicJS(DUMMY_DECISION_LOGIC_JS)
                        .setTrustedScoringSignals(DUMMY_TRUSTED_SCORING_SIGNALS.toString())
                        .build();

        mAdSelectionEntryDao.persistAdSelectionOverride(dbAdSelectionOverride1);
        mAdSelectionEntryDao.persistAdSelectionOverride(dbAdSelectionOverride2);
        mAdSelectionEntryDao.persistAdSelectionOverride(dbAdSelectionOverride3);

        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId1, TEST_PACKAGE_NAME));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId2, TEST_PACKAGE_NAME));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId3, TEST_PACKAGE_NAME));

        assertThrows(SecurityException.class, () -> callResetAllOverrides(adSelectionService));

        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId1, TEST_PACKAGE_NAME));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId2, TEST_PACKAGE_NAME));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        adSelectionConfigId3, TEST_PACKAGE_NAME));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(SHORT_API_NAME_RESET_ALL_OVERRIDES),
                        eq(null), // Actually logged as ""
                        eq(STATUS_INTERNAL_ERROR),
                        anyInt());
    }

    @Test
    public void testCloseJSScriptEngineConnectionAtShutDown() {
        JSScriptEngine jsScriptEngineMock = mock(JSScriptEngine.class);
        doReturn(jsScriptEngineMock).when(() -> JSScriptEngine.getInstance(any(), any()));

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        mFlags,
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

        adSelectionService.destroy();
        verify(jsScriptEngineMock).shutdown();
    }

    @Test
    public void testJSScriptEngineConnectionExceptionAtShutDown() {
        JSScriptEngine jsScriptEngineMock = mock(JSScriptEngine.class);
        doThrow(JSSandboxIsNotAvailableException.class)
                .when(() -> JSScriptEngine.getInstance(any(), any()));

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        mFlags,
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

        adSelectionService.destroy();
        verify(jsScriptEngineMock, never()).shutdown();
    }

    @Test
    public void testReportImpressionForegroundCheckEnabledFails_throwsException() throws Exception {
        Assume.assumeTrue(JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());
        when(mDevContextFilterMock.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        doThrow(new FilterException(new WrongCallingApplicationStateException()))
                .when(mAdSelectionServiceFilterMock)
                .filterRequest(
                        mSeller,
                        TEST_PACKAGE_NAME,
                        true,
                        true,
                        CALLER_UID,
                        AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION,
                        Throttler.ApiKey.FLEDGE_API_REPORT_IMPRESSIONS,
                        DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        mFlags,
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

        ReportImpressionInput request =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(INCORRECT_AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();

        ReportImpressionTestCallback callback = callReportImpression(adSelectionService, request);

        // The call fails because there is no ad selection with the given ID
        assertFalse(callback.mIsSuccess);
        assertEquals(
                AdServicesStatusUtils.STATUS_BACKGROUND_CALLER,
                callback.mFledgeErrorResponse.getStatusCode());
        assertEquals(
                AdServicesStatusUtils.ILLEGAL_STATE_BACKGROUND_CALLER_ERROR_MESSAGE,
                callback.mFledgeErrorResponse.getErrorMessage());
    }

    @Test
    public void testOverrideAdSelectionForegroundCheckEnabledFails_throwsException()
            throws Exception {
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(TEST_PACKAGE_NAME)
                                .build());

        doThrow(new WrongCallingApplicationStateException())
                .when(mAppImportanceFilterMock)
                .assertCallerIsInForeground(
                        Process.myUid(),
                        AD_SERVICES_API_CALLED__API_NAME__OVERRIDE_AD_SELECTION_CONFIG_REMOTE_INFO,
                        null);

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        mFlags,
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

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        AdSelectionOverrideTestCallback callback =
                callAddOverrideForSelectAds(
                        adSelectionService,
                        adSelectionConfig,
                        DUMMY_DECISION_LOGIC_JS,
                        DUMMY_TRUSTED_SCORING_SIGNALS,
                        BUYERS_DECISION_LOGIC);

        // The call fails because there is no ad selection with the given ID
        assertFalse(callback.mIsSuccess);
        assertEquals(
                AdServicesStatusUtils.STATUS_BACKGROUND_CALLER,
                callback.mFledgeErrorResponse.getStatusCode());
        assertEquals(
                AdServicesStatusUtils.ILLEGAL_STATE_BACKGROUND_CALLER_ERROR_MESSAGE,
                callback.mFledgeErrorResponse.getErrorMessage());
    }

    @Test
    public void testOverrideAdSelectionForegroundCheckDisabled_acceptBackgroundApp()
            throws Exception {
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(TEST_PACKAGE_NAME)
                                .build());

        doThrow(new WrongCallingApplicationStateException())
                .when(mAppImportanceFilterMock)
                .assertCallerIsInForeground(
                        Process.myUid(),
                        AD_SERVICES_API_CALLED__API_NAME__OVERRIDE_AD_SELECTION_CONFIG_REMOTE_INFO,
                        null);

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        FlagsWithOverriddenFledgeChecks.createFlagsWithFledgeChecksDisabled(),
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

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        AdSelectionOverrideTestCallback callback =
                callAddOverrideForSelectAds(
                        adSelectionService,
                        adSelectionConfig,
                        DUMMY_DECISION_LOGIC_JS,
                        DUMMY_TRUSTED_SCORING_SIGNALS,
                        BUYERS_DECISION_LOGIC);

        assertTrue(callback.mIsSuccess);
    }

    @Test
    public void testRemoveOverrideForegroundCheckEnabledFails_throwsException() throws Exception {
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(TEST_PACKAGE_NAME)
                                .build());

        int apiName =
                AD_SERVICES_API_CALLED__API_NAME__REMOVE_AD_SELECTION_CONFIG_REMOTE_INFO_OVERRIDE;
        doThrow(new WrongCallingApplicationStateException())
                .when(mAppImportanceFilterMock)
                .assertCallerIsInForeground(Process.myUid(), apiName, null);

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        mFlags,
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

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        AdSelectionOverrideTestCallback callback =
                callRemoveOverride(adSelectionService, adSelectionConfig);

        // The call fails because there is no ad selection with the given ID
        assertFalse(callback.mIsSuccess);
        assertEquals(
                AdServicesStatusUtils.STATUS_BACKGROUND_CALLER,
                callback.mFledgeErrorResponse.getStatusCode());
        assertEquals(
                AdServicesStatusUtils.ILLEGAL_STATE_BACKGROUND_CALLER_ERROR_MESSAGE,
                callback.mFledgeErrorResponse.getErrorMessage());
    }

    @Test
    public void testRemoveOverrideForegroundCheckDisabled_acceptBackgroundApp() throws Exception {
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(TEST_PACKAGE_NAME)
                                .build());

        int apiName =
                AD_SERVICES_API_CALLED__API_NAME__REMOVE_AD_SELECTION_CONFIG_REMOTE_INFO_OVERRIDE;
        doThrow(new WrongCallingApplicationStateException())
                .when(mAppImportanceFilterMock)
                .assertCallerIsInForeground(Process.myUid(), apiName, null);

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        FlagsWithOverriddenFledgeChecks.createFlagsWithFledgeChecksDisabled(),
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

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        AdSelectionOverrideTestCallback callback =
                callRemoveOverride(adSelectionService, adSelectionConfig);

        assertTrue(callback.mIsSuccess);
    }

    @Test
    public void testResetAllOverridesForegroundCheckEnabledFails_throwsException()
            throws Exception {
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(TEST_PACKAGE_NAME)
                                .build());

        int apiName =
                AD_SERVICES_API_CALLED__API_NAME__RESET_ALL_AD_SELECTION_CONFIG_REMOTE_OVERRIDES;
        doThrow(new WrongCallingApplicationStateException())
                .when(mAppImportanceFilterMock)
                .assertCallerIsInForeground(Process.myUid(), apiName, null);

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        mFlags,
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

        AdSelectionOverrideTestCallback callback = callResetAllOverrides(adSelectionService);

        // The call fails because there is no ad selection with the given ID
        assertFalse(callback.mIsSuccess);
        assertEquals(
                AdServicesStatusUtils.STATUS_BACKGROUND_CALLER,
                callback.mFledgeErrorResponse.getStatusCode());
        assertEquals(
                AdServicesStatusUtils.ILLEGAL_STATE_BACKGROUND_CALLER_ERROR_MESSAGE,
                callback.mFledgeErrorResponse.getErrorMessage());
    }

    @Test
    public void testResetAllOverridesForegroundCheckDisabled_acceptBackgroundApp()
            throws Exception {
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(TEST_PACKAGE_NAME)
                                .build());

        int apiName =
                AD_SERVICES_API_CALLED__API_NAME__RESET_ALL_AD_SELECTION_CONFIG_REMOTE_OVERRIDES;
        doThrow(new WrongCallingApplicationStateException())
                .when(mAppImportanceFilterMock)
                .assertCallerIsInForeground(Process.myUid(), apiName, null);

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        FlagsWithOverriddenFledgeChecks.createFlagsWithFledgeChecksDisabled(),
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

        AdSelectionOverrideTestCallback callback = callResetAllOverrides(adSelectionService);

        assertTrue(callback.mIsSuccess);
    }

    @Test
    public void testReportImpressionFailsWithInvalidPackageName() throws Exception {
        Assume.assumeTrue(JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());
        String otherPackageName = CommonFixture.TEST_PACKAGE_NAME + "incorrectPackage";
        Uri sellerReportingUri = mMockWebServerRule.uriForPath(mSellerReportingPath);
        Uri buyerReportingUri = mMockWebServerRule.uriForPath(mBuyerReportingPath);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        doThrow(new FilterException(new FledgeAuthorizationFilter.CallerMismatchException()))
                .when(mAdSelectionServiceFilterMock)
                .filterRequest(
                        mSeller,
                        otherPackageName,
                        true,
                        true,
                        CALLER_UID,
                        AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION,
                        Throttler.ApiKey.FLEDGE_API_REPORT_IMPRESSIONS,
                        DevContext.createForDevOptionsDisabled());

        String sellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) {"
                        + " \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + sellerReportingUri
                        + "' } };\n"
                        + "}";

        String buyerDecisionLogicJs =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,"
                        + " contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'results': {'reporting_uri': '"
                        + buyerReportingUri
                        + "' } };\n"
                        + "}";

        mMockWebServerRule.startMockWebServer(
                List.of(
                        new MockResponse().setBody(sellerDecisionLogicJs),
                        new MockResponse(),
                        new MockResponse()));

        DBBuyerDecisionLogic dbBuyerDecisionLogic =
                new DBBuyerDecisionLogic.Builder()
                        .setBiddingLogicUri(BUYER_BIDDING_LOGIC_URI)
                        .setBuyerDecisionLogicJs(buyerDecisionLogicJs)
                        .build();

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(mCustomAudienceSignals)
                        .setBuyerContextualSignals(mContextualSignals.toString())
                        .setBiddingLogicUri(BUYER_BIDDING_LOGIC_URI)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        mFlags,
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

        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(otherPackageName)
                        .build();

        ReportImpressionTestCallback callback = callReportImpression(adSelectionService, input);

        assertFalse(callback.mIsSuccess);
        assertEquals(callback.mFledgeErrorResponse.getStatusCode(), STATUS_UNAUTHORIZED);
        assertEquals(
                callback.mFledgeErrorResponse.getErrorMessage(),
                AdServicesStatusUtils
                        .SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_ON_BEHALF_ERROR_MESSAGE);
    }

    @Test
    public void testReportImpressionFailsWhenAppCannotUsePPApi() throws Exception {
        Assume.assumeTrue(JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        doThrow(new FilterException(new FledgeAuthorizationFilter.AdTechNotAllowedException()))
                .when(mAdSelectionServiceFilterMock)
                .filterRequest(
                        mSeller,
                        TEST_PACKAGE_NAME,
                        true,
                        true,
                        CALLER_UID,
                        AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION,
                        Throttler.ApiKey.FLEDGE_API_REPORT_IMPRESSIONS,
                        DevContext.createForDevOptionsDisabled());

        Uri sellerReportingUri = mMockWebServerRule.uriForPath(mSellerReportingPath);
        Uri buyerReportingUri = mMockWebServerRule.uriForPath(mBuyerReportingPath);

        String sellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) {"
                        + " \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + sellerReportingUri
                        + "' } };\n"
                        + "}";

        String buyerDecisionLogicJs =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,"
                        + " contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'results': {'reporting_uri': '"
                        + buyerReportingUri
                        + "' } };\n"
                        + "}";

        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        List.of(
                                new MockResponse().setBody(sellerDecisionLogicJs),
                                new MockResponse(),
                                new MockResponse()));

        DBBuyerDecisionLogic dbBuyerDecisionLogic =
                new DBBuyerDecisionLogic.Builder()
                        .setBiddingLogicUri(BUYER_BIDDING_LOGIC_URI)
                        .setBuyerDecisionLogicJs(buyerDecisionLogicJs)
                        .build();

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(mCustomAudienceSignals)
                        .setBuyerContextualSignals(mContextualSignals.toString())
                        .setBiddingLogicUri(BUYER_BIDDING_LOGIC_URI)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        mFlags,
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

        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();
        ReportImpressionTestCallback callback = callReportImpression(adSelectionService, input);

        assertFalse(callback.mIsSuccess);
        assertEquals(STATUS_CALLER_NOT_ALLOWED, callback.mFledgeErrorResponse.getStatusCode());
        assertEquals(
                AdServicesStatusUtils.SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_ERROR_MESSAGE,
                callback.mFledgeErrorResponse.getErrorMessage());

        // Confirm a duplicate log entry does not exist.
        // AdSelectionServiceFilter ensures the failing assertion is logged internally.
        verify(mAdServicesLoggerMock, never())
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_CALLER_NOT_ALLOWED),
                        anyInt());
    }

    @Test
    public void testReportImpressionFailsWhenSellerFailsEnrollmentCheck() throws Exception {
        Assume.assumeTrue(JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());
        Uri sellerReportingUri = mMockWebServerRule.uriForPath(mSellerReportingPath);
        Uri buyerReportingUri = mMockWebServerRule.uriForPath(mBuyerReportingPath);

        doThrow(new FilterException(new FledgeAuthorizationFilter.AdTechNotAllowedException()))
                .when(mAdSelectionServiceFilterMock)
                .filterRequest(
                        mSeller,
                        TEST_PACKAGE_NAME,
                        true,
                        true,
                        CALLER_UID,
                        AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION,
                        Throttler.ApiKey.FLEDGE_API_REPORT_IMPRESSIONS,
                        DevContext.createForDevOptionsDisabled());

        String sellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) {"
                        + " \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + sellerReportingUri
                        + "' } };\n"
                        + "}";

        String buyerDecisionLogicJs =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,"
                        + " contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'results': {'reporting_uri': '"
                        + buyerReportingUri
                        + "' } };\n"
                        + "}";

        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        List.of(
                                new MockResponse().setBody(sellerDecisionLogicJs),
                                new MockResponse(),
                                new MockResponse()));

        DBBuyerDecisionLogic dbBuyerDecisionLogic =
                new DBBuyerDecisionLogic.Builder()
                        .setBiddingLogicUri(BUYER_BIDDING_LOGIC_URI)
                        .setBuyerDecisionLogicJs(buyerDecisionLogicJs)
                        .build();

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(mCustomAudienceSignals)
                        .setBuyerContextualSignals(mContextualSignals.toString())
                        .setBiddingLogicUri(BUYER_BIDDING_LOGIC_URI)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        mFlags,
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

        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();
        ReportImpressionTestCallback callback = callReportImpression(adSelectionService, input);

        assertFalse(callback.mIsSuccess);
        assertEquals(STATUS_CALLER_NOT_ALLOWED, callback.mFledgeErrorResponse.getStatusCode());
        assertEquals(
                AdServicesStatusUtils.SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_ERROR_MESSAGE,
                callback.mFledgeErrorResponse.getErrorMessage());

        // Confirm a duplicate log entry does not exist.
        // AdSelectionServiceFilter ensures the failing assertion is logged internally.
        Mockito.verify(mAdServicesLoggerMock, never())
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_CALLER_NOT_ALLOWED),
                        anyInt());
    }

    @Test
    @FlakyTest(bugId = 315521295)
    public void testReportImpressionSucceedsWhenAdTechPassesEnrollmentCheck() throws Exception {
        Assume.assumeTrue(JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());

        // Reset flags to perform enrollment check
        boolean enrollmentCheckEnabled = true;
        mFlags = new AdSelectionServicesTestsFlags(enrollmentCheckEnabled);

        Uri sellerReportingUri = mMockWebServerRule.uriForPath(mSellerReportingPath);
        Uri buyerReportingUri = mMockWebServerRule.uriForPath(mBuyerReportingPath);

        String sellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) {"
                        + " \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + sellerReportingUri
                        + "' } };\n"
                        + "}";

        String buyerDecisionLogicJs =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,"
                        + " contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'results': {'reporting_uri': '"
                        + buyerReportingUri
                        + "' } };\n"
                        + "}";

        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        List.of(
                                new MockResponse().setBody(sellerDecisionLogicJs),
                                new MockResponse(),
                                new MockResponse()));

        DBBuyerDecisionLogic dbBuyerDecisionLogic =
                new DBBuyerDecisionLogic.Builder()
                        .setBiddingLogicUri(BUYER_BIDDING_LOGIC_URI)
                        .setBuyerDecisionLogicJs(buyerDecisionLogicJs)
                        .build();

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(mCustomAudienceSignals)
                        .setBuyerContextualSignals(mContextualSignals.toString())
                        .setBiddingLogicUri(BUYER_BIDDING_LOGIC_URI)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        doNothing()
                .when(mAdSelectionServiceFilterMock)
                .filterRequest(
                        mSeller,
                        TEST_PACKAGE_NAME,
                        true,
                        true,
                        CALLER_UID,
                        AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION,
                        Throttler.ApiKey.FLEDGE_API_REPORT_IMPRESSIONS,
                        DevContext.createForDevOptionsDisabled());

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        mFlags,
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

        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();

        // Count down callback + log interaction.
        ReportImpressionTestCallback callback =
                callReportImpression(adSelectionService, input, true);

        assertTrue(callback.mIsSuccess);
        RecordedRequest fetchRequest = server.takeRequest();
        assertEquals(mFetchJavaScriptPathSeller, fetchRequest.getPath());

        List<String> notifications =
                ImmutableList.of(server.takeRequest().getPath(), server.takeRequest().getPath());

        assertThat(notifications).containsExactly(mSellerReportingPath, mBuyerReportingPath);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        anyInt());
    }

    @Test
    public void testAdSelectionConfigInvalidSellerAndSellerUris() throws Exception {
        Assume.assumeTrue(JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());
        Uri sellerReportingUri = mMockWebServerRule.uriForPath(mSellerReportingPath);
        Uri buyerReportingUri = mMockWebServerRule.uriForPath(mBuyerReportingPath);

        String sellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) {"
                        + " \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + sellerReportingUri
                        + "' } };\n"
                        + "}";

        String buyerDecisionLogicJs =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,"
                        + " contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'results': {'reporting_uri': '"
                        + buyerReportingUri
                        + "' } };\n"
                        + "}";

        DBBuyerDecisionLogic dbBuyerDecisionLogic =
                new DBBuyerDecisionLogic.Builder()
                        .setBiddingLogicUri(BUYER_BIDDING_LOGIC_URI)
                        .setBuyerDecisionLogicJs(buyerDecisionLogicJs)
                        .build();

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(mCustomAudienceSignals)
                        .setBuyerContextualSignals(mContextualSignals.toString())
                        .setBiddingLogicUri(BUYER_BIDDING_LOGIC_URI)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);
        AdSelectionConfig invalidAdSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setSeller(SELLER_VALID)
                        .setDecisionLogicUri(DECISION_LOGIC_URI_INCONSISTENT)
                        .build();
        when(mDevContextFilterMock.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        mFlags,
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
        ReportImpressionInput request =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(INCORRECT_AD_SELECTION_ID)
                        .setAdSelectionConfig(invalidAdSelectionConfig)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();

        ReportImpressionTestCallback callback = callReportImpression(adSelectionService, request);

        assertFalse(callback.mIsSuccess);

        FledgeErrorResponse response = callback.mFledgeErrorResponse;
        assertEquals(
                "Error response code mismatch", STATUS_INVALID_ARGUMENT, response.getStatusCode());

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_INVALID_ARGUMENT),
                        anyInt());
    }

    @Test
    @FlakyTest(bugId = 315521295)
    public void testReportImpressionSuccessThrottledSubsequentCallFailure() throws Exception {
        Assume.assumeTrue(JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());
        Uri sellerReportingUri = mMockWebServerRule.uriForPath(mSellerReportingPath);
        Uri buyerReportingUri = mMockWebServerRule.uriForPath(mBuyerReportingPath);

        String sellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) {"
                        + " \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + sellerReportingUri
                        + "' } };\n"
                        + "}";

        String buyerDecisionLogicJs =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,"
                        + " contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'results': {'reporting_uri': '"
                        + buyerReportingUri
                        + "' } };\n"
                        + "}";

        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        List.of(
                                new MockResponse().setBody(sellerDecisionLogicJs),
                                new MockResponse(),
                                new MockResponse()));

        DBBuyerDecisionLogic dbBuyerDecisionLogic =
                new DBBuyerDecisionLogic.Builder()
                        .setBiddingLogicUri(BUYER_BIDDING_LOGIC_URI)
                        .setBuyerDecisionLogicJs(buyerDecisionLogicJs)
                        .build();

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(mCustomAudienceSignals)
                        .setBuyerContextualSignals(mContextualSignals.toString())
                        .setBiddingLogicUri(BUYER_BIDDING_LOGIC_URI)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        Throttler.destroyExistingThrottler();
        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        mFlags,
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

        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();

        // First call should succeed
        // Count down callback + log interaction.
        ReportImpressionTestCallback callbackFirstCall =
                callReportImpression(adSelectionService, input, true);

        doThrow(new FilterException(new LimitExceededException(RATE_LIMIT_REACHED_ERROR_MESSAGE)))
                .when(mAdSelectionServiceFilterMock)
                .filterRequest(
                        mSeller,
                        TEST_PACKAGE_NAME,
                        true,
                        true,
                        CALLER_UID,
                        AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION,
                        Throttler.ApiKey.FLEDGE_API_REPORT_IMPRESSIONS,
                        DevContext.createForDevOptionsDisabled());

        // Immediately made subsequent call should fail
        ReportImpressionTestCallback callbackSubsequentCall =
                callReportImpression(adSelectionService, input);

        assertTrue(callbackFirstCall.mIsSuccess);
        RecordedRequest fetchRequest = server.takeRequest();
        assertEquals(mFetchJavaScriptPathSeller, fetchRequest.getPath());

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        anyInt());

        assertFalse(callbackSubsequentCall.mIsSuccess);
        assertEquals(
                STATUS_RATE_LIMIT_REACHED,
                callbackSubsequentCall.mFledgeErrorResponse.getStatusCode());
        assertEquals(
                callbackSubsequentCall.mFledgeErrorResponse.getErrorMessage(),
                RATE_LIMIT_REACHED_ERROR_MESSAGE);
        resetThrottlerToNoRateLimits();
    }

    @Test
    @FlakyTest(bugId = 315521295)
    public void testReportImpressionDoestNotReportWhenUrisDoNotMatchDomain() throws Exception {
        Assume.assumeTrue(JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());
        // Instantiate a server with different domain from buyer and seller for reporting
        MockWebServer reportingServer = new MockWebServer();
        reportingServer.play();
        Uri sellerReportingUri = Uri.parse(reportingServer.getUrl(mSellerReportingPath).toString());
        Uri buyerReportingUri = Uri.parse(reportingServer.getUrl(mBuyerReportingPath).toString());

        Uri biddingLogicUri = (mMockWebServerRule.uriForPath(mFetchJavaScriptPathBuyer));

        String sellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) {"
                        + " \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + sellerReportingUri
                        + "' } };\n"
                        + "}";

        String buyerDecisionLogicJs =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,"
                        + " contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'results': {'reporting_uri': '"
                        + buyerReportingUri
                        + "' } };\n"
                        + "}";

        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        List.of(new MockResponse().setBody(sellerDecisionLogicJs)));

        DBBuyerDecisionLogic dbBuyerDecisionLogic =
                new DBBuyerDecisionLogic.Builder()
                        .setBiddingLogicUri(biddingLogicUri)
                        .setBuyerDecisionLogicJs(buyerDecisionLogicJs)
                        .build();

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(mCustomAudienceSignals)
                        .setBuyerContextualSignals(mContextualSignals.toString())
                        .setBiddingLogicUri(biddingLogicUri)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        mFlags,
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

        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();

        // Count down callback + log interaction.
        ReportImpressionTestCallback callback =
                callReportImpression(adSelectionService, input, true);
        assertTrue(callback.mIsSuccess);
        RecordedRequest fetchRequest = server.takeRequest();
        assertEquals(mFetchJavaScriptPathSeller, fetchRequest.getPath());

        // Assert that reporting didn't happen
        assertEquals(reportingServer.getRequestCount(), 0);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        anyInt());
    }

    @Test
    public void testReportImpressionOnlyReportsBuyerWhenSellerReportingUriDoesNotMatchDomain()
            throws Exception {
        Assume.assumeTrue(JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());

        Uri buyerReportingUri = mMockWebServerRule.uriForPath(mBuyerReportingPath);

        // Instantiate a server with a different domain than seller
        MockWebServer sellerServer = new MockWebServer();
        sellerServer.play();
        Uri sellerReportingUri = Uri.parse(sellerServer.getUrl(mSellerReportingPath).toString());

        Uri biddingLogicUri = (mMockWebServerRule.uriForPath(mFetchJavaScriptPathBuyer));

        String sellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) {"
                        + " \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + sellerReportingUri
                        + "' } };\n"
                        + "}";

        String buyerDecisionLogicJs =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,"
                        + " contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'results': {'reporting_uri': '"
                        + buyerReportingUri
                        + "' } };\n"
                        + "}";

        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        List.of(
                                new MockResponse().setBody(sellerDecisionLogicJs),
                                new MockResponse()));

        DBBuyerDecisionLogic dbBuyerDecisionLogic =
                new DBBuyerDecisionLogic.Builder()
                        .setBiddingLogicUri(biddingLogicUri)
                        .setBuyerDecisionLogicJs(buyerDecisionLogicJs)
                        .build();

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(mCustomAudienceSignals)
                        .setBuyerContextualSignals(mContextualSignals.toString())
                        .setBiddingLogicUri(biddingLogicUri)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        mFlags,
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

        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();

        // Count down callback + log interaction.
        ReportImpressionTestCallback callback =
                callReportImpression(adSelectionService, input, true);
        assertTrue(callback.mIsSuccess);
        RecordedRequest fetchRequest = server.takeRequest();
        assertEquals(mFetchJavaScriptPathSeller, fetchRequest.getPath());

        assertEquals(mBuyerReportingPath, server.takeRequest().getPath());

        // Assert that buyer reporting didn't happen
        assertEquals(sellerServer.getRequestCount(), 0);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        anyInt());
    }

    @Test
    public void testReportImpressionOnlyReportsSellerWhenBuyerReportingUriDoesNotMatchDomain()
            throws Exception {
        Assume.assumeTrue(JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());

        Uri sellerReportingUri = mMockWebServerRule.uriForPath(mSellerReportingPath);

        // Instantiate a server with a different domain than buyer
        MockWebServer buyerServer = new MockWebServer();
        buyerServer.play();
        Uri buyerReportingUri = Uri.parse(buyerServer.getUrl(mBuyerReportingPath).toString());

        Uri biddingLogicUri = (mMockWebServerRule.uriForPath(mFetchJavaScriptPathBuyer));

        String sellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) {"
                        + " \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + sellerReportingUri
                        + "' } };\n"
                        + "}";

        String buyerDecisionLogicJs =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,"
                        + " contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'results': {'reporting_uri': '"
                        + buyerReportingUri
                        + "' } };\n"
                        + "}";

        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        List.of(
                                new MockResponse().setBody(sellerDecisionLogicJs),
                                new MockResponse()));

        DBBuyerDecisionLogic dbBuyerDecisionLogic =
                new DBBuyerDecisionLogic.Builder()
                        .setBiddingLogicUri(biddingLogicUri)
                        .setBuyerDecisionLogicJs(buyerDecisionLogicJs)
                        .build();

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(mCustomAudienceSignals)
                        .setBuyerContextualSignals(mContextualSignals.toString())
                        .setBiddingLogicUri(biddingLogicUri)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        mFlags,
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

        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();

        // Count down callback + log interaction.
        ReportImpressionTestCallback callback =
                callReportImpression(adSelectionService, input, true);
        assertTrue(callback.mIsSuccess);
        RecordedRequest fetchRequest = server.takeRequest();
        assertEquals(mFetchJavaScriptPathSeller, fetchRequest.getPath());

        assertEquals(mSellerReportingPath, server.takeRequest().getPath());

        // Assert that buyer reporting didn't happen
        assertEquals(buyerServer.getRequestCount(), 0);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        anyInt());
    }

    @Test
    @FlakyTest(bugId = 315521295)
    public void testReportImpressionSuccessWithValidImpressionReportingSubdomains()
            throws Exception {
        Assume.assumeTrue(JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());

        Uri sellerReportingUriWithSubdomain =
                CommonFixture.getUriWithValidSubdomain(
                        AdSelectionConfigFixture.SELLER.toString(), mSellerReportingPath);
        Uri buyerReportingUriWithSubdomain =
                CommonFixture.getUriWithValidSubdomain(
                        AdSelectionConfigFixture.BUYER.toString(), mBuyerReportingPath);

        Uri sellerTrustedScoringSignalsUriWithSubdomain =
                CommonFixture.getUriWithValidSubdomain(
                        AdSelectionConfigFixture.SELLER.toString(),
                        mFetchTrustedScoringSignalsPath);
        Uri biddingLogicUriWithSubdomain =
                CommonFixture.getUriWithValidSubdomain(
                        AdSelectionConfigFixture.BUYER.toString(), mFetchJavaScriptPathBuyer);

        String sellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) {"
                        + " \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + sellerReportingUriWithSubdomain
                        + "' } };\n"
                        + "}";

        String buyerDecisionLogicJs =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,"
                        + " contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'results': {'reporting_uri': '"
                        + buyerReportingUriWithSubdomain
                        + "' } };\n"
                        + "}";

        doReturn(
                        Futures.immediateFuture(
                                AdServicesHttpClientResponse.builder()
                                        .setResponseBody(sellerDecisionLogicJs)
                                        .setResponseHeaders(
                                                ImmutableMap.<String, List<String>>builder()
                                                        .build())
                                        .build()))
                .when(mClientSpy)
                .fetchPayload(any(AdServicesHttpClientRequest.class));
        doReturn(Futures.immediateVoidFuture()).when(mClientSpy).getAndReadNothing(any(), any());

        DBBuyerDecisionLogic dbBuyerDecisionLogic =
                new DBBuyerDecisionLogic.Builder()
                        .setBiddingLogicUri(biddingLogicUriWithSubdomain)
                        .setBuyerDecisionLogicJs(buyerDecisionLogicJs)
                        .build();

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(
                                CustomAudienceSignalsFixture.aCustomAudienceSignalsBuilder()
                                        .setBuyer(AdSelectionConfigFixture.BUYER)
                                        .build())
                        .setBuyerContextualSignals(mContextualSignals.toString())
                        .setBiddingLogicUri(biddingLogicUriWithSubdomain)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        AdSelectionConfig adSelectionConfig =
                mAdSelectionConfigBuilder
                        .setSeller(AdSelectionConfigFixture.SELLER)
                        .setDecisionLogicUri(sellerReportingUriWithSubdomain)
                        .setTrustedScoringSignalsUri(sellerTrustedScoringSignalsUriWithSubdomain)
                        .setCustomAudienceBuyers(Arrays.asList(AdSelectionConfigFixture.BUYER))
                        .build();

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        mFlags,
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

        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();

        // Count down callback + log interaction.
        ReportImpressionTestCallback callback =
                callReportImpression(adSelectionService, input, true);

        assertWithMessage("Impression reporting callback success")
                .that(callback.mIsSuccess)
                .isTrue();

        verify(mClientSpy).fetchPayload(any(AdServicesHttpClientRequest.class));
        verify(mClientSpy).getAndReadNothing(eq(buyerReportingUriWithSubdomain), eq(mDevContext));
        verify(mClientSpy).getAndReadNothing(eq(sellerReportingUriWithSubdomain), eq(mDevContext));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        anyInt());
    }

    @Test
    public void testReportImpressionOnlyReportsSellerWhenBuyerReportingUriIsNotEnrolled()
            throws Exception {
        Assume.assumeTrue(JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());

        Uri sellerReportingUri = mMockWebServerRule.uriForPath(mSellerReportingPath);
        Uri buyerReportingUri = mMockWebServerRule.uriForPath(mBuyerReportingPath);

        Flags flagsWithEnrollment = new AdSelectionServicesTestsFlags(true);

        doThrow(new FledgeAuthorizationFilter.AdTechNotAllowedException())
                .when(mFledgeAuthorizationFilterMock)
                .assertAdTechEnrolled(
                        AdTechIdentifier.fromString(buyerReportingUri.getHost()),
                        AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION);

        Uri biddingLogicUri = (mMockWebServerRule.uriForPath(mFetchJavaScriptPathBuyer));

        String sellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) {"
                        + " \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + sellerReportingUri
                        + "' } };\n"
                        + "}";

        String buyerDecisionLogicJs =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,"
                        + " contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'results': {'reporting_uri': '"
                        + buyerReportingUri
                        + "' } };\n"
                        + "}";

        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        new Dispatcher() {
                            @Override
                            public MockResponse dispatch(RecordedRequest request) {
                                switch (request.getPath()) {
                                    case mFetchJavaScriptPathSeller:
                                        return new MockResponse().setBody(sellerDecisionLogicJs);
                                    case mSellerReportingPath:
                                        return new MockResponse();
                                    default:
                                        throw new IllegalStateException(
                                                "Only seller reporting can occur");
                                }
                            }
                        });

        DBBuyerDecisionLogic dbBuyerDecisionLogic =
                new DBBuyerDecisionLogic.Builder()
                        .setBiddingLogicUri(biddingLogicUri)
                        .setBuyerDecisionLogicJs(buyerDecisionLogicJs)
                        .build();

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(mCustomAudienceSignals)
                        .setBuyerContextualSignals(mContextualSignals.toString())
                        .setBiddingLogicUri(biddingLogicUri)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        flagsWithEnrollment,
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

        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();

        // Count down callback + log interaction.
        ReportImpressionTestCallback callback =
                callReportImpression(adSelectionService, input, true);
        assertTrue(callback.mIsSuccess);
        RecordedRequest fetchRequest = server.takeRequest();
        assertEquals(mFetchJavaScriptPathSeller, fetchRequest.getPath());

        assertEquals(mSellerReportingPath, server.takeRequest().getPath());

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        anyInt());
    }

    @Test
    public void testReportImpressionReportsToBothWithEnrollmentCheckDisabledBuyerNotEnrolled()
            throws Exception {
        Assume.assumeTrue(JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());

        Uri sellerReportingUri = mMockWebServerRule.uriForPath(mSellerReportingPath);
        Uri buyerReportingUri = mMockWebServerRule.uriForPath(mBuyerReportingPath);

        // Makes buyer enrollment fail if it is checked, but this shouldn't be checked due to the
        // enrollment flag
        doThrow(new FledgeAuthorizationFilter.AdTechNotAllowedException())
                .when(mFledgeAuthorizationFilterMock)
                .assertAdTechEnrolled(any(AdTechIdentifier.class), anyInt());

        Uri biddingLogicUri = (mMockWebServerRule.uriForPath(mFetchJavaScriptPathBuyer));

        String sellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) {"
                        + " \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + sellerReportingUri
                        + "' } };\n"
                        + "}";

        String buyerDecisionLogicJs =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,"
                        + " contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'results': {'reporting_uri': '"
                        + buyerReportingUri
                        + "' } };\n"
                        + "}";

        MockWebServer server =
                mMockWebServerRule.startMockWebServer(
                        List.of(
                                new MockResponse().setBody(sellerDecisionLogicJs),
                                new MockResponse(),
                                new MockResponse()));

        DBBuyerDecisionLogic dbBuyerDecisionLogic =
                new DBBuyerDecisionLogic.Builder()
                        .setBiddingLogicUri(biddingLogicUri)
                        .setBuyerDecisionLogicJs(buyerDecisionLogicJs)
                        .build();

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(mCustomAudienceSignals)
                        .setBuyerContextualSignals(mContextualSignals.toString())
                        .setBiddingLogicUri(biddingLogicUri)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
        mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        mFlags,
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

        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();

        // Count down callback + log interaction.
        ReportImpressionTestCallback callback =
                callReportImpression(adSelectionService, input, true);

        assertTrue(callback.mIsSuccess);
        RecordedRequest fetchRequest = server.takeRequest();
        assertEquals(mFetchJavaScriptPathSeller, fetchRequest.getPath());

        List<String> notifications =
                ImmutableList.of(server.takeRequest().getPath(), server.takeRequest().getPath());

        assertThat(notifications).containsExactly(mSellerReportingPath, mBuyerReportingPath);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        anyInt());
    }

    @Test
    public void testReportImpression_webViewNotInstalled_failsGracefully() throws Exception {
        // A null package means WebView is not installed
        doReturn(null).when(WebView::getCurrentWebViewPackage);

        // Shut down any running JSScriptEngine to ensure the new singleton gets picked up
        JSScriptEngine.getInstance(CONTEXT, LoggerFactory.getFledgeLogger()).shutdown();

        try {
            Uri sellerReportingUri = mMockWebServerRule.uriForPath(mSellerReportingPath);
            Uri buyerReportingUri = mMockWebServerRule.uriForPath(mBuyerReportingPath);

            Flags flagsWithEnrollment = new AdSelectionServicesTestsFlags(true);

            Uri biddingLogicUri = (mMockWebServerRule.uriForPath(mFetchJavaScriptPathBuyer));

            String sellerDecisionLogicJs =
                    "function reportResult(ad_selection_config, render_uri, bid, "
                            + "contextual_signals) {"
                            + " \n"
                            + " return {'status': 0, 'results': {'signals_for_buyer':"
                            + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                            + sellerReportingUri
                            + "' } };\n"
                            + "}";

            String buyerDecisionLogicJs =
                    "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,"
                            + " contextual_signals, custom_audience_signals) { \n"
                            + " return {'status': 0, 'results': {'reporting_uri': '"
                            + buyerReportingUri
                            + "' } };\n"
                            + "}";

            mMockWebServerRule.startMockWebServer(
                    new Dispatcher() {
                        @Override
                        public MockResponse dispatch(RecordedRequest request) {
                            switch (request.getPath()) {
                                case mFetchJavaScriptPathSeller:
                                    return new MockResponse().setBody(sellerDecisionLogicJs);
                                default:
                                    throw new IllegalStateException(
                                            "Only JavaScript logic fetch can occur");
                            }
                        }
                    });

            DBBuyerDecisionLogic dbBuyerDecisionLogic =
                    new DBBuyerDecisionLogic.Builder()
                            .setBiddingLogicUri(biddingLogicUri)
                            .setBuyerDecisionLogicJs(buyerDecisionLogicJs)
                            .build();

            DBAdSelection dbAdSelection =
                    new DBAdSelection.Builder()
                            .setAdSelectionId(AD_SELECTION_ID)
                            .setCustomAudienceSignals(mCustomAudienceSignals)
                            .setBuyerContextualSignals(mContextualSignals.toString())
                            .setBiddingLogicUri(biddingLogicUri)
                            .setWinningAdRenderUri(RENDER_URI)
                            .setWinningAdBid(BID)
                            .setCreationTimestamp(ACTIVATION_TIME)
                            .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                            .build();

            mAdSelectionEntryDao.persistAdSelection(dbAdSelection);
            mAdSelectionEntryDao.persistBuyerDecisionLogic(dbBuyerDecisionLogic);

            AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

            when(mDevContextFilterMock.createDevContext())
                    .thenReturn(DevContext.createForDevOptionsDisabled());

            // Create new service impl to let the WebView stub take effect
            AdSelectionServiceImpl adSelectionService =
                    new AdSelectionServiceImpl(
                            mAdSelectionEntryDao,
                            mAppInstallDao,
                            mCustomAudienceDao,
                            mEncodedPayloadDao,
                            mFrequencyCapDao,
                            mEncryptionKeyDao,
                            mEnrollmentDao,
                            mClientSpy,
                            mDevContextFilterMock,
                            mLightweightExecutorService,
                            mBackgroundExecutorService,
                            mScheduledExecutor,
                            CONTEXT,
                            mAdServicesLoggerMock,
                            flagsWithEnrollment,
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

            ReportImpressionInput input =
                    new ReportImpressionInput.Builder()
                            .setAdSelectionId(AD_SELECTION_ID)
                            .setAdSelectionConfig(adSelectionConfig)
                            .setCallerPackageName(TEST_PACKAGE_NAME)
                            .build();

            // Count down callback + log interaction.
            // Impression reporting should still fail due to unsupported WebView,
            // but gracefully instead of crashing the process
            ReportImpressionTestCallback callback =
                    callReportImpression(adSelectionService, input, true);
            assertWithMessage("Callback success").that(callback.mIsSuccess).isFalse();
            assertWithMessage("Error status code")
                    .that(callback.mFledgeErrorResponse.getStatusCode())
                    .isEqualTo(STATUS_INTERNAL_ERROR);

            verify(mAdServicesLoggerMock)
                    .logFledgeApiCallStats(
                            eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_IMPRESSION),
                            eq(TEST_PACKAGE_NAME),
                            eq(STATUS_INTERNAL_ERROR),
                            anyInt());
        } finally {
            // Shut down any running JSScriptEngine to ensure the new singleton gets picked up
            JSScriptEngine.getInstance(CONTEXT, LoggerFactory.getFledgeLogger()).shutdown();
        }
    }

    @Test
    public void testAddOverrideAdSelectionFromOutcomesConfigRemoteInfoSuccess() throws Exception {
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        when(mDevContextFilterMock.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(TEST_PACKAGE_NAME)
                                .build());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        mFlags,
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

        AdSelectionFromOutcomesConfig config =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig();

        AdSelectionOverrideTestCallback callback =
                callAddOverrideForSelectAds(
                        adSelectionService,
                        config,
                        DUMMY_SELECTION_LOGIC_JS,
                        DUMMY_SELECTION_SIGNALS);

        assertTrue(callback.mIsSuccess);
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionFromOutcomesOverrideExistForPackageName(
                        AdSelectionDevOverridesHelper.calculateAdSelectionFromOutcomesConfigId(
                                config),
                        TEST_PACKAGE_NAME));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        anyInt());
    }

    @Test
    public void testOverrideAdSelectionFromOutcomesConfigRemoteInfoWithRevokedUserConsentSuccess()
            throws Exception {
        doReturn(AdServicesApiConsent.REVOKED)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(TEST_PACKAGE_NAME)
                                .build());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        mFlags,
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

        AdSelectionFromOutcomesConfig config =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig();

        AdSelectionOverrideTestCallback callback =
                callAddOverrideForSelectAds(
                        adSelectionService,
                        config,
                        DUMMY_SELECTION_LOGIC_JS,
                        DUMMY_SELECTION_SIGNALS);

        assertTrue(callback.mIsSuccess);
        assertFalse(
                mAdSelectionEntryDao.doesAdSelectionFromOutcomesOverrideExistForPackageName(
                        AdSelectionDevOverridesHelper.calculateAdSelectionFromOutcomesConfigId(
                                config),
                        TEST_PACKAGE_NAME));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_USER_CONSENT_REVOKED),
                        anyInt());
    }

    @Test
    public void testOverrideAdSelectionFromOutcomesConfigRemoteInfoFailsWithDevOptionsDisabled() {
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        when(mDevContextFilterMock.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        mFlags,
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

        AdSelectionFromOutcomesConfig config =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig();

        assertThrows(
                SecurityException.class,
                () ->
                        callAddOverrideForSelectAds(
                                adSelectionService,
                                config,
                                DUMMY_DECISION_LOGIC_JS,
                                DUMMY_TRUSTED_SCORING_SIGNALS));

        assertFalse(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        AdSelectionDevOverridesHelper.calculateAdSelectionFromOutcomesConfigId(
                                config),
                        TEST_PACKAGE_NAME));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN),
                        eq(null), // Actually logged as ""
                        eq(STATUS_INTERNAL_ERROR),
                        anyInt());
    }

    @Test
    public void testRemoveAdSelectionFromOutcomesConfigRemoteInfoOverrideSuccess()
            throws Exception {
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        when(mDevContextFilterMock.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(TEST_PACKAGE_NAME)
                                .build());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        mFlags,
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

        AdSelectionFromOutcomesConfig config =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig();

        String configId =
                AdSelectionDevOverridesHelper.calculateAdSelectionFromOutcomesConfigId(config);

        DBAdSelectionFromOutcomesOverride dbAdSelectionFromOutcomesOverride =
                DBAdSelectionFromOutcomesOverride.builder()
                        .setAdSelectionFromOutcomesConfigId(configId)
                        .setAppPackageName(TEST_PACKAGE_NAME)
                        .setSelectionLogicJs(DUMMY_SELECTION_LOGIC_JS)
                        .setSelectionSignals(DUMMY_SELECTION_SIGNALS.toString())
                        .build();

        mAdSelectionEntryDao.persistAdSelectionFromOutcomesOverride(
                dbAdSelectionFromOutcomesOverride);

        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionFromOutcomesOverrideExistForPackageName(
                        configId, TEST_PACKAGE_NAME));

        AdSelectionOverrideTestCallback callback = callRemoveOverride(adSelectionService, config);

        assertTrue(callback.mIsSuccess);
        assertFalse(
                mAdSelectionEntryDao.doesAdSelectionOverrideExistForPackageName(
                        configId, TEST_PACKAGE_NAME));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        anyInt());
    }

    @Test
    public void
            testRemoveAdSelectionFromOutcomesConfigRemoteInfoOverrideWithRevokedUserConsentSuccess()
                    throws Exception {
        doReturn(AdServicesApiConsent.REVOKED)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(TEST_PACKAGE_NAME)
                                .build());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        mFlags,
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

        AdSelectionFromOutcomesConfig config =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig();

        String adSelectionConfigId =
                AdSelectionDevOverridesHelper.calculateAdSelectionFromOutcomesConfigId(config);

        DBAdSelectionFromOutcomesOverride dbAdSelectionFromOutcomesOverride =
                DBAdSelectionFromOutcomesOverride.builder()
                        .setAdSelectionFromOutcomesConfigId(adSelectionConfigId)
                        .setAppPackageName(TEST_PACKAGE_NAME)
                        .setSelectionLogicJs(DUMMY_SELECTION_LOGIC_JS)
                        .setSelectionSignals(DUMMY_SELECTION_SIGNALS.toString())
                        .build();

        mAdSelectionEntryDao.persistAdSelectionFromOutcomesOverride(
                dbAdSelectionFromOutcomesOverride);

        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionFromOutcomesOverrideExistForPackageName(
                        adSelectionConfigId, TEST_PACKAGE_NAME));

        AdSelectionOverrideTestCallback callback = callRemoveOverride(adSelectionService, config);

        assertTrue(callback.mIsSuccess);
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionFromOutcomesOverrideExistForPackageName(
                        adSelectionConfigId, TEST_PACKAGE_NAME));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_USER_CONSENT_REVOKED),
                        anyInt());
    }

    @Test
    public void
            testRemoveAdSelectionFromOutcomesConfigRemoteInfoOverrideFailsWithDevOptionsDisabled() {
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        when(mDevContextFilterMock.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        mFlags,
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

        AdSelectionFromOutcomesConfig config =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig();

        String adSelectionConfigId =
                AdSelectionDevOverridesHelper.calculateAdSelectionFromOutcomesConfigId(config);

        DBAdSelectionFromOutcomesOverride dbAdSelectionFromOutcomesOverride =
                DBAdSelectionFromOutcomesOverride.builder()
                        .setAdSelectionFromOutcomesConfigId(adSelectionConfigId)
                        .setAppPackageName(TEST_PACKAGE_NAME)
                        .setSelectionLogicJs(DUMMY_SELECTION_LOGIC_JS)
                        .setSelectionSignals(DUMMY_SELECTION_SIGNALS.toString())
                        .build();

        mAdSelectionEntryDao.persistAdSelectionFromOutcomesOverride(
                dbAdSelectionFromOutcomesOverride);

        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionFromOutcomesOverrideExistForPackageName(
                        adSelectionConfigId, TEST_PACKAGE_NAME));

        assertThrows(SecurityException.class, () -> callRemoveOverride(adSelectionService, config));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionFromOutcomesOverrideExistForPackageName(
                        adSelectionConfigId, TEST_PACKAGE_NAME));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN),
                        eq(null), // Actually logged as ""
                        eq(STATUS_INTERNAL_ERROR),
                        anyInt());
    }

    @Test
    public void testRemoveAdSelectionFromOutcomesConfigRemoteOverrideNotDeleteIncorrectPackageName()
            throws Exception {
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        String incorrectPackageName = "com.google.ppapi.test.incorrect";

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(incorrectPackageName)
                                .build());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        mFlags,
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

        AdSelectionFromOutcomesConfig config =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig();

        String adSelectionConfigId =
                AdSelectionDevOverridesHelper.calculateAdSelectionFromOutcomesConfigId(config);

        DBAdSelectionFromOutcomesOverride dbAdSelectionFromOutcomesOverride =
                DBAdSelectionFromOutcomesOverride.builder()
                        .setAdSelectionFromOutcomesConfigId(adSelectionConfigId)
                        .setAppPackageName(TEST_PACKAGE_NAME)
                        .setSelectionLogicJs(DUMMY_SELECTION_LOGIC_JS)
                        .setSelectionSignals(DUMMY_SELECTION_SIGNALS.toString())
                        .build();

        mAdSelectionEntryDao.persistAdSelectionFromOutcomesOverride(
                dbAdSelectionFromOutcomesOverride);

        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionFromOutcomesOverrideExistForPackageName(
                        adSelectionConfigId, TEST_PACKAGE_NAME));

        AdSelectionOverrideTestCallback callback = callRemoveOverride(adSelectionService, config);

        assertTrue(callback.mIsSuccess);
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionFromOutcomesOverrideExistForPackageName(
                        adSelectionConfigId, TEST_PACKAGE_NAME));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN),
                        eq(incorrectPackageName),
                        eq(STATUS_SUCCESS),
                        anyInt());
    }

    @Test
    public void testResetAllAdSelectionFromOutcomesConfigRemoteOverridesSuccess() throws Exception {
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        when(mDevContextFilterMock.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(TEST_PACKAGE_NAME)
                                .build());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        mFlags,
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

        AdSelectionFromOutcomesConfig config1 =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig();
        AdSelectionFromOutcomesConfig config2 =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig(
                        AdTechIdentifier.fromString("adidas.com"),
                        Uri.parse("https://adidas.com/decisoin_logic_uri"));
        AdSelectionFromOutcomesConfig config3 =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig(
                        AdTechIdentifier.fromString("nike.com"),
                        Uri.parse("https://nike.com/decisoin_logic_uri"));

        String configId1 =
                AdSelectionDevOverridesHelper.calculateAdSelectionFromOutcomesConfigId(config1);
        String configId2 =
                AdSelectionDevOverridesHelper.calculateAdSelectionFromOutcomesConfigId(config2);
        String configId3 =
                AdSelectionDevOverridesHelper.calculateAdSelectionFromOutcomesConfigId(config3);

        DBAdSelectionFromOutcomesOverride dbAdSelectionFromOutcomesOverride1 =
                DBAdSelectionFromOutcomesOverride.builder()
                        .setAdSelectionFromOutcomesConfigId(configId1)
                        .setAppPackageName(TEST_PACKAGE_NAME)
                        .setSelectionLogicJs(DUMMY_SELECTION_LOGIC_JS)
                        .setSelectionSignals(DUMMY_SELECTION_SIGNALS.toString())
                        .build();

        DBAdSelectionFromOutcomesOverride dbAdSelectionFromOutcomesOverride2 =
                DBAdSelectionFromOutcomesOverride.builder()
                        .setAdSelectionFromOutcomesConfigId(configId2)
                        .setAppPackageName(TEST_PACKAGE_NAME)
                        .setSelectionLogicJs(DUMMY_SELECTION_LOGIC_JS)
                        .setSelectionSignals(DUMMY_SELECTION_SIGNALS.toString())
                        .build();

        DBAdSelectionFromOutcomesOverride dbAdSelectionFromOutcomesOverride3 =
                DBAdSelectionFromOutcomesOverride.builder()
                        .setAdSelectionFromOutcomesConfigId(configId3)
                        .setAppPackageName(TEST_PACKAGE_NAME)
                        .setSelectionLogicJs(DUMMY_SELECTION_LOGIC_JS)
                        .setSelectionSignals(DUMMY_SELECTION_SIGNALS.toString())
                        .build();

        mAdSelectionEntryDao.persistAdSelectionFromOutcomesOverride(
                dbAdSelectionFromOutcomesOverride1);
        mAdSelectionEntryDao.persistAdSelectionFromOutcomesOverride(
                dbAdSelectionFromOutcomesOverride2);
        mAdSelectionEntryDao.persistAdSelectionFromOutcomesOverride(
                dbAdSelectionFromOutcomesOverride3);

        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionFromOutcomesOverrideExistForPackageName(
                        configId1, TEST_PACKAGE_NAME));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionFromOutcomesOverrideExistForPackageName(
                        configId2, TEST_PACKAGE_NAME));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionFromOutcomesOverrideExistForPackageName(
                        configId3, TEST_PACKAGE_NAME));

        AdSelectionOverrideTestCallback callback =
                callResetAllSelectionOutcomesOverrides(adSelectionService);

        assertTrue(callback.mIsSuccess);

        assertFalse(
                mAdSelectionEntryDao.doesAdSelectionFromOutcomesOverrideExistForPackageName(
                        configId1, TEST_PACKAGE_NAME));
        assertFalse(
                mAdSelectionEntryDao.doesAdSelectionFromOutcomesOverrideExistForPackageName(
                        configId2, TEST_PACKAGE_NAME));
        assertFalse(
                mAdSelectionEntryDao.doesAdSelectionFromOutcomesOverrideExistForPackageName(
                        configId3, TEST_PACKAGE_NAME));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        anyInt());
    }

    @Test
    public void
            testResetAllAdSelectionFromOutcomesConfigRemoteOverridesWithRevokedUserConsentSuccess()
                    throws Exception {
        doReturn(AdServicesApiConsent.REVOKED)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(TEST_PACKAGE_NAME)
                                .build());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        mFlags,
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

        AdSelectionFromOutcomesConfig config1 =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig();
        AdSelectionFromOutcomesConfig config2 =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig(
                        AdTechIdentifier.fromString("adidas.com"),
                        Uri.parse("https://adidas.com/decisoin_logic_uri"));
        AdSelectionFromOutcomesConfig config3 =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig(
                        AdTechIdentifier.fromString("nike.com"),
                        Uri.parse("https://nike.com/decisoin_logic_uri"));

        String configId1 =
                AdSelectionDevOverridesHelper.calculateAdSelectionFromOutcomesConfigId(config1);
        String configId2 =
                AdSelectionDevOverridesHelper.calculateAdSelectionFromOutcomesConfigId(config2);
        String configId3 =
                AdSelectionDevOverridesHelper.calculateAdSelectionFromOutcomesConfigId(config3);

        DBAdSelectionFromOutcomesOverride dbAdSelectionFromOutcomesOverride1 =
                DBAdSelectionFromOutcomesOverride.builder()
                        .setAdSelectionFromOutcomesConfigId(configId1)
                        .setAppPackageName(TEST_PACKAGE_NAME)
                        .setSelectionLogicJs(DUMMY_SELECTION_LOGIC_JS)
                        .setSelectionSignals(DUMMY_SELECTION_SIGNALS.toString())
                        .build();

        DBAdSelectionFromOutcomesOverride dbAdSelectionFromOutcomesOverride2 =
                DBAdSelectionFromOutcomesOverride.builder()
                        .setAdSelectionFromOutcomesConfigId(configId2)
                        .setAppPackageName(TEST_PACKAGE_NAME)
                        .setSelectionLogicJs(DUMMY_SELECTION_LOGIC_JS)
                        .setSelectionSignals(DUMMY_SELECTION_SIGNALS.toString())
                        .build();

        DBAdSelectionFromOutcomesOverride dbAdSelectionFromOutcomesOverride3 =
                DBAdSelectionFromOutcomesOverride.builder()
                        .setAdSelectionFromOutcomesConfigId(configId3)
                        .setAppPackageName(TEST_PACKAGE_NAME)
                        .setSelectionLogicJs(DUMMY_SELECTION_LOGIC_JS)
                        .setSelectionSignals(DUMMY_SELECTION_SIGNALS.toString())
                        .build();

        mAdSelectionEntryDao.persistAdSelectionFromOutcomesOverride(
                dbAdSelectionFromOutcomesOverride1);
        mAdSelectionEntryDao.persistAdSelectionFromOutcomesOverride(
                dbAdSelectionFromOutcomesOverride2);
        mAdSelectionEntryDao.persistAdSelectionFromOutcomesOverride(
                dbAdSelectionFromOutcomesOverride3);

        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionFromOutcomesOverrideExistForPackageName(
                        configId1, TEST_PACKAGE_NAME));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionFromOutcomesOverrideExistForPackageName(
                        configId2, TEST_PACKAGE_NAME));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionFromOutcomesOverrideExistForPackageName(
                        configId3, TEST_PACKAGE_NAME));

        AdSelectionOverrideTestCallback callback =
                callResetAllSelectionOutcomesOverrides(adSelectionService);

        assertTrue(callback.mIsSuccess);

        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionFromOutcomesOverrideExistForPackageName(
                        configId1, TEST_PACKAGE_NAME));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionFromOutcomesOverrideExistForPackageName(
                        configId2, TEST_PACKAGE_NAME));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionFromOutcomesOverrideExistForPackageName(
                        configId3, TEST_PACKAGE_NAME));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_USER_CONSENT_REVOKED),
                        anyInt());
    }

    @Test
    public void
            testResetAllAdSelectionFromOutcomesConfigRemoteOverridesFailsWithDevOptionsDisabled() {
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        when(mDevContextFilterMock.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        mFlags,
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

        AdSelectionFromOutcomesConfig config1 =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig();
        AdSelectionFromOutcomesConfig config2 =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig(
                        AdTechIdentifier.fromString("adidas.com"),
                        Uri.parse("https://adidas.com/decisoin_logic_uri"));
        AdSelectionFromOutcomesConfig config3 =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig(
                        AdTechIdentifier.fromString("nike.com"),
                        Uri.parse("https://nike.com/decisoin_logic_uri"));

        String configId1 =
                AdSelectionDevOverridesHelper.calculateAdSelectionFromOutcomesConfigId(config1);
        String configId2 =
                AdSelectionDevOverridesHelper.calculateAdSelectionFromOutcomesConfigId(config2);
        String configId3 =
                AdSelectionDevOverridesHelper.calculateAdSelectionFromOutcomesConfigId(config3);

        DBAdSelectionFromOutcomesOverride dbAdSelectionFromOutcomesOverride1 =
                DBAdSelectionFromOutcomesOverride.builder()
                        .setAdSelectionFromOutcomesConfigId(configId1)
                        .setAppPackageName(TEST_PACKAGE_NAME)
                        .setSelectionLogicJs(DUMMY_SELECTION_LOGIC_JS)
                        .setSelectionSignals(DUMMY_SELECTION_SIGNALS.toString())
                        .build();

        DBAdSelectionFromOutcomesOverride dbAdSelectionFromOutcomesOverride2 =
                DBAdSelectionFromOutcomesOverride.builder()
                        .setAdSelectionFromOutcomesConfigId(configId2)
                        .setAppPackageName(TEST_PACKAGE_NAME)
                        .setSelectionLogicJs(DUMMY_SELECTION_LOGIC_JS)
                        .setSelectionSignals(DUMMY_SELECTION_SIGNALS.toString())
                        .build();

        DBAdSelectionFromOutcomesOverride dbAdSelectionFromOutcomesOverride3 =
                DBAdSelectionFromOutcomesOverride.builder()
                        .setAdSelectionFromOutcomesConfigId(configId3)
                        .setAppPackageName(TEST_PACKAGE_NAME)
                        .setSelectionLogicJs(DUMMY_SELECTION_LOGIC_JS)
                        .setSelectionSignals(DUMMY_SELECTION_SIGNALS.toString())
                        .build();

        mAdSelectionEntryDao.persistAdSelectionFromOutcomesOverride(
                dbAdSelectionFromOutcomesOverride1);
        mAdSelectionEntryDao.persistAdSelectionFromOutcomesOverride(
                dbAdSelectionFromOutcomesOverride2);
        mAdSelectionEntryDao.persistAdSelectionFromOutcomesOverride(
                dbAdSelectionFromOutcomesOverride3);

        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionFromOutcomesOverrideExistForPackageName(
                        configId1, TEST_PACKAGE_NAME));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionFromOutcomesOverrideExistForPackageName(
                        configId2, TEST_PACKAGE_NAME));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionFromOutcomesOverrideExistForPackageName(
                        configId3, TEST_PACKAGE_NAME));

        assertThrows(
                SecurityException.class,
                () -> callResetAllSelectionOutcomesOverrides(adSelectionService));

        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionFromOutcomesOverrideExistForPackageName(
                        configId1, TEST_PACKAGE_NAME));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionFromOutcomesOverrideExistForPackageName(
                        configId2, TEST_PACKAGE_NAME));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionFromOutcomesOverrideExistForPackageName(
                        configId3, TEST_PACKAGE_NAME));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN),
                        eq(null), // Actually logged as ""
                        eq(STATUS_INTERNAL_ERROR),
                        anyInt());
    }

    @Test
    public void testResetAllAdSelectionFromOutcomesConfigRemoteOverrideNotDeleteIncorrectPkgName()
            throws Exception {
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        String incorrectPackageName = "com.google.ppapi.test.incorrect";

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(incorrectPackageName)
                                .build());

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        mFlags,
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

        AdSelectionFromOutcomesConfig config1 =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig();
        AdSelectionFromOutcomesConfig config2 =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig(
                        AdTechIdentifier.fromString("adidas.com"),
                        Uri.parse("https://adidas.com/decisoin_logic_uri"));
        AdSelectionFromOutcomesConfig config3 =
                AdSelectionFromOutcomesConfigFixture.anAdSelectionFromOutcomesConfig(
                        AdTechIdentifier.fromString("nike.com"),
                        Uri.parse("https://nike.com/decisoin_logic_uri"));

        String configId1 =
                AdSelectionDevOverridesHelper.calculateAdSelectionFromOutcomesConfigId(config1);
        String configId2 =
                AdSelectionDevOverridesHelper.calculateAdSelectionFromOutcomesConfigId(config2);
        String configId3 =
                AdSelectionDevOverridesHelper.calculateAdSelectionFromOutcomesConfigId(config3);

        DBAdSelectionFromOutcomesOverride dbAdSelectionFromOutcomesOverride1 =
                DBAdSelectionFromOutcomesOverride.builder()
                        .setAdSelectionFromOutcomesConfigId(configId1)
                        .setAppPackageName(TEST_PACKAGE_NAME)
                        .setSelectionLogicJs(DUMMY_SELECTION_LOGIC_JS)
                        .setSelectionSignals(DUMMY_SELECTION_SIGNALS.toString())
                        .build();

        DBAdSelectionFromOutcomesOverride dbAdSelectionFromOutcomesOverride2 =
                DBAdSelectionFromOutcomesOverride.builder()
                        .setAdSelectionFromOutcomesConfigId(configId2)
                        .setAppPackageName(TEST_PACKAGE_NAME)
                        .setSelectionLogicJs(DUMMY_SELECTION_LOGIC_JS)
                        .setSelectionSignals(DUMMY_SELECTION_SIGNALS.toString())
                        .build();

        DBAdSelectionFromOutcomesOverride dbAdSelectionFromOutcomesOverride3 =
                DBAdSelectionFromOutcomesOverride.builder()
                        .setAdSelectionFromOutcomesConfigId(configId3)
                        .setAppPackageName(TEST_PACKAGE_NAME)
                        .setSelectionLogicJs(DUMMY_SELECTION_LOGIC_JS)
                        .setSelectionSignals(DUMMY_SELECTION_SIGNALS.toString())
                        .build();

        mAdSelectionEntryDao.persistAdSelectionFromOutcomesOverride(
                dbAdSelectionFromOutcomesOverride1);
        mAdSelectionEntryDao.persistAdSelectionFromOutcomesOverride(
                dbAdSelectionFromOutcomesOverride2);
        mAdSelectionEntryDao.persistAdSelectionFromOutcomesOverride(
                dbAdSelectionFromOutcomesOverride3);

        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionFromOutcomesOverrideExistForPackageName(
                        configId1, TEST_PACKAGE_NAME));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionFromOutcomesOverrideExistForPackageName(
                        configId2, TEST_PACKAGE_NAME));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionFromOutcomesOverrideExistForPackageName(
                        configId3, TEST_PACKAGE_NAME));

        AdSelectionOverrideTestCallback callback =
                callResetAllSelectionOutcomesOverrides(adSelectionService);

        assertTrue(callback.mIsSuccess);

        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionFromOutcomesOverrideExistForPackageName(
                        configId1, TEST_PACKAGE_NAME));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionFromOutcomesOverrideExistForPackageName(
                        configId2, TEST_PACKAGE_NAME));
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionFromOutcomesOverrideExistForPackageName(
                        configId3, TEST_PACKAGE_NAME));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN),
                        eq(incorrectPackageName),
                        eq(STATUS_SUCCESS),
                        anyInt());
    }

    @Test
    public void testOverrideAdSelectionConfigRemoteOverridesSuccess() throws Exception {
        Assume.assumeTrue(JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());
        doReturn(AdServicesApiConsent.GIVEN)
                .when(mConsentManagerMock)
                .getConsent(AdServicesApiType.FLEDGE);
        when(mDevContextFilterMock.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(TEST_PACKAGE_NAME)
                                .build());

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
                        Uri.parse("https://this.uri.isnt/called"));

        final String selectionPickSmallestAdSelectionIdLogicJs =
                "function selectOutcome(outcomes, selection_signals) {\n"
                        + "    outcomes.sort(function(a, b) { return a.id - b.id;});\n"
                        + "    return {'status': 0, 'result': outcomes[0]};\n"
                        + "}";

        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDao,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        mFlags,
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

        AdSelectionOverrideTestCallback overridesCallback =
                callAddOverrideForSelectAds(
                        adSelectionService,
                        config,
                        selectionPickSmallestAdSelectionIdLogicJs,
                        AdSelectionSignals.EMPTY);

        assertTrue(overridesCallback.mIsSuccess);
        assertTrue(
                mAdSelectionEntryDao.doesAdSelectionFromOutcomesOverrideExistForPackageName(
                        AdSelectionDevOverridesHelper.calculateAdSelectionFromOutcomesConfigId(
                                config),
                        TEST_PACKAGE_NAME));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        anyInt());

        AdSelectionFromOutcomesTestCallback selectionCallback =
                invokeSelectAdsFromOutcomes(adSelectionService, config, TEST_PACKAGE_NAME);

        assertTrue(selectionCallback.mIsSuccess);
        assertEquals(AD_SELECTION_ID_1, selectionCallback.mAdSelectionResponse.getAdSelectionId());
    }

    @Test
    public void testSetAppInstallAdvertisersSuccess() throws Exception {
        SetAppInstallAdvertisersInput input =
                new SetAppInstallAdvertisersInput.Builder()
                        .setAdvertisers(
                                new HashSet<>(
                                        Arrays.asList(
                                                AdTechIdentifier.fromString("example1.com"),
                                                AdTechIdentifier.fromString("example2.com"))))
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();
        SetAppInstallAdvertisersTestCallback callback =
                callSetAppInstallAdvertisers(generateAdSelectionServiceImpl(), input);
        assertTrue(callback.mIsSuccess);
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SET_APP_INSTALL_ADVERTISERS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        anyInt());
    }

    @Test
    public void testSetAppInstallAdvertisersNullInput() {
        assertThrows(
                NullPointerException.class,
                () -> callSetAppInstallAdvertisers(generateAdSelectionServiceImpl(), null));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SET_APP_INSTALL_ADVERTISERS),
                        eq(STATUS_INVALID_ARGUMENT),
                        eq(0));
    }

    @Test
    public void testUpdateAdCounterHistogramNullInputThrows() {
        assertThrows(
                NullPointerException.class,
                () -> callUpdateAdCounterHistogram(generateAdSelectionServiceImpl(), null));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__UPDATE_AD_COUNTER_HISTOGRAM),
                        eq(STATUS_INVALID_ARGUMENT),
                        eq(0));
    }

    @Test
    public void testUpdateAdCounterHistogramNullCallbackThrows() throws InterruptedException {
        AdSelectionServiceImpl adSelectionService = generateAdSelectionServiceImpl();
        UpdateAdCounterHistogramInput inputParams =
                new UpdateAdCounterHistogramInput.Builder(
                                10,
                                FrequencyCapFilters.AD_EVENT_TYPE_VIEW,
                                CommonFixture.VALID_BUYER_1,
                                TEST_PACKAGE_NAME)
                        .build();

        // Wait for the logging call, which happens after the callback
        CountDownLatch resultLatch = new CountDownLatch(1);
        Answer<Void> countDownAnswer =
                unused -> {
                    resultLatch.countDown();
                    return null;
                };
        doAnswer(countDownAnswer)
                .when(mAdServicesLoggerMock)
                .logFledgeApiCallStats(anyInt(), anyInt(), anyInt());

        assertThrows(
                NullPointerException.class,
                () -> adSelectionService.updateAdCounterHistogram(inputParams, null));
        assertTrue(
                "Timed out waiting for updateAdCounterHistogram call to complete",
                resultLatch.await(5, TimeUnit.SECONDS));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__UPDATE_AD_COUNTER_HISTOGRAM),
                        eq(STATUS_INVALID_ARGUMENT),
                        eq(0));
    }

    @Test
    public void testReportEvent_nullInput_throws() {
        assertThrows(
                NullPointerException.class,
                () -> callReportInteraction(generateAdSelectionServiceImpl(), null));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION),
                        eq(STATUS_INVALID_ARGUMENT),
                        eq(0));
    }

    @Test
    public void testReportEvent_nullCallback_throws() throws InterruptedException {
        AdSelectionServiceImpl adSelectionService = generateAdSelectionServiceImpl();

        ReportInteractionInput inputParams =
                new ReportInteractionInput.Builder()
                        .setAdSelectionId(10)
                        .setInteractionData(INTERACTION_DATA)
                        .setInteractionKey(CLICK_EVENT_BUYER)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .setReportingDestinations(FLAG_REPORTING_DESTINATION_BUYER)
                        .build();

        // Wait for the logging call, which happens after the callback
        CountDownLatch resultLatch = new CountDownLatch(1);
        Answer<Void> countDownAnswer =
                unused -> {
                    resultLatch.countDown();
                    return null;
                };
        doAnswer(countDownAnswer)
                .when(mAdServicesLoggerMock)
                .logFledgeApiCallStats(anyInt(), anyInt(), anyInt());

        assertThrows(
                NullPointerException.class,
                () -> adSelectionService.reportInteraction(inputParams, null));
        assertTrue(
                "Timed out waiting for reportInteraction call to complete",
                resultLatch.await(5, TimeUnit.SECONDS));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION),
                        eq(STATUS_INVALID_ARGUMENT),
                        eq(0));
    }

    // TODO(b/271652362): Investigate logging and testing of failures during callback reporting
    @Ignore("b/271652362")
    @Test
    public void testReportEvent_callbackErrorReported() throws Exception {
        doReturn(mMeasurementServiceMock).when(() -> MeasurementImpl.getInstance(any()));

        Uri biddingLogicUri = (mMockWebServerRule.uriForPath(mFetchJavaScriptPathBuyer));

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(mCustomAudienceSignals)
                        .setBuyerContextualSignals(mContextualSignals.toString())
                        .setBiddingLogicUri(biddingLogicUri)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);

        AdSelectionServiceImpl adSelectionService = generateAdSelectionServiceImpl();

        ReportInteractionInput inputParams =
                new ReportInteractionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setInteractionData(INTERACTION_DATA)
                        .setInteractionKey(CLICK_EVENT_BUYER)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .setReportingDestinations(FLAG_REPORTING_DESTINATION_BUYER)
                        .build();

        // Counted down in 1) callback and 2) logApiCall
        CountDownLatch resultLatch = new CountDownLatch(2);
        ReportInteractionTestErrorCallback callback =
                new ReportInteractionTestErrorCallback(resultLatch);

        // Wait for the logging call, which happens after the callback
        Answer<Void> countDownAnswer =
                unused -> {
                    resultLatch.countDown();
                    return null;
                };
        doAnswer(countDownAnswer)
                .when(mAdServicesLoggerMock)
                .logFledgeApiCallStats(anyInt(), anyString(), anyInt(), anyInt());

        adSelectionService.reportInteraction(inputParams, callback);
        assertTrue(
                "Timed out waiting for reportInteraction call to complete",
                resultLatch.await(5, TimeUnit.SECONDS));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_INTERNAL_ERROR),
                        eq(0));
    }

    @Test
    public void testReportEvent_disabled_failsFast() throws Exception {
        doReturn(mMeasurementServiceMock).when(() -> MeasurementImpl.getInstance(any()));

        // Generate service instance with feature disabled.
        mFlags =
                new AdSelectionServicesTestsFlags(false) {
                    @Override
                    public boolean getFledgeRegisterAdBeaconEnabled() {
                        return false;
                    }
                };
        AdSelectionServiceImpl adSelectionService = generateAdSelectionServiceImpl();

        // Call disabled feature.
        ReportInteractionInput input =
                new ReportInteractionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setInteractionData(INTERACTION_DATA)
                        .setInteractionKey(CLICK_EVENT_BUYER)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .setReportingDestinations(FLAG_REPORTING_DESTINATION_BUYER)
                        .build();
        ReportInteractionTestCallback callback = callReportInteraction(adSelectionService, input);

        // Assert call failed since the feature is disabled.
        assertFalse("reportInteraction() callback was unsuccessful", callback.mIsSuccess);
        assertEquals(STATUS_INTERNAL_ERROR, callback.mFledgeErrorResponse.getStatusCode());
        assertEquals(API_DISABLED_MESSAGE, callback.mFledgeErrorResponse.getErrorMessage());
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_INTERNAL_ERROR),
                        eq(0));
    }

    @Test
    public void testReportEvent_onlyReport_success() throws Exception {
        Uri biddingLogicUri = (mMockWebServerRule.uriForPath(mFetchJavaScriptPathBuyer));

        DBAdSelection dbAdSelection =
                new DBAdSelection.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setCustomAudienceSignals(mCustomAudienceSignals)
                        .setBuyerContextualSignals(mContextualSignals.toString())
                        .setBiddingLogicUri(biddingLogicUri)
                        .setWinningAdRenderUri(RENDER_URI)
                        .setWinningAdBid(BID)
                        .setCreationTimestamp(ACTIVATION_TIME)
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        mAdSelectionEntryDao.persistAdSelection(dbAdSelection);

        ReportInteractionInput inputParams =
                new ReportInteractionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setInteractionData(INTERACTION_DATA)
                        .setInteractionKey(CLICK_EVENT_BUYER)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .setReportingDestinations(FLAG_REPORTING_DESTINATION_BUYER)
                        .build();

        doReturn(mMeasurementServiceMock).when(() -> MeasurementImpl.getInstance(any()));

        // Count down callback + log interaction.
        ReportInteractionTestCallback callback =
                callReportInteraction(generateAdSelectionServiceImpl(), inputParams, true);
        assertTrue("reportInteraction() callback was unsuccessful", callback.mIsSuccess);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__REPORT_INTERACTION),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        eq(0));
    }

    @Test
    public void testSetAdCounterHistogramOverrideNullInputThrows() {
        assertThrows(
                NullPointerException.class,
                () -> callSetAdCounterHistogramOverride(generateAdSelectionServiceImpl(), null));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN),
                        eq(STATUS_INVALID_ARGUMENT),
                        eq(0));
    }

    @Test
    public void testSetAdCounterHistogramOverrideNullCallbackThrows() throws InterruptedException {
        AdSelectionServiceImpl adSelectionService = generateAdSelectionServiceImpl();
        SetAdCounterHistogramOverrideInput inputParams =
                new SetAdCounterHistogramOverrideInput.Builder()
                        .setAdEventType(FrequencyCapFilters.AD_EVENT_TYPE_CLICK)
                        .setAdCounterKey(KeyedFrequencyCapFixture.KEY1)
                        .setBuyer(CommonFixture.VALID_BUYER_1)
                        .setCustomAudienceOwner(CommonFixture.TEST_PACKAGE_NAME)
                        .setCustomAudienceName("testName")
                        .build();

        // Wait for the logging call, which happens after the callback
        CountDownLatch resultLatch = new CountDownLatch(1);
        Answer<Void> countDownAnswer =
                unused -> {
                    resultLatch.countDown();
                    return null;
                };
        doAnswer(countDownAnswer)
                .when(mAdServicesLoggerMock)
                .logFledgeApiCallStats(anyInt(), anyInt(), anyInt());

        assertThrows(
                NullPointerException.class,
                () -> adSelectionService.setAdCounterHistogramOverride(inputParams, null));
        assertTrue(
                "Timed out waiting for setAdCounterHistogramOverride call to complete",
                resultLatch.await(5, TimeUnit.SECONDS));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN),
                        eq(STATUS_INVALID_ARGUMENT),
                        eq(0));
    }

    @Test
    public void testSetAdCounterHistogramOverrideCallbackErrorReported()
            throws InterruptedException {
        when(mDevContextFilterMock.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(TEST_PACKAGE_NAME)
                                .build());
        AdSelectionServiceImpl adSelectionService = generateAdSelectionServiceImpl();
        SetAdCounterHistogramOverrideInput inputParams =
                new SetAdCounterHistogramOverrideInput.Builder()
                        .setAdEventType(FrequencyCapFilters.AD_EVENT_TYPE_CLICK)
                        .setAdCounterKey(KeyedFrequencyCapFixture.KEY1)
                        .setBuyer(CommonFixture.VALID_BUYER_1)
                        .setCustomAudienceOwner(CommonFixture.TEST_PACKAGE_NAME)
                        .setCustomAudienceName("testName")
                        .build();

        // Counted down in 1) callback and 2) logApiCall
        CountDownLatch resultLatch = new CountDownLatch(2);
        AdSelectionOverrideTestCallback callback =
                new AdSelectionOverrideTestErrorCallback(resultLatch);

        // Wait for the logging call, which happens after the callback
        Answer<Void> countDownAnswer =
                unused -> {
                    resultLatch.countDown();
                    return null;
                };
        doAnswer(countDownAnswer)
                .when(mAdServicesLoggerMock)
                .logFledgeApiCallStats(anyInt(), anyString(), anyInt(), anyInt());

        adSelectionService.setAdCounterHistogramOverride(inputParams, callback);
        assertTrue(
                "Timed out waiting for setAdCounterHistogramOverride call to complete",
                resultLatch.await(5, TimeUnit.SECONDS));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_INTERNAL_ERROR),
                        eq(0));
    }

    @Test
    public void testSetAdCounterHistogramOverrideSuccess() throws InterruptedException {
        when(mDevContextFilterMock.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(TEST_PACKAGE_NAME)
                                .build());
        SetAdCounterHistogramOverrideInput inputParams =
                new SetAdCounterHistogramOverrideInput.Builder()
                        .setAdEventType(FrequencyCapFilters.AD_EVENT_TYPE_CLICK)
                        .setAdCounterKey(KeyedFrequencyCapFixture.KEY1)
                        .setBuyer(CommonFixture.VALID_BUYER_1)
                        .setCustomAudienceOwner(CommonFixture.TEST_PACKAGE_NAME)
                        .setCustomAudienceName("testName")
                        .build();

        AdSelectionOverrideTestCallback callback =
                callSetAdCounterHistogramOverride(generateAdSelectionServiceImpl(), inputParams);
        assertTrue(
                "setAdCounterHistogramOverride() callback should have been successful",
                callback.mIsSuccess);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        anyInt());
    }

    @Test
    public void testRemoveAdCounterHistogramOverrideNullInputThrows() {
        assertThrows(
                NullPointerException.class,
                () -> callRemoveAdCounterHistogramOverride(generateAdSelectionServiceImpl(), null));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN),
                        eq(STATUS_INVALID_ARGUMENT),
                        eq(0));
    }

    @Test
    public void testRemoveAdCounterHistogramOverrideNullCallbackThrows()
            throws InterruptedException {
        AdSelectionServiceImpl adSelectionService = generateAdSelectionServiceImpl();
        RemoveAdCounterHistogramOverrideInput inputParams =
                new RemoveAdCounterHistogramOverrideInput.Builder()
                        .setAdEventType(FrequencyCapFilters.AD_EVENT_TYPE_CLICK)
                        .setAdCounterKey(KeyedFrequencyCapFixture.KEY1)
                        .setBuyer(CommonFixture.VALID_BUYER_1)
                        .build();

        // Wait for the logging call, which happens after the callback
        CountDownLatch resultLatch = new CountDownLatch(1);
        Answer<Void> countDownAnswer =
                unused -> {
                    resultLatch.countDown();
                    return null;
                };
        doAnswer(countDownAnswer)
                .when(mAdServicesLoggerMock)
                .logFledgeApiCallStats(anyInt(), anyInt(), anyInt());

        assertThrows(
                NullPointerException.class,
                () -> adSelectionService.removeAdCounterHistogramOverride(inputParams, null));
        assertTrue(
                "Timed out waiting for removeAdCounterHistogramOverride call to complete",
                resultLatch.await(5, TimeUnit.SECONDS));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN),
                        eq(STATUS_INVALID_ARGUMENT),
                        eq(0));
    }

    @Test
    public void testRemoveAdCounterHistogramOverrideCallbackErrorReported()
            throws InterruptedException {
        when(mDevContextFilterMock.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(TEST_PACKAGE_NAME)
                                .build());
        AdSelectionServiceImpl adSelectionService = generateAdSelectionServiceImpl();
        RemoveAdCounterHistogramOverrideInput inputParams =
                new RemoveAdCounterHistogramOverrideInput.Builder()
                        .setAdEventType(FrequencyCapFilters.AD_EVENT_TYPE_CLICK)
                        .setAdCounterKey(KeyedFrequencyCapFixture.KEY1)
                        .setBuyer(CommonFixture.VALID_BUYER_1)
                        .build();

        // Counted down in 1) callback and 2) logApiCall
        CountDownLatch resultLatch = new CountDownLatch(2);
        AdSelectionOverrideTestCallback callback =
                new AdSelectionOverrideTestErrorCallback(resultLatch);

        // Wait for the logging call, which happens after the callback
        Answer<Void> countDownAnswer =
                unused -> {
                    resultLatch.countDown();
                    return null;
                };
        doAnswer(countDownAnswer)
                .when(mAdServicesLoggerMock)
                .logFledgeApiCallStats(anyInt(), anyString(), anyInt(), anyInt());

        adSelectionService.removeAdCounterHistogramOverride(inputParams, callback);
        assertTrue(
                "Timed out waiting for removeAdCounterHistogramOverride call to complete",
                resultLatch.await(5, TimeUnit.SECONDS));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_INTERNAL_ERROR),
                        eq(0));
    }

    @Test
    public void testRemoveAdCounterHistogramOverrideSuccess() throws InterruptedException {
        when(mDevContextFilterMock.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(TEST_PACKAGE_NAME)
                                .build());
        RemoveAdCounterHistogramOverrideInput inputParams =
                new RemoveAdCounterHistogramOverrideInput.Builder()
                        .setAdEventType(FrequencyCapFilters.AD_EVENT_TYPE_CLICK)
                        .setAdCounterKey(KeyedFrequencyCapFixture.KEY1)
                        .setBuyer(CommonFixture.VALID_BUYER_1)
                        .build();

        AdSelectionOverrideTestCallback callback =
                callRemoveAdCounterHistogramOverride(generateAdSelectionServiceImpl(), inputParams);
        assertTrue(
                "removeAdCounterHistogramOverride() callback should have been successful",
                callback.mIsSuccess);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        anyInt());
    }

    @Test
    public void testResetAllAdCounterHistogramOverridesNullCallbackThrows()
            throws InterruptedException {
        AdSelectionServiceImpl adSelectionService = generateAdSelectionServiceImpl();

        // Wait for the logging call, which happens after the callback
        CountDownLatch resultLatch = new CountDownLatch(1);
        Answer<Void> countDownAnswer =
                unused -> {
                    resultLatch.countDown();
                    return null;
                };
        doAnswer(countDownAnswer)
                .when(mAdServicesLoggerMock)
                .logFledgeApiCallStats(anyInt(), anyInt(), anyInt());

        assertThrows(
                NullPointerException.class,
                () -> adSelectionService.resetAllAdCounterHistogramOverrides(null));
        assertTrue(
                "Timed out waiting for resetAllAdCounterHistogramOverrides call to complete",
                resultLatch.await(5, TimeUnit.SECONDS));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN),
                        eq(STATUS_INVALID_ARGUMENT),
                        eq(0));
    }

    @Test
    public void testResetAllAdCounterHistogramOverridesCallbackErrorReported()
            throws InterruptedException {
        when(mDevContextFilterMock.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(TEST_PACKAGE_NAME)
                                .build());
        AdSelectionServiceImpl adSelectionService = generateAdSelectionServiceImpl();

        // Counted down in 1) callback and 2) logApiCall
        CountDownLatch resultLatch = new CountDownLatch(2);
        AdSelectionOverrideTestCallback callback =
                new AdSelectionOverrideTestErrorCallback(resultLatch);

        // Wait for the logging call, which happens after the callback
        Answer<Void> countDownAnswer =
                unused -> {
                    resultLatch.countDown();
                    return null;
                };
        doAnswer(countDownAnswer)
                .when(mAdServicesLoggerMock)
                .logFledgeApiCallStats(anyInt(), anyString(), anyInt(), anyInt());

        adSelectionService.resetAllAdCounterHistogramOverrides(callback);
        assertTrue(
                "Timed out waiting for resetAllAdCounterHistogramOverrides call to complete",
                resultLatch.await(5, TimeUnit.SECONDS));

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_INTERNAL_ERROR),
                        eq(0));
    }

    @Test
    public void testResetAllAdCounterHistogramOverridesSuccess() throws InterruptedException {
        when(mDevContextFilterMock.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(TEST_PACKAGE_NAME)
                                .build());
        AdSelectionOverrideTestCallback callback =
                callResetAllAdCounterHistogramOverrides(generateAdSelectionServiceImpl());
        assertTrue(
                "resetAllAdCounterHistogramOverrides() callback should have been successful",
                callback.mIsSuccess);

        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__API_NAME_UNKNOWN),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        anyInt());
    }

    @Test
    public void testReportImpressionSuccess_callsServerAuctionForImpressionReporterIsOff()
            throws Exception {
        Assume.assumeTrue(JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());

        boolean enrollmentCheck = false;
        Flags unifiedFlowReportingDisabled =
                new AdSelectionServicesTestsFlags(enrollmentCheck) {
                    @Override
                    public boolean getFledgeAuctionServerEnabledForReportImpression() {
                        return false;
                    }
                };

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());
        AdSelectionEntryDao adSelectionEntryDaoSpy = spy(mAdSelectionEntryDao);
        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        adSelectionEntryDaoSpy,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        unifiedFlowReportingDisabled,
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

        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();

        // Count down callback + log interaction.
        callReportImpression(adSelectionService, input, true);

        verify(adSelectionEntryDaoSpy, times(1)).doesAdSelectionIdExist(AD_SELECTION_ID);
        verify(adSelectionEntryDaoSpy, times(0))
                .doesAdSelectionMatchingCallerPackageNameExistInOnDeviceTable(
                        AD_SELECTION_ID, TEST_PACKAGE_NAME);
    }

    @Test
    public void testReportImpressionSuccess_callsServerAuctionForImpressionReporterIsOn()
            throws Exception {
        Assume.assumeTrue(JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());

        boolean enrollmentCheck = false;
        Flags unifiedFlowReportingEnabled =
                new AdSelectionServicesTestsFlags(enrollmentCheck) {
                    @Override
                    public boolean getFledgeAuctionServerEnabledForReportImpression() {
                        return true;
                    }
                };

        AdSelectionConfig adSelectionConfig = mAdSelectionConfigBuilder.build();

        when(mDevContextFilterMock.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());
        AdSelectionEntryDao adSelectionEntryDaoSpy = spy(mAdSelectionEntryDao);
        AdSelectionServiceImpl adSelectionService =
                new AdSelectionServiceImpl(
                        adSelectionEntryDaoSpy,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        mClientSpy,
                        mDevContextFilterMock,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        CONTEXT,
                        mAdServicesLoggerMock,
                        unifiedFlowReportingEnabled,
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

        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(AD_SELECTION_ID)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(TEST_PACKAGE_NAME)
                        .build();

        // Count down callback + log interaction.
        callReportImpression(adSelectionService, input, true);

        verify(adSelectionEntryDaoSpy, times(1))
                .doesAdSelectionMatchingCallerPackageNameExistInOnDeviceTable(
                        AD_SELECTION_ID, TEST_PACKAGE_NAME);
        verify(adSelectionEntryDaoSpy, times(0)).doesAdSelectionIdExist(AD_SELECTION_ID);
    }

    private AdSelectionServiceImpl generateAdSelectionServiceImpl() {
        return new AdSelectionServiceImpl(
                mAdSelectionEntryDao,
                mAppInstallDao,
                mCustomAudienceDao,
                mEncodedPayloadDao,
                mFrequencyCapDao,
                mEncryptionKeyDao,
                mEnrollmentDao,
                mClientSpy,
                mDevContextFilterMock,
                mLightweightExecutorService,
                mBackgroundExecutorService,
                mScheduledExecutor,
                CONTEXT,
                mAdServicesLoggerMock,
                mFlags,
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

    private void persistAdSelectionEntryDaoResults(Map<Long, Double> adSelectionIdToBidMap) {
        persistAdSelectionEntryDaoResults(adSelectionIdToBidMap, TEST_PACKAGE_NAME);
    }

    private void persistAdSelectionEntryDaoResults(
            Map<Long, Double> adSelectionIdToBidMap, String callerPackageName) {
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
                            .setCallerPackageName(callerPackageName)
                            .build();
            mAdSelectionEntryDao.persistAdSelection(dbAdSelectionEntry);
        }
    }

    /**
     * Given Throttler is singleton, & shared across tests, this method should be invoked after
     * tests that impose restrictive rate limits.
     */
    private void resetThrottlerToNoRateLimits() {
        Throttler.destroyExistingThrottler();
        final float noRateLimit = -1;
        Flags mockNoRateLimitFlags = mock(Flags.class);
        doReturn(noRateLimit).when(mockNoRateLimitFlags).getSdkRequestPermitsPerSecond();
        Throttler.getInstance(mockNoRateLimitFlags);
    }

    private AdSelectionOverrideTestCallback callAddOverrideForSelectAds(
            AdSelectionServiceImpl adSelectionService,
            AdSelectionConfig adSelectionConfig,
            String decisionLogicJS,
            AdSelectionSignals trustedScoringSignals,
            PerBuyerDecisionLogic perBuyerDecisionLogic)
            throws Exception {
        // Counted down in 1) callback and 2) logApiCall
        CountDownLatch resultLatch = new CountDownLatch(2);
        AdSelectionOverrideTestCallback callback = new AdSelectionOverrideTestCallback(resultLatch);

        // Wait for the logging call, which happens after the callback
        Answer<Void> countDownAnswer =
                unused -> {
                    resultLatch.countDown();
                    return null;
                };
        doAnswer(countDownAnswer)
                .when(mAdServicesLoggerMock)
                .logFledgeApiCallStats(anyInt(), anyString(), anyInt(), anyInt());

        adSelectionService.overrideAdSelectionConfigRemoteInfo(
                adSelectionConfig,
                decisionLogicJS,
                trustedScoringSignals,
                perBuyerDecisionLogic,
                callback);
        resultLatch.await();
        return callback;
    }

    private AdSelectionOverrideTestCallback callAddOverrideForSelectAds(
            AdSelectionServiceImpl adSelectionService,
            AdSelectionFromOutcomesConfig adSelectionFromOutcomesConfig,
            String selectionLogic,
            AdSelectionSignals selectionSignals)
            throws Exception {
        // Counted down in 1) callback and 2) logApiCall
        CountDownLatch resultLatch = new CountDownLatch(2);
        AdSelectionOverrideTestCallback callback = new AdSelectionOverrideTestCallback(resultLatch);

        // Wait for the logging call, which happens after the callback
        Answer<Void> countDownAnswer =
                unused -> {
                    resultLatch.countDown();
                    return null;
                };
        doAnswer(countDownAnswer)
                .when(mAdServicesLoggerMock)
                .logFledgeApiCallStats(anyInt(), anyString(), anyInt(), anyInt());

        adSelectionService.overrideAdSelectionFromOutcomesConfigRemoteInfo(
                adSelectionFromOutcomesConfig, selectionLogic, selectionSignals, callback);
        resultLatch.await();
        return callback;
    }

    private AdSelectionOverrideTestCallback callRemoveOverride(
            AdSelectionServiceImpl adSelectionService, AdSelectionConfig adSelectionConfig)
            throws Exception {
        // Counted down in 1) callback and 2) logApiCall
        CountDownLatch resultLatch = new CountDownLatch(2);
        AdSelectionOverrideTestCallback callback = new AdSelectionOverrideTestCallback(resultLatch);

        // Wait for the logging call, which happens after the callback
        Answer<Void> countDownAnswer =
                unused -> {
                    resultLatch.countDown();
                    return null;
                };
        doAnswer(countDownAnswer)
                .when(mAdServicesLoggerMock)
                .logFledgeApiCallStats(anyInt(), anyString(), anyInt(), anyInt());

        adSelectionService.removeAdSelectionConfigRemoteInfoOverride(adSelectionConfig, callback);
        resultLatch.await();
        return callback;
    }

    private AdSelectionOverrideTestCallback callRemoveOverride(
            AdSelectionServiceImpl adSelectionService, AdSelectionFromOutcomesConfig config)
            throws Exception {
        // Counted down in 1) callback and 2) logApiCall
        CountDownLatch resultLatch = new CountDownLatch(2);
        AdSelectionOverrideTestCallback callback = new AdSelectionOverrideTestCallback(resultLatch);

        // Wait for the logging call, which happens after the callback
        Answer<Void> countDownAnswer =
                unused -> {
                    resultLatch.countDown();
                    return null;
                };
        doAnswer(countDownAnswer)
                .when(mAdServicesLoggerMock)
                .logFledgeApiCallStats(anyInt(), anyString(), anyInt(), anyInt());

        adSelectionService.removeAdSelectionFromOutcomesConfigRemoteInfoOverride(config, callback);
        resultLatch.await();
        return callback;
    }

    private AdSelectionOverrideTestCallback callResetAllOverrides(
            AdSelectionServiceImpl adSelectionService) throws Exception {
        // Counted down in 1) callback and 2) logApiCall
        CountDownLatch resultLatch = new CountDownLatch(2);
        AdSelectionOverrideTestCallback callback = new AdSelectionOverrideTestCallback(resultLatch);

        // Wait for the logging call, which happens after the callback
        Answer<Void> countDownAnswer =
                unused -> {
                    resultLatch.countDown();
                    return null;
                };
        doAnswer(countDownAnswer)
                .when(mAdServicesLoggerMock)
                .logFledgeApiCallStats(anyInt(), anyString(), anyInt(), anyInt());

        adSelectionService.resetAllAdSelectionConfigRemoteOverrides(callback);
        resultLatch.await();
        return callback;
    }

    private AdSelectionOverrideTestCallback callResetAllSelectionOutcomesOverrides(
            AdSelectionServiceImpl adSelectionService) throws Exception {
        // Counted down in 1) callback and 2) logApiCall
        CountDownLatch resultLatch = new CountDownLatch(2);
        AdSelectionOverrideTestCallback callback = new AdSelectionOverrideTestCallback(resultLatch);

        // Wait for the logging call, which happens after the callback
        Answer<Void> countDownAnswer =
                unused -> {
                    resultLatch.countDown();
                    return null;
                };
        doAnswer(countDownAnswer)
                .when(mAdServicesLoggerMock)
                .logFledgeApiCallStats(anyInt(), anyString(), anyInt(), anyInt());

        adSelectionService.resetAllAdSelectionFromOutcomesConfigRemoteOverrides(callback);
        resultLatch.await();
        return callback;
    }

    private AdSelectionFromOutcomesTestCallback invokeSelectAdsFromOutcomes(
            AdSelectionServiceImpl adSelectionService,
            AdSelectionFromOutcomesConfig adSelectionFromOutcomesConfig,
            String callerPackageName)
            throws InterruptedException, RemoteException {

        CountDownLatch countdownLatch = new CountDownLatch(1);
        AdSelectionFromOutcomesTestCallback adSelectionTestCallback =
                new AdSelectionFromOutcomesTestCallback(countdownLatch);

        AdSelectionFromOutcomesInput input =
                new AdSelectionFromOutcomesInput.Builder()
                        .setAdSelectionFromOutcomesConfig(adSelectionFromOutcomesConfig)
                        .setCallerPackageName(callerPackageName)
                        .build();

        adSelectionService.selectAdsFromOutcomes(input, null, adSelectionTestCallback);
        adSelectionTestCallback.mCountDownLatch.await();
        return adSelectionTestCallback;
    }

    private ReportImpressionTestCallback callReportImpression(
            AdSelectionServiceImpl adSelectionService, ReportImpressionInput requestParams)
            throws Exception {
        return callReportImpression(adSelectionService, requestParams, false);
    }

    /**
     * @param shouldCountLog if true, adds a latch to the log interaction as well.
     */
    private ReportImpressionTestCallback callReportImpression(
            AdSelectionServiceImpl adSelectionService,
            ReportImpressionInput requestParams,
            boolean shouldCountLog)
            throws Exception {
        CountDownLatch resultLatch = new CountDownLatch(shouldCountLog ? 2 : 1);

        if (shouldCountLog) {
            // Wait for the logging call, which happens after the callback
            Answer<Void> countDownAnswer =
                    unused -> {
                        resultLatch.countDown();
                        sLogger.i("Log called.");
                        return null;
                    };
            doAnswer(countDownAnswer)
                    .when(mAdServicesLoggerMock)
                    .logFledgeApiCallStats(anyInt(), anyString(), anyInt(), anyInt());
        }

        ReportImpressionTestCallback callback = new ReportImpressionTestCallback(resultLatch);
        adSelectionService.reportImpression(requestParams, callback);
        resultLatch.await();
        return callback;
    }

    private AppInstallAdvertisersSetterTest.SetAppInstallAdvertisersTestCallback
            callSetAppInstallAdvertisers(
                    AdSelectionServiceImpl adSelectionService,
                    SetAppInstallAdvertisersInput request)
                    throws Exception {
        // Counted down in callback
        CountDownLatch resultLatch = new CountDownLatch(1);
        AppInstallAdvertisersSetterTest.SetAppInstallAdvertisersTestCallback callback =
                new AppInstallAdvertisersSetterTest.SetAppInstallAdvertisersTestCallback(
                        resultLatch);

        adSelectionService.setAppInstallAdvertisers(request, callback);
        resultLatch.await();
        return callback;
    }

    private UpdateAdCounterHistogramWorkerTest.UpdateAdCounterHistogramTestCallback
            callUpdateAdCounterHistogram(
                    AdSelectionServiceImpl adSelectionService,
                    UpdateAdCounterHistogramInput inputParams)
                    throws InterruptedException {
        // Counted down in 1) callback and 2) logApiCall
        CountDownLatch resultLatch = new CountDownLatch(2);
        UpdateAdCounterHistogramWorkerTest.UpdateAdCounterHistogramTestCallback callback =
                new UpdateAdCounterHistogramWorkerTest.UpdateAdCounterHistogramTestCallback(
                        resultLatch);

        // Wait for the logging call, which happens after the callback
        Answer<Void> countDownAnswer =
                unused -> {
                    resultLatch.countDown();
                    return null;
                };
        doAnswer(countDownAnswer)
                .when(mAdServicesLoggerMock)
                .logFledgeApiCallStats(anyInt(), anyString(), anyInt(), anyInt());

        adSelectionService.updateAdCounterHistogram(inputParams, callback);
        assertTrue(
                "Timed out waiting for updateAdCounterHistogram call to complete",
                resultLatch.await(5, TimeUnit.SECONDS));
        return callback;
    }

    private AdSelectionOverrideTestCallback callSetAdCounterHistogramOverride(
            AdSelectionServiceImpl adSelectionService,
            SetAdCounterHistogramOverrideInput inputParams)
            throws InterruptedException {
        // Counted down in 1) callback and 2) logApiCall
        CountDownLatch resultLatch = new CountDownLatch(2);
        AdSelectionOverrideTestCallback callback = new AdSelectionOverrideTestCallback(resultLatch);

        // Wait for the logging call, which happens after the callback
        Answer<Void> countDownAnswer =
                unused -> {
                    resultLatch.countDown();
                    return null;
                };
        doAnswer(countDownAnswer)
                .when(mAdServicesLoggerMock)
                .logFledgeApiCallStats(anyInt(), anyString(), anyInt(), anyInt());

        adSelectionService.setAdCounterHistogramOverride(inputParams, callback);
        assertTrue(
                "Timed out waiting for setAdCounterHistogramOverride call to complete",
                resultLatch.await(5, TimeUnit.SECONDS));
        return callback;
    }

    private AdSelectionOverrideTestCallback callRemoveAdCounterHistogramOverride(
            AdSelectionServiceImpl adSelectionService,
            RemoveAdCounterHistogramOverrideInput inputParams)
            throws InterruptedException {
        // Counted down in 1) callback and 2) logApiCall
        CountDownLatch resultLatch = new CountDownLatch(2);
        AdSelectionOverrideTestCallback callback = new AdSelectionOverrideTestCallback(resultLatch);

        // Wait for the logging call, which happens after the callback
        Answer<Void> countDownAnswer =
                unused -> {
                    resultLatch.countDown();
                    return null;
                };
        doAnswer(countDownAnswer)
                .when(mAdServicesLoggerMock)
                .logFledgeApiCallStats(anyInt(), anyString(), anyInt(), anyInt());

        adSelectionService.removeAdCounterHistogramOverride(inputParams, callback);
        assertTrue(
                "Timed out waiting for removeAdCounterHistogramOverride call to complete",
                resultLatch.await(5, TimeUnit.SECONDS));
        return callback;
    }

    private AdSelectionOverrideTestCallback callResetAllAdCounterHistogramOverrides(
            AdSelectionServiceImpl adSelectionService) throws InterruptedException {
        // Counted down in 1) callback and 2) logApiCall
        CountDownLatch resultLatch = new CountDownLatch(2);
        AdSelectionOverrideTestCallback callback = new AdSelectionOverrideTestCallback(resultLatch);

        // Wait for the logging call, which happens after the callback
        Answer<Void> countDownAnswer =
                unused -> {
                    resultLatch.countDown();
                    return null;
                };
        doAnswer(countDownAnswer)
                .when(mAdServicesLoggerMock)
                .logFledgeApiCallStats(anyInt(), anyString(), anyInt(), anyInt());

        adSelectionService.resetAllAdCounterHistogramOverrides(callback);
        assertTrue(
                "Timed out waiting for resetAllAdCounterHistogramOverrides call to complete",
                resultLatch.await(5, TimeUnit.SECONDS));
        return callback;
    }

    private ReportInteractionTestCallback callReportInteraction(
            AdSelectionServiceImpl adSelectionService, ReportInteractionInput inputParams)
            throws Exception {
        return callReportInteraction(adSelectionService, inputParams, false);
    }

    /**
     * @param shouldCountLog if true, adds a latch to the log interaction as well.
     */
    private ReportInteractionTestCallback callReportInteraction(
            AdSelectionServiceImpl adSelectionService,
            ReportInteractionInput inputParams,
            boolean shouldCountLog)
            throws Exception {
        CountDownLatch resultLatch = new CountDownLatch(shouldCountLog ? 2 : 1);

        if (shouldCountLog) {
            // Wait for the logging call, which happens after the callback
            Answer<Void> countDownAnswer =
                    unused -> {
                        resultLatch.countDown();
                        return null;
                    };
            doAnswer(countDownAnswer)
                    .when(mAdServicesLoggerMock)
                    .logFledgeApiCallStats(anyInt(), anyString(), anyInt(), anyInt());
        }

        ReportInteractionTestCallback callback = new ReportInteractionTestCallback(resultLatch);
        adSelectionService.reportInteraction(inputParams, callback);
        resultLatch.await();
        return callback;
    }

    private String insertJsWait(long waitTime) {
        return "    const wait = (ms) => {\n"
                + "       var start = new Date().getTime();\n"
                + "       var end = start;\n"
                + "       while(end < start + ms) {\n"
                + "         end = new Date().getTime();\n"
                + "      }\n"
                + "    }\n"
                + String.format(Locale.ENGLISH, "    wait(\"%d\");\n", waitTime);
    }

    String getSaltString(int length) {
        String chars = "abcdefghijklmnopqrstuvwxyz";
        StringBuilder salt = new StringBuilder();
        Random rnd = new Random();
        while (salt.length() < length) {
            int index = (int) (rnd.nextFloat() * chars.length());
            salt.append(chars.charAt(index));
        }
        return salt.toString();
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

    public static class AdSelectionOverrideTestCallback extends AdSelectionOverrideCallback.Stub {
        protected final CountDownLatch mCountDownLatch;
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

    static class ReportInteractionTestCallback extends ReportInteractionCallback.Stub {
        protected final CountDownLatch mCountDownLatch;
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

    public static class ReportInteractionTestErrorCallback extends ReportInteractionTestCallback {
        public ReportInteractionTestErrorCallback(CountDownLatch countDownLatch) {
            super(countDownLatch);
        }

        @Override
        public void onSuccess() throws RemoteException {
            mIsSuccess = true;
            mCountDownLatch.countDown();
            throw new RemoteException();
        }
    }

    public static class AdSelectionOverrideTestErrorCallback
            extends AdSelectionOverrideTestCallback {
        public AdSelectionOverrideTestErrorCallback(CountDownLatch countDownLatch) {
            super(countDownLatch);
        }

        @Override
        public void onSuccess() throws RemoteException {
            mIsSuccess = true;
            mCountDownLatch.countDown();
            throw new RemoteException();
        }
    }

    private static class AdSelectionServicesTestsFlags implements Flags {
        private final boolean mEnrollmentCheckEnabled;

        AdSelectionServicesTestsFlags(boolean enrollmentCheckEnabled) {
            mEnrollmentCheckEnabled = enrollmentCheckEnabled;
        }

        @Override
        public boolean getDisableFledgeEnrollmentCheck() {
            return !mEnrollmentCheckEnabled;
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
        public boolean getEnforceIsolateMaxHeapSize() {
            return false;
        }

        @Override
        public float getSdkRequestPermitsPerSecond() {
            // Unlimited rate for unit tests to avoid flake in tests due to rate limiting
            return -1;
        }

        @Override
        public boolean getFledgeRegisterAdBeaconEnabled() {
            return true;
        }

        @Override
        public boolean getFledgeMeasurementReportAndRegisterEventApiEnabled() {
            return false;
        }

        @Override
        public boolean getFledgeMeasurementReportAndRegisterEventApiFallbackEnabled() {
            return false;
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
    }
}
