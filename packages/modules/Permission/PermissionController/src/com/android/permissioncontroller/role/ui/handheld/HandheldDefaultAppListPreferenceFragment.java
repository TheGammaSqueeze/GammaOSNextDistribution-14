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

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceFragmentCompat;

import com.android.permissioncontroller.role.ui.DefaultAppListChildFragment;
import com.android.permissioncontroller.role.ui.RolePreference;

/**
 * Handheld preference fragment for the list of default apps.
 * <p>
 * Must be added as a child fragment and its parent fragment must implement {@link Parent}.
 */
public class HandheldDefaultAppListPreferenceFragment extends PreferenceFragmentCompat
        implements DefaultAppListChildFragment.Parent {

    /**
     * Create a new instance of this fragment.
     *
     * @return a new instance of this fragment
     */
    @NonNull
    public static HandheldDefaultAppListPreferenceFragment newInstance() {
        return new HandheldDefaultAppListPreferenceFragment();
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (savedInstanceState == null) {
            DefaultAppListChildFragment fragment = DefaultAppListChildFragment.newInstance();
            getChildFragmentManager().beginTransaction()
                    .add(fragment, null)
                    .commit();
        }
    }

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        // Preferences will be added by the child fragment later.
    }

    @NonNull
    @Override
    public RolePreference createPreference() {
        return new HandheldRolePreference(requireContext());
    }

    @Override
    public void onPreferenceScreenChanged() {
        requireParent().onPreferenceScreenChanged();
    }

    @NonNull
    private Parent requireParent() {
        //noinspection unchecked
        return (Parent) requireParentFragment();
    }

    /**
     * Interface that the parent fragment must implement.
     */
    public interface Parent {

        /**
         * Callback when changes have been made to the {@link androidx.preference.PreferenceScreen}
         * of this {@link PreferenceFragmentCompat}.
         */
        void onPreferenceScreenChanged();
    }
}
