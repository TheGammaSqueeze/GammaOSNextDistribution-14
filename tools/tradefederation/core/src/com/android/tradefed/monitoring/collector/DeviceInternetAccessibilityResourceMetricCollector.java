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
import com.android.tradefed.config.Option;
import com.android.tradefed.device.IDeviceManager;

import com.google.common.annotations.VisibleForTesting;
import com.google.dualhomelab.monitoringagent.resourcemonitoring.Metric;
import com.google.dualhomelab.monitoringagent.resourcemonitoring.Resource;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The collector pings google.com to check if the device has internet accessibility or not. */
public class DeviceInternetAccessibilityResourceMetricCollector implements IResourceMetricCollector {
    public static final String INTERNET_ACCESSIBILITY_METRIC_NAME = "internet_access";
    /*
    The example response:
    PING google.com (172.217.27.142) 56(84) bytes of data.
    64 bytes from tsa03s02-in-f14.1e100.net (172.217.27.142): icmp_seq=1 ttl=116 time=4.63 ms

    --- google.com ping statistics ---
    1 packets transmitted, 1 received, 0% packet loss, time 0ms
    rtt min/avg/max/mdev = 4.638/4.638/4.638/0.000 ms
    */
    public static final String PING_CMD = "ping -c 2 -W 1 google.com";
    public static final String PING6_CMD = "ping6 -c 2 -W 1 google.com";
    public static final Pattern SUCCESS_PATTERN =
            Pattern.compile(".*min/avg/max/.* = [0-9.]+/(?<avgping>[0-9.]+)/");
    public static final String AVG_PING = "avgping";
    public static final String AVG_PING_TAG = "avgping";
    public static final String AVG_PING6_TAG = "avgping6";
    // the ping response timeout was set to 1 second, thus we use 1000 ms to represent failed ping.
    public static final Float FAILED_VAL = 1000.f;

    @Option(name = "commandTimeout", description = "The timeout in ms for each ping command.")
    private long mCmdTimeoutMs = 2000;

    @Option(
            name = "deviceMetricizeTimeout",
            description = "The timeout in ms for device metricize.")
    private long mDeviceMetricizeTimeoutMs = 5000;

    /** Issues ping command to collect internet accessibility metrics. */
    @Override
    public Collection<Resource> getDeviceResourceMetrics(
            DeviceDescriptor descriptor, IDeviceManager deviceManager) {
        final Resource.Builder builder =
                Resource.newBuilder()
                        .setResourceName(INTERNET_ACCESSIBILITY_METRIC_NAME)
                        .setTimestamp(ResourceMetricUtil.GetCurrentTimestamp());
        final float avgPing = getAveragePing(descriptor, deviceManager, PING_CMD);
        final Metric.Builder metricBuilder = Metric.newBuilder().setTag(AVG_PING_TAG);
        metricBuilder.setValue(avgPing);
        builder.addMetric(metricBuilder);
        final float avgPing6 = getAveragePing(descriptor, deviceManager, PING6_CMD);
        final Metric.Builder metric6Builder = Metric.newBuilder().setTag(AVG_PING6_TAG);
        metric6Builder.setValue(avgPing6);
        builder.addMetric(metric6Builder);
        return List.of(builder.build());
    }

    /** Utility function that issues ping command and parse the result. */
    @VisibleForTesting
    float getAveragePing(
            DeviceDescriptor descriptor, IDeviceManager deviceManager, String command) {
        final Optional<String> response =
                ResourceMetricUtil.GetCommandResponse(
                        deviceManager, descriptor.getSerial(), command, mCmdTimeoutMs);
        if (!response.isPresent()) {
            return FAILED_VAL;
        }
        final Matcher matcher = SUCCESS_PATTERN.matcher(response.get());
        if (!matcher.find()) {
            return FAILED_VAL;
        }
        return ResourceMetricUtil.RoundedMetricValue(matcher.group(AVG_PING));
    }

    @VisibleForTesting
    long getCmdTimeoutMs() {
        return mCmdTimeoutMs;
    }

    @Override
    public long getDeviceMetricizeTimeoutMs() {
        return mDeviceMetricizeTimeoutMs;
    }
}
