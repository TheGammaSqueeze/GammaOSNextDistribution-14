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

package com.android.intentresolver;

import android.app.Activity;
import android.app.ActivityManager;
import android.os.UserHandle;
import android.os.UserManager;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

/**
 * Helper class to precompute the (immutable) designations of various user handles in the system
 * that may contribute to the current Sharesheet session.
 */
public final class AnnotatedUserHandles {
    /** The user id of the app that started the share activity. */
    public final int userIdOfCallingApp;

    /**
     * The {@link UserHandle} that launched Sharesheet.
     * TODO: I believe this would always be the handle corresponding to {@code userIdOfCallingApp}
     * except possibly if the caller used {@link Activity#startActivityAsUser} to launch
     * Sharesheet as a different user than they themselves were running as. Verify and document.
     */
    public final UserHandle userHandleSharesheetLaunchedAs;

    /**
     * The {@link UserHandle} that owns the "personal tab" in a tabbed share UI (or the *only* 'tab'
     * in a non-tabbed UI).
     *
     * This is never a work or clone user, but may either be the root user (0) or a "secondary"
     * multi-user profile (i.e., one that's not root, work, nor clone). This is a "secondary"
     * profile only when that user is the active "foreground" user.
     *
     * In the current implementation, we can assert that this is the root user (0) any time we
     * display a tabbed UI (i.e., any time `workProfileUserHandle` is non-null), or any time that we
     * have a clone profile. This note is only provided for informational purposes; clients should
     * avoid making any reliances on that assumption.
     */
    public final UserHandle personalProfileUserHandle;

    /**
     * The {@link UserHandle} that owns the "work tab" in a tabbed share UI. This is (an arbitrary)
     * one of the "managed" profiles associated with {@link #personalProfileUserHandle}.
     */
    @Nullable
    public final UserHandle workProfileUserHandle;

    /**
     * The {@link UserHandle} of the clone profile belonging to {@link #personalProfileUserHandle}.
     */
    @Nullable
    public final UserHandle cloneProfileUserHandle;

    /**
     * The "tab owner" user handle (i.e., either {@link #personalProfileUserHandle} or
     * {@link #workProfileUserHandle}) that either matches or owns the profile of the
     * {@link #userHandleSharesheetLaunchedAs}.
     *
     * In the current implementation, we can assert that this is the same as
     * `userHandleSharesheetLaunchedAs` except when the latter is the clone profile; then this is
     * the "personal" profile owning that clone profile (which we currently know must belong to
     * user 0, but clients should avoid making any reliances on that assumption).
     */
    public final UserHandle tabOwnerUserHandleForLaunch;

    /** Compute all handle designations for a new Sharesheet session in the specified activity. */
    public static AnnotatedUserHandles forShareActivity(Activity shareActivity) {
        // TODO: consider integrating logic for `ResolverActivity.EXTRA_CALLING_USER`?
        UserHandle userHandleSharesheetLaunchedAs = UserHandle.of(UserHandle.myUserId());

        // ActivityManager.getCurrentUser() refers to the current Foreground user. When clone/work
        // profile is active, we always make the personal tab from the foreground user.
        // Outside profiles, current foreground user is potentially the same as the sharesheet
        // process's user (UserHandle.myUserId()), so we continue to create personal tab with the
        // current foreground user.
        UserHandle personalProfileUserHandle = UserHandle.of(ActivityManager.getCurrentUser());

        UserManager userManager = shareActivity.getSystemService(UserManager.class);

        return newBuilder()
                .setUserIdOfCallingApp(shareActivity.getLaunchedFromUid())
                .setUserHandleSharesheetLaunchedAs(userHandleSharesheetLaunchedAs)
                .setPersonalProfileUserHandle(personalProfileUserHandle)
                .setWorkProfileUserHandle(
                        getWorkProfileForUser(userManager, personalProfileUserHandle))
                .setCloneProfileUserHandle(
                        getCloneProfileForUser(userManager, personalProfileUserHandle))
                .build();
    }

