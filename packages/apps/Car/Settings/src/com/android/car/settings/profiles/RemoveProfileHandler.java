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

package com.android.car.settings.profiles;

import static android.os.UserManager.DISALLOW_REMOVE_USER;

import static com.android.car.settings.common.PreferenceController.AVAILABLE;
import static com.android.car.settings.common.PreferenceController.AVAILABLE_FOR_VIEWING;
import static com.android.car.settings.common.PreferenceController.DISABLED_FOR_PROFILE;
import static com.android.car.settings.enterprise.ActionDisabledByAdminDialogFragment.DISABLED_BY_ADMIN_CONFIRM_DIALOG_TAG;
import static com.android.car.settings.enterprise.EnterpriseUtils.hasUserRestrictionByDpm;
import static com.android.car.settings.enterprise.EnterpriseUtils.hasUserRestrictionByUm;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.UserInfo;
import android.os.UserHandle;
import android.os.UserManager;
import android.widget.Toast;

import androidx.annotation.VisibleForTesting;

import com.android.car.settings.R;
import com.android.car.settings.common.ConfirmationDialogFragment;
import com.android.car.settings.common.ErrorDialog;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.enterprise.EnterpriseUtils;

/**
 * Consolidates profile removal logic into one handler so we can have consistent logic across
 * various parts of the Settings app.
 */
public class RemoveProfileHandler {
    @VisibleForTesting
    static final String REMOVE_PROFILE_DIALOG_TAG = "RemoveProfileDialogFragment";

    private final Context mContext;
    private final ProfileHelper mProfileHelper;
    private final UserManager mUserManager;
    private final FragmentController mFragmentController;

    private UserInfo mUserInfo;

    @VisibleForTesting
    ConfirmationDialogFragment.ConfirmListener mRemoveConfirmListener;

    public RemoveProfileHandler(Context context, ProfileHelper profileHelper,
            UserManager userManager, FragmentController fragmentController) {
        mContext = context;
        mProfileHelper = profileHelper;
        mUserManager = userManager;
        mFragmentController = fragmentController;
    }

    /**
     * Sets the profile info to be handled for removal
     * @param userInfo UserInfo of the profile to remove
     */
    public void setUserInfo(UserInfo userInfo) {
        mUserInfo = userInfo;

        mRemoveConfirmListener = arguments -> {
            String profileType = arguments.getString(ProfilesDialogProvider.KEY_PROFILE_TYPE);
            if (profileType.equals(ProfilesDialogProvider.LAST_ADMIN)) {
                mFragmentController.launchFragment(
                        ChooseNewAdminFragment.newInstance(userInfo));
            } else {
                int removeProfileResult = mProfileHelper.removeProfile(mContext, mUserInfo);
                if (removeProfileResult == ProfileHelper.REMOVE_PROFILE_RESULT_SUCCESS) {
                    mFragmentController.goBack();
                } else {
                    // If failed, need to show error dialog for users.
                    mFragmentController.showDialog(
                            ErrorDialog.newInstance(mProfileHelper.getErrorMessageForProfileResult(
                                    removeProfileResult)), null);
                }
            }
        };
    }

    /**
     * Resets listeners as they can get unregistered with certain configuration changes.
     */
    public void resetListeners() {
        ConfirmationDialogFragment removeProfileDialog =
                (ConfirmationDialogFragment) mFragmentController.findDialogByTag(
                        REMOVE_PROFILE_DIALOG_TAG);

        ConfirmationDialogFragment.resetListeners(
                removeProfileDialog,
                mRemoveConfirmListener,
                /* rejectListener= */ null,
                /* neutralListener= */ null);
    }

    /**
     * Checks to see if the current active profile can delete the requested profile.
     * @param userInfo UserInfo of the profile to delete
     * @return True if the profile can be deleted by the current active profile. False otherwise.
     */
    public boolean canRemoveProfile(UserInfo userInfo) {
        return !mUserManager.hasUserRestriction(DISALLOW_REMOVE_USER)
                && !isSystemOrDemoUser(userInfo)
                && !isNonCurrentForegroundUser(userInfo);
    }

