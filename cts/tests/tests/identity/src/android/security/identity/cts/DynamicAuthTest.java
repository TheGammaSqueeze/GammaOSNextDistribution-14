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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.Context;

import android.os.SystemClock;
import android.security.identity.AuthenticationKeyMetadata;
import android.security.identity.EphemeralPublicKeyNotFoundException;
import android.security.identity.IdentityCredential;
import android.security.identity.IdentityCredentialException;
import android.security.identity.IdentityCredentialStore;
import android.security.identity.NoAuthenticationKeyAvailableException;
import android.security.identity.ResultData;
import androidx.test.InstrumentationRegistry;
import com.android.security.identity.internal.Util;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.KeyPair;
import java.security.SignatureException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.SecretKey;

import co.nstant.in.cbor.CborBuilder;
import co.nstant.in.cbor.CborEncoder;
import co.nstant.in.cbor.CborException;

public class DynamicAuthTest {
    private static final String TAG = "DynamicAuthTest";

    @Test
    public void dynamicAuthTest() throws Exception {
        assumeTrue("IC HAL is not implemented", TestUtil.isHalImplemented());

        Context appContext = InstrumentationRegistry.getTargetContext();
        IdentityCredentialStore store = IdentityCredentialStore.getInstance(appContext);

        String credentialName = "test";

        store.deleteCredentialByName(credentialName);
        Collection<X509Certificate> certChain = ProvisioningTest.createCredential(store,
                credentialName);

        IdentityCredential credential = store.getCredentialByName(credentialName,
                IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);
        assertNotNull(credential);
        assertArrayEquals(new int[0], credential.getAuthenticationDataUsageCount());

        credential.setAvailableAuthenticationKeys(5, 3);
        assertArrayEquals(
                new int[]{0, 0, 0, 0, 0},
                credential.getAuthenticationDataUsageCount());

        // Getting data without device authentication should work even in the case where we haven't
        // provisioned any authentication keys. Check that.
        Map<String, Collection<String>> entriesToRequest = new LinkedHashMap<>();
        entriesToRequest.put("org.iso.18013-5.2019", Arrays.asList("First name", "Last name"));
        ResultData rd = credential.getEntries(
                Util.createItemsRequest(entriesToRequest, null),
                entriesToRequest,
                null, // sessionTranscript null indicates Device Authentication not requested.
                null);
        byte[] resultCbor = rd.getAuthenticatedData();
        try {
            String pretty = Util.cborPrettyPrint(Util.canonicalizeCbor(resultCbor));
            assertEquals("{\n"
                            + "  'org.iso.18013-5.2019' : {\n"
                            + "    'Last name' : 'Turing',\n"
                            + "    'First name' : 'Alan'\n"
                            + "  }\n"
                            + "}",
                    pretty);
        } catch (CborException e) {
            e.printStackTrace();
            assertTrue(false);
        }

        KeyPair readerEphemeralKeyPair = Util.createEphemeralKeyPair();

        credential = store.getCredentialByName(credentialName,
                IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);
        KeyPair ephemeralKeyPair = credential.createEphemeralKeyPair();
        credential.setReaderEphemeralPublicKey(readerEphemeralKeyPair.getPublic());

        byte[] sessionTranscript = Util.buildSessionTranscript(ephemeralKeyPair);
        // Then check that getEntries() throw NoAuthenticationKeyAvailableException (_even_ when
        // allowing using exhausted keys).
        try {
            rd = credential.getEntries(
                    Util.createItemsRequest(entriesToRequest, null),
                    entriesToRequest,
                    sessionTranscript,
                    null);
            assertTrue(false);
        } catch (NoAuthenticationKeyAvailableException e) {
            // This is the expected path...
        } catch (IdentityCredentialException e) {
            e.printStackTrace();
            assertTrue(false);
        }

        // Get auth keys needing certification. This should be all of them. Note that
        // this forces the creation of the authentication keys in the HAL.
        Collection<X509Certificate> certificates = null;
        certificates = credential.getAuthKeysNeedingCertification();
        assertEquals(5, certificates.size());

        // Do it one more time to check that an auth key is still pending even
        // when the corresponding key has been created.
        Collection<X509Certificate> certificates2 = null;
        certificates2 = credential.getAuthKeysNeedingCertification();
        assertArrayEquals(certificates.toArray(), certificates2.toArray());

        // Now set auth data for the *first* key (this is the act of certifying the key) and check
        // that one less key now needs certification.
        X509Certificate key0Cert = certificates.iterator().next();

        // Check key0Cert is signed by CredentialKey.
        try {
            key0Cert.verify(certChain.iterator().next().getPublicKey());
        } catch (CertificateException
                | InvalidKeyException
                | NoSuchAlgorithmException
                | NoSuchProviderException
                | SignatureException e) {
            e.printStackTrace();
            assertTrue(false);
        }

        try {
            credential.storeStaticAuthenticationData(key0Cert, new byte[]{42, 43, 44});
            certificates = credential.getAuthKeysNeedingCertification();
        } catch (IdentityCredentialException e) {
            e.printStackTrace();
            assertTrue(false);
        }
        assertEquals(4, certificates.size());

        // Now certify the *last* key.
        X509Certificate key4Cert = new ArrayList<X509Certificate>(certificates).get(
            certificates.size() - 1);
        try {
            key4Cert.verify(certChain.iterator().next().getPublicKey());
        } catch (CertificateException
                | InvalidKeyException
                | NoSuchAlgorithmException
                | NoSuchProviderException
                | SignatureException e) {
            e.printStackTrace();
            assertTrue(false);
        }

        try {
            credential.storeStaticAuthenticationData(key4Cert, new byte[]{43, 44, 45});
            certificates = credential.getAuthKeysNeedingCertification();
        } catch (IdentityCredentialException e) {
            e.printStackTrace();
            assertTrue(false);
        }
        assertEquals(3, certificates.size());

        // Now that we've provisioned authentication keys, presentation will no longer fail with
        // NoAuthenticationKeyAvailableException ... So now we can try a sessionTranscript without
        // the ephemeral public key that was created in the Secure Area and check it fails with
        // EphemeralPublicKeyNotFoundException instead...
        ByteArrayOutputStream stBaos = new ByteArrayOutputStream();
        try {
            new CborEncoder(stBaos).encode(new CborBuilder()
                    .addArray()
                    .add(new byte[]{0x01, 0x02})  // The device engagement structure, encoded
                    .add(new byte[]{0x03, 0x04})  // Reader ephemeral public key, encoded
                    .end()
                    .build());
        } catch (CborException e) {
            e.printStackTrace();
            assertTrue(false);
        }
        byte[] wrongSessionTranscript = stBaos.toByteArray();
        try {
            rd = credential.getEntries(
                    Util.createItemsRequest(entriesToRequest, null),
                    entriesToRequest,
                    wrongSessionTranscript,
                    null);
            assertTrue(false);
        } catch (EphemeralPublicKeyNotFoundException e) {
            // This is the expected path...
        } catch (IdentityCredentialException e) {
            e.printStackTrace();
            assertTrue(false);
        }

        // Now use one of the keys...
        entriesToRequest = new LinkedHashMap<>();
        entriesToRequest.put("org.iso.18013-5.2019",
                Arrays.asList("First name",
                        "Last name",
                        "Home address",
                        "Birth date",
                        "Cryptanalyst",
                        "Portrait image",
                        "Height"));
        credential = store.getCredentialByName(credentialName,
                IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);
        ephemeralKeyPair = credential.createEphemeralKeyPair();
        credential.setReaderEphemeralPublicKey(readerEphemeralKeyPair.getPublic());
        sessionTranscript = Util.buildSessionTranscript(ephemeralKeyPair);
        rd = credential.getEntries(
                Util.createItemsRequest(entriesToRequest, null),
                entriesToRequest,
                sessionTranscript,
                null);
        resultCbor = rd.getAuthenticatedData();
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

        // Calculate the MAC by deriving the key using ECDH and HKDF.
        SecretKey eMacKey = Util.calcEMacKeyForReader(
            key0Cert.getPublicKey(),
            readerEphemeralKeyPair.getPrivate(),
            sessionTranscript);
        byte[] deviceAuthenticationBytes =
                Util.prependSemanticTagForEncodedCbor(deviceAuthenticationCbor);
        byte[] expectedMac = Util.coseMac0(eMacKey,
                new byte[0],                 // payload
                deviceAuthenticationBytes);  // detached content

        // Then compare it with what the TA produced.
        assertArrayEquals(expectedMac, rd.getMessageAuthenticationCode());

        // Check that key0's static auth data is returned and that this
        // key has an increased use-count.
        assertArrayEquals(new byte[]{42, 43, 44}, rd.getStaticAuthenticationData());
        assertArrayEquals(new int[]{1, 0, 0, 0, 0}, credential.getAuthenticationDataUsageCount());

        // Now do this one more time.... this time key4 should have been used. Check this by
        // inspecting use-counts and the static authentication data.
        credential = store.getCredentialByName(credentialName,
                IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);
        ephemeralKeyPair = credential.createEphemeralKeyPair();
        credential.setReaderEphemeralPublicKey(readerEphemeralKeyPair.getPublic());
        sessionTranscript = Util.buildSessionTranscript(ephemeralKeyPair);
        rd = credential.getEntries(
                Util.createItemsRequest(entriesToRequest, null),
                entriesToRequest,
                sessionTranscript,
                null);
        assertArrayEquals(new byte[]{43, 44, 45}, rd.getStaticAuthenticationData());
        assertArrayEquals(new int[]{1, 0, 0, 0, 1}, credential.getAuthenticationDataUsageCount());

        // Verify MAC was made with key4.
        deviceAuthenticationCbor = Util.buildDeviceAuthenticationCbor(
            "org.iso.18013-5.2019.mdl",
            sessionTranscript,
            resultCbor);
        eMacKey = Util.calcEMacKeyForReader(
            key4Cert.getPublicKey(),
            readerEphemeralKeyPair.getPrivate(),
            sessionTranscript);
        deviceAuthenticationBytes =
                Util.prependSemanticTagForEncodedCbor(deviceAuthenticationCbor);
        expectedMac = Util.coseMac0(eMacKey,
                new byte[0],                 // payload
                deviceAuthenticationBytes);  // detached content
        assertArrayEquals(expectedMac, rd.getMessageAuthenticationCode());

        // And again.... this time key0 should have been used. Check it.
        credential = store.getCredentialByName(credentialName,
                IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);
        ephemeralKeyPair = credential.createEphemeralKeyPair();
        credential.setReaderEphemeralPublicKey(readerEphemeralKeyPair.getPublic());
        sessionTranscript = Util.buildSessionTranscript(ephemeralKeyPair);
        rd = credential.getEntries(
                Util.createItemsRequest(entriesToRequest, null),
                entriesToRequest,
                sessionTranscript,
                null);
        assertArrayEquals(new byte[]{42, 43, 44}, rd.getStaticAuthenticationData());
        assertArrayEquals(new int[]{2, 0, 0, 0, 1}, credential.getAuthenticationDataUsageCount());

        // And again.... this time key4 should have been used. Check it.
        credential = store.getCredentialByName(credentialName,
                IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);
        ephemeralKeyPair = credential.createEphemeralKeyPair();
        credential.setReaderEphemeralPublicKey(readerEphemeralKeyPair.getPublic());
        sessionTranscript = Util.buildSessionTranscript(ephemeralKeyPair);
        rd = credential.getEntries(
                Util.createItemsRequest(entriesToRequest, null),
                entriesToRequest,
                sessionTranscript,
                null);
        assertArrayEquals(new byte[]{43, 44, 45}, rd.getStaticAuthenticationData());
        assertArrayEquals(new int[]{2, 0, 0, 0, 2}, credential.getAuthenticationDataUsageCount());

        // We configured each key to have three uses only. So we have two more presentations
        // to go until we run out... first, check that only three keys need certifications
        certificates = credential.getAuthKeysNeedingCertification();
        assertEquals(3, certificates.size());

        // Then exhaust the two we've already configured.
        for (int n = 0; n < 2; n++) {
            credential = store.getCredentialByName(credentialName,
                IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);
            ephemeralKeyPair = credential.createEphemeralKeyPair();
            credential.setReaderEphemeralPublicKey(readerEphemeralKeyPair.getPublic());
            sessionTranscript = Util.buildSessionTranscript(ephemeralKeyPair);
            rd = credential.getEntries(
                    Util.createItemsRequest(entriesToRequest, null),
                    entriesToRequest,
                    sessionTranscript,
                    null);
            assertNotNull(rd);
        }
        assertArrayEquals(new int[]{3, 0, 0, 0, 3}, credential.getAuthenticationDataUsageCount());

        // Now we should have five certs needing certification.
        certificates = credential.getAuthKeysNeedingCertification();
        assertEquals(5, certificates.size());

        // We still have the two keys which have been exhausted.
        assertArrayEquals(new int[]{3, 0, 0, 0, 3}, credential.getAuthenticationDataUsageCount());

        // Check that we fail when running out of presentations (and explicitly don't allow
        // exhausting keys).
        try {
            credential = store.getCredentialByName(credentialName,
                IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);
            ephemeralKeyPair = credential.createEphemeralKeyPair();
            credential.setReaderEphemeralPublicKey(readerEphemeralKeyPair.getPublic());
            sessionTranscript = Util.buildSessionTranscript(ephemeralKeyPair);
            credential.setAllowUsingExhaustedKeys(false);
            rd = credential.getEntries(
                    Util.createItemsRequest(entriesToRequest, null),
                    entriesToRequest,
                    sessionTranscript,
                    null);
            assertTrue(false);
        } catch (IdentityCredentialException e) {
            assertTrue(e instanceof NoAuthenticationKeyAvailableException);
        }
        assertArrayEquals(new int[]{3, 0, 0, 0, 3}, credential.getAuthenticationDataUsageCount());

        // Now try with allowing using auth keys already exhausted... this should work!
        try {
            credential = store.getCredentialByName(credentialName,
                IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);
            ephemeralKeyPair = credential.createEphemeralKeyPair();
            credential.setReaderEphemeralPublicKey(readerEphemeralKeyPair.getPublic());
            sessionTranscript = Util.buildSessionTranscript(ephemeralKeyPair);
            rd = credential.getEntries(
                    Util.createItemsRequest(entriesToRequest, null),
                    entriesToRequest,
                    sessionTranscript,
                    null);
        } catch (IdentityCredentialException e) {
            e.printStackTrace();
            assertTrue(false);
        }
        assertArrayEquals(new int[]{4, 0, 0, 0, 3}, credential.getAuthenticationDataUsageCount());

        // Check that replenishing works...
        certificates = credential.getAuthKeysNeedingCertification();
        assertEquals(5, certificates.size());
        X509Certificate keyNewCert = certificates.iterator().next();
        try {
            credential.storeStaticAuthenticationData(keyNewCert, new byte[]{10, 11, 12});
            certificates = credential.getAuthKeysNeedingCertification();
        } catch (IdentityCredentialException e) {
            e.printStackTrace();
            assertTrue(false);
        }
        assertEquals(4, certificates.size());
        assertArrayEquals(new int[]{0, 0, 0, 0, 3}, credential.getAuthenticationDataUsageCount());
        credential = store.getCredentialByName(credentialName,
                IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);
        ephemeralKeyPair = credential.createEphemeralKeyPair();
        credential.setReaderEphemeralPublicKey(readerEphemeralKeyPair.getPublic());
        sessionTranscript = Util.buildSessionTranscript(ephemeralKeyPair);
        rd = credential.getEntries(
                Util.createItemsRequest(entriesToRequest, null),
                entriesToRequest,
                sessionTranscript,
                null);
        assertArrayEquals(new byte[]{10, 11, 12}, rd.getStaticAuthenticationData());
        assertArrayEquals(new int[]{1, 0, 0, 0, 3}, credential.getAuthenticationDataUsageCount());

        deviceAuthenticationCbor = Util.buildDeviceAuthenticationCbor(
            "org.iso.18013-5.2019.mdl",
            sessionTranscript,
            resultCbor);
        eMacKey = Util.calcEMacKeyForReader(
            keyNewCert.getPublicKey(),
            readerEphemeralKeyPair.getPrivate(),
            sessionTranscript);
        deviceAuthenticationBytes =
                Util.prependSemanticTagForEncodedCbor(deviceAuthenticationCbor);
        expectedMac = Util.coseMac0(eMacKey,
                new byte[0],                 // payload
                deviceAuthenticationBytes);  // detached content
        assertArrayEquals(expectedMac, rd.getMessageAuthenticationCode());

        // ... and we're done. Clean up after ourselves.
        store.deleteCredentialByName(credentialName);
    }


