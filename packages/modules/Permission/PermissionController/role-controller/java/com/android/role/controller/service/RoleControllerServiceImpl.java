/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.role.controller.service;

import android.app.role.RoleControllerService;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.Process;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.android.role.controller.model.Role;
import com.android.role.controller.model.Roles;
import com.android.role.controller.util.CollectionUtils;
import com.android.role.controller.util.LegacyRoleFallbackEnabledUtils;
import com.android.role.controller.util.PackageUtils;
import com.android.role.controller.util.UserUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Implementation of {@link RoleControllerService}.
 */
public class RoleControllerServiceImpl extends RoleControllerService {

    private static final String LOG_TAG = RoleControllerServiceImpl.class.getSimpleName();

    private static final boolean DEBUG = false;


    private UserHandle mUser;
    private Context mContext;
    private RoleManager mUserRoleManager;

    public RoleControllerServiceImpl() {}

    public RoleControllerServiceImpl(@NonNull UserHandle user, @NonNull Context context) {
        init(user, context);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        init(Process.myUserHandle(), this);
    }

    private void init(@NonNull UserHandle user, @NonNull Context context) {
        mUser = user;
        mContext = context;
        Context userContext = UserUtils.getUserContext(context, user);
        mUserRoleManager = userContext.getSystemService(RoleManager.class);
    }

    @Override
    @WorkerThread
    public boolean onGrantDefaultRoles() {
        if (DEBUG) {
            Log.i(LOG_TAG, "Granting default roles, user: " + mUser.myUserId());
        }

        // Gather the available roles for current user.
        ArrayMap<String, Role> roleMap = Roles.get(mContext);
        List<Role> roles = new ArrayList<>();
        List<String> roleNames = new ArrayList<>();
        ArraySet<String> addedRoleNames = new ArraySet<>();
        int roleMapSize = roleMap.size();
        for (int i = 0; i < roleMapSize; i++) {
            Role role = roleMap.valueAt(i);

            if (!role.isAvailableAsUser(mUser, mContext)) {
                continue;
            }
            roles.add(role);
            String roleName = role.getName();
            roleNames.add(roleName);
            if (!mUserRoleManager.isRoleAvailable(roleName)) {
                addedRoleNames.add(roleName);
            }
        }

        // TODO: Clean up holders of roles that will be removed.

        // Set the available role names in RoleManager.
        mUserRoleManager.setRoleNamesFromController(roleNames);

        int addedRoleNamesSize = addedRoleNames.size();
        for (int i = 0; i < addedRoleNamesSize; i++) {
            String roleName = addedRoleNames.valueAt(i);

            Role role = roleMap.get(roleName);
            role.onRoleAddedAsUser(mUser, mContext);
        }

        // Go through the holders of all roles.
        int rolesSize = roles.size();
        for (int rolesIndex = 0; rolesIndex < rolesSize; rolesIndex++) {
            Role role = roles.get(rolesIndex);

            String roleName = role.getName();

            // For each of the current holders, check if it is still qualified, redo grant if so, or
            // remove it otherwise.
            List<String> currentPackageNames = mUserRoleManager.getRoleHolders(roleName);
            int currentPackageNamesSize = currentPackageNames.size();
            for (int currentPackageNamesIndex = 0;
                    currentPackageNamesIndex < currentPackageNamesSize;
                    currentPackageNamesIndex++) {
                String packageName = currentPackageNames.get(currentPackageNamesIndex);

                if (role.isPackageQualifiedAsUser(packageName, mUser, mContext)) {
                    if (!role.shouldOnlyGrantWhenAdded()) {
                        // We should not override user set or fixed permissions because we are only
                        // redoing the grant here. Otherwise, user won't be able to revoke
                        // permissions granted by role.
                        addRoleHolderInternal(role, packageName, false, false, true);
                    }
                } else {
                    Log.i(LOG_TAG, "Removing package that no longer qualifies for the role,"
                            + " package: " + packageName + ", role: " + roleName);
                    removeRoleHolderInternal(role, packageName, false);
                }
            }

            // If there is no holder for a role now, or the role is static, we need to add default
            // or fallback holders, if any.
            currentPackageNames = mUserRoleManager.getRoleHolders(roleName);
            currentPackageNamesSize = currentPackageNames.size();
            boolean isStaticRole = role.isStatic();
            if (currentPackageNamesSize == 0 || isStaticRole) {
                List<String> packageNamesToAdd = null;
                if (addedRoleNames.contains(roleName) || isStaticRole) {
                    packageNamesToAdd = role.getDefaultHoldersAsUser(mUser, mContext);
                }
                if (packageNamesToAdd == null || packageNamesToAdd.isEmpty()) {
                    packageNamesToAdd = CollectionUtils.singletonOrEmpty(
                            role.getFallbackHolderAsUser(mUser, mContext));
                }

                int packageNamesToAddSize = packageNamesToAdd.size();
                for (int packageNamesToAddIndex = 0; packageNamesToAddIndex < packageNamesToAddSize;
                        packageNamesToAddIndex++) {
                    String packageName = packageNamesToAdd.get(packageNamesToAddIndex);

                    if (currentPackageNames.contains(packageName)) {
                        // This may happen when we are ensuring all default holders are added for
                        // static roles.
                        continue;
                    }
                    if (!role.isPackageQualifiedAsUser(packageName, mUser, mContext)) {
                        Log.e(LOG_TAG, "Default/fallback role holder package doesn't qualify for"
                                + " the role, package: " + packageName + ", role: " + roleName);
                        continue;
                    }
                    Log.i(LOG_TAG, "Adding package as default/fallback role holder, package: "
                            + packageName + ", role: " + roleName);
                    // TODO: If we don't override user here, user might end up missing incoming
                    // phone calls or SMS, so we just keep the old behavior. But overriding user
                    // choice about permission without explicit user action is bad, so maybe we
                    // should at least show a notification?
                    addRoleHolderInternal(role, packageName, role.shouldOverrideUserWhenGranting());
                }
            }

            // Ensure that an exclusive role has at most one holder.
            currentPackageNames = mUserRoleManager.getRoleHolders(roleName);
            currentPackageNamesSize = currentPackageNames.size();
            if (role.isExclusive() && currentPackageNamesSize > 1) {
                Log.w(LOG_TAG, "Multiple packages holding an exclusive role, role: "
                        + roleName);
                // No good way to determine who should be the only one, just keep the first one.
                for (int currentPackageNamesIndex = 1;
                        currentPackageNamesIndex < currentPackageNamesSize;
                        currentPackageNamesIndex++) {
                    String packageName = currentPackageNames.get(currentPackageNamesIndex);

                    Log.i(LOG_TAG, "Removing extraneous package for an exclusive role, package: "
                            + packageName + ", role: " + roleName);
                    removeRoleHolderInternal(role, packageName, false);
                }
            }
        }

        return true;
    }

