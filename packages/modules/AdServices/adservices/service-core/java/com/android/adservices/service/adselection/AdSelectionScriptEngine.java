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

import static com.android.adservices.service.js.JSScriptArgument.arrayArg;
import static com.android.adservices.service.js.JSScriptArgument.jsonArg;
import static com.android.adservices.service.js.JSScriptArgument.numericArg;
import static com.android.adservices.service.js.JSScriptArgument.recordArg;
import static com.android.adservices.service.js.JSScriptArgument.stringArg;
import static com.android.adservices.service.js.JSScriptArgument.stringArrayArg;

import static com.google.common.util.concurrent.Futures.transform;

import android.adservices.adselection.AdSelectionConfig;
import android.adservices.adselection.AdWithBid;
import android.adservices.common.AdData;
import android.adservices.common.AdSelectionSignals;
import android.annotation.NonNull;
import android.content.Context;
import android.net.Uri;
import android.webkit.URLUtil;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.adselection.CustomAudienceSignals;
import com.android.adservices.data.adselection.datahandlers.AdSelectionResultBidAndUri;
import com.android.adservices.data.common.DBAdData;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.service.common.RetryStrategy;
import com.android.adservices.service.exception.JSExecutionException;
import com.android.adservices.service.js.IsolateSettings;
import com.android.adservices.service.js.JSScriptArgument;
import com.android.adservices.service.js.JSScriptEngine;
import com.android.adservices.service.profiling.Tracing;
import com.android.adservices.service.signals.ProtectedSignal;
import com.android.adservices.service.signals.ProtectedSignalsArgumentUtil;
import com.android.adservices.service.stats.AdSelectionExecutionLogger;
import com.android.adservices.service.stats.RunAdBiddingPerCAExecutionLogger;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Utility class to execute an auction script. Current implementation is thread safe but relies on a
 * singleton JS execution environment and will serialize calls done either using the same or
 * different instances of {@link AdSelectionScriptEngine}. This will change once we will use the new
 * WebView API.
 *
 * <p>This class is thread safe but, for performance reasons, it is suggested to use one instance
 * per thread. See the threading comments for {@link JSScriptEngine}.
 */
public class AdSelectionScriptEngine {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();

    private static final String JS_EXECUTION_STATUS_UNSUCCESSFUL =
            "Outcome selection script failed with status '%s' or returned unexpected result '%s'";

    private static final String JS_EXECUTION_RESULT_INVALID =
            "Result of outcome selection script result is invalid: %s";

    // TODO: (b/228094391): Put these common constants in a separate class
    private static final String SCRIPT_ARGUMENT_NAME_IGNORED = "ignored";
    public static final String FUNCTION_NAMES_ARG_NAME = "__rb_functionNames";
    public static final String RESULTS_FIELD_NAME = "results";
    public static final String STATUS_FIELD_NAME = "status";
    // This is a local variable and doesn't need any prefix.
    public static final String CUSTOM_AUDIENCE_ARG_NAME = "__rb_custom_audience";
    public static final String AD_VAR_NAME = "ad";
    public static final String ADS_ARG_NAME = "__rb_ads";
    public static final String AUCTION_SIGNALS_ARG_NAME = "__rb_auction_signals";
    public static final String PER_BUYER_SIGNALS_ARG_NAME = "__rb_per_buyer_signals";
    public static final String TRUSTED_BIDDING_SIGNALS_ARG_NAME = "__rb_trusted_bidding_signals";
    public static final String CONTEXTUAL_SIGNALS_ARG_NAME = "__rb_contextual_signals";
    public static final String CUSTOM_AUDIENCE_BIDDING_SIGNALS_ARG_NAME =
            "__rb_custom_audience_bidding_signals";
    public static final String CUSTOM_AUDIENCE_SCORING_SIGNALS_ARG_NAME =
            "__rb_custom_audience_scoring_signals";
    public static final String AUCTION_CONFIG_ARG_NAME = "__rb_auction_config";
    public static final String SELLER_SIGNALS_ARG_NAME = "__rb_seller_signals";
    public static final String TRUSTED_SCORING_SIGNALS_ARG_NAME = "__rb_trusted_scoring_signals";
    public static final String GENERATE_BID_FUNCTION_NAME = "generateBid";
    public static final String SCORE_AD_FUNCTION_NAME = "scoreAd";
    public static final String ENCODE_SIGNALS_DRIVER_FUNCTION_NAME = "encodeSignalsDriver";
    public static final String ENCODE_SIGNALS_FUNCTION_NAME = "encodeSignals";
    public static final String USER_SIGNALS_ARG_NAME = "__rb_user_signals";
    public static final String AD_SCORE_FIELD_NAME = "score";

    public static final String SIGNALS_ARG_NAME = "__rb_protected_signals";
    public static final String MAX_SIZE_BYTES_ARG_NAME = "__rb_max_size_bytes";
    public static final String DEBUG_REPORTING_WIN_URI_FIELD_NAME = "debug_reporting_win_uri";
    public static final String DEBUG_REPORTING_LOSS_URI_FIELD_NAME = "debug_reporting_loss_uri";
    public static final String DEBUG_REPORTING_SELLER_REJECT_REASON_FIELD_NAME = "rejectReason";
    public static final String AD_COST_FIELD_NAME = "adCost";
    public static final int NUM_BITS_STOCHASTIC_ROUNDING = 8;

    /**
     * Template for the iterative invocation function. The two tokens to expand are the list of
     * parameters and the invocation of the actual per-ad function.
     */
    public static final String AD_SELECTION_ITERATIVE_PROCESSING_JS =
            "function "
                    + JSScriptEngine.ENTRY_POINT_FUNC_NAME
                    + "(%s) {\n"
                    + " let status = 0;\n"
                    + " const results = []; \n"
                    + " for (const "
                    + AD_VAR_NAME
                    + " of "
                    + ADS_ARG_NAME
                    + ") {\n"
                    + "   //Short circuit the processing of all ads if there was any failure.\n"
                    + "   const script_result = %s;\n"
                    + "   if (script_result === Object(script_result) && \n"
                    + "         'status' in script_result) {\n"
                    + "      status = script_result.status;\n"
                    + "   } else {\n"
                    + "     // invalid script\n"
                    + "     status = -1;\n"
                    + "   } \n"
                    + "   if (status != 0) break;\n"
                    + "   script_result.debug_reporting_win_uri = "
                    + DebugReportingEnabledScriptStrategy.WIN_URI_GLOBAL_VARIABLE
                    + ";\n"
                    + "   script_result.debug_reporting_loss_uri = "
                    + DebugReportingEnabledScriptStrategy.LOSS_URI_GLOBAL_VARIABLE
                    + ";\n"
                    + DebugReportingEnabledScriptStrategy.RESET_SCRIPT
                    + "   results.push(script_result);\n"
                    + "  }\n"
                    + "  return {'status': status, 'results': results};\n"
                    + "};";

