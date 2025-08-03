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

import static android.adservices.adselection.CustomAudienceBiddingInfoFixture.BUYER_CONTEXTUAL_SIGNALS_All;
import static android.adservices.adselection.CustomAudienceBiddingInfoFixture.BUYER_CONTEXTUAL_SIGNALS_WITH_AD_COST;
import static android.adservices.adselection.CustomAudienceBiddingInfoFixture.BUYER_CONTEXTUAL_SIGNALS_WITH_DATA_VERSION;
import static android.adservices.adselection.CustomAudienceBiddingInfoFixture.DATA_VERSION_1;
import static android.adservices.adselection.CustomAudienceBiddingInfoFixture.DATA_VERSION_2;
import static android.adservices.common.AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;
import static android.adservices.common.AdServicesStatusUtils.STATUS_UNSET;

import static com.android.adservices.common.CommonFlagsValues.EXTENDED_FLEDGE_AD_SELECTION_BIDDING_TIMEOUT_PER_CA_MS;
import static com.android.adservices.service.adselection.AdBidGeneratorImpl.BIDDING_TIMED_OUT;
import static com.android.adservices.service.adselection.AdBidGeneratorImpl.MISSING_TRUSTED_BIDDING_SIGNALS;
import static com.android.adservices.service.adselection.DataVersionFetcher.DATA_VERSION_HEADER_BIDDING_KEY;
import static com.android.adservices.service.stats.AdSelectionExecutionLogger.SCRIPT_JAVASCRIPT;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTest.START_ELAPSED_TIMESTAMP;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTest.STOP_ELAPSED_TIMESTAMP;
import static com.android.adservices.service.stats.RunAdBiddingPerCAExecutionLogger.SCRIPT_UNSET;
import static com.android.adservices.service.stats.RunAdBiddingPerCAExecutionLoggerTest.GENERATE_BIDS_END_TIMESTAMP;
import static com.android.adservices.service.stats.RunAdBiddingPerCAExecutionLoggerTest.GENERATE_BIDS_LATENCY_IN_MS;
import static com.android.adservices.service.stats.RunAdBiddingPerCAExecutionLoggerTest.GENERATE_BIDS_START_TIMESTAMP;
import static com.android.adservices.service.stats.RunAdBiddingPerCAExecutionLoggerTest.GET_BUYER_DECISION_LOGIC_END_TIMESTAMP;
import static com.android.adservices.service.stats.RunAdBiddingPerCAExecutionLoggerTest.GET_BUYER_DECISION_LOGIC_LATENCY_IN_MS;
import static com.android.adservices.service.stats.RunAdBiddingPerCAExecutionLoggerTest.GET_BUYER_DECISION_LOGIC_START_TIMESTAMP;
import static com.android.adservices.service.stats.RunAdBiddingPerCAExecutionLoggerTest.GET_TRUSTED_BIDDING_SIGNALS_END_TIMESTAMP;
import static com.android.adservices.service.stats.RunAdBiddingPerCAExecutionLoggerTest.GET_TRUSTED_BIDDING_SIGNALS_IN_MS;
import static com.android.adservices.service.stats.RunAdBiddingPerCAExecutionLoggerTest.GET_TRUSTED_BIDDING_SIGNALS_START_TIMESTAMP;
import static com.android.adservices.service.stats.RunAdBiddingPerCAExecutionLoggerTest.RUN_AD_BIDDING_PER_CA_LATENCY_IN_MS;
import static com.android.adservices.service.stats.RunAdBiddingPerCAExecutionLoggerTest.RUN_AD_BIDDING_PER_CA_START_TIMESTAMP;
import static com.android.adservices.service.stats.RunAdBiddingPerCAExecutionLoggerTest.RUN_BIDDING_END_TIMESTAMP;
import static com.android.adservices.service.stats.RunAdBiddingPerCAExecutionLoggerTest.RUN_BIDDING_LATENCY_IN_MS;
import static com.android.adservices.service.stats.RunAdBiddingPerCAExecutionLoggerTest.RUN_BIDDING_START_TIMESTAMP;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.isA;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.adservices.adselection.AdWithBid;
import android.adservices.common.AdData;
import android.adservices.common.AdDataFixture;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.CommonFixture;
import android.adservices.http.MockWebServerRule;
import android.annotation.NonNull;
import android.content.Context;
import android.net.Uri;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.MockWebServerRuleFactory;
import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.customaudience.DBCustomAudienceFixture;
import com.android.adservices.data.adselection.CustomAudienceSignals;
import com.android.adservices.data.common.DBAdData;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.CustomAudienceDatabase;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.data.customaudience.DBCustomAudienceOverride;
import com.android.adservices.data.customaudience.DBTrustedBiddingData;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.cache.CacheProviderFactory;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.devapi.CustomAudienceDevOverridesHelper;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.js.IsolateSettings;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerUtil;
import com.android.adservices.service.stats.RunAdBiddingPerCAExecutionLogger;
import com.android.adservices.service.stats.RunAdBiddingPerCAProcessReportedStats;
import com.android.adservices.shared.util.Clock;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.mockwebserver.Dispatcher;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;
import com.google.mockwebserver.RecordedRequest;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.stream.Collectors;

public class AdBidGeneratorImplTest {
    public static final List<Double> BIDS = new ArrayList<>(ImmutableList.of(-10.0, 0.0, 1.0, 5.4));
    public static final List<AdData> ADS =
            AdDataFixture.getValidAdsByBuyer(CommonFixture.VALID_BUYER_1);
    public static final List<AdWithBid> AD_WITH_NON_POSITIVE_BIDS =
            new ArrayList<>(
                    ImmutableList.of(
                            new AdWithBid(ADS.get(0), BIDS.get(1)),
                            new AdWithBid(ADS.get(1), BIDS.get(0)),
                            new AdWithBid(ADS.get(2), BIDS.get(1)),
                            new AdWithBid(ADS.get(3), BIDS.get(0))));
    public static final List<AdWithBid> AD_WITH_BIDS =
            new ArrayList<>(
                    ImmutableList.of(
                            new AdWithBid(ADS.get(0), BIDS.get(1)),
                            new AdWithBid(ADS.get(1), BIDS.get(0)),
                            new AdWithBid(ADS.get(2), BIDS.get(3)),
                            new AdWithBid(ADS.get(3), BIDS.get(2))));
    private static final AdSelectionSignals EMPTY_AD_SELECTION_SIGNALS = AdSelectionSignals.EMPTY;
    private static final AdSelectionSignals EMPTY_BUYER_SIGNALS = AdSelectionSignals.EMPTY;
    private static final AdSelectionSignals EMPTY_CONTEXTUAL_SIGNALS = AdSelectionSignals.EMPTY;
    private static final DBCustomAudience CUSTOM_AUDIENCE_WITH_EMPTY_ADS =
            DBCustomAudienceFixture.getValidBuilderByBuyer(CommonFixture.VALID_BUYER_1)
                    .setAds(Collections.emptyList())
                    .build();
    private static final String JSON_EXCEPTION_MESSAGE = "Badly formatted JSON";
    private static final List<GenerateBidResult> GENERATE_BIDS_RESPONSE =
            AD_WITH_BIDS.stream()
                    .map(
                            adWithBid ->
                                    GenerateBidResult.builder()
                                            .setAdWithBid(adWithBid)
                                            .build())
                    .collect(Collectors.toList());
    private static final List<GenerateBidResult> GENERATE_BIDS_RESPONSE_WITH_AD_COST =
            AD_WITH_BIDS.stream()
                    .map(
                            adWithBid ->
                                    GenerateBidResult.builder()
                                            .setAdWithBid(adWithBid)
                                            .setAdCost(
                                                    BUYER_CONTEXTUAL_SIGNALS_WITH_AD_COST
                                                            .getAdCost())
                                            .build())
                    .collect(Collectors.toList());
    // Winning ad doesn't have ad cost, but all others do
    private static final List<GenerateBidResult> GENERATE_BIDS_RESPONSE_WINNER_NO_AD_COST =
            AD_WITH_BIDS.stream()
                    .map(
                            adWithBid -> {
                                if (adWithBid.getBid() == BIDS.get(3)) {
                                    return GenerateBidResult.builder()
                                            .setAdWithBid(adWithBid)
                                            .build();
                                } else {
                                    return GenerateBidResult.builder()
                                            .setAdWithBid(adWithBid)
                                            .setAdCost(
                                                    BUYER_CONTEXTUAL_SIGNALS_WITH_AD_COST
                                                            .getAdCost())
                                            .build();
                                }
                            })
                    .collect(Collectors.toList());

    private static final List<GenerateBidResult> GENERATE_NON_POSITIVE_BIDS_RESPONSE =
            AD_WITH_NON_POSITIVE_BIDS.stream()
                    .map(
                            adWithBid ->
                                    GenerateBidResult.builder()
                                            .setAdWithBid(adWithBid)
                                            .build())
                    .collect(Collectors.toList());
    @Rule public final MockitoRule rule = MockitoJUnit.rule();
    private static final String FETCH_JAVA_SCRIPT_PATH = "/fetchJavascript/";
    private static final Map<String, Object> TRUSTED_BIDDING_SIGNALS_MAP =
            ImmutableMap.of("max_bid_limit", 20, "ad_type", "retail");
    private static final List<String> TRUSTED_BIDDING_KEYS =
            ImmutableList.copyOf(TRUSTED_BIDDING_SIGNALS_MAP.keySet());
    private static final String TRUSTED_BIDDING_PATH = "/fetchBiddingSignals/";
    private static final ArgumentMatcher<AdSelectionSignals> TRUSTED_BIDDING_SIGNALS_MATCHER =
            new JsonObjectStringSimpleMatcher(new JSONObject(TRUSTED_BIDDING_SIGNALS_MAP));
    private static final AdSelectionSignals TRUSTED_BIDDING_SIGNALS =
            AdSelectionSignals.fromString(new JSONObject(TRUSTED_BIDDING_SIGNALS_MAP).toString());
    private static final String BUYER_DECISION_LOGIC_JS =
            "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,"
                    + " contextual_signals, custom_audience_signals) { \n"
                    + " return {'status': 0, 'results': {'reporting_uri': '"
                    + " buyerReportingUri "
                    + "' } };\n"
                    + "}";
    private static final Dispatcher DEFAULT_DISPATCHER_PRE_V3 =
            new Dispatcher() {
                @Override
                public MockResponse dispatch(RecordedRequest request) {
                    String headerName =
                            JsVersionHelper.getVersionHeaderName(
                                    JsVersionHelper.JS_PAYLOAD_TYPE_BUYER_BIDDING_LOGIC_JS);
                    switch (request.getPath()) {
                        case FETCH_JAVA_SCRIPT_PATH:
                            if (Long.parseLong(request.getHeader(headerName))
                                    == JsVersionRegister.BUYER_BIDDING_LOGIC_VERSION_VERSION_3) {
                                return new MockResponse().setBody(BUYER_DECISION_LOGIC_JS);
                            }
                            return new MockResponse().setResponseCode(404);
                        default:
                            return new MockResponse().setResponseCode(404);
                    }
                }
            };

