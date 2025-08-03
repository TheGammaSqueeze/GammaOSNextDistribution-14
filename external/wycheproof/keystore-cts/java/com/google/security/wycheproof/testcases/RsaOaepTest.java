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
import static org.junit.Assume.assumeTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import org.junit.After;
import org.junit.Test;
import org.junit.Ignore;
import android.security.Flags;
import android.security.keystore.KeyProtection;
import android.security.keystore.KeyProperties;
import android.keystore.cts.util.KeyStoreUtil;
import android.text.TextUtils;
import android.util.Log;

/**
 * Checks implementations of RSA-OAEP.
 */
public class RsaOaepTest {
  private static final String TAG = "RsaOaepTest";
  private static final String EXPECTED_PROVIDER_NAME = TestUtil.EXPECTED_CRYPTO_OP_PROVIDER_NAME;
  private static final String KEY_ALIAS_1 = "TestKey";

  @After
  public void tearDown() throws Exception {
    KeyStoreUtil.cleanUpKeyStore();
  }

  private static PrivateKey saveKeyPairToKeystoreAndReturnPrivateKey(PublicKey pubKey,
        PrivateKey privKey, String digest, String mgfDigest, boolean isStrongBox)
          throws Exception {
    KeyProtection.Builder keyProtection = new KeyProtection.Builder(KeyProperties.PURPOSE_SIGN |
            KeyProperties.PURPOSE_VERIFY |
            KeyProperties.PURPOSE_ENCRYPT |
            KeyProperties.PURPOSE_DECRYPT)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1,
                    KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
            .setIsStrongBoxBacked(isStrongBox);
    if (Flags.mgf1DigestSetterV2()) {
      keyProtection.setDigests(digest);
      keyProtection.setMgf1Digests(mgfDigest);
    } else {
      if (digest.equalsIgnoreCase(mgfDigest)) {
        keyProtection.setDigests(digest);
      } else {
        keyProtection.setDigests(digest, mgfDigest);
      }
    }
    return (PrivateKey) KeyStoreUtil.saveKeysToKeystore(KEY_ALIAS_1, pubKey, privKey,
            keyProtection.build()).getKey(KEY_ALIAS_1, null);
  }

  /**
   * A list of algorithm names for RSA-OAEP.
   *
   * The standard algorithm names for RSA-OAEP are defined in
   * https://docs.oracle.com/javase/8/docs/technotes/guides/security/StandardNames.html
   */
  static String[] OaepAlgorithmNames = {
      "RSA/None/OAEPPadding",
      "RSA/None/OAEPwithSHA-1andMGF1Padding",
      "RSA/None/OAEPwithSHA-224andMGF1Padding",
      "RSA/None/OAEPwithSHA-256andMGF1Padding",
      "RSA/None/OAEPwithSHA-384andMGF1Padding",
      "RSA/None/OAEPwithSHA-512andMGF1Padding",
  };

  protected static void printParameters(AlgorithmParameterSpec params) {
    if (params instanceof OAEPParameterSpec) {
      OAEPParameterSpec oaepParams = (OAEPParameterSpec) params;
      Log.d(TAG, "OAEPParameterSpec");
      Log.d(TAG, "digestAlgorithm:" + oaepParams.getDigestAlgorithm());
      Log.d(TAG, "mgfAlgorithm:" + oaepParams.getMGFAlgorithm());
      printParameters(oaepParams.getMGFParameters());
    } else if (params instanceof MGF1ParameterSpec) {
      MGF1ParameterSpec mgf1Params = (MGF1ParameterSpec) params;
      Log.d(TAG, "MGF1ParameterSpec");
      Log.d(TAG, "digestAlgorithm:" + mgf1Params.getDigestAlgorithm());
    } else {
      Log.d(TAG, params.toString());
    }
  }

