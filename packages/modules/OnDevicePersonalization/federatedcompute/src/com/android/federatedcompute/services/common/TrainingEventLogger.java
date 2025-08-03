/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.federatedcompute.services.common;

import static com.android.federatedcompute.services.stats.FederatedComputeStatsLog.FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_COMPUTATION_COMPLETED;
import static com.android.federatedcompute.services.stats.FederatedComputeStatsLog.FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_COMPUTATION_ERROR_EXAMPLE_ITERATOR;
import static com.android.federatedcompute.services.stats.FederatedComputeStatsLog.FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_COMPUTATION_ERROR_INVALID_ARGUMENT;
import static com.android.federatedcompute.services.stats.FederatedComputeStatsLog.FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_COMPUTATION_ERROR_TENSORFLOW;
import static com.android.federatedcompute.services.stats.FederatedComputeStatsLog.FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_DOWNLOAD_ERROR_INVALID_PAYLOAD;
import static com.android.federatedcompute.services.stats.FederatedComputeStatsLog.FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_DOWNLOAD_PLAN_RECEIVED;
import static com.android.federatedcompute.services.stats.FederatedComputeStatsLog.FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_DOWNLOAD_PLAN_URI_RECEIVED;
import static com.android.federatedcompute.services.stats.FederatedComputeStatsLog.FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_DOWNLOAD_STARTED;
import static com.android.federatedcompute.services.stats.FederatedComputeStatsLog.FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_DOWNLOAD_TURNED_AWAY;
import static com.android.federatedcompute.services.stats.FederatedComputeStatsLog.FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_FAILURE_UPLOADED;
import static com.android.federatedcompute.services.stats.FederatedComputeStatsLog.FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_FAILURE_UPLOAD_STARTED;
import static com.android.federatedcompute.services.stats.FederatedComputeStatsLog.FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_INITIATE_REPORT_RESULT_AUTH_SUCCEEDED;
import static com.android.federatedcompute.services.stats.FederatedComputeStatsLog.FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_NOT_STARTED;
import static com.android.federatedcompute.services.stats.FederatedComputeStatsLog.FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_REPORT_RESULT_UNAUTHORIZED;
import static com.android.federatedcompute.services.stats.FederatedComputeStatsLog.FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_RESULT_UPLOADED;
import static com.android.federatedcompute.services.stats.FederatedComputeStatsLog.FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_RESULT_UPLOAD_SERVER_ABORTED;
import static com.android.federatedcompute.services.stats.FederatedComputeStatsLog.FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_RESULT_UPLOAD_STARTED;
import static com.android.federatedcompute.services.stats.FederatedComputeStatsLog.FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_TASK_ASSIGNMENT_AUTH_SUCCEEDED;
import static com.android.federatedcompute.services.stats.FederatedComputeStatsLog.FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_TASK_ASSIGNMENT_UNAUTHORIZED;

import com.android.federatedcompute.internal.util.LogUtil;
import com.android.federatedcompute.services.statsd.FederatedComputeStatsdLogger;
import com.android.federatedcompute.services.statsd.TrainingEventReported;

/** The helper function to log {@link TrainingEventReported} in statsd. */
public class TrainingEventLogger {
    private static final String TAG = TrainingEventLogger.class.getSimpleName();
    private long mTaskId = 0;
    private long mVersion = 0;

    public void setTaskId(long taskId) {
        this.mTaskId = taskId;
    }

    public void setClientVersion(long version) {
        this.mVersion = version;
    }

    /** Logs when device doesn't start federated task like not meet training constraints. */
    public void logTaskNotStarted() {
        TrainingEventReported.Builder event =
                new TrainingEventReported.Builder()
                        .setEventKind(
                                FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_NOT_STARTED);
        logEvent(event);
    }

    /** Logs when device checks in starts. */
    public void logCheckinStarted() {
        TrainingEventReported.Builder event =
                new TrainingEventReported.Builder()
                        .setEventKind(
                                FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_DOWNLOAD_STARTED);
        logEvent(event);
    }

    /** Logs when device is turned away from federated training. */
    public void logCheckinRejected(NetworkStats networkStats) {
        logNetworkEvent(
                FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_DOWNLOAD_TURNED_AWAY,
                networkStats);
    }

    /**
     * Logs when device checks in, gets task assignment, download plan model and plan is invalid.
     */
    public void logCheckinInvalidPayload(NetworkStats networkStats) {
        logNetworkEvent(
                FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_DOWNLOAD_ERROR_INVALID_PAYLOAD,
                networkStats);
    }

    /**
     * Logs when device checks in, gets task assignment and receive plan uri but not download yet.
     */
    public void logCheckinPlanUriReceived(NetworkStats networkStats) {
        logNetworkEvent(
                FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_DOWNLOAD_PLAN_URI_RECEIVED,
                networkStats);
    }

    /** Logs when device checks in, gets task assignment, download plan model and plan is valid. */
    public void logCheckinFinished(NetworkStats networkStats) {
        logNetworkEvent(
                FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_DOWNLOAD_PLAN_RECEIVED,
                networkStats);
    }

