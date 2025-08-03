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

import android.health.connect.datatypes.units.BloodGlucose;
import android.platform.test.annotations.AppModeFull;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@AppModeFull(reason = "HealthConnectManager is not accessible to instant apps")
@RunWith(AndroidJUnit4.class)
public class BloodGlucoseTest {

    @Test
    public void testCreate() {
        assertThat(BloodGlucose.fromMillimolesPerLiter(10.0)).isInstanceOf(BloodGlucose.class);
        assertThat(BloodGlucose.fromMillimolesPerLiter(10.0).getInMillimolesPerLiter())
                .isEqualTo(10.0);
    }

    @Test
    public void testEquals() {
        BloodGlucose bloodGlucose1 = BloodGlucose.fromMillimolesPerLiter(10.0);
        BloodGlucose bloodGlucose2 = BloodGlucose.fromMillimolesPerLiter(10.0);
        BloodGlucose bloodGlucose3 = BloodGlucose.fromMillimolesPerLiter(20.0);

        assertThat(bloodGlucose1.equals(bloodGlucose2)).isEqualTo(true);
        assertThat(bloodGlucose1.equals(bloodGlucose3)).isEqualTo(false);
    }

    @Test
    public void testCompare() {
        BloodGlucose bloodGlucose1 = BloodGlucose.fromMillimolesPerLiter(10.0);
        BloodGlucose bloodGlucose2 = BloodGlucose.fromMillimolesPerLiter(10.0);
        BloodGlucose bloodGlucose3 = BloodGlucose.fromMillimolesPerLiter(20.0);

        assertThat(bloodGlucose1.compareTo(bloodGlucose2)).isEqualTo(0);
        assertThat(bloodGlucose1.compareTo(bloodGlucose3)).isEqualTo(-1);
        assertThat(bloodGlucose3.compareTo(bloodGlucose1)).isEqualTo(1);
    }
}
