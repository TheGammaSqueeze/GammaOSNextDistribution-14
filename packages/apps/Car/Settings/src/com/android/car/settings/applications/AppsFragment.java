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

package com.android.car.settings.applications;

import android.content.Context;
import android.os.Bundle;
import android.provider.Settings;

import androidx.annotation.XmlRes;

import com.android.car.settings.R;
import com.android.car.settings.applications.performance.PerfImpactingAppsItemManager;
import com.android.car.settings.common.SettingsFragment;
import com.android.car.settings.search.CarBaseSearchIndexProvider;
import com.android.settingslib.search.SearchIndexable;

/** Shows subsettings related to apps. */
@SearchIndexable
public class AppsFragment extends SettingsFragment {

    private RecentAppsItemManager mRecentAppsItemManager;
    private InstalledAppCountItemManager mInstalledAppCountItemManager;
    private HibernatedAppsItemManager mHibernatedAppsItemManager;
    private PerfImpactingAppsItemManager mPerfImpactingAppsItemManager;

    @Override
    @XmlRes
    protected int getPreferenceScreenResId() {
        return R.xml.apps_fragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mRecentAppsItemManager = new RecentAppsItemManager(context,
                context.getResources().getInteger(R.integer.recent_apps_max_count));
        mRecentAppsItemManager.addListener(use(AllAppsPreferenceController.class,
                R.string.pk_applications_settings_screen_entry));
        mRecentAppsItemManager.addListener(use(RecentAppsGroupPreferenceController.class,
                R.string.pk_recent_apps_group));
        mRecentAppsItemManager.addListener(use(RecentAppsListPreferenceController.class,
                R.string.pk_recent_apps_list));

        mInstalledAppCountItemManager = new InstalledAppCountItemManager(context);
        mInstalledAppCountItemManager.addListener(use(AllAppsPreferenceController.class,
                R.string.pk_applications_settings_screen_entry));
        mInstalledAppCountItemManager.addListener(use(RecentAppsViewAllPreferenceController.class,
                R.string.pk_recent_apps_view_all));

        mHibernatedAppsItemManager = new HibernatedAppsItemManager(context);
        mHibernatedAppsItemManager.setListener(use(HibernatedAppsPreferenceController.class,
                R.string.pk_hibernated_apps));

        mPerfImpactingAppsItemManager = new PerfImpactingAppsItemManager(context);
        mPerfImpactingAppsItemManager.addListener(
                use(PerfImpactingAppsEntryPreferenceController.class,
                        R.string.pk_performance_impacting_apps_entry));
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mRecentAppsItemManager.startLoading();
        mInstalledAppCountItemManager.startLoading();
        mHibernatedAppsItemManager.startLoading();
    }

    @Override
    public void onResume() {
        super.onResume();
        mPerfImpactingAppsItemManager.startLoading();
    }

    /**
     * Data provider for Settings Search.
     */
    public static final CarBaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new CarBaseSearchIndexProvider(R.xml.apps_fragment,
                    Settings.ACTION_APPLICATION_SETTINGS);
}
