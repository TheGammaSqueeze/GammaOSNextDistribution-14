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

package com.android.safetycenter;

import static java.util.Objects.requireNonNull;

import android.annotation.IntDef;
import android.annotation.UserIdInt;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.permission.util.UserUtils;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * A class that represent all the enabled profiles (profile parent and managed profile(s))
 * associated with a user id.
 *
 * @hide
 */
//TODO(b/286539356) Do not expose the private profile when it's not running.
public final class UserProfileGroup {

    private static final String TAG = "UserProfileGroup";
    // UserHandle#USER_NULL is a @TestApi so it cannot be accessed from the mainline module.
    public static final @UserIdInt int USER_NULL = -10000;

    @UserIdInt private final int mProfileParentUserId;
    private final int[] mManagedProfilesUserIds;
    private final int[] mManagedRunningProfilesUserIds;

    @UserIdInt private final int mPrivateProfileUserId;
    private final boolean mPrivateProfileRunning;

    /** Respresents the profile type of the primary user. */
    public static final int PROFILE_TYPE_PRIMARY = 0;
    /** Respresents the profile type of the managed profile. */
    public static final int PROFILE_TYPE_MANAGED = 1;
    /** Respresents the profile type of the private profile. */
    public static final int PROFILE_TYPE_PRIVATE = 2;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = {PROFILE_TYPE_PRIMARY, PROFILE_TYPE_MANAGED, PROFILE_TYPE_PRIVATE})
    public @interface ProfileType {
        // This array needs to cover all profile types. So whenever a new entry is added above then
        // please remember to include it in this array as well.
        int[] ALL_PROFILE_TYPES =
                {PROFILE_TYPE_PRIMARY, PROFILE_TYPE_MANAGED, PROFILE_TYPE_PRIVATE};
    }

    private UserProfileGroup(
            @UserIdInt int profileParentUserId,
            int[] managedProfilesUserIds,
            int[] managedRunningProfilesUserIds,
            @UserIdInt int privateProfileUserId,
            boolean privateProfileRunning) {
        mProfileParentUserId = profileParentUserId;
        mManagedProfilesUserIds = managedProfilesUserIds;
        mManagedRunningProfilesUserIds = managedRunningProfilesUserIds;
        mPrivateProfileUserId = privateProfileUserId;
        mPrivateProfileRunning = privateProfileRunning;
    }

    /** Returns all the alive {@link UserProfileGroup}s. */
    public static List<UserProfileGroup> getAllUserProfileGroups(Context context) {
        List<UserProfileGroup> userProfileGroups = new ArrayList<>();
        List<UserHandle> userHandles = UserUtils.getUserHandles(context);
        for (int i = 0; i < userHandles.size(); i++) {
            UserHandle userHandle = userHandles.get(i);
            int userId = userHandle.getIdentifier();

            if (userProfileGroupsContain(userProfileGroups, userId)) {
                continue;
            }

            UserProfileGroup userProfileGroup = UserProfileGroup.fromUser(context, userId);
            if (!userProfileGroup.contains(userId)) {
                continue;
            }

            userProfileGroups.add(userProfileGroup);
        }
        return userProfileGroups;
    }

    private static boolean userProfileGroupsContain(
            List<UserProfileGroup> userProfileGroups, @UserIdInt int userId) {
        for (int i = 0; i < userProfileGroups.size(); i++) {
            UserProfileGroup userProfileGroup = userProfileGroups.get(i);

            if (userProfileGroup.contains(userId)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Returns the {@link UserProfileGroup} associated with the given {@code userId}.
     *
     * <p>The given {@code userId} could be related to the profile parent or any of its associated
     * profile(s).
     *
     * <p>It is possible for the {@code userId} to not be contained within the returned {@link
     * UserProfileGroup}. This can happen if the {@code userId} is a profile that is not managed or
     * is disabled.
     */
    public static UserProfileGroup fromUser(Context context, @UserIdInt int userId) {
        UserManager userManager = getUserManagerForUser(userId, context);
        List<UserHandle> userProfiles = getEnabledUserProfiles(userManager);
        UserHandle profileParent = getProfileParent(userManager, userId);
        int profileParentUserId = userId;
        if (profileParent != null) {
            profileParentUserId = profileParent.getIdentifier();
        }

        int[] managedProfilesUserIds = new int[userProfiles.size()];
        int[] managedRunningProfilesUserIds = new int[userProfiles.size()];
        int managedProfilesUserIdsLen = 0;
        int managedRunningProfilesUserIdsLen = 0;

        int privateProfileUserId = USER_NULL;
        boolean privateProfileRunning = false;

        for (int i = 0; i < userProfiles.size(); i++) {
            UserHandle userProfileHandle = userProfiles.get(i);
            int userProfileId = userProfileHandle.getIdentifier();

            if (UserUtils.isManagedProfile(userProfileId, context)) {
                managedProfilesUserIds[managedProfilesUserIdsLen++] = userProfileId;
                if (UserUtils.isProfileRunning(userProfileId, context)) {
                    managedRunningProfilesUserIds[managedRunningProfilesUserIdsLen++] =
                            userProfileId;
                }
            } else if (UserUtils.isPrivateProfile(userProfileId, context)) {
                privateProfileUserId = userProfileId;
                privateProfileRunning = UserUtils.isProfileRunning(userProfileId, context);
            }
        }

        UserProfileGroup userProfileGroup = new UserProfileGroup(
                profileParentUserId,
                Arrays.copyOf(managedProfilesUserIds, managedProfilesUserIdsLen),
                Arrays.copyOf(managedRunningProfilesUserIds, managedRunningProfilesUserIdsLen),
                privateProfileUserId,
                privateProfileRunning
        );
        if (!userProfileGroup.contains(userId)) {
            Log.i(
                    TAG,
                    "User id: " + userId + " does not belong to: " + userProfileGroup,
                    new Exception());
        }
        return userProfileGroup;
    }

    /** Returns whether the given {@code userId} is supported by {@link UserProfileGroup}. */
    public static boolean isSupported(@UserIdInt int userId, Context context) {
        if (!isProfile(userId, context)) {
            return true;
        }
        return UserUtils.isManagedProfile(userId, context)
                || UserUtils.isPrivateProfile(userId, context);
    }

    private static UserManager getUserManagerForUser(@UserIdInt int userId, Context context) {
        Context userContext = getUserContext(context, UserHandle.of(userId));
        return requireNonNull(userContext.getSystemService(UserManager.class));
    }

    private static Context getUserContext(Context context, UserHandle userHandle) {
        if (Process.myUserHandle().equals(userHandle)) {
            return context;
        } else {
            try {
                return context.createPackageContextAsUser(
                        context.getPackageName(), /* flags= */ 0, userHandle);
            } catch (PackageManager.NameNotFoundException doesNotHappen) {
                throw new IllegalStateException(doesNotHappen);
            }
        }
    }

    private static boolean isProfile(@UserIdInt int userId, Context context) {
        // This call requires the INTERACT_ACROSS_USERS permission.
        final long callingId = Binder.clearCallingIdentity();
        try {
            UserManager userManager = getUserManagerForUser(userId, context);
            return userManager.isProfile();
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
    }

    private static List<UserHandle> getEnabledUserProfiles(UserManager userManager) {
        // This call requires the QUERY_USERS permission.
        final long callingId = Binder.clearCallingIdentity();
        try {
            return userManager.getUserProfiles();
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
    }

    @Nullable
    private static UserHandle getProfileParent(UserManager userManager, @UserIdInt int userId) {
        // This call requires the INTERACT_ACROSS_USERS permission.
        final long callingId = Binder.clearCallingIdentity();
        try {
            return userManager.getProfileParent(UserHandle.of(userId));
        } finally {
            Binder.restoreCallingIdentity(callingId);
        }
    }

    /** Returns the profile parent user id of the {@link UserProfileGroup}. */
    public int getProfileParentUserId() {
        return mProfileParentUserId;
    }

    /**
     * A convenience method to get all the profile ids of all the users of all profile types. So, in
     * essence, this is equivalent to iterating through all the profile types using
     * {@link ProfileType#ALL_PROFILE_TYPES} and getting all the users for each of the profile type
     * using {@link #getProfilesOfType(int profileType)}
     */
    public int[] getAllProfilesUserIds() {
        int[] allProfileIds = new int[getNumProfiles()];
        allProfileIds[0] = mProfileParentUserId;
        System.arraycopy(
                mManagedProfilesUserIds,
                /* srcPos= */ 0,
                allProfileIds,
                /* destPos= */ 1,
                mManagedProfilesUserIds.length);

        if (mPrivateProfileUserId != USER_NULL) {
            allProfileIds[allProfileIds.length - 1] = mPrivateProfileUserId;
        }

        return allProfileIds;
    }

    /**
     * A convenience method to get all the profile ids of all the users (that are currently running)
     * of all profile types. So, in essence, this is equivalent to iterating through all the profile
     * {types using {@link ProfileType#ALL_PROFILE_TYPES} and getting all the users for each of the
     * profile type using {@link #getProfilesOfType(int profileType)} only if they are running.
     */
    public int[] getAllRunningProfilesUserIds() {
        int[] allRunningProfileIds = new int[getNumRunningProfiles()];
        allRunningProfileIds[0] = mProfileParentUserId;
        System.arraycopy(
                mManagedRunningProfilesUserIds,
                /* srcPos= */ 0,
                allRunningProfileIds,
                /* destPos= */ 1,
                mManagedRunningProfilesUserIds.length);

        if (mPrivateProfileRunning) {
            allRunningProfileIds[allRunningProfileIds.length - 1] = mPrivateProfileUserId;
        }

        return allRunningProfileIds;
    }

    /**
     * Returns the profiles of the specified type. Returns an empty array if no profile of the
     * specified type exists.
     */
    public int[] getProfilesOfType(@ProfileType int profileType) {
        switch (profileType) {
            case PROFILE_TYPE_PRIMARY:
                return new int[] {mProfileParentUserId};
            case PROFILE_TYPE_MANAGED:
                return mManagedProfilesUserIds;
            case PROFILE_TYPE_PRIVATE:
                return mPrivateProfileUserId != USER_NULL
                        ? new int[]{mPrivateProfileUserId} : new int[]{};
            default:
                Log.w(TAG, "profiles requested for unexpected profile type " + profileType);
                return new int[] {};
        }
    }

    /**
     * Returns the running profiles of the specified type. Returns an empty array if no profile of
     * the specified type exists.
     */
    public int[] getRunningProfilesOfType(@ProfileType int profileType) {
        switch (profileType) {
            case PROFILE_TYPE_PRIMARY:
                return new int[] {mProfileParentUserId};
            case PROFILE_TYPE_MANAGED:
                return mManagedRunningProfilesUserIds;
            case PROFILE_TYPE_PRIVATE:
                //TODO(b/286539356) add the new feature flag protection when available.
                return mPrivateProfileRunning
                    ? new int[] {mPrivateProfileUserId} : new int[] {};
            default:
                Log.w(TAG, "Unexpected profile type " + profileType);
                return new int[] {};
        }
    }

    /** Returns the total number of running profiles in this user profile group */
    public int getNumRunningProfiles() {
        return 1
                + mManagedRunningProfilesUserIds.length
                + (mPrivateProfileRunning ? 1 : 0);
    }

    /** Returns the total number of profiles in this user profile group */
    private int getNumProfiles() {
        return 1
                + mManagedProfilesUserIds.length
                + (mPrivateProfileUserId == USER_NULL ? 0 : 1);
    }

    /**
     * Returns the {@link ProfileType} for the provided {@code userId}. Note that the provided
     * {@code userId} must be supported by the {@link UserProfileGroup} i.e.
     * {@link #isSupported(int, Context)} should return true for {@code userId}.
     */
    public static @ProfileType int getProfileTypeOfUser(@UserIdInt int userId, Context context) {
        if (UserUtils.isManagedProfile(userId, context)) {
            return PROFILE_TYPE_MANAGED;
        }
        if (UserUtils.isPrivateProfile(userId, context)) {
            return PROFILE_TYPE_PRIVATE;
        }
        return PROFILE_TYPE_PRIMARY;
    }

    /**
     * Returns true iff the given userId is contained in this {@link UserProfileGroup} and it's
     * running.
     */
    boolean containsRunningUserId(@UserIdInt int userId, @ProfileType int profileType) {
        switch (profileType) {
            case PROFILE_TYPE_PRIMARY:
                return true;
            case PROFILE_TYPE_MANAGED:
                for (int i = 0; i < mManagedRunningProfilesUserIds.length; i++) {
                    if (mManagedRunningProfilesUserIds[i] == userId) {
                        return true;
                    }
                }
                return false;
            case PROFILE_TYPE_PRIVATE:
                return mPrivateProfileRunning;
            default:
                Log.w(TAG, "Unexpected profile type " + profileType);
                return false;
        }
    }

    /** Returns whether the {@link UserProfileGroup} contains the given {@code userId}. */
    public boolean contains(@UserIdInt int userId) {
        if (userId == mProfileParentUserId) {
            return true;
        }

        for (int i = 0; i < mManagedProfilesUserIds.length; i++) {
            if (userId == mManagedProfilesUserIds[i]) {
                return true;
            }
        }

        return USER_NULL != mPrivateProfileUserId && userId == mPrivateProfileUserId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserProfileGroup)) return false;
        UserProfileGroup that = (UserProfileGroup) o;
        return mProfileParentUserId == that.mProfileParentUserId
                && Arrays.equals(mManagedProfilesUserIds, that.mManagedProfilesUserIds)
                && Arrays.equals(
                        mManagedRunningProfilesUserIds, that.mManagedRunningProfilesUserIds)
                && mPrivateProfileUserId == that.mPrivateProfileUserId
                && mPrivateProfileRunning == that.mPrivateProfileRunning;
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mProfileParentUserId,
                Arrays.hashCode(mManagedProfilesUserIds),
                Arrays.hashCode(mManagedRunningProfilesUserIds),
                mPrivateProfileUserId,
                mPrivateProfileRunning);
    }

    @Override
    public String toString() {
        return "UserProfileGroup{"
                + "mProfileParentUserId="
                + mProfileParentUserId
                + ", mManagedProfilesUserIds="
                + Arrays.toString(mManagedProfilesUserIds)
                + ", mManagedRunningProfilesUserIds="
                + Arrays.toString(mManagedRunningProfilesUserIds)
                + ", mPrivateProfileUserId"
                + mPrivateProfileUserId
                + ", mPrivateProfileRunning"
                + mPrivateProfileRunning
                + '}';
    }
}
