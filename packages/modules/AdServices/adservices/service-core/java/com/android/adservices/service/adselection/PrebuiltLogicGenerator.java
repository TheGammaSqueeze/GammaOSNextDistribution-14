/*
 * Copyright (C) 2023 The Android Open Source Project
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

import android.annotation.NonNull;
import android.net.Uri;

import com.android.adservices.LoggerFactory;
import com.android.adservices.service.Flags;
import com.android.internal.annotations.VisibleForTesting;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Generates JS scripts given prebuilt URIs.
 *
 * <p>Prebuilt URIs are in '{@code ad-selection-prebuilt://<use-case>/<name>?<query-param>}'
 *
 * <p>
 */
public class PrebuiltLogicGenerator {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    // TODO (b/271055928): Investigate abstracting use cases to their own classes with different
    //  flavors
    @VisibleForTesting
    static final String UNKNOWN_PREBUILT_IDENTIFIER = "Unknown prebuilt identifier: '%s'!";

    @VisibleForTesting
    static final String MISSING_PREBUILT_PARAMS = "Missing prebuilt URI query params: '%s'!";

    @VisibleForTesting
    static final String PREBUILT_FEATURE_IS_DISABLED = "Prebuilt Uri feature is disabled!";

    @VisibleForTesting
    static final String UNRECOGNIZED_PREBUILT_PARAMS =
            "Unrecognized prebuilt URI query params: '%s'!";

    @VisibleForTesting
    public static final String AD_SELECTION_PREBUILT_SCHEMA = "ad-selection-prebuilt";

    @VisibleForTesting public static final String AD_SELECTION_USE_CASE = "ad-selection";

    @VisibleForTesting
    public static final String AD_SELECTION_HIGHEST_BID_WINS = "highest-bid-wins";

    @VisibleForTesting
    static final String AD_SELECTION_HIGHEST_BID_WINS_JS =
            "//From prebuilts AD_SELECTION_HIGHEST_BID_WINS_JS\n"
                    + "function scoreAd(ad, bid, auction_config, seller_signals,"
                    + " trusted_scoring_signals,\n"
                    + "    contextual_signal, user_signal, custom_audience_signal) {\n"
                    + "    return {'status': 0, 'score': bid };\n"
                    + "}\n"
                    + "function reportResult(ad_selection_config, render_uri, bid,"
                    + " contextual_signals) {\n"
                    + "    // Add the address of your reporting server here\n"
                    + "    let reporting_address = '${reportingUrl}';\n"
                    + "    return {'status': 0, 'results': {'signals_for_buyer':"
                    + " '{\"signals_for_buyer\" : 1}'\n"
                    + "            , 'reporting_uri': reporting_address + '?render_uri='\n"
                    + "                + render_uri + '?bid=' + bid }};\n"
                    + "}";

    public static final String AD_SELECTION_FROM_OUTCOMES_USE_CASE = "ad-selection-from-outcomes";

    public static final String AD_OUTCOME_SELECTION_WATERFALL_MEDIATION_TRUNCATION =
            "waterfall-mediation-truncation";

    @VisibleForTesting
    static final String AD_OUTCOME_SELECTION_WATERFALL_MEDIATION_TRUNCATION_JS =
            "function selectOutcome(outcomes, selection_signals) {\n"
                    + "    const outcome_1p = outcomes[0];\n"
                    + "    const bid_floor = selection_signals.${bidFloor};\n"
                    + "    return {'status': 0, 'result': (outcome_1p.bid >= bid_floor) ?"
                    + " outcome_1p : null};\n"
                    + "}";

    @VisibleForTesting static final String NAMED_PARAM_TEMPLATE = "\\$\\{%s\\}";
    private static final Pattern PARAM_IDENTIFIER_REGEX_PATTERN =
            Pattern.compile(String.format(NAMED_PARAM_TEMPLATE, "(.*?)"));
    @NonNull private final Flags mFlags;

    public PrebuiltLogicGenerator(@NonNull Flags flags) {
        Objects.requireNonNull(flags);

        mFlags = flags;
    }

    /**
     * Returns true if the given URI is in FLEDGE Ad Selection Prebuilt format
     *
     * @param decisionUri URI to check
     * @return true if prebuilt URI, otherwise false
     */
    public boolean isPrebuiltUri(Uri decisionUri) {
        String scheme = decisionUri.getScheme();
        boolean isPrebuilt = Objects.nonNull(scheme) && scheme.equals(AD_SELECTION_PREBUILT_SCHEMA);
        sLogger.v("Checking if URI %s is of prebuilt schema: %s", decisionUri, isPrebuilt);
        sLogger.v("Prebuilt enabled flag: %s", mFlags.getFledgeAdSelectionPrebuiltUriEnabled());
        if (isPrebuilt && !mFlags.getFledgeAdSelectionPrebuiltUriEnabled()) {
            sLogger.e(PREBUILT_FEATURE_IS_DISABLED);
            throw new IllegalArgumentException(PREBUILT_FEATURE_IS_DISABLED);
        }
        return isPrebuilt;
    }

