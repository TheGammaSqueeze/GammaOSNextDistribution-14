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

import android.health.connect.datatypes.units.Volume;
import android.platform.test.annotations.AppModeFull;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@AppModeFull(reason = "HealthConnectManager is not accessible to instant apps")
@RunWith(AndroidJUnit4.class)
public class VolumeTest {
    @Test
    public void testCreate() {
        assertThat(Volume.fromLiters(10.0)).isInstanceOf(Volume.class);
        assertThat(Volume.fromLiters(10.0).getInLiters()).isEqualTo(10.0);
    }

    @Test
    public void testEquals() {
        Volume Volume1 = Volume.fromLiters(10.0);
        Volume Volume2 = Volume.fromLiters(10.0);
        Volume Volume3 = Volume.fromLiters(20.0);

        assertThat(Volume1.equals(Volume2)).isEqualTo(true);
        assertThat(Volume1.equals(Volume3)).isEqualTo(false);
    }

    @Test
    public void testCompare() {
        Volume Volume1 = Volume.fromLiters(10.0);
        Volume Volume2 = Volume.fromLiters(10.0);
        Volume Volume3 = Volume.fromLiters(20.0);

        assertThat(Volume1.compareTo(Volume2)).isEqualTo(0);
        assertThat(Volume1.compareTo(Volume3)).isEqualTo(-1);
        assertThat(Volume3.compareTo(Volume1)).isEqualTo(1);
    }
}