  /**
   * This is not a real test. The JCE algorithm names only specify one hash algorithm. But OAEP
   * uses two hases. One hash algorithm is used to hash the labels. The other hash algorithm is
   * used for the mask generation function.
   *
   * <p>Different provider use different default values for the hash function that is not specified
   * in the algorithm name. Jdk uses mgfsha1 as default. BouncyCastle and Conscrypt use the same
   * hash for labels and mgf. Every provider allows to specify all the parameters using
   * an OAEPParameterSpec instance.
   *
   * <p>This test simply tries a number of algorithm names for RSA-OAEP and prints the OAEP
   * parameters for the case where no OAEPParameterSpec is used.
   */
  // TODO(bleichen): jdk11 will also add parameters to the RSA keys. This will need more tests.
  @Test
  public void testDefaults() throws Exception {
    String pubKey =
        "30820122300d06092a864886f70d01010105000382010f003082010a02820101"
            + "00bdf90898577911c71c4d9520c5f75108548e8dfd389afdbf9c997769b8594e"
            + "7dc51c6a1b88d1670ec4bb03fa550ba6a13d02c430bfe88ae4e2075163017f4d"
            + "8926ce2e46e068e88962f38112fc2dbd033e84e648d4a816c0f5bd89cadba0b4"
            + "d6cac01832103061cbb704ebacd895def6cff9d988c5395f2169a6807207333d"
            + "569150d7f569f7ebf4718ddbfa2cdbde4d82a9d5d8caeb467f71bfc0099b0625"
            + "a59d2bad12e3ff48f2fd50867b89f5f876ce6c126ced25f28b1996ee21142235"
            + "fb3aef9fe58d9e4ef6e4922711a3bbcd8adcfe868481fd1aa9c13e5c658f5172"
            + "617204314665092b4d8dca1b05dc7f4ecd7578b61edeb949275be8751a5a1fab"
            + "c30203010001";
    KeyFactory kf;
    kf = KeyFactory.getInstance("RSA");
    X509EncodedKeySpec x509keySpec = new X509EncodedKeySpec(TestUtil.hexToBytes(pubKey));
    PublicKey key = kf.generatePublic(x509keySpec);
    for (String oaepName : OaepAlgorithmNames) {
        Cipher c = Cipher.getInstance(oaepName, EXPECTED_PROVIDER_NAME);
        c.init(Cipher.ENCRYPT_MODE, key);
        Log.d(TAG, "Algorithm " + oaepName + " uses the following defaults");
        AlgorithmParameters params = c.getParameters();
        printParameters(params.getParameterSpec(OAEPParameterSpec.class));
    }
  }

  /** Convenience mehtod to get a String from a JsonObject */
  protected static String getString(JsonObject object, String name) throws Exception {
    return object.get(name).getAsString();
  }

  /** Convenience method to get a byte array from a JsonObject */
  protected static byte[] getBytes(JsonObject object, String name) throws Exception {
    return JsonUtil.asByteArray(object.get(name));
  }

  /**
   * Get a PublicKey from a JsonObject.
   *
   * <p>object contains the key in multiple formats: "key" : elements of the public key "keyDer":
   * the key in ASN encoding encoded hexadecimal "keyPem": the key in Pem format encoded hexadecimal
   * The test can use the format that is most convenient.
   */
  // This is a false positive, since errorprone cannot track values passed into a method.
  @SuppressWarnings("InsecureCryptoUsage")
  protected static PrivateKey getPrivateKey(JsonObject object, boolean isStrongBox)
          throws Exception {
    KeyFactory kf;
    kf = KeyFactory.getInstance("RSA");
    byte[] encoded = TestUtil.hexToBytes(getString(object, "privateKeyPkcs8"));
    PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(encoded);
    PrivateKey intermediateKey = kf.generatePrivate(keySpec);
    BigInteger modulus = new BigInteger(TestUtil.hexToBytes(object.get("n").getAsString()));
    BigInteger exponent = new BigInteger(TestUtil.hexToBytes(object.get("e").getAsString()));
    PublicKey pubKey = kf.generatePublic(new RSAPublicKeySpec(modulus, exponent));
    String digest = getString(object, "sha");
    String mgfDigest = getString(object, "mgfSha");
    int keysize = object.get("keysize").getAsInt();
    if (!KeyStoreUtil.isSupportedDigest(digest, isStrongBox)
          || !KeyStoreUtil.isSupportedMgfDigest(mgfDigest, isStrongBox)
          || !KeyStoreUtil.isSupportedRsaKeySize(keysize, isStrongBox)) {
      throw new UnsupportedKeyParametersException();
    }
    return saveKeyPairToKeystoreAndReturnPrivateKey(pubKey, intermediateKey, digest, mgfDigest,
            isStrongBox);
  }

