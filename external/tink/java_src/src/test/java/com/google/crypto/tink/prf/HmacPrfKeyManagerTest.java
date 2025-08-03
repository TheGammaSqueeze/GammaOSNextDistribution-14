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

package com.google.crypto.tink.prf;

import static com.google.common.truth.Truth.assertThat;
import static com.google.crypto.tink.testing.KeyTypeManagerTestUtil.testKeyTemplateCompatible;
import static org.junit.Assert.assertThrows;

import com.google.crypto.tink.KeyTemplate;
import com.google.crypto.tink.KeyTemplates;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.internal.KeyTypeManager;
import com.google.crypto.tink.internal.MutablePrimitiveRegistry;
import com.google.crypto.tink.proto.HashType;
import com.google.crypto.tink.proto.HmacPrfKey;
import com.google.crypto.tink.proto.HmacPrfKeyFormat;
import com.google.crypto.tink.proto.HmacPrfParams;
import com.google.crypto.tink.subtle.Hex;
import com.google.crypto.tink.subtle.PrfHmacJce;
import com.google.crypto.tink.subtle.Random;
import com.google.crypto.tink.util.SecretBytes;
import com.google.protobuf.ByteString;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.util.Set;
import java.util.TreeSet;
import javax.crypto.spec.SecretKeySpec;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.FromDataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

/** Unit tests for {@link HmacPrfKeyManager}. */
@RunWith(Theories.class)
public class HmacPrfKeyManagerTest {
  private final HmacPrfKeyManager manager = new HmacPrfKeyManager();
  private final KeyTypeManager.KeyFactory<HmacPrfKeyFormat, HmacPrfKey> factory =
      manager.keyFactory();

  @Before
  public void register() throws Exception {
    PrfConfig.register();
  }

  @Test
  public void validateKeyFormat_empty() throws Exception {
    assertThrows(
        GeneralSecurityException.class,
        () -> factory.validateKeyFormat(HmacPrfKeyFormat.getDefaultInstance()));
  }

  private static HmacPrfKeyFormat makeHmacPrfKeyFormat(int keySize, HashType hashType) {
    HmacPrfParams params = HmacPrfParams.newBuilder().setHash(hashType).build();
    return HmacPrfKeyFormat.newBuilder().setParams(params).setKeySize(keySize).build();
  }

  @Test
  public void validateKeyFormat_keySizes() throws Exception {
    factory.validateKeyFormat(makeHmacPrfKeyFormat(16, HashType.SHA256));
    assertThrows(
        GeneralSecurityException.class,
        () -> factory.validateKeyFormat(makeHmacPrfKeyFormat(15, HashType.SHA256)));
  }

  @Test
  public void createKey_valid() throws Exception {
    manager.validateKey(factory.createKey(makeHmacPrfKeyFormat(16, HashType.SHA1)));
    manager.validateKey(factory.createKey(makeHmacPrfKeyFormat(20, HashType.SHA1)));
    manager.validateKey(factory.createKey(makeHmacPrfKeyFormat(32, HashType.SHA1)));
    manager.validateKey(factory.createKey(makeHmacPrfKeyFormat(16, HashType.SHA256)));
    manager.validateKey(factory.createKey(makeHmacPrfKeyFormat(32, HashType.SHA256)));
    manager.validateKey(factory.createKey(makeHmacPrfKeyFormat(16, HashType.SHA512)));
    manager.validateKey(factory.createKey(makeHmacPrfKeyFormat(32, HashType.SHA512)));
    manager.validateKey(factory.createKey(makeHmacPrfKeyFormat(64, HashType.SHA512)));
  }

  @Test
  public void createKey_checkValues() throws Exception {
    HmacPrfKeyFormat keyFormat = makeHmacPrfKeyFormat(16, HashType.SHA256);
    HmacPrfKey key = factory.createKey(keyFormat);
    assertThat(key.getKeyValue()).hasSize(keyFormat.getKeySize());
    assertThat(key.getParams().getHash()).isEqualTo(keyFormat.getParams().getHash());
  }

  @Test
  public void createKey_multipleTimes() throws Exception {
    HmacPrfKeyFormat keyFormat = makeHmacPrfKeyFormat(16, HashType.SHA256);
    int numKeys = 100;
    Set<String> keys = new TreeSet<String>();
    for (int i = 0; i < numKeys; ++i) {
      keys.add(Hex.encode(factory.createKey(keyFormat).getKeyValue().toByteArray()));
    }
    assertThat(keys).hasSize(numKeys);
  }

