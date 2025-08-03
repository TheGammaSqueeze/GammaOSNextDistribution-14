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

package com.android.federatedcompute.services.scheduling;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.util.concurrent.TimeUnit.SECONDS;

import android.federatedcompute.common.TrainingInterval;
import android.federatedcompute.common.TrainingOptions;

import com.android.federatedcompute.services.common.Flags;
import com.android.federatedcompute.services.data.FederatedTrainingTask;
import com.android.federatedcompute.services.data.fbs.SchedulingMode;
import com.android.federatedcompute.services.data.fbs.TrainingIntervalOptions;

import com.google.intelligence.fcp.client.engine.TaskRetry;

import java.util.Random;

/** The util function about federated job scheduler. */
public class SchedulingUtil {
    private SchedulingUtil() {}

    /** Gets the next run time when federated compute job finishes. */
    public static long getEarliestRuntimeForFCReschedule(
            long nowMs,
            TrainingIntervalOptions interval,
            TaskRetry taskRetry,
            boolean hasContributed,
            Flags flags) {
        long newLatencyMillis;
        if (taskRetry == null || (taskRetry.getDelayMin() <= 0 && taskRetry.getDelayMax() <= 0)) {
            TaskRetry transientErrorRetry = generateTransientErrorTaskRetry(flags);
            newLatencyMillis =
                    generateMinimumDelayMillisFromRange(
                            transientErrorRetry.getDelayMin(), transientErrorRetry.getDelayMax());
        } else {
            long unsanitizedMillis =
                    generateMinimumDelayMillisFromRange(
                            taskRetry.getDelayMin(), taskRetry.getDelayMax());
            long serverSpecifiedLatency =
                    sanitizeMinimumLatencyMillis(
                            unsanitizedMillis, SchedulingMode.UNDEFINED, flags);
            if (interval.schedulingMode() == SchedulingMode.RECURRENT && hasContributed) {
                // Only use the user-specified retry latency if we actually successfully published a
                // result to the server.
                long userSpecifiedLatency = interval.minIntervalMillis();
                userSpecifiedLatency =
                        sanitizeMinimumLatencyMillis(
                                userSpecifiedLatency, SchedulingMode.RECURRENT, flags);
                newLatencyMillis = max(userSpecifiedLatency, serverSpecifiedLatency);
            } else {
                // Use server defined retry window
                newLatencyMillis = serverSpecifiedLatency;
            }
        }
        return nowMs + newLatencyMillis;
    }

    /** Gets the next run time when first time schedule the federated compute job. */
    public static long getEarliestRuntimeForInitialSchedule(
            long nowMs, long lastRunTimeMs, TrainingOptions trainerOptions, Flags flags) {
        long defaultNextRunTimeMs =
                nowMs + SECONDS.toMillis(flags.getDefaultSchedulingPeriodSecs());
        int schedulingMode =
                trainerOptions.getTrainingInterval() != null
                        ? convertSchedulingMode(
                                trainerOptions.getTrainingInterval().getSchedulingMode())
                        : SchedulingMode.ONE_TIME;
        if (schedulingMode != SchedulingMode.RECURRENT) {
            // Non-recurrent task doesn't have user defined interval.
            return defaultNextRunTimeMs;
        }

        long userDefinedMinIntervalMillis =
                sanitizeMinimumLatencyMillis(
                        trainerOptions.getTrainingInterval() == null
                                ? 0
                                : trainerOptions.getTrainingInterval().getMinimumIntervalMillis(),
                        schedulingMode,
                        flags);
        // Take the smaller value of default next run time, and the next run time with user defined
        // interval.
        long minIntervalMsForRecurrentTask =
                min(nowMs + userDefinedMinIntervalMillis, defaultNextRunTimeMs);

        if (lastRunTimeMs == 0) {
            // The task has never run in the past
            return minIntervalMsForRecurrentTask;
        } else {
            // If the task has run in the past, we want to make sure the user defined minimum
            // interval has passed since last time it ran.
            return max(lastRunTimeMs + userDefinedMinIntervalMillis, minIntervalMsForRecurrentTask);
        }
    }

    /** Gets the next run time when the federated job with same job id may be running. */
    public static long getEarliestRuntimeForExistingTask(
            FederatedTrainingTask existingTask,
            TrainingOptions trainingOptions,
            Flags flags,
            long nowMs) {
        long existingTaskMinLatencyMillis = existingTask.earliestNextRunTime() - nowMs;
        int schedulingMode =
                trainingOptions.getTrainingInterval() != null
                        ? convertSchedulingMode(
                                trainingOptions.getTrainingInterval().getSchedulingMode())
                        : SchedulingMode.ONE_TIME;
        long sanitizedMinLatencyMillis =
                sanitizeMinimumLatencyMillis(existingTaskMinLatencyMillis, schedulingMode, flags);
        return nowMs + sanitizedMinLatencyMillis;
    }

    /** Gets the task retry range for transient error happens and worth retry. */
    public static TaskRetry generateTransientErrorTaskRetry(Flags flags) {
        double jitterPercent = min(1.0, max(0.0, flags.getTransientErrorRetryDelayJitterPercent()));
        long targetDelayMillis = SECONDS.toMillis(flags.getTransientErrorRetryDelaySecs());
        long maxDelay = (long) (targetDelayMillis * (1.0 + jitterPercent));
        long minDelay = (long) (targetDelayMillis * (1.0 - jitterPercent));
        return TaskRetry.newBuilder().setDelayMax(maxDelay).setDelayMin(minDelay).build();
    }

    /** Generates a random delay between the provided min and max values. */
    private static long generateMinimumDelayMillisFromRange(long minMillis, long maxMillis) {
        // Sanitize the min/max values.
        minMillis = max(0, minMillis);
        maxMillis = max(minMillis, maxMillis);
        Random randomGen = new Random();
        return minMillis + (long) ((double) (maxMillis - minMillis) * randomGen.nextDouble());
    }

    private static long sanitizeMinimumLatencyMillis(
            long unsanitizedMillis, int schedulingMode, Flags flags) {
        long lowerBoundMillis;
        long upperBoundMillis;
        if (schedulingMode == SchedulingMode.RECURRENT) {
            // Recurrent task with user defined interval
            lowerBoundMillis =
                    SECONDS.toMillis(flags.getMinSchedulingIntervalSecsForFederatedComputation());
            upperBoundMillis =
                    SECONDS.toMillis(flags.getMaxSchedulingIntervalSecsForFederatedComputation());
        } else {
            // One-time task or recurrent task without user defined interval
            lowerBoundMillis = 0L;
            upperBoundMillis = SECONDS.toMillis(flags.getMaxSchedulingPeriodSecs());
        }
        return max(lowerBoundMillis, min(upperBoundMillis, unsanitizedMillis));
    }

    /** Converts from TrainingOptions SchedulingMode to the storage fbs.SchedulingMode. */
    public static int convertSchedulingMode(@TrainingInterval.SchedulingMode int schedulingMode) {
        if (schedulingMode == TrainingInterval.SCHEDULING_MODE_RECURRENT) {
            return SchedulingMode.RECURRENT;
        } else if (schedulingMode == TrainingInterval.SCHEDULING_MODE_ONE_TIME) {
            return SchedulingMode.ONE_TIME;
        } else {
            throw new IllegalStateException("Unknown value for scheduling mode");
        }
    }
}
