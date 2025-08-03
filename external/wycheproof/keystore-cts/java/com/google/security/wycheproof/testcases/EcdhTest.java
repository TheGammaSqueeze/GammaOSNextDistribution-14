/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.security.wycheproof;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECFieldFp;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.EllipticCurve;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import javax.crypto.KeyAgreement;
import org.junit.After;
import org.junit.Test;
import org.junit.Ignore;
import android.content.Context;
import android.security.KeyStoreException;
import android.security.keystore.KeyProtection;
import android.security.keystore.KeyProperties;
import android.security.keystore.KeyGenParameterSpec;
import android.keystore.cts.util.KeyStoreUtil;

import androidx.test.InstrumentationRegistry;

/**
 * Testing ECDH.
 *
 * <p><b>Defense in depth</b>: The tests for ECDH assume that a attacker has control over all
 * aspects of the public key in an exchange. That means that the attacker can potentially send weak
 * or invalid public keys. For example, invalid public keys can contain points not on the curve,
 * curves that have been deliberately chosen so that DLs are easy to compute as well as orders or
 * cofactors that are wrong. It is expected that implementations validate the inputs of a key
 * agreement and that in no case information about the private key is leaked.
 *
 * <p><b>References:</b> Ingrid Biehl, Bernd Meyer, Volker Müller, "Differential Fault Attacks on
 * Elliptic Curve Cryptosystems", Crypto '00, pp. 131-164
 *
 * <p>Adrian Antipa, Daniel Brown, Alfred Menezes, Rene Struik, and Scott Vanstone, "Validation of
 * Elliptic Curve Public Keys", PKC 2003, https://www.iacr.org/archive/pkc2003/25670211/25670211.pdf
 *
 * <p><b>Bugs:</b> CVE-2015-7940: BouncyCastle before 1.51 does not validate a point is on the
 * curve. BouncyCastle v.1.52 checks that the public key point is on the public key curve but does
 * not check whether public key and private key use the same curve. BouncyCastle v.1.53 is still
 * vulnerable to attacks with modified public keys. An attacker can change the order of the curve
 * used by the public key. ECDHC would then reduce the private key modulo this order, which can be
 * used to find the private key.
 *
 * <p>CVE-2015-6924: Utimaco HSMs vulnerable to invalid curve attacks, which made the private key
 * extraction possible.
 *
 * <p>CVE-2015-7940: Issue with elliptic curve addition in mixed Jacobian-affine coordinates
 *
 * @author bleichen@google.com (Daniel Bleichenbacher)
 */
// TODO(bleichen): Stuff we haven't implemented:
//   - timing attacks
// Stuff we are delaying because there are more important bugs:
//   - testWrongOrder using BouncyCastle with ECDHWithSHA1Kdf throws
//     java.lang.UnsupportedOperationException: KDF can only be used when algorithm is known
//     Not sure if that is expected or another bug.
// CVEs for ECDH we haven't used anywhere.
//   - CVE-2014-3470: OpenSSL anonymous ECDH denial of service: triggered by NULL value in
//     certificate.
//   - CVE-2014-3572: OpenSSL downgrades ECDHE to ECDH
//   - CVE-2011-3210: OpenSSL was not thread safe
public class EcdhTest {
  private static final String EXPECTED_PROVIDER_NAME = TestUtil.EXPECTED_PROVIDER_NAME;
  private static final String KEY_ALIAS_1 = "TestKey";
  private static final String KEY_ALIAS_2 = "wycheproofkey1";
  private static final String KEY_ALIAS_3 = "wycheproofkey2";

  @After
  public void tearDown() throws Exception {
    KeyStoreUtil.cleanUpKeyStore();
  }

  private static PrivateKey getKeystorePrivateKey(PublicKey pubKey, PrivateKey privKey,
                                                  boolean isStrongBox)
    throws Exception {
    return (PrivateKey) KeyStoreUtil.saveKeysToKeystore(
                                    KEY_ALIAS_1, pubKey, privKey,
                                    new KeyProtection.Builder(KeyProperties.PURPOSE_AGREE_KEY)
                                    .setIsStrongBoxBacked(isStrongBox)
                                    .build())
                                .getKey(KEY_ALIAS_1, null);
  }

  private KeyPair generateECKeyPair(String alias, ECGenParameterSpec ecSpec, boolean isStrongBox)
          throws Exception {
    KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC", EXPECTED_PROVIDER_NAME);
    KeyGenParameterSpec ecKeySpec =
                new KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_AGREE_KEY)
                        .setAlgorithmParameterSpec(ecSpec)
                        .setIsStrongBoxBacked(isStrongBox)
                        .build();

    keyGen.initialize(ecKeySpec);
    return keyGen.generateKeyPair();
  }

  static final String[] ECDH_VARIANTS = {
    // Raw ECDH. The shared secret is the x-coordinate of the ECDH computation.
    // The tests below assume that this variant is implemenented.
    "ECDH",
    // ECDHC is a variant described in P1363 7.2.2 ECSVDP-DHC.
    // BouncyCastle implements this variant.
    "ECDHC",
    // A variant with an explicit key derivation function.
    // This is implemented by BouncyCastle.
    "ECDHWITHSHA1KDF",
  };

  /** Test vectors */
  public static class EcPublicKeyTestVector {
    final String comment;
    final String encoded; // hexadecimal representation of the X509 encoding
    final BigInteger p; // characteristic of the field
    final BigInteger n; // order of the subgroup
    final BigInteger a; // parameter a of the Weierstrass representation
    final BigInteger b; // parameter b of the Weierstrass represnetation
    final BigInteger gx; // x-coordinate of the generator
    final BigInteger gy; // y-coordainat of the generator
    final Integer h; // cofactor: may be null
    final BigInteger pubx; // x-coordinate of the public point
    final BigInteger puby; // y-coordinate of the public point

    public EcPublicKeyTestVector(
        String comment,
        String encoded,
        BigInteger p,
        BigInteger n,
        BigInteger a,
        BigInteger b,
        BigInteger gx,
        BigInteger gy,
        Integer h,
        BigInteger pubx,
        BigInteger puby) {
      this.comment = comment;
      this.encoded = encoded;
      this.p = p;
      this.n = n;
      this.a = a;
      this.b = b;
      this.gx = gx;
      this.gy = gy;
      this.h = h;
      this.pubx = pubx;
      this.puby = puby;
    }

    /**
     * Returns this key as ECPublicKeySpec or null if the key cannot be represented as
     * ECPublicKeySpec. The later happens for example if the order of cofactor are not positive.
     */
    public ECPublicKeySpec getSpec() {
      try {
        ECFieldFp fp = new ECFieldFp(p);
        EllipticCurve curve = new EllipticCurve(fp, a, b);
        ECPoint g = new ECPoint(gx, gy);
        // ECParameterSpec requires that the cofactor h is specified.
        if (h == null) {
          return null;
        }
        ECParameterSpec params = new ECParameterSpec(curve, g, n, h);
        ECPoint pubPoint = new ECPoint(pubx, puby);
        ECPublicKeySpec pub = new ECPublicKeySpec(pubPoint, params);
        return pub;
      } catch (Exception ex) {
        return null;
      }
    }

    public X509EncodedKeySpec getX509EncodedKeySpec() {
      return new X509EncodedKeySpec(TestUtil.hexToBytes(encoded));
    }
  }

