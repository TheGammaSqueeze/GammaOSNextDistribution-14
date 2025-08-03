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
package com.android.car.user;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.UserIdInt;
import android.car.builtin.os.UserManagerHelper;
import android.content.Context;
import android.os.UserHandle;
import android.os.UserManager;

import java.util.List;

/**
 * Helper class to get User properties using UserHandle
 */
//TODO(b/214340360) : Add unit test
public final class UserHandleHelper {

    private final Context mContext;
    private final UserManager mUserManager;

    public UserHandleHelper(@NonNull Context context, @NonNull UserManager userManager) {
        mContext = context;
        mUserManager = userManager;
    }

    /**
     * Gets user handle if user exists
     */
    @Nullable
    public UserHandle getExistingUserHandle(@UserIdInt int userId) {
        List<UserHandle> users = UserManagerHelper.getUserHandles(mUserManager,
                /* excludeDying= */ false);

        for (UserHandle user : users) {
            if (user.getIdentifier() == userId) return user;
        }
        return null;
    }

    /**
     * Get enabled profiles
     */
    @NonNull
    public List<UserHandle> getEnabledProfiles(@UserIdInt int userId) {
        return getUserContextAwareUserManager(userId).getEnabledProfiles();
    }

    /**
     * Is User a guest user?
     */
    public boolean isGuestUser(UserHandle user) {
        return getUserContextAwareUserManager(user.getIdentifier()).isGuestUser();
    }

    /**
     * Is User an admin user?
     */
    public boolean isAdminUser(UserHandle user) {
        return getUserContextAwareUserManager(user.getIdentifier()).isAdminUser();
    }

    /**
     * Is User an ephemeral user?
     */
    public boolean isEphemeralUser(UserHandle user) {
        return UserManagerHelper.isEphemeralUser(mUserManager, user);
    }

    /**
     * Is User enabled?
     */
    public boolean isEnabledUser(UserHandle user) {
        return UserManagerHelper.isEnabledUser(mUserManager, user);
    }

    /**
     * Is User a managed profile?
     */
    public boolean isManagedProfile(UserHandle user) {
        return mUserManager.isManagedProfile(user.getIdentifier());
    }

    /**
     * Is user for a profile?
     */
    public boolean isProfileUser(UserHandle user) {
        return getUserContextAwareUserManager(user.getIdentifier()).isProfile();
    }

    /**
     * Is User Initialized?
     */
    public boolean isInitializedUser(UserHandle user) {
        return UserManagerHelper.isInitializedUser(mUserManager, user);
    }

    private UserManager getUserContextAwareUserManager(@UserIdInt int userId) {
        Context userContext = mContext.createContextAsUser(UserHandle.of(userId), /* flags= */ 0);
        return userContext.getSystemService(UserManager.class);
    }
}