    /**
     * Returns the generated JS script from a valid prebuilt URI.
     *
     * @param prebuiltUri valid prebuilt URI. {@link IllegalArgumentException} is thrown if the
     *     given URI is not valid or supported.
     * @return JS script
     */
    public String jsScriptFromPrebuiltUri(Uri prebuiltUri) {

        if (!isPrebuiltUri(prebuiltUri)) {
            String err = String.format(UNKNOWN_PREBUILT_IDENTIFIER, prebuiltUri);
            sLogger.e(err);
            throw new IllegalArgumentException(err);
        }
        sLogger.v("Prebuilt enabled: %s", mFlags.getFledgeAdSelectionPrebuiltUriEnabled());
        if (!mFlags.getFledgeAdSelectionPrebuiltUriEnabled()) {
            sLogger.e(PREBUILT_FEATURE_IS_DISABLED);
            throw new IllegalArgumentException(PREBUILT_FEATURE_IS_DISABLED);
        }
        sLogger.v("Generating JS for URI: %s", prebuiltUri);
        String jsTemplate =
                getPrebuiltJsScriptTemplate(prebuiltUri.getHost(), prebuiltUri.getPath());
        sLogger.v("Template found for URI %s:%n%s", prebuiltUri, jsTemplate);

        Set<String> requiredParams = calculateRequiredParameters(jsTemplate);
        Set<String> queryParams = prebuiltUri.getQueryParameterNames();
        sLogger.v("Required parameters are calculated: %s", requiredParams);
        sLogger.v("Query parameters are calculated: %s", queryParams);

        crossValidateRequiredAndQueryParams(requiredParams, queryParams);

        for (String param : requiredParams) {
            if (!prebuiltUri.getQueryParameterNames().contains(param)) {
                String err = String.format(MISSING_PREBUILT_PARAMS, param);
                sLogger.e(err);
                throw new IllegalArgumentException(err);
            }

            jsTemplate =
                    jsTemplate.replaceAll(
                            String.format(NAMED_PARAM_TEMPLATE, param),
                            prebuiltUri.getQueryParameter(param));
        }
        sLogger.i("Final prebuilt JS is generated:%n%s", jsTemplate);

        return jsTemplate;
    }

    private Set<String> calculateRequiredParameters(String jsTemplate) {
        Set<String> requiredParameters = new HashSet<>();
        Matcher matcher = PARAM_IDENTIFIER_REGEX_PATTERN.matcher(jsTemplate);
        while (matcher.find()) {
            requiredParameters.add(matcher.group(1));
        }
        return requiredParameters;
    }

    private String getPrebuiltJsScriptTemplate(String prebuiltUseCase, String prebuiltName) {
        sLogger.v("Use case is '%s', prebuilt name is '%s'.", prebuiltUseCase, prebuiltName);
        switch (prebuiltUseCase) {
            case AD_SELECTION_USE_CASE:
                sLogger.v("Use case matched with %s", AD_SELECTION_USE_CASE);
                return getPrebuiltJsScriptTemplateForAdSelection(prebuiltName);
            case AD_SELECTION_FROM_OUTCOMES_USE_CASE:
                sLogger.v("Use case matched with %s", AD_SELECTION_FROM_OUTCOMES_USE_CASE);
                return getPrebuiltJsScriptTemplateForAdSelectionFromOutcome(prebuiltName);
            default:
                String err = String.format(UNKNOWN_PREBUILT_IDENTIFIER, prebuiltUseCase);
                sLogger.e(err);
                throw new IllegalArgumentException(err);
        }
    }

    private String getPrebuiltJsScriptTemplateForAdSelection(String prebuiltName) {
        switch (prebuiltName) {
            case "/" + AD_SELECTION_HIGHEST_BID_WINS + "/":
                sLogger.v("Prebuilt use case matched with %s", AD_SELECTION_HIGHEST_BID_WINS);
                return AD_SELECTION_HIGHEST_BID_WINS_JS;
            default:
                String err = String.format(UNKNOWN_PREBUILT_IDENTIFIER, prebuiltName);
                sLogger.e(err);
                throw new IllegalArgumentException(err);
        }
    }

    private String getPrebuiltJsScriptTemplateForAdSelectionFromOutcome(String prebuiltName) {
        switch (prebuiltName) {
            case "/" + AD_OUTCOME_SELECTION_WATERFALL_MEDIATION_TRUNCATION + "/":
                sLogger.v(
                        "Prebuilt use case matched with %s",
                        AD_OUTCOME_SELECTION_WATERFALL_MEDIATION_TRUNCATION);
                return AD_OUTCOME_SELECTION_WATERFALL_MEDIATION_TRUNCATION_JS;
            default:
                String err = String.format(UNKNOWN_PREBUILT_IDENTIFIER, prebuiltName);
                sLogger.e(err);
                throw new IllegalArgumentException(err);
        }
    }

    private void crossValidateRequiredAndQueryParams(
            Set<String> requiredParams, Set<String> queryParams) {
        Set<String> erroneousParams;
        if (!(erroneousParams =
                        requiredParams.stream()
                                .filter(p -> !queryParams.contains(p))
                                .collect(Collectors.toSet()))
                .isEmpty()) {
            String err = String.format(MISSING_PREBUILT_PARAMS, erroneousParams);
            sLogger.e(err);
            throw new IllegalArgumentException(err);
        }
        if (!(erroneousParams =
                        queryParams.stream()
                                .filter(p -> !requiredParams.contains(p))
                                .collect(Collectors.toSet()))
                .isEmpty()) {
            String err = String.format(UNRECOGNIZED_PREBUILT_PARAMS, erroneousParams);
            sLogger.e(err);
            throw new IllegalArgumentException(err);
        }
    }
}
