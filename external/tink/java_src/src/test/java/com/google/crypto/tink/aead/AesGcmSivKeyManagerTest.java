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

package com.google.crypto.tink.aead;

import static com.google.common.truth.Truth.assertThat;
import static com.google.crypto.tink.testing.KeyTypeManagerTestUtil.testKeyTemplateCompatible;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;

import com.google.crypto.tink.Aead;
import com.google.crypto.tink.KeyTemplate;
import com.google.crypto.tink.KeyTemplates;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.aead.subtle.AesGcmSiv;
import com.google.crypto.tink.internal.KeyTypeManager;
import com.google.crypto.tink.proto.AesGcmSivKey;
import com.google.crypto.tink.proto.AesGcmSivKeyFormat;
import com.google.crypto.tink.proto.KeyData.KeyMaterialType;
import com.google.crypto.tink.subtle.Hex;
import com.google.crypto.tink.subtle.Random;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.Security;
import java.util.Set;
import java.util.TreeSet;
import org.conscrypt.Conscrypt;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.FromDataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

/** Test for AesGcmJce and its key manager. */
@RunWith(Theories.class)
public class AesGcmSivKeyManagerTest {
  private final AesGcmSivKeyManager manager = new AesGcmSivKeyManager();
  private final KeyTypeManager.KeyFactory<AesGcmSivKeyFormat, AesGcmSivKey> factory =
      manager.keyFactory();

  @Before
  public void setUp() throws Exception {
    try {
      Conscrypt.checkAvailability();
      Security.addProvider(Conscrypt.newProvider());
    } catch (Throwable cause) {
      throw new IllegalStateException(
          "Cannot test AesGcmSivKeyManager without Conscrypt Provider", cause);
    }
    AeadConfig.register();
  }

  @Test
  public void basics() throws Exception {
    assertThat(manager.getKeyType())
        .isEqualTo("type.googleapis.com/google.crypto.tink.AesGcmSivKey");
    assertThat(manager.getVersion()).isEqualTo(0);
    assertThat(manager.keyMaterialType()).isEqualTo(KeyMaterialType.SYMMETRIC);
  }

  @Test
  public void validateKeyFormat_empty() throws Exception {
    assertThrows(
        GeneralSecurityException.class,
        () -> factory.validateKeyFormat(AesGcmSivKeyFormat.getDefaultInstance()));
  }

  @Test
  public void validateKeyFormat_valid() throws Exception {
    factory.validateKeyFormat(AesGcmSivKeyFormat.newBuilder().setKeySize(16).build());
    factory.validateKeyFormat(AesGcmSivKeyFormat.newBuilder().setKeySize(32).build());
  }

  @Test
  public void validateKeyFormat_invalid() throws Exception {
    assertThrows(
        GeneralSecurityException.class,
        () -> factory.validateKeyFormat(AesGcmSivKeyFormat.newBuilder().setKeySize(1).build()));
    assertThrows(
        GeneralSecurityException.class,
        () -> factory.validateKeyFormat(AesGcmSivKeyFormat.newBuilder().setKeySize(15).build()));
    assertThrows(
        GeneralSecurityException.class,
        () -> factory.validateKeyFormat(AesGcmSivKeyFormat.newBuilder().setKeySize(17).build()));
    assertThrows(
        GeneralSecurityException.class,
        () -> factory.validateKeyFormat(AesGcmSivKeyFormat.newBuilder().setKeySize(31).build()));
    assertThrows(
        GeneralSecurityException.class,
        () -> factory.validateKeyFormat(AesGcmSivKeyFormat.newBuilder().setKeySize(33).build()));
    assertThrows(
        GeneralSecurityException.class,
        () -> factory.validateKeyFormat(AesGcmSivKeyFormat.newBuilder().setKeySize(64).build()));
  }

  @Test
  public void createKey_16Bytes() throws Exception {
    AesGcmSivKey key = factory.createKey(AesGcmSivKeyFormat.newBuilder().setKeySize(16).build());
    assertThat(key.getKeyValue()).hasSize(16);
  }

  @Test
  public void createKey_32Bytes() throws Exception {
    AesGcmSivKey key = factory.createKey(AesGcmSivKeyFormat.newBuilder().setKeySize(32).build());
    assertThat(key.getKeyValue()).hasSize(32);
  }

