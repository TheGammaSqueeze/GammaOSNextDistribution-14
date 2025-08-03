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

package com.android.ondevicepersonalization.services;

import static com.android.ondevicepersonalization.services.PhFlags.KEY_CALLER_APP_ALLOW_LIST;
import static com.android.ondevicepersonalization.services.PhFlags.KEY_ENABLE_PERSONALIZATION_STATUS_OVERRIDE;
import static com.android.ondevicepersonalization.services.PhFlags.KEY_GLOBAL_KILL_SWITCH;
import static com.android.ondevicepersonalization.services.PhFlags.KEY_ISOLATED_SERVICE_ALLOW_LIST;
import static com.android.ondevicepersonalization.services.PhFlags.KEY_OUTPUT_DATA_ALLOW_LIST;

import android.provider.DeviceConfig;

import androidx.test.InstrumentationRegistry;

public class PhFlagsTestUtil {
    private static final String WRITE_DEVICE_CONFIG_PERMISSION =
            "android.permission.WRITE_DEVICE_CONFIG";

    private static final String READ_DEVICE_CONFIG_PERMISSION =
            "android.permission.READ_DEVICE_CONFIG";

    private static final String MONITOR_DEVICE_CONFIG_ACCESS =
            "android.permission.MONITOR_DEVICE_CONFIG_ACCESS";

    /**
     * Get necessary permissions to access Setting.Config API and set up context
     */
    public static void setUpDeviceConfigPermissions() throws Exception {
        InstrumentationRegistry.getInstrumentation().getUiAutomation().adoptShellPermissionIdentity(
                WRITE_DEVICE_CONFIG_PERMISSION, READ_DEVICE_CONFIG_PERMISSION,
                MONITOR_DEVICE_CONFIG_ACCESS);
    }

    public static void enableGlobalKillSwitch() {
        // Override the global_kill_switch to test other flag values.
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                KEY_GLOBAL_KILL_SWITCH,
                Boolean.toString(true),
                /* makeDefault */ false);
    }

    public static void disableGlobalKillSwitch() {
        // Override the global_kill_switch to test other flag values.
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                KEY_GLOBAL_KILL_SWITCH,
                Boolean.toString(false),
                /* makeDefault */ false);
    }

    /**
     * Disable the enable_personalization_status_override to test personalization-related features.
     */
    public static void disablePersonalizationStatusOverride() {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                KEY_ENABLE_PERSONALIZATION_STATUS_OVERRIDE,
                Boolean.toString(false),
                /* makeDefault */ false);
    }

    /**
     * Set up caller app allow list in device config
     */
    public static void setCallerAppAllowList(final String callerAppAllowList) {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                KEY_CALLER_APP_ALLOW_LIST,
                callerAppAllowList,
                /* makeDefault */ false);
    }

    /**
     * Set up isolated service allow list in device config
     */
    public static void setIsolatedServiceAllowList(final String isolatedServiceAllowList) {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                KEY_ISOLATED_SERVICE_ALLOW_LIST,
                isolatedServiceAllowList,
                /* makeDefault */ false);
    }

    /**
     * Set up output data allow list in device config
     */
    public static void setOutputDataAllowList(final String outputDataAllowList) {
        DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_ON_DEVICE_PERSONALIZATION,
                KEY_OUTPUT_DATA_ALLOW_LIST,
                outputDataAllowList,
                /* makeDefault */ false);
    }
}
