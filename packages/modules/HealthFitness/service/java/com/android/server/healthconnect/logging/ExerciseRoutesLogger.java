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

package com.android.server.healthconnect.logging;

import static android.health.HealthFitnessStatsLog.EXERCISE_ROUTE_API_CALLED;
import static android.health.HealthFitnessStatsLog.EXERCISE_ROUTE_API_CALLED__OPERATION__OPERATION_READ;
import static android.health.HealthFitnessStatsLog.EXERCISE_ROUTE_API_CALLED__OPERATION__OPERATION_UPSERT;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.health.HealthFitnessStatsLog;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Objects;

/**
 * Class to log metrics for ExerciseRoutes
 *
 * @hide
 */
public class ExerciseRoutesLogger {

    /**
     * Operations to supported by ExerciseRoutes logging
     *
     * @hide
     */
    public static final class Operations {
        public static final int UPSERT = EXERCISE_ROUTE_API_CALLED__OPERATION__OPERATION_UPSERT;
        public static final int READ = EXERCISE_ROUTE_API_CALLED__OPERATION__OPERATION_READ;

        @IntDef({UPSERT, READ})
        @Retention(RetentionPolicy.SOURCE)
        public @interface Operation {}
    }

    /**
     * Log exercise route metrics
     *
     * @param operation Operation being done on the record
     * @param packageName Package name of the caller
     * @param numberOfRecordsWithExerciseRoutes Number of records with Exercise Routes
     */
    public static void log(
            @Operations.Operation int operation,
            @NonNull String packageName,
            int numberOfRecordsWithExerciseRoutes) {
        Objects.requireNonNull(packageName);

        if (numberOfRecordsWithExerciseRoutes == 0) {
            return;
        }

        HealthFitnessStatsLog.write(
                EXERCISE_ROUTE_API_CALLED,
                operation,
                packageName,
                numberOfRecordsWithExerciseRoutes);
    }
}
