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
import com.android.adservices.data.measurement.IMeasurementDao;
import com.android.adservices.errorlogging.ErrorLogUtil;
import com.android.adservices.service.Flags;
import com.android.adservices.service.measurement.KeyValueData;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.MeasurementReportsStats;
import com.android.internal.annotations.VisibleForTesting;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/** Class for handling debug reporting. */
public class DebugReportingJobHandler {

    private final DatastoreManager mDatastoreManager;
    private final Flags mFlags;
    private ReportingStatus.UploadMethod mUploadMethod;
    private AdServicesLogger mLogger;

    private Context mContext;

    @VisibleForTesting
    DebugReportingJobHandler(
            DatastoreManager datastoreManager,
            Flags flags,
            AdServicesLogger logger,
            Context context) {
        this(
                datastoreManager,
                flags,
                logger,
                ReportingStatus.UploadMethod.UNKNOWN,
                context);
    }

    DebugReportingJobHandler(
            DatastoreManager datastoreManager,
            Flags flags,
            AdServicesLogger logger,
            ReportingStatus.UploadMethod uploadMethod,
            Context context) {
        mDatastoreManager = datastoreManager;
        mFlags = flags;
        mLogger = logger;
        mUploadMethod = uploadMethod;
        mContext = context;
    }

    /** Finds all debug reports and attempts to upload them individually. */
    void performScheduledPendingReports() {
        Optional<List<String>> pendingDebugReports =
                mDatastoreManager.runInTransactionWithResult(IMeasurementDao::getDebugReportIds);
        if (!pendingDebugReports.isPresent()) {
            LoggerFactory.getMeasurementLogger().d("Pending Debug Reports not found");
            return;
        }

        List<String> pendingDebugReportIdsInWindow = pendingDebugReports.get();
        for (String debugReportId : pendingDebugReportIdsInWindow) {
            // If the job service's requirements specified at runtime are no longer met, the job
            // service will interrupt this thread.  If the thread has been interrupted, it will exit
            // early.
            if (Thread.currentThread().isInterrupted()) {
                LoggerFactory.getMeasurementLogger()
                        .d(
                                "DebugReportingJobHandler performScheduledPendingReports "
                                        + "thread interrupted, exiting early.");
                return;
            }

            ReportingStatus reportingStatus = new ReportingStatus();
            if (mUploadMethod != null) {
                reportingStatus.setUploadMethod(mUploadMethod);
            }
            @AdServicesStatusUtils.StatusCode
            int result = performReport(debugReportId, reportingStatus);
            if (result == AdServicesStatusUtils.STATUS_SUCCESS) {
                reportingStatus.setUploadStatus(ReportingStatus.UploadStatus.SUCCESS);
            } else {
                reportingStatus.setUploadStatus(ReportingStatus.UploadStatus.FAILURE);
                mDatastoreManager.runInTransaction(
                        (dao) -> {
                            int retryCount =
                                    dao.incrementAndGetReportingRetryCount(
                                            debugReportId,
                                            KeyValueData.DataType.DEBUG_REPORT_RETRY_COUNT);
                            reportingStatus.setRetryCount(retryCount);
                        });
            }
            logReportingStats(reportingStatus);
        }
    }

