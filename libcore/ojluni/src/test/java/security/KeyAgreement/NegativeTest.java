/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @bug 8200219
 * @summary Negative tests for Key related Test with DiffieHellman, ECDH, XDH.
 *          It Tests,
 *          Use modified encoding while generating Public/Private Keys
 *          Short, long, unsupported keysize
 *          Invalid Algo names including Null
 *          Invalid provider names including Null
 *          Invalid curve names
 *          Invalid spec usage
 *  Arguments order <KeyExchangeAlgorithm> <Provider> <KeyGenAlgorithm>
 *                  <keySize> <Curve*>
 * @library /test/lib
 * @run main NegativeTest DiffieHellman SunJCE DiffieHellman 1024
 * @run main NegativeTest ECDH SunEC EC 256
 * @run main NegativeTest XDH SunEC XDH 255 X25519
 * @run main NegativeTest XDH SunEC XDH 448 X448
 */
package test.java.security.KeyAgreement;

import java.math.BigInteger;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidParameterException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Security;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.NamedParameterSpec;
import java.security.spec.KeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.security.spec.XECPrivateKeySpec;
import java.security.spec.XECPublicKeySpec;
import java.util.Arrays;
import java.util.List;

import javax.crypto.KeyAgreement;
import org.testng.annotations.Test;
import static org.junit.Assert.fail;

public class NegativeTest {

    @Test
    public void testDHNegative() throws Exception {
        String kaAlgo = "DiffieHellman";
        String provider = "BC";
        String kpgAlgo = "DiffieHellman";
        int keySize = 1024;
        String kpgInit = "DiffieHellman";
        testModifiedKeyEncodingTest(provider, kpgAlgo, kpgInit);
        testInvalidKeyLen(provider, kaAlgo, kpgAlgo, kpgInit);
        testInvalidKpgAlgo(provider, kaAlgo, keySize);
        testInvalidKaAlgo(provider, kpgAlgo, keySize);
        testInvalidProvider(kaAlgo, kpgAlgo, keySize);
    }

    @Test
    public void testECDHNegative() throws Exception {
        String kaAlgo = "ECDH";
        String provider = "AndroidOpenSSL";
        String kpgAlgo = "EC";
        int keySize = 256;
        String kpgInit = "EC";
        testModifiedKeyEncodingTest(provider, kpgAlgo, kpgInit);
        testInvalidKeyLen(provider, kaAlgo, kpgAlgo, kpgInit);
        testInvalidKpgAlgo(provider, kaAlgo, keySize);
        testInvalidKaAlgo(provider, kpgAlgo, keySize);
        testInvalidProvider(kaAlgo, kpgAlgo, keySize);
        testNamedParameter(provider, kpgAlgo);
    }

    // BEGIN Android-removed: XDH is not yet supported
    /*
    @Test
    public void testXDHNegative() throws Exception {
        String kaAlgo = "XDH";
        String provider = "AndroidOpenSSL";
        String kpgAlgo = "XDH";
        List<Integer> keySizes = Arrays.asList(256, 448);
        List<String> kpgInits = Arrays.asList("X25519", "X448");
        for (int i = 0; i < keySizes.size(); i++){
            testModifiedKeyEncodingTest(provider, kpgAlgo, kpgInits.get(i));
            testInvalidKeyLen(provider, kaAlgo, kpgAlgo, kpgInits.get(i));
            testInvalidKpgAlgo(provider, kaAlgo, keySizes.get(i));
            testInvalidKaAlgo(provider, kpgAlgo, keySizes.get(i));
            testInvalidProvider(kaAlgo, kpgAlgo, keySizes.get(i));
            testInvalidSpec(provider, kpgAlgo, kpgInits.get(i));
            testInCompatibleSpec(provider, kpgAlgo, kpgInits.get(i));
        }
        testNamedParameter(provider, kpgAlgo);
    }
     */
    // END Android-removed: XDH is not yet supported

    /**
     * Generate keyPair based on KeyPairGenerator algorithm.
     */
    private static KeyPair genKeyPair(String provider, String kpgAlgo,
            String kpgInit) throws Exception {

        KeyPairGenerator kpg = KeyPairGenerator.getInstance(kpgAlgo,
                Security.getProvider(provider));
        switch (kpgInit) {
            case "DiffieHellman":
                kpg.initialize(512);
                break;
            case "EC":
                kpg.initialize(256);
                break;
            case "X25519":
                kpg.initialize(255);
                break;
            case "X448":
                kpg.initialize(448);
                break;
            default:
                fail("Invalid Algo name " + kpgInit);
        }
        return kpg.generateKeyPair();
    }