  protected static String getOaepAlgorithmName(JsonObject group) throws Exception {
    String mgf = getString(group, "mgf");
    String mgfSha = getString(group, "mgfSha");
    return "RSA/ECB/OAEPwith" + mgfSha + "and" + mgf + "Padding";
  }

  protected static OAEPParameterSpec getOaepParameters(JsonObject group,
    JsonObject test, boolean isStrongBox) throws Exception {
    String sha = getString(group, "sha");
    String mgf = getString(group, "mgf");
    String mgfSha = getString(group, "mgfSha");
    // mgfDigest other than SHA-1 are supported from KeyMint V1 and above but some implementations
    // of keymint V1 and V2 (notably the C++ reference implementation) does not include MGF_DIGEST
    // tag in key characteriestics hence issue b/287532460 introduced. So non-default MGF_DIGEST is
    // tested on Keymint V3 and above.
    if (!mgfSha.equalsIgnoreCase("SHA-1")) {
      assumeTrue("This test is valid for KeyMint version 3 and above.",
          KeyStoreUtil.getFeatureVersionKeystore(isStrongBox) >= KeyStoreUtil.KM_VERSION_KEYMINT_3);
    }
    PSource p = PSource.PSpecified.DEFAULT;
    if (test.has("label") && !TextUtils.isEmpty(getString(test, "label"))) {
      // p = new PSource.PSpecified(getBytes(test, "label"));
      throw new UnsupportedKeyParametersException();
    }
    return new OAEPParameterSpec(sha, mgf, new MGF1ParameterSpec(mgfSha), p);
  }

  /**
   * Tests the signature verification with test vectors in a given JSON file.
   *
   * <p> Example format for test vectors
   * { "algorithm" : "RSA-OAEP",
   *   "schema" : "rsaes_oaep_decrypt_schema.json",
   *   "generatorVersion" : "0.7",
   *   ...
   *   "testGroups" : [
   *     {
   *       "d" : "...",
   *       "e" : "10001",
   *       "n" : "...",
   *       "keysize" : 2048,
   *       "sha" : "SHA-256",
   *       "mgf" : "MGF1",
   *       "mgfSha" : "SHA-256",
   *       "privateKeyPem" : "-----BEGIN RSA PRIVATE KEY-----\n...",
   *       "privateKeyPkcs8" : "...",
   *       "type" : "RSAES",
   *       "tests" : [
   *         {
   *           "tcId" : 1,
   *           "comment" : "",
   *           "msg" : "30313233343030",
   *           "ct" : "...", 
   *           "label" : "",
   *           "result" : "valid",
   *           "flags" : [],
   *         },
   *        ...
   *
   * @param filename the filename of the test vectors
   * @param allowSkippingKeys if true then keys that cannot be constructed will not fail the test.
   *        Most of the tests below are using allowSkippingKeys == false. The reason for doing this
   *        is that providers have distinctive defaults. E.g., no OAEPParameterSpec is given then
   *        BouncyCastle and Conscrypt use the same hash function for hashing the label and for the
   *        mask generation function, while jdk uses MGF1SHA1. This is unfortunate and probably
   *        difficult to fix. Hence, the tests below simply require that providers support each
   *        others default parameters under the assumption that the OAEPParameterSpec is fully
   *        specified.
   **/
  public void testOaep(String filename, boolean allowSkippingKeys)
          throws Exception {
    testOaep(filename, allowSkippingKeys, false);
  }

  private static class UnsupportedKeyParametersException extends Exception { }

