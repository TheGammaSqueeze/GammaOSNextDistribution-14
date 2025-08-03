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

package com.android.role.controller.behavior;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.os.Build;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.android.role.controller.model.Role;
import com.android.role.controller.model.RoleBehavior;
import com.android.role.controller.util.UserUtils;

/**
 * Class for behavior of the Retail Demo role.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
public class RetailDemoRoleBehavior implements RoleBehavior {

    @Override
    public Boolean isPackageQualifiedAsUser(@NonNull Role role, @NonNull String packageName,
            @NonNull UserHandle user, @NonNull Context context) {
        if (Settings.Global.getInt(context.getContentResolver(),
                Settings.Global.DEVICE_DEMO_MODE, 0) == 0) {
            return false;
        }
        Context userContext = UserUtils.getUserContext(context, user);
        DevicePolicyManager userDevicePolicyManager =
                userContext.getSystemService(DevicePolicyManager.class);
        if (!(userDevicePolicyManager.isDeviceOwnerApp(packageName)
                || userDevicePolicyManager.isProfileOwnerApp(packageName))) {
            return false;
        }
        // Fallback to do additional default check.
        return null;
    }

    @Override
    public boolean isAvailableAsUser(@NonNull Role role, @NonNull UserHandle user,
            @NonNull Context context) {
        Context userContext = UserUtils.getUserContext(context, user);
        UserManager userUserManager = userContext.getSystemService(UserManager.class);
        return userUserManager.isSystemUser() || userUserManager.isMainUser()
                || userUserManager.isDemoUser();
    }
}
