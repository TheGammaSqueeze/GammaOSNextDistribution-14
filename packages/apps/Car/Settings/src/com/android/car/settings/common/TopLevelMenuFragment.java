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

package com.android.car.settings.common;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.XmlRes;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.car.settings.R;
import com.android.car.ui.recyclerview.CarUiRecyclerView;

import java.util.List;

/**
 * Top level settings menu.
 */
public class TopLevelMenuFragment extends SettingsFragment {

    /**
     * The preference key for the top-level menu item associated with a fragment.
     * This is intended to be included with fragments launched from top-level menu
     * preferences using the {@link #launchFragment} method.
     */
    public static final String FRAGMENT_MENU_PREFERENCE_KEY = "fragment_menu_preference_key";

    private static final String KEY_SAVED_SELECTED_PREFERENCE_KEY = "saved_selected_preference_key";

    private String mSelectedPreferenceKey;

    @Override
    @XmlRes
    protected int getPreferenceScreenResId() {
        return R.xml.homepage_fragment;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (savedInstanceState != null
                && savedInstanceState.getString(KEY_SAVED_SELECTED_PREFERENCE_KEY) != null) {
            updatePreferenceHighlight(
                    savedInstanceState.getString(KEY_SAVED_SELECTED_PREFERENCE_KEY));
        } else {
            updatePreferenceHighlight(getActivity().getIntent()
                    .getStringExtra(BaseCarSettingsActivity.META_DATA_KEY_HEADER_KEY));
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_SAVED_SELECTED_PREFERENCE_KEY, mSelectedPreferenceKey);
    }

    @Override
    public CarUiRecyclerView onCreateCarUiRecyclerView(LayoutInflater inflater, ViewGroup parent,
            Bundle savedInstanceState) {
        inflater.inflate(R.layout.top_level_recyclerview, parent, /* attachToRoot= */ true);
        return parent.findViewById(R.id.top_level_recycler_view);
    }

    @Override
    public void launchFragment(@Nullable Fragment fragment) {
        if (fragment == null) {
            return;
        }
        String preferenceKey = null;
        if (fragment.getArguments() != null) {
            preferenceKey = fragment.getArguments().getString(FRAGMENT_MENU_PREFERENCE_KEY);
        }
        if (TextUtils.equals(getCurrentFragmentClass(), fragment.getClass().getName())
                && TextUtils.equals(preferenceKey, mSelectedPreferenceKey)) {
            // Do nothing - already at the location being navigated to.
            return;
        }
        clearBackStack();
        updatePreferenceHighlight(preferenceKey);
        super.launchFragment(fragment);
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (preference.getFragment() == null) {
            // No fragment provided - likely launching a new activity.
            return super.onPreferenceTreeClick(preference);
        }
        if (TextUtils.equals(getCurrentFragmentClass(), preference.getFragment())
                && TextUtils.equals(preference.getKey(), mSelectedPreferenceKey)) {
            // Do nothing - already at the location being navigated to.
            return true;
        }
        clearBackStack();
        updatePreferenceHighlight(preference.getKey());
        return super.onPreferenceTreeClick(preference);
    }

    @Override
    protected HighlightablePreferenceGroupAdapter createHighlightableAdapter(
            PreferenceScreen preferenceScreen) {
        return new HighlightablePreferenceGroupAdapter(preferenceScreen,
                R.drawable.top_level_preference_background,
                R.drawable.top_level_preference_highlight);
    }

    private void updatePreferenceHighlight(String key) {
        mSelectedPreferenceKey = key;
        if (!TextUtils.isEmpty(mSelectedPreferenceKey)) {
            requestPreferenceHighlight(mSelectedPreferenceKey);
        } else {
            clearPreferenceHighlight();
        }
    }

    @VisibleForTesting
    String getSelectedPreferenceKey() {
        return mSelectedPreferenceKey;
    }

    @Nullable
    private String getCurrentFragmentClass() {
        FragmentActivity activity = getActivity();
        if (activity == null) {
            return null;
        }
        Fragment currentFragment =
                activity.getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (currentFragment == null) {
            return null;
        }
        return currentFragment.getClass().getName();
    }

    private void clearBackStack() {
        FragmentActivity activity = getActivity();
        if (activity == null) {
            return;
        }

        // dismiss dialogs
        List<Fragment> fragments = activity.getSupportFragmentManager().getFragments();
        for (Fragment fragment : fragments) {
            if (fragment instanceof DialogFragment) {
                ((DialogFragment) fragment).dismiss();
            }
        }
        // clear fragments
        activity.getSupportFragmentManager().popBackStackImmediate(/* name= */ null,
                FragmentManager.POP_BACK_STACK_INCLUSIVE);
    }
}
