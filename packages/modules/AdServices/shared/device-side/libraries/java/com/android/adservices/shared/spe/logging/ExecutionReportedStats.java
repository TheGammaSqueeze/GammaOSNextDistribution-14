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
package com.android.adservices.shared.spe.logging;

import com.google.auto.value.AutoValue;

/**
 * Class for AdServicesBackgroundJobsExecutionReportedStats atom. It's used by {@link
 * JobServiceLogger}.
 */
@AutoValue
public abstract class ExecutionReportedStats {
    /** @return the unique id of a background job. */
    public abstract int getJobId();

    /**
     * @return Time interval from the start to the end of an execution of a background job. It is on
     *     a millisecond basis.
     */
    public abstract int getExecutionLatencyMs();

    /**
     * @return Time interval from the start of previous execution to the start of current execution
     *     of a background job. It is on a minute basis.
     */
    public abstract int getExecutionPeriodMinute();

    /** @return Type of the result code that implies different execution results. */
    public abstract int getExecutionResultCode();

    /**
     * @return The returned reason onStopJob() was called. This is only applicable when the state is
     *     FINISHED, but may be undefined if JobService.onStopJob() was never called for the job.
     *     The default value is STOP_REASON_UNDEFINED.
     */
    public abstract int getStopReason();

    /**
     * @return The module name for which the job run is being reported.
     */
    public abstract int getModuleName();

    /** Create an instance for {@link ExecutionReportedStats.Builder}. */
    public static ExecutionReportedStats.Builder builder() {
        return new AutoValue_ExecutionReportedStats.Builder();
    }

    /** Builder class for {@link ExecutionReportedStats} */
    @AutoValue.Builder
    public abstract static class Builder {
        /** Set Job ID. */
        public abstract Builder setJobId(int value);

        /** Set job execution latency in millisecond. */
        public abstract Builder setExecutionLatencyMs(int value);

        /** Set job period in minute. */
        public abstract Builder setExecutionPeriodMinute(int value);

        /** Set job execution result code. */
        public abstract Builder setExecutionResultCode(int value);

        /** Set job stop reason. */
        public abstract Builder setStopReason(int value);

        /** Set module name. */
        public abstract Builder setModuleName(int value);

        /** Build an instance of {@link ExecutionReportedStats}. */
        public abstract ExecutionReportedStats build();
    }
}
