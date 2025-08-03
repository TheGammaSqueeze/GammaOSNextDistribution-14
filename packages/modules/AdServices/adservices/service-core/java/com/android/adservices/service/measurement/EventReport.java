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

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.net.Uri;
import android.util.Pair;

import androidx.annotation.Nullable;

import com.android.adservices.service.measurement.noising.SourceNoiseHandler;
import com.android.adservices.service.measurement.reporting.EventReportWindowCalcDelegate;
import com.android.adservices.service.measurement.util.UnsignedLong;

import com.google.common.collect.ImmutableMultiset;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * POJO for EventReport.
 */
public class EventReport {

    private String mId;
    private UnsignedLong mSourceEventId;
    private long mReportTime;
    private long mTriggerTime;
    private long mTriggerPriority;
    private List<Uri> mAttributionDestinations;
    private String mEnrollmentId;
    private UnsignedLong mTriggerData;
    private UnsignedLong mTriggerDedupKey;
    private double mRandomizedTriggerRate;
    private @Status int mStatus;
    private @DebugReportStatus int mDebugReportStatus;
    private Source.SourceType mSourceType;
    @Nullable private UnsignedLong mSourceDebugKey;
    @Nullable private UnsignedLong mTriggerDebugKey;
    @NonNull private List<UnsignedLong> mTriggerDebugKeys;
    private String mSourceId;
    private String mTriggerId;
    private Uri mRegistrationOrigin;
    private long mTriggerValue;
    private Pair<Long, Long> mTriggerSummaryBucket;

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

