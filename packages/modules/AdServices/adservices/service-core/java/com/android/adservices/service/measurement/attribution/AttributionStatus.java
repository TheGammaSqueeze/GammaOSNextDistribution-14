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

package com.android.adservices.service.measurement.attribution;

import com.android.adservices.service.measurement.EventSurfaceType;
import com.android.adservices.service.measurement.Source;
import com.android.adservices.service.measurement.Trigger;



/** POJO for storing attribution status */
public class AttributionStatus {
    /** Enums are tied to the AdservicesMeasurementAttributionStatus atom */
    public enum SourceType {
        UNKNOWN(0),
        /**
         * @deprecated use {@link AttributionStatus.SourceType.VIEW} instead.
         */
        @Deprecated
        EVENT(1),
        /**
         * @deprecated use {@link AttributionStatus.SourceType.CLICK} instead.
         */
        @Deprecated
        NAVIGATION(2),
        VIEW(3),
        CLICK(4);
        private final int mValue;

        SourceType(int value) {
            mValue = value;
        }

        public int getValue() {
            return mValue;
        }
    }

    public enum AttributionSurface {
        UNKNOWN(0),
        APP_APP(1),
        APP_WEB(2),
        WEB_APP(3),
        WEB_WEB(4);
        private final int mValue;

        AttributionSurface(int value) {
            mValue = value;
        }

        public int getValue() {
            return mValue;
        }
    }

    public enum AttributionResult {
        UNKNOWN(0),
        SUCCESS(1),
        NOT_ATTRIBUTED(2),
        AGGREGATE_REPORT_GENERATED_SUCCESS_STATUS(3),
        EVENT_REPORT_GENERATED_SUCCESS_STATUS(4),
        AGGREGATE_AND_EVENT_REPORTS_GENERATED_SUCCESS_STATUS(5);
        private final int mValue;

        AttributionResult(int value) {
            mValue = value;
        }

        public int getValue() {
            return mValue;
        }
    }

    public enum FailureType {
        UNKNOWN(0),
        TRIGGER_IGNORED(1),
        TRIGGER_ALREADY_ATTRIBUTED(2),
        TRIGGER_MARKED_FOR_DELETION(3),
        NO_MATCHING_SOURCE(4),
        TOP_LEVEL_FILTER_MATCH_FAILURE(5),
        RATE_LIMIT_EXCEEDED(6),
        NO_REPORTS_GENERATED(7),
        JOB_RETRY_LIMIT_EXCEEDED(8),
        TRIGGER_NOT_FOUND(9);
        private final int mValue;

        FailureType(int value) {
            mValue = value;
        }

        public int getValue() {
            return mValue;
        }
    }

    private SourceType mSourceType;
    private AttributionSurface mAttributionSurface;
    private AttributionResult mAttributionResult;
    private FailureType mFailureType;
    private boolean mIsSourceDerived;
    private boolean mIsInstallAttribution;
    private long mAttributionDelay;
    private String mSourceRegistrant;
    private int mAggregateReportCount;
    private int mAggregateDebugReportCount;
    private int mEventReportCount;
    private int mEventDebugReportCount;

    public AttributionStatus() {
        mSourceType = SourceType.UNKNOWN;
        mAttributionSurface = AttributionSurface.UNKNOWN;
        mAttributionResult = AttributionResult.UNKNOWN;
        mFailureType = FailureType.UNKNOWN;
        mIsSourceDerived = false;
        mIsInstallAttribution = false;
        mSourceRegistrant = "";
        mAttributionDelay = 0L;
    }

    /** Get the type of the source that is getting attributed. */
    public SourceType getSourceType() {
        return mSourceType;
    }

    /** Set the type of the source that is getting attributed. */
    public void setSourceType(SourceType type) {
        mSourceType = type;
    }

    /** Set the type of the source that is getting attributed using Source.SourceType. */
    public void setSourceType(Source.SourceType type) {
        if (type == Source.SourceType.EVENT) {
            setSourceType(SourceType.VIEW);
        } else if (type == Source.SourceType.NAVIGATION) {
            setSourceType(SourceType.CLICK);
        }
    }

    /** Get the surface type for the attributed source and trigger. */
    public AttributionSurface getAttributionSurface() {
        return mAttributionSurface;
    }

    /** Set the surface type for the attributed source and trigger. */
    public void setAttributionSurface(AttributionSurface attributionSurface) {
        mAttributionSurface = attributionSurface;
    }

