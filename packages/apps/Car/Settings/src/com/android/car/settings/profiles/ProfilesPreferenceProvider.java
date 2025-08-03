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
import android.content.pm.UserInfo;

import androidx.preference.Preference;

import com.android.car.settings.R;
import com.android.car.ui.preference.CarUiPreference;

import java.util.ArrayList;
import java.util.List;

/**
 * Constructs the preferences to be displayed in {@link ProfilesListFragment} and
 * {@link ChooseNewAdminFragment}.
 */
public class ProfilesPreferenceProvider {

    /**
     * Interface for registering clicks on profiles.
     */
    public interface ProfileClickListener {
        /**
         * Invoked when profile is clicked.
         *
         * @param userInfo User for which the click is registered.
         */
        void onProfileClicked(UserInfo userInfo);
    }

    private final Context mContext;
    private final ProfileClickListener mProfilePreferenceClickListener;
    private boolean mIncludeCurrentProfile;
    private boolean mIncludeGuest;

    public ProfilesPreferenceProvider(Context context, ProfileClickListener listener) {
        mContext = context;
        mProfilePreferenceClickListener = listener;
        mIncludeCurrentProfile = false;
        mIncludeGuest = false;
    }

    /**
     * Sets whether createProfileList should include an entry for the current profile. Default is
     * {@code true}.
     */
    public void setIncludeCurrentProfile(boolean includeCurrentProfile) {
        mIncludeCurrentProfile = includeCurrentProfile;
    }

    /**
     * Sets whether createProfileList should include an entry for the guest profile. Default is
     * {@code true}.
     */
    public void setIncludeGuest(boolean includeGuest) {
        mIncludeGuest = includeGuest;
    }

    /**
     * Creates the list of profiles (as preferences). The first profile will be the current profile
     * (if requested) and the last profile will be the guest profile (if requested). Otherwise, the
     * list is populated with all of the remaining switchable profiles.
     */
    public List<Preference> createProfileList() {
        List<Preference> profiles = new ArrayList<>();
        UserInfo currUserInfo = ProfileHelper.getInstance(mContext).getCurrentProcessUserInfo();

        // Show current profile at the top of the list.
        if (mIncludeCurrentProfile) {
            profiles.add(createProfilePreference(currUserInfo));
        }

        // Display all profiles on the system, except: Guests and current profile who's displayed
        // already.
        List<UserInfo> infos = ProfileHelper.getInstance(mContext).getAllLivingProfiles(
                userInfo -> !userInfo.isGuest() && userInfo.id != currUserInfo.id);
        for (UserInfo userInfo : infos) {
            profiles.add(createProfilePreference(userInfo));
        }

        // Display guest session option.
        if (mIncludeGuest) {
            profiles.add(createGuestProfilePreference());
        }

        return profiles;
    }

    private Preference createProfilePreference(UserInfo userInfo) {
        CarUiPreference preference = new CarUiPreference(mContext);
        preference.setIcon(
                new ProfileIconProvider().getRoundedProfileIcon(userInfo, mContext));
        preference.setTitle(ProfileUtils.getProfileDisplayName(mContext, userInfo));

        if (!userInfo.isInitialized()) {
            preference.setSummary(R.string.user_summary_not_set_up);
        }
        if (userInfo.isAdmin()) {
            preference.setSummary(isCurrentProfile(userInfo)
                    ? R.string.signed_in_admin_user : R.string.user_admin);
        }

        preference.setOnPreferenceClickListener(pref -> {
            if (mProfilePreferenceClickListener == null) {
                return false;
            }
            mProfilePreferenceClickListener.onProfileClicked(userInfo);
            return true;
        });

        return preference;
    }

    private Preference createGuestProfilePreference() {
        CarUiPreference preference = new CarUiPreference(mContext);
        preference.setIcon(
                new ProfileIconProvider().getRoundedGuestDefaultIcon(mContext));
        preference.setTitle(com.android.internal.R.string.guest_name);
        return preference;
    }

    private boolean isCurrentProfile(UserInfo userInfo) {
        return ProfileHelper.getInstance(mContext).isCurrentProcessUser(userInfo);
    }
}
