/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package tests.security.interfaces;
import java.math.BigInteger;
import java.security.SecureRandomSpi;
import java.security.spec.ECFieldFp;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.EllipticCurve;
import java.security.spec.RSAPrivateCrtKeySpec;

/**
 * Utility class to provide some constants
 */
class Util {

    /**
     * Valid P for DSA tests
     */
    static final BigInteger P = new BigInteger(
            "178479572281896551646004364479186243274554253442971675202712037168"
                    + "82805439171286757012622742273566628953929784385654859898"
                    + "28019943266498970695878014699423565775500281013661604573"
                    + "09351370942441879889477647669664876805999161358675121909"
                    + "02875461840550932624652402732307184862051812119809510467"
                    + "6997149499533466361");

    /**
     * Valid Q for DSA tests
     */
    static final BigInteger Q = new BigInteger(
            "764905408100544417452957057404815852894534709423");

    /**
     * Valid G for DSA tests
     */
    static final BigInteger G = new BigInteger(
            "250346303870482828530842176986393415513071912937041425322012361012"
                    + "16575725689706821855929265075265423817009497798948914793"
                    + "36272769721567876826949070538671438636626715308216064610"
                    + "91161573885991070984580607652541845127399865661520191726"
                    + "47818913386618968229835178446104566543814577436312685021"
                    + "713979414153557537");

    /**
     * Value returned using MySecureRandomSpi
     */
    static final BigInteger RND_RET = new BigInteger("10");

    /**
     * Valid RSA parameters
     */
    static final RSAPrivateCrtKeySpec rsaCrtParam = new RSAPrivateCrtKeySpec(
            new BigInteger(
                    "ab07c37595818b1c0e543c8a0f157e932c7e7be59a45b7575b4101560f"
                            + "270bd4bc33e62da586b33baae8a51b4f30c3166e0e065984"
                            + "6b6915a5a8d5e50de6952989835fed9d271d371de66429d5"
                            + "565e8cd92f11e3c35777ae119b98c3c1d8683589e181bbc1"
                            + "0202db748fa07d0e81c66f33089f2641351ff85c86ede32d"
                            + "a17dd4398d324ea7feb9eb9341bd16f5a525a64696fbf7b1"
                            + "447ad227a4fd3ec95b4674a87f0fb4bf9459d812ded94437"
                            + "8b55e5dd2cfa9e154f0b8a606aaa7ff1411bd989d66a73e8"
                            + "76985d9a87c022b23997e497aa9addcbd76aabff6dc0b661"
                            + "53233aa4f71e63460d6a0814d19e26f91bb6cc562dc815d8"
                            + "0fd619d952d33cbb511c6f",
                    16),
            BigInteger.valueOf(65537),
            new BigInteger(
                    "04e9fd30b4b1fb283c3529642218d101338712d23f037fc80fc42760ac"
                            + "a0c7933e31138cfe338794d04d4ce922f658b49d46e3e1d0"
                            + "8f317ec0bac0228baab3607526042c94965a1d8a4b63575c"
                            + "5cd5eca014dd23b92a1b3332285154568e7c380595282017"
                            + "e491e6724bea258db426da6d11f358ab08f6b93117636062"
                            + "a040383baf7f065696f0f5235be275787742c6d5a0c5052f"
                            + "ad6878078f8f1801f0187b8793984a31135ab522c789bdcd"
                            + "02b9fca48136b090b7d2b7e69f961109fa3ac7edc9c11176"
                            + "c719a4ba960b7cacf8af291d35f55c8427f373f019185608"
                            + "2a57c885b195e6350a37ef9e120fa05a0a65ece5f3124204"
                            + "6bd8ac9b4d0f45b94ad361",
                    16),
            new BigInteger(
                    "eb68d3cd6a402405c59e59d69ea5a998f0310a232ebcaa3bab1bec9888"
                            + "125ea7afb3acc65f718187c609e03d422699f387a740a929"
                            + "48229f0a1bf988a6cdf83b8ba989c51881af21f39519b8b4"
                            + "04820db3ff7e92bc691c29319f315de074d66e14ca5a67fa"
                            + "76eef3ebe49ca31a4bb33f56d1006d04960051524a4f0df4"
                            + "0838f1",
                    16),
            new BigInteger(
                    "b9fd63d45c6e921487361b25d6d4a056b27e0e62e4e1408dc80ba08c03"
                            + "0110a339e62ffa4a2c642497c9588e66d7c03f377f09ca95"
                            + "c7a1da6eb6773c9ef9e6d312bf51a15fb7f25d792990b312"
                            + "d2c5ed229adab2fe8258508ff193add0599dd876543f51c5"
                            + "1279329ccd3beb3d78542179b978cb66de27d665e7f20ff9"
                            + "ebab5f",
                    16),
            new BigInteger(
                    "d6d775ad5bd1abce84bb6edc1b2fafa77f56121d67d3113da17cbba1bd"
                            + "559e83452c227b6cb1a7786539b027d80e68a443e25575cc"
                            + "d2239ea7d83e1503bea85497b2a3a478282ad03e808dd7ee"
                            + "9acaa27ceaa8f67bf3259b46c93581aa8e97002203471cec"
                            + "0dcd2859d9eb46438260d5668319f364a30d292a83dcc02c"
                            + "3db0d1",
                    16),
            new BigInteger(
                    "94ede00866536bc9e05364fdb1bee4fd47444544cf3dc7b140647f145e"
                            + "3b76151e01c29d4ad3ad04d83e34192324d41e4ad4010525"
                            + "568bb61b3d24d1780367dfd07d3e62580135a3bfa796e4ad"
                            + "c7f51163ca582148471b6cb7369c286c53b65f64b1255ad6"
                            + "d16a172b13f44cf415ec5839de5869975f80cadf2311e3d5"
                            + "1def45",
                    16),
            new BigInteger(
                    "cd611f5e9698258144c87f14326ac40ef0070c447f28a20022a0fa62b2"
                            + "41ebce6a581bcb4754684bae886dcc893aca6390dd368718"
                            + "8e3b80035827a72f050f0356c784922b5d15be932658d427"
                            + "fa122adb87d7d1f32bdf19743c25543f3728d8f55a1d55d6"
                            + "b23cd8223690bd7e594c64517acd5bf925b32dfe56b09d05"
                            + "7b4668",
                    16));

    /**
     * Valid EC parameters
     */
    static final ECParameterSpec ecParam = new ECParameterSpec(
            new EllipticCurve(
                    new ECFieldFp(BigInteger.valueOf(23)),
                    BigInteger.valueOf(5), BigInteger.valueOf(3)),
            new ECPoint(BigInteger.valueOf(1), BigInteger.valueOf(3)),
            BigInteger.valueOf(23), 1);

    private Util() {
    }
}

/**
 * Utility class to provide "random" data.
 * Returned value is always constant 10 if converted to BigInteger
 */
@SuppressWarnings("serial")
class MySecureRandomSpi extends SecureRandomSpi {

    @Override
    protected byte[] engineGenerateSeed(int arg0) {
        return null;
    }

    @Override
    protected void engineNextBytes(byte[] bytes) {
        java.util.Arrays.fill(bytes, (byte) 0);
        bytes[bytes.length - 1] = (byte) 10;
    }

    @Override
    protected void engineSetSeed(byte[] arg0) {
        return;
    }
}

