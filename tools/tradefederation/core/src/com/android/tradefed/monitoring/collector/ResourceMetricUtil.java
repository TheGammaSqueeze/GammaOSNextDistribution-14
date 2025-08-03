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

import com.android.tradefed.device.IDeviceManager;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.CommandResult;
import com.android.tradefed.util.CommandStatus;

import com.google.protobuf.Timestamp;
import com.google.protobuf.util.Timestamps;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/** Utility functions for composing metrics. */
public class ResourceMetricUtil {

    /**
     * Executes adb command and returns response if succeed.
     *
     * @param deviceManager the IDeviceManager instance for executing command on "Available"
     *     devices.
     * @param serial the device serial.
     * @param cmd the command string.
     * @param timeoutMs the time to wait in milliseconds.
     * @return A {@link CommandResult} instance.
     */
    public static Optional<String> GetCommandResponse(
            IDeviceManager deviceManager, String serial, String cmd, long timeoutMs) {
        final CommandResult result =
                deviceManager.executeCmdOnAvailableDevice(
                        serial, cmd, timeoutMs, TimeUnit.MILLISECONDS);
        if (result == null) {
            CLog.d(
                    "Null command result for executing %s on device %s with timeout %d ms",
                    cmd, serial, timeoutMs);
            return Optional.empty();
        }
        if (result.getStatus().equals(CommandStatus.SUCCESS) && result.getStdout() != null) {
            return Optional.of(result.getStdout());
        } else {
            CLog.d(
                    "Failed to execute command %s on device %s: %s",
                    cmd, serial, result.getStderr());
            return Optional.empty();
        }
    }

    /** Gets current timestamp from system UTC clock. */
    public static Timestamp GetCurrentTimestamp() {
        return Timestamps.fromMillis(Instant.now().toEpochMilli());
    }

    /**
     * Parse and format the metric value.
     *
     * @param original the original value string.
     * @throws NumberFormatException if the original string is null.
     * @return The output float value.
     */
    public static float RoundedMetricValue(String original) throws NumberFormatException {
        return ConvertedMetricValue(original, 0.0f);
    }

    /**
     * Converts the metric value to different units and formats the output value.
     *
     * @param original the original value string.
     * @param conversionDivisor the divisor for unit conversion.
     * @throws NumberFormatException if the original string is null.
     * @return The output float value.
     */
    public static float ConvertedMetricValue(String original, float conversionDivisor)
            throws NumberFormatException {
        float val = Float.parseFloat(original);
        if (conversionDivisor != 0.0f) {
            val /= conversionDivisor;
        }
        return Math.round(val * 100) / 100.f;
    }
}
