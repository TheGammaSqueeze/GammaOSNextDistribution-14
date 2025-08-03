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

package android.healthconnect.cts.datatypes;

import static android.healthconnect.cts.utils.DataFactory.SESSION_END_TIME;
import static android.healthconnect.cts.utils.DataFactory.SESSION_START_TIME;
import static android.healthconnect.cts.utils.DataFactory.generateMetadata;

import static com.google.common.truth.Truth.assertThat;

import android.health.connect.datatypes.ExerciseLap;
import android.health.connect.datatypes.ExerciseSessionRecord;
import android.health.connect.datatypes.ExerciseSessionType;
import android.health.connect.datatypes.units.Length;

import org.junit.Test;

import java.time.Instant;
import java.util.List;

public class ExerciseLapTest {
    private static final Instant START_TIME = Instant.ofEpochMilli((long) 1e1);
    private static final Instant END_TIME = Instant.ofEpochMilli((long) 1e2);

    @Test
    public void testExerciseLap_buildLap_buildCorrectObject() {
        ExerciseLap lap =
                new ExerciseLap.Builder(START_TIME, END_TIME)
                        .setLength(Length.fromMeters(12))
                        .build();
        assertThat(lap.getStartTime()).isEqualTo(START_TIME);
        assertThat(lap.getEndTime()).isEqualTo(END_TIME);
        assertThat(lap.getLength()).isEqualTo(Length.fromMeters(12));
    }

    @Test
    public void testExerciseLap_buildWithoutLength_lengthIsNull() {
        ExerciseLap lap = new ExerciseLap.Builder(START_TIME, END_TIME).build();
        assertThat(lap.getLength()).isNull();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testExerciseLap_endTimeEarlierThanStartTime_throwsException() {
        new ExerciseLap.Builder(END_TIME, START_TIME).build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testExerciseLap_lengthIsNegative_throwsException() {
        new ExerciseLap.Builder(START_TIME, END_TIME).setLength(Length.fromMeters(-10)).build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testExerciseLap_lengthIsHuge_throwsException() {
        new ExerciseLap.Builder(START_TIME, END_TIME).setLength(Length.fromMeters(1e10)).build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testLaps_lapStartTimeIllegal_throwsException() {
        new ExerciseSessionRecord.Builder(
                        generateMetadata(),
                        SESSION_START_TIME,
                        SESSION_START_TIME.plusSeconds(200),
                        ExerciseSessionType.EXERCISE_SESSION_TYPE_CALISTHENICS)
                .setLaps(
                        List.of(
                                new ExerciseLap.Builder(
                                                SESSION_START_TIME.minusSeconds(1),
                                                SESSION_START_TIME.plusSeconds(100))
                                        .build()))
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testLaps_lapEndTimeIllegal_throwsException() {
        new ExerciseSessionRecord.Builder(
                        generateMetadata(),
                        SESSION_START_TIME,
                        SESSION_START_TIME.plusSeconds(200),
                        ExerciseSessionType.EXERCISE_SESSION_TYPE_CALISTHENICS)
                .setLaps(
                        List.of(
                                new ExerciseLap.Builder(
                                                SESSION_START_TIME,
                                                SESSION_START_TIME.plusSeconds(1200))
                                        .build()))
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testLaps_lapsOverlaps_throwsException() {
        new ExerciseSessionRecord.Builder(
                        generateMetadata(),
                        SESSION_START_TIME,
                        SESSION_END_TIME,
                        ExerciseSessionType.EXERCISE_SESSION_TYPE_CALISTHENICS)
                .setLaps(
                        List.of(
                                new ExerciseLap.Builder(
                                                SESSION_START_TIME,
                                                SESSION_START_TIME.plusSeconds(200))
                                        .build(),
                                new ExerciseLap.Builder(
                                                SESSION_START_TIME.plusSeconds(100),
                                                SESSION_START_TIME.plusSeconds(400))
                                        .build()))
                .build();
    }
}
