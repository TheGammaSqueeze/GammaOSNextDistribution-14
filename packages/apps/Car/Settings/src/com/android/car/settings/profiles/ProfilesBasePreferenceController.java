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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.UserInfo;

import androidx.annotation.CallSuper;
import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Shared business logic between {@link ProfilesListFragment} and
 * {@link ChooseNewAdminFragment}.
 */
public abstract class ProfilesBasePreferenceController extends
        PreferenceController<PreferenceGroup> {

    private ProfilesPreferenceProvider mPreferenceProvider;
    private List<Preference> mProfilesToDisplay = new ArrayList<>();

    @VisibleForTesting
    final BroadcastReceiver mProfileUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            /** Update screen when profiles list is updated. */
            refreshUi();
        }
    };

    public ProfilesBasePreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        ProfilesPreferenceProvider.ProfileClickListener profileClickListener = this::profileClicked;
        mPreferenceProvider = new ProfilesPreferenceProvider(context, profileClickListener);
    }

    @Override
    protected Class<PreferenceGroup> getPreferenceType() {
        return PreferenceGroup.class;
    }

    /**
     * Ensure that helper is set by the time onCreate is called. Register a listener to refresh
     * screen on updates.
     */
    @Override
    @CallSuper
    protected void onCreateInternal() {
        registerForProfileEvents();
    }

    /** Unregister listener to refresh screen on updates. */
    @Override
    @CallSuper
    protected void onDestroyInternal() {
        unregisterForProfileEvents();
    }

    @Override
    protected void updateState(PreferenceGroup preferenceGroup) {
        List<Preference> newProfiles = mPreferenceProvider.createProfileList();
        if (profileListsAreDifferent(mProfilesToDisplay, newProfiles)) {
            mProfilesToDisplay = newProfiles;
            preferenceGroup.removeAll();

            for (Preference preference : mProfilesToDisplay) {
                preferenceGroup.addPreference(preference);
            }
        }
    }

    /** Handles the profile click on a preference for a certain profile. */
    protected abstract void profileClicked(UserInfo userInfo);


    /** Gets the preference provider to set additional arguments if necessary. */
    protected ProfilesPreferenceProvider getPreferenceProvider() {
        return mPreferenceProvider;
    }

    protected List<Preference> getProfilesToDisplay() {
        return mProfilesToDisplay;
    }

    private boolean profileListsAreDifferent(List<Preference> currentList,
            List<Preference> newList) {
        if (currentList.size() != newList.size()) {
            return true;
        }

        for (int i = 0; i < currentList.size(); i++) {
            // Cannot use "compareTo" on preference, since it uses the order attribute to compare.
            if (preferencesAreDifferent(currentList.get(i), newList.get(i))) {
                return true;
            }
        }

        return false;
    }

    private boolean preferencesAreDifferent(Preference lhs, Preference rhs) {
        return !Objects.equals(lhs.getTitle(), rhs.getTitle())
                || !Objects.equals(lhs.getSummary(), rhs.getSummary());
    }

    private void registerForProfileEvents() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_USER_INFO_CHANGED);
        getContext().registerReceiver(mProfileUpdateReceiver, filter);
    }

    private void unregisterForProfileEvents() {
        getContext().unregisterReceiver(mProfileUpdateReceiver);
    }
}
