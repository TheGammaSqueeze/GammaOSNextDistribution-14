/*
 * Copyright (C) 2015 The Android Open Source Project
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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.keystore.cts.util.ImportedKey;
import android.keystore.cts.util.TestUtils;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.security.keystore.KeyProtection;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.internal.util.HexDump;

import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.ASN1Sequence;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Security;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.ECGenParameterSpec;
import java.util.Arrays;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

@RunWith(AndroidJUnit4.class)
public class ECDSASignatureTest {

    private static final String TAG = "ECDSASignatureTest";

    private Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @Test
    public void testNONEwithECDSATruncatesInputToFieldSize() throws Exception {
        for (ImportedKey key : importKatKeyPairs("NONEwithECDSA")) {
            try {
                assertNONEwithECDSATruncatesInputToFieldSize(key.getKeystoreBackedKeyPair());
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + key.getAlias(), e);
            }
        }
    }

    private void assertNONEwithECDSATruncatesInputToFieldSize(KeyPair keyPair)
            throws Exception {
        int keySizeBits = TestUtils.getKeySizeBits(keyPair.getPublic());
        byte[] message = new byte[(keySizeBits * 3) / 8];
        for (int i = 0; i < message.length; i++) {
            message[i] = (byte) (i + 1);
        }

        Signature signature = Signature.getInstance("NONEwithECDSA");
        signature.initSign(keyPair.getPrivate());
        assertSame(Security.getProvider(SignatureTest.EXPECTED_PROVIDER_NAME),
                signature.getProvider());
        signature.update(message);
        byte[] sigBytes = signature.sign();

        signature = Signature.getInstance(signature.getAlgorithm(), signature.getProvider());
        signature.initVerify(keyPair.getPublic());

        // Verify the full-length message
        signature.update(message);
        assertTrue(signature.verify(sigBytes));

        // Verify the message truncated to field size
        signature.update(message, 0, (keySizeBits + 7) / 8);
        assertTrue(signature.verify(sigBytes));

        // Verify message truncated to one byte shorter than field size -- this should fail
        signature.update(message, 0, (keySizeBits / 8) - 1);
        assertFalse(signature.verify(sigBytes));
    }

    @Test
    public void testNONEwithECDSASupportsMessagesShorterThanFieldSize() throws Exception {
        for (ImportedKey key : importKatKeyPairs("NONEwithECDSA")) {
            try {
                assertNONEwithECDSASupportsMessagesShorterThanFieldSize(
                        key.getKeystoreBackedKeyPair());
            } catch (Throwable e) {
                throw new RuntimeException("Failed for " + key.getAlias(), e);
            }
        }
    }

    /* Duplicate nonces can leak the ECDSA private key, even if each nonce is only used once per
     * keypair. See Brengel & Rossow 2018 ( https://doi.org/10.1007/978-3-030-00470-5_29 ).
     */
    @Test
    public void testECDSANonceReuse() throws Exception {
        testECDSANonceReuse_Helper(false /* useStrongbox */, "secp224r1");
        testECDSANonceReuse_Helper(false /* useStrongbox */, "secp256r1");
        testECDSANonceReuse_Helper(false /* useStrongbox */, "secp384r1");
        testECDSANonceReuse_Helper(false /* useStrongbox */, "secp521r1");

        if (TestUtils.hasStrongBox(getContext())) {
            testECDSANonceReuse_Helper(true /* useStrongbox */, "secp256r1");
        }
    }

    private void testECDSANonceReuse_Helper(boolean useStrongbox, String curve)
            throws NoSuchAlgorithmException, NoSuchProviderException,
                    InvalidAlgorithmParameterException, InvalidKeyException, SignatureException,
                    IOException {
        KeyPair kp = generateKeyPairForNonceReuse_Helper(useStrongbox, curve);
        /* An ECDSA signature is a pair of integers (r,s).
         *
         * Let G be the curve base point, let n be the order of G, and let k be a random
         * per-signature nonce.
         *
         * ECDSA defines:
         *     r := x_coordinate( k x G) mod n
         *
         * It follows that if r_1 == r_2 mod n, then k_1 == k_2 mod n. That is, congruent r
         * values mod n imply a compromised private key.
         */
        Map<String, byte[]> rValueStrToSigMap = new HashMap<String, byte[]>();
        for (byte i = 1; i <= 100; i++) {
            byte[] message = new byte[] {i};
            byte[] signature = computeSignatureForNonceReuse_Helper(message, kp);
            byte[] rValue = extractRValueFromEcdsaSignature_Helper(signature);
            String rValueStr = HexDump.toHexString(rValue);
            if (!rValueStrToSigMap.containsKey(rValueStr)) {
                rValueStrToSigMap.put(rValueStr, signature);
                continue;
            }
            // Duplicate nonces.
            Log.i(
                    TAG,
                    "Found duplicate nonce after "
                            + Integer.toString(rValueStrToSigMap.size())
                            + " ECDSA signatures.");

            byte[] otherSig = rValueStrToSigMap.get(rValueStr);
            String otherSigStr = HexDump.toHexString(otherSig);
            String currentSigStr = HexDump.toHexString(signature);
            fail(
                    "Duplicate ECDSA nonce detected."
                            + " Curve: " + curve
                            + " Strongbox: " + Boolean.toString(useStrongbox)
                            + " Signature 1: "
                            + otherSigStr
                            + " Signature 2: "
                            + currentSigStr);
        }
    }

    private KeyPair generateKeyPairForNonceReuse_Helper(boolean useStrongbox,
            String curve)
            throws NoSuchAlgorithmException, NoSuchProviderException,
                    InvalidAlgorithmParameterException {
        // We use a generated key instead of an imported key since key generation drains the entropy
        // pool and thus increase the chance of duplicate nonces.
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC", "AndroidKeyStore");
        generator.initialize(
                new KeyGenParameterSpec.Builder("test1", KeyProperties.PURPOSE_SIGN)
                        .setAlgorithmParameterSpec(new ECGenParameterSpec(curve))
                        .setDigests(KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA256)
                        .setIsStrongBoxBacked(useStrongbox)
                        .build());
        KeyPair kp = generator.generateKeyPair();
        return kp;
    }

    /**
     * Extract the R value from the ECDSA signature.
     *
     * @param sigBytes ASN.1 encoded ECDSA signature.
     * @return The r value extracted from the signature.
     * @throws IOException
     */
    private byte[] extractRValueFromEcdsaSignature_Helper(byte[] sigBytes) throws IOException {
        /* ECDSA Signature format (X9.62 Section 6.5):
         * ECDSA-Sig-Value ::= SEQUENCE {
         *      r INTEGER,
         *      s INTEGER
         *  }
         */
        ASN1Primitive sig1prim = ASN1Primitive.fromByteArray(sigBytes);
        Enumeration secEnum = ((ASN1Sequence) sig1prim).getObjects();
        ASN1Primitive seqObj = (ASN1Primitive) secEnum.nextElement();
        // The first ASN1 object is the r value.
        byte[] r = seqObj.getEncoded();
        return r;
    }

    private byte[] computeSignatureForNonceReuse_Helper(byte[] message, KeyPair keyPair)
            throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        Signature signature = Signature.getInstance("NONEwithECDSA");
        signature.initSign(keyPair.getPrivate());
        signature.update(message);
        byte[] sigBytes = signature.sign();
        return sigBytes;
    }

    private void assertNONEwithECDSASupportsMessagesShorterThanFieldSize(KeyPair keyPair)
            throws Exception {
        int keySizeBits = TestUtils.getKeySizeBits(keyPair.getPublic());
        byte[] message = new byte[(keySizeBits * 3 / 4) / 8];
        for (int i = 0; i < message.length; i++) {
            message[i] = (byte) (i + 1);
        }

        Signature signature = Signature.getInstance("NONEwithECDSA");
        signature.initSign(keyPair.getPrivate());
        assertSame(Security.getProvider(SignatureTest.EXPECTED_PROVIDER_NAME),
                signature.getProvider());
        signature.update(message);
        byte[] sigBytes = signature.sign();

        signature = Signature.getInstance(signature.getAlgorithm(), signature.getProvider());
        signature.initVerify(keyPair.getPublic());

        // Verify the message
        signature.update(message);
        assertTrue(signature.verify(sigBytes));

        // Assert that the message is left-padded with zero bits
        byte[] fullLengthMessage = TestUtils.leftPadWithZeroBytes(message, keySizeBits / 8);
        signature.update(fullLengthMessage);
        assertTrue(signature.verify(sigBytes));
    }

    private Collection<ImportedKey> importKatKeyPairs(String signatureAlgorithm)
            throws Exception {
        KeyProtection params =
                TestUtils.getMinimalWorkingImportParametersForSigningingWith(signatureAlgorithm);
        return importKatKeyPairs(getContext(), params);
    }

    static Collection<ImportedKey> importKatKeyPairs(
            Context context, KeyProtection importParams) throws Exception {
        return Arrays.asList(new ImportedKey[] {
                TestUtils.importIntoAndroidKeyStore("testECsecp224r1", context,
                        R.raw.ec_key3_secp224r1_pkcs8, R.raw.ec_key3_secp224r1_cert, importParams),
                TestUtils.importIntoAndroidKeyStore("testECsecp256r1", context,
                        R.raw.ec_key4_secp256r1_pkcs8, R.raw.ec_key4_secp256r1_cert, importParams),
                TestUtils.importIntoAndroidKeyStore("testECsecp384r1", context,
                        R.raw.ec_key5_secp384r1_pkcs8, R.raw.ec_key5_secp384r1_cert, importParams),
                TestUtils.importIntoAndroidKeyStore("testECsecp521r1", context,
                        R.raw.ec_key6_secp521r1_pkcs8, R.raw.ec_key6_secp521r1_cert, importParams),
                });
    }
}
