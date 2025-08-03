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

package com.android.car.telemetry;

import static com.android.car.telemetry.CarTelemetryService.DEBUG;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.car.builtin.util.Slogf;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.SparseArray;

import com.android.car.CarLog;
import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Maps app package name to UID using {@link PackageManager}, and app install/remove and user
 * add/remove broadcasts. It also stores some uninstalled apps, because some publishers may have
 * data for recent uninstalled apps.
 *
 * <p>See https://source.android.com/security/app-sandbox to learn more about UIDs. Note that an app
 * (package name) has single UID, but a UID can have multiple apps, there is one-to-many
 * relationship.
 *
 * <p>Use {@code adb shell pm list packages -U -u --show-versioncode} to list the packages.
 */
public class UidPackageMapper {
    // Store removed app info just in case some publishers (e.g. ConnectivityPublisher) may send
    // data related to them.
    private static final int DEFAULT_MAX_REMOVED_APPS_COUNT = 100;

    private final Context mContext;
    private final Handler mTelemetryHandler;
    private final int mMaxRemovedAppsCount;

    // Maps uid to the list of AppInfo.
    private final SparseArray<ArrayList<AppInfo>> mUidAppInfo = new SparseArray<>();

    // Caches "mMaxRemovedAppsCount" removed apps, as there will be statistics even for the
    // uninstalled apps.
    //
    // Note that it may contain different AppInfo object for the same uid/packageName, because
    // of refetchAllAppInfo() method.
    private final ArrayDeque<AppInfo> mRemovedApps = new ArrayDeque<>();

    private final BroadcastReceiver mAppUpdateReceiver = new AppUpdateReceiver();
    private final BroadcastReceiver mUserUpdateReceiver = new UserUpdateReceiver();

    /** Constructs an instance. */
    public UidPackageMapper(@NonNull Context context, @NonNull Handler telemetryHandler) {
        this(context, telemetryHandler, DEFAULT_MAX_REMOVED_APPS_COUNT);
    }

    @VisibleForTesting
    UidPackageMapper(
            @NonNull Context context, @NonNull Handler telemetryHandler, int maxRemovedAppsCount) {
        mContext = context;
        mTelemetryHandler = telemetryHandler;
        mMaxRemovedAppsCount = maxRemovedAppsCount;
    }

    /**
     * Subscribes for broadcast events and initializes the mapper by fetching all the apps from
     * PackageManager.
     */
    public void init() {
        // Setup broadcast receiver for app updates.
        IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_REPLACED);
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addDataScheme("package");
        mContext.registerReceiverForAllUsers(mAppUpdateReceiver, filter, null, null);

        // Setup receiver for user initialize (happens once for a new user) and if a user is
        // removed.
        filter = new IntentFilter(Intent.ACTION_USER_INITIALIZE);
        filter.addAction(Intent.ACTION_USER_REMOVED);
        mContext.registerReceiverForAllUsers(mUserUpdateReceiver, filter, null, null);

