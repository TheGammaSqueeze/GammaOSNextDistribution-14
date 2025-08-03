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
import static com.android.permissioncontroller.permission.ui.Category.ALLOWED;
import static com.android.permissioncontroller.permission.ui.Category.ALLOWED_FOREGROUND;
import static com.android.permissioncontroller.permission.ui.Category.ASK;
import static com.android.permissioncontroller.permission.ui.Category.DENIED;
import static com.android.permissioncontroller.permission.ui.Category.STORAGE_FOOTER;
import static com.android.permissioncontroller.permission.ui.ManagePermissionsActivity.EXTRA_CALLER_NAME;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.UserHandle;
import android.util.ArrayMap;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;

import com.android.modules.utils.build.SdkLevel;
import com.android.permissioncontroller.R;
import com.android.permissioncontroller.auto.AutoSettingsFrameFragment;
import com.android.permissioncontroller.permission.model.v31.AppPermissionUsage;
import com.android.permissioncontroller.permission.model.v31.PermissionUsages;
import com.android.permissioncontroller.permission.ui.Category;
import com.android.permissioncontroller.permission.ui.handheld.SmartIconLoadPackagePermissionPreference;
import com.android.permissioncontroller.permission.ui.model.PermissionAppsViewModel;
import com.android.permissioncontroller.permission.ui.model.PermissionAppsViewModelFactory;
import com.android.permissioncontroller.permission.utils.KotlinUtils;
import com.android.permissioncontroller.permission.utils.Utils;
import com.android.settingslib.utils.applications.AppUtils;

import kotlin.Pair;
import kotlin.Triple;

