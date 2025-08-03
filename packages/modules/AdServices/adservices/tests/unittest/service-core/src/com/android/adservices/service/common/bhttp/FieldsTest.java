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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

public class FieldsTest {

    @Test
    public void testDeserialize_normal() {
        String fieldName1 = "f1";
        String fieldName2 = "f2";
        String fieldValue1 = "v1";
        String fieldValue2 = "v2";
        Fields fields =
                Fields.builder()
                        .appendField(fieldName1, fieldValue1)
                        .appendField(fieldName2, fieldValue2)
                        .build();

        byte[] fieldsWithoutTotalLength =
                combineSections(
                        Frc9000VariableLengthIntegerUtil.toFrc9000Int(fieldName1.length()),
                        fieldName1.getBytes(),
                        Frc9000VariableLengthIntegerUtil.toFrc9000Int(fieldValue1.length()),
                        fieldValue1.getBytes(),
                        Frc9000VariableLengthIntegerUtil.toFrc9000Int(fieldName2.length()),
                        fieldName2.getBytes(),
                        Frc9000VariableLengthIntegerUtil.toFrc9000Int(fieldValue2.length()),
                        fieldValue2.getBytes());

        assertEquals(
                fields,
                BinaryHttpMessageDeserializer.deserializeKnownLengthFields(
                        new BinaryHttpMessageDeserializer.BinaryHttpByteArrayReader(
                                combineSections(
                                        new byte[1],
                                        Frc9000VariableLengthIntegerUtil.toFrc9000Int(
                                                fieldsWithoutTotalLength.length),
                                        fieldsWithoutTotalLength))));
    }

    @Test
    public void testSubReaderGoOutOfBound() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        BinaryHttpMessageDeserializer.deserializeKnownLengthFields(
                                new BinaryHttpMessageDeserializer.BinaryHttpByteArrayReader(
                                        combineSections(
                                                new byte[] {0},
                                                Frc9000VariableLengthIntegerUtil.toFrc9000Int(10),
                                                Frc9000VariableLengthIntegerUtil.toFrc9000Int(9),
                                                new byte[8]))));
    }
}
