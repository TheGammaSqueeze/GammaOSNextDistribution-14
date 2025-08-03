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
 * limitations under the License.
 */

package com.android.server.wifi;

import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE;
import static android.content.pm.PackageManager.FEATURE_DEVICE_ADMIN;

import android.annotation.NonNull;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.database.ContentObserver;
import android.net.TrafficStats;
import android.net.Uri;
import android.net.ip.IpClientCallbacks;
import android.net.ip.IpClientUtil;
import android.os.PersistableBundle;
import android.os.Process;
import android.os.UserHandle;
import android.os.WorkSource;
import android.provider.Settings;
import android.security.KeyChain;
import android.telephony.CarrierConfigManager;
import android.util.Log;
import android.widget.Toast;

import com.android.modules.utils.build.SdkLevel;
import com.android.wifi.resources.R;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * This class allows overriding objects with mocks to write unit tests
 */
public class FrameworkFacade {
    public static final String TAG = "FrameworkFacade";

    private ContentResolver mContentResolver = null;
    private CarrierConfigManager mCarrierConfigManager = null;
    private ActivityManager mActivityManager = null;

    // verbose logging controlled by user
    private static final int VERBOSE_LOGGING_ALWAYS_ON_LEVEL_NONE = 0;
    // verbose logging on by default for userdebug
    private static final int VERBOSE_LOGGING_ALWAYS_ON_LEVEL_USERDEBUG = 1;
    // verbose logging on by default for all builds -->
    private static final int VERBOSE_LOGGING_ALWAYS_ON_LEVEL_ALL = 2;

    private ContentResolver getContentResolver(Context context) {
        if (mContentResolver == null) {
            mContentResolver = context.getContentResolver();
        }
        return mContentResolver;
    }

    private CarrierConfigManager getCarrierConfigManager(Context context) {
        if (mCarrierConfigManager == null) {
            mCarrierConfigManager =
                (CarrierConfigManager) context.getSystemService(Context.CARRIER_CONFIG_SERVICE);
        }
        return mCarrierConfigManager;
    }

