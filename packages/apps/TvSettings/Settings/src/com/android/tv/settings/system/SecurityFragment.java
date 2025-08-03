/*
 * Copyright (C) 2023 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.tv.settings.system;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.os.UserManager;

import androidx.annotation.Keep;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;

import com.android.tv.settings.R;
import com.android.tv.settings.users.AppRestrictionsFragment;
import com.android.tv.settings.users.RestrictedProfileModel;

import java.util.List;

/**
 * The security settings screen in Tv settings.
 */
@Keep
public class SecurityFragment extends BaseSecurityFragment {
    private Preference mUnknownSourcesPref;
    private PreferenceGroup mRestrictedProfileGroup;
    private Preference mRestrictedProfileEnterPref;
    private Preference mRestrictedProfileExitPref;
    private Preference mRestrictedProfileAppsPref;
    private Preference mRestrictedProfilePinPref;
    private Preference mRestrictedProfileCreatePref;
    private Preference mRestrictedProfileDeletePref;

    private Preference mManageDeviceAdminPref;
    private Preference mEnterprisePrivacyPref;

    public static SecurityFragment newInstance() {
        return new SecurityFragment();
    }

    /**
     * Called by other Fragments to decide whether to show or hide profile-related views.
     */
    public static boolean isRestrictedProfileInEffect(Context context) {
        return new RestrictedProfileModel(context).isCurrentUser();
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.security, null);

        mUnknownSourcesPref = findPreference(KEY_UNKNOWN_SOURCES);
        mRestrictedProfileGroup = (PreferenceGroup) findPreference(KEY_RESTRICTED_PROFILE_GROUP);
        mRestrictedProfileEnterPref = findPreference(KEY_RESTRICTED_PROFILE_ENTER);
        mRestrictedProfileExitPref = findPreference(KEY_RESTRICTED_PROFILE_EXIT);
        mRestrictedProfileAppsPref = findPreference(KEY_RESTRICTED_PROFILE_APPS);
        mRestrictedProfilePinPref = findPreference(KEY_RESTRICTED_PROFILE_PIN);
        mRestrictedProfileCreatePref = findPreference(KEY_RESTRICTED_PROFILE_CREATE);
        mRestrictedProfileDeletePref = findPreference(KEY_RESTRICTED_PROFILE_DELETE);

        mManageDeviceAdminPref = findPreference(KEY_MANAGE_DEVICE_ADMIN);
        mEnterprisePrivacyPref = findPreference(KEY_ENTERPRISE_PRIVACY);
        refresh();
    }

    @Override
    protected void refresh() {
        if (mRestrictedProfile.isCurrentUser()) {
            // We are in restricted profile
            mUnknownSourcesPref.setVisible(false);

            mRestrictedProfileGroup.setVisible(true);
            mRestrictedProfileEnterPref.setVisible(false);
            mRestrictedProfileExitPref.setVisible(true);
            mRestrictedProfileAppsPref.setVisible(false);
            mRestrictedProfilePinPref.setVisible(false);
            mRestrictedProfileCreatePref.setVisible(false);
            mRestrictedProfileDeletePref.setVisible(false);
        } else if (mRestrictedProfile.getUser() != null) {
            // Not in restricted profile, but it exists
            mUnknownSourcesPref.setVisible(true);

            mRestrictedProfileGroup.setVisible(true);
            mRestrictedProfileEnterPref.setVisible(true);
            mRestrictedProfileExitPref.setVisible(false);
            mRestrictedProfileAppsPref.setVisible(true);
            mRestrictedProfilePinPref.setVisible(true);
            mRestrictedProfileCreatePref.setVisible(false);
            mRestrictedProfileDeletePref.setVisible(true);

            AppRestrictionsFragment.prepareArgs(mRestrictedProfileAppsPref.getExtras(),
                    mRestrictedProfile.getUser().id, false, false);
        } else if (UserManager.supportsMultipleUsers()) {
            // Not in restricted profile, and it doesn't exist
            mUnknownSourcesPref.setVisible(true);

            mRestrictedProfileGroup.setVisible(true);
            mRestrictedProfileEnterPref.setVisible(false);
            mRestrictedProfileExitPref.setVisible(false);
            mRestrictedProfileAppsPref.setVisible(false);
            mRestrictedProfilePinPref.setVisible(false);
            mRestrictedProfileCreatePref.setVisible(true);
            mRestrictedProfileDeletePref.setVisible(false);
        } else {
            // Not in restricted profile, and can't create one either
            mUnknownSourcesPref.setVisible(true);

            mRestrictedProfileGroup.setVisible(false);
            mRestrictedProfileEnterPref.setVisible(false);
            mRestrictedProfileExitPref.setVisible(false);
            mRestrictedProfileAppsPref.setVisible(false);
            mRestrictedProfilePinPref.setVisible(false);
            mRestrictedProfileCreatePref.setVisible(false);
            mRestrictedProfileDeletePref.setVisible(false);
        }

        mRestrictedProfileCreatePref.setEnabled(
                !isRestrictedProfileCreationInProgress());

        mUnknownSourcesPref.setEnabled(!isUnknownSourcesBlocked());

        mManageDeviceAdminPref.setVisible(hasActiveAdmins());
        mEnterprisePrivacyPref.setVisible(isDeviceManaged());
    }

    private boolean isUnknownSourcesBlocked() {
        final UserManager um = (UserManager) getContext().getSystemService(Context.USER_SERVICE);
        return um.hasUserRestriction(UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES);
    }

    private boolean isDeviceManaged() {
        final DevicePolicyManager devicePolicyManager = getContext().getSystemService(
                DevicePolicyManager.class);
        return devicePolicyManager.isDeviceManaged();
    }

    private boolean hasActiveAdmins() {
        final DevicePolicyManager devicePolicyManager = getContext().getSystemService(
                DevicePolicyManager.class);
        final List<ComponentName> admins = devicePolicyManager.getActiveAdmins();
        return (admins != null && !admins.isEmpty());
    }
}
