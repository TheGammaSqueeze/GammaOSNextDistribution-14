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

package com.android.tv.settings.library.device.apps;

import static android.content.pm.ApplicationInfo.FLAG_ALLOW_CLEAR_USER_DATA;
import static android.content.pm.ApplicationInfo.FLAG_SYSTEM;

import static com.android.tv.settings.library.ManagerUtil.STATE_APP_MANAGEMENT;
import static com.android.tv.settings.library.device.apps.EnableDisablePreferenceController.KEY_ENABLE_DISABLE;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.IPackageDataObserver;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.hardware.usb.IUsbManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.util.Log;
import android.widget.Toast;

import com.android.tv.settings.library.ManagerUtil;
import com.android.tv.settings.library.PreferenceCompat;
import com.android.tv.settings.library.UIUpdateCallback;
import com.android.tv.settings.library.data.PreferenceControllerState;
import com.android.tv.settings.library.util.AbstractPreferenceController;
import com.android.tv.settings.library.util.ResourcesUtil;

import java.util.ArrayList;
import java.util.List;

/** State to handle app management settings screen. */
public class AppManagementState extends PreferenceControllerState {
    private static final String TAG = "AppManagementState";
    // Intent action implemented by apps that have open source licenses to display under settings
    private static final String VIEW_LICENSES_ACTION = "com.android.tv.settings.VIEW_LICENSES";
    private static final String ARG_PACKAGE_NAME = "packageName";

    private static final String KEY_VERSION = "version";
    private static final String KEY_OPEN = "open";
    private static final String KEY_LICENSES = "licenses";
    private static final String KEY_PERMISSIONS = "permissions";

    // Result code identifiers
    static final int REQUEST_UNINSTALL = 1;
    static final int REQUEST_MANAGE_SPACE = 2;
    static final int REQUEST_UNINSTALL_UPDATES = 3;
    static final int REQUEST_CLEAR_DATA = 4;
    static final int REQUEST_CLEAR_CACHE = 5;
    static final int REQUEST_CLEAR_DEFAULTS = 6;

    private PackageManager mPackageManager;
    private String mPackageName;
    private ApplicationsState mApplicationsState;
    private ApplicationsState.Session mSession;
    private ApplicationsState.AppEntry mEntry;
    private final ApplicationsState.Callbacks mCallbacks = new ApplicationsStateCallbacks();

    private ForceStopPreferenceController mForceStopPreferenceController;
    private UninstallPreferenceController mUninstallPreferenceController;
    private EnableDisablePreferenceController mEnableDisablePreferenceController;
    private AppStoragePreferenceController mAppStoragePreferenceController;
    private ClearDataPreferenceController mClearDataPreferenceController;
    private ClearCachePreferenceController mClearCachePreferenceController;
    private ClearDefaultsPreferenceController mClearDefaultsPreferenceController;
    private NotificationsPreferenceController mNotificationsPreferenceController;
    private final Handler mHandler = new Handler();

    public AppManagementState(Context context,
            UIUpdateCallback callback) {
        super(context, callback);
    }

    public static void prepareArgs(Bundle args, String packageName) {
        args.putString(ARG_PACKAGE_NAME, packageName);
    }

    @Override
    public void onCreate(Bundle extras) {
        mPackageName = extras.getString(ARG_PACKAGE_NAME);

        Activity activity = (Activity) mContext;
        mPackageManager = activity.getPackageManager();
        mApplicationsState = ApplicationsState.getInstance(activity.getApplication());
        mSession = mApplicationsState.newSession(mCallbacks, getLifecycle());
        mEntry = mApplicationsState.getEntry(mPackageName, UserHandle.myUserId());
        super.onCreate(extras);
    }

    @Override
    public void onResume() {
        super.onResume();

        if (mEntry == null) {
            Log.w(TAG, "App not found, trying to bail out");
            mUIUpdateCallback.notifyNavigateBackward(getStateIdentifier());
        }

        if (mClearDefaultsPreferenceController != null) {
            mClearDefaultsPreferenceController.updateAndNotify();
        }
        if (mEnableDisablePreferenceController != null) {
            mEnableDisablePreferenceController.updateAndNotify();
        }
        updatePrefs();
    }


    @Override
    public int getStateIdentifier() {
        return STATE_APP_MANAGEMENT;
    }

