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

package com.android.tv.settings.library.enterprise.apps;

import android.app.admin.DevicePolicyManager;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.os.UserManager;

/**
 * Lists installed apps across all users that have been granted one or more specific permissions by
 * the admin.
 * Forked from:
 * Settings/src/com/android/settings/applications/AppWithAdminGrantedPermissionsLister.java
 */
public abstract class AppWithAdminGrantedPermissionsLister extends AppLister {
    private final String[] mPermissions;
    private final IPackageManager mPackageManagerService;
    private final DevicePolicyManager mDevicePolicyManager;

    public AppWithAdminGrantedPermissionsLister(String[] permissions,
            PackageManager packageManager, IPackageManager packageManagerService,
            DevicePolicyManager devicePolicyManager, UserManager userManager) {
        super(packageManager, userManager);
        mPermissions = permissions;
        mPackageManagerService = packageManagerService;
        mDevicePolicyManager = devicePolicyManager;
    }

    @Override
    protected boolean includeInCount(ApplicationInfo info) {
        return AppWithAdminGrantedPermissionsCounter.includeInCount(mPermissions,
                mDevicePolicyManager, mPm, mPackageManagerService, info);
    }
}
