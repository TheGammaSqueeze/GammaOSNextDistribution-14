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

import static com.android.adservices.common.CommonFlagsValues.EXTENDED_FLEDGE_AD_SELECTION_SELECTING_OUTCOME_TIMEOUT_MS;
import static com.android.adservices.service.adselection.AdOutcomeSelectorImpl.OUTCOME_SELECTION_TIMED_OUT;
import static com.android.adservices.service.adselection.PrebuiltLogicGenerator.AD_OUTCOME_SELECTION_WATERFALL_MEDIATION_TRUNCATION;
import static com.android.adservices.service.adselection.PrebuiltLogicGenerator.AD_SELECTION_FROM_OUTCOMES_USE_CASE;
import static com.android.adservices.service.adselection.PrebuiltLogicGenerator.AD_SELECTION_PREBUILT_SCHEMA;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.adservices.adselection.AdSelectionFromOutcomesConfig;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
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
import com.android.adservices.data.adselection.datahandlers.AdSelectionResultBidAndUri;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.cache.CacheProviderFactory;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.devapi.AdSelectionDevOverridesHelper;
import com.android.adservices.service.devapi.DevContext;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.UncheckedTimeoutException;
import com.google.mockwebserver.Dispatcher;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;
import com.google.mockwebserver.RecordedRequest;

import org.json.JSONException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;

public class AdOutcomeSelectorImplTest {
    private static final long AD_SELECTION_ID = 12345L;
    private static final double AD_BID = 10.0;
    private static final AdTechIdentifier SELLER = AdTechIdentifier.fromString("test.com");
    private static final Uri AD_RENDER_URI = Uri.parse("test.com");
    private static final String SELECTION_LOGIC_JS_PATH = "/selectionLogicJsPath/";

    @Rule public MockWebServerRule mMockWebServerRule = MockWebServerRuleFactory.createForHttps();

    @Mock private AdSelectionScriptEngine mMockAdSelectionScriptEngine;

    private ListeningExecutorService mLightweightExecutorService;
    private ListeningExecutorService mBackgroundExecutorService;
    private ListeningExecutorService mBlockingExecutorService;
    private ScheduledThreadPoolExecutor mSchedulingExecutor;
    private AdServicesHttpsClient mWebClient;
    private String mSelectionLogicJs;
    private AdSelectionEntryDao mAdSelectionEntryDao;
    private AdOutcomeSelector mAdOutcomeSelector;
    private Flags mFlags;

    private Dispatcher mDefaultDispatcher;
    private MockWebServerRule.RequestMatcher<String> mRequestMatcherExactMatch;

    private DevContext mDevContext = DevContext.createForDevOptionsDisabled();

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        mFlags = new AdOutcomeSelectorImplTestFlags();
        mLightweightExecutorService = AdServicesExecutors.getLightWeightExecutor();
        mBackgroundExecutorService = AdServicesExecutors.getBackgroundExecutor();
        mBlockingExecutorService = AdServicesExecutors.getBlockingExecutor();
        mSchedulingExecutor = AdServicesExecutors.getScheduler();
        mWebClient =
                new AdServicesHttpsClient(
                        AdServicesExecutors.getBlockingExecutor(),
                        CacheProviderFactory.createNoOpCache());

        mSelectionLogicJs =
                "function selectOutcome(outcomes, selection_signals) {\n"
                        + "    return {'status': 0, 'result': outcomes[0]};\n"
                        + "}";

        mDefaultDispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        if (request.getPath().equals(SELECTION_LOGIC_JS_PATH)) {
                            return new MockResponse().setBody(mSelectionLogicJs);
                        }
                        return new MockResponse().setResponseCode(404);
                    }
                };
        mRequestMatcherExactMatch =
                (actualRequest, expectedRequest) -> actualRequest.equals(expectedRequest);
        mAdSelectionEntryDao =
                Room.inMemoryDatabaseBuilder(
                                ApplicationProvider.getApplicationContext(),
                                AdSelectionDatabase.class)
                        .build()
                        .adSelectionEntryDao();

        mAdOutcomeSelector =
                new AdOutcomeSelectorImpl(
                        mMockAdSelectionScriptEngine,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mSchedulingExecutor,
                        mWebClient,
                        new AdSelectionDevOverridesHelper(
                                DevContext.createForDevOptionsDisabled(), mAdSelectionEntryDao),
                        mFlags,
                        mDevContext);
    }

    @Test
    public void testAdOutcomeSelectorReturnsOutcomeSuccess() throws Exception {
        MockWebServer server = mMockWebServerRule.startMockWebServer(mDefaultDispatcher);

        List<AdSelectionResultBidAndUri> adverts =
                Collections.singletonList(
                        AdSelectionResultBidAndUri.builder()
                                .setAdSelectionId(AD_SELECTION_ID)
                                .setWinningAdBid(AD_BID)
                                .setWinningAdRenderUri(AD_RENDER_URI)
                                .build());
        AdSelectionSignals signals = AdSelectionSignals.EMPTY;
        Uri uri = mMockWebServerRule.uriForPath(SELECTION_LOGIC_JS_PATH);
        AdSelectionFromOutcomesConfig config =
                new AdSelectionFromOutcomesConfig.Builder()
                        .setSeller(SELLER)
                        .setAdSelectionIds(Collections.singletonList(AD_SELECTION_ID))
                        .setSelectionSignals(signals)
                        .setSelectionLogicUri(uri)
                        .build();

        Mockito.when(
                        mMockAdSelectionScriptEngine.selectOutcome(
                                mSelectionLogicJs, adverts, signals))
                .thenReturn(Futures.immediateFuture(AD_SELECTION_ID));

        Long selectedOutcomeId =
                waitForFuture(() -> mAdOutcomeSelector.runAdOutcomeSelector(adverts, config));

        mMockWebServerRule.verifyMockServerRequests(
                server,
                1,
                Collections.singletonList(SELECTION_LOGIC_JS_PATH),
                mRequestMatcherExactMatch);
        assertEquals(AD_SELECTION_ID, (long) selectedOutcomeId);
    }

    @Test
    public void testAdOutcomeSelectorReturnsNullSuccess() throws Exception {
        MockWebServer server = mMockWebServerRule.startMockWebServer(mDefaultDispatcher);

        List<AdSelectionResultBidAndUri> adverts =
                Collections.singletonList(
                        AdSelectionResultBidAndUri.builder()
                                .setAdSelectionId(AD_SELECTION_ID)
                                .setWinningAdBid(AD_BID)
                                .setWinningAdRenderUri(AD_RENDER_URI)
                                .build());
        AdSelectionSignals signals = AdSelectionSignals.EMPTY;
        Uri uri = mMockWebServerRule.uriForPath(SELECTION_LOGIC_JS_PATH);
        AdSelectionFromOutcomesConfig config =
                new AdSelectionFromOutcomesConfig.Builder()
                        .setSeller(SELLER)
                        .setAdSelectionIds(Collections.singletonList(AD_SELECTION_ID))
                        .setSelectionSignals(signals)
                        .setSelectionLogicUri(uri)
                        .build();

        Mockito.when(
                        mMockAdSelectionScriptEngine.selectOutcome(
                                mSelectionLogicJs, adverts, signals))
                .thenReturn(Futures.immediateFuture(null));

        Long selectedOutcomeId =
                waitForFuture(() -> mAdOutcomeSelector.runAdOutcomeSelector(adverts, config));

        mMockWebServerRule.verifyMockServerRequests(
                server,
                1,
                Collections.singletonList(SELECTION_LOGIC_JS_PATH),
                mRequestMatcherExactMatch);
        assertNull(selectedOutcomeId);
    }

    @Test
    public void testAdOutcomeSelectorJsonException() throws Exception {
        MockWebServer server = mMockWebServerRule.startMockWebServer(mDefaultDispatcher);

        List<AdSelectionResultBidAndUri> adverts =
                Collections.singletonList(
                        AdSelectionResultBidAndUri.builder()
                                .setAdSelectionId(AD_SELECTION_ID)
                                .setWinningAdBid(AD_BID)
                                .setWinningAdRenderUri(AD_RENDER_URI)
                                .build());
        AdSelectionSignals signals = AdSelectionSignals.EMPTY;
        Uri uri = mMockWebServerRule.uriForPath(SELECTION_LOGIC_JS_PATH);
        AdSelectionFromOutcomesConfig config =
                new AdSelectionFromOutcomesConfig.Builder()
                        .setSeller(SELLER)
                        .setAdSelectionIds(Collections.singletonList(AD_SELECTION_ID))
                        .setSelectionSignals(signals)
                        .setSelectionLogicUri(uri)
                        .build();

        String jsonExceptionMessage = "Badly formatted JSON";
        Mockito.when(
                        mMockAdSelectionScriptEngine.selectOutcome(
                                mSelectionLogicJs, adverts, signals))
                .thenThrow(new JSONException(jsonExceptionMessage));

        ExecutionException exception =
                Assert.assertThrows(
                        ExecutionException.class,
                        () ->
                                waitForFuture(
                                        () ->
                                                mAdOutcomeSelector.runAdOutcomeSelector(
                                                        adverts, config)));
        Assert.assertTrue(exception.getCause() instanceof JSONException);
        Assert.assertEquals(exception.getCause().getMessage(), jsonExceptionMessage);
        mMockWebServerRule.verifyMockServerRequests(
                server,
                1,
                Collections.singletonList(SELECTION_LOGIC_JS_PATH),
                mRequestMatcherExactMatch);
    }

    @Test
    public void testAdOutcomeSelectorTimeoutFailure() throws Exception {
        MockWebServer server = mMockWebServerRule.startMockWebServer(mDefaultDispatcher);
        Flags flagsWithSmallerLimits =
                new Flags() {
                    @Override
                    public long getAdSelectionSelectingOutcomeTimeoutMs() {
                        return 300;
                    }
                };

        AdOutcomeSelector adOutcomeSelector =
                new AdOutcomeSelectorImpl(
                        mMockAdSelectionScriptEngine,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mSchedulingExecutor,
                        mWebClient,
                        new AdSelectionDevOverridesHelper(
                                DevContext.createForDevOptionsDisabled(), mAdSelectionEntryDao),
                        flagsWithSmallerLimits,
                        mDevContext);

        List<AdSelectionResultBidAndUri> adverts =
                Collections.singletonList(
                        AdSelectionResultBidAndUri.builder()
                                .setAdSelectionId(AD_SELECTION_ID)
                                .setWinningAdBid(AD_BID)
                                .setWinningAdRenderUri(AD_RENDER_URI)
                                .build());
        AdSelectionSignals signals = AdSelectionSignals.EMPTY;
        Uri uri = mMockWebServerRule.uriForPath(SELECTION_LOGIC_JS_PATH);
        AdSelectionFromOutcomesConfig config =
                new AdSelectionFromOutcomesConfig.Builder()
                        .setSeller(SELLER)
                        .setAdSelectionIds(Collections.singletonList(AD_SELECTION_ID))
                        .setSelectionSignals(signals)
                        .setSelectionLogicUri(uri)
                        .build();

        Mockito.when(
                        mMockAdSelectionScriptEngine.selectOutcome(
                                mSelectionLogicJs, adverts, signals))
                .thenAnswer(
                        (invocation) ->
                                getOutcomeWithDelay(AD_SELECTION_ID, flagsWithSmallerLimits));

        ExecutionException exception =
                Assert.assertThrows(
                        ExecutionException.class,
                        () ->
                                waitForFuture(
                                        () ->
                                                adOutcomeSelector.runAdOutcomeSelector(
                                                        adverts, config)));
        Assert.assertTrue(exception.getCause() instanceof UncheckedTimeoutException);
        Assert.assertEquals(exception.getCause().getMessage(), OUTCOME_SELECTION_TIMED_OUT);
        mMockWebServerRule.verifyMockServerRequests(
                server,
                1, // Gets one call that causes the timeout
                Collections.singletonList(SELECTION_LOGIC_JS_PATH),
                mRequestMatcherExactMatch);
    }

    @Test
    public void testAdOutcomeSelectorWithPrebuiltUriReturnsOutcomeSuccess() throws Exception {
        MockWebServer server = mMockWebServerRule.startMockWebServer(mDefaultDispatcher);

        Flags prebuiltFlagEnabled =
                new Flags() {
                    @Override
                    public boolean getFledgeAdSelectionPrebuiltUriEnabled() {
                        return true;
                    }
                };

        AdOutcomeSelector adOutcomeSelector =
                new AdOutcomeSelectorImpl(
                        mMockAdSelectionScriptEngine,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mSchedulingExecutor,
                        mWebClient,
                        new AdSelectionDevOverridesHelper(
                                DevContext.createForDevOptionsDisabled(), mAdSelectionEntryDao),
                        prebuiltFlagEnabled,
                        mDevContext);

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

        AdSelectionSignals selectionSignals =
                AdSelectionSignals.fromString(String.format("{%s: %s}", paramValue, AD_BID + 1));

        List<AdSelectionResultBidAndUri> adverts =
                Collections.singletonList(
                        AdSelectionResultBidAndUri.builder()
                                .setAdSelectionId(AD_SELECTION_ID)
                                .setWinningAdBid(AD_BID)
                                .setWinningAdRenderUri(AD_RENDER_URI)
                                .build());

        AdSelectionFromOutcomesConfig config =
                new AdSelectionFromOutcomesConfig.Builder()
                        .setSeller(SELLER)
                        .setAdSelectionIds(Collections.singletonList(AD_SELECTION_ID))
                        .setSelectionSignals(selectionSignals)
                        .setSelectionLogicUri(prebuiltUri)
                        .build();

        Mockito.when(
                        mMockAdSelectionScriptEngine.selectOutcome(
                                Mockito.anyString(),
                                Mockito.eq(adverts),
                                Mockito.eq(selectionSignals)))
                .thenReturn(Futures.immediateFuture(AD_SELECTION_ID));

        Long selectedOutcomeId =
                waitForFuture(() -> adOutcomeSelector.runAdOutcomeSelector(adverts, config));

        mMockWebServerRule.verifyMockServerRequests(
                server,
                0, // Shouldn't get any requests
                Collections.emptyList(),
                (actualRequest, expectedRequest) -> true); // Count any request
        assertEquals(AD_SELECTION_ID, (long) selectedOutcomeId);
    }

    private ListenableFuture<Long> getOutcomeWithDelay(Long outcomeId, @NonNull Flags flags) {
        return mBlockingExecutorService.submit(
                () -> {
                    Thread.sleep(2 * flags.getAdSelectionSelectingOutcomeTimeoutMs());
                    return outcomeId;
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

    private static class AdOutcomeSelectorImplTestFlags implements Flags {
        @Override
        public long getAdSelectionSelectingOutcomeTimeoutMs() {
            return EXTENDED_FLEDGE_AD_SELECTION_SELECTING_OUTCOME_TIMEOUT_MS;
        }
    }
}