    private static void testModifiedKeyEncodingTest(String provider,
            String kpgAlgo, String kpgInit) throws Exception {

        KeyFactory kf = KeyFactory.getInstance(kpgAlgo, provider);
        KeyPair kp = genKeyPair(provider, kpgAlgo, kpgInit);
        // Test modified PrivateKey encoding
        byte[] encoded = kp.getPrivate().getEncoded();
        byte[] modified = modifyEncoded(encoded);
        PKCS8EncodedKeySpec priSpec = new PKCS8EncodedKeySpec(modified);
        try {
            // Generate PrivateKey with modified encoding
            kf.generatePrivate(priSpec);
            fail("testModifiedKeyTest should fail but passed.");
        } catch (InvalidKeySpecException e) {
        }
        // Test modified PublicKey encoding
        encoded = kp.getPublic().getEncoded();
        modified = modifyEncoded(encoded);
        X509EncodedKeySpec pubSpec = new X509EncodedKeySpec(modified);
        try {
            // Generate PublicKey with modified encoding
            kf.generatePublic(pubSpec);
            fail("testModifiedKeyTest should fail but passed.");
        } catch (InvalidKeySpecException e) {
        }
    }

    /**
     * Test with all Invalid key length.
     */
    private static void testInvalidKeyLen(String provider, String kaAlgo,
            String kpgAlgo, String kpgInit) throws Exception {

        for (int keySize : selectInvalidKeylength(kpgInit)) {
            try {
                startKeyAgreement(provider, kaAlgo, kpgAlgo, keySize);
                fail("testInvalidKeyLen should fail but passed: " + kaAlgo + ", " + Integer.toString(keySize));
            } catch (InvalidParameterException e) {
            } catch (IllegalArgumentException e) {
            }
        }
    }

    /**
     * Test with all Invalid KeyPairGenerator algorithms.
     */
    private static void testInvalidKpgAlgo(String provider, String algo,
            int keySize) throws Exception {

        for (String kpgAlgo : new String[]{null, " ", "", "NoSuchAlgorithm"}) {
            try {
                startKeyAgreement(provider, algo, kpgAlgo, keySize);
                fail("testInvalidKpgAlgo should fail but passed.");
            } catch (NoSuchAlgorithmException e) {
            } catch (NullPointerException e) {
                if (kpgAlgo == null) {
                    continue;
                }
                fail("Unknown failure in testInvalidKpgAlgo.");
            }
        }
    }

    /**
     * Test with all Invalid KeyAgreement algorithms.
     */
    private static void testInvalidKaAlgo(String provider, String kpgAlgo,
            int keySize) throws Exception {

        for (String algo : new String[]{null, " ", "", "NoSuchAlgorithm"}) {
            try {
                startKeyAgreement(provider, algo, kpgAlgo, keySize);
                fail("testInvalidKaAlgo should fail but passed.");
            } catch (NoSuchAlgorithmException e) {
            } catch (NullPointerException e) {
                if (algo == null) {
                    continue;
                }
                fail("Unknown failure in testInvalidKaAlgo.");
            }
        }
    }

    /**
     * Test with all Invalid Provider names.
     */
    private static void testInvalidProvider(String kaAlgo, String kpgAlgo,
            int keySize) throws Exception {

        for (String provider : new String[]{null, " ", "", "NoSuchProvider"}) {
            try {
                startKeyAgreement(provider, kaAlgo, kpgAlgo, keySize);
                fail("testInvalidProvider should fail but passed.");
            } catch (NoSuchProviderException e) {
            } catch (IllegalArgumentException e) {
            }
        }
    }

    /**
     * Test for (in)valid curve names as argument to NamedParameterSpec
     */
    private static void testNamedParameter(String provider, String kpgAlgo)
            throws Exception {

        for (String name : new String[]{null, " ", "", "NoSuchCurve"}) {
            try {
                NamedParameterSpec spec = new NamedParameterSpec(name);
                KeyPairGenerator kpg
                        = KeyPairGenerator.getInstance(kpgAlgo, provider);
                kpg.initialize(spec);
                kpg.generateKeyPair();
                fail("testNamedParameter should fail but passed.");
            } catch (NullPointerException e) {
                if (name == null) {
                    continue;
                }
                fail("Unknown failure in testNamedParameter.");
            } catch (InvalidAlgorithmParameterException e) {
            }
        }
    }

