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

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.util.Arrays;
import java.util.HashSet;
import org.junit.After;
import org.junit.Test;
import org.junit.Ignore;
import android.security.keystore.KeyProtection;
import android.security.keystore.KeyProperties;
import android.keystore.cts.util.KeyStoreUtil;

/**
 * Tests ECDSA signatures.
 *
 * <p>Tests for signature verification with test vectors are in JsonSignatureTest.java toghether
 * with other signature schemes.
 *
 * @author bleichen@google.com (Daniel Bleichenbacher)
 */
public class EcdsaTest {
  private static final String EXPECTED_PROVIDER_NAME = TestUtil.EXPECTED_CRYPTO_OP_PROVIDER_NAME;
  private static final String KEY_ALIAS_1 = "TestKey";
  private static final String TAG = "EcdsaTest";

  @After
  public void tearDown() throws Exception {
    KeyStoreUtil.cleanUpKeyStore();
  }

  private static PrivateKey getKeystorePrivateKey(PublicKey pubKey, PrivateKey privKey,
                                                  boolean isStrongBox) throws Exception {
    KeyProtection keyProtection = new KeyProtection.Builder(KeyProperties.PURPOSE_SIGN)
          .setDigests(KeyProperties.DIGEST_SHA224,
                      KeyProperties.DIGEST_SHA256,
                      KeyProperties.DIGEST_SHA384,
                      KeyProperties.DIGEST_SHA512)
          .setIsStrongBoxBacked(isStrongBox)
          .build();
    KeyStore keyStore = KeyStoreUtil.saveKeysToKeystore(KEY_ALIAS_1, pubKey, privKey,
                                                        keyProtection);
    return (PrivateKey) keyStore.getKey(KEY_ALIAS_1, null);
  }

  /**
   * Determines the Hash name from the ECDSA algorithm. There is a small inconsistency in the naming
   * of algorithms. The Oracle standard use no hyphen in SHA256WithECDSA but uses a hyphen in the
   * message digest, i.e., SHA-256.
   */
  private String getHashAlgorithm(String ecdsaAlgorithm) {
    ecdsaAlgorithm = ecdsaAlgorithm.toUpperCase();
    int idx = ecdsaAlgorithm.indexOf("WITH");
    if (idx > 0) {
      if (ecdsaAlgorithm.startsWith("SHA")) {
        return "SHA-" + ecdsaAlgorithm.substring(3, idx);
      } else {
        return ecdsaAlgorithm.substring(0, idx);
      }
    }
    return "";
  }

  /**
   * Returns true if the signature scheme is deterministic. Even though a non-deterministic
   * signature scheme can in principle return the same signature twice this should never happen in
   * practice.
   */
  private boolean isDeterministic(Signature signer, PrivateKey priv) throws Exception {
    byte[][] signature = new byte[2][];
    byte[] message = new byte[1];
    for (int i = 0; i < 2; i++) {
      signer.initSign(priv);
      signer.update(message);
      signature[i] = signer.sign();
    }
    return Arrays.equals(signature[0], signature[1]);
  }

  /**
   * Returns number of count messages to sign. If the signature scheme is deterministic then the
   * messages are all different. If the signature scheme is randomized then the messages are all
   * the same. If the messages signed are all the same then it may be easier to detect a bias.
   */
  private byte[][] getMessagesToSign(int count, Signature signer, PrivateKey priv)
          throws Exception {
    byte[][] messages = new byte[count][];
    if (isDeterministic(signer, priv)) {
      for (int i = 0; i < count; i++) {
        messages[i] = ByteBuffer.allocate(4).putInt(i).array();
      }
    } else {
      byte[] msg = new byte[4];
      for (int i = 0; i < count; i++) {
        messages[i] = msg;
      }
    }
    return messages;
  }

  /**
   * Extract the integer r from an ECDSA signature. This method implicitely assumes that the ECDSA
   * signature is DER encoded. and that the order of the curve is smaller than 2^1024.
   */
  BigInteger extractR(byte[] signature) throws Exception {
    int startR = (signature[1] & 0x80) != 0 ? 3 : 2;
    int lengthR = signature[startR + 1];
    return new BigInteger(Arrays.copyOfRange(signature, startR + 2, startR + 2 + lengthR));
  }

