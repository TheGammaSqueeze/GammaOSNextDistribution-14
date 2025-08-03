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

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.pm.UserInfo;
import android.os.UserManager;

import androidx.annotation.VisibleForTesting;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.Logger;
import com.android.car.ui.preference.CarUiPreference;

/**
 * Controller that displays the preference for letting the user delete the current profile
 */
public class ProfileDetailsDeletePreferenceController
        extends ProfileDetailsBasePreferenceController<CarUiPreference> {

    private static final Logger LOG = new Logger(ProfileDetailsDeletePreferenceController.class);

    private ProfileHelper mProfileHelper;

    private final RemoveProfileHandler mRemoveProfileHandler;

    public ProfileDetailsDeletePreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        this(context, preferenceKey, fragmentController, uxRestrictions,
                ProfileHelper.getInstance(context),
                new RemoveProfileHandler(context, ProfileHelper.getInstance(context),
                        UserManager.get(context), fragmentController));
    }

    @VisibleForTesting
    ProfileDetailsDeletePreferenceController(Context context,
            String preferenceKey, FragmentController fragmentController,
            CarUxRestrictions uxRestrictions, ProfileHelper profileHelper,
            RemoveProfileHandler removeProfileHandler) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mProfileHelper = profileHelper;
        mRemoveProfileHandler = removeProfileHandler;
    }

    @Override
    protected Class<CarUiPreference> getPreferenceType() {
        return CarUiPreference.class;
    }

    @Override
    protected void onCreateInternal() {
        super.onCreateInternal();
        mRemoveProfileHandler.resetListeners();
        setClickableWhileDisabled(getPreference(), /* clickable= */ true, p ->
                mRemoveProfileHandler.runClickableWhileDisabled());
    }

    @Override
    public void setUserInfo(UserInfo userInfo) {
        super.setUserInfo(userInfo);
        mRemoveProfileHandler.setUserInfo(userInfo);
    }

    @Override
    public boolean handlePreferenceClicked(CarUiPreference preference) {
        if (getAvailabilityStatus() == AVAILABLE_FOR_VIEWING) {
            mRemoveProfileHandler.runClickableWhileDisabled();
            return true;
        }
        mRemoveProfileHandler.showConfirmRemoveProfileDialog();
        return true;
    }

    @Override
    protected int getDefaultAvailabilityStatus() {
        return mRemoveProfileHandler.getAvailabilityStatus(
                getContext(), getUserInfo(), /* allowRemoveCurrentProcessUser= */ true);
    }
}
