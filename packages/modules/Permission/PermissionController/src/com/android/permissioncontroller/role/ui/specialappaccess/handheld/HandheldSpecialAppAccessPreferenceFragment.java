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
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.android.permissioncontroller.role.ui.RoleApplicationPreference;
import com.android.permissioncontroller.role.ui.specialappaccess.SpecialAppAccessChildFragment;
import com.android.settingslib.widget.FooterPreference;

/**
 * Handheld fragment for a special app access.
 * <p>
 * Must be added as a child fragment and its parent fragment must implement {@link Parent}.
 */
public class HandheldSpecialAppAccessPreferenceFragment extends PreferenceFragmentCompat
        implements SpecialAppAccessChildFragment.Parent {

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
    public static HandheldSpecialAppAccessPreferenceFragment newInstance(@NonNull String roleName) {
        HandheldSpecialAppAccessPreferenceFragment
                fragment = new HandheldSpecialAppAccessPreferenceFragment();
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

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (savedInstanceState == null) {
            SpecialAppAccessChildFragment fragment = SpecialAppAccessChildFragment.newInstance(
                    mRoleName);
            getChildFragmentManager().beginTransaction()
                    .add(fragment, null)
                    .commit();
        }
    }

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        // Preferences will be added by the child fragment later.
    }

    @Override
    public void setTitle(@NonNull CharSequence title) {
        requireParent().setTitle(title);
    }

    @NonNull
    @Override
    public RoleApplicationPreference createApplicationPreference() {
        return new HandheldSwitchPreference(requireContext());
    }

    @NonNull
    @Override
    public Preference createFooterPreference() {
        return new FooterPreference(requireContext());
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
         * Set the title of the current settings page.
         *
         * @param title the title of the current settings page
         */
        void setTitle(@NonNull CharSequence title);

        /**
         * Callback when changes have been made to the {@link androidx.preference.PreferenceScreen}
         * of this {@link PreferenceFragmentCompat}.
         */
        void onPreferenceScreenChanged();
    }
}
