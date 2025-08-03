/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static android.os.UserManager.DISALLOW_ADD_USER;

import static com.android.car.settings.common.ActionButtonsPreference.ActionButtons;
import static com.android.car.settings.enterprise.EnterpriseUtils.hasUserRestrictionByDpm;
import static com.android.car.settings.enterprise.EnterpriseUtils.hasUserRestrictionByUm;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.pm.UserInfo;
import android.os.UserManager;

import androidx.annotation.VisibleForTesting;

import com.android.car.settings.R;
import com.android.car.settings.common.ActionButtonInfo;
import com.android.car.settings.common.ActionButtonsPreference;
import com.android.car.settings.common.ConfirmationDialogFragment;
import com.android.car.settings.common.FragmentController;

/**
 * Displays the action buttons for profile details.
 *
 * <p>The actions shown depends on the current and selected profile.
 * <ol>
 * <li>Rename: shown if selected profile is the current profile
 * <li>Make admin: shown if current profile is an admin and the selected profile is not
 * <li>Manage other profiles: shown if selected profile is the current profile and
 * there are other profiles
 * <li>Add a profile: shown if selected profile is the current profile and
 * there are no other profiles
 * <li> Exit demo: shown if selected profile is the current profile and is a demo
 * <li>Delete: shown if the current profile is allowed to remove profiles, is not a demo
 * profile, and selected profile is not the current profile
 * </ol>
 */