    /**
     * Template for the batch invocation function. The two tokens to expand are the list of
     * parameters and the invocation of the actual per-ad function.
     */
    public static final String AD_SELECTION_BATCH_PROCESSING_JS =
            "function "
                    + JSScriptEngine.ENTRY_POINT_FUNC_NAME
                    + "(%s) {\n"
                    + "  let status = 0;\n"
                    + "  const results = []; \n"
                    + "  const script_result = %s;\n"
                    + "  if (script_result === Object(script_result) && \n"
                    + "      'status' in script_result && \n"
                    + "      'result' in script_result) {\n"
                    + "    status = script_result.status;\n"
                    + "    results.push(script_result.result)\n"
                    + "  } else {\n"
                    + "    // invalid script\n"
                    + "    status = -1;\n"
                    + "  }\n"
                    + "  return {'status': status, 'results': results};\n"
                    + "};";

    public static final String AD_SELECTION_GENERATE_BID_JS_V3 =
            "function "
                    + JSScriptEngine.ENTRY_POINT_FUNC_NAME
                    + "(%s) {\n"
                    + "    let status = 0;\n"
                    + "    let results = null;\n"
                    + "    const script_result = %s;\n"
                    + "    if (script_result === Object(script_result) &&\n"
                    + "        'ad' in script_result &&\n"
                    + "        'bid' in script_result &&\n"
                    + "        'render' in script_result) {\n"
                    + "        results = [{"
                    + "           'ad': script_result.ad,\n"
                    + "           'bid': script_result.bid,\n"
                    + "           'adCost': script_result.adCost,\n"
                    + "           'debug_reporting_win_uri': "
                    + DebugReportingEnabledScriptStrategy.WIN_URI_GLOBAL_VARIABLE
                    + ",\n"
                    + "           'debug_reporting_loss_uri': "
                    + DebugReportingEnabledScriptStrategy.LOSS_URI_GLOBAL_VARIABLE
                    + ",\n"
                    + "        }];\n"
                    + "    } else {\n"
                    + "        // invalid script\n"
                    + "        status = -1;\n"
                    + "    }\n"
                    + "    return {'status': status, 'results': results};\n"
                    + "};";

    public static final String CHECK_FUNCTIONS_EXIST_JS =
            "function "
                    + JSScriptEngine.ENTRY_POINT_FUNC_NAME
                    + "(names) {\n"
                    + " for (const name of names) {\n"
                    + "     try {\n"
                    + "         if (typeof eval(name) != 'function') return false;\n"
                    + "     } catch(e) {\n"
                    + "         if (e instanceof ReferenceError) return false;\n"
                    + "     }\n"
                    + " }\n"
                    + " return true;\n"
                    + "}";
    public static final String GET_FUNCTION_ARGUMENT_COUNT =
            "function "
                    + JSScriptEngine.ENTRY_POINT_FUNC_NAME
                    + "(names) {\n"
                    + " for (const name of names) {\n"
                    + "     try {\n"
                    + "         if (typeof eval(name) != 'function') return -1;\n"
                    + "     } catch(e) {\n"
                    + "         if (e instanceof ReferenceError) return -1;\n"
                    + "     }\n"
                    + "     if (typeof eval(name) === 'function') return eval(name).length;\n"
                    + " }\n"
                    + " return -1;\n"
                    + "}";

    /**
     * Minified JS that un-marshals signals to map their keys and values to byte[]. Should be
     * invoked by the driver script before passing the signals to encodeSignals() function.
     *
     * <pre>
     * function decodeHex(hexString) {
     *     hexString = hexString.replace(/\\s/g, '');
     *     if (hexString.length % 2 !== 0) {
     *         throw new Error('hex must have even chars.');
     *     }
     *     const byteArray = new Uint8Array(hexString.length / 2);
     *     for (let i = 0; i < hexString.length; i += 2) {
     *         const byteValue = parseInt(hexString.substr(i, 2), 16);
     *         byteArray[i / 2] = byteValue;
     *     }
     *
     *     return byteArray;
     * }
     *
     * function unmarshal(signalObjects){
     *    const result=new Map();
     *    signalObjects.forEach(
     *      (signalGroup=> {
     *        for(const key in signalGroup){
     *          const signal_key = decodeHex(key)
     *          const decodedValues= signalGroup[key].map((entry=>({
     *                 signal_value: decodeHex(entry.val),
     *                 creation_time: entry.time,
     *                 package_name: entry.app.trim()
     *               })));
     *
     *           result.set(signal_key, decodedValues)
     *         }
     *        })
     *     )
     *   return result;
     * }
     * </pre>
     */
    private static final String UNMARSHAL_SIGNALS_JS =
            "function decodeHex(e){if((e=e.replace(/\\\\s/g,\"\")).length%2!=0)throw Error(\"hex "
                    + "must have even chars.\");let t=new Uint8Array(e.length/2);for(let n=0;n<e"
                    + ".length;n+=2){let a=parseInt(e.substr(n,2),16);t[n/2]=a}return t}function "
                    + "unmarshal(e){let t=new Map;return e.forEach(e=>{for(let n in e){let a=e[n]."
                    + "map(e=>({signal_value:decodeHex(e.val),creation_time:e.time,package_name:e."
                    + "app.trim()}));t.set(decodeHex(n),a)}}),t}";

    /**
     * Function used to marshal the Uint8Array returned by encodeSignals into an hex string.
     *
     * <pre>
     * function encodeHex(signals) {
     *    return signals.reduce(
     *      (output, byte) => output + byte.toString(16).padStart(2, '0'), ''
     *    );
     * };
     * </pre>
     */
    private static final String MARSHALL_ENCODED_SIGNALS_JS =
            "function encodeHex(e){return e.reduce((e,n)=>e+n.toString(16).padStart(2,"
                    + " '0'),\"\");}";

    /**
     * This JS wraps around encodeSignals() logic. Un-marshals the signals and then invokes the
     * encodeSignals() script.
     */
    private static final String ENCODE_SIGNALS_DRIVER_JS =
            "function "
                    + ENCODE_SIGNALS_DRIVER_FUNCTION_NAME
                    + "(signals, maxSize) {\n"
                    + "  const unmarshalledSignals = unmarshal(signals);\n"
                    + "\n"
                    + "  "
                    + "  let encodeResult = "
                    + ENCODE_SIGNALS_FUNCTION_NAME
                    + "(unmarshalledSignals, maxSize);\n"
                    + "   return { 'status': encodeResult.status, "
                    + "'results': encodeHex(encodeResult.results) };\n"
                    + "}\n"
                    + "\n"
                    + UNMARSHAL_SIGNALS_JS
                    + "\n"
                    + MARSHALL_ENCODED_SIGNALS_JS
                    + "\n";

