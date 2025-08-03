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

import android.health.connect.datatypes.ExercisePerformanceGoal;
import android.health.connect.datatypes.units.Mass;
import android.health.connect.datatypes.units.Power;
import android.health.connect.datatypes.units.Velocity;
import android.healthconnect.cts.utils.AssumptionCheckerRule;
import android.healthconnect.cts.utils.TestUtils;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class ExercisePerformanceGoalTest {
    @Rule
    public AssumptionCheckerRule mSupportedHardwareRule =
            new AssumptionCheckerRule(
                    TestUtils::isHardwareSupported, "Tests should run on supported hardware only.");

    @Test
    public void powerGoal() {
        assertThat(
                        new ExercisePerformanceGoal.PowerGoal(
                                Power.fromWatts(100), Power.fromWatts(100)))
                .isNotEqualTo(
                        new ExercisePerformanceGoal.PowerGoal(
                                Power.fromWatts(200), Power.fromWatts(200)));
        assertThat(
                        new ExercisePerformanceGoal.PowerGoal(
                                Power.fromWatts(100), Power.fromWatts(100)))
                .isEqualTo(
                        new ExercisePerformanceGoal.PowerGoal(
                                Power.fromWatts(100), Power.fromWatts(100)));

        assertThat(
                        new ExercisePerformanceGoal.PowerGoal(
                                        Power.fromWatts(100), Power.fromWatts(100))
                                .hashCode())
                .isNotEqualTo(
                        new ExercisePerformanceGoal.PowerGoal(
                                        Power.fromWatts(200), Power.fromWatts(200))
                                .hashCode());
        assertThat(
                        new ExercisePerformanceGoal.PowerGoal(
                                        Power.fromWatts(100), Power.fromWatts(100))
                                .hashCode())
                .isEqualTo(
                        new ExercisePerformanceGoal.PowerGoal(
                                        Power.fromWatts(100), Power.fromWatts(100))
                                .hashCode());
    }

    @Test
    public void speedGoal() {
        assertThat(
                        new ExercisePerformanceGoal.SpeedGoal(
                                Velocity.fromMetersPerSecond(10), Velocity.fromMetersPerSecond(10)))
                .isNotEqualTo(
                        new ExercisePerformanceGoal.SpeedGoal(
                                Velocity.fromMetersPerSecond(20),
                                Velocity.fromMetersPerSecond(20)));
        assertThat(
                        new ExercisePerformanceGoal.SpeedGoal(
                                Velocity.fromMetersPerSecond(10), Velocity.fromMetersPerSecond(10)))
                .isEqualTo(
                        new ExercisePerformanceGoal.SpeedGoal(
                                Velocity.fromMetersPerSecond(10),
                                Velocity.fromMetersPerSecond(10)));

        assertThat(
                        new ExercisePerformanceGoal.SpeedGoal(
                                        Velocity.fromMetersPerSecond(10),
                                        Velocity.fromMetersPerSecond(10))
                                .hashCode())
                .isNotEqualTo(
                        new ExercisePerformanceGoal.SpeedGoal(
                                        Velocity.fromMetersPerSecond(20),
                                        Velocity.fromMetersPerSecond(20))
                                .hashCode());
        assertThat(
                        new ExercisePerformanceGoal.SpeedGoal(
                                        Velocity.fromMetersPerSecond(10),
                                        Velocity.fromMetersPerSecond(10))
                                .hashCode())
                .isEqualTo(
                        new ExercisePerformanceGoal.SpeedGoal(
                                        Velocity.fromMetersPerSecond(10),
                                        Velocity.fromMetersPerSecond(10))
                                .hashCode());
    }

    @Test
    public void cadenceGoal() {
        assertThat(new ExercisePerformanceGoal.CadenceGoal(10, 10))
                .isNotEqualTo(new ExercisePerformanceGoal.CadenceGoal(20, 20));
        assertThat(new ExercisePerformanceGoal.CadenceGoal(10, 10))
                .isEqualTo(new ExercisePerformanceGoal.CadenceGoal(10, 10));

        assertThat(new ExercisePerformanceGoal.CadenceGoal(10, 10).hashCode())
                .isNotEqualTo(new ExercisePerformanceGoal.CadenceGoal(20, 20).hashCode());
        assertThat(new ExercisePerformanceGoal.CadenceGoal(10, 10).hashCode())
                .isEqualTo(new ExercisePerformanceGoal.CadenceGoal(10, 10).hashCode());
    }

    @Test
    public void heartRateGoal() {
        assertThat(new ExercisePerformanceGoal.HeartRateGoal(100, 100))
                .isNotEqualTo(new ExercisePerformanceGoal.HeartRateGoal(200, 200));
        assertThat(new ExercisePerformanceGoal.HeartRateGoal(100, 100))
                .isEqualTo(new ExercisePerformanceGoal.HeartRateGoal(100, 100));

        assertThat(new ExercisePerformanceGoal.HeartRateGoal(100, 100).hashCode())
                .isNotEqualTo(new ExercisePerformanceGoal.HeartRateGoal(200, 200).hashCode());
        assertThat(new ExercisePerformanceGoal.HeartRateGoal(100, 100).hashCode())
                .isEqualTo(new ExercisePerformanceGoal.HeartRateGoal(100, 100).hashCode());
    }

    @Test
    public void weightGoal() {
        assertThat(new ExercisePerformanceGoal.WeightGoal(Mass.fromGrams(100_000)))
                .isNotEqualTo(new ExercisePerformanceGoal.WeightGoal(Mass.fromGrams(200_000)));
        assertThat(new ExercisePerformanceGoal.WeightGoal(Mass.fromGrams(100_000)))
                .isEqualTo(new ExercisePerformanceGoal.WeightGoal(Mass.fromGrams(100_000)));

        assertThat(new ExercisePerformanceGoal.WeightGoal(Mass.fromGrams(100_000)).hashCode())
                .isNotEqualTo(
                        new ExercisePerformanceGoal.WeightGoal(Mass.fromGrams(200_000)).hashCode());
        assertThat(new ExercisePerformanceGoal.WeightGoal(Mass.fromGrams(100_000)).hashCode())
                .isEqualTo(
                        new ExercisePerformanceGoal.WeightGoal(Mass.fromGrams(100_000)).hashCode());
    }

    @Test
    public void rpeGoal() {
        assertThat(new ExercisePerformanceGoal.RateOfPerceivedExertionGoal(1))
                .isNotEqualTo(new ExercisePerformanceGoal.RateOfPerceivedExertionGoal(2));
        assertThat(new ExercisePerformanceGoal.RateOfPerceivedExertionGoal(1))
                .isEqualTo(new ExercisePerformanceGoal.RateOfPerceivedExertionGoal(1));

        assertThat(new ExercisePerformanceGoal.RateOfPerceivedExertionGoal(1).hashCode())
                .isNotEqualTo(
                        new ExercisePerformanceGoal.RateOfPerceivedExertionGoal(2).hashCode());
        assertThat(new ExercisePerformanceGoal.RateOfPerceivedExertionGoal(1).hashCode())
                .isEqualTo(new ExercisePerformanceGoal.RateOfPerceivedExertionGoal(1).hashCode());
    }
}