    @Test
    public void dynamicAuthWithExpirationTest() throws Exception {
        assumeTrue("IC HAL is not implemented", TestUtil.isHalImplemented());

        Context appContext = InstrumentationRegistry.getTargetContext();
        IdentityCredentialStore store = IdentityCredentialStore.getInstance(appContext);

        String credentialName = "test";

        store.deleteCredentialByName(credentialName);
        Collection<X509Certificate> certChain = ProvisioningTest.createCredential(store,
                credentialName);

        IdentityCredential credential = store.getCredentialByName(credentialName,
                IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);
        assertNotNull(credential);

        credential.setAvailableAuthenticationKeys(3, 5);

        Collection<X509Certificate> certificates = null;
        certificates = credential.getAuthKeysNeedingCertification();
        assertEquals(3, certificates.size());

        // Endorse an auth-key but set expiration to 10 seconds in the future.
        //
        Instant now = Instant.now();
        Instant tenSecondsFromNow = now.plusSeconds(10);
        try {
            X509Certificate key0Cert = certificates.iterator().next();
            credential.storeStaticAuthenticationData(key0Cert,
                    tenSecondsFromNow,
                    new byte[]{52, 53, 44});
            certificates = credential.getAuthKeysNeedingCertification();
        } catch (IdentityCredentialException e) {
            e.printStackTrace();
            assertTrue(false);
        }
        assertEquals(2, certificates.size());
        assertArrayEquals(
                new int[]{0, 0, 0},
                credential.getAuthenticationDataUsageCount());
        // Check that presentation works.
        try {
            IdentityCredential tc = store.getCredentialByName(credentialName,
                IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);
            KeyPair ekp = tc.createEphemeralKeyPair();
            KeyPair rekp = Util.createEphemeralKeyPair();
            tc.setReaderEphemeralPublicKey(rekp.getPublic());
            byte[] st = Util.buildSessionTranscript(ekp);
            Map<String, Collection<String>> etr = new LinkedHashMap<>();
            etr.put("org.iso.18013-5.2019", Arrays.asList("First name", "Last name"));
            ResultData rd = tc.getEntries(
                Util.createItemsRequest(etr, null),
                etr,
                st,
                null);
        } catch (IdentityCredentialException e) {
            e.printStackTrace();
            assertTrue(false);
        }
        credential = store.getCredentialByName(credentialName,
                IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);
        assertArrayEquals(
                new int[]{1, 0, 0},
                credential.getAuthenticationDataUsageCount());

        SystemClock.sleep(11 * 1000);

        certificates = credential.getAuthKeysNeedingCertification();
        assertEquals(3, certificates.size());

        // Check that presentation now fails..
        try {
            IdentityCredential tc = store.getCredentialByName(credentialName,
                IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);
            KeyPair ekp = tc.createEphemeralKeyPair();
            KeyPair rekp = Util.createEphemeralKeyPair();
            tc.setReaderEphemeralPublicKey(rekp.getPublic());
            byte[] st = Util.buildSessionTranscript(ekp);
            Map<String, Collection<String>> etr = new LinkedHashMap<>();
            etr.put("org.iso.18013-5.2019", Arrays.asList("First name", "Last name"));
            ResultData rd = tc.getEntries(
                Util.createItemsRequest(etr, null),
                etr,
                st,
                null);
            assertTrue(false);
        } catch (NoAuthenticationKeyAvailableException e) {
            // This is the expected path...
        } catch (IdentityCredentialException e) {
            e.printStackTrace();
            assertTrue(false);
        }
        credential = store.getCredentialByName(credentialName,
                IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);
        assertArrayEquals(
                new int[]{1, 0, 0},
                credential.getAuthenticationDataUsageCount());

        // Check that it works if we use setAllowUsingExpiredKeys(true)
        try {
            IdentityCredential tc = store.getCredentialByName(credentialName,
                IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);
            tc.setAllowUsingExpiredKeys(true);   // <-- this is the call that makes the difference!
            KeyPair ekp = tc.createEphemeralKeyPair();
            KeyPair rekp = Util.createEphemeralKeyPair();
            tc.setReaderEphemeralPublicKey(rekp.getPublic());
            byte[] st = Util.buildSessionTranscript(ekp);
            Map<String, Collection<String>> etr = new LinkedHashMap<>();
            etr.put("org.iso.18013-5.2019", Arrays.asList("First name", "Last name"));
            ResultData rd = tc.getEntries(
                Util.createItemsRequest(etr, null),
                etr,
                st,
                null);
        } catch (IdentityCredentialException e) {
            e.printStackTrace();
            assertTrue(false);
        }
        credential = store.getCredentialByName(credentialName,
                IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);
        assertArrayEquals(
                new int[]{2, 0, 0},
                credential.getAuthenticationDataUsageCount());

        // ... and we're done. Clean up after ourselves.
        store.deleteCredentialByName(credentialName);
    }

