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

package com.android.tv.settings.library.device.apps.specialaccess;

import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageItemInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.IconDrawableFactory;
import android.util.Log;

import com.android.tv.settings.library.ManagerUtil;
import com.android.tv.settings.library.PreferenceCompat;
import com.android.tv.settings.library.UIUpdateCallback;
import com.android.tv.settings.library.data.PreferenceControllerState;
import com.android.tv.settings.library.util.AbstractPreferenceController;
import com.android.tv.settings.library.util.ResourcesUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * State for managing notification listener permission
 */
public class NotificationAccessState extends PreferenceControllerState {
    private static final String TAG = "NotificationAccess";

    private static final String HEADER_KEY = "header";
    private static final String KEY_NO_SERVICES = "no_services";
    private static final String DEFAULT_PACKAGES_SEPARATOR = ":";
    private ArraySet<String> mDefaultPackages;

    private NotificationManager mNotificationManager;
    private PackageManager mPackageManager;
    private ServiceListing mServiceListing;
    private IconDrawableFactory mIconDrawableFactory;
    private final ArrayMap<String, ComponentName> mComponentNameByKey = new ArrayMap<>();

    public NotificationAccessState(Context context,
            UIUpdateCallback callback) {
        super(context, callback);
    }

    @Override
    public void onAttach() {
        super.onAttach();
        mPackageManager = mContext.getPackageManager();
        mNotificationManager = mContext.getSystemService(NotificationManager.class);
        mIconDrawableFactory = IconDrawableFactory.newInstance(mContext);
    }

    @Override
    public void onCreate(Bundle extras) {
        super.onCreate(extras);
        mServiceListing = new ServiceListing.Builder(mContext)
                .setTag(TAG)
                .setSetting(Settings.Secure.ENABLED_NOTIFICATION_LISTENERS)
                .setIntentAction(NotificationListenerService.SERVICE_INTERFACE)
                .setPermission(android.Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE)
                .setNoun("notification listener")
                .build();
        String packages = ResourcesUtil.getString(mContext, "config_defaultListenerAccessPackages");
        if (!TextUtils.isEmpty(packages)) {
            mDefaultPackages = new ArraySet<String>(packages.split(DEFAULT_PACKAGES_SEPARATOR));
        } else {
            mDefaultPackages = new ArraySet<>();
        }
        mServiceListing.addCallback(this::updateList);
    }

    private void updateList(List<ServiceInfo> services) {
        List<PreferenceCompat> prefCompats = new ArrayList<>();
        final PreferenceCompat header =
                mPreferenceCompatManager.getOrCreatePrefCompat(HEADER_KEY);
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
            final PreferenceCompat pref = new PreferenceCompat(cn.flattenToString());
            pref.setIcon(mIconDrawableFactory.getBadgedIcon(service, service.applicationInfo,
                    UserHandle.getUserId(service.applicationInfo.uid)));
            if (title != null && !title.equals(summary)) {
                pref.setTitle(title.toString());
                pref.setSummary(summary);
            } else {
                pref.setTitle(summary);
            }
            pref.setChecked(mNotificationManager.isNotificationListenerAccessGranted(cn));
            // Prevent the user from removing access from a default notification listener.
            if (mDefaultPackages.contains(cn.getPackageName()) && ManagerUtil.isChecked(pref)) {
                pref.setEnabled(false);
                pref.setSummary(
                        ResourcesUtil.getString(mContext,
                                "default_notification_access_package_summary"));
            }
            pref.setType(PreferenceCompat.TYPE_SWITCH);
            prefCompats.add(pref);
            mComponentNameByKey.put(pref.getKey()[0], cn);
        }
        if (services.isEmpty()) {
            final PreferenceCompat preference = new PreferenceCompat(KEY_NO_SERVICES);
            preference.setTitle(
                    ResourcesUtil.getString(mContext, "no_notification_listeners"));
        }
        mUIUpdateCallback.notifyUpdateAll(getStateIdentifier(), prefCompats);
    }

    @Override
    public boolean onPreferenceChange(String[] key, Object newValue) {
        final ComponentName cn = mComponentNameByKey.get(key[0]);
        if (cn != null) {
            mNotificationManager.setNotificationListenerAccessGranted(cn, (boolean) newValue);
        }
        return true;
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
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        // no-op
    }

    @Override
    public int getStateIdentifier() {
        return ManagerUtil.STATE_NOTIFICATION_ACCESS;
    }

    @Override
    protected List<AbstractPreferenceController> onCreatePreferenceControllers(Context context) {
        return null;
    }

}
