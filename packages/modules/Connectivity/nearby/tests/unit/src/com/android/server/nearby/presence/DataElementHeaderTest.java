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

import static org.junit.Assert.assertThrows;

import android.nearby.BroadcastRequest;

import org.junit.Test;

import java.util.List;

/**
 * Unit test for {@link DataElementHeader}.
 */
public class DataElementHeaderTest {

    private static final int VERSION = BroadcastRequest.PRESENCE_VERSION_V1;

    @Test
    public void test_illegalLength() {
        assertThrows(IllegalArgumentException.class,
                () -> new DataElementHeader(VERSION, 12, 128));
    }

    @Test
    public void test_singeByteConversion() {
        DataElementHeader header = new DataElementHeader(VERSION, 12, 3);
        byte[] bytes = header.toBytes();
        assertThat(bytes).isEqualTo(new byte[]{(byte) 0b00111100});

        DataElementHeader afterConversionHeader = DataElementHeader.fromBytes(VERSION, bytes);
        assertThat(afterConversionHeader.getDataLength()).isEqualTo(3);
        assertThat(afterConversionHeader.getDataType()).isEqualTo(12);
    }

    @Test
    public void test_multipleBytesConversion() {
        DataElementHeader header = new DataElementHeader(VERSION, 6, 100);
        DataElementHeader afterConversionHeader =
                DataElementHeader.fromBytes(VERSION, header.toBytes());
        assertThat(afterConversionHeader.getDataLength()).isEqualTo(100);
        assertThat(afterConversionHeader.getDataType()).isEqualTo(6);
    }

    @Test
    public void test_fromBytes() {
        // Single byte case.
        byte[] singleByte = new byte[]{(byte) 0b01011101};
        DataElementHeader singeByteHeader = DataElementHeader.fromBytes(VERSION, singleByte);
        assertThat(singeByteHeader.getDataLength()).isEqualTo(5);
        assertThat(singeByteHeader.getDataType()).isEqualTo(13);

        // Two bytes case.
        byte[] twoBytes = new byte[]{(byte) 0b11011101, (byte) 0b01011101};
        DataElementHeader twoBytesHeader = DataElementHeader.fromBytes(VERSION, twoBytes);
        assertThat(twoBytesHeader.getDataLength()).isEqualTo(93);
        assertThat(twoBytesHeader.getDataType()).isEqualTo(93);

        // Three bytes case.
        byte[] threeBytes = new byte[]{(byte) 0b11011101, (byte) 0b11111111, (byte) 0b01011101};
        DataElementHeader threeBytesHeader = DataElementHeader.fromBytes(VERSION, threeBytes);
        assertThat(threeBytesHeader.getDataLength()).isEqualTo(93);
        assertThat(threeBytesHeader.getDataType()).isEqualTo(16349);

        // Four bytes case.
        byte[] fourBytes = new byte[]{
                (byte) 0b11011101, (byte) 0b11111111, (byte) 0b11111111, (byte) 0b01011101};

        DataElementHeader fourBytesHeader = DataElementHeader.fromBytes(VERSION, fourBytes);
        assertThat(fourBytesHeader.getDataLength()).isEqualTo(93);
        assertThat(fourBytesHeader.getDataType()).isEqualTo(2097117);
    }

    @Test
    public void test_fromBytesIllegal_singleByte() {
        assertThrows(IllegalArgumentException.class,
                () -> DataElementHeader.fromBytes(VERSION, new byte[]{(byte) 0b11011101}));
    }

    @Test
    public void test_fromBytesIllegal_twoBytes_wrongFirstByte() {
        assertThrows(IllegalArgumentException.class,
                () -> DataElementHeader.fromBytes(VERSION,
                        new byte[]{(byte) 0b01011101, (byte) 0b01011101}));
    }

    @Test
    public void test_fromBytesIllegal_twoBytes_wrongLastByte() {
        assertThrows(IllegalArgumentException.class,
                () -> DataElementHeader.fromBytes(VERSION,
                        new byte[]{(byte) 0b11011101, (byte) 0b11011101}));
    }

    @Test
    public void test_fromBytesIllegal_threeBytes() {
        assertThrows(IllegalArgumentException.class,
                () -> DataElementHeader.fromBytes(VERSION,
                        new byte[]{(byte) 0b11011101, (byte) 0b11011101, (byte) 0b11011101}));
    }

    @Test
    public void test_multipleBytesConversion_largeNumber() {
        DataElementHeader header = new DataElementHeader(VERSION, 22213546, 66);
        DataElementHeader afterConversionHeader =
                DataElementHeader.fromBytes(VERSION, header.toBytes());
        assertThat(afterConversionHeader.getDataLength()).isEqualTo(66);
        assertThat(afterConversionHeader.getDataType()).isEqualTo(22213546);
    }

    @Test
    public void test_isExtending() {
        assertThat(DataElementHeader.isExtending((byte) 0b10000100)).isTrue();
        assertThat(DataElementHeader.isExtending((byte) 0b01110100)).isFalse();
        assertThat(DataElementHeader.isExtending((byte) 0b00000000)).isFalse();
    }

    @Test
    public void test_convertTag() {
        assertThat(DataElementHeader.convertTag(true)).isEqualTo((byte) 128);
        assertThat(DataElementHeader.convertTag(false)).isEqualTo(0);
    }

    @Test
    public void test_getHeaderValue() {
        assertThat(DataElementHeader.getHeaderValue((byte) 0b10000100)).isEqualTo(4);
        assertThat(DataElementHeader.getHeaderValue((byte) 0b00000100)).isEqualTo(4);
        assertThat(DataElementHeader.getHeaderValue((byte) 0b11010100)).isEqualTo(84);
        assertThat(DataElementHeader.getHeaderValue((byte) 0b01010100)).isEqualTo(84);
    }

    @Test
    public void test_convertTypeMultipleIntList() {
        List<Byte> list = DataElementHeader.convertTypeMultipleBytes(128);
        assertThat(list.size()).isEqualTo(2);
        assertThat(list.get(0)).isEqualTo((byte) 0b10000001);
        assertThat(list.get(1)).isEqualTo((byte) 0b00000000);

        List<Byte> list2 = DataElementHeader.convertTypeMultipleBytes(10);
        assertThat(list2.size()).isEqualTo(1);
        assertThat(list2.get(0)).isEqualTo((byte) 0b00001010);

        List<Byte> list3 = DataElementHeader.convertTypeMultipleBytes(5242398);
        assertThat(list3.size()).isEqualTo(4);
        assertThat(list3.get(0)).isEqualTo((byte) 0b10000010);
        assertThat(list3.get(1)).isEqualTo((byte) 0b10111111);
        assertThat(list3.get(2)).isEqualTo((byte) 0b11111100);
        assertThat(list3.get(3)).isEqualTo((byte) 0b00011110);
    }

    @Test
    public void test_getTypeMultipleBytes() {
        byte[] inputBytes = new byte[]{(byte) 0b11011000, (byte) 0b10000000, (byte) 0b00001001};
        // 0b101100000000000001001
        assertThat(DataElementHeader.getTypeMultipleBytes(inputBytes)).isEqualTo(1441801);

        byte[] inputBytes2 = new byte[]{(byte) 0b00010010};
        assertThat(DataElementHeader.getTypeMultipleBytes(inputBytes2)).isEqualTo(18);

        byte[] inputBytes3 = new byte[]{(byte) 0b10000001, (byte) 0b00000000};
        assertThat(DataElementHeader.getTypeMultipleBytes(inputBytes3)).isEqualTo(128);
    }
}
