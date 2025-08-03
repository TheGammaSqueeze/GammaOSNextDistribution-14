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

import static com.android.adservices.service.adselection.encryption.AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.JOIN;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateFuture;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;

import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.ohttp.ObliviousHttpKeyConfig;

import com.google.common.io.BaseEncoding;
import com.google.common.util.concurrent.FluentFuture;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.nio.charset.StandardCharsets;
import java.security.spec.InvalidKeySpecException;
import java.util.concurrent.ExecutorService;

public class KAnonObliviousHttpEncryptorImplTest {
    private static final String SERVER_PUBLIC_KEY =
            "6d21cfe09fbea5122f9ebc2eb2a69fcc4f06408cd54aac934f012e76fcdcef62";
    private static final Long UNUSED_CONTEXT_ID = 1L;
    private static final Long KEY_FETCH_TIMEOUT_MS = 1000L;
    @Rule public final MockitoRule mockito = MockitoJUnit.rule();

    @Mock AdSelectionEncryptionKeyManager mEncryptionKeyManagerMock;

    private KAnonObliviousHttpEncryptorImpl mKAnonObliviousHttpEncryptor;
    private ExecutorService mLightweightExecutor;

    @Before
    public void setUp() {
        mLightweightExecutor = AdServicesExecutors.getLightWeightExecutor();
        mKAnonObliviousHttpEncryptor =
                new KAnonObliviousHttpEncryptorImpl(
                        mEncryptionKeyManagerMock, mLightweightExecutor);
    }

    @Test
    public void test_encryptBytes_invalidPlainText() {
        assertThrows(
                NullPointerException.class,
                () ->
                        mKAnonObliviousHttpEncryptor.encryptBytes(
                                null, UNUSED_CONTEXT_ID, KEY_FETCH_TIMEOUT_MS, null));
    }

    @Test
    public void test_encryptBytes_success() throws Exception {
        when(mEncryptionKeyManagerMock.getLatestOhttpKeyConfigOfType(
                        JOIN, KEY_FETCH_TIMEOUT_MS, null))
                .thenReturn(FluentFuture.from(immediateFuture(getKeyConfig(4))));

        String plainText = "test request 1";
        byte[] plainTextBytes = plainText.getBytes(StandardCharsets.US_ASCII);

        assertThat(
                        BaseEncoding.base16()
                                .lowerCase()
                                .encode(
                                        mKAnonObliviousHttpEncryptor
                                                .encryptBytes(
                                                        plainTextBytes,
                                                        UNUSED_CONTEXT_ID,
                                                        KEY_FETCH_TIMEOUT_MS,
                                                        null)
                                                .get()))
                // Only the Ohttp header containing key ID and algorithm IDs is same across
                // multiple test runs since, a random seed is used to generate rest of the
                // cipher text.
                .startsWith("04002000010002");
    }

    @Test
    public void test_decryptBytes_invalidEncryptedBytes() {
        assertThrows(
                NullPointerException.class,
                () -> mKAnonObliviousHttpEncryptor.decryptBytes(null, UNUSED_CONTEXT_ID));
    }

    /**
     * Test that an exception is thrown if encryption context is absent. This happens when decrypt
     * is called without calling encrypt.
     */
    @Test
    public void test_decryptBytes_noEncryptionContext_throwsException() {
        String responseCipherText =
                "6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6cf623"
                        + "a32dba30cdf1a011543bdd7e95ace60be30b029574dc3be9abee478df9";
        byte[] responseCipherTextBytes =
                BaseEncoding.base16().lowerCase().decode(responseCipherText);

        assertThrows(
                NullPointerException.class,
                () ->
                        mKAnonObliviousHttpEncryptor.decryptBytes(
                                responseCipherTextBytes, UNUSED_CONTEXT_ID));
    }

    @Test
    public void test_decryptBytes_success() throws Exception {
        when(mEncryptionKeyManagerMock.getLatestOhttpKeyConfigOfType(
                        JOIN, KEY_FETCH_TIMEOUT_MS, null))
                .thenReturn(FluentFuture.from(immediateFuture(getKeyConfig(4))));

        String plainText = "test request 1";
        byte[] plainTextBytes = plainText.getBytes(StandardCharsets.US_ASCII);

        byte[] encryptedBytes =
                mKAnonObliviousHttpEncryptor
                        .encryptBytes(plainTextBytes, UNUSED_CONTEXT_ID, KEY_FETCH_TIMEOUT_MS, null)
                        .get();

        assertThat(encryptedBytes).isNotNull();
        assertThat(encryptedBytes).isNotEmpty();

        // TODO(b/324206646): Implement kanon oblivious encryptor to test this method.
        mKAnonObliviousHttpEncryptor.decryptBytes(encryptedBytes, UNUSED_CONTEXT_ID);
    }

    private ObliviousHttpKeyConfig getKeyConfig(int keyIdentifier) throws InvalidKeySpecException {
        byte[] keyId = new byte[1];
        keyId[0] = (byte) (keyIdentifier & 0xFF);
        String keyConfigHex =
                BaseEncoding.base16().lowerCase().encode(keyId)
                        + "0020"
                        + SERVER_PUBLIC_KEY
                        + "000400010002";
        return ObliviousHttpKeyConfig.fromSerializedKeyConfig(
                BaseEncoding.base16().lowerCase().decode(keyConfigHex));
    }
}
