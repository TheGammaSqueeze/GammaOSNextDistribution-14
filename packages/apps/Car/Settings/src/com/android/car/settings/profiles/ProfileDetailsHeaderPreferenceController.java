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
import android.graphics.drawable.Drawable;

import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;

/** Header preference showing the profile's icon, name, and summary on the profile details page. */
public class ProfileDetailsHeaderPreferenceController extends
        ProfileDetailsBasePreferenceController<Preference> {

    private ProfileHelper mProfileHelper;

    public ProfileDetailsHeaderPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mProfileHelper = ProfileHelper.getInstance(getContext());
    }

    @Override
    protected Class<Preference> getPreferenceType() {
        return Preference.class;
    }

    @Override
    protected void updateState(Preference preference) {
        Drawable icon = new ProfileIconProvider().getDrawableWithBadge(getContext(), getUserInfo());

        preference.setIcon(icon);
        preference.setTitle(ProfileUtils.getProfileDisplayName(getContext(), getUserInfo()));
        preference.setSummary(getSummary());
    }

    @VisibleForTesting
    void setProfileHelper(ProfileHelper profileHelper) {
        mProfileHelper = profileHelper;
    }

    private CharSequence getSummary() {
        if (!getUserInfo().isInitialized()) {
            return getContext().getString(R.string.user_summary_not_set_up);
        }
        if (getUserInfo().isAdmin()) {
            return mProfileHelper.isCurrentProcessUser(getUserInfo())
                    ? getContext().getString(R.string.signed_in_admin_user)
                    : getContext().getString(R.string.user_admin);
        }
        return null;
    }
}