  BigInteger extractS(byte[] signature) throws Exception {
    int startR = (signature[1] & 0x80) != 0 ? 3 : 2;
    int lengthR = signature[startR + 1];
    int startS = startR + 2 + lengthR;
    int lengthS = signature[startS + 1];
    return new BigInteger(Arrays.copyOfRange(signature, startS + 2, startS + 2 + lengthS));
  }

  /** Extract the k that was used to sign the signature. */
  BigInteger extractK(byte[] signature, BigInteger h, ECPrivateKey priv) throws Exception {
    BigInteger x = priv.getS();
    BigInteger n = priv.getParams().getOrder();
    BigInteger r = extractR(signature);
    BigInteger s = extractS(signature);
    BigInteger k = x.multiply(r).add(h).multiply(s.modInverse(n)).mod(n);
    return k;
  }

  /**
   * Computes the bias of samples as
   *
   * <p>abs(sum(e^(2 pi i s m / modulus) for s in samples) / sqrt(samples.length).
   *
   * <p>If the samples are taken from a uniform distribution in the range 0 .. modulus - 1 and the
   * number of samples is significantly larger than L^2 then the probability that the result is
   * larger than L is approximately e^(-L^2). The approximation can be derived from the assumption
   * that samples taken from a uniform distribution give a result that approximates a standard
   * complex normal distribution Z. I.e. Z has a density f_Z(z) = exp(-abs(z)^2) / pi.
   * https://en.wikipedia.org/wiki/Complex_normal_distribution
   */
  double bias(BigInteger[] samples, BigInteger modulus, BigInteger m) {
    double sumReal = 0.0;
    double sumImag = 0.0;
    for (BigInteger s : samples) {
      BigInteger r = s.multiply(m).mod(modulus);
      // multiplier = 2 * pi / 2^52
      double multiplier = 1.3951473992034527e-15;
      // computes the quotent 2 * pi * r / modulus
      double quot = r.shiftLeft(52).divide(modulus).doubleValue() * multiplier;
      sumReal += Math.cos(quot);
      sumImag += Math.sin(quot);
    }
    return Math.sqrt((sumReal * sumReal + sumImag * sumImag) / samples.length);
  }

  /**
   * This test checks the basic functionality of ECDSA. It simply tries to generate a key, sign and
   * verify a message for a given, algorithm and curve.
   *
   * @param algorithm the algorithm to test (e.g. "SHA256WithECDSA")
   * @param curve the curve to test (e.g. "secp256r1")
   * @return whether the algorithm and curve are supported.
   * @throws Exception if an unexpected error occurred.
   */
  boolean testParameters(String algorithm, String curve) throws Exception {
    return testParameters(algorithm, curve, false);
  }
  boolean testParameters(String algorithm, String curve, boolean isStrongBox) throws Exception {
    if (isStrongBox) {
      KeyStoreUtil.assumeStrongBox();
    }
    String message = "123400";

    KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
    KeyPair keyPair;
    try {
      keyGen.initialize(new ECGenParameterSpec(curve));
      keyPair = keyGen.generateKeyPair();
    } catch (InvalidAlgorithmParameterException ex) {
      // The curve is not supported.
      // The documentation does not specify whether the method initialize
      // has to reject unsupported curves or if only generateKeyPair checks
      // whether the curve is supported.
      return false;
    }
    ECPublicKey pub = (ECPublicKey) keyPair.getPublic();
    ECPrivateKey priv = (ECPrivateKey) keyPair.getPrivate();

    Signature signer;
    Signature verifier;
    try {
      signer = Signature.getInstance(algorithm, EXPECTED_PROVIDER_NAME);
      verifier = Signature.getInstance(algorithm, EXPECTED_PROVIDER_NAME);
    } catch (NoSuchAlgorithmException ex) {
      // The algorithm is not supported.
      return false;
    }
    // Both algorithm and curve are supported.
    // Hence, we expect that signing and verifying properly works.
    byte[] messageBytes = message.getBytes("UTF-8");
    signer.initSign(getKeystorePrivateKey(pub, priv, isStrongBox));
    signer.update(messageBytes);
    byte[] signature = signer.sign();
    verifier.initVerify(pub);
    verifier.update(messageBytes);
    assertTrue(verifier.verify(signature));
    return true;
  }

