/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.role.controller.util;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.permission.flags.Flags;

import androidx.annotation.NonNull;

import com.android.modules.utils.build.SdkLevel;

public class ResourceUtils {

    private ResourceUtils() {}

    public static String RESOURCE_PACKAGE_NAME_PERMISSION_CONTROLLER =
            "com.android.permissioncontroller";

    /**
     * Get a {@link Resources} object to be used to access PermissionController resources.
     */
    @NonNull
    public static Resources getPermissionControllerResources(@NonNull Context context) {
        return getPermissionControllerContext(context).getResources();
    }

    @NonNull
    private static Context getPermissionControllerContext(@NonNull Context context) {
        if (!SdkLevel.isAtLeastV() || !Flags.systemServerRoleControllerEnabled()) {
            // We don't have the getPermissionControllerPackageName() API below V,
            // but role controller always runs in PermissionController below V.
            return context;
        }
        String packageName = context.getPackageManager().getPermissionControllerPackageName();
        try {
            return context.createPackageContext(packageName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException("Cannot create PermissionController context", e);
        }
    }
}
