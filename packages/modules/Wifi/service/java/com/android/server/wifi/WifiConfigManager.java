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

import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.net.wifi.WifiManager.AddNetworkResult.STATUS_INVALID_CONFIGURATION;
import static android.net.wifi.WifiManager.AddNetworkResult.STATUS_INVALID_CONFIGURATION_ENTERPRISE;
import static android.net.wifi.WifiManager.AddNetworkResult.STATUS_NO_PERMISSION_MODIFY_CONFIG;
import static android.net.wifi.WifiManager.AddNetworkResult.STATUS_SUCCESS;
import static android.net.wifi.WifiManager.WIFI_FEATURE_TRUST_ON_FIRST_USE;

import android.Manifest;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.net.DhcpOption;
import android.net.IpConfiguration;
import android.net.MacAddress;
import android.net.ProxyInfo;
import android.net.StaticIpConfiguration;
import android.net.wifi.ScanResult;
import android.net.wifi.SecurityParams;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiConfiguration.NetworkSelectionStatus;
import android.net.wifi.WifiEnterpriseConfig;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiScanner;
import android.net.wifi.WifiSsid;
import android.os.Handler;
import android.os.Process;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.LocalLog;
import android.util.Log;
import android.util.Pair;

import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.build.SdkLevel;
import com.android.net.module.util.MacAddressUtils;
import com.android.server.wifi.hotspot2.PasspointManager;
import com.android.server.wifi.proto.nano.WifiMetricsProto.UserActionEvent;
import com.android.server.wifi.util.CertificateSubjectInfo;
import com.android.server.wifi.util.LruConnectionTracker;
import com.android.server.wifi.util.MissingCounterTimerLockList;
import com.android.server.wifi.util.WifiPermissionsUtil;
import com.android.wifi.flags.FeatureFlags;
import com.android.wifi.resources.R;

import org.xmlpull.v1.XmlPullParserException;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.PrintWriter;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This class provides the APIs to manage configured Wi-Fi networks.
 * It deals with the following:
 * - Maintaining a list of configured networks for quick access.
 * - Persisting the configurations to store when required.
 * - Supporting WifiManager Public API calls:
 *   > addOrUpdateNetwork()
 *   > removeNetwork()
 *   > enableNetwork()
 *   > disableNetwork()
 * - Handle user switching on multi-user devices.
 *
 * All network configurations retrieved from this class are copies of the original configuration
 * stored in the internal database. So, any updates to the retrieved configuration object are
 * meaningless and will not be reflected in the original database.
 * This is done on purpose to ensure that only WifiConfigManager can modify configurations stored
 * in the internal database. Any configuration updates should be triggered with appropriate helper
 * methods of this class using the configuration's unique networkId.
 *
 * NOTE: These API's are not thread safe and should only be used from the main Wifi thread.
 */
public class WifiConfigManager {
    /**
     * String used to mask passwords to public interface.
     */
    @VisibleForTesting
    public static final String PASSWORD_MASK = "*";

    private final AlarmManager mAlarmManager;
    private final FeatureFlags mFeatureFlags;
    private boolean mBufferedWritePending;
    /** Alarm tag to use for starting alarms for buffering file writes. */
    @VisibleForTesting public static final String BUFFERED_WRITE_ALARM_TAG = "WriteBufferAlarm";
    /** Time interval for buffering file writes for non-forced writes */
    private static final int BUFFERED_WRITE_ALARM_INTERVAL_MS = 10 * 1000;
    /** Alarm listener for flushing out any buffered writes. */
    private final AlarmManager.OnAlarmListener mBufferedWriteListener =
            new AlarmManager.OnAlarmListener() {
                public void onAlarm() {
                    if (mBufferedWritePending) {
                        writeBufferedData(true);
                    }
                }
            };

    /**
     * Interface for other modules to listen to the network updated events.
     * Note: Credentials are masked to avoid accidentally sending credentials outside the stack.
     * Use WifiConfigManager#getConfiguredNetworkWithPassword() to retrieve credentials.
     */
    public interface OnNetworkUpdateListener {
        /**
         * Invoked on network being added.
         */
        default void onNetworkAdded(@NonNull WifiConfiguration config) { };
        /**
         * Invoked on network being enabled.
         */
        default void onNetworkEnabled(@NonNull WifiConfiguration config) { };
        /**
         * Invoked on network being permanently disabled.
         */
        default void onNetworkPermanentlyDisabled(@NonNull WifiConfiguration config,
                int disableReason) { };
        /**
         * Invoked on network being removed.
         */
        default void onNetworkRemoved(@NonNull WifiConfiguration config) { };
        /**
         * Invoked on network being temporarily disabled.
         */
        default void onNetworkTemporarilyDisabled(@NonNull WifiConfiguration config,
                int disableReason) { };
        /**
         * Invoked on network being updated.
         *
         * @param newConfig Updated WifiConfiguration object.
         * @param oldConfig Prev WifiConfiguration object.
         * @param hasCredentialChanged true if credential is changed, false otherwise.
         */
        default void onNetworkUpdated(
                @NonNull WifiConfiguration newConfig, @NonNull WifiConfiguration oldConfig,
                boolean hasCredentialChanged) { };

        /**
         * Invoked when user connect choice is set.
         * @param networks List of network profiles to set user connect choice.
         * @param choiceKey Network key {@link WifiConfiguration#getProfileKey()}
         *                  corresponding to the network which the user chose.
         * @param rssi the signal strength of the user selected network
         */
        default void onConnectChoiceSet(@NonNull List<WifiConfiguration> networks,
                String choiceKey, int rssi) { }

        /**
         * Invoked when user connect choice is removed.
         * @param choiceKey The network profile key of the user connect choice that was removed.
         */
        default void onConnectChoiceRemoved(@NonNull String choiceKey){ }

        /**
         * Invoke when security params changed, especially when NetworkTransitionDisable event
         * received
         * @param oldConfig The original WifiConfiguration
         * @param securityParams the updated securityParams
         */
        default void onSecurityParamsUpdate(@NonNull WifiConfiguration oldConfig,
                List<SecurityParams> securityParams) { }
    }
    /**
     * Max size of scan details to cache in {@link #mScanDetailCaches}.
     */
    @VisibleForTesting
    public static final int SCAN_CACHE_ENTRIES_MAX_SIZE = 192;
    /**
     * Once the size of the scan details in the cache {@link #mScanDetailCaches} exceeds
     * {@link #SCAN_CACHE_ENTRIES_MAX_SIZE}, trim it down to this value so that we have some
     * buffer time before the next eviction.
     */
    @VisibleForTesting
    public static final int SCAN_CACHE_ENTRIES_TRIM_SIZE = 128;
    /**
     * Link networks only if they have less than this number of scan cache entries.
     */
    @VisibleForTesting
    public static final int LINK_CONFIGURATION_MAX_SCAN_CACHE_ENTRIES = 6;
    /**
     * Link networks only if the bssid in scan results for the networks match in the first
     * 16 ASCII chars in the bssid string. For example = "af:de:56;34:15:7"
     */
    @VisibleForTesting
    public static final int LINK_CONFIGURATION_BSSID_MATCH_LENGTH = 16;
    /**
     * Log tag for this class.
     */
    private static final String TAG = "WifiConfigManager";
    /**
     * Maximum age of scan results that can be used for averaging out RSSI value.
     */
    private static final int SCAN_RESULT_MAXIMUM_AGE_MS = 40000;

    /**
     * Enforce a minimum time to wait after the last disconnect to generate a new randomized MAC,
     * since IPv6 networks don't provide the DHCP lease duration.
     * 4 hours.
     */
    @VisibleForTesting
    protected static final long NON_PERSISTENT_MAC_WAIT_AFTER_DISCONNECT_MS = 4 * 60 * 60 * 1000;
    @VisibleForTesting
    protected static final long NON_PERSISTENT_MAC_REFRESH_MS_MIN = 30 * 60 * 1000; // 30 minutes
    @VisibleForTesting
    protected static final long NON_PERSISTENT_MAC_REFRESH_MS_MAX = 24 * 60 * 60 * 1000; // 24 hours

    private static final MacAddress DEFAULT_MAC_ADDRESS =
            MacAddress.fromString(WifiInfo.DEFAULT_MAC_ADDRESS);

    private static final String VRRP_MAC_ADDRESS_PREFIX = "00:00:5E:00:01";

    /**
     * Expiration timeout for user disconnect network. (1 hour)
     */
    @VisibleForTesting
    public static final long USER_DISCONNECT_NETWORK_BLOCK_EXPIRY_MS = (long) 1000 * 60 * 60;

    @VisibleForTesting
    public static final int SCAN_RESULT_MISSING_COUNT_THRESHOLD = 1;
    public static final String NON_PERSISTENT_MAC_RANDOMIZATION_FEATURE_FORCE_ENABLE_FLAG =
            "non_persistent_mac_randomization_force_enabled";
    private static final int NON_CARRIER_MERGED_NETWORKS_SCAN_CACHE_QUERY_DURATION_MS =
            10 * 60 * 1000; // 10 minutes

    /**
     * General sorting algorithm of all networks for scanning purposes:
     * Place the configurations in ascending order of their AgeIndex. AgeIndex is based on most
     * recently connected order. The lower the more recently connected.
     * If networks have the same AgeIndex, place the configurations with
     * |lastSeenInQualifiedNetworkSelection| set first.
     */
    private final WifiConfigurationUtil.WifiConfigurationComparator mScanListComparator =
            new WifiConfigurationUtil.WifiConfigurationComparator() {
                @Override
                public int compareNetworksWithSameStatus(WifiConfiguration a, WifiConfiguration b) {
                    int indexA = mLruConnectionTracker.getAgeIndexOfNetwork(a);
                    int indexB = mLruConnectionTracker.getAgeIndexOfNetwork(b);
                    if (indexA != indexB) {
                        return Integer.compare(indexA, indexB);
                    } else {
                        boolean isConfigALastSeen =
                                a.getNetworkSelectionStatus()
                                        .getSeenInLastQualifiedNetworkSelection();
                        boolean isConfigBLastSeen =
                                b.getNetworkSelectionStatus()
                                        .getSeenInLastQualifiedNetworkSelection();
                        return Boolean.compare(isConfigBLastSeen, isConfigALastSeen);
                    }
                }
            };

    /**
     * List of external dependencies for WifiConfigManager.
     */
    private final Context mContext;
    private final WifiInjector mWifiInjector;
    private final Clock mClock;
    private final UserManager mUserManager;
    private final BackupManagerProxy mBackupManagerProxy;
    private final WifiKeyStore mWifiKeyStore;
    private final WifiConfigStore mWifiConfigStore;
    private final WifiPermissionsUtil mWifiPermissionsUtil;
    private final MacAddressUtil mMacAddressUtil;
    private final WifiMetrics mWifiMetrics;
    private final WifiBlocklistMonitor mWifiBlocklistMonitor;
    private final WifiLastResortWatchdog mWifiLastResortWatchdog;
    private final WifiCarrierInfoManager mWifiCarrierInfoManager;
    private final WifiScoreCard mWifiScoreCard;
    // Keep order of network connection.
    private final LruConnectionTracker mLruConnectionTracker;
    private final BuildProperties mBuildProperties;

    /**
     * Local log used for debugging any WifiConfigManager issues.
     */
    private final LocalLog mLocalLog;
    /**
     * Map of configured networks with network id as the key.
     */
    private final ConfigurationMap mConfiguredNetworks;
    /**
     * Stores a map of NetworkId to ScanDetailCache.
     */
    private final Map<Integer, ScanDetailCache> mScanDetailCaches;
    /**
     * Framework keeps a list of networks that where temporarily disabled by user,
     * framework knows not to autoconnect again even if the app/scorer recommends it.
     * Network will be based on FQDN for passpoint and SSID for non-passpoint.
     * List will be deleted when Wifi turn off, device restart or network settings reset.
     * Also when user manfully select to connect network will unblock that network.
     */
    private final MissingCounterTimerLockList<String> mUserTemporarilyDisabledList;
    private final NonCarrierMergedNetworksStatusTracker mNonCarrierMergedNetworksStatusTracker;


    /**
     * Framework keeps a mapping from configKey to the randomized MAC address so that
     * when a user forgets a network and thne adds it back, the same randomized MAC address
     * will get used.
     */
    private final Map<String, String> mRandomizedMacAddressMapping;

    /**
     * Store the network update listeners.
     */
    private final Set<OnNetworkUpdateListener> mListeners;

    private final FrameworkFacade mFrameworkFacade;
    private final DeviceConfigFacade mDeviceConfigFacade;
    private final Handler mHandler;

    /**
     * Verbose logging flag. Toggled by developer options.
     */
    private boolean mVerboseLoggingEnabled = false;
    /**
     * Current logged in user ID.
     */
    private int mCurrentUserId = UserHandle.SYSTEM.getIdentifier();
    /**
     * Flag to indicate that the new user's store has not yet been read since user switch.
     * Initialize this flag to |true| to trigger a read on the first user unlock after
     * bootup.
     */
    private boolean mPendingUnlockStoreRead = true;
    /**
     * Flag to indicate if we have performed a read from store at all. This is used to gate
     * any user unlock/switch operations until we read the store (Will happen if wifi is disabled
     * when user updates from N to O).
     */
    private boolean mPendingStoreRead = true;
    /**
     * Flag to indicate if the user unlock was deferred until the store load occurs.
     */
    private boolean mDeferredUserUnlockRead = false;
    /**
     * This is keeping track of the next network ID to be assigned. Any new networks will be
     * assigned |mNextNetworkId| as network ID.
     */
    private int mNextNetworkId = 0;
    /**
     * This is used to remember which network was selected successfully last by an app. This is set
     * when an app invokes {@link #enableNetwork(int, boolean, int)} with |disableOthers| flag set.
     * This is the only way for an app to request connection to a specific network using the
     * {@link WifiManager} API's.
     */
    private int mLastSelectedNetworkId = WifiConfiguration.INVALID_NETWORK_ID;
    private long mLastSelectedTimeStamp =
            WifiConfiguration.NetworkSelectionStatus.INVALID_NETWORK_SELECTION_DISABLE_TIMESTAMP;

    // Store data for network list and deleted ephemeral SSID list.  Used for serializing
    // parsing data to/from the config store.
    private final NetworkListSharedStoreData mNetworkListSharedStoreData;
    private final NetworkListUserStoreData mNetworkListUserStoreData;
    private final RandomizedMacStoreData mRandomizedMacStoreData;

    private static class NetworkIdentifier {
        private WifiSsid mSsid;
        private byte[] mOui;
        NetworkIdentifier(WifiSsid ssid, byte[] oui) {
            mSsid = ssid;
            mOui = oui;
        }

        @Override
        public int hashCode() {
            return Objects.hash(mSsid, Arrays.hashCode(mOui));
        }

        @Override
        public boolean equals(Object otherObj) {
            if (this == otherObj) {
                return true;
            } else if (!(otherObj instanceof NetworkIdentifier)) {
                return false;
            }
            NetworkIdentifier other = (NetworkIdentifier) otherObj;
            return Objects.equals(mSsid, other.mSsid) && Arrays.equals(mOui, other.mOui);
        }
    }
    private final Map<NetworkIdentifier, List<DhcpOption>> mCustomDhcpOptions = new HashMap<>();

    /** Create new instance of WifiConfigManager. */
    WifiConfigManager(
            Context context,
            WifiKeyStore wifiKeyStore,
            WifiConfigStore wifiConfigStore,
            NetworkListSharedStoreData networkListSharedStoreData,
            NetworkListUserStoreData networkListUserStoreData,
            RandomizedMacStoreData randomizedMacStoreData,
            LruConnectionTracker lruConnectionTracker,
            WifiInjector wifiInjector,
            Handler handler) {
        mContext = context;
        mHandler = handler;
        mWifiInjector = wifiInjector;
        mClock = wifiInjector.getClock();
        mUserManager = wifiInjector.getUserManager();
        mWifiCarrierInfoManager = wifiInjector.getWifiCarrierInfoManager();
        mWifiMetrics = wifiInjector.getWifiMetrics();
        mWifiBlocklistMonitor = wifiInjector.getWifiBlocklistMonitor();
        mWifiLastResortWatchdog = wifiInjector.getWifiLastResortWatchdog();
        mWifiScoreCard = wifiInjector.getWifiScoreCard();
        mWifiPermissionsUtil = wifiInjector.getWifiPermissionsUtil();
        mFrameworkFacade = wifiInjector.getFrameworkFacade();
        mDeviceConfigFacade = wifiInjector.getDeviceConfigFacade();
        mFeatureFlags = mDeviceConfigFacade.getFeatureFlags();
        mMacAddressUtil = wifiInjector.getMacAddressUtil();
        mBuildProperties = wifiInjector.getBuildProperties();

        mBackupManagerProxy = new BackupManagerProxy();
        mWifiKeyStore = wifiKeyStore;
        mWifiConfigStore = wifiConfigStore;
        mConfiguredNetworks = new ConfigurationMap(mWifiPermissionsUtil);
        mScanDetailCaches = new HashMap<>(16, 0.75f);
        mUserTemporarilyDisabledList =
                new MissingCounterTimerLockList<>(SCAN_RESULT_MISSING_COUNT_THRESHOLD, mClock);
        mNonCarrierMergedNetworksStatusTracker = new NonCarrierMergedNetworksStatusTracker(mClock);
        mRandomizedMacAddressMapping = new HashMap<>();
        mListeners = new ArraySet<>();

        // Register store data for network list and deleted ephemeral SSIDs.
        mNetworkListSharedStoreData = networkListSharedStoreData;
        mNetworkListUserStoreData = networkListUserStoreData;
        mRandomizedMacStoreData = randomizedMacStoreData;
        mWifiConfigStore.registerStoreData(mNetworkListSharedStoreData);
        mWifiConfigStore.registerStoreData(mNetworkListUserStoreData);
        mWifiConfigStore.registerStoreData(mRandomizedMacStoreData);

        mLocalLog = new LocalLog(
                context.getSystemService(ActivityManager.class).isLowRamDevice() ? 128 : 256);
        mLruConnectionTracker = lruConnectionTracker;
        mAlarmManager = context.getSystemService(AlarmManager.class);
    }

    /**
     * Update the cellular data availability of the default data SIM.
     */
    public void onCellularConnectivityChanged(@WifiDataStall.CellularDataStatusCode int status) {
        localLog("onCellularConnectivityChanged:" + status);
        if (status == WifiDataStall.CELLULAR_DATA_NOT_AVAILABLE) {
            stopRestrictingAutoJoinToSubscriptionId();
        }
    }

    /**
     * Determine if the framework should perform non-persistent MAC randomization when connecting
     * to the SSID or FQDN in the input WifiConfiguration.
     * @param config
     * @return
     */
    public boolean shouldUseNonPersistentRandomization(WifiConfiguration config) {
        if (!isMacRandomizationSupported()
                || config.macRandomizationSetting == WifiConfiguration.RANDOMIZATION_NONE) {
            return false;
        }

        // Use non-persistent randomization if it's forced on by dev option
        if (mFrameworkFacade.getIntegerSetting(mContext,
                NON_PERSISTENT_MAC_RANDOMIZATION_FEATURE_FORCE_ENABLE_FLAG, 0) == 1) {
            return true;
        }

        // use non-persistent or persistent randomization if configured to do so.
        if (config.macRandomizationSetting == WifiConfiguration.RANDOMIZATION_NON_PERSISTENT) {
            return true;
        }
        if (config.macRandomizationSetting == WifiConfiguration.RANDOMIZATION_PERSISTENT) {
            return false;
        }

        // otherwise the wifi frameworks should decide automatically
        if (config.getIpConfiguration().getIpAssignment() == IpConfiguration.IpAssignment.STATIC) {
            return false;
        }
        if (config.isOpenNetwork() && shouldEnableNonPersistentRandomizationOnOpenNetwork(config)) {
            return true;
        }
        if (config.isPasspoint()) {
            return isNetworkOptInForNonPersistentRandomization(config.FQDN);
        } else {
            return isNetworkOptInForNonPersistentRandomization(config.SSID);
        }
    }

    private boolean shouldEnableNonPersistentRandomizationOnOpenNetwork(WifiConfiguration config) {
        if (!mContext.getResources().getBoolean(
                        R.bool.config_wifiAllowNonPersistentMacRandomizationOnOpenSsids)) {
            return false;
        }
        return config.getNetworkSelectionStatus().hasEverConnected()
                && config.getNetworkSelectionStatus().hasNeverDetectedCaptivePortal();
    }

    private boolean isNetworkOptInForNonPersistentRandomization(String ssidOrFqdn) {
        Set<String> perDeviceSsidBlocklist = new ArraySet<>(mContext.getResources().getStringArray(
                R.array.config_wifi_non_persistent_randomization_ssid_blocklist));
        if (mDeviceConfigFacade.getNonPersistentMacRandomizationSsidBlocklist().contains(ssidOrFqdn)
                || perDeviceSsidBlocklist.contains(ssidOrFqdn)) {
            return false;
        }
        Set<String> perDeviceSsidAllowlist = new ArraySet<>(mContext.getResources().getStringArray(
                R.array.config_wifi_non_persistent_randomization_ssid_allowlist));
        return mDeviceConfigFacade.getNonPersistentMacRandomizationSsidAllowlist()
                .contains(ssidOrFqdn) || perDeviceSsidAllowlist.contains(ssidOrFqdn);
    }

    @VisibleForTesting
    protected int getRandomizedMacAddressMappingSize() {
        return mRandomizedMacAddressMapping.size();
    }

    /**
     * The persistent randomized MAC address is locally generated for each SSID and does not
     * change until factory reset of the device. In the initial Q release the per-SSID randomized
     * MAC is saved on the device, but in an update the storing of randomized MAC is removed.
     * Instead, the randomized MAC is calculated directly from the SSID and a on device secret.
     * For backward compatibility, this method first checks the device storage for saved
     * randomized MAC. If it is not found or the saved MAC is invalid then it will calculate the
     * randomized MAC directly.
     *
     * In the future as devices launched on Q no longer get supported, this method should get
     * simplified to return the calculated MAC address directly.
     * @param config the WifiConfiguration to obtain MAC address for.
     * @return persistent MAC address for this WifiConfiguration
     */
    @VisibleForTesting
    public MacAddress getPersistentMacAddress(WifiConfiguration config) {
        // mRandomizedMacAddressMapping had been the location to save randomized MAC addresses.
        String persistentMacString = mRandomizedMacAddressMapping.get(
                config.getNetworkKey());
        // Use the MAC address stored in the storage if it exists and is valid. Otherwise
        // use the MAC address calculated from a hash function as the persistent MAC.
        if (persistentMacString != null) {
            try {
                return MacAddress.fromString(persistentMacString);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Error creating randomized MAC address from stored value.");
                mRandomizedMacAddressMapping.remove(config.getNetworkKey());
            }
        }
        MacAddress result = mMacAddressUtil.calculatePersistentMacForSta(config.getNetworkKey(),
                Process.WIFI_UID);
        if (result == null) {
            Log.wtf(TAG, "Failed to generate MAC address from KeyStore even after retrying. "
                    + "Using locally generated MAC address instead.");
            result = config.getRandomizedMacAddress();
            if (DEFAULT_MAC_ADDRESS.equals(result)) {
                result = MacAddressUtils.createRandomUnicastAddress();
            }
        }
        return result;
    }

