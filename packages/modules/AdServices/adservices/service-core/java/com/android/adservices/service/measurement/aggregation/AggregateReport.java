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

package com.android.adservices.service.measurement.aggregation;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.net.Uri;

import androidx.annotation.Nullable;

import com.android.adservices.LoggerFactory;
import com.android.adservices.service.AdServicesConfig;
import com.android.adservices.service.measurement.Trigger;
import com.android.adservices.service.measurement.util.UnsignedLong;


import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Class that contains all the real data needed after aggregation, it is not encrypted.
 */
public class AggregateReport {
    static final String OPERATION = "operation";
    static final String HISTOGRAM = "histogram";
    static final String DATA = "data";

    private String mId;
    private Uri mPublisher;
    private Uri mAttributionDestination;
    @Nullable private Long mSourceRegistrationTime;
    private long mScheduledReportTime;   // triggerTime + random([10min, 1hour])
    private String mEnrollmentId;
    private String mDebugCleartextPayload;
    private AggregateAttributionData mAggregateAttributionData;
    private @Status int mStatus;
    private @DebugReportStatus int mDebugReportStatus;
    private String mApiVersion;
    @Nullable private UnsignedLong mSourceDebugKey;
    @Nullable private UnsignedLong mTriggerDebugKey;
    private String mSourceId;
    private String mTriggerId;
    private UnsignedLong mDedupKey;
    private Uri mRegistrationOrigin;
    private Uri mAggregationCoordinatorOrigin;
    private boolean mIsFakeReport;

