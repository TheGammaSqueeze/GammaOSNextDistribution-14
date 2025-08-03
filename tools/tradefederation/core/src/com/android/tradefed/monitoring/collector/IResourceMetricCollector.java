/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.tradefed.monitoring.collector;

import com.android.tradefed.command.remote.DeviceDescriptor;
import com.android.tradefed.device.IDeviceManager;

import com.google.dualhomelab.monitoringagent.resourcemonitoring.Resource;
import com.android.tradefed.monitoring.LabResourceDeviceMonitor;
import java.util.Collection;
import java.util.List;

/** The interface for user to implement customized resource collectors. */
public interface IResourceMetricCollector {

    /**
     * Collects host resource metrics. The function must return in {@link
     * IResourceMetricCollector#getHostMetricizeTimeoutMs()} ms, otherwise the result will be
     * dropped by the {@link LabResourceDeviceMonitor}. Also, please check
     * Thread.currentThread().isInterrupted() before expensive operation and return immediately.
     *
     * @return a {@link Collection} of host {@link Resource}.
     */
    public default Collection<Resource> getHostResourceMetrics() {
        return List.of();
    }

    /**
     * Collects device resource metrics. The function must return in {@link
     * IResourceMetricCollector#getDeviceMetricizeTimeoutMs()} ms, otherwise the result will be
     * dropped by the {@link LabResourceDeviceMonitor}. Also, please check
     * Thread.currentThread().isInterrupted() before expensive operation and return immediately.
     *
     * @param descriptor the {@link DeviceDescriptor} about the metricizing device.
     * @param deviceManager the {@link IDeviceManager} instance.
     * @return a {@link Collection} of device {@link Resource}.
     */
    public default Collection<Resource> getDeviceResourceMetrics(
            DeviceDescriptor descriptor, IDeviceManager deviceManager) {
        return List.of();
    }

    /** Gets the host metricize timeout in ms. */
    public default long getHostMetricizeTimeoutMs() {
        return 1000;
    }

    /** Gets the device metricize timeout in ms. */
    public default long getDeviceMetricizeTimeoutMs() {
        return 1000;
    }
}
