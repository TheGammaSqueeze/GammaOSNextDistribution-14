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
import android.car.hardware.property.LaneKeepAssistState;

import org.junit.Test;

import java.util.List;

public class LaneKeepAssistStateTest {

    @Test
    public void testToString() {
        assertThat(LaneKeepAssistState.toString(LaneKeepAssistState.OTHER))
                .isEqualTo("OTHER");
        assertThat(LaneKeepAssistState.toString(LaneKeepAssistState.ENABLED))
                .isEqualTo("ENABLED");
        assertThat(LaneKeepAssistState.toString(LaneKeepAssistState.ACTIVATED_STEER_LEFT))
                .isEqualTo("ACTIVATED_STEER_LEFT");
        assertThat(LaneKeepAssistState.toString(LaneKeepAssistState.ACTIVATED_STEER_RIGHT))
                .isEqualTo("ACTIVATED_STEER_RIGHT");
        assertThat(LaneKeepAssistState.toString(LaneKeepAssistState.USER_OVERRIDE))
                .isEqualTo("USER_OVERRIDE");
        assertThat(LaneKeepAssistState.toString(5)).isEqualTo("0x5");
        assertThat(LaneKeepAssistState.toString(12)).isEqualTo("0xc");
    }

    @Test
    public void testAllLaneKeepAssistStatesAreMappedInToString() {
        List<Integer> laneKeepAssistStates = VehiclePropertyUtils.getIntegersFromDataEnums(
                LaneKeepAssistState.class);
        for (Integer laneKeepAssistState : laneKeepAssistStates) {
            String laneKeepAssistStateString = LaneKeepAssistState.toString(laneKeepAssistState);
            assertWithMessage("%s starts with 0x", laneKeepAssistStateString).that(
                    laneKeepAssistStateString.startsWith("0x")).isFalse();
        }
    }
}
