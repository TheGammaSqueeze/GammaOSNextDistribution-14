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
import static org.junit.Assert.assertNull;
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

import javax.crypto.IllegalBlockSizeException;

@RunWith(JUnit4.class)
public final class IkeNormalModeCipherTest {
    private static final String IKE_AUTH_INIT_REQUEST_IV = "b9132b7bb9f658dfdc648e5017a6322a";
    private static final String IKE_AUTH_INIT_REQUEST_ENCRYPT_PADDED_DATA =
            "030c316ce55f365760d46426ce5cfc78bd1ed9abff63eb9594c1bd58"
                    + "46de333ecd3ea2b705d18293b130395300ba92a351041345"
                    + "0a10525cea51b2753b4e92b081fd78d995659a98f742278f"
                    + "f9b8fd3e21554865c15c79a5134d66b2744966089e416c60"
                    + "a274e44a9a3f084eb02f3bdce1e7de9de8d9a62773ab563b"
                    + "9a69ba1db03c752acb6136452b8a86c41addb4210d68c423"
                    + "efed80e26edca5fa3fe5d0a5ca9375ce332c474b93fb1fa3"
                    + "59eb4e81";
    private static final String IKE_AUTH_INIT_REQUEST_UNENCRYPTED_PADDED_DATA =
            "2400000c010000000a50500d2700000c010000000a505050"
                    + "2100001c02000000df7c038aefaaa32d3f44b228b52a3327"
                    + "44dfb2c12c00002c00000028010304032ad4c0a20300000c"
                    + "0100000c800e008003000008030000020000000805000000"
                    + "2d00001801000000070000100000ffff00000000ffffffff"
                    + "2900001801000000070000100000ffff00000000ffffffff"
                    + "29000008000040000000000c000040010000000100000000"
                    + "000000000000000b";

    private static final String ENCR_KEY_FROM_INIT_TO_RESP = "5cbfd33f75796c0188c4a3a546aec4a1";

    private static final String AES_CTR_IV = "C0543B59DA48D90B";
    private static final String AES_CTR_ENCRYPT_DATA =
            "5104A106168A72D9790D41EE8EDAD388EB2E1EFC46DA57C8FCE630DF9141BE28";
    private static final String AES_CTR_UNENCRYPTED_DATA =
            "000102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F";
    private static final String AES_CTR_KEY = "7E24067817FAE0D743D6CE1F32539163006CB6DB";

    private static final int AES_BLOCK_SIZE = 16;

    private IkeNormalModeCipher mAesCbcCipher;
    private byte[] mAesCbcKey;
    private byte[] mIv;
    private byte[] mEncryptedPaddedData;
    private byte[] mUnencryptedPaddedData;

    private IkeNormalModeCipher mAesCtrCipher;
    private byte[] mAesCtrKey;
    private byte[] mAesCtrIv;
    private byte[] mAesCtrEncryptedData;
    private byte[] mAesCtrUnencryptedData;

    @Before
    public void setUp() throws Exception {
        mAesCbcCipher =
                (IkeNormalModeCipher)
                        IkeCipher.create(
                                new EncryptionTransform(
                                        SaProposal.ENCRYPTION_ALGORITHM_AES_CBC,
                                        SaProposal.KEY_LEN_AES_128));
        mAesCbcKey = TestUtils.hexStringToByteArray(ENCR_KEY_FROM_INIT_TO_RESP);
        mIv = TestUtils.hexStringToByteArray(IKE_AUTH_INIT_REQUEST_IV);
        mEncryptedPaddedData =
                TestUtils.hexStringToByteArray(IKE_AUTH_INIT_REQUEST_ENCRYPT_PADDED_DATA);
        mUnencryptedPaddedData =
                TestUtils.hexStringToByteArray(IKE_AUTH_INIT_REQUEST_UNENCRYPTED_PADDED_DATA);

        mAesCtrCipher =
                (IkeNormalModeCipher)
                        IkeCipher.create(
                                new EncryptionTransform(
                                        SaProposal.ENCRYPTION_ALGORITHM_AES_CTR,
                                        SaProposal.KEY_LEN_AES_128));
        mAesCtrKey = TestUtils.hexStringToByteArray(AES_CTR_KEY);
        mAesCtrIv = TestUtils.hexStringToByteArray(AES_CTR_IV);
        mAesCtrEncryptedData = TestUtils.hexStringToByteArray(AES_CTR_ENCRYPT_DATA);
        mAesCtrUnencryptedData = TestUtils.hexStringToByteArray(AES_CTR_UNENCRYPTED_DATA);
    }

    @Test
    public void testBuild() throws Exception {
        assertFalse(mAesCbcCipher.isAead());
        assertEquals(AES_BLOCK_SIZE, mAesCbcCipher.getBlockSize());
        assertEquals(AES_BLOCK_SIZE, mAesCbcCipher.generateIv().length);
    }

