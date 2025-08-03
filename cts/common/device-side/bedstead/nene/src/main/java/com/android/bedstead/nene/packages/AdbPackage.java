/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.bedstead.nene.packages;

import static android.content.pm.PackageManager.GET_PERMISSIONS;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.users.UserReference;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Resolved information about a package on the device.
 */
public final class AdbPackage {

    private static final String LOG_TAG = "Package";
    private final PackageManager mPackageManager;

    static final class MutablePackage {
        String mPackageName;
        Map<UserReference, MutableUserPackage> mInstalledOnUsers;
        Set<String> mInstallPermissions;
    }

    static final class MutableUserPackage {
        Set<String> mGrantedPermissions;

        @Override
        public String toString() {
            return "UserPackage{grantedPermissions=" + mGrantedPermissions + "}";
        }
    }

    private final MutablePackage mMutablePackage;
    private final Set<String> mRequestedPermissions;

    AdbPackage(MutablePackage mutablePackage) {
        mMutablePackage = mutablePackage;
        mRequestedPermissions = new HashSet<>();
        mPackageManager = TestApis.context().instrumentedContext().getPackageManager();

        try {
            PackageInfo packageInfo = mPackageManager.getPackageInfo(
                    mMutablePackage.mPackageName, /* flags= */ GET_PERMISSIONS);
            if (packageInfo.requestedPermissions != null) {
                mRequestedPermissions.addAll(Arrays.asList(packageInfo.requestedPermissions));
            }
        } catch (PackageManager.NameNotFoundException e) {
            // This happens frequently and is not useful to log
            // TODO(scottjonathan): Figure out why we get this for packages which should exist
        }
    }

    /** Get {@link UserReference}s who have this {@link java.lang.Package} installed. */
    public Set<UserReference> installedOnUsers() {
        return mMutablePackage.mInstalledOnUsers.keySet();
    }

    /**
     * Get all permissions granted to this package on the given user.
     *
     * <p>This will also include permissions which are granted for all users.
     */
    public Set<String> grantedPermissions(UserReference user) {
        MutableUserPackage userPackage = mMutablePackage.mInstalledOnUsers.get(user);
        if (userPackage == null) {
            return new HashSet<>();
        }

        Set<String> mergedPermissions = new HashSet<>();
        mergedPermissions.addAll(mMutablePackage.mInstallPermissions);
        mergedPermissions.addAll(userPackage.mGrantedPermissions);

        return mergedPermissions;
    }

    /**
     * Get all permissions requested by this package.
     */
    public Set<String> requestedPermissions() {
        return mRequestedPermissions;
    }

    /**
     * Get the package name.
     */
    public String packageName() {
        return mMutablePackage.mPackageName;
    }

    @Override
    public String toString() {
        StringBuilder stringBuilder = new StringBuilder("Package{");
        stringBuilder.append("packageName=" + mMutablePackage.mPackageName);
        stringBuilder.append(", installedOnUsers=" + mMutablePackage.mInstalledOnUsers);
        stringBuilder.append("}");
        return stringBuilder.toString();
    }
}
