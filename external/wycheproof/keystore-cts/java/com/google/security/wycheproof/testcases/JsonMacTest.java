/**
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.security.wycheproof;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.security.Key;
import java.security.KeyStore;
import java.security.GeneralSecurityException;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.After;
import org.junit.Test;
import org.junit.Ignore;
import android.security.keystore.KeyProtection;
import android.security.keystore.KeyProperties;
import java.io.IOException;
import android.keystore.cts.util.KeyStoreUtil;

/** This test uses test vectors in JSON format to test MAC primitives. */
public class JsonMacTest {
  private static final String EXPECTED_PROVIDER_NAME = TestUtil.EXPECTED_CRYPTO_OP_PROVIDER_NAME;
  private static final String KEY_ALIAS_1 = "Key1";

  @After
  public void tearDown() throws Exception {
    KeyStoreUtil.cleanUpKeyStore();
  }

  /** Convenience method to get a byte array from an JsonObject */
  protected static byte[] getBytes(JsonObject obj, String name) throws Exception {
    return JsonUtil.asByteArray(obj.get(name));
  }

  protected static boolean arrayEquals(byte[] a, byte[] b) {
    if (a.length != b.length) {
      return false;
    }
    byte res = 0;
    for (int i = 0; i < a.length; i++) {
      res |= (byte) (a[i] ^ b[i]);
    }
    return res == 0;
  }

  /**
   * Computes a MAC.
   *
   * @param algorithm the algorithm.
   * @param key the key bytes
   * @param msg the message to MAC.
   * @param tagSize the expected size of the tag in bits.
   * @return the tag
   * @throws GeneralSecurityException if the algorithm or the parameter sizes are not supported or
   *     if the initialization failed. For example one case are GMACs with a tag size othe than 128
   *     bits, since the JCE interface does not seem to support such a specification.
   */
  protected static byte[] computeMac(String algorithm, byte[] key, byte[] msg, int tagSize,
                                     boolean isStrongBox) throws Exception {
    Mac mac = Mac.getInstance(algorithm, EXPECTED_PROVIDER_NAME);
    algorithm = algorithm.toUpperCase(Locale.ENGLISH);
    if (algorithm.startsWith("HMAC")) {
      SecretKeySpec keySpec = new SecretKeySpec(key, algorithm);
      // TODO(bleichen): Is there a provider independent truncation?
      //   The class javax.xml.crypto.dsig.spec.HMACParameterSpec would allow to
      //   truncate HMAC tags as follows:
      //   <pre>
      //     HMACParameterSpec params = new HMACParameterSpec(tagSize);
      //     mac.init(keySpec, params);
      //     mac.update(msg);
      //     return mac.doFinal();
      //   </pre>
      //   But this class is often not supported. Hence the computation here, just computes a
      //   full length tag and truncates it. The drawback of having to truncate tags is that
      //   the caller has to compare truncated tags during verification.
      KeyStore keyStore = KeyStoreUtil.saveSecretKeyToKeystore(KEY_ALIAS_1, keySpec, 
              new KeyProtection.Builder(KeyProperties.PURPOSE_SIGN)
                      .setIsStrongBoxBacked(isStrongBox)
                      .build());
      // Key imported, obtain a reference to it.
      Key keyStoreKey = keyStore.getKey(KEY_ALIAS_1, null);
      mac.init(keyStoreKey);
      mac.update(msg);
      byte[] tag = mac.doFinal();
      return Arrays.copyOf(tag, tagSize / 8);
    } else {
      throw new NoSuchAlgorithmException(algorithm);
    }
  }