    @Test
    public void testGenerateRandomIv() throws Exception {
        assertFalse(Arrays.equals(mAesCbcCipher.generateIv(), mAesCbcCipher.generateIv()));
    }

    @Test
    public void testEncryptWithAesCbc() throws Exception {
        byte[] calculatedData = mAesCbcCipher.encrypt(mUnencryptedPaddedData, mAesCbcKey, mIv);

        assertArrayEquals(mEncryptedPaddedData, calculatedData);
    }

    @Test
    public void testDecryptWithAesCbc() throws Exception {
        byte[] calculatedData = mAesCbcCipher.decrypt(mEncryptedPaddedData, mAesCbcKey, mIv);
        assertArrayEquals(mUnencryptedPaddedData, calculatedData);
    }

    @Test
    public void testEncryptWithAesCtr() throws Exception {
        byte[] calculatedData =
                mAesCtrCipher.encrypt(mAesCtrUnencryptedData, mAesCtrKey, mAesCtrIv);

        assertArrayEquals(mAesCtrEncryptedData, calculatedData);
    }

    @Test
    public void testDecryptWithAesCtr() throws Exception {
        byte[] calculatedData = mAesCtrCipher.decrypt(mAesCtrEncryptedData, mAesCtrKey, mAesCtrIv);
        assertArrayEquals(mAesCtrUnencryptedData, calculatedData);
    }

    @Test
    public void testEncryptWithWrongKey() throws Exception {
        byte[] encryptionKey = TestUtils.hexStringToByteArray(ENCR_KEY_FROM_INIT_TO_RESP + "00");

        try {
            mAesCbcCipher.encrypt(mEncryptedPaddedData, encryptionKey, mIv);
            fail("Expected to fail due to encryption key with wrong length.");
        } catch (IllegalArgumentException expected) {

        }
    }

    @Test
    public void testDecryptWithNormalCipherWithBadPad() throws Exception {
        byte[] dataToDecrypt =
                TestUtils.hexStringToByteArray(
                        IKE_AUTH_INIT_REQUEST_UNENCRYPTED_PADDED_DATA + "00");
        try {
            mAesCbcCipher.decrypt(dataToDecrypt, mAesCbcKey, mIv);
            fail("Expected to fail when try to decrypt data with bad padding");
        } catch (IllegalBlockSizeException expected) {

        }
    }

    @Test
    public void testBuildIpSecAlgorithm() throws Exception {
        IpSecAlgorithm ipsecAlgorithm = mAesCbcCipher.buildIpSecAlgorithmWithKey(mAesCbcKey);

        IpSecAlgorithm expectedIpSecAlgorithm =
                new IpSecAlgorithm(IpSecAlgorithm.CRYPT_AES_CBC, mAesCbcKey);

        assertTrue(IpSecAlgorithm.equals(expectedIpSecAlgorithm, ipsecAlgorithm));
    }

    @Test
    public void testBuildIpSecAlgorithmWithInvalidKey() throws Exception {
        byte[] encryptionKey = TestUtils.hexStringToByteArray(ENCR_KEY_FROM_INIT_TO_RESP + "00");

        try {
            mAesCbcCipher.buildIpSecAlgorithmWithKey(encryptionKey);

            fail("Expected to fail due to encryption key with wrong length.");
        } catch (IllegalArgumentException expected) {

        }
    }

    @Test
    public void testGetIpSecAlgorithmName() throws Exception {
        assertEquals(
                IpSecAlgorithm.CRYPT_AES_CBC,
                IkeCipher.getIpSecAlgorithmName(SaProposal.ENCRYPTION_ALGORITHM_AES_CBC));
        assertEquals(
                IpSecAlgorithm.CRYPT_AES_CTR,
                IkeCipher.getIpSecAlgorithmName(SaProposal.ENCRYPTION_ALGORITHM_AES_CTR));
        assertEquals(
                IpSecAlgorithm.AUTH_CRYPT_AES_GCM,
                IkeCipher.getIpSecAlgorithmName(SaProposal.ENCRYPTION_ALGORITHM_AES_GCM_8));
        assertEquals(
                IpSecAlgorithm.AUTH_CRYPT_AES_GCM,
                IkeCipher.getIpSecAlgorithmName(SaProposal.ENCRYPTION_ALGORITHM_AES_GCM_12));
        assertEquals(
                IpSecAlgorithm.AUTH_CRYPT_AES_GCM,
                IkeCipher.getIpSecAlgorithmName(SaProposal.ENCRYPTION_ALGORITHM_AES_GCM_16));
        assertEquals(
                IpSecAlgorithm.AUTH_CRYPT_CHACHA20_POLY1305,
                IkeCipher.getIpSecAlgorithmName(SaProposal.ENCRYPTION_ALGORITHM_CHACHA20_POLY1305));
        assertNull(IkeCipher.getIpSecAlgorithmName(SaProposal.ENCRYPTION_ALGORITHM_3DES));
    }
}
