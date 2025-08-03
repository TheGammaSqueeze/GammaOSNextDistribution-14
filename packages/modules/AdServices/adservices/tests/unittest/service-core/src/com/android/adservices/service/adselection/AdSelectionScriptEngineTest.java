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

import static com.android.adservices.service.adselection.AdSelectionScriptEngine.NUM_BITS_STOCHASTIC_ROUNDING;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;

import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdWithBid;
import android.adservices.common.AdData;
import android.adservices.common.AdDataFixture;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;
import android.adservices.customaudience.CustomAudienceFixture;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.FlakyTest;
import androidx.test.filters.SmallTest;

import com.android.adservices.LoggerFactory;
import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.data.adselection.CustomAudienceSignals;
import com.android.adservices.data.adselection.datahandlers.AdSelectionResultBidAndUri;
import com.android.adservices.data.customaudience.AdDataConversionStrategy;
import com.android.adservices.data.customaudience.AdDataConversionStrategyFactory;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.service.adselection.AdSelectionScriptEngine.AuctionScriptResult;
import com.android.adservices.service.common.NoOpRetryStrategyImpl;
import com.android.adservices.service.common.RetryStrategy;
import com.android.adservices.service.exception.JSExecutionException;
import com.android.adservices.service.js.IsolateSettings;
import com.android.adservices.service.js.JSScriptArgument;
import com.android.adservices.service.js.JSScriptEngine;
import com.android.adservices.service.signals.ProtectedSignal;
import com.android.adservices.service.signals.ProtectedSignalsFixture;
import com.android.adservices.service.stats.AdSelectionExecutionLogger;
import com.android.adservices.service.stats.RunAdBiddingPerCAExecutionLogger;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.ListenableFuture;

import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@SmallTest
public class AdSelectionScriptEngineTest {
    protected static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final String TAG = "AdSelectionScriptEngineTest";

    private static final AdDataArgumentUtil AD_DATA_ARGUMENT_UTIL_WITHOUT_COPIER =
            new AdDataArgumentUtil(new AdCounterKeyCopierNoOpImpl());
    private static final AdDataArgumentUtil AD_DATA_ARGUMENT_UTIL_WITH_COPIER =
            new AdDataArgumentUtil(new AdCounterKeyCopierImpl());

    private static final AdDataConversionStrategy AD_DATA_CONVERSION_STRATEGY =
            AdDataConversionStrategyFactory.getAdDataConversionStrategy(true, true);
    private static final String BASE_DOMAIN = "https://www.domain.com/adverts/";
    private static final double BID_1 = 1.1;
    private static final double BID_2 = 2.1;
    private static final AdCost AD_COST_1 = new AdCost(1.2, NUM_BITS_STOCHASTIC_ROUNDING);
    private static final AdCost AD_COST_2 = new AdCost(2.2, NUM_BITS_STOCHASTIC_ROUNDING);
    private static final AdData AD_DATA_WITH_DOUBLE_RESULT_1 =
            getAdDataWithResult("123", Double.toString(BID_1), ImmutableSet.of());
    private static final AdData AD_DATA_WITH_DOUBLE_WITH_AD_COUNTER_KEYS_RESULT_1 =
            getAdDataWithResult("123", Double.toString(BID_1), AdDataFixture.getAdCounterKeys());
    private static final AdData AD_DATA_WITH_DOUBLE_AD_COST_1 =
            getAdDataWithBidAndCost(
                    "123", Double.toString(BID_1), AD_COST_1.toString(), ImmutableSet.of());
    private static final AdData AD_DATA_WITH_DOUBLE_RESULT_2 =
            getAdDataWithResult("456", Double.toString(BID_2), ImmutableSet.of());
    private static final AdData AD_DATA_WITH_DOUBLE_WITH_AD_COUNTER_KEYS_RESULT_2 =
            getAdDataWithResult("456", Double.toString(BID_2), AdDataFixture.getAdCounterKeys());
    private static final AdData AD_DATA_WITH_DOUBLE_AD_COST_2 =
            getAdDataWithBidAndCost(
                    "456", Double.toString(BID_2), AD_COST_2.toString(), ImmutableSet.of());
    private static final AdData AD_DATA_WITH_DOUBLE_AD_COST_EMPTY =
            getAdDataWithBidAndCost("456", Double.toString(BID_2), "\"junk\"", ImmutableSet.of());
    private static final List<AdData> AD_DATA_WITH_DOUBLE_RESULT_LIST =
            ImmutableList.of(AD_DATA_WITH_DOUBLE_RESULT_1, AD_DATA_WITH_DOUBLE_RESULT_2);
    private static final List<AdData> AD_DATA_WITH_DOUBLE_WITH_AD_COUNTER_KEYS_RESULT_LIST =
            ImmutableList.of(
                    AD_DATA_WITH_DOUBLE_WITH_AD_COUNTER_KEYS_RESULT_1,
                    AD_DATA_WITH_DOUBLE_WITH_AD_COUNTER_KEYS_RESULT_2);
    private static final List<AdData> AD_DATA_WITH_DOUBLE_WITH_AD_COST_LIST =
            ImmutableList.of(AD_DATA_WITH_DOUBLE_AD_COST_1, AD_DATA_WITH_DOUBLE_AD_COST_2);
    private static final List<AdData> AD_DATA_WITH_DOUBLE_WITH_EMPTY_AD_COST =
            ImmutableList.of(AD_DATA_WITH_DOUBLE_AD_COST_1, AD_DATA_WITH_DOUBLE_AD_COST_EMPTY);

    private static final AdWithBid AD_WITH_BID_1 =
            new AdWithBid(AD_DATA_WITH_DOUBLE_RESULT_1, BID_1);
    private static final AdWithBid AD_WITH_BID_WITH_AD_COUNTER_KEYS_1 =
            new AdWithBid(AD_DATA_WITH_DOUBLE_WITH_AD_COUNTER_KEYS_RESULT_1, BID_1);
    private static final AdWithBid AD_WITH_BID_2 =
            new AdWithBid(AD_DATA_WITH_DOUBLE_RESULT_2, BID_2);
    private static final AdWithBid AD_WITH_BID_WITH_AD_COUNTER_KEYS_2 =
            new AdWithBid(AD_DATA_WITH_DOUBLE_WITH_AD_COUNTER_KEYS_RESULT_2, BID_2);
    private static final List<AdWithBid> AD_WITH_BID_LIST =
            ImmutableList.of(AD_WITH_BID_1, AD_WITH_BID_2);
    private static final List<AdWithBid> AD_WITH_BID_WITH_AD_COUNTER_KEYS_LIST =
            ImmutableList.of(
                    AD_WITH_BID_WITH_AD_COUNTER_KEYS_1, AD_WITH_BID_WITH_AD_COUNTER_KEYS_2);

    private static final Instant NOW = Instant.now();
    private static final CustomAudienceSignals CUSTOM_AUDIENCE_SIGNALS_1 =
            new CustomAudienceSignals(
                    CustomAudienceFixture.VALID_OWNER,
                    CommonFixture.VALID_BUYER_1,
                    "name",
                    NOW,
                    NOW.plus(Duration.ofDays(1)),
                    AdSelectionSignals.EMPTY);
    private static final CustomAudienceSignals CUSTOM_AUDIENCE_SIGNALS_2 =
            new CustomAudienceSignals(
                    CustomAudienceFixture.VALID_OWNER,
                    CommonFixture.VALID_BUYER_1,
                    "name",
                    NOW,
                    NOW.plus(Duration.ofDays(1)),
                    AdSelectionSignals.EMPTY);
    private static final List<CustomAudienceSignals> CUSTOM_AUDIENCE_SIGNALS_LIST =
            ImmutableList.of(CUSTOM_AUDIENCE_SIGNALS_1, CUSTOM_AUDIENCE_SIGNALS_2);

    private static final long AD_SELECTION_ID_1 = 12345L;
    private static final double AD_BID_1 = 10.0;
    private static final long AD_SELECTION_ID_2 = 123456L;
    private static final double AD_BID_2 = 11.0;
    private static final long AD_SELECTION_ID_3 = 1234567L;
    private static final double AD_BID_3 = 12.0;
    private static final Uri AD_RENDER_URI = Uri.parse("test.com/");
    private static final AdSelectionResultBidAndUri AD_SELECTION_ID_WITH_BID_1 =
            AdSelectionResultBidAndUri.builder()
                    .setAdSelectionId(AD_SELECTION_ID_1)
                    .setWinningAdBid(AD_BID_1)
                    .setWinningAdRenderUri(AD_RENDER_URI)
                    .build();
    private static final AdSelectionResultBidAndUri AD_SELECTION_ID_WITH_BID_2 =
            AdSelectionResultBidAndUri.builder()
                    .setAdSelectionId(AD_SELECTION_ID_2)
                    .setWinningAdBid(AD_BID_2)
                    .setWinningAdRenderUri(AD_RENDER_URI)
                    .build();
    private static final AdSelectionResultBidAndUri AD_SELECTION_ID_WITH_BID_3 =
            AdSelectionResultBidAndUri.builder()
                    .setAdSelectionId(AD_SELECTION_ID_3)
                    .setWinningAdBid(AD_BID_3)
                    .setWinningAdRenderUri(AD_RENDER_URI)
                    .build();
    private final ExecutorService mExecutorService = Executors.newFixedThreadPool(1);
    IsolateSettings mIsolateSettings = IsolateSettings.forMaxHeapSizeEnforcementDisabled();
    private AdSelectionScriptEngine mAdSelectionScriptEngine;

