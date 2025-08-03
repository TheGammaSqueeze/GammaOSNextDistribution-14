/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.healthconnect.permission;

import android.annotation.NonNull;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.health.connect.Constants;
import android.health.connect.HealthConnectManager;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.ArraySet;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tracks apps which support {@link android.content.Intent#ACTION_VIEW_PERMISSION_USAGE} with {@link
 * HealthConnectManager#CATEGORY_HEALTH_PERMISSIONS}.
 *
 * @hide
 */
public class HealthPermissionIntentAppsTracker {
    private static final String TAG = "HealthPermIntentTracker";
    private static final Intent HEALTH_PERMISSIONS_USAGE_INTENT = getHealthPermissionsUsageIntent();

    private final PackageManager mPackageManager;
    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private Map<UserHandle, Set<String>> mUserToHealthPackageNamesMap;

    public HealthPermissionIntentAppsTracker(@NonNull Context context) {
        mPackageManager = context.getPackageManager();
        initPerUserMapping(context);
    }

    /**
     * Checks if the given app supports {@link android.content.Intent#ACTION_VIEW_PERMISSION_USAGE}
     * with {@link HealthConnectManager#CATEGORY_HEALTH_PERMISSIONS}
     *
     * @param packageName: name of the package to check
     * @param userHandle: the user to query
     */
    boolean supportsPermissionUsageIntent(
            @NonNull String packageName, @NonNull UserHandle userHandle) {
        // Consider readWrite lock if this is performance bottleneck.
        synchronized (mLock) {
            if (!mUserToHealthPackageNamesMap.containsKey(userHandle)) {
                Log.w(
                        TAG,
                        "Requested user handle: "
                                + userHandle.toString()
                                + " is not present in the state.");
                return false;
            }

            if (!mUserToHealthPackageNamesMap.get(userHandle).contains(packageName)) {
                updateStateAndGetIfIntentWasRemoved(packageName, userHandle);
            }

            return mUserToHealthPackageNamesMap.get(userHandle).contains(packageName);
        }
    }

    /**
     * Updates package state if needed, returns whether activity for {@link
     * android.content.Intent#ACTION_VIEW_PERMISSION_USAGE} with {@link
     * HealthConnectManager#CATEGORY_HEALTH_PERMISSIONS} support has been disabled/removed.
     */
    boolean updateStateAndGetIfIntentWasRemoved(
            @NonNull String packageNameToUpdate, @NonNull UserHandle userHandle) {
        synchronized (mLock) {
            if (!mUserToHealthPackageNamesMap.containsKey(userHandle)) {
                mUserToHealthPackageNamesMap.put(userHandle, new ArraySet<>());
            }
        }

        Intent permissionPackageUsageIntent = getHealthPermissionsUsageIntent();
        permissionPackageUsageIntent.setPackage(packageNameToUpdate);
        boolean removedIntent = false;
        if (!mPackageManager
                .queryIntentActivitiesAsUser(
                        permissionPackageUsageIntent,
                        PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL),
                        userHandle)
                .isEmpty()) {
            synchronized (mLock) {
                mUserToHealthPackageNamesMap.get(userHandle).add(packageNameToUpdate);
            }
        } else {
            synchronized (mLock) {
                removedIntent =
                        mUserToHealthPackageNamesMap.get(userHandle).remove(packageNameToUpdate);
            }
        }
        logStateIfDebugMode(userHandle);
        return removedIntent;
    }

    private static Intent getHealthPermissionsUsageIntent() {
        Intent healthIntent = new Intent(Intent.ACTION_VIEW_PERMISSION_USAGE);
        healthIntent.addCategory(HealthConnectManager.CATEGORY_HEALTH_PERMISSIONS);
        return healthIntent;
    }

    private void initPerUserMapping(Context context) {
        synchronized (mLock) {
            mUserToHealthPackageNamesMap = new HashMap<>();
        }
        List<UserHandle> userHandles =
                context.getSystemService(UserManager.class)
                        .getUserHandles(/* excludeDying= */ true);
        for (UserHandle userHandle : userHandles) {
            initPackageSetForUser(userHandle);
        }
    }

    /** Update list of health apps for given user. */
    private void initPackageSetForUser(@NonNull UserHandle userHandle) {
        List<ResolveInfo> healthAppInfos = getHealthIntentSupportiveAppsForUser(userHandle);
        Set<String> healthApps = new ArraySet<String>(healthAppInfos.size());
        for (ResolveInfo info : healthAppInfos) {
            String packageName = extractPackageName(info);
            if (packageName != null) {
                healthApps.add(packageName);
            }
        }
        synchronized (mLock) {
            mUserToHealthPackageNamesMap.put(userHandle, healthApps);
        }
        logStateIfDebugMode(userHandle);
    }

    private List<ResolveInfo> getHealthIntentSupportiveAppsForUser(@NonNull UserHandle userHandle) {
        return mPackageManager.queryIntentActivitiesAsUser(
                HEALTH_PERMISSIONS_USAGE_INTENT,
                PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL),
                userHandle);
    }

    @SuppressWarnings("NullAway") // TODO(b/317029272): fix this suppression
    private String extractPackageName(ResolveInfo info) {
        if (info == null
                || info.activityInfo == null
                || info.activityInfo.applicationInfo == null) {
            Log.w(TAG, "Can't fetch application info from resolve info.");
            return null;
        }
        return info.activityInfo.applicationInfo.packageName;
    }

    private void logStateIfDebugMode(@NonNull UserHandle userHandle) {
        if (Constants.DEBUG) {
            Log.d(TAG, "State for user: " + userHandle.getIdentifier());
            synchronized (mLock) {
                Log.d(TAG, mUserToHealthPackageNamesMap.toString());
            }
        }
    }
}