    /**
     * Test to generate Public/Private keys using (in)valid coordinate/scalar.
     */
    private static void testInvalidSpec(String provider,
            String kpgAlgo, String curve) throws Exception {

        NamedParameterSpec spec = new NamedParameterSpec(curve);
        KeyFactory kf = KeyFactory.getInstance(kpgAlgo, provider);
        int validLen = curve.equalsIgnoreCase("X448") ? 56 : 32;
        for (byte[] scalarBytes : new byte[][]{null, new byte[]{},
                new byte[32], new byte[56], new byte[65535]}) {
            try {
                KeySpec privateSpec = new XECPrivateKeySpec(spec, scalarBytes);
                kf.generatePrivate(privateSpec);
                if (scalarBytes.length != validLen) {
                    fail(String.format("testInvalidSpec "
                            + "should fail but passed when Scalar bytes length "
                            + "!= %s for curve %s", validLen, curve));
                }
            } catch (NullPointerException e) {
                if (scalarBytes == null) {
                    continue;
                }
                fail(e.getMessage());
            } catch (InvalidKeySpecException e) {
                if (scalarBytes.length != validLen) {
                    continue;
                }
                fail(e.getMessage());
            }
        }
        for (BigInteger coordinate : new BigInteger[]{null, BigInteger.ZERO,
                BigInteger.ONE, new BigInteger("2").pow(255),
                new BigInteger("2").pow(448)}) {
            try {
                KeySpec publicSpec = new XECPublicKeySpec(spec, coordinate);
                kf.generatePublic(publicSpec);
            } catch (NullPointerException e) {
                if (coordinate == null) {
                    continue;
                }
                fail(e.getMessage());
            }
        }
    }

    private static void testInCompatibleSpec(String provider,
            String kpgAlgo, String curve) throws Exception {

        int validLen = curve.equalsIgnoreCase("X448") ? 56 : 32;
        NamedParameterSpec spec = new NamedParameterSpec(curve);
        KeyFactory kf = KeyFactory.getInstance(kpgAlgo, provider);
        KeySpec privateSpec = new XECPrivateKeySpec(spec, new byte[validLen]);
        KeySpec publicSpec = new XECPublicKeySpec(spec, BigInteger.ONE);
        try {
            kf.generatePrivate(publicSpec);
            fail("testInCompatibleSpec should fail but passed.");
        } catch (InvalidKeySpecException e) {
        }
        try {
            kf.generatePublic(privateSpec);
            fail("testInCompatibleSpec should fail but passed.");
        } catch (InvalidKeySpecException e) {
        }
    }

    /**
     * Perform KeyAgreement operation.
     */
    private static void startKeyAgreement(String provider, String kaAlgo,
            String kpgAlgo, int keySize) throws Exception {

        KeyPairGenerator kpg = KeyPairGenerator.getInstance(kpgAlgo, provider);
        kpg.initialize(keySize);
        KeyPair kp = kpg.generateKeyPair();
        KeyAgreement ka = KeyAgreement.getInstance(kaAlgo, provider);
        ka.init(kp.getPrivate());
        ka.doPhase(kp.getPublic(), true);
        ka.generateSecret();
    }

    /**
     * Return manipulated encoded bytes.
     */
    private static byte[] modifyEncoded(byte[] encoded) {

        byte[] copy = Arrays.copyOf(encoded, encoded.length);
        for (int i = 0; i < copy.length; i++) {
            copy[i] = (byte) ~copy[i];
        }
        return copy;
    }

    /**
     * Select invalid key sizes for different Key generation algorithms.
     */
    private static int[] selectInvalidKeylength(String kpgInit) {

        int[] keySize = new int[]{};
        switch (kpgInit) {
            case "DiffieHellman":
                keySize = new int[]{100};
                break;
            case "EC":
            case "X25519":
                keySize = new int[]{100, 300};
                break;
            case "X448":
                keySize = new int[]{100, 500};
                break;
            default:
                fail("Invalid Algo name " + kpgInit);
        }
        return keySize;
    }
}