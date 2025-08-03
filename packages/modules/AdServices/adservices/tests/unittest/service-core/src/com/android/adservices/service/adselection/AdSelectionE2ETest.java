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
import static android.adservices.common.AdServicesStatusUtils.RATE_LIMIT_REACHED_ERROR_MESSAGE;
import static android.adservices.common.AdServicesStatusUtils.STATUS_CALLER_NOT_ALLOWED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
import static android.adservices.common.AdServicesStatusUtils.STATUS_INVALID_ARGUMENT;
import static android.adservices.common.AdServicesStatusUtils.STATUS_SUCCESS;
import static android.adservices.common.AdServicesStatusUtils.STATUS_TIMEOUT;
import static android.adservices.common.AdServicesStatusUtils.STATUS_UNAUTHORIZED;
import static android.adservices.common.AdServicesStatusUtils.STATUS_USER_CONSENT_REVOKED;
import static android.adservices.common.CommonFixture.TEST_PACKAGE_NAME;

import static com.android.adservices.data.adselection.AdSelectionDatabase.DATABASE_NAME;
import static com.android.adservices.data.encryptionkey.EncryptionKeyDaoTest.ENCRYPTION_KEY1;
import static com.android.adservices.data.enrollment.EnrollmentDaoTest.ENROLLMENT_DATA1;
import static com.android.adservices.common.CommonFlagsValues.EXTENDED_FLEDGE_AD_SELECTION_BIDDING_TIMEOUT_PER_CA_MS;
import static com.android.adservices.common.CommonFlagsValues.EXTENDED_FLEDGE_AD_SELECTION_FROM_OUTCOMES_OVERALL_TIMEOUT_MS;
import static com.android.adservices.common.CommonFlagsValues.EXTENDED_FLEDGE_AD_SELECTION_OVERALL_TIMEOUT_MS;
import static com.android.adservices.common.CommonFlagsValues.EXTENDED_FLEDGE_AD_SELECTION_SCORING_TIMEOUT_MS;
import static com.android.adservices.common.CommonFlagsValues.EXTENDED_FLEDGE_AD_SELECTION_SELECTING_OUTCOME_TIMEOUT_MS;
import static com.android.adservices.common.CommonFlagsValues.EXTENDED_FLEDGE_BACKGROUND_FETCH_NETWORK_CONNECT_TIMEOUT_MS;
import static com.android.adservices.common.CommonFlagsValues.EXTENDED_FLEDGE_BACKGROUND_FETCH_NETWORK_READ_TIMEOUT_MS;
import static com.android.adservices.common.CommonFlagsValues.EXTENDED_FLEDGE_REPORT_IMPRESSION_OVERALL_TIMEOUT_MS;
import static com.android.adservices.service.adselection.AdSelectionRunner.ERROR_AD_SELECTION_FAILURE;
import static com.android.adservices.service.adselection.AdSelectionRunner.ERROR_NO_BUYERS_OR_CONTEXTUAL_ADS_AVAILABLE;
import static com.android.adservices.service.adselection.AdSelectionRunner.ERROR_NO_CA_AND_CONTEXTUAL_ADS_AVAILABLE;
import static com.android.adservices.service.adselection.AdSelectionRunner.ERROR_NO_VALID_BIDS_OR_CONTEXTUAL_ADS_FOR_SCORING;
import static com.android.adservices.service.adselection.AdSelectionRunner.ERROR_NO_WINNING_AD_FOUND;
import static com.android.adservices.service.adselection.AdSelectionScriptEngine.NUM_BITS_STOCHASTIC_ROUNDING;
import static com.android.adservices.service.adselection.AdsScoreGeneratorImpl.MISSING_TRUSTED_SCORING_SIGNALS;
import static com.android.adservices.service.adselection.AdsScoreGeneratorImpl.SCORING_TIMED_OUT;
import static com.android.adservices.service.adselection.DataVersionFetcher.DATA_VERSION_HEADER_BIDDING_KEY;
import static com.android.adservices.service.adselection.DataVersionFetcher.DATA_VERSION_HEADER_SCORING_KEY;
import static com.android.adservices.service.adselection.JsFetcher.MISSING_SCORING_LOGIC;
import static com.android.adservices.service.adselection.PrebuiltLogicGenerator.AD_SELECTION_HIGHEST_BID_WINS;
import static com.android.adservices.service.adselection.PrebuiltLogicGenerator.AD_SELECTION_PREBUILT_SCHEMA;
import static com.android.adservices.service.adselection.PrebuiltLogicGenerator.AD_SELECTION_USE_CASE;
import static com.android.adservices.service.adselection.PrebuiltLogicGenerator.PREBUILT_FEATURE_IS_DISABLED;
import static com.android.adservices.service.adselection.signature.ProtectedAudienceSignatureManager.PUBLIC_TEST_KEY_STRING;
import static com.android.adservices.service.stats.AdSelectionExecutionLoggerTest.DB_AD_SELECTION_FILE_SIZE;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doNothing;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.eq;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.isA;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mock;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.never;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spy;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.times;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.when;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.AdditionalMatchers.geq;
import static org.mockito.Mockito.timeout;

import android.adservices.adid.AdId;
import android.adservices.adselection.AdSelectionCallback;
import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdSelectionConfigFixture;
import android.adservices.adselection.AdSelectionInput;
import android.adservices.adselection.AdSelectionResponse;
import android.adservices.adselection.ReportImpressionCallback;
import android.adservices.adselection.ReportImpressionInput;
import android.adservices.adselection.SignedContextualAds;
import android.adservices.adselection.SignedContextualAdsFixture;
import android.adservices.common.AdDataFixture;
import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdServicesStatusUtils;
import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CallerMetadata;
import android.adservices.common.CallingAppUidSupplierProcessImpl;
import android.adservices.common.CommonFixture;
import android.adservices.common.FledgeErrorResponse;
import android.adservices.customaudience.CustomAudienceFixture;
import android.adservices.customaudience.TrustedBiddingDataFixture;
import android.adservices.http.MockWebServerRule;
import android.net.Uri;
import android.os.LimitExceededException;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;
import android.webkit.WebView;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.FlakyTest;

import com.android.adservices.LoggerFactory;
import com.android.adservices.MockWebServerRuleFactory;
import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.common.RequiresSdkLevelAtLeastS;
import com.android.adservices.common.SupportedByConditionRule;
import com.android.adservices.common.WebViewSupportUtil;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.DbTestUtil;
import com.android.adservices.data.adselection.AdSelectionDatabase;
import com.android.adservices.data.adselection.AdSelectionDebugReportDao;
import com.android.adservices.data.adselection.AdSelectionDebugReportingDatabase;
import com.android.adservices.data.adselection.AdSelectionEntryDao;
import com.android.adservices.data.adselection.AppInstallDao;
import com.android.adservices.data.adselection.DBAdSelectionOverride;
import com.android.adservices.data.adselection.DBBuyerDecisionOverride;
import com.android.adservices.data.adselection.FrequencyCapDao;
import com.android.adservices.data.adselection.SharedStorageDatabase;
import com.android.adservices.data.common.DBAdData;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.CustomAudienceDatabase;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.data.customaudience.DBCustomAudienceOverride;
import com.android.adservices.data.customaudience.DBTrustedBiddingData;
import com.android.adservices.data.encryptionkey.EncryptionKeyDao;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.data.signals.EncodedPayloadDao;
import com.android.adservices.data.signals.ProtectedSignalsDatabase;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.adid.AdIdCacheManager;
import com.android.adservices.service.adselection.encryption.ObliviousHttpEncryptor;
import com.android.adservices.service.common.AdSelectionServiceFilter;
import com.android.adservices.service.common.FledgeAuthorizationFilter;
import com.android.adservices.service.common.RetryStrategyFactory;
import com.android.adservices.service.common.Throttler;
import com.android.adservices.service.common.cache.CacheProviderFactory;
import com.android.adservices.service.common.cache.FledgeHttpCache;
import com.android.adservices.service.common.cache.HttpCache;
import com.android.adservices.service.common.httpclient.AdServicesHttpsClient;
import com.android.adservices.service.consent.ConsentManager;
import com.android.adservices.service.devapi.AdSelectionDevOverridesHelper;
import com.android.adservices.service.devapi.DevContext;
import com.android.adservices.service.devapi.DevContextFilter;
import com.android.adservices.service.encryptionkey.EncryptionKey;
import com.android.adservices.service.exception.FilterException;
import com.android.adservices.service.js.JSScriptEngine;
import com.android.adservices.service.kanon.KAnonSignJoinFactory;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.AdServicesStatsLog;
import com.android.adservices.service.stats.RunAdBiddingProcessReportedStats;
import com.android.adservices.service.stats.RunAdScoringProcessReportedStats;
import com.android.adservices.service.stats.RunAdSelectionProcessReportedStats;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.modules.utils.testing.ExtendedMockitoRule.SpyStatic;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.mockwebserver.Dispatcher;
import com.google.mockwebserver.MockResponse;
import com.google.mockwebserver.MockWebServer;
import com.google.mockwebserver.RecordedRequest;

import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;

import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

/**
 * This test the actual flow of Ad Selection internal flow without any mocking. The dependencies in
 * this test are invoked and used in real time.
 */
// Test applications don't have the required permissions to read config P/H flags, and
// injecting mocked flags everywhere is annoying and non-trivial for static methods
@RequiresSdkLevelAtLeastS()
@SpyStatic(FlagsFactory.class)
@SpyStatic(WebView.class)
public final class AdSelectionE2ETest extends AdServicesExtendedMockitoTestCase {

    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    private static final int CALLER_UID = Process.myUid();
    private static final String ERROR_SCORE_AD_LOGIC_MISSING = "scoreAd is not defined";

    private static final AdTechIdentifier BUYER_1 = AdSelectionConfigFixture.BUYER_1;
    private static final AdTechIdentifier BUYER_2 = AdSelectionConfigFixture.BUYER_2;
    private static final AdTechIdentifier BUYER_3 = AdSelectionConfigFixture.BUYER_3;
    private static final String BUYER = "buyer";
    private static final String AD_URI_PREFIX = "http://www.domain.com/adverts/123/";
    private static final String DEFAULT_CUSTOM_AUDIENCE_NAME_SUFFIX = "";

    private static final String BUYER_BIDDING_LOGIC_URI_PATH = "/buyer/bidding/logic/";
    private static final String BUYER_BIDDING_LOGIC_URI_PATH_AD_COST =
            "/buyer/bidding/logic/adCost/";
    private static final String BUYER_TRUSTED_SIGNAL_URI_PATH_WITH_DATA_VERSION =
            "/kv/buyer/withDataVersion/signals/";
    private static final String BUYER_TRUSTED_SIGNAL_URI_PATH = "/kv/buyer/signals/";
    private static final String BUYER_TRUSTED_SIGNAL_PARAMS =
            "?keys=example%2Cvalid%2Clist%2Cof%2Ckeys";

    private static final String SELLER_DECISION_LOGIC_URI_PATH = "/ssp/decision/logic/";
    private static final String SELLER_TRUSTED_SIGNAL_URI_PATH = "/kv/seller/signals/";
    private static final String SELLER_TRUSTED_SIGNAL_URI_PATH_WITH_DATA_VERSION =
            "/kv/seller/withDataVersion/signals/";
    private static final String SELLER_TRUSTED_SIGNAL_PARAMS = "?renderuris=";
    private static final String SELLER_REPORTING_URI_PATH = "ssp/reporting/";
    private static final String BUYER_REPORTING_URI_PATH = "dsp/reporting/";

    public static final String READ_BID_FROM_AD_METADATA_JS =
            "function generateBid(ad, auction_signals, per_buyer_signals,"
                    + " trusted_bidding_signals, contextual_signals,"
                    + " custom_audience_signals) { \n"
                    + "  return {'status': 0, 'ad': ad, 'bid': ad.metadata.result };\n"
                    + "}\n"
                    + "\n"
                    + "function reportWin(ad_selection_signals, per_buyer_signals,"
                    + " signals_for_buyer, contextual_signals, custom_audience_signals) { \n"
                    + " return {'status': 0, 'results': {'reporting_uri': '%s' } };\n"
                    + "}";

    public static final String READ_BID_FROM_AD_METADATA_JS_WITH_AD_COST =
            "function generateBid(ad, auction_signals, per_buyer_signals, trusted_bidding_signals,"
                    + " contextual_signals, custom_audience_signals) { \n"
                    + "  return {'status': 0, 'ad': ad, 'bid': ad.metadata.result, 'adCost':"
                    + " ad.metadata.adCost };\n"
                    + "}\n"
                    + "\n"
                    + "function reportWin(ad_selection_signals, per_buyer_signals, "
                    + "signals_for_buyer,"
                    + " contextual_signals, custom_audience_signals) { \n"
                    + " return {'status': 0, 'results': {'reporting_uri': '%s' } };\n"
                    + "}";

    private static final String READ_BID_FROM_AD_METADATA_JS_V3 =
            "function generateBid(custom_audience, auction_signals, per_buyer_signals,\n"
                    + "    trusted_bidding_signals, contextual_signals) {\n"
                    + "    const ads = custom_audience.ads;\n"
                    + "    let result = null;\n"
                    + "    for (const ad of ads) {\n"
                    + "        if (!result || ad.metadata.result > result.metadata.result) {\n"
                    + "            result = ad;\n"
                    + "        }\n"
                    + "    }\n"
                    + "    return { 'status': 0, 'ad': result, 'bid': result.metadata.result, "
                    + "'render': result.render_uri };\n"
                    + "}";

    private static final String READ_BID_FROM_AD_METADATA_JS_V3_WITH_AD_COST =
            "function generateBid(custom_audience, auction_signals, per_buyer_signals,\n"
                    + "    trusted_bidding_signals, contextual_signals) {\n"
                    + "    const ads = custom_audience.ads;\n"
                    + "    let result = null;\n"
                    + "    for (const ad of ads) {\n"
                    + "        if (!result || ad.metadata.result > result.metadata.result) {\n"
                    + "            result = ad;\n"
                    + "        }\n"
                    + "    }\n"
                    + "    return { 'status': 0, 'ad': result, 'bid': result.metadata.result, "
                    + "'render': result.render_uri, 'adCost': result.metadata.adCost };\n"
                    + "}";

    public static final String USE_BID_AS_SCORE_JS =
            "//From dispatcher USE_BID_AS_SCORE_JS\n"
                    + "function scoreAd(ad, bid, auction_config, seller_signals,"
                    + " trusted_scoring_signals, contextual_signal, user_signal,"
                    + " custom_audience_signal) { \n"
                    + "  return {'status': 0, 'score': bid };\n"
                    + "}\n"
                    + "\n"
                    + "function reportResult(ad_selection_config, render_uri, bid, "
                    + "contextual_signals)"
                    + " { \n"
                    + " return {'status': 0, 'results': {'signals_for_buyer':"
                    + " '{\"signals_for_buyer\":1}', 'reporting_uri': '%s' } };\n"
                    + "}";

    private static final String SELECTION_PICK_HIGHEST_LOGIC_JS_PATH =
            "/selectionPickHighestLogicJS/";
    private static final String SELECTION_PICK_NONE_LOGIC_JS_PATH = "/selectionPickNoneLogicJS/";
    private static final String SELECTION_WATERFALL_LOGIC_JS_PATH = "/selectionWaterfallLogicJS/";
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
    private static final String SELECTION_WATERFALL_LOGIC_JS =
            "function selectOutcome(outcomes, selection_signals) {\n"
                    + "    if (outcomes.length != 1 || selection_signals.bid_floor =="
                    + " undefined) return null;\n"
                    + "\n"
                    + "    const outcome_1p = outcomes[0];\n"
                    + "    return {'status': 0, 'result': (outcome_1p.bid >"
                    + " selection_signals.bid_floor) ? outcome_1p : null};\n"
                    + "}";

    private static final Map<String, String> TRUSTED_BIDDING_SIGNALS_SERVER_DATA =
            new ImmutableMap.Builder<String, String>()
                    .put("example", "example")
                    .put("valid", "Also valid")
                    .put("list", "list")
                    .put("of", "of")
                    .put("keys", "trusted bidding signal Values")
                    .build();

    private static final AdSelectionSignals TRUSTED_SCORING_SIGNALS =
            AdSelectionSignals.fromString(
                    "{\n"
                            + "\t\"render_uri_1\": \"signals_for_1\",\n"
                            + "\t\"render_uri_2\": \"signals_for_2\"\n"
                            + "}");
    private static final MockWebServerRule.RequestMatcher<String> REQUEST_PREFIX_MATCHER =
            (a, b) -> !b.isEmpty() && a.startsWith(b);

    private static final AdCost AD_COST_1 = new AdCost(1.2, NUM_BITS_STOCHASTIC_ROUNDING);
    private static final AdCost AD_COST_2 = new AdCost(2.2, NUM_BITS_STOCHASTIC_ROUNDING);

    // TODO(b/275657377) Refactor duplicate dispatchers
    public static final Dispatcher DISPATCHER_V2_ONLY_BIDDING_LOGIC =
            new Dispatcher() {
                @Override
                public MockResponse dispatch(RecordedRequest request) {
                    if (SELLER_DECISION_LOGIC_URI_PATH.equals(request.getPath())) {
                        return new MockResponse().setBody(USE_BID_AS_SCORE_JS);
                    } else if ((BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1.toString())
                                    .equals(request.getPath())
                            || (BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2.toString())
                                    .equals(request.getPath())) {
                        return new MockResponse().setBody(READ_BID_FROM_AD_METADATA_JS);
                    } else if (request.getPath().equals(SELECTION_PICK_HIGHEST_LOGIC_JS_PATH)) {
                        return new MockResponse().setBody(SELECTION_PICK_HIGHEST_LOGIC_JS);
                    } else if (request.getPath().equals(SELECTION_PICK_NONE_LOGIC_JS_PATH)) {
                        return new MockResponse().setBody(SELECTION_PICK_NONE_LOGIC_JS);
                    } else if (request.getPath().equals(SELECTION_WATERFALL_LOGIC_JS_PATH)) {
                        return new MockResponse().setBody(SELECTION_WATERFALL_LOGIC_JS);
                    } else if (request.getPath().startsWith(BUYER_TRUSTED_SIGNAL_URI_PATH)) {
                        String[] keys =
                                Uri.parse(request.getPath())
                                        .getQueryParameter(DBTrustedBiddingData.QUERY_PARAM_KEYS)
                                        .split(",");
                        Map<String, String> jsonMap = new HashMap<>();
                        for (String key : keys) {
                            jsonMap.put(key, TRUSTED_BIDDING_SIGNALS_SERVER_DATA.get(key));
                        }
                        return new MockResponse().setBody(new JSONObject(jsonMap).toString());
                    }

                    // The seller params vary based on runtime, so we are returning trusted
                    // signals based on correct path prefix
                    if (request.getPath()
                            .startsWith(
                                    SELLER_TRUSTED_SIGNAL_URI_PATH
                                            + SELLER_TRUSTED_SIGNAL_PARAMS)) {
                        return new MockResponse().setBody(TRUSTED_SCORING_SIGNALS.toString());
                    }
                    sLogger.w("Unexpected call to MockWebServer " + request.getPath());
                    return new MockResponse().setResponseCode(404);
                }
            };
    public static final Dispatcher DISPATCHER_V3_BIDDING_LOGIC =
            new Dispatcher() {
                @Override
                public MockResponse dispatch(RecordedRequest request) {
                    if (SELLER_DECISION_LOGIC_URI_PATH.equals(request.getPath())) {
                        return new MockResponse().setBody(USE_BID_AS_SCORE_JS);
                    } else if ((BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1.toString())
                                    .equals(request.getPath())
                            || (BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2.toString())
                                    .equals(request.getPath())) {
                        String headerName =
                                JsVersionHelper.getVersionHeaderName(
                                        JsVersionHelper.JS_PAYLOAD_TYPE_BUYER_BIDDING_LOGIC_JS);
                        long versionFromHeader = Long.parseLong(request.getHeader(headerName));
                        if (JsVersionRegister.BUYER_BIDDING_LOGIC_VERSION_VERSION_3
                                == versionFromHeader) {
                            return new MockResponse()
                                    .setBody(READ_BID_FROM_AD_METADATA_JS_V3)
                                    .setHeader(headerName, versionFromHeader);
                        }
                    } else if ((BUYER_BIDDING_LOGIC_URI_PATH_AD_COST + BUYER_1.toString())
                                    .equals(request.getPath())
                            || (BUYER_BIDDING_LOGIC_URI_PATH_AD_COST + BUYER_2.toString())
                                    .equals(request.getPath())) {
                        String headerName =
                                JsVersionHelper.getVersionHeaderName(
                                        JsVersionHelper.JS_PAYLOAD_TYPE_BUYER_BIDDING_LOGIC_JS);
                        long versionFromHeader = Long.parseLong(request.getHeader(headerName));
                        if (JsVersionRegister.BUYER_BIDDING_LOGIC_VERSION_VERSION_3
                                == versionFromHeader) {
                            return new MockResponse()
                                    .setBody(READ_BID_FROM_AD_METADATA_JS_V3_WITH_AD_COST)
                                    .setHeader(headerName, versionFromHeader);
                        }
                    } else if (request.getPath().equals(SELECTION_PICK_HIGHEST_LOGIC_JS_PATH)) {
                        return new MockResponse().setBody(SELECTION_PICK_HIGHEST_LOGIC_JS);
                    } else if (request.getPath().equals(SELECTION_PICK_NONE_LOGIC_JS_PATH)) {
                        return new MockResponse().setBody(SELECTION_PICK_NONE_LOGIC_JS);
                    } else if (request.getPath().equals(SELECTION_WATERFALL_LOGIC_JS_PATH)) {
                        return new MockResponse().setBody(SELECTION_WATERFALL_LOGIC_JS);
                    } else if (request.getPath().startsWith(BUYER_TRUSTED_SIGNAL_URI_PATH)) {
                        String[] keys =
                                Uri.parse(request.getPath())
                                        .getQueryParameter(DBTrustedBiddingData.QUERY_PARAM_KEYS)
                                        .split(",");
                        Map<String, String> jsonMap = new HashMap<>();
                        for (String key : keys) {
                            jsonMap.put(key, TRUSTED_BIDDING_SIGNALS_SERVER_DATA.get(key));
                        }
                        return new MockResponse().setBody(new JSONObject(jsonMap).toString());
                    } else if (request.getPath()
                            .startsWith(
                                    BUYER_TRUSTED_SIGNAL_URI_PATH_WITH_DATA_VERSION
                                            + BUYER_1.toString())) {
                        String[] keys =
                                Uri.parse(request.getPath())
                                        .getQueryParameter(DBTrustedBiddingData.QUERY_PARAM_KEYS)
                                        .split(",");
                        Map<String, String> jsonMap = new HashMap<>();
                        for (String key : keys) {
                            jsonMap.put(key, TRUSTED_BIDDING_SIGNALS_SERVER_DATA.get(key));
                        }
                        return new MockResponse()
                                .setBody(new JSONObject(jsonMap).toString())
                                .addHeader(DATA_VERSION_HEADER_BIDDING_KEY, DATA_VERSION_1);
                    } else if (request.getPath()
                            .startsWith(
                                    BUYER_TRUSTED_SIGNAL_URI_PATH_WITH_DATA_VERSION
                                            + BUYER_2.toString())) {
                        String[] keys =
                                Uri.parse(request.getPath())
                                        .getQueryParameter(DBTrustedBiddingData.QUERY_PARAM_KEYS)
                                        .split(",");
                        Map<String, String> jsonMap = new HashMap<>();
                        for (String key : keys) {
                            jsonMap.put(key, TRUSTED_BIDDING_SIGNALS_SERVER_DATA.get(key));
                        }
                        return new MockResponse()
                                .setBody(new JSONObject(jsonMap).toString())
                                .addHeader(DATA_VERSION_HEADER_BIDDING_KEY, DATA_VERSION_2);
                    }

                    // The seller params vary based on runtime, so we are returning trusted
                    // signals based on correct path prefix
                    if (request.getPath()
                            .startsWith(
                                    SELLER_TRUSTED_SIGNAL_URI_PATH
                                            + SELLER_TRUSTED_SIGNAL_PARAMS)) {
                        return new MockResponse().setBody(TRUSTED_SCORING_SIGNALS.toString());
                    } else if (request.getPath()
                            .startsWith(
                                    SELLER_TRUSTED_SIGNAL_URI_PATH_WITH_DATA_VERSION
                                            + SELLER_TRUSTED_SIGNAL_PARAMS)) {
                        return new MockResponse()
                                .setBody(TRUSTED_SCORING_SIGNALS.toString())
                                .addHeader(DATA_VERSION_HEADER_SCORING_KEY, DATA_VERSION_1);
                    }
                    sLogger.w("Unexpected call to MockWebServer " + request.getPath());
                    return new MockResponse().setResponseCode(404);
                }
            };

