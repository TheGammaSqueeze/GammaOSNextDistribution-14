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

public class SelectAdsLatencyHelperTest {
    private static final String TEST_FILTER_LABEL = "SelectAds";
    private static final String LOG_LABEL_P50_5G =
            "SELECT_ADS_LATENCY_SelectAdsLatency#selectAds_p50_5G";
    private static final String LOG_LABEL_P50_4GPLUS =
            "SELECT_ADS_LATENCY_SelectAdsLatency#selectAds_p50_4GPLUS";
    private static final String LOG_LABEL_P50_4G =
            "SELECT_ADS_LATENCY_SelectAdsLatency#selectAds_p50_4G";
    private static final String LOG_LABEL_P90_5G =
            "SELECT_ADS_LATENCY_SelectAdsLatency#selectAds_p90_5G";
    private static final String LOG_LABEL_P90_4GPLUS =
            "SELECT_ADS_LATENCY_SelectAdsLatency#selectAds_p90_4GPLUS";
    private static final String LOG_LABEL_P90_4G =
            "SELECT_ADS_LATENCY_SelectAdsLatency#selectAds_p90_4G";
    private static final String LOG_LABEL_REAL_SERVER_P50 =
            "SELECT_ADS_LATENCY_SelectAdsTestServerLatency#selectAds_oneBuyer_realServer";

    @Mock private LatencyHelper.InputStreamFilter mInputStreamFilter;

    @Mock private Clock mClock;

    private LatencyHelper mSelectAdsLatencyHelper;
    private Instant mInstant = Clock.systemUTC().instant();

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        when(mClock.getZone()).thenReturn(ZoneId.of("UTC")); // Used by DateTimeFormatter.
        when(mClock.instant()).thenReturn(mInstant);
        mSelectAdsLatencyHelper = SelectAdsLatencyHelper.getCollector(mInputStreamFilter, mClock);
        mSelectAdsLatencyHelper.startCollecting();
    }

    @Test
    public void testInitTimeInLogcat() throws IOException {
        String logcatOutput =
                "06-13 18:09:24.022 20765 D SelectAds: "
                        + "(SELECT_ADS_LATENCY_SelectAdsLatency#selectAds_p50_5G: 102 ms)\n"
                        + "06-29 02:47:32.030 31075 D SelectAds: "
                        + "(SELECT_ADS_LATENCY_SelectAdsLatency#selectAds_p50_4GPLUS: 1"
                        + " ms)\n"
                        + "06-13 18:09:24.058 20765 D SelectAds: "
                        + "(SELECT_ADS_LATENCY_SelectAdsLatency#selectAds_p50_4G: 43 "
                        + "ms)\n"
                        + "06-13 18:09:24.058 31075 D SelectAds: "
                        + "(SELECT_ADS_LATENCY_SelectAdsLatency#selectAds_p90_5G: 23 "
                        + "ms)\n"
                        + "06-13 18:09:24.129 20765 D SelectAds: "
                        + "(SELECT_ADS_LATENCY_SelectAdsLatency#selectAds_p90_4GPLUS: 45"
                        + " ms)\n"
                        + "06-13 18:09:24.130 31075 D SelectAds: "
                        + "(SELECT_ADS_LATENCY_SelectAdsLatency#selectAds_p90_4G: 1 "
                        + "ms)\n"
                        + "06-13 18:09:25.058 20765 D SelectAds: "
                        + "(SELECT_ADS_LATENCY_SelectAdsTestServerLatency"
                        + "#selectAds_oneBuyer_realServer: 117 ms)\n";

        when(mInputStreamFilter.getStream(TEST_FILTER_LABEL, mInstant))
                .thenReturn(new ByteArrayInputStream(logcatOutput.getBytes()));
        Map<String, Long> actual = mSelectAdsLatencyHelper.getMetrics();

        assertThat(actual.get(LOG_LABEL_P50_5G)).isEqualTo(102);
        assertThat(actual.get(LOG_LABEL_P50_4GPLUS)).isEqualTo(1);
        assertThat(actual.get(LOG_LABEL_P50_4G)).isEqualTo(43);
        assertThat(actual.get(LOG_LABEL_P90_5G)).isEqualTo(23);
        assertThat(actual.get(LOG_LABEL_P90_4GPLUS)).isEqualTo(45);
        assertThat(actual.get(LOG_LABEL_P90_4G)).isEqualTo(1);
        assertThat(actual.get(LOG_LABEL_REAL_SERVER_P50)).isEqualTo(117);
    }

    @Test
    public void testWithNonMatchingInput() throws IOException {
        String logcatOutput = "Some random string";
        when(mInputStreamFilter.getStream(TEST_FILTER_LABEL, mInstant))
                .thenReturn(new ByteArrayInputStream(logcatOutput.getBytes()));
        Map<String, Long> actual = mSelectAdsLatencyHelper.getMetrics();

        assertThat(actual.size()).isEqualTo(0);
    }

    @Test
    public void testWithEmptyLogcat() throws IOException {
        when(mInputStreamFilter.getStream(TEST_FILTER_LABEL, mInstant))
                .thenReturn(new ByteArrayInputStream("".getBytes()));
        Map<String, Long> actual = mSelectAdsLatencyHelper.getMetrics();
        assertThat(actual.size()).isEqualTo(0);
    }

    @Test
    public void testInputStreamThrowsException() throws IOException {
        when(mInputStreamFilter.getStream(TEST_FILTER_LABEL, mInstant))
                .thenThrow(new IOException());
        Map<String, Long> actual = mSelectAdsLatencyHelper.getMetrics();
        for (Long val : actual.values()) {
            assertThat(val).isEqualTo(0);
        }
    }
}
