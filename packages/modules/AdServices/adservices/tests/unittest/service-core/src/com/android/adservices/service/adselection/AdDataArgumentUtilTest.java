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
import static com.android.adservices.service.js.JSScriptArgument.numericArrayArg;
import static com.android.adservices.service.js.JSScriptArgument.recordArg;
import static com.android.adservices.service.js.JSScriptArgument.stringArg;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.adservices.common.AdData;
import android.adservices.common.AdDataFixture;
import android.adservices.common.CommonFixture;

import androidx.test.filters.SmallTest;

import com.android.adservices.common.SdkLevelSupportRule;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;

import java.util.ArrayList;

@SmallTest
public class AdDataArgumentUtilTest {
    private static final AdDataArgumentUtil AD_DATA_ARGUMENT_UTIL_WITHOUT_COPIER =
            new AdDataArgumentUtil(new AdCounterKeyCopierNoOpImpl());
    private static final AdDataArgumentUtil AD_DATA_ARGUMENT_UTIL_WITH_COPIER =
            new AdDataArgumentUtil(new AdCounterKeyCopierImpl());

    public static final AdData AD_DATA =
            AdDataFixture.getValidAdDataByBuyer(CommonFixture.VALID_BUYER_1, 0);
    public static final AdData AD_DATA_WITH_AD_COUNTER_KEYS =
            AdDataFixture.getValidAdDataBuilderByBuyer(CommonFixture.VALID_BUYER_1, 0)
                    .setAdCounterKeys(AdDataFixture.getAdCounterKeys())
                    .build();

    public static JSONObject aValidAdDataJson() throws JSONException {
        return new JSONObject()
                .put(AdDataArgumentUtil.RENDER_URI_FIELD_NAME, AD_DATA.getRenderUri())
                .put(AdDataArgumentUtil.METADATA_FIELD_NAME, new JSONObject(AD_DATA.getMetadata()));
    }

    public static JSONObject aValidAdDataWithAdCounterKeysJson() throws JSONException {
        return new JSONObject()
                .put(
                        AdDataArgumentUtil.RENDER_URI_FIELD_NAME,
                        AD_DATA_WITH_AD_COUNTER_KEYS.getRenderUri())
                .put(
                        AdDataArgumentUtil.METADATA_FIELD_NAME,
                        new JSONObject(AD_DATA_WITH_AD_COUNTER_KEYS.getMetadata()))
                .put(
                        AdCounterKeyCopierImpl.AD_COUNTER_KEYS_FIELD_NAME,
                        new JSONArray(AD_DATA_WITH_AD_COUNTER_KEYS.getAdCounterKeys()));
    }

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testShouldReadValidJSON() throws Exception {
        assertThat(AD_DATA_ARGUMENT_UTIL_WITHOUT_COPIER.parseJsonResponse(aValidAdDataJson()))
                .isEqualTo(AD_DATA);
    }

    @Test
    public void testShouldReadValidJSONParseWithCopier() throws Exception {
        assertThat(AD_DATA_ARGUMENT_UTIL_WITH_COPIER.parseJsonResponse(aValidAdDataJson()))
                .isEqualTo(AD_DATA);
    }

    @Test
    public void testShouldReadValidJSONWithAdCounterKeys() throws Exception {
        assertThat(
                        AD_DATA_ARGUMENT_UTIL_WITH_COPIER.parseJsonResponse(
                                aValidAdDataWithAdCounterKeysJson()))
                .isEqualTo(AD_DATA_WITH_AD_COUNTER_KEYS);
    }

