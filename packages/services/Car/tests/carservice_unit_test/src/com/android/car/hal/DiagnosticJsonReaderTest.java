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

package com.android.car.hal.test;

import static android.car.VehiclePropertyIds.OBD2_FREEZE_FRAME;
import static android.car.VehiclePropertyIds.OBD2_LIVE_FRAME;

import static com.android.car.hal.test.DiagnosticJsonReader.FRAME_TYPE_FREEZE;
import static com.android.car.hal.test.DiagnosticJsonReader.FRAME_TYPE_LIVE;
import static com.android.car.hal.test.DiagnosticJsonTestUtils.ANY_TIMESTAMP_VALUE;
import static com.android.car.hal.test.DiagnosticJsonTestUtils.buildEmptyVehiclePropertyValue;

import static com.google.common.truth.Truth.assertThat;

import android.hardware.automotive.vehicle.VehiclePropConfig;
import android.hardware.automotive.vehicle.VehiclePropValue;
import android.hardware.automotive.vehicle.VehicleProperty;
import android.util.JsonReader;
import android.util.JsonWriter;

import org.junit.Test;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;

public final class DiagnosticJsonReaderTest {

    private static final String INVALID_TYPE = "invalid_type";

    @Test
    public void testBuild_returnNullForEmptyJsonReader() throws IOException {
        assertThat(new DiagnosticJsonReader().build(
                buildEmptyFrameJsonReader(INVALID_TYPE, ANY_TIMESTAMP_VALUE))).isNull();
    }

    @Test
    public void testBuild_freezeFrame() throws IOException {
        JsonReader jsonReader = buildEmptyFrameJsonReader(FRAME_TYPE_FREEZE, ANY_TIMESTAMP_VALUE);
        DiagnosticJsonReader diagnosticJsonReader = buildDiagnosticJsonReader();

        VehiclePropValue actual = diagnosticJsonReader.build(jsonReader);

        assertThat(actual).isEqualTo(
                buildEmptyVehiclePropertyValue(OBD2_FREEZE_FRAME, ANY_TIMESTAMP_VALUE));
    }

    @Test
    public void testBuild_liveFrame() throws IOException {
        JsonReader jsonReader = buildEmptyFrameJsonReader(FRAME_TYPE_LIVE, ANY_TIMESTAMP_VALUE);
        DiagnosticJsonReader diagnosticJsonReader = buildDiagnosticJsonReader();

        VehiclePropValue actual = diagnosticJsonReader.build(jsonReader);

        assertThat(actual).isEqualTo(
                buildEmptyVehiclePropertyValue(OBD2_LIVE_FRAME, ANY_TIMESTAMP_VALUE));
    }

    private JsonReader buildEmptyFrameJsonReader(String frameType, int timestampValue)
            throws IOException {
        StringWriter stringWriter = new StringWriter(1024);
        JsonWriter jsonWriter = new JsonWriter(stringWriter);
        jsonWriter.beginObject()
                .name("type")
                .value(frameType)
                .name("timestamp")
                .value(timestampValue).endObject();
        return new JsonReader(new StringReader(stringWriter.toString()));
    }

    private DiagnosticJsonReader buildDiagnosticJsonReader() {
        VehiclePropConfig liveFrameConfig = new VehiclePropConfig();
        liveFrameConfig.configString = "";
        liveFrameConfig.prop = VehicleProperty.OBD2_LIVE_FRAME;
        liveFrameConfig.configArray = new int[]{0, 0};  // Empty config

        VehiclePropConfig freezeFrameConfig = new VehiclePropConfig();
        freezeFrameConfig.configString = "";
        freezeFrameConfig.prop = VehicleProperty.OBD2_FREEZE_FRAME;
        freezeFrameConfig.configArray = new int[]{0, 0};  // Empty config

        DiagnosticJsonReader diagnosticJsonReader = new DiagnosticJsonReader(liveFrameConfig,
                freezeFrameConfig);
        return diagnosticJsonReader;
    }
}