    @Test
    public void dynamicAuthMinValidTimeTest() throws Exception {
        assumeTrue("IC HAL is not implemented", TestUtil.isHalImplemented());

        Context appContext = InstrumentationRegistry.getTargetContext();
        IdentityCredentialStore store = IdentityCredentialStore.getInstance(appContext);

        String credentialName = "test";

        store.deleteCredentialByName(credentialName);
        Collection<X509Certificate> certChain = ProvisioningTest.createCredential(store,
                credentialName);

        IdentityCredential credential = store.getCredentialByName(credentialName,
                IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);
        assertNotNull(credential);

        // Set minValidTime to five seconds...
        credential.setAvailableAuthenticationKeys(3, 5, 5000);

        Collection<X509Certificate> authKeys = null;
        authKeys = credential.getAuthKeysNeedingCertification();
        assertEquals(3, authKeys.size());

        // Endorse all auth-key but set expiration to 10 seconds in the future.
        //
        Instant beginTime = Instant.now();
        Instant tenSecondsFromBeginning = beginTime.plusSeconds(10);
        for (X509Certificate authKey : authKeys) {
            try {
                credential.storeStaticAuthenticationData(authKey,
                                                         tenSecondsFromBeginning,
                                                         new byte[]{52, 53, 44});
            } catch (IdentityCredentialException e) {
                e.printStackTrace();
                assertTrue(false);
            }
        }
        authKeys = credential.getAuthKeysNeedingCertification();
        assertEquals(0, authKeys.size());
        assertArrayEquals(
                new int[]{0, 0, 0},
                credential.getAuthenticationDataUsageCount());
        // Check that presentation works.
        try {
            IdentityCredential tc = store.getCredentialByName(credentialName,
                IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);
            KeyPair ekp = tc.createEphemeralKeyPair();
            KeyPair rekp = Util.createEphemeralKeyPair();
            tc.setReaderEphemeralPublicKey(rekp.getPublic());
            byte[] st = Util.buildSessionTranscript(ekp);
            Map<String, Collection<String>> etr = new LinkedHashMap<>();
            etr.put("org.iso.18013-5.2019", Arrays.asList("First name", "Last name"));
            ResultData rd = tc.getEntries(
                Util.createItemsRequest(etr, null),
                etr,
                st,
                null);
        } catch (IdentityCredentialException e) {
            e.printStackTrace();
            assertTrue(false);
        }
        // At this point, no authKeys are tagged as needing replacement
        credential = store.getCredentialByName(credentialName,
                IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);
        assertArrayEquals(
                new int[]{1, 0, 0},
                credential.getAuthenticationDataUsageCount());
        authKeys = credential.getAuthKeysNeedingCertification();
        assertEquals(0, authKeys.size());

        // **Six** seconds later the AuthKeys are still usable but all three should be tagged as
        // needing replacement.
        SystemClock.sleep(6 * 1000);
        authKeys = credential.getAuthKeysNeedingCertification();
        assertEquals(3, authKeys.size());
        // However presentations should _still_ work since they haven't expired. Check this.
        try {
            IdentityCredential tc = store.getCredentialByName(credentialName,
                IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);
            KeyPair ekp = tc.createEphemeralKeyPair();
            KeyPair rekp = Util.createEphemeralKeyPair();
            tc.setReaderEphemeralPublicKey(rekp.getPublic());
            byte[] st = Util.buildSessionTranscript(ekp);
            Map<String, Collection<String>> etr = new LinkedHashMap<>();
            etr.put("org.iso.18013-5.2019", Arrays.asList("First name", "Last name"));
            ResultData rd = tc.getEntries(
                Util.createItemsRequest(etr, null),
                etr,
                st,
                null);
        } catch (IdentityCredentialException e) {
            e.printStackTrace();
            assertTrue(false);
        }
        credential = store.getCredentialByName(credentialName,
                IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);
        assertArrayEquals(
                new int[]{1, 1, 0},
                credential.getAuthenticationDataUsageCount());
        authKeys = credential.getAuthKeysNeedingCertification();
        assertEquals(3, authKeys.size());

        // Now replace all AuthKeys, set expiration to 15 seconds from beginning
        Instant fifteenSecondsFromBeginning = beginTime.plusSeconds(15);
        for (X509Certificate authKey : authKeys) {
            try {
                credential.storeStaticAuthenticationData(authKey,
                                                         fifteenSecondsFromBeginning,
                                                         new byte[]{52, 53, 44});
            } catch (IdentityCredentialException e) {
                e.printStackTrace();
                assertTrue(false);
            }
        }
        // At this point (T + 6 sec), no authKeys are tagged as needing replacement
        authKeys = credential.getAuthKeysNeedingCertification();
        assertEquals(0, authKeys.size());
        assertArrayEquals(
                new int[]{0, 0, 0},
                credential.getAuthenticationDataUsageCount());

        // **Five** seconds after this (T + 11sec), these new authKeys should be tagged
        // as needing replacement but still work. Check this.
        SystemClock.sleep(5 * 1000);
        try {
            IdentityCredential tc = store.getCredentialByName(credentialName,
                IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);
            KeyPair ekp = tc.createEphemeralKeyPair();
            KeyPair rekp = Util.createEphemeralKeyPair();
            tc.setReaderEphemeralPublicKey(rekp.getPublic());
            byte[] st = Util.buildSessionTranscript(ekp);
            Map<String, Collection<String>> etr = new LinkedHashMap<>();
            etr.put("org.iso.18013-5.2019", Arrays.asList("First name", "Last name"));
            ResultData rd = tc.getEntries(
                Util.createItemsRequest(etr, null),
                etr,
                st,
                null);
        } catch (IdentityCredentialException e) {
            e.printStackTrace();
            assertTrue(false);
        }
        credential = store.getCredentialByName(credentialName,
                IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);
        assertArrayEquals(
                new int[]{1, 0, 0},
                credential.getAuthenticationDataUsageCount());
        authKeys = credential.getAuthKeysNeedingCertification();
        assertEquals(3, authKeys.size());

        // **Five** seconds later (T + 16sec) all AuthKeys should be expired and still
        // tagged as needing replacement. Check this.
        SystemClock.sleep(5 * 1000);
        authKeys = credential.getAuthKeysNeedingCertification();
        assertEquals(3, authKeys.size());
        // Check that presentation now fails..
        try {
            IdentityCredential tc = store.getCredentialByName(credentialName,
                IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);
            KeyPair ekp = tc.createEphemeralKeyPair();
            KeyPair rekp = Util.createEphemeralKeyPair();
            tc.setReaderEphemeralPublicKey(rekp.getPublic());
            byte[] st = Util.buildSessionTranscript(ekp);
            Map<String, Collection<String>> etr = new LinkedHashMap<>();
            etr.put("org.iso.18013-5.2019", Arrays.asList("First name", "Last name"));
            ResultData rd = tc.getEntries(
                Util.createItemsRequest(etr, null),
                etr,
                st,
                null);
            assertTrue(false);
        } catch (NoAuthenticationKeyAvailableException e) {
            // This is the expected path...
        } catch (IdentityCredentialException e) {
            e.printStackTrace();
            assertTrue(false);
        }
        credential = store.getCredentialByName(credentialName,
                IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);
        assertArrayEquals(
                new int[]{1, 0, 0},
                credential.getAuthenticationDataUsageCount());

        // Check that it works if we use setAllowUsingExpiredKeys(true)
        try {
            IdentityCredential tc = store.getCredentialByName(credentialName,
                IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);
            tc.setAllowUsingExpiredKeys(true);   // <-- this is the call that makes the difference!
            KeyPair ekp = tc.createEphemeralKeyPair();
            KeyPair rekp = Util.createEphemeralKeyPair();
            tc.setReaderEphemeralPublicKey(rekp.getPublic());
            byte[] st = Util.buildSessionTranscript(ekp);
            Map<String, Collection<String>> etr = new LinkedHashMap<>();
            etr.put("org.iso.18013-5.2019", Arrays.asList("First name", "Last name"));
            ResultData rd = tc.getEntries(
                Util.createItemsRequest(etr, null),
                etr,
                st,
                null);
        } catch (IdentityCredentialException e) {
            e.printStackTrace();
            assertTrue(false);
        }
        credential = store.getCredentialByName(credentialName,
                IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);
        assertArrayEquals(
                new int[]{1, 1, 0},
                credential.getAuthenticationDataUsageCount());

        // ... and we're done. Clean up after ourselves.
        store.deleteCredentialByName(credentialName);
    }

