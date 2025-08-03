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

package com.android.adservices.service.measurement;

import androidx.test.filters.SmallTest;

import org.junit.Assert;
import org.junit.Test;

import java.util.List;
import java.util.Map;

/** Unit tests for {@link MeasurementHttpResponse} */
@SmallTest
public class MeasurementHttpResponseTest {

    @Test
    public void testBuildWithMinimalRequiredValues() throws Exception {
        final int responseCode = 200;
        final MeasurementHttpResponse response =
                new MeasurementHttpResponse.Builder().setStatusCode(responseCode).build();

        Assert.assertNotNull(response);
        Assert.assertEquals(responseCode, response.getStatusCode());
        Assert.assertNotNull(response.getHeaders());
        Assert.assertEquals(0, response.getHeaders().size());
        Assert.assertNull(response.getPayload());
    }

    @Test
    public void testBuildWithAllValues() throws Exception {
        final int responseCode = 202;
        final String payload = "{\"foo\":\"bar\"}";
        final MeasurementHttpResponse response =
                new MeasurementHttpResponse.Builder()
                        .setStatusCode(responseCode)
                        .setPayload(payload)
                        .setHeaders(
                                Map.of(
                                        "x-content", List.of("1", "2"),
                                        "api-key", List.of("xyz")))
                        .build();

        Assert.assertNotNull(response);
        Assert.assertEquals(responseCode, response.getStatusCode());
        Assert.assertNotNull(response.getHeaders());
        Assert.assertEquals(2, response.getHeaders().size());
        Assert.assertEquals("1", response.getHeaders().get("x-content").get(0));
        Assert.assertEquals("2", response.getHeaders().get("x-content").get(1));
        Assert.assertEquals("xyz", response.getHeaders().get("api-key").get(0));
        Assert.assertNotNull(response.getPayload());
        Assert.assertEquals(payload, response.getPayload());
    }

    @Test
    public void testMissingRequiredFields_throwIllegalArgumentException() throws Exception {
        Assert.assertThrows(
                IllegalArgumentException.class,
                () ->
                        new MeasurementHttpResponse.Builder()
                                .setHeaders(Map.of())
                                .setPayload("payload")
                                .build());
    }
}