    @Override
    @WorkerThread
    public boolean onAddRoleHolder(@NonNull String roleName, @NonNull String packageName,
            int flags) {
        if (!checkFlags(flags, RoleManager.MANAGE_HOLDERS_FLAG_DONT_KILL_APP)) {
            return false;
        }

        Role role = Roles.get(mContext).get(roleName);
        if (role == null) {
            Log.e(LOG_TAG, "Unknown role: " + roleName);
            return false;
        }
        if (!role.isAvailableAsUser(mUser, mContext)) {
            Log.e(LOG_TAG, "Role is unavailable: " + roleName);
            return false;
        }

        if (!role.isPackageQualifiedAsUser(packageName, mUser, mContext)) {
            Log.e(LOG_TAG, "Package does not qualify for the role, package: " + packageName
                    + ", role: " + roleName);
            return false;
        }

        boolean added = false;
        if (role.isExclusive()) {
            List<String> currentPackageNames = mUserRoleManager.getRoleHolders(roleName);
            int currentPackageNamesSize = currentPackageNames.size();
            for (int i = 0; i < currentPackageNamesSize; i++) {
                String currentPackageName = currentPackageNames.get(i);

                if (Objects.equals(currentPackageName, packageName)) {
                    Log.i(LOG_TAG, "Package is already a role holder, package: " + packageName
                            + ", role: " + roleName);
                    added = true;
                    continue;
                }

                boolean removed = removeRoleHolderInternal(role, currentPackageName, false);
                if (!removed) {
                    // TODO: Clean up?
                    return false;
                }
            }
        }

        boolean dontKillApp = hasFlag(flags, RoleManager.MANAGE_HOLDERS_FLAG_DONT_KILL_APP);
        added = addRoleHolderInternal(role, packageName, dontKillApp,
                role.shouldOverrideUserWhenGranting(), added);
        if (!added) {
            return false;
        }

        role.onHolderAddedAsUser(packageName, mUser, mContext);
        role.onHolderChangedAsUser(mUser, mContext);

        return true;
    }

