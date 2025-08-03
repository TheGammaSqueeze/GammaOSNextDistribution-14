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

import static com.android.car.settings.common.TopLevelMenuFragment.FRAGMENT_MENU_PREFERENCE_KEY;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.os.Bundle;
import android.os.UserHandle;

import androidx.fragment.app.Fragment;
import androidx.preference.Preference;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.Logger;
import com.android.car.settings.common.PreferenceController;

/**
 * Controller for the top-level entry into Profile settings that open to the user's profile details
 * page.
 */
public class ProfilesEntryPreferenceController extends PreferenceController<Preference> {

    private static final Logger LOG = new Logger(ProfilesEntryPreferenceController.class);

    public ProfilesEntryPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @Override
    protected Class<Preference> getPreferenceType() {
        return Preference.class;
    }

    @Override
    public boolean handlePreferenceClicked(Preference preference) {
        Fragment fragment = ProfileDetailsFragment.newInstance(UserHandle.myUserId());

        Bundle args = fragment.getArguments();
        if (args == null) {
            args = new Bundle();
            fragment.setArguments(args);
        }
        args.putString(FRAGMENT_MENU_PREFERENCE_KEY, getPreferenceKey());
        getFragmentController().launchFragment(fragment);
        return true;
    }
}
