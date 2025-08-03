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

package com.android.permissioncontroller.role.ui.specialappaccess.handheld;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.preference.PreferenceFragmentCompat;

import com.android.permissioncontroller.R;
import com.android.permissioncontroller.role.ui.handheld.SettingsFragment;

/**
 * Handheld fragment for a special app access.
 */
public class HandheldSpecialAppAccessFragment extends SettingsFragment
        implements HandheldSpecialAppAccessPreferenceFragment.Parent {

    @NonNull
    private String mRoleName;

    /**
     * Create a new instance of this fragment.
     *
     * @param roleName the name of the role for the special app access
     *
     * @return a new instance of this fragment
     */
    @NonNull
    public static HandheldSpecialAppAccessFragment newInstance(@NonNull String roleName) {
        HandheldSpecialAppAccessFragment fragment = new HandheldSpecialAppAccessFragment();
        Bundle arguments = new Bundle();
        arguments.putString(Intent.EXTRA_ROLE_NAME, roleName);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle arguments = getArguments();
        mRoleName = arguments.getString(Intent.EXTRA_ROLE_NAME);
    }

    @NonNull
    @Override
    protected PreferenceFragmentCompat onCreatePreferenceFragment() {
        return HandheldSpecialAppAccessPreferenceFragment.newInstance(mRoleName);
    }

    @Override
    @StringRes
    protected int getEmptyTextResource() {
        return R.string.special_app_access_no_apps;
    }
}