        refetchAllAppInfo(mContext);
    }

    /** Releases resources. */
    public void release() {
        mContext.unregisterReceiver(mAppUpdateReceiver);
        mContext.unregisterReceiver(mUserUpdateReceiver);
    }

    /**
     * Returns the list of packages for uid, including APEX and some uninstalled apps. May return
     * an empty list.
     */
    @NonNull
    public List<String> getPackagesForUid(int uid) {
        List<AppInfo> uidApps = mUidAppInfo.get(uid);
        if (uidApps == null) {
            return List.of();
        }
        ArrayList<String> result = new ArrayList<>();
        for (int i = 0; i < uidApps.size(); i++) {
            result.add(uidApps.get(i).mPackageName);
        }
        return result;
    }

    /** Gets AppInfo from "mUidAppInfo" map. */
    @Nullable
    private AppInfo getAppInfo(int uid, @NonNull String packageName) {
        ArrayList<AppInfo> uidApps = mUidAppInfo.get(uid);
        if (uidApps == null) {
            uidApps = new ArrayList<>();
            mUidAppInfo.put(uid, uidApps);
        }
        for (int i = 0; i < uidApps.size(); i++) {
            AppInfo current = uidApps.get(i);
            if (current.mPackageName.equals(packageName)) {
                return current;
            }
        }
        return null;
    }

    private void onAppAddedOrUpdated(int uid, @NonNull String packageName) {
        AppInfo appInfo = getAppInfo(uid, packageName);
        if (appInfo == null) {
            // The uid always exists in mUidAppInfo after getAppInfo() was called.
            mUidAppInfo.get(uid).add(new AppInfo(uid, packageName));
        } else {
            appInfo.mIsRemoved = false;
        }
    }

    /** Marks the AppInfo removed */
    private void onAppRemoved(int uid, @NonNull String packageName) {
        AppInfo appInfo = getAppInfo(uid, packageName);
        if (appInfo == null) {
            Slogf.i(
                    CarLog.TAG_TELEMETRY,
                    "UidPackageMapper failed to remove the app from its cache, "
                            + "the app not found.");
            return;
        }
        if (appInfo.mIsRemoved) {
            return; // ignore the already removed apps
        }
        appInfo.mIsRemoved = true;

        mRemovedApps.add(appInfo);
        if (mRemovedApps.size() > mMaxRemovedAppsCount) {
            AppInfo completelyRemoved = mRemovedApps.removeFirst();
            if (completelyRemoved.mIsRemoved && mUidAppInfo.contains(completelyRemoved.mUid)) {
                mUidAppInfo
                        .get(completelyRemoved.mUid)
                        .removeIf(app -> app.mPackageName.equals(completelyRemoved.mPackageName));
            }
        }
        return;
    }

    /** Returns installed and uninstalled packages, including Apex packages. */
    @NonNull
    private static List<PackageInfo> getAllPackagesIncludingApex(
            @NonNull PackageManager pm, @NonNull UserHandle user) {
        ArrayList<PackageInfo> packages =
                new ArrayList<>(
                        pm.getInstalledPackagesAsUser(
                                PackageManager.MATCH_UNINSTALLED_PACKAGES
                                        | PackageManager.MATCH_ANY_USER,
                                user.getIdentifier()));
        // Get only installed APEX packages, because inactive apexes can conflict with active ones.
        for (PackageInfo info : pm.getInstalledPackages(PackageManager.MATCH_APEX)) {
            if (info.isApex) {
                packages.add(info);
            }
        }
        return packages;
    }

    private void refetchAllAppInfo(@NonNull Context context) {
        UserManager um = context.getSystemService(UserManager.class);
        PackageManager pm = context.getPackageManager();
        List<UserHandle> users = um.getUserHandles(/* excludeDying= */ true);
        mUidAppInfo.clear();
        if (DEBUG) {
            Slogf.d(CarLog.TAG_TELEMETRY, "Fetching packages for %d users", users.size());
        }
        for (int i = 0; i < users.size(); i++) {
            List<PackageInfo> packages = getAllPackagesIncludingApex(pm, users.get(i));
            for (int j = 0; j < packages.size(); j++) {
                onAppAddedOrUpdated(
                        packages.get(j).applicationInfo.uid, packages.get(j).packageName);
            }
        }
        // Add removed apps back to the "mUidAppInfo".
        for (AppInfo removedApp : mRemovedApps) {
            // This "appInfo" instance is different than the "removedApp" instance.
            AppInfo appInfo = getAppInfo(removedApp.mUid, removedApp.mPackageName);
            if (appInfo == null) {
                onAppAddedOrUpdated(removedApp.mUid, removedApp.mPackageName);
            }
        }
    }

    private class AppUpdateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DEBUG) {
                Slogf.d(
                        CarLog.TAG_TELEMETRY,
                        "UidPackageMapper received intent %s",
                        intent);
            }
            if (intent == null || intent.getAction() == null || intent.getData() == null) {
                Slogf.w(
                        CarLog.TAG_TELEMETRY,
                        "UidPackageMapper received null intent or null action or null data."
                                + " Ignoring.");
                return;
            }
            /**
             * App updates (ACTION_PACKAGE_REPLACED) actually consist of REMOVE, ADD, and then
             * REPLACE broadcasts. To avoid waste, we ignore the extra REMOVE and ADD broadcasts
             * that contain the replacing flag (EXTRA_REPLACING).
             */
            if (!intent.getAction().equals(Intent.ACTION_PACKAGE_REPLACED)
                    && intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                return; // Keep only replacing or normal add and remove.
            }

            Bundle extra = intent.getExtras();
            if (extra == null) {
                Slogf.w(
                        CarLog.TAG_TELEMETRY,
                        "UidPackageMapper received an intent with null extras. Ignoring.");
                return;
            }
            int uid = extra.getInt(Intent.EXTRA_UID, -1);
            String packageName = intent.getData().getSchemeSpecificPart();

            if (uid == -1) {
                Slogf.w(
                        CarLog.TAG_TELEMETRY,
                        "UidPackageMapper received app update intent with no uid. Ignoring.");
                return;
            }

            if (intent.getAction().equals(Intent.ACTION_PACKAGE_REMOVED)) {
                mTelemetryHandler.post(() -> onAppRemoved(uid, packageName));
            } else {
                mTelemetryHandler.post(() -> onAppAddedOrUpdated(uid, packageName));
            }
        }
    }

    private class UserUpdateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            mTelemetryHandler.post(() -> refetchAllAppInfo(context));
        }
    }

    /** Stores information about an app identified by "uid" and "packageName". */
    private static class AppInfo {
        int mUid;
        @NonNull String mPackageName;
        boolean mIsRemoved;

        AppInfo(int uid, @NonNull String packageName) {
            mUid = uid;
            mPackageName = packageName;
            mIsRemoved = false;
        }
    }
}
