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

package com.android.adservices.service.measurement.reporting;



/** POJO for storing aggregate and event reporting status */
public class ReportingStatus {

    /** Enums are tied to the AdservicesMeasurementReportsUploaded atom */
    public enum ReportType {
        UNKNOWN(0),
        EVENT(1),
        AGGREGATE(2),
        DEBUG_EVENT(3),
        DEBUG_AGGREGATE(4),
        VERBOSE_DEBUG_SOURCE_DESTINATION_LIMIT(5),
        VERBOSE_DEBUG_SOURCE_NOISED(6),
        VERBOSE_DEBUG_SOURCE_STORAGE_LIMIT(7),
        VERBOSE_DEBUG_SOURCE_SUCCESS(8),
        VERBOSE_DEBUG_SOURCE_UNKNOWN_ERROR(9),
        VERBOSE_DEBUG_SOURCE_FLEXIBLE_EVENT_REPORT_VALUE_ERROR(10),
        VERBOSE_DEBUG_TRIGGER_AGGREGATE_DEDUPLICATED(11),
        VERBOSE_DEBUG_TRIGGER_AGGREGATE_INSUFFICIENT_BUDGET(12),
        VERBOSE_DEBUG_TRIGGER_AGGREGATE_NO_CONTRIBUTIONS(13),
        VERBOSE_DEBUG_TRIGGER_AGGREGATE_REPORT_WINDOW_PASSED(14),
        VERBOSE_DEBUG_TRIGGER_ATTRIBUTIONS_PER_SOURCE_DESTINATION_LIMIT(15),
        VERBOSE_DEBUG_TRIGGER_EVENT_DEDUPLICATED(16),
        VERBOSE_DEBUG_TRIGGER_EVENT_EXCESSIVE_REPORTS(17),
        VERBOSE_DEBUG_TRIGGER_EVENT_LOW_PRIORITY(18),
        VERBOSE_DEBUG_TRIGGER_EVENT_NO_MATCHING_CONFIGURATIONS(19),
        VERBOSE_DEBUG_TRIGGER_EVENT_NOISE(20),
        VERBOSE_DEBUG_TRIGGER_EVENT_REPORT_WINDOW_PASSED(21),
        VERBOSE_DEBUG_TRIGGER_NO_MATCHING_FILTER_DATA(22),
        VERBOSE_DEBUG_TRIGGER_NO_MATCHING_SOURCE(23),
        VERBOSE_DEBUG_TRIGGER_REPORTING_ORIGIN_LIMIT(24),
        VERBOSE_DEBUG_TRIGGER_EVENT_STORAGE_LIMIT(25),
        VERBOSE_DEBUG_TRIGGER_UNKNOWN_ERROR(26),
        VERBOSE_DEBUG_TRIGGER_AGGREGATE_STORAGE_LIMIT(27),
        VERBOSE_DEBUG_TRIGGER_AGGREGATE_EXCESSIVE_REPORTS(28),
        VERBOSE_DEBUG_TRIGGER_EVENT_REPORT_WINDOW_NOT_STARTED(29),
        VERBOSE_DEBUG_TRIGGER_EVENT_NO_MATCHING_TRIGGER_DATA(30),
        VERBOSE_DEBUG_UNKNOWN(9999);

        private final int mValue;

        ReportType(int value) {
            mValue = value;
        }

        public int getValue() {
            return mValue;
        }
    }

    public enum UploadStatus {
        UNKNOWN(0),
        SUCCESS(1),
        FAILURE(2);

        private final int mValue;

        UploadStatus(int value) {
            mValue = value;
        }

        public int getValue() {
            return mValue;
        }
    }

    public enum FailureStatus {
        UNKNOWN(0),
        ENROLLMENT_NOT_FOUND(1),
        NETWORK(2),
        DATASTORE(3),
        REPORT_NOT_PENDING(4),
        JOB_RETRY_LIMIT_REACHED(5),
        SERIALIZATION_ERROR(6),
        ENCRYPTION_ERROR(7),
        UNSUCCESSFUL_HTTP_RESPONSE_CODE(8),
        REPORT_NOT_FOUND(9);
        private final int mValue;

        FailureStatus(int value) {
            mValue = value;
        }

        public int getValue() {
            return mValue;
        }
    }

    public enum UploadMethod {
        UNKNOWN(0),
        REGULAR(1),
        FALLBACK(2);
        private final int mValue;

        UploadMethod(int value) {
            mValue = value;
        }

        public int getValue() {
            return mValue;
        }
    }

