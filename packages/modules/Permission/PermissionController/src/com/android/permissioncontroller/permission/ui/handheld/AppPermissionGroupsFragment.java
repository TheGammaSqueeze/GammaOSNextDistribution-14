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

package com.android.permissioncontroller.permission.ui.handheld;

import static com.android.permissioncontroller.Constants.EXTRA_SESSION_ID;
import static com.android.permissioncontroller.Constants.INVALID_SESSION_ID;
import static com.android.permissioncontroller.PermissionControllerStatsLog.APP_PERMISSIONS_FRAGMENT_VIEWED;
import static com.android.permissioncontroller.PermissionControllerStatsLog.APP_PERMISSIONS_FRAGMENT_VIEWED__CATEGORY__ALLOWED;
import static com.android.permissioncontroller.PermissionControllerStatsLog.APP_PERMISSIONS_FRAGMENT_VIEWED__CATEGORY__ALLOWED_FOREGROUND;
import static com.android.permissioncontroller.PermissionControllerStatsLog.APP_PERMISSIONS_FRAGMENT_VIEWED__CATEGORY__DENIED;
import static com.android.permissioncontroller.hibernation.HibernationPolicyKt.isHibernationEnabled;
import static com.android.permissioncontroller.permission.ui.Category.STORAGE_FOOTER;
import static com.android.permissioncontroller.permission.ui.handheld.UtilsKt.pressBack;

import static java.util.concurrent.TimeUnit.DAYS;

import android.app.ActionBar;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.icu.text.ListFormatter;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.annotation.StringRes;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;

import com.android.modules.utils.build.SdkLevel;
import com.android.permission.flags.Flags;
import com.android.permissioncontroller.PermissionControllerStatsLog;
import com.android.permissioncontroller.R;
import com.android.permissioncontroller.permission.model.livedatatypes.HibernationSettingState;
import com.android.permissioncontroller.permission.model.v31.AppPermissionUsage;
import com.android.permissioncontroller.permission.model.v31.PermissionUsages;
import com.android.permissioncontroller.permission.ui.Category;
import com.android.permissioncontroller.permission.ui.model.AppPermissionGroupsViewModel;
import com.android.permissioncontroller.permission.ui.model.AppPermissionGroupsViewModel.GroupUiInfo;
import com.android.permissioncontroller.permission.ui.model.AppPermissionGroupsViewModelFactory;
import com.android.permissioncontroller.permission.utils.KotlinUtils;
import com.android.permissioncontroller.permission.utils.StringUtils;
import com.android.permissioncontroller.permission.utils.Utils;
import com.android.settingslib.HelpUtils;
import com.android.settingslib.widget.FooterPreference;

import java.text.Collator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

/**
 * Show and manage permission groups for an app.
 *
 * <p>Shows the list of permission groups the app has requested at one permission for.
 */