    /** Logs when federated computation job fails with invalid argument reason. */
    public void logComputationInvalidArgument(ExampleStats exampleStats) {
        logComputationTerminationEvent(
                FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_COMPUTATION_ERROR_INVALID_ARGUMENT,
                exampleStats);
    }

    /** Logs when federated computation job fails due to example iterator. */
    public void logComputationExampleIteratorError(ExampleStats exampleStats) {
        logComputationTerminationEvent(
                FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_COMPUTATION_ERROR_EXAMPLE_ITERATOR,
                exampleStats);
    }

    /**
     * Logs when federated computation job fails due to tensorflow issue like unsupported
     * operations, kernels.
     */
    public void logComputationTensorflowError(ExampleStats exampleStats) {
        logComputationTerminationEvent(
                FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_COMPUTATION_ERROR_TENSORFLOW,
                exampleStats);
    }

    /** Logs when federated computation job complete. */
    public void logComputationCompleted(ExampleStats exampleStats) {
        logComputationTerminationEvent(
                FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_COMPUTATION_COMPLETED,
                exampleStats);
    }

    private void logComputationTerminationEvent(int eventKind, ExampleStats exampleStats) {
        TrainingEventReported.Builder event =
                new TrainingEventReported.Builder()
                        .setEventKind(eventKind)
                        .setExampleSize(exampleStats.getExampleSizeBytes());
        logEvent(event);
    }

    /** Logs when device starts to upload computation result. */
    public void logResultUploadStarted() {
        TrainingEventReported.Builder event =
                new TrainingEventReported.Builder()
                        .setEventKind(
                                FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_RESULT_UPLOAD_STARTED);
        logEvent(event);
    }

    /** Logs when device uploads computation result but rejected by federated server. */
    public void logResultUploadRejected(NetworkStats networkStats) {
        logNetworkEvent(
                FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_RESULT_UPLOAD_SERVER_ABORTED,
                networkStats);
    }

    /** Logs when device uploads computation result completed. */
    public void logResultUploadCompleted(NetworkStats networkStats) {
        logNetworkEvent(
                FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_RESULT_UPLOADED,
                networkStats);
    }

    /** Logs when device starts to upload failure computation result. */
    public void logFailureResultUploadStarted() {
        TrainingEventReported.Builder event =
                new TrainingEventReported.Builder()
                        .setEventKind(
                                FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_FAILURE_UPLOAD_STARTED);
        logEvent(event);
    }

    /** Logs when device finishes uploading failure computation result. */
    public void logFailureResultUploadCompleted(NetworkStats networkStats) {
        logNetworkEvent(
                FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_FAILURE_UPLOADED,
                networkStats);
    }

    private void logNetworkEvent(int eventKind, NetworkStats networkStats) {
        TrainingEventReported.Builder event =
                new TrainingEventReported.Builder()
                        .setEventKind(eventKind)
                        .setBytesUploaded(networkStats.getTotalBytesUploaded())
                        .setBytesDownloaded(networkStats.getTotalBytesDownloaded());
        logEvent(event);
    }

    /** Logs when devices are unauthorized to create task assignment. */
    public void logTaskAssignmentUnauthorized() {
        logKeyAttestationEvent(
                FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_TASK_ASSIGNMENT_UNAUTHORIZED);
    }

    /** Logs when devices are successfully authenticated to create task assignment.*/
    public void logTaskAssignmentAuthSucceeded() {
        logKeyAttestationEvent(
                FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_TASK_ASSIGNMENT_AUTH_SUCCEEDED);
    }

    /** Logs when devices are not authorized to report result. */
    public void logReportResultUnauthorized() {
        logKeyAttestationEvent(
                FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_REPORT_RESULT_UNAUTHORIZED);
    }

    /** Logs when devices are successfully authenticated to report result. */
    public void logReportResultAuthSucceeded() {
        logKeyAttestationEvent(
                FEDERATED_COMPUTE_TRAINING_EVENT_REPORTED__KIND__TRAIN_INITIATE_REPORT_RESULT_AUTH_SUCCEEDED);
    }

    /** Logs the latency of calling key attestation on device */
    public void logKeyAttestationLatencyEvent(long latencyMillis) {
        TrainingEventReported.Builder event = new TrainingEventReported.Builder()
                .setKeyAttestationLatencyMillis(latencyMillis);
        logEvent(event);
    }

    private void logKeyAttestationEvent(int eventKind) {
        TrainingEventReported.Builder event =
                new TrainingEventReported.Builder()
                        .setEventKind(eventKind);
        logEvent(event);
    }

    private void logEvent(TrainingEventReported.Builder event) {
        if (mTaskId != 0) {
            event.setTaskId(mTaskId);
        }
        if (mVersion != 0) {
            event.setClientVersion(mVersion);
        }
        TrainingEventReported trainingEvent = event.build();
        LogUtil.i(
                TAG,
                "Log event kind %d, network upload %d download %d example stats %d"
                + "key attestation stats %d",
                trainingEvent.getEventKind(),
                trainingEvent.getBytesUploaded(),
                trainingEvent.getBytesDownloaded(),
                trainingEvent.getExampleSize(),
                trainingEvent.getKeyAttestationLatencyMillis());
        FederatedComputeStatsdLogger.getInstance().logTrainingEventReported(trainingEvent);
    }
}
