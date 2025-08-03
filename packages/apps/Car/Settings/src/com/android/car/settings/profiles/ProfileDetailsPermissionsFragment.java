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

import android.content.Context;

import androidx.annotation.XmlRes;

import com.android.car.settings.R;

/**
 * Business logic for the permissions fragment. This fragment is used when an admin user views the
 * user details of a non-admin user.
 */
public class ProfileDetailsPermissionsFragment extends ProfileDetailsBaseFragment {

    /**
     * Creates instance of ProfileDetailsPermissionsFragment.
     */
    public static ProfileDetailsPermissionsFragment newInstance(int userId) {
        return (ProfileDetailsPermissionsFragment) addUserIdToFragmentArguments(
                new ProfileDetailsPermissionsFragment(), userId);
    }

    @Override
    @XmlRes
    protected int getPreferenceScreenResId() {
        return R.xml.profile_details_permissions_fragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        use(ProfileDetailsHeaderPreferenceController.class,
                R.string.pk_profile_details_permissions_header).setUserInfo(getUserInfo());
        use(ProfileDetailsActionButtonsPreferenceController.class,
                R.string.pk_profile_details_permissions_action_buttons).setUserInfo(getUserInfo());
        use(PermissionsPreferenceController.class, R.string.pk_profile_permissions).setUserInfo(
                getUserInfo());
    }

    @Override
    protected String getTitleText() {
        return getContext().getString(R.string.user_details_admin_title,
                ProfileUtils.getProfileDisplayName(getContext(), getUserInfo()));
    }
}

