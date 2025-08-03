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

import com.android.adservices.data.adselection.DBEncryptionKey;
import com.android.adservices.data.adselection.EncryptionKeyConstants;
import com.android.adservices.ohttp.ObliviousHttpKeyConfig;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.httpclient.AdServicesHttpClientResponse;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.io.BaseEncoding;

import java.nio.charset.StandardCharsets;
import java.security.spec.InvalidKeySpecException;
import java.util.List;

/** Class to parse JOIN encryption key. */
public class JoinEncryptionKeyParser implements EncryptionKeyParser {

    private static final String CONTENT_TYPE_HEADER_LABEL = "Content-Type";
    private static final String CONTENT_TYPE = "application/ohttp-keys";

    // As per OHTTP key format
    // https://www.ietf.org/archive/id/draft-ietf-ohai-ohttp-02.html#section-3.1
    private static final int JOIN_KEY_ID_SIZE_IN_BYTES = 1;
    private final Flags mFlags;

    public JoinEncryptionKeyParser(Flags flags) {
        mFlags = flags;
    }

    @Override
    public AdSelectionEncryptionKey parseDbEncryptionKey(DBEncryptionKey dbEncryptionKey) {
        Preconditions.checkNotNull(dbEncryptionKey, "DBEncryption key is null.");
        Preconditions.checkArgument(
                dbEncryptionKey.getEncryptionKeyType()
                        == EncryptionKeyConstants.EncryptionKeyType.ENCRYPTION_KEY_TYPE_JOIN,
                "DBEncryptionKey is not of JOIN type.");

        return AdSelectionEncryptionKey.builder()
                .setKeyIdentifier(dbEncryptionKey.getKeyIdentifier())
                .setKeyType(AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.JOIN)
                .setPublicKey(
                        BaseEncoding.base16().lowerCase().decode(dbEncryptionKey.getPublicKey()))
                .build();
    }

    @Override
    public List<DBEncryptionKey> getDbEncryptionKeys(AdServicesHttpClientResponse response) {
        Preconditions.checkArgument(
                response.getResponseBody() != null && !response.getResponseBody().isEmpty(),
                "response body is null or empty.");
        Preconditions.checkArgument(
                response.getResponseHeaders().containsKey(CONTENT_TYPE_HEADER_LABEL),
                "content type not specified.");
        List<String> contentTypeValues =
                response.getResponseHeaders().get(CONTENT_TYPE_HEADER_LABEL);

        Preconditions.checkArgument(
                contentTypeValues.size() == 1 && contentTypeValues.get(0).equals(CONTENT_TYPE),
                "ohttp-keys not specified in content type.");

        DBEncryptionKey.Builder keyBuilder = DBEncryptionKey.builder();

        byte[] keyInBytes = BaseEncoding.base64().decode(response.getResponseBody());
        // As per OHTTP format, the first byte in the response bytes is the key identifier.
        // https://www.ietf.org/archive/id/draft-ietf-ohai-ohttp-02.html#section-3.1
        byte[] keyId = new byte[JOIN_KEY_ID_SIZE_IN_BYTES];
        System.arraycopy(keyInBytes, 0, keyId, 0, JOIN_KEY_ID_SIZE_IN_BYTES);

        keyBuilder
                .setEncryptionKeyType(
                        EncryptionKeyConstants.EncryptionKeyType.ENCRYPTION_KEY_TYPE_JOIN)
                .setPublicKey(BaseEncoding.base16().lowerCase().encode(keyInBytes))
                .setKeyIdentifier(new String(keyId, StandardCharsets.UTF_8))
                .setExpiryTtlSeconds(mFlags.getFledgeAuctionServerEncryptionKeyMaxAgeSeconds())
                .build();

        // TODO(b/286839408): Validate that the built key can be parsed into ObliviousHttpKeyConfig.
        return ImmutableList.of(keyBuilder.build());
    }

    @Override
    public ObliviousHttpKeyConfig getObliviousHttpKeyConfig(AdSelectionEncryptionKey key)
            throws InvalidKeySpecException {
        Preconditions.checkNotNull(key, "AdSelectionEncryptionKey is null.");
        Preconditions.checkArgument(
                key.keyType() == AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.JOIN,
                "AdSelectionEncryptionKey is not of keyType JOIN - " + key);

        return ObliviousHttpKeyConfig.fromSerializedKeyConfig(key.publicKey());
    }
}
