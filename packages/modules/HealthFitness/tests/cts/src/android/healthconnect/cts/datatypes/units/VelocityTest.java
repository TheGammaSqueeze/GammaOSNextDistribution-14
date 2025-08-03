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

import android.health.connect.datatypes.units.Velocity;
import android.platform.test.annotations.AppModeFull;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@AppModeFull(reason = "HealthConnectManager is not accessible to instant apps")
@RunWith(AndroidJUnit4.class)
public class VelocityTest {
    @Test
    public void testCreate() {
        assertThat(Velocity.fromMetersPerSecond(10.0)).isInstanceOf(Velocity.class);
        assertThat(Velocity.fromMetersPerSecond(10.0).getInMetersPerSecond()).isEqualTo(10.0);
    }

    @Test
    public void testEquals() {
        Velocity Velocity1 = Velocity.fromMetersPerSecond(10.0);
        Velocity Velocity2 = Velocity.fromMetersPerSecond(10.0);
        Velocity Velocity3 = Velocity.fromMetersPerSecond(20.0);

        assertThat(Velocity1.equals(Velocity2)).isEqualTo(true);
        assertThat(Velocity1.equals(Velocity3)).isEqualTo(false);
    }

    @Test
    public void testCompare() {
        Velocity Velocity1 = Velocity.fromMetersPerSecond(10.0);
        Velocity Velocity2 = Velocity.fromMetersPerSecond(10.0);
        Velocity Velocity3 = Velocity.fromMetersPerSecond(20.0);

        assertThat(Velocity1.compareTo(Velocity2)).isEqualTo(0);
        assertThat(Velocity1.compareTo(Velocity3)).isEqualTo(-1);
        assertThat(Velocity3.compareTo(Velocity1)).isEqualTo(1);
    }
}
