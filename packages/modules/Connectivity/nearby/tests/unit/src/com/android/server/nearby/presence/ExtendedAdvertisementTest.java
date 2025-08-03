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

import static com.android.server.nearby.presence.PresenceConstants.PRESENCE_UUID_BYTES;

import static com.google.common.truth.Truth.assertThat;

import android.nearby.BroadcastRequest;
import android.nearby.DataElement;
import android.nearby.PresenceBroadcastRequest;
import android.nearby.PresenceCredential;
import android.nearby.PrivateCredential;
import android.nearby.PublicCredential;

import com.android.server.nearby.util.ArrayUtils;
import com.android.server.nearby.util.encryption.CryptorMicImp;

import org.junit.Before;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ExtendedAdvertisementTest {
    private static final int EXTENDED_ADVERTISEMENT_BYTE_LENGTH = 67;
    private static final int IDENTITY_TYPE = PresenceCredential.IDENTITY_TYPE_PRIVATE;
    private static final int DATA_TYPE_ACTION = 6;
    private static final int DATA_TYPE_MODEL_ID = 7;
    private static final int DATA_TYPE_BLE_ADDRESS = 101;
    private static final int DATA_TYPE_PUBLIC_IDENTITY = 3;
    private static final byte[] MODE_ID_DATA =
            new byte[]{2, 1, 30, 2, 10, -16, 6, 22, 44, -2, -86, -69, -52};
    private static final byte[] BLE_ADDRESS = new byte[]{124, 4, 56, 60, 120, -29, -90};
    private static final DataElement MODE_ID_ADDRESS_ELEMENT =
            new DataElement(DATA_TYPE_MODEL_ID, MODE_ID_DATA);
    private static final DataElement BLE_ADDRESS_ELEMENT =
            new DataElement(DATA_TYPE_BLE_ADDRESS, BLE_ADDRESS);

    private static final byte[] METADATA_ENCRYPTION_KEY =
            new byte[]{-39, -55, 115, 78, -57, 40, 115, 0, -112, 86, -86, 7, -42, 68, 11, 12};
    private static final int MEDIUM_TYPE_BLE = 0;
    private static final byte[] SALT = {2, 3};

    private static final int PRESENCE_ACTION_1 = 1;
    private static final int PRESENCE_ACTION_2 = 2;
    private static final DataElement PRESENCE_ACTION_DE_1 =
            new DataElement(DATA_TYPE_ACTION, new byte[]{PRESENCE_ACTION_1});
    private static final DataElement PRESENCE_ACTION_DE_2 =
            new DataElement(DATA_TYPE_ACTION, new byte[]{PRESENCE_ACTION_2});

    private static final byte[] SECRET_ID = new byte[]{1, 2, 3, 4};
    private static final byte[] AUTHENTICITY_KEY =
            new byte[]{-97, 10, 107, -86, 25, 65, -54, -95, -72, 59, 54, 93, 9, 3, -24, -88};
    private static final byte[] PUBLIC_KEY =
            new byte[]{
                    48, 89, 48, 19, 6, 7, 42, -122, 72, -50, 61, 2, 1, 6, 8, 42, -122, 72, -50, 61,
                    66, 0, 4, -56, -39, -92, 69, 0, 52, 23, 67, 83, -14, 75, 52, -14, -5, -41, 48,
                    -83, 31, 42, -39, 102, -13, 22, -73, -73, 86, 30, -96, -84, -13, 4, 122, 104,
                    -65, 64, 91, -109, -45, -35, -56, 55, -79, 47, -85, 27, -96, -119, -82, -80,
                    123, 41, -119, -25, 1, -112, 112
            };
    private static final byte[] ENCRYPTED_METADATA_BYTES =
            new byte[]{
                    -44, -25, -95, -124, -7, 90, 116, -8, 7, -120, -23, -22, -106, -44, -19, 61,
                    -18, 39, 29, 78, 108, -11, -39, 85, -30, 64, -99, 102, 65, 37, -42, 114, -37,
                    88, -112, 8, -75, -53, 23, -16, -104, 67, 49, 48, -53, 73, -109, 44, -23, -11,
                    -118, -61, -37, -104, 60, 105, 115, 1, 56, -89, -107, -45, -116, -1, -25, 84,
                    -19, -128, 81, 11, 92, 77, -58, 82, 122, 123, 31, -87, -57, 70, 23, -81, 7, 2,
                    -114, -83, 74, 124, -68, -98, 47, 91, 9, 48, -67, 41, -7, -97, 78, 66, -65, 58,
                    -4, -46, -30, -85, -50, 100, 46, -66, -128, 7, 66, 9, 88, 95, 12, -13, 81, -91,
            };
    private static final byte[] METADATA_ENCRYPTION_KEY_TAG =
            new byte[]{-100, 102, -35, -99, 66, -85, -55, -58, -52, 11, -74, 102, 109, -89, 1, -34,
                    45, 43, 107, -60, 99, -21, 28, 34, 31, -100, -96, 108, 108, -18, 107, 5};

    private static final String ENCODED_ADVERTISEMENT_ENCRYPTION_INFO =
            "2091911000DE2A89ED98474AF3E41E48487E8AEBDE90014C18BCB9F9AAC5C11A1BE00A10A5DCD2C49A74BE"
                    + "BAF0FE72FD5053B9DF8B9976C80BE0DCE8FEE83F1BFA9A89EB176CA48EE4ED5D15C6CDAD6B9E"
                    + "41187AA6316D7BFD8E454A53971AC00836F7AB0771FF0534050037D49C6AEB18CF9F8590E5CD"
                    + "EE2FBC330FCDC640C63F0735B7E3F02FE61A0496EF976A158AD3455D";
    private static final byte[] METADATA_ENCRYPTION_KEY_TAG_2 =
            new byte[]{-54, -39, 41, 16, 61, 79, -116, 14, 94, 0, 84, 45, 26, -108, 66, -48, 124,
                    -81, 61, 56, -98, -47, 14, -19, 116, 106, -27, 123, -81, 49, 83, -42};

    private static final String DEVICE_NAME = "test_device";

    private static final byte[] SALT_16 =
            ArrayUtils.stringToBytes("DE2A89ED98474AF3E41E48487E8AEBDE");
    private static final byte[] AUTHENTICITY_KEY_2 =  ArrayUtils.stringToBytes(
            "959D2F3CAB8EE4A2DEB0255C03762CF5D39EB919300420E75A089050FB025E20");
    private static final byte[] METADATA_ENCRYPTION_KEY_2 =  ArrayUtils.stringToBytes(
            "EF5E9A0867560E52AE1F05FCA7E48D29");

    private static final DataElement DE1 = new DataElement(571, ArrayUtils.stringToBytes(
            "537F96FD94E13BE589F0141145CFC0EEC4F86FBDB2"));
    private static final DataElement DE2 = new DataElement(541, ArrayUtils.stringToBytes(
            "D301FFB24B5B"));
    private static final DataElement DE3 = new DataElement(51, ArrayUtils.stringToBytes(
            "EA95F07C25B75C04E1B2B8731F6A55BA379FB141"));
    private static final DataElement DE4 = new DataElement(729, ArrayUtils.stringToBytes(
            "2EFD3101E2311BBB108F0A7503907EAF0C2EAAA60CDA8D33A294C4CEACE0"));
    private static final DataElement DE5 = new DataElement(411, ArrayUtils.stringToBytes("B0"));

    private PresenceBroadcastRequest.Builder mBuilder;
    private PresenceBroadcastRequest.Builder mBuilderCredentialInfo;
    private PrivateCredential mPrivateCredential;
    private PrivateCredential mPrivateCredential2;

    private PublicCredential mPublicCredential;
    private PublicCredential mPublicCredential2;

    @Before
    public void setUp() {
        mPrivateCredential =
                new PrivateCredential.Builder(
                        SECRET_ID, AUTHENTICITY_KEY, METADATA_ENCRYPTION_KEY, DEVICE_NAME)
                        .setIdentityType(PresenceCredential.IDENTITY_TYPE_PRIVATE)
                        .build();
        mPrivateCredential2 =
                new PrivateCredential.Builder(
                        SECRET_ID, AUTHENTICITY_KEY_2, METADATA_ENCRYPTION_KEY_2, DEVICE_NAME)
                        .setIdentityType(PresenceCredential.IDENTITY_TYPE_PRIVATE)
                        .build();
        mPublicCredential =
                new PublicCredential.Builder(SECRET_ID, AUTHENTICITY_KEY, PUBLIC_KEY,
                        ENCRYPTED_METADATA_BYTES, METADATA_ENCRYPTION_KEY_TAG)
                        .build();
        mPublicCredential2 =
                new PublicCredential.Builder(SECRET_ID, AUTHENTICITY_KEY_2, PUBLIC_KEY,
                        ENCRYPTED_METADATA_BYTES, METADATA_ENCRYPTION_KEY_TAG_2)
                        .build();
        mBuilder =
                new PresenceBroadcastRequest.Builder(Collections.singletonList(MEDIUM_TYPE_BLE),
                        SALT, mPrivateCredential)
                        .setVersion(BroadcastRequest.PRESENCE_VERSION_V1)
                        .addAction(PRESENCE_ACTION_1)
                        .addAction(PRESENCE_ACTION_2)
                        .addExtendedProperty(new DataElement(DATA_TYPE_BLE_ADDRESS, BLE_ADDRESS))
                        .addExtendedProperty(new DataElement(DATA_TYPE_MODEL_ID, MODE_ID_DATA));

        mBuilderCredentialInfo =
                new PresenceBroadcastRequest.Builder(Collections.singletonList(MEDIUM_TYPE_BLE),
                        SALT_16, mPrivateCredential2)
                        .setVersion(BroadcastRequest.PRESENCE_VERSION_V1)
                        .addExtendedProperty(DE1)
                        .addExtendedProperty(DE2)
                        .addExtendedProperty(DE3)
                        .addExtendedProperty(DE4)
                        .addExtendedProperty(DE5);
    }

    @Test
    public void test_createFromRequest() {
        ExtendedAdvertisement originalAdvertisement = ExtendedAdvertisement.createFromRequest(
                mBuilder.build());

        assertThat(originalAdvertisement.getActions())
                .containsExactly(PRESENCE_ACTION_1, PRESENCE_ACTION_2);
        assertThat(originalAdvertisement.getIdentity()).isEqualTo(METADATA_ENCRYPTION_KEY);
        assertThat(originalAdvertisement.getIdentityType()).isEqualTo(IDENTITY_TYPE);
        assertThat(originalAdvertisement.getVersion()).isEqualTo(
                BroadcastRequest.PRESENCE_VERSION_V1);
        assertThat(originalAdvertisement.getSalt()).isEqualTo(SALT);
        assertThat(originalAdvertisement.getDataElements())
                .containsExactly(PRESENCE_ACTION_DE_1, PRESENCE_ACTION_DE_2,
                        MODE_ID_ADDRESS_ELEMENT, BLE_ADDRESS_ELEMENT);
        assertThat(originalAdvertisement.getLength()).isEqualTo(EXTENDED_ADVERTISEMENT_BYTE_LENGTH);
    }

    @Test
    public void test_createFromRequest_credentialInfo() {
        ExtendedAdvertisement originalAdvertisement = ExtendedAdvertisement.createFromRequest(
                mBuilderCredentialInfo.build());

        assertThat(originalAdvertisement.getIdentity()).isEqualTo(METADATA_ENCRYPTION_KEY_2);
        assertThat(originalAdvertisement.getIdentityType()).isEqualTo(IDENTITY_TYPE);
        assertThat(originalAdvertisement.getVersion()).isEqualTo(
                BroadcastRequest.PRESENCE_VERSION_V1);
        assertThat(originalAdvertisement.getSalt()).isEqualTo(SALT_16);
        assertThat(originalAdvertisement.getDataElements())
                .containsExactly(DE1, DE2, DE3, DE4, DE5);
    }

    @Test
    public void test_createFromRequest_encodeAndDecode() {
        ExtendedAdvertisement originalAdvertisement = ExtendedAdvertisement.createFromRequest(
                mBuilder.build());
        byte[] generatedBytes = originalAdvertisement.toBytes();
        ExtendedAdvertisement newAdvertisement =
                ExtendedAdvertisement.fromBytes(generatedBytes, mPublicCredential);

        assertThat(newAdvertisement.getActions())
                .containsExactly(PRESENCE_ACTION_1, PRESENCE_ACTION_2);
        assertThat(newAdvertisement.getIdentity()).isEqualTo(METADATA_ENCRYPTION_KEY);
        assertThat(newAdvertisement.getIdentityType()).isEqualTo(IDENTITY_TYPE);
        assertThat(newAdvertisement.getLength()).isEqualTo(EXTENDED_ADVERTISEMENT_BYTE_LENGTH);
        assertThat(newAdvertisement.getVersion()).isEqualTo(
                BroadcastRequest.PRESENCE_VERSION_V1);
        assertThat(newAdvertisement.getSalt()).isEqualTo(SALT);
        assertThat(newAdvertisement.getDataElements())
                .containsExactly(MODE_ID_ADDRESS_ELEMENT, BLE_ADDRESS_ELEMENT,
                        PRESENCE_ACTION_DE_1, PRESENCE_ACTION_DE_2);
    }

    @Test
    public void test_createFromRequest_invalidParameter() {
        // invalid version
        mBuilder.setVersion(BroadcastRequest.PRESENCE_VERSION_V0);
        assertThat(ExtendedAdvertisement.createFromRequest(mBuilder.build())).isNull();

        // invalid salt
        PresenceBroadcastRequest.Builder builder =
                new PresenceBroadcastRequest.Builder(Collections.singletonList(MEDIUM_TYPE_BLE),
                        new byte[]{1, 2, 3}, mPrivateCredential)
                        .setVersion(BroadcastRequest.PRESENCE_VERSION_V1)
                        .addAction(PRESENCE_ACTION_1);
        assertThat(ExtendedAdvertisement.createFromRequest(builder.build())).isNull();

        // invalid identity
        PrivateCredential privateCredential =
                new PrivateCredential.Builder(SECRET_ID,
                        AUTHENTICITY_KEY, new byte[]{1, 2, 3, 4}, DEVICE_NAME)
                        .setIdentityType(PresenceCredential.IDENTITY_TYPE_PRIVATE)
                        .build();
        PresenceBroadcastRequest.Builder builder2 =
                new PresenceBroadcastRequest.Builder(Collections.singletonList(MEDIUM_TYPE_BLE),
                        new byte[]{1, 2, 3}, privateCredential)
                        .setVersion(BroadcastRequest.PRESENCE_VERSION_V1)
                        .addAction(PRESENCE_ACTION_1);
        assertThat(ExtendedAdvertisement.createFromRequest(builder2.build())).isNull();
    }

    @Test
    public void test_toBytesSalt() throws Exception {
        ExtendedAdvertisement adv = ExtendedAdvertisement.createFromRequest(mBuilder.build());
        assertThat(adv.toBytes()).isEqualTo(getExtendedAdvertisementByteArray());
    }

    @Test
    public void test_fromBytesSalt() throws Exception {
        byte[] originalBytes = getExtendedAdvertisementByteArray();
        ExtendedAdvertisement adv =
                ExtendedAdvertisement.fromBytes(originalBytes, mPublicCredential);

        assertThat(adv.getActions())
                .containsExactly(PRESENCE_ACTION_1, PRESENCE_ACTION_2);
        assertThat(adv.getIdentity()).isEqualTo(METADATA_ENCRYPTION_KEY);
        assertThat(adv.getIdentityType()).isEqualTo(IDENTITY_TYPE);
        assertThat(adv.getLength()).isEqualTo(EXTENDED_ADVERTISEMENT_BYTE_LENGTH);
        assertThat(adv.getVersion()).isEqualTo(
                BroadcastRequest.PRESENCE_VERSION_V1);
        assertThat(adv.getSalt()).isEqualTo(SALT);
        assertThat(adv.getDataElements())
                .containsExactly(MODE_ID_ADDRESS_ELEMENT, BLE_ADDRESS_ELEMENT,
                        PRESENCE_ACTION_DE_1, PRESENCE_ACTION_DE_2);
    }

    @Test
    public void test_toBytesCredentialElement() {
        ExtendedAdvertisement adv =
                ExtendedAdvertisement.createFromRequest(mBuilderCredentialInfo.build());
        assertThat(ArrayUtils.bytesToStringUppercase(adv.toBytes())).isEqualTo(
                ENCODED_ADVERTISEMENT_ENCRYPTION_INFO);
    }

    @Test
    public void test_fromBytesCredentialElement() {
        ExtendedAdvertisement adv =
                ExtendedAdvertisement.fromBytes(
                        ArrayUtils.stringToBytes(ENCODED_ADVERTISEMENT_ENCRYPTION_INFO),
                        mPublicCredential2);
        assertThat(adv.getIdentity()).isEqualTo(METADATA_ENCRYPTION_KEY_2);
        assertThat(adv.getIdentityType()).isEqualTo(IDENTITY_TYPE);
        assertThat(adv.getVersion()).isEqualTo(BroadcastRequest.PRESENCE_VERSION_V1);
        assertThat(adv.getSalt()).isEqualTo(SALT_16);
        assertThat(adv.getDataElements()).containsExactly(DE1, DE2, DE3, DE4, DE5);
    }

    @Test
    public void test_fromBytes_metadataTagNotMatched_fail() throws Exception {
        byte[] originalBytes = getExtendedAdvertisementByteArray();
        PublicCredential credential =
                new PublicCredential.Builder(SECRET_ID, AUTHENTICITY_KEY, PUBLIC_KEY,
                        ENCRYPTED_METADATA_BYTES,
                        new byte[]{113, 90, -55, 73, 25, -9, 55, -44, 102, 44, 81, -68, 101, 21, 32,
                                92, -107, 3, 108, 90, 28, -73, 16, 49, -95, -121, 8, -45, -27, 16,
                                6, 108})
                        .build();
        ExtendedAdvertisement adv =
                ExtendedAdvertisement.fromBytes(originalBytes, credential);
        assertThat(adv).isNull();
    }

    @Test
    public void test_toString() {
        ExtendedAdvertisement adv = ExtendedAdvertisement.createFromRequest(mBuilder.build());
        assertThat(adv.toString()).isEqualTo("ExtendedAdvertisement:"
                + "<VERSION: 1, length: " + EXTENDED_ADVERTISEMENT_BYTE_LENGTH
                + ", dataElementCount: 4, identityType: 1, "
                + "identity: " + Arrays.toString(METADATA_ENCRYPTION_KEY)
                + ", salt: [2, 3],"
                + " actions: [1, 2]>");
    }

    @Test
    public void test_getDataElements_accordingToType() {
        ExtendedAdvertisement adv = ExtendedAdvertisement.createFromRequest(mBuilder.build());
        List<DataElement> dataElements = new ArrayList<>();

        dataElements.add(BLE_ADDRESS_ELEMENT);
        assertThat(adv.getDataElements(DATA_TYPE_BLE_ADDRESS)).isEqualTo(dataElements);
        assertThat(adv.getDataElements(DATA_TYPE_PUBLIC_IDENTITY)).isEmpty();
    }

    private static byte[] getExtendedAdvertisementByteArray() throws Exception {
        CryptorMicImp cryptor = CryptorMicImp.getInstance();
        ByteBuffer buffer = ByteBuffer.allocate(EXTENDED_ADVERTISEMENT_BYTE_LENGTH);
        buffer.put((byte) 0b00100000); // Header V1
        buffer.put(
                (byte) (EXTENDED_ADVERTISEMENT_BYTE_LENGTH - 2)); // Section header (section length)

        // Salt data
        // Salt header: length 2, type 0
        byte[] saltBytes = ArrayUtils.concatByteArrays(new byte[]{(byte) 0b00100000}, SALT);
        buffer.put(saltBytes);
        // Identity header: length 16, type 1 (private identity)
        byte[] identityHeader = new byte[]{(byte) 0b10010000, (byte) 0b00000001};
        buffer.put(identityHeader);

        ByteBuffer deBuffer = ByteBuffer.allocate(28);
        // Ble address header: length 7, type 102
        deBuffer.put(new byte[]{(byte) 0b10000111, (byte) 0b01100101});
        // Ble address data
        deBuffer.put(BLE_ADDRESS);
        // model id header: length 13, type 7
        deBuffer.put(new byte[]{(byte) 0b10001101, (byte) 0b00000111});
        // model id data
        deBuffer.put(MODE_ID_DATA);
        // Action1 header: length 1, type 6
        deBuffer.put(new byte[]{(byte) 0b00010110});
        // Action1 data
        deBuffer.put((byte) PRESENCE_ACTION_1);
        // Action2 header: length 1, type 6
        deBuffer.put(new byte[]{(byte) 0b00010110});
        // Action2 data
        deBuffer.put((byte) PRESENCE_ACTION_2);
        byte[] deBytes = deBuffer.array();
        byte[] nonce = CryptorMicImp.generateAdvNonce(SALT);
        byte[] ciphertext =
                cryptor.encrypt(
                        ArrayUtils.concatByteArrays(METADATA_ENCRYPTION_KEY, deBytes),
                        nonce, AUTHENTICITY_KEY);
        buffer.put(ciphertext);

        byte[] dataToSign = ArrayUtils.concatByteArrays(
                PRESENCE_UUID_BYTES, /* UUID */
                new byte[]{(byte) 0b00100000}, /* header */
                new byte[]{(byte) (EXTENDED_ADVERTISEMENT_BYTE_LENGTH - 2)} /* sectionHeader */,
                saltBytes, /* salt */
                nonce, identityHeader, ciphertext);
        byte[] mic = cryptor.sign(dataToSign, AUTHENTICITY_KEY);
        buffer.put(mic);

        return buffer.array();
    }
}
