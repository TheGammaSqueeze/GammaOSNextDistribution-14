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

import android.car.VehiclePropertyIds;
import android.hardware.automotive.vehicle.VehiclePropertyGroup;

import org.junit.Test;

public class VehiclePropertyIdDebugUtilsUnitTest {
    @Test
    public void testToName() {
        assertThat(VehiclePropertyIdDebugUtils.toName(VehiclePropertyIds.ABS_ACTIVE)).isEqualTo(
                "ABS_ACTIVE");
        assertThat(VehiclePropertyIdDebugUtils.toName(
                VehiclePropertyIds.VEHICLE_SPEED_DISPLAY_UNITS)).isEqualTo(
                "VEHICLE_SPEED_DISPLAY_UNITS");
        assertThat(VehiclePropertyIdDebugUtils.toName(-1)).isNull();
    }

    @Test
    public void testToId() {
        assertThat(VehiclePropertyIdDebugUtils.toId("ABS_ACTIVE")).isEqualTo(
                VehiclePropertyIds.ABS_ACTIVE);
        assertThat(VehiclePropertyIdDebugUtils.toId("VEHICLE_SPEED_DISPLAY_UNITS")).isEqualTo(
                VehiclePropertyIds.VEHICLE_SPEED_DISPLAY_UNITS);
        assertThat(VehiclePropertyIdDebugUtils.toId("saljflsadj")).isNull();
    }

    @Test
    public void testToDebugString() {
        assertThat(
                VehiclePropertyIdDebugUtils.toDebugString(VehiclePropertyIds.ABS_ACTIVE)).isEqualTo(
                "ABS_ACTIVE");
        assertThat(VehiclePropertyIdDebugUtils.toDebugString(
                VehiclePropertyIds.VEHICLE_SPEED_DISPLAY_UNITS)).isEqualTo(
                "VEHICLE_SPEED_DISPLAY_UNITS");
        assertThat(
                VehiclePropertyIdDebugUtils.toDebugString(VehiclePropertyGroup.VENDOR)).isEqualTo(
                "VENDOR_PROPERTY(0x" + Integer.toHexString(VehiclePropertyGroup.VENDOR) + ")");
        assertThat(VehiclePropertyIdDebugUtils.toDebugString(-1)).isEqualTo(
                "0x" + Integer.toHexString(-1));
    }

    @Test
    public void testIsDefined() {
        assertThat(VehiclePropertyIdDebugUtils.isDefined(VehiclePropertyIds.ABS_ACTIVE)).isTrue();
        assertThat(VehiclePropertyIdDebugUtils.isDefined(
                VehiclePropertyIds.VEHICLE_SPEED_DISPLAY_UNITS)).isTrue();
        assertThat(VehiclePropertyIdDebugUtils.isDefined(-1)).isFalse();
    }
}
