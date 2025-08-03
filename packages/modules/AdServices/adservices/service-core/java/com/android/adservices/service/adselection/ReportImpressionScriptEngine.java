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

import static com.android.adservices.service.common.JsonUtils.getStringFromJson;
import static com.android.adservices.service.js.JSScriptArgument.jsonArg;
import static com.android.adservices.service.js.JSScriptArgument.numericArg;
import static com.android.adservices.service.js.JSScriptArgument.stringArg;

import static com.google.common.util.concurrent.Futures.transform;

import android.adservices.adselection.AdSelectionConfig;
import android.adservices.common.AdSelectionSignals;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.net.Uri;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.adselection.CustomAudienceSignals;
import com.android.adservices.service.common.RetryStrategy;
import com.android.adservices.service.js.IsolateSettings;
import com.android.adservices.service.js.JSScriptArgument;
import com.android.adservices.service.js.JSScriptEngine;
import com.android.internal.util.Preconditions;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Utility class to execute a reporting script. Current implementation is thread safe but relies on
 * a singleton JS execution environment and will serialize calls done either using the same or
 * different instances of {@link ReportImpressionScriptEngine}. This will change once we will use
 * the new WebView API.
 *
 * <p>This class is thread safe but, for performance reasons, it is suggested to use one instance
 * per thread. See the threading comments for {@link JSScriptEngine}.
 */
public class ReportImpressionScriptEngine {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    private static final String TAG = "ReportImpressionScriptEngine";

    // TODO: (b/228094391): Put these common constants in a separate class
    private static final int JS_SCRIPT_STATUS_SUCCESS = 0;
    public static final String RESULTS_FIELD_NAME = "results";
    public static final String STATUS_FIELD_NAME = "status";
    public static final String AD_SELECTION_SIGNALS_ARG_NAME = "selection_signals";
    public static final String PER_BUYER_SIGNALS_ARG_NAME = "per_buyer_signals";
    public static final String SIGNALS_FOR_BUYER_ARG_NAME = "signals_for_buyer";
    public static final String CONTEXTUAL_SIGNALS_ARG_NAME = "contextual_signals";
    public static final String CUSTOM_AUDIENCE_REPORTING_SIGNALS_ARG_NAME =
            "custom_audience_reporting_signals";
    public static final String AD_SELECTION_CONFIG_ARG_NAME = "ad_selection_config";
    public static final String BID_ARG_NAME = "bid";
    public static final String RENDER_URI_ARG_NAME = "render_uri";
    public static final String SIGNALS_FOR_BUYER_RESPONSE_NAME = "signals_for_buyer";
    public static final String REPORTING_URI_RESPONSE_NAME = "reporting_uri";
    public static final String REPORT_RESULT_FUNC_NAME = "reportResult";
    public static final String REPORT_WIN_FUNC_NAME = "reportWin";
    public static final String INTERACTION_REPORTING_URIS_RESPONSE_NAME =
            "interaction_reporting_uris";

    public static final String INTERACTION_KEY_ARG_NAME = "interaction_key";
    public static final String INTERACTION_REPORTING_URI_ARG_NAME = "interaction_reporting_uri";

    public static final String REPORT_RESULT_ENTRY_NAME =
            REPORT_RESULT_FUNC_NAME + JSScriptEngine.ENTRY_POINT_FUNC_NAME;
    public static final String REPORT_WIN_ENTRY_NAME =
            REPORT_WIN_FUNC_NAME + JSScriptEngine.ENTRY_POINT_FUNC_NAME;

    public static final String REGISTER_BEACON_JS =
            "function isString(s) {\n"
                    + "    return typeof s === 'string' || s instanceof String;\n"
                    + "}\n"
                    + "\n"
                    + "function isDict(dict) {\n"
                    + "return dict !== null && typeof dict === 'object' && !Array.isArray(dict);\n"
                    + "}"
                    + "\n"
                    + "const interaction_reporting_uris = [];\n"
                    + "\n"
                    + " function registerAdBeacon(beacons) {\n"
                    + "     if(interaction_reporting_uris.length) {\n"
                    + "         throw new TypeError(\"registerAdBeacon may be called at most "
                    + "once\");\n"
                    + "     }\n"
                    + "\n"
                    + "     // Validating that beacons is a dictionary\n"
                    + "     if (!isDict(beacons)) {\n"
                    + "         throw new TypeError(\"registerAdBeacon requires 1 object "
                    + "parameter\");\n"
                    + "     }\n"
                    + "\n"
                    + "     for (const key in beacons) {\n"
                    + "         // Validating that both key and value are strings\n"
                    + "         if(!(isString(key) && isString(beacons[key]))) {\n"
                    + "             throw new TypeError(\"registerAdBeacon object attributes must"
                    + " be strings\");\n"
                    + "         }\n"
                    + "         interaction_reporting_uris.push({"
                    + INTERACTION_KEY_ARG_NAME
                    + ":key, "
                    + INTERACTION_REPORTING_URI_ARG_NAME
                    + ":beacons[key]});\n"
                    + "     }\n"
                    + " }\n";

