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
import android.car.hardware.property.WindshieldWipersState;

import org.junit.Test;

import java.util.List;

public class WindshieldWipersStateTest {

    @Test
    public void testToString() {
        assertThat(WindshieldWipersState.toString(WindshieldWipersState.OTHER)).isEqualTo("OTHER");
        assertThat(WindshieldWipersState.toString(WindshieldWipersState.ON)).isEqualTo("ON");
        assertThat(WindshieldWipersState.toString(WindshieldWipersState.OFF)).isEqualTo("OFF");
        assertThat(WindshieldWipersState.toString(WindshieldWipersState.SERVICE))
                .isEqualTo("SERVICE");
        assertThat(WindshieldWipersState.toString(4)).isEqualTo("0x4");
        assertThat(WindshieldWipersState.toString(12)).isEqualTo("0xc");
    }

    @Test
    public void testAllWindshieldWipersStatesAreMappedInToString() {
        List<Integer> windshieldWipersStates =
                VehiclePropertyUtils.getIntegersFromDataEnums(WindshieldWipersState.class);
        for (Integer windshieldWipersState : windshieldWipersStates) {
            String windshieldWipersStateString = WindshieldWipersState.toString(
                    windshieldWipersState);
            assertWithMessage("%s starts with 0x", windshieldWipersStateString).that(
                    windshieldWipersStateString.startsWith("0x")).isFalse();
        }
    }
}