    private ReportType mReportType;
    private UploadStatus mUploadStatus;

    private FailureStatus mFailureStatus;

    private UploadMethod mUploadMethod;

    private long mReportingDelay;

    private String mSourceRegistrant;

    private int mRetryCount;

    public ReportingStatus() {
        mReportType = ReportType.UNKNOWN;
        mUploadStatus = UploadStatus.UNKNOWN;
        mFailureStatus = FailureStatus.UNKNOWN;
        mUploadMethod = UploadMethod.UNKNOWN;
        mReportingDelay = 0L;
        mSourceRegistrant = "";
    }

    /** Get the type of report that is being uploaded. */
    public ReportType getReportType() {
        return mReportType;
    }

    /** Set the type of report that is being uploaded. */
    public void setReportType(ReportType reportType) {
        mReportType = reportType;
    }

    /** set the type of report that is being uploaded from debug report type string. */
    public void setReportType(String reportType) {
        if (reportType.equals(DebugReportApi.Type.SOURCE_DESTINATION_LIMIT)) {
            mReportType = ReportType.VERBOSE_DEBUG_SOURCE_DESTINATION_LIMIT;
        } else if (reportType.equals(DebugReportApi.Type.SOURCE_NOISED)) {
            mReportType = ReportType.VERBOSE_DEBUG_SOURCE_NOISED;
        } else if (reportType.equals(DebugReportApi.Type.SOURCE_STORAGE_LIMIT)) {
            mReportType = ReportType.VERBOSE_DEBUG_SOURCE_STORAGE_LIMIT;
        } else if (reportType.equals(DebugReportApi.Type.SOURCE_SUCCESS)) {
            mReportType = ReportType.VERBOSE_DEBUG_SOURCE_SUCCESS;
        } else if (reportType.equals(DebugReportApi.Type.SOURCE_UNKNOWN_ERROR)) {
            mReportType = ReportType.VERBOSE_DEBUG_SOURCE_UNKNOWN_ERROR;
        } else if (reportType.equals(
                DebugReportApi.Type.SOURCE_FLEXIBLE_EVENT_REPORT_VALUE_ERROR)) {
            mReportType = ReportType.VERBOSE_DEBUG_SOURCE_FLEXIBLE_EVENT_REPORT_VALUE_ERROR;
        } else if (reportType.equals(DebugReportApi.Type.TRIGGER_AGGREGATE_DEDUPLICATED)) {
            mReportType = ReportType.VERBOSE_DEBUG_TRIGGER_AGGREGATE_DEDUPLICATED;
        } else if (reportType.equals(DebugReportApi.Type.TRIGGER_AGGREGATE_INSUFFICIENT_BUDGET)) {
            mReportType = ReportType.VERBOSE_DEBUG_TRIGGER_AGGREGATE_INSUFFICIENT_BUDGET;
        } else if (reportType.equals(DebugReportApi.Type.TRIGGER_AGGREGATE_NO_CONTRIBUTIONS)) {
            mReportType = ReportType.VERBOSE_DEBUG_TRIGGER_AGGREGATE_NO_CONTRIBUTIONS;
        } else if (reportType.equals(DebugReportApi.Type.TRIGGER_AGGREGATE_REPORT_WINDOW_PASSED)) {
            mReportType = ReportType.VERBOSE_DEBUG_TRIGGER_AGGREGATE_REPORT_WINDOW_PASSED;
        } else if (reportType.equals(
                DebugReportApi.Type.TRIGGER_ATTRIBUTIONS_PER_SOURCE_DESTINATION_LIMIT)) {
            mReportType = ReportType.VERBOSE_DEBUG_SOURCE_DESTINATION_LIMIT;
        } else if (reportType.equals(DebugReportApi.Type.TRIGGER_EVENT_DEDUPLICATED)) {
            mReportType = ReportType.VERBOSE_DEBUG_TRIGGER_EVENT_DEDUPLICATED;
        } else if (reportType.equals(DebugReportApi.Type.TRIGGER_EVENT_EXCESSIVE_REPORTS)) {
            mReportType = ReportType.VERBOSE_DEBUG_TRIGGER_EVENT_EXCESSIVE_REPORTS;
        } else if (reportType.equals(DebugReportApi.Type.TRIGGER_EVENT_LOW_PRIORITY)) {
            mReportType = ReportType.VERBOSE_DEBUG_TRIGGER_EVENT_LOW_PRIORITY;
        } else if (reportType.equals(
                DebugReportApi.Type.TRIGGER_EVENT_NO_MATCHING_CONFIGURATIONS)) {
            mReportType = ReportType.VERBOSE_DEBUG_TRIGGER_EVENT_NO_MATCHING_CONFIGURATIONS;
        } else if (reportType.equals(DebugReportApi.Type.TRIGGER_EVENT_NOISE)) {
            mReportType = ReportType.VERBOSE_DEBUG_TRIGGER_EVENT_NOISE;
        } else if (reportType.equals(DebugReportApi.Type.TRIGGER_EVENT_REPORT_WINDOW_PASSED)) {
            mReportType = ReportType.VERBOSE_DEBUG_TRIGGER_EVENT_REPORT_WINDOW_PASSED;
        } else if (reportType.equals(DebugReportApi.Type.TRIGGER_NO_MATCHING_FILTER_DATA)) {
            mReportType = ReportType.VERBOSE_DEBUG_TRIGGER_NO_MATCHING_FILTER_DATA;
        } else if (reportType.equals(DebugReportApi.Type.TRIGGER_NO_MATCHING_SOURCE)) {
            mReportType = ReportType.VERBOSE_DEBUG_TRIGGER_NO_MATCHING_SOURCE;
        } else if (reportType.equals(DebugReportApi.Type.TRIGGER_REPORTING_ORIGIN_LIMIT)) {
            mReportType = ReportType.VERBOSE_DEBUG_TRIGGER_REPORTING_ORIGIN_LIMIT;
        } else if (reportType.equals(DebugReportApi.Type.TRIGGER_EVENT_STORAGE_LIMIT)) {
            mReportType = ReportType.VERBOSE_DEBUG_TRIGGER_EVENT_STORAGE_LIMIT;
        } else if (reportType.equals(DebugReportApi.Type.TRIGGER_UNKNOWN_ERROR)) {
            mReportType = ReportType.VERBOSE_DEBUG_TRIGGER_UNKNOWN_ERROR;
        } else if (reportType.equals(DebugReportApi.Type.TRIGGER_AGGREGATE_STORAGE_LIMIT)) {
            mReportType = ReportType.VERBOSE_DEBUG_TRIGGER_AGGREGATE_STORAGE_LIMIT;
        } else if (reportType.equals(DebugReportApi.Type.TRIGGER_AGGREGATE_EXCESSIVE_REPORTS)) {
            mReportType = ReportType.VERBOSE_DEBUG_TRIGGER_AGGREGATE_EXCESSIVE_REPORTS;
        } else if (reportType.equals(DebugReportApi.Type.TRIGGER_EVENT_REPORT_WINDOW_NOT_STARTED)) {
            mReportType = ReportType.VERBOSE_DEBUG_TRIGGER_EVENT_REPORT_WINDOW_NOT_STARTED;
        } else if (reportType.equals(DebugReportApi.Type.TRIGGER_EVENT_NO_MATCHING_TRIGGER_DATA)) {
            mReportType = ReportType.VERBOSE_DEBUG_TRIGGER_EVENT_NO_MATCHING_TRIGGER_DATA;
        } else {
            mReportType = ReportType.VERBOSE_DEBUG_UNKNOWN;
        }
    }