  /**
   * Tests a randomized MAC (i.e. a message authetication that takes an additional IV as parameter)
   * against test vectors.
   *
   * @param filename the JSON file with the test vectors.
   */
  public void testMac(String filename) throws Exception {
    testMac(filename, false);
  }
  public void testMac(String filename, boolean isStrongBox) throws Exception {
    // Checking preconditions.
    JsonObject test = JsonUtil.getTestVectors(this.getClass(), filename);
    String algorithm = test.get("algorithm").getAsString();
    Mac.getInstance(algorithm, EXPECTED_PROVIDER_NAME);

    int numTests = test.get("numberOfTests").getAsInt();
    int cntTests = 0;
    int passedTests = 0;
    int errors = 0;
    for (JsonElement g : test.getAsJsonArray("testGroups")) {
      JsonObject group = g.getAsJsonObject();
      int tagSize = group.get("tagSize").getAsInt();
      for (JsonElement t : group.getAsJsonArray("tests")) {
        cntTests++;
        JsonObject testcase = t.getAsJsonObject();
        int tcid = testcase.get("tcId").getAsInt();
        String tc = "tcId: " + tcid + " " + testcase.get("comment").getAsString();
        byte[] key = getBytes(testcase, "key");
        byte[] msg = getBytes(testcase, "msg");
        byte[] expectedTag = getBytes(testcase, "tag");
        // Strongbox only supports key size from 8 to 32 bytes.
        if (isStrongBox && (key.length < 8 || key.length > 32)) {
          continue;
        }
        // Result is one of "valid", "invalid", "acceptable".
        // "valid" are test vectors with matching plaintext, ciphertext and tag.
        // "invalid" are test vectors with invalid parameters or invalid ciphertext and tag.
        // "acceptable" are test vectors with weak parameters or legacy formats.
        String result = testcase.get("result").getAsString();
        byte[] computedTag = null;
        computedTag = computeMac(algorithm, key, msg, tagSize, isStrongBox);

        boolean eq = arrayEquals(expectedTag, computedTag);
        if (result.equals("invalid")) {
          if (eq) {
            // Some test vectors use invalid parameters that should be rejected.
            // E.g. an implementation must not allow AES-GMAC with an IV of length 0,
            // since this leaks the authentication key.
            errors++;
          }
        } else {
          if (eq) {
            passedTests++;
          } else {
            errors++;
          }
        }
      }
    }
    assertEquals(0, errors);
    assertEquals(numTests, cntTests);
  }

  /**
   * Returns an initialized instance of a randomized MAC.
   *
   * @param algorithm the algorithm.
   * @param key the key bytes
   * @param iv the bytes of the initialization vector
   * @param tagSize the expected size of the tag in bits.
   * @return an initialized instance of a MAC.
   * @throws GeneralSecurityException if the algorithm or the parameter sizes are not supported or
   *     if the initialization failed. For example one case are GMACs with a tag size othe than 128
   *     bits, since the JCE interface does not seem to support such a specification.
   */
  protected static Mac getInitializedMacWithIv(String algorithm, byte[] key, byte[] iv, int tagSize)
      throws GeneralSecurityException {
    Mac mac = Mac.getInstance(algorithm, EXPECTED_PROVIDER_NAME);
    algorithm = algorithm.toUpperCase(Locale.ENGLISH);
    if (algorithm.equals("AES-GMAC")) {
      SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
      if (tagSize != 128) {
        throw new InvalidAlgorithmParameterException("only 128-bit tag is supported");
      }
      IvParameterSpec params = new IvParameterSpec(iv);
      // TODO(bleichen): I'm unaware of a method that allows to specify the tag size in JCE.
      //   E.g. the following parameter specification does not work (at least not in BC):
      //   GCMParameterSpec params = new GCMParameterSpec(tagSize, iv);
      mac.init(keySpec, params);
      return mac;
    } else {
      throw new NoSuchAlgorithmException(algorithm);
    }
  }

