/*
 * Copyright (C) 2024 The Android Open Source Project
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

import android.net.Uri;

import androidx.annotation.Nullable;

import com.android.adservices.LoggerFactory;
import com.android.adservices.ohttp.ObliviousHttpClient;
import com.android.adservices.ohttp.ObliviousHttpKeyConfig;
import com.android.adservices.ohttp.ObliviousHttpRequest;
import com.android.adservices.ohttp.ObliviousHttpRequestContext;
import com.android.adservices.ohttp.algorithms.UnsupportedHpkeAlgorithmException;

import com.google.common.util.concurrent.FluentFuture;

import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

public class KAnonObliviousHttpEncryptorImpl implements ObliviousHttpEncryptor {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getFledgeLogger();
    private AdSelectionEncryptionKeyManager mEncryptionKeyManager;
    private ExecutorService mLightweightExecutor;
    private ObliviousHttpRequestContext mObliviousRequestContext;

    public KAnonObliviousHttpEncryptorImpl(
            AdSelectionEncryptionKeyManager encryptionKeyManager,
            ExecutorService lightweightExecutor) {
        Objects.requireNonNull(encryptionKeyManager);
        Objects.requireNonNull(lightweightExecutor);

        mEncryptionKeyManager = encryptionKeyManager;
        mLightweightExecutor = lightweightExecutor;
    }

    /**
     * Encrypts the given bytes. This method fetches the required key to encrypt the bytes and also
     * stores the created {@link ObliviousHttpRequestContext} in the memory which is later used
     * while decrypting the bytes in {@link KAnonObliviousHttpEncryptorImpl#decryptBytes(byte[],
     * long)} method.
     */
    @Override
    public FluentFuture<byte[]> encryptBytes(
            byte[] plainText,
            long contextId,
            long keyFetchTimeoutMs,
            @Nullable Uri unusedCoordinatorUri) {
        return mEncryptionKeyManager
                .getLatestOhttpKeyConfigOfType(
                        AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.JOIN,
                        keyFetchTimeoutMs,
                        null)
                .transform(key -> createAndSerializeRequest(key, plainText), mLightweightExecutor);
    }

    /** Decrypt given bytes. */
    @Override
    public byte[] decryptBytes(byte[] encryptedBytes, long unusedContext) {
        Objects.requireNonNull(encryptedBytes);
        Objects.requireNonNull(mObliviousRequestContext);
        try {
            ObliviousHttpClient client =
                    ObliviousHttpClient.create(mObliviousRequestContext.keyConfig());

            return client.decryptObliviousHttpResponse(encryptedBytes, mObliviousRequestContext);
        } catch (UnsupportedHpkeAlgorithmException | IOException e) {
            sLogger.e("Unexpected error during decryption");
            throw new RuntimeException(e);
        }
    }

    private byte[] createAndSerializeRequest(ObliviousHttpKeyConfig config, byte[] plainText) {
        try {
            Objects.requireNonNull(config);
            ObliviousHttpClient client = ObliviousHttpClient.create(config);

            Objects.requireNonNull(client);
            ObliviousHttpRequest request = client.createObliviousHttpRequest(plainText);

            Objects.requireNonNull(request);
            // we will need this context later when we try to call the decrypt method.
            mObliviousRequestContext = request.requestContext();

            return request.serialize();
        } catch (UnsupportedHpkeAlgorithmException e) {
            sLogger.e("Unexpected error during Oblivious Http Client creation");
            throw new RuntimeException(e);
        } catch (IOException e) {
            sLogger.e("Unexpected error during Oblivious HTTP Request creation");
            throw new RuntimeException(e);
        }
    }
}
