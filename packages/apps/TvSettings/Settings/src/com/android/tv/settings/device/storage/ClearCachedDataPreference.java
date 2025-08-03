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

package com.android.tv.settings.device.storage;

import static com.android.tv.settings.util.InstrumentationUtils.logEntrySelected;

import android.app.tvsettings.TvSettingsEnums;
import android.content.Context;
import android.content.pm.IPackageDataObserver;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserHandle;
import android.text.format.Formatter;
import android.util.ArraySet;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.leanback.preference.LeanbackSettingsFragmentCompat;
import androidx.leanback.widget.GuidanceStylist;

import com.android.settingslib.RestrictedPreference;
import com.android.settingslib.applications.ApplicationsState;
import com.android.tv.settings.R;
import com.android.tv.settings.device.apps.AppActionPreference;
import com.android.tv.twopanelsettings.TwoPanelSettingsFragment;
import com.android.tv.twopanelsettings.slices.InfoFragment;

import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Preference for handling clear cached data of all apps.
 */
public class ClearCachedDataPreference extends RestrictedPreference implements
        AllAppsSession.OnUpdateAppListListener {
    private boolean mClearingCache;
    private AllAppsSession mAllAppsSession;
    private ApplicationsState mApplicationsState;
    private PackageManager mPackageManager;
    FreeUpStorageFragment mFreeUpStorageFragment;
    private ArraySet<ApplicationsState.AppEntry> mAllApps;
    private final Handler mHandler = new Handler(null, false);

    public ClearCachedDataPreference(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public ClearCachedDataPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public ClearCachedDataPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ClearCachedDataPreference(Context context) {
        super(context);
    }

    /**
     * Initialize the ClearCachedDataPreference with required resources.
     */
    public void initialize(AllAppsSession allAppsSession, ApplicationsState applicationsState,
            PackageManager packageManager, FreeUpStorageFragment freeUpStorageFragment) {
        mAllAppsSession = allAppsSession;
        mApplicationsState = applicationsState;
        mPackageManager = packageManager;
        mFreeUpStorageFragment = freeUpStorageFragment;
        mAllAppsSession.setOnUpdateAppListListener(this);
        setTitle(R.string.device_apps_app_management_clear_cache);
        setSummary(getContext().getString(R.string.computing_size));
    }

    /**
     * Refresh the appearance of the ClearCachedDataPreference, based on the apps info and the
     * process of clearing caches.
     */
    public void refresh() {
        mAllApps = mAllAppsSession.getAllApps().stream().collect(
                Collectors.toCollection(ArraySet::new));
        final Context context = getContext();
        final long cacheSize = getAllAppsCacheSize();
        setSummary(mClearingCache ? context.getString(R.string.computing_size)
                : context.getString(R.string.storage_free_up_clear_cached_data_summary,
                        Formatter.formatFileSize(context, cacheSize)));
        setEnabled(!mClearingCache && cacheSize > 0);
        this.setOnPreferenceClickListener(
                preference -> {
                    logEntrySelected(
                            TvSettingsEnums.SYSTEM_STORAGE_FREE_UP_STORAGE_CLEAR_CACHED_DATA);
                    return false;
                });
    }

    @Override
    public String getFragment() {
        final Fragment settingsFragment = mFreeUpStorageFragment.getCallbackFragment();
        if (settingsFragment instanceof TwoPanelSettingsFragment) {
            // Show info fragment only if the UI is TwoPanel.
            return ClearCachedDataInfoFragment.class.getName();
        }
        return null;
    }

    @Override
    protected void onClick() {
        final ConfirmationFragment confirmFragment = new ConfirmationFragment();
        confirmFragment.setOnOkListener(() -> clearCache());
        final Fragment settingsFragment = mFreeUpStorageFragment.getCallbackFragment();
        if (settingsFragment instanceof LeanbackSettingsFragmentCompat) {
            ((LeanbackSettingsFragmentCompat) settingsFragment)
                    .startImmersiveFragment(confirmFragment);
        } else if (settingsFragment instanceof TwoPanelSettingsFragment) {
            // Show confirmation fragment in TwoPanel UI.
            ((TwoPanelSettingsFragment) settingsFragment)
                    .startImmersiveFragment(confirmFragment);
        } else {
            throw new IllegalStateException("Not attached to settings fragment??");
        }
    }

    @Override
    public void onUpdateAppList(@NonNull ArrayList<ApplicationsState.AppEntry> entries) {
        refresh();
    }

    /**
     * A class that hosts {@link InfoFragment} for Clear Cached Data preference.
     */
    public static class ClearCachedDataInfoFragment extends InfoFragment {
        protected int getSummaryResId() {
            return R.string.storage_free_up_clear_cached_data_info;
        }

        @Override
        public View onCreateView(
                LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
            View view = super.onCreateView(inflater, container, savedInstanceState);
            ((ImageView) view.findViewById(com.android.tv.twopanelsettings.R.id.info_title_icon))
                    .setImageResource(
                            com.android.tv.twopanelsettings.R.drawable.ic_info_outline_base);
            view.findViewById(com.android.tv.twopanelsettings.R.id.info_title_icon).setVisibility(
                    View.VISIBLE);
            ((TextView) view.findViewById(
                    com.android.tv.twopanelsettings.R.id.info_summary)).setText(getSummaryResId());
            view.findViewById(com.android.tv.twopanelsettings.R.id.info_summary).setVisibility(
                    View.VISIBLE);
            return view;
        }
    }

    /**
     * Fragment for confirming to clear cached data of all apps.
     */
    public static class ConfirmationFragment extends AppActionPreference.ConfirmationFragment {
        private OnOkListener mOnOkListener;

        @NonNull
        @Override
        public GuidanceStylist.Guidance onCreateGuidance(Bundle savedInstanceState) {
            return new GuidanceStylist.Guidance(
                    getString(R.string.storage_free_up_clear_cached_data_confirm_title),
                    getString(R.string.storage_free_up_clear_cached_data_confirm_description),
                    null,
                    getContext().getDrawable(R.drawable.ic_settings_backup_restore_132dp));
        }

        @Override
        public void onOk() {
            if (mOnOkListener != null) {
                mOnOkListener.onOk();
            }
        }

        public void setOnOkListener(OnOkListener listener) {
            mOnOkListener = listener;
        }

        /**
         * A listener to respond ok event.
         */
        public interface OnOkListener {
            /**
             * It will be called when the Ok button was clicked.
             */
            void onOk();
        }
    }

    private long getAllAppsCacheSize() {
        long cacheSize = 0;
        for (ApplicationsState.AppEntry app : mAllApps) {
            cacheSize += app.cacheSize + app.externalCacheSize;
        }
        return cacheSize;
    }

    private void setClearingCache(boolean clearingCache) {
        mClearingCache = clearingCache;
        refresh();
    }

    private void clearCache() {
        if (mClearingCache) {
            return;
        }
        setClearingCache(true);
        Map<String, ApplicationsState.AppEntry> packageNamesClearingCache = mAllApps.stream()
                .collect(Collectors.toMap(entry -> entry.info.packageName, entry -> entry));
        ArraySet<String> packageNames = mAllApps.stream().map(
                entry -> entry.info.packageName).collect(
                Collectors.toCollection(ArraySet::new));
        for (String packageName : packageNames) {
            mPackageManager.deleteApplicationCacheFiles(packageName,
                    new IPackageDataObserver.Stub() {
                        public void onRemoveCompleted(final String packageName,
                                final boolean succeeded) {
                            mHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    ApplicationsState.AppEntry entry =
                                            packageNamesClearingCache.get(packageName);
                                    packageNamesClearingCache.remove(packageName);
                                    if (succeeded) {
                                        final int userId = UserHandle.getUserId(entry.info.uid);
                                        mApplicationsState.requestSize(packageName, userId);
                                    }
                                    if (packageNamesClearingCache.isEmpty()) {
                                        setClearingCache(false);
                                        refresh();
                                    }
                                }
                            });
                        }
                    });
        }
        refresh();
    }
}