import java.text.Collator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Shows the list of applications which have (or do not have) the given permission. */
public class AutoPermissionAppsFragment extends AutoSettingsFrameFragment implements
        PermissionUsages.PermissionsUsagesChangeCallback {

    private static final String LOG_TAG = "AutoPermissionAppsFragment";
    private static final String KEY_EMPTY = "_empty";

    /** Creates a new instance of {@link AutoPermissionAppsFragment} for the given permission. */
    public static AutoPermissionAppsFragment newInstance(String permissionGroupName,
            long sessionId) {
        return setPermissionGroupName(new AutoPermissionAppsFragment(), permissionGroupName,
                sessionId);
    }

    private static <T extends Fragment> T setPermissionGroupName(
            T fragment, String permissionGroupName, long sessionId) {
        Bundle arguments = new Bundle();
        arguments.putString(Intent.EXTRA_PERMISSION_GROUP_NAME, permissionGroupName);
        arguments.putLong(EXTRA_SESSION_ID, sessionId);
        fragment.setArguments(arguments);
        return fragment;
    }

    private PermissionAppsViewModel mViewModel;
    private PermissionUsages mPermissionUsages;
    private List<AppPermissionUsage> mAppPermissionUsages = new ArrayList<>();
    private String mPermGroupName;

    private Collator mCollator;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setLoading(true);

        mPermGroupName = getArguments().getString(Intent.EXTRA_PERMISSION_GROUP_NAME);
        if (mPermGroupName == null) {
            mPermGroupName = getArguments().getString(Intent.EXTRA_PERMISSION_NAME);
        }

        Drawable icon = KotlinUtils.INSTANCE.getPermGroupIcon(getContext(), mPermGroupName);
        CharSequence label = KotlinUtils.INSTANCE.getPermGroupLabel(getContext(), mPermGroupName);
        CharSequence description = KotlinUtils.INSTANCE.getPermGroupDescription(getContext(),
                mPermGroupName);

        setHeaderLabel(label);
        Preference header = new Preference(getContext());
        header.setTitle(label);
        header.setIcon(icon);
        header.setSummary(Utils.getPermissionGroupDescriptionString(getContext(), mPermGroupName,
                description));
        getPreferenceScreen().addPreference(header);

        mCollator = Collator.getInstance(
                getContext().getResources().getConfiguration().getLocales().get(0));

        PermissionAppsViewModelFactory factory =
                new PermissionAppsViewModelFactory(getActivity().getApplication(), mPermGroupName,
                        this, new Bundle());
        mViewModel = new ViewModelProvider(this, factory).get(PermissionAppsViewModel.class);

        mViewModel.getCategorizedAppsLiveData().observe(this, this::onPackagesLoaded);
        mViewModel.getShouldShowSystemLiveData().observe(this, this::updateMenu);
        mViewModel.getHasSystemAppsLiveData().observe(this, this::hideSystemAppToggleIfNecessary);

        // If the build type is below S, the app ops for permission usage can't be found. Thus, we
        // shouldn't load permission usages, for them.
        if (SdkLevel.isAtLeastS()) {
            Context context = getPreferenceManager().getContext();
            mPermissionUsages = new PermissionUsages(context);

            long filterTimeBeginMillis = mViewModel.getFilterTimeBeginMillis();
            mPermissionUsages.load(null, null, filterTimeBeginMillis, Long.MAX_VALUE,
                    PermissionUsages.USAGE_FLAG_LAST, getActivity().getLoaderManager(),
                    false, false, this, false);
        }
    }

    private void updateMenu(Boolean showSystem) {
        if (showSystem == null) {
            showSystem = false;
        }
        // Show the opposite label from the current state.
        String label;
        if (showSystem) {
            label = getString(R.string.menu_hide_system);
        } else {
            label = getString(R.string.menu_show_system);
        }

        boolean showSystemFinal = showSystem;
        setAction(label, v -> mViewModel.updateShowSystem(!showSystemFinal));
    }

    /**
     * Main differences between this phone implementation and this one are:
     * <ul>
     *     <li>No special handling for scoped storage</li>
     * </ul>
     */
    private void onPackagesLoaded(Map<Category, List<Pair<String, UserHandle>>> categories) {
        Preference additionalPermissionsPreference = getPreferenceScreen().findPreference(
                ALLOWED_FOREGROUND.getCategoryName());
        if (additionalPermissionsPreference == null) {
            // This preference resources includes the "Ask" permission group. That's okay for Auto
            // even though Auto doesn't support the one-time permission because the code later in
            // this method will hide unused permission groups.
            addPreferencesFromResource(R.xml.allowed_denied);
        }
        // Hide allowed foreground label by default, to avoid briefly showing it before updating
        findPreference(ALLOWED_FOREGROUND.getCategoryName()).setVisible(false);

        // Hide storage footer category
        findPreference(STORAGE_FOOTER.getCategoryName()).setVisible(false);

        Context context = getPreferenceManager().getContext();

        if (context == null || getActivity() == null || categories == null) {
            return;
        }

        Map<String, Preference> existingPrefs = new ArrayMap<>();

        // Start at 1 since the header preference will always be in the 0th index
        for (int i = 1; i < getPreferenceScreen().getPreferenceCount(); i++) {
            PreferenceCategory category = (PreferenceCategory)
                    getPreferenceScreen().getPreference(i);
            category.setOrderingAsAdded(true);
            int numPreferences = category.getPreferenceCount();
            for (int j = 0; j < numPreferences; j++) {
                Preference preference = category.getPreference(j);
                existingPrefs.put(preference.getKey(), preference);
            }
            category.removeAll();
        }

        long viewIdForLogging = new Random().nextLong();
        long sessionId = getArguments().getLong(EXTRA_SESSION_ID, INVALID_SESSION_ID);

        Boolean showAlways = mViewModel.getShowAllowAlwaysStringLiveData().getValue();
        if (showAlways != null && showAlways) {
            findPreference(ALLOWED.getCategoryName()).setTitle(R.string.allowed_always_header);
        } else {
            findPreference(ALLOWED.getCategoryName()).setTitle(R.string.allowed_header);
        }

        // A mapping of user + packageName to their last access timestamps for the permission group.
        Map<String, Long> groupUsageLastAccessTime =
                mViewModel.extractGroupUsageLastAccessTime(mAppPermissionUsages);

        for (Category grantCategory : categories.keySet()) {
            List<Pair<String, UserHandle>> packages = categories.get(grantCategory);
            PreferenceCategory category = findPreference(grantCategory.getCategoryName());

            // If this category is empty set up the empty preference.
            if (packages.size() == 0) {
                Preference empty = new Preference(context);
                empty.setSelectable(false);
                empty.setKey(category.getKey() + KEY_EMPTY);
                if (grantCategory.equals(ALLOWED)) {
                    empty.setTitle(getString(R.string.no_apps_allowed));
                } else if (grantCategory.equals(ALLOWED_FOREGROUND)) {
                    category.setVisible(false);
                } else if (grantCategory.equals(ASK)) {
                    category.setVisible(false);
                } else {
                    empty.setTitle(getString(R.string.no_apps_denied));
                }
                category.addPreference(empty);
                continue;
            } else if (grantCategory.equals(ALLOWED_FOREGROUND)) {
                category.setVisible(true);
            } else if (grantCategory.equals(ASK)) {
                category.setVisible(true);
            }

            for (Pair<String, UserHandle> packageUserLabel : packages) {
                String packageName = packageUserLabel.getFirst();
                UserHandle user = packageUserLabel.getSecond();

                String key = user + packageName;

                Long lastAccessTime = groupUsageLastAccessTime.get(key);
                Triple<String, Integer, String> summaryTimestamp = Utils
                        .getPermissionLastAccessSummaryTimestamp(
                                lastAccessTime, context, mPermGroupName);

                Preference existingPref = existingPrefs.get(key);
                if (existingPref != null) {
                    updatePreferenceSummary(existingPref, summaryTimestamp);
                    category.addPreference(existingPref);
                    continue;
                }

                SmartIconLoadPackagePermissionPreference pref =
                        new SmartIconLoadPackagePermissionPreference(getActivity().getApplication(),
                                packageName, user, context);
                pref.setKey(key);
                pref.setTitle(KotlinUtils.INSTANCE.getPackageLabel(getActivity().getApplication(),
                        packageName, user));
                pref.setOnPreferenceClickListener((Preference p) -> {
                    Intent intent = new Intent(Intent.ACTION_MANAGE_APP_PERMISSION);
                    intent.putExtra(Intent.EXTRA_PACKAGE_NAME, packageName);
                    intent.putExtra(Intent.EXTRA_PERMISSION_GROUP_NAME, mPermGroupName);
                    intent.putExtra(Intent.EXTRA_USER, user);
                    intent.putExtra(EXTRA_CALLER_NAME, getClass().getName());
                    intent.putExtra(EXTRA_SESSION_ID, sessionId);
                    startActivity(intent);
                    return true;
                });
                pref.setTitleContentDescription(AppUtils.getAppContentDescription(context,
                        packageName, user.getIdentifier()));

                updatePreferenceSummary(pref, summaryTimestamp);

                category.addPreference(pref);
                if (!mViewModel.getCreationLogged()) {
                    logPermissionAppsFragmentCreated(packageName, user, viewIdForLogging,
                            grantCategory.equals(ALLOWED), grantCategory.equals(ALLOWED_FOREGROUND),
                            grantCategory.equals(DENIED));
                }
            }
            KotlinUtils.INSTANCE.sortPreferenceGroup(category, this::comparePreference, false);
        }

        mViewModel.setCreationLogged(true);

        setLoading(false);
    }

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
        setPreferenceScreen(getPreferenceManager().createPreferenceScreen(getContext()));
    }

    private void hideSystemAppToggleIfNecessary(Boolean hasSystemApps) {
        if (hasSystemApps == null || !hasSystemApps) {
            setAction(/* label= */ null, /* onClickListener= */ null);
        }
    }

    private void updatePreferenceSummary(Preference preference,
            Triple<String, Integer, String> summaryTimestamp) {
        String summary = mViewModel.getPreferenceSummary(getResources(), summaryTimestamp);
        if (!summary.isEmpty()) {
            preference.setSummary(summary);
        }
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
        onPackagesLoaded(mViewModel.getCategorizedAppsLiveData().getValue());
    }

    private int comparePreference(Preference lhs, Preference rhs) {
        return mViewModel.comparePreference(mCollator, lhs, rhs);
    }

    private void logPermissionAppsFragmentCreated(String packageName, UserHandle user, long viewId,
            boolean isAllowed, boolean isAllowedForeground, boolean isDenied) {
        long sessionId = getArguments().getLong(EXTRA_SESSION_ID, 0);
        mViewModel.logPermissionAppsFragmentCreated(packageName, user, viewId, isAllowed,
                isAllowedForeground, isDenied, sessionId, getActivity().getApplication(),
                mPermGroupName, LOG_TAG);
    }
}
