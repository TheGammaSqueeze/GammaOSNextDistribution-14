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

package com.android.car.telemetry.publisher;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.car.telemetry.TelemetryProto;

import com.android.car.telemetry.databroker.DataSubscriber;
import com.android.car.telemetry.sessioncontroller.SessionAnnotation;

import java.util.List;

/**
 * Abstract class for publishers. It is 1-1 with data source and manages sending data to
 * subscribers. Publisher stops itself when there are no subscribers.
 *
 * <p>Note that it doesn't map 1-1 to {@link android.car.telemetry.TelemetryProto.Publisher}
 * configuration. Single publisher instance can send data as several
 * {@link android.car.telemetry.TelemetryProto.Publisher} to subscribers.
 *
 * <p>The methods must be called from the telemetry thread.
 */
public abstract class AbstractPublisher {
    private final PublisherListener mPublisherListener;

    /**
     * Listener for publisher updates, such as failing to connect to a underlying service or
     * invalid Publisher configuration.
     */
    public interface PublisherListener {
        /**
         * Called by publishers when they fail.
         * When publishers fail, the affected configs should be disabled, because the associated
         * scripts cannot receive data from the failed publishers.
         */
        void onPublisherFailure(
                @NonNull List<TelemetryProto.MetricsConfig> affectedConfigs,
                @Nullable Throwable error);

        /**
         * Called by publishers when a config satisfies terminating conditions.
         */
        void onConfigFinished(@NonNull TelemetryProto.MetricsConfig metricsConfig);
    }

    AbstractPublisher(@NonNull PublisherListener listener) {
        mPublisherListener = listener;
    }

    /**
     * Handles driving session update changes. Must be overridden by concrete publisher classes.
     *
     * @param annotation Contains annotating information about the state change.
     */
    protected abstract void handleSessionStateChange(@NonNull SessionAnnotation annotation);

    /**
     * Adds a subscriber that listens for data produced by this publisher.
     *
     * <p>DataBroker may call this method when a new {@code MetricsConfig} is added,
     * {@code CarTelemetryService} is restarted or the device is restarted.
     *
     * @param subscriber a subscriber to receive data
     * @throws IllegalArgumentException if the subscriber is invalid.
     * @throws IllegalStateException if there are internal errors.
     */
    public abstract void addDataSubscriber(@NonNull DataSubscriber subscriber);

    /**
     * Removes the subscriber from the publisher. Publisher stops if necessary.
     *
     * <p>It does nothing if subscriber is not found.
     */
    public abstract void removeDataSubscriber(@NonNull DataSubscriber subscriber);

    /**
     * Removes all the subscribers from the publisher. The publisher may stop.
     *
     * <p>This method also cleans-up internal publisher and the data source persisted state.
     */
    public abstract void removeAllDataSubscribers();

    /** Returns true if the publisher already has this data subscriber. */
    public abstract boolean hasDataSubscriber(@NonNull DataSubscriber subscriber);

    /**
     * Notifies the PublisherListener that this publisher failed.
     */
    protected void onPublisherFailure(
            @NonNull List<TelemetryProto.MetricsConfig> affectedConfigs, @NonNull Throwable error) {
        mPublisherListener.onPublisherFailure(affectedConfigs, error);
    }

    /**
     * Notifies the PublisherListener that a MetricsConfig should be marked as finished.
     */
    protected void onConfigFinished(@NonNull TelemetryProto.MetricsConfig metricsConfig) {
        mPublisherListener.onConfigFinished(metricsConfig);
    }
}
