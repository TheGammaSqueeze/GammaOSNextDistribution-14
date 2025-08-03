/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.internal.net.ipsec.test.ike.crypto;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.net.IpSecAlgorithm;
import android.net.ipsec.test.ike.SaProposal;

import com.android.internal.net.TestUtils;
import com.android.internal.net.ipsec.test.ike.message.IkeSaPayload.EncryptionTransform;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Arrays;
import java.util.Random;

import javax.crypto.AEADBadTagException;

@RunWith(JUnit4.class)
public final class IkeCombinedModeCipherTest {
    private static final String IV = "fbd69d9de2dafc5e";
    private static final String ENCRYPTED_PADDED_DATA_WITH_CHECKSUM =
            "f4109834e9f3559758c05edf119917521b885f67f0d14ced43";
    private static final String UNENCRYPTED_PADDED_DATA = "000000080000400f00";
    private static final String ADDITIONAL_AUTH_DATA =
            "77c708b4523e39a471dc683c1d4f21362e202508000000060000004129000025";
    private static final String KEY =
            "7C04513660DEC572D896105254EF92608054F8E6EE19E79CE52AB8697B2B5F2C2AA90C29";

    private static final String CHACHA_POLY_IV = "1011121314151617";
    private static final String CHACHA_POLY_ENCRYPTED_DATA_WITH_CHECKSUM =
            "24039428b97f417e3c13753a4f05087b67c352e6a7fab1b982d466ef407ae5c614ee8099"
                    + "d52844eb61aa95dfab4c02f72aa71e7c4c4f64c9befe2facc638e8f3cbec163fac469b50"
                    + "2773f6fb94e664da9165b82829f641e076aaa8266b7fb0f7b11b369907e1ad43";
    private static final String CHACHA_POLY_UNENCRYPTED_DATA =
            "45000054a6f200004001e778c6336405c000020508005b7a3a080000553bec1000073627"
                    + "08090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f202122232425262728292a2b"
                    + "2c2d2e2f303132333435363701020204";
    private static final String CHACHA_POLY_ADDITIONAL_AUTH_DATA = "0102030400000005";
    private static final String CHACHA_POLY_KEY =
            "808182838485868788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9fa0a1a2a3";

    private static final int AES_GCM_IV_LEN = 8;
    private static final int AES_GCM_16_CHECKSUM_LEN = 128;

    private IkeCombinedModeCipher mAesGcm16Cipher;
    private byte[] mAesGcmKey;
    private byte[] mIv;
    private byte[] mEncryptedPaddedDataWithChecksum;
    private byte[] mUnencryptedPaddedData;
    private byte[] mAdditionalAuthData;

    private IkeCombinedModeCipher mChaChaPolyCipher;
    private byte[] mChaChaPolyKey;
    private byte[] mChaChaPolyIv;
    private byte[] mChaChaPolyEncryptedDataWithChecksum;
    private byte[] mChaChaPolyUnencryptedData;
    private byte[] mChaChaPolyAdditionalAuthData;

    @Before
    public void setUp() {
        mAesGcm16Cipher =
                (IkeCombinedModeCipher)
                        IkeCipher.create(
                                new EncryptionTransform(
                                        SaProposal.ENCRYPTION_ALGORITHM_AES_GCM_16,
                                        SaProposal.KEY_LEN_AES_256));

        mAesGcmKey = TestUtils.hexStringToByteArray(KEY);
        mIv = TestUtils.hexStringToByteArray(IV);
        mEncryptedPaddedDataWithChecksum =
                TestUtils.hexStringToByteArray(ENCRYPTED_PADDED_DATA_WITH_CHECKSUM);
        mUnencryptedPaddedData = TestUtils.hexStringToByteArray(UNENCRYPTED_PADDED_DATA);
        mAdditionalAuthData = TestUtils.hexStringToByteArray(ADDITIONAL_AUTH_DATA);

        mChaChaPolyCipher =
                (IkeCombinedModeCipher)
                        IkeCipher.create(
                                new EncryptionTransform(
                                        SaProposal.ENCRYPTION_ALGORITHM_CHACHA20_POLY1305));

        mChaChaPolyKey = TestUtils.hexStringToByteArray(CHACHA_POLY_KEY);
        mChaChaPolyIv = TestUtils.hexStringToByteArray(CHACHA_POLY_IV);
        mChaChaPolyEncryptedDataWithChecksum =
                TestUtils.hexStringToByteArray(CHACHA_POLY_ENCRYPTED_DATA_WITH_CHECKSUM);
        mChaChaPolyUnencryptedData = TestUtils.hexStringToByteArray(CHACHA_POLY_UNENCRYPTED_DATA);
        mChaChaPolyAdditionalAuthData =
                TestUtils.hexStringToByteArray(CHACHA_POLY_ADDITIONAL_AUTH_DATA);
    }

