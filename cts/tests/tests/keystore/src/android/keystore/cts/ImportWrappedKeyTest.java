/*
 * Copyright 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.keystore.cts;

import static android.security.keymaster.KeymasterDefs.KM_ALGORITHM_3DES;
import static android.security.keymaster.KeymasterDefs.KM_ALGORITHM_AES;
import static android.security.keymaster.KeymasterDefs.KM_ALGORITHM_EC;
import static android.security.keymaster.KeymasterDefs.KM_ALGORITHM_RSA;
import static android.security.keymaster.KeymasterDefs.KM_DIGEST_MD5;
import static android.security.keymaster.KeymasterDefs.KM_DIGEST_NONE;
import static android.security.keymaster.KeymasterDefs.KM_DIGEST_SHA1;
import static android.security.keymaster.KeymasterDefs.KM_DIGEST_SHA_2_224;
import static android.security.keymaster.KeymasterDefs.KM_DIGEST_SHA_2_256;
import static android.security.keymaster.KeymasterDefs.KM_DIGEST_SHA_2_384;
import static android.security.keymaster.KeymasterDefs.KM_DIGEST_SHA_2_512;
import static android.security.keymaster.KeymasterDefs.KM_KEY_FORMAT_PKCS8;
import static android.security.keymaster.KeymasterDefs.KM_KEY_FORMAT_RAW;
import static android.security.keymaster.KeymasterDefs.KM_MODE_CBC;
import static android.security.keymaster.KeymasterDefs.KM_MODE_ECB;
import static android.security.keymaster.KeymasterDefs.KM_PAD_NONE;
import static android.security.keymaster.KeymasterDefs.KM_PAD_PKCS7;
import static android.security.keymaster.KeymasterDefs.KM_PAD_RSA_OAEP;
import static android.security.keymaster.KeymasterDefs.KM_PAD_RSA_PKCS1_1_5_ENCRYPT;
import static android.security.keymaster.KeymasterDefs.KM_PAD_RSA_PKCS1_1_5_SIGN;
import static android.security.keymaster.KeymasterDefs.KM_PAD_RSA_PSS;
import static android.security.keymaster.KeymasterDefs.KM_PURPOSE_DECRYPT;
import static android.security.keymaster.KeymasterDefs.KM_PURPOSE_ENCRYPT;
import static android.security.keymaster.KeymasterDefs.KM_PURPOSE_SIGN;
import static android.security.keymaster.KeymasterDefs.KM_PURPOSE_VERIFY;
import static android.security.keymaster.KeymasterDefs.KM_TAG_PURPOSE;
import static android.security.keymaster.KeymasterDefs.KM_TAG_ALGORITHM;
import static android.security.keymaster.KeymasterDefs.KM_TAG_KEY_SIZE;
import static android.security.keymaster.KeymasterDefs.KM_TAG_BLOCK_MODE;
import static android.security.keymaster.KeymasterDefs.KM_TAG_DIGEST;
import static android.security.keymaster.KeymasterDefs.KM_TAG_PADDING;
import static android.security.keymaster.KeymasterDefs.KM_TAG_NO_AUTH_REQUIRED;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNoException;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.keystore.cts.util.TestUtils;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.security.keystore.SecureKeyImportUnavailableException;
import android.security.keystore.WrappedKeyEntry;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.bouncycastle.asn1.ASN1Encoding;
import org.bouncycastle.asn1.DEREncodableVector;
import org.bouncycastle.asn1.DERInteger;
import org.bouncycastle.asn1.DERNull;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DERSequence;
import org.bouncycastle.asn1.DERSet;
import org.bouncycastle.asn1.DERTaggedObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStore.Entry;
import java.security.KeyStoreException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.MGF1ParameterSpec;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;

@RunWith(AndroidJUnit4.class)
public class ImportWrappedKeyTest {
    private static final String TAG = "ImportWrappedKeyTest";

    private static final String ALIAS = "my key";
    private static final String WRAPPING_KEY_ALIAS = "my_favorite_wrapping_key";

    private static final int WRAPPED_FORMAT_VERSION = 0;
    private static final int GCM_TAG_SIZE = 128;

    SecureRandom random = new SecureRandom();

    private Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    private int removeTagType(int tag) {
        int kmTagTypeMask = 0x0FFFFFFF;
        return tag & kmTagTypeMask;
    }

    @Test
    public void testKeyStore_ImportWrappedKey_AES() throws Exception {
        testKeyStore_ImportWrappedKey_AES(false);
    }

    @Test
    public void testKeyStore_ImportWrappedKey_AES_StrongBox() throws Exception {
        testKeyStore_ImportWrappedKey_AES(true);
    }

    public void testKeyStore_ImportWrappedKey_AES(boolean isStrongBox) throws Exception {
        if (isStrongBox) {
            TestUtils.assumeStrongBox();
        }

        KeyGenerator kg = KeyGenerator.getInstance("AES");
        kg.init(256);
        Key swKey = kg.generateKey();

        byte[] keyMaterial = swKey.getEncoded();
        byte[] mask = new byte[32]; // Zero mask

        try {
            importWrappedKey(wrapKey(
                    genKeyPair(WRAPPING_KEY_ALIAS, isStrongBox).getPublic(),
                    keyMaterial,
                    mask,
                    KM_KEY_FORMAT_RAW,
                    makeAesAuthList(keyMaterial.length * 8)));
        } catch (SecureKeyImportUnavailableException e) {
            assumeNoException("Can only test if secure key import is available", e);
        }

        // Use Key
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null, null);

        assertTrue("Failed to load key after wrapped import", keyStore.containsAlias(ALIAS));

        Key importedKey = keyStore.getKey(ALIAS, null);
        String plaintext = "hello, world";

        Cipher c = Cipher.getInstance("AES/ECB/PKCS7Padding");
        c.init(Cipher.ENCRYPT_MODE, importedKey);
        byte[] encrypted = c.doFinal(plaintext.getBytes());

        // Decrypt using key imported into keystore.
        c = Cipher.getInstance("AES/ECB/PKCS7Padding");
        c.init(Cipher.DECRYPT_MODE, importedKey);
        assertEquals(new String(c.doFinal(encrypted)), plaintext);

        // Decrypt using local software copy of the key.
        c = Cipher.getInstance("AES/ECB/PKCS7Padding");
        c.init(Cipher.DECRYPT_MODE, swKey);
        assertEquals(new String(c.doFinal(encrypted)), plaintext);
    }

    @Test
    public void testKeyStore_ImportIncorrectWrappedKey() throws Exception {
        testKeyStore_ImportIncorrectWrappedKey(false);
    }

    @Test
    public void testKeyStore_ImportIncorrectWrappedKey_StrongBox() throws Exception {
        testKeyStore_ImportIncorrectWrappedKey(true);
    }

    private void testKeyStore_ImportIncorrectWrappedKey(boolean isStrongBox) throws Exception {
        if (isStrongBox) {
            TestUtils.assumeStrongBox();
        }
        random.setSeed(0);

        byte[] keyMaterial = new byte[32];
        random.nextBytes(keyMaterial);
        byte[] mask = new byte[32]; // Zero mask

        KeyStoreException exception = null;
        try {
            importWrappedKey(wrapKey(
                    genKeyPair(WRAPPING_KEY_ALIAS, isStrongBox).getPublic(),
                    keyMaterial,
                    mask,
                    KM_KEY_FORMAT_RAW,
                    makeAesAuthList(keyMaterial.length * 8),
                    false /* incorrect wrapping required*/));
        } catch (SecureKeyImportUnavailableException e) {
            assumeNoException("Can only test if secure key import is available", e);
        } catch (KeyStoreException e) {
            exception = e;
        }
        assertWithMessage("Did not hit a failure but expected one").that(exception).isNotNull();
        assertThat(exception.getCause()).isInstanceOf(android.security.KeyStoreException.class);
        android.security.KeyStoreException ksException =
                (android.security.KeyStoreException) exception.getCause();
        assertFalse("Importing incorrectly wrapped key should not cause transient failure in"
                    + " Key{Mint/Master}. That means performing same operation will fail always.",
                        ksException.isTransientFailure());
    }

    @Test
    public void testKeyStore_ImportWrappedKeyWrappingKeyMissing() throws Exception {
        final String EXPECTED_FAILURE = "Failed to import wrapped key. Keystore error code: 7";
        KeyStoreException exception = null;

        try {
            byte [] fakeWrappedKey = new byte[1];
            importWrappedKey(fakeWrappedKey, WRAPPING_KEY_ALIAS + "_Missing");
        } catch (KeyStoreException e) {
            exception = e;
        }

        assertWithMessage("Did not hit a failure but expected one").that(exception).isNotNull();

        assertThat(exception.getMessage()).isEqualTo(EXPECTED_FAILURE);
        assertThat(exception.getCause()).isInstanceOf(android.security.KeyStoreException.class);
        android.security.KeyStoreException ksException =
                (android.security.KeyStoreException) exception.getCause();
        assertThat(ksException.getNumericErrorCode()).isEqualTo(
                android.security.KeyStoreException.ERROR_KEY_DOES_NOT_EXIST);
    }

    @Test
    public void testKeyStore_ImportWrappedKey_3DES() throws Exception {
        testKeyStore_ImportWrappedKey_3DES(false);
    }

    @Test
    public void testKeyStore_ImportWrappedKey_3DES_StrongBox() throws Exception {
        testKeyStore_ImportWrappedKey_3DES(true);
    }

    public void testKeyStore_ImportWrappedKey_3DES(boolean isStrongBox) throws Exception {
        if (isStrongBox) {
            TestUtils.assumeStrongBox();
        }

        assumeTrue("Can only test if device supports 3DES", TestUtils.supports3DES());

        KeyGenerator kg = KeyGenerator.getInstance("DESEDE");
        kg.init(168);
        Key swKey = kg.generateKey();

        byte[] keyMaterial = swKey.getEncoded();
        byte[] mask = new byte[24]; // Zero mask

        try {
            importWrappedKey(wrapKey(
                    genKeyPair(WRAPPING_KEY_ALIAS, isStrongBox).getPublic(),
                    keyMaterial,
                    mask,
                    KM_KEY_FORMAT_RAW,
                    make3desAuthList(168)));
        } catch (SecureKeyImportUnavailableException e) {
            assumeNoException("Can only test if secure key import is available", e);
        }

        // Use Key
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null, null);

        assertTrue("Failed to load key after wrapped import", keyStore.containsAlias(ALIAS));

        Key importedKey = keyStore.getKey(ALIAS, null);
        String plaintext = "hello, world";

        Cipher c = Cipher.getInstance("DESede/CBC/PKCS7Padding");
        c.init(Cipher.ENCRYPT_MODE, importedKey);
        IvParameterSpec paramSpec = new IvParameterSpec(c.getIV());
        byte[] encrypted = c.doFinal(plaintext.getBytes());

        // Decrypt using key imported into keystore.
        c = Cipher.getInstance("DESede/CBC/PKCS7Padding");
        c.init(Cipher.DECRYPT_MODE, importedKey, paramSpec);
        assertEquals(new String(c.doFinal(encrypted)), plaintext);

        // Decrypt using local software copy of the key.
        c = Cipher.getInstance("DESede/CBC/PKCS7Padding");
        c.init(Cipher.DECRYPT_MODE, swKey, paramSpec);
        assertEquals(new String(c.doFinal(encrypted)), plaintext);
    }

    @Test
    public void testKeyStore_ImportWrappedKey_RSA() throws Exception {
        testKeyStore_ImportWrappedKey_RSA(false);
    }

    @Test
    public void testKeyStore_ImportWrappedKey_RSA_StrongBox() throws Exception {
        testKeyStore_ImportWrappedKey_RSA(true);
    }

    public void testKeyStore_ImportWrappedKey_RSA(boolean isStrongBox) throws Exception {
        assumeTrue("Only VSR V+ KeyMint implementations are expected to pass.",
                TestUtils.getVendorApiLevel() >= 35);

        if (isStrongBox) {
            TestUtils.assumeStrongBox();
        }

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        // Both TEE and Strongbox must support 2048-bit keys.
        int keySize = 2048;
        kpg.initialize(keySize);
        KeyPair kp = kpg.generateKeyPair();
        PublicKey publicKey = kp.getPublic();
        PrivateKey privateKey = kp.getPrivate();

        assertEquals(privateKey.getFormat(), "PKCS#8");

        byte[] keyMaterial = privateKey.getEncoded();
        byte[] mask = new byte[32]; // Zero mask

        try {
            importWrappedKey(wrapKey(
                    genKeyPair(WRAPPING_KEY_ALIAS, isStrongBox).getPublic(),
                    keyMaterial,
                    mask,
                    KM_KEY_FORMAT_PKCS8,
                    makeRsaAuthList(keySize)));
        } catch (SecureKeyImportUnavailableException e) {
            assumeNoException("Can only test if secure key import is available", e);
        }

        // Use Key
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null, null);

        assertTrue("Failed to load key after wrapped import", keyStore.containsAlias(ALIAS));

        String plaintext = "hello, world";

        Key importedKey = keyStore.getKey(ALIAS, null);
        assertTrue(importedKey instanceof PrivateKey);

        // Encrypt with KS private key, then decrypt with local public key.
        Cipher c = Cipher.getInstance("RSA/ECB/PKCS1Padding");
        c.init(Cipher.ENCRYPT_MODE, importedKey);
        byte[] encrypted = c.doFinal(plaintext.getBytes());

        c.init(Cipher.DECRYPT_MODE, publicKey);
        assertEquals(new String(c.doFinal(encrypted)), plaintext);

        // Encrypt with local public key, then decrypt with KS private key.
        c.init(Cipher.ENCRYPT_MODE, publicKey);
        encrypted = c.doFinal(plaintext.getBytes());

        c.init(Cipher.DECRYPT_MODE, importedKey);
        assertEquals(new String(c.doFinal(encrypted)), plaintext);

        // Sign with KS private key, then verify with local public key.
        Signature s = Signature.getInstance("SHA256withRSA");
        s.initSign((PrivateKey) importedKey);
        s.update(plaintext.getBytes());
        byte[] signature = s.sign();

        s.initVerify(publicKey);
        s.update(plaintext.getBytes());
        assertTrue(s.verify(signature));
    }

    @Test
    public void testKeyStore_ImportWrappedKey_EC() throws Exception {
        testKeyStore_ImportWrappedKey_EC(false);
    }

    @Test
    public void testKeyStore_ImportWrappedKey_EC_StrongBox() throws Exception {
        testKeyStore_ImportWrappedKey_EC(true);
    }

    public void testKeyStore_ImportWrappedKey_EC(boolean isStrongBox) throws Exception {
        assumeTrue("Only VSR V+ KeyMint implementations are expected to pass.",
                TestUtils.getVendorApiLevel() >= 35);

        if (isStrongBox) {
            TestUtils.assumeStrongBox();
        }

        KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
        // Both TEE and Strongbox must support P256 curve.
        int keySize = 256;
        kpg.initialize(keySize);
        KeyPair kp = kpg.generateKeyPair();
        PublicKey publicKey = kp.getPublic();
        PrivateKey privateKey = kp.getPrivate();

        assertEquals(privateKey.getFormat(), "PKCS#8");

        byte[] keyMaterial = privateKey.getEncoded();
        byte[] mask = new byte[32]; // Zero mask

        try {
            importWrappedKey(wrapKey(
                    genKeyPair(WRAPPING_KEY_ALIAS, isStrongBox).getPublic(),
                    keyMaterial,
                    mask,
                    KM_KEY_FORMAT_PKCS8,
                    makeEcAuthList(keySize)));
        } catch (SecureKeyImportUnavailableException e) {
            assumeNoException("Can only test if secure key import is available", e);
        }

        // Use Key
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null, null);

        assertTrue("Failed to load key after wrapped import", keyStore.containsAlias(ALIAS));

        String plaintext = "hello, world";

        Key importedKey = keyStore.getKey(ALIAS, null);
        assertTrue(importedKey instanceof PrivateKey);

        // Sign with KS private key, then verify with local public key.
        Signature s = Signature.getInstance("SHA256withECDSA");
        s.initSign((PrivateKey) importedKey);
        s.update(plaintext.getBytes());
        byte[] signature = s.sign();

        s.initVerify(publicKey);
        s.update(plaintext.getBytes());
        assertTrue(s.verify(signature));
    }

    public void importWrappedKey(byte[] wrappedKey, String wrappingKeyAlias) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null, null);

        AlgorithmParameterSpec spec = new KeyGenParameterSpec.Builder(wrappingKeyAlias,
                KeyProperties.PURPOSE_WRAP_KEY)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build();
        Entry wrappedKeyEntry = new WrappedKeyEntry(wrappedKey, wrappingKeyAlias,
                  "RSA/ECB/OAEPPadding", spec);
        keyStore.setEntry(ALIAS, wrappedKeyEntry, null);
    }

    public void importWrappedKey(byte[] wrappedKey) throws Exception {
        importWrappedKey(wrappedKey, WRAPPING_KEY_ALIAS);
    }

    public byte[] wrapKey(PublicKey publicKey, byte[] keyMaterial, byte[] mask,
                          int keyFormat, DERSequence authorizationList) throws Exception {
        return wrapKey(publicKey, keyMaterial, mask, keyFormat, authorizationList, true);
    }

    public byte[] wrapKey(PublicKey publicKey, byte[] keyMaterial, byte[] mask,
            int keyFormat, DERSequence authorizationList, boolean correctWrappingRequired)
            throws Exception {
        // Build description
        DEREncodableVector descriptionItems = new DEREncodableVector();
        descriptionItems.add(new DERInteger(keyFormat));
        descriptionItems.add(authorizationList);
        DERSequence wrappedKeyDescription = new DERSequence(descriptionItems);

        // Generate 12 byte initialization vector
        byte[] iv = new byte[12];
        random.nextBytes(iv);

        // Generate 256 bit AES key. This is the ephemeral key used to encrypt the secure key.
        byte[] aesKeyBytes = new byte[32];
        random.nextBytes(aesKeyBytes);

        // Encrypt ephemeral keys
        OAEPParameterSpec spec = new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT);
        Cipher pkCipher = Cipher.getInstance("RSA/ECB/OAEPPadding");
        if (correctWrappingRequired) {
            pkCipher.init(Cipher.ENCRYPT_MODE, publicKey, spec);
        } else {
            // Use incorrect OAEPParameters while initializing cipher. By default, main digest and
            // MGF1 digest are SHA-1 here.
            pkCipher.init(Cipher.ENCRYPT_MODE, publicKey);
        }
        byte[] encryptedEphemeralKeys = pkCipher.doFinal(aesKeyBytes);

        // Encrypt secure key
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        SecretKeySpec secretKeySpec = new SecretKeySpec(aesKeyBytes, "AES");
        GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(GCM_TAG_SIZE, iv);
        cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, gcmParameterSpec);
        byte[] aad = wrappedKeyDescription.getEncoded();

        cipher.updateAAD(aad);
        byte[] encryptedSecureKey = cipher.doFinal(keyMaterial);
        // Get GCM tag. Java puts the tag at the end of the ciphertext data :(
        int len = encryptedSecureKey.length;
        int tagSize = (GCM_TAG_SIZE / 8);
        byte[] tag = Arrays.copyOfRange(encryptedSecureKey, len - tagSize, len);

        // Remove GCM tag from end of output
        encryptedSecureKey = Arrays.copyOfRange(encryptedSecureKey, 0, len - tagSize);

        // Build ASN.1 DER encoded sequence WrappedKeyWrapper
        DEREncodableVector items = new DEREncodableVector();
        items.add(new DERInteger(WRAPPED_FORMAT_VERSION));
        items.add(new DEROctetString(encryptedEphemeralKeys));
        items.add(new DEROctetString(iv));
        items.add(wrappedKeyDescription);
        items.add(new DEROctetString(encryptedSecureKey));
        items.add(new DEROctetString(tag));
        return new DERSequence(items).getEncoded(ASN1Encoding.DER);
    }

    private DERSequence makeSymKeyAuthList(int size, int algo) {
        DEREncodableVector allPurposes = new DEREncodableVector();
        allPurposes.add(new DERInteger(KM_PURPOSE_ENCRYPT));
        allPurposes.add(new DERInteger(KM_PURPOSE_DECRYPT));
        DERSet purposeSet = new DERSet(allPurposes);
        DERTaggedObject purpose =
                new DERTaggedObject(true, removeTagType(KM_TAG_PURPOSE), purposeSet);
        DERTaggedObject algorithm =
                new DERTaggedObject(true, removeTagType(KM_TAG_ALGORITHM), new DERInteger(algo));
        DERTaggedObject keySize =
                new DERTaggedObject(true, removeTagType(KM_TAG_KEY_SIZE), new DERInteger(size));

        DEREncodableVector allBlockModes = new DEREncodableVector();
        allBlockModes.add(new DERInteger(KM_MODE_ECB));
        allBlockModes.add(new DERInteger(KM_MODE_CBC));
        DERSet blockModeSet = new DERSet(allBlockModes);
        DERTaggedObject blockMode =
                new DERTaggedObject(true, removeTagType(KM_TAG_BLOCK_MODE), blockModeSet);

        DEREncodableVector allPaddings = new DEREncodableVector();
        allPaddings.add(new DERInteger(KM_PAD_PKCS7));
        allPaddings.add(new DERInteger(KM_PAD_NONE));
        DERSet paddingSet = new DERSet(allPaddings);
        DERTaggedObject padding =
                new DERTaggedObject(true, removeTagType(KM_TAG_PADDING), paddingSet);

        DERTaggedObject noAuthRequired =
                new DERTaggedObject(true, removeTagType(KM_TAG_NO_AUTH_REQUIRED), DERNull.INSTANCE);

        // Build sequence
        DEREncodableVector allItems = new DEREncodableVector();
        allItems.add(purpose);
        allItems.add(algorithm);
        allItems.add(keySize);
        allItems.add(blockMode);
        allItems.add(padding);
        allItems.add(noAuthRequired);

        return new DERSequence(allItems);
    }

    private DERSequence make3desAuthList(int size) {
        return makeSymKeyAuthList(size, KM_ALGORITHM_3DES);
    }

    private DERSequence makeAesAuthList(int size) {
        return makeSymKeyAuthList(size, KM_ALGORITHM_AES);
    }

    private DERSequence makeRsaAuthList(int size) {
        DEREncodableVector allPurposes = new DEREncodableVector();
        allPurposes.add(new DERInteger(KM_PURPOSE_ENCRYPT));
        allPurposes.add(new DERInteger(KM_PURPOSE_DECRYPT));
        allPurposes.add(new DERInteger(KM_PURPOSE_SIGN));
        allPurposes.add(new DERInteger(KM_PURPOSE_VERIFY));
        DERSet purposeSet = new DERSet(allPurposes);
        DERTaggedObject purpose =
                new DERTaggedObject(true, removeTagType(KM_TAG_PURPOSE), purposeSet);

        DERTaggedObject algorithm =
                new DERTaggedObject(true, removeTagType(KM_TAG_ALGORITHM),
                                    new DERInteger(KM_ALGORITHM_RSA));
        DERTaggedObject keySize =
                new DERTaggedObject(true, removeTagType(KM_TAG_KEY_SIZE), new DERInteger(size));

        DEREncodableVector allBlockModes = new DEREncodableVector();
        allBlockModes.add(new DERInteger(KM_MODE_ECB));
        allBlockModes.add(new DERInteger(KM_MODE_CBC));
        DERSet blockModeSet = new DERSet(allBlockModes);
        DERTaggedObject blockMode =
                new DERTaggedObject(true, removeTagType(KM_TAG_BLOCK_MODE), blockModeSet);

        DEREncodableVector allDigests = new DEREncodableVector();
        allDigests.add(new DERInteger(KM_DIGEST_NONE));
        allDigests.add(new DERInteger(KM_DIGEST_MD5));
        allDigests.add(new DERInteger(KM_DIGEST_SHA1));
        allDigests.add(new DERInteger(KM_DIGEST_SHA_2_224));
        allDigests.add(new DERInteger(KM_DIGEST_SHA_2_256));
        allDigests.add(new DERInteger(KM_DIGEST_SHA_2_384));
        allDigests.add(new DERInteger(KM_DIGEST_SHA_2_512));
        DERSet digestSet = new DERSet(allDigests);
        DERTaggedObject digest =
                new DERTaggedObject(true, removeTagType(KM_TAG_DIGEST), digestSet);

        DEREncodableVector allPaddings = new DEREncodableVector();
        allPaddings.add(new DERInteger(KM_PAD_PKCS7));
        allPaddings.add(new DERInteger(KM_PAD_NONE));
        allPaddings.add(new DERInteger(KM_PAD_RSA_OAEP));
        allPaddings.add(new DERInteger(KM_PAD_RSA_PSS));
        allPaddings.add(new DERInteger(KM_PAD_RSA_PKCS1_1_5_ENCRYPT));
        allPaddings.add(new DERInteger(KM_PAD_RSA_PKCS1_1_5_SIGN));
        DERSet paddingSet = new DERSet(allPaddings);
        DERTaggedObject padding =
                new DERTaggedObject(true, removeTagType(KM_TAG_PADDING), paddingSet);

        DERTaggedObject noAuthRequired =
                new DERTaggedObject(true, removeTagType(KM_TAG_NO_AUTH_REQUIRED), DERNull.INSTANCE);

        // Build sequence
        DEREncodableVector allItems = new DEREncodableVector();
        allItems.add(purpose);
        allItems.add(algorithm);
        allItems.add(keySize);
        allItems.add(blockMode);
        allItems.add(digest);
        allItems.add(padding);
        allItems.add(noAuthRequired);

        return new DERSequence(allItems);
    }

    private DERSequence makeEcAuthList(int size) {
        DEREncodableVector allPurposes = new DEREncodableVector();
        allPurposes.add(new DERInteger(KM_PURPOSE_SIGN));
        allPurposes.add(new DERInteger(KM_PURPOSE_VERIFY));
        DERSet purposeSet = new DERSet(allPurposes);
        DERTaggedObject purpose =
                new DERTaggedObject(true, removeTagType(KM_TAG_PURPOSE), purposeSet);

        DERTaggedObject algorithm =
                new DERTaggedObject(true, removeTagType(KM_TAG_ALGORITHM),
                                    new DERInteger(KM_ALGORITHM_EC));
        DERTaggedObject keySize =
                new DERTaggedObject(true, removeTagType(KM_TAG_KEY_SIZE), new DERInteger(size));

        DEREncodableVector allDigests = new DEREncodableVector();
        allDigests.add(new DERInteger(KM_DIGEST_SHA_2_224));
        allDigests.add(new DERInteger(KM_DIGEST_SHA_2_256));
        allDigests.add(new DERInteger(KM_DIGEST_SHA_2_384));
        allDigests.add(new DERInteger(KM_DIGEST_SHA_2_512));
        DERSet digestSet = new DERSet(allDigests);
        DERTaggedObject digest =
                new DERTaggedObject(true, removeTagType(KM_TAG_DIGEST), digestSet);

        DERTaggedObject noAuthRequired =
                new DERTaggedObject(true, removeTagType(KM_TAG_NO_AUTH_REQUIRED), DERNull.INSTANCE);

        // Build sequence
        DEREncodableVector allItems = new DEREncodableVector();
        allItems.add(purpose);
        allItems.add(algorithm);
        allItems.add(keySize);
        allItems.add(digest);
        allItems.add(noAuthRequired);

        return new DERSequence(allItems);
    }

    private KeyPair genKeyPair(String alias, boolean isStrongBoxBacked) throws Exception {
        KeyPairGenerator kpg =
                KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore");
        kpg.initialize(
                new KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_WRAP_KEY)
                        .setDigests(KeyProperties.DIGEST_SHA256)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
                        .setBlockModes(KeyProperties.BLOCK_MODE_ECB)
                        .setIsStrongBoxBacked(isStrongBoxBacked)
                        .build());
        return kpg.generateKeyPair();
    }
}
