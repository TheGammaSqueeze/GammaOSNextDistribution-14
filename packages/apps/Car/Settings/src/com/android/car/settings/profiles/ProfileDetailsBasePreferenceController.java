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

import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;

/**
 * Common setup of all preference controllers related to profile details.
 *
 * @param <V> the upper bound on the type of {@link Preference} on which the controller
 *            expects to operate.
 */
public abstract class ProfileDetailsBasePreferenceController<V extends Preference> extends
        PreferenceController<V> {

    private UserInfo mUserInfo;
    private boolean mNeedToRefreshUserInfo = false;

    @VisibleForTesting
    final BroadcastReceiver mProfileUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshUserInfo();
            refreshUi();
        }
    };

    public ProfileDetailsBasePreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    /** Sets the user info for which this preference controller operates. */
    public void setUserInfo(UserInfo userInfo) {
        mUserInfo = userInfo;
    }

    /** Gets the current user info. */
    public UserInfo getUserInfo() {
        return mUserInfo;
    }

    /** Refreshes the user info, since it might have changed. */
    protected void refreshUserInfo() {
        mUserInfo = ProfileUtils.getUserInfo(getContext(), mUserInfo.id);
    }

    @Override
    protected void checkInitialized() {
        if (mUserInfo == null) {
            throw new IllegalStateException("UserInfo should be non-null by this point");
        }
    }

    /** Registers a listener which updates the displayed profile name when a profile is modified. */
    @Override
    protected void onStartInternal() {
        registerForProfileEvents();

        /* refresh UserInfo and UI only when restarting */
        if (mNeedToRefreshUserInfo) {
            refreshUserInfo();
            refreshUi();
        }
    }

    /**
     * Unregisters a listener which updates the displayed profile name when a profile is modified.
     */
    @Override
    protected void onStopInternal() {
        unregisterForProfileEvents();
        mNeedToRefreshUserInfo = true;
    }

    private void registerForProfileEvents() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_USER_INFO_CHANGED);
        getContext().registerReceiver(mProfileUpdateReceiver, filter);
    }

    private void unregisterForProfileEvents() {
        getContext().unregisterReceiver(mProfileUpdateReceiver);
    }
}
