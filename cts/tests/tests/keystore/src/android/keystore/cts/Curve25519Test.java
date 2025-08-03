/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

import android.content.Context;
import android.keystore.cts.util.ImportedKey;
import android.keystore.cts.util.TestUtils;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.security.keystore.KeyProtection;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Signature;
import java.security.SignatureException;
import java.security.interfaces.EdECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.NamedParameterSpec;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.KeyAgreement;

@RunWith(AndroidJUnit4.class)
public class Curve25519Test {
    private void deleteEntry(String entry) {
        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.deleteEntry(entry);
        } catch (KeyStoreException e) {
            // Skipped
        }
    }

    private Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @Test
    public void x25519KeyAgreementTest() throws NoSuchAlgorithmException, NoSuchProviderException,
            InvalidAlgorithmParameterException, InvalidKeySpecException, InvalidKeyException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC", "AndroidKeyStore");
        // Aliases for both keys.
        final String firstKeyAlias = "x25519-alias";
        deleteEntry(firstKeyAlias);
        final String secondKeyAlias = "x25519-alias-second";
        deleteEntry(secondKeyAlias);

        // Generate first x25519 key pair.
        KeyGenParameterSpec firstKeySpec = new KeyGenParameterSpec.Builder(firstKeyAlias,
                KeyProperties.PURPOSE_AGREE_KEY)
                .setAlgorithmParameterSpec(new ECGenParameterSpec("x25519")).build();
        kpg.initialize(firstKeySpec);
        KeyPair firstKeyPair = kpg.generateKeyPair();

        // Generate second x25519 key pair.
        KeyGenParameterSpec secondKeySpec = new KeyGenParameterSpec.Builder(secondKeyAlias,
                KeyProperties.PURPOSE_AGREE_KEY)
                .setAlgorithmParameterSpec(new ECGenParameterSpec("x25519")).build();
        kpg.initialize(secondKeySpec);
        KeyPair secondKeyPair = kpg.generateKeyPair();

        // Attempt a key agreement with the private key from the first key pair and the public
        // key from the second key pair.
        KeyAgreement secondKa = KeyAgreement.getInstance("XDH");
        secondKa.init(firstKeyPair.getPrivate());
        secondKa.doPhase(secondKeyPair.getPublic(), true);
        byte[] secondSecret = secondKa.generateSecret();

        // Attempt a key agreement "the other way around": using the private key from the second
        // key pair and the public key from the first key pair.
        KeyAgreement firstKa = KeyAgreement.getInstance("XDH");
        firstKa.init(secondKeyPair.getPrivate());
        firstKa.doPhase(firstKeyPair.getPublic(), true);
        byte[] firstSecret = firstKa.generateSecret();

        // Both secrets being equal means the key agreement was successful.
        assertThat(Arrays.compare(firstSecret, secondSecret)).isEqualTo(0);
    }

    @Test
    @ApiTest(apis = {"java.security.KeyStore#setEntry", "javax.crypto.KeyAgreement#doPhase"})
    public void x25519KeyImportAndAgreementTest() throws Exception {
        final String alias = "import-x25519";
        deleteEntry(alias);

        KeyProtection importParams = new KeyProtection.Builder(KeyProperties.PURPOSE_AGREE_KEY)
                .build();
        ImportedKey importedKey = TestUtils.importIntoAndroidKeyStore(
                    alias,
                    getContext(),
                    R.raw.ec_key8_x25519,
                    R.raw.ec_key8_x25519_cert,
                    importParams);

        assertThat(importedKey).isNotNull();
        //Second key generate from AndroidOpenSSL
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("XDH");
        KeyPair secondKeyPair = kpg.generateKeyPair();

        // Attempt a key agreement with the private key from the first key pair and the public
        // key from the second key pair.
        KeyAgreement secondKa = KeyAgreement.getInstance("XDH");
        secondKa.init(importedKey.getKeystoreBackedKeyPair().getPrivate());
        secondKa.doPhase(secondKeyPair.getPublic(), true);
        byte[] secondSecret = secondKa.generateSecret();

        // Attempt a key agreement "the other way around": using the private key from the second
        // key pair and the public key from the first key pair.
        KeyAgreement firstKa = KeyAgreement.getInstance("XDH");
        firstKa.init(secondKeyPair.getPrivate());
        firstKa.doPhase(importedKey.getKeystoreBackedKeyPair().getPublic(), true);
        byte[] firstSecret = firstKa.generateSecret();

        // Both secrets being equal means the key agreement was successful.
        assertThat(Arrays.compare(firstSecret, secondSecret)).isEqualTo(0);
    }

    @Test
    public void ed25519KeyGenerationAndSigningTest()
            throws NoSuchAlgorithmException, NoSuchProviderException,
            InvalidAlgorithmParameterException, InvalidKeyException, SignatureException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC", "AndroidKeyStore");
        final String alias = "ed25519-alias";
        deleteEntry(alias);

        KeyGenParameterSpec keySpec = new KeyGenParameterSpec.Builder(alias,
                KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                .setAlgorithmParameterSpec(new ECGenParameterSpec("ed25519"))
                .setDigests(KeyProperties.DIGEST_NONE).build();
        kpg.initialize(keySpec);

        KeyPair kp = kpg.generateKeyPair();
        assertThat(kp.getPublic()).isInstanceOf(EdECPublicKey.class);

        byte[] data = "helloxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx".getBytes();
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(kp.getPrivate());
        signer.update(data);
        byte[] sigBytes = signer.sign();
        assertThat(sigBytes.length).isEqualTo(64);
        EdECPublicKey publicKey = (EdECPublicKey) kp.getPublic();
        android.util.Log.i("Curve25519Test", "Manually validate: Payload "
                + Base64.getEncoder().encodeToString(data) + " encoded key: "
                + Base64.getEncoder().encodeToString(kp.getPublic().getEncoded())
                + " signature: " + Base64.getEncoder().encodeToString(sigBytes));

        //TODO: Verify signature over the data when Conscrypt supports validating Ed25519
        // signatures.
    }

    @Test
    public void testX25519CannotBeUsedForSigning()
            throws NoSuchAlgorithmException, NoSuchProviderException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC", "AndroidKeyStore");
        final String alias = "x25519-baduse-alias";
        deleteEntry(alias);

        KeyGenParameterSpec keySpec = new KeyGenParameterSpec.Builder(alias,
                KeyProperties.PURPOSE_SIGN | KeyProperties.PURPOSE_VERIFY)
                .setAlgorithmParameterSpec(new ECGenParameterSpec("x25519")).build();

        assertThrows(InvalidAlgorithmParameterException.class, () -> kpg.initialize(keySpec));
    }

    @Test
    public void testEd25519CannotBeUsedForKeyExchange() throws NoSuchAlgorithmException,
            NoSuchProviderException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC", "AndroidKeyStore");
        final String alias = "ed25519-baduse-alias";
        deleteEntry(alias);

        KeyGenParameterSpec keySpec = new KeyGenParameterSpec.Builder(alias,
                KeyProperties.PURPOSE_AGREE_KEY)
                .setAlgorithmParameterSpec(new ECGenParameterSpec("ed25519")).build();

        assertThrows(InvalidAlgorithmParameterException.class, () -> kpg.initialize(keySpec));
    }

    @Test
    public void x25519CannotCreateKeyUsingKPGWithNamedParameterSpec()
            throws NoSuchAlgorithmException, NoSuchProviderException,
            InvalidAlgorithmParameterException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("XDH", "AndroidKeyStore");

        NamedParameterSpec paramSpec = new NamedParameterSpec("X25519");
        try {
            kpg.initialize(paramSpec);
            fail("Should not be able to generate keys using NamedParameterSpec");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).contains("cannot be initialized using NamedParameterSpec");
        }
    }

    @Test
    public void ed25519CannotCreateKeyUsingKPGWithNamedParameterSpec()
            throws NoSuchAlgorithmException, NoSuchProviderException,
            InvalidAlgorithmParameterException {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("XDH", "AndroidKeyStore");

        NamedParameterSpec paramSpec = new NamedParameterSpec("Ed25519");
        try {
            kpg.initialize(paramSpec);
            fail("Should not be able to generate keys using NamedParameterSpec");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage()).contains("cannot be initialized using NamedParameterSpec");
        }
    }
}
