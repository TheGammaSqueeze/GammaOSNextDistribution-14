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
import static org.junit.Assert.assertThrows;

import com.google.crypto.tink.Aead;
import com.google.crypto.tink.KeyTemplate;
import com.google.crypto.tink.KeyTemplates;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.internal.KeyTypeManager;
import com.google.crypto.tink.proto.KeyData.KeyMaterialType;
import com.google.crypto.tink.proto.XChaCha20Poly1305Key;
import com.google.crypto.tink.proto.XChaCha20Poly1305KeyFormat;
import com.google.crypto.tink.subtle.Hex;
import com.google.crypto.tink.subtle.Random;
import com.google.crypto.tink.subtle.XChaCha20Poly1305;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
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

/** Test for XChaCha20Poly1305KeyManager. */
@RunWith(Theories.class)
public class XChaCha20Poly1305KeyManagerTest {
  private final XChaCha20Poly1305KeyManager manager = new XChaCha20Poly1305KeyManager();
  private final KeyTypeManager.KeyFactory<XChaCha20Poly1305KeyFormat, XChaCha20Poly1305Key>
      factory = manager.keyFactory();

  @Before
  public void register() throws Exception {
    AeadConfig.register();
  }

  @Test
  public void basics() throws Exception {
    assertThat(manager.getKeyType())
        .isEqualTo("type.googleapis.com/google.crypto.tink.XChaCha20Poly1305Key");
    assertThat(manager.getVersion()).isEqualTo(0);
    assertThat(manager.keyMaterialType()).isEqualTo(KeyMaterialType.SYMMETRIC);
  }

  @Test
  public void validateKeyFormat() throws Exception {
    factory.validateKeyFormat(XChaCha20Poly1305KeyFormat.getDefaultInstance());
  }

  @Test
  public void createKey_valid() throws Exception {
    manager.validateKey(factory.createKey(XChaCha20Poly1305KeyFormat.getDefaultInstance()));
  }

  @Test
  public void createKey_correctVersion() throws Exception {
    assertThat(factory.createKey(XChaCha20Poly1305KeyFormat.getDefaultInstance()).getVersion())
        .isEqualTo(0);
  }

  @Test
  public void createKey_keySize() throws Exception {
    assertThat(factory.createKey(XChaCha20Poly1305KeyFormat.getDefaultInstance()).getKeyValue())
        .hasSize(32);
  }

  @Test
  public void createKey_multipleCallsCreateDifferentKeys() throws Exception {
    Set<String> keys = new TreeSet<>();
    final int numKeys = 100;
    for (int i = 0; i < numKeys; ++i) {
      keys.add(
          Hex.encode(
              factory
                  .createKey(XChaCha20Poly1305KeyFormat.getDefaultInstance())
                  .getKeyValue()
                  .toByteArray()));
    }
    assertThat(keys).hasSize(numKeys);
  }

  @Test
  public void testDeriveKey() throws Exception {
    final int keySize = 32;
    byte[] keyMaterial = Random.randBytes(100);
    XChaCha20Poly1305Key key =
        factory.deriveKey(
            XChaCha20Poly1305KeyFormat.newBuilder().setVersion(0).build(),
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

    XChaCha20Poly1305Key key =
        factory.deriveKey(
            XChaCha20Poly1305KeyFormat.newBuilder().setVersion(0).build(), fragmentedInputStream);

    assertThat(key.getKeyValue()).hasSize(keySize);
    for (int i = 0; i < keySize; ++i) {
      assertThat(key.getKeyValue().byteAt(i)).isEqualTo(randomness);
    }
  }

  @Test
  public void testDeriveKeyNotEnoughRandomness() throws Exception {
    byte[] keyMaterial = Random.randBytes(10);
    assertThrows(
        GeneralSecurityException.class,
        () ->
            factory.deriveKey(
                XChaCha20Poly1305KeyFormat.newBuilder().setVersion(0).build(),
                new ByteArrayInputStream(keyMaterial)));
  }

  @Test
  public void testDeriveKeyWrongVersion() throws Exception {
    byte[] keyMaterial = Random.randBytes(32);
    assertThrows(
        GeneralSecurityException.class,
        () ->
            factory.deriveKey(
                XChaCha20Poly1305KeyFormat.newBuilder().setVersion(1).build(),
                new ByteArrayInputStream(keyMaterial)));
  }

  @Test
  public void getPrimitive() throws Exception {
    XChaCha20Poly1305Key key = factory.createKey(XChaCha20Poly1305KeyFormat.getDefaultInstance());
    Aead managerAead = manager.getPrimitive(key, Aead.class);
    Aead directAead = new XChaCha20Poly1305(key.getKeyValue().toByteArray());

    byte[] plaintext = Random.randBytes(20);
    byte[] associatedData = Random.randBytes(20);
    assertThat(directAead.decrypt(managerAead.encrypt(plaintext, associatedData), associatedData))
        .isEqualTo(plaintext);
  }

  @Test
  public void testXChaCha20Poly1305Template() throws Exception {
    KeyTemplate template = XChaCha20Poly1305KeyManager.xChaCha20Poly1305Template();
    assertThat(template.toParameters())
        .isEqualTo(XChaCha20Poly1305Parameters.create(XChaCha20Poly1305Parameters.Variant.TINK));
  }

  @Test
  public void testRawXChaCha20Poly1305Template() throws Exception {
    KeyTemplate template = XChaCha20Poly1305KeyManager.rawXChaCha20Poly1305Template();
    assertThat(template.toParameters()).isEqualTo(XChaCha20Poly1305Parameters.create());
  }

  @Test
  public void testKeyTemplateAndManagerCompatibility() throws Exception {
    XChaCha20Poly1305KeyManager manager = new XChaCha20Poly1305KeyManager();

    testKeyTemplateCompatible(manager, XChaCha20Poly1305KeyManager.xChaCha20Poly1305Template());
    testKeyTemplateCompatible(manager, XChaCha20Poly1305KeyManager.rawXChaCha20Poly1305Template());
  }

  @DataPoints("templateNames")
  public static final String[] KEY_TEMPLATES =
      new String[] {"XCHACHA20_POLY1305", "XCHACHA20_POLY1305_RAW"};

  @Theory
  public void testTemplates(@FromDataPoints("templateNames") String templateName) throws Exception {
    KeysetHandle h = KeysetHandle.generateNew(KeyTemplates.get(templateName));
    assertThat(h.size()).isEqualTo(1);
    assertThat(h.getAt(0).getKey().getParameters())
        .isEqualTo(KeyTemplates.get(templateName).toParameters());
  }
}
