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

package com.android.adservices.data.adselection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;

import com.android.adservices.common.SdkLevelSupportRule;

import org.junit.Rule;
import org.junit.Test;

public class DBBuyerDecisionOverrideTest {

    private static final String AD_SELECTION_CONFIG_ID = "123";
    private static final String APP_PACKAGE_NAME = "appPackageName";
    private static final AdTechIdentifier BUYER = CommonFixture.VALID_BUYER_1;
    private static final String DECISION_LOGIC_JS =
            "function test() { return \"hello world\"; " + "var randomSymbols = \"+-:;'.\"\"}";

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testBuildDBBuyerDecisionOverride() {
        DBBuyerDecisionOverride override =
                DBBuyerDecisionOverride.builder()
                        .setBuyer(BUYER)
                        .setDecisionLogic(DECISION_LOGIC_JS)
                        .setAdSelectionConfigId(AD_SELECTION_CONFIG_ID)
                        .setAppPackageName(APP_PACKAGE_NAME)
                        .build();

        assertEquals(AD_SELECTION_CONFIG_ID, override.getAdSelectionConfigId());
        assertEquals(APP_PACKAGE_NAME, override.getAppPackageName());
        assertEquals(DECISION_LOGIC_JS, override.getDecisionLogic());
        assertEquals(BUYER, override.getBuyer());
    }

    @Test
    public void testDBBuyerDecisionOverrideCreate() {
        DBBuyerDecisionOverride override =
                DBBuyerDecisionOverride.create(
                        AD_SELECTION_CONFIG_ID, APP_PACKAGE_NAME, BUYER, DECISION_LOGIC_JS);

        assertEquals(AD_SELECTION_CONFIG_ID, override.getAdSelectionConfigId());
        assertEquals(APP_PACKAGE_NAME, override.getAppPackageName());
        assertEquals(DECISION_LOGIC_JS, override.getDecisionLogic());
        assertEquals(BUYER, override.getBuyer());
    }

    @Test
    public void testThrowsExceptionWithNoAdSelectionId() {
        assertThrows(
                IllegalStateException.class,
                () -> {
                    DBBuyerDecisionOverride.builder()
                            .setAppPackageName(APP_PACKAGE_NAME)
                            .setDecisionLogic(DECISION_LOGIC_JS)
                            .setBuyer(BUYER)
                            .build();
                });
    }

    @Test
    public void testThrowsExceptionWithNoAppPackageName() {
        assertThrows(
                IllegalStateException.class,
                () -> {
                    DBBuyerDecisionOverride.builder()
                            .setAdSelectionConfigId(AD_SELECTION_CONFIG_ID)
                            .setDecisionLogic(DECISION_LOGIC_JS)
                            .setBuyer(BUYER)
                            .build();
                });
    }

    @Test
    public void testThrowsExceptionWithNoBuyer() {
        assertThrows(
                IllegalStateException.class,
                () -> {
                    DBBuyerDecisionOverride.builder()
                            .setDecisionLogic(DECISION_LOGIC_JS)
                            .setAdSelectionConfigId(AD_SELECTION_CONFIG_ID)
                            .setAppPackageName(APP_PACKAGE_NAME)
                            .build();
                });
    }

    @Test
    public void testThrowsExceptionWithNoDecisionLogic() {
        assertThrows(
                IllegalStateException.class,
                () -> {
                    DBBuyerDecisionOverride.builder()
                            .setBuyer(BUYER)
                            .setAdSelectionConfigId(AD_SELECTION_CONFIG_ID)
                            .setAppPackageName(APP_PACKAGE_NAME)
                            .build();
                });
    }
}
