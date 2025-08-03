/*
 * Copyright (C) 2022 The Android Open Source Project
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

import android.health.connect.datatypes.ExerciseSegmentType;
import android.health.connect.datatypes.ExerciseSessionType;
import android.health.connect.datatypes.PlannedExerciseStep;
import android.health.connect.datatypes.SleepSessionRecord;
import android.health.connect.datatypes.units.Length;
import android.health.connect.datatypes.units.Power;
import android.health.connect.internal.datatypes.ExerciseCompletionGoalInternal;
import android.health.connect.internal.datatypes.ExerciseLapInternal;
import android.health.connect.internal.datatypes.ExercisePerformanceGoalInternal;
import android.health.connect.internal.datatypes.ExerciseRouteInternal;
import android.health.connect.internal.datatypes.ExerciseSegmentInternal;
import android.health.connect.internal.datatypes.ExerciseSessionRecordInternal;
import android.health.connect.internal.datatypes.PlannedExerciseBlockInternal;
import android.health.connect.internal.datatypes.PlannedExerciseSessionRecordInternal;
import android.health.connect.internal.datatypes.PlannedExerciseStepInternal;
import android.health.connect.internal.datatypes.SleepSessionRecordInternal;
import android.health.connect.internal.datatypes.SleepStageInternal;

import java.time.Instant;
import java.time.Period;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class TestUtils {

    public static long START_TIME = Instant.now().minus(Period.ofDays(1)).toEpochMilli();
    public static long END_TIME = Instant.now().toEpochMilli();

    public static ExerciseRouteInternal.LocationInternal buildInternalLocationAllFields() {
        return new ExerciseRouteInternal.LocationInternal()
                .setTime(START_TIME + 2)
                .setLatitude(60.321)
                .setLongitude(59.123)
                .setVerticalAccuracy(1.2)
                .setHorizontalAccuracy(20)
                .setAltitude(-12);
    }

    public static ExerciseRouteInternal.LocationInternal buildInternalLocation() {
        return new ExerciseRouteInternal.LocationInternal()
                .setTime(START_TIME + 1)
                .setLatitude(60.321)
                .setLongitude(59.123);
    }

    public static ExerciseRouteInternal buildExerciseRouteInternal() {
        return new ExerciseRouteInternal(
                List.of(buildInternalLocationAllFields(), buildInternalLocation()));
    }

    public static ExerciseSessionRecordInternal buildExerciseSessionInternal() {
        return (ExerciseSessionRecordInternal)
                new ExerciseSessionRecordInternal()
                        .setExerciseType(ExerciseSessionType.EXERCISE_SESSION_TYPE_OTHER_WORKOUT)
                        .setRoute(buildExerciseRouteInternal())
                        .setTitle("Morning walk")
                        .setNotes("Sunny weather")
                        .setExerciseLaps(Collections.singletonList(buildExerciseLap()))
                        .setExerciseSegments(Collections.singletonList(buildExerciseSegment()))
                        .setStartTime(START_TIME)
                        .setEndTime(END_TIME)
                        .setEndZoneOffset(1)
                        .setStartZoneOffset(1)
                        .setAppInfoId(1)
                        .setClientRecordId("client_id")
                        .setManufacturer("manufacturer")
                        .setClientRecordVersion(12)
                        .setUuid(UUID.randomUUID())
                        .setPackageName("android.healthconnect.unittests")
                        .setModel("Pixel4a");
    }
    /** Returns an internal planned exercise session instance. */
    public static PlannedExerciseSessionRecordInternal buildPlannedExerciseSessionInternal() {
        return (PlannedExerciseSessionRecordInternal)
                new PlannedExerciseSessionRecordInternal()
                        .setTitle("Sunday easy run")
                        .setNotes("Don't push yourself too hard for this one.")
                        .setExerciseType(ExerciseSegmentType.EXERCISE_SEGMENT_TYPE_RUNNING)
                        .setHasExplicitTime(true)
                        .setStartTime(START_TIME)
                        .setEndTime(START_TIME)
                        .setStartZoneOffset(1)
                        .setEndZoneOffset(1)
                        .setAppInfoId(1)
                        .setClientRecordId("client_id")
                        .setManufacturer("manufacturer")
                        .setClientRecordVersion(12)
                        .setUuid(UUID.randomUUID())
                        .setPackageName("android.healthconnect.unittests")
                        .setModel("Pixel4a");
    }

    /** Returns an internal exercise block instance. */
    public static PlannedExerciseBlockInternal buildExerciseBlockInternal() {
        PlannedExerciseBlockInternal result = new PlannedExerciseBlockInternal(1);
        result.setDescription("Warmup");
        return result;
    }

    /** Returns an internal exercise step instance. */
    public static PlannedExerciseStepInternal buildExerciseStepInternal() {
        PlannedExerciseStepInternal result =
                new PlannedExerciseStepInternal(
                        ExerciseSegmentType.EXERCISE_SEGMENT_TYPE_RUNNING,
                        PlannedExerciseStep.EXERCISE_CATEGORY_ACTIVE,
                        new ExerciseCompletionGoalInternal.DistanceGoalInternal(
                                Length.fromMeters(1_500)));
        result.setPerformanceGoals(
                Collections.singletonList(
                        new ExercisePerformanceGoalInternal.PowerGoalInternal(
                                Power.fromWatts(180), Power.fromWatts((220)))));
        return result;
    }

    public static SleepSessionRecordInternal buildSleepSessionInternal() {
        return (SleepSessionRecordInternal)
                new SleepSessionRecordInternal()
                        .setSleepStages(Collections.singletonList(buildSleepStage()))
                        .setTitle("Morning walk")
                        .setNotes("Sunny weather")
                        .setStartTime(START_TIME)
                        .setEndTime(END_TIME)
                        .setEndZoneOffset(1)
                        .setStartZoneOffset(1)
                        .setAppInfoId(1)
                        .setClientRecordId("client_id")
                        .setManufacturer("manufacturer")
                        .setClientRecordVersion(12)
                        .setUuid(UUID.randomUUID())
                        .setPackageName("android.healthconnect.unittests")
                        .setModel("Pixel4a");
    }

    public static ExerciseLapInternal buildExerciseLap() {
        return new ExerciseLapInternal().setStarTime(START_TIME).setEndTime(END_TIME).setLength(10);
    }

    public static SleepStageInternal buildSleepStage() {
        return new SleepStageInternal()
                .setStartTime(START_TIME)
                .setEndTime(END_TIME)
                .setStageType(SleepSessionRecord.StageType.STAGE_TYPE_AWAKE_OUT_OF_BED);
    }

    public static ExerciseSegmentInternal buildExerciseSegment() {
        return new ExerciseSegmentInternal()
                .setStarTime(START_TIME)
                .setEndTime(END_TIME)
                .setSegmentType(ExerciseSegmentType.EXERCISE_SEGMENT_TYPE_ARM_CURL)
                .setRepetitionsCount(10);
    }

    public static ExerciseSessionRecordInternal buildExerciseSessionInternalNoExtraFields() {
        return (ExerciseSessionRecordInternal)
                new ExerciseSessionRecordInternal()
                        .setExerciseType(ExerciseSessionType.EXERCISE_SESSION_TYPE_OTHER_WORKOUT)
                        .setStartTime((long) 1e9)
                        .setEndTime((long) 1e10)
                        .setUuid(UUID.randomUUID())
                        .setPackageName("android.healthconnect.unittests");
    }

    public static SleepSessionRecordInternal buildSleepSessionInternalNoExtraFields() {
        return (SleepSessionRecordInternal)
                new SleepSessionRecordInternal()
                        .setStartTime((long) 1e9)
                        .setEndTime((long) 1e10)
                        .setUuid(UUID.randomUUID())
                        .setPackageName("android.healthconnect.unittests");
    }

    public static void assertCharSequencesEqualToStringWithNull(String str, CharSequence sequence) {
        if (str == null) {
            assertThat(sequence).isNull();
        } else {
            assertThat(sequence).isNotNull();
            assertThat(sequence.toString()).isEqualTo(str);
        }
    }
}
