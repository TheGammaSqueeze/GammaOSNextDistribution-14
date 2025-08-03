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
package com.android.adservices.service.measurement.aggregation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import android.net.Uri;

import androidx.test.filters.SmallTest;

import org.junit.Test;

/**
 * Unit tests for {@link AggregateEncryptionKey}
 */
@SmallTest
public final class AggregateEncryptionKeyTest {
    private static final String KEY_ID = "38b1d571-f924-4dc0-abe1-e2bac9b6a6be";
    private static final String PUBLIC_KEY = "/amqBgfDOvHAIuatDyoHxhfHaMoYA4BDxZxwtWBRQhc=";
    private static final long EXPIRY = 1653594000961L;
    private static final Uri AGGREGATION_COORDINATOR_ORIGIN = Uri.parse("https://1.test");

    private AggregateEncryptionKey createExample() {
        return new AggregateEncryptionKey.Builder()
                .setKeyId(KEY_ID)
                .setPublicKey(PUBLIC_KEY)
                .setExpiry(EXPIRY)
                .setAggregationCoordinatorOrigin(AGGREGATION_COORDINATOR_ORIGIN)
                .build();
    }

    void verifyExample(AggregateEncryptionKey aggregateEncryptionKey) {
        assertEquals(KEY_ID, aggregateEncryptionKey.getKeyId());
        assertEquals(PUBLIC_KEY, aggregateEncryptionKey.getPublicKey());
        assertEquals(EXPIRY, aggregateEncryptionKey.getExpiry());
        assertEquals(
                AGGREGATION_COORDINATOR_ORIGIN,
                aggregateEncryptionKey.getAggregationCoordinatorOrigin());
    }

    @Test
    public void testCreation() {
        verifyExample(createExample());
    }

    @Test
    public void testFailsToBuildWithNullKeyId() {
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    new AggregateEncryptionKey.Builder()
                            .setPublicKey(PUBLIC_KEY)
                            .setExpiry(EXPIRY)
                            .setAggregationCoordinatorOrigin(AGGREGATION_COORDINATOR_ORIGIN)
                            .build();
                });
    }

    @Test
    public void testFailsToBuildWithNullPublicKey() {
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    new AggregateEncryptionKey.Builder()
                            .setKeyId(KEY_ID)
                            .setExpiry(EXPIRY)
                            .setAggregationCoordinatorOrigin(AGGREGATION_COORDINATOR_ORIGIN)
                            .build();
                });
    }

    @Test
    public void testFailsToBuildWithoutSetExpiry() {
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    new AggregateEncryptionKey.Builder()
                            .setKeyId(KEY_ID)
                            .setPublicKey(PUBLIC_KEY)
                            .setAggregationCoordinatorOrigin(AGGREGATION_COORDINATOR_ORIGIN)
                            .build();
                });
    }

    @Test
    public void testFailsToBuildWithoutCoordinatorOrigin() {
        assertThrows(
                IllegalArgumentException.class,
                () -> {
                    new AggregateEncryptionKey.Builder()
                            .setKeyId(KEY_ID)
                            .setPublicKey(PUBLIC_KEY)
                            .setExpiry(EXPIRY)
                            .build();
                });
    }
}