    private static final int JS_SCRIPT_STATUS_SUCCESS = 0;
    private static final String ARG_PASSING_SEPARATOR = ", ";
    private final JSScriptEngine mJsEngine;
    // Used for the Futures.transform calls to compose futures.
    private final Executor mExecutor = MoreExecutors.directExecutor();
    private final Supplier<Boolean> mEnforceMaxHeapSizeFeatureSupplier;
    private final Supplier<Long> mMaxHeapSizeBytesSupplier;
    private final AdWithBidArgumentUtil mAdWithBidArgumentUtil;
    private final AdDataArgumentUtil mAdDataArgumentUtil;
    private final DebugReportingScriptStrategy mDebugReportingScript;
    private final boolean mCpcBillingEnabled;
    private final RetryStrategy mRetryStrategy;

    public AdSelectionScriptEngine(
            Context context,
            Supplier<Boolean> enforceMaxHeapSizeFeatureSupplier,
            Supplier<Long> maxHeapSizeBytesSupplier,
            AdCounterKeyCopier adCounterKeyCopier,
            DebugReportingScriptStrategy debugReportingScript,
            boolean cpcBillingEnabled,
            RetryStrategy retryStrategy) {
        mJsEngine = JSScriptEngine.getInstance(context, sLogger);
        mEnforceMaxHeapSizeFeatureSupplier = enforceMaxHeapSizeFeatureSupplier;
        mMaxHeapSizeBytesSupplier = maxHeapSizeBytesSupplier;
        mAdDataArgumentUtil = new AdDataArgumentUtil(adCounterKeyCopier);
        mAdWithBidArgumentUtil = new AdWithBidArgumentUtil(mAdDataArgumentUtil);
        mDebugReportingScript = debugReportingScript;
        mCpcBillingEnabled = cpcBillingEnabled;
        mRetryStrategy = retryStrategy;
    }

    /**
     * @return The result of invoking the {@code generateBid} function in the given {@code
     *     generateBidJS} JS script for the list of {@code ads} and signals provided. Will return an
     *     empty list if the script fails for any reason.
     * @throws JSONException If any of the signals is not a valid JSON object.
     */
    public ListenableFuture<List<GenerateBidResult>> generateBids(
            @NonNull String generateBidJS,
            @NonNull List<AdData> ads,
            @NonNull AdSelectionSignals auctionSignals,
            @NonNull AdSelectionSignals perBuyerSignals,
            @NonNull AdSelectionSignals trustedBiddingSignals,
            @NonNull AdSelectionSignals contextualSignals,
            @NonNull CustomAudienceSignals customAudienceSignals,
            @NonNull RunAdBiddingPerCAExecutionLogger runAdBiddingPerCAExecutionLogger)
            throws JSONException {
        Objects.requireNonNull(generateBidJS);
        Objects.requireNonNull(ads);
        Objects.requireNonNull(auctionSignals);
        Objects.requireNonNull(perBuyerSignals);
        Objects.requireNonNull(trustedBiddingSignals);
        Objects.requireNonNull(contextualSignals);
        Objects.requireNonNull(customAudienceSignals);
        Objects.requireNonNull(runAdBiddingPerCAExecutionLogger);
        int traceCookie = Tracing.beginAsyncSection(Tracing.GENERATE_BIDS);

        ImmutableList<JSScriptArgument> signals =
                ImmutableList.<JSScriptArgument>builder()
                        .add(jsonArg(AUCTION_SIGNALS_ARG_NAME, auctionSignals.toString()))
                        .add(jsonArg(PER_BUYER_SIGNALS_ARG_NAME, perBuyerSignals.toString()))
                        .add(
                                jsonArg(
                                        TRUSTED_BIDDING_SIGNALS_ARG_NAME,
                                        trustedBiddingSignals.toString()))
                        .add(jsonArg(CONTEXTUAL_SIGNALS_ARG_NAME, contextualSignals.toString()))
                        .add(
                                CustomAudienceBiddingSignalsArgumentUtil.asScriptArgument(
                                        CUSTOM_AUDIENCE_BIDDING_SIGNALS_ARG_NAME,
                                        customAudienceSignals))
                        .build();

        ImmutableList.Builder<JSScriptArgument> adDataArguments = new ImmutableList.Builder<>();
        for (AdData currAd : ads) {
            // Ads are going to be in an array their individual name is ignored.
            adDataArguments.add(mAdDataArgumentUtil.asScriptArgument("ignored", currAd));
        }
        runAdBiddingPerCAExecutionLogger.startGenerateBids();
        return FluentFuture.from(
                        transform(
                                runAuctionScriptIterative(
                                        generateBidJS,
                                        adDataArguments.build(),
                                        signals,
                                        this::callGenerateBid),
                                result -> {
                                    List<GenerateBidResult> bidsResults =
                                            handleGenerateBidsOutput(result);
                                    runAdBiddingPerCAExecutionLogger.endGenerateBids();
                                    Tracing.endAsyncSection(Tracing.GENERATE_BIDS, traceCookie);
                                    return bidsResults;
                                },
                                mExecutor))
                .catchingAsync(
                        JSExecutionException.class,
                        e -> {
                            Tracing.endAsyncSection(Tracing.GENERATE_BIDS, traceCookie);
                            sLogger.e(
                                    e,
                                    "Encountered exception when generating bids, attempting to run"
                                            + " backward compatible JS");
                            return handleBackwardIncompatibilityScenario(
                                    generateBidJS,
                                    signals,
                                    adDataArguments.build(),
                                    runAdBiddingPerCAExecutionLogger,
                                    e);
                        },
                        mExecutor);
    }