public static final EcPublicKeyTestVector EC_VALID_PUBLIC_KEY =
      new EcPublicKeyTestVector(
          "unmodified",
          "3059301306072a8648ce3d020106082a8648ce3d03010703420004cdeb39edd0"
              + "3e2b1a11a5e134ec99d5f25f21673d403f3ecb47bd1fa676638958ea58493b84"
              + "29598c0b49bbb85c3303ddb1553c3b761c2caacca71606ba9ebac8",
          new BigInteger("ffffffff00000001000000000000000000000000ffffffffffffffffffffffff", 16),
          new BigInteger("ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551", 16),
          new BigInteger("ffffffff00000001000000000000000000000000fffffffffffffffffffffffc", 16),
          new BigInteger("5ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53b0f63bce3c3e27d2604b", 16),
          new BigInteger("6b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296", 16),
          new BigInteger("4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5", 16),
          1,
          new BigInteger("cdeb39edd03e2b1a11a5e134ec99d5f25f21673d403f3ecb47bd1fa676638958", 16),
          new BigInteger("ea58493b8429598c0b49bbb85c3303ddb1553c3b761c2caacca71606ba9ebac8", 16));

  public static final EcPublicKeyTestVector[] EC_MODIFIED_PUBLIC_KEYS = {
      // Modified keys
      new EcPublicKeyTestVector(
          "public point not on curve",
          "3059301306072a8648ce3d020106082a8648ce3d03010703420004cdeb39edd0"
              + "3e2b1a11a5e134ec99d5f25f21673d403f3ecb47bd1fa676638958ea58493b84"
              + "29598c0b49bbb85c3303ddb1553c3b761c2caacca71606ba9ebaca",
          new BigInteger("ffffffff00000001000000000000000000000000ffffffffffffffffffffffff", 16),
          new BigInteger("ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551", 16),
          new BigInteger("ffffffff00000001000000000000000000000000fffffffffffffffffffffffc", 16),
          new BigInteger("5ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53b0f63bce3c3e27d2604b", 16),
          new BigInteger("6b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296", 16),
          new BigInteger("4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5", 16),
          1,
          new BigInteger("cdeb39edd03e2b1a11a5e134ec99d5f25f21673d403f3ecb47bd1fa676638958", 16),
          new BigInteger("ea58493b8429598c0b49bbb85c3303ddb1553c3b761c2caacca71606ba9ebaca", 16)),
      new EcPublicKeyTestVector(
          "public point = (0,0)",
          "3059301306072a8648ce3d020106082a8648ce3d030107034200040000000000"
              + "0000000000000000000000000000000000000000000000000000000000000000"
              + "000000000000000000000000000000000000000000000000000000",
          new BigInteger("ffffffff00000001000000000000000000000000ffffffffffffffffffffffff", 16),
          new BigInteger("ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551", 16),
          new BigInteger("ffffffff00000001000000000000000000000000fffffffffffffffffffffffc", 16),
          new BigInteger("5ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53b0f63bce3c3e27d2604b", 16),
          new BigInteger("6b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296", 16),
          new BigInteger("4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5", 16),
          1,
          new BigInteger("0"),
          new BigInteger("0")),
      new EcPublicKeyTestVector(
          "order = 1",
          "308201133081cc06072a8648ce3d02013081c0020101302c06072a8648ce3d01"
              + "01022100ffffffff00000001000000000000000000000000ffffffffffffffff"
              + "ffffffff30440420ffffffff00000001000000000000000000000000ffffffff"
              + "fffffffffffffffc04205ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53"
              + "b0f63bce3c3e27d2604b0441046b17d1f2e12c4247f8bce6e563a440f277037d"
              + "812deb33a0f4a13945d898c2964fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33"
              + "576b315ececbb6406837bf51f502010102010103420004cdeb39edd03e2b1a11"
              + "a5e134ec99d5f25f21673d403f3ecb47bd1fa676638958ea58493b8429598c0b"
              + "49bbb85c3303ddb1553c3b761c2caacca71606ba9ebac8",
          new BigInteger("ffffffff00000001000000000000000000000000ffffffffffffffffffffffff", 16),
          new BigInteger("01", 16),
          new BigInteger("ffffffff00000001000000000000000000000000fffffffffffffffffffffffc", 16),
          new BigInteger("5ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53b0f63bce3c3e27d2604b", 16),
          new BigInteger("6b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296", 16),
          new BigInteger("4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5", 16),
          1,
          new BigInteger("cdeb39edd03e2b1a11a5e134ec99d5f25f21673d403f3ecb47bd1fa676638958", 16),
          new BigInteger("ea58493b8429598c0b49bbb85c3303ddb1553c3b761c2caacca71606ba9ebac8", 16)),
      new EcPublicKeyTestVector(
          "order = 26959946660873538060741835960514744168612397095220107664918121663170",
          "3082012f3081e806072a8648ce3d02013081dc020101302c06072a8648ce3d01"
              + "01022100ffffffff00000001000000000000000000000000ffffffffffffffff"
              + "ffffffff30440420ffffffff00000001000000000000000000000000ffffffff"
              + "fffffffffffffffc04205ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53"
              + "b0f63bce3c3e27d2604b0441046b17d1f2e12c4247f8bce6e563a440f277037d"
              + "812deb33a0f4a13945d898c2964fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33"
              + "576b315ececbb6406837bf51f5021d00ffffffff00000000ffffffffffffffff"
              + "bce6faada7179e84f3b9cac202010103420004cdeb39edd03e2b1a11a5e134ec"
              + "99d5f25f21673d403f3ecb47bd1fa676638958ea58493b8429598c0b49bbb85c"
              + "3303ddb1553c3b761c2caacca71606ba9ebac8",
          new BigInteger("ffffffff00000001000000000000000000000000ffffffffffffffffffffffff", 16),
          new BigInteger("ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2", 16),
          new BigInteger("ffffffff00000001000000000000000000000000fffffffffffffffffffffffc", 16),
          new BigInteger("5ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53b0f63bce3c3e27d2604b", 16),
          new BigInteger("6b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296", 16),
          new BigInteger("4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5", 16),
          1,
          new BigInteger("cdeb39edd03e2b1a11a5e134ec99d5f25f21673d403f3ecb47bd1fa676638958", 16),
          new BigInteger("ea58493b8429598c0b49bbb85c3303ddb1553c3b761c2caacca71606ba9ebac8", 16)),
      new EcPublicKeyTestVector(
          "generator = (0,0)",
          "308201333081ec06072a8648ce3d02013081e0020101302c06072a8648ce3d01"
              + "01022100ffffffff00000001000000000000000000000000ffffffffffffffff"
              + "ffffffff30440420ffffffff00000001000000000000000000000000ffffffff"
              + "fffffffffffffffc04205ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53"
              + "b0f63bce3c3e27d2604b04410400000000000000000000000000000000000000"
              + "0000000000000000000000000000000000000000000000000000000000000000"
              + "00000000000000000000000000022100ffffffff00000000ffffffffffffffff"
              + "bce6faada7179e84f3b9cac2fc63255102010103420004cdeb39edd03e2b1a11"
              + "a5e134ec99d5f25f21673d403f3ecb47bd1fa676638958ea58493b8429598c0b"
              + "49bbb85c3303ddb1553c3b761c2caacca71606ba9ebac8",
          new BigInteger("ffffffff00000001000000000000000000000000ffffffffffffffffffffffff", 16),
          new BigInteger("ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551", 16),
          new BigInteger("ffffffff00000001000000000000000000000000fffffffffffffffffffffffc", 16),
          new BigInteger("5ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53b0f63bce3c3e27d2604b", 16),
          new BigInteger("0"),
          new BigInteger("0"),
          1,
          new BigInteger("cdeb39edd03e2b1a11a5e134ec99d5f25f21673d403f3ecb47bd1fa676638958", 16),
          new BigInteger("ea58493b8429598c0b49bbb85c3303ddb1553c3b761c2caacca71606ba9ebac8", 16)),
      new EcPublicKeyTestVector(
          "generator not on curve",
          "308201333081ec06072a8648ce3d02013081e0020101302c06072a8648ce3d01"
              + "01022100ffffffff00000001000000000000000000000000ffffffffffffffff"
              + "ffffffff30440420ffffffff00000001000000000000000000000000ffffffff"
              + "fffffffffffffffc04205ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53"
              + "b0f63bce3c3e27d2604b0441046b17d1f2e12c4247f8bce6e563a440f277037d"
              + "812deb33a0f4a13945d898c2964fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33"
              + "576b315ececbb6406837bf51f7022100ffffffff00000000ffffffffffffffff"
              + "bce6faada7179e84f3b9cac2fc63255102010103420004cdeb39edd03e2b1a11"
              + "a5e134ec99d5f25f21673d403f3ecb47bd1fa676638958ea58493b8429598c0b"
              + "49bbb85c3303ddb1553c3b761c2caacca71606ba9ebac8",
          new BigInteger("ffffffff00000001000000000000000000000000ffffffffffffffffffffffff", 16),
          new BigInteger("ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551", 16),
          new BigInteger("ffffffff00000001000000000000000000000000fffffffffffffffffffffffc", 16),
          new BigInteger("5ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53b0f63bce3c3e27d2604b", 16),
          new BigInteger("6b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296", 16),
          new BigInteger("4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f7", 16),
          1,
          new BigInteger("cdeb39edd03e2b1a11a5e134ec99d5f25f21673d403f3ecb47bd1fa676638958", 16),
          new BigInteger("ea58493b8429598c0b49bbb85c3303ddb1553c3b761c2caacca71606ba9ebac8", 16)),
      new EcPublicKeyTestVector(
          "cofactor = 2",
          "308201333081ec06072a8648ce3d02013081e0020101302c06072a8648ce3d01"
              + "01022100ffffffff00000001000000000000000000000000ffffffffffffffff"
              + "ffffffff30440420ffffffff00000001000000000000000000000000ffffffff"
              + "fffffffffffffffc04205ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53"
              + "b0f63bce3c3e27d2604b0441046b17d1f2e12c4247f8bce6e563a440f277037d"
              + "812deb33a0f4a13945d898c2964fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33"
              + "576b315ececbb6406837bf51f5022100ffffffff00000000ffffffffffffffff"
              + "bce6faada7179e84f3b9cac2fc63255102010203420004cdeb39edd03e2b1a11"
              + "a5e134ec99d5f25f21673d403f3ecb47bd1fa676638958ea58493b8429598c0b"
              + "49bbb85c3303ddb1553c3b761c2caacca71606ba9ebac8",
          new BigInteger("ffffffff00000001000000000000000000000000ffffffffffffffffffffffff", 16),
          new BigInteger("ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551", 16),
          new BigInteger("ffffffff00000001000000000000000000000000fffffffffffffffffffffffc", 16),
          new BigInteger("5ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53b0f63bce3c3e27d2604b", 16),
          new BigInteger("6b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296", 16),
          new BigInteger("4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5", 16),
          2,
          new BigInteger("cdeb39edd03e2b1a11a5e134ec99d5f25f21673d403f3ecb47bd1fa676638958", 16),
          new BigInteger("ea58493b8429598c0b49bbb85c3303ddb1553c3b761c2caacca71606ba9ebac8", 16)),
      new EcPublicKeyTestVector(
          "cofactor = None",
          "308201303081e906072a8648ce3d02013081dd020101302c06072a8648ce3d01"
              + "01022100ffffffff00000001000000000000000000000000ffffffffffffffff"
              + "ffffffff30440420ffffffff00000001000000000000000000000000ffffffff"
              + "fffffffffffffffc04205ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53"
              + "b0f63bce3c3e27d2604b0441046b17d1f2e12c4247f8bce6e563a440f277037d"
              + "812deb33a0f4a13945d898c2964fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33"
              + "576b315ececbb6406837bf51f5022100ffffffff00000000ffffffffffffffff"
              + "bce6faada7179e84f3b9cac2fc63255103420004cdeb39edd03e2b1a11a5e134"
              + "ec99d5f25f21673d403f3ecb47bd1fa676638958ea58493b8429598c0b49bbb8"
              + "5c3303ddb1553c3b761c2caacca71606ba9ebac8",
          new BigInteger("ffffffff00000001000000000000000000000000ffffffffffffffffffffffff", 16),
          new BigInteger("ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551", 16),
          new BigInteger("ffffffff00000001000000000000000000000000fffffffffffffffffffffffc", 16),
          new BigInteger("5ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53b0f63bce3c3e27d2604b", 16),
          new BigInteger("6b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296", 16),
          new BigInteger("4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5", 16),
          null,
          new BigInteger("cdeb39edd03e2b1a11a5e134ec99d5f25f21673d403f3ecb47bd1fa676638958", 16),
          new BigInteger("ea58493b8429598c0b49bbb85c3303ddb1553c3b761c2caacca71606ba9ebac8", 16)),
      new EcPublicKeyTestVector(
          "modified prime",
          "308201333081ec06072a8648ce3d02013081e0020101302c06072a8648ce3d01"
              + "01022100fd091059a6893635f900e9449d63f572b2aebc4cff7b4e5e33f1b200"
              + "e8bbc1453044042002f6efa55976c9cb06ff16bb629c0a8d4d5143b40084b1a1"
              + "cc0e4dff17443eb704205ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53"
              + "b0f63bce3c3e27d2604b0441040000000000000000000006597fa94b1fd90000"
              + "000000000000000000000000021b8c7dd77f9a95627922eceefea73f028f1ec9"
              + "5ba9b8fa95a3ad24bdf9fff414022100ffffffff00000000ffffffffffffffff"
              + "bce6faada7179e84f3b9cac2fc63255102010103420004000000000000000000"
              + "0006597fa94b1fd90000000000000000000000000000021b8c7dd77f9a956279"
              + "22eceefea73f028f1ec95ba9b8fa95a3ad24bdf9fff414",
          new BigInteger("fd091059a6893635f900e9449d63f572b2aebc4cff7b4e5e33f1b200e8bbc145", 16),
          new BigInteger("ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551", 16),
          new BigInteger("ffffffff00000001000000000000000000000000fffffffffffffffffffffffc", 16),
          new BigInteger("5ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53b0f63bce3c3e27d2604b", 16),
          new BigInteger("06597fa94b1fd9000000000000000000000000000002", 16),
          new BigInteger("1b8c7dd77f9a95627922eceefea73f028f1ec95ba9b8fa95a3ad24bdf9fff414", 16),
          1,
          new BigInteger("06597fa94b1fd9000000000000000000000000000002", 16),
          new BigInteger("1b8c7dd77f9a95627922eceefea73f028f1ec95ba9b8fa95a3ad24bdf9fff414", 16)),
      new EcPublicKeyTestVector(
          "using secp224r1",
          "304e301006072a8648ce3d020106052b81040021033a0004074f56dc2ea648ef"
              + "89c3b72e23bbd2da36f60243e4d2067b70604af1c2165cec2f86603d60c8a611"
              + "d5b84ba3d91dfe1a480825bcc4af3bcf",
          new BigInteger("ffffffffffffffffffffffffffffffff000000000000000000000001", 16),
          new BigInteger("ffffffffffffffffffffffffffff16a2e0b8f03e13dd29455c5c2a3d", 16),
          new BigInteger("fffffffffffffffffffffffffffffffefffffffffffffffffffffffe", 16),
          new BigInteger("b4050a850c04b3abf54132565044b0b7d7bfd8ba270b39432355ffb4", 16),
          new BigInteger("b70e0cbd6bb4bf7f321390b94a03c1d356c21122343280d6115c1d21", 16),
          new BigInteger("bd376388b5f723fb4c22dfe6cd4375a05a07476444d5819985007e34", 16),
          1,
          new BigInteger("074f56dc2ea648ef89c3b72e23bbd2da36f60243e4d2067b70604af1", 16),
          new BigInteger("c2165cec2f86603d60c8a611d5b84ba3d91dfe1a480825bcc4af3bcf", 16)),
      new EcPublicKeyTestVector(
          "a = 0",
          "308201143081cd06072a8648ce3d02013081c1020101302c06072a8648ce3d01"
              + "01022100ffffffff00000001000000000000000000000000ffffffffffffffff"
              + "ffffffff30250401000420f104880c3980129c7efa19b6b0cb04e547b8d0fc0b"
              + "95f4946496dd4ac4a7c440044104cdeb39edd03e2b1a11a5e134ec99d5f25f21"
              + "673d403f3ecb47bd1fa676638958ea58493b8429598c0b49bbb85c3303ddb155"
              + "3c3b761c2caacca71606ba9ebac8022100ffffffff00000000ffffffffffffff"
              + "ffbce6faada7179e84f3b9cac2fc63255102010103420004cdeb39edd03e2b1a"
              + "11a5e134ec99d5f25f21673d403f3ecb47bd1fa676638958ea58493b8429598c"
              + "0b49bbb85c3303ddb1553c3b761c2caacca71606ba9ebac8",
          new BigInteger("ffffffff00000001000000000000000000000000ffffffffffffffffffffffff", 16),
          new BigInteger("ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551", 16),
          new BigInteger("0"),
          new BigInteger("f104880c3980129c7efa19b6b0cb04e547b8d0fc0b95f4946496dd4ac4a7c440", 16),
          new BigInteger("cdeb39edd03e2b1a11a5e134ec99d5f25f21673d403f3ecb47bd1fa676638958", 16),
          new BigInteger("ea58493b8429598c0b49bbb85c3303ddb1553c3b761c2caacca71606ba9ebac8", 16),
          1,
          new BigInteger("cdeb39edd03e2b1a11a5e134ec99d5f25f21673d403f3ecb47bd1fa676638958", 16),
          new BigInteger("ea58493b8429598c0b49bbb85c3303ddb1553c3b761c2caacca71606ba9ebac8", 16)),
      new EcPublicKeyTestVector(
          "new curve with generator of order 3 that is also on secp256r1",
          "308201333081ec06072a8648ce3d02013081e0020101302c06072a8648ce3d01"
              + "01022100ffffffff00000001000000000000000000000000ffffffffffffffff"
              + "ffffffff3044042046dc879a5c2995d0e6f682468ea95791b7bbd0225cfdb251"
              + "3fb10a737afece170420bea6c109251bfe4acf2eeda7c24c4ab70a1473335dec"
              + "28b244d4d823d15935e2044104701c05255026aa4630b78fc6b769e388059ab1"
              + "443cbdd1f8348bedc3be589dc34cfdab998ad27738ae382aa013986ade0f4859"
              + "2a9a1ae37ca61d25ec5356f1bd022100ffffffff00000000ffffffffffffffff"
              + "bce6faada7179e84f3b9cac2fc63255102010103420004701c05255026aa4630"
              + "b78fc6b769e388059ab1443cbdd1f8348bedc3be589dc3b3025465752d88c851"
              + "c7d55fec679521f0b7a6d665e51c8359e2da13aca90e42",
          new BigInteger("ffffffff00000001000000000000000000000000ffffffffffffffffffffffff", 16),
          new BigInteger("ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551", 16),
          new BigInteger("46dc879a5c2995d0e6f682468ea95791b7bbd0225cfdb2513fb10a737afece17", 16),
          new BigInteger("bea6c109251bfe4acf2eeda7c24c4ab70a1473335dec28b244d4d823d15935e2", 16),
          new BigInteger("701c05255026aa4630b78fc6b769e388059ab1443cbdd1f8348bedc3be589dc3", 16),
          new BigInteger("4cfdab998ad27738ae382aa013986ade0f48592a9a1ae37ca61d25ec5356f1bd", 16),
          1,
          new BigInteger("701c05255026aa4630b78fc6b769e388059ab1443cbdd1f8348bedc3be589dc3", 16),
          new BigInteger("b3025465752d88c851c7d55fec679521f0b7a6d665e51c8359e2da13aca90e42", 16)),
      // Invalid keys
      new EcPublicKeyTestVector(
          "order = -1157920892103562487626974469494075735299969552241357603"
              + "42422259061068512044369",
          "308201333081ec06072a8648ce3d02013081e0020101302c06072a8648ce3d01"
              + "01022100ffffffff00000001000000000000000000000000ffffffffffffffff"
              + "ffffffff30440420ffffffff00000001000000000000000000000000ffffffff"
              + "fffffffffffffffc04205ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53"
              + "b0f63bce3c3e27d2604b0441046b17d1f2e12c4247f8bce6e563a440f277037d"
              + "812deb33a0f4a13945d898c2964fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33"
              + "576b315ececbb6406837bf51f50221ff00000000ffffffff0000000000000000"
              + "4319055258e8617b0c46353d039cdaaf02010103420004cdeb39edd03e2b1a11"
              + "a5e134ec99d5f25f21673d403f3ecb47bd1fa676638958ea58493b8429598c0b"
              + "49bbb85c3303ddb1553c3b761c2caacca71606ba9ebac8",
          new BigInteger("ffffffff00000001000000000000000000000000ffffffffffffffffffffffff", 16),
          new BigInteger(
              "-115792089210356248762697446949407573529996955224135760342422259061068512044369"),
          new BigInteger("ffffffff00000001000000000000000000000000fffffffffffffffffffffffc", 16),
          new BigInteger("5ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53b0f63bce3c3e27d2604b", 16),
          new BigInteger("6b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296", 16),
          new BigInteger("4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5", 16),
          1,
          new BigInteger("cdeb39edd03e2b1a11a5e134ec99d5f25f21673d403f3ecb47bd1fa676638958", 16),
          new BigInteger("ea58493b8429598c0b49bbb85c3303ddb1553c3b761c2caacca71606ba9ebac8", 16)),
      new EcPublicKeyTestVector(
          "order = 0",
          "308201133081cc06072a8648ce3d02013081c0020101302c06072a8648ce3d01"
              + "01022100ffffffff00000001000000000000000000000000ffffffffffffffff"
              + "ffffffff30440420ffffffff00000001000000000000000000000000ffffffff"
              + "fffffffffffffffc04205ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53"
              + "b0f63bce3c3e27d2604b0441046b17d1f2e12c4247f8bce6e563a440f277037d"
              + "812deb33a0f4a13945d898c2964fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33"
              + "576b315ececbb6406837bf51f502010002010103420004cdeb39edd03e2b1a11"
              + "a5e134ec99d5f25f21673d403f3ecb47bd1fa676638958ea58493b8429598c0b"
              + "49bbb85c3303ddb1553c3b761c2caacca71606ba9ebac8",
          new BigInteger("ffffffff00000001000000000000000000000000ffffffffffffffffffffffff", 16),
          new BigInteger("0"),
          new BigInteger("ffffffff00000001000000000000000000000000fffffffffffffffffffffffc", 16),
          new BigInteger("5ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53b0f63bce3c3e27d2604b", 16),
          new BigInteger("6b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296", 16),
          new BigInteger("4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5", 16),
          1,
          new BigInteger("cdeb39edd03e2b1a11a5e134ec99d5f25f21673d403f3ecb47bd1fa676638958", 16),
          new BigInteger("ea58493b8429598c0b49bbb85c3303ddb1553c3b761c2caacca71606ba9ebac8", 16)),
      new EcPublicKeyTestVector(
          "cofactor = -1",
          "308201333081ec06072a8648ce3d02013081e0020101302c06072a8648ce3d01"
              + "01022100ffffffff00000001000000000000000000000000ffffffffffffffff"
              + "ffffffff30440420ffffffff00000001000000000000000000000000ffffffff"
              + "fffffffffffffffc04205ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53"
              + "b0f63bce3c3e27d2604b0441046b17d1f2e12c4247f8bce6e563a440f277037d"
              + "812deb33a0f4a13945d898c2964fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33"
              + "576b315ececbb6406837bf51f5022100ffffffff00000000ffffffffffffffff"
              + "bce6faada7179e84f3b9cac2fc6325510201ff03420004cdeb39edd03e2b1a11"
              + "a5e134ec99d5f25f21673d403f3ecb47bd1fa676638958ea58493b8429598c0b"
              + "49bbb85c3303ddb1553c3b761c2caacca71606ba9ebac8",
          new BigInteger("ffffffff00000001000000000000000000000000ffffffffffffffffffffffff", 16),
          new BigInteger("ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551", 16),
          new BigInteger("ffffffff00000001000000000000000000000000fffffffffffffffffffffffc", 16),
          new BigInteger("5ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53b0f63bce3c3e27d2604b", 16),
          new BigInteger("6b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296", 16),
          new BigInteger("4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5", 16),
          -1,
          new BigInteger("cdeb39edd03e2b1a11a5e134ec99d5f25f21673d403f3ecb47bd1fa676638958", 16),
          new BigInteger("ea58493b8429598c0b49bbb85c3303ddb1553c3b761c2caacca71606ba9ebac8", 16)),
      new EcPublicKeyTestVector(
          "cofactor = 0",
          "308201333081ec06072a8648ce3d02013081e0020101302c06072a8648ce3d01"
              + "01022100ffffffff00000001000000000000000000000000ffffffffffffffff"
              + "ffffffff30440420ffffffff00000001000000000000000000000000ffffffff"
              + "fffffffffffffffc04205ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53"
              + "b0f63bce3c3e27d2604b0441046b17d1f2e12c4247f8bce6e563a440f277037d"
              + "812deb33a0f4a13945d898c2964fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33"
              + "576b315ececbb6406837bf51f5022100ffffffff00000000ffffffffffffffff"
              + "bce6faada7179e84f3b9cac2fc63255102010003420004cdeb39edd03e2b1a11"
              + "a5e134ec99d5f25f21673d403f3ecb47bd1fa676638958ea58493b8429598c0b"
              + "49bbb85c3303ddb1553c3b761c2caacca71606ba9ebac8",
          new BigInteger("ffffffff00000001000000000000000000000000ffffffffffffffffffffffff", 16),
          new BigInteger("ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551", 16),
          new BigInteger("ffffffff00000001000000000000000000000000fffffffffffffffffffffffc", 16),
          new BigInteger("5ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53b0f63bce3c3e27d2604b", 16),
          new BigInteger("6b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296", 16),
          new BigInteger("4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5", 16),
          0,
          new BigInteger("cdeb39edd03e2b1a11a5e134ec99d5f25f21673d403f3ecb47bd1fa676638958", 16),
          new BigInteger("ea58493b8429598c0b49bbb85c3303ddb1553c3b761c2caacca71606ba9ebac8", 16)),
  };

  /** Checks that key agreement using ECDH works. */
  @Test
  public void testBasic() throws Exception {
    testBasic(false);
  }
  @Test
  public void testBasic_StrongBox() throws Exception {
    KeyStoreUtil.assumeStrongBox();
    testBasic(true);
  }
  private void testBasic(boolean isStrongBox) throws Exception {
    KeyPair keyPairA = generateECKeyPair(KEY_ALIAS_2, new ECGenParameterSpec("secp256r1"),
                                        isStrongBox);
    KeyPair keyPairB = generateECKeyPair(KEY_ALIAS_3, new ECGenParameterSpec("secp256r1"),
                                        isStrongBox);

    KeyAgreement kaA = KeyAgreement.getInstance("ECDH", EXPECTED_PROVIDER_NAME);
    KeyAgreement kaB = KeyAgreement.getInstance("ECDH", EXPECTED_PROVIDER_NAME);
    kaA.init(keyPairA.getPrivate());
    kaB.init(keyPairB.getPrivate());
    kaA.doPhase(keyPairB.getPublic(), true);
    kaB.doPhase(keyPairA.getPublic(), true);
    byte[] kAB = kaA.generateSecret();
    byte[] kBA = kaB.generateSecret();
    assertEquals(TestUtil.bytesToHex(kAB), TestUtil.bytesToHex(kBA));
  }

  @Test
  public void testEncode() throws Exception {
    KeyFactory kf = KeyFactory.getInstance("EC");
    ECPublicKey valid = (ECPublicKey) kf.generatePublic(EC_VALID_PUBLIC_KEY.getSpec());
    assertEquals(TestUtil.bytesToHex(valid.getEncoded()), EC_VALID_PUBLIC_KEY.encoded);
  }

  @Test
  public void testDecode() throws Exception {
    KeyFactory kf = KeyFactory.getInstance("EC");
    ECPublicKey key1 = (ECPublicKey) kf.generatePublic(EC_VALID_PUBLIC_KEY.getSpec());
    ECPublicKey key2 = (ECPublicKey) kf.generatePublic(EC_VALID_PUBLIC_KEY.getX509EncodedKeySpec());
    ECParameterSpec params1 = key1.getParams();
    ECParameterSpec params2 = key2.getParams();
    assertEquals(params1.getCofactor(), params2.getCofactor());
    assertEquals(params1.getCurve(), params2.getCurve());
    assertEquals(params1.getGenerator(), params2.getGenerator());
    assertEquals(params1.getOrder(), params2.getOrder());
    assertEquals(key1.getW(), key2.getW());
  }

  /**
   * This test modifies the order of group in the public key. A severe bug would be an
   * implementation that leaks information whether the private key is larger than the order given in
   * the public key. Also a severe bug would be to reduce the private key modulo the order given in
   * the public key parameters.
   */
  @SuppressWarnings("InsecureCryptoUsage")
  public void testModifiedPublic(String algorithm) throws Exception {
    testModifiedPublic(algorithm, false);
  }
  @SuppressWarnings("InsecureCryptoUsage")
  public void testModifiedPublic(String algorithm, boolean isStrongBox) throws Exception {
    KeyAgreement ka = KeyAgreement.getInstance(algorithm, EXPECTED_PROVIDER_NAME);
    KeyPair pair = generateECKeyPair(KEY_ALIAS_1, new ECGenParameterSpec("secp256r1"),
            isStrongBox);
    KeyFactory kf = KeyFactory.getInstance("EC");
    ECPublicKey validKey = (ECPublicKey) kf.generatePublic(EC_VALID_PUBLIC_KEY.getSpec());
    ka.init(pair.getPrivate());
    ka.doPhase(validKey, true);
    String expected = TestUtil.bytesToHex(ka.generateSecret());
    for (EcPublicKeyTestVector test : EC_MODIFIED_PUBLIC_KEYS) {
      try {
        X509EncodedKeySpec spec = test.getX509EncodedKeySpec();
        ECPublicKey modifiedKey = (ECPublicKey) kf.generatePublic(spec);
        ka.init(pair.getPrivate());
        ka.doPhase(modifiedKey, true);
        String shared = TestUtil.bytesToHex(ka.generateSecret());
        // The implementation did not notice that the public key was modified.
        // This is not nice, but at the moment we only fail the test if the
        // modification was essential for computing the shared secret.
        //
        // BouncyCastle v.1.53 fails this test, for ECDHC with modified order.
        // This implementation reduces the product s*h modulo the order given
        // in the public key. An attacker who can modify the order of the public key
        // and who can learn whether such a modification changes the shared secret is
        // able to learn the private key with a simple binary search.
        assertEquals("algorithm:" + algorithm + " test:" + test.comment, expected, shared);
      } catch (GeneralSecurityException ex) {
        // OK, since the public keys have been modified.
      }
    }
  }

  /**
   * This is a similar test as testModifiedPublic. However, this test uses test vectors
   * ECPublicKeySpec
   */
  @SuppressWarnings("InsecureCryptoUsage")
  public void testModifiedPublicSpec(String algorithm) throws Exception {
    testModifiedPublicSpec(algorithm, false);
  }
  @SuppressWarnings("InsecureCryptoUsage")
  public void testModifiedPublicSpec(String algorithm, boolean isStrongBox) throws Exception {
    KeyAgreement ka = KeyAgreement.getInstance(algorithm, EXPECTED_PROVIDER_NAME);
    KeyPair pair = generateECKeyPair(KEY_ALIAS_1, new ECGenParameterSpec("secp256r1"),
            isStrongBox);
    KeyFactory kf = KeyFactory.getInstance("EC");
    ECPublicKey validKey = (ECPublicKey) kf.generatePublic(EC_VALID_PUBLIC_KEY.getSpec());
    ka.init(pair.getPrivate());
    ka.doPhase(validKey, true);
    String expected = TestUtil.bytesToHex(ka.generateSecret());
    for (EcPublicKeyTestVector test : EC_MODIFIED_PUBLIC_KEYS) {
      ECPublicKeySpec spec = test.getSpec();
      if (spec == null) {
        // The constructor of EcPublicKeySpec performs some very minor validity checks.
        // spec == null if one of these validity checks fails. Of course such a failure is OK.
        continue;
      }
      try {
        ECPublicKey modifiedKey = (ECPublicKey) kf.generatePublic(spec);
        ka.init(pair.getPrivate());
        ka.doPhase(modifiedKey, true);
        String shared = TestUtil.bytesToHex(ka.generateSecret());
        // The implementation did not notice that the public key was modified.
        // This is not nice, but at the moment we only fail the test if the
        // modification was essential for computing the shared secret.
        //
        // BouncyCastle v.1.53 fails this test, for ECDHC with modified order.
        // This implementation reduces the product s*h modulo the order given
        // in the public key. An attacker who can modify the order of the public key
        // and who can learn whether such a modification changes the shared secret is
        // able to learn the private key with a simple binary search.
        assertEquals("algorithm:" + algorithm + " test:" + test.comment, expected, shared);
      } catch (GeneralSecurityException ex) {
        // OK, since the public keys have been modified.
      }
    }
  }

  @Test
  public void testEcdhModifiedPublic() throws Exception {
    testModifiedPublic("ECDH");
  }
  @Test
  public void testEcdhModifiedPublic_StrongBox() throws Exception {
    KeyStoreUtil.assumeStrongBox();
    testModifiedPublic("ECDH", true);
  }

  @Test
  @Ignore // ECDHC algorithm is not supported in AndroidKeyStore
  public void testEcdhcModifiedPublic() throws Exception {
    testModifiedPublic("ECDHC");
  }

  @Test
  public void testEcdhModifiedPublicSpec() throws Exception {
    testModifiedPublicSpec("ECDH");
  }
  @Test
  public void testEcdhModifiedPublicSpec_StrongBox() throws Exception {
    KeyStoreUtil.assumeStrongBox();
    testModifiedPublicSpec("ECDH", true);
  }

  @Test
  @Ignore // ECDHC algorithm is not supported in AndroidKeyStore
  public void testEcdhcModifiedPublicSpec() throws Exception {
    testModifiedPublicSpec("ECDHC");
  }

  /**
   * This test modifies the order of group in the public key. A severe bug would be an
   * implementation that leaks information whether the private key is larger than the order given in
   * the public key. Also a severe bug would be to reduce the private key modulo the order given in
   * the public key parameters.
   */
  // TODO(bleichen): This can be merged with testModifiedPublic once this is fixed.
  @SuppressWarnings("InsecureCryptoUsage")
  public void testWrongOrder(String algorithm, ECParameterSpec spec)
          throws Exception {
    testWrongOrder(algorithm, spec, false);
  }
  @SuppressWarnings("InsecureCryptoUsage")
  public void testWrongOrder(String algorithm, ECParameterSpec spec, boolean isStrongBox)
          throws Exception {
    KeyAgreement ka = KeyAgreement.getInstance(algorithm, EXPECTED_PROVIDER_NAME);
    PrivateKey priv = generateECKeyPair(KEY_ALIAS_2,
                                        new ECGenParameterSpec("secp256r1"), isStrongBox)
            .getPrivate();
    ECPublicKey pub = (ECPublicKey) generateECKeyPair(KEY_ALIAS_3,
                                        new ECGenParameterSpec("secp256r1"), isStrongBox)
            .getPublic();
    // Get the shared secret for the unmodified keys.
    ka.init(priv);
    ka.doPhase(pub, true);
    byte[] shared = ka.generateSecret();
    // Generate a modified public key.
    ECParameterSpec modifiedParams =
        new ECParameterSpec(
            spec.getCurve(), spec.getGenerator(), spec.getOrder().shiftRight(16), 1);
    ECPublicKeySpec modifiedPubSpec = new ECPublicKeySpec(pub.getW(), modifiedParams);
    KeyFactory kf = KeyFactory.getInstance("EC");
    ECPublicKey modifiedPub = (ECPublicKey) kf.generatePublic(modifiedPubSpec);
    byte[] shared2;
    try {
      ka.init(priv);
      ka.doPhase(modifiedPub, true);
      shared2 = ka.generateSecret();
    } catch (GeneralSecurityException ex) {
      // This is the expected behavior
      return;
    }
    // TODO(bleichen): Getting here is already a bug and we might flag this later.
    // At the moment we are only interested in really bad behavior of a library, that potentially
    // leaks the secret key. This is the case when the shared secrets are different, since this
    // suggests that the implementation reduces the multiplier modulo the given order of the curve
    // or some other behaviour that is dependent on the private key.
    // An attacker who can check whether a DH computation was done correctly or incorrectly because
    // of modular reduction, can determine the private key, either by a binary search or by trying
    // to guess the private key modulo some small "order".
    // BouncyCastle v.1.53 fails this test, and leaks the private key.
    
    assertEquals(
        "Algorithm:" + algorithm, TestUtil.bytesToHex(shared), TestUtil.bytesToHex(shared2));
  }

  @Test
  public void testWrongOrderEcdhNist() throws Exception {
    testWrongOrder("ECDH", EcUtil.getNistP256Params());
  }
  @Test
  public void testWrongOrderEcdhNist_StrongBox() throws Exception {
    KeyStoreUtil.assumeStrongBox();
    testWrongOrder("ECDH", EcUtil.getNistP256Params(), true);
  }

  @Test
  @Ignore // Brainpool curves are not supported in AndroidKeyStore.
  public void testWrongOrderEcdhBrainpool() throws Exception {
    testWrongOrder("ECDH", EcUtil.getBrainpoolP256r1Params());
  }

  @Test
  @Ignore // ECDHC algorithm not supported in AndroidKeyStore.
  public void testWrongOrderEcdhc() throws Exception {
    testWrongOrder("ECDHC", EcUtil.getNistP256Params());
    testWrongOrder("ECDHC", EcUtil.getBrainpoolP256r1Params());
  }

  /**
   * Tests for the problem detected by CVE-2017-10176. 
   *
   * <p>Some libraries do not compute P + (-P) correctly and return 2 * P or throw exceptions. When
   * the library uses addition-subtraction chains for the point multiplication then such cases can
   * occur for example when the private key is close to the order of the curve.
   */
  private void testLargePrivateKey(ECParameterSpec spec) throws Exception {
    testLargePrivateKey(spec, false);
  }
  private void testLargePrivateKey(ECParameterSpec spec, boolean isStrongBox) throws Exception {
    BigInteger order = spec.getOrder();
    KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
    keyGen.initialize(spec);
    ECPublicKey pub = (ECPublicKey) keyGen.generateKeyPair().getPublic();
    KeyFactory kf = KeyFactory.getInstance("EC");
    KeyAgreement ka = KeyAgreement.getInstance("ECDH", EXPECTED_PROVIDER_NAME);
    for (int i = 1; i <= 64; i++) {
      BigInteger p1 = BigInteger.valueOf(i);
      ECPrivateKeySpec spec1 = new ECPrivateKeySpec(p1, spec);
      ECPrivateKeySpec spec2 = new ECPrivateKeySpec(order.subtract(p1), spec);
      PrivateKey priv1 = kf.generatePrivate(spec1);
      PrivateKey priv2 = kf.generatePrivate(spec2);
      // This Public key is not pair of priv1, but it is required to create KeyPair to import into
      // AndroidKeyStore, So using dummy public key.
      PublicKey pub1 = kf.generatePublic(EC_VALID_PUBLIC_KEY.getX509EncodedKeySpec());
      try {
        ka.init(getKeystorePrivateKey(pub1, priv1, isStrongBox));
        ka.doPhase(pub, true);
        byte[] shared1 = ka.generateSecret();
        ka.init(getKeystorePrivateKey(pub1, priv2, isStrongBox));
        ka.doPhase(pub, true);
        byte[] shared2 = ka.generateSecret();
        // The private keys p1 and p2 are equivalent, since only the x-coordinate of the
        // shared point is used to generate the shared secret.
        assertEquals(TestUtil.bytesToHex(shared1), TestUtil.bytesToHex(shared2));
      } catch (InvalidKeyException e) {
        if (i <= 15 && e.getCause() instanceof KeyStoreException &&
            ((KeyStoreException)e.getCause()).getNumericErrorCode() ==
                KeyStoreException.ERROR_KEYMINT_FAILURE) {
          // Known rejected on some vendor implementations for certain i values.
          // Keymint Applet error code is INVALID_OPERATION (-76).
          continue;
        } else {
          throw e;
        }
      }
    }
  }

  @Test
  public void testNistCurveLargePrivateKey() throws Exception {
    testLargePrivateKey(EcUtil.getNistP224Params());
    testLargePrivateKey(EcUtil.getNistP256Params());
    testLargePrivateKey(EcUtil.getNistP384Params());
    // This test failed before CVE-2017-10176 was fixed.
    testLargePrivateKey(EcUtil.getNistP521Params());
  }
  @Test
  public void testNistCurveLargePrivateKey_StrongBox() throws Exception {
    KeyStoreUtil.assumeStrongBox();
    testLargePrivateKey(EcUtil.getNistP256Params(), true);
  }

  @Test
  @Ignore // Brainpool curves are not supported in AndroidKeyStore.
  public void testBrainpoolCurveLargePrivateKey() throws Exception {
    testLargePrivateKey(EcUtil.getBrainpoolP256r1Params());
  }
}