    public static final Dispatcher DISPATCHER_V3_BIDDING_LOGIC_HEADER_WITH_V2_LOGIC =
            new Dispatcher() {
                @Override
                public MockResponse dispatch(RecordedRequest request) {
                    if (SELLER_DECISION_LOGIC_URI_PATH.equals(request.getPath())) {
                        return new MockResponse().setBody(USE_BID_AS_SCORE_JS);
                    } else if ((BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1.toString())
                                    .equals(request.getPath())
                            || (BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2.toString())
                                    .equals(request.getPath())) {
                        String headerName =
                                JsVersionHelper.getVersionHeaderName(
                                        JsVersionHelper.JS_PAYLOAD_TYPE_BUYER_BIDDING_LOGIC_JS);
                        return new MockResponse()
                                .setBody(READ_BID_FROM_AD_METADATA_JS)
                                .setHeader(
                                        headerName,
                                        JsVersionRegister.BUYER_BIDDING_LOGIC_VERSION_VERSION_3);
                    } else if (request.getPath().equals(SELECTION_PICK_HIGHEST_LOGIC_JS_PATH)) {
                        return new MockResponse().setBody(SELECTION_PICK_HIGHEST_LOGIC_JS);
                    } else if (request.getPath().equals(SELECTION_PICK_NONE_LOGIC_JS_PATH)) {
                        return new MockResponse().setBody(SELECTION_PICK_NONE_LOGIC_JS);
                    } else if (request.getPath().equals(SELECTION_WATERFALL_LOGIC_JS_PATH)) {
                        return new MockResponse().setBody(SELECTION_WATERFALL_LOGIC_JS);
                    } else if (request.getPath().startsWith(BUYER_TRUSTED_SIGNAL_URI_PATH)) {
                        String[] keys =
                                Uri.parse(request.getPath())
                                        .getQueryParameter(DBTrustedBiddingData.QUERY_PARAM_KEYS)
                                        .split(",");
                        Map<String, String> jsonMap = new HashMap<>();
                        for (String key : keys) {
                            jsonMap.put(key, TRUSTED_BIDDING_SIGNALS_SERVER_DATA.get(key));
                        }
                        return new MockResponse().setBody(new JSONObject(jsonMap).toString());
                    }

                    // The seller params vary based on runtime, so we are returning trusted
                    // signals based on correct path prefix
                    if (request.getPath()
                            .startsWith(
                                    SELLER_TRUSTED_SIGNAL_URI_PATH
                                            + SELLER_TRUSTED_SIGNAL_PARAMS)) {
                        return new MockResponse().setBody(TRUSTED_SCORING_SIGNALS.toString());
                    }
                    sLogger.w("Unexpected call to MockWebServer " + request.getPath());
                    return new MockResponse().setResponseCode(404);
                }
            };

    public static final Dispatcher DISPATCHER_TOO_HIGH_BIDDING_LOGIC_JS_VERSION =
            new Dispatcher() {
                @Override
                public MockResponse dispatch(RecordedRequest request) {
                    if (SELLER_DECISION_LOGIC_URI_PATH.equals(request.getPath())) {
                        return new MockResponse().setBody(USE_BID_AS_SCORE_JS);
                    } else if ((BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1.toString())
                                    .equals(request.getPath())
                            || (BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2.toString())
                                    .equals(request.getPath())) {
                        String headerName =
                                JsVersionHelper.getVersionHeaderName(
                                        JsVersionHelper.JS_PAYLOAD_TYPE_BUYER_BIDDING_LOGIC_JS);
                        return new MockResponse()
                                .setBody(READ_BID_FROM_AD_METADATA_JS)
                                .setHeader(
                                        headerName,
                                        JsVersionRegister.BUYER_BIDDING_LOGIC_VERSION_VERSION_3
                                                + 1);
                    } else if (request.getPath().equals(SELECTION_PICK_HIGHEST_LOGIC_JS_PATH)) {
                        return new MockResponse().setBody(SELECTION_PICK_HIGHEST_LOGIC_JS);
                    } else if (request.getPath().equals(SELECTION_PICK_NONE_LOGIC_JS_PATH)) {
                        return new MockResponse().setBody(SELECTION_PICK_NONE_LOGIC_JS);
                    } else if (request.getPath().equals(SELECTION_WATERFALL_LOGIC_JS_PATH)) {
                        return new MockResponse().setBody(SELECTION_WATERFALL_LOGIC_JS);
                    } else if (request.getPath().startsWith(BUYER_TRUSTED_SIGNAL_URI_PATH)) {
                        String[] keys =
                                Uri.parse(request.getPath())
                                        .getQueryParameter(DBTrustedBiddingData.QUERY_PARAM_KEYS)
                                        .split(",");
                        Map<String, String> jsonMap = new HashMap<>();
                        for (String key : keys) {
                            jsonMap.put(key, TRUSTED_BIDDING_SIGNALS_SERVER_DATA.get(key));
                        }
                        return new MockResponse().setBody(new JSONObject(jsonMap).toString());
                    }

                    // The seller params vary based on runtime, so we are returning trusted
                    // signals based on correct path prefix
                    if (request.getPath()
                            .startsWith(
                                    SELLER_TRUSTED_SIGNAL_URI_PATH
                                            + SELLER_TRUSTED_SIGNAL_PARAMS)) {
                        return new MockResponse().setBody(TRUSTED_SCORING_SIGNALS.toString());
                    }
                    sLogger.w("Unexpected call to MockWebServer " + request.getPath());
                    return new MockResponse().setResponseCode(404);
                }
            };

    private static final AdTechIdentifier SELLER_VALID =
            AdTechIdentifier.fromString("developer.android.com");
    private static final Uri DECISION_LOGIC_URI_INCONSISTENT =
            Uri.parse("https://developer%$android.com/test/decisions_logic_uris");
    private static final long BINDER_ELAPSED_TIME_MS = 100L;
    private static final String CALLER_PACKAGE_NAME = TEST_PACKAGE_NAME;
    private static final String MY_APP_PACKAGE_NAME = TEST_PACKAGE_NAME;
    private final AdServicesLogger mAdServicesLoggerMock =
            ExtendedMockito.mock(AdServicesLoggerImpl.class);
    private Flags mFlags = new AdSelectionE2ETestFlags();

    // Every test in this class requires that the JS Sandbox be available. The JS Sandbox
    // availability depends on an external component (the system webview) being higher than a
    // certain minimum version.
    @Rule(order = 11)
    public final SupportedByConditionRule webViewSupportsJSSandbox =
            WebViewSupportUtil.createJSSandboxAvailableRule(
                    ApplicationProvider.getApplicationContext());

    @Rule(order = 12)
    public final MockWebServerRule mMockWebServerRule = MockWebServerRuleFactory.createForHttps();

    // Mocking DevContextFilter to test behavior with and without override api authorization
    @Mock private DevContextFilter mDevContextFilter;
    @Mock private CallerMetadata mMockCallerMetadata;
    @Mock private FledgeHttpCache.HttpCacheObserver mCacheObserver;

    @Mock private File mMockDBAdSelectionFile;
    @Mock private ConsentManager mConsentManagerMock;
    @Mock private KAnonSignJoinFactory mUnusedKAnonSignJoinFactory;

    private FledgeAuthorizationFilter mFledgeAuthorizationFilterSpy =
            spy(
                    new FledgeAuthorizationFilter(
                            mSpyContext.getPackageManager(),
                            new EnrollmentDao(
                                    mSpyContext, DbTestUtil.getSharedDbHelperForTest(), mFlags),
                            mAdServicesLoggerMock));

    private ExecutorService mLightweightExecutorService;
    private ExecutorService mBackgroundExecutorService;
    private ScheduledThreadPoolExecutor mScheduledExecutor;
    private CustomAudienceDao mCustomAudienceDao;
    private EncodedPayloadDao mEncodedPayloadDao;
    private AppInstallDao mAppInstallDao;
    private FrequencyCapDao mFrequencyCapDao;
    private EnrollmentDao mEnrollmentDao;
    private EncryptionKeyDao mEncryptionKeyDao;
    @Spy private AdSelectionEntryDao mAdSelectionEntryDaoSpy;
    private AdServicesHttpsClient mAdServicesHttpsClient;
    private AdSelectionConfig mAdSelectionConfig;
    private AdSelectionServiceImpl mAdSelectionService;
    private Dispatcher mDispatcher;
    private AdTechIdentifier mSeller;
    private AdFilteringFeatureFactory mAdFilteringFeatureFactory;

    @Mock private AdSelectionServiceFilter mAdSelectionServiceFilter;
    @Mock private ObliviousHttpEncryptor mObliviousHttpEncryptor;
    private MultiCloudSupportStrategy mMultiCloudSupportStrategy;
    private AdSelectionDebugReportDao mAdSelectionDebugReportDao;

    private MockAdIdWorker mMockAdIdWorker;
    private AdIdFetcher mAdIdFetcher;
    private RetryStrategyFactory mRetryStrategyFactory;

