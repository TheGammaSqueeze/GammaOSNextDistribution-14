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

import static com.android.adservices.service.adselection.PrebuiltLogicGenerator.AD_OUTCOME_SELECTION_WATERFALL_MEDIATION_TRUNCATION;
import static com.android.adservices.service.adselection.PrebuiltLogicGenerator.AD_OUTCOME_SELECTION_WATERFALL_MEDIATION_TRUNCATION_JS;
import static com.android.adservices.service.adselection.PrebuiltLogicGenerator.AD_SELECTION_FROM_OUTCOMES_USE_CASE;
import static com.android.adservices.service.adselection.PrebuiltLogicGenerator.AD_SELECTION_HIGHEST_BID_WINS;
import static com.android.adservices.service.adselection.PrebuiltLogicGenerator.AD_SELECTION_HIGHEST_BID_WINS_JS;
import static com.android.adservices.service.adselection.PrebuiltLogicGenerator.AD_SELECTION_PREBUILT_SCHEMA;
import static com.android.adservices.service.adselection.PrebuiltLogicGenerator.AD_SELECTION_USE_CASE;
import static com.android.adservices.service.adselection.PrebuiltLogicGenerator.MISSING_PREBUILT_PARAMS;
import static com.android.adservices.service.adselection.PrebuiltLogicGenerator.NAMED_PARAM_TEMPLATE;
import static com.android.adservices.service.adselection.PrebuiltLogicGenerator.PREBUILT_FEATURE_IS_DISABLED;
import static com.android.adservices.service.adselection.PrebuiltLogicGenerator.UNKNOWN_PREBUILT_IDENTIFIER;
import static com.android.adservices.service.adselection.PrebuiltLogicGenerator.UNRECOGNIZED_PREBUILT_PARAMS;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

