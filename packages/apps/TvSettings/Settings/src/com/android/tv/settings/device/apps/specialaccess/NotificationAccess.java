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

package com.android.tv.settings.device.apps.specialaccess;

import android.app.NotificationManager;
import android.app.tvsettings.TvSettingsEnums;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageItemInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.util.ArraySet;
import android.util.IconDrawableFactory;
import android.util.Log;

import androidx.annotation.Keep;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settingslib.applications.ServiceListing;
import com.android.tv.settings.R;
import com.android.tv.settings.SettingsPreferenceFragment;
import com.android.tv.settings.widget.SwitchWithSoundPreference;

import java.util.List;

/**
 * Settings screen for managing notification listener permissions
 */
@Keep
public class NotificationAccess extends SettingsPreferenceFragment {
    private static final String TAG = "NotificationAccess";

    private static final int MAX_CN_LENGTH = 500;

    private static final String HEADER_KEY = "header";

    private static final String DEFAULT_PACKAGES_SEPARATOR = ":";
    private ArraySet<String> mDefaultPackages;

    private NotificationManager mNotificationManager;
    private PackageManager mPackageManager;
    private ServiceListing mServiceListing;
    private IconDrawableFactory mIconDrawableFactory;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mPackageManager = context.getPackageManager();
        mNotificationManager = context.getSystemService(NotificationManager.class);
        mIconDrawableFactory = IconDrawableFactory.newInstance(context);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mServiceListing = new ServiceListing.Builder(getContext())
                .setTag(TAG)
                .setSetting(Settings.Secure.ENABLED_NOTIFICATION_LISTENERS)
                .setIntentAction(NotificationListenerService.SERVICE_INTERFACE)
                .setPermission(android.Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE)
                .setNoun("notification listener")
                .setValidator(info -> {
                    if (info.getComponentName().flattenToString().length() > MAX_CN_LENGTH) {
                        return false;
                    }
                    return true;
                 })
                .build();
        mServiceListing.addCallback(this::updateList);
    }

    @Override
    public void onResume() {
        super.onResume();
        mServiceListing.reload();
        mServiceListing.setListening(true);
    }

    @Override
    public void onPause() {
        super.onPause();
        mServiceListing.setListening(false);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.notification_access, null);

        String packages = getString(
                getResources().getIdentifier("config_defaultListenerAccessPackages",
                        "string", "android"));
        mDefaultPackages = new ArraySet<String>(packages.split(DEFAULT_PACKAGES_SEPARATOR));
    }

    private void updateList(List<ServiceInfo> services) {
        final PreferenceScreen screen = getPreferenceScreen();
        final Preference header = screen.findPreference(HEADER_KEY);
        screen.removeAll();
        if (header != null) {
            screen.addPreference(header);
        }
        services.sort(new PackageItemInfo.DisplayNameComparator(mPackageManager));
        for (ServiceInfo service : services) {
            final ComponentName cn = new ComponentName(service.packageName, service.name);
            CharSequence title = null;
            try {
                title = mPackageManager.getApplicationInfo(
                        service.packageName, 0).loadLabel(mPackageManager);
            } catch (PackageManager.NameNotFoundException e) {
                // unlikely, as we are iterating over live services.
                Log.w(TAG, "can't find package name", e);
            }
            final String summary = service.loadLabel(mPackageManager).toString();
            final SwitchWithSoundPreference pref = new SwitchWithSoundPreference(
                    getPreferenceManager().getContext());
            pref.setPersistent(false);
            pref.setIcon(mIconDrawableFactory.getBadgedIcon(service, service.applicationInfo,
                    UserHandle.getUserId(service.applicationInfo.uid)));
            if (title != null && !title.equals(summary)) {
                pref.setTitle(title);
                pref.setSummary(summary);
            } else {
                pref.setTitle(summary);
            }
            pref.setKey(cn.flattenToString());
            pref.setChecked(mNotificationManager.isNotificationListenerAccessGranted(cn));
            // Prevent the user from removing access from a default notification listener.
            if (mDefaultPackages.contains(cn.getPackageName()) && pref.isChecked()) {
                pref.setEnabled(false);
                pref.setSummary(R.string.default_notification_access_package_summary);
            }
            pref.setOnPreferenceChangeListener((preference, newValue) -> {
                final boolean enable = (boolean) newValue;
                mNotificationManager.setNotificationListenerAccessGranted(cn, enable);
                return true;
            });
            screen.addPreference(pref);
        }
        if (services.isEmpty()) {
            final Preference preference = new Preference(getPreferenceManager().getContext());
            preference.setTitle(R.string.no_notification_listeners);
        }
    }

    @Override
    protected int getPageId() {
        return TvSettingsEnums.APPS_SPECIAL_APP_ACCESS_NOTIFICATION_ACCESS;
    }
}