    @Test
    public void testShouldFailIfAdDataHasInvalidMetadata() throws JSONException {
        JSONObject adDataWithInvalidMetadata =
                aValidAdDataJson().put(AdDataArgumentUtil.METADATA_FIELD_NAME, 10);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        AD_DATA_ARGUMENT_UTIL_WITHOUT_COPIER.parseJsonResponse(
                                adDataWithInvalidMetadata));
    }

    @Test
    public void testShouldFailIfAdDataHasInvalidMetadataParseWithCopier() throws JSONException {
        JSONObject adDataWithInvalidMetadata =
                aValidAdDataJson().put(AdDataArgumentUtil.METADATA_FIELD_NAME, 10);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        AD_DATA_ARGUMENT_UTIL_WITH_COPIER.parseJsonResponse(
                                adDataWithInvalidMetadata));
    }

    @Test
    public void testShouldFailIfAdDataIsMissingMetadata() throws JSONException {
        JSONObject adDataWithoutMetadata = aValidAdDataJson();
        adDataWithoutMetadata.remove(AdDataArgumentUtil.METADATA_FIELD_NAME);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        AD_DATA_ARGUMENT_UTIL_WITHOUT_COPIER.parseJsonResponse(
                                adDataWithoutMetadata));
    }

    @Test
    public void testShouldFailIfAdDataIsMissingMetadataParseWithCopier() throws JSONException {
        JSONObject adDataWithoutMetadata = aValidAdDataJson();
        adDataWithoutMetadata.remove(AdDataArgumentUtil.METADATA_FIELD_NAME);
        assertThrows(
                IllegalArgumentException.class,
                () -> AD_DATA_ARGUMENT_UTIL_WITH_COPIER.parseJsonResponse(adDataWithoutMetadata));
    }

    @Test
    public void testShouldFailIfAdDataIsMissingRenderUri() throws JSONException {
        JSONObject adDataWithoutRenderUri = aValidAdDataJson();
        adDataWithoutRenderUri.remove(AdDataArgumentUtil.RENDER_URI_FIELD_NAME);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        AD_DATA_ARGUMENT_UTIL_WITHOUT_COPIER.parseJsonResponse(
                                adDataWithoutRenderUri));
    }

    @Test
    public void testShouldFailIfAdDataIsMissingRenderUriParseWithCopier() throws JSONException {
        JSONObject adDataWithoutRenderUri = aValidAdDataJson();
        adDataWithoutRenderUri.remove(AdDataArgumentUtil.RENDER_URI_FIELD_NAME);
        assertThrows(
                IllegalArgumentException.class,
                () -> AD_DATA_ARGUMENT_UTIL_WITH_COPIER.parseJsonResponse(adDataWithoutRenderUri));
    }

    @Test
    public void testConversionToScriptArgument() throws JSONException {
        assertThat(AD_DATA_ARGUMENT_UTIL_WITHOUT_COPIER.asScriptArgument("name", AD_DATA))
                .isEqualTo(
                        recordArg(
                                "name",
                                stringArg(
                                        AdDataArgumentUtil.RENDER_URI_FIELD_NAME,
                                        AD_DATA.getRenderUri().toString()),
                                jsonArg(
                                        AdDataArgumentUtil.METADATA_FIELD_NAME,
                                        AD_DATA.getMetadata())));
    }

    @Test
    public void testConversionToScriptArgumentWithCopier() throws JSONException {
        assertThat(AD_DATA_ARGUMENT_UTIL_WITH_COPIER.asScriptArgument("name", AD_DATA))
                .isEqualTo(
                        recordArg(
                                "name",
                                stringArg(
                                        AdDataArgumentUtil.RENDER_URI_FIELD_NAME,
                                        AD_DATA.getRenderUri().toString()),
                                jsonArg(
                                        AdDataArgumentUtil.METADATA_FIELD_NAME,
                                        AD_DATA.getMetadata())));
    }

    @Test
    public void testConversionToScriptArgumentWithAdCounterKeys() throws JSONException {
        assertThat(
                        AD_DATA_ARGUMENT_UTIL_WITH_COPIER.asScriptArgument(
                                "name", AD_DATA_WITH_AD_COUNTER_KEYS))
                .isEqualTo(
                        recordArg(
                                "name",
                                stringArg(
                                        AdDataArgumentUtil.RENDER_URI_FIELD_NAME,
                                        AD_DATA_WITH_AD_COUNTER_KEYS.getRenderUri().toString()),
                                jsonArg(
                                        AdDataArgumentUtil.METADATA_FIELD_NAME,
                                        AD_DATA_WITH_AD_COUNTER_KEYS.getMetadata()),
                                numericArrayArg(
                                        AdCounterKeyCopierImpl.AD_COUNTER_KEYS_FIELD_NAME,
                                        new ArrayList<>(
                                                AD_DATA_WITH_AD_COUNTER_KEYS.getAdCounterKeys()))));
    }
}