    /**
     * @return The result of invoking the {@code generateBidV3} function in the given {@code
     *     generateBidJS} JS script for the args provided. Will return an empty list if the script
     *     fails for any reason.
     * @throws JSONException If any of the signals is not a valid JSON object.
     */
    @NonNull
    public ListenableFuture<List<GenerateBidResult>> generateBidsV3(
            @NonNull String generateBidJS,
            @NonNull DBCustomAudience customAudience,
            @NonNull AdSelectionSignals auctionSignals,
            @NonNull AdSelectionSignals perBuyerSignals,
            @NonNull AdSelectionSignals trustedBiddingSignals,
            @NonNull AdSelectionSignals contextualSignals,
            @NonNull RunAdBiddingPerCAExecutionLogger runAdBiddingPerCAExecutionLogger)
            throws JSONException {
        Objects.requireNonNull(generateBidJS);
        Objects.requireNonNull(customAudience);
        Objects.requireNonNull(auctionSignals);
        Objects.requireNonNull(perBuyerSignals);
        Objects.requireNonNull(trustedBiddingSignals);
        Objects.requireNonNull(contextualSignals);
        Objects.requireNonNull(runAdBiddingPerCAExecutionLogger);
        int traceCookie = Tracing.beginAsyncSection(Tracing.GENERATE_BIDS);

        ImmutableList<JSScriptArgument> signals =
                ImmutableList.<JSScriptArgument>builder()
                        .add(translateCustomAudience(customAudience))
                        .add(jsonArg(AUCTION_SIGNALS_ARG_NAME, auctionSignals))
                        .add(jsonArg(PER_BUYER_SIGNALS_ARG_NAME, perBuyerSignals))
                        .add(jsonArg(TRUSTED_BIDDING_SIGNALS_ARG_NAME, trustedBiddingSignals))
                        .add(jsonArg(CONTEXTUAL_SIGNALS_ARG_NAME, contextualSignals))
                        .build();
        runAdBiddingPerCAExecutionLogger.startGenerateBids();
        return FluentFuture.from(
                transform(
                        runAuctionScriptGenerateBidV3(
                                generateBidJS, signals, this::callGenerateBidV3),
                        result -> {
                            List<GenerateBidResult> bidResults = handleGenerateBidsOutput(result);
                            runAdBiddingPerCAExecutionLogger.endGenerateBids();
                            Tracing.endAsyncSection(Tracing.GENERATE_BIDS, traceCookie);
                            return bidResults;
                        },
                        mExecutor));
    }

    /**
     * @return The scored ads for this custom audiences given the list of Ads with associated bid
     *     and the set of signals. Will return an empty list if the script fails for any reason.
     * @throws JSONException If any of the data is not a valid JSON object.
     */
    public ListenableFuture<List<ScoreAdResult>> scoreAds(
            @NonNull String scoreAdJS,
            @NonNull List<AdWithBid> adsWithBid,
            @NonNull AdSelectionConfig adSelectionConfig,
            @NonNull AdSelectionSignals sellerSignals,
            @NonNull AdSelectionSignals trustedScoringSignals,
            @NonNull AdSelectionSignals contextualSignals,
            @NonNull List<CustomAudienceSignals> customAudienceSignalsList,
            @NonNull AdSelectionExecutionLogger adSelectionExecutionLogger)
            throws JSONException {
        Objects.requireNonNull(scoreAdJS);
        Objects.requireNonNull(adsWithBid);
        Objects.requireNonNull(adSelectionConfig);
        Objects.requireNonNull(sellerSignals);
        Objects.requireNonNull(trustedScoringSignals);
        Objects.requireNonNull(contextualSignals);
        Objects.requireNonNull(customAudienceSignalsList);
        Objects.requireNonNull(adSelectionExecutionLogger);
        ImmutableList<JSScriptArgument> args =
                ImmutableList.<JSScriptArgument>builder()
                        .add(
                                AdSelectionConfigArgumentUtil.asScriptArgument(
                                        adSelectionConfig, AUCTION_CONFIG_ARG_NAME))
                        .add(jsonArg(SELLER_SIGNALS_ARG_NAME, sellerSignals.toString()))
                        .add(
                                jsonArg(
                                        TRUSTED_SCORING_SIGNALS_ARG_NAME,
                                        trustedScoringSignals.toString()))
                        .add(jsonArg(CONTEXTUAL_SIGNALS_ARG_NAME, contextualSignals.toString()))
                        .add(
                                CustomAudienceScoringSignalsArgumentUtil.asScriptArgument(
                                        CUSTOM_AUDIENCE_SCORING_SIGNALS_ARG_NAME,
                                        customAudienceSignalsList))
                        .build();

        ImmutableList.Builder<JSScriptArgument> adWithBidArguments = new ImmutableList.Builder<>();
        for (AdWithBid currAdWithBid : adsWithBid) {
            // Ad with bids are going to be in an array their individual name is ignored.
            adWithBidArguments.add(
                    mAdWithBidArgumentUtil.asScriptArgument(
                            SCRIPT_ARGUMENT_NAME_IGNORED, currAdWithBid));
        }
        // Start scoreAds script execution process.
        adSelectionExecutionLogger.startScoreAds();
        return FluentFuture.from(
                        runAuctionScriptIterative(
                                scoreAdJS, adWithBidArguments.build(), args, this::callScoreAd))
                .transform(
                        result -> handleScoreAdsOutput(result, adSelectionExecutionLogger),
                        mExecutor);
    }

    /**
     * Injects buyer provided encodeSignals() logic into a driver JS. The driver script first
     * un-marshals the signals, which would be marshaled by {@link ProtectedSignalsArgumentUtil},
     * and then invokes the buyer provided encoding logic. The script marshals the Uint8Array
     * returned by encodeSignals as HEX string and converts it into the byte[] returned by this
     * method.
     *
     * @param encodingLogic The buyer provided encoding logic
     * @param rawSignals The signals fetched from buyer delegation
     * @param maxSize maxSize of payload generated by the buyer
     * @throws IllegalStateException if the JSON created from raw Signals is invalid
     */
    public ListenableFuture<byte[]> encodeSignals(
            @NonNull String encodingLogic,
            @NonNull Map<String, List<ProtectedSignal>> rawSignals,
            @NonNull int maxSize)
            throws IllegalStateException {

        if (rawSignals.isEmpty()) {
            return Futures.immediateFuture(new byte[0]);
        }

        String combinedDriverAndEncodingLogic = ENCODE_SIGNALS_DRIVER_JS + encodingLogic;
        ImmutableList<JSScriptArgument> args;
        try {
            args =
                    ImmutableList.<JSScriptArgument>builder()
                            .add(
                                    ProtectedSignalsArgumentUtil.asScriptArgument(
                                            SIGNALS_ARG_NAME, rawSignals))
                            .add(numericArg(MAX_SIZE_BYTES_ARG_NAME, maxSize))
                            .build();
        } catch (JSONException e) {
            throw new IllegalStateException("Exception processing JSON version of signals");
        }

        IsolateSettings isolateSettings =
                mEnforceMaxHeapSizeFeatureSupplier.get()
                        ? IsolateSettings.forMaxHeapSizeEnforcementEnabled(
                                mMaxHeapSizeBytesSupplier.get())
                        : IsolateSettings.forMaxHeapSizeEnforcementDisabled();

        return FluentFuture.from(
                        mJsEngine.evaluate(
                                combinedDriverAndEncodingLogic,
                                args,
                                ENCODE_SIGNALS_DRIVER_FUNCTION_NAME,
                                isolateSettings,
                                mRetryStrategy))
                .transform(this::handleEncodingOutput, mExecutor);
    }

