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

package com.android.server.wifi.util;

import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.app.ActivityManager;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Process;
import android.os.UserHandle;
import android.os.WorkSource;
import android.text.TextUtils;
import android.util.Log;

import com.android.wifi.resources.R;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Arrays;

/**
 * Class for wrapping a WorkSource object and providing some (wifi specific) utility methods.
 *
 * This is primarily used in {@link com.android.server.wifi.HalDeviceManager} class.
 */
public class WorkSourceHelper {
    private static final String TAG = "WorkSourceHelper";
    private static final int APP_INFO_FLAGS_SYSTEM_APP =
            ApplicationInfo.FLAG_SYSTEM | ApplicationInfo.FLAG_UPDATED_SYSTEM_APP;
    private final WorkSource mWorkSource;
    private final WifiPermissionsUtil mWifiPermissionsUtil;
    private final ActivityManager mActivityManager;
    private final PackageManager mPackageManager;
    private final Resources mResources;

    // Internal opportunistic request.
    public static final int PRIORITY_INTERNAL = 0;

    // Request from a background app.
    public static final int PRIORITY_BG = 1;

    // Request from a foreground service.
    public static final int PRIORITY_FG_SERVICE = 2;

    // Request from a foreground app.
    public static final int PRIORITY_FG_APP = 3;

    // Request from a system app.
    public static final int PRIORITY_SYSTEM = 4;

    // Request from an app with NETWORK_SETTINGS, NETWORK_SETUP_WIZARD or NETWORK_STACK permission.
    public static final int PRIORITY_PRIVILEGED = 5;

    // Keep these in sync with any additions/deletions to above buckets.
    public static final int PRIORITY_MIN = PRIORITY_INTERNAL;
    public static final int PRIORITY_MAX = PRIORITY_PRIVILEGED;
    @IntDef(prefix = { "PRIORITY_" }, value = {
            PRIORITY_INTERNAL,
            PRIORITY_BG,
            PRIORITY_FG_SERVICE,
            PRIORITY_FG_APP,
            PRIORITY_SYSTEM,
            PRIORITY_PRIVILEGED,
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface RequestorWsPriority {}

    /**
     * Returns integer priority level for the provided |ws|.
     */
    public @RequestorWsPriority int getRequestorWsPriority() {
        @RequestorWsPriority int totalPriority = PRIORITY_INTERNAL;
        for (int i = 0; i < mWorkSource.size(); i++) {
            String packageName = mWorkSource.getPackageName(i);
            int uid = mWorkSource.getUid(i);
            final @RequestorWsPriority int priority;
            if (uid == Process.WIFI_UID) {
                priority = PRIORITY_INTERNAL;
            } else if (isPrivileged(uid)) {
                priority = PRIORITY_PRIVILEGED;
            } else if (isSystem(packageName, uid)) {
                priority = PRIORITY_SYSTEM;
            } else if (isForegroundApp(packageName)) {
                priority = PRIORITY_FG_APP;
            } else if (isForegroundService(packageName)) {
                priority = PRIORITY_FG_SERVICE;
            } else {
                priority = PRIORITY_BG;
            }
            if (priority > totalPriority) {
                totalPriority = priority;
            }
        }
        return totalPriority;
    }

    public WorkSourceHelper(
            @NonNull WorkSource workSource,
            @NonNull WifiPermissionsUtil wifiPermissionsUtil,
            @NonNull ActivityManager activityManager,
            @NonNull PackageManager packageManager,
            @NonNull Resources resources) {
        mWorkSource = workSource;
        mWifiPermissionsUtil = wifiPermissionsUtil;
        mActivityManager = activityManager;
        mPackageManager = packageManager;
        mResources = resources;
    }

    public WorkSource getWorkSource() {
        return mWorkSource;
    }

    @Override
    public String toString() {
        return mWorkSource.toString();
    }

    /**
     * Check if the request comes from an app with privileged permissions.
     */
    private boolean isPrivileged(int uid) {
        return mWifiPermissionsUtil.checkNetworkSettingsPermission(uid)
                || mWifiPermissionsUtil.checkNetworkSetupWizardPermission(uid)
                || mWifiPermissionsUtil.checkNetworkStackPermission(uid)
                || mWifiPermissionsUtil.checkMainlineNetworkStackPermission(uid);
    }

    /**
     * Check if the request comes from a system app.
     */
    private boolean isSystem(String packageName, int uid) {
        // when checking ActiveModeWarden#INTERNAL_REQUESTOR_WS
        if (packageName == null) {
            return false;
        }
        try {
            ApplicationInfo info = mPackageManager.getApplicationInfoAsUser(
                    packageName, 0, UserHandle.getUserHandleForUid(uid));
            return (info.flags & APP_INFO_FLAGS_SYSTEM_APP) != 0;
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Failed to retrieve app info for packageName=" + packageName + " uid=" + uid,
                    e);
            // In case of exception, assume unknown app (more strict checking)
            // Note: This case will never happen since checkPackage is
            // called to verify validity before checking App's version.
            return false;
        }
    }

    /**
     * Check if the request comes from a foreground app.
     */
    private boolean isForegroundApp(@NonNull String requestorPackageName) {
        String[] exceptionList = mResources.getStringArray(
                R.array.config_wifiInterfacePriorityTreatAsForegroundList);
        if (exceptionList != null && Arrays.stream(exceptionList).anyMatch(
                s -> TextUtils.equals(requestorPackageName, s))) {
            return true;
        }
        try {
            return mActivityManager.getPackageImportance(requestorPackageName)
                    <= IMPORTANCE_FOREGROUND;
        } catch (SecurityException e) {
            Log.e(TAG, "Failed to check the app state", e);
            return false;
        }
    }

    /**
     * Check if the request comes from a foreground service.
     */
    private boolean isForegroundService(@NonNull String requestorPackageName) {
        try {
            int importance = mActivityManager.getPackageImportance(requestorPackageName);
            return IMPORTANCE_FOREGROUND < importance
                    && importance <= IMPORTANCE_FOREGROUND_SERVICE;
        } catch (SecurityException e) {
            Log.e(TAG, "Failed to check the app state", e);
            return false;
        }
    }
}
