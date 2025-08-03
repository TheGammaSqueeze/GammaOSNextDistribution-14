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

package com.android.role.controller.util;

import android.app.role.RoleManager;
import android.content.Context;
import android.os.UserHandle;
import android.permission.flags.Flags;

import androidx.annotation.NonNull;

import com.android.modules.utils.build.SdkLevel;
import com.android.role.controller.model.Role;

/**
 * Helper for accessing features in {@link RoleManager}.
 */
public class RoleManagerCompat {



    private RoleManagerCompat() {}

    /**
     * @see RoleManager#isBypassingRoleQualification()
     */
    public static boolean isBypassingRoleQualification(@NonNull RoleManager roleManager) {
        if (SdkLevel.isAtLeastS()) {
            return roleManager.isBypassingRoleQualification();
        } else {
            return false;
        }
    }

    /**
     * Check whether the role has the fallback holder enabled.
     *
     * @return whether the "none" role holder is not selected
     */
    public static boolean isRoleFallbackEnabledAsUser(@NonNull Role role, @NonNull UserHandle user,
            @NonNull Context context) {
        if (SdkLevel.isAtLeastV() && Flags.systemServerRoleControllerEnabled()) {
            Context userContext = UserUtils.getUserContext(context, user);
            RoleManager userRoleManager = userContext.getSystemService(RoleManager.class);
            return userRoleManager.isRoleFallbackEnabled(role.getName());
        } else {
            return LegacyRoleFallbackEnabledUtils.isRoleFallbackEnabledAsUser(role.getName(), user,
                    context);
        }
    }

    /**
     * Set whether the role has fallback holder enabled.
     */
    public static void setRoleFallbackEnabledAsUser(@NonNull Role role,
            boolean fallbackEnabled, @NonNull UserHandle user, @NonNull Context context) {
        if (SdkLevel.isAtLeastV() && Flags.systemServerRoleControllerEnabled()) {
            Context userContext = UserUtils.getUserContext(context, user);
            RoleManager userRoleManager = userContext.getSystemService(RoleManager.class);
            userRoleManager.setRoleFallbackEnabled(role.getName(), fallbackEnabled);
        } else {
            LegacyRoleFallbackEnabledUtils.setRoleFallbackEnabledAsUser(role.getName(),
                    fallbackEnabled, user, context);
        }
    }
}
