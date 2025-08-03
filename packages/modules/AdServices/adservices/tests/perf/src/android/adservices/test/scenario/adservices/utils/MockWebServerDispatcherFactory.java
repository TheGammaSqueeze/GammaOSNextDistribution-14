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

package android.adservices.test.scenario.adservices.utils;

import android.adservices.common.AdSelectionSignals;

import com.google.common.collect.ImmutableList;
import com.google.mockwebserver.Dispatcher;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.RecordedRequest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

/** Setup the dispatcher for mock web server. */
public final class MockWebServerDispatcherFactory {

    // Networks types
    public static final String NETWORK_5G = "5G";
    public static final String NETWORK_4GPLUS = "4GPLUS";
    public static final String NETWORK_4G = "4G";

    public static final String DECISION_LOGIC_PATH = "/seller/decision/simple_logic_with_delay";
    public static final String TRUSTED_SCORING_SIGNAL_PATH =
            "/trusted/scoringsignals/simple_with_delay";
    public static final String TRUSTED_BIDDING_SIGNALS_PATH =
            "/trusted/biddingsignals/simple_with_delay";
    public static final ArrayList<String> VALID_TRUSTED_BIDDING_KEYS =
            new ArrayList<>(Arrays.asList("example", "valid", "list", "of", "keys"));
    // Estimated based on
    // https://docs.google.com/spreadsheets/d/1EP_cwBbwYI-NMro0Qq5uif1krwjIQhjK8fjOu15j7hQ/edit?usp=sharing&resourcekey=0-A67kzEnAKKz1k7qpshSedg
    public static final Map<Integer, Integer> scoringJsPercentileToExecutionTimeMs =
            Map.of(50, 40, 90, 70);
    public static final Map<Integer, Integer> biddingJsPercentileToExecutionTimeMs =
            Map.of(50, 40, 90, 70);
    // Map from network name to (percentile, delay ms)
    public static final Map<String, Map<Integer, Integer>> DECISION_LOGIC_DELAY_MS =
            generateNetworkMap(22, 23, 56, 57, 114, 116);
    public static final Map<String, Map<Integer, Integer>> BIDDING_LOGIC_DELAY_MS =
            generateNetworkMap(23, 25, 57, 62, 116, 128);
    public static final Map<String, Map<Integer, Integer>> SCORING_SIGNALS_DELAY_MS =
            generateNetworkMap(21, 22, 51, 52, 101, 104);
    public static final Map<String, Map<Integer, Integer>> BIDDING_SIGNALS_DELAY_MS =
            generateNetworkMap(22, 47, 53, 123, 105, 275);

    // Estimated based on https://screenshot.googleplex.com/5PW2bQ8Azfyb9rS
    // Assuming PP API has access to only 10% of bandwidth
    public static final Map<String, Integer> NETWORK_TO_BANDWIDTH =
            Map.of(NETWORK_5G, 50, NETWORK_4GPLUS, 16, NETWORK_4G, 6);

    // Base throttling constant. Feel free to increase it to see more drastic throttling.
    public static final int BASE_THROTTLING = 2;