    /**
     * Runs selection logic on map of {@code long} ad selection id {@code double} bid
     *
     * @return either one of the ad selection ids passed in {@code adSelectionIdBidPairs} or {@code
     *     null}
     * @throws JSONException if any input or the result is failed to parse
     * @throws IllegalStateException If JS script fails to run or returns an illegal results (i.e.
     *     two ad selection ids or empty)
     */
    public ListenableFuture<Long> selectOutcome(
            @NonNull String selectionLogic,
            @NonNull List<AdSelectionResultBidAndUri> adSelectionIdWithBidAndRenderUris,
            @NonNull AdSelectionSignals selectionSignals)
            throws JSONException, IllegalStateException {
        Objects.requireNonNull(selectionLogic);
        Objects.requireNonNull(adSelectionIdWithBidAndRenderUris);
        Objects.requireNonNull(selectionSignals);

        ImmutableList<JSScriptArgument> args =
                ImmutableList.<JSScriptArgument>builder()
                        .add(jsonArg("selection_signals", selectionSignals.toString()))
                        .build();
        sLogger.v("Other args creates " + args);

        ImmutableList.Builder<JSScriptArgument> adSelectionIdWithBidArguments =
                new ImmutableList.Builder<>();
        for (AdSelectionResultBidAndUri curr : adSelectionIdWithBidAndRenderUris) {
            // Ad with bids are going to be in an array their individual name is ignored.
            adSelectionIdWithBidArguments.add(
                    SelectAdsFromOutcomesArgumentUtil.asScriptArgument(
                            SCRIPT_ARGUMENT_NAME_IGNORED, curr));
        }
        ImmutableList<JSScriptArgument> advertArgs = adSelectionIdWithBidArguments.build();
        sLogger.v("Advert args created " + advertArgs);

        return transform(
                runAuctionScriptBatch(selectionLogic, advertArgs, args, this::callSelectOutcome),
                this::handleSelectOutcomesOutput,
                mExecutor);
    }

    /**
     * Parses the output from the invocation of the {@code generateBid} JS function on a list of ads
     * and convert it to a list of {@link GenerateBidResult} objects. The script output has been
     * pre-parsed into an {@link AuctionScriptResult} object that will contain the script status
     * code and the list of ads. The method will return an empty list of ads if the status code is
     * not {@link #JS_SCRIPT_STATUS_SUCCESS} or if there has been any problem parsing the JS
     * response.
     */
    private List<GenerateBidResult> handleGenerateBidsOutput(AuctionScriptResult batchBidResult) {
        ImmutableList.Builder<GenerateBidResult> results = ImmutableList.builder();
        if (batchBidResult.status != JS_SCRIPT_STATUS_SUCCESS) {
            sLogger.v("Bid script failed, returning empty result.");
            return ImmutableList.of();
        }

        try {
            for (int i = 0; i < batchBidResult.results.length(); i++) {
                JSONObject json = batchBidResult.results.optJSONObject(i);
                AdWithBid adWithBid = mAdWithBidArgumentUtil.parseJsonResponse(json);
                Uri debugReportingWinUri =
                        extractValidUri(json.optString(DEBUG_REPORTING_WIN_URI_FIELD_NAME, ""));
                Uri debugReportingLossUri =
                        extractValidUri(json.optString(DEBUG_REPORTING_LOSS_URI_FIELD_NAME, ""));

                GenerateBidResult.Builder generateBidResultBuilder =
                        GenerateBidResult.builder()
                                .setAdWithBid(adWithBid)
                                .setWinDebugReportUri(debugReportingWinUri)
                                .setLossDebugReportUri(debugReportingLossUri);

                if (mCpcBillingEnabled) {
                    double adCost = json.optDouble(AD_COST_FIELD_NAME);
                    if (!Double.isNaN(adCost) && !Double.isInfinite(adCost)) {
                        generateBidResultBuilder.setAdCost(
                                new AdCost(adCost, NUM_BITS_STOCHASTIC_ROUNDING));
                    }
                }
                results.add(generateBidResultBuilder.build());
            }
        } catch (IllegalArgumentException e) {
            sLogger.w(
                    e,
                    "Invalid ad with bid returned by a generateBid script. Returning empty"
                            + " list of ad with bids.");
            return ImmutableList.of();
        }

        return results.build();
    }

    /**
     * Parses the output from the invocation of the {@code scoreAd} JS function on a list of ad with
     * associated bids {@link Double}. The script output has been pre-parsed into an {@link
     * AuctionScriptResult} object that will contain the script sstatus code and the list of scores.
     * The method will return an empty list of ads if the status code is not {@link
     * #JS_SCRIPT_STATUS_SUCCESS} or if there has been any problem parsing the JS response.
     */
    private List<ScoreAdResult> handleScoreAdsOutput(
            AuctionScriptResult batchBidResult,
            AdSelectionExecutionLogger adSelectionExecutionLogger) {
        ImmutableList.Builder<ScoreAdResult> result = ImmutableList.builder();

        if (batchBidResult.status != JS_SCRIPT_STATUS_SUCCESS) {
            sLogger.v("Scoring script failed, returning empty result.");
        } else {
            for (int i = 0; i < batchBidResult.results.length(); i++) {
                // If the output of the score for this advert is invalid JSON or doesn't have a
                // score we are dropping the advert by scoring it with 0.
                JSONObject json = batchBidResult.results.optJSONObject(i);
                Double score = json.optDouble(AD_SCORE_FIELD_NAME, 0.0);
                Uri debugReportingWinUri =
                        extractValidUri(json.optString(DEBUG_REPORTING_WIN_URI_FIELD_NAME, ""));
                Uri debugReportingLossUri =
                        extractValidUri(json.optString(DEBUG_REPORTING_LOSS_URI_FIELD_NAME, ""));
                String sellerRejectReason =
                        json.optString(DEBUG_REPORTING_SELLER_REJECT_REASON_FIELD_NAME, "");
                result.add(
                        ScoreAdResult.builder()
                                .setAdScore(score)
                                .setSellerRejectReason(sellerRejectReason)
                                .setWinDebugReportUri(debugReportingWinUri)
                                .setLossDebugReportUri(debugReportingLossUri)
                                .build());
            }
        }

        adSelectionExecutionLogger.endScoreAds();
        return result.build();
    }

