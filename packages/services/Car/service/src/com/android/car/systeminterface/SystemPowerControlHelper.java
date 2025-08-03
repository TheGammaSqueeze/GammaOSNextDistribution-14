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

package com.android.car.systeminterface;

import android.annotation.NonNull;
import android.car.builtin.util.Slogf;

import com.android.internal.annotations.VisibleForTesting;

import libcore.io.IoUtils;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Uses sysfs(sys/power/state) to force device into suspend
 */
public final class SystemPowerControlHelper {
    // Constants matching return values from libsuspend
    public static final int SUSPEND_RESULT_SUCCESS = 0;
    public static final int SUSPEND_RESULT_FAILURE = -1;

    @VisibleForTesting
    static final String TAG = "SystemPowerControlHelper";
    @VisibleForTesting
    static final String SUSPEND_TYPE_MEM = "mem";
    @VisibleForTesting
    static final String SUSPEND_TYPE_DISK = "disk";

    private static final String SYSFS_POWER_STATE_CONTROL_FILE = "/sys/power/state";

    private SystemPowerControlHelper() {
    }

    /**
     * Forces system to enter deep sleep (Suspend-to-RAM)
     *
     * @return {@code SUSPEND_RESULT_SUCCESS} in case of success and {@code SUSPEND_RESULT_FAILURE}
     * if Suspend-to-RAM fails
     */
    public static int forceDeepSleep() {
        return enterSuspend(SUSPEND_TYPE_MEM);
    }

    /**
     * Forces system to enter hibernation (Suspend-to-disk)
     *
     * @return {@code SUSPEND_RESULT_SUCCESS} in case of success and {@code SUSPEND_RESULT_FAILURE}
     * if Suspend-to-disk fails
     */
    public static int forceHibernate() {
        return enterSuspend(SUSPEND_TYPE_DISK);
    }

    /**
     * Gets whether the device supports deep sleep
     */
    public static boolean isSystemSupportingDeepSleep() {
        return isSuspendTypeSupported(SUSPEND_TYPE_MEM);
    }

    /**
     * Gets whether the device supports hibernation
     */
    public static boolean isSystemSupportingHibernation() {
        return isSuspendTypeSupported(SUSPEND_TYPE_DISK);
    }


    @VisibleForTesting
    static String getSysFsPowerControlFile() {
        return SYSFS_POWER_STATE_CONTROL_FILE;
    }

    /*
     * To match libsuspend API, functions returns SUSPEND_FAILURE(-1) in case of error
     * and SUSPEND_SUCCESS(0) on Success
     */
    private static int enterSuspend(String mode) {
        String sysFsPowerControlFile = getSysFsPowerControlFile();

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(sysFsPowerControlFile))) {
            writer.write(mode);
            writer.flush();
        } catch (IOException e) {
            Slogf.e(TAG, e, "Failed to suspend. Target %s. Failed to write to %s", mode,
                    sysFsPowerControlFile);
            return SUSPEND_RESULT_FAILURE;
        }
        return SUSPEND_RESULT_SUCCESS;
    }

    private static boolean isSuspendTypeSupported(@NonNull String suspendType) {
        String sysFsPowerControlFile = getSysFsPowerControlFile();

        boolean isSuspendTypeSupported = false;
        try {
            String fileContents = IoUtils.readFileAsString(sysFsPowerControlFile).trim();
            for (String supported : fileContents.split(" ")) {
                if (suspendType.equals(supported)) {
                    isSuspendTypeSupported = true;
                    break;
                }
            }
        } catch (IOException e) {
            Slogf.e(TAG, e, "Failed to check supported suspend types. Target %s."
                    + " Unable to read %s", suspendType, sysFsPowerControlFile);
        }

        return isSuspendTypeSupported;
    }
}
