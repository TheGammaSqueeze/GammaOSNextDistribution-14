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

import java.time.Instant;

public class DBEncodedPayloadTest {

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastT();

    @Test
    public void testCreateEncodedPayload() {
        AdTechIdentifier buyer = CommonFixture.VALID_BUYER_1;
        int version = 1;
        Instant time = CommonFixture.FIXED_NOW;
        byte[] payload = {(byte) 10, (byte) 20, (byte) 30, (byte) 40};

        DBEncodedPayload encodedPayload = DBEncodedPayload.create(buyer, version, time, payload);
        assertEquals(buyer, encodedPayload.getBuyer());
        assertEquals(version, (int) encodedPayload.getVersion());
        assertEquals(time, encodedPayload.getCreationTime());
        assertEquals(payload, encodedPayload.getEncodedPayload());
    }

    @Test
    public void testBuildEncodedPayload() {
        AdTechIdentifier buyer = CommonFixture.VALID_BUYER_1;
        int version = 1;
        Instant time = CommonFixture.FIXED_NOW;
        byte[] payload = {(byte) 10, (byte) 20, (byte) 30, (byte) 40};

        DBEncodedPayload encodedPayload =
                DBEncodedPayload.builder()
                        .setBuyer(buyer)
                        .setVersion(version)
                        .setCreationTime(time)
                        .setEncodedPayload(payload)
                        .build();
        assertEquals(buyer, encodedPayload.getBuyer());
        assertEquals(version, (int) encodedPayload.getVersion());
        assertEquals(time, encodedPayload.getCreationTime());
        assertEquals(payload, encodedPayload.getEncodedPayload());
    }

    @Test
    public void testNullFails() {
        assertThrows(
                IllegalStateException.class,
                () -> {
                    DBEncodedPayload.builder().build();
                });
    }

    @Test
    public void testEquals() {
        DBEncodedPayload payload1 = DBEncodedPayloadFixture.anEncodedPayload();
        DBEncodedPayload payload2 = DBEncodedPayloadFixture.anEncodedPayload();

        assertEquals(payload1, payload2);
    }

    @Test
    public void testNotEquals() {
        DBEncodedPayload payload1 = DBEncodedPayloadFixture.anEncodedPayload();
        DBEncodedPayload payload2 =
                DBEncodedPayloadFixture.anEncodedPayloadBuilder().setVersion(2).build();

        assertNotEquals(payload1, payload2);
    }

    @Test
    public void testHashCode() {
        DBEncodedPayload payload1 = DBEncodedPayloadFixture.anEncodedPayload();
        DBEncodedPayload payload2 = DBEncodedPayloadFixture.anEncodedPayload();

        assertEquals(payload1.hashCode(), payload2.hashCode());
    }
}
