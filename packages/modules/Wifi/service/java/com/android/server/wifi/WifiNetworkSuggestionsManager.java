/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static android.app.AppOpsManager.MODE_IGNORED;
import static android.app.AppOpsManager.OPSTR_CHANGE_WIFI_STATE;
import static android.net.wifi.WifiManager.ACTION_REMOVE_SUGGESTION_DISCONNECT;
import static android.net.wifi.WifiManager.ACTION_REMOVE_SUGGESTION_LINGER;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.drawable.Icon;
import android.net.MacAddress;
import android.net.wifi.ISuggestionConnectionStatusListener;
import android.net.wifi.ISuggestionUserApprovalStatusListener;
import android.net.wifi.ScanResult;
import android.net.wifi.SecurityParams;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiContext;
import android.net.wifi.WifiEnterpriseConfig;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSuggestion;
import android.net.wifi.WifiScanner;
import android.net.wifi.WifiSsid;
import android.net.wifi.hotspot2.PasspointConfiguration;
import android.os.Process;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.EventLog;
import android.util.Log;
import android.util.Pair;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.messages.nano.SystemMessageProto.SystemMessage;
import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.util.LruConnectionTracker;
import com.android.server.wifi.util.WifiPermissionsUtil;
import com.android.wifi.resources.R;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.concurrent.NotThreadSafe;

/**
 * Network Suggestions Manager.
 * NOTE: This class should always be invoked from the main wifi service thread.
 */
@NotThreadSafe
@SuppressLint("LongLogTag")
public class WifiNetworkSuggestionsManager {
    private static final String TAG = "WifiNetworkSuggestionsManager";

    /** Intent when user tapped action button to allow the app. */
    @VisibleForTesting
    public static final String NOTIFICATION_USER_ALLOWED_APP_INTENT_ACTION =
            "com.android.server.wifi.action.NetworkSuggestion.USER_ALLOWED_APP";
    /** Intent when user tapped action button to disallow the app. */
    @VisibleForTesting
    public static final String NOTIFICATION_USER_DISALLOWED_APP_INTENT_ACTION =
            "com.android.server.wifi.action.NetworkSuggestion.USER_DISALLOWED_APP";
    /** Intent when user dismissed the notification. */
    @VisibleForTesting
    public static final String NOTIFICATION_USER_DISMISSED_INTENT_ACTION =
            "com.android.server.wifi.action.NetworkSuggestion.USER_DISMISSED";
    @VisibleForTesting
    public static final String EXTRA_PACKAGE_NAME =
            "com.android.server.wifi.extra.NetworkSuggestion.PACKAGE_NAME";
    @VisibleForTesting
    public static final String EXTRA_UID =
            "com.android.server.wifi.extra.NetworkSuggestion.UID";

    public static final int APP_TYPE_CARRIER_PRIVILEGED = 1;
    public static final int APP_TYPE_NETWORK_PROVISIONING = 2;
    public static final int APP_TYPE_NON_PRIVILEGED = 3;

    public static final int ACTION_USER_ALLOWED_APP = 1;
    public static final int ACTION_USER_DISALLOWED_APP = 2;
    public static final int ACTION_USER_DISMISS = 3;

    public static final int DEFAULT_PRIORITY_GROUP = 0;

    @IntDef(prefix = { "ACTION_USER_" }, value = {
            ACTION_USER_ALLOWED_APP,
            ACTION_USER_DISALLOWED_APP,
            ACTION_USER_DISMISS
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface UserActionCode { }

    /**
     * Limit number of hidden networks attach to scan
     */
    private static final int NUMBER_OF_HIDDEN_NETWORK_FOR_ONE_SCAN = 100;
    /**
     * Expiration timeout for user notification in milliseconds. (15 min)
     */
    private static final long NOTIFICATION_EXPIRY_MILLS = 15 * 60 * 1000;
    /**
     * Notification update delay in milliseconds. (10 min)
     */
    private static final long NOTIFICATION_UPDATE_DELAY_MILLS = 10 * 60 * 1000;

    /**
     * Modifiable only for testing.
     */
    private static final String LINGER_DELAY_PROPERTY = "persist.netmon.linger";
    /**
     * Default to 30s linger time-out. Should be same as ConnectivityService#DEFAULT_LINGER_DELAY_MS
     */
    @VisibleForTesting
    public static final int DEFAULT_LINGER_DELAY_MS = 30_000;

    private final WifiContext mContext;
    private final Resources mResources;
    private final RunnerHandler mHandler;
    private final AppOpsManager mAppOps;
    private final ActivityManager mActivityManager;
    private final WifiNotificationManager mNotificationManager;
    private final WifiPermissionsUtil mWifiPermissionsUtil;
    private final WifiConfigManager mWifiConfigManager;
    private final WifiMetrics mWifiMetrics;
    private final WifiInjector mWifiInjector;
    private final FrameworkFacade mFrameworkFacade;
    private final WifiCarrierInfoManager mWifiCarrierInfoManager;
    private final WifiKeyStore mWifiKeyStore;
    private final Clock mClock;
    // Keep order of network connection.
    private final LruConnectionTracker mLruConnectionTracker;

    private class OnNetworkUpdateListener implements
            WifiConfigManager.OnNetworkUpdateListener {

        @Override
        public void onConnectChoiceSet(@NonNull List<WifiConfiguration> networks,
                String choiceKey, int rssi) {
            onUserConnectChoiceSetForSuggestion(networks, choiceKey, rssi);
        }

        @Override
        public void onConnectChoiceRemoved(@NonNull String choiceKey) {
            if (choiceKey == null) {
                return;
            }
            onUserConnectChoiceRemoveForSuggestion(choiceKey);
        }

        @Override
        public void onSecurityParamsUpdate(@NonNull WifiConfiguration configuration,
                @NonNull List<SecurityParams> securityParams) {
            if (configuration == null || securityParams == null || securityParams.isEmpty()) {
                Log.e(TAG, "onSecurityParamsUpdate: must have valid config and "
                        + "securityParams");
                return;
            }
            onSecurityParamsUpdateForSuggestion(configuration, securityParams);
        }
    }

    /**
     * Per app meta data to store network suggestions, status, etc for each app providing network
     * suggestions on the device.
     */
    public static class PerAppInfo {
        /**
         * UID of the app.
         */
        public int uid;
        /**
         * Package Name of the app.
         */
        public final String packageName;
        /**
         * First Feature in the package that registered the suggestion
         */
        public final String featureId;
        /**
97         * Map of active network suggestions provided by the app keyed by hashcode.
         */
        public final Map<Integer, ExtendedWifiNetworkSuggestion> extNetworkSuggestions =
                new ArrayMap<>();
        /**
         * Whether we have shown the user a notification for this app.
         */
        public boolean hasUserApproved = false;
        /**
         * Carrier Id of SIM which give app carrier privileges.
         */
        public int carrierId = TelephonyManager.UNKNOWN_CARRIER_ID;

        /** Stores the max size of the {@link #extNetworkSuggestions} list ever for this app */
        public int maxSize = 0;

        public PerAppInfo(int uid, @NonNull String packageName, @Nullable String featureId) {
            this.uid = uid;
            this.packageName = packageName;
            this.featureId = featureId;
        }

        /**
         * Needed for migration of config store data.
         */
        public void setUid(int uid) {
            if (this.uid == Process.INVALID_UID) {
                this.uid = uid;
            }
            // else ignored.
        }

        /**
         * Needed when a normal App became carrier privileged when SIM insert
         */
        public void setCarrierId(int carrierId) {
            if (this.carrierId == TelephonyManager.UNKNOWN_CARRIER_ID) {
                this.carrierId = carrierId;
            }
            // else ignored.
        }

        /**
         * Returns true if this app has the necessary approvals to place network suggestions.
         */
        private boolean isApproved() {
            return hasUserApproved || carrierId != TelephonyManager.UNKNOWN_CARRIER_ID;
        }

        // This is only needed for comparison in unit tests.
        @Override
        public boolean equals(Object other) {
            if (other == null) return false;
            if (!(other instanceof PerAppInfo)) return false;
            PerAppInfo otherPerAppInfo = (PerAppInfo) other;
            return uid == otherPerAppInfo.uid
                    && TextUtils.equals(packageName, otherPerAppInfo.packageName)
                    && Objects.equals(extNetworkSuggestions, otherPerAppInfo.extNetworkSuggestions)
                    && hasUserApproved == otherPerAppInfo.hasUserApproved;
        }

        // This is only needed for comparison in unit tests.
        @Override
        public int hashCode() {
            return Objects.hash(uid, packageName, extNetworkSuggestions, hasUserApproved);
        }

        @Override
        public String toString() {
            return new StringBuilder("PerAppInfo[ ")
                    .append("uid=").append(uid)
                    .append(", packageName=").append(packageName)
                    .append(", hasUserApproved=").append(hasUserApproved)
                    .append(", suggestions=").append(extNetworkSuggestions)
                    .append(" ]")
                    .toString();
        }
    }

    /**
     * Internal container class which holds a network suggestion and a pointer to the
     * {@link PerAppInfo} entry from {@link #mActiveNetworkSuggestionsPerApp} corresponding to the
     * app that made the suggestion.
     */
    public static class ExtendedWifiNetworkSuggestion {
        public final WifiNetworkSuggestion wns;
        // Store the pointer to the corresponding app's meta data.
        public final PerAppInfo perAppInfo;
        public boolean isAutojoinEnabled;
        public String anonymousIdentity = null;
        public String connectChoice = null;
        public int connectChoiceRssi = 0;

        public ExtendedWifiNetworkSuggestion(@NonNull WifiNetworkSuggestion wns,
                                             @NonNull PerAppInfo perAppInfo,
                                             boolean isAutoJoinEnabled) {
            this.wns = wns;
            this.perAppInfo = perAppInfo;
            this.isAutojoinEnabled = isAutoJoinEnabled;
            this.wns.wifiConfiguration.fromWifiNetworkSuggestion = true;
            this.wns.wifiConfiguration.ephemeral = true;
            this.wns.wifiConfiguration.creatorName = perAppInfo.packageName;
            this.wns.wifiConfiguration.creatorUid = perAppInfo.uid;
            if (perAppInfo.carrierId == TelephonyManager.UNKNOWN_CARRIER_ID) {
                return;
            }
            // If App is carrier privileged, set carrier Id to the profile.
            this.wns.wifiConfiguration.carrierId = perAppInfo.carrierId;
            if (this.wns.passpointConfiguration != null) {
                this.wns.passpointConfiguration.setCarrierId(perAppInfo.carrierId);
            }
        }

        @Override
        public int hashCode() {
            return Objects.hash(wns, perAppInfo.uid, perAppInfo.packageName);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof ExtendedWifiNetworkSuggestion)) {
                return false;
            }
            ExtendedWifiNetworkSuggestion other = (ExtendedWifiNetworkSuggestion) obj;
            return wns.equals(other.wns)
                    && perAppInfo.uid == other.perAppInfo.uid
                    && TextUtils.equals(perAppInfo.packageName, other.perAppInfo.packageName);
        }

        @Override
        public String toString() {
            return new StringBuilder(wns.toString())
                    .append(", isAutoJoinEnabled=").append(isAutojoinEnabled)
                    .toString();
        }

        /**
         * Convert from {@link WifiNetworkSuggestion} to a new instance of
         * {@link ExtendedWifiNetworkSuggestion}.
         */
        public static ExtendedWifiNetworkSuggestion fromWns(@NonNull WifiNetworkSuggestion wns,
                @NonNull PerAppInfo perAppInfo, boolean isAutoJoinEnabled) {
            return new ExtendedWifiNetworkSuggestion(wns, perAppInfo, isAutoJoinEnabled);
        }

        /**
         * Create a {@link WifiConfiguration} from suggestion for framework internal use.
         */
        public WifiConfiguration createInternalWifiConfiguration(
                @Nullable WifiCarrierInfoManager carrierInfoManager) {
            WifiConfiguration config = new WifiConfiguration(wns.getWifiConfiguration());
            config.shared = false;
            config.allowAutojoin = isAutojoinEnabled;
            if (config.enterpriseConfig
                    != null && config.enterpriseConfig.isAuthenticationSimBased()) {
                config.enterpriseConfig.setAnonymousIdentity(anonymousIdentity);
            }
            config.getNetworkSelectionStatus().setConnectChoice(connectChoice);
            config.getNetworkSelectionStatus().setConnectChoiceRssi(connectChoiceRssi);
            if (carrierInfoManager != null) {
                config.subscriptionId = carrierInfoManager.getBestMatchSubscriptionId(config);
                // shouldDisableMacRandomization checks if the SSID matches with a SSID configured
                // in CarrierConfigManger for the provided subscriptionId.
                if (carrierInfoManager.shouldDisableMacRandomization(config.SSID,
                        config.carrierId, config.subscriptionId)) {
                    Log.i(TAG, "Disabling MAC randomization on " + config.SSID
                            + " due to CarrierConfig override");
                    config.macRandomizationSetting = WifiConfiguration.RANDOMIZATION_NONE;
                }
            }
            return config;
        }
    }

    /**
     * Map of package name of an app to the set of active network suggestions provided by the app.
     */
    private final Map<String, PerAppInfo> mActiveNetworkSuggestionsPerApp = new HashMap<>();
    /**
     * Map of package name of an app to the app ops changed listener for the app.
     */
    private final Map<String, AppOpsChangedListener> mAppOpsChangedListenerPerApp = new HashMap<>();
    /**
     * Map maintained to help lookup all the network suggestions (with no bssid) that match a
     * provided scan result.
     * Note:
     * <li>There could be multiple suggestions (provided by different apps) that match a single
     * scan result.</li>
     * <li>Adding/Removing to this set for scan result lookup is expensive. But, we expect scan
     * result lookup to happen much more often than apps modifying network suggestions.</li>
     */
    private final Map<ScanResultMatchInfo, Set<ExtendedWifiNetworkSuggestion>>
            mActiveScanResultMatchInfoWithNoBssid = new HashMap<>();
    /**
     * Map maintained to help lookup all the network suggestions (with bssid) that match a provided
     * scan result.
     * Note:
     * <li>There could be multiple suggestions (provided by different apps) that match a single
     * scan result.</li>
     * <li>Adding/Removing to this set for scan result lookup is expensive. But, we expect scan
     * result lookup to happen much more often than apps modifying network suggestions.</li>
     */
    private final Map<Pair<ScanResultMatchInfo, MacAddress>, Set<ExtendedWifiNetworkSuggestion>>
            mActiveScanResultMatchInfoWithBssid = new HashMap<>();

    private final Map<String, Set<ExtendedWifiNetworkSuggestion>>
            mPasspointInfo = new HashMap<>();

    private final HashMap<String, RemoteCallbackList<ISuggestionConnectionStatusListener>>
            mSuggestionStatusListenerPerApp = new HashMap<>();

    private final HashMap<String, RemoteCallbackList<ISuggestionUserApprovalStatusListener>>
            mSuggestionUserApprovalStatusListenerPerApp = new HashMap<>();

    /**
     * Store the suggestion update listeners.
     */
    private final List<OnSuggestionUpdateListener> mListeners = new ArrayList<>();

    /**
     * Intent filter for processing notification actions.
     */
    private final IntentFilter mIntentFilter;

    /**
     * Verbose logging flag.
     */
    private boolean mVerboseLoggingEnabled = false;
    /**
     * Indicates that we have new data to serialize.
     */
    private boolean mHasNewDataToSerialize = false;
    /**
     * The {@link Clock#getElapsedSinceBootMillis()} must be at least this value for us
     * to update/show the notification.
     */
    private long mNotificationUpdateTime;

