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

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.UserManager;

import androidx.annotation.Keep;
import androidx.preference.Preference;

import com.android.tv.settings.R;
import com.android.tv.settings.users.RestrictedProfileModel;

/**
 * A subset of security settings to create a restricted profile.
 */
@Keep
public class CreateRestrictedProfileFragment extends BaseSecurityFragment {
    private static final String KEY_RESTRICTED_PROFILE_ALREADY_CREATED =
            "restricted_profile_already_created";

    private Preference mRestrictedProfileCreatePref;
    private Preference mRestrictedProfileAlreadyCreatedPref;

    public static CreateRestrictedProfileFragment newInstance() {
        return new CreateRestrictedProfileFragment();
    }

    /**
     * Called by other Fragments to decide whether to show or hide profile-related views.
     */
    public static boolean isRestrictedProfileInEffect(Context context) {
        return new RestrictedProfileModel(context).isCurrentUser();
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.create_restricted_profile, null);
        mRestrictedProfileCreatePref = findPreference(KEY_RESTRICTED_PROFILE_CREATE);
        mRestrictedProfileAlreadyCreatedPref = findPreference(
                KEY_RESTRICTED_PROFILE_ALREADY_CREATED);
        refresh();
    }

    @Override
    protected void refresh() {
        mRestrictedProfileCreatePref.setEnabled(
                UserManager.supportsMultipleUsers() && !isRestrictedProfileCreationInProgress());
        if (mRestrictedProfile.getUser() != null) {
            mRestrictedProfileAlreadyCreatedPref.setVisible(true);
            mRestrictedProfileCreatePref.setVisible(false);
        } else {
            mRestrictedProfileAlreadyCreatedPref.setVisible(false);
            mRestrictedProfileCreatePref.setVisible(true);
        }
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        final String key = preference.getKey();
        if (KEY_RESTRICTED_PROFILE_SKIP.equals(key)) {
            requireActivity().setResult(Activity.RESULT_CANCELED);
            requireActivity().finish();
            return true;
        }
        return super.onPreferenceTreeClick(preference);
    }

    @Override
    protected boolean shouldExitAfterUpdatingApps() {
        return true;
    }
}