    @Before
    public void setUp() throws Exception {
        doReturn(new AdSelectionE2ETestFlags()).when(FlagsFactory::getFlags);

        mAdSelectionEntryDaoSpy =
                Room.inMemoryDatabaseBuilder(mSpyContext, AdSelectionDatabase.class)
                        .build()
                        .adSelectionEntryDao();
        mAppInstallDao =
                Room.inMemoryDatabaseBuilder(mSpyContext, SharedStorageDatabase.class)
                        .build()
                        .appInstallDao();
        mFrequencyCapDao =
                Room.inMemoryDatabaseBuilder(mSpyContext, SharedStorageDatabase.class)
                        .build()
                        .frequencyCapDao();
        mEncryptionKeyDao = EncryptionKeyDao.getInstance(mSpyContext);
        mEnrollmentDao = EnrollmentDao.getInstance(mSpyContext);
        mAdFilteringFeatureFactory =
                new AdFilteringFeatureFactory(mAppInstallDao, mFrequencyCapDao, mFlags);
        mMultiCloudSupportStrategy =
                MultiCloudTestStrategyFactory.getDisabledTestStrategy(mObliviousHttpEncryptor);

        // Initialize dependencies for the AdSelectionService
        mLightweightExecutorService = AdServicesExecutors.getLightWeightExecutor();
        mBackgroundExecutorService = AdServicesExecutors.getBackgroundExecutor();
        mScheduledExecutor = AdServicesExecutors.getScheduler();
        mCustomAudienceDao =
                Room.inMemoryDatabaseBuilder(mSpyContext, CustomAudienceDatabase.class)
                        .addTypeConverter(new DBCustomAudience.Converters(true, true))
                        .build()
                        .customAudienceDao();
        mEncodedPayloadDao =
                Room.inMemoryDatabaseBuilder(mSpyContext, ProtectedSignalsDatabase.class)
                        .build()
                        .getEncodedPayloadDao();
        mAdServicesHttpsClient =
                new AdServicesHttpsClient(
                        AdServicesExecutors.getBlockingExecutor(),
                        CacheProviderFactory.createNoOpCache());
        mAdSelectionDebugReportDao =
                Room.inMemoryDatabaseBuilder(mSpyContext, AdSelectionDebugReportingDatabase.class)
                        .build()
                        .getAdSelectionDebugReportDao();
        mMockAdIdWorker = new MockAdIdWorker(new AdIdCacheManager(mSpyContext));
        mAdIdFetcher =
                new AdIdFetcher(mMockAdIdWorker, mLightweightExecutorService, mScheduledExecutor);
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
                        mSpyContext,
                        mAdServicesLoggerMock,
                        mFlags,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
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
        Uri uriPathForScoringWithReportResults =
                mMockWebServerRule.uriForPath(SELLER_REPORTING_URI_PATH);
        Uri uriPathForBiddingWithReportResults =
                mMockWebServerRule.uriForPath(BUYER_REPORTING_URI_PATH);
        mDispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        if (SELLER_DECISION_LOGIC_URI_PATH.equals(request.getPath())) {
                            return new MockResponse()
                                    .setBody(
                                            String.format(
                                                    USE_BID_AS_SCORE_JS,
                                                    uriPathForScoringWithReportResults));
                        } else if ((BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1.toString())
                                        .equals(request.getPath())
                                || (BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2.toString())
                                        .equals(request.getPath())) {
                            return new MockResponse()
                                    .setBody(
                                            String.format(
                                                    READ_BID_FROM_AD_METADATA_JS,
                                                    uriPathForBiddingWithReportResults));
                        } else if ((BUYER_BIDDING_LOGIC_URI_PATH_AD_COST + BUYER_1.toString())
                                        .equals(request.getPath())
                                || (BUYER_BIDDING_LOGIC_URI_PATH_AD_COST + BUYER_2.toString())
                                        .equals(request.getPath())) {
                            return new MockResponse()
                                    .setBody(READ_BID_FROM_AD_METADATA_JS_WITH_AD_COST);
                        } else if (request.getPath()
                                .startsWith(
                                        BUYER_TRUSTED_SIGNAL_URI_PATH_WITH_DATA_VERSION
                                                + BUYER_1.toString())) {
                            String[] keys =
                                    Uri.parse(request.getPath())
                                            .getQueryParameter(
                                                    DBTrustedBiddingData.QUERY_PARAM_KEYS)
                                            .split(",");
                            Map<String, String> jsonMap = new HashMap<>();
                            for (String key : keys) {
                                jsonMap.put(key, TRUSTED_BIDDING_SIGNALS_SERVER_DATA.get(key));
                            }
                            return new MockResponse()
                                    .setBody(new JSONObject(jsonMap).toString())
                                    .addHeader(DATA_VERSION_HEADER_BIDDING_KEY, DATA_VERSION_1);
                        } else if (request.getPath()
                                .startsWith(
                                        BUYER_TRUSTED_SIGNAL_URI_PATH_WITH_DATA_VERSION
                                                + BUYER_2.toString())) {
                            String[] keys =
                                    Uri.parse(request.getPath())
                                            .getQueryParameter(
                                                    DBTrustedBiddingData.QUERY_PARAM_KEYS)
                                            .split(",");
                            Map<String, String> jsonMap = new HashMap<>();
                            for (String key : keys) {
                                jsonMap.put(key, TRUSTED_BIDDING_SIGNALS_SERVER_DATA.get(key));
                            }
                            return new MockResponse()
                                    .setBody(new JSONObject(jsonMap).toString())
                                    .addHeader(DATA_VERSION_HEADER_BIDDING_KEY, DATA_VERSION_2);
                        } else if (request.getPath().equals(SELECTION_PICK_HIGHEST_LOGIC_JS_PATH)) {
                            return new MockResponse().setBody(SELECTION_PICK_HIGHEST_LOGIC_JS);
                        } else if (request.getPath().equals(SELECTION_PICK_NONE_LOGIC_JS_PATH)) {
                            return new MockResponse().setBody(SELECTION_PICK_NONE_LOGIC_JS);
                        } else if (request.getPath().equals(SELECTION_WATERFALL_LOGIC_JS_PATH)) {
                            return new MockResponse().setBody(SELECTION_WATERFALL_LOGIC_JS);
                        } else if (SELLER_REPORTING_URI_PATH.equals(request.getPath())) {
                            return new MockResponse().setBody("");
                        } else if (request.getPath().startsWith(BUYER_TRUSTED_SIGNAL_URI_PATH)) {
                            String[] keys =
                                    Uri.parse(request.getPath())
                                            .getQueryParameter(
                                                    DBTrustedBiddingData.QUERY_PARAM_KEYS)
                                            .split(",");
                            Map<String, String> jsonMap = new HashMap<>();
                            for (String key : keys) {
                                jsonMap.put(key, TRUSTED_BIDDING_SIGNALS_SERVER_DATA.get(key));
                            }
                            return new MockResponse().setBody(new JSONObject(jsonMap).toString());
                        }

                        // The seller params vary based on runtime, so we are returning trusted
                        // signals based on correct path prefix
                        if (request.getPath()
                                .startsWith(
                                        SELLER_TRUSTED_SIGNAL_URI_PATH
                                                + SELLER_TRUSTED_SIGNAL_PARAMS)) {
                            return new MockResponse().setBody(TRUSTED_SCORING_SIGNALS.toString());
                        } else if (request.getPath()
                                .startsWith(
                                        SELLER_TRUSTED_SIGNAL_URI_PATH_WITH_DATA_VERSION
                                                + SELLER_TRUSTED_SIGNAL_PARAMS)) {
                            return new MockResponse()
                                    .setBody(TRUSTED_SCORING_SIGNALS.toString())
                                    .addHeader(DATA_VERSION_HEADER_SCORING_KEY, DATA_VERSION_1);
                        }
                        sLogger.w("Unexpected call to MockWebServer " + request.getPath());
                        return new MockResponse().setResponseCode(404);
                    }
                };

        mSeller =
                AdTechIdentifier.fromString(
                        mMockWebServerRule.uriForPath(SELLER_DECISION_LOGIC_URI_PATH).getHost());

        // Create an Ad Selection Config with the buyers and decision logic URI
        // the URI points to a JS with score generation logic
        mAdSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setCustomAudienceBuyers(ImmutableList.of(BUYER_1, BUYER_2, BUYER_3))
                        .setSeller(mSeller)
                        .setDecisionLogicUri(
                                mMockWebServerRule.uriForPath(SELLER_DECISION_LOGIC_URI_PATH))
                        .setTrustedScoringSignalsUri(
                                mMockWebServerRule.uriForPath(SELLER_TRUSTED_SIGNAL_URI_PATH))
                        .build();
        when(mSpyContext.getDatabasePath(DATABASE_NAME)).thenReturn(mMockDBAdSelectionFile);
        when(mMockDBAdSelectionFile.length()).thenReturn(DB_AD_SELECTION_FILE_SIZE);
        doNothing()
                .when(mAdSelectionServiceFilter)
                .filterRequest(
                        mSeller,
                        CALLER_PACKAGE_NAME,
                        true,
                        true,
                        CALLER_UID,
                        AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS,
                        Throttler.ApiKey.FLEDGE_API_SELECT_ADS,
                        DevContext.createForDevOptionsDisabled());
        mMockAdIdWorker.setResult(AdId.ZERO_OUT, true);
    }

    @Test
    public void testRunAdSelectionSuccess_preV3BiddingLogic() throws Exception {
        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(3);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());

        mMockWebServerRule.startMockWebServer(mDispatcher);
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, mAdSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        assertCallbackIsSuccessful(resultsCallback);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(resultSelectionId));
        assertEquals(
                AD_URI_PREFIX + BUYER_2 + "/ad3",
                resultsCallback.mAdSelectionResponse.getRenderUri().toString());
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(isA(RunAdBiddingProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(isA(RunAdScoringProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        geq((int) BINDER_ELAPSED_TIME_MS));
    }

    @Test
    @FlakyTest(bugId = 304764127)
    public void testRunAdSelectionSuccess_preV3BiddingLogicWithAdCostCpcBillingEnabled()
            throws Exception {
        AdSelectionE2ETestFlags flagsWithCPCEnabled =
                new AdSelectionE2ETestFlags() {
                    @Override
                    public boolean getFledgeCpcBillingEnabled() {
                        return true;
                    }
                };
        doReturn(flagsWithCPCEnabled).when(FlagsFactory::getFlags);

        // Re init adSelection service with new flags
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
                        mSpyContext,
                        mAdServicesLoggerMock,
                        flagsWithCPCEnabled,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mAdSelectionServiceFilter,
                        mAdFilteringFeatureFactory,
                        mConsentManagerMock,
                        mMultiCloudSupportStrategy,
                        mAdSelectionDebugReportDao,
                        mAdIdFetcher,
                        mUnusedKAnonSignJoinFactory,
                        false,
                        mRetryStrategyFactory);

        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(3);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());

        mMockWebServerRule.startMockWebServer(mDispatcher);
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudienceWithAdCost(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(
                                BUYER_BIDDING_LOGIC_URI_PATH_AD_COST + BUYER_1),
                        bidsForBuyer1,
                        AD_COST_1.getAdCost());
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudienceWithAdCost(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(
                                BUYER_BIDDING_LOGIC_URI_PATH_AD_COST + BUYER_2),
                        bidsForBuyer2,
                        AD_COST_2.getAdCost());

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, mAdSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        assertCallbackIsSuccessful(resultsCallback);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(resultSelectionId));

        // Assert adCost was propagated through
        String expectedBuyerContextualSignals =
                BuyerContextualSignals.builder().setAdCost(AD_COST_2).build().toString();
        assertEquals(
                expectedBuyerContextualSignals,
                mAdSelectionEntryDaoSpy
                        .getAdSelectionEntityById(resultSelectionId)
                        .getBuyerContextualSignals());

        assertEquals(
                AD_URI_PREFIX + BUYER_2 + "/ad3",
                resultsCallback.mAdSelectionResponse.getRenderUri().toString());
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(isA(RunAdBiddingProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(isA(RunAdScoringProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        geq((int) BINDER_ELAPSED_TIME_MS));
    }

    @Test
    public void testRunAdSelectionSuccess_preV3BiddingLogicWithDataVersionHeaderFlagEnabled()
            throws Exception {
        AdSelectionE2ETestFlags flagsWithDataVersionHeaderEnabled =
                new AdSelectionE2ETestFlags() {
                    @Override
                    public boolean getFledgeDataVersionHeaderEnabled() {
                        return true;
                    }
                };
        doReturn(flagsWithDataVersionHeaderEnabled).when(FlagsFactory::getFlags);

        // Re init adSelection service with new flags
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
                        mSpyContext,
                        mAdServicesLoggerMock,
                        flagsWithDataVersionHeaderEnabled,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mAdSelectionServiceFilter,
                        mAdFilteringFeatureFactory,
                        mConsentManagerMock,
                        mMultiCloudSupportStrategy,
                        mAdSelectionDebugReportDao,
                        mAdIdFetcher,
                        mUnusedKAnonSignJoinFactory,
                        false,
                        mRetryStrategyFactory);

        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(3);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());

        mMockWebServerRule.startMockWebServer(mDispatcher);
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1,
                        BUYER_TRUSTED_SIGNAL_URI_PATH_WITH_DATA_VERSION + BUYER_1);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2,
                        BUYER_TRUSTED_SIGNAL_URI_PATH_WITH_DATA_VERSION + BUYER_2);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        // Instantiate new adSelectionConfig with data version header in trusted seller signals
        AdSelectionConfig adSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setCustomAudienceBuyers(ImmutableList.of(BUYER_1, BUYER_2, BUYER_3))
                        .setSeller(mSeller)
                        .setDecisionLogicUri(
                                mMockWebServerRule.uriForPath(SELLER_DECISION_LOGIC_URI_PATH))
                        .setTrustedScoringSignalsUri(
                                mMockWebServerRule.uriForPath(
                                        SELLER_TRUSTED_SIGNAL_URI_PATH_WITH_DATA_VERSION))
                        .build();

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, adSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        assertCallbackIsSuccessful(resultsCallback);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(resultSelectionId));

        // Assert data version was propagated through to buyer
        String expectedBuyerContextualSignals =
                BuyerContextualSignals.builder().setDataVersion(DATA_VERSION_2).build().toString();
        assertEquals(
                expectedBuyerContextualSignals,
                mAdSelectionEntryDaoSpy
                        .getAdSelectionEntityById(resultSelectionId)
                        .getBuyerContextualSignals());

        // Assert data version was propagated through to seller
        String expectedSellerContextualSignals =
                SellerContextualSignals.builder().setDataVersion(DATA_VERSION_1).build().toString();
        assertEquals(
                expectedSellerContextualSignals,
                mAdSelectionEntryDaoSpy
                        .getAdSelectionEntityById(resultSelectionId)
                        .getSellerContextualSignals());

        assertEquals(
                AD_URI_PREFIX + BUYER_2 + "/ad3",
                resultsCallback.mAdSelectionResponse.getRenderUri().toString());
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(isA(RunAdBiddingProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(isA(RunAdScoringProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        geq((int) BINDER_ELAPSED_TIME_MS));
    }

    @Test
    @FlakyTest(bugId = 304764127)
    public void testRunAdSelectionSuccess_preV3BiddingLogicWithDataVersionHeaderFlagDisabled()
            throws Exception {
        AdSelectionE2ETestFlags flagsWithDataVersionHeaderEnabled =
                new AdSelectionE2ETestFlags() {
                    @Override
                    public boolean getFledgeDataVersionHeaderEnabled() {
                        return false;
                    }
                };
        doReturn(flagsWithDataVersionHeaderEnabled).when(FlagsFactory::getFlags);

        // Re init adSelection service with new flags
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
                        mSpyContext,
                        mAdServicesLoggerMock,
                        flagsWithDataVersionHeaderEnabled,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mAdSelectionServiceFilter,
                        mAdFilteringFeatureFactory,
                        mConsentManagerMock,
                        mMultiCloudSupportStrategy,
                        mAdSelectionDebugReportDao,
                        mAdIdFetcher,
                        mUnusedKAnonSignJoinFactory,
                        false,
                        mRetryStrategyFactory);

        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(3);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());

        mMockWebServerRule.startMockWebServer(mDispatcher);
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1,
                        BUYER_TRUSTED_SIGNAL_URI_PATH_WITH_DATA_VERSION + BUYER_1);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2,
                        BUYER_TRUSTED_SIGNAL_URI_PATH_WITH_DATA_VERSION + BUYER_2);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        // Instantiate new adSelectionConfig with data version header in trusted seller signals
        AdSelectionConfig adSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setCustomAudienceBuyers(ImmutableList.of(BUYER_1, BUYER_2, BUYER_3))
                        .setSeller(mSeller)
                        .setDecisionLogicUri(
                                mMockWebServerRule.uriForPath(SELLER_DECISION_LOGIC_URI_PATH))
                        .setTrustedScoringSignalsUri(
                                mMockWebServerRule.uriForPath(
                                        SELLER_TRUSTED_SIGNAL_URI_PATH_WITH_DATA_VERSION))
                        .build();

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, adSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        assertCallbackIsSuccessful(resultsCallback);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(resultSelectionId));

        // Assert data version was not propagated through to buyer
        assertEquals(
                AdSelectionSignals.EMPTY.toString(),
                mAdSelectionEntryDaoSpy
                        .getAdSelectionEntityById(resultSelectionId)
                        .getBuyerContextualSignals());

        // Assert data version was not propagated through to buyer
        assertEquals(
                AdSelectionSignals.EMPTY.toString(),
                mAdSelectionEntryDaoSpy
                        .getAdSelectionEntityById(resultSelectionId)
                        .getSellerContextualSignals());

        assertEquals(
                AD_URI_PREFIX + BUYER_2 + "/ad3",
                resultsCallback.mAdSelectionResponse.getRenderUri().toString());
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(isA(RunAdBiddingProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(isA(RunAdScoringProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        geq((int) BINDER_ELAPSED_TIME_MS));
    }

    @Test
    @FlakyTest(bugId = 304764127)
    public void testRunAdSelectionSuccess_preV3BiddingLogicWinnerWithoutBuyerDataVersionHeader()
            throws Exception {
        AdSelectionE2ETestFlags flagsWithDataVersionHeaderEnabled =
                new AdSelectionE2ETestFlags() {
                    @Override
                    public boolean getFledgeDataVersionHeaderEnabled() {
                        return true;
                    }
                };
        doReturn(flagsWithDataVersionHeaderEnabled).when(FlagsFactory::getFlags);

        // Re init adSelection service with new flags
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
                        mSpyContext,
                        mAdServicesLoggerMock,
                        flagsWithDataVersionHeaderEnabled,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mAdSelectionServiceFilter,
                        mAdFilteringFeatureFactory,
                        mConsentManagerMock,
                        mMultiCloudSupportStrategy,
                        mAdSelectionDebugReportDao,
                        mAdIdFetcher,
                        mUnusedKAnonSignJoinFactory,
                        false,
                        mRetryStrategyFactory);
        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(3);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());

        mMockWebServerRule.startMockWebServer(mDispatcher);
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1,
                        BUYER_TRUSTED_SIGNAL_URI_PATH_WITH_DATA_VERSION + BUYER_1);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2,
                        BUYER_TRUSTED_SIGNAL_URI_PATH + BUYER_2);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, mAdSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        assertCallbackIsSuccessful(resultsCallback);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(resultSelectionId));

        // Assert data version was not propagated through since buyer 2 wins
        String expectedBuyerContextualSignals = BuyerContextualSignals.builder().build().toString();
        assertEquals(
                expectedBuyerContextualSignals,
                mAdSelectionEntryDaoSpy
                        .getAdSelectionEntityById(resultSelectionId)
                        .getBuyerContextualSignals());

        assertEquals(
                AD_URI_PREFIX + BUYER_2 + "/ad3",
                resultsCallback.mAdSelectionResponse.getRenderUri().toString());
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(isA(RunAdBiddingProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(isA(RunAdScoringProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        geq((int) BINDER_ELAPSED_TIME_MS));
    }

    @Test
    @FlakyTest(bugId = 304764127)
    public void testRunAdSelectionSuccess_preV3BiddingLogicWithAdCostCpcBillingDisabled()
            throws Exception {
        AdSelectionE2ETestFlags flagsWithCPCDisabled =
                new AdSelectionE2ETestFlags() {
                    @Override
                    public boolean getFledgeCpcBillingEnabled() {
                        return false;
                    }
                };
        doReturn(flagsWithCPCDisabled).when(FlagsFactory::getFlags);

        // Re init adSelection service with new flags
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
                        mSpyContext,
                        mAdServicesLoggerMock,
                        flagsWithCPCDisabled,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mAdSelectionServiceFilter,
                        mAdFilteringFeatureFactory,
                        mConsentManagerMock,
                        mMultiCloudSupportStrategy,
                        mAdSelectionDebugReportDao,
                        mAdIdFetcher,
                        mUnusedKAnonSignJoinFactory,
                        false,
                        mRetryStrategyFactory);

        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(3);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());

        mMockWebServerRule.startMockWebServer(mDispatcher);
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudienceWithAdCost(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(
                                BUYER_BIDDING_LOGIC_URI_PATH_AD_COST + BUYER_1),
                        bidsForBuyer1,
                        AD_COST_1.getAdCost());
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudienceWithAdCost(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(
                                BUYER_BIDDING_LOGIC_URI_PATH_AD_COST + BUYER_2),
                        bidsForBuyer2,
                        AD_COST_2.getAdCost());

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, mAdSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        assertCallbackIsSuccessful(resultsCallback);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(resultSelectionId));

        // Assert adCost was not propagated through due to flag disabled
        String expectedBuyerContextualSignals = "{}";
        assertEquals(
                expectedBuyerContextualSignals,
                mAdSelectionEntryDaoSpy
                        .getAdSelectionEntityById(resultSelectionId)
                        .getBuyerContextualSignals());

        assertEquals(
                AD_URI_PREFIX + BUYER_2 + "/ad3",
                resultsCallback.mAdSelectionResponse.getRenderUri().toString());
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(isA(RunAdBiddingProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(isA(RunAdScoringProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        geq((int) BINDER_ELAPSED_TIME_MS));
    }

    @Test
    public void testRunAdSelectionSuccess_prebuiltScoringLogic() throws Exception {
        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(3);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());

        mMockWebServerRule.startMockWebServer(mDispatcher);
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        String paramKey = "reportingUrl";
        String paramValue = "https://www.test.com/reporting/seller";
        AdSelectionConfig adSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setCustomAudienceBuyers(ImmutableList.of(BUYER_1, BUYER_2, BUYER_3))
                        .setSeller(mSeller)
                        .setDecisionLogicUri(
                                Uri.parse(
                                        String.format(
                                                "%s://%s/%s/?%s=%s",
                                                AD_SELECTION_PREBUILT_SCHEMA,
                                                AD_SELECTION_USE_CASE,
                                                AD_SELECTION_HIGHEST_BID_WINS,
                                                paramKey,
                                                paramValue)))
                        .setTrustedScoringSignalsUri(
                                mMockWebServerRule.uriForPath(SELLER_TRUSTED_SIGNAL_URI_PATH))
                        .build();

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, adSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        assertCallbackIsSuccessful(resultsCallback);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(resultSelectionId));
        assertEquals(
                AD_URI_PREFIX + BUYER_2 + "/ad3",
                resultsCallback.mAdSelectionResponse.getRenderUri().toString());
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(isA(RunAdBiddingProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(isA(RunAdScoringProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        geq((int) BINDER_ELAPSED_TIME_MS));
    }

    @Test
    public void testRunAdSelectionSuccess_prebuiltFeatureDisabled_failure() throws Exception {
        Flags prebuiltDisabledFlags =
                new AdSelectionE2ETestFlags() {
                    @Override
                    public boolean getFledgeAdSelectionPrebuiltUriEnabled() {
                        return false;
                    }
                };

        AdSelectionServiceImpl adSelectionService =
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
                        mSpyContext,
                        mAdServicesLoggerMock,
                        prebuiltDisabledFlags,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mAdSelectionServiceFilter,
                        mAdFilteringFeatureFactory,
                        mConsentManagerMock,
                        mMultiCloudSupportStrategy,
                        mAdSelectionDebugReportDao,
                        mAdIdFetcher,
                        mUnusedKAnonSignJoinFactory,
                        false,
                        mRetryStrategyFactory);

        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());

        mMockWebServerRule.startMockWebServer(mDispatcher);
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        String paramKey = "reportingUrl";
        String paramValue = "https://www.test.com/reporting/seller";
        AdSelectionConfig adSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setCustomAudienceBuyers(ImmutableList.of(BUYER_1, BUYER_2, BUYER_3))
                        .setSeller(mSeller)
                        .setDecisionLogicUri(
                                Uri.parse(
                                        String.format(
                                                "%s://%s/%s/?%s=%s",
                                                AD_SELECTION_PREBUILT_SCHEMA,
                                                AD_SELECTION_USE_CASE,
                                                AD_SELECTION_HIGHEST_BID_WINS,
                                                paramKey,
                                                paramValue)))
                        .setTrustedScoringSignalsUri(
                                mMockWebServerRule.uriForPath(SELLER_TRUSTED_SIGNAL_URI_PATH))
                        .build();

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(adSelectionService, adSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        assertCallbackFailed(resultsCallback);
        assertTrue(
                resultsCallback
                        .mFledgeErrorResponse
                        .getErrorMessage()
                        .contains(PREBUILT_FEATURE_IS_DISABLED));
        assertEquals(STATUS_INVALID_ARGUMENT, resultsCallback.mFledgeErrorResponse.getStatusCode());
    }

    @Test
    public void testRunAdSelectionSuccess_flagToPreV3_preV3BiddingLogic() throws Exception {
        mFlags = new AdSelectionE2ETestFlags(2, false);
        extendedMockito.mockGetFlags(mFlags);
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
                        mSpyContext,
                        mAdServicesLoggerMock,
                        mFlags,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mAdSelectionServiceFilter,
                        mAdFilteringFeatureFactory,
                        mConsentManagerMock,
                        mMultiCloudSupportStrategy,
                        mAdSelectionDebugReportDao,
                        mAdIdFetcher,
                        mUnusedKAnonSignJoinFactory,
                        false,
                        mRetryStrategyFactory);
        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(3);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());

        mMockWebServerRule.startMockWebServer(DISPATCHER_V2_ONLY_BIDDING_LOGIC);
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, mAdSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        assertCallbackIsSuccessful(resultsCallback);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(resultSelectionId));
        assertEquals(
                AD_URI_PREFIX + BUYER_2 + "/ad3",
                resultsCallback.mAdSelectionResponse.getRenderUri().toString());
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(isA(RunAdBiddingProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(isA(RunAdScoringProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        geq((int) BINDER_ELAPSED_TIME_MS));
    }

    @Test
    public void testRunAdSelectionSuccess_v3BiddingLogic() throws Exception {
        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(3);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());
        mMockWebServerRule.startMockWebServer(DISPATCHER_V3_BIDDING_LOGIC);
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, mAdSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        assertCallbackIsSuccessful(resultsCallback);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(resultSelectionId));
        assertEquals(
                AD_URI_PREFIX + BUYER_2 + "/ad3",
                resultsCallback.mAdSelectionResponse.getRenderUri().toString());
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(isA(RunAdBiddingProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(isA(RunAdScoringProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        geq((int) BINDER_ELAPSED_TIME_MS));
    }

    @Test
    public void testRunAdSelectionSuccess_v3BiddingLogicWithAdCostCpcBillingEnabled()
            throws Exception {
        AdSelectionE2ETestFlags flagsWithCPCEnabled =
                new AdSelectionE2ETestFlags() {
                    @Override
                    public boolean getFledgeCpcBillingEnabled() {
                        return true;
                    }
                };
        doReturn(flagsWithCPCEnabled).when(FlagsFactory::getFlags);

        // Re init adSelection service with new flags
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
                        mSpyContext,
                        mAdServicesLoggerMock,
                        flagsWithCPCEnabled,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mAdSelectionServiceFilter,
                        mAdFilteringFeatureFactory,
                        mConsentManagerMock,
                        mMultiCloudSupportStrategy,
                        mAdSelectionDebugReportDao,
                        mAdIdFetcher,
                        mUnusedKAnonSignJoinFactory,
                        false,
                        mRetryStrategyFactory);
        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(3);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());
        mMockWebServerRule.startMockWebServer(DISPATCHER_V3_BIDDING_LOGIC);
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudienceWithAdCost(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(
                                BUYER_BIDDING_LOGIC_URI_PATH_AD_COST + BUYER_1),
                        bidsForBuyer1,
                        AD_COST_1.getAdCost());
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudienceWithAdCost(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(
                                BUYER_BIDDING_LOGIC_URI_PATH_AD_COST + BUYER_2),
                        bidsForBuyer2,
                        AD_COST_2.getAdCost());

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, mAdSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        assertCallbackIsSuccessful(resultsCallback);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(resultSelectionId));

        // Assert adCost was propagated through
        String expectedBuyerContextualSignals =
                BuyerContextualSignals.builder().setAdCost(AD_COST_2).build().toString();
        assertEquals(
                expectedBuyerContextualSignals,
                mAdSelectionEntryDaoSpy
                        .getAdSelectionEntityById(resultSelectionId)
                        .getBuyerContextualSignals());

        assertEquals(
                AD_URI_PREFIX + BUYER_2 + "/ad3",
                resultsCallback.mAdSelectionResponse.getRenderUri().toString());
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(isA(RunAdBiddingProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(isA(RunAdScoringProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        geq((int) BINDER_ELAPSED_TIME_MS));
    }

    @Test
    public void testRunAdSelectionSuccess_v3BiddingLogicWithDataVersionHeaderFlagEnabled()
            throws Exception {
        AdSelectionE2ETestFlags flagsWithDataVersionHeaderEnabled =
                new AdSelectionE2ETestFlags() {
                    @Override
                    public boolean getFledgeDataVersionHeaderEnabled() {
                        return true;
                    }
                };
        doReturn(flagsWithDataVersionHeaderEnabled).when(FlagsFactory::getFlags);

        // Re init adSelection service with new flags
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
                        mSpyContext,
                        mAdServicesLoggerMock,
                        flagsWithDataVersionHeaderEnabled,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mAdSelectionServiceFilter,
                        mAdFilteringFeatureFactory,
                        mConsentManagerMock,
                        mMultiCloudSupportStrategy,
                        mAdSelectionDebugReportDao,
                        mAdIdFetcher,
                        mUnusedKAnonSignJoinFactory,
                        false,
                        mRetryStrategyFactory);

        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(3);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());

        mMockWebServerRule.startMockWebServer(DISPATCHER_V3_BIDDING_LOGIC);
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1,
                        BUYER_TRUSTED_SIGNAL_URI_PATH_WITH_DATA_VERSION + BUYER_1);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2,
                        BUYER_TRUSTED_SIGNAL_URI_PATH_WITH_DATA_VERSION + BUYER_2);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        // Instantiate new adSelectionConfig with data version header in trusted seller signals
        AdSelectionConfig adSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setCustomAudienceBuyers(ImmutableList.of(BUYER_1, BUYER_2, BUYER_3))
                        .setSeller(mSeller)
                        .setDecisionLogicUri(
                                mMockWebServerRule.uriForPath(SELLER_DECISION_LOGIC_URI_PATH))
                        .setTrustedScoringSignalsUri(
                                mMockWebServerRule.uriForPath(
                                        SELLER_TRUSTED_SIGNAL_URI_PATH_WITH_DATA_VERSION))
                        .build();

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, adSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        assertCallbackIsSuccessful(resultsCallback);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(resultSelectionId));

        // Assert data version was propagated through to buyer
        String expectedBuyerContextualSignals =
                BuyerContextualSignals.builder().setDataVersion(DATA_VERSION_2).build().toString();
        assertEquals(
                expectedBuyerContextualSignals,
                mAdSelectionEntryDaoSpy
                        .getAdSelectionEntityById(resultSelectionId)
                        .getBuyerContextualSignals());

        // Assert data version was propagated through to seller
        String expectedSellerContextualSignals =
                SellerContextualSignals.builder().setDataVersion(DATA_VERSION_1).build().toString();
        assertEquals(
                expectedSellerContextualSignals,
                mAdSelectionEntryDaoSpy
                        .getAdSelectionEntityById(resultSelectionId)
                        .getSellerContextualSignals());

        assertEquals(
                AD_URI_PREFIX + BUYER_2 + "/ad3",
                resultsCallback.mAdSelectionResponse.getRenderUri().toString());
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(isA(RunAdBiddingProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(isA(RunAdScoringProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        geq((int) BINDER_ELAPSED_TIME_MS));
    }

    @Test
    public void testRunAdSelectionSuccess_v3BiddingLogicWithDataVersionHeaderFlagDisabled()
            throws Exception {
        AdSelectionE2ETestFlags flagsWithDataVersionHeaderEnabled =
                new AdSelectionE2ETestFlags() {
                    @Override
                    public boolean getFledgeDataVersionHeaderEnabled() {
                        return false;
                    }
                };
        doReturn(flagsWithDataVersionHeaderEnabled).when(FlagsFactory::getFlags);

        // Re init adSelection service with new flags
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
                        mSpyContext,
                        mAdServicesLoggerMock,
                        flagsWithDataVersionHeaderEnabled,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mAdSelectionServiceFilter,
                        mAdFilteringFeatureFactory,
                        mConsentManagerMock,
                        mMultiCloudSupportStrategy,
                        mAdSelectionDebugReportDao,
                        mAdIdFetcher,
                        mUnusedKAnonSignJoinFactory,
                        false,
                        mRetryStrategyFactory);

        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(3);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());

        mMockWebServerRule.startMockWebServer(DISPATCHER_V3_BIDDING_LOGIC);
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1,
                        BUYER_TRUSTED_SIGNAL_URI_PATH_WITH_DATA_VERSION + BUYER_1);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2,
                        BUYER_TRUSTED_SIGNAL_URI_PATH_WITH_DATA_VERSION + BUYER_2);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        // Instantiate new adSelectionConfig with data version header in trusted seller signals
        AdSelectionConfig adSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setCustomAudienceBuyers(ImmutableList.of(BUYER_1, BUYER_2, BUYER_3))
                        .setSeller(mSeller)
                        .setDecisionLogicUri(
                                mMockWebServerRule.uriForPath(SELLER_DECISION_LOGIC_URI_PATH))
                        .setTrustedScoringSignalsUri(
                                mMockWebServerRule.uriForPath(
                                        SELLER_TRUSTED_SIGNAL_URI_PATH_WITH_DATA_VERSION))
                        .build();

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, adSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        assertCallbackIsSuccessful(resultsCallback);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(resultSelectionId));

        // Assert data version was not propagated through to buyer
        assertEquals(
                AdSelectionSignals.EMPTY.toString(),
                mAdSelectionEntryDaoSpy
                        .getAdSelectionEntityById(resultSelectionId)
                        .getBuyerContextualSignals());

        // Assert data version was not propagated through to seller
        assertEquals(
                AdSelectionSignals.EMPTY.toString(),
                mAdSelectionEntryDaoSpy
                        .getAdSelectionEntityById(resultSelectionId)
                        .getSellerContextualSignals());

        assertEquals(
                AD_URI_PREFIX + BUYER_2 + "/ad3",
                resultsCallback.mAdSelectionResponse.getRenderUri().toString());
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(isA(RunAdBiddingProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(isA(RunAdScoringProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        geq((int) BINDER_ELAPSED_TIME_MS));
    }

    @Test
    public void testRunAdSelectionSuccess_v3BiddingLogicWinnerWithoutBuyerDataVersionHeader()
            throws Exception {
        AdSelectionE2ETestFlags flagsWithDataVersionHeaderEnabled =
                new AdSelectionE2ETestFlags() {
                    @Override
                    public boolean getFledgeDataVersionHeaderEnabled() {
                        return true;
                    }
                };
        doReturn(flagsWithDataVersionHeaderEnabled).when(FlagsFactory::getFlags);

        // Re init adSelection service with new flags
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
                        mSpyContext,
                        mAdServicesLoggerMock,
                        flagsWithDataVersionHeaderEnabled,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mAdSelectionServiceFilter,
                        mAdFilteringFeatureFactory,
                        mConsentManagerMock,
                        mMultiCloudSupportStrategy,
                        mAdSelectionDebugReportDao,
                        mAdIdFetcher,
                        mUnusedKAnonSignJoinFactory,
                        false,
                        mRetryStrategyFactory);
        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(3);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());

        mMockWebServerRule.startMockWebServer(DISPATCHER_V3_BIDDING_LOGIC);
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1,
                        BUYER_TRUSTED_SIGNAL_URI_PATH_WITH_DATA_VERSION + BUYER_1);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2,
                        BUYER_TRUSTED_SIGNAL_URI_PATH + BUYER_2);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, mAdSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        assertCallbackIsSuccessful(resultsCallback);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(resultSelectionId));

        // Assert data version was not propagated through since buyer 2 wins
        String expectedBuyerContextualSignals = BuyerContextualSignals.builder().build().toString();
        assertEquals(
                expectedBuyerContextualSignals,
                mAdSelectionEntryDaoSpy
                        .getAdSelectionEntityById(resultSelectionId)
                        .getBuyerContextualSignals());

        assertEquals(
                AD_URI_PREFIX + BUYER_2 + "/ad3",
                resultsCallback.mAdSelectionResponse.getRenderUri().toString());
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(isA(RunAdBiddingProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(isA(RunAdScoringProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        geq((int) BINDER_ELAPSED_TIME_MS));
    }

    @Test
    public void testRunAdSelectionSuccess_v3BiddingLogicWithAdCostCpcBillingDisabled()
            throws Exception {
        AdSelectionE2ETestFlags flagsWithCPCDisabled =
                new AdSelectionE2ETestFlags() {
                    @Override
                    public boolean getFledgeCpcBillingEnabled() {
                        return false;
                    }
                };
        doReturn(flagsWithCPCDisabled).when(FlagsFactory::getFlags);

        // Re init adSelection service with new flags
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
                        mSpyContext,
                        mAdServicesLoggerMock,
                        flagsWithCPCDisabled,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mAdSelectionServiceFilter,
                        mAdFilteringFeatureFactory,
                        mConsentManagerMock,
                        mMultiCloudSupportStrategy,
                        mAdSelectionDebugReportDao,
                        mAdIdFetcher,
                        mUnusedKAnonSignJoinFactory,
                        false,
                        mRetryStrategyFactory);
        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(3);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());
        mMockWebServerRule.startMockWebServer(DISPATCHER_V3_BIDDING_LOGIC);
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudienceWithAdCost(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(
                                BUYER_BIDDING_LOGIC_URI_PATH_AD_COST + BUYER_1),
                        bidsForBuyer1,
                        AD_COST_1.getAdCost());
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudienceWithAdCost(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(
                                BUYER_BIDDING_LOGIC_URI_PATH_AD_COST + BUYER_2),
                        bidsForBuyer2,
                        AD_COST_2.getAdCost());

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, mAdSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        assertCallbackIsSuccessful(resultsCallback);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(resultSelectionId));

        // Assert adCost was not propagated through due to flag disabled
        String expectedBuyerContextualSignals = "{}";
        assertEquals(
                expectedBuyerContextualSignals,
                mAdSelectionEntryDaoSpy
                        .getAdSelectionEntityById(resultSelectionId)
                        .getBuyerContextualSignals());

        assertEquals(
                AD_URI_PREFIX + BUYER_2 + "/ad3",
                resultsCallback.mAdSelectionResponse.getRenderUri().toString());
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(isA(RunAdBiddingProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(isA(RunAdScoringProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        geq((int) BINDER_ELAPSED_TIME_MS));
    }

    @Test
    public void testRunAdSelectionSuccess_preV3BiddingLogicWithV3Header_scriptFail()
            throws Exception {
        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(2);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());
        mMockWebServerRule.startMockWebServer(DISPATCHER_V3_BIDDING_LOGIC_HEADER_WITH_V2_LOGIC);
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, mAdSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        assertCallbackFailed(resultsCallback);
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(isA(RunAdBiddingProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_INTERNAL_ERROR),
                        geq((int) BINDER_ELAPSED_TIME_MS));
    }

    @Test
    public void testRunAdSelection_getTooHighHeader_failWithError() throws Exception {
        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(2);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());
        mMockWebServerRule.startMockWebServer(DISPATCHER_TOO_HIGH_BIDDING_LOGIC_JS_VERSION);
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, mAdSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        assertCallbackFailed(resultsCallback);
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(isA(RunAdBiddingProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_INTERNAL_ERROR),
                        geq((int) BINDER_ELAPSED_TIME_MS));
    }

    @Test
    public void testRunAdSelectionWithOverride_getTooHighHeader_failWithError() throws Exception {
        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(2);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());
        mMockWebServerRule.startMockWebServer(DISPATCHER_TOO_HIGH_BIDDING_LOGIC_JS_VERSION);
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        DBCustomAudienceOverride dbCustomAudienceOverride1 =
                DBCustomAudienceOverride.builder()
                        .setOwner(dBCustomAudienceForBuyer1.getOwner())
                        .setBuyer(dBCustomAudienceForBuyer1.getBuyer())
                        .setName(dBCustomAudienceForBuyer1.getName())
                        .setAppPackageName(MY_APP_PACKAGE_NAME)
                        .setBiddingLogicJS(READ_BID_FROM_AD_METADATA_JS)
                        .setBiddingLogicJsVersion(
                                JsVersionRegister.BUYER_BIDDING_LOGIC_VERSION_VERSION_3 + 1)
                        .setTrustedBiddingData(
                                new JSONObject(TRUSTED_BIDDING_SIGNALS_SERVER_DATA).toString())
                        .build();
        DBCustomAudienceOverride dbCustomAudienceOverride2 =
                DBCustomAudienceOverride.builder()
                        .setOwner(dBCustomAudienceForBuyer2.getOwner())
                        .setBuyer(dBCustomAudienceForBuyer2.getBuyer())
                        .setName(dBCustomAudienceForBuyer2.getName())
                        .setAppPackageName(MY_APP_PACKAGE_NAME)
                        .setBiddingLogicJS(READ_BID_FROM_AD_METADATA_JS)
                        .setBiddingLogicJsVersion(
                                JsVersionRegister.BUYER_BIDDING_LOGIC_VERSION_VERSION_3 + 1)
                        .setTrustedBiddingData(
                                new JSONObject(TRUSTED_BIDDING_SIGNALS_SERVER_DATA).toString())
                        .build();
        mCustomAudienceDao.persistCustomAudienceOverride(dbCustomAudienceOverride1);
        mCustomAudienceDao.persistCustomAudienceOverride(dbCustomAudienceOverride2);

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, mAdSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        assertCallbackFailed(resultsCallback);
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(isA(RunAdBiddingProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_INTERNAL_ERROR),
                        geq((int) BINDER_ELAPSED_TIME_MS));
    }

    @Test
    public void testRunAdSelectionContextualAds_Success() throws Exception {
        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(3);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());

        AdSelectionConfig adSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigWithSignedContextualAdsBuilder()
                        .setCustomAudienceBuyers(ImmutableList.of(BUYER_1, BUYER_2, BUYER_3))
                        .setSeller(mSeller)
                        .setDecisionLogicUri(
                                mMockWebServerRule.uriForPath(SELLER_DECISION_LOGIC_URI_PATH))
                        .setTrustedScoringSignalsUri(
                                mMockWebServerRule.uriForPath(SELLER_TRUSTED_SIGNAL_URI_PATH))
                        .setPerBuyerSignedContextualAds(createContextualAds())
                        .build();

        mMockWebServerRule.startMockWebServer(mDispatcher);
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, adSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        assertCallbackIsSuccessful(resultsCallback);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(resultSelectionId));

        // The contextual Ad with maximum bid should have won
        assertEquals(
                AdDataFixture.getValidRenderUriByBuyer(
                                AdTechIdentifier.fromString(
                                        mMockWebServerRule
                                                .uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2)
                                                .getHost()),
                                500)
                        .toString(),
                resultsCallback.mAdSelectionResponse.getRenderUri().toString());
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(isA(RunAdBiddingProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(isA(RunAdScoringProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        geq((int) BINDER_ELAPSED_TIME_MS));
    }

    @FlakyTest(bugId = 304764127)
    @Test
    public void testRunAdSelectionContextualAds_UseOverrides_Success() throws Exception {
        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(3);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());

        AdSelectionConfig adSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigWithSignedContextualAdsBuilder()
                        .setCustomAudienceBuyers(ImmutableList.of(BUYER_1, BUYER_2, BUYER_3))
                        .setSeller(mSeller)
                        .setDecisionLogicUri(
                                mMockWebServerRule.uriForPath(SELLER_DECISION_LOGIC_URI_PATH))
                        .setTrustedScoringSignalsUri(
                                mMockWebServerRule.uriForPath(SELLER_TRUSTED_SIGNAL_URI_PATH))
                        .setPerBuyerSignedContextualAds(createContextualAds())
                        .build();

        MockWebServer server = mMockWebServerRule.startMockWebServer(mDispatcher);
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        final String fakeDecisionLogicForBuyer = "\"reportWin() { completely fake }\"";
        // Set dev override for  ad selection
        DBAdSelectionOverride adSelectionOverride =
                DBAdSelectionOverride.builder()
                        .setAdSelectionConfigId(
                                AdSelectionDevOverridesHelper.calculateAdSelectionConfigId(
                                        mAdSelectionConfig))
                        .setAppPackageName(MY_APP_PACKAGE_NAME)
                        .setDecisionLogicJS(USE_BID_AS_SCORE_JS)
                        .setTrustedScoringSignals(TRUSTED_SCORING_SIGNALS.toString())
                        .build();
        mAdSelectionEntryDaoSpy.persistAdSelectionOverride(adSelectionOverride);
        DBBuyerDecisionOverride buyerDecisionOverride =
                DBBuyerDecisionOverride.builder()
                        .setAdSelectionConfigId(
                                AdSelectionDevOverridesHelper.calculateAdSelectionConfigId(
                                        mAdSelectionConfig))
                        .setAppPackageName(MY_APP_PACKAGE_NAME)
                        .setDecisionLogic(fakeDecisionLogicForBuyer)
                        .setBuyer(BUYER_2)
                        .build();
        mAdSelectionEntryDaoSpy.persistPerBuyerDecisionLogicOverride(
                ImmutableList.of(buyerDecisionOverride));

        // Set dev override for custom audience
        DBCustomAudienceOverride dbCustomAudienceOverride =
                DBCustomAudienceOverride.builder()
                        .setOwner(dBCustomAudienceForBuyer2.getOwner())
                        .setBuyer(dBCustomAudienceForBuyer2.getBuyer())
                        .setName(dBCustomAudienceForBuyer2.getName())
                        .setAppPackageName(MY_APP_PACKAGE_NAME)
                        .setBiddingLogicJS(READ_BID_FROM_AD_METADATA_JS)
                        .setTrustedBiddingData(
                                new JSONObject(TRUSTED_BIDDING_SIGNALS_SERVER_DATA).toString())
                        .build();
        mCustomAudienceDao.persistCustomAudienceOverride(dbCustomAudienceOverride);

        when(mDevContextFilter.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(MY_APP_PACKAGE_NAME)
                                .build());

        // Creating new instance of service with new DevContextFilter
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
                        mSpyContext,
                        mAdServicesLoggerMock,
                        mFlags,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mAdSelectionServiceFilter,
                        mAdFilteringFeatureFactory,
                        mConsentManagerMock,
                        mMultiCloudSupportStrategy,
                        mAdSelectionDebugReportDao,
                        mAdIdFetcher,
                        mUnusedKAnonSignJoinFactory,
                        false,
                        mRetryStrategyFactory);

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, adSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        assertCallbackIsSuccessful(resultsCallback);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(resultSelectionId));

        // The contextual Ad with maximum bid should have won
        assertEquals(
                AdDataFixture.getValidRenderUriByBuyer(
                                AdTechIdentifier.fromString(
                                        mMockWebServerRule
                                                .uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2)
                                                .getHost()),
                                500)
                        .toString(),
                resultsCallback.mAdSelectionResponse.getRenderUri().toString());
        // No calls to fetch Contextual decision logic should have been made to the server, as
        // overrides are set
        mMockWebServerRule.verifyMockServerRequests(
                server,
                2,
                ImmutableList.of(
                        BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1, BUYER_TRUSTED_SIGNAL_URI_PATH),
                REQUEST_PREFIX_MATCHER);
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(isA(RunAdBiddingProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(isA(RunAdScoringProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        geq((int) BINDER_ELAPSED_TIME_MS));
    }

    @Test
    public void testRunAdSelectionContextualAds_Disabled_Success() throws Exception {
        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(3);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());

        AdSelectionConfig adSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigWithSignedContextualAdsBuilder()
                        .setCustomAudienceBuyers(ImmutableList.of(BUYER_1, BUYER_2, BUYER_3))
                        .setSeller(mSeller)
                        .setDecisionLogicUri(
                                mMockWebServerRule.uriForPath(SELLER_DECISION_LOGIC_URI_PATH))
                        .setTrustedScoringSignalsUri(
                                mMockWebServerRule.uriForPath(SELLER_TRUSTED_SIGNAL_URI_PATH))
                        .setPerBuyerSignedContextualAds(createContextualAds())
                        .build();

        mMockWebServerRule.startMockWebServer(mDispatcher);
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        Flags flagsWithContextualAdsDisabled =
                new AdSelectionE2ETestFlags() {
                    @Override
                    public boolean getFledgeAdSelectionContextualAdsEnabled() {
                        return false;
                    }
                };

        AdSelectionServiceImpl adSelectionService =
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
                        mSpyContext,
                        mAdServicesLoggerMock,
                        flagsWithContextualAdsDisabled,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mAdSelectionServiceFilter,
                        mAdFilteringFeatureFactory,
                        mConsentManagerMock,
                        mMultiCloudSupportStrategy,
                        mAdSelectionDebugReportDao,
                        mAdIdFetcher,
                        mUnusedKAnonSignJoinFactory,
                        false,
                        mRetryStrategyFactory);

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(adSelectionService, adSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        assertCallbackIsSuccessful(resultsCallback);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(resultSelectionId));

        // The remarketing ad with maximum bid should have won, as contextual ads are excluded
        assertEquals(
                AD_URI_PREFIX + BUYER_2 + "/ad3",
                resultsCallback.mAdSelectionResponse.getRenderUri().toString());
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(isA(RunAdBiddingProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(isA(RunAdScoringProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        geq((int) BINDER_ELAPSED_TIME_MS));
    }

    @Test
    public void testRunAdSelectionOnlyContextualAds_NoBuyers_Success() throws Exception {
        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(2);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());

        AdSelectionConfig adSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigWithSignedContextualAdsBuilder()
                        .setCustomAudienceBuyers(ImmutableList.of())
                        .setSeller(mSeller)
                        .setDecisionLogicUri(
                                mMockWebServerRule.uriForPath(SELLER_DECISION_LOGIC_URI_PATH))
                        .setTrustedScoringSignalsUri(
                                mMockWebServerRule.uriForPath(SELLER_TRUSTED_SIGNAL_URI_PATH))
                        .setPerBuyerSignedContextualAds(createContextualAds())
                        .build();

        mMockWebServerRule.startMockWebServer(mDispatcher);

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, adSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        assertCallbackIsSuccessful(resultsCallback);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(resultSelectionId));

        // The contextual Ad with maximum bid should have won
        assertEquals(
                AdDataFixture.getValidRenderUriByBuyer(
                                AdTechIdentifier.fromString(
                                        mMockWebServerRule
                                                .uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2)
                                                .getHost()),
                                500)
                        .toString(),
                resultsCallback.mAdSelectionResponse.getRenderUri().toString());
        verify(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(isA(RunAdScoringProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        geq((int) BINDER_ELAPSED_TIME_MS));
    }

    @Test
    public void testRunAdSelectionOnlyContextualAds_unauthenticatedContextualAdsRemoved_Success()
            throws Exception {
        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());

        AdSelectionConfig adSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigWithSignedContextualAdsBuilder()
                        .setCustomAudienceBuyers(ImmutableList.of())
                        .setSeller(mSeller)
                        .setDecisionLogicUri(
                                mMockWebServerRule.uriForPath(SELLER_DECISION_LOGIC_URI_PATH))
                        .setTrustedScoringSignalsUri(
                                mMockWebServerRule.uriForPath(SELLER_TRUSTED_SIGNAL_URI_PATH))
                        .setPerBuyerSignedContextualAds(createUnauthenticatedContextualAds())
                        .build();

        mMockWebServerRule.startMockWebServer(mDispatcher);

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, adSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        assertCallbackFailed(resultsCallback);
        assertTrue(
                resultsCallback
                        .mFledgeErrorResponse
                        .getErrorMessage()
                        .contains(ERROR_NO_BUYERS_OR_CONTEXTUAL_ADS_AVAILABLE));
        assertEquals(STATUS_INVALID_ARGUMENT, resultsCallback.mFledgeErrorResponse.getStatusCode());
    }

    @Test
    public void testRunAdSelectionOnlyContextualAds_NoCAs_Success() throws Exception {
        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(2);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());

        AdSelectionConfig adSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigWithSignedContextualAdsBuilder()
                        .setCustomAudienceBuyers(ImmutableList.of(BUYER_1, BUYER_2, BUYER_3))
                        .setSeller(mSeller)
                        .setDecisionLogicUri(
                                mMockWebServerRule.uriForPath(SELLER_DECISION_LOGIC_URI_PATH))
                        .setTrustedScoringSignalsUri(
                                mMockWebServerRule.uriForPath(SELLER_TRUSTED_SIGNAL_URI_PATH))
                        .setPerBuyerSignedContextualAds(createContextualAds())
                        .build();

        mMockWebServerRule.startMockWebServer(mDispatcher);

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, adSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        assertCallbackIsSuccessful(resultsCallback);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(resultSelectionId));

        // The contextual Ad with maximum bid should have won
        assertEquals(
                AdDataFixture.getValidRenderUriByBuyer(
                                AdTechIdentifier.fromString(
                                        mMockWebServerRule
                                                .uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2)
                                                .getHost()),
                                500)
                        .toString(),
                resultsCallback.mAdSelectionResponse.getRenderUri().toString());
        verify(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(isA(RunAdScoringProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        geq((int) BINDER_ELAPSED_TIME_MS));

        ReportImpressionTestCallback reportingCallback =
                invokeReporting(
                        resultsCallback.mAdSelectionResponse.getAdSelectionId(),
                        mAdSelectionService,
                        adSelectionConfig,
                        CALLER_PACKAGE_NAME);
        assertCallbackIsSuccessful(reportingCallback);
    }

    @Test
    @FlakyTest(bugId = 304764127)
    public void testRunAdSelectionOnlyContextualAds_NoCAsNoNetworkCall_Success() throws Exception {
        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(2);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());

        MockWebServer server = mMockWebServerRule.startMockWebServer(mDispatcher);

        String sellerReportingPath = "/seller/report";
        String paramKey = "reportingUrl";
        String paramValue = mMockWebServerRule.uriForPath(sellerReportingPath).toString();
        Uri prebuiltUri =
                Uri.parse(
                        String.format(
                                "%s://%s/%s/?%s=%s",
                                AD_SELECTION_PREBUILT_SCHEMA,
                                AD_SELECTION_USE_CASE,
                                AD_SELECTION_HIGHEST_BID_WINS,
                                paramKey,
                                paramValue));
        AdSelectionConfig adSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigWithSignedContextualAdsBuilder()
                        .setCustomAudienceBuyers(Collections.emptyList())
                        .setSeller(mSeller)
                        .setDecisionLogicUri(prebuiltUri)
                        .setPerBuyerSignedContextualAds(createContextualAds())
                        .setTrustedScoringSignalsUri(Uri.EMPTY)
                        .build();

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, adSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();

        assertCallbackIsSuccessful(resultsCallback);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(resultSelectionId));

        // The contextual Ad with maximum bid should have won
        assertEquals(
                AdDataFixture.getValidRenderUriByBuyer(
                                AdTechIdentifier.fromString(
                                        mMockWebServerRule
                                                .uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2)
                                                .getHost()),
                                500)
                        .toString(),
                resultsCallback.mAdSelectionResponse.getRenderUri().toString());
        verify(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(isA(RunAdScoringProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        geq((int) BINDER_ELAPSED_TIME_MS));
        mMockWebServerRule.verifyMockServerRequests(
                server, 0, Collections.emptyList(), String::equals);

        ReportImpressionTestCallback reportingCallback =
                invokeReporting(
                        resultsCallback.mAdSelectionResponse.getAdSelectionId(),
                        mAdSelectionService,
                        adSelectionConfig,
                        CALLER_PACKAGE_NAME);
        assertCallbackIsSuccessful(reportingCallback);
    }

    @Test
    @FlakyTest(bugId = 315521295)
    public void testRunAdSelectionNoContextualAds_NoCAs_Failure() throws Exception {
        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(2);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());

        AdSelectionConfig adSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigWithSignedContextualAdsBuilder()
                        .setCustomAudienceBuyers(ImmutableList.of(BUYER_1, BUYER_2, BUYER_3))
                        .setSeller(mSeller)
                        .setDecisionLogicUri(
                                mMockWebServerRule.uriForPath(SELLER_DECISION_LOGIC_URI_PATH))
                        .setTrustedScoringSignalsUri(
                                mMockWebServerRule.uriForPath(SELLER_TRUSTED_SIGNAL_URI_PATH))
                        .setPerBuyerSignedContextualAds(createContextualAds())
                        .build();

        mMockWebServerRule.startMockWebServer(mDispatcher);

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, adSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        assertCallbackIsSuccessful(resultsCallback);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(resultSelectionId));

        // The contextual Ad with maximum bid should have won
        assertEquals(
                AdDataFixture.getValidRenderUriByBuyer(
                                AdTechIdentifier.fromString(
                                        mMockWebServerRule
                                                .uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2)
                                                .getHost()),
                                500)
                        .toString(),
                resultsCallback.mAdSelectionResponse.getRenderUri().toString());
        verify(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(isA(RunAdScoringProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        geq((int) BINDER_ELAPSED_TIME_MS));
    }

    @Test
    public void testRunAdSelectionWithRevokedUserConsentSuccess() throws Exception {
        doThrow(new FilterException(new ConsentManager.RevokedConsentException()))
                .when(mAdSelectionServiceFilter)
                .filterRequest(
                        mSeller,
                        CALLER_PACKAGE_NAME,
                        true,
                        true,
                        CALLER_UID,
                        AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS,
                        Throttler.ApiKey.FLEDGE_API_SELECT_ADS,
                        DevContext.createForDevOptionsDisabled());

        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());

        mMockWebServerRule.startMockWebServer(mDispatcher);
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, mAdSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();

        assertCallbackIsSuccessful(resultsCallback);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertFalse(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(resultSelectionId));
        assertEquals(
                Uri.EMPTY.toString(),
                resultsCallback.mAdSelectionResponse.getRenderUri().toString());
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));

        // Confirm a duplicate log entry does not exist.
        // AdSelectionServiceFilter ensures the failing assertion is logged internally.
        verify(mAdServicesLoggerMock, never())
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_USER_CONSENT_REVOKED),
                        geq((int) BINDER_ELAPSED_TIME_MS));
    }

    @Test
    public void testRunAdSelectionMultipleCAsSuccess_preV3BiddingLogic() throws Exception {
        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(3);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());

        mMockWebServerRule.startMockWebServer(mDispatcher);
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        List<AdTechIdentifier> participatingBuyers = new ArrayList<>();
        participatingBuyers.add(BUYER_1);
        participatingBuyers.add(BUYER_2);

        for (int i = 3; i <= 50; i++) {
            AdTechIdentifier buyerX = AdTechIdentifier.fromString(BUYER + i + ".com");
            DBCustomAudience dBCustomAudienceForBuyerX =
                    createDBCustomAudience(
                            buyerX,
                            mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                            bidsForBuyer1,
                            BUYER_TRUSTED_SIGNAL_URI_PATH);
            mCustomAudienceDao.insertOrOverwriteCustomAudience(
                    dBCustomAudienceForBuyerX,
                    CustomAudienceFixture.getValidDailyUpdateUriByBuyer(buyerX),
                    false);

            participatingBuyers.add(buyerX);
        }

        AdSelectionConfig adSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setCustomAudienceBuyers(participatingBuyers)
                        .setSeller(
                                AdTechIdentifier.fromString(
                                        mMockWebServerRule
                                                .uriForPath(SELLER_DECISION_LOGIC_URI_PATH)
                                                .getHost()))
                        .setDecisionLogicUri(
                                mMockWebServerRule.uriForPath(SELLER_DECISION_LOGIC_URI_PATH))
                        .setTrustedScoringSignalsUri(
                                mMockWebServerRule.uriForPath(SELLER_TRUSTED_SIGNAL_URI_PATH))
                        .build();

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, adSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        assertCallbackIsSuccessful(resultsCallback);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(resultSelectionId));
        assertEquals(
                AD_URI_PREFIX + BUYER_2 + "/ad3",
                resultsCallback.mAdSelectionResponse.getRenderUri().toString());
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(isA(RunAdBiddingProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(isA(RunAdScoringProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        geq((int) BINDER_ELAPSED_TIME_MS));
    }

    @Test
    public void testRunAdSelectionMultipleCAsSuccess_v3BiddingLogic() throws Exception {
        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(3);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());

        mMockWebServerRule.startMockWebServer(DISPATCHER_V3_BIDDING_LOGIC);
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        List<AdTechIdentifier> participatingBuyers = new ArrayList<>();
        participatingBuyers.add(BUYER_1);
        participatingBuyers.add(BUYER_2);

        for (int i = 3; i <= 50; i++) {
            AdTechIdentifier buyerX = AdTechIdentifier.fromString(BUYER + i + ".com");
            DBCustomAudience dBCustomAudienceForBuyerX =
                    createDBCustomAudience(
                            buyerX,
                            mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                            bidsForBuyer1,
                            BUYER_TRUSTED_SIGNAL_URI_PATH);
            mCustomAudienceDao.insertOrOverwriteCustomAudience(
                    dBCustomAudienceForBuyerX,
                    CustomAudienceFixture.getValidDailyUpdateUriByBuyer(buyerX),
                    false);

            participatingBuyers.add(buyerX);
        }

        AdSelectionConfig adSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setCustomAudienceBuyers(participatingBuyers)
                        .setSeller(
                                AdTechIdentifier.fromString(
                                        mMockWebServerRule
                                                .uriForPath(SELLER_DECISION_LOGIC_URI_PATH)
                                                .getHost()))
                        .setDecisionLogicUri(
                                mMockWebServerRule.uriForPath(SELLER_DECISION_LOGIC_URI_PATH))
                        .setTrustedScoringSignalsUri(
                                mMockWebServerRule.uriForPath(SELLER_TRUSTED_SIGNAL_URI_PATH))
                        .build();

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, adSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        assertCallbackIsSuccessful(resultsCallback);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(resultSelectionId));
        assertEquals(
                AD_URI_PREFIX + BUYER_2 + "/ad3",
                resultsCallback.mAdSelectionResponse.getRenderUri().toString());
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(isA(RunAdBiddingProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(isA(RunAdScoringProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        geq((int) BINDER_ELAPSED_TIME_MS));
    }

    @Test
    public void testRunAdSelectionMultipleCAsNoCachingSuccess_preV3BiddingLogic() throws Exception {
        MockWebServer server = mMockWebServerRule.startMockWebServer(mDispatcher);
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        List<AdTechIdentifier> participatingBuyers = new ArrayList<>();
        participatingBuyers.add(BUYER_1);
        participatingBuyers.add(BUYER_2);

        int extraCustomAudienceCount = 100;
        for (int i = 1; i <= extraCustomAudienceCount; i++) {
            DBCustomAudience dBCustomAudienceX =
                    createDBCustomAudience(
                            BUYER_1,
                            DEFAULT_CUSTOM_AUDIENCE_NAME_SUFFIX + i,
                            mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                            bidsForBuyer1,
                            CustomAudienceFixture.VALID_ACTIVATION_TIME,
                            CustomAudienceFixture.VALID_EXPIRATION_TIME,
                            CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI,
                            BUYER_TRUSTED_SIGNAL_URI_PATH);
            mCustomAudienceDao.insertOrOverwriteCustomAudience(
                    dBCustomAudienceX,
                    CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                    false);
        }

        AdSelectionConfig adSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setCustomAudienceBuyers(participatingBuyers)
                        .setSeller(
                                AdTechIdentifier.fromString(
                                        mMockWebServerRule
                                                .uriForPath(SELLER_DECISION_LOGIC_URI_PATH)
                                                .getHost()))
                        .setDecisionLogicUri(
                                mMockWebServerRule.uriForPath(SELLER_DECISION_LOGIC_URI_PATH))
                        .setTrustedScoringSignalsUri(
                                mMockWebServerRule.uriForPath(SELLER_TRUSTED_SIGNAL_URI_PATH))
                        .build();

        // Creating client which does not have caching
        AdServicesHttpsClient httpClientWithNoCaching =
                new AdServicesHttpsClient(
                        AdServicesExecutors.getBlockingExecutor(),
                        CacheProviderFactory.createNoOpCache());

        AdSelectionServiceImpl adSelectionServiceNoCache =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDaoSpy,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        httpClientWithNoCaching,
                        mDevContextFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mSpyContext,
                        mAdServicesLoggerMock,
                        mFlags,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mAdSelectionServiceFilter,
                        mAdFilteringFeatureFactory,
                        mConsentManagerMock,
                        mMultiCloudSupportStrategy,
                        mAdSelectionDebugReportDao,
                        mAdIdFetcher,
                        mUnusedKAnonSignJoinFactory,
                        false,
                        mRetryStrategyFactory);

        AdSelectionTestCallback resultsCallbackNoCache =
                invokeSelectAds(adSelectionServiceNoCache, adSelectionConfig, CALLER_PACKAGE_NAME);

        assertCallbackIsSuccessful(resultsCallbackNoCache);
        long resultSelectionId = resultsCallbackNoCache.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(resultSelectionId));
        assertEquals(
                AD_URI_PREFIX + BUYER_2 + "/ad3",
                resultsCallbackNoCache.mAdSelectionResponse.getRenderUri().toString());

        // 1 call per CA bidJs + 2 calls per buyer signals + 2 calls per seller scoreAdJS, signals
        int expectedNetworkCalls = (102 * 1) + 2 + 2;
        int serverCallsCountNoCaching = server.getRequestCount();
        Assert.assertEquals(
                "Server calls mismatch", expectedNetworkCalls, serverCallsCountNoCaching);
    }

    @Test
    @FlakyTest(bugId = 304764127)
    public void testRunAdSelectionMultipleCAsNoCachingSuccess_v3BiddingLogic() throws Exception {
        MockWebServer server = mMockWebServerRule.startMockWebServer(DISPATCHER_V3_BIDDING_LOGIC);
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        List<AdTechIdentifier> participatingBuyers = new ArrayList<>();
        participatingBuyers.add(BUYER_1);
        participatingBuyers.add(BUYER_2);

        int extraCustomAudienceCount = 100;
        for (int i = 1; i <= extraCustomAudienceCount; i++) {
            DBCustomAudience dBCustomAudienceX =
                    createDBCustomAudience(
                            BUYER_1,
                            DEFAULT_CUSTOM_AUDIENCE_NAME_SUFFIX + i,
                            mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                            bidsForBuyer1,
                            CustomAudienceFixture.VALID_ACTIVATION_TIME,
                            CustomAudienceFixture.VALID_EXPIRATION_TIME,
                            CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI,
                            BUYER_TRUSTED_SIGNAL_URI_PATH);
            mCustomAudienceDao.insertOrOverwriteCustomAudience(
                    dBCustomAudienceX,
                    CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                    false);
        }

        AdSelectionConfig adSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setCustomAudienceBuyers(participatingBuyers)
                        .setSeller(
                                AdTechIdentifier.fromString(
                                        mMockWebServerRule
                                                .uriForPath(SELLER_DECISION_LOGIC_URI_PATH)
                                                .getHost()))
                        .setDecisionLogicUri(
                                mMockWebServerRule.uriForPath(SELLER_DECISION_LOGIC_URI_PATH))
                        .setTrustedScoringSignalsUri(
                                mMockWebServerRule.uriForPath(SELLER_TRUSTED_SIGNAL_URI_PATH))
                        .build();

        // Creating client which does not have caching
        AdServicesHttpsClient httpClientWithNoCaching =
                new AdServicesHttpsClient(
                        AdServicesExecutors.getBlockingExecutor(),
                        CacheProviderFactory.createNoOpCache());

        AdSelectionServiceImpl adSelectionServiceNoCache =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDaoSpy,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        httpClientWithNoCaching,
                        mDevContextFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mSpyContext,
                        mAdServicesLoggerMock,
                        mFlags,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mAdSelectionServiceFilter,
                        mAdFilteringFeatureFactory,
                        mConsentManagerMock,
                        mMultiCloudSupportStrategy,
                        mAdSelectionDebugReportDao,
                        mAdIdFetcher,
                        mUnusedKAnonSignJoinFactory,
                        false,
                        mRetryStrategyFactory);

        AdSelectionTestCallback resultsCallbackNoCache =
                invokeSelectAds(adSelectionServiceNoCache, adSelectionConfig, CALLER_PACKAGE_NAME);

        assertCallbackIsSuccessful(resultsCallbackNoCache);
        long resultSelectionId = resultsCallbackNoCache.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(resultSelectionId));
        assertEquals(
                AD_URI_PREFIX + BUYER_2 + "/ad3",
                resultsCallbackNoCache.mAdSelectionResponse.getRenderUri().toString());

        // 1 call per CA bidJs + 2 calls per buyer signals + 2 calls per seller scoreAdJS, signals
        int expectedNetworkCalls = (102 * 1) + 2 + 2;
        int serverCallsCountNoCaching = server.getRequestCount();
        Assert.assertEquals(
                "Server calls mismatch", expectedNetworkCalls, serverCallsCountNoCaching);
    }

    @Test
    public void testRunAdSelectionMultipleCAsJSCachedSuccess_preV3BiddingLogic() throws Exception {
        MockWebServer server = mMockWebServerRule.startMockWebServer(mDispatcher);
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        List<AdTechIdentifier> participatingBuyers = new ArrayList<>();
        participatingBuyers.add(BUYER_1);
        participatingBuyers.add(BUYER_2);

        int extraCustomAudienceCount = 100;
        for (int i = 1; i <= extraCustomAudienceCount; i++) {
            DBCustomAudience dBCustomAudienceX =
                    createDBCustomAudience(
                            BUYER_1,
                            DEFAULT_CUSTOM_AUDIENCE_NAME_SUFFIX + i,
                            // All these CAs use the same uri, therefore JS should be cached
                            mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                            bidsForBuyer1,
                            CustomAudienceFixture.VALID_ACTIVATION_TIME,
                            CustomAudienceFixture.VALID_EXPIRATION_TIME,
                            CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI,
                            BUYER_TRUSTED_SIGNAL_URI_PATH);
            mCustomAudienceDao.insertOrOverwriteCustomAudience(
                    dBCustomAudienceX,
                    CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                    false);
        }

        AdSelectionConfig adSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setCustomAudienceBuyers(participatingBuyers)
                        .setSeller(
                                AdTechIdentifier.fromString(
                                        mMockWebServerRule
                                                .uriForPath(SELLER_DECISION_LOGIC_URI_PATH)
                                                .getHost()))
                        .setDecisionLogicUri(
                                mMockWebServerRule.uriForPath(SELLER_DECISION_LOGIC_URI_PATH))
                        .setTrustedScoringSignalsUri(
                                mMockWebServerRule.uriForPath(SELLER_TRUSTED_SIGNAL_URI_PATH))
                        .build();

        HttpCache cache = CacheProviderFactory.create(mSpyContext, mFlags);
        cache.addObserver(mCacheObserver);

        // Creating client which has caching
        AdServicesHttpsClient httpClientWithCaching =
                new AdServicesHttpsClient(AdServicesExecutors.getBlockingExecutor(), cache);

        AdSelectionServiceImpl adSelectionServiceWithCache =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDaoSpy,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        httpClientWithCaching,
                        mDevContextFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mSpyContext,
                        mAdServicesLoggerMock,
                        mFlags,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mAdSelectionServiceFilter,
                        mAdFilteringFeatureFactory,
                        mConsentManagerMock,
                        mMultiCloudSupportStrategy,
                        mAdSelectionDebugReportDao,
                        mAdIdFetcher,
                        mUnusedKAnonSignJoinFactory,
                        false,
                        mRetryStrategyFactory);

        // We call selectAds again to verify that scoring logic was also cached
        AdSelectionTestCallback resultsCallbackWithCaching =
                invokeSelectAds(
                        adSelectionServiceWithCache, adSelectionConfig, CALLER_PACKAGE_NAME);
        assertCallbackIsSuccessful(resultsCallbackWithCaching);

        // 1 call per CA bidJs + 2 calls per buyer signals + 2 calls per seller scoreAdJS, signals
        int expectedNetworkCallsNoCaching = (102 * 1) + 2 + 2;

        int serverCallsCountWithCaching = server.getRequestCount();
        assertTrue("Some requests should have been cached", cache.getCachedEntriesCount() > 0);
        assertTrue(
                "Network calls with caching should have been lesser",
                serverCallsCountWithCaching < expectedNetworkCallsNoCaching);

        // Cache cleanup should have been eventually invoked
        verify(mCacheObserver, timeout(3000)).update(HttpCache.CacheEventType.CLEANUP);
        cache.delete();
    }

    @Test
    public void testRunAdSelectionMultipleCAsJSCachedSuccess_v3BiddingLogic() throws Exception {
        MockWebServer server = mMockWebServerRule.startMockWebServer(DISPATCHER_V3_BIDDING_LOGIC);
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        List<AdTechIdentifier> participatingBuyers = new ArrayList<>();
        participatingBuyers.add(BUYER_1);
        participatingBuyers.add(BUYER_2);

        int extraCustomAudienceCount = 100;
        for (int i = 1; i <= extraCustomAudienceCount; i++) {
            DBCustomAudience dBCustomAudienceX =
                    createDBCustomAudience(
                            BUYER_1,
                            DEFAULT_CUSTOM_AUDIENCE_NAME_SUFFIX + i,
                            // All these CAs use the same uri, therefore JS should be cached
                            mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                            bidsForBuyer1,
                            CustomAudienceFixture.VALID_ACTIVATION_TIME,
                            CustomAudienceFixture.VALID_EXPIRATION_TIME,
                            CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI,
                            BUYER_TRUSTED_SIGNAL_URI_PATH);
            mCustomAudienceDao.insertOrOverwriteCustomAudience(
                    dBCustomAudienceX,
                    CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                    false);
        }

        AdSelectionConfig adSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setCustomAudienceBuyers(participatingBuyers)
                        .setSeller(
                                AdTechIdentifier.fromString(
                                        mMockWebServerRule
                                                .uriForPath(SELLER_DECISION_LOGIC_URI_PATH)
                                                .getHost()))
                        .setDecisionLogicUri(
                                mMockWebServerRule.uriForPath(SELLER_DECISION_LOGIC_URI_PATH))
                        .setTrustedScoringSignalsUri(
                                mMockWebServerRule.uriForPath(SELLER_TRUSTED_SIGNAL_URI_PATH))
                        .build();

        HttpCache cache = CacheProviderFactory.create(mSpyContext, mFlags);
        cache.addObserver(mCacheObserver);

        // Creating client which has caching
        AdServicesHttpsClient httpClientWithCaching =
                new AdServicesHttpsClient(AdServicesExecutors.getBlockingExecutor(), cache);

        AdSelectionServiceImpl adSelectionServiceWithCache =
                new AdSelectionServiceImpl(
                        mAdSelectionEntryDaoSpy,
                        mAppInstallDao,
                        mCustomAudienceDao,
                        mEncodedPayloadDao,
                        mFrequencyCapDao,
                        mEncryptionKeyDao,
                        mEnrollmentDao,
                        httpClientWithCaching,
                        mDevContextFilter,
                        mLightweightExecutorService,
                        mBackgroundExecutorService,
                        mScheduledExecutor,
                        mSpyContext,
                        mAdServicesLoggerMock,
                        mFlags,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mAdSelectionServiceFilter,
                        mAdFilteringFeatureFactory,
                        mConsentManagerMock,
                        mMultiCloudSupportStrategy,
                        mAdSelectionDebugReportDao,
                        mAdIdFetcher,
                        mUnusedKAnonSignJoinFactory,
                        false,
                        mRetryStrategyFactory);

        // We call selectAds again to verify that scoring logic was also cached
        AdSelectionTestCallback resultsCallbackWithCaching =
                invokeSelectAds(
                        adSelectionServiceWithCache, adSelectionConfig, CALLER_PACKAGE_NAME);
        assertCallbackIsSuccessful(resultsCallbackWithCaching);

        // 1 call per CA bidJs + 2 calls per buyer signals + 2 calls per seller scoreAdJS, signals
        int expectedNetworkCallsNoCaching = (102 * 1) + 2 + 2;

        int serverCallsCountWithCaching = server.getRequestCount();
        assertTrue("Some requests should have been cached", cache.getCachedEntriesCount() > 0);
        assertTrue(
                "Network calls with caching should have been lesser",
                serverCallsCountWithCaching < expectedNetworkCallsNoCaching);

        // Cache cleanup should have been eventually invoked
        verify(mCacheObserver, timeout(3000)).update(HttpCache.CacheEventType.CLEANUP);
        cache.delete();
    }

    @Test
    @FlakyTest(bugId = 304764127)
    public void testRunAdSelectionSucceedsWithOverride_preV3BiddingLogic() throws Exception {
        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(3);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());

        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        mMockWebServerRule.startMockWebServer(mDispatcher);
        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);

        // Set dev override for  ad selection
        DBAdSelectionOverride adSelectionOverride =
                DBAdSelectionOverride.builder()
                        .setAdSelectionConfigId(
                                AdSelectionDevOverridesHelper.calculateAdSelectionConfigId(
                                        mAdSelectionConfig))
                        .setAppPackageName(MY_APP_PACKAGE_NAME)
                        .setDecisionLogicJS(USE_BID_AS_SCORE_JS)
                        .setTrustedScoringSignals(TRUSTED_SCORING_SIGNALS.toString())
                        .build();
        mAdSelectionEntryDaoSpy.persistAdSelectionOverride(adSelectionOverride);

        // Set dev override for custom audience
        DBCustomAudienceOverride dbCustomAudienceOverride =
                DBCustomAudienceOverride.builder()
                        .setOwner(dBCustomAudienceForBuyer2.getOwner())
                        .setBuyer(dBCustomAudienceForBuyer2.getBuyer())
                        .setName(dBCustomAudienceForBuyer2.getName())
                        .setAppPackageName(MY_APP_PACKAGE_NAME)
                        .setBiddingLogicJS(READ_BID_FROM_AD_METADATA_JS)
                        .setTrustedBiddingData(
                                new JSONObject(TRUSTED_BIDDING_SIGNALS_SERVER_DATA).toString())
                        .build();
        mCustomAudienceDao.persistCustomAudienceOverride(dbCustomAudienceOverride);

        when(mDevContextFilter.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(MY_APP_PACKAGE_NAME)
                                .build());

        // Creating new instance of service with new DevContextFilter
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
                        mSpyContext,
                        mAdServicesLoggerMock,
                        mFlags,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mAdSelectionServiceFilter,
                        mAdFilteringFeatureFactory,
                        mConsentManagerMock,
                        mMultiCloudSupportStrategy,
                        mAdSelectionDebugReportDao,
                        mAdIdFetcher,
                        mUnusedKAnonSignJoinFactory,
                        false,
                        mRetryStrategyFactory);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, mAdSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        assertCallbackIsSuccessful(resultsCallback);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(resultSelectionId));
        assertEquals(
                AD_URI_PREFIX + BUYER_2 + "/ad3",
                resultsCallback.mAdSelectionResponse.getRenderUri().toString());
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(isA(RunAdBiddingProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(isA(RunAdScoringProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        geq((int) BINDER_ELAPSED_TIME_MS));
    }

    @Test
    @FlakyTest(bugId = 304764127)
    public void testRunAdSelectionSucceedsWithOverride_v3BiddingLogic() throws Exception {
        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(3);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());

        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        mMockWebServerRule.startMockWebServer(mDispatcher);
        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);

        // Set dev override for  ad selection
        DBAdSelectionOverride adSelectionOverride =
                DBAdSelectionOverride.builder()
                        .setAdSelectionConfigId(
                                AdSelectionDevOverridesHelper.calculateAdSelectionConfigId(
                                        mAdSelectionConfig))
                        .setAppPackageName(MY_APP_PACKAGE_NAME)
                        .setDecisionLogicJS(USE_BID_AS_SCORE_JS)
                        .setTrustedScoringSignals(TRUSTED_SCORING_SIGNALS.toString())
                        .build();
        mAdSelectionEntryDaoSpy.persistAdSelectionOverride(adSelectionOverride);

        // Set dev override for custom audience
        DBCustomAudienceOverride dbCustomAudienceOverride =
                DBCustomAudienceOverride.builder()
                        .setOwner(dBCustomAudienceForBuyer2.getOwner())
                        .setBuyer(dBCustomAudienceForBuyer2.getBuyer())
                        .setName(dBCustomAudienceForBuyer2.getName())
                        .setAppPackageName(MY_APP_PACKAGE_NAME)
                        .setBiddingLogicJS(READ_BID_FROM_AD_METADATA_JS_V3)
                        .setBiddingLogicJsVersion(
                                JsVersionRegister.BUYER_BIDDING_LOGIC_VERSION_VERSION_3)
                        .setTrustedBiddingData(
                                new JSONObject(TRUSTED_BIDDING_SIGNALS_SERVER_DATA).toString())
                        .build();
        mCustomAudienceDao.persistCustomAudienceOverride(dbCustomAudienceOverride);

        when(mDevContextFilter.createDevContext())
                .thenReturn(
                        DevContext.builder()
                                .setDevOptionsEnabled(true)
                                .setCallingAppPackageName(MY_APP_PACKAGE_NAME)
                                .build());

        // Creating new instance of service with new DevContextFilter
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
                        mSpyContext,
                        mAdServicesLoggerMock,
                        mFlags,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mAdSelectionServiceFilter,
                        mAdFilteringFeatureFactory,
                        mConsentManagerMock,
                        mMultiCloudSupportStrategy,
                        mAdSelectionDebugReportDao,
                        mAdIdFetcher,
                        mUnusedKAnonSignJoinFactory,
                        false,
                        mRetryStrategyFactory);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, mAdSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        assertCallbackIsSuccessful(resultsCallback);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(resultSelectionId));
        assertEquals(
                AD_URI_PREFIX + BUYER_2 + "/ad3",
                resultsCallback.mAdSelectionResponse.getRenderUri().toString());
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(isA(RunAdBiddingProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(isA(RunAdScoringProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        geq((int) BINDER_ELAPSED_TIME_MS));
    }

    @Test
    public void testRunAdSelectionActiveCAs() throws Exception {
        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(3);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());

        mMockWebServerRule.startMockWebServer(mDispatcher);
        List<Double> bidsForBuyer1 = ImmutableList.of(0.9, 0.45);
        List<Double> bidsForBuyer2 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer3 = ImmutableList.of(10.0, 100.0);
        DBCustomAudience dbCustomAudienceActive =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);
        DBCustomAudience dBCustomAudienceInactive =
                createDBCustomAudience(
                        BUYER_2,
                        DEFAULT_CUSTOM_AUDIENCE_NAME_SUFFIX,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2,
                        CustomAudienceFixture.INVALID_DELAYED_ACTIVATION_TIME,
                        CustomAudienceFixture.VALID_EXPIRATION_TIME,
                        CustomAudienceFixture.VALID_LAST_UPDATE_TIME_24_HRS_BEFORE,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);
        DBCustomAudience dBCustomAudienceExpired =
                createDBCustomAudience(
                        BUYER_3,
                        DEFAULT_CUSTOM_AUDIENCE_NAME_SUFFIX,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_3),
                        bidsForBuyer3,
                        CustomAudienceFixture.VALID_ACTIVATION_TIME,
                        CustomAudienceFixture.INVALID_NOW_EXPIRATION_TIME,
                        CustomAudienceFixture.VALID_LAST_UPDATE_TIME_24_HRS_BEFORE,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);
        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dbCustomAudienceActive,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceInactive,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceExpired,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_3),
                false);
        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, mAdSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        assertCallbackIsSuccessful(resultsCallback);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(resultSelectionId));
        assertEquals(
                AD_URI_PREFIX + BUYER_1 + "/ad1",
                resultsCallback.mAdSelectionResponse.getRenderUri().toString());
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(isA(RunAdBiddingProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(isA(RunAdScoringProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        geq((int) BINDER_ELAPSED_TIME_MS));
    }

    @Test
    public void testRunAdSelectionNoCAsActive() throws Exception {
        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(2);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());

        mMockWebServerRule.startMockWebServer(mDispatcher);
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);
        List<Double> bidsForBuyer3 = ImmutableList.of(4.3, 6.0, 10.0);

        DBCustomAudience dBCustomAudienceInactive =
                createDBCustomAudience(
                        BUYER_1,
                        DEFAULT_CUSTOM_AUDIENCE_NAME_SUFFIX,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1,
                        CustomAudienceFixture.INVALID_DELAYED_ACTIVATION_TIME,
                        CustomAudienceFixture.VALID_EXPIRATION_TIME,
                        CustomAudienceFixture.VALID_LAST_UPDATE_TIME_24_HRS_BEFORE,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);
        DBCustomAudience dBCustomAudienceExpired =
                createDBCustomAudience(
                        BUYER_2,
                        DEFAULT_CUSTOM_AUDIENCE_NAME_SUFFIX,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2,
                        CustomAudienceFixture.VALID_ACTIVATION_TIME,
                        CustomAudienceFixture.INVALID_NOW_EXPIRATION_TIME,
                        CustomAudienceFixture.VALID_LAST_UPDATE_TIME_24_HRS_BEFORE,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);

        DBCustomAudience dBCustomAudienceOutdated =
                createDBCustomAudience(
                        BUYER_3,
                        DEFAULT_CUSTOM_AUDIENCE_NAME_SUFFIX,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_3),
                        bidsForBuyer3,
                        CustomAudienceFixture.VALID_ACTIVATION_TIME,
                        CustomAudienceFixture.VALID_EXPIRATION_TIME,
                        CustomAudienceFixture.INVALID_LAST_UPDATE_TIME_72_DAYS_BEFORE,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);
        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceInactive,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceExpired,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceOutdated,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_3),
                false);

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, mAdSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        assertCallbackFailed(resultsCallback);
        assertEquals(resultsCallback.mFledgeErrorResponse.getStatusCode(), STATUS_INTERNAL_ERROR);
        verifyErrorMessageIsCorrect(
                resultsCallback.mFledgeErrorResponse.getErrorMessage(),
                String.format(
                        Locale.ENGLISH,
                        AdSelectionRunner.AD_SELECTION_ERROR_PATTERN,
                        AdSelectionRunner.ERROR_AD_SELECTION_FAILURE,
                        ERROR_NO_CA_AND_CONTEXTUAL_ADS_AVAILABLE));
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(isA(RunAdBiddingProcessReportedStats.class));
        verify(mAdServicesLoggerMock, never()).logRunAdScoringProcessReportedStats(any());
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_INTERNAL_ERROR),
                        geq((int) BINDER_ELAPSED_TIME_MS));
    }

    @Test
    public void testRunAdSelectionNoCAsFailure() throws Exception {
        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(2);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());

        // Do not populate CustomAudience DAO
        mMockWebServerRule.startMockWebServer(mDispatcher);
        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, mAdSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        assertCallbackFailed(resultsCallback);
        verifyErrorMessageIsCorrect(
                resultsCallback.mFledgeErrorResponse.getErrorMessage(),
                ERROR_NO_CA_AND_CONTEXTUAL_ADS_AVAILABLE);
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(isA(RunAdBiddingProcessReportedStats.class));
        verify(mAdServicesLoggerMock, never()).logRunAdScoringProcessReportedStats(any());
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_INTERNAL_ERROR),
                        geq((int) BINDER_ELAPSED_TIME_MS));
    }

    @Test
    public void testRunAdSelectionNoBuyersFailure() throws Exception {
        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());

        // Do not populate buyers in AdSelectionConfig
        mAdSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setSeller(
                                AdTechIdentifier.fromString(
                                        mMockWebServerRule
                                                .uriForPath(SELLER_DECISION_LOGIC_URI_PATH)
                                                .getHost()))
                        .setDecisionLogicUri(
                                mMockWebServerRule.uriForPath(SELLER_DECISION_LOGIC_URI_PATH))
                        .setTrustedScoringSignalsUri(
                                mMockWebServerRule.uriForPath(SELLER_TRUSTED_SIGNAL_URI_PATH))
                        .setCustomAudienceBuyers(Collections.emptyList())
                        .build();

        mMockWebServerRule.startMockWebServer(mDispatcher);
        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, mAdSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        assertCallbackFailed(resultsCallback);
        verifyErrorMessageIsCorrect(
                resultsCallback.mFledgeErrorResponse.getErrorMessage(),
                ERROR_NO_BUYERS_OR_CONTEXTUAL_ADS_AVAILABLE);
        verify(mAdServicesLoggerMock, never()).logRunAdScoringProcessReportedStats(any());
        verify(mAdServicesLoggerMock, never()).logRunAdBiddingProcessReportedStats(any());
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_INVALID_ARGUMENT),
                        geq((int) BINDER_ELAPSED_TIME_MS));
    }

    @Test
    public void testRunAdSelectionPartialAdsExcludedBidding() throws Exception {
        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(3);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());

        mMockWebServerRule.startMockWebServer(mDispatcher);
        // Setting bids which are partially non-positive
        List<Double> bidsForBuyer1 = ImmutableList.of(-1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(-4.5, -6.7, -10.0);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, mAdSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        assertCallbackIsSuccessful(resultsCallback);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(resultSelectionId));
        assertEquals(
                AD_URI_PREFIX + BUYER_1 + "/ad2",
                resultsCallback.mAdSelectionResponse.getRenderUri().toString());
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(isA(RunAdBiddingProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(isA(RunAdScoringProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        geq((int) BINDER_ELAPSED_TIME_MS));
    }

    private void assertCallbackIsSuccessful(AdSelectionTestCallback resultsCallback) {
        assertTrue(
                resultsCallback.mFledgeErrorResponse != null
                        ? String.format(
                                Locale.ENGLISH,
                                "Expected callback to succeed but it failed with status %d and"
                                        + " message '%s'",
                                resultsCallback.mFledgeErrorResponse.getStatusCode(),
                                resultsCallback.mFledgeErrorResponse.getErrorMessage())
                        : "Expected callback to succeed but it failed with no details",
                resultsCallback.mIsSuccess);
    }

    private void assertCallbackIsSuccessful(ReportImpressionTestCallback resultsCallback) {
        assertTrue(
                resultsCallback.mFledgeErrorResponse != null
                        ? String.format(
                                Locale.ENGLISH,
                                "Expected callback to succeed but it failed with status %d and"
                                        + " message '%s'",
                                resultsCallback.mFledgeErrorResponse.getStatusCode(),
                                resultsCallback.mFledgeErrorResponse.getErrorMessage())
                        : "Expected callback to succeed but it failed with no details",
                resultsCallback.mIsSuccess);
    }

    private void assertCallbackFailed(AdSelectionTestCallback resultsCallback) {
        assertFalse("Expected callback to fail but succeeded", resultsCallback.mIsSuccess);
    }

    @Test
    public void testRunAdSelectionMissingBiddingLogicFailure() throws Exception {
        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(2);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());

        // Setting bids->scores
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        // In this case the Buyers have no bidding logic response in dispatcher
        Dispatcher dispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        if (SELLER_DECISION_LOGIC_URI_PATH.equals(request.getPath())) {
                            return new MockResponse().setBody(USE_BID_AS_SCORE_JS);
                        } else if ((BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1.toString())
                                        .equals(request.getPath())
                                || (BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2.toString())
                                        .equals(request.getPath())) {
                            return new MockResponse().setBody("");
                        } else if (request.getPath().startsWith(BUYER_TRUSTED_SIGNAL_URI_PATH)) {
                            String[] keys =
                                    Uri.parse(request.getPath())
                                            .getQueryParameter(
                                                    DBTrustedBiddingData.QUERY_PARAM_KEYS)
                                            .split(",");
                            Map<String, String> jsonMap = new HashMap<>();
                            for (String key : keys) {
                                jsonMap.put(key, TRUSTED_BIDDING_SIGNALS_SERVER_DATA.get(key));
                            }
                            return new MockResponse().setBody(new JSONObject(jsonMap).toString());
                        }

                        // The seller params vary based on runtime, so we are returning trusted
                        // signals based on correct path prefix
                        if (request.getPath()
                                .startsWith(
                                        SELLER_TRUSTED_SIGNAL_URI_PATH
                                                + SELLER_TRUSTED_SIGNAL_PARAMS)) {
                            return new MockResponse().setBody(TRUSTED_SCORING_SIGNALS.toString());
                        }
                        return new MockResponse().setResponseCode(404);
                    }
                };

        mMockWebServerRule.startMockWebServer(dispatcher);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, mAdSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        assertCallbackFailed(resultsCallback);
        verifyErrorMessageIsCorrect(
                resultsCallback.mFledgeErrorResponse.getErrorMessage(),
                ERROR_NO_VALID_BIDS_OR_CONTEXTUAL_ADS_FOR_SCORING);
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(isA(RunAdBiddingProcessReportedStats.class));
        verify(mAdServicesLoggerMock, never()).logRunAdScoringProcessReportedStats(any());
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_INTERNAL_ERROR),
                        geq((int) BINDER_ELAPSED_TIME_MS));
    }

    @Test
    public void testRunAdSelectionMissingScoringLogicFailure() throws Exception {
        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(3);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());

        // Setting bids->scores
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        // In this case the Seller has no scoring logic in dispatcher
        Dispatcher dispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        if (SELLER_DECISION_LOGIC_URI_PATH.equals(request.getPath())) {
                            return new MockResponse().setBody("");
                        } else if ((BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1.toString())
                                        .equals(request.getPath())
                                || (BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2.toString())
                                        .equals(request.getPath())) {
                            return new MockResponse().setBody(READ_BID_FROM_AD_METADATA_JS);
                        } else if (request.getPath().startsWith(BUYER_TRUSTED_SIGNAL_URI_PATH)) {
                            String[] keys =
                                    Uri.parse(request.getPath())
                                            .getQueryParameter(
                                                    DBTrustedBiddingData.QUERY_PARAM_KEYS)
                                            .split(",");
                            Map<String, String> jsonMap = new HashMap<>();
                            for (String key : keys) {
                                jsonMap.put(key, TRUSTED_BIDDING_SIGNALS_SERVER_DATA.get(key));
                            }
                            return new MockResponse().setBody(new JSONObject(jsonMap).toString());
                        }

                        // The seller params vary based on runtime, so we are returning trusted
                        // signals based on correct path prefix
                        if (request.getPath()
                                .startsWith(
                                        SELLER_TRUSTED_SIGNAL_URI_PATH
                                                + SELLER_TRUSTED_SIGNAL_PARAMS)) {
                            return new MockResponse().setBody(TRUSTED_SCORING_SIGNALS.toString());
                        }
                        return new MockResponse().setResponseCode(404);
                    }
                };

        mMockWebServerRule.startMockWebServer(dispatcher);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, mAdSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        assertCallbackFailed(resultsCallback);
        verifyErrorMessageIsCorrect(
                resultsCallback.mFledgeErrorResponse.getErrorMessage(),
                ERROR_SCORE_AD_LOGIC_MISSING);
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(isA(RunAdBiddingProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(isA(RunAdScoringProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_INTERNAL_ERROR),
                        geq((int) BINDER_ELAPSED_TIME_MS));
    }

    @Test
    public void testRunAdSelectionErrorFetchingScoringLogicFailure() throws Exception {
        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(3);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());

        // Setting bids->scores
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        // In this case the web server returns failure for scoring
        Dispatcher dispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        if (SELLER_DECISION_LOGIC_URI_PATH.equals(request.getPath())) {
                            return new MockResponse().setResponseCode(404);
                        } else if ((BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1.toString())
                                        .equals(request.getPath())
                                || (BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2.toString())
                                        .equals(request.getPath())) {
                            return new MockResponse().setBody(READ_BID_FROM_AD_METADATA_JS);
                        } else if (request.getPath().startsWith(BUYER_TRUSTED_SIGNAL_URI_PATH)) {
                            String[] keys =
                                    Uri.parse(request.getPath())
                                            .getQueryParameter(
                                                    DBTrustedBiddingData.QUERY_PARAM_KEYS)
                                            .split(",");
                            Map<String, String> jsonMap = new HashMap<>();
                            for (String key : keys) {
                                jsonMap.put(key, TRUSTED_BIDDING_SIGNALS_SERVER_DATA.get(key));
                            }
                            return new MockResponse().setBody(new JSONObject(jsonMap).toString());
                        }

                        // The seller params vary based on runtime, so we are returning trusted
                        // signals based on correct path prefix
                        if (request.getPath()
                                .startsWith(
                                        SELLER_TRUSTED_SIGNAL_URI_PATH
                                                + SELLER_TRUSTED_SIGNAL_PARAMS)) {
                            return new MockResponse().setBody(TRUSTED_SCORING_SIGNALS.toString());
                        }
                        return new MockResponse().setResponseCode(404);
                    }
                };

        mMockWebServerRule.startMockWebServer(dispatcher);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, mAdSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        assertCallbackFailed(resultsCallback);
        verifyErrorMessageIsCorrect(
                resultsCallback.mFledgeErrorResponse.getErrorMessage(), MISSING_SCORING_LOGIC);
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(isA(RunAdBiddingProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(isA(RunAdScoringProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_INTERNAL_ERROR),
                        geq((int) BINDER_ELAPSED_TIME_MS));
    }

    @Test
    public void testRunAdSelectionPartialMissingBiddingLogic() throws Exception {
        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(3);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());

        // Setting bids->scores
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        // In this case the Buyer 2 has no bidding logic response in dispatcher
        Dispatcher dispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        if (SELLER_DECISION_LOGIC_URI_PATH.equals(request.getPath())) {
                            return new MockResponse().setBody(USE_BID_AS_SCORE_JS);
                        } else if ((BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1.toString())
                                .equals(request.getPath())) {
                            return new MockResponse().setBody(READ_BID_FROM_AD_METADATA_JS);
                        } else if ((BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2.toString())
                                .equals(request.getPath())) {
                            return new MockResponse().setBody("");
                        } else if (request.getPath().startsWith(BUYER_TRUSTED_SIGNAL_URI_PATH)) {
                            String[] keys =
                                    Uri.parse(request.getPath())
                                            .getQueryParameter(
                                                    DBTrustedBiddingData.QUERY_PARAM_KEYS)
                                            .split(",");
                            Map<String, String> jsonMap = new HashMap<>();
                            for (String key : keys) {
                                jsonMap.put(key, TRUSTED_BIDDING_SIGNALS_SERVER_DATA.get(key));
                            }
                            return new MockResponse().setBody(new JSONObject(jsonMap).toString());
                        }

                        // The seller params vary based on runtime, so we are returning trusted
                        // signals based on correct path prefix
                        if (request.getPath()
                                .startsWith(
                                        SELLER_TRUSTED_SIGNAL_URI_PATH
                                                + SELLER_TRUSTED_SIGNAL_PARAMS)) {
                            return new MockResponse().setBody(TRUSTED_SCORING_SIGNALS.toString());
                        }
                        return new MockResponse().setResponseCode(404);
                    }
                };

        mMockWebServerRule.startMockWebServer(dispatcher);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, mAdSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();

        assertCallbackIsSuccessful(resultsCallback);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(resultSelectionId));
        assertEquals(
                AD_URI_PREFIX + BUYER_1 + "/ad2",
                resultsCallback.mAdSelectionResponse.getRenderUri().toString());
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(isA(RunAdBiddingProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(isA(RunAdScoringProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        geq((int) BINDER_ELAPSED_TIME_MS));
    }

    @Test
    public void testRunAdSelectionPartialNonPositiveScoring() throws Exception {
        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(3);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());

        // Setting bids, in this case the odd bids will be made negative by scoring logic
        List<Double> bidsForBuyer1 = ImmutableList.of(1.0, 2.0);
        List<Double> bidsForBuyer2 = ImmutableList.of(3.0, 5.0, 7.0);

        // This scoring logic assigns negative score to odd bids
        String makeOddBidsNegativeScoreJs =
                "function scoreAd(ad, bid, auction_config, seller_signals, "
                        + "trusted_scoring_signals, contextual_signal, user_signal, "
                        + "custom_audience_signal) { \n"
                        + "  return {'status': 0, 'score': (bid % 2 == 0) ? bid : -bid };\n"
                        + "}";

        Dispatcher dispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        if (SELLER_DECISION_LOGIC_URI_PATH.equals(request.getPath())) {
                            return new MockResponse().setBody(makeOddBidsNegativeScoreJs);
                        } else if ((BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1.toString())
                                        .equals(request.getPath())
                                || (BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2.toString())
                                        .equals(request.getPath())) {
                            return new MockResponse().setBody(READ_BID_FROM_AD_METADATA_JS);
                        } else if (request.getPath().startsWith(BUYER_TRUSTED_SIGNAL_URI_PATH)) {
                            String[] keys =
                                    Uri.parse(request.getPath())
                                            .getQueryParameter(
                                                    DBTrustedBiddingData.QUERY_PARAM_KEYS)
                                            .split(",");
                            Map<String, String> jsonMap = new HashMap<>();
                            for (String key : keys) {
                                jsonMap.put(key, TRUSTED_BIDDING_SIGNALS_SERVER_DATA.get(key));
                            }
                            return new MockResponse().setBody(new JSONObject(jsonMap).toString());
                        }

                        // The seller params vary based on runtime, so we are returning trusted
                        // signals based on correct path prefix
                        if (request.getPath()
                                .startsWith(
                                        SELLER_TRUSTED_SIGNAL_URI_PATH
                                                + SELLER_TRUSTED_SIGNAL_PARAMS)) {
                            return new MockResponse().setBody(TRUSTED_SCORING_SIGNALS.toString());
                        }
                        return new MockResponse().setResponseCode(404);
                    }
                };

        mMockWebServerRule.startMockWebServer(dispatcher);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, mAdSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        assertCallbackIsSuccessful(resultsCallback);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(resultSelectionId));
        assertEquals(
                AD_URI_PREFIX + BUYER_1 + "/ad2",
                resultsCallback.mAdSelectionResponse.getRenderUri().toString());
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(isA(RunAdBiddingProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(isA(RunAdScoringProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        geq((int) BINDER_ELAPSED_TIME_MS));
    }

    @Test
    public void testRunAdSelectionNonPositiveScoringFailure() throws Exception {
        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(3);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());

        // Setting bids, in this case the odd bids will be made negative by scoring logic
        List<Double> bidsForBuyer1 = ImmutableList.of(1.0, 9.0);
        List<Double> bidsForBuyer2 = ImmutableList.of(3.0, 5.0, 7.0);

        // This scoring logic assigns negative score to odd bids
        String makeOddBidsNegativeScoreJs =
                "function scoreAd(ad, bid, auction_config, seller_signals, "
                        + "trusted_scoring_signals, contextual_signal, user_signal, "
                        + "custom_audience_signal) { \n"
                        + "  return {'status': 0, 'score': (bid % 2 == 0) ? bid : -bid };\n"
                        + "}";

        Dispatcher dispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        if (SELLER_DECISION_LOGIC_URI_PATH.equals(request.getPath())) {
                            return new MockResponse().setBody(makeOddBidsNegativeScoreJs);
                        } else if ((BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1.toString())
                                        .equals(request.getPath())
                                || (BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2.toString())
                                        .equals(request.getPath())) {
                            return new MockResponse().setBody(READ_BID_FROM_AD_METADATA_JS);
                        } else if (request.getPath().startsWith(BUYER_TRUSTED_SIGNAL_URI_PATH)) {
                            String[] keys =
                                    Uri.parse(request.getPath())
                                            .getQueryParameter(
                                                    DBTrustedBiddingData.QUERY_PARAM_KEYS)
                                            .split(",");
                            Map<String, String> jsonMap = new HashMap<>();
                            for (String key : keys) {
                                jsonMap.put(key, TRUSTED_BIDDING_SIGNALS_SERVER_DATA.get(key));
                            }
                            return new MockResponse().setBody(new JSONObject(jsonMap).toString());
                        }

                        // The seller params vary based on runtime, so we are returning trusted
                        // signals based on correct path prefix
                        if (request.getPath()
                                .startsWith(
                                        SELLER_TRUSTED_SIGNAL_URI_PATH
                                                + SELLER_TRUSTED_SIGNAL_PARAMS)) {
                            return new MockResponse().setBody(TRUSTED_SCORING_SIGNALS.toString());
                        }
                        return new MockResponse().setResponseCode(404);
                    }
                };

        mMockWebServerRule.startMockWebServer(dispatcher);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, mAdSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        assertCallbackFailed(resultsCallback);
        verifyErrorMessageIsCorrect(
                resultsCallback.mFledgeErrorResponse.getErrorMessage(), ERROR_NO_WINNING_AD_FOUND);
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(isA(RunAdBiddingProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(isA(RunAdScoringProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_INTERNAL_ERROR),
                        geq((int) BINDER_ELAPSED_TIME_MS));
    }

    @Test
    public void testRunAdSelectionBiddingTimesOutForCA() throws Exception {
        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(3);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());

        Flags flagsWithSmallerLimits =
                new AdSelectionE2ETestFlags() {
                    @Override
                    public long getAdSelectionBiddingTimeoutPerCaMs() {
                        return 1500;
                    }

                    @Override
                    public boolean getEnforceIsolateMaxHeapSize() {
                        return false;
                    }

                    @Override
                    public boolean getDisableFledgeEnrollmentCheck() {
                        return true;
                    }

                    @Override
                    public float getSdkRequestPermitsPerSecond() {
                        // Unlimited rate for unit tests to avoid flake in tests due to rate
                        // limiting
                        return -1;
                    }
                };

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
                        mSpyContext,
                        mAdServicesLoggerMock,
                        flagsWithSmallerLimits,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mAdSelectionServiceFilter,
                        mAdFilteringFeatureFactory,
                        mConsentManagerMock,
                        mMultiCloudSupportStrategy,
                        mAdSelectionDebugReportDao,
                        mAdIdFetcher,
                        mUnusedKAnonSignJoinFactory,
                        false,
                        mRetryStrategyFactory);

        String jsWaitMoreThanAllowedForBiddingPerCa =
                insertJsWait(2 * mFlags.getAdSelectionBiddingTimeoutPerCaMs());
        String readBidFromAdMetadataWithDelayJs =
                "function generateBid(ad, auction_signals, per_buyer_signals,"
                        + " trusted_bidding_signals, contextual_signals,"
                        + " custom_audience_signals) { \n"
                        + jsWaitMoreThanAllowedForBiddingPerCa
                        + "  return {'status': 0, 'ad': ad, 'bid': ad.metadata.result };\n"
                        + "}";

        // In this case the one buyer's logic takes more than the bidding time limit
        Dispatcher dispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        if (SELLER_DECISION_LOGIC_URI_PATH.equals(request.getPath())) {
                            return new MockResponse().setBody(USE_BID_AS_SCORE_JS);
                        } else if ((BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1.toString())
                                .equals(request.getPath())) {
                            return new MockResponse().setBody(readBidFromAdMetadataWithDelayJs);
                        } else if ((BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2.toString())
                                .equals(request.getPath())) {
                            return new MockResponse().setBody(READ_BID_FROM_AD_METADATA_JS);
                        } else if (request.getPath().startsWith(BUYER_TRUSTED_SIGNAL_URI_PATH)) {
                            String[] keys =
                                    Uri.parse(request.getPath())
                                            .getQueryParameter(
                                                    DBTrustedBiddingData.QUERY_PARAM_KEYS)
                                            .split(",");
                            Map<String, String> jsonMap = new HashMap<>();
                            for (String key : keys) {
                                jsonMap.put(key, TRUSTED_BIDDING_SIGNALS_SERVER_DATA.get(key));
                            }
                            return new MockResponse().setBody(new JSONObject(jsonMap).toString());
                        }

                        // The seller params vary based on runtime, so we are returning trusted
                        // signals based on correct path prefix
                        if (request.getPath()
                                .startsWith(
                                        SELLER_TRUSTED_SIGNAL_URI_PATH
                                                + SELLER_TRUSTED_SIGNAL_PARAMS)) {
                            return new MockResponse().setBody(TRUSTED_SCORING_SIGNALS.toString());
                        }
                        return new MockResponse().setResponseCode(404);
                    }
                };

        mMockWebServerRule.startMockWebServer(dispatcher);
        // buyer1/ad3 is clear winner but will time out
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2, 15.0);
        // due to timeout buyer2/ad3 will win
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, mAdSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        assertCallbackIsSuccessful(resultsCallback);
        assertEquals(
                AD_URI_PREFIX + BUYER_2 + "/ad3",
                resultsCallback.mAdSelectionResponse.getRenderUri().toString());
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(isA(RunAdBiddingProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(isA(RunAdScoringProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        geq((int) BINDER_ELAPSED_TIME_MS));
    }

    @Test
    public void testRunAdSelectionImposesPerBuyerBiddingTimeout_preV3BiddingLogic()
            throws Exception {
        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(6);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());
        Long lenientPerBuyerTimeOutLimit = 50000L;
        Long tightPerBuyerTimeOutLimit = 2000L;
        int largeCACountForBuyer = 300;

        Flags flagsWithLenientBuyerBiddingLimits =
                new AdSelectionE2ETestFlags() {
                    @Override
                    public long getAdSelectionBiddingTimeoutPerBuyerMs() {
                        return lenientPerBuyerTimeOutLimit;
                    }

                    @Override
                    public boolean getEnforceIsolateMaxHeapSize() {
                        return false;
                    }

                    @Override
                    public boolean getDisableFledgeEnrollmentCheck() {
                        return true;
                    }

                    @Override
                    public float getSdkRequestPermitsPerSecond() {
                        // Unlimited rate for unit tests to avoid flake in tests due to rate
                        // limiting
                        return -1;
                    }

                    @Override
                    public long getAdSelectionOverallTimeoutMs() {
                        return lenientPerBuyerTimeOutLimit * 3;
                    }
                };

        MockWebServer server = mMockWebServerRule.startMockWebServer(mDispatcher);
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        List<AdTechIdentifier> participatingBuyers = new ArrayList<>();
        participatingBuyers.add(BUYER_1);
        participatingBuyers.add(BUYER_2);
        participatingBuyers.add(BUYER_3);

        for (int i = 1; i <= largeCACountForBuyer; i++) {
            DBCustomAudience dBCustomAudienceX =
                    createDBCustomAudience(
                            BUYER_3,
                            "-" + i,
                            mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                            bidsForBuyer1,
                            CustomAudienceFixture.VALID_ACTIVATION_TIME,
                            CustomAudienceFixture.VALID_EXPIRATION_TIME,
                            CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI,
                            BUYER_TRUSTED_SIGNAL_URI_PATH);
            mCustomAudienceDao.insertOrOverwriteCustomAudience(
                    dBCustomAudienceX,
                    CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_3),
                    false);
        }

        AdSelectionConfig adSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setCustomAudienceBuyers(participatingBuyers)
                        .setSeller(
                                AdTechIdentifier.fromString(
                                        mMockWebServerRule
                                                .uriForPath(SELLER_DECISION_LOGIC_URI_PATH)
                                                .getHost()))
                        .setDecisionLogicUri(
                                mMockWebServerRule.uriForPath(SELLER_DECISION_LOGIC_URI_PATH))
                        .setTrustedScoringSignalsUri(
                                mMockWebServerRule.uriForPath(SELLER_TRUSTED_SIGNAL_URI_PATH))
                        .build();

        // Create an instance of AdSelection Service with lenient dependencies
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
                        mSpyContext,
                        mAdServicesLoggerMock,
                        flagsWithLenientBuyerBiddingLimits,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mAdSelectionServiceFilter,
                        mAdFilteringFeatureFactory,
                        mConsentManagerMock,
                        mMultiCloudSupportStrategy,
                        mAdSelectionDebugReportDao,
                        mAdIdFetcher,
                        mUnusedKAnonSignJoinFactory,
                        false,
                        mRetryStrategyFactory);

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, adSelectionConfig, CALLER_PACKAGE_NAME);

        assertCallbackIsSuccessful(resultsCallback);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(resultSelectionId));
        assertEquals(
                AD_URI_PREFIX + BUYER_2 + "/ad3",
                resultsCallback.mAdSelectionResponse.getRenderUri().toString());

        int networkRequestCountWithLenientTimeout = server.getRequestCount();

        // Now we run the same Ad selection with tight per buyer timeout limits
        Flags flagsWithTightBuyerBiddingLimits =
                new AdSelectionE2ETestFlags() {
                    @Override
                    public long getAdSelectionBiddingTimeoutPerBuyerMs() {
                        return tightPerBuyerTimeOutLimit;
                    }

                    @Override
                    public boolean getEnforceIsolateMaxHeapSize() {
                        return false;
                    }

                    @Override
                    public boolean getDisableFledgeEnrollmentCheck() {
                        return true;
                    }

                    @Override
                    public float getSdkRequestPermitsPerSecond() {
                        // Unlimited rate for unit tests to avoid flake in tests due to rate
                        // limiting
                        return -1;
                    }

                    @Override
                    public long getAdSelectionOverallTimeoutMs() {
                        return lenientPerBuyerTimeOutLimit * 3;
                    }

                    @Override
                    public int getAdSelectionMaxConcurrentBiddingCount() {
                        return 1;
                    }
                };

        // Create an instance of AdSelection Service with tight dependencies
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
                        mSpyContext,
                        mAdServicesLoggerMock,
                        flagsWithTightBuyerBiddingLimits,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mAdSelectionServiceFilter,
                        mAdFilteringFeatureFactory,
                        mConsentManagerMock,
                        mMultiCloudSupportStrategy,
                        mAdSelectionDebugReportDao,
                        mAdIdFetcher,
                        mUnusedKAnonSignJoinFactory,
                        false,
                        mRetryStrategyFactory);

        resultsCallback =
                invokeSelectAds(mAdSelectionService, adSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        assertCallbackIsSuccessful(resultsCallback);
        resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(resultSelectionId));
        assertEquals(
                AD_URI_PREFIX + BUYER_2 + "/ad3",
                resultsCallback.mAdSelectionResponse.getRenderUri().toString());

        int networkRequestCountWithTightTimeout =
                server.getRequestCount() - networkRequestCountWithLenientTimeout;

        sLogger.v(
                String.format(
                        "Network calls with buyer timeout :%d, network calls with"
                                + " lenient timeout :%d",
                        networkRequestCountWithTightTimeout,
                        networkRequestCountWithLenientTimeout));
        assertTrue(
                String.format(
                        "Network calls with buyer timeout :%d, are not less than network calls with"
                                + " lenient timeout :%d",
                        networkRequestCountWithTightTimeout, networkRequestCountWithLenientTimeout),
                networkRequestCountWithTightTimeout < networkRequestCountWithLenientTimeout);
        verify(mAdServicesLoggerMock, times(2))
                .logRunAdBiddingProcessReportedStats(isA(RunAdBiddingProcessReportedStats.class));
        verify(mAdServicesLoggerMock, times(2))
                .logRunAdScoringProcessReportedStats(isA(RunAdScoringProcessReportedStats.class));
        verify(mAdServicesLoggerMock, times(2))
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        verify(mAdServicesLoggerMock, times(2))
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        geq((int) BINDER_ELAPSED_TIME_MS));
    }

    @FlakyTest(bugId = 304764127)
    @Test
    public void testRunAdSelectionImposesPerBuyerBiddingTimeout_v3BiddingLogic() throws Exception {
        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(6);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());
        Long lenientPerBuyerTimeOutLimit = 50000L;
        Long tightPerBuyerTimeOutLimit = 2000L;
        int largeCACountForBuyer = 300;

        Flags flagsWithLenientBuyerBiddingLimits =
                new AdSelectionE2ETestFlags() {
                    @Override
                    public long getAdSelectionBiddingTimeoutPerBuyerMs() {
                        return lenientPerBuyerTimeOutLimit;
                    }

                    @Override
                    public boolean getEnforceIsolateMaxHeapSize() {
                        return false;
                    }

                    @Override
                    public boolean getDisableFledgeEnrollmentCheck() {
                        return true;
                    }

                    @Override
                    public float getSdkRequestPermitsPerSecond() {
                        // Unlimited rate for unit tests to avoid flake in tests due to rate
                        // limiting
                        return -1;
                    }

                    @Override
                    public long getAdSelectionOverallTimeoutMs() {
                        return lenientPerBuyerTimeOutLimit * 3;
                    }
                };

        MockWebServer server = mMockWebServerRule.startMockWebServer(DISPATCHER_V3_BIDDING_LOGIC);
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        List<AdTechIdentifier> participatingBuyers = new ArrayList<>();
        participatingBuyers.add(BUYER_1);
        participatingBuyers.add(BUYER_2);
        participatingBuyers.add(BUYER_3);

        for (int i = 1; i <= largeCACountForBuyer; i++) {
            DBCustomAudience dBCustomAudienceX =
                    createDBCustomAudience(
                            BUYER_3,
                            "-" + i,
                            mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                            bidsForBuyer1,
                            CustomAudienceFixture.VALID_ACTIVATION_TIME,
                            CustomAudienceFixture.VALID_EXPIRATION_TIME,
                            CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI,
                            BUYER_TRUSTED_SIGNAL_URI_PATH);
            mCustomAudienceDao.insertOrOverwriteCustomAudience(
                    dBCustomAudienceX,
                    CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_3),
                    false);
        }

        AdSelectionConfig adSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setCustomAudienceBuyers(participatingBuyers)
                        .setSeller(
                                AdTechIdentifier.fromString(
                                        mMockWebServerRule
                                                .uriForPath(SELLER_DECISION_LOGIC_URI_PATH)
                                                .getHost()))
                        .setDecisionLogicUri(
                                mMockWebServerRule.uriForPath(SELLER_DECISION_LOGIC_URI_PATH))
                        .setTrustedScoringSignalsUri(
                                mMockWebServerRule.uriForPath(SELLER_TRUSTED_SIGNAL_URI_PATH))
                        .build();

        // Create an instance of AdSelection Service with lenient dependencies
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
                        mSpyContext,
                        mAdServicesLoggerMock,
                        flagsWithLenientBuyerBiddingLimits,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mAdSelectionServiceFilter,
                        mAdFilteringFeatureFactory,
                        mConsentManagerMock,
                        mMultiCloudSupportStrategy,
                        mAdSelectionDebugReportDao,
                        mAdIdFetcher,
                        mUnusedKAnonSignJoinFactory,
                        false,
                        mRetryStrategyFactory);

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, adSelectionConfig, CALLER_PACKAGE_NAME);

        assertCallbackIsSuccessful(resultsCallback);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(resultSelectionId));
        assertEquals(
                AD_URI_PREFIX + BUYER_2 + "/ad3",
                resultsCallback.mAdSelectionResponse.getRenderUri().toString());

        int networkRequestCountWithLenientTimeout = server.getRequestCount();

        // Now we run the same Ad selection with tight per buyer timeout limits
        Flags flagsWithTightBuyerBiddingLimits =
                new AdSelectionE2ETestFlags() {
                    @Override
                    public long getAdSelectionBiddingTimeoutPerBuyerMs() {
                        return tightPerBuyerTimeOutLimit;
                    }

                    @Override
                    public boolean getEnforceIsolateMaxHeapSize() {
                        return false;
                    }

                    @Override
                    public boolean getDisableFledgeEnrollmentCheck() {
                        return true;
                    }

                    @Override
                    public float getSdkRequestPermitsPerSecond() {
                        // Unlimited rate for unit tests to avoid flake in tests due to rate
                        // limiting
                        return -1;
                    }

                    @Override
                    public long getAdSelectionOverallTimeoutMs() {
                        return lenientPerBuyerTimeOutLimit * 3;
                    }

                    @Override
                    public int getAdSelectionMaxConcurrentBiddingCount() {
                        return 1;
                    }
                };

        // Create an instance of AdSelection Service with tight dependencies
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
                        mSpyContext,
                        mAdServicesLoggerMock,
                        flagsWithTightBuyerBiddingLimits,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mAdSelectionServiceFilter,
                        mAdFilteringFeatureFactory,
                        mConsentManagerMock,
                        mMultiCloudSupportStrategy,
                        mAdSelectionDebugReportDao,
                        mAdIdFetcher,
                        mUnusedKAnonSignJoinFactory,
                        false,
                        mRetryStrategyFactory);

        resultsCallback =
                invokeSelectAds(mAdSelectionService, adSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        assertCallbackIsSuccessful(resultsCallback);
        resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(resultSelectionId));
        assertEquals(
                AD_URI_PREFIX + BUYER_2 + "/ad3",
                resultsCallback.mAdSelectionResponse.getRenderUri().toString());

        int networkRequestCountWithTightTimeout =
                server.getRequestCount() - networkRequestCountWithLenientTimeout;

        sLogger.v(
                String.format(
                        "Network calls with buyer timeout :%d, network calls with"
                                + " lenient timeout :%d",
                        networkRequestCountWithTightTimeout,
                        networkRequestCountWithLenientTimeout));
        assertTrue(
                String.format(
                        "Network calls with buyer timeout :%d, are not less than network calls with"
                                + " lenient timeout :%d",
                        networkRequestCountWithTightTimeout, networkRequestCountWithLenientTimeout),
                networkRequestCountWithTightTimeout < networkRequestCountWithLenientTimeout);
        verify(mAdServicesLoggerMock, times(2))
                .logRunAdBiddingProcessReportedStats(isA(RunAdBiddingProcessReportedStats.class));
        verify(mAdServicesLoggerMock, times(2))
                .logRunAdScoringProcessReportedStats(isA(RunAdScoringProcessReportedStats.class));
        verify(mAdServicesLoggerMock, times(2))
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        verify(mAdServicesLoggerMock, times(2))
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        geq((int) BINDER_ELAPSED_TIME_MS));
    }

    @Test
    public void testRunAdSelectionScoringTimesOut() throws Exception {
        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(3);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());

        Flags flagsWithSmallerLimits =
                new AdSelectionE2ETestFlags() {
                    @Override
                    public long getAdSelectionScoringTimeoutMs() {
                        return 1500;
                    }

                    @Override
                    public boolean getEnforceIsolateMaxHeapSize() {
                        return false;
                    }

                    @Override
                    public boolean getDisableFledgeEnrollmentCheck() {
                        return true;
                    }

                    @Override
                    public float getSdkRequestPermitsPerSecond() {
                        // Unlimited rate for unit tests to avoid flake in tests due to rate
                        // limiting
                        return -1;
                    }
                };

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
                        mSpyContext,
                        mAdServicesLoggerMock,
                        flagsWithSmallerLimits,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mAdSelectionServiceFilter,
                        mAdFilteringFeatureFactory,
                        mConsentManagerMock,
                        mMultiCloudSupportStrategy,
                        mAdSelectionDebugReportDao,
                        mAdIdFetcher,
                        mUnusedKAnonSignJoinFactory,
                        false,
                        mRetryStrategyFactory);

        String jsWaitMoreThanAllowedForScoring =
                insertJsWait(2 * mFlags.getAdSelectionScoringTimeoutMs());
        String useBidAsScoringWithDelayJs =
                "function scoreAd(ad, bid, auction_config, seller_signals, "
                        + "trusted_scoring_signals, contextual_signal, user_signal, "
                        + "custom_audience_signal) { \n"
                        + jsWaitMoreThanAllowedForScoring
                        + "  return {'status': 0, 'score': bid };\n"
                        + "}";

        // In this case the one buyer's logic takes more than the bidding time limit
        Dispatcher dispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        if (SELLER_DECISION_LOGIC_URI_PATH.equals(request.getPath())) {
                            return new MockResponse().setBody(useBidAsScoringWithDelayJs);
                        } else if ((BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1.toString())
                                        .equals(request.getPath())
                                || (BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2.toString())
                                        .equals(request.getPath())) {
                            return new MockResponse().setBody(READ_BID_FROM_AD_METADATA_JS);
                        } else if (request.getPath().startsWith(BUYER_TRUSTED_SIGNAL_URI_PATH)) {
                            String[] keys =
                                    Uri.parse(request.getPath())
                                            .getQueryParameter(
                                                    DBTrustedBiddingData.QUERY_PARAM_KEYS)
                                            .split(",");
                            Map<String, String> jsonMap = new HashMap<>();
                            for (String key : keys) {
                                jsonMap.put(key, TRUSTED_BIDDING_SIGNALS_SERVER_DATA.get(key));
                            }
                            return new MockResponse().setBody(new JSONObject(jsonMap).toString());
                        }

                        // The seller params vary based on runtime, so we are returning trusted
                        // signals based on correct path prefix
                        if (request.getPath()
                                .startsWith(
                                        SELLER_TRUSTED_SIGNAL_URI_PATH
                                                + SELLER_TRUSTED_SIGNAL_PARAMS)) {
                            return new MockResponse().setBody(TRUSTED_SCORING_SIGNALS.toString());
                        }
                        return new MockResponse().setResponseCode(404);
                    }
                };

        mMockWebServerRule.startMockWebServer(dispatcher);
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2, 15.0);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, mAdSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        Assert.assertFalse(resultsCallback.mIsSuccess);

        FledgeErrorResponse response = resultsCallback.mFledgeErrorResponse;
        verifyErrorMessageIsCorrect(response.getErrorMessage(), SCORING_TIMED_OUT);
        Assert.assertEquals(
                "Error response code mismatch",
                AdServicesStatusUtils.STATUS_TIMEOUT,
                response.getStatusCode());
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(isA(RunAdBiddingProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(isA(RunAdScoringProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_TIMEOUT),
                        geq((int) BINDER_ELAPSED_TIME_MS));
    }

    @Test
    public void testAdSelectionConfigInvalidInput() throws Exception {
        doThrow(new IllegalArgumentException())
                .when(mAdSelectionServiceFilter)
                .filterRequest(
                        mSeller,
                        CALLER_PACKAGE_NAME,
                        true,
                        true,
                        CALLER_UID,
                        AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS,
                        Throttler.ApiKey.FLEDGE_API_SELECT_ADS,
                        DevContext.createForDevOptionsDisabled());

        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());

        AdSelectionConfig invalidAdSelectionConfig =
                AdSelectionConfigFixture.anAdSelectionConfigBuilder()
                        .setSeller(SELLER_VALID)
                        .setDecisionLogicUri(DECISION_LOGIC_URI_INCONSISTENT)
                        .build();

        Mockito.lenient()
                .when(mDevContextFilter.createDevContext())
                .thenReturn(DevContext.createForDevOptionsDisabled());

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, invalidAdSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        assertFalse(resultsCallback.mIsSuccess);

        FledgeErrorResponse response = resultsCallback.mFledgeErrorResponse;
        assertEquals(
                "Error response code mismatch", STATUS_INVALID_ARGUMENT, response.getStatusCode());
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_INVALID_ARGUMENT),
                        geq((int) BINDER_ELAPSED_TIME_MS));
    }

    @Test
    public void testRunAdSelectionMissingBiddingSignalsFailure() throws Exception {
        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(2);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());
        // Create a dispatcher without buyer trusted Signal end point
        Dispatcher missingBiddingSignalsDispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        if (SELLER_DECISION_LOGIC_URI_PATH.equals(request.getPath())) {
                            return new MockResponse().setBody(USE_BID_AS_SCORE_JS);
                        } else if ((BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1.toString())
                                        .equals(request.getPath())
                                || (BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2.toString())
                                        .equals(request.getPath())) {
                            return new MockResponse().setBody(READ_BID_FROM_AD_METADATA_JS);
                        }

                        // The seller params vary based on runtime, so we are returning trusted
                        // signals based on correct path prefix
                        if (request.getPath()
                                .startsWith(
                                        SELLER_TRUSTED_SIGNAL_URI_PATH
                                                + SELLER_TRUSTED_SIGNAL_PARAMS)) {
                            return new MockResponse().setBody(TRUSTED_SCORING_SIGNALS.toString());
                        }
                        return new MockResponse().setResponseCode(404);
                    }
                };

        mMockWebServerRule.startMockWebServer(missingBiddingSignalsDispatcher);
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, mAdSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        assertCallbackFailed(resultsCallback);
        verifyErrorMessageIsCorrect(
                resultsCallback.mFledgeErrorResponse.getErrorMessage(),
                ERROR_NO_VALID_BIDS_OR_CONTEXTUAL_ADS_FOR_SCORING);
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(isA(RunAdBiddingProcessReportedStats.class));
        verify(mAdServicesLoggerMock, never()).logRunAdScoringProcessReportedStats(any());
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_INTERNAL_ERROR),
                        geq((int) BINDER_ELAPSED_TIME_MS));
    }

    @Test
    @FlakyTest(bugId = 315521295)
    public void testRunAdSelectionMissingScoringSignalsFailure() throws Exception {
        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(3);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());
        // Create a dispatcher without buyer trusted Signal end point
        Dispatcher missingScoringSignalsDispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {
                        if (SELLER_DECISION_LOGIC_URI_PATH.equals(request.getPath())) {
                            return new MockResponse().setBody(USE_BID_AS_SCORE_JS);
                        } else if ((BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1.toString())
                                        .equals(request.getPath())
                                || (BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2.toString())
                                        .equals(request.getPath())) {
                            return new MockResponse().setBody(READ_BID_FROM_AD_METADATA_JS);
                        } else if (request.getPath().startsWith(BUYER_TRUSTED_SIGNAL_URI_PATH)) {
                            String[] keys =
                                    Uri.parse(request.getPath())
                                            .getQueryParameter(
                                                    DBTrustedBiddingData.QUERY_PARAM_KEYS)
                                            .split(",");
                            Map<String, String> jsonMap = new HashMap<>();
                            for (String key : keys) {
                                jsonMap.put(key, TRUSTED_BIDDING_SIGNALS_SERVER_DATA.get(key));
                            }
                            return new MockResponse().setBody(new JSONObject(jsonMap).toString());
                        }
                        return new MockResponse().setResponseCode(404);
                    }
                };

        mMockWebServerRule.startMockWebServer(missingScoringSignalsDispatcher);
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, mAdSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        assertCallbackFailed(resultsCallback);
        verifyErrorMessageIsCorrect(
                resultsCallback.mFledgeErrorResponse.getErrorMessage(),
                MISSING_TRUSTED_SCORING_SIGNALS);
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(isA(RunAdBiddingProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(isA(RunAdScoringProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_INTERNAL_ERROR),
                        geq((int) BINDER_ELAPSED_TIME_MS));
    }

    @Test
    public void testRunAdSelectionMissingPartialBiddingSignalsSuccess() throws Exception {
        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(3);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());
        // Create a dispatcher with valid end points
        Dispatcher missingBiddingSignalsDispatcher =
                new Dispatcher() {
                    @Override
                    public MockResponse dispatch(RecordedRequest request) {

                        if (SELLER_DECISION_LOGIC_URI_PATH.equals(request.getPath())) {
                            return new MockResponse().setBody(USE_BID_AS_SCORE_JS);
                        } else if ((BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1.toString())
                                        .equals(request.getPath())
                                || (BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2.toString())
                                        .equals(request.getPath())) {
                            return new MockResponse().setBody(READ_BID_FROM_AD_METADATA_JS);
                        } else if (request.getPath().startsWith(BUYER_TRUSTED_SIGNAL_URI_PATH)) {
                            String[] keys =
                                    Uri.parse(request.getPath())
                                            .getQueryParameter(
                                                    DBTrustedBiddingData.QUERY_PARAM_KEYS)
                                            .split(",");
                            Map<String, String> jsonMap = new HashMap<>();
                            for (String key : keys) {
                                jsonMap.put(key, TRUSTED_BIDDING_SIGNALS_SERVER_DATA.get(key));
                            }
                            return new MockResponse().setBody(new JSONObject(jsonMap).toString());
                        }

                        // The seller params vary based on runtime, so we are returning trusted
                        // signals based on correct path prefix
                        if (request.getPath()
                                .startsWith(
                                        SELLER_TRUSTED_SIGNAL_URI_PATH
                                                + SELLER_TRUSTED_SIGNAL_PARAMS)) {
                            return new MockResponse().setBody(TRUSTED_SCORING_SIGNALS.toString());
                        }
                        return new MockResponse().setResponseCode(404);
                    }
                };

        mMockWebServerRule.startMockWebServer(missingBiddingSignalsDispatcher);
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        // Invalid trusted bidding signal path for buyer 2
                        mMockWebServerRule.uriForPath(""),
                        bidsForBuyer2,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, mAdSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        assertCallbackIsSuccessful(resultsCallback);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(resultSelectionId));
        // Given buyer 2 will be excluded from bidding for missing signals, Buyer 1 : Ad 2 will win
        assertEquals(
                AD_URI_PREFIX + BUYER_1 + "/ad2",
                resultsCallback.mAdSelectionResponse.getRenderUri().toString());
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(isA(RunAdBiddingProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(isA(RunAdScoringProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        geq((int) BINDER_ELAPSED_TIME_MS));
    }

    @Test
    public void testRunAdSelectionFailsWithInvalidPackageName() throws Exception {
        String invalidPackageName = CALLER_PACKAGE_NAME + "invalidPackageName";

        doThrow(new FilterException(new FledgeAuthorizationFilter.CallerMismatchException()))
                .when(mAdSelectionServiceFilter)
                .filterRequest(
                        mSeller,
                        invalidPackageName,
                        true,
                        true,
                        CALLER_UID,
                        AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS,
                        Throttler.ApiKey.FLEDGE_API_SELECT_ADS,
                        DevContext.createForDevOptionsDisabled());

        // Bypass the permission check since it's enforced before the package name check
        doNothing()
                .when(mFledgeAuthorizationFilterSpy)
                .assertAppDeclaredAnyPermission(
                        mSpyContext,
                        invalidPackageName,
                        AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS,
                        AdSelectionServiceImpl.PERMISSIONS_SET);

        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());

        mMockWebServerRule.startMockWebServer(mDispatcher);
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, mAdSelectionConfig, invalidPackageName);
        runAdSelectionProcessLoggerLatch.await();
        Assert.assertFalse(resultsCallback.mIsSuccess);

        FledgeErrorResponse response = resultsCallback.mFledgeErrorResponse;
        assertEquals("Error response code mismatch", STATUS_UNAUTHORIZED, response.getStatusCode());

        verifyErrorMessageIsCorrect(
                resultsCallback.mFledgeErrorResponse.getErrorMessage(),
                String.format(
                        AdSelectionRunner.AD_SELECTION_ERROR_PATTERN,
                        AdSelectionRunner.ERROR_AD_SELECTION_FAILURE,
                        AdServicesStatusUtils
                                .SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_ON_BEHALF_ERROR_MESSAGE));
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));

        // Confirm a duplicate log entry does not exist.
        // AdSelectionServiceFilter ensures the failing assertion is logged internally.
        verify(mAdServicesLoggerMock, never())
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_UNAUTHORIZED),
                        geq(0));

        verify(mFledgeAuthorizationFilterSpy)
                .assertAppDeclaredAnyPermission(
                        mSpyContext,
                        invalidPackageName,
                        AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS,
                        AdSelectionServiceImpl.PERMISSIONS_SET);
    }

    @Test
    public void testRunAdSelectionFailsWhenAppCannotUsePPApi() throws Exception {
        doThrow(new FilterException(new FledgeAuthorizationFilter.AdTechNotAllowedException()))
                .when(mAdSelectionServiceFilter)
                .filterRequest(
                        mSeller,
                        CALLER_PACKAGE_NAME,
                        true,
                        true,
                        CALLER_UID,
                        AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS,
                        Throttler.ApiKey.FLEDGE_API_SELECT_ADS,
                        DevContext.createForDevOptionsDisabled());

        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());

        mMockWebServerRule.startMockWebServer(mDispatcher);
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, mAdSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        Assert.assertFalse(resultsCallback.mIsSuccess);

        FledgeErrorResponse response = resultsCallback.mFledgeErrorResponse;
        assertEquals(
                "Error response code mismatch",
                STATUS_CALLER_NOT_ALLOWED,
                response.getStatusCode());

        verifyErrorMessageIsCorrect(
                resultsCallback.mFledgeErrorResponse.getErrorMessage(),
                String.format(
                        AdSelectionRunner.AD_SELECTION_ERROR_PATTERN,
                        AdSelectionRunner.ERROR_AD_SELECTION_FAILURE,
                        AdServicesStatusUtils.SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_ERROR_MESSAGE));
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));

        // Confirm a duplicate log entry does not exist.
        // AdSelectionServiceFilter ensures the failing assertion is logged internally.
        verify(mAdServicesLoggerMock, never())
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_CALLER_NOT_ALLOWED),
                        geq(0));
    }

    @Test
    public void testRunAdSelectionFailsWhenAdTechFailsEnrollmentCheck() throws Exception {
        Flags flagsWithEnrollmentCheckEnabled =
                new AdSelectionE2ETestFlags() {
                    @Override
                    public boolean getDisableFledgeEnrollmentCheck() {
                        return false;
                    }
                };

        doReturn(flagsWithEnrollmentCheckEnabled).when(FlagsFactory::getFlags);

        doThrow(new FilterException(new FledgeAuthorizationFilter.AdTechNotAllowedException()))
                .when(mAdSelectionServiceFilter)
                .filterRequest(
                        mSeller,
                        CALLER_PACKAGE_NAME,
                        true,
                        true,
                        CALLER_UID,
                        AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS,
                        Throttler.ApiKey.FLEDGE_API_SELECT_ADS,
                        DevContext.createForDevOptionsDisabled());

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
                        mSpyContext,
                        mAdServicesLoggerMock,
                        flagsWithEnrollmentCheckEnabled,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mAdSelectionServiceFilter,
                        mAdFilteringFeatureFactory,
                        mConsentManagerMock,
                        mMultiCloudSupportStrategy,
                        mAdSelectionDebugReportDao,
                        mAdIdFetcher,
                        mUnusedKAnonSignJoinFactory,
                        false,
                        mRetryStrategyFactory);

        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());

        mMockWebServerRule.startMockWebServer(mDispatcher);
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, mAdSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        Assert.assertFalse(resultsCallback.mIsSuccess);

        FledgeErrorResponse response = resultsCallback.mFledgeErrorResponse;
        assertEquals(
                "Error response code mismatch",
                STATUS_CALLER_NOT_ALLOWED,
                response.getStatusCode());

        verifyErrorMessageIsCorrect(
                resultsCallback.mFledgeErrorResponse.getErrorMessage(),
                String.format(
                        AdSelectionRunner.AD_SELECTION_ERROR_PATTERN,
                        AdSelectionRunner.ERROR_AD_SELECTION_FAILURE,
                        AdServicesStatusUtils.SECURITY_EXCEPTION_CALLER_NOT_ALLOWED_ERROR_MESSAGE));
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));

        // Confirm a duplicate log entry does not exist.
        // AdSelectionServiceFilter ensures the failing assertion is logged internally.
        verify(mAdServicesLoggerMock, never())
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_CALLER_NOT_ALLOWED),
                        geq(0));
    }

    @Test
    @FlakyTest(bugId = 304764127)
    public void testRunAdSelectionThrottledSubsequentCallFailure() throws Exception {
        doReturn(FlagsFactory.getFlagsForTest()).when(FlagsFactory::getFlags);

        class FlagsWithThrottling extends AdSelectionE2ETestFlags implements Flags {
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

            // Testing the default throttling limit
            @Override
            public float getSdkRequestPermitsPerSecond() {
                return 1;
            }
        }

        Throttler.destroyExistingThrottler();
        Flags throttlingFlags = new FlagsWithThrottling();
        AdSelectionServiceImpl adSelectionServiceWithThrottling =
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
                        mSpyContext,
                        mAdServicesLoggerMock,
                        throttlingFlags,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mAdSelectionServiceFilter,
                        mAdFilteringFeatureFactory,
                        mConsentManagerMock,
                        mMultiCloudSupportStrategy,
                        mAdSelectionDebugReportDao,
                        mAdIdFetcher,
                        mUnusedKAnonSignJoinFactory,
                        false,
                        mRetryStrategyFactory);

        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(1);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());

        mMockWebServerRule.startMockWebServer(mDispatcher);
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        // First call to Ad Selection should succeed
        AdSelectionTestCallback resultsCallbackFirstCall =
                invokeSelectAds(
                        adSelectionServiceWithThrottling, mAdSelectionConfig, CALLER_PACKAGE_NAME);

        doThrow(new FilterException(new LimitExceededException(RATE_LIMIT_REACHED_ERROR_MESSAGE)))
                .when(mAdSelectionServiceFilter)
                .filterRequest(
                        mSeller,
                        CALLER_PACKAGE_NAME,
                        true,
                        true,
                        CALLER_UID,
                        AdServicesStatsLog.AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS,
                        Throttler.ApiKey.FLEDGE_API_SELECT_ADS,
                        DevContext.createForDevOptionsDisabled());

        // Immediately made subsequent call should fail
        AdSelectionTestCallback resultsCallbackSecondCall =
                invokeSelectAds(
                        adSelectionServiceWithThrottling, mAdSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        assertCallbackIsSuccessful(resultsCallbackFirstCall);
        long resultSelectionId = resultsCallbackFirstCall.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(resultSelectionId));
        assertEquals(
                AD_URI_PREFIX + BUYER_2 + "/ad3",
                resultsCallbackFirstCall.mAdSelectionResponse.getRenderUri().toString());

        assertCallbackFailed(resultsCallbackSecondCall);

        FledgeErrorResponse response = resultsCallbackSecondCall.mFledgeErrorResponse;
        assertEquals(
                "Error response code mismatch",
                AdServicesStatusUtils.STATUS_RATE_LIMIT_REACHED,
                response.getStatusCode());

        verifyErrorMessageIsCorrect(
                resultsCallbackSecondCall.mFledgeErrorResponse.getErrorMessage(),
                String.format(
                        AdSelectionRunner.AD_SELECTION_ERROR_PATTERN,
                        AdSelectionRunner.ERROR_AD_SELECTION_FAILURE,
                        RATE_LIMIT_REACHED_ERROR_MESSAGE));
        resetThrottlerToNoRateLimits();
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

    @Test
    public void testRunAdSelectionSucceedsWhenAdTechPassesEnrollmentCheck() throws Exception {
        Flags flagsWithEnrollmentCheckEnabled =
                new AdSelectionE2ETestFlags() {
                    @Override
                    public boolean getDisableFledgeEnrollmentCheck() {
                        return false;
                    }
                };

        doReturn(flagsWithEnrollmentCheckEnabled).when(FlagsFactory::getFlags);

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
                        mSpyContext,
                        mAdServicesLoggerMock,
                        flagsWithEnrollmentCheckEnabled,
                        CallingAppUidSupplierProcessImpl.create(),
                        mFledgeAuthorizationFilterSpy,
                        mAdSelectionServiceFilter,
                        mAdFilteringFeatureFactory,
                        mConsentManagerMock,
                        mMultiCloudSupportStrategy,
                        mAdSelectionDebugReportDao,
                        mAdIdFetcher,
                        mUnusedKAnonSignJoinFactory,
                        false,
                        mRetryStrategyFactory);

        // Logger calls come after the callback is returned
        CountDownLatch runAdSelectionProcessLoggerLatch = new CountDownLatch(3);
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(any());
        doAnswer(
                        unusedInvocation -> {
                            runAdSelectionProcessLoggerLatch.countDown();
                            return null;
                        })
                .when(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(any());

        mMockWebServerRule.startMockWebServer(mDispatcher);
        List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
        List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

        DBCustomAudience dBCustomAudienceForBuyer1 =
                createDBCustomAudience(
                        BUYER_1,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                        bidsForBuyer1,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);
        DBCustomAudience dBCustomAudienceForBuyer2 =
                createDBCustomAudience(
                        BUYER_2,
                        mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                        bidsForBuyer2,
                        BUYER_TRUSTED_SIGNAL_URI_PATH);

        // Populating the Custom Audience DB
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer1,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                false);
        mCustomAudienceDao.insertOrOverwriteCustomAudience(
                dBCustomAudienceForBuyer2,
                CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                false);

        AdSelectionTestCallback resultsCallback =
                invokeSelectAds(mAdSelectionService, mAdSelectionConfig, CALLER_PACKAGE_NAME);
        runAdSelectionProcessLoggerLatch.await();
        assertCallbackIsSuccessful(resultsCallback);
        long resultSelectionId = resultsCallback.mAdSelectionResponse.getAdSelectionId();
        assertTrue(mAdSelectionEntryDaoSpy.doesAdSelectionIdExist(resultSelectionId));
        assertEquals(
                AD_URI_PREFIX + BUYER_2 + "/ad3",
                resultsCallback.mAdSelectionResponse.getRenderUri().toString());
        verify(mAdServicesLoggerMock)
                .logRunAdBiddingProcessReportedStats(isA(RunAdBiddingProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdScoringProcessReportedStats(isA(RunAdScoringProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logRunAdSelectionProcessReportedStats(
                        isA(RunAdSelectionProcessReportedStats.class));
        verify(mAdServicesLoggerMock)
                .logFledgeApiCallStats(
                        eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                        eq(TEST_PACKAGE_NAME),
                        eq(STATUS_SUCCESS),
                        geq((int) BINDER_ELAPSED_TIME_MS));
    }

    @Test
    public void testRunAdSelection_webViewNotInstalled_failsGracefully() throws Exception {
        // A null package means WebView is not installed
        doReturn(null).when(WebView::getCurrentWebViewPackage);

        // Shut down any running JSScriptEngine to ensure the new singleton gets picked up
        JSScriptEngine.getInstance(mSpyContext, LoggerFactory.getFledgeLogger()).shutdown();

        try {
            // Create a new local service impl so that the WebView stub takes effect
            AdSelectionServiceImpl adSelectionServiceImpl =
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
                            mSpyContext,
                            mAdServicesLoggerMock,
                            mFlags,
                            CallingAppUidSupplierProcessImpl.create(),
                            mFledgeAuthorizationFilterSpy,
                            mAdSelectionServiceFilter,
                            mAdFilteringFeatureFactory,
                            mConsentManagerMock,
                            mMultiCloudSupportStrategy,
                            mAdSelectionDebugReportDao,
                            mAdIdFetcher,
                            mUnusedKAnonSignJoinFactory,
                            false,
                            mRetryStrategyFactory);

            mMockWebServerRule.startMockWebServer(mDispatcher);
            List<Double> bidsForBuyer1 = ImmutableList.of(1.1, 2.2);
            List<Double> bidsForBuyer2 = ImmutableList.of(4.5, 6.7, 10.0);

            DBCustomAudience dBCustomAudienceForBuyer1 =
                    createDBCustomAudience(
                            BUYER_1,
                            mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_1),
                            bidsForBuyer1,
                            BUYER_TRUSTED_SIGNAL_URI_PATH);
            DBCustomAudience dBCustomAudienceForBuyer2 =
                    createDBCustomAudience(
                            BUYER_2,
                            mMockWebServerRule.uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2),
                            bidsForBuyer2,
                            BUYER_TRUSTED_SIGNAL_URI_PATH);

            // Populating the Custom Audience DB
            mCustomAudienceDao.insertOrOverwriteCustomAudience(
                    dBCustomAudienceForBuyer1,
                    CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_1),
                    false);
            mCustomAudienceDao.insertOrOverwriteCustomAudience(
                    dBCustomAudienceForBuyer2,
                    CustomAudienceFixture.getValidDailyUpdateUriByBuyer(BUYER_2),
                    false);

            // Ad selection should fail gracefully and not crash
            AdSelectionTestCallback resultsCallback =
                    invokeSelectAds(
                            adSelectionServiceImpl, mAdSelectionConfig, CALLER_PACKAGE_NAME);
            assertCallbackFailed(resultsCallback);
            assertWithMessage("Error code")
                    .that(resultsCallback.mFledgeErrorResponse.getStatusCode())
                    .isEqualTo(STATUS_INTERNAL_ERROR);

            verify(mAdServicesLoggerMock)
                    .logFledgeApiCallStats(
                            eq(AD_SERVICES_API_CALLED__API_NAME__SELECT_ADS),
                            eq(TEST_PACKAGE_NAME),
                            eq(STATUS_INTERNAL_ERROR),
                            geq((int) BINDER_ELAPSED_TIME_MS));
        } finally {
            // Shut down any running JSScriptEngine to ensure the new singleton gets picked up
            JSScriptEngine.getInstance(mSpyContext, LoggerFactory.getFledgeLogger()).shutdown();
        }
    }

    /**
     * @param buyer The name of the buyer for this Custom Audience
     * @param biddingUri path from where the bidding logic for this CA can be fetched from
     * @param bids these bids, are added to its metadata. Our JS logic then picks this value and
     *     creates ad with the provided value as bid
     * @param activationTime is the activation time of the Custom Audience
     * @param expirationTime is the expiration time of the Custom Audience
     * @param lastUpdateTime is the last time of the Custom Audience ads and bidding data got
     *     updated
     * @param trustedSignalUriPath uri path for trusted signals
     * @return a real Custom Audience object that can be persisted and used in bidding and scoring
     */
    private DBCustomAudience createDBCustomAudience(
            final AdTechIdentifier buyer,
            final String nameSuffix,
            final Uri biddingUri,
            List<Double> bids,
            Instant activationTime,
            Instant expirationTime,
            Instant lastUpdateTime,
            String trustedSignalUriPath) {

        // Generate ads for with bids provided
        List<DBAdData> ads = new ArrayList<>();

        // Create ads with the buyer name and bid number as the ad URI
        // Add the bid value to the metadata
        for (int i = 0; i < bids.size(); i++) {
            // TODO(b/266015983) Add real data
            ads.add(
                    new DBAdData(
                            Uri.parse(AD_URI_PREFIX + buyer + "/ad" + (i + 1)),
                            "{\"result\":" + bids.get(i) + "}",
                            Collections.EMPTY_SET,
                            null,
                            null));
        }

        return new DBCustomAudience.Builder()
                .setOwner(buyer + CustomAudienceFixture.VALID_OWNER)
                .setBuyer(buyer)
                .setName(buyer.toString() + CustomAudienceFixture.VALID_NAME + nameSuffix)
                .setActivationTime(activationTime)
                .setExpirationTime(expirationTime)
                .setCreationTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                .setLastAdsAndBiddingDataUpdatedTime(lastUpdateTime)
                .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                .setTrustedBiddingData(
                        new DBTrustedBiddingData.Builder()
                                .setUri(mMockWebServerRule.uriForPath(trustedSignalUriPath))
                                .setKeys(TrustedBiddingDataFixture.getValidTrustedBiddingKeys())
                                .build())
                .setBiddingLogicUri(biddingUri)
                .setAds(ads)
                .build();
    }

    /**
     * @param buyer The name of the buyer for this Custom Audience
     * @param biddingUri path from where the bidding logic for this CA can be fetched from
     * @param bids these bids, are added to its metadata. Our JS logic then picks this value and
     *     creates ad with the provided value as bid
     * @param activationTime is the activation time of the Custom Audience
     * @param expirationTime is the expiration time of the Custom Audience
     * @param lastUpdateTime is the last time of the Custom Audience ads and bidding data got
     *     updated
     * @param adCost The click cost of the ad
     * @return a real Custom Audience object that can be persisted and used in bidding and scoring
     */
    private DBCustomAudience createDBCustomAudienceWithAdCost(
            final AdTechIdentifier buyer,
            final String nameSuffix,
            final Uri biddingUri,
            List<Double> bids,
            Instant activationTime,
            Instant expirationTime,
            Instant lastUpdateTime,
            double adCost) {

        // Generate ads for with bids provided
        List<DBAdData> ads = new ArrayList<>();

        // Create ads with the buyer name and bid number as the ad URI
        // Add the bid value to the metadata
        for (int i = 0; i < bids.size(); i++) {
            // TODO(b/266015983) Add real data
            ads.add(
                    new DBAdData(
                            Uri.parse(AD_URI_PREFIX + buyer + "/ad" + (i + 1)),
                            "{\"result\":" + bids.get(i) + ",\"adCost\":" + adCost + "}",
                            Collections.EMPTY_SET,
                            null,
                            null));
        }

        return new DBCustomAudience.Builder()
                .setOwner(buyer + CustomAudienceFixture.VALID_OWNER)
                .setBuyer(buyer)
                .setName(buyer.toString() + CustomAudienceFixture.VALID_NAME + nameSuffix)
                .setActivationTime(activationTime)
                .setExpirationTime(expirationTime)
                .setCreationTime(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                .setLastAdsAndBiddingDataUpdatedTime(lastUpdateTime)
                .setUserBiddingSignals(CustomAudienceFixture.VALID_USER_BIDDING_SIGNALS)
                .setTrustedBiddingData(
                        new DBTrustedBiddingData.Builder()
                                .setUri(
                                        mMockWebServerRule.uriForPath(
                                                BUYER_TRUSTED_SIGNAL_URI_PATH))
                                .setKeys(TrustedBiddingDataFixture.getValidTrustedBiddingKeys())
                                .build())
                .setBiddingLogicUri(biddingUri)
                .setAds(ads)
                .build();
    }

    /**
     * @param buyer The name of the buyer for this Custom Audience
     * @param biddingUri path from where the bidding logic for this CA can be fetched from
     * @param bids these bids, are added to its metadata. Our JS logic then picks this value and
     *     creates ad with the provided value as bid
     * @param trustedSignalUriPath uri path for trusted signals
     * @return a real Custom Audience object that can be persisted and used in bidding and scoring
     */
    private DBCustomAudience createDBCustomAudience(
            final AdTechIdentifier buyer,
            final Uri biddingUri,
            List<Double> bids,
            String trustedSignalUriPath) {
        return createDBCustomAudience(
                buyer,
                DEFAULT_CUSTOM_AUDIENCE_NAME_SUFFIX,
                biddingUri,
                bids,
                CustomAudienceFixture.VALID_ACTIVATION_TIME,
                CustomAudienceFixture.VALID_EXPIRATION_TIME,
                CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI,
                trustedSignalUriPath);
    }

    /**
     * @param buyer The name of the buyer for this Custom Audience
     * @param biddingUri path from where the bidding logic for this CA can be fetched from
     * @param bids these bids, are added to its metadata. Our JS logic then picks this value and
     *     creates ad with the provided value as bid
     * @param adCost The click cost of the ad
     * @return a real Custom Audience object that can be persisted and used in bidding and scoring
     */
    private DBCustomAudience createDBCustomAudienceWithAdCost(
            final AdTechIdentifier buyer, final Uri biddingUri, List<Double> bids, double adCost) {
        return createDBCustomAudienceWithAdCost(
                buyer,
                DEFAULT_CUSTOM_AUDIENCE_NAME_SUFFIX,
                biddingUri,
                bids,
                CustomAudienceFixture.VALID_ACTIVATION_TIME,
                CustomAudienceFixture.VALID_EXPIRATION_TIME,
                CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI,
                adCost);
    }

    private Map<AdTechIdentifier, SignedContextualAds> createUnauthenticatedContextualAds() {
        Map<AdTechIdentifier, SignedContextualAds> authenticatedContextualAds =
                createContextualAds();
        Map<AdTechIdentifier, SignedContextualAds> unauthenticatedContextualAds = new HashMap<>();
        byte[] invalidSignatures = new byte[] {1, 2, 3};
        for (Map.Entry<AdTechIdentifier, SignedContextualAds> buyersBundle :
                authenticatedContextualAds.entrySet()) {
            AdTechIdentifier buyer = buyersBundle.getKey();
            SignedContextualAds ads = buyersBundle.getValue();
            unauthenticatedContextualAds.put(
                    buyer,
                    new SignedContextualAds.Builder(ads).setSignature(invalidSignatures).build());
        }
        return unauthenticatedContextualAds;
    }

    private Map<AdTechIdentifier, SignedContextualAds> createContextualAds() {
        Map<AdTechIdentifier, SignedContextualAds> buyerContextualAds = new HashMap<>();

        // In order to meet ETLd+1 requirements creating Contextual ads with MockWebserver's host
        AdTechIdentifier buyer2 =
                AdTechIdentifier.fromString(
                        mMockWebServerRule
                                .uriForPath(BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2)
                                .getHost());
        SignedContextualAds contextualAds2 =
                SignedContextualAdsFixture.signContextualAds(
                        SignedContextualAdsFixture.aContextualAdsWithEmptySignatureBuilder(
                                        buyer2, ImmutableList.of(100.0, 200.0, 300.0, 400.0, 500.0))
                                .setDecisionLogicUri(
                                        mMockWebServerRule.uriForPath(
                                                BUYER_BIDDING_LOGIC_URI_PATH + BUYER_2)));
        buyerContextualAds.put(buyer2, contextualAds2);
        persistEncryptionKeyInDb(buyerContextualAds);
        return buyerContextualAds;
    }

    private void persistEncryptionKeyInDb(
            Map<AdTechIdentifier, SignedContextualAds> buyerContextualAds) {
        for (AdTechIdentifier adTech : buyerContextualAds.keySet()) {
            String enrollmentId = adTech + "-enrollment-id";
            String sourceRegistration = CommonFixture.getUri(adTech, "/source").toString();
            String triggerRegistration = CommonFixture.getUri(adTech, "/trigger").toString();
            String attributionReporting = CommonFixture.getUri(adTech, "/attrReport").toString();
            String responseBasedRegistration = CommonFixture.getUri(adTech, "/response").toString();
            String encryptionKeyUrl = CommonFixture.getUri(adTech, "/keys").toString();
            mEnrollmentDao.insert(
                    ENROLLMENT_DATA1
                            .cloneToBuilder()
                            .setEnrollmentId(enrollmentId)
                            .setAttributionSourceRegistrationUrl(sourceRegistration)
                            .setAttributionTriggerRegistrationUrl(triggerRegistration)
                            .setAttributionReportingUrl(attributionReporting)
                            .setRemarketingResponseBasedRegistrationUrl(responseBasedRegistration)
                            .setEncryptionKeyUrl(encryptionKeyUrl)
                            .build());
            Uri reportingOriginUrl = CommonFixture.getUri(adTech, "/reportingOriginUrl");
            mEncryptionKeyDao.insert(
                    ENCRYPTION_KEY1
                            .cloneToBuilder()
                            .setEnrollmentId(enrollmentId)
                            .setKeyType(EncryptionKey.KeyType.SIGNING)
                            .setEncryptionKeyUrl(encryptionKeyUrl)
                            .setProtocolType(EncryptionKey.ProtocolType.ECDSA)
                            .setBody(PUBLIC_TEST_KEY_STRING)
                            .setReportingOrigin(reportingOriginUrl)
                            .build());
        }
    }

    private void verifyErrorMessageIsCorrect(
            final String actualErrorMassage, final String expectedErrorReason) {
        assertTrue(
                String.format(
                        Locale.ENGLISH,
                        "Actual error [%s] does not begin with [%s]",
                        actualErrorMassage,
                        ERROR_AD_SELECTION_FAILURE),
                actualErrorMassage.startsWith(ERROR_AD_SELECTION_FAILURE));
        assertTrue(
                String.format(
                        Locale.ENGLISH,
                        "Actual error [%s] does not contain expected message: [%s]",
                        actualErrorMassage,
                        expectedErrorReason),
                actualErrorMassage.contains(expectedErrorReason));
    }

    private AdSelectionTestCallback invokeSelectAds(
            AdSelectionServiceImpl adSelectionService,
            AdSelectionConfig adSelectionConfig,
            String callerPackageName)
            throws InterruptedException {

        CountDownLatch countdownLatch = new CountDownLatch(1);
        AdSelectionTestCallback adSelectionTestCallback =
                new AdSelectionTestCallback(countdownLatch);

        AdSelectionInput input =
                new AdSelectionInput.Builder()
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(callerPackageName)
                        .build();

        adSelectionService.selectAds(input, mMockCallerMetadata, adSelectionTestCallback);
        adSelectionTestCallback.mCountDownLatch.await();
        return adSelectionTestCallback;
    }

    private ReportImpressionTestCallback invokeReporting(
            long adSelectionId,
            AdSelectionServiceImpl adSelectionService,
            AdSelectionConfig adSelectionConfig,
            String callerPackageName)
            throws InterruptedException {

        CountDownLatch countdownLatch = new CountDownLatch(1);
        ReportImpressionTestCallback reportImpressionCallback =
                new ReportImpressionTestCallback(countdownLatch);

        ReportImpressionInput input =
                new ReportImpressionInput.Builder()
                        .setAdSelectionId(adSelectionId)
                        .setAdSelectionConfig(adSelectionConfig)
                        .setCallerPackageName(callerPackageName)
                        .build();

        adSelectionService.reportImpression(input, reportImpressionCallback);
        reportImpressionCallback.mCountDownLatch.await();
        return reportImpressionCallback;
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

        @Override
        public String toString() {
            return "AdSelectionTestCallback{"
                    + "mCountDownLatch="
                    + mCountDownLatch
                    + ", mIsSuccess="
                    + mIsSuccess
                    + ", mAdSelectionResponse="
                    + mAdSelectionResponse
                    + ", mFledgeErrorResponse="
                    + mFledgeErrorResponse
                    + '}';
        }
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

    private static class AdSelectionE2ETestFlags implements Flags {
        private final long mBiddingLogicVersion;

        private final boolean mDebugReportingEnabled;

        AdSelectionE2ETestFlags() {
            this(JsVersionRegister.BUYER_BIDDING_LOGIC_VERSION_VERSION_3, false);
        }

        AdSelectionE2ETestFlags(long biddingLogicVersion, boolean enableDebugReporting) {
            mBiddingLogicVersion = biddingLogicVersion;
            mDebugReportingEnabled = enableDebugReporting;
        }

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
        public boolean getFledgeOnDeviceAuctionKillSwitch() {
            return false;
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
        public int getFledgeBackgroundFetchNetworkConnectTimeoutMs() {
            return EXTENDED_FLEDGE_BACKGROUND_FETCH_NETWORK_CONNECT_TIMEOUT_MS;
        }

        @Override
        public int getFledgeBackgroundFetchNetworkReadTimeoutMs() {
            return EXTENDED_FLEDGE_BACKGROUND_FETCH_NETWORK_READ_TIMEOUT_MS;
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
        public long getFledgeAdSelectionBiddingLogicJsVersion() {
            return mBiddingLogicVersion;
        }

        @Override
        public boolean getFledgeAdSelectionContextualAdsEnabled() {
            return true;
        }

        @Override
        public boolean getFledgeAdSelectionPrebuiltUriEnabled() {
            return true;
        }

        @Override
        public boolean getFledgeCpcBillingEnabled() {
            return false;
        }

        @Override
        public boolean getFledgeEventLevelDebugReportingEnabled() {
            return mDebugReportingEnabled;
        }

        @Override
        public boolean getFledgeEventLevelDebugReportSendImmediately() {
            return false;
        }
    }
}
