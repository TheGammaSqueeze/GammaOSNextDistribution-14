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

import android.os.Parcel;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link TrainingOptions} */
@RunWith(JUnit4.class)
public final class TrainingOptionsTest {
    private static final String POPULATION_NAME = "population";
    private static final String SERVER_ADDRESS = "https://adtech.uri/";
    private static final TrainingInterval TRAINING_INTERVAL =
            new TrainingInterval.Builder().setSchedulingMode(SCHEDULING_MODE_ONE_TIME).build();

    @Test
    public void testFederatedTask() {
        TrainingOptions options =
                new TrainingOptions.Builder()
                        .setPopulationName(POPULATION_NAME)
                        .setServerAddress(SERVER_ADDRESS)
                        .setTrainingInterval(TRAINING_INTERVAL)
                        .build();
        assertThat(options.getPopulationName()).isEqualTo(POPULATION_NAME);
        assertThat(options.getTrainingInterval()).isEqualTo(TRAINING_INTERVAL);
    }

    @Test
    public void testNullPopulation() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new TrainingOptions.Builder()
                                .setPopulationName(null)
                                .setTrainingInterval(TRAINING_INTERVAL)
                                .build());
    }

    @Test
    public void testEmptyPopulation() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new TrainingOptions.Builder()
                                .setPopulationName("")
                                .setTrainingInterval(TRAINING_INTERVAL)
                                .build());
    }

    @Test
    public void testNullServerAddressIsNotAllowed() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new TrainingOptions.Builder()
                                .setPopulationName(POPULATION_NAME)
                                .setServerAddress(null)
                                .build());
    }

    @Test
    public void testEmptyServerAddressIsNotAllowed() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new TrainingOptions.Builder()
                                .setPopulationName(POPULATION_NAME)
                                .setServerAddress("")
                                .build());
    }

    @Test
    public void testNullTrainingIntervalIsAllowed() {
        TrainingOptions options =
                new TrainingOptions.Builder()
                        .setPopulationName(POPULATION_NAME)
                        .setServerAddress(SERVER_ADDRESS)
                        .setTrainingInterval(null)
                        .build();
        assertThat(options.getTrainingInterval()).isNull();
    }

    @Test
    public void testNullContextDataIsAllowed() {
        TrainingOptions options =
                new TrainingOptions.Builder()
                        .setPopulationName(POPULATION_NAME)
                        .setServerAddress(SERVER_ADDRESS)
                        .setTrainingInterval(null)
                        .setContextData(null)
                        .build();

        assertThat(options.getContextData()).isNull();
    }

    @Test
    public void testParcelValidInterval() {
        TrainingOptions options =
                new TrainingOptions.Builder()
                        .setPopulationName(POPULATION_NAME)
                        .setServerAddress(SERVER_ADDRESS)
                        .setTrainingInterval(null)
                        .build();

        Parcel p = Parcel.obtain();
        options.writeToParcel(p, 0);
        p.setDataPosition(0);
        TrainingOptions fromParcel = TrainingOptions.CREATOR.createFromParcel(p);

        assertThat(options).isEqualTo(fromParcel);
    }
}