  /**
   * This test checks the basic functionality of ECDSA. This mainly checks that the provider follows
   * the JCA interface.
   */
  @Test
  public void testBasic() throws Exception {
    String algorithm = "SHA256WithECDSA";
    String curve = "secp256r1";
    assertTrue(testParameters(algorithm, curve));
  }
  @Test
  public void testBasic_StrongBox() throws Exception {
    String algorithm = "SHA256WithECDSA";
    String curve = "secp256r1";
    assertTrue(testParameters(algorithm, curve, true));
  }

  /** Checks whether the one time key k in ECDSA is biased. */
  public void testBias(String algorithm, String curve) throws Exception {
    testBias(algorithm, curve, false);
  }
  public void testBias(String algorithm, String curve,
                       boolean isStrongBox) throws Exception {
    if (isStrongBox) {
      KeyStoreUtil.assumeStrongBox();
    }
    Signature signer = Signature.getInstance(algorithm, EXPECTED_PROVIDER_NAME);
    KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
    keyGen.initialize(new ECGenParameterSpec(curve));
    KeyPair keyPair = keyGen.generateKeyPair();

    ECPrivateKey priv = (ECPrivateKey)keyPair.getPrivate();
    PrivateKey keystorePrivateKey = getKeystorePrivateKey(keyPair.getPublic(),
            keyPair.getPrivate(), isStrongBox);
    // If we throw a fair coin tests times then the probability that
    // either heads or tails appears less than mincount is less than 2^{-32}.
    // Therefore the test below is not expected to fail unless the generation
    // of the one time keys is indeed biased.
    final int tests = 1024;
    final int mincount = 410;

    BigInteger[] kList = new BigInteger[tests];
    byte[][] message = getMessagesToSign(tests, signer, keystorePrivateKey);
    signer.initSign(keystorePrivateKey);
    String hashAlgorithm = getHashAlgorithm(algorithm);
    for (int i = 0; i < tests; i++) {
      signer.update(message[i]);
      byte[] signature = signer.sign();
      byte[] digest = MessageDigest.getInstance(hashAlgorithm).digest(message[i]);
      // TODO(bleichen): Truncate the digest if the digest size is larger than the
      //   curve size.
      BigInteger h = new BigInteger(1, digest);
      kList[i] = extractK(signature, h, priv);
    }

    // Checks whether the most significant bits and the least significant bits
    // of the value k are unbiased.
    int countMsb = 0; // count the number of k's with lsb set
    int countLsb = 0; // count the number of k's with msb set
    BigInteger q = priv.getParams().getOrder();
    BigInteger qHalf = q.shiftRight(1);
    for (BigInteger k : kList) {
      if (k.testBit(0)) {
        countLsb++;
      }
      if (k.compareTo(qHalf) > 0) {
        countMsb++;
      }
    }
    if (countLsb < mincount || countLsb > tests - mincount) {
      fail("Bias detected in the least significant bit of k:" + countLsb);
    }
    if (countMsb < mincount || countMsb > tests - mincount) {
      fail("Bias detected in the most significant bit of k:" + countMsb);
    }

    // One situation where the bits above are not biased even if k itself is
    // badly distributed is the case where the signer replaces s by
    // min(s, q - s). Such a replacement is sometimes done to avoid signature
    // malleability of ECDSA.
    // Breitner and Heninger describe such cases in the paper
    // "Biased Nonce Sense: Lattice Attacks against Weak ECDSA Signatures in Cryptocurrencies",
    // https://eprint.iacr.org/2019/023.pdf
    // The following tests should catch the bugs described in this paper.
    // The threshold below has been chosen to give false positives with probability < 2^{-32}.
    double threshold = 5;

    // This test detects for example the case when either k or q-k is small.
    double bias1 = bias(kList, q, BigInteger.ONE);
    if (bias1 > threshold) {
      fail("Bias for k detected. bias1 = " + bias1);
    }
    // Same as above but shifing by one bit.
    double bias2 = bias(kList, q, BigInteger.valueOf(2));
    if (bias2 > threshold) {
      fail("Bias for k detected. bias2 = " + bias2);
    }
    double bias3 = bias(kList, q, qHalf);
    if (bias3 > threshold) {
      fail("Bias for k detected. bias3 = " + bias3);
    }
    // Checks whether most significant bytes, words, dwords or qwords are strongly correlated.
    for (int bits : new int[] {8, 16, 32, 64}) {
      BigInteger multiplier = BigInteger.ONE.shiftLeft(bits).subtract(BigInteger.ONE);
      double bias4 = bias(kList, q, multiplier);
      if (bias4 > threshold) {
        fail("Bias for k detected. bits = " + bits + " bias4 = " + bias4);
      }
    }
  }