    /**
     * Checks to see if the current active profile is {@code USER_SYSTEM} or has user type
     * {@code USER_TYPE_FULL_DEMO}
     */
    public boolean isSystemOrDemoUser(UserInfo userInfo) {
        return userInfo.id == UserHandle.USER_SYSTEM
                || mUserManager.isDemoUser();
    }

    /**
     * Returns {@code PreferenceController.AVAILABLE} when preference should be available,
     * {@code PreferenceController.DISABLED_FOR_PROFILE} when preference should be unavailable,
     * {@code PreferenceController.AVAILABLE_FOR_VIEWING} when preference is visible but
     * disabled.
     *
     * @param context to get user restriction
     * @param userInfo target user to check
     * @param availableForCurrentProcessUser when true, only available for current user process.
     * When false, disabled for current user process.
     */
    public int getAvailabilityStatus(Context context, UserInfo userInfo,
            boolean availableForCurrentProcessUser) {
        boolean allowCurrentProcess =
                !(availableForCurrentProcessUser ^ mProfileHelper.isCurrentProcessUser(userInfo));
        if (canRemoveProfile(userInfo) && allowCurrentProcess) {
            return AVAILABLE;
        }
        if (!allowCurrentProcess || isSystemOrDemoUser(userInfo)
                || hasUserRestrictionByUm(context, DISALLOW_REMOVE_USER)) {
            return DISABLED_FOR_PROFILE;
        }
        return AVAILABLE_FOR_VIEWING;
    }

    /**
     * Show the remove profile confirmation dialog. This will handle edge cases such as removing
     * the last profile or removing the last admin profile.
     */
    public void showConfirmRemoveProfileDialog() {
        boolean isLastProfile = mProfileHelper.getAllPersistentProfiles().size() == 1;
        boolean isLastAdmin = mUserInfo.isAdmin()
                && mProfileHelper.getAllAdminProfiles().size() == 1;

        ConfirmationDialogFragment dialogFragment;

        if (isLastProfile) {
            dialogFragment = ProfilesDialogProvider.getConfirmRemoveLastProfileDialogFragment(
                    mContext, mRemoveConfirmListener, /* rejectListener= */ null);
        } else if (isLastAdmin) {
            dialogFragment = ProfilesDialogProvider.getConfirmRemoveLastAdminDialogFragment(
                    mContext, mRemoveConfirmListener, /* rejectListener= */ null);
        } else {
            dialogFragment = ProfilesDialogProvider.getConfirmRemoveProfileDialogFragment(mContext,
                    mRemoveConfirmListener, /* rejectListener= */ null);
        }
        mFragmentController.showDialog(dialogFragment, REMOVE_PROFILE_DIALOG_TAG);
    }

    /**
     * When the Preference is disabled while still visible, {@code ActionDisabledByAdminDialog}
     * should be shown when the action is disallowed by a device owner or a profile owner.
     * Otherwise, a {@code Toast} will be shown to inform the user that the action is disabled.
     */
    public void runClickableWhileDisabled() {
        if (hasUserRestrictionByDpm(mContext, DISALLOW_REMOVE_USER)) {
            showActionDisabledByAdminDialog();
        } else if (isNonCurrentForegroundUser(mUserInfo)) {
            Toast.makeText(mContext, mContext.getString(R.string.cannot_remove_driver_profile),
                    Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(mContext, mContext.getString(R.string.action_unavailable),
                    Toast.LENGTH_LONG).show();
        }
    }

    /** Returns whether the provided user is the foreground user and the current user is not. */
    private boolean isNonCurrentForegroundUser(UserInfo userInfo) {
        if (userInfo == null) {
            return false;
        }
        int foregroundUserId = ActivityManager.getCurrentUser();
        return userInfo.id == foregroundUserId && UserHandle.myUserId() != foregroundUserId;
    }

    private void showActionDisabledByAdminDialog() {
        mFragmentController.showDialog(
                EnterpriseUtils.getActionDisabledByAdminDialog(mContext,
                        DISALLOW_REMOVE_USER),
                DISABLED_BY_ADMIN_CONFIRM_DIALOG_TAG);
    }
}