  @Test
  public void validateKey_wrongVersion_throws() throws Exception {
    HmacPrfKey validKey = factory.createKey(makeHmacPrfKeyFormat(16, HashType.SHA1));
    assertThrows(
        GeneralSecurityException.class,
        () -> manager.validateKey(HmacPrfKey.newBuilder(validKey).setVersion(1).build()));
  }

  @Test
  public void validateKey_notValid_throws() throws Exception {
    HmacPrfKey validKey = factory.createKey(makeHmacPrfKeyFormat(16, HashType.SHA1));
    assertThrows(
        GeneralSecurityException.class,
        () ->
            manager.validateKey(
                HmacPrfKey.newBuilder(validKey)
                    .setKeyValue(ByteString.copyFrom(Random.randBytes(15)))
                    .build()));
    assertThrows(
        GeneralSecurityException.class,
        () ->
            manager.validateKey(
                HmacPrfKey.newBuilder(validKey)
                    .setParams(
                        HmacPrfParams.newBuilder(validKey.getParams())
                            .setHash(HashType.UNKNOWN_HASH)
                            .build())
                    .build()));
  }

  @Test
  public void getPrimitive_worksForSha1() throws Exception {
    HmacPrfKey validKey = factory.createKey(makeHmacPrfKeyFormat(16, HashType.SHA1));
    Prf managerPrf = manager.getPrimitive(validKey, Prf.class);
    Prf directPrf =
        new PrfHmacJce("HMACSHA1", new SecretKeySpec(validKey.getKeyValue().toByteArray(), "HMAC"));
    byte[] message = Random.randBytes(50);
    assertThat(managerPrf.compute(message, 19)).isEqualTo(directPrf.compute(message, 19));
  }

  @Test
  public void getPrimitive_worksForSha256() throws Exception {
    HmacPrfKey validKey = factory.createKey(makeHmacPrfKeyFormat(16, HashType.SHA256));
    Prf managerPrf = manager.getPrimitive(validKey, Prf.class);
    Prf directPrf =
        new PrfHmacJce(
            "HMACSHA256", new SecretKeySpec(validKey.getKeyValue().toByteArray(), "HMAC"));
    byte[] message = Random.randBytes(50);
    assertThat(managerPrf.compute(message, 29)).isEqualTo(directPrf.compute(message, 29));
  }

  @Test
  public void getPrimitive_worksForSha512() throws Exception {
    HmacPrfKey validKey = factory.createKey(makeHmacPrfKeyFormat(16, HashType.SHA512));
    Prf managerPrf = manager.getPrimitive(validKey, Prf.class);
    Prf directPrf =
        new PrfHmacJce(
            "HMACSHA512", new SecretKeySpec(validKey.getKeyValue().toByteArray(), "HMAC"));
    byte[] message = Random.randBytes(50);
    assertThat(managerPrf.compute(message, 33)).isEqualTo(directPrf.compute(message, 33));
  }

  @Test
  public void testDeriveKey_size27() throws Exception {
    final int keySize = 27;

    byte[] keyMaterial = Random.randBytes(100);
    HmacPrfParams params = HmacPrfParams.newBuilder().setHash(HashType.SHA256).build();
    HmacPrfKey key =
        factory.deriveKey(
            HmacPrfKeyFormat.newBuilder()
                .setVersion(0)
                .setParams(params)
                .setKeySize(keySize)
                .build(),
            new ByteArrayInputStream(keyMaterial));
    assertThat(key.getKeyValue()).hasSize(keySize);
    for (int i = 0; i < keySize; ++i) {
      assertThat(key.getKeyValue().byteAt(i)).isEqualTo(keyMaterial[i]);
    }
    assertThat(key.getParams()).isEqualTo(params);
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

    HmacPrfParams params = HmacPrfParams.newBuilder().setHash(HashType.SHA256).build();
    HmacPrfKey key =
        factory.deriveKey(
            HmacPrfKeyFormat.newBuilder()
                .setVersion(0)
                .setParams(params)
                .setKeySize(keySize)
                .build(),
            fragmentedInputStream);

    assertThat(key.getKeyValue()).hasSize(keySize);
    for (int i = 0; i < keySize; ++i) {
      assertThat(key.getKeyValue().byteAt(i)).isEqualTo(randomness);
    }
  }

