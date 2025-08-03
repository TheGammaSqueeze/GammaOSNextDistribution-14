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

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.car.cts.utils.VehiclePropertyUtils;
import android.car.hardware.property.CruiseControlType;

import org.junit.Test;

import java.util.List;

public class CruiseControlTypeTest {

    @Test
    public void testToString() {
        assertThat(CruiseControlType.toString(CruiseControlType.OTHER))
                .isEqualTo("OTHER");
        assertThat(CruiseControlType.toString(CruiseControlType.STANDARD))
                .isEqualTo("STANDARD");
        assertThat(CruiseControlType.toString(CruiseControlType.ADAPTIVE))
                .isEqualTo("ADAPTIVE");
        assertThat(CruiseControlType.toString(CruiseControlType.PREDICTIVE))
                .isEqualTo("PREDICTIVE");
        assertThat(CruiseControlType.toString(4))
                .isEqualTo("0x4");
        assertThat(CruiseControlType.toString(12))
                .isEqualTo("0xc");
    }

    @Test
    public void testAllCruiseControlTypesAreMappedInToString() {
        List<Integer> cruiseControlTypes = VehiclePropertyUtils.getIntegersFromDataEnums(
                CruiseControlType.class);
        for (Integer cruiseControlType : cruiseControlTypes) {
            String cruiseControlTypeString =
                    CruiseControlType.toString(cruiseControlType);
            assertWithMessage("string for mode %s must not start with 0x",
                    cruiseControlTypeString).that(
                    cruiseControlTypeString.startsWith("0x")).isFalse();
        }
    }
}

