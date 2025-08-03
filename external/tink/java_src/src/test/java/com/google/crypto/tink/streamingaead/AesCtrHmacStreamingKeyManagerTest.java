// Copyright 2017 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
////////////////////////////////////////////////////////////////////////////////

package com.google.crypto.tink.streamingaead;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.crypto.tink.KeyTemplate;
import com.google.crypto.tink.KeyTemplates;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.StreamingAead;
import com.google.crypto.tink.internal.KeyTypeManager;
import com.google.crypto.tink.internal.Util;
import com.google.crypto.tink.proto.AesCtrHmacStreamingKey;
import com.google.crypto.tink.proto.AesCtrHmacStreamingKeyFormat;
import com.google.crypto.tink.proto.AesCtrHmacStreamingParams;
import com.google.crypto.tink.proto.HashType;
import com.google.crypto.tink.proto.HmacParams;
import com.google.crypto.tink.proto.KeyData.KeyMaterialType;
import com.google.crypto.tink.subtle.Hex;
import com.google.crypto.tink.testing.StreamingTestUtil;
import com.google.protobuf.ByteString;
import java.security.GeneralSecurityException;
import java.util.Set;
import java.util.TreeSet;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.FromDataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

/** Test for AesCtrHmacStreamingKeyManager. */
@RunWith(Theories.class)
public class AesCtrHmacStreamingKeyManagerTest {
  private final AesCtrHmacStreamingKeyManager manager = new AesCtrHmacStreamingKeyManager();
  private final KeyTypeManager.KeyFactory<AesCtrHmacStreamingKeyFormat, AesCtrHmacStreamingKey>
      factory = manager.keyFactory();

  @Before
  public void register() throws Exception {
    StreamingAeadConfig.register();
  }

  // Returns an HmacParams.Builder with valid parameters
  private static HmacParams.Builder createHmacParams() {
    return HmacParams.newBuilder().setHash(HashType.SHA256).setTagSize(32);
  }

  // Returns an AesCtrHmacStreamingParams.Builder with valid parameters
  private static AesCtrHmacStreamingParams.Builder createParams() {
    return AesCtrHmacStreamingParams.newBuilder()
        .setCiphertextSegmentSize(1024)
        .setDerivedKeySize(32)
        .setHkdfHashType(HashType.SHA256)
        .setHmacParams(createHmacParams());
  }

  // Returns an AesCtrHmacStreamingKeyFormat.Builder with valid parameters
  private static AesCtrHmacStreamingKeyFormat.Builder createKeyFormat() {
    return AesCtrHmacStreamingKeyFormat.newBuilder().setKeySize(32).setParams(createParams());
  }

  // Returns a valid AesCtrHmacStreamingKey.Builder
  private static AesCtrHmacStreamingKey.Builder createKey() {
    return AesCtrHmacStreamingKey.newBuilder()
        .setParams(createParams())
        .setVersion(0)
        .setKeyValue(ByteString.copyFrom("This is a 32 byte random key.   ", Util.UTF_8));
  }

  @Test
  public void basics() throws Exception {
    assertThat(manager.getKeyType())
        .isEqualTo("type.googleapis.com/google.crypto.tink.AesCtrHmacStreamingKey");
    assertThat(manager.getVersion()).isEqualTo(0);
    assertThat(manager.keyMaterialType()).isEqualTo(KeyMaterialType.SYMMETRIC);
  }

  @Test
  public void validateKeyFormat_empty_throws() throws Exception {
    assertThrows(
        GeneralSecurityException.class,
        () -> factory.validateKeyFormat(AesCtrHmacStreamingKeyFormat.getDefaultInstance()));
  }

  @Test
  public void validateKeyFormat_valid() throws Exception {
    AesCtrHmacStreamingKeyFormat format = createKeyFormat().build();

    factory.validateKeyFormat(format);
  }

  @Test
  public void validateKeyFormat_derivedKeySizes() throws Exception {
    for (int derivedKeySize = 0; derivedKeySize < 42; ++derivedKeySize) {
      AesCtrHmacStreamingKeyFormat format =
          createKeyFormat().setParams(createParams().setDerivedKeySize(derivedKeySize)).build();
      if (derivedKeySize == 16 || derivedKeySize == 32) {
        factory.validateKeyFormat(format);
      } else {
        assertThrows(GeneralSecurityException.class, () -> factory.validateKeyFormat(format));
      }
    }
  }

