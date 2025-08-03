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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionConfigFixture;
import android.adservices.common.AdData;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.FlakyTest;
import androidx.test.filters.SmallTest;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.common.SupportedByConditionRule;
import com.android.adservices.common.WebViewSupportUtil;
import com.android.adservices.data.adselection.CustomAudienceSignals;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.adselection.ReportImpressionScriptEngine.BuyerReportingResult;
import com.android.adservices.service.adselection.ReportImpressionScriptEngine.ReportingScriptResult;
import com.android.adservices.service.adselection.ReportImpressionScriptEngine.SellerReportingResult;
import com.android.adservices.service.common.NoOpRetryStrategyImpl;
import com.android.adservices.service.exception.JSExecutionException;
import com.android.adservices.service.js.IsolateSettings;
import com.android.adservices.service.js.JSScriptArgument;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

@SmallTest
public class ReportImpressionScriptEngineTest {
    protected static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final String TAG = "ReportImpressionScriptEngineTest";
    private final ExecutorService mExecutorService = Executors.newFixedThreadPool(1);
    IsolateSettings mIsolateSettings = IsolateSettings.forMaxHeapSizeEnforcementDisabled();
    private static final Flags TEST_FLAGS = FlagsFactory.getFlagsForTest();
    private static final Flags FLAGS_WITH_SMALLER_MAX_ARRAY_SIZE =
            new Flags() {
                @Override
                public long getFledgeReportImpressionMaxRegisteredAdBeaconsPerAdTechCount() {
                    return 2;
                }
            };

    private final long mFledgeReportImpressionMaxRegisteredAdBeaconsPerAdTechCount =
            TEST_FLAGS.getFledgeReportImpressionMaxRegisteredAdBeaconsPerAdTechCount();

    private ReportImpressionScriptEngine mReportImpressionScriptEngine;

    private static final AdTechIdentifier BUYER_1 = AdSelectionConfigFixture.BUYER_1;

    private static final String RESULT_FIELD = "result";
    private static final String TEST_DOMAIN = "https://www.domain.com/adverts/123";
    private static final Uri TEST_DOMAIN_URI = Uri.parse(TEST_DOMAIN);
    private static final AdData AD_DATA =
            new AdData.Builder().setRenderUri(TEST_DOMAIN_URI).setMetadata("{}").build();

    private final AdSelectionSignals mContextualSignals =
            AdSelectionSignals.fromString("{\"test_contextual_signals\":1}");

    private final AdSelectionSignals mSignalsForBuyer =
            AdSelectionSignals.fromString("{\"test_signals_for_buyer\":1}");

    private final CustomAudienceSignals mCustomAudienceSignals =
            new CustomAudienceSignals.Builder()
                    .setOwner("test_owner")
                    .setBuyer(AdTechIdentifier.fromString("test_buyer"))
                    .setName("test_name")
                    .setActivationTime(Instant.now())
                    .setExpirationTime(Instant.now())
                    .setUserBiddingSignals(
                            AdSelectionSignals.fromString("{\"user_bidding_signals\":1}"))
                    .build();

    private static final Uri REPORTING_URI = Uri.parse("https://domain.com/reporting");
    private static final String SELLER_KEY = "{\"seller\":\"";

    private static final Uri CLICK_URI = Uri.parse("https://domain.com/click");
    private static final Uri HOVER_URI = Uri.parse("https://domain.com/hover");

    private static final InteractionUriRegistrationInfo CLICK_EVENT_URI_REGISTRATION_INFO =
            InteractionUriRegistrationInfo.builder()
                    .setInteractionKey("click")
                    .setInteractionReportingUri(CLICK_URI)
                    .build();
    private static final InteractionUriRegistrationInfo HOVER_EVENT_URI_REGISTRATION_INFO =
            InteractionUriRegistrationInfo.builder()
                    .setInteractionKey("hover")
                    .setInteractionReportingUri(HOVER_URI)
                    .build();

    // Only used for setup, so no need to use the real impl for now
    private static final AdDataArgumentUtil AD_DATA_ARGUMENT_UTIL =
            new AdDataArgumentUtil(new AdCounterKeyCopierNoOpImpl());

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    // Every test in this class requires that the JS Sandbox be available. The JS Sandbox
    // availability depends on an external component (the system webview) being higher than a
    // certain minimum version.
    @Rule(order = 1)
    public final SupportedByConditionRule webViewSupportsJSSandbox =
            WebViewSupportUtil.createJSSandboxAvailableRule(sContext);

    @Before
    public void setUp() {
        mReportImpressionScriptEngine =
                initEngine(true, mFledgeReportImpressionMaxRegisteredAdBeaconsPerAdTechCount);
    }