    @Test
    public void testBuild() throws Exception {
        assertTrue(mAesGcm16Cipher.isAead());
        assertEquals(AES_GCM_IV_LEN, mAesGcm16Cipher.generateIv().length);
    }

    @Test
    public void testGenerateRandomIv() throws Exception {
        assertFalse(Arrays.equals(mAesGcm16Cipher.generateIv(), mAesGcm16Cipher.generateIv()));
    }

    @Test
    public void testAesGcmEncrypt() throws Exception {
        byte[] encryptedCiphertext =
                mAesGcm16Cipher.encrypt(
                        mUnencryptedPaddedData, mAdditionalAuthData, mAesGcmKey, mIv);

        assertArrayEquals(mEncryptedPaddedDataWithChecksum, encryptedCiphertext);
    }

    @Test
    public void testAesGcmDecrypt() throws Exception {
        byte[] decryptedPlaintext =
                mAesGcm16Cipher.decrypt(
                        mEncryptedPaddedDataWithChecksum, mAdditionalAuthData, mAesGcmKey, mIv);

        assertArrayEquals(mUnencryptedPaddedData, decryptedPlaintext);
    }

    @Test
    public void testChaChaPolyEncrypt() throws Exception {
        byte[] encryptedCiphertext =
                mChaChaPolyCipher.encrypt(
                        mChaChaPolyUnencryptedData,
                        mChaChaPolyAdditionalAuthData,
                        mChaChaPolyKey,
                        mChaChaPolyIv);

        assertArrayEquals(mChaChaPolyEncryptedDataWithChecksum, encryptedCiphertext);
    }

    @Test
    public void testChaChaPolyDecrypt() throws Exception {
        byte[] decryptedPlaintext =
                mChaChaPolyCipher.decrypt(
                        mChaChaPolyEncryptedDataWithChecksum,
                        mChaChaPolyAdditionalAuthData,
                        mChaChaPolyKey,
                        mChaChaPolyIv);

        assertArrayEquals(mChaChaPolyUnencryptedData, decryptedPlaintext);
    }

    @Test
    public void testEncryptWithWrongKeyLen() throws Exception {
        byte[] encryptionKey = TestUtils.hexStringToByteArray(KEY + "00");

        try {
            mAesGcm16Cipher.encrypt(
                    mUnencryptedPaddedData, mAdditionalAuthData, encryptionKey, mIv);
            fail("Expected to fail because encryption key has wrong length.");
        } catch (IllegalArgumentException expected) {

        }
    }

    @Test
    public void testDecrypWithWrongKey() throws Exception {
        byte[] encryptionKey = new byte[mAesGcmKey.length];
        new Random().nextBytes(encryptionKey);

        try {
            mAesGcm16Cipher.decrypt(
                    mEncryptedPaddedDataWithChecksum, mAdditionalAuthData, encryptionKey, mIv);
            fail("Expected to fail because decryption key is wrong");
        } catch (AEADBadTagException expected) {

        }
    }

    @Test
    public void testBuildIpSecAlgorithm() throws Exception {
        IpSecAlgorithm ipsecAlgorithm = mAesGcm16Cipher.buildIpSecAlgorithmWithKey(mAesGcmKey);

        IpSecAlgorithm expectedIpSecAlgorithm =
                new IpSecAlgorithm(
                        IpSecAlgorithm.AUTH_CRYPT_AES_GCM, mAesGcmKey, AES_GCM_16_CHECKSUM_LEN);

        assertTrue(IpSecAlgorithm.equals(expectedIpSecAlgorithm, ipsecAlgorithm));
    }

    @Test
    public void buildIpSecAlgorithmWithInvalidKey() throws Exception {
        byte[] encryptionKey = TestUtils.hexStringToByteArray(KEY + "00");

        try {
            mAesGcm16Cipher.buildIpSecAlgorithmWithKey(encryptionKey);
            fail("Expected to fail because encryption key has wrong length.");
        } catch (IllegalArgumentException expected) {

        }
    }
}