    private static final String BUYER_REPORTING_PATH = "/reporting/buyer";
    private static final String SELLER_REPORTING_PATH = "/reporting/seller";
    private static final String BUYER_BIDDING_LOGIC_URI_PATH =
            "/buyer/bidding/simple_logic_with_delay";
    private static final String BUYER_BIDDING_LOGIC_URI_PATH_FIXED_BID =
            "/buyer/bidding/simple_logic_with_delay_fixed_bid";
    private static final String DEFAULT_DECISION_LOGIC_JS_WITH_EXECUTION_TIME_FORMAT =
            "function scoreAd(ad, bid, auction_config, seller_signals,"
                    + " trusted_scoring_signals, contextual_signal, user_signal,"
                    + " custom_audience_signal) { \n"
                    + " const start = Date.now(); let now = start; while (now-start < %d) "
                    + "{now=Date.now();}\n"
                    + "  return {'status': 0, 'score': bid };\n"
                    + "}\n"
                    + "function reportResult(ad_selection_config, render_uri, bid,"
                    + " contextual_signals) { \n"
                    + " return {'status': 0, 'results': {'signals_for_buyer':"
                    + " '{\"signals_for_buyer\":1}', 'reporting_uri': '%s"
                    + "' } };\n"
                    + "}";
    private static final String DEFAULT_BIDDING_LOGIC_JS_WITH_EXECUTION_TIME_FORMAT =
            "function generateBid(ad, auction_signals, per_buyer_signals,"
                    + " trusted_bidding_signals, contextual_signals,"
                    + " custom_audience_signals) { \n"
                    + " const start = Date.now(); let now = start; while (now-start < %d) "
                    + "{now=Date.now();}\n"
                    + " return {'status': 0, 'ad': ad, 'bid': ad.metadata.result };\n"
                    + "}\n"
                    + "function reportWin(ad_selection_signals, per_buyer_signals,"
                    + " signals_for_buyer, contextual_signals, custom_audience_signals) { \n"
                    + " return {'status': 0, 'results': {'reporting_uri': '%s"
                    + "' } };\n"
                    + "}";
    private static final String DEFAULT_BIDDING_LOGIC_JS_FIXED_BID =
            "function generateBid(ad, auction_signals, per_buyer_signals,"
                    + " trusted_bidding_signals, contextual_signals,"
                    + " custom_audience_signals) { \n"
                    + " const start = Date.now(); let now = start; while (now-start < %d) "
                    + "{now=Date.now();}\n"
                    + " return {'status': 0, 'ad': ad, 'bid': 1 };\n"
                    + "}\n"
                    + "function reportWin(ad_selection_signals, per_buyer_signals,"
                    + " signals_for_buyer, contextual_signals, custom_audience_signals) { \n"
                    + " return {'status': 0, 'results': {'reporting_uri': '%s"
                    + "' } };\n"
                    + "}";
    private static final AdSelectionSignals TRUSTED_SCORING_SIGNALS =
            AdSelectionSignals.fromString(
                    "{\n"
                            + "\t\"render_uri_1\": \"signals_for_1\",\n"
                            + "\t\"render_uri_2\": \"signals_for_2\"\n"
                            + "}");
    private static final AdSelectionSignals TRUSTED_BIDDING_SIGNALS =
            AdSelectionSignals.fromString(
                    "{\n"
                            + "\t\"example\": \"example\",\n"
                            + "\t\"valid\": \"Also valid\",\n"
                            + "\t\"list\": \"list\",\n"
                            + "\t\"of\": \"of\",\n"
                            + "\t\"keys\": \"trusted bidding signal Values\"\n"
                            + "}");

    public static Dispatcher createLatencyDispatcher(
            MockWebServerRule mockWebServerRule, String network, int percentile) {
        return create(
                DECISION_LOGIC_DELAY_MS.get(network).get(percentile),
                BIDDING_LOGIC_DELAY_MS.get(network).get(percentile),
                SCORING_SIGNALS_DELAY_MS.get(network).get(percentile),
                BIDDING_SIGNALS_DELAY_MS.get(network).get(percentile),
                biddingJsPercentileToExecutionTimeMs.get(percentile),
                scoringJsPercentileToExecutionTimeMs.get(percentile),
                NETWORK_TO_BANDWIDTH.get(network),
                mockWebServerRule);
    }