    private EventReport() {
        mTriggerDedupKey = null;
        mTriggerDebugKeys = new ArrayList<>();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof EventReport)) {
            return false;
        }
        EventReport eventReport = (EventReport) obj;
        return mStatus == eventReport.mStatus
                && mDebugReportStatus == eventReport.mDebugReportStatus
                && mReportTime == eventReport.mReportTime
                && Objects.equals(mAttributionDestinations, eventReport.mAttributionDestinations)
                && ImmutableMultiset.copyOf(mAttributionDestinations)
                        .equals(ImmutableMultiset.copyOf(eventReport.mAttributionDestinations))
                && Objects.equals(mEnrollmentId, eventReport.mEnrollmentId)
                && mTriggerTime == eventReport.mTriggerTime
                && Objects.equals(mTriggerData, eventReport.mTriggerData)
                && Objects.equals(mSourceEventId, eventReport.mSourceEventId)
                && mTriggerPriority == eventReport.mTriggerPriority
                && Objects.equals(mTriggerDedupKey, eventReport.mTriggerDedupKey)
                && mSourceType == eventReport.mSourceType
                && mRandomizedTriggerRate == eventReport.mRandomizedTriggerRate
                && Objects.equals(mSourceDebugKey, eventReport.mSourceDebugKey)
                && Objects.equals(mTriggerDebugKey, eventReport.mTriggerDebugKey)
                && Objects.equals(mTriggerDebugKeys, eventReport.mTriggerDebugKeys)
                && Objects.equals(mSourceId, eventReport.mSourceId)
                && Objects.equals(mTriggerId, eventReport.mTriggerId)
                && Objects.equals(mRegistrationOrigin, eventReport.mRegistrationOrigin)
                && mTriggerValue == eventReport.mTriggerValue
                && Objects.equals(mTriggerSummaryBucket, eventReport.mTriggerSummaryBucket);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mStatus,
                mDebugReportStatus,
                mReportTime,
                mAttributionDestinations,
                mEnrollmentId,
                mTriggerTime,
                mTriggerData,
                mSourceEventId,
                mTriggerPriority,
                mTriggerDedupKey,
                mSourceType,
                mRandomizedTriggerRate,
                mSourceDebugKey,
                mTriggerDebugKey,
                mTriggerDebugKeys,
                mSourceId,
                mTriggerId,
                mRegistrationOrigin,
                mTriggerValue,
                mTriggerSummaryBucket);
    }

    /** Unique identifier for the report. */
    public String getId() {
        return mId;
    }

    /** Identifier of the associated {@link Source} event. */
    public UnsignedLong getSourceEventId() {
        return mSourceEventId;
    }

    /**
     * Scheduled time for the report to be sent.
     */
    public long getReportTime() {
        return mReportTime;
    }

    /**
     * TriggerTime of the associated {@link Trigger}.
     */
    public long getTriggerTime() {
        return mTriggerTime;
    }

    /**
     * Priority of the associated {@link Trigger}.
     */
    public long getTriggerPriority() {
        return mTriggerPriority;
    }

    /**
     * AttributionDestinations of the {@link Source} and {@link Trigger}.
     */
    public List<Uri> getAttributionDestinations() {
        return mAttributionDestinations;
    }

    /**
     * Ad Tech enrollment ID.
     */
    public String getEnrollmentId() {
        return mEnrollmentId;
    }

    /**
     * Metadata for the report.
     */
    public UnsignedLong getTriggerData() {
        return mTriggerData;
    }

    /**
     * Deduplication key of the associated {@link Trigger}
     */
    public UnsignedLong getTriggerDedupKey() {
        return mTriggerDedupKey;
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
     * SourceType of the event's source.
     */
    public Source.SourceType getSourceType() {
        return mSourceType;
    }

    /**
     * Randomized trigger rate for noising
     */
    public double getRandomizedTriggerRate() {
        return mRandomizedTriggerRate;
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

    /** Trigger Debug Keys */
    @NonNull
    public List<UnsignedLong> getTriggerDebugKeys() {
        return mTriggerDebugKeys;
    }

    /** Source ID */
    public String getSourceId() {
        return mSourceId;
    }

    /** Trigger ID */
    public String getTriggerId() {
        return mTriggerId;
    }

    /** Trigger summary bucket */
    public Pair<Long, Long> getTriggerSummaryBucket() {
        return mTriggerSummaryBucket;
    }

    /** Returns registration origin used to register the source and trigger */
    public Uri getRegistrationOrigin() {
        return mRegistrationOrigin;
    }

    /** Trigger Value */
    public long getTriggerValue() {
        return mTriggerValue;
    }

    /** Get Summary Bucket As String */
    public String getStringEncodedTriggerSummaryBucket() {
        if (mTriggerSummaryBucket == null) {
            return null;
        } else {
            return mTriggerSummaryBucket.first + "," + mTriggerSummaryBucket.second;
        }
    }

    /** Builder for {@link EventReport} */
    public static final class Builder {

        private final EventReport mBuilding;

        public Builder() {
            mBuilding = new EventReport();
        }

        /** See {@link EventReport#getId()} */
        public Builder setId(String id) {
            mBuilding.mId = id;
            return this;
        }

        /** See {@link EventReport#getSourceEventId()} */
        public Builder setSourceEventId(UnsignedLong sourceEventId) {
            mBuilding.mSourceEventId = sourceEventId;
            return this;
        }

        /** See {@link EventReport#getEnrollmentId()} */
        public Builder setEnrollmentId(String enrollmentId) {
            mBuilding.mEnrollmentId = enrollmentId;
            return this;
        }

        /** See {@link EventReport#getAttributionDestinations()} */
        public Builder setAttributionDestinations(List<Uri> attributionDestinations) {
            mBuilding.mAttributionDestinations = attributionDestinations;
            return this;
        }

        /**
         * See {@link EventReport#getTriggerTime()}
         */
        public Builder setTriggerTime(long triggerTime) {
            mBuilding.mTriggerTime = triggerTime;
            return this;
        }

        /**
         * See {@link EventReport#getTriggerData()}
         */
        public Builder setTriggerData(UnsignedLong triggerData) {
            mBuilding.mTriggerData = triggerData;
            return this;
        }

        /**
         * See {@link EventReport#getTriggerPriority()}
         */
        public Builder setTriggerPriority(long triggerPriority) {
            mBuilding.mTriggerPriority = triggerPriority;
            return this;
        }

        /**
         * See {@link EventReport#getTriggerDedupKey()}
         */
        public Builder setTriggerDedupKey(UnsignedLong triggerDedupKey) {
            mBuilding.mTriggerDedupKey = triggerDedupKey;
            return this;
        }

        /**
         * See {@link EventReport#getReportTime()}
         */
        public Builder setReportTime(long reportTime) {
            mBuilding.mReportTime = reportTime;
            return this;
        }

        /**
         * See {@link EventReport#getStatus()}
         */
        public Builder setStatus(@Status int status) {
            mBuilding.mStatus = status;
            return this;
        }

        /** See {@link EventReport#getDebugReportStatus()}} */
        public Builder setDebugReportStatus(@DebugReportStatus int debugReportStatus) {
            mBuilding.mDebugReportStatus = debugReportStatus;
            return this;
        }

        /**
         * See {@link EventReport#getSourceType()}
         */
        public Builder setSourceType(Source.SourceType sourceType) {
            mBuilding.mSourceType = sourceType;
            return this;
        }

        /** See {@link EventReport#getRandomizedTriggerRate()}} */
        public Builder setRandomizedTriggerRate(double randomizedTriggerRate) {
            mBuilding.mRandomizedTriggerRate = randomizedTriggerRate;
            return this;
        }

        /** See {@link EventReport#getSourceDebugKey()}} */
        public Builder setSourceDebugKey(UnsignedLong sourceDebugKey) {
            mBuilding.mSourceDebugKey = sourceDebugKey;
            return this;
        }

        /** See {@link EventReport#getTriggerDebugKey()}} */
        public Builder setTriggerDebugKey(UnsignedLong triggerDebugKey) {
            mBuilding.mTriggerDebugKey = triggerDebugKey;
            return this;
        }

        /** See {@link EventReport#getTriggerDebugKeys()}} */
        public Builder setTriggerDebugKeys(List<UnsignedLong> triggerDebugKeys) {
            mBuilding.mTriggerDebugKeys = triggerDebugKeys;
            return this;
        }

        /** See {@link EventReport#getSourceId()} */
        public Builder setSourceId(String sourceId) {
            mBuilding.mSourceId = sourceId;
            return this;
        }

        /** See {@link EventReport#getTriggerId()} */
        public Builder setTriggerId(String triggerId) {
            mBuilding.mTriggerId = triggerId;
            return this;
        }

        /**
         * set the summary bucket from input DB text encode summary bucket
         *
         * @param summaryBucket the string encoded summary bucket
         * @return builder
         */
        public Builder setTriggerSummaryBucket(@Nullable String summaryBucket) {
            if (summaryBucket == null || summaryBucket.isEmpty()) {
                mBuilding.mTriggerSummaryBucket = null;
                return this;
            }
            String[] numbers = summaryBucket.split(",");

            Long firstNumber = Long.parseLong(numbers[0].trim());
            Long secondNumber = Long.parseLong(numbers[1].trim());
            mBuilding.mTriggerSummaryBucket = new Pair<>(firstNumber, secondNumber);

            return this;
        }

        /** See {@link EventReport#getTriggerSummaryBucket()} */
        public Builder setTriggerSummaryBucket(@Nullable Pair<Long, Long> summaryBucket) {
            mBuilding.mTriggerSummaryBucket = summaryBucket;
            return this;
        }

        /** See {@link EventReport#getTriggerId()} */
        public Builder setTriggerValue(long triggerValue) {
            mBuilding.mTriggerValue = triggerValue;
            return this;
        }

        /** See {@link Source#getRegistrationOrigin()} ()} */
        @NonNull
        public Builder setRegistrationOrigin(Uri registrationOrigin) {
            mBuilding.mRegistrationOrigin = registrationOrigin;
            return this;
        }

        // TODO (b/285607306): cleanup since this doesn't just do "populateFromSourceAndTrigger"
        /** Populates fields using {@link Source}, {@link Trigger} and {@link EventTrigger}. */
        public Builder populateFromSourceAndTrigger(
                @NonNull Source source,
                @NonNull Trigger trigger,
                @NonNull EventTrigger eventTrigger,
                @NonNull Pair<UnsignedLong, UnsignedLong> debugKeyPair,
                @NonNull EventReportWindowCalcDelegate eventReportWindowCalcDelegate,
                @NonNull SourceNoiseHandler sourceNoiseHandler,
                List<Uri> eventReportDestinations) {
            mBuilding.mTriggerDedupKey = eventTrigger.getDedupKey();
            mBuilding.mTriggerTime = trigger.getTriggerTime();
            mBuilding.mSourceEventId = source.getEventId();
            mBuilding.mEnrollmentId = source.getEnrollmentId();
            mBuilding.mStatus = Status.PENDING;
            mBuilding.mAttributionDestinations = eventReportDestinations;
            mBuilding.mSourceType = source.getSourceType();
            mBuilding.mSourceDebugKey = debugKeyPair.first;
            mBuilding.mTriggerDebugKey = debugKeyPair.second;
            mBuilding.mDebugReportStatus = DebugReportStatus.NONE;
            if (mBuilding.mSourceDebugKey != null && mBuilding.mTriggerDebugKey != null) {
                mBuilding.mDebugReportStatus = DebugReportStatus.PENDING;
            }
            mBuilding.mSourceId = source.getId();
            mBuilding.mTriggerId = trigger.getId();
            mBuilding.mRegistrationOrigin = trigger.getRegistrationOrigin();
            mBuilding.mTriggerPriority = eventTrigger.getTriggerPriority();
            // truncate trigger data to 3-bit or 1-bit based on {@link Source.SourceType}
            mBuilding.mTriggerData = getTruncatedTriggerData(source, eventTrigger);
            mBuilding.mReportTime =
                    eventReportWindowCalcDelegate.getReportingTime(
                            source, trigger.getTriggerTime(), trigger.getDestinationType());
            mBuilding.mRandomizedTriggerRate =
                    sourceNoiseHandler.getRandomizedTriggerRate(source);
            return this;
        }

        /** Provides event report builder for flexible event-level reports */
        public Builder getForFlex(
                @NonNull Source source,
                @NonNull Trigger trigger,
                @NonNull AttributedTrigger attributedTrigger,
                long reportTime,
                @NonNull Pair<Long, Long> triggerSummaryBucket,
                @Nullable UnsignedLong sourceDebugKey,
                @NonNull List<UnsignedLong> debugKeys,
                double flipProbability,
                List<Uri> eventReportDestinations) {
            mBuilding.mTriggerTime = trigger.getTriggerTime();
            mBuilding.mSourceEventId = source.getEventId();
            mBuilding.mEnrollmentId = source.getEnrollmentId();
            mBuilding.mStatus = Status.PENDING;
            mBuilding.mAttributionDestinations = eventReportDestinations;
            mBuilding.mSourceType = source.getSourceType();
            mBuilding.mSourceDebugKey = sourceDebugKey;
            mBuilding.mTriggerDebugKeys = debugKeys;
            mBuilding.mDebugReportStatus = DebugReportStatus.NONE;
            if (mBuilding.mSourceDebugKey != null && debugKeys.size() > 0) {
                mBuilding.mDebugReportStatus = DebugReportStatus.PENDING;
            }
            mBuilding.mSourceId = source.getId();
            mBuilding.mTriggerId = trigger.getId();
            mBuilding.mRegistrationOrigin = trigger.getRegistrationOrigin();
            mBuilding.mRandomizedTriggerRate = flipProbability;
            mBuilding.mTriggerData = attributedTrigger.getTriggerData();
            mBuilding.mReportTime = reportTime;
            mBuilding.mTriggerSummaryBucket = triggerSummaryBucket;
            return this;
        }

        private UnsignedLong getTruncatedTriggerData(Source source, EventTrigger eventTrigger) {
            UnsignedLong triggerData = eventTrigger.getTriggerData();
            return triggerData.mod(source.getTriggerDataCardinality());
        }

        /**
         * Build the {@link EventReport}.
         */
        public EventReport build() {
            return mBuilding;
        }
    }
}
