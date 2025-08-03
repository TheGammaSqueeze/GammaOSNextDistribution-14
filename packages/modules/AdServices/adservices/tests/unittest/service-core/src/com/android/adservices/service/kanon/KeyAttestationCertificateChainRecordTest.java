/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.adservices.service.kanon;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import com.google.common.io.BaseEncoding;

import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class KeyAttestationCertificateChainRecordTest {

    private static final int BYTES_TO_ENCODE_CERTIFICATE_LENGTH = 4;
    private static final byte[] FAKE_CERT_1 =
            BaseEncoding.base16().lowerCase().decode("aabbccddee");
    private static final byte[] FAKE_CERT_2 = BaseEncoding.base16().lowerCase().decode("112233");
    private static final byte[] FAKE_CERT_3 =
            BaseEncoding.base16().lowerCase().decode("11223322aabbddeeff4e");
    private static final List<byte[]> AS_LIST =
            Arrays.asList(FAKE_CERT_1, FAKE_CERT_2, FAKE_CERT_3);

    @Test
    public void test_serialize() throws IOException {
        byte[] encoded = KeyAttestationCertificateChainRecord.create(AS_LIST).encode();

        assertEquals(
                12 + FAKE_CERT_1.length + FAKE_CERT_2.length + FAKE_CERT_3.length, encoded.length);

        byte[] length1 = new byte[BYTES_TO_ENCODE_CERTIFICATE_LENGTH];
        System.arraycopy(encoded, 0, length1, 0, BYTES_TO_ENCODE_CERTIFICATE_LENGTH);
        assertThat(ByteBuffer.wrap(length1).getInt()).isEqualTo(FAKE_CERT_1.length);
        byte[] cert1 = new byte[FAKE_CERT_1.length];
        System.arraycopy(encoded, 4, cert1, 0, FAKE_CERT_1.length);
        assertArrayEquals(FAKE_CERT_1, cert1);

        byte[] length2 = new byte[BYTES_TO_ENCODE_CERTIFICATE_LENGTH];
        System.arraycopy(
                encoded, 4 + FAKE_CERT_1.length, length2, 0, BYTES_TO_ENCODE_CERTIFICATE_LENGTH);
        assertThat(ByteBuffer.wrap(length2).getInt()).isEqualTo(FAKE_CERT_2.length);
        byte[] cert2 = new byte[FAKE_CERT_2.length];
        System.arraycopy(encoded, 8 + FAKE_CERT_1.length, cert2, 0, FAKE_CERT_2.length);
        assertArrayEquals(FAKE_CERT_2, cert2);

        byte[] length3 = new byte[BYTES_TO_ENCODE_CERTIFICATE_LENGTH];
        System.arraycopy(
                encoded,
                8 + FAKE_CERT_1.length + FAKE_CERT_2.length,
                length3,
                0,
                BYTES_TO_ENCODE_CERTIFICATE_LENGTH);
        assertThat(ByteBuffer.wrap(length3).getInt()).isEqualTo(FAKE_CERT_3.length);
        byte[] cert3 = new byte[FAKE_CERT_3.length];
        System.arraycopy(
                encoded,
                12 + FAKE_CERT_1.length + FAKE_CERT_2.length,
                cert3,
                0,
                FAKE_CERT_3.length);
        assertArrayEquals(FAKE_CERT_3, cert3);
        assertThat(FAKE_CERT_1).isEqualTo(cert1);
    }

    @Test
    public void test_passNull_throwsException() throws IOException {
        assertThrows(
                NullPointerException.class,
                () -> KeyAttestationCertificateChainRecord.create(null).encode());
    }

    @Test
    public void test_passEmpty_returnsEmptyByteArray() throws IOException {
        byte[] encoded = KeyAttestationCertificateChainRecord.create(new ArrayList<>()).encode();
        assertEquals(0, encoded.length);
    }
}
