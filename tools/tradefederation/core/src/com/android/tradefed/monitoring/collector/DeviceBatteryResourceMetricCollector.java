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

import com.google.dualhomelab.monitoringagent.resourcemonitoring.Metric;
import com.google.dualhomelab.monitoringagent.resourcemonitoring.Resource;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * This collector collects device battery metrics. It issues adb shell commands and parse the
 * response, the metrics include status, health, level, scale and temperature.
 */
public class DeviceBatteryResourceMetricCollector implements IResourceMetricCollector {
    public static final String BATTERY_RESOURCE_NAME = "battery";
    public static final String BATTERY_CMD = "dumpsys battery";
    /* The example command output:
    Current Battery Service state:
      AC powered: true
      USB powered: false
      Wireless powered: false
      Max charging current: 3000000
      Max charging voltage: 5000000
      Charge counter: 3333000
      status: 5
      health: 2
      present: true
      level: 100
      scale: 100
      voltage: 4451
      temperature: 286
      technology: Unknown
    */
    public static final Pattern BATTERY_PATTERN =
            Pattern.compile(
                    "(?<field>status|health|level|scale|temperature)\\:\\s(?<value>[0-9]+)");
    public static final String TEMPERATURE = "temperature";
    public static final String FIELD_GROUP = "field";
    public static final String VALUE_GROUP = "value";
    public static final float BATTERY_TEMP_DENOMINATOR = 10.0f;
    private static final long CMD_TIMEOUT_MS = 500;

    /** Gets device battery state. */
    @Override
    public Collection<Resource> getDeviceResourceMetrics(
            DeviceDescriptor descriptor, IDeviceManager deviceManager) {
        Optional<String> response =
                ResourceMetricUtil.GetCommandResponse(
                        deviceManager, descriptor.getSerial(), BATTERY_CMD, CMD_TIMEOUT_MS);
        if (!response.isPresent()) {
            return List.of();
        }
        final Matcher matcher = BATTERY_PATTERN.matcher(response.get());
        if (!matcher.find()) {
            return List.of();
        }
        final Resource.Builder builder =
                Resource.newBuilder()
                        .setResourceName(BATTERY_RESOURCE_NAME)
                        .setTimestamp(ResourceMetricUtil.GetCurrentTimestamp());
        do {
            Metric.Builder metricBuilder = Metric.newBuilder();
            metricBuilder.setTag(matcher.group(FIELD_GROUP));
            if (Objects.equals(matcher.group(FIELD_GROUP), TEMPERATURE)) {
                metricBuilder.setValue(
                        ResourceMetricUtil.ConvertedMetricValue(
                                matcher.group(VALUE_GROUP), BATTERY_TEMP_DENOMINATOR));
            } else {
                metricBuilder.setValue(
                        ResourceMetricUtil.RoundedMetricValue(matcher.group(VALUE_GROUP)));
            }
            builder.addMetric(metricBuilder);
        } while (matcher.find());
        return List.of(builder.build());
    }
}
