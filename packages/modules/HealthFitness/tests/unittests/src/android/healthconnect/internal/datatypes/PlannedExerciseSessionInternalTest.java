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

import android.health.connect.datatypes.units.Power;
import android.health.connect.internal.datatypes.ExercisePerformanceGoalInternal;
import android.health.connect.internal.datatypes.PlannedExerciseBlockInternal;
import android.health.connect.internal.datatypes.PlannedExerciseSessionRecordInternal;
import android.health.connect.internal.datatypes.PlannedExerciseStepInternal;
import android.os.Parcel;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class PlannedExerciseSessionInternalTest {
    @Test
    public void testPlanWriteToParcel_populateToParcelAndFrom_restoredFieldsAreIdentical() {
        PlannedExerciseSessionRecordInternal plan = TestUtils.buildPlannedExerciseSessionInternal();
        PlannedExerciseBlockInternal block1 = TestUtils.buildExerciseBlockInternal();
        PlannedExerciseBlockInternal block2 = TestUtils.buildExerciseBlockInternal();
        PlannedExerciseStepInternal step1 = TestUtils.buildExerciseStepInternal();
        step1.setPerformanceGoals(
                Arrays.asList(
                        new ExercisePerformanceGoalInternal.PowerGoalInternal(
                                Power.fromWatts(140), Power.fromWatts(160)),
                        ExercisePerformanceGoalInternal.UnknownGoalInternal.INSTANCE,
                        new ExercisePerformanceGoalInternal.PowerGoalInternal(
                                Power.fromWatts(180), Power.fromWatts(200))));
        PlannedExerciseStepInternal step2 = TestUtils.buildExerciseStepInternal();
        step2.setPerformanceGoals(
                Collections.singletonList(
                        new ExercisePerformanceGoalInternal.PowerGoalInternal(
                                Power.fromWatts(100), Power.fromWatts(120))));
        block1.setExerciseSteps(Arrays.asList(step1, step2));
        plan.setExerciseBlocks(Arrays.asList(block1, block2));
        PlannedExerciseSessionRecordInternal restoredPlan = writeAndRestoreFromParcel(plan);
        assertFieldsAreEqual(plan, restoredPlan);
    }

    private PlannedExerciseSessionRecordInternal writeAndRestoreFromParcel(
            PlannedExerciseSessionRecordInternal plan) {
        Parcel parcel = Parcel.obtain();
        plan.writeToParcel(parcel);
        parcel.setDataPosition(0);
        PlannedExerciseSessionRecordInternal restoredSession =
                new PlannedExerciseSessionRecordInternal();
        restoredSession.populateUsing(parcel);
        parcel.recycle();
        return restoredSession;
    }

    private void assertFieldsAreEqual(
            PlannedExerciseSessionRecordInternal first,
            PlannedExerciseSessionRecordInternal second) {
        // Common interval record fields.
        assertThat(first.getStartTimeInMillis()).isEqualTo(second.getStartTimeInMillis());
        assertThat(first.getEndTimeInMillis()).isEqualTo(second.getEndTimeInMillis());
        assertThat(first.getStartZoneOffsetInSeconds())
                .isEqualTo(second.getStartZoneOffsetInSeconds());
        assertThat(first.getEndZoneOffsetInSeconds()).isEqualTo(second.getEndZoneOffsetInSeconds());
        // Top level fields.
        assertThat(first.getTitle()).isEqualTo(second.getTitle());
        assertThat(first.getNotes()).isEqualTo(second.getNotes());
        assertThat(first.getExerciseType()).isEqualTo(second.getExerciseType());
        assertThat(first.getHasExplicitTime()).isEqualTo(second.getHasExplicitTime());
        // Blocks.
        assertThat(first.getExerciseBlocks().size()).isEqualTo(second.getExerciseBlocks().size());
        for (int i = 0; i < first.getExerciseBlocks().size(); i++) {
            PlannedExerciseBlockInternal firstBlock = first.getExerciseBlocks().get(i);
            PlannedExerciseBlockInternal secondBlock = second.getExerciseBlocks().get(i);
            assertThat(firstBlock.getRepetitions()).isEqualTo(secondBlock.getRepetitions());
            assertThat(firstBlock.getDescription()).isEqualTo(secondBlock.getDescription());
            // Steps.
            for (int j = 0; j < firstBlock.getExerciseSteps().size(); j++) {
                PlannedExerciseStepInternal firstStep = firstBlock.getExerciseSteps().get(j);
                PlannedExerciseStepInternal secondStep = secondBlock.getExerciseSteps().get(j);
                assertThat(firstStep.getExerciseType()).isEqualTo(secondStep.getExerciseType());
                assertThat(firstStep.getDescription()).isEqualTo(secondStep.getDescription());
                // Goals.
                assertThat(firstStep.getCompletionGoal().toExternalObject())
                        .isEqualTo(secondStep.getCompletionGoal().toExternalObject());
                for (int k = 0; k < firstStep.getPerformanceGoals().size(); k++) {
                    assertThat(firstStep.getPerformanceGoals().get(k).toExternalObject())
                            .isEqualTo(secondStep.getPerformanceGoals().get(k).toExternalObject());
                }
            }
        }
    }
}
