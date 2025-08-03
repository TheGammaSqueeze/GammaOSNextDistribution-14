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

package com.android.federatedcompute.services.security;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;

import android.security.keystore.KeyProperties;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.CertificateException;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public final class KeyAttestationTest {
    private static final byte[] CHALLENGE =
            ("AHXUDhoSEFikqOefmo8xE7kGp/xjVMRDYBecBiHGxCN8rTv9W0Z4L/14d0OLB"
                            + "vC1VVzXBAnjgHoKLZzuJifTOaBJwGNIQ2ejnx3n6ayoRchDNCgpK29T+EAhBWzH")
                    .getBytes();

    private static final String CALLING_APP = "sampleApp1";

    private static final String ANDROID_KEY_STORE = "AndroidKeyStore";

    private static final String KEY_ALIAS = CALLING_APP + "-ODPKeyAttestation";

    private KeyAttestation mKeyAttestation;

    private KeyStore mSpyKeyStore;

    private KeyPairGenerator mSpyKeyPairGenerator;

    @Before
    public void setUp() throws Exception {
        mSpyKeyStore = spy(KeyStore.getInstance(ANDROID_KEY_STORE));
        mSpyKeyPairGenerator = spy(KeyPairGenerator
                .getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEY_STORE));
        mKeyAttestation = KeyAttestation.getInstanceForTest(
                ApplicationProvider.getApplicationContext(), new TestInjector());
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
    public void testGenerateAttestationRecord_success() {
        List<String> record = mKeyAttestation.generateAttestationRecord(CHALLENGE, CALLING_APP);

        assertThat(record.size()).isGreaterThan(0);
    }

    @Test
    public void testGenerateAttestationRecord_nullKey() {
        doReturn(null).when(mSpyKeyPairGenerator).generateKeyPair();

        List<String> record = mKeyAttestation.generateAttestationRecord(CHALLENGE, CALLING_APP);

        assertThat(record.size()).isEqualTo(0);
    }

    @Test
    public void testGenerateHybridKey_success() {
        String keyAlias = CALLING_APP + "-ODPKeyAttestation";

        KeyPair keyPair = mKeyAttestation.generateHybridKey(CHALLENGE, keyAlias);

        assertThat(keyPair).isNotNull();
        assertThat(keyPair.getPublic()).isNotNull();
        assertThat(keyPair.getPrivate()).isNotNull();
    }


    @Test
    public void testGenerateHybridKey_initFailure() throws Exception {
        doThrow(new InvalidAlgorithmParameterException("Invalid Parameters"))
                .when(mSpyKeyPairGenerator).initialize(any());

        KeyPair keyPair = mKeyAttestation.generateHybridKey(CHALLENGE, KEY_ALIAS);

        assertThat(keyPair).isNull();
    }


    @Test
    public void testGetAttestationRecordFromKeyAlias_noKey() {
        String keyAlias2 = CALLING_APP + "-ODPKeyAttestation2";

        KeyPair unused = mKeyAttestation.generateHybridKey(CHALLENGE, KEY_ALIAS);
        List<String> record = mKeyAttestation.getAttestationRecordFromKeyAlias(keyAlias2);

        assertThat(record.size()).isEqualTo(0);
    }

    @Test
    public void testGetAttestationRecordFromKeyAlias_success() {
        KeyPair unused = mKeyAttestation.generateHybridKey(CHALLENGE, KEY_ALIAS);

        List<String> record = mKeyAttestation.getAttestationRecordFromKeyAlias(KEY_ALIAS);

        assertThat(record.size()).isGreaterThan(0);
    }

    @Test
    public void testGetAttestationRecordFromKeyAlias_certFailure() throws Exception {
        doThrow(new CertificateException("Cert Exception"))
                .when(mSpyKeyStore).load(any());

        List<String> record = mKeyAttestation.getAttestationRecordFromKeyAlias(KEY_ALIAS);

        assertThat(record.size()).isEqualTo(0);
    }

    @Test
    public void testGetAttestationRecordFromKeyAlias_keyStoreFailure() throws Exception {
        doThrow(new KeyStoreException("Key Store Exception"))
                .when(mSpyKeyStore).getCertificateChain(any());

        List<String> record = mKeyAttestation.getAttestationRecordFromKeyAlias(KEY_ALIAS);

        assertThat(record.size()).isEqualTo(0);
    }

    class TestInjector extends KeyAttestation.Injector {
        @Override
        KeyStore getKeyStore() {
            return mSpyKeyStore;
        }

        @Override
        KeyPairGenerator getKeyPairGenerator() {
            return mSpyKeyPairGenerator;
        }
    }
}