    private boolean mIsLastUserApprovalUiDialog = false;

    private boolean mUserDataLoaded = false;

    /**
     * Keep a set of packageNames which is treated as carrier provider.
     */
    private final Set<String> mCrossCarrierProvidersSet = new ArraySet<>();

    /**
     * Listener for app-ops changes for active suggestor apps.
     */
    private final class AppOpsChangedListener implements AppOpsManager.OnOpChangedListener {
        private final String mPackageName;
        private final int mUid;

        AppOpsChangedListener(@NonNull String packageName, int uid) {
            mPackageName = packageName;
            mUid = uid;
        }

        @Override
        public void onOpChanged(String op, String packageName) {
            mHandler.post(() -> {
                if (!mPackageName.equals(packageName)) return;
                if (!OPSTR_CHANGE_WIFI_STATE.equals(op)) return;

                // Ensure the uid to package mapping is still correct.
                try {
                    mAppOps.checkPackage(mUid, mPackageName);
                } catch (SecurityException e) {
                    Log.wtf(TAG, "Invalid uid/package" + packageName);
                    return;
                }

                if (mAppOps.unsafeCheckOpNoThrow(OPSTR_CHANGE_WIFI_STATE, mUid, mPackageName)
                        == AppOpsManager.MODE_IGNORED) {
                    Log.i(TAG, "User disallowed change wifi state for " + packageName);
                    // User disabled the app, remove app from database. We want the notification
                    // again if the user enabled the app-op back.
                    removeApp(mPackageName);
                    mWifiMetrics.incrementNetworkSuggestionUserRevokePermission();
                }
            });
        }
    };

    /**
     * Module to interact with the wifi config store.
     */
    private class NetworkSuggestionDataSource implements NetworkSuggestionStoreData.DataSource {
        @Override
        public Map<String, PerAppInfo> toSerialize() {
            for (Map.Entry<String, PerAppInfo> entry : mActiveNetworkSuggestionsPerApp.entrySet()) {
                for (ExtendedWifiNetworkSuggestion ewns : entry.getValue().extNetworkSuggestions
                        .values()) {
                    if (ewns.wns.passpointConfiguration != null) {
                        continue;
                    }
                    ewns.wns.wifiConfiguration.isMostRecentlyConnected = mLruConnectionTracker
                            .isMostRecentlyConnected(ewns.createInternalWifiConfiguration(
                                    mWifiCarrierInfoManager));
                }
            }
            // Clear the flag after writing to disk.
            // TODO(b/115504887): Don't reset the flag on write failure.
            mHasNewDataToSerialize = false;
            return mActiveNetworkSuggestionsPerApp;
        }

        @Override
        public void fromDeserialized(Map<String, PerAppInfo> networkSuggestionsMap) {
            mActiveNetworkSuggestionsPerApp.clear();
            mActiveNetworkSuggestionsPerApp.putAll(networkSuggestionsMap);
            // Build the scan cache.
            for (Map.Entry<String, PerAppInfo> entry : networkSuggestionsMap.entrySet()) {
                String packageName = entry.getKey();
                Collection<ExtendedWifiNetworkSuggestion> extNetworkSuggestions =
                        entry.getValue().extNetworkSuggestions.values();
                // Start tracking app-op changes from for all the app in the database
                startTrackingAppOpsChange(packageName, entry.getValue().uid);
                for (ExtendedWifiNetworkSuggestion ewns : extNetworkSuggestions) {
                    if (ewns.wns.passpointConfiguration != null) {
                        addToPasspointInfoMap(ewns);
                    } else {
                        if (ewns.wns.wifiConfiguration.isMostRecentlyConnected) {
                            mLruConnectionTracker
                                    .addNetwork(ewns.createInternalWifiConfiguration(
                                            mWifiCarrierInfoManager));
                        }
                        addToScanResultMatchInfoMap(ewns);
                    }
                }
            }
            mUserDataLoaded = true;
        }

        @Override
        public void reset() {
            mUserDataLoaded = false;
            mActiveNetworkSuggestionsPerApp.clear();
            mActiveScanResultMatchInfoWithBssid.clear();
            mActiveScanResultMatchInfoWithNoBssid.clear();
            mPasspointInfo.clear();
        }

        @Override
        public boolean hasNewDataToSerialize() {
            return mHasNewDataToSerialize;
        }
    }

    private void handleUserAllowAction(int uid, String packageName) {
        Log.i(TAG, "User clicked to allow app");
        // Set the user approved flag.
        setHasUserApprovedForApp(true, uid, packageName);
        mNotificationUpdateTime = 0;
        mWifiMetrics.addUserApprovalSuggestionAppUiReaction(
                ACTION_USER_ALLOWED_APP,
                mIsLastUserApprovalUiDialog);
    }

    private void handleUserDisallowAction(int uid, String packageName) {
        Log.i(TAG, "User clicked to disallow app");
        // Take away CHANGE_WIFI_STATE app-ops from the app.
        mAppOps.setMode(AppOpsManager.OPSTR_CHANGE_WIFI_STATE, uid, packageName,
                MODE_IGNORED);
        // Set the user approved flag.
        setHasUserApprovedForApp(false, uid, packageName);
        mNotificationUpdateTime = 0;
        mWifiMetrics.addUserApprovalSuggestionAppUiReaction(
                ACTION_USER_DISALLOWED_APP,
                mIsLastUserApprovalUiDialog);
    }

    private void handleUserDismissAction() {
        Log.i(TAG, "User dismissed the notification");
        mNotificationUpdateTime = 0;
        mWifiMetrics.addUserApprovalSuggestionAppUiReaction(
                ACTION_USER_DISMISS,
                mIsLastUserApprovalUiDialog);
    }

