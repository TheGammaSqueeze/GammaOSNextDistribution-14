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
package com.android.server.uwb.data;

import static com.google.uwb.support.radar.RadarParams.RADAR_DATA_TYPE_RADAR_SWEEP_SAMPLES;

import java.util.Arrays;

/** Container for UWB radar data based on the Android UWB Radar UCI specification v1.0. */
public class UwbRadarData {
    public long sessionId;
    public int statusCode;
    public int radarDataType;
    public int samplesPerSweep;
    public int bitsPerSample;
    public int sweepOffset;
    public UwbRadarSweepData[] radarSweepData;

    public UwbRadarData(
            long sessionId,
            int statusCode,
            int radarDataType,
            int samplesPerSweep,
            int bitsPerSample,
            int sweepOffset,
            UwbRadarSweepData[] radarSweepData) {
        this.sessionId = sessionId;
        this.statusCode = statusCode;
        this.radarDataType = radarDataType;
        this.samplesPerSweep = samplesPerSweep;
        this.bitsPerSample = bitsPerSample;
        this.sweepOffset = sweepOffset;
        this.radarSweepData = radarSweepData;
    }

    @Override
    public String toString() {
        if (radarDataType == RADAR_DATA_TYPE_RADAR_SWEEP_SAMPLES) {
            return "UwbRadarData { "
                    + " SessionId = "
                    + sessionId
                    + ", StatusCode = "
                    + statusCode
                    + ", RadarDataType = "
                    + radarDataType
                    + ", SamplesPerSweep = "
                    + samplesPerSweep
                    + ", BitsPerSample = "
                    + bitsPerSample
                    + ", SweepOffset = "
                    + sweepOffset
                    + ", RadarSweepData = "
                    + Arrays.toString(radarSweepData)
                    + '}';
        } else {
            return "Unknown uwb radar data type";
        }
    }
}
