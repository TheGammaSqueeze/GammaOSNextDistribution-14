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
import static com.android.permissioncontroller.permission.ui.Category.ALLOWED;
import static com.android.permissioncontroller.permission.ui.Category.ALLOWED_FOREGROUND;
import static com.android.permissioncontroller.permission.ui.Category.ASK;
import static com.android.permissioncontroller.permission.ui.Category.DENIED;
import static com.android.permissioncontroller.permission.ui.Category.STORAGE_FOOTER;
import static com.android.permissioncontroller.permission.ui.handheld.UtilsKt.pressBack;

import android.Manifest;
import android.app.ActionBar;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.safetycenter.SafetyCenterManager;
import android.util.ArrayMap;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.lifecycle.ViewModelProvider;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;

import com.android.modules.utils.build.SdkLevel;
import com.android.permissioncontroller.R;
import com.android.permissioncontroller.permission.model.v31.AppPermissionUsage;
import com.android.permissioncontroller.permission.model.v31.PermissionUsages;
import com.android.permissioncontroller.permission.ui.Category;
import com.android.permissioncontroller.permission.ui.handheld.v31.CardViewPreference;
import com.android.permissioncontroller.permission.ui.model.PermissionAppsViewModel;
import com.android.permissioncontroller.permission.ui.model.PermissionAppsViewModelFactory;
import com.android.permissioncontroller.permission.utils.KotlinUtils;
import com.android.permissioncontroller.permission.utils.Utils;
import com.android.settingslib.HelpUtils;
import com.android.settingslib.utils.applications.AppUtils;
import com.android.settingslib.widget.FooterPreference;

import kotlin.Pair;
import kotlin.Triple;

import java.text.Collator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Show and manage apps which request a single permission group.
 *
 * <p>Shows a list of apps which request at least on permission of this group.
 */
