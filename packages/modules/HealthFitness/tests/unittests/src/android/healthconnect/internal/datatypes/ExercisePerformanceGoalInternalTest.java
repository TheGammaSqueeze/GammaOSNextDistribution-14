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

package android.healthconnect.internal.datatypes;

import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.truth.Truth.assertThat;

import android.health.connect.datatypes.units.Mass;
import android.health.connect.datatypes.units.Power;
import android.health.connect.datatypes.units.Velocity;
import android.health.connect.internal.datatypes.ExercisePerformanceGoalInternal;
import android.health.connect.internal.datatypes.ExercisePerformanceGoalInternal.CadenceGoalInternal;
import android.health.connect.internal.datatypes.ExercisePerformanceGoalInternal.HeartRateGoalInternal;
import android.health.connect.internal.datatypes.ExercisePerformanceGoalInternal.PowerGoalInternal;
import android.health.connect.internal.datatypes.ExercisePerformanceGoalInternal.SpeedGoalInternal;
import android.health.connect.internal.datatypes.ExercisePerformanceGoalInternal.WeightGoalInternal;
import android.os.Parcel;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ExercisePerformanceGoalInternalTest {
    @Test
    public void powerGoal_writeToParcelThenRestore_objectsAreIdentical() {
        PowerGoalInternal original =
                new PowerGoalInternal(Power.fromWatts(200.0), Power.fromWatts(240.0));

        Parcel parcel = writeToParcel(original);
        PowerGoalInternal restored =
                (PowerGoalInternal)
                        getOnlyElement(ExercisePerformanceGoalInternal.readFromParcel(parcel));

        assertThat(restored).isInstanceOf(PowerGoalInternal.class);
        assertThat(restored.getMinPower()).isEqualTo(original.getMinPower());
        assertThat(restored.getMaxPower()).isEqualTo(original.getMaxPower());
        parcel.recycle();
    }

    @Test
    public void powerGoal_convertToExternalAndBack_objectsAreIdentical() {
        ExercisePerformanceGoalInternal.PowerGoalInternal original =
                new ExercisePerformanceGoalInternal.PowerGoalInternal(
                        Power.fromWatts(200), Power.fromWatts(240));

        assertThat(
                        ((PowerGoalInternal)
                                        ExercisePerformanceGoalInternal.fromExternalObject(
                                                original.toExternalObject()))
                                .getMinPower())
                .isEqualTo(original.getMinPower());
        assertThat(
                        ((PowerGoalInternal)
                                        ExercisePerformanceGoalInternal.fromExternalObject(
                                                original.toExternalObject()))
                                .getMaxPower())
                .isEqualTo(original.getMaxPower());
    }

    @Test
    public void speedGoal_writeToParcelThenRestore_objectsAreIdentical() {
        SpeedGoalInternal original =
                new SpeedGoalInternal(
                        Velocity.fromMetersPerSecond(8.5), Velocity.fromMetersPerSecond(9.5));

        Parcel parcel = writeToParcel(original);
        SpeedGoalInternal restored =
                (SpeedGoalInternal)
                        getOnlyElement(ExercisePerformanceGoalInternal.readFromParcel(parcel));

        assertThat(restored).isInstanceOf(SpeedGoalInternal.class);
        assertThat(restored.getMinSpeed()).isEqualTo(original.getMinSpeed());
        assertThat(restored.getMaxSpeed()).isEqualTo(original.getMaxSpeed());
        parcel.recycle();
    }

    @Test
    public void speedGoal_convertToExternalAndBack_objectsAreIdentical() {
        SpeedGoalInternal original =
                new SpeedGoalInternal(
                        Velocity.fromMetersPerSecond(10.5), Velocity.fromMetersPerSecond(12.5));

        assertThat(
                        ((SpeedGoalInternal)
                                        ExercisePerformanceGoalInternal.fromExternalObject(
                                                original.toExternalObject()))
                                .getMinSpeed())
                .isEqualTo(original.getMinSpeed());
        assertThat(
                        ((SpeedGoalInternal)
                                        ExercisePerformanceGoalInternal.fromExternalObject(
                                                original.toExternalObject()))
                                .getMaxSpeed())
                .isEqualTo(original.getMaxSpeed());
    }

    @Test
    public void cadenceGoal_writeToParcelThenRestore_objectsAreIdentical() {
        CadenceGoalInternal original = new CadenceGoalInternal(80, 85);

        Parcel parcel = writeToParcel(original);
        CadenceGoalInternal restored =
                (CadenceGoalInternal)
                        getOnlyElement(ExercisePerformanceGoalInternal.readFromParcel(parcel));

        assertThat(restored).isInstanceOf(CadenceGoalInternal.class);
        assertThat(restored.getMinRpm()).isEqualTo(original.getMinRpm());
        assertThat(restored.getMaxRpm()).isEqualTo(original.getMaxRpm());
        parcel.recycle();
    }

    @Test
    public void cadenceGoal_convertToExternalAndBack_objectsAreIdentical() {
        ExercisePerformanceGoalInternal.CadenceGoalInternal original =
                new ExercisePerformanceGoalInternal.CadenceGoalInternal(80, 90);

        assertThat(
                        ((CadenceGoalInternal)
                                        ExercisePerformanceGoalInternal.fromExternalObject(
                                                original.toExternalObject()))
                                .getMinRpm())
                .isEqualTo(original.getMinRpm());
        assertThat(
                        ((CadenceGoalInternal)
                                        ExercisePerformanceGoalInternal.fromExternalObject(
                                                original.toExternalObject()))
                                .getMaxRpm())
                .isEqualTo(original.getMaxRpm());
    }

    @Test
    public void heartRateGoal_writeToParcelThenRestore_objectsAreIdentical() {
        HeartRateGoalInternal original = new HeartRateGoalInternal(150, 160);

        Parcel parcel = writeToParcel(original);
        HeartRateGoalInternal restored =
                (HeartRateGoalInternal)
                        getOnlyElement(ExercisePerformanceGoalInternal.readFromParcel(parcel));

        assertThat(restored).isInstanceOf(HeartRateGoalInternal.class);
        assertThat(restored.getMinBpm()).isEqualTo(original.getMinBpm());
        assertThat(restored.getMaxBpm()).isEqualTo(original.getMaxBpm());
        parcel.recycle();
    }

    @Test
    public void heartRateGoal_convertToExternalAndBack_objectsAreIdentical() {
        ExercisePerformanceGoalInternal.HeartRateGoalInternal original =
                new ExercisePerformanceGoalInternal.HeartRateGoalInternal(80, 90);

        assertThat(
                        ((HeartRateGoalInternal)
                                        ExercisePerformanceGoalInternal.fromExternalObject(
                                                original.toExternalObject()))
                                .getMinBpm())
                .isEqualTo(original.getMinBpm());
        assertThat(
                        ((HeartRateGoalInternal)
                                        ExercisePerformanceGoalInternal.fromExternalObject(
                                                original.toExternalObject()))
                                .getMaxBpm())
                .isEqualTo(original.getMaxBpm());
    }

    @Test
    public void weightGoal_writeToParcelThenRestore_objectsAreIdentical() {
        WeightGoalInternal original = new WeightGoalInternal(Mass.fromGrams(80_000));

        Parcel parcel = writeToParcel(original);
        WeightGoalInternal restored =
                (WeightGoalInternal)
                        getOnlyElement(ExercisePerformanceGoalInternal.readFromParcel(parcel));

        assertThat(restored).isInstanceOf(WeightGoalInternal.class);
        assertThat(restored.getMass()).isEqualTo(original.getMass());
        parcel.recycle();
    }

    @Test
    public void weightGoal_convertToExternalAndBack_objectsAreIdentical() {
        ExercisePerformanceGoalInternal.WeightGoalInternal original =
                new ExercisePerformanceGoalInternal.WeightGoalInternal(Mass.fromGrams(5_000));

        assertThat(
                        ((WeightGoalInternal)
                                        ExercisePerformanceGoalInternal.fromExternalObject(
                                                original.toExternalObject()))
                                .getMass())
                .isEqualTo(original.getMass());
    }

    @Test
    public void multipleGoals_writeToParcelThenRestore_objectsAreIdentical() {
        PowerGoalInternal originalPowerGoal =
                new PowerGoalInternal(Power.fromWatts(200.0), Power.fromWatts(240.0));
        HeartRateGoalInternal originalHeartRateGoal = new HeartRateGoalInternal(150, 160);
        Parcel parcel = Parcel.obtain();

        ExercisePerformanceGoalInternal.writeToParcel(
                Arrays.asList(originalPowerGoal, originalHeartRateGoal), parcel);
        parcel.setDataPosition(0);

        List<ExercisePerformanceGoalInternal> result =
                ExercisePerformanceGoalInternal.readFromParcel(parcel);
        assertThat(result.get(0)).isInstanceOf(PowerGoalInternal.class);
        assertThat(result.get(1)).isInstanceOf(HeartRateGoalInternal.class);
        assertThat(((PowerGoalInternal) result.get(0)).getMinPower())
                .isEqualTo(originalPowerGoal.getMinPower());
        assertThat(((PowerGoalInternal) result.get(0)).getMaxPower())
                .isEqualTo(originalPowerGoal.getMaxPower());
        assertThat(result.get(1)).isInstanceOf(HeartRateGoalInternal.class);
        assertThat(((HeartRateGoalInternal) result.get(1)).getMinBpm())
                .isEqualTo(originalHeartRateGoal.getMinBpm());
        assertThat(((HeartRateGoalInternal) result.get(1)).getMaxBpm())
                .isEqualTo(originalHeartRateGoal.getMaxBpm());
    }

    private Parcel writeToParcel(ExercisePerformanceGoalInternal goal) {
        Parcel parcel = Parcel.obtain();

        ExercisePerformanceGoalInternal.writeToParcel(Collections.singletonList(goal), parcel);
        parcel.setDataPosition(0);
        return parcel;
    }
}
