/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.server.wifi.util;

import android.app.ActivityManager;
import android.content.Context;

/**
 * A wifi permissions dependency class to wrap around external
 * calls to static methods that enable testing.
 */
public class WifiPermissionsWrapper {
    private static final String TAG = "WifiPermissionsWrapper";
    private final Context mContext;

    public WifiPermissionsWrapper(Context context) {
        mContext = context;
    }

    public int getCurrentUser() {
        return ActivityManager.getCurrentUser();
    }

    /**
     * Determine if a UID has a permission.
     *
     * @param permissionType permission string
     * @param uid to get permission for
     * @param pid to get permission for
     * @return Permissions setting
     */
    public int getUidPermission(String permissionType, int uid, int pid) {
        return mContext.checkPermission(permissionType, pid, uid);
    }

    /**
     * Wrapper around {@link #getUidPermission(String, int, int)}.
     * TODO (b/231480106): Remove this wrapper and always pass the pid
     */
    public int getUidPermission(String permissionType, int uid) {
        // We don't care about the pid, pass in -1
        return getUidPermission(permissionType, uid, -1);
    }

    /**
     * Determines if the caller has the override wifi config permission.
     *
     * @param uid to check the permission for
     * @param pid to check the permission for
     * @return int representation of success or denied
     */
    public int getOverrideWifiConfigPermission(int uid, int pid) {
        return getUidPermission(android.Manifest.permission.OVERRIDE_WIFI_CONFIG, uid, pid);
    }

    /**
     * Wrapper around {@link #getOverrideWifiConfigPermission(int, int)}
     * TODO (b/231480106): Remove this wrapper and always pass the pid
     */
    public int getOverrideWifiConfigPermission(int uid) {
        // We don't care about the pid, pass in -1
        return getOverrideWifiConfigPermission(uid, -1);
    }
}
