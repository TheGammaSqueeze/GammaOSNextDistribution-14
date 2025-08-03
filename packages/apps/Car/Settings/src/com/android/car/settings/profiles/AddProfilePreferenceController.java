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

import static com.android.car.settings.enterprise.EnterpriseUtils.hasUserRestrictionByDpm;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.os.UserManager;

import androidx.preference.Preference;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;
import com.android.internal.annotations.VisibleForTesting;

/**
 * Business Logic for preference either adds a new profile or exits retail mode, depending on the
 * current profile.
 */
public class AddProfilePreferenceController extends PreferenceController<Preference> {

    @VisibleForTesting
    static final String MAX_PROFILES_LIMIT_REACHED_DIALOG_TAG =
            "com.android.car.settings.profiles.MaxProfilesLimitReachedDialog";

    private UserManager mUserManager;
    private DemoProfileDialogHandler mDemoProfileDialogHandler;
    private AddProfileHandler mAddProfileHandler;

    public AddProfilePreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController,
            CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mUserManager = context.getSystemService(UserManager.class);
        mDemoProfileDialogHandler = new DemoProfileDialogHandler(context, fragmentController);
        mAddProfileHandler = new AddProfileHandler(context, fragmentController, this);
    }

    @Override
    protected Class<Preference> getPreferenceType() {
        return Preference.class;
    }

    @Override
    protected void onCreateInternal() {
        mDemoProfileDialogHandler.onCreateInternal();
        mAddProfileHandler.onCreateInternal();

        if (mUserManager.isDemoUser()) {
            getPreference().setTitle(R.string.exit_retail_button_text);
        } else {
            getPreference().setTitle(R.string.add_profile_text);
            getPreference().setIcon(R.drawable.ic_add);
        }
        setClickableWhileDisabled(getPreference(), /* clickable= */ true, p ->
                mAddProfileHandler.runClickableWhileDisabled());
    }

    @Override
    protected void onStopInternal() {
        mAddProfileHandler.onStopInternal();
    }

    @Override
    protected void onDestroyInternal() {
        mAddProfileHandler.onDestroyInternal();
    }

    @Override
    protected void updateState(Preference preference) {
        mAddProfileHandler.updateState(preference);
    }

    @Override
    protected boolean handlePreferenceClicked(Preference preference) {
        // If the user is a demo user, show a dialog asking if they want to exit retail/demo mode.
        if (mUserManager.isDemoUser()) {
            mDemoProfileDialogHandler.showExitRetailDialog();
            return true;
        }

        if (!mUserManager.canAddMoreUsers()
                || hasUserRestrictionByDpm(getContext(), UserManager.DISALLOW_ADD_USER)) {
            mAddProfileHandler.runClickableWhileDisabled();
            return true;
        }

        // Add the add profile button if the current profile is allowed to add a profile.
        if (mAddProfileHandler.canAddProfiles(mUserManager)) {
            mAddProfileHandler.showAddProfileDialog();
            return true;
        }

        return false;
    }

    @Override
    protected int getDefaultAvailabilityStatus() {
        return mAddProfileHandler
                .getAddProfilePreferenceAvailabilityStatus(getContext());
    }

    @VisibleForTesting
    void setUserManager(UserManager userManager) {
        mUserManager = userManager;
    }
}
