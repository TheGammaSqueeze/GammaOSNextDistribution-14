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

package android.federatedcompute.common;

import static android.federatedcompute.common.TrainingInterval.SCHEDULING_MODE_ONE_TIME;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class ScheduleFederatedComputeRequestTest {
    private static final String POPULATION_NAME = "population";

    @Test
    public void buildScheduleFederatedComputeRequest_success() {
        ScheduleFederatedComputeRequest request =
                new ScheduleFederatedComputeRequest.Builder()
                        .setTrainingOptions(
                                new TrainingOptions.Builder()
                                        .setPopulationName(POPULATION_NAME)
                                        .setTrainingInterval(
                                                new TrainingInterval.Builder()
                                                        .setSchedulingMode(SCHEDULING_MODE_ONE_TIME)
                                                        .build())
                                        .build())
                        .build();

        assertThat(request.getTrainingOptions().getPopulationName()).isEqualTo(POPULATION_NAME);
    }

    @Test
    public void testScheduleFederatedComputeRequestEquals() {
        ScheduleFederatedComputeRequest request1 =
                new ScheduleFederatedComputeRequest.Builder()
                        .setTrainingOptions(
                                new TrainingOptions.Builder()
                                        .setPopulationName(POPULATION_NAME)
                                        .setTrainingInterval(
                                                new TrainingInterval.Builder()
                                                        .setSchedulingMode(SCHEDULING_MODE_ONE_TIME)
                                                        .build())
                                        .build())
                        .build();
        ScheduleFederatedComputeRequest request2 =
                new ScheduleFederatedComputeRequest.Builder()
                        .setTrainingOptions(
                                new TrainingOptions.Builder()
                                        .setPopulationName(POPULATION_NAME)
                                        .setTrainingInterval(
                                                new TrainingInterval.Builder()
                                                        .setSchedulingMode(SCHEDULING_MODE_ONE_TIME)
                                                        .build())
                                        .build())
                        .build();

        assertThat(request1).isEqualTo(request2);
    }

    @Test
    public void buildNullTrainingOptions_failed() {
        assertThrows(
                NullPointerException.class,
                () -> new ScheduleFederatedComputeRequest.Builder().build());
    }
}
