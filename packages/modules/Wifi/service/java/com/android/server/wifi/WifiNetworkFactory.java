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

import static android.net.wifi.WifiScanner.WIFI_BAND_ALL;
import static android.net.wifi.WifiScanner.WIFI_BAND_UNSPECIFIED;

import static com.android.internal.util.Preconditions.checkNotNull;
import static com.android.server.wifi.ActiveModeManager.ROLE_CLIENT_LOCAL_ONLY;
import static com.android.server.wifi.ActiveModeManager.ROLE_CLIENT_PRIMARY;
import static com.android.server.wifi.WifiMetrics.ConnectionEvent.FAILURE_AUTHENTICATION_FAILURE;
import static com.android.server.wifi.proto.nano.WifiMetricsProto.ConnectionEvent.AUTH_FAILURE_WRONG_PSWD;
import static com.android.server.wifi.proto.nano.WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN;
import static com.android.server.wifi.util.NativeUtil.addEnclosingQuotes;

import static java.lang.Math.toIntExact;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.AppOpsManager;
import android.companion.CompanionDeviceManager;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.MacAddress;
import android.net.NetworkCapabilities;
import android.net.NetworkFactory;
import android.net.NetworkRequest;
import android.net.NetworkSpecifier;
import android.net.wifi.IActionListener;
import android.net.wifi.ILocalOnlyConnectionStatusListener;
import android.net.wifi.INetworkRequestMatchCallback;
import android.net.wifi.INetworkRequestUserSelectionCallback;
import android.net.wifi.ScanResult;
import android.net.wifi.SecurityParams;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiConfiguration.SecurityType;
import android.net.wifi.WifiContext;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSpecifier;
import android.net.wifi.WifiScanner;
import android.net.wifi.util.ScanResultUtil;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PatternMatcher;
import android.os.Process;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.WorkSource;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.Log;
import android.util.Pair;

import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.HandlerExecutor;
import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.proto.nano.WifiMetricsProto;
import com.android.server.wifi.util.ActionListenerWrapper;
import com.android.server.wifi.util.WifiPermissionsUtil;
import com.android.wifi.resources.R;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Network factory to handle trusted wifi network requests.
 */
public class WifiNetworkFactory extends NetworkFactory {
    private static final String TAG = "WifiNetworkFactory";
    @VisibleForTesting
    private static final int SCORE_FILTER = 60;
    @VisibleForTesting
    public static final int CACHED_SCAN_RESULTS_MAX_AGE_IN_MILLIS = 30 * 1000;
    @VisibleForTesting
    public static final int PERIODIC_SCAN_INTERVAL_MS = 10 * 1000; // 10 seconds
    @VisibleForTesting
    public static final int NETWORK_CONNECTION_TIMEOUT_MS = 30 * 1000; // 30 seconds
    @VisibleForTesting
    public static final int USER_SELECTED_NETWORK_CONNECT_RETRY_MAX = 3; // max of 3 retries.
    @VisibleForTesting
    public static final String UI_START_INTENT_ACTION =
            "com.android.settings.wifi.action.NETWORK_REQUEST";
    @VisibleForTesting
    public static final String UI_START_INTENT_CATEGORY = "android.intent.category.DEFAULT";
    @VisibleForTesting
    public static final String UI_START_INTENT_EXTRA_APP_NAME =
            "com.android.settings.wifi.extra.APP_NAME";
    @VisibleForTesting
    public static final String UI_START_INTENT_EXTRA_REQUEST_IS_FOR_SINGLE_NETWORK =
            "com.android.settings.wifi.extra.REQUEST_IS_FOR_SINGLE_NETWORK";
    // Capacity limit of approved Access Point per App
    @VisibleForTesting
    public static final int NUM_OF_ACCESS_POINT_LIMIT_PER_APP = 50;

    private final WifiContext mContext;
    private final ActivityManager mActivityManager;
    private final AlarmManager mAlarmManager;
    private final AppOpsManager mAppOpsManager;
    private final Clock mClock;
    private final Handler mHandler;
    private final WifiInjector mWifiInjector;
    private final WifiConnectivityManager mWifiConnectivityManager;
    private final WifiConfigManager mWifiConfigManager;
    private final WifiConfigStore mWifiConfigStore;
    private final WifiPermissionsUtil mWifiPermissionsUtil;
    private final WifiMetrics mWifiMetrics;
    private final WifiNative mWifiNative;
    private final ActiveModeWarden mActiveModeWarden;
    private final WifiScanner.ScanSettings mScanSettings;
    private final NetworkFactoryScanListener mScanListener;
    private final PeriodicScanAlarmListener mPeriodicScanTimerListener;
    private final ConnectionTimeoutAlarmListener mConnectionTimeoutAlarmListener;
    private final ConnectHelper mConnectHelper;
    private final ClientModeImplMonitor mClientModeImplMonitor;
    private final FrameworkFacade mFacade;
    private final MultiInternetManager mMultiInternetManager;
    private final NetworkCapabilities mCapabilitiesFilter;
    private RemoteCallbackList<INetworkRequestMatchCallback> mRegisteredCallbacks;
    // Store all user approved access points for apps.
    @VisibleForTesting
    public final Map<String, LinkedHashSet<AccessPoint>> mUserApprovedAccessPointMap;
    private WifiScanner mWifiScanner;
    @Nullable private ClientModeManager mClientModeManager;
    @Nullable private ActiveModeManager.ClientRole mClientModeManagerRole;
    private CompanionDeviceManager mCompanionDeviceManager;
    // Temporary approval set by shell commands.
    @Nullable private String mApprovedApp = null;

    private int mGenericConnectionReqCount = 0;
    // Request that is being actively processed. All new requests start out as an "active" request
    // because we're processing it & handling all the user interactions associated with it. Once we
    // successfully connect to the network, we transition that request to "connected".
    @Nullable private NetworkRequest mActiveSpecificNetworkRequest;
    @Nullable private WifiNetworkSpecifier mActiveSpecificNetworkRequestSpecifier;
    private boolean mSkipUserDialogue;
    // Request corresponding to the the network that the device is currently connected to.
    @Nullable private NetworkRequest mConnectedSpecificNetworkRequest;
    @Nullable private WifiNetworkSpecifier mConnectedSpecificNetworkRequestSpecifier;
    @Nullable private WifiConfiguration mUserSelectedNetwork;
    private boolean mShouldHaveInternetCapabilities = false;
    private Set<Integer> mConnectedUids = new ArraySet<>();
    private int mUserSelectedNetworkConnectRetryCount;
    // Map of bssid to latest scan results for all scan results matching a request. Will be
    //  - null, if there are no active requests.
    //  - empty, if there are no matching scan results received for the active request.
    @Nullable private Map<String, ScanResult> mActiveMatchedScanResults;
    /** Connection start time to keep track of connection duration */
    private long mConnectionStartTimeMillis = -1L;
    /**
     * CMI listener used for concurrent connection metrics collection.
     * Not used when the connection is on primary STA (i.e not STA + STA).
     */
    @Nullable private CmiListener mCmiListener;
    // Verbose logging flag.
    private boolean mVerboseLoggingEnabled = false;
    private boolean mPeriodicScanTimerSet = false;
    private boolean mConnectionTimeoutSet = false;
    private boolean mIsPeriodicScanEnabled = false;
    private boolean mIsPeriodicScanPaused = false;
    // We sent a new connection request and are waiting for connection success.
    private boolean mPendingConnectionSuccess = false;
    /**
     * Indicates that we have new data to serialize.
     */
    private boolean mHasNewDataToSerialize = false;

    private final HashMap<String, RemoteCallbackList<ILocalOnlyConnectionStatusListener>>
            mLocalOnlyStatusListenerPerApp = new HashMap<>();
    private final HashMap<String, String> mFeatureIdPerApp = new HashMap<>();

    /**
     * Helper class to store an access point that the user previously approved for a specific app.
     * TODO(b/123014687): Move to a common util class.
     */
    public static class AccessPoint {
        public final String ssid;
        public final MacAddress bssid;
        public final @SecurityType int networkType;

        AccessPoint(@NonNull String ssid, @NonNull MacAddress bssid,
                @SecurityType int networkType) {
            this.ssid = ssid;
            this.bssid = bssid;
            this.networkType = networkType;
        }

