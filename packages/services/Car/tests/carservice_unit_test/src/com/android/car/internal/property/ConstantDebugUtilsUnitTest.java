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

package com.android.car.internal.property;

import static com.google.common.truth.Truth.assertThat;

import android.car.VehicleAreaMirror;
import android.car.VehicleGear;
import android.car.VehicleSeatOccupancyState;

import com.android.car.internal.util.ConstantDebugUtils;

import org.junit.Test;

import java.util.List;

public class ConstantDebugUtilsUnitTest {
    @Test
    public void testToName() {
        assertThat(ConstantDebugUtils.toName(VehicleGear.class, VehicleGear.GEAR_DRIVE)).isEqualTo(
                "GEAR_DRIVE");
        assertThat(ConstantDebugUtils.toName(VehicleGear.class, -1)).isNull();
        assertThat(ConstantDebugUtils.toName(VehicleSeatOccupancyState.class,
                VehicleSeatOccupancyState.OCCUPIED)).isEqualTo("OCCUPIED");
    }

    @Test
    public void testToValue() {
        assertThat(ConstantDebugUtils.toValue(VehicleGear.class, "GEAR_DRIVE")).isEqualTo(
                VehicleGear.GEAR_DRIVE);
        assertThat(ConstantDebugUtils.toValue(VehicleGear.class, "saljflsadj")).isNull();
        assertThat(
                ConstantDebugUtils.toValue(VehicleSeatOccupancyState.class, "OCCUPIED")).isEqualTo(
                VehicleSeatOccupancyState.OCCUPIED);
    }

    @Test
    public void testGetValues() {
        assertThat(ConstantDebugUtils.getValues(VehicleAreaMirror.class)).containsExactlyElementsIn(
                List.of(VehicleAreaMirror.MIRROR_DRIVER_LEFT, VehicleAreaMirror.MIRROR_DRIVER_RIGHT,
                        VehicleAreaMirror.MIRROR_DRIVER_CENTER));
    }
}
