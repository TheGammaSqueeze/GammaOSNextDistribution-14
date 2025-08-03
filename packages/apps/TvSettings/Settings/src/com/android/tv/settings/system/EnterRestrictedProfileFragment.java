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

import androidx.annotation.Keep;
import androidx.preference.Preference;

import com.android.tv.settings.R;
import com.android.tv.settings.users.RestrictedProfileModel;

/**
 * A subset of security settings to enter a restricted profile.
 */
@Keep
public class EnterRestrictedProfileFragment extends BaseSecurityFragment {
    private static final String KEY_RESTRICTED_PROFILE_NOT_FOUND =
            "restricted_profile_not_found";
    private static final String KEY_RESTRICTED_PROFILE_ALREADY_ENTERED =
            "restricted_profile_already_entered";

    private Preference mRestrictedProfileEnterPref;
    private Preference mRestrictedProfileNotFoundPerf;
    private Preference mRestrictedProfileAlreadyEnteredPerf;

    public static EnterRestrictedProfileFragment newInstance() {
        return new EnterRestrictedProfileFragment();
    }

    /**
     * Called by other Fragments to decide whether to show or hide profile-related views.
     */
    public static boolean isRestrictedProfileInEffect(Context context) {
        return new RestrictedProfileModel(context).isCurrentUser();
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.enter_restricted_profile, null);
        mRestrictedProfileEnterPref = findPreference(KEY_RESTRICTED_PROFILE_ENTER);
        mRestrictedProfileNotFoundPerf = findPreference(KEY_RESTRICTED_PROFILE_NOT_FOUND);
        mRestrictedProfileAlreadyEnteredPerf = findPreference(
                KEY_RESTRICTED_PROFILE_ALREADY_ENTERED);
        refresh();
    }

    @Override
    protected void refresh() {
        if (mRestrictedProfile.isCurrentUser()) {
            mRestrictedProfileAlreadyEnteredPerf.setVisible(true);
            mRestrictedProfileNotFoundPerf.setVisible(false);
            mRestrictedProfileEnterPref.setVisible(false);
        } else if (mRestrictedProfile.getUser() == null) {
            mRestrictedProfileAlreadyEnteredPerf.setVisible(false);
            mRestrictedProfileNotFoundPerf.setVisible(true);
            mRestrictedProfileEnterPref.setVisible(false);
        } else {
            mRestrictedProfileAlreadyEnteredPerf.setVisible(false);
            mRestrictedProfileNotFoundPerf.setVisible(false);
            mRestrictedProfileEnterPref.setVisible(true);
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
        requireActivity().setResult(Activity.RESULT_OK); // For enter.
        return super.onPreferenceTreeClick(preference);
    }

    @Override
    protected boolean shouldExitAfterUpdatingApps() {
        return true;
    }
}
