/*
 * Copyright 2015 The Android Open Source Project
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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.fail;

import android.content.Context;
import android.keystore.cts.util.EmptyArray;
import android.keystore.cts.util.ImportedKey;
import android.keystore.cts.util.TestUtils;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Provider;
import java.security.PublicKey;
import java.security.Security;
import java.security.interfaces.RSAKey;
import java.security.spec.MGF1ParameterSpec;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

@RunWith(AndroidJUnit4.class)
public class RSACipherTest {

    private static final String EXPECTED_PROVIDER_NAME = TestUtils.EXPECTED_CRYPTO_OP_PROVIDER_NAME;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @Test
    public void testNoPaddingEncryptionAndDecryptionSucceedsWithInputShorterThanModulus()
            throws Exception {
        Provider provider = Security.getProvider(EXPECTED_PROVIDER_NAME);
        assertNotNull(provider);

        for (ImportedKey key : RSASignatureTest.importKatKeyPairs(getContext(),
                TestUtils.getMinimalWorkingImportParametersForCipheringWith(
                        "RSA/ECB/NoPadding",
                        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT,
                        false))) {
            try {
                PublicKey publicKey = key.getKeystoreBackedKeyPair().getPublic();
                PrivateKey privateKey = key.getKeystoreBackedKeyPair().getPrivate();
                BigInteger modulus = ((RSAKey) publicKey).getModulus();
                int modulusSizeBytes = (modulus.bitLength() + 7) / 8;

                // 1-byte long input for which we know the output
                byte[] input = new byte[] {1};
                // Because of how RSA works, the output is 1 (left-padded with zero bytes).
                byte[] expectedOutput = TestUtils.leftPadWithZeroBytes(input, modulusSizeBytes);

                Cipher cipher = Cipher.getInstance("RSA/ECB/NoPadding", provider);
                cipher.init(Cipher.ENCRYPT_MODE, publicKey);
                assertArrayEquals(expectedOutput, cipher.doFinal(input));

                cipher.init(Cipher.DECRYPT_MODE, privateKey);
                assertArrayEquals(expectedOutput, cipher.doFinal(input));
            } catch (Throwable e) {
                throw new RuntimeException("Failed for key " + key.getAlias(), e);
            }
        }
    }

    @Test
    public void testNoPaddingEncryptionSucceedsWithPlaintextOneSmallerThanModulus()
            throws Exception {
        Provider provider = Security.getProvider(EXPECTED_PROVIDER_NAME);
        assertNotNull(provider);

        for (ImportedKey key : RSASignatureTest.importKatKeyPairs(getContext(),
                TestUtils.getMinimalWorkingImportParametersForCipheringWith(
                        "RSA/ECB/NoPadding",
                        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT,
                        false))) {
            try {
                PublicKey publicKey = key.getKeystoreBackedKeyPair().getPublic();
                PrivateKey privateKey = key.getKeystoreBackedKeyPair().getPrivate();
                BigInteger modulus = ((RSAKey) publicKey).getModulus();

                // Plaintext is one smaller than the modulus
                byte[] plaintext =
                        TestUtils.getBigIntegerMagnitudeBytes(modulus.subtract(BigInteger.ONE));
                Cipher cipher = Cipher.getInstance("RSA/ECB/NoPadding", provider);
                cipher.init(Cipher.ENCRYPT_MODE, publicKey);
                byte[] ciphertext = cipher.doFinal(plaintext);
                cipher.init(Cipher.DECRYPT_MODE, privateKey);
                assertArrayEquals(plaintext, cipher.doFinal(ciphertext));
            } catch (Throwable e) {
                throw new RuntimeException("Failed for key " + key.getAlias(), e);
            }
        }
    }

    @Test
    public void testNoPaddingEncryptionFailsWithPlaintextEqualToModulus() throws Exception {
        Provider provider = Security.getProvider(EXPECTED_PROVIDER_NAME);
        assertNotNull(provider);

        for (ImportedKey key : RSASignatureTest.importKatKeyPairs(getContext(),
                TestUtils.getMinimalWorkingImportParametersForCipheringWith(
                        "RSA/ECB/NoPadding",
                        KeyProperties.PURPOSE_ENCRYPT ,
                        false))) {
            try {
                PublicKey publicKey = key.getKeystoreBackedKeyPair().getPublic();
                BigInteger modulus = ((RSAKey) publicKey).getModulus();

                // Plaintext is exactly the modulus
                byte[] plaintext = TestUtils.getBigIntegerMagnitudeBytes(modulus);
                Cipher cipher = Cipher.getInstance("RSA/ECB/NoPadding", provider);
                cipher.init(Cipher.ENCRYPT_MODE, publicKey);
                try {
                    byte[] ciphertext = cipher.doFinal(plaintext);
                    fail("Unexpectedly produced ciphertext (" + ciphertext.length + " bytes): "
                            + HexEncoding.encode(ciphertext));
                } catch (BadPaddingException | IllegalBlockSizeException expected) {}
            } catch (Throwable e) {
                throw new RuntimeException("Failed for key " + key.getAlias(), e);
            }
        }
    }

    @Test
    public void testNoPaddingEncryptionFailsWithPlaintextOneLargerThanModulus() throws Exception {
        Provider provider = Security.getProvider(EXPECTED_PROVIDER_NAME);
        assertNotNull(provider);

        for (ImportedKey key : RSASignatureTest.importKatKeyPairs(getContext(),
                TestUtils.getMinimalWorkingImportParametersForCipheringWith(
                        "RSA/ECB/NoPadding",
                        KeyProperties.PURPOSE_ENCRYPT,
                        false))) {
            try {
                PublicKey publicKey = key.getKeystoreBackedKeyPair().getPublic();
                BigInteger modulus = ((RSAKey) publicKey).getModulus();

                // Plaintext is one larger than the modulus
                byte[] plaintext =
                        TestUtils.getBigIntegerMagnitudeBytes(modulus.add(BigInteger.ONE));
                Cipher cipher = Cipher.getInstance("RSA/ECB/NoPadding", provider);
                cipher.init(Cipher.ENCRYPT_MODE, publicKey);
                try {
                    byte[] ciphertext = cipher.doFinal(plaintext);
                    fail("Unexpectedly produced ciphertext (" + ciphertext.length + " bytes): "
                            + HexEncoding.encode(ciphertext));
                } catch (BadPaddingException | IllegalBlockSizeException expected) {}
            } catch (Throwable e) {
                throw new RuntimeException("Failed for key " + key.getAlias(), e);
            }
        }
    }

    @Test
    public void testNoPaddingEncryptionFailsWithPlaintextOneByteLongerThanModulus()
            throws Exception {
        Provider provider = Security.getProvider(EXPECTED_PROVIDER_NAME);
        assertNotNull(provider);

        for (ImportedKey key : RSASignatureTest.importKatKeyPairs(getContext(),
                TestUtils.getMinimalWorkingImportParametersForCipheringWith(
                        "RSA/ECB/NoPadding",
                        KeyProperties.PURPOSE_ENCRYPT,
                        false))) {
            try {
                PublicKey publicKey = key.getKeystoreBackedKeyPair().getPublic();
                BigInteger modulus = ((RSAKey) publicKey).getModulus();

                // Plaintext is one byte longer than the modulus. The message is filled with zeros
                // (thus being 0 if treated as a BigInteger). This is on purpose, to check that the
                // Cipher implementation rejects such long message without comparing it to the value
                // of the modulus.
                byte[] plaintext = new byte[((modulus.bitLength() + 7) / 8) + 1];
                Cipher cipher = Cipher.getInstance("RSA/ECB/NoPadding", provider);
                cipher.init(Cipher.ENCRYPT_MODE, publicKey);
                try {
                    byte[] ciphertext = cipher.doFinal(plaintext);
                    fail("Unexpectedly produced ciphertext (" + ciphertext.length + " bytes): "
                            + HexEncoding.encode(ciphertext));
                } catch (IllegalBlockSizeException expected) {}
            } catch (Throwable e) {
                throw new RuntimeException("Failed for key " + key.getAlias(), e);
            }
        }
    }

    @Test
    public void testNoPaddingDecryptionFailsWithCiphertextOneByteLongerThanModulus()
            throws Exception {
        Provider provider = Security.getProvider(EXPECTED_PROVIDER_NAME);
        assertNotNull(provider);

        for (ImportedKey key : RSASignatureTest.importKatKeyPairs(getContext(),
                TestUtils.getMinimalWorkingImportParametersForCipheringWith(
                        "RSA/ECB/NoPadding",
                        KeyProperties.PURPOSE_DECRYPT,
                        false))) {
            try {
                PrivateKey privateKey = key.getKeystoreBackedKeyPair().getPrivate();
                BigInteger modulus = ((RSAKey) privateKey).getModulus();

                // Ciphertext is one byte longer than the modulus. The message is filled with zeros
                // (thus being 0 if treated as a BigInteger). This is on purpose, to check that the
                // Cipher implementation rejects such long message without comparing it to the value
                // of the modulus.
                byte[] ciphertext = new byte[((modulus.bitLength() + 7) / 8) + 1];
                Cipher cipher = Cipher.getInstance("RSA/ECB/NoPadding", EXPECTED_PROVIDER_NAME);
                cipher.init(Cipher.DECRYPT_MODE, privateKey);
                try {
                    byte[] plaintext = cipher.doFinal(ciphertext);
                    fail("Unexpectedly produced plaintext (" + ciphertext.length + " bytes): "
                            + HexEncoding.encode(plaintext));
                } catch (IllegalBlockSizeException expected) {}
            } catch (Throwable e) {
                throw new RuntimeException("Failed for key " + key.getAlias(), e);
            }
        }
    }

    @Test
    public void testNoPaddingWithZeroMessage() throws Exception {
        Provider provider = Security.getProvider(EXPECTED_PROVIDER_NAME);
        assertNotNull(provider);

        for (ImportedKey key : RSASignatureTest.importKatKeyPairs(getContext(),
                TestUtils.getMinimalWorkingImportParametersForCipheringWith(
                        "RSA/ECB/NoPadding",
                        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT,
                        false))) {
            try {
                PublicKey publicKey = key.getKeystoreBackedKeyPair().getPublic();
                PrivateKey privateKey = key.getKeystoreBackedKeyPair().getPrivate();

                byte[] plaintext = EmptyArray.BYTE;
                Cipher cipher = Cipher.getInstance("RSA/ECB/NoPadding", EXPECTED_PROVIDER_NAME);
                cipher.init(Cipher.ENCRYPT_MODE, publicKey);
                byte[] ciphertext = cipher.doFinal(plaintext);
                // Ciphertext should be all zero bytes
                byte[] expectedCiphertext = new byte[(TestUtils.getKeySizeBits(publicKey) + 7) / 8];
                assertArrayEquals(expectedCiphertext, ciphertext);

                cipher.init(Cipher.DECRYPT_MODE, privateKey);
                // Decrypted plaintext should also be all zero bytes
                byte[] expectedPlaintext = new byte[expectedCiphertext.length];
                assertArrayEquals(expectedPlaintext, cipher.doFinal(ciphertext));
            } catch (Throwable e) {
                throw new RuntimeException("Failed for key " + key.getAlias(), e);
            }
        }
    }

    @Test
    @RequiresFlagsEnabled(android.security.Flags.FLAG_MGF1_DIGEST_SETTER_V2)
    public void testRsaOaepDecryptWithWrongMGF1DigestFails() throws Exception {
        Provider provider = Security.getProvider(EXPECTED_PROVIDER_NAME);
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA", "AndroidKeyStore");
        generator.initialize(new KeyGenParameterSpec.Builder("test_1",
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_ECB)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setMgf1Digests(KeyProperties.DIGEST_SHA256)
                .build());
        KeyPair keyPair = generator.generateKeyPair();

        Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
        assertThrows("RSA cipher should fail to initialize if MGF1 digest specified while key"
                        + " generation is different than MGF1 digest specified in initialization"
                        + " parameters.", InvalidKeyException.class,
                () -> cipher.init(Cipher.DECRYPT_MODE, keyPair.getPrivate(),
                    new OAEPParameterSpec("SHA-256", "MGF1",
                            new MGF1ParameterSpec("SHA-1"), PSource.PSpecified.DEFAULT)));
    }
}
