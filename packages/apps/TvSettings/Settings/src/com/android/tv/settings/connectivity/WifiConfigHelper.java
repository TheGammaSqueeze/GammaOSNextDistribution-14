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

package com.android.tv.settings.connectivity;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiConfiguration.AuthAlgorithm;
import android.net.wifi.WifiConfiguration.KeyMgmt;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import com.android.tv.settings.library.network.AccessPoint;
import com.android.tv.settings.R;
import com.android.tv.settings.connectivity.util.WifiSecurityUtil;
import com.android.wifitrackerlib.WifiEntry;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper class that deals with Wi-fi configuration.
 */
public final class WifiConfigHelper {

    private static final String TAG = "WifiConfigHelper";
    private static final boolean DEBUG = false;

    // Allows underscore char to supports proxies that do not
    // follow the spec
    private static final String HC = "a-zA-Z0-9\\_";

    // Matches blank input, ips, and domain names
    private static final String HOSTNAME_REGEXP =
            "^$|^[" + HC + "]+(\\-[" + HC + "]+)*(\\.[" + HC + "]+(\\-[" + HC + "]+)*)*$";
    private static final Pattern HOSTNAME_PATTERN;
    private static final String EXCLUSION_REGEXP =
            "$|^(\\*)?\\.?[" + HC + "]+(\\-[" + HC + "]+)*(\\.[" + HC + "]+(\\-[" + HC + "]+)*)*$";
    private static final Pattern EXCLUSION_PATTERN;

    private static final String BYPASS_PROXY_EXCLUDE_REGEX =
            "[a-zA-Z0-9*]+(\\-[a-zA-Z0-9*]+)*(\\.[a-zA-Z0-9*]+(\\-[a-zA-Z0-9*]+)*)*";
    private static final String BYPASS_PROXY_EXCLUDE_LIST_REGEXP = "^$|^"
            + BYPASS_PROXY_EXCLUDE_REGEX + "(," + BYPASS_PROXY_EXCLUDE_REGEX + ")*$";
    private static final Pattern BYPASS_PROXY_EXCLUSION_PATTERN;

    static {
        HOSTNAME_PATTERN = Pattern.compile(HOSTNAME_REGEXP);
        EXCLUSION_PATTERN = Pattern.compile(EXCLUSION_REGEXP);
        BYPASS_PROXY_EXCLUSION_PATTERN = Pattern.compile(BYPASS_PROXY_EXCLUDE_LIST_REGEXP);
    }

    private WifiConfigHelper() {
    }

    /**
     * Set configuration ssid.
     *
     * @param config configuration
     * @param ssid   network ssid
     */
    public static void setConfigSsid(WifiConfiguration config, String ssid) {
        config.SSID = AccessPoint.convertToQuotedString(ssid);
    }