    @Test
    public void dynamicAuthCanGetExpirations() throws Exception {
        assumeTrue("IC HAL is not implemented", TestUtil.isHalImplemented());

        Context appContext = InstrumentationRegistry.getTargetContext();
        IdentityCredentialStore store = IdentityCredentialStore.getInstance(appContext);

        String credentialName = "test";

        store.deleteCredentialByName(credentialName);
        Collection<X509Certificate> certChain = ProvisioningTest.createCredential(store,
                credentialName);

        IdentityCredential credential = store.getCredentialByName(credentialName,
                IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);
        assertNotNull(credential);

        int numAuthKeys = 10;
        credential.setAvailableAuthenticationKeys(numAuthKeys, 1, 0);

        Collection<X509Certificate> authKeys = null;
        authKeys = credential.getAuthKeysNeedingCertification();
        assertEquals(numAuthKeys, authKeys.size());

        // Endorse all auth keys and set expiration times to known values in the future
        //
        long tenSecondsFromBeginning = Instant.now().plusSeconds(10).toEpochMilli();
        int n = 0;
        for (X509Certificate authKey : authKeys) {
            try {
                Instant expiration = Instant.ofEpochMilli(tenSecondsFromBeginning + n);
                // For the fourth certificate, don't set expiration
                if (n == 3) {
                    credential.storeStaticAuthenticationData(authKey,
                                                             new byte[]{52, 53, 44});
                } else {
                    credential.storeStaticAuthenticationData(authKey,
                                                             expiration,
                                                             new byte[]{52, 53, 44});
                }
            } catch (IdentityCredentialException e) {
                e.printStackTrace();
                assertTrue(false);
            }
            n++;
        }
        authKeys = credential.getAuthKeysNeedingCertification();
        assertEquals(0, authKeys.size());

        // Check we can read back expirations and usage counts
        List<AuthenticationKeyMetadata> mds = credential.getAuthenticationKeyMetadata();
        assertEquals(mds.size(), numAuthKeys);
        for (n = 0; n < numAuthKeys; n++) {
            AuthenticationKeyMetadata md = mds.get(n);
            if (n == 3) {
                assertNull(md);
            } else {
                assertEquals(tenSecondsFromBeginning + n, md.getExpirationDate().toEpochMilli());
                assertEquals(0, md.getUsageCount());
            }
        }

        // ... and we're done. Clean up after ourselves.
        store.deleteCredentialByName(credentialName);
    }