    public static final String ADD_INTERACTION_REPORTING_URIS_TO_RESULT_JS =
            "if(results.hasOwnProperty('results')) {\n"
                    + "    if(typeof interaction_reporting_uris !== 'undefined') {\n"
                    + "        results['results']['interaction_reporting_uris'] = "
                    + "interaction_reporting_uris\n"
                    + "    }\n"
                    + "}";

    public static final String REPORT_RESULT_ENTRY_JS =
            "function "
                    + REPORT_RESULT_ENTRY_NAME
                    + "(ad_selection_config, render_uri, bid, contextual_signals) {\n"
                    + "    let results = reportResult(ad_selection_config, render_uri, bid,"
                    + " contextual_signals);\n";
    public static final String REPORT_WIN_ENTRY_JS =
            "function "
                    + REPORT_WIN_ENTRY_NAME
                    + "(ad_selection_signals, per_buyer_signals, signals_for_buyer"
                    + " ,contextual_signals, custom_audience_signals) {\n"
                    + "    let results = reportWin(ad_selection_signals, per_buyer_signals,"
                    + " signals_for_buyer ,contextual_signals, custom_audience_signals);\n";

    public static final String RETURN_RESULT_JS = "return results;\n" + "}";

    private final JSScriptEngine mJsEngine;
    // Used for the Futures.transform calls to compose futures.
    private final Executor mExecutor = MoreExecutors.directExecutor();
    private final Supplier<Boolean> mEnforceMaxHeapSizeFeatureSupplier;
    private final Supplier<Long> mMaxHeapSizeBytesSupplier;
    private final RegisterAdBeaconScriptEngineHelper mRegisterAdBeaconScriptEngineHelper;
    private final RetryStrategy mRetryStrategy;

    public ReportImpressionScriptEngine(
            Context context,
            Supplier<Boolean> enforceMaxHeapSizeFeatureSupplier,
            Supplier<Long> maxHeapSizeBytesSupplier,
            RegisterAdBeaconScriptEngineHelper registerAdBeaconScriptEngineHelper,
            RetryStrategy retryStrategy) {
        mJsEngine = JSScriptEngine.getInstance(context, sLogger);
        mEnforceMaxHeapSizeFeatureSupplier = enforceMaxHeapSizeFeatureSupplier;
        mMaxHeapSizeBytesSupplier = maxHeapSizeBytesSupplier;
        mRegisterAdBeaconScriptEngineHelper = registerAdBeaconScriptEngineHelper;
        mRetryStrategy = retryStrategy;
    }

    /**
     * @param decisionLogicJS Javascript containing the reportResult() function
     * @param adSelectionConfig Configuration object passed by the SDK containing various signals to
     *     be used in ad selection and reporting. See {@link AdSelectionConfig} for more details
     * @param renderUri URI to render the advert, is an input to the reportResult() function
     * @param bid Bid for the winning ad, is an input to the reportResult() function
     * @param contextualSignals another input to reportResult(), contains fields such as appName
     * @return The result of invoking the {@code reportResult} function in the given {@code
     *     decisionLogicJS} JS script for the {@code adSelectionConfig} bid, and signals provided.
     *     Will return an empty Uri if the script fails for any reason.
     * @throws JSONException If any of the signals are not a valid JSON object.
     */
    public ListenableFuture<SellerReportingResult> reportResult(
            @NonNull String decisionLogicJS,
            @NonNull AdSelectionConfig adSelectionConfig,
            @NonNull Uri renderUri,
            @NonNull double bid,
            @NonNull AdSelectionSignals contextualSignals)
            throws JSONException, IllegalStateException {
        Objects.requireNonNull(decisionLogicJS);
        Objects.requireNonNull(adSelectionConfig);
        Objects.requireNonNull(renderUri);
        Objects.requireNonNull(contextualSignals);

        sLogger.v("Reporting result");
        ImmutableList<JSScriptArgument> arguments =
                ImmutableList.<JSScriptArgument>builder()
                        .add(
                                AdSelectionConfigArgumentUtil.asScriptArgument(
                                        adSelectionConfig, AD_SELECTION_CONFIG_ARG_NAME))
                        .add(stringArg(RENDER_URI_ARG_NAME, renderUri.toString()))
                        .add(numericArg(BID_ARG_NAME, bid))
                        .add(jsonArg(CONTEXTUAL_SIGNALS_ARG_NAME, contextualSignals.toString()))
                        .build();

        return transform(
                runReportingScript(
                        mRegisterAdBeaconScriptEngineHelper.injectReportingJs(
                                decisionLogicJS, REPORT_RESULT_ENTRY_JS),
                        REPORT_RESULT_ENTRY_NAME,
                        arguments),
                mRegisterAdBeaconScriptEngineHelper::handleReportResultOutput,
                mExecutor);
    }

