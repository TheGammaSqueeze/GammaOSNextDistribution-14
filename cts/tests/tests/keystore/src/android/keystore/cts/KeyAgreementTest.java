/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.keystore.cts;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

import android.keystore.cts.util.ImportedKey;
import android.keystore.cts.util.TestUtils;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyInfo;
import android.security.keystore.KeyProperties;
import android.security.keystore.KeyProtection;

import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiTest;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.InvalidKeySpecException;

import javax.crypto.KeyAgreement;

public class KeyAgreementTest {
    private static final String PRIVATE_KEY_ALIAS = "TemporaryPrivateKey";

    @Before
    @After
    public void deleteKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        keyStore.deleteEntry(PRIVATE_KEY_ALIAS);
    }

    @Test
    public void testGenerateSecret_succeeds() throws Exception {
        KeyAgreement ka = getKeyStoreKeyAgreement();
        ka.init(generateEphemeralAndroidKeyPair().getPrivate());
        ka.doPhase(generateEphemeralServerKeyPair().getPublic(), true);
        byte[] sharedSecret = ka.generateSecret();
        assertNotNull(sharedSecret);
    }

    @Test
    public void testGenerateSecret_forTwoParties_returnsSameSharedSecret() throws Exception {
        KeyPair ourKeyPair = generateEphemeralAndroidKeyPair();
        KeyPair theirKeyPair = generateEphemeralServerKeyPair();

        KeyAgreement ka = getKeyStoreKeyAgreement();
        // Generate the shared secret with our own private key and the public key of the other party
        ka.init(ourKeyPair.getPrivate());
        ka.doPhase(theirKeyPair.getPublic(), true);
        byte[] ourSharedSecret = ka.generateSecret();
        // Generate the shared secret as if we were the other party
        KeyAgreement otherKeyAgreement = KeyAgreement.getInstance("ECDH"); // Use default provider
        otherKeyAgreement.init(theirKeyPair.getPrivate());
        otherKeyAgreement.doPhase(ourKeyPair.getPublic(), true);
        byte[] theirSharedSecret = otherKeyAgreement.generateSecret();

        Assert.assertArrayEquals(ourSharedSecret, theirSharedSecret);
    }

    @Test
    public void testGenerateSecret_preservesPrivateKeyAndNothingElse() throws Exception {
        KeyPair otherPartyKey = generateEphemeralServerKeyPair();
        KeyAgreement ka = getKeyStoreKeyAgreement();
        ka.init(generateEphemeralAndroidKeyPair().getPrivate());
        ka.doPhase(otherPartyKey.getPublic(), true);
        byte[] sharedSecret1 = ka.generateSecret();

        try {
            ka.generateSecret();
            fail(
                    "Should not be able to generate secret twice. Only private key should have"
                            + " been retained");
        } catch (IllegalStateException ise) {
            // Expected
        }
        ka.doPhase(otherPartyKey.getPublic(), true);
        byte[] sharedSecret2 = ka.generateSecret();

        Assert.assertArrayEquals(sharedSecret1, sharedSecret2);
    }

    @Test
    public void testGenerateSecret_withImportedKey() throws Exception {
        KeyProtection importParams =
                new KeyProtection.Builder(KeyProperties.PURPOSE_AGREE_KEY).build();
        ImportedKey importedKey = TestUtils.importIntoAndroidKeyStore("testECsecp256r1",
                InstrumentationRegistry.getTargetContext(),
                R.raw.ec_key4_secp256r1_pkcs8, R.raw.ec_key4_secp256r1_cert, importParams);
        KeyPair keyPair = importedKey.getKeystoreBackedKeyPair();

        KeyAgreement ka = getKeyStoreKeyAgreement();
        ka.init(keyPair.getPrivate());
        ka.doPhase(generateEphemeralServerKeyPair().getPublic(), true);
        byte[] sharedSecret = ka.generateSecret();
        assertNotNull(sharedSecret);
    }

    @Test
    public void testInit_withNonPrivateKey_fails() throws Exception {
        KeyAgreement ka = getKeyStoreKeyAgreement();
        try {
            ka.init(new TransparentSecretKey(new byte[0], null));
            fail("Initializing KeyAgreement with non-keystore key didn't throw exception.");
        } catch (InvalidKeyException ike) {
            // Expected
        }
    }

    @Test
    public void testInit_withNonEcKey_fails() throws Exception {
        KeyPairGenerator kpg =
                KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore");
        kpg.initialize(
                new KeyGenParameterSpec.Builder("rsakey", KeyProperties.PURPOSE_AGREE_KEY).build());
        KeyPair rsaKeyPair = kpg.genKeyPair();
        KeyAgreement ka = getKeyStoreKeyAgreement();

        try {
            ka.init(rsaKeyPair.getPrivate());
            fail("Initializing KeyAgreement with non-EC key should fail.");
        } catch (InvalidKeyException ike) {
            // Expected
        }
    }

    @Test
    public void testDoPhase_withoutInitialization_fails() throws Exception {
        KeyAgreement ka = getKeyStoreKeyAgreement();
        try {
            ka.doPhase(generateEphemeralServerKeyPair().getPublic(), true);
            fail("Should not be able to call doPhase without performing initialization first");
        } catch (IllegalStateException ise) {
            // Expected
        }
    }

    @Test
    public void testGenerateSecret_withoutSecondPartyKey_fails() throws Exception {
        KeyAgreement ka = getKeyStoreKeyAgreement();
        ka.init(generateEphemeralAndroidKeyPair().getPrivate());
        try {
            ka.generateSecret();
            fail("Should not be able to generate secret without other party key.");
        } catch (IllegalStateException ise) {
            // Expected
        }
    }

    @Test
    public void testDoPhase_multiparty_fails() throws Exception {
        // Multi-party key agreement is not supported by Keymint
        KeyAgreement ka = getKeyStoreKeyAgreement();
        ka.init(generateEphemeralAndroidKeyPair().getPrivate());
        try {
            ka.doPhase(generateEphemeralServerKeyPair().getPublic(), false);
            fail("Calling doPhase with lastPhase set to false should fail.");
        } catch (IllegalStateException ise) {
            // Expected
        }
        ka.doPhase(generateEphemeralServerKeyPair().getPublic(), true);
        try {
            ka.doPhase(generateEphemeralServerKeyPair().getPublic(), true);
            fail("Calling doPhase multiple times should fail.");
        } catch (IllegalStateException ise) {
            // Expected
        }
    }

    @Test
    @ApiTest(apis = "javax.crypto.KeyAgreement#doPhase")
    public void testDoPhase_withDifferentCurveKey_fails_withSecp256AndSecp224() throws Exception {
        KeyAgreement ka = getKeyStoreKeyAgreement();
        ka.init(generateEphemeralAndroidKeyPair(
                new ECGenParameterSpec("secp256r1")).getPrivate());
        assertThrows("Calling KeyAgreement.doPhase with EC key of different curves should throw an "
                + "InvalidKeyException.", InvalidKeyException.class, () -> {
                ka.doPhase(generateEphemeralAndroidKeyPair(
                        new ECGenParameterSpec("secp224r1")).getPublic(), true);
            });
    }

    @Test
    @ApiTest(apis = "javax.crypto.KeyAgreement#doPhase")
    public void testDoPhase_withDifferentCurveKey_fails_withX25519AndSecp224() throws Exception {
        // Due to key factory for x25519 not available we could not use
        // generateEphemeralAndroidKeyPair().
        KeyPairGenerator kpg =
                KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore");
        KeyGenParameterSpec.Builder specBuilder = new KeyGenParameterSpec.Builder(PRIVATE_KEY_ALIAS,
                KeyProperties.PURPOSE_AGREE_KEY)
                .setAlgorithmParameterSpec(new ECGenParameterSpec("x25519"));
        kpg.initialize(specBuilder.build());

        KeyPair kp = kpg.generateKeyPair();
        KeyAgreement ka = getKeyStoreKeyAgreement();
        /* TODO This case is temporary commented, because OpenSSL implementation does not implement
         * OpenSSLX25519PublicKey from XECKey interface (b/214203951). This case has to execute
         * once conscrypt implements OpenSSLX25519PublicKey from XECKey interface.
         * ka.init(kp.getPrivate());
         * assertThrows("Calling KeyAgreement.doPhase with XEC private key and EC public key should"
         *       + " throw an InvalidKeyException.", InvalidKeyException.class, () -> {
         *       ka.doPhase(generateEphemeralAndroidKeyPair(
         *               new ECGenParameterSpec("secp224r1")).getPublic(), true);
         *   });*/
        ka.init(generateEphemeralAndroidKeyPair(
                new ECGenParameterSpec("secp224r1")).getPrivate());
        assertThrows("Calling KeyAgreement.doPhase with EC private key and XEC public key should"
                + " throw an InvalidKeyException.", InvalidKeyException.class, () -> {
                ka.doPhase(kp.getPublic(), true);
            });
    }

    private static KeyPair generateEphemeralAndroidKeyPair() throws Exception {
        return generateEphemeralAndroidKeyPair(null);
    }

    private static KeyPair generateEphemeralAndroidKeyPair(ECGenParameterSpec spec)
            throws Exception {
        KeyPairGenerator kpg =
                KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore");
        KeyGenParameterSpec.Builder specBuilder = new KeyGenParameterSpec.Builder(PRIVATE_KEY_ALIAS,
                KeyProperties.PURPOSE_AGREE_KEY);
        if (spec != null) {
            specBuilder.setAlgorithmParameterSpec(spec);
        }
        kpg.initialize(specBuilder.build());

        KeyPair kp = kpg.generateKeyPair();

        KeyFactory factory = KeyFactory.getInstance(kp.getPrivate().getAlgorithm(),
                "AndroidKeyStore");
        KeyInfo keyInfo = null;
        try {
            keyInfo = factory.getKeySpec(kp.getPrivate(), KeyInfo.class);
        } catch (InvalidKeySpecException e) {
            // Not an Android KeyStore key.
            fail("Unable to get KeyInfo for created key.");
        }

        TestUtils.assertImplementedByKeyMintAfter(keyInfo, Attestation.KM_VERSION_KEYMINT_1);

        return kp;
    }

    private static KeyPair generateEphemeralServerKeyPair() throws GeneralSecurityException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC"); // Use default provider
        kpg.initialize(256);

        return kpg.generateKeyPair();
    }

    // Helper function allowing us to include information about Keystore2 likely not being available
    // in the test output.
    private static KeyAgreement getKeyStoreKeyAgreement() throws NoSuchProviderException {
        try {
            KeyAgreement ka = KeyAgreement.getInstance("ECDH", "AndroidKeyStore");
            return ka;
        } catch (NoSuchAlgorithmException nsa) {
            fail(
                    "AndroidKeyStore is missing an ECDH implementation, which is likely caused by"
                        + " keystore2 not being enabled. See b/160623310 for more information.");
            return null;
        }
    }
}
