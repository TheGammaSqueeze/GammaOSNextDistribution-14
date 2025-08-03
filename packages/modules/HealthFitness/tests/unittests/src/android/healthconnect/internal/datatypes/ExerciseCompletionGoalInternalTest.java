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

import static com.google.common.truth.Truth.assertThat;

import android.health.connect.datatypes.units.Energy;
import android.health.connect.datatypes.units.Length;
import android.health.connect.internal.datatypes.ExerciseCompletionGoalInternal;
import android.health.connect.internal.datatypes.ExerciseCompletionGoalInternal.ActiveCaloriesBurnedGoalInternal;
import android.health.connect.internal.datatypes.ExerciseCompletionGoalInternal.DistanceGoalInternal;
import android.health.connect.internal.datatypes.ExerciseCompletionGoalInternal.DurationGoalInternal;
import android.health.connect.internal.datatypes.ExerciseCompletionGoalInternal.RepetitionsGoalInternal;
import android.health.connect.internal.datatypes.ExerciseCompletionGoalInternal.StepsGoalInternal;
import android.health.connect.internal.datatypes.ExerciseCompletionGoalInternal.TotalCaloriesBurnedGoalInternal;
import android.health.connect.internal.datatypes.ExerciseCompletionGoalInternal.UnknownGoalInternal;
import android.health.connect.internal.datatypes.ExerciseCompletionGoalInternal.UnspecifiedGoalInternal;
import android.os.Parcel;

import org.junit.Test;

import java.time.Duration;

public class ExerciseCompletionGoalInternalTest {
    @Test
    public void distanceGoal_writeToParcelThenRestore_objectsAreIdentical() {
        DistanceGoalInternal original = new DistanceGoalInternal(Length.fromMeters(800));

        Parcel parcel = writeToParcel(original);
        DistanceGoalInternal restored =
                (DistanceGoalInternal) ExerciseCompletionGoalInternal.readFromParcel(parcel);

        assertThat(restored).isInstanceOf(DistanceGoalInternal.class);
        assertThat(restored.getDistance()).isEqualTo(original.getDistance());
        parcel.recycle();
    }

    @Test
    public void distanceGoal_convertToExternalAndBack_objectsAreIdentical() {
        DistanceGoalInternal original = new DistanceGoalInternal(Length.fromMeters(800));

        assertThat(
                        ((DistanceGoalInternal)
                                        ExerciseCompletionGoalInternal.fromExternalObject(
                                                original.toExternalObject()))
                                .getDistance())
                .isEqualTo(original.getDistance());
    }

    @Test
    public void stepsGoal_writeToParcelThenRestore_objectsAreIdentical() {
        StepsGoalInternal original = new StepsGoalInternal(500);

        Parcel parcel = writeToParcel(original);
        StepsGoalInternal restored =
                (StepsGoalInternal) ExerciseCompletionGoalInternal.readFromParcel(parcel);

        assertThat(restored).isInstanceOf(StepsGoalInternal.class);
        assertThat(restored.getSteps()).isEqualTo(original.getSteps());
        parcel.recycle();
    }

    @Test
    public void stepsGoal_convertToExternalAndBack_objectsAreIdentical() {
        StepsGoalInternal original = new StepsGoalInternal(100);

        assertThat(
                        ((StepsGoalInternal)
                                        ExerciseCompletionGoalInternal.fromExternalObject(
                                                original.toExternalObject()))
                                .getSteps())
                .isEqualTo(original.getSteps());
    }

    @Test
    public void durationGoal_writeToParcelThenRestore_objectsAreIdentical() {
        DurationGoalInternal original = new DurationGoalInternal(Duration.ofHours(5));

        Parcel parcel = writeToParcel(original);
        DurationGoalInternal restored =
                (DurationGoalInternal) ExerciseCompletionGoalInternal.readFromParcel(parcel);

        assertThat(restored).isInstanceOf(DurationGoalInternal.class);
        assertThat(restored.getDuration()).isEqualTo(original.getDuration());
        parcel.recycle();
    }

    @Test
    public void durationGoal_convertToExternalAndBack_objectsAreIdentical() {
        DurationGoalInternal original = new DurationGoalInternal(Duration.ofMinutes(45));

        assertThat(
                        ((DurationGoalInternal)
                                        ExerciseCompletionGoalInternal.fromExternalObject(
                                                original.toExternalObject()))
                                .getDuration())
                .isEqualTo(original.getDuration());
    }

    @Test
    public void repsGoal_writeToParcelThenRestore_objectsAreIdentical() {
        RepetitionsGoalInternal original = new RepetitionsGoalInternal(8);

        Parcel parcel = writeToParcel(original);
        RepetitionsGoalInternal restored =
                (RepetitionsGoalInternal) ExerciseCompletionGoalInternal.readFromParcel(parcel);

        assertThat(restored).isInstanceOf(RepetitionsGoalInternal.class);
        assertThat(restored.getReps()).isEqualTo(original.getReps());
        parcel.recycle();
    }

