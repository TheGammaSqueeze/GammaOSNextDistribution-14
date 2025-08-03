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

package com.android.role.controller.model;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.os.Build;
import android.os.UserHandle;

import androidx.annotation.NonNull;

import com.android.modules.utils.build.SdkLevel;
import com.android.role.controller.util.UserUtils;

import java.util.Objects;

/**
 * A permission to be granted or revoke by a {@link Role}.
 */
public class Permission {

    /**
     * The name of the permission.
     */
    @NonNull
    private final String mName;

    /**
     * The minimum SDK version for this permission to be granted.
     */
    private final int mMinSdkVersion;

    /**
     * The minimum SDK version for this permission to be optionally granted (when it is grantable).
     */
    private final int mOptionalMinSdkVersion;

    public Permission(@NonNull String name, int minSdkVersion, int optionalMinSdkVersion) {
        mName = name;
        mMinSdkVersion = minSdkVersion;
        mOptionalMinSdkVersion = optionalMinSdkVersion;
    }

    @NonNull
    public String getName() {
        return mName;
    }

    public int getMinSdkVersion() {
        return mMinSdkVersion;
    }

    public int getOptionalMinSdkVersion() {
        return mOptionalMinSdkVersion;
    }

    /**
     * Check whether this permission is available.
     *
     * @param user the user to check for
     * @param context the {@code Context} to retrieve system services
     *
     * @return whether this permission is available
     */
    public boolean isAvailableAsUser(@NonNull UserHandle user, @NonNull Context context) {
        if (Build.VERSION.SDK_INT >= mMinSdkVersion
                // Workaround to match the value 35 for V in roles.xml before SDK finalization.
                || (mMinSdkVersion == 35 && SdkLevel.isAtLeastV())) {
            return true;
        }
        if (Build.VERSION.SDK_INT >= mOptionalMinSdkVersion) {
            Context userContext = UserUtils.getUserContext(context, user);
            PackageManager userPackageManager = userContext.getPackageManager();
            PermissionInfo permissionInfo;
            try {
                permissionInfo = userPackageManager.getPermissionInfo(mName, 0);
            } catch (PackageManager.NameNotFoundException e) {
                return false;
            }
            return permissionInfo.getProtection() == PermissionInfo.PROTECTION_DANGEROUS
                    || (permissionInfo.getProtectionFlags() & PermissionInfo.PROTECTION_FLAG_ROLE)
                            == PermissionInfo.PROTECTION_FLAG_ROLE
                    || (permissionInfo.getProtectionFlags() & PermissionInfo.PROTECTION_FLAG_APPOP)
                            == PermissionInfo.PROTECTION_FLAG_APPOP;
        }
        return false;
    }

    /**
     * Return a new permission with the specified SDK versions, or this permission if it already has
     * the same SDK versions.
     *
     * @param minSdkVersion the minimum SDK version
     * @param optionalMinSdkVersion the optional minimum SDK version
     * @return a permission with the specified SDK versions
     */
    @NonNull
    public Permission withSdkVersions(int minSdkVersion, int optionalMinSdkVersion) {
        if (mMinSdkVersion == minSdkVersion && mOptionalMinSdkVersion == optionalMinSdkVersion) {
            return this;
        }
        return new Permission(mName, minSdkVersion, optionalMinSdkVersion);
    }

    @Override
    public String toString() {
        return "Permission{"
                + "mName='" + mName + '\''
                + ", mMinSdkVersion=" + mMinSdkVersion
                + ", mOptionalMinSdkVersion=" + mOptionalMinSdkVersion
                + '}';
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        Permission that = (Permission) object;
        return mMinSdkVersion == that.mMinSdkVersion
                && mOptionalMinSdkVersion == that.mOptionalMinSdkVersion
                && mName.equals(that.mName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mName, mMinSdkVersion, mOptionalMinSdkVersion);
    }
}
