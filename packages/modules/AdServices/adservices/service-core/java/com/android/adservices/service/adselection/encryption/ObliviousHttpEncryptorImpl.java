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

import static com.android.adservices.service.adselection.encryption.AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION;

import android.net.Uri;

import androidx.annotation.Nullable;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.adselection.EncryptionContextDao;
import com.android.adservices.ohttp.ObliviousHttpClient;
import com.android.adservices.ohttp.ObliviousHttpKeyConfig;
import com.android.adservices.ohttp.ObliviousHttpRequest;
import com.android.adservices.ohttp.ObliviousHttpRequestContext;
import com.android.adservices.ohttp.algorithms.UnsupportedHpkeAlgorithmException;
import com.android.adservices.service.profiling.Tracing;

import com.google.common.util.concurrent.FluentFuture;

import java.io.IOException;
import java.security.spec.InvalidKeySpecException;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

/** Class to encrypt and decrypt bytes using OHTTP. */
public class ObliviousHttpEncryptorImpl implements ObliviousHttpEncryptor {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    private ProtectedServersEncryptionConfigManagerBase mEncryptionConfigManager;
    private ObliviousHttpRequestContextMarshaller mObliviousHttpRequestContextMarshaller;

    private ExecutorService mLightweightExecutor;

    public ObliviousHttpEncryptorImpl(
            ProtectedServersEncryptionConfigManagerBase encryptionConfigManager,
            EncryptionContextDao encryptionContextDao,
            ExecutorService lightweightExecutor) {
        Objects.requireNonNull(encryptionConfigManager);
        Objects.requireNonNull(encryptionContextDao);
        Objects.requireNonNull(lightweightExecutor);

        mEncryptionConfigManager = encryptionConfigManager;
        mObliviousHttpRequestContextMarshaller =
                new ObliviousHttpRequestContextMarshaller(encryptionContextDao);
        mLightweightExecutor = lightweightExecutor;
    }

    /** Encrypts the given byte and stores the encryption context data keyed by given contextId */
    @Override
    public FluentFuture<byte[]> encryptBytes(
            byte[] plainText, long contextId, long keyFetchTimeoutMs, @Nullable Uri coordinator) {
        int traceCookie = Tracing.beginAsyncSection(Tracing.OHTTP_ENCRYPT_BYTES);
        return mEncryptionConfigManager
                .getLatestOhttpKeyConfigOfType(AUCTION, keyFetchTimeoutMs, coordinator)
                .transform(
                        key -> {
                            byte[] serializedRequest =
                                    createAndSerializeRequest(key, plainText, contextId);
                            Tracing.endAsyncSection(Tracing.OHTTP_ENCRYPT_BYTES, traceCookie);
                            return serializedRequest;
                        },
                        mLightweightExecutor);
    }

    /**
     * Decrypts the given bytes using context stored in the DB keyed by the given storedContextId.
     */
    @Override
    public byte[] decryptBytes(byte[] encryptedBytes, long storedContextId) {
        Objects.requireNonNull(encryptedBytes);
        try {
            ObliviousHttpRequestContext context =
                    mObliviousHttpRequestContextMarshaller.getAuctionOblivioushttpRequestContext(
                            storedContextId);
            ObliviousHttpClient client = ObliviousHttpClient.create(context.keyConfig());

            return client.decryptObliviousHttpResponse(encryptedBytes, context);
        } catch (InvalidKeySpecException | UnsupportedHpkeAlgorithmException | IOException e) {
            sLogger.e("Unexpected error during decryption");
            throw new RuntimeException(e);
        }
    }

    private byte[] createAndSerializeRequest(
            ObliviousHttpKeyConfig config, byte[] plainText, long contextId) {
        int traceCookie = Tracing.beginAsyncSection(Tracing.CREATE_AND_SERIALIZE_REQUEST);
        try {
            Objects.requireNonNull(config);
            ObliviousHttpClient client = ObliviousHttpClient.create(config);

            Objects.requireNonNull(client);
            ObliviousHttpRequest request = client.createObliviousHttpRequest(plainText);

            Objects.requireNonNull(request);
            mObliviousHttpRequestContextMarshaller.insertAuctionEncryptionContext(
                    contextId, request.requestContext());

            byte[] serializedRequest = request.serialize();
            Tracing.endAsyncSection(Tracing.CREATE_AND_SERIALIZE_REQUEST, traceCookie);
            return serializedRequest;
        } catch (UnsupportedHpkeAlgorithmException e) {
            sLogger.e("Unexpected error during Oblivious Http Client creation");
            Tracing.endAsyncSection(Tracing.CREATE_AND_SERIALIZE_REQUEST, traceCookie);
            throw new RuntimeException(e);
        } catch (IOException e) {
            sLogger.e("Unexpected error during Oblivious HTTP Request creation");
            Tracing.endAsyncSection(Tracing.CREATE_AND_SERIALIZE_REQUEST, traceCookie);
            throw new RuntimeException(e);
        }
    }
}