    /**
     * Sets the randomized MAC expiration time based on the DHCP lease duration.
     * This should be called every time DHCP lease information is obtained.
     */
    public void updateRandomizedMacExpireTime(WifiConfiguration config, long dhcpLeaseSeconds) {
        WifiConfiguration internalConfig = getInternalConfiguredNetwork(config.networkId);
        if (internalConfig == null) {
            return;
        }
        long expireDurationMs = (dhcpLeaseSeconds & 0xffffffffL) * 1000;
        expireDurationMs = Math.max(NON_PERSISTENT_MAC_REFRESH_MS_MIN, expireDurationMs);
        expireDurationMs = Math.min(NON_PERSISTENT_MAC_REFRESH_MS_MAX, expireDurationMs);
        internalConfig.randomizedMacExpirationTimeMs = mClock.getWallClockMillis()
                + expireDurationMs;
    }

    private void setRandomizedMacAddress(WifiConfiguration config, MacAddress mac) {
        config.setRandomizedMacAddress(mac);
        config.randomizedMacLastModifiedTimeMs = mClock.getWallClockMillis();
    }

    /**
     * Obtain the persistent MAC address by first reading from an internal database. If non exists
     * then calculate the persistent MAC using HMAC-SHA256.
     * Finally set the randomized MAC of the configuration to the randomized MAC obtained.
     * @param config the WifiConfiguration to make the update
     * @return the persistent MacAddress or null if the operation is unsuccessful
     */
    private MacAddress setRandomizedMacToPersistentMac(WifiConfiguration config) {
        MacAddress persistentMac = getPersistentMacAddress(config);
        if (persistentMac == null || persistentMac.equals(config.getRandomizedMacAddress())) {
            return persistentMac;
        }
        WifiConfiguration internalConfig = getInternalConfiguredNetwork(config.networkId);
        setRandomizedMacAddress(internalConfig, persistentMac);
        return persistentMac;
    }

    /**
     * This method is called before connecting to a network that has non-persistent randomization
     * enabled, and will re-randomize the MAC address if needed.
     * @param config the WifiConfiguration to make the update
     * @return the updated MacAddress
     */
    private MacAddress updateRandomizedMacIfNeeded(WifiConfiguration config) {
        boolean shouldUpdateMac = config.randomizedMacExpirationTimeMs
                < mClock.getWallClockMillis() || mClock.getWallClockMillis()
                - config.randomizedMacLastModifiedTimeMs >= NON_PERSISTENT_MAC_REFRESH_MS_MAX;
        if (!shouldUpdateMac) {
            return config.getRandomizedMacAddress();
        }
        WifiConfiguration internalConfig = getInternalConfiguredNetwork(config.networkId);
        setRandomizedMacAddress(internalConfig, MacAddressUtils.createRandomUnicastAddress());
        return internalConfig.getRandomizedMacAddress();
    }

    /**
     * Returns the randomized MAC address that should be used for this WifiConfiguration.
     * This API may return a randomized MAC different from the persistent randomized MAC if
     * the WifiConfiguration is configured for non-persistent MAC randomization.
     * @param config
     * @return MacAddress
     */
    public MacAddress getRandomizedMacAndUpdateIfNeeded(WifiConfiguration config,
            boolean isForSecondaryDbs) {
        MacAddress mac = shouldUseNonPersistentRandomization(config)
                ? updateRandomizedMacIfNeeded(config)
                : setRandomizedMacToPersistentMac(config);
        // If this is the secondary STA for multi internet for DBS AP, use a different MAC than the
        // persistent mac randomization, as the primary and secondary STAs could connect to the
        // same SSID.
        if (isForSecondaryDbs) {
            mac = MacAddressUtil.nextMacAddress(mac);
        }
        return mac;
    }

    /**
     * Enable/disable verbose logging in WifiConfigManager & its helper classes.
     */
    public void enableVerboseLogging(boolean verbose) {
        mVerboseLoggingEnabled = verbose;
        mWifiConfigStore.enableVerboseLogging(mVerboseLoggingEnabled);
        mWifiKeyStore.enableVerboseLogging(mVerboseLoggingEnabled);
        mWifiBlocklistMonitor.enableVerboseLogging(mVerboseLoggingEnabled);
    }

    /**
     * Helper method to mask all passwords/keys from the provided WifiConfiguration object. This
     * is needed when the network configurations are being requested via the public WifiManager
     * API's.
     * This currently masks the following elements: psk, wepKeys & enterprise config password.
     */
    private void maskPasswordsInWifiConfiguration(WifiConfiguration configuration) {
        if (!TextUtils.isEmpty(configuration.preSharedKey)) {
            configuration.preSharedKey = PASSWORD_MASK;
        }
        if (configuration.wepKeys != null) {
            for (int i = 0; i < configuration.wepKeys.length; i++) {
                if (!TextUtils.isEmpty(configuration.wepKeys[i])) {
                    configuration.wepKeys[i] = PASSWORD_MASK;
                }
            }
        }
        if (configuration.enterpriseConfig != null && !TextUtils.isEmpty(
                configuration.enterpriseConfig.getPassword())) {
            configuration.enterpriseConfig.setPassword(PASSWORD_MASK);
        }
    }

    /**
     * Helper method to mask randomized MAC address from the provided WifiConfiguration Object.
     * This is needed when the network configurations are being requested via the public
     * WifiManager API's. This method puts "02:00:00:00:00:00" as the MAC address.
     * @param configuration WifiConfiguration to hide the MAC address
     */
    private void maskRandomizedMacAddressInWifiConfiguration(WifiConfiguration configuration) {
        setRandomizedMacAddress(configuration, DEFAULT_MAC_ADDRESS);
    }

    /**
     * Helper method to create a copy of the provided internal WifiConfiguration object to be
     * passed to external modules.
     *
     * @param configuration provided WifiConfiguration object.
     * @param maskPasswords Mask passwords or not.
     * @param targetUid Target UID for MAC address reading: -1 = mask all, 0 = mask none, >0 =
     *                  mask all but the targetUid (carrier app).
     * @return Copy of the WifiConfiguration object, or a default WifiConfiguration if the input
     *         is null.
     */
    private @NonNull WifiConfiguration createExternalWifiConfiguration(
            @NonNull WifiConfiguration configuration, boolean maskPasswords, int targetUid) {
        if (configuration == null) {
            Log.wtf(TAG, "Unexpected null configuration in createExternalWifiConfiguration");
            return new WifiConfiguration();
        }
        WifiConfiguration network = new WifiConfiguration(configuration);
        if (maskPasswords) {
            maskPasswordsInWifiConfiguration(network);
        }
        if (targetUid != Process.WIFI_UID && targetUid != Process.SYSTEM_UID
                && targetUid != configuration.creatorUid) {
            maskRandomizedMacAddressInWifiConfiguration(network);
        }
        if (!isMacRandomizationSupported()) {
            network.macRandomizationSetting = WifiConfiguration.RANDOMIZATION_NONE;
        }
        return network;
    }

    /**
     * Returns whether MAC randomization is supported on this device.
     * @return
     */
    private boolean isMacRandomizationSupported() {
        return mContext.getResources().getBoolean(
                R.bool.config_wifi_connected_mac_randomization_supported);
    }

    /**
     * Fetch the list of currently configured networks maintained in WifiConfigManager.
     *
     * This retrieves a copy of the internal configurations maintained by WifiConfigManager and
     * should be used for any public interfaces.
     *
     * @param savedOnly     Retrieve only saved networks.
     * @param maskPasswords Mask passwords or not.
     * @param targetUid Target UID for MAC address reading: -1 (Invalid UID) = mask all,
     *                  WIFI||SYSTEM = mask none, <other> = mask all but the targetUid (carrier
     *                  app).
     * @return List of WifiConfiguration objects representing the networks.
     */
    private List<WifiConfiguration> getConfiguredNetworks(
            boolean savedOnly, boolean maskPasswords, int targetUid) {
        List<WifiConfiguration> networks = new ArrayList<>();
        for (WifiConfiguration config : getInternalConfiguredNetworks()) {
            if (savedOnly && (config.ephemeral || config.isPasspoint())) {
                continue;
            }
            networks.add(createExternalWifiConfiguration(config, maskPasswords, targetUid));
        }
        return networks;
    }

    /**
     * Retrieves the list of all configured networks with passwords masked.
     *
     * @return List of WifiConfiguration objects representing the networks.
     */
    public List<WifiConfiguration> getConfiguredNetworks() {
        return getConfiguredNetworks(false, true, Process.WIFI_UID);
    }

    /**
     * Retrieves the list of all configured networks with the passwords in plaintext.
     *
     * WARNING: Don't use this to pass network configurations to external apps. Should only be
     * sent to system apps/wifi stack, when there is a need for passwords in plaintext.
     * TODO: Need to understand the current use case of this API.
     *
     * @return List of WifiConfiguration objects representing the networks.
     */
    public List<WifiConfiguration> getConfiguredNetworksWithPasswords() {
        return getConfiguredNetworks(false, false, Process.WIFI_UID);
    }

    /**
     * Retrieves the list of all configured networks with the passwords masked.
     *
     * @return List of WifiConfiguration objects representing the networks.
     */
    public List<WifiConfiguration> getSavedNetworks(int targetUid) {
        return getConfiguredNetworks(true, true, targetUid);
    }

    /**
     * Check Wi-Fi 7 is enabled for this network.
     *
     * @param networkId networkId of the requested network.
     * @return true if Wi-Fi 7 is enabled for this network, false otherwise.
     */
    public boolean isWifi7Enabled(int networkId) {
        WifiConfiguration config = getInternalConfiguredNetwork(networkId);
        if (config == null) {
            return false;
        }
        return config.isWifi7Enabled();
    }

    /**
     * Retrieves the configured network corresponding to the provided networkId with password
     * masked.
     *
     * @param networkId networkId of the requested network.
     * @return WifiConfiguration object if found, null otherwise.
     */
    public @Nullable WifiConfiguration getConfiguredNetwork(int networkId) {
        WifiConfiguration config = getInternalConfiguredNetwork(networkId);
        if (config == null) {
            return null;
        }
        // Create a new configuration object with the passwords masked to send out to the external
        // world.
        return createExternalWifiConfiguration(config, true, Process.WIFI_UID);
    }

    /**
     * Retrieves the configured network corresponding to the provided config key with password
     * masked.
     *
     * @param configKey configKey of the requested network.
     * @return WifiConfiguration object if found, null otherwise.
     */
    public @Nullable WifiConfiguration getConfiguredNetwork(String configKey) {
        WifiConfiguration config = getInternalConfiguredNetwork(configKey);
        if (config == null) {
            return null;
        }
        // Create a new configuration object with the passwords masked to send out to the external
        // world.
        return createExternalWifiConfiguration(config, true, Process.WIFI_UID);
    }

    /**
     * Retrieves the configured network corresponding to the provided networkId with password
     * in plaintext.
     *
     * WARNING: Don't use this to pass network configurations to external apps. Should only be
     * sent to system apps/wifi stack, when there is a need for passwords in plaintext.
     *
     * @param networkId networkId of the requested network.
     * @return WifiConfiguration object if found, null otherwise.
     */
    public @Nullable WifiConfiguration getConfiguredNetworkWithPassword(int networkId) {
        WifiConfiguration config = getInternalConfiguredNetwork(networkId);
        if (config == null) {
            return null;
        }
        // Create a new configuration object without the passwords masked to send out to the
        // external world.
        return createExternalWifiConfiguration(config, false, Process.WIFI_UID);
    }

    /**
     * Retrieves the configured network corresponding to the provided networkId
     * without any masking.
     *
     * WARNING: Don't use this to pass network configurations except in the wifi stack, when
     * there is a need for passwords and randomized MAC address.
     *
     * @param networkId networkId of the requested network.
     * @return Copy of WifiConfiguration object if found, null otherwise.
     */
    public @Nullable WifiConfiguration getConfiguredNetworkWithoutMasking(int networkId) {
        WifiConfiguration config = getInternalConfiguredNetwork(networkId);
        if (config == null) {
            return null;
        }
        return new WifiConfiguration(config);
    }

    /**
     * Helper method to retrieve all the internal WifiConfiguration objects corresponding to all
     * the networks in our database.
     */
    private Collection<WifiConfiguration> getInternalConfiguredNetworks() {
        return mConfiguredNetworks.valuesForCurrentUser();
    }