    private static Dispatcher create(
            int decisionLogicFetchDelayMs,
            int biddingLogicFetchDelayMs,
            int scoringSignalFetchDelayMs,
            int biddingSignalFetchDelayMs,
            int biddingLogicExecutionRunMs,
            int scoringLogicExecutionRunMs,
            int bandwidth,
            MockWebServerRule mockWebServerRule) {

        return new Dispatcher() {

            private int mNumRequests = 0;

            @Override
            public MockResponse dispatch(RecordedRequest request) {
                mNumRequests++;
                if (DECISION_LOGIC_PATH.equals(request.getPath())) {
                    return new MockResponse()
                            .setBodyDelayTimeMs(
                                    (int)
                                            (decisionLogicFetchDelayMs
                                                    * getThrottlingFactor(bandwidth, mNumRequests)))
                            .setBody(
                                    getDecisionLogicJS(
                                            scoringLogicExecutionRunMs,
                                            mockWebServerRule
                                                    .uriForPath(SELLER_REPORTING_PATH)
                                                    .toString()));
                } else if (BUYER_BIDDING_LOGIC_URI_PATH.equals(request.getPath())) {
                    return new MockResponse()
                            .setBodyDelayTimeMs(
                                    (int)
                                            (biddingLogicFetchDelayMs
                                                    * getThrottlingFactor(bandwidth, mNumRequests)))
                            .setBody(
                                    getBiddingLogicJS(
                                            biddingLogicExecutionRunMs,
                                            mockWebServerRule
                                                    .uriForPath(BUYER_REPORTING_PATH)
                                                    .toString()));
                } else if (BUYER_BIDDING_LOGIC_URI_PATH_FIXED_BID.equals((request.getPath()))) {
                    return new MockResponse()
                            .setBodyDelayTimeMs(
                                    (int)
                                            (biddingLogicFetchDelayMs
                                                    * getThrottlingFactor(bandwidth, mNumRequests)))
                            .setBody(
                                    getBiddingLogicJS(
                                            biddingLogicExecutionRunMs,
                                            mockWebServerRule
                                                    .uriForPath(BUYER_REPORTING_PATH)
                                                    .toString(),
                                            DEFAULT_BIDDING_LOGIC_JS_FIXED_BID));
                } else if (BUYER_REPORTING_PATH.equals(request.getPath())
                        || SELLER_REPORTING_PATH.equals(request.getPath())) {
                    return new MockResponse().setBody("");
                } else if (request.getPath().startsWith(TRUSTED_SCORING_SIGNAL_PATH)) {
                    return new MockResponse()
                            .setBodyDelayTimeMs(
                                    (int)
                                            (scoringSignalFetchDelayMs
                                                    * getThrottlingFactor(bandwidth, mNumRequests)))
                            .setBody(TRUSTED_SCORING_SIGNALS.toString());
                } else if (request.getPath().startsWith(TRUSTED_BIDDING_SIGNALS_PATH)) {
                    return new MockResponse()
                            .setBodyDelayTimeMs(
                                    (int)
                                            (biddingSignalFetchDelayMs
                                                    * getThrottlingFactor(bandwidth, mNumRequests)))
                            .setBody(TRUSTED_BIDDING_SIGNALS.toString());
                }
                return new MockResponse().setResponseCode(404);
            }

            private double getThrottlingFactor(int bandwidth, int numRequests) {
                if (numRequests <= bandwidth) {
                    return 1;
                }
                int numRequestsMod = numRequests % bandwidth;

                // do not throttle if > bandwidth/2
                if (numRequestsMod > (bandwidth / 2)) {
                    return 1;
                }

                // otherwise, throttle to the equation (BASE_THROTTLING + ln(1+x))
                return (BASE_THROTTLING + Math.log(1 + numRequestsMod));
            }
        };
    }

    public static String getBiddingLogicUriPath() {
        return BUYER_BIDDING_LOGIC_URI_PATH;
    }

    public static String getBiddingLogicUriPathFixedBid() {
        return BUYER_BIDDING_LOGIC_URI_PATH_FIXED_BID;
    }

    public static String getDecisionLogicPath() {
        return DECISION_LOGIC_PATH;
    }

    public static String getTrustedScoringSignalPath() {
        return TRUSTED_SCORING_SIGNAL_PATH;
    }

    public static ImmutableList<String> getValidTrustedBiddingKeys() {
        return ImmutableList.copyOf(VALID_TRUSTED_BIDDING_KEYS);
    }

    public static String getTrustedBiddingSignalsPath() {
        return TRUSTED_BIDDING_SIGNALS_PATH;
    }

    private static String getDecisionLogicJS(
            int scoringLogicExecutionRunMs, String sellerReportingUri) {
        return String.format(
                DEFAULT_DECISION_LOGIC_JS_WITH_EXECUTION_TIME_FORMAT,
                scoringLogicExecutionRunMs,
                sellerReportingUri);
    }

    private static String getBiddingLogicJS(
            int biddingLogicExecutionRunMs, String buyerReportingUri) {
        return getBiddingLogicJS(
                biddingLogicExecutionRunMs,
                buyerReportingUri,
                DEFAULT_BIDDING_LOGIC_JS_WITH_EXECUTION_TIME_FORMAT);
    }

    private static String getBiddingLogicJS(
            int biddingLogicExecutionRunMs, String buyerReportingUri, String script) {
        return String.format(script, biddingLogicExecutionRunMs, buyerReportingUri);
    }

    private static Map<String, Map<Integer, Integer>> generateNetworkMap(
            int latency5GP50,
            int latency5GP90,
            int latency4GPLUSP50,
            int latency4GPLUSP90,
            int latency4GP50,
            int latency4GP90) {
        return Map.of(
                NETWORK_5G, Map.of(50, latency5GP50, 90, latency5GP90),
                NETWORK_4GPLUS, Map.of(50, latency4GPLUSP50, 90, latency4GPLUSP90),
                NETWORK_4G, Map.of(50, latency4GP50, 90, latency4GP90));
    }
}
