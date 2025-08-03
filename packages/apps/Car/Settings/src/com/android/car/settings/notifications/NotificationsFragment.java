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

package com.android.car.settings.notifications;

import static com.android.car.settings.storage.StorageUtils.maybeInitializeVolume;

import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import android.os.storage.StorageManager;
import android.os.storage.VolumeInfo;
import android.provider.Settings;

import androidx.annotation.XmlRes;

import com.android.car.settings.R;
import com.android.car.settings.applications.ApplicationListItemManager;
import com.android.car.settings.common.SettingsFragment;
import com.android.car.settings.search.CarBaseSearchIndexProvider;
import com.android.settingslib.applications.ApplicationsState;
import com.android.settingslib.search.SearchIndexable;

/** Shows subsettings related to notifications. */
@SearchIndexable
public class NotificationsFragment extends SettingsFragment {

    private ApplicationListItemManager mAppListItemManager;
    @Override
    @XmlRes
    protected int getPreferenceScreenResId() {
        return R.xml.notifications_fragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        Application application = requireActivity().getApplication();
        ApplicationsState applicationsState = ApplicationsState.getInstance(application);
        StorageManager sm = context.getSystemService(StorageManager.class);
        VolumeInfo volume = maybeInitializeVolume(sm, getArguments());

        NotificationsAppListPreferenceController notificationsAppListController =
                use(NotificationsAppListPreferenceController.class,
                        R.string.pk_notifications_settings_all_apps);
        RecentNotificationsAppsPreferenceController recentNotificationsController =
                use(RecentNotificationsAppsPreferenceController.class,
                        R.string.pk_notifications_settings_recently_sent);

        mAppListItemManager = new ApplicationListItemManager(volume, getLifecycle(),
                applicationsState,
                getContext().getResources().getInteger(
                        R.integer.millisecond_app_data_update_interval),
                getContext().getResources().getInteger(
                        R.integer.millisecond_max_app_load_wait_interval));
        mAppListItemManager.registerListener(notificationsAppListController);
        mAppListItemManager.registerListener(recentNotificationsController);
        recentNotificationsController.setApplicationsState(applicationsState);

        // Since an app can show in both sections, use a listener to notify the other section to
        // update in order to maintain consistency
        notificationsAppListController.setNotificationSwitchListener(() ->
                mAppListItemManager.onPackageListChanged());
        recentNotificationsController.setNotificationSwitchListener(() ->
                mAppListItemManager.onPackageListChanged());
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAppListItemManager.startLoading(getAppFilter(), ApplicationsState.ALPHA_COMPARATOR);
    }

    @Override
    public void onStart() {
        super.onStart();
        mAppListItemManager.onFragmentStart();
    }

    @Override
    public void onStop() {
        super.onStop();
        mAppListItemManager.onFragmentStop();
    }

    private ApplicationsState.AppFilter getAppFilter() {
        // Display only non-system apps
        return ApplicationsState.FILTER_DOWNLOADED_AND_LAUNCHER_AND_INSTANT;
    }

    /**
     * Callback that is called when a app's notification setting is toggled
     */
    public interface NotificationSwitchListener {
        /**
         * An app's notification setting has been changed
         */
        void onSwitchChanged();
    }

    /**
     * Data provider for Settings Search.
     */
    public static final CarBaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new CarBaseSearchIndexProvider(R.xml.notifications_fragment,
                    Settings.ACTION_NOTIFICATION_SETTINGS);
}
