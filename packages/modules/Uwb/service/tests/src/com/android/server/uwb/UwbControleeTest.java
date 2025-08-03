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

package com.android.server.uwb;

import static android.uwb.RangingMeasurement.RANGING_STATUS_SUCCESS;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.uwb.AngleMeasurement;
import android.uwb.AngleOfArrivalMeasurement;
import android.uwb.DistanceMeasurement;
import android.uwb.RangingMeasurement;
import android.uwb.UwbAddress;

import com.android.server.uwb.correction.TestHelpers;
import com.android.server.uwb.correction.UwbFilterEngine;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public class UwbControleeTest {
    public static final UwbAddress UWB_ADDRESS = UwbAddress.fromBytes(new byte[] {1, 2});
    UwbControlee mControlee;
    @Mock
    UwbInjector mUwbInjector;

    @Before
    public void setUp() {
        UwbFilterEngine.Builder builder = new UwbFilterEngine.Builder();
        UwbFilterEngine engine = builder.build();
        mUwbInjector = mock(UwbInjector.class);
        when(mUwbInjector.getElapsedSinceBootMillis()).thenReturn(10L);
        mControlee = new UwbControlee(
                UWB_ADDRESS,
                engine,
                mUwbInjector);
    }

    @After
    public void shutdown() throws Exception {
        mControlee.close();
    }

    @Test
    public void testGetUwbAddress() {
        assertThat(mControlee.getUwbAddress()).isEqualTo(UWB_ADDRESS);
    }

    @Test
    public void testFilterMeasurement() {
        final double testRads = 0.1;
        final double testDist = 2;
        AngleMeasurement am = new AngleMeasurement(testRads, 0.0, 1.0);
        AngleOfArrivalMeasurement aoam = new AngleOfArrivalMeasurement.Builder(am).build();
        DistanceMeasurement dm = new DistanceMeasurement.Builder()
                .setMeters(testDist)
                .setErrorMeters(0.0)
                .setConfidenceLevel(1.0)
                .build();

        RangingMeasurement.Builder rm = new RangingMeasurement.Builder()
                .setDistanceMeasurement(dm)
                .setAngleOfArrivalMeasurement(aoam)
                .setStatus(RANGING_STATUS_SUCCESS)
                .setRemoteDeviceAddress(UWB_ADDRESS)
                .setElapsedRealtimeNanos(100);

        // Filtering a single measurement value should just yield that same value.
        mControlee.filterMeasurement(rm);

        RangingMeasurement newMeasure = rm.build();
        TestHelpers.assertClose(newMeasure.getAngleOfArrivalMeasurement().getAzimuth()
                .getRadians(), testRads);
    }

    @Test
    public void testNonAoaMeasurement() {
        final double testDist = 2;
        DistanceMeasurement dm = new DistanceMeasurement.Builder()
                .setMeters(testDist)
                .setErrorMeters(0.0)
                .setConfidenceLevel(1.0)
                .build();

        RangingMeasurement.Builder rm = new RangingMeasurement.Builder()
                .setDistanceMeasurement(dm)
                .setStatus(RANGING_STATUS_SUCCESS)
                .setRemoteDeviceAddress(UWB_ADDRESS)
                .setElapsedRealtimeNanos(100);

        // Filtering a single measurement value should just yield that same value.
        mControlee.filterMeasurement(rm);

        RangingMeasurement newMeasure = rm.build();
        assertThat(newMeasure.getAngleOfArrivalMeasurement()).isNull();
    }
}
