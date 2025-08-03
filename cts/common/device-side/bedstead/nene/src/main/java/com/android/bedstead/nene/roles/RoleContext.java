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

import static com.android.bedstead.nene.permissions.CommonPermissions.INTERACT_ACROSS_USERS_FULL;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.packages.Package;
import com.android.bedstead.nene.permissions.PermissionContext;
import com.android.bedstead.nene.users.UserReference;

import java.util.Set;

/**
 * A context that, when closed, will remove the package from the role.
 */
public final class RoleContext implements AutoCloseable {

    private final String mRole;
    private final Package mPackage;
    private final UserReference mUser;
    private final Set<String> mPreviousRoleHolders;

    public RoleContext(String role, Package pkg, UserReference user) {
        mRole = role;
        mPackage = pkg;
        mUser = user;
        mPreviousRoleHolders = TestApis.roles().getRoleHoldersAsUser(role, user);
    }

    @Override
    public void close() {
        try (PermissionContext p = TestApis.permissions().withPermission(
                INTERACT_ACROSS_USERS_FULL)) {
            mPackage.removeAsRoleHolder(mRole, mUser);

            Set<String> currentRoleHolders = TestApis.roles().getRoleHoldersAsUser(mRole, mUser);
            // Re-adding previous role holder just for exclusive role as we would have overridden
            // the previous role holder in this case, for non exclusive roles it's not a problem
            // as we just add as one of the role holder so just removing them is fine.
            if (currentRoleHolders.isEmpty() && mPreviousRoleHolders.size() == 1) {
                Package roleHolderPackage = Package.of(
                        mPreviousRoleHolders.stream().toList().get(0));
                roleHolderPackage.setAsRoleHolder(mRole, mUser);
            }
        }
    }
}
