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

package com.android.adservices.service.devapi;

import static com.google.common.truth.Truth.assertThat;

import android.adservices.common.AdSelectionSignals;
import android.adservices.common.AdTechIdentifier;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.data.common.DecisionLogic;
import com.android.adservices.data.customaudience.CustomAudienceDao;
import com.android.adservices.data.customaudience.CustomAudienceDatabase;
import com.android.adservices.data.customaudience.DBCustomAudience;
import com.android.adservices.data.customaudience.DBCustomAudienceOverride;
import com.android.adservices.service.adselection.JsVersionHelper;

import com.google.common.collect.ImmutableMap;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class CustomAudienceDevOverridesHelperTest {
    private CustomAudienceDao mCustomAudienceDao;
    private static final AdTechIdentifier BUYER = AdTechIdentifier.fromString("buyer");
    private static final String NAME = "name";
    private static final String APP_PACKAGE_NAME = "appPackageName";
    private static final String BIDDING_LOGIC_JS = "function test() { return \"hello world\"; }";
    private static final Long BIDDING_LOGIC_JS_VERSION = 2L;
    private static final String TRUSTED_BIDDING_DATA = "{\"trusted_bidding_data\":1}";

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setUp() {
        mCustomAudienceDao =
                Room.inMemoryDatabaseBuilder(
                                ApplicationProvider.getApplicationContext(),
                                CustomAudienceDatabase.class)
                        .addTypeConverter(new DBCustomAudience.Converters(true, true))
                        .build()
                        .customAudienceDao();
    }

    @Test
    public void testGetOverridesFindsMatchingOverride() {
        mCustomAudienceDao.persistCustomAudienceOverride(
                DBCustomAudienceOverride.builder()
                        .setOwner(APP_PACKAGE_NAME)
                        .setBuyer(BUYER)
                        .setName(NAME)
                        .setAppPackageName(APP_PACKAGE_NAME)
                        .setBiddingLogicJS(BIDDING_LOGIC_JS)
                        .setBiddingLogicJsVersion(BIDDING_LOGIC_JS_VERSION)
                        .setTrustedBiddingData(TRUSTED_BIDDING_DATA)
                        .build());

        DevContext devContext =
                DevContext.builder()
                        .setCallingAppPackageName(APP_PACKAGE_NAME)
                        .setDevOptionsEnabled(true)
                        .build();

        CustomAudienceDevOverridesHelper helper =
                new CustomAudienceDevOverridesHelper(devContext, mCustomAudienceDao);

        assertThat(helper.getBiddingLogicOverride(APP_PACKAGE_NAME, BUYER, NAME))
                .isEqualTo(
                        DecisionLogic.create(
                                BIDDING_LOGIC_JS,
                                ImmutableMap.of(
                                        JsVersionHelper.JS_PAYLOAD_TYPE_BUYER_BIDDING_LOGIC_JS,
                                        BIDDING_LOGIC_JS_VERSION)));
        assertThat(helper.getTrustedBiddingSignalsOverride(APP_PACKAGE_NAME, BUYER, NAME))
                .isEqualTo(AdSelectionSignals.fromString(TRUSTED_BIDDING_DATA));
    }

    @Test
    public void testGetOverridesFindsMatchingOverride_nullJsVersion() {
        mCustomAudienceDao.persistCustomAudienceOverride(
                DBCustomAudienceOverride.builder()
                        .setOwner(APP_PACKAGE_NAME)
                        .setBuyer(BUYER)
                        .setName(NAME)
                        .setAppPackageName(APP_PACKAGE_NAME)
                        .setBiddingLogicJS(BIDDING_LOGIC_JS)
                        .setTrustedBiddingData(TRUSTED_BIDDING_DATA)
                        .build());

        DevContext devContext =
                DevContext.builder()
                        .setCallingAppPackageName(APP_PACKAGE_NAME)
                        .setDevOptionsEnabled(true)
                        .build();

        CustomAudienceDevOverridesHelper helper =
                new CustomAudienceDevOverridesHelper(devContext, mCustomAudienceDao);

        assertThat(helper.getBiddingLogicOverride(APP_PACKAGE_NAME, BUYER, NAME))
                .isEqualTo(DecisionLogic.create(BIDDING_LOGIC_JS, ImmutableMap.of()));
        assertThat(helper.getTrustedBiddingSignalsOverride(APP_PACKAGE_NAME, BUYER, NAME))
                .isEqualTo(AdSelectionSignals.fromString(TRUSTED_BIDDING_DATA));
    }

    @Test
    public void testGetOverridesReturnsNullIfDevOptionsAreDisabled() {
        mCustomAudienceDao.persistCustomAudienceOverride(
                DBCustomAudienceOverride.builder()
                        .setOwner(APP_PACKAGE_NAME)
                        .setBuyer(BUYER)
                        .setName(NAME)
                        .setAppPackageName(APP_PACKAGE_NAME)
                        .setBiddingLogicJS(BIDDING_LOGIC_JS)
                        .setTrustedBiddingData(TRUSTED_BIDDING_DATA)
                        .build());

        DevContext devContext = DevContext.createForDevOptionsDisabled();

        CustomAudienceDevOverridesHelper helper =
                new CustomAudienceDevOverridesHelper(devContext, mCustomAudienceDao);

        assertThat(helper.getBiddingLogicOverride(APP_PACKAGE_NAME, BUYER, NAME)).isNull();
        assertThat(helper.getTrustedBiddingSignalsOverride(APP_PACKAGE_NAME, BUYER, NAME)).isNull();
    }

    @Test
    public void testGetOverridesReturnsNullIfTheOverrideBelongsToAnotherApp() {
        mCustomAudienceDao.persistCustomAudienceOverride(
                DBCustomAudienceOverride.builder()
                        .setOwner(APP_PACKAGE_NAME)
                        .setBuyer(BUYER)
                        .setName(NAME)
                        .setAppPackageName(APP_PACKAGE_NAME)
                        .setBiddingLogicJS(BIDDING_LOGIC_JS)
                        .setTrustedBiddingData(TRUSTED_BIDDING_DATA)
                        .build());

        DevContext devContext =
                DevContext.builder()
                        .setCallingAppPackageName(APP_PACKAGE_NAME + ".different")
                        .setDevOptionsEnabled(true)
                        .build();

        CustomAudienceDevOverridesHelper helper =
                new CustomAudienceDevOverridesHelper(devContext, mCustomAudienceDao);

        assertThat(helper.getBiddingLogicOverride(APP_PACKAGE_NAME, BUYER, NAME)).isNull();
        assertThat(helper.getTrustedBiddingSignalsOverride(APP_PACKAGE_NAME, BUYER, NAME)).isNull();
    }
}
