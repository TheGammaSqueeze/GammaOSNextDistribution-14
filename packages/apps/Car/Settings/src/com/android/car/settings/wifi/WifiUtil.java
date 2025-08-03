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
 * limitations under the License
 */
package com.android.car.settings.wifi;

import static android.net.wifi.WifiConfiguration.NetworkSelectionStatus.NETWORK_SELECTION_ENABLED;
import static android.os.UserManager.DISALLOW_CONFIG_WIFI;

import static com.android.car.settings.common.PreferenceController.AVAILABLE;
import static com.android.car.settings.common.PreferenceController.AVAILABLE_FOR_VIEWING;
import static com.android.car.settings.common.PreferenceController.UNSUPPORTED_ON_DEVICE;
import static com.android.car.settings.enterprise.ActionDisabledByAdminDialogFragment.DISABLED_BY_ADMIN_CONFIRM_DIALOG_TAG;
import static com.android.car.settings.enterprise.EnterpriseUtils.hasUserRestrictionByDpm;
import static com.android.car.settings.enterprise.EnterpriseUtils.hasUserRestrictionByUm;

import android.annotation.DrawableRes;
import android.annotation.Nullable;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.SimpleClock;
import android.provider.Settings;
import android.text.TextUtils;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.lifecycle.Lifecycle;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.Logger;
import com.android.car.settings.enterprise.EnterpriseUtils;
import com.android.wifitrackerlib.NetworkDetailsTracker;
import com.android.wifitrackerlib.WifiEntry;
import com.android.wifitrackerlib.WifiPickerTracker;

import java.time.Clock;
import java.time.ZoneOffset;
import java.util.regex.Pattern;

/**
 * A collections of util functions for WIFI.
 */
public class WifiUtil {

    private static final Logger LOG = new Logger(WifiUtil.class);

    /** Value that is returned when we fail to connect wifi. */
    public static final int INVALID_NET_ID = -1;
    /** Max age of tracked WifiEntries. */
    private static final long DEFAULT_MAX_SCAN_AGE_MILLIS = 15_000;
    /** Interval between initiating WifiPickerTracker scans. */
    private static final long DEFAULT_SCAN_INTERVAL_MILLIS = 10_000;
    private static final Pattern HEX_PATTERN = Pattern.compile("^[0-9A-F]+$");

    /** Clock used for evaluating the age of WiFi scans */
    private static final Clock ELAPSED_REALTIME_CLOCK = new SimpleClock(ZoneOffset.UTC) {
        @Override
        public long millis() {
            return android.os.SystemClock.elapsedRealtime();
        }
    };

    @DrawableRes
    public static int getIconRes(int state) {
        switch (state) {
            case WifiManager.WIFI_STATE_ENABLING:
            case WifiManager.WIFI_STATE_DISABLED:
                return R.drawable.ic_settings_wifi_disabled;
            default:
                return R.drawable.ic_settings_wifi;
        }
    }

    public static boolean isWifiOn(int state) {
        switch (state) {
            case WifiManager.WIFI_STATE_ENABLING:
            case WifiManager.WIFI_STATE_DISABLED:
                return false;
            default:
                return true;
        }
    }

    /**
     * @return 0 if no proper description can be found.
     */
    @StringRes
    public static Integer getStateDesc(int state) {
        switch (state) {
            case WifiManager.WIFI_STATE_ENABLING:
                return R.string.wifi_starting;
            case WifiManager.WIFI_STATE_DISABLING:
                return R.string.wifi_stopping;
            case WifiManager.WIFI_STATE_DISABLED:
                return R.string.wifi_disabled;
            default:
                return 0;
        }
    }

