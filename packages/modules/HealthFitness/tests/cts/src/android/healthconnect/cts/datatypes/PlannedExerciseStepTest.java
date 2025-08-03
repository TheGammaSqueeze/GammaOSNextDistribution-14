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
import android.health.connect.datatypes.ExercisePerformanceGoal;
import android.health.connect.datatypes.ExerciseSessionType;
import android.health.connect.datatypes.PlannedExerciseStep;
import android.health.connect.datatypes.units.Length;
import android.health.connect.datatypes.units.Power;
import android.healthconnect.cts.utils.AssumptionCheckerRule;
import android.healthconnect.cts.utils.TestUtils;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(AndroidJUnit4.class)
public class PlannedExerciseStepTest {

    @Rule
    public AssumptionCheckerRule mSupportedHardwareRule =
            new AssumptionCheckerRule(
                    TestUtils::isHardwareSupported, "Tests should run on supported hardware only.");

    @Test
    public void hashCodeAndEquals_sensitiveToAllFields() {
        // Go through each field, first check hashCode/equals the same when field the same. Then
        // change field value for
        // one of the instances and check that hashCode/equals now differ.
        PlannedExerciseStep.Builder builder =
                new PlannedExerciseStep.Builder(
                        ExerciseSessionType.EXERCISE_SESSION_TYPE_BOXING,
                        PlannedExerciseStep.EXERCISE_CATEGORY_ACTIVE,
                        new ExerciseCompletionGoal.DistanceGoal(Length.fromMeters(100)));

        assertThat(
                        builder.setExerciseType(ExerciseSessionType.EXERCISE_SESSION_TYPE_BOXING)
                                .build())
                .isNotEqualTo(
                        builder.setExerciseType(ExerciseSessionType.EXERCISE_SESSION_TYPE_BASEBALL)
                                .build());
        assertThat(
                        builder.setExerciseType(ExerciseSessionType.EXERCISE_SESSION_TYPE_BOXING)
                                .build()
                                .hashCode())
                .isNotEqualTo(
                        builder.setExerciseType(ExerciseSessionType.EXERCISE_SESSION_TYPE_BASEBALL)
                                .build()
                                .hashCode());
        assertThat(
                        builder.setExerciseType(ExerciseSessionType.EXERCISE_SESSION_TYPE_BOXING)
                                .build())
                .isEqualTo(
                        builder.setExerciseType(ExerciseSessionType.EXERCISE_SESSION_TYPE_BOXING)
                                .build());
        assertThat(
                        builder.setExerciseType(ExerciseSessionType.EXERCISE_SESSION_TYPE_BOXING)
                                .build()
                                .hashCode())
                .isEqualTo(
                        builder.setExerciseType(ExerciseSessionType.EXERCISE_SESSION_TYPE_BOXING)
                                .build()
                                .hashCode());

        assertThat(
                        builder.setExerciseCategory(PlannedExerciseStep.EXERCISE_CATEGORY_ACTIVE)
                                .build())
                .isNotEqualTo(
                        builder.setExerciseCategory(PlannedExerciseStep.EXERCISE_CATEGORY_COOLDOWN)
                                .build());
        assertThat(
                        builder.setExerciseCategory(PlannedExerciseStep.EXERCISE_CATEGORY_ACTIVE)
                                .build()
                                .hashCode())
                .isNotEqualTo(
                        builder.setExerciseCategory(PlannedExerciseStep.EXERCISE_CATEGORY_COOLDOWN)
                                .build()
                                .hashCode());
        assertThat(
                        builder.setExerciseCategory(PlannedExerciseStep.EXERCISE_CATEGORY_ACTIVE)
                                .build())
                .isEqualTo(
                        builder.setExerciseCategory(PlannedExerciseStep.EXERCISE_CATEGORY_ACTIVE)
                                .build());
        assertThat(
                        builder.setExerciseCategory(PlannedExerciseStep.EXERCISE_CATEGORY_ACTIVE)
                                .build()
                                .hashCode())
                .isEqualTo(
                        builder.setExerciseCategory(PlannedExerciseStep.EXERCISE_CATEGORY_ACTIVE)
                                .build()
                                .hashCode());

        assertThat(builder.setDescription("X").build())
                .isNotEqualTo(builder.setDescription("Y").build());
        assertThat(builder.setDescription("X").build().hashCode())
                .isNotEqualTo(builder.setDescription("Y").build().hashCode());
        assertThat(builder.setDescription("X").build())
                .isEqualTo(builder.setDescription("X").build());
        assertThat(builder.setDescription("X").build().hashCode())
                .isEqualTo(builder.setDescription("X").build().hashCode());

        List<ExercisePerformanceGoal> performanceGoalsX =
                List.of(
                        new ExercisePerformanceGoal.PowerGoal(
                                Power.fromWatts(100), Power.fromWatts(200)));
        List<ExercisePerformanceGoal> performanceGoalsY =
                List.of(
                        new ExercisePerformanceGoal.PowerGoal(
                                Power.fromWatts(200), Power.fromWatts(300)));
        assertThat(builder.setPerformanceGoals(performanceGoalsX).build())
                .isNotEqualTo(builder.setPerformanceGoals(performanceGoalsY).build());
        assertThat(builder.setPerformanceGoals(performanceGoalsX).build().hashCode())
                .isNotEqualTo(builder.setPerformanceGoals(performanceGoalsY).build().hashCode());
        assertThat(builder.setPerformanceGoals(performanceGoalsX).build())
                .isEqualTo(builder.setPerformanceGoals(performanceGoalsX).build());
        assertThat(builder.setPerformanceGoals(performanceGoalsX).build().hashCode())
                .isEqualTo(builder.setPerformanceGoals(performanceGoalsX).build().hashCode());

        ExerciseCompletionGoal completionGoalsX =
                new ExerciseCompletionGoal.DistanceGoal(Length.fromMeters(100));
        ExerciseCompletionGoal completionGoalsY =
                new ExerciseCompletionGoal.DistanceGoal(Length.fromMeters(200));
        assertThat(builder.setCompletionGoal(completionGoalsX).build())
                .isNotEqualTo(builder.setCompletionGoal(completionGoalsY).build());
        assertThat(builder.setCompletionGoal(completionGoalsX).build().hashCode())
                .isNotEqualTo(builder.setCompletionGoal(completionGoalsY).build().hashCode());
        assertThat(builder.setCompletionGoal(completionGoalsX).build())
                .isEqualTo(builder.setCompletionGoal(completionGoalsX).build());
        assertThat(builder.setCompletionGoal(completionGoalsX).build().hashCode())
                .isEqualTo(builder.setCompletionGoal(completionGoalsX).build().hashCode());
    }