    @Test
    public void dynamicAuthMultipleGetEntries() throws Exception {
        assumeTrue("IC HAL is not implemented", TestUtil.isHalImplemented());

        Context appContext = InstrumentationRegistry.getTargetContext();
        IdentityCredentialStore store = IdentityCredentialStore.getInstance(appContext);

        String credentialName = "test";

        store.deleteCredentialByName(credentialName);
        Collection<X509Certificate> certChain = ProvisioningTest.createCredential(store,
                credentialName);

        IdentityCredential credential = store.getCredentialByName(credentialName,
                IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);
        assertNotNull(credential);
        assertArrayEquals(new int[0], credential.getAuthenticationDataUsageCount());

        credential.setAvailableAuthenticationKeys(5, 3);
        assertArrayEquals(new int[]{0, 0, 0, 0, 0},
                          credential.getAuthenticationDataUsageCount());

        Collection<X509Certificate> certs = credential.getAuthKeysNeedingCertification();
        assertEquals(5, certs.size());

        // Certify authKeys 0 and 1
        //
        X509Certificate key0Cert = new ArrayList<X509Certificate>(certs).get(0);
        credential.storeStaticAuthenticationData(key0Cert, new byte[]{42, 43, 44});
        X509Certificate key1Cert = new ArrayList<X509Certificate>(certs).get(1);
        credential.storeStaticAuthenticationData(key1Cert, new byte[]{43, 44, 45});

        // Get ready to present...
        //
        Map<String, Collection<String>> entriesToRequest = new LinkedHashMap<>();
        entriesToRequest.put("org.iso.18013-5.2019", Arrays.asList("First name", "Last name"));
        KeyPair ephemeralKeyPair = credential.createEphemeralKeyPair();
        KeyPair readerEphemeralKeyPair = Util.createEphemeralKeyPair();
        byte[] sessionTranscript = Util.buildSessionTranscript(ephemeralKeyPair);
        credential.setReaderEphemeralPublicKey(readerEphemeralKeyPair.getPublic());

        // First getEntries() call should pick key0
        //
        ResultData rd = credential.getEntries(
                Util.createItemsRequest(entriesToRequest, null),
                entriesToRequest,
                sessionTranscript,
                null);
        assertArrayEquals(new int[]{1, 0, 0, 0, 0}, credential.getAuthenticationDataUsageCount());
        assertArrayEquals(new byte[]{42, 43, 44}, rd.getStaticAuthenticationData());

        // Doing another getEntries() call on the same object should use the same key. Check this
        // by inspecting use-counts and the static authentication data.
        //
        rd = credential.getEntries(
            Util.createItemsRequest(entriesToRequest, null),
            entriesToRequest,
            sessionTranscript,
            null);
        assertArrayEquals(new int[]{1, 0, 0, 0, 0}, credential.getAuthenticationDataUsageCount());
        assertArrayEquals(new byte[]{42, 43, 44}, rd.getStaticAuthenticationData());
    }

