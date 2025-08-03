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

package com.android.managedprovisioning.task;

import static android.app.AppOpsManager.OP_INTERACT_ACROSS_PROFILES;

import android.Manifest;
import android.app.AppOpsManager;
import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.CrossProfileApps;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.ArraySet;

import com.android.managedprovisioning.analytics.ProvisioningAnalyticsTracker;
import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.managedprovisioning.task.interactacrossprofiles.CrossProfileAppsSnapshot;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Task which resets the {@code INTERACT_ACROSS_USERS} app op when the OEM whitelist is changed.
 */
public class UpdateInteractAcrossProfilesAppOpTask extends AbstractProvisioningTask {

    private final CrossProfileAppsSnapshot mCrossProfileAppsSnapshot;
    private final CrossProfileApps mCrossProfileApps;
    private final DevicePolicyManager mDevicePolicyManager;
    private final AppOpsManager mAppOpsManager;
    private final PackageManager mPackageManager;
    private final UserManager mUserManager;

    public UpdateInteractAcrossProfilesAppOpTask(Context context,
            ProvisioningParams provisioningParams,
            Callback callback,
            ProvisioningAnalyticsTracker provisioningAnalyticsTracker) {
        super(context, provisioningParams, callback, provisioningAnalyticsTracker);
        mCrossProfileAppsSnapshot = new CrossProfileAppsSnapshot(context);
        mCrossProfileApps = context.getSystemService(CrossProfileApps.class);
        mDevicePolicyManager = context.getSystemService(DevicePolicyManager.class);
        mAppOpsManager = context.getSystemService(AppOpsManager.class);
        mPackageManager = context.getPackageManager();
        mUserManager = context.getSystemService(UserManager.class);
    }

    @Override
    public void run(int userId) {
        ProvisionLogger.logi("Running UpdateInteractAcrossProfilesAppOpTask.");
        Set<String> previousCrossProfileApps =
                mCrossProfileAppsSnapshot.hasSnapshot(userId) ?
                        mCrossProfileAppsSnapshot.getSnapshot(userId) :
                        new ArraySet<>();
        ProvisionLogger.logi("Previous cross profile apps snapshot: " + previousCrossProfileApps);

        mCrossProfileAppsSnapshot.takeNewSnapshot(userId);
        Set<String> currentCrossProfileApps = mCrossProfileAppsSnapshot.getSnapshot(userId);
        ProvisionLogger.logi("current cross profile apps snapshot: " + currentCrossProfileApps);

        updateAfterOtaChanges(previousCrossProfileApps, currentCrossProfileApps);
    }

    private void updateAfterOtaChanges(
            Set<String> previousCrossProfilePackages, Set<String> currentCrossProfilePackages) {
        mCrossProfileApps.resetInteractAcrossProfilesAppOps(
                previousCrossProfilePackages, currentCrossProfilePackages);
        Set<String> newCrossProfilePackages = new HashSet<>(currentCrossProfilePackages);
        newCrossProfilePackages.removeAll(previousCrossProfilePackages);

        grantNewConfigurableDefaultCrossProfilePackages(newCrossProfilePackages);
        reapplyCrossProfileAppsPermission();
    }

    private void grantNewConfigurableDefaultCrossProfilePackages(
            Set<String> newCrossProfilePackages) {
        ProvisionLogger.logi("Granting INTERACT_ACROSS_PROFILES to the new default cross profile "
                + "apps: " + newCrossProfilePackages);

        final String op =
                AppOpsManager.permissionToOp(Manifest.permission.INTERACT_ACROSS_PROFILES);
        for (String crossProfilePackageName : newCrossProfilePackages) {
            if (!mCrossProfileApps.canConfigureInteractAcrossProfiles(crossProfilePackageName)) {
                ProvisionLogger.logi("Can't grant appop to unconfigurable app: "
                        + crossProfilePackageName);
                continue;
            }
            try {
                final int uid = mPackageManager.getPackageUid(
                        crossProfilePackageName, /* flags= */ 0);
                if (appOpIsChangedFromDefault(op, uid, crossProfilePackageName)) {
                    ProvisionLogger.logi("Can't change appop from non default value: ("
                            + crossProfilePackageName + ", " + mAppOpsManager.unsafeCheckOpNoThrow(
                                    op, uid, crossProfilePackageName) + ")");
                    continue;
                }
            } catch (PackageManager.NameNotFoundException e) {
                ProvisionLogger.loge("Missing package, this should not happen.", e);
                continue;
            }
            ProvisionLogger.logi("Setting appop to allowed for " + crossProfilePackageName);
            mCrossProfileApps.setInteractAcrossProfilesAppOp(crossProfilePackageName,
                    AppOpsManager.MODE_ALLOWED);
        }
    }

