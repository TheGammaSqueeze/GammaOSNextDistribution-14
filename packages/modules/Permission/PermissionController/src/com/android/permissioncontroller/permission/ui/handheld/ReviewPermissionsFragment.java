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

package com.android.permissioncontroller.permission.ui.handheld;

import static android.content.pm.PackageManager.FLAG_PERMISSION_REVIEW_REQUIRED;
import static android.content.pm.PackageManager.FLAG_PERMISSION_USER_SET;

import static com.android.permissioncontroller.PermissionControllerStatsLog.REVIEW_PERMISSIONS_FRAGMENT_RESULT_REPORTED;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.RemoteCallback;
import android.os.UserHandle;
import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceScreen;

import com.android.permissioncontroller.PermissionControllerStatsLog;
import com.android.permissioncontroller.R;
import com.android.permissioncontroller.permission.model.livedatatypes.LightAppPermGroup;
import com.android.permissioncontroller.permission.model.livedatatypes.LightPermission;
import com.android.permissioncontroller.permission.ui.ManagePermissionsActivity;
import com.android.permissioncontroller.permission.ui.model.ReviewPermissionViewModelFactory;
import com.android.permissioncontroller.permission.ui.model.ReviewPermissionsViewModel;
import com.android.permissioncontroller.permission.ui.model.ReviewPermissionsViewModel.PermissionTarget;
import com.android.permissioncontroller.permission.utils.KotlinUtils;
import com.android.permissioncontroller.permission.utils.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * If an app does not support runtime permissions the user is prompted via this fragment to select
 * which permissions to grant to the app before first use and if an update changed the permissions.
 */