  public void testOaep(String filename, boolean allowSkippingKeys, boolean isStrongBox)
      throws Exception {
    if (isStrongBox) {
      KeyStoreUtil.assumeStrongBox();
    }
    JsonObject test = JsonUtil.getTestVectors(this.getClass(), filename);

    // Compares the expected and actual JSON schema of the test vector file.
    // Mismatched JSON schemas will likely lead to a test failure.
    String generatorVersion = getString(test, "generatorVersion");
    String expectedSchema = "rsaes_oaep_decrypt_schema.json";
    String actualSchema = getString(test, "schema");
    assertTrue(
          "Expecting test vectors with schema "
              + expectedSchema
              + " found vectors with schema "
              + actualSchema
              + " generatorVersion:"
              + generatorVersion,
          expectedSchema.equals(actualSchema));

    int numTests = test.get("numberOfTests").getAsInt();
    int cntTests = 0;
    int errors = 0;
    int skippedKeys = 0;
    for (JsonElement g : test.getAsJsonArray("testGroups")) {
      JsonObject group = g.getAsJsonObject();
      PrivateKey key = null;
      try {
        key = getPrivateKey(group, isStrongBox);
      } catch (UnsupportedKeyParametersException e) {
        skippedKeys++;
        if (!allowSkippingKeys) {
          throw e;
        } else {
          continue;
        }
      }
      String algorithm = getOaepAlgorithmName(group);
      Cipher decrypter = Cipher.getInstance(algorithm, EXPECTED_PROVIDER_NAME);
      for (JsonElement t : group.getAsJsonArray("tests")) {
        cntTests++;
        JsonObject testcase = t.getAsJsonObject();
        int tcid = testcase.get("tcId").getAsInt();
        String messageHex = TestUtil.bytesToHex(getBytes(testcase, "msg"));
        OAEPParameterSpec params;
        try {
          params = getOaepParameters(group, testcase, isStrongBox);
        } catch (UnsupportedKeyParametersException e) {
          // TODO This try catch block should be removed once issue b/229183581 is fixed.
          continue;
        }
        byte[] ciphertext = getBytes(testcase, "ct");
        String ciphertextHex = TestUtil.bytesToHex(ciphertext);
        String result = getString(testcase, "result");
        byte[] decrypted = null;
        try {
          decrypter.init(Cipher.DECRYPT_MODE, key, params);
          decrypted = decrypter.doFinal(ciphertext);
        } catch (GeneralSecurityException ex) {
          decrypted = null;
        } catch (Exception ex) {
          // Other exceptions (i.e. unchecked exceptions) are considered as error
          // since a third party should never be able to cause such exceptions.
          Log.e(TAG, String.format("Decryption throws %s. filename:%s tcId:%d ct:%s\n",
                            ex.toString(), filename, tcid, ciphertextHex));
          decrypted = null;
          // TODO(bleichen): BouncyCastle throws some non-conforming exceptions.
          //   For the moment we do not count this as a problem to avoid that
          //   more serious bugs remain hidden.
          // errors++;
        }
        if (decrypted == null && result.equals("valid")) {
            Log.e(TAG,
                String.format("Valid ciphertext not decrypted. filename:%s tcId:%d ct:%s\n",
                filename, tcid, ciphertextHex));
          errors++;
        } else if (decrypted != null) {
          String decryptedHex = TestUtil.bytesToHex(decrypted);
          if (result.equals("invalid")) {
            Log.e(TAG,
                String.format("Invalid ciphertext decrypted. filename:%s tcId:%d expected:%s" +
                              " decrypted:%s\n", filename, tcid, messageHex, decryptedHex));
             errors++;
          } else if (!decryptedHex.equals(messageHex)) {
            Log.e(TAG,
                String.format("Incorrect decryption. filename:%s tcId:%d expected:%s" +
                              " decrypted:%s\n", filename, tcid, messageHex, decryptedHex));
             errors++;
          }
        }
      }
    }
    assertEquals(0, errors);
    if (skippedKeys > 0) {
      Log.d(TAG, "RSAES-OAEP: file:" + filename + " skipped key:" + skippedKeys);
      assertTrue(allowSkippingKeys);
    } else {
      assertEquals(numTests, cntTests);
    }
  }

  @Test
  public void testRsaOaep2048Sha1Mgf1Sha1() throws Exception {
    // b/244609904#comment64
    KeyStoreUtil.assumeKeyMintV1OrNewer(false);
   testOaep("rsa_oaep_2048_sha1_mgf1sha1_test.json", false);
  }

