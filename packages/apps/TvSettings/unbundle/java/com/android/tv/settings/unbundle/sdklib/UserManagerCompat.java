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

import android.annotation.NonNull;
import android.annotation.UserIdInt;
import android.content.Context;
import android.content.pm.UserInfo;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;

import java.util.List;

public class UserManagerCompat {
    private final UserManager mUserManager;

    public UserManagerCompat(UserManager userManager) {
        mUserManager = userManager;
    }

    public UserManagerCompat(Context context) {
        mUserManager = (UserManager) context.getSystemService(Context.USER_SERVICE);
    }

    public int[] getProfileIdsWithDisabled(@UserIdInt int userId) {
        return mUserManager.getProfileIdsWithDisabled(userId);
    }

    public UserInfoCompat getUserInfo(@UserIdInt int userId) {
        return new UserInfoCompat(mUserManager.getUserInfo(userId));
    }

    public boolean removeUser(@UserIdInt int userId) {
        return mUserManager.removeUser(userId);
    }

    public List<UserInfoCompat> getUsers() {
        return UserInfoCompat.convert(mUserManager.getUsers());
    }

    public UserInfoCompat createProfileForUser(String name, @NonNull String userType,
            @UserInfo.UserInfoFlag int flags, @UserIdInt int userId) {
        return new UserInfoCompat(mUserManager.createProfileForUser(name, userType, flags, userId));
    }

    public static UserManagerCompat getCompat(Context context) {
        return new UserManagerCompat(context);
    }

    public static UserManager get(Context context) {
        return UserManager.get(context);
    }

    public boolean isSameProfileGroup(@UserIdInt int userId, int otherUserId) {
        return mUserManager.isSameProfileGroup(userId, otherUserId);
    }

    public @NonNull
    List<UserInfoCompat> getAliveUsers() {
        return UserInfoCompat.convert(mUserManager.getAliveUsers());
    }

    public List<UserInfoCompat> getProfiles(@UserIdInt int userId) {
        return UserInfoCompat.convert(mUserManager.getProfiles(userId));
    }

    public UserInfoCompat getProfileParent(@UserIdInt int userId) {
        return new UserInfoCompat(mUserManager.getProfileParent(userId));
    }

    public boolean isUserAdmin(@UserIdInt int userId) {
        return mUserManager.isUserAdmin(userId);
    }

    public boolean isAdminUser() {
        return mUserManager.isAdminUser();
    }

    public void setUserRestriction(String key, boolean value, UserHandle userHandle) {
        mUserManager.setUserRestriction(key, value, userHandle);
    }

    public boolean hasBaseUserRestriction(
            @UserManager.UserRestrictionKey @NonNull String restrictionKey,
            @NonNull UserHandle userHandle) {
        return mUserManager.hasBaseUserRestriction(restrictionKey, userHandle);
    }

    public void setApplicationRestrictions(
            String packageName, Bundle restrictions, UserHandle user) {
        mUserManager.setApplicationRestrictions(packageName, restrictions, user);
    }

    public Bundle getApplicationRestrictions(String packageName, UserHandle user) {
        return mUserManager.getApplicationRestrictions(packageName, user);
    }

    public void setUserIcon(@UserIdInt int userId, @NonNull Bitmap icon) {
        mUserManager.setUserIcon(userId, icon);
    }
}
