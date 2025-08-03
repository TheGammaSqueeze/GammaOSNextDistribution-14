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
import android.car.telemetry.TelemetryProto;
import android.os.PersistableBundle;
import android.os.SystemClock;

import java.util.List;
import java.util.Objects;

/**
 * Subscriber class that receives published data and schedules tasks for execution.
 * All methods of this class must be accessed on telemetry thread.
 */
public class DataSubscriber {
    /**
     * Binder transaction size limit is 1MB for all binders per process, so for large script input
     * file pipe will be used to transfer the data to script executor instead of binder call. This
     * is the input size threshold above which piping is used.
     */
    public static final int SCRIPT_INPUT_SIZE_THRESHOLD_BYTES = 20 * 1024; // 20 kb

    private final DataBroker mDataBroker;
    private final TelemetryProto.MetricsConfig mMetricsConfig;
    private final TelemetryProto.Subscriber mSubscriber;

    public DataSubscriber(
            @NonNull DataBroker dataBroker,
            @NonNull TelemetryProto.MetricsConfig metricsConfig,
            @NonNull TelemetryProto.Subscriber subscriber) {
        mDataBroker = dataBroker;
        mMetricsConfig = metricsConfig;
        mSubscriber = subscriber;
    }

    /** Returns the handler function name for this subscriber. */
    @NonNull
    public String getHandlerName() {
        return mSubscriber.getHandler();
    }

    /**
     * Returns the publisher param {@link TelemetryProto.Publisher} that
     * contains the data source and the config.
     */
    @NonNull
    public TelemetryProto.Publisher getPublisherParam() {
        return mSubscriber.getPublisher();
    }

    /**
     * Returns the publisher type (as a number) indicates which type of
     * {@link TelemetryProto.Publisher} will publish the data.
     */
    private int getPublisherType() {
        return getPublisherParam().getPublisherCase().getNumber();
    }

    /**
     * Creates a {@link ScriptExecutionTask} and pushes it to the priority queue where the task
     * will be pending execution. Flag isLargeData indicates whether data is large.
     *
     * @param data The published data.
     * @param isLargeData Whether the data is large.
     * @return The number of tasks that are pending execution that are produced by the calling
     * publisher.
     */
    public int push(@NonNull PersistableBundle data, boolean isLargeData) {
        ScriptExecutionTask task = new ScriptExecutionTask(
                this, data, SystemClock.elapsedRealtime(), isLargeData, getPublisherType());
        return mDataBroker.addTaskToQueue(task);
    }

    /**
     * Creates a {@link ScriptExecutionTask} and pushes it to the priority queue where the task
     * will be pending execution.
     *
     * @param bundleList The published bundle list data.
     * @return The number of tasks that are pending execution that are produced by the calling
     * publisher.
     */
    public int push(@NonNull List<PersistableBundle> bundleList) {
        ScriptExecutionTask task = new ScriptExecutionTask(
                this, bundleList, SystemClock.elapsedRealtime(), getPublisherType());
        return mDataBroker.addTaskToQueue(task);
    }

    /**
     * Creates a {@link ScriptExecutionTask} and pushes it to the priority queue where the task
     * will be pending execution. Defaults isLargeData flag to false.
     *
     * @param data The published data.
     * @return The number of tasks that are pending execution that are produced by the calling
     * publisher.
     */
    public int push(@NonNull PersistableBundle data) {
        return push(data, false);
    }

    /** Returns the {@link TelemetryProto.MetricsConfig}. */
    @NonNull
    public TelemetryProto.MetricsConfig getMetricsConfig() {
        return mMetricsConfig;
    }

    /** Returns the {@link TelemetryProto.Subscriber}. */
    @NonNull
    public TelemetryProto.Subscriber getSubscriber() {
        return mSubscriber;
    }

    /** Returns the priority of subscriber. */
    public int getPriority() {
        return mSubscriber.getPriority();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DataSubscriber)) {
            return false;
        }
        DataSubscriber other = (DataSubscriber) o;
        return mMetricsConfig.getName().equals(other.getMetricsConfig().getName())
                && mMetricsConfig.getVersion() == other.getMetricsConfig().getVersion()
                && mSubscriber.getHandler().equals(other.getSubscriber().getHandler());
    }

    @Override
    public int hashCode() {
        return Objects.hash(mMetricsConfig.getName(), mMetricsConfig.getVersion(),
                mSubscriber.getHandler());
    }
}
