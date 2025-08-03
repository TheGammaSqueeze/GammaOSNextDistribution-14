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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.net.Uri;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.service.common.httpclient.AdServicesHttpClientRequest;
import com.android.adservices.service.devapi.DevContext;

import com.google.common.collect.ImmutableMap;

import org.junit.Rule;
import org.junit.Test;

import java.util.List;
import java.util.Map;

public class JsVersionHelperTest {

    private static final Uri URI = Uri.parse("https://example.com");
    private static final Long VERSION = 3L;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Test
    public void testGetRequestWithVersionAttribute() {
        AdServicesHttpClientRequest request =
                JsVersionHelper.getRequestWithVersionHeader(
                        URI,
                        ImmutableMap.of(
                                JsVersionHelper.JS_PAYLOAD_TYPE_BUYER_BIDDING_LOGIC_JS, VERSION),
                        false,
                        DevContext.createForDevOptionsDisabled());

        assertEquals(request.getUri(), URI);
        assertEquals(request.getRequestProperties().size(), 1);
        assertEquals(
                request.getRequestProperties()
                        .get(
                                JsVersionHelper.getVersionHeaderName(
                                        JsVersionHelper.JS_PAYLOAD_TYPE_BUYER_BIDDING_LOGIC_JS)),
                Long.toString(VERSION));
        assertEquals(request.getResponseHeaderKeys().size(), 1);
        assertTrue(
                request.getResponseHeaderKeys()
                        .contains(
                                JsVersionHelper.getVersionHeaderName(
                                        JsVersionHelper.JS_PAYLOAD_TYPE_BUYER_BIDDING_LOGIC_JS)));
        assertFalse(request.getUseCache());
    }

    @Test
    public void testConstructVersionHeader() {
        Map<String, List<String>> header =
                JsVersionHelper.constructVersionHeader(
                        JsVersionHelper.JS_PAYLOAD_TYPE_BUYER_BIDDING_LOGIC_JS, VERSION);

        assertEquals(header.size(), 1);
        assertEquals(
                header.get(
                                JsVersionHelper.getVersionHeaderName(
                                        JsVersionHelper.JS_PAYLOAD_TYPE_BUYER_BIDDING_LOGIC_JS))
                        .size(),
                1);
        assertEquals(
                header.get(
                                JsVersionHelper.getVersionHeaderName(
                                        JsVersionHelper.JS_PAYLOAD_TYPE_BUYER_BIDDING_LOGIC_JS))
                        .get(0),
                Long.toString(VERSION));
    }
}
