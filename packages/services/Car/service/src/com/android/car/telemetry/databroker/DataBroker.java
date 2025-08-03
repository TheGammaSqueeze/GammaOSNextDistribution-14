/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.car.telemetry.databroker;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.car.telemetry.TelemetryProto;
import android.os.PersistableBundle;

/** Interface for the data path. Handles data forwarding from publishers to subscribers */
public interface DataBroker {

    /**
     * Interface for receiving notification from DataBroker.
     */
    interface DataBrokerListener {
        /**
         * Called when subscribers consumed an event and an interim state should be saved as a
         * result.
         * @param metricsConfigName that uniquely identifies the config whose script finished.
         * @param state an interim state to be saved for the next script execution.
         */
        void onEventConsumed(@NonNull String metricsConfigName, @NonNull PersistableBundle state);

        /**
         * Called when a MetricsConfig's lifecycle ends without a report or error.
         *
         * @param metricsConfigName that uniquely identifies the config whose script finished.
         */
        void onReportFinished(@NonNull String metricsConfigName);

        /**
         * Called when a MetricsConfig's lifecycle ends and a metrics report is produced by it.
         *
         * @param metricsConfigName that uniquely identifies the config whose script finished.
         * @param report the final report produced by the MetricsConfig.
         */
        void onReportFinished(@NonNull String metricsConfigName, @NonNull PersistableBundle report);

        /**
         * Called when a MetricsConfig's lifecycle ends and an error is produced.
         *
         * @param metricsConfigName that uniquely identifies the config that terminated.
         */
        void onReportFinished(
                @NonNull String metricsConfigName,
                @NonNull TelemetryProto.TelemetryError error);

        /**
         * Called when a MetricsConfig produces a metrics report without ending its lifecycle.
         *
         * @param metricsConfigName that uniquely identifies the config whose script produced a
         *                          report.
         * @param report the metrics report.
         * @param state optional state to persist for the next script execution.
         */
        void onMetricsReport(
                @NonNull String metricsConfigName,
                @NonNull PersistableBundle report,
                @Nullable PersistableBundle state);
    }

    /**
     * Adds an active {@link android.car.telemetry.TelemetryProto.MetricsConfig} that is pending
     * execution. When updating the MetricsConfig to a newer version, the caller must call
     * {@link #removeMetricsConfig(String)} first to clear the old MetricsConfig.
     * @param metricsConfigName name of the MetricsConfig.
     * @param metricsConfig to be added and queued for execution.
     */
    void addMetricsConfig(
            @NonNull String metricsConfigName, @NonNull TelemetryProto.MetricsConfig metricsConfig);

    /**
     * Removes a {@link android.car.telemetry.TelemetryProto.MetricsConfig} and all its
     * relevant subscriptions.
     *
     * @param metricsConfigName to identify the MetricsConfig to be removed.
     */
    void removeMetricsConfig(@NonNull String metricsConfigName);

    /**
     * Removes all {@link android.car.telemetry.TelemetryProto.MetricsConfig}s and subscriptions.
     */
    void removeAllMetricsConfigs();

    /**
     * Adds a {@link ScriptExecutionTask} to the priority queue. This method will schedule the
     * next task if a task is not currently running.
     *
     * @param task The task that contains the script and published data for ScriptExecutor.
     * @return The number of tasks that are pending execution that are produced by the calling
     * publisher.
     */
    int addTaskToQueue(@NonNull ScriptExecutionTask task);

    /**
     * Checks system health state and executes a task if condition allows.
     */
    void scheduleNextTask();

    /**
     * Sets listener for DataBroker events.
     */
    void setDataBrokerListener(@NonNull DataBrokerListener dataBrokerListener);

    /**
     * Sets the priority which affects which subscribers can consume data. Invoked by controller to
     * indicate system health state and which subscribers can be consumed. If controller does not
     * set the priority, it will be defaulted to 1. A smaller priority number indicates higher
     * priority. Range 0 - 100. A priority of 0 means the script should run regardless of system
     * health conditions.
     */
    void setTaskExecutionPriority(int priority);
}
