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

package com.android.permissioncontroller.role.ui.handheld;

import android.app.ActionBar;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.preference.PreferenceFragmentCompat;

import com.android.modules.utils.build.SdkLevel;
import com.android.permissioncontroller.R;
import com.android.permissioncontroller.permission.utils.Utils;
import com.android.permissioncontroller.role.utils.UiUtils;
import com.android.settingslib.HelpUtils;
import com.android.settingslib.collapsingtoolbar.CollapsingToolbarBaseFragment;

import com.google.android.material.appbar.CollapsingToolbarLayout;

/**
 * Base class for settings fragments.
 */
// Made public for com.android.permissioncontroller.role.ui.specialappaccess.handheld
public abstract class SettingsFragment extends CollapsingToolbarBaseFragment {

    @NonNull
    private View mLoadingView;
    @NonNull
    private TextView mEmptyText;

    @NonNull
    private PreferenceFragmentCompat mPreferenceFragment;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
        inflater.inflate(R.layout.settings_fragment_include, getContentFrameLayout());
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mLoadingView = view.findViewById(R.id.loading);
        mEmptyText = view.findViewById(R.id.empty);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        ActionBar actionBar = requireActivity().getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        if (savedInstanceState == null) {
            mPreferenceFragment = onCreatePreferenceFragment();
            getChildFragmentManager().beginTransaction()
                    .add(R.id.preference_fragment_container, mPreferenceFragment)
                    .commit();
        } else {
            mPreferenceFragment = (PreferenceFragmentCompat) getChildFragmentManager()
                    .findFragmentById(R.id.preference_fragment_container);
        }
        mEmptyText.setText(getEmptyTextResource());
        updateStateViews();
    }

    @NonNull
    protected abstract PreferenceFragmentCompat onCreatePreferenceFragment();

    @StringRes
    protected abstract int getEmptyTextResource();

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);

        if (!SdkLevel.isAtLeastS()) {
            Utils.prepareSearchMenuItem(menu, requireContext());
            int helpUriResource = getHelpUriResource();
            if (helpUriResource != 0) {
                HelpUtils.prepareHelpMenuItem(requireActivity(), menu, helpUriResource,
                        getClass().getName());
            }
        }
    }

    @StringRes
    protected int getHelpUriResource() {
        return 0;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                requireActivity().finishAfterTransition();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * Set the title of the current settings page.
     *
     * @param title the title of the current settings page
     */
    public void setTitle(@NonNull CharSequence title) {
        requireActivity().setTitle(title);
        CollapsingToolbarLayout collapsingToolbarLayout = getCollapsingToolbarLayout();
        if (collapsingToolbarLayout != null) {
            collapsingToolbarLayout.setTitle(title);
        }
    }

    /**
     * Callback when changes have been made to the {@link androidx.preference.PreferenceScreen}
     * of this {@link PreferenceFragmentCompat}.
     */
    public void onPreferenceScreenChanged() {
        updateStateViews();
    }

    private void updateStateViews() {
        boolean isLoading = mPreferenceFragment.getPreferenceManager() == null
                || mPreferenceFragment.getPreferenceScreen() == null;
        UiUtils.setViewShown(mLoadingView, isLoading);
        boolean isEmpty = !isLoading
                && mPreferenceFragment.getPreferenceScreen().getPreferenceCount() == 0;
        UiUtils.setViewShown(mEmptyText, isEmpty);
    }
}