public final class AppPermissionGroupsFragment extends SettingsWithLargeHeader implements
        PermissionUsages.PermissionsUsagesChangeCallback {

    private static final String LOG_TAG = AppPermissionGroupsFragment.class.getSimpleName();
    private static final String IS_SYSTEM_PERMS_SCREEN = "_is_system_screen";
    private static final String AUTO_REVOKE_CATEGORY_KEY = "_AUTO_REVOKE_KEY";
    private static final String AUTO_REVOKE_SWITCH_KEY = "_AUTO_REVOKE_SWITCH_KEY";
    private static final String AUTO_REVOKE_SUMMARY_KEY = "_AUTO_REVOKE_SUMMARY_KEY";
    private static final String ASSISTANT_MIC_CATEGORY_KEY = "_ASSISTANT_MIC_KEY";
    private static final String ASSISTANT_MIC_SWITCH_KEY = "_ASSISTANT_MIC_SWITCH_KEY";
    private static final String ASSISTANT_MIC_SUMMARY_KEY = "_ASSISTANT_MIC_SUMMARY_KEY";

    static final String EXTRA_HIDE_INFO_BUTTON = "hideInfoButton";

    private AppPermissionGroupsViewModel mViewModel;
    private boolean mIsSystemPermsScreen;
    private boolean mIsFirstLoad;
    private String mPackageName;
    private UserHandle mUser;
    private PermissionUsages mPermissionUsages;
    private List<AppPermissionUsage> mAppPermissionUsages = new ArrayList<>();

    private Collator mCollator;

    /**
     * Create a bundle with the arguments needed by this fragment
     *
     * @param packageName The name of the package
     * @param userHandle The user of this package
     * @param sessionId The current session ID
     * @param isSystemPermsScreen Whether or not this screen is the system permission screen, or
     * the extra permissions screen
     *
     * @return A bundle with all of the args placed
     */
    public static Bundle createArgs(@NonNull String packageName, @NonNull UserHandle userHandle,
            long sessionId, boolean isSystemPermsScreen) {
        Bundle arguments = new Bundle();
        arguments.putString(Intent.EXTRA_PACKAGE_NAME, packageName);
        arguments.putParcelable(Intent.EXTRA_USER, userHandle);
        arguments.putLong(EXTRA_SESSION_ID, sessionId);
        arguments.putBoolean(IS_SYSTEM_PERMS_SCREEN, isSystemPermsScreen);
        return arguments;
    }

    /**
     * Create a bundle for a system permissions fragment
     *
     * @param packageName The name of the package
     * @param userHandle The user of this package
     * @param sessionId The current session ID
     *
     * @return A bundle with all of the args placed
     */
    public static Bundle createArgs(@NonNull String packageName, @NonNull UserHandle userHandle,
            long sessionId) {
        return createArgs(packageName, userHandle, sessionId, true);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        mIsFirstLoad = true;
        final ActionBar ab = getActivity().getActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
        }

        mPackageName = getArguments().getString(Intent.EXTRA_PACKAGE_NAME);
        mUser = getArguments().getParcelable(Intent.EXTRA_USER);
        mIsSystemPermsScreen = getArguments().getBoolean(IS_SYSTEM_PERMS_SCREEN, true);

        AppPermissionGroupsViewModelFactory factory =
                new AppPermissionGroupsViewModelFactory(mPackageName, mUser,
                        getArguments().getLong(EXTRA_SESSION_ID, 0));

        mViewModel = new ViewModelProvider(this, factory).get(AppPermissionGroupsViewModel.class);
        mViewModel.getPackagePermGroupsLiveData().observe(this, this::updatePreferences);
        mViewModel.getAutoRevokeLiveData().observe(this, this::setAutoRevokeToggleState);

        mCollator = Collator.getInstance(
                getContext().getResources().getConfiguration().getLocales().get(0));

        // If the build type is below S, the app ops for permission usage can't be found. Thus, we
        // shouldn't load permission usages, for them.
        if (SdkLevel.isAtLeastS()) {
            Context context = getPreferenceManager().getContext();
            mPermissionUsages = new PermissionUsages(context);

            long aggregateDataFilterBeginDays = KotlinUtils.INSTANCE.is7DayToggleEnabled()
                    ? AppPermissionGroupsViewModel.AGGREGATE_DATA_FILTER_BEGIN_DAYS_7 :
                    AppPermissionGroupsViewModel.AGGREGATE_DATA_FILTER_BEGIN_DAYS_1;

            long filterTimeBeginMillis = Math.max(System.currentTimeMillis()
                            - DAYS.toMillis(aggregateDataFilterBeginDays),
                    Instant.EPOCH.toEpochMilli());
            mPermissionUsages.load(null, null, filterTimeBeginMillis, Long.MAX_VALUE,
                    PermissionUsages.USAGE_FLAG_LAST, getActivity().getLoaderManager(),
                    false, false, this, false);
            // TODO 206455664: remove once issue is identified
            new Handler(Looper.getMainLooper()).postDelayed(this::printState, 3000);
        }

        updatePreferences(mViewModel.getPackagePermGroupsLiveData().getValue());

    }

    @RequiresApi(Build.VERSION_CODES.S)
    private void printState() {
        int numPrefs =
                getPreferenceScreen() != null ? getPreferenceScreen().getPreferenceCount() : -1;
        if (numPrefs > 0) {
            return;
        }

        Log.i(LOG_TAG, "number of prefs: " + numPrefs);
        Log.i(LOG_TAG, "Has created screen: " + (getPreferenceScreen() != null));
        Log.i(LOG_TAG, "Has usages: " + (!mPermissionUsages.getUsages().isEmpty()));
        mViewModel.logLiveDataState();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        getActivity().setTitle(R.string.app_permissions);
        return super.onCreateView(inflater, container, savedInstanceState);
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

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home: {
                pressBack(this);
                return true;
            }

            case MENU_ALL_PERMS: {
                mViewModel.showAllPermissions(this, AllAppPermissionsFragment.createArgs(
                        mPackageName, mUser));
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        if (mIsSystemPermsScreen) {
            menu.add(Menu.NONE, MENU_ALL_PERMS, Menu.NONE, R.string.all_permissions);
            if (!SdkLevel.isAtLeastS()) {
                HelpUtils.prepareHelpMenuItem(getActivity(), menu, R.string.help_app_permissions,
                        getClass().getName());
            }
        }
    }

    private static void bindUi(SettingsWithLargeHeader fragment, String packageName,
            UserHandle user) {
        Activity activity = fragment.getActivity();
        Intent infoIntent = null;
        if (!activity.getIntent().getBooleanExtra(EXTRA_HIDE_INFO_BUTTON, false)) {
            infoIntent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", packageName, null));
        }

        Drawable icon = KotlinUtils.INSTANCE.getBadgedPackageIcon(activity.getApplication(),
                packageName, user);
        fragment.setHeader(icon, KotlinUtils.INSTANCE.getPackageLabel(activity.getApplication(),
                packageName, user), infoIntent, user, false);

    }

    private void createPreferenceScreenIfNeeded() {
        if (getPreferenceScreen() == null) {
            addPreferencesFromResource(R.xml.allowed_denied);
            addAutoRevokePreferences(getPreferenceScreen());
            bindUi(this, mPackageName, mUser);
        }
    }

    private void updatePreferences(Map<Category, List<GroupUiInfo>> groupMap) {
        if (groupMap == null && !mViewModel.getPackagePermGroupsLiveData().isStale()) {
            // null because explicitly set to null
            Toast.makeText(
                    getActivity(), R.string.app_not_found_dlg_title, Toast.LENGTH_LONG).show();
            Log.w(LOG_TAG, "invalid package " + mPackageName);

            pressBack(this);

            return;
        } else if (groupMap == null) {
            // null because uninitialized
            return;
        }

        createPreferenceScreenIfNeeded();

        Context context = getPreferenceManager().getContext();
        if (context == null) {
            return;
        }


        Map<String, Long> groupUsageLastAccessTime = new HashMap<>();
        mViewModel.extractGroupUsageLastAccessTime(groupUsageLastAccessTime, mAppPermissionUsages,
                mPackageName);

        findPreference(Category.ALLOWED_FOREGROUND.getCategoryName()).setVisible(false);

        // Hide storage footer category
        findPreference(STORAGE_FOOTER.getCategoryName()).setVisible(false);

        long sessionId = getArguments().getLong(EXTRA_SESSION_ID, INVALID_SESSION_ID);

        for (Category grantCategory : groupMap.keySet()) {
            PreferenceCategory category = findPreference(grantCategory.getCategoryName());
            int numExtraPerms = 0;

            category.removeAll();

            if (grantCategory.equals(Category.ALLOWED_FOREGROUND)) {
                category.setVisible(false);
                category = findPreference(Category.ALLOWED.getCategoryName());
            }

            if (grantCategory.equals(Category.ASK)) {
                if (groupMap.get(grantCategory).size() == 0) {
                    category.setVisible(false);
                } else {
                    category.setVisible(true);
                }
            }

            for (GroupUiInfo groupInfo : groupMap.get(grantCategory)) {
                String groupName = groupInfo.getGroupName();

                PermissionControlPreference preference = new PermissionControlPreference(context,
                        mPackageName, groupName, mUser, AppPermissionGroupsFragment.class.getName(),
                        sessionId, grantCategory.getCategoryName(), true);
                preference.setTitle(KotlinUtils.INSTANCE.getPermGroupLabel(context, groupName));
                preference.setIcon(KotlinUtils.INSTANCE.getPermGroupIcon(context, groupName));
                preference.setKey(groupName);
                String summary = mViewModel.getPreferenceSummary(groupInfo, context,
                        groupUsageLastAccessTime.get(groupName));
                if (!summary.isEmpty()) {
                    preference.setSummary(summary);
                }
                // Add an info icon if the package handles ACTION_VIEW_PERMISSION_USAGE.
                PackageManager packageManager = requireActivity().getPackageManager();
                Intent viewUsageIntent = new Intent()
                        .setPackage(mPackageName)
                        .setAction(Intent.ACTION_VIEW_PERMISSION_USAGE)
                        .putExtra(Intent.EXTRA_PERMISSION_GROUP_NAME, groupName);
                ResolveInfo resolveInfo = packageManager.resolveActivity(viewUsageIntent,
                        PackageManager.MATCH_INSTANT);
                if (resolveInfo != null && resolveInfo.activityInfo != null && Objects.equals(
                        resolveInfo.activityInfo.permission,
                        android.Manifest.permission.START_VIEW_PERMISSION_USAGE)) {
                    // Make the intent explicit to not require CATEGORY_DEFAULT.
                    viewUsageIntent.setComponent(new ComponentName(
                            resolveInfo.activityInfo.packageName, resolveInfo.activityInfo.name));
                    preference.setRightIcon(
                            context.getDrawable(R.drawable.ic_info_outline),
                            context.getString(R.string.learn_more_content_description,
                                    KotlinUtils.INSTANCE.getPermGroupLabel(context, groupName)),
                            v -> {
                                try {
                                    startActivity(viewUsageIntent);
                                } catch (ActivityNotFoundException e) {
                                    Log.e(LOG_TAG, "No activity found for viewing permission "
                                            + "usage.");
                                }
                            });
                }
                if (groupInfo.isSystem() == mIsSystemPermsScreen) {
                    category.addPreference(preference);
                } else if (!groupInfo.isSystem()) {
                    numExtraPerms++;
                }
            }

            int noPermsStringRes = grantCategory.equals(Category.DENIED)
                    ? R.string.no_permissions_denied : R.string.no_permissions_allowed;

            if (numExtraPerms > 0) {
                final Preference extraPerms = setUpCustomPermissionsScreen(context, numExtraPerms,
                        grantCategory.getCategoryName());
                category.addPreference(extraPerms);
            }

            if (category.getPreferenceCount() == 0) {
                setNoPermissionPreference(category, noPermsStringRes, context);
            }

            KotlinUtils.INSTANCE.sortPreferenceGroup(category, this::comparePreferences, false);
        }

        setAutoRevokeToggleState(mViewModel.getAutoRevokeLiveData().getValue());

        if (mIsFirstLoad) {
            logAppPermissionGroupsFragmentView();
            mIsFirstLoad = false;
        }
    }

    private void addAutoRevokePreferences(PreferenceScreen screen) {
        Context context = screen.getPreferenceManager().getContext();

        PreferenceCategory autoRevokeCategory = new PreferenceCategory(context);
        autoRevokeCategory.setKey(AUTO_REVOKE_CATEGORY_KEY);
        screen.addPreference(autoRevokeCategory);

        SwitchPreference autoRevokeSwitch = new SwitchPreference(context);
        autoRevokeSwitch.setOnPreferenceClickListener((preference) -> {
            mViewModel.setAutoRevoke(autoRevokeSwitch.isChecked());
            return true;
        });

        int switchTitleId;
        if (isHibernationEnabled()) {
            if (SdkLevel.isAtLeastT()) {
                switchTitleId = isArchivingEnabled() ? R.string.unused_apps_label_v3
                        : R.string.unused_apps_label_v2;
                autoRevokeSwitch.setSummary(isArchivingEnabled() ? R.string.unused_apps_summary_v2
                        : R.string.unused_apps_summary);
            } else {
                switchTitleId = R.string.unused_apps_label;
            }
        } else {
            switchTitleId = R.string.auto_revoke_label;
        }
        autoRevokeSwitch.setTitle(switchTitleId);
        autoRevokeSwitch.setKey(AUTO_REVOKE_SWITCH_KEY);
        autoRevokeCategory.addPreference(autoRevokeSwitch);

        Preference autoRevokeSummary = SdkLevel.isAtLeastS() ? new FooterPreference(context)
                : new Preference(context);
        autoRevokeSummary.setIcon(Utils.applyTint(getActivity(), R.drawable.ic_info_outline,
                android.R.attr.colorControlNormal));
        autoRevokeSummary.setKey(AUTO_REVOKE_SUMMARY_KEY);
        if (isHibernationEnabled()) {
            autoRevokeCategory.setTitle(
                    SdkLevel.isAtLeastT() ? R.string.unused_apps_category_title
                            : R.string.unused_apps);
        }
        autoRevokeCategory.addPreference(autoRevokeSummary);
    }

    private boolean isArchivingEnabled() {
        return SdkLevel.isAtLeastV() && Flags.archivingReadOnly();
    }

    private void setAutoRevokeToggleState(HibernationSettingState state) {
        if (state == null || !mViewModel.getPackagePermGroupsLiveData().isInitialized()
                || getPreferenceScreen() == null || getListView() == null || getView() == null) {
            return;
        }

        PreferenceCategory autoRevokeCategory = getPreferenceScreen()
                .findPreference(AUTO_REVOKE_CATEGORY_KEY);
        SwitchPreference autoRevokeSwitch = autoRevokeCategory.findPreference(
                AUTO_REVOKE_SWITCH_KEY);
        Preference autoRevokeSummary = autoRevokeCategory.findPreference(
                AUTO_REVOKE_SUMMARY_KEY);

        autoRevokeSwitch.setChecked(state.isEligibleForHibernation());
        autoRevokeSwitch.setEnabled(!state.isExemptBySystem());

        List<String> groupLabels = new ArrayList<>();
        for (String groupName : state.getRevocableGroupNames()) {
            PreferenceCategory category = getPreferenceScreen().findPreference(
                    Category.ALLOWED.getCategoryName());
            Preference pref = category.findPreference(groupName);
            if (pref != null) {
                groupLabels.add(pref.getTitle().toString());
            }
        }

        groupLabels.sort(mCollator);
        if (groupLabels.isEmpty()) {
            autoRevokeSummary.setSummary(R.string.auto_revoke_summary);
        } else {
            autoRevokeSummary.setSummary(getString(R.string.auto_revoke_summary_with_permissions,
                    ListFormatter.getInstance().format(groupLabels)));
        }
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

    private Preference setUpCustomPermissionsScreen(Context context, int count, String category) {
        final Preference extraPerms = new Preference(context);
        extraPerms.setIcon(Utils.applyTint(getActivity(), R.drawable.ic_toc,
                android.R.attr.colorControlNormal));
        extraPerms.setTitle(R.string.additional_permissions);
        extraPerms.setKey(extraPerms.getTitle() + category);
        extraPerms.setOnPreferenceClickListener(preference -> {
            mViewModel.showExtraPerms(this, AppPermissionGroupsFragment.createArgs(
                    mPackageName, mUser, getArguments().getLong(EXTRA_SESSION_ID), false));
            return true;
        });
        extraPerms.setSummary(StringUtils.getIcuPluralsString(getContext(),
                R.string.additional_permissions_more, count));
        return extraPerms;
    }

    private void setNoPermissionPreference(PreferenceCategory category, @StringRes int stringId,
            Context context) {
        Preference empty = new Preference(context);
        empty.setKey(getString(stringId));
        empty.setTitle(empty.getKey());
        empty.setSelectable(false);
        category.addPreference(empty);
    }

    private void logAppPermissionGroupsFragmentView() {
        Context context = getPreferenceManager().getContext();
        if (context == null) {
            return;
        }
        String permissionSubtitleOnlyInForeground =
                context.getString(R.string.permission_subtitle_only_in_foreground);


        long sessionId = getArguments().getLong(EXTRA_SESSION_ID, INVALID_SESSION_ID);
        long viewId = new Random().nextLong();

        PreferenceCategory allowed = findPreference(Category.ALLOWED.getCategoryName());

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

            logAppPermissionsFragmentViewEntry(sessionId, viewId, preference.getKey(),
                    category);
        }

        PreferenceCategory denied = findPreference(Category.DENIED.getCategoryName());

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
        Log.i(LOG_TAG, "AppPermissionFragment view logged with sessionId=" + sessionId + " viewId="
                + viewId + " permissionGroupName=" + permissionGroupName + " uid="
                + uid + " packageName="
                + mPackageName + " category=" + category);
    }
}