        @Override
        public int hashCode() {
            return Objects.hash(ssid, bssid, networkType);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof AccessPoint)) {
                return false;
            }
            AccessPoint other = (AccessPoint) obj;
            return TextUtils.equals(this.ssid, other.ssid)
                    && Objects.equals(this.bssid, other.bssid)
                    && this.networkType == other.networkType;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("AccessPoint: ");
            return sb.append(ssid)
                    .append(", ")
                    .append(bssid)
                    .append(", ")
                    .append(networkType)
                    .toString();
        }
    }

    // Scan listener for scan requests.
    private class NetworkFactoryScanListener implements WifiScanner.ScanListener {
        @Override
        public void onSuccess() {
            // Scan request succeeded, wait for results to report to external clients.
            if (mVerboseLoggingEnabled) {
                Log.d(TAG, "Scan request succeeded");
            }
        }

        @Override
        public void onFailure(int reason, String description) {
            Log.e(TAG, "Scan failure received. reason: " + reason
                    + ", description: " + description);
            // TODO(b/113878056): Retry scan to workaround any transient scan failures.
            scheduleNextPeriodicScan();
        }

        @Override
        public void onResults(WifiScanner.ScanData[] scanDatas) {
            if (mVerboseLoggingEnabled) {
                Log.d(TAG, "Scan results received");
            }
            // For single scans, the array size should always be 1.
            if (scanDatas.length != 1) {
                Log.wtf(TAG, "Found more than 1 batch of scan results, Ignoring...");
                return;
            }
            WifiScanner.ScanData scanData = scanDatas[0];
            ScanResult[] scanResults = scanData.getResults();
            if (mVerboseLoggingEnabled) {
                Log.v(TAG, "Received " + scanResults.length + " scan results");
            }
            handleScanResults(scanResults);
            if (!mSkipUserDialogue && mActiveMatchedScanResults != null) {
                sendNetworkRequestMatchCallbacksForActiveRequest(
                        mActiveMatchedScanResults.values());
            }
            scheduleNextPeriodicScan();
        }

        @Override
        public void onFullResult(ScanResult fullScanResult) {
            // Ignore for single scans.
        }

        @Override
        public void onPeriodChanged(int periodInMs) {
            // Ignore for single scans.
        }
    };

    private class PeriodicScanAlarmListener implements AlarmManager.OnAlarmListener {
        @Override
        public void onAlarm() {
            // Trigger the next scan.
            startScan();
            mPeriodicScanTimerSet = false;
        }
    }

    private class ConnectionTimeoutAlarmListener implements AlarmManager.OnAlarmListener {
        @Override
        public void onAlarm() {
            Log.e(TAG, "Timed-out connecting to network");
            if (mUserSelectedNetwork != null) {
                handleNetworkConnectionFailure(mUserSelectedNetwork, mUserSelectedNetwork.BSSID,
                        WifiMetrics.ConnectionEvent.FAILURE_ASSOCIATION_TIMED_OUT,
                        FAILURE_REASON_UNKNOWN);
            } else {
                Log.wtf(TAG, "mUserSelectedNetwork is null, when connection time out");
            }
            mConnectionTimeoutSet = false;
        }
    }

    // Callback result from settings UI.
    private class NetworkFactoryUserSelectionCallback extends
            INetworkRequestUserSelectionCallback.Stub {
        private final NetworkRequest mNetworkRequest;

        NetworkFactoryUserSelectionCallback(NetworkRequest networkRequest) {
            mNetworkRequest = networkRequest;
        }

        @Override
        public void select(WifiConfiguration wifiConfiguration) {
            if (wifiConfiguration == null) {
                Log.wtf(TAG, "User select null config, seems a settings UI issue");
                return;
            }
            mHandler.post(() -> {
                Log.i(TAG, "select configuration " + wifiConfiguration);
                if (mActiveSpecificNetworkRequest != mNetworkRequest) {
                    Log.e(TAG, "Stale callback select received");
                    return;
                }
                handleConnectToNetworkUserSelection(wifiConfiguration, true);
            });
        }

        @Override
        public void reject() {
            mHandler.post(() -> {
                if (mActiveSpecificNetworkRequest != mNetworkRequest) {
                    Log.e(TAG, "Stale callback reject received");
                    return;
                }
                handleRejectUserSelection();
            });
        }
    }

    private final class ConnectActionListener extends IActionListener.Stub {
        @Override
        public void onSuccess() {
            if (mVerboseLoggingEnabled) {
                Log.v(TAG, "Triggered network connection");
            }
        }

        @Override
        public void onFailure(int reason) {
            Log.e(TAG, "Failed to trigger network connection");
            if (mUserSelectedNetwork == null) {
                Log.e(TAG, "mUserSelectedNetwork is null, when connection failure");
                return;
            }
            handleNetworkConnectionFailure(mUserSelectedNetwork, mUserSelectedNetwork.BSSID,
                    reason, FAILURE_REASON_UNKNOWN);
        }
    }

    private final class ClientModeManagerRequestListener implements
            ActiveModeWarden.ExternalClientModeManagerRequestListener {
        @Override
        public void onAnswer(@Nullable ClientModeManager modeManager) {
            if (modeManager != null) {
                // Remove the mode manager if the associated request is no longer active.
                if (mActiveSpecificNetworkRequest == null
                        && mConnectedSpecificNetworkRequest == null) {
                    Log.w(TAG, "Client mode manager request answer received with no active and "
                            + "connected requests, remove the manager");
                    mActiveModeWarden.removeClientModeManager(modeManager);
                    return;
                }
                if (mActiveSpecificNetworkRequest == null) {
                    Log.w(TAG, "Client mode manager request answer received with no active"
                            + " requests, but has connected request. ");
                    if (modeManager != mClientModeManager) {
                        // If clientModeManager changes, teardown the current connection
                        mActiveModeWarden.removeClientModeManager(modeManager);
                    }
                    return;
                }
                if (modeManager != mClientModeManager) {
                    // If clientModeManager changes, teardown the current connection
                    removeClientModeManagerIfNecessary();
                }
                mClientModeManager = modeManager;
                mClientModeManagerRole = modeManager.getRole();
                if (mVerboseLoggingEnabled) {
                    Log.v(TAG, "retrieve CMM: " + mClientModeManager.toString());
                }
                handleClientModeManagerRetrieval();
            } else {
                handleClientModeManagerRemovalOrFailure();
            }
        }
    }

    private class ModeChangeCallback implements ActiveModeWarden.ModeChangeCallback {
        @Override
        public void onActiveModeManagerAdded(@NonNull ActiveModeManager activeModeManager) {
            // ignored.
            // Will get a dedicated ClientModeManager instance for our request via
            // ClientModeManagerRequestListener.
        }

        @Override
        public void onActiveModeManagerRemoved(@NonNull ActiveModeManager activeModeManager) {
            if (!(activeModeManager instanceof ClientModeManager)) return;
            if (mVerboseLoggingEnabled) {
                Log.v(TAG, "ModeManager removed " + activeModeManager.getInterfaceName());
            }
            // Mode manager removed. Cleanup any ongoing requests.
            if (activeModeManager == mClientModeManager
                    || !mActiveModeWarden.hasPrimaryClientModeManager()) {
                handleClientModeManagerRemovalOrFailure();
            }
        }

        @Override
        public void onActiveModeManagerRoleChanged(@NonNull ActiveModeManager activeModeManager) {
            if (!(activeModeManager instanceof ClientModeManager)) return;
            if (mVerboseLoggingEnabled) {
                Log.v(TAG, "ModeManager role changed " + activeModeManager.getInterfaceName());
            }
            // Mode manager role changed. Cleanup any ongoing requests.
            if (activeModeManager == mClientModeManager
                    || !mActiveModeWarden.hasPrimaryClientModeManager()) {
                handleClientModeManagerRemovalOrFailure();
            }
        }
    }

    /**
     * Module to interact with the wifi config store.
     */
    private class NetworkRequestDataSource implements NetworkRequestStoreData.DataSource {
        @Override
        public Map<String, Set<AccessPoint>> toSerialize() {
            // Clear the flag after writing to disk.
            mHasNewDataToSerialize = false;
            return new HashMap<>(mUserApprovedAccessPointMap);
        }

        @Override
        public void fromDeserialized(Map<String, Set<AccessPoint>> approvedAccessPointMap) {
            approvedAccessPointMap.forEach((key, value) ->
                    mUserApprovedAccessPointMap.put(key, new LinkedHashSet<>(value)));
        }

        @Override
        public void reset() {
            mUserApprovedAccessPointMap.clear();
        }

        @Override
        public boolean hasNewDataToSerialize() {
            return mHasNewDataToSerialize;
        }
    }

    /**
     * To keep track of concurrent connections using this API surface (for metrics collection only).
     *
     * Only used if the connection is initiated on secondary STA.
     */
    private class CmiListener implements ClientModeImplListener {
        /** Concurrent connection start time to keep track of connection duration */
        private long mConcurrentConnectionStartTimeMillis = -1L;
        /** Whether we have already indicated the presence of concurrent connection */
        private boolean mHasAlreadyIncrementedConcurrentConnectionCount = false;

        private boolean isLocalOnlyOrPrimary(@NonNull ClientModeManager cmm) {
            return cmm.getRole() == ROLE_CLIENT_PRIMARY
                    || cmm.getRole() == ROLE_CLIENT_LOCAL_ONLY;
        }

        private void checkForConcurrencyStartAndIncrementMetrics() {
            int numLocalOnlyOrPrimaryConnectedCmms = 0;
            for (ClientModeManager cmm : mActiveModeWarden.getClientModeManagers()) {
                if (isLocalOnlyOrPrimary(cmm) && cmm.isConnected()) {
                    numLocalOnlyOrPrimaryConnectedCmms++;
                }
            }
            if (numLocalOnlyOrPrimaryConnectedCmms > 1) {
                mConcurrentConnectionStartTimeMillis = mClock.getElapsedSinceBootMillis();
                // Note: We could have multiple connect/disconnect of the primary connection
                // while remaining connected to the local only connection. We want to keep track
                // of the connection durations accurately across those disconnects. However, we do
                // not want to increment the connection count metric since that should be a 1:1
                // mapping with the number of requests processed (i.e don't indicate 2 concurrent
                // connection count if the primary disconnected & connected back while processing
                // the same local only request).
                if (!mHasAlreadyIncrementedConcurrentConnectionCount) {
                    mWifiMetrics.incrementNetworkRequestApiNumConcurrentConnection();
                    mHasAlreadyIncrementedConcurrentConnectionCount = true;
                }
            }
        }

        public void checkForConcurrencyEndAndIncrementMetrics() {
            if (mConcurrentConnectionStartTimeMillis != -1L) {
                mWifiMetrics.incrementNetworkRequestApiConcurrentConnectionDurationSecHistogram(
                        toIntExact(TimeUnit.MILLISECONDS.toSeconds(
                                mClock.getElapsedSinceBootMillis()
                                        - mConcurrentConnectionStartTimeMillis)));
                mConcurrentConnectionStartTimeMillis = -1L;
            }
        }

        CmiListener() {
            checkForConcurrencyStartAndIncrementMetrics();
        }

        @Override
        public void onL3Connected(@NonNull ConcreteClientModeManager clientModeManager) {
            if (isLocalOnlyOrPrimary(clientModeManager)) {
                checkForConcurrencyStartAndIncrementMetrics();
            }
        }

        @Override
        public void onConnectionEnd(@NonNull ConcreteClientModeManager clientModeManager) {
            if (isLocalOnlyOrPrimary(clientModeManager)) {
                checkForConcurrencyEndAndIncrementMetrics();
            }
        }
    }

    public WifiNetworkFactory(Looper looper, WifiContext context, NetworkCapabilities nc,
            ActivityManager activityManager, AlarmManager alarmManager,
            AppOpsManager appOpsManager,
            Clock clock, WifiInjector wifiInjector,
            WifiConnectivityManager connectivityManager,
            WifiConfigManager configManager,
            WifiConfigStore configStore,
            WifiPermissionsUtil wifiPermissionsUtil,
            WifiMetrics wifiMetrics,
            WifiNative wifiNative,
            ActiveModeWarden activeModeWarden,
            ConnectHelper connectHelper,
            ClientModeImplMonitor clientModeImplMonitor,
            FrameworkFacade facade,
            MultiInternetManager multiInternetManager) {
        super(looper, context, TAG, nc);
        mContext = context;
        mActivityManager = activityManager;
        mAlarmManager = alarmManager;
        mAppOpsManager = appOpsManager;
        mClock = clock;
        mHandler = new Handler(looper);
        mWifiInjector = wifiInjector;
        mWifiConnectivityManager = connectivityManager;
        mWifiConfigManager = configManager;
        mWifiConfigStore = configStore;
        mWifiPermissionsUtil = wifiPermissionsUtil;
        mWifiMetrics = wifiMetrics;
        mWifiNative = wifiNative;
        mActiveModeWarden = activeModeWarden;
        mConnectHelper = connectHelper;
        mClientModeImplMonitor = clientModeImplMonitor;
        // Create the scan settings.
        mScanSettings = new WifiScanner.ScanSettings();
        mScanSettings.type = WifiScanner.SCAN_TYPE_HIGH_ACCURACY;
        mScanSettings.band = WifiScanner.WIFI_BAND_ALL;
        mScanSettings.reportEvents = WifiScanner.REPORT_EVENT_AFTER_EACH_SCAN;
        mScanListener = new NetworkFactoryScanListener();
        mPeriodicScanTimerListener = new PeriodicScanAlarmListener();
        mConnectionTimeoutAlarmListener = new ConnectionTimeoutAlarmListener();
        mUserApprovedAccessPointMap = new HashMap<>();
        mFacade = facade;
        mMultiInternetManager = multiInternetManager;
        mCapabilitiesFilter = nc;

        // register the data store for serializing/deserializing data.
        configStore.registerStoreData(
                wifiInjector.makeNetworkRequestStoreData(new NetworkRequestDataSource()));

        activeModeWarden.registerModeChangeCallback(new ModeChangeCallback());

        setScoreFilter(SCORE_FILTER);
        mWifiInjector
                .getWifiDeviceStateChangeManager()
                .registerStateChangeCallback(
                        new WifiDeviceStateChangeManager.StateChangeCallback() {
                            @Override
                            public void onScreenStateChanged(boolean screenOn) {
                                handleScreenStateChanged(screenOn);
                            }
                        });
    }

    // package-private
    @TargetApi(Build.VERSION_CODES.S)
    void updateSubIdsInCapabilitiesFilter(Set<Integer> subIds) {
        // setSubscriptionIds is only available on Android S+ devices.
        if (SdkLevel.isAtLeastS()) {
            NetworkCapabilities newFilter =
                    new NetworkCapabilities.Builder(mCapabilitiesFilter)
                            .setSubscriptionIds(subIds).build();
            setCapabilityFilter(newFilter);
        }
    }

    private void saveToStore() {
        // Set the flag to let WifiConfigStore that we have new data to write.
        mHasNewDataToSerialize = true;
        if (!mWifiConfigManager.saveToStore(true)) {
            Log.w(TAG, "Failed to save to store");
        }
    }

    /**
     * Enable verbose logging.
     */
    public void enableVerboseLogging(boolean verbose) {
        mVerboseLoggingEnabled = verbose;
    }

    /**
     * Add a new callback for network request match handling.
     */
    public void addCallback(INetworkRequestMatchCallback callback) {
        if (mActiveSpecificNetworkRequest == null) {
            Log.wtf(TAG, "No valid network request. Ignoring callback registration");
            try {
                callback.onAbort();
            } catch (RemoteException e) {
                Log.e(TAG, "Unable to invoke network request abort callback " + callback, e);
            }
            return;
        }
        if (mRegisteredCallbacks == null) {
            mRegisteredCallbacks = new RemoteCallbackList<>();
        }
        if (!mRegisteredCallbacks.register(callback)) {
            Log.e(TAG, "Failed to add callback");
            return;
        }
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "Adding callback. Num callbacks: "
                    + mRegisteredCallbacks.getRegisteredCallbackCount());
        }
        // Register our user selection callback.
        try {
            callback.onUserSelectionCallbackRegistration(
                    new NetworkFactoryUserSelectionCallback(mActiveSpecificNetworkRequest));
        } catch (RemoteException e) {
            Log.e(TAG, "Unable to invoke user selection registration callback " + callback, e);
            return;
        }

        // If we are already in the midst of processing a request, send matching callbacks
        // immediately on registering the callback.
        if (mActiveMatchedScanResults != null) {
            sendNetworkRequestMatchCallbacksForActiveRequest(
                    mActiveMatchedScanResults.values());
        }
    }

    /**
     * Remove an existing callback for network request match handling.
     */
    public void removeCallback(INetworkRequestMatchCallback callback) {
        if (mRegisteredCallbacks == null) return;
        mRegisteredCallbacks.unregister(callback);
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "Removing callback. Num callbacks: "
                    + mRegisteredCallbacks.getRegisteredCallbackCount());
        }
    }

    private boolean canNewRequestOverrideExistingRequest(
            NetworkRequest newRequest, NetworkRequest existingRequest) {
        if (existingRequest == null) return true;
        // Request from app with NETWORK_SETTINGS can override any existing requests.
        if (mWifiPermissionsUtil.checkNetworkSettingsPermission(newRequest.getRequestorUid())) {
            return true;
        }
        // Request from fg app can override any existing requests.
        if (mFacade.isRequestFromForegroundApp(mContext, newRequest.getRequestorPackageName())) {
            return true;
        }
        // Request from fg service can override only if the existing request is not from a fg app.
        if (!mFacade.isRequestFromForegroundApp(mContext,
                existingRequest.getRequestorPackageName())) {
            return true;
        }
        Log.e(TAG, "Already processing request from a foreground app "
                + existingRequest.getRequestorPackageName() + ". Rejecting request from "
                + newRequest.getRequestorPackageName());
        return false;
    }

    boolean isRequestWithWifiNetworkSpecifierValid(NetworkRequest networkRequest) {
        WifiNetworkSpecifier wns = (WifiNetworkSpecifier) networkRequest.getNetworkSpecifier();
        // Request cannot have internet capability since such a request can never be fulfilled.
        // (NetworkAgent for connection with WifiNetworkSpecifier will not have internet capability)
        if (networkRequest.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            Log.e(TAG, "Request with wifi network specifier cannot contain "
                    + "NET_CAPABILITY_INTERNET. Rejecting");
            return false;
        }
        if (networkRequest.getRequestorUid() == Process.INVALID_UID) {
            Log.e(TAG, "Request with wifi network specifier should contain valid uid. Rejecting");
            return false;
        }
        if (TextUtils.isEmpty(networkRequest.getRequestorPackageName())) {
            Log.e(TAG, "Request with wifi network specifier should contain valid package name."
                    + "Rejecting");
            return false;
        }
        try {
            mAppOpsManager.checkPackage(
                    networkRequest.getRequestorUid(), networkRequest.getRequestorPackageName());
        } catch (SecurityException e) {
            Log.e(TAG, "Invalid uid/package name " + networkRequest.getRequestorUid() + ", "
                    + networkRequest.getRequestorPackageName() + ". Rejecting", e);
            return false;
        }

        if (wns.getBand() != ScanResult.UNSPECIFIED) {
            Log.e(TAG, "Requesting specific frequency bands is not yet supported. Rejecting");
            return false;
        }
        if (!WifiConfigurationUtil.validateNetworkSpecifier(wns, mContext.getResources()
                .getInteger(R.integer.config_wifiNetworkSpecifierMaxPreferredChannels))) {
            Log.e(TAG, "Invalid wifi network specifier: " + wns + ". Rejecting ");
            return false;
        }
        if (wns.wifiConfiguration.enterpriseConfig != null
                && wns.wifiConfiguration.enterpriseConfig.isTrustOnFirstUseEnabled()) {
            Log.e(TAG, "Invalid wifi network specifier with TOFU enabled: " + wns + ". Rejecting ");
            return false;
        }
        return true;
    }

    /**
     * Check whether to accept the new network connection request.
     *
     * All the validation of the incoming request is done in this method.
     */
    @Override
    public boolean acceptRequest(NetworkRequest networkRequest) {
        NetworkSpecifier ns = networkRequest.getNetworkSpecifier();
        boolean isFromSetting = mWifiPermissionsUtil.checkNetworkSettingsPermission(
                networkRequest.getRequestorUid());
        if (ns == null) {
            // Generic wifi request. Always accept.
        } else {
            // Unsupported network specifier.
            if (!(ns instanceof WifiNetworkSpecifier)) {
                Log.e(TAG, "Unsupported network specifier: " + ns + ". Rejecting");
                return false;
            }
            // MultiInternet Request to be handled by MultiInternetWifiNetworkFactory.
            if (mMultiInternetManager.isStaConcurrencyForMultiInternetEnabled()
                    && MultiInternetWifiNetworkFactory.isWifiMultiInternetRequest(networkRequest,
                    isFromSetting)) {
                return false;
            }
            // Invalid request with wifi network specifier.
            if (!isRequestWithWifiNetworkSpecifierValid(networkRequest)) {
                Log.e(TAG, "Invalid network specifier: " + ns + ". Rejecting");
                releaseRequestAsUnfulfillableByAnyFactory(networkRequest);
                return false;
            }
            if (mWifiPermissionsUtil.isGuestUser()) {
                Log.e(TAG, "network specifier from guest user, reject");
                releaseRequestAsUnfulfillableByAnyFactory(networkRequest);
                return false;
            }
            if (Objects.equals(mActiveSpecificNetworkRequest, networkRequest)
                    || Objects.equals(mConnectedSpecificNetworkRequest, networkRequest)) {
                Log.e(TAG, "acceptRequest: Already processing the request " + networkRequest);
                return true;
            }
            // Only allow specific wifi network request from foreground app/service.
            if (!mWifiPermissionsUtil.checkNetworkSettingsPermission(
                    networkRequest.getRequestorUid())
                    && !mFacade.isRequestFromForegroundAppOrService(mContext,
                    networkRequest.getRequestorPackageName())) {
                Log.e(TAG, "Request not from foreground app or service."
                        + " Rejecting request from " + networkRequest.getRequestorPackageName());
                releaseRequestAsUnfulfillableByAnyFactory(networkRequest);
                return false;
            }
            // If there is an active request, only proceed if the new request is from a foreground
            // app.
            if (!canNewRequestOverrideExistingRequest(
                    networkRequest, mActiveSpecificNetworkRequest)) {
                Log.e(TAG, "Request cannot override active request."
                        + " Rejecting request from " + networkRequest.getRequestorPackageName());
                releaseRequestAsUnfulfillableByAnyFactory(networkRequest);
                return false;
            }
            // If there is a connected request, only proceed if the new request is from a foreground
            // app.
            if (!canNewRequestOverrideExistingRequest(
                    networkRequest, mConnectedSpecificNetworkRequest)) {
                Log.e(TAG, "Request cannot override connected request."
                        + " Rejecting request from " + networkRequest.getRequestorPackageName());
                releaseRequestAsUnfulfillableByAnyFactory(networkRequest);
                return false;
            }
            if (mVerboseLoggingEnabled) {
                Log.v(TAG, "Accepted network request with specifier from fg "
                        + (mFacade.isRequestFromForegroundApp(mContext,
                                networkRequest.getRequestorPackageName())
                        ? "app" : "service"));
            }
        }
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "Accepted network request " + networkRequest);
        }
        return true;
    }

    /**
     * Handle new network connection requests.
     *
     * The assumption here is that {@link #acceptRequest(NetworkRequest)} has already sanitized
     * the incoming request.
     */
    @Override
    protected void needNetworkFor(NetworkRequest networkRequest) {
        NetworkSpecifier ns = networkRequest.getNetworkSpecifier();
        boolean isFromSetting = mWifiPermissionsUtil.checkNetworkSettingsPermission(
                networkRequest.getRequestorUid());
        if (ns == null) {
            // Generic wifi request. Turn on auto-join if necessary.
            if (++mGenericConnectionReqCount == 1) {
                mWifiConnectivityManager.setTrustedConnectionAllowed(true);
            }
        } else {
            // Unsupported network specifier.
            if (!(ns instanceof WifiNetworkSpecifier)) {
                Log.e(TAG, "Unsupported network specifier: " + ns + ". Ignoring");
                return;
            }
            // MultiInternet Request to be handled by MultiInternetWifiNetworkFactory.
            if (mMultiInternetManager.isStaConcurrencyForMultiInternetEnabled()
                    && MultiInternetWifiNetworkFactory.isWifiMultiInternetRequest(networkRequest,
                    isFromSetting)) {
                return;
            }
            // Invalid request with wifi network specifier.
            if (!isRequestWithWifiNetworkSpecifierValid(networkRequest)) {
                Log.e(TAG, "Invalid network specifier: " + ns + ". Rejecting");
                releaseRequestAsUnfulfillableByAnyFactory(networkRequest);
                return;
            }
            if (mWifiPermissionsUtil.isGuestUser()) {
                Log.e(TAG, "network specifier from guest user, reject");
                releaseRequestAsUnfulfillableByAnyFactory(networkRequest);
                return;
            }
            // Wifi-off abort early.
            if (!mActiveModeWarden.hasPrimaryClientModeManager()) {
                Log.e(TAG, "Request with wifi network specifier when wifi is off."
                        + "Rejecting");
                releaseRequestAsUnfulfillableByAnyFactory(networkRequest);
                return;
            }
            if (Objects.equals(mActiveSpecificNetworkRequest, networkRequest)
                    || Objects.equals(mConnectedSpecificNetworkRequest, networkRequest)) {
                Log.e(TAG, "needNetworkFor: Already processing the request " + networkRequest);
                return;
            }

            retrieveWifiScanner();
            // Reset state from any previous request.
            setupForActiveRequest();
            // Store the active network request.
            mActiveSpecificNetworkRequest = networkRequest;
            WifiNetworkSpecifier wns = (WifiNetworkSpecifier) ns;
            mActiveSpecificNetworkRequestSpecifier = new WifiNetworkSpecifier(
                    wns.ssidPatternMatcher, wns.bssidPatternMatcher, wns.getBand(),
                    wns.wifiConfiguration, wns.getPreferredChannelFrequenciesMhz());
            mSkipUserDialogue = false;
            mWifiMetrics.incrementNetworkRequestApiNumRequest();

            // special case for STA+STA: since we are not allowed to replace the primary STA we
            // should check if we are able to get an interface for a secondary STA. If not - we
            // want to escalate and display the dialog to the user EVEN if we have a normal bypass
            // (normal == user approved before, if the app has full UI bypass we won't override it)
            boolean revokeNormalBypass = false;
            if (mContext.getResources().getBoolean(
                    R.bool.config_wifiMultiStaLocalOnlyConcurrencyEnabled)
                    && !mWifiPermissionsUtil.isTargetSdkLessThan(
                    mActiveSpecificNetworkRequest.getRequestorPackageName(), Build.VERSION_CODES.S,
                    mActiveSpecificNetworkRequest.getRequestorUid())
                    && mClientModeManager == null) {
                revokeNormalBypass = !mWifiNative.isItPossibleToCreateStaIface(
                        new WorkSource(mActiveSpecificNetworkRequest.getRequestorUid(),
                                mActiveSpecificNetworkRequest.getRequestorPackageName()));
            }

            ScanResult[] cachedScanResults = getFilteredCachedScanResults();
            if (!triggerConnectIfUserApprovedMatchFound(revokeNormalBypass, cachedScanResults)) {
                // Didn't find an approved match, send the matching results to UI and trigger
                // periodic scans for finding a network in the request.
                // Fetch the latest cached scan results to speed up network matching.

                if (mVerboseLoggingEnabled) {
                    Log.v(TAG, "Using cached " + cachedScanResults.length + " scan results");
                }
                handleScanResults(cachedScanResults);
                // Start UI to let the user grant/disallow this request from the app.
                if (!mSkipUserDialogue) {
                    startUi();
                    if (mActiveMatchedScanResults != null) {
                        sendNetworkRequestMatchCallbacksForActiveRequest(
                                mActiveMatchedScanResults.values());
                    }
                }
                startPeriodicScans();
            }
        }
    }

    @Override
    protected void releaseNetworkFor(NetworkRequest networkRequest) {
        NetworkSpecifier ns = networkRequest.getNetworkSpecifier();
        if (ns == null) {
            // Generic wifi request. Turn off auto-join if necessary.
            if (mGenericConnectionReqCount == 0) {
                Log.e(TAG, "No valid network request to release");
                return;
            }
            if (--mGenericConnectionReqCount == 0) {
                mWifiConnectivityManager.setTrustedConnectionAllowed(false);
            }
        } else {
            // Unsupported network specifier.
            if (!(ns instanceof WifiNetworkSpecifier)) {
                Log.e(TAG, "Unsupported network specifier mentioned. Ignoring");
                return;
            }
            if (mActiveSpecificNetworkRequest == null && mConnectedSpecificNetworkRequest == null) {
                Log.e(TAG, "Network release received with no active/connected request."
                        + " Ignoring");
                return;
            }
            if (Objects.equals(mActiveSpecificNetworkRequest, networkRequest)) {
                Log.i(TAG, "App released active request, cancelling "
                        + mActiveSpecificNetworkRequest);
                teardownForActiveRequest();
            } else if (Objects.equals(mConnectedSpecificNetworkRequest, networkRequest)) {
                Log.i(TAG, "App released connected request, cancelling "
                        + mConnectedSpecificNetworkRequest);
                teardownForConnectedNetwork();
            } else {
                Log.e(TAG, "Network specifier does not match the active/connected request."
                        + " Ignoring");
            }
        }
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        super.dump(fd, pw, args);
        pw.println(TAG + ": mGenericConnectionReqCount " + mGenericConnectionReqCount);
        pw.println(TAG + ": mActiveSpecificNetworkRequest " + mActiveSpecificNetworkRequest);
        pw.println(TAG + ": mUserApprovedAccessPointMap " + mUserApprovedAccessPointMap);
    }

    /**
     * Check if there is at least one connection request.
     */
    public boolean hasConnectionRequests() {
        return mGenericConnectionReqCount > 0 || mActiveSpecificNetworkRequest != null
                || mConnectedSpecificNetworkRequest != null;
    }

    /**
     * Return the uid of the specific network request being processed if connected to the requested
     * network.
     *
     * @param connectedNetwork WifiConfiguration corresponding to the connected network.
     * @return Pair of uid & package name of the specific request (if any), else <-1, "">.
     */
    public Pair<Integer, String> getSpecificNetworkRequestUidAndPackageName(
            @NonNull WifiConfiguration connectedNetwork, @NonNull String connectedBssid) {
        if (mUserSelectedNetwork == null || connectedNetwork == null) {
            return Pair.create(Process.INVALID_UID, "");
        }
        if (!isUserSelectedNetwork(connectedNetwork, connectedBssid)) {
            Log.w(TAG, "Connected to unknown network " + connectedNetwork + ":" + connectedBssid
                    + ". Ignoring...");
            return Pair.create(Process.INVALID_UID, "");
        }
        if (mConnectedSpecificNetworkRequestSpecifier != null) {
            return Pair.create(mConnectedSpecificNetworkRequest.getRequestorUid(),
                    mConnectedSpecificNetworkRequest.getRequestorPackageName());
        }
        if (mActiveSpecificNetworkRequestSpecifier != null) {
            return Pair.create(mActiveSpecificNetworkRequest.getRequestorUid(),
                    mActiveSpecificNetworkRequest.getRequestorPackageName());
        }
        return Pair.create(Process.INVALID_UID, "");
    }

    /**
     * Return the uids of the specific network request being processed if connected to the requested
     * network.
     *
     * @param connectedNetwork WifiConfiguration corresponding to the connected network.
     * @return Set of uids which request this network
     */
    public Set<Integer> getSpecificNetworkRequestUids(
            @NonNull WifiConfiguration connectedNetwork, @NonNull String connectedBssid) {
        if (mUserSelectedNetwork == null || connectedNetwork == null) {
            return Collections.emptySet();
        }
        if (!isUserSelectedNetwork(connectedNetwork, connectedBssid)) {
            Log.w(TAG, "Connected to unknown network " + connectedNetwork + ":" + connectedBssid
                    + ". Ignoring...");
            return Collections.emptySet();
        }
        if (mConnectedSpecificNetworkRequestSpecifier != null) {
            return mConnectedUids;
        }
        if (mActiveSpecificNetworkRequestSpecifier != null) {
            return Set.of(mActiveSpecificNetworkRequest.getRequestorUid());
        }
        return Collections.emptySet();
    }

    /**
     * Return whether if current network request should have the internet capabilities due to a
     * same saved/suggestion network is present.
     */
    public boolean shouldHaveInternetCapabilities() {
        return mShouldHaveInternetCapabilities;
    }

    // Helper method to add the provided network configuration to WifiConfigManager, if it does not
    // already exist & return the allocated network ID. This ID will be used in the CONNECT_NETWORK
    // request to ClientModeImpl.
    // If the network already exists, just return the network ID of the existing network.
    private int addNetworkToWifiConfigManager(@NonNull WifiConfiguration network) {
        WifiConfiguration existingSavedNetwork =
                mWifiConfigManager.getConfiguredNetwork(network.getProfileKey());
        if (existingSavedNetwork != null) {
            if (WifiConfigurationUtil.hasCredentialChanged(existingSavedNetwork, network)) {
                // TODO (b/142035508): What if the user has a saved network with different
                // credentials?
                Log.w(TAG, "Network config already present in config manager, reusing");
            }
            return existingSavedNetwork.networkId;
        }
        NetworkUpdateResult networkUpdateResult =
                mWifiConfigManager.addOrUpdateNetwork(
                        network, mActiveSpecificNetworkRequest.getRequestorUid(),
                        mActiveSpecificNetworkRequest.getRequestorPackageName(), false);
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "Added network to config manager " + networkUpdateResult.getNetworkId());
        }
        return networkUpdateResult.getNetworkId();
    }

    // Helper method to remove the provided network configuration from WifiConfigManager, if it was
    // added by an app's specifier request.
    private void disconnectAndRemoveNetworkFromWifiConfigManager(
            @Nullable WifiConfiguration network) {
        // Trigger a disconnect first.
        if (mClientModeManager != null) mClientModeManager.disconnect();

        if (network == null) return;
        WifiConfiguration wcmNetwork =
                mWifiConfigManager.getConfiguredNetwork(network.getProfileKey());
        if (wcmNetwork == null) {
            Log.e(TAG, "Network not present in config manager");
            return;
        }
        // Remove the network if it was added previously by an app's specifier request.
        if (wcmNetwork.ephemeral && wcmNetwork.fromWifiNetworkSpecifier) {
            boolean success =
                    mWifiConfigManager.removeNetwork(
                            wcmNetwork.networkId, wcmNetwork.creatorUid, wcmNetwork.creatorName);
            if (!success) {
                Log.e(TAG, "Failed to remove network from config manager");
            } else if (mVerboseLoggingEnabled) {
                Log.v(TAG, "Removed network from config manager " + wcmNetwork.networkId);
            }
        }
    }

    // Helper method to trigger a connection request & schedule a timeout alarm to track the
    // connection request.
    private void connectToNetwork(@NonNull WifiConfiguration network) {
        // Cancel connection timeout alarm for any previous connection attempts.
        cancelConnectionTimeout();

        // First add the network to WifiConfigManager and then use the obtained networkId
        // in the CONNECT_NETWORK request.
        // Note: We don't do any error checks on the networkId because ClientModeImpl will do the
        // necessary checks when processing CONNECT_NETWORK.
        int networkId = addNetworkToWifiConfigManager(network);

        mWifiMetrics.setNominatorForNetwork(networkId,
                WifiMetricsProto.ConnectionEvent.NOMINATOR_SPECIFIER);
        if (mClientModeManagerRole == ROLE_CLIENT_PRIMARY) {
            mWifiMetrics.incrementNetworkRequestApiNumConnectOnPrimaryIface();
        } else {
            mWifiMetrics.incrementNetworkRequestApiNumConnectOnSecondaryIface();
        }

        // Send the connect request to ClientModeImpl.
        // TODO(b/117601161): Refactor this.
        ConnectActionListener listener = new ConnectActionListener();
        mConnectHelper.connectToNetwork(
                mClientModeManager,
                new NetworkUpdateResult(networkId),
                new ActionListenerWrapper(listener),
                mActiveSpecificNetworkRequest.getRequestorUid(),
                mActiveSpecificNetworkRequest.getRequestorPackageName(), null);

        // Post an alarm to handle connection timeout.
        scheduleConnectionTimeout();
    }

    private void handleConnectToNetworkUserSelectionInternal(WifiConfiguration network,
            boolean didUserSeeUi) {
        // Copy over the credentials from the app's request and then copy the ssid from user
        // selection.
        WifiConfiguration networkToConnect =
                new WifiConfiguration(mActiveSpecificNetworkRequestSpecifier.wifiConfiguration);
        networkToConnect.SSID = network.SSID;
        // Set the WifiConfiguration.BSSID field to prevent roaming.
        if (network.BSSID != null) {
            // If pre-approved, use the bssid from the request.
            networkToConnect.BSSID = network.BSSID;
        } else {
            // If not pre-approved, find the best bssid matching the request.
            networkToConnect.BSSID =
                    findBestBssidFromActiveMatchedScanResultsForNetwork(
                            ScanResultMatchInfo.fromWifiConfiguration(networkToConnect));
        }
        networkToConnect.ephemeral = true;
        // Mark it user private to avoid conflicting with any saved networks the user might have.
        // TODO (b/142035508): Use a more generic mechanism to fix this.
        networkToConnect.shared = false;
        networkToConnect.fromWifiNetworkSpecifier = true;

        // TODO(b/188021807): Implement the band request from the specifier on the network to
        // connect.

        // Store the user selected network.
        mUserSelectedNetwork = networkToConnect;

        // Request a new CMM for the connection processing.
        if (mVerboseLoggingEnabled) {
            Log.v(TAG,
                    "Requesting new ClientModeManager instance - didUserSeeUi = " + didUserSeeUi);
        }
        mShouldHaveInternetCapabilities = false;
        ClientModeManagerRequestListener listener = new ClientModeManagerRequestListener();
        if (mWifiPermissionsUtil.checkEnterCarModePrioritized(mActiveSpecificNetworkRequest
                .getRequestorUid())) {
            mShouldHaveInternetCapabilities = hasNetworkForInternet(mUserSelectedNetwork);
            if (mShouldHaveInternetCapabilities) {
                listener.onAnswer(mActiveModeWarden.getPrimaryClientModeManager());
                return;
            }
        }
        WorkSource ws = new WorkSource(mActiveSpecificNetworkRequest.getRequestorUid(),
                mActiveSpecificNetworkRequest.getRequestorPackageName());
        mActiveModeWarden.requestLocalOnlyClientModeManager(new ClientModeManagerRequestListener(),
                ws, networkToConnect.SSID, networkToConnect.BSSID, didUserSeeUi);
    }

    private boolean hasNetworkForInternet(WifiConfiguration network) {
        List<WifiConfiguration> networks = mWifiConfigManager.getConfiguredNetworksWithPasswords();
        return networks.stream().anyMatch(a -> Objects.equals(a.SSID, network.SSID)
                && !WifiConfigurationUtil.hasCredentialChanged(a, network)
                && !a.fromWifiNetworkSpecifier
                && !a.noInternetAccessExpected);
    }

    private void handleConnectToNetworkUserSelection(WifiConfiguration network,
            boolean didUserSeeUi) {
        Log.d(TAG, "User initiated connect to network: " + network.SSID);

        // Cancel the ongoing scans after user selection.
        cancelPeriodicScans();
        mIsPeriodicScanEnabled = false;

        // Trigger connection attempts.
        handleConnectToNetworkUserSelectionInternal(network, didUserSeeUi);

        // Add the network to the approved access point map for the app.
        addNetworkToUserApprovedAccessPointMap(mUserSelectedNetwork);
    }

    private void handleRejectUserSelection() {
        Log.w(TAG, "User dismissed notification, cancelling " + mActiveSpecificNetworkRequest);
        teardownForActiveRequest();
        mWifiMetrics.incrementNetworkRequestApiNumUserReject();
    }

    private boolean isUserSelectedNetwork(WifiConfiguration config, String bssid) {
        if (!TextUtils.equals(mUserSelectedNetwork.SSID, config.SSID)) {
            return false;
        }
        if (!Objects.equals(
                mUserSelectedNetwork.allowedKeyManagement, config.allowedKeyManagement)) {
            return false;
        }
        if (!TextUtils.equals(mUserSelectedNetwork.BSSID, bssid)) {
            return false;
        }
        return true;
    }

    /**
     * Invoked by {@link ClientModeImpl} on end of connection attempt to a network.
     */
    public void handleConnectionAttemptEnded(
            int failureCode, @NonNull WifiConfiguration network, @NonNull String bssid,
            int failureReason) {
        if (failureCode == WifiMetrics.ConnectionEvent.FAILURE_NONE) {
            handleNetworkConnectionSuccess(network, bssid);
        } else {
            handleNetworkConnectionFailure(network, bssid, failureCode, failureReason);
        }
    }

    /**
     * Invoked by {@link ClientModeImpl} on successful connection to a network.
     */
    private void handleNetworkConnectionSuccess(@NonNull WifiConfiguration connectedNetwork,
            @NonNull String connectedBssid) {
        if (mUserSelectedNetwork == null || connectedNetwork == null
                || !mPendingConnectionSuccess) {
            return;
        }
        if (!isUserSelectedNetwork(connectedNetwork, connectedBssid)) {
            Log.w(TAG, "Connected to unknown network " + connectedNetwork + ":" + connectedBssid
                    + ". Ignoring...");
            return;
        }
        Log.d(TAG, "Connected to network " + mUserSelectedNetwork);

        // transition the request from "active" to "connected".
        setupForConnectedRequest(true);
    }

    /**
     * Invoked by {@link ClientModeImpl} on failure to connect to a network.
     */
    private void handleNetworkConnectionFailure(@NonNull WifiConfiguration failedNetwork,
            @NonNull String failedBssid, int failureCode, int failureReason) {
        if (mUserSelectedNetwork == null || failedNetwork == null) {
            return;
        }
        if (!isUserSelectedNetwork(failedNetwork, failedBssid)) {
            Log.w(TAG, "Connection failed to unknown network " + failedNetwork + ":" + failedBssid
                    + ". Ignoring...");
            return;
        }

        if (!mPendingConnectionSuccess) {
            if (mConnectedSpecificNetworkRequest != null) {
                Log.w(TAG, "Connection is terminated, cancelling "
                        + mConnectedSpecificNetworkRequest);
                teardownForConnectedNetwork();
            }
            return;
        }
        boolean isCredentialWrong = failureCode == FAILURE_AUTHENTICATION_FAILURE
                && failureReason == AUTH_FAILURE_WRONG_PSWD;
        Log.w(TAG, "Failed to connect to network " + mUserSelectedNetwork);
        if (!isCredentialWrong && mUserSelectedNetworkConnectRetryCount++
                < USER_SELECTED_NETWORK_CONNECT_RETRY_MAX) {
            Log.i(TAG, "Retrying connection attempt, attempt# "
                    + mUserSelectedNetworkConnectRetryCount);
            connectToNetwork(mUserSelectedNetwork);
            return;
        }
        Log.e(TAG, "Connection failures, cancelling " + mUserSelectedNetwork);
        if (mRegisteredCallbacks != null) {
            int itemCount = mRegisteredCallbacks.beginBroadcast();
            for (int i = 0; i < itemCount; i++) {
                try {
                    mRegisteredCallbacks.getBroadcastItem(i).onUserSelectionConnectFailure(
                            mUserSelectedNetwork);
                } catch (RemoteException e) {
                    Log.e(TAG, "Unable to invoke network request connect failure callback ", e);
                }
            }
            mRegisteredCallbacks.finishBroadcast();
        }
        sendConnectionFailureIfAllowed(mActiveSpecificNetworkRequest.getRequestorPackageName(),
                mActiveSpecificNetworkRequest.getRequestorUid(),
                mActiveSpecificNetworkRequestSpecifier, failureCode);
        teardownForActiveRequest();
    }

    /**
     * Invoked by {@link ClientModeImpl} to indicate screen state changes.
     */
    private void handleScreenStateChanged(boolean screenOn) {
        // If there is no active request or if the user has already selected a network,
        // ignore screen state changes.
        if (mActiveSpecificNetworkRequest == null || !mIsPeriodicScanEnabled) return;

        // Pause periodic scans when the screen is off & resume when the screen is on.
        if (screenOn) {
            if (mVerboseLoggingEnabled) Log.v(TAG, "Resuming scans on screen on");
            mIsPeriodicScanPaused = false;
            startScan();
        } else {
            if (mVerboseLoggingEnabled) Log.v(TAG, "Pausing scans on screen off");
            cancelPeriodicScans();
            mIsPeriodicScanPaused = true;
        }
    }

    // Common helper method for start/end of active request processing.
    private void cleanupActiveRequest() {
        if (mVerboseLoggingEnabled) Log.v(TAG, "cleanupActiveRequest");
        // Send the abort to the UI for the current active request.
        if (mRegisteredCallbacks != null) {
            int itemCount = mRegisteredCallbacks.beginBroadcast();
            for (int i = 0; i < itemCount; i++) {
                try {
                    mRegisteredCallbacks.getBroadcastItem(i).onAbort();
                } catch (RemoteException e) {
                    Log.e(TAG, "Unable to invoke network request abort callback ", e);
                }
            }
            mRegisteredCallbacks.finishBroadcast();
        }
        // Force-release the network request to let the app know early that the attempt failed.
        if (mActiveSpecificNetworkRequest != null) {
            releaseRequestAsUnfulfillableByAnyFactory(mActiveSpecificNetworkRequest);
        }
        // Cancel periodic scan, connection timeout alarm.
        cancelPeriodicScans();
        cancelConnectionTimeout();
        // Reset the active network request.
        mActiveSpecificNetworkRequest = null;
        mActiveSpecificNetworkRequestSpecifier = null;
        mSkipUserDialogue = false;
        mUserSelectedNetworkConnectRetryCount = 0;
        mIsPeriodicScanEnabled = false;
        mIsPeriodicScanPaused = false;
        mActiveMatchedScanResults = null;
        mPendingConnectionSuccess = false;
        // Remove any callbacks registered for the request.
        if (mRegisteredCallbacks != null) mRegisteredCallbacks.kill();
        mRegisteredCallbacks = null;
    }

    // Invoked at the start of new active request processing.
    private void setupForActiveRequest() {
        if (mActiveSpecificNetworkRequest != null) {
            cleanupActiveRequest();
        }
    }

    private void removeClientModeManagerIfNecessary() {
        if (mClientModeManager != null) {
            // Set to false anyway, because no network request is active.
            mWifiConnectivityManager.setSpecificNetworkRequestInProgress(false);
            if (mContext.getResources().getBoolean(R.bool.config_wifiUseHalApiToDisableFwRoaming)) {
                mClientModeManager.enableRoaming(true); // Re-enable roaming.
            }
            if (mVerboseLoggingEnabled) {
                Log.v(TAG, "removeClientModeManager, role: " + mClientModeManagerRole);
            }
            mActiveModeWarden.removeClientModeManager(mClientModeManager);
            // For every connection attempt, get the appropriate client mode impl to use.
            mClientModeManager = null;
            mClientModeManagerRole = null;
        }
    }

    // Invoked at the termination of current active request processing.
    private void teardownForActiveRequest() {
        if (mPendingConnectionSuccess) {
            Log.i(TAG, "Disconnecting from network on reset");
            disconnectAndRemoveNetworkFromWifiConfigManager(mUserSelectedNetwork);
        }
        cleanupActiveRequest();
        // ensure there is no connected request in progress.
        if (mConnectedSpecificNetworkRequest == null) {
            removeClientModeManagerIfNecessary();
        }
    }

    // Invoked at the start of new connected request processing.
    private void setupForConnectedRequest(boolean newConnection) {
        if (mRegisteredCallbacks != null) {
            int itemCount = mRegisteredCallbacks.beginBroadcast();
            for (int i = 0; i < itemCount; i++) {
                try {
                    mRegisteredCallbacks.getBroadcastItem(i).onUserSelectionConnectSuccess(
                            mUserSelectedNetwork);
                } catch (RemoteException e) {
                    Log.e(TAG, "Unable to invoke network request connect failure callback ", e);
                }
            }
            mRegisteredCallbacks.finishBroadcast();
        }
        if (newConnection) {
            mConnectedSpecificNetworkRequest = mActiveSpecificNetworkRequest;
            mConnectedSpecificNetworkRequestSpecifier = mActiveSpecificNetworkRequestSpecifier;
            mConnectedUids.clear();
        }
        if (mActiveSpecificNetworkRequest.getRequestorUid() == 0) {
            // For shell test call from root
            mConnectedUids.add(Process.SYSTEM_UID);
        } else {
            mConnectedUids.add(mActiveSpecificNetworkRequest.getRequestorUid());
        }
        mActiveSpecificNetworkRequest = null;
        mActiveSpecificNetworkRequestSpecifier = null;
        mSkipUserDialogue = false;
        mActiveMatchedScanResults = null;
        mPendingConnectionSuccess = false;
        if (!newConnection) {
            mClientModeManager.updateCapabilities();
            return;
        }
        // Cancel connection timeout alarm.
        cancelConnectionTimeout();

        mConnectionStartTimeMillis = mClock.getElapsedSinceBootMillis();
        if (mClientModeManagerRole == ROLE_CLIENT_PRIMARY) {
            mWifiMetrics.incrementNetworkRequestApiNumConnectSuccessOnPrimaryIface();
        } else {
            mWifiMetrics.incrementNetworkRequestApiNumConnectSuccessOnSecondaryIface();
            // secondary STA being used, register CMI listener for concurrent connection metrics
            // collection.
            mCmiListener = new CmiListener();
            mClientModeImplMonitor.registerListener(mCmiListener);
        }
        // Disable roaming.
        if (mContext.getResources().getBoolean(R.bool.config_wifiUseHalApiToDisableFwRoaming)) {
            // Note: This is an old HAL API, but since it wasn't being exercised before, we are
            // being extra cautious and only using it on devices running >= S.
            if (!mClientModeManager.enableRoaming(false)) {
                Log.w(TAG, "Failed to disable roaming");
            }
        }
    }

    // Invoked at the termination of current connected request processing.
    private void teardownForConnectedNetwork() {
        Log.i(TAG, "Disconnecting from network on reset");
        disconnectAndRemoveNetworkFromWifiConfigManager(mUserSelectedNetwork);
        mConnectedSpecificNetworkRequest = null;
        mConnectedSpecificNetworkRequestSpecifier = null;
        mConnectedUids.clear();

        if (mConnectionStartTimeMillis != -1) {
            int connectionDurationSec = toIntExact(TimeUnit.MILLISECONDS.toSeconds(
                    mClock.getElapsedSinceBootMillis() - mConnectionStartTimeMillis));
            if (mClientModeManagerRole == ROLE_CLIENT_PRIMARY) {
                mWifiMetrics.incrementNetworkRequestApiConnectionDurationSecOnPrimaryIfaceHistogram(
                        connectionDurationSec);

            } else {
                mWifiMetrics
                        .incrementNetworkRequestApiConnectionDurationSecOnSecondaryIfaceHistogram(
                                connectionDurationSec);
            }
            mConnectionStartTimeMillis = -1L;
        }
        if (mCmiListener != null) {
            mCmiListener.checkForConcurrencyEndAndIncrementMetrics();
            mClientModeImplMonitor.unregisterListener(mCmiListener);
            mCmiListener = null;
        }
        // ensure there is no active request in progress.
        if (mActiveSpecificNetworkRequest == null) {
            removeClientModeManagerIfNecessary();
        }
    }

    /**
     * Helper method to populate WifiScanner handle. This is done lazily because
     * WifiScanningService is started after WifiService.
     */
    private void retrieveWifiScanner() {
        if (mWifiScanner != null) return;
        mWifiScanner = mWifiInjector.getWifiScanner();
        checkNotNull(mWifiScanner);
    }

    private void handleClientModeManagerRetrieval() {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "ClientModeManager retrieved: " + mClientModeManager);
        }
        if (mUserSelectedNetwork == null) {
            Log.e(TAG, "No user selected network to connect to. Ignoring ClientModeManager"
                    + "retrieval..");
            return;
        }
        // TODO(230795804): remove the car mode check when we can smooth switch the ownership of the
        //  network and attribute to the right App with correct package name.
        if (SdkLevel.isAtLeastS() && ActiveModeWarden
                .isClientModeManagerConnectedOrConnectingToBssid(mClientModeManager,
                mUserSelectedNetwork.SSID, mUserSelectedNetwork.BSSID)
                && mConnectedSpecificNetworkRequest != null
                && !WifiConfigurationUtil.hasCredentialChanged(
                        mConnectedSpecificNetworkRequestSpecifier.wifiConfiguration,
                mActiveSpecificNetworkRequestSpecifier.wifiConfiguration)
                && !mWifiPermissionsUtil.checkEnterCarModePrioritized(
                        mActiveSpecificNetworkRequest.getRequestorUid())) {
            // Already connected to the same network.
            setupForConnectedRequest(false);
            return;
        }

        // If using primary STA, disable Auto-join so that NetworkFactory can take control of the
        // network connection.
        if (mClientModeManagerRole == ROLE_CLIENT_PRIMARY) {
            mWifiConnectivityManager.setSpecificNetworkRequestInProgress(true);
        }

        // Disconnect from the current network before issuing a new connect request.
        disconnectAndRemoveNetworkFromWifiConfigManager(mUserSelectedNetwork);

        // Trigger connection to the network.
        connectToNetwork(mUserSelectedNetwork);
        // Triggered connection to network, now wait for the connection status.
        mPendingConnectionSuccess = true;
    }

    private void handleClientModeManagerRemovalOrFailure() {
        if (mActiveSpecificNetworkRequest != null) {
            Log.w(TAG, "ClientModeManager retrieval failed or removed, cancelling "
                    + mActiveSpecificNetworkRequest);
            teardownForActiveRequest();
        }
        if (mConnectedSpecificNetworkRequest != null) {
            Log.w(TAG, "ClientModeManager retrieval failed or removed, cancelling "
                    + mConnectedSpecificNetworkRequest);
            teardownForConnectedNetwork();
        }
    }

    private void startPeriodicScans() {
        if (mActiveSpecificNetworkRequestSpecifier == null) {
            Log.e(TAG, "Periodic scan triggered when there is no active network request. "
                    + "Ignoring...");
            return;
        }
        WifiNetworkSpecifier wns = mActiveSpecificNetworkRequestSpecifier;
        WifiConfiguration wifiConfiguration = wns.wifiConfiguration;
        if (wifiConfiguration.hiddenSSID) {
            // Can't search for SSID pattern in hidden networks.
            mScanSettings.hiddenNetworks.clear();
            mScanSettings.hiddenNetworks.add(new WifiScanner.ScanSettings.HiddenNetwork(
                    addEnclosingQuotes(wns.ssidPatternMatcher.getPath())));
        }
        int[] channelFreqs = wns.getPreferredChannelFrequenciesMhz();
        if (channelFreqs.length > 0) {
            int index = 0;
            mScanSettings.channels = new WifiScanner.ChannelSpec[channelFreqs.length];
            for (int freq : channelFreqs) {
                mScanSettings.channels[index++] = new WifiScanner.ChannelSpec(freq);
            }
            mScanSettings.band = WIFI_BAND_UNSPECIFIED;
        }
        mIsPeriodicScanEnabled = true;
        startScan();
        // Clear the channel settings to perform a full band scan.
        mScanSettings.channels = new WifiScanner.ChannelSpec[0];
        mScanSettings.band = WIFI_BAND_ALL;
    }

    private void cancelPeriodicScans() {
        if (mPeriodicScanTimerSet) {
            mAlarmManager.cancel(mPeriodicScanTimerListener);
            mPeriodicScanTimerSet = false;
        }
        // Clear the hidden networks field after each request.
        mScanSettings.hiddenNetworks.clear();
    }

    private void scheduleNextPeriodicScan() {
        if (mIsPeriodicScanPaused) {
            Log.e(TAG, "Scan triggered when periodic scanning paused. Ignoring...");
            return;
        }
        mAlarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                mClock.getElapsedSinceBootMillis() + PERIODIC_SCAN_INTERVAL_MS,
                TAG, mPeriodicScanTimerListener, mHandler);
        mPeriodicScanTimerSet = true;
    }

    private void startScan() {
        if (mActiveSpecificNetworkRequestSpecifier == null) {
            Log.e(TAG, "Scan triggered when there is no active network request. Ignoring...");
            return;
        }
        if (!mIsPeriodicScanEnabled) {
            Log.e(TAG, "Scan triggered after user selected network. Ignoring...");
            return;
        }
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "Starting the next scan for " + mActiveSpecificNetworkRequestSpecifier);
        }
        // Create a worksource using the caller's UID.
        WorkSource workSource = new WorkSource(mActiveSpecificNetworkRequest.getRequestorUid());
        mWifiScanner.startScan(
                mScanSettings, new HandlerExecutor(mHandler), mScanListener, workSource);
    }

    private boolean doesScanResultMatchWifiNetworkSpecifier(
            WifiNetworkSpecifier wns, ScanResult scanResult) {
        if (!wns.ssidPatternMatcher.match(scanResult.SSID)) {
            return false;
        }
        MacAddress bssid = MacAddress.fromString(scanResult.BSSID);
        MacAddress matchBaseAddress = wns.bssidPatternMatcher.first;
        MacAddress matchMask = wns.bssidPatternMatcher.second;
        if (!bssid.matches(matchBaseAddress, matchMask)) {
            return false;
        }
        ScanResultMatchInfo fromScanResult = ScanResultMatchInfo.fromScanResult(scanResult);
        ScanResultMatchInfo fromWifiConfiguration =
                ScanResultMatchInfo.fromWifiConfiguration(wns.wifiConfiguration);
        return fromScanResult.networkTypeEquals(fromWifiConfiguration);
    }

    // Loops through the scan results and finds scan results matching the active network
    // request.
    private List<ScanResult> getNetworksMatchingActiveNetworkRequest(
            ScanResult[] scanResults) {
        if (mActiveSpecificNetworkRequestSpecifier == null) {
            Log.e(TAG, "Scan results received with no active network request. Ignoring...");
            return Collections.emptyList();
        }
        List<ScanResult> matchedScanResults = new ArrayList<>();
        WifiNetworkSpecifier wns = mActiveSpecificNetworkRequestSpecifier;

        for (ScanResult scanResult : scanResults) {
            if (doesScanResultMatchWifiNetworkSpecifier(wns, scanResult)) {
                matchedScanResults.add(scanResult);
            }
        }
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "List of scan results matching the active request "
                    + matchedScanResults);
        }
        return matchedScanResults;
    }

    private void sendNetworkRequestMatchCallbacksForActiveRequest(
            @NonNull Collection<ScanResult> matchedScanResults) {
        if (matchedScanResults.isEmpty()) return;
        if (mRegisteredCallbacks == null
                || mRegisteredCallbacks.getRegisteredCallbackCount() == 0) {
            Log.e(TAG, "No callback registered for sending network request matches. "
                    + "Ignoring...");
            return;
        }
        int itemCount = mRegisteredCallbacks.beginBroadcast();
        for (int i = 0; i < itemCount; i++) {
            try {
                mRegisteredCallbacks.getBroadcastItem(i).onMatch(
                        new ArrayList<>(matchedScanResults));
            } catch (RemoteException e) {
                Log.e(TAG, "Unable to invoke network request match callback ", e);
            }
        }
        mRegisteredCallbacks.finishBroadcast();
    }

    private void cancelConnectionTimeout() {
        if (mConnectionTimeoutSet) {
            mAlarmManager.cancel(mConnectionTimeoutAlarmListener);
            mConnectionTimeoutSet = false;
        }
    }

    private void scheduleConnectionTimeout() {
        mAlarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                mClock.getElapsedSinceBootMillis() + NETWORK_CONNECTION_TIMEOUT_MS,
                TAG, mConnectionTimeoutAlarmListener, mHandler);
        mConnectionTimeoutSet = true;
    }

    private @NonNull CharSequence getAppName(@NonNull String packageName, int uid) {
        ApplicationInfo applicationInfo = null;
        try {
            applicationInfo = mContext.getPackageManager().getApplicationInfoAsUser(
                    packageName, 0, UserHandle.getUserHandleForUid(uid));
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Failed to find app name for " + packageName);
            return "";
        }
        CharSequence appName = mContext.getPackageManager().getApplicationLabel(applicationInfo);
        return (appName != null) ? appName : "";
    }

    private void startUi() {
        Intent intent = new Intent();
        intent.setAction(UI_START_INTENT_ACTION);
        intent.addCategory(UI_START_INTENT_CATEGORY);
        intent.setFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT | Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(UI_START_INTENT_EXTRA_APP_NAME,
                getAppName(mActiveSpecificNetworkRequest.getRequestorPackageName(),
                        mActiveSpecificNetworkRequest.getRequestorUid()));
        intent.putExtra(UI_START_INTENT_EXTRA_REQUEST_IS_FOR_SINGLE_NETWORK,
                isActiveRequestForSingleNetwork());
        mContext.startActivityAsUser(intent, UserHandle.CURRENT);
    }

    // Helper method to determine if the specifier does not contain any patterns and matches
    // a single access point.
    private boolean isActiveRequestForSingleAccessPoint() {
        if (mActiveSpecificNetworkRequestSpecifier == null) return false;

        if (mActiveSpecificNetworkRequestSpecifier.ssidPatternMatcher.getType()
                != PatternMatcher.PATTERN_LITERAL) {
            return false;
        }
        if (!Objects.equals(
                mActiveSpecificNetworkRequestSpecifier.bssidPatternMatcher.second,
                MacAddress.BROADCAST_ADDRESS)) {
            return false;
        }
        return true;
    }

    // Helper method to determine if the specifier does not contain any patterns and matches
    // a single network.
    private boolean isActiveRequestForSingleNetwork() {
        if (mActiveSpecificNetworkRequestSpecifier == null) return false;

        if (mActiveSpecificNetworkRequestSpecifier.ssidPatternMatcher.getType()
                == PatternMatcher.PATTERN_LITERAL) {
            return true;
        }
        if (Objects.equals(
                mActiveSpecificNetworkRequestSpecifier.bssidPatternMatcher.second,
                MacAddress.BROADCAST_ADDRESS)) {
            return true;
        }
        return false;
    }

    // Will return the best bssid to use for the current request's connection.
    //
    // Note: This will never return null, unless there is some internal error.
    // For ex:
    // i) The latest scan results were empty.
    // ii) The latest scan result did not contain any BSSID for the SSID user chose.
    private @Nullable String findBestBssidFromActiveMatchedScanResultsForNetwork(
            @NonNull ScanResultMatchInfo scanResultMatchInfo) {
        if (mActiveSpecificNetworkRequestSpecifier == null
                || mActiveMatchedScanResults == null) return null;
        ScanResult selectedScanResult = mActiveMatchedScanResults
                .values()
                .stream()
                .filter(scanResult -> Objects.equals(
                        ScanResultMatchInfo.fromScanResult(scanResult),
                        scanResultMatchInfo))
                .max(Comparator.comparing(scanResult -> scanResult.level))
                .orElse(null);
        if (selectedScanResult == null) { // Should never happen.
            Log.wtf(TAG, "Expected to find at least one matching scan result");
            return null;
        }
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "Best bssid selected for the request " + selectedScanResult);
        }
        return selectedScanResult.BSSID;
    }

    private boolean isAccessPointApprovedInInternalApprovalList(
            @NonNull String ssid, @NonNull MacAddress bssid, @SecurityType int networkType,
            @NonNull String requestorPackageName) {
        Set<AccessPoint> approvedAccessPoints =
                mUserApprovedAccessPointMap.get(requestorPackageName);
        if (approvedAccessPoints == null) return false;
        AccessPoint accessPoint =
                new AccessPoint(ssid, bssid, networkType);
        if (approvedAccessPoints.contains(accessPoint)) {
            // keep the most recently used AP in the end
            approvedAccessPoints.remove(accessPoint);
            approvedAccessPoints.add(accessPoint);
            if (mVerboseLoggingEnabled) {
                Log.v(TAG, "Found " + bssid
                        + " in internal user approved access point for " + requestorPackageName);
            }
            return true;
        }
        // AP does not match, but check if SSID + security type match
        if (networkType == WifiConfiguration.SECURITY_TYPE_OPEN
                || networkType == WifiConfiguration.SECURITY_TYPE_OWE) {
            // require exact BSSID match for open networks
            return false;
        }
        // Only require SSID and SecurityType match for non-open networks.
        if (approvedAccessPoints.stream()
                .anyMatch((ap) -> ap.ssid.equals(ssid) && ap.networkType == networkType)) {
            if (mVerboseLoggingEnabled) {
                Log.v(TAG, "Found SSID=" + ssid
                        + " in internal user approved access point for " + requestorPackageName);
            }
            return true;
        }
        return false;
    }

    private boolean isAccessPointApprovedInCompanionDeviceManager(
            @NonNull MacAddress bssid,
            @NonNull UserHandle requestorUserHandle,
            @NonNull String requestorPackageName) {
        if (mCompanionDeviceManager == null) {
            mCompanionDeviceManager = mContext.getSystemService(CompanionDeviceManager.class);
        }
        boolean approved = mCompanionDeviceManager.isDeviceAssociatedForWifiConnection(
                requestorPackageName, bssid, requestorUserHandle);
        if (!approved) return false;
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "Found " + bssid
                    + " in CompanionDeviceManager approved access point for "
                    + requestorPackageName);
        }
        return true;
    }

    private boolean isAccessPointApprovedForActiveRequest(@NonNull String ssid,
            @NonNull MacAddress bssid, @SecurityType int networkType, boolean revokeNormalBypass) {
        String requestorPackageName = mActiveSpecificNetworkRequest.getRequestorPackageName();
        UserHandle requestorUserHandle =
                UserHandle.getUserHandleForUid(mActiveSpecificNetworkRequest.getRequestorUid());
        // Check if access point is approved via CompanionDeviceManager first.
        if (isAccessPointApprovedInCompanionDeviceManager(
                bssid, requestorUserHandle, requestorPackageName)) {
            return true;
        }
        // Check if access point is approved in internal approval list next.
        if (!revokeNormalBypass && isAccessPointApprovedInInternalApprovalList(
                ssid, bssid, networkType, requestorPackageName)) {
            return true;
        }
        // Shell approved app
        if (TextUtils.equals(mApprovedApp, requestorPackageName)) {
            return true;
        }
        // no bypass approvals, show UI.
        return false;
    }


    // Helper method to store the all the BSSIDs matching the network from the matched scan results
    private void addNetworkToUserApprovedAccessPointMap(@NonNull WifiConfiguration network) {
        if (mActiveSpecificNetworkRequestSpecifier == null
                || mActiveMatchedScanResults == null) return;
        // Note: This hopefully is a list of size 1, because we want to store a 1:1 mapping
        // from user selection and the AP that was approved. But, since we get a WifiConfiguration
        // object representing an entire network from UI, we need to ensure that all the visible
        // BSSIDs matching the original request and the selected network are stored.
        Set<AccessPoint> newUserApprovedAccessPoints = new HashSet<>();

        ScanResultMatchInfo fromWifiConfiguration =
                ScanResultMatchInfo.fromWifiConfiguration(network);
        for (ScanResult scanResult : mActiveMatchedScanResults.values()) {
            ScanResultMatchInfo fromScanResult = ScanResultMatchInfo.fromScanResult(scanResult);
            SecurityParams params = fromScanResult.matchForNetworkSelection(fromWifiConfiguration);
            if (null != params) {
                AccessPoint approvedAccessPoint =
                        new AccessPoint(scanResult.SSID, MacAddress.fromString(scanResult.BSSID),
                                params.getSecurityType());
                newUserApprovedAccessPoints.add(approvedAccessPoint);
            }
        }
        if (newUserApprovedAccessPoints.isEmpty()) return;

        String requestorPackageName = mActiveSpecificNetworkRequest.getRequestorPackageName();
        LinkedHashSet<AccessPoint> approvedAccessPoints =
                mUserApprovedAccessPointMap.get(requestorPackageName);
        if (approvedAccessPoints == null) {
            approvedAccessPoints = new LinkedHashSet<>();
            mUserApprovedAccessPointMap.put(requestorPackageName, approvedAccessPoints);
            // Note the new app in metrics.
            mWifiMetrics.incrementNetworkRequestApiNumApps();
        }
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "Adding " + newUserApprovedAccessPoints
                    + " to user approved access point for " + requestorPackageName);
        }
        // keep the most recently added APs in the end
        approvedAccessPoints.removeAll(newUserApprovedAccessPoints);
        approvedAccessPoints.addAll(newUserApprovedAccessPoints);
        cleanUpLRUAccessPoints(approvedAccessPoints);
        saveToStore();
    }

    /**
     * 1) If the request is for a single bssid, check if the matching ScanResult was pre-approved
     * by the user.
     * 2) If yes to (b), trigger a connect immediately and returns true. Else, returns false.
     *
     * @return true if a pre-approved network was found for connection, false otherwise.
     */
    private boolean triggerConnectIfUserApprovedMatchFound(boolean revokeNormalBypass,
            ScanResult[] scanResults) {
        if (mActiveSpecificNetworkRequestSpecifier == null) return false;
        boolean requestForSingleAccessPoint = isActiveRequestForSingleAccessPoint();
        if (!requestForSingleAccessPoint && !isActiveRequestForSingleNetwork()) {
            Log.i(TAG, "ActiveRequest not for single access point or network.");
            return false;
        }

        String ssid = mActiveSpecificNetworkRequestSpecifier.ssidPatternMatcher.getPath();
        MacAddress bssid = mActiveSpecificNetworkRequestSpecifier.bssidPatternMatcher.first;
        SecurityParams params =
                ScanResultMatchInfo.fromWifiConfiguration(
                        mActiveSpecificNetworkRequestSpecifier.wifiConfiguration)
                                .getFirstAvailableSecurityParams();
        if (null == params) return false;
        int networkType = params.getSecurityType();

        if (!isAccessPointApprovedForActiveRequest(ssid, bssid, networkType, revokeNormalBypass)
                || mWifiConfigManager.isNetworkTemporarilyDisabledByUser(
                ScanResultUtil.createQuotedSsid(ssid))) {
            if (mVerboseLoggingEnabled) {
                Log.v(TAG, "No approved access point found");
            }
            return false;
        }
        List<ScanResult> matchedScanResults =
                getNetworksMatchingActiveNetworkRequest(scanResults);
        if (requestForSingleAccessPoint && !matchedScanResults.isEmpty()) {
            Log.v(TAG, "Approved access point found in matching scan results. "
                    + "Triggering connect " + ssid + "/" + bssid);
            // Request is for a single AP which is already approved. Connect directly.
            WifiConfiguration config = mActiveSpecificNetworkRequestSpecifier.wifiConfiguration;
            config.SSID = "\"" + ssid + "\"";
            config.BSSID = bssid.toString();
            handleConnectToNetworkUserSelectionInternal(config, false);
            mWifiMetrics.incrementNetworkRequestApiNumUserApprovalBypass();
            return true;
        }
        // request is for a single network (but not a particular AP) that's already approved.
        // Scanning is still needed to select the best BSSID, but allow skipping the UI.
        Log.v(TAG, "Approved network found. Allowing user dialogue to get bypassed.");
        mSkipUserDialogue = true;
        return false;
    }

    /**
     * Handle scan results
     *
     * @param scanResults Array of {@link ScanResult} to be processed.
     */
    private void handleScanResults(ScanResult[] scanResults) {
        List<ScanResult> matchedScanResults =
                getNetworksMatchingActiveNetworkRequest(scanResults);
        if ((mActiveMatchedScanResults == null || mActiveMatchedScanResults.isEmpty())
                && !matchedScanResults.isEmpty()) {
            // only note the first match size in metrics (chances of this changing in further
            // scans is pretty low)
            mWifiMetrics.incrementNetworkRequestApiMatchSizeHistogram(
                    matchedScanResults.size());
        }
        // First set of scan results for this request.
        if (mActiveMatchedScanResults == null) mActiveMatchedScanResults = new HashMap<>();
        // Coalesce the new set of scan results with previous scan results received for request.
        mActiveMatchedScanResults.putAll(matchedScanResults
                .stream()
                .collect(Collectors.toMap(
                        scanResult -> scanResult.BSSID, scanResult -> scanResult, (a, b) -> a)));
        // Weed out any stale cached scan results.
        long currentTimeInMillis = mClock.getElapsedSinceBootMillis();
        mActiveMatchedScanResults.entrySet().removeIf(
                e -> ((currentTimeInMillis - (e.getValue().timestamp / 1000))
                        >= CACHED_SCAN_RESULTS_MAX_AGE_IN_MILLIS));
        if (!mActiveMatchedScanResults.isEmpty() && mSkipUserDialogue) {
            WifiConfiguration config = mActiveSpecificNetworkRequestSpecifier.wifiConfiguration;
            config.SSID = "\""
                    + mActiveSpecificNetworkRequestSpecifier.ssidPatternMatcher.getPath() + "\"";
            config.BSSID = findBestBssidFromActiveMatchedScanResultsForNetwork(
                    ScanResultMatchInfo.fromWifiConfiguration(config));
            Log.v(TAG, "Bypassing user dialog for connection to SSID="
                    + config.SSID + ", BSSID=" + config.BSSID);
            handleConnectToNetworkUserSelection(config, false);
        }
    }

    /**
     * Retrieve the latest cached scan results from wifi scanner and filter out any
     * {@link ScanResult} older than {@link #CACHED_SCAN_RESULTS_MAX_AGE_IN_MILLIS}.
     */
    private @NonNull ScanResult[] getFilteredCachedScanResults() {
        List<ScanResult> cachedScanResults = mWifiScanner.getSingleScanResults();
        if (cachedScanResults == null || cachedScanResults.isEmpty()) return new ScanResult[0];
        long currentTimeInMillis = mClock.getElapsedSinceBootMillis();
        return cachedScanResults.stream()
                .filter(scanResult
                        -> ((currentTimeInMillis - (scanResult.timestamp / 1000))
                        < CACHED_SCAN_RESULTS_MAX_AGE_IN_MILLIS))
                .toArray(ScanResult[]::new);
    }

    /**
     * Clean up least recently used Access Points if specified app reach the limit.
     */
    private static void cleanUpLRUAccessPoints(Set<AccessPoint> approvedAccessPoints) {
        if (approvedAccessPoints.size() <= NUM_OF_ACCESS_POINT_LIMIT_PER_APP) {
            return;
        }
        Iterator iter = approvedAccessPoints.iterator();
        while (iter.hasNext() && approvedAccessPoints.size() > NUM_OF_ACCESS_POINT_LIMIT_PER_APP) {
            iter.next();
            iter.remove();
        }
    }

    /**
     * Sets all access points approved for the specified app.
     * Used by shell commands.
     */
    public void setUserApprovedApp(@NonNull String packageName, boolean approved) {
        if (approved) {
            mApprovedApp = packageName;
        } else if (TextUtils.equals(packageName, mApprovedApp)) {
            mApprovedApp = null;
        }
    }

    /**
     * Whether all access points are approved for the specified app.
     * Used by shell commands.
     */
    public boolean hasUserApprovedApp(@NonNull String packageName) {
        return TextUtils.equals(packageName, mApprovedApp);
    }

    /**
     * Remove all user approved access points and listener for the specified app.
     */
    public void removeApp(@NonNull String packageName) {
        if (mUserApprovedAccessPointMap.remove(packageName) != null) {
            Log.i(TAG, "Removing all approved access points for " + packageName);
        }
        RemoteCallbackList<ILocalOnlyConnectionStatusListener> listenerTracker =
                mLocalOnlyStatusListenerPerApp.remove(packageName);
        if (listenerTracker != null) listenerTracker.kill();
        mFeatureIdPerApp.remove(packageName);
        saveToStore();
    }

    /**
     * Add a listener to get the connection failure of the local-only conncetion
     */
    public void addLocalOnlyConnectionStatusListener(
            @NonNull ILocalOnlyConnectionStatusListener listener, String packageName,
            String featureId) {
        RemoteCallbackList<ILocalOnlyConnectionStatusListener> listenersTracker =
                mLocalOnlyStatusListenerPerApp.get(packageName);
        if (listenersTracker == null) {
            listenersTracker = new RemoteCallbackList<>();
        }
        listenersTracker.register(listener);
        mLocalOnlyStatusListenerPerApp.put(packageName, listenersTracker);
        if (!mFeatureIdPerApp.containsKey(packageName)) {
            mFeatureIdPerApp.put(packageName, featureId);
        }
    }

    /**
     * Remove a listener which added before
     */
    public void removeLocalOnlyConnectionStatusListener(
            @NonNull ILocalOnlyConnectionStatusListener listener, String packageName) {
        RemoteCallbackList<ILocalOnlyConnectionStatusListener> listenersTracker =
                mLocalOnlyStatusListenerPerApp.get(packageName);
        if (listenersTracker == null || !listenersTracker.unregister(listener)) {
            Log.w(TAG, "removeLocalOnlyConnectionFailureListener: Listener from " + packageName
                    + " already unregister.");
        }
        if (listenersTracker != null && listenersTracker.getRegisteredCallbackCount() == 0) {
            mLocalOnlyStatusListenerPerApp.remove(packageName);
            mFeatureIdPerApp.remove(packageName);
        }
    }

    private void sendConnectionFailureIfAllowed(String packageName,
            int uid, @NonNull WifiNetworkSpecifier networkSpecifier, int connectionEvent) {
        RemoteCallbackList<ILocalOnlyConnectionStatusListener> listenersTracker =
                mLocalOnlyStatusListenerPerApp.get(packageName);
        if (listenersTracker == null || listenersTracker.getRegisteredCallbackCount() == 0) {
            return;
        }

        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "Sending connection failure event to " + packageName);
        }
        final int n = listenersTracker.beginBroadcast();
        for (int i = 0; i < n; i++) {
            try {
                listenersTracker.getBroadcastItem(i).onConnectionStatus(networkSpecifier,
                        internalConnectionEventToLocalOnlyFailureCode(connectionEvent));
            } catch (RemoteException e) {
                Log.e(TAG, "sendNetworkCallback: remote exception -- " + e);
            }
        }
        listenersTracker.finishBroadcast();
    }

    private @WifiManager.LocalOnlyConnectionStatusCode int
            internalConnectionEventToLocalOnlyFailureCode(int connectionEvent) {
        switch (connectionEvent) {
            case WifiMetrics.ConnectionEvent.FAILURE_ASSOCIATION_REJECTION:
            case WifiMetrics.ConnectionEvent.FAILURE_ASSOCIATION_TIMED_OUT:
                return WifiManager.STATUS_LOCAL_ONLY_CONNECTION_FAILURE_ASSOCIATION;
            case WifiMetrics.ConnectionEvent.FAILURE_SSID_TEMP_DISABLED:
            case FAILURE_AUTHENTICATION_FAILURE:
                return WifiManager.STATUS_LOCAL_ONLY_CONNECTION_FAILURE_AUTHENTICATION;
            case WifiMetrics.ConnectionEvent.FAILURE_DHCP:
                return WifiManager.STATUS_LOCAL_ONLY_CONNECTION_FAILURE_IP_PROVISIONING;
            case WifiMetrics.ConnectionEvent.FAILURE_NETWORK_NOT_FOUND:
                return WifiManager.STATUS_LOCAL_ONLY_CONNECTION_FAILURE_NOT_FOUND;
            case WifiMetrics.ConnectionEvent.FAILURE_NO_RESPONSE:
                return WifiManager.STATUS_LOCAL_ONLY_CONNECTION_FAILURE_NO_RESPONSE;
            default:
                return WifiManager.STATUS_LOCAL_ONLY_CONNECTION_FAILURE_UNKNOWN;
        }
    }

    /**
     * Clear all internal state (for network settings reset).
     */
    public void clear() {
        mUserApprovedAccessPointMap.clear();
        mApprovedApp = null;
        for (RemoteCallbackList<ILocalOnlyConnectionStatusListener> listenerTracker
                : mLocalOnlyStatusListenerPerApp.values()) {
            listenerTracker.kill();
        }
        mLocalOnlyStatusListenerPerApp.clear();
        mFeatureIdPerApp.clear();
        Log.i(TAG, "Cleared all internal state");
        saveToStore();
    }
}
