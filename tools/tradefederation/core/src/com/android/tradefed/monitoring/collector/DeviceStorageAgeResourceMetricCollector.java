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
import com.android.tradefed.log.LogUtil.CLog;

import com.google.common.annotations.VisibleForTesting;
import com.google.dualhomelab.monitoringagent.resourcemonitoring.Metric;
import com.google.dualhomelab.monitoringagent.resourcemonitoring.Resource;

import java.util.Calendar;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parse storage age from logcat. The storaged must been enabled for this collector. Please check
 * before you use this collector, otherwise it will return empty resources.
 */
public class DeviceStorageAgeResourceMetricCollector implements IResourceMetricCollector {
    public static final String STORAGE_AGE_RESOURCE_NAME = "storage_age";
    // The storaged export the log when the device reboot, we assume most storage related tests
    // require to reboot the device. Thus, we only search the storaged log in last hour.
    public static final String STORAGE_AGE_CMD_FORMAT =
            "logcat -b events -d -t \"%d-%02d-%02d %02d:%02d:00.000\" | grep storaged_emmc_info |"
                    + " tail -1";
    public static final Pattern STORAGE_AGE_PATTERN =
            Pattern.compile(
                    "storaged_emmc_info:\\s\\[.*\\,(?<lifeTimeA>[0-9]{1,2}),(?<lifeTimeB>[0-9]{1,"
                            + "2})\\]");
    public static final String LIFE_A_TAG = "lifeTimeA";
    public static final String LIFE_B_TAG = "lifeTimeB";
    public static final String AGE_TAG = "age";
    private static final long STORAGE_AGE_TIMEOUT_MS = 500;

    /** {@inheritDoc} */
    @Override
    public Collection<Resource> getDeviceResourceMetrics(
            DeviceDescriptor descriptor, IDeviceManager deviceManager) {
        Optional<String> response =
                ResourceMetricUtil.GetCommandResponse(
                        deviceManager,
                        descriptor.getSerial(),
                        buildStorageAgeCommand(Calendar.getInstance()),
                        STORAGE_AGE_TIMEOUT_MS);
        // Returns empty resources if the command failed or no storaged log found.
        if (!response.isPresent() || response.get().isEmpty()) {
            CLog.d("Failed to find the storaged logs.");
            return List.of();
        }
        final Matcher matcher = STORAGE_AGE_PATTERN.matcher(response.get());
        if (!matcher.find()) {
            return List.of();
        }
        Resource.Builder builder =
                Resource.newBuilder()
                        .setResourceName(STORAGE_AGE_RESOURCE_NAME)
                        .setTimestamp(ResourceMetricUtil.GetCurrentTimestamp())
                        .addMetric(
                                Metric.newBuilder()
                                        .setTag(AGE_TAG)
                                        .setValue(
                                                Math.max(
                                                        ResourceMetricUtil.RoundedMetricValue(
                                                                matcher.group(LIFE_A_TAG)),
                                                        ResourceMetricUtil.RoundedMetricValue(
                                                                matcher.group(LIFE_B_TAG)))));
        return List.of(builder.build());
    }

    /** Builds storage age command to get storaged log for the last hour. */
    @VisibleForTesting
    String buildStorageAgeCommand(Calendar cal) {
        cal.add(Calendar.HOUR, -1);
        return String.format(
                Locale.US,
                STORAGE_AGE_CMD_FORMAT,
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH),
                cal.get(Calendar.HOUR),
                cal.get(Calendar.MINUTE));
    }
}