    @Mock private AdSelectionExecutionLogger mAdSelectionExecutionLoggerMock;
    @Mock private RunAdBiddingPerCAExecutionLogger mRunAdBiddingPerCAExecutionLoggerMock;
    private RetryStrategy mRetryStrategy;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setUp() {
        Assume.assumeTrue(JSScriptEngine.AvailabilityChecker.isJSSandboxAvailable());
        mRetryStrategy = new NoOpRetryStrategyImpl();
        mAdSelectionScriptEngine =
                new AdSelectionScriptEngine(
                        sContext,
                        () -> mIsolateSettings.getEnforceMaxHeapSizeFeature(),
                        () -> mIsolateSettings.getMaxHeapSizeBytes(),
                        new AdCounterKeyCopierNoOpImpl(),
                        new DebugReportingScriptDisabledStrategy(),
                        false,
                        mRetryStrategy);

        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testAuctionScriptIsInvalidIfRequiredFunctionDoesNotExist() throws Exception {
        assertFalse(
                callJsValidation(
                        "function helloAdvert(ad) { return {'status': 0, 'greeting': 'hello ' +"
                                + " ad.render_uri }; }",
                        ImmutableList.of("helloAdvertWrongName")));
    }

    @Test
    @FlakyTest(bugId = 304766178)
    public void testAuctionScriptIsInvalidIfAnyRequiredFunctionDoesNotExist() throws Exception {
        assertFalse(
                callJsValidation(
                        "function helloAdvert(ad) { return {'status': 0, 'greeting': 'hello ' +"
                                + " ad.render_uri }; }",
                        ImmutableList.of("helloAdvert", "helloAdvertWrongName")));
    }

    @Test
    public void testAuctionScriptIsValidIfAllRequiredFunctionsExist() throws Exception {
        assertTrue(
                callJsValidation(
                        "function helloAdvert(ad) { return {'status': 0, 'greeting': 'hello ' +"
                                + " ad.render_uri }; }",
                        ImmutableList.of("helloAdvert")));
    }

    @Test
    @FlakyTest(bugId = 304766178)
    public void testCanCallScript() throws Exception {
        final AuctionScriptResult result =
                callAuctionEngine(
                        "function helloAdvert(ad) { return {'status': 0, 'greeting': 'hello ' +"
                                + " ad.render_uri }; }",
                        "helloAdvert(ad)",
                        AD_DATA_WITH_DOUBLE_RESULT_1,
                        ImmutableList.of(),
                        AD_DATA_ARGUMENT_UTIL_WITHOUT_COPIER);
        assertThat(result.status).isEqualTo(0);
        assertThat(((JSONObject) result.results.get(0)).getString("greeting"))
                .isEqualTo("hello " + AD_DATA_WITH_DOUBLE_RESULT_1.getRenderUri());
    }

    @Test
    @FlakyTest(bugId = 315521295)
    public void testCanCallScriptRunWithCopier() throws Exception {
        final AuctionScriptResult result =
                callAuctionEngine(
                        "function helloAdvert(ad) { return {'status': 0, 'greeting': 'hello ' +"
                                + " ad.render_uri }; }",
                        "helloAdvert(ad)",
                        AD_DATA_WITH_DOUBLE_RESULT_1,
                        ImmutableList.of(),
                        AD_DATA_ARGUMENT_UTIL_WITH_COPIER);
        assertThat(result.status).isEqualTo(0);
        assertThat(((JSONObject) result.results.get(0)).getString("greeting"))
                .isEqualTo("hello " + AD_DATA_WITH_DOUBLE_RESULT_1.getRenderUri());
    }

    @Test
    @FlakyTest(bugId = 304766178)
    public void testThrowsJSExecutionExceptionIfTheFunctionIsNotFound() throws Exception {
        Exception exception =
                Assert.assertThrows(
                        ExecutionException.class,
                        () ->
                                callAuctionEngine(
                                        "function helloAdvert(ad) { return {'status': 0,"
                                                + " 'greeting': 'hello ' + ad.render_uri }; }",
                                        "helloAdvertWrongName",
                                        AD_DATA_WITH_DOUBLE_RESULT_1,
                                        ImmutableList.of(),
                                        AD_DATA_ARGUMENT_UTIL_WITHOUT_COPIER));

        assertThat(exception.getCause()).isInstanceOf(JSExecutionException.class);
    }

    @Test
    public void testFailsIfScriptIsNotReturningJson() throws Exception {
        final AuctionScriptResult result =
                callAuctionEngine(
                        "function helloAdvert(ad) { return 'hello ' + ad.render_uri; }",
                        "helloAdvert(ad)",
                        AD_DATA_WITH_DOUBLE_RESULT_1,
                        ImmutableList.of(),
                        AD_DATA_ARGUMENT_UTIL_WITHOUT_COPIER);
        assertThat(result.status).isEqualTo(-1);
    }

    @Test
    public void testCallsFailAtFirstNonzeroStatus() throws Exception {
        AdData processedSuccessfully = getAdDataWithResult("123", "0", ImmutableSet.of());
        AdData failToProcess = getAdDataWithResult("456", "1", ImmutableSet.of());
        AdData willNotBeProcessed = getAdDataWithResult("789", "0", ImmutableSet.of());
        final AuctionScriptResult result =
                callAuctionEngine(
                        "function injectFailure(ad) { return {'status': ad.metadata.result,"
                                + " 'value': ad.render_uri }; }",
                        "injectFailure(ad)",
                        ImmutableList.of(processedSuccessfully, failToProcess, willNotBeProcessed),
                        ImmutableList.of(),
                        AD_DATA_ARGUMENT_UTIL_WITHOUT_COPIER);
        assertThat(result.status).isEqualTo(1);
        // Only processed result is returned
        assertThat(result.results.length()).isEqualTo(1);
        assertThat(((JSONObject) result.results.get(0)).getString("value"))
                .isEqualTo(processedSuccessfully.getRenderUri().toString());
    }

    @Test
    @FlakyTest(bugId = 304766178)
    public void testGenerateBidSuccessfulCase() throws Exception {
        doNothing().when(mRunAdBiddingPerCAExecutionLoggerMock).startGenerateBids();
        // Logger calls come after the callback is returned
        CountDownLatch loggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            loggerLatch.countDown();
                            return null;
                        })
                .when(mRunAdBiddingPerCAExecutionLoggerMock)
                .endGenerateBids();
        final List<GenerateBidResult> results =
                generateBids(
                        "function generateBid(ad, auction_signals, per_buyer_signals,"
                                + " trusted_bidding_signals, contextual_signals,"
                                + " custom_audience_signals) { \n"
                                + "  return {'status': 0, 'ad': ad, 'bid': ad.metadata.result };\n"
                                + "}",
                        AD_DATA_WITH_DOUBLE_RESULT_LIST,
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        CUSTOM_AUDIENCE_SIGNALS_1);
        loggerLatch.await();
        verify(mRunAdBiddingPerCAExecutionLoggerMock).startGenerateBids();
        verify(mRunAdBiddingPerCAExecutionLoggerMock).endGenerateBids();
        assertThat(
                        results.stream()
                                .map(GenerateBidResult::getAdWithBid)
                                .collect(Collectors.toList()))
                .containsExactly(
                        new AdWithBid(AD_DATA_WITH_DOUBLE_RESULT_1, BID_1),
                        new AdWithBid(AD_DATA_WITH_DOUBLE_RESULT_2, BID_2));
    }

    @Test
    @FlakyTest(bugId = 315521295)
    public void testGenerateBidWithAdCostSuccessfulCaseCpcBillingEnabled() throws Exception {
        // Reinit engine with cpc billing enabled
        mAdSelectionScriptEngine =
                new AdSelectionScriptEngine(
                        sContext,
                        () -> mIsolateSettings.getEnforceMaxHeapSizeFeature(),
                        () -> mIsolateSettings.getMaxHeapSizeBytes(),
                        new AdCounterKeyCopierNoOpImpl(),
                        new DebugReportingScriptDisabledStrategy(),
                        true,
                        mRetryStrategy);
        doNothing().when(mRunAdBiddingPerCAExecutionLoggerMock).startGenerateBids();
        // Logger calls come after the callback is returned
        CountDownLatch loggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            loggerLatch.countDown();
                            return null;
                        })
                .when(mRunAdBiddingPerCAExecutionLoggerMock)
                .endGenerateBids();
        final List<GenerateBidResult> results =
                generateBids(
                        "function generateBid(ad, auction_signals, per_buyer_signals,"
                                + " trusted_bidding_signals, contextual_signals,"
                                + " custom_audience_signals) { \n"
                                + "  return {'status': 0, 'ad': ad, 'bid': ad.metadata.bid, "
                                + "'adCost':"
                                + " ad.metadata.adCost };\n"
                                + "}",
                        AD_DATA_WITH_DOUBLE_WITH_AD_COST_LIST,
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        CUSTOM_AUDIENCE_SIGNALS_1);
        loggerLatch.await();
        verify(mRunAdBiddingPerCAExecutionLoggerMock).startGenerateBids();
        verify(mRunAdBiddingPerCAExecutionLoggerMock).endGenerateBids();
        assertThat(
                        results.stream()
                                .map(GenerateBidResult::getAdWithBid)
                                .collect(Collectors.toList()))
                .containsExactly(
                        new AdWithBid(AD_DATA_WITH_DOUBLE_AD_COST_1, BID_1),
                        new AdWithBid(AD_DATA_WITH_DOUBLE_AD_COST_2, BID_2));
        assertThat(results.stream().map(GenerateBidResult::getAdCost).collect(Collectors.toList()))
                .containsExactly(AD_COST_1, AD_COST_2);
    }

    @Test
    public void testGenerateBidWithAdCostDoesNotAddAdCostCpcBillingDisabled() throws Exception {
        // Init engine with false
        mAdSelectionScriptEngine =
                new AdSelectionScriptEngine(
                        sContext,
                        () -> mIsolateSettings.getEnforceMaxHeapSizeFeature(),
                        () -> mIsolateSettings.getMaxHeapSizeBytes(),
                        new AdCounterKeyCopierNoOpImpl(),
                        new DebugReportingScriptDisabledStrategy(),
                        false,
                        mRetryStrategy);
        doNothing().when(mRunAdBiddingPerCAExecutionLoggerMock).startGenerateBids();
        // Logger calls come after the callback is returned
        CountDownLatch loggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            loggerLatch.countDown();
                            return null;
                        })
                .when(mRunAdBiddingPerCAExecutionLoggerMock)
                .endGenerateBids();
        final List<GenerateBidResult> results =
                generateBids(
                        "function generateBid(ad, auction_signals, per_buyer_signals,"
                                + " trusted_bidding_signals, contextual_signals,"
                                + " custom_audience_signals) { \n"
                                + "  return {'status': 0, 'ad': ad, 'bid': ad.metadata.bid, "
                                + "'adCost':"
                                + " ad.metadata.adCost };\n"
                                + "}",
                        AD_DATA_WITH_DOUBLE_WITH_AD_COST_LIST,
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        CUSTOM_AUDIENCE_SIGNALS_1);
        loggerLatch.await();
        verify(mRunAdBiddingPerCAExecutionLoggerMock).startGenerateBids();
        verify(mRunAdBiddingPerCAExecutionLoggerMock).endGenerateBids();
        assertThat(
                        results.stream()
                                .map(GenerateBidResult::getAdWithBid)
                                .collect(Collectors.toList()))
                .containsExactly(
                        new AdWithBid(AD_DATA_WITH_DOUBLE_AD_COST_1, BID_1),
                        new AdWithBid(AD_DATA_WITH_DOUBLE_AD_COST_2, BID_2));
        assertThat(results.stream().map(GenerateBidResult::getAdCost).collect(Collectors.toList()))
                .containsExactly(null, null);
    }

    @Test
    public void testGenerateBidWithOnlyOneAdCostSuccessfulCase() throws Exception {
        // Reinit engine with cpc billing enabled
        mAdSelectionScriptEngine =
                new AdSelectionScriptEngine(
                        sContext,
                        () -> mIsolateSettings.getEnforceMaxHeapSizeFeature(),
                        () -> mIsolateSettings.getMaxHeapSizeBytes(),
                        new AdCounterKeyCopierNoOpImpl(),
                        new DebugReportingScriptDisabledStrategy(),
                        true,
                        mRetryStrategy);
        doNothing().when(mRunAdBiddingPerCAExecutionLoggerMock).startGenerateBids();
        // Logger calls come after the callback is returned
        CountDownLatch loggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            loggerLatch.countDown();
                            return null;
                        })
                .when(mRunAdBiddingPerCAExecutionLoggerMock)
                .endGenerateBids();
        final List<GenerateBidResult> results =
                generateBids(
                        "function generateBid(ad, auction_signals, per_buyer_signals,"
                                + " trusted_bidding_signals, contextual_signals,"
                                + " custom_audience_signals) { \n"
                                + "  return {'status': 0, 'ad': ad, 'bid': ad.metadata.bid, "
                                + "'adCost':"
                                + " ad.metadata.adCost };\n"
                                + "}",
                        AD_DATA_WITH_DOUBLE_WITH_EMPTY_AD_COST,
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        CUSTOM_AUDIENCE_SIGNALS_1);
        loggerLatch.await();
        verify(mRunAdBiddingPerCAExecutionLoggerMock).startGenerateBids();
        verify(mRunAdBiddingPerCAExecutionLoggerMock).endGenerateBids();
        for (GenerateBidResult result : results) {
            LoggerFactory.getFledgeLogger().i(result.getAdWithBid().getAdData().toString());
        }
        LoggerFactory.getFledgeLogger()
                .i(new AdWithBid(AD_DATA_WITH_DOUBLE_AD_COST_EMPTY, BID_2).getAdData().toString());
        assertThat(
                        results.stream()
                                .map(GenerateBidResult::getAdWithBid)
                                .collect(Collectors.toList()))
                .containsExactly(
                        new AdWithBid(AD_DATA_WITH_DOUBLE_AD_COST_1, BID_1),
                        new AdWithBid(AD_DATA_WITH_DOUBLE_AD_COST_EMPTY, BID_2));
        assertThat(results.stream().map(GenerateBidResult::getAdCost).collect(Collectors.toList()))
                .containsExactly(AD_COST_1, null);
    }

    @Test
    @FlakyTest(bugId = 304766178)
    public void testGenerateBidWithCopierSuccessfulCase() throws Exception {
        mAdSelectionScriptEngine =
                new AdSelectionScriptEngine(
                        sContext,
                        () -> mIsolateSettings.getEnforceMaxHeapSizeFeature(),
                        () -> mIsolateSettings.getMaxHeapSizeBytes(),
                        new AdCounterKeyCopierImpl(),
                        new DebugReportingScriptDisabledStrategy(),
                        false,
                        mRetryStrategy);

        doNothing().when(mRunAdBiddingPerCAExecutionLoggerMock).startGenerateBids();
        // Logger calls come after the callback is returned
        CountDownLatch loggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            loggerLatch.countDown();
                            return null;
                        })
                .when(mRunAdBiddingPerCAExecutionLoggerMock)
                .endGenerateBids();
        final List<GenerateBidResult> results =
                generateBids(
                        "function generateBid(ad, auction_signals, per_buyer_signals,"
                                + " trusted_bidding_signals, contextual_signals,"
                                + " custom_audience_signals) { \n"
                                + "  return {'status': 0, 'ad': ad, 'bid': ad.metadata.result };\n"
                                + "}",
                        AD_DATA_WITH_DOUBLE_RESULT_LIST,
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        CUSTOM_AUDIENCE_SIGNALS_1);
        loggerLatch.await();
        verify(mRunAdBiddingPerCAExecutionLoggerMock).startGenerateBids();
        verify(mRunAdBiddingPerCAExecutionLoggerMock).endGenerateBids();
        assertThat(
                        results.stream()
                                .map(GenerateBidResult::getAdWithBid)
                                .collect(Collectors.toList()))
                .containsExactly(
                        new AdWithBid(AD_DATA_WITH_DOUBLE_RESULT_1, BID_1),
                        new AdWithBid(AD_DATA_WITH_DOUBLE_RESULT_2, BID_2));
    }

    @Test
    public void testGenerateBidWithCopierWithAdCounterKeysSuccessfulCase() throws Exception {
        mAdSelectionScriptEngine =
                new AdSelectionScriptEngine(
                        sContext,
                        () -> mIsolateSettings.getEnforceMaxHeapSizeFeature(),
                        () -> mIsolateSettings.getMaxHeapSizeBytes(),
                        new AdCounterKeyCopierImpl(),
                        new DebugReportingScriptDisabledStrategy(),
                        false,
                        mRetryStrategy);

        doNothing().when(mRunAdBiddingPerCAExecutionLoggerMock).startGenerateBids();
        // Logger calls come after the callback is returned
        CountDownLatch loggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            loggerLatch.countDown();
                            return null;
                        })
                .when(mRunAdBiddingPerCAExecutionLoggerMock)
                .endGenerateBids();
        final List<GenerateBidResult> results =
                generateBids(
                        "function generateBid(ad, auction_signals, per_buyer_signals,"
                                + " trusted_bidding_signals, contextual_signals,"
                                + " custom_audience_signals) { \n"
                                + "  return {'status': 0, 'ad': ad, 'bid': ad.metadata.result };\n"
                                + "}",
                        AD_DATA_WITH_DOUBLE_WITH_AD_COUNTER_KEYS_RESULT_LIST,
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        CUSTOM_AUDIENCE_SIGNALS_1);
        loggerLatch.await();
        verify(mRunAdBiddingPerCAExecutionLoggerMock).startGenerateBids();
        verify(mRunAdBiddingPerCAExecutionLoggerMock).endGenerateBids();
        assertThat(
                        results.stream()
                                .map(GenerateBidResult::getAdWithBid)
                                .collect(Collectors.toList()))
                .containsExactly(
                        new AdWithBid(AD_DATA_WITH_DOUBLE_WITH_AD_COUNTER_KEYS_RESULT_1, BID_1),
                        new AdWithBid(AD_DATA_WITH_DOUBLE_WITH_AD_COUNTER_KEYS_RESULT_2, BID_2));
    }

    @Test
    public void testGenerateBidV3SuccessfulCase() throws Exception {
        doNothing().when(mRunAdBiddingPerCAExecutionLoggerMock).startGenerateBids();
        // Logger calls come after the callback is returned
        CountDownLatch loggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            loggerLatch.countDown();
                            return null;
                        })
                .when(mRunAdBiddingPerCAExecutionLoggerMock)
                .endGenerateBids();
        final List<GenerateBidResult> results =
                generateBidsV3(
                        "function generateBid(custom_audience, auction_signals,"
                                + " per_buyer_signals,\n"
                                + "    trusted_bidding_signals, contextual_signals) {\n"
                                + "    const ads = custom_audience.ads;\n"
                                + "    let result = null;\n"
                                + "    for (const ad of ads) {\n"
                                + "        if (!result || "
                                + "            ad.metadata.result > result.metadata.result) {\n"
                                + "            result = ad;\n"
                                + "        }\n"
                                + "    }\n"
                                + "    return { 'status': 0, 'ad': result, 'bid':"
                                + " result.metadata.result, 'render': result.render_uri };\n"
                                + "}",
                        DBCustomAudience.fromServiceObject(
                                CustomAudienceFixture.getValidBuilderForBuyer(
                                                CommonFixture.VALID_BUYER_1)
                                        .setAds(AD_DATA_WITH_DOUBLE_RESULT_LIST)
                                        .build(),
                                CustomAudienceFixture.VALID_OWNER,
                                CustomAudienceFixture.VALID_ACTIVATION_TIME,
                                CustomAudienceFixture.CUSTOM_AUDIENCE_DEFAULT_EXPIRE_IN,
                                AD_DATA_CONVERSION_STRATEGY,
                                false,
                                /* auctionServerRequestFlags = */ false),
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY);
        loggerLatch.await();
        verify(mRunAdBiddingPerCAExecutionLoggerMock).startGenerateBids();
        verify(mRunAdBiddingPerCAExecutionLoggerMock).endGenerateBids();
        assertThat(
                        results.stream()
                                .map(GenerateBidResult::getAdWithBid)
                                .collect(Collectors.toList()))
                .containsExactly(new AdWithBid(AD_DATA_WITH_DOUBLE_RESULT_2, BID_2));
    }

    @Test
    @FlakyTest(bugId = 315521295)
    public void testGenerateBidV3WithCopierSuccessfulCase() throws Exception {
        mAdSelectionScriptEngine =
                new AdSelectionScriptEngine(
                        sContext,
                        () -> mIsolateSettings.getEnforceMaxHeapSizeFeature(),
                        () -> mIsolateSettings.getMaxHeapSizeBytes(),
                        new AdCounterKeyCopierImpl(),
                        new DebugReportingScriptDisabledStrategy(),
                        false,
                        mRetryStrategy);

        doNothing().when(mRunAdBiddingPerCAExecutionLoggerMock).startGenerateBids();
        // Logger calls come after the callback is returned
        CountDownLatch loggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            loggerLatch.countDown();
                            return null;
                        })
                .when(mRunAdBiddingPerCAExecutionLoggerMock)
                .endGenerateBids();
        final List<GenerateBidResult> results =
                generateBidsV3(
                        "function generateBid(custom_audience, auction_signals,"
                                + " per_buyer_signals,\n"
                                + "    trusted_bidding_signals, contextual_signals) {\n"
                                + "    const ads = custom_audience.ads;\n"
                                + "    let result = null;\n"
                                + "    for (const ad of ads) {\n"
                                + "        if (!result || "
                                + "            ad.metadata.result > result.metadata.result) {\n"
                                + "            result = ad;\n"
                                + "        }\n"
                                + "    }\n"
                                + "    return { 'status': 0, 'ad': result, 'bid':"
                                + " result.metadata.result, 'render': result.render_uri };\n"
                                + "}",
                        DBCustomAudience.fromServiceObject(
                                CustomAudienceFixture.getValidBuilderForBuyer(
                                                CommonFixture.VALID_BUYER_1)
                                        .setAds(AD_DATA_WITH_DOUBLE_RESULT_LIST)
                                        .build(),
                                CustomAudienceFixture.VALID_OWNER,
                                CustomAudienceFixture.VALID_ACTIVATION_TIME,
                                CustomAudienceFixture.CUSTOM_AUDIENCE_DEFAULT_EXPIRE_IN,
                                AD_DATA_CONVERSION_STRATEGY,
                                false,
                                /* auctionServerRequestFlags = */ false),
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY);
        loggerLatch.await();
        verify(mRunAdBiddingPerCAExecutionLoggerMock).startGenerateBids();
        verify(mRunAdBiddingPerCAExecutionLoggerMock).endGenerateBids();
        assertThat(
                        results.stream()
                                .map(GenerateBidResult::getAdWithBid)
                                .collect(Collectors.toList()))
                .containsExactly(new AdWithBid(AD_DATA_WITH_DOUBLE_RESULT_2, BID_2));
    }

    @Test
    @FlakyTest(bugId = 315521295)
    public void testGenerateBidV3WithCopierWithAdCounterKeysSuccessfulCase() throws Exception {
        mAdSelectionScriptEngine =
                new AdSelectionScriptEngine(
                        sContext,
                        () -> mIsolateSettings.getEnforceMaxHeapSizeFeature(),
                        () -> mIsolateSettings.getMaxHeapSizeBytes(),
                        new AdCounterKeyCopierImpl(),
                        new DebugReportingScriptDisabledStrategy(),
                        false,
                        mRetryStrategy);

        doNothing().when(mRunAdBiddingPerCAExecutionLoggerMock).startGenerateBids();
        // Logger calls come after the callback is returned
        CountDownLatch loggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            loggerLatch.countDown();
                            return null;
                        })
                .when(mRunAdBiddingPerCAExecutionLoggerMock)
                .endGenerateBids();
        DBCustomAudience inputCustomAudience =
                DBCustomAudience.fromServiceObject(
                        CustomAudienceFixture.getValidBuilderForBuyer(CommonFixture.VALID_BUYER_1)
                                .setAds(AD_DATA_WITH_DOUBLE_WITH_AD_COUNTER_KEYS_RESULT_LIST)
                                .build(),
                        CustomAudienceFixture.VALID_OWNER,
                        CustomAudienceFixture.VALID_ACTIVATION_TIME,
                        CustomAudienceFixture.CUSTOM_AUDIENCE_DEFAULT_EXPIRE_IN,
                        AD_DATA_CONVERSION_STRATEGY,
                        false,
                        false);
        final List<GenerateBidResult> results =
                generateBidsV3(
                        "function generateBid(custom_audience, auction_signals,"
                                + " per_buyer_signals,\n"
                                + "    trusted_bidding_signals, contextual_signals) {\n"
                                + "    const ads = custom_audience.ads;\n"
                                + "    let result = null;\n"
                                + "    for (const ad of ads) {\n"
                                + "        if (!result || "
                                + "            ad.metadata.result > result.metadata.result) {\n"
                                + "            result = ad;\n"
                                + "        }\n"
                                + "    }\n"
                                + "    return { 'status': 0, 'ad': result, 'bid':"
                                + " result.metadata.result, 'render': result.render_uri };\n"
                                + "}",
                        inputCustomAudience,
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY);
        loggerLatch.await();
        verify(mRunAdBiddingPerCAExecutionLoggerMock).startGenerateBids();
        verify(mRunAdBiddingPerCAExecutionLoggerMock).endGenerateBids();
        assertThat(
                        results.stream()
                                .map(GenerateBidResult::getAdWithBid)
                                .collect(Collectors.toList()))
                .containsExactly(
                        new AdWithBid(AD_DATA_WITH_DOUBLE_WITH_AD_COUNTER_KEYS_RESULT_2, BID_2));
    }

    @Test
    public void testGetFunctionArgumentCountSuccess() throws Exception {
        String jsScript =
                "function generateBid(ad, auction_signals, per_buyer_signals,"
                        + " trusted_bidding_signals, contextual_signals, user_signals,"
                        + " custom_audience_signals) { \n"
                        + "  return {'status': 0, 'ad': ad, 'bid': ad.metadata.result };\n"
                        + "}";
        String functionName = "generateBid";
        int argCount = getArgCount(jsScript, functionName);
        assertEquals("Argument count mismatch", 7, argCount);
    }

    @Test
    public void testGetFunctionArgumentCountGracefulFallBack() throws Exception {
        String jsScript =
                "function generateBid(ad, auction_signals, per_buyer_signals,"
                        + " trusted_bidding_signals, contextual_signals, user_signals,"
                        + " custom_audience_signals) { \n"
                        + "  return {'status': 0, 'ad': ad, 'bid': ad.metadata.result };\n"
                        + "}";
        String functionName = "functionThatDoesNotExist";
        int argCount = getArgCount(jsScript, functionName);
        assertEquals("Should have gracefully fallen back to -1", -1, argCount);
    }

    @Test
    @FlakyTest(bugId = 304766178)
    public void testGenerateBidBackwardCompatCaseSuccess() throws Exception {
        doNothing().when(mRunAdBiddingPerCAExecutionLoggerMock).startGenerateBids();
        // Logger calls come after the callback is returned
        CountDownLatch loggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            loggerLatch.countDown();
                            return null;
                        })
                .when(mRunAdBiddingPerCAExecutionLoggerMock)
                .endGenerateBids();
        final String previousVersionOfJS =
                "function generateBid(ad, auction_signals, per_buyer_signals,"
                        + " trusted_bidding_signals, contextual_signals, user_signals,"
                        + " custom_audience_signals) {\n"
                        + " custom_audience_signals.name;\n"
                        + " return {'status': 0, 'ad': ad, 'bid': ad.metadata.result };\n"
                        + "}";
        final List<GenerateBidResult> results =
                generateBids(
                        previousVersionOfJS,
                        AD_DATA_WITH_DOUBLE_RESULT_LIST,
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        CUSTOM_AUDIENCE_SIGNALS_1);
        loggerLatch.await();
        verify(mRunAdBiddingPerCAExecutionLoggerMock).startGenerateBids();
        verify(mRunAdBiddingPerCAExecutionLoggerMock).endGenerateBids();
        assertThat(
                        results.stream()
                                .map(GenerateBidResult::getAdWithBid)
                                .collect(Collectors.toList()))
                .containsExactly(
                        new AdWithBid(AD_DATA_WITH_DOUBLE_RESULT_1, BID_1),
                        new AdWithBid(AD_DATA_WITH_DOUBLE_RESULT_2, BID_2));
    }

    @Test
    public void testGenerateBidWithCopierBackwardCompatCaseSuccess() throws Exception {
        mAdSelectionScriptEngine =
                new AdSelectionScriptEngine(
                        sContext,
                        () -> mIsolateSettings.getEnforceMaxHeapSizeFeature(),
                        () -> mIsolateSettings.getMaxHeapSizeBytes(),
                        new AdCounterKeyCopierImpl(),
                        new DebugReportingScriptDisabledStrategy(),
                        false,
                        mRetryStrategy);

        doNothing().when(mRunAdBiddingPerCAExecutionLoggerMock).startGenerateBids();
        // Logger calls come after the callback is returned
        CountDownLatch loggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            loggerLatch.countDown();
                            return null;
                        })
                .when(mRunAdBiddingPerCAExecutionLoggerMock)
                .endGenerateBids();
        final String previousVersionOfJS =
                "function generateBid(ad, auction_signals, per_buyer_signals,"
                        + " trusted_bidding_signals, contextual_signals, user_signals,"
                        + " custom_audience_signals) {\n"
                        + " custom_audience_signals.name;\n"
                        + " return {'status': 0, 'ad': ad, 'bid': ad.metadata.result };\n"
                        + "}";
        final List<GenerateBidResult> results =
                generateBids(
                        previousVersionOfJS,
                        AD_DATA_WITH_DOUBLE_RESULT_LIST,
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        CUSTOM_AUDIENCE_SIGNALS_1);
        loggerLatch.await();
        verify(mRunAdBiddingPerCAExecutionLoggerMock).startGenerateBids();
        verify(mRunAdBiddingPerCAExecutionLoggerMock).endGenerateBids();
        assertThat(
                        results.stream()
                                .map(GenerateBidResult::getAdWithBid)
                                .collect(Collectors.toList()))
                .containsExactly(
                        new AdWithBid(AD_DATA_WITH_DOUBLE_RESULT_1, BID_1),
                        new AdWithBid(AD_DATA_WITH_DOUBLE_RESULT_2, BID_2));
    }

    @Test
    public void testGenerateBidWithCopierWithAdCounterKeysBackwardCompatCaseSuccess()
            throws Exception {
        mAdSelectionScriptEngine =
                new AdSelectionScriptEngine(
                        sContext,
                        () -> mIsolateSettings.getEnforceMaxHeapSizeFeature(),
                        () -> mIsolateSettings.getMaxHeapSizeBytes(),
                        new AdCounterKeyCopierImpl(),
                        new DebugReportingEnabledScriptStrategy(),
                        false,
                        mRetryStrategy);

        doNothing().when(mRunAdBiddingPerCAExecutionLoggerMock).startGenerateBids();
        // Logger calls come after the callback is returned
        CountDownLatch loggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            loggerLatch.countDown();
                            return null;
                        })
                .when(mRunAdBiddingPerCAExecutionLoggerMock)
                .endGenerateBids();
        final String previousVersionOfJS =
                "function generateBid(ad, auction_signals, per_buyer_signals,"
                        + " trusted_bidding_signals, contextual_signals, user_signals,"
                        + " custom_audience_signals) {\n"
                        + " custom_audience_signals.name;\n"
                        + " return {'status': 0, 'ad': ad, 'bid': ad.metadata.result };\n"
                        + "}";
        final List<GenerateBidResult> results =
                generateBids(
                        previousVersionOfJS,
                        AD_DATA_WITH_DOUBLE_WITH_AD_COUNTER_KEYS_RESULT_LIST,
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        CUSTOM_AUDIENCE_SIGNALS_1);
        loggerLatch.await();
        verify(mRunAdBiddingPerCAExecutionLoggerMock).startGenerateBids();
        verify(mRunAdBiddingPerCAExecutionLoggerMock).endGenerateBids();
        assertThat(
                        results.stream()
                                .map(GenerateBidResult::getAdWithBid)
                                .collect(Collectors.toList()))
                .containsExactly(
                        new AdWithBid(AD_DATA_WITH_DOUBLE_WITH_AD_COUNTER_KEYS_RESULT_1, BID_1),
                        new AdWithBid(AD_DATA_WITH_DOUBLE_WITH_AD_COUNTER_KEYS_RESULT_2, BID_2));
    }

    @Test
    @FlakyTest(bugId = 304766178)
    public void testGenerateBidBackwardCompatCaseException() throws Exception {
        final String incompatibleVersionOfJS =
                "function generateBids(ad, auction_signals, per_buyer_signals,"
                        + " trusted_bidding_signals) {\n"
                        + " custom_audience_signals.name;\n"
                        + " return {'status': 0, 'ad': ad, 'bid': ad.metadata.result };\n"
                        + "}";

        Exception exception =
                Assert.assertThrows(
                        ExecutionException.class,
                        () ->
                                generateBids(
                                        incompatibleVersionOfJS,
                                        AD_DATA_WITH_DOUBLE_RESULT_LIST,
                                        AdSelectionSignals.EMPTY,
                                        AdSelectionSignals.EMPTY,
                                        AdSelectionSignals.EMPTY,
                                        AdSelectionSignals.EMPTY,
                                        CUSTOM_AUDIENCE_SIGNALS_1));

        assertThat(exception.getCause()).isInstanceOf(JSExecutionException.class);
        Assert.assertTrue(exception.getCause() instanceof JSExecutionException);
    }

    @Test
    public void testGenerateBidReturnEmptyListInCaseNonSuccessStatus() throws Exception {
        doNothing().when(mRunAdBiddingPerCAExecutionLoggerMock).startGenerateBids();
        // Logger calls come after the callback is returned
        CountDownLatch loggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            loggerLatch.countDown();
                            return null;
                        })
                .when(mRunAdBiddingPerCAExecutionLoggerMock)
                .endGenerateBids();
        final List<GenerateBidResult> results =
                generateBids(
                        "function generateBid(ad, auction_signals, per_buyer_signals,"
                                + " trusted_bidding_signals, contextual_signals,"
                                + " custom_audience_signals) { \n"
                                + "  return {'status': 1, 'ad': ad, 'bid': ad.metadata.result };\n"
                                + "}",
                        AD_DATA_WITH_DOUBLE_RESULT_LIST,
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        CUSTOM_AUDIENCE_SIGNALS_1);
        loggerLatch.await();
        assertThat(results).isEmpty();
        verify(mRunAdBiddingPerCAExecutionLoggerMock).startGenerateBids();
        verify(mRunAdBiddingPerCAExecutionLoggerMock).endGenerateBids();
    }

    @Test
    public void testGenerateBidReturnEmptyListInCaseOfMalformedResponseForAnyAd() throws Exception {
        doNothing().when(mRunAdBiddingPerCAExecutionLoggerMock).startGenerateBids();
        // Logger calls come after the callback is returned
        CountDownLatch loggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            loggerLatch.countDown();
                            return null;
                        })
                .when(mRunAdBiddingPerCAExecutionLoggerMock)
                .endGenerateBids();
        final List<GenerateBidResult> results =
                generateBids(
                        // The response for the second add doesn't include the bid so we cannot
                        // parse and AdWithBid
                        "function generateBid(ad, auction_signals, per_buyer_signals,"
                                + " trusted_bidding_signals, contextual_signals,"
                                + " custom_audience_signals) { \n"
                                + " if (ad.metadata.result > 2) return {'status': 0, 'ad': ad };\n"
                                + " else return {'status': 0, 'ad': ad, 'bid': 10 };\n"
                                + "}",
                        AD_DATA_WITH_DOUBLE_RESULT_LIST,
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        CUSTOM_AUDIENCE_SIGNALS_1);
        loggerLatch.await();
        assertThat(results).isEmpty();
        verify(mRunAdBiddingPerCAExecutionLoggerMock).startGenerateBids();
        verify(mRunAdBiddingPerCAExecutionLoggerMock).endGenerateBids();
    }

    @Test
    public void testGenerateBidV3ReturnDebugReportingNoUrl() throws Exception {
        mAdSelectionScriptEngine =
                new AdSelectionScriptEngine(
                        sContext,
                        () -> mIsolateSettings.getEnforceMaxHeapSizeFeature(),
                        () -> mIsolateSettings.getMaxHeapSizeBytes(),
                        new AdCounterKeyCopierImpl(),
                        new DebugReportingEnabledScriptStrategy(),
                        false,
                        mRetryStrategy);
        doNothing().when(mRunAdBiddingPerCAExecutionLoggerMock).startGenerateBids();
        // Logger calls come after the callback is returned
        CountDownLatch loggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            loggerLatch.countDown();
                            return null;
                        })
                .when(mRunAdBiddingPerCAExecutionLoggerMock)
                .endGenerateBids();
        final List<GenerateBidResult> results =
                generateBidsV3(
                        "function generateBid(custom_audience, auction_signals,"
                                + " per_buyer_signals,\n"
                                + "    trusted_bidding_signals, contextual_signals) {\n"
                                + "    const ads = custom_audience.ads;\n"
                                + "    let result = null;\n"
                                + "; \n"
                                + "    for (const ad of ads) {\n"
                                + "        if (!result || ad.metadata.result > result.metadata"
                                + ".result)"
                                + " {\n"
                                + "            result = ad;\n"
                                + "        }\n"
                                + "    }\n"
                                + "    return { 'status': 0, 'ad': result, 'bid':"
                                + " result.metadata.result, 'render': result.render_uri };\n"
                                + "}",
                        DBCustomAudience.fromServiceObject(
                                CustomAudienceFixture.getValidBuilderForBuyer(
                                                CommonFixture.VALID_BUYER_1)
                                        .setAds(AD_DATA_WITH_DOUBLE_RESULT_LIST)
                                        .build(),
                                CustomAudienceFixture.VALID_OWNER,
                                CustomAudienceFixture.VALID_ACTIVATION_TIME,
                                CustomAudienceFixture.CUSTOM_AUDIENCE_DEFAULT_EXPIRE_IN,
                                AD_DATA_CONVERSION_STRATEGY,
                                false,
                                /* auctionServerRequestFlags = */ false),
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY);
        loggerLatch.await();
        verify(mRunAdBiddingPerCAExecutionLoggerMock).startGenerateBids();
        verify(mRunAdBiddingPerCAExecutionLoggerMock).endGenerateBids();
        assertThat(results.size()).isEqualTo(1);
        assertThat(results.get(0).getWinDebugReportUri()).isEqualTo(Uri.EMPTY);
        assertThat(results.get(0).getLossDebugReportUri()).isEqualTo(Uri.EMPTY);
    }

    @Test
    public void testGenerateBidV3ReturnDebugReportingNoUrl_WhenDisabled() throws Exception {
        mAdSelectionScriptEngine =
                new AdSelectionScriptEngine(
                        sContext,
                        () -> mIsolateSettings.getEnforceMaxHeapSizeFeature(),
                        () -> mIsolateSettings.getMaxHeapSizeBytes(),
                        new AdCounterKeyCopierImpl(),
                        new DebugReportingScriptDisabledStrategy(),
                        false,
                        mRetryStrategy);
        doNothing().when(mRunAdBiddingPerCAExecutionLoggerMock).startGenerateBids();
        // Logger calls come after the callback is returned
        CountDownLatch loggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            loggerLatch.countDown();
                            return null;
                        })
                .when(mRunAdBiddingPerCAExecutionLoggerMock)
                .endGenerateBids();
        final List<GenerateBidResult> results =
                generateBidsV3(
                        "function generateBid(custom_audience, auction_signals,"
                                + " per_buyer_signals,\n"
                                + "    trusted_bidding_signals, contextual_signals) {\n"
                                + "    const ads = custom_audience.ads;\n"
                                + "    let result = null;\n"
                                + "; \n"
                                + "    for (const ad of ads) {\n"
                                + "        if (!result || ad.metadata.result > result.metadata"
                                + ".result)"
                                + " {\n"
                                + "            result = ad;\n"
                                + "        }\n"
                                + "    }\n"
                                + "    forDebuggingOnly.reportAdAuctionWin("
                                + "\"https://example-win.com\");"
                                + "    forDebuggingOnly.reportAdAuctionLoss("
                                + "\"https://example-loss.com\");"
                                + "    return { 'status': 0, 'ad': result, 'bid':"
                                + " result.metadata.result, 'render': result.render_uri };\n"
                                + "}",
                        DBCustomAudience.fromServiceObject(
                                CustomAudienceFixture.getValidBuilderForBuyer(
                                                CommonFixture.VALID_BUYER_1)
                                        .setAds(AD_DATA_WITH_DOUBLE_RESULT_LIST)
                                        .build(),
                                CustomAudienceFixture.VALID_OWNER,
                                CustomAudienceFixture.VALID_ACTIVATION_TIME,
                                CustomAudienceFixture.CUSTOM_AUDIENCE_DEFAULT_EXPIRE_IN,
                                AD_DATA_CONVERSION_STRATEGY,
                                false,
                                /* auctionServerRequestFlags = */ false),
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY);
        loggerLatch.await();
        verify(mRunAdBiddingPerCAExecutionLoggerMock).startGenerateBids();
        verify(mRunAdBiddingPerCAExecutionLoggerMock).endGenerateBids();
        assertThat(results.size()).isEqualTo(1);
        assertThat(results.get(0).getWinDebugReportUri()).isEqualTo(Uri.EMPTY);
        assertThat(results.get(0).getLossDebugReportUri()).isEqualTo(Uri.EMPTY);
    }

    @Test
    public void testGenerateBidV3ReturnDebugReportingNoBadUrl() throws Exception {
        mAdSelectionScriptEngine =
                new AdSelectionScriptEngine(
                        sContext,
                        () -> mIsolateSettings.getEnforceMaxHeapSizeFeature(),
                        () -> mIsolateSettings.getMaxHeapSizeBytes(),
                        new AdCounterKeyCopierImpl(),
                        new DebugReportingEnabledScriptStrategy(),
                        false,
                        mRetryStrategy);
        doNothing().when(mRunAdBiddingPerCAExecutionLoggerMock).startGenerateBids();
        // Logger calls come after the callback is returned
        CountDownLatch loggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            loggerLatch.countDown();
                            return null;
                        })
                .when(mRunAdBiddingPerCAExecutionLoggerMock)
                .endGenerateBids();
        final List<GenerateBidResult> results =
                generateBidsV3(
                        "function generateBid(custom_audience, auction_signals,"
                                + " per_buyer_signals,\n"
                                + "    trusted_bidding_signals, contextual_signals) {\n"
                                + "    const ads = custom_audience.ads;\n"
                                + "    forDebuggingOnly.reportAdAuctionWin(123);"
                                + "    forDebuggingOnly.reportAdAuctionLoss(false);"
                                + "    let result = null;\n"
                                + "; \n"
                                + "    for (const ad of ads) {\n"
                                + "        if (!result || ad.metadata.result > result.metadata"
                                + ".result)"
                                + " {\n"
                                + "            result = ad;\n"
                                + "        }\n"
                                + "    }\n"
                                + "    return { 'status': 0, 'ad': result, 'bid':"
                                + " result.metadata.result, 'render': result.render_uri };\n"
                                + "}",
                        DBCustomAudience.fromServiceObject(
                                CustomAudienceFixture.getValidBuilderForBuyer(
                                                CommonFixture.VALID_BUYER_1)
                                        .setAds(AD_DATA_WITH_DOUBLE_RESULT_LIST)
                                        .build(),
                                CustomAudienceFixture.VALID_OWNER,
                                CustomAudienceFixture.VALID_ACTIVATION_TIME,
                                CustomAudienceFixture.CUSTOM_AUDIENCE_DEFAULT_EXPIRE_IN,
                                AD_DATA_CONVERSION_STRATEGY,
                                false,
                                /* auctionServerRequestFlags = */ false),
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY);
        loggerLatch.await();
        verify(mRunAdBiddingPerCAExecutionLoggerMock).startGenerateBids();
        verify(mRunAdBiddingPerCAExecutionLoggerMock).endGenerateBids();
        assertThat(results.size()).isEqualTo(1);
        assertThat(results.get(0).getWinDebugReportUri()).isEqualTo(Uri.EMPTY);
        assertThat(results.get(0).getLossDebugReportUri()).isEqualTo(Uri.EMPTY);
    }

    @Test
    public void testScoreAdsSuccessfulCase() throws Exception {
        doNothing().when(mAdSelectionExecutionLoggerMock).startScoreAds();
        // Logger calls come after the callback is returned
        CountDownLatch loggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            loggerLatch.countDown();
                            return null;
                        })
                .when(mAdSelectionExecutionLoggerMock)
                .endScoreAds();
        final List<ScoreAdResult> results =
                scoreAds(
                        "function scoreAd(ad, bid, auction_config, seller_signals, "
                                + "trusted_scoring_signals, contextual_signal, user_signal, "
                                + "custom_audience_signal) { \n"
                                + "  return {'status': 0, 'score': bid };\n"
                                + "}",
                        AD_WITH_BID_LIST,
                        anAdSelectionConfig(),
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        CUSTOM_AUDIENCE_SIGNALS_LIST);
        loggerLatch.await();
        assertThat(results.stream().map(ScoreAdResult::getAdScore).collect(Collectors.toList()))
                .containsExactly(BID_1, BID_2);
        verify(mAdSelectionExecutionLoggerMock).startScoreAds();
        verify(mAdSelectionExecutionLoggerMock).endScoreAds();
    }

    @Test
    public void testScoreAdsWithCopierSuccessfulCase() throws Exception {
        mAdSelectionScriptEngine =
                new AdSelectionScriptEngine(
                        sContext,
                        () -> mIsolateSettings.getEnforceMaxHeapSizeFeature(),
                        () -> mIsolateSettings.getMaxHeapSizeBytes(),
                        new AdCounterKeyCopierImpl(),
                        new DebugReportingScriptDisabledStrategy(),
                        false,
                        mRetryStrategy);

        doNothing().when(mAdSelectionExecutionLoggerMock).startScoreAds();
        // Logger calls come after the callback is returned
        CountDownLatch loggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            loggerLatch.countDown();
                            return null;
                        })
                .when(mAdSelectionExecutionLoggerMock)
                .endScoreAds();
        final List<ScoreAdResult> results =
                scoreAds(
                        "function scoreAd(ad, bid, auction_config, seller_signals, "
                                + "trusted_scoring_signals, contextual_signal, user_signal, "
                                + "custom_audience_signal) { \n"
                                + "  return {'status': 0, 'score': bid };\n"
                                + "}",
                        AD_WITH_BID_WITH_AD_COUNTER_KEYS_LIST,
                        anAdSelectionConfig(),
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        CUSTOM_AUDIENCE_SIGNALS_LIST);
        loggerLatch.await();
        assertThat(results.stream().map(ScoreAdResult::getAdScore).collect(Collectors.toList()))
                .containsExactly(BID_1, BID_2);
        verify(mAdSelectionExecutionLoggerMock).startScoreAds();
        verify(mAdSelectionExecutionLoggerMock).endScoreAds();
    }

    @Test
    public void testScoreAdsWithCopierWithAdCounterKeysSuccessfulCase() throws Exception {
        mAdSelectionScriptEngine =
                new AdSelectionScriptEngine(
                        sContext,
                        () -> mIsolateSettings.getEnforceMaxHeapSizeFeature(),
                        () -> mIsolateSettings.getMaxHeapSizeBytes(),
                        new AdCounterKeyCopierImpl(),
                        new DebugReportingEnabledScriptStrategy(),
                        false,
                        mRetryStrategy);

        doNothing().when(mAdSelectionExecutionLoggerMock).startScoreAds();
        // Logger calls come after the callback is returned
        CountDownLatch loggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            loggerLatch.countDown();
                            return null;
                        })
                .when(mAdSelectionExecutionLoggerMock)
                .endScoreAds();
        final List<ScoreAdResult> results =
                scoreAds(
                        "function scoreAd(ad, bid, auction_config, seller_signals, "
                                + "trusted_scoring_signals, contextual_signal, user_signal, "
                                + "custom_audience_signal) { \n"
                                + "  return {'status': 0, 'score': bid };\n"
                                + "}",
                        AD_WITH_BID_LIST,
                        anAdSelectionConfig(),
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        CUSTOM_AUDIENCE_SIGNALS_LIST);
        loggerLatch.await();

        assertThat(results.stream().map(ScoreAdResult::getAdScore).collect(Collectors.toList()))
                .containsExactly(BID_1, BID_2);
        verify(mAdSelectionExecutionLoggerMock).startScoreAds();
        verify(mAdSelectionExecutionLoggerMock).endScoreAds();
    }

    @Test
    public void testScoreAdsReturnEmptyListInCaseOfNonSuccessStatus() throws Exception {
        doNothing().when(mAdSelectionExecutionLoggerMock).startScoreAds();
        // Logger calls come after the callback is returned
        CountDownLatch loggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            loggerLatch.countDown();
                            return null;
                        })
                .when(mAdSelectionExecutionLoggerMock)
                .endScoreAds();
        final List<ScoreAdResult> result =
                scoreAds(
                        "function scoreAd(ad, bid, auction_config, seller_signals, "
                                + "trusted_scoring_signals, contextual_signal, user_signal, "
                                + "custom_audience_signal) { \n"
                                + "  return {'status': 1, 'score': bid };\n"
                                + "}",
                        AD_WITH_BID_LIST,
                        anAdSelectionConfig(),
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        CUSTOM_AUDIENCE_SIGNALS_LIST);
        loggerLatch.await();
        assertThat(result).isEmpty();
        verify(mAdSelectionExecutionLoggerMock).startScoreAds();
        verify(mAdSelectionExecutionLoggerMock).endScoreAds();
    }

    @Test
    public void testScoreAdsReturnsDebugReportingUrl() throws Exception {
        mAdSelectionScriptEngine =
                new AdSelectionScriptEngine(
                        sContext,
                        () -> mIsolateSettings.getEnforceMaxHeapSizeFeature(),
                        () -> mIsolateSettings.getMaxHeapSizeBytes(),
                        new AdCounterKeyCopierNoOpImpl(),
                        new DebugReportingEnabledScriptStrategy(),
                        false,
                        mRetryStrategy);
        doNothing().when(mAdSelectionExecutionLoggerMock).startScoreAds();
        // Logger calls come after the callback is returned
        CountDownLatch loggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            loggerLatch.countDown();
                            return null;
                        })
                .when(mAdSelectionExecutionLoggerMock)
                .endScoreAds();
        final List<ScoreAdResult> results =
                scoreAds(
                        "function scoreAd(ad, bid, auction_config, seller_signals, "
                                + "trusted_scoring_signals, contextual_signal, user_signal, "
                                + "custom_audience_signal) { \n"
                                + "  let url = 'http://example.com/1';"
                                + "  if (bid == 1.1) {\n"
                                + "    url = 'http://example.com/2';\n"
                                + "  }\n"
                                + "  forDebuggingOnly.reportAdAuctionWin(url);\n"
                                + "  return {'status': 0, 'score': bid };\n"
                                + "}",
                        AD_WITH_BID_LIST,
                        anAdSelectionConfig(),
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        CUSTOM_AUDIENCE_SIGNALS_LIST);
        loggerLatch.await();
        assertThat(
                        results.stream()
                                .map(ScoreAdResult::getWinDebugReportUri)
                                .filter(Objects::nonNull)
                                .map(Uri::toString)
                                .collect(Collectors.toList()))
                .containsExactly("http://example.com/1", "http://example.com/2");
        verify(mAdSelectionExecutionLoggerMock).startScoreAds();
        verify(mAdSelectionExecutionLoggerMock).endScoreAds();
    }

    @Test
    public void testScoreAdsReturnsNoDebugReportingUrlWhenDisabled() throws Exception {
        mAdSelectionScriptEngine =
                new AdSelectionScriptEngine(
                        sContext,
                        () -> mIsolateSettings.getEnforceMaxHeapSizeFeature(),
                        () -> mIsolateSettings.getMaxHeapSizeBytes(),
                        new AdCounterKeyCopierNoOpImpl(),
                        new DebugReportingScriptDisabledStrategy(),
                        false,
                        mRetryStrategy);
        doNothing().when(mAdSelectionExecutionLoggerMock).startScoreAds();
        // Logger calls come after the callback is returned
        CountDownLatch loggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            loggerLatch.countDown();
                            return null;
                        })
                .when(mAdSelectionExecutionLoggerMock)
                .endScoreAds();
        final List<ScoreAdResult> results =
                scoreAds(
                        "function scoreAd(ad, bid, auction_config, seller_signals, "
                                + "trusted_scoring_signals, contextual_signal, user_signal, "
                                + "custom_audience_signal) { \n"
                                + "  let url = 'http://example.com/1';"
                                + "  if (bid == 1.1) {\n"
                                + "    url = 'http://example.com/2';\n"
                                + "  }\n"
                                + "  forDebuggingOnly.reportAdAuctionWin(url);\n"
                                + "  return {'status': 0, 'score': bid };\n"
                                + "}",
                        AD_WITH_BID_LIST,
                        anAdSelectionConfig(),
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        CUSTOM_AUDIENCE_SIGNALS_LIST);
        loggerLatch.await();
        assertThat(
                        results.stream()
                                .map(ScoreAdResult::getWinDebugReportUri)
                                .filter(Objects::nonNull)
                                .filter(uri -> uri != Uri.EMPTY)
                                .map(Uri::toString)
                                .collect(Collectors.toList()))
                .isEmpty();
        verify(mAdSelectionExecutionLoggerMock).startScoreAds();
        verify(mAdSelectionExecutionLoggerMock).endScoreAds();
    }

    @Test
    public void testScoreAdsReturnsDebugReportingSellerRejectReason() throws Exception {
        doNothing().when(mAdSelectionExecutionLoggerMock).startScoreAds();
        mAdSelectionScriptEngine =
                new AdSelectionScriptEngine(
                        sContext,
                        () -> mIsolateSettings.getEnforceMaxHeapSizeFeature(),
                        () -> mIsolateSettings.getMaxHeapSizeBytes(),
                        new AdCounterKeyCopierNoOpImpl(),
                        new DebugReportingEnabledScriptStrategy(),
                        false,
                        mRetryStrategy);
        // Logger calls come after the callback is returned
        CountDownLatch loggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            loggerLatch.countDown();
                            return null;
                        })
                .when(mAdSelectionExecutionLoggerMock)
                .endScoreAds();
        final List<ScoreAdResult> results =
                scoreAds(
                        "function scoreAd(ad, bid, auction_config, seller_signals, "
                                + "trusted_scoring_signals, contextual_signal, user_signal, "
                                + "custom_audience_signal) { \n"
                                + "  let url = 'http://example.com/1';"
                                + "  let rejectReason = 'hello';"
                                + "  if (bid == 1.1) {\n"
                                + "    url = 'http://example.com/2';\n"
                                + "    rejectReason = 'world';\n"
                                + "  }\n"
                                + "  return {"
                                + "    'status': 0,"
                                + "    'score': bid,"
                                + "    'rejectReason': rejectReason"
                                + "  };\n"
                                + "}",
                        AD_WITH_BID_LIST,
                        anAdSelectionConfig(),
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        CUSTOM_AUDIENCE_SIGNALS_LIST);
        loggerLatch.await();
        assertThat(
                        results.stream()
                                .map(ScoreAdResult::getSellerRejectReason)
                                .filter(Objects::nonNull)
                                .collect(Collectors.toList()))
                .containsExactly("hello", "world");
        verify(mAdSelectionExecutionLoggerMock).startScoreAds();
        verify(mAdSelectionExecutionLoggerMock).endScoreAds();
    }

    @Test
    public void testScoreAdsReturnsDebugReportingEmptySellerRejectReason() throws Exception {
        doNothing().when(mAdSelectionExecutionLoggerMock).startScoreAds();
        // Logger calls come after the callback is returned
        CountDownLatch loggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            loggerLatch.countDown();
                            return null;
                        })
                .when(mAdSelectionExecutionLoggerMock)
                .endScoreAds();
        final List<ScoreAdResult> results =
                scoreAds(
                        "function scoreAd(ad, bid, auction_config, seller_signals, "
                                + "trusted_scoring_signals, contextual_signal, user_signal, "
                                + "custom_audience_signal) { \n"
                                + "  let url = 'http://example.com/1';"
                                + "  if (bid == 1.1) {\n"
                                + "    url = 'http://example.com/2';\n"
                                + "  }\n"
                                + "  return {'status': 0, 'score': bid };\n"
                                + "}",
                        AD_WITH_BID_LIST,
                        anAdSelectionConfig(),
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        AdSelectionSignals.EMPTY,
                        CUSTOM_AUDIENCE_SIGNALS_LIST);
        loggerLatch.await();
        assertThat(
                        results.stream()
                                .map(ScoreAdResult::getSellerRejectReason)
                                .filter(Objects::nonNull)
                                .collect(Collectors.toList()))
                .containsExactly("", "");
        verify(mAdSelectionExecutionLoggerMock).startScoreAds();
        verify(mAdSelectionExecutionLoggerMock).endScoreAds();
    }

    @Test
    public void testSelectOutcomeWaterfallMediationLogicReturnAdJsSuccess() throws Exception {
        final Long result =
                selectOutcome(
                        "function selectOutcome(outcomes, selection_signals) {\n"
                                + "    if (outcomes.length != 1 || selection_signals.bid_floor =="
                                + " undefined) return null;\n"
                                + "\n"
                                + "    const outcome_1p = outcomes[0];\n"
                                + "    return {'status': 0, 'result': (outcome_1p.bid >"
                                + " selection_signals.bid_floor) ? outcome_1p : null};\n"
                                + "}",
                        Collections.singletonList(AD_SELECTION_ID_WITH_BID_1),
                        AdSelectionSignals.fromString("{bid_floor: 9}"));
        assertThat(result).isEqualTo(AD_SELECTION_ID_WITH_BID_1.getAdSelectionId());
    }

    @Test
    public void testSelectOutcomeWaterfallMediationLogicReturnNullJsSuccess() throws Exception {
        final Long result =
                selectOutcome(
                        "function selectOutcome(outcomes, selection_signals) {\n"
                                + "    if (outcomes.length != 1 || selection_signals.bid_floor =="
                                + " undefined) return null;\n"
                                + "\n"
                                + "    const outcome_1p = outcomes[0];\n"
                                + "    return {'status': 0, 'result': (outcome_1p.bid >"
                                + " selection_signals.bid_floor) ? outcome_1p : null};\n"
                                + "}",
                        Collections.singletonList(AD_SELECTION_ID_WITH_BID_1),
                        AdSelectionSignals.fromString("{bid_floor: 11}"));
        assertThat(result).isNull();
    }

    @Test
    public void testSelectOutcomeOpenBiddingMediationLogicJsSuccess() throws Exception {
        final Long result =
                selectOutcome(
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
                                + "}",
                        List.of(
                                AD_SELECTION_ID_WITH_BID_1,
                                AD_SELECTION_ID_WITH_BID_2,
                                AD_SELECTION_ID_WITH_BID_3),
                        AdSelectionSignals.EMPTY);
        assertThat(result).isEqualTo(AD_SELECTION_ID_WITH_BID_3.getAdSelectionId());
    }

    @Test
    public void testSelectOutcomeReturningMultipleIdsFailure() {
        ExecutionException exception =
                assertThrows(
                        ExecutionException.class,
                        () ->
                                selectOutcome(
                                        "function selectOutcome(outcomes, selection_signals) {\n"
                                                + "    return {'status': 0, 'result': outcomes};\n"
                                                + "}",
                                        List.of(
                                                AD_SELECTION_ID_WITH_BID_1,
                                                AD_SELECTION_ID_WITH_BID_2,
                                                AD_SELECTION_ID_WITH_BID_3),
                                        AdSelectionSignals.EMPTY));
        Assert.assertTrue(exception.getCause() instanceof IllegalStateException);
    }

    @Test
    @FlakyTest(bugId = 304766178)
    public void testCanRunScriptWithStringInterpolationTokenInIt() throws Exception {
        final AuctionScriptResult result =
                callAuctionEngine(
                        "function helloAdvert(ad) { return {'status': 0, 'greeting': '%shello ' +"
                                + " ad.render_uri }; }",
                        "helloAdvert(ad)",
                        AD_DATA_WITH_DOUBLE_RESULT_1,
                        ImmutableList.of(),
                        AD_DATA_ARGUMENT_UTIL_WITHOUT_COPIER);
        assertThat(result.status).isEqualTo(0);
        assertThat(((JSONObject) result.results.get(0)).getString("greeting"))
                .isEqualTo("%shello " + AD_DATA_WITH_DOUBLE_RESULT_1.getRenderUri());
    }

    @Test
    @FlakyTest(bugId = 304766178)
    public void testEncodeSignals()
            throws ExecutionException, InterruptedException, TimeoutException {
        List<String> seeds = List.of("SignalsA", "SignalsB");
        Map<String, List<ProtectedSignal>> rawSignalsMap =
                ProtectedSignalsFixture.generateMapOfProtectedSignals(seeds, 20);

        byte[] expectedResult = new byte[] {0x0A, (byte) 0xB1};
        String encodeSignalsJS =
                "function encodeSignals(signals, maxSize) {\n"
                        + "  return {'status': 0, 'results': new Uint8Array([0x0A, 0xB1])};\n"
                        + "}\n";
        ListenableFuture<byte[]> jsOutcome =
                mAdSelectionScriptEngine.encodeSignals(encodeSignalsJS, rawSignalsMap, 10);
        byte[] result = jsOutcome.get(5, TimeUnit.SECONDS);

        assertArrayEquals(
                "The result expected is the size of keys in the input signals",
                expectedResult,
                result);
    }

    @Test
    public void testEncodeSignalsSignalsAreRepresentedAsAMapInJS()
            throws ExecutionException, InterruptedException, TimeoutException {
        Map<String, List<ProtectedSignal>> rawSignalsMap = new HashMap<>();
        rawSignalsMap.put(
                Base64.getEncoder().encodeToString(new byte[] {0x00}),
                List.of(
                        ProtectedSignalsFixture.generateDBProtectedSignal(
                                "", new byte[] {(byte) 0xA0})));

        rawSignalsMap.put(
                Base64.getEncoder().encodeToString(new byte[] {0x01}),
                List.of(
                        ProtectedSignalsFixture.generateDBProtectedSignal(
                                "", new byte[] {(byte) 0xA1}),
                        ProtectedSignalsFixture.generateDBProtectedSignal(
                                "", new byte[] {(byte) 0xA2})));

        // Assumes keys and values are 1 byte long
        // Generates an array with the following structure
        // [signals.size() signal0.key #signal0.values.size() signal0.values[0] signal0.values[2]
        //                 signal1.key ... ]
        String encodeSignalsJS =
                "function encodeSignals(signals, maxSize) {\n"
                        + "  let result = new Uint8Array(maxSize);\n"
                        + "  // first entry will contain the total size\n"
                        + "  let size = 1;\n"
                        + "  let keys = 0;\n"
                        + "  \n"
                        + "  for (const [key, values] of signals.entries()) {\n"
                        + "    keys++;\n"
                        + "    // Assuming all data are 1 byte only\n"
                        + "    console.log(\"key \" + keys + \" is \" + key)\n"
                        + "    result[size++] = key[0];\n"
                        + "    result[size++] = values.length;\n"
                        + "    for(const value of values) {\n"
                        + "      result[size++] = value.signal_value[0];\n"
                        + "    }\n"
                        + "  }\n"
                        + "  result[0] = keys;\n"
                        + "  \n"
                        + "  return { 'status': 0, 'results': result.subarray(0, size)};\n"
                        + "}\n";
        ListenableFuture<byte[]> jsOutcome =
                mAdSelectionScriptEngine.encodeSignals(encodeSignalsJS, rawSignalsMap, 10);
        byte[] result = jsOutcome.get(5, TimeUnit.SECONDS);

        assertEquals(
                "Encoded result has wrong count of signal keys",
                (byte) rawSignalsMap.size(),
                result[0]);
        int offset = 1;
        for (int i = 0; i < rawSignalsMap.size(); i++) {
            byte signalKey = result[offset++];
            assertTrue(signalKey == 0x00 || signalKey == 0x01);
            if (signalKey == 0x00) {
                assertEquals("Wrong signal values length", 0x01, result[offset++]);
                assertEquals("Wrong signal values", (byte) 0xA0, result[offset++]);
            } else {
                assertEquals("Wrong signal values length", 0x02, result[offset++]);
                assertEquals("Wrong signal values", (byte) 0xA1, result[offset++]);
                assertEquals("Wrong signal values", (byte) 0xA2, result[offset++]);
            }
        }
    }

    @Test
    public void testEncodeSignalsSignalsAreRepresentedAsAMapInJS_timestampIsCorrect()
            throws ExecutionException, InterruptedException, TimeoutException {
        Map<String, List<ProtectedSignal>> rawSignalsMap = new HashMap<>();
        ProtectedSignal signalValue =
                ProtectedSignalsFixture.generateDBProtectedSignal("", new byte[] {(byte) 0xA0});
        rawSignalsMap.put(
                Base64.getEncoder().encodeToString(new byte[] {0x00}), List.of(signalValue));

        String encodeSignalsJS =
                String.format(
                        "function encodeSignals(signals, maxSize) {\n"
                                + "  // returning error if the creation time name of the only "
                                + "  // signal is correct\n"
                                + "  if(signals.size != 1) {\n"
                                + "     return { 'status': 0, 'results': new Uint8Array([1]) };\n"
                                + "  }\n"
                                + "  let signalValues = signals.values().next().value;\n"
                                + "  if(signalValues[0].creation_time == %d) {\n"
                                + "     return { 'status': 0, 'results': new Uint8Array([0]) };\n"
                                + "  }\n"
                                + "  return { 'status': 0, 'results': new Uint8Array([2]) };\n"
                                + "}\n",
                        signalValue.getCreationTime().getEpochSecond());
        ListenableFuture<byte[]> jsOutcome =
                mAdSelectionScriptEngine.encodeSignals(encodeSignalsJS, rawSignalsMap, 10);
        byte[] result = jsOutcome.get(5, TimeUnit.SECONDS);

        assertArrayEquals(
                "Expected a single byte response with value 0 to indicate success "
                        + "in the JS validations",
                new byte[] {0},
                result);
    }

    @Test
    public void testEncodeSignalsSignalsAreRepresentedAsAMapInJS_packageNameIsCorrect()
            throws ExecutionException, InterruptedException, TimeoutException {
        Map<String, List<ProtectedSignal>> rawSignalsMap = new HashMap<>();
        ProtectedSignal signalValue =
                ProtectedSignalsFixture.generateDBProtectedSignal("", new byte[] {(byte) 0xA0});
        rawSignalsMap.put(
                Base64.getEncoder().encodeToString(new byte[] {0x00}), List.of(signalValue));

        String encodeSignalsJS =
                String.format(
                        "function encodeSignals(signals, maxSize) {\n"
                                + "  // returning error if the package name of the only signal is"
                                + "  // correct\n"
                                + "  if(signals.size != 1) {\n"
                                + "     return { 'status': 0, 'results': new Uint8Array([1]) };\n"
                                + "  }\n"
                                + "  let signalValues = signals.values().next().value;\n"
                                + "  if(signalValues[0].package_name == '%s') {\n"
                                + "     return { 'status': 0, 'results': new Uint8Array([0]) };\n"
                                + "  }\n"
                                + "  return { 'status': 0, 'results': new Uint8Array([2]) };\n"
                                + "}\n",
                        signalValue.getPackageName());
        ListenableFuture<byte[]> jsOutcome =
                mAdSelectionScriptEngine.encodeSignals(encodeSignalsJS, rawSignalsMap, 10);
        byte[] result = jsOutcome.get(5, TimeUnit.SECONDS);

        assertArrayEquals(
                "Expected a single byte response with value 0 to indicate success "
                        + "in the JS validations",
                new byte[] {0},
                result);
    }

    @Test
    public void testEncodeEmptySignals()
            throws ExecutionException, InterruptedException, TimeoutException {
        String encodeSignalsJS =
                "function encodeSignals(signals, maxSize) {\n"
                        + "    return {'status' : 0, 'results' : new Uint8Array()};\n"
                        + "}\n";
        ListenableFuture<byte[]> jsOutcome =
                mAdSelectionScriptEngine.encodeSignals(encodeSignalsJS, Collections.EMPTY_MAP, 10);
        byte[] result = jsOutcome.get(5, TimeUnit.SECONDS);

        Assert.assertTrue("The result should have been empty", result.length == 0);
    }

    @Test
    public void testHandleEncodingEmptyOutput() {
        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> {
                            mAdSelectionScriptEngine.handleEncodingOutput("");
                        });
        assertEquals(
                "The encoding script either doesn't contain the required function or the"
                        + " function returned null",
                exception.getMessage());
    }

    @Test
    public void testHandleEncodingOutputFailedStatus() {
        int status = 1;
        String result = "unused";

        String encodingScriptOutput =
                "  {\"status\": " + status + ", \"results\" : \"" + result + "\" }";
        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> {
                            mAdSelectionScriptEngine.handleEncodingOutput(encodingScriptOutput);
                        });
        assertEquals(
                String.format(
                        "Outcome selection script failed with status '%s' or returned unexpected"
                                + " result '%s'",
                        status, result),
                exception.getMessage());
    }

    @Test
    public void testHandleEncodingOutputMissingResult() {
        int status = 1;
        String result = "unused";

        String encodingScriptOutput =
                "  {\"status\": " + status + ", \"bad_result_key\" : \"" + result + "\" }";
        IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> {
                            mAdSelectionScriptEngine.handleEncodingOutput(encodingScriptOutput);
                        });
        assertEquals("Exception processing result from encoding", exception.getMessage());
    }

    private AdSelectionConfig anAdSelectionConfig() {
        return new AdSelectionConfig.Builder()
                .setSeller(AdTechIdentifier.fromString("www.mydomain.com"))
                .setPerBuyerSignals(ImmutableMap.of())
                .setDecisionLogicUri(Uri.parse("https://www.mydomain.com/updateAds"))
                .setSellerSignals(AdSelectionSignals.EMPTY)
                .setCustomAudienceBuyers(
                        ImmutableList.of(AdTechIdentifier.fromString("www.buyer.com")))
                .setAdSelectionSignals(AdSelectionSignals.EMPTY)
                .setTrustedScoringSignalsUri(Uri.parse("https://kvtrusted.com/scoring_signals"))
                .build();
    }

    private AuctionScriptResult callAuctionEngine(
            String jsScript,
            String auctionFunctionName,
            AdData advert,
            List<JSScriptArgument> otherArgs,
            AdDataArgumentUtil adDataArgumentUtil)
            throws Exception {
        return callAuctionEngine(
                jsScript,
                auctionFunctionName,
                ImmutableList.of(advert),
                otherArgs,
                adDataArgumentUtil);
    }

    private List<GenerateBidResult> generateBids(
            String jsScript,
            List<AdData> ads,
            AdSelectionSignals auctionSignals,
            AdSelectionSignals perBuyerSignals,
            AdSelectionSignals trustedBiddingSignals,
            AdSelectionSignals contextualSignals,
            CustomAudienceSignals customAudienceSignals)
            throws Exception {
        return waitForFuture(
                () -> {
                    Log.i(TAG, "Calling generateBids");
                    return mAdSelectionScriptEngine.generateBids(
                            jsScript,
                            ads,
                            auctionSignals,
                            perBuyerSignals,
                            trustedBiddingSignals,
                            contextualSignals,
                            customAudienceSignals,
                            mRunAdBiddingPerCAExecutionLoggerMock);
                });
    }

    private List<GenerateBidResult> generateBidsV3(
            String jsScript,
            DBCustomAudience customAudience,
            AdSelectionSignals auctionSignals,
            AdSelectionSignals perBuyerSignals,
            AdSelectionSignals trustedBiddingSignals,
            AdSelectionSignals contextualSignals)
            throws Exception {
        return waitForFuture(
                () -> {
                    Log.i(TAG, "Calling generateBids");
                    return mAdSelectionScriptEngine.generateBidsV3(
                            jsScript,
                            customAudience,
                            auctionSignals,
                            perBuyerSignals,
                            trustedBiddingSignals,
                            contextualSignals,
                            mRunAdBiddingPerCAExecutionLoggerMock);
                });
    }

    private List<ScoreAdResult> scoreAds(
            String jsScript,
            List<AdWithBid> adsWithBids,
            AdSelectionConfig adSelectionConfig,
            AdSelectionSignals sellerSignals,
            AdSelectionSignals trustedScoringSignals,
            AdSelectionSignals contextualSignals,
            List<CustomAudienceSignals> customAudienceSignals)
            throws Exception {
        return waitForFuture(
                () -> {
                    Log.i(TAG, "Calling scoreAds");
                    return mAdSelectionScriptEngine.scoreAds(
                            jsScript,
                            adsWithBids,
                            adSelectionConfig,
                            sellerSignals,
                            trustedScoringSignals,
                            contextualSignals,
                            customAudienceSignals,
                            mAdSelectionExecutionLoggerMock);
                });
    }

    private Long selectOutcome(
            String jsScript,
            List<AdSelectionResultBidAndUri> adSelectionIdWithBidAndRenderUris,
            AdSelectionSignals selectionSignals)
            throws Exception {
        return waitForFuture(
                () -> {
                    Log.i(TAG, "Calling selectOutcome");
                    return mAdSelectionScriptEngine.selectOutcome(
                            jsScript, adSelectionIdWithBidAndRenderUris, selectionSignals);
                });
    }

    private AuctionScriptResult callAuctionEngine(
            String jsScript,
            String auctionFunctionCall,
            List<AdData> adData,
            List<JSScriptArgument> otherArgs,
            AdDataArgumentUtil adDataArgumentUtil)
            throws Exception {
        ImmutableList.Builder<JSScriptArgument> adDataArgs = new ImmutableList.Builder<>();
        for (AdData ad : adData) {
            adDataArgs.add(adDataArgumentUtil.asScriptArgument("ignored", ad));
        }
        return waitForFuture(
                () -> {
                    Log.i(TAG, "Calling Auction Script Engine");
                    return mAdSelectionScriptEngine.runAuctionScriptIterative(
                            jsScript,
                            adDataArgs.build(),
                            otherArgs,
                            ignoredArgs -> auctionFunctionCall);
                });
    }

    private boolean callJsValidation(String jsScript, List<String> functionNames) throws Exception {
        return waitForFuture(
                () -> {
                    Log.i(TAG, "Calling Auction Script Engine");
                    return mAdSelectionScriptEngine.validateAuctionScript(jsScript, functionNames);
                });
    }

    private int getArgCount(String jsScript, String functionName) throws Exception {
        return waitForFuture(
                () -> {
                    return mAdSelectionScriptEngine.getAuctionScriptArgCount(
                            jsScript, functionName);
                });
    }

    private <T> T waitForFuture(ThrowingSupplier<ListenableFuture<T>> function) throws Exception {
        CountDownLatch resultLatch = new CountDownLatch(1);
        AtomicReference<ListenableFuture<T>> futureResult = new AtomicReference<>();
        futureResult.set(function.get());
        futureResult.get().addListener(resultLatch::countDown, mExecutorService);
        resultLatch.await();
        return futureResult.get().get();
    }

    private static AdData getAdDataWithResult(
            String renderUriSuffix, String resultValue, Set<Integer> adCounterKeys) {
        Objects.requireNonNull(renderUriSuffix, "Suffix must not be null");
        Objects.requireNonNull(resultValue, "Result value must not be null");
        return new AdData.Builder()
                .setRenderUri(Uri.parse(BASE_DOMAIN + renderUriSuffix))
                .setMetadata("{\"result\":" + resultValue + "}")
                .setAdCounterKeys(adCounterKeys)
                .build();
    }

    private static AdData getAdDataWithBidAndCost(
            String renderUriSuffix,
            String bidValue,
            String adCostValue,
            Set<Integer> adCounterKeys) {
        Objects.requireNonNull(renderUriSuffix, "Suffix must not be null");
        Objects.requireNonNull(bidValue, "Bid value must not be null");
        return new AdData.Builder()
                .setRenderUri(Uri.parse(BASE_DOMAIN + renderUriSuffix))
                .setMetadata("{\"bid\":" + bidValue + ",\"adCost\":" + adCostValue + "}")
                .setAdCounterKeys(adCounterKeys)
                .build();
    }

    interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