    /**
     * @param biddingLogicJS Javascript containing the reportWin() function
     * @param adSelectionSignals One of the opaque fields of {@link AdSelectionConfig} that is an
     *     input to reportWin()
     * @param perBuyerSignals The value associated with the key {@code buyer} from the {@code
     *     perBuyerSignals} map in {@link AdSelectionConfig}
     * @param signalsForBuyer One of the fields returned by the seller's reportResult(), intended to
     *     be passed to the buyer
     * @param contextualSignals another input to reportWin(), contains fields such as appName
     * @param customAudienceSignals an input to reportWin(), which contains information about the
     *     custom audience the winning ad originated from
     * @return The result of invoking the {@code reportResult} function in the given {@code
     *     decisionLogicJS} JS script for the {@code adSelectionConfig} bid, and signals provided.
     *     Will return an empty Uri if the script fails for any reason.
     * @throws JSONException If any of the signals are not a valid JSON object.
     */
    public ListenableFuture<BuyerReportingResult> reportWin(
            @NonNull String biddingLogicJS,
            @NonNull AdSelectionSignals adSelectionSignals,
            @NonNull AdSelectionSignals perBuyerSignals,
            @NonNull AdSelectionSignals signalsForBuyer,
            @NonNull AdSelectionSignals contextualSignals,
            @NonNull CustomAudienceSignals customAudienceSignals)
            throws JSONException, IllegalStateException {
        Objects.requireNonNull(biddingLogicJS);
        Objects.requireNonNull(adSelectionSignals);
        Objects.requireNonNull(perBuyerSignals);
        Objects.requireNonNull(signalsForBuyer);
        Objects.requireNonNull(contextualSignals);
        Objects.requireNonNull(customAudienceSignals);
        sLogger.v("Reporting win");

        ImmutableList<JSScriptArgument> arguments =
                ImmutableList.<JSScriptArgument>builder()
                        .add(jsonArg(AD_SELECTION_SIGNALS_ARG_NAME, adSelectionSignals.toString()))
                        .add(jsonArg(PER_BUYER_SIGNALS_ARG_NAME, perBuyerSignals.toString()))
                        .add(jsonArg(SIGNALS_FOR_BUYER_ARG_NAME, signalsForBuyer.toString()))
                        .add(jsonArg(CONTEXTUAL_SIGNALS_ARG_NAME, contextualSignals.toString()))
                        .add(
                                CustomAudienceReportingSignalsArgumentUtil.asScriptArgument(
                                        CUSTOM_AUDIENCE_REPORTING_SIGNALS_ARG_NAME,
                                        customAudienceSignals))
                        .build();

        return transform(
                runReportingScript(
                        mRegisterAdBeaconScriptEngineHelper.injectReportingJs(
                                biddingLogicJS, REPORT_WIN_ENTRY_JS),
                        REPORT_WIN_ENTRY_NAME,
                        arguments),
                mRegisterAdBeaconScriptEngineHelper::handleReportWinOutput,
                mExecutor);
    }

    ListenableFuture<ReportingScriptResult> runReportingScript(
            String jsScript, String functionName, List<JSScriptArgument> args) {
        sLogger.v("Executing reporting script");
        try {
            return transform(
                    callReportingScript(jsScript, functionName, args),
                    this::parseReportingOutput,
                    mExecutor);
        } catch (Exception e) {
            throw new IllegalStateException("Illegal result returned by our calling function.", e);
        }
    }