public final class ProfileDetailsActionButtonsPreferenceController
        extends ProfileDetailsBasePreferenceController<ActionButtonsPreference> {

    @VisibleForTesting
    static final String MAKE_ADMIN_DIALOG_TAG = "MakeAdminDialogFragment";

    private final ProfileHelper mProfileHelper;
    private final UserManager mUserManager;
    private DemoProfileDialogHandler mDemoProfileDialogHandler;
    private AddProfileHandler mAddProfileHandler;

    @VisibleForTesting
    final ConfirmationDialogFragment.ConfirmListener mMakeAdminConfirmListener =
            arguments -> {
                UserInfo profileToMakeAdmin =
                        (UserInfo) arguments.get(ProfilesDialogProvider.KEY_PROFILE_TO_MAKE_ADMIN);
                com.android.car.internal.user.UserHelper.grantAdminPermissions(getContext(),
                        profileToMakeAdmin.getUserHandle());
                getFragmentController().goBack();
            };

    private final RemoveProfileHandler mRemoveProfileHandler;

    public ProfileDetailsActionButtonsPreferenceController(Context context,
            String preferenceKey, FragmentController fragmentController,
            CarUxRestrictions uxRestrictions) {
        this(context, preferenceKey, fragmentController, uxRestrictions,
                ProfileHelper.getInstance(context), UserManager.get(context),
                new RemoveProfileHandler(context, ProfileHelper.getInstance(context),
                        UserManager.get(context), fragmentController));
    }

    @VisibleForTesting
    ProfileDetailsActionButtonsPreferenceController(Context context,
            String preferenceKey, FragmentController fragmentController,
            CarUxRestrictions uxRestrictions, ProfileHelper profileHelper, UserManager userManager,
            RemoveProfileHandler removeProfileHandler) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mProfileHelper = profileHelper;
        mUserManager = userManager;
        mRemoveProfileHandler = removeProfileHandler;
        mDemoProfileDialogHandler = new DemoProfileDialogHandler(context, fragmentController);
        mAddProfileHandler = new AddProfileHandler(context, fragmentController, this);
    }

    @Override
    protected Class<ActionButtonsPreference> getPreferenceType() {
        return ActionButtonsPreference.class;
    }

    @Override
    protected void onCreateInternal() {
        super.onCreateInternal();

        mDemoProfileDialogHandler.onCreateInternal();
        mAddProfileHandler.onCreateInternal();

        ConfirmationDialogFragment makeAdminDialog =
                (ConfirmationDialogFragment) getFragmentController().findDialogByTag(
                        MAKE_ADMIN_DIALOG_TAG);
        ConfirmationDialogFragment.resetListeners(
                makeAdminDialog,
                mMakeAdminConfirmListener,
                /* rejectListener= */ null,
                /* neutralListener= */ null);

        mRemoveProfileHandler.resetListeners();
    }

    @Override
    protected void onStartInternal() {
        super.onStartInternal();

        ActionButtonInfo renameButton = getPreference().getButton(ActionButtons.BUTTON1);
        ActionButtonInfo makeAdminButton = getPreference().getButton(ActionButtons.BUTTON2);
        ActionButtonInfo profilesButton = getPreference().getButton(ActionButtons.BUTTON3);
        ActionButtonInfo deleteButton = getPreference().getButton(ActionButtons.BUTTON4);

        boolean isDemoProfile = mUserManager.isDemoUser();
        // When DISALLOW_ADD_USER is set by device or profile owner, the button should still be
        // visible but disabled
        boolean shouldShowAddProfile = !(mUserManager.isAdminUser() && areThereOtherProfiles())
                && mProfileHelper.isCurrentProcessUser(getUserInfo())
                && !hasUserRestrictionByUm(getContext(), DISALLOW_ADD_USER);
        boolean shouldEnableAddProfile = shouldShowAddProfile
                && !hasUserRestrictionByDpm(getContext(), DISALLOW_ADD_USER);
        boolean shouldShowProfilesButton = isDemoProfile || shouldShowAddProfile
                || mUserManager.isAdminUser() && mProfileHelper.isCurrentProcessUser(getUserInfo())
                && areThereOtherProfiles();

        int removeProfileAvailabilityStatus = mRemoveProfileHandler.getAvailabilityStatus(
                getContext(), getUserInfo(), /* availableForCurrentProcessUser= */ false);
        boolean shouldShowDeleteProfile = removeProfileAvailabilityStatus != DISABLED_FOR_PROFILE;
        boolean shouldEnableDeleteProfile = removeProfileAvailabilityStatus == AVAILABLE;

        int profileButtonText;
        if (shouldShowAddProfile && isDemoProfile) {
            profileButtonText = R.string.exit_retail_button_text;
        } else if (shouldShowAddProfile) {
            profileButtonText = R.string.add_a_profile_button_text;
        } else {
            profileButtonText = R.string.manage_other_profiles_button_text;
        }

        renameButton
                .setText(R.string.bluetooth_rename_button)
                .setIcon(R.drawable.ic_edit)
                .setVisible(mProfileHelper.isCurrentProcessUser(getUserInfo()))
                .setOnClickListener(v -> getFragmentController().launchFragment(
                        EditProfileNameFragment.newInstance(getUserInfo())));

        makeAdminButton
                .setText(R.string.grant_admin_permissions_button_text)
                .setIcon(R.drawable.ic_person)
                .setVisible(ProfileUtils.isAdminViewingNonAdmin(mUserManager, getUserInfo()))
                .setOnClickListener(v -> showConfirmMakeAdminDialog());

        profilesButton
                .setText(profileButtonText)
                .setVisible(shouldShowProfilesButton)
                .setOnClickListener(v -> {
                    if (shouldShowAddProfile && isDemoProfile) {
                        mDemoProfileDialogHandler.showExitRetailDialog();
                    } else if (shouldShowAddProfile && !shouldEnableAddProfile) {
                        mAddProfileHandler.runClickableWhileDisabled();
                    } else if (shouldShowAddProfile) {
                        mAddProfileHandler.showAddProfileDialog();
                    } else {
                        getFragmentController().launchFragment(
                                new ProfilesListFragment());
                    }
                });

        if (!isDemoProfile && shouldShowAddProfile) {
            profilesButton.setIcon(R.drawable.ic_add);
        } else if (!isDemoProfile && shouldShowProfilesButton) {
            profilesButton.setIcon(R.drawable.ic_people);
        }

        // Do not show delete button if the current profile can't remove the selected profile
        deleteButton
                .setText(R.string.delete_button)
                .setIcon(R.drawable.ic_delete)
                .setVisible(shouldShowDeleteProfile)
                .setOnClickListener(v -> {
                    if (shouldEnableDeleteProfile) {
                        mRemoveProfileHandler.showConfirmRemoveProfileDialog();
                    } else {
                        mRemoveProfileHandler.runClickableWhileDisabled();
                    }
                });
    }

    @Override
    public void setUserInfo(UserInfo userInfo) {
        super.setUserInfo(userInfo);
        mRemoveProfileHandler.setUserInfo(userInfo);
    }

    @Override
    protected void onStopInternal() {
        super.onStopInternal();
        mAddProfileHandler.onStopInternal();
    }

    @Override
    protected void onDestroyInternal() {
        super.onDestroyInternal();
        mAddProfileHandler.onDestroyInternal();
    }

    @Override
    protected void updateState(ActionButtonsPreference preference) {
        mAddProfileHandler.updateState(preference);
    }

    private void showConfirmMakeAdminDialog() {
        ConfirmationDialogFragment dialogFragment =
                ProfilesDialogProvider.getConfirmGrantAdminDialogFragment(getContext(),
                        mMakeAdminConfirmListener, /* rejectListener= */ null, getUserInfo());

        getFragmentController().showDialog(dialogFragment, MAKE_ADMIN_DIALOG_TAG);
    }

    private boolean areThereOtherProfiles() {
        UserInfo currUserInfo = mProfileHelper.getCurrentProcessUserInfo();
        return !mProfileHelper.getAllLivingProfiles(
                userInfo -> !userInfo.isGuest() && userInfo.id != currUserInfo.id).isEmpty();
    }
}