  @Test
  public void testDeriveKey_notEnoughKeyMaterial_throws() throws Exception {
    byte[] keyMaterial = Random.randBytes(31);
    HmacPrfParams params = HmacPrfParams.newBuilder().setHash(HashType.SHA256).build();
    HmacPrfKeyFormat format =
        HmacPrfKeyFormat.newBuilder().setVersion(0).setParams(params).setKeySize(32).build();
    assertThrows(
        GeneralSecurityException.class,
        () -> factory.deriveKey(format, new ByteArrayInputStream(keyMaterial)));
  }

  @Test
  public void testDeriveKey_badVersion_throws() throws Exception {
    final int keySize = 32;

    byte[] keyMaterial = Random.randBytes(100);
    HmacPrfParams params = HmacPrfParams.newBuilder().setHash(HashType.SHA256).build();
    HmacPrfKeyFormat format =
        HmacPrfKeyFormat.newBuilder().setVersion(1).setParams(params).setKeySize(keySize).build();
    assertThrows(
        GeneralSecurityException.class,
        () -> factory.deriveKey(format, new ByteArrayInputStream(keyMaterial)));
  }

  @Test
  public void testDeriveKey_justEnoughKeyMaterial() throws Exception {
    final int keySize = 32;

    byte[] keyMaterial = Random.randBytes(keySize);
    HmacPrfParams params = HmacPrfParams.newBuilder().setHash(HashType.SHA256).build();
    HmacPrfKey key =
        factory.deriveKey(
            HmacPrfKeyFormat.newBuilder()
                .setVersion(0)
                .setParams(params)
                .setKeySize(keySize)
                .build(),
            new ByteArrayInputStream(keyMaterial));
    assertThat(key.getKeyValue()).hasSize(keySize);
    for (int i = 0; i < keySize; ++i) {
      assertThat(key.getKeyValue().byteAt(i)).isEqualTo(keyMaterial[i]);
    }
  }

  @Test
  public void testHmacSha256Template() throws Exception {
    KeyTemplate template = HmacPrfKeyManager.hmacSha256Template();
    assertThat(template.toParameters())
        .isEqualTo(
            HmacPrfParameters.builder()
                .setKeySizeBytes(32)
                .setHashType(HmacPrfParameters.HashType.SHA256)
                .build());
  }

  @Test
  public void testHmacSha512Template() throws Exception {
    KeyTemplate template = HmacPrfKeyManager.hmacSha512Template();
    assertThat(template.toParameters())
        .isEqualTo(
            HmacPrfParameters.builder()
                .setKeySizeBytes(64)
                .setHashType(HmacPrfParameters.HashType.SHA512)
                .build());
  }

  @Test
  public void testKeyTemplateAndManagerCompatibility() throws Exception {
    HmacPrfKeyManager manager = new HmacPrfKeyManager();

    testKeyTemplateCompatible(manager, HmacPrfKeyManager.hmacSha256Template());
    testKeyTemplateCompatible(manager, HmacPrfKeyManager.hmacSha512Template());
  }

  @DataPoints("templateNames")
  public static final String[] KEY_TEMPLATES = new String[] {"HMAC_SHA256_PRF", "HMAC_SHA512_PRF"};

  @Theory
  public void testTemplates(@FromDataPoints("templateNames") String templateName) throws Exception {
    KeysetHandle h = KeysetHandle.generateNew(KeyTemplates.get(templateName));
    assertThat(h.size()).isEqualTo(1);
    assertThat(h.getAt(0).getKey().getParameters())
        .isEqualTo(KeyTemplates.get(templateName).toParameters());
  }

  @Test
  public void registersPrfPrimitiveConstructor() throws Exception {
    Prf prf =
        MutablePrimitiveRegistry.globalInstance()
            .getPrimitive(
                com.google.crypto.tink.prf.HmacPrfKey.builder()
                    .setParameters(
                        HmacPrfParameters.builder()
                            .setHashType(HmacPrfParameters.HashType.SHA256)
                            .setKeySizeBytes(32)
                            .build())
                    .setKeyBytes(SecretBytes.randomBytes(32))
                    .build(),
                Prf.class);

    assertThat(prf).isInstanceOf(PrfHmacJce.class);
  }
}
