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

import static android.hardware.automotive.vehicle.VehicleProperty.OBD2_LIVE_FRAME;

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
import com.android.car.obd2.Obd2LiveFrameGenerator;

import org.junit.Test;

import java.io.StringReader;
import java.io.StringWriter;

public final class Obd2LiveFrameGeneratorTest {

    private static final String[] EXPECTED_FRAME_COMMANDS = new String[]{"010C\r", "010D\r"};

    private static final String[] EXPECTED_FRAME_RESPONSES =
            new String[]{"41 0C 12 0F", OBD2_PROMPT, "41 0D 82", OBD2_PROMPT};

    @Test
    public void testObd2LiveFrameGeneration() throws Exception {
        MockObd2UnderlyingTransport transport =
                new MockObd2UnderlyingTransport(
                        concatIntArrays(
                                stringsToIntArray(EXPECTED_INIT_COMMANDS),
                                stringsToIntArray(EXPECTED_DISCOVERY_COMMANDS),
                                stringsToIntArray(EXPECTED_FRAME_COMMANDS)),
                        concatIntArrays(
                                stringsToIntArray(EXPECTED_INIT_RESPONSES),
                                stringsToIntArray(EXPECTED_DISCOVERY_RESPONSES),
                                stringsToIntArray(EXPECTED_FRAME_RESPONSES)));
        Obd2Connection obd2Connection = new Obd2Connection(transport);
        Obd2LiveFrameGenerator obd2Generator = new Obd2LiveFrameGenerator(obd2Connection);
        StringWriter stringWriter = new StringWriter(1024);
        JsonWriter jsonWriter = new JsonWriter(stringWriter);
        obd2Generator.generate(jsonWriter);
        JsonReader jsonReader = new JsonReader(new StringReader(stringWriter.toString()));
        DiagnosticJsonReader diagnosticJsonReader = new DiagnosticJsonReader();
        VehiclePropValue vehiclePropValue = diagnosticJsonReader.build(jsonReader);
        assertEquals(OBD2_LIVE_FRAME, vehiclePropValue.prop);
        assertEquals(1155, (long) vehiclePropValue.value.int32Values[0xC]);
        assertEquals(130, (long) vehiclePropValue.value.int32Values[0xD]);
    }
}