    @IntDef(value = {Status.PENDING, Status.DELIVERED, Status.MARKED_TO_DELETE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Status {
        int PENDING = 0;
        int DELIVERED = 1;
        int MARKED_TO_DELETE = 2;
    }

    @IntDef(
            value = {
                DebugReportStatus.NONE,
                DebugReportStatus.PENDING,
                DebugReportStatus.DELIVERED,
            })
    @Retention(RetentionPolicy.SOURCE)
    public @interface DebugReportStatus {
        int NONE = 0;
        int PENDING = 1;
        int DELIVERED = 2;
    }

    private AggregateReport() {
        mId = null;
        mPublisher = null;
        mAttributionDestination = null;
        mSourceRegistrationTime = null;
        mScheduledReportTime = 0L;
        mEnrollmentId = null;
        mDebugCleartextPayload = null;
        mAggregateAttributionData = null;
        mStatus = Status.PENDING;
        mDebugReportStatus = DebugReportStatus.NONE;
        mSourceDebugKey = null;
        mTriggerDebugKey = null;
        mDedupKey = null;
        mRegistrationOrigin = null;
        mAggregationCoordinatorOrigin = null;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof AggregateReport)) {
            return false;
        }
        AggregateReport aggregateReport = (AggregateReport) obj;
        return Objects.equals(mPublisher, aggregateReport.mPublisher)
                && Objects.equals(mAttributionDestination, aggregateReport.mAttributionDestination)
                && Objects.equals(mSourceRegistrationTime, aggregateReport.mSourceRegistrationTime)
                && mScheduledReportTime == aggregateReport.mScheduledReportTime
                && Objects.equals(mEnrollmentId, aggregateReport.mEnrollmentId)
                && Objects.equals(mDebugCleartextPayload, aggregateReport.mDebugCleartextPayload)
                && Objects.equals(
                        mAggregateAttributionData, aggregateReport.mAggregateAttributionData)
                && mStatus == aggregateReport.mStatus
                && mDebugReportStatus == aggregateReport.mDebugReportStatus
                && Objects.equals(mApiVersion, aggregateReport.mApiVersion)
                && Objects.equals(mSourceDebugKey, aggregateReport.mSourceDebugKey)
                && Objects.equals(mTriggerDebugKey, aggregateReport.mTriggerDebugKey)
                && Objects.equals(mSourceId, aggregateReport.mSourceId)
                && Objects.equals(mTriggerId, aggregateReport.mTriggerId)
                && Objects.equals(mDedupKey, aggregateReport.mDedupKey)
                && Objects.equals(mRegistrationOrigin, aggregateReport.mRegistrationOrigin)
                && Objects.equals(
                        mAggregationCoordinatorOrigin,
                        aggregateReport.mAggregationCoordinatorOrigin)
                && mIsFakeReport == aggregateReport.mIsFakeReport;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mId,
                mPublisher,
                mAttributionDestination,
                mSourceRegistrationTime,
                mScheduledReportTime,
                mEnrollmentId,
                mDebugCleartextPayload,
                mAggregateAttributionData,
                mStatus,
                mDebugReportStatus,
                mSourceDebugKey,
                mTriggerDebugKey,
                mSourceId,
                mTriggerId,
                mDedupKey,
                mRegistrationOrigin,
                mAggregationCoordinatorOrigin,
                mIsFakeReport);
    }

    /**
     * Unique identifier for the {@link AggregateReport}.
     */
    public String getId() {
        return mId;
    }

    /**
     * Uri for publisher of this source, primarily an App.
     */
    public Uri getPublisher() {
        return mPublisher;
    }

    /**
     * Uri for attribution destination of source.
     */
    public Uri getAttributionDestination() {
        return mAttributionDestination;
    }

    /** Source registration time. */
    @Nullable
    public Long getSourceRegistrationTime() {
        return mSourceRegistrationTime;
    }

    /**
     * Scheduled report time for aggregate report .
     */
    public long getScheduledReportTime() {
        return mScheduledReportTime;
    }

    /**
     * Ad-tech enrollment ID.
     */
    public String getEnrollmentId() {
        return mEnrollmentId;
    }

    /**
     * Unencrypted aggregate payload string, convert from List of AggregateHistogramContribution.
     */
    public String getDebugCleartextPayload() {
        return mDebugCleartextPayload;
    }

    /** Source Debug Key */
    @Nullable
    public UnsignedLong getSourceDebugKey() {
        return mSourceDebugKey;
    }

    /** Trigger Debug Key */
    @Nullable
    public UnsignedLong getTriggerDebugKey() {
        return mTriggerDebugKey;
    }

    /**
     * Contains the data specific to the aggregate report.
     *
     * @deprecated use {@link #getDebugCleartextPayload()} instead
     */
    @Deprecated
    public AggregateAttributionData getAggregateAttributionData() {
        return mAggregateAttributionData;
    }

    /**
     * Current {@link Status} of the report.
     */
    public @Status int getStatus() {
        return mStatus;
    }

    /** Current {@link DebugReportStatus} of the report. */
    public @DebugReportStatus int getDebugReportStatus() {
        return mDebugReportStatus;
    }

    /**
     * Api version when the report was issued.
     */
    public String getApiVersion() {
        return mApiVersion;
    }

    /** Deduplication key assigned to theis aggregate report. */
    @Nullable
    public UnsignedLong getDedupKey() {
        return mDedupKey;
    }

    /** Returns registration origin used to register the source */
    public Uri getRegistrationOrigin() {
        return mRegistrationOrigin;
    }

    /** Returns coordinator origin for aggregatable reports */
    public Uri getAggregationCoordinatorOrigin() {
        return mAggregationCoordinatorOrigin;
    }

    /** Is the report a null report. */
    public boolean isFakeReport() {
        return mIsFakeReport;
    }

    /**
     * Generates String for debugCleartextPayload. JSON for format : { "operation": "histogram",
     * "data": [{ "bucket": 1369, "value": 32768 }, { "bucket": 3461, "value": 1664 }] }
     */
    @NonNull
    public static String generateDebugPayload(List<AggregateHistogramContribution> contributions)
            throws JSONException {
        JSONArray jsonArray = new JSONArray();
        for (AggregateHistogramContribution contribution : contributions) {
            jsonArray.put(contribution.toJSONObject());
        }
        JSONObject debugPayload = new JSONObject();
        debugPayload.put(OPERATION, HISTOGRAM);
        debugPayload.put(DATA, jsonArray);
        return debugPayload.toString();
    }

    /**
     * It deserializes the debug cleartext payload into {@link AggregateHistogramContribution}s.
     *
     * @return list of {@link AggregateHistogramContribution}s
     */
    @NonNull
    public List<AggregateHistogramContribution> extractAggregateHistogramContributions() {
        try {
            ArrayList<AggregateHistogramContribution> aggregateHistogramContributions =
                    new ArrayList<>();
            JSONObject debugCleartextPayload = new JSONObject(mDebugCleartextPayload);
            JSONArray contributionsArray = debugCleartextPayload.getJSONArray(DATA);
            for (int i = 0; i < contributionsArray.length(); i++) {
                AggregateHistogramContribution aggregateHistogramContribution =
                        new AggregateHistogramContribution.Builder()
                                .fromJsonObject(contributionsArray.getJSONObject(i));
                aggregateHistogramContributions.add(aggregateHistogramContribution);
            }
            return aggregateHistogramContributions;
        } catch (JSONException e) {
            LoggerFactory.getMeasurementLogger()
                    .e(e, "Failed to parse contributions on Aggregate report.");
            return Collections.emptyList();
        }
    }

    /** Source ID */
    public String getSourceId() {
        return mSourceId;
    }

    /** Trigger ID */
    public String getTriggerId() {
        return mTriggerId;
    }

    /**
     * Builder for {@link AggregateReport}.
     */
    public static final class Builder {
        private final AggregateReport mAttributionReport;

        public Builder() {
            mAttributionReport = new AggregateReport();
        }

        /**
         * See {@link AggregateReport#getId()}.
         */
        public Builder setId(String id) {
            mAttributionReport.mId = id;
            return this;
        }

        /**
         * See {@link AggregateReport#getPublisher()}.
         */
        public Builder setPublisher(Uri publisher) {
            mAttributionReport.mPublisher = publisher;
            return this;
        }

        /**
         * See {@link AggregateReport#getAttributionDestination()}.
         */
        public Builder setAttributionDestination(Uri attributionDestination) {
            mAttributionReport.mAttributionDestination = attributionDestination;
            return this;
        }

        /** See {@link AggregateReport#getSourceRegistrationTime()}. */
        public Builder setSourceRegistrationTime(@Nullable Long sourceRegistrationTime) {
            mAttributionReport.mSourceRegistrationTime = sourceRegistrationTime;
            return this;
        }

        /**
         * See {@link AggregateReport#getScheduledReportTime()}.
         */
        public Builder setScheduledReportTime(long scheduledReportTime) {
            mAttributionReport.mScheduledReportTime = scheduledReportTime;
            return this;
        }

        /**
         * See {@link AggregateReport#getEnrollmentId()}.
         */
        public Builder setEnrollmentId(String enrollmentId) {
            mAttributionReport.mEnrollmentId = enrollmentId;
            return this;
        }

        /**
         * See {@link AggregateReport#getDebugCleartextPayload()}.
         */
        public Builder setDebugCleartextPayload(String debugCleartextPayload) {
            mAttributionReport.mDebugCleartextPayload = debugCleartextPayload;
            return this;
        }

        /**
         * See {@link AggregateReport#getAggregateAttributionData()}.
         *
         * @deprecated use {@link #getDebugCleartextPayload()} instead
         */
        @Deprecated
        public Builder setAggregateAttributionData(
                AggregateAttributionData aggregateAttributionData) {
            mAttributionReport.mAggregateAttributionData = aggregateAttributionData;
            return this;
        }

        /**
         * See {@link AggregateReport#getStatus()}
         */
        public Builder setStatus(@Status int status) {
            mAttributionReport.mStatus = status;
            return this;
        }

        /** See {@link AggregateReport#getDebugReportStatus()} */
        public Builder setDebugReportStatus(@DebugReportStatus int debugReportStatus) {
            mAttributionReport.mDebugReportStatus = debugReportStatus;
            return this;
        }

        /**
         * See {@link AggregateReport#getApiVersion()}
         */
        public Builder setApiVersion(String version) {
            mAttributionReport.mApiVersion = version;
            return this;
        }

        /** See {@link AggregateReport#getSourceDebugKey()} */
        public Builder setSourceDebugKey(UnsignedLong sourceDebugKey) {
            mAttributionReport.mSourceDebugKey = sourceDebugKey;
            return this;
        }

        /** See {@link AggregateReport#getTriggerDebugKey()} */
        public Builder setTriggerDebugKey(UnsignedLong triggerDebugKey) {
            mAttributionReport.mTriggerDebugKey = triggerDebugKey;
            return this;
        }

        /** See {@link AggregateReport#getSourceId()} */
        public Builder setSourceId(String sourceId) {
            mAttributionReport.mSourceId = sourceId;
            return this;
        }

        /** See {@link AggregateReport#getTriggerId()} */
        public Builder setTriggerId(String triggerId) {
            mAttributionReport.mTriggerId = triggerId;
            return this;
        }

        /** See {@link AggregateReport#getDedupKey()} */
        @NonNull
        public Builder setDedupKey(@Nullable UnsignedLong dedupKey) {
            mAttributionReport.mDedupKey = dedupKey;
            return this;
        }

        /** See {@link AggregateReport#getRegistrationOrigin()} */
        @NonNull
        public Builder setRegistrationOrigin(Uri registrationOrigin) {
            mAttributionReport.mRegistrationOrigin = registrationOrigin;
            return this;
        }

        /** See {@link AggregateReport#getAggregationCoordinatorOrigin()} */
        public Builder setAggregationCoordinatorOrigin(Uri aggregationCoordinatorOrigin) {
            mAttributionReport.mAggregationCoordinatorOrigin = aggregationCoordinatorOrigin;
            return this;
        }

        /** See {@link AggregateReport#isFakeReport()} */
        public Builder setIsFakeReport(boolean isFakeReport) {
            mAttributionReport.mIsFakeReport = isFakeReport;
            return this;
        }

        /**
         * Given a {@link Trigger} trigger, source registration time, reporting delay, and the api
         * version, initialize an {@link AggregateReport.Builder} builder that builds a null
         * aggregate report. A null aggregate report is used to obscure the number of real aggregate
         * reports.
         *
         * @param trigger the trigger that created a real aggregate report
         * @param fakeSourceTime a fake source registration time
         * @param delay amount of delay in ms to wait before sending out report
         * @param apiVersion api version string that is sent to aggregate service
         * @return builder initialized to build a null aggregate report
         * @throws JSONException thrown if fake contributions create invalid JSON. Fake
         *     contributions are hardcoded, so this should not be thrown.
         */
        public Builder getNullAggregateReportBuilder(
                Trigger trigger, @Nullable Long fakeSourceTime, long delay, String apiVersion)
                throws JSONException {
            long reportTime = trigger.getTriggerTime() + delay;
            AggregateHistogramContribution paddingContribution =
                    new AggregateHistogramContribution.Builder().setPaddingContribution().build();

            String debugPayload =
                    AggregateReport.generateDebugPayload(List.of(paddingContribution));

            mAttributionReport.mApiVersion = apiVersion;
            mAttributionReport.mPublisher = Uri.EMPTY;
            mAttributionReport.mRegistrationOrigin = trigger.getRegistrationOrigin();
            mAttributionReport.mAttributionDestination = trigger.getAttributionDestinationBaseUri();
            mAttributionReport.mSourceRegistrationTime = fakeSourceTime;
            mAttributionReport.mScheduledReportTime = reportTime;
            mAttributionReport.mDebugCleartextPayload = debugPayload;
            mAttributionReport.mSourceDebugKey = null;
            mAttributionReport.mTriggerDebugKey = trigger.getDebugKey();
            mAttributionReport.mIsFakeReport = true;
            mAttributionReport.mTriggerId = trigger.getId();

            if (trigger.getAggregationCoordinatorOrigin() != null) {
                mAttributionReport.mAggregationCoordinatorOrigin =
                        trigger.getAggregationCoordinatorOrigin();
            } else {
                mAttributionReport.mAggregationCoordinatorOrigin =
                        Uri.parse(
                                AdServicesConfig
                                        .getMeasurementDefaultAggregationCoordinatorOrigin());
            }

            return this;
        }

        /** Build the {@link AggregateReport}. */
        public AggregateReport build() {
            return mAttributionReport;
        }
    }
}
