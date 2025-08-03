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

package com.google.crypto.tink.hybrid;

import static com.google.crypto.tink.testing.TestUtil.assertExceptionContains;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.google.crypto.tink.Parameters;
import com.google.crypto.tink.TinkProtoParametersFormat;
import com.google.crypto.tink.aead.AeadConfig;
import com.google.crypto.tink.aead.AeadKeyTemplates;
import com.google.crypto.tink.aead.PredefinedAeadParameters;
import com.google.crypto.tink.daead.DeterministicAeadConfig;
import com.google.crypto.tink.daead.PredefinedDeterministicAeadParameters;
import com.google.crypto.tink.hybrid.subtle.AeadOrDaead;
import com.google.crypto.tink.proto.KeyTemplate;
import com.google.crypto.tink.signature.SignatureKeyTemplates;
import com.google.crypto.tink.subtle.Random;
import com.google.protobuf.ExtensionRegistryLite;
import java.nio.charset.Charset;
import java.security.GeneralSecurityException;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.FromDataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

/** Tests for RegistryEciesAeadHkdfDemHelper. */
@RunWith(Theories.class)
public class RegistryEciesAeadHkdfDemHelperTest {
  private static final Charset UTF_8 = Charset.forName("UTF-8");

  @DataPoints("parameters")
  public static final Parameters[] PARAMETERS_TO_TEST =
      new Parameters[] {
        PredefinedAeadParameters.AES128_GCM,
        PredefinedAeadParameters.AES256_GCM,
        PredefinedAeadParameters.AES128_CTR_HMAC_SHA256,
        PredefinedAeadParameters.AES256_CTR_HMAC_SHA256,
        PredefinedDeterministicAeadParameters.AES256_SIV
      };

  @Before
  public void setUp() throws Exception {
    AeadConfig.register();
    DeterministicAeadConfig.register();
  }

  @Test
  public void testConstructorWith128BitCiphers() throws Exception {
    RegistryEciesAeadHkdfDemHelper helper;

    // Supported templates.
    helper = new RegistryEciesAeadHkdfDemHelper(AeadKeyTemplates.AES128_GCM);
    assertEquals(16, helper.getSymmetricKeySizeInBytes());
    helper = new RegistryEciesAeadHkdfDemHelper(AeadKeyTemplates.AES128_CTR_HMAC_SHA256);
    assertEquals(48, helper.getSymmetricKeySizeInBytes());
  }

  @Test
  public void testConstructorWith256BitCiphers() throws Exception {
    // Supported templates.
    RegistryEciesAeadHkdfDemHelper helper =
        new RegistryEciesAeadHkdfDemHelper(AeadKeyTemplates.AES256_GCM);
    assertEquals(32, helper.getSymmetricKeySizeInBytes());
    helper = new RegistryEciesAeadHkdfDemHelper(AeadKeyTemplates.AES256_CTR_HMAC_SHA256);
    assertEquals(64, helper.getSymmetricKeySizeInBytes());
  }

  @Test
  public void testConstructorWithUnsupportedTemplates() throws Exception {
    // Unsupported templates.
    int templateCount = 4;
    KeyTemplate[] templates = new KeyTemplate[templateCount];
    templates[0] = AeadKeyTemplates.AES128_EAX;
    templates[1] = AeadKeyTemplates.AES256_EAX;
    templates[2] = AeadKeyTemplates.CHACHA20_POLY1305;
    templates[3] = SignatureKeyTemplates.ECDSA_P256;
    int count = 0;
    for (final KeyTemplate template : templates) {
      GeneralSecurityException e =
          assertThrows(
              "DEM type not supported, should have thrown exception:\n" + template.toString(),
              GeneralSecurityException.class,
              () -> new RegistryEciesAeadHkdfDemHelper(template));
      assertExceptionContains(e, "unsupported AEAD DEM key type");
      assertExceptionContains(e, template.getTypeUrl());
      count++;
    }
    assertEquals(templateCount, count);

    // An inconsistent template.
    final KeyTemplate template =
        KeyTemplate.newBuilder()
            .setTypeUrl(AeadKeyTemplates.AES128_CTR_HMAC_SHA256.getTypeUrl())
            .setValue(SignatureKeyTemplates.ECDSA_P256.getValue())
            .build();
    assertThrows(
        "Inconsistent template, should have thrown exception:\n" + template.toString(),
        GeneralSecurityException.class,
        () -> new RegistryEciesAeadHkdfDemHelper(template));
  }

  @Theory
  public void testGetAead(@FromDataPoints("parameters") Parameters parameters) throws Exception {
    byte[] plaintext = "some plaintext string".getBytes(UTF_8);
    byte[] associatedData = "some associated data".getBytes(UTF_8);
    KeyTemplate template =
        KeyTemplate.parseFrom(
            TinkProtoParametersFormat.serialize(parameters),
            ExtensionRegistryLite.getEmptyRegistry());

    RegistryEciesAeadHkdfDemHelper helper = new RegistryEciesAeadHkdfDemHelper(template);
    byte[] symmetricKey = Random.randBytes(helper.getSymmetricKeySizeInBytes());
    AeadOrDaead aead = helper.getAeadOrDaead(symmetricKey);
    byte[] ciphertext = aead.encrypt(plaintext, associatedData);
    byte[] decrypted = aead.decrypt(ciphertext, associatedData);
    assertArrayEquals(plaintext, decrypted);

    // Try using a symmetric key that is too short.
    final byte[] symmetricKey2 = Random.randBytes(helper.getSymmetricKeySizeInBytes() - 1);
    GeneralSecurityException e =
        assertThrows(
            "Symmetric key too short, should have thrown exception:\n" + template,
            GeneralSecurityException.class,
            () -> helper.getAeadOrDaead(symmetricKey2));
    assertExceptionContains(e, "incorrect length");

    // Try using a symmetric key that is too long.
    final byte[] symmetricKey3 = Random.randBytes(helper.getSymmetricKeySizeInBytes() + 1);
    e =
        assertThrows(
            "Symmetric key too long, should have thrown exception:\n" + template,
            GeneralSecurityException.class,
            () -> helper.getAeadOrDaead(symmetricKey3));
    assertExceptionContains(e, "incorrect length");
  }
}
