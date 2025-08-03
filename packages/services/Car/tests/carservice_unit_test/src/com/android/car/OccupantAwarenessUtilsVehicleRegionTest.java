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
package com.android.car;

import static com.google.common.truth.Truth.assertThat;

import android.car.occupantawareness.GazeDetection;
import android.car.occupantawareness.OccupantAwarenessDetection;
import android.hardware.automotive.occupant_awareness.VehicleRegion;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;
import java.util.Collection;

@RunWith(Parameterized.class)
public final class OccupantAwarenessUtilsVehicleRegionTest {
    private final int mInputRegion;
    private final int mExpectedGazeDetectionRegion;

    public OccupantAwarenessUtilsVehicleRegionTest(int inputRegion,
            int expectedGazeDetectionRegion) {
        mInputRegion = inputRegion;
        mExpectedGazeDetectionRegion = expectedGazeDetectionRegion;
    }

    @Parameterized.Parameters
    public static Collection<?> regionToRegionMapping() {
        return Arrays.asList(new Object[][]{
            {VehicleRegion.UNKNOWN, GazeDetection.VEHICLE_REGION_UNKNOWN},
            {VehicleRegion.INSTRUMENT_CLUSTER,
                      GazeDetection.VEHICLE_REGION_CENTER_INSTRUMENT_CLUSTER},
            {VehicleRegion.REAR_VIEW_MIRROR, GazeDetection.VEHICLE_REGION_REAR_VIEW_MIRROR},
            {VehicleRegion.LEFT_SIDE_MIRROR, GazeDetection.VEHICLE_REGION_LEFT_SIDE_MIRROR},
            {VehicleRegion.RIGHT_SIDE_MIRROR, GazeDetection.VEHICLE_REGION_RIGHT_SIDE_MIRROR},
            {VehicleRegion.FORWARD_ROADWAY, GazeDetection.VEHICLE_REGION_FORWARD_ROADWAY},
            {VehicleRegion.LEFT_ROADWAY, GazeDetection.VEHICLE_REGION_LEFT_ROADWAY},
            {VehicleRegion.RIGHT_ROADWAY, GazeDetection.VEHICLE_REGION_RIGHT_ROADWAY},
            {VehicleRegion.HEAD_UNIT_DISPLAY, GazeDetection.VEHICLE_REGION_HEAD_UNIT_DISPLAY}
        });
    }

    @Test
    public void testConvertToGazeDetection_returnsVehicleRegions() {
        android.hardware.automotive.occupant_awareness.GazeDetection inputDetection =
                new android.hardware.automotive.occupant_awareness.GazeDetection();
        inputDetection.gazeTarget = mInputRegion;

        GazeDetection outputDetection =
                OccupantAwarenessUtils.convertToGazeDetection(inputDetection);

        assertThat(outputDetection.gazeTarget).isEqualTo(mExpectedGazeDetectionRegion);
    }
}
