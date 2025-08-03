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

package com.android.adservices.service.signals;

import android.adservices.common.CommonFixture;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProtectedSignalsFixture {

    private static final Instant NOW = CommonFixture.FIXED_NOW;

    /**
     * Generates a map of keys and List of {@link ProtectedSignal} where keys and values are base64
     *
     * @param seeds These gets transformed to the base64 encode keys, and appended to value and
     *     package name in the {@link ProtectedSignal}
     * @param count for each key, these many values are generated and put in the map
     */
    public static Map<String, List<ProtectedSignal>> generateMapOfProtectedSignals(
            List<String> seeds, int count) {

        Map<String, List<ProtectedSignal>> protectedSignalsMap = new HashMap<>();
        for (String seed : seeds) {
            String base64EncodedKey = generateKey(seed);
            protectedSignalsMap.putIfAbsent(base64EncodedKey, new ArrayList<>());
            for (int i = 1; i <= count; i++) {
                protectedSignalsMap
                        .get(base64EncodedKey)
                        .add(generateDBProtectedSignal(seed, new byte[] {(byte) i}));
            }
        }

        return protectedSignalsMap;
    }

    /**
     * @return a DB Protected signal instance where the package name starts with the given {@code
     *     seed}, the creation time is the fixed {@code NOW} value and the signal value is the given
     *     value.
     */
    public static ProtectedSignal generateDBProtectedSignal(String seed, byte[] value) {
        return ProtectedSignal.builder()
                .setCreationTime(NOW)
                .setBase64EncodedValue(Base64.getEncoder().encodeToString(value))
                .setPackageName(generatePackageName(seed))
                .build();
    }

    private static String generateKey(String seed) {
        String key = "TestKey" + seed;
        return Base64.getEncoder().encodeToString(key.getBytes());
    }

    private static String generateValue(String seed) {
        String value = "TestValue" + seed;
        return Base64.getEncoder().encodeToString(value.getBytes());
    }

    private static String generatePackageName(String seed) {
        return "com.fake.package" + seed;
    }
}
