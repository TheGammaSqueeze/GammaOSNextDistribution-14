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

package com.android.permissioncontroller.permission.ui.handheld;

import android.app.ActionBar;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceFragmentCompat;

import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseFragment;

/**
 * Base class which act as a wrapper over a preference fragment
 */
public abstract class PermissionsCollapsingToolbarBaseFragment
        extends CollapsingToolbarBaseFragment {

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        ActionBar actionBar = requireActivity().getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        PreferenceFragmentCompat preferenceFragment =
                (PreferenceFragmentCompat) getChildFragmentManager()
                        .findFragmentById(
                                com.android.settingslib.collapsingtoolbar.R.id.content_frame);

        if (preferenceFragment == null) {
            preferenceFragment = createPreferenceFragment();
            preferenceFragment.setArguments(getArguments());
            getChildFragmentManager().beginTransaction()
                    .add(com.android.settingslib.collapsingtoolbar.R.id.content_frame,
                            preferenceFragment)
                    .commit();
        }
    }

    /**
     * @return a new instance of a customized PermissionsFrameFragment.
     */
    @NonNull
    public abstract PreferenceFragmentCompat createPreferenceFragment();
}
