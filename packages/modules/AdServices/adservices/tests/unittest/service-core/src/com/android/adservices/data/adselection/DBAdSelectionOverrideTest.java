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

package com.android.adservices.data.adselection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.android.adservices.common.SdkLevelSupportRule;

import org.junit.Rule;
import org.junit.Test;

public class DBAdSelectionOverrideTest {
    private static final String AD_SELECTION_CONFIG_ID = "123";
    private static final String APP_PACKAGE_NAME = "appPackageName";
    private static final String DECISION_LOGIC_JS = "function test() { return \"hello world\"; }";
    private static final String TRUSTED_SCORING_SIGNALS =
            "{\n"
                    + "\t\"render_uri_1\": \"signals_for_1\",\n"
                    + "\t\"render_uri_2\": \"signals_for_2\"\n"
                    + "}";

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testBuildDBAdSelectionOverride() {
        DBAdSelectionOverride dbAdSelectionOverride =
                DBAdSelectionOverride.builder()
                        .setAdSelectionConfigId(AD_SELECTION_CONFIG_ID)
                        .setAppPackageName(APP_PACKAGE_NAME)
                        .setDecisionLogicJS(DECISION_LOGIC_JS)
                        .setTrustedScoringSignals(TRUSTED_SCORING_SIGNALS)
                        .build();

        assertEquals(AD_SELECTION_CONFIG_ID, dbAdSelectionOverride.getAdSelectionConfigId());
        assertEquals(APP_PACKAGE_NAME, dbAdSelectionOverride.getAppPackageName());
        assertEquals(DECISION_LOGIC_JS, dbAdSelectionOverride.getDecisionLogicJS());
        assertEquals(TRUSTED_SCORING_SIGNALS, dbAdSelectionOverride.getTrustedScoringSignals());
    }

    @Test
    public void testThrowsExceptionWithNoAdSelectionId() {
        assertThrows(
                IllegalStateException.class,
                () -> {
                    DBAdSelectionOverride.builder()
                            .setAppPackageName(APP_PACKAGE_NAME)
                            .setDecisionLogicJS(DECISION_LOGIC_JS)
                            .setTrustedScoringSignals(TRUSTED_SCORING_SIGNALS)
                            .build();
                });
    }

    @Test
    public void testThrowsExceptionWithNoAppPackageName() {
        assertThrows(
                IllegalStateException.class,
                () -> {
                    DBAdSelectionOverride.builder()
                            .setAdSelectionConfigId(AD_SELECTION_CONFIG_ID)
                            .setDecisionLogicJS(DECISION_LOGIC_JS)
                            .setTrustedScoringSignals(TRUSTED_SCORING_SIGNALS)
                            .build();
                });
    }

    @Test
    public void testThrowsExceptionWithNoDecisionLogicJS() {
        assertThrows(
                IllegalStateException.class,
                () -> {
                    DBAdSelectionOverride.builder()
                            .setAdSelectionConfigId(AD_SELECTION_CONFIG_ID)
                            .setAppPackageName(APP_PACKAGE_NAME)
                            .build();
                });
    }

    @Test
    public void testThrowsExceptionWithNoTrustedScoringSignals() {
        assertThrows(
                IllegalStateException.class,
                () -> {
                    DBAdSelectionOverride.builder()
                            .setAdSelectionConfigId(AD_SELECTION_CONFIG_ID)
                            .setAppPackageName(APP_PACKAGE_NAME)
                            .setDecisionLogicJS(DECISION_LOGIC_JS)
                            .build();
                });
    }

    @Test
    public void testDBAdSelectionOverrideCreate() {
        DBAdSelectionOverride dbAdSelectionOverride =
                DBAdSelectionOverride.create(
                        AD_SELECTION_CONFIG_ID,
                        APP_PACKAGE_NAME,
                        DECISION_LOGIC_JS,
                        TRUSTED_SCORING_SIGNALS);

        assertEquals(AD_SELECTION_CONFIG_ID, dbAdSelectionOverride.getAdSelectionConfigId());
        assertEquals(APP_PACKAGE_NAME, dbAdSelectionOverride.getAppPackageName());
        assertEquals(DECISION_LOGIC_JS, dbAdSelectionOverride.getDecisionLogicJS());
        assertEquals(TRUSTED_SCORING_SIGNALS, dbAdSelectionOverride.getTrustedScoringSignals());
    }
}
