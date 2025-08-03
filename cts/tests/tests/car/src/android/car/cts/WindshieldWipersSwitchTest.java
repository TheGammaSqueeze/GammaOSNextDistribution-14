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
import android.car.hardware.property.WindshieldWipersSwitch;

import org.junit.Test;

import java.util.List;

public class WindshieldWipersSwitchTest {

    @Test
    public void testToString() {
        assertThat(WindshieldWipersSwitch.toString(WindshieldWipersSwitch.OTHER))
                .isEqualTo("OTHER");
        assertThat(WindshieldWipersSwitch.toString(WindshieldWipersSwitch.OFF)).isEqualTo("OFF");
        assertThat(WindshieldWipersSwitch.toString(WindshieldWipersSwitch.MIST)).isEqualTo("MIST");
        assertThat(WindshieldWipersSwitch.toString(WindshieldWipersSwitch.INTERMITTENT_LEVEL_1))
                .isEqualTo("INTERMITTENT_LEVEL_1");
        assertThat(WindshieldWipersSwitch.toString(WindshieldWipersSwitch.INTERMITTENT_LEVEL_2))
                .isEqualTo("INTERMITTENT_LEVEL_2");
        assertThat(WindshieldWipersSwitch.toString(WindshieldWipersSwitch.INTERMITTENT_LEVEL_3))
                .isEqualTo("INTERMITTENT_LEVEL_3");
        assertThat(WindshieldWipersSwitch.toString(WindshieldWipersSwitch.INTERMITTENT_LEVEL_4))
                .isEqualTo("INTERMITTENT_LEVEL_4");
        assertThat(WindshieldWipersSwitch.toString(WindshieldWipersSwitch.INTERMITTENT_LEVEL_5))
                .isEqualTo("INTERMITTENT_LEVEL_5");
        assertThat(WindshieldWipersSwitch.toString(WindshieldWipersSwitch.CONTINUOUS_LEVEL_1))
                .isEqualTo("CONTINUOUS_LEVEL_1");
        assertThat(WindshieldWipersSwitch.toString(WindshieldWipersSwitch.CONTINUOUS_LEVEL_2))
                .isEqualTo("CONTINUOUS_LEVEL_2");
        assertThat(WindshieldWipersSwitch.toString(WindshieldWipersSwitch.CONTINUOUS_LEVEL_3))
                .isEqualTo("CONTINUOUS_LEVEL_3");
        assertThat(WindshieldWipersSwitch.toString(WindshieldWipersSwitch.CONTINUOUS_LEVEL_4))
                .isEqualTo("CONTINUOUS_LEVEL_4");
        assertThat(WindshieldWipersSwitch.toString(WindshieldWipersSwitch.CONTINUOUS_LEVEL_5))
                .isEqualTo("CONTINUOUS_LEVEL_5");
        assertThat(WindshieldWipersSwitch.toString(WindshieldWipersSwitch.AUTO)).isEqualTo("AUTO");
        assertThat(WindshieldWipersSwitch.toString(WindshieldWipersSwitch.SERVICE))
                .isEqualTo("SERVICE");
        assertThat(WindshieldWipersSwitch.toString(15)).isEqualTo("0xf");
        assertThat(WindshieldWipersSwitch.toString(16)).isEqualTo("0x10");
    }

    @Test
    public void testAllWindshieldWipersSwitchesAreMappedInToString() {
        List<Integer> windshieldWipersSwitches =
                VehiclePropertyUtils.getIntegersFromDataEnums(WindshieldWipersSwitch.class);
        for (Integer windshieldWipersSwitch : windshieldWipersSwitches) {
            String windshieldWipersSwitchString = WindshieldWipersSwitch.toString(
                    windshieldWipersSwitch);
            assertWithMessage("%s starts with 0x", windshieldWipersSwitchString).that(
                    windshieldWipersSwitchString.startsWith("0x")).isFalse();
        }
    }
}
