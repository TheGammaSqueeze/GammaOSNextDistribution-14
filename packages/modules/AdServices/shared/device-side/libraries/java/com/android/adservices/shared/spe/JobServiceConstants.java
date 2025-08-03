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

package com.android.adservices.shared.spe;

import android.app.job.JobParameters;

/** Class to store constants used by background jobs. */
public final class JobServiceConstants {
    /**
     * Unavailable stop reason, used when {@link
     * android.app.job.JobService#onStopJob(JobParameters)} is not invoked in an execution.
     *
     * <p>Use the value of {@link JobParameters#STOP_REASON_UNDEFINED} in case API version is lower
     * than S.
     */
    public static final int UNAVAILABLE_STOP_REASON = 0;

    /** The shared preference file name for background jobs */
    public static final String SHARED_PREFS_BACKGROUND_JOBS = "PPAPI_Background_Jobs";

    /** The suffix to compose the key to store job start timestamp */
    public static final String SHARED_PREFS_START_TIMESTAMP_SUFFIX = "_job_start_timestamp";

    /** The suffix to compose the key to store job stop timestamp */
    public static final String SHARED_PREFS_STOP_TIMESTAMP_SUFFIX = "_job_stop_timestamp";

    /** The suffix to compose the key to store job execution period */
    public static final String SHARED_PREFS_EXEC_PERIOD_SUFFIX = "_job_execution_period";

    /**
     * Value of the execution start timestamp when it's unavailable to achieve. For example, the
     * shared preference key doesn't exist.
     */
    public static final long UNAVAILABLE_JOB_EXECUTION_START_TIMESTAMP = -1L;

    /**
     * Value of the execution stop timestamp when it's unavailable to achieve. For example, the
     * shared preference key doesn't exist.
     */
    public static final long UNAVAILABLE_JOB_EXECUTION_STOP_TIMESTAMP = -1L;

    /**
     * Value of the execution period when it's unavailable to achieve, such as in the first
     * execution.
     */
    public static final long UNAVAILABLE_JOB_EXECUTION_PERIOD = -1L;

    /**
     * Value of the execution latency if it cannot be computed, such as an open-end execution caused
     * by system or device issue.
     */
    public static final long UNAVAILABLE_JOB_LATENCY = -1L;

    /** The number of milliseconds per minute. */
    public static final int MILLISECONDS_PER_MINUTE = 60 * 1000;

    /** Maximum possible percentage for percentage variables. */
    public static final int MAX_PERCENTAGE = 100;
}
