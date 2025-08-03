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

package com.android.adservices.data.signals;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

import android.adservices.common.AdTechIdentifier;
import android.adservices.common.CommonFixture;

import com.android.adservices.common.SdkLevelSupportRule;

import org.junit.Rule;
import org.junit.Test;

public class DBEncoderEndpointTest {
    private static final String PATH = "/download/encoder";

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastT();

    @Test
    public void testCreateEndPoint() {
        AdTechIdentifier buyer = CommonFixture.VALID_BUYER_1;
        DBEncoderEndpoint endpoint =
                DBEncoderEndpoint.create(
                        buyer, CommonFixture.getUri(buyer, PATH), CommonFixture.FIXED_NOW);
        assertEquals(buyer, endpoint.getBuyer());
        assertEquals(CommonFixture.getUri(buyer, PATH), endpoint.getDownloadUri());
        assertEquals(CommonFixture.FIXED_NOW, endpoint.getCreationTime());
    }

    @Test
    public void testBuildEndPoint() {
        AdTechIdentifier buyer = CommonFixture.VALID_BUYER_1;
        DBEncoderEndpoint endpoint =
                DBEncoderEndpoint.builder()
                        .setBuyer(buyer)
                        .setDownloadUri(CommonFixture.getUri(buyer, PATH))
                        .setCreationTime(CommonFixture.FIXED_NOW)
                        .build();
        assertEquals(buyer, endpoint.getBuyer());
        assertEquals(CommonFixture.getUri(buyer, PATH), endpoint.getDownloadUri());
        assertEquals(CommonFixture.FIXED_NOW, endpoint.getCreationTime());
    }

    @Test
    public void testNullFails() {
        assertThrows(
                IllegalStateException.class,
                () -> {
                    DBEncoderEndpoint.builder().build();
                });
    }

    @Test
    public void testEquals() {
        AdTechIdentifier buyer = CommonFixture.VALID_BUYER_1;
        DBEncoderEndpoint endpoint1 =
                DBEncoderEndpoint.builder()
                        .setBuyer(buyer)
                        .setDownloadUri(CommonFixture.getUri(buyer, PATH))
                        .setCreationTime(CommonFixture.FIXED_NOW)
                        .build();
        DBEncoderEndpoint endpoint2 =
                DBEncoderEndpoint.builder()
                        .setBuyer(buyer)
                        .setDownloadUri(CommonFixture.getUri(buyer, PATH))
                        .setCreationTime(CommonFixture.FIXED_NOW)
                        .build();
        assertEquals(endpoint1, endpoint2);
    }

    @Test
    public void testNotEquals() {
        AdTechIdentifier buyer1 = CommonFixture.VALID_BUYER_1;
        AdTechIdentifier buyer2 = CommonFixture.VALID_BUYER_2;
        DBEncoderEndpoint endpoint1 =
                DBEncoderEndpoint.builder()
                        .setBuyer(buyer1)
                        .setDownloadUri(CommonFixture.getUri(buyer1, PATH))
                        .setCreationTime(CommonFixture.FIXED_NOW)
                        .build();
        DBEncoderEndpoint endpoint2 =
                DBEncoderEndpoint.builder()
                        .setBuyer(buyer2)
                        .setDownloadUri(CommonFixture.getUri(buyer2, PATH))
                        .setCreationTime(CommonFixture.FIXED_NOW)
                        .build();
        assertNotEquals(endpoint1, endpoint2);
    }

    @Test
    public void testHashCode() {
        AdTechIdentifier buyer = CommonFixture.VALID_BUYER_1;
        DBEncoderEndpoint endpoint1 =
                DBEncoderEndpoint.builder()
                        .setBuyer(buyer)
                        .setDownloadUri(CommonFixture.getUri(buyer, PATH))
                        .setCreationTime(CommonFixture.FIXED_NOW)
                        .build();
        DBEncoderEndpoint endpoint2 =
                DBEncoderEndpoint.builder()
                        .setBuyer(buyer)
                        .setDownloadUri(CommonFixture.getUri(buyer, PATH))
                        .setCreationTime(CommonFixture.FIXED_NOW)
                        .build();
        assertEquals(endpoint1.hashCode(), endpoint2.hashCode());
    }
}
