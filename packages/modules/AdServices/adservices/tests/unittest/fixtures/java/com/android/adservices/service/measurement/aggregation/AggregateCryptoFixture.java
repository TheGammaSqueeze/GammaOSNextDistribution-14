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

import android.net.Uri;

import com.android.adservices.common.WebUtil;

import java.time.Instant;
import java.util.Base64;

public class AggregateCryptoFixture {

    private static final String SHARED_INFO_PREFIX = "aggregation_service";
    private static final String PUBLIC_KEY_BASE64 = "rSJBSUYG0ebvfW1AXCWO0CMGMJhDzpfQm3eLyw1uxX8=";
    private static final String PRIVATE_KEY_BASE64 = "f86EzLmGaVmc+PwjJk5ADPE4ijQvliWf0CQyY/Zyy7I=";
    private static final byte[] sPublicKey = Base64.getDecoder().decode(PUBLIC_KEY_BASE64);
    private static final byte[] sPrivateKey = Base64.getDecoder().decode(PRIVATE_KEY_BASE64);

    public static final Uri AGGREGATE_COORDINATOR_ORIGIN =
            WebUtil.validUri("https://publickeyservice.test.privacysandboxservices.test");
    private static final AggregateEncryptionKey sKey =
            new AggregateEncryptionKey.Builder()
                    .setKeyId("ea91d481-1482-4975-8c63-635fb866b4c8")
                    .setPublicKey(PUBLIC_KEY_BASE64)
                    .setExpiry(Instant.now().plusSeconds(60).toEpochMilli())
                    .setAggregationCoordinatorOrigin(AGGREGATE_COORDINATOR_ORIGIN)
                    .build();

    public static String getSharedInfoPrefix() {
        return SHARED_INFO_PREFIX;
    }

    public static String getPublicKeyBase64() {
        return PUBLIC_KEY_BASE64;
    }

    public static String getPrivateKeyBase64() {
        return PRIVATE_KEY_BASE64;
    }

    public static byte[] getPublicKey() {
        return sPublicKey;
    }

    public static byte[] getPrivateKey() {
        return sPrivateKey;
    }

    public static AggregateEncryptionKey getKey() {
        return sKey;
    }
}