    @Test
    @FlakyTest(bugId = 315521295)
    public void testCanCallScript() throws Exception {
        ImmutableList.Builder<JSScriptArgument> args = new ImmutableList.Builder<>();
        args.add(AD_DATA_ARGUMENT_UTIL.asScriptArgument("ignored", AD_DATA));
        final ReportingScriptResult result =
                callReportingEngine(
                        "function helloAdvert(ad) { return {'status': 0, 'results': {'result':"
                                + " 'hello ' + ad.render_uri }}; }",
                        "helloAdvert",
                        args.build());
        assertThat(result.status).isEqualTo(0);
        assertThat((result.results.getString(RESULT_FIELD))).isEqualTo("hello " + TEST_DOMAIN);
    }

    @Test
    @FlakyTest(bugId = 315521295)
    public void testThrowsJSExecutionExceptionIfFunctionNotFound() throws Exception {
        ImmutableList.Builder<JSScriptArgument> args = new ImmutableList.Builder<>();
        args.add(AD_DATA_ARGUMENT_UTIL.asScriptArgument("ignored", AD_DATA));

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            callReportingEngine(
                                    "function helloAdvert(ad) { return {'status': 0, 'results':"
                                            + " {'result': 'hello ' + ad.render_uri }}; }",
                                    "helloAdvertWrongName",
                                    args.build());
                        });
        assertThat(exception.getCause()).isInstanceOf(JSExecutionException.class);
    }

    @Test
    @FlakyTest(bugId = 315521295)
    public void testThrowsIllegalStateExceptionIfScriptIsNotReturningJson() throws Exception {
        ImmutableList.Builder<JSScriptArgument> args = new ImmutableList.Builder<>();
        args.add(AD_DATA_ARGUMENT_UTIL.asScriptArgument("ignored", AD_DATA));

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            callReportingEngine(
                                    "function helloAdvert(ad) { return 'hello ' + ad.render_uri; }",
                                    "helloAdvert",
                                    args.build());
                        });
        assertThat(exception.getCause()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void testReportResultSuccessfulCaseRegisterAdBeaconEnabled() throws Exception {
        String jsScript =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) {"
                        + " \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"seller\":\"' + ad_selection_config.seller + '\"}', "
                        + "'reporting_uri': 'https://domain.com/reporting' } };\n"
                        + "}";
        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();
        double bid = 5;

        final SellerReportingResult result =
                reportResult(jsScript, adSelectionConfig, TEST_DOMAIN_URI, bid, mContextualSignals);

        assertThat(
                        AdSelectionSignals.fromString(
                                SELLER_KEY + adSelectionConfig.getSeller() + "\"}"))
                .isEqualTo(result.getSignalsForBuyer());

        assertEquals(REPORTING_URI, result.getReportingUri());
    }

    @Test
    public void testReportResultSuccessfulCaseRegisterAdBeaconDisabled() throws Exception {
        // Re init engine
        mReportImpressionScriptEngine =
                initEngine(false, mFledgeReportImpressionMaxRegisteredAdBeaconsPerAdTechCount);

        String jsScript =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) {"
                        + " \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"seller\":\"' + ad_selection_config.seller + '\"}', "
                        + "'reporting_uri': 'https://domain.com/reporting' } };\n"
                        + "}";
        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();
        double bid = 5;

        final SellerReportingResult result =
                reportResult(jsScript, adSelectionConfig, TEST_DOMAIN_URI, bid, mContextualSignals);

        assertThat(
                        AdSelectionSignals.fromString(
                                SELLER_KEY + adSelectionConfig.getSeller() + "\"}"))
                .isEqualTo(result.getSignalsForBuyer());

        assertEquals(REPORTING_URI, result.getReportingUri());
    }

    @Test
    @FlakyTest(bugId = 315521295)
    public void testReportResultSuccessfulCaseWithMoreResultsFieldsThanExpected() throws Exception {
        String jsScript =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) {"
                    + " \n"
                    + " return {'status': 0, 'results': {'signals_for_buyer': '{\"seller\":\"' +"
                    + " ad_selection_config.seller + '\"}', 'reporting_uri':"
                    + " 'https://domain.com/reporting', 'extra_key':'extra_value' } };\n"
                    + "}";
        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();
        double bid = 5;

        final SellerReportingResult result =
                reportResult(jsScript, adSelectionConfig, TEST_DOMAIN_URI, bid, mContextualSignals);

        assertThat(
                        AdSelectionSignals.fromString(
                                SELLER_KEY + adSelectionConfig.getSeller() + "\"}"))
                .isEqualTo(result.getSignalsForBuyer());

        assertEquals(REPORTING_URI, result.getReportingUri());
    }

    @Test
    @FlakyTest(bugId = 317817375)
    public void testReportResultSuccessfulCaseWithCallingRegisterAdBeacon() throws Exception {
        String jsScript =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) "
                        + "{\n"
                        + "const beacons = {'click': 'https://domain.com/click', 'hover': "
                        + "'https://domain.com/hover'};\n"
                        + "registerAdBeacon(beacons);"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"seller\":\"' + ad_selection_config.seller + '\"}', "
                        + "'reporting_uri': 'https://domain.com/reporting' } };\n"
                        + "}";
        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();
        double bid = 5;

        final SellerReportingResult result =
                reportResult(jsScript, adSelectionConfig, TEST_DOMAIN_URI, bid, mContextualSignals);

        assertEquals(REPORTING_URI, result.getReportingUri());

        assertThat(
                        AdSelectionSignals.fromString(
                                SELLER_KEY + adSelectionConfig.getSeller() + "\"}"))
                .isEqualTo(result.getSignalsForBuyer());

        assertEquals(2, result.getInteractionReportingUris().size());

        assertThat(
                        ImmutableList.of(
                                CLICK_EVENT_URI_REGISTRATION_INFO,
                                HOVER_EVENT_URI_REGISTRATION_INFO))
                .containsExactlyElementsIn(result.getInteractionReportingUris());
    }

    @Test
    public void testReportResultFailsWhenCallingRegisterAdBeaconWhenFlagDisabled()
            throws Exception {
        // Re init engine
        mReportImpressionScriptEngine =
                initEngine(false, mFledgeReportImpressionMaxRegisteredAdBeaconsPerAdTechCount);

        String jsScript =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) "
                        + "{\n"
                        + "const beacons = {'click': 'https://domain.com/click', 'hover': "
                        + "'https://domain.com/hover'};\n"
                        + "registerAdBeacon(beacons);"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"seller\":\"' + ad_selection_config.seller + '\"}', "
                        + "'reporting_uri': 'https://domain.com/reporting' } };\n"
                        + "}";
        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();
        double bid = 5;

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            reportResult(
                                    jsScript,
                                    adSelectionConfig,
                                    TEST_DOMAIN_URI,
                                    bid,
                                    mContextualSignals);
                        });

        assertThat(exception.getCause()).isInstanceOf(JSExecutionException.class);
    }

    @Test
    @FlakyTest(bugId = 315521295)
    public void testReportResultFailsInvalidInteractionKeyType() throws Exception {
        String jsScript =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals)"
                        + " {\n"
                        + "const beacons = {{'hi'}: 'https://domain.com/click'};\n"
                        + "registerAdBeacon(beacons); return {'status': 0, 'results':"
                        + " {'signals_for_buyer': '{\"seller\":\"' + ad_selection_config.seller +"
                        + " '\"}', 'reporting_uri': 'https://domain.com/reporting' } };\n"
                        + "}";
        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();
        double bid = 5;

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            reportResult(
                                    jsScript,
                                    adSelectionConfig,
                                    TEST_DOMAIN_URI,
                                    bid,
                                    mContextualSignals);
                        });

        assertThat(exception.getCause()).isInstanceOf(JSExecutionException.class);
    }

    @Test
    @FlakyTest(bugId = 315521295)
    public void testReportResultFailsInvalidInteractionReportingUriType() throws Exception {
        String jsScript =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals)"
                        + " {\n"
                        + "const beacons = {'click': 1};\n"
                        + "registerAdBeacon(beacons); return {'status': 0, 'results':"
                        + " {'signals_for_buyer': '{\"seller\":\"' + ad_selection_config.seller +"
                        + " '\"}', 'reporting_uri': 'https://domain.com/reporting' } };\n"
                        + "}";
        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();
        double bid = 5;

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            reportResult(
                                    jsScript,
                                    adSelectionConfig,
                                    TEST_DOMAIN_URI,
                                    bid,
                                    mContextualSignals);
                        });

        assertThat(exception.getCause()).isInstanceOf(JSExecutionException.class);
    }

    @Test
    public void testReportResultFailsWhenRegisterAdBeaconCalledMoreThanOnce() throws Exception {
        String jsScript =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals)"
                    + " {\n"
                    + "const beacons_1 = {'click': ''https://domain.com/click''};\n"
                    + "const beacons_2 = {'hover': ''https://domain.com/hober''};\n"
                    + "registerAdBeacon(beacons_1);registerAdBeacon(beacons_2); return {'status':"
                    + " 0, 'results': {'signals_for_buyer': '{\"seller\":\"' +"
                    + " ad_selection_config.seller + '\"}', 'reporting_uri':"
                    + " 'https://domain.com/reporting' } };\n"
                    + "}";
        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();
        double bid = 5;

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            reportResult(
                                    jsScript,
                                    adSelectionConfig,
                                    TEST_DOMAIN_URI,
                                    bid,
                                    mContextualSignals);
                        });

        assertThat(exception.getCause()).isInstanceOf(JSExecutionException.class);
    }

    @Test
    @FlakyTest(bugId = 315521295)
    public void testReportResultFailsWhenRegisterAdBeaconInputNotAnObject__Null() throws Exception {
        String jsScript =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals)"
                        + " {\n"
                        + "registerAdBeacon(null); return {'status': 0, 'results':"
                        + " {'signals_for_buyer': '{\"seller\":\"' + ad_selection_config.seller +"
                        + " '\"}', 'reporting_uri': 'https://domain.com/reporting' } };\n"
                        + "}";
        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();
        double bid = 5;

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            reportResult(
                                    jsScript,
                                    adSelectionConfig,
                                    TEST_DOMAIN_URI,
                                    bid,
                                    mContextualSignals);
                        });

        assertThat(exception.getCause()).isInstanceOf(JSExecutionException.class);
    }

    @Test
    @FlakyTest(bugId = 315521295)
    public void testReportResultFailsWhenRegisterAdBeaconInputNotAnObject__Int() throws Exception {
        String jsScript =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals)"
                    + " {\n"
                    + "registerAdBeacon(1); return {'status': 0, 'results': {'signals_for_buyer':"
                    + " '{\"seller\":\"' + ad_selection_config.seller + '\"}', 'reporting_uri':"
                    + " 'https://domain.com/reporting' } };\n"
                    + "}";
        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();
        double bid = 5;

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            reportResult(
                                    jsScript,
                                    adSelectionConfig,
                                    TEST_DOMAIN_URI,
                                    bid,
                                    mContextualSignals);
                        });

        assertThat(exception.getCause()).isInstanceOf(JSExecutionException.class);
    }

    @Test
    public void testReportResultFailsWhenRegisterAdBeaconInputNotAnObject__String()
            throws Exception {
        String jsScript =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals)"
                        + " {\n"
                        + "registerAdBeacon('hi'); return {'status': 0, 'results':"
                        + " {'signals_for_buyer': '{\"seller\":\"' + ad_selection_config.seller +"
                        + " '\"}', 'reporting_uri': 'https://domain.com/reporting' } };\n"
                        + "}";
        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();
        double bid = 5;

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            reportResult(
                                    jsScript,
                                    adSelectionConfig,
                                    TEST_DOMAIN_URI,
                                    bid,
                                    mContextualSignals);
                        });

        assertThat(exception.getCause()).isInstanceOf(JSExecutionException.class);
    }

    @Test
    public void testReportResultFailsWhenRegisterAdBeaconInputNotAnObject__Array()
            throws Exception {
        String jsScript =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals)"
                        + " {\n"
                        + "registerAdBeacon(['hi']); return {'status': 0, 'results':"
                        + " {'signals_for_buyer': '{\"seller\":\"' + ad_selection_config.seller +"
                        + " '\"}', 'reporting_uri': 'https://domain.com/reporting' } };\n"
                        + "}";
        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();
        double bid = 5;

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            reportResult(
                                    jsScript,
                                    adSelectionConfig,
                                    TEST_DOMAIN_URI,
                                    bid,
                                    mContextualSignals);
                        });

        assertThat(exception.getCause()).isInstanceOf(JSExecutionException.class);
    }

    @Test
    public void testReportResultSuccessfulCaseWithNoBeaconRegistered() throws Exception {
        String jsScript =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) "
                        + "{\n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"seller\":\"' + ad_selection_config.seller + '\"}', "
                        + "'reporting_uri': 'https://domain.com/reporting' } };\n"
                        + "}";
        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();
        double bid = 5;

        final SellerReportingResult result =
                reportResult(jsScript, adSelectionConfig, TEST_DOMAIN_URI, bid, mContextualSignals);

        assertEquals(REPORTING_URI, result.getReportingUri());

        assertThat(
                        AdSelectionSignals.fromString(
                                SELLER_KEY + adSelectionConfig.getSeller() + "\"}"))
                .isEqualTo(result.getSignalsForBuyer());

        assertEquals(0, result.getInteractionReportingUris().size());
    }

    @Test
    public void testReportResultSuccessfulCaseDoesNotExceedInteractionReportingUrisMaxSize()
            throws Exception {
        // Re-init Engine with smaller max size
        mReportImpressionScriptEngine =
                initEngine(
                        true,
                        FLAGS_WITH_SMALLER_MAX_ARRAY_SIZE
                                .getFledgeReportImpressionMaxRegisteredAdBeaconsPerAdTechCount());

        String jsScript =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) "
                        + "{\n"
                        + "const beacons = {'click': 'https://domain.com/click', 'hover': "
                        + "'https://domain.com/hover', 'view': 'https://domain.com/view'};\n"
                        + "registerAdBeacon(beacons);"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"seller\":\"' + ad_selection_config.seller + '\"}', "
                        + "'reporting_uri': 'https://domain.com/reporting' } };\n"
                        + "}";
        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();
        double bid = 5;

        final SellerReportingResult result =
                reportResult(jsScript, adSelectionConfig, TEST_DOMAIN_URI, bid, mContextualSignals);

        assertEquals(REPORTING_URI, result.getReportingUri());

        assertThat(
                        AdSelectionSignals.fromString(
                                SELLER_KEY + adSelectionConfig.getSeller() + "\"}"))
                .isEqualTo(result.getSignalsForBuyer());

        assertEquals(2, result.getInteractionReportingUris().size());
    }

    @Test
    public void testReportResultFailedStatusCase() throws Exception {
        String jsScript =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) {"
                        + " \n"
                        + " return {'status': -1, 'results': {'signals_for_buyer':"
                        + " '{\"seller\":\"' + ad_selection_config.seller + '\"}', "
                        + "'reporting_uri': 'https://domain.com/reporting' } };\n"
                        + "}";
        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();
        double bid = 5;

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            reportResult(
                                    jsScript,
                                    adSelectionConfig,
                                    TEST_DOMAIN_URI,
                                    bid,
                                    mContextualSignals);
                        });
        assertThat(exception.getCause()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void testReportResultFailedCaseNoReportingUri() throws Exception {
        String jsScript =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) {"
                        + " \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"seller\":\"' + ad_selection_config.seller + '\"}' } };\n"
                        + "}";
        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();
        double bid = 5;

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            reportResult(
                                    jsScript,
                                    adSelectionConfig,
                                    TEST_DOMAIN_URI,
                                    bid,
                                    mContextualSignals);
                        });
        assertThat(exception.getCause()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void testReportResultIncorrectReportingUriNameCase() throws Exception {
        String jsScript =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) {"
                        + " \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"seller\":\"' + ad_selection_config.seller + '\"}', "
                        + "'incorrect_name_reporting_uri': 'https://domain.com/reporting' }"
                        + " };\n"
                        + "}";
        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();
        double bid = 5;

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            reportResult(
                                    jsScript,
                                    adSelectionConfig,
                                    TEST_DOMAIN_URI,
                                    bid,
                                    mContextualSignals);
                        });
        assertThat(exception.getCause()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void testReportResultIncorrectNameForResultsCase() throws Exception {
        String jsScript =
                "function reportResult(ad_selection_config, render_uri, bid, contextual_signals) {"
                        + " \n"
                        + " return {'status': 0, 'incorrect_results': {'signals_for_buyer':"
                        + " '{\"seller\":\"' + ad_selection_config.seller + '\"}', "
                        + "'reporting_uri': 'https://domain.com/reporting' } };\n"
                        + "}";
        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();
        double bid = 5;

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            reportResult(
                                    jsScript,
                                    adSelectionConfig,
                                    TEST_DOMAIN_URI,
                                    bid,
                                    mContextualSignals);
                        });
        assertThat(exception.getCause()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    public void testReportWinSuccessfulCaseRegisterAdBeaconEnabled() throws Exception {
        String jsScript =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,"
                        + " contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'results': {'reporting_uri':"
                        + " 'https://domain.com/reporting' } };\n"
                        + "}";
        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();

        final BuyerReportingResult result =
                reportWin(
                        jsScript,
                        adSelectionConfig.getAdSelectionSignals(),
                        adSelectionConfig.getPerBuyerSignals().get(BUYER_1),
                        mSignalsForBuyer,
                        adSelectionConfig.getSellerSignals(),
                        mCustomAudienceSignals);
        assertEquals(REPORTING_URI, result.getReportingUri());
    }

    @Test
    public void testReportWinSuccessfulCaseRegisterAdBeaconEnabledDisabled() throws Exception {
        // Re init engine
        mReportImpressionScriptEngine =
                initEngine(false, mFledgeReportImpressionMaxRegisteredAdBeaconsPerAdTechCount);

        String jsScript =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,"
                        + " contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'results': {'reporting_uri':"
                        + " 'https://domain.com/reporting' } };\n"
                        + "}";
        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();

        final BuyerReportingResult result =
                reportWin(
                        jsScript,
                        adSelectionConfig.getAdSelectionSignals(),
                        adSelectionConfig.getPerBuyerSignals().get(BUYER_1),
                        mSignalsForBuyer,
                        adSelectionConfig.getSellerSignals(),
                        mCustomAudienceSignals);
        assertEquals(REPORTING_URI, result.getReportingUri());
    }

    @Test
    public void testReportWinSuccessfulCaseMoreResultsFieldsThanExpected() throws Exception {
        String jsScript =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer, "
                        + "contextual_signals, custom_audience_signals) {\n"
                        + "    return {'status': 0, 'results': {'reporting_uri': 'https://domain"
                        + ".com/reporting', 'extra_key': 'extra_value'}}\n"
                        + "}";
        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();

        final BuyerReportingResult result =
                reportWin(
                        jsScript,
                        adSelectionConfig.getAdSelectionSignals(),
                        adSelectionConfig.getPerBuyerSignals().get(BUYER_1),
                        mSignalsForBuyer,
                        adSelectionConfig.getSellerSignals(),
                        mCustomAudienceSignals);
        assertEquals(REPORTING_URI, result.getReportingUri());
    }

    @Test
    public void testReportWinSuccessfulCaseWithCallingRegisterAdBeacon() throws Exception {
        String jsScript =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer ,"
                        + "contextual_signals, custom_audience_signals) {\n"
                        + "const beacons = {'click': 'https://domain.com/click', 'hover': "
                        + "'https://domain.com/hover'};\n"
                        + "registerAdBeacon(beacons);"
                        + "    return {'status': 0, 'results': {'reporting_uri': 'https://domain"
                        + ".com/reporting' }};\n"
                        + "}";
        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();
        final BuyerReportingResult result =
                reportWin(
                        jsScript,
                        adSelectionConfig.getAdSelectionSignals(),
                        adSelectionConfig.getPerBuyerSignals().get(BUYER_1),
                        mSignalsForBuyer,
                        adSelectionConfig.getSellerSignals(),
                        mCustomAudienceSignals);
        assertEquals(REPORTING_URI, result.getReportingUri());

        assertEquals(2, result.getInteractionReportingUris().size());

        assertThat(
                        ImmutableList.of(
                                CLICK_EVENT_URI_REGISTRATION_INFO,
                                HOVER_EVENT_URI_REGISTRATION_INFO))
                .containsExactlyElementsIn(result.getInteractionReportingUris());
    }

    @Test
    public void testReportWinFailsWhenCallingRegisterAdBeaconFlagDisabled() throws Exception {
        // Re init engine
        mReportImpressionScriptEngine =
                initEngine(false, mFledgeReportImpressionMaxRegisteredAdBeaconsPerAdTechCount);

        String jsScript =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer ,"
                        + "contextual_signals, custom_audience_signals) {\n"
                        + "const beacons = {'click': 'https://domain.com/click', 'hover': "
                        + "'https://domain.com/hover'};\n"
                        + "registerAdBeacon(beacons);"
                        + "    return {'status': 0, 'results': {'reporting_uri': 'https://domain"
                        + ".com/reporting' }};\n"
                        + "}";
        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            reportWin(
                                    jsScript,
                                    adSelectionConfig.getAdSelectionSignals(),
                                    adSelectionConfig.getPerBuyerSignals().get(BUYER_1),
                                    mSignalsForBuyer,
                                    adSelectionConfig.getSellerSignals(),
                                    mCustomAudienceSignals);
                        });

        assertThat(exception.getCause()).isInstanceOf(JSExecutionException.class);
    }

    @Test
    @FlakyTest(bugId = 315521295)
    public void testReportWinFailsInvalidInteractionKeyType() throws Exception {
        String jsScript =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer ,"
                        + "contextual_signals, custom_audience_signals) {\n"
                        + "const beacons = {{'hi'}: 'https://domain.com/click'};\n"
                        + "registerAdBeacon(beacons); return {'status': 0, 'results':"
                        + "    return {'status': 0, 'results': {'reporting_uri': 'https://domain"
                        + ".com/reporting' }};\n"
                        + "}";
        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            reportWin(
                                    jsScript,
                                    adSelectionConfig.getAdSelectionSignals(),
                                    adSelectionConfig.getPerBuyerSignals().get(BUYER_1),
                                    mSignalsForBuyer,
                                    adSelectionConfig.getSellerSignals(),
                                    mCustomAudienceSignals);
                        });

        assertThat(exception.getCause()).isInstanceOf(JSExecutionException.class);
    }

    @Test
    @FlakyTest(bugId = 315521295)
    public void testReportWinFailsInvalidInteractionReportingUriType() throws Exception {
        String jsScript =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer ,"
                        + "contextual_signals, custom_audience_signals) {\n"
                        + "const beacons = {'click': 1};\n"
                        + "registerAdBeacon(beacons); return {'status': 0, 'results':"
                        + "    return {'status': 0, 'results': {'reporting_uri': 'https://domain"
                        + ".com/reporting' }};\n"
                        + "}";
        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            reportWin(
                                    jsScript,
                                    adSelectionConfig.getAdSelectionSignals(),
                                    adSelectionConfig.getPerBuyerSignals().get(BUYER_1),
                                    mSignalsForBuyer,
                                    adSelectionConfig.getSellerSignals(),
                                    mCustomAudienceSignals);
                        });

        assertThat(exception.getCause()).isInstanceOf(JSExecutionException.class);
    }

    @Test
    @FlakyTest(bugId = 315521295)
    public void testReportWinFailsWhenRegisterAdBeaconCalledMoreThanOnce() throws Exception {
        String jsScript =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer"
                    + " ,contextual_signals, custom_audience_signals) {\n"
                    + "const beacons_1 = {'click': ''https://domain.com/click''};\n"
                    + "const beacons_2 = {'hover': ''https://domain.com/hober''};\n"
                    + "registerAdBeacon(beacons_1);registerAdBeacon(beacons_2); return {'status':  "
                    + "  return {'status': 0, 'results': {'reporting_uri':"
                    + " 'https://domain.com/reporting' }};\n"
                    + "}";
        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            reportWin(
                                    jsScript,
                                    adSelectionConfig.getAdSelectionSignals(),
                                    adSelectionConfig.getPerBuyerSignals().get(BUYER_1),
                                    mSignalsForBuyer,
                                    adSelectionConfig.getSellerSignals(),
                                    mCustomAudienceSignals);
                        });

        assertThat(exception.getCause()).isInstanceOf(JSExecutionException.class);
    }

    @Test
    public void testFailsWhenRegisterAdBeaconInputNotAnObject__Null() throws Exception {
        String jsScript =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer ,"
                        + "contextual_signals, custom_audience_signals) {\n"
                        + "registerAdBeacon(null);"
                        + "    return {'status': 0, 'results': {'reporting_uri': 'https://domain"
                        + ".com/reporting' }};\n"
                        + "}";
        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            reportWin(
                                    jsScript,
                                    adSelectionConfig.getAdSelectionSignals(),
                                    adSelectionConfig.getPerBuyerSignals().get(BUYER_1),
                                    mSignalsForBuyer,
                                    adSelectionConfig.getSellerSignals(),
                                    mCustomAudienceSignals);
                        });

        assertThat(exception.getCause()).isInstanceOf(JSExecutionException.class);
    }

    @Test
    public void testFailsWhenRegisterAdBeaconInputNotAnObject__Int() throws Exception {
        String jsScript =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer ,"
                        + "contextual_signals, custom_audience_signals) {\n"
                        + "registerAdBeacon(1);"
                        + "    return {'status': 0, 'results': {'reporting_uri': 'https://domain"
                        + ".com/reporting' }};\n"
                        + "}";
        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            reportWin(
                                    jsScript,
                                    adSelectionConfig.getAdSelectionSignals(),
                                    adSelectionConfig.getPerBuyerSignals().get(BUYER_1),
                                    mSignalsForBuyer,
                                    adSelectionConfig.getSellerSignals(),
                                    mCustomAudienceSignals);
                        });

        assertThat(exception.getCause()).isInstanceOf(JSExecutionException.class);
    }

    @Test
    public void testFailsWhenRegisterAdBeaconInputNotAnObject__Array() throws Exception {
        String jsScript =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer ,"
                        + "contextual_signals, custom_audience_signals) {\n"
                        + "registerAdBeacon(['hi']);"
                        + "    return {'status': 0, 'results': {'reporting_uri': 'https://domain"
                        + ".com/reporting' }};\n"
                        + "}";
        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();

        Exception exception =
                assertThrows(
                        ExecutionException.class,
                        () -> {
                            reportWin(
                                    jsScript,
                                    adSelectionConfig.getAdSelectionSignals(),
                                    adSelectionConfig.getPerBuyerSignals().get(BUYER_1),
                                    mSignalsForBuyer,
                                    adSelectionConfig.getSellerSignals(),
                                    mCustomAudienceSignals);
                        });

        assertThat(exception.getCause()).isInstanceOf(JSExecutionException.class);
    }

    @Test
    public void testReportWinSuccessfulCaseWithNoBeaconRegistered() throws Exception {
        String jsScript =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer ,"
                        + "contextual_signals, custom_audience_signals) {\n"
                        + "    return {'status': 0, 'results': {'reporting_uri': 'https://domain"
                        + ".com/reporting' }};\n"
                        + "}";
        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();
        final BuyerReportingResult result =
                reportWin(
                        jsScript,
                        adSelectionConfig.getAdSelectionSignals(),
                        adSelectionConfig.getPerBuyerSignals().get(BUYER_1),
                        mSignalsForBuyer,
                        adSelectionConfig.getSellerSignals(),
                        mCustomAudienceSignals);
        assertEquals(REPORTING_URI, result.getReportingUri());

        assertEquals(0, result.getInteractionReportingUris().size());
    }

    @Test
    public void testReportWinSuccessfulCaseDoesNotExceedInteractionReportingUrisMaxSize()
            throws Exception {
        // Re-init Engine with smaller max size
        mReportImpressionScriptEngine =
                initEngine(
                        true,
                        FLAGS_WITH_SMALLER_MAX_ARRAY_SIZE
                                .getFledgeReportImpressionMaxRegisteredAdBeaconsPerAdTechCount());

        String jsScript =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer ,"
                        + "contextual_signals, custom_audience_signals) {\n"
                        + "const beacons = {'click': 'https://domain.com/click', 'hover': "
                        + "'https://domain.com/hover', 'view': 'https://domain.com/view'};\n"
                        + "registerAdBeacon(beacons);"
                        + "    return {'status': 0, 'results': {'reporting_uri': 'https://domain"
                        + ".com/reporting' }};\n"
                        + "}";
        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();
        final BuyerReportingResult result =
                reportWin(
                        jsScript,
                        adSelectionConfig.getAdSelectionSignals(),
                        adSelectionConfig.getPerBuyerSignals().get(BUYER_1),
                        mSignalsForBuyer,
                        adSelectionConfig.getSellerSignals(),
                        mCustomAudienceSignals);
        assertEquals(REPORTING_URI, result.getReportingUri());

        assertEquals(2, result.getInteractionReportingUris().size());
    }

    @Test
    public void testReportWinFailedStatusCase() throws Exception {
        String jsScript =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,"
                        + " contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': -1, 'results': {'reporting_uri':"
                        + " 'https://domain.com/reporting' } };\n"
                        + "}";
        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();

        assertThrows(
                ExecutionException.class,
                () -> {
                    reportWin(
                            jsScript,
                            adSelectionConfig.getAdSelectionSignals(),
                            adSelectionConfig.getPerBuyerSignals().get(BUYER_1),
                            mSignalsForBuyer,
                            adSelectionConfig.getSellerSignals(),
                            mCustomAudienceSignals);
                });
    }

    @Test
    public void testReportWinIncorrectReportingUriNameCase() throws Exception {
        String jsScript =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,"
                        + " contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'results': {'incorrect_reporting_uri':"
                        + " 'https://domain.com/incorrectReporting' } };\n"
                        + "}";
        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();

        assertThrows(
                ExecutionException.class,
                () -> {
                    reportWin(
                            jsScript,
                            adSelectionConfig.getAdSelectionSignals(),
                            adSelectionConfig.getPerBuyerSignals().get(BUYER_1),
                            mSignalsForBuyer,
                            adSelectionConfig.getSellerSignals(),
                            mCustomAudienceSignals);
                });
    }

    @Test
    public void testReportWinIncorrectNameForResultsCase() throws Exception {
        String jsScript =
                "function reportWin(ad_selection_signals, per_buyer_signals, signals_for_buyer,"
                        + " contextual_signals, custom_audience_signals) { \n"
                        + " return {'status': 0, 'incorrect_results': {'reporting_uri':"
                        + " 'https://domain.com/reporting' } };\n"
                        + "}";
        AdSelectionConfig adSelectionConfig = AdSelectionConfigFixture.anAdSelectionConfig();

        assertThrows(
                ExecutionException.class,
                () -> {
                    reportWin(
                            jsScript,
                            adSelectionConfig.getAdSelectionSignals(),
                            adSelectionConfig.getPerBuyerSignals().get(BUYER_1),
                            mSignalsForBuyer,
                            adSelectionConfig.getSellerSignals(),
                            mCustomAudienceSignals);
                });
    }

    private ReportingScriptResult callReportingEngine(
            String jsScript, String functionCall, List<JSScriptArgument> args) throws Exception {
        return waitForFuture(
                () -> {
                    Log.i(TAG, "Calling Reporting Script Engine");
                    return mReportImpressionScriptEngine.runReportingScript(
                            jsScript, functionCall, args);
                });
    }

    private SellerReportingResult reportResult(
            String jsScript,
            AdSelectionConfig adSelectionConfig,
            Uri renderUri,
            double bid,
            AdSelectionSignals contextualSignals)
            throws Exception {

        return waitForFuture(
                () -> {
                    Log.i(TAG, "Calling reportResult");
                    return mReportImpressionScriptEngine.reportResult(
                            jsScript, adSelectionConfig, renderUri, bid, contextualSignals);
                });
    }

    private BuyerReportingResult reportWin(
            String jsScript,
            AdSelectionSignals adSelectionSignals,
            AdSelectionSignals perBuyerSignals,
            AdSelectionSignals signalsForBuyer,
            AdSelectionSignals contextualSignals,
            CustomAudienceSignals customAudienceSignals)
            throws Exception {
        return waitForFuture(
                () -> {
                    Log.i(TAG, "Calling reportWin");
                    return mReportImpressionScriptEngine.reportWin(
                            jsScript,
                            adSelectionSignals,
                            perBuyerSignals,
                            signalsForBuyer,
                            contextualSignals,
                            customAudienceSignals);
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

    interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    private ReportImpressionScriptEngine initEngine(
            boolean registerAdBeaconEnabled, long maxInteractionReportingUrisSize) {
        ReportImpressionScriptEngine.RegisterAdBeaconScriptEngineHelper
                registerAdBeaconScriptEngineHelper;

        if (registerAdBeaconEnabled) {
            registerAdBeaconScriptEngineHelper =
                    new ReportImpressionScriptEngine.RegisterAdBeaconScriptEngineHelperEnabled(
                            maxInteractionReportingUrisSize);
        } else {
            registerAdBeaconScriptEngineHelper =
                    new ReportImpressionScriptEngine.RegisterAdBeaconScriptEngineHelperDisabled();
        }
        return new ReportImpressionScriptEngine(
                sContext,
                () -> mIsolateSettings.getEnforceMaxHeapSizeFeature(),
                () -> mIsolateSettings.getMaxHeapSizeBytes(),
                registerAdBeaconScriptEngineHelper,
                new NoOpRetryStrategyImpl());
    }
}
