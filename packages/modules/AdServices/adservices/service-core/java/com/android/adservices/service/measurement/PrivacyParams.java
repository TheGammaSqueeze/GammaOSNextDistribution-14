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

package com.android.adservices.service.measurement;

import com.android.adservices.service.Flags;

import java.util.concurrent.TimeUnit;

/**
 * Class for holding privacy related parameters.
 * All values in this class are temporary and subject to change based on feedback and testing.
 */
public final class PrivacyParams {

    /**
     * Max reports for 'Navigation' {@link Source}.
     */
    public static final int NAVIGATION_SOURCE_MAX_REPORTS = 3;

    /**
     * Max reports for 'Event' {@link Source}.
     */
    public static final int EVENT_SOURCE_MAX_REPORTS = 1;

    /**
     * Max reports for Install Attributed 'Event' {@link Source}.
     */
    public static final int INSTALL_ATTR_EVENT_SOURCE_MAX_REPORTS = 2;

    /**
     * Rate limit window for (Source Site, Destination Site, Reporting Site, Window) privacy unit.
     * 30 days.
     */
    public static final long RATE_LIMIT_WINDOW_MILLISECONDS = TimeUnit.DAYS.toMillis(30);

    /**
     * Early reporting window for 'Navigation' {@link Source}.
     * 2 days and 7 days.
     */
    public static final long[] NAVIGATION_EARLY_REPORTING_WINDOW_MILLISECONDS = new long[]{
            TimeUnit.DAYS.toMillis(2), TimeUnit.DAYS.toMillis(7)
    };

    /**
     * Early reporting window for 'Event' {@link Source}.
     * No windows.
     */
    public static final long[] EVENT_EARLY_REPORTING_WINDOW_MILLISECONDS = new long[]{ };

    /**
     * Early reporting window for Install Attributed 'Navigation' {@link Source}. 2 days and 7 days.
     */
    public static final long[] INSTALL_ATTR_NAVIGATION_EARLY_REPORTING_WINDOW_MILLISECONDS =
            NAVIGATION_EARLY_REPORTING_WINDOW_MILLISECONDS;

    /**
     * Early reporting window for Install Attributed 'Event' {@link Source}.
     * 2 days.
     */
    public static final long[] INSTALL_ATTR_EVENT_EARLY_REPORTING_WINDOW_MILLISECONDS =
            new long[]{ TimeUnit.DAYS.toMillis(2) };


    /**
     * Trigger data cardinality for 'Event' {@link Source} attribution.
     */
    public static final int EVENT_TRIGGER_DATA_CARDINALITY = 2;

    /**
     * Trigger data cardinality for 'Navigation' {@link Source} attribution.
     */
    private static final int NAVIGATION_TRIGGER_DATA_CARDINALITY = 8;

    public static int getNavigationTriggerDataCardinality() {
        return NAVIGATION_TRIGGER_DATA_CARDINALITY;
    }

    /**
     * L1, the maximum sum of the contributions (values) across all buckets for a given source
     * event.
     */
    public static final int MAX_SUM_OF_AGGREGATE_VALUES_PER_SOURCE = 65536;

    /** Amount of bytes allocated for aggregate histogram bucket */
    public static final int AGGREGATE_HISTOGRAM_BUCKET_BYTE_SIZE = 16;

    /** Amount of bytes allocated for aggregate histogram value */
    public static final int AGGREGATE_HISTOGRAM_VALUE_BYTE_SIZE = 4;

    /** Minimum time an aggregate report is delayed after trigger */
    public static final long AGGREGATE_REPORT_MIN_DELAY = TimeUnit.MINUTES.toMillis(0L);

    /** Maximum time an aggregate report is delayed after trigger */
    public static final long AGGREGATE_REPORT_DELAY_SPAN = TimeUnit.MINUTES.toMillis(10L);

    private static final int PRIVACY_EPSILON = 14;

    public static int getPrivacyEpsilon() {
        return PRIVACY_EPSILON;
    }

    public static final double NUMBER_EQUAL_THRESHOLD = 0.0000001D;

    /**
     * Maximum early reporting windows configured through {@link
     * Flags#MEASUREMENT_EVENT_REPORTS_VTC_EARLY_REPORTING_WINDOWS} or {@link
     * Flags#MEASUREMENT_EVENT_REPORTS_CTC_EARLY_REPORTING_WINDOWS}.
     */
    public static final int MAX_CONFIGURABLE_EVENT_REPORT_EARLY_REPORTING_WINDOWS = 2;

    private PrivacyParams() {
    }
}
