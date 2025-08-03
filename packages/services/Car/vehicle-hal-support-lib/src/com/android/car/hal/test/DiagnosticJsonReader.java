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

package com.android.car.hal.test;

import static android.hardware.automotive.vehicle.VehicleProperty.OBD2_FREEZE_FRAME;
import static android.hardware.automotive.vehicle.VehicleProperty.OBD2_LIVE_FRAME;

import android.hardware.automotive.vehicle.VehiclePropConfig;
import android.hardware.automotive.vehicle.VehiclePropValue;
import android.util.JsonReader;

import java.io.IOException;

public class DiagnosticJsonReader {
    public static final String FRAME_TYPE_LIVE = "live";
    public static final String FRAME_TYPE_FREEZE = "freeze";

    private final DiagnosticEventBuilder mLiveFrameBuilder;
    private final DiagnosticEventBuilder mFreezeFrameBuilder;

    public DiagnosticJsonReader(VehiclePropConfig liveConfig, VehiclePropConfig freezeConfig) {
        mLiveFrameBuilder =
                new DiagnosticEventBuilder(
                        OBD2_LIVE_FRAME,
                        liveConfig.configArray[0],
                        liveConfig.configArray[1]);
        mFreezeFrameBuilder =
                new DiagnosticEventBuilder(
                        OBD2_FREEZE_FRAME,
                        freezeConfig.configArray[0],
                        freezeConfig.configArray[1]);
    }

    public DiagnosticJsonReader() {
        mLiveFrameBuilder = new DiagnosticEventBuilder(OBD2_LIVE_FRAME);
        mFreezeFrameBuilder = new DiagnosticEventBuilder(OBD2_FREEZE_FRAME);
    }

    public VehiclePropValue build(JsonReader jsonReader) throws IOException {
        DiagnosticJson diagnosticJson = DiagnosticJson.build(jsonReader);
        switch (diagnosticJson.type) {
            case FRAME_TYPE_LIVE:
                return diagnosticJson.build(mLiveFrameBuilder);
            case FRAME_TYPE_FREEZE:
                return diagnosticJson.build(mFreezeFrameBuilder);
            default:
                return null;
        }
    }
}