    /**
     * Parses the output from the invocation of the {@code selectOutcome} JS function on a list of
     * ad selection ids {@link Double} with associated bids {@link Double}. The script output has
     * been pre-parsed into an {@link AuctionScriptResult} object that will contain the script
     * status code and the results as a list. This handler expects a single result in the {@code
     * results} or an empty list which is also valid as long as {@code status} is {@link
     * #JS_SCRIPT_STATUS_SUCCESS}
     *
     * <p>The method will return a status code is not {@link #JS_SCRIPT_STATUS_SUCCESS} if there has
     * been any problem executing JS script or parsing the JS response.
     *
     * @throws IllegalStateException is thrown in case the status is not success or the results has
     *     more than one item.
     */
    private Long handleSelectOutcomesOutput(AuctionScriptResult scriptResults)
            throws IllegalStateException {
        if (scriptResults.status != JS_SCRIPT_STATUS_SUCCESS
                || scriptResults.results.length() != 1) {
            String errorMsg =
                    String.format(
                            JS_EXECUTION_STATUS_UNSUCCESSFUL,
                            scriptResults.status,
                            scriptResults.results);
            sLogger.v(errorMsg);
            throw new IllegalStateException(errorMsg);
        }

        if (scriptResults.results.isNull(0)) {
            return null;
        }

        try {
            JSONObject resultOutcomeJson = scriptResults.results.getJSONObject(0);
            // Use Long class to parse from string
            return Long.valueOf(
                    resultOutcomeJson.optString(SelectAdsFromOutcomesArgumentUtil.ID_FIELD_NAME));
        } catch (JSONException e) {
            String errorMsg = String.format(JS_EXECUTION_RESULT_INVALID, scriptResults.results);
            sLogger.v(errorMsg);
            throw new IllegalStateException(errorMsg);
        }
    }

    @VisibleForTesting
    byte[] handleEncodingOutput(String encodingScriptResult) throws IllegalStateException {

        if (encodingScriptResult == null || encodingScriptResult.isEmpty()) {
            throw new IllegalStateException(
                    "The encoding script either doesn't contain the required function or the"
                            + " function returned null");
        }

        try {
            JSONObject jsonResult = new JSONObject(encodingScriptResult);
            int status = jsonResult.getInt(STATUS_FIELD_NAME);
            String result = jsonResult.getString(RESULTS_FIELD_NAME);

            if (status != JS_SCRIPT_STATUS_SUCCESS || result == null) {
                String errorMsg = String.format(JS_EXECUTION_STATUS_UNSUCCESSFUL, status, result);
                sLogger.v(errorMsg);
                throw new IllegalStateException(errorMsg);
            }

            try {
                return decodeHexString(result);
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("Malformed encoded payload.", e);
            }
        } catch (JSONException e) {
            sLogger.e("Could not extract the Encoded Payload result");
            throw new IllegalStateException("Exception processing result from encoding");
        }
    }

    private byte[] decodeHexString(String hexString) {
        Preconditions.checkArgument(
                hexString.length() % 2 == 0, "Expected an hex string but the arg length is odd");

        byte[] result = new byte[hexString.length() / 2];
        for (int i = 0; i < hexString.length() / 2; i++) {
            result[i] = byteValue(hexString.charAt(2 * i), hexString.charAt(2 * i + 1));
        }
        return result;
    }

    private byte byteValue(char highNibble, char lowNibble) {
        int high = Character.digit(highNibble, 16);
        Preconditions.checkArgument(high >= 0, "Invalid value for HEX string char");
        int low = Character.digit(lowNibble, 16);
        Preconditions.checkArgument(low >= 0, "Invalid value for HEX string char");

        return (byte) (high << 4 | low);
    }

    /**
     * Runs the function call generated by {@code auctionFunctionCallGenerator} in the JS script
     * {@code jsScript} for the list of {code ads} provided. The function will be called by a
     * generated extra function that is responsible for iterating through all arguments and causing
     * an early failure if the result of the function invocations is not an object containing a
     * 'status' field or the value of the 'status' is not 0. In case of success status is 0, if the
     * result doesn't have a status field, status is -1 otherwise the status is the non-zero status
     * returned by the failed invocation. The 'results' field contains the JSON array with the
     * results of the function invocations. The parameter {@code auctionFunctionCallGenerator} is
     * responsible for generating the call to the auction function by splitting the advert data
     *
     * <p>The inner function call generated by {@code auctionFunctionCallGenerator} will receive for
     * every call one of the ads or ads with bid and the extra arguments specified using {@code
     * otherArgs} in the order they are specified.
     *
     * @return A future with the result of the function or failing with {@link
     *     IllegalArgumentException} if the script is not valid, doesn't contain {@code
     *     auctionFunctionName}.
     */
    ListenableFuture<AuctionScriptResult> runAuctionScriptIterative(
            String jsScript,
            List<JSScriptArgument> ads,
            List<JSScriptArgument> otherArgs,
            Function<List<JSScriptArgument>, String> auctionFunctionCallGenerator) {
        try {
            return transform(
                    callAuctionScript(
                            mDebugReportingScript.wrapIterativeJs(jsScript),
                            ads,
                            otherArgs,
                            auctionFunctionCallGenerator,
                            AD_SELECTION_ITERATIVE_PROCESSING_JS),
                    this::parseAuctionScriptResult,
                    mExecutor);
        } catch (JSONException e) {
            throw new JSExecutionException(
                    "Illegal result returned by our internal iterative calling function.", e);
        }
    }

    ListenableFuture<AuctionScriptResult> runAuctionScriptGenerateBidV3(
            String jsScript,
            List<JSScriptArgument> args,
            Function<List<JSScriptArgument>, String> auctionFunctionCallGenerator) {
        try {
            return transform(
                    callAuctionScript(
                            mDebugReportingScript.wrapGenerateBidsV3Js(jsScript),
                            args,
                            auctionFunctionCallGenerator,
                            AD_SELECTION_GENERATE_BID_JS_V3),
                    this::parseAuctionScriptResult,
                    mExecutor);
        } catch (JSONException e) {
            throw new JSExecutionException(
                    "Illegal result returned by our internal batch calling function.", e);
        }
    }

