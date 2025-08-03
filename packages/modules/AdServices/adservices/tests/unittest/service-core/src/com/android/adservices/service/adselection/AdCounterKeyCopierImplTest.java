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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.adservices.common.AdData;
import android.adservices.common.AdDataFixture;
import android.adservices.common.CommonFixture;

import com.android.adservices.common.DBAdDataFixture;
import com.android.adservices.common.JsonFixture;
import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.data.adselection.DBAdSelection;
import com.android.adservices.data.common.DBAdData;
import com.android.adservices.service.js.JSScriptRecordArgument;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;

public class AdCounterKeyCopierImplTest {
    private static final String EXPECTED_AD_COUNTER_KEYS_FIELD_NAME = "ad_counter_keys";
    private final AdCounterKeyCopierImpl mAdCounterKeyCopier = new AdCounterKeyCopierImpl();

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testCopyAdCounterKeys_DBAdDataToAdDataBuilder_NullBuilderThrows() {
        DBAdData sourceAdData = DBAdDataFixture.VALID_DB_AD_DATA_NO_FILTERS;

        assertThrows(
                NullPointerException.class,
                () -> mAdCounterKeyCopier.copyAdCounterKeys((AdData.Builder) null, sourceAdData));
    }

    @Test
    public void testCopyAdCounterKeys_DBAdDataToAdDataBuilder_NullDBAdDataThrows() {
        assertThrows(
                NullPointerException.class,
                () -> mAdCounterKeyCopier.copyAdCounterKeys(new AdData.Builder(), (DBAdData) null));
    }

    @Test
    public void testCopyAdCounterKeys_DBAdDataToAdDataBuilder() {
        DBAdData sourceAdData =
                DBAdDataFixture.getValidDbAdDataNoFiltersBuilder()
                        .setAdCounterKeys(AdDataFixture.getAdCounterKeys())
                        .build();
        AdData.Builder targetBuilder =
                AdDataFixture.getValidAdDataBuilderByBuyer(CommonFixture.VALID_BUYER_1, 0)
                        .setAdCounterKeys(new HashSet<>());

        AdData.Builder outputBuilder =
                mAdCounterKeyCopier.copyAdCounterKeys(targetBuilder, sourceAdData);

        AdData outputAdData = outputBuilder.build();
        assertThat(outputAdData.getAdCounterKeys()).isEqualTo(AdDataFixture.getAdCounterKeys());
    }

    @Test
    public void testCopyAdCounterKeys_AdDataToRecordArg_NullBuilderThrows() {
        AdData sourceAdData =
                AdDataFixture.getValidAdDataBuilderByBuyer(CommonFixture.VALID_BUYER_1, 0)
                        .setAdCounterKeys(AdDataFixture.getAdCounterKeys())
                        .build();

        assertThrows(
                NullPointerException.class,
                () -> mAdCounterKeyCopier.copyAdCounterKeys(null, sourceAdData));
    }

