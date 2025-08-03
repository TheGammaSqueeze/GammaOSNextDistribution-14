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

package android.cts.host.utils;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;

public final class DeviceConfigHelper {
    private static final String TAG = "DeviceConfigHelper";

    private static final String PERSISTENT_SYNC_DISABLED = "persistent";

    // Prevent Phenotype overriding device_config changes
    public static String deviceDisableConfigSync(ITestDevice device) throws Exception {
        String previousSyncDisabledStatus = device
                .executeShellCommand("device_config get_sync_disabled_for_tests");

        device.executeShellCommand(String.format("device_config set_sync_disabled_for_tests %s",
                PERSISTENT_SYNC_DISABLED));
        String res = device.executeShellCommand("device_config get_sync_disabled_for_tests");
        if (res == null || !res.contains(PERSISTENT_SYNC_DISABLED)) {
            CLog.w(TAG, "Could not disable device config for test");
        }

        return previousSyncDisabledStatus;
    }

    public static void deviceRestoreConfigSync(ITestDevice device,
            String previousSyncDisabledStatus) throws Exception {
        if (previousSyncDisabledStatus != null
                && !previousSyncDisabledStatus.equals(PERSISTENT_SYNC_DISABLED)) {
            device.executeShellCommand(
                    "device_config set_sync_disabled_for_tests " + previousSyncDisabledStatus);
        }
    }
}
