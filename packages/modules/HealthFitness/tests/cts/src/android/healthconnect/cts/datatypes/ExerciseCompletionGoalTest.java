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
import android.health.connect.datatypes.units.Energy;
import android.health.connect.datatypes.units.Length;
import android.healthconnect.cts.utils.AssumptionCheckerRule;
import android.healthconnect.cts.utils.TestUtils;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;

@RunWith(AndroidJUnit4.class)
public class ExerciseCompletionGoalTest {
    @Rule
    public AssumptionCheckerRule mSupportedHardwareRule =
            new AssumptionCheckerRule(
                    TestUtils::isHardwareSupported, "Tests should run on supported hardware only.");

    @Test
    public void distanceGoal_equalsAndHashCode() {
        assertThat(new ExerciseCompletionGoal.DistanceGoal(Length.fromMeters(100)))
                .isNotEqualTo(new ExerciseCompletionGoal.DistanceGoal(Length.fromMeters(200)));
        assertThat(new ExerciseCompletionGoal.DistanceGoal(Length.fromMeters(100)))
                .isEqualTo(new ExerciseCompletionGoal.DistanceGoal(Length.fromMeters(100)));

        assertThat(new ExerciseCompletionGoal.DistanceGoal(Length.fromMeters(100)).hashCode())
                .isNotEqualTo(
                        new ExerciseCompletionGoal.DistanceGoal(Length.fromMeters(200)).hashCode());
        assertThat(new ExerciseCompletionGoal.DistanceGoal(Length.fromMeters(100)).hashCode())
                .isEqualTo(
                        new ExerciseCompletionGoal.DistanceGoal(Length.fromMeters(100)).hashCode());
    }

    @Test
    public void stepsGoal_equalsAndHashCode() {
        assertThat(new ExerciseCompletionGoal.StepsGoal(100))
                .isNotEqualTo(new ExerciseCompletionGoal.StepsGoal(200));
        assertThat(new ExerciseCompletionGoal.StepsGoal(100))
                .isEqualTo(new ExerciseCompletionGoal.StepsGoal(100));

        assertThat(new ExerciseCompletionGoal.StepsGoal(100).hashCode())
                .isNotEqualTo(new ExerciseCompletionGoal.StepsGoal(200).hashCode());
        assertThat(new ExerciseCompletionGoal.StepsGoal(100).hashCode())
                .isEqualTo(new ExerciseCompletionGoal.StepsGoal(100).hashCode());
    }

    @Test
    public void durationGoal_equalsAndHashCode() {
        assertThat(new ExerciseCompletionGoal.DurationGoal(Duration.ofHours(1)))
                .isNotEqualTo(new ExerciseCompletionGoal.DurationGoal(Duration.ofHours(2)));
        assertThat(new ExerciseCompletionGoal.DurationGoal(Duration.ofHours(1)))
                .isEqualTo(new ExerciseCompletionGoal.DurationGoal(Duration.ofHours(1)));

        assertThat(new ExerciseCompletionGoal.DurationGoal(Duration.ofHours(1)).hashCode())
                .isNotEqualTo(
                        new ExerciseCompletionGoal.DurationGoal(Duration.ofHours(2)).hashCode());
        assertThat(new ExerciseCompletionGoal.DurationGoal(Duration.ofHours(1)).hashCode())
                .isEqualTo(new ExerciseCompletionGoal.DurationGoal(Duration.ofHours(1)).hashCode());
    }

    @Test
    public void repsGoal_equalsAndHashCode() {
        assertThat(new ExerciseCompletionGoal.RepetitionsGoal(1))
                .isNotEqualTo(new ExerciseCompletionGoal.RepetitionsGoal(2));
        assertThat(new ExerciseCompletionGoal.RepetitionsGoal(1))
                .isEqualTo(new ExerciseCompletionGoal.RepetitionsGoal(1));

        assertThat(new ExerciseCompletionGoal.RepetitionsGoal(1).hashCode())
                .isNotEqualTo(new ExerciseCompletionGoal.RepetitionsGoal(2).hashCode());
        assertThat(new ExerciseCompletionGoal.RepetitionsGoal(1).hashCode())
                .isEqualTo(new ExerciseCompletionGoal.RepetitionsGoal(1).hashCode());
    }

    @Test
    public void totalCaloriesBurnedGoal_equalsAndHashCode() {
        assertThat(new ExerciseCompletionGoal.TotalCaloriesBurnedGoal(Energy.fromCalories(100)))
                .isNotEqualTo(
                        new ExerciseCompletionGoal.TotalCaloriesBurnedGoal(
                                Energy.fromCalories(200)));
        assertThat(new ExerciseCompletionGoal.TotalCaloriesBurnedGoal(Energy.fromCalories(100)))
                .isEqualTo(
                        new ExerciseCompletionGoal.TotalCaloriesBurnedGoal(
                                Energy.fromCalories(100)));

        assertThat(
                        new ExerciseCompletionGoal.TotalCaloriesBurnedGoal(Energy.fromCalories(100))
                                .hashCode())
                .isNotEqualTo(
                        new ExerciseCompletionGoal.TotalCaloriesBurnedGoal(Energy.fromCalories(200))
                                .hashCode());
        assertThat(
                        new ExerciseCompletionGoal.TotalCaloriesBurnedGoal(Energy.fromCalories(100))
                                .hashCode())
                .isEqualTo(
                        new ExerciseCompletionGoal.TotalCaloriesBurnedGoal(Energy.fromCalories(100))
                                .hashCode());
    }

    @Test
    public void activeCaloriesBurnedGoal_equalsAndHashCode() {
        assertThat(new ExerciseCompletionGoal.ActiveCaloriesBurnedGoal(Energy.fromCalories(100)))
                .isNotEqualTo(
                        new ExerciseCompletionGoal.ActiveCaloriesBurnedGoal(
                                Energy.fromCalories(200)));
        assertThat(new ExerciseCompletionGoal.ActiveCaloriesBurnedGoal(Energy.fromCalories(100)))
                .isEqualTo(
                        new ExerciseCompletionGoal.ActiveCaloriesBurnedGoal(
                                Energy.fromCalories(100)));

        assertThat(
                        new ExerciseCompletionGoal.ActiveCaloriesBurnedGoal(
                                        Energy.fromCalories(100))
                                .hashCode())
                .isNotEqualTo(
                        new ExerciseCompletionGoal.ActiveCaloriesBurnedGoal(
                                        Energy.fromCalories(200))
                                .hashCode());
        assertThat(
                        new ExerciseCompletionGoal.ActiveCaloriesBurnedGoal(
                                        Energy.fromCalories(100))
                                .hashCode())
                .isEqualTo(
                        new ExerciseCompletionGoal.ActiveCaloriesBurnedGoal(
                                        Energy.fromCalories(100))
                                .hashCode());
    }
}
