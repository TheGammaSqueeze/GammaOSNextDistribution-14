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

import static android.adservices.adselection.AuctionEncryptionKeyFixture.ENCRYPTION_KEY_AUCTION;

import static com.android.adservices.service.adselection.encryption.JoinEncryptionKeyTestUtil.DEFAULT_JOIN_HEADERS;
import static com.android.adservices.service.adselection.encryption.JoinEncryptionKeyTestUtil.ENCRYPTION_KEY_JOIN;
import static com.android.adservices.service.adselection.encryption.JoinEncryptionKeyTestUtil.JOIN_PUBLIC_KEY_1_BASE_16;
import static com.android.adservices.service.adselection.encryption.JoinEncryptionKeyTestUtil.JOIN_PUBLIC_KEY_2_BASE_16;
import static com.android.adservices.service.adselection.encryption.JoinEncryptionKeyTestUtil.mockJoinKeyFetchResponse;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.data.adselection.DBEncryptionKey;
import com.android.adservices.data.adselection.EncryptionKeyConstants;
import com.android.adservices.ohttp.ObliviousHttpKeyConfig;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.httpclient.AdServicesHttpClientResponse;

import com.google.common.io.BaseEncoding;

import org.json.JSONException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.spec.InvalidKeySpecException;
import java.util.List;

public class JoinEncryptionKeyParserTest {
    private static final long DEFAULT_MAX_AGE_SECONDS = 100L;

    private Flags mFlags = new JoinEncryptionKeyParserTestFlags();
    private JoinEncryptionKeyParser mJoinEncryptionKeyParser;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setUp() {
        mJoinEncryptionKeyParser = new JoinEncryptionKeyParser(mFlags);
    }

    @Test
    public void parseDbEncryptionKey_nullDbEncryptionKey_throwsNPE() {
        assertThrows(
                NullPointerException.class,
                () -> mJoinEncryptionKeyParser.parseDbEncryptionKey(null));
    }

    @Test
    public void parseDbEncryptionKey_wrongKeyType_throwsIAE() {
        assertThrows(
                IllegalArgumentException.class,
                () -> mJoinEncryptionKeyParser.parseDbEncryptionKey(ENCRYPTION_KEY_AUCTION));
    }

    @Test
    public void parseDbEncryptionKey_returnsSuccess() {
        assertThat(mJoinEncryptionKeyParser.parseDbEncryptionKey(ENCRYPTION_KEY_JOIN))
                .isEqualTo(
                        AdSelectionEncryptionKey.builder()
                                .setKeyType(
                                        AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.JOIN)
                                .setKeyIdentifier(ENCRYPTION_KEY_JOIN.getKeyIdentifier())
                                .setPublicKey(
                                        BaseEncoding.base16()
                                                .lowerCase()
                                                .decode(JOIN_PUBLIC_KEY_1_BASE_16))
                                .build());
    }

    @Test
    public void getDbEncryptionKeys_missingHeaders_throwsIAE() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mJoinEncryptionKeyParser.getDbEncryptionKeys(
                                AdServicesHttpClientResponse.builder()
                                        .setResponseBody("")
                                        .build()));
    }

    @Test
    public void getDbEncryptionKeys_emptyResponseBody_throwsIAE() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        mJoinEncryptionKeyParser.getDbEncryptionKeys(
                                AdServicesHttpClientResponse.builder()
                                        .setResponseBody("")
                                        .setResponseHeaders(DEFAULT_JOIN_HEADERS)
                                        .build()));
    }

    @Test
    public void getDbEncryptionKeys_missingMaxAge_usesDefaultAge() throws JSONException {
        List<DBEncryptionKey> keys =
                mJoinEncryptionKeyParser.getDbEncryptionKeys(mockJoinKeyFetchResponse());

        assertThat(keys).hasSize(1);
        assertThat(keys.get(0).getKeyIdentifier()).isEqualTo("h");
        assertThat(keys.get(0).getPublicKey()).isEqualTo(JOIN_PUBLIC_KEY_2_BASE_16);
        assertThat(keys.get(0).getEncryptionKeyType())
                .isEqualTo(EncryptionKeyConstants.EncryptionKeyType.ENCRYPTION_KEY_TYPE_JOIN);
        assertThat(keys.get(0).getExpiryTtlSeconds()).isEqualTo(DEFAULT_MAX_AGE_SECONDS);
    }

    @Test
    public void getObliviousHttpKeyConfig_wrongFormat_throwsException() {
        assertThrows(
                InvalidKeySpecException.class,
                () ->
                        mJoinEncryptionKeyParser.getObliviousHttpKeyConfig(
                                AdSelectionEncryptionKey.builder()
                                        .setKeyIdentifier("key_id")
                                        .setKeyType(
                                                AdSelectionEncryptionKey
                                                        .AdSelectionEncryptionKeyType.JOIN)
                                        .setPublicKey("public_key".getBytes(StandardCharsets.UTF_8))
                                        .build()));
    }

    @Test
    public void getObliviousHttpKeyConfig_returnsKeyConfig() throws Exception {
        String keyConfigHex =
                "01002031e1f05a740102115220e9af918f738674aec95f54db6e04eb705aae8e798155"
                        + "00080001000100010003";
        byte[] bytes = BaseEncoding.base16().lowerCase().decode(keyConfigHex);
        AdSelectionEncryptionKey key =
                AdSelectionEncryptionKey.builder()
                        .setKeyIdentifier("1")
                        .setKeyType(AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.JOIN)
                        .setPublicKey(bytes)
                        .build();

        ObliviousHttpKeyConfig keyConfig = mJoinEncryptionKeyParser.getObliviousHttpKeyConfig(key);
        assertThat(keyConfig.keyId()).isEqualTo(0x01);
        assertThat(BaseEncoding.base16().lowerCase().encode(keyConfig.getPublicKey()))
                .isEqualTo("31e1f05a740102115220e9af918f738674aec95f54db6e04eb705aae8e798155");
        assertThat(keyConfig.kemId()).isEqualTo(0x0020);
        assertThat(keyConfig.kdfId()).isEqualTo(0x0001);
        assertThat(keyConfig.aeadId()).isEqualTo(0x0001);
    }

    private static class JoinEncryptionKeyParserTestFlags implements Flags {
        JoinEncryptionKeyParserTestFlags() {}

        @Override
        public long getFledgeAuctionServerEncryptionKeyMaxAgeSeconds() {
            return DEFAULT_MAX_AGE_SECONDS;
        }
    }
}
