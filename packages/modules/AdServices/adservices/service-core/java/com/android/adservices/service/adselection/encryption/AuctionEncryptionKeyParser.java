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

import androidx.annotation.NonNull;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.adselection.DBEncryptionKey;
import com.android.adservices.data.adselection.EncryptionKeyConstants;
import com.android.adservices.ohttp.ObliviousHttpKeyConfig;
import com.android.adservices.service.Flags;
import com.android.adservices.service.common.httpclient.AdServicesHttpClientResponse;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Class to parse AUCTION encryption key. */
public class AuctionEncryptionKeyParser implements EncryptionKeyParser {
    private static final String AUCTION_KEY_FETCH_RESPONSE_HEADER_CACHE_CONTROL_LABEL =
            "cache-control";
    private static final String AUCTION_KEY_FETCH_RESPONSE_HEADER_AGE_LABEL = "age";
    private static final String AUCTION_KEY_FETCH_RESPONSE_HEADER_CACHE_CONTROL_MAX_AGE_LABEL =
            "max-age=";

    private static final int HEXADECIMAL_RADIX = 16;
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    private final Flags mFlags;

    public AuctionEncryptionKeyParser(Flags flags) {
        mFlags = flags;
    }

    @Override
    public AdSelectionEncryptionKey parseDbEncryptionKey(DBEncryptionKey dbEncryptionKey) {
        Preconditions.checkNotNull(dbEncryptionKey, "DBEncryption key is null.");
        Preconditions.checkArgument(
                dbEncryptionKey.getEncryptionKeyType()
                        == EncryptionKeyConstants.EncryptionKeyType.ENCRYPTION_KEY_TYPE_AUCTION,
                "DBEncryptionKey is not of AUCTION type.");

        return AdSelectionEncryptionKey.builder()
                .setKeyType(AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION)
                .setKeyIdentifier(dbEncryptionKey.getKeyIdentifier())
                .setPublicKey(
                        // Auction public key is base 64 encoded.
                        Base64.getDecoder()
                                .decode(
                                        // We serialize the string public key into UTF_8 bytes
                                        // because the AdServicesHttpClient parses the incoming
                                        // bytes in the payload as a UTF_8 string.
                                        dbEncryptionKey
                                                .getPublicKey()
                                                .getBytes(StandardCharsets.UTF_8)))
                .build();
    }

    @Override
    public List<DBEncryptionKey> getDbEncryptionKeys(AdServicesHttpClientResponse response) {
        String responseBody = response.getResponseBody();
        ImmutableMap<String, List<String>> headers = response.getResponseHeaders();
        long maxAge = getMaxAgeInSeconds(headers);
        if (maxAge <= 0) {
            maxAge = mFlags.getFledgeAuctionServerEncryptionKeyMaxAgeSeconds();
        }
        try {
            JSONObject responseObj = new JSONObject(responseBody);
            JSONArray keys = responseObj.getJSONArray(AuctionKeyResponseContract.KEYS);
            ImmutableList.Builder<DBEncryptionKey> dbKeys = ImmutableList.builder();
            for (int i = 0; i < keys.length(); i++) {
                JSONObject keyObj = keys.getJSONObject(i);
                DBEncryptionKey.Builder keyBuilder = DBEncryptionKey.builder();
                keyBuilder.setEncryptionKeyType(
                        EncryptionKeyConstants.EncryptionKeyType.ENCRYPTION_KEY_TYPE_AUCTION);
                keyBuilder.setKeyIdentifier(
                        keyObj.getString(AuctionKeyResponseContract.KEYS_KEY_ID));
                keyBuilder.setPublicKey(
                        keyObj.getString(AuctionKeyResponseContract.KEYS_PUBLIC_KEY));
                // TODO(b/284445328): Set creation Instant as the instant key was fetched.
                // This would allow accurate computation of expiry instant as fetchInstant + maxage.
                keyBuilder.setExpiryTtlSeconds(maxAge);
                dbKeys.add(keyBuilder.build());
            }
            return dbKeys.build();
        } catch (JSONException e) {
            sLogger.d("Invalid JSON.");
            return ImmutableList.of();
        }
    }

