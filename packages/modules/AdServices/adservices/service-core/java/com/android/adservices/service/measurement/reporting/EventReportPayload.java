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

package com.android.adservices.service.measurement.reporting;

import android.annotation.NonNull;
import android.net.Uri;
import android.util.Pair;

import androidx.annotation.Nullable;

import com.android.adservices.service.measurement.util.UnsignedLong;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * EventReportPayload.
 */
public final class EventReportPayload {

    private List<Uri> mAttributionDestinations;
    private String mScheduledReportTime;
    private UnsignedLong mSourceEventId;
    @NonNull private UnsignedLong mTriggerData;
    private String mReportId;
    private String mSourceType;
    private double mRandomizedTriggerRate;
    @Nullable private UnsignedLong mSourceDebugKey;
    @Nullable private UnsignedLong mTriggerDebugKey;
    @NonNull private List<UnsignedLong> mTriggerDebugKeys;
    @Nullable private Pair<Long, Long> mTriggerSummaryBucket;

    private EventReportPayload() {
        mAttributionDestinations = new ArrayList<>();
        mTriggerDebugKeys = new ArrayList<>();
    }

    private EventReportPayload(EventReportPayload other) {
        mAttributionDestinations = other.mAttributionDestinations;
        mScheduledReportTime = other.mScheduledReportTime;
        mSourceEventId = other.mSourceEventId;
        mTriggerData = other.mTriggerData;
        mReportId = other.mReportId;
        mSourceType = other.mSourceType;
        mRandomizedTriggerRate = other.mRandomizedTriggerRate;
        mSourceDebugKey = other.mSourceDebugKey;
        mTriggerDebugKey = other.mTriggerDebugKey;
        mTriggerDebugKeys = other.mTriggerDebugKeys;
        mTriggerSummaryBucket = other.mTriggerSummaryBucket;
    }

    /**
     * Generate the JSON serialization of the event report.
     */
    public JSONObject toJson() throws JSONException {
        JSONObject eventPayloadJson = new JSONObject();

        eventPayloadJson.put("attribution_destination",
                ReportUtil.serializeAttributionDestinations(mAttributionDestinations));
        eventPayloadJson.put("scheduled_report_time", mScheduledReportTime);
        eventPayloadJson.put("source_event_id", mSourceEventId.toString());
        eventPayloadJson.put("trigger_data", mTriggerData.toString());
        eventPayloadJson.put("report_id", mReportId);
        eventPayloadJson.put("source_type", mSourceType);
        eventPayloadJson.put("randomized_trigger_rate", mRandomizedTriggerRate);
        if (mSourceDebugKey != null) {
            eventPayloadJson.put("source_debug_key", mSourceDebugKey.toString());
        }
        if (mTriggerDebugKey != null) {
            eventPayloadJson.put("trigger_debug_key", mTriggerDebugKey.toString());
        }
        if (mTriggerDebugKeys.size() > 0) {
            eventPayloadJson.put(
                    "trigger_debug_keys",
                    ReportUtil.serializeUnsignedLongs(mTriggerDebugKeys));
        }
        if (mTriggerSummaryBucket != null) {
            eventPayloadJson.put(
                    "trigger_summary_bucket",
                    ReportUtil.serializeSummaryBucket(mTriggerSummaryBucket));
        }

        return eventPayloadJson;
    }

    /**
     * Builder class for EventPayloadGenerator.
     */
    public static final class Builder {
        private EventReportPayload mBuilding;

        public Builder() {
            mBuilding = new EventReportPayload();
        }

        /**
         * The attribution destination set on the source.
         */
        public @NonNull Builder setAttributionDestination(
                @NonNull List<Uri> attributionDestinations) {
            mBuilding.mAttributionDestinations = attributionDestinations;
            return this;
        }

        /**
         * The scheduled report time in seconds.
         */
        public @NonNull Builder setScheduledReportTime(String scheduledReportTime) {
            mBuilding.mScheduledReportTime = scheduledReportTime;
            return this;
        }

        /**
         * 64-bit event id set on the attribution source.
         */
        public @NonNull Builder setSourceEventId(@NonNull UnsignedLong sourceEventId) {
            mBuilding.mSourceEventId = sourceEventId;
            return this;
        }

        /**
         * Course data set in the attribution trigger registration.
         */
        public @NonNull Builder setTriggerData(@NonNull UnsignedLong triggerData) {
            mBuilding.mTriggerData = triggerData;
            return this;
        }

        /**
         * A unique id for this report which can be used to prevent double counting.
         */
        public @NonNull Builder setReportId(@NonNull String reportId) {
            mBuilding.mReportId = reportId;
            return this;
        }

        /**
         * Either "navigation" or "event", indicates whether this source was associated with a
         * navigation.
         */
        public @NonNull Builder setSourceType(@NonNull String sourceType) {
            mBuilding.mSourceType = sourceType;
            return this;
        }

        /**
         * Decimal number between 0 and 1 indicating how often noise is applied.
         */
        public @NonNull Builder setRandomizedTriggerRate(@NonNull double randomizedTriggerRate) {
            mBuilding.mRandomizedTriggerRate = randomizedTriggerRate;
            return this;
        }

        /** Source debug key */
        public @NonNull Builder setSourceDebugKey(@Nullable UnsignedLong sourceDebugKey) {
            mBuilding.mSourceDebugKey = sourceDebugKey;
            return this;
        }

        /** Trigger debug key */
        public @NonNull Builder setTriggerDebugKey(@Nullable UnsignedLong triggerDebugKey) {
            mBuilding.mTriggerDebugKey = triggerDebugKey;
            return this;
        }

        /** Trigger debug keys */
        public @NonNull Builder setTriggerDebugKeys(@Nullable List<UnsignedLong> triggerDebugKeys) {
            mBuilding.mTriggerDebugKeys = triggerDebugKeys;
            return this;
        }

        /** Set Trigger Summary Bucket */
        public @NonNull Builder setTriggerSummaryBucket(@Nullable Pair<Long, Long> summaryBucket) {
            mBuilding.mTriggerSummaryBucket = summaryBucket;
            return this;
        }

        /**
         * Build the EventReportPayload.
         */
        public @NonNull EventReportPayload build() {
            if (mBuilding.mTriggerData == null) {
                mBuilding.mTriggerData = new UnsignedLong(0L);
            }
            return new EventReportPayload(mBuilding);
        }
    }
}