  @Test
  public void validateKeyFormat_smallKey_throws() throws Exception {
    // TODO(b/140161847): Also check for key size 16.
    AesCtrHmacStreamingKeyFormat format = createKeyFormat().setKeySize(15).build();
    assertThrows(GeneralSecurityException.class, () -> factory.validateKeyFormat(format));
  }

  @Test
  public void validateKeyFormat_unkownHash_throws() throws Exception {
    AesCtrHmacStreamingKeyFormat format =
        createKeyFormat().setParams(createParams().setHkdfHashType(HashType.UNKNOWN_HASH)).build();
    assertThrows(GeneralSecurityException.class, () -> factory.validateKeyFormat(format));
  }

  @Test
  public void validateKeyFormat_unkownHmacHash_throws() throws Exception {
    AesCtrHmacStreamingKeyFormat format =
        createKeyFormat()
            .setParams(
                createParams().setHmacParams(createHmacParams().setHash(HashType.UNKNOWN_HASH)))
            .build();
    assertThrows(GeneralSecurityException.class, () -> factory.validateKeyFormat(format));
  }

  @Test
  public void validateKeyFormat_smallSegment_throws() throws Exception {
    AesCtrHmacStreamingKeyFormat format =
        createKeyFormat().setParams(createParams().setCiphertextSegmentSize(45)).build();

    assertThrows(GeneralSecurityException.class, () -> factory.validateKeyFormat(format));
  }

  @Test
  public void validateKeyFormat_tagSizeTooBigSha1_throws() throws Exception {
    AesCtrHmacStreamingKeyFormat format =
        createKeyFormat()
            .setParams(
                createParams()
                    .setHmacParams(createHmacParams().setHash(HashType.SHA1).setTagSize(21)))
            .build();

    assertThrows(GeneralSecurityException.class, () -> factory.validateKeyFormat(format));
  }

  @Test
  public void validateKeyFormat_tagSizeTooBigSha256_throws() throws Exception {
    AesCtrHmacStreamingKeyFormat format =
        createKeyFormat()
            .setParams(
                createParams()
                    .setHmacParams(createHmacParams().setHash(HashType.SHA256).setTagSize(33)))
            .build();

    assertThrows(GeneralSecurityException.class, () -> factory.validateKeyFormat(format));
  }

  @Test
  public void validateKeyFormat_tagSizeTooBigSha512_throws() throws Exception {
    AesCtrHmacStreamingKeyFormat format =
        createKeyFormat()
            .setParams(
                createParams()
                    .setHmacParams(createHmacParams().setHash(HashType.SHA512).setTagSize(65)))
            .build();

    assertThrows(GeneralSecurityException.class, () -> factory.validateKeyFormat(format));
  }

  @Test
  public void validateKeyFormat_ciphertextSegmentSizeOverflow_throws() throws Exception {
    // ciphertext_segment_size is uint in the proto, so we check that overflows make the manager
    // fail.
    AesCtrHmacStreamingKeyFormat format =
        createKeyFormat()
            .setParams(createParams().setCiphertextSegmentSize(Integer.MIN_VALUE).build())
            .build();

    assertThrows(GeneralSecurityException.class, () -> factory.validateKeyFormat(format));
  }

  @Test
  public void validateKey_validKey_works() throws Exception {
    AesCtrHmacStreamingKey key = createKey().build();

    manager.validateKey(key);
  }

  @Test
  public void validateKey_badHkdfHashType_throws() throws Exception {
    AesCtrHmacStreamingKey key =
        createKey().setParams(createParams().setHkdfHashType(HashType.SHA224)).build();

    assertThrows(GeneralSecurityException.class, () -> manager.validateKey(key));
  }

  @Test
  public void createKey_values() throws Exception {
    AesCtrHmacStreamingKeyFormat format = createKeyFormat().build();
    AesCtrHmacStreamingKey key = factory.createKey(format);
    assertThat(key.getVersion()).isEqualTo(0);
    assertThat(key.getKeyValue()).hasSize(format.getKeySize());
    assertThat(key.getParams()).isEqualTo(format.getParams());
  }

  @Test
  public void testSkip() throws Exception {
    AesCtrHmacStreamingKeyFormat format = createKeyFormat().build();
    AesCtrHmacStreamingKey key = factory.createKey(format);
    StreamingAead streamingAead = manager.getPrimitive(key, StreamingAead.class);
    int offset = 0;
    int plaintextSize = 1 << 16;
    // Runs the test with different sizes for the chunks to skip.
    StreamingTestUtil.testSkipWithStream(streamingAead, offset, plaintextSize, 1);
    StreamingTestUtil.testSkipWithStream(streamingAead, offset, plaintextSize, 64);
    StreamingTestUtil.testSkipWithStream(streamingAead, offset, plaintextSize, 300);
  }

