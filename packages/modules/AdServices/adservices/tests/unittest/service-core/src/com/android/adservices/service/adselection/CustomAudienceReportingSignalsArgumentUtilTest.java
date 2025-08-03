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

import static com.android.adservices.service.adselection.CustomAudienceReportingSignalsArgumentUtil.NAME_FIELD_NAME;
import static com.android.adservices.service.adselection.ReportImpressionScriptEngine.CUSTOM_AUDIENCE_REPORTING_SIGNALS_ARG_NAME;
import static com.android.adservices.service.js.JSScriptArgument.recordArg;
import static com.android.adservices.service.js.JSScriptArgument.stringArg;

import static com.google.common.truth.Truth.assertThat;

import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.data.adselection.CustomAudienceSignals;
import com.android.adservices.service.js.JSScriptArgument;

import org.json.JSONException;
import org.junit.Rule;
import org.junit.Test;

import java.time.Instant;

public class CustomAudienceReportingSignalsArgumentUtilTest {
    private final CustomAudienceSignals mCustomAudienceSignals = createCustomAudienceSignals("1");

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testConversionToScriptArgument() throws JSONException {
        JSScriptArgument caSignalJsArgument =
                CustomAudienceReportingSignalsArgumentUtil.asScriptArgument(
                        CUSTOM_AUDIENCE_REPORTING_SIGNALS_ARG_NAME, mCustomAudienceSignals);
        matchCustomAudienceSignals(
                CUSTOM_AUDIENCE_REPORTING_SIGNALS_ARG_NAME,
                caSignalJsArgument,
                mCustomAudienceSignals);
    }

    private CustomAudienceSignals createCustomAudienceSignals(final String uniqueCASignalsPostfix) {
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
            final CustomAudienceSignals actualSignals) {
        assertThat(expectedJsArgument)
                .isEqualTo(recordArg(argName, stringArg(NAME_FIELD_NAME, actualSignals.getName())));
    }
}
