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
import android.content.Intent;
import android.content.pm.UserInfo;
import android.os.Bundle;

import androidx.annotation.NonNull;

import com.android.car.settings.common.SettingsFragment;
import com.android.car.ui.toolbar.ToolbarController;

/** Common logic shared for controlling the action bar which contains a button to delete a
 * profile.
 */
public abstract class ProfileDetailsBaseFragment extends SettingsFragment {
    private UserInfo mUserInfo;

    /** Adds user id to fragment arguments. */
    protected static ProfileDetailsBaseFragment addUserIdToFragmentArguments(
            ProfileDetailsBaseFragment fragment, int userId) {
        Bundle bundle = new Bundle();
        bundle.putInt(Intent.EXTRA_USER_ID, userId);
        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        int userId = getArguments().getInt(Intent.EXTRA_USER_ID);
        mUserInfo = ProfileUtils.getUserInfo(getContext(), userId);
    }

    @Override
    protected void setupToolbar(@NonNull ToolbarController toolbar) {
        super.setupToolbar(toolbar);

        toolbar.setTitle(getTitleText());
    }

    /** Make UserInfo available to subclasses. */
    protected UserInfo getUserInfo() {
        return mUserInfo;
    }

    /** Refresh UserInfo in case it becomes invalid. */
    protected void refreshUserInfo() {
        mUserInfo = ProfileUtils.getUserInfo(getContext(), mUserInfo.id);
    }

    /** Defines the text that should be shown in the action bar. */
    protected abstract String getTitleText();
}