    @Override
    protected List<AbstractPreferenceController> onCreatePreferenceControllers(Context context) {
        mForceStopPreferenceController = new ForceStopPreferenceController(
                mContext, mUIUpdateCallback, getStateIdentifier(), mEntry,
                mPreferenceCompatManager);
        mUninstallPreferenceController = new UninstallPreferenceController(
                mContext, mUIUpdateCallback, getStateIdentifier(), mEntry,
                mPreferenceCompatManager);
        mEnableDisablePreferenceController = new EnableDisablePreferenceController(
                mContext, mUIUpdateCallback, getStateIdentifier(), mEntry,
                mPreferenceCompatManager);
        mAppStoragePreferenceController = new AppStoragePreferenceController(
                mContext, mUIUpdateCallback, getStateIdentifier(), mEntry,
                mPreferenceCompatManager);
        mClearDataPreferenceController = new ClearDataPreferenceController(
                mContext, mUIUpdateCallback, getStateIdentifier(), mEntry,
                mPreferenceCompatManager);
        mClearCachePreferenceController = new ClearCachePreferenceController(
                mContext, mUIUpdateCallback, getStateIdentifier(), mEntry,
                mPreferenceCompatManager);
        mClearDefaultsPreferenceController = new ClearDefaultsPreferenceController(
                mContext, mUIUpdateCallback, getStateIdentifier(), mEntry,
                mPreferenceCompatManager);
        mNotificationsPreferenceController = new NotificationsPreferenceController(
                mContext, mUIUpdateCallback, getStateIdentifier(), mEntry,
                mPreferenceCompatManager);
        List<AbstractPreferenceController> list = new ArrayList<>();
        list.add(mForceStopPreferenceController);
        list.add(mUninstallPreferenceController);
        list.add(mEnableDisablePreferenceController);
        list.add(mAppStoragePreferenceController);
        list.add(mClearCachePreferenceController);
        list.add(mClearDataPreferenceController);
        list.add(mClearDefaultsPreferenceController);
        list.add(mNotificationsPreferenceController);
        return list;
    }