    /** Get the upload status of reporting. */
    public UploadStatus getUploadStatus() {
        return mUploadStatus;
    }

    /** Set upload status of reporting. */
    public void setUploadStatus(UploadStatus status) {
        mUploadStatus = status;
    }

    /** Get the failure status of reporting. */
    public FailureStatus getFailureStatus() {
        return mFailureStatus;
    }

    /** Set failure status of reporting. */
    public void setFailureStatus(FailureStatus status) {
        mFailureStatus = status;
    }

    /** Get the upload method of reporting. */
    public UploadMethod getUploadMethod() {
        return mUploadMethod;
    }

    /** Set upload method of reporting. */
    public void setUploadMethod(UploadMethod method) {
        mUploadMethod = method;
    }

    /** Get registration delay. */
    public long getReportingDelay() {
        return mReportingDelay;
    }

    /** Set registration delay. */
    public void setReportingDelay(long reportingDelay) {
        mReportingDelay = reportingDelay;
    }

    /** Get source registrant. */
    public String getSourceRegistrant() {
        return mSourceRegistrant;
    }

    /** Set source registrant. */
    public void setSourceRegistrant(String sourceRegistrant) {
        mSourceRegistrant = sourceRegistrant;
    }

    /** Get retry count. */
    public int getRetryCount() {
        return mRetryCount;
    }

    /** Set retry count. */
    public void setRetryCount(int retryCount) {
        mRetryCount = retryCount;
    }
}
