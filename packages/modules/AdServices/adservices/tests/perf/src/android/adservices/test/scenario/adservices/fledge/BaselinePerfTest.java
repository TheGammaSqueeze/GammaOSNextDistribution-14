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

package android.adservices.test.scenario.adservices.fledge;

import android.adservices.adselection.AdSelectionOutcome;
import android.adservices.adselection.ReportImpressionRequest;
import android.adservices.customaudience.CustomAudience;

import com.android.compatibility.common.util.ShellUtils;

import com.google.common.collect.ImmutableList;
import com.google.mockwebserver.Dispatcher;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.RecordedRequest;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Self-contained performance/system health tests covering basic FLEDGE scenarios */
public class BaselinePerfTest extends AbstractPerfTest {
    @Test
    public void test_joinCustomAudience_success() throws Exception {
        CustomAudience ca =
                createCustomAudience(
                        BUYER_1, CUSTOM_AUDIENCE_SHOES, Collections.singletonList(1.0));
        mCustomAudienceClient
                .joinCustomAudience(ca)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        mCustomAudienceClient
                .leaveCustomAudience(ca.getBuyer(), ca.getName())
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    public void testAdSelectionAndReporting_normalFlow_success() throws Exception {
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);
        CustomAudience customAudience1 =
                createCustomAudience(BUYER_1, CUSTOM_AUDIENCE_SHOES, bidsForBuyer1);

        CustomAudience customAudience2 =
                createCustomAudience(BUYER_1, CUSTOM_AUDIENCE_SHIRT, bidsForBuyer2);
        List<CustomAudience> customAudienceList = Arrays.asList(customAudience1, customAudience2);

        mMockWebServerRule.startMockWebServer(mDefaultDispatcher);

        for (CustomAudience ca : customAudienceList) {
            mCustomAudienceClient
                    .joinCustomAudience(ca)
                    .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }

        // Running ad selection and asserting that the outcome is returned in < 10 seconds
        AdSelectionOutcome outcome =
                mAdSelectionClient
                        .selectAds(createAdSelectionConfig())
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // The winning ad should be ad3 from CA shirt
        Assert.assertEquals(
                "Ad selection outcome is not expected",
                createExpectedWinningUri(BUYER_1, CUSTOM_AUDIENCE_SHIRT, 3),
                outcome.getRenderUri());

        ReportImpressionRequest reportImpressionRequest =
                new ReportImpressionRequest(outcome.getAdSelectionId(), createAdSelectionConfig());

        // Performing reporting, and asserting that no exception is thrown
        mAdSelectionClient
                .reportImpression(reportImpressionRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Cleanup
        for (CustomAudience ca : customAudienceList) {
            mCustomAudienceClient
                    .leaveCustomAudience(ca.getBuyer(), ca.getName())
                    .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    @Test
    public void testAdSelectionAndReporting_normalFlowWithThrottling_success() throws Exception {
        ShellUtils.runShellCommand("device_config put adservices sdk_request_permits_per_second 1");
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);
        CustomAudience customAudience1 =
                createCustomAudience(BUYER_1, CUSTOM_AUDIENCE_SHOES, bidsForBuyer1);

        CustomAudience customAudience2 =
                createCustomAudience(BUYER_1, CUSTOM_AUDIENCE_SHIRT, bidsForBuyer2);
        List<CustomAudience> customAudienceList = Arrays.asList(customAudience1, customAudience2);

        mMockWebServerRule.startMockWebServer(mDefaultDispatcher);

        for (CustomAudience ca : customAudienceList) {
            addDelayToAvoidThrottle();
            mCustomAudienceClient
                    .joinCustomAudience(ca)
                    .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }

        // Running ad selection and asserting that the outcome is returned in < 10 seconds
        addDelayToAvoidThrottle();
        AdSelectionOutcome outcome =
                mAdSelectionClient
                        .selectAds(createAdSelectionConfig())
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // The winning ad should be ad3 from CA shirt
        Assert.assertEquals(
                "Ad selection outcome is not expected",
                createExpectedWinningUri(BUYER_1, CUSTOM_AUDIENCE_SHIRT, 3),
                outcome.getRenderUri());

        ReportImpressionRequest reportImpressionRequest =
                new ReportImpressionRequest(outcome.getAdSelectionId(), createAdSelectionConfig());

        // Performing reporting, and asserting that no exception is thrown
        addDelayToAvoidThrottle();
        mAdSelectionClient
                .reportImpression(reportImpressionRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Cleanup
        for (CustomAudience ca : customAudienceList) {
            addDelayToAvoidThrottle();
            mCustomAudienceClient
                    .leaveCustomAudience(ca.getBuyer(), ca.getName())
                    .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
        ShellUtils.runShellCommand(
                "device_config put adservices sdk_request_permits_per_second 1000");
    }

    @Test
    public void testAdSelectionAndReporting_executionHeavyJS_success() throws Exception {
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);
        CustomAudience customAudience1 =
                createCustomAudience(BUYER_1, CUSTOM_AUDIENCE_SHOES, bidsForBuyer1);

        CustomAudience customAudience2 =
                createCustomAudience(BUYER_1, CUSTOM_AUDIENCE_SHIRT, bidsForBuyer2);
        List<CustomAudience> customAudienceList = Arrays.asList(customAudience1, customAudience2);

        String calculation_intense_logic_js =
                "function scoreAd(ad, bid, auction_config, seller_signals,"
                        + " trusted_scoring_signals, contextual_signal, user_signal,"
                        + " custom_audience_signal) { \n"
                        + CALCULATION_INTENSE_JS
                        + "  return {'status': 0, 'score': bid };\n"
                        + "}\n"
                        + "function reportResult(ad_selection_config, render_uri, bid,"
                        + " contextual_signals) { \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + SELLER_REPORTING_PATH
                        + "' } };\n"
                        + "}";

        Dispatcher dispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        if (DECISION_LOGIC_PATH.equals(request.getPath())) {
                            return new MockResponse().setBody(calculation_intense_logic_js);
                        } else if (BUYER_BIDDING_LOGIC_URI_PATH.equals(request.getPath())) {
                            return new MockResponse().setBody(DEFAULT_BIDDING_LOGIC_JS);
                        } else if (BUYER_REPORTING_PATH.equals(request.getPath())
                                || SELLER_REPORTING_PATH.equals(request.getPath())) {
                            return new MockResponse().setBody("");
                        } else if (request.getPath().startsWith(TRUSTED_SCORING_SIGNAL_PATH)) {
                            return new MockResponse().setBody(TRUSTED_SCORING_SIGNALS.toString());
                        } else if (request.getPath().startsWith(VALID_TRUSTED_BIDDING_URI_PATH)) {
                            return new MockResponse().setBody(TRUSTED_BIDDING_SIGNALS.toString());
                        }
                        return new MockResponse().setResponseCode(404);
                    }
                };

        mMockWebServerRule.startMockWebServer(dispatcher);

        for (CustomAudience ca : customAudienceList) {
            mCustomAudienceClient
                    .joinCustomAudience(ca)
                    .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }

        // Running ad selection and asserting that the outcome is returned in < 10 seconds
        AdSelectionOutcome outcome =
                mAdSelectionClient
                        .selectAds(createAdSelectionConfig())
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // The winning ad should be ad3 from CA shirt
        Assert.assertEquals(
                "Ad selection outcome is not expected",
                createExpectedWinningUri(BUYER_1, CUSTOM_AUDIENCE_SHIRT, 3),
                outcome.getRenderUri());

        ReportImpressionRequest reportImpressionRequest =
                new ReportImpressionRequest(outcome.getAdSelectionId(), createAdSelectionConfig());

        // Performing reporting, and asserting that no exception is thrown
        mAdSelectionClient
                .reportImpression(reportImpressionRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Cleanup
        for (CustomAudience ca : customAudienceList) {
            mCustomAudienceClient
                    .leaveCustomAudience(ca.getBuyer(), ca.getName())
                    .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    @Test
    public void testAdSelectionAndReporting_memoryHeavyJS_success() throws Exception {
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);
        CustomAudience customAudience1 =
                createCustomAudience(BUYER_1, CUSTOM_AUDIENCE_SHOES, bidsForBuyer1);

        CustomAudience customAudience2 =
                createCustomAudience(BUYER_1, CUSTOM_AUDIENCE_SHIRT, bidsForBuyer2);
        List<CustomAudience> customAudienceList = Arrays.asList(customAudience1, customAudience2);

        String memory_intense_logic_js =
                "function scoreAd(ad, bid, auction_config, seller_signals,"
                        + " trusted_scoring_signals, contextual_signal, user_signal,"
                        + " custom_audience_signal) { \n"
                        + MEMORY_INTENSE_JS
                        + "  return {'status': 0, 'score': bid };\n"
                        + "}\n"
                        + "function reportResult(ad_selection_config, render_uri, bid,"
                        + " contextual_signals) { \n"
                        + " return {'status': 0, 'results': {'signals_for_buyer':"
                        + " '{\"signals_for_buyer\":1}', 'reporting_uri': '"
                        + SELLER_REPORTING_PATH
                        + "' } };\n"
                        + "}";

        Dispatcher dispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        if (DECISION_LOGIC_PATH.equals(request.getPath())) {
                            return new MockResponse().setBody(memory_intense_logic_js);
                        } else if (BUYER_BIDDING_LOGIC_URI_PATH.equals(request.getPath())) {
                            return new MockResponse().setBody(DEFAULT_BIDDING_LOGIC_JS);
                        } else if (BUYER_REPORTING_PATH.equals(request.getPath())
                                || SELLER_REPORTING_PATH.equals(request.getPath())) {
                            return new MockResponse().setBody("");
                        } else if (request.getPath().startsWith(TRUSTED_SCORING_SIGNAL_PATH)) {
                            return new MockResponse().setBody(TRUSTED_SCORING_SIGNALS.toString());
                        } else if (request.getPath().startsWith(VALID_TRUSTED_BIDDING_URI_PATH)) {
                            return new MockResponse().setBody(TRUSTED_BIDDING_SIGNALS.toString());
                        }
                        return new MockResponse().setResponseCode(404);
                    }
                };

        mMockWebServerRule.startMockWebServer(dispatcher);

        for (CustomAudience ca : customAudienceList) {
            mCustomAudienceClient
                    .joinCustomAudience(ca)
                    .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }

        // Running ad selection and asserting that the outcome is returned in < 10 seconds
        AdSelectionOutcome outcome =
                mAdSelectionClient
                        .selectAds(createAdSelectionConfig())
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // The winning ad should be ad3 from CA shirt
        Assert.assertEquals(
                "Ad selection outcome is not expected",
                createExpectedWinningUri(BUYER_1, CUSTOM_AUDIENCE_SHIRT, 3),
                outcome.getRenderUri());

        ReportImpressionRequest reportImpressionRequest =
                new ReportImpressionRequest(outcome.getAdSelectionId(), createAdSelectionConfig());

        // Performing reporting, and asserting that no exception is thrown
        mAdSelectionClient
                .reportImpression(reportImpressionRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Cleanup
        for (CustomAudience ca : customAudienceList) {
            mCustomAudienceClient
                    .leaveCustomAudience(ca.getBuyer(), ca.getName())
                    .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }

    @Test
    public void testAdSelectionAndReporting_multipleCustomAudienceList_success() throws Exception {
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);
        CustomAudience customAudience1 =
                createCustomAudience(BUYER_1, CUSTOM_AUDIENCE_SHOES, bidsForBuyer1);

        CustomAudience customAudience2 =
                createCustomAudience(BUYER_1, CUSTOM_AUDIENCE_SHIRT, bidsForBuyer2);
        List<CustomAudience> customAudienceList = new ArrayList<>();
        customAudienceList.add(customAudience1);
        customAudienceList.add(customAudience2);

        // Create multiple generic custom audience entries
        for (int i = 1; i <= 48; i++) {
            CustomAudience customAudience =
                    createCustomAudience(BUYER_1, "GENERIC_CA_" + i, bidsForBuyer1);
            customAudienceList.add(customAudience);
        }
        mMockWebServerRule.startMockWebServer(mDefaultDispatcher);

        for (CustomAudience ca : customAudienceList) {
            mCustomAudienceClient
                    .joinCustomAudience(ca)
                    .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }

        // Running ad selection and asserting that the outcome is returned in < 10 seconds
        AdSelectionOutcome outcome =
                mAdSelectionClient
                        .selectAds(createAdSelectionConfig())
                        .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // The winning ad should be ad3 from CA shirt
        Assert.assertEquals(
                "Ad selection outcome is not expected",
                createExpectedWinningUri(BUYER_1, CUSTOM_AUDIENCE_SHIRT, 3),
                outcome.getRenderUri());

        ReportImpressionRequest reportImpressionRequest =
                new ReportImpressionRequest(outcome.getAdSelectionId(), createAdSelectionConfig());

        // Performing reporting, and asserting that no exception is thrown
        mAdSelectionClient
                .reportImpression(reportImpressionRequest)
                .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // Cleanup
        for (CustomAudience ca : customAudienceList) {
            mCustomAudienceClient
                    .leaveCustomAudience(ca.getBuyer(), ca.getName())
                    .get(API_RESPONSE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }
    }
}
