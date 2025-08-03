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
 * @bug 8184359
 * @summary KeyLength support test for DiffieHellman, EC, XDH.
 *  Arguments order <KeyExchangeAlgorithm> <Provider> <KeyGenAlgorithm> <keyLen>
 * @library /test/lib
 * @build jdk.test.lib.Convert
 * @run main KeySizeTest DiffieHellman SunJCE DiffieHellman 512
 * @run main KeySizeTest DiffieHellman SunJCE DiffieHellman 768
 * @run main KeySizeTest DiffieHellman SunJCE DiffieHellman 832
 * @run main KeySizeTest DiffieHellman SunJCE DiffieHellman 1024
 * @run main KeySizeTest DiffieHellman SunJCE DiffieHellman 2048
 * @run main KeySizeTest DiffieHellman SunJCE DiffieHellman 3072
 * @run main KeySizeTest DiffieHellman SunJCE DiffieHellman 4096
 * @run main KeySizeTest DiffieHellman SunJCE DiffieHellman 6144
 * @run main KeySizeTest DiffieHellman SunJCE DiffieHellman 8192
 * @run main KeySizeTest ECDH SunEC EC 128
 * @run main KeySizeTest ECDH SunEC EC 192
 * @run main KeySizeTest ECDH SunEC EC 256
 * @run main KeySizeTest XDH SunEC XDH 255
 * @run main KeySizeTest XDH SunEC XDH 448
 */
package test.java.security.KeyAgreement;

import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.XECPrivateKey;
import java.security.interfaces.XECPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.security.spec.NamedParameterSpec;
import java.util.Arrays;
import java.util.List;

import javax.crypto.KeyAgreement;
import javax.crypto.interfaces.DHPrivateKey;
import javax.crypto.interfaces.DHPublicKey;

import org.testng.annotations.Test;
import static org.junit.Assert.fail;

import android.platform.test.annotations.LargeTest;

public class KeySizeTest {

    List<Integer> sizesDH = Arrays.asList(512, 768, 832, 1024, 2048);
    List<Integer> sizesECDH = Arrays.asList(224, 256, 384, 521);
    List<Integer> sizesXDH = Arrays.asList(512, 768, 832, 1024, 2048, 3072, 4096, 6144, 8192);

    @LargeTest
    @Test
    public void testDHKeySize() throws Exception {
        for (int i = 0; i < sizesDH.size(); i++) {
            testKeyAgreement("BC", "DiffieHellman", "DiffieHellman", sizesDH.get(i));
        }
    }

    @Test
    public void testECDHKeySize() throws Exception {
        for (int i = 0; i < sizesECDH.size(); i++) {
            testKeyAgreement("AndroidOpenSSL", "ECDH", "EC", sizesECDH.get(i));
        }
    }

    // BEGIN Android-removed: XDH is not yet supported
    /*
    @Test
    public void testXDHKeySize() throws Exception {
        for (int i = 0; i < sizesXDH.size(); i++) {
            testKeyAgreement("AndroidOpenSSL", "XDH", "XDH", sizesXDH.get(i));
        }
    }
     */
    // END Android-removed: XDH is not yet supported

    /**
     * Perform KeyAgreement operation.
     */
    private static void testKeyAgreement(String provider, String kaAlgo,
            String kpgAlgo, int keySize) throws Exception {

        KeyPairGenerator kpg = KeyPairGenerator.getInstance(kpgAlgo, provider);
        kpg.initialize(keySize);
        KeyPair kp = kpg.generateKeyPair();
        // Test standard Key attributes.
        testKeyAttributes(provider, kpgAlgo, kp, keySize);
        // Test KeyAgreement.
        KeyAgreement ka = KeyAgreement.getInstance(kaAlgo, provider);
        ka.init(kp.getPrivate());
        ka.doPhase(kp.getPublic(), true);
        ka.generateSecret();
    }

