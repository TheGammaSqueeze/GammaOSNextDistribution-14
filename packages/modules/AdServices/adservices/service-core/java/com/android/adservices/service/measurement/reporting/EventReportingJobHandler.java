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

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REPORTING_NETWORK_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REPORTING_PARSING_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REPORTING_UNKNOWN_ERROR;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_MESUREMENT_REPORTS_UPLOADED;

import android.adservices.common.AdServicesStatusUtils;
import android.content.Context;
import android.net.Uri;

import com.android.adservices.LoggerFactory;
import com.android.adservices.data.measurement.DatastoreManager;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.Flags;
import com.android.adservices.service.measurement.EventReport;
import com.android.adservices.service.measurement.KeyValueData;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.AdServicesLoggerImpl;
import com.android.adservices.service.stats.MeasurementReportsStats;
import com.android.internal.annotations.VisibleForTesting;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Class for handling event level reporting.
 */
public class EventReportingJobHandler {

    private final DatastoreManager mDatastoreManager;
    private boolean mIsDebugInstance;
    private final Flags mFlags;
    private final AdServicesLogger mLogger;
    private ReportingStatus.ReportType mReportType;
    private ReportingStatus.UploadMethod mUploadMethod;

    private Context mContext;

    @VisibleForTesting
    EventReportingJobHandler(
            DatastoreManager datastoreManager,
            Flags flags,
            Context context) {
        this(datastoreManager, flags, AdServicesLoggerImpl.getInstance(), context);
    }

    EventReportingJobHandler(
            DatastoreManager datastoreManager,
            Flags flags,
            AdServicesLogger logger,
            ReportingStatus.ReportType reportType,
            ReportingStatus.UploadMethod uploadMethod,
            Context context) {
        mDatastoreManager = datastoreManager;
        mFlags = flags;
        mLogger = logger;
        mReportType = reportType;
        mUploadMethod = uploadMethod;
        mContext = context;
    }

    @VisibleForTesting
    EventReportingJobHandler(
            DatastoreManager datastoreManager,
            Flags flags,
            AdServicesLogger logger,
            Context context) {
        mDatastoreManager = datastoreManager;
        mFlags = flags;
        mLogger = logger;
        mContext = context;
    }

    /**
     * Set isDebugInstance
     *
     * @param isDebugInstance indicates a debug event report
     * @return the instance of EventReportingJobHandler
     */
    public EventReportingJobHandler setIsDebugInstance(boolean isDebugInstance) {
        mIsDebugInstance = isDebugInstance;
        return this;
    }

    /**
     * Finds all reports within the given window that have a status {@link
     * EventReport.Status#PENDING} or {@link EventReport.DebugReportStatus#PENDING} based on
     * mIsDebugReport and attempts to upload them individually.
     *
     * @param windowStartTime Start time of the search window
     * @param windowEndTime End time of the search window
     * @return always return true to signal to JobScheduler that the task is done.
     */
    synchronized boolean performScheduledPendingReportsInWindow(
            long windowStartTime, long windowEndTime) {
        Optional<List<String>> pendingEventReportsInWindowOpt =
                mDatastoreManager.runInTransactionWithResult(
                        (dao) -> {
                            if (mIsDebugInstance) {
                                return dao.getPendingDebugEventReportIds();
                            } else {
                                return dao.getPendingEventReportIdsInWindow(
                                        windowStartTime, windowEndTime);
                            }
                        });
        if (!pendingEventReportsInWindowOpt.isPresent()) {
            // Failure during event report retrieval
            return true;
        }

        List<String> pendingEventReportIdsInWindow = pendingEventReportsInWindowOpt.get();
        for (String eventReportId : pendingEventReportIdsInWindow) {

            // If the job service's requirements specified at runtime are no longer met, the job
            // service will interrupt this thread.  If the thread has been interrupted, it will exit
            // early.
            if (Thread.currentThread().isInterrupted()) {
                LoggerFactory.getMeasurementLogger()
                        .d(
                                "EventReportingJobHandler performScheduledPendingReports "
                                        + "thread interrupted, exiting early.");
                return true;
            }

            // TODO: Use result to track rate of success vs retry vs failure
            ReportingStatus reportingStatus = new ReportingStatus();
            if (mReportType != null) {
                reportingStatus.setReportType(mReportType);
            }
            if (mUploadMethod != null) {
                reportingStatus.setUploadMethod(mUploadMethod);
            }
            @AdServicesStatusUtils.StatusCode
            int result = performReport(eventReportId, reportingStatus);

            if (result == AdServicesStatusUtils.STATUS_SUCCESS) {
                reportingStatus.setUploadStatus(ReportingStatus.UploadStatus.SUCCESS);
            } else {
                reportingStatus.setUploadStatus(ReportingStatus.UploadStatus.FAILURE);
                mDatastoreManager.runInTransaction(
                        (dao) -> {
                            int retryCount =
                                    dao.incrementAndGetReportingRetryCount(
                                            eventReportId,
                                            mIsDebugInstance
                                                    ? KeyValueData.DataType
                                                            .DEBUG_EVENT_REPORT_RETRY_COUNT
                                                    : KeyValueData.DataType
                                                            .EVENT_REPORT_RETRY_COUNT);
                            reportingStatus.setRetryCount(retryCount);
                        });
            }
            logReportingStats(reportingStatus);
        }
        return true;
    }

