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

package com.android.federatedcompute.services.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;


@RunWith(AndroidJUnit4.class)
public class FederatedComputeEncryptionKeyTest {

    private static final String KEY_ID = "0962201a-5abd-4e25-a486-2c7bd1ee1887";
    private static final String PUBLIC_KEY = "GOcMAnY4WkDYp6R3WSw8IpYK6eVe2RGZ9Z0OBb3EbjQ\\u003d";

    private static final int KEY_TYPE = FederatedComputeEncryptionKey.KEY_TYPE_ENCRYPTION;

    private static final long NOW = 1698193647L;

    private static final long TTL = 100L;

    @Test
    public void testBuilderAndEquals() {
        FederatedComputeEncryptionKey key1 =
                new FederatedComputeEncryptionKey.Builder(
                                KEY_ID, PUBLIC_KEY, KEY_TYPE, NOW, NOW + TTL)
                        .build();

        FederatedComputeEncryptionKey key2 =
                new FederatedComputeEncryptionKey.Builder()
                        .setKeyIdentifier(KEY_ID)
                        .setPublicKey(PUBLIC_KEY)
                        .setKeyType(KEY_TYPE)
                        .setCreationTime(NOW)
                        .setExpiryTime(NOW + TTL)
                        .build();

        assertEquals(key1, key2);

        FederatedComputeEncryptionKey key3 =
                new FederatedComputeEncryptionKey.Builder()
                        .setKeyIdentifier(KEY_ID)
                        .setPublicKey(PUBLIC_KEY)
                        .setKeyType(FederatedComputeEncryptionKey.KEY_TYPE_UNDEFINED)
                        .setCreationTime(NOW)
                        .setExpiryTime(NOW + TTL)
                        .build();

        assertNotEquals(key1, key3);
        assertNotEquals(key2, key3);
    }

    @Test
    public void testBuildTwiceThrows() {
        FederatedComputeEncryptionKey.Builder builder =
                new FederatedComputeEncryptionKey.Builder(
                        KEY_ID, PUBLIC_KEY, KEY_TYPE, NOW, NOW + TTL);
        builder.build();

        assertThrows(IllegalStateException.class, () -> builder.build());
    }
}
