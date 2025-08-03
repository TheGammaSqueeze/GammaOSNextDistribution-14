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
import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;
import static android.adservices.common.AdServicesStatusUtils.STATUS_UNSET;

import static com.android.adservices.data.adselection.CustomAudienceSignals.CONTEXTUAL_CA_NAME;
import static com.android.adservices.common.CommonFlagsValues.EXTENDED_FLEDGE_AD_SELECTION_OVERALL_TIMEOUT_MS;
import static com.android.adservices.common.CommonFlagsValues.EXTENDED_FLEDGE_AD_SELECTION_SCORING_TIMEOUT_MS;
import static com.android.adservices.service.adselection.AdsScoreGeneratorImpl.MISSING_TRUSTED_SCORING_SIGNALS;
import static com.android.adservices.service.adselection.AdsScoreGeneratorImpl.QUERY_PARAM_RENDER_URIS;
import static com.android.adservices.service.adselection.AdsScoreGeneratorImpl.SCORES_COUNT_LESS_THAN_EXPECTED;
import static com.android.adservices.service.adselection.AdsScoreGeneratorImpl.SCORING_TIMED_OUT;
import static com.android.adservices.service.adselection.DataVersionFetcher.DATA_VERSION_HEADER_SCORING_KEY;
import static com.android.adservices.service.stats.AdSelectionExecutionLogger.SCRIPT_JAVASCRIPT;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTest.GET_AD_SCORES_END_TIMESTAMP;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTest.GET_AD_SCORES_LATENCY_MS;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTest.GET_AD_SCORES_START_TIMESTAMP;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTest.GET_AD_SELECTION_LOGIC_END_TIMESTAMP;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTest.GET_AD_SELECTION_LOGIC_LATENCY_MS;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTest.GET_AD_SELECTION_LOGIC_START_TIMESTAMP;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTest.GET_TRUSTED_SCORING_SIGNALS_END_TIMESTAMP;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTest.GET_TRUSTED_SCORING_SIGNALS_LATENCY_MS;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTest.GET_TRUSTED_SCORING_SIGNALS_START_TIMESTAMP;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTest.RUN_AD_SCORING_END_TIMESTAMP;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTest.RUN_AD_SCORING_LATENCY_MS;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTest.RUN_AD_SCORING_START_TIMESTAMP;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTest.SCORE_ADS_END_TIMESTAMP;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTest.SCORE_ADS_LATENCY_MS;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTest.SCORE_ADS_START_TIMESTAMP;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTest.START_ELAPSED_TIMESTAMP;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTest.sCallerMetadata;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.adservices.adselection.AdBiddingOutcomeFixture;
import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionConfigFixture;
import android.adservices.adselection.AdWithBid;
import android.adservices.adselection.SignedContextualAds;
import android.adservices.adselection.SignedContextualAdsFixture;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.adservices.http.MockWebServerRule;
import android.annotation.NonNull;
import android.net.Uri;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.MockWebServerRuleFactory;
import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.adselection.AdSelectionDatabase;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.adselection.CustomAudienceSignals;
import com.android.adservices.data.adselection.DBAdSelectionOverride;
import com.android.adservices.data.adselection.DBBuyerDecisionOverride;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.cache.CacheProviderFactory;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.devapi.AdSelectionDevOverridesHelper;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.stats.AdSelectionExecutionLogger;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerUtil;
import com.android.adservices.service.stats.RunAdScoringProcessReportedStats;
import com.android.adservices.shared.util.Clock;

import com.google.common.collect.ImmutableList;
import com.google.common.truth.Truth;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.mockwebserver.Dispatcher;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;
import com.google.mockwebserver.RecordedRequest;

import org.json.JSONException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.stream.Collectors;

public class AdsScoreGeneratorImplTest {

    private static final AdTechIdentifier BUYER_1 = AdSelectionConfigFixture.BUYER_1;
    private static final AdTechIdentifier BUYER_2 = AdSelectionConfigFixture.BUYER_2;
    private final String mFetchJavaScriptPath = "/fetchJavascript/";
    private final String mTrustedScoringSignalsPath = "/getTrustedScoringSignals/";
    private final String mTrustedScoringSignalsPathWithDataVersionHeader =
            "/getTrustedScoringSignalsWithDataVersionHeader/";
    @Rule public MockWebServerRule mMockWebServerRule = MockWebServerRuleFactory.createForHttps();
    AdSelectionConfig mAdSelectionConfig;

    @Mock private AdSelectionScriptEngine mMockAdSelectionScriptEngine;

    private ListeningExecutorService mLightweightExecutorService;
    private ListeningExecutorService mBackgroundExecutorService;
    private ListeningExecutorService mBlockingExecutorService;
    private ScheduledThreadPoolExecutor mSchedulingExecutor;
    private AdServicesHttpsClient mWebClient;
    private String mSellerDecisionLogicJs;

    private AdBiddingOutcome mAdBiddingOutcomeBuyer1;
    private AdBiddingOutcome mAdBiddingOutcomeBuyer2;
    private List<AdBiddingOutcome> mAdBiddingOutcomeList;

    private AdsScoreGenerator mAdsScoreGenerator;
    private DevContext mDevContext;
    private Flags mFlags;

    private AdSelectionEntryDao mAdSelectionEntryDao;

    private AdSelectionSignals mTrustedScoringSignals;
    private String mTrustedScoringParams;
    private List<String> mTrustedScoringSignalsKeys;

    private Dispatcher mDefaultDispatcher;
    private MockWebServerRule.RequestMatcher<String> mRequestMatcherExactMatch;
    private AdSelectionExecutionLogger mAdSelectionExecutionLogger;
    @Mock Clock mAdSelectionExecutionLoggerClock;
    @Mock private AdServicesLogger mAdServicesLoggerMock;

    @Mock private DebugReporting mDebugReporting;

    @Captor
    ArgumentCaptor<RunAdScoringProcessReportedStats>
            mRunAdScoringProcessReportedStatsArgumentCaptor;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mFlags = new AdsScoreGeneratorImplTestFlags();
        mDevContext = DevContext.createForDevOptionsDisabled();
        mLightweightExecutorService = AdServicesExecutors.getLightWeightExecutor();
        mBackgroundExecutorService = AdServicesExecutors.getBackgroundExecutor();
        mBlockingExecutorService = AdServicesExecutors.getBlockingExecutor();
        mSchedulingExecutor = AdServicesExecutors.getScheduler();
        mWebClient =
                new AdServicesHttpsClient(
                        AdServicesExecutors.getBlockingExecutor(),
                        CacheProviderFactory.createNoOpCache());

        mAdBiddingOutcomeBuyer1 =
                AdBiddingOutcomeFixture.anAdBiddingOutcomeBuilder(BUYER_1, 1.0).build();
        mAdBiddingOutcomeBuyer2 =
                AdBiddingOutcomeFixture.anAdBiddingOutcomeBuilder(BUYER_2, 2.0).build();
        mAdBiddingOutcomeList = ImmutableList.of(mAdBiddingOutcomeBuyer1, mAdBiddingOutcomeBuyer2);

        mSellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) {"
                        + " \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + " /reporting/seller "
                        + "' } };\n"
                        + "}";

        mAdSelectionEntryDao =
                Room.inMemoryDatabaseBuilder(
                                ApplicationProvider.getApplicationContext(),
                                AdSelectionDatabase.class)
                        .build()
                        .adSelectionEntryDao();

        mTrustedScoringSignalsKeys =
                ImmutableList.of(
                        mAdBiddingOutcomeBuyer1
                                .getAdWithBid()
                                .getAdData()
                                .getRenderUri()
                                .getEncodedPath(),
                        mAdBiddingOutcomeBuyer2
                                .getAdWithBid()
                                .getAdData()
                                .getRenderUri()
                                .getEncodedPath());

        mTrustedScoringParams =
                String.format(
                        "?%s=%s",
                        QUERY_PARAM_RENDER_URIS,
                        Uri.encode(String.join(",", mTrustedScoringSignalsKeys)));

        mTrustedScoringSignals =
                AdSelectionSignals.fromString(
                        "{\n"
                                + mAdBiddingOutcomeBuyer1
                                        .getAdWithBid()
                                        .getAdData()
                                        .getRenderUri()
                                        .getEncodedPath()
                                + ": signalsForUri1,\n"
                                + mAdBiddingOutcomeBuyer2
                                        .getAdWithBid()
                                        .getAdData()
                                        .getRenderUri()
                                        .getEncodedPath()
                                + ": signalsForUri2,\n"
                                + "}");

        mDefaultDispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        if (mFetchJavaScriptPath.equals(request.getPath())) {
                            return new MockResponse().setBody(mSellerDecisionLogicJs);
                        } else if (mTrustedScoringSignalsPath
                                .concat(mTrustedScoringParams)
                                .equals(request.getPath())) {
                            return new MockResponse().setBody(mTrustedScoringSignals.toString());
                        } else if (mTrustedScoringSignalsPathWithDataVersionHeader
                                .concat(mTrustedScoringParams)
                                .equals(request.getPath()))
                            return new MockResponse()
                                    .setBody(mTrustedScoringSignals.toString())
                                    .addHeader(DATA_VERSION_HEADER_SCORING_KEY, DATA_VERSION_1);
                        return new MockResponse().setResponseCode(404);
                    }
                };

        mRequestMatcherExactMatch =
                (actualRequest, expectedRequest) -> actualRequest.equals(expectedRequest);

        when(mAdSelectionExecutionLoggerClock.elapsedRealtime())
                .thenReturn(START_ELAPSED_TIMESTAMP);
        mAdSelectionExecutionLogger =
                new AdSelectionExecutionLogger(
                        sCallerMetadata,
                        mAdSelectionExecutionLoggerClock,
                        ApplicationProvider.getApplicationContext(),
                        mAdServicesLoggerMock);
        when(mDebugReporting.isEnabled()).thenReturn(false);
        mAdsScoreGenerator =
                new AdsScoreGeneratorImpl(
                        mMockAdSelectionScriptEngine,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mSchedulingExecutor,
                        mWebClient,
                        mDevContext,
                        mAdSelectionEntryDao,
                        mFlags,
                        mAdSelectionExecutionLogger,
                        mDebugReporting,
                        false);
    }

    @Test
    public void testRunAdScoringSuccess() throws Exception {
        when(mAdSelectionExecutionLoggerClock.elapsedRealtime())
                .thenReturn(
                        RUN_AD_SCORING_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_END_TIMESTAMP,
                        GET_AD_SCORES_START_TIMESTAMP,
                        GET_TRUSTED_SCORING_SIGNALS_START_TIMESTAMP,
                        GET_TRUSTED_SCORING_SIGNALS_END_TIMESTAMP,
                        SCORE_ADS_START_TIMESTAMP,
                        SCORE_ADS_END_TIMESTAMP,
                        GET_AD_SCORES_END_TIMESTAMP,
                        RUN_AD_SCORING_END_TIMESTAMP);
        // Logger calls come after the callback is returned
        CountDownLatch runAdScoringProcessLoggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            runAdScoringProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());

        List<Double> scores = ImmutableList.of(1.0, 2.0);
        MockWebServer server = mMockWebServerRule.startMockWebServer(mDefaultDispatcher);

        Uri decisionLogicUri = mMockWebServerRule.uriForPath(mFetchJavaScriptPath);

        mAdSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setDecisionLogicUri(decisionLogicUri)
                        .setTrustedScoringSignalsUri(
                                mMockWebServerRule.uriForPath(mTrustedScoringSignalsPath))
                        .build();

        Answer<ListenableFuture<List<ScoreAdResult>>> loggerAnswer =
                unused -> {
                    mAdSelectionExecutionLogger.startScoreAds();
                    mAdSelectionExecutionLogger.endScoreAds();
                    return Futures.immediateFuture(
                            scores.stream()
                                    .map(
                                            score ->
                                                    ScoreAdResult.builder()
                                                            .setAdScore(score)
                                                            .build())
                                    .collect(Collectors.toList()));
                };
        Mockito.when(
                        mMockAdSelectionScriptEngine.scoreAds(
                                mSellerDecisionLogicJs,
                                mAdBiddingOutcomeList.stream()
                                        .map(a -> a.getAdWithBid())
                                        .collect(Collectors.toList()),
                                mAdSelectionConfig,
                                mAdSelectionConfig.getSellerSignals(),
                                mTrustedScoringSignals,
                                AdSelectionSignals.EMPTY,
                                mAdBiddingOutcomeList.stream()
                                        .map(
                                                a ->
                                                        a.getCustomAudienceBiddingInfo()
                                                                .getCustomAudienceSignals())
                                        .collect(Collectors.toList()),
                                mAdSelectionExecutionLogger))
                .thenAnswer(loggerAnswer);

        FluentFuture<List<AdScoringOutcome>> scoringResultFuture =
                mAdsScoreGenerator.runAdScoring(mAdBiddingOutcomeList, mAdSelectionConfig);

        List<AdScoringOutcome> scoringOutcome = waitForFuture(() -> scoringResultFuture);

        Mockito.verify(mMockAdSelectionScriptEngine)
                .scoreAds(
                        mSellerDecisionLogicJs,
                        mAdBiddingOutcomeList.stream()
                                .map(a -> a.getAdWithBid())
                                .collect(Collectors.toList()),
                        mAdSelectionConfig,
                        mAdSelectionConfig.getSellerSignals(),
                        mTrustedScoringSignals,
                        AdSelectionSignals.EMPTY,
                        mAdBiddingOutcomeList.stream()
                                .map(
                                        a ->
                                                a.getCustomAudienceBiddingInfo()
                                                        .getCustomAudienceSignals())
                                .collect(Collectors.toList()),
                        mAdSelectionExecutionLogger);

        mMockWebServerRule.verifyMockServerRequests(
                server,
                2,
                ImmutableList.of(
                        mFetchJavaScriptPath, mTrustedScoringSignalsPath + mTrustedScoringParams),
                mRequestMatcherExactMatch);
        runAdScoringProcessLoggerLatch.await();
        assertEquals(1L, scoringOutcome.get(0).getAdWithScore().getScore().longValue());
        assertEquals(2L, scoringOutcome.get(1).getAdWithScore().getScore().longValue());
        verifySuccessAdScoringLogging(
                mSellerDecisionLogicJs, mTrustedScoringSignals, mAdBiddingOutcomeList);
    }

    @Test
    public void testRunAdScoringSuccessWithDataVersionHeaderEnabled() throws Exception {
        // Re init generator
        mAdsScoreGenerator =
                new AdsScoreGeneratorImpl(
                        mMockAdSelectionScriptEngine,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mSchedulingExecutor,
                        mWebClient,
                        mDevContext,
                        mAdSelectionEntryDao,
                        mFlags,
                        mAdSelectionExecutionLogger,
                        mDebugReporting,
                        true);

        when(mAdSelectionExecutionLoggerClock.elapsedRealtime())
                .thenReturn(
                        RUN_AD_SCORING_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_END_TIMESTAMP,
                        GET_AD_SCORES_START_TIMESTAMP,
                        GET_TRUSTED_SCORING_SIGNALS_START_TIMESTAMP,
                        GET_TRUSTED_SCORING_SIGNALS_END_TIMESTAMP,
                        SCORE_ADS_START_TIMESTAMP,
                        SCORE_ADS_END_TIMESTAMP,
                        GET_AD_SCORES_END_TIMESTAMP,
                        RUN_AD_SCORING_END_TIMESTAMP);
        // Logger calls come after the callback is returned
        CountDownLatch runAdScoringProcessLoggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            runAdScoringProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());

        List<Double> scores = ImmutableList.of(1.0, 2.0);
        MockWebServer server = mMockWebServerRule.startMockWebServer(mDefaultDispatcher);

        Uri decisionLogicUri = mMockWebServerRule.uriForPath(mFetchJavaScriptPath);

        // Set trusted scoring uri to return data version header
        mAdSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setDecisionLogicUri(decisionLogicUri)
                        .setTrustedScoringSignalsUri(
                                mMockWebServerRule.uriForPath(
                                        mTrustedScoringSignalsPathWithDataVersionHeader))
                        .build();

        AdSelectionSignals expectedSellerContextualSignals =
                SellerContextualSignals.builder()
                        .setDataVersion(DATA_VERSION_1)
                        .build()
                        .toAdSelectionSignals();

        Answer<ListenableFuture<List<ScoreAdResult>>> loggerAnswer =
                unused -> {
                    mAdSelectionExecutionLogger.startScoreAds();
                    mAdSelectionExecutionLogger.endScoreAds();
                    return Futures.immediateFuture(
                            scores.stream()
                                    .map(score -> ScoreAdResult.builder().setAdScore(score).build())
                                    .collect(Collectors.toList()));
                };
        Mockito.when(
                        mMockAdSelectionScriptEngine.scoreAds(
                                mSellerDecisionLogicJs,
                                mAdBiddingOutcomeList.stream()
                                        .map(a -> a.getAdWithBid())
                                        .collect(Collectors.toList()),
                                mAdSelectionConfig,
                                mAdSelectionConfig.getSellerSignals(),
                                mTrustedScoringSignals,
                                expectedSellerContextualSignals,
                                mAdBiddingOutcomeList.stream()
                                        .map(
                                                a ->
                                                        a.getCustomAudienceBiddingInfo()
                                                                .getCustomAudienceSignals())
                                        .collect(Collectors.toList()),
                                mAdSelectionExecutionLogger))
                .thenAnswer(loggerAnswer);

        FluentFuture<List<AdScoringOutcome>> scoringResultFuture =
                mAdsScoreGenerator.runAdScoring(mAdBiddingOutcomeList, mAdSelectionConfig);

        List<AdScoringOutcome> scoringOutcome = waitForFuture(() -> scoringResultFuture);

        Mockito.verify(mMockAdSelectionScriptEngine)
                .scoreAds(
                        mSellerDecisionLogicJs,
                        mAdBiddingOutcomeList.stream()
                                .map(a -> a.getAdWithBid())
                                .collect(Collectors.toList()),
                        mAdSelectionConfig,
                        mAdSelectionConfig.getSellerSignals(),
                        mTrustedScoringSignals,
                        expectedSellerContextualSignals,
                        mAdBiddingOutcomeList.stream()
                                .map(
                                        a ->
                                                a.getCustomAudienceBiddingInfo()
                                                        .getCustomAudienceSignals())
                                .collect(Collectors.toList()),
                        mAdSelectionExecutionLogger);

        mMockWebServerRule.verifyMockServerRequests(
                server,
                2,
                ImmutableList.of(
                        mFetchJavaScriptPath,
                        mTrustedScoringSignalsPathWithDataVersionHeader + mTrustedScoringParams),
                mRequestMatcherExactMatch);
        runAdScoringProcessLoggerLatch.await();
        assertEquals(1L, scoringOutcome.get(0).getAdWithScore().getScore().longValue());
        assertEquals(2L, scoringOutcome.get(1).getAdWithScore().getScore().longValue());

        // Assert seller contextual signals were propagated through
        assertEquals(
                expectedSellerContextualSignals,
                scoringOutcome.get(0).getSellerContextualSignals().toAdSelectionSignals());
        assertEquals(
                expectedSellerContextualSignals,
                scoringOutcome.get(1).getSellerContextualSignals().toAdSelectionSignals());
        verifySuccessAdScoringLogging(
                mSellerDecisionLogicJs, mTrustedScoringSignals, mAdBiddingOutcomeList);
    }

    @Test
    public void testRunAdScoringSuccessWithDataVersionHeaderDisabled() throws Exception {
        // Re init generator
        mAdsScoreGenerator =
                new AdsScoreGeneratorImpl(
                        mMockAdSelectionScriptEngine,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mSchedulingExecutor,
                        mWebClient,
                        mDevContext,
                        mAdSelectionEntryDao,
                        mFlags,
                        mAdSelectionExecutionLogger,
                        mDebugReporting,
                        false);

        when(mAdSelectionExecutionLoggerClock.elapsedRealtime())
                .thenReturn(
                        RUN_AD_SCORING_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_END_TIMESTAMP,
                        GET_AD_SCORES_START_TIMESTAMP,
                        GET_TRUSTED_SCORING_SIGNALS_START_TIMESTAMP,
                        GET_TRUSTED_SCORING_SIGNALS_END_TIMESTAMP,
                        SCORE_ADS_START_TIMESTAMP,
                        SCORE_ADS_END_TIMESTAMP,
                        GET_AD_SCORES_END_TIMESTAMP,
                        RUN_AD_SCORING_END_TIMESTAMP);
        // Logger calls come after the callback is returned
        CountDownLatch runAdScoringProcessLoggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            runAdScoringProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());

        List<Double> scores = ImmutableList.of(1.0, 2.0);
        MockWebServer server = mMockWebServerRule.startMockWebServer(mDefaultDispatcher);

        Uri decisionLogicUri = mMockWebServerRule.uriForPath(mFetchJavaScriptPath);

        // Set trusted scoring uri to return data version header
        mAdSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setDecisionLogicUri(decisionLogicUri)
                        .setTrustedScoringSignalsUri(
                                mMockWebServerRule.uriForPath(
                                        mTrustedScoringSignalsPathWithDataVersionHeader))
                        .build();

        Answer<ListenableFuture<List<ScoreAdResult>>> loggerAnswer =
                unused -> {
                    mAdSelectionExecutionLogger.startScoreAds();
                    mAdSelectionExecutionLogger.endScoreAds();
                    return Futures.immediateFuture(
                            scores.stream()
                                    .map(score -> ScoreAdResult.builder().setAdScore(score).build())
                                    .collect(Collectors.toList()));
                };
        Mockito.when(
                        mMockAdSelectionScriptEngine.scoreAds(
                                mSellerDecisionLogicJs,
                                mAdBiddingOutcomeList.stream()
                                        .map(a -> a.getAdWithBid())
                                        .collect(Collectors.toList()),
                                mAdSelectionConfig,
                                mAdSelectionConfig.getSellerSignals(),
                                mTrustedScoringSignals,
                                AdSelectionSignals.EMPTY,
                                mAdBiddingOutcomeList.stream()
                                        .map(
                                                a ->
                                                        a.getCustomAudienceBiddingInfo()
                                                                .getCustomAudienceSignals())
                                        .collect(Collectors.toList()),
                                mAdSelectionExecutionLogger))
                .thenAnswer(loggerAnswer);

        FluentFuture<List<AdScoringOutcome>> scoringResultFuture =
                mAdsScoreGenerator.runAdScoring(mAdBiddingOutcomeList, mAdSelectionConfig);

        List<AdScoringOutcome> scoringOutcome = waitForFuture(() -> scoringResultFuture);

        Mockito.verify(mMockAdSelectionScriptEngine)
                .scoreAds(
                        mSellerDecisionLogicJs,
                        mAdBiddingOutcomeList.stream()
                                .map(a -> a.getAdWithBid())
                                .collect(Collectors.toList()),
                        mAdSelectionConfig,
                        mAdSelectionConfig.getSellerSignals(),
                        mTrustedScoringSignals,
                        AdSelectionSignals.EMPTY,
                        mAdBiddingOutcomeList.stream()
                                .map(
                                        a ->
                                                a.getCustomAudienceBiddingInfo()
                                                        .getCustomAudienceSignals())
                                .collect(Collectors.toList()),
                        mAdSelectionExecutionLogger);

        mMockWebServerRule.verifyMockServerRequests(
                server,
                2,
                ImmutableList.of(
                        mFetchJavaScriptPath,
                        mTrustedScoringSignalsPathWithDataVersionHeader + mTrustedScoringParams),
                mRequestMatcherExactMatch);
        runAdScoringProcessLoggerLatch.await();
        assertEquals(1L, scoringOutcome.get(0).getAdWithScore().getScore().longValue());
        assertEquals(2L, scoringOutcome.get(1).getAdWithScore().getScore().longValue());

        // Assert seller contextual signals were propagated through
        assertEquals(
                AdSelectionSignals.EMPTY,
                scoringOutcome.get(0).getSellerContextualSignals().toAdSelectionSignals());
        assertEquals(
                AdSelectionSignals.EMPTY,
                scoringOutcome.get(1).getSellerContextualSignals().toAdSelectionSignals());
        verifySuccessAdScoringLogging(
                mSellerDecisionLogicJs, mTrustedScoringSignals, mAdBiddingOutcomeList);
    }

    @Test
    public void testRunAdScoringSuccess_withDebugReportingEnabled() throws Exception {
        Uri winUri = Uri.parse("http://example.com/reportWin");
        Uri lossUri = Uri.parse("http://example.com/reportLoss");
        String sellerRejectReason = "invalid-bid";
        when(mDebugReporting.isEnabled()).thenReturn(true);
        when(mAdSelectionExecutionLoggerClock.elapsedRealtime())
                .thenReturn(
                        RUN_AD_SCORING_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_END_TIMESTAMP,
                        GET_AD_SCORES_START_TIMESTAMP,
                        GET_TRUSTED_SCORING_SIGNALS_START_TIMESTAMP,
                        GET_TRUSTED_SCORING_SIGNALS_END_TIMESTAMP,
                        SCORE_ADS_START_TIMESTAMP,
                        SCORE_ADS_END_TIMESTAMP,
                        GET_AD_SCORES_END_TIMESTAMP,
                        RUN_AD_SCORING_END_TIMESTAMP);
        // Logger calls come after the callback is returned
        CountDownLatch runAdScoringProcessLoggerLatch = new CountDownLatch(1);
        doAnswer(
                unusedInvocation -> {
                    runAdScoringProcessLoggerLatch.countDown();
                    return null;
                })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());

        List<Double> scores = ImmutableList.of(1.0, 2.0);
        MockWebServer server = mMockWebServerRule.startMockWebServer(mDefaultDispatcher);

        Uri decisionLogicUri = mMockWebServerRule.uriForPath(mFetchJavaScriptPath);

        mAdSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setDecisionLogicUri(decisionLogicUri)
                        .setTrustedScoringSignalsUri(
                                mMockWebServerRule.uriForPath(mTrustedScoringSignalsPath))
                        .build();

        Answer<ListenableFuture<List<ScoreAdResult>>> loggerAnswer =
                unused -> {
                    mAdSelectionExecutionLogger.startScoreAds();
                    mAdSelectionExecutionLogger.endScoreAds();
                    return Futures.immediateFuture(
                            scores.stream()
                                    .map(
                                            score ->
                                                    ScoreAdResult.builder()
                                                            .setAdScore(score)
                                                            .setWinDebugReportUri(winUri)
                                                            .setLossDebugReportUri(lossUri)
                                                            .setSellerRejectReason(
                                                                    sellerRejectReason)
                                                            .build())
                                    .collect(Collectors.toList()));
                };
        Mockito.when(
                        mMockAdSelectionScriptEngine.scoreAds(
                                mSellerDecisionLogicJs,
                                mAdBiddingOutcomeList.stream()
                                        .map(a -> a.getAdWithBid())
                                        .collect(Collectors.toList()),
                                mAdSelectionConfig,
                                mAdSelectionConfig.getSellerSignals(),
                                mTrustedScoringSignals,
                                AdSelectionSignals.EMPTY,
                                mAdBiddingOutcomeList.stream()
                                        .map(
                                                a ->
                                                        a.getCustomAudienceBiddingInfo()
                                                                .getCustomAudienceSignals())
                                        .collect(Collectors.toList()),
                                mAdSelectionExecutionLogger))
                .thenAnswer(loggerAnswer);

        AdsScoreGeneratorImpl adsScoreGenerator =
                new AdsScoreGeneratorImpl(
                        mMockAdSelectionScriptEngine,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mSchedulingExecutor,
                        mWebClient,
                        mDevContext,
                        mAdSelectionEntryDao,
                        mFlags,
                        mAdSelectionExecutionLogger,
                        mDebugReporting,
                        false);
        FluentFuture<List<AdScoringOutcome>> scoringResultFuture =
                adsScoreGenerator.runAdScoring(mAdBiddingOutcomeList, mAdSelectionConfig);

        List<AdScoringOutcome> scoringOutcome = waitForFuture(() -> scoringResultFuture);

        Mockito.verify(mMockAdSelectionScriptEngine)
                .scoreAds(
                        mSellerDecisionLogicJs,
                        mAdBiddingOutcomeList.stream()
                                .map(a -> a.getAdWithBid())
                                .collect(Collectors.toList()),
                        mAdSelectionConfig,
                        mAdSelectionConfig.getSellerSignals(),
                        mTrustedScoringSignals,
                        AdSelectionSignals.EMPTY,
                        mAdBiddingOutcomeList.stream()
                                .map(
                                        a ->
                                                a.getCustomAudienceBiddingInfo()
                                                        .getCustomAudienceSignals())
                                .collect(Collectors.toList()),
                        mAdSelectionExecutionLogger);

        mMockWebServerRule.verifyMockServerRequests(
                server,
                2,
                ImmutableList.of(
                        mFetchJavaScriptPath, mTrustedScoringSignalsPath + mTrustedScoringParams),
                mRequestMatcherExactMatch);
        runAdScoringProcessLoggerLatch.await();
        assertEquals(winUri, scoringOutcome.get(0).getDebugReport().getWinDebugReportUri());
        assertEquals(lossUri, scoringOutcome.get(0).getDebugReport().getLossDebugReportUri());
        assertEquals(
                sellerRejectReason, scoringOutcome.get(0).getDebugReport().getSellerRejectReason());
        assertEquals(winUri, scoringOutcome.get(1).getDebugReport().getWinDebugReportUri());
        assertEquals(lossUri, scoringOutcome.get(1).getDebugReport().getLossDebugReportUri());
        assertEquals(
                sellerRejectReason, scoringOutcome.get(1).getDebugReport().getSellerRejectReason());
        verifySuccessAdScoringLogging(
                mSellerDecisionLogicJs, mTrustedScoringSignals, mAdBiddingOutcomeList);
    }

    @Test
    public void testRunAdScoringContextual_Success() throws Exception {
        when(mAdSelectionExecutionLoggerClock.elapsedRealtime())
                .thenReturn(
                        RUN_AD_SCORING_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_END_TIMESTAMP,
                        GET_AD_SCORES_START_TIMESTAMP,
                        GET_TRUSTED_SCORING_SIGNALS_START_TIMESTAMP,
                        GET_TRUSTED_SCORING_SIGNALS_END_TIMESTAMP,
                        SCORE_ADS_START_TIMESTAMP,
                        SCORE_ADS_END_TIMESTAMP,
                        GET_AD_SCORES_END_TIMESTAMP,
                        RUN_AD_SCORING_END_TIMESTAMP);
        // Logger calls come after the callback is returned
        CountDownLatch runAdScoringProcessLoggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            runAdScoringProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());

        List<Double> scores = ImmutableList.of(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0);
        MockWebServer server = mMockWebServerRule.startMockWebServer(mDefaultDispatcher);

        Uri decisionLogicUri = mMockWebServerRule.uriForPath(mFetchJavaScriptPath);

        Map<AdTechIdentifier, SignedContextualAds> contextualAdsMap = createContextualAds();
        mAdSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigWithSignedContextualAdsBuilder()
                        .setDecisionLogicUri(decisionLogicUri)
                        .setTrustedScoringSignalsUri(
                                mMockWebServerRule.uriForPath(mTrustedScoringSignalsPath))
                        .setPerBuyerSignedContextualAds(contextualAdsMap)
                        .build();

        List<AdWithBid> adsWithBid =
                mAdBiddingOutcomeList.stream()
                        .map(a -> a.getAdWithBid())
                        .collect(Collectors.toList());
        List<SignedContextualAds> signedContextualAds =
                mAdSelectionConfig.getPerBuyerSignedContextualAds().values().stream()
                        .collect(Collectors.toList());
        List<AdWithBid> contextualBidAds = new ArrayList<>();
        for (SignedContextualAds ctx : signedContextualAds) {
            contextualBidAds.addAll(ctx.getAdsWithBid());
        }

        adsWithBid.addAll(contextualBidAds);
        Answer<ListenableFuture<List<ScoreAdResult>>> loggerAnswer =
                unused -> {
                    mAdSelectionExecutionLogger.startScoreAds();
                    mAdSelectionExecutionLogger.endScoreAds();
                    return Futures.immediateFuture(
                            scores.stream()
                                    .map(
                                            score ->
                                                    ScoreAdResult.builder()
                                                            .setAdScore(score)
                                                            .build())
                                    .collect(Collectors.toList()));
                };
        Mockito.when(
                        mMockAdSelectionScriptEngine.scoreAds(
                                mSellerDecisionLogicJs,
                                adsWithBid,
                                mAdSelectionConfig,
                                mAdSelectionConfig.getSellerSignals(),
                                mTrustedScoringSignals,
                                AdSelectionSignals.EMPTY,
                                mAdBiddingOutcomeList.stream()
                                        .map(
                                                a ->
                                                        a.getCustomAudienceBiddingInfo()
                                                                .getCustomAudienceSignals())
                                        .collect(Collectors.toList()),
                                mAdSelectionExecutionLogger))
                .thenAnswer(loggerAnswer);

        FluentFuture<List<AdScoringOutcome>> scoringResultFuture =
                mAdsScoreGenerator.runAdScoring(mAdBiddingOutcomeList, mAdSelectionConfig);

        List<AdScoringOutcome> scoringOutcome = waitForFuture(() -> scoringResultFuture);

        Mockito.verify(mMockAdSelectionScriptEngine)
                .scoreAds(
                        mSellerDecisionLogicJs,
                        adsWithBid,
                        mAdSelectionConfig,
                        mAdSelectionConfig.getSellerSignals(),
                        mTrustedScoringSignals,
                        AdSelectionSignals.EMPTY,
                        mAdBiddingOutcomeList.stream()
                                .map(
                                        a ->
                                                a.getCustomAudienceBiddingInfo()
                                                        .getCustomAudienceSignals())
                                .collect(Collectors.toList()),
                        mAdSelectionExecutionLogger);

        mMockWebServerRule.verifyMockServerRequests(
                server,
                2,
                ImmutableList.of(
                        mFetchJavaScriptPath, mTrustedScoringSignalsPath + mTrustedScoringParams),
                mRequestMatcherExactMatch);
        runAdScoringProcessLoggerLatch.await();
        assertEquals(1L, scoringOutcome.get(0).getAdWithScore().getScore().longValue());
        assertEquals(2L, scoringOutcome.get(1).getAdWithScore().getScore().longValue());
        assertEquals(5L, scoringOutcome.get(4).getAdWithScore().getScore().longValue());
        assertEquals(300, scoringOutcome.get(4).getAdWithScore().getAdWithBid().getBid(), 0);
        assertEquals(500, scoringOutcome.get(6).getAdWithScore().getAdWithBid().getBid(), 0);

        verifySuccessAdScoringLogging(
                mSellerDecisionLogicJs, mTrustedScoringSignals, mAdBiddingOutcomeList);
    }


    @Test
    public void testRunAdScoringContextual_withDebugReportingEnabled_Success() throws Exception {
        when(mAdSelectionExecutionLoggerClock.elapsedRealtime())
                .thenReturn(
                        RUN_AD_SCORING_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_END_TIMESTAMP,
                        GET_AD_SCORES_START_TIMESTAMP,
                        GET_TRUSTED_SCORING_SIGNALS_START_TIMESTAMP,
                        GET_TRUSTED_SCORING_SIGNALS_END_TIMESTAMP,
                        SCORE_ADS_START_TIMESTAMP,
                        SCORE_ADS_END_TIMESTAMP,
                        GET_AD_SCORES_END_TIMESTAMP,
                        RUN_AD_SCORING_END_TIMESTAMP);
        // Logger calls come after the callback is returned
        CountDownLatch runAdScoringProcessLoggerLatch = new CountDownLatch(1);
        doAnswer(
                unusedInvocation -> {
                    runAdScoringProcessLoggerLatch.countDown();
                    return null;
                })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());
        when(mDebugReporting.isEnabled()).thenReturn(true);

        List<Double> scores = ImmutableList.of(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0);
        MockWebServer server = mMockWebServerRule.startMockWebServer(mDefaultDispatcher);

        Uri decisionLogicUri = mMockWebServerRule.uriForPath(mFetchJavaScriptPath);

        Map<AdTechIdentifier, SignedContextualAds> contextualAdsMap = createContextualAds();
        mAdSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigWithSignedContextualAdsBuilder()
                        .setDecisionLogicUri(decisionLogicUri)
                        .setTrustedScoringSignalsUri(
                                mMockWebServerRule.uriForPath(mTrustedScoringSignalsPath))
                        .setPerBuyerSignedContextualAds(contextualAdsMap)
                        .build();

        List<AdWithBid> adsWithBid =
                mAdBiddingOutcomeList.stream()
                        .map(a -> a.getAdWithBid())
                        .collect(Collectors.toList());
        List<SignedContextualAds> signedContextualAds =
                mAdSelectionConfig.getPerBuyerSignedContextualAds().values().stream()
                        .collect(Collectors.toList());
        List<AdWithBid> contextualBidAds = new ArrayList<>();
        for (SignedContextualAds ctx : signedContextualAds) {
            contextualBidAds.addAll(ctx.getAdsWithBid());
        }

        adsWithBid.addAll(contextualBidAds);
        Answer<ListenableFuture<List<ScoreAdResult>>> loggerAnswer =
                unused -> {
                    mAdSelectionExecutionLogger.startScoreAds();
                    mAdSelectionExecutionLogger.endScoreAds();
                    return Futures.immediateFuture(
                            scores.stream()
                                    .map(
                                            score ->
                                                    ScoreAdResult.builder()
                                                            .setAdScore(score)
                                                            .setWinDebugReportUri(
                                                                    Uri.parse(
                                                                            "http://example.com/1"))
                                                            .setLossDebugReportUri(
                                                                    Uri.parse(
                                                                            "http://example.com/2"))
                                                            .setSellerRejectReason("invalid-bid")
                                                            .build())
                                    .collect(Collectors.toList()));
                };
        Mockito.when(
                        mMockAdSelectionScriptEngine.scoreAds(
                                mSellerDecisionLogicJs,
                                adsWithBid,
                                mAdSelectionConfig,
                                mAdSelectionConfig.getSellerSignals(),
                                mTrustedScoringSignals,
                                AdSelectionSignals.EMPTY,
                                mAdBiddingOutcomeList.stream()
                                        .map(
                                                a ->
                                                        a.getCustomAudienceBiddingInfo()
                                                                .getCustomAudienceSignals())
                                        .collect(Collectors.toList()),
                                mAdSelectionExecutionLogger))
                .thenAnswer(loggerAnswer);

        AdsScoreGeneratorImpl adsScoreGenerator =
                new AdsScoreGeneratorImpl(
                        mMockAdSelectionScriptEngine,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mSchedulingExecutor,
                        mWebClient,
                        mDevContext,
                        mAdSelectionEntryDao,
                        mFlags,
                        mAdSelectionExecutionLogger,
                        mDebugReporting,
                        false);
        FluentFuture<List<AdScoringOutcome>> scoringResultFuture =
                adsScoreGenerator.runAdScoring(mAdBiddingOutcomeList, mAdSelectionConfig);

        List<AdScoringOutcome> scoringOutcome = waitForFuture(() -> scoringResultFuture);

        Mockito.verify(mMockAdSelectionScriptEngine)
                .scoreAds(
                        mSellerDecisionLogicJs,
                        adsWithBid,
                        mAdSelectionConfig,
                        mAdSelectionConfig.getSellerSignals(),
                        mTrustedScoringSignals,
                        AdSelectionSignals.EMPTY,
                        mAdBiddingOutcomeList.stream()
                                .map(
                                        a ->
                                                a.getCustomAudienceBiddingInfo()
                                                        .getCustomAudienceSignals())
                                .collect(Collectors.toList()),
                        mAdSelectionExecutionLogger);

        mMockWebServerRule.verifyMockServerRequests(
                server,
                2,
                ImmutableList.of(
                        mFetchJavaScriptPath, mTrustedScoringSignalsPath + mTrustedScoringParams),
                mRequestMatcherExactMatch);
        runAdScoringProcessLoggerLatch.await();
        assertEquals(
                Uri.parse("http://example.com/1"),
                scoringOutcome.get(0).getDebugReport().getWinDebugReportUri());
        assertEquals(
                Uri.parse("http://example.com/2"),
                scoringOutcome.get(0).getDebugReport().getLossDebugReportUri());
        assertEquals("invalid-bid", scoringOutcome.get(0).getDebugReport().getSellerRejectReason());
        assertEquals(
                Uri.parse("http://example.com/1"),
                scoringOutcome.get(1).getDebugReport().getWinDebugReportUri());
        assertEquals(
                Uri.parse("http://example.com/2"),
                scoringOutcome.get(1).getDebugReport().getLossDebugReportUri());
        assertEquals("invalid-bid", scoringOutcome.get(1).getDebugReport().getSellerRejectReason());

        verifySuccessAdScoringLogging(
                mSellerDecisionLogicJs, mTrustedScoringSignals, mAdBiddingOutcomeList);
    }

    @Test
    public void testRunAdScoringContextual_UseOverride_Success() throws Exception {
        when(mAdSelectionExecutionLoggerClock.elapsedRealtime())
                .thenReturn(
                        RUN_AD_SCORING_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_END_TIMESTAMP,
                        GET_AD_SCORES_START_TIMESTAMP,
                        GET_TRUSTED_SCORING_SIGNALS_START_TIMESTAMP,
                        GET_TRUSTED_SCORING_SIGNALS_END_TIMESTAMP,
                        SCORE_ADS_START_TIMESTAMP,
                        SCORE_ADS_END_TIMESTAMP,
                        GET_AD_SCORES_END_TIMESTAMP,
                        RUN_AD_SCORING_END_TIMESTAMP);
        // Logger calls come after the callback is returned
        CountDownLatch runAdScoringProcessLoggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            runAdScoringProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());

        List<Double> scores = ImmutableList.of(1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0);
        MockWebServer server = mMockWebServerRule.startMockWebServer(mDefaultDispatcher);

        Uri decisionLogicUri = mMockWebServerRule.uriForPath(mFetchJavaScriptPath);

        Map<AdTechIdentifier, SignedContextualAds> contextualAdsMap = createContextualAds();
        mAdSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigWithSignedContextualAdsBuilder()
                        .setDecisionLogicUri(decisionLogicUri)
                        .setTrustedScoringSignalsUri(
                                mMockWebServerRule.uriForPath(mTrustedScoringSignalsPath))
                        .setPerBuyerSignedContextualAds(contextualAdsMap)
                        .build();

        List<AdWithBid> adsWithBid =
                mAdBiddingOutcomeList.stream()
                        .map(a -> a.getAdWithBid())
                        .collect(Collectors.toList());
        List<SignedContextualAds> signedContextualAds =
                mAdSelectionConfig.getPerBuyerSignedContextualAds().values().stream()
                        .collect(Collectors.toList());
        List<AdWithBid> contextualBidAds = new ArrayList<>();
        for (SignedContextualAds ctx : signedContextualAds) {
            contextualBidAds.addAll(ctx.getAdsWithBid());
        }

        final String fakeDecisionLogicForBuyer = "\"reportWin() { completely fake }\"";
        // Create an override for buyers decision logic only for Buyer 2
        String myAppPackageName = "com.google.ppapi.test";
        DBAdSelectionOverride adSelectionOverride =
                DBAdSelectionOverride.builder()
                        .setAdSelectionConfigId(
                                AdSelectionDevOverridesHelper.calculateAdSelectionConfigId(
                                        mAdSelectionConfig))
                        .setAppPackageName(myAppPackageName)
                        .setDecisionLogicJS(mSellerDecisionLogicJs)
                        .setTrustedScoringSignals(mTrustedScoringSignals.toString())
                        .build();
        mAdSelectionEntryDao.persistAdSelectionOverride(adSelectionOverride);
        DBBuyerDecisionOverride buyerDecisionOverride =
                DBBuyerDecisionOverride.builder()
                        .setAdSelectionConfigId(
                                AdSelectionDevOverridesHelper.calculateAdSelectionConfigId(
                                        mAdSelectionConfig))
                        .setAppPackageName(myAppPackageName)
                        .setDecisionLogic(fakeDecisionLogicForBuyer)
                        .setBuyer(BUYER_2)
                        .build();
        mAdSelectionEntryDao.persistPerBuyerDecisionLogicOverride(
                ImmutableList.of(buyerDecisionOverride));
        mDevContext =
                DevContext.builder()
                        .setDevOptionsEnabled(true)
                        .setCallingAppPackageName(myAppPackageName)
                        .build();

        adsWithBid.addAll(contextualBidAds);
        Answer<ListenableFuture<List<ScoreAdResult>>> loggerAnswer =
                unused -> {
                    mAdSelectionExecutionLogger.startScoreAds();
                    mAdSelectionExecutionLogger.endScoreAds();
                    return Futures.immediateFuture(
                            scores.stream()
                                    .map(
                                            score ->
                                                    ScoreAdResult.builder()
                                                            .setAdScore(score)
                                                            .build())
                                    .collect(Collectors.toList()));
                };
        Mockito.when(
                        mMockAdSelectionScriptEngine.scoreAds(
                                mSellerDecisionLogicJs,
                                adsWithBid,
                                mAdSelectionConfig,
                                mAdSelectionConfig.getSellerSignals(),
                                mTrustedScoringSignals,
                                AdSelectionSignals.EMPTY,
                                mAdBiddingOutcomeList.stream()
                                        .map(
                                                a ->
                                                        a.getCustomAudienceBiddingInfo()
                                                                .getCustomAudienceSignals())
                                        .collect(Collectors.toList()),
                                mAdSelectionExecutionLogger))
                .thenAnswer(loggerAnswer);

        mAdsScoreGenerator =
                new AdsScoreGeneratorImpl(
                        mMockAdSelectionScriptEngine,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mSchedulingExecutor,
                        mWebClient,
                        mDevContext,
                        mAdSelectionEntryDao,
                        mFlags,
                        mAdSelectionExecutionLogger,
                        mDebugReporting,
                        false);
        FluentFuture<List<AdScoringOutcome>> scoringResultFuture =
                mAdsScoreGenerator.runAdScoring(mAdBiddingOutcomeList, mAdSelectionConfig);

        List<AdScoringOutcome> scoringOutcome = waitForFuture(() -> scoringResultFuture);

        Mockito.verify(mMockAdSelectionScriptEngine)
                .scoreAds(
                        mSellerDecisionLogicJs,
                        adsWithBid,
                        mAdSelectionConfig,
                        mAdSelectionConfig.getSellerSignals(),
                        mTrustedScoringSignals,
                        AdSelectionSignals.EMPTY,
                        mAdBiddingOutcomeList.stream()
                                .map(
                                        a ->
                                                a.getCustomAudienceBiddingInfo()
                                                        .getCustomAudienceSignals())
                                .collect(Collectors.toList()),
                        mAdSelectionExecutionLogger);

        // No calls should have been made to the server, as overrides are set
        mMockWebServerRule.verifyMockServerRequests(
                server, 0, ImmutableList.of(), mRequestMatcherExactMatch);
        runAdScoringProcessLoggerLatch.await();
        assertEquals(1L, scoringOutcome.get(0).getAdWithScore().getScore().longValue());
        assertEquals(2L, scoringOutcome.get(1).getAdWithScore().getScore().longValue());
        assertEquals(5L, scoringOutcome.get(4).getAdWithScore().getScore().longValue());
        assertEquals(300, scoringOutcome.get(4).getAdWithScore().getAdWithBid().getBid(), 0);
        assertEquals(500, scoringOutcome.get(6).getAdWithScore().getAdWithBid().getBid(), 0);
        validateCustomAudienceSignals(scoringOutcome.get(6).getCustomAudienceSignals(), BUYER_2);

        // Only buyer2 decision logic should have been populated from overrides
        assertFalse(
                "Buyer 1 should not have gotten decision logic",
                scoringOutcome.get(4).isBiddingLogicJsDownloaded());
        assertTrue(
                "Buyer 2 ctx ads should have gotten decision logic from overrides",
                scoringOutcome.get(5).isBiddingLogicJsDownloaded()
                        && scoringOutcome.get(6).isBiddingLogicJsDownloaded());
        assertEquals(fakeDecisionLogicForBuyer, scoringOutcome.get(6).getBiddingLogicJs());

        verifySuccessAdScoringLogging(
                mSellerDecisionLogicJs, mTrustedScoringSignals, mAdBiddingOutcomeList);
    }

    @Test
    public void testRunAdScoringContextualScoresMismatch_Failure() throws Exception {
        when(mAdSelectionExecutionLoggerClock.elapsedRealtime())
                .thenReturn(
                        RUN_AD_SCORING_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_END_TIMESTAMP,
                        GET_AD_SCORES_START_TIMESTAMP,
                        GET_TRUSTED_SCORING_SIGNALS_START_TIMESTAMP,
                        GET_TRUSTED_SCORING_SIGNALS_END_TIMESTAMP,
                        SCORE_ADS_START_TIMESTAMP,
                        SCORE_ADS_END_TIMESTAMP,
                        GET_AD_SCORES_END_TIMESTAMP,
                        RUN_AD_SCORING_END_TIMESTAMP);
        // Logger calls come after the callback is returned
        CountDownLatch runAdScoringProcessLoggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            runAdScoringProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());

        // Ads expected to be scored are 7, but scoring is wired to return only 6 scores
        List<Double> scores = ImmutableList.of(1.0, 2.0, 3.0, 4.0, 5.0, 6.0);
        MockWebServer server = mMockWebServerRule.startMockWebServer(mDefaultDispatcher);

        Uri decisionLogicUri = mMockWebServerRule.uriForPath(mFetchJavaScriptPath);

        Map<AdTechIdentifier, SignedContextualAds> contextualAdsMap = createContextualAds();
        mAdSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigWithSignedContextualAdsBuilder()
                        .setDecisionLogicUri(decisionLogicUri)
                        .setTrustedScoringSignalsUri(
                                mMockWebServerRule.uriForPath(mTrustedScoringSignalsPath))
                        .setPerBuyerSignedContextualAds(contextualAdsMap)
                        .build();

        List<AdWithBid> adsWithBid =
                mAdBiddingOutcomeList.stream()
                        .map(a -> a.getAdWithBid())
                        .collect(Collectors.toList());
        List<SignedContextualAds> signedContextualAds =
                mAdSelectionConfig.getPerBuyerSignedContextualAds().values().stream()
                        .collect(Collectors.toList());
        List<AdWithBid> contextualBidAds = new ArrayList<>();
        for (SignedContextualAds ctx : signedContextualAds) {
            contextualBidAds.addAll(ctx.getAdsWithBid());
        }

        adsWithBid.addAll(contextualBidAds);
        Answer<ListenableFuture<List<ScoreAdResult>>> loggerAnswer =
                unused -> {
                    mAdSelectionExecutionLogger.startScoreAds();
                    mAdSelectionExecutionLogger.endScoreAds();
                    return Futures.immediateFuture(
                            scores.stream()
                                    .map(
                                            score ->
                                                    ScoreAdResult.builder()
                                                            .setAdScore(score)
                                                            .build())
                                    .collect(Collectors.toList()));
                };
        Mockito.when(
                        mMockAdSelectionScriptEngine.scoreAds(
                                mSellerDecisionLogicJs,
                                adsWithBid,
                                mAdSelectionConfig,
                                mAdSelectionConfig.getSellerSignals(),
                                mTrustedScoringSignals,
                                AdSelectionSignals.EMPTY,
                                mAdBiddingOutcomeList.stream()
                                        .map(
                                                a ->
                                                        a.getCustomAudienceBiddingInfo()
                                                                .getCustomAudienceSignals())
                                        .collect(Collectors.toList()),
                                mAdSelectionExecutionLogger))
                .thenAnswer(loggerAnswer);

        FluentFuture<List<AdScoringOutcome>> scoringResultFuture =
                mAdsScoreGenerator.runAdScoring(mAdBiddingOutcomeList, mAdSelectionConfig);

        IllegalStateException missingScoresException =
                new IllegalStateException(SCORES_COUNT_LESS_THAN_EXPECTED);

        ExecutionException outException =
                assertThrows(ExecutionException.class, scoringResultFuture::get);
        assertEquals(outException.getCause().getMessage(), missingScoresException.getMessage());
    }

    @Test
    public void testMissingTrustedSignalsException() throws Exception {
        when(mAdSelectionExecutionLoggerClock.elapsedRealtime())
                .thenReturn(
                        RUN_AD_SCORING_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_END_TIMESTAMP,
                        GET_AD_SCORES_START_TIMESTAMP,
                        GET_TRUSTED_SCORING_SIGNALS_START_TIMESTAMP,
                        RUN_AD_SCORING_END_TIMESTAMP);
        // Logger calls come after the callback is returned
        CountDownLatch runAdScoringProcessLoggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            runAdScoringProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());

        // Missing server connection for trusted signals
        Dispatcher missingSignalsDispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        if (mFetchJavaScriptPath.equals(request.getPath())) {
                            return new MockResponse().setBody(mSellerDecisionLogicJs);
                        }
                        return new MockResponse().setResponseCode(404);
                    }
                };
        MockWebServer server = mMockWebServerRule.startMockWebServer(missingSignalsDispatcher);

        Uri decisionLogicUri = mMockWebServerRule.uriForPath(mFetchJavaScriptPath);

        IllegalStateException missingSignalsException =
                new IllegalStateException(MISSING_TRUSTED_SCORING_SIGNALS);

        mAdSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setDecisionLogicUri(decisionLogicUri)
                        .setTrustedScoringSignalsUri(
                                mMockWebServerRule.uriForPath(mTrustedScoringSignalsPath))
                        .build();

        FluentFuture<List<AdScoringOutcome>> scoringResultFuture =
                mAdsScoreGenerator.runAdScoring(mAdBiddingOutcomeList, mAdSelectionConfig);
        runAdScoringProcessLoggerLatch.await();
        ExecutionException outException =
                assertThrows(ExecutionException.class, scoringResultFuture::get);
        assertEquals(outException.getCause().getMessage(), missingSignalsException.getMessage());
        mMockWebServerRule.verifyMockServerRequests(
                server,
                2,
                ImmutableList.of(
                        mFetchJavaScriptPath, mTrustedScoringSignalsPath + mTrustedScoringParams),
                mRequestMatcherExactMatch);
        verifyFailedAdScoringLoggingMissingTrustedScoringSignals(
                mSellerDecisionLogicJs,
                mAdBiddingOutcomeList,
                AdServicesLoggerUtil.getResultCodeFromException(
                        missingSignalsException.getCause()));
    }

    @Test
    public void testRunAdScoringUseDevOverrideForJS() throws Exception {
        when(mAdSelectionExecutionLoggerClock.elapsedRealtime())
                .thenReturn(
                        RUN_AD_SCORING_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_END_TIMESTAMP,
                        GET_AD_SCORES_START_TIMESTAMP,
                        GET_TRUSTED_SCORING_SIGNALS_START_TIMESTAMP,
                        GET_TRUSTED_SCORING_SIGNALS_END_TIMESTAMP,
                        SCORE_ADS_START_TIMESTAMP,
                        SCORE_ADS_END_TIMESTAMP,
                        GET_AD_SCORES_END_TIMESTAMP,
                        RUN_AD_SCORING_END_TIMESTAMP);
        // Logger calls come after the callback is returned
        CountDownLatch runAdScoringProcessLoggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            runAdScoringProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());

        List<Double> scores = ImmutableList.of(1.0, 2.0);
        MockWebServer server = mMockWebServerRule.startMockWebServer(mDefaultDispatcher);
        Uri decisionLogicUri = mMockWebServerRule.uriForPath(mFetchJavaScriptPath);

        // Different seller decision logic JS to simulate different different override from server
        String differentSellerDecisionLogicJs =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) {"
                        + " \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":2}', 'reporting_uri': '"
                        + " /reporting/seller "
                        + "' } };\n"
                        + "}";

        mAdSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setDecisionLogicUri(decisionLogicUri)
                        .setTrustedScoringSignalsUri(
                                mMockWebServerRule.uriForPath(mTrustedScoringSignalsPath))
                        .build();

        // Set dev override for this AdSelection
        String myAppPackageName = "com.google.ppapi.test";
        DBAdSelectionOverride adSelectionOverride =
                DBAdSelectionOverride.builder()
                        .setAdSelectionConfigId(
                                AdSelectionDevOverridesHelper.calculateAdSelectionConfigId(
                                        mAdSelectionConfig))
                        .setAppPackageName(myAppPackageName)
                        .setDecisionLogicJS(differentSellerDecisionLogicJs)
                        .setTrustedScoringSignals(mTrustedScoringSignals.toString())
                        .build();
        mAdSelectionEntryDao.persistAdSelectionOverride(adSelectionOverride);

        // Resetting Generator to use new dev context
        mDevContext =
                DevContext.builder()
                        .setDevOptionsEnabled(true)
                        .setCallingAppPackageName(myAppPackageName)
                        .build();

        mAdsScoreGenerator =
                new AdsScoreGeneratorImpl(
                        mMockAdSelectionScriptEngine,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mSchedulingExecutor,
                        mWebClient,
                        mDevContext,
                        mAdSelectionEntryDao,
                        mFlags,
                        mAdSelectionExecutionLogger,
                        mDebugReporting,
                        false);
        Answer<ListenableFuture<List<ScoreAdResult>>> loggerAnswer =
                unused -> {
                    mAdSelectionExecutionLogger.startScoreAds();
                    mAdSelectionExecutionLogger.endScoreAds();
                    return Futures.immediateFuture(
                            scores.stream()
                                    .map(
                                            score ->
                                                    ScoreAdResult.builder()
                                                            .setAdScore(score)
                                                            .build())
                                    .collect(Collectors.toList()));
                };
        Mockito.when(
                        mMockAdSelectionScriptEngine.scoreAds(
                                differentSellerDecisionLogicJs,
                                mAdBiddingOutcomeList.stream()
                                        .map(AdBiddingOutcome::getAdWithBid)
                                        .collect(Collectors.toList()),
                                mAdSelectionConfig,
                                mAdSelectionConfig.getSellerSignals(),
                                mTrustedScoringSignals,
                                AdSelectionSignals.EMPTY,
                                mAdBiddingOutcomeList.stream()
                                        .map(
                                                a ->
                                                        a.getCustomAudienceBiddingInfo()
                                                                .getCustomAudienceSignals())
                                        .collect(Collectors.toList()),
                                mAdSelectionExecutionLogger))
                .thenAnswer(loggerAnswer);

        FluentFuture<List<AdScoringOutcome>> scoringResultFuture =
                mAdsScoreGenerator.runAdScoring(mAdBiddingOutcomeList, mAdSelectionConfig);

        List<AdScoringOutcome> scoringOutcome = waitForFuture(() -> scoringResultFuture);
        runAdScoringProcessLoggerLatch.await();
        // The server will not be invoked as the web calls should be overridden
        mMockWebServerRule.verifyMockServerRequests(
                server, 0, Collections.emptyList(), mRequestMatcherExactMatch);
        Assert.assertEquals(1L, scoringOutcome.get(0).getAdWithScore().getScore().longValue());
        Assert.assertEquals(2L, scoringOutcome.get(1).getAdWithScore().getScore().longValue());
        verifySuccessAdScoringLogging(
                differentSellerDecisionLogicJs, mTrustedScoringSignals, mAdBiddingOutcomeList);
    }

    @Test
    public void testRunAdScoringJsonException() throws Exception {
        when(mAdSelectionExecutionLoggerClock.elapsedRealtime())
                .thenReturn(
                        RUN_AD_SCORING_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_END_TIMESTAMP,
                        GET_AD_SCORES_START_TIMESTAMP,
                        GET_TRUSTED_SCORING_SIGNALS_START_TIMESTAMP,
                        GET_TRUSTED_SCORING_SIGNALS_END_TIMESTAMP,
                        RUN_AD_SCORING_END_TIMESTAMP);
        // Logger calls come after the callback is returned
        CountDownLatch runAdScoringProcessLoggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            runAdScoringProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());

        MockWebServer server = mMockWebServerRule.startMockWebServer(mDefaultDispatcher);
        Uri decisionLogicUri = mMockWebServerRule.uriForPath(mFetchJavaScriptPath);

        mAdSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setDecisionLogicUri(decisionLogicUri)
                        .setTrustedScoringSignalsUri(
                                mMockWebServerRule.uriForPath(mTrustedScoringSignalsPath))
                        .build();

        Mockito.when(
                        mMockAdSelectionScriptEngine.scoreAds(
                                mSellerDecisionLogicJs,
                                mAdBiddingOutcomeList.stream()
                                        .map(a -> a.getAdWithBid())
                                        .collect(Collectors.toList()),
                                mAdSelectionConfig,
                                mAdSelectionConfig.getSellerSignals(),
                                mTrustedScoringSignals,
                                AdSelectionSignals.EMPTY,
                                mAdBiddingOutcomeList.stream()
                                        .map(
                                                a ->
                                                        a.getCustomAudienceBiddingInfo()
                                                                .getCustomAudienceSignals())
                                        .collect(Collectors.toList()),
                                mAdSelectionExecutionLogger))
                .thenThrow(new JSONException("Badly formatted JSON"));

        FluentFuture<List<AdScoringOutcome>> scoringResultFuture =
                mAdsScoreGenerator.runAdScoring(mAdBiddingOutcomeList, mAdSelectionConfig);
        runAdScoringProcessLoggerLatch.await();
        ExecutionException adServicesException =
                Assert.assertThrows(
                        ExecutionException.class,
                        () -> {
                            waitForFuture(() -> scoringResultFuture);
                        });
        Truth.assertThat(adServicesException.getMessage()).contains("Badly formatted JSON");
        mMockWebServerRule.verifyMockServerRequests(
                server,
                2,
                ImmutableList.of(
                        mFetchJavaScriptPath, mTrustedScoringSignalsPath + mTrustedScoringParams),
                mRequestMatcherExactMatch);
        verifyFailedAdScoringLoggingJSONExceptionWithScoreAds(
                mSellerDecisionLogicJs,
                mTrustedScoringSignals,
                mAdBiddingOutcomeList,
                AdServicesLoggerUtil.getResultCodeFromException(adServicesException.getCause()));
    }

    @Test
    public void testRunAdScoringTimesOut() throws Exception {
        when(mAdSelectionExecutionLoggerClock.elapsedRealtime())
                .thenReturn(
                        RUN_AD_SCORING_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_START_TIMESTAMP,
                        GET_AD_SELECTION_LOGIC_END_TIMESTAMP,
                        GET_AD_SCORES_START_TIMESTAMP,
                        GET_TRUSTED_SCORING_SIGNALS_START_TIMESTAMP,
                        GET_TRUSTED_SCORING_SIGNALS_END_TIMESTAMP,
                        RUN_AD_SCORING_END_TIMESTAMP);
        // Logger calls come after the callback is returned
        CountDownLatch runAdScoringProcessLoggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            runAdScoringProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());

        Flags flagsWithSmallerLimits =
                new Flags() {
                    @Override
                    public long getAdSelectionScoringTimeoutMs() {
                        return 100;
                    }
                };
        mAdsScoreGenerator =
                new AdsScoreGeneratorImpl(
                        mMockAdSelectionScriptEngine,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mSchedulingExecutor,
                        mWebClient,
                        mDevContext,
                        mAdSelectionEntryDao,
                        flagsWithSmallerLimits,
                        mAdSelectionExecutionLogger,
                        mDebugReporting,
                        false);

        List<Double> scores = Arrays.asList(1.0, 2.0);
        mMockWebServerRule.startMockWebServer(mDefaultDispatcher);

        Uri decisionLogicUri = mMockWebServerRule.uriForPath(mFetchJavaScriptPath);

        mAdSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setDecisionLogicUri(decisionLogicUri)
                        .setTrustedScoringSignalsUri(
                                mMockWebServerRule.uriForPath(mTrustedScoringSignalsPath))
                        .build();

        Mockito.when(
                        mMockAdSelectionScriptEngine.scoreAds(
                                mSellerDecisionLogicJs,
                                mAdBiddingOutcomeList.stream()
                                        .map(a -> a.getAdWithBid())
                                        .collect(Collectors.toList()),
                                mAdSelectionConfig,
                                mAdSelectionConfig.getSellerSignals(),
                                mTrustedScoringSignals,
                                AdSelectionSignals.EMPTY,
                                mAdBiddingOutcomeList.stream()
                                        .map(
                                                a ->
                                                        a.getCustomAudienceBiddingInfo()
                                                                .getCustomAudienceSignals())
                                        .collect(Collectors.toList()),
                                mAdSelectionExecutionLogger))
                .thenAnswer((invocation) -> getScoresWithDelay(scores, flagsWithSmallerLimits));

        FluentFuture<List<AdScoringOutcome>> scoringResultFuture =
                mAdsScoreGenerator.runAdScoring(mAdBiddingOutcomeList, mAdSelectionConfig);
        runAdScoringProcessLoggerLatch.await();
        ExecutionException thrown =
                assertThrows(ExecutionException.class, scoringResultFuture::get);
        assertTrue(thrown.getMessage().contains(SCORING_TIMED_OUT));
        verifyFailedAdScoringLoggingTimeout(
                mAdBiddingOutcomeList,
                AdServicesLoggerUtil.getResultCodeFromException(thrown.getCause()));
    }

    private void verifyFailedAdScoringLoggingTimeout(
            List<AdBiddingOutcome> adBiddingOutcomeList, int resultCode) {
        int numOfCAsEnteringScoring =
                adBiddingOutcomeList.stream()
                        .filter(Objects::nonNull)
                        .map(a -> a.getCustomAudienceBiddingInfo())
                        .filter(Objects::nonNull)
                        .map(a -> a.getCustomAudienceSignals().hashCode())
                        .collect(Collectors.toSet())
                        .size();
        int numOfAdsEnteringScoring =
                adBiddingOutcomeList.stream()
                        .filter(Objects::nonNull)
                        .map(AdBiddingOutcome::getAdWithBid)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet())
                        .size();
        verify(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(
                        mRunAdScoringProcessReportedStatsArgumentCaptor.capture());
        RunAdScoringProcessReportedStats runAdScoringProcessReportedStats =
                mRunAdScoringProcessReportedStatsArgumentCaptor.getValue();
        // Timeout exception could be thrown at any stage of the RunAdScoring process, so we only
        // verify partial logging of the start and the end stage of RunAdScoring.
        assertThat(runAdScoringProcessReportedStats.getNumOfCasEnteringScoring())
                .isEqualTo(numOfCAsEnteringScoring);
        assertThat(runAdScoringProcessReportedStats.getNumOfRemarketingAdsEnteringScoring())
                .isEqualTo(numOfAdsEnteringScoring);
        assertThat(runAdScoringProcessReportedStats.getNumOfContextualAdsEnteringScoring())
                .isEqualTo(STATUS_UNSET);
        assertThat(runAdScoringProcessReportedStats.getRunAdScoringResultCode())
                .isEqualTo(resultCode);
    }

    private void verifyFailedAdScoringLoggingJSONExceptionWithScoreAds(
            String sellerDecisionLogicJs,
            AdSelectionSignals trustedScoringSignals,
            List<AdBiddingOutcome> adBiddingOutcomeList,
            int resultCode) {
        int fetchedAdSelectionLogicScriptSizeInBytes = sellerDecisionLogicJs.getBytes().length;
        int fetchedTrustedScoringSignalsDataSizeInBytes =
                trustedScoringSignals.toString().getBytes().length;
        int numOfCAsEnteringScoring =
                adBiddingOutcomeList.stream()
                        .filter(Objects::nonNull)
                        .map(a -> a.getCustomAudienceBiddingInfo())
                        .filter(Objects::nonNull)
                        .map(a -> a.getCustomAudienceSignals().hashCode())
                        .collect(Collectors.toSet())
                        .size();
        int numOfAdsEnteringScoring =
                adBiddingOutcomeList.stream()
                        .filter(Objects::nonNull)
                        .map(AdBiddingOutcome::getAdWithBid)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet())
                        .size();
        verify(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(
                        mRunAdScoringProcessReportedStatsArgumentCaptor.capture());
        RunAdScoringProcessReportedStats runAdScoringProcessReportedStats =
                mRunAdScoringProcessReportedStatsArgumentCaptor.getValue();
        assertThat(runAdScoringProcessReportedStats.getGetAdSelectionLogicLatencyInMillis())
                .isEqualTo(GET_AD_SELECTION_LOGIC_LATENCY_MS);
        assertThat(runAdScoringProcessReportedStats.getGetAdSelectionLogicResultCode())
                .isEqualTo(STATUS_SUCCESS);
        assertThat(runAdScoringProcessReportedStats.getGetAdSelectionLogicScriptType())
                .isEqualTo(SCRIPT_JAVASCRIPT);
        assertThat(runAdScoringProcessReportedStats.getFetchedAdSelectionLogicScriptSizeInBytes())
                .isEqualTo(fetchedAdSelectionLogicScriptSizeInBytes);
        assertThat(runAdScoringProcessReportedStats.getGetTrustedScoringSignalsLatencyInMillis())
                .isEqualTo(GET_TRUSTED_SCORING_SIGNALS_LATENCY_MS);
        assertThat(runAdScoringProcessReportedStats.getGetTrustedScoringSignalsResultCode())
                .isEqualTo(STATUS_SUCCESS);
        assertThat(
                        runAdScoringProcessReportedStats
                                .getFetchedTrustedScoringSignalsDataSizeInBytes())
                .isEqualTo(fetchedTrustedScoringSignalsDataSizeInBytes);
        assertThat(runAdScoringProcessReportedStats.getScoreAdsLatencyInMillis())
                .isEqualTo(STATUS_UNSET);
        assertThat(runAdScoringProcessReportedStats.getGetAdScoresLatencyInMillis())
                .isEqualTo((int) (RUN_AD_SCORING_END_TIMESTAMP - GET_AD_SCORES_START_TIMESTAMP));
        assertThat(runAdScoringProcessReportedStats.getGetAdScoresResultCode())
                .isEqualTo(resultCode);
        assertThat(runAdScoringProcessReportedStats.getNumOfCasEnteringScoring())
                .isEqualTo(numOfCAsEnteringScoring);
        assertThat(runAdScoringProcessReportedStats.getNumOfRemarketingAdsEnteringScoring())
                .isEqualTo(numOfAdsEnteringScoring);
        assertThat(runAdScoringProcessReportedStats.getNumOfContextualAdsEnteringScoring())
                .isEqualTo(STATUS_UNSET);
        assertThat(runAdScoringProcessReportedStats.getRunAdScoringLatencyInMillis())
                .isEqualTo(RUN_AD_SCORING_LATENCY_MS);
        assertThat(runAdScoringProcessReportedStats.getRunAdScoringResultCode())
                .isEqualTo(resultCode);
    }

    private void verifyFailedAdScoringLoggingMissingTrustedScoringSignals(
            String sellerDecisionLogicJs,
            List<AdBiddingOutcome> adBiddingOutcomeList,
            int resultCode) {
        int fetchedAdSelectionLogicScriptSizeInBytes = sellerDecisionLogicJs.getBytes().length;
        int numOfCAsEnteringScoring =
                adBiddingOutcomeList.stream()
                        .filter(Objects::nonNull)
                        .map(a -> a.getCustomAudienceBiddingInfo())
                        .filter(Objects::nonNull)
                        .map(a -> a.getCustomAudienceSignals().hashCode())
                        .collect(Collectors.toSet())
                        .size();
        int numOfAdsEnteringScoring =
                adBiddingOutcomeList.stream()
                        .filter(Objects::nonNull)
                        .map(AdBiddingOutcome::getAdWithBid)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet())
                        .size();
        verify(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(
                        mRunAdScoringProcessReportedStatsArgumentCaptor.capture());
        RunAdScoringProcessReportedStats runAdScoringProcessReportedStats =
                mRunAdScoringProcessReportedStatsArgumentCaptor.getValue();
        assertThat(runAdScoringProcessReportedStats.getGetAdSelectionLogicLatencyInMillis())
                .isEqualTo(GET_AD_SELECTION_LOGIC_LATENCY_MS);
        assertThat(runAdScoringProcessReportedStats.getGetAdSelectionLogicResultCode())
                .isEqualTo(STATUS_SUCCESS);
        assertThat(runAdScoringProcessReportedStats.getGetAdSelectionLogicScriptType())
                .isEqualTo(SCRIPT_JAVASCRIPT);
        assertThat(runAdScoringProcessReportedStats.getFetchedAdSelectionLogicScriptSizeInBytes())
                .isEqualTo(fetchedAdSelectionLogicScriptSizeInBytes);
        assertThat(runAdScoringProcessReportedStats.getGetTrustedScoringSignalsLatencyInMillis())
                .isEqualTo(
                        (int)
                                (RUN_AD_SCORING_END_TIMESTAMP
                                        - GET_TRUSTED_SCORING_SIGNALS_START_TIMESTAMP));
        assertThat(runAdScoringProcessReportedStats.getGetTrustedScoringSignalsResultCode())
                .isEqualTo(resultCode);
        assertThat(
                        runAdScoringProcessReportedStats
                                .getFetchedTrustedScoringSignalsDataSizeInBytes())
                .isEqualTo(STATUS_UNSET);
        assertThat(runAdScoringProcessReportedStats.getScoreAdsLatencyInMillis())
                .isEqualTo(STATUS_UNSET);
        assertThat(runAdScoringProcessReportedStats.getGetAdScoresLatencyInMillis())
                .isEqualTo((int) (RUN_AD_SCORING_END_TIMESTAMP - GET_AD_SCORES_START_TIMESTAMP));
        assertThat(runAdScoringProcessReportedStats.getGetAdScoresResultCode())
                .isEqualTo(resultCode);
        assertThat(runAdScoringProcessReportedStats.getNumOfCasEnteringScoring())
                .isEqualTo(numOfCAsEnteringScoring);
        assertThat(runAdScoringProcessReportedStats.getNumOfRemarketingAdsEnteringScoring())
                .isEqualTo(numOfAdsEnteringScoring);
        assertThat(runAdScoringProcessReportedStats.getNumOfContextualAdsEnteringScoring())
                .isEqualTo(STATUS_UNSET);
        assertThat(runAdScoringProcessReportedStats.getRunAdScoringLatencyInMillis())
                .isEqualTo(RUN_AD_SCORING_LATENCY_MS);
        assertThat(runAdScoringProcessReportedStats.getRunAdScoringResultCode())
                .isEqualTo(resultCode);
    }

    private void verifySuccessAdScoringLogging(
            String sellerDecisionLogicJs,
            AdSelectionSignals trustedScoringSignals,
            List<AdBiddingOutcome> adBiddingOutcomeList) {
        int fetchedAdSelectionLogicScriptSizeInBytes = sellerDecisionLogicJs.getBytes().length;
        int fetchedTrustedScoringSignalsDataSizeInBytes =
                trustedScoringSignals.toString().getBytes().length;
        int numOfCAsEnteringScoring =
                adBiddingOutcomeList.stream()
                        .filter(Objects::nonNull)
                        .map(a -> a.getCustomAudienceBiddingInfo())
                        .filter(Objects::nonNull)
                        .map(a -> a.getCustomAudienceSignals().hashCode())
                        .collect(Collectors.toSet())
                        .size();
        int numOfAdsEnteringScoring =
                adBiddingOutcomeList.stream()
                        .filter(Objects::nonNull)
                        .map(AdBiddingOutcome::getAdWithBid)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet())
                        .size();
        verify(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(
                        mRunAdScoringProcessReportedStatsArgumentCaptor.capture());
        RunAdScoringProcessReportedStats runAdScoringProcessReportedStats =
                mRunAdScoringProcessReportedStatsArgumentCaptor.getValue();
        assertThat(runAdScoringProcessReportedStats.getGetAdSelectionLogicLatencyInMillis())
                .isEqualTo(GET_AD_SELECTION_LOGIC_LATENCY_MS);
        assertThat(runAdScoringProcessReportedStats.getGetAdSelectionLogicResultCode())
                .isEqualTo(STATUS_SUCCESS);
        assertThat(runAdScoringProcessReportedStats.getGetAdSelectionLogicScriptType())
                .isEqualTo(SCRIPT_JAVASCRIPT);
        assertThat(runAdScoringProcessReportedStats.getFetchedAdSelectionLogicScriptSizeInBytes())
                .isEqualTo(fetchedAdSelectionLogicScriptSizeInBytes);
        assertThat(runAdScoringProcessReportedStats.getGetTrustedScoringSignalsLatencyInMillis())
                .isEqualTo(GET_TRUSTED_SCORING_SIGNALS_LATENCY_MS);
        assertThat(runAdScoringProcessReportedStats.getGetTrustedScoringSignalsResultCode())
                .isEqualTo(STATUS_SUCCESS);
        assertThat(
                        runAdScoringProcessReportedStats
                                .getFetchedTrustedScoringSignalsDataSizeInBytes())
                .isEqualTo(fetchedTrustedScoringSignalsDataSizeInBytes);
        assertThat(runAdScoringProcessReportedStats.getScoreAdsLatencyInMillis())
                .isEqualTo(SCORE_ADS_LATENCY_MS);
        assertThat(runAdScoringProcessReportedStats.getGetAdScoresLatencyInMillis())
                .isEqualTo(GET_AD_SCORES_LATENCY_MS);
        assertThat(runAdScoringProcessReportedStats.getGetAdScoresResultCode())
                .isEqualTo(STATUS_SUCCESS);
        assertThat(runAdScoringProcessReportedStats.getNumOfCasEnteringScoring())
                .isEqualTo(numOfCAsEnteringScoring);
        assertThat(runAdScoringProcessReportedStats.getNumOfRemarketingAdsEnteringScoring())
                .isEqualTo(numOfAdsEnteringScoring);
        assertThat(runAdScoringProcessReportedStats.getNumOfContextualAdsEnteringScoring())
                .isEqualTo(STATUS_UNSET);
        assertThat(runAdScoringProcessReportedStats.getRunAdScoringLatencyInMillis())
                .isEqualTo(RUN_AD_SCORING_LATENCY_MS);
        assertThat(runAdScoringProcessReportedStats.getRunAdScoringResultCode())
                .isEqualTo(STATUS_SUCCESS);
    }

    private ListenableFuture<List<Double>> getScoresWithDelay(
            List<Double> scores, @NonNull Flags flags) {
        return mBlockingExecutorService.submit(
                () -> {
                    Thread.sleep(2 * flags.getAdSelectionScoringTimeoutMs());
                    return scores;
                });
    }

    private void validateCustomAudienceSignals(
            CustomAudienceSignals signals, AdTechIdentifier buyer) {
        assertEquals(CONTEXTUAL_CA_NAME, signals.getName());
        assertEquals(buyer.toString(), signals.getOwner());
        assertEquals(buyer, signals.getBuyer());
        assertEquals(AdSelectionSignals.EMPTY, signals.getUserBiddingSignals());
    }

    private Map<AdTechIdentifier, SignedContextualAds> createContextualAds() {
        Map<AdTechIdentifier, SignedContextualAds> buyerContextualAds = new HashMap<>();

        AdTechIdentifier buyer1 = BUYER_1;
        SignedContextualAds contextualAds1 =
                SignedContextualAdsFixture.aContextualAdsWithEmptySignatureBuilder(
                                buyer1, ImmutableList.of(100.0, 200.0, 300.0))
                        .build();

        AdTechIdentifier buyer2 = CommonFixture.VALID_BUYER_2;
        SignedContextualAds contextualAds2 =
                SignedContextualAdsFixture.aContextualAdsWithEmptySignatureBuilder(
                                buyer2, ImmutableList.of(400.0, 500.0))
                        .build();

        buyerContextualAds.put(buyer1, contextualAds1);
        buyerContextualAds.put(buyer2, contextualAds2);

        return buyerContextualAds;
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

    interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    private static class AdsScoreGeneratorImplTestFlags implements Flags {

        @Override
        public boolean getFledgeAdSelectionContextualAdsEnabled() {
            return false;
        }

        @Override
        public long getAdSelectionScoringTimeoutMs() {
            return EXTENDED_FLEDGE_AD_SELECTION_SCORING_TIMEOUT_MS;
        }

        @Override
        public long getAdSelectionOverallTimeoutMs() {
            return EXTENDED_FLEDGE_AD_SELECTION_OVERALL_TIMEOUT_MS;
        }
    }
}