    private String getAppPackageName(EventReport eventReport) {
        if (!mFlags.getMeasurementEnableAppPackageNameLogging()) {
            return "";
        }
        if (eventReport.getSourceId() == null) {
            LoggerFactory.getMeasurementLogger().d("SourceId is null on event report.");
            return "";
        }
        Optional<String> sourceRegistrant =
                mDatastoreManager.runInTransactionWithResult(
                        (dao) -> dao.getSourceRegistrant(eventReport.getSourceId()));
        if (!sourceRegistrant.isPresent()) {
            LoggerFactory.getMeasurementLogger().d("Source registrant not found");
            return "";
        }
        return sourceRegistrant.get();
    }

    /**
     * Perform reporting by finding the relevant {@link EventReport} and making an HTTP POST request
     * to the specified report to URL with the report data as a JSON in the body.
     *
     * @param eventReportId for the datastore id of the {@link EventReport}
     * @return success
     */
    synchronized int performReport(String eventReportId, ReportingStatus reportingStatus) {
        Optional<EventReport> eventReportOpt =
                mDatastoreManager.runInTransactionWithResult((dao)
                        -> dao.getEventReport(eventReportId));
        if (!eventReportOpt.isPresent()) {
            LoggerFactory.getMeasurementLogger().d("Event report not found");
            reportingStatus.setFailureStatus(ReportingStatus.FailureStatus.REPORT_NOT_FOUND);
            return AdServicesStatusUtils.STATUS_IO_ERROR;
        }
        EventReport eventReport = eventReportOpt.get();
        reportingStatus.setReportingDelay(System.currentTimeMillis() - eventReport.getReportTime());
        reportingStatus.setSourceRegistrant(getAppPackageName(eventReport));
        if (mIsDebugInstance
                && eventReport.getDebugReportStatus() != EventReport.DebugReportStatus.PENDING) {
            LoggerFactory.getMeasurementLogger().d("debugging status is not pending");
            reportingStatus.setFailureStatus(ReportingStatus.FailureStatus.REPORT_NOT_PENDING);
            return AdServicesStatusUtils.STATUS_INVALID_ARGUMENT;
        }
        if (!mIsDebugInstance && eventReport.getStatus() != EventReport.Status.PENDING) {
            LoggerFactory.getMeasurementLogger().d("event report status is not pending");
            reportingStatus.setFailureStatus(ReportingStatus.FailureStatus.REPORT_NOT_PENDING);
            return AdServicesStatusUtils.STATUS_INVALID_ARGUMENT;
        }
        try {
            Uri reportingOrigin = eventReport.getRegistrationOrigin();
            JSONObject eventReportJsonPayload = createReportJsonPayload(eventReport);
            int returnCode = makeHttpPostRequest(reportingOrigin, eventReportJsonPayload);

            if (returnCode >= HttpURLConnection.HTTP_OK
                    && returnCode <= 299) {
                boolean success =
                        mDatastoreManager.runInTransaction(
                                (dao) -> {
                                    if (mIsDebugInstance) {
                                        dao.markEventDebugReportDelivered(eventReportId);
                                    } else {
                                        dao.markEventReportStatus(
                                                eventReportId, EventReport.Status.DELIVERED);
                                    }
                                });

                if (success) {
                    return AdServicesStatusUtils.STATUS_SUCCESS;
                } else {
                    reportingStatus.setFailureStatus(ReportingStatus.FailureStatus.DATASTORE);
                    return AdServicesStatusUtils.STATUS_IO_ERROR;
                }
            } else {
                reportingStatus.setFailureStatus(
                        ReportingStatus.FailureStatus.UNSUCCESSFUL_HTTP_RESPONSE_CODE);
                // TODO: Determine behavior for other response codes?
                return AdServicesStatusUtils.STATUS_IO_ERROR;
            }
        } catch (IOException e) {
            LoggerFactory.getMeasurementLogger()
                    .d(e, "Network error occurred when attempting to deliver event report.");
            reportingStatus.setFailureStatus(ReportingStatus.FailureStatus.NETWORK);
            // TODO(b/298330312): Change to defined error codes
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REPORTING_NETWORK_ERROR,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
            return AdServicesStatusUtils.STATUS_IO_ERROR;
        } catch (JSONException e) {
            LoggerFactory.getMeasurementLogger()
                    .d(e, "Serialization error occurred at event report delivery.");
            // TODO(b/298330312): Indicate serialization error
            reportingStatus.setFailureStatus(ReportingStatus.FailureStatus.SERIALIZATION_ERROR);
            // TODO(b/298330312): Change to defined error codes
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REPORTING_PARSING_ERROR,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
            if (mFlags.getMeasurementEnableReportDeletionOnUnrecoverableException()) {
                // Unrecoverable state - delete the report.
                mDatastoreManager.runInTransaction(
                        dao ->
                                dao.markEventReportStatus(
                                        eventReportId, EventReport.Status.MARKED_TO_DELETE));
            }

            if (mFlags.getMeasurementEnableReportingJobsThrowJsonException()
                    && ThreadLocalRandom.current().nextFloat()
                            < mFlags.getMeasurementThrowUnknownExceptionSamplingRate()) {
                // JSONException is unexpected.
                throw new IllegalStateException(
                        "Serialization error occurred at event report delivery", e);
            }
            return AdServicesStatusUtils.STATUS_UNKNOWN_ERROR;
        } catch (Exception e) {
            LoggerFactory.getMeasurementLogger()
                    .e(e, "Unexpected exception occurred when attempting to deliver event report.");
            reportingStatus.setFailureStatus(ReportingStatus.FailureStatus.UNKNOWN);
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REPORTING_UNKNOWN_ERROR,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
            if (mFlags.getMeasurementEnableReportingJobsThrowUnaccountedException()
                    && ThreadLocalRandom.current().nextFloat()
                            < mFlags.getMeasurementThrowUnknownExceptionSamplingRate()) {
                throw e;
            }
            return AdServicesStatusUtils.STATUS_UNKNOWN_ERROR;
        }
    }

