/*
 * Copyright (C) 2016 The Android Open Source Project
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
 * limitations under the License
 */

package com.example.sampleleanbacklauncher.apps;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentCallbacks2;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.IBinder;
import android.os.Trace;
import android.provider.Settings;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.ArraySet;

import com.example.sampleleanbacklauncher.R;
import com.example.sampleleanbacklauncher.LauncherConstants;
import com.example.sampleleanbacklauncher.notifications.NotificationsContract;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LaunchItemsManager extends Service implements ComponentCallbacks2 {
    private static final String TAG = "LaunchItemsManager";

    public static final String ACTION_APP_LIST_INVALIDATED =
            "LaunchItemsManager.APP_LIST_INVALIDATED";
    public static final String ACTION_GAME_LIST_INVALIDATED =
            "LaunchItemsManager.GAME_LIST_INVALIDATED";
    public static final String ACTION_SETTINGS_LIST_INVALIDATED =
            "LaunchItemsManager.SETTINGS_LIST_INVALIDATED";

    private LaunchItemsDbHelper mDbHelper;
    private final Object mDbHelperLock = new Object();

    private Set<LaunchItem> mAppItems = new ArraySet<>();
    private Set<LaunchItem> mGameItems = new ArraySet<>();
    private Set<LaunchItem> mSettingsItems = new ArraySet<>();

    private Map<ComponentName, Date> mLastOpenMap;
    private Map<ComponentName, Long> mPriorityMap;
    private Map<String, Long> mOobPriority;

    private volatile boolean mAppListValid;
    private volatile boolean mGameListValid;
    private volatile boolean mSettingsListValid;

    private NotificationsLaunchItem mNotifsLaunchItem;
    private Cursor mNotifsCountCursor = null;

    private final PackageListener mPackageListener = new PackageListener();
    private final NetworkListener mNetworkListener = new NetworkListener();
    private final IBinder mLocalBinder = new LocalBinder();

    // We can't query the signal strength directly, so we have to just listen for it all the time.
    private SignalStrength mSignalStrength;
    private final PhoneStateListener mPhoneStateListener = new PhoneStateListener() {
        @Override
        public void onSignalStrengthsChanged(SignalStrength signalStrength) {
            mSignalStrength = signalStrength;
            invalidateSettingsList();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();

        IntentFilter packageIntentFilter = new IntentFilter();
        packageIntentFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        packageIntentFilter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        packageIntentFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        packageIntentFilter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        packageIntentFilter.addDataScheme("package");
        registerReceiver(mPackageListener, packageIntentFilter);

        IntentFilter networkIntentFilter = new IntentFilter();
        networkIntentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        networkIntentFilter.addAction(WifiManager.RSSI_CHANGED_ACTION);
        networkIntentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        networkIntentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(mNetworkListener, networkIntentFilter);

        registerComponentCallbacks(this);

        getSystemService(TelephonyManager.class).listen(mPhoneStateListener,
                PhoneStateListener.LISTEN_SIGNAL_STRENGTHS);

        invalidateAllLists();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        unregisterReceiver(mPackageListener);
        unregisterReceiver(mNetworkListener);

        unregisterComponentCallbacks(this);
        getSystemService(TelephonyManager.class).listen(mPhoneStateListener,
                PhoneStateListener.LISTEN_NONE);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mLocalBinder;
    }

    @WorkerThread
    private void ensureDatabase() {
        synchronized (mDbHelperLock) {
            if (mDbHelper == null) {
                mDbHelper = new LaunchItemsDbHelper(getApplicationContext());
                mLastOpenMap = mDbHelper.readLastOpens();
                mPriorityMap = mDbHelper.readOrderPriorities();
                final String[] oobOrder = getResources().getStringArray(R.array.oob_order);
                mOobPriority = new ArrayMap<>(oobOrder.length);
                for (int i = 0; i < oobOrder.length; i++) {
                    mOobPriority.put(oobOrder[i], (long) oobOrder.length - i);
                }
            }
        }
    }

    @WorkerThread
    private long getPackagePriority(ResolveInfo info) {
        ensureDatabase();

        final ComponentName cn =
                new ComponentName(info.activityInfo.packageName, info.activityInfo.name);

        // Since 1970 was quite a while ago, the last open time should be much larger than
        // the numeric priority
        Long priority = mPriorityMap.get(cn);
        final Date lastOpen = mLastOpenMap.get(cn);
        if (lastOpen != null) {
            priority = lastOpen.getTime();
        }
        if (priority == null) {
            if (mOobPriority.containsKey(info.activityInfo.packageName)) {
                priority = mOobPriority.get(info.activityInfo.packageName);
            } else {
                priority = 0L;
            }
            mDbHelper.writeOrderPriority(cn, priority);
            mPriorityMap.put(cn, priority);
        }

        return priority;
    }

    @WorkerThread
    private void updateAppList() {
        Trace.beginSection("updateAppList");
        try {
            final PackageManager packageManager = getPackageManager();
            List<ResolveInfo> infos = packageManager.queryIntentActivities(
                    new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER),
                    0);
            final Set<LaunchItem> appItems = new ArraySet<>(infos.size());
            for (ResolveInfo info : infos) {

                ApplicationInfo appInfo = info.activityInfo.applicationInfo;
                if ((appInfo.flags & ApplicationInfo.FLAG_IS_GAME) == 0) {
                    appItems.add(new LaunchItem(this, info, getPackagePriority(info)));
                }
            }
            mAppItems = appItems;
            mAppListValid = true;
        } finally {
            Trace.endSection();
        }
    }

    @WorkerThread
    private void updateGamesList() {
        Trace.beginSection("updateGamesList");
        try {
            final PackageManager packageManager = getPackageManager();
            List<ResolveInfo> infos = packageManager.queryIntentActivities(
                    new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER),
                    0);
            final Set<LaunchItem> gameItems = new ArraySet<>(infos.size());
            for (ResolveInfo info : infos) {
                ApplicationInfo appInfo = info.activityInfo.applicationInfo;
                if ((appInfo.flags & ApplicationInfo.FLAG_IS_GAME) != 0) {
                    gameItems.add(new LaunchItem(this, info, getPackagePriority(info)));
                }
            }
            mGameItems = gameItems;
            mGameListValid = true;
        } finally {
            Trace.endSection();
        }
    }

    @WorkerThread
    private void updateSettingsList() {
        Trace.beginSection("updateSettingsList");
        try {
            final PackageManager packageManager = getPackageManager();
            List<ResolveInfo> networkInfos =
                    packageManager.queryIntentActivities(new Intent(Settings.ACTION_WIFI_SETTINGS)
                    .addCategory(LauncherConstants.CATEGORY_LEANBACK_SETTINGS), 0);
            List<ResolveInfo> settingsInfos = packageManager.queryIntentActivities(
                    new Intent(Intent.ACTION_MAIN)
                            .addCategory(LauncherConstants.CATEGORY_LEANBACK_SETTINGS),
                    PackageManager.GET_RESOLVED_FILTER);
            final Set<LaunchItem> settingsItems = new ArraySet<>(settingsInfos.size());
            for (ResolveInfo info : settingsInfos) {
                if (info.activityInfo == null) {
                    continue;
                }
                boolean isNetwork = false;
                for (ResolveInfo networkInfo : networkInfos) {
                    if (networkInfo.activityInfo == null) {
                        continue;
                    }
                    if (TextUtils.equals(networkInfo.activityInfo.name,
                            info.activityInfo.name)
                        && TextUtils.equals(networkInfo.activityInfo.packageName,
                            info.activityInfo.packageName)) {
                        isNetwork = true;
                        break;
                    }
                }
                int priority = info.priority;
                if (isNetwork) {
                    settingsItems.add(new NetworkLaunchItem(this, info, mSignalStrength, priority));
                } else {
                    settingsItems.add(new SettingsLaunchItem(this, info, priority));
                }
            }

            mNotifsLaunchItem = new NotificationsLaunchItem(this);
            mNotifsLaunchItem.setNotificationsCount(getNotifsCount());
            settingsItems.add(mNotifsLaunchItem);

            mSettingsItems = settingsItems;
            mSettingsListValid = true;
        } finally {
            Trace.endSection();
        }
    }

    public void updateNotifsCountCursor(Cursor cursor) {
        mNotifsCountCursor = cursor;
        invalidateSettingsList();
    }

    @WorkerThread
    private int getNotifsCount() {
        if (mNotifsCountCursor != null && mNotifsCountCursor.moveToFirst()) {
            mNotifsCountCursor.moveToFirst();
            int index = mNotifsCountCursor.getColumnIndex(NotificationsContract.COLUMN_COUNT);
            return mNotifsCountCursor.getInt(index);
        }
        return 0;
    }

    private void invalidateAllLists() {
        invalidateAppList();
        invalidateGameList();
        invalidateSettingsList();
    }

    private void invalidateAppList() {
        mAppListValid = false;
        LocalBroadcastManager.getInstance(this)
                .sendBroadcast(new Intent(ACTION_APP_LIST_INVALIDATED));
    }

    private void invalidateGameList() {
        mGameListValid = false;
        LocalBroadcastManager.getInstance(this)
                .sendBroadcast(new Intent(ACTION_GAME_LIST_INVALIDATED));
    }

    private void invalidateSettingsList() {
        mSettingsListValid = false;
        LocalBroadcastManager.getInstance(this)
                .sendBroadcast(new Intent(ACTION_SETTINGS_LIST_INVALIDATED));
    }

    @WorkerThread
    public Set<LaunchItem> getAppItems() {
        if (!mAppListValid) {
            updateAppList();
        }
        return mAppItems;
    }

    @WorkerThread
    public Set<LaunchItem> getGameItems() {
        if (!mGameListValid) {
            updateGamesList();
        }
        return mGameItems;
    }

    @WorkerThread
    public Set<LaunchItem> getSettingsItems() {
        if (!mSettingsListValid) {
            updateSettingsList();
        }
        return mSettingsItems;
    }

    private void invalidateListsForPackage(String packageName) {
        boolean updateApps = false;
        boolean updateGames = false;
        boolean updateSettings = false;

        // Check if the package was previously listed in apps, games or settings
        for (final LaunchItem item : mAppItems) {
            if (TextUtils.equals(item.getIntent().getComponent().getPackageName(), packageName)) {
                updateApps = true;
                break;
            }
        }
        if (!updateApps) {
            // Can't be both an app and a game at the same time
            for (final LaunchItem item : mGameItems) {
                if (TextUtils.equals(item.getIntent().getComponent().getPackageName(),
                        packageName)) {
                    updateGames = true;
                    break;
                }
            }
        }
        for (final LaunchItem item : mSettingsItems) {
            if (TextUtils.equals(item.getIntent().getPackage(), packageName)) {
                updateSettings = true;
                break;
            }
        }

        // Check if the app will be listed in apps, games or settings
        final List<ResolveInfo> leanbackInfos =
                getPackageManager().queryIntentActivities(
                        new Intent(Intent.ACTION_MAIN)
                                .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                                .setPackage(packageName),
                        0);
        if (!leanbackInfos.isEmpty()) {
            ApplicationInfo applicationInfo = leanbackInfos.get(0).activityInfo.applicationInfo;
            if ((applicationInfo.flags & ApplicationInfo.FLAG_IS_GAME) == 0) {
                updateApps = true;
            } else {
                updateGames = true;
            }
        }
        if (!updateSettings) {
            final List<ResolveInfo> settingsInfos =
                    getPackageManager().queryIntentActivities(
                            new Intent(Intent.ACTION_MAIN)
                                    .addCategory(LauncherConstants.CATEGORY_LEANBACK_SETTINGS)
                                    .setPackage(packageName),
                            0);
            if (!settingsInfos.isEmpty()) {
                updateSettings = true;
            }
        }

        if (updateApps) {
            invalidateAppList();
        }
        if (updateGames) {
            invalidateGameList();
        }
        if (updateSettings) {
            invalidateSettingsList();
        }
    }

    public void notifyItemLaunched(LaunchItem item) {
        final Date now = new Date();
        final ComponentName component = item.getIntent().getComponent();
        mLastOpenMap.put(component, now);
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                ensureDatabase();
                if (component != null) {
                    mDbHelper.writeLastOpen(component, now);
                }
                return null;
            }
        }.execute();
        if (mAppItems.contains(item)) {
            invalidateAppList();
        } else if (mGameItems.contains(item)) {
            invalidateGameList();
        }
        // No recency for settings row
    }

    @Override
    public void onTrimMemory(int level) {}

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        invalidateAllLists();
    }

    @Override
    public void onLowMemory() {}

    @Override
    protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        writer.println("App Items");
        if (mAppItems != null) {
            for (final LaunchItem item : mAppItems.toArray(new LaunchItem[mAppItems.size()])) {
                writer.println(item.toDebugString());
            }
        } else {
            writer.println("Null");
        }
        writer.println("Game Items");
        if (mGameItems != null) {
            for (final LaunchItem item : mGameItems.toArray(new LaunchItem[mGameItems.size()])) {
                writer.println(item.toDebugString());
            }
        } else {
            writer.println("Null");
        }
        writer.println("Settings Items");
        if (mSettingsItems != null) {
            for (final LaunchItem item :
                    mSettingsItems.toArray(new LaunchItem[mSettingsItems.size()])) {
                writer.println(item.toDebugString());
            }
        } else {
            writer.println("Null");
        }
    }

    public class LocalBinder extends Binder {
        public LaunchItemsManager getLaunchItemsManager() {
            return LaunchItemsManager.this;
        }
    }

    public class PackageListener extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            final Uri packageUri = Uri.parse(intent.getDataString());
            invalidateListsForPackage(packageUri.getSchemeSpecificPart());
        }
    }

    public class NetworkListener extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            invalidateSettingsList();
        }
    }
}