    @Override
    @WorkerThread
    public boolean onRemoveRoleHolder(@NonNull String roleName, @NonNull String packageName,
            int flags) {
        if (!checkFlags(flags, RoleManager.MANAGE_HOLDERS_FLAG_DONT_KILL_APP)) {
            return false;
        }

        Role role = Roles.get(mContext).get(roleName);
        if (role == null) {
            Log.e(LOG_TAG, "Unknown role: " + roleName);
            return false;
        }
        if (!role.isAvailableAsUser(mUser, mContext)) {
            Log.e(LOG_TAG, "Role is unavailable: " + roleName);
            return false;
        }

        boolean dontKillApp = hasFlag(flags, RoleManager.MANAGE_HOLDERS_FLAG_DONT_KILL_APP);
        boolean removed = removeRoleHolderInternal(role, packageName, dontKillApp);
        if (!removed) {
            return false;
        }

        // TODO: Should we consider this successful regardless?
        boolean fallbackSuccessful = addFallbackRoleHolderMaybe(role);
        if (!fallbackSuccessful) {
            return false;
        }

        role.onHolderChangedAsUser(mUser, mContext);

        return true;
    }

    @Override
    @WorkerThread
    public boolean onClearRoleHolders(@NonNull String roleName, int flags) {
        if (!checkFlags(flags, RoleManager.MANAGE_HOLDERS_FLAG_DONT_KILL_APP)) {
            return false;
        }

        Role role = Roles.get(mContext).get(roleName);
        if (role == null) {
            Log.e(LOG_TAG, "Unknown role: " + roleName);
            return false;
        }
        if (!role.isAvailableAsUser(mUser, mContext)) {
            Log.e(LOG_TAG, "Role is unavailable: " + roleName);
            return false;
        }

        boolean dontKillApp = hasFlag(flags, RoleManager.MANAGE_HOLDERS_FLAG_DONT_KILL_APP);
        boolean cleared = clearRoleHoldersInternal(role, dontKillApp);
        if (!cleared) {
            return false;
        }

        // TODO: Should we consider this successful regardless?
        boolean fallbackSuccessful = addFallbackRoleHolderMaybe(role);
        if (!fallbackSuccessful) {
            return false;
        }

        role.onHolderChangedAsUser(mUser, mContext);

        return true;
    }

    @WorkerThread
    private boolean addRoleHolderInternal(@NonNull Role role, @NonNull String packageName,
            boolean overrideUser) {
        return addRoleHolderInternal(role, packageName, false, overrideUser, false);
    }

    @WorkerThread
    private boolean addRoleHolderInternal(@NonNull Role role, @NonNull String packageName,
            boolean dontKillApp, boolean overrideUser, boolean added) {
        role.grantAsUser(packageName, dontKillApp, overrideUser, mUser, mContext);

        String roleName = role.getName();
        if (!added) {
            added = mUserRoleManager.addRoleHolderFromController(roleName, packageName);
        }
        if (!added) {
            Log.e(LOG_TAG, "Failed to add role holder in RoleManager, package: " + packageName
                    + ", role: " + roleName);
        }
        return added;
    }

    @WorkerThread
    private boolean removeRoleHolderInternal(@NonNull Role role, @NonNull String packageName,
            boolean dontKillApp) {
        ApplicationInfo applicationInfo = PackageUtils.getApplicationInfoAsUser(packageName,
                mUser, mContext);
        if (applicationInfo == null) {
            Log.w(LOG_TAG, "Cannot get ApplicationInfo for package: " + packageName);
        }

        if (applicationInfo != null) {
            role.revokeAsUser(packageName, dontKillApp, false, mUser, mContext);
        }

        String roleName = role.getName();
        boolean removed = mUserRoleManager.removeRoleHolderFromController(roleName, packageName);
        if (!removed) {
            Log.e(LOG_TAG, "Failed to remove role holder in RoleManager," + " package: "
                    + packageName + ", role: " + roleName);
        }
        return removed;
    }

