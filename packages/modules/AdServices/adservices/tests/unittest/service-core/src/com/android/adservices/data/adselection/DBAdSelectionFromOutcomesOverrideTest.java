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

public class DBAdSelectionFromOutcomesOverrideTest {
    private static final String AD_SELECTION_FROM_OUTCOMES_CONFIG_ID = "testConfigId";
    private static final String APP_PACKAGE_NAME = "com.test.package";
    private static final String SELECTION_LOGIC_JS = "function outcomeSelection{ return null; }";
    private static final String SELECTION_SIGNALS = "testSignals";

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testBuildDBAdSelectionOverride() {
        DBAdSelectionFromOutcomesOverride dbAdSelectionFromOutcomesOverride =
                DBAdSelectionFromOutcomesOverride.builder()
                        .setAdSelectionFromOutcomesConfigId(AD_SELECTION_FROM_OUTCOMES_CONFIG_ID)
                        .setAppPackageName(APP_PACKAGE_NAME)
                        .setSelectionLogicJs(SELECTION_LOGIC_JS)
                        .setSelectionSignals(SELECTION_SIGNALS)
                        .build();

        assertEquals(
                dbAdSelectionFromOutcomesOverride.getAdSelectionFromOutcomesConfigId(),
                AD_SELECTION_FROM_OUTCOMES_CONFIG_ID);
        assertEquals(APP_PACKAGE_NAME, dbAdSelectionFromOutcomesOverride.getAppPackageName());
        assertEquals(SELECTION_LOGIC_JS, dbAdSelectionFromOutcomesOverride.getSelectionLogicJs());
        assertEquals(SELECTION_SIGNALS, dbAdSelectionFromOutcomesOverride.getSelectionSignals());
    }

    @Test
    public void testCreateDBAdSelectionOverride() {
        DBAdSelectionFromOutcomesOverride dbAdSelectionFromOutcomesOverride =
                DBAdSelectionFromOutcomesOverride.create(
                        AD_SELECTION_FROM_OUTCOMES_CONFIG_ID,
                        APP_PACKAGE_NAME,
                        SELECTION_LOGIC_JS,
                        SELECTION_SIGNALS);

        assertEquals(
                dbAdSelectionFromOutcomesOverride.getAdSelectionFromOutcomesConfigId(),
                AD_SELECTION_FROM_OUTCOMES_CONFIG_ID);
        assertEquals(APP_PACKAGE_NAME, dbAdSelectionFromOutcomesOverride.getAppPackageName());
        assertEquals(SELECTION_LOGIC_JS, dbAdSelectionFromOutcomesOverride.getSelectionLogicJs());
        assertEquals(SELECTION_SIGNALS, dbAdSelectionFromOutcomesOverride.getSelectionSignals());
    }

    @Test
    public void testBuildDBAdSelectionFromOutcomesOverrideNoConfigIdFailure() {
        assertThrows(
                IllegalStateException.class,
                () -> {
                    DBAdSelectionFromOutcomesOverride.builder()
                            .setAppPackageName(APP_PACKAGE_NAME)
                            .setSelectionLogicJs(SELECTION_LOGIC_JS)
                            .setSelectionSignals(SELECTION_SIGNALS)
                            .build();
                });
    }

    @Test
    public void testBuildDBAdSelectionFromOutcomesOverrideNoPackageNameFailure() {
        assertThrows(
                IllegalStateException.class,
                () -> {
                    DBAdSelectionFromOutcomesOverride.builder()
                            .setAdSelectionFromOutcomesConfigId(
                                    AD_SELECTION_FROM_OUTCOMES_CONFIG_ID)
                            .setSelectionLogicJs(SELECTION_LOGIC_JS)
                            .setSelectionSignals(SELECTION_SIGNALS)
                            .build();
                });
    }

    @Test
    public void testBuildDBAdSelectionFromOutcomesOverrideNoSelectionJsFailure() {
        assertThrows(
                IllegalStateException.class,
                () -> {
                    DBAdSelectionFromOutcomesOverride.builder()
                            .setAdSelectionFromOutcomesConfigId(
                                    AD_SELECTION_FROM_OUTCOMES_CONFIG_ID)
                            .setAppPackageName(APP_PACKAGE_NAME)
                            .setSelectionSignals(SELECTION_SIGNALS)
                            .build();
                });
    }

    @Test
    public void testBuildDBAdSelectionFromOutcomesOverrideNoSelectionSignalsFailure() {
        assertThrows(
                IllegalStateException.class,
                () -> {
                    DBAdSelectionFromOutcomesOverride.builder()
                            .setAdSelectionFromOutcomesConfigId(
                                    AD_SELECTION_FROM_OUTCOMES_CONFIG_ID)
                            .setAppPackageName(APP_PACKAGE_NAME)
                            .setSelectionLogicJs(SELECTION_LOGIC_JS)
                            .build();
                });
    }
}