    /**
     * Iterate over all apps and reapply the app-op permission.
     *
     * <p>This is to fix an issue that existed in Android 11 where the appop was set per-package
     * instead of per-UID causing issues for applications with shared UIDs.
     */
    private void reapplyCrossProfileAppsPermission() {
        ProvisionLogger.logi("Reapplying INTERACT_ACROSS_PROFILES for apps with non default "
                + "values.");
        final Set<Integer> uids = getUidsWithNonDefaultMode();
        reapplyCrossProfileAppsPermissionForUids(uids);
    }

    private Set<Integer> getUidsWithNonDefaultMode() {
        final String op = AppOpsManager.permissionToOp(
                Manifest.permission.INTERACT_ACROSS_PROFILES);
        final Set<Integer> uids = new HashSet<>();
        for (ApplicationInfo appInfo : getAllInstalledApps()) {
            if (appOpIsChangedFromDefault(
                    op, appInfo.uid, appInfo.packageName)) {
                uids.add(appInfo.uid);
            }
        }
        return uids;
    }

    private Set<ApplicationInfo> getAllInstalledApps() {
        final Set<ApplicationInfo> apps = new HashSet<>();
        List<UserHandle> profiles = mUserManager.getAllProfiles();
        for (UserHandle profile : profiles) {
            if (profile.getIdentifier() != mContext.getUserId()
                    && !mUserManager.isManagedProfile(profile.getIdentifier())) {
                continue;
            }
            try {
                apps.addAll(
                        mContext.createPackageContextAsUser(
                        /* packageName= */ "android", /* flags= */ 0, profile)
                                .getPackageManager().getInstalledApplications(/* flags= */ 0));
            } catch (PackageManager.NameNotFoundException ignored) {
                // Should never happen.
            }
        }
        return apps;
    }

    private void reapplyCrossProfileAppsPermissionForUids(Set<Integer> uids) {
        for (int uid : uids) {
            reapplyCrossProfileAppsPermissionForUid(uid);
        }
    }

    private void reapplyCrossProfileAppsPermissionForUid(int uid) {
        final String op = AppOpsManager.permissionToOp(
                Manifest.permission.INTERACT_ACROSS_PROFILES);
        final String[] packages = mPackageManager.getPackagesForUid(uid);
        final int consolidatedUidMode = getConsolidatedModeForPackagesInUid(uid, packages, op);
        setAppOpForPackagesInUid(uid, packages, consolidatedUidMode);
    }

    private int getConsolidatedModeForPackagesInUid(int uid, String[] packages, String op) {
        int uidMode = AppOpsManager.MODE_DEFAULT;
        for (String packageName : packages) {
            if (mCrossProfileApps.canConfigureInteractAcrossProfiles(packageName)) {
                final int packageMode = mAppOpsManager.unsafeCheckOpNoThrow(op, uid, packageName);
                if (shouldUpdateUidMode(packageMode, uidMode)) {
                    uidMode = packageMode;
                }
            }
        }
        return uidMode;
    }

    private boolean shouldUpdateUidMode(int packageMode, @AppOpsManager.Mode int uidMode) {
        if (!appOpIsChangedFromDefault(packageMode)) {
            return false;
        }
        if (!appOpIsChangedFromDefault(uidMode)) {
            return true;
        }
        if (packageMode == AppOpsManager.MODE_ALLOWED) {
            return true;
        }
        return false;
    }

    private void setAppOpForPackagesInUid(
            int uid, String[] packages, @AppOpsManager.Mode int mode) {
        for (String packageName : packages) {
            setInteractAcrossProfilesAppOpForPackage(uid, packageName, mode);
        }
    }

    /**
     * Sets the package appop to default mode and the uid appop to {@code mode}.
     */
    private void setInteractAcrossProfilesAppOpForPackage(
            int uid, String packageName, @AppOpsManager.Mode int mode) {
        final String op =
                AppOpsManager.permissionToOp(Manifest.permission.INTERACT_ACROSS_PROFILES);
        int previousMode = mAppOpsManager.unsafeCheckOpNoThrow(op, uid, packageName);
        mAppOpsManager.setMode(
                OP_INTERACT_ACROSS_PROFILES, uid, packageName,
                AppOpsManager.opToDefaultMode(OP_INTERACT_ACROSS_PROFILES));
        mAppOpsManager.setUidMode(OP_INTERACT_ACROSS_PROFILES, uid, mode);
        int currentMode = mAppOpsManager.unsafeCheckOpNoThrow(op, uid, packageName);
        ProvisionLogger.logi(
                "Reapplying INTERACT_ACROSS_PROFILES for " + packageName + " with uid + " + uid
                        + ", previous mode: " + previousMode + ", current mode is " + currentMode);
    }

    private boolean appOpIsChangedFromDefault(String op, int uid, String packageName) {
        return mAppOpsManager.unsafeCheckOpNoThrow(op, uid, packageName)
                != AppOpsManager.MODE_DEFAULT;
    }

    private boolean appOpIsChangedFromDefault(int mode) {
        return mode != AppOpsManager.MODE_DEFAULT;
    }
}
