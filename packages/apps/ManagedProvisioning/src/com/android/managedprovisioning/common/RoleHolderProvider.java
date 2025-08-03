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

package com.android.managedprovisioning.common;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.Nullable;

/**
 * A provider for the role holder package name.
 */
public interface RoleHolderProvider {
    RoleHolderProvider DEFAULT = (Context context) -> {
        DevicePolicyManager dpm = context.getSystemService(DevicePolicyManager.class);
        String deviceManagerConfig = dpm.getDevicePolicyManagementRoleHolderPackage();
        if (TextUtils.isEmpty(deviceManagerConfig)) {
            ProvisionLogger.logi("Role holder is not defined for this device.");
            return null;
        }
        return RoleHolderParser.getRoleHolderPackage(deviceManagerConfig);
    };

    /**
     * Returns the package name of the role holder.
     */
    @Nullable
    String getPackageName(Context context);
}
