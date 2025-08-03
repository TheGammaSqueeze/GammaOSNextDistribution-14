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

package com.android.adservices.data.customaudience;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import android.adservices.common.CommonFixture;
import android.adservices.customaudience.CustomAudienceFixture;

import com.android.adservices.common.SdkLevelSupportRule;

import org.junit.Rule;
import org.junit.Test;

public class CustomAudienceOverrideTest {
    private static final String NAME = "name";
    private static final String APP_PACKAGE_NAME = "appPackageName";
    private static final String BIDDING_LOGIC_JS = "function test() { return \"hello world\"; }";
    private static final Long BIDDING_LOGIC_JS_VERSION = 3L;
    private static final String TRUSTED_BIDDING_DATA = "{\"trusted_bidding_data\":1}";

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testBuildDBAdSelectionOverride() {
        DBCustomAudienceOverride dbCustomAudienceOverride =
                DBCustomAudienceOverride.builder()
                        .setOwner(CustomAudienceFixture.VALID_OWNER)
                        .setBuyer(CommonFixture.VALID_BUYER_1)
                        .setName(NAME)
                        .setAppPackageName(APP_PACKAGE_NAME)
                        .setBiddingLogicJS(BIDDING_LOGIC_JS)
                        .setBiddingLogicJsVersion(BIDDING_LOGIC_JS_VERSION)
                        .setTrustedBiddingData(TRUSTED_BIDDING_DATA)
                        .build();

        assertEquals(dbCustomAudienceOverride.getOwner(), CustomAudienceFixture.VALID_OWNER);
        assertEquals(dbCustomAudienceOverride.getBuyer(), CommonFixture.VALID_BUYER_1);
        assertEquals(dbCustomAudienceOverride.getName(), NAME);
        assertEquals(dbCustomAudienceOverride.getAppPackageName(), APP_PACKAGE_NAME);
        assertEquals(dbCustomAudienceOverride.getBiddingLogicJS(), BIDDING_LOGIC_JS);
        assertEquals(dbCustomAudienceOverride.getBiddingLogicJsVersion(), BIDDING_LOGIC_JS_VERSION);
        assertEquals(dbCustomAudienceOverride.getTrustedBiddingData(), TRUSTED_BIDDING_DATA);
    }

    @Test
    public void testThrowsExceptionWithNoOwner() {
        assertThrows(
                IllegalStateException.class,
                () -> {
                    DBCustomAudienceOverride.builder()
                            .setBuyer(CommonFixture.VALID_BUYER_1)
                            .setName(NAME)
                            .setAppPackageName(APP_PACKAGE_NAME)
                            .setBiddingLogicJS(BIDDING_LOGIC_JS)
                            .setBiddingLogicJsVersion(BIDDING_LOGIC_JS_VERSION)
                            .setTrustedBiddingData(TRUSTED_BIDDING_DATA)
                            .build();
                });
    }

    @Test
    public void testThrowsExceptionWithNoBuyer() {
        assertThrows(
                IllegalStateException.class,
                () -> {
                    DBCustomAudienceOverride.builder()
                            .setOwner(CustomAudienceFixture.VALID_OWNER)
                            .setName(NAME)
                            .setAppPackageName(APP_PACKAGE_NAME)
                            .setBiddingLogicJS(BIDDING_LOGIC_JS)
                            .setBiddingLogicJsVersion(BIDDING_LOGIC_JS_VERSION)
                            .setTrustedBiddingData(TRUSTED_BIDDING_DATA)
                            .build();
                });
    }

    @Test
    public void testThrowsExceptionWithNoName() {
        assertThrows(
                IllegalStateException.class,
                () -> {
                    DBCustomAudienceOverride.builder()
                            .setOwner(CustomAudienceFixture.VALID_OWNER)
                            .setBuyer(CommonFixture.VALID_BUYER_1)
                            .setAppPackageName(APP_PACKAGE_NAME)
                            .setBiddingLogicJS(BIDDING_LOGIC_JS)
                            .setBiddingLogicJsVersion(BIDDING_LOGIC_JS_VERSION)
                            .setTrustedBiddingData(TRUSTED_BIDDING_DATA)
                            .build();
                });
    }

    @Test
    public void testThrowsExceptionWithNoAppPackageName() {
        assertThrows(
                IllegalStateException.class,
                () -> {
                    DBCustomAudienceOverride.builder()
                            .setOwner(CustomAudienceFixture.VALID_OWNER)
                            .setBuyer(CommonFixture.VALID_BUYER_1)
                            .setName(NAME)
                            .setBiddingLogicJS(BIDDING_LOGIC_JS)
                            .setBiddingLogicJsVersion(BIDDING_LOGIC_JS_VERSION)
                            .setTrustedBiddingData(TRUSTED_BIDDING_DATA)
                            .build();
                });
    }

    @Test
    public void testThrowsExceptionWithNoBiddingLogicJS() {
        assertThrows(
                IllegalStateException.class,
                () -> {
                    DBCustomAudienceOverride.builder()
                            .setOwner(CustomAudienceFixture.VALID_OWNER)
                            .setBuyer(CommonFixture.VALID_BUYER_1)
                            .setName(NAME)
                            .setAppPackageName(APP_PACKAGE_NAME)
                            .setBiddingLogicJsVersion(BIDDING_LOGIC_JS_VERSION)
                            .setTrustedBiddingData(TRUSTED_BIDDING_DATA)
                            .build();
                });
    }

    @Test
    public void testThrowsExceptionWithNoTrustedBiddingData() {
        assertThrows(
                IllegalStateException.class,
                () -> {
                    DBCustomAudienceOverride.builder()
                            .setOwner(CustomAudienceFixture.VALID_OWNER)
                            .setBuyer(CommonFixture.VALID_BUYER_1)
                            .setName(NAME)
                            .setAppPackageName(APP_PACKAGE_NAME)
                            .setBiddingLogicJS(BIDDING_LOGIC_JS)
                            .setBiddingLogicJsVersion(BIDDING_LOGIC_JS_VERSION)
                            .build();
                });
    }

    @Test
    public void testNoBiddingLogicJsVersion() {
        DBCustomAudienceOverride dbCustomAudienceOverride =
                DBCustomAudienceOverride.builder()
                        .setOwner(CustomAudienceFixture.VALID_OWNER)
                        .setBuyer(CommonFixture.VALID_BUYER_1)
                        .setName(NAME)
                        .setAppPackageName(APP_PACKAGE_NAME)
                        .setBiddingLogicJS(BIDDING_LOGIC_JS)
                        .setTrustedBiddingData(TRUSTED_BIDDING_DATA)
                        .build();

        assertEquals(dbCustomAudienceOverride.getOwner(), CustomAudienceFixture.VALID_OWNER);
        assertEquals(dbCustomAudienceOverride.getBuyer(), CommonFixture.VALID_BUYER_1);
        assertEquals(dbCustomAudienceOverride.getName(), NAME);
        assertEquals(dbCustomAudienceOverride.getAppPackageName(), APP_PACKAGE_NAME);
        assertEquals(dbCustomAudienceOverride.getBiddingLogicJS(), BIDDING_LOGIC_JS);
        assertNull(dbCustomAudienceOverride.getBiddingLogicJsVersion());
        assertEquals(dbCustomAudienceOverride.getTrustedBiddingData(), TRUSTED_BIDDING_DATA);
    }
}