    @Test
    public void dynamicAuthNoUsageCountIncrement() throws Exception {
        assumeTrue("IC HAL is not implemented", TestUtil.isHalImplemented());

        Context appContext = InstrumentationRegistry.getTargetContext();
        IdentityCredentialStore store = IdentityCredentialStore.getInstance(appContext);
        assumeTrue(
            "IdentityCredential.setIncrementKeyUsageCount(boolean) not supported",
            TestUtil.getFeatureVersion() >= 202201);

        String credentialName = "test";

        store.deleteCredentialByName(credentialName);
        Collection<X509Certificate> certChain = ProvisioningTest.createCredential(store,
                                                                                  credentialName);

        IdentityCredential credential = store.getCredentialByName(
            credentialName,
            IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);
        assertNotNull(credential);
        assertArrayEquals(new int[0], credential.getAuthenticationDataUsageCount());

        credential.setAvailableAuthenticationKeys(5, 3);
        assertArrayEquals(new int[]{0, 0, 0, 0, 0},
                          credential.getAuthenticationDataUsageCount());

        Collection<X509Certificate> certs = credential.getAuthKeysNeedingCertification();
        assertEquals(5, certs.size());

        // Certify authKeys 0 and 1
        //
        X509Certificate key0Cert = new ArrayList<X509Certificate>(certs).get(0);
        credential.storeStaticAuthenticationData(key0Cert, new byte[]{42, 43, 44});
        X509Certificate key1Cert = new ArrayList<X509Certificate>(certs).get(1);
        credential.storeStaticAuthenticationData(key1Cert, new byte[]{43, 44, 45});

        // Get ready to present...
        //
        Map<String, Collection<String>> entriesToRequest = new LinkedHashMap<>();
        entriesToRequest.put("org.iso.18013-5.2019", Arrays.asList("First name", "Last name"));
        KeyPair ephemeralKeyPair = credential.createEphemeralKeyPair();
        KeyPair readerEphemeralKeyPair = Util.createEphemeralKeyPair();
        byte[] sessionTranscript = Util.buildSessionTranscript(ephemeralKeyPair);
        credential.setReaderEphemeralPublicKey(readerEphemeralKeyPair.getPublic());

        // First getEntries() call should pick key0
        //
        ResultData rd = credential.getEntries(
            Util.createItemsRequest(entriesToRequest, null),
            entriesToRequest,
            sessionTranscript,
            null);
        assertArrayEquals(new int[]{1, 0, 0, 0, 0}, credential.getAuthenticationDataUsageCount());
        assertArrayEquals(new byte[]{42, 43, 44}, rd.getStaticAuthenticationData());

        // Now configure to not increase the usage count... we should be able to do as many
        // presentations as we want and usage counts shouldn't change. We'll just do 3.
        //
        // Note that key1 will be picked (we can check that by inspecting static authentication
        // data) but its usage count won't be increased.
        //
        credential = store.getCredentialByName(credentialName,
                IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);
        ephemeralKeyPair = credential.createEphemeralKeyPair();
        readerEphemeralKeyPair = Util.createEphemeralKeyPair();
        sessionTranscript = Util.buildSessionTranscript(ephemeralKeyPair);
        credential.setReaderEphemeralPublicKey(readerEphemeralKeyPair.getPublic());
        credential.setIncrementKeyUsageCount(false);
        for (int n = 0; n < 3; n++) {
            rd = credential.getEntries(
                Util.createItemsRequest(entriesToRequest, null),
                entriesToRequest,
                sessionTranscript,
                null);
            assertArrayEquals(new int[]{1, 0, 0, 0, 0},
                              credential.getAuthenticationDataUsageCount());
            assertArrayEquals(new byte[]{43, 44, 45}, rd.getStaticAuthenticationData());
        }

        // With usage counts incrementing again, do another getEntries() call. It should pick key1
        // and use it up. Check this by inspecting use-counts and the static authentication data.
        //
        credential = store.getCredentialByName(credentialName,
                IdentityCredentialStore.CIPHERSUITE_ECDHE_HKDF_ECDSA_WITH_AES_256_GCM_SHA256);
        ephemeralKeyPair = credential.createEphemeralKeyPair();
        readerEphemeralKeyPair = Util.createEphemeralKeyPair();
        sessionTranscript = Util.buildSessionTranscript(ephemeralKeyPair);
        credential.setReaderEphemeralPublicKey(readerEphemeralKeyPair.getPublic());
        rd = credential.getEntries(
            Util.createItemsRequest(entriesToRequest, null),
            entriesToRequest,
            sessionTranscript,
            null);
        assertArrayEquals(new int[]{1, 1, 0, 0, 0}, credential.getAuthenticationDataUsageCount());
        assertArrayEquals(new byte[]{43, 44, 45}, rd.getStaticAuthenticationData());
    }

    // TODO: test storeStaticAuthenticationData() throwing UnknownAuthenticationKeyException
    // on an unknown auth key
}