public final class ReviewPermissionsFragment extends PreferenceFragmentCompat
        implements View.OnClickListener, PermissionPreference.PermissionPreferenceChangeListener,
        PermissionPreference.PermissionPreferenceOwnerFragment {

    private static final String EXTRA_PACKAGE_INFO =
            "com.android.permissioncontroller.permission.ui.extra.PACKAGE_INFO";
    private static final String LOG_TAG = ReviewPermissionsFragment.class.getSimpleName();

    private ReviewPermissionsViewModel mViewModel;
    private View mView;
    private Button mContinueButton;
    private Button mCancelButton;
    private Button mMoreInfoButton;
    private PreferenceCategory mNewPermissionsCategory;
    private PreferenceCategory mCurrentPermissionsCategory;

    private boolean mHasConfirmedRevoke;

    /**
     * Creates bundle arguments for the navigation graph
     * @param packageInfo packageInfo added to the bundle
     * @return the bundle
     */
    public static Bundle getArgs(PackageInfo packageInfo) {
        Bundle arguments = new Bundle();
        arguments.putParcelable(EXTRA_PACKAGE_INFO, packageInfo);
        return arguments;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        PackageInfo packageInfo = getArguments().getParcelable(EXTRA_PACKAGE_INFO);
        if (packageInfo == null) {
            activity.finishAfterTransition();
            return;
        }

        ReviewPermissionViewModelFactory factory = new ReviewPermissionViewModelFactory(
                getActivity().getApplication(), packageInfo);
        mViewModel = new ViewModelProvider(this, factory).get(ReviewPermissionsViewModel.class);
        mViewModel.getPermissionGroupsLiveData().observe(this,
                (Map<String, LightAppPermGroup> permGroupsMap) -> {
                    if (getActivity().isFinishing()) {
                        return;
                    }
                    if (permGroupsMap.isEmpty()) {
                        //If the system called for a review but no groups are found, this means
                        // that all groups are restricted. Hence there is nothing to review
                        // and instantly continue.
                        confirmPermissionsReview();
                        executeCallback(true);
                        activity.finishAfterTransition();
                    } else {
                        bindUi(permGroupsMap);
                        loadPreferences(permGroupsMap);
                    }
                });
    }

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
        // empty
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        mView = inflater.inflate(R.layout.review_permissions, container, false);
        ViewGroup preferenceRootView = mView.requireViewById(R.id.preferences_frame);
        View prefsContainer = super.onCreateView(inflater, preferenceRootView, savedInstanceState);
        preferenceRootView.addView(prefsContainer);
        return mView;
    }

    @Override
    public void onClick(View view) {
        Activity activity = getActivity();
        if (activity == null) {
            return;
        }
        if (view == mContinueButton) {
            confirmPermissionsReview();
            executeCallback(true);
        } else if (view == mCancelButton) {
            executeCallback(false);
            activity.setResult(Activity.RESULT_CANCELED);
        } else if (view == mMoreInfoButton) {
            Intent intent = new Intent(Intent.ACTION_MANAGE_APP_PERMISSIONS);
            intent.putExtra(Intent.EXTRA_PACKAGE_NAME,
                    mViewModel.getPackageInfo().packageName);
            intent.putExtra(Intent.EXTRA_USER, UserHandle.getUserHandleForUid(
                    mViewModel.getPackageInfo().applicationInfo.uid));
            intent.putExtra(ManagePermissionsActivity.EXTRA_ALL_PERMISSIONS, true);
            getActivity().startActivity(intent);
        }
        activity.finishAfterTransition();
    }

    private void confirmPermissionsReview() {
        final List<PreferenceGroup> preferenceGroups = new ArrayList<>();
        if (mNewPermissionsCategory != null) {
            preferenceGroups.add(mNewPermissionsCategory);
            preferenceGroups.add(mCurrentPermissionsCategory);
        } else {
            PreferenceScreen preferenceScreen = getPreferenceScreen();
            if (preferenceScreen != null) {
                preferenceGroups.add(preferenceScreen);
            }
        }

        final int preferenceGroupCount = preferenceGroups.size();
        long changeIdForLogging = new Random().nextLong();
        Application app = getActivity().getApplication();
        for (int groupNum = 0; groupNum < preferenceGroupCount; groupNum++) {
            final PreferenceGroup preferenceGroup = preferenceGroups.get(groupNum);

            final int preferenceCount = preferenceGroup.getPreferenceCount();
            for (int prefNum = 0; prefNum < preferenceCount; prefNum++) {
                Preference preference = preferenceGroup.getPreference(prefNum);
                if (preference instanceof PermissionReviewPreference) {
                    PermissionReviewPreference permPreference =
                            (PermissionReviewPreference) preference;
                    LightAppPermGroup group = permPreference.getGroup();


                    if (permPreference.getState().and(
                            PermissionTarget.PERMISSION_FOREGROUND)
                            != PermissionTarget.PERMISSION_NONE.getValue()) {
                        KotlinUtils.INSTANCE.grantForegroundRuntimePermissions(app, group);
                    }
                    if (permPreference.getState().and(
                            PermissionTarget.PERMISSION_BACKGROUND)
                            != PermissionTarget.PERMISSION_NONE.getValue()) {
                        KotlinUtils.INSTANCE.grantBackgroundRuntimePermissions(app, group);
                    }
                    if (permPreference.getState() == PermissionTarget.PERMISSION_NONE) {
                        KotlinUtils.INSTANCE.revokeForegroundRuntimePermissions(app, group);
                        KotlinUtils.INSTANCE.revokeBackgroundRuntimePermissions(app, group);
                    }
                    logReviewPermissionsFragmentResult(changeIdForLogging, group);
                }
            }
        }

        // Some permission might be restricted and hence there is no AppPermissionGroup for it.
        // Manually unset all review-required flags, regardless of restriction.
        PackageManager pm = getContext().getPackageManager();
        PackageInfo pkg = mViewModel.getPackageInfo();
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
                Log.e(LOG_TAG, "Cannot unmark " + perm + " requested by " + pkg.packageName
                        + " as review required", e);
            }
        }
    }

    private void logReviewPermissionsFragmentResult(long changeId, LightAppPermGroup group) {
        ArrayList<LightPermission> permissions = new ArrayList<>(
                group.getAllPermissions().values());

        int numPermissions = permissions.size();
        for (int i = 0; i < numPermissions; i++) {
            LightPermission permission = permissions.get(i);

            PermissionControllerStatsLog.write(REVIEW_PERMISSIONS_FRAGMENT_RESULT_REPORTED,
                    changeId, mViewModel.getPackageInfo().applicationInfo.uid,
                    group.getPackageName(),
                    permission.getName(), permission.isGranted());
            Log.i(LOG_TAG, "Permission grant via permission review changeId=" + changeId + " uid="
                    + mViewModel.getPackageInfo().applicationInfo.uid + " packageName="
                    + group.getPackageName() + " permission="
                    + permission.getName() + " granted=" + permission.isGranted());
        }
    }

    private void bindUi(Map<String, LightAppPermGroup> permGroupsMap) {
        Activity activity = getActivity();
        if (activity == null || !mViewModel.isInitialized()) {
            return;
        }

        Drawable icon = mViewModel.getPackageInfo().applicationInfo.loadIcon(
                    getContext().getPackageManager());
        ImageView iconView = mView.requireViewById(R.id.app_icon);
        iconView.setImageDrawable(icon);

        // Set message
        final int labelTemplateResId = mViewModel.isPackageUpdated()
                ? R.string.permission_review_title_template_update
                : R.string.permission_review_title_template_install;
        Spanned message = Html.fromHtml(getString(labelTemplateResId,
                Utils.getAppLabel(mViewModel.getPackageInfo().applicationInfo,
                        getActivity().getApplication())), 0);
        // Set the permission message as the title so it can be announced.
        activity.setTitle(message.toString());

        // Color the app name.
        TextView permissionsMessageView = mView.requireViewById(
                R.id.permissions_message);
        permissionsMessageView.setText(message);

        mContinueButton = mView.requireViewById(R.id.continue_button);
        mContinueButton.setOnClickListener(this);

        mCancelButton = mView.requireViewById(R.id.cancel_button);
        mCancelButton.setOnClickListener(this);

        if (activity.getPackageManager().arePermissionsIndividuallyControlled()) {
            mMoreInfoButton = mView.requireViewById(
                    R.id.permission_more_info_button);
            mMoreInfoButton.setOnClickListener(this);
            mMoreInfoButton.setVisibility(View.VISIBLE);
        }
    }

    private PermissionReviewPreference getPreference(String key) {
        if (mNewPermissionsCategory != null) {
            PermissionReviewPreference pref =
                    mNewPermissionsCategory.findPreference(key);

            if (pref == null && mCurrentPermissionsCategory != null) {
                return mCurrentPermissionsCategory.findPreference(key);
            } else {
                return pref;
            }
        } else {
            return getPreferenceScreen().findPreference(key);
        }
    }

    private void loadPreferences(Map<String, LightAppPermGroup> permGroupsMap) {
        Activity activity = getActivity();
        if (activity == null || !mViewModel.isInitialized()) {
            return;
        }

        PreferenceScreen screen = getPreferenceScreen();
        if (screen == null) {
            screen = getPreferenceManager().createPreferenceScreen(getContext());
            setPreferenceScreen(screen);
        } else {
            screen.removeAll();
        }

        mCurrentPermissionsCategory = null;
        mNewPermissionsCategory = null;

        final boolean isPackageUpdated = mViewModel.isPackageUpdated();

        for (LightAppPermGroup group : permGroupsMap.values()) {
            PermissionReviewPreference preference = getPreference(group.getPermGroupName());
            if (preference == null) {
                preference = new PermissionReviewPreference(this,
                        group, this, mViewModel);
                preference.setKey(group.getPermGroupName());
                Drawable icon = KotlinUtils.INSTANCE.getPermGroupIcon(getContext(),
                        group.getPermGroupName());
                preference.setIcon(icon);
                preference.setTitle(KotlinUtils.INSTANCE.getPermGroupLabel(getContext(),
                        group.getPermGroupName()));
            } else {
                preference.updateUi();
            }

            if (group.isReviewRequired()) {
                if (!isPackageUpdated) {
                    screen.addPreference(preference);
                } else {
                    if (mNewPermissionsCategory == null) {
                        mNewPermissionsCategory = new PreferenceCategory(activity);
                        mNewPermissionsCategory.setTitle(R.string.new_permissions_category);
                        mNewPermissionsCategory.setOrder(1);
                        screen.addPreference(mNewPermissionsCategory);
                    }
                    mNewPermissionsCategory.addPreference(preference);
                }
            } else {
                if (mCurrentPermissionsCategory == null) {
                    mCurrentPermissionsCategory = new PreferenceCategory(activity);
                    mCurrentPermissionsCategory.setTitle(R.string.current_permissions_category);
                    mCurrentPermissionsCategory.setOrder(2);
                    screen.addPreference(mCurrentPermissionsCategory);
                }
                mCurrentPermissionsCategory.addPreference(preference);
            }
        }
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

    @Override
    public boolean shouldConfirmDefaultPermissionRevoke() {
        return !mHasConfirmedRevoke;
    }

    @Override
    public void hasConfirmDefaultPermissionRevoke() {
        mHasConfirmedRevoke = true;
    }

    @Override
    public void onPreferenceChanged(String key) {
        getPreference(key).setChanged();
    }

    @Override
    public void onDenyAnyWay(String key, PermissionTarget changeTarget) {
        getPreference(key).onDenyAnyWay(changeTarget);
    }

    @Override
    public void onBackgroundAccessChosen(String key, int chosenItem) {
        getPreference(key).onBackgroundAccessChosen(chosenItem);
    }

    /**
     * Extend the {@link PermissionPreference}:
     * <ul>
     *     <li>Show the description of the permission group</li>
     *     <li>Show the permission group as granted if the user has not toggled it yet. This means
     *     that if the user does not touch the preference, we will later grant the permission
     *     in {@link #confirmPermissionsReview()}.</li>
     * </ul>
     */
    private static class PermissionReviewPreference extends PermissionPreference {
        private final LightAppPermGroup mGroup;
        private final Context mContext;
        private boolean mWasChanged;

        PermissionReviewPreference(PreferenceFragmentCompat fragment, LightAppPermGroup group,
                PermissionPreferenceChangeListener callbacks,
                ReviewPermissionsViewModel reviewPermissionsViewModel) {
            super(fragment, group, callbacks, reviewPermissionsViewModel);
            mGroup = group;
            mContext = fragment.getContext();
            updateUi();
        }

        LightAppPermGroup getGroup() {
            return mGroup;
        }

        /**
         * Mark the permission as changed by the user
         */
        void setChanged() {
            mWasChanged = true;
            updateUi();
        }

        @Override
        void updateUi() {
            // updateUi might be called in super-constructor before group is initialized
            if (mGroup == null) {
                return;
            }

            super.updateUi();

            if (isEnabled()) {
                if (mGroup.isReviewRequired() && !mWasChanged) {
                    setSummary(KotlinUtils.INSTANCE.getPermGroupDescription(mContext,
                            mGroup.getPermGroupName()));
                    setCheckedOverride(true);
                } else if (TextUtils.isEmpty(getSummary())) {
                    // Sometimes the summary is already used, e.g. when this for a
                    // foreground/background group. In this case show leave the original summary.
                    setSummary(KotlinUtils.INSTANCE.getPermGroupDescription(mContext,
                            mGroup.getPermGroupName()));
                }
            }
        }
    }
}
