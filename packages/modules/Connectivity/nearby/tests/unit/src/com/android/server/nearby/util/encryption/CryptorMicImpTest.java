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

package com.android.server.nearby.util.encryption;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
/**
 * Unit test for {@link CryptorMicImp}
 */
public final class CryptorMicImpTest {
    private static final String TAG = "CryptorImpV1Test";
    private static final byte[] SALT = new byte[]{102, 22};
    private static final byte[] DATA =
            new byte[]{107, -102, 101, 107, 20, 62, 2, 73, 113, 59, 8, -14, -58, 122};
    private static final byte[] AUTHENTICITY_KEY =
            new byte[]{-89, 88, -50, -42, -99, 57, 84, -24, 121, 1, -104, -8, -26, -73, -36, 100};

    private static byte[] getEncryptedData() {
        return new byte[]{112, 23, -111, 87, 122, -27, 45, -25, -35, 84, -89, 115, 61, 113};
    }

    @Test
    public void test_encryption() throws Exception {
        Cryptor v1Cryptor = CryptorMicImp.getInstance();
        byte[] encryptedData =
                v1Cryptor.encrypt(DATA,  CryptorMicImp.generateAdvNonce(SALT), AUTHENTICITY_KEY);
        assertThat(encryptedData).isEqualTo(getEncryptedData());
    }

    @Test
    public void test_decryption() throws Exception {
        Cryptor v1Cryptor = CryptorMicImp.getInstance();
        byte[] decryptedData =
                v1Cryptor.decrypt(getEncryptedData(), CryptorMicImp.generateAdvNonce(SALT),
                        AUTHENTICITY_KEY);
        assertThat(decryptedData).isEqualTo(DATA);
    }

    @Test
    public void test_verify() {
        CryptorMicImp v1Cryptor = CryptorMicImp.getInstance();
        byte[] expectedTag = new byte[]{
                -80, -51, -101, -7, -65, 110, 37, 68, 122, -128, 57, -90, -115, -59, -61, 46};
        assertThat(v1Cryptor.verify(DATA, AUTHENTICITY_KEY, expectedTag)).isTrue();
        assertThat(v1Cryptor.verify(DATA, AUTHENTICITY_KEY, DATA)).isFalse();
    }

    @Test
    public void test_generateHmacTag_sameResult() {
        CryptorMicImp v1Cryptor = CryptorMicImp.getInstance();
        byte[] res1 = v1Cryptor.generateHmacTag(DATA, AUTHENTICITY_KEY);
        assertThat(res1)
                .isEqualTo(v1Cryptor.generateHmacTag(DATA, AUTHENTICITY_KEY));
    }

    @Test
    public void test_generateHmacTag_nullData() {
        CryptorMicImp v1Cryptor = CryptorMicImp.getInstance();
        assertThat(v1Cryptor.generateHmacTag(/* data= */ null, AUTHENTICITY_KEY)).isNull();
    }

    @Test
    public void test_generateHmacTag_nullKey() {
        CryptorMicImp v1Cryptor = CryptorMicImp.getInstance();
        assertThat(v1Cryptor.generateHmacTag(DATA, /* authenticityKey= */ null)).isNull();
    }
}
