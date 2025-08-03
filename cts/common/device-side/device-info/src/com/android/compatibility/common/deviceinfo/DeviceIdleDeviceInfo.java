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
package com.android.compatibility.common.deviceinfo;

import com.android.compatibility.common.util.DeviceInfoStore;
import com.android.compatibility.common.util.SystemUtil;

/**
 * A device info collector that collects whether device-idle was enabled or not on the device.
 */
public final class DeviceIdleDeviceInfo extends DeviceInfo {
    private static final String KEY_DEVICE_IDLE_DEEP_ENABLED = "device_idle_deep_enabled";
    private static final String KEY_DEVICE_IDLE_LIGHT_ENABLED = "device_idle_light_enabled";

    private static boolean isDeviceIdleEnabled() throws Exception {
        final String output = SystemUtil.runShellCommand("cmd deviceidle enabled deep").trim();
        return Integer.parseInt(output) != 0;
    }

    private static boolean isDeviceLightIdleEnabled() throws Exception {
        final String output = SystemUtil.runShellCommand("cmd deviceidle enabled light").trim();
        return Integer.parseInt(output) != 0;
    }

    @Override
    protected void collectDeviceInfo(DeviceInfoStore store) throws Exception {
        store.addResult(KEY_DEVICE_IDLE_DEEP_ENABLED, isDeviceIdleEnabled());
        store.addResult(KEY_DEVICE_IDLE_LIGHT_ENABLED, isDeviceLightIdleEnabled());
    }
}