    @Override
    public ObliviousHttpKeyConfig getObliviousHttpKeyConfig(AdSelectionEncryptionKey key)
            throws InvalidKeySpecException {
        Preconditions.checkNotNull(key, "AdSelectionEncryptionKey is null.");
        Preconditions.checkArgument(
                key.keyType() == AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION,
                "AdSelectionEncryptionKey not of AUCTION key type " + key);

        return ObliviousHttpKeyConfig.builder()
                .setKeyId(getOhttpKeyId(key.keyIdentifier()))
                // Encryption algorithms used by the client are decided using flags for Auction
                // B&A encryption because Auction KMS does not return encryption algorithms in the
                // key. If auction server does not accept the flag configured algorithms then server
                // will throw an error.
                .setKdfId(mFlags.getFledgeAuctionServerEncryptionAlgorithmKdfId())
                .setKemId(mFlags.getFledgeAuctionServerEncryptionAlgorithmKemId())
                .setAeadId(mFlags.getFledgeAuctionServerEncryptionAlgorithmAeadId())
                .setPublicKey(key.publicKey())
                .build();
    }

    private static int getOhttpKeyId(String keyId) throws InvalidKeySpecException {
        if (keyId.length() < 2) {
            throw new InvalidKeySpecException("The keyId length needs to be at least two");
        }

        // Take the first character of the hex key ID. Left shift it by 4. OR it with second
        // character of the hex key id
        int leftmost = Character.digit(keyId.charAt(0), HEXADECIMAL_RADIX);
        int secondLeftmost = Character.digit(keyId.charAt(1), HEXADECIMAL_RADIX);
        if (leftmost == -1 || secondLeftmost == -1) {
            throw new InvalidKeySpecException("The first two characters in keyID should be hex");
        }

        return (leftmost << 4) | secondLeftmost;
    }

    private static long getMaxAgeInSeconds(@NonNull Map<String, List<String>> headers) {
        Objects.requireNonNull(headers);
        String cacheControl = null;
        int cachedAge = 0;
        int remainingHeaders = 2;
        for (String key : headers.keySet()) {
            if (key != null) {
                if (key.equalsIgnoreCase(AUCTION_KEY_FETCH_RESPONSE_HEADER_CACHE_CONTROL_LABEL)) {
                    List<String> field = headers.get(key);
                    if (field != null && field.size() > 0) {
                        cacheControl = field.get(0).toLowerCase(Locale.ENGLISH);
                        remainingHeaders -= 1;
                    }
                } else if (key.equalsIgnoreCase(AUCTION_KEY_FETCH_RESPONSE_HEADER_AGE_LABEL)) {
                    List<String> field = headers.get(key);
                    if (field != null && field.size() > 0) {
                        try {
                            cachedAge = Integer.parseInt(field.get(0));
                        } catch (NumberFormatException e) {
                            sLogger.e(e, "Error parsing age header");
                        }
                        remainingHeaders -= 1;
                    }
                }
            }
            if (remainingHeaders == 0) {
                break;
            }
        }
        if (cacheControl == null) {
            sLogger.d("Cache-Control header or value is missing");
            return 0;
        }
        String[] tokens = cacheControl.split(",", 0);
        long maxAge = 0;
        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i].trim();
            if (token.startsWith(AUCTION_KEY_FETCH_RESPONSE_HEADER_CACHE_CONTROL_MAX_AGE_LABEL)) {
                try {
                    maxAge = Long.parseLong(token.substring(8));
                } catch (NumberFormatException e) {
                    sLogger.d(e, "Failed to parse max-age value");
                    return 0;
                }
            }
        }
        if (maxAge == 0) {
            sLogger.d("max-age directive is missing");
            return 0;
        }
        return maxAge - cachedAge;
    }

    private interface AuctionKeyResponseContract {
        String KEYS = "keys";
        String KEYS_PUBLIC_KEY = "key";
        String KEYS_KEY_ID = "id";
    }
}
