/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static com.android.federatedcompute.services.common.Flags.USE_BACKGROUND_ENCRYPTION_KEY_FETCH;
import static com.android.federatedcompute.services.common.PhFlags.ENABLE_BACKGROUND_ENCRYPTION_KEY_FETCH;
import static com.android.federatedcompute.services.common.PhFlags.KEY_FEDERATED_COMPUTE_KILL_SWITCH;

import android.provider.DeviceConfig;

import androidx.test.InstrumentationRegistry;

public class PhFlagsTestUtil {
    private static final String WRITE_DEVICE_CONFIG_PERMISSION =
            "android.permission.WRITE_DEVICE_CONFIG";

    private static final String READ_DEVICE_CONFIG_PERMISSION =
            "android.permission.READ_DEVICE_CONFIG";

    private static final String MONITOR_DEVICE_CONFIG_ACCESS =
            "android.permission.MONITOR_DEVICE_CONFIG_ACCESS";

    /** Get necessary permissions to access Setting.Config API and set up context */
    public static void setUpDeviceConfigPermissions() throws Exception {
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(
                        WRITE_DEVICE_CONFIG_PERMISSION,
                        READ_DEVICE_CONFIG_PERMISSION,
                        MONITOR_DEVICE_CONFIG_ACCESS);
    }

    public static void enableGlobalKillSwitch() {
        // Override the global_kill_switch to test other flag values.
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                KEY_FEDERATED_COMPUTE_KILL_SWITCH,
                Boolean.toString(true),
                /* makeDefault */ false);
    }

    public static void disableGlobalKillSwitch() {
        // Override the global_kill_switch to test other flag values.
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                KEY_FEDERATED_COMPUTE_KILL_SWITCH,
                Boolean.toString(false),
                /* makeDefault */ false);
    }

    /** Enable scheduling the background key fetch job. */
    public static void enableScheduleBackgroundKeyFetchJob() {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                ENABLE_BACKGROUND_ENCRYPTION_KEY_FETCH,
                Boolean.toString(USE_BACKGROUND_ENCRYPTION_KEY_FETCH),
                /* makeDefault= */ false);
    }

    /** Disable scheduling the background key fetch job. */
    public static void disableScheduleBackgroundKeyFetchJob() {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                ENABLE_BACKGROUND_ENCRYPTION_KEY_FETCH,
                Boolean.toString(false),
                /* makeDefault= */ false);
    }
}
