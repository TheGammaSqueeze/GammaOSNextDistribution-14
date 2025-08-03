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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import android.platform.test.annotations.Presubmit;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

/** Unit tests for {@link com.android.server.uwb.data.UwbRadarSweepData}. */
@RunWith(AndroidJUnit4.class)
@SmallTest
@Presubmit
public class UwbRadarSweepDataTest {
    private static final long TEST_SEQUENCE_NUMBER = 1;
    private static final long TEST_TIMESTAMP = 1000;
    private static final byte[] TEST_VENDOR_SPECIFIC_DATA = new byte[] {0x01, 0x02, 0x03};
    private static final byte[] TEST_SAMPLE_DATA = new byte[] {0x03, 0x02, 0x01};

    @Test
    public void testInitializeUwbRadarSweepData() throws Exception {
        UwbRadarSweepData uwbRadarSweepData =
                new UwbRadarSweepData(
                        TEST_SEQUENCE_NUMBER,
                        TEST_TIMESTAMP,
                        TEST_VENDOR_SPECIFIC_DATA,
                        TEST_SAMPLE_DATA);

        assertEquals(uwbRadarSweepData.sequenceNumber, TEST_SEQUENCE_NUMBER);
        assertEquals(uwbRadarSweepData.timestamp, TEST_TIMESTAMP);
        assertArrayEquals(uwbRadarSweepData.vendorSpecificData, TEST_VENDOR_SPECIFIC_DATA);
        assertArrayEquals(uwbRadarSweepData.sampleData, TEST_SAMPLE_DATA);

        final String testString =
                "UwbRadarSweepData { "
                        + " SequenceNumber = "
                        + TEST_SEQUENCE_NUMBER
                        + ", Timestamp = "
                        + TEST_TIMESTAMP
                        + ", VendorSpecificData = "
                        + Arrays.toString(TEST_VENDOR_SPECIFIC_DATA)
                        + ", SampleData = "
                        + Arrays.toString(TEST_SAMPLE_DATA)
                        + '}';

        assertEquals(uwbRadarSweepData.toString(), testString);
    }
}