    @WorkerThread
    private boolean clearRoleHoldersInternal(@NonNull Role role, boolean dontKillApp) {
        String roleName = role.getName();
        List<String> packageNames = mUserRoleManager.getRoleHolders(roleName);
        boolean cleared = true;

        int packageNamesSize = packageNames.size();
        for (int i = 0; i < packageNamesSize; i++) {
            String packageName = packageNames.get(i);
            boolean removed = removeRoleHolderInternal(role, packageName, dontKillApp);
            if (!removed) {
                cleared = false;
            }
        }

        if (!cleared) {
            Log.e(LOG_TAG, "Failed to clear role holders, role: " + roleName);
        }
        return cleared;
    }

    @WorkerThread
    private boolean addFallbackRoleHolderMaybe(@NonNull Role role) {
        String roleName = role.getName();
        List<String> currentPackageNames = mUserRoleManager.getRoleHolders(roleName);
        if (!currentPackageNames.isEmpty()) {
            return true;
        }

        String fallbackPackageName = role.getFallbackHolderAsUser(mUser, mContext);
        if (fallbackPackageName == null) {
            return true;
        }

        if (!role.isPackageQualifiedAsUser(fallbackPackageName, mUser, mContext)) {
            Log.e(LOG_TAG, "Fallback role holder package doesn't qualify for the role, package: "
                    + fallbackPackageName + ", role: " + roleName);
            return false;
        }

        Log.i(LOG_TAG, "Adding package as fallback role holder, package: " + fallbackPackageName
                + ", role: " + roleName);
        // TODO: If we don't override user here, user might end up missing incoming
        // phone calls or SMS, so we just keep the old behavior. But overriding user
        // choice about permission without explicit user action is bad, so maybe we
        // should at least show a notification?
        return addRoleHolderInternal(role, fallbackPackageName,
                role.shouldOverrideUserWhenGranting());
    }

    @Override
    public boolean onIsApplicationQualifiedForRole(@NonNull String roleName,
            @NonNull String packageName) {
        // This API has been deprecated and Settings has been using onIsApplicationVisibleForRole()
        // instead.
        return false;
    }

    @Override
    public boolean onIsApplicationVisibleForRole(@NonNull String roleName,
            @NonNull String packageName) {
        Role role = Roles.get(mContext).get(roleName);
        if (role == null) {
            return false;
        }
        if (!role.isAvailableAsUser(mUser, mContext)) {
            return false;
        }
        if (!role.isPackageQualifiedAsUser(packageName, mUser, mContext)) {
            return false;
        }
        ApplicationInfo applicationInfo = PackageUtils.getApplicationInfoAsUser(packageName,
                mUser, mContext);
        if (applicationInfo == null || !role.isApplicationVisibleAsUser(applicationInfo, mUser,
                mContext)) {
            return false;
        }
        return true;
    }

    @Override
    public boolean onIsRoleVisible(@NonNull String roleName) {
        Role role = Roles.get(mContext).get(roleName);
        if (role == null) {
            return false;
        }
        if (!role.isAvailableAsUser(mUser, mContext)) {
            return false;
        }

        return role.isVisibleAsUser(mUser, mContext);
    }

    @Override
    @NonNull
    public List<String> onGetLegacyFallbackDisabledRoles() {
        return LegacyRoleFallbackEnabledUtils.getFallbackDisabledRoles(mUser, mContext);
    }


    private static boolean checkFlags(int flags, int allowedFlags) {
        if ((flags & allowedFlags) != flags) {
            Log.e(LOG_TAG, "flags is invalid, flags: 0x" + Integer.toHexString(flags)
                    + ", allowed flags: 0x" + Integer.toHexString(allowedFlags));
            return false;
        }
        return true;
    }

    private static boolean hasFlag(int flags, int flag) {
        return (flags & flag) == flag;
    }
}
