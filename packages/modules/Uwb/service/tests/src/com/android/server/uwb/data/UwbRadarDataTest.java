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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import android.platform.test.annotations.Presubmit;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.google.uwb.support.fira.FiraParams;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

/** Unit tests for {@link com.android.server.uwb.data.UwbRadarData}. */
@RunWith(AndroidJUnit4.class)
@SmallTest
@Presubmit
public class UwbRadarDataTest {
    private static final long TEST_SESSION_ID = 7;
    private static final int TEST_STATUS_CODE = FiraParams.STATUS_CODE_OK;
    private static final int TEST_RADAR_DATA_TYPE = RADAR_DATA_TYPE_RADAR_SWEEP_SAMPLES;
    private static final int TEST_SAMPLES_PER_SWEEP = 64;
    private static final int TEST_BITS_PER_SAMPLE = 128;
    private static final int TEST_SWEEP_OFFSET = -9;
    private static final long TEST_SEQUENCE_NUMBER = 1;
    private static final long TEST_SEQUENCE_NUMBER2 = 2;
    private static final long TEST_TIMESTAMP = 1000;
    private static final long TEST_TIMESTAMP2 = 1001;
    private static final byte[] TEST_VENDOR_SPECIFIC_DATA = new byte[] {0x01, 0x02, 0x03};
    private static final byte[] TEST_SAMPLE_DATA = new byte[] {0x03, 0x02, 0x01};

    @Test
    public void testInitializeUwbRadarData_withUwbRadarSweepData() throws Exception {
        final int noOfRadarSweepData = 2;
        final UwbRadarSweepData[] uwbRadarSweepDataArray =
                new UwbRadarSweepData[noOfRadarSweepData];
        uwbRadarSweepDataArray[0] =
                new UwbRadarSweepData(
                        TEST_SEQUENCE_NUMBER,
                        TEST_TIMESTAMP,
                        TEST_VENDOR_SPECIFIC_DATA,
                        TEST_SAMPLE_DATA);
        uwbRadarSweepDataArray[1] =
                new UwbRadarSweepData(
                        TEST_SEQUENCE_NUMBER2,
                        TEST_TIMESTAMP2,
                        TEST_VENDOR_SPECIFIC_DATA,
                        TEST_SAMPLE_DATA);
        UwbRadarData uwbRadarData =
                new UwbRadarData(
                        TEST_SESSION_ID,
                        TEST_STATUS_CODE,
                        TEST_RADAR_DATA_TYPE,
                        TEST_SAMPLES_PER_SWEEP,
                        TEST_BITS_PER_SAMPLE,
                        TEST_SWEEP_OFFSET,
                        uwbRadarSweepDataArray);

        assertEquals(uwbRadarData.sessionId, TEST_SESSION_ID);
        assertEquals(uwbRadarData.statusCode, TEST_STATUS_CODE);
        assertEquals(uwbRadarData.radarDataType, TEST_RADAR_DATA_TYPE);
        assertEquals(uwbRadarData.samplesPerSweep, TEST_SAMPLES_PER_SWEEP);
        assertEquals(uwbRadarData.bitsPerSample, TEST_BITS_PER_SAMPLE);
        assertEquals(uwbRadarData.sweepOffset, TEST_SWEEP_OFFSET);
        assertArrayEquals(uwbRadarData.radarSweepData, uwbRadarSweepDataArray);

        final String testString =
                "UwbRadarData { "
                        + " SessionId = "
                        + TEST_SESSION_ID
                        + ", StatusCode = "
                        + TEST_STATUS_CODE
                        + ", RadarDataType = "
                        + TEST_RADAR_DATA_TYPE
                        + ", SamplesPerSweep = "
                        + TEST_SAMPLES_PER_SWEEP
                        + ", BitsPerSample = "
                        + TEST_BITS_PER_SAMPLE
                        + ", SweepOffset = "
                        + TEST_SWEEP_OFFSET
                        + ", RadarSweepData = "
                        + Arrays.toString(uwbRadarSweepDataArray)
                        + '}';

        assertEquals(uwbRadarData.toString(), testString);
    }
}
