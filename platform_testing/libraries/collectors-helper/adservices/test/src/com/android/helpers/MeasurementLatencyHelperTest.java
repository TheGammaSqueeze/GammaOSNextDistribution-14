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

package com.android.helpers;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;

public class MeasurementLatencyHelperTest {
    private static final String TEST_FILTER_LABEL = "Measurement";
    private static final String LOG_LABEL_REGISTER_SOURCE_HOT_START =
            "MEASUREMENT_LATENCY_MeasurementLatencyTest#registerSource_hotStart";
    private static final String LOG_LABEL_REGISTER_SOURCE_COLD_START =
            "MEASUREMENT_LATENCY_MeasurementLatencyTest#registerSource_coldStart";

    @Mock private LatencyHelper.InputStreamFilter mInputStreamFilter;

    @Mock private Clock mClock;

    private LatencyHelper mMeasurementLatencyHelper;

    private Instant mInstant = Clock.systemUTC().instant();

    @Before
    public void setUo() {
        MockitoAnnotations.initMocks(this);
        when(mClock.getZone()).thenReturn(ZoneId.of("UTC"));
        when(mClock.instant()).thenReturn(mInstant);
        mMeasurementLatencyHelper =
                MeasurementLatencyHelper.getCollector(mInputStreamFilter, mClock);
        mMeasurementLatencyHelper.startCollecting();
    }

    @Test
    public void testInitTimeInLogcat() throws IOException {
        String logcatOutput =
                "04-01 00:06:25.394 12970 I Measurement:"
                    + " (MEASUREMENT_LATENCY_MeasurementLatencyTest#registerSource_coldStart: 65"
                    + " ms)\n"
                    + "04-01 00:06:25.444 12970 I Measurement:"
                    + " (MEASUREMENT_LATENCY_MeasurementLatencyTest#registerSource_hotStart: 17"
                    + " ms)\n";

        when(mInputStreamFilter.getStream(TEST_FILTER_LABEL, mInstant))
                .thenReturn(new ByteArrayInputStream(logcatOutput.getBytes()));
        Map<String, Long> actual = mMeasurementLatencyHelper.getMetrics();

        assertThat(actual.get(LOG_LABEL_REGISTER_SOURCE_COLD_START)).isEqualTo(65);
        assertThat(actual.get(LOG_LABEL_REGISTER_SOURCE_HOT_START)).isEqualTo(17);
    }

    @Test
    public void testWithNonMatchingInput() throws IOException {
        String logcatOutput = "Some random string";
        when(mInputStreamFilter.getStream(TEST_FILTER_LABEL, mInstant))
                .thenReturn(new ByteArrayInputStream(logcatOutput.getBytes()));
        Map<String, Long> actual = mMeasurementLatencyHelper.getMetrics();

        assertThat(actual.size()).isEqualTo(0);
    }

    @Test
    public void testWithEmptyLogcat() throws IOException {
        when(mInputStreamFilter.getStream(TEST_FILTER_LABEL, mInstant))
                .thenReturn(new ByteArrayInputStream("".getBytes()));
        Map<String, Long> actual = mMeasurementLatencyHelper.getMetrics();
        assertThat(actual.size()).isEqualTo(0);
    }

    @Test
    public void testInputStreamThrowsException() throws IOException {
        when(mInputStreamFilter.getStream(TEST_FILTER_LABEL, mInstant))
                .thenThrow(new IOException());
        Map<String, Long> actual = mMeasurementLatencyHelper.getMetrics();
        for (Long val : actual.values()) {
            assertThat(val).isEqualTo(0);
        }
    }
}
