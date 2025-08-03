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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BinaryHttpByteArrayReaderTest {

    @Test
    public void testReadNextKnownLengthData_canReadAllComponents() {
        byte[] expectedData = new byte[] {'c'};
        BinaryHttpMessageDeserializer.BinaryHttpByteArrayReader reader =
                new BinaryHttpMessageDeserializer.BinaryHttpByteArrayReader(
                        combineSections(
                                new byte[] {0}, // Framing Indicator
                                new byte[] {0x02}, // data length
                                new byte[] {0x01}, // sub data length
                                expectedData // data
                                ));

        assertEquals(0, reader.getFramingIndicatorByte());
        assertTrue(reader.hasRemainingBytes());
        BinaryHttpMessageDeserializer.BinaryHttpByteArrayReader subReader =
                reader.readNextKnownLengthData();
        assertFalse(reader.hasRemainingBytes());
        assertArrayEquals(combineSections(new byte[] {0x01}, expectedData), subReader.getData());
        assertTrue(subReader.hasRemainingBytes());
        assertArrayEquals(expectedData, subReader.readNextKnownLengthData().getData());
        assertFalse(subReader.hasRemainingBytes());
        assertThrows(IllegalArgumentException.class, subReader::readNextKnownLengthData);
    }

    @Test
    public void testReadNextKnownLengthData_notEnoughDataToRead_throwException() {
        BinaryHttpMessageDeserializer.BinaryHttpByteArrayReader reader =
                new BinaryHttpMessageDeserializer.BinaryHttpByteArrayReader(
                        combineSections(
                                new byte[] {0}, // Framing Indicator
                                new byte[] {0x01} // data length
                                ));

        assertEquals(0, reader.getFramingIndicatorByte());
        assertTrue(reader.hasRemainingBytes());
        assertThrows(IllegalArgumentException.class, reader::readNextKnownLengthData);
    }
}
