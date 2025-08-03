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

package com.android.server.nearby.presence;

import static com.google.common.truth.Truth.assertThat;

import android.nearby.BroadcastRequest;
import android.nearby.DataElement;

import org.junit.Test;

import java.util.Arrays;

/**
 * Unit test for {@link ExtendedAdvertisementUtils}.
 */
public class ExtendedAdvertisementUtilsTest {
    private static final byte[] ADVERTISEMENT1 = new byte[]{0b00100000, 12, 34, 78, 10};
    private static final byte[] ADVERTISEMENT2 = new byte[]{0b00100000, 0b00100011, 34, 78,
            (byte) 0b10010000, (byte) 0b00000100,
            1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};

    private static final int DATA_TYPE_SALT = 3;
    private static final int DATA_TYPE_PRIVATE_IDENTITY = 4;

    @Test
    public void test_constructHeader() {
        assertThat(ExtendedAdvertisementUtils.constructHeader(1)).isEqualTo(0b100000);
        assertThat(ExtendedAdvertisementUtils.constructHeader(0)).isEqualTo(0);
        assertThat(ExtendedAdvertisementUtils.constructHeader(6)).isEqualTo((byte) 0b11000000);
    }

    @Test
    public void test_getVersion() {
        assertThat(ExtendedAdvertisementUtils.getVersion(ADVERTISEMENT1)).isEqualTo(1);
        byte[] adv = new byte[]{(byte) 0b10111100, 9, 19, 90, 23};
        assertThat(ExtendedAdvertisementUtils.getVersion(adv)).isEqualTo(5);
        byte[] adv2 = new byte[]{(byte) 0b10011111, 9, 19, 90, 23};
        assertThat(ExtendedAdvertisementUtils.getVersion(adv2)).isEqualTo(4);
    }

    @Test
    public void test_getDataElementHeader_salt() {
        byte[] saltHeaderArray = ExtendedAdvertisementUtils.getDataElementHeader(ADVERTISEMENT2, 1);
        DataElementHeader header = DataElementHeader.fromBytes(
                BroadcastRequest.PRESENCE_VERSION_V1, saltHeaderArray);
        assertThat(header.getDataType()).isEqualTo(DATA_TYPE_SALT);
        assertThat(header.getDataLength()).isEqualTo(ExtendedAdvertisement.SALT_DATA_LENGTH);
    }

    @Test
    public void test_getDataElementHeader_identity() {
        byte[] identityHeaderArray =
                ExtendedAdvertisementUtils.getDataElementHeader(ADVERTISEMENT2, 4);
        DataElementHeader header = DataElementHeader.fromBytes(BroadcastRequest.PRESENCE_VERSION_V1,
                identityHeaderArray);
        assertThat(header.getDataType()).isEqualTo(DATA_TYPE_PRIVATE_IDENTITY);
        assertThat(header.getDataLength()).isEqualTo(ExtendedAdvertisement.IDENTITY_DATA_LENGTH);
    }

    @Test
    public void test_constructDataElement_salt() {
        DataElement salt = new DataElement(DATA_TYPE_SALT, new byte[]{13, 14});
        byte[] saltArray = ExtendedAdvertisementUtils.convertDataElementToBytes(salt);
        // Data length and salt header length.
        assertThat(saltArray.length).isEqualTo(ExtendedAdvertisement.SALT_DATA_LENGTH + 1);
        // Header
        assertThat(saltArray[0]).isEqualTo((byte) 0b00100011);
        // Data
        assertThat(saltArray[1]).isEqualTo((byte) 13);
        assertThat(saltArray[2]).isEqualTo((byte) 14);
    }

    @Test
    public void test_constructDataElement_privateIdentity() {
        byte[] identityData = new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16};
        DataElement identity = new DataElement(DATA_TYPE_PRIVATE_IDENTITY, identityData);
        byte[] identityArray = ExtendedAdvertisementUtils.convertDataElementToBytes(identity);
        // Data length and identity header length.
        assertThat(identityArray.length).isEqualTo(ExtendedAdvertisement.IDENTITY_DATA_LENGTH + 2);
        // 1st header byte
        assertThat(identityArray[0]).isEqualTo((byte) 0b10010000);
        // 2st header byte
        assertThat(identityArray[1]).isEqualTo((byte) 0b00000100);
        // Data
        assertThat(Arrays.copyOfRange(identityArray, 2, identityArray.length))
                .isEqualTo(identityData);
    }
}
