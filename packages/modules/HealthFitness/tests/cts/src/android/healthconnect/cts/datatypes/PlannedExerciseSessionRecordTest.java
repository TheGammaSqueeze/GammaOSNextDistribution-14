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

package android.healthconnect.cts.datatypes;

import static android.healthconnect.cts.utils.DataFactory.SESSION_END_TIME;
import static android.healthconnect.cts.utils.DataFactory.SESSION_START_TIME;

import static com.google.common.truth.Truth.assertThat;

import static java.time.Month.APRIL;
import static java.time.temporal.ChronoUnit.HOURS;

import android.health.connect.datatypes.DataOrigin;
import android.health.connect.datatypes.ExerciseSessionType;
import android.health.connect.datatypes.Metadata;
import android.health.connect.datatypes.PlannedExerciseBlock;
import android.health.connect.datatypes.PlannedExerciseSessionRecord;
import android.healthconnect.cts.utils.AssumptionCheckerRule;
import android.healthconnect.cts.utils.TestUtils;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Month;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@RunWith(AndroidJUnit4.class)
public class PlannedExerciseSessionRecordTest {

    @Rule
    public AssumptionCheckerRule mSupportedHardwareRule =
            new AssumptionCheckerRule(
                    TestUtils::isHardwareSupported, "Tests should run on supported hardware only.");

    @Test
    public void hashCodeAndEquals_sensitiveToAllFields() {
        // Go through each field, first check hashCode/equals the same when field the same. Then
        // change field value for
        // one of the instances and check that hashCode/equals now differ.
        PlannedExerciseSessionRecord.Builder builder =
                basePlannedExerciseSession(ExerciseSessionType.EXERCISE_SESSION_TYPE_BIKING);

        int exerciseX = ExerciseSessionType.EXERCISE_SESSION_TYPE_BASEBALL;
        int exerciseY = ExerciseSessionType.EXERCISE_SESSION_TYPE_BASKETBALL;
        assertThat(builder.setExerciseType(exerciseX).build())
                .isNotEqualTo(builder.setExerciseType(exerciseY).build());
        assertThat(builder.setExerciseType(exerciseX).build().hashCode())
                .isNotEqualTo(builder.setExerciseType(exerciseY).build().hashCode());
        assertThat(builder.setExerciseType(exerciseX).build())
                .isEqualTo(builder.setExerciseType(exerciseX).build());
        assertThat(builder.setExerciseType(exerciseX).build().hashCode())
                .isEqualTo(builder.setExerciseType(exerciseX).build().hashCode());

        assertThat(builder.setTitle("X").build()).isNotEqualTo(builder.setTitle("Y").build());
        assertThat(builder.setTitle("X").build().hashCode())
                .isNotEqualTo(builder.setTitle("Y").build().hashCode());
        assertThat(builder.setTitle("X").build()).isEqualTo(builder.setTitle("X").build());
        assertThat(builder.setTitle("X").build().hashCode())
                .isEqualTo(builder.setTitle("X").build().hashCode());

        assertThat(builder.setNotes("X").build()).isNotEqualTo(builder.setNotes("Y").build());
        assertThat(builder.setNotes("X").build().hashCode())
                .isNotEqualTo(builder.setNotes("Y").build().hashCode());
        assertThat(builder.setNotes("X").build()).isEqualTo(builder.setNotes("X").build());
        assertThat(builder.setNotes("X").build().hashCode())
                .isEqualTo(builder.setNotes("X").build().hashCode());

        PlannedExerciseBlock.Builder block = baseExerciseBlock();
        List<PlannedExerciseBlock> blocksX = List.of(block.setDescription("X").build());
        List<PlannedExerciseBlock> blocksY = List.of(block.setDescription("Y").build());
        assertThat(builder.setBlocks(blocksX).build())
                .isNotEqualTo(builder.setBlocks(blocksY).build());
        assertThat(builder.setBlocks(blocksX).build().hashCode())
                .isNotEqualTo(builder.setBlocks(blocksY).build().hashCode());
        assertThat(builder.setBlocks(blocksX).build())
                .isEqualTo(builder.setBlocks(blocksX).build());
        assertThat(builder.setBlocks(blocksX).build().hashCode())
                .isEqualTo(builder.setBlocks(blocksX).build().hashCode());

        LocalDate startDateX = LocalDate.of(2000, Month.JANUARY, 1);
        LocalDate startDateY = LocalDate.of(2000, Month.JANUARY, 2);

        PlannedExerciseSessionRecord.Builder builderWithStartDateX =
                new PlannedExerciseSessionRecord.Builder(
                        buildMetadata("some_client_record_id"),
                        exerciseX,
                        startDateX,
                        Duration.ofHours(1));
        PlannedExerciseSessionRecord.Builder builderWithStartDateY =
                new PlannedExerciseSessionRecord.Builder(
                        buildMetadata("some_client_record_id"),
                        exerciseX,
                        startDateY,
                        Duration.ofHours(1));
        assertThat(builderWithStartDateX.build()).isNotEqualTo(builderWithStartDateY.build());
        assertThat(builderWithStartDateX.build().hashCode())
                .isNotEqualTo(builderWithStartDateY.build().hashCode());
        assertThat(builderWithStartDateX.build()).isEqualTo(builderWithStartDateX.build());
        assertThat(builderWithStartDateX.build().hashCode())
                .isEqualTo(builderWithStartDateX.build().hashCode());
    }

