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

import static android.net.wifi.WifiManager.SAP_CLIENT_DISCONNECT_REASON_CODE_UNSPECIFIED;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.compat.CompatChanges;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.MacAddress;
import android.net.wifi.OuiKeyedData;
import android.net.wifi.ScanResult;
import android.net.wifi.SoftApCapability;
import android.net.wifi.SoftApConfiguration;
import android.net.wifi.SoftApInfo;
import android.net.wifi.SoftApState;
import android.net.wifi.WifiAnnotations;
import android.net.wifi.WifiClient;
import android.net.wifi.WifiContext;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiScanner;
import android.net.wifi.WifiSsid;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.WorkSource;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.IntDef;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.IState;
import com.android.internal.util.Preconditions;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;
import com.android.internal.util.WakeupMessage;
import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.WifiNative.InterfaceCallback;
import com.android.server.wifi.WifiNative.SoftApHalCallback;
import com.android.server.wifi.coex.CoexManager;
import com.android.server.wifi.coex.CoexManager.CoexListener;
import com.android.server.wifi.util.ApConfigUtil;
import com.android.server.wifi.util.WaitingState;
import com.android.wifi.resources.R;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Manage WiFi in AP mode.
 * The internal state machine runs under the ClientModeImpl handler thread context.
 */
public class SoftApManager implements ActiveModeManager {
    private static final String TAG = "SoftApManager";

    @VisibleForTesting
    public static final String SOFT_AP_SEND_MESSAGE_TIMEOUT_TAG = TAG
            + " Soft AP Send Message Timeout on ";