  @Test
  public void testRsaOaep2048Sha1Mgf1Sha1_StrongBox() throws Exception {
    testOaep("rsa_oaep_2048_sha1_mgf1sha1_test.json", true, true);
  }

  @Test
  public void testRsaOaep2048Sha224Mgf1Sha1() throws Exception {
   testOaep("rsa_oaep_2048_sha224_mgf1sha1_test.json", false);
  }

  @Test
  public void testRsaOaep2048Sha224Mgf1Sha224() throws Exception {
   testOaep("rsa_oaep_2048_sha224_mgf1sha224_test.json", false);
  }

  @Test
  public void testRsaOaep2048Sha256Mgf1Sha1() throws Exception {
    testOaep("rsa_oaep_2048_sha256_mgf1sha1_test.json", false);
  }
  @Test
  public void testRsaOaep2048Sha256Mgf1Sha1_StrongBox() throws Exception {
    testOaep("rsa_oaep_2048_sha256_mgf1sha1_test.json", false, true);
  }

  @Test
  public void testRsaOaep2048Sha256Mgf1Sha256() throws Exception {
    testOaep("rsa_oaep_2048_sha256_mgf1sha256_test.json", false);
  }
  @Test
  public void testRsaOaep2048Sha256Mgf1Sha256_StrongBox() throws Exception {
    testOaep("rsa_oaep_2048_sha256_mgf1sha256_test.json", false, true);
  }

  @Test
  public void testRsaOaep2048Sha384Mgf1Sha1() throws Exception {
   testOaep("rsa_oaep_2048_sha384_mgf1sha1_test.json", false);
  }

  @Test
  public void testRsaOaep2048Sha384Mgf1Sha384() throws Exception {
   testOaep("rsa_oaep_2048_sha384_mgf1sha384_test.json", false);
  }

  @Test
  public void testRsaOaep2048Sha512Mgf1Sha1() throws Exception {
   testOaep("rsa_oaep_2048_sha512_mgf1sha1_test.json", false);
  }

  @Test
  public void testRsaOaep2048Sha512Mgf1Sha512() throws Exception {
   testOaep("rsa_oaep_2048_sha512_mgf1sha512_test.json", false);
  }

  @Test
  public void testRsaOaep3072Sha256Mgf1Sha1() throws Exception {
    // b/244609904#comment64
    KeyStoreUtil.assumeKeyMintV1OrNewer(false);
   testOaep("rsa_oaep_3072_sha256_mgf1sha1_test.json", false);
  }

  @Test
  public void testRsaOaep3072Sha256Mgf1Sha256() throws Exception {
   testOaep("rsa_oaep_3072_sha256_mgf1sha256_test.json", false);
  }

  @Test
  public void testRsaOaep3072Sha512Mgf1Sha1() throws Exception {
   testOaep("rsa_oaep_3072_sha512_mgf1sha1_test.json", false);
  }

  @Test
  public void testRsaOaep3072Sha512Mgf1Sha512() throws Exception {
   testOaep("rsa_oaep_3072_sha512_mgf1sha512_test.json", false);
  }

  @Test
  public void testRsaOaep4096Sha256Mgf1Sha1() throws Exception {
    // b/244609904#comment64
    KeyStoreUtil.assumeKeyMintV1OrNewer(false);
   testOaep("rsa_oaep_4096_sha256_mgf1sha1_test.json", false);
  }

  @Test
  public void testRsaOaep4096Sha256Mgf1Sha256() throws Exception {
   testOaep("rsa_oaep_4096_sha256_mgf1sha256_test.json", false);
  }

  @Test
  public void testRsaOaep4096Sha512Mgf1Sha1() throws Exception {
   testOaep("rsa_oaep_4096_sha512_mgf1sha1_test.json", false);
  }

  @Test
  public void testRsaOaep4096Sha512Mgf1Sha512() throws Exception {
   testOaep("rsa_oaep_4096_sha512_mgf1sha512_test.json", false);
  }

  @Test
  public void testRsaOaepMisc() throws Exception {
    testOaep("rsa_oaep_misc_test.json", true);
  }
  @Test
  public void testRsaOaepMisc_StrongBox() throws Exception {
    testOaep("rsa_oaep_misc_test.json", true, true);
  }
}

