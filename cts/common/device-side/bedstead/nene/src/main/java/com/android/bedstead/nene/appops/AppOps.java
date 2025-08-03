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

package com.android.bedstead.nene.appops;

import static android.os.Build.VERSION_CODES.Q;

import static com.android.bedstead.nene.permissions.CommonPermissions.MANAGE_APP_OPS_MODES;

import android.annotation.TargetApi;
import android.app.AppOpsManager;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.annotations.Experimental;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.packages.Package;
import com.android.bedstead.nene.permissions.PermissionContext;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.nene.utils.Versions;

@Experimental
/** Manage AppOps for a package. */
public final class AppOps {

    private static final AppOpsManager sAppOpsManager =
            TestApis.context().instrumentedContext().getSystemService(AppOpsManager.class);
    private final Package mPackage;
    private final UserReference mUser;

    public AppOps(Package pkg, UserReference user) {
        this.mPackage = pkg;
        this.mUser = user;
    }

    /** Set an AppOp for the given permission. */
    @Experimental
    public void setPermission(String permissionName, AppOpsMode mode) {
        set(AppOpsManager.permissionToOp(permissionName), mode);
    }

    /** Set an AppOp for the given package. */
    public void set(String appOpName, AppOpsMode mode) {
        try (PermissionContext p = TestApis.permissions().withPermission(MANAGE_APP_OPS_MODES)) {
            sAppOpsManager.setMode(appOpName, mPackage.uid(mUser),
                    mPackage.packageName(), mode.mValue);
        }
    }

    /** Get the AppOp mode for the given package. */
    @TargetApi(Q)
    public AppOpsMode get(String appOpName) {
        Versions.requireMinimumVersion(Q);

        return AppOpsMode.forValue(
                sAppOpsManager.unsafeCheckOpNoThrow(
                        maybeConvertPermissionToAppOp(appOpName), mPackage.uid(mUser),
                        mPackage.packageName()));
    }

    private String maybeConvertPermissionToAppOp(String permission) {
        if (permission.contains("android:")) {
            return permission;
        }
        String appOp = AppOpsManager.permissionToOp(permission);
        if (appOp == null) {
            throw new NeneException(permission + " is not a valid appOp");
        }
        return appOp;
    }
}
