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

import android.health.connect.datatypes.units.Length;
import android.platform.test.annotations.AppModeFull;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@AppModeFull(reason = "HealthConnectManager is not accessible to instant apps")
@RunWith(AndroidJUnit4.class)
public class LengthTest {
    @Test
    public void testCreate() {
        assertThat(Length.fromMeters(10.0)).isInstanceOf(Length.class);
        assertThat(Length.fromMeters(10.0).getInMeters()).isEqualTo(10.0);
    }

    @Test
    public void testEquals() {
        Length Length1 = Length.fromMeters(10.0);
        Length Length2 = Length.fromMeters(10.0);
        Length Length3 = Length.fromMeters(20.0);

        assertThat(Length1.equals(Length2)).isEqualTo(true);
        assertThat(Length1.equals(Length3)).isEqualTo(false);
    }

    @Test
    public void testCompare() {
        Length Length1 = Length.fromMeters(10.0);
        Length Length2 = Length.fromMeters(10.0);
        Length Length3 = Length.fromMeters(20.0);

        assertThat(Length1.compareTo(Length2)).isEqualTo(0);
        assertThat(Length1.compareTo(Length3)).isEqualTo(-1);
        assertThat(Length3.compareTo(Length1)).isEqualTo(1);
    }
}
