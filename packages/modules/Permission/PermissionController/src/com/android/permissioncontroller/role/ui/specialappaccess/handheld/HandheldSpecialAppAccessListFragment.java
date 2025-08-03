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

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.preference.PreferenceFragmentCompat;

import com.android.permissioncontroller.R;
import com.android.permissioncontroller.role.ui.handheld.SettingsFragment;

/**
 * Handheld fragment for the list of special app accesses.
 */
public class HandheldSpecialAppAccessListFragment extends SettingsFragment
        implements HandheldSpecialAppAccessListPreferenceFragment.Parent {

    /**
     * Create a new instance of this fragment.
     *
     * @return a new instance of this fragment
     */
    @NonNull
    public static HandheldSpecialAppAccessListFragment newInstance() {
        return new HandheldSpecialAppAccessListFragment();
    }

    @NonNull
    @Override
    protected PreferenceFragmentCompat onCreatePreferenceFragment() {
        return HandheldSpecialAppAccessListPreferenceFragment.newInstance();
    }

    @Override
    @StringRes
    protected int getEmptyTextResource() {
        return R.string.no_special_app_access;
    }

    @Override
    protected int getHelpUriResource() {
        return R.string.help_uri_special_app_access;
    }
}
