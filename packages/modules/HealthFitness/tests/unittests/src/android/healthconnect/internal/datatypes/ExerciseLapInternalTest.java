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

package android.healthconnect.internal.datatypes;

import static com.google.common.truth.Truth.assertThat;

import android.health.connect.datatypes.ExerciseLap;
import android.health.connect.datatypes.units.Length;
import android.health.connect.internal.datatypes.ExerciseLapInternal;
import android.os.Parcel;

import org.junit.Test;

import java.time.Instant;
import java.time.Period;

public class ExerciseLapInternalTest {
    private final Instant mStartTime = Instant.now().minus(Period.ofDays(1));
    private final Instant mEndTime = Instant.now();

    @Test
    public void testExerciseLapInternal_convertToExternalAndBack_recordsAreEqual() {
        ExerciseLap externalLap =
                new ExerciseLap.Builder(mStartTime, mEndTime)
                        .setLength(Length.fromMeters(10))
                        .build();
        ExerciseLap converted = externalLap.toExerciseLapInternal().toExternalRecord();
        assertLapsAreEqual(converted, externalLap);
    }

    @Test
    public void testExerciseLapInternal_convertToExternalAndBackNoLength_recordsAreEqual() {
        ExerciseLap externalLap = new ExerciseLap.Builder(mStartTime, mEndTime).build();
        ExerciseLap converted = externalLap.toExerciseLapInternal().toExternalRecord();
        assertLapsAreEqual(converted, externalLap);
    }

    @Test
    public void testExerciseLapInternal_writeToParcelAndBack_recordsAreEqual() {
        ExerciseLapInternal lap =
                new ExerciseLapInternal()
                        .setStarTime(mStartTime.toEpochMilli())
                        .setEndTime(mEndTime.toEpochMilli())
                        .setLength(10);
        Parcel parcel = Parcel.obtain();
        lap.writeToParcel(parcel);
        parcel.setDataPosition(0);
        assertLapsAreEqual(ExerciseLapInternal.readFromParcel(parcel), lap);
        parcel.recycle();
    }

    @Test
    public void testExerciseLapInternal_writeToParcelAndBackNoLength_recordsAreEqual() {
        ExerciseLapInternal lap =
                new ExerciseLapInternal()
                        .setStarTime(mStartTime.toEpochMilli())
                        .setEndTime(mEndTime.toEpochMilli());
        Parcel parcel = Parcel.obtain();
        lap.writeToParcel(parcel);
        parcel.setDataPosition(0);
        assertLapsAreEqual(ExerciseLapInternal.readFromParcel(parcel), lap);
        parcel.recycle();
    }

    private void assertLapsAreEqual(ExerciseLapInternal restored, ExerciseLapInternal lap) {
        assertThat(restored.getLength()).isEqualTo(lap.getLength());
        assertThat(restored.getStartTime()).isEqualTo(lap.getStartTime());
        assertThat(restored.getEndTime()).isEqualTo(lap.getEndTime());
        assertThat(restored).isEqualTo(lap);
    }

    private void assertLapsAreEqual(ExerciseLap converted, ExerciseLap externalLap) {
        // Compare time in milliseconds as we store time in milliseconds in the database.
        assertThat(converted.getStartTime().toEpochMilli())
                .isEqualTo(externalLap.getStartTime().toEpochMilli());
        assertThat(converted.getEndTime().toEpochMilli())
                .isEqualTo(externalLap.getEndTime().toEpochMilli());
        assertThat(converted.getLength()).isEqualTo(externalLap.getLength());
    }
}
