/*
 * Copyright (C) 2018 The Android Open Source Project
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

import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;

/** Business logic for populating the profiles for the profiles list settings. */
public class ProfilesListPreferenceController extends ProfilesBasePreferenceController {

    public ProfilesListPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @Override
    protected void profileClicked(UserInfo userInfo) {
        if (ProfileUtils.isAdminViewingNonAdmin(UserManager.get(getContext()), userInfo)) {
            // Admin viewing non admin.
            getFragmentController().launchFragment(
                    ProfileDetailsPermissionsFragment.newInstance(userInfo.id));
        } else {
            getFragmentController().launchFragment(ProfileDetailsFragment.newInstance(userInfo.id));
        }
    }

    @Override
    protected void updateState(PreferenceGroup preferenceGroup) {
        super.updateState(preferenceGroup);

        if (getProfilesToDisplay().isEmpty()) {
            // Post the fragment navigation because FragmentManager may still be executing
            // transactions
            getContext().getMainExecutor().execute(() -> getFragmentController().goBack());
        } else {
            for (Preference preference : getProfilesToDisplay()) {
                preference.setWidgetLayoutResource(R.layout.preference_settings_icon);
            }
        }
    }
}