    @Test
    public void setDescriptionNull_setsDescriptionToNull() {
        PlannedExerciseStep.Builder builder =
                new PlannedExerciseStep.Builder(
                        ExerciseSessionType.EXERCISE_SESSION_TYPE_BOXING,
                        PlannedExerciseStep.EXERCISE_CATEGORY_ACTIVE,
                        new ExerciseCompletionGoal.DistanceGoal(Length.fromMeters(100)));
        builder.setDescription("Some description");

        assertThat(builder.setDescription(null).build().getDescription()).isNull();
    }

    @Test
    public void clearPerformanceGoals_removesAllExistingPerformanceGoals() {
        PlannedExerciseStep.Builder builder =
                new PlannedExerciseStep.Builder(
                        ExerciseSessionType.EXERCISE_SESSION_TYPE_BOXING,
                        PlannedExerciseStep.EXERCISE_CATEGORY_ACTIVE,
                        new ExerciseCompletionGoal.DistanceGoal(Length.fromMeters(100)));
        builder.addPerformanceGoal(
                new ExercisePerformanceGoal.PowerGoal(Power.fromWatts(200), Power.fromWatts(250)));

        assertThat(builder.clearPerformanceGoals().build().getPerformanceGoals()).isEmpty();
    }

    @Test(expected = IllegalArgumentException.class)
    public void addingMultiplePerformanceGoalsOfSameType_throwsIllegalArgumentException() {
        PlannedExerciseStep.Builder builder =
                new PlannedExerciseStep.Builder(
                        ExerciseSessionType.EXERCISE_SESSION_TYPE_BOXING,
                        PlannedExerciseStep.EXERCISE_CATEGORY_ACTIVE,
                        new ExerciseCompletionGoal.DistanceGoal(Length.fromMeters(100)));
        builder.addPerformanceGoal(
                new ExercisePerformanceGoal.PowerGoal(Power.fromWatts(200), Power.fromWatts(250)));
        builder.addPerformanceGoal(
                new ExercisePerformanceGoal.PowerGoal(Power.fromWatts(100), Power.fromWatts(200)));

        builder.build();
    }
}
