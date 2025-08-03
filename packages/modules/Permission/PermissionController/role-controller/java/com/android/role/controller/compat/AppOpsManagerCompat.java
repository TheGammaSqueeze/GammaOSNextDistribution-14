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


package com.android.role.controller.compat;

import android.app.AppOpsManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.modules.utils.build.SdkLevel;

import java.util.Objects;

/** Helper for accessing features in {@link AppOpsManager}. */
public class AppOpsManagerCompat {

    private AppOpsManagerCompat() {}

    /**
     * @see AppOpsManager#permissionToOp().
    */
    @Nullable
    public static String permissionToOp(@NonNull String permission) {
        if (!SdkLevel.isAtLeastV()) {
            // On Android U and below, PACKAGE_USAGE_STATUS is missing from the mapping
            // in the framework.
            if (Objects.equals(permission, android.Manifest.permission.PACKAGE_USAGE_STATS)) {
                return AppOpsManager.OPSTR_GET_USAGE_STATS;
            }
        }
        return AppOpsManager.permissionToOp(permission);
    }
}