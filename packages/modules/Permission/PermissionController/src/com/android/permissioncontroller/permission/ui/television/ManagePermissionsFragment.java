/*
 * Copyright (C) 2015 The Android Open Source Project
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

import static android.Manifest.permission_group.NOTIFICATIONS;

import static com.android.permissioncontroller.permission.ui.television.DebugConfig.DEBUG;
import static com.android.permissioncontroller.permission.ui.television.DebugConfig.TAG;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.permissioncontroller.R;
import com.android.permissioncontroller.permission.model.livedatatypes.PermGroupPackagesUiInfo;
import com.android.permissioncontroller.permission.ui.model.ManagePermissionsViewModel;
import com.android.permissioncontroller.permission.ui.model.PermissionGroupPreferenceUtils;
import com.android.permissioncontroller.permission.utils.Utils;

import java.util.ArrayList;
import java.util.List;

/**
 * The {@link androidx.fragment.app.Fragment} that represents the "main" screen of the
 * PermissionController application.
 * It lists the most relevant permission groups and contains a link to a different screen that lists
 * the other permission groups - {@link ManagePermissionsOtherFragment}.
 * It relies on the {@link ManagePermissionsViewModel} to for the data loading, maintaining and
 * monitoring for changes.
 */
public final class ManagePermissionsFragment extends SettingsWithHeader {
    private static final String KEY_OTHER_PERMISSIONS = "other_permissions";

    private ManagePermissionsViewModel mViewModel;

    /**
     * @return a new instance of {@link ManagePermissionsFragment}.
     */
    public static ManagePermissionsFragment newInstance() {
        return new ManagePermissionsFragment();
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        final FragmentActivity activity = requireActivity();
        final ViewModelProvider.Factory viewModelFactory =
                ViewModelProvider.AndroidViewModelFactory.getInstance(activity.getApplication());
        mViewModel = new ViewModelProvider(activity, viewModelFactory)
                        .get(ManagePermissionsViewModel.class);
        if (DEBUG) {
            Log.d(TAG, "ManagePermissionsFragment.onCreate()\n  viewModel=" + mViewModel);
        }
        mViewModel.getUsedPermissionGroups().observe(this, this::onPermissionGroupsChanged);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final String title = getString(R.string.manage_permissions_decor_title);
        setHeader(null, null, null, title);
    }

    private void onPermissionGroupsChanged(List<PermGroupPackagesUiInfo> permissionGroups) {
        final Context context = getPreferenceManager().getContext();
        if (context == null) {
            return;
        }

        final PreferenceScreen screen = getPreferenceScreen();

        // First check if "Other preferences" button exists. If it does: remove it, but hold on to
        // the reference - we'll it add back later, after the preferences for the permission groups
        // have been updated. If it does not exist: create and hold on to it.
        Preference otherPermissionsPreference = screen.findPreference(KEY_OTHER_PERMISSIONS);
        if (otherPermissionsPreference == null) {
            otherPermissionsPreference = buildOtherPermissionsPreference(context);
        } else {
            screen.removePreference(otherPermissionsPreference);
            // The PreferenceScreen is ordering items as added
            // (see PreferenceGroup#setOrderingAsAdded()), which means that it assigns positional
            // indexes ("order") to Preferences incrementally as they are added, BUT ONLY IF their
            // current "order" is the DEFAULT_ORDER.
            // However, when the Preferences are removed from the group they keep their "order" and
            // thus when they are re-added to a group (same or another) their "order" does not get
            // re-assigned, so they may show up at the position they previously were at.
            // We want the otherPermissionsPreference to always be the last in the list, so reset
            // its "order" to DEFAULT, so that we add last to the group, it indeed goes into the
            // last position.
            otherPermissionsPreference.setOrder(Preference.DEFAULT_ORDER);
        }

        ArrayList<PermGroupPackagesUiInfo> filteredGroups = new ArrayList<>(permissionGroups);
        filteredGroups.removeIf(info -> info.getName().equals(NOTIFICATIONS));
        PermissionGroupPreferenceUtils.updateGroupOfPermissionPreferences(context, screen,
                filteredGroups);

        screen.addPreference(otherPermissionsPreference);
    }

    private Preference buildOtherPermissionsPreference(Context context) {
        final Preference preference = new Preference(context);
        preference.setPersistent(false);
        preference.setKey(KEY_OTHER_PERMISSIONS);
        preference.setTitle(R.string.other_permissions_label);
        preference.setIcon(
                Utils.applyTint(
                        context, R.drawable.ic_more_items, android.R.attr.colorControlNormal));
        preference.setOnPreferenceClickListener(p -> {
            getParentFragmentManager().beginTransaction()
                    .replace(android.R.id.content, ManagePermissionsOtherFragment.newInstance())
                    .addToBackStack(null)
                    .commit();
            return true;
        });
        // Make invisible for now and subscribe to the LiveData that tracks whether there are any
        // unused or additional permissions.
        preference.setVisible(false);
        mViewModel.hasUnusedOrAdditionalPermissionGroups().observe(this, preference::setVisible);
        return preference;
    }
}
