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

import java.util.Arrays;

/** Container for UWB radar sweep data based on the Android UWB Radar UCI specification v1.0. */
public class UwbRadarSweepData {
    public long sequenceNumber;
    public long timestamp;
    public byte[] vendorSpecificData;
    public byte[] sampleData;

    public UwbRadarSweepData(
            long sequenceNumber, long timestamp, byte[] vendorSpecificData, byte[] sampleData) {
        this.sequenceNumber = sequenceNumber;
        this.timestamp = timestamp;
        this.vendorSpecificData = vendorSpecificData;
        this.sampleData = sampleData;
    }

    @Override
    public String toString() {
        return "UwbRadarSweepData { "
                + " SequenceNumber = "
                + sequenceNumber
                + ", Timestamp = "
                + timestamp
                + ", VendorSpecificData = "
                + Arrays.toString(vendorSpecificData)
                + ", SampleData = "
                + Arrays.toString(sampleData)
                + '}';
    }
}