    @Test
    public void repsGoal_convertToExternalAndBack_objectsAreIdentical() {
        RepetitionsGoalInternal original = new RepetitionsGoalInternal(8);

        assertThat(
                        ((RepetitionsGoalInternal)
                                        ExerciseCompletionGoalInternal.fromExternalObject(
                                                original.toExternalObject()))
                                .getReps())
                .isEqualTo(original.getReps());
    }

    @Test
    public void totalCaloriesBurnedGoal_writeToParcelThenRestore_objectsAreIdentical() {
        TotalCaloriesBurnedGoalInternal original =
                new TotalCaloriesBurnedGoalInternal(Energy.fromCalories(400.0));

        Parcel parcel = writeToParcel(original);
        TotalCaloriesBurnedGoalInternal restored =
                (TotalCaloriesBurnedGoalInternal)
                        ExerciseCompletionGoalInternal.readFromParcel(parcel);

        assertThat(restored).isInstanceOf(TotalCaloriesBurnedGoalInternal.class);
        assertThat(restored.getTotalCalories()).isEqualTo(original.getTotalCalories());
        parcel.recycle();
    }

    @Test
    public void totalCaloriesBurnedGoal_convertToExternalAndBack_objectsAreIdentical() {
        TotalCaloriesBurnedGoalInternal original =
                new TotalCaloriesBurnedGoalInternal(Energy.fromCalories(500));

        assertThat(
                        ((TotalCaloriesBurnedGoalInternal)
                                        ExerciseCompletionGoalInternal.fromExternalObject(
                                                original.toExternalObject()))
                                .getTotalCalories())
                .isEqualTo(original.getTotalCalories());
    }

    @Test
    public void activeCaloriesBurnedGoal_writeToParcelThenRestore_objectsAreIdentical() {
        ActiveCaloriesBurnedGoalInternal original =
                new ActiveCaloriesBurnedGoalInternal(Energy.fromCalories(400.0));

        Parcel parcel = writeToParcel(original);
        ActiveCaloriesBurnedGoalInternal restored =
                (ActiveCaloriesBurnedGoalInternal)
                        ExerciseCompletionGoalInternal.readFromParcel(parcel);

        assertThat(restored).isInstanceOf(ActiveCaloriesBurnedGoalInternal.class);
        assertThat(restored.getActiveCalories()).isEqualTo(original.getActiveCalories());
        parcel.recycle();
    }

    @Test
    public void activeCaloriesBurnedGoal_convertToExternalAndBack_objectsAreIdentical() {
        ActiveCaloriesBurnedGoalInternal original =
                new ActiveCaloriesBurnedGoalInternal(Energy.fromCalories(500));

        assertThat(
                        ((ActiveCaloriesBurnedGoalInternal)
                                        ExerciseCompletionGoalInternal.fromExternalObject(
                                                original.toExternalObject()))
                                .getActiveCalories())
                .isEqualTo(original.getActiveCalories());
    }

    @Test
    public void unknownGoal_writeToParcelThenRestore_objectsAreIdentical() {
        UnknownGoalInternal original = UnknownGoalInternal.INSTANCE;

        Parcel parcel = writeToParcel(original);
        UnknownGoalInternal restored =
                (UnknownGoalInternal) ExerciseCompletionGoalInternal.readFromParcel(parcel);

        assertThat(restored).isInstanceOf(UnknownGoalInternal.class);
        assertThat(restored).isEqualTo(original);
        parcel.recycle();
    }

    @Test
    public void unknownGoal_convertToExternalAndBack_objectsAreIdentical() {
        UnknownGoalInternal original = UnknownGoalInternal.INSTANCE;

        assertThat(ExerciseCompletionGoalInternal.fromExternalObject(original.toExternalObject()))
                .isEqualTo(original);
    }

    @Test
    public void unspecifiedGoal_writeToParcelThenRestore_objectsAreIdentical() {
        UnspecifiedGoalInternal original = UnspecifiedGoalInternal.INSTANCE;

        Parcel parcel = writeToParcel(original);
        UnspecifiedGoalInternal restored =
                (UnspecifiedGoalInternal) ExerciseCompletionGoalInternal.readFromParcel(parcel);

        assertThat(restored).isInstanceOf(UnspecifiedGoalInternal.class);
        assertThat(restored).isEqualTo(original);
        parcel.recycle();
    }

    @Test
    public void unspecifiedGoal_convertToExternalAndBack_objectsAreIdentical() {
        UnspecifiedGoalInternal original = UnspecifiedGoalInternal.INSTANCE;

        assertThat(UnspecifiedGoalInternal.fromExternalObject(original.toExternalObject()))
                .isEqualTo(original);
    }

    private Parcel writeToParcel(ExerciseCompletionGoalInternal goal) {
        Parcel parcel = Parcel.obtain();

        goal.writeToParcel(parcel);
        parcel.setDataPosition(0);
        return parcel;
    }
}