    private static final Dispatcher DISPATCHER_EMPTY =
            new Dispatcher() {
                @Override
                public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
                    return new MockResponse().setResponseCode(404);
                }
            };

    private static final Dispatcher DEFAULT_DISPATCHER_V3 =
            new Dispatcher() {
                @Override
                public MockResponse dispatch(RecordedRequest request) {
                    String headerName =
                            JsVersionHelper.getVersionHeaderName(
                                    JsVersionHelper.JS_PAYLOAD_TYPE_BUYER_BIDDING_LOGIC_JS);
                    String jsVersion =
                            Long.toString(JsVersionRegister.BUYER_BIDDING_LOGIC_VERSION_VERSION_3);
                    switch (request.getPath()) {
                        case FETCH_JAVA_SCRIPT_PATH:
                            if (Long.parseLong(request.getHeader(headerName))
                                    == JsVersionRegister.BUYER_BIDDING_LOGIC_VERSION_VERSION_3) {
                                return new MockResponse()
                                        .setBody(BUYER_DECISION_LOGIC_JS)
                                        .setHeader(headerName, jsVersion);
                            }
                            return new MockResponse().setResponseCode(404);
                        default:
                            return new MockResponse().setResponseCode(404);
                    }
                }
            };

    private static final Dispatcher DISPATCHER_V3_RETURN_TOO_HIGH_VERSION =
            new Dispatcher() {
                @Override
                public MockResponse dispatch(RecordedRequest request) {
                    String headerName =
                            JsVersionHelper.getVersionHeaderName(
                                    JsVersionHelper.JS_PAYLOAD_TYPE_BUYER_BIDDING_LOGIC_JS);
                    String jsVersion =
                            Long.toString(
                                    JsVersionRegister.BUYER_BIDDING_LOGIC_VERSION_VERSION_3 + 1);
                    switch (request.getPath()) {
                        case FETCH_JAVA_SCRIPT_PATH:
                            if (Long.parseLong(request.getHeader(headerName))
                                    == JsVersionRegister.BUYER_BIDDING_LOGIC_VERSION_VERSION_3) {
                                return new MockResponse()
                                        .setBody(BUYER_DECISION_LOGIC_JS)
                                        .setHeader(headerName, jsVersion);
                            }
                            return new MockResponse().setResponseCode(404);
                        default:
                            return new MockResponse().setResponseCode(404);
                    }
                }
            };

    private static final AdCounterKeyCopier AD_COUNTER_KEY_COPIER_NO_OP =
            new AdCounterKeyCopierNoOpImpl();
    private static final AdCounterKeyCopier AD_COUNTER_KEY_COPIER = new AdCounterKeyCopierImpl();

    private ListeningExecutorService mLightweightExecutorService;
    private ListeningExecutorService mBackgroundExecutorService;
    private ListeningExecutorService mBlockingExecutorService;
    private ScheduledThreadPoolExecutor mScheduledExecutor;
    private final Context mContext = ApplicationProvider.getApplicationContext();
    @Rule public MockWebServerRule mMockWebServerRule = MockWebServerRuleFactory.createForHttps();
    @Mock AdSelectionScriptEngine mAdSelectionScriptEngine;
    private JsFetcher mJsFetcher;
    private Uri mDecisionLogicUri;
    private AdBidGeneratorImpl mAdBidGenerator;
    private AdServicesHttpsClient mAdServicesHttpsClient;
    private MockWebServer mServer;
    private DBCustomAudience mCustomAudienceWithAds;
    private DBTrustedBiddingData mTrustedBiddingData;
    private Map<Uri, TrustedBiddingResponse> mTrustedBiddingDataByBaseUri;
    private Uri mTrustedBiddingUri;
    private CustomAudienceSignals mCustomAudienceSignals;
    private CustomAudienceBiddingInfo mCustomAudienceBiddingInfo;
    private DevContext mDevContext;
    private CustomAudienceDao mCustomAudienceDao;
    private Flags mFlags;
    private MockWebServerRule.RequestMatcher<String> mRequestMatcherExactMatch;
    private IsolateSettings mIsolateSettings;
    @Mock private Clock mRunAdBiddingPerCAClockMock;
    @Mock private AdServicesLogger mAdServicesLoggerMock;
    private RunAdBiddingPerCAExecutionLogger mRunAdBiddingPerCAExecutionLogger;

    @Mock private DebugReporting mDebugReporting;

    @Captor
    ArgumentCaptor<RunAdBiddingPerCAProcessReportedStats>
            mRunAdBiddingPerCAProcessReportedStatsArgumentCaptor;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mFlags = new AdBidGeneratorImplTestFlags();
        mDevContext = DevContext.createForDevOptionsDisabled();
        mLightweightExecutorService = AdServicesExecutors.getLightWeightExecutor();
        mBackgroundExecutorService = AdServicesExecutors.getBackgroundExecutor();
        mBlockingExecutorService = AdServicesExecutors.getBlockingExecutor();
        mScheduledExecutor = AdServicesExecutors.getScheduler();
        mAdServicesHttpsClient =
                new AdServicesHttpsClient(
                        AdServicesExecutors.getBlockingExecutor(),
                        CacheProviderFactory.createNoOpCache());
        mJsFetcher =
                new JsFetcher(
                        mBackgroundExecutorService,
                        mLightweightExecutorService,
                        mAdServicesHttpsClient,
                        mFlags,
                        mDevContext);

        mDecisionLogicUri = mMockWebServerRule.uriForPath(FETCH_JAVA_SCRIPT_PATH);

        mTrustedBiddingUri = mMockWebServerRule.uriForPath(TRUSTED_BIDDING_PATH);
        mTrustedBiddingData =
                new DBTrustedBiddingData.Builder()
                        .setKeys(TRUSTED_BIDDING_KEYS)
                        .setUri(mTrustedBiddingUri)
                        .build();

        mTrustedBiddingDataByBaseUri =
                ImmutableMap.of(
                        mTrustedBiddingUri,
                        TrustedBiddingResponse.builder()
                                .setBody(new JSONObject(TRUSTED_BIDDING_SIGNALS_MAP))
                                .setHeaders(new JSONObject())
                                .build());

        mCustomAudienceWithAds =
                DBCustomAudienceFixture.getValidBuilderByBuyerNoFilters(CommonFixture.VALID_BUYER_1)
                        .setBiddingLogicUri(mDecisionLogicUri)
                        .setTrustedBiddingData(mTrustedBiddingData)
                        .build();

        mCustomAudienceSignals =
                CustomAudienceSignals.buildFromCustomAudience(mCustomAudienceWithAds);

        mCustomAudienceBiddingInfo =
                CustomAudienceBiddingInfo.create(
                        mDecisionLogicUri, BUYER_DECISION_LOGIC_JS, mCustomAudienceSignals, null);

        mIsolateSettings = IsolateSettings.forMaxHeapSizeEnforcementDisabled();

        mRequestMatcherExactMatch =
                (actualRequest, expectedRequest) -> actualRequest.equals(expectedRequest);

        when(mRunAdBiddingPerCAClockMock.elapsedRealtime()).thenReturn(START_ELAPSED_TIMESTAMP);
        mRunAdBiddingPerCAExecutionLogger =
                new RunAdBiddingPerCAExecutionLogger(
                        mRunAdBiddingPerCAClockMock, mAdServicesLoggerMock);
        mCustomAudienceDao =
                Room.inMemoryDatabaseBuilder(
                                ApplicationProvider.getApplicationContext(),
                                CustomAudienceDatabase.class)
                        .addTypeConverter(new DBCustomAudience.Converters(true, true))
                        .build()
                        .customAudienceDao();
        when(mDebugReporting.isEnabled()).thenReturn(false);
        when(mDebugReporting.getScriptStrategy())
                .thenReturn(new DebugReportingScriptDisabledStrategy());
    }

    @Test
    public void testRunAdBiddingPerCASuccess_preV3BiddingLogic() throws Exception {
        // Given we are using a direct executor and mock the returned result from the
        // AdSelectionScriptEngine.generateBids for preparing the test,
        mServer = mMockWebServerRule.startMockWebServer(DEFAULT_DISPATCHER_PRE_V3);

        CustomAudienceDevOverridesHelper customAudienceDevOverridesHelper =
                new CustomAudienceDevOverridesHelper(mDevContext, mCustomAudienceDao);
        mAdBidGenerator =
                new AdBidGeneratorImpl(
                        mContext,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mAdSelectionScriptEngine,
                        customAudienceDevOverridesHelper,
                        AD_COUNTER_KEY_COPIER_NO_OP,
                        mFlags,
                        mIsolateSettings,
                        mJsFetcher,
                        mDebugReporting,
                        mDevContext,
                        false);
        when(mAdSelectionScriptEngine.generateBids(
                        eq(BUYER_DECISION_LOGIC_JS),
                        eq(ADS),
                        eq(EMPTY_AD_SELECTION_SIGNALS),
                        eq(EMPTY_BUYER_SIGNALS),
                        argThat(TRUSTED_BIDDING_SIGNALS_MATCHER),
                        eq(EMPTY_CONTEXTUAL_SIGNALS),
                        eq(mCustomAudienceSignals),
                        isA(RunAdBiddingPerCAExecutionLogger.class)))
                .thenAnswer(
                        unUsedInvocation -> {
                            mRunAdBiddingPerCAExecutionLogger.startGenerateBids();
                            mRunAdBiddingPerCAExecutionLogger.endGenerateBids();
                            return FluentFuture.from(
                                    Futures.immediateFuture(GENERATE_BIDS_RESPONSE));
                        });
        when(mRunAdBiddingPerCAClockMock.elapsedRealtime())
                .thenReturn(
                        RUN_AD_BIDDING_PER_CA_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_END_TIMESTAMP,
                        RUN_BIDDING_START_TIMESTAMP,
                        GET_TRUSTED_BIDDING_SIGNALS_START_TIMESTAMP,
                        GET_TRUSTED_BIDDING_SIGNALS_END_TIMESTAMP,
                        GENERATE_BIDS_START_TIMESTAMP,
                        GENERATE_BIDS_END_TIMESTAMP,
                        RUN_BIDDING_END_TIMESTAMP,
                        STOP_ELAPSED_TIMESTAMP);
        // Logger calls come after the callback is returned
        CountDownLatch runAdBiddingPerCAProcessLoggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            runAdBiddingPerCAProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingPerCAProcessReportedStats(any());

        // When the call to runAdBiddingPerCA, and the computation of future is complete,
        FluentFuture<AdBiddingOutcome> result =
                mAdBidGenerator.runAdBiddingPerCA(
                        mCustomAudienceWithAds,
                        mTrustedBiddingDataByBaseUri,
                        EMPTY_AD_SELECTION_SIGNALS,
                        EMPTY_BUYER_SIGNALS,
                        mRunAdBiddingPerCAExecutionLogger);
        AdBiddingOutcome expectedAdBiddingOutcome =
                AdBiddingOutcome.builder()
                        .setAdWithBid(AD_WITH_BIDS.get(2))
                        .setCustomAudienceBiddingInfo(mCustomAudienceBiddingInfo)
                        .build();
        runAdBiddingPerCAProcessLoggerLatch.await();
        // Then we can test the result by assertion,
        assertEquals(expectedAdBiddingOutcome, result.get());
        verify(mAdSelectionScriptEngine)
                .generateBids(
                        eq(BUYER_DECISION_LOGIC_JS),
                        eq(ADS),
                        eq(EMPTY_AD_SELECTION_SIGNALS),
                        eq(EMPTY_BUYER_SIGNALS),
                        argThat(TRUSTED_BIDDING_SIGNALS_MATCHER),
                        eq(EMPTY_CONTEXTUAL_SIGNALS),
                        eq(mCustomAudienceSignals),
                        eq(mRunAdBiddingPerCAExecutionLogger));
        mMockWebServerRule.verifyMockServerRequests(
                mServer, 1, ImmutableList.of(FETCH_JAVA_SCRIPT_PATH), mRequestMatcherExactMatch);
        verifySuccessAdBiddingPerCALogging(
                mCustomAudienceWithAds.getAds().size(),
                BUYER_DECISION_LOGIC_JS.getBytes().length,
                TRUSTED_BIDDING_KEYS.size(),
                TRUSTED_BIDDING_SIGNALS.toString().getBytes().length);
    }

    @Test
    public void testRunAdBiddingPerCASuccess_preV3BiddingLogicWithDataVersionHeaderEnabled()
            throws Exception {
        // Given we are using a direct executor and mock the returned result from the
        // AdSelectionScriptEngine.generateBids for preparing the test,
        mServer = mMockWebServerRule.startMockWebServer(DEFAULT_DISPATCHER_PRE_V3);

        CustomAudienceDevOverridesHelper customAudienceDevOverridesHelper =
                new CustomAudienceDevOverridesHelper(mDevContext, mCustomAudienceDao);

        Map<Uri, TrustedBiddingResponse> trustedBiddingResponseAndHeadersMapWithDataVersionHeader =
                ImmutableMap.of(
                        mTrustedBiddingUri,
                        TrustedBiddingResponse.builder()
                                .setBody(new JSONObject(TRUSTED_BIDDING_SIGNALS_MAP))
                                .setHeaders(
                                        new JSONObject(
                                                ImmutableMap.of(
                                                        DATA_VERSION_HEADER_BIDDING_KEY,
                                                        List.of(DATA_VERSION_1))))
                                .build());

        mAdBidGenerator =
                new AdBidGeneratorImpl(
                        mContext,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mAdSelectionScriptEngine,
                        customAudienceDevOverridesHelper,
                        AD_COUNTER_KEY_COPIER_NO_OP,
                        mFlags,
                        mIsolateSettings,
                        mJsFetcher,
                        mDebugReporting,
                        mDevContext,
                        true);
        when(mAdSelectionScriptEngine.generateBids(
                        eq(BUYER_DECISION_LOGIC_JS),
                        eq(ADS),
                        eq(EMPTY_AD_SELECTION_SIGNALS),
                        eq(EMPTY_BUYER_SIGNALS),
                        argThat(TRUSTED_BIDDING_SIGNALS_MATCHER),
                        eq(BUYER_CONTEXTUAL_SIGNALS_WITH_DATA_VERSION.toAdSelectionSignals()),
                        eq(mCustomAudienceSignals),
                        isA(RunAdBiddingPerCAExecutionLogger.class)))
                .thenAnswer(
                        unUsedInvocation -> {
                            mRunAdBiddingPerCAExecutionLogger.startGenerateBids();
                            mRunAdBiddingPerCAExecutionLogger.endGenerateBids();
                            return FluentFuture.from(
                                    Futures.immediateFuture(GENERATE_BIDS_RESPONSE));
                        });
        when(mRunAdBiddingPerCAClockMock.elapsedRealtime())
                .thenReturn(
                        RUN_AD_BIDDING_PER_CA_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_END_TIMESTAMP,
                        RUN_BIDDING_START_TIMESTAMP,
                        GET_TRUSTED_BIDDING_SIGNALS_START_TIMESTAMP,
                        GET_TRUSTED_BIDDING_SIGNALS_END_TIMESTAMP,
                        GENERATE_BIDS_START_TIMESTAMP,
                        GENERATE_BIDS_END_TIMESTAMP,
                        RUN_BIDDING_END_TIMESTAMP,
                        STOP_ELAPSED_TIMESTAMP);
        // Logger calls come after the callback is returned
        CountDownLatch runAdBiddingPerCAProcessLoggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            runAdBiddingPerCAProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingPerCAProcessReportedStats(any());

        // When the call to runAdBiddingPerCA, and the computation of future is complete,
        FluentFuture<AdBiddingOutcome> result =
                mAdBidGenerator.runAdBiddingPerCA(
                        mCustomAudienceWithAds,
                        trustedBiddingResponseAndHeadersMapWithDataVersionHeader,
                        EMPTY_AD_SELECTION_SIGNALS,
                        EMPTY_AD_SELECTION_SIGNALS,
                        mRunAdBiddingPerCAExecutionLogger);
        // Create another CustomAudienceBiddingInfo with buyer contextual signals
        CustomAudienceBiddingInfo customAudienceBiddingInfo =
                CustomAudienceBiddingInfo.create(
                        mDecisionLogicUri,
                        BUYER_DECISION_LOGIC_JS,
                        mCustomAudienceSignals,
                        BUYER_CONTEXTUAL_SIGNALS_WITH_DATA_VERSION);
        AdBiddingOutcome expectedAdBiddingOutcome =
                AdBiddingOutcome.builder()
                        .setAdWithBid(AD_WITH_BIDS.get(2))
                        .setCustomAudienceBiddingInfo(customAudienceBiddingInfo)
                        .build();
        runAdBiddingPerCAProcessLoggerLatch.await();
        // Then we can test the result by assertion,
        assertEquals(expectedAdBiddingOutcome, result.get());
        verify(mAdSelectionScriptEngine)
                .generateBids(
                        eq(BUYER_DECISION_LOGIC_JS),
                        eq(ADS),
                        eq(EMPTY_AD_SELECTION_SIGNALS),
                        eq(EMPTY_BUYER_SIGNALS),
                        argThat(TRUSTED_BIDDING_SIGNALS_MATCHER),
                        eq(BUYER_CONTEXTUAL_SIGNALS_WITH_DATA_VERSION.toAdSelectionSignals()),
                        eq(mCustomAudienceSignals),
                        eq(mRunAdBiddingPerCAExecutionLogger));
        mMockWebServerRule.verifyMockServerRequests(
                mServer, 1, ImmutableList.of(FETCH_JAVA_SCRIPT_PATH), mRequestMatcherExactMatch);
        verifySuccessAdBiddingPerCALogging(
                mCustomAudienceWithAds.getAds().size(),
                BUYER_DECISION_LOGIC_JS.getBytes().length,
                TRUSTED_BIDDING_KEYS.size(),
                TRUSTED_BIDDING_SIGNALS.toString().getBytes().length);
    }

    @Test
    public void testRunAdBiddingPerCASuccess_preV3BiddingLogicWithDataVersionHeaderDisabled()
            throws Exception {
        // Given we are using a direct executor and mock the returned result from the
        // AdSelectionScriptEngine.generateBids for preparing the test,
        mServer = mMockWebServerRule.startMockWebServer(DEFAULT_DISPATCHER_PRE_V3);

        CustomAudienceDevOverridesHelper customAudienceDevOverridesHelper =
                new CustomAudienceDevOverridesHelper(mDevContext, mCustomAudienceDao);

        Map<Uri, TrustedBiddingResponse> trustedBiddingResponseAndHeadersMapWithDataVersionHeader =
                ImmutableMap.of(
                        mTrustedBiddingUri,
                        TrustedBiddingResponse.builder()
                                .setBody(new JSONObject(TRUSTED_BIDDING_SIGNALS_MAP))
                                .setHeaders(
                                        new JSONObject(
                                                ImmutableMap.of(
                                                        DATA_VERSION_HEADER_BIDDING_KEY,
                                                        List.of(DATA_VERSION_1))))
                                .build());

        mAdBidGenerator =
                new AdBidGeneratorImpl(
                        mContext,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mAdSelectionScriptEngine,
                        customAudienceDevOverridesHelper,
                        AD_COUNTER_KEY_COPIER_NO_OP,
                        mFlags,
                        mIsolateSettings,
                        mJsFetcher,
                        mDebugReporting,
                        mDevContext,
                        false);
        when(mAdSelectionScriptEngine.generateBids(
                        eq(BUYER_DECISION_LOGIC_JS),
                        eq(ADS),
                        eq(EMPTY_AD_SELECTION_SIGNALS),
                        eq(EMPTY_BUYER_SIGNALS),
                        argThat(TRUSTED_BIDDING_SIGNALS_MATCHER),
                        eq(EMPTY_AD_SELECTION_SIGNALS),
                        eq(mCustomAudienceSignals),
                        isA(RunAdBiddingPerCAExecutionLogger.class)))
                .thenAnswer(
                        unUsedInvocation -> {
                            mRunAdBiddingPerCAExecutionLogger.startGenerateBids();
                            mRunAdBiddingPerCAExecutionLogger.endGenerateBids();
                            return FluentFuture.from(
                                    Futures.immediateFuture(GENERATE_BIDS_RESPONSE));
                        });
        when(mRunAdBiddingPerCAClockMock.elapsedRealtime())
                .thenReturn(
                        RUN_AD_BIDDING_PER_CA_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_END_TIMESTAMP,
                        RUN_BIDDING_START_TIMESTAMP,
                        GET_TRUSTED_BIDDING_SIGNALS_START_TIMESTAMP,
                        GET_TRUSTED_BIDDING_SIGNALS_END_TIMESTAMP,
                        GENERATE_BIDS_START_TIMESTAMP,
                        GENERATE_BIDS_END_TIMESTAMP,
                        RUN_BIDDING_END_TIMESTAMP,
                        STOP_ELAPSED_TIMESTAMP);
        // Logger calls come after the callback is returned
        CountDownLatch runAdBiddingPerCAProcessLoggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            runAdBiddingPerCAProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingPerCAProcessReportedStats(any());

        // When the call to runAdBiddingPerCA, and the computation of future is complete,
        FluentFuture<AdBiddingOutcome> result =
                mAdBidGenerator.runAdBiddingPerCA(
                        mCustomAudienceWithAds,
                        trustedBiddingResponseAndHeadersMapWithDataVersionHeader,
                        EMPTY_AD_SELECTION_SIGNALS,
                        EMPTY_AD_SELECTION_SIGNALS,
                        mRunAdBiddingPerCAExecutionLogger);
        // Create another CustomAudienceBiddingInfo without buyer contextual signals
        CustomAudienceBiddingInfo customAudienceBiddingInfo =
                CustomAudienceBiddingInfo.create(
                        mDecisionLogicUri, BUYER_DECISION_LOGIC_JS, mCustomAudienceSignals, null);
        AdBiddingOutcome expectedAdBiddingOutcome =
                AdBiddingOutcome.builder()
                        .setAdWithBid(AD_WITH_BIDS.get(2))
                        .setCustomAudienceBiddingInfo(customAudienceBiddingInfo)
                        .build();
        runAdBiddingPerCAProcessLoggerLatch.await();
        // Then we can test the result by assertion,
        assertEquals(expectedAdBiddingOutcome, result.get());
        verify(mAdSelectionScriptEngine)
                .generateBids(
                        eq(BUYER_DECISION_LOGIC_JS),
                        eq(ADS),
                        eq(EMPTY_AD_SELECTION_SIGNALS),
                        eq(EMPTY_BUYER_SIGNALS),
                        argThat(TRUSTED_BIDDING_SIGNALS_MATCHER),
                        eq(EMPTY_AD_SELECTION_SIGNALS),
                        eq(mCustomAudienceSignals),
                        eq(mRunAdBiddingPerCAExecutionLogger));
        mMockWebServerRule.verifyMockServerRequests(
                mServer, 1, ImmutableList.of(FETCH_JAVA_SCRIPT_PATH), mRequestMatcherExactMatch);
        verifySuccessAdBiddingPerCALogging(
                mCustomAudienceWithAds.getAds().size(),
                BUYER_DECISION_LOGIC_JS.getBytes().length,
                TRUSTED_BIDDING_KEYS.size(),
                TRUSTED_BIDDING_SIGNALS.toString().getBytes().length);
    }

    @Test
    public void
            testRunAdBiddingPerCASuccess_preV3BiddingLogicWithDataVersionHeaderTakesFirstOfMultipleValues()
                    throws Exception {
        // Given we are using a direct executor and mock the returned result from the
        // AdSelectionScriptEngine.generateBids for preparing the test,
        mServer = mMockWebServerRule.startMockWebServer(DEFAULT_DISPATCHER_PRE_V3);

        CustomAudienceDevOverridesHelper customAudienceDevOverridesHelper =
                new CustomAudienceDevOverridesHelper(mDevContext, mCustomAudienceDao);

        Map<Uri, TrustedBiddingResponse> trustedBiddingResponseAndHeadersMapWithDataVersionHeader =
                ImmutableMap.of(
                        mTrustedBiddingUri,
                        TrustedBiddingResponse.builder()
                                .setBody(new JSONObject(TRUSTED_BIDDING_SIGNALS_MAP))
                                .setHeaders(
                                        new JSONObject(
                                                ImmutableMap.of(
                                                        DATA_VERSION_HEADER_BIDDING_KEY,
                                                        List.of(DATA_VERSION_1, DATA_VERSION_2))))
                                .build());

        mAdBidGenerator =
                new AdBidGeneratorImpl(
                        mContext,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mAdSelectionScriptEngine,
                        customAudienceDevOverridesHelper,
                        AD_COUNTER_KEY_COPIER_NO_OP,
                        mFlags,
                        mIsolateSettings,
                        mJsFetcher,
                        mDebugReporting,
                        mDevContext,
                        true);
        when(mAdSelectionScriptEngine.generateBids(
                        eq(BUYER_DECISION_LOGIC_JS),
                        eq(ADS),
                        eq(EMPTY_AD_SELECTION_SIGNALS),
                        eq(EMPTY_BUYER_SIGNALS),
                        argThat(TRUSTED_BIDDING_SIGNALS_MATCHER),
                        eq(BUYER_CONTEXTUAL_SIGNALS_WITH_DATA_VERSION.toAdSelectionSignals()),
                        eq(mCustomAudienceSignals),
                        isA(RunAdBiddingPerCAExecutionLogger.class)))
                .thenAnswer(
                        unUsedInvocation -> {
                            mRunAdBiddingPerCAExecutionLogger.startGenerateBids();
                            mRunAdBiddingPerCAExecutionLogger.endGenerateBids();
                            return FluentFuture.from(
                                    Futures.immediateFuture(GENERATE_BIDS_RESPONSE));
                        });
        when(mRunAdBiddingPerCAClockMock.elapsedRealtime())
                .thenReturn(
                        RUN_AD_BIDDING_PER_CA_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_END_TIMESTAMP,
                        RUN_BIDDING_START_TIMESTAMP,
                        GET_TRUSTED_BIDDING_SIGNALS_START_TIMESTAMP,
                        GET_TRUSTED_BIDDING_SIGNALS_END_TIMESTAMP,
                        GENERATE_BIDS_START_TIMESTAMP,
                        GENERATE_BIDS_END_TIMESTAMP,
                        RUN_BIDDING_END_TIMESTAMP,
                        STOP_ELAPSED_TIMESTAMP);
        // Logger calls come after the callback is returned
        CountDownLatch runAdBiddingPerCAProcessLoggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            runAdBiddingPerCAProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingPerCAProcessReportedStats(any());

        // When the call to runAdBiddingPerCA, and the computation of future is complete,
        FluentFuture<AdBiddingOutcome> result =
                mAdBidGenerator.runAdBiddingPerCA(
                        mCustomAudienceWithAds,
                        trustedBiddingResponseAndHeadersMapWithDataVersionHeader,
                        EMPTY_AD_SELECTION_SIGNALS,
                        EMPTY_AD_SELECTION_SIGNALS,
                        mRunAdBiddingPerCAExecutionLogger);
        // Create another CustomAudienceBiddingInfo with buyer contextual signals
        CustomAudienceBiddingInfo customAudienceBiddingInfo =
                CustomAudienceBiddingInfo.create(
                        mDecisionLogicUri,
                        BUYER_DECISION_LOGIC_JS,
                        mCustomAudienceSignals,
                        BUYER_CONTEXTUAL_SIGNALS_WITH_DATA_VERSION);
        AdBiddingOutcome expectedAdBiddingOutcome =
                AdBiddingOutcome.builder()
                        .setAdWithBid(AD_WITH_BIDS.get(2))
                        .setCustomAudienceBiddingInfo(customAudienceBiddingInfo)
                        .build();
        runAdBiddingPerCAProcessLoggerLatch.await();
        // Then we can test the result by assertion,
        assertEquals(expectedAdBiddingOutcome, result.get());
        verify(mAdSelectionScriptEngine)
                .generateBids(
                        eq(BUYER_DECISION_LOGIC_JS),
                        eq(ADS),
                        eq(EMPTY_AD_SELECTION_SIGNALS),
                        eq(EMPTY_BUYER_SIGNALS),
                        argThat(TRUSTED_BIDDING_SIGNALS_MATCHER),
                        eq(BUYER_CONTEXTUAL_SIGNALS_WITH_DATA_VERSION.toAdSelectionSignals()),
                        eq(mCustomAudienceSignals),
                        eq(mRunAdBiddingPerCAExecutionLogger));
        mMockWebServerRule.verifyMockServerRequests(
                mServer, 1, ImmutableList.of(FETCH_JAVA_SCRIPT_PATH), mRequestMatcherExactMatch);
        verifySuccessAdBiddingPerCALogging(
                mCustomAudienceWithAds.getAds().size(),
                BUYER_DECISION_LOGIC_JS.getBytes().length,
                TRUSTED_BIDDING_KEYS.size(),
                TRUSTED_BIDDING_SIGNALS.toString().getBytes().length);
    }

    @Test
    public void testRunAdBiddingPerCASuccess_preV3BiddingLogicWithAdCost() throws Exception {
        // Given we are using a direct executor and mock the returned result from the
        // AdSelectionScriptEngine.generateBids for preparing the test,
        mServer = mMockWebServerRule.startMockWebServer(DEFAULT_DISPATCHER_PRE_V3);

        CustomAudienceDevOverridesHelper customAudienceDevOverridesHelper =
                new CustomAudienceDevOverridesHelper(mDevContext, mCustomAudienceDao);
        mAdBidGenerator =
                new AdBidGeneratorImpl(
                        mContext,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mAdSelectionScriptEngine,
                        customAudienceDevOverridesHelper,
                        AD_COUNTER_KEY_COPIER_NO_OP,
                        mFlags,
                        mIsolateSettings,
                        mJsFetcher,
                        mDebugReporting,
                        mDevContext,
                        false);
        when(mAdSelectionScriptEngine.generateBids(
                        eq(BUYER_DECISION_LOGIC_JS),
                        eq(ADS),
                        eq(EMPTY_AD_SELECTION_SIGNALS),
                        eq(EMPTY_BUYER_SIGNALS),
                        argThat(TRUSTED_BIDDING_SIGNALS_MATCHER),
                        eq(EMPTY_CONTEXTUAL_SIGNALS),
                        eq(mCustomAudienceSignals),
                        isA(RunAdBiddingPerCAExecutionLogger.class)))
                .thenAnswer(
                        unUsedInvocation -> {
                            mRunAdBiddingPerCAExecutionLogger.startGenerateBids();
                            mRunAdBiddingPerCAExecutionLogger.endGenerateBids();
                            return FluentFuture.from(
                                    Futures.immediateFuture(GENERATE_BIDS_RESPONSE_WITH_AD_COST));
                        });
        when(mRunAdBiddingPerCAClockMock.elapsedRealtime())
                .thenReturn(
                        RUN_AD_BIDDING_PER_CA_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_END_TIMESTAMP,
                        RUN_BIDDING_START_TIMESTAMP,
                        GET_TRUSTED_BIDDING_SIGNALS_START_TIMESTAMP,
                        GET_TRUSTED_BIDDING_SIGNALS_END_TIMESTAMP,
                        GENERATE_BIDS_START_TIMESTAMP,
                        GENERATE_BIDS_END_TIMESTAMP,
                        RUN_BIDDING_END_TIMESTAMP,
                        STOP_ELAPSED_TIMESTAMP);
        // Logger calls come after the callback is returned
        CountDownLatch runAdBiddingPerCAProcessLoggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            runAdBiddingPerCAProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingPerCAProcessReportedStats(any());

        // When the call to runAdBiddingPerCA, and the computation of future is complete,
        FluentFuture<AdBiddingOutcome> result =
                mAdBidGenerator.runAdBiddingPerCA(
                        mCustomAudienceWithAds,
                        mTrustedBiddingDataByBaseUri,
                        EMPTY_AD_SELECTION_SIGNALS,
                        EMPTY_BUYER_SIGNALS,
                        mRunAdBiddingPerCAExecutionLogger);
        // Create another CustomAudienceBiddingInfo with buyer contextual signals
        CustomAudienceBiddingInfo customAudienceBiddingInfo =
                CustomAudienceBiddingInfo.create(
                        mDecisionLogicUri,
                        BUYER_DECISION_LOGIC_JS,
                        mCustomAudienceSignals,
                        BUYER_CONTEXTUAL_SIGNALS_WITH_AD_COST);
        AdBiddingOutcome expectedAdBiddingOutcome =
                AdBiddingOutcome.builder()
                        .setAdWithBid(AD_WITH_BIDS.get(2))
                        .setCustomAudienceBiddingInfo(customAudienceBiddingInfo)
                        .build();
        runAdBiddingPerCAProcessLoggerLatch.await();
        // Then we can test the result by assertion,
        assertEquals(expectedAdBiddingOutcome, result.get());
        verify(mAdSelectionScriptEngine)
                .generateBids(
                        eq(BUYER_DECISION_LOGIC_JS),
                        eq(ADS),
                        eq(EMPTY_AD_SELECTION_SIGNALS),
                        eq(EMPTY_BUYER_SIGNALS),
                        argThat(TRUSTED_BIDDING_SIGNALS_MATCHER),
                        eq(EMPTY_CONTEXTUAL_SIGNALS),
                        eq(mCustomAudienceSignals),
                        eq(mRunAdBiddingPerCAExecutionLogger));
        mMockWebServerRule.verifyMockServerRequests(
                mServer, 1, ImmutableList.of(FETCH_JAVA_SCRIPT_PATH), mRequestMatcherExactMatch);
        verifySuccessAdBiddingPerCALogging(
                mCustomAudienceWithAds.getAds().size(),
                BUYER_DECISION_LOGIC_JS.getBytes().length,
                TRUSTED_BIDDING_KEYS.size(),
                TRUSTED_BIDDING_SIGNALS.toString().getBytes().length);
    }

    @Test
    public void testRunAdBiddingPerCASuccess_preV3BiddingLogicAllAdsButWinnerHaveAdCost()
            throws Exception {
        // Given we are using a direct executor and mock the returned result from the
        // AdSelectionScriptEngine.generateBids for preparing the test,
        mServer = mMockWebServerRule.startMockWebServer(DEFAULT_DISPATCHER_PRE_V3);

        CustomAudienceDevOverridesHelper customAudienceDevOverridesHelper =
                new CustomAudienceDevOverridesHelper(mDevContext, mCustomAudienceDao);
        mAdBidGenerator =
                new AdBidGeneratorImpl(
                        mContext,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mAdSelectionScriptEngine,
                        customAudienceDevOverridesHelper,
                        AD_COUNTER_KEY_COPIER_NO_OP,
                        mFlags,
                        mIsolateSettings,
                        mJsFetcher,
                        mDebugReporting,
                        mDevContext,
                        false);
        when(mAdSelectionScriptEngine.generateBids(
                        eq(BUYER_DECISION_LOGIC_JS),
                        eq(ADS),
                        eq(EMPTY_AD_SELECTION_SIGNALS),
                        eq(EMPTY_BUYER_SIGNALS),
                        argThat(TRUSTED_BIDDING_SIGNALS_MATCHER),
                        eq(EMPTY_CONTEXTUAL_SIGNALS),
                        eq(mCustomAudienceSignals),
                        isA(RunAdBiddingPerCAExecutionLogger.class)))
                .thenAnswer(
                        unUsedInvocation -> {
                            mRunAdBiddingPerCAExecutionLogger.startGenerateBids();
                            mRunAdBiddingPerCAExecutionLogger.endGenerateBids();
                            return FluentFuture.from(
                                    Futures.immediateFuture(
                                            GENERATE_BIDS_RESPONSE_WINNER_NO_AD_COST));
                        });
        when(mRunAdBiddingPerCAClockMock.elapsedRealtime())
                .thenReturn(
                        RUN_AD_BIDDING_PER_CA_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_END_TIMESTAMP,
                        RUN_BIDDING_START_TIMESTAMP,
                        GET_TRUSTED_BIDDING_SIGNALS_START_TIMESTAMP,
                        GET_TRUSTED_BIDDING_SIGNALS_END_TIMESTAMP,
                        GENERATE_BIDS_START_TIMESTAMP,
                        GENERATE_BIDS_END_TIMESTAMP,
                        RUN_BIDDING_END_TIMESTAMP,
                        STOP_ELAPSED_TIMESTAMP);
        // Logger calls come after the callback is returned
        CountDownLatch runAdBiddingPerCAProcessLoggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            runAdBiddingPerCAProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingPerCAProcessReportedStats(any());

        // When the call to runAdBiddingPerCA, and the computation of future is complete,
        FluentFuture<AdBiddingOutcome> result =
                mAdBidGenerator.runAdBiddingPerCA(
                        mCustomAudienceWithAds,
                        mTrustedBiddingDataByBaseUri,
                        EMPTY_AD_SELECTION_SIGNALS,
                        EMPTY_BUYER_SIGNALS,
                        mRunAdBiddingPerCAExecutionLogger);
        // Assume default custom audience bidding info since winning ad does not have adcost
        AdBiddingOutcome expectedAdBiddingOutcome =
                AdBiddingOutcome.builder()
                        .setAdWithBid(AD_WITH_BIDS.get(2))
                        .setCustomAudienceBiddingInfo(mCustomAudienceBiddingInfo)
                        .build();
        runAdBiddingPerCAProcessLoggerLatch.await();
        // Then we can test the result by assertion,
        assertEquals(expectedAdBiddingOutcome, result.get());
        verify(mAdSelectionScriptEngine)
                .generateBids(
                        eq(BUYER_DECISION_LOGIC_JS),
                        eq(ADS),
                        eq(EMPTY_AD_SELECTION_SIGNALS),
                        eq(EMPTY_BUYER_SIGNALS),
                        argThat(TRUSTED_BIDDING_SIGNALS_MATCHER),
                        eq(EMPTY_CONTEXTUAL_SIGNALS),
                        eq(mCustomAudienceSignals),
                        eq(mRunAdBiddingPerCAExecutionLogger));
        mMockWebServerRule.verifyMockServerRequests(
                mServer, 1, ImmutableList.of(FETCH_JAVA_SCRIPT_PATH), mRequestMatcherExactMatch);
        verifySuccessAdBiddingPerCALogging(
                mCustomAudienceWithAds.getAds().size(),
                BUYER_DECISION_LOGIC_JS.getBytes().length,
                TRUSTED_BIDDING_KEYS.size(),
                TRUSTED_BIDDING_SIGNALS.toString().getBytes().length);
    }

    @Test
    public void testRunAdBiddingPerCASuccess_preV3BiddingLogicWithAdCostAndDataVersionHeader()
            throws Exception {
        // Given we are using a direct executor and mock the returned result from the
        // AdSelectionScriptEngine.generateBids for preparing the test,
        mServer = mMockWebServerRule.startMockWebServer(DEFAULT_DISPATCHER_PRE_V3);

        CustomAudienceDevOverridesHelper customAudienceDevOverridesHelper =
                new CustomAudienceDevOverridesHelper(mDevContext, mCustomAudienceDao);

        Map<Uri, TrustedBiddingResponse> trustedBiddingResponseAndHeadersMapWithDataVersionHeader =
                ImmutableMap.of(
                        mTrustedBiddingUri,
                        TrustedBiddingResponse.builder()
                                .setBody(new JSONObject(TRUSTED_BIDDING_SIGNALS_MAP))
                                .setHeaders(
                                        new JSONObject(
                                                ImmutableMap.of(
                                                        DATA_VERSION_HEADER_BIDDING_KEY,
                                                        List.of(DATA_VERSION_1))))
                                .build());

        mAdBidGenerator =
                new AdBidGeneratorImpl(
                        mContext,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mAdSelectionScriptEngine,
                        customAudienceDevOverridesHelper,
                        AD_COUNTER_KEY_COPIER_NO_OP,
                        mFlags,
                        mIsolateSettings,
                        mJsFetcher,
                        mDebugReporting,
                        mDevContext,
                        true);
        when(mAdSelectionScriptEngine.generateBids(
                        eq(BUYER_DECISION_LOGIC_JS),
                        eq(ADS),
                        eq(EMPTY_AD_SELECTION_SIGNALS),
                        eq(EMPTY_BUYER_SIGNALS),
                        argThat(TRUSTED_BIDDING_SIGNALS_MATCHER),
                        eq(BUYER_CONTEXTUAL_SIGNALS_WITH_DATA_VERSION.toAdSelectionSignals()),
                        eq(mCustomAudienceSignals),
                        isA(RunAdBiddingPerCAExecutionLogger.class)))
                .thenAnswer(
                        unUsedInvocation -> {
                            mRunAdBiddingPerCAExecutionLogger.startGenerateBids();
                            mRunAdBiddingPerCAExecutionLogger.endGenerateBids();
                            return FluentFuture.from(
                                    Futures.immediateFuture(GENERATE_BIDS_RESPONSE_WITH_AD_COST));
                        });
        when(mRunAdBiddingPerCAClockMock.elapsedRealtime())
                .thenReturn(
                        RUN_AD_BIDDING_PER_CA_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_END_TIMESTAMP,
                        RUN_BIDDING_START_TIMESTAMP,
                        GET_TRUSTED_BIDDING_SIGNALS_START_TIMESTAMP,
                        GET_TRUSTED_BIDDING_SIGNALS_END_TIMESTAMP,
                        GENERATE_BIDS_START_TIMESTAMP,
                        GENERATE_BIDS_END_TIMESTAMP,
                        RUN_BIDDING_END_TIMESTAMP,
                        STOP_ELAPSED_TIMESTAMP);
        // Logger calls come after the callback is returned
        CountDownLatch runAdBiddingPerCAProcessLoggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            runAdBiddingPerCAProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingPerCAProcessReportedStats(any());

        // When the call to runAdBiddingPerCA, and the computation of future is complete,
        FluentFuture<AdBiddingOutcome> result =
                mAdBidGenerator.runAdBiddingPerCA(
                        mCustomAudienceWithAds,
                        trustedBiddingResponseAndHeadersMapWithDataVersionHeader,
                        EMPTY_AD_SELECTION_SIGNALS,
                        EMPTY_AD_SELECTION_SIGNALS,
                        mRunAdBiddingPerCAExecutionLogger);
        // Create another CustomAudienceBiddingInfo with buyer contextual signals
        CustomAudienceBiddingInfo customAudienceBiddingInfo =
                CustomAudienceBiddingInfo.create(
                        mDecisionLogicUri,
                        BUYER_DECISION_LOGIC_JS,
                        mCustomAudienceSignals,
                        BUYER_CONTEXTUAL_SIGNALS_All);
        AdBiddingOutcome expectedAdBiddingOutcome =
                AdBiddingOutcome.builder()
                        .setAdWithBid(AD_WITH_BIDS.get(2))
                        .setCustomAudienceBiddingInfo(customAudienceBiddingInfo)
                        .build();
        runAdBiddingPerCAProcessLoggerLatch.await();
        // Then we can test the result by assertion,
        assertEquals(expectedAdBiddingOutcome, result.get());
        verify(mAdSelectionScriptEngine)
                .generateBids(
                        eq(BUYER_DECISION_LOGIC_JS),
                        eq(ADS),
                        eq(EMPTY_AD_SELECTION_SIGNALS),
                        eq(EMPTY_BUYER_SIGNALS),
                        argThat(TRUSTED_BIDDING_SIGNALS_MATCHER),
                        eq(BUYER_CONTEXTUAL_SIGNALS_WITH_DATA_VERSION.toAdSelectionSignals()),
                        eq(mCustomAudienceSignals),
                        eq(mRunAdBiddingPerCAExecutionLogger));
        mMockWebServerRule.verifyMockServerRequests(
                mServer, 1, ImmutableList.of(FETCH_JAVA_SCRIPT_PATH), mRequestMatcherExactMatch);
        verifySuccessAdBiddingPerCALogging(
                mCustomAudienceWithAds.getAds().size(),
                BUYER_DECISION_LOGIC_JS.getBytes().length,
                TRUSTED_BIDDING_KEYS.size(),
                TRUSTED_BIDDING_SIGNALS.toString().getBytes().length);
    }

    @Test
    public void testRunAdBiddingPerCASuccess_preV3BiddingLogicWithCopier() throws Exception {
        // Given we are using a direct executor and mock the returned result from the
        // AdSelectionScriptEngine.generateBids for preparing the test,
        mServer = mMockWebServerRule.startMockWebServer(DEFAULT_DISPATCHER_PRE_V3);

        CustomAudienceDevOverridesHelper customAudienceDevOverridesHelper =
                new CustomAudienceDevOverridesHelper(mDevContext, mCustomAudienceDao);
        mAdBidGenerator =
                new AdBidGeneratorImpl(
                        mContext,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mAdSelectionScriptEngine,
                        customAudienceDevOverridesHelper,
                        AD_COUNTER_KEY_COPIER,
                        mFlags,
                        mIsolateSettings,
                        mJsFetcher,
                        mDebugReporting,
                        mDevContext,
                        false);
        when(mAdSelectionScriptEngine.generateBids(
                        eq(BUYER_DECISION_LOGIC_JS),
                        eq(ADS),
                        eq(EMPTY_AD_SELECTION_SIGNALS),
                        eq(EMPTY_BUYER_SIGNALS),
                        argThat(TRUSTED_BIDDING_SIGNALS_MATCHER),
                        eq(EMPTY_CONTEXTUAL_SIGNALS),
                        eq(mCustomAudienceSignals),
                        isA(RunAdBiddingPerCAExecutionLogger.class)))
                .thenAnswer(
                        unUsedInvocation -> {
                            mRunAdBiddingPerCAExecutionLogger.startGenerateBids();
                            mRunAdBiddingPerCAExecutionLogger.endGenerateBids();
                            return FluentFuture.from(
                                    Futures.immediateFuture(GENERATE_BIDS_RESPONSE));
                        });
        when(mRunAdBiddingPerCAClockMock.elapsedRealtime())
                .thenReturn(
                        RUN_AD_BIDDING_PER_CA_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_END_TIMESTAMP,
                        RUN_BIDDING_START_TIMESTAMP,
                        GET_TRUSTED_BIDDING_SIGNALS_START_TIMESTAMP,
                        GET_TRUSTED_BIDDING_SIGNALS_END_TIMESTAMP,
                        GENERATE_BIDS_START_TIMESTAMP,
                        GENERATE_BIDS_END_TIMESTAMP,
                        RUN_BIDDING_END_TIMESTAMP,
                        STOP_ELAPSED_TIMESTAMP);
        // Logger calls come after the callback is returned
        CountDownLatch runAdBiddingPerCAProcessLoggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            runAdBiddingPerCAProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingPerCAProcessReportedStats(any());

        // When the call to runAdBiddingPerCA, and the computation of future is complete,
        FluentFuture<AdBiddingOutcome> result =
                mAdBidGenerator.runAdBiddingPerCA(
                        mCustomAudienceWithAds,
                        mTrustedBiddingDataByBaseUri,
                        EMPTY_AD_SELECTION_SIGNALS,
                        EMPTY_BUYER_SIGNALS,
                        mRunAdBiddingPerCAExecutionLogger);
        AdBiddingOutcome expectedAdBiddingOutcome =
                AdBiddingOutcome.builder()
                        .setAdWithBid(AD_WITH_BIDS.get(2))
                        .setCustomAudienceBiddingInfo(mCustomAudienceBiddingInfo)
                        .build();
        runAdBiddingPerCAProcessLoggerLatch.await();
        // Then we can test the result by assertion,
        assertEquals(expectedAdBiddingOutcome, result.get());
        verify(mAdSelectionScriptEngine)
                .generateBids(
                        eq(BUYER_DECISION_LOGIC_JS),
                        eq(ADS),
                        eq(EMPTY_AD_SELECTION_SIGNALS),
                        eq(EMPTY_BUYER_SIGNALS),
                        argThat(TRUSTED_BIDDING_SIGNALS_MATCHER),
                        eq(EMPTY_CONTEXTUAL_SIGNALS),
                        eq(mCustomAudienceSignals),
                        eq(mRunAdBiddingPerCAExecutionLogger));
        mMockWebServerRule.verifyMockServerRequests(
                mServer, 1, ImmutableList.of(FETCH_JAVA_SCRIPT_PATH), mRequestMatcherExactMatch);
        verifySuccessAdBiddingPerCALogging(
                mCustomAudienceWithAds.getAds().size(),
                BUYER_DECISION_LOGIC_JS.getBytes().length,
                TRUSTED_BIDDING_KEYS.size(),
                TRUSTED_BIDDING_SIGNALS.toString().getBytes().length);
    }

    @Test
    public void testRunAdBiddingPerCASuccess_preV3BiddingLogicWithCopierWithAdCounterKeys()
            throws Exception {
        // Given we are using a direct executor and mock the returned result from the
        // AdSelectionScriptEngine.generateBids for preparing the test,
        mServer = mMockWebServerRule.startMockWebServer(DEFAULT_DISPATCHER_PRE_V3);

        DBCustomAudience customAudienceWithFiltersAndAdCounterKeys =
                DBCustomAudienceFixture.getValidBuilderByBuyer(CommonFixture.VALID_BUYER_1)
                        .setBiddingLogicUri(mDecisionLogicUri)
                        .setTrustedBiddingData(mTrustedBiddingData)
                        .build();
        List<AdData> expectedAdsWithCounterKeys = new ArrayList<>();
        for (DBAdData dbAdData : customAudienceWithFiltersAndAdCounterKeys.getAds()) {
            // We expect the filters to be stripped out and the keys to be copied over
            expectedAdsWithCounterKeys.add(
                    new AdData.Builder()
                            .setRenderUri(dbAdData.getRenderUri())
                            .setMetadata(dbAdData.getMetadata())
                            .setAdCounterKeys(dbAdData.getAdCounterKeys())
                            .build());
        }
        List<AdWithBid> expectedAdsWithBids =
                ImmutableList.of(
                        new AdWithBid(expectedAdsWithCounterKeys.get(0), BIDS.get(1)),
                        new AdWithBid(expectedAdsWithCounterKeys.get(1), BIDS.get(0)),
                        new AdWithBid(expectedAdsWithCounterKeys.get(2), BIDS.get(3)),
                        new AdWithBid(expectedAdsWithCounterKeys.get(3), BIDS.get(2)));

        CustomAudienceDevOverridesHelper customAudienceDevOverridesHelper =
                new CustomAudienceDevOverridesHelper(mDevContext, mCustomAudienceDao);
        mAdBidGenerator =
                new AdBidGeneratorImpl(
                        mContext,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mAdSelectionScriptEngine,
                        customAudienceDevOverridesHelper,
                        AD_COUNTER_KEY_COPIER,
                        mFlags,
                        mIsolateSettings,
                        mJsFetcher,
                        mDebugReporting,
                        mDevContext,
                        false);
        when(mAdSelectionScriptEngine.generateBids(
                        eq(BUYER_DECISION_LOGIC_JS),
                        eq(expectedAdsWithCounterKeys),
                        eq(EMPTY_AD_SELECTION_SIGNALS),
                        eq(EMPTY_BUYER_SIGNALS),
                        argThat(TRUSTED_BIDDING_SIGNALS_MATCHER),
                        eq(EMPTY_CONTEXTUAL_SIGNALS),
                        eq(mCustomAudienceSignals),
                        isA(RunAdBiddingPerCAExecutionLogger.class)))
                .thenAnswer(
                        unUsedInvocation -> {
                            mRunAdBiddingPerCAExecutionLogger.startGenerateBids();
                            mRunAdBiddingPerCAExecutionLogger.endGenerateBids();
                            return FluentFuture.from(
                                    Futures.immediateFuture(GENERATE_BIDS_RESPONSE));
                        });
        when(mRunAdBiddingPerCAClockMock.elapsedRealtime())
                .thenReturn(
                        RUN_AD_BIDDING_PER_CA_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_END_TIMESTAMP,
                        RUN_BIDDING_START_TIMESTAMP,
                        GET_TRUSTED_BIDDING_SIGNALS_START_TIMESTAMP,
                        GET_TRUSTED_BIDDING_SIGNALS_END_TIMESTAMP,
                        GENERATE_BIDS_START_TIMESTAMP,
                        GENERATE_BIDS_END_TIMESTAMP,
                        RUN_BIDDING_END_TIMESTAMP,
                        STOP_ELAPSED_TIMESTAMP);
        // Logger calls come after the callback is returned
        CountDownLatch runAdBiddingPerCAProcessLoggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            runAdBiddingPerCAProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingPerCAProcessReportedStats(any());

        // When the call to runAdBiddingPerCA, and the computation of future is complete,
        FluentFuture<AdBiddingOutcome> result =
                mAdBidGenerator.runAdBiddingPerCA(
                        customAudienceWithFiltersAndAdCounterKeys,
                        mTrustedBiddingDataByBaseUri,
                        EMPTY_AD_SELECTION_SIGNALS,
                        EMPTY_BUYER_SIGNALS,
                        mRunAdBiddingPerCAExecutionLogger);
        AdBiddingOutcome expectedAdBiddingOutcome =
                AdBiddingOutcome.builder()
                        .setAdWithBid(expectedAdsWithBids.get(2))
                        .setCustomAudienceBiddingInfo(mCustomAudienceBiddingInfo)
                        .build();
        runAdBiddingPerCAProcessLoggerLatch.await();
        // Then we can test the result by assertion,
        assertEquals(expectedAdBiddingOutcome, result.get());
        verify(mAdSelectionScriptEngine)
                .generateBids(
                        eq(BUYER_DECISION_LOGIC_JS),
                        eq(expectedAdsWithCounterKeys),
                        eq(EMPTY_AD_SELECTION_SIGNALS),
                        eq(EMPTY_BUYER_SIGNALS),
                        argThat(TRUSTED_BIDDING_SIGNALS_MATCHER),
                        eq(EMPTY_CONTEXTUAL_SIGNALS),
                        eq(mCustomAudienceSignals),
                        eq(mRunAdBiddingPerCAExecutionLogger));
        mMockWebServerRule.verifyMockServerRequests(
                mServer, 1, ImmutableList.of(FETCH_JAVA_SCRIPT_PATH), mRequestMatcherExactMatch);
        verifySuccessAdBiddingPerCALogging(
                customAudienceWithFiltersAndAdCounterKeys.getAds().size(),
                BUYER_DECISION_LOGIC_JS.getBytes().length,
                TRUSTED_BIDDING_KEYS.size(),
                TRUSTED_BIDDING_SIGNALS.toString().getBytes().length);
    }

    @Test
    public void testRunAdBiddingPerCASuccess_v3BiddingLogic() throws Exception {
        // Given we are using a direct executor and mock the returned result from the
        // AdSelectionScriptEngine.generateBids for preparing the test,
        mServer = mMockWebServerRule.startMockWebServer(DEFAULT_DISPATCHER_V3);

        CustomAudienceDevOverridesHelper customAudienceDevOverridesHelper =
                new CustomAudienceDevOverridesHelper(mDevContext, mCustomAudienceDao);
        mAdBidGenerator =
                new AdBidGeneratorImpl(
                        mContext,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mAdSelectionScriptEngine,
                        customAudienceDevOverridesHelper,
                        AD_COUNTER_KEY_COPIER_NO_OP,
                        mFlags,
                        mIsolateSettings,
                        mJsFetcher,
                        mDebugReporting,
                        mDevContext,
                        false);
        when(mAdSelectionScriptEngine.generateBidsV3(
                eq(BUYER_DECISION_LOGIC_JS),
                eq(mCustomAudienceWithAds),
                eq(EMPTY_AD_SELECTION_SIGNALS),
                eq(EMPTY_BUYER_SIGNALS),
                argThat(TRUSTED_BIDDING_SIGNALS_MATCHER),
                eq(EMPTY_CONTEXTUAL_SIGNALS),
                isA(RunAdBiddingPerCAExecutionLogger.class)))
                .thenAnswer(
                        unUsedInvocation -> {
                            mRunAdBiddingPerCAExecutionLogger.startGenerateBids();
                            mRunAdBiddingPerCAExecutionLogger.endGenerateBids();
                            return FluentFuture.from(
                                    Futures.immediateFuture(GENERATE_BIDS_RESPONSE));
                        });
        when(mRunAdBiddingPerCAClockMock.elapsedRealtime())
                .thenReturn(
                        RUN_AD_BIDDING_PER_CA_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_END_TIMESTAMP,
                        RUN_BIDDING_START_TIMESTAMP,
                        GET_TRUSTED_BIDDING_SIGNALS_START_TIMESTAMP,
                        GET_TRUSTED_BIDDING_SIGNALS_END_TIMESTAMP,
                        GENERATE_BIDS_START_TIMESTAMP,
                        GENERATE_BIDS_END_TIMESTAMP,
                        RUN_BIDDING_END_TIMESTAMP,
                        STOP_ELAPSED_TIMESTAMP);
        // Logger calls come after the callback is returned
        CountDownLatch runAdBiddingPerCAProcessLoggerLatch = new CountDownLatch(1);
        doAnswer(
                unusedInvocation -> {
                    runAdBiddingPerCAProcessLoggerLatch.countDown();
                    return null;
                })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingPerCAProcessReportedStats(any());

        // When the call to runAdBiddingPerCA, and the computation of future is complete,
        FluentFuture<AdBiddingOutcome> result =
                mAdBidGenerator.runAdBiddingPerCA(
                        mCustomAudienceWithAds,
                        mTrustedBiddingDataByBaseUri,
                        EMPTY_AD_SELECTION_SIGNALS,
                        EMPTY_BUYER_SIGNALS,
                        mRunAdBiddingPerCAExecutionLogger);
        AdBiddingOutcome expectedAdBiddingOutcome =
                AdBiddingOutcome.builder()
                        .setAdWithBid(AD_WITH_BIDS.get(2))
                        .setCustomAudienceBiddingInfo(mCustomAudienceBiddingInfo)
                        .build();
        runAdBiddingPerCAProcessLoggerLatch.await();
        // Then we can test the result by assertion,
        assertEquals(expectedAdBiddingOutcome, result.get());
        verify(mAdSelectionScriptEngine)
                .generateBidsV3(
                        eq(BUYER_DECISION_LOGIC_JS),
                        eq(mCustomAudienceWithAds),
                        eq(EMPTY_AD_SELECTION_SIGNALS),
                        eq(EMPTY_BUYER_SIGNALS),
                        argThat(TRUSTED_BIDDING_SIGNALS_MATCHER),
                        eq(EMPTY_CONTEXTUAL_SIGNALS),
                        eq(mRunAdBiddingPerCAExecutionLogger));
        mMockWebServerRule.verifyMockServerRequests(
                mServer, 1, ImmutableList.of(FETCH_JAVA_SCRIPT_PATH), mRequestMatcherExactMatch);
        verifySuccessAdBiddingPerCALogging(
                mCustomAudienceWithAds.getAds().size(),
                BUYER_DECISION_LOGIC_JS.getBytes().length,
                TRUSTED_BIDDING_KEYS.size(),
                TRUSTED_BIDDING_SIGNALS.toString().getBytes().length);
    }

    @Test
    public void testRunAdBiddingPerCASuccess_v3BiddingLogic_debugReportingEnabled()
            throws Exception {
        Uri winUri = Uri.parse("http://example.com/reportWin");
        Uri lossUri = Uri.parse("http://example.com/reportLoss");
        // Given we are using a direct executor and mock the returned result from the
        // AdSelectionScriptEngine.generateBids for preparing the test,
        mServer = mMockWebServerRule.startMockWebServer(DEFAULT_DISPATCHER_V3);

        CustomAudienceDevOverridesHelper customAudienceDevOverridesHelper =
                new CustomAudienceDevOverridesHelper(mDevContext, mCustomAudienceDao);
        when(mDebugReporting.isEnabled()).thenReturn(true);
        AdBidGeneratorImpl adBidGenerator =
                new AdBidGeneratorImpl(
                        mContext,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mAdSelectionScriptEngine,
                        customAudienceDevOverridesHelper,
                        AD_COUNTER_KEY_COPIER_NO_OP,
                        mFlags,
                        mIsolateSettings,
                        mJsFetcher,
                        mDebugReporting,
                        mDevContext,
                        false);
        when(mAdSelectionScriptEngine.generateBidsV3(
                eq(BUYER_DECISION_LOGIC_JS),
                eq(mCustomAudienceWithAds),
                eq(EMPTY_AD_SELECTION_SIGNALS),
                eq(EMPTY_BUYER_SIGNALS),
                argThat(TRUSTED_BIDDING_SIGNALS_MATCHER),
                eq(EMPTY_CONTEXTUAL_SIGNALS),
                isA(RunAdBiddingPerCAExecutionLogger.class)))
                .thenAnswer(
                        unUsedInvocation -> {
                            mRunAdBiddingPerCAExecutionLogger.startGenerateBids();
                            mRunAdBiddingPerCAExecutionLogger.endGenerateBids();
                            GenerateBidResult.Builder builder =
                                    GenerateBidResult.builder()
                                            .setWinDebugReportUri(winUri)
                                            .setLossDebugReportUri(lossUri);
                            return FluentFuture.from(
                                    Futures.immediateFuture(
                                            AD_WITH_BIDS.stream()
                                                    .map(
                                                            adWithBid -> builder.setAdWithBid(
                                                                    adWithBid).build())
                                                    .collect(Collectors.toList())));
                        });
        when(mRunAdBiddingPerCAClockMock.elapsedRealtime())
                .thenReturn(
                        RUN_AD_BIDDING_PER_CA_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_END_TIMESTAMP,
                        RUN_BIDDING_START_TIMESTAMP,
                        GET_TRUSTED_BIDDING_SIGNALS_START_TIMESTAMP,
                        GET_TRUSTED_BIDDING_SIGNALS_END_TIMESTAMP,
                        GENERATE_BIDS_START_TIMESTAMP,
                        GENERATE_BIDS_END_TIMESTAMP,
                        RUN_BIDDING_END_TIMESTAMP,
                        STOP_ELAPSED_TIMESTAMP);
        // Logger calls come after the callback is returned
        CountDownLatch runAdBiddingPerCAProcessLoggerLatch = new CountDownLatch(1);
        doAnswer(
                unusedInvocation -> {
                    runAdBiddingPerCAProcessLoggerLatch.countDown();
                    return null;
                })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingPerCAProcessReportedStats(any());

        // When the call to runAdBiddingPerCA, and the computation of future is complete,
        FluentFuture<AdBiddingOutcome> result =
                adBidGenerator.runAdBiddingPerCA(
                        mCustomAudienceWithAds,
                        mTrustedBiddingDataByBaseUri,
                        EMPTY_AD_SELECTION_SIGNALS,
                        EMPTY_BUYER_SIGNALS,
                        mRunAdBiddingPerCAExecutionLogger);
        AdBiddingOutcome expectedAdBiddingOutcome =
                AdBiddingOutcome.builder()
                        .setAdWithBid(AD_WITH_BIDS.get(2))
                        .setCustomAudienceBiddingInfo(mCustomAudienceBiddingInfo)
                        .setDebugReport(
                                DebugReport.builder()
                                        .setCustomAudienceSignals(mCustomAudienceSignals)
                                        .setWinDebugReportUri(winUri)
                                        .setLossDebugReportUri(lossUri)
                                        .build())
                        .build();
        runAdBiddingPerCAProcessLoggerLatch.await();
        // Then we can test the result by assertion,
        assertEquals(expectedAdBiddingOutcome, result.get());
        verify(mAdSelectionScriptEngine)
                .generateBidsV3(
                        eq(BUYER_DECISION_LOGIC_JS),
                        eq(mCustomAudienceWithAds),
                        eq(EMPTY_AD_SELECTION_SIGNALS),
                        eq(EMPTY_BUYER_SIGNALS),
                        argThat(TRUSTED_BIDDING_SIGNALS_MATCHER),
                        eq(EMPTY_CONTEXTUAL_SIGNALS),
                        eq(mRunAdBiddingPerCAExecutionLogger));
        mMockWebServerRule.verifyMockServerRequests(
                mServer, 1, ImmutableList.of(FETCH_JAVA_SCRIPT_PATH), mRequestMatcherExactMatch);
        verifySuccessAdBiddingPerCALogging(
                mCustomAudienceWithAds.getAds().size(),
                BUYER_DECISION_LOGIC_JS.getBytes().length,
                TRUSTED_BIDDING_KEYS.size(),
                TRUSTED_BIDDING_SIGNALS.toString().getBytes().length);
    }

    @Test
    public void testV2BiddingLogicDoesNotAddHeaders_Success() throws Exception {
        Flags jsVersionV2Flags =
                new AdBidGeneratorImplTestFlags() {
                    @Override
                    public long getFledgeAdSelectionBiddingLogicJsVersion() {
                        return 2;
                    }
                };
        final Dispatcher versionHeaderFailsDispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        String headerName =
                                JsVersionHelper.getVersionHeaderName(
                                        JsVersionHelper.JS_PAYLOAD_TYPE_BUYER_BIDDING_LOGIC_JS);
                        if (request.getHeaders().stream()
                                .noneMatch(h -> h.startsWith(headerName))) {
                            return new MockResponse().setBody(BUYER_DECISION_LOGIC_JS);
                        }
                        return new MockResponse().setResponseCode(404);
                    }
                };

        mServer = mMockWebServerRule.startMockWebServer(versionHeaderFailsDispatcher);

        CustomAudienceDevOverridesHelper customAudienceDevOverridesHelper =
                new CustomAudienceDevOverridesHelper(mDevContext, mCustomAudienceDao);
        mAdBidGenerator =
                new AdBidGeneratorImpl(
                        mContext,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mAdSelectionScriptEngine,
                        customAudienceDevOverridesHelper,
                        AD_COUNTER_KEY_COPIER_NO_OP,
                        jsVersionV2Flags,
                        mIsolateSettings,
                        mJsFetcher,
                        mDebugReporting,
                        mDevContext,
                        false);
        when(mAdSelectionScriptEngine.generateBids(
                        eq(BUYER_DECISION_LOGIC_JS),
                        eq(ADS),
                        eq(EMPTY_AD_SELECTION_SIGNALS),
                        eq(EMPTY_BUYER_SIGNALS),
                        argThat(TRUSTED_BIDDING_SIGNALS_MATCHER),
                        eq(EMPTY_CONTEXTUAL_SIGNALS),
                        eq(mCustomAudienceSignals),
                        isA(RunAdBiddingPerCAExecutionLogger.class)))
                .thenAnswer(
                        unUsedInvocation -> {
                            mRunAdBiddingPerCAExecutionLogger.startGenerateBids();
                            mRunAdBiddingPerCAExecutionLogger.endGenerateBids();
                            return FluentFuture.from(
                                    Futures.immediateFuture(GENERATE_BIDS_RESPONSE));
                        });
        when(mRunAdBiddingPerCAClockMock.elapsedRealtime())
                .thenReturn(
                        RUN_AD_BIDDING_PER_CA_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_END_TIMESTAMP,
                        RUN_BIDDING_START_TIMESTAMP,
                        GET_TRUSTED_BIDDING_SIGNALS_START_TIMESTAMP,
                        GET_TRUSTED_BIDDING_SIGNALS_END_TIMESTAMP,
                        GENERATE_BIDS_START_TIMESTAMP,
                        GENERATE_BIDS_END_TIMESTAMP,
                        RUN_BIDDING_END_TIMESTAMP,
                        STOP_ELAPSED_TIMESTAMP);
        // Logger calls come after the callback is returned
        CountDownLatch runAdBiddingPerCAProcessLoggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            runAdBiddingPerCAProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingPerCAProcessReportedStats(any());

        // When the call to runAdBiddingPerCA, and the computation of future is complete,
        FluentFuture<AdBiddingOutcome> result =
                mAdBidGenerator.runAdBiddingPerCA(
                        mCustomAudienceWithAds,
                        mTrustedBiddingDataByBaseUri,
                        EMPTY_AD_SELECTION_SIGNALS,
                        EMPTY_BUYER_SIGNALS,
                        mRunAdBiddingPerCAExecutionLogger);

        AdBiddingOutcome expectedAdBiddingOutcome =
                AdBiddingOutcome.builder()
                        .setAdWithBid(AD_WITH_BIDS.get(2))
                        .setCustomAudienceBiddingInfo(mCustomAudienceBiddingInfo)
                        .build();
        runAdBiddingPerCAProcessLoggerLatch.await();
        // Then we can test the result by assertion,
        assertEquals(expectedAdBiddingOutcome, result.get());
        verify(mAdSelectionScriptEngine)
                .generateBids(
                        eq(BUYER_DECISION_LOGIC_JS),
                        eq(ADS),
                        eq(EMPTY_AD_SELECTION_SIGNALS),
                        eq(EMPTY_BUYER_SIGNALS),
                        argThat(TRUSTED_BIDDING_SIGNALS_MATCHER),
                        eq(EMPTY_CONTEXTUAL_SIGNALS),
                        eq(mCustomAudienceSignals),
                        eq(mRunAdBiddingPerCAExecutionLogger));
        verify(mAdSelectionScriptEngine, times(0))
                .generateBidsV3(any(), any(), any(), any(), any(), any(), any());
        mMockWebServerRule.verifyMockServerRequests(
                mServer, 1, ImmutableList.of(FETCH_JAVA_SCRIPT_PATH), mRequestMatcherExactMatch);
        verifySuccessAdBiddingPerCALogging(
                mCustomAudienceWithAds.getAds().size(),
                BUYER_DECISION_LOGIC_JS.getBytes().length,
                TRUSTED_BIDDING_KEYS.size(),
                TRUSTED_BIDDING_SIGNALS.toString().getBytes().length);
    }

    @Test
    public void testRunAdBiddingPerCASuccess_v3BiddingLogicWithCopier() throws Exception {
        // Given we are using a direct executor and mock the returned result from the
        // AdSelectionScriptEngine.generateBids for preparing the test,
        mServer = mMockWebServerRule.startMockWebServer(DEFAULT_DISPATCHER_V3);

        CustomAudienceDevOverridesHelper customAudienceDevOverridesHelper =
                new CustomAudienceDevOverridesHelper(mDevContext, mCustomAudienceDao);
        mAdBidGenerator =
                new AdBidGeneratorImpl(
                        mContext,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mAdSelectionScriptEngine,
                        customAudienceDevOverridesHelper,
                        AD_COUNTER_KEY_COPIER,
                        mFlags,
                        mIsolateSettings,
                        mJsFetcher,
                        mDebugReporting,
                        mDevContext,
                        false);
        when(mAdSelectionScriptEngine.generateBidsV3(
                        eq(BUYER_DECISION_LOGIC_JS),
                        eq(mCustomAudienceWithAds),
                        eq(EMPTY_AD_SELECTION_SIGNALS),
                        eq(EMPTY_BUYER_SIGNALS),
                        argThat(TRUSTED_BIDDING_SIGNALS_MATCHER),
                        eq(EMPTY_CONTEXTUAL_SIGNALS),
                        isA(RunAdBiddingPerCAExecutionLogger.class)))
                .thenAnswer(
                        unUsedInvocation -> {
                            mRunAdBiddingPerCAExecutionLogger.startGenerateBids();
                            mRunAdBiddingPerCAExecutionLogger.endGenerateBids();
                            return FluentFuture.from(
                                    Futures.immediateFuture(GENERATE_BIDS_RESPONSE));
                        });
        when(mRunAdBiddingPerCAClockMock.elapsedRealtime())
                .thenReturn(
                        RUN_AD_BIDDING_PER_CA_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_END_TIMESTAMP,
                        RUN_BIDDING_START_TIMESTAMP,
                        GET_TRUSTED_BIDDING_SIGNALS_START_TIMESTAMP,
                        GET_TRUSTED_BIDDING_SIGNALS_END_TIMESTAMP,
                        GENERATE_BIDS_START_TIMESTAMP,
                        GENERATE_BIDS_END_TIMESTAMP,
                        RUN_BIDDING_END_TIMESTAMP,
                        STOP_ELAPSED_TIMESTAMP);
        // Logger calls come after the callback is returned
        CountDownLatch runAdBiddingPerCAProcessLoggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            runAdBiddingPerCAProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingPerCAProcessReportedStats(any());

        // When the call to runAdBiddingPerCA, and the computation of future is complete,
        FluentFuture<AdBiddingOutcome> result =
                mAdBidGenerator.runAdBiddingPerCA(
                        mCustomAudienceWithAds,
                        mTrustedBiddingDataByBaseUri,
                        EMPTY_AD_SELECTION_SIGNALS,
                        EMPTY_BUYER_SIGNALS,
                        mRunAdBiddingPerCAExecutionLogger);
        AdBiddingOutcome expectedAdBiddingOutcome =
                AdBiddingOutcome.builder()
                        .setAdWithBid(AD_WITH_BIDS.get(2))
                        .setCustomAudienceBiddingInfo(mCustomAudienceBiddingInfo)
                        .build();
        runAdBiddingPerCAProcessLoggerLatch.await();
        // Then we can test the result by assertion,
        assertEquals(expectedAdBiddingOutcome, result.get());
        verify(mAdSelectionScriptEngine)
                .generateBidsV3(
                        eq(BUYER_DECISION_LOGIC_JS),
                        eq(mCustomAudienceWithAds),
                        eq(EMPTY_AD_SELECTION_SIGNALS),
                        eq(EMPTY_BUYER_SIGNALS),
                        argThat(TRUSTED_BIDDING_SIGNALS_MATCHER),
                        eq(EMPTY_CONTEXTUAL_SIGNALS),
                        eq(mRunAdBiddingPerCAExecutionLogger));
        mMockWebServerRule.verifyMockServerRequests(
                mServer, 1, ImmutableList.of(FETCH_JAVA_SCRIPT_PATH), mRequestMatcherExactMatch);
        verifySuccessAdBiddingPerCALogging(
                mCustomAudienceWithAds.getAds().size(),
                BUYER_DECISION_LOGIC_JS.getBytes().length,
                TRUSTED_BIDDING_KEYS.size(),
                TRUSTED_BIDDING_SIGNALS.toString().getBytes().length);
    }

    @Test
    public void testRunAdBiddingPerCASuccess_v3BiddingLogicWithCopierWithAdCounterKeys()
            throws Exception {
        // Given we are using a direct executor and mock the returned result from the
        // AdSelectionScriptEngine.generateBids for preparing the test,
        mServer = mMockWebServerRule.startMockWebServer(DEFAULT_DISPATCHER_V3);

        DBCustomAudience customAudienceWithAdCounterKeys =
                DBCustomAudienceFixture.getValidBuilderByBuyer(CommonFixture.VALID_BUYER_1)
                        .setBiddingLogicUri(mDecisionLogicUri)
                        .setTrustedBiddingData(mTrustedBiddingData)
                        .build();

        CustomAudienceDevOverridesHelper customAudienceDevOverridesHelper =
                new CustomAudienceDevOverridesHelper(mDevContext, mCustomAudienceDao);
        mAdBidGenerator =
                new AdBidGeneratorImpl(
                        mContext,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mAdSelectionScriptEngine,
                        customAudienceDevOverridesHelper,
                        AD_COUNTER_KEY_COPIER,
                        mFlags,
                        mIsolateSettings,
                        mJsFetcher,
                        mDebugReporting,
                        mDevContext,
                        false);
        when(mAdSelectionScriptEngine.generateBidsV3(
                        eq(BUYER_DECISION_LOGIC_JS),
                        eq(customAudienceWithAdCounterKeys),
                        eq(EMPTY_AD_SELECTION_SIGNALS),
                        eq(EMPTY_BUYER_SIGNALS),
                        argThat(TRUSTED_BIDDING_SIGNALS_MATCHER),
                        eq(EMPTY_CONTEXTUAL_SIGNALS),
                        isA(RunAdBiddingPerCAExecutionLogger.class)))
                .thenAnswer(
                        unUsedInvocation -> {
                            mRunAdBiddingPerCAExecutionLogger.startGenerateBids();
                            mRunAdBiddingPerCAExecutionLogger.endGenerateBids();
                            return FluentFuture.from(
                                    Futures.immediateFuture(GENERATE_BIDS_RESPONSE));
                        });
        when(mRunAdBiddingPerCAClockMock.elapsedRealtime())
                .thenReturn(
                        RUN_AD_BIDDING_PER_CA_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_END_TIMESTAMP,
                        RUN_BIDDING_START_TIMESTAMP,
                        GET_TRUSTED_BIDDING_SIGNALS_START_TIMESTAMP,
                        GET_TRUSTED_BIDDING_SIGNALS_END_TIMESTAMP,
                        GENERATE_BIDS_START_TIMESTAMP,
                        GENERATE_BIDS_END_TIMESTAMP,
                        RUN_BIDDING_END_TIMESTAMP,
                        STOP_ELAPSED_TIMESTAMP);
        // Logger calls come after the callback is returned
        CountDownLatch runAdBiddingPerCAProcessLoggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            runAdBiddingPerCAProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingPerCAProcessReportedStats(any());

        // When the call to runAdBiddingPerCA, and the computation of future is complete,
        FluentFuture<AdBiddingOutcome> result =
                mAdBidGenerator.runAdBiddingPerCA(
                        customAudienceWithAdCounterKeys,
                        mTrustedBiddingDataByBaseUri,
                        EMPTY_AD_SELECTION_SIGNALS,
                        EMPTY_BUYER_SIGNALS,
                        mRunAdBiddingPerCAExecutionLogger);
        AdBiddingOutcome expectedAdBiddingOutcome =
                AdBiddingOutcome.builder()
                        .setAdWithBid(AD_WITH_BIDS.get(2))
                        .setCustomAudienceBiddingInfo(mCustomAudienceBiddingInfo)
                        .build();
        runAdBiddingPerCAProcessLoggerLatch.await();
        // Then we can test the result by assertion,
        assertEquals(expectedAdBiddingOutcome, result.get());
        verify(mAdSelectionScriptEngine)
                .generateBidsV3(
                        eq(BUYER_DECISION_LOGIC_JS),
                        eq(customAudienceWithAdCounterKeys),
                        eq(EMPTY_AD_SELECTION_SIGNALS),
                        eq(EMPTY_BUYER_SIGNALS),
                        argThat(TRUSTED_BIDDING_SIGNALS_MATCHER),
                        eq(EMPTY_CONTEXTUAL_SIGNALS),
                        eq(mRunAdBiddingPerCAExecutionLogger));
        mMockWebServerRule.verifyMockServerRequests(
                mServer, 1, ImmutableList.of(FETCH_JAVA_SCRIPT_PATH), mRequestMatcherExactMatch);
        verifySuccessAdBiddingPerCALogging(
                customAudienceWithAdCounterKeys.getAds().size(),
                BUYER_DECISION_LOGIC_JS.getBytes().length,
                TRUSTED_BIDDING_KEYS.size(),
                TRUSTED_BIDDING_SIGNALS.toString().getBytes().length);
    }

    @Test
    public void testRunAdBiddingPerCAServerReturnTooHighVersionBiddingLogic() throws Exception {
        // Set server to return too high version.
        mServer = mMockWebServerRule.startMockWebServer(DISPATCHER_V3_RETURN_TOO_HIGH_VERSION);

        CustomAudienceDevOverridesHelper customAudienceDevOverridesHelper =
                new CustomAudienceDevOverridesHelper(mDevContext, mCustomAudienceDao);
        mAdBidGenerator =
                new AdBidGeneratorImpl(
                        mContext,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mAdSelectionScriptEngine,
                        customAudienceDevOverridesHelper,
                        AD_COUNTER_KEY_COPIER_NO_OP,
                        mFlags,
                        mIsolateSettings,
                        mJsFetcher,
                        mDebugReporting,
                        mDevContext,
                        false);
        when(mRunAdBiddingPerCAClockMock.elapsedRealtime())
                .thenReturn(
                        RUN_AD_BIDDING_PER_CA_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_END_TIMESTAMP,
                        RUN_BIDDING_START_TIMESTAMP,
                        GET_TRUSTED_BIDDING_SIGNALS_START_TIMESTAMP,
                        GET_TRUSTED_BIDDING_SIGNALS_END_TIMESTAMP,
                        GENERATE_BIDS_START_TIMESTAMP,
                        GENERATE_BIDS_END_TIMESTAMP,
                        RUN_BIDDING_END_TIMESTAMP,
                        STOP_ELAPSED_TIMESTAMP);
        // Logger calls come after the callback is returned
        CountDownLatch runAdBiddingPerCAProcessLoggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            runAdBiddingPerCAProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingPerCAProcessReportedStats(any());

        // When the call to runAdBiddingPerCA, and the computation of future is complete,
        FluentFuture<AdBiddingOutcome> result =
                mAdBidGenerator.runAdBiddingPerCA(
                        mCustomAudienceWithAds,
                        mTrustedBiddingDataByBaseUri,
                        EMPTY_AD_SELECTION_SIGNALS,
                        EMPTY_BUYER_SIGNALS,
                        mRunAdBiddingPerCAExecutionLogger);
        runAdBiddingPerCAProcessLoggerLatch.await();
        ExecutionException e = assertThrows(ExecutionException.class, result::get);
        assertTrue(e.getCause() instanceof IllegalStateException);
        assertEquals(
                String.format(
                        AdBidGeneratorImpl.TOO_HIGH_JS_VERSION,
                        JsVersionRegister.BUYER_BIDDING_LOGIC_VERSION_VERSION_3,
                        JsVersionRegister.BUYER_BIDDING_LOGIC_VERSION_VERSION_3 + 1),
                e.getCause().getMessage());
        // Then we can test the result by assertion,
        mMockWebServerRule.verifyMockServerRequests(
                mServer, 1, ImmutableList.of(FETCH_JAVA_SCRIPT_PATH), mRequestMatcherExactMatch);
        // TODO: missing telemetry verification.
    }

    @Test
    public void testRunAdBiddingPerCAWithOverrideSuccess() throws Exception {
        // Resetting the server with a missing response body, we do not expect any server calls
        mServer = mMockWebServerRule.startMockWebServer(DISPATCHER_EMPTY);

        // Set dev override
        String myAppPackageName = "com.google.ppapi.test";

        DBCustomAudienceOverride dbCustomAudienceOverride =
                DBCustomAudienceOverride.builder()
                        .setOwner(mCustomAudienceWithAds.getOwner())
                        .setBuyer(mCustomAudienceWithAds.getBuyer())
                        .setName(mCustomAudienceWithAds.getName())
                        .setAppPackageName(myAppPackageName)
                        .setBiddingLogicJS(BUYER_DECISION_LOGIC_JS)
                        .setTrustedBiddingData(
                                new JSONObject(TRUSTED_BIDDING_SIGNALS_MAP).toString())
                        .build();
        mCustomAudienceDao.persistCustomAudienceOverride(dbCustomAudienceOverride);

        // Setting dev context to allow overrides
        mDevContext =
                DevContext.builder()
                        .setDevOptionsEnabled(true)
                        .setCallingAppPackageName(myAppPackageName)
                        .build();

        // Resetting adBidGenerator to use the new dev context
        CustomAudienceDevOverridesHelper customAudienceDevOverridesHelper =
                new CustomAudienceDevOverridesHelper(mDevContext, mCustomAudienceDao);
        mAdBidGenerator =
                new AdBidGeneratorImpl(
                        mContext,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mAdSelectionScriptEngine,
                        customAudienceDevOverridesHelper,
                        AD_COUNTER_KEY_COPIER_NO_OP,
                        mFlags,
                        mIsolateSettings,
                        mJsFetcher,
                        mDebugReporting,
                        mDevContext,
                        false);
        // Given we are using a direct executor and mock the returned result from the
        // AdSelectionScriptEngine.generateBids for preparing the test,
        when(mAdSelectionScriptEngine.generateBids(
                        eq(BUYER_DECISION_LOGIC_JS),
                        eq(ADS),
                        eq(EMPTY_AD_SELECTION_SIGNALS),
                        eq(EMPTY_BUYER_SIGNALS),
                        argThat(TRUSTED_BIDDING_SIGNALS_MATCHER),
                        eq(EMPTY_CONTEXTUAL_SIGNALS),
                        eq(mCustomAudienceSignals),
                isA(RunAdBiddingPerCAExecutionLogger.class)))
                .thenAnswer(
                        unUsedInvocation -> {
                            mRunAdBiddingPerCAExecutionLogger.startGenerateBids();
                            mRunAdBiddingPerCAExecutionLogger.endGenerateBids();
                            return FluentFuture.from(
                                    Futures.immediateFuture(GENERATE_BIDS_RESPONSE));
                        });
        when(mRunAdBiddingPerCAClockMock.elapsedRealtime())
                .thenReturn(
                        RUN_AD_BIDDING_PER_CA_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_END_TIMESTAMP,
                        RUN_BIDDING_START_TIMESTAMP,
                        GET_TRUSTED_BIDDING_SIGNALS_START_TIMESTAMP,
                        GET_TRUSTED_BIDDING_SIGNALS_END_TIMESTAMP,
                        GENERATE_BIDS_START_TIMESTAMP,
                        GENERATE_BIDS_END_TIMESTAMP,
                        RUN_BIDDING_END_TIMESTAMP,
                        STOP_ELAPSED_TIMESTAMP);
        // Logger calls come after the callback is returned
        CountDownLatch runAdBiddingPerCAProcessLoggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            runAdBiddingPerCAProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingPerCAProcessReportedStats(any());
        // When the call to runAdBiddingPerCA, and the computation of future is complete,
        FluentFuture<AdBiddingOutcome> result =
                mAdBidGenerator.runAdBiddingPerCA(
                        mCustomAudienceWithAds,
                        mTrustedBiddingDataByBaseUri,
                        EMPTY_AD_SELECTION_SIGNALS,
                        EMPTY_BUYER_SIGNALS,
                        mRunAdBiddingPerCAExecutionLogger);

        AdBiddingOutcome expectedAdBiddingOutcome =
                AdBiddingOutcome.builder()
                        .setAdWithBid(AD_WITH_BIDS.get(2))
                        .setCustomAudienceBiddingInfo(mCustomAudienceBiddingInfo)
                        .build();
        runAdBiddingPerCAProcessLoggerLatch.await();
        // Then we can test the result by assertion,
        assertEquals(expectedAdBiddingOutcome, waitForFuture(() -> result));
        verify(mAdSelectionScriptEngine)
                .generateBids(
                        eq(BUYER_DECISION_LOGIC_JS),
                        eq(ADS),
                        eq(EMPTY_AD_SELECTION_SIGNALS),
                        eq(EMPTY_BUYER_SIGNALS),
                        argThat(TRUSTED_BIDDING_SIGNALS_MATCHER),
                        eq(EMPTY_CONTEXTUAL_SIGNALS),
                        eq(mCustomAudienceSignals),
                        isA(RunAdBiddingPerCAExecutionLogger.class));
        mMockWebServerRule.verifyMockServerRequests(
                mServer, 0, Collections.emptyList(), mRequestMatcherExactMatch);
        verifySuccessAdBiddingPerCALogging(
                mCustomAudienceWithAds.getAds().size(),
                BUYER_DECISION_LOGIC_JS.getBytes().length,
                TRUSTED_BIDDING_KEYS.size(),
                TRUSTED_BIDDING_SIGNALS.toString().getBytes().length);
    }

    @Test
    public void testRunAdBiddingPerCAWithOverrideV3LogicSuccess() throws Exception {
        // Resetting the server with a missing response body, we do not expect any server calls
        mServer = mMockWebServerRule.startMockWebServer(DISPATCHER_EMPTY);

        // Set dev override
        String myAppPackageName = "com.google.ppapi.test";

        DBCustomAudienceOverride dbCustomAudienceOverride =
                DBCustomAudienceOverride.builder()
                        .setOwner(mCustomAudienceWithAds.getOwner())
                        .setBuyer(mCustomAudienceWithAds.getBuyer())
                        .setName(mCustomAudienceWithAds.getName())
                        .setAppPackageName(myAppPackageName)
                        .setBiddingLogicJS(BUYER_DECISION_LOGIC_JS)
                        .setBiddingLogicJsVersion(
                                JsVersionRegister.BUYER_BIDDING_LOGIC_VERSION_VERSION_3)
                        .setTrustedBiddingData(
                                new JSONObject(TRUSTED_BIDDING_SIGNALS_MAP).toString())
                        .build();
        mCustomAudienceDao.persistCustomAudienceOverride(dbCustomAudienceOverride);

        // Setting dev context to allow overrides
        mDevContext =
                DevContext.builder()
                        .setDevOptionsEnabled(true)
                        .setCallingAppPackageName(myAppPackageName)
                        .build();

        // Resetting adBidGenerator to use the new dev context
        CustomAudienceDevOverridesHelper customAudienceDevOverridesHelper =
                new CustomAudienceDevOverridesHelper(mDevContext, mCustomAudienceDao);
        mJsFetcher =
                new JsFetcher(
                        mBackgroundExecutorService,
                        mLightweightExecutorService,
                        mAdServicesHttpsClient,
                        mFlags,
                        mDevContext);
        mAdBidGenerator =
                new AdBidGeneratorImpl(
                        mContext,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mAdSelectionScriptEngine,
                        customAudienceDevOverridesHelper,
                        AD_COUNTER_KEY_COPIER_NO_OP,
                        mFlags,
                        mIsolateSettings,
                        mJsFetcher,
                        mDebugReporting,
                        mDevContext,
                        false);
        // Given we are using a direct executor and mock the returned result from the
        // AdSelectionScriptEngine.generateBids for preparing the test,
        when(mAdSelectionScriptEngine.generateBidsV3(
                eq(BUYER_DECISION_LOGIC_JS),
                eq(mCustomAudienceWithAds),
                eq(EMPTY_AD_SELECTION_SIGNALS),
                eq(EMPTY_BUYER_SIGNALS),
                argThat(TRUSTED_BIDDING_SIGNALS_MATCHER),
                eq(EMPTY_CONTEXTUAL_SIGNALS),
                isA(RunAdBiddingPerCAExecutionLogger.class)))
                .thenAnswer(
                        unUsedInvocation -> {
                            mRunAdBiddingPerCAExecutionLogger.startGenerateBids();
                            mRunAdBiddingPerCAExecutionLogger.endGenerateBids();
                            return FluentFuture.from(
                                    Futures.immediateFuture(GENERATE_BIDS_RESPONSE));
                        });
        when(mRunAdBiddingPerCAClockMock.elapsedRealtime())
                .thenReturn(
                        RUN_AD_BIDDING_PER_CA_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_END_TIMESTAMP,
                        RUN_BIDDING_START_TIMESTAMP,
                        GET_TRUSTED_BIDDING_SIGNALS_START_TIMESTAMP,
                        GET_TRUSTED_BIDDING_SIGNALS_END_TIMESTAMP,
                        GENERATE_BIDS_START_TIMESTAMP,
                        GENERATE_BIDS_END_TIMESTAMP,
                        RUN_BIDDING_END_TIMESTAMP,
                        STOP_ELAPSED_TIMESTAMP);
        // Logger calls come after the callback is returned
        CountDownLatch runAdBiddingPerCAProcessLoggerLatch = new CountDownLatch(1);
        doAnswer(
                unusedInvocation -> {
                    runAdBiddingPerCAProcessLoggerLatch.countDown();
                    return null;
                })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingPerCAProcessReportedStats(any());
        // When the call to runAdBiddingPerCA, and the computation of future is complete,
        FluentFuture<AdBiddingOutcome> result =
                mAdBidGenerator.runAdBiddingPerCA(
                        mCustomAudienceWithAds,
                        mTrustedBiddingDataByBaseUri,
                        EMPTY_AD_SELECTION_SIGNALS,
                        EMPTY_BUYER_SIGNALS,
                        mRunAdBiddingPerCAExecutionLogger);

        AdBiddingOutcome expectedAdBiddingOutcome =
                AdBiddingOutcome.builder()
                        .setAdWithBid(AD_WITH_BIDS.get(2))
                        .setCustomAudienceBiddingInfo(mCustomAudienceBiddingInfo)
                        .build();
        runAdBiddingPerCAProcessLoggerLatch.await();
        // Then we can test the result by assertion,
        assertEquals(expectedAdBiddingOutcome, waitForFuture(() -> result));
        verify(mAdSelectionScriptEngine)
                .generateBidsV3(
                        eq(BUYER_DECISION_LOGIC_JS),
                        eq(mCustomAudienceWithAds),
                        eq(EMPTY_AD_SELECTION_SIGNALS),
                        eq(EMPTY_BUYER_SIGNALS),
                        argThat(TRUSTED_BIDDING_SIGNALS_MATCHER),
                        eq(EMPTY_CONTEXTUAL_SIGNALS),
                        isA(RunAdBiddingPerCAExecutionLogger.class));
        mMockWebServerRule.verifyMockServerRequests(
                mServer, 0, Collections.emptyList(), mRequestMatcherExactMatch);
        verifySuccessAdBiddingPerCALogging(
                mCustomAudienceWithAds.getAds().size(),
                BUYER_DECISION_LOGIC_JS.getBytes().length,
                TRUSTED_BIDDING_KEYS.size(),
                TRUSTED_BIDDING_SIGNALS.toString().getBytes().length);
    }

    @Test
    public void testRunAdBiddingPerCANonPositiveBidResult_preV3BiddingLogic() throws Exception {
        // Given we are using a direct executor and mock the returned result from the
        // AdSelectionScriptEngine.generateBids for preparing the test,
        mServer = mMockWebServerRule.startMockWebServer(DEFAULT_DISPATCHER_PRE_V3);

        CustomAudienceDevOverridesHelper customAudienceDevOverridesHelper =
                new CustomAudienceDevOverridesHelper(mDevContext, mCustomAudienceDao);
        mAdBidGenerator =
                new AdBidGeneratorImpl(
                        mContext,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mAdSelectionScriptEngine,
                        customAudienceDevOverridesHelper,
                        AD_COUNTER_KEY_COPIER_NO_OP,
                        mFlags,
                        mIsolateSettings,
                        mJsFetcher,
                        mDebugReporting,
                        mDevContext,
                        false);
        when(mRunAdBiddingPerCAClockMock.elapsedRealtime())
                .thenReturn(
                        RUN_AD_BIDDING_PER_CA_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_END_TIMESTAMP,
                        RUN_BIDDING_START_TIMESTAMP,
                        GET_TRUSTED_BIDDING_SIGNALS_START_TIMESTAMP,
                        GET_TRUSTED_BIDDING_SIGNALS_END_TIMESTAMP,
                        GENERATE_BIDS_START_TIMESTAMP,
                        GENERATE_BIDS_END_TIMESTAMP,
                        RUN_BIDDING_END_TIMESTAMP,
                        STOP_ELAPSED_TIMESTAMP);
        // Logger calls come after the callback is returned
        CountDownLatch runAdBiddingPerCAProcessLoggerLatch = new CountDownLatch(1);
        doAnswer(
                unusedInvocation -> {
                    runAdBiddingPerCAProcessLoggerLatch.countDown();
                    return null;
                })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingPerCAProcessReportedStats(any());
        when(mAdSelectionScriptEngine.generateBids(
                        eq(BUYER_DECISION_LOGIC_JS),
                        eq(ADS),
                        eq(EMPTY_AD_SELECTION_SIGNALS),
                        eq(EMPTY_BUYER_SIGNALS),
                        argThat(TRUSTED_BIDDING_SIGNALS_MATCHER),
                        eq(EMPTY_CONTEXTUAL_SIGNALS),
                        eq(mCustomAudienceSignals),
                        isA(RunAdBiddingPerCAExecutionLogger.class)))
                .thenAnswer(
                        unUsedInvocation -> {
                            mRunAdBiddingPerCAExecutionLogger.startGenerateBids();
                            mRunAdBiddingPerCAExecutionLogger.endGenerateBids();
                            return FluentFuture.from(
                                    Futures.immediateFuture(GENERATE_NON_POSITIVE_BIDS_RESPONSE));
                        });
        // When the call to runAdBiddingPerCA, and the computation of future is complete,
        FluentFuture<AdBiddingOutcome> result =
                mAdBidGenerator.runAdBiddingPerCA(
                        mCustomAudienceWithAds,
                        mTrustedBiddingDataByBaseUri,
                        EMPTY_AD_SELECTION_SIGNALS,
                        EMPTY_BUYER_SIGNALS,
                        mRunAdBiddingPerCAExecutionLogger);
        runAdBiddingPerCAProcessLoggerLatch.await();
        // Then we can test the result by assertion,
        assertNull(result.get());
        verify(mAdSelectionScriptEngine)
                .generateBids(
                        eq(BUYER_DECISION_LOGIC_JS),
                        eq(ADS),
                        eq(EMPTY_AD_SELECTION_SIGNALS),
                        eq(EMPTY_BUYER_SIGNALS),
                        argThat(TRUSTED_BIDDING_SIGNALS_MATCHER),
                        eq(EMPTY_CONTEXTUAL_SIGNALS),
                        eq(mCustomAudienceSignals),
                        isA(RunAdBiddingPerCAExecutionLogger.class));
        mMockWebServerRule.verifyMockServerRequests(
                mServer, 1, ImmutableList.of(FETCH_JAVA_SCRIPT_PATH), mRequestMatcherExactMatch);
        verifySuccessAdBiddingPerCALogging(
                mCustomAudienceWithAds.getAds().size(),
                BUYER_DECISION_LOGIC_JS.getBytes().length,
                TRUSTED_BIDDING_KEYS.size(),
                TRUSTED_BIDDING_SIGNALS.toString().getBytes().length);
    }

    @Test
    public void testRunAdBiddingPerCANonPositiveBidResult_v3BiddingLogic() throws Exception {
        // Given we are using a direct executor and mock the returned result from the
        // AdSelectionScriptEngine.generateBids for preparing the test,
        mServer = mMockWebServerRule.startMockWebServer(DEFAULT_DISPATCHER_V3);

        CustomAudienceDevOverridesHelper customAudienceDevOverridesHelper =
                new CustomAudienceDevOverridesHelper(mDevContext, mCustomAudienceDao);
        mAdBidGenerator =
                new AdBidGeneratorImpl(
                        mContext,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mAdSelectionScriptEngine,
                        customAudienceDevOverridesHelper,
                        AD_COUNTER_KEY_COPIER_NO_OP,
                        mFlags,
                        mIsolateSettings,
                        mJsFetcher,
                        mDebugReporting,
                        mDevContext,
                        false);
        when(mRunAdBiddingPerCAClockMock.elapsedRealtime())
                .thenReturn(
                        RUN_AD_BIDDING_PER_CA_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_END_TIMESTAMP,
                        RUN_BIDDING_START_TIMESTAMP,
                        GET_TRUSTED_BIDDING_SIGNALS_START_TIMESTAMP,
                        GET_TRUSTED_BIDDING_SIGNALS_END_TIMESTAMP,
                        GENERATE_BIDS_START_TIMESTAMP,
                        GENERATE_BIDS_END_TIMESTAMP,
                        RUN_BIDDING_END_TIMESTAMP,
                        STOP_ELAPSED_TIMESTAMP);
        // Logger calls come after the callback is returned
        CountDownLatch runAdBiddingPerCAProcessLoggerLatch = new CountDownLatch(1);
        doAnswer(
                unusedInvocation -> {
                    runAdBiddingPerCAProcessLoggerLatch.countDown();
                    return null;
                })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingPerCAProcessReportedStats(any());
        when(mAdSelectionScriptEngine.generateBidsV3(
                        eq(BUYER_DECISION_LOGIC_JS),
                        eq(mCustomAudienceWithAds),
                        eq(EMPTY_AD_SELECTION_SIGNALS),
                        eq(EMPTY_BUYER_SIGNALS),
                        argThat(TRUSTED_BIDDING_SIGNALS_MATCHER),
                        eq(EMPTY_CONTEXTUAL_SIGNALS),
                        isA(RunAdBiddingPerCAExecutionLogger.class)))
                .thenAnswer(
                        unUsedInvocation -> {
                            mRunAdBiddingPerCAExecutionLogger.startGenerateBids();
                            mRunAdBiddingPerCAExecutionLogger.endGenerateBids();
                            return FluentFuture.from(
                                    Futures.immediateFuture(GENERATE_NON_POSITIVE_BIDS_RESPONSE));
                        });
        // When the call to runAdBiddingPerCA, and the computation of future is complete,
        FluentFuture<AdBiddingOutcome> result =
                mAdBidGenerator.runAdBiddingPerCA(
                        mCustomAudienceWithAds,
                        mTrustedBiddingDataByBaseUri,
                        EMPTY_AD_SELECTION_SIGNALS,
                        EMPTY_BUYER_SIGNALS,
                        mRunAdBiddingPerCAExecutionLogger);
        runAdBiddingPerCAProcessLoggerLatch.await();
        // Then we can test the result by assertion,
        assertNull(result.get());
        verify(mAdSelectionScriptEngine)
                .generateBidsV3(
                        eq(BUYER_DECISION_LOGIC_JS),
                        eq(mCustomAudienceWithAds),
                        eq(EMPTY_AD_SELECTION_SIGNALS),
                        eq(EMPTY_BUYER_SIGNALS),
                        argThat(TRUSTED_BIDDING_SIGNALS_MATCHER),
                        eq(EMPTY_CONTEXTUAL_SIGNALS),
                        isA(RunAdBiddingPerCAExecutionLogger.class));
        mMockWebServerRule.verifyMockServerRequests(
                mServer, 1, ImmutableList.of(FETCH_JAVA_SCRIPT_PATH), mRequestMatcherExactMatch);
        verifySuccessAdBiddingPerCALogging(
                mCustomAudienceWithAds.getAds().size(),
                BUYER_DECISION_LOGIC_JS.getBytes().length,
                TRUSTED_BIDDING_KEYS.size(),
                TRUSTED_BIDDING_SIGNALS.toString().getBytes().length);
    }

    @Test
    // TODO(b/290846406): Remove ignore annotation once test is fixed and verified
    @Ignore("b/290846406")
    public void testRunAdBiddingPerCABiddingTimesOut() throws Exception {
        // Given we are using a direct executor and mock the returned result from the
        // AdSelectionScriptEngine.generateBids for preparing the test,
        mServer = mMockWebServerRule.startMockWebServer(DEFAULT_DISPATCHER_PRE_V3);

        CustomAudienceDevOverridesHelper customAudienceDevOverridesHelper =
                new CustomAudienceDevOverridesHelper(mDevContext, mCustomAudienceDao);
        Flags flagsWithSmallerLimits =
                new Flags() {
                    @Override
                    public long getAdSelectionBiddingTimeoutPerCaMs() {
                        return 100;
                    }
                };
        mAdBidGenerator =
                new AdBidGeneratorImpl(
                        mContext,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mAdSelectionScriptEngine,
                        customAudienceDevOverridesHelper,
                        AD_COUNTER_KEY_COPIER_NO_OP,
                        flagsWithSmallerLimits,
                        mIsolateSettings,
                        mJsFetcher,
                        mDebugReporting,
                        mDevContext,
                        false);
        when(mRunAdBiddingPerCAClockMock.elapsedRealtime())
                .thenReturn(
                        RUN_AD_BIDDING_PER_CA_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_END_TIMESTAMP,
                        RUN_BIDDING_START_TIMESTAMP,
                        GET_TRUSTED_BIDDING_SIGNALS_START_TIMESTAMP,
                        GET_TRUSTED_BIDDING_SIGNALS_END_TIMESTAMP,
                        GENERATE_BIDS_START_TIMESTAMP,
                        GENERATE_BIDS_END_TIMESTAMP,
                        RUN_BIDDING_END_TIMESTAMP,
                        STOP_ELAPSED_TIMESTAMP);
        // Logger calls come after the callback is returned
        CountDownLatch runAdBiddingPerCAProcessLoggerLatch = new CountDownLatch(1);
        doAnswer(
                unusedInvocation -> {
                    runAdBiddingPerCAProcessLoggerLatch.countDown();
                    return null;
                })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingPerCAProcessReportedStats(any());
        when(mAdSelectionScriptEngine.generateBids(
                eq(BUYER_DECISION_LOGIC_JS),
                eq(ADS),
                eq(EMPTY_AD_SELECTION_SIGNALS),
                eq(EMPTY_BUYER_SIGNALS),
                argThat(TRUSTED_BIDDING_SIGNALS_MATCHER),
                eq(EMPTY_CONTEXTUAL_SIGNALS),
                eq(mCustomAudienceSignals),
                isA(RunAdBiddingPerCAExecutionLogger.class)))
                .thenAnswer((invocation) -> generateBidsWithDelay(flagsWithSmallerLimits));
        // When the call to runAdBiddingPerCA, and the computation of future is complete,
        FluentFuture<AdBiddingOutcome> result =
                mAdBidGenerator.runAdBiddingPerCA(
                        mCustomAudienceWithAds,
                        mTrustedBiddingDataByBaseUri,
                        EMPTY_AD_SELECTION_SIGNALS,
                        EMPTY_BUYER_SIGNALS,
                        mRunAdBiddingPerCAExecutionLogger);
        runAdBiddingPerCAProcessLoggerLatch.await();
        // Then we can test the result by assertion
        ExecutionException thrown = assertThrows(ExecutionException.class, result::get);
        assertTrue(thrown.getMessage().contains(BIDDING_TIMED_OUT));
        verify(mAdSelectionScriptEngine)
                .generateBids(
                        eq(BUYER_DECISION_LOGIC_JS),
                        eq(ADS),
                        eq(EMPTY_AD_SELECTION_SIGNALS),
                        eq(EMPTY_BUYER_SIGNALS),
                        argThat(TRUSTED_BIDDING_SIGNALS_MATCHER),
                        eq(EMPTY_CONTEXTUAL_SIGNALS),
                        eq(mCustomAudienceSignals),
                        isA(RunAdBiddingPerCAExecutionLogger.class));
        mMockWebServerRule.verifyMockServerRequests(
                mServer, 1, ImmutableList.of(FETCH_JAVA_SCRIPT_PATH), mRequestMatcherExactMatch);
        verifyFailedRunAdBiddingPerCALoggingTimeoutException(
                mCustomAudienceWithAds.getAds().size(),
                AdServicesLoggerUtil.getResultCodeFromException(thrown.getCause()));
    }

    @Test
    public void testRunAdBiddingPerCAThrowsException_preV3BiddingLogic() throws Exception {
        // Given we are using a direct executor and mock the returned result from the
        // AdSelectionScriptEngine.generateBids for preparing the test,
        mServer = mMockWebServerRule.startMockWebServer(DEFAULT_DISPATCHER_PRE_V3);

        CustomAudienceDevOverridesHelper customAudienceDevOverridesHelper =
                new CustomAudienceDevOverridesHelper(mDevContext, mCustomAudienceDao);
        mAdBidGenerator =
                new AdBidGeneratorImpl(
                        mContext,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mAdSelectionScriptEngine,
                        customAudienceDevOverridesHelper,
                        AD_COUNTER_KEY_COPIER_NO_OP,
                        mFlags,
                        mIsolateSettings,
                        mJsFetcher,
                        mDebugReporting,
                        mDevContext,
                        false);
        when(mRunAdBiddingPerCAClockMock.elapsedRealtime())
                .thenReturn(
                        RUN_AD_BIDDING_PER_CA_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_END_TIMESTAMP,
                        RUN_BIDDING_START_TIMESTAMP,
                        GET_TRUSTED_BIDDING_SIGNALS_START_TIMESTAMP,
                        GET_TRUSTED_BIDDING_SIGNALS_END_TIMESTAMP,
                        STOP_ELAPSED_TIMESTAMP);
        // Logger calls come after the callback is returned
        CountDownLatch runAdBiddingPerCAProcessLoggerLatch = new CountDownLatch(1);
        doAnswer(
                unusedInvocation -> {
                    runAdBiddingPerCAProcessLoggerLatch.countDown();
                    return null;
                })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingPerCAProcessReportedStats(any());
        JSONException jsonException = new JSONException(JSON_EXCEPTION_MESSAGE);
        when(mAdSelectionScriptEngine.generateBids(
                eq(BUYER_DECISION_LOGIC_JS),
                eq(ADS),
                eq(EMPTY_AD_SELECTION_SIGNALS),
                eq(EMPTY_BUYER_SIGNALS),
                argThat(TRUSTED_BIDDING_SIGNALS_MATCHER),
                eq(EMPTY_CONTEXTUAL_SIGNALS),
                eq(mCustomAudienceSignals),
                isA(RunAdBiddingPerCAExecutionLogger.class)))
                .thenThrow(jsonException);
        // When the call to runBidding, and the computation of future is complete.
        FluentFuture<AdBiddingOutcome> result =
                mAdBidGenerator.runAdBiddingPerCA(
                        mCustomAudienceWithAds,
                        mTrustedBiddingDataByBaseUri,
                        EMPTY_AD_SELECTION_SIGNALS,
                        EMPTY_BUYER_SIGNALS,
                        mRunAdBiddingPerCAExecutionLogger);
        runAdBiddingPerCAProcessLoggerLatch.await();
        ExecutionException outException = assertThrows(ExecutionException.class, result::get);
        assertThat(outException.getMessage()).contains(JSON_EXCEPTION_MESSAGE);
        verifyFailedRunAdBiddingPerCALoggingByGenerateBids(
                mCustomAudienceWithAds.getAds().size(),
                BUYER_DECISION_LOGIC_JS.getBytes().length,
                TRUSTED_BIDDING_KEYS.size(),
                TRUSTED_BIDDING_SIGNALS.toString().getBytes().length,
                AdServicesLoggerUtil.getResultCodeFromException(outException.getCause()));
    }

    @Test
    public void testRunAdBiddingPerCAThrowsException_v3BiddingLogic() throws Exception {
        // Given we are using a direct executor and mock the returned result from the
        // AdSelectionScriptEngine.generateBids for preparing the test,
        mServer = mMockWebServerRule.startMockWebServer(DEFAULT_DISPATCHER_V3);

        CustomAudienceDevOverridesHelper customAudienceDevOverridesHelper =
                new CustomAudienceDevOverridesHelper(mDevContext, mCustomAudienceDao);
        mAdBidGenerator =
                new AdBidGeneratorImpl(
                        mContext,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mAdSelectionScriptEngine,
                        customAudienceDevOverridesHelper,
                        AD_COUNTER_KEY_COPIER_NO_OP,
                        mFlags,
                        mIsolateSettings,
                        mJsFetcher,
                        mDebugReporting,
                        mDevContext,
                        false);
        when(mRunAdBiddingPerCAClockMock.elapsedRealtime())
                .thenReturn(
                        RUN_AD_BIDDING_PER_CA_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_END_TIMESTAMP,
                        RUN_BIDDING_START_TIMESTAMP,
                        GET_TRUSTED_BIDDING_SIGNALS_START_TIMESTAMP,
                        GET_TRUSTED_BIDDING_SIGNALS_END_TIMESTAMP,
                        STOP_ELAPSED_TIMESTAMP);
        // Logger calls come after the callback is returned
        CountDownLatch runAdBiddingPerCAProcessLoggerLatch = new CountDownLatch(1);
        doAnswer(
                unusedInvocation -> {
                    runAdBiddingPerCAProcessLoggerLatch.countDown();
                    return null;
                })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingPerCAProcessReportedStats(any());
        JSONException jsonException = new JSONException(JSON_EXCEPTION_MESSAGE);
        when(mAdSelectionScriptEngine.generateBidsV3(
                        eq(BUYER_DECISION_LOGIC_JS),
                        eq(mCustomAudienceWithAds),
                        eq(EMPTY_AD_SELECTION_SIGNALS),
                        eq(EMPTY_BUYER_SIGNALS),
                        argThat(TRUSTED_BIDDING_SIGNALS_MATCHER),
                        eq(EMPTY_CONTEXTUAL_SIGNALS),
                        isA(RunAdBiddingPerCAExecutionLogger.class)))
                .thenThrow(jsonException);
        // When the call to runBidding, and the computation of future is complete.
        FluentFuture<AdBiddingOutcome> result =
                mAdBidGenerator.runAdBiddingPerCA(
                        mCustomAudienceWithAds,
                        mTrustedBiddingDataByBaseUri,
                        EMPTY_AD_SELECTION_SIGNALS,
                        EMPTY_BUYER_SIGNALS,
                        mRunAdBiddingPerCAExecutionLogger);
        runAdBiddingPerCAProcessLoggerLatch.await();
        ExecutionException outException = assertThrows(ExecutionException.class, result::get);
        assertThat(outException.getMessage()).contains(JSON_EXCEPTION_MESSAGE);
        verifyFailedRunAdBiddingPerCALoggingByGenerateBids(
                mCustomAudienceWithAds.getAds().size(),
                BUYER_DECISION_LOGIC_JS.getBytes().length,
                TRUSTED_BIDDING_KEYS.size(),
                TRUSTED_BIDDING_SIGNALS.toString().getBytes().length,
                AdServicesLoggerUtil.getResultCodeFromException(outException.getCause()));
    }

    @Test
    public void testTrustedSignalsEmptyKeysSuccess() throws Exception {
        // Given we are using a direct executor and mock the returned result from the
        // AdSelectionScriptEngine.generateBids for preparing the test,

        // In case there are no keys, the server will send empty json
        // Given this transaction is opaque to our logic this is a valid response
        // Missing server connection for trusted signals
        Dispatcher dispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        switch (request.getPath()) {
                            case FETCH_JAVA_SCRIPT_PATH:
                                return new MockResponse().setBody(BUYER_DECISION_LOGIC_JS);
                            default:
                                return new MockResponse().setResponseCode(404);
                        }
                    }
                };
        mServer = mMockWebServerRule.startMockWebServer(dispatcher);
        CustomAudienceDevOverridesHelper customAudienceDevOverridesHelper =
                new CustomAudienceDevOverridesHelper(mDevContext, mCustomAudienceDao);
        List<String> emptyTrustedBiddingKeys = Collections.EMPTY_LIST;
        DBTrustedBiddingData trustedBiddingData =
                new DBTrustedBiddingData.Builder()
                        .setKeys(emptyTrustedBiddingKeys)
                        .setUri(mTrustedBiddingUri)
                        .build();

        DBCustomAudience customAudienceWithAds =
                DBCustomAudienceFixture.getValidBuilderByBuyerNoFilters(CommonFixture.VALID_BUYER_1)
                        .setBiddingLogicUri(mDecisionLogicUri)
                        .setTrustedBiddingData(trustedBiddingData)
                        .build();

        CustomAudienceSignals customAudienceSignals =
                CustomAudienceSignals.buildFromCustomAudience(customAudienceWithAds);

        CustomAudienceBiddingInfo customAudienceBiddingInfo =
                CustomAudienceBiddingInfo.create(
                        mDecisionLogicUri, BUYER_DECISION_LOGIC_JS, customAudienceSignals, null);

        mAdBidGenerator =
                new AdBidGeneratorImpl(
                        mContext,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mAdSelectionScriptEngine,
                        customAudienceDevOverridesHelper,
                        AD_COUNTER_KEY_COPIER_NO_OP,
                        mFlags,
                        mIsolateSettings,
                        mJsFetcher,
                        mDebugReporting,
                        mDevContext,
                        false);
        when(mRunAdBiddingPerCAClockMock.elapsedRealtime())
                .thenReturn(
                        RUN_AD_BIDDING_PER_CA_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_END_TIMESTAMP,
                        RUN_BIDDING_START_TIMESTAMP,
                        GET_TRUSTED_BIDDING_SIGNALS_START_TIMESTAMP,
                        GET_TRUSTED_BIDDING_SIGNALS_END_TIMESTAMP,
                        GENERATE_BIDS_START_TIMESTAMP,
                        GENERATE_BIDS_END_TIMESTAMP,
                        RUN_BIDDING_END_TIMESTAMP,
                        STOP_ELAPSED_TIMESTAMP);
        // Logger calls come after the callback is returned
        CountDownLatch runAdBiddingPerCAProcessLoggerLatch = new CountDownLatch(1);
        doAnswer(
                unusedInvocation -> {
                    runAdBiddingPerCAProcessLoggerLatch.countDown();
                    return null;
                })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingPerCAProcessReportedStats(any());
        when(mAdSelectionScriptEngine.generateBids(
                BUYER_DECISION_LOGIC_JS,
                ADS,
                EMPTY_AD_SELECTION_SIGNALS,
                EMPTY_BUYER_SIGNALS,
                AdSelectionSignals.EMPTY,
                EMPTY_CONTEXTUAL_SIGNALS,
                customAudienceSignals,
                mRunAdBiddingPerCAExecutionLogger))
                .thenAnswer(
                        unusedInvocation -> {
                            mRunAdBiddingPerCAExecutionLogger.startGenerateBids();
                            mRunAdBiddingPerCAExecutionLogger.endGenerateBids();
                            return FluentFuture.from(
                                    Futures.immediateFuture(GENERATE_BIDS_RESPONSE));
                        });
        // When the call to runAdBiddingPerCA, and the computation of future is complete,
        FluentFuture<AdBiddingOutcome> result =
                mAdBidGenerator.runAdBiddingPerCA(
                        customAudienceWithAds,
                        mTrustedBiddingDataByBaseUri,
                        EMPTY_AD_SELECTION_SIGNALS,
                        EMPTY_BUYER_SIGNALS,
                        mRunAdBiddingPerCAExecutionLogger);
        runAdBiddingPerCAProcessLoggerLatch.await();
        AdBiddingOutcome expectedAdBiddingOutcome =
                AdBiddingOutcome.builder()
                        .setAdWithBid(AD_WITH_BIDS.get(2))
                        .setCustomAudienceBiddingInfo(customAudienceBiddingInfo)
                        .build();
        // Then we can test the result by assertion,
        assertEquals(expectedAdBiddingOutcome, result.get());
        verify(mAdSelectionScriptEngine)
                .generateBids(
                        BUYER_DECISION_LOGIC_JS,
                        ADS,
                        EMPTY_AD_SELECTION_SIGNALS,
                        EMPTY_BUYER_SIGNALS,
                        AdSelectionSignals.EMPTY,
                        EMPTY_CONTEXTUAL_SIGNALS,
                        customAudienceSignals,
                        mRunAdBiddingPerCAExecutionLogger);
        mMockWebServerRule.verifyMockServerRequests(
                mServer, 1, ImmutableList.of(FETCH_JAVA_SCRIPT_PATH), mRequestMatcherExactMatch);
        verifySuccessAdBiddingPerCALogging(
                mCustomAudienceWithAds.getAds().size(),
                BUYER_DECISION_LOGIC_JS.getBytes().length,
                emptyTrustedBiddingKeys.size(),
                AdSelectionSignals.EMPTY.toString().getBytes().length);
    }

    @Test
    public void testMissingBiddingLogicException() throws Exception {
        // Given we are using a direct executor and mock the returned result from the
        // AdSelectionScriptEngine.generateBids for preparing the test,

        // Send error response for JS logic fetch
        mServer = mMockWebServerRule.startMockWebServer(DISPATCHER_EMPTY);
        IllegalStateException missingJSLogicException =
                new IllegalStateException(JsFetcher.MISSING_BIDDING_LOGIC);

        CustomAudienceDevOverridesHelper customAudienceDevOverridesHelper =
                new CustomAudienceDevOverridesHelper(mDevContext, mCustomAudienceDao);
        mAdBidGenerator =
                new AdBidGeneratorImpl(
                        mContext,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mAdSelectionScriptEngine,
                        customAudienceDevOverridesHelper,
                        AD_COUNTER_KEY_COPIER_NO_OP,
                        mFlags,
                        mIsolateSettings,
                        mJsFetcher,
                        mDebugReporting,
                        mDevContext,
                        false);
        when(mRunAdBiddingPerCAClockMock.elapsedRealtime())
                .thenReturn(
                        RUN_AD_BIDDING_PER_CA_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_START_TIMESTAMP,
                        STOP_ELAPSED_TIMESTAMP);
        // Logger calls come after the callback is returned
        CountDownLatch runAdBiddingPerCAProcessLoggerLatch = new CountDownLatch(1);
        doAnswer(
                unusedInvocation -> {
                    runAdBiddingPerCAProcessLoggerLatch.countDown();
                    return null;
                })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingPerCAProcessReportedStats(any());
        // When the call to runAdBiddingPerCA, and the computation of future is complete,
        FluentFuture<AdBiddingOutcome> result =
                mAdBidGenerator.runAdBiddingPerCA(
                        mCustomAudienceWithAds,
                        mTrustedBiddingDataByBaseUri,
                        EMPTY_AD_SELECTION_SIGNALS,
                        EMPTY_BUYER_SIGNALS,
                        mRunAdBiddingPerCAExecutionLogger);
        runAdBiddingPerCAProcessLoggerLatch.await();
        ExecutionException outException = assertThrows(ExecutionException.class, result::get);
        assertEquals(outException.getCause().getMessage(), missingJSLogicException.getMessage());
        mMockWebServerRule.verifyMockServerRequests(
                mServer, 1, ImmutableList.of(FETCH_JAVA_SCRIPT_PATH), mRequestMatcherExactMatch);
        verifyFailedRunAdBiddingPerCALoggingGetBuyerBiddingJs(
                mCustomAudienceWithAds.getAds().size(),
                AdServicesLoggerUtil.getResultCodeFromException(outException.getCause()));
    }

    @Test
    public void testMissingTrustedSignalsException() throws Exception {
        // Given we are using a direct executor and mock the returned result from the
        // AdSelectionScriptEngine.generateBids for preparing the test,

        // Missing server connection for trusted signals
        mServer = mMockWebServerRule.startMockWebServer(DEFAULT_DISPATCHER_V3);
        IllegalStateException missingSignalsException =
                new IllegalStateException(MISSING_TRUSTED_BIDDING_SIGNALS);

        CustomAudienceDevOverridesHelper customAudienceDevOverridesHelper =
                new CustomAudienceDevOverridesHelper(mDevContext, mCustomAudienceDao);
        mAdBidGenerator =
                new AdBidGeneratorImpl(
                        mContext,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mAdSelectionScriptEngine,
                        customAudienceDevOverridesHelper,
                        AD_COUNTER_KEY_COPIER_NO_OP,
                        mFlags,
                        mIsolateSettings,
                        mJsFetcher,
                        mDebugReporting,
                        mDevContext,
                        false);
        when(mRunAdBiddingPerCAClockMock.elapsedRealtime())
                .thenReturn(
                        RUN_AD_BIDDING_PER_CA_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_START_TIMESTAMP,
                        GET_BUYER_DECISION_LOGIC_END_TIMESTAMP,
                        RUN_BIDDING_START_TIMESTAMP,
                        GET_TRUSTED_BIDDING_SIGNALS_START_TIMESTAMP,
                        STOP_ELAPSED_TIMESTAMP);
        // Logger calls come after the callback is returned
        CountDownLatch runAdBiddingPerCAProcessLoggerLatch = new CountDownLatch(1);
        doAnswer(
                unusedInvocation -> {
                    runAdBiddingPerCAProcessLoggerLatch.countDown();
                    return null;
                })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingPerCAProcessReportedStats(any());
        // When the call to runAdBiddingPerCA, and the computation of future is complete,
        FluentFuture<AdBiddingOutcome> result =
                mAdBidGenerator.runAdBiddingPerCA(
                        mCustomAudienceWithAds,
                        ImmutableMap.of(),
                        EMPTY_AD_SELECTION_SIGNALS,
                        EMPTY_BUYER_SIGNALS,
                        mRunAdBiddingPerCAExecutionLogger);
        runAdBiddingPerCAProcessLoggerLatch.await();
        ExecutionException outException = assertThrows(ExecutionException.class, result::get);
        assertEquals(outException.getCause().getMessage(), missingSignalsException.getMessage());
        mMockWebServerRule.verifyMockServerRequests(
                mServer, 1, ImmutableList.of(FETCH_JAVA_SCRIPT_PATH), mRequestMatcherExactMatch);
        verifyFailedRunAdBiddingPerCALoggingTrustedBiddingSignals(
                mCustomAudienceWithAds.getAds().size(),
                BUYER_DECISION_LOGIC_JS.getBytes().length,
                mCustomAudienceWithAds.getTrustedBiddingData().getKeys().size(),
                AdServicesLoggerUtil.getResultCodeFromException(outException.getCause()));
    }

    @Test
    public void testRunAdBiddingPerCAEmptyAds() throws Exception {
        mServer = mMockWebServerRule.startMockWebServer(DISPATCHER_EMPTY);

        CustomAudienceDevOverridesHelper customAudienceDevOverridesHelper =
                new CustomAudienceDevOverridesHelper(mDevContext, mCustomAudienceDao);
        mAdBidGenerator =
                new AdBidGeneratorImpl(
                        mContext,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mAdSelectionScriptEngine,
                        customAudienceDevOverridesHelper,
                        AD_COUNTER_KEY_COPIER_NO_OP,
                        mFlags,
                        mIsolateSettings,
                        mJsFetcher,
                        mDebugReporting,
                        mDevContext,
                        false);
        when(mRunAdBiddingPerCAClockMock.elapsedRealtime())
                .thenReturn(RUN_AD_BIDDING_PER_CA_START_TIMESTAMP, STOP_ELAPSED_TIMESTAMP);
        // Logger calls come after the callback is returned
        CountDownLatch runAdBiddingPerCAProcessLoggerLatch = new CountDownLatch(1);
        doAnswer(
                unusedInvocation -> {
                    runAdBiddingPerCAProcessLoggerLatch.countDown();
                    return null;
                })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingPerCAProcessReportedStats(any());
        FluentFuture<AdBiddingOutcome> result =
                mAdBidGenerator.runAdBiddingPerCA(
                        CUSTOM_AUDIENCE_WITH_EMPTY_ADS,
                        mTrustedBiddingDataByBaseUri,
                        EMPTY_AD_SELECTION_SIGNALS,
                        EMPTY_BUYER_SIGNALS,
                        mRunAdBiddingPerCAExecutionLogger);
        runAdBiddingPerCAProcessLoggerLatch.await();
        // The result is an early return with a FluentFuture of Null, after checking the Ads list is
        // empty.
        assertNull(result.get());
        verifyFailedRunAdBiddingEmptyAds(STATUS_INTERNAL_ERROR);
    }

    private void verifyFailedRunAdBiddingEmptyAds(int resultCode) {
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingPerCAProcessReportedStats(
                        mRunAdBiddingPerCAProcessReportedStatsArgumentCaptor.capture());
        RunAdBiddingPerCAProcessReportedStats runAdBiddingPerCAProcessReportedStats =
                mRunAdBiddingPerCAProcessReportedStatsArgumentCaptor.getValue();
        assertThat(runAdBiddingPerCAProcessReportedStats.getNumOfAdsForBidding()).isEqualTo(0);
        assertThat(runAdBiddingPerCAProcessReportedStats.getRunAdBiddingPerCaLatencyInMillis())
                .isEqualTo(RUN_AD_BIDDING_PER_CA_LATENCY_IN_MS);
        assertThat(runAdBiddingPerCAProcessReportedStats.getRunAdBiddingPerCaResultCode())
                .isEqualTo(resultCode);
        assertThat(runAdBiddingPerCAProcessReportedStats.getGetBuyerDecisionLogicLatencyInMillis())
                .isEqualTo(STATUS_UNSET);
        assertThat(runAdBiddingPerCAProcessReportedStats.getGetBuyerDecisionLogicResultCode())
                .isEqualTo(STATUS_UNSET);
        assertThat(runAdBiddingPerCAProcessReportedStats.getBuyerDecisionLogicScriptType())
                .isEqualTo(SCRIPT_UNSET);
        assertThat(
                runAdBiddingPerCAProcessReportedStats
                        .getFetchedBuyerDecisionLogicScriptSizeInBytes())
                .isEqualTo(STATUS_UNSET);
        assertThat(runAdBiddingPerCAProcessReportedStats.getNumOfKeysOfTrustedBiddingSignals())
                .isEqualTo(STATUS_UNSET);
        assertThat(
                runAdBiddingPerCAProcessReportedStats
                        .getFetchedTrustedBiddingSignalsDataSizeInBytes())
                .isEqualTo(STATUS_UNSET);
        assertThat(
                runAdBiddingPerCAProcessReportedStats
                        .getGetTrustedBiddingSignalsLatencyInMillis())
                .isEqualTo(STATUS_UNSET);
        assertThat(runAdBiddingPerCAProcessReportedStats.getGetTrustedBiddingSignalsResultCode())
                .isEqualTo(STATUS_UNSET);
        assertThat(runAdBiddingPerCAProcessReportedStats.getGenerateBidsLatencyInMillis())
                .isEqualTo(STATUS_UNSET);
        assertThat(runAdBiddingPerCAProcessReportedStats.getRunBiddingLatencyInMillis())
                .isEqualTo(STATUS_UNSET);
        assertThat(runAdBiddingPerCAProcessReportedStats.getRunBiddingResultCode())
                .isEqualTo(STATUS_UNSET);
    }

    private void verifyFailedRunAdBiddingPerCALoggingTrustedBiddingSignals(
            int numOfAdsForBidding,
            int buyerDecisionLogicScriptSizeInBytes,
            int numOfKeysOfTrustedBiddingSignals,
            int resultCode) {
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingPerCAProcessReportedStats(
                        mRunAdBiddingPerCAProcessReportedStatsArgumentCaptor.capture());
        RunAdBiddingPerCAProcessReportedStats runAdBiddingPerCAProcessReportedStats =
                mRunAdBiddingPerCAProcessReportedStatsArgumentCaptor.getValue();
        assertThat(runAdBiddingPerCAProcessReportedStats.getNumOfAdsForBidding())
                .isEqualTo(numOfAdsForBidding);
        assertThat(runAdBiddingPerCAProcessReportedStats.getRunAdBiddingPerCaLatencyInMillis())
                .isEqualTo(RUN_AD_BIDDING_PER_CA_LATENCY_IN_MS);
        assertThat(runAdBiddingPerCAProcessReportedStats.getRunAdBiddingPerCaResultCode())
                .isEqualTo(resultCode);
        assertThat(runAdBiddingPerCAProcessReportedStats.getGetBuyerDecisionLogicLatencyInMillis())
                .isEqualTo(GET_BUYER_DECISION_LOGIC_LATENCY_IN_MS);
        assertThat(runAdBiddingPerCAProcessReportedStats.getGetBuyerDecisionLogicResultCode())
                .isEqualTo(STATUS_SUCCESS);
        assertThat(runAdBiddingPerCAProcessReportedStats.getBuyerDecisionLogicScriptType())
                .isEqualTo(SCRIPT_JAVASCRIPT);
        assertThat(
                runAdBiddingPerCAProcessReportedStats
                        .getFetchedBuyerDecisionLogicScriptSizeInBytes())
                .isEqualTo(buyerDecisionLogicScriptSizeInBytes);
        assertThat(runAdBiddingPerCAProcessReportedStats.getNumOfKeysOfTrustedBiddingSignals())
                .isEqualTo(numOfKeysOfTrustedBiddingSignals);
        assertThat(
                runAdBiddingPerCAProcessReportedStats
                        .getFetchedTrustedBiddingSignalsDataSizeInBytes())
                .isEqualTo(STATUS_UNSET);
        assertThat(
                runAdBiddingPerCAProcessReportedStats
                        .getGetTrustedBiddingSignalsLatencyInMillis())
                .isEqualTo(
                        (int)
                                (STOP_ELAPSED_TIMESTAMP
                                        - GET_TRUSTED_BIDDING_SIGNALS_START_TIMESTAMP));
        assertThat(runAdBiddingPerCAProcessReportedStats.getGetTrustedBiddingSignalsResultCode())
                .isEqualTo(resultCode);
        assertThat(runAdBiddingPerCAProcessReportedStats.getGenerateBidsLatencyInMillis())
                .isEqualTo(STATUS_UNSET);
        assertThat(runAdBiddingPerCAProcessReportedStats.getRunBiddingLatencyInMillis())
                .isEqualTo((int) (STOP_ELAPSED_TIMESTAMP - RUN_BIDDING_START_TIMESTAMP));
        assertThat(runAdBiddingPerCAProcessReportedStats.getRunBiddingResultCode())
                .isEqualTo(resultCode);
    }

    private void verifyFailedRunAdBiddingPerCALoggingGetBuyerBiddingJs(
            int numOfAdsForBidding, int resultCode) {
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingPerCAProcessReportedStats(
                        mRunAdBiddingPerCAProcessReportedStatsArgumentCaptor.capture());
        RunAdBiddingPerCAProcessReportedStats runAdBiddingPerCAProcessReportedStats =
                mRunAdBiddingPerCAProcessReportedStatsArgumentCaptor.getValue();
        assertThat(runAdBiddingPerCAProcessReportedStats.getNumOfAdsForBidding())
                .isEqualTo(numOfAdsForBidding);
        assertThat(runAdBiddingPerCAProcessReportedStats.getRunAdBiddingPerCaLatencyInMillis())
                .isEqualTo(RUN_AD_BIDDING_PER_CA_LATENCY_IN_MS);
        assertThat(runAdBiddingPerCAProcessReportedStats.getRunAdBiddingPerCaResultCode())
                .isEqualTo(resultCode);
        assertThat(runAdBiddingPerCAProcessReportedStats.getGetBuyerDecisionLogicLatencyInMillis())
                .isEqualTo(
                        (int) (STOP_ELAPSED_TIMESTAMP - GET_BUYER_DECISION_LOGIC_START_TIMESTAMP));
        assertThat(runAdBiddingPerCAProcessReportedStats.getGetBuyerDecisionLogicResultCode())
                .isEqualTo(resultCode);
        assertThat(runAdBiddingPerCAProcessReportedStats.getBuyerDecisionLogicScriptType())
                .isEqualTo(SCRIPT_UNSET);
        assertThat(
                runAdBiddingPerCAProcessReportedStats
                        .getFetchedBuyerDecisionLogicScriptSizeInBytes())
                .isEqualTo(STATUS_UNSET);
        assertThat(runAdBiddingPerCAProcessReportedStats.getNumOfKeysOfTrustedBiddingSignals())
                .isEqualTo(STATUS_UNSET);
        assertThat(
                runAdBiddingPerCAProcessReportedStats
                        .getFetchedTrustedBiddingSignalsDataSizeInBytes())
                .isEqualTo(STATUS_UNSET);
        assertThat(
                runAdBiddingPerCAProcessReportedStats
                        .getGetTrustedBiddingSignalsLatencyInMillis())
                .isEqualTo(STATUS_UNSET);
        assertThat(runAdBiddingPerCAProcessReportedStats.getGetTrustedBiddingSignalsResultCode())
                .isEqualTo(STATUS_UNSET);
        assertThat(runAdBiddingPerCAProcessReportedStats.getGenerateBidsLatencyInMillis())
                .isEqualTo(STATUS_UNSET);
        assertThat(runAdBiddingPerCAProcessReportedStats.getRunBiddingLatencyInMillis())
                .isEqualTo(STATUS_UNSET);
        assertThat(runAdBiddingPerCAProcessReportedStats.getRunBiddingResultCode())
                .isEqualTo(STATUS_UNSET);
    }

    private void verifyFailedRunAdBiddingPerCALoggingByGenerateBids(
            int numOfAdsForBidding,
            int buyerDecisionLogicScriptSizeInBytes,
            int numOfKeysOfTrustedBiddingSignals,
            int trustedBiddingSignalsDataSizeInBytes,
            int resultCode) {
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingPerCAProcessReportedStats(
                        mRunAdBiddingPerCAProcessReportedStatsArgumentCaptor.capture());
        RunAdBiddingPerCAProcessReportedStats runAdBiddingPerCAProcessReportedStats =
                mRunAdBiddingPerCAProcessReportedStatsArgumentCaptor.getValue();
        assertThat(runAdBiddingPerCAProcessReportedStats.getNumOfAdsForBidding())
                .isEqualTo(numOfAdsForBidding);
        assertThat(runAdBiddingPerCAProcessReportedStats.getRunAdBiddingPerCaLatencyInMillis())
                .isEqualTo(RUN_AD_BIDDING_PER_CA_LATENCY_IN_MS);
        assertThat(runAdBiddingPerCAProcessReportedStats.getRunAdBiddingPerCaResultCode())
                .isEqualTo(resultCode);
        assertThat(runAdBiddingPerCAProcessReportedStats.getGetBuyerDecisionLogicLatencyInMillis())
                .isEqualTo(GET_BUYER_DECISION_LOGIC_LATENCY_IN_MS);
        assertThat(runAdBiddingPerCAProcessReportedStats.getGetBuyerDecisionLogicResultCode())
                .isEqualTo(STATUS_SUCCESS);
        assertThat(runAdBiddingPerCAProcessReportedStats.getBuyerDecisionLogicScriptType())
                .isEqualTo(SCRIPT_JAVASCRIPT);
        assertThat(
                runAdBiddingPerCAProcessReportedStats
                        .getFetchedBuyerDecisionLogicScriptSizeInBytes())
                .isEqualTo(buyerDecisionLogicScriptSizeInBytes);
        assertThat(runAdBiddingPerCAProcessReportedStats.getNumOfKeysOfTrustedBiddingSignals())
                .isEqualTo(numOfKeysOfTrustedBiddingSignals);
        assertThat(
                runAdBiddingPerCAProcessReportedStats
                        .getFetchedTrustedBiddingSignalsDataSizeInBytes())
                .isEqualTo(trustedBiddingSignalsDataSizeInBytes);
        assertThat(
                runAdBiddingPerCAProcessReportedStats
                        .getGetTrustedBiddingSignalsLatencyInMillis())
                .isEqualTo(GET_TRUSTED_BIDDING_SIGNALS_IN_MS);
        assertThat(runAdBiddingPerCAProcessReportedStats.getGetTrustedBiddingSignalsResultCode())
                .isEqualTo(STATUS_SUCCESS);
        assertThat(runAdBiddingPerCAProcessReportedStats.getGenerateBidsLatencyInMillis())
                .isEqualTo(STATUS_UNSET);
        assertThat(runAdBiddingPerCAProcessReportedStats.getRunBiddingLatencyInMillis())
                .isEqualTo((int) (STOP_ELAPSED_TIMESTAMP - RUN_BIDDING_START_TIMESTAMP));
        assertThat(runAdBiddingPerCAProcessReportedStats.getRunBiddingResultCode())
                .isEqualTo(resultCode);
    }

    private void verifyFailedRunAdBiddingPerCALoggingTimeoutException(
            int numOfAdsForBidding, int resultCode) {
        // Timeout exception could be thrown at any stage of the RunAdBiddingPerCA process, so we
        // only verify partial logging of the start and the end stage of RunAdScoring.
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingPerCAProcessReportedStats(
                        mRunAdBiddingPerCAProcessReportedStatsArgumentCaptor.capture());
        RunAdBiddingPerCAProcessReportedStats runAdBiddingPerCAProcessReportedStats =
                mRunAdBiddingPerCAProcessReportedStatsArgumentCaptor.getValue();
        assertThat(runAdBiddingPerCAProcessReportedStats.getNumOfAdsForBidding())
                .isEqualTo(numOfAdsForBidding);
        assertThat(runAdBiddingPerCAProcessReportedStats.getRunAdBiddingPerCaResultCode())
                .isEqualTo(resultCode);
    }

    private void verifySuccessAdBiddingPerCALogging(
            int numOfAdsForBidding,
            int buyerDecisionLogicScriptSizeInBytes,
            int numOfKeysOfTrustedBiddingSignals,
            int trustedBiddingSignalsDataSizeInBytes) {
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingPerCAProcessReportedStats(
                        mRunAdBiddingPerCAProcessReportedStatsArgumentCaptor.capture());
        RunAdBiddingPerCAProcessReportedStats runAdBiddingPerCAProcessReportedStats =
                mRunAdBiddingPerCAProcessReportedStatsArgumentCaptor.getValue();
        assertThat(runAdBiddingPerCAProcessReportedStats.getNumOfAdsForBidding())
                .isEqualTo(numOfAdsForBidding);
        assertThat(runAdBiddingPerCAProcessReportedStats.getRunAdBiddingPerCaLatencyInMillis())
                .isEqualTo(RUN_AD_BIDDING_PER_CA_LATENCY_IN_MS);
        assertThat(runAdBiddingPerCAProcessReportedStats.getRunAdBiddingPerCaResultCode())
                .isEqualTo(STATUS_SUCCESS);
        assertThat(runAdBiddingPerCAProcessReportedStats.getGetBuyerDecisionLogicLatencyInMillis())
                .isEqualTo(GET_BUYER_DECISION_LOGIC_LATENCY_IN_MS);
        assertThat(runAdBiddingPerCAProcessReportedStats.getGetBuyerDecisionLogicResultCode())
                .isEqualTo(STATUS_SUCCESS);
        assertThat(runAdBiddingPerCAProcessReportedStats.getBuyerDecisionLogicScriptType())
                .isEqualTo(SCRIPT_JAVASCRIPT);
        assertThat(
                runAdBiddingPerCAProcessReportedStats
                        .getFetchedBuyerDecisionLogicScriptSizeInBytes())
                .isEqualTo(buyerDecisionLogicScriptSizeInBytes);
        assertThat(runAdBiddingPerCAProcessReportedStats.getNumOfKeysOfTrustedBiddingSignals())
                .isEqualTo(numOfKeysOfTrustedBiddingSignals);
        assertThat(
                runAdBiddingPerCAProcessReportedStats
                        .getFetchedTrustedBiddingSignalsDataSizeInBytes())
                .isEqualTo(trustedBiddingSignalsDataSizeInBytes);
        assertThat(
                runAdBiddingPerCAProcessReportedStats
                        .getGetTrustedBiddingSignalsLatencyInMillis())
                .isEqualTo(GET_TRUSTED_BIDDING_SIGNALS_IN_MS);
        assertThat(runAdBiddingPerCAProcessReportedStats.getGetTrustedBiddingSignalsResultCode())
                .isEqualTo(STATUS_SUCCESS);
        assertThat(runAdBiddingPerCAProcessReportedStats.getGenerateBidsLatencyInMillis())
                .isEqualTo(GENERATE_BIDS_LATENCY_IN_MS);
        assertThat(runAdBiddingPerCAProcessReportedStats.getRunBiddingLatencyInMillis())
                .isEqualTo(RUN_BIDDING_LATENCY_IN_MS);
        assertThat(runAdBiddingPerCAProcessReportedStats.getRunBiddingResultCode())
                .isEqualTo(STATUS_SUCCESS);
    }

    private ListenableFuture<List<GenerateBidResult>> generateBidsWithDelay(@NonNull Flags flags) {
        return mBlockingExecutorService.submit(
                () -> {
                    Thread.sleep(2 * flags.getAdSelectionBiddingTimeoutPerCaMs());
                    return GENERATE_BIDS_RESPONSE;
                });
    }

    private <T> T waitForFuture(
            AdsScoreGeneratorImplTest.ThrowingSupplier<ListenableFuture<T>> function)
            throws Exception {
        CountDownLatch resultLatch = new CountDownLatch(1);
        ListenableFuture<T> futureResult = function.get();
        futureResult.addListener(resultLatch::countDown, mLightweightExecutorService);
        resultLatch.await();
        return futureResult.get();
    }

    static class JsonObjectStringSimpleMatcher implements ArgumentMatcher<AdSelectionSignals> {

        private final JSONObject mJsonObject;

        JsonObjectStringSimpleMatcher(JSONObject jsonObject) {
            mJsonObject = jsonObject;
        }

        @Override
        public boolean matches(AdSelectionSignals argument) {
            try {
                JSONObject fromArgument = new JSONObject(argument.toString());
                if (!fromArgument.keySet().containsAll(mJsonObject.keySet())) {
                    return false;
                }
                if (!mJsonObject.keySet().containsAll(fromArgument.keySet())) {
                    return false;
                }
                for (String key : mJsonObject.keySet()) {
                    if (!mJsonObject.get(key).equals(fromArgument.opt(key))) {
                        return false;
                    }
                }
            } catch (JSONException e) {
                return false;
            }
            return true;
        }
    }

    private static class AdBidGeneratorImplTestFlags implements Flags {

        @Override
        public long getAdSelectionBiddingTimeoutPerCaMs() {
            return EXTENDED_FLEDGE_AD_SELECTION_BIDDING_TIMEOUT_PER_CA_MS;
        }

        @Override
        public boolean getFledgeEventLevelDebugReportingEnabled() {
            return false;
        }

        @Override
        public long getFledgeAdSelectionBiddingLogicJsVersion() {
            return JsVersionRegister.BUYER_BIDDING_LOGIC_VERSION_VERSION_3;
        }
    }
}
