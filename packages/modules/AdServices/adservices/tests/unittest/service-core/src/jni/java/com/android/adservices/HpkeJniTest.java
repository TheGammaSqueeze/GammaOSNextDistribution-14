/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.adservices;

import com.android.adservices.service.measurement.aggregation.AggregateCryptoFixture;

import org.junit.Assert;
import org.junit.Test;

import java.util.Base64;

public class HpkeJniTest {

    private static final byte[] sAssociatedData = "associated_data".getBytes();
    private static final byte[] sPlaintext = "plaintext".getBytes();
    private static final byte[] sCiphertext =
            decode("0Ie+jDZ/Hznx1IrIkS06V+kAHuD5RsybXWwrKRIbGEL5TJT4/HYny2SHfWbeXxMydwvS0FEZqvzs");
    private static final byte[] sPublicKey = AggregateCryptoFixture.getPublicKey();
    private static final byte[] sPrivateKey = AggregateCryptoFixture.getPrivateKey();

    @Test
    public void testHpkeEncrypt_Success() {
        final byte[] result = HpkeJni.encrypt(sPublicKey, sPlaintext, sAssociatedData);
        Assert.assertNotNull(result);
        Assert.assertTrue(result.length > 0);
    }

    @Test
    public void testHpkeDecrypt_Success() {
        final byte[] result = HpkeJni.decrypt(sPrivateKey, sCiphertext, sAssociatedData);
        Assert.assertNotNull(result);
        Assert.assertTrue(result.length > 0);
        Assert.assertTrue(new String(sPlaintext).equals(new String(result)));
    }

    @Test
    public void testHpkeEncryptDecrypt_Success() {
        final byte[] ciphertext = HpkeJni.encrypt(sPublicKey, sPlaintext, sAssociatedData);
        Assert.assertNotNull(ciphertext);
        Assert.assertTrue(ciphertext.length > 0);
        Assert.assertFalse(new String(sPlaintext).equals(new String(ciphertext)));

        final byte[] plaintext = HpkeJni.decrypt(sPrivateKey, ciphertext, sAssociatedData);
        Assert.assertNotNull(plaintext);
        Assert.assertTrue(plaintext.length > 0);
        Assert.assertTrue(new String(sPlaintext).equals(new String(plaintext)));
    }

    @Test
    public void testHpkeEncrypt_publicKeyNull_fail() {
        final byte[] result = HpkeJni.encrypt(/* publicKey= */ null, sPlaintext, sAssociatedData);
        Assert.assertNull(result);
    }

    @Test
    public void testHpkeEncrypt_publicKeyShorterThan32_fail() {
        final byte[] shortPublicKey = new byte[31];
        final byte[] result = HpkeJni.encrypt(shortPublicKey, sPlaintext, sAssociatedData);
        Assert.assertNull(result);
    }

    @Test
    public void testHpkeEncrypt_publicKeyLongerThan32_fail() {
        final byte[] longPublicKey = new byte[33];
        final byte[] result = HpkeJni.encrypt(longPublicKey, sPlaintext, sAssociatedData);
        Assert.assertNull(result);
    }

    @Test
    public void testHpkeEncrypt_plainTextNull_fail() {
        final byte[] result = HpkeJni.encrypt(sPublicKey, /* plainText = */ null, sAssociatedData);
        Assert.assertNull(result);
    }

    @Test
    public void testHpkeEncrypt_plainTextEmpty_success() {
        final byte[] emptyPlainText = new byte[] {};
        final byte[] result = HpkeJni.encrypt(sPublicKey, emptyPlainText, sAssociatedData);
        Assert.assertNotNull(result);
        Assert.assertTrue(result.length > 0);
    }

    @Test
    public void testHpkeEncrypt_associatedDataNull_fail() {
        final byte[] result = HpkeJni.encrypt(sPublicKey, sPlaintext, /* associatedData = */ null);
        Assert.assertNull(result);
    }

    @Test
    public void testHpkeEncrypt_associatedDataEmpty_success() {
        final byte[] emptyAssociatedData = new byte[] {};
        final byte[] result = HpkeJni.encrypt(sPublicKey, sPlaintext, emptyAssociatedData);
        Assert.assertNotNull(result);
        Assert.assertTrue(result.length > 0);
    }

    @Test
    public void testHpkeDecrypt_privateKeyNull_fail() {
        final byte[] result = HpkeJni.decrypt(/* privateKey= */ null, sCiphertext, sAssociatedData);
        Assert.assertNull(result);
    }

    @Test
    public void testHpkDecrypt_privateKeyShorterThan32_fail() {
        final byte[] shortPrivateKey = new byte[31];
        final byte[] result = HpkeJni.decrypt(shortPrivateKey, sCiphertext, sAssociatedData);
        Assert.assertNull(result);
    }

    @Test
    public void testHpkeDecrypt_privateKeyLargerThan32_fail() {
        final byte[] longPrivateKey = new byte[33];
        final byte[] result = HpkeJni.decrypt(longPrivateKey, sCiphertext, sAssociatedData);
        Assert.assertNull(result);
    }

    @Test
    public void testHpkeDecrypt_privateKeyInvalid_fail() {
        final byte[] privateKey = new byte[32];
        final byte[] result = HpkeJni.decrypt(privateKey, sCiphertext, sAssociatedData);
        Assert.assertNull(result);
    }

    @Test
    public void testHpkeDecrypt_ciphertextNull_fail() {
        final byte[] result =
                HpkeJni.encrypt(sPrivateKey, /* ciphertext = */ null, sAssociatedData);
        Assert.assertNull(result);
    }

    @Test
    public void testHpkeDecrypt_ciphertextInvalid_fail() {
        final byte[] emptyCiphertext = new byte[] {};
        final byte[] result = HpkeJni.decrypt(sPrivateKey, emptyCiphertext, sAssociatedData);
        Assert.assertNull(result);
    }

    @Test
    public void testHpkeDecrypt_associatedDataNull_fail() {
        final byte[] result =
                HpkeJni.decrypt(sPrivateKey, sCiphertext, /* associatedData = */ null);
        Assert.assertNull(result);
    }

    private static byte[] decode(String value) {
        return Base64.getDecoder().decode(value.getBytes());
    }
}