  @Test
  public void testBiasSecp224r1() throws Exception {
    testBias("SHA224WithECDSA", "secp224r1");
  }

  @Test
  public void testBiasSecp256r1() throws Exception {
    testBias("SHA256WithECDSA", "secp256r1");
  }

  @Test
  public void testBiasSecp256r1_StrongBox() throws Exception {
    testBias("SHA256WithECDSA", "secp256r1", true);
  }

  @Test
  public void testBiasSecp384r1() throws Exception {
    testBias("SHA384WithECDSA", "secp384r1");
  }

  @Test
  public void testBiasSecp521r1() throws Exception {
    testBias("SHA512WithECDSA", "secp521r1");
  }

  @Test
  @Ignore // Brainpool curve are not supported in AndroidKeyStore
  public void testBiasBrainpoolP256r1() throws Exception {
    testBias("SHA512WithECDSA", "brainpoolP256r1");
  }

  /**
   * This test uses the deterministic ECDSA implementation from BouncyCastle (if BouncyCastle is
   * being tested.)
   */
  @Test
  @Ignore // Algorithm SHA256WithECDDSA is not supported in AndroidKeyStore.
  public void testBiasSecp256r1ECDDSA() throws Exception {
    testBias("SHA256WithECDDSA", "secp256r1");
  }

  /**
   * Tests initSign with a null value for SecureRandom. The expected behaviour is that a default
   * instance of SecureRandom is used and that this instance is properly seeded. I.e., the expected
   * behaviour is that Signature.initSign(ECPrivateKey, null) behaves like
   * Signature.initSign(ECPrivateKey). If the signature scheme normally is randomized then
   * Signature.initSign(ECprivateKey, null) should still be a randomized signature scheme. If the
   * implementation is deterministic then we simply want this to work.
   *
   * <p>In principle, the correct behaviour is not really defined. However, if a provider would
   * throw a null pointer exception then this can lead to unnecessary breakages.
   */
  public void testNullRandom(String algorithm, String curve) throws Exception {
    testNullRandom(algorithm, curve, false);
  }
  public void testNullRandom(String algorithm, String curve, boolean isStrongBox)
          throws Exception {
    if (isStrongBox) {
      KeyStoreUtil.assumeStrongBox();
    }
    int samples = 8;
    Signature signer = Signature.getInstance(algorithm);
    KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");
    keyGen.initialize(new ECGenParameterSpec(curve));
    KeyPair keyPair = keyGen.generateKeyPair();
    PrivateKey priv = getKeystorePrivateKey(keyPair.getPublic(), keyPair.getPrivate(),
            isStrongBox);
    byte[][] message = getMessagesToSign(samples, signer, priv);
    HashSet<BigInteger> rSet = new HashSet<>();
    for (int i = 0; i < samples; i++) {
      // This is the function call that is tested by this test.
      signer.initSign(priv, null);
      signer.update(message[i]);
      byte[] signature = signer.sign();
      BigInteger r = extractR(signature);
      assertTrue("Same r computed twice", rSet.add(r));
    }
  }

  @Test
  public void testNullRandomSecp224r1() throws Exception {
    testNullRandom("SHA224WithECDSA", "secp224r1");
  }

  @Test
  public void testNullRandomSecp256r1() throws Exception {
    testNullRandom("SHA256WithECDSA", "secp256r1");
  }

  @Test
  public void testNullRandomSecp256r1_StrongBox() throws Exception {
    testNullRandom("SHA256WithECDSA", "secp256r1", true);
  }

  @Test
  public void testNullRandomSecp384r1() throws Exception {
    testNullRandom("SHA384WithECDSA", "secp384r1");
  }

  @Test
  public void testNullRandomSecp521r1() throws Exception {
    testNullRandom("SHA512WithECDSA", "secp521r1");
  }

  /**
   * This test uses the deterministic ECDSA implementation from BouncyCastle (if BouncyCastle is
   * being tested.)
   */
  @Test
  @Ignore // Algorithm SHA256WithECdDSA is not supported in AndroidKeyStore.
  public void testNullRandomSecp256r1ECDDSA() throws Exception {
    testNullRandom("SHA256WithECdDSA", "secp256r1");
  }
}
