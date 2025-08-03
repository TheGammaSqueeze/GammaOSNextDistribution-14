/*
 * Copyright (C) 2019 The Android Open Source Project
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

import android.content.Context;
import android.content.pm.UserInfo;

import androidx.annotation.Nullable;

import com.android.car.settings.R;
import com.android.car.settings.common.ConfirmationDialogFragment;
import com.android.settingslib.utils.StringUtil;

/**
 * Provides common Profiles-related ConfirmationDialogFragments to ensure consistency
 */
public final class ProfilesDialogProvider {

    /** Argument key to store the profile info that the device is trying to make an admin. */
    static final String KEY_PROFILE_TO_MAKE_ADMIN = "PROFILE_TO_MAKE_ADMIN";
    /** Argument key to store the profile type that the device is trying to remove. */
    static final String KEY_PROFILE_TYPE = "PROFILE_TYPE";
    /** {@link KEY_PROFILE_TYPE} value when removing the last admin on the device. */
    static final String LAST_ADMIN = "LAST_ADMIN";
    /** {@link KEY_PROFILE_TYPE} value when removing the last profile on the device. */
    static final String LAST_PROFILE = "LAST_PROFILE";
    /**
     * Default {@link KEY_PROFILE_TYPE} value when removing a profile that is neither
     * {@link LAST_ADMIN} nor {@link LAST_PROFILE}.
     */
    static final String ANY_PROFILE = "ANY_PROFILE";

    private ProfilesDialogProvider() {
    }

    /** Gets a confirmation dialog fragment to confirm or reject adding a new profile. */
    public static ConfirmationDialogFragment getConfirmCreateNewProfileDialogFragment(
            Context context, @Nullable ConfirmationDialogFragment.ConfirmListener confirmListener,
            @Nullable ConfirmationDialogFragment.RejectListener rejectListener) {

        String message = context.getString(R.string.user_add_user_message_setup)
                .concat(System.lineSeparator())
                .concat(System.lineSeparator())
                .concat(context.getString(R.string.user_add_user_message_update));

        ConfirmationDialogFragment dialogFragment = new ConfirmationDialogFragment.Builder(context)
                .setTitle(R.string.user_add_user_title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, confirmListener)
                .setNegativeButton(android.R.string.cancel, rejectListener)
                .build();

        return dialogFragment;
    }

    /** Gets a confirmation dialog fragment to confirm or reject exiting retail mode. */
    public static ConfirmationDialogFragment getConfirmExitRetailModeDialogFragment(Context context,
            @Nullable ConfirmationDialogFragment.ConfirmListener confirmListener,
            @Nullable ConfirmationDialogFragment.RejectListener rejectListener) {
        return new ConfirmationDialogFragment.Builder(context)
                .setTitle(R.string.exit_retail_mode_dialog_title)
                .setMessage(R.string.exit_retail_mode_dialog_body)
                .setPositiveButton(
                        R.string.exit_retail_mode_dialog_confirmation_button_text, confirmListener)
                .setNegativeButton(android.R.string.cancel, rejectListener)
                .build();
    }

    /**
     * Gets a confirmation dialog fragment to indicate the maximum allowed number of profiles is
     * reached.
     */
    public static ConfirmationDialogFragment getMaxProfilesLimitReachedDialogFragment(
            Context context, int maxProfileLimit) {
        return new ConfirmationDialogFragment.Builder(context)
                .setTitle(R.string.user_limit_reached_title)
                .setMessage(StringUtil.getIcuPluralsString(context, maxProfileLimit,
                        R.string.user_limit_reached_message))
                .setPositiveButton(android.R.string.ok, /* confirmListener= */ null)
                .build();
    }

    /** Gets a confirmation dialog fragment to confirm or reject making a profile an admin. */
    public static ConfirmationDialogFragment getConfirmGrantAdminDialogFragment(Context context,
            @Nullable ConfirmationDialogFragment.ConfirmListener confirmListener,
            @Nullable ConfirmationDialogFragment.RejectListener rejectListener,
            UserInfo userToMakeAdmin) {

        String message = context.getString(R.string.grant_admin_permissions_message)
                .concat(System.lineSeparator())
                .concat(System.lineSeparator())
                .concat(context.getString(R.string.action_not_reversible_message));

        ConfirmationDialogFragment dialogFragment = new ConfirmationDialogFragment.Builder(context)
                .setTitle(R.string.grant_admin_permissions_title)
                .setMessage(message)
                .setPositiveButton(R.string.confirm_grant_admin, confirmListener)
                .setNegativeButton(android.R.string.cancel, rejectListener)
                .addArgumentParcelable(KEY_PROFILE_TO_MAKE_ADMIN, userToMakeAdmin)
                .build();

        return dialogFragment;
    }

    /**
     * Gets a confirmation dialog fragment to confirm or reject removing the last profile on the
     * device.
     */
    public static ConfirmationDialogFragment getConfirmRemoveLastProfileDialogFragment(
            Context context, @Nullable ConfirmationDialogFragment.ConfirmListener confirmListener,
            @Nullable ConfirmationDialogFragment.RejectListener rejectListener) {

        String message = context.getString(R.string.delete_last_user_delete_warning)
                .concat(System.lineSeparator())
                .concat(System.lineSeparator())
                .concat(context.getString(R.string.delete_last_user_system_setup_required_message));

        ConfirmationDialogFragment dialogFragment = new ConfirmationDialogFragment.Builder(context)
                .setTitle(R.string.delete_last_user_dialog_title)
                .setMessage(message)
                .setPositiveButton(R.string.delete_button, confirmListener)
                .setNegativeButton(android.R.string.cancel, rejectListener)
                .addArgumentString(KEY_PROFILE_TYPE, LAST_PROFILE)
                .build();

        return dialogFragment;
    }

    /**
     * Gets a confirmation dialog fragment to confirm or reject removing the last admin profile on
     * the device.
     */
    public static ConfirmationDialogFragment getConfirmRemoveLastAdminDialogFragment(
            Context context,
            @Nullable ConfirmationDialogFragment.ConfirmListener confirmListener,
            @Nullable ConfirmationDialogFragment.RejectListener rejectListener) {

        ConfirmationDialogFragment dialogFragment = new ConfirmationDialogFragment.Builder(context)
                .setTitle(R.string.choose_new_admin_title)
                .setMessage(R.string.choose_new_admin_message)
                .setPositiveButton(R.string.choose_new_admin_label, confirmListener)
                .setNegativeButton(android.R.string.cancel, rejectListener)
                .addArgumentString(KEY_PROFILE_TYPE, LAST_ADMIN)
                .build();

        return dialogFragment;
    }

    /**
     * Gets a confirmation dialog fragment to confirm or reject removing a profile that is neither
     * the last admin nor the last profile on the device.
     */
    public static ConfirmationDialogFragment getConfirmRemoveProfileDialogFragment(Context context,
            @Nullable ConfirmationDialogFragment.ConfirmListener confirmListener,
            @Nullable ConfirmationDialogFragment.RejectListener rejectListener) {

        ConfirmationDialogFragment dialogFragment = new ConfirmationDialogFragment.Builder(context)
                .setTitle(R.string.delete_user_dialog_title)
                .setMessage(R.string.delete_user_dialog_message)
                .setPositiveButton(R.string.delete_button, confirmListener)
                .setNegativeButton(android.R.string.cancel, rejectListener)
                .addArgumentString(KEY_PROFILE_TYPE, ANY_PROFILE)
                .build();

        return dialogFragment;
    }
}
