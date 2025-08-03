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

package com.android.permissioncontroller.permission.ui.auto;


import static com.android.permissioncontroller.Constants.EXTRA_SESSION_ID;
import static com.android.permissioncontroller.Constants.INVALID_SESSION_ID;

import android.app.Application;
import android.content.Context;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.permissioncontroller.R;
import com.android.permissioncontroller.auto.AutoSettingsFrameFragment;
import com.android.permissioncontroller.permission.model.livedatatypes.PermGroupPackagesUiInfo;
import com.android.permissioncontroller.permission.ui.UnusedAppsFragment;
import com.android.permissioncontroller.permission.ui.model.ManagePermissionsViewModel;
import com.android.permissioncontroller.permission.ui.model.ManageStandardPermissionsViewModel;
import com.android.permissioncontroller.permission.ui.model.PermissionGroupPreferenceUtils;
import com.android.permissioncontroller.permission.utils.Utils;

import java.util.List;

/**
 * Shows the standard permissions that can be granted/denied.
 *
 * Largely based on the implementation of
 * {@link com.android.permissioncontroller.permission.ui.television.ManagePermissionsFragment}.
 */
public class AutoManageStandardPermissionsFragment extends AutoSettingsFrameFragment {

    private static final String KEY_OTHER_PERMISSIONS = "other_permissions";
    private static final String KEY_AUTO_REVOKE = "auto_revoke_key";
    private ManagePermissionsViewModel mManagePermissionsViewModel;
    private ManageStandardPermissionsViewModel mManageStandardPermissionsViewModel;

    /** Returns a new instance of {@link AutoManageStandardPermissionsFragment}. */
    public static AutoManageStandardPermissionsFragment newInstance() {
        return new AutoManageStandardPermissionsFragment();
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setLoading(true);
        setHeaderLabel(getString(R.string.app_permission_manager));

        final Application application = getActivity().getApplication();
        final ViewModelProvider.Factory viewModelFactory =
                ViewModelProvider.AndroidViewModelFactory.getInstance(application);
        mManagePermissionsViewModel = new ViewModelProvider(this, viewModelFactory)
                .get(ManagePermissionsViewModel.class);
        mManageStandardPermissionsViewModel = new ViewModelProvider(this,
                ViewModelProvider.AndroidViewModelFactory.getInstance(application))
                .get(ManageStandardPermissionsViewModel.class);
        mManagePermissionsViewModel.getUsedPermissionGroups().observe(this,
                this::onPermissionGroupsChanged);
        mManageStandardPermissionsViewModel.getNumAutoRevoked().observe(this,
                this::onNumAutoRevokedChanged);
    }

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
        setPreferenceScreen(getPreferenceManager().createPreferenceScreen(getContext()));
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

        PermissionGroupPreferenceUtils.updateGroupOfPermissionPreferences(context, screen,
                permissionGroups);

        screen.addPreference(otherPermissionsPreference);

        // load initial auto-revoke count, if it is ready
        Integer numAutoRevoked = mManageStandardPermissionsViewModel.getNumAutoRevoked().getValue();
        onNumAutoRevokedChanged(numAutoRevoked);

        setLoading(false);
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
            AutoManageOtherPermissionsFragment frag =
                    new AutoManageOtherPermissionsFragment();
            frag.setTargetFragment(AutoManageStandardPermissionsFragment.this,
                    /* requestCode= */ 0);
            FragmentTransaction ft = getFragmentManager().beginTransaction();
            ft.replace(android.R.id.content, frag);
            ft.addToBackStack(null);
            ft.commit();
            return true;
        });
        // Make invisible for now and subscribe to the LiveData that tracks whether there are any
        // unused or additional permissions.
        preference.setVisible(false);
        mManagePermissionsViewModel.hasUnusedOrAdditionalPermissionGroups().observe(this,
                preference::setVisible);
        return preference;
    }

    private void onNumAutoRevokedChanged(Integer numAutoRevoked) {
        // to prevent ui jank, don't display auto-revoke until categories have loaded
        if (mManagePermissionsViewModel.getUsedPermissionGroups().getValue() == null) {
            return;
        }
        Preference autoRevokePreference = getPreferenceScreen().findPreference(KEY_AUTO_REVOKE);
        if (numAutoRevoked != null && numAutoRevoked != 0) {
            if (autoRevokePreference == null) {
                autoRevokePreference = new Preference(getPreferenceManager().getContext());
                autoRevokePreference.setOrder(-1);
                autoRevokePreference.setKey(KEY_AUTO_REVOKE);
                autoRevokePreference.setSingleLineTitle(false);
                autoRevokePreference.setIcon(R.drawable.ic_info_outline);
                autoRevokePreference.setTitle(
                        R.string.auto_revoke_permission_notification_title);
                autoRevokePreference.setSummary(
                        R.string.auto_revoke_setting_subtitle);
                autoRevokePreference.setOnPreferenceClickListener(preference -> {
                    AutoUnusedAppsFragment frag = AutoUnusedAppsFragment.newInstance();
                    frag.setArguments(UnusedAppsFragment.createArgs(
                            getArguments().getLong(EXTRA_SESSION_ID, INVALID_SESSION_ID)));
                    frag.setTargetFragment(AutoManageStandardPermissionsFragment.this,
                            /* requestCode= */ 0);
                    FragmentTransaction ft = getFragmentManager().beginTransaction();
                    ft.replace(android.R.id.content, frag);
                    ft.addToBackStack(null);
                    ft.commit();
                    return true;
                });

                getPreferenceScreen().addPreference(autoRevokePreference);
            }
        } else if (numAutoRevoked != null && autoRevokePreference != null) {
            getPreferenceScreen().removePreference(autoRevokePreference);
        }
    }
}