    @Test
    public void builtRecord_fieldsMatchValuesSpecifiedViaBuilder() {
        PlannedExerciseSessionRecord.Builder builder =
                new PlannedExerciseSessionRecord.Builder(
                        buildMetadata("some client record ID"),
                        ExerciseSessionType.EXERCISE_SESSION_TYPE_BIKING,
                        SESSION_START_TIME,
                        SESSION_END_TIME);
        builder.setExerciseType(ExerciseSessionType.EXERCISE_SESSION_TYPE_BOXING);
        builder.setTitle("Some title");
        builder.setNotes("Some notes");
        builder.setBlocks(List.of(baseExerciseBlock().build()));

        PlannedExerciseSessionRecord builtRecord = builder.build();

        assertThat(builtRecord.getExerciseType())
                .isEqualTo(ExerciseSessionType.EXERCISE_SESSION_TYPE_BOXING);
        assertThat(builtRecord.getTitle()).isEqualTo("Some title");
        assertThat(builtRecord.getNotes()).isEqualTo("Some notes");
        assertThat(builtRecord.getStartTime()).isEqualTo(SESSION_START_TIME);
        assertThat(builtRecord.getEndTime()).isEqualTo(SESSION_END_TIME);
        assertThat(builtRecord.getBlocks()).isEqualTo(List.of(baseExerciseBlock().build()));
    }

    @Test
    public void noExplicitTimeSpecified_hasExplicitTimeIsFalse() {
        PlannedExerciseSessionRecord.Builder builder =
                new PlannedExerciseSessionRecord.Builder(
                        buildMetadata("some client record ID"),
                        ExerciseSessionType.EXERCISE_SESSION_TYPE_BIKING,
                        LocalDate.of(2007, APRIL, 5),
                        Duration.of(1, HOURS));

        assertThat(builder.build().hasExplicitTime()).isFalse();
    }

    @Test
    public void noExplicitTimeSpecified_startTimeSetToStartOfDay_endTimeDeterminedFromDuration() {
        PlannedExerciseSessionRecord.Builder builder =
                new PlannedExerciseSessionRecord.Builder(
                        buildMetadata("some client record ID"),
                        ExerciseSessionType.EXERCISE_SESSION_TYPE_BIKING,
                        LocalDate.of(2007, APRIL, 5),
                        Duration.of(1, HOURS));

        assertThat(builder.build().getStartTime())
                .isEqualTo(
                        LocalDate.of(2007, APRIL, 5)
                                .atTime(LocalTime.NOON)
                                .atZone(ZoneId.systemDefault())
                                .toInstant());
        assertThat(builder.build().getEndTime())
                .isEqualTo(
                        LocalDate.of(2007, APRIL, 5)
                                .atTime(LocalTime.NOON)
                                .atZone(ZoneId.systemDefault())
                                .toInstant()
                                .plus(Duration.of(1, HOURS)));
    }

    @Test
    public void getStartDate_returnsLocalDateFromTime() {
        PlannedExerciseSessionRecord.Builder builder =
                new PlannedExerciseSessionRecord.Builder(
                        buildMetadata("some client record ID"),
                        ExerciseSessionType.EXERCISE_SESSION_TYPE_BIKING,
                        LocalDate.of(2007, APRIL, 5),
                        Duration.of(1, HOURS));

        assertThat(builder.build().getStartDate()).isEqualTo(LocalDate.of(2007, APRIL, 5));
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidExerciseType_throwsException() {
        new PlannedExerciseSessionRecord.Builder(
                        buildMetadata("some_client_record_id"),
                        -1,
                        SESSION_START_TIME,
                        SESSION_END_TIME)
                .build();
    }

    private PlannedExerciseSessionRecord.Builder basePlannedExerciseSession(int exerciseType) {
        PlannedExerciseSessionRecord.Builder builder =
                new PlannedExerciseSessionRecord.Builder(
                        buildMetadata("some_client_record_id"),
                        exerciseType,
                        SESSION_START_TIME,
                        SESSION_END_TIME);
        builder.setNotes("Some notes");
        builder.setTitle("Some training plan");
        builder.setStartZoneOffset(ZoneOffset.UTC);
        builder.setEndZoneOffset(ZoneOffset.UTC);
        return builder;
    }

    private static Metadata buildMetadata(String clientRecordId) {
        return new Metadata.Builder()
                .setDataOrigin(
                        new DataOrigin.Builder()
                                .setPackageName("android.healthconnect.cts")
                                .build())
                .setId(UUID.randomUUID().toString())
                .setClientRecordId(clientRecordId)
                .setRecordingMethod(Metadata.RECORDING_METHOD_ACTIVELY_RECORDED)
                .build();
    }

    private static PlannedExerciseBlock.Builder baseExerciseBlock() {
        return new PlannedExerciseBlock.Builder(1);
    }
}