    private @Nullable WifiConfiguration getInternalConfiguredNetworkByUpgradableType(
            @NonNull WifiConfiguration config) {
        WifiConfiguration internalConfig = null;
        int securityType = config.getDefaultSecurityParams().getSecurityType();
        WifiConfiguration possibleExistingConfig = new WifiConfiguration(config);
        switch (securityType) {
            case WifiConfiguration.SECURITY_TYPE_PSK:
                possibleExistingConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_SAE);
                break;
            case WifiConfiguration.SECURITY_TYPE_SAE:
                possibleExistingConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_PSK);
                break;
            case WifiConfiguration.SECURITY_TYPE_EAP:
                possibleExistingConfig.setSecurityParams(
                        WifiConfiguration.SECURITY_TYPE_EAP_WPA3_ENTERPRISE);
                break;
            case WifiConfiguration.SECURITY_TYPE_EAP_WPA3_ENTERPRISE:
                possibleExistingConfig.setSecurityParams(
                        WifiConfiguration.SECURITY_TYPE_EAP);
                break;
            case WifiConfiguration.SECURITY_TYPE_OPEN:
                possibleExistingConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_OWE);
                break;
            case WifiConfiguration.SECURITY_TYPE_OWE:
                possibleExistingConfig.setSecurityParams(WifiConfiguration.SECURITY_TYPE_OPEN);
                break;
            default:
                return null;
        }
        internalConfig = mConfiguredNetworks.getByConfigKeyForCurrentUser(
                possibleExistingConfig.getProfileKey());
        return internalConfig;
    }

    /**
     * Helper method to retrieve the internal WifiConfiguration object corresponding to the
     * provided configuration in our database.
     * This first attempts to find the network using the provided network ID in configuration,
     * else it attempts to find a matching configuration using the configKey.
     */
    private @Nullable WifiConfiguration getInternalConfiguredNetwork(
            @NonNull WifiConfiguration config) {
        WifiConfiguration internalConfig = mConfiguredNetworks.getForCurrentUser(config.networkId);
        if (internalConfig != null) {
            return internalConfig;
        }
        internalConfig = mConfiguredNetworks.getByConfigKeyForCurrentUser(
                config.getProfileKey());
        if (internalConfig != null) {
            return internalConfig;
        }
        internalConfig = getInternalConfiguredNetworkByUpgradableType(config);
        if (internalConfig == null) {
            Log.e(TAG, "Cannot find network with networkId " + config.networkId
                    + " or configKey " + config.getProfileKey()
                    + " or upgradable security type check");
        }
        return internalConfig;
    }

    /**
     * Helper method to retrieve the internal WifiConfiguration object corresponding to the
     * provided network ID in our database.
     */
    private @Nullable WifiConfiguration getInternalConfiguredNetwork(int networkId) {
        if (networkId == WifiConfiguration.INVALID_NETWORK_ID) {
            return null;
        }
        WifiConfiguration internalConfig = mConfiguredNetworks.getForCurrentUser(networkId);
        if (internalConfig == null) {
            Log.e(TAG, "Cannot find network with networkId " + networkId);
        }
        return internalConfig;
    }

    /**
     * Helper method to retrieve the internal WifiConfiguration object corresponding to the
     * provided configKey in our database.
     */
    private @Nullable WifiConfiguration getInternalConfiguredNetwork(String configKey) {
        WifiConfiguration internalConfig =
                mConfiguredNetworks.getByConfigKeyForCurrentUser(configKey);
        if (internalConfig == null) {
            Log.e(TAG, "Cannot find network with configKey " + configKey);
        }
        return internalConfig;
    }

    /**
     * Method to send out the configured networks change broadcast when network configurations
     * changed.
     *
     * In Android R we stopped sending out WifiConfiguration due to user privacy concerns.
     * Thus, no matter how many networks changed,
     * {@link WifiManager#EXTRA_MULTIPLE_NETWORKS_CHANGED} is always set to true, and
     * {@link WifiManager#EXTRA_WIFI_CONFIGURATION} is always null.
     *
     * @param reason  The reason for the change, should be one of WifiManager.CHANGE_REASON_ADDED,
     *                WifiManager.CHANGE_REASON_REMOVED, or WifiManager.CHANGE_REASON_CHANGE.
     * @param config The related to the change. This is only sent out for system users, and could
     *               be null if multiple WifiConfigurations are affected by the change.
     */
    private void sendConfiguredNetworkChangedBroadcast(int reason,
            @Nullable WifiConfiguration config) {
        Intent intent = new Intent(WifiManager.CONFIGURED_NETWORKS_CHANGED_ACTION);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        intent.putExtra(WifiManager.EXTRA_MULTIPLE_NETWORKS_CHANGED, true);
        intent.putExtra(WifiManager.EXTRA_CHANGE_REASON, reason);
        mContext.sendBroadcastAsUser(intent, UserHandle.ALL, Manifest.permission.ACCESS_WIFI_STATE);

        // Send another broadcast including the WifiConfiguration to System only
        Intent intentForSystem = new Intent(WifiManager.CONFIGURED_NETWORKS_CHANGED_ACTION);
        intentForSystem.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        intentForSystem.putExtra(WifiManager.EXTRA_MULTIPLE_NETWORKS_CHANGED, config == null);
        intentForSystem.putExtra(WifiManager.EXTRA_CHANGE_REASON, reason);
        intentForSystem.putExtra(WifiManager.EXTRA_WIFI_CONFIGURATION,
                config == null ? null : createExternalWifiConfiguration(config, true, -1));
        mContext.sendBroadcastAsUser(intentForSystem, UserHandle.SYSTEM,
                Manifest.permission.NETWORK_STACK);
    }

    /**
     * Checks if |uid| has permission to modify the provided configuration.
     *
     * @param config         WifiConfiguration object corresponding to the network to be modified.
     * @param uid            UID of the app requesting the modification.
     * @param packageName    Package name of the app requesting the modification.
     */
    private boolean canModifyNetwork(WifiConfiguration config, int uid,
            @Nullable String packageName) {
        // Passpoint configurations are generated and managed by PasspointManager. They can be
        // added by either PasspointNetworkNominator (for auto connection) or Settings app
        // (for manual connection), and need to be removed once the connection is completed.
        // Since it is "owned" by us, so always allow us to modify them.
        if (config.isPasspoint() && uid == Process.WIFI_UID) {
            return true;
        }

        // EAP-SIM/AKA/AKA' network needs framework to update the anonymous identity provided
        // by authenticator back to the WifiConfiguration object.
        // Since it is "owned" by us, so always allow us to modify them.
        if (config.enterpriseConfig != null
                && uid == Process.WIFI_UID
                && config.enterpriseConfig.isAuthenticationSimBased()) {
            return true;
        }

        // TODO: ideally package should not be null here (and hence we wouldn't need the
        // isDeviceOwner(uid) method), but it would require changing  many methods to pass the
        // package name around (for example, all methods called by
        // WifiServiceImpl.triggerConnectAndReturnStatus(netId, callingUid)
        final boolean isOrganizationOwnedDeviceAdmin =
                mWifiPermissionsUtil.isOrganizationOwnedDeviceAdmin(uid, packageName);

        // If |uid| corresponds to the device owner or the profile owner of an organization owned
        // device, allow all modifications.
        if (isOrganizationOwnedDeviceAdmin) {
            return true;
        }

        final boolean isCreator = (config.creatorUid == uid);

        // WiFi config lockdown related logic. At this point we know uid is NOT a Device Owner
        // or a Profile Owner of an organization owned device.
        final boolean isConfigEligibleForLockdown =
                mWifiPermissionsUtil.isOrganizationOwnedDeviceAdmin(config.creatorUid,
                        config.creatorName);
        if (!isConfigEligibleForLockdown) {
            // App that created the network or settings app (i.e user) has permission to
            // modify the network.
            return isCreator
                    || mWifiPermissionsUtil.checkNetworkSettingsPermission(uid)
                    || mWifiPermissionsUtil.checkNetworkSetupWizardPermission(uid)
                    || mWifiPermissionsUtil.checkConfigOverridePermission(uid);
        }

        final ContentResolver resolver = mContext.getContentResolver();
        final boolean isLockdownFeatureEnabled = Settings.Global.getInt(resolver,
                Settings.Global.WIFI_DEVICE_OWNER_CONFIGS_LOCKDOWN, 0) != 0;
        return !isLockdownFeatureEnabled
                // If not locked down, settings app (i.e user) has permission to modify the network.
                && (mWifiPermissionsUtil.checkNetworkSettingsPermission(uid)
                || mWifiPermissionsUtil.checkNetworkSetupWizardPermission(uid)
                || mWifiPermissionsUtil.checkConfigOverridePermission(uid));
    }

    private void mergeSecurityParamsListWithInternalWifiConfiguration(
            WifiConfiguration internalConfig, WifiConfiguration externalConfig) {
        // If not set, just copy over all list.
        if (internalConfig.getSecurityParamsList().isEmpty()) {
            internalConfig.setSecurityParams(externalConfig.getSecurityParamsList());
            return;
        }

        WifiConfigurationUtil.addUpgradableSecurityTypeIfNecessary(externalConfig);

        // An external caller is only allowed to set one type manually.
        // As a result, only default type matters.
        // There might be 3 cases:
        // 1. Existing config with new upgradable type config,
        //    ex. PSK/SAE config with SAE config.
        // 2. Existing configuration with downgradable type config,
        //    ex. SAE config with PSK config.
        // 3. The new type is not a compatible type of existing config.
        //    ex. Open config with PSK config.
        //    This might happen when updating a config via network ID directly.
        int oldType = internalConfig.getDefaultSecurityParams().getSecurityType();
        int newType = externalConfig.getDefaultSecurityParams().getSecurityType();
        if (oldType != newType) {
            if (internalConfig.isSecurityType(newType)) {
                internalConfig.setSecurityParamsIsAddedByAutoUpgrade(newType,
                        externalConfig.getDefaultSecurityParams().isAddedByAutoUpgrade());
            } else if (externalConfig.isSecurityType(oldType)) {
                internalConfig.setSecurityParams(newType);
                internalConfig.addSecurityParams(oldType);
            } else {
                internalConfig.setSecurityParams(externalConfig.getSecurityParamsList());
            }
        }
    }

    private void mergeDppSecurityParamsWithInternalWifiConfiguration(
            WifiConfiguration internalConfig, WifiConfiguration externalConfig) {
        // Do not update for non-DPP network
        if (!externalConfig.isSecurityType(WifiConfiguration.SECURITY_TYPE_DPP)) {
            return;
        }

        if (externalConfig.getDppConnector().length != 0
                && externalConfig.getDppCSignKey().length != 0
                && externalConfig.getDppNetAccessKey().length != 0) {
            internalConfig.setDppConnectionKeys(externalConfig.getDppConnector(),
                    externalConfig.getDppCSignKey(), externalConfig.getDppNetAccessKey());
        }

        if (externalConfig.getDppPrivateEcKey().length != 0) {
            internalConfig.setDppConfigurator(externalConfig.getDppPrivateEcKey());
        }
    }

    private static @WifiEnterpriseConfig.TofuConnectionState int mergeTofuConnectionState(
            WifiConfiguration internalConfig, WifiConfiguration externalConfig) {
        // Prioritize the internal config if it has reached a post-connection state.
        int internalTofuState = internalConfig.enterpriseConfig.getTofuConnectionState();
        if (internalTofuState == WifiEnterpriseConfig.TOFU_STATE_CONFIGURE_ROOT_CA
                || internalTofuState == WifiEnterpriseConfig.TOFU_STATE_CERT_PINNING) {
            return internalTofuState;
        }
        // Else assign a pre-connection state based on the latest external config.
        return externalConfig.enterpriseConfig.isTrustOnFirstUseEnabled()
                ? WifiEnterpriseConfig.TOFU_STATE_ENABLED_PRE_CONNECTION
                : WifiEnterpriseConfig.TOFU_STATE_NOT_ENABLED;
    }

    /**
     * Copy over public elements from an external WifiConfiguration object to the internal
     * configuration object if element has been set in the provided external WifiConfiguration.
     * The only exception is the hidden |IpConfiguration| parameters, these need to be copied over
     * for every update.
     *
     * This method updates all elements that are common to both network addition & update.
     * The following fields of {@link WifiConfiguration} are not copied from external configs:
     *  > networkId - These are allocated by Wi-Fi stack internally for any new configurations.
     *  > status - The status needs to be explicitly updated using
     *             {@link WifiManager#enableNetwork(int, boolean)} or
     *             {@link WifiManager#disableNetwork(int)}.
     *
     * @param internalConfig WifiConfiguration object in our internal map.
     * @param externalConfig WifiConfiguration object provided from the external API.
     */
    private void mergeWithInternalWifiConfiguration(
            WifiConfiguration internalConfig, WifiConfiguration externalConfig) {
        if (externalConfig.SSID != null) {
            // Translate the SSID in case it is in hexadecimal for a translatable charset.
            if (externalConfig.SSID.length() > 0 && externalConfig.SSID.charAt(0) != '\"') {
                internalConfig.SSID = mWifiInjector.getSsidTranslator().getTranslatedSsid(
                        WifiSsid.fromString(externalConfig.SSID)).toString();
            } else {
                internalConfig.SSID = externalConfig.SSID;
            }
        }
        internalConfig.BSSID = externalConfig.BSSID == null ? null
                : externalConfig.BSSID.toLowerCase();
        if (externalConfig.hiddenSSID) {
            internalConfig.hiddenSSID = true;
        } else if (internalConfig.getSecurityParams(
                externalConfig.getDefaultSecurityParams().getSecurityType()) != null) {
            // Only set hiddenSSID to false if we're updating an existing config.
            // This is to prevent users from mistakenly converting an existing hidden config to
            // unhidden when adding a new config of the same security family.
            internalConfig.hiddenSSID = false;
        }

        if (externalConfig.preSharedKey != null
                && !externalConfig.preSharedKey.equals(PASSWORD_MASK)) {
            internalConfig.preSharedKey = externalConfig.preSharedKey;
        }
        // Modify only wep keys are present in the provided configuration. This is a little tricky
        // because there is no easy way to tell if the app is actually trying to null out the
        // existing keys or not.
        if (externalConfig.wepKeys != null) {
            boolean hasWepKey = false;
            for (int i = 0; i < internalConfig.wepKeys.length; i++) {
                if (externalConfig.wepKeys[i] != null
                        && !externalConfig.wepKeys[i].equals(PASSWORD_MASK)) {
                    internalConfig.wepKeys[i] = externalConfig.wepKeys[i];
                    hasWepKey = true;
                }
            }
            if (hasWepKey) {
                internalConfig.wepTxKeyIndex = externalConfig.wepTxKeyIndex;
            }
        }
        if (externalConfig.FQDN != null) {
            internalConfig.FQDN = externalConfig.FQDN;
        }
        if (externalConfig.providerFriendlyName != null) {
            internalConfig.providerFriendlyName = externalConfig.providerFriendlyName;
        }
        if (externalConfig.roamingConsortiumIds != null) {
            internalConfig.roamingConsortiumIds = externalConfig.roamingConsortiumIds.clone();
        }

        mergeSecurityParamsListWithInternalWifiConfiguration(internalConfig, externalConfig);
        mergeDppSecurityParamsWithInternalWifiConfiguration(internalConfig, externalConfig);

        // Copy over the |IpConfiguration| parameters if set.
        if (externalConfig.getIpConfiguration() != null) {
            IpConfiguration.IpAssignment ipAssignment = externalConfig.getIpAssignment();
            if (ipAssignment != IpConfiguration.IpAssignment.UNASSIGNED) {
                internalConfig.setIpAssignment(ipAssignment);
                if (ipAssignment == IpConfiguration.IpAssignment.STATIC) {
                    internalConfig.setStaticIpConfiguration(
                            new StaticIpConfiguration(externalConfig.getStaticIpConfiguration()));
                }
            }
            IpConfiguration.ProxySettings proxySettings = externalConfig.getProxySettings();
            if (proxySettings != IpConfiguration.ProxySettings.UNASSIGNED) {
                internalConfig.setProxySettings(proxySettings);
                if (proxySettings == IpConfiguration.ProxySettings.PAC
                        || proxySettings == IpConfiguration.ProxySettings.STATIC) {
                    internalConfig.setHttpProxy(new ProxyInfo(externalConfig.getHttpProxy()));
                }
            }
        }

        internalConfig.allowAutojoin = externalConfig.allowAutojoin;
        // Copy over the |WifiEnterpriseConfig| parameters if set. For fields which should
        // only be set by the framework, cache the internal config's value and restore.
        if (externalConfig.enterpriseConfig != null) {
            boolean userApproveNoCaCertInternal =
                    internalConfig.enterpriseConfig.isUserApproveNoCaCert();
            int tofuDialogStateInternal = internalConfig.enterpriseConfig.getTofuDialogState();
            int tofuConnectionState = mergeTofuConnectionState(internalConfig, externalConfig);
            internalConfig.enterpriseConfig.copyFromExternal(
                    externalConfig.enterpriseConfig, PASSWORD_MASK);
            internalConfig.enterpriseConfig.setUserApproveNoCaCert(userApproveNoCaCertInternal);
            internalConfig.enterpriseConfig.setTofuDialogState(tofuDialogStateInternal);
            internalConfig.enterpriseConfig.setTofuConnectionState(tofuConnectionState);
        }

        // Copy over any metered information.
        internalConfig.meteredHint = externalConfig.meteredHint;
        internalConfig.meteredOverride = externalConfig.meteredOverride;

        internalConfig.trusted = externalConfig.trusted;
        internalConfig.oemPaid = externalConfig.oemPaid;
        internalConfig.oemPrivate = externalConfig.oemPrivate;
        internalConfig.carrierMerged = externalConfig.carrierMerged;
        internalConfig.restricted = externalConfig.restricted;

        // Copy over macRandomizationSetting
        internalConfig.macRandomizationSetting = externalConfig.macRandomizationSetting;
        internalConfig.carrierId = externalConfig.carrierId;
        internalConfig.isHomeProviderNetwork = externalConfig.isHomeProviderNetwork;
        internalConfig.subscriptionId = externalConfig.subscriptionId;
        internalConfig.setSubscriptionGroup(externalConfig.getSubscriptionGroup());
        internalConfig.getNetworkSelectionStatus()
                .setConnectChoice(externalConfig.getNetworkSelectionStatus().getConnectChoice());
        internalConfig.getNetworkSelectionStatus().setConnectChoiceRssi(
                externalConfig.getNetworkSelectionStatus().getConnectChoiceRssi());
        internalConfig.setBssidAllowlist(externalConfig.getBssidAllowlistInternal());
        internalConfig.setRepeaterEnabled(externalConfig.isRepeaterEnabled());
        internalConfig.setSendDhcpHostnameEnabled(externalConfig.isSendDhcpHostnameEnabled());
        internalConfig.setWifi7Enabled(externalConfig.isWifi7Enabled());
    }

    /**
     * Set all the exposed defaults in the newly created WifiConfiguration object.
     * These fields have a default value advertised in our public documentation. The only exception
     * is the hidden |IpConfiguration| parameters, these have a default value even though they're
     * hidden.
     *
     * @param configuration provided WifiConfiguration object.
     */
    private void setDefaultsInWifiConfiguration(WifiConfiguration configuration) {
        configuration.setIpAssignment(IpConfiguration.IpAssignment.DHCP);
        configuration.setProxySettings(IpConfiguration.ProxySettings.NONE);

        configuration.status = WifiConfiguration.Status.DISABLED;
        configuration.getNetworkSelectionStatus().setNetworkSelectionStatus(
                NetworkSelectionStatus.NETWORK_SELECTION_PERMANENTLY_DISABLED);
        configuration.getNetworkSelectionStatus().setNetworkSelectionDisableReason(
                NetworkSelectionStatus.DISABLED_BY_WIFI_MANAGER);
    }

    /**
     * Create a new internal WifiConfiguration object by copying over parameters from the provided
     * external configuration and set defaults for the appropriate parameters.
     *
     * @param externalConfig WifiConfiguration object provided from the external API.
     * @return New WifiConfiguration object with parameters merged from the provided external
     * configuration.
     */
    private WifiConfiguration createNewInternalWifiConfigurationFromExternal(
            WifiConfiguration externalConfig, int uid, @Nullable String packageName) {
        WifiConfiguration newInternalConfig = new WifiConfiguration();

        // First allocate a new network ID for the configuration.
        newInternalConfig.networkId = mNextNetworkId++;

        // First set defaults in the new configuration created.
        setDefaultsInWifiConfiguration(newInternalConfig);

        // Convert legacy fields to new security params
        externalConfig.convertLegacyFieldsToSecurityParamsIfNeeded();

        // Copy over all the public elements from the provided configuration.
        mergeWithInternalWifiConfiguration(newInternalConfig, externalConfig);

        // Copy over the hidden configuration parameters. These are the only parameters used by
        // system apps to indicate some property about the network being added.
        // These are only copied over for network additions and ignored for network updates.
        newInternalConfig.noInternetAccessExpected = externalConfig.noInternetAccessExpected;
        newInternalConfig.ephemeral = externalConfig.ephemeral;
        newInternalConfig.osu = externalConfig.osu;
        newInternalConfig.fromWifiNetworkSuggestion = externalConfig.fromWifiNetworkSuggestion;
        newInternalConfig.fromWifiNetworkSpecifier = externalConfig.fromWifiNetworkSpecifier;
        newInternalConfig.useExternalScores = externalConfig.useExternalScores;
        newInternalConfig.shared = externalConfig.shared;
        newInternalConfig.updateIdentifier = externalConfig.updateIdentifier;
        newInternalConfig.setPasspointUniqueId(externalConfig.getPasspointUniqueId());

        // Add debug information for network addition.
        newInternalConfig.creatorUid = newInternalConfig.lastUpdateUid = uid;
        newInternalConfig.creatorName = newInternalConfig.lastUpdateName =
                packageName != null ? packageName : mContext.getPackageManager().getNameForUid(uid);
        newInternalConfig.lastUpdated = mClock.getWallClockMillis();
        newInternalConfig.numRebootsSinceLastUse = 0;
        initRandomizedMacForInternalConfig(newInternalConfig);
        return newInternalConfig;
    }

    /**
     * Create a new internal WifiConfiguration object by copying over parameters from the provided
     * external configuration to a copy of the existing internal WifiConfiguration object.
     *
     * @param internalConfig WifiConfiguration object in our internal map.
     * @param externalConfig WifiConfiguration object provided from the external API.
     * @param overrideCreator when this set to true, will overrider the creator to the current
     *                        modifier.
     * @return Copy of existing WifiConfiguration object with parameters merged from the provided
     * configuration.
     */
    private @NonNull WifiConfiguration updateExistingInternalWifiConfigurationFromExternal(
            @NonNull WifiConfiguration internalConfig, @NonNull WifiConfiguration externalConfig,
            int uid, @Nullable String packageName, boolean overrideCreator) {
        WifiConfiguration newInternalConfig = new WifiConfiguration(internalConfig);

        // Copy over all the public elements from the provided configuration.
        mergeWithInternalWifiConfiguration(newInternalConfig, externalConfig);

        // Add debug information for network update.
        newInternalConfig.lastUpdateUid = uid;
        newInternalConfig.lastUpdateName =
                packageName != null ? packageName : mContext.getPackageManager().getNameForUid(uid);
        newInternalConfig.lastUpdated = mClock.getWallClockMillis();
        newInternalConfig.numRebootsSinceLastUse = 0;
        if (overrideCreator) {
            newInternalConfig.creatorName = newInternalConfig.lastUpdateName;
            newInternalConfig.creatorUid = uid;
        }
        return newInternalConfig;
    }

    private void logUserActionEvents(WifiConfiguration before, WifiConfiguration after) {
        // Logs changes in meteredOverride.
        if (before.meteredOverride != after.meteredOverride) {
            mWifiMetrics.logUserActionEvent(
                    WifiMetrics.convertMeteredOverrideEnumToUserActionEventType(
                            after.meteredOverride),
                    after.networkId);
        }

        // Logs changes in macRandomizationSetting.
        if (before.macRandomizationSetting != after.macRandomizationSetting) {
            mWifiMetrics.logUserActionEvent(
                    after.macRandomizationSetting == WifiConfiguration.RANDOMIZATION_NONE
                            ? UserActionEvent.EVENT_CONFIGURE_MAC_RANDOMIZATION_OFF
                            : UserActionEvent.EVENT_CONFIGURE_MAC_RANDOMIZATION_ON,
                    after.networkId);
        }
    }

    /**
     * Add a network or update a network configuration to our database.
     * If the supplied networkId is INVALID_NETWORK_ID, we create a new empty
     * network configuration. Otherwise, the networkId should refer to an existing configuration.
     *
     * @param config provided WifiConfiguration object.
     * @param uid UID of the app requesting the network addition/modification.
     * @param packageName Package name of the app requesting the network addition/modification.
     * @param overrideCreator when this set to true, will overrider the creator to the current
     *                        modifier.
     * @return NetworkUpdateResult object representing status of the update.
     *         WifiConfiguration object representing the existing configuration matching
     *         the new config, or null if none matches.
     */
    private @NonNull Pair<NetworkUpdateResult, WifiConfiguration> addOrUpdateNetworkInternal(
            @NonNull WifiConfiguration config, int uid, @Nullable String packageName,
            boolean overrideCreator) {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "Adding/Updating network " + config.getPrintableSsid());
        }
        WifiConfiguration newInternalConfig = null;

        long supportedFeatures = mWifiInjector.getActiveModeWarden()
                .getPrimaryClientModeManager().getSupportedFeatures();

        // First check if we already have a network with the provided network id or configKey.
        WifiConfiguration existingInternalConfig = getInternalConfiguredNetwork(config);
        // No existing network found. So, potentially a network add.
        if (existingInternalConfig == null) {
            if (!WifiConfigurationUtil.validate(config, supportedFeatures,
                    WifiConfigurationUtil.VALIDATE_FOR_ADD)) {
                Log.e(TAG, "Cannot add network with invalid config");
                return new Pair<>(
                        new NetworkUpdateResult(WifiConfiguration.INVALID_NETWORK_ID,
                                STATUS_INVALID_CONFIGURATION),
                        existingInternalConfig);
            }
            newInternalConfig =
                    createNewInternalWifiConfigurationFromExternal(config, uid, packageName);
            // Since the original config provided may have had an empty
            // {@link WifiConfiguration#allowedKeyMgmt} field, check again if we already have a
            // network with the the same configkey.
            existingInternalConfig =
                    getInternalConfiguredNetwork(newInternalConfig.getProfileKey());
        }
        // Existing network found. So, a network update.
        if (existingInternalConfig != null) {
            if (!WifiConfigurationUtil.validate(
                    config, supportedFeatures, WifiConfigurationUtil.VALIDATE_FOR_UPDATE)) {
                Log.e(TAG, "Cannot update network with invalid config");
                return new Pair<>(
                        new NetworkUpdateResult(WifiConfiguration.INVALID_NETWORK_ID,
                                STATUS_INVALID_CONFIGURATION),
                        existingInternalConfig);
            }
            // Check for the app's permission before we let it update this network.
            if (!canModifyNetwork(existingInternalConfig, uid, packageName)) {
                Log.e(TAG, "UID " + uid + " does not have permission to update configuration "
                        + config.getProfileKey());
                return new Pair<>(
                        new NetworkUpdateResult(WifiConfiguration.INVALID_NETWORK_ID,
                                STATUS_NO_PERMISSION_MODIFY_CONFIG),
                        existingInternalConfig);
            }
            if (mWifiPermissionsUtil.checkNetworkSettingsPermission(uid)
                    && !config.isPasspoint()) {
                logUserActionEvents(existingInternalConfig, config);
            }
            newInternalConfig =
                    updateExistingInternalWifiConfigurationFromExternal(
                            existingInternalConfig, config, uid, packageName, overrideCreator);
        }

        if (!WifiConfigurationUtil.addUpgradableSecurityTypeIfNecessary(newInternalConfig)) {
            return new Pair<>(
                    new NetworkUpdateResult(WifiConfiguration.INVALID_NETWORK_ID),
                    existingInternalConfig);
        }

        // Only add networks with proxy settings if the user has permission to
        if (WifiConfigurationUtil.hasProxyChanged(existingInternalConfig, newInternalConfig)
                && !canModifyProxySettings(uid, packageName)) {
            Log.e(TAG, "UID " + uid + " does not have permission to modify proxy Settings "
                    + config.getProfileKey() + ". Must have NETWORK_SETTINGS,"
                    + " or be device or profile owner.");
            return new Pair<>(
                    new NetworkUpdateResult(WifiConfiguration.INVALID_NETWORK_ID),
                    existingInternalConfig);
        }

        // Only allow changes in Repeater Enabled flag if the user has permission to
        if (WifiConfigurationUtil.hasRepeaterEnabledChanged(
                existingInternalConfig, newInternalConfig)
                && !mWifiPermissionsUtil.checkNetworkSettingsPermission(uid)
                && !mWifiPermissionsUtil.checkConfigOverridePermission(uid)) {
            Log.e(TAG, "UID " + uid
                    + " does not have permission to modify Repeater Enabled Settings "
                    + " , or add a network with Repeater Enabled set to true "
                    + config.getProfileKey() + ". Must have NETWORK_SETTINGS.");
            return new Pair<>(
                    new NetworkUpdateResult(WifiConfiguration.INVALID_NETWORK_ID),
                    existingInternalConfig);
        }

        if (WifiConfigurationUtil.hasMacRandomizationSettingsChanged(existingInternalConfig,
                newInternalConfig) && !mWifiPermissionsUtil.checkNetworkSettingsPermission(uid)
                && !mWifiPermissionsUtil.checkNetworkSetupWizardPermission(uid)
                && !mWifiPermissionsUtil.checkConfigOverridePermission(uid)
                && !(newInternalConfig.isPasspoint() && uid == newInternalConfig.creatorUid)
                && !config.fromWifiNetworkSuggestion
                && !mWifiPermissionsUtil.isDeviceInDemoMode(mContext)
                && !(mWifiPermissionsUtil.isAdmin(uid, packageName)
                && uid == newInternalConfig.creatorUid)) {
            Log.e(TAG, "UID " + uid + " does not have permission to modify MAC randomization "
                    + "Settings " + config.getProfileKey() + ". Must have "
                    + "NETWORK_SETTINGS or NETWORK_SETUP_WIZARD or be in Demo Mode "
                    + "or be the creator adding or updating a passpoint network "
                    + "or be an admin updating their own network.");
            return new Pair<>(
                    new NetworkUpdateResult(WifiConfiguration.INVALID_NETWORK_ID),
                    existingInternalConfig);
        }

        if (WifiConfigurationUtil.hasSendDhcpHostnameEnabledChanged(existingInternalConfig,
                newInternalConfig) && !mWifiPermissionsUtil.checkNetworkSettingsPermission(uid)
                && !mWifiPermissionsUtil.checkNetworkSetupWizardPermission(uid)) {
            Log.e(TAG, "UID " + uid + " does not have permission to modify send DHCP hostname "
                    + "setting " + config.getProfileKey() + ". Must have "
                    + "NETWORK_SETTINGS or NETWORK_SETUP_WIZARD.");
            return new Pair<>(
                    new NetworkUpdateResult(WifiConfiguration.INVALID_NETWORK_ID),
                    existingInternalConfig);
        }

        if (config.isEnterprise()
                && config.enterpriseConfig.isEapMethodServerCertUsed()
                && !config.enterpriseConfig.isMandatoryParameterSetForServerCertValidation()
                && !config.enterpriseConfig.isTrustOnFirstUseEnabled()) {
            boolean isSettingsOrSuw = mContext.checkPermission(Manifest.permission.NETWORK_SETTINGS,
                    -1 /* pid */, uid) == PERMISSION_GRANTED
                    || mContext.checkPermission(Manifest.permission.NETWORK_SETUP_WIZARD,
                    -1 /* pid */, uid) == PERMISSION_GRANTED;
            if (!(mWifiInjector.getWifiGlobals().isInsecureEnterpriseConfigurationAllowed()
                    && isSettingsOrSuw)) {
                Log.e(TAG, "Enterprise network configuration is missing either a Root CA "
                        + "or a domain name");
                return new Pair<>(
                        new NetworkUpdateResult(WifiConfiguration.INVALID_NETWORK_ID,
                                STATUS_INVALID_CONFIGURATION_ENTERPRISE),
                        existingInternalConfig);
            }
            Log.w(TAG, "Insecure Enterprise network " + config.SSID
                    + " configured by Settings/SUW");

            // Implicit user approval, when creating an insecure connection which is allowed
            // in the configuration of the device
            newInternalConfig.enterpriseConfig.setUserApproveNoCaCert(true);
        }

        // Update the keys for saved enterprise networks. For Passpoint, the certificates
        // and keys are installed at the time the provider is installed. For suggestion enterprise
        // network the certificates and keys are installed at the time the suggestion is added
        if (!config.isPasspoint() && !config.fromWifiNetworkSuggestion && config.isEnterprise()) {
            if (!(mWifiKeyStore.updateNetworkKeys(newInternalConfig, existingInternalConfig))) {
                return new Pair<>(
                        new NetworkUpdateResult(WifiConfiguration.INVALID_NETWORK_ID),
                        existingInternalConfig);
            }
        }

        // Validate an Enterprise network with Trust On First Use.
        if (config.isEnterprise() && config.enterpriseConfig.isTrustOnFirstUseEnabled()) {
            if ((supportedFeatures & WIFI_FEATURE_TRUST_ON_FIRST_USE) == 0) {
                Log.e(TAG, "Trust On First Use could not be set "
                        + "when Trust On First Use is not supported.");
                return new Pair<>(
                        new NetworkUpdateResult(WifiConfiguration.INVALID_NETWORK_ID),
                        existingInternalConfig);
            }
            if (!config.enterpriseConfig.isEapMethodServerCertUsed()) {
                Log.e(TAG, "Trust On First Use could not be set "
                        + "when the server certificate is not used.");
                return new Pair<>(
                        new NetworkUpdateResult(WifiConfiguration.INVALID_NETWORK_ID),
                        existingInternalConfig);
            } else if (config.enterpriseConfig.hasCaCertificate()) {
                Log.e(TAG, "Trust On First Use could not be set "
                        + "when Root CA certificate is set.");
                return new Pair<>(
                        new NetworkUpdateResult(WifiConfiguration.INVALID_NETWORK_ID),
                        existingInternalConfig);
            }
        }

        boolean newNetwork = (existingInternalConfig == null);
        // This is needed to inform IpClient about any IP configuration changes.
        boolean hasIpChanged =
                newNetwork || WifiConfigurationUtil.hasIpChanged(
                        existingInternalConfig, newInternalConfig);
        boolean hasProxyChanged =
                newNetwork || WifiConfigurationUtil.hasProxyChanged(
                        existingInternalConfig, newInternalConfig);
        // Reset the |hasEverConnected| flag if the credential parameters changed in this update.
        boolean hasCredentialChanged =
                newNetwork || WifiConfigurationUtil.hasCredentialChanged(
                        existingInternalConfig, newInternalConfig);
        if (hasCredentialChanged) {
            newInternalConfig.getNetworkSelectionStatus().setHasEverConnected(false);
            newInternalConfig.setHasPreSharedKeyChanged(true);
            Log.i(TAG, "Credential changed for netId=" + newInternalConfig.networkId);
        }

        // Add it to our internal map. This will replace any existing network configuration for
        // updates.
        try {
            if (null != existingInternalConfig) {
                mConfiguredNetworks.remove(existingInternalConfig.networkId);
            }
            mConfiguredNetworks.put(newInternalConfig);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Failed to add network to config map", e);
            return new Pair<>(
                    new NetworkUpdateResult(WifiConfiguration.INVALID_NETWORK_ID),
                    existingInternalConfig);
        }
        if (removeExcessNetworks(uid, packageName)) {
            if (mConfiguredNetworks.getForAllUsers(newInternalConfig.networkId) == null) {
                Log.e(TAG, "Cannot add network because number of configured networks is maxed.");
                return new Pair<>(
                        new NetworkUpdateResult(WifiConfiguration.INVALID_NETWORK_ID),
                        existingInternalConfig);
            }
        }

        // Only re-enable network: 1. add or update user saved network; 2. add or update a user
        // saved passpoint network framework consider it is a new network.
        if (!newInternalConfig.fromWifiNetworkSuggestion
                && (!newInternalConfig.isPasspoint() || newNetwork)) {
            userEnabledNetwork(newInternalConfig.networkId);
        }

        // Stage the backup of the SettingsProvider package which backs this up.
        mBackupManagerProxy.notifyDataChanged();

        NetworkUpdateResult result = new NetworkUpdateResult(
                newInternalConfig.networkId,
                STATUS_SUCCESS,
                hasIpChanged,
                hasProxyChanged,
                hasCredentialChanged,
                newNetwork);

        localLog("addOrUpdateNetworkInternal: added/updated config."
                + " netId=" + newInternalConfig.networkId
                + " configKey=" + newInternalConfig.getProfileKey()
                + " uid=" + Integer.toString(newInternalConfig.creatorUid)
                + " name=" + newInternalConfig.creatorName);
        return new Pair<>(result, existingInternalConfig);
    }

    /**
     * Add a network or update a network configuration to our database.
     * If the supplied networkId is INVALID_NETWORK_ID, we create a new empty
     * network configuration. Otherwise, the networkId should refer to an existing configuration.
     *
     * @param config provided WifiConfiguration object.
     * @param uid UID of the app requesting the network addition/modification.
     * @param packageName Package name of the app requesting the network addition/modification.
     * @param overrideCreator when this set to true, will overrider the creator to the current
     *                        modifier.
     * @return NetworkUpdateResult object representing status of the update.
     */
    public NetworkUpdateResult addOrUpdateNetwork(WifiConfiguration config, int uid,
            @Nullable String packageName, boolean overrideCreator) {
        if (!mWifiPermissionsUtil.doesUidBelongToCurrentUserOrDeviceOwner(uid)) {
            Log.e(TAG, "UID " + uid + " not visible to the current user");
            return new NetworkUpdateResult(WifiConfiguration.INVALID_NETWORK_ID);
        }
        if (config == null) {
            Log.e(TAG, "Cannot add/update network with null config");
            return new NetworkUpdateResult(WifiConfiguration.INVALID_NETWORK_ID);
        }
        if (SdkLevel.isAtLeastV() && config.getVendorData() != null
                && !config.getVendorData().isEmpty()
                && !mWifiPermissionsUtil.checkManageWifiNetworkSelectionPermission(uid)) {
            Log.e(TAG, "UID " + uid + " does not have permission to include vendor data");
            return new NetworkUpdateResult(WifiConfiguration.INVALID_NETWORK_ID);
        }
        if (mPendingStoreRead) {
            Log.e(TAG, "Cannot add/update network before store is read!");
            return new NetworkUpdateResult(WifiConfiguration.INVALID_NETWORK_ID);
        }
        config.convertLegacyFieldsToSecurityParamsIfNeeded();
        WifiConfiguration existingConfig = getInternalConfiguredNetwork(config);
        if (!config.isEphemeral()) {
            // Removes the existing ephemeral network if it exists to add this configuration.
            if (existingConfig != null && existingConfig.isEphemeral()) {
                // In this case, new connection for this config won't happen because same
                // network is already registered as an ephemeral network.
                // Clear the Ephemeral Network to address the situation.
                removeNetwork(
                        existingConfig.networkId, existingConfig.creatorUid, config.creatorName);
            }
        }

        Pair<NetworkUpdateResult, WifiConfiguration> resultPair = addOrUpdateNetworkInternal(
                config, uid, packageName, overrideCreator);
        NetworkUpdateResult result = resultPair.first;
        existingConfig = resultPair.second;
        if (!result.isSuccess()) {
            Log.e(TAG, "Failed to add/update network " + config.getPrintableSsid());
            return result;
        }
        WifiConfiguration newConfig = getInternalConfiguredNetwork(result.getNetworkId());
        sendConfiguredNetworkChangedBroadcast(
                result.isNewNetwork()
                        ? WifiManager.CHANGE_REASON_ADDED
                        : WifiManager.CHANGE_REASON_CONFIG_CHANGE, newConfig);
        // Unless the added network is ephemeral or Passpoint, persist the network update/addition.
        if (!config.ephemeral && !config.isPasspoint()) {
            saveToStore(true);
        }

        for (OnNetworkUpdateListener listener : mListeners) {
            if (result.isNewNetwork()) {
                listener.onNetworkAdded(
                        createExternalWifiConfiguration(newConfig, true, Process.WIFI_UID));
            } else {
                listener.onNetworkUpdated(
                        createExternalWifiConfiguration(newConfig, true, Process.WIFI_UID),
                        createExternalWifiConfiguration(existingConfig, true, Process.WIFI_UID),
                        result.hasCredentialChanged());
            }
        }
        return result;
    }

    /**
     * Adds a network configuration to our database if a matching configuration cannot be found.
     * @param config provided WifiConfiguration object.
     * @param uid    UID of the app requesting the network addition.
     * @return
     */
    public NetworkUpdateResult addNetwork(WifiConfiguration config, int uid) {
        config.convertLegacyFieldsToSecurityParamsIfNeeded();
        if (getInternalConfiguredNetwork(config) == null) {
            return addOrUpdateNetwork(config, uid);
        }
        return new NetworkUpdateResult(WifiConfiguration.INVALID_NETWORK_ID);
    }

    /**
     * Add a network or update a network configuration to our database.
     * If the supplied networkId is INVALID_NETWORK_ID, we create a new empty
     * network configuration. Otherwise, the networkId should refer to an existing configuration.
     *
     * @param config provided WifiConfiguration object.
     * @param uid    UID of the app requesting the network addition/modification.
     * @return NetworkUpdateResult object representing status of the update.
     */
    public NetworkUpdateResult addOrUpdateNetwork(WifiConfiguration config, int uid) {
        return addOrUpdateNetwork(config, uid, null, false);
    }

    /**
     * Increments the number of reboots since last use for each configuration.
     *
     * @see {@link WifiConfiguration#numRebootsSinceLastUse}
     */
    public void incrementNumRebootsSinceLastUse() {
        getInternalConfiguredNetworks().forEach(config -> config.numRebootsSinceLastUse++);
        saveToStore(false);
    }

    private boolean isDeviceOwnerProfileOwnerOrSystem(int uid, String packageName) {
        return mWifiPermissionsUtil.isDeviceOwner(uid, packageName)
                || mWifiPermissionsUtil.isProfileOwner(uid, packageName)
                || mWifiPermissionsUtil.isSystem(packageName, uid)
                || mWifiPermissionsUtil.isSignedWithPlatformKey(uid);
    }

    /**
     * Filter non app-added networks from the input list.
     *
     * Note: Optimized to avoid checking the permissions for each config in the input list,
     * since {@link WifiPermissionsUtil#isProfileOwner(int, String)} is fairly expensive.
     *
     * Many configs will have the same creator, so we can cache the permissions per-creator.
     *
     * @param networks List of WifiConfigurations to filter.
     * @return List of app-added networks.
     */
    @VisibleForTesting
    protected List<WifiConfiguration> filterNonAppAddedNetworks(List<WifiConfiguration> networks) {
        List<WifiConfiguration> appAddedNetworks = new ArrayList<>();
        Map<Pair<Integer, String>, Boolean> isAppAddedCache = new ArrayMap<>();

        for (WifiConfiguration network : networks) {
            Pair<Integer, String> identityPair =
                    new Pair<>(network.creatorUid, network.creatorName);
            boolean isAppAdded;

            // Checking the DO/PO/System permissions is expensive - cache the result.
            if (isAppAddedCache.containsKey(identityPair)) {
                isAppAdded = isAppAddedCache.get(identityPair);
            } else {
                isAppAdded = !isDeviceOwnerProfileOwnerOrSystem(
                        network.creatorUid, network.creatorName);
                isAppAddedCache.put(identityPair, isAppAdded);
            }

            if (isAppAdded) {
                appAddedNetworks.add(network);
            }
        }
        return appAddedNetworks;
    }

    /**
     * Removes excess networks in case the number of saved networks exceeds the max limit
     * specified in config_wifiMaxNumWifiConfigurations.
     *
     * If called by a non DO/PO/system app, and a limit on app-added networks is specified in
     * config_wifiMaxNumWifiConfigurationsForAppAddedNetworks, only removes excess
     * app-added networks.
     *
     * Configs are removed in ascending order of
     *     1. Non-carrier networks before carrier networks
     *     2. Non-connected networks before connected networks.
     *     3. Deletion priority {@see WifiConfiguration#getDeletionPriority()}
     *     4. Last use/creation/update time (lastUpdated/lastConnected or numRebootsSinceLastUse)
     *     5. Open and OWE networks before networks with other security types.
     *     6. Number of associations
     *
     * @param uid    UID of the app requesting the network addition/modification.
     * @param packageName Package name of the app requesting the network addition/modification.
     * @return {@code true} if networks were removed, {@code false} otherwise.
     */
    private boolean removeExcessNetworks(int uid, String packageName) {
        final int maxNumTotalConfigs = mContext.getResources().getInteger(
                R.integer.config_wifiMaxNumWifiConfigurations);
        final int maxNumAppAddedConfigs = mContext.getResources().getInteger(
                R.integer.config_wifiMaxNumWifiConfigurationsAddedByAllApps);

        boolean callerIsApp = !isDeviceOwnerProfileOwnerOrSystem(uid, packageName);
        if (maxNumTotalConfigs < 0 && (!callerIsApp || maxNumAppAddedConfigs < 0)) {
            // Max number of saved networks not specified or does not need to be checked.
            return false;
        }

        int numExcessNetworks = -1;
        List<WifiConfiguration> networkList = getSavedNetworks(Process.WIFI_UID);
        if (maxNumTotalConfigs >= 0) {
            numExcessNetworks = networkList.size() - maxNumTotalConfigs;
        }

        if (callerIsApp && maxNumAppAddedConfigs >= 0
                && networkList.size() > maxNumAppAddedConfigs) {
            List<WifiConfiguration> appAddedNetworks = filterNonAppAddedNetworks(networkList);
            int numExcessAppAddedNetworks = appAddedNetworks.size() - maxNumAppAddedConfigs;
            if (numExcessAppAddedNetworks > 0) {
                // Only enforce the limit on app-added networks if it has been exceeded.
                // Otherwise, default to checking the limit on the total number of networks.
                numExcessNetworks = numExcessAppAddedNetworks;
                networkList = appAddedNetworks;
            }
        }

        if (numExcessNetworks <= 0) {
            return false;
        }

        List<WifiConfiguration> configsToDelete = networkList
                .stream()
                .sorted(Comparator.comparing((WifiConfiguration config) -> config.carrierId
                        != TelephonyManager.UNKNOWN_CARRIER_ID)
                        .thenComparing((WifiConfiguration config) -> config.isCurrentlyConnected)
                        .thenComparing((WifiConfiguration config) -> config.getDeletionPriority())
                        .thenComparing((WifiConfiguration config) -> -config.numRebootsSinceLastUse)
                        .thenComparing((WifiConfiguration config) ->
                                Math.max(config.lastConnected, config.lastUpdated))
                        .thenComparing((WifiConfiguration config) -> {
                            try {
                                int authType = config.getAuthType();
                                return !(authType == WifiConfiguration.KeyMgmt.NONE
                                        || authType == WifiConfiguration.KeyMgmt.OWE);
                            } catch (IllegalStateException e) {
                                // An invalid keymgmt configuration should be pruned first.
                                return false;
                            }
                        })
                        .thenComparing((WifiConfiguration config) -> config.numAssociation))
                .limit(numExcessNetworks)
                .collect(Collectors.toList());
        for (WifiConfiguration config : configsToDelete) {
            mConfiguredNetworks.remove(config.networkId);
            localLog("removeExcessNetworks: removed config."
                    + " netId=" + config.networkId
                    + " configKey=" + config.getProfileKey());
        }
        return true;
    }

    /**
     * Removes the specified network configuration from our database.
     *
     * @param config provided WifiConfiguration object.
     * @param uid UID of the app requesting the network deletion.
     * @return true if successful, false otherwise.
     */
    private boolean removeNetworkInternal(WifiConfiguration config, int uid) {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "Removing network " + config.getPrintableSsid());
        }
        // Remove any associated enterprise keys for saved enterprise networks. Passpoint network
        // will remove the enterprise keys when provider is uninstalled. Suggestion enterprise
        // networks will remove the enterprise keys when suggestion is removed.
        if (!config.fromWifiNetworkSuggestion && !config.isPasspoint() && config.isEnterprise()) {
            mWifiKeyStore.removeKeys(config.enterpriseConfig, false);
        }

        // Do not remove the user choice when passpoint or suggestion networks are removed from
        // WifiConfigManager. Will remove that when profile is deleted from PassointManager or
        // WifiNetworkSuggestionsManager.
        if (!config.isPasspoint() && !config.fromWifiNetworkSuggestion) {
            removeConnectChoiceFromAllNetworks(config.getProfileKey());
        }
        mConfiguredNetworks.remove(config.networkId);
        mScanDetailCaches.remove(config.networkId);
        // Stage the backup of the SettingsProvider package which backs this up.
        mBackupManagerProxy.notifyDataChanged();
        mWifiBlocklistMonitor.handleNetworkRemoved(config.SSID);

        localLog("removeNetworkInternal: removed config."
                + " netId=" + config.networkId
                + " configKey=" + config.getProfileKey()
                + " uid=" + Integer.toString(uid)
                + " name=" + mContext.getPackageManager().getNameForUid(uid));
        return true;
    }

    /**
     * Removes the specified network configuration from our database.
     *
     * @param networkId network ID of the provided network.
     * @param uid       UID of the app requesting the network deletion.
     * @return true if successful, false otherwise.
     */
    public boolean removeNetwork(int networkId, int uid, String packageName) {
        if (!mWifiPermissionsUtil.doesUidBelongToCurrentUserOrDeviceOwner(uid)) {
            Log.e(TAG, "UID " + uid + " not visible to the current user");
            return false;
        }
        WifiConfiguration config = getInternalConfiguredNetwork(networkId);
        if (config == null) {
            return false;
        }
        if (!canModifyNetwork(config, uid, packageName)) {
            Log.e(TAG, "UID " + uid + " does not have permission to delete configuration "
                    + config.getProfileKey());
            return false;
        }
        if (!removeNetworkInternal(config, uid)) {
            Log.e(TAG, "Failed to remove network " + config.getPrintableSsid());
            return false;
        }
        if (networkId == mLastSelectedNetworkId) {
            clearLastSelectedNetwork();
        }
        if (!config.ephemeral && !config.isPasspoint()) {
            mLruConnectionTracker.removeNetwork(config);
        }
        sendConfiguredNetworkChangedBroadcast(WifiManager.CHANGE_REASON_REMOVED, config);
        // Unless the removed network is ephemeral or Passpoint, persist the network removal.
        if (!config.ephemeral && !config.isPasspoint()) {
            saveToStore(true);
        }
        for (OnNetworkUpdateListener listener : mListeners) {
            listener.onNetworkRemoved(
                    createExternalWifiConfiguration(config, true, Process.WIFI_UID));
        }
        return true;
    }

    private String getCreatorPackageName(WifiConfiguration config) {
        String creatorName = config.creatorName;
        // getNameForUid (Stored in WifiConfiguration.creatorName) returns a concatenation of name
        // and uid for shared UIDs ("name:uid").
        if (!creatorName.contains(":")) {
            return creatorName; // regular app not using shared UID.
        }
        // Separate the package name from the string for app using shared UID.
        return creatorName.substring(0, creatorName.indexOf(":"));
    }

    /**
     * Remove all networks associated with an application.
     *
     * @param app Application info of the package of networks to remove.
     * @return the {@link Set} of networks that were removed by this call. Networks which matched
     *         but failed to remove are omitted from this set.
     */
    public Set<Integer> removeNetworksForApp(ApplicationInfo app) {
        if (app == null || app.packageName == null) {
            return Collections.<Integer>emptySet();
        }
        Log.d(TAG, "Remove all networks for app " + app);
        Set<Integer> removedNetworks = new ArraySet<>();
        WifiConfiguration[] copiedConfigs =
                mConfiguredNetworks.valuesForAllUsers().toArray(new WifiConfiguration[0]);
        for (WifiConfiguration config : copiedConfigs) {
            if (app.uid != config.creatorUid
                    || !app.packageName.equals(getCreatorPackageName(config))) {
                continue;
            }
            localLog("Removing network " + config.SSID
                    + ", application \"" + app.packageName + "\" uninstalled"
                    + " from user " + UserHandle.getUserHandleForUid(app.uid));
            if (removeNetwork(config.networkId, config.creatorUid, config.creatorName)) {
                removedNetworks.add(config.networkId);
            }
        }
        return removedNetworks;
    }

    /**
     * Remove all networks associated with a user.
     *
     * @param userId The identifier of the user which is being removed.
     * @return the {@link Set} of networks that were removed by this call. Networks which matched
     *         but failed to remove are omitted from this set.
     */
    Set<Integer> removeNetworksForUser(int userId) {
        Log.d(TAG, "Remove all networks for user " + userId);
        Set<Integer> removedNetworks = new ArraySet<>();
        WifiConfiguration[] copiedConfigs =
                mConfiguredNetworks.valuesForAllUsers().toArray(new WifiConfiguration[0]);
        for (WifiConfiguration config : copiedConfigs) {
            if (userId != UserHandle.getUserHandleForUid(config.creatorUid).getIdentifier()) {
                continue;
            }
            localLog("Removing network " + config.SSID + ", user " + userId + " removed");
            if (removeNetwork(config.networkId, config.creatorUid, config.creatorName)) {
                removedNetworks.add(config.networkId);
            }
        }
        return removedNetworks;
    }

    /**
     * Iterates through the internal list of configured networks and removes any ephemeral or
     * passpoint network configurations which are transient in nature.
     *
     * @return true if a network was removed, false otherwise.
     */
    public boolean removeAllEphemeralOrPasspointConfiguredNetworks() {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "Removing all passpoint or ephemeral configured networks");
        }
        boolean didRemove = false;
        WifiConfiguration[] copiedConfigs =
                mConfiguredNetworks.valuesForAllUsers().toArray(new WifiConfiguration[0]);
        for (WifiConfiguration config : copiedConfigs) {
            if (config.isPasspoint()) {
                Log.d(TAG, "Removing passpoint network config " + config.getProfileKey());
                removeNetwork(config.networkId, config.creatorUid, config.creatorName);
                didRemove = true;
            } else if (config.ephemeral) {
                Log.d(TAG, "Removing ephemeral network config " + config.getProfileKey());
                removeNetwork(config.networkId, config.creatorUid, config.creatorName);
                didRemove = true;
            }
        }
        return didRemove;
    }

    /**
     * Removes the suggestion network configuration matched with WifiConfiguration provided.
     * @param suggestion WifiConfiguration for suggestion which needs to remove
     * @return true if a network was removed, false otherwise.
     */
    public boolean removeSuggestionConfiguredNetwork(@NonNull WifiConfiguration suggestion) {
        WifiConfiguration config = getInternalConfiguredNetwork(
                suggestion.getProfileKey());
        if (config != null && config.ephemeral && config.fromWifiNetworkSuggestion) {
            Log.d(TAG, "Removing suggestion network config " + config.getProfileKey());
            return removeNetwork(config.networkId, suggestion.creatorUid, suggestion.creatorName);
        }
        return false;
    }

    /**
     * Removes the passpoint network configuration matched with {@code configKey} provided.
     *
     * @param configKey Config Key for the corresponding passpoint.
     * @return true if a network was removed, false otherwise.
     */
    public boolean removePasspointConfiguredNetwork(@NonNull String configKey) {
        WifiConfiguration config = getInternalConfiguredNetwork(configKey);
        if (config != null && config.isPasspoint()) {
            Log.d(TAG, "Removing passpoint network config " + config.getProfileKey());
            return removeNetwork(config.networkId, config.creatorUid, config.creatorName);
        }
        return false;
    }

    /**
     * Removes all save networks configurations not created by the caller.
     *
     * @param callerUid the uid of the caller
     * @return {@code true} if at least one network is removed.
     */
    public boolean removeNonCallerConfiguredNetwork(int callerUid) {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "removeNonCallerConfiguredNetwork caller = " + callerUid);
        }
        boolean didRemove = false;
        WifiConfiguration[] copiedConfigs =
                mConfiguredNetworks.valuesForAllUsers().toArray(new WifiConfiguration[0]);
        for (WifiConfiguration config : copiedConfigs) {
            if (config.creatorUid != callerUid) {
                Log.d(TAG, "Removing non-caller network config " + config.getProfileKey());
                removeNetwork(config.networkId, config.creatorUid, config.creatorName);
                didRemove = true;
            }
        }
        return didRemove;
    }

    /**
     * Check whether a network belong to a known list of networks that may not support randomized
     * MAC.
     * @param networkId
     * @return true if the network is in the hotlist and MAC randomization is enabled.
     */
    public boolean isInFlakyRandomizationSsidHotlist(int networkId) {
        WifiConfiguration config = getConfiguredNetwork(networkId);
        return config != null
                && config.macRandomizationSetting != WifiConfiguration.RANDOMIZATION_NONE
                && mDeviceConfigFacade.getRandomizationFlakySsidHotlist().contains(config.SSID);
    }

    /**
     * Helper method to set the publicly exposed status for the network and send out the network
     * status change broadcast.
     */
    private void setNetworkStatus(WifiConfiguration config, int status) {
        config.status = status;
        sendConfiguredNetworkChangedBroadcast(WifiManager.CHANGE_REASON_CONFIG_CHANGE, config);
    }

    /**
     * Update a network's status (both internal and public) according to the update reason and
     * its current state.
     *
     * Each network has 2 status:
     * 1. NetworkSelectionStatus: This is internal selection status of the network. This is used
     * for temporarily disabling a network for Network Selector.
     * 2. Status: This is the exposed status for a network. This is mostly set by
     * the public API's {@link WifiManager#enableNetwork(int, boolean)} &
     * {@link WifiManager#disableNetwork(int)}.
     *
     * @param networkId network ID of the network that needs the update.
     * @param reason    reason to update the network.
     * @return true if the input configuration has been updated, false otherwise.
     */
    public boolean updateNetworkSelectionStatus(int networkId, int reason) {
        WifiConfiguration config = getInternalConfiguredNetwork(networkId);
        if (config == null) {
            return false;
        }
        return updateNetworkSelectionStatus(config, reason);
    }

    private boolean updateNetworkSelectionStatus(@NonNull WifiConfiguration config, int reason) {
        int prevNetworkSelectionStatus = config.getNetworkSelectionStatus()
                .getNetworkSelectionStatus();
        int prevAuthFailureCounter = config.getNetworkSelectionStatus().getDisableReasonCounter(
                WifiConfiguration.NetworkSelectionStatus.DISABLED_AUTHENTICATION_FAILURE);
        if (!mWifiBlocklistMonitor.updateNetworkSelectionStatus(config, reason)) {
            return false;
        }
        int newNetworkSelectionStatus = config.getNetworkSelectionStatus()
                .getNetworkSelectionStatus();
        int newAuthFailureCounter = config.getNetworkSelectionStatus().getDisableReasonCounter(
                WifiConfiguration.NetworkSelectionStatus.DISABLED_AUTHENTICATION_FAILURE);
        if (prevNetworkSelectionStatus != newNetworkSelectionStatus) {
            sendNetworkSelectionStatusChangedUpdate(config, newNetworkSelectionStatus, reason);
            sendConfiguredNetworkChangedBroadcast(WifiManager.CHANGE_REASON_CONFIG_CHANGE, config);
        } else if (prevAuthFailureCounter != newAuthFailureCounter) {
            // Send out configured network changed broadcast in this special case since the UI
            // may need to update the wrong password text.
            sendConfiguredNetworkChangedBroadcast(WifiManager.CHANGE_REASON_CONFIG_CHANGE, config);
        }
        saveToStore(false);
        return true;
    }

    private void sendNetworkSelectionStatusChangedUpdate(@NonNull WifiConfiguration config,
            int newNetworkSelectionStatus, int disableReason) {
        switch (newNetworkSelectionStatus) {
            case NetworkSelectionStatus.NETWORK_SELECTION_ENABLED:
                for (OnNetworkUpdateListener listener : mListeners) {
                    listener.onNetworkEnabled(
                            createExternalWifiConfiguration(config, true, Process.WIFI_UID));
                }
                break;
            case NetworkSelectionStatus.NETWORK_SELECTION_TEMPORARY_DISABLED:
                for (OnNetworkUpdateListener listener : mListeners) {
                    listener.onNetworkTemporarilyDisabled(
                            createExternalWifiConfiguration(config, true, Process.WIFI_UID),
                            disableReason);
                }
                break;
            case NetworkSelectionStatus.NETWORK_SELECTION_PERMANENTLY_DISABLED:
                for (OnNetworkUpdateListener listener : mListeners) {
                    WifiConfiguration configForListener = new WifiConfiguration(config);
                    listener.onNetworkPermanentlyDisabled(
                            createExternalWifiConfiguration(config, true, Process.WIFI_UID),
                            disableReason);
                }
                break;
            default:
                // all cases covered
        }
    }

    /**
     * Re-enable all temporary disabled configured networks.
     */
    public void enableTemporaryDisabledNetworks() {
        mWifiBlocklistMonitor.clearBssidBlocklist();
        for (WifiConfiguration config : getInternalConfiguredNetworks()) {
            if (config.getNetworkSelectionStatus().isNetworkTemporaryDisabled()) {
                updateNetworkSelectionStatus(config,
                        NetworkSelectionStatus.DISABLED_NONE);
            }
        }
    }

    /**
     * Attempt to re-enable a network for network selection, if this network was either:
     * a) Previously temporarily disabled, but its disable timeout has expired, or
     * b) Previously disabled because of a user switch, but is now visible to the current
     * user.
     *
     * @param networkId the id of the network to be checked for possible unblock (due to timeout)
     * @return true if the network identified by {@param networkId} was re-enabled for qualified
     * network selection, false otherwise.
     */
    public boolean tryEnableNetwork(int networkId) {
        WifiConfiguration config = getInternalConfiguredNetwork(networkId);
        if (config == null) {
            return false;
        }
        if (mWifiBlocklistMonitor.shouldEnableNetwork(config)) {
            return updateNetworkSelectionStatus(config, NetworkSelectionStatus.DISABLED_NONE);
        }
        return false;
    }

    /**
     * Enable a network using the public {@link WifiManager#enableNetwork(int, boolean)} API.
     *
     * @param networkId     network ID of the network that needs the update.
     * @param disableOthers Whether to disable all other networks or not. This is used to indicate
     *                      that the app requested connection to a specific network.
     * @param uid           uid of the app requesting the update.
     * @param packageName   Package name of calling apps
     * @return true if it succeeds, false otherwise
     */
    public boolean enableNetwork(int networkId, boolean disableOthers, int uid,
                                 @NonNull String packageName) {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "Enabling network " + networkId + " (disableOthers " + disableOthers + ")");
        }
        if (!mWifiPermissionsUtil.doesUidBelongToCurrentUserOrDeviceOwner(uid)) {
            Log.e(TAG, "UID " + uid + " not visible to the current user");
            return false;
        }
        WifiConfiguration config = getInternalConfiguredNetwork(networkId);
        if (config == null) {
            return false;
        }
        // Set the "last selected" flag even if the app does not have permissions to modify this
        // network config. Apps are allowed to connect to networks even if they don't have
        // permission to modify it.
        if (disableOthers) {
            setLastSelectedNetwork(networkId);
        }
        if (!canModifyNetwork(config, uid, packageName)) {
            Log.e(TAG, "UID " + uid +  " package " + packageName
                    + " does not have permission to update configuration "
                    + config.getProfileKey());
            return false;
        }
        if (!updateNetworkSelectionStatus(
                networkId, WifiConfiguration.NetworkSelectionStatus.DISABLED_NONE)) {
            return false;
        }
        mWifiBlocklistMonitor.clearBssidBlocklistForSsid(config.SSID);
        saveToStore(true);
        return true;
    }

    /**
     * Disable a network using the public {@link WifiManager#disableNetwork(int)} API.
     *
     * @param networkId network ID of the network that needs the update.
     * @param uid       uid of the app requesting the update.
     * @return true if it succeeds, false otherwise
     */
    public boolean disableNetwork(int networkId, int uid, @NonNull String packageName) {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "Disabling network " + networkId);
        }
        if (!mWifiPermissionsUtil.doesUidBelongToCurrentUserOrDeviceOwner(uid)) {
            Log.e(TAG, "UID " + uid + " package " + packageName
                    + " not visible to the current user");
            return false;
        }
        WifiConfiguration config = getInternalConfiguredNetwork(networkId);
        if (config == null) {
            return false;
        }
        // Reset the "last selected" flag even if the app does not have permissions to modify this
        // network config.
        if (networkId == mLastSelectedNetworkId) {
            clearLastSelectedNetwork();
        }
        if (!canModifyNetwork(config, uid, packageName)) {
            Log.e(TAG, "UID " + uid + " package " + packageName
                    + " does not have permission to update configuration "
                    + config.getProfileKey());
            return false;
        }
        if (!updateNetworkSelectionStatus(
                networkId, NetworkSelectionStatus.DISABLED_BY_WIFI_MANAGER)) {
            return false;
        }
        saveToStore(true);
        return true;
    }

    /**
     * Changes the user's choice to allow auto-join using the
     * {@link WifiManager#allowAutojoin(int, boolean)} API.
     *
     * @param networkId network ID of the network that needs the update.
     * @param choice the choice to allow auto-join or not
     * @return true if it succeeds, false otherwise
     */
    public boolean allowAutojoin(int networkId, boolean choice) {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "Setting allowAutojoin to " + choice + " for netId " + networkId);
        }
        WifiConfiguration config = getInternalConfiguredNetwork(networkId);
        if (config == null) {
            Log.e(TAG, "allowAutojoin: Supplied networkId " + networkId
                    + " has no matching config");
            return false;
        }

        config.allowAutojoin = choice;
        if (!choice) {
            removeConnectChoiceFromAllNetworks(config.getProfileKey());
            clearConnectChoiceInternal(config);
        }
        sendConfiguredNetworkChangedBroadcast(WifiManager.CHANGE_REASON_CONFIG_CHANGE, config);
        if (!config.ephemeral) {
            saveToStore(true);
        }
        return true;
    }

    /**
     * Updates the last connected UID for the provided configuration.
     *
     * @param networkId network ID corresponding to the network.
     * @param uid       uid of the app requesting the connection.
     * @return true if the network was found, false otherwise.
     */
    private boolean updateLastConnectUid(int networkId, int uid) {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "Update network last connect UID for " + networkId);
        }
        if (!mWifiPermissionsUtil.doesUidBelongToCurrentUserOrDeviceOwner(uid)) {
            Log.e(TAG, "UID " + uid + " not visible to the current user");
            return false;
        }
        WifiConfiguration config = getInternalConfiguredNetwork(networkId);
        if (config == null) {
            return false;
        }
        config.lastConnectUid = uid;
        return true;
    }

    /**
     * Updates a network configuration after a successful connection to it.
     *
     * This method updates the following WifiConfiguration elements:
     * 1. Set the |lastConnected| timestamp.
     * 2. Increment |numAssociation| counter.
     * 3. Clear the disable reason counters in the associated |NetworkSelectionStatus|.
     * 4. Set the hasEverConnected| flag in the associated |NetworkSelectionStatus|.
     * 5. Set the status of network to |CURRENT|.
     * 6. Set the |isCurrentlyConnected| flag to true.
     * 7. Set the |isUserSelected| flag.
     *
     * @param networkId network ID corresponding to the network.
     * @param isUserSelected network is user selected.
     * @param shouldSetUserConnectChoice setup user connect choice on this network.
     * @param rssi signal strength of the connected network.
     * @return true if the network was found, false otherwise.
     */
    public boolean updateNetworkAfterConnect(int networkId, boolean isUserSelected,
            boolean shouldSetUserConnectChoice, int rssi) {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "Update network after connect for " + networkId);
        }
        WifiConfiguration config = getInternalConfiguredNetwork(networkId);
        if (config == null) {
            return false;
        }

        // Only record connection order for non-passpoint from user saved or suggestion.
        if (!config.isPasspoint() && (config.fromWifiNetworkSuggestion || !config.ephemeral)) {
            mLruConnectionTracker.addNetwork(config);
        }
        if (shouldSetUserConnectChoice) {
            setUserConnectChoice(config.networkId, rssi);
        }
        config.lastConnected = mClock.getWallClockMillis();
        config.numRebootsSinceLastUse = 0;
        config.numAssociation++;
        config.getNetworkSelectionStatus().clearDisableReasonCounter();
        config.getNetworkSelectionStatus().setHasEverConnected(true);
        setNetworkStatus(config, WifiConfiguration.Status.CURRENT);
        config.isCurrentlyConnected = true;
        config.setIsUserSelected(isUserSelected);
        saveToStore(false);
        return true;
    }

    /**
     * Set captive portal to be detected for this network.
     * @param networkId
     */
    public void noteCaptivePortalDetected(int networkId) {
        WifiConfiguration config = getInternalConfiguredNetwork(networkId);
        if (config != null) {
            config.getNetworkSelectionStatus().setHasNeverDetectedCaptivePortal(false);
        }
    }

    /**
     * Updates a network configuration after disconnection from it.
     *
     * This method updates the following WifiConfiguration elements:
     * 1. Set the |lastDisconnected| timestamp.
     * 2. Set the status of network back to |ENABLED|.
     * 3. Set the |isCurrentlyConnected| flag to false.
     *
     * @param networkId network ID corresponding to the network.
     * @return true if the network was found, false otherwise.
     */
    public boolean updateNetworkAfterDisconnect(int networkId) {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "Update network after disconnect for " + networkId);
        }
        WifiConfiguration config = getInternalConfiguredNetwork(networkId);
        if (config == null) {
            return false;
        }
        config.lastDisconnected = mClock.getWallClockMillis();
        config.randomizedMacExpirationTimeMs = Math.max(config.randomizedMacExpirationTimeMs,
                config.lastDisconnected + NON_PERSISTENT_MAC_WAIT_AFTER_DISCONNECT_MS);
        // If the network hasn't been disabled, mark it back as
        // enabled after disconnection.
        if (config.status == WifiConfiguration.Status.CURRENT) {
            setNetworkStatus(config, WifiConfiguration.Status.ENABLED);
        }
        config.isCurrentlyConnected = false;
        config.setIsUserSelected(false);
        saveToStore(false);
        return true;
    }

    /**
     * Set default GW MAC address for the provided network.
     *
     * @param networkId network ID corresponding to the network.
     * @param macAddress MAC address of the gateway to be set.
     * @return true if the network was found, false otherwise.
     */
    public boolean setNetworkDefaultGwMacAddress(int networkId, String macAddress) {
        WifiConfiguration config = getInternalConfiguredNetwork(networkId);
        if (config == null) {
            return false;
        }
        config.defaultGwMacAddress = macAddress;
        return true;
    }

    /**
     * Clear the {@link NetworkSelectionStatus#mCandidate},
     * {@link NetworkSelectionStatus#mCandidateScore} &
     * {@link NetworkSelectionStatus#mSeenInLastQualifiedNetworkSelection} for the provided network.
     *
     * This is invoked by Network Selector at the start of every selection procedure to clear all
     * configured networks' scan-result-candidates.
     *
     * @param networkId network ID corresponding to the network.
     * @return true if the network was found, false otherwise.
     */
    public boolean clearNetworkCandidateScanResult(int networkId) {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "Clear network candidate scan result for " + networkId);
        }
        WifiConfiguration config = getInternalConfiguredNetwork(networkId);
        if (config == null) {
            return false;
        }
        config.getNetworkSelectionStatus().setCandidate(null);
        config.getNetworkSelectionStatus().setCandidateScore(Integer.MIN_VALUE);
        config.getNetworkSelectionStatus().setSeenInLastQualifiedNetworkSelection(false);
        config.getNetworkSelectionStatus().setCandidateSecurityParams(null);
        return true;
    }

    /**
     * Set the {@link NetworkSelectionStatus#mCandidate},
     * {@link NetworkSelectionStatus#mCandidateScore} &
     * {@link NetworkSelectionStatus#mSeenInLastQualifiedNetworkSelection} for the provided network.
     *
     * This is invoked by Network Selector when it sees a network during network selection procedure
     * to set the scan result candidate.
     *
     * @param networkId  network ID corresponding to the network.
     * @param scanResult Candidate ScanResult associated with this network.
     * @param score      Score assigned to the candidate.
     * @param params     Security params for this candidate.
     * @return true if the network was found, false otherwise.
     */
    public boolean setNetworkCandidateScanResult(int networkId, ScanResult scanResult, int score,
            SecurityParams params) {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "Set network candidate scan result " + scanResult + " for " + networkId
                    + " with security params " + params);
        }
        WifiConfiguration config = getInternalConfiguredNetwork(networkId);
        if (config == null) {
            Log.e(TAG, "Cannot find network for " + networkId);
            return false;
        }
        config.getNetworkSelectionStatus().setCandidate(scanResult);
        config.getNetworkSelectionStatus().setCandidateScore(score);
        config.getNetworkSelectionStatus().setSeenInLastQualifiedNetworkSelection(true);
        config.getNetworkSelectionStatus().setCandidateSecurityParams(params);
        return true;
    }

    /**
     * Set the {@link NetworkSelectionStatus#mLastUsedSecurityParams}.
     *
     * @param networkId  network ID corresponding to the network.
     * @param params     Security params for this candidate.
     * @return true if the network was found, false otherwise.
     */
    public boolean setNetworkLastUsedSecurityParams(int networkId, SecurityParams params) {
        WifiConfiguration config = getInternalConfiguredNetwork(networkId);
        if (config == null) {
            Log.e(TAG, "Cannot find network for " + networkId);
            return false;
        }
        config.getNetworkSelectionStatus().setLastUsedSecurityParams(params);
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "Update last used security param for " + config.getProfileKey()
                    + " with security type " + params.getSecurityType());
        }
        return true;
    }

    /**
     * Iterate through all the saved networks and remove the provided configuration from the
     * {@link NetworkSelectionStatus#mConnectChoice} from them.
     *
     * This is invoked when a network is removed from our records.
     *
     * @param connectChoiceConfigKey ConfigKey corresponding to the network that is being removed.
     */
    public void removeConnectChoiceFromAllNetworks(String connectChoiceConfigKey) {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "Removing connect choice from all networks " + connectChoiceConfigKey);
        }
        if (connectChoiceConfigKey == null) {
            return;
        }
        for (WifiConfiguration config : getInternalConfiguredNetworks()) {
            WifiConfiguration.NetworkSelectionStatus status = config.getNetworkSelectionStatus();
            String connectChoice = status.getConnectChoice();
            if (TextUtils.equals(connectChoice, connectChoiceConfigKey)) {
                Log.d(TAG, "remove connect choice:" + connectChoice + " from " + config.SSID
                        + " : " + config.networkId);
                clearConnectChoiceInternal(config);
            }
        }
        for (OnNetworkUpdateListener listener : mListeners) {
            listener.onConnectChoiceRemoved(connectChoiceConfigKey);
        }
    }

    /**
     * Increments the number of no internet access reports in the provided network.
     *
     * @param networkId network ID corresponding to the network.
     * @return true if the network was found, false otherwise.
     */
    public boolean incrementNetworkNoInternetAccessReports(int networkId) {
        WifiConfiguration config = getInternalConfiguredNetwork(networkId);
        if (config == null) {
            return false;
        }
        config.numNoInternetAccessReports++;
        config.validatedInternetAccess = false;
        return true;
    }

    /**
     * Sets the internet access is validated or not in the provided network.
     *
     * @param networkId network ID corresponding to the network.
     * @param validated Whether access is validated or not.
     * @return true if the network was found, false otherwise.
     */
    public boolean setNetworkValidatedInternetAccess(int networkId, boolean validated) {
        WifiConfiguration config = getInternalConfiguredNetwork(networkId);
        if (config == null) {
            return false;
        }
        config.validatedInternetAccess = validated;
        if (validated) {
            config.numNoInternetAccessReports = 0;
            config.getNetworkSelectionStatus().setHasEverValidatedInternetAccess(true);
        }
        saveToStore(false);
        return true;
    }

    /**
     * Sets whether the internet access is expected or not in the provided network.
     *
     * @param networkId network ID corresponding to the network.
     * @param expected  Whether access is expected or not.
     * @return true if the network was found, false otherwise.
     */
    public boolean setNetworkNoInternetAccessExpected(int networkId, boolean expected) {
        WifiConfiguration config = getInternalConfiguredNetwork(networkId);
        if (config == null) {
            return false;
        }
        config.noInternetAccessExpected = expected;
        return true;
    }

    /**
     * Sets whether the provided network is local only due to ip provisioning timeout
     *
     * @param networkId             network ID corresponding to the network.
     * @param isIpProvisionTimedOut Whether the network is local-only or not.
     * @return true if the network was found, false otherwise.
     */
    public boolean setIpProvisioningTimedOut(int networkId, boolean isIpProvisionTimedOut) {
        WifiConfiguration config = getInternalConfiguredNetwork(networkId);
        if (config == null) {
            return false;
        }
        config.setIpProvisioningTimedOut(isIpProvisionTimedOut);
        return true;
    }


    /**
     * Helper method to clear out the {@link #mNextNetworkId} user/app network selection. This
     * is done when either the corresponding network is either removed or disabled.
     */
    public void clearLastSelectedNetwork() {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "Clearing last selected network");
        }
        mLastSelectedNetworkId = WifiConfiguration.INVALID_NETWORK_ID;
        mLastSelectedTimeStamp = NetworkSelectionStatus.INVALID_NETWORK_SELECTION_DISABLE_TIMESTAMP;
    }

    /**
     * Helper method to mark a network as the last selected one by an app/user. This is set
     * when an app invokes {@link #enableNetwork(int, boolean, int)} with |disableOthers| flag set.
     * This is used by network selector to assign a special bonus during network selection.
     */
    private void setLastSelectedNetwork(int networkId) {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "Setting last selected network to " + networkId);
        }
        mLastSelectedNetworkId = networkId;
        mLastSelectedTimeStamp = mClock.getElapsedSinceBootMillis();
    }

    /**
     * Retrieve the network Id corresponding to the last network that was explicitly selected by
     * an app/user.
     *
     * @return network Id corresponding to the last selected network.
     */
    public int getLastSelectedNetwork() {
        return mLastSelectedNetworkId;
    }

    /**
     * Retrieve the configKey corresponding to the last network that was explicitly selected by
     * an app/user.
     *
     * @return network Id corresponding to the last selected network.
     */
    public String getLastSelectedNetworkConfigKey() {
        if (mLastSelectedNetworkId == WifiConfiguration.INVALID_NETWORK_ID) {
            return "";
        }
        WifiConfiguration config = getInternalConfiguredNetwork(mLastSelectedNetworkId);
        if (config == null) {
            return "";
        }
        return config.getProfileKey();
    }

    /**
     * Retrieve the time stamp at which a network was explicitly selected by an app/user.
     *
     * @return timestamp in milliseconds from boot when this was set.
     */
    public long getLastSelectedTimeStamp() {
        return mLastSelectedTimeStamp;
    }

    /**
     * Helper method to get the scan detail cache entry {@link #mScanDetailCaches} for the provided
     * network.
     *
     * @param networkId network ID corresponding to the network.
     * @return existing {@link ScanDetailCache} entry if one exists or null.
     */
    public ScanDetailCache getScanDetailCacheForNetwork(int networkId) {
        return mScanDetailCaches.get(networkId);
    }

    /**
     * Helper method to get or create a scan detail cache entry {@link #mScanDetailCaches} for
     * the provided network.
     *
     * @param config configuration corresponding to the the network.
     * @return existing {@link ScanDetailCache} entry if one exists or a new instance created for
     * this network.
     */
    private ScanDetailCache getOrCreateScanDetailCacheForNetwork(WifiConfiguration config) {
        if (config == null) return null;
        ScanDetailCache cache = getScanDetailCacheForNetwork(config.networkId);
        if (cache == null && config.networkId != WifiConfiguration.INVALID_NETWORK_ID) {
            cache = new ScanDetailCache(
                    config, SCAN_CACHE_ENTRIES_MAX_SIZE, SCAN_CACHE_ENTRIES_TRIM_SIZE);
            mScanDetailCaches.put(config.networkId, cache);
        }
        return cache;
    }

    /**
     * Saves the provided ScanDetail into the corresponding scan detail cache entry
     * {@link #mScanDetailCaches} for the provided network.
     *
     * @param config     configuration corresponding to the the network.
     * @param scanDetail new scan detail instance to be saved into the cache.
     */
    private void saveToScanDetailCacheForNetwork(
            WifiConfiguration config, ScanDetail scanDetail) {
        ScanResult scanResult = scanDetail.getScanResult();

        WifiScoreCard.PerNetwork network = mWifiScoreCard.lookupNetwork(config.SSID);
        network.addFrequency(scanResult.frequency);
        ScanDetailCache scanDetailCache = getOrCreateScanDetailCacheForNetwork(config);
        if (scanDetailCache == null) {
            Log.e(TAG, "Could not allocate scan cache for " + config.getPrintableSsid());
            return;
        }

        // Adding a new BSSID
        if (config.ephemeral) {
            // For an ephemeral Wi-Fi config, the ScanResult should be considered
            // untrusted.
            scanResult.untrusted = true;
        }

        // Add the scan detail to this network's scan detail cache.
        scanDetailCache.put(scanDetail);
    }

    /**
     * Retrieves a configured network corresponding to the provided scan detail if one exists.
     *
     * @param scanDetail ScanDetail instance  to use for looking up the network.
     * @return WifiConfiguration object representing the network corresponding to the scanDetail,
     * null if none exists.
     */
    public WifiConfiguration getSavedNetworkForScanDetail(ScanDetail scanDetail) {
        ScanResult scanResult = scanDetail.getScanResult();
        if (scanResult == null) {
            Log.e(TAG, "No scan result found in scan detail");
            return null;
        }
        return getSavedNetworkForScanResult(scanResult);
    }

    /**
     * Retrieves a configured network corresponding to the provided scan result if one exists.
     *
     * @param scanResult ScanResult instance to use for looking up the network.
     * @return WifiConfiguration object representing the network corresponding to the scanResult,
     * null if none exists.
     */
    public WifiConfiguration getSavedNetworkForScanResult(@NonNull ScanResult scanResult) {
        WifiConfiguration config = null;
        try {
            config = mConfiguredNetworks.getByScanResultForCurrentUser(scanResult);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Failed to lookup network from config map", e);
        }
        if (config != null) {
            if (mVerboseLoggingEnabled) {
                Log.v(TAG, "getSavedNetworkFromScanResult Found " + config.getProfileKey()
                        + " for " + scanResult.SSID + "[" + scanResult.capabilities + "]");
            }
        }
        return config;
    }

    /**
     * Caches the provided |scanDetail| into the corresponding scan detail cache entry
     * {@link #mScanDetailCaches} for the retrieved network.
     *
     * @param scanDetail input a scanDetail from the scan result
     */
    public void updateScanDetailCacheFromScanDetailForSavedNetwork(ScanDetail scanDetail) {
        WifiConfiguration network = getSavedNetworkForScanDetail(scanDetail);
        if (network == null) {
            return;
        }
        saveToScanDetailCacheForNetwork(network, scanDetail);
    }
    /**
     * Retrieves a configured network corresponding to the provided scan detail if one exists and
     * caches the provided |scanDetail| into the corresponding scan detail cache entry
     * {@link #mScanDetailCaches} for the retrieved network.
     *
     * @param scanDetail input a scanDetail from the scan result
     * @return WifiConfiguration object representing the network corresponding to the scanDetail,
     * null if none exists.
     */
    public WifiConfiguration getSavedNetworkForScanDetailAndCache(ScanDetail scanDetail) {
        WifiConfiguration network = getSavedNetworkForScanDetail(scanDetail);
        if (network == null) {
            return null;
        }
        saveToScanDetailCacheForNetwork(network, scanDetail);
        // Cache DTIM values parsed from the beacon frame Traffic Indication Map (TIM)
        // Information Element (IE), into the associated WifiConfigurations. Most of the
        // time there is no TIM IE in the scan result (Probe Response instead of Beacon
        // Frame), these scanResult DTIM's are negative and ignored.
        // Used for metrics collection.
        if (scanDetail.getNetworkDetail() != null
                && scanDetail.getNetworkDetail().getDtimInterval() > 0) {
            network.dtimInterval = scanDetail.getNetworkDetail().getDtimInterval();
        }
        return createExternalWifiConfiguration(network, true, Process.WIFI_UID);
    }

    /**
     * Update the scan detail cache associated with current connected network with latest
     * RSSI value in the provided WifiInfo.
     * This is invoked when we get an RSSI poll update after connection.
     *
     * @param info WifiInfo instance pointing to the current connected network.
     */
    public void updateScanDetailCacheFromWifiInfo(WifiInfo info) {
        WifiConfiguration config = getInternalConfiguredNetwork(info.getNetworkId());
        ScanDetailCache scanDetailCache = getScanDetailCacheForNetwork(info.getNetworkId());
        if (config != null && scanDetailCache != null) {
            ScanDetail scanDetail = scanDetailCache.getScanDetail(info.getBSSID());
            if (scanDetail != null) {
                ScanResult result = scanDetail.getScanResult();
                long previousSeen = result.seen;
                int previousRssi = result.level;
                // Update the scan result
                scanDetail.setSeen();
                result.level = info.getRssi();
                // Average the RSSI value
                long maxAge = SCAN_RESULT_MAXIMUM_AGE_MS;
                long age = result.seen - previousSeen;
                if (previousSeen > 0 && age > 0 && age < maxAge / 2) {
                    // Average the RSSI with previously seen instances of this scan result
                    double alpha = 0.5 - (double) age / (double) maxAge;
                    result.level = (int) ((double) result.level * (1 - alpha)
                                        + (double) previousRssi * alpha);
                }
                if (mVerboseLoggingEnabled) {
                    Log.v(TAG, "Updating scan detail cache freq=" + result.frequency
                            + " BSSID=" + result.BSSID
                            + " RSSI=" + result.level
                            + " for " + config.getProfileKey());
                }
            }
        }
    }

    /**
     * Save the ScanDetail to the ScanDetailCache of the given network.  This is used
     * by {@link PasspointNetworkNominator} for caching
     * ScanDetail for newly created {@link WifiConfiguration} for Passpoint network.
     *
     * @param networkId The ID of the network to save ScanDetail to
     * @param scanDetail The ScanDetail to cache
     */
    public void updateScanDetailForNetwork(int networkId, ScanDetail scanDetail) {
        WifiConfiguration network = getInternalConfiguredNetwork(networkId);
        if (network == null) {
            return;
        }
        saveToScanDetailCacheForNetwork(network, scanDetail);
    }

    /**
     * Helper method to check if the 2 provided networks can be linked or not.
     * Networks are considered for linking if:
     * 1. Share the same GW MAC address.
     * 2. Scan results for the networks have AP's with MAC address which differ only in the last
     * nibble.
     *
     * @param network1         WifiConfiguration corresponding to network 1.
     * @param network2         WifiConfiguration corresponding to network 2.
     * @param scanDetailCache1 ScanDetailCache entry for network 1.
     * @param scanDetailCache1 ScanDetailCache entry for network 2.
     * @return true if the networks should be linked, false if the networks should be unlinked.
     */
    private boolean shouldNetworksBeLinked(
            WifiConfiguration network1, WifiConfiguration network2,
            ScanDetailCache scanDetailCache1, ScanDetailCache scanDetailCache2) {
        // Check if networks should not be linked due to credential mismatch
        if (mContext.getResources().getBoolean(
                R.bool.config_wifi_only_link_same_credential_configurations)) {
            if (!TextUtils.equals(network1.preSharedKey, network2.preSharedKey)) {
                return false;
            }
        }

        // Skip VRRP MAC addresses since they are likely to correspond to different networks even if
        // they match.
        if ((network1.defaultGwMacAddress != null && network1.defaultGwMacAddress
                .regionMatches(true, 0, VRRP_MAC_ADDRESS_PREFIX, 0,
                        VRRP_MAC_ADDRESS_PREFIX.length()))
                || (network2.defaultGwMacAddress != null && network2.defaultGwMacAddress
                .regionMatches(true, 0, VRRP_MAC_ADDRESS_PREFIX, 0,
                        VRRP_MAC_ADDRESS_PREFIX.length()))) {
            return false;
        }

        // Check if networks should be linked due to default gateway match
        if (network1.defaultGwMacAddress != null && network2.defaultGwMacAddress != null) {
            // If both default GW are known, link only if they are equal
            if (network1.defaultGwMacAddress.equalsIgnoreCase(network2.defaultGwMacAddress)) {
                if (mVerboseLoggingEnabled) {
                    Log.v(TAG, "shouldNetworksBeLinked link due to same gw " + network2.SSID
                            + " and " + network1.SSID + " GW " + network1.defaultGwMacAddress);
                }
                return true;
            }
            return false;
        }

        // We do not know BOTH default gateways yet, but if the first 16 ASCII characters of BSSID
        // match then we can assume this is a DBDC with the same gateway. Once both gateways become
        // known, we will unlink the networks if it turns out the gateways are actually different.
        if (!mContext.getResources().getBoolean(
                R.bool.config_wifiAllowLinkingUnknownDefaultGatewayConfigurations)) {
            return false;
        }
        if (scanDetailCache1 != null && scanDetailCache2 != null) {
            for (String abssid : scanDetailCache1.keySet()) {
                for (String bbssid : scanDetailCache2.keySet()) {
                    if (abssid.regionMatches(
                            true, 0, bbssid, 0, LINK_CONFIGURATION_BSSID_MATCH_LENGTH)) {
                        if (mVerboseLoggingEnabled) {
                            Log.v(TAG, "shouldNetworksBeLinked link due to DBDC BSSID match "
                                    + network2.SSID + " and " + network1.SSID
                                    + " bssida " + abssid + " bssidb " + bbssid);
                        }
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Helper methods to link 2 networks together.
     *
     * @param network1 WifiConfiguration corresponding to network 1.
     * @param network2 WifiConfiguration corresponding to network 2.
     */
    private void linkNetworks(WifiConfiguration network1, WifiConfiguration network2) {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "linkNetworks will link " + network2.getProfileKey()
                    + " and " + network1.getProfileKey());
        }
        if (network2.linkedConfigurations == null) {
            network2.linkedConfigurations = new HashMap<>();
        }
        if (network1.linkedConfigurations == null) {
            network1.linkedConfigurations = new HashMap<>();
        }
        // TODO (b/30638473): This needs to become a set instead of map, but it will need
        // public interface changes and need some migration of existing store data.
        network2.linkedConfigurations.put(network1.getProfileKey(), 1);
        network1.linkedConfigurations.put(network2.getProfileKey(), 1);
    }

    /**
     * Helper methods to unlink 2 networks from each other.
     *
     * @param network1 WifiConfiguration corresponding to network 1.
     * @param network2 WifiConfiguration corresponding to network 2.
     */
    private void unlinkNetworks(WifiConfiguration network1, WifiConfiguration network2) {
        if (network2.linkedConfigurations != null
                && (network2.linkedConfigurations.get(network1.getProfileKey()) != null)) {
            if (mVerboseLoggingEnabled) {
                Log.v(TAG, "unlinkNetworks un-link " + network1.getProfileKey()
                        + " from " + network2.getProfileKey());
            }
            network2.linkedConfigurations.remove(network1.getProfileKey());
        }
        if (network1.linkedConfigurations != null
                && (network1.linkedConfigurations.get(network2.getProfileKey()) != null)) {
            if (mVerboseLoggingEnabled) {
                Log.v(TAG, "unlinkNetworks un-link " + network2.getProfileKey()
                        + " from " + network1.getProfileKey());
            }
            network1.linkedConfigurations.remove(network2.getProfileKey());
        }
    }

    /**
     * This method runs through all the saved networks and checks if the provided network can be
     * linked with any of them.
     *
     * @param config WifiConfiguration object corresponding to the network that needs to be
     *               checked for potential links.
     */
    private void attemptNetworkLinking(WifiConfiguration config) {
        if (!WifiConfigurationUtil.isConfigLinkable(config)) return;

        ScanDetailCache scanDetailCache = getScanDetailCacheForNetwork(config.networkId);
        // Ignore configurations with large number of BSSIDs.
        if (scanDetailCache != null
                && scanDetailCache.size() > LINK_CONFIGURATION_MAX_SCAN_CACHE_ENTRIES) {
            return;
        }
        for (WifiConfiguration linkConfig : getInternalConfiguredNetworks()) {
            if (linkConfig.getProfileKey().equals(config.getProfileKey())) {
                continue;
            }
            if (linkConfig.ephemeral) {
                continue;
            }
            if (!linkConfig.getNetworkSelectionStatus().isNetworkEnabled()) {
                continue;
            }
            // Network Selector will be allowed to dynamically jump from a linked configuration
            // to another, hence only link configurations that have WPA_PSK/SAE security type
            // if auto upgrade enabled (OR) WPA_PSK if auto upgrade disabled.
            if (!WifiConfigurationUtil.isConfigLinkable(linkConfig)) continue;
            ScanDetailCache linkScanDetailCache =
                    getScanDetailCacheForNetwork(linkConfig.networkId);
            // Ignore configurations with large number of BSSIDs.
            if (linkScanDetailCache != null
                    && linkScanDetailCache.size() > LINK_CONFIGURATION_MAX_SCAN_CACHE_ENTRIES) {
                continue;
            }
            // Check if the networks should be linked/unlinked.
            if (shouldNetworksBeLinked(
                    config, linkConfig, scanDetailCache, linkScanDetailCache)) {
                linkNetworks(config, linkConfig);
            } else {
                unlinkNetworks(config, linkConfig);
            }
        }
    }

    /**
     * Retrieves a list of all the saved hidden networks for scans
     *
     * Hidden network list sent to the firmware has limited size. If there are a lot of saved
     * networks, this list will be truncated and we might end up not sending the networks
     * with the highest chance of connecting to the firmware.
     * So, re-sort the network list based on the frequency of connection to those networks
     * and whether it was last seen in the scan results.
     *
     * @param autoJoinOnly retrieve hidden network autojoin enabled only.
     * @return list of hidden networks in the order of priority.
     */
    public List<WifiScanner.ScanSettings.HiddenNetwork> retrieveHiddenNetworkList(
            boolean autoJoinOnly) {
        List<WifiScanner.ScanSettings.HiddenNetwork> hiddenList = new ArrayList<>();
        List<WifiConfiguration> networks = getConfiguredNetworks();
        // Remove any non hidden networks.
        networks.removeIf(config -> !config.hiddenSSID);
        networks.sort(mScanListComparator);
        // The most frequently connected network has the highest priority now.
        Set<WifiSsid> ssidSet = new LinkedHashSet<>();
        for (WifiConfiguration config : networks) {
            if (autoJoinOnly && !config.allowAutojoin) {
                continue;
            }
            ssidSet.addAll(mWifiInjector.getSsidTranslator()
                    .getAllPossibleOriginalSsids(WifiSsid.fromString(config.SSID)));
        }
        for (WifiSsid ssid : ssidSet) {
            hiddenList.add(new WifiScanner.ScanSettings.HiddenNetwork(ssid.toString()));
        }
        return hiddenList;
    }

    /**
     * Check if the provided network was temporarily disabled by the user and still blocked.
     *
     * @param network Input can be SSID or FQDN. And caller must ensure that the SSID passed thru
     *                this API matched the WifiConfiguration.SSID rules, and thus be surrounded by
     *                quotes.
     * @return true if network is blocking, otherwise false.
     */
    public boolean isNetworkTemporarilyDisabledByUser(String network) {
        if (mUserTemporarilyDisabledList.isLocked(network)) {
            return true;
        }
        mUserTemporarilyDisabledList.remove(network);
        return false;
    }

    /**
     * Check if the provided network should be disabled because it's a non-carrier-merged network.
     * @param config WifiConfiguration
     * @return true if the network is a non-carrier-merged network and it should be disabled,
     * otherwise false.
     */
    public boolean isNonCarrierMergedNetworkTemporarilyDisabled(
            @NonNull WifiConfiguration config) {
        return mNonCarrierMergedNetworksStatusTracker.isNetworkDisabled(config);
    }

    /**
     * User temporarily disable a network and will be block to auto-join when network is still
     * nearby.
     *
     * The network will be re-enabled when:
     * a) User select to connect the network.
     * b) The network is not in range for {@link #USER_DISCONNECT_NETWORK_BLOCK_EXPIRY_MS}
     * c) The maximum disable duration configured by
     * config_wifiAllNonCarrierMergedWifiMaxDisableDurationMinutes has passed.
     * d) Toggle wifi off, reset network settings or device reboot.
     *
     * @param network Input can be SSID or FQDN. And caller must ensure that the SSID passed thru
     *                this API matched the WifiConfiguration.SSID rules, and thus be surrounded by
     *                quotes.
     *        uid     UID of the calling process.
     */
    public void userTemporarilyDisabledNetwork(String network, int uid) {
        int maxDisableDurationMinutes = mContext.getResources().getInteger(R.integer
                .config_wifiAllNonCarrierMergedWifiMaxDisableDurationMinutes);
        mUserTemporarilyDisabledList.add(network, USER_DISCONNECT_NETWORK_BLOCK_EXPIRY_MS,
                maxDisableDurationMinutes * 60 * 1000);
        Log.d(TAG, "Temporarily disable network: " + network + " uid=" + uid + " num="
                + mUserTemporarilyDisabledList.size() + ", maxDisableDurationMinutes:"
                + maxDisableDurationMinutes);
        removeUserChoiceFromDisabledNetwork(network, uid);
        saveToStore(false);
    }

    /**
     * Temporarily disable visible and configured networks except for carrier merged networks for
     * the given subscriptionId.
     * @param subscriptionId
     */
    public void startRestrictingAutoJoinToSubscriptionId(int subscriptionId) {
        int minDisableDurationMinutes = mContext.getResources().getInteger(R.integer
                .config_wifiAllNonCarrierMergedWifiMinDisableDurationMinutes);
        int maxDisableDurationMinutes = mContext.getResources().getInteger(R.integer
                .config_wifiAllNonCarrierMergedWifiMaxDisableDurationMinutes);
        localLog("startRestrictingAutoJoinToSubscriptionId: " + subscriptionId
                + " minDisableDurationMinutes:" + minDisableDurationMinutes
                + " maxDisableDurationMinutes:" + maxDisableDurationMinutes);
        long maxDisableDurationMs = maxDisableDurationMinutes * 60 * 1000;
        // do a clear to make sure we start at a clean state.
        mNonCarrierMergedNetworksStatusTracker.clear();
        mNonCarrierMergedNetworksStatusTracker.disableAllNonCarrierMergedNetworks(subscriptionId,
                minDisableDurationMinutes * 60 * 1000,
                maxDisableDurationMs);
        for (WifiConfiguration config : getInternalConfiguredNetworks()) {
            ScanDetailCache scanDetailCache = getScanDetailCacheForNetwork(config.networkId);
            if (scanDetailCache == null) {
                continue;
            }
            ScanResult scanResult = scanDetailCache.getMostRecentScanResult();
            if (scanResult == null) {
                continue;
            }
            if (mClock.getWallClockMillis() - scanResult.seen
                    < NON_CARRIER_MERGED_NETWORKS_SCAN_CACHE_QUERY_DURATION_MS) {
                // do not disable if this is a carrier-merged-network with the given subscriptionId
                if (config.carrierMerged && config.subscriptionId == subscriptionId) {
                    continue;
                }
                mNonCarrierMergedNetworksStatusTracker.temporarilyDisableNetwork(config,
                        USER_DISCONNECT_NETWORK_BLOCK_EXPIRY_MS, maxDisableDurationMs);
            }
        }
    }

    /**
     * Resets the effects of startTemporarilyDisablngAllNonCarrierMergedWifi.
     */
    public void stopRestrictingAutoJoinToSubscriptionId() {
        mNonCarrierMergedNetworksStatusTracker.clear();
    }

    /**
     * Update the user temporarily disabled network list with networks in range.
     * @param networks networks in range in String format, FQDN or SSID. And caller must ensure
     *                 that the SSID passed thru this API matched the WifiConfiguration.SSID rules,
     *                 and thus be surrounded by quotes.
     */
    public void updateUserDisabledList(List<String> networks) {
        mUserTemporarilyDisabledList.update(new HashSet<>(networks));
        mNonCarrierMergedNetworksStatusTracker.update(new HashSet<>(networks));
    }

    private void removeUserChoiceFromDisabledNetwork(
            @NonNull String network, int uid) {
        for (WifiConfiguration config : getInternalConfiguredNetworks()) {
            if (TextUtils.equals(config.SSID, network) || TextUtils.equals(config.FQDN, network)) {
                if (mWifiPermissionsUtil.checkNetworkSettingsPermission(uid)) {
                    mWifiMetrics.logUserActionEvent(
                            UserActionEvent.EVENT_DISCONNECT_WIFI, config.networkId);
                }
                removeConnectChoiceFromAllNetworks(config.getProfileKey());
            }
        }
    }

    /**
     * User enabled network manually, maybe trigger by user select to connect network.
     * @param networkId enabled network id.
     * @return true if the operation succeeded, false otherwise.
     */
    public boolean userEnabledNetwork(int networkId) {
        WifiConfiguration configuration = getInternalConfiguredNetwork(networkId);
        if (configuration == null) {
            return false;
        }
        final String network;
        if (configuration.isPasspoint()) {
            network = configuration.FQDN;
        } else {
            network = configuration.SSID;
        }
        mUserTemporarilyDisabledList.remove(network);
        mWifiBlocklistMonitor.clearBssidBlocklistForSsid(configuration.SSID);
        Log.d(TAG, "Enable disabled network: " + network + " num="
                + mUserTemporarilyDisabledList.size());
        return true;
    }

    /**
     * Clear all user temporarily disabled networks.
     */
    public void clearUserTemporarilyDisabledList() {
        mUserTemporarilyDisabledList.clear();
    }

    /**
     * Resets all sim networks state.
     */
    public void resetSimNetworks() {
        if (mVerboseLoggingEnabled) localLog("resetSimNetworks");
        for (WifiConfiguration config : getInternalConfiguredNetworks()) {
            if (config.enterpriseConfig == null
                    || !config.enterpriseConfig.isAuthenticationSimBased()) {
                continue;
            }
            if (config.enterpriseConfig.getEapMethod() == WifiEnterpriseConfig.Eap.PEAP) {
                Pair<String, String> currentIdentity =
                        mWifiCarrierInfoManager.getSimIdentity(config);
                if (mVerboseLoggingEnabled) {
                    Log.d(TAG, "New identity for config " + config + ": " + currentIdentity);
                }
                // Update the loaded config
                if (currentIdentity == null) {
                    Log.d(TAG, "Identity is null");
                } else {
                    config.enterpriseConfig.setIdentity(currentIdentity.first);
                }
                // do not reset anonymous identity since it may be dependent on user-entry
                // (i.e. cannot re-request on every reboot/SIM re-entry)
            } else {
                // reset identity as well: supplicant will ask us for it
                config.enterpriseConfig.setIdentity("");
                if (!WifiCarrierInfoManager.isAnonymousAtRealmIdentity(
                        config.enterpriseConfig.getAnonymousIdentity())) {
                    config.enterpriseConfig.setAnonymousIdentity("");
                }
            }
        }
    }

    /**
     * Clear all ephemeral carrier networks from the app without carrier privilege, which leads to
     * a disconnection.
     * Disconnection and removing networks installed by privileged apps is handled by will be
     * cleaned when privilege revokes.
     */
    public void removeEphemeralCarrierNetworks(Set<String> carrierPrivilegedPackages) {
        if (mVerboseLoggingEnabled) localLog("removeEphemeralCarrierNetwork");
        WifiConfiguration[] copiedConfigs =
                mConfiguredNetworks.valuesForAllUsers().toArray(new WifiConfiguration[0]);
        for (WifiConfiguration config : copiedConfigs) {
            if (!config.ephemeral
                    || config.subscriptionId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                continue;
            }
            if (carrierPrivilegedPackages.contains(config.creatorName)) {
                continue;
            }
            removeNetwork(config.networkId, config.creatorUid, config.creatorName);
        }
    }

    /**
     * Helper method to perform the following operations during user switch/unlock:
     * - Remove private networks of the old user.
     * - Load from the new user store file.
     * - Save the store files again to migrate any user specific networks from the shared store
     *   to user store.
     * This method assumes the user store is visible (i.e CE storage is unlocked). So, the caller
     * should ensure that the stores are accessible before invocation.
     *
     * @param userId The identifier of the new foreground user, after the unlock or switch.
     */
    private void handleUserUnlockOrSwitch(int userId) {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "Loading from store after user switch/unlock for " + userId);
        }
        // Switch out the user store file.
        if (loadFromUserStoreAfterUnlockOrSwitch(userId)) {
            writeBufferedData(true);
            mPendingUnlockStoreRead = false;
        }
    }

    /**
     * Handles the switch to a different foreground user:
     * - Flush the current state to the old user's store file.
     * - Switch the user specific store file.
     * - Reload the networks from the store files (shared & user).
     * - Write the store files to move any user specific private networks from shared store to user
     *   store.
     *
     * Need to be called when {@link com.android.server.SystemService#onUserSwitching} is invoked.
     *
     * @param userId The identifier of the new foreground user, after the switch.
     * @return List of network ID's of all the private networks of the old user which will be
     * removed from memory.
     */
    public Set<Integer> handleUserSwitch(int userId) {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "Handling user switch for " + userId);
        }
        if (userId == mCurrentUserId) {
            Log.w(TAG, "User already in foreground " + userId);
            return new HashSet<>();
        }
        if (mPendingStoreRead) {
            Log.w(TAG, "User switch before store is read!");
            mConfiguredNetworks.setNewUser(userId);
            mCurrentUserId = userId;
            // Reset any state from previous user unlock.
            mDeferredUserUnlockRead = false;
            // Cannot read data from new user's CE store file before they log-in.
            mPendingUnlockStoreRead = true;
            return new HashSet<>();
        }
        if (mUserManager.isUserUnlockingOrUnlocked(UserHandle.of(mCurrentUserId))) {
            writeBufferedData(true);
        }
        // Remove any private networks of the old user before switching the userId.
        Set<Integer> removedNetworkIds = clearInternalDataForUser(mCurrentUserId);
        mConfiguredNetworks.setNewUser(userId);
        mCurrentUserId = userId;

        if (mUserManager.isUserUnlockingOrUnlocked(UserHandle.of(mCurrentUserId))) {
            handleUserUnlockOrSwitch(mCurrentUserId);
            // only handle the switching of unlocked users in {@link WifiCarrierInfoManager}.
            mWifiCarrierInfoManager.onUnlockedUserSwitching(mCurrentUserId);
        } else {
            // Cannot read data from new user's CE store file before they log-in.
            mPendingUnlockStoreRead = true;
            Log.i(TAG, "Waiting for user unlock to load from store");
        }
        return removedNetworkIds;
    }

    /**
     * Handles the unlock of foreground user. This maybe needed to read the store file if the user's
     * CE storage is not visible when {@link #handleUserSwitch(int)} is invoked.
     *
     * Need to be called when {@link com.android.server.SystemService#onUserUnlocking} is invoked.
     *
     * @param userId The identifier of the user that unlocked.
     */
    public void handleUserUnlock(int userId) {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "Handling user unlock for " + userId);
        }
        if (userId != mCurrentUserId) {
            Log.e(TAG, "Ignore user unlock for non current user " + userId);
            return;
        }
        if (mPendingStoreRead) {
            Log.w(TAG, "Ignore user unlock until store is read!");
            mDeferredUserUnlockRead = true;
            return;
        }
        if (mPendingUnlockStoreRead) {
            handleUserUnlockOrSwitch(mCurrentUserId);
        }
    }

    /**
     * Handles the stop of foreground user. This is needed to write the store file to flush
     * out any pending data before the user's CE store storage is unavailable.
     *
     * Need to be called when {@link com.android.server.SystemService#onUserStopping} is invoked.
     *
     * @param userId The identifier of the user that stopped.
     */
    public void handleUserStop(int userId) {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "Handling user stop for " + userId);
        }
        if (userId == mCurrentUserId
                && mUserManager.isUserUnlockingOrUnlocked(UserHandle.of(mCurrentUserId))) {
            writeBufferedData(true);
            clearInternalDataForUser(mCurrentUserId);
        }
    }

    /**
     * Helper method to clear internal databases.
     * This method clears the:
     *  - List of configured networks.
     *  - Map of scan detail caches.
     *  - List of deleted ephemeral networks.
     */
    private void clearInternalData() {
        localLog("clearInternalData: Clearing all internal data");
        mConfiguredNetworks.clear();
        mUserTemporarilyDisabledList.clear();
        mNonCarrierMergedNetworksStatusTracker.clear();
        mRandomizedMacAddressMapping.clear();
        mScanDetailCaches.clear();
        clearLastSelectedNetwork();
    }

    /**
     * Helper method to clear internal databases of the specified user.
     * This method clears the:
     *  - Private configured configured networks of the specified user.
     *  - Map of scan detail caches.
     *  - List of deleted ephemeral networks.
     *
     * @return List of network ID's of all the private networks of the old user which will be
     * removed from memory.
     */
    private Set<Integer> clearInternalDataForUser(int user) {
        localLog("clearInternalUserData: Clearing user internal data for " + user);
        Set<Integer> removedNetworkIds = new HashSet<>();
        // Remove any private networks of the old user before switching the userId.
        for (WifiConfiguration config : getConfiguredNetworks()) {
            if ((!config.shared
                    && mWifiPermissionsUtil.doesUidBelongToUser(config.creatorUid, user))
                    || config.ephemeral) {
                removedNetworkIds.add(config.networkId);
                localLog("clearInternalUserData: removed config."
                        + " netId=" + config.networkId
                        + " configKey=" + config.getProfileKey());
                mConfiguredNetworks.remove(config.networkId);
                for (OnNetworkUpdateListener listener : mListeners) {
                    listener.onNetworkRemoved(
                            createExternalWifiConfiguration(config, true, Process.WIFI_UID));
                }
            }
        }
        if (!removedNetworkIds.isEmpty()) {
            sendConfiguredNetworkChangedBroadcast(WifiManager.CHANGE_REASON_REMOVED, null);
        }
        mUserTemporarilyDisabledList.clear();
        mNonCarrierMergedNetworksStatusTracker.clear();
        mScanDetailCaches.clear();
        clearLastSelectedNetwork();
        return removedNetworkIds;
    }

    /**
     * Helper function to populate the internal (in-memory) data from the retrieved shared store
     * (file) data.
     *
     * @param configurations list of configurations retrieved from store.
     */
    private void loadInternalDataFromSharedStore(
            List<WifiConfiguration> configurations,
            Map<String, String> macAddressMapping) {

        long supportedFeatures = mWifiInjector.getActiveModeWarden()
                .getPrimaryClientModeManager().getSupportedFeatures();

        for (WifiConfiguration configuration : configurations) {
            if (!WifiConfigurationUtil.validate(
                    configuration, supportedFeatures, WifiConfigurationUtil.VALIDATE_FOR_ADD)) {
                Log.e(TAG, "Skipping malformed network from shared store: " + configuration);
                continue;
            }

            WifiConfiguration existingConfiguration = getInternalConfiguredNetwork(configuration);
            if (null != existingConfiguration) {
                Log.d(TAG, "Merging network from shared store "
                        + configuration.getProfileKey());
                mergeWithInternalWifiConfiguration(existingConfiguration, configuration);
                continue;
            }

            configuration.networkId = mNextNetworkId++;
            if (mVerboseLoggingEnabled) {
                Log.v(TAG, "Adding network from shared store "
                        + configuration.getProfileKey());
            }
            try {
                mConfiguredNetworks.put(configuration);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Failed to add network to config map", e);
            }
        }
        mRandomizedMacAddressMapping.putAll(macAddressMapping);
    }

    /**
     * Helper function to populate the internal (in-memory) data from the retrieved user store
     * (file) data.
     *
     * @param configurations list of configurations retrieved from store.
     */
    private void loadInternalDataFromUserStore(List<WifiConfiguration> configurations) {
        long supportedFeatures = mWifiInjector.getActiveModeWarden()
                .getPrimaryClientModeManager().getSupportedFeatures();

        for (WifiConfiguration configuration : configurations) {
            if (!WifiConfigurationUtil.validate(
                    configuration, supportedFeatures, WifiConfigurationUtil.VALIDATE_FOR_ADD)) {
                Log.e(TAG, "Skipping malformed network from user store: " + configuration);
                continue;
            }

            WifiConfiguration existingConfiguration = getInternalConfiguredNetwork(configuration);
            if (null != existingConfiguration) {
                Log.d(TAG, "Merging network from user store "
                        + configuration.getProfileKey());
                mergeWithInternalWifiConfiguration(existingConfiguration, configuration);
                continue;
            }

            configuration.networkId = mNextNetworkId++;
            if (mVerboseLoggingEnabled) {
                Log.v(TAG, "Adding network from user store "
                        + configuration.getProfileKey());
            }
            try {
                mConfiguredNetworks.put(configuration);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Failed to add network to config map", e);
            }

            if (configuration.isMostRecentlyConnected) {
                mLruConnectionTracker.addNetwork(configuration);
            }
        }
    }

    /**
     * Initializes the randomized MAC address for an internal WifiConfiguration depending on
     * whether it should use non-persistent randomization.
     * @param config
     */
    private void initRandomizedMacForInternalConfig(WifiConfiguration internalConfig) {
        MacAddress randomizedMac = shouldUseNonPersistentRandomization(internalConfig)
                ? MacAddressUtils.createRandomUnicastAddress()
                : getPersistentMacAddress(internalConfig);
        if (randomizedMac != null) {
            setRandomizedMacAddress(internalConfig, randomizedMac);
        }
    }

    /**
     * Assign randomized MAC addresses for configured networks.
     * This is needed to generate persistent randomized MAC address for existing networks when
     * a device updates to Q+ for the first time since we are not calling addOrUpdateNetwork when
     * we load configuration at boot.
     */
    private void generateRandomizedMacAddresses() {
        for (WifiConfiguration config : getInternalConfiguredNetworks()) {
            if (DEFAULT_MAC_ADDRESS.equals(config.getRandomizedMacAddress())) {
                initRandomizedMacForInternalConfig(config);
            }
        }
    }

    /**
     * Helper function to populate the internal (in-memory) data from the retrieved stores (file)
     * data.
     * This method:
     * 1. Clears all existing internal data.
     * 2. Sends out the networks changed broadcast after loading all the data.
     *
     * @param sharedConfigurations list of network configurations retrieved from shared store.
     * @param userConfigurations list of network configurations retrieved from user store.
     * @param macAddressMapping
     */
    private void loadInternalData(
            List<WifiConfiguration> sharedConfigurations,
            List<WifiConfiguration> userConfigurations,
            Map<String, String> macAddressMapping) {
        // Clear out all the existing in-memory lists and load the lists from what was retrieved
        // from the config store.
        clearInternalData();
        loadInternalDataFromSharedStore(sharedConfigurations, macAddressMapping);
        loadInternalDataFromUserStore(userConfigurations);
        generateRandomizedMacAddresses();
        if (mConfiguredNetworks.sizeForAllUsers() == 0) {
            Log.w(TAG, "No stored networks found.");
        }
        // reset identity & anonymous identity for networks using SIM-based authentication
        // on load (i.e. boot) so that if the user changed SIMs while the device was powered off,
        // we do not reuse stale credentials that would lead to authentication failure.
        resetSimNetworks();
        sendConfiguredNetworkChangedBroadcast(WifiManager.CHANGE_REASON_ADDED, null);
        mPendingStoreRead = false;
    }

    /**
     * Helper method to handle any config store errors on user builds vs other debuggable builds.
     */
    private boolean handleConfigStoreFailure(boolean onlyUserStore) {
        // On eng/userdebug builds, return failure to leave the device in a debuggable state.
        if (!mBuildProperties.isUserBuild()) return false;

        // On user builds, ignore the failure and let the user create new networks.
        Log.w(TAG, "Ignoring config store errors on user build");
        if (!onlyUserStore) {
            loadInternalData(Collections.emptyList(), Collections.emptyList(),
                    Collections.emptyMap());
        } else {
            loadInternalDataFromUserStore(Collections.emptyList());
        }
        return true;
    }

    /**
     * Read the config store and load the in-memory lists from the store data retrieved and sends
     * out the networks changed broadcast.
     *
     * This reads all the network configurations from:
     * 1. Shared WifiConfigStore.xml
     * 2. User WifiConfigStore.xml
     *
     * @return true on success or not needed (fresh install), false otherwise.
     */
    public boolean loadFromStore() {
        // If the user unlock comes in before we load from store, which means the user store have
        // not been setup yet for the current user. Setup the user store before the read so that
        // configurations for the current user will also being loaded.
        if (mDeferredUserUnlockRead) {
            Log.i(TAG, "Handling user unlock before loading from store.");
            List<WifiConfigStore.StoreFile> userStoreFiles =
                    WifiConfigStore.createUserFiles(
                            mCurrentUserId, mFrameworkFacade.isNiapModeOn(mContext));
            if (userStoreFiles == null) {
                Log.wtf(TAG, "Failed to create user store files");
                return false;
            }
            mWifiConfigStore.setUserStores(userStoreFiles);
            mDeferredUserUnlockRead = false;
        }
        try {
            mWifiConfigStore.read();
        } catch (IOException | IllegalStateException e) {
            Log.wtf(TAG, "Reading from new store failed. All saved networks are lost!", e);
            return handleConfigStoreFailure(false);
        } catch (XmlPullParserException e) {
            Log.wtf(TAG, "XML deserialization of store failed. All saved networks are lost!", e);
            return handleConfigStoreFailure(false);
        }
        loadInternalData(mNetworkListSharedStoreData.getConfigurations(),
                mNetworkListUserStoreData.getConfigurations(),
                mRandomizedMacStoreData.getMacMapping());
        return true;
    }

    /**
     * Read the user config store and load the in-memory lists from the store data retrieved and
     * sends out the networks changed broadcast.
     * This should be used for all user switches/unlocks to only load networks from the user
     * specific store and avoid reloading the shared networks.
     *
     * This reads all the network configurations from:
     * 1. User WifiConfigStore.xml
     *
     * @param userId The identifier of the foreground user.
     * @return true on success, false otherwise.
     */
    private boolean loadFromUserStoreAfterUnlockOrSwitch(int userId) {
        try {
            List<WifiConfigStore.StoreFile> userStoreFiles =
                    WifiConfigStore.createUserFiles(
                            userId, mFrameworkFacade.isNiapModeOn(mContext));
            if (userStoreFiles == null) {
                Log.e(TAG, "Failed to create user store files");
                return false;
            }
            mWifiConfigStore.switchUserStoresAndRead(userStoreFiles);
        } catch (IOException | IllegalStateException e) {
            Log.wtf(TAG, "Reading from new store failed. All saved private networks are lost!", e);
            return handleConfigStoreFailure(true);
        } catch (XmlPullParserException e) {
            Log.wtf(TAG, "XML deserialization of store failed. All saved private networks are "
                    + "lost!", e);
            return handleConfigStoreFailure(true);
        }
        loadInternalDataFromUserStore(mNetworkListUserStoreData.getConfigurations());
        return true;
    }

    /**
     * Save the current snapshot of the in-memory lists to the config store.
     *
     * @param forceWrite Whether the write needs to be forced or not.
     * @return Whether the write was successful or not, this is applicable only for force writes.
     */
    public synchronized boolean saveToStore(boolean forceWrite) {
        if (mPendingStoreRead) {
            Log.e(TAG, "Cannot save to store before store is read!");
            return false;
        }
        if (mFeatureFlags.delaySaveToStore()) {
            // When feature enabled, always do a delay write
            startBufferedWriteAlarm();
            return true;
        }
        return writeBufferedData(forceWrite);
    }

    /** Helper method to start a buffered write alarm if one doesn't already exist. */
    private void startBufferedWriteAlarm() {
        if (!mBufferedWritePending) {
            mAlarmManager.set(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    mClock.getElapsedSinceBootMillis() + BUFFERED_WRITE_ALARM_INTERVAL_MS,
                    BUFFERED_WRITE_ALARM_TAG,
                    mBufferedWriteListener,
                    mHandler);
            mBufferedWritePending = true;
        }
    }

    /** Helper method to stop a buffered write alarm if one exists. */
    private void stopBufferedWriteAlarm() {
        if (mBufferedWritePending) {
            mAlarmManager.cancel(mBufferedWriteListener);
            mBufferedWritePending = false;
        }
    }

    private boolean writeBufferedData(Boolean forceWrite) {
        stopBufferedWriteAlarm();
        ArrayList<WifiConfiguration> sharedConfigurations = new ArrayList<>();
        ArrayList<WifiConfiguration> userConfigurations = new ArrayList<>();
        // List of network IDs for legacy Passpoint configuration to be removed.
        List<Integer> legacyPasspointNetId = new ArrayList<>();
        for (WifiConfiguration config : mConfiguredNetworks.valuesForAllUsers()) {
            // Ignore ephemeral networks and non-legacy Passpoint configurations.
            if (config.ephemeral || (config.isPasspoint() && !config.isLegacyPasspointConfig)) {
                continue;
            }

            // Migrate the legacy Passpoint configurations owned by the current user to
            // {@link PasspointManager}.
            if (config.isLegacyPasspointConfig && mWifiPermissionsUtil
                    .doesUidBelongToUser(config.creatorUid, mCurrentUserId)) {
                legacyPasspointNetId.add(config.networkId);
                // Migrate the legacy Passpoint configuration and add it to PasspointManager.
                if (!PasspointManager.addLegacyPasspointConfig(config)) {
                    Log.e(TAG, "Failed to migrate legacy Passpoint config: " + config.FQDN);
                }
                // This will prevent adding |config| to the |sharedConfigurations|.
                continue;
            }

            config.isMostRecentlyConnected =
                    mLruConnectionTracker.isMostRecentlyConnected(config);

            // We push all shared networks & private networks not belonging to the current
            // user to the shared store. Ideally, private networks for other users should
            // not even be in memory,
            // But, this logic is in place to deal with store migration from N to O
            // because all networks were previously stored in a central file. We cannot
            // write these private networks to the user specific store until the corresponding
            // user logs in.
            if (config.shared || !mWifiPermissionsUtil
                    .doesUidBelongToUser(config.creatorUid, mCurrentUserId)) {
                sharedConfigurations.add(config);
            } else {
                userConfigurations.add(config);
            }
        }

        // Remove the configurations for migrated Passpoint configurations.
        for (int networkId : legacyPasspointNetId) {
            mConfiguredNetworks.remove(networkId);
        }

        // Setup store data for write.
        mNetworkListSharedStoreData.setConfigurations(sharedConfigurations);
        mNetworkListUserStoreData.setConfigurations(userConfigurations);
        mRandomizedMacStoreData.setMacMapping(mRandomizedMacAddressMapping);

        try {
            long start = mClock.getElapsedSinceBootMillis();
            mWifiConfigStore.write(forceWrite);
            mWifiMetrics.wifiConfigStored((int) (mClock.getElapsedSinceBootMillis() - start));
        } catch (IOException | IllegalStateException e) {
            Log.wtf(TAG, "Writing to store failed. Saved networks maybe lost!", e);
            return false;
        } catch (XmlPullParserException e) {
            Log.wtf(TAG, "XML serialization for store failed. Saved networks maybe lost!", e);
            return false;
        }
        return true;
    }

    /**
     * Helper method for logging into local log buffer.
     */
    private void localLog(String s) {
        if (mLocalLog != null) {
            mLocalLog.log(s);
        }
    }

    /**
     * Dump the local log buffer and other internal state of WifiConfigManager.
     */
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("Dump of WifiConfigManager");
        pw.println("WifiConfigManager - Log Begin ----");
        mLocalLog.dump(fd, pw, args);
        pw.println("WifiConfigManager - Log End ----");
        pw.println("WifiConfigManager - Configured networks Begin ----");
        for (WifiConfiguration network : getInternalConfiguredNetworks()) {
            pw.println(network);
        }
        pw.println("WifiConfigManager - Configured networks End ----");
        pw.println("WifiConfigManager - ConfigurationMap Begin ----");
        mConfiguredNetworks.dump(fd, pw, args);
        pw.println("WifiConfigManager - ConfigurationMap End ----");
        pw.println("WifiConfigManager - Next network ID to be allocated " + mNextNetworkId);
        pw.println("WifiConfigManager - Last selected network ID " + mLastSelectedNetworkId);
        pw.println("WifiConfigManager - PNO scan frequency culling enabled = "
                + mContext.getResources().getBoolean(R.bool.config_wifiPnoFrequencyCullingEnabled));
        pw.println("WifiConfigManager - PNO scan recency sorting enabled = "
                + mContext.getResources().getBoolean(R.bool.config_wifiPnoRecencySortingEnabled));
        mWifiConfigStore.dump(fd, pw, args);
        mWifiCarrierInfoManager.dump(fd, pw, args);
        mNonCarrierMergedNetworksStatusTracker.dump(fd, pw, args);
    }

    /**
     * Returns true if the given uid has permission to add, update or remove proxy settings
     */
    private boolean canModifyProxySettings(int uid, String packageName) {
        final boolean isAdmin = mWifiPermissionsUtil.isAdmin(uid, packageName);
        final boolean hasNetworkSettingsPermission =
                mWifiPermissionsUtil.checkNetworkSettingsPermission(uid);
        final boolean hasNetworkSetupWizardPermission =
                mWifiPermissionsUtil.checkNetworkSetupWizardPermission(uid);
        final boolean hasNetworkManagedProvisioningPermission =
                mWifiPermissionsUtil.checkNetworkManagedProvisioningPermission(uid);
        // If |uid| corresponds to the admin, allow all modifications.
        if (isAdmin || hasNetworkSettingsPermission
                || hasNetworkSetupWizardPermission || hasNetworkManagedProvisioningPermission
                || mWifiPermissionsUtil.checkConfigOverridePermission(uid)) {
            return true;
        }
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "UID: " + uid + " cannot modify WifiConfiguration proxy settings."
                    + " hasNetworkSettings=" + hasNetworkSettingsPermission
                    + " hasNetworkSetupWizard=" + hasNetworkSetupWizardPermission
                    + " Admin=" + isAdmin);
        }
        return false;
    }

    /**
     * Add the network update event listener
     */
    public void addOnNetworkUpdateListener(@NonNull OnNetworkUpdateListener listener) {
        if (listener == null) {
            Log.wtf(TAG, "addOnNetworkUpdateListener: listener must not be null");
            return;
        }
        mListeners.add(listener);
    }

    /**
     * Remove the network update event listener
     */
    public void removeOnNetworkUpdateListener(@NonNull OnNetworkUpdateListener listener) {
        if (listener == null) {
            Log.wtf(TAG, "removeOnNetworkUpdateListener: listener must not be null");
            return;
        }
        mListeners.remove(listener);
    }

    /**
     * Set extra failure reason for given config. Used to surface extra failure details to the UI
     * @param netId The network ID of the config to set the extra failure reason for
     * @param reason the WifiConfiguration.ExtraFailureReason failure code representing the most
     *               recent failure reason
     */
    public void setRecentFailureAssociationStatus(int netId, int reason) {
        WifiConfiguration config = getInternalConfiguredNetwork(netId);
        if (config == null) {
            return;
        }
        mWifiMetrics.incrementRecentFailureAssociationStatusCount(reason);
        int previousReason = config.recentFailure.getAssociationStatus();
        config.recentFailure.setAssociationStatus(reason, mClock.getElapsedSinceBootMillis());
        if (previousReason != reason) {
            sendConfiguredNetworkChangedBroadcast(WifiManager.CHANGE_REASON_CONFIG_CHANGE, config);
        }
    }

    /**
     * @param netId The network ID of the config to clear the extra failure reason from
     */
    public void clearRecentFailureReason(int netId) {
        WifiConfiguration config = getInternalConfiguredNetwork(netId);
        if (config == null) {
            return;
        }
        config.recentFailure.clear();
    }

    /**
     * Clear all recent failure reasons that have timed out.
     */
    public void cleanupExpiredRecentFailureReasons() {
        long timeoutDuration = mContext.getResources().getInteger(
                R.integer.config_wifiRecentFailureReasonExpirationMinutes) * 60 * 1000;
        for (WifiConfiguration config : getInternalConfiguredNetworks()) {
            if (config.recentFailure.getAssociationStatus()
                    != WifiConfiguration.RECENT_FAILURE_NONE
                    && mClock.getElapsedSinceBootMillis()
                    >= config.recentFailure.getLastUpdateTimeSinceBootMillis() + timeoutDuration) {
                config.recentFailure.clear();
                sendConfiguredNetworkChangedBroadcast(WifiManager.CHANGE_REASON_CONFIG_CHANGE,
                        config);
            }
        }
    }

    /**
     * Find the highest RSSI among all valid scanDetails in current network's scanDetail cache.
     * If scanDetail is too old, it is not considered to be valid.
     * @param netId The network ID of the config to find scan RSSI
     * @params scanRssiValidTimeMs The valid time for scan RSSI
     * @return The highest RSSI in dBm found with current network's scanDetail cache.
     */
    public int findScanRssi(int netId, int scanRssiValidTimeMs) {
        int scanMaxRssi = WifiInfo.INVALID_RSSI;
        ScanDetailCache scanDetailCache = getScanDetailCacheForNetwork(netId);
        if (scanDetailCache == null || scanDetailCache.size() == 0) return scanMaxRssi;
        long nowInMillis = mClock.getWallClockMillis();
        for (ScanDetail scanDetail : scanDetailCache.values()) {
            ScanResult result = scanDetail.getScanResult();
            if (result == null) continue;
            boolean valid = (nowInMillis - result.seen) < scanRssiValidTimeMs;

            if (valid) {
                scanMaxRssi = Math.max(scanMaxRssi, result.level);
            }
        }
        return scanMaxRssi;
    }

    public Comparator<WifiConfiguration> getScanListComparator() {
        return mScanListComparator;
    }

    /**
     * This API is called when a connection successfully completes on an existing network
     * selected by the user. It is not called after the first connection of a newly added network.
     * Following actions will be triggered:
     * 1. If this network is disabled, we need re-enable it again.
     * 2. This network is favored over all the other networks visible in latest network
     * selection procedure.
     *
     * @param netId ID for the network chosen by the user
     * @param rssi the signal strength of the user selected network
     * @return true -- There is change made to connection choice of any saved network.
     * false -- There is no change made to connection choice of any saved network.
     */
    private boolean setUserConnectChoice(int netId, int rssi) {
        localLog("userSelectNetwork: network ID=" + netId);
        WifiConfiguration selected = getInternalConfiguredNetwork(netId);

        if (selected == null || selected.getProfileKey() == null) {
            localLog("userSelectNetwork: Invalid configuration with nid=" + netId);
            return false;
        }

        // Enable the network if it is disabled.
        if (!selected.getNetworkSelectionStatus().isNetworkEnabled()) {
            updateNetworkSelectionStatus(selected,
                    WifiConfiguration.NetworkSelectionStatus.DISABLED_NONE);
        }
        boolean changed = setLegacyUserConnectChoice(selected, rssi);
        return changed;
    }

    /**
     * This maintains the legacy user connect choice state in the config store
     */
    public boolean setLegacyUserConnectChoice(@NonNull final WifiConfiguration selected,
            int rssi) {
        boolean change = false;
        Collection<WifiConfiguration> configuredNetworks = getInternalConfiguredNetworks();
        ArrayList<WifiConfiguration> networksInRange = new ArrayList<>();
        String key = selected.getProfileKey();
        for (WifiConfiguration network : configuredNetworks) {
            WifiConfiguration.NetworkSelectionStatus status = network.getNetworkSelectionStatus();
            if (network.networkId == selected.networkId) {
                if (status.getConnectChoice() != null) {
                    localLog("Remove user selection preference of " + status.getConnectChoice()
                            + " from " + network.SSID + " : " + network.networkId);
                    clearConnectChoiceInternal(network);
                    change = true;
                }
                continue;
            }

            if (status.getSeenInLastQualifiedNetworkSelection()) {
                setConnectChoiceInternal(network, key, rssi);
                change = true;
                networksInRange.add(network);
            }
        }

        for (OnNetworkUpdateListener listener : mListeners) {
            listener.onConnectChoiceSet(networksInRange, key, rssi);
        }
        return change;
    }

    private void clearConnectChoiceInternal(WifiConfiguration config) {
        config.getNetworkSelectionStatus().setConnectChoice(null);
        config.getNetworkSelectionStatus().setConnectChoiceRssi(0);
    }

    private void setConnectChoiceInternal(WifiConfiguration config, String key, int rssi) {
        config.getNetworkSelectionStatus().setConnectChoice(key);
        config.getNetworkSelectionStatus().setConnectChoiceRssi(rssi);
        localLog("Add connect choice key: " + key + " rssi: " + rssi + " to "
                + WifiNetworkSelector.toNetworkString(config));
    }

    /** Update WifiConfigManager before connecting to a network. */
    public void updateBeforeConnect(int networkId, int callingUid, @NonNull String packageName,
            boolean disableOthers) {
        userEnabledNetwork(networkId);
        if (!enableNetwork(networkId, disableOthers, callingUid, null)
                || !updateLastConnectUid(networkId, callingUid)) {
            Log.i(TAG, "connect Allowing uid " + callingUid + " packageName " + packageName
                    + " with insufficient permissions to connect=" + networkId);
        }
    }

    /** See {@link WifiManager#save(WifiConfiguration, WifiManager.ActionListener)} */
    public NetworkUpdateResult updateBeforeSaveNetwork(WifiConfiguration config, int callingUid,
            @NonNull String packageName) {
        NetworkUpdateResult result = addOrUpdateNetwork(config, callingUid);
        if (!result.isSuccess()) {
            Log.e(TAG, "saveNetwork adding/updating config=" + config + " failed");
            return result;
        }
        if (!enableNetwork(result.getNetworkId(), false, callingUid, null)) {
            Log.e(TAG, "saveNetwork enabling config=" + config + " failed");
            return NetworkUpdateResult.makeFailed();
        }
        return result;
    }

    /**
     * Gets the most recent scan result that is newer than maxAgeMillis for each configured network.
     * @param maxAgeMillis scan results older than this parameter will get filtered out.
     */
    public @NonNull List<ScanResult> getMostRecentScanResultsForConfiguredNetworks(
            int maxAgeMillis) {
        List<ScanResult> results = new ArrayList<>();
        long timeNowMs = mClock.getWallClockMillis();
        for (WifiConfiguration config : getInternalConfiguredNetworks()) {
            ScanDetailCache scanDetailCache = getScanDetailCacheForNetwork(config.networkId);
            if (scanDetailCache == null) {
                continue;
            }
            ScanResult scanResult = scanDetailCache.getMostRecentScanResult();
            if (scanResult == null) {
                continue;
            }
            if (timeNowMs - scanResult.seen < maxAgeMillis) {
                results.add(scanResult);
            }
        }
        return results;
    }

    /**
     * Update the configuration according to transition disable indications.
     *
     * @param networkId network ID corresponding to the network.
     * @param indicationBit transition disable indication bits.
     * @return true if the network was found, false otherwise.
     */
    public boolean updateNetworkTransitionDisable(int networkId,
            @WifiMonitor.TransitionDisableIndication int indicationBit) {
        localLog("updateNetworkTransitionDisable: network ID=" + networkId
                + " indication: " + indicationBit);
        WifiConfiguration config = getInternalConfiguredNetwork(networkId);
        if (config == null) {
            Log.e(TAG, "Cannot find network for " + networkId);
            return false;
        }
        WifiConfiguration copy = new WifiConfiguration(config);
        boolean changed = false;
        if (0 != (indicationBit & WifiMonitor.TDI_USE_WPA3_PERSONAL)
                && config.isSecurityType(WifiConfiguration.SECURITY_TYPE_SAE)) {
            config.setSecurityParamsEnabled(WifiConfiguration.SECURITY_TYPE_PSK, false);
            changed = true;
        }
        if (0 != (indicationBit & WifiMonitor.TDI_USE_SAE_PK)) {
            config.enableSaePkOnlyMode(true);
            changed = true;
        }
        if (0 != (indicationBit & WifiMonitor.TDI_USE_WPA3_ENTERPRISE)
                && config.isSecurityType(WifiConfiguration.SECURITY_TYPE_EAP_WPA3_ENTERPRISE)) {
            config.setSecurityParamsEnabled(WifiConfiguration.SECURITY_TYPE_EAP, false);
            changed = true;
        }
        if (0 != (indicationBit & WifiMonitor.TDI_USE_ENHANCED_OPEN)
                && config.isSecurityType(WifiConfiguration.SECURITY_TYPE_OWE)) {
            config.setSecurityParamsEnabled(WifiConfiguration.SECURITY_TYPE_OPEN, false);
            changed = true;
        }
        if (changed) {
            for (OnNetworkUpdateListener listener : mListeners) {
                listener.onSecurityParamsUpdate(copy, config.getSecurityParamsList());
            }
        }

        return true;
    }

    /**
     * Retrieves the configured network corresponding to the provided configKey
     * without any masking.
     *
     * WARNING: Don't use this to pass network configurations except in the wifi stack, when
     * there is a need for passwords and randomized MAC address.
     *
     * @param configKey configKey of the requested network.
     * @return Copy of WifiConfiguration object if found, null otherwise.
     */
    private WifiConfiguration getConfiguredNetworkWithoutMasking(String configKey) {
        WifiConfiguration config = getInternalConfiguredNetwork(configKey);
        if (config == null) {
            return null;
        }
        return new WifiConfiguration(config);
    }

    /**
     * This method links the config of the provided network id to every linkable saved network.
     *
     * @param networkId networkId corresponding to the network to be potentially linked.
     */
    public void updateLinkedNetworks(int networkId) {
        WifiConfiguration internalConfig = getInternalConfiguredNetwork(networkId);
        if (internalConfig == null) {
            return;
        }
        internalConfig.linkedConfigurations = new HashMap<>();
        attemptNetworkLinking(internalConfig);
    }

    /**
     * This method returns a map containing each config key and unmasked WifiConfiguration of every
     * network linked to the provided network id.
     * @param networkId networkId to get the linked configs of.
     * @return HashMap of config key to unmasked WifiConfiguration
     */
    public Map<String, WifiConfiguration> getLinkedNetworksWithoutMasking(int networkId) {
        WifiConfiguration internalConfig = getInternalConfiguredNetwork(networkId);
        if (internalConfig == null) {
            return null;
        }

        Map<String, WifiConfiguration> linkedNetworks = new HashMap<>();
        Map<String, Integer> linkedConfigurations = internalConfig.linkedConfigurations;
        if (linkedConfigurations == null) {
            return null;
        }
        for (String configKey : linkedConfigurations.keySet()) {
            WifiConfiguration linkConfig = getConfiguredNetworkWithoutMasking(configKey);
            if (linkConfig == null) continue;

            if (!WifiConfigurationUtil.isConfigLinkable(linkConfig)) continue;

            SecurityParams defaultParams =
                     SecurityParams.createSecurityParamsBySecurityType(
                             WifiConfiguration.SECURITY_TYPE_PSK);

            if (!linkConfig.isSecurityType(WifiConfiguration.SECURITY_TYPE_PSK)
                    || !linkConfig.getSecurityParams(
                            WifiConfiguration.SECURITY_TYPE_PSK).isEnabled()) {
                defaultParams = SecurityParams.createSecurityParamsBySecurityType(
                        WifiConfiguration.SECURITY_TYPE_SAE);
            }

            linkConfig.getNetworkSelectionStatus().setCandidateSecurityParams(defaultParams);
            linkedNetworks.put(configKey, linkConfig);
        }
        return linkedNetworks;
    }

    /**
     * This method updates FILS AKMs to the internal network.
     *
     * @param networkId networkId corresponding to the network to be updated.
     */
    public void updateFilsAkms(int networkId,
            boolean isFilsSha256Supported, boolean isFilsSha384Supported) {
        WifiConfiguration internalConfig = getInternalConfiguredNetwork(networkId);
        if (internalConfig == null) {
            return;
        }
        internalConfig.enableFils(isFilsSha256Supported, isFilsSha384Supported);
    }

    /**
     * This method updates auto-upgrade flag to the internal network.
     *
     * @param networkId networkId corresponding to the network to be updated.
     * @param securityType the target security type
     * @param isAddedByAutoUpgrade indicate whether the target security type is added
     *        by auto-upgrade or not.
     */
    public void updateIsAddedByAutoUpgradeFlag(int networkId,
            int securityType, boolean isAddedByAutoUpgrade) {
        WifiConfiguration internalConfig = getInternalConfiguredNetwork(networkId);
        if (internalConfig == null) {
            return;
        }
        internalConfig.setSecurityParamsIsAddedByAutoUpgrade(securityType, isAddedByAutoUpgrade);
        saveToStore(true);
    }

    private static final int SUBJECT_ALTERNATIVE_NAMES_EMAIL = 1;
    private static final int SUBJECT_ALTERNATIVE_NAMES_DNS = 2;
    private static final int SUBJECT_ALTERNATIVE_NAMES_URI = 6;
    /** altSubjectMatch only matches EMAIL, DNS, and URI. */
    private static String getAltSubjectMatchFromAltSubjectName(X509Certificate cert) {
        Collection<List<?>> col = null;
        try {
            col = cert.getSubjectAlternativeNames();
        } catch (CertificateParsingException ex) {
            col = null;
        }

        if (null == col) return null;
        if (0 == col.size()) return null;

        List<String> altSubjectNameList = new ArrayList<>();
        for (List<?> item: col) {
            if (2 != item.size()) continue;
            if (!(item.get(0) instanceof Integer)) continue;
            if (!(item.get(1) instanceof String)) continue;

            StringBuilder sb = new StringBuilder();
            int type = (Integer) item.get(0);
            if (SUBJECT_ALTERNATIVE_NAMES_EMAIL == type) {
                sb.append("EMAIL:");
            } else if (SUBJECT_ALTERNATIVE_NAMES_DNS == type) {
                sb.append("DNS:");
            } else if (SUBJECT_ALTERNATIVE_NAMES_URI == type) {
                sb.append("URI:");
            } else {
                Log.d(TAG, "Ignore type " + type + " for altSubjectMatch");
                continue;
            }
            sb.append((String) item.get(1));
            altSubjectNameList.add(sb.toString());
        }
        if (altSubjectNameList.size() > 0) {
            // wpa_supplicant uses ';' as the separator.
            return String.join(";", altSubjectNameList);
        }
        return null;
    }

    /**
     * This method updates the Root CA certificate and the domain name of the
     * server in the internal network.
     *
     * @param networkId networkId corresponding to the network to be updated.
     * @param caCert Root CA certificate to be updated.
     * @param serverCert Server certificate to be updated.
     * @param certHash Server certificate hash (for TOFU case with no Root CA). Replaces the use of
     *                 a Root CA certificate for authentication.
     * @param useSystemTrustStore Indicate if to use the system trust store for authentication. If
     *                            this flag is set, then any Root CA or certificate hash specified
     *                            is not used.
     * @return true if updating Root CA certificate successfully; otherwise, false.
     */
    public boolean updateCaCertificate(int networkId, @NonNull X509Certificate caCert,
            @NonNull X509Certificate serverCert, String certHash, boolean useSystemTrustStore) {
        WifiConfiguration internalConfig = getInternalConfiguredNetwork(networkId);
        if (internalConfig == null) {
            Log.e(TAG, "No network for network ID " + networkId);
            return false;
        }
        if (!internalConfig.isEnterprise()) {
            Log.e(TAG, "Network " + networkId + " is not an Enterprise network");
            return false;
        }
        if (!internalConfig.enterpriseConfig.isEapMethodServerCertUsed()) {
            Log.e(TAG, "Network " + networkId + " does not need verifying server cert");
            return false;
        }
        if (null == caCert) {
            Log.e(TAG, "Root CA cert is null");
            return false;
        }
        if (null == serverCert) {
            Log.e(TAG, "Server cert is null");
            return false;
        }
        CertificateSubjectInfo serverCertInfo = CertificateSubjectInfo.parse(
                serverCert.getSubjectX500Principal().getName());
        if (null == serverCertInfo) {
            Log.e(TAG, "Invalid Server CA cert subject");
            return false;
        }

        WifiConfiguration newConfig = new WifiConfiguration(internalConfig);
        try {
            if (newConfig.enterpriseConfig.isTrustOnFirstUseEnabled()) {
                if (useSystemTrustStore) {
                    newConfig.enterpriseConfig
                            .setCaPath(WifiConfigurationUtil.getSystemTrustStorePath());
                } else if (TextUtils.isEmpty(certHash)) {
                    newConfig.enterpriseConfig.setCaCertificateForTrustOnFirstUse(caCert);
                } else {
                    newConfig.enterpriseConfig.setServerCertificateHash(certHash);
                }
                newConfig.enterpriseConfig.enableTrustOnFirstUse(false);
            } else {
                // setCaCertificate will mark that this CA certificate should be removed on
                // removing this configuration.
                newConfig.enterpriseConfig.setCaCertificate(caCert);
            }
        } catch (IllegalArgumentException ex) {
            Log.e(TAG, "Failed to set CA cert: " + caCert);
            return false;
        }

        // If there is a subject alternative name, it should be matched first.
        String altSubjectNames = getAltSubjectMatchFromAltSubjectName(serverCert);
        if (!TextUtils.isEmpty(altSubjectNames)) {
            if (mVerboseLoggingEnabled) {
                Log.d(TAG, "Set altSubjectMatch to " + altSubjectNames);
            }
            newConfig.enterpriseConfig.setAltSubjectMatch(altSubjectNames);
        }
        if (mVerboseLoggingEnabled) {
            Log.d(TAG, "Set domainSuffixMatch to " + serverCertInfo.commonName);
        }
        newConfig.enterpriseConfig.setDomainSuffixMatch(serverCertInfo.commonName);
        newConfig.enterpriseConfig.setUserApproveNoCaCert(false);
        // Trigger an update to install CA certificate and the corresponding configuration.
        NetworkUpdateResult result = addOrUpdateNetwork(newConfig, internalConfig.creatorUid);
        if (!result.isSuccess()) {
            Log.e(TAG, "Failed to install CA cert for network " + internalConfig.SSID);
            mFrameworkFacade.showToast(mContext, mContext.getResources().getString(
                    R.string.wifi_ca_cert_failed_to_install_ca_cert));
            return false;
        }
        return true;
    }

    /**
     * This method updates Trust On First Use flag according to
     * Trust On First Use support and No-Ca-Cert Approval.
     */
    public void updateTrustOnFirstUseFlag(boolean enableTrustOnFirstUse) {
        getInternalConfiguredNetworks().stream()
                .filter(config -> config.isEnterprise())
                .filter(config -> config.enterpriseConfig.isEapMethodServerCertUsed())
                .filter(config -> !config.enterpriseConfig.hasCaCertificate())
                .forEach(config ->
                        config.enterpriseConfig.enableTrustOnFirstUse(enableTrustOnFirstUse));
    }

    /**
     * This method updates that a network could has no CA cert as a user approves it.
     *
     * @param networkId networkId corresponding to the network to be updated.
     * @param approved true for the approval; otherwise, false.
     */
    public void setUserApproveNoCaCert(int networkId, boolean approved) {
        WifiConfiguration internalConfig = getInternalConfiguredNetwork(networkId);
        if (internalConfig == null) return;
        if (!internalConfig.isEnterprise()) return;
        if (!internalConfig.enterpriseConfig.isEapMethodServerCertUsed()) return;
        internalConfig.enterpriseConfig.setUserApproveNoCaCert(approved);
    }

    /**
     * This method updates that a network uses Trust On First Use.
     *
     * @param networkId networkId corresponding to the network to be updated.
     * @param enable true to enable Trust On First Use; otherwise, disable Trust On First Use.
     */
    public void enableTrustOnFirstUse(int networkId, boolean enable) {
        WifiConfiguration internalConfig = getInternalConfiguredNetwork(networkId);
        if (internalConfig == null) return;
        if (!internalConfig.isEnterprise()) return;
        if (!internalConfig.enterpriseConfig.isEapMethodServerCertUsed()) return;
        internalConfig.enterpriseConfig.enableTrustOnFirstUse(enable);
    }

    /**
     * Indicate whether the user approved the TOFU dialog for this network.
     *
     * @param networkId networkId corresponding to the network to be updated.
     * @param approved true if the user approved the dialog, false otherwise.
     */
    public void setTofuDialogApproved(int networkId, boolean approved) {
        WifiConfiguration internalConfig = getInternalConfiguredNetwork(networkId);
        if (internalConfig == null) return;
        if (!internalConfig.isEnterprise()) return;
        if (!internalConfig.enterpriseConfig.isEapMethodServerCertUsed()) return;
        internalConfig.enterpriseConfig.setTofuDialogApproved(approved);
    }

    /**
     * Indicate the post-connection TOFU state for this network.
     *
     * @param networkId networkId corresponding to the network to be updated.
     * @param state one of the post-connection {@link WifiEnterpriseConfig.TofuConnectionState}
     *              values
     */
    public void setTofuPostConnectionState(int networkId,
            @WifiEnterpriseConfig.TofuConnectionState int state) {
        if (state != WifiEnterpriseConfig.TOFU_STATE_CONFIGURE_ROOT_CA
                && state != WifiEnterpriseConfig.TOFU_STATE_CERT_PINNING) {
            Log.e(TAG, "Invalid post-connection TOFU state " + state);
            return;
        }
        WifiConfiguration internalConfig = getInternalConfiguredNetwork(networkId);
        if (internalConfig == null) return;
        if (!internalConfig.isEnterprise()) return;
        if (!internalConfig.enterpriseConfig.isEapMethodServerCertUsed()) return;
        internalConfig.enterpriseConfig.setTofuConnectionState(state);
    }

    /**
     * Add custom DHCP options.
     *
     * @param ssid the network SSID.
     * @param oui the 3-byte OUI.
     * @param options the list of DHCP options.
     */
    public void addCustomDhcpOptions(@NonNull WifiSsid ssid, @NonNull byte[] oui,
            @NonNull List<DhcpOption> options) {
        mCustomDhcpOptions.put(new NetworkIdentifier(ssid, oui), options);
    }

    /**
     * Remove custom DHCP options.
     *
     * @param ssid the network SSID.
     * @param oui the 3-byte OUI.
     */
    public void removeCustomDhcpOptions(@NonNull WifiSsid ssid, @NonNull byte[] oui) {
        mCustomDhcpOptions.remove(new NetworkIdentifier(ssid, oui));
    }

    /**
     * Get custom DHCP options.
     *
     * @param ssid the network SSID.
     * @param ouiList the list of OUIs.
     *
     * @return null if no entry in the map is keyed by the SSID and any OUI in the list.
     *         all the DHCP options keyed by the SSID and the OUIs in the list.
     */
    public List<DhcpOption> getCustomDhcpOptions(@NonNull WifiSsid ssid,
            @NonNull List<byte[]> ouiList) {
        Set<DhcpOption> results = new HashSet<>();
        for (byte[] oui : ouiList) {
            List<DhcpOption> options = mCustomDhcpOptions.get(new NetworkIdentifier(ssid, oui));
            if (options != null) {
                results.addAll(options);
            }
        }
        return new ArrayList<>(results);
    }

    /**
     * Handle the device shutdown, should write all cached data to the storage
     */
    public void handleShutDown() {
        if (mPendingStoreRead) {
            Log.e(TAG, "Cannot save to store before store is read!");
            return;
        }
        writeBufferedData(true);
    }
}
