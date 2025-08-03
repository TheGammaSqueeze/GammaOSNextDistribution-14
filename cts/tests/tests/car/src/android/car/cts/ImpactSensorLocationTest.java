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

package android.car.cts;

import static android.car.feature.Flags.FLAG_ANDROID_VIC_VEHICLE_PROPERTIES;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.car.cts.utils.VehiclePropertyUtils;
import android.car.hardware.property.ImpactSensorLocation;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import org.junit.Rule;
import org.junit.Test;

import java.util.List;

public class ImpactSensorLocationTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    @RequiresFlagsEnabled(FLAG_ANDROID_VIC_VEHICLE_PROPERTIES)
    public void testToString() {
        assertThat(ImpactSensorLocation.toString(
                ImpactSensorLocation.OTHER))
                .isEqualTo("OTHER");
        assertThat(ImpactSensorLocation.toString(
                ImpactSensorLocation.FRONT))
                .isEqualTo("FRONT");
        assertThat(ImpactSensorLocation.toString(
                ImpactSensorLocation.FRONT_LEFT_DOOR_SIDE))
                .isEqualTo("FRONT_LEFT_DOOR_SIDE");
        assertThat(ImpactSensorLocation.toString(
                ImpactSensorLocation.FRONT_RIGHT_DOOR_SIDE))
                .isEqualTo("FRONT_RIGHT_DOOR_SIDE");
        assertThat(ImpactSensorLocation.toString(
                ImpactSensorLocation.REAR_LEFT_DOOR_SIDE))
                .isEqualTo("REAR_LEFT_DOOR_SIDE");
        assertThat(ImpactSensorLocation.toString(
                ImpactSensorLocation.REAR_RIGHT_DOOR_SIDE))
                .isEqualTo("REAR_RIGHT_DOOR_SIDE");
        assertThat(ImpactSensorLocation.toString(
                ImpactSensorLocation.REAR))
                .isEqualTo("REAR");
        assertThat(ImpactSensorLocation.toString(0)).isEqualTo("0x0");
        assertThat(ImpactSensorLocation.toString(12)).isEqualTo("0xc");
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ANDROID_VIC_VEHICLE_PROPERTIES)
    public void testAllImpactSensorLocationsAreMappedInToString() {
        List<Integer> impactSensorLocations =
                VehiclePropertyUtils.getIntegersFromDataEnums(ImpactSensorLocation.class);
        for (Integer impactSensorLocation : impactSensorLocations) {
            String impactSensorLocationString = ImpactSensorLocation.toString(
                    impactSensorLocation);
            assertWithMessage("%s starts with 0x", impactSensorLocationString).that(
                    impactSensorLocationString.startsWith("0x")).isFalse();
        }
    }
}
