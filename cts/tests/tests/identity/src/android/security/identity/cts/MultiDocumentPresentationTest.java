/*
 * Copyright 2019 The Android Open Source Project
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

package android.security.identity.cts;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNull;
import static org.junit.Assume.assumeTrue;

import android.content.Context;

import android.hardware.biometrics.CryptoObject;
import android.security.identity.AccessControlProfile;
import android.security.identity.AccessControlProfileId;
import android.security.identity.PersonalizationData;
import android.security.identity.IdentityCredential;
import android.security.identity.IdentityCredentialException;
import android.security.identity.IdentityCredentialStore;
import android.security.identity.InvalidReaderSignatureException;
import android.security.identity.ResultData;
import android.security.identity.WritableIdentityCredential;
import android.security.identity.PresentationSession;
import android.security.identity.CredentialDataRequest;
import android.security.identity.CredentialDataResult;
import com.android.security.identity.internal.Util;
import androidx.test.InstrumentationRegistry;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import co.nstant.in.cbor.CborException;

import javax.crypto.SecretKey;

public class MultiDocumentPresentationTest {
    private static final String TAG = "MultiDocumentPresentationTest";

    int[] getAuthKeyUsageCount(IdentityCredentialStore store, String credentialName)
        throws Exception {
        IdentityCredential credential = store.getCredentialByName(
            credentialName,
            IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);
        return credential.getAuthenticationDataUsageCount();
    }

    static Collection<X509Certificate> createAuthKeys(IdentityCredentialStore store, String credentialName)
            throws Exception {
        IdentityCredential credential = store.getCredentialByName(
            credentialName,
            IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);
        credential.setAvailableAuthenticationKeys(5, 3);
        Collection<X509Certificate> certificates = credential.getAuthKeysNeedingCertification();
        for (X509Certificate certificate : certificates) {
            credential.storeStaticAuthenticationData(certificate, new byte[]{42, 43, 44});
        }
        return certificates;
    }

    @Test
    public void multipleDocuments() throws Exception {
        assumeTrue("IC HAL is not implemented", TestUtil.isHalImplemented());
        assumeTrue("IdentityCredentialStore.createPresentationSession(int) not supported",
                   TestUtil.getFeatureVersion() >= 202201);

        Context appContext = InstrumentationRegistry.getTargetContext();
        IdentityCredentialStore store = IdentityCredentialStore.getInstance(appContext);

        store.deleteCredentialByName("credential1");
        assertNull(store.deleteCredentialByName("credential1"));
        ProvisioningTest.createCredential(store, "credential1");
        Collection<X509Certificate> credential1AuthKeys = createAuthKeys(store, "credential1");

        store.deleteCredentialByName("credential2");
        assertNull(store.deleteCredentialByName("credential2"));
        ProvisioningTest.createCredential(store, "credential2");
        Collection<X509Certificate> credential2AuthKeys = createAuthKeys(store, "credential2");

        PresentationSession session = store.createPresentationSession(
            IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);

        KeyPair ephemeralKeyPair = session.getEphemeralKeyPair();
        KeyPair readerEphemeralKeyPair = Util.createEphemeralKeyPair();
        session.setReaderEphemeralPublicKey(readerEphemeralKeyPair.getPublic());
        byte[] sessionTranscript = Util.buildSessionTranscript(ephemeralKeyPair);
        session.setSessionTranscript(sessionTranscript);

        checkPresentation(session, "credential1",
                          credential1AuthKeys.iterator().next().getPublicKey(),
                          readerEphemeralKeyPair.getPrivate(), sessionTranscript);
        // We should only have used a single key here.
        assertArrayEquals(new int[]{1, 0, 0, 0, 0}, getAuthKeyUsageCount(store, "credential1"));

        checkPresentation(session, "credential2",
                          credential2AuthKeys.iterator().next().getPublicKey(),
                          readerEphemeralKeyPair.getPrivate(), sessionTranscript);
        assertArrayEquals(new int[]{1, 0, 0, 0, 0}, getAuthKeyUsageCount(store, "credential2"));

        // Since it's the same session, additional getCredentialData() calls shouldn't consume
        // additional auth-keys. Check this.
        checkPresentation(session, "credential1",
                          null,
                          readerEphemeralKeyPair.getPrivate(), sessionTranscript);
        assertArrayEquals(new int[]{1, 0, 0, 0, 0}, getAuthKeyUsageCount(store, "credential1"));
    }

    static void checkPresentation(PresentationSession session,
                                  String credentialName,
                                  PublicKey expectedAuthKey,
                                  PrivateKey readerEphemeralPrivateKey,
                                  byte[] sessionTranscript) throws Exception {
        // Now use one of the keys...
        Map<String, Collection<String>> dsEntriesToRequest = new LinkedHashMap<>();
        dsEntriesToRequest.put("org.iso.18013-5.2019",
                Arrays.asList("First name",
                        "Last name",
                        "Home address",
                        "Birth date",
                        "Cryptanalyst",
                        "Portrait image",
                        "Height"));
        CredentialDataResult rd = session.getCredentialData(
            credentialName,
            new CredentialDataRequest.Builder()
            .setDeviceSignedEntriesToRequest(dsEntriesToRequest)
            .setRequestMessage(Util.createItemsRequest(dsEntriesToRequest, null))
            .setAllowUsingExhaustedKeys(true)
            .setReaderSignature(null)
            .setIncrementUseCount(true)
            .setAllowUsingExpiredKeys(false)
            .build());
        byte[] resultCbor = rd.getDeviceNameSpaces();
        try {
            String pretty = Util.cborPrettyPrint(Util.canonicalizeCbor(resultCbor));
            assertEquals("{\n"
                         + "  'org.iso.18013-5.2019' : {\n"
                         + "    'Height' : 180,\n"
                         + "    'Last name' : 'Turing',\n"
                         + "    'Birth date' : '19120623',\n"
                         + "    'First name' : 'Alan',\n"
                         + "    'Cryptanalyst' : true,\n"
                         + "    'Home address' : 'Maida Vale, London, England',\n"
                         + "    'Portrait image' : [0x01, 0x02]\n"
                         + "  }\n"
                         + "}",
                         pretty);
        } catch (CborException e) {
            e.printStackTrace();
            assertTrue(false);
        }

        byte[] deviceAuthenticationCbor = Util.buildDeviceAuthenticationCbor(
            "org.iso.18013-5.2019.mdl",
            sessionTranscript,
            resultCbor);

        if (expectedAuthKey != null) {
            // Calculate the MAC by deriving the key using ECDH and HKDF.
            SecretKey eMacKey = Util.calcEMacKeyForReader(
                expectedAuthKey,
                readerEphemeralPrivateKey,
                sessionTranscript);
            byte[] deviceAuthenticationBytes =
                Util.prependSemanticTagForEncodedCbor(deviceAuthenticationCbor);
            byte[] expectedMac = Util.coseMac0(eMacKey,
                                               new byte[0],                 // payload
                                               deviceAuthenticationBytes);  // detached content

            // Then compare it with what the TA produced.
            assertArrayEquals(expectedMac, rd.getDeviceMac());

            // Feature version 202301 and later also returns an ECDSA signature. Check this.
            if (TestUtil.getFeatureVersion() >= 202301) {
                byte[] signature = rd.getDeviceSignature();
                assertNotNull(signature);
                assertEquals(0, Util.coseSign1GetData(signature).length);
                assertTrue(Util.coseSign1CheckSignature(signature,
                                                        deviceAuthenticationBytes, // detached content
                                                        expectedAuthKey));
            } else {
                try {
                    rd.getDeviceSignature();
                    assertTrue(false);
                } catch (UnsupportedOperationException e) {
                    // This is the expected path...
                }
            }
        }
    }

    @Test
    public void cryptoObjectReturnsCorrectSession() throws Exception {
        assumeTrue("IC HAL is not implemented", TestUtil.isHalImplemented());
        assumeTrue("IdentityCredentialStore.createPresentationSession(int) not supported",
                   TestUtil.getFeatureVersion() >= 202201);

        Context appContext = InstrumentationRegistry.getTargetContext();
        IdentityCredentialStore store = IdentityCredentialStore.getInstance(appContext);

        PresentationSession session = store.createPresentationSession(
            IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);

        CryptoObject cryptoObject = new CryptoObject(session);
        assertEquals(session, cryptoObject.getPresentationSession());
    }
}
