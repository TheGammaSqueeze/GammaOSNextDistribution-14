/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.adservices.service;

/**
 * Hard Coded Configs for AdServices.
 *
 * <p>For Feature Flags that are backed by PH, please see {@link PhFlags}
 */
public class AdServicesConfig {

    public static long getMeasurementEventMainReportingJobPeriodMs() {
        return FlagsFactory.getFlags().getMeasurementEventMainReportingJobPeriodMs();
    }

    /** Returns the min time period (in millis) between each event fallback reporting job run. */
    public static long getMeasurementEventFallbackReportingJobPeriodMs() {
        return FlagsFactory.getFlags().getMeasurementEventFallbackReportingJobPeriodMs();
    }

    /**
     * Returns the list of URL(comma-separated string) for fetching public encryption keys for
     * aggregatable reports.
     */
    public static String getMeasurementAggregationCoordinatorOriginList() {
        return FlagsFactory.getFlags().getMeasurementAggregationCoordinatorOriginList();
    }

    /** Returns the list of URL for fetching public encryption keys for aggregatable reports. */
    public static String getMeasurementAggregationCoordinatorPath() {
        return FlagsFactory.getFlags().getMeasurementAggregationCoordinatorPath();
    }

    public static String getMeasurementDefaultAggregationCoordinatorOrigin() {
        return FlagsFactory.getFlags().getMeasurementDefaultAggregationCoordinatorOrigin();
    }

    /** Returns the min time period (in millis) between each aggregate main reporting job run. */
    public static long getMeasurementAggregateMainReportingJobPeriodMs() {
        return FlagsFactory.getFlags().getMeasurementAggregateMainReportingJobPeriodMs();
    }

    /**
     * Returns the min time period (in millis) between each aggregate fallback reporting job run.
     */
    public static long getMeasurementAggregateFallbackReportingJobPeriodMs() {
        return FlagsFactory.getFlags().getMeasurementAggregateFallbackReportingJobPeriodMs();
    }
}
