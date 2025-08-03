/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.permissioncontroller.role.ui.handheld;

import android.content.Intent;
import android.os.Bundle;
import android.os.UserHandle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.preference.PreferenceFragmentCompat;

import com.android.permissioncontroller.R;

/**
 * Handheld fragment for a default app.
 */
public class HandheldDefaultAppFragment extends SettingsFragment
        implements HandheldDefaultAppPreferenceFragment.Parent {

    @NonNull
    private String mRoleName;
    @NonNull
    private UserHandle mUser;

    /**
     * Create a new instance of this fragment.
     *
     * @param roleName the name of the role for the default app
     * @param user the user for the default app
     *
     * @return a new instance of this fragment
     */
    @NonNull
    public static HandheldDefaultAppFragment newInstance(@NonNull String roleName,
            @NonNull UserHandle user) {
        HandheldDefaultAppFragment fragment = new HandheldDefaultAppFragment();
        Bundle arguments = new Bundle();
        arguments.putString(Intent.EXTRA_ROLE_NAME, roleName);
        arguments.putParcelable(Intent.EXTRA_USER, user);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle arguments = getArguments();
        mRoleName = arguments.getString(Intent.EXTRA_ROLE_NAME);
        mUser = arguments.getParcelable(Intent.EXTRA_USER);
    }

    @NonNull
    @Override
    protected PreferenceFragmentCompat onCreatePreferenceFragment() {
        return HandheldDefaultAppPreferenceFragment.newInstance(mRoleName, mUser);
    }

    @Override
    @StringRes
    protected int getEmptyTextResource() {
        return R.string.default_app_no_apps;
    }
}
