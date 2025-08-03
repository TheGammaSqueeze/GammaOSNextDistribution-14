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
import static com.android.permissioncontroller.PermissionControllerStatsLog.APP_PERMISSIONS_FRAGMENT_VIEWED;
import static com.android.permissioncontroller.PermissionControllerStatsLog.APP_PERMISSIONS_FRAGMENT_VIEWED__CATEGORY__ALLOWED;
import static com.android.permissioncontroller.PermissionControllerStatsLog.APP_PERMISSIONS_FRAGMENT_VIEWED__CATEGORY__ALLOWED_FOREGROUND;
import static com.android.permissioncontroller.PermissionControllerStatsLog.APP_PERMISSIONS_FRAGMENT_VIEWED__CATEGORY__DENIED;
import static com.android.permissioncontroller.permission.ui.ManagePermissionsActivity.EXTRA_CALLER_NAME;

import static java.util.concurrent.TimeUnit.DAYS;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.UserHandle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceGroup;

import com.android.modules.utils.build.SdkLevel;
import com.android.permissioncontroller.PermissionControllerStatsLog;
import com.android.permissioncontroller.R;
import com.android.permissioncontroller.auto.AutoSettingsFrameFragment;
import com.android.permissioncontroller.permission.model.v31.AppPermissionUsage;
import com.android.permissioncontroller.permission.model.v31.PermissionUsages;
import com.android.permissioncontroller.permission.ui.Category;
import com.android.permissioncontroller.permission.ui.model.AppPermissionGroupsViewModel;
import com.android.permissioncontroller.permission.ui.model.AppPermissionGroupsViewModelFactory;
import com.android.permissioncontroller.permission.utils.KotlinUtils;
import com.android.permissioncontroller.permission.utils.StringUtils;

