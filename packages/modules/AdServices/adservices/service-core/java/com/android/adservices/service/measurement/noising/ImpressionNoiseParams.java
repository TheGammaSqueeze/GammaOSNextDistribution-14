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

package com.android.adservices.service.measurement.noising;

import java.util.Objects;

/**
 * POJO for Impression Noise params.
 */
public class ImpressionNoiseParams {
    private final int mReportCount;
    private final int mTriggerDataCardinality;
    private final int mReportingWindowCount;
    private final int mDestinationTypeMultiplier;

    public ImpressionNoiseParams(
            int reportCount,
            int triggerDataCardinality,
            int reportingWindowCount,
            int destinationTypeMultiplier) {
        mReportCount = reportCount;
        mTriggerDataCardinality = triggerDataCardinality;
        mReportingWindowCount = reportingWindowCount;
        mDestinationTypeMultiplier = destinationTypeMultiplier;
    }

    @Override
    public String toString() {
        return "ImpressionNoiseParams{"
                + "mReportCount="
                + mReportCount
                + ", mTriggerDataCardinality="
                + mTriggerDataCardinality
                + ", mReportingWindowCount="
                + mReportingWindowCount
                + ", mDestinationTypeMultiplier="
                + mDestinationTypeMultiplier
                + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ImpressionNoiseParams)) return false;
        ImpressionNoiseParams that = (ImpressionNoiseParams) o;
        return mReportCount == that.mReportCount
                && mTriggerDataCardinality == that.mTriggerDataCardinality
                && mDestinationTypeMultiplier == that.mDestinationTypeMultiplier
                && mReportingWindowCount == that.mReportingWindowCount;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mReportCount, mTriggerDataCardinality, mReportingWindowCount);
    }

    /**
     * Number of reports.
     */
    public int getReportCount() {
        return mReportCount;
    }

    /**
     * Trigger data cardinality.
     */
    public int getTriggerDataCardinality() {
        return mTriggerDataCardinality;
    }

    /**
     * Number of report windows.
     */
    public int getReportingWindowCount() {
        return mReportingWindowCount;
    }

    /**
     * Its value depends on number of destinations to consider for report generation. Helps to
     * increase possible states count accordingly.
     */
    public int getDestinationTypeMultiplier() {
        return mDestinationTypeMultiplier;
    }
}
