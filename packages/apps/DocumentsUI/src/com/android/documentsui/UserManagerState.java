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

package com.android.documentsui;

import static androidx.core.util.Preconditions.checkNotNull;

import static com.android.documentsui.DevicePolicyResources.Drawables.Style.SOLID_COLORED;
import static com.android.documentsui.DevicePolicyResources.Drawables.WORK_PROFILE_ICON;
import static com.android.documentsui.DevicePolicyResources.Strings.PERSONAL_TAB;
import static com.android.documentsui.DevicePolicyResources.Strings.WORK_TAB;
import static com.android.documentsui.base.SharedMinimal.DEBUG;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.UserProperties;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;

import androidx.annotation.GuardedBy;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;

import com.android.documentsui.base.Features;
import com.android.documentsui.base.UserId;
import com.android.documentsui.util.CrossProfileUtils;
import com.android.documentsui.util.VersionUtils;
import com.android.modules.utils.build.SdkLevel;

import com.google.common.base.Objects;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface UserManagerState {

    /**
     * Returns the {@link UserId} of each profile which should be queried for documents. This will
     * always
     * include {@link UserId#CURRENT_USER}.
     */
    List<UserId> getUserIds();

    /**
     * Returns mapping between the {@link UserId} and the label for the profile
     */
    Map<UserId, String> getUserIdToLabelMap();

    /**
     * Returns mapping between the {@link UserId} and the drawable badge for the profile
     *
     * returns {@code null} for non-profile userId
     */
    Map<UserId, Drawable> getUserIdToBadgeMap();

    /**
     * Returns a map of {@link UserId} to boolean value indicating whether
     * the {@link UserId}.CURRENT_USER can forward {@link Intent} to that {@link UserId}
     */
    Map<UserId, Boolean> getCanForwardToProfileIdMap(Intent intent);

    /**
     * Updates the state of the list of userIds and all the associated maps according the intent
     * received in broadcast
     *
     * @param userId {@link UserId} for the profile for which the availability status changed
     * @param action {@link Intent}.ACTION_PROFILE_UNAVAILABLE or
     *               {@link Intent}.ACTION_PROFILE_AVAILABLE
     */
    void onProfileActionStatusChange(String action, UserId userId);

    /**
     * Sets the intent that triggered the launch of the DocsUI
     */
    void setCurrentStateIntent(Intent intent);

    /**
     * Creates an implementation of {@link UserManagerState}.
     */
    // TODO: b/314746383 Make this class a singleton
    static UserManagerState create(Context context) {
        return new RuntimeUserManagerState(context);
    }

    /**
     * Implementation of {@link UserManagerState}
     */
    final class RuntimeUserManagerState implements UserManagerState {

        private static final String TAG = "UserManagerState";
        private final Context mContext;
        private final UserId mCurrentUser;
        private final boolean mIsDeviceSupported;
        private final UserManager mUserManager;
        private final ConfigStore mConfigStore;
        /**
         * List of all the {@link UserId} that have the {@link UserProperties.ShowInSharingSurfaces}
         * set as `SHOW_IN_SHARING_SURFACES_SEPARATE` OR it is a system/personal user
         */
        @GuardedBy("mUserIds")
        private final List<UserId> mUserIds = new ArrayList<>();
        /**
         * Mapping between the {@link UserId} to the corresponding profile label
         */
        @GuardedBy("mUserIdToLabelMap")
        private final Map<UserId, String> mUserIdToLabelMap = new HashMap<>();
        /**
         * Mapping between the {@link UserId} to the corresponding profile badge
         */
        @GuardedBy("mUserIdToBadgeMap")
        private final Map<UserId, Drawable> mUserIdToBadgeMap = new HashMap<>();
        /**
         * Map containing {@link UserId}, other than that of the current user, as key and boolean
         * denoting whether it is accessible by the current user or not as value
         */
        @GuardedBy("mCanFrowardToProfileIdMap")
        private final Map<UserId, Boolean> mCanFrowardToProfileIdMap = new HashMap<>();

        private Intent mCurrentStateIntent;

        private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                synchronized (mUserIds) {
                    mUserIds.clear();
                }
                synchronized (mUserIdToLabelMap) {
                    mUserIdToLabelMap.clear();
                }
                synchronized (mUserIdToBadgeMap) {
                    mUserIdToBadgeMap.clear();
                }
                synchronized (mCanFrowardToProfileIdMap) {
                    mCanFrowardToProfileIdMap.clear();
                }
            }
        };


        private RuntimeUserManagerState(Context context) {
            this(context, UserId.CURRENT_USER,
                    Features.CROSS_PROFILE_TABS && isDeviceSupported(context),
                    DocumentsApplication.getConfigStore());
        }

        @VisibleForTesting
        RuntimeUserManagerState(Context context, UserId currentUser, boolean isDeviceSupported,
                ConfigStore configStore) {
            mContext = context.getApplicationContext();
            mCurrentUser = checkNotNull(currentUser);
            mIsDeviceSupported = isDeviceSupported;
            mUserManager = mContext.getSystemService(UserManager.class);
            mConfigStore = configStore;

            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_MANAGED_PROFILE_ADDED);
            filter.addAction(Intent.ACTION_MANAGED_PROFILE_REMOVED);
            if (SdkLevel.isAtLeastV() && mConfigStore.isPrivateSpaceInDocsUIEnabled()) {
                filter.addAction(Intent.ACTION_PROFILE_ADDED);
                filter.addAction(Intent.ACTION_PROFILE_REMOVED);
            }
            mContext.registerReceiver(mIntentReceiver, filter);
        }

        @Override
        public List<UserId> getUserIds() {
            synchronized (mUserIds) {
                if (mUserIds.isEmpty()) {
                    mUserIds.addAll(getUserIdsInternal());
                }
                return mUserIds;
            }
        }

        @Override
        public Map<UserId, String> getUserIdToLabelMap() {
            synchronized (mUserIdToLabelMap) {
                if (mUserIdToLabelMap.isEmpty()) {
                    getUserIdToLabelMapInternal();
                }
                return mUserIdToLabelMap;
            }
        }

        @Override
        public Map<UserId, Drawable> getUserIdToBadgeMap() {
            synchronized (mUserIdToBadgeMap) {
                if (mUserIdToBadgeMap.isEmpty()) {
                    getUserIdToBadgeMapInternal();
                }
                return mUserIdToBadgeMap;
            }
        }

        @Override
        public Map<UserId, Boolean> getCanForwardToProfileIdMap(Intent intent) {
            synchronized (mCanFrowardToProfileIdMap) {
                if (mCanFrowardToProfileIdMap.isEmpty()) {
                    getCanForwardToProfileIdMapInternal(intent);
                }
                return mCanFrowardToProfileIdMap;
            }
        }

        @Override
        @SuppressLint("NewApi")
        public void onProfileActionStatusChange(String action, UserId userId) {
            UserProperties userProperties = mUserManager.getUserProperties(
                    UserHandle.of(userId.getIdentifier()));
            if (userProperties.getShowInQuietMode() != UserProperties.SHOW_IN_QUIET_MODE_HIDDEN) {
                return;
            }
            if (Intent.ACTION_PROFILE_UNAVAILABLE.equals(action)) {
                synchronized (mUserIds) {
                    mUserIds.remove(userId);
                }
            } else if (Intent.ACTION_PROFILE_AVAILABLE.equals(action)) {
                synchronized (mUserIds) {
                    if (!mUserIds.contains(userId)) {
                        mUserIds.add(userId);
                    }
                }
                synchronized (mUserIdToLabelMap) {
                    if (!mUserIdToLabelMap.containsKey(userId)) {
                        mUserIdToLabelMap.put(userId, getProfileLabel(userId));
                    }
                }
                synchronized (mUserIdToBadgeMap) {
                    if (!mUserIdToBadgeMap.containsKey(userId)) {
                        mUserIdToBadgeMap.put(userId, getProfileBadge(userId));
                    }
                }
                synchronized (mCanFrowardToProfileIdMap) {
                    if (!mCanFrowardToProfileIdMap.containsKey(userId)) {
                        if (userId.getIdentifier() == ActivityManager.getCurrentUser()
                                || isCrossProfileContentSharingStrategyDelegatedFromParent(
                                UserHandle.of(userId.getIdentifier()))
                                || CrossProfileUtils.getCrossProfileResolveInfo(mCurrentUser,
                                mContext.getPackageManager(), mCurrentStateIntent, mContext,
                                mConfigStore.isPrivateSpaceInDocsUIEnabled()) != null) {
                            mCanFrowardToProfileIdMap.put(userId, true);
                        } else {
                            mCanFrowardToProfileIdMap.put(userId, false);
                        }

                    }
                }
            } else {
                Log.e(TAG, "Unexpected action received: " + action);
            }
        }

        @Override
        public void setCurrentStateIntent(Intent intent) {
            mCurrentStateIntent = intent;
        }

        private List<UserId> getUserIdsInternal() {
            final List<UserId> result = new ArrayList<>();

            if (!mIsDeviceSupported) {
                result.add(mCurrentUser);
                return result;
            }

            if (mUserManager == null) {
                Log.e(TAG, "cannot obtain user manager");
                result.add(mCurrentUser);
                return result;
            }

            final List<UserHandle> userProfiles = mUserManager.getUserProfiles();
            if (userProfiles.size() < 2) {
                result.add(mCurrentUser);
                return result;
            }

            if (SdkLevel.isAtLeastV()) {
                getUserIdsInternalPostV(userProfiles, result);
            } else {
                getUserIdsInternalPreV(userProfiles, result);
            }
            return result;
        }

        @SuppressLint("NewApi")
        private void getUserIdsInternalPostV(List<UserHandle> userProfiles, List<UserId> result) {
            for (UserHandle userHandle : userProfiles) {
                if (userHandle.getIdentifier() == ActivityManager.getCurrentUser()) {
                    result.add(UserId.of(userHandle));
                } else {
                    // Out of all the profiles returned by user manager the profiles that are
                    // returned should satisfy both the following conditions:
                    // 1. It has user property SHOW_IN_SHARING_SURFACES_SEPARATE
                    // 2. Quite mode is not enabled, if it is enabled then the profile's user
                    //    property is not SHOW_IN_QUIET_MODE_HIDDEN
                    if (isProfileAllowed(userHandle)) {
                        result.add(UserId.of(userHandle));
                    }
                }
            }
            if (result.isEmpty()) {
                result.add(mCurrentUser);
            }
        }

        @SuppressLint("NewApi")
        private boolean isProfileAllowed(UserHandle userHandle) {
            final UserProperties userProperties =
                    mUserManager.getUserProperties(userHandle);
            if (userProperties.getShowInSharingSurfaces()
                    == UserProperties.SHOW_IN_SHARING_SURFACES_SEPARATE) {
                return !UserId.of(userHandle).isQuietModeEnabled(mContext)
                        || userProperties.getShowInQuietMode()
                        != UserProperties.SHOW_IN_QUIET_MODE_HIDDEN;
            }
            return false;
        }

        private void getUserIdsInternalPreV(List<UserHandle> userProfiles, List<UserId> result) {
            result.add(mCurrentUser);
            UserId systemUser = null;
            UserId managedUser = null;
            for (UserHandle userHandle : userProfiles) {
                if (userHandle.isSystem()) {
                    systemUser = UserId.of(userHandle);
                } else if (mUserManager.isManagedProfile(userHandle.getIdentifier())) {
                    managedUser = UserId.of(userHandle);
                }
            }
            if (mCurrentUser.isSystem() && managedUser != null) {
                result.add(managedUser);
            } else if (mCurrentUser.isManagedProfile(mUserManager) && systemUser != null) {
                result.add(0, systemUser);
            } else {
                if (DEBUG) {
                    Log.w(TAG, "The current user " + UserId.CURRENT_USER
                            + " is neither system nor managed user. has system user: "
                            + (systemUser != null));
                }
            }
        }

        private void getUserIdToLabelMapInternal() {
            if (SdkLevel.isAtLeastV()) {
                getUserIdToLabelMapInternalPostV();
            } else {
                getUserIdToLabelMapInternalPreV();
            }
        }

        @SuppressLint("NewApi")
        private void getUserIdToLabelMapInternalPostV() {
            if (mUserManager == null) {
                Log.e(TAG, "cannot obtain user manager");
                return;
            }
            List<UserId> userIds = getUserIds();
            for (UserId userId : userIds) {
                synchronized (mUserIdToLabelMap) {
                    mUserIdToLabelMap.put(userId, getProfileLabel(userId));
                }
            }
        }

        private void getUserIdToLabelMapInternalPreV() {
            if (mUserManager == null) {
                Log.e(TAG, "cannot obtain user manager");
                return;
            }
            List<UserId> userIds = getUserIds();
            for (UserId userId : userIds) {
                if (mUserManager.isManagedProfile(userId.getIdentifier())) {
                    synchronized (mUserIdToLabelMap) {
                        mUserIdToLabelMap.put(userId,
                                getEnterpriseString(WORK_TAB, R.string.work_tab));
                    }
                } else {
                    synchronized (mUserIdToLabelMap) {
                        mUserIdToLabelMap.put(userId,
                                getEnterpriseString(PERSONAL_TAB, R.string.personal_tab));
                    }
                }
            }
        }

        @SuppressLint("NewApi")
        private String getProfileLabel(UserId userId) {
            if (userId.getIdentifier() == ActivityManager.getCurrentUser()) {
                return getEnterpriseString(PERSONAL_TAB, R.string.personal_tab);
            }
            try {
                Context userContext = mContext.createContextAsUser(
                        UserHandle.of(userId.getIdentifier()), 0 /* flags */);
                UserManager userManagerAsUser = userContext.getSystemService(UserManager.class);
                if (userManagerAsUser == null) {
                    Log.e(TAG, "cannot obtain user manager");
                    return null;
                }
                return userManagerAsUser.getProfileLabel();
            } catch (Exception e) {
                Log.e(TAG, "Exception occurred while trying to get profile label:\n" + e);
                return null;
            }
        }

        private String getEnterpriseString(String updatableStringId, int defaultStringId) {
            if (SdkLevel.isAtLeastT()) {
                return getUpdatableEnterpriseString(updatableStringId, defaultStringId);
            } else {
                return mContext.getString(defaultStringId);
            }
        }

        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        private String getUpdatableEnterpriseString(String updatableStringId, int defaultStringId) {
            DevicePolicyManager dpm = mContext.getSystemService(DevicePolicyManager.class);
            if (Objects.equal(dpm, null)) {
                Log.e(TAG, "can not get device policy manager");
                return mContext.getString(defaultStringId);
            }
            return dpm.getResources().getString(
                    updatableStringId,
                    () -> mContext.getString(defaultStringId));
        }

        private void getUserIdToBadgeMapInternal() {
            if (SdkLevel.isAtLeastV()) {
                getUserIdToBadgeMapInternalPostV();
            } else {
                getUserIdToBadgeMapInternalPreV();
            }
        }

        @SuppressLint("NewApi")
        private void getUserIdToBadgeMapInternalPostV() {
            if (mUserManager == null) {
                Log.e(TAG, "cannot obtain user manager");
                return;
            }
            List<UserId> userIds = getUserIds();
            for (UserId userId : userIds) {
                synchronized (mUserIdToBadgeMap) {
                    mUserIdToBadgeMap.put(userId, getProfileBadge(userId));
                }
            }
        }

        private void getUserIdToBadgeMapInternalPreV() {
            if (!SdkLevel.isAtLeastR()) return;
            if (mUserManager == null) {
                Log.e(TAG, "cannot obtain user manager");
                return;
            }
            List<UserId> userIds = getUserIds();
            for (UserId userId : userIds) {
                if (mUserManager.isManagedProfile(userId.getIdentifier())) {
                    synchronized (mUserIdToBadgeMap) {
                        mUserIdToBadgeMap.put(userId,
                                SdkLevel.isAtLeastT() ? getWorkProfileBadge()
                                        : mContext.getDrawable(R.drawable.ic_briefcase));
                    }
                }
            }
        }

        @SuppressLint("NewApi")
        private Drawable getProfileBadge(UserId userId) {
            if (userId.getIdentifier() == ActivityManager.getCurrentUser()) {
                return null;
            }
            try {
                Context userContext = mContext.createContextAsUser(
                        UserHandle.of(userId.getIdentifier()), 0 /* flags */);
                UserManager userManagerAsUser = userContext.getSystemService(UserManager.class);
                if (userManagerAsUser == null) {
                    Log.e(TAG, "cannot obtain user manager");
                    return null;
                }
                return userManagerAsUser.getUserBadge();
            } catch (Exception e) {
                Log.e(TAG, "Exception occurred while trying to get profile badge:\n" + e);
                return null;
            }
        }

        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        private Drawable getWorkProfileBadge() {
            DevicePolicyManager dpm = mContext.getSystemService(DevicePolicyManager.class);
            Drawable drawable = dpm.getResources().getDrawable(WORK_PROFILE_ICON, SOLID_COLORED,
                    () ->
                            mContext.getDrawable(R.drawable.ic_briefcase));
            return drawable;
        }

        private void getCanForwardToProfileIdMapInternal(Intent intent) {
            // Versions less than V will not have the user properties required to determine whether
            // cross profile check is delegated from parent or not
            if (!SdkLevel.isAtLeastV()) {
                getCanForwardToProfileIdMapPreV(intent);
                return;
            }
            if (mUserManager == null) {
                Log.e(TAG, "can not get user manager");
                return;
            }

            List<UserId> parentOrDelegatedFromParent = new ArrayList<>();
            List<UserId> canForwardToProfileIds = new ArrayList<>();
            List<UserId> noDelegation = new ArrayList<>();

            List<UserId> userIds = getUserIds();
            for (UserId userId : userIds) {
                final UserHandle userHandle = UserHandle.of(userId.getIdentifier());
                // Parent (personal) profile and all the child profiles that delegate cross profile
                // content sharing check to parent can share among each other
                if (userId.getIdentifier() == ActivityManager.getCurrentUser()
                        || isCrossProfileContentSharingStrategyDelegatedFromParent(userHandle)) {
                    parentOrDelegatedFromParent.add(userId);
                } else {
                    noDelegation.add(userId);
                }
            }

            if (noDelegation.size() > 1) {
                Log.e(TAG, "There cannot be more than one profile delegating cross profile "
                        + "content sharing check from self.");
            }

            /*
             * Cross profile resolve info need to be checked in the following 2 cases:
             * 1. current user is either parent or delegates check to parent and the target user
             *    does not delegate to parent
             * 2. current user does not delegate check to the parent and the target user is the
             *    parent profile
             */
            UserId needToCheck = null;
            if (parentOrDelegatedFromParent.contains(mCurrentUser)
                    && !noDelegation.isEmpty()) {
                needToCheck = noDelegation.get(0);
            } else if (mCurrentUser.getIdentifier() != ActivityManager.getCurrentUser()) {
                final UserHandle parentProfile = mUserManager.getProfileParent(
                        UserHandle.of(mCurrentUser.getIdentifier()));
                needToCheck = UserId.of(parentProfile);
            }

            if (needToCheck != null && CrossProfileUtils.getCrossProfileResolveInfo(mCurrentUser,
                    mContext.getPackageManager(), intent, mContext,
                    mConfigStore.isPrivateSpaceInDocsUIEnabled()) != null) {
                if (parentOrDelegatedFromParent.contains(needToCheck)) {
                    canForwardToProfileIds.addAll(parentOrDelegatedFromParent);
                } else {
                    canForwardToProfileIds.add(needToCheck);
                }
            }

            if (parentOrDelegatedFromParent.contains(mCurrentUser)) {
                canForwardToProfileIds.addAll(parentOrDelegatedFromParent);
            }

            for (UserId userId : userIds) {
                synchronized (mCanFrowardToProfileIdMap) {
                    if (userId.equals(mCurrentUser)) {
                        mCanFrowardToProfileIdMap.put(userId, true);
                        continue;
                    }
                    mCanFrowardToProfileIdMap.put(userId, canForwardToProfileIds.contains(userId));
                }
            }
        }

        @SuppressLint("NewApi")
        private boolean isCrossProfileContentSharingStrategyDelegatedFromParent(
                UserHandle userHandle) {
            if (mUserManager == null) {
                Log.e(TAG, "can not obtain user manager");
                return false;
            }
            UserProperties userProperties = mUserManager.getUserProperties(userHandle);
            if (java.util.Objects.equals(userProperties, null)) {
                Log.e(TAG, "can not obtain user properties");
                return false;
            }

            return userProperties.getCrossProfileContentSharingStrategy()
                    == UserProperties.CROSS_PROFILE_CONTENT_SHARING_DELEGATE_FROM_PARENT;
        }

        private void getCanForwardToProfileIdMapPreV(Intent intent) {
            // There only two profiles pre V
            List<UserId> userIds = getUserIds();
            for (UserId userId : userIds) {
                synchronized (mCanFrowardToProfileIdMap) {
                    if (mCurrentUser.equals(userId)) {
                        mCanFrowardToProfileIdMap.put(userId, true);
                    } else {
                        mCanFrowardToProfileIdMap.put(userId,
                                CrossProfileUtils.getCrossProfileResolveInfo(
                                        mCurrentUser, mContext.getPackageManager(), intent,
                                        mContext, mConfigStore.isPrivateSpaceInDocsUIEnabled())
                                        != null);
                    }
                }
            }
        }

        private static boolean isDeviceSupported(Context context) {
            // The feature requires Android R DocumentsContract APIs and INTERACT_ACROSS_USERS_FULL
            // permission.
            return VersionUtils.isAtLeastR()
                    && context.checkSelfPermission(Manifest.permission.INTERACT_ACROSS_USERS)
                    == PackageManager.PERMISSION_GRANTED;
        }
    }
}