    /** Set the surface type for the attributed source and trigger using Source and Trigger. */
    public void setSurfaceTypeFromSourceAndTrigger(Source source, Trigger trigger) {
        if (source.getPublisherType() == EventSurfaceType.APP
                && trigger.getDestinationType() == EventSurfaceType.APP) {
            setAttributionSurface(AttributionSurface.APP_APP);
        } else if (source.getPublisherType() == EventSurfaceType.APP
                && trigger.getDestinationType() == EventSurfaceType.WEB) {
            setAttributionSurface(AttributionSurface.APP_WEB);
        } else if (source.getPublisherType() == EventSurfaceType.WEB
                && trigger.getDestinationType() == EventSurfaceType.APP) {
            setAttributionSurface(AttributionSurface.WEB_APP);
        } else if (source.getPublisherType() == EventSurfaceType.WEB
                && trigger.getDestinationType() == EventSurfaceType.WEB) {
            setAttributionSurface(AttributionSurface.WEB_WEB);
        }
    }

    /** Get the result of attribution. */
    public AttributionResult getAttributionResult() {
        return mAttributionResult;
    }

    /** Set the result of attribution. */
    public void setAttributionResult(AttributionResult attributionResult) {
        mAttributionResult = attributionResult;
    }

    /** Set the result of attribution base on the type of generated reports. */
    public void setAttributionResult(
            boolean aggregateReportGenerated, boolean eventReportGenerated) {
        if (aggregateReportGenerated && !eventReportGenerated) {
            mAttributionResult = AttributionResult.AGGREGATE_REPORT_GENERATED_SUCCESS_STATUS;
        } else if (!aggregateReportGenerated && eventReportGenerated) {
            mAttributionResult = AttributionResult.EVENT_REPORT_GENERATED_SUCCESS_STATUS;
        } else if (aggregateReportGenerated && eventReportGenerated) {
            mAttributionResult =
                    AttributionResult.AGGREGATE_AND_EVENT_REPORTS_GENERATED_SUCCESS_STATUS;
        }
    }

    /** Get failure type. */
    public FailureType getFailureType() {
        return mFailureType;
    }

    /** Set failure type. */
    public void setFailureType(FailureType failureType) {
        mFailureType = failureType;
    }

    /** Set failure type using Trigger.Status. */
    public void setFailureTypeFromTriggerStatus(int triggerStatus) {
        if (triggerStatus == Trigger.Status.IGNORED) {
            setFailureType(FailureType.TRIGGER_IGNORED);
        } else if (triggerStatus == Trigger.Status.ATTRIBUTED) {
            setFailureType(FailureType.TRIGGER_ALREADY_ATTRIBUTED);
        } else if (triggerStatus == Trigger.Status.MARKED_TO_DELETE) {
            setFailureType(FailureType.TRIGGER_MARKED_FOR_DELETION);
        }
    }

    /** See if source is derived. */
    public boolean isSourceDerived() {
        return mIsSourceDerived;
    }

    /** Set source derived status */
    public void setSourceDerived(boolean isSourceDerived) {
        mIsSourceDerived = isSourceDerived;
    }

    /** See if attribution is an install attribution */
    public boolean isInstallAttribution() {
        return mIsInstallAttribution;
    }

    /** Set install attribution status */
    public void setInstallAttribution(boolean installAttribution) {
        mIsInstallAttribution = installAttribution;
    }

    /** Get attribution delay. */
    public long getAttributionDelay() {
        return mAttributionDelay;
    }

    /** Set attribution delay. */
    public void setAttributionDelay(long attributionDelay) {
        mAttributionDelay = attributionDelay;
    }

    /** Get source registrant. */
    public String getSourceRegistrant() {
        return mSourceRegistrant;
    }

    /** Set source registrant. */
    public void setSourceRegistrant(String sourceRegistrant) {
        mSourceRegistrant = sourceRegistrant;
    }

    /** Get aggregate report count. */
    public int getAggregateReportCount() {
        return mAggregateReportCount;
    }

    /** Set aggregate report count. */
    public void setAggregateReportCount(int aggregateReportCount) {
        mAggregateReportCount = aggregateReportCount;
    }

    /** Get aggregate debug report count. */
    public int getAggregateDebugReportCount() {
        return mAggregateDebugReportCount;
    }

    /** Set aggregate debug report count. */
    public void setAggregateDebugReportCount(int aggregateDebugReportCount) {
        mAggregateDebugReportCount = aggregateDebugReportCount;
    }

    /** Get event report count. */
    public int getEventReportCount() {
        return mEventReportCount;
    }

    /** Set event report count. */
    public void setEventReportCount(int eventReportCount) {
        mEventReportCount = eventReportCount;
    }

    /** Get evenet debug report count. */
    public int getEventDebugReportCount() {
        return mEventDebugReportCount;
    }

    /** Set event debug report count. */
    public void setEventDebugReportCount(int eventDebugReportCount) {
        mEventDebugReportCount = eventDebugReportCount;
    }
}