  /**
   * Tests a randomized MAC (i.e. a message authetication that takes an additional IV as
   * parameter) against test vectors.
   *
   * @param filename the JSON file with the test vectors.
   * @param algorithm the JCE name of the algorithm to test.
   */
  public void testMacWithIv(String filename, String algorithm) throws Exception {
    // Checking preconditions.
    Mac.getInstance(algorithm, EXPECTED_PROVIDER_NAME);

    JsonObject test = JsonUtil.getTestVectors(this.getClass(), filename);
    int numTests = test.get("numberOfTests").getAsInt();
    int cntTests = 0;
    int passedTests = 0;
    int errors = 0;
    for (JsonElement g : test.getAsJsonArray("testGroups")) {
      JsonObject group = g.getAsJsonObject();
      int tagSize = group.get("tagSize").getAsInt();
      for (JsonElement t : group.getAsJsonArray("tests")) {
        cntTests++;
        JsonObject testcase = t.getAsJsonObject();
        int tcid = testcase.get("tcId").getAsInt();
        String tc = "tcId: " + tcid + " " + testcase.get("comment").getAsString();
        byte[] key = getBytes(testcase, "key");
        byte[] iv = getBytes(testcase, "iv");
        byte[] msg = getBytes(testcase, "msg");
        byte[] expectedTag = getBytes(testcase, "tag");
        // Result is one of "valid", "invalid", "acceptable".
        // "valid" are test vectors with matching plaintext, ciphertext and tag.
        // "invalid" are test vectors with invalid parameters or invalid ciphertext and tag.
        // "acceptable" are test vectors with weak parameters or legacy formats.
        String result = testcase.get("result").getAsString();

        Mac mac = getInitializedMacWithIv(algorithm, key, iv, tagSize);

        byte[] computedTag = mac.doFinal(msg);
        boolean eq = arrayEquals(expectedTag, computedTag);
        if (result.equals("invalid")) {
          if (eq) {
            // Some test vectors use invalid parameters that should be rejected.
            // E.g. an implementation must not allow AES-GMAC with an IV of length 0,
            // since this leaks the authentication key.
            errors++;
          }
        } else {
          if (eq) {
            passedTests++;
          } else {
            errors++;
          }
        }
      }
    }
    assertEquals(0, errors);
    assertEquals(numTests, cntTests);
  }

  @Test
  public void testHmacSha1() throws Exception {
    testMac("hmac_sha1_test.json");
  }

  @Test
  public void testHmacSha224() throws Exception {
    testMac("hmac_sha224_test.json");
  }

  @Test
  public void testHmacSha256() throws Exception {
    testMac("hmac_sha256_test.json");
  }
  @Test
  public void testHmacSha256_StrongBox() throws Exception {
    KeyStoreUtil.assumeStrongBox();
    testMac("hmac_sha256_test.json", true);
  }

  @Test
  public void testHmacSha384() throws Exception {
    testMac("hmac_sha384_test.json");
  }

  @Test
  public void testHmacSha512() throws Exception {
    testMac("hmac_sha512_test.json");
  }

  @Test
  @Ignore // HMAC Sha3 algorithms are not supported in AndroidKeyStore
  public void testHmacSha3_224() throws Exception {
    testMac("hmac_sha3_224_test.json");
  }

  @Test
  @Ignore // HMAC Sha3 algorithms are not supported in AndroidKeyStore
  public void testHmacSha3_256() throws Exception {
    testMac("hmac_sha3_256_test.json");
  }

  @Test
  @Ignore // HMAC Sha3 algorithms are not supported in AndroidKeyStore
  public void testHmacSha3_384() throws Exception {
    testMac("hmac_sha3_384_test.json");
  }

  @Test
  @Ignore // HMAC Sha3 algorithms are not supported in AndroidKeyStore
  public void testHmacSha3_512() throws Exception {
    testMac("hmac_sha3_512_test.json");
  }

  @Test
  @Ignore // Ignored due to AES-GMAC algorithm not supported in AndroidKeyStore
  public void testAesGmac() throws Exception {
    testMacWithIv("gmac_test.json", "AES-GMAC");
  }
}