    /**
     * @return a {@link ListenableFuture} containing the string representation of a JSON object
     *     containing two fields:
     *     <p>
     *     <ul>
     *       <li>{@code status} field that will be 0 in case of success or non-zero in case a
     *           failure is encountered. The function {@code reportingFunctionName} is assumed to
     *           return a JSON object containing at least a {@code status} field.
     *       <li>{@code results} with the results of the invocation of {@code reportingFunctionName}
     *     </ul>
     *     <p>
     */
    private ListenableFuture<String> callReportingScript(
            String jsScript, String functionName, List<JSScriptArgument> args)
            throws JSONException {
        IsolateSettings isolateSettings =
                mEnforceMaxHeapSizeFeatureSupplier.get()
                        ? IsolateSettings.forMaxHeapSizeEnforcementEnabled(
                                mMaxHeapSizeBytesSupplier.get())
                        : IsolateSettings.forMaxHeapSizeEnforcementDisabled();
        return mJsEngine.evaluate(jsScript, args, functionName, isolateSettings, mRetryStrategy);
    }

    @NonNull
    private ReportingScriptResult parseReportingOutput(@NonNull String reportScriptResult) {
        Objects.requireNonNull(reportScriptResult);
        sLogger.v("Parsing Reporting output");
        try {
            Preconditions.checkState(
                    !reportScriptResult.equals("null"),
                    "Null string result returned by report script!");

            JSONObject jsonResult = new JSONObject(reportScriptResult);

            return new ReportingScriptResult(
                    jsonResult.getInt(STATUS_FIELD_NAME),
                    jsonResult.getJSONObject(RESULTS_FIELD_NAME));
        } catch (JSONException e) {
            throw new IllegalStateException("Illegal result returned by our calling function.", e);
        }
    }

    static class ReportingScriptResult {
        public final int status;
        @NonNull public final JSONObject results;

        ReportingScriptResult(int status, @NonNull JSONObject results) {
            Objects.requireNonNull(results);

            this.status = status;
            this.results = results;
        }
    }

    static class SellerReportingResult {
        @NonNull private final AdSelectionSignals mSignalsForBuyer;
        @NonNull private final Uri mReportingUri;

        @Nullable
        private final List<InteractionUriRegistrationInfo> mInteractionUriRegistrationInfos;

        SellerReportingResult(
                @NonNull AdSelectionSignals signalsForBuyer,
                @NonNull Uri reportingUri,
                @NonNull List<InteractionUriRegistrationInfo> interactionUriRegistrationInfos) {
            Objects.requireNonNull(signalsForBuyer);
            Objects.requireNonNull(reportingUri);

            this.mSignalsForBuyer = signalsForBuyer;
            this.mReportingUri = reportingUri;
            this.mInteractionUriRegistrationInfos = interactionUriRegistrationInfos;
        }

        public AdSelectionSignals getSignalsForBuyer() {
            return mSignalsForBuyer;
        }

        public Uri getReportingUri() {
            return mReportingUri;
        }

        public List<InteractionUriRegistrationInfo> getInteractionReportingUris() {
            return mInteractionUriRegistrationInfos;
        }
    }

    static class BuyerReportingResult {
        @NonNull private final Uri mReportingUri;

        @Nullable
        private final List<InteractionUriRegistrationInfo> mInteractionUriRegistrationInfos;

        BuyerReportingResult(
                @NonNull Uri reportingUri,
                @NonNull List<InteractionUriRegistrationInfo> interactionUriRegistrationInfos) {
            mReportingUri = reportingUri;
            mInteractionUriRegistrationInfos = interactionUriRegistrationInfos;
        }

        public Uri getReportingUri() {
            return mReportingUri;
        }

        public List<InteractionUriRegistrationInfo> getInteractionReportingUris() {
            return mInteractionUriRegistrationInfos;
        }
    }

    /**
     * Interface that contains methods that are implemented differently depending on whether the
     * {@code registerAdBeacon} feature is enabled.
     */
    public interface RegisterAdBeaconScriptEngineHelper {
        /**
         * Creates the overall script to be evaluated by inserting the JS provided by buyer or
         * seller into a larger script that contains the entry function and reporting function.
         *
         * @param reportingJs JS provided by buyer or seller
         * @param entryJS {@code REPORT_RESULT_ENTRY_JS} or {@code REPORT_WIN_ENTRY_JS}
         * @return the overall script to be executed by the {@link JSScriptEngine}
         */
        String injectReportingJs(@NonNull String reportingJs, @NonNull String entryJS);

