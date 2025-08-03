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

package android.federatedcompute.common;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.os.Parcel;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link TrainingInterval} */
@RunWith(AndroidJUnit4.class)
public class TrainingIntervalTest {
    @Test
    public void testRecurrentTask() {
        long minimumIntervalMillis = 10000000L;
        TrainingInterval interval =
                createInterval(TrainingInterval.SCHEDULING_MODE_RECURRENT, minimumIntervalMillis);
        assertThat(interval.getSchedulingMode())
                .isEqualTo(TrainingInterval.SCHEDULING_MODE_RECURRENT);
        assertThat(interval.getMinimumIntervalMillis()).isEqualTo(minimumIntervalMillis);
    }

    @Test
    public void testOneOffTask() {
        TrainingInterval interval = createInterval(TrainingInterval.SCHEDULING_MODE_ONE_TIME, 0);
        assertThat(interval.getSchedulingMode())
                .isEqualTo(TrainingInterval.SCHEDULING_MODE_ONE_TIME);
    }

    @Test
    public void testInvalidRecurrentTask() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new TrainingInterval.Builder()
                                .setSchedulingMode(TrainingInterval.SCHEDULING_MODE_RECURRENT)
                                .setMinimumIntervalMillis(0)
                                .build());
    }

    @Test
    public void testEquals() {
        TrainingInterval interval =
                createInterval(TrainingInterval.SCHEDULING_MODE_RECURRENT, 10000);
        TrainingInterval copy = interval;
        assertThat(interval.equals(copy)).isTrue();
        assertThat(interval.equals(new Object())).isFalse();
        TrainingInterval identical =
                createInterval(TrainingInterval.SCHEDULING_MODE_RECURRENT, 10000);
        assertThat(interval.equals(identical)).isTrue();
        assertThat(interval.hashCode()).isEqualTo(identical.hashCode());
        TrainingInterval differentMode =
                createInterval(TrainingInterval.SCHEDULING_MODE_ONE_TIME, 10000);
        assertThat(interval.equals(differentMode)).isFalse();
        TrainingInterval differentInterval =
                createInterval(TrainingInterval.SCHEDULING_MODE_RECURRENT, 20000);
        assertThat(interval.equals(differentInterval)).isFalse();
    }

    @Test
    public void testParcelValidInterval() {
        TrainingInterval interval =
                createInterval(TrainingInterval.SCHEDULING_MODE_RECURRENT, 10000);

        Parcel p = Parcel.obtain();
        interval.writeToParcel(p, 0);
        p.setDataPosition(0);
        TrainingInterval fromParcel = TrainingInterval.CREATOR.createFromParcel(p);

        assertThat(interval).isEqualTo(fromParcel);
    }

    private static TrainingInterval createInterval(int mode, long minimumIntervalMillis) {
        return new TrainingInterval.Builder()
                .setSchedulingMode(mode)
                .setMinimumIntervalMillis(minimumIntervalMillis)
                .build();
    }
}
