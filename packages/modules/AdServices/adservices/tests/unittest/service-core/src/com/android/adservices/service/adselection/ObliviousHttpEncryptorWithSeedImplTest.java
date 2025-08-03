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

package com.android.adservices.service.adselection;

import static com.android.adservices.service.adselection.encryption.AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.AUCTION;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.util.concurrent.Futures.immediateFuture;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;

import android.adservices.adselection.ObliviousHttpEncryptorWithSeedImpl;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.common.SdkLevelSupportRule;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.data.adselection.AdSelectionServerDatabase;
import com.android.adservices.data.adselection.EncryptionContextDao;
import com.android.adservices.ohttp.ObliviousHttpKeyConfig;
import com.android.adservices.service.adselection.encryption.AdSelectionEncryptionKeyManager;
import com.android.adservices.service.adselection.encryption.ObliviousHttpEncryptor;

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

public class ObliviousHttpEncryptorWithSeedImplTest {
    private static final String SERVER_PUBLIC_KEY =
            "6d21cfe09fbea5122f9ebc2eb2a69fcc4f06408cd54aac934f012e76fcdcef62";
    @Rule public final MockitoRule mockito = MockitoJUnit.rule();

    @Mock AdSelectionEncryptionKeyManager mEncryptionKeyManagerMock;
    private ExecutorService mLightweightExecutor;
    private EncryptionContextDao mEncryptionContextDao;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Before
    public void setUp() {
        mLightweightExecutor = AdServicesExecutors.getLightWeightExecutor();
        mEncryptionContextDao =
                Room.inMemoryDatabaseBuilder(
                                ApplicationProvider.getApplicationContext(),
                                AdSelectionServerDatabase.class)
                        .build()
                        .encryptionContextDao();
    }

    @Test
    public void test_encryptBytes_success() throws Exception {
        when(mEncryptionKeyManagerMock.getLatestOhttpKeyConfigOfType(AUCTION, 1000L, null))
                .thenReturn(FluentFuture.from(immediateFuture(getKeyConfig(4))));
        String seed = "wwwwwwwwwwwwwwwwwwwwwwwwwwwwwwww";
        byte[] seedBytes = seed.getBytes(StandardCharsets.US_ASCII);
        ObliviousHttpEncryptor encryptor =
                new ObliviousHttpEncryptorWithSeedImpl(
                        mEncryptionKeyManagerMock,
                        mEncryptionContextDao,
                        seedBytes,
                        mLightweightExecutor);

        String plainText = "test request 1";
        byte[] plainTextBytes = plainText.getBytes(StandardCharsets.US_ASCII);

        String expectedCipherText =
                "040020000100021cf579aba45a10ba1d1ef06d91fca2aa9ed0a1150515653155405d"
                        + "0b18cb9a672ef2da3b97acee493624b9959f0fc6df008a6f0701c923c5a60ed0ed2c34";

        assertThat(
                        BaseEncoding.base16()
                                .lowerCase()
                                .encode(
                                        encryptor
                                                .encryptBytes(plainTextBytes, 1L, 1000L, null)
                                                .get()))
                // Only the Ohttp header containing key ID and algorithm IDs is same across
                // multiple test runs since, a random seed is used to generate rest of the
                // cipher text.
                .isEqualTo(expectedCipherText);
    }

    @Test
    public void test_decryptBytes_invalidEncryptedBytes() {
        String seed = "wwwwwwwwwwwwwwwwwwwwwwwwwwwwwwww";
        byte[] seedBytes = seed.getBytes(StandardCharsets.US_ASCII);
        ObliviousHttpEncryptor encryptor =
                new ObliviousHttpEncryptorWithSeedImpl(
                        mEncryptionKeyManagerMock,
                        mEncryptionContextDao,
                        seedBytes,
                        mLightweightExecutor);
        assertThrows(NullPointerException.class, () -> encryptor.decryptBytes(null, 1L));
    }

    @Test
    public void test_decryptBytes_success() throws Exception {
        when(mEncryptionKeyManagerMock.getLatestOhttpKeyConfigOfType(AUCTION, 1000, null))
                .thenReturn(FluentFuture.from(immediateFuture(getKeyConfig(4))));

        String seed = "wwwwwwwwwwwwwwwwwwwwwwwwwwwwwwww";
        byte[] seedBytes = seed.getBytes(StandardCharsets.US_ASCII);
        ObliviousHttpEncryptor encryptor =
                new ObliviousHttpEncryptorWithSeedImpl(
                        mEncryptionKeyManagerMock,
                        mEncryptionContextDao,
                        seedBytes,
                        mLightweightExecutor);

        String plainText = "test request 1";
        byte[] plainTextBytes = plainText.getBytes(StandardCharsets.US_ASCII);

        byte[] encryptedBytes = encryptor.encryptBytes(plainTextBytes, 1L, 1000L, null).get();

        assertThat(encryptedBytes).isNotNull();
        assertThat(encryptedBytes).isNotEmpty();
        assertThat(mEncryptionContextDao.getEncryptionContext(1L, AUCTION)).isNotNull();

        String responseCipherText =
                "6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6c6cf623"
                        + "a32dba30cdf1a011543bdd7e95ace60be30b029574dc3be9abee478df9";
        byte[] responseCipherTextBytes =
                BaseEncoding.base16().lowerCase().decode(responseCipherText);

        String expectedPlainText = "test response 1";
        assertThat(
                        new String(
                                encryptor.decryptBytes(responseCipherTextBytes, 1L),
                                StandardCharsets.UTF_8))
                .isEqualTo(expectedPlainText);
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
