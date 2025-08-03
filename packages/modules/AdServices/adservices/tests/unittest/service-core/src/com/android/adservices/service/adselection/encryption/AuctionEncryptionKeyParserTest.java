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

import static android.adservices.adselection.AuctionEncryptionKeyFixture.AUCTION_KEY_1;
import static android.adservices.adselection.AuctionEncryptionKeyFixture.DEFAULT_CACHED_AGE;
import static android.adservices.adselection.AuctionEncryptionKeyFixture.DEFAULT_MAX_AGE_SECONDS;
import static android.adservices.adselection.AuctionEncryptionKeyFixture.DEFAULT_RESPONSE_HEADERS;
import static android.adservices.adselection.AuctionEncryptionKeyFixture.ENCRYPTION_KEY_AUCTION;
import static android.adservices.adselection.AuctionEncryptionKeyFixture.getAuctionResponseBodySingleKey;
import static android.adservices.adselection.AuctionEncryptionKeyFixture.mockAuctionKeyFetchResponse;

import static com.android.adservices.service.adselection.encryption.JoinEncryptionKeyTestUtil.ENCRYPTION_KEY_JOIN;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.data.adselection.DBEncryptionKey;
import com.android.adservices.data.adselection.EncryptionKeyConstants;
import com.android.adservices.ohttp.ObliviousHttpKeyConfig;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.httpclient.AdServicesHttpClientResponse;

import org.json.JSONException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.List;

public class AuctionEncryptionKeyParserTest {

    private Flags mFlags = new AuctionEncryptionKeyParserTestFlags();
    private AuctionEncryptionKeyParser mAuctionEncryptionKeyParser;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setUp() {
        mAuctionEncryptionKeyParser = new AuctionEncryptionKeyParser(mFlags);
    }

    @Test
    public void parseDbEncryptionKey_nullDbEncryptionKey_throwsNPE() {
        assertThrows(
                NullPointerException.class,
                () -> mAuctionEncryptionKeyParser.parseDbEncryptionKey(null));
    }

    @Test
    public void parseDbEncryptionKey_wrongKeyType_throwsIAE() {
        assertThrows(
                IllegalArgumentException.class,
                () -> mAuctionEncryptionKeyParser.parseDbEncryptionKey(ENCRYPTION_KEY_JOIN));
    }

    @Test
    public void parseDbEncryptionKey_returnsSuccess() {
        assertThat(mAuctionEncryptionKeyParser.parseDbEncryptionKey(ENCRYPTION_KEY_AUCTION))
                .isEqualTo(
                        AdSelectionEncryptionKey.builder()
                                .setKeyType(
                                        AdSelectionEncryptionKey.AdSelectionEncryptionKeyType
                                                .AUCTION)
                                .setKeyIdentifier(ENCRYPTION_KEY_AUCTION.getKeyIdentifier())
                                .setPublicKey(
                                        Base64.getDecoder()
                                                .decode(
                                                        ENCRYPTION_KEY_AUCTION
                                                                .getPublicKey()
                                                                .getBytes(StandardCharsets.UTF_8)))
                                .build());
    }

    @Test
    public void getDbEncryptionKeys_emptyResponseBody_emptyList() {
        assertThat(
                        mAuctionEncryptionKeyParser.getDbEncryptionKeys(
                                AdServicesHttpClientResponse.builder().setResponseBody("").build()))
                .isEmpty();
    }

    @Test
    public void getDbEncryptionKeys_missingMaxAge_usesDefaultAge() throws JSONException {
        List<DBEncryptionKey> keys =
                mAuctionEncryptionKeyParser.getDbEncryptionKeys(
                        AdServicesHttpClientResponse.builder()
                                .setResponseBody(getAuctionResponseBodySingleKey())
                                .build());

        assertThat(keys).hasSize(1);
        assertThat(keys.get(0).getKeyIdentifier()).isEqualTo(AUCTION_KEY_1.keyId());
        assertThat(keys.get(0).getPublicKey()).isEqualTo(AUCTION_KEY_1.publicKey());
        assertThat(keys.get(0).getEncryptionKeyType())
                .isEqualTo(EncryptionKeyConstants.EncryptionKeyType.ENCRYPTION_KEY_TYPE_AUCTION);
        assertThat(keys.get(0).getExpiryTtlSeconds()).isEqualTo(DEFAULT_MAX_AGE_SECONDS);
    }

