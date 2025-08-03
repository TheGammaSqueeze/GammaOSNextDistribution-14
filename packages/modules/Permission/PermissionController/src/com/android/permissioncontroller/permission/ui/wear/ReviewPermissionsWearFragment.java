/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.permissioncontroller.permission.ui.wear;

import static android.content.pm.PackageManager.FLAG_PERMISSION_REVIEW_REQUIRED;
import static android.content.pm.PackageManager.FLAG_PERMISSION_USER_SET;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteCallback;
import android.os.UserHandle;
import android.text.Html;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.util.TypedValue;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;
import androidx.preference.TwoStatePreference;
import androidx.wear.ble.view.WearableDialogHelper;

import com.android.permissioncontroller.R;
import com.android.permissioncontroller.permission.model.AppPermissionGroup;
import com.android.permissioncontroller.permission.model.AppPermissions;
import com.android.permissioncontroller.permission.utils.Utils;

import java.util.ArrayList;
import java.util.List;

public class ReviewPermissionsWearFragment extends PreferenceFragmentCompat
        implements Preference.OnPreferenceChangeListener {
    private static final String TAG = "ReviewPermWear";

    private static final int ORDER_NEW_PERMS = 1;
    private static final int ORDER_CURRENT_PERMS = 2;
    // Category for showing actions should be displayed last.
    private static final int ORDER_ACTION = 100000;
    private static final int ORDER_PERM_OFFSET_START = 100;

    private static final String EXTRA_PACKAGE_INFO =
        "com.android.permissioncontroller.permission.ui.extra.PACKAGE_INFO";

    public static ReviewPermissionsWearFragment newInstance(PackageInfo packageInfo) {
        Bundle arguments = new Bundle();
        arguments.putParcelable(EXTRA_PACKAGE_INFO, packageInfo);
        ReviewPermissionsWearFragment instance = new ReviewPermissionsWearFragment();
        instance.setArguments(arguments);
        instance.setRetainInstance(true);
        return instance;
    }

    private AppPermissions mAppPermissions;

    private PreferenceCategory mNewPermissionsCategory;
    private PreferenceCategory mCurrentPermissionsCategory;

    private boolean mHasConfirmedRevoke;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        PackageInfo packageInfo = getArguments().getParcelable(EXTRA_PACKAGE_INFO);
        if (packageInfo == null) {
            activity.finish();
            return;
        }

        mAppPermissions = new AppPermissions(activity, packageInfo, false,
                () -> getActivity().finish());

        boolean reviewRequired = false;
        for (AppPermissionGroup group : mAppPermissions.getPermissionGroups()) {
            if (group.isReviewRequired()) {
                reviewRequired = true;
                break;
            }
        }

        if (!reviewRequired) {
            confirmPermissionsReview();
            activity.finish();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        mAppPermissions.refresh();
        loadPreferences();
    }

    private void loadPreferences() {
        Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        PreferenceScreen screen = getPreferenceScreen();
        if (screen == null) {
            screen = getPreferenceManager().createPreferenceScreen(getActivity());
            setPreferenceScreen(screen);
        } else {
            screen.removeAll();
        }

        mCurrentPermissionsCategory = null;
        PreferenceGroup oldNewPermissionsCategory = mNewPermissionsCategory;
        mNewPermissionsCategory = null;

        final boolean isPackageUpdated = isPackageUpdated();
        int permOrder = ORDER_PERM_OFFSET_START;

        PackageInfo pkg = mAppPermissions.getPackageInfo();
        ApplicationInfo appInfo = pkg.applicationInfo;

        for (AppPermissionGroup group : mAppPermissions.getPermissionGroups()) {
            if (!Utils.shouldShowPermission(getContext(), group)
                    || !Utils.OS_PKG.equals(group.getDeclaringPackage())) {
                continue;
            }

            final PermissionSwitchPreference preference;
            Preference cachedPreference = oldNewPermissionsCategory != null
                    ? oldNewPermissionsCategory.findPreference(group.getName()) : null;
            if (cachedPreference instanceof PermissionSwitchPreference) {
                preference = (PermissionSwitchPreference) cachedPreference;
            } else {
                preference = new PermissionSwitchPreference(getActivity());

                preference.setKey(group.getName());
                preference.setTitle(group.getLabel());
                preference.setPersistent(false);
                preference.setOrder(permOrder++);

                preference.setOnPreferenceChangeListener(this);
            }

            if (appInfo.targetSdkVersion < Build.VERSION_CODES.M &&
                    group.isReviewRequired() ) {
                preference.setChecked(true);
            } else {
                preference.setChecked(group.areRuntimePermissionsGranted());
            }

            // Mutable state
            if (group.isSystemFixed() || group.isPolicyFixed()) {
                preference.setEnabled(false);
            } else {
                preference.setEnabled(true);
            }
            if (preference.getParent() != null) {
                // Remove first if already added.
                preference.getParent().removePreference(preference);
            }
            if (group.isReviewRequired()) {
                if (!isPackageUpdated) {
                    // An app just being installed, which means all groups requiring reviews.
                    screen.addPreference(preference);
                } else {
                    if (mNewPermissionsCategory == null) {
                        mNewPermissionsCategory = new PreferenceCategory(activity);
                        mNewPermissionsCategory.setTitle(R.string.new_permissions_category);
                        mNewPermissionsCategory.setOrder(ORDER_NEW_PERMS);
                        screen.addPreference(mNewPermissionsCategory);
                    }
                    mNewPermissionsCategory.addPreference(preference);
                }
            } else {
                if (mCurrentPermissionsCategory == null) {
                    mCurrentPermissionsCategory = new PreferenceCategory(activity);
                    mCurrentPermissionsCategory.setTitle(R.string.current_permissions_category);
                    mCurrentPermissionsCategory.setOrder(ORDER_CURRENT_PERMS);
                    screen.addPreference(mCurrentPermissionsCategory);
                }
                mCurrentPermissionsCategory.addPreference(preference);
            }
        }
        addTitlePreferenceToScreen(screen);
        addActionPreferencesToScreen(screen);
    }

    private boolean isPackageUpdated() {
        List<AppPermissionGroup> groups = mAppPermissions.getPermissionGroups();
        final int groupCount = groups.size();
        for (int i = 0; i < groupCount; i++) {
            AppPermissionGroup group = groups.get(i);
            if (!group.isReviewRequired()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        Log.d(TAG, "onPreferenceChange " + preference.getTitle());
        if (mHasConfirmedRevoke) {
            return true;
        }
        if (preference instanceof PermissionSwitchPreference) {
            PermissionSwitchPreference permPreference = (PermissionSwitchPreference)
                    preference;
            permPreference.setChanged();
            if (permPreference.isChecked()) {
                showWarnRevokeDialog(permPreference);
            } else {
                return true;
            }
        }
        return false;
    }

    private void showWarnRevokeDialog(final SwitchPreference preference) {
        // When revoking, we set "confirm" as the negative icon to be shown at the bottom.
        new WearableDialogHelper.DialogBuilder(getContext())
                .setPositiveIcon(R.drawable.cancel_button)
                .setNegativeIcon(R.drawable.confirm_button)
                .setPositiveButton(R.string.grant_dialog_button_deny_anyway, (dialog, which) -> {
                    preference.setChecked(false);
                    mHasConfirmedRevoke = true;
                })
                .setNegativeButton(R.string.cancel, null)
                .setMessage(R.string.old_sdk_deny_warning)
                .show();
    }

    private void confirmPermissionsReview() {
        final List<PreferenceGroup> preferenceGroups = new ArrayList<>();
        if (mNewPermissionsCategory != null) {
            preferenceGroups.add(mNewPermissionsCategory);
        }
        if (mCurrentPermissionsCategory != null) {
            preferenceGroups.add(mCurrentPermissionsCategory);
        }
        if (preferenceGroups.isEmpty()){
            PreferenceScreen preferenceScreen = getPreferenceScreen();
            if (preferenceScreen != null) {
                preferenceGroups.add(preferenceScreen);
            }
        }

        for (PreferenceGroup preferenceGroup: preferenceGroups) {
            final int preferenceCount = preferenceGroup.getPreferenceCount();
            for (int i = 0; i < preferenceCount; i++) {
                Preference preference = preferenceGroup.getPreference(i);
                if (preference instanceof PermissionSwitchPreference) {
                    PermissionSwitchPreference permPreference = (PermissionSwitchPreference)
                            preference;
                    String groupName = preference.getKey();
                    AppPermissionGroup group = mAppPermissions.getPermissionGroup(groupName);
                    if (group.isReviewRequired() || permPreference.wasChanged()) {
                        if (permPreference.isChecked()) {
                            Log.i(TAG, groupName + " permPreference.isChecked()");
                            group.grantRuntimePermissions(true, false);
                        } else {
                            Log.i(TAG, groupName + " !permPreference.isChecked()");
                            group.revokeRuntimePermissions(false);
                        }
                    }

                    AppPermissionGroup backgroundGroup = group.getBackgroundPermissions();
                    if (backgroundGroup != null) {
                        // If the preference wasn't toggled we show it as "fully granted"
                        if (backgroundGroup.isReviewRequired() && !permPreference.wasChanged()) {
                            backgroundGroup.grantRuntimePermissions(true, false);
                        }
                        backgroundGroup.unsetReviewRequired();
                    }
                }
            }
        }

        // Some permission might be restricted and hence there is no AppPermissionGroup for it.
        // Manually unset all review-required flags, regardless of restriction.
        PackageManager pm = getContext().getPackageManager();
        PackageInfo pkg = mAppPermissions.getPackageInfo();
        UserHandle user = UserHandle.getUserHandleForUid(pkg.applicationInfo.uid);

        if (pkg.requestedPermissions == null) {
            // No flag updating to do
            return;
        }

        for (String perm : pkg.requestedPermissions) {
            try {
                pm.updatePermissionFlags(perm, pkg.packageName,
                        FLAG_PERMISSION_REVIEW_REQUIRED | FLAG_PERMISSION_USER_SET,
                        FLAG_PERMISSION_USER_SET, user);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Cannot unmark " + perm + " requested by " + pkg.packageName
                        + " as review required", e);
            }
        }
    }

    private void addTitlePreferenceToScreen(PreferenceScreen screen) {
        Activity activity = getActivity();
        Preference titlePref = new Preference(activity);
        screen.addPreference(titlePref);

        // Set icon
        Drawable icon = mAppPermissions.getPackageInfo().applicationInfo.loadIcon(
              activity.getPackageManager());
        titlePref.setIcon(icon);

        // Set message
        String appLabel = Html.escapeHtml(mAppPermissions.getAppLabel().toString());
        final int labelTemplateResId = isPackageUpdated()
                ?  R.string.permission_review_title_template_update
                :  R.string.permission_review_title_template_install;
        SpannableString message =
            new SpannableString(Html.fromHtml(getString(labelTemplateResId, appLabel)));

        // Color the app name.
        final int appLabelStart = message.toString().indexOf(appLabel, 0);
        final int appLabelLength = appLabel.length();

        TypedValue typedValue = new TypedValue();
        activity.getTheme().resolveAttribute(android.R.attr.colorAccent, typedValue, true);
        final int color = activity.getColor(typedValue.resourceId);

        if (appLabelStart >= 0) {
            message.setSpan(new ForegroundColorSpan(color), appLabelStart,
                    appLabelStart + appLabelLength, 0);
        }

        titlePref.setTitle(message);

        titlePref.setSelectable(false);
        titlePref.setLayoutResource(R.layout.wear_review_permission_title_pref);
    }

    private void addActionPreferencesToScreen(PreferenceScreen screen) {
        final Activity activity = getActivity();

        Preference cancelPref = new Preference(activity);
        cancelPref.setTitle(R.string.review_button_cancel);
        cancelPref.setOrder(ORDER_ACTION);
        cancelPref.setEnabled(true);
        cancelPref.setLayoutResource(R.layout.wear_review_permission_action_pref);
        cancelPref.setOnPreferenceClickListener(p -> {
            executeCallback(false);
            activity.setResult(Activity.RESULT_CANCELED);
            activity.finish();
            return true;
        });
        screen.addPreference(cancelPref);

        Preference continuePref = new Preference(activity);
        continuePref.setTitle(R.string.review_button_continue);
        continuePref.setOrder(ORDER_ACTION + 1);
        continuePref.setEnabled(true);
        continuePref.setLayoutResource(R.layout.wear_review_permission_action_pref);
        continuePref.setOnPreferenceClickListener(p -> {
            confirmPermissionsReview();
            executeCallback(true);
            activity.setResult(Activity.RESULT_OK);
            getActivity().finish();
            return true;
        });
        screen.addPreference(continuePref);
    }

    private void executeCallback(boolean success) {
        Activity activity = getActivity();
        if (activity == null) {
            return;
        }
        if (success) {
            IntentSender intent = activity.getIntent().getParcelableExtra(Intent.EXTRA_INTENT);
            if (intent != null) {
                try {
                    int flagMask = 0;
                    int flagValues = 0;
                    if (activity.getIntent().getBooleanExtra(
                            Intent.EXTRA_RESULT_NEEDED, false)) {
                        flagMask = Intent.FLAG_ACTIVITY_FORWARD_RESULT;
                        flagValues = Intent.FLAG_ACTIVITY_FORWARD_RESULT;
                    }
                    activity.startIntentSenderForResult(intent, -1, null,
                            flagMask, flagValues, 0);
                } catch (IntentSender.SendIntentException e) {
                    /* ignore */
                }
                return;
            }
        }
        RemoteCallback callback = activity.getIntent().getParcelableExtra(
                Intent.EXTRA_REMOTE_CALLBACK);
        if (callback != null) {
            Bundle result = new Bundle();
            result.putBoolean(Intent.EXTRA_RETURN_RESULT, success);
            callback.sendResult(result);
        }
    }

    /**
     * Extend the {@link SwitchPreference}:
     * <ul>
     *     <li>Monitor the changed state</li>
     * </ul>
     */
    private static class PermissionSwitchPreference extends SwitchPreference {
        private boolean mWasChanged = false;

        PermissionSwitchPreference(Context context) {
            super(context);
        }

        /**
         * Mark the permission as changed by the user
         */
        void setChanged() {
            mWasChanged = true;
        }

        /**
         * @return {@code true} iff the permission was changed by the user
         */
        boolean wasChanged() {
            return mWasChanged;
        }
    }
}
