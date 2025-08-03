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

import static com.android.adservices.service.js.JSScriptArgument.jsonArg;
import static com.android.adservices.service.js.JSScriptArgument.numericArg;
import static com.android.adservices.service.js.JSScriptArgument.numericArrayArg;
import static com.android.adservices.service.js.JSScriptArgument.recordArg;
import static com.android.adservices.service.js.JSScriptArgument.stringArg;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.adservices.adselection.AdWithBid;

import androidx.test.filters.SmallTest;

import com.android.adservices.common.SdkLevelSupportRule;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;

@SmallTest
public class AdWithBidArgumentUtilTest {
    private static final AdWithBidArgumentUtil AD_WITH_BID_ARGUMENT_UTIL_WITHOUT_COPIER =
            new AdWithBidArgumentUtil(new AdDataArgumentUtil(new AdCounterKeyCopierNoOpImpl()));
    private static final AdWithBidArgumentUtil AD_WITH_BID_ARGUMENT_UTIL_WITH_COPIER =
            new AdWithBidArgumentUtil(new AdDataArgumentUtil(new AdCounterKeyCopierImpl()));

    public static final int BID_VALUE = 10;
    public static final AdWithBid AD_WITH_BID =
            new AdWithBid(AdDataArgumentUtilTest.AD_DATA, BID_VALUE);
    public static final AdWithBid AD_WITH_BID_WITH_AD_COUNTER_KEYS =
            new AdWithBid(AdDataArgumentUtilTest.AD_DATA_WITH_AD_COUNTER_KEYS, BID_VALUE);

    private JSONObject aValidAdWithBidJson() throws JSONException {
        return new JSONObject()
                .put(AdWithBidArgumentUtil.AD_FIELD_NAME, AdDataArgumentUtilTest.aValidAdDataJson())
                .put(AdWithBidArgumentUtil.BID_FIELD_NAME, BID_VALUE);
    }

    private JSONObject aValidAdWithBidWithAdCounterKeysJson() throws JSONException {
        return new JSONObject()
                .put(
                        AdWithBidArgumentUtil.AD_FIELD_NAME,
                        AdDataArgumentUtilTest.aValidAdDataWithAdCounterKeysJson())
                .put(AdWithBidArgumentUtil.BID_FIELD_NAME, BID_VALUE);
    }

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testShouldReadValidJSON() throws Exception {
        assertThat(
                        AD_WITH_BID_ARGUMENT_UTIL_WITHOUT_COPIER.parseJsonResponse(
                                aValidAdWithBidJson()))
                .isEqualTo(AD_WITH_BID);
    }

    @Test
    public void testShouldReadValidJSONParseWithCopier() throws Exception {
        assertThat(AD_WITH_BID_ARGUMENT_UTIL_WITH_COPIER.parseJsonResponse(aValidAdWithBidJson()))
                .isEqualTo(AD_WITH_BID);
    }

    @Test
    public void testShouldReadValidJSONWithAdCounterKeys() throws Exception {
        assertThat(
                        AD_WITH_BID_ARGUMENT_UTIL_WITH_COPIER.parseJsonResponse(
                                aValidAdWithBidWithAdCounterKeysJson()))
                .isEqualTo(AD_WITH_BID_WITH_AD_COUNTER_KEYS);
    }

