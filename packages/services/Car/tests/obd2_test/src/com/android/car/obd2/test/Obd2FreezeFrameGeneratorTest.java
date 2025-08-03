/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.car.obd2.test;

import static android.hardware.automotive.vehicle.VehicleProperty.OBD2_FREEZE_FRAME;

import static com.android.car.obd2.test.Obd2FrameGeneratorTestConstants.EXPECTED_DISCOVERY_COMMANDS;
import static com.android.car.obd2.test.Obd2FrameGeneratorTestConstants.EXPECTED_DISCOVERY_RESPONSES;
import static com.android.car.obd2.test.Obd2FrameGeneratorTestConstants.EXPECTED_INIT_COMMANDS;
import static com.android.car.obd2.test.Obd2FrameGeneratorTestConstants.EXPECTED_INIT_RESPONSES;
import static com.android.car.obd2.test.Obd2FrameGeneratorTestConstants.OBD2_PROMPT;
import static com.android.car.obd2.test.Utils.concatIntArrays;
import static com.android.car.obd2.test.Utils.stringsToIntArray;

import static org.junit.Assert.*;

import android.hardware.automotive.vehicle.VehiclePropValue;
import android.util.JsonReader;
import android.util.JsonWriter;

import com.android.car.hal.test.DiagnosticJsonReader;
import com.android.car.obd2.Obd2Connection;
import com.android.car.obd2.Obd2FreezeFrameGenerator;

import org.junit.Test;

import java.io.StringReader;
import java.io.StringWriter;

public final class Obd2FreezeFrameGeneratorTest {

    private static final String[] EXPECTED_MODE3_COMMANDS = new String[]{"03\r"};

    private static final String[] EXPECTED_MODE3_RESPONSES =
            new String[]{
                    "0300E0:4306010002001:030043008200C12:0000000000000043010101", OBD2_PROMPT
            };

    private static final String[] EXPECTED_FRAME_COMMANDS =
            new String[]{"020C 00\r", "020D 00\r", "020C 01\r", "020D 01\r"};

    private static final String[] EXPECTED_FRAME_RESPONSES =
            new String[]{
                    "42 0C 00 12 0F",
                    OBD2_PROMPT,
                    "42 0D 00 82",
                    OBD2_PROMPT,
                    "42 0C 01 12 0F",
                    OBD2_PROMPT,
                    "42 0D 01 83",
                    OBD2_PROMPT
            };

    @Test
    public void testObd2FreezeFrameGeneration() throws Exception {
        MockObd2UnderlyingTransport transport =
                new MockObd2UnderlyingTransport(
                        concatIntArrays(
                                stringsToIntArray(EXPECTED_INIT_COMMANDS),
                                stringsToIntArray(EXPECTED_DISCOVERY_COMMANDS),
                                stringsToIntArray(EXPECTED_MODE3_COMMANDS),
                                stringsToIntArray(EXPECTED_FRAME_COMMANDS)),
                        concatIntArrays(
                                stringsToIntArray(EXPECTED_INIT_RESPONSES),
                                stringsToIntArray(EXPECTED_DISCOVERY_RESPONSES),
                                stringsToIntArray(EXPECTED_MODE3_RESPONSES),
                                stringsToIntArray(EXPECTED_FRAME_RESPONSES)));
        Obd2Connection obd2Connection = new Obd2Connection(transport);
        Obd2FreezeFrameGenerator obd2Generator = new Obd2FreezeFrameGenerator(obd2Connection);
        StringWriter stringWriter = new StringWriter(1024);
        JsonWriter jsonWriter = new JsonWriter(stringWriter);
        jsonWriter.beginArray();
        obd2Generator.generate(jsonWriter);
        jsonWriter.endArray();
        JsonReader jsonReader = new JsonReader(new StringReader(stringWriter.toString()));
        DiagnosticJsonReader diagnosticJsonReader = new DiagnosticJsonReader();
        jsonReader.beginArray();
        VehiclePropValue vehiclePropValue = diagnosticJsonReader.build(jsonReader);
        assertEquals(OBD2_FREEZE_FRAME, vehiclePropValue.prop);
        assertEquals(1155, (long) vehiclePropValue.value.int32Values[0xC]);
        assertEquals(130, (long) vehiclePropValue.value.int32Values[0xD]);
        vehiclePropValue = diagnosticJsonReader.build(jsonReader);
        assertEquals(OBD2_FREEZE_FRAME, vehiclePropValue.prop);
        assertEquals(1155, (long) vehiclePropValue.value.int32Values[0xC]);
        assertEquals(131, (long) vehiclePropValue.value.int32Values[0xD]);
    }
}
