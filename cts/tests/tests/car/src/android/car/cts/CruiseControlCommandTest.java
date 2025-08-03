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
import android.car.hardware.property.CruiseControlCommand;

import org.junit.Test;

import java.util.List;

public class CruiseControlCommandTest {

    @Test
    public void testToString() {
        assertThat(CruiseControlCommand.toString(CruiseControlCommand.ACTIVATE))
                .isEqualTo("ACTIVATE");
        assertThat(CruiseControlCommand.toString(CruiseControlCommand.SUSPEND))
                .isEqualTo("SUSPEND");
        assertThat(CruiseControlCommand.toString(CruiseControlCommand.INCREASE_TARGET_SPEED))
                .isEqualTo("INCREASE_TARGET_SPEED");
        assertThat(CruiseControlCommand.toString(CruiseControlCommand.DECREASE_TARGET_SPEED))
                .isEqualTo("DECREASE_TARGET_SPEED");
        assertThat(CruiseControlCommand.toString(CruiseControlCommand.INCREASE_TARGET_TIME_GAP))
                .isEqualTo("INCREASE_TARGET_TIME_GAP");
        assertThat(CruiseControlCommand.toString(CruiseControlCommand.DECREASE_TARGET_TIME_GAP))
                .isEqualTo("DECREASE_TARGET_TIME_GAP");
        assertThat(CruiseControlCommand.toString(7))
                .isEqualTo("0x7");
        assertThat(CruiseControlCommand.toString(12))
                .isEqualTo("0xc");
    }

    @Test
    public void testAllCruiseControlCommandsAreMappedInToString() {
        List<Integer> cruiseControlCommands = VehiclePropertyUtils.getIntegersFromDataEnums(
                CruiseControlCommand.class);
        for (Integer cruiseControlCommand : cruiseControlCommands) {
            String cruiseControlCommandString =
                    CruiseControlCommand.toString(cruiseControlCommand);
            assertWithMessage("string for mode %s must not start with 0x",
                    cruiseControlCommandString).that(
                    cruiseControlCommandString.startsWith("0x")).isFalse();
        }
    }
}