    /**
     * Creates the JSON payload for the POST request from the EventReport.
     */
    @VisibleForTesting
    JSONObject createReportJsonPayload(EventReport eventReport) throws JSONException {
        return new EventReportPayload.Builder()
                .setReportId(eventReport.getId())
                .setSourceEventId(eventReport.getSourceEventId())
                .setAttributionDestination(eventReport.getAttributionDestinations())
                .setScheduledReportTime(
                        String.valueOf(
                                TimeUnit.MILLISECONDS.toSeconds(eventReport.getReportTime())))
                .setTriggerData(eventReport.getTriggerData())
                .setSourceType(eventReport.getSourceType().getValue())
                .setRandomizedTriggerRate(eventReport.getRandomizedTriggerRate())
                .setSourceDebugKey(eventReport.getSourceDebugKey())
                .setTriggerDebugKey(eventReport.getTriggerDebugKey())
                .setTriggerDebugKeys(eventReport.getTriggerDebugKeys())
                .setTriggerSummaryBucket(eventReport.getTriggerSummaryBucket())
                .build()
                .toJson();
    }

    /**
     * Makes the POST request to the reporting URL.
     */
    @VisibleForTesting
    public int makeHttpPostRequest(Uri adTechDomain, JSONObject eventReportPayload)
            throws IOException {
        EventReportSender eventReportSender = new EventReportSender(mIsDebugInstance, mContext);
        return eventReportSender.sendReport(adTechDomain, eventReportPayload);
    }

    private void logReportingStats(ReportingStatus reportingStatus) {
        mLogger.logMeasurementReports(
                new MeasurementReportsStats.Builder()
                        .setCode(AD_SERVICES_MESUREMENT_REPORTS_UPLOADED)
                        .setType(reportingStatus.getReportType().getValue())
                        .setResultCode(reportingStatus.getUploadStatus().getValue())
                        .setFailureType(reportingStatus.getFailureStatus().getValue())
                        .setUploadMethod(reportingStatus.getUploadMethod().getValue())
                        .setReportingDelay(reportingStatus.getReportingDelay())
                        .setSourceRegistrant(reportingStatus.getSourceRegistrant())
                        .setRetryCount(reportingStatus.getRetryCount())
                        .build());
    }
}