import java.text.Collator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Screen to show the permissions for a specific application. */
public class AutoAppPermissionsFragment extends AutoSettingsFrameFragment implements
        PermissionUsages.PermissionsUsagesChangeCallback {
    private static final String LOG_TAG = AutoAppPermissionsFragment.class.getSimpleName();

    private static final String IS_SYSTEM_PERMS_SCREEN = "_is_system_screen";
    private static final String KEY_ALLOWED_PERMISSIONS_GROUP = Category.ALLOWED.getCategoryName();
    private static final String KEY_DENIED_PERMISSIONS_GROUP = Category.DENIED.getCategoryName();

    private AppPermissionGroupsViewModel mViewModel;

    private String mPackageName;
    private boolean mIsFirstLoad;
    private UserHandle mUser;
    private PermissionUsages mPermissionUsages;
    private List<AppPermissionUsage> mAppPermissionUsages = new ArrayList<>();
    private boolean mIsSystemPermsScreen;

    private Collator mCollator;

    /**
     * @return A new fragment
     */
    public static AutoAppPermissionsFragment newInstance(@NonNull String packageName,
            @NonNull UserHandle userHandle, long sessionId, boolean isSystemPermsScreen) {
        return setPackageNameAndUserHandle(new AutoAppPermissionsFragment(), packageName,
                userHandle, sessionId, isSystemPermsScreen);
    }

    private static <T extends Fragment> T setPackageNameAndUserHandle(@NonNull T fragment,
            @NonNull String packageName, @NonNull UserHandle userHandle, long sessionId,
            boolean isSystemPermsScreen) {
        Bundle arguments = new Bundle();
        arguments.putString(Intent.EXTRA_PACKAGE_NAME, packageName);
        arguments.putParcelable(Intent.EXTRA_USER, userHandle);
        arguments.putLong(EXTRA_SESSION_ID, sessionId);
        arguments.putBoolean(IS_SYSTEM_PERMS_SCREEN, isSystemPermsScreen);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setLoading(true);

        mIsFirstLoad = true;
        mPackageName = getArguments().getString(Intent.EXTRA_PACKAGE_NAME);
        mUser = getArguments().getParcelable(Intent.EXTRA_USER);
        mIsSystemPermsScreen = getArguments().getBoolean(IS_SYSTEM_PERMS_SCREEN, true);
        UserHandle userHandle = getArguments().getParcelable(Intent.EXTRA_USER);
        Activity activity = requireActivity();
        PackageInfo packageInfo = AutoPermissionsUtils.getPackageInfo(activity, mPackageName,
                userHandle);
        if (packageInfo == null) {
            Toast.makeText(getContext(), R.string.app_not_found_dlg_title,
                    Toast.LENGTH_LONG).show();
            activity.finish();
            return;
        }

        mCollator = Collator.getInstance(
                getContext().getResources().getConfiguration().getLocales().get(0));
        AppPermissionGroupsViewModelFactory factory =
                new AppPermissionGroupsViewModelFactory(mPackageName, userHandle,
                        getArguments().getLong(EXTRA_SESSION_ID, 0));
        mViewModel = new ViewModelProvider(this, factory).get(AppPermissionGroupsViewModel.class);

        setHeaderLabel(getContext().getString(R.string.app_permissions));
        if (mIsSystemPermsScreen) {
            setAction(getContext().getString(R.string.all_permissions), v -> showAllPermissions());
        }
        createPreferenceCategories(packageInfo);

        mViewModel.getPackagePermGroupsLiveData().observe(this, this::updatePreferences);
        updatePreferences(mViewModel.getPackagePermGroupsLiveData().getValue());

        if (SdkLevel.isAtLeastS()) {
            mPermissionUsages = new PermissionUsages(getContext());

            long aggregateDataFilterBeginDays = KotlinUtils.INSTANCE.is7DayToggleEnabled()
                    ? AppPermissionGroupsViewModel.AGGREGATE_DATA_FILTER_BEGIN_DAYS_7 :
                    AppPermissionGroupsViewModel.AGGREGATE_DATA_FILTER_BEGIN_DAYS_1;

            long filterTimeBeginMillis = Math.max(System.currentTimeMillis()
                            - DAYS.toMillis(aggregateDataFilterBeginDays),
                    Instant.EPOCH.toEpochMilli());
            mPermissionUsages.load(null, null, filterTimeBeginMillis, Long.MAX_VALUE,
                    PermissionUsages.USAGE_FLAG_LAST, getActivity().getLoaderManager(),
                    false, false, this, false);
        }
    }

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
        setPreferenceScreen(getPreferenceManager().createPreferenceScreen(getContext()));
    }


    @Override
    @RequiresApi(Build.VERSION_CODES.S)
    public void onPermissionUsagesChanged() {
        if (mPermissionUsages.getUsages().isEmpty()) {
            return;
        }
        if (getContext() == null) {
            // Async result has come in after our context is gone.
            return;
        }

        mAppPermissionUsages = new ArrayList<>(mPermissionUsages.getUsages());
        updatePreferences(mViewModel.getPackagePermGroupsLiveData().getValue());
    }

    private void showAllPermissions() {
        Fragment frag = AutoAllAppPermissionsFragment.newInstance(
                getArguments().getString(Intent.EXTRA_PACKAGE_NAME),
                getArguments().getParcelable(Intent.EXTRA_USER),
                getArguments().getLong(EXTRA_SESSION_ID, INVALID_SESSION_ID));
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, frag)
                .addToBackStack("AllPerms")
                .commit();
    }

    protected void bindUi(PackageInfo packageInfo) {
        getPreferenceScreen().addPreference(
                AutoPermissionsUtils.createHeaderPreference(getContext(),
                        packageInfo.applicationInfo));

        PreferenceGroup allowed = new PreferenceCategory(getContext());
        allowed.setKey(KEY_ALLOWED_PERMISSIONS_GROUP);
        allowed.setTitle(R.string.allowed_header);
        getPreferenceScreen().addPreference(allowed);

        PreferenceGroup denied = new PreferenceCategory(getContext());
        denied.setKey(KEY_DENIED_PERMISSIONS_GROUP);
        denied.setTitle(R.string.denied_header);
        getPreferenceScreen().addPreference(denied);
    }

    private void createPreferenceCategories(PackageInfo packageInfo) {
        bindUi(packageInfo);
    }

    private void updatePreferences(@Nullable
            Map<Category, List<AppPermissionGroupsViewModel.GroupUiInfo>> groupMap) {
        if (groupMap == null && mViewModel.getPackagePermGroupsLiveData().isInitialized()) {
            // null because explicitly set to null
            Toast.makeText(
                    getActivity(), R.string.app_not_found_dlg_title, Toast.LENGTH_LONG).show();
            Log.w(LOG_TAG, "invalid package " + mPackageName);

            getActivity().finish();
            return;
        } else if (groupMap == null) {
            // null because uninitialized
            return;
        }

        Context context = getPreferenceManager().getContext();
        if (context == null) {
            return;
        }

        Map<String, Long> groupUsageLastAccessTime = new HashMap<>();
        mViewModel.extractGroupUsageLastAccessTime(groupUsageLastAccessTime, mAppPermissionUsages,
                mPackageName);

        for (Category grantCategory : groupMap.keySet()) {
            if (Category.ASK.equals(grantCategory)) {
                // skip ask category for auto
                continue;
            }
            PreferenceCategory category = getPreferenceScreen().findPreference(
                    grantCategory.getCategoryName());
            if (grantCategory.equals(Category.ALLOWED_FOREGROUND)) {
                category = findPreference(Category.ALLOWED.getCategoryName());
            }
            int numExtraPerms = 0;

            category.removeAll();


            for (AppPermissionGroupsViewModel.GroupUiInfo groupInfo : groupMap.get(grantCategory)) {
                if (groupInfo.isSystem() == mIsSystemPermsScreen) {
                    Preference preference = createPermissionPreference(getContext(), groupInfo,
                            groupUsageLastAccessTime);
                    category.addPreference(preference);
                } else if (!groupInfo.isSystem()) {
                    numExtraPerms++;
                }
            }


            if (numExtraPerms > 0) {
                setAdditionalPermissionsPreference(category, numExtraPerms, context);
            }

            if (category.getPreferenceCount() == 0) {
                setNoPermissionPreference(category, grantCategory, context);
            }

            KotlinUtils.INSTANCE.sortPreferenceGroup(category, this::comparePreferences, false);
        }


        if (mIsFirstLoad) {
            logAppPermissionsFragmentView();
            mIsFirstLoad = false;
        }
        setLoading(false);
    }

    private Preference createPermissionPreference(Context context,
            AppPermissionGroupsViewModel.GroupUiInfo groupInfo,
            Map<String, Long> groupUsageLastAccessTime) {
        String groupName = groupInfo.getGroupName();
        Preference preference = new Preference(context);
        preference.setTitle(KotlinUtils.INSTANCE.getPermGroupLabel(context, groupName));
        preference.setIcon(KotlinUtils.INSTANCE.getPermGroupIcon(context, groupName));
        preference.setKey(groupName);
        String summary = mViewModel.getPreferenceSummary(groupInfo, context,
                groupUsageLastAccessTime.get(groupName));
        if (!summary.isEmpty()) {
            preference.setSummary(summary);
        }
        preference.setOnPreferenceClickListener(pref -> {
            Intent intent = new Intent(Intent.ACTION_MANAGE_APP_PERMISSION);
            intent.putExtra(Intent.EXTRA_PACKAGE_NAME, mPackageName);
            intent.putExtra(Intent.EXTRA_PERMISSION_NAME, groupName);
            intent.putExtra(Intent.EXTRA_USER, mUser);
            intent.putExtra(EXTRA_CALLER_NAME, AutoAppPermissionsFragment.class.getName());
            context.startActivity(intent);
            return true;
        });
        return preference;
    }

    private void setAdditionalPermissionsPreference(PreferenceCategory category, int numExtraPerms,
            Context context) {
        Preference extraPerms = new Preference(context);
        extraPerms.setIcon(R.drawable.ic_toc);
        extraPerms.setTitle(R.string.additional_permissions);
        extraPerms.setOnPreferenceClickListener(preference -> {
            AutoAppPermissionsFragment
                    frag = AutoAppPermissionsFragment.newInstance(mPackageName, mUser,
                    getArguments().getLong(EXTRA_SESSION_ID), false);
            frag.setTargetFragment(AutoAppPermissionsFragment.this, 0);
            getFragmentManager().beginTransaction()
                    .replace(android.R.id.content, frag)
                    .addToBackStack(null)
                    .commit();
            return true;
        });
        extraPerms.setSummary(StringUtils.getIcuPluralsString(getContext(),
                R.string.additional_permissions_more, numExtraPerms));
        category.addPreference(extraPerms);
    }

    private void setNoPermissionPreference(PreferenceCategory category, Category grantCategory,
            Context context) {
        Preference empty = new Preference(context);
        empty.setKey(getString(grantCategory.equals(Category.DENIED)
                ? R.string.no_permissions_denied : R.string.no_permissions_allowed));
        empty.setTitle(empty.getKey());
        empty.setSelectable(false);
        category.addPreference(empty);
    }

    private int comparePreferences(Preference lhs, Preference rhs) {
        String additionalTitle = lhs.getContext().getString(R.string.additional_permissions);
        if (lhs.getTitle().equals(additionalTitle)) {
            return 1;
        } else if (rhs.getTitle().equals(additionalTitle)) {
            return -1;
        }
        return mCollator.compare(lhs.getTitle().toString(),
                rhs.getTitle().toString());
    }

    private void logAppPermissionsFragmentView() {
        Context context = getPreferenceManager().getContext();
        if (context == null) {
            return;
        }
        String permissionSubtitleOnlyInForeground =
                context.getString(R.string.permission_subtitle_only_in_foreground);


        long sessionId = getArguments().getLong(EXTRA_SESSION_ID, INVALID_SESSION_ID);
        long viewId = new Random().nextLong();

        PreferenceCategory allowed = findPreference(KEY_ALLOWED_PERMISSIONS_GROUP);

        int numAllowed = allowed.getPreferenceCount();
        for (int i = 0; i < numAllowed; i++) {
            Preference preference = allowed.getPreference(i);
            if (preference.getTitle().equals(getString(R.string.no_permissions_allowed))) {
                // R.string.no_permission_allowed was added to PreferenceCategory
                continue;
            }

            int category = APP_PERMISSIONS_FRAGMENT_VIEWED__CATEGORY__ALLOWED;
            if (preference.getSummary() != null
                    && permissionSubtitleOnlyInForeground.contentEquals(preference.getSummary())) {
                category = APP_PERMISSIONS_FRAGMENT_VIEWED__CATEGORY__ALLOWED_FOREGROUND;
            }

            logAppPermissionsFragmentViewEntry(sessionId, viewId, preference.getKey(), category);
        }

        PreferenceCategory denied = findPreference(KEY_DENIED_PERMISSIONS_GROUP);

        int numDenied = denied.getPreferenceCount();
        for (int i = 0; i < numDenied; i++) {
            Preference preference = denied.getPreference(i);
            if (preference.getTitle().equals(getString(R.string.no_permissions_denied))) {
                // R.string.no_permission_denied was added to PreferenceCategory
                continue;
            }
            logAppPermissionsFragmentViewEntry(sessionId, viewId, preference.getKey(),
                    APP_PERMISSIONS_FRAGMENT_VIEWED__CATEGORY__DENIED);
        }
    }

    private void logAppPermissionsFragmentViewEntry(
            long sessionId, long viewId, String permissionGroupName, int category) {
        Integer uid = KotlinUtils.INSTANCE.getPackageUid(getActivity().getApplication(),
                mPackageName, mUser);
        if (uid == null) {
            return;
        }
        PermissionControllerStatsLog.write(APP_PERMISSIONS_FRAGMENT_VIEWED, sessionId, viewId,
                permissionGroupName, uid, mPackageName, category);
        Log.i(LOG_TAG, "AutoAppPermissionFragment view logged with sessionId=" + sessionId
                + " viewId=" + viewId + " permissionGroupName=" + permissionGroupName + " uid="
                + uid + " packageName="
                + mPackageName + " category=" + category);
    }
}