    @Override
    public boolean onPreferenceTreeClick(String[] key, boolean status) {
        if (KEY_ENABLE_DISABLE.equals(key[0])) {
            // disable the preference to prevent double clicking
            mEnableDisablePreferenceController.setEnabled(false);
        }
        try {
            return super.onPreferenceTreeClick(key, status);
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, "Could not find activity to launch", e);
            Toast.makeText(mContext,
                    ResourcesUtil.getString(
                            mContext, "device_apps_app_management_not_available"),
                    Toast.LENGTH_SHORT).show();
        }
        return false;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (mEntry == null) {
            return;
        }
        switch (requestCode) {
            case REQUEST_UNINSTALL:
                final int deleteResult = data != null
                        ? data.getIntExtra(Intent.EXTRA_INSTALL_RESULT, 0) : 0;
                if (deleteResult == PackageManager.DELETE_SUCCEEDED) {
                    final int userId = UserHandle.getUserId(mEntry.info.uid);
                    mApplicationsState.removePackage(mPackageName, userId);
                    mUIUpdateCallback.notifyNavigateBackward(getStateIdentifier());
                } else {
                    Log.e(TAG, "Uninstall failed with result " + deleteResult);
                }
                break;
            case REQUEST_MANAGE_SPACE:
                mClearDataPreferenceController.setClearingData(false);
                if (resultCode == Activity.RESULT_OK) {
                    final int userId = UserHandle.getUserId(mEntry.info.uid);
                    mApplicationsState.requestSize(mPackageName, userId);
                } else {
                    Log.w(TAG, "Failed to clear data!");
                }
                break;
            case REQUEST_UNINSTALL_UPDATES:
                mUninstallPreferenceController.updateAndNotify();
                break;
            case REQUEST_CLEAR_DATA:
                if (resultCode == Activity.RESULT_OK) {
                    clearData();
                }
                break;
            case REQUEST_CLEAR_CACHE:
                if (resultCode == Activity.RESULT_OK) {
                    clearCache();
                }
                break;
            case REQUEST_CLEAR_DEFAULTS:
                if (resultCode == Activity.RESULT_OK) {
                    clearDefaults();
                }
                break;
            default:
                break;
        }
    }

    private void clearDefaults() {
        PackageManager packageManager = mContext.getPackageManager();
        packageManager.clearPackagePreferredActivities(mPackageName);
        try {
            final IBinder usbBinder = ServiceManager.getService(Context.USB_SERVICE);
            IUsbManager.Stub.asInterface(usbBinder)
                    .clearDefaults(mPackageName, UserHandle.myUserId());
        } catch (RemoteException e) {
            // Ignore
        }
    }

    private void clearData() {
        if (!clearDataAllowed()) {
            Log.e(TAG, "Attempt to clear data failed. Clear data is disabled for " + mPackageName);
            return;
        }

        mClearDataPreferenceController.setClearingData(true);
        String spaceManagementActivityName = mEntry.info.manageSpaceActivityName;
        if (spaceManagementActivityName != null) {
            if (!ActivityManager.isUserAMonkey()) {
                Intent intent = new Intent(Intent.ACTION_DEFAULT);
                intent.setClassName(mEntry.info.packageName, spaceManagementActivityName);
                ((Activity) mContext).startActivityForResult(intent,
                        ManagerUtil.calculateCompoundCode(getStateIdentifier(),
                                REQUEST_MANAGE_SPACE));
            }
        } else {
            // Disabling clear cache preference while clearing data is in progress. See b/77815256
            // for details.
            mClearCachePreferenceController.setClearingCache(true);
            ActivityManager am = mContext.getSystemService(ActivityManager.class);
            boolean success = am.clearApplicationUserData(
                    mEntry.info.packageName, new IPackageDataObserver.Stub() {
                        public void onRemoveCompleted(
                                final String packageName, final boolean succeeded) {
                            mHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    mClearDataPreferenceController.setClearingData(false);
                                    mClearCachePreferenceController.setClearingCache(false);
                                    dataCleared(succeeded);
                                }
                            });
                        }
                    });
            if (!success) {
                mClearDataPreferenceController.setClearingData(false);
                dataCleared(false);
            }
        }
        mClearDataPreferenceController.updateAndNotify();
    }

    private void dataCleared(boolean succeeded) {
        if (succeeded) {
            final int userId = UserHandle.getUserId(mEntry.info.uid);
            mApplicationsState.requestSize(mPackageName, userId);
        } else {
            Log.w(TAG, "Failed to clear data!");
            mClearDataPreferenceController.update();
        }
    }


    private void clearCache() {
        mClearCachePreferenceController.setClearingCache(true);
        mPackageManager.deleteApplicationCacheFiles(mEntry.info.packageName,
                new IPackageDataObserver.Stub() {
                    public void onRemoveCompleted(final String packageName,
                            final boolean succeeded) {
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                mClearCachePreferenceController.setClearingCache(false);
                                cacheCleared(succeeded);
                            }
                        });
                    }
                });
        mClearCachePreferenceController.update();
    }

    private void cacheCleared(boolean succeeded) {
        if (succeeded) {
            final int userId = UserHandle.getUserId(mEntry.info.uid);
            mApplicationsState.requestSize(mPackageName, userId);
        } else {
            Log.w(TAG, "Failed to clear cache!");
            mClearCachePreferenceController.update();
        }
    }

    private void updatePrefs() {
        // Version
        PreferenceCompat versionPreference = mPreferenceCompatManager
                .getOrCreatePrefCompat(KEY_VERSION);
        if (versionPreference == null) {
            versionPreference.setSelectable(false);
        }
        versionPreference.setTitle(
                ResourcesUtil.getString(mContext, "device_apps_app_management_version",
                        mEntry.getVersion(mContext)));
        versionPreference.setSummary(mPackageName);
        mUIUpdateCallback.notifyUpdate(getStateIdentifier(), versionPreference);

        // Open
        PreferenceCompat openPreference = mPreferenceCompatManager.getOrCreatePrefCompat(KEY_OPEN);
        Intent appLaunchIntent =
                mPackageManager.getLeanbackLaunchIntentForPackage(mEntry.info.packageName);
        if (appLaunchIntent == null) {
            appLaunchIntent = mPackageManager.getLaunchIntentForPackage(mEntry.info.packageName);
        }
        if (appLaunchIntent != null) {
            openPreference.setIntent(appLaunchIntent);
            openPreference.setTitle(
                    ResourcesUtil.getString(mContext, "device_apps_app_management_open"));
            openPreference.setVisible(true);
        } else {
            openPreference.setVisible(false);
        }
        mUIUpdateCallback.notifyUpdate(getStateIdentifier(), openPreference);

        // Force stop
        if (mForceStopPreferenceController != null) {
            mForceStopPreferenceController.setEntry(mEntry);
        }

        // Uninstall
        if (mUninstallPreferenceController != null) {
            mUninstallPreferenceController.setEntry(mEntry);
        }

        // Disable/Enable
        if (mEnableDisablePreferenceController != null) {
            mEnableDisablePreferenceController.setEntry(mEntry);
            mEnableDisablePreferenceController.setEnabled(true);
        }

        // Storage used
        if (mAppStoragePreferenceController != null) {
            mAppStoragePreferenceController.setEntry(mEntry);
        }

        // Clear data
        if (clearDataAllowed() && mClearDataPreferenceController != null) {
            mClearDataPreferenceController.setEntry(mEntry);
        }

        // Clear cache
        if (mClearCachePreferenceController != null) {
            mClearCachePreferenceController.setEntry(mEntry);
        }

        // Clear defaults
        if (mClearDefaultsPreferenceController != null) {
            mClearDefaultsPreferenceController.setEntry(mEntry);
        }

        // Notifications
        if (mNotificationsPreferenceController == null) {
            mNotificationsPreferenceController.setEntry(mEntry);
        }

        // Open Source Licenses
        PreferenceCompat licensesPreference = mPreferenceCompatManager.getOrCreatePrefCompat(
                KEY_LICENSES);
        // Check if app has open source licenses to display
        Intent licenseIntent = new Intent(VIEW_LICENSES_ACTION);
        licenseIntent.setPackage(mEntry.info.packageName);
        ResolveInfo resolveInfo = resolveIntent(licenseIntent);
        if (resolveInfo == null) {
            licensesPreference.setVisible(false);
        } else {
            Intent intent = new Intent(licenseIntent);
            intent.setClassName(resolveInfo.activityInfo.packageName,
                    resolveInfo.activityInfo.name);
            licensesPreference.setIntent(intent);
            licensesPreference.setTitle(ResourcesUtil.getString(mContext,
                    "device_apps_app_management_licenses"));
            licensesPreference.setVisible(true);
        }
        mUIUpdateCallback.notifyUpdate(getStateIdentifier(), licensesPreference);

        // Permissions
        PreferenceCompat permissionsPreference = mPreferenceCompatManager.getOrCreatePrefCompat(
                KEY_PERMISSIONS);
        permissionsPreference.setTitle(ResourcesUtil.getString(mContext,
                "device_apps_app_management_permissions"));
        permissionsPreference.setIntent(new Intent(Intent.ACTION_MANAGE_APP_PERMISSIONS)
                .putExtra(Intent.EXTRA_PACKAGE_NAME, mPackageName));
        mUIUpdateCallback.notifyUpdate(getStateIdentifier(), permissionsPreference);
    }

    private class ApplicationsStateCallbacks implements ApplicationsState.Callbacks {

        @Override
        public void onRunningStateChanged(boolean running) {
            if (mForceStopPreferenceController != null) {
                mForceStopPreferenceController.update();
            }
        }

        @Override
        public void onPackageListChanged() {
            if (mEntry == null || mEntry.info == null) {
                return;
            }
            final int userId = UserHandle.getUserId(mEntry.info.uid);
            mEntry = mApplicationsState.getEntry(mPackageName, userId);
            if (mEntry == null) {
                mUIUpdateCallback.notifyNavigateBackward(getStateIdentifier());
            }
            updatePrefs();
        }

        @Override
        public void onRebuildComplete(ArrayList<ApplicationsState.AppEntry> apps) {
        }

        @Override
        public void onPackageIconChanged() {
        }

        @Override
        public void onPackageSizeChanged(String packageName) {
            if (mAppStoragePreferenceController == null) {
                // Nothing to do here.
                return;
            }
            mAppStoragePreferenceController.updateAndNotify();
            if (mClearCachePreferenceController != null) {
                mClearCachePreferenceController.updateAndNotify();
            }

            if (mClearDataPreferenceController != null) {
                mClearDataPreferenceController.updateAndNotify();
            }
        }

        @Override
        public void onAllSizesComputed() {
            if (mAppStoragePreferenceController == null) {
                // Nothing to do here.
                return;
            }
            mAppStoragePreferenceController.updateAndNotify();
            if (mClearCachePreferenceController != null) {
                mClearCachePreferenceController.updateAndNotify();
            }

            if (mClearDataPreferenceController != null) {
                mClearDataPreferenceController.updateAndNotify();
            }
        }

        @Override
        public void onLauncherInfoChanged() {
            updatePrefs();
        }

        @Override
        public void onLoadEntriesCompleted() {
            mEntry = mApplicationsState.getEntry(mPackageName, UserHandle.myUserId());
            updatePrefs();
            if (mAppStoragePreferenceController == null) {
                // Nothing to do here.
                return;
            }
            mAppStoragePreferenceController.updateAndNotify();
            if (mClearCachePreferenceController != null) {
                mClearCachePreferenceController.updateAndNotify();
            }

            if (mClearDataPreferenceController != null) {
                mClearDataPreferenceController.updateAndNotify();
            }
        }
    }

    private ResolveInfo resolveIntent(Intent intent) {
        List<ResolveInfo> resolveInfos = mPackageManager.queryIntentActivities(intent, 0);
        return (resolveInfos == null || resolveInfos.size() <= 0) ? null : resolveInfos.get(0);
    }

    /**
     * Clearing data can only be disabled for system apps. For all non-system apps it is enabled.
     * System apps disable it explicitly via the android:allowClearUserData tag.
     **/
    private boolean clearDataAllowed() {
        boolean sysApp = (mEntry.info.flags & FLAG_SYSTEM) == FLAG_SYSTEM;
        boolean allowClearData =
                (mEntry.info.flags & FLAG_ALLOW_CLEAR_USER_DATA) == FLAG_ALLOW_CLEAR_USER_DATA;
        return !sysApp || allowClearData;
    }
}