    /**
     * Runs the function call generated by {@code auctionFunctionCallGenerator} in the JS script
     * {@code jsScript} for the list of {@code ads} provided. The function will be called by a
     * generated extra function that is responsible for calling the JS script.
     *
     * <p>If the result of the function invocations is not an object containing a 'status' or
     * 'results' field, or the value of the 'status' is not 0 then will return failure status.
     *
     * <p>In case of success status is 0. The 'results' field contains the JSON array with the
     * results of the function invocations. The parameter {@code auctionFunctionCallGenerator} is
     * responsible for generating the call to the auction function by passing the list of advert
     * data
     *
     * <p>The inner function call generated by {@code auctionFunctionCallGenerator} will receive the
     * list of ads and the extra arguments specified using {@code otherArgs} in the order they are
     * specified.
     *
     * @return A future with the result of the function or failing with {@link
     *     IllegalArgumentException} if the script is not valid, doesn't contain {@code
     *     auctionFunctionName}.
     */
    ListenableFuture<AuctionScriptResult> runAuctionScriptBatch(
            String jsScript,
            List<JSScriptArgument> ads,
            List<JSScriptArgument> otherArgs,
            Function<List<JSScriptArgument>, String> auctionFunctionCallGenerator) {
        try {
            return transform(
                    callAuctionScript(
                            jsScript,
                            ads,
                            otherArgs,
                            auctionFunctionCallGenerator,
                            AD_SELECTION_BATCH_PROCESSING_JS),
                    this::parseAuctionScriptResult,
                    mExecutor);
        } catch (JSONException e) {
            throw new JSExecutionException(
                    "Illegal result returned by our internal batch calling function.", e);
        }
    }

    /**
     * @return A {@link ListenableFuture} containing the result of the validation of the given
     *     {@code jsScript} script. A script is valid if it is valid JS code and it contains all the
     *     functions specified in {@code expectedFunctionsNames} are defined in the script. There is
     *     no validation of the expected signature.
     */
    ListenableFuture<Boolean> validateAuctionScript(
            String jsScript, List<String> expectedFunctionsNames) {
        IsolateSettings isolateSettings =
                mEnforceMaxHeapSizeFeatureSupplier.get()
                        ? IsolateSettings.forMaxHeapSizeEnforcementEnabled(
                                mMaxHeapSizeBytesSupplier.get())
                        : IsolateSettings.forMaxHeapSizeEnforcementDisabled();
        return transform(
                mJsEngine.evaluate(
                        jsScript + "\n" + CHECK_FUNCTIONS_EXIST_JS,
                        ImmutableList.of(
                                stringArrayArg(FUNCTION_NAMES_ARG_NAME, expectedFunctionsNames)),
                        isolateSettings,
                        mRetryStrategy),
                Boolean::parseBoolean,
                mExecutor);
    }

    // TODO(b/260786980) remove the patch added to make bidding JS backward compatible
    private ListenableFuture<List<GenerateBidResult>> handleBackwardIncompatibilityScenario(
            String generateBidJS,
            List<JSScriptArgument> signals,
            List<JSScriptArgument> adDataArguments,
            RunAdBiddingPerCAExecutionLogger runAdBiddingPerCAExecutionLogger,
            JSExecutionException jsExecutionException) {
        ListenableFuture<AdSelectionScriptEngine.AuctionScriptResult> biddingResult =
                updateArgsIfNeeded(generateBidJS, signals, jsExecutionException)
                        .transformAsync(
                                args ->
                                        runAuctionScriptIterative(
                                                generateBidJS,
                                                adDataArguments,
                                                args,
                                                this::callGenerateBid),
                                mExecutor);
        return transform(
                biddingResult,
                result -> {
                    List<GenerateBidResult> bidResults = handleGenerateBidsOutput(result);
                    runAdBiddingPerCAExecutionLogger.endGenerateBids();
                    return bidResults;
                },
                mExecutor);
    }

    /**
     * @return the number of arguments taken by {@code functionName} in a given {@code jsScript}
     *     falls-back to -1 if there is no function found the with given name.
     */
    @VisibleForTesting
    ListenableFuture<Integer> getAuctionScriptArgCount(String jsScript, String functionName) {
        IsolateSettings isolateSettings =
                mEnforceMaxHeapSizeFeatureSupplier.get()
                        ? IsolateSettings.forMaxHeapSizeEnforcementEnabled(
                                mMaxHeapSizeBytesSupplier.get())
                        : IsolateSettings.forMaxHeapSizeEnforcementDisabled();
        return transform(
                mJsEngine.evaluate(
                        jsScript + "\n" + GET_FUNCTION_ARGUMENT_COUNT,
                        ImmutableList.of(
                                stringArrayArg(
                                        FUNCTION_NAMES_ARG_NAME, ImmutableList.of(functionName))),
                        isolateSettings,
                        mRetryStrategy),
                Integer::parseInt,
                mExecutor);
    }

    /**
     * @return Updates the args passed to bidding JS to maintain backward compatibility
     *     (b/259718738), this shall be removed with TODO(b/260786980). Throws back the original
     *     {@link JSExecutionException} if the js method does not match signature of the backward
     *     compat.
     */
    private FluentFuture<List<JSScriptArgument>> updateArgsIfNeeded(
            String generateBidJS,
            List<JSScriptArgument> originalArgs,
            JSExecutionException jsExecutionException) {
        final int previousJSArgumentCount = 7;
        final int previousJSUserSignalsIndex = 5;

        return FluentFuture.from(
                transform(
                        getAuctionScriptArgCount(generateBidJS, GENERATE_BID_FUNCTION_NAME),
                        argCount -> {
                            List<JSScriptArgument> updatedArgList =
                                    originalArgs.stream().collect(Collectors.toList());
                            if (argCount == previousJSArgumentCount) {
                                try {
                                    // This argument needs to be placed at the second last position
                                    updatedArgList.add(
                                            previousJSUserSignalsIndex,
                                            jsonArg(
                                                    USER_SIGNALS_ARG_NAME,
                                                    AdSelectionSignals.EMPTY));
                                    return updatedArgList;
                                } catch (JSONException e) {
                                    sLogger.e(
                                            "Could not create JS argument: %s",
                                            USER_SIGNALS_ARG_NAME);
                                }
                            }
                            throw jsExecutionException;
                        },
                        mExecutor));
    }

    private AuctionScriptResult parseAuctionScriptResult(String auctionScriptResult) {
        try {
            if (auctionScriptResult.isEmpty()) {
                throw new IllegalArgumentException(
                        "The auction script either doesn't contain the required function or the"
                                + " function returns null");
            }

            JSONObject jsonResult = new JSONObject(auctionScriptResult);

            return new AuctionScriptResult(
                    jsonResult.getInt(STATUS_FIELD_NAME),
                    jsonResult.getJSONArray(RESULTS_FIELD_NAME));
        } catch (JSONException e) {
            throw new RuntimeException(
                    "Illegal result returned by our internal batch calling function.", e);
        }
    }

