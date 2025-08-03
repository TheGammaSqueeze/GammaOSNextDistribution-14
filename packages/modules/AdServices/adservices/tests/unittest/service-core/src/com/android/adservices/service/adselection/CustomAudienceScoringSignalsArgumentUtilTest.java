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

import static com.android.adservices.service.adselection.AdSelectionScriptEngine.CUSTOM_AUDIENCE_SCORING_SIGNALS_ARG_NAME;
import static com.android.adservices.service.js.JSScriptArgument.recordArg;
import static com.android.adservices.service.js.JSScriptArgument.stringArg;

import static com.google.common.truth.Truth.assertThat;

import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.data.adselection.CustomAudienceSignals;
import com.android.adservices.service.js.JSScriptArgument;

import com.google.common.collect.ImmutableList;

import org.json.JSONException;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

import java.time.Instant;

public class CustomAudienceScoringSignalsArgumentUtilTest {
    private final CustomAudienceSignals mCustomAudienceSignals1 = createCustomAudience("1");
    private final CustomAudienceSignals mCustomAudienceSignals2 = createCustomAudience("2");

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testConversionToScriptArgument() throws JSONException {
        JSScriptArgument caSignalJsArgument =
                CustomAudienceScoringSignalsArgumentUtil.asScriptArgument(
                        CUSTOM_AUDIENCE_SCORING_SIGNALS_ARG_NAME, mCustomAudienceSignals1);
        matchCustomAudienceSignals(
                CUSTOM_AUDIENCE_SCORING_SIGNALS_ARG_NAME,
                caSignalJsArgument,
                mCustomAudienceSignals1);
    }

    @Test
    public void testConversionToScriptArguments() throws JSONException {
        ImmutableList<CustomAudienceSignals> customAudienceSignalsList =
                ImmutableList.of(mCustomAudienceSignals1, mCustomAudienceSignals2);
        JSScriptArgument customAudienceSignalsArgumentList =
                CustomAudienceScoringSignalsArgumentUtil.asScriptArgument(
                        CUSTOM_AUDIENCE_SCORING_SIGNALS_ARG_NAME, customAudienceSignalsList);

        Assert.assertEquals(
                "Custom Audience Signals JS argument name mismatch",
                CUSTOM_AUDIENCE_SCORING_SIGNALS_ARG_NAME,
                customAudienceSignalsArgumentList.name());

        String expectedJsArgValue =
                "const "
                        + CUSTOM_AUDIENCE_SCORING_SIGNALS_ARG_NAME
                        + " = ["
                        + createCAJsArrayListArgument(mCustomAudienceSignals1)
                        + ","
                        + createCAJsArrayListArgument(mCustomAudienceSignals2)
                        + "];";

        Assert.assertEquals(
                "Custom Audience Signals Js arg variable declaration mismatch",
                expectedJsArgValue.strip().replaceAll("\n", ""),
                customAudienceSignalsArgumentList
                        .variableDeclaration()
                        .strip()
                        .replaceAll("\n", ""));
    }

    /**
     * Mimics the asScript argument and extracts the value part of the javaScript variable. For ex
     * variable: ["const ignored = {...};"] becomes ["ignored" : {...}]. This is how JS arguments
     * are passed in list, when we want to send multiple of the same type. See {@code
     * JSScriptArgument#recordArg(java.lang.String, java.util.List)}
     *
     * @param caSignals An instance of CustomAudienceSignals
     * @return a js argument string which could be added in a list type js param
     */
    private String createCAJsArrayListArgument(final CustomAudienceSignals caSignals) {
        try {
            return CustomAudienceScoringSignalsArgumentUtil.asScriptArgument("ignored", caSignals)
                    .variableDeclaration()
                    .split("=", 2)[1]
                    .trim()
                    .replace(";", "");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return "";
    }

    private CustomAudienceSignals createCustomAudience(final String uniqueCASignalsPostfix) {
        return new CustomAudienceSignals.Builder()
                .setOwner("test_owner" + uniqueCASignalsPostfix)
                .setBuyer(AdTechIdentifier.fromString("test_buyer" + uniqueCASignalsPostfix))
                .setName("test_name" + uniqueCASignalsPostfix)
                .setActivationTime(Instant.now())
                .setExpirationTime(Instant.now())
                .setUserBiddingSignals(
                        AdSelectionSignals.fromString(
                                "{\"user_bidding_signals\":" + uniqueCASignalsPostfix + "}"))
                .build();
    }

    private void matchCustomAudienceSignals(
            final String argName,
            final JSScriptArgument expectedJsArgument,
            final CustomAudienceSignals actualSignals)
            throws JSONException {
        assertThat(expectedJsArgument)
                .isEqualTo(
                        recordArg(
                                argName,
                                stringArg(
                                        CustomAudienceScoringSignalsArgumentUtil.BUYER_FIELD_NAME,
                                        actualSignals.getBuyer().toString()),
                                stringArg(
                                        CustomAudienceScoringSignalsArgumentUtil.NAME_FIELD_NAME,
                                        actualSignals.getName())));
    }
}
