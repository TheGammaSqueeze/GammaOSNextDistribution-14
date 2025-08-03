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

package com.android.car.settings.privacy;

import android.app.ActivityManager;
import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.pm.UserInfo;
import android.os.UserManager;
import android.widget.Toast;

import androidx.preference.Preference;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;
import com.android.car.settings.profiles.ProfileHelper;
import com.android.car.settings.profiles.ProfileUtils;
import com.android.car.settings.profiles.RemoveProfileHandler;
import com.android.internal.annotations.VisibleForTesting;

/** Business logic for preference that deletes the current user profile. */
public class DeleteUserPreferenceController
        extends PreferenceController<Preference> {

    private final UserInfo mUserInfo;
    private final RemoveProfileHandler mRemoveProfileHandler;

    public DeleteUserPreferenceController(Context context,
            String preferenceKey, FragmentController fragmentController,
            CarUxRestrictions uxRestrictions) {
        this(context, preferenceKey, fragmentController, uxRestrictions,
                new RemoveProfileHandler(context, ProfileHelper.getInstance(context),
                        UserManager.get(context), fragmentController));
    }

    @VisibleForTesting
    DeleteUserPreferenceController(Context context,
            String preferenceKey, FragmentController fragmentController,
            CarUxRestrictions uxRestrictions, RemoveProfileHandler removeProfileHandler) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mRemoveProfileHandler = removeProfileHandler;
        mUserInfo = ProfileUtils.getUserInfo(getContext(), ActivityManager.getCurrentUser());
        mRemoveProfileHandler.setUserInfo(mUserInfo);
    }

    @Override
    protected void onCreateInternal() {
        super.onCreateInternal();
        mRemoveProfileHandler.resetListeners();
        setClickableWhileDisabled(getPreference(), /* clickable= */true, p ->
                Toast.makeText(getContext(), getContext().getString(R.string.action_unavailable),
                        Toast.LENGTH_LONG).show());
    }

    @Override
    protected Class<Preference> getPreferenceType() {
        return Preference.class;
    }

    @Override
    protected boolean handlePreferenceClicked(Preference preference) {
        mRemoveProfileHandler.showConfirmRemoveProfileDialog();
        return true;
    }

    @AvailabilityStatus
    protected int getDefaultAvailabilityStatus() {
        return mRemoveProfileHandler.canRemoveProfile(mUserInfo) ? AVAILABLE
                : AVAILABLE_FOR_VIEWING;
    }
}
