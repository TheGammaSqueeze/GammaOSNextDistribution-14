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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

import android.net.Uri;

import com.android.adservices.common.WebUtil;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.net.ssl.HttpsURLConnection;

class AggregateEncryptionKeyTestUtil {
    static final Uri DEFAULT_TARGET = WebUtil.validUri("https://foo.test");
    static final Uri DEFAULT_COORDINATOR_ORIGIN = WebUtil.validUri("https://foo.test");
    static final String DEFAULT_MAX_AGE = "max-age=604800";
    static final String DEFAULT_CACHED_AGE = "800";
    static final long DEFAULT_EVENT_TIME = 1653681612892L;
    static final long DEFAULT_EXPIRY = 1654285612892L; // 1653681612892L + (604000000L - 800000L)

    interface DEFAULT_KEY_1 {
        String KEY_ID = "38b1d571-f924-4dc0-abe1-e2bac9b6a6be";
        String PUBLIC_KEY = "/amqBgfDOvHAIuatDyoHxhfHaMoYA4BDxZxwtWBRQhc=";
    }

    interface DEFAULT_KEY_2 {
        String KEY_ID = "e52dbbda-4e3a-4380-a7c8-14db3e08ef33";
        String PUBLIC_KEY = "dU3hTbFy1RgCddQIQIZjoVNPJ3KScryj8BSREFr9yW8=";
    }

    static String getDefaultResponseBody() {
        return String.format("{\"keys\":[{\"id\":\"%s\",\"key\":\"%s\"},"
                + "{\"id\":\"%s\",\"key\":\"%s\"}]}",
                DEFAULT_KEY_1.KEY_ID, DEFAULT_KEY_1.PUBLIC_KEY,
                DEFAULT_KEY_2.KEY_ID, DEFAULT_KEY_2.PUBLIC_KEY);
    }

    static void prepareMockAggregateEncryptionKeyFetcher(AggregateEncryptionKeyFetcher fetcher,
            HttpsURLConnection urlConnection, String response) throws IOException {
        InputStream inputStream = new ByteArrayInputStream(response.getBytes());
        doReturn(urlConnection).when(fetcher).openUrl(any());
        when(urlConnection.getResponseCode()).thenReturn(200);
        when(urlConnection.getHeaderFields()).thenReturn(Map.of(
                "cache-control", List.of(DEFAULT_MAX_AGE),
                "age", List.of(DEFAULT_CACHED_AGE)));
        when(urlConnection.getInputStream()).thenReturn(inputStream);
    }

    static String prettify(List<AggregateEncryptionKey> keys) {
        StringBuilder result = new StringBuilder("\n\n----------\n");
        for (AggregateEncryptionKey key : keys) {
            result.append(String.format("id: %s\n", key.getId()));
            result.append(String.format("keyId: %s\n", key.getKeyId()));
            result.append(String.format("publicKey: %s\n", key.getPublicKey()));
            result.append(String.format("expiry: %s\n", key.getExpiry()));
            result.append(
                    String.format(
                            "aggregationCoordinatorOrigin: %s\n",
                            key.getAggregationCoordinatorOrigin()));
            result.append("----------\n\n");
        }
        return result.toString();
    }

    static boolean isSuperset(List<AggregateEncryptionKey> list1,
            List<AggregateEncryptionKey> list2) {
        Set<AggregateEncryptionKey> set1 = new HashSet(list1);
        Set<AggregateEncryptionKey> set2 = new HashSet(list2);
        return set1.containsAll(set2);
    }
}