    /**
     * Returns {@code true} if wifi is available on this device.
     */
    public static boolean isWifiAvailable(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_WIFI);
    }

    /**
     * Returns {@code true} if configuring wifi is allowed by user manager.
     */
    public static boolean isConfigWifiRestrictedByUm(Context context) {
        return hasUserRestrictionByUm(context, DISALLOW_CONFIG_WIFI);
    }

    /**
     * Returns {@code true} if configuring wifi is allowed by device policy manager.
     */
    public static boolean isConfigWifiRestrictedByDpm(Context context) {
        return hasUserRestrictionByDpm(context, DISALLOW_CONFIG_WIFI);
    }

    /**
     * Returns Preference's availability status.
     */
    public static int getAvailabilityStatus(Context context) {
        if (!isWifiAvailable(context)) {
            return UNSUPPORTED_ON_DEVICE;
        }
        if (isConfigWifiRestrictedByUm(context)
                || isConfigWifiRestrictedByDpm(context)) {
            return AVAILABLE_FOR_VIEWING;
        }
        return AVAILABLE;
    }

    /**
     * Gets a unique key for a {@link WifiEntry}.
     */
    public static String getKey(WifiEntry wifiEntry) {
        return String.valueOf(wifiEntry.hashCode());
    }

    /**
     * This method is a stripped and negated version of WifiConfigStore.canModifyNetwork.
     *
     * @param context Context of caller
     * @param config  The WiFi config.
     * @return {@code true} if Settings cannot modify the config due to lockDown.
     */
    public static boolean isNetworkLockedDown(Context context, WifiConfiguration config) {
        if (config == null) {
            return false;
        }

        final DevicePolicyManager dpm =
                (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        final PackageManager pm = context.getPackageManager();

        // Check if device has DPM capability. If it has and dpm is still null, then we
        // treat this case with suspicion and bail out.
        if (pm.hasSystemFeature(PackageManager.FEATURE_DEVICE_ADMIN) && dpm == null) {
            return true;
        }

        boolean isConfigEligibleForLockdown = false;
        if (dpm != null) {
            final ComponentName deviceOwner = dpm.getDeviceOwnerComponentOnAnyUser();
            if (deviceOwner != null) {
                final int deviceOwnerUserId = dpm.getDeviceOwnerUserId();
                try {
                    final int deviceOwnerUid = pm.getPackageUidAsUser(deviceOwner.getPackageName(),
                            deviceOwnerUserId);
                    isConfigEligibleForLockdown = deviceOwnerUid == config.creatorUid;
                } catch (PackageManager.NameNotFoundException e) {
                    // don't care
                }
            }
        }
        if (!isConfigEligibleForLockdown) {
            return false;
        }

        final ContentResolver resolver = context.getContentResolver();
        final boolean isLockdownFeatureEnabled = Settings.Global.getInt(resolver,
                Settings.Global.WIFI_DEVICE_OWNER_CONFIGS_LOCKDOWN, 0) != 0;
        return isLockdownFeatureEnabled;
    }

    /**
     * Returns {@code true} if the network security type doesn't require authentication.
     */
    public static boolean isOpenNetwork(int security) {
        return security == WifiEntry.SECURITY_NONE || security == WifiEntry.SECURITY_OWE;
    }

    /**
     * Returns {@code true} if the provided NetworkCapabilities indicate a captive portal network.
     */
    public static boolean canSignIntoNetwork(NetworkCapabilities capabilities) {
        return (capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL));
    }

    /**
     * Attempts to connect to a specified Wi-Fi entry.
     *
     * @param listener for callbacks on success or failure of connection attempt (can be null)
     */
    public static void connectToWifiEntry(Context context, String ssid, int security,
            String password, boolean hidden, @Nullable WifiManager.ActionListener listener) {
        WifiManager wifiManager = context.getSystemService(WifiManager.class);
        WifiConfiguration wifiConfig = getWifiConfig(ssid, security, password, hidden);
        wifiManager.connect(wifiConfig, listener);
    }

    private static WifiConfiguration getWifiConfig(String ssid, int security,
            String password, boolean hidden) {
        WifiConfiguration wifiConfig = new WifiConfiguration();
        wifiConfig.SSID = String.format("\"%s\"", ssid);
        wifiConfig.hiddenSSID = hidden;

        return finishWifiConfig(wifiConfig, security, password);
    }

    /** Similar to above, but uses WifiEntry to get additional relevant information. */
    public static WifiConfiguration getWifiConfig(@NonNull WifiEntry wifiEntry,
            String password) {
        WifiConfiguration wifiConfig = new WifiConfiguration();
        if (wifiEntry.getWifiConfiguration() == null) {
            wifiConfig.SSID = "\"" + wifiEntry.getSsid() + "\"";
        } else {
            wifiConfig.networkId = wifiEntry.getWifiConfiguration().networkId;
            wifiConfig.hiddenSSID = wifiEntry.getWifiConfiguration().hiddenSSID;
        }

        return finishWifiConfig(wifiConfig, wifiEntry.getSecurity(), password);
    }

    private static WifiConfiguration finishWifiConfig(WifiConfiguration wifiConfig, int security,
            String password) {
        switch (security) {
            case WifiEntry.SECURITY_NONE:
                wifiConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_OPEN);
                break;
            case WifiEntry.SECURITY_WEP:
                wifiConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_WEP);
                if (!TextUtils.isEmpty(password)) {
                    int length = password.length();
                    // WEP-40, WEP-104, and 256-bit WEP (WEP-232?)
                    if ((length == 10 || length == 26 || length == 58)
                            && password.matches("[0-9A-Fa-f]*")) {
                        wifiConfig.wepKeys[0] = password;
                    } else {
                        wifiConfig.wepKeys[0] = '"' + password + '"';
                    }
                }
                break;
            case WifiEntry.SECURITY_PSK:
                wifiConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_PSK);
                if (!TextUtils.isEmpty(password)) {
                    if (password.matches("[0-9A-Fa-f]{64}")) {
                        wifiConfig.preSharedKey = password;
                    } else {
                        wifiConfig.preSharedKey = '"' + password + '"';
                    }
                }
                break;
            case WifiEntry.SECURITY_EAP:
            case WifiEntry.SECURITY_EAP_SUITE_B:
                if (security == WifiEntry.SECURITY_EAP_SUITE_B) {
                    // allowedSuiteBCiphers will be set according to certificate type
                    wifiConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_EAP_SUITE_B);
                } else {
                    wifiConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_EAP);
                }
                if (!TextUtils.isEmpty(password)) {
                    wifiConfig.enterpriseConfig.setPassword(password);
                }
                break;
            case WifiEntry.SECURITY_SAE:
                wifiConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_SAE);
                if (!TextUtils.isEmpty(password)) {
                    wifiConfig.preSharedKey = '"' + password + '"';
                }
                break;
            case WifiEntry.SECURITY_OWE:
                wifiConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_OWE);
                break;
            default:
                throw new IllegalArgumentException("unknown security type " + security);
        }
        return wifiConfig;
    }

    /** Returns {@code true} if the Wi-Fi entry is connected or connecting. */
    public static boolean isWifiEntryConnectedOrConnecting(WifiEntry wifiEntry) {
        if (wifiEntry == null) {
            return false;
        }
        return wifiEntry.getConnectedState() != WifiEntry.CONNECTED_STATE_DISCONNECTED;
    }

    /** Returns {@code true} if the Wi-Fi entry was disabled due to the wrong password. */
    public static boolean isWifiEntryDisabledByWrongPassword(WifiEntry wifiEntry) {
        WifiConfiguration config = wifiEntry.getWifiConfiguration();
        if (config == null) {
            return false;
        }
        WifiConfiguration.NetworkSelectionStatus networkStatus =
                config.getNetworkSelectionStatus();
        if (networkStatus == null
                || networkStatus.getNetworkSelectionStatus() == NETWORK_SELECTION_ENABLED) {
            return false;
        }
        return networkStatus.getNetworkSelectionDisableReason()
                == WifiConfiguration.NetworkSelectionStatus.DISABLED_BY_WRONG_PASSWORD;
    }

    private static boolean isHexString(String password) {
        return HEX_PATTERN.matcher(password).matches();
    }

    /**
     * Gets the security value from a ScanResult.
     *
     * @return related security value based on {@link WifiEntry}
     */
    public static int getWifiEntrySecurity(ScanResult result) {
        if (result.capabilities.contains("WEP")) {
            return WifiEntry.SECURITY_WEP;
        } else if (result.capabilities.contains("SAE")) {
            return WifiEntry.SECURITY_SAE;
        } else if (result.capabilities.contains("PSK")) {
            return WifiEntry.SECURITY_PSK;
        } else if (result.capabilities.contains("EAP_SUITE_B_192")) {
            return WifiEntry.SECURITY_EAP_SUITE_B;
        } else if (result.capabilities.contains("EAP")) {
            return WifiEntry.SECURITY_EAP;
        } else if (result.capabilities.contains("OWE")) {
            return WifiEntry.SECURITY_OWE;
        }
        return WifiEntry.SECURITY_NONE;
    }

    /**
     * Creates an instance of WifiPickerTracker using the default MAX_SCAN_AGE and
     * SCAN_INTERVAL values.
     */
    public static WifiPickerTracker createWifiPickerTracker(
            Lifecycle lifecycle, Context context,
            Handler mainHandler, Handler workerHandler,
            WifiPickerTracker.WifiPickerTrackerCallback listener) {
        return createWifiPickerTracker(lifecycle, context, mainHandler, workerHandler,
                DEFAULT_MAX_SCAN_AGE_MILLIS, DEFAULT_SCAN_INTERVAL_MILLIS, listener);
    }

    /**
     * Creates an instance of WifiPickerTracker.
     */
    public static WifiPickerTracker createWifiPickerTracker(
            Lifecycle lifecycle, Context context,
            Handler mainHandler, Handler workerHandler,
            long maxScanAgeMillis, long scanIntervalMillis,
            WifiPickerTracker.WifiPickerTrackerCallback listener) {
        return new WifiPickerTracker(
                lifecycle, context,
                context.getSystemService(WifiManager.class),
                context.getSystemService(ConnectivityManager.class),
                mainHandler, workerHandler, ELAPSED_REALTIME_CLOCK,
                maxScanAgeMillis, scanIntervalMillis,
                listener);
    }

    /**
     * Creates an instance of NetworkDetailsTracker using the default MAX_SCAN_AGE and
     * SCAN_INTERVAL values.
     */
    public static NetworkDetailsTracker createNetworkDetailsTracker(
            Lifecycle lifecycle, Context context,
            Handler mainHandler, Handler workerHandler,
            String key) {
        return createNetworkDetailsTracker(lifecycle, context, mainHandler, workerHandler,
                DEFAULT_MAX_SCAN_AGE_MILLIS, DEFAULT_SCAN_INTERVAL_MILLIS, key);
    }

    /**
     * Creates an instance of NetworkDetailsTracker.
     */
    public static NetworkDetailsTracker createNetworkDetailsTracker(
            Lifecycle lifecycle, Context context,
            Handler mainHandler, Handler workerHandler,
            long maxScanAgeMillis, long scanIntervalMillis,
            String key) {
        return NetworkDetailsTracker.createNetworkDetailsTracker(
                lifecycle, context,
                context.getSystemService(WifiManager.class),
                context.getSystemService(ConnectivityManager.class),
                mainHandler, workerHandler, ELAPSED_REALTIME_CLOCK,
                maxScanAgeMillis, scanIntervalMillis,
                key);
    }

    /**
     * Shows {@code ActionDisabledByAdminDialog} when the action is disallowed by
     * a device owner or a profile owner. Otherwise, a {@code Toast} will be shown to inform the
     * user that the action is disabled.
     */
    // TODO(b/186905050): add unit tests for this class and {@code PreferenceController} that uses
    // this method.
    public static void runClickableWhileDisabled(Context context, String restriction,
            FragmentController fragmentController) {
        if (hasUserRestrictionByDpm(context, restriction)) {
            showActionDisabledByAdminDialog(context, restriction, fragmentController);
        } else {
            Toast.makeText(context, context.getString(R.string.action_unavailable),
                    Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Shows {@code ActionDisabledByAdminDialog} when the action is disallowed with
     * {@code DISALLOW_CONFIG_WIFI} restriction by DevicePolicyManager. Otherwise, a {@code Toast}
     * will be shown to inform the user that the action is disabled.
     */
    public static void runClickableWhileDisabled(Context context,
            FragmentController fragmentController) {
        runClickableWhileDisabled(context, DISALLOW_CONFIG_WIFI, fragmentController);
    }

    /**
     * Shows ActionDisabledByAdminDialog when there is user restriction set by device policy
     * manager.
     */
    // TODO(b/186905050): add unit tests for this class and {@code PreferenceController} that uses
    // this method.
    public static void showActionDisabledByAdminDialog(Context context, String restriction,
            FragmentController fragmentController) {
        fragmentController.showDialog(
                EnterpriseUtils.getActionDisabledByAdminDialog(context, restriction),
                DISABLED_BY_ADMIN_CONFIRM_DIALOG_TAG);
    }
}
