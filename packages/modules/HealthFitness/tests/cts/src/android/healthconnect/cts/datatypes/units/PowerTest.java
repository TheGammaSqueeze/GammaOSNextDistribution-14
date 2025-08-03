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

package android.healthconnect.cts.datatypes.units;

import static com.google.common.truth.Truth.assertThat;

import android.health.connect.datatypes.units.Power;
import android.platform.test.annotations.AppModeFull;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@AppModeFull(reason = "HealthConnectManager is not accessible to instant apps")
@RunWith(AndroidJUnit4.class)
public class PowerTest {
    @Test
    public void testCreate() {
        assertThat(Power.fromWatts(10.0)).isInstanceOf(Power.class);
        assertThat(Power.fromWatts(10.0).getInWatts()).isEqualTo(10.0);
    }

    @Test
    public void testEquals() {
        Power Power1 = Power.fromWatts(10.0);
        Power Power2 = Power.fromWatts(10.0);
        Power Power3 = Power.fromWatts(20.0);

        assertThat(Power1.equals(Power2)).isEqualTo(true);
        assertThat(Power1.equals(Power3)).isEqualTo(false);
    }

    @Test
    public void testCompare() {
        Power Power1 = Power.fromWatts(10.0);
        Power Power2 = Power.fromWatts(10.0);
        Power Power3 = Power.fromWatts(20.0);

        assertThat(Power1.compareTo(Power2)).isEqualTo(0);
        assertThat(Power1.compareTo(Power3)).isEqualTo(-1);
        assertThat(Power3.compareTo(Power1)).isEqualTo(1);
    }
}