        /**
         * Parses the output from the invocation of the {@code reportWin} JS function and convert it
         * to a {@link BuyerReportingResult}. The script output has been pre-parsed into an {@link
         * ReportingScriptResult} object that will contain the script status code and JSONObject
         * that holds both {@code interactionReportingUris} and {@code reportingUri}. The method
         * will throw an exception if the status code is not {@link #JS_SCRIPT_STATUS_SUCCESS} or if
         * there has been any problem parsing the JS response.
         *
         * @throws IllegalStateException If the result is unsuccessful or doesn't match the expected
         *     structure.
         */
        BuyerReportingResult handleReportWinOutput(@NonNull ReportingScriptResult reportResult);

        /**
         * Parses the output from the invocation of the {@code reportResult} JS function and
         * converts it to a {@link SellerReportingResult}. The script output has been pre-parsed
         * into an {@link ReportingScriptResult} object that will contain the script status code and
         * JSONObject that holds the {@code reportingUri}, {@code signalsForBuyer}, and {@code
         * interactionReportingUris}. The method will throw an exception if the status code is not
         * {@link #JS_SCRIPT_STATUS_SUCCESS} or if there has been any problem parsing the JS
         * response.
         *
         * @throws IllegalStateException If the result is unsuccessful or doesn't match the expected
         *     structure.
         */
        SellerReportingResult handleReportResultOutput(@NonNull ReportingScriptResult reportResult);
    }

    /**
     * Implements {@link RegisterAdBeaconScriptEngineHelper} with the {@code registerAdBeacon}
     * emabled.
     */
    public static class RegisterAdBeaconScriptEngineHelperEnabled
            implements RegisterAdBeaconScriptEngineHelper {

        long mMaxInteractionReportingUrisSize;

        public RegisterAdBeaconScriptEngineHelperEnabled(long maxInteractionReportingUrisSize) {
            mMaxInteractionReportingUrisSize = maxInteractionReportingUrisSize;
        }

        @Override
        public String injectReportingJs(@NonNull String reportingJs, @NonNull String entryJS) {
            return String.format(
                    "%s\n%s\n%s\n%s\n%s",
                    REGISTER_BEACON_JS,
                    reportingJs,
                    entryJS,
                    ADD_INTERACTION_REPORTING_URIS_TO_RESULT_JS,
                    RETURN_RESULT_JS);
        }

        @Override
        public BuyerReportingResult handleReportWinOutput(
                @NonNull ReportingScriptResult reportResult) {
            Objects.requireNonNull(reportResult);
            sLogger.v("Handling report win output");

            Preconditions.checkState(
                    reportResult.status == JS_SCRIPT_STATUS_SUCCESS,
                    "Report Result script failed!");
            try {
                Uri reportingUri =
                        Uri.parse(
                                getStringFromJson(
                                        reportResult.results, REPORTING_URI_RESPONSE_NAME));

                JSONArray interactionUriJsonArray =
                        reportResult.results.getJSONArray(INTERACTION_REPORTING_URIS_RESPONSE_NAME);

                List<InteractionUriRegistrationInfo> interactionUriRegistrationInfoList =
                        extractInteractionUriRegistrationInfoFromArray(interactionUriJsonArray);

                return new BuyerReportingResult(reportingUri, interactionUriRegistrationInfoList);
            } catch (Exception e) {
                throw new IllegalStateException("Result does not match expected structure!");
            }
        }

        @Override
        public SellerReportingResult handleReportResultOutput(
                @NonNull ReportingScriptResult reportResult) {
            Objects.requireNonNull(reportResult);
            sLogger.v("Handling reporting result output");
            Preconditions.checkState(
                    reportResult.status == JS_SCRIPT_STATUS_SUCCESS,
                    "Report Result script failed!");
            try {
                AdSelectionSignals adSelectionSignals =
                        AdSelectionSignals.fromString(
                                getStringFromJson(
                                        reportResult.results, SIGNALS_FOR_BUYER_RESPONSE_NAME));

                Uri reportingUri =
                        Uri.parse(
                                getStringFromJson(
                                        reportResult.results, REPORTING_URI_RESPONSE_NAME));

                JSONArray interactionUriJsonArray =
                        reportResult.results.getJSONArray(INTERACTION_REPORTING_URIS_RESPONSE_NAME);

                List<InteractionUriRegistrationInfo> interactionUriRegistrationInfoList =
                        extractInteractionUriRegistrationInfoFromArray(interactionUriJsonArray);

                return new SellerReportingResult(
                        adSelectionSignals, reportingUri, interactionUriRegistrationInfoList);
            } catch (Exception e) {
                sLogger.e(e.getMessage());
                throw new IllegalStateException("Result does not match expected structure!");
            }
        }

        /**
         * Parses each entry of {@code interactionUriJsonArray} into an {@link
         * InteractionUriRegistrationInfo} object and adds it to the resulting list. Any entry that
         * fails to parse properly into an {@link InteractionUriRegistrationInfo} object will be
         * skipped and not added to the list. If the size of {@code interactionUriJsonArray} is
         * larger than {@link #mMaxInteractionReportingUrisSize}, we will only add the first {@link
         * #mMaxInteractionReportingUrisSize} entries to the result.
         */
        @NonNull
        private List<InteractionUriRegistrationInfo> extractInteractionUriRegistrationInfoFromArray(
                JSONArray interactionUriJsonArray) {
            ImmutableList.Builder<InteractionUriRegistrationInfo> interactionReportingUris =
                    ImmutableList.builder();

            long maxResultArraySize =
                    Math.min(interactionUriJsonArray.length(), mMaxInteractionReportingUrisSize);

            for (int i = 0; i < maxResultArraySize; i++) {
                try {
                    InteractionUriRegistrationInfo interactionUriRegistrationInfoToAdd =
                            InteractionUriRegistrationInfo.fromJson(
                                    interactionUriJsonArray.getJSONObject(i));
                    interactionReportingUris.add(interactionUriRegistrationInfoToAdd);
                } catch (Exception e) {
                    sLogger.v(
                            "Error converting this JSONObject to InteractionUriRegistrationInfo,"
                                    + " skipping");
                    sLogger.v(e.toString());
                }
            }
            return interactionReportingUris.build();
        }
    }

