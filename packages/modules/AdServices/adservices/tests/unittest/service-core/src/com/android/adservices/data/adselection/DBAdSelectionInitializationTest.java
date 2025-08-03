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

import android.adservices.adselection.AdSelectionConfigFixture;
import android.adservices.common.CommonFixture;

import com.android.adservices.common.SdkLevelSupportRule;

import org.junit.Rule;
import org.junit.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

public class DBAdSelectionInitializationTest {

    private static final long AD_SELECTION_ID_1 = 1L;
    private static final Instant CREATION_INSTANT_1 = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testBuild_unsetAdSelectionId_throwsISE() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        DBAdSelectionInitialization.builder()
                                .setCreationInstant(CREATION_INSTANT_1)
                                .setSeller(AdSelectionConfigFixture.SELLER)
                                .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                                .build());
    }

    @Test
    public void testBuild_unsetCreationInstant_throwsISE() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        DBAdSelectionInitialization.builder()
                                .setAdSelectionId(AD_SELECTION_ID_1)
                                .setSeller(AdSelectionConfigFixture.SELLER)
                                .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                                .build());
    }

    @Test
    public void testBuild_allFieldsSet_success() {
        DBAdSelectionInitialization dbAdSelectionInitialization =
                DBAdSelectionInitialization.builder()
                        .setAdSelectionId(AD_SELECTION_ID_1)
                        .setCreationInstant(CREATION_INSTANT_1)
                        .setSeller(AdSelectionConfigFixture.SELLER)
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();

        assertEquals(AD_SELECTION_ID_1, dbAdSelectionInitialization.getAdSelectionId());
        assertEquals(CREATION_INSTANT_1, dbAdSelectionInitialization.getCreationInstant());
        assertEquals(AdSelectionConfigFixture.SELLER, dbAdSelectionInitialization.getSeller());
        assertEquals(
                CommonFixture.TEST_PACKAGE_NAME,
                dbAdSelectionInitialization.getCallerPackageName());
    }

    @Test
    public void testCreate_nullCreationInstant_throwsNPE() {
        assertThrows(
                NullPointerException.class,
                () ->
                        DBAdSelectionInitialization.create(
                                AD_SELECTION_ID_1,
                                /** creationInstnat */
                                null,
                                AdSelectionConfigFixture.SELLER,
                                CommonFixture.TEST_PACKAGE_NAME));
    }

    @Test
    public void testCreate_allFieldsSet_success() {
        DBAdSelectionInitialization dbAdSelectionInitialization =
                DBAdSelectionInitialization.create(
                        AD_SELECTION_ID_1,
                        CREATION_INSTANT_1,
                        AdSelectionConfigFixture.SELLER,
                        CommonFixture.TEST_PACKAGE_NAME);

        assertEquals(AD_SELECTION_ID_1, dbAdSelectionInitialization.getAdSelectionId());
        assertEquals(CREATION_INSTANT_1, dbAdSelectionInitialization.getCreationInstant());
        assertEquals(AdSelectionConfigFixture.SELLER, dbAdSelectionInitialization.getSeller());
        assertEquals(
                CommonFixture.TEST_PACKAGE_NAME,
                dbAdSelectionInitialization.getCallerPackageName());
    }
}