  @Test
  public void createKey_multipleTimes() throws Exception {
    AesGcmSivKeyFormat format = AesGcmSivKeyFormat.newBuilder().setKeySize(16).build();
    Set<String> keys = new TreeSet<>();
    // Calls newKey multiple times and make sure that they generate different keys.
    int numTests = 50;
    for (int i = 0; i < numTests; i++) {
      keys.add(Hex.encode(factory.createKey(format).getKeyValue().toByteArray()));
    }
    assertThat(keys).hasSize(numTests);
  }

  @Test
  public void getPrimitive() throws Exception {
    AesGcmSivKey key = factory.createKey(AesGcmSivKeyFormat.newBuilder().setKeySize(16).build());
    Aead managerAead = manager.getPrimitive(key, Aead.class);
    Aead directAead = new AesGcmSiv(key.getKeyValue().toByteArray());

    byte[] plaintext = Random.randBytes(20);
    byte[] associatedData = Random.randBytes(20);
    assertThat(directAead.decrypt(managerAead.encrypt(plaintext, associatedData), associatedData))
        .isEqualTo(plaintext);
  }

  @Test
  public void testCiphertextSize() throws Exception {
    AesGcmSivKey key = factory.createKey(AesGcmSivKeyFormat.newBuilder().setKeySize(32).build());
    Aead aead = new AesGcmSivKeyManager().getPrimitive(key, Aead.class);
    byte[] plaintext = "plaintext".getBytes(UTF_8);
    byte[] associatedData = "associatedData".getBytes(UTF_8);
    byte[] ciphertext = aead.encrypt(plaintext, associatedData);
    assertThat(ciphertext.length)
        .isEqualTo(12 /* IV_SIZE */ + plaintext.length + 16 /* TAG_SIZE */);
  }

  @Test
  public void testDeriveKey_size32() throws Exception {
    final int keySize = 32;

    byte[] keyMaterial = Random.randBytes(100);
    AesGcmSivKey key =
        factory.deriveKey(
            AesGcmSivKeyFormat.newBuilder().setVersion(0).setKeySize(keySize).build(),
            new ByteArrayInputStream(keyMaterial));
    assertThat(key.getKeyValue()).hasSize(keySize);
    for (int i = 0; i < keySize; ++i) {
      assertThat(key.getKeyValue().byteAt(i)).isEqualTo(keyMaterial[i]);
    }
  }

  @Test
  public void testDeriveKey_size16() throws Exception {
    final int keySize = 16;

    byte[] keyMaterial = Random.randBytes(100);
    AesGcmSivKey key =
        factory.deriveKey(
            AesGcmSivKeyFormat.newBuilder().setVersion(0).setKeySize(keySize).build(),
            new ByteArrayInputStream(keyMaterial));
    assertThat(key.getKeyValue()).hasSize(keySize);
    for (int i = 0; i < keySize; ++i) {
      assertThat(key.getKeyValue().byteAt(i)).isEqualTo(keyMaterial[i]);
    }
  }

  @Test
  public void testDeriveKey_handlesDataFragmentationCorrectly() throws Exception {
    int keySize = 32;
    byte randomness = 4;
    InputStream fragmentedInputStream =
        new InputStream() {
          @Override
          public int read() {
            return 0;
          }

          @Override
          public int read(byte[] b, int off, int len) {
            b[off] = randomness;
            return 1;
          }
        };

    AesGcmSivKey key =
        factory.deriveKey(
            AesGcmSivKeyFormat.newBuilder().setVersion(0).setKeySize(keySize).build(),
            fragmentedInputStream);

    assertThat(key.getKeyValue()).hasSize(keySize);
    for (int i = 0; i < keySize; ++i) {
      assertThat(key.getKeyValue().byteAt(i)).isEqualTo(randomness);
    }
  }

  @Test
  public void testDeriveKey_notEnoughKeyMaterial_throws() throws Exception {
    byte[] keyMaterial = Random.randBytes(31);
    AesGcmSivKeyFormat format =
        AesGcmSivKeyFormat.newBuilder().setVersion(0).setKeySize(32).build();
    assertThrows(
        GeneralSecurityException.class,
        () -> factory.deriveKey(format, new ByteArrayInputStream(keyMaterial)));
  }

