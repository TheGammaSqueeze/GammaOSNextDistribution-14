/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.adservices.service.common.bhttp;

import static com.android.adservices.service.common.bhttp.BinaryHttpTestUtil.combineSections;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class Frc9000VariableLengthIntegerTest {

    @Test
    public void testFrc9000VariableLengthInteger() {
        testEquals(
                new byte[] {
                    (byte) 0xDA,
                    (byte) 0xDA,
                    (byte) 0xDA,
                    (byte) 0xDA,
                    (byte) 0xDA,
                    (byte) 0xDA,
                    (byte) 0xDA,
                    (byte) 0xDA
                },
                1935099623418551002L);
        testEquals(new byte[] {(byte) 0x94, (byte) 0x94, (byte) 0x94, (byte) 0x94}, 345281684L);
        testEquals(new byte[] {(byte) 0x58, (byte) 0x58}, 6232L);
        testEquals(new byte[] {(byte) 0x33}, 51L);
    }

    @Test
    public void testNotEnoughBytesToRead() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new BinaryHttpMessageDeserializer.BinaryHttpByteArrayReader(
                                        new byte[] {(byte) 0x00})
                                .readNextRfc9000Int());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new BinaryHttpMessageDeserializer.BinaryHttpByteArrayReader(
                                        new byte[] {(byte) 0x00, (byte) 0x60})
                                .readNextRfc9000Int());
    }

    private void testEquals(byte[] bytes, long number) {
        assertEquals(
                number,
                new BinaryHttpMessageDeserializer.BinaryHttpByteArrayReader(
                                combineSections(new byte[1], bytes))
                        .readNextRfc9000Int());
        assertArrayEquals(bytes, Frc9000VariableLengthIntegerUtil.toFrc9000Int(number));
    }
}
