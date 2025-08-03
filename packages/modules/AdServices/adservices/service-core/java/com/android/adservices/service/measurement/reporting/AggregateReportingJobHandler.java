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

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REPORTING_ENCRYPTION_ERROR;
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
import com.android.adservices.service.exception.CryptoException;
import com.android.adservices.service.measurement.KeyValueData;
import com.android.adservices.service.measurement.aggregation.AggregateEncryptionKey;
import com.android.adservices.service.measurement.aggregation.AggregateEncryptionKeyManager;
import com.android.adservices.service.measurement.aggregation.AggregateReport;
import com.android.adservices.service.stats.AdServicesLogger;
import com.android.adservices.service.stats.MeasurementReportsStats;
import com.android.internal.annotations.VisibleForTesting;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Class for handling aggregate reporting.
 */

public class AggregateReportingJobHandler {

    private final DatastoreManager mDatastoreManager;
    private final AggregateEncryptionKeyManager mAggregateEncryptionKeyManager;
    private boolean mIsDebugInstance;
    private final Flags mFlags;
    private ReportingStatus.ReportType mReportType;
    private ReportingStatus.UploadMethod mUploadMethod;
    private final AdServicesLogger mLogger;

    private final Context mContext;

    AggregateReportingJobHandler(
            DatastoreManager datastoreManager,
            AggregateEncryptionKeyManager aggregateEncryptionKeyManager,
            Flags flags,
            AdServicesLogger logger,
            ReportingStatus.ReportType reportType,
            ReportingStatus.UploadMethod uploadMethod,
            Context context) {
        mDatastoreManager = datastoreManager;
        mAggregateEncryptionKeyManager = aggregateEncryptionKeyManager;
        mFlags = flags;
        mLogger = logger;
        mReportType = reportType;
        mUploadMethod = uploadMethod;
        mContext = context;
    }

    @VisibleForTesting
    AggregateReportingJobHandler(
            DatastoreManager datastoreManager,
            AggregateEncryptionKeyManager aggregateEncryptionKeyManager,
            Flags flags,
            AdServicesLogger logger,
            Context context) {
        this(
                datastoreManager,
                aggregateEncryptionKeyManager,
                flags,
                logger,
                ReportingStatus.ReportType.UNKNOWN,
                ReportingStatus.UploadMethod.UNKNOWN,
                context);
    }

    /**
     * Set isDebugInstance
     *
     * @param isDebugInstance indicates a debug aggregate report
     * @return the instance of AggregateReportingJobHandler
     */
    public AggregateReportingJobHandler setIsDebugInstance(boolean isDebugInstance) {
        mIsDebugInstance = isDebugInstance;
        return this;
    }

    /**
     * Finds all aggregate reports within the given window that have a status {@link
     * AggregateReport.Status#PENDING} or {@link AggregateReport.DebugReportStatus#PENDING} based on
     * mIsDebugReport and attempts to upload them individually.
     *
     * @param windowStartTime Start time of the search window
     * @param windowEndTime End time of the search window
     * @return always return true to signal to JobScheduler that the task is done.
     */
    synchronized boolean performScheduledPendingReportsInWindow(
            long windowStartTime, long windowEndTime) {
        Optional<Map<String, List<String>>> pendingAggregateReportsInWindowOpt =
                mDatastoreManager.runInTransactionWithResult(
                        (dao) -> {
                            if (mIsDebugInstance) {
                                return dao.getPendingAggregateDebugReportIdsByCoordinator();
                            } else {
                                return dao.getPendingAggregateReportIdsByCoordinatorInWindow(
                                        windowStartTime, windowEndTime);
                            }
                        });
        if (!pendingAggregateReportsInWindowOpt.isPresent()) {
            // Failure during aggregate report retrieval
            return true;
        }

        Map<String, List<String>> idsByCoordinator = pendingAggregateReportsInWindowOpt.get();

        for (Map.Entry<String, List<String>> entrySet : idsByCoordinator.entrySet()) {
            String coordinator = entrySet.getKey();
            List<String> reportIds = entrySet.getValue();
            List<AggregateEncryptionKey> keys =
                    mAggregateEncryptionKeyManager.getAggregateEncryptionKeys(
                            Uri.parse(coordinator), reportIds.size());

            if (keys.size() == reportIds.size()) {
                for (int i = 0; i < reportIds.size(); i++) {
                    // If the job service's requirements specified at runtime are no longer met, the
                    // job service will interrupt this thread.  If the thread has been interrupted,
                    // it will exit early.
                    if (Thread.currentThread().isInterrupted()) {
                        LoggerFactory.getMeasurementLogger()
                                .d(
                                        "AggregateReportingJobHandler"
                                            + " performScheduledPendingReports thread interrupted,"
                                            + " exiting early.");
                        return true;
                    }

                    ReportingStatus reportingStatus = new ReportingStatus();
                    reportingStatus.setReportType(mReportType);
                    reportingStatus.setUploadMethod(mUploadMethod);
                    final String aggregateReportId = reportIds.get(i);
                    @AdServicesStatusUtils.StatusCode
                    int result = performReport(aggregateReportId, keys.get(i), reportingStatus);

                    if (result == AdServicesStatusUtils.STATUS_SUCCESS) {
                        reportingStatus.setUploadStatus(ReportingStatus.UploadStatus.SUCCESS);
                    } else {
                        reportingStatus.setUploadStatus(ReportingStatus.UploadStatus.FAILURE);
                        mDatastoreManager.runInTransaction(
                                (dao) -> {
                                    int retryCount =
                                            dao.incrementAndGetReportingRetryCount(
                                                    aggregateReportId,
                                                    mIsDebugInstance
                                                            ? KeyValueData.DataType
                                                                .DEBUG_AGGREGATE_REPORT_RETRY_COUNT
                                                            : KeyValueData.DataType
                                                                .AGGREGATE_REPORT_RETRY_COUNT);
                                    reportingStatus.setRetryCount(retryCount);
                                });
                    }
                    logReportingStats(reportingStatus);

                }
            } else {
                LoggerFactory.getMeasurementLogger()
                        .w("The number of keys do not align with the number of reports");
            }
        }

        return true;
    }