  @Test
  public void testDeriveKey_badVersion_throws() throws Exception {
    final int keySize = 32;

    byte[] keyMaterial = Random.randBytes(100);
    AesGcmSivKeyFormat format =
        AesGcmSivKeyFormat.newBuilder().setVersion(1).setKeySize(keySize).build();
    assertThrows(
        GeneralSecurityException.class,
        () -> factory.deriveKey(format, new ByteArrayInputStream(keyMaterial)));
  }

  @Test
  public void testDeriveKey_justEnoughKeyMaterial() throws Exception {
    final int keySize = 32;

    byte[] keyMaterial = Random.randBytes(32);
    AesGcmSivKey key =
        factory.deriveKey(
            AesGcmSivKeyFormat.newBuilder().setVersion(0).setKeySize(keySize).build(),
            new ByteArrayInputStream(keyMaterial));
    assertThat(key.getKeyValue()).hasSize(keySize);
    for (int i = 0; i < keySize; ++i) {
      assertThat(key.getKeyValue().byteAt(i)).isEqualTo(keyMaterial[i]);
    }
  }

  @Test
  public void testAes128GcmSivTemplate() throws Exception {
    KeyTemplate template = AesGcmSivKeyManager.aes128GcmSivTemplate();
    assertThat(template.toParameters())
        .isEqualTo(
            AesGcmSivParameters.builder()
                .setKeySizeBytes(16)
                .setVariant(AesGcmSivParameters.Variant.TINK)
                .build());
  }

  @Test
  public void testRawAes128GcmSivTemplate() throws Exception {
    KeyTemplate template = AesGcmSivKeyManager.rawAes128GcmSivTemplate();
    assertThat(template.toParameters())
        .isEqualTo(
            AesGcmSivParameters.builder()
                .setKeySizeBytes(16)
                .setVariant(AesGcmSivParameters.Variant.NO_PREFIX)
                .build());
  }

  @Test
  public void testAes256GcmSivTemplate() throws Exception {
    KeyTemplate template = AesGcmSivKeyManager.aes256GcmSivTemplate();
    assertThat(template.toParameters())
        .isEqualTo(
            AesGcmSivParameters.builder()
                .setKeySizeBytes(32)
                .setVariant(AesGcmSivParameters.Variant.TINK)
                .build());
  }

  @Test
  public void testRawAes256GcmSivTemplate() throws Exception {
    KeyTemplate template = AesGcmSivKeyManager.rawAes256GcmSivTemplate();
    assertThat(template.toParameters())
        .isEqualTo(
            AesGcmSivParameters.builder()
                .setKeySizeBytes(32)
                .setVariant(AesGcmSivParameters.Variant.NO_PREFIX)
                .build());
  }

  @Test
  public void testKeyTemplateAndManagerCompatibility() throws Exception {
    AesGcmSivKeyManager manager = new AesGcmSivKeyManager();

    testKeyTemplateCompatible(manager, AesGcmSivKeyManager.aes128GcmSivTemplate());
    testKeyTemplateCompatible(manager, AesGcmSivKeyManager.rawAes128GcmSivTemplate());
    testKeyTemplateCompatible(manager, AesGcmSivKeyManager.aes256GcmSivTemplate());
    testKeyTemplateCompatible(manager, AesGcmSivKeyManager.rawAes256GcmSivTemplate());
  }

  @DataPoints("templateNames")
  public static final String[] KEY_TEMPLATES =
      new String[] {"AES128_GCM_SIV", "AES256_GCM_SIV", "AES256_GCM_SIV_RAW", "AES128_GCM_SIV_RAW"};

  @Theory
  public void testTemplates(@FromDataPoints("templateNames") String templateName) throws Exception {
    KeysetHandle h = KeysetHandle.generateNew(KeyTemplates.get(templateName));
    assertThat(h.size()).isEqualTo(1);
    assertThat(h.getAt(0).getKey().getParameters())
        .isEqualTo(KeyTemplates.get(templateName).toParameters());
  }
}
