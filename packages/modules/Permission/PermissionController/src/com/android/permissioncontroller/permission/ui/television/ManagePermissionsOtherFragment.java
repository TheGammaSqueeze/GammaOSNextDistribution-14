/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.permissioncontroller.permission.ui.television;

import static com.android.permissioncontroller.permission.ui.television.DebugConfig.DEBUG;
import static com.android.permissioncontroller.permission.ui.television.DebugConfig.TAG;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;

import com.android.permissioncontroller.R;
import com.android.permissioncontroller.permission.model.livedatatypes.PermGroupPackagesUiInfo;
import com.android.permissioncontroller.permission.ui.model.ManagePermissionsViewModel;
import com.android.permissioncontroller.permission.ui.model.PermissionGroupPreferenceUtils;

import java.util.List;

/**
 * The {@link androidx.fragment.app.Fragment} implements a screen that displays the list of
 * permission groups that are not considered the most relevant to the user at the moment, and thus
 * did not make it to the "main" screen - {@link ManagePermissionsFragment}.
 * It relies on the {@link ManagePermissionsViewModel} for the data loading, maintaining and
 * monitoring for changes.
 */
public class ManagePermissionsOtherFragment extends SettingsWithHeader {
    private static final String KEY_UNUSED_CATEGORY = "category_unused";
    private static final String KEY_ADDITIONAL_CATEGORY = "category_additional";

    private PreferenceCategory mUnusedCategory;
    private PreferenceCategory mAdditionalCategory;

    /**
     * @return a new instance of {@link ManagePermissionsOtherFragment}.
     */
    public static ManagePermissionsOtherFragment newInstance() {
        return new ManagePermissionsOtherFragment();
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        final FragmentActivity activity = requireActivity();
        final ViewModelProvider.Factory factory =
                ViewModelProvider.AndroidViewModelFactory.getInstance(activity.getApplication());
        final ManagePermissionsViewModel viewModel = new ViewModelProvider(activity, factory)
                        .get(ManagePermissionsViewModel.class);
        if (DEBUG) {
            Log.d(TAG, "ManagePermissionsOtherFragment.onCreate()\n  viewModel=" + viewModel);
        }
        viewModel.getUnusedPermissionGroups().observe(this, this::onUnusedPermissionGroupsChanged);
        viewModel.getAdditionalPermissionGroups().observe(this,
                this::onAdditionalPermissionGroupsChanged);
        viewModel.hasUnusedOrAdditionalPermissionGroups().observe(this,
                this::onHasUnusedOrAdditionalPermissionGroups);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setHeader(null, null, null, getString(R.string.other_permissions_label));

        final Context context = getPreferenceManager().getContext();
        final PreferenceScreen screen = getPreferenceScreen();
        // We add categories here, but make them invisible until the data is loaded.
        mUnusedCategory = new PreferenceCategory(context);
        mUnusedCategory.setKey(KEY_UNUSED_CATEGORY);
        mUnusedCategory.setTitle(R.string.not_used_permissions_label);
        mUnusedCategory.setSummary(R.string.not_used_permissions_description);
        mUnusedCategory.setVisible(false);
        screen.addPreference(mUnusedCategory);

        mAdditionalCategory = new PreferenceCategory(context);
        mAdditionalCategory.setKey(KEY_ADDITIONAL_CATEGORY);
        mAdditionalCategory.setTitle(R.string.additional_permissions_label);
        mAdditionalCategory.setSummary(R.string.additional_permissions_description);
        mAdditionalCategory.setVisible(false);
        screen.addPreference(mAdditionalCategory);
    }

    private void onUnusedPermissionGroupsChanged(List<PermGroupPackagesUiInfo> permissionGroups) {
        updateCategory(mUnusedCategory, permissionGroups);
    }

    private void onAdditionalPermissionGroupsChanged(
            List<PermGroupPackagesUiInfo> permissionGroups) {
        updateCategory(mAdditionalCategory, permissionGroups);
    }

    private void onHasUnusedOrAdditionalPermissionGroups(Boolean hasPGs) {
        if (!hasPGs) {
            if (DEBUG) {
                Log.d(TAG, "ManagePermissionsOtherFragment is \"empty\" now > go back.");
            }
            // There are not more permissions on this screen - go back.
            getParentFragmentManager().popBackStack();
        }
    }

    private void updateCategory(PreferenceCategory category,
            List<PermGroupPackagesUiInfo> permissionGroups) {
        final Context context = getPreferenceManager().getContext();
        if (context == null) {
            return;
        }

        PermissionGroupPreferenceUtils.updateGroupOfPermissionPreferences(context, category,
                permissionGroups);
        // Only show the category if it's not empty.
        category.setVisible(!permissionGroups.isEmpty());
    }
}
