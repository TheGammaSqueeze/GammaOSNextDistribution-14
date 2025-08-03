/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import android.health.connect.datatypes.ExerciseCompletionGoal;
import android.health.connect.datatypes.ExerciseSessionType;
import android.health.connect.datatypes.PlannedExerciseBlock;
import android.health.connect.datatypes.PlannedExerciseStep;
import android.health.connect.datatypes.units.Length;
import android.healthconnect.cts.utils.AssumptionCheckerRule;
import android.healthconnect.cts.utils.TestUtils;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class PlannedExerciseBlockTest {

    @Rule
    public AssumptionCheckerRule mSupportedHardwareRule =
            new AssumptionCheckerRule(
                    TestUtils::isHardwareSupported, "Tests should run on supported hardware only.");

    @Test
    public void hashCodeAndEquals_sensitiveToAllFields() {
        // Go through each field, first check hashCode/equals the same when field the same. Then
        // change field value for
        // one of the instances and check that hashCode/equals now differ.
        PlannedExerciseBlock.Builder builder = new PlannedExerciseBlock.Builder(1);

        assertThat(builder.setRepetitions(1).build())
                .isNotEqualTo(builder.setRepetitions(2).build());
        assertThat(builder.setRepetitions(1).build().hashCode())
                .isNotEqualTo(builder.setRepetitions(2).build().hashCode());
        assertThat(builder.setRepetitions(1).build()).isEqualTo(builder.setRepetitions(1).build());
        assertThat(builder.setRepetitions(1).build().hashCode())
                .isEqualTo(builder.setRepetitions(1).build().hashCode());

        assertThat(builder.setDescription("X").build())
                .isNotEqualTo(builder.setDescription("Y").build());
        assertThat(builder.setDescription("X").build().hashCode())
                .isNotEqualTo(builder.setDescription("Y").build().hashCode());
        assertThat(builder.setDescription("X").build())
                .isEqualTo(builder.setDescription("X").build());
        assertThat(builder.setDescription("X").build().hashCode())
                .isEqualTo(builder.setDescription("X").build().hashCode());

        PlannedExerciseStep.Builder stepBuilder =
                new PlannedExerciseStep.Builder(
                        ExerciseSessionType.EXERCISE_SESSION_TYPE_BIKING,
                        PlannedExerciseStep.EXERCISE_CATEGORY_ACTIVE,
                        new ExerciseCompletionGoal.DistanceGoal(Length.fromMeters(100)));
        PlannedExerciseStep stepX =
                stepBuilder
                        .setExerciseType(ExerciseSessionType.EXERCISE_SESSION_TYPE_BADMINTON)
                        .build();
        PlannedExerciseStep stepY =
                stepBuilder
                        .setExerciseType(ExerciseSessionType.EXERCISE_SESSION_TYPE_BOXING)
                        .build();
        assertThat(builder.setSteps(List.of(stepX)).build())
                .isNotEqualTo(builder.setSteps(List.of(stepY)).build());
        assertThat(builder.setSteps(List.of(stepX)).build().hashCode())
                .isNotEqualTo(builder.setSteps(List.of(stepY)).build().hashCode());
        assertThat(builder.setSteps(List.of(stepX)).build())
                .isEqualTo(builder.setSteps(List.of(stepX)).build());
        assertThat(builder.setSteps(List.of(stepX)).build().hashCode())
                .isEqualTo(builder.setSteps(List.of(stepX)).build().hashCode());
    }

    @Test
    public void clearSteps_removesAllExistingSteps() {
        PlannedExerciseBlock.Builder builder = new PlannedExerciseBlock.Builder(1);
        PlannedExerciseStep.Builder stepBuilder =
                new PlannedExerciseStep.Builder(
                        ExerciseSessionType.EXERCISE_SESSION_TYPE_BIKING,
                        PlannedExerciseStep.EXERCISE_CATEGORY_ACTIVE,
                        new ExerciseCompletionGoal.DistanceGoal(Length.fromMeters(100)));
        builder.setSteps(Arrays.asList(stepBuilder.build()));

        assertThat(builder.clearSteps().build().getSteps()).isEmpty();
    }

    @Test
    public void clearDescription_setsDescriptionToNull() {
        PlannedExerciseBlock.Builder builder = new PlannedExerciseBlock.Builder(1);
        builder.setDescription("Some description");

        assertThat(builder.setDescription(null).build().getDescription()).isNull();
    }
}
