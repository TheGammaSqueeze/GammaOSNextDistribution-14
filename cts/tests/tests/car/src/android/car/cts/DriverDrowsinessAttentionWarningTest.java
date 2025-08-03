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
import android.car.hardware.property.DriverDrowsinessAttentionWarning;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import org.junit.Rule;
import org.junit.Test;

import java.util.List;

public class DriverDrowsinessAttentionWarningTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    @RequiresFlagsEnabled(FLAG_ANDROID_VIC_VEHICLE_PROPERTIES)
    public void testToString() {
        assertThat(
                DriverDrowsinessAttentionWarning.toString(DriverDrowsinessAttentionWarning.OTHER))
                .isEqualTo("OTHER");
        assertThat(
                DriverDrowsinessAttentionWarning.toString(
                        DriverDrowsinessAttentionWarning.NO_WARNING))
                .isEqualTo("NO_WARNING");
        assertThat(
                DriverDrowsinessAttentionWarning.toString(DriverDrowsinessAttentionWarning.WARNING))
                .isEqualTo("WARNING");
        assertThat(DriverDrowsinessAttentionWarning.toString(3)).isEqualTo("0x3");
        assertThat(DriverDrowsinessAttentionWarning.toString(12)).isEqualTo("0xc");
    }

    @Test
    @RequiresFlagsEnabled(FLAG_ANDROID_VIC_VEHICLE_PROPERTIES)
    public void testAllDriverDrowsinessAttentionWarningsAreMappedInToString() {
        List<Integer> driverDrowsinessAttentionWarnings =
                VehiclePropertyUtils.getIntegersFromDataEnums(
                        DriverDrowsinessAttentionWarning.class);
        for (Integer driverDrowsinessAttentionWarning : driverDrowsinessAttentionWarnings) {
            String driverDrowsinessAttentionWarningString =
                    DriverDrowsinessAttentionWarning.toString(driverDrowsinessAttentionWarning);
            assertWithMessage("string for mode %s must not start with 0x",
                    driverDrowsinessAttentionWarningString).that(
                    driverDrowsinessAttentionWarningString.startsWith("0x")).isFalse();
        }
    }
}
