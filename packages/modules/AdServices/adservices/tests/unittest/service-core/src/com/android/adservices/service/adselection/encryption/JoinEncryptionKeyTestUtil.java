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

package com.android.adservices.service.adselection.encryption;

import static com.android.adservices.data.adselection.EncryptionKeyConstants.EncryptionKeyType.ENCRYPTION_KEY_TYPE_JOIN;

import com.android.adservices.data.adselection.DBEncryptionKey;
import com.android.adservices.data.adselection.DBProtectedServersEncryptionConfig;
import com.android.adservices.service.common.httpclient.AdServicesHttpClientResponse;

import com.google.common.collect.ImmutableMap;

import java.util.List;

public class JoinEncryptionKeyTestUtil {

    static final String CONTENT_TYPE_HEADER_LABEL = "Content-Type";
    static final String CONTENT_TYPE = "application/ohttp-keys";
    private static final Long EXPIRY_TTL_1SEC = 1L;
    public static final String COORDINATOR_URL_JOIN = "https://example-join.com";

    public static final String JOIN_PUBLIC_KEY_1_BASE_16 =
            "01002031e1f05a740102115220e9af918f738674aec95f54db6e04eb705aae8e"
                    + "79815500080001000100010003";
    public static final String JOIN_PUBLIC_KEY_2_BASE_64 =
            "aMbHJuvvZ4qroxfl/WmLdLITQ4linmPqd7WXvd2WJt8yAAQAAQAC";
    public static final String JOIN_PUBLIC_KEY_2_BASE_16 =
            "68c6c726ebef678aaba317e5fd698b74b2134389629e63ea77b597bddd9626df32000400010002";

    static final DBEncryptionKey ENCRYPTION_KEY_JOIN =
            DBEncryptionKey.builder()
                    .setKeyIdentifier("key_id_2")
                    .setPublicKey(JOIN_PUBLIC_KEY_1_BASE_16)
                    .setEncryptionKeyType(ENCRYPTION_KEY_TYPE_JOIN)
                    .setExpiryTtlSeconds(1000L)
                    .build();

    static final DBProtectedServersEncryptionConfig ENCRYPTION_KEY_JOIN_WITH_COORDINATOR =
            DBProtectedServersEncryptionConfig.builder()
                    .setKeyIdentifier("key_id_2")
                    .setPublicKey(JOIN_PUBLIC_KEY_1_BASE_16)
                    .setEncryptionKeyType(ENCRYPTION_KEY_TYPE_JOIN)
                    .setExpiryTtlSeconds(1000L)
                    .setCoordinatorUrl(COORDINATOR_URL_JOIN)
                    .build();

    static final DBEncryptionKey ENCRYPTION_KEY_JOIN_TTL_1SECS =
            DBEncryptionKey.builder()
                    .setKeyIdentifier("key_id_5")
                    .setPublicKey(JOIN_PUBLIC_KEY_1_BASE_16)
                    .setEncryptionKeyType(ENCRYPTION_KEY_TYPE_JOIN)
                    .setExpiryTtlSeconds(EXPIRY_TTL_1SEC)
                    .build();

    static final DBProtectedServersEncryptionConfig ENCRYPTION_KEY_JOIN_WITH_COORDINATOR_TTL_1SECS =
            DBProtectedServersEncryptionConfig.builder()
                    .setKeyIdentifier("key_id_5")
                    .setPublicKey(JOIN_PUBLIC_KEY_1_BASE_16)
                    .setEncryptionKeyType(ENCRYPTION_KEY_TYPE_JOIN)
                    .setExpiryTtlSeconds(EXPIRY_TTL_1SEC)
                    .setCoordinatorUrl(COORDINATOR_URL_JOIN)
                    .build();

    static final ImmutableMap<String, List<String>> DEFAULT_JOIN_HEADERS =
            ImmutableMap.of(CONTENT_TYPE_HEADER_LABEL, List.of(CONTENT_TYPE));

    static String getDefaultJoinResponseBody() {
        return JOIN_PUBLIC_KEY_2_BASE_64;
    }

    static AdServicesHttpClientResponse mockJoinKeyFetchResponse() {
        return AdServicesHttpClientResponse.builder()
                .setResponseHeaders(DEFAULT_JOIN_HEADERS)
                .setResponseBody(getDefaultJoinResponseBody())
                .build();
    }
}
