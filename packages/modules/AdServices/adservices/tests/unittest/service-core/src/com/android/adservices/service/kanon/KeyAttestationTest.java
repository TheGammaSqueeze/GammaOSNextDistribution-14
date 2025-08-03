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

package com.android.adservices.service.kanon;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.spy;

import android.security.keystore.KeyProperties;

import com.android.adservices.common.AdServicesDeviceSupportedRule;
import com.android.adservices.common.KeyAttestationSupportedRule;
import com.android.adservices.common.SdkLevelSupportRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;

import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.CertificateException;

public final class KeyAttestationTest {
    private static final byte[] CHALLENGE =
            ("AHXUDhoSEFikqOefmo8xE7kGp/xjVMRDYBecBiHGxCN8rTv9W0Z4L/14d0OLB"
                            + "vC1VVzXBAnjgHoKLZzuJifTOaBJwGNIQ2ejnx3n6ayoRchDNCgpK29T+EAhBWzH")
                    .getBytes();

    private static final String ANDROID_KEY_STORE = "AndroidKeyStore";

    private static final String KEY_ALIAS = "PaKanonKeyAttestation";

    private KeyAttestation mKeyAttestation;

    private KeyStore mSpyKeyStore;

    private KeyPairGenerator mSpyKeyPairGenerator;

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastS();

    @Rule(order = 1)
    public final AdServicesDeviceSupportedRule deviceSupportRule =
            new AdServicesDeviceSupportedRule();

    @Rule(order = 2)
    public final KeyAttestationSupportedRule keyAttestationSupportedRule =
            new KeyAttestationSupportedRule();

    @Before
    public void setUp() throws Exception {
        mSpyKeyStore = spy(KeyStore.getInstance(ANDROID_KEY_STORE));
        mSpyKeyPairGenerator =
                spy(
                        KeyPairGenerator.getInstance(
                                KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEY_STORE));
        mKeyAttestation =
                new KeyAttestation(/* useStrongBox= */ false, mSpyKeyStore, mSpyKeyPairGenerator);
    }

    @After
    public void tearDown() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEY_STORE);
        keyStore.load(null);
        if (keyStore.containsAlias(KEY_ALIAS)) {
            keyStore.deleteEntry(KEY_ALIAS);
        }

        Mockito.reset(mSpyKeyStore, mSpyKeyPairGenerator);
    }

    @Test
    public void testGenerateAttestationRecord_success() throws Exception {
        KeyAttestationCertificateChainRecord record =
                mKeyAttestation.generateAttestationRecord(CHALLENGE);

        assertThat(record.encode().length).isGreaterThan(4);
    }

    @Test
    public void testGenerateAttestationRecord_nullKey_throwsException() {
        doReturn(null).when(mSpyKeyPairGenerator).generateKeyPair();

        assertThrows(
                IllegalStateException.class,
                () -> mKeyAttestation.generateAttestationRecord(CHALLENGE));
    }

    @Test
    public void testGenerateHybridKey_success() {
        KeyPair keyPair = mKeyAttestation.generateHybridKey(CHALLENGE, KEY_ALIAS);

        assertThat(keyPair).isNotNull();
        assertThat(keyPair.getPublic()).isNotNull();
        assertThat(keyPair.getPrivate()).isNotNull();
    }

    @Test
    public void testGenerateHybridKey_initFailure() throws Exception {
        doThrow(new InvalidAlgorithmParameterException("Invalid Parameters"))
                .when(mSpyKeyPairGenerator)
                .initialize(any());

        assertThrows(
                IllegalStateException.class,
                () -> mKeyAttestation.generateHybridKey(CHALLENGE, KEY_ALIAS));
    }

    @Test
    public void testGetAttestationRecordFromKeyAlias_success() throws Exception {
        KeyPair unused = mKeyAttestation.generateHybridKey(CHALLENGE, KEY_ALIAS);

        KeyAttestationCertificateChainRecord record =
                mKeyAttestation.getAttestationRecordFromKeyAlias(KEY_ALIAS);

        assertThat(record.encode().length).isGreaterThan(4);
    }

    @Test
    public void testGetAttestationRecordFromKeyAlias_certFailure() throws Exception {
        doThrow(new CertificateException("Cert Exception")).when(mSpyKeyStore).load(any());

        KeyAttestationCertificateChainRecord record =
                mKeyAttestation.getAttestationRecordFromKeyAlias(KEY_ALIAS);

        assertThat(record.encode().length).isEqualTo(0);
    }

    @Test
    public void testGetAttestationRecordFromKeyAlias_keyStoreFailure() throws Exception {
        doThrow(new KeyStoreException("Key Store Exception"))
                .when(mSpyKeyStore)
                .getCertificateChain(any());

        KeyAttestationCertificateChainRecord record =
                mKeyAttestation.getAttestationRecordFromKeyAlias(KEY_ALIAS);

        assertThat(record.encode().length).isEqualTo(0);
    }

    @Test
    public void testGetAttestationRecordFromKeyAlias_keyStoreReturnsNullChain_Failure()
            throws Exception {
        doReturn(null).when(mSpyKeyStore).getCertificateChain(any());

        assertThrows(
                IllegalStateException.class,
                () -> mKeyAttestation.getAttestationRecordFromKeyAlias(KEY_ALIAS));
    }
}
