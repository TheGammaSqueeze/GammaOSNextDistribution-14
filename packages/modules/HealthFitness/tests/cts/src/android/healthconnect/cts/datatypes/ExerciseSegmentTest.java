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

import android.health.connect.datatypes.ExerciseSegment;
import android.health.connect.datatypes.ExerciseSegmentType;
import android.health.connect.datatypes.ExerciseSessionRecord;
import android.health.connect.datatypes.ExerciseSessionType;

import org.junit.Test;

import java.time.Instant;
import java.util.List;

public class ExerciseSegmentTest {
    private static final Instant START_TIME = Instant.ofEpochMilli((long) 1e1);
    private static final Instant END_TIME = Instant.ofEpochMilli((long) 1e2);

    @Test
    public void testExerciseSegment_buildSegment_buildCorrectObject() {
        ExerciseSegment segment =
                new ExerciseSegment.Builder(
                                START_TIME,
                                END_TIME,
                                ExerciseSegmentType.EXERCISE_SEGMENT_TYPE_ARM_CURL)
                        .setRepetitionsCount(10)
                        .build();
        assertThat(segment.getStartTime()).isEqualTo(START_TIME);
        assertThat(segment.getEndTime()).isEqualTo(END_TIME);
        assertThat(segment.getSegmentType())
                .isEqualTo(ExerciseSegmentType.EXERCISE_SEGMENT_TYPE_ARM_CURL);
        assertThat(segment.getRepetitionsCount()).isEqualTo(10);
    }

    @Test
    public void testExerciseSegment_buildWithoutRepetitions_repetitionsIsZero() {
        ExerciseSegment segment =
                new ExerciseSegment.Builder(
                                START_TIME,
                                END_TIME,
                                ExerciseSegmentType.EXERCISE_SEGMENT_TYPE_ARM_CURL)
                        .build();
        assertThat(segment.getRepetitionsCount()).isEqualTo(0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testExerciseSegment_endTimeEarlierThanStartTime_throwsException() {
        new ExerciseSegment.Builder(
                        END_TIME, START_TIME, ExerciseSegmentType.EXERCISE_SEGMENT_TYPE_ARM_CURL)
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testExerciseSegment_repetitionsIsNegative_throwsException() {
        new ExerciseSegment.Builder(
                        START_TIME, END_TIME, ExerciseSegmentType.EXERCISE_SEGMENT_TYPE_ARM_CURL)
                .setRepetitionsCount(-1)
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testExerciseSegment_lapStartTimeIllegal_throwsException() {
        new ExerciseSessionRecord.Builder(
                        generateMetadata(),
                        SESSION_START_TIME,
                        SESSION_START_TIME.plusSeconds(200),
                        ExerciseSessionType.EXERCISE_SESSION_TYPE_BADMINTON)
                .setSegments(
                        List.of(
                                new ExerciseSegment.Builder(
                                                SESSION_START_TIME.minusSeconds(2),
                                                SESSION_START_TIME.plusSeconds(100),
                                                ExerciseSegmentType.EXERCISE_SEGMENT_TYPE_BURPEE)
                                        .build()))
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testExerciseSegment_lapEndTimeIllegal_throwsException() {
        new ExerciseSessionRecord.Builder(
                        generateMetadata(),
                        SESSION_START_TIME,
                        SESSION_START_TIME.plusSeconds(200),
                        ExerciseSessionType.EXERCISE_SESSION_TYPE_BADMINTON)
                .setSegments(
                        List.of(
                                new ExerciseSegment.Builder(
                                                SESSION_START_TIME,
                                                SESSION_START_TIME.plusSeconds(1200),
                                                ExerciseSegmentType.EXERCISE_SEGMENT_TYPE_BURPEE)
                                        .build()))
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testExerciseSegment_segmentsOverlap_throwsException() {
        new ExerciseSessionRecord.Builder(
                        generateMetadata(),
                        SESSION_START_TIME,
                        SESSION_END_TIME,
                        ExerciseSessionType.EXERCISE_SESSION_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING)
                .setSegments(
                        List.of(
                                new ExerciseSegment.Builder(
                                                SESSION_START_TIME,
                                                SESSION_START_TIME.plusSeconds(100),
                                                ExerciseSegmentType.EXERCISE_SEGMENT_TYPE_BURPEE)
                                        .build(),
                                new ExerciseSegment.Builder(
                                                SESSION_START_TIME.plusSeconds(50),
                                                SESSION_START_TIME.plusSeconds(200),
                                                ExerciseSegmentType.EXERCISE_SEGMENT_TYPE_CRUNCH)
                                        .build()))
                .build();
    }
}