    @VisibleForTesting public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Returns the {@link UserHandle} to use when querying resolutions for intents in a
     * {@link ResolverListController} configured for the provided {@code userHandle}.
     */
    public UserHandle getQueryIntentsUser(UserHandle userHandle) {
        // In case launching app is in clonedProfile, and we are building the personal tab, intent
        // resolution will be attempted as clonedUser instead of user 0. This is because intent
        // resolution from user 0 and clonedUser is not guaranteed to return same results.
        // We do not care about the case when personal adapter is started with non-root user
        // (secondary user case), as clone profile is guaranteed to be non-active in that case.
        UserHandle queryIntentsUser = userHandle;
        if (isLaunchedAsCloneProfile() && userHandle.equals(personalProfileUserHandle)) {
            queryIntentsUser = cloneProfileUserHandle;
        }
        return queryIntentsUser;
    }

    private Boolean isLaunchedAsCloneProfile() {
        return userHandleSharesheetLaunchedAs.equals(cloneProfileUserHandle);
    }

    private AnnotatedUserHandles(
            int userIdOfCallingApp,
            UserHandle userHandleSharesheetLaunchedAs,
            UserHandle personalProfileUserHandle,
            @Nullable UserHandle workProfileUserHandle,
            @Nullable UserHandle cloneProfileUserHandle) {
        if ((userIdOfCallingApp < 0) || UserHandle.isIsolated(userIdOfCallingApp)) {
            throw new SecurityException("Can't start a resolver from uid " + userIdOfCallingApp);
        }

        this.userIdOfCallingApp = userIdOfCallingApp;
        this.userHandleSharesheetLaunchedAs = userHandleSharesheetLaunchedAs;
        this.personalProfileUserHandle = personalProfileUserHandle;
        this.workProfileUserHandle = workProfileUserHandle;
        this.cloneProfileUserHandle = cloneProfileUserHandle;
        this.tabOwnerUserHandleForLaunch =
                (userHandleSharesheetLaunchedAs == workProfileUserHandle)
                    ? workProfileUserHandle : personalProfileUserHandle;
    }

    @Nullable
    private static UserHandle getWorkProfileForUser(
            UserManager userManager, UserHandle profileOwnerUserHandle) {
        return userManager.getProfiles(profileOwnerUserHandle.getIdentifier())
                .stream()
                .filter(info -> info.isManagedProfile())
                .findFirst()
                .map(info -> info.getUserHandle())
                .orElse(null);
    }

    @Nullable
    private static UserHandle getCloneProfileForUser(
            UserManager userManager, UserHandle profileOwnerUserHandle) {
        return userManager.getProfiles(profileOwnerUserHandle.getIdentifier())
                .stream()
                .filter(info -> info.isCloneProfile())
                .findFirst()
                .map(info -> info.getUserHandle())
                .orElse(null);
    }

    @VisibleForTesting
    public static class Builder {
        private int mUserIdOfCallingApp;
        private UserHandle mUserHandleSharesheetLaunchedAs;
        private UserHandle mPersonalProfileUserHandle;
        private UserHandle mWorkProfileUserHandle;
        private UserHandle mCloneProfileUserHandle;

        public Builder setUserIdOfCallingApp(int id) {
            mUserIdOfCallingApp = id;
            return this;
        }

        public Builder setUserHandleSharesheetLaunchedAs(UserHandle user) {
            mUserHandleSharesheetLaunchedAs = user;
            return this;
        }

        public Builder setPersonalProfileUserHandle(UserHandle user) {
            mPersonalProfileUserHandle = user;
            return this;
        }

        public Builder setWorkProfileUserHandle(UserHandle user) {
            mWorkProfileUserHandle = user;
            return this;
        }

        public Builder setCloneProfileUserHandle(UserHandle user) {
            mCloneProfileUserHandle = user;
            return this;
        }

        public AnnotatedUserHandles build() {
            return new AnnotatedUserHandles(
                    mUserIdOfCallingApp,
                    mUserHandleSharesheetLaunchedAs,
                    mPersonalProfileUserHandle,
                    mWorkProfileUserHandle,
                    mCloneProfileUserHandle);
        }
    }
}