    @Test
    public void getDbEncryptionKeys_parsesSingleKey() throws JSONException {
        List<DBEncryptionKey> keys =
                mAuctionEncryptionKeyParser.getDbEncryptionKeys(
                        AdServicesHttpClientResponse.builder()
                                .setResponseBody(getAuctionResponseBodySingleKey())
                                .setResponseHeaders(DEFAULT_RESPONSE_HEADERS)
                                .build());

        assertThat(keys).hasSize(1);
        assertThat(keys.get(0).getKeyIdentifier()).isEqualTo(AUCTION_KEY_1.keyId());
        assertThat(keys.get(0).getPublicKey()).isEqualTo(AUCTION_KEY_1.publicKey());
        assertThat(keys.get(0).getEncryptionKeyType())
                .isEqualTo(EncryptionKeyConstants.EncryptionKeyType.ENCRYPTION_KEY_TYPE_AUCTION);
        assertThat(keys.get(0).getExpiryTtlSeconds())
                .isEqualTo(DEFAULT_MAX_AGE_SECONDS - Long.valueOf(DEFAULT_CACHED_AGE));
    }

    @Test
    public void getDbEncryptionKeys_parsesMultipleKeys() throws JSONException {
        List<DBEncryptionKey> keys =
                mAuctionEncryptionKeyParser.getDbEncryptionKeys(mockAuctionKeyFetchResponse());
        assertThat(keys).hasSize(5);
    }

    @Test
    public void getObliviousHttpKeyConfig_insufficientKeyLength_throwsError() {
        byte[] keyContent =
                "7tabvCt19oMF5Quu4cAQetS6xlLFkjIbcY6330+cjlo=".getBytes(StandardCharsets.UTF_8);
        AdSelectionEncryptionKey key =
                AdSelectionEncryptionKey.builder()
                        .setKeyType(AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION)
                        .setKeyIdentifier("1")
                        .setPublicKey(keyContent)
                        .build();

        Assert.assertThrows(
                InvalidKeySpecException.class,
                () -> mAuctionEncryptionKeyParser.getObliviousHttpKeyConfig(key));
    }

    @Test
    public void getObliviousHttpKeyConfig_keyNotHexadecimal_throwsError() {
        byte[] keyContent =
                "7tabvCt19oMF5Quu4cAQetS6xlLFkjIbcY6330+cjlo=".getBytes(StandardCharsets.UTF_8);
        AdSelectionEncryptionKey key =
                AdSelectionEncryptionKey.builder()
                        .setKeyType(AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION)
                        .setKeyIdentifier("@12231")
                        .setPublicKey(keyContent)
                        .build();

        Assert.assertThrows(
                InvalidKeySpecException.class,
                () -> mAuctionEncryptionKeyParser.getObliviousHttpKeyConfig(key));
    }

    @Test
    public void getObliviousHttpKeyConfig_convertsKmsKeyIdToOhttpKeyIdCorrectly() throws Exception {
        byte[] keyContent =
                "7tabvCt19oMF5Quu4cAQetS6xlLFkjIbcY6330+cjlo=".getBytes(StandardCharsets.UTF_8);
        AdSelectionEncryptionKey key =
                AdSelectionEncryptionKey.builder()
                        .setKeyType(AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION)
                        .setKeyIdentifier("3480000000000000")
                        .setPublicKey(keyContent)
                        .build();

        ObliviousHttpKeyConfig keyConfig =
                mAuctionEncryptionKeyParser.getObliviousHttpKeyConfig(key);
        assertThat(keyConfig.keyId()).isEqualTo(52);
        assertThat(keyConfig.getPublicKey()).isEqualTo(keyContent);
        assertThat(keyConfig.kemId()).isEqualTo(0x0005);
        assertThat(keyConfig.kdfId()).isEqualTo(0x0002);
        assertThat(keyConfig.aeadId()).isEqualTo(0x0022);
    }

    private static class AuctionEncryptionKeyParserTestFlags implements Flags {
        AuctionEncryptionKeyParserTestFlags() {}

        @Override
        public long getFledgeAuctionServerEncryptionKeyMaxAgeSeconds() {
            return DEFAULT_MAX_AGE_SECONDS;
        }

        @Override
        public int getFledgeAuctionServerEncryptionAlgorithmKdfId() {
            return 0x0002;
        }

        @Override
        public int getFledgeAuctionServerEncryptionAlgorithmKemId() {
            return 0x0005;
        }

        @Override
        public int getFledgeAuctionServerEncryptionAlgorithmAeadId() {
            return 0x0022;
        }
    }
}