    /**
     * Set configuration key managment by security.
     */
    public static void setConfigKeyManagementBySecurity(
            WifiConfiguration config, int security) {
        switch (security) {
            case WifiEntry.SECURITY_NONE:
                config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_OPEN);
                break;
            case WifiEntry.SECURITY_WEP:
                config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_WEP);
                break;
            case WifiEntry.SECURITY_PSK:
                config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_PSK);
                break;
            case WifiEntry.SECURITY_EAP:
                config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_EAP);
                break;
            case WifiEntry.SECURITY_EAP_WPA3_ENTERPRISE:
                config.setSecurityParams(
                        WifiConfiguration.SECURITY_TYPE_EAP_WPA3_ENTERPRISE);
                break;
            case WifiEntry.SECURITY_EAP_SUITE_B:
                // allowedSuiteBCiphers will be set according to certificate type
                config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_EAP_SUITE_B);
                break;
            case WifiEntry.SECURITY_SAE:
                config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_SAE);
                break;
            case WifiEntry.SECURITY_OWE:
                config.setSecurityParams(WifiConfiguration.SECURITY_TYPE_OWE);
                break;
        }
    }

    /**
     * validate syntax of hostname and port entries
     *
     * @param hostname host name to be used
     * @param port port to be used
     * @param exclList what should be accepted as input
     * @return 0 on success, string resource ID on failure
     */
    public static int validate(String hostname, String port, String exclList) {
        return validate(hostname, port, exclList, false);
    }

    /**
     * validate syntax of hostname and port entries
     *
     * @param hostname host name to be used
     * @param port port to be used
     * @param exclList what should be accepted as input
     * @param forProxyCheck if extra check for bypass proxy should be done
     * @return 0 on success, string resource ID on failure
     */
    public static int validate(String hostname, String port, String exclList,
                               boolean forProxyCheck) {
        if (DEBUG) {
            Log.i(TAG, "validate, hostname: " + hostname + ", for proxy=" + forProxyCheck);
        }
        Matcher match = HOSTNAME_PATTERN.matcher(hostname);
        String[] exclListArray = exclList.split(",");

        if (!match.matches()) return R.string.proxy_error_invalid_host;

        for (String excl : exclListArray) {
            Matcher m;
            if (forProxyCheck) {
                m = BYPASS_PROXY_EXCLUSION_PATTERN.matcher(excl);
            } else {
                m = EXCLUSION_PATTERN.matcher(excl);
            }
            if (!m.matches()) {
                return R.string.proxy_error_invalid_exclusion_list;
            }
        }

        if (hostname.length() > 0 && port.length() == 0) {
            return R.string.proxy_error_empty_port;
        }

        if (port.length() > 0) {
            if (hostname.length() == 0) {
                return R.string.proxy_error_empty_host_set_port;
            }
            int portVal = -1;
            try {
                portVal = Integer.parseInt(port);
            } catch (NumberFormatException ex) {
                return R.string.proxy_error_invalid_port;
            }
            if (portVal <= 0 || portVal > 0xFFFF) {
                return R.string.proxy_error_invalid_port;
            }
        }
        return 0;
    }

    /**
     * Get {@link WifiConfiguration} based upon the {@link WifiManager} and networkId.
     *
     * @param networkId the id of the network.
     * @return the {@link WifiConfiguration} of the specified network.
     */
    public static WifiConfiguration getWifiConfiguration(WifiManager wifiManager, int networkId) {
        List<WifiConfiguration> configuredNetworks = wifiManager.getConfiguredNetworks();
        if (configuredNetworks != null) {
            for (WifiConfiguration configuredNetwork : configuredNetworks) {
                if (configuredNetwork.networkId == networkId) {
                    return configuredNetwork;
                }
            }
        }
        return null;
    }

    /**
     * Return the configured network that matches the ssid/security pair, or create one.
     */
    public static WifiConfiguration getConfiguration(String ssid, int security) {
        WifiConfiguration config = new WifiConfiguration();
        setConfigSsid(config, ssid);
        setConfigKeyManagementBySecurity(config, security);
        return config;
    }

    /**
     * @param context Context of caller
     * @param config  The WiFi config.
     * @return true if Settings cannot modify the config due to lockDown.
     */
    public static boolean isNetworkLockedDown(Context context, WifiConfiguration config) {
        if (config == null) {
            return false;
        }

        final DevicePolicyManager dpm = context.getSystemService(DevicePolicyManager.class);
        final PackageManager pm = context.getPackageManager();
        final UserManager um = context.getSystemService(UserManager.class);

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
            } else if (dpm.isOrganizationOwnedDeviceWithManagedProfile()) {
                int profileOwnerUserId = getManagedProfileId(um, UserHandle.myUserId());
                final ComponentName profileOwner = dpm.getProfileOwnerAsUser(profileOwnerUserId);
                if (profileOwner != null) {
                    try {
                        final int profileOwnerUid = pm.getPackageUidAsUser(
                                profileOwner.getPackageName(), profileOwnerUserId);
                        isConfigEligibleForLockdown = profileOwnerUid == config.creatorUid;
                    } catch (PackageManager.NameNotFoundException e) {
                        // don't care
                    }
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
     * Retrieves the id for the given user's  profile.
     *
     * @return the profile id or UserHandle.USER_NULL if there is none.
     */
    private static int getManagedProfileId(UserManager um, int parentUserId) {
        final int[] profileIds = um.getProfileIdsWithDisabled(parentUserId);
        for (int profileId : profileIds) {
            if (profileId != parentUserId && um.isManagedProfile(profileId)) {
                return profileId;
            }
        }
        return UserHandle.USER_NULL;
    }
}