    private final BroadcastReceiver mBroadcastReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME);
                    int uid = intent.getIntExtra(EXTRA_UID, -1);
                    if (packageName == null || uid == -1) {
                        Log.e(TAG, "No package name or uid found in intent");
                        return;
                    }
                    switch (intent.getAction()) {
                        case NOTIFICATION_USER_ALLOWED_APP_INTENT_ACTION:
                            handleUserAllowAction(uid, packageName);
                            break;
                        case NOTIFICATION_USER_DISALLOWED_APP_INTENT_ACTION:
                            handleUserDisallowAction(uid, packageName);
                            break;
                        case NOTIFICATION_USER_DISMISSED_INTENT_ACTION:
                            handleUserDismissAction();
                            return; // no need to cancel a dismissed notification, return.
                        default:
                            Log.e(TAG, "Unknown action " + intent.getAction());
                            return;
                    }
                    // Clear notification once the user interacts with it.
                    mNotificationManager.cancel(SystemMessage.NOTE_NETWORK_SUGGESTION_AVAILABLE);
                }
            };

    /**
     * Interface for other modules to listen to the suggestion updated events.
     */
    public interface OnSuggestionUpdateListener {
        /**
         * Invoked on suggestion being added or updated.
         */
        void onSuggestionsAddedOrUpdated(@NonNull List<WifiNetworkSuggestion> addedSuggestions);
        /**
         * Invoked on suggestion being removed.
         */
        void onSuggestionsRemoved(@NonNull List<WifiNetworkSuggestion> removedSuggestions);
    }

    private final class ImsiProtectedOrUserApprovedListener implements
            WifiCarrierInfoManager.OnImsiProtectedOrUserApprovedListener {

        @Override
        public void onImsiProtectedOrUserApprovalChanged(int carrierId, boolean allowAutoJoin) {
            restoreInitialAutojoinForCarrierId(carrierId, allowAutoJoin);
        }
    }

    public WifiNetworkSuggestionsManager(WifiContext context, RunnerHandler handler,
            WifiInjector wifiInjector, WifiPermissionsUtil wifiPermissionsUtil,
            WifiConfigManager wifiConfigManager, WifiConfigStore wifiConfigStore,
            WifiMetrics wifiMetrics, WifiCarrierInfoManager wifiCarrierInfoManager,
            WifiKeyStore keyStore, LruConnectionTracker lruConnectionTracker,
            Clock clock) {
        mContext = context;
        mResources = context.getResources();
        mHandler = handler;
        mAppOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        mActivityManager = context.getSystemService(ActivityManager.class);
        mWifiInjector = wifiInjector;
        mFrameworkFacade = mWifiInjector.getFrameworkFacade();
        mWifiPermissionsUtil = wifiPermissionsUtil;
        mWifiConfigManager = wifiConfigManager;
        mWifiMetrics = wifiMetrics;
        mWifiCarrierInfoManager = wifiCarrierInfoManager;
        mWifiKeyStore = keyStore;
        mNotificationManager = mWifiInjector.getWifiNotificationManager();
        mClock = clock;

        // register the data store for serializing/deserializing data.
        wifiConfigStore.registerStoreData(
                wifiInjector.makeNetworkSuggestionStoreData(new NetworkSuggestionDataSource()));

        mWifiCarrierInfoManager.addImsiProtectedOrUserApprovedListener(
                new ImsiProtectedOrUserApprovedListener());

        // Register broadcast receiver for UI interactions.
        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(NOTIFICATION_USER_ALLOWED_APP_INTENT_ACTION);
        mIntentFilter.addAction(NOTIFICATION_USER_DISALLOWED_APP_INTENT_ACTION);
        mIntentFilter.addAction(NOTIFICATION_USER_DISMISSED_INTENT_ACTION);

        mContext.registerReceiver(mBroadcastReceiver, mIntentFilter, null, handler);
        mLruConnectionTracker = lruConnectionTracker;
        mHandler.postToFront(() -> mWifiConfigManager.addOnNetworkUpdateListener(
                new WifiNetworkSuggestionsManager.OnNetworkUpdateListener()));
    }

    /**
     * Enable verbose logging.
     */
    public void enableVerboseLogging(boolean verboseEnabled) {
        mVerboseLoggingEnabled = verboseEnabled;
    }

    private void saveToStore() {
        // Set the flag to let WifiConfigStore that we have new data to write.
        mHasNewDataToSerialize = true;
        if (!mWifiConfigManager.saveToStore(true)) {
            Log.w(TAG, "Failed to save to store");
        }
    }

    private void addToScanResultMatchInfoMap(
            @NonNull ExtendedWifiNetworkSuggestion extNetworkSuggestion) {
        ScanResultMatchInfo scanResultMatchInfo =
                ScanResultMatchInfo.fromWifiConfiguration(
                        extNetworkSuggestion.wns.wifiConfiguration);
        Set<ExtendedWifiNetworkSuggestion> extNetworkSuggestionsForScanResultMatchInfo;
        if (!TextUtils.isEmpty(extNetworkSuggestion.wns.wifiConfiguration.BSSID)) {
            Pair<ScanResultMatchInfo, MacAddress> lookupPair =
                    Pair.create(scanResultMatchInfo,
                            MacAddress.fromString(
                                    extNetworkSuggestion.wns.wifiConfiguration.BSSID));
            extNetworkSuggestionsForScanResultMatchInfo =
                    mActiveScanResultMatchInfoWithBssid.get(lookupPair);
            if (extNetworkSuggestionsForScanResultMatchInfo == null) {
                extNetworkSuggestionsForScanResultMatchInfo = new HashSet<>();
                mActiveScanResultMatchInfoWithBssid.put(
                        lookupPair, extNetworkSuggestionsForScanResultMatchInfo);
            }
        } else {
            extNetworkSuggestionsForScanResultMatchInfo =
                    mActiveScanResultMatchInfoWithNoBssid.get(scanResultMatchInfo);
            if (extNetworkSuggestionsForScanResultMatchInfo == null) {
                extNetworkSuggestionsForScanResultMatchInfo = new HashSet<>();
                mActiveScanResultMatchInfoWithNoBssid.put(
                        scanResultMatchInfo, extNetworkSuggestionsForScanResultMatchInfo);
            }
        }
        extNetworkSuggestionsForScanResultMatchInfo.remove(extNetworkSuggestion);
        extNetworkSuggestionsForScanResultMatchInfo.add(extNetworkSuggestion);
    }

    private void removeFromScanResultMatchInfoMapAndRemoveRelatedScoreCard(
            @NonNull ExtendedWifiNetworkSuggestion extNetworkSuggestion, boolean removeScoreCard) {
        ScanResultMatchInfo scanResultMatchInfo =
                ScanResultMatchInfo.fromWifiConfiguration(
                        extNetworkSuggestion.wns.wifiConfiguration);
        Set<ExtendedWifiNetworkSuggestion> extNetworkSuggestionsForScanResultMatchInfo;
        if (!TextUtils.isEmpty(extNetworkSuggestion.wns.wifiConfiguration.BSSID)) {
            Pair<ScanResultMatchInfo, MacAddress> lookupPair =
                    Pair.create(scanResultMatchInfo,
                            MacAddress.fromString(
                                    extNetworkSuggestion.wns.wifiConfiguration.BSSID));
            extNetworkSuggestionsForScanResultMatchInfo =
                    mActiveScanResultMatchInfoWithBssid.get(lookupPair);
            // This should never happen because we should have done necessary error checks in
            // the parent method.
            if (extNetworkSuggestionsForScanResultMatchInfo == null) {
                Log.wtf(TAG, "No scan result match info found.");
                return;
            }
            extNetworkSuggestionsForScanResultMatchInfo.remove(extNetworkSuggestion);
            // Remove the set from map if empty.
            if (extNetworkSuggestionsForScanResultMatchInfo.isEmpty()) {
                mActiveScanResultMatchInfoWithBssid.remove(lookupPair);
                if (!mActiveScanResultMatchInfoWithNoBssid.containsKey(scanResultMatchInfo)) {
                    if (removeScoreCard) {
                        removeNetworkFromScoreCard(extNetworkSuggestion.wns.wifiConfiguration);
                    }
                    mLruConnectionTracker.removeNetwork(
                            extNetworkSuggestion.wns.wifiConfiguration);
                }
            }
        } else {
            extNetworkSuggestionsForScanResultMatchInfo =
                    mActiveScanResultMatchInfoWithNoBssid.get(scanResultMatchInfo);
            // This should never happen because we should have done necessary error checks in
            // the parent method.
            if (extNetworkSuggestionsForScanResultMatchInfo == null) {
                Log.wtf(TAG, "No scan result match info found.");
                return;
            }
            extNetworkSuggestionsForScanResultMatchInfo.remove(extNetworkSuggestion);
            // Remove the set from map if empty.
            if (extNetworkSuggestionsForScanResultMatchInfo.isEmpty()) {
                mActiveScanResultMatchInfoWithNoBssid.remove(scanResultMatchInfo);
                if (removeScoreCard) {
                    removeNetworkFromScoreCard(extNetworkSuggestion.wns.wifiConfiguration);
                }
                mLruConnectionTracker.removeNetwork(
                        extNetworkSuggestion.wns.wifiConfiguration);
            }
        }
    }

    private void removeNetworkFromScoreCard(WifiConfiguration wifiConfiguration) {
        WifiConfiguration existing =
                mWifiConfigManager.getConfiguredNetwork(wifiConfiguration.getProfileKey());
        // If there is a saved network, do not remove from the score card.
        if (existing != null && !existing.fromWifiNetworkSuggestion) {
            return;
        }
        mWifiInjector.getWifiScoreCard().removeNetwork(wifiConfiguration.SSID);
    }

    private void addToPasspointInfoMap(ExtendedWifiNetworkSuggestion ewns) {
        Set<ExtendedWifiNetworkSuggestion> extendedWifiNetworkSuggestions =
                mPasspointInfo.get(ewns.wns.wifiConfiguration.FQDN);
        if (extendedWifiNetworkSuggestions == null) {
            extendedWifiNetworkSuggestions = new HashSet<>();
        }
        extendedWifiNetworkSuggestions.remove(ewns);
        extendedWifiNetworkSuggestions.add(ewns);
        mPasspointInfo.put(ewns.wns.wifiConfiguration.FQDN, extendedWifiNetworkSuggestions);
    }

    private void removeFromPassPointInfoMap(ExtendedWifiNetworkSuggestion ewns) {
        Set<ExtendedWifiNetworkSuggestion> extendedWifiNetworkSuggestions =
                mPasspointInfo.get(ewns.wns.wifiConfiguration.FQDN);
        if (extendedWifiNetworkSuggestions == null
                || !extendedWifiNetworkSuggestions.contains(ewns)) {
            Log.wtf(TAG, "No Passpoint info found.");
            return;
        }
        extendedWifiNetworkSuggestions.remove(ewns);
        if (extendedWifiNetworkSuggestions.isEmpty()) {
            mPasspointInfo.remove(ewns.wns.wifiConfiguration.FQDN);
        }
    }

    private void startTrackingAppOpsChange(@NonNull String packageName, int uid) {
        AppOpsChangedListener appOpsChangedListener =
                new AppOpsChangedListener(packageName, uid);
        mAppOps.startWatchingMode(OPSTR_CHANGE_WIFI_STATE, packageName, appOpsChangedListener);
        mAppOpsChangedListenerPerApp.put(packageName, appOpsChangedListener);
    }

    /**
     * Helper method to convert the incoming collection of public {@link WifiNetworkSuggestion}
     * objects to a set of corresponding internal wrapper
     * {@link ExtendedWifiNetworkSuggestion} objects.
     */
    private Set<ExtendedWifiNetworkSuggestion> convertToExtendedWnsSet(
            final Collection<WifiNetworkSuggestion> networkSuggestions,
            final PerAppInfo perAppInfo) {
        return networkSuggestions
                .stream()
                .map(n -> ExtendedWifiNetworkSuggestion.fromWns(n, perAppInfo,
                        n.isInitialAutoJoinEnabled))
                .collect(Collectors.toSet());
    }

    /**
     * Helper method to convert the incoming collection of internal wrapper
     * {@link ExtendedWifiNetworkSuggestion} objects to a set of corresponding public
     * {@link WifiNetworkSuggestion} objects.
     */
    private Set<WifiNetworkSuggestion> convertToWnsSet(
            final Collection<ExtendedWifiNetworkSuggestion> extNetworkSuggestions) {
        return extNetworkSuggestions
                .stream()
                .map(n -> n.wns)
                .collect(Collectors.toSet());
    }

    private void updateWifiConfigInWcmIfPresent(
            WifiConfiguration newConfig, int uid, String packageName) {
        WifiConfiguration configInWcm =
                mWifiConfigManager.getConfiguredNetwork(newConfig.getProfileKey());
        if (configInWcm == null) return;
        // !suggestion
        if (!configInWcm.fromWifiNetworkSuggestion) return;
        // is suggestion from same app.
        if (configInWcm.creatorUid != uid
                || !TextUtils.equals(configInWcm.creatorName, packageName)) {
            return;
        }
        NetworkUpdateResult result = mWifiConfigManager.addOrUpdateNetwork(
                newConfig, uid, packageName, false);
        if (!result.isSuccess()) {
            Log.e(TAG, "Failed to update config in WifiConfigManager");
            return;
        }
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "Updated config in WifiConfigManager");
        }
    }

    /**
     * Add the provided list of network suggestions from the corresponding app's active list.
     */
    public @WifiManager.NetworkSuggestionsStatusCode int add(
            List<WifiNetworkSuggestion> networkSuggestions, int uid, String packageName,
            @Nullable String featureId) {
        if (!mWifiPermissionsUtil.doesUidBelongToCurrentUserOrDeviceOwner(uid)) {
            Log.e(TAG, "UID " + uid + " not visible to the current user");
            return WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_INTERNAL;
        }
        if (!mUserDataLoaded) {
            Log.e(TAG, "Add Network suggestion before boot complete is not allowed.");
            return WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_INTERNAL;
        }
        if (networkSuggestions == null || networkSuggestions.isEmpty()) {
            Log.w(TAG, "Empty list of network suggestions for " + packageName + ". Ignoring");
            return WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS;
        }
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "Adding " + networkSuggestions.size() + " networks from " + packageName);
        }
        if (!validateNetworkSuggestions(networkSuggestions, packageName, uid)) {
            Log.e(TAG, "Invalid suggestion add from app: " + packageName);
            return WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_INVALID;
        }
        int carrierId = mWifiCarrierInfoManager
                .getCarrierIdForPackageWithCarrierPrivileges(packageName);
        if (!validateCarrierNetworkSuggestions(networkSuggestions, uid, packageName, carrierId)) {
            Log.e(TAG, "bad wifi suggestion from app: " + packageName);
            return WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_NOT_ALLOWED;
        }
        for (WifiNetworkSuggestion wns : networkSuggestions) {
            wns.wifiConfiguration.convertLegacyFieldsToSecurityParamsIfNeeded();
            if (!WifiConfigurationUtil.addUpgradableSecurityTypeIfNecessary(
                    wns.wifiConfiguration)) {
                Log.e(TAG, "Invalid suggestion add from app: " + packageName);
                return WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_INVALID;
            }
        }

        PerAppInfo perAppInfo = mActiveNetworkSuggestionsPerApp.get(packageName);
        if (perAppInfo == null) {
            perAppInfo = new PerAppInfo(uid, packageName, featureId);
            mActiveNetworkSuggestionsPerApp.put(packageName, perAppInfo);
            if (mWifiPermissionsUtil.checkNetworkCarrierProvisioningPermission(uid)) {
                Log.i(TAG, "Setting the carrier provisioning app approved");
                perAppInfo.hasUserApproved = true;
                mWifiMetrics.incrementNetworkSuggestionApiUsageNumOfAppInType(
                        APP_TYPE_NETWORK_PROVISIONING);
            } else if (mWifiPermissionsUtil.checkNetworkSettingsPermission(uid)
                        || isAppWorkingAsCrossCarrierProvider(packageName)) {
                // Bypass added for tests & shell commands.
                Log.i(TAG, "Setting the test app approved");
                perAppInfo.hasUserApproved = true;
            } else if (carrierId != TelephonyManager.UNKNOWN_CARRIER_ID) {
                Log.i(TAG, "Setting the carrier privileged app approved");
                perAppInfo.setCarrierId(carrierId);
                mWifiMetrics.incrementNetworkSuggestionApiUsageNumOfAppInType(
                        APP_TYPE_CARRIER_PRIVILEGED);
            } else {
                if (isSuggestionFromForegroundApp(packageName)) {
                    sendUserApprovalDialog(packageName, uid);
                } else {
                    sendUserApprovalNotificationIfNotApproved(packageName, uid);
                }
                mWifiMetrics.incrementNetworkSuggestionApiUsageNumOfAppInType(
                        APP_TYPE_NON_PRIVILEGED);
            }
            onSuggestionUserApprovalStatusChanged(uid, packageName);
            startTrackingAppOpsChange(packageName, uid);
        }
        // If PerAppInfo is upgrade from pre-R, uid may not be set.
        perAppInfo.setUid(uid);
        // If App became carrier privileged, set the carrier Id.
        perAppInfo.setCarrierId(carrierId);
        Set<ExtendedWifiNetworkSuggestion> extNetworkSuggestions =
                convertToExtendedWnsSet(networkSuggestions, perAppInfo);
        boolean isLowRamDevice = mActivityManager.isLowRamDevice();
        int networkSuggestionsMaxPerApp =
                WifiManager.getMaxNumberOfNetworkSuggestionsPerApp(isLowRamDevice);
        if (perAppInfo.extNetworkSuggestions.size() + extNetworkSuggestions.size()
                > networkSuggestionsMaxPerApp) {
            Set<Integer> keySet = extNetworkSuggestions
                    .stream()
                    .map(ExtendedWifiNetworkSuggestion::hashCode)
                    .collect(Collectors.toSet());
            Set<Integer> savedKeySet = new HashSet<>(perAppInfo.extNetworkSuggestions.keySet());
            savedKeySet.addAll(keySet);
            if (savedKeySet.size() > networkSuggestionsMaxPerApp) {
                Log.e(TAG, "Failed to add network suggestions for " + packageName
                        + ". Exceeds max per app, current list size: "
                        + perAppInfo.extNetworkSuggestions.size()
                        + ", new list size: "
                        + extNetworkSuggestions.size());
                return WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_ADD_EXCEEDS_MAX_PER_APP;
            }
        }

        for (ExtendedWifiNetworkSuggestion ewns: extNetworkSuggestions) {
            ExtendedWifiNetworkSuggestion oldEwns = perAppInfo.extNetworkSuggestions
                    .get(ewns.hashCode());
            // Keep the user connect choice and AnonymousIdentity
            if (oldEwns != null) {
                ewns.connectChoice = oldEwns.connectChoice;
                ewns.connectChoiceRssi = oldEwns.connectChoiceRssi;
                ewns.anonymousIdentity = oldEwns.anonymousIdentity;
                // If user change the auto-join, keep the user choice.
                if (oldEwns.isAutojoinEnabled != oldEwns.wns.isInitialAutoJoinEnabled) {
                    ewns.isAutojoinEnabled = oldEwns.isAutojoinEnabled;
                }
            }
            // If network has no IMSI protection and user didn't approve exemption, make it initial
            // auto join disabled
            if (isSimBasedPhase1Suggestion(ewns)) {
                int carrierIdFromSuggestion = getCarrierIdFromSuggestion(ewns);
                int subId = ewns.wns.wifiConfiguration.subscriptionId;
                if (subId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                    if (ewns.wns.wifiConfiguration.getSubscriptionGroup() != null) {
                        subId = mWifiCarrierInfoManager.getActiveSubscriptionIdInGroup(
                                ewns.wns.wifiConfiguration.getSubscriptionGroup());
                    } else {
                        subId = mWifiCarrierInfoManager.getMatchingSubId(carrierIdFromSuggestion);
                    }
                }
                if (!(mWifiCarrierInfoManager.requiresImsiEncryption(subId)
                        || mWifiCarrierInfoManager.hasUserApprovedImsiPrivacyExemptionForCarrier(
                                carrierIdFromSuggestion)
                        || mWifiCarrierInfoManager.isOobPseudonymFeatureEnabled(
                                carrierIdFromSuggestion))) {
                    ewns.isAutojoinEnabled = false;
                }
            }
            mWifiMetrics.addNetworkSuggestionPriorityGroup(ewns.wns.priorityGroup);
            if (ewns.wns.passpointConfiguration == null) {
                if (ewns.wns.wifiConfiguration.isEnterprise()) {
                    if (!mWifiKeyStore.updateNetworkKeys(ewns.wns.wifiConfiguration, null)) {
                        Log.e(TAG, "Enterprise network install failure for SSID: "
                                + ewns.wns.wifiConfiguration.SSID);
                        continue;
                    }
                }
                // If we have a config in WifiConfigManager for this suggestion, update
                // WifiConfigManager with the latest WifiConfig.
                // Note: Similar logic is present in PasspointManager for passpoint networks.
                updateWifiConfigInWcmIfPresent(ewns.createInternalWifiConfiguration(
                        mWifiCarrierInfoManager), uid, packageName);
                addToScanResultMatchInfoMap(ewns);
            } else {
                ewns.wns.passpointConfiguration.setAutojoinEnabled(ewns.isAutojoinEnabled);
                // Install Passpoint config, if failure, ignore that suggestion
                if (!mWifiInjector.getPasspointManager().addOrUpdateProvider(
                        ewns.wns.passpointConfiguration, uid,
                        packageName, true, !ewns.wns.isUntrusted(),
                        ewns.wns.isRestricted())) {
                    Log.e(TAG, "Passpoint profile install failure for FQDN: "
                            + ewns.wns.wifiConfiguration.FQDN);
                    continue;
                }
                addToPasspointInfoMap(ewns);
            }
            perAppInfo.extNetworkSuggestions.remove(ewns.hashCode());
            perAppInfo.extNetworkSuggestions.put(ewns.hashCode(), ewns);
        }
        for (OnSuggestionUpdateListener listener : mListeners) {
            listener.onSuggestionsAddedOrUpdated(networkSuggestions);
        }
        // Update the max size for this app.
        perAppInfo.maxSize = Math.max(perAppInfo.extNetworkSuggestions.size(), perAppInfo.maxSize);
        try {
            saveToStore();
        } catch (OutOfMemoryError e) {
            Optional<PerAppInfo> appInfo = mActiveNetworkSuggestionsPerApp.values()
                    .stream()
                    .max(Comparator.comparingInt(a -> a.extNetworkSuggestions.size()));
            if (appInfo.isPresent()) {
                EventLog.writeEvent(0x534e4554, "245299920", appInfo.get().uid,
                        "Trying to add large number of suggestion, num="
                                + appInfo.get().extNetworkSuggestions.size());
            } else {
                Log.e(TAG, "serialize out of memory but no app has suggestion!");
            }
            // Remove the most recently added suggestions, which should cause the failure.
            remove(networkSuggestions, uid, packageName, ACTION_REMOVE_SUGGESTION_DISCONNECT);
            return WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_INTERNAL;
        }
        mWifiMetrics.incrementNetworkSuggestionApiNumModification();
        mWifiMetrics.noteNetworkSuggestionApiListSizeHistogram(getAllMaxSizes());
        return WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS;
    }

    private int getCarrierIdFromSuggestion(ExtendedWifiNetworkSuggestion ewns) {
        if (ewns.wns.passpointConfiguration == null) {
            return ewns.wns.wifiConfiguration.carrierId;
        }
        return ewns.wns.passpointConfiguration.getCarrierId();
    }

    private boolean isSimBasedPhase1Suggestion(ExtendedWifiNetworkSuggestion ewns) {
        if (ewns.wns.passpointConfiguration == null) {
            return ewns.wns.wifiConfiguration.enterpriseConfig != null
                    && ewns.wns.wifiConfiguration.enterpriseConfig.isAuthenticationSimBased()
                    && !ewns.wns.wifiConfiguration.enterpriseConfig.isEapMethodServerCertUsed();
        } else {
            return ewns.wns.passpointConfiguration.getCredential().getSimCredential() != null;
        }
    }

    private boolean checkNetworkSuggestionsNoNulls(List<WifiNetworkSuggestion> networkSuggestions) {
        for (WifiNetworkSuggestion wns : networkSuggestions) {
            if (wns == null || wns.wifiConfiguration == null) {
                return false;
            }
        }
        return true;
    }

    private boolean validateNetworkSuggestions(
            List<WifiNetworkSuggestion> networkSuggestions, String packageName, int uid) {
        if (!checkNetworkSuggestionsNoNulls(networkSuggestions)) {
            return false;
        }

        long supportedFeatures = mWifiInjector.getActiveModeWarden()
                .getPrimaryClientModeManager().getSupportedFeatures();

        for (WifiNetworkSuggestion wns : networkSuggestions) {
            if (wns.passpointConfiguration == null) {
                WifiConfiguration config = wns.wifiConfiguration;
                if (!WifiConfigurationUtil.validate(config, supportedFeatures,
                        WifiConfigurationUtil.VALIDATE_FOR_ADD)) {
                    return false;
                }
                if (config.macRandomizationSetting != WifiConfiguration.RANDOMIZATION_PERSISTENT
                        && config.macRandomizationSetting
                        != WifiConfiguration.RANDOMIZATION_NON_PERSISTENT) {
                    Log.w(TAG, "MAC randomization setting is invalid. Automatically setting"
                            + " config to use persistent random MAC address.");
                    config.macRandomizationSetting = WifiConfiguration.RANDOMIZATION_PERSISTENT;
                }
                if (config.isEnterprise()) {
                    final WifiEnterpriseConfig enterpriseConfig = config.enterpriseConfig;
                    if (enterpriseConfig.isEapMethodServerCertUsed()
                            && !enterpriseConfig.isMandatoryParameterSetForServerCertValidation()) {
                        Log.e(TAG, "Insecure enterprise suggestion is invalid.");
                        return false;
                    }
                    final String alias = enterpriseConfig.getClientKeyPairAliasInternal();
                    if (alias != null && !mWifiKeyStore.validateKeyChainAlias(alias, uid)) {
                        Log.e(TAG, "Invalid client key pair KeyChain alias: " + alias);
                        return false;
                    }
                }

            } else {
                if (!wns.passpointConfiguration.validate()) {
                    EventLog.writeEvent(0x534e4554, "245299920", uid,
                            "Trying to add invalid passpoint suggestion");
                    return false;
                }
                if (!wns.passpointConfiguration.isMacRandomizationEnabled()) {
                    Log.w(TAG, "MAC randomization must be enabled on Passpoint suggestion."
                            + " Defaulting to use persistent MAC randomization for invalid"
                            + " configuration.");
                    wns.passpointConfiguration.setMacRandomizationEnabled(true);
                    wns.passpointConfiguration.setNonPersistentMacRandomizationEnabled(false);
                }
            }
            if (!isAppWorkingAsCrossCarrierProvider(packageName)
                    && !isValidCarrierMergedNetworkSuggestion(wns)) {
                Log.e(TAG, "Merged carrier network must be a metered, enterprise config with a "
                        + "valid subscription Id");
                return false;
            }
            if (!SdkLevel.isAtLeastS()) {
                if (wns.wifiConfiguration.oemPaid) {
                    Log.e(TAG, "OEM paid suggestions are only allowed from Android S.");
                    return false;
                }
                if (wns.wifiConfiguration.oemPrivate) {
                    Log.e(TAG, "OEM private suggestions are only allowed from Android S.");
                    return false;
                }
                if (wns.wifiConfiguration.subscriptionId
                        != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                    Log.e(TAG, "Setting Subscription Id is only allowed from Android S.");
                    return false;
                }
                if (wns.priorityGroup != 0) {
                    Log.e(TAG, "Setting Priority group is only allowed from Android S.");
                    return false;
                }
                if (wns.wifiConfiguration.carrierMerged) {
                    Log.e(TAG, "Setting carrier merged network is only allowed from Android S.");
                    return false;
                }
            }
            if (!SdkLevel.isAtLeastT()) {
                if (wns.wifiConfiguration.getSubscriptionGroup() != null) {
                    Log.e(TAG, "Setting subscription group is only allowed from Android T.");
                    return false;
                }
            }
            if (wns.wifiConfiguration.getSubscriptionGroup() != null
                    && wns.wifiConfiguration.subscriptionId
                    != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                Log.e(TAG, "Setting both subscription group and subscription id are not "
                        + "allowed.");
                return false;
            }
        }
        return true;
    }

    private boolean isValidCarrierMergedNetworkSuggestion(WifiNetworkSuggestion wns) {
        if (!wns.wifiConfiguration.carrierMerged) {
            // Not carrier merged.
            return true;
        }
        if (!wns.wifiConfiguration.isEnterprise() && wns.passpointConfiguration == null) {
            // Carrier merged network must be a enterprise network.
            return false;
        }
        if (!WifiConfiguration.isMetered(wns.wifiConfiguration, null)) {
            // Carrier merged network must be metered.
            return false;
        }
        if (wns.wifiConfiguration.subscriptionId == SubscriptionManager.INVALID_SUBSCRIPTION_ID
                && wns.wifiConfiguration.getSubscriptionGroup() == null) {
            // Carrier merged network must have a valid subscription Id.
            return false;
        }
        return true;
    }

    private boolean validateCarrierNetworkSuggestions(
            List<WifiNetworkSuggestion> networkSuggestions, int uid, String packageName,
            int provisionerCarrierId) {
        boolean isAppWorkingAsCrossCarrierProvider = isAppWorkingAsCrossCarrierProvider(
                packageName);
        boolean isCrossCarrierProvisioner =
                mWifiPermissionsUtil.checkNetworkCarrierProvisioningPermission(uid)
                        || isAppWorkingAsCrossCarrierProvider;

        for (WifiNetworkSuggestion suggestion : networkSuggestions) {
            WifiConfiguration wifiConfiguration = suggestion.wifiConfiguration;
            PasspointConfiguration passpointConfiguration = suggestion.passpointConfiguration;
            if (wifiConfiguration.carrierMerged && !areCarrierMergedSuggestionsAllowed(
                    wifiConfiguration, packageName)) {
                // Carrier must be explicitly configured as merged carrier offload enabled
                return false;
            }
            if (!isCrossCarrierProvisioner && provisionerCarrierId
                    ==  TelephonyManager.UNKNOWN_CARRIER_ID) {
                // If an app doesn't have carrier privileges or carrier provisioning permission,
                // suggests SIM-based network, sets CarrierId and sets SubscriptionId are illegal.
                if (wifiConfiguration.carrierId != TelephonyManager.UNKNOWN_CARRIER_ID) {
                    return false;
                }
                if (wifiConfiguration.subscriptionId
                        != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                    return false;
                }
                if (wifiConfiguration.getSubscriptionGroup() != null) {
                    return false;
                }
                if (passpointConfiguration == null) {
                    if (wifiConfiguration.enterpriseConfig != null
                            && wifiConfiguration.enterpriseConfig.isAuthenticationSimBased()) {
                        return false;
                    }
                } else {
                    if (passpointConfiguration.getCredential() != null
                            && passpointConfiguration.getCredential().getSimCredential() != null) {
                        return false;
                    }
                }
            } else {
                int carrierId = isCrossCarrierProvisioner ? wifiConfiguration.carrierId
                        : provisionerCarrierId;
                int subId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
                if (wifiConfiguration.getSubscriptionGroup() != null) {
                    subId = mWifiCarrierInfoManager.getActiveSubscriptionIdInGroup(
                            wifiConfiguration.getSubscriptionGroup());
                } else {
                    subId = wifiConfiguration.subscriptionId;
                }
                if (!mWifiCarrierInfoManager
                        .isSubIdMatchingCarrierId(subId, carrierId)) {
                    Log.e(TAG, "Subscription ID doesn't match the carrier. CarrierId:"
                            + carrierId + ", subscriptionId:" + subId + ", NetworkSuggestion:"
                            + suggestion);
                    return false;
                }
            }
        }
        return true;
    }

    private void stopTrackingAppOpsChange(@NonNull String packageName) {
        AppOpsChangedListener appOpsChangedListener =
                mAppOpsChangedListenerPerApp.remove(packageName);
        if (appOpsChangedListener == null) {
            Log.wtf(TAG, "No app ops listener found for " + packageName);
            return;
        }
        mAppOps.stopWatchingMode(appOpsChangedListener);
    }

    /**
     * Remove provided list from that App active list. If provided list is empty, will remove all.
     * Will disconnect network if current connected network is in the remove list.
     */
    private void removeInternal(
            @NonNull Collection<ExtendedWifiNetworkSuggestion> extNetworkSuggestions,
            @NonNull String packageName,
            @NonNull PerAppInfo perAppInfo, @WifiManager.ActionAfterRemovingSuggestion int action) {
        // Get internal suggestions
        Set<ExtendedWifiNetworkSuggestion> removingExtSuggestions =
                new HashSet<>(perAppInfo.extNetworkSuggestions.values());
        if (!extNetworkSuggestions.isEmpty()) {
            // Keep the internal suggestions need to remove.
            removingExtSuggestions.retainAll(extNetworkSuggestions);
            perAppInfo.extNetworkSuggestions.values().removeAll(extNetworkSuggestions);
        } else {
            // empty list is used to clear everything for the app. Store a copy for use below.
            perAppInfo.extNetworkSuggestions.clear();
        }
        if (perAppInfo.extNetworkSuggestions.isEmpty()) {
            // Note: We don't remove the app entry even if there is no active suggestions because
            // we want to keep the notification state for all apps that have ever provided
            // suggestions.
            if (mVerboseLoggingEnabled) Log.v(TAG, "No active suggestions for " + packageName);
        }
        // Clear the cache.
        WifiConfiguration connected = mWifiInjector.getActiveModeWarden()
                .getPrimaryClientModeManager().getConnectedWifiConfiguration();
        List<WifiNetworkSuggestion> removingSuggestions = new ArrayList<>();
        for (ExtendedWifiNetworkSuggestion ewns : removingExtSuggestions) {
            removeNetworkSuggestionCache(ewns);
            removingSuggestions.add(ewns.wns);
            WifiConfiguration removing = ewns
                    .createInternalWifiConfiguration(mWifiCarrierInfoManager);
            WifiConfiguration cached = mWifiConfigManager.getConfiguredNetwork(
                    removing.getProfileKey());
            if (connected != null && cached != null && cached.networkId == connected.networkId
                    && action == ACTION_REMOVE_SUGGESTION_LINGER) {
                mWifiInjector.getActiveModeWarden().getPrimaryClientModeManager()
                        .setShouldReduceNetworkScore(true);
                // Execute when linger time out clean up the cache in WifiConfigManager.
                mHandler.postDelayed(() -> removeSuggestionFromWifiConfigManager(ewns),
                        getLingerDelayMs());
            } else {
                // Remove the config from WifiConfigManager. If current connected suggestion is
                // remove, would trigger a disconnect.
                mWifiConfigManager.removeSuggestionConfiguredNetwork(removing);
            }
        }
        for (OnSuggestionUpdateListener listener : mListeners) {
            listener.onSuggestionsRemoved(removingSuggestions);
        }
    }

    private void removeNetworkSuggestionCache(ExtendedWifiNetworkSuggestion ewns) {
        if (ewns.wns.passpointConfiguration != null) {
            // Clear the Passpoint config.
            mWifiInjector.getPasspointManager().removeProvider(
                    ewns.perAppInfo.uid,
                    false,
                    ewns.wns.passpointConfiguration.getUniqueId(), null);
            removeFromPassPointInfoMap(ewns);
        } else {
            if (ewns.wns.wifiConfiguration.isEnterprise()) {
                mWifiKeyStore.removeKeys(ewns.wns.wifiConfiguration.enterpriseConfig, false);
            }
            removeFromScanResultMatchInfoMapAndRemoveRelatedScoreCard(ewns, true);
            mWifiConfigManager.removeConnectChoiceFromAllNetworks(ewns
                    .createInternalWifiConfiguration(mWifiCarrierInfoManager)
                    .getProfileKey());
        }
    }

    private void removeSuggestionFromWifiConfigManager(
            ExtendedWifiNetworkSuggestion extendedWifiNetworkSuggestion) {
        PerAppInfo perAppInfo = extendedWifiNetworkSuggestion.perAppInfo;
        if (perAppInfo.extNetworkSuggestions.containsValue(extendedWifiNetworkSuggestion)) {
            // If the suggestion is added by app again, do not remove it from WifiConfigManager.
            return;
        }
        mWifiConfigManager.removeSuggestionConfiguredNetwork(extendedWifiNetworkSuggestion
                .createInternalWifiConfiguration(mWifiCarrierInfoManager));
    }

    /**
     * Remove the provided list of network suggestions from the corresponding app's active list.
     */
    public @WifiManager.NetworkSuggestionsStatusCode int remove(
            List<WifiNetworkSuggestion> networkSuggestions, int uid, String packageName,
            @WifiManager.ActionAfterRemovingSuggestion int action) {
        if (!mWifiPermissionsUtil.doesUidBelongToCurrentUserOrDeviceOwner(uid)) {
            Log.e(TAG, "UID " + uid + " not visible to the current user");
            return WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_INTERNAL;
        }
        if (!mUserDataLoaded) {
            Log.e(TAG, "Remove Network suggestion before boot complete is not allowed.");
            return WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_INTERNAL;
        }
        if (networkSuggestions == null) {
            Log.w(TAG, "Null list of network suggestions for " + packageName + ". Ignoring");
            return WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS;
        }
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "Removing " + networkSuggestions.size() + " networks from " + packageName);
        }
        if (!checkNetworkSuggestionsNoNulls(networkSuggestions)) {
            Log.e(TAG, "Null in suggestion remove from app: " + packageName);
            return WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_REMOVE_INVALID;
        }
        PerAppInfo perAppInfo = mActiveNetworkSuggestionsPerApp.get(packageName);
        if (perAppInfo == null) {
            Log.e(TAG, "Failed to remove network suggestions for " + packageName
                    + ". No network suggestions found");
            return WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_REMOVE_INVALID;
        }
        Set<ExtendedWifiNetworkSuggestion> extNetworkSuggestions =
                convertToExtendedWnsSet(networkSuggestions, perAppInfo);
        Set<Integer> keySet = extNetworkSuggestions
                .stream()
                .map(ExtendedWifiNetworkSuggestion::hashCode)
                .collect(Collectors.toSet());
        // check if all the request network suggestions are present in the active list.
        if (!extNetworkSuggestions.isEmpty()
                && !perAppInfo.extNetworkSuggestions.keySet().containsAll(keySet)) {
            Log.e(TAG, "Failed to remove network suggestions for " + packageName
                    + ". Network suggestions not found in active network suggestions");
            return WifiManager.STATUS_NETWORK_SUGGESTIONS_ERROR_REMOVE_INVALID;
        }
        removeInternal(extNetworkSuggestions, packageName, perAppInfo, action);
        saveToStore();
        mWifiMetrics.incrementNetworkSuggestionApiNumModification();
        mWifiMetrics.noteNetworkSuggestionApiListSizeHistogram(getAllMaxSizes());
        return WifiManager.STATUS_NETWORK_SUGGESTIONS_SUCCESS;
    }

    /**
     * Remove all tracking of the app that has been uninstalled.
     */
    public void removeApp(@NonNull String packageName) {
        PerAppInfo perAppInfo = mActiveNetworkSuggestionsPerApp.get(packageName);
        if (perAppInfo == null) return;
        removeInternal(List.of(), packageName, perAppInfo, ACTION_REMOVE_SUGGESTION_DISCONNECT);
        // Stop tracking app-op changes when the App is removed from suggestion database
        stopTrackingAppOpsChange(packageName);
        // Remove the package fully from the internal database.
        mActiveNetworkSuggestionsPerApp.remove(packageName);
        RemoteCallbackList<ISuggestionConnectionStatusListener> listenerTracker =
                mSuggestionStatusListenerPerApp.remove(packageName);
        if (listenerTracker != null) listenerTracker.kill();
        saveToStore();
        Log.i(TAG, "Removed " + packageName);
    }

    /**
     * Get all network suggestion for target App
     * @return List of WifiNetworkSuggestions
     */
    public @NonNull List<WifiNetworkSuggestion> get(@NonNull String packageName, int uid) {
        List<WifiNetworkSuggestion> networkSuggestionList = new ArrayList<>();
        if (!mWifiPermissionsUtil.doesUidBelongToCurrentUserOrDeviceOwner(uid)) {
            Log.e(TAG, "UID " + uid + " not visible to the current user");
            return networkSuggestionList;
        }
        if (!mUserDataLoaded) {
            Log.e(TAG, "Get Network suggestion before boot complete is not allowed.");
            return networkSuggestionList;
        }
        PerAppInfo perAppInfo = mActiveNetworkSuggestionsPerApp.get(packageName);
        // if App never suggested return empty list.
        if (perAppInfo == null) return networkSuggestionList;
        for (ExtendedWifiNetworkSuggestion extendedSuggestion : perAppInfo.extNetworkSuggestions
                .values()) {
            networkSuggestionList.add(extendedSuggestion.wns);
        }
        return networkSuggestionList;
    }

    /**
     * Clear all internal state (for network settings reset).
     */
    public void clear() {
        Iterator<Map.Entry<String, PerAppInfo>> iter =
                mActiveNetworkSuggestionsPerApp.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<String, PerAppInfo> entry = iter.next();
            removeInternal(List.of(), entry.getKey(), entry.getValue(),
                    ACTION_REMOVE_SUGGESTION_DISCONNECT);
            // Stop tracking app-op changes when the App is removed from suggestion database
            stopTrackingAppOpsChange(entry.getKey());
            iter.remove();
        }
        mSuggestionStatusListenerPerApp.clear();
        mSuggestionUserApprovalStatusListenerPerApp.clear();
        resetNotification();
        saveToStore();
        Log.i(TAG, "Cleared all internal state");
    }

    /**
     * Check if network suggestions are enabled or disabled for the app.
     */
    public boolean hasUserApprovedForApp(String packageName) {
        PerAppInfo perAppInfo = mActiveNetworkSuggestionsPerApp.get(packageName);
        if (perAppInfo == null) return false;

        return perAppInfo.hasUserApproved;
    }

    /**
     * Enable or Disable network suggestions for the app.
     */
    public void setHasUserApprovedForApp(boolean approved, int uid, String packageName) {
        PerAppInfo perAppInfo = mActiveNetworkSuggestionsPerApp.get(packageName);
        if (perAppInfo == null) return;

        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "Setting the app " + packageName
                    + (approved ? " approved" : " not approved"));
        }
        perAppInfo.hasUserApproved = approved;
        onSuggestionUserApprovalStatusChanged(uid, packageName);
        saveToStore();
    }

    /**
     * When user approve the IMSI protection exemption for carrier or the IMSI protection is
     * enabled, restore the initial auto join configure. If user already change it to enabled,
     * keep that choice.
     */
    private void restoreInitialAutojoinForCarrierId(int carrierId, boolean allowAutoJoin) {
        for (PerAppInfo appInfo : mActiveNetworkSuggestionsPerApp.values()) {
            for (ExtendedWifiNetworkSuggestion ewns : appInfo.extNetworkSuggestions.values()) {
                if (!(isSimBasedPhase1Suggestion(ewns)
                        && getCarrierIdFromSuggestion(ewns) == carrierId)) {
                    continue;
                }
                if (ewns.isAutojoinEnabled == allowAutoJoin) {
                    continue;
                }
                if (mVerboseLoggingEnabled) {
                    Log.v(TAG, "Restore auto-join for suggestion: " + ewns);
                }
                if (allowAutoJoin) {
                    ewns.isAutojoinEnabled |= ewns.wns.isInitialAutoJoinEnabled;
                } else {
                    ewns.isAutojoinEnabled = false;
                }
                // Restore passpoint provider auto join.
                if (ewns.wns.passpointConfiguration != null) {
                    mWifiInjector.getPasspointManager()
                            .enableAutojoin(ewns.wns.passpointConfiguration.getUniqueId(),
                                    null, ewns.isAutojoinEnabled);
                } else {
                    // Update WifiConfigManager to sync auto-join.
                    updateWifiConfigInWcmIfPresent(ewns.createInternalWifiConfiguration(
                            mWifiCarrierInfoManager),
                            ewns.perAppInfo.uid, ewns.perAppInfo.packageName);
                }
            }
        }
        saveToStore();
    }

    /**
     * Returns a set of all network suggestions across all apps.
     */
    @VisibleForTesting
    public Set<WifiNetworkSuggestion> getAllNetworkSuggestions() {
        return mActiveNetworkSuggestionsPerApp.values()
                .stream()
                .flatMap(e -> convertToWnsSet(e.extNetworkSuggestions.values())
                        .stream())
                .collect(Collectors.toSet());
    }

    /**
     * Returns a set of all network suggestions across all apps that have been approved by user.
     */
    public Set<WifiNetworkSuggestion> getAllApprovedNetworkSuggestions() {
        return mActiveNetworkSuggestionsPerApp.values()
                .stream()
                .filter(e -> e.isApproved())
                .flatMap(e -> convertToWnsSet(e.extNetworkSuggestions.values())
                        .stream())
                .collect(Collectors.toSet());
    }

    /**
     * Get all user approved, non-passpoint networks from suggestion.
     */
    public List<WifiConfiguration> getAllScanOptimizationSuggestionNetworks() {
        List<WifiConfiguration> networks = new ArrayList<>();
        for (PerAppInfo info : mActiveNetworkSuggestionsPerApp.values()) {
            if (!info.isApproved()) {
                continue;
            }
            for (ExtendedWifiNetworkSuggestion ewns : info.extNetworkSuggestions.values()) {
                if (ewns.wns.getPasspointConfig() != null) {
                    continue;
                }
                WifiConfiguration network = mWifiConfigManager
                        .getConfiguredNetwork(ewns.wns.getWifiConfiguration()
                                .getProfileKey());
                if (network == null) {
                    network = ewns.createInternalWifiConfiguration(mWifiCarrierInfoManager);
                }
                networks.add(network);
            }
        }
        return networks;
    }

    /**
     * Get all user-approved Passpoint networks from suggestion.
     *
     * @param requireSsid If true, this method will only return Passpoint suggestions that include
     *     an SSID. If false, this method will return all Passpoint suggestions, including those
     *     which do not include an SSID.
     *     <p>Note: Passpoint SSIDs are recorded upon successful connection to a network. Having an
     *     SSID indicates that a Passpoint network has connected since the last reboot.
     */
    public List<WifiConfiguration> getAllPasspointScanOptimizationSuggestionNetworks(
            boolean requireSsid) {
        List<WifiConfiguration> networks = new ArrayList<>();
        for (PerAppInfo info : mActiveNetworkSuggestionsPerApp.values()) {
            if (!info.isApproved()) {
                continue;
            }
            for (ExtendedWifiNetworkSuggestion ewns : info.extNetworkSuggestions.values()) {
                if (ewns.wns.getPasspointConfig() == null) {
                    continue;
                }
                WifiConfiguration network = mWifiConfigManager
                        .getConfiguredNetwork(ewns.wns.getWifiConfiguration()
                                .getProfileKey());
                if (network == null) {
                    network = ewns.createInternalWifiConfiguration(mWifiCarrierInfoManager);
                }
                network.SSID = mWifiInjector.getPasspointManager()
                        .getMostRecentSsidForProfile(network.getPasspointUniqueId());
                if (requireSsid && network.SSID == null) {
                    continue;
                }
                networks.add(network);
            }
        }
        return networks;
    }

    private List<Integer> getAllMaxSizes() {
        return mActiveNetworkSuggestionsPerApp.values()
                .stream()
                .map(e -> e.maxSize)
                .collect(Collectors.toList());
    }

    private PendingIntent getPrivateBroadcast(@NonNull String action,
            @NonNull Pair<String, String> extra1, @NonNull Pair<String, Integer> extra2) {
        Intent intent = new Intent(action)
                .setPackage(mContext.getServiceWifiPackageName())
                .putExtra(extra1.first, extra1.second)
                .putExtra(extra2.first, extra2.second);
        return mFrameworkFacade.getBroadcast(mContext, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /**
     * Check if the request came from foreground app.
     */
    private boolean isSuggestionFromForegroundApp(@NonNull String packageName) {
        try {
            return mActivityManager.getPackageImportance(packageName)
                    <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
        } catch (SecurityException e) {
            Log.e(TAG, "Failed to check the app state", e);
            return false;
        }
    }

    private void sendUserApprovalDialog(@NonNull String packageName, int uid) {
        CharSequence appName = mFrameworkFacade.getAppName(mContext, packageName, uid);
        mWifiInjector.getWifiDialogManager().createSimpleDialog(
                mResources.getString(R.string.wifi_suggestion_title),
                mResources.getString(R.string.wifi_suggestion_content, appName),
                mResources.getString(R.string.wifi_suggestion_action_allow_app),
                mResources.getString(R.string.wifi_suggestion_action_disallow_app),
                null /* neutralButtonText */,
                new WifiDialogManager.SimpleDialogCallback() {
                    @Override
                    public void onPositiveButtonClicked() {
                        handleUserAllowAction(uid, packageName);
                    }

                    @Override
                    public void onNegativeButtonClicked() {
                        handleUserDisallowAction(uid, packageName);
                    }

                    @Override
                    public void onNeutralButtonClicked() {
                        // Not used.
                        handleUserDismissAction();
                    }

                    @Override
                    public void onCancelled() {
                        handleUserDismissAction();
                    }
                },
                new WifiThreadRunner(mHandler)).launchDialog();
        mNotificationUpdateTime = mClock.getElapsedSinceBootMillis()
                + NOTIFICATION_UPDATE_DELAY_MILLS;
        mIsLastUserApprovalUiDialog = true;
    }

    private void sendUserApprovalNotification(@NonNull String packageName, int uid) {
        Notification.Action userAllowAppNotificationAction =
                new Notification.Action.Builder(null,
                        mResources.getText(R.string.wifi_suggestion_action_allow_app),
                        getPrivateBroadcast(NOTIFICATION_USER_ALLOWED_APP_INTENT_ACTION,
                                Pair.create(EXTRA_PACKAGE_NAME, packageName),
                                Pair.create(EXTRA_UID, uid)))
                        .build();
        Notification.Action userDisallowAppNotificationAction =
                new Notification.Action.Builder(null,
                        mResources.getText(R.string.wifi_suggestion_action_disallow_app),
                        getPrivateBroadcast(NOTIFICATION_USER_DISALLOWED_APP_INTENT_ACTION,
                                Pair.create(EXTRA_PACKAGE_NAME, packageName),
                                Pair.create(EXTRA_UID, uid)))
                        .build();

        CharSequence appName = mFrameworkFacade.getAppName(mContext, packageName, uid);
        Notification notification = mFrameworkFacade.makeNotificationBuilder(
                mContext, WifiService.NOTIFICATION_NETWORK_STATUS)
                .setSmallIcon(Icon.createWithResource(mContext.getWifiOverlayApkPkgName(),
                        com.android.wifi.resources.R.drawable.stat_notify_wifi_in_range))
                .setTicker(mResources.getString(R.string.wifi_suggestion_title))
                .setContentTitle(mResources.getString(R.string.wifi_suggestion_title))
                .setStyle(new Notification.BigTextStyle()
                        .bigText(mResources.getString(R.string.wifi_suggestion_content, appName)))
                .setDeleteIntent(getPrivateBroadcast(NOTIFICATION_USER_DISMISSED_INTENT_ACTION,
                        Pair.create(EXTRA_PACKAGE_NAME, packageName), Pair.create(EXTRA_UID, uid)))
                .setShowWhen(false)
                .setLocalOnly(true)
                .setColor(mResources.getColor(android.R.color.system_notification_accent_color,
                        mContext.getTheme()))
                .addAction(userAllowAppNotificationAction)
                .addAction(userDisallowAppNotificationAction)
                .setTimeoutAfter(NOTIFICATION_EXPIRY_MILLS)
                .build();

        // Post the notification.
        mNotificationManager.notify(SystemMessage.NOTE_NETWORK_SUGGESTION_AVAILABLE, notification);
        mNotificationUpdateTime = mClock.getElapsedSinceBootMillis()
                + NOTIFICATION_UPDATE_DELAY_MILLS;
        mIsLastUserApprovalUiDialog = false;
    }

    /**
     * Send user approval notification if the app is not approved
     * @param packageName app package name
     * @param uid app UID
     * @return true if app is not approved and send notification.
     */
    private boolean sendUserApprovalNotificationIfNotApproved(
            @NonNull String packageName, @NonNull int uid) {
        if (!mActiveNetworkSuggestionsPerApp.containsKey(packageName)) {
            Log.wtf(TAG, "AppInfo is missing for " + packageName);
            return false;
        }
        if (mActiveNetworkSuggestionsPerApp.get(packageName).hasUserApproved) {
            return false; // already approved.
        }

        if (mNotificationUpdateTime > mClock.getElapsedSinceBootMillis()) {
            return false; // Active notification is still available, do not update.
        }
        Log.i(TAG, "Sending user approval notification for " + packageName);
        sendUserApprovalNotification(packageName, uid);
        return true;
    }

    private @Nullable Set<ExtendedWifiNetworkSuggestion>
            getNetworkSuggestionsForScanResultMatchInfo(
            @NonNull ScanResultMatchInfo scanResultMatchInfo, @Nullable MacAddress bssid) {
        Set<ExtendedWifiNetworkSuggestion> extNetworkSuggestions = new HashSet<>();
        if (bssid != null) {
            Set<ExtendedWifiNetworkSuggestion> matchingExtNetworkSuggestionsWithBssid =
                    mActiveScanResultMatchInfoWithBssid.get(
                            Pair.create(scanResultMatchInfo, bssid));
            if (matchingExtNetworkSuggestionsWithBssid != null) {
                extNetworkSuggestions.addAll(matchingExtNetworkSuggestionsWithBssid);
            }
        }
        Set<ExtendedWifiNetworkSuggestion> matchingNetworkSuggestionsWithNoBssid =
                mActiveScanResultMatchInfoWithNoBssid.get(scanResultMatchInfo);
        if (matchingNetworkSuggestionsWithNoBssid != null) {
            extNetworkSuggestions.addAll(matchingNetworkSuggestionsWithNoBssid);
        }
        if (extNetworkSuggestions.isEmpty()) {
            return null;
        }
        return extNetworkSuggestions;
    }

    private @Nullable Set<ExtendedWifiNetworkSuggestion> getNetworkSuggestionsForFqdnMatch(
            @Nullable String fqdn) {
        if (TextUtils.isEmpty(fqdn)) {
            return null;
        }
        return mPasspointInfo.get(fqdn);
    }

    /**
     * Returns a set of all network suggestions matching the provided FQDN.
     */
    public @NonNull Set<ExtendedWifiNetworkSuggestion> getNetworkSuggestionsForFqdn(String fqdn) {
        Set<ExtendedWifiNetworkSuggestion> extNetworkSuggestions =
                getNetworkSuggestionsForFqdnMatch(fqdn);
        if (extNetworkSuggestions == null) {
            return Set.of();
        }
        Set<ExtendedWifiNetworkSuggestion> approvedExtNetworkSuggestions = new HashSet<>();
        for (ExtendedWifiNetworkSuggestion ewns : extNetworkSuggestions) {
            if (!ewns.perAppInfo.isApproved()) {
                sendUserApprovalNotificationIfNotApproved(ewns.perAppInfo.packageName,
                        ewns.perAppInfo.uid);
                continue;
            }
            if (ewns.wns.wifiConfiguration.carrierMerged && !areCarrierMergedSuggestionsAllowed(
                    ewns.wns.wifiConfiguration, ewns.perAppInfo.packageName)) {
                continue;
            }
            if (isSimBasedPhase1Suggestion(ewns)) {
                mWifiCarrierInfoManager.sendImsiProtectionExemptionNotificationIfRequired(
                        getCarrierIdFromSuggestion(ewns));
            }
            approvedExtNetworkSuggestions.add(ewns);
        }

        if (approvedExtNetworkSuggestions.isEmpty()) {
            return Set.of();
        }
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "getNetworkSuggestionsForFqdn Found "
                    + approvedExtNetworkSuggestions + " for " + fqdn);
        }
        return approvedExtNetworkSuggestions;
    }

    /**
     * Returns a set of all network suggestions matching the provided scan detail.
     */
    public @NonNull Set<ExtendedWifiNetworkSuggestion> getNetworkSuggestionsForScanDetail(
            @NonNull ScanDetail scanDetail) {
        ScanResult scanResult = scanDetail.getScanResult();
        if (scanResult == null) {
            Log.e(TAG, "No scan result found in scan detail");
            return Set.of();
        }
        Set<ExtendedWifiNetworkSuggestion> extNetworkSuggestions = null;
        try {
            ScanResultMatchInfo scanResultMatchInfo =
                    ScanResultMatchInfo.fromScanResult(scanResult);
            extNetworkSuggestions = getNetworkSuggestionsForScanResultMatchInfo(
                    scanResultMatchInfo,  MacAddress.fromString(scanResult.BSSID));
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Failed to lookup network from scan result match info map", e);
        }
        if (extNetworkSuggestions == null) {
            return Set.of();
        }
        Set<ExtendedWifiNetworkSuggestion> approvedExtNetworkSuggestions = new HashSet<>();
        for (ExtendedWifiNetworkSuggestion ewns : extNetworkSuggestions) {
            if (!ewns.perAppInfo.isApproved()) {
                sendUserApprovalNotificationIfNotApproved(ewns.perAppInfo.packageName,
                        ewns.perAppInfo.uid);
                continue;
            }
            if (ewns.wns.wifiConfiguration.carrierMerged && !areCarrierMergedSuggestionsAllowed(
                    ewns.wns.wifiConfiguration, ewns.perAppInfo.packageName)) {
                continue;
            }
            if (isSimBasedPhase1Suggestion(ewns)) {
                mWifiCarrierInfoManager.sendImsiProtectionExemptionNotificationIfRequired(
                        getCarrierIdFromSuggestion(ewns));
            }
            approvedExtNetworkSuggestions.add(ewns);
        }

        if (approvedExtNetworkSuggestions.isEmpty()) {
            return Set.of();
        }
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "getNetworkSuggestionsForScanDetail Found "
                    + approvedExtNetworkSuggestions + " for " + scanResult.SSID
                    + "[" + scanResult.capabilities + "]");
        }
        return approvedExtNetworkSuggestions;
    }

    /**
     * Returns a set of all network suggestions matching the provided the WifiConfiguration.
     */
    public @Nullable Set<ExtendedWifiNetworkSuggestion> getNetworkSuggestionsForWifiConfiguration(
            @NonNull WifiConfiguration wifiConfiguration, @Nullable String bssid) {
        Set<ExtendedWifiNetworkSuggestion> extNetworkSuggestions = null;
        if (wifiConfiguration.isPasspoint()) {
            extNetworkSuggestions = getNetworkSuggestionsForFqdnMatch(wifiConfiguration.FQDN);
        } else {
            try {
                ScanResultMatchInfo scanResultMatchInfo =
                        ScanResultMatchInfo.fromWifiConfiguration(wifiConfiguration);
                extNetworkSuggestions = getNetworkSuggestionsForScanResultMatchInfo(
                        scanResultMatchInfo, bssid == null ? null : MacAddress.fromString(bssid));
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Failed to lookup network from scan result match info map", e);
            }
        }
        if (extNetworkSuggestions == null || extNetworkSuggestions.isEmpty()) {
            return null;
        }
        Set<ExtendedWifiNetworkSuggestion> approvedExtNetworkSuggestions =
                extNetworkSuggestions
                        .stream()
                        .filter(n -> n.perAppInfo.isApproved())
                        .collect(Collectors.toSet());
        if (approvedExtNetworkSuggestions.isEmpty()) {
            return null;
        }
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "getNetworkSuggestionsForWifiConfiguration Found "
                    + approvedExtNetworkSuggestions + " for " + wifiConfiguration.SSID
                    + wifiConfiguration.FQDN + "[" + wifiConfiguration.allowedKeyManagement + "]");
        }
        return approvedExtNetworkSuggestions;
    }

    /**
     * Retrieve the WifiConfigurations for all matched suggestions which allow user manually connect
     * and user already approved for non-open networks.
     */
    public @NonNull List<WifiConfiguration> getWifiConfigForMatchedNetworkSuggestionsSharedWithUser(
            List<ScanResult> scanResults) {
        // Create a temporary look-up table.
        // As they are all single type configurations, they should have unique keys.
        Map<String, WifiConfiguration> wifiConfigMap = new HashMap<>();
        WifiConfigurationUtil.convertMultiTypeConfigsToLegacyConfigs(
                mWifiConfigManager.getConfiguredNetworks(), true)
                        .forEach(c -> wifiConfigMap.put(c.getProfileKey(), c));

        // Create a HashSet to avoid return multiple result for duplicate ScanResult.
        Set<String> networkKeys = new HashSet<>();
        List<WifiConfiguration> sharedWifiConfigs = new ArrayList<>();
        for (ScanResult scanResult : scanResults) {
            ScanResultMatchInfo scanResultMatchInfo =
                    ScanResultMatchInfo.fromScanResult(scanResult);
            if (scanResultMatchInfo.securityParamsList.size() == 0) continue;
            // Only filter legacy Open network.
            if (scanResultMatchInfo.securityParamsList.size() == 1
                    && scanResultMatchInfo.getDefaultSecurityParams().getSecurityType()
                            == WifiConfiguration.SECURITY_TYPE_OPEN) {
                continue;
            }
            Set<ExtendedWifiNetworkSuggestion> extNetworkSuggestions =
                    getNetworkSuggestionsForScanResultMatchInfo(
                            scanResultMatchInfo,  MacAddress.fromString(scanResult.BSSID));
            if (extNetworkSuggestions == null || extNetworkSuggestions.isEmpty()) {
                continue;
            }
            Set<ExtendedWifiNetworkSuggestion> sharedNetworkSuggestions = extNetworkSuggestions
                    .stream()
                    .filter(ewns -> ewns.perAppInfo.hasUserApproved
                            && ewns.wns.isUserAllowedToManuallyConnect)
                    .collect(Collectors.toSet());
            if (sharedNetworkSuggestions.isEmpty()) {
                continue;
            }
            for (ExtendedWifiNetworkSuggestion ewns : sharedNetworkSuggestions) {
                if (mVerboseLoggingEnabled) {
                    Log.v(TAG, "getWifiConfigForMatchedNetworkSuggestionsSharedWithUser Found "
                            + ewns + " for " + scanResult.SSID
                            + "[" + scanResult.capabilities + "]");
                }
                WifiConfiguration config = ewns.createInternalWifiConfiguration(
                        mWifiCarrierInfoManager);
                if (config.carrierId != TelephonyManager.UNKNOWN_CARRIER_ID
                        && !mWifiCarrierInfoManager.isSimReady(config.subscriptionId)) {
                    continue;
                }
                if (config.carrierMerged && !areCarrierMergedSuggestionsAllowed(
                        config, ewns.perAppInfo.packageName)) {
                    continue;
                }
                WifiConfiguration wCmWifiConfig = wifiConfigMap.get(config.getProfileKey());
                if (wCmWifiConfig == null) {
                    continue;
                }
                if (networkKeys.add(wCmWifiConfig.getProfileKey())) {
                    sharedWifiConfigs.add(wCmWifiConfig);
                }
            }
        }
        return sharedWifiConfigs;
    }

    /**
     * Check if the given passpoint suggestion has user approval and allow user manually connect.
     */
    public boolean isPasspointSuggestionSharedWithUser(WifiConfiguration config) {
        if (WifiConfiguration.isMetered(config, null)
                && mWifiCarrierInfoManager.isCarrierNetworkFromNonDefaultDataSim(config)) {
            return false;
        }
        if (config.carrierId != TelephonyManager.UNKNOWN_CARRIER_ID) {
            int subId = mWifiCarrierInfoManager.getBestMatchSubscriptionId(config);
            if (!mWifiCarrierInfoManager.isSimReady(subId)) {
                return false;
            }
        }
        Set<ExtendedWifiNetworkSuggestion> extendedWifiNetworkSuggestions =
                getNetworkSuggestionsForFqdnMatch(config.FQDN);
        Set<ExtendedWifiNetworkSuggestion> matchedSuggestions =
                extendedWifiNetworkSuggestions == null ? null : extendedWifiNetworkSuggestions
                .stream().filter(ewns -> ewns.perAppInfo.uid == config.creatorUid)
                .collect(Collectors.toSet());
        if (matchedSuggestions == null || matchedSuggestions.isEmpty()) {
            Log.e(TAG, "Matched network suggestion is missing for FQDN:" + config.FQDN);
            return false;
        }
        ExtendedWifiNetworkSuggestion suggestion = matchedSuggestions
                .stream().findAny().get();
        return suggestion.wns.isUserAllowedToManuallyConnect
                && suggestion.perAppInfo.hasUserApproved;
    }

    /**
     * Get hidden network from active network suggestions.
     * Todo(): Now limit by a fixed number, maybe we can try rotation?
     * @param autoJoinOnly retrieve hidden network autojoin enabled only.
     * @return list of HiddenNetwork
     */
    public List<WifiScanner.ScanSettings.HiddenNetwork> retrieveHiddenNetworkList(
            boolean autoJoinOnly) {
        List<WifiScanner.ScanSettings.HiddenNetwork> hiddenNetworks = new ArrayList<>();
        Set<WifiSsid> ssidSet = new LinkedHashSet<>();
        for (PerAppInfo appInfo : mActiveNetworkSuggestionsPerApp.values()) {
            if (!appInfo.hasUserApproved) continue;
            for (ExtendedWifiNetworkSuggestion ewns : appInfo.extNetworkSuggestions.values()) {
                if (!ewns.wns.wifiConfiguration.hiddenSSID) continue;
                if (autoJoinOnly && !ewns.isAutojoinEnabled) continue;
                ssidSet.addAll(mWifiInjector.getSsidTranslator().getAllPossibleOriginalSsids(
                        WifiSsid.fromString(ewns.wns.wifiConfiguration.SSID)));
                if (ssidSet.size() >= NUMBER_OF_HIDDEN_NETWORK_FOR_ONE_SCAN) {
                    break;
                }
            }
        }
        for (WifiSsid ssid : ssidSet) {
            hiddenNetworks.add(new WifiScanner.ScanSettings.HiddenNetwork(ssid.toString()));
            if (hiddenNetworks.size() >= NUMBER_OF_HIDDEN_NETWORK_FOR_ONE_SCAN) {
                break;
            }
        }
        return hiddenNetworks;
    }

    /**
     * Helper method to send the post connection broadcast to specified package.
     */
    private void sendPostConnectionBroadcast(
            ExtendedWifiNetworkSuggestion extSuggestion) {
        Intent intent = new Intent(WifiManager.ACTION_WIFI_NETWORK_SUGGESTION_POST_CONNECTION);
        intent.putExtra(WifiManager.EXTRA_NETWORK_SUGGESTION, extSuggestion.wns);
        // Intended to wakeup the receiving app so set the specific package name.
        intent.setPackage(extSuggestion.perAppInfo.packageName);
        mContext.sendBroadcastAsUser(
                intent, UserHandle.getUserHandleForUid(extSuggestion.perAppInfo.uid));
    }

    /**
     * Helper method to send the post connection broadcast to specified package.
     */
    private void sendPostConnectionBroadcastIfAllowed(
            ExtendedWifiNetworkSuggestion matchingExtSuggestion, @NonNull String message) {
        try {
            mWifiPermissionsUtil.enforceCanAccessScanResults(
                    matchingExtSuggestion.perAppInfo.packageName,
                    matchingExtSuggestion.perAppInfo.featureId,
                    matchingExtSuggestion.perAppInfo.uid, message);
        } catch (SecurityException se) {
            Log.w(TAG, "Permission denied for sending post connection broadcast to "
                    + matchingExtSuggestion.perAppInfo.packageName);
            return;
        }
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "Sending post connection broadcast to "
                    + matchingExtSuggestion.perAppInfo.packageName);
        }
        sendPostConnectionBroadcast(matchingExtSuggestion);
    }

    /**
     * Send out the {@link WifiManager#ACTION_WIFI_NETWORK_SUGGESTION_POST_CONNECTION} to the
     * network suggestion that provided credential for the current connection network.
     * If current connection network is open user saved network, broadcast will be only sent out to
     * one of the carrier apps that suggested matched network suggestions.
     *
     * @param connectedNetwork {@link WifiConfiguration} representing the network connected to.
     * @param connectedBssid BSSID of the network connected to.
     */
    private void handleConnectionSuccess(
            @NonNull WifiConfiguration connectedNetwork, @NonNull String connectedBssid) {
        if (!(connectedNetwork.fromWifiNetworkSuggestion || connectedNetwork.isOpenNetwork())) {
            return;
        }
        Set<ExtendedWifiNetworkSuggestion> matchingExtNetworkSuggestions =
                    getNetworkSuggestionsForWifiConfiguration(connectedNetwork, connectedBssid);

        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "Network suggestions matching the connection "
                    + matchingExtNetworkSuggestions);
        }
        if (matchingExtNetworkSuggestions == null
                || matchingExtNetworkSuggestions.isEmpty()) return;

        Set<ExtendedWifiNetworkSuggestion> matchingExtNetworkSuggestionsFromTargetApp;
        if (connectedNetwork.fromWifiNetworkSuggestion) {
            matchingExtNetworkSuggestionsFromTargetApp =
                    getMatchedSuggestionsWithSameProfileKey(matchingExtNetworkSuggestions,
                            connectedNetwork);
            if (matchingExtNetworkSuggestionsFromTargetApp.isEmpty()) {
                Log.wtf(TAG, "Current connected network suggestion is missing!");
                return;
            }
        } else {
            // If not suggestion, the connected network is open network.
            // For saved open network, found the matching suggestion from carrier privileged
            // apps. As we only expect one suggestor app to take action on post connection, if
            // multiple apps suggested matched suggestions, framework will randomly pick one.
            matchingExtNetworkSuggestionsFromTargetApp = matchingExtNetworkSuggestions.stream()
                    .filter(x -> x.perAppInfo.carrierId != TelephonyManager.UNKNOWN_CARRIER_ID
                            || mWifiPermissionsUtil
                            .checkNetworkCarrierProvisioningPermission(x.perAppInfo.uid))
                    .limit(1).collect(Collectors.toSet());
            if (matchingExtNetworkSuggestionsFromTargetApp.isEmpty()) {
                if (mVerboseLoggingEnabled) {
                    Log.v(TAG, "No suggestion matched connected user saved open network.");
                }
                return;
            }
        }

        mWifiMetrics.incrementNetworkSuggestionApiNumConnectSuccess();
        // Find subset of network suggestions have set |isAppInteractionRequired|.
        Set<ExtendedWifiNetworkSuggestion> matchingExtNetworkSuggestionsWithReqAppInteraction =
                matchingExtNetworkSuggestionsFromTargetApp.stream()
                        .filter(x -> x.wns.isAppInteractionRequired)
                        .collect(Collectors.toSet());
        if (matchingExtNetworkSuggestionsWithReqAppInteraction.isEmpty()) return;

        // Iterate over the matching network suggestions list:
        // a) Ensure that these apps have the necessary location permissions.
        // b) Send directed broadcast to the app with their corresponding network suggestion.
        for (ExtendedWifiNetworkSuggestion matchingExtNetworkSuggestion
                : matchingExtNetworkSuggestionsWithReqAppInteraction) {
            sendPostConnectionBroadcastIfAllowed(
                    matchingExtNetworkSuggestion,
                    "Connected to " + matchingExtNetworkSuggestion.wns.wifiConfiguration.SSID
                            + ". featureId is first feature of the app using network suggestions");
        }
    }

    /**
     * Handle connection failure.
     *
     * @param network {@link WifiConfiguration} representing the network that connection failed to.
     * @param bssid BSSID of the network connection failed to if known, else null.
     * @param failureCode failure reason code.
     */
    private void handleConnectionFailure(@NonNull WifiConfiguration network,
                                         @Nullable String bssid, int failureCode) {
        if (!network.fromWifiNetworkSuggestion) {
            return;
        }
        Set<ExtendedWifiNetworkSuggestion> matchingExtNetworkSuggestions =
                getNetworkSuggestionsForWifiConfiguration(network, bssid);
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "Network suggestions matching the connection failure "
                    + matchingExtNetworkSuggestions);
        }
        if (matchingExtNetworkSuggestions == null
                || matchingExtNetworkSuggestions.isEmpty()) return;

        mWifiMetrics.incrementNetworkSuggestionApiNumConnectFailure();
        // TODO (b/115504887, b/112196799): Blocklist the corresponding network suggestion if
        // the connection failed.

        // Find subset of network suggestions which suggested the connection failure network.
        Set<ExtendedWifiNetworkSuggestion> matchingExtNetworkSuggestionsFromTargetApp =
                getMatchedSuggestionsWithSameProfileKey(matchingExtNetworkSuggestions, network);
        if (matchingExtNetworkSuggestionsFromTargetApp.isEmpty()) {
            Log.wtf(TAG, "Current connection failure network suggestion is missing!");
            return;
        }

        for (ExtendedWifiNetworkSuggestion matchingExtNetworkSuggestion
                : matchingExtNetworkSuggestionsFromTargetApp) {
            sendConnectionFailureIfAllowed(matchingExtNetworkSuggestion.perAppInfo.packageName,
                    matchingExtNetworkSuggestion.perAppInfo.featureId,
                    matchingExtNetworkSuggestion.perAppInfo.uid,
                    matchingExtNetworkSuggestion.wns, failureCode);
        }
    }

    private Set<ExtendedWifiNetworkSuggestion> getMatchedSuggestionsWithSameProfileKey(
            Set<ExtendedWifiNetworkSuggestion> matchingSuggestions, WifiConfiguration network) {
        if (matchingSuggestions == null || matchingSuggestions.isEmpty()) {
            return Set.of();
        }
        Set<ExtendedWifiNetworkSuggestion> matchingExtNetworkSuggestionsWithSameProfileKey =
                new HashSet<>();
        for (ExtendedWifiNetworkSuggestion ewns : matchingSuggestions) {
            WifiConfiguration config = ewns
                    .createInternalWifiConfiguration(mWifiCarrierInfoManager);
            if (config.getProfileKey().equals(network.getProfileKey())
                    && config.creatorName.equals(network.creatorName)) {
                matchingExtNetworkSuggestionsWithSameProfileKey.add(ewns);
            }
        }
        return matchingExtNetworkSuggestionsWithSameProfileKey;
    }

    /**
     * Invoked by {@link ClientModeImpl} on end of connection attempt to a network.
     *
     * @param failureCode Failure codes representing {@link WifiMetrics.ConnectionEvent} codes.
     * @param network WifiConfiguration corresponding to the current network.
     * @param bssid BSSID of the current network.
     */
    public void handleConnectionAttemptEnded(
            int failureCode, @NonNull WifiConfiguration network, @Nullable String bssid) {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "handleConnectionAttemptEnded " + failureCode + ", " + network);
        }
        if (failureCode == WifiMetrics.ConnectionEvent.FAILURE_NONE) {
            handleConnectionSuccess(network, bssid);
        } else {
            handleConnectionFailure(network, bssid, failureCode);
        }
    }

    /**
     * Send network connection failure event to app when an connection attempt failure.
     * @param packageName package name to send event
     * @param featureId The feature in the package
     * @param uid uid of the app.
     * @param matchingSuggestion suggestion on this connection failure
     * @param connectionEvent connection failure code
     */
    private void sendConnectionFailureIfAllowed(String packageName, @Nullable String featureId,
            int uid, @NonNull WifiNetworkSuggestion matchingSuggestion, int connectionEvent) {
        RemoteCallbackList<ISuggestionConnectionStatusListener> listenersTracker =
                mSuggestionStatusListenerPerApp.get(packageName);
        if (listenersTracker == null || listenersTracker.getRegisteredCallbackCount() == 0) {
            return;
        }
        try {
            mWifiPermissionsUtil.enforceCanAccessScanResults(
                    packageName, featureId, uid, "Connection failure");
        } catch (SecurityException se) {
            Log.w(TAG, "Permission denied for sending connection failure event to " + packageName);
            return;
        }
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "Sending connection failure event to " + packageName);
        }
        int itemCount = listenersTracker.beginBroadcast();
        for (int i = 0; i < itemCount; i++) {
            try {
                listenersTracker.getBroadcastItem(i).onConnectionStatus(matchingSuggestion,
                        internalConnectionEventToSuggestionFailureCode(connectionEvent));
            } catch (RemoteException e) {
                Log.e(TAG, "sendNetworkCallback: remote exception -- " + e);
            }
        }
        listenersTracker.finishBroadcast();
    }

    private @WifiManager.SuggestionConnectionStatusCode
            int internalConnectionEventToSuggestionFailureCode(int connectionEvent) {
        switch (connectionEvent) {
            case WifiMetrics.ConnectionEvent.FAILURE_ASSOCIATION_REJECTION:
            case WifiMetrics.ConnectionEvent.FAILURE_ASSOCIATION_TIMED_OUT:
                return WifiManager.STATUS_SUGGESTION_CONNECTION_FAILURE_ASSOCIATION;
            case WifiMetrics.ConnectionEvent.FAILURE_SSID_TEMP_DISABLED:
            case WifiMetrics.ConnectionEvent.FAILURE_AUTHENTICATION_FAILURE:
                return WifiManager.STATUS_SUGGESTION_CONNECTION_FAILURE_AUTHENTICATION;
            case WifiMetrics.ConnectionEvent.FAILURE_DHCP:
                return WifiManager.STATUS_SUGGESTION_CONNECTION_FAILURE_IP_PROVISIONING;
            default:
                return WifiManager.STATUS_SUGGESTION_CONNECTION_FAILURE_UNKNOWN;
        }
    }

    /**
     * Register a SuggestionUserApprovalStatusListener on user approval status changes.
     * @param listener ISuggestionUserApprovalStatusListener instance to add.
     * @param uid uid of the app.
     */
    public void addSuggestionUserApprovalStatusListener(
            @NonNull ISuggestionUserApprovalStatusListener listener, String packageName, int uid) {
        RemoteCallbackList<ISuggestionUserApprovalStatusListener> listenersTracker =
                mSuggestionUserApprovalStatusListenerPerApp.get(packageName);
        if (listenersTracker == null) {
            listenersTracker = new RemoteCallbackList<>();
        }
        listenersTracker.register(listener);
        mSuggestionUserApprovalStatusListenerPerApp.put(packageName, listenersTracker);
        try {
            listener.onUserApprovalStatusChange(
                    getNetworkSuggestionUserApprovalStatus(uid, packageName));
        } catch (RemoteException e) {
            Log.e(TAG, "sendUserApprovalStatusChange: remote exception -- " + e);
        }
    }

    /**
     * Unregister a listener on on user approval status changes.
     * @param listener ISuggestionUserApprovalStatusListener instance to remove.
     * @param uid uid of the app.
     */
    public void removeSuggestionUserApprovalStatusListener(
            @NonNull ISuggestionUserApprovalStatusListener listener, String packageName, int uid) {
        RemoteCallbackList<ISuggestionUserApprovalStatusListener> listenersTracker =
                mSuggestionUserApprovalStatusListenerPerApp.get(packageName);
        if (listenersTracker == null || !listenersTracker.unregister(listener)) {
            Log.w(TAG, "removeSuggestionUserApprovalStatusListener: Listener from " + packageName
                    + " already removed.");
            return;
        }
        if (listenersTracker != null && listenersTracker.getRegisteredCallbackCount() == 0) {
            mSuggestionUserApprovalStatusListenerPerApp.remove(packageName);
        }
    }

    /**
     * Register a SuggestionConnectionStatusListener on network connection failure.
     * @param listener ISuggestionNetworkCallback instance to add.
     * @param uid uid of the app.
     * @return true if succeed otherwise false.
     */
    public boolean registerSuggestionConnectionStatusListener(
            @NonNull ISuggestionConnectionStatusListener listener,
            String packageName, int uid) {
        if (!mWifiPermissionsUtil.doesUidBelongToCurrentUserOrDeviceOwner(uid)) {
            Log.e(TAG, "UID " + uid + " not visible to the current user");
            return false;
        }
        RemoteCallbackList<ISuggestionConnectionStatusListener> listenersTracker =
                mSuggestionStatusListenerPerApp.get(packageName);
        if (listenersTracker == null) {
            listenersTracker = new RemoteCallbackList<>();
        }
        listenersTracker.register(listener);
        mSuggestionStatusListenerPerApp.put(packageName, listenersTracker);
        return true;
    }

    /**
     * Unregister a listener on network connection failure.
     * @param listener ISuggestionNetworkCallback instance to remove.
     * @param uid uid of the app.
     */
    public void unregisterSuggestionConnectionStatusListener(
            @NonNull ISuggestionConnectionStatusListener listener, String packageName, int uid) {
        if (!mWifiPermissionsUtil.doesUidBelongToCurrentUserOrDeviceOwner(uid)) {
            Log.e(TAG, "UID " + uid + " not visible to the current user");
            return;
        }
        RemoteCallbackList<ISuggestionConnectionStatusListener> listenersTracker =
                mSuggestionStatusListenerPerApp.get(packageName);
        if (listenersTracker == null || !listenersTracker.unregister(listener)) {
            Log.w(TAG, "unregisterSuggestionConnectionStatusListener: Listener from " + packageName
                    + " already unregister.");
        }
        if (listenersTracker != null && listenersTracker.getRegisteredCallbackCount() == 0) {
            mSuggestionStatusListenerPerApp.remove(packageName);
        }
    }

    /**
     * When SIM state changes, check if carrier privileges changes for app.
     * If app changes from privileged to not privileged, remove all suggestions and reset state.
     * If app changes from not privileges to privileged, set target carrier id for all suggestions.
     */
    public void updateCarrierPrivilegedApps() {
        if (SdkLevel.isAtLeastT()) {
            return;
        }
        Log.w(TAG, "SIM state is changed!");
        Iterator<Map.Entry<String, PerAppInfo>> iter =
                mActiveNetworkSuggestionsPerApp.entrySet().iterator();
        while (iter.hasNext()) {
            PerAppInfo appInfo = iter.next().getValue();
            int carrierId = mWifiCarrierInfoManager
                    .getCarrierIdForPackageWithCarrierPrivileges(appInfo.packageName);
            if (carrierId == appInfo.carrierId) {
                continue;
            }
            if (carrierId == TelephonyManager.UNKNOWN_CARRIER_ID) {
                Log.i(TAG, "Carrier privilege revoked for " + appInfo.packageName);
                removeInternal(List.of(), appInfo.packageName, appInfo,
                        ACTION_REMOVE_SUGGESTION_DISCONNECT);
                // Stop tracking app-op changes when the App is removed from suggestion database
                stopTrackingAppOpsChange(appInfo.packageName);
                iter.remove();
                continue;
            }
            Log.i(TAG, "Carrier privilege granted for " + appInfo.packageName);
            appInfo.carrierId = carrierId;
            for (ExtendedWifiNetworkSuggestion ewns : appInfo.extNetworkSuggestions.values()) {
                ewns.wns.wifiConfiguration.carrierId = carrierId;
            }
        }
        saveToStore();
    }

    /**
     * When carrier privileged packages list changes, handle the apps which privileged state changed
     * - If app changes from privileged to not privileged, remove all suggestions and reset state
     * - If app changes from not privileges to privileged, set target carrier id for all suggestions
     */
    public void updateCarrierPrivilegedApps(Set<String> privilegedApps) {
        if (!SdkLevel.isAtLeastT()) {
            return;
        }
        if (mVerboseLoggingEnabled) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("Carrier privileged packages changed, privileged apps=[");
            for (String packagesName : privilegedApps) {
                stringBuilder.append(packagesName).append(", ");
            }
            stringBuilder.append("]");
            Log.d(TAG, stringBuilder.toString());
        }
        Iterator<Map.Entry<String, PerAppInfo>> iter =
                mActiveNetworkSuggestionsPerApp.entrySet().iterator();
        while (iter.hasNext()) {
            PerAppInfo appInfo = iter.next().getValue();
            if (privilegedApps.contains(appInfo.packageName)) {
                if (appInfo.carrierId != TelephonyManager.UNKNOWN_CARRIER_ID) {
                    // Already privileged before, no change.
                    continue;
                }
                // for (newly) privileged packages: update carrier ID
                int carrierId = mWifiCarrierInfoManager
                        .getCarrierIdForPackageWithCarrierPrivileges(appInfo.packageName);
                Log.i(TAG, "Carrier privilege granted for " + appInfo.packageName);
                appInfo.carrierId = carrierId;
                for (ExtendedWifiNetworkSuggestion ewns : appInfo.extNetworkSuggestions.values()) {
                    ewns.wns.wifiConfiguration.carrierId = carrierId;
                }
                continue;
            }
            if (appInfo.carrierId == TelephonyManager.UNKNOWN_CARRIER_ID) {
                // Apps never got privileged, no change.
                continue;
            }
            // Carrier privilege revoked, remove.
            Log.i(TAG, "Carrier privilege revoked for " + appInfo.packageName);
            removeInternal(List.of(), appInfo.packageName, appInfo,
                    ACTION_REMOVE_SUGGESTION_DISCONNECT);
            // Stop tracking app-op changes when the App is removed from suggestion database
            stopTrackingAppOpsChange(appInfo.packageName);
            iter.remove();
        }
        saveToStore();
    }

    /**
     * Resets all sim networks state.
     */
    public void resetSimNetworkSuggestions() {
        mActiveNetworkSuggestionsPerApp.values().stream()
                .flatMap(e -> e.extNetworkSuggestions.values().stream())
                .forEach(ewns -> ewns.anonymousIdentity = null);
        saveToStore();
    }

    /**
     * Set auto-join enable/disable for suggestion network
     * @param config WifiConfiguration which is to change.
     * @param choice true to enable auto-join, false to disable.
     * @return true on success, false otherwise (e.g. if no match suggestion exists).
     */
    public boolean allowNetworkSuggestionAutojoin(WifiConfiguration config, boolean choice) {
        if (!config.fromWifiNetworkSuggestion) {
            Log.e(TAG, "allowNetworkSuggestionAutojoin: on non-suggestion network: "
                    + config);
            return false;
        }

        if (config.isPasspoint()) {
            if (!mWifiInjector.getPasspointManager().enableAutojoin(config.getProfileKey(),
                    null, choice)) {
                return false;
            }
        }

        Set<ExtendedWifiNetworkSuggestion> matchingExtendedWifiNetworkSuggestions =
                getMatchedSuggestionsWithSameProfileKey(
                        getNetworkSuggestionsForWifiConfiguration(config, config.BSSID), config);
        if (matchingExtendedWifiNetworkSuggestions.isEmpty()) {
            Log.e(TAG, "allowNetworkSuggestionAutojoin: network is missing: "
                    + config);
            return false;
        }
        for (ExtendedWifiNetworkSuggestion ewns : matchingExtendedWifiNetworkSuggestions) {
            ewns.isAutojoinEnabled = choice;
        }
        saveToStore();
        return true;
    }

    /**
     * Get the filtered ScanResults which may be authenticated by the suggested configurations.
     * @param wifiNetworkSuggestions The list of {@link WifiNetworkSuggestion}
     * @param scanResults The list of {@link ScanResult}
     * @return The filtered ScanResults
     */
    @NonNull
    public Map<WifiNetworkSuggestion, List<ScanResult>> getMatchingScanResults(
            @NonNull List<WifiNetworkSuggestion> wifiNetworkSuggestions,
            @NonNull List<ScanResult> scanResults) {
        Map<WifiNetworkSuggestion, List<ScanResult>> filteredScanResults = new HashMap<>();
        if (wifiNetworkSuggestions == null || wifiNetworkSuggestions.isEmpty()
                || scanResults == null || scanResults.isEmpty()) {
            return filteredScanResults;
        }
        for (WifiNetworkSuggestion suggestion : wifiNetworkSuggestions) {
            if (suggestion == null || suggestion.wifiConfiguration == null) {
                continue;
            }
            filteredScanResults.put(suggestion,
                    getMatchingScanResultsForSuggestion(suggestion, scanResults));
        }

        return filteredScanResults;
    }

    private List<ScanResult> getMatchingScanResultsForSuggestion(WifiNetworkSuggestion suggestion,
            List<ScanResult> scanResults) {
        if (suggestion.passpointConfiguration != null) {
            return mWifiInjector.getPasspointManager().getMatchingScanResults(
                    suggestion.passpointConfiguration, scanResults);
        } else {
            return getMatchingScanResults(suggestion.wifiConfiguration, scanResults);
        }
    }

    /**
     * Get the filtered ScanResults which may be authenticated by the {@link WifiConfiguration}.
     * @param wifiConfiguration The instance of {@link WifiConfiguration}
     * @param scanResults The list of {@link ScanResult}
     * @return The filtered ScanResults
     */
    @NonNull
    private List<ScanResult> getMatchingScanResults(
            @NonNull WifiConfiguration wifiConfiguration,
            @NonNull List<ScanResult> scanResults) {
        ScanResultMatchInfo matchInfoFromConfigration =
                ScanResultMatchInfo.fromWifiConfiguration(wifiConfiguration);
        if (matchInfoFromConfigration == null) {
            return new ArrayList<>();
        }
        List<ScanResult> filteredScanResult = new ArrayList<>();
        for (ScanResult scanResult : scanResults) {
            if (matchInfoFromConfigration.equals(ScanResultMatchInfo.fromScanResult(scanResult))) {
                filteredScanResult.add(scanResult);
            }
        }

        return filteredScanResult;
    }

    /**
     * Add the suggestion update event listener
     */
    public void addOnSuggestionUpdateListener(OnSuggestionUpdateListener listener) {
        mListeners.add(listener);
    }

    /**
     * When a saved open network has a same network suggestion which is from app has
     * NETWORK_CARRIER_PROVISIONING permission, also that app suggested secure network suggestion
     * for same carrier with higher or equal priority and Auto-Join enabled, also that secure
     * network is in the range. The saved open network will be ignored during the network selection.
     * TODO (b/142035508): revert all these changes once we build infra needed to solve this.
     * @param configuration Saved open network to check if it should be ignored.
     * @param scanDetails Available ScanDetail nearby.
     * @return True if the open network should be ignored, false otherwise.
     */
    public boolean shouldBeIgnoredBySecureSuggestionFromSameCarrier(
            @NonNull WifiConfiguration configuration, List<ScanDetail> scanDetails) {
        if (!mResources.getBoolean(
                R.bool.config_wifiIgnoreOpenSavedNetworkWhenSecureSuggestionAvailable)) {
            return false;
        }
        if (configuration == null || scanDetails == null || !configuration.isOpenNetwork()) {
            return false;
        }
        Set<ExtendedWifiNetworkSuggestion> matchedExtSuggestions =
                getNetworkSuggestionsForWifiConfiguration(configuration, null);
        if (matchedExtSuggestions == null || matchedExtSuggestions.isEmpty()) {
            return false;
        }
        matchedExtSuggestions = matchedExtSuggestions.stream().filter(ewns ->
                mWifiPermissionsUtil.checkNetworkCarrierProvisioningPermission(ewns.perAppInfo.uid))
                .collect(Collectors.toSet());
        if (matchedExtSuggestions.isEmpty()) {
            return false;
        }
        for (ExtendedWifiNetworkSuggestion ewns : matchedExtSuggestions) {
            if (hasSecureSuggestionFromSameCarrierAvailable(ewns, scanDetails)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check the suggestion user approval status.
     */
    private  @WifiManager.SuggestionUserApprovalStatus int getNetworkSuggestionUserApprovalStatus(
            int uid, String packageName) {
        if (mAppOps.unsafeCheckOpNoThrow(OPSTR_CHANGE_WIFI_STATE, uid, packageName)
                == AppOpsManager.MODE_IGNORED) {
            return WifiManager.STATUS_SUGGESTION_APPROVAL_REJECTED_BY_USER;
        }
        if (!mActiveNetworkSuggestionsPerApp.containsKey(packageName)) {
            return WifiManager.STATUS_SUGGESTION_APPROVAL_UNKNOWN;
        }
        PerAppInfo info = mActiveNetworkSuggestionsPerApp.get(packageName);
        if (info.hasUserApproved) {
            return WifiManager.STATUS_SUGGESTION_APPROVAL_APPROVED_BY_USER;
        }
        if (info.carrierId != TelephonyManager.UNKNOWN_CARRIER_ID) {
            return WifiManager.STATUS_SUGGESTION_APPROVAL_APPROVED_BY_CARRIER_PRIVILEGE;
        }
        return WifiManager.STATUS_SUGGESTION_APPROVAL_PENDING;
    }

    private boolean hasSecureSuggestionFromSameCarrierAvailable(
            ExtendedWifiNetworkSuggestion extendedWifiNetworkSuggestion,
            List<ScanDetail> scanDetails) {
        boolean isOpenSuggestionMetered = WifiConfiguration.isMetered(
                extendedWifiNetworkSuggestion.wns.wifiConfiguration, null);
        Set<ExtendedWifiNetworkSuggestion> secureExtSuggestions = new HashSet<>();
        for (ExtendedWifiNetworkSuggestion ewns : extendedWifiNetworkSuggestion.perAppInfo
                .extNetworkSuggestions.values()) {
            // Open network and auto-join disable suggestion, ignore.
            if (isOpenSuggestion(ewns) || !ewns.isAutojoinEnabled) {
                continue;
            }
            // From different carrier as open suggestion, ignore.
            if (getCarrierIdFromSuggestion(ewns)
                    != getCarrierIdFromSuggestion(extendedWifiNetworkSuggestion)) {
                continue;
            }
            // Secure and open has different meterness, ignore
            if (WifiConfiguration.isMetered(ewns.wns.wifiConfiguration, null)
                    != isOpenSuggestionMetered) {
                continue;
            }
            // Low priority than open suggestion, ignore.
            if (ewns.wns.wifiConfiguration.priority
                    < extendedWifiNetworkSuggestion.wns.wifiConfiguration.priority) {
                continue;
            }
            WifiConfiguration wcmConfig = mWifiConfigManager
                    .getConfiguredNetwork(ewns.wns.wifiConfiguration.getProfileKey());
            // Network selection is disabled, ignore.
            if (wcmConfig != null && !wcmConfig.getNetworkSelectionStatus().isNetworkEnabled()) {
                continue;
            }
            secureExtSuggestions.add(ewns);
        }

        if (secureExtSuggestions.isEmpty()) {
            return false;
        }
        List<ScanResult> scanResults = scanDetails.stream().map(ScanDetail::getScanResult)
                .collect(Collectors.toList());
        // Check if the secure suggestion is in the range.
        for (ExtendedWifiNetworkSuggestion ewns : secureExtSuggestions) {
            if (!getMatchingScanResultsForSuggestion(ewns.wns, scanResults).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Set the app treated as cross carrier provider. That can suggest for any carrier
     * @param packageName App name to set.
     * @param enabled True to set app treated as cross carrier provider, false otherwise.
     */
    public void setAppWorkingAsCrossCarrierProvider(String packageName, boolean enabled) {
        if (enabled) {
            mCrossCarrierProvidersSet.add(packageName);
        } else {
            mCrossCarrierProvidersSet.remove(packageName);
        }
    }

    /**
     * Check whether the app is treated as a cross carrier provider or not.
     * @param packageName App name to check
     * @return True for app is treated as a carrier provider, false otherwise.
     */
    public boolean isAppWorkingAsCrossCarrierProvider(String packageName) {
        return mCrossCarrierProvidersSet.contains(packageName);
    }

    /**
     * Store Anonymous Identity for SIM based suggestion after connection.
     */
    public void setAnonymousIdentity(WifiConfiguration config) {
        if (config.isPasspoint() || !config.fromWifiNetworkSuggestion) {
            return;
        }
        if (config.enterpriseConfig == null
                || !config.enterpriseConfig.isAuthenticationSimBased()) {
            Log.e(TAG, "Network is not SIM based, AnonymousIdentity is invalid");
        }
        Set<ExtendedWifiNetworkSuggestion> matchedSuggestionSet =
                getMatchedSuggestionsWithSameProfileKey(
                        getNetworkSuggestionsForWifiConfiguration(config, config.BSSID), config);
        if (matchedSuggestionSet.isEmpty()) {
            Log.wtf(TAG, "Current connected SIM based network suggestion is missing!");
            return;
        }
        for (ExtendedWifiNetworkSuggestion ewns : matchedSuggestionSet) {
            ewns.anonymousIdentity = config.enterpriseConfig.getAnonymousIdentity();
        }
        saveToStore();
    }

    private boolean isOpenSuggestion(ExtendedWifiNetworkSuggestion extendedWifiNetworkSuggestion) {
        if (extendedWifiNetworkSuggestion.wns.passpointConfiguration != null) {
            return false;
        }
        return extendedWifiNetworkSuggestion.wns.wifiConfiguration.isOpenNetwork();
    }

    private void onUserConnectChoiceSetForSuggestion(Collection<WifiConfiguration> networks,
            String choiceKey, int rssi) {
        Set<String> networkKeys = networks.stream()
                .filter(config -> config.fromWifiNetworkSuggestion)
                .map(WifiConfiguration::getProfileKey)
                .collect(Collectors.toSet());
        mActiveNetworkSuggestionsPerApp.values().stream()
                .flatMap(e -> e.extNetworkSuggestions.values().stream())
                .forEach(ewns -> {
                    String profileKey = ewns
                            .createInternalWifiConfiguration(mWifiCarrierInfoManager)
                            .getProfileKey();
                    if (TextUtils.equals(profileKey, choiceKey)) {
                        ewns.connectChoice = null;
                        ewns.connectChoiceRssi = 0;
                    } else if (networkKeys.contains(profileKey)) {
                        ewns.connectChoice = choiceKey;
                        ewns.connectChoiceRssi = rssi;
                    }
                });
        saveToStore();
    }

    private void onUserConnectChoiceRemoveForSuggestion(String choiceKey) {
        if (mActiveNetworkSuggestionsPerApp.values().stream()
                .flatMap(e -> e.extNetworkSuggestions.values().stream())
                .filter(ewns -> TextUtils.equals(ewns.connectChoice, choiceKey))
                .peek(ewns -> {
                    ewns.connectChoice = null;
                    ewns.connectChoiceRssi = 0;
                }).count() == 0) {
            return;
        }
        saveToStore();
    }

    private void onSuggestionUserApprovalStatusChanged(int uid, String packageName) {
        RemoteCallbackList<ISuggestionUserApprovalStatusListener> listenersTracker =
                mSuggestionUserApprovalStatusListenerPerApp.get(packageName);
        if (listenersTracker == null || listenersTracker.getRegisteredCallbackCount() == 0) {
            return;
        }

        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "Sending user approval status change event to " + packageName);
        }
        int itemCount = listenersTracker.beginBroadcast();
        for (int i = 0; i < itemCount; i++) {
            try {
                listenersTracker.getBroadcastItem(i).onUserApprovalStatusChange(
                        getNetworkSuggestionUserApprovalStatus(uid, packageName));
            } catch (RemoteException e) {
                Log.e(TAG, "sendUserApprovalStatusChange: remote exception -- " + e);
            }
        }
        listenersTracker.finishBroadcast();
    }

    private boolean areCarrierMergedSuggestionsAllowed(WifiConfiguration config,
            String packageName) {
        if (isAppWorkingAsCrossCarrierProvider(packageName)) {
            return true;
        }
        int subId = config.subscriptionId;
        if (config.getSubscriptionGroup() != null) {
            subId = mWifiCarrierInfoManager.getActiveSubscriptionIdInGroup(
                    config.getSubscriptionGroup());
        }

        return mWifiCarrierInfoManager.areMergedCarrierWifiNetworksAllowed(subId);
    }

    /**
     * Dump of {@link WifiNetworkSuggestionsManager}.
     */
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("Dump of WifiNetworkSuggestionsManager");
        pw.println("WifiNetworkSuggestionsManager - Networks Begin ----");
        for (Map.Entry<String, PerAppInfo> networkSuggestionsEntry
                : mActiveNetworkSuggestionsPerApp.entrySet()) {
            pw.println("Package Name: " + networkSuggestionsEntry.getKey());
            PerAppInfo appInfo = networkSuggestionsEntry.getValue();
            pw.println("Has user approved: " + appInfo.hasUserApproved);
            pw.println("Has carrier privileges: "
                    + (appInfo.carrierId != TelephonyManager.UNKNOWN_CARRIER_ID));
            for (ExtendedWifiNetworkSuggestion extNetworkSuggestion
                    : appInfo.extNetworkSuggestions.values()) {
                pw.println("Network: " + extNetworkSuggestion);
            }
        }
        pw.println("WifiNetworkSuggestionsManager - Networks End ----");
    }

    public void resetNotification() {
        mNotificationManager.cancel(SystemMessage.NOTE_NETWORK_SUGGESTION_AVAILABLE);
        mNotificationUpdateTime = 0;
    }

    private int getLingerDelayMs() {
        return SystemProperties.getInt(LINGER_DELAY_PROPERTY, DEFAULT_LINGER_DELAY_MS);
    }

    private void onSecurityParamsUpdateForSuggestion(WifiConfiguration config,
            List<SecurityParams> securityParams) {
        Set<ExtendedWifiNetworkSuggestion> matchingExtendedWifiNetworkSuggestions =
                        getNetworkSuggestionsForWifiConfiguration(config, config.BSSID);
        if (matchingExtendedWifiNetworkSuggestions == null
                || matchingExtendedWifiNetworkSuggestions.isEmpty()) {
            if (mVerboseLoggingEnabled) {
                Log.w(TAG, "onSecurityParamsUpdateForSuggestion: no network matches: " + config);
            }
            return;
        }
        for (ExtendedWifiNetworkSuggestion ewns : matchingExtendedWifiNetworkSuggestions) {
            removeFromScanResultMatchInfoMapAndRemoveRelatedScoreCard(ewns, false);
            ewns.wns.wifiConfiguration.setSecurityParams(securityParams);
            addToScanResultMatchInfoMap(ewns);
        }
        saveToStore();
    }
}
