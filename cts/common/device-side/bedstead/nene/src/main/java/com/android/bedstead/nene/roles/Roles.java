/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.bedstead.nene.roles;

import static com.android.bedstead.nene.permissions.CommonPermissions.BYPASS_ROLE_QUALIFICATION;
import static com.android.bedstead.nene.permissions.CommonPermissions.INTERACT_ACROSS_USERS_FULL;
import static com.android.bedstead.nene.permissions.CommonPermissions.MANAGE_ROLE_HOLDERS;
import static com.android.bedstead.nene.utils.Versions.T;

import android.annotation.TargetApi;
import android.app.role.RoleManager;
import android.content.Context;
import android.os.Build;
import android.os.UserHandle;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.annotations.Experimental;
import com.android.bedstead.nene.packages.Package;
import com.android.bedstead.nene.permissions.PermissionContext;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.nene.utils.Versions;

import java.util.HashSet;
import java.util.Set;

/**
 * Test APIs related to roles.
 *
 * <p>To add or remove a role to or from a specific package, see
 * {@link Package#setAsRoleHolder(String)} and {@link Package#removeAsRoleHolder(String)}.
 */
@TargetApi(Build.VERSION_CODES.TIRAMISU)
public class Roles {
    public static final Roles sInstance = new Roles();

    private static final Context sContext = TestApis.context().instrumentedContext();

    private Roles() {}

    /**
     * @see RoleManager#setBypassingRoleQualification(boolean)
     */
    @Experimental
    public void setBypassingRoleQualification(boolean bypassingRoleQualification) {
        if (!Versions.meetsMinimumSdkVersionRequirement(T)) {
            return;
        }
        try (PermissionContext p = TestApis.permissions().withPermission(
                BYPASS_ROLE_QUALIFICATION)) {
            sContext.getSystemService(RoleManager.class)
                    .setBypassingRoleQualification(bypassingRoleQualification);
        }
    }

    /**
     * @see RoleManager#getRoleHolders(String)
     */
    @Experimental
    public Set<String> getRoleHolders(String role) {
        try (PermissionContext p = TestApis.permissions().withPermission(
                MANAGE_ROLE_HOLDERS)) {
            return new HashSet<>(sContext.getSystemService(RoleManager.class).getRoleHolders(role));
        }
    }

    /**
     * @see RoleManager#getRoleHoldersAsUser(String, UserHandle)
     */
    @Experimental
    public Set<String> getRoleHoldersAsUser(String role, UserReference user) {
        try (PermissionContext p = TestApis.permissions().withPermission(
                MANAGE_ROLE_HOLDERS).withPermission(INTERACT_ACROSS_USERS_FULL)) {
            return new HashSet<>(
                    sContext.getSystemService(RoleManager.class).getRoleHoldersAsUser(role,
                            user.userHandle()));
        }
    }
}