    /**
     * Test standard Key attributes.
     */
    private static void testKeyAttributes(String provider, String kpgAlgo,
            KeyPair kp, int keySize) throws Exception {

        KeyFactory kf = KeyFactory.getInstance(kpgAlgo, provider);
        switch (kpgAlgo) {
            case "DiffieHellman":
                // Verify PrivateKey attributes.
                DHPrivateKey dhPri = (DHPrivateKey) kp.getPrivate();
                BigInteger p = dhPri.getParams().getP();
                if (p.bitLength() != keySize) {
                    fail(String.format("Invalid modulus size: "
                            + "%s/%s", p.bitLength(), keySize));
                }
                if (!p.isProbablePrime(128)) {
                    fail("The modulus is composite!");
                }
                PKCS8EncodedKeySpec dhPriSpec
                        = new PKCS8EncodedKeySpec(dhPri.getEncoded());
                DHPrivateKey dhPriDecod
                        = (DHPrivateKey) kf.generatePrivate(dhPriSpec);
                equals(dhPri.getX(), dhPriDecod.getX());
                equals(dhPri.getFormat(), dhPriDecod.getFormat());
                equals(dhPri.getEncoded(), dhPriDecod.getEncoded());
                equals(dhPri.getParams().getG(), dhPriDecod.getParams().getG());
                equals(dhPri.getParams().getL(), dhPriDecod.getParams().getL());
                equals(dhPri.getParams().getP(), dhPriDecod.getParams().getP());

                // Verify PublicKey attributes.
                DHPublicKey dhPub = (DHPublicKey) kp.getPublic();
                p = dhPub.getParams().getP();
                if (p.bitLength() != keySize) {
                   fail(String.format("Invalid modulus size: "
                            + "%s/%s", p.bitLength(), keySize));
                }
                X509EncodedKeySpec dhPubSpec
                        = new X509EncodedKeySpec(dhPub.getEncoded());
                DHPublicKey dhPubDecod
                        = (DHPublicKey) kf.generatePublic(dhPubSpec);
                equals(dhPub.getY(), dhPubDecod.getY());
                equals(dhPub.getFormat(), dhPubDecod.getFormat());
                equals(dhPub.getEncoded(), dhPubDecod.getEncoded());
                equals(dhPub.getParams().getG(), dhPubDecod.getParams().getG());
                equals(dhPub.getParams().getL(), dhPubDecod.getParams().getL());
                equals(dhPub.getParams().getP(), dhPubDecod.getParams().getP());

                BigInteger left = BigInteger.ONE;
                BigInteger right = p.subtract(BigInteger.ONE);
                BigInteger x = dhPri.getX();
                if ((x.compareTo(left) <= 0) || (x.compareTo(right) >= 0)) {
                    fail(
                            "X outside range [2, p - 2]: x: " + x + " p: " + p);
                }
                BigInteger y = dhPub.getY();
                if ((y.compareTo(left) <= 0) || (y.compareTo(right) >= 0)) {
                    fail(
                            "Y outside range [2, p - 2]: x: " + x + " p: " + p);
                }
                break;
            case "EC":
                // Verify PrivateKey attributes.
                ECPrivateKey ecPriv = (ECPrivateKey) kp.getPrivate();
                PKCS8EncodedKeySpec ecPriSpec
                        = new PKCS8EncodedKeySpec(ecPriv.getEncoded());
                ECPrivateKey ecPriDecod
                        = (ECPrivateKey) kf.generatePrivate(ecPriSpec);
                equals(ecPriv.getS(), ecPriDecod.getS());
                equals(ecPriv.getFormat(), ecPriDecod.getFormat());
                equals(ecPriv.getEncoded(), ecPriDecod.getEncoded());
                equals(ecPriv.getParams().getCofactor(),
                        ecPriDecod.getParams().getCofactor());
                equals(ecPriv.getParams().getCurve(),
                        ecPriDecod.getParams().getCurve());
                equals(ecPriv.getParams().getGenerator(),
                        ecPriDecod.getParams().getGenerator());
                equals(ecPriv.getParams().getOrder(),
                        ecPriDecod.getParams().getOrder());

                // Verify PublicKey attributes.
                ECPublicKey ecPub = (ECPublicKey) kp.getPublic();
                X509EncodedKeySpec ecPubSpec
                        = new X509EncodedKeySpec(ecPub.getEncoded());
                ECPublicKey ecPubDecod
                        = (ECPublicKey) kf.generatePublic(ecPubSpec);
                equals(ecPub.getW(), ecPubDecod.getW());
                equals(ecPub.getFormat(), ecPubDecod.getFormat());
                equals(ecPub.getEncoded(), ecPubDecod.getEncoded());
                equals(ecPub.getParams().getCofactor(),
                        ecPubDecod.getParams().getCofactor());
                equals(ecPub.getParams().getCurve(),
                        ecPubDecod.getParams().getCurve());
                equals(ecPub.getParams().getGenerator(),
                        ecPubDecod.getParams().getGenerator());
                equals(ecPub.getParams().getOrder(),
                        ecPubDecod.getParams().getOrder());
                break;
            case "XDH":
                // Verify PrivateKey attributes.
                XECPrivateKey xdhPri = (XECPrivateKey) kp.getPrivate();
                PKCS8EncodedKeySpec xdhPriSpec
                        = new PKCS8EncodedKeySpec(xdhPri.getEncoded());
                XECPrivateKey xdhPriDec
                        = (XECPrivateKey) kf.generatePrivate(xdhPriSpec);
                equals(xdhPri.getScalar().get(), xdhPriDec.getScalar().get());
                equals(xdhPri.getFormat(), xdhPriDec.getFormat());
                equals(xdhPri.getEncoded(), xdhPriDec.getEncoded());
                equals(((NamedParameterSpec) xdhPri.getParams()).getName(),
                        ((NamedParameterSpec) xdhPriDec.getParams()).getName());

                // Verify PublicKey attributes.
                XECPublicKey xdhPub = (XECPublicKey) kp.getPublic();
                X509EncodedKeySpec xdhPubSpec
                        = new X509EncodedKeySpec(xdhPub.getEncoded());
                XECPublicKey xdhPubDec
                        = (XECPublicKey) kf.generatePublic(xdhPubSpec);
                equals(xdhPub.getU(), xdhPubDec.getU());
                equals(xdhPub.getFormat(), xdhPubDec.getFormat());
                equals(xdhPub.getEncoded(), xdhPubDec.getEncoded());
                equals(((NamedParameterSpec) xdhPub.getParams()).getName(),
                        ((NamedParameterSpec) xdhPubDec.getParams()).getName());
                break;
            default:
                fail("Invalid Algo name " + kpgAlgo);
        }
    }

    private static boolean equals(Object actual, Object expected) {
        boolean equals = actual.equals(expected);
        if (!equals) {
            fail(String.format("Actual: %s, Expected: %s",
                    actual, expected));
        }
        return equals;
    }

    private static boolean equals(byte[] actual, byte[] expected) {
        boolean equals = Arrays.equals(actual, expected);
        if (!equals) {
            fail("Actual array does not equal Expected array");
        }
        return equals;
    }
}