    /**
     * @return a {@link ListenableFuture} containing the string representation of a JSON object
     *     containing two fields:
     *     <p>
     *     <ul>
     *       <li>{@code status} field that will be 0 in case of successful processing of all ads or
     *           non-zero if any of the calls to processed an ad returned a non-zero status. In the
     *           last case the returned status will be the same returned in the failing invocation.
     *           The function {@code auctionFunctionName} is assumed to return a JSON object
     *           containing at least a {@code status} field.
     *       <li>{@code results} with the results of the invocation of {@code auctionFunctionName}
     *           to all the given ads.
     *     </ul>
     *     <p>
     */
    private ListenableFuture<String> callAuctionScript(
            String jsScript,
            List<JSScriptArgument> args,
            Function<List<JSScriptArgument>, String> auctionFunctionCallGenerator,
            String adSelectionProcessorJS)
            throws JSONException {

        String argPassing =
                args.stream()
                        .map(JSScriptArgument::name)
                        .collect(Collectors.joining(ARG_PASSING_SEPARATOR));

        IsolateSettings isolateSettings =
                mEnforceMaxHeapSizeFeatureSupplier.get()
                        ? IsolateSettings.forMaxHeapSizeEnforcementEnabled(
                                mMaxHeapSizeBytesSupplier.get())
                        : IsolateSettings.forMaxHeapSizeEnforcementDisabled();

        return mJsEngine.evaluate(
                jsScript
                        + "\n"
                        + String.format(
                                adSelectionProcessorJS,
                                argPassing,
                                auctionFunctionCallGenerator.apply(args)),
                args,
                isolateSettings,
                mRetryStrategy);
    }

    /**
     * @return a {@link ListenableFuture} containing the string representation of a JSON object
     *     containing two fields:
     *     <p>
     *     <ul>
     *       <li>{@code status} field that will be 0 in case of successful processing of all ads or
     *           non-zero if any of the calls to processed an ad returned a non-zero status. In the
     *           last case the returned status will be the same returned in the failing invocation.
     *           The function {@code auctionFunctionName} is assumed to return a JSON object
     *           containing at least a {@code status} field.
     *       <li>{@code results} with the results of the invocation of {@code auctionFunctionName}
     *           to all the given ads.
     *     </ul>
     *     <p>
     */
    private ListenableFuture<String> callAuctionScript(
            String jsScript,
            List<JSScriptArgument> adverts,
            List<JSScriptArgument> otherArgs,
            Function<List<JSScriptArgument>, String> auctionFunctionCallGenerator,
            String adSelectionProcessorJS)
            throws JSONException {
        ImmutableList.Builder<JSScriptArgument> advertsArg = ImmutableList.builder();
        advertsArg.addAll(adverts);
        sLogger.v(
                "script: %s%nadverts: %s%nother args: %s%nprocessor script: %s%n",
                jsScript, advertsArg, otherArgs, adSelectionProcessorJS);

        List<JSScriptArgument> allArgs =
                ImmutableList.<JSScriptArgument>builder()
                        .add(arrayArg(ADS_ARG_NAME, advertsArg.build()))
                        .addAll(otherArgs)
                        .build();

        String argPassing =
                allArgs.stream()
                        .map(JSScriptArgument::name)
                        .collect(Collectors.joining(ARG_PASSING_SEPARATOR));

        IsolateSettings isolateSettings =
                mEnforceMaxHeapSizeFeatureSupplier.get()
                        ? IsolateSettings.forMaxHeapSizeEnforcementEnabled(
                                mMaxHeapSizeBytesSupplier.get())
                        : IsolateSettings.forMaxHeapSizeEnforcementDisabled();

        return mJsEngine.evaluate(
                jsScript
                        + "\n"
                        + String.format(
                                adSelectionProcessorJS,
                                argPassing,
                                auctionFunctionCallGenerator.apply(otherArgs)),
                allArgs,
                isolateSettings,
                mRetryStrategy);
    }

    private String callGenerateBid(List<JSScriptArgument> otherArgs) {
        // The first argument is the local variable "ad" defined in AD_SELECTION_BATCH_PROCESSING_JS
        StringBuilder callArgs = new StringBuilder(AD_VAR_NAME);
        for (JSScriptArgument currArg : otherArgs) {
            callArgs.append(String.format(",%s", currArg.name()));
        }
        return String.format(GENERATE_BID_FUNCTION_NAME + "(%s)", callArgs);
    }

    private String callGenerateBidV3(List<JSScriptArgument> args) {
        return String.format(
                GENERATE_BID_FUNCTION_NAME + "(%s)",
                args.stream()
                        .map(JSScriptArgument::name)
                        .collect(Collectors.joining(ARG_PASSING_SEPARATOR)));
    }

    private String callScoreAd(List<JSScriptArgument> otherArgs) {
        StringBuilder callArgs =
                new StringBuilder(
                        String.format(
                                "%s.%s, %s.%s",
                                AD_VAR_NAME,
                                AdWithBidArgumentUtil.AD_FIELD_NAME,
                                AD_VAR_NAME,
                                AdWithBidArgumentUtil.BID_FIELD_NAME));
        for (JSScriptArgument currArg : otherArgs) {
            callArgs.append(String.format(",%s", currArg.name()));
        }
        return String.format(SCORE_AD_FUNCTION_NAME + "(%s)", callArgs);
    }

    private String callSelectOutcome(List<JSScriptArgument> otherArgs) {
        StringBuilder callArgs = new StringBuilder(ADS_ARG_NAME);
        for (JSScriptArgument currArg : otherArgs) {
            callArgs.append(String.format(",%s", currArg.name()));
        }
        return String.format("selectOutcome(%s)", callArgs);
    }

    static class AuctionScriptResult {
        public final int status;
        public final JSONArray results;

        AuctionScriptResult(int status, JSONArray results) {
            this.status = status;
            this.results = results;
        }
    }

    JSScriptArgument translateCustomAudience(DBCustomAudience customAudience) throws JSONException {
        ImmutableList.Builder<JSScriptArgument> adsArg = ImmutableList.builder();
        for (DBAdData ad : customAudience.getAds()) {
            adsArg.add(mAdDataArgumentUtil.asRecordArgument("ignored", ad));
        }
        // TODO(b/273357664): Verify with product on the set of fields we want to include.
        return recordArg(
                CUSTOM_AUDIENCE_ARG_NAME,
                stringArg("owner", customAudience.getOwner()),
                stringArg("name", customAudience.getName()),
                jsonArg("userBiddingSignals", customAudience.getUserBiddingSignals()),
                arrayArg("ads", adsArg.build()));
    }

    private static Uri extractValidUri(String uriString) {
        if (Strings.isNullOrEmpty(uriString) || !URLUtil.isValidUrl(uriString)) {
            return Uri.EMPTY;
        }
        return Uri.parse(uriString);
    }
}