    /**
     * Perform reporting by finding the relevant {@link DebugReport} and making an HTTP POST request
     * to the specified report to URL with the report data as a JSON in the body.
     *
     * @param debugReportId for the datastore id of the {@link DebugReport}
     * @return success
     */
    int performReport(String debugReportId, ReportingStatus reportingStatus) {
        Optional<DebugReport> debugReportOpt =
                mDatastoreManager.runInTransactionWithResult(
                        (dao) -> dao.getDebugReport(debugReportId));
        if (!debugReportOpt.isPresent()) {
            LoggerFactory.getMeasurementLogger().d("Reading Scheduled Debug Report failed");
            reportingStatus.setReportType(ReportingStatus.ReportType.VERBOSE_DEBUG_UNKNOWN);
            reportingStatus.setFailureStatus(ReportingStatus.FailureStatus.REPORT_NOT_FOUND);
            return AdServicesStatusUtils.STATUS_IO_ERROR;
        }
        DebugReport debugReport = debugReportOpt.get();
        reportingStatus.setReportingDelay(
                System.currentTimeMillis() - debugReport.getInsertionTime());
        reportingStatus.setReportType(debugReport.getType());
        reportingStatus.setSourceRegistrant(getAppPackageName(debugReport));

        try {
            Uri reportingOrigin = debugReport.getRegistrationOrigin();
            JSONArray debugReportJsonPayload = createReportJsonPayload(debugReport);
            int returnCode = makeHttpPostRequest(reportingOrigin, debugReportJsonPayload);

            if (returnCode >= HttpURLConnection.HTTP_OK && returnCode <= 299) {
                boolean success =
                        mDatastoreManager.runInTransaction(
                                (dao) -> {
                                    dao.deleteDebugReport(debugReport.getId());
                                });
                if (success) {
                    return AdServicesStatusUtils.STATUS_SUCCESS;
                } else {
                    LoggerFactory.getMeasurementLogger().d("Deleting debug report failed");
                    reportingStatus.setFailureStatus(ReportingStatus.FailureStatus.DATASTORE);
                    return AdServicesStatusUtils.STATUS_IO_ERROR;
                }
            } else {
                LoggerFactory.getMeasurementLogger()
                        .d("Sending debug report failed with http error");
                reportingStatus.setFailureStatus(
                        ReportingStatus.FailureStatus.UNSUCCESSFUL_HTTP_RESPONSE_CODE);
                return AdServicesStatusUtils.STATUS_IO_ERROR;
            }
        } catch (IOException e) {
            LoggerFactory.getMeasurementLogger()
                    .d(e, "Network error occurred when attempting to deliver debug report.");
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REPORTING_NETWORK_ERROR,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
            reportingStatus.setFailureStatus(ReportingStatus.FailureStatus.NETWORK);
            // TODO(b/298330312): Change to defined error codes
            return AdServicesStatusUtils.STATUS_IO_ERROR;
        } catch (JSONException e) {
            LoggerFactory.getMeasurementLogger()
                    .d(e, "Serialization error occurred at debug report delivery.");
            // TODO(b/298330312): Change to defined error codes
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REPORTING_PARSING_ERROR,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
            reportingStatus.setFailureStatus(ReportingStatus.FailureStatus.SERIALIZATION_ERROR);
            if (mFlags.getMeasurementEnableReportDeletionOnUnrecoverableException()) {
                // Unrecoverable state - delete the report.
                mDatastoreManager.runInTransaction(dao -> dao.deleteDebugReport(debugReportId));
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
                    .e(e, "Unexpected exception occurred when attempting to deliver debug report.");
            // TODO(b/298330312): Change to defined error codes
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REPORTING_UNKNOWN_ERROR,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
            reportingStatus.setFailureStatus(ReportingStatus.FailureStatus.UNKNOWN);
            if (mFlags.getMeasurementEnableReportingJobsThrowUnaccountedException()
                    && ThreadLocalRandom.current().nextFloat()
                            < mFlags.getMeasurementThrowUnknownExceptionSamplingRate()) {
                throw e;
            }
            return AdServicesStatusUtils.STATUS_UNKNOWN_ERROR;
        }
    }

    /** Creates the JSON payload for the POST request from the DebugReport. */
    @VisibleForTesting
    JSONArray createReportJsonPayload(DebugReport debugReport) throws JSONException {
        JSONArray debugReportJsonPayload = new JSONArray();
        debugReportJsonPayload.put(debugReport.toPayloadJson());
        return debugReportJsonPayload;
    }

    /** Makes the POST request to the reporting URL. */
    @VisibleForTesting
    public int makeHttpPostRequest(Uri adTechDomain, JSONArray debugReportPayload)
            throws IOException {
        DebugReportSender debugReportSender = new DebugReportSender(mContext);
        return debugReportSender.sendReport(adTechDomain, debugReportPayload);
    }

    private String getAppPackageName(DebugReport debugReport) {
        if (!mFlags.getMeasurementEnableAppPackageNameLogging()) {
            return "";
        }
        Uri sourceRegistrant = debugReport.getRegistrant();
        if (sourceRegistrant == null) {
            LoggerFactory.getMeasurementLogger().d("Source registrant is null on debug report");
            return "";
        }
        return sourceRegistrant.toString();
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