    @Test
    public void testShouldFailIfAdDataHasInvalidMetadata() throws Exception {
        final JSONObject adWithInvalidMetadata =
                AdDataArgumentUtilTest.aValidAdDataJson()
                        .put(AdDataArgumentUtil.METADATA_FIELD_NAME, 10);
        JSONObject adWithBidWithInvalidMetadata =
                new JSONObject()
                        .put(AdWithBidArgumentUtil.AD_FIELD_NAME, adWithInvalidMetadata)
                        .put(AdWithBidArgumentUtil.BID_FIELD_NAME, BID_VALUE);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        AD_WITH_BID_ARGUMENT_UTIL_WITHOUT_COPIER.parseJsonResponse(
                                adWithBidWithInvalidMetadata));
    }

    @Test
    public void testShouldFailIfAdDataHasInvalidMetadataParseWithCopier() throws Exception {
        final JSONObject adWithInvalidMetadata =
                AdDataArgumentUtilTest.aValidAdDataJson()
                        .put(AdDataArgumentUtil.METADATA_FIELD_NAME, 10);
        JSONObject adWithBidWithInvalidMetadata =
                new JSONObject()
                        .put(AdWithBidArgumentUtil.AD_FIELD_NAME, adWithInvalidMetadata)
                        .put(AdWithBidArgumentUtil.BID_FIELD_NAME, BID_VALUE);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        AD_WITH_BID_ARGUMENT_UTIL_WITH_COPIER.parseJsonResponse(
                                adWithBidWithInvalidMetadata));
    }

    @Test
    public void testShouldFailIfIsMissingBid() throws Exception {
        JSONObject adWithBidMissingBid = aValidAdWithBidJson();
        adWithBidMissingBid.remove(AdWithBidArgumentUtil.BID_FIELD_NAME);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        AD_WITH_BID_ARGUMENT_UTIL_WITHOUT_COPIER.parseJsonResponse(
                                adWithBidMissingBid));
    }

    @Test
    public void testShouldFailIfIsMissingBidParseWithCopier() throws Exception {
        JSONObject adWithBidMissingBid = aValidAdWithBidJson();
        adWithBidMissingBid.remove(AdWithBidArgumentUtil.BID_FIELD_NAME);
        assertThrows(
                IllegalArgumentException.class,
                () -> AD_WITH_BID_ARGUMENT_UTIL_WITH_COPIER.parseJsonResponse(adWithBidMissingBid));
    }

    @Test
    public void testShouldFailIfIsMissingAdData() throws Exception {
        JSONObject adWithBidMissingAdData = aValidAdWithBidJson();
        adWithBidMissingAdData.remove(AdWithBidArgumentUtil.AD_FIELD_NAME);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        AD_WITH_BID_ARGUMENT_UTIL_WITHOUT_COPIER.parseJsonResponse(
                                adWithBidMissingAdData));
    }

    @Test
    public void testShouldFailIfIsMissingAdDataParseWithCopier() throws Exception {
        JSONObject adWithBidMissingAdData = aValidAdWithBidJson();
        adWithBidMissingAdData.remove(AdWithBidArgumentUtil.AD_FIELD_NAME);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        AD_WITH_BID_ARGUMENT_UTIL_WITH_COPIER.parseJsonResponse(
                                adWithBidMissingAdData));
    }

    @Test
    public void testConversionToScriptArgument() throws JSONException {
        assertThat(AD_WITH_BID_ARGUMENT_UTIL_WITHOUT_COPIER.asScriptArgument("name", AD_WITH_BID))
                .isEqualTo(
                        recordArg(
                                "name",
                                recordArg(
                                        AdWithBidArgumentUtil.AD_FIELD_NAME,
                                        stringArg(
                                                AdDataArgumentUtil.RENDER_URI_FIELD_NAME,
                                                AD_WITH_BID.getAdData().getRenderUri().toString()),
                                        jsonArg(
                                                AdDataArgumentUtil.METADATA_FIELD_NAME,
                                                AD_WITH_BID.getAdData().getMetadata())),
                                numericArg(
                                        AdWithBidArgumentUtil.BID_FIELD_NAME,
                                        AD_WITH_BID.getBid())));
    }

    @Test
    public void testConversionToScriptArgumentWithCopier() throws JSONException {
        assertThat(AD_WITH_BID_ARGUMENT_UTIL_WITH_COPIER.asScriptArgument("name", AD_WITH_BID))
                .isEqualTo(
                        recordArg(
                                "name",
                                recordArg(
                                        AdWithBidArgumentUtil.AD_FIELD_NAME,
                                        stringArg(
                                                AdDataArgumentUtil.RENDER_URI_FIELD_NAME,
                                                AD_WITH_BID.getAdData().getRenderUri().toString()),
                                        jsonArg(
                                                AdDataArgumentUtil.METADATA_FIELD_NAME,
                                                AD_WITH_BID.getAdData().getMetadata())),
                                numericArg(
                                        AdWithBidArgumentUtil.BID_FIELD_NAME,
                                        AD_WITH_BID.getBid())));
    }

    @Test
    public void testConversionToScriptArgumentWithAdCounterKeys() throws JSONException {
        assertThat(
                        AD_WITH_BID_ARGUMENT_UTIL_WITH_COPIER.asScriptArgument(
                                "name", AD_WITH_BID_WITH_AD_COUNTER_KEYS))
                .isEqualTo(
                        recordArg(
                                "name",
                                recordArg(
                                        AdWithBidArgumentUtil.AD_FIELD_NAME,
                                        stringArg(
                                                AdDataArgumentUtil.RENDER_URI_FIELD_NAME,
                                                AD_WITH_BID_WITH_AD_COUNTER_KEYS
                                                        .getAdData()
                                                        .getRenderUri()
                                                        .toString()),
                                        jsonArg(
                                                AdDataArgumentUtil.METADATA_FIELD_NAME,
                                                AD_WITH_BID_WITH_AD_COUNTER_KEYS
                                                        .getAdData()
                                                        .getMetadata()),
                                        numericArrayArg(
                                                AdCounterKeyCopierImpl.AD_COUNTER_KEYS_FIELD_NAME,
                                                new ArrayList<>(
                                                        AD_WITH_BID_WITH_AD_COUNTER_KEYS
                                                                .getAdData()
                                                                .getAdCounterKeys()))),
                                numericArg(
                                        AdWithBidArgumentUtil.BID_FIELD_NAME,
                                        AD_WITH_BID_WITH_AD_COUNTER_KEYS.getBid())));
    }
}