    @Test
    public void testCopyAdCounterKeys_AdDataToRecordArg_NullAdDataThrows() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mAdCounterKeyCopier.copyAdCounterKeys(
                                new JSScriptRecordArgument("test", Collections.emptyList()),
                                (AdData) null));
    }

    @Test
    public void testCopyAdCounterKeys_AdDataToRecordArg() throws JSONException {
        AdData sourceAdData =
                AdDataFixture.getValidAdDataBuilderByBuyer(CommonFixture.VALID_BUYER_1, 0)
                        .setAdCounterKeys(AdDataFixture.getAdCounterKeys())
                        .build();
        JSScriptRecordArgument originalRecordArg =
                new JSScriptRecordArgument("test", Collections.emptyList());
        String expectedInitValue =
                new JSONObject()
                        .put(
                                EXPECTED_AD_COUNTER_KEYS_FIELD_NAME,
                                new JSONArray(AdDataFixture.getAdCounterKeys()))
                        .toString();

        JSScriptRecordArgument outputRecordArg =
                mAdCounterKeyCopier.copyAdCounterKeys(originalRecordArg, sourceAdData);

        assertThat(outputRecordArg).isNotEqualTo(originalRecordArg);
        assertThat(
                        JsonFixture.formatAsOrgJsonJSONObjectString(
                                outputRecordArg.initializationValue()))
                .isEqualTo(expectedInitValue);
    }

    @Test
    public void testCopyAdCounterKeys_DBAdDataToRecordArg_NullBuilderThrows() {
        DBAdData sourceAdData = DBAdDataFixture.VALID_DB_AD_DATA_NO_FILTERS;

        assertThrows(
                NullPointerException.class,
                () ->
                        mAdCounterKeyCopier.copyAdCounterKeys(
                                (JSScriptRecordArgument) null, sourceAdData));
    }

    @Test
    public void testCopyAdCounterKeys_DBAdDataToRecordArg_NullDBAdDataThrows() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mAdCounterKeyCopier.copyAdCounterKeys(
                                new JSScriptRecordArgument("test", Collections.emptyList()),
                                (DBAdData) null));
    }

    @Test
    public void testCopyAdCounterKeys_DBAdDataToRecordArg() throws JSONException {
        DBAdData sourceAdData =
                DBAdDataFixture.getValidDbAdDataNoFiltersBuilder()
                        .setAdCounterKeys(AdDataFixture.getAdCounterKeys())
                        .build();
        JSScriptRecordArgument originalRecordArg =
                new JSScriptRecordArgument("test", Collections.emptyList());
        String expectedInitValue =
                new JSONObject()
                        .put(
                                EXPECTED_AD_COUNTER_KEYS_FIELD_NAME,
                                new JSONArray(AdDataFixture.getAdCounterKeys()))
                        .toString();

        JSScriptRecordArgument outputRecordArg =
                mAdCounterKeyCopier.copyAdCounterKeys(originalRecordArg, sourceAdData);

        assertThat(outputRecordArg).isNotEqualTo(originalRecordArg);
        assertThat(
                        JsonFixture.formatAsOrgJsonJSONObjectString(
                                outputRecordArg.initializationValue()))
                .isEqualTo(expectedInitValue);
    }

    @Test
    public void testCopyAdCounterKeys_JsonObjectToAdDataBuilder_NullBuilderThrows() {
        assertThrows(
                NullPointerException.class,
                () -> mAdCounterKeyCopier.copyAdCounterKeys(null, new JSONObject()));
    }

    @Test
    public void testCopyAdCounterKeys_JsonObjectToAdDataBuilder_NullJsonObjectThrows() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mAdCounterKeyCopier.copyAdCounterKeys(
                                new AdData.Builder(), (JSONObject) null));
    }

    @Test
    public void testCopyAdCounterKeys_JsonObjectToAdDataBuilder() throws JSONException {
        JSONObject sourceObject = new JSONObject();
        sourceObject.put("ad_counter_keys", new JSONArray(AdDataFixture.getAdCounterKeys()));
        AdData.Builder targetBuilder =
                AdDataFixture.getValidAdDataBuilderByBuyer(CommonFixture.VALID_BUYER_1, 0)
                        .setAdCounterKeys(new HashSet<>());

        AdData.Builder outputBuilder =
                mAdCounterKeyCopier.copyAdCounterKeys(targetBuilder, sourceObject);

        AdData outputAdData = outputBuilder.build();
        assertThat(outputAdData.getAdCounterKeys()).isEqualTo(AdDataFixture.getAdCounterKeys());
    }

    @Test
    public void testCopyAdCounterKeys_OutcomeToAdSelectionBuilder_NullBuilderThrows() {
        AdScoringOutcome sourceOutcome =
                AdScoringOutcomeFixture.anAdScoringBuilder(CommonFixture.VALID_BUYER_1, 1.0)
                        .build();
        assertThrows(
                NullPointerException.class,
                () -> mAdCounterKeyCopier.copyAdCounterKeys(null, sourceOutcome));
    }

    @Test
    public void testCopyAdCounterKeys_OutcomeToAdSelectionBuilder_NullOutcomeThrows() {
        assertThrows(
                NullPointerException.class,
                () -> mAdCounterKeyCopier.copyAdCounterKeys(new DBAdSelection.Builder(), null));
    }

    @Test
    public void testCopyAdCounterKeys_OutcomeToAdSelectionBuilder() {
        AdScoringOutcome sourceOutcome =
                AdScoringOutcomeFixture.anAdScoringBuilderWithAdCounterKeys(
                                CommonFixture.VALID_BUYER_1, 1.0)
                        .build();
        DBAdSelection.Builder targetBuilder =
                new DBAdSelection.Builder()
                        .setWinningAdBid(sourceOutcome.getAdWithScore().getAdWithBid().getBid())
                        .setCustomAudienceSignals(sourceOutcome.getCustomAudienceSignals())
                        .setWinningAdRenderUri(
                                sourceOutcome
                                        .getAdWithScore()
                                        .getAdWithBid()
                                        .getAdData()
                                        .getRenderUri())
                        .setBiddingLogicUri(sourceOutcome.getBiddingLogicUri())
                        .setBuyerContextualSignals("{}");

        DBAdSelection outputSelection =
                mAdCounterKeyCopier
                        .copyAdCounterKeys(targetBuilder, sourceOutcome)
                        .setAdSelectionId(10)
                        .setCreationTimestamp(CommonFixture.FIXED_NOW_TRUNCATED_TO_MILLI)
                        .setCallerPackageName(CommonFixture.TEST_PACKAGE_NAME)
                        .build();
        assertThat(outputSelection.getAdCounterIntKeys())
                .containsExactlyElementsIn(
                        sourceOutcome
                                .getAdWithScore()
                                .getAdWithBid()
                                .getAdData()
                                .getAdCounterKeys());
    }
}