  @Test
  public void createKey_multipleTimes_differentValues() throws Exception {
    AesCtrHmacStreamingKeyFormat keyFormat = createKeyFormat().build();
    Set<String> keys = new TreeSet<>();
    // Calls newKey multiple times and make sure that they generate different keys.
    int numTests = 100;
    for (int i = 0; i < numTests; i++) {
      keys.add(Hex.encode(factory.createKey(keyFormat).getKeyValue().toByteArray()));
    }
    assertThat(keys).hasSize(numTests);
  }

  @Test
  public void testAes128CtrHmacSha2564KBTemplate() throws Exception {
    KeyTemplate template = AesCtrHmacStreamingKeyManager.aes128CtrHmacSha2564KBTemplate();
    assertThat(template.toParameters())
        .isEqualTo(
            AesCtrHmacStreamingParameters.builder()
                .setKeySizeBytes(16)
                .setDerivedKeySizeBytes(16)
                .setHkdfHashType(AesCtrHmacStreamingParameters.HashType.SHA256)
                .setHmacHashType(AesCtrHmacStreamingParameters.HashType.SHA256)
                .setHmacTagSizeBytes(32)
                .setCiphertextSegmentSizeBytes(4 * 1024)
                .build());
  }

  @Test
  public void testAes128CtrHmacSha2561MBTemplate() throws Exception {
    KeyTemplate template = AesCtrHmacStreamingKeyManager.aes128CtrHmacSha2561MBTemplate();
    assertThat(template.toParameters())
        .isEqualTo(
            AesCtrHmacStreamingParameters.builder()
                .setKeySizeBytes(16)
                .setDerivedKeySizeBytes(16)
                .setHkdfHashType(AesCtrHmacStreamingParameters.HashType.SHA256)
                .setHmacHashType(AesCtrHmacStreamingParameters.HashType.SHA256)
                .setHmacTagSizeBytes(32)
                .setCiphertextSegmentSizeBytes(1024 * 1024)
                .build());
  }

  @Test
  public void testAes256CtrHmacSha2564KBTemplate() throws Exception {
    KeyTemplate template = AesCtrHmacStreamingKeyManager.aes256CtrHmacSha2564KBTemplate();
    assertThat(template.toParameters())
        .isEqualTo(
            AesCtrHmacStreamingParameters.builder()
                .setKeySizeBytes(32)
                .setDerivedKeySizeBytes(32)
                .setHkdfHashType(AesCtrHmacStreamingParameters.HashType.SHA256)
                .setHmacHashType(AesCtrHmacStreamingParameters.HashType.SHA256)
                .setHmacTagSizeBytes(32)
                .setCiphertextSegmentSizeBytes(4 * 1024)
                .build());
  }

  @Test
  public void testAes256CtrHmacSha2561MBTemplate() throws Exception {
    KeyTemplate template = AesCtrHmacStreamingKeyManager.aes256CtrHmacSha2561MBTemplate();
    assertThat(template.toParameters())
        .isEqualTo(
            AesCtrHmacStreamingParameters.builder()
                .setKeySizeBytes(32)
                .setDerivedKeySizeBytes(32)
                .setHkdfHashType(AesCtrHmacStreamingParameters.HashType.SHA256)
                .setHmacHashType(AesCtrHmacStreamingParameters.HashType.SHA256)
                .setHmacTagSizeBytes(32)
                .setCiphertextSegmentSizeBytes(1024 * 1024)
                .build());
  }

  @DataPoints("templateNames")
  public static final String[] KEY_TEMPLATES =
      new String[] {
        "AES128_CTR_HMAC_SHA256_4KB",
        "AES128_CTR_HMAC_SHA256_1MB",
        "AES256_CTR_HMAC_SHA256_4KB",
        "AES256_CTR_HMAC_SHA256_1MB"
      };

  @Theory
  public void testTemplates(@FromDataPoints("templateNames") String templateName) throws Exception {
    KeysetHandle h = KeysetHandle.generateNew(KeyTemplates.get(templateName));
    assertThat(h.size()).isEqualTo(1);
    assertThat(h.getAt(0).getKey().getParameters())
        .isEqualTo(KeyTemplates.get(templateName).toParameters());
  }
}