    // Start result codes. These should reflect the SoftApStopped.StartResult metrics codes.
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = {
            START_RESULT_UNKNOWN,
            START_RESULT_SUCCESS,
            START_RESULT_FAILURE_GENERAL,
            START_RESULT_FAILURE_NO_CHANNEL,
            START_RESULT_FAILURE_UNSUPPORTED_CONFIG,
            START_RESULT_FAILURE_START_HAL,
            START_RESULT_FAILURE_START_HOSTAPD,
            START_RESULT_FAILURE_INTERFACE_CONFLICT_USER_REJECTED,
            START_RESULT_FAILURE_INTERFACE_CONFLICT,
            START_RESULT_FAILURE_CREATE_INTERFACE,
            START_RESULT_FAILURE_SET_COUNTRY_CODE,
            START_RESULT_FAILURE_SET_MAC_ADDRESS,
            START_RESULT_FAILURE_REGISTER_AP_CALLBACK_HOSTAPD,
            START_RESULT_FAILURE_REGISTER_AP_CALLBACK_WIFICOND,
            START_RESULT_FAILURE_ADD_AP_HOSTAPD,
    })
    public @interface StartResult {}

    // Unknown start result
    public static final int START_RESULT_UNKNOWN = 0;
    // Successful start
    public static final int START_RESULT_SUCCESS = 1;
    // General failure
    public static final int START_RESULT_FAILURE_GENERAL = 2;
    // Failed due to no channel available
    public static final int START_RESULT_FAILURE_NO_CHANNEL = 3;
    // Failed due to config being unsupported
    public static final int START_RESULT_FAILURE_UNSUPPORTED_CONFIG = 4;
    // Failed to start the HAL
    public static final int START_RESULT_FAILURE_START_HAL = 5;
    // Failed to start hostapd
    public static final int START_RESULT_FAILURE_START_HOSTAPD = 6;
    // Failed due to interface conflict with user rejection
    public static final int START_RESULT_FAILURE_INTERFACE_CONFLICT_USER_REJECTED = 7;
    // Failed due to interface conflict
    public static final int START_RESULT_FAILURE_INTERFACE_CONFLICT = 8;
    // Failed to create interface in vendor HAL
    public static final int START_RESULT_FAILURE_CREATE_INTERFACE = 9;
    // Failed to set country code
    public static final int START_RESULT_FAILURE_SET_COUNTRY_CODE = 10;
    // Failed to set mac address
    public static final int START_RESULT_FAILURE_SET_MAC_ADDRESS = 11;
    // Failed to register AP callback with hostapd
    public static final int START_RESULT_FAILURE_REGISTER_AP_CALLBACK_HOSTAPD = 12;
    // Failed to register AP callback with wificond
    public static final int START_RESULT_FAILURE_REGISTER_AP_CALLBACK_WIFICOND = 13;
    // Failed to add AP to hostapd
    public static final int START_RESULT_FAILURE_ADD_AP_HOSTAPD = 14;

    // Stop event codes. These should reflect the SoftApStopped.StopEvent metrics codes.
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(value = {
            STOP_EVENT_UNKNOWN,
            STOP_EVENT_STOPPED,
            STOP_EVENT_INTERFACE_DOWN,
            STOP_EVENT_INTERFACE_DESTROYED,
            STOP_EVENT_HOSTAPD_FAILURE,
            STOP_EVENT_NO_USAGE_TIMEOUT,
    })
    public @interface StopEvent {}

    // Unknown stop event
    public static final int STOP_EVENT_UNKNOWN = 0;
    // Stopped by the user
    public static final int STOP_EVENT_STOPPED = 1;
    // Stopped due to interface down
    public static final int STOP_EVENT_INTERFACE_DOWN = 2;
    // Stopped due to interface destroyed
    public static final int STOP_EVENT_INTERFACE_DESTROYED = 3;
    // Stopped due to hostapd failure
    public static final int STOP_EVENT_HOSTAPD_FAILURE = 4;
    // Stopped due to no usage timeout
    public static final int STOP_EVENT_NO_USAGE_TIMEOUT = 5;

    private final WifiContext mContext;
    private final FrameworkFacade mFrameworkFacade;
    private final WifiNative mWifiNative;
    // This will only be null if SdkLevel is not at least S
    @Nullable private final CoexManager mCoexManager;
    private final ClientModeImplMonitor mCmiMonitor;
    private final ActiveModeWarden mActiveModeWarden;
    private final SoftApNotifier mSoftApNotifier;
    private final InterfaceConflictManager mInterfaceConflictManager;
    private final WifiInjector mWifiInjector;

    @VisibleForTesting
    static final long SOFT_AP_PENDING_DISCONNECTION_CHECK_DELAY_MS = 1000;

    private static final long SCHEDULE_IDLE_INSTANCE_SHUTDOWN_TIMEOUT_DELAY_MS = 10;

    private String mCountryCode;

    private final SoftApStateMachine mStateMachine;

    private final Listener<SoftApManager> mModeListener;
    private final WifiServiceImpl.SoftApCallbackInternal mSoftApCallback;

    private String mApInterfaceName;
    private boolean mIfaceIsUp;
    private boolean mIfaceIsDestroyed;

    private final WifiApConfigStore mWifiApConfigStore;

    private final ClientModeImplListener mCmiListener = new ClientModeImplListener() {
        @Override
        public void onL2Connected(@NonNull ConcreteClientModeManager clientModeManager) {
            SoftApManager.this.onL2Connected(clientModeManager);
        }
    };

    private final WifiMetrics mWifiMetrics;
    private final long mId;

    private boolean mIsUnsetBssid;

    private boolean mVerboseLoggingEnabled = false;

    /**
     * The specified configuration passed in during initialization or during a configuration update
     * that doesn't require a restart.
     *
     * Use it when doing configuration update to know if the input configuration was changed. For
     * others use case, it should use {@code mCurrentSoftApConfiguration}.
     */
    @NonNull private SoftApModeConfiguration mSpecifiedModeConfiguration;

    /**
     * Current Soft AP configuration which is used to start Soft AP.
     * The configuration may be changed because
     * 1. bssid is changed because MAC randomization
     * 2. bands are changed because fallback to single AP mode mechanism.
     */
    @Nullable
    private SoftApConfiguration mCurrentSoftApConfiguration;

    @NonNull
    private Map<String, SoftApInfo> mCurrentSoftApInfoMap = new HashMap<>();

    @NonNull
    private SoftApCapability mCurrentSoftApCapability;

    private Map<String, List<WifiClient>> mConnectedClientWithApInfoMap = new HashMap<>();
    @VisibleForTesting
    Map<WifiClient, Integer> mPendingDisconnectClients = new HashMap<>();

    private boolean mTimeoutEnabled = false;
    private boolean mBridgedModeOpportunisticsShutdownTimeoutEnabled = false;

    private final SarManager mSarManager;

    private String mStartTimestamp;

    private long mDefaultShutdownTimeoutMillis;

    private long mDefaultShutdownIdleInstanceInBridgedModeTimeoutMillis;

    private final boolean mIsDisableShutDownBridgedModeIdleInstanceTimerWhenPlugged;

    private static final SimpleDateFormat FORMATTER = new SimpleDateFormat("MM-dd HH:mm:ss.SSS");

    private WifiDiagnostics mWifiDiagnostics;

    @Nullable
    private SoftApRole mRole = null;
    @Nullable
    private WorkSource mRequestorWs = null;

    private boolean mEverReportMetricsForMaxClient = false;

    @NonNull
    private Set<MacAddress> mBlockedClientList = new HashSet<>();

    @NonNull
    private Set<MacAddress> mAllowedClientList = new HashSet<>();

    @NonNull
    private Set<Integer> mSafeChannelFrequencyList = new HashSet<>();

    private boolean mIsPlugged = false;

    private int mCurrentApState = WifiManager.WIFI_AP_STATE_DISABLED;

    private boolean mIsSoftApStartedEventWritten = false;

    /**
     * A map stores shutdown timeouts for each Soft Ap instance.
     * There are three timeout messages now.
     * 1. <mApInterfaceName, timeout> which uses to monitor whole Soft AP interface.
     * It works on single AP mode and bridged AP mode.
     *
     * 2. <instance_lower_band, timeout> which is used to shutdown the AP when there are no
     * connected devices. It is scheduled only in bridged mode to move dual mode AP to single
     * mode AP in lower band.
     *
     * 3. <instance_higher_band, timeout> which is used to shutdown the AP when there are no
     * connected devices. It is scheduled only in bridged mode to move dual mode AP to single
     * mode AP in higher band.
     */
    @VisibleForTesting
    public Map<String, WakeupMessage> mSoftApTimeoutMessageMap = new HashMap<>();

    /**
     * Listener for soft AP events.
     */
    private final SoftApHalCallback mSoftApHalCallback = new SoftApHalCallback() {
        @Override
        public void onFailure() {
            mStateMachine.sendMessage(SoftApStateMachine.CMD_FAILURE);
        }

        @Override
        public void onInstanceFailure(String instanceName) {
            mStateMachine.sendMessage(SoftApStateMachine.CMD_FAILURE, instanceName);
        }

        @Override
        public void onInfoChanged(String apIfaceInstance, int frequency,
                @WifiAnnotations.Bandwidth int bandwidth,
                @WifiAnnotations.WifiStandard int generation,
                MacAddress apIfaceInstanceMacAddress,
                @NonNull List<OuiKeyedData> vendorData) {
            SoftApInfo apInfo = new SoftApInfo();
            apInfo.setFrequency(frequency);
            apInfo.setBandwidth(bandwidth);
            apInfo.setWifiStandard(generation);
            if (apIfaceInstanceMacAddress != null) {
                apInfo.setBssid(apIfaceInstanceMacAddress);
            }
            apInfo.setApInstanceIdentifier(apIfaceInstance != null
                    ? apIfaceInstance : mApInterfaceName);
            if (SdkLevel.isAtLeastV() && vendorData != null && !vendorData.isEmpty()) {
                apInfo.setVendorData(vendorData);
            }
            mStateMachine.sendMessage(
                    SoftApStateMachine.CMD_AP_INFO_CHANGED, 0, 0, apInfo);
        }

        @Override
        public void onConnectedClientsChanged(String apIfaceInstance, MacAddress clientAddress,
                boolean isConnected) {
            if (clientAddress != null) {
                WifiClient client = new WifiClient(clientAddress, apIfaceInstance != null
                        ? apIfaceInstance : mApInterfaceName);
                mStateMachine.sendMessage(SoftApStateMachine.CMD_ASSOCIATED_STATIONS_CHANGED,
                        isConnected ? 1 : 0, 0, client);
            } else {
                Log.e(getTag(), "onConnectedClientsChanged: Invalid type returned");
            }
        }
    };

    // This will only be null if SdkLevel is not at least S
    @Nullable private final CoexListener mCoexListener;

    private void updateSafeChannelFrequencyList() {
        if (!SdkLevel.isAtLeastS() || mCurrentSoftApConfiguration == null) {
            return;
        }
        mSafeChannelFrequencyList.clear();
        for (int configuredBand : mCurrentSoftApConfiguration.getBands()) {
            for (int band : SoftApConfiguration.BAND_TYPES) {
                if ((band & configuredBand) == 0) {
                    continue;
                }
                for (int channel : mCurrentSoftApCapability.getSupportedChannelList(band)) {
                    mSafeChannelFrequencyList.add(
                            ApConfigUtil.convertChannelToFrequency(channel, band));
                }
            }
        }
        if ((mCoexManager.getCoexRestrictions() & WifiManager.COEX_RESTRICTION_SOFTAP) != 0) {
            mSafeChannelFrequencyList.removeAll(
                    ApConfigUtil.getUnsafeChannelFreqsFromCoex(mCoexManager));
        }
        if (isBridgedMode() && mCurrentSoftApInfoMap.size() == 2) {
            // Logging only for bridged use case since it only used to fallback to single AP mode.
            Log.d(getTag(), "SafeChannelFrequencyList = " + mSafeChannelFrequencyList);
        }
    }

    private void configureInternalConfiguration() {
        if (mCurrentSoftApConfiguration == null) {
            return;
        }
        mBlockedClientList = new HashSet<>(mCurrentSoftApConfiguration.getBlockedClientList());
        mAllowedClientList = new HashSet<>(mCurrentSoftApConfiguration.getAllowedClientList());
        mTimeoutEnabled = mCurrentSoftApConfiguration.isAutoShutdownEnabled();
        mBridgedModeOpportunisticsShutdownTimeoutEnabled =
                mCurrentSoftApConfiguration.isBridgedModeOpportunisticShutdownEnabledInternal();
    }

    private void updateChangeableConfiguration(SoftApConfiguration newConfig) {
        if (mCurrentSoftApConfiguration == null || newConfig == null) {
            return;
        }
        /**
         * update configurations only which mentioned in WifiManager#setSoftApConfiguration
         */
        long newShutdownTimeoutMillis = newConfig.getShutdownTimeoutMillis();
        // Compatibility check is used for unit test only since the SoftApManager is created by
        // the unit test thread (not the system_server) when running unit test. In other cases,
        // the SoftApManager would run in system server(i.e. always bypasses the app compat check).
        if (CompatChanges.isChangeEnabled(SoftApConfiguration.REMOVE_ZERO_FOR_TIMEOUT_SETTING)
                && newShutdownTimeoutMillis == 0) {
            newShutdownTimeoutMillis = SoftApConfiguration.DEFAULT_TIMEOUT;
        }
        SoftApConfiguration.Builder newConfigurBuilder =
                new SoftApConfiguration.Builder(mCurrentSoftApConfiguration)
                .setAllowedClientList(newConfig.getAllowedClientList())
                .setBlockedClientList(newConfig.getBlockedClientList())
                .setClientControlByUserEnabled(newConfig.isClientControlByUserEnabled())
                .setMaxNumberOfClients(newConfig.getMaxNumberOfClients())
                .setShutdownTimeoutMillis(newShutdownTimeoutMillis)
                .setAutoShutdownEnabled(newConfig.isAutoShutdownEnabled());
        if (SdkLevel.isAtLeastS()) {
            newConfigurBuilder.setBridgedModeOpportunisticShutdownEnabled(
                    newConfig.isBridgedModeOpportunisticShutdownEnabledInternal());
        }
        mCurrentSoftApConfiguration = newConfigurBuilder.build();
        configureInternalConfiguration();
    }

    public SoftApManager(
            @NonNull WifiContext context,
            @NonNull Looper looper,
            @NonNull FrameworkFacade framework,
            @NonNull WifiNative wifiNative,
            @NonNull WifiInjector wifiInjector,
            @NonNull CoexManager coexManager,
            @NonNull InterfaceConflictManager interfaceConflictManager,
            @NonNull Listener<SoftApManager> listener,
            @NonNull WifiServiceImpl.SoftApCallbackInternal callback,
            @NonNull WifiApConfigStore wifiApConfigStore,
            @NonNull SoftApModeConfiguration apConfig,
            @NonNull WifiMetrics wifiMetrics,
            @NonNull SarManager sarManager,
            @NonNull WifiDiagnostics wifiDiagnostics,
            @NonNull SoftApNotifier softApNotifier,
            @NonNull ClientModeImplMonitor cmiMonitor,
            @NonNull ActiveModeWarden activeModeWarden,
            long id,
            @NonNull WorkSource requestorWs,
            @NonNull SoftApRole role,
            boolean verboseLoggingEnabled) {
        mContext = context;
        mFrameworkFacade = framework;
        mSoftApNotifier = softApNotifier;
        mWifiNative = wifiNative;
        mWifiInjector = wifiInjector;
        mCoexManager = coexManager;
        mInterfaceConflictManager = interfaceConflictManager;
        if (SdkLevel.isAtLeastS()) {
            mCoexListener = new CoexListener() {
                @Override
                public void onCoexUnsafeChannelsChanged() {
                    if (mCurrentSoftApConfiguration == null) {
                        return;
                    }
                    mStateMachine.sendMessage(
                            SoftApStateMachine.CMD_SAFE_CHANNEL_FREQUENCY_CHANGED);
                }
            };
        } else {
            mCoexListener = null;
        }
        mCountryCode = apConfig.getCountryCode();
        mModeListener = listener;
        mSoftApCallback = callback;
        mWifiApConfigStore = wifiApConfigStore;
        mCurrentSoftApConfiguration = apConfig.getSoftApConfiguration();
        mCurrentSoftApCapability = apConfig.getCapability();
        // null is a valid input and means we use the user-configured tethering settings.
        if (mCurrentSoftApConfiguration == null) {
            mCurrentSoftApConfiguration = mWifiApConfigStore.getApConfiguration();
            // may still be null if we fail to load the default config
        }
        // Store mode configuration before update the configuration.
        mSpecifiedModeConfiguration =
                new SoftApModeConfiguration(
                        apConfig.getTargetMode(),
                        mCurrentSoftApConfiguration,
                        mCurrentSoftApCapability,
                        mCountryCode,
                        apConfig.getTetheringRequest());
        if (mCurrentSoftApConfiguration != null) {
            mIsUnsetBssid = mCurrentSoftApConfiguration.getBssid() == null;
            if (mCurrentSoftApCapability.areFeaturesSupported(
                    SoftApCapability.SOFTAP_FEATURE_MAC_ADDRESS_CUSTOMIZATION)) {
                mCurrentSoftApConfiguration = mWifiApConfigStore.randomizeBssidIfUnset(
                        mContext, mCurrentSoftApConfiguration);
            }
        }
        mWifiMetrics = wifiMetrics;
        mSarManager = sarManager;
        mWifiDiagnostics = wifiDiagnostics;
        mStateMachine = new SoftApStateMachine(looper);
        configureInternalConfiguration();
        mDefaultShutdownTimeoutMillis = mContext.getResources().getInteger(
                R.integer.config_wifiFrameworkSoftApShutDownTimeoutMilliseconds);
        mDefaultShutdownIdleInstanceInBridgedModeTimeoutMillis = mContext.getResources().getInteger(
                R.integer
                .config_wifiFrameworkSoftApShutDownIdleInstanceInBridgedModeTimeoutMillisecond);
        mIsDisableShutDownBridgedModeIdleInstanceTimerWhenPlugged = mContext.getResources()
                .getBoolean(R.bool
                .config_wifiFrameworkSoftApDisableBridgedModeShutdownIdleInstanceWhenCharging);
        mCmiMonitor = cmiMonitor;
        mActiveModeWarden = activeModeWarden;
        mCmiMonitor.registerListener(mCmiListener);
        updateSafeChannelFrequencyList();
        mId = id;
        mRole = role;
        enableVerboseLogging(verboseLoggingEnabled);
        mStateMachine.sendMessage(SoftApStateMachine.CMD_START, requestorWs);
    }

    @Override
    public long getId() {
        return mId;
    }

    private String getTag() {
        return TAG + "[" + (mApInterfaceName == null ? "unknown" : mApInterfaceName) + "]";
    }

    /**
     * Stop soft AP.
     */
    @Override
    public void stop() {
        Log.d(getTag(), " currentstate: " + getCurrentStateName());
        mStateMachine.sendMessage(SoftApStateMachine.CMD_STOP);
    }

    private boolean isOweTransition() {
        return (SdkLevel.isAtLeastT() && mCurrentSoftApConfiguration != null
                && mCurrentSoftApConfiguration.getSecurityType()
                        == SoftApConfiguration.SECURITY_TYPE_WPA3_OWE_TRANSITION);
    }

    private boolean isBridgedMode() {
        return (SdkLevel.isAtLeastS() && mCurrentSoftApConfiguration != null
                && (mCurrentSoftApConfiguration.getBands().length > 1));
    }

    private boolean isBridgeRequired() {
        return isBridgedMode() || isOweTransition();
    }

    private long getShutdownTimeoutMillis() {
        long timeout = mCurrentSoftApConfiguration.getShutdownTimeoutMillis();
        return timeout > 0 ? timeout : mDefaultShutdownTimeoutMillis;
    }

    private long getShutdownIdleInstanceInBridgedModeTimeoutMillis() {
        long timeout = mCurrentSoftApConfiguration
                .getBridgedModeOpportunisticShutdownTimeoutMillisInternal();
        return timeout > 0 ? timeout : mDefaultShutdownIdleInstanceInBridgedModeTimeoutMillis;
    }

    private List<OuiKeyedData> getVendorData() {
        return (SdkLevel.isAtLeastV() && mCurrentSoftApConfiguration != null)
                ? mCurrentSoftApConfiguration.getVendorData()
                : new ArrayList<>();
    }

    private String getHighestFrequencyInstance(Set<String> candidateInstances) {
        int currentHighestFrequencyOnAP = 0;
        String highestFrequencyInstance = null;
        for (String instance : candidateInstances) {
            SoftApInfo info = mCurrentSoftApInfoMap.get(instance);
            if (info == null) {
                Log.wtf(getTag(), "Invalid instance name, no way to get the frequency");
                return "";
            }
            int frequencyOnInstance = info.getFrequency();
            if (frequencyOnInstance > currentHighestFrequencyOnAP) {
                currentHighestFrequencyOnAP = frequencyOnInstance;
                highestFrequencyInstance = instance;
            }
        }
        return highestFrequencyInstance;
    }

    @Override
    @Nullable public SoftApRole getRole() {
        return mRole;
    }

    @Override
    @Nullable public ClientRole getPreviousRole() {
        return null;
    }

    @Override
    public long getLastRoleChangeSinceBootMs() {
        return 0;
    }

    /** Set the role of this SoftApManager */
    public void setRole(SoftApRole role) {
        // softap does not allow in-place switching of roles.
        Preconditions.checkState(mRole == null);
        mRole = role;
    }

    @Override
    public String getInterfaceName() {
        return mApInterfaceName;
    }

    @Override
    public WorkSource getRequestorWs() {
        return mRequestorWs;
    }

    /**
     * Update AP capability. Called when carrier config or device resouce config changed.
     *
     * @param capability new AP capability.
     */
    public void updateCapability(@NonNull SoftApCapability capability) {
        mStateMachine.sendMessage(SoftApStateMachine.CMD_UPDATE_CAPABILITY, capability);
    }

    /**
     * Update AP configuration. Called when setting update config via
     * {@link WifiManager#setSoftApConfiguration(SoftApConfiguration)}
     *
     * @param config new AP config.
     */
    public void updateConfiguration(@NonNull SoftApConfiguration config) {
        mStateMachine.sendMessage(SoftApStateMachine.CMD_UPDATE_CONFIG, config);
    }

    /**
     * Retrieve the {@link SoftApModeConfiguration} instance associated with this mode manager.
     */
    public SoftApModeConfiguration getSoftApModeConfiguration() {
        return new SoftApModeConfiguration(
                mSpecifiedModeConfiguration.getTargetMode(),
                mSpecifiedModeConfiguration.getSoftApConfiguration(),
                mCurrentSoftApCapability,
                mCountryCode,
                mSpecifiedModeConfiguration.getTetheringRequest());
    }

    /**
     * Retrieve the name of the Bridged AP iface instance to remove for a downgrade, or null if a
     * downgrade is not possible.
     */
    public String getBridgedApDowngradeIfaceInstanceForRemoval() {
        if (!isBridgedMode() || mCurrentSoftApInfoMap.size() == 0) {
            return null;
        }
        List<String> instances = mWifiNative.getBridgedApInstances(mApInterfaceName);
        if (instances == null || instances.size() == 1) {
            return null;
        }
        return getHighestFrequencyInstance(mCurrentSoftApInfoMap.keySet());
    }

    /**
     * Dump info about this softap manager.
     */
    @Override
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("Dump of SoftApManager id=" + mId);

        pw.println("current StateMachine mode: " + getCurrentStateName());
        pw.println("mRole: " + mRole);
        pw.println("mApInterfaceName: " + mApInterfaceName);
        pw.println("mIfaceIsUp: " + mIfaceIsUp);
        pw.println("mSoftApCountryCode: " + mCountryCode);
        pw.println(
                "mSpecifiedModeConfiguration.targetMode: "
                        + mSpecifiedModeConfiguration.getTargetMode());
        pw.println("mCurrentSoftApConfiguration: " + mCurrentSoftApConfiguration);
        pw.println("mCurrentSoftApCapability: " + mCurrentSoftApCapability);
        pw.println("getConnectedClientList().size(): " + getConnectedClientList().size());
        pw.println("mTimeoutEnabled: " + mTimeoutEnabled);
        pw.println("mBridgedModeOpportunisticsShutdownTimeoutEnabled: "
                + mBridgedModeOpportunisticsShutdownTimeoutEnabled);
        pw.println("mCurrentSoftApInfoMap " + mCurrentSoftApInfoMap);
        pw.println("mStartTimestamp: " + mStartTimestamp);
        pw.println("mSafeChannelFrequencyList: " + mSafeChannelFrequencyList.stream()
                .map(Object::toString)
                .collect(Collectors.joining(",")));
        mStateMachine.dump(fd, pw, args);
    }

    @Override
    public void enableVerboseLogging(boolean verbose) {
        mVerboseLoggingEnabled = verbose;
    }

    @Override
    public String toString() {
        return "SoftApManager{id=" + getId()
                + " iface=" + getInterfaceName()
                + " role=" + getRole()
                + "}";
    }

    /**
     * A ClientModeImpl instance has been L2 connected.
     *
     * @param newPrimary the corresponding ConcreteClientModeManager instance for the ClientModeImpl
     *                   that has been L2 connected.
     */
    private void onL2Connected(@NonNull ConcreteClientModeManager clientModeManager) {
        Log.d(getTag(), "onL2Connected called");
        mStateMachine.sendMessage(SoftApStateMachine.CMD_HANDLE_WIFI_CONNECTED,
                clientModeManager);
    }


    private String getCurrentStateName() {
        IState currentState = mStateMachine.getCurrentState();

        if (currentState != null) {
            return currentState.getName();
        }

        return "StateMachine not active";
    }

    /**
     * Update AP state.
     *
     * @param newState     new AP state
     * @param currentState current AP state
     * @param reason       Failure reason if the new AP state is in failure state
     */
    private void updateApState(int newState, int currentState, int reason) {
        mCurrentApState = newState;
        mSoftApCallback.onStateChanged(new SoftApState(
                newState,
                reason,
                mSpecifiedModeConfiguration.getTetheringRequest(),
                mApInterfaceName));

        //send the AP state change broadcast
        final Intent intent = new Intent(WifiManager.WIFI_AP_STATE_CHANGED_ACTION);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        intent.putExtra(WifiManager.EXTRA_WIFI_AP_STATE, newState);
        intent.putExtra(WifiManager.EXTRA_PREVIOUS_WIFI_AP_STATE, currentState);
        if (newState == WifiManager.WIFI_AP_STATE_FAILED) {
            //only set reason number when softAP start failed
            intent.putExtra(WifiManager.EXTRA_WIFI_AP_FAILURE_REASON, reason);
        }

        intent.putExtra(WifiManager.EXTRA_WIFI_AP_INTERFACE_NAME, mApInterfaceName);
        intent.putExtra(
                WifiManager.EXTRA_WIFI_AP_MODE, mSpecifiedModeConfiguration.getTargetMode());

        if (SdkLevel.isAtLeastSv2()) {
            mContext.sendBroadcastAsUser(intent, UserHandle.ALL,
                    android.Manifest.permission.ACCESS_WIFI_STATE);
        } else {
            mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
        }
    }

    private int setMacAddress() {
        MacAddress mac = mCurrentSoftApConfiguration.getBssid();

        if (mac == null) {
            // If no BSSID is explicitly requested, (re-)configure the factory MAC address. Some
            // drivers may not support setting the MAC at all, so fail soft in this case.
            if (!mWifiNative.resetApMacToFactoryMacAddress(mApInterfaceName)) {
                Log.w(getTag(), "failed to reset to factory MAC address; "
                        + "continuing with current MAC");
            }
        } else {
            if (mWifiNative.isApSetMacAddressSupported(mApInterfaceName)) {
                if (!mWifiNative.setApMacAddress(mApInterfaceName, mac)) {
                    Log.e(getTag(), "failed to set explicitly requested MAC address");
                    return START_RESULT_FAILURE_SET_MAC_ADDRESS;
                }
            } else if (!mIsUnsetBssid) {
                // If hardware does not support MAC address setter,
                // only report the error for non randomization.
                return START_RESULT_FAILURE_UNSUPPORTED_CONFIG;
            }
        }

        return START_RESULT_SUCCESS;
    }

    /**
     * Dynamic update the country code when Soft AP enabled.
     *
     * @param countryCode 2 byte ASCII string. For ex: US, CA.
     * @return true if request is sent successfully, false otherwise.
     */
    public boolean updateCountryCode(@NonNull String countryCode) {
        if (ApConfigUtil.isSoftApDynamicCountryCodeSupported(mContext)
                && mCurrentSoftApCapability.areFeaturesSupported(
                        SoftApCapability.SOFTAP_FEATURE_ACS_OFFLOAD)) {
            mStateMachine.sendMessage(SoftApStateMachine.CMD_UPDATE_COUNTRY_CODE, countryCode);
            return true;
        }
        return false;
    }

    private boolean setCountryCode() {
        int band = mCurrentSoftApConfiguration.getBand();
        if (TextUtils.isEmpty(mCountryCode)) {
            if (band == SoftApConfiguration.BAND_5GHZ || band == SoftApConfiguration.BAND_6GHZ) {
                // Country code is mandatory for 5GHz/6GHz band.
                Log.e(getTag(), "Invalid country code, "
                        + "required for setting up soft ap in band:" + band);
                return false;
            }
            // Absence of country code is not fatal for 2Ghz & Any band options.
            return true;
        }
        if (!mWifiNative.setApCountryCode(
                mApInterfaceName, mCountryCode.toUpperCase(Locale.ROOT))) {
            if (band == SoftApConfiguration.BAND_5GHZ || band == SoftApConfiguration.BAND_6GHZ) {
                // Return an error if failed to set country code when AP is configured for
                // 5GHz/6GHz band.
                Log.e(getTag(), "Failed to set country code, "
                        + "required for setting up soft ap in band: " + band);
                return false;
            }
            // Failure to set country code is not fatal for other band options.
        }
        return true;
    }

    /**
     * Start a soft AP instance as configured.
     *
     * @return One of {@link StartResult}
     */
    private @StartResult int startSoftAp() {
        if (SdkLevel.isAtLeastS()) {
            Log.d(getTag(), "startSoftAp: channels " + mCurrentSoftApConfiguration.getChannels()
                    + " iface " + mApInterfaceName + " country " + mCountryCode);
        } else {
            Log.d(getTag(), "startSoftAp: band " + mCurrentSoftApConfiguration.getBand());
        }

        int startResult = setMacAddress();
        if (startResult != START_RESULT_SUCCESS) {
            return startResult;
        }

        // Make a copy of configuration for updating AP band and channel.
        SoftApConfiguration.Builder localConfigBuilder =
                new SoftApConfiguration.Builder(mCurrentSoftApConfiguration);

        startResult = ApConfigUtil.updateApChannelConfig(
                mWifiNative, mCoexManager, mContext.getResources(), mCountryCode,
                localConfigBuilder, mCurrentSoftApConfiguration, mCurrentSoftApCapability);
        if (startResult != START_RESULT_SUCCESS) {
            Log.e(getTag(), "Failed to update AP band and channel");
            return startResult;
        }

        if (mCurrentSoftApConfiguration.isHiddenSsid()) {
            Log.d(getTag(), "SoftAP is a hidden network");
        }

        if (!ApConfigUtil.checkSupportAllConfiguration(
                mCurrentSoftApConfiguration, mCurrentSoftApCapability)) {
            Log.d(getTag(), "Unsupported Configuration detect! config = "
                    + mCurrentSoftApConfiguration);
            return START_RESULT_FAILURE_UNSUPPORTED_CONFIG;
        }

        startResult =
                mWifiNative.startSoftAp(
                        mApInterfaceName,
                        localConfigBuilder.build(),
                        mSpecifiedModeConfiguration.getTargetMode()
                                == WifiManager.IFACE_IP_MODE_TETHERED,
                        mSoftApHalCallback);
        if (startResult != START_RESULT_SUCCESS) {
            Log.e(getTag(), "Soft AP start failed");
            return startResult;
        }

        mWifiDiagnostics.startLogging(mApInterfaceName);
        mStartTimestamp = FORMATTER.format(new Date(System.currentTimeMillis()));
        Log.d(getTag(), "Soft AP is started ");

        return START_RESULT_SUCCESS;
    }

    /**
     * Handles a start failure and writes the start failure metrics.
     * @param startResult One of {@link StartResult}.
     */
    private void handleStartSoftApFailure(@StartResult int startResult) {
        if (startResult == START_RESULT_SUCCESS) {
            Log.wtf(TAG, "handleStartSoftApFailure called with START_RESULT_SUCCESS");
            return;
        }

        int wifiManagerFailureReason = WifiManager.SAP_START_FAILURE_GENERAL;
        if (startResult == START_RESULT_FAILURE_NO_CHANNEL) {
            wifiManagerFailureReason = WifiManager.SAP_START_FAILURE_NO_CHANNEL;
        } else if (startResult == START_RESULT_FAILURE_UNSUPPORTED_CONFIG) {
            wifiManagerFailureReason = WifiManager.SAP_START_FAILURE_UNSUPPORTED_CONFIGURATION;
        } else if (startResult == START_RESULT_FAILURE_INTERFACE_CONFLICT_USER_REJECTED) {
            wifiManagerFailureReason = WifiManager.SAP_START_FAILURE_USER_REJECTED;
        }
        updateApState(WifiManager.WIFI_AP_STATE_FAILED,
                mCurrentApState,
                wifiManagerFailureReason);
        stopSoftAp();
        mWifiMetrics.incrementSoftApStartResult(false, wifiManagerFailureReason);
        mModeListener.onStartFailure(SoftApManager.this);
        writeSoftApStartedEvent(startResult);
    }

    /**
     * Disconnect all connected clients on active softap interface(s).
     * This is usually done just before stopSoftAp().
     */
    private void disconnectAllClients() {
        for (WifiClient client : getConnectedClientList()) {
            mWifiNative.forceClientDisconnect(mApInterfaceName, client.getMacAddress(),
                    SAP_CLIENT_DISCONNECT_REASON_CODE_UNSPECIFIED);
        }
    }

    /**
     * Teardown soft AP and teardown the interface.
     */
    private void stopSoftAp() {
        disconnectAllClients();
        mWifiDiagnostics.stopLogging(mApInterfaceName);
        mWifiNative.teardownInterface(mApInterfaceName);
        Log.d(getTag(), "Soft AP is stopped");
    }

    private void addClientToPendingDisconnectionList(WifiClient client, int reason) {
        Log.d(getTag(), "Fail to disconnect client: " + client.getMacAddress()
                + ", add it into pending list");
        mPendingDisconnectClients.put(client, reason);
        mStateMachine.getHandler().removeMessages(
                SoftApStateMachine.CMD_FORCE_DISCONNECT_PENDING_CLIENTS);
        mStateMachine.sendMessageDelayed(
                SoftApStateMachine.CMD_FORCE_DISCONNECT_PENDING_CLIENTS,
                SOFT_AP_PENDING_DISCONNECTION_CHECK_DELAY_MS);
    }

    private List<WifiClient> getConnectedClientList() {
        List<WifiClient> connectedClientList = new ArrayList<>();
        for (List<WifiClient> it : mConnectedClientWithApInfoMap.values()) {
            connectedClientList.addAll(it);
        }
        return connectedClientList;
    }

    private boolean checkSoftApClient(SoftApConfiguration config, WifiClient newClient) {
        if (!mCurrentSoftApCapability.areFeaturesSupported(
                SoftApCapability.SOFTAP_FEATURE_CLIENT_FORCE_DISCONNECT)) {
            return true;
        }

        if (mBlockedClientList.contains(newClient.getMacAddress())) {
            Log.d(getTag(), "Force disconnect for client: " + newClient + "in blocked list");
            if (!mWifiNative.forceClientDisconnect(
                    mApInterfaceName, newClient.getMacAddress(),
                    WifiManager.SAP_CLIENT_BLOCK_REASON_CODE_BLOCKED_BY_USER)) {
                addClientToPendingDisconnectionList(newClient,
                        WifiManager.SAP_CLIENT_BLOCK_REASON_CODE_BLOCKED_BY_USER);
            }
            return false;
        }
        if (config.isClientControlByUserEnabled()
                && !mAllowedClientList.contains(newClient.getMacAddress())) {
            mSoftApCallback.onBlockedClientConnecting(newClient,
                    WifiManager.SAP_CLIENT_BLOCK_REASON_CODE_BLOCKED_BY_USER);
            Log.d(getTag(), "Force disconnect for unauthorized client: " + newClient);
            if (!mWifiNative.forceClientDisconnect(
                    mApInterfaceName, newClient.getMacAddress(),
                    WifiManager.SAP_CLIENT_BLOCK_REASON_CODE_BLOCKED_BY_USER)) {
                addClientToPendingDisconnectionList(newClient,
                        WifiManager.SAP_CLIENT_BLOCK_REASON_CODE_BLOCKED_BY_USER);
            }
            return false;
        }
        int maxConfig = mCurrentSoftApCapability.getMaxSupportedClients();
        if (config.getMaxNumberOfClients() > 0) {
            maxConfig = Math.min(maxConfig, config.getMaxNumberOfClients());
        }

        if (getConnectedClientList().size() >= maxConfig) {
            Log.i(getTag(), "No more room for new client:" + newClient);
            if (!mWifiNative.forceClientDisconnect(
                    mApInterfaceName, newClient.getMacAddress(),
                    WifiManager.SAP_CLIENT_BLOCK_REASON_CODE_NO_MORE_STAS)) {
                addClientToPendingDisconnectionList(newClient,
                        WifiManager.SAP_CLIENT_BLOCK_REASON_CODE_NO_MORE_STAS);
            }
            mSoftApCallback.onBlockedClientConnecting(newClient,
                    WifiManager.SAP_CLIENT_BLOCK_REASON_CODE_NO_MORE_STAS);
            // Avoid report the max client blocked in the same settings.
            if (!mEverReportMetricsForMaxClient) {
                mWifiMetrics.noteSoftApClientBlocked(maxConfig);
                mEverReportMetricsForMaxClient = true;
            }
            return false;
        }
        return true;
    }

    private class SoftApStateMachine extends StateMachine {
        // Commands for the state machine.
        public static final int CMD_START = 0;
        public static final int CMD_STOP = 1;
        public static final int CMD_FAILURE = 2;
        public static final int CMD_INTERFACE_STATUS_CHANGED = 3;
        public static final int CMD_ASSOCIATED_STATIONS_CHANGED = 4;
        public static final int CMD_NO_ASSOCIATED_STATIONS_TIMEOUT = 5;
        public static final int CMD_INTERFACE_DESTROYED = 7;
        public static final int CMD_INTERFACE_DOWN = 8;
        public static final int CMD_AP_INFO_CHANGED = 9;
        public static final int CMD_UPDATE_CAPABILITY = 10;
        public static final int CMD_UPDATE_CONFIG = 11;
        public static final int CMD_FORCE_DISCONNECT_PENDING_CLIENTS = 12;
        public static final int CMD_NO_ASSOCIATED_STATIONS_TIMEOUT_ON_ONE_INSTANCE = 13;
        public static final int CMD_SAFE_CHANNEL_FREQUENCY_CHANGED = 14;
        public static final int CMD_HANDLE_WIFI_CONNECTED = 15;
        public static final int CMD_UPDATE_COUNTRY_CODE = 16;
        public static final int CMD_DRIVER_COUNTRY_CODE_CHANGED = 17;
        public static final int CMD_DRIVER_COUNTRY_CODE_CHANGE_TIMED_OUT = 18;
        public static final int CMD_PLUGGED_STATE_CHANGED = 19;

        private final State mActiveState = new ActiveState();
        private final State mIdleState;
        private final State mWaitingForDriverCountryCodeChangedState;
        private final WaitingState mWaitingForIcmDialogState = new WaitingState(this);
        private final State mStartedState;

        private final InterfaceCallback mWifiNativeInterfaceCallback = new InterfaceCallback() {
            @Override
            public void onDestroyed(String ifaceName) {
                if (mApInterfaceName != null && mApInterfaceName.equals(ifaceName)) {
                    sendMessage(CMD_INTERFACE_DESTROYED);
                }
            }

            @Override
            public void onUp(String ifaceName) {
                if (mApInterfaceName != null && mApInterfaceName.equals(ifaceName)) {
                    sendMessage(CMD_INTERFACE_STATUS_CHANGED, 1);
                }
            }

            @Override
            public void onDown(String ifaceName) {
                if (mApInterfaceName != null && mApInterfaceName.equals(ifaceName)) {
                    sendMessage(CMD_INTERFACE_STATUS_CHANGED, 0);
                }
            }
        };

        SoftApStateMachine(Looper looper) {
            super(TAG, looper);

            final int threshold =  mContext.getResources().getInteger(
                    R.integer.config_wifiConfigurationWifiRunnerThresholdInMs);
            mIdleState = new IdleState(threshold);
            mWaitingForDriverCountryCodeChangedState =
                    new WaitingForDriverCountryCodeChangedState(threshold);
            mStartedState = new StartedState(threshold);
            // CHECKSTYLE:OFF IndentationCheck
            addState(mActiveState);
                addState(mIdleState, mActiveState);
                addState(mWaitingForDriverCountryCodeChangedState, mActiveState);
                addState(mWaitingForIcmDialogState, mActiveState);
                addState(mStartedState, mActiveState);
            // CHECKSTYLE:ON IndentationCheck

            setInitialState(mIdleState);
            start();
        }


        private class ActiveState extends State {
            @Override
            public void exit() {
                mModeListener.onStopped(SoftApManager.this);
                mCmiMonitor.unregisterListener(mCmiListener);
            }
        }

        @Override
        protected String getWhatToString(int what) {
            switch (what) {
                case CMD_START:
                    return "CMD_START";
                case CMD_STOP:
                    return "CMD_STOP";
                case CMD_FAILURE:
                    return "CMD_FAILURE";
                case CMD_INTERFACE_STATUS_CHANGED:
                    return "CMD_INTERFACE_STATUS_CHANGED";
                case CMD_ASSOCIATED_STATIONS_CHANGED:
                    return "CMD_ASSOCIATED_STATIONS_CHANGED";
                case CMD_NO_ASSOCIATED_STATIONS_TIMEOUT:
                    return "CMD_NO_ASSOCIATED_STATIONS_TIMEOUT";
                case CMD_INTERFACE_DESTROYED:
                    return "CMD_INTERFACE_DESTROYED";
                case CMD_INTERFACE_DOWN:
                    return "CMD_INTERFACE_DOWN";
                case CMD_AP_INFO_CHANGED:
                    return "CMD_AP_INFO_CHANGED";
                case CMD_UPDATE_CAPABILITY:
                    return "CMD_UPDATE_CAPABILITY";
                case CMD_UPDATE_CONFIG:
                    return "CMD_UPDATE_CONFIG";
                case CMD_FORCE_DISCONNECT_PENDING_CLIENTS:
                    return "CMD_FORCE_DISCONNECT_PENDING_CLIENTS";
                case CMD_NO_ASSOCIATED_STATIONS_TIMEOUT_ON_ONE_INSTANCE:
                    return "CMD_NO_ASSOCIATED_STATIONS_TIMEOUT_ON_ONE_INSTANCE";
                case CMD_SAFE_CHANNEL_FREQUENCY_CHANGED:
                    return "CMD_SAFE_CHANNEL_FREQUENCY_CHANGED";
                case CMD_HANDLE_WIFI_CONNECTED:
                    return "CMD_HANDLE_WIFI_CONNECTED";
                case CMD_UPDATE_COUNTRY_CODE:
                    return "CMD_UPDATE_COUNTRY_CODE";
                case CMD_DRIVER_COUNTRY_CODE_CHANGED:
                    return "CMD_DRIVER_COUNTRY_CODE_CHANGED";
                case CMD_DRIVER_COUNTRY_CODE_CHANGE_TIMED_OUT:
                    return "CMD_DRIVER_COUNTRY_CODE_CHANGE_TIMED_OUT";
                case CMD_PLUGGED_STATE_CHANGED:
                    return "CMD_PLUGGED_STATE_CHANGED";
                case RunnerState.STATE_ENTER_CMD:
                    return "Enter";
                case RunnerState.STATE_EXIT_CMD:
                    return "Exit";
                default:
                    return "what:" + what;
            }
        }

        private class IdleState extends RunnerState {
            IdleState(int threshold) {
                super(threshold, mWifiInjector.getWifiHandlerLocalLog());
            }

            @Override
            public void enterImpl() {
                mApInterfaceName = null;
                mIfaceIsUp = false;
                mIfaceIsDestroyed = false;
            }

            @Override
            public void exitImpl() {
            }

            @Override
            public String getMessageLogRec(int what) {
                return SoftApManager.class.getSimpleName() + "." + IdleState.class.getSimpleName()
                        + "." + getWhatToString(what);
            }

            @Override
            public boolean processMessageImpl(Message message) {
                switch (message.what) {
                    case CMD_STOP:
                        writeSoftApStoppedEvent(STOP_EVENT_STOPPED);
                        quitNow();
                        break;
                    case CMD_START:
                        boolean isCountryCodeChanged = false;
                        mRequestorWs = (WorkSource) message.obj;
                        WifiSsid wifiSsid = mCurrentSoftApConfiguration != null
                                ? mCurrentSoftApConfiguration.getWifiSsid() : null;
                        if (wifiSsid == null || wifiSsid.getBytes().length == 0) {
                            Log.e(getTag(), "Unable to start soft AP without valid configuration");
                            handleStartSoftApFailure(START_RESULT_FAILURE_GENERAL);
                            break;
                        }
                        if (!TextUtils.isEmpty(mCountryCode)
                                && !TextUtils.equals(
                                mCountryCode, mCurrentSoftApCapability.getCountryCode())) {
                            isCountryCodeChanged = true;
                            Log.i(getTag(), "CountryCode changed - "
                                    + " mCountryCode = " + mCountryCode
                                    + ", base country in SoftApCapability = "
                                    + mCurrentSoftApCapability.getCountryCode());
                        }
                        if (isBridgedMode()) {
                            boolean isFallbackToSingleAp = false;
                            final List<ClientModeManager> cmms =
                                    mActiveModeWarden.getClientModeManagers();
                            // Checking STA status only when device supports STA + AP concurrency
                            // since STA would be dropped when device doesn't support it.
                            if (cmms.size() != 0 && mWifiNative.isStaApConcurrencySupported()) {
                                if (ApConfigUtil.isStaWithBridgedModeSupported(mContext,
                                        mWifiNative)) {
                                    for (ClientModeManager cmm
                                            : mActiveModeWarden.getClientModeManagers()) {
                                        WifiInfo wifiConnectedInfo = cmm.getConnectionInfo();
                                        int wifiFrequency = wifiConnectedInfo.getFrequency();
                                        if (wifiFrequency > 0
                                                && !mSafeChannelFrequencyList.contains(
                                                wifiFrequency)) {
                                            Log.d(getTag(), "Wifi connected to unavailable freq: "
                                                    + wifiFrequency);
                                            isFallbackToSingleAp = true;
                                            break;
                                        }
                                    }
                                } else {
                                    // The client mode exist but DUT doesn't support
                                    // STA + bridged AP, we should fallback to single AP mode.
                                    Log.d(getTag(), " STA iface exist but device doesn't support"
                                            + " STA + Bridged AP");
                                    isFallbackToSingleAp = true;
                                }
                            }
                            if (isCountryCodeChanged && mCountryCode.equalsIgnoreCase(
                                    mContext.getResources().getString(
                                            R.string.config_wifiDriverWorldModeCountryCode))) {
                                Log.i(getTag(), "Country code changed to world mode"
                                        + " - fallback to single AP");
                                isFallbackToSingleAp = true;
                            }
                            if (!isCountryCodeChanged) {
                                SoftApConfiguration tempConfig =
                                        ApConfigUtil.removeUnavailableBandsFromConfig(
                                                mCurrentSoftApConfiguration,
                                                mCurrentSoftApCapability,
                                                mCoexManager,
                                                mContext);
                                if (tempConfig == null) {
                                    handleStartSoftApFailure(
                                            START_RESULT_FAILURE_UNSUPPORTED_CONFIG);
                                    break;
                                }
                                mCurrentSoftApConfiguration = tempConfig;
                                if (mCurrentSoftApConfiguration.getBands().length == 1) {
                                    isFallbackToSingleAp = true;
                                    Log.i(
                                            getTag(),
                                            "Removed unavailable bands"
                                                    + " - fallback to single AP");
                                }
                            }
                            // Fall back to Single AP if it's not possible to create a Bridged AP.
                            if (!mWifiNative.isItPossibleToCreateBridgedApIface(mRequestorWs)) {
                                isFallbackToSingleAp = true;
                            }
                            // Fall back to single AP if creating a single AP does not require
                            // destroying an existing iface, but creating a bridged AP does.
                            if (mWifiNative.shouldDowngradeToSingleApForConcurrency(mRequestorWs)) {
                                Log.d(getTag(), "Creating bridged AP will destroy an existing"
                                        + " iface, but single AP will not.");
                                isFallbackToSingleAp = true;
                            }
                            if (isFallbackToSingleAp) {
                                int newSingleApBand = 0;
                                for (int configuredBand : mCurrentSoftApConfiguration.getBands()) {
                                    newSingleApBand |= configuredBand;
                                }
                                newSingleApBand = ApConfigUtil.append24GToBandIf24GSupported(
                                        newSingleApBand, mContext);
                                Log.i(getTag(), "Fallback to single AP mode with band "
                                        + newSingleApBand);
                                mCurrentSoftApConfiguration =
                                        new SoftApConfiguration.Builder(mCurrentSoftApConfiguration)
                                        .setBand(newSingleApBand)
                                        .build();
                            }
                        }

                        // Remove 6GHz from requested bands if security type is restricted
                        // Note: 6GHz only band is already handled by initial validation
                        SoftApConfiguration tempConfig =
                                ApConfigUtil.remove6gBandForUnsupportedSecurity(
                                        mContext.getResources(),
                                        mCurrentSoftApConfiguration, isBridgedMode());
                        if (tempConfig == null) {
                            handleStartSoftApFailure(START_RESULT_FAILURE_UNSUPPORTED_CONFIG);
                            break;
                        }
                        mCurrentSoftApConfiguration = tempConfig;
                        // Don't show the ICM dialog if this is for tethering.
                        boolean bypassDialog =
                                mSpecifiedModeConfiguration.getTargetMode()
                                        == WifiManager.IFACE_IP_MODE_TETHERED;
                        int icmResult = mInterfaceConflictManager
                                .manageInterfaceConflictForStateMachine(
                                        TAG, message, mStateMachine, mWaitingForIcmDialogState,
                                        mIdleState, isBridgeRequired()
                                                ? HalDeviceManager.HDM_CREATE_IFACE_AP_BRIDGE
                                                : HalDeviceManager.HDM_CREATE_IFACE_AP,
                                        mRequestorWs, bypassDialog);
                        if (icmResult == InterfaceConflictManager.ICM_ABORT_COMMAND) {
                            Log.e(getTag(), "User refused to set up interface");
                            handleStartSoftApFailure(
                                    START_RESULT_FAILURE_INTERFACE_CONFLICT_USER_REJECTED);
                            break;
                        } else if (icmResult
                                == InterfaceConflictManager.ICM_SKIP_COMMAND_WAIT_FOR_USER) {
                            break;
                        }

                        if (SdkLevel.isAtLeastT()
                                && mCurrentSoftApConfiguration.isIeee80211beEnabled()
                                && !mCurrentSoftApCapability.areFeaturesSupported(
                                SoftApCapability.SOFTAP_FEATURE_IEEE80211_BE)) {
                            Log.d(getTag(), "11BE is not supported, removing from configuration");
                            mCurrentSoftApConfiguration = new SoftApConfiguration
                                    .Builder(mCurrentSoftApConfiguration)
                                    .setIeee80211beEnabled(false)
                                    .build();
                        }
                        mApInterfaceName = mWifiNative.setupInterfaceForSoftApMode(
                                mWifiNativeInterfaceCallback, mRequestorWs,
                                mCurrentSoftApConfiguration.getBand(), isBridgeRequired(),
                                SoftApManager.this, getVendorData());
                        if (TextUtils.isEmpty(mApInterfaceName)) {
                            Log.e(getTag(), "setup failure when creating ap interface.");
                            // Only check if it's possible to create single AP, since a DBS request
                            // already falls back to single AP if we can't create DBS.
                            if (!mWifiNative.isItPossibleToCreateApIface(mRequestorWs)) {
                                handleStartSoftApFailure(START_RESULT_FAILURE_INTERFACE_CONFLICT);
                            } else {
                                handleStartSoftApFailure(START_RESULT_FAILURE_CREATE_INTERFACE);
                            }
                            break;
                        }
                        mSoftApNotifier.dismissSoftApShutdownTimeoutExpiredNotification();
                        updateApState(WifiManager.WIFI_AP_STATE_ENABLING,
                                WifiManager.WIFI_AP_STATE_DISABLED, 0);
                        if (!setCountryCode()) {
                            handleStartSoftApFailure(START_RESULT_FAILURE_SET_COUNTRY_CODE);
                            break;
                        }
                        if (isCountryCodeChanged) {
                            Log.i(getTag(), "Need to wait for driver country code update before"
                                    + " starting");
                            transitionTo(mWaitingForDriverCountryCodeChangedState);
                            break;
                        }
                        int startResult = startSoftAp();
                        if (startResult != START_RESULT_SUCCESS) {
                            handleStartSoftApFailure(startResult);
                            break;
                        }

                        transitionTo(mStartedState);
                        break;
                    case CMD_UPDATE_CAPABILITY:
                        SoftApCapability capability = (SoftApCapability) message.obj;
                        mCurrentSoftApCapability = new SoftApCapability(capability);
                        updateSafeChannelFrequencyList();
                        break;
                    case CMD_UPDATE_CONFIG: {
                        SoftApConfiguration newConfig = (SoftApConfiguration) message.obj;
                        mSpecifiedModeConfiguration =
                                new SoftApModeConfiguration(
                                        mSpecifiedModeConfiguration.getTargetMode(),
                                        newConfig,
                                        mCurrentSoftApCapability,
                                        mCountryCode,
                                        mSpecifiedModeConfiguration.getTetheringRequest());
                        Log.d(getTag(), "Configuration changed to " + newConfig);
                        // Idle mode, update all configurations.
                        mCurrentSoftApConfiguration = newConfig;
                        configureInternalConfiguration();
                        break;
                    }
                    case CMD_UPDATE_COUNTRY_CODE:
                        String countryCode = (String) message.obj;
                        if (!TextUtils.isEmpty(countryCode)) {
                            mCountryCode = countryCode;
                        }
                        break;
                    default:
                        // Ignore all other commands.
                        break;
                }

                return HANDLED;
            }
        }

        private class WaitingForDriverCountryCodeChangedState extends RunnerState {
            private static final int TIMEOUT_MS = 5_000;

            private final WifiCountryCode.ChangeListener mCountryCodeChangeListener =
                    countryCode -> sendMessage(CMD_DRIVER_COUNTRY_CODE_CHANGED, countryCode);

            WaitingForDriverCountryCodeChangedState(int threshold) {
                super(threshold, mWifiInjector.getWifiHandlerLocalLog());
            }

            @Override
            public void enterImpl() {
                mWifiInjector.getWifiCountryCode().registerListener(mCountryCodeChangeListener);
                sendMessageDelayed(CMD_DRIVER_COUNTRY_CODE_CHANGE_TIMED_OUT, TIMEOUT_MS);
            }

            @Override
            public void exitImpl() {
                mWifiInjector.getWifiCountryCode().unregisterListener(mCountryCodeChangeListener);
                removeMessages(CMD_DRIVER_COUNTRY_CODE_CHANGE_TIMED_OUT);
            }

            @Override
            public boolean processMessageImpl(Message message) {
                if (message.what == CMD_DRIVER_COUNTRY_CODE_CHANGED) {
                    if (!TextUtils.equals(mCountryCode, (String) message.obj)) {
                        Log.i(getTag(), "Ignore country code changed: " + message.obj);
                        return HANDLED;
                    }
                    Log.i(getTag(), "Driver country code change to " + message.obj
                            + ", continue starting.");
                    mCurrentSoftApCapability.setCountryCode(mCountryCode);
                    mCurrentSoftApCapability =
                            ApConfigUtil.updateSoftApCapabilityWithAvailableChannelList(
                                    mCurrentSoftApCapability, mContext, mWifiNative, null);
                    updateSafeChannelFrequencyList();
                    if (isBridgedMode()) {
                        SoftApConfiguration tempConfig =
                                ApConfigUtil.removeUnavailableBandsFromConfig(
                                        mCurrentSoftApConfiguration,
                                        mCurrentSoftApCapability, mCoexManager, mContext);
                        if (tempConfig == null) {
                            handleStartSoftApFailure(START_RESULT_FAILURE_UNSUPPORTED_CONFIG);
                            transitionTo(mIdleState);
                            return HANDLED;
                        }
                        mCurrentSoftApConfiguration = tempConfig;
                        if (mCurrentSoftApConfiguration.getBands().length == 1) {
                            Log.i(getTag(), "Moving to single AP after updating the CC and band."
                                    + " Teardown bridged interface and setup single AP interface");
                            mWifiNative.teardownInterface(mApInterfaceName);
                            mApInterfaceName = mWifiNative.setupInterfaceForSoftApMode(
                                    mWifiNativeInterfaceCallback, mRequestorWs,
                                    mCurrentSoftApConfiguration.getBand(), isBridgeRequired(),
                                    SoftApManager.this, getVendorData());
                            if (TextUtils.isEmpty(mApInterfaceName)) {
                                Log.e(getTag(), "setup failure when creating single AP iface");
                                handleStartSoftApFailure(START_RESULT_FAILURE_GENERAL);
                                transitionTo(mIdleState);
                                return HANDLED;
                            }
                        }
                    }
                } else if (message.what == CMD_DRIVER_COUNTRY_CODE_CHANGE_TIMED_OUT) {
                    Log.i(getTag(), "Timed out waiting for driver country code change, "
                            + "continue starting anyway.");
                } else {
                    Log.i(getTag(), "Defer " + getWhatToString(message.what)
                            + " while waiting for driver country code change.");
                    deferMessage(message);
                    return HANDLED;
                }
                int startResult = startSoftAp();
                if (startResult != START_RESULT_SUCCESS) {
                    handleStartSoftApFailure(startResult);
                    transitionTo(mIdleState);
                    return HANDLED;
                }
                transitionTo(mStartedState);
                return HANDLED;
            }

            @Override
            public String getMessageLogRec(int what) {
                return SoftApManager.class.getSimpleName() + "." + RunnerState.class.getSimpleName()
                        + "." + getWhatToString(what);
            }
        }

        private class StartedState extends RunnerState {
            StartedState(int threshold) {
                super(threshold, mWifiInjector.getWifiHandlerLocalLog());
            }

            BroadcastReceiver mBatteryPluggedReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    sendMessage(CMD_PLUGGED_STATE_CHANGED,
                            intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0));
                }
            };

            private void rescheduleBothBridgedInstancesTimeoutMessage() {
                Set<String> instances = mCurrentSoftApInfoMap.keySet();
                String HighestFrequencyInstance = getHighestFrequencyInstance(instances);
                // Schedule bridged mode timeout on all instances if needed
                for (String instance : instances) {
                    long timeout = getShutdownIdleInstanceInBridgedModeTimeoutMillis();
                    if (!TextUtils.equals(instance, HighestFrequencyInstance)) {
                        //  Make sure that we shutdown the higher frequency instance first by adding
                        //  offset for lower frequency instance.
                        timeout += SCHEDULE_IDLE_INSTANCE_SHUTDOWN_TIMEOUT_DELAY_MS;
                    }
                    rescheduleTimeoutMessageIfNeeded(instance, timeout);
                }
            }

            /**
             * Schedule timeout message depends on Soft Ap instance
             *
             * @param changedInstance the schedule should change on specific instance only.
             *                        If changedInstance is mApInterfaceName, it means that
             *                        we need to reschedule all of timeout message.
             */
            private void rescheduleTimeoutMessages(@NonNull String changedInstance) {
                // Don't trigger bridged mode shutdown timeout when only one active instance
                // In Dual AP, one instance may already be closed due to LTE coexistence or DFS
                // restrictions or due to inactivity. i.e. mCurrentSoftApInfoMap.size() is 1)
                if (isBridgedMode() &&  mCurrentSoftApInfoMap.size() == 2) {
                    if (TextUtils.equals(mApInterfaceName, changedInstance)) {
                        rescheduleBothBridgedInstancesTimeoutMessage();
                    } else {
                        rescheduleTimeoutMessageIfNeeded(changedInstance,
                                getShutdownIdleInstanceInBridgedModeTimeoutMillis());
                    }
                }

                // Always evaluate timeout schedule on tetheringInterface
                rescheduleTimeoutMessageIfNeeded(mApInterfaceName, getShutdownTimeoutMillis());
            }

            private void removeIfaceInstanceFromBridgedApIface(String instanceName) {
                if (TextUtils.isEmpty(instanceName)) {
                    return;
                }
                if (mCurrentSoftApInfoMap.containsKey(instanceName)) {
                    Log.i(getTag(), "remove instance " + instanceName + "("
                            + mCurrentSoftApInfoMap.get(instanceName).getFrequency()
                            + ") from bridged iface " + mApInterfaceName);
                    mWifiNative.removeIfaceInstanceFromBridgedApIface(mApInterfaceName,
                            instanceName);
                    // Remove the info and update it.
                    updateSoftApInfo(mCurrentSoftApInfoMap.get(instanceName), true);
                }
            }

            /**
             * Schedule the timeout message when timeout control is enabled and there is no client
             * connect to the instance.
             *
             * @param instance The key of the {@code mSoftApTimeoutMessageMap},
             *                 @see mSoftApTimeoutMessageMap for details.
             */
            private void rescheduleTimeoutMessageIfNeeded(String instance, long timeoutValue) {
                final boolean isTetheringInterface =
                        TextUtils.equals(mApInterfaceName, instance);
                final boolean timeoutEnabled = isTetheringInterface ? mTimeoutEnabled
                        : (mBridgedModeOpportunisticsShutdownTimeoutEnabled && !mIsPlugged);
                final int clientNumber = isTetheringInterface
                        ? getConnectedClientList().size()
                        : mConnectedClientWithApInfoMap.get(instance).size();
                Log.d(getTag(), "rescheduleTimeoutMessageIfNeeded " + instance + ", timeoutEnabled="
                        + timeoutEnabled + ", isPlugged=" + mIsPlugged + ", clientNumber="
                        + clientNumber);
                if (!timeoutEnabled || clientNumber != 0) {
                    cancelTimeoutMessage(instance);
                    return;
                }
                scheduleTimeoutMessage(instance, timeoutValue);
            }

            private void scheduleTimeoutMessage(String instance, long timeout) {
                if (mSoftApTimeoutMessageMap.containsKey(instance)) {
                    mSoftApTimeoutMessageMap.get(instance).schedule(
                            SystemClock.elapsedRealtime() + timeout);
                    Log.d(getTag(), "Timeout message scheduled, on " + instance + ", delay = "
                            + timeout);
                }
            }

            private void cancelTimeoutMessage(String instance) {
                if (mSoftApTimeoutMessageMap.containsKey(instance)) {
                    mSoftApTimeoutMessageMap.get(instance).cancel();
                    Log.d(getTag(), "Timeout message canceled on " + instance);
                }
            }

            /**
             * When configuration changed, it need to force some clients disconnect to match the
             * configuration.
             */
            private void updateClientConnection() {
                if (!mCurrentSoftApCapability.areFeaturesSupported(
                        SoftApCapability.SOFTAP_FEATURE_CLIENT_FORCE_DISCONNECT)) {
                    return;
                }
                final int maxAllowedClientsByHardwareAndCarrier =
                        mCurrentSoftApCapability.getMaxSupportedClients();
                final int userApConfigMaxClientCount =
                        mCurrentSoftApConfiguration.getMaxNumberOfClients();
                int finalMaxClientCount = maxAllowedClientsByHardwareAndCarrier;
                if (userApConfigMaxClientCount > 0) {
                    finalMaxClientCount = Math.min(userApConfigMaxClientCount,
                            maxAllowedClientsByHardwareAndCarrier);
                }
                List<WifiClient> currentClients = getConnectedClientList();
                int targetDisconnectClientNumber = currentClients.size() - finalMaxClientCount;
                List<WifiClient> allowedConnectedList = new ArrayList<>();
                Iterator<WifiClient> iterator = currentClients.iterator();
                while (iterator.hasNext()) {
                    WifiClient client = iterator.next();
                    if (mBlockedClientList.contains(client.getMacAddress())
                              || (mCurrentSoftApConfiguration.isClientControlByUserEnabled()
                              && !mAllowedClientList.contains(client.getMacAddress()))) {
                        Log.d(getTag(), "Force disconnect for not allowed client: " + client);
                        if (!mWifiNative.forceClientDisconnect(
                                mApInterfaceName, client.getMacAddress(),
                                WifiManager.SAP_CLIENT_BLOCK_REASON_CODE_BLOCKED_BY_USER)) {
                            addClientToPendingDisconnectionList(client,
                                    WifiManager.SAP_CLIENT_BLOCK_REASON_CODE_BLOCKED_BY_USER);
                        }
                        targetDisconnectClientNumber--;
                    } else {
                        allowedConnectedList.add(client);
                    }
                }

                if (targetDisconnectClientNumber > 0) {
                    Iterator<WifiClient> allowedClientIterator = allowedConnectedList.iterator();
                    while (allowedClientIterator.hasNext()) {
                        if (targetDisconnectClientNumber == 0) break;
                        WifiClient allowedClient = allowedClientIterator.next();
                        Log.d(getTag(), "Force disconnect for client due to no more room: "
                                + allowedClient);
                        if (!mWifiNative.forceClientDisconnect(
                                mApInterfaceName, allowedClient.getMacAddress(),
                                WifiManager.SAP_CLIENT_BLOCK_REASON_CODE_NO_MORE_STAS)) {
                            addClientToPendingDisconnectionList(allowedClient,
                                    WifiManager.SAP_CLIENT_BLOCK_REASON_CODE_NO_MORE_STAS);
                        }
                        targetDisconnectClientNumber--;
                    }
                }
            }

            /**
             * Set stations associated with this soft AP
             * @param client The station for which connection state changed.
             * @param isConnected True for the connection changed to connect, otherwise false.
             */
            private void updateConnectedClients(WifiClient client, boolean isConnected) {
                if (client == null) {
                    return;
                }

                if (null != mPendingDisconnectClients.remove(client)) {
                    Log.d(getTag(), "Remove client: " + client.getMacAddress()
                            + "from pending disconnectionlist");
                }

                String apInstanceIdentifier = client.getApInstanceIdentifier();
                List clientList = mConnectedClientWithApInfoMap.computeIfAbsent(
                        apInstanceIdentifier, k -> new ArrayList<>());
                int index = clientList.indexOf(client);

                if ((index != -1) == isConnected) {
                    Log.e(getTag(), "Drop client connection event, client "
                            + client + "isConnected: " + isConnected
                            + " , duplicate event or client is blocked");
                    return;
                }
                if (isConnected) {
                    boolean isAllow = checkSoftApClient(mCurrentSoftApConfiguration, client);
                    if (isAllow) {
                        clientList.add(client);
                    } else {
                        return;
                    }
                } else {
                    if (null == clientList.remove(index)) {
                        Log.e(getTag(), "client doesn't exist in list, it should NOT happen");
                    }
                }

                // Update clients list.
                mConnectedClientWithApInfoMap.put(apInstanceIdentifier, clientList);
                SoftApInfo currentInfoWithClientsChanged = mCurrentSoftApInfoMap
                        .get(apInstanceIdentifier);
                Log.d(getTag(), "The connected wifi stations have changed with count: "
                        + clientList.size() + ": " + clientList + " on the AP which info is "
                        + currentInfoWithClientsChanged);

                if (mSoftApCallback != null) {
                    mSoftApCallback.onConnectedClientsOrInfoChanged(mCurrentSoftApInfoMap,
                            mConnectedClientWithApInfoMap, isBridgeRequired());
                } else {
                    Log.e(getTag(),
                            "SoftApCallback is null. Dropping ConnectedClientsChanged event.");
                }

                mWifiMetrics.addSoftApNumAssociatedStationsChangedEvent(
                        getConnectedClientList().size(),
                        mConnectedClientWithApInfoMap.get(apInstanceIdentifier).size(),
                        mSpecifiedModeConfiguration.getTargetMode(),
                        mCurrentSoftApInfoMap.get(apInstanceIdentifier));

                rescheduleTimeoutMessages(apInstanceIdentifier);
            }

            /**
             * @param apInfo, the new SoftApInfo changed. Null used to clean up.
             */
            private void updateSoftApInfo(@Nullable SoftApInfo apInfo, boolean isRemoved) {
                Log.d(getTag(), "SoftApInfo update " + apInfo + ", isRemoved: " + isRemoved);
                if (apInfo == null) {
                    // Clean up
                    mCurrentSoftApInfoMap.clear();
                    mConnectedClientWithApInfoMap.clear();
                    mSoftApCallback.onConnectedClientsOrInfoChanged(mCurrentSoftApInfoMap,
                            mConnectedClientWithApInfoMap, isBridgeRequired());
                    return;
                }
                String changedInstance = apInfo.getApInstanceIdentifier();
                if (apInfo.equals(mCurrentSoftApInfoMap.get(changedInstance))) {
                    if (isRemoved) {
                        boolean isClientConnected =
                                mConnectedClientWithApInfoMap.get(changedInstance).size() > 0;
                        mCurrentSoftApInfoMap.remove(changedInstance);
                        mSoftApTimeoutMessageMap.remove(changedInstance);
                        mConnectedClientWithApInfoMap.remove(changedInstance);
                        mSoftApCallback.onConnectedClientsOrInfoChanged(mCurrentSoftApInfoMap,
                                mConnectedClientWithApInfoMap, isBridgeRequired());
                        if (isClientConnected) {
                            mWifiMetrics.addSoftApNumAssociatedStationsChangedEvent(
                                    getConnectedClientList().size(),
                                    0,
                                    mSpecifiedModeConfiguration.getTargetMode(),
                                    apInfo);
                        }
                        if (isBridgeRequired()) {
                            mWifiMetrics.addSoftApInstanceDownEventInDualMode(
                                    mSpecifiedModeConfiguration.getTargetMode(), apInfo);
                        }
                    }
                    return;
                }

                // Make sure an empty client list is created when info updated
                List clientList = mConnectedClientWithApInfoMap.computeIfAbsent(
                        changedInstance, k -> new ArrayList<>());

                if (clientList.size() != 0) {
                    Log.e(getTag(), "The info: " + apInfo
                            + " changed when client connected, it should NOT happen!!");
                }

                mCurrentSoftApInfoMap.put(changedInstance, new SoftApInfo(apInfo));
                mSoftApCallback.onConnectedClientsOrInfoChanged(mCurrentSoftApInfoMap,
                        mConnectedClientWithApInfoMap, isBridgeRequired());

                boolean isNeedToScheduleTimeoutMessage = false;
                if (!mSoftApTimeoutMessageMap.containsKey(mApInterfaceName)) {
                    // First info update, create WakeupMessage for mApInterfaceName.
                    mSoftApTimeoutMessageMap.put(mApInterfaceName, new WakeupMessage(
                            mContext, mStateMachine.getHandler(),
                            SOFT_AP_SEND_MESSAGE_TIMEOUT_TAG + mApInterfaceName,
                            SoftApStateMachine.CMD_NO_ASSOCIATED_STATIONS_TIMEOUT));
                    isNeedToScheduleTimeoutMessage = true;
                }

                if (isBridgedMode()
                        && !mSoftApTimeoutMessageMap.containsKey(changedInstance)) {
                    mSoftApTimeoutMessageMap.put(changedInstance,
                            new WakeupMessage(mContext, mStateMachine.getHandler(),
                            SOFT_AP_SEND_MESSAGE_TIMEOUT_TAG + changedInstance,
                            SoftApStateMachine.CMD_NO_ASSOCIATED_STATIONS_TIMEOUT_ON_ONE_INSTANCE,
                            0, 0, changedInstance));
                    isNeedToScheduleTimeoutMessage = true;
                }

                // Trigger schedule after mCurrentSoftApInfoMap is updated.
                if (isNeedToScheduleTimeoutMessage) {
                    rescheduleTimeoutMessages(mApInterfaceName);
                }

                // ignore invalid freq and softap disable case for metrics
                if (apInfo.getFrequency() > 0
                        && apInfo.getBandwidth() != SoftApInfo.CHANNEL_WIDTH_INVALID) {
                    mWifiMetrics.addSoftApChannelSwitchedEvent(
                            new ArrayList<>(mCurrentSoftApInfoMap.values()),
                            mSpecifiedModeConfiguration.getTargetMode(),
                            isBridgeRequired());
                    updateUserBandPreferenceViolationMetricsIfNeeded(apInfo);
                }
            }

            private void onUpChanged(boolean isUp) {
                if (isUp == mIfaceIsUp) {
                    return;  // no change
                }

                mIfaceIsUp = isUp;
                if (isUp) {
                    Log.d(getTag(), "SoftAp is ready for use");
                    updateApState(WifiManager.WIFI_AP_STATE_ENABLED,
                            WifiManager.WIFI_AP_STATE_ENABLING, 0);
                    mModeListener.onStarted(SoftApManager.this);
                    mWifiMetrics.incrementSoftApStartResult(true, 0);
                    mCurrentSoftApInfoMap.clear();
                    mConnectedClientWithApInfoMap.clear();
                    if (mSoftApCallback != null) {
                        mSoftApCallback.onConnectedClientsOrInfoChanged(mCurrentSoftApInfoMap,
                                mConnectedClientWithApInfoMap, isBridgeRequired());
                    }
                } else {
                    // the interface was up, but goes down
                    sendMessage(CMD_INTERFACE_DOWN);
                }
                mWifiMetrics.addSoftApUpChangedEvent(
                        isUp,
                        mSpecifiedModeConfiguration.getTargetMode(),
                        mDefaultShutdownTimeoutMillis,
                        isBridgeRequired());
                if (isUp) {
                    mWifiMetrics.updateSoftApConfiguration(
                            mCurrentSoftApConfiguration,
                            mSpecifiedModeConfiguration.getTargetMode(),
                            isBridgeRequired());
                    mWifiMetrics.updateSoftApCapability(
                            mCurrentSoftApCapability,
                            mSpecifiedModeConfiguration.getTargetMode(),
                            isBridgeRequired());
                }
            }

            @Override
            public void enterImpl() {
                mIfaceIsUp = false;
                mIfaceIsDestroyed = false;
                onUpChanged(mWifiNative.isInterfaceUp(mApInterfaceName));

                Handler handler = mStateMachine.getHandler();
                if (SdkLevel.isAtLeastS()) {
                    mCoexManager.registerCoexListener(mCoexListener);
                }
                if (mIsDisableShutDownBridgedModeIdleInstanceTimerWhenPlugged) {
                    Intent stickyIntent = mContext.registerReceiver(mBatteryPluggedReceiver,
                            new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                    mIsPlugged = stickyIntent.getIntExtra(BatteryManager.EXTRA_STATUS, 0) != 0;
                }
                mSarManager.setSapWifiState(WifiManager.WIFI_AP_STATE_ENABLED);
                Log.d(getTag(), "Resetting connected clients on start");
                mConnectedClientWithApInfoMap.clear();
                mPendingDisconnectClients.clear();
                mEverReportMetricsForMaxClient = false;
                writeSoftApStartedEvent(START_RESULT_SUCCESS);
            }

            @Override
            public void exitImpl() {
                if (!mIfaceIsDestroyed) {
                    stopSoftAp();
                }
                if (SdkLevel.isAtLeastS()) {
                    mCoexManager.unregisterCoexListener(mCoexListener);
                }
                if (getConnectedClientList().size() != 0) {
                    Log.d(getTag(), "Resetting num stations on stop");
                    for (List<WifiClient> it : mConnectedClientWithApInfoMap.values()) {
                        if (it.size() != 0) {
                            mWifiMetrics.addSoftApNumAssociatedStationsChangedEvent(
                                    0,
                                    0,
                                    mSpecifiedModeConfiguration.getTargetMode(),
                                    mCurrentSoftApInfoMap.get(it.get(0).getApInstanceIdentifier()));
                        }
                    }
                    mConnectedClientWithApInfoMap.clear();
                    if (mSoftApCallback != null) {
                        mSoftApCallback.onConnectedClientsOrInfoChanged(mCurrentSoftApInfoMap,
                                mConnectedClientWithApInfoMap, isBridgeRequired());
                    }
                }
                mPendingDisconnectClients.clear();
                for (String key : mSoftApTimeoutMessageMap.keySet()) {
                    cancelTimeoutMessage(key);
                }
                mSoftApTimeoutMessageMap.clear();
                if (mIsDisableShutDownBridgedModeIdleInstanceTimerWhenPlugged) {
                    mContext.unregisterReceiver(mBatteryPluggedReceiver);
                }
                // Need this here since we are exiting |Started| state and won't handle any
                // future CMD_INTERFACE_STATUS_CHANGED events after this point
                mWifiMetrics.addSoftApUpChangedEvent(
                        false,
                        mSpecifiedModeConfiguration.getTargetMode(),
                        mDefaultShutdownTimeoutMillis,
                        isBridgeRequired());
                updateApState(WifiManager.WIFI_AP_STATE_DISABLED,
                        WifiManager.WIFI_AP_STATE_DISABLING, 0);

                mSarManager.setSapWifiState(WifiManager.WIFI_AP_STATE_DISABLED);

                mApInterfaceName = null;
                mIfaceIsUp = false;
                mIfaceIsDestroyed = false;
                mRole = null;
                updateSoftApInfo(null, false);
            }

            private void updateUserBandPreferenceViolationMetricsIfNeeded(SoftApInfo apInfo) {
                // The band preference violation only need to detect in single AP mode.
                if (isBridgeRequired()) return;
                int band = mCurrentSoftApConfiguration.getBand();
                boolean bandPreferenceViolated =
                        (ScanResult.is24GHz(apInfo.getFrequency())
                            && !ApConfigUtil.containsBand(band,
                                    SoftApConfiguration.BAND_2GHZ))
                        || (ScanResult.is5GHz(apInfo.getFrequency())
                            && !ApConfigUtil.containsBand(band,
                                    SoftApConfiguration.BAND_5GHZ))
                        || (ScanResult.is6GHz(apInfo.getFrequency())
                            && !ApConfigUtil.containsBand(band,
                                    SoftApConfiguration.BAND_6GHZ));

                if (bandPreferenceViolated) {
                    Log.e(getTag(), "Channel does not satisfy user band preference: "
                            + apInfo.getFrequency());
                    mWifiMetrics.incrementNumSoftApUserBandPreferenceUnsatisfied();
                }
            }

            @Override
            public String getMessageLogRec(int what) {
                return SoftApManager.class.getSimpleName() + "." + RunnerState.class.getSimpleName()
                        + "." + getWhatToString(what);
            }

            @Override
            public boolean processMessageImpl(Message message) {
                switch (message.what) {
                    case CMD_ASSOCIATED_STATIONS_CHANGED:
                        if (!(message.obj instanceof WifiClient)) {
                            Log.e(getTag(), "Invalid type returned for"
                                    + " CMD_ASSOCIATED_STATIONS_CHANGED");
                            break;
                        }
                        boolean isConnected = (message.arg1 == 1);
                        WifiClient client = (WifiClient) message.obj;
                        Log.d(getTag(), "CMD_ASSOCIATED_STATIONS_CHANGED, Client: "
                                + client.getMacAddress().toString() + " isConnected: "
                                + isConnected);
                        updateConnectedClients(client, isConnected);
                        break;
                    case CMD_AP_INFO_CHANGED:
                        if (!(message.obj instanceof SoftApInfo)) {
                            Log.e(getTag(), "Invalid type returned for"
                                    + " CMD_AP_INFO_CHANGED");
                            break;
                        }
                        SoftApInfo apInfo = (SoftApInfo) message.obj;
                        if (apInfo.getFrequency() < 0) {
                            Log.e(getTag(), "Invalid ap channel frequency: "
                                    + apInfo.getFrequency());
                            break;
                        }
                        // Update shutdown timeout
                        apInfo.setAutoShutdownTimeoutMillis(mTimeoutEnabled
                                ? getShutdownTimeoutMillis() : 0);
                        updateSoftApInfo(apInfo, false);
                        break;
                    case CMD_INTERFACE_STATUS_CHANGED:
                        boolean isUp = message.arg1 == 1;
                        onUpChanged(isUp);
                        break;
                    case CMD_STOP:
                        if (mIfaceIsUp) {
                            updateApState(WifiManager.WIFI_AP_STATE_DISABLING,
                                    WifiManager.WIFI_AP_STATE_ENABLED, 0);
                        } else {
                            updateApState(WifiManager.WIFI_AP_STATE_DISABLING,
                                    WifiManager.WIFI_AP_STATE_ENABLING, 0);
                        }
                        quitNow();
                        break;
                    case CMD_START:
                        // Already started, ignore this command.
                        break;
                    case CMD_NO_ASSOCIATED_STATIONS_TIMEOUT:
                        if (!mTimeoutEnabled) {
                            Log.i(getTag(), "Timeout message received while timeout is disabled."
                                    + " Dropping.");
                            break;
                        }
                        if (getConnectedClientList().size() != 0) {
                            Log.i(getTag(), "Timeout message received but has clients. "
                                    + "Dropping.");
                            break;
                        }
                        mSoftApNotifier.showSoftApShutdownTimeoutExpiredNotification();
                        Log.i(getTag(), "Timeout message received. Stopping soft AP.");
                        updateApState(WifiManager.WIFI_AP_STATE_DISABLING,
                                WifiManager.WIFI_AP_STATE_ENABLED, 0);
                        writeSoftApStoppedEvent(STOP_EVENT_NO_USAGE_TIMEOUT);
                        quitNow();
                        break;
                    case CMD_NO_ASSOCIATED_STATIONS_TIMEOUT_ON_ONE_INSTANCE:
                        String idleInstance = (String) message.obj;
                        if (!isBridgedMode() || mCurrentSoftApInfoMap.size() != 2) {
                            Log.d(getTag(), "Ignore Bridged Mode Timeout message received"
                                    + " in single AP state. Dropping it from " + idleInstance);
                            break;
                        }
                        if (!mBridgedModeOpportunisticsShutdownTimeoutEnabled) {
                            Log.i(getTag(), "Bridged Mode Timeout message received"
                                    + " while timeout is disabled. Dropping.");
                            break;
                        }
                        Log.d(getTag(), "Instance idle timout on " + idleInstance);
                        removeIfaceInstanceFromBridgedApIface(idleInstance);
                        break;
                    case CMD_INTERFACE_DESTROYED:
                        Log.d(getTag(), "Interface was cleanly destroyed.");
                        updateApState(WifiManager.WIFI_AP_STATE_DISABLING,
                                WifiManager.WIFI_AP_STATE_ENABLED, 0);
                        mIfaceIsDestroyed = true;
                        writeSoftApStoppedEvent(STOP_EVENT_INTERFACE_DESTROYED);
                        quitNow();
                        break;
                    case CMD_FAILURE:
                        String instance = (String) message.obj;
                        if (isBridgedMode()) {
                            List<String> instances =
                                    mWifiNative.getBridgedApInstances(mApInterfaceName);
                            if (instance != null) {
                                Log.i(getTag(), "receive instanceFailure on " + instance);
                                removeIfaceInstanceFromBridgedApIface(instance);
                                // there is an available instance, keep AP on.
                                if (mCurrentSoftApInfoMap.size() == 1) {
                                    break;
                                }
                            } else if (mCurrentSoftApInfoMap.size() == 1 && instances != null
                                    && instances.size() == 1) {
                                if (!mCurrentSoftApInfoMap.containsKey(instances.get(0))) {
                                    // there is an available instance but the info doesn't be
                                    // updated, keep AP on and remove unavailable instance info.
                                    for (String unavailableInstance
                                            : mCurrentSoftApInfoMap.keySet()) {
                                        removeIfaceInstanceFromBridgedApIface(unavailableInstance);
                                    }
                                    break;
                                }
                            }
                        }
                        Log.w(getTag(), "hostapd failure, stop and report failure");
                        writeSoftApStoppedEvent(STOP_EVENT_HOSTAPD_FAILURE);
                        /* fall through */
                    case CMD_INTERFACE_DOWN:
                        Log.w(getTag(), "interface error, stop and report failure");
                        updateApState(WifiManager.WIFI_AP_STATE_FAILED,
                                WifiManager.WIFI_AP_STATE_ENABLED,
                                WifiManager.SAP_START_FAILURE_GENERAL);
                        updateApState(WifiManager.WIFI_AP_STATE_DISABLING,
                                WifiManager.WIFI_AP_STATE_FAILED, 0);
                        writeSoftApStoppedEvent(STOP_EVENT_INTERFACE_DOWN);
                        quitNow();
                        break;
                    case CMD_UPDATE_CAPABILITY:
                        SoftApCapability capability = (SoftApCapability) message.obj;
                        mCurrentSoftApCapability = new SoftApCapability(capability);
                        mWifiMetrics.updateSoftApCapability(
                                mCurrentSoftApCapability,
                                mSpecifiedModeConfiguration.getTargetMode(),
                                isBridgeRequired());
                        updateClientConnection();
                        updateSafeChannelFrequencyList();
                        break;
                    case CMD_UPDATE_CONFIG: {
                        SoftApConfiguration newConfig = (SoftApConfiguration) message.obj;
                        SoftApConfiguration originalConfig =
                                mSpecifiedModeConfiguration.getSoftApConfiguration();
                        if (!ApConfigUtil.checkConfigurationChangeNeedToRestart(
                                originalConfig, newConfig)) {
                            mSpecifiedModeConfiguration =
                                    new SoftApModeConfiguration(
                                            mSpecifiedModeConfiguration.getTargetMode(),
                                            newConfig,
                                            mCurrentSoftApCapability,
                                            mCountryCode,
                                            mSpecifiedModeConfiguration.getTetheringRequest());
                            Log.d(getTag(), "Configuration changed to " + newConfig);
                            if (mCurrentSoftApConfiguration.getMaxNumberOfClients()
                                    != newConfig.getMaxNumberOfClients()) {
                                Log.d(getTag(), "Max Client changed, reset to record the metrics");
                                mEverReportMetricsForMaxClient = false;
                            }
                            boolean needRescheduleTimeoutMessage =
                                    mCurrentSoftApConfiguration.getShutdownTimeoutMillis()
                                            != newConfig.getShutdownTimeoutMillis()
                                            || mTimeoutEnabled != newConfig.isAutoShutdownEnabled()
                                            || mBridgedModeOpportunisticsShutdownTimeoutEnabled
                                            != newConfig
                                            .isBridgedModeOpportunisticShutdownEnabledInternal();
                            updateChangeableConfiguration(newConfig);
                            updateClientConnection();
                            if (needRescheduleTimeoutMessage) {
                                for (String key : mSoftApTimeoutMessageMap.keySet()) {
                                    cancelTimeoutMessage(key);
                                }
                                rescheduleTimeoutMessages(mApInterfaceName);
                                // Update SoftApInfo
                                for (SoftApInfo info : mCurrentSoftApInfoMap.values()) {
                                    SoftApInfo newInfo = new SoftApInfo(info);
                                    newInfo.setAutoShutdownTimeoutMillis(mTimeoutEnabled
                                            ? getShutdownTimeoutMillis() : 0);
                                    updateSoftApInfo(newInfo, false);
                                }
                            }
                            mWifiMetrics.updateSoftApConfiguration(
                                    mCurrentSoftApConfiguration,
                                    mSpecifiedModeConfiguration.getTargetMode(),
                                    isBridgeRequired());
                        } else {
                            Log.d(getTag(), "Ignore the config: " + newConfig
                                    + " update since it requires restart");
                        }
                        break;
                    }
                    case CMD_UPDATE_COUNTRY_CODE:
                        String countryCode = (String) message.obj;
                        if (!TextUtils.isEmpty(countryCode)
                                && !TextUtils.equals(mCountryCode, countryCode)
                                && mWifiNative.setApCountryCode(
                                mApInterfaceName, countryCode.toUpperCase(Locale.ROOT))) {
                            Log.i(getTag(), "Update country code when Soft AP enabled from "
                                    + mCountryCode + " to " + countryCode);
                            mCountryCode = countryCode;
                        }
                        break;
                    case CMD_FORCE_DISCONNECT_PENDING_CLIENTS:
                        if (mPendingDisconnectClients.size() != 0) {
                            Log.d(getTag(), "Disconnect pending list is NOT empty");
                            mPendingDisconnectClients.forEach((pendingClient, reason)->
                                    mWifiNative.forceClientDisconnect(mApInterfaceName,
                                    pendingClient.getMacAddress(), reason));
                            sendMessageDelayed(
                                    SoftApStateMachine.CMD_FORCE_DISCONNECT_PENDING_CLIENTS,
                                    SOFT_AP_PENDING_DISCONNECTION_CHECK_DELAY_MS);
                        }
                        break;
                    case CMD_SAFE_CHANNEL_FREQUENCY_CHANGED:
                        updateSafeChannelFrequencyList();
                        if (!isBridgedMode() || mCurrentSoftApInfoMap.size() != 2) {
                            Log.d(getTag(), "Ignore safe channel changed in single AP state");
                            break;
                        }
                        Set<String> unavailableInstances = new HashSet<>();
                        for (SoftApInfo currentInfo : mCurrentSoftApInfoMap.values()) {
                            int sapFreq = currentInfo.getFrequency();
                            if (!mSafeChannelFrequencyList.contains(sapFreq)) {
                                int sapBand = ApConfigUtil.convertFrequencyToBand(sapFreq);
                                if (sapBand != ApConfigUtil.removeUnavailableBands(
                                            mCurrentSoftApCapability,
                                            sapBand, mCoexManager)) {
                                    unavailableInstances.add(currentInfo.getApInstanceIdentifier());
                                }
                            }
                        }
                        removeIfaceInstanceFromBridgedApIface(
                                getHighestFrequencyInstance(unavailableInstances));
                        break;
                    case CMD_HANDLE_WIFI_CONNECTED:
                        if (!isBridgeRequired() || mCurrentSoftApInfoMap.size() != 2) {
                            Log.d(getTag(), "Ignore wifi connected in single AP state");
                            break;
                        }
                        ConcreteClientModeManager cmm = (ConcreteClientModeManager) message.obj;
                        String wifiInterface = cmm.getInterfaceName();
                        WifiInfo wifiInfo = cmm.getConnectionInfo();
                        int wifiFreq = wifiInfo.getFrequency();
                        int wifiBand = ApConfigUtil.convertFrequencyToBand(wifiFreq);
                        List<Integer> bands = new ArrayList<Integer>();
                        bands.add(wifiBand);
                        String targetShutDownInstance = "";
                        if (wifiFreq > 0 && !mSafeChannelFrequencyList.contains(wifiFreq)) {
                            Log.i(getTag(), "Wifi connected to freq:" + wifiFreq
                                    + " which is unavailable for SAP");
                            for (SoftApInfo sapInfo : mCurrentSoftApInfoMap.values()) {
                                int sapBand =
                                        ApConfigUtil.convertFrequencyToBand(sapInfo.getFrequency());
                                if (sapBand == wifiBand) {
                                    targetShutDownInstance = sapInfo.getApInstanceIdentifier();
                                    Log.d(getTag(), "Remove the " + targetShutDownInstance
                                            + " instance which is running on the same band as "
                                            + "the wifi connection on an unsafe channel");
                                } else {
                                    bands.add(sapBand);
                                }
                            }
                            // Wifi may connect to different band as the SAP. For instances:
                            // Wifi connect to 6Ghz but bridged AP is running on 2.4Ghz + 5Ghz.
                            // In this case, targetShutDownInstance will be empty, check whether
                            // the chip supports this combination. If not, shutdown the highest
                            // frequency instance.
                            if (TextUtils.isEmpty(targetShutDownInstance)) {
                                // We have to use STA ifacename to query band combinations.
                                if (!mWifiNative.isBandCombinationSupported(wifiInterface, bands)) {
                                    removeIfaceInstanceFromBridgedApIface(
                                            getHighestFrequencyInstance(
                                                    mCurrentSoftApInfoMap.keySet()));
                                }
                            } else {
                                removeIfaceInstanceFromBridgedApIface(targetShutDownInstance);
                            }
                        }
                        break;
                    case CMD_PLUGGED_STATE_CHANGED:
                        boolean newIsPlugged = (message.arg1 != 0);
                        if (mIsPlugged != newIsPlugged) {
                            mIsPlugged = newIsPlugged;
                            if (mCurrentSoftApInfoMap.size() == 2) {
                                rescheduleBothBridgedInstancesTimeoutMessage();
                            }
                        }
                        break;
                    default:
                        return NOT_HANDLED;
                }
                return HANDLED;
            }
        }
    }

    // Logging code

    private int getCurrentStaFreqMhz() {
        int staFreqMhz = WifiInfo.UNKNOWN_FREQUENCY;
        for (ClientModeManager cmm : mActiveModeWarden.getClientModeManagers()) {
            WifiInfo wifiConnectedInfo = cmm.getConnectionInfo();
            if (wifiConnectedInfo != null) {
                staFreqMhz = wifiConnectedInfo.getFrequency();
                break;
            }
        }
        return staFreqMhz;
    }

    /**
     * Writes the SoftApStarted event to metrics. Only the first call will write the metrics, any
     * subsequent calls will be ignored.
     */
    public void writeSoftApStartedEvent(@StartResult int startResult) {
        if (mIsSoftApStartedEventWritten) {
            return;
        }
        mIsSoftApStartedEventWritten = true;
        int band1 = WifiScanner.WIFI_BAND_UNSPECIFIED;
        int band2 = WifiScanner.WIFI_BAND_UNSPECIFIED;
        @SoftApConfiguration.SecurityType int securityType = SoftApConfiguration.SECURITY_TYPE_OPEN;
        if (mCurrentSoftApConfiguration != null) {
            int[] bands = mCurrentSoftApConfiguration.getBands();
            if (bands.length >= 1) {
                band1 = bands[0];
            }
            if (bands.length >= 2) {
                band2 = bands[1];
            }
            securityType = mCurrentSoftApConfiguration.getSecurityType();
        }
        mWifiMetrics.writeSoftApStartedEvent(startResult,
                getRole(),
                band1,
                band2,
                ApConfigUtil.isBridgedModeSupported(mContext, mWifiNative),
                mWifiNative.isStaApConcurrencySupported(),
                ApConfigUtil.isStaWithBridgedModeSupported(mContext, mWifiNative),
                getCurrentStaFreqMhz(),
                securityType);
    }

    private void writeSoftApStoppedEvent(@StopEvent int stopEvent) {
        @WifiScanner.WifiBand int band = WifiScanner.WIFI_BAND_UNSPECIFIED;
        @WifiAnnotations.WifiStandard int standard = ScanResult.WIFI_STANDARD_UNKNOWN;
        for (SoftApInfo info : mCurrentSoftApInfoMap.values()) {
            band |= ScanResult.toBand(info.getFrequency());
            if (SdkLevel.isAtLeastS()) {
                standard = info.getWifiStandard();
            }
        }
        @SoftApConfiguration.SecurityType int securityType = SoftApConfiguration.SECURITY_TYPE_OPEN;
        if (mCurrentSoftApConfiguration != null) {
            securityType = mCurrentSoftApConfiguration.getSecurityType();
        }
        // TODO(b/245824786): Fill out the rest of the fields
        mWifiMetrics.writeSoftApStoppedEvent(
                stopEvent,
                getRole(),
                band,
                isBridgedMode(),
                mWifiNative.isStaApConcurrencySupported(),
                ApConfigUtil.isStaWithBridgedModeSupported(mContext, mWifiNative),
                getCurrentStaFreqMhz(),
                mDefaultShutdownTimeoutMillis > 0,
                -1,
                securityType,
                standard,
                -1,
                mDefaultShutdownIdleInstanceInBridgedModeTimeoutMillis > 0,
                -1,
                -1,
                null);
    }
}