    /**
     * Implements {@link RegisterAdBeaconScriptEngineHelper} with the {@code registerAdBeacon}
     * disabled.
     */
    public static class RegisterAdBeaconScriptEngineHelperDisabled
            implements RegisterAdBeaconScriptEngineHelper {

        @Override
        public String injectReportingJs(@NonNull String reportingJs, @NonNull String entryJS) {
            return String.format("%s\n%s\n%s", reportingJs, entryJS, RETURN_RESULT_JS);
        }

        @Override
        public BuyerReportingResult handleReportWinOutput(
                @NonNull ReportingScriptResult reportResult) {
            Objects.requireNonNull(reportResult);
            sLogger.v("Handling report win output");

            Preconditions.checkState(
                    reportResult.status == JS_SCRIPT_STATUS_SUCCESS,
                    "Report Result script failed!");
            try {
                Uri reportingUri =
                        Uri.parse(
                                getStringFromJson(
                                        reportResult.results, REPORTING_URI_RESPONSE_NAME));

                // Do not parse beacons since flag is disabled
                return new BuyerReportingResult(reportingUri, null);
            } catch (Exception e) {
                throw new IllegalStateException("Result does not match expected structure!");
            }
        }

        @Override
        public SellerReportingResult handleReportResultOutput(
                @NonNull ReportingScriptResult reportResult) {
            Objects.requireNonNull(reportResult);
            sLogger.v("Handling reporting result output");
            Preconditions.checkState(
                    reportResult.status == JS_SCRIPT_STATUS_SUCCESS,
                    "Report Result script failed!");
            try {
                AdSelectionSignals adSelectionSignals =
                        AdSelectionSignals.fromString(
                                getStringFromJson(
                                        reportResult.results, SIGNALS_FOR_BUYER_RESPONSE_NAME));

                Uri reportingUri =
                        Uri.parse(
                                getStringFromJson(
                                        reportResult.results, REPORTING_URI_RESPONSE_NAME));

                return new SellerReportingResult(adSelectionSignals, reportingUri, null);
            } catch (Exception e) {
                sLogger.e(e.getMessage());
                throw new IllegalStateException("Result does not match expected structure!");
            }
        }
    }
}
