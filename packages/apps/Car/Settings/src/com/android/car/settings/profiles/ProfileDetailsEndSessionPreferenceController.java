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

import android.annotation.Nullable;
import android.app.admin.DevicePolicyManager;
import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.pm.UserInfo;
import android.os.UserHandle;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.Logger;
import com.android.car.ui.preference.CarUiPreference;

/**
 * Controller that ends a managed user session.
 */
public class ProfileDetailsEndSessionPreferenceController
        extends ProfileDetailsBasePreferenceController<CarUiPreference> {

    private static final Logger LOG = new Logger(
            ProfileDetailsEndSessionPreferenceController.class);

    @Nullable
    private final DevicePolicyManager mDpm;

    private @Nullable UserHandle mLogoutUser;

    public ProfileDetailsEndSessionPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);

        mDpm = context.getSystemService(DevicePolicyManager.class);
    }

    @Override
    protected Class<CarUiPreference> getPreferenceType() {
        return CarUiPreference.class;
    }

    @Override
    public void setUserInfo(UserInfo userInfo) {
        super.setUserInfo(userInfo);
    }

    @Override
    public boolean handlePreferenceClicked(CarUiPreference preference) {
        LOG.i("ending session (" + getUserInfo().toFullString() + ") and switching back to user "
                + mLogoutUser);
        boolean switched = ProfileHelper.getInstance(getContext()).logoutProfile();
        if (!switched) {
            LOG.e("Switch failed");
        }
        return true;
    }

    @Override
    protected int getDefaultAvailabilityStatus() {
        if (mDpm == null) {
            LOG.d("getAvailabilityStatus(): no dpm");
            return UNSUPPORTED_ON_DEVICE;
        }
        boolean isLogoutEnabled = mDpm.isLogoutEnabled();
        mLogoutUser = mDpm.getLogoutUser();
        LOG.d("getAvailabilityStatus(): isLogoutEnabled()=" + isLogoutEnabled + ", mLogoutUser="
                + mLogoutUser);
        return isLogoutEnabled && mLogoutUser != null
                ? AVAILABLE
                : CONDITIONALLY_UNAVAILABLE;
    }
}