public final class PermissionAppsFragment extends SettingsWithLargeHeader implements
        PermissionUsages.PermissionsUsagesChangeCallback {

    private static final String KEY_SHOW_SYSTEM_PREFS = "_showSystem";
    private static final String CREATION_LOGGED_SYSTEM_PREFS = "_creationLogged";
    private static final String KEY_FOOTER = "_footer";
    private static final String KEY_EMPTY = "_empty";
    private static final String LOG_TAG = "PermissionAppsFragment";
    private static final String STORAGE_ALLOWED_FULL = "allowed_storage_full";
    private static final String STORAGE_ALLOWED_SCOPED = "allowed_storage_scoped";
    private static final String BLOCKED_SENSOR_PREF_KEY = "sensor_card";
    private static final String STORAGE_FOOTER_PREFERENCE_KEY = "storage_footer_preference";
    private static final int SHOW_LOAD_DELAY_MS = 200;

    private static final String PRIVACY_CONTROLS_ACTION = "android.settings.PRIVACY_CONTROLS";

    /**
     * Create a bundle with the arguments needed by this fragment
     *
     * @param permGroupName The name of the permission group
     * @param sessionId     The current session ID
     * @return A bundle with all of the args placed
     */
    public static Bundle createArgs(String permGroupName, long sessionId) {
        Bundle arguments = new Bundle();
        arguments.putString(Intent.EXTRA_PERMISSION_GROUP_NAME, permGroupName);
        arguments.putLong(EXTRA_SESSION_ID, sessionId);
        return arguments;
    }

    private MenuItem mShowSystemMenu;
    private MenuItem mHideSystemMenu;
    private String mPermGroupName;
    private Collator mCollator;
    private PermissionAppsViewModel mViewModel;
    private PermissionUsages mPermissionUsages;
    private List<AppPermissionUsage> mAppPermissionUsages = new ArrayList<>();
    private Boolean mSensorStatus;
    private UserManager mUserManager;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mPermGroupName = getArguments().getString(Intent.EXTRA_PERMISSION_GROUP_NAME);
        if (mPermGroupName == null) {
            mPermGroupName = getArguments().getString(Intent.EXTRA_PERMISSION_NAME);
        }

        mCollator = Collator.getInstance(
                getContext().getResources().getConfiguration().getLocales().get(0));

        PermissionAppsViewModelFactory factory =
                new PermissionAppsViewModelFactory(getActivity().getApplication(), mPermGroupName,
                        this, new Bundle());
        mViewModel = new ViewModelProvider(this, factory).get(PermissionAppsViewModel.class);

        mViewModel.getCategorizedAppsLiveData().observe(this, this::onPackagesLoaded);
        mViewModel.getShouldShowSystemLiveData().observe(this, this::updateMenu);
        mViewModel.getHasSystemAppsLiveData().observe(this, (Boolean hasSystem) ->
                getActivity().invalidateOptionsMenu());

        if (!mViewModel.arePackagesLoaded()) {
            Handler handler = new Handler(Looper.getMainLooper());
            handler.postDelayed(() -> {
                if (!mViewModel.arePackagesLoaded()) {
                    setLoading(true /* loading */, false /* animate */);
                }
            }, SHOW_LOAD_DELAY_MS);
        }

        setHasOptionsMenu(true);
        final ActionBar ab = getActivity().getActionBar();
        if (ab != null) {
            ab.setDisplayHomeAsUpEnabled(true);
        }

        // If the build type is below S, the app ops for permission usage can't be found. Thus, we
        // shouldn't load permission usages, for them.
        if (SdkLevel.isAtLeastS()) {
            Context context = getPreferenceManager().getContext();
            mPermissionUsages = new PermissionUsages(context);

            long filterTimeBeginMillis = mViewModel.getFilterTimeBeginMillis();
            mPermissionUsages.load(null, null, filterTimeBeginMillis, Long.MAX_VALUE,
                    PermissionUsages.USAGE_FLAG_LAST, getActivity().getLoaderManager(),
                    false, false, this, false);

            if (Utils.shouldDisplayCardIfBlocked(mPermGroupName)) {
                mViewModel.getSensorStatusLiveData().observe(this, this::setSensorStatus);
            }
        }

        mUserManager = Utils.getSystemServiceSafe(getContext(), UserManager.class);
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

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);

        if (mViewModel.getHasSystemAppsLiveData().getValue()) {
            mShowSystemMenu = menu.add(Menu.NONE, MENU_SHOW_SYSTEM, Menu.NONE,
                    R.string.menu_show_system);
            mHideSystemMenu = menu.add(Menu.NONE, MENU_HIDE_SYSTEM, Menu.NONE,
                    R.string.menu_hide_system);
            updateMenu(mViewModel.getShouldShowSystemLiveData().getValue());
        }

        if (!SdkLevel.isAtLeastS()) {
            HelpUtils.prepareHelpMenuItem(getActivity(), menu, R.string.help_app_permissions,
                    getClass().getName());
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                mViewModel.updateShowSystem(false);
                pressBack(this);
                return true;
            case MENU_SHOW_SYSTEM:
            case MENU_HIDE_SYSTEM:
                mViewModel.updateShowSystem(item.getItemId() == MENU_SHOW_SYSTEM);
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateMenu(Boolean showSystem) {
        if (showSystem == null) {
            showSystem = false;
        }
        if (mShowSystemMenu != null && mHideSystemMenu != null) {
            mShowSystemMenu.setVisible(!showSystem);
            mHideSystemMenu.setVisible(showSystem);
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private void setSensorStatus(Boolean sensorStatus) {
        mSensorStatus = sensorStatus;
        displaySensorCard();
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private void displaySensorCard() {
        if (Utils.shouldDisplayCardIfBlocked(mPermGroupName)) {
            if (mSensorStatus) {
                setSensorCard();
            } else {
                removeSensorCard();
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private void setSensorCard() {
        CardViewPreference sensorCard = findPreference(BLOCKED_SENSOR_PREF_KEY);
        if (sensorCard == null) {
            sensorCard = createSensorCard();
            ensurePreferenceScreen();
            getPreferenceScreen().addPreference(sensorCard);
        }
        sensorCard.setVisible(true);
    }

    private void ensurePreferenceScreen() {
        // Check if preference screen has been already loaded
        if (getPreferenceScreen() != null) {
            return;
        }
        boolean isStorageAndLessThanT = !SdkLevel.isAtLeastT()
                && mPermGroupName.equals(Manifest.permission_group.STORAGE);
        if (isStorageAndLessThanT) {
            addPreferencesFromResource(R.xml.allowed_denied_storage);
        } else {
            addPreferencesFromResource(R.xml.allowed_denied);
        }
        // Hide allowed foreground label by default, to avoid briefly showing it before updating
        findPreference(ALLOWED_FOREGROUND.getCategoryName()).setVisible(false);
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private String getPrivacyControlsIntent() {
        Context context = getPreferenceManager().getContext();
        SafetyCenterManager safetyCenterManager =
                context.getSystemService(SafetyCenterManager.class);
        if (safetyCenterManager.isSafetyCenterEnabled()) {
            return PRIVACY_CONTROLS_ACTION;
        } else {
            return Settings.ACTION_PRIVACY_SETTINGS;
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private CardViewPreference createSensorCard() {
        boolean isLocation = Manifest.permission_group.LOCATION.equals(mPermGroupName);
        Context context = getPreferenceManager().getContext();

        String action;
        if (isLocation) {
            action = Settings.ACTION_LOCATION_SOURCE_SETTINGS;
        } else  if (SdkLevel.isAtLeastT()) {
            action = getPrivacyControlsIntent();
        } else {
            action = Settings.ACTION_PRIVACY_SETTINGS;
        }
        CardViewPreference sensorCard = new CardViewPreference(context, action);
        sensorCard.setKey(BLOCKED_SENSOR_PREF_KEY);
        sensorCard.setIcon(Utils.getBlockedIcon(mPermGroupName));
        sensorCard.setTitle(Utils.getBlockedTitle(mPermGroupName));
        boolean isMicrophone = Manifest.permission_group.MICROPHONE.equals(mPermGroupName);
        int cardSummary =
                isMicrophone ? R.string.blocked_mic_summary : R.string.blocked_sensor_summary;
        sensorCard.setSummary(context.getString(cardSummary));
        sensorCard.setVisible(true);
        sensorCard.setOrder(-1);
        return sensorCard;
    }

    private void addStorageFooterSeeAllFilesAccess() {
        PreferenceScreen screen = getPreferenceScreen();
        Context context = screen.getPreferenceManager().getContext();
        PreferenceCategory preferenceCategory = findPreference(STORAGE_FOOTER.getCategoryName());
        Preference existingPreference = findPreference(STORAGE_FOOTER_PREFERENCE_KEY);

        if (preferenceCategory == null || existingPreference != null) {
            return;
        }

        FooterPreference preference = new FooterPreference(context);
        preference.setKey(STORAGE_FOOTER_PREFERENCE_KEY);
        preference.setIcon(Utils.applyTint(getActivity(), R.drawable.ic_info_outline,
                android.R.attr.colorControlNormal));
        preference.setLearnMoreText(getString(R.string.storage_footer_hyperlink_text));
        preference.setLearnMoreAction(v -> {
            context.startActivity(
                    new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
        });

        preferenceCategory.addPreference(preference);
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private void removeSensorCard() {
        CardViewPreference sensorCard = findPreference(BLOCKED_SENSOR_PREF_KEY);
        if (sensorCard != null) {
            sensorCard.setVisible(false);
        }
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindUi(this, mPermGroupName);
    }

    private static void bindUi(SettingsWithLargeHeader fragment, @NonNull String groupName) {
        Context context = fragment.getContext();
        if (context == null || fragment.getActivity() == null) {
            return;
        }
        Drawable icon = KotlinUtils.INSTANCE.getPermGroupIcon(context, groupName);

        CharSequence label = KotlinUtils.INSTANCE.getPermGroupLabel(context, groupName);
        CharSequence description = KotlinUtils.INSTANCE.getPermGroupDescription(context, groupName);

        fragment.setHeader(icon, label, null, null, true);
        fragment.setSummary(Utils.getPermissionGroupDescriptionString(fragment.getActivity(),
                groupName, description), null);
        fragment.getActivity().setTitle(label);
    }

    private void onPackagesLoaded(Map<Category, List<Pair<String, UserHandle>>> categories) {
        boolean isStorageAndLessThanT = !SdkLevel.isAtLeastT()
                && mPermGroupName.equals(Manifest.permission_group.STORAGE);
        ensurePreferenceScreen();
        Context context = getPreferenceManager().getContext();

        if (context == null || getActivity() == null || categories == null) {
            return;
        }

        Map<String, Preference> existingPrefs = new ArrayMap<>();

        for (int i = 0; i < getPreferenceScreen().getPreferenceCount(); i++) {
            Preference pref = getPreferenceScreen().getPreference(i);
            if (BLOCKED_SENSOR_PREF_KEY.equals(pref.getKey())) {
                continue;
            }
            PreferenceCategory category = (PreferenceCategory) pref;
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
        if (!isStorageAndLessThanT) {
            if (showAlways != null && showAlways) {
                findPreference(ALLOWED.getCategoryName()).setTitle(R.string.allowed_always_header);
            } else {
                findPreference(ALLOWED.getCategoryName()).setTitle(R.string.allowed_header);
            }
        }

        // A mapping of user + packageName to their last access timestamps for the permission group.
        Map<String, Long> groupUsageLastAccessTime =
                mViewModel.extractGroupUsageLastAccessTime(mAppPermissionUsages);

        for (Category grantCategory : categories.keySet()) {
            List<Pair<String, UserHandle>> packages = categories.get(grantCategory);
            PreferenceCategory category = findPreference(grantCategory.getCategoryName());


            // If this category is empty, and this isn't the "allowed" category of the storage
            // permission, set up the empty preference.
            if (packages.size() == 0
                    && (!isStorageAndLessThanT || !grantCategory.equals(ALLOWED))) {
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
                if (!Utils.shouldShowInSettings(packageUserLabel.getSecond(), mUserManager)) {
                    continue;
                }
                String packageName = packageUserLabel.getFirst();
                UserHandle user = packageUserLabel.getSecond();

                String key = user + packageName;

                Long lastAccessTime = groupUsageLastAccessTime.get(key);
                Triple<String, Integer, String> summaryTimestamp = Utils
                        .getPermissionLastAccessSummaryTimestamp(
                                lastAccessTime, context, mPermGroupName);

                if (isStorageAndLessThanT && grantCategory.equals(ALLOWED)) {
                    category = mViewModel.packageHasFullStorage(packageName, user)
                            ? findPreference(STORAGE_ALLOWED_FULL)
                            : findPreference(STORAGE_ALLOWED_SCOPED);
                }

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
                    mViewModel.navigateToAppPermission(this, packageName, user,
                            AppPermissionFragment.createArgs(packageName, null, mPermGroupName,
                                    user, getClass().getName(), sessionId,
                                    grantCategory.getCategoryName()));
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

            if (isStorageAndLessThanT && grantCategory.equals(ALLOWED)) {
                PreferenceCategory full = findPreference(STORAGE_ALLOWED_FULL);
                PreferenceCategory scoped = findPreference(STORAGE_ALLOWED_SCOPED);
                if (full.getPreferenceCount() == 0) {
                    Preference empty = new Preference(context);
                    empty.setSelectable(false);
                    empty.setKey(STORAGE_ALLOWED_FULL + KEY_EMPTY);
                    empty.setTitle(getString(R.string.no_apps_allowed_full));
                    full.addPreference(empty);
                }

                if (scoped.getPreferenceCount() == 0) {
                    Preference empty = new Preference(context);
                    empty.setSelectable(false);
                    empty.setKey(STORAGE_ALLOWED_FULL + KEY_EMPTY);
                    empty.setTitle(getString(R.string.no_apps_allowed_scoped));
                    scoped.addPreference(empty);
                }
                KotlinUtils.INSTANCE.sortPreferenceGroup(full, this::comparePreference, false);
                KotlinUtils.INSTANCE.sortPreferenceGroup(scoped, this::comparePreference, false);
            } else {
                KotlinUtils.INSTANCE.sortPreferenceGroup(category, this::comparePreference, false);
            }
        }

        if (SdkLevel.isAtLeastT() && Manifest.permission_group.STORAGE.equals(mPermGroupName)) {
            addStorageFooterSeeAllFilesAccess();
        } else {
            // Hide storage footer category
            PreferenceCategory storageFooterPreferenceCategory =
                    findPreference(STORAGE_FOOTER.getCategoryName());
            if (storageFooterPreferenceCategory != null) {
                storageFooterPreferenceCategory.setVisible(false);
            }
        }

        mViewModel.setCreationLogged(true);

        setLoading(false /* loading */, true /* animate */);
    }

    private void updatePreferenceSummary(Preference preference,
            Triple<String, Integer, String> summaryTimestamp) {
        String summary = mViewModel.getPreferenceSummary(getResources(), summaryTimestamp);
        if (!summary.isEmpty()) {
            preference.setSummary(summary);
        }
    }


    private int comparePreference(Preference lhs, Preference rhs) {
        int result = mCollator.compare(lhs.getTitle().toString(),
                rhs.getTitle().toString());
        if (result == 0) {
            result = lhs.getKey().compareTo(rhs.getKey());
        }
        return result;
    }

    private void logPermissionAppsFragmentCreated(String packageName, UserHandle user, long viewId,
            boolean isAllowed, boolean isAllowedForeground, boolean isDenied) {
        long sessionId = getArguments().getLong(EXTRA_SESSION_ID, 0);
        mViewModel.logPermissionAppsFragmentCreated(packageName, user, viewId, isAllowed,
                isAllowedForeground, isDenied, sessionId, getActivity().getApplication(),
                mPermGroupName, LOG_TAG);
    }
}
