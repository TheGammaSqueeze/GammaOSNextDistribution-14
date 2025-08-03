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

package com.android.adservices.service.profiling;

import android.annotation.NonNull;
import android.os.Trace;

import com.android.adservices.LogUtil;

import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;

/** Utility class providing methods for using {@link android.os.Trace}. */
public final class Tracing {

    public static final String FILTERER_FILTER_CA = "AdFilterer#FilterCustomAudiences";
    public static final String FILTERER_FOR_EACH_CA = "AdFilterer#ForEachCustomAudience";
    public static final String FILTERER_FOR_EACH_AD = "AdFilterer#ForEachAd";
    public static final String FILTERER_FREQUENCY_CAP = "AdFilterer#doesAdPassFrequencyCapFilters";
    public static final String FILTERER_FREQUENCY_CAP_WIN =
            "AdFilterer#doesAdPassFrequencyCapFiltersForWinType";
    public static final String FREQUENCY_CAP_GET_NUM_EVENTS_CA =
            "FrequencyCapDao#getNumEventsForCustomAudienceAfterTime";
    public static final String FREQUENCY_CAP_GET_NUM_EVENTS_BUYER =
            "FrequencyCapDao#getNumEventsForBuyerAfterTime";
    public static final String FILTERER_FREQUENCY_CAP_NON_WIN =
            "AdFilterer#doesAdPassFrequencyCapFiltersForNonWinType";
    public static final String FILTERER_FILTER_CONTEXTUAL = "AdFilterer#FilterContextualAds";
    public static final String RUN_AD_SELECTION = "RunOnDeviceAdSelection";
    public static final String PERSIST_AD_SELECTION = "PersistOnDeviceAdSelection";
    public static final String GET_BUYERS_CUSTOM_AUDIENCE = "GetBuyersCustomAudience";
    public static final String VALIDATE_REQUEST = "ValidateRequest";
    public static final String GET_BUYER_DECISION_LOGIC = "GetBuyerDecisionLogic";
    public static final String GET_TRUSTED_BIDDING_SIGNALS = "GetTrustedBiddingSignals";
    public static final String RUN_BIDDING = "RunBidding";
    public static final String RUN_BIDDING_PER_CA = "RunBiddingPerCustomAudience";
    public static final String RUN_AD_SCORING = "RunAdScoring";
    public static final String GET_AD_SELECTION_LOGIC = "GetAdSelectionLogic";
    public static final String GET_TRUSTED_SCORING_SIGNALS = "GetTrustedScoringSignals";
    public static final String SCORE_AD = "ScoreAd";
    public static final String RUN_OUTCOME_SELECTION = "RunAdOutcomeSelection";
    public static final String GENERATE_BIDS = "GenerateBids";
    public static final String FETCH_PAYLOAD = "FetchPayload";
    public static final String CACHE_GET = "CacheGet";
    public static final String CACHE_PUT = "CachePut";
    public static final String HTTP_REQUEST = "HttpRequest";
    public static final String JSSCRIPTENGINE_CREATE_ISOLATE = "JSScriptEngine#createIsolate";
    public static final String JSSCRIPTENGINE_EVALUATE_ON_SANDBOX =
            "JSScriptEngine#evaluateOnSandbox";
    public static final String JSSCRIPTENGINE_CLOSE_ISOLATE = "JSScriptEngine#closeIsolate";
    public static final String PERSIST_AD_SELECTION_RESULT =
            "AdSelectionServiceImpl#persistAdSelectionResult";
    public static final String ORCHESTRATE_PERSIST_AD_SELECTION_RESULT =
            "PersistAdSelectionResultRunner#orchestratePersistAdSelectionResultRunner";
    public static final String PERSIST_AUCTION_RESULTS =
            "PersistAdSelectionResultRunner#persistAuctionResults";
    public static final String OHTTP_DECRYPT_BYTES = "PersistAdSelectionResultRunner#decryptBytes";
    public static final String PARSE_AD_SELECTION_RESULT =
            "PersistAdSelectionResultRunner#parseAdSelectionResult";
    public static final String GET_AD_SELECTION_DATA = "AdSelectionServiceImpl#getAdSelectionData";
    public static final String GET_BUYERS_CA = "BuyerInputGenerator#getBuyersCustomAudience";
    public static final String GET_FILTERED_BUYERS_CA =
            "BuyerInputGenerator#getFilteredCustomAudiences";
    public static final String GET_BUYERS_PS = "BuyerInputGenerator#getBuyersProtectedSignals";
    public static final String GET_COMPRESSED_BUYERS_INPUTS =
            "BuyerInputGenerator#getCompressedBuyerInputs";
    public static final String AUCTION_SERVER_GZIP_COMPRESS =
            "AuctionServerDataCompressorGzip#compress";
    public static final String FORMAT_PAYLOAD_V0 = "AuctionServerPayloadFormatterV0#apply";
    public static final String FORMAT_PAYLOAD_EXCESSIVE_MAX_SIZE =
            "AuctionServerPayloadFormatterExcessiveMaxSize#apply";
    public static final String CREATE_BUYER_INPUTS = "BuyerInputGenerator#createBuyerInputs";
    public static final String CREATE_GET_AD_SELECTION_DATA_PAYLOAD =
            "GetAdSelectionDataRunner#createPayload";
    public static final String ORCHESTRATE_GET_AD_SELECTION_DATA =
            "GetAdSelectionDataRunner#orchestrateGetAdSelectionDataRunner";
    public static final String PERSIST_AD_SELECTION_ID_REQUEST =
            "GetAdSelectionDataRunner#persistAdSelectionIdRequest";
    public static final String GET_LATEST_OHTTP_KEY_CONFIG =
            "AdSelectionEncryptionKeyManager#getLatestOhttpKeyConfigOfType";
    public static final String CREATE_AND_SERIALIZE_REQUEST =
            "ObliviousHttpEncryptorImpl#createAndSerializeRequest";
    public static final String OHTTP_ENCRYPT_BYTES = "ObliviousHttpEncryptorImpl#encryptBytes";

    private static final String PERFETTO_TRIGGER_COMMAND = "/system/bin/trigger_perfetto";

    /**
     * Begins an asynchronous trace and generates random cookie.
     *
     * @param sectionName used to identify trace type.
     * @return unique cookie for identifying trace.
     */
    public static int beginAsyncSection(@NonNull String sectionName) {
        if (!Trace.isEnabled()) {
            return -1;
        }
        int traceCookie = ThreadLocalRandom.current().nextInt();
        Trace.beginAsyncSection(sectionName, traceCookie);
        return traceCookie;
    }

    /**
     * Ends an asynchronous trace section.
     *
     * @param sectionName used to identify trace type.
     * @param traceCookie unique cookie for identifying trace.
     */
    public static void endAsyncSection(@NonNull String sectionName, int traceCookie) {
        Trace.endAsyncSection(sectionName, traceCookie);
    }

    /**
     * Notifies perfetto to start AOT given a trace event. This can be an expensive operation so
     * only use it to record failures but not general trace events.
     *
     * @param triggerEvent name of Perfetto trigger event.
     */
    public static void triggerPerfetto(String triggerEvent) {
        try {
            ProcessBuilder pb = new ProcessBuilder(PERFETTO_TRIGGER_COMMAND, triggerEvent);
            LogUtil.d("Triggering perfetto with " + triggerEvent);
            pb.start();
        } catch (IOException e) {
            LogUtil.e("Failed to trigger perfetto with " + triggerEvent, e);
        }
    }
}
