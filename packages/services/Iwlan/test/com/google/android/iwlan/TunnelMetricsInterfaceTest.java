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

package com.google.android.iwlan;

import static org.junit.Assert.assertEquals;

import android.net.InetAddresses;

import com.google.android.iwlan.TunnelMetricsInterface.*;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class TunnelMetricsInterfaceTest {
    private static final String TEST_EPDG_ADDRESS = "127.0.0.1";
    private static final String TEST_APN_NAME = "www.xyz.com";

    @Rule public final MockitoRule mockito = MockitoJUnit.rule();

    @Test
    public void testTunnelMetricsBuilder() {
        TunnelMetricsData metricsData =
                new TunnelMetricsData.Builder()
                        .setApnName(TEST_APN_NAME)
                        .setEpdgServerAddress(InetAddresses.parseNumericAddress(TEST_EPDG_ADDRESS))
                        .build();
        assertEquals(TEST_APN_NAME, metricsData.getApnName());
        assertEquals(TEST_EPDG_ADDRESS, metricsData.getEpdgServerAddress());
    }

    @Test
    public void testOnOpenedMetricsBuilder() {
        OnOpenedMetrics metricsData =
                new OnOpenedMetrics.Builder()
                        .setApnName(TEST_APN_NAME)
                        .setEpdgServerAddress(InetAddresses.parseNumericAddress(TEST_EPDG_ADDRESS))
                        .build();
        assertEquals(TEST_APN_NAME, metricsData.getApnName());
        assertEquals(TEST_EPDG_ADDRESS, metricsData.getEpdgServerAddress());
    }

    @Test
    public void testOnClosedMetricsBuilder() {
        OnClosedMetrics metricsData =
                new OnClosedMetrics.Builder()
                        .setApnName(TEST_APN_NAME)
                        .setEpdgServerAddress(InetAddresses.parseNumericAddress(TEST_EPDG_ADDRESS))
                        .build();
        assertEquals(TEST_APN_NAME, metricsData.getApnName());
        assertEquals(TEST_EPDG_ADDRESS, metricsData.getEpdgServerAddress());
    }
}
