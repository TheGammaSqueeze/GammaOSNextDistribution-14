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

package com.android.bedstead.nene.devicepolicy;

import static com.android.bedstead.nene.permissions.CommonPermissions.INTERACT_ACROSS_USERS;

import android.os.UserManager;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.exceptions.AdbException;
import com.android.bedstead.nene.permissions.PermissionContext;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.nene.utils.ShellCommand;

/**
 * Test APIs related to interacting with user restrictions.
 */
public final class UserRestrictions {

    private final UserReference mUser;
    private static final UserManager sUserManager =
            TestApis.context().instrumentedContext().getSystemService(UserManager.class);

    UserRestrictions(UserReference user) {
        mUser = user;
    }

    // These are disabled due to b/264642433 - we should restore them and add tests when we have
    // capabilities to manage user restrictions directly
//    public UserRestrictionsContext set(String restriction) {
//        return new UserRestrictionsContext(mUser).set(restriction);
//    }
//
//    public UserRestrictionsContext unset(String restriction) {
//        return new UserRestrictionsContext(mUser).unset(restriction);
//    }

    /**
     * {@code true} if the restriction is set on the given user.
     */
    public boolean isSet(String restriction) {
        try (PermissionContext p = TestApis.permissions().withPermission(INTERACT_ACROSS_USERS)) {
            return sUserManager.hasUserRestrictionForUser(restriction, mUser.userHandle());
        }
    }

    /**
     * Set {@code restriction}.
     * <p>This requires {@code @RequireAdbRoot} to be set for the executing test.
     */
    public void set(String restriction, boolean set) throws AdbException {
        ShellCommand.builderForUser(mUser, "pm set-user-restriction")
                .asRoot(true)
                .addOperand(restriction)
                .addOperand(set ? 1 : 0)
                .execute();
    }

}