    private ActivityManager getActivityManager(Context context) {
        if (mActivityManager == null) {
            mActivityManager =
                (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        }
        return mActivityManager;
    }

    /**
     * Mockable getter for user context from ActivityManager
     */
    public Context getUserContext(Context context) {
        return context.createContextAsUser(UserHandle.of(ActivityManager.getCurrentUser()), 0);
    }

    /**
     * Mockable setter for Settings.Global
     */
    public boolean setIntegerSetting(ContentResolver contentResolver, String name, int value) {
        return Settings.Global.putInt(contentResolver, name, value);
    }

    /**
     * Mockable getter for Settings.Global
     */
    public int getIntegerSetting(ContentResolver contentResolver, String name, int def) {
        return Settings.Global.getInt(contentResolver, name, def);
    }

    public boolean setIntegerSetting(Context context, String name, int def) {
        return Settings.Global.putInt(getContentResolver(context), name, def);
    }

    public int getIntegerSetting(Context context, String name, int def) {
        return Settings.Global.getInt(getContentResolver(context), name, def);
    }

    /**
     * Mockable getter for Settings.Global.getInt with SettingNotFoundException
     */
    public int getIntegerSetting(ContentResolver contentResolver, String name)
            throws Settings.SettingNotFoundException {
        return Settings.Global.getInt(contentResolver, name);
    }

    public long getLongSetting(Context context, String name, long def) {
        return Settings.Global.getLong(getContentResolver(context), name, def);
    }

    public boolean setStringSetting(Context context, String name, String def) {
        return Settings.Global.putString(getContentResolver(context), name, def);
    }

    public String getStringSetting(Context context, String name) {
        return Settings.Global.getString(getContentResolver(context), name);
    }

    /**
     * Mockable facade to Settings.Secure.getInt(.).
     */
    public int getSecureIntegerSetting(Context context, String name, int def) {
        return Settings.Secure.getInt(context.getContentResolver(), name, def);
    }

    /**
     * Mockable facade to Settings.Secure.putInt(.).
     */
    public boolean setSecureIntegerSetting(Context context, String name, int def) {
        return Settings.Secure.putInt(context.getContentResolver(), name, def);
    }

    /**
     * Mockable facade to Settings.Secure.getString(.).
     */
    public String getSecureStringSetting(Context context, String name) {
        return Settings.Secure.getString(context.getContentResolver(), name);
    }

    /**
     * Returns whether the device is in NIAP mode or not.
     */
    public boolean isNiapModeOn(Context context) {
        boolean isNiapModeEnabled = context.getResources().getBoolean(
                R.bool.config_wifiNiapModeEnabled);
        if (isNiapModeEnabled) return true;

        DevicePolicyManager devicePolicyManager =
                context.getSystemService(DevicePolicyManager.class);
        if (devicePolicyManager == null
                && context.getPackageManager().hasSystemFeature(FEATURE_DEVICE_ADMIN)) {
            Log.e(TAG, "Error retrieving DPM service");
        }
        if (devicePolicyManager == null) return false;
        return devicePolicyManager.isCommonCriteriaModeEnabled(null);
    }

    /**
     * Helper method for classes to register a ContentObserver
     * {@see ContentResolver#registerContentObserver(Uri,boolean,ContentObserver)}.
     *
     * @param context
     * @param uri
     * @param notifyForDescendants
     * @param contentObserver
     */
    public void registerContentObserver(Context context, Uri uri,
            boolean notifyForDescendants, ContentObserver contentObserver) {
        getContentResolver(context).registerContentObserver(uri, notifyForDescendants,
                contentObserver);
    }

    /**
     * Helper method for classes to unregister a ContentObserver
     * {@see ContentResolver#unregisterContentObserver(ContentObserver)}.
     *
     * @param context
     * @param contentObserver
     */
    public void unregisterContentObserver(Context context, ContentObserver contentObserver) {
        getContentResolver(context).unregisterContentObserver(contentObserver);
    }

    public PendingIntent getBroadcast(Context context, int requestCode, Intent intent, int flags) {
        return PendingIntent.getBroadcast(context, requestCode, intent, flags);
    }

    /**
     * Wrapper for {@link PendingIntent#getActivity} using the current foreground user.
     */
    public PendingIntent getActivity(Context context, int requestCode, Intent intent, int flags) {
        return PendingIntent.getActivity(context.createContextAsUser(UserHandle.CURRENT, 0),
                requestCode, intent, flags);
    }

    public boolean getConfigWiFiDisableInECBM(Context context) {
        CarrierConfigManager configManager = getCarrierConfigManager(context);
        if (configManager == null) {
            return false;
        }
        PersistableBundle bundle = configManager.getConfig();
        if (bundle == null) {
            return false;
        }
        return bundle.getBoolean(CarrierConfigManager.KEY_CONFIG_WIFI_DISABLE_IN_ECBM);
    }

    public long getTxPackets(String iface) {
        return TrafficStats.getTxPackets(iface);
    }

    public long getRxPackets(String iface) {
        return TrafficStats.getRxPackets(iface);
    }

    public long getTxBytes(String iface) {
        return SdkLevel.isAtLeastS() ? TrafficStats.getTxBytes(iface) : 0;
    }

    public long getRxBytes(String iface) {
        return SdkLevel.isAtLeastS() ? TrafficStats.getRxBytes(iface) : 0;
    }

    /**
     * Request a new IpClient to be created asynchronously.
     * @param context Context to use for creation.
     * @param iface Interface the client should act on.
     * @param callback IpClient event callbacks.
     */
    public void makeIpClient(Context context, String iface, IpClientCallbacks callback) {
        IpClientUtil.makeIpClient(context, iface, callback);
    }

    /**
     * Check if the provided uid is the app in the foreground.
     * @param uid the uid to check
     * @return true if the app is in the foreground, false otherwise
     */
    public boolean isAppForeground(Context context, int uid) {
        ActivityManager activityManager = getActivityManager(context);
        if (activityManager == null) return false;
        return activityManager.getUidImportance(uid) <= IMPORTANCE_VISIBLE;
    }

    /**
     * Create a new instance of {@link Notification.Builder}.
     * @param context reference to a Context
     * @param channelId ID of the notification channel
     * @return an instance of Notification.Builder
     */
    public Notification.Builder makeNotificationBuilder(Context context, String channelId) {
        return new Notification.Builder(context, channelId);
    }

    /**
     * Starts supplicant
     */
    public boolean startSupplicant() {
        try {
            SupplicantManager.start();
            return true;
        } catch (NoSuchElementException e) {
            return false;
        }
    }

    /**
     * Stops supplicant
     */
    public void stopSupplicant() {
        SupplicantManager.stop();
    }

    /**
     * Create a new instance of {@link AlertDialog.Builder}.
     * @param context reference to a Context
     * @return an instance of AlertDialog.Builder
     * @deprecated Use {@link WifiDialogManager#createSimpleDialog} instead, or create another
     *             dialog type in WifiDialogManager.
     */
    public AlertDialog.Builder makeAlertDialogBuilder(Context context) {
        boolean isDarkTheme = (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        return new AlertDialog.Builder(context, isDarkTheme
                ? android.R.style.Theme_DeviceDefault_Dialog_Alert : 0);
    }

    /**
     * Show a toast message
     * @param context reference to a Context
     * @param text the message to display
     */
    public void showToast(Context context, String text) {
        Toast toast = Toast.makeText(context, text, Toast.LENGTH_SHORT);
        toast.show();
    }

    /**
     * Wrapper for {@link TrafficStats#getMobileTxBytes}.
     */
    public long getMobileTxBytes() {
        return TrafficStats.getMobileTxBytes();
    }

    /**
     * Wrapper for {@link TrafficStats#getMobileRxBytes}.
     */
    public long getMobileRxBytes() {
        return TrafficStats.getMobileRxBytes();
    }

    /**
     * Wrapper for {@link TrafficStats#getTotalTxBytes}.
     */
    public long getTotalTxBytes() {
        return TrafficStats.getTotalTxBytes();
    }

    /**
     * Wrapper for {@link TrafficStats#getTotalRxBytes}.
     */
    public long getTotalRxBytes() {
        return TrafficStats.getTotalRxBytes();
    }

    private String mSettingsPackageName;

    /**
     * @return Get settings package name.
     */
    public String getSettingsPackageName(@NonNull Context context) {
        if (mSettingsPackageName != null) return mSettingsPackageName;

        Intent intent = new Intent(Settings.ACTION_WIFI_SETTINGS);
        List<ResolveInfo> resolveInfos = context.getPackageManager().queryIntentActivitiesAsUser(
                intent, PackageManager.MATCH_SYSTEM_ONLY | PackageManager.MATCH_DEFAULT_ONLY,
                UserHandle.of(ActivityManager.getCurrentUser()));
        if (resolveInfos == null || resolveInfos.isEmpty()) {
            Log.e(TAG, "Failed to resolve wifi settings activity");
            return null;
        }
        // Pick the first one if there are more than 1 since the list is ordered from best to worst.
        mSettingsPackageName = resolveInfos.get(0).activityInfo.packageName;
        return mSettingsPackageName;
    }

    /**
     * @return Get a worksource to blame settings apps.
     */
    public WorkSource getSettingsWorkSource(Context context) {
        String settingsPackageName = getSettingsPackageName(context);
        if (settingsPackageName == null) return new WorkSource(Process.SYSTEM_UID);
        return new WorkSource(Process.SYSTEM_UID, settingsPackageName);
    }

    /**
     * Returns whether a KeyChain key is granted to the WiFi stack.
     */
    public boolean hasWifiKeyGrantAsUser(Context context, UserHandle user, String alias) {
        return KeyChain.hasWifiKeyGrantAsUser(context, user, alias);
    }

    /**
     * Returns grant string for a given KeyChain alias or null if key not granted.
     */
    public String getWifiKeyGrantAsUser(Context context, UserHandle user, String alias) {
        if (!SdkLevel.isAtLeastS()) {
            return null;
        }
        return KeyChain.getWifiKeyGrantAsUser(context, user, alias);
    }

    /**
     * Check if the request comes from foreground app/service.
     * @param context Application context
     * @param requestorPackageName requestor package name
     * @return true if the requestor is foreground app/service.
     */
    public boolean isRequestFromForegroundAppOrService(Context context,
            @NonNull String requestorPackageName) {
        ActivityManager activityManager = getActivityManager(context);
        if (activityManager == null) return false;
        try {
            return activityManager.getPackageImportance(requestorPackageName)
                    <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE;
        } catch (SecurityException e) {
            Log.e(TAG, "Failed to check the app state", e);
            return false;
        }
    }

    /**
     * Check if the request comes from foreground app.
     * @param context Application context
     * @param requestorPackageName requestor package name
     * @return true if requestor is foreground app.
     */
    public boolean isRequestFromForegroundApp(Context context,
            @NonNull String requestorPackageName) {
        ActivityManager activityManager = getActivityManager(context);
        if (activityManager == null) return false;
        try {
            return activityManager.getPackageImportance(requestorPackageName)
                    <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
        } catch (SecurityException e) {
            Log.e(TAG, "Failed to check the app state", e);
            return false;
        }
    }

    /**
     * Check if the verbose always on is enabled
     * @param alwaysOnLevel verbose logging always on level
     * @param buildProperties build property of current build
     * @return true if verbose always on is enabled on current build
     */
    public boolean isVerboseLoggingAlwaysOn(int alwaysOnLevel,
            @NonNull BuildProperties buildProperties) {
        switch (alwaysOnLevel) {
            // If the overlay setting enabled for all builds
            case VERBOSE_LOGGING_ALWAYS_ON_LEVEL_ALL:
                return true;
            //If the overlay setting enabled for userdebug builds only
            case VERBOSE_LOGGING_ALWAYS_ON_LEVEL_USERDEBUG:
                // If it is a userdebug build
                if (buildProperties.isUserdebugBuild()) return true;
                break;
            case VERBOSE_LOGGING_ALWAYS_ON_LEVEL_NONE:
                // nothing
                break;
            default:
                Log.e(TAG, "Unrecognized config_wifiVerboseLoggingAlwaysOnLevel " + alwaysOnLevel);
                break;
        }
        return false;
    }

    /**
     * Return the (displayable) application name corresponding to the (uid, packageName).
     */
    public @NonNull CharSequence getAppName(Context context, @NonNull String packageName, int uid) {
        ApplicationInfo applicationInfo = null;
        try {
            applicationInfo = context.getPackageManager().getApplicationInfoAsUser(
                    packageName, 0, UserHandle.getUserHandleForUid(uid));
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Failed to find app name for " + packageName);
            return "";
        }
        CharSequence appName = context.getPackageManager().getApplicationLabel(applicationInfo);
        return (appName != null) ? appName : "";
    }
}