    private String getAppPackageName(AggregateReport report) {
        if (!mFlags.getMeasurementEnableAppPackageNameLogging()) {
            return "";
        }
        if (report.getSourceId() == null) {
            LoggerFactory.getMeasurementLogger().d("SourceId is null on aggregate report.");
            return "";
        }
        Optional<String> sourceRegistrant =
                mDatastoreManager.runInTransactionWithResult(
                        (dao) -> dao.getSourceRegistrant(report.getSourceId()));
        if (sourceRegistrant.isEmpty()) {
            LoggerFactory.getMeasurementLogger().d("Source registrant not found");
            return "";
        }
        return sourceRegistrant.get();
    }

    /**
     * Perform aggregate reporting by finding the relevant {@link AggregateReport} and making an
     * HTTP POST request to the specified report to URL with the report data as a JSON in the body.
     *
     * @param aggregateReportId for the datastore id of the {@link AggregateReport}
     * @param key used for encrypting report payload
     * @return success
     */
    @AdServicesStatusUtils.StatusCode
    synchronized int performReport(
            String aggregateReportId, AggregateEncryptionKey key, ReportingStatus reportingStatus) {
        Optional<AggregateReport> aggregateReportOpt =
                mDatastoreManager.runInTransactionWithResult((dao)
                        -> dao.getAggregateReport(aggregateReportId));
        if (!aggregateReportOpt.isPresent()) {
            LoggerFactory.getMeasurementLogger().d("Aggregate report not found");
            reportingStatus.setFailureStatus(ReportingStatus.FailureStatus.REPORT_NOT_FOUND);
            return AdServicesStatusUtils.STATUS_IO_ERROR;
        }
        AggregateReport aggregateReport = aggregateReportOpt.get();
        reportingStatus.setReportingDelay(
                System.currentTimeMillis() - aggregateReport.getScheduledReportTime());
        reportingStatus.setSourceRegistrant(getAppPackageName(aggregateReport));
        if (mIsDebugInstance
                && aggregateReport.getDebugReportStatus()
                        != AggregateReport.DebugReportStatus.PENDING) {
            LoggerFactory.getMeasurementLogger().d("Debugging status is not pending");
            reportingStatus.setFailureStatus(ReportingStatus.FailureStatus.REPORT_NOT_PENDING);
            return AdServicesStatusUtils.STATUS_INVALID_ARGUMENT;
        }
        if (!mIsDebugInstance && aggregateReport.getStatus() != AggregateReport.Status.PENDING) {
            reportingStatus.setFailureStatus(ReportingStatus.FailureStatus.REPORT_NOT_PENDING);
            return AdServicesStatusUtils.STATUS_INVALID_ARGUMENT;
        }
        try {
            Uri reportingOrigin = aggregateReport.getRegistrationOrigin();
            JSONObject aggregateReportJsonBody =
                    createReportJsonPayload(aggregateReport, reportingOrigin, key);
            int returnCode = makeHttpPostRequest(reportingOrigin, aggregateReportJsonBody);
            if (returnCode >= HttpURLConnection.HTTP_OK
                    && returnCode <= 299) {
                boolean success =
                        mDatastoreManager.runInTransaction(
                                (dao) -> {
                                    if (mIsDebugInstance) {
                                        dao.markAggregateDebugReportDelivered(aggregateReportId);
                                    } else {
                                        dao.markAggregateReportStatus(
                                                aggregateReportId,
                                                AggregateReport.Status.DELIVERED);
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
                return AdServicesStatusUtils.STATUS_IO_ERROR;
            }
        } catch (IOException e) {
            LoggerFactory.getMeasurementLogger()
                    .d(e, "Network error occurred when attempting to deliver aggregate report.");
            reportingStatus.setFailureStatus(ReportingStatus.FailureStatus.NETWORK);
            // TODO(b/298330312): Change to defined error codes
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REPORTING_NETWORK_ERROR,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
            return AdServicesStatusUtils.STATUS_IO_ERROR;
        } catch (JSONException e) {
            LoggerFactory.getMeasurementLogger()
                    .d(e, "Serialization error occurred at aggregate report delivery.");
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
                                dao.markAggregateReportStatus(
                                        aggregateReportId,
                                        AggregateReport.Status.MARKED_TO_DELETE));
            }

            if (mFlags.getMeasurementEnableReportingJobsThrowJsonException()
                    && ThreadLocalRandom.current().nextFloat()
                            < mFlags.getMeasurementThrowUnknownExceptionSamplingRate()) {
                // JSONException is unexpected.
                throw new IllegalStateException(
                        "Serialization error occurred at aggregate report delivery", e);
            }
            return AdServicesStatusUtils.STATUS_UNKNOWN_ERROR;
        } catch (CryptoException e) {
            LoggerFactory.getMeasurementLogger().e(e, e.toString());
            reportingStatus.setFailureStatus(ReportingStatus.FailureStatus.ENCRYPTION_ERROR);
            // TODO(b/298330312): Change to defined error codes
            ErrorLogUtil.e(
                    e,
                    AD_SERVICES_ERROR_REPORTED__ERROR_CODE__MEASUREMENT_REPORTING_ENCRYPTION_ERROR,
                    AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__MEASUREMENT);
            if (mFlags.getMeasurementEnableReportingJobsThrowCryptoException()
                    && ThreadLocalRandom.current().nextFloat()
                            < mFlags.getMeasurementThrowUnknownExceptionSamplingRate()) {
                throw e;
            }
            return AdServicesStatusUtils.STATUS_UNKNOWN_ERROR;
        } catch (Exception e) {
            LoggerFactory.getMeasurementLogger().e(e, e.toString());
            reportingStatus.setFailureStatus(ReportingStatus.FailureStatus.UNKNOWN);
            // TODO(b/298330312): Change to defined error codes
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

    /** Creates the JSON payload for the POST request from the AggregateReport. */
    @VisibleForTesting
    JSONObject createReportJsonPayload(AggregateReport aggregateReport, Uri reportingOrigin,
            AggregateEncryptionKey key) throws JSONException {
        String sourceRegistrationTimeStr =
                aggregateReport.getSourceRegistrationTime() == null
                        ? null
                        : String.valueOf(
                                TimeUnit.MILLISECONDS.toSeconds(
                                        aggregateReport.getSourceRegistrationTime()));
        return new AggregateReportBody.Builder()
                .setReportId(aggregateReport.getId())
                .setAttributionDestination(aggregateReport.getAttributionDestination().toString())
                .setSourceRegistrationTime(sourceRegistrationTimeStr)
                .setScheduledReportTime(
                        String.valueOf(
                                TimeUnit.MILLISECONDS.toSeconds(
                                        aggregateReport.getScheduledReportTime())))
                .setApiVersion(aggregateReport.getApiVersion())
                .setReportingOrigin(reportingOrigin.toString())
                .setDebugCleartextPayload(aggregateReport.getDebugCleartextPayload())
                .setSourceDebugKey(aggregateReport.getSourceDebugKey())
                .setTriggerDebugKey(aggregateReport.getTriggerDebugKey())
                .setAggregationCoordinatorOrigin(aggregateReport.getAggregationCoordinatorOrigin())
                .setDebugMode(
                        aggregateReport.getSourceDebugKey() != null
                                        && aggregateReport.getTriggerDebugKey() != null
                                ? "enabled"
                                : null)
                .build()
                .toJson(key, mFlags);
    }

    /**
     * Makes the POST request to the reporting URL.
     */
    @VisibleForTesting
    public int makeHttpPostRequest(Uri adTechDomain, JSONObject aggregateReportBody)
            throws IOException {
        AggregateReportSender aggregateReportSender =
                new AggregateReportSender(mIsDebugInstance, mContext);
        return aggregateReportSender.sendReport(adTechDomain, aggregateReportBody);
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
