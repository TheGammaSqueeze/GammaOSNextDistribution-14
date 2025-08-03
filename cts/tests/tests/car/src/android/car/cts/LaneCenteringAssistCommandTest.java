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
import android.car.hardware.property.LaneCenteringAssistCommand;

import org.junit.Test;

import java.util.List;

public class LaneCenteringAssistCommandTest {

    @Test
    public void testToString() {
        assertThat(LaneCenteringAssistCommand.toString(LaneCenteringAssistCommand.ACTIVATE))
                .isEqualTo("ACTIVATE");
        assertThat(LaneCenteringAssistCommand.toString(LaneCenteringAssistCommand.DEACTIVATE))
                .isEqualTo("DEACTIVATE");
        assertThat(LaneCenteringAssistCommand.toString(3)).isEqualTo("0x3");
        assertThat(LaneCenteringAssistCommand.toString(12)).isEqualTo("0xc");
    }

    @Test
    public void testAllLaneCenteringAssistCommandsAreMappedInToString() {
        List<Integer> laneCenteringAssistCommands = VehiclePropertyUtils.getIntegersFromDataEnums(
                LaneCenteringAssistCommand.class);
        for (Integer laneCenteringAssistCommand : laneCenteringAssistCommands) {
            String laneCenteringAssistCommandString = LaneCenteringAssistCommand.toString(
                    laneCenteringAssistCommand);
            assertWithMessage("%s starts with 0x", laneCenteringAssistCommandString).that(
                    laneCenteringAssistCommandString.startsWith("0x")).isFalse();
        }
    }
}