import android.net.Uri;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.service.Flags;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class PrebuiltLogicGeneratorTest {
    private PrebuiltLogicGenerator mPrebuiltLogicGenerator;
    private Flags mFlags;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setup() {
        mFlags = new PrebuiltLogicGeneratorTestFlags(true);
        mPrebuiltLogicGenerator = new PrebuiltLogicGenerator(mFlags);
    }

    @Test
    public void testPrebuiltLogicRunnerGeneratesWaterfallScriptSuccess() {
        String paramKey = "bidFloor";
        String paramValue = "bid_floor";
        Uri prebuiltUri =
                Uri.parse(
                        String.format(
                                "%s://%s/%s/?%s=%s",
                                AD_SELECTION_PREBUILT_SCHEMA,
                                AD_SELECTION_FROM_OUTCOMES_USE_CASE,
                                AD_OUTCOME_SELECTION_WATERFALL_MEDIATION_TRUNCATION,
                                paramKey,
                                paramValue));

        String result = mPrebuiltLogicGenerator.jsScriptFromPrebuiltUri(prebuiltUri);
        assertEquals(
                AD_OUTCOME_SELECTION_WATERFALL_MEDIATION_TRUNCATION_JS.replaceAll(
                        String.format(NAMED_PARAM_TEMPLATE, paramKey), paramValue),
                result);
    }

    @Test
    public void testPrebuiltLogicRunnerNotPrebuiltSchemaReturnFalseSuccess() {
        String paramKey = "bidFloor";
        String paramValue = "bid_floor";
        Uri prebuiltUri =
                Uri.parse(
                        String.format(
                                "%s://%s/%s/?%s=%s",
                                "not-ad-selection-schema",
                                AD_SELECTION_FROM_OUTCOMES_USE_CASE,
                                AD_OUTCOME_SELECTION_WATERFALL_MEDIATION_TRUNCATION,
                                paramKey,
                                paramValue));

        assertFalse(mPrebuiltLogicGenerator.isPrebuiltUri(prebuiltUri));
    }

    @Test
    public void testPrebuiltLogicRunnerGeneratesPickHighestBidScoringScriptSuccessfully() {
        String paramKey = "reportingUrl";
        String paramValue = "www.test.com";
        Uri prebuiltUri =
                Uri.parse(
                        String.format(
                                "%s://%s/%s/?%s=%s",
                                AD_SELECTION_PREBUILT_SCHEMA,
                                AD_SELECTION_USE_CASE,
                                AD_SELECTION_HIGHEST_BID_WINS,
                                paramKey,
                                paramValue));

        String result = mPrebuiltLogicGenerator.jsScriptFromPrebuiltUri(prebuiltUri);
        assertEquals(
                AD_SELECTION_HIGHEST_BID_WINS_JS.replaceAll(
                        String.format(NAMED_PARAM_TEMPLATE, paramKey), paramValue),
                result);
    }

    @Test
    public void testPrebuiltLogicRunnerNameIsNotDefinedException() {
        String unregisteredName = "not-registered-name";
        Uri prebuiltUri =
                Uri.parse(
                        String.format(
                                "%s://%s/%s/",
                                AD_SELECTION_PREBUILT_SCHEMA,
                                AD_SELECTION_USE_CASE,
                                unregisteredName));

        assertThrows(
                String.format(UNKNOWN_PREBUILT_IDENTIFIER, unregisteredName),
                IllegalArgumentException.class,
                () -> mPrebuiltLogicGenerator.jsScriptFromPrebuiltUri(prebuiltUri));
    }

    @Test
    public void testPrebuiltLogicRunnerUserCaseIsNotDefinedException() {
        String unregisteredUseCase = "not-registered-use-case";
        Uri prebuiltUri =
                Uri.parse(
                        String.format(
                                "%s://%s/", AD_SELECTION_PREBUILT_SCHEMA, unregisteredUseCase));

        assertThrows(
                String.format(UNKNOWN_PREBUILT_IDENTIFIER, unregisteredUseCase),
                IllegalArgumentException.class,
                () -> mPrebuiltLogicGenerator.jsScriptFromPrebuiltUri(prebuiltUri));
    }

    @Test
    public void testPrebuiltLogicRunnerParameterNotPresentException() {
        Uri prebuiltUri =
                Uri.parse(
                        String.format(
                                "%s://%s/%s/?",
                                AD_SELECTION_PREBUILT_SCHEMA,
                                AD_SELECTION_FROM_OUTCOMES_USE_CASE,
                                AD_OUTCOME_SELECTION_WATERFALL_MEDIATION_TRUNCATION));

        assertThrows(
                String.format(MISSING_PREBUILT_PARAMS, "bidFloor"),
                IllegalArgumentException.class,
                () -> mPrebuiltLogicGenerator.jsScriptFromPrebuiltUri(prebuiltUri));
    }

    @Test
    public void testPrebuiltLogicRunnerParameterUnrecognizedException() {
        String paramExtraKey = "extraParamKey";
        String paramValue = "bid_floor";
        Uri prebuiltUri =
                Uri.parse(
                        String.format(
                                "%s://%s/%s/?%s=%s",
                                AD_SELECTION_PREBUILT_SCHEMA,
                                AD_SELECTION_FROM_OUTCOMES_USE_CASE,
                                AD_OUTCOME_SELECTION_WATERFALL_MEDIATION_TRUNCATION,
                                paramExtraKey,
                                paramValue));

        assertThrows(
                String.format(UNRECOGNIZED_PREBUILT_PARAMS, paramExtraKey),
                IllegalArgumentException.class,
                () -> mPrebuiltLogicGenerator.jsScriptFromPrebuiltUri(prebuiltUri));
    }

    @Test
    public void testPrebuiltLogicFeatureFlagDisabledPrebuiltUriFailure() {
        Flags prebuiltFeatureDisabled = new PrebuiltLogicGeneratorTestFlags(false);

        PrebuiltLogicGenerator prebuiltLogicGenerator =
                new PrebuiltLogicGenerator(prebuiltFeatureDisabled);

        String paramKey = "bidFloor";
        String paramValue = "bid_floor";
        Uri prebuiltUri =
                Uri.parse(
                        String.format(
                                "%s://%s/%s/?%s=%s",
                                AD_SELECTION_PREBUILT_SCHEMA,
                                AD_SELECTION_FROM_OUTCOMES_USE_CASE,
                                AD_OUTCOME_SELECTION_WATERFALL_MEDIATION_TRUNCATION,
                                paramKey,
                                paramValue));

        assertThrows(
                PREBUILT_FEATURE_IS_DISABLED,
                IllegalArgumentException.class,
                () -> prebuiltLogicGenerator.isPrebuiltUri(prebuiltUri));
    }

    @Test
    public void testPrebuiltLogicFeatureFlagDisabledNonPrebuiltUriSuccess() {
        Flags prebuiltFeatureDisabled = new PrebuiltLogicGeneratorTestFlags(false);

        PrebuiltLogicGenerator prebuiltLogicGenerator =
                new PrebuiltLogicGenerator(prebuiltFeatureDisabled);

        Uri nonPrebuiltUri = Uri.parse("www.test.com");

        assertFalse(prebuiltLogicGenerator.isPrebuiltUri(nonPrebuiltUri));
    }

    private static class PrebuiltLogicGeneratorTestFlags implements Flags {
        private final boolean mPrebuiltLogicEnabled;

        PrebuiltLogicGeneratorTestFlags(boolean prebuiltLogicEnabled) {
            mPrebuiltLogicEnabled = prebuiltLogicEnabled;
        }

        @Override
        public boolean getFledgeAdSelectionPrebuiltUriEnabled() {
            return mPrebuiltLogicEnabled;
        }
    }
}
