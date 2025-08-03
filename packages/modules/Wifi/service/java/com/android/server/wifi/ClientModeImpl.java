/*
 * Copyright (C) 2010 The Android Open Source Project
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

import static android.net.util.KeepalivePacketDataUtil.parseTcpKeepalivePacketData;
import static android.net.wifi.WifiConfiguration.NetworkSelectionStatus.DISABLED_NONE;
import static android.net.wifi.WifiConfiguration.NetworkSelectionStatus.DISABLED_NO_INTERNET_PERMANENT;
import static android.net.wifi.WifiConfiguration.NetworkSelectionStatus.DISABLED_NO_INTERNET_TEMPORARY;
import static android.net.wifi.WifiConfiguration.NetworkSelectionStatus.DISABLED_UNWANTED_LOW_RSSI;
import static android.net.wifi.WifiManager.WIFI_FEATURE_FILS_SHA256;
import static android.net.wifi.WifiManager.WIFI_FEATURE_FILS_SHA384;
import static android.net.wifi.WifiManager.WIFI_FEATURE_LINK_LAYER_STATS;
import static android.net.wifi.WifiManager.WIFI_FEATURE_TDLS;
import static android.net.wifi.WifiManager.WIFI_FEATURE_TRUST_ON_FIRST_USE;

import static com.android.server.wifi.ActiveModeManager.ROLE_CLIENT_LOCAL_ONLY;
import static com.android.server.wifi.ActiveModeManager.ROLE_CLIENT_PRIMARY;
import static com.android.server.wifi.ActiveModeManager.ROLE_CLIENT_SCAN_ONLY;
import static com.android.server.wifi.ActiveModeManager.ROLE_CLIENT_SECONDARY_LONG_LIVED;
import static com.android.server.wifi.ActiveModeManager.ROLE_CLIENT_SECONDARY_TRANSIENT;
import static com.android.server.wifi.WifiSettingsConfigStore.SECONDARY_WIFI_STA_FACTORY_MAC_ADDRESS;
import static com.android.server.wifi.WifiSettingsConfigStore.WIFI_STA_FACTORY_MAC_ADDRESS;
import static com.android.server.wifi.proto.WifiStatsLog.WIFI_DISCONNECT_REPORTED__FAILURE_CODE__SUPPLICANT_DISCONNECTED;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.BroadcastOptions;
import android.app.admin.SecurityLog;
import android.content.Context;
import android.content.Intent;
import android.net.CaptivePortalData;
import android.net.ConnectivityManager;
import android.net.DhcpResultsParcelable;
import android.net.InvalidPacketException;
import android.net.IpConfiguration;
import android.net.KeepalivePacketData;
import android.net.Layer2PacketParcelable;
import android.net.LinkProperties;
import android.net.MacAddress;
import android.net.NattKeepalivePacketData;
import android.net.Network;
import android.net.NetworkAgent;
import android.net.NetworkAgentConfig;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkInfo.DetailedState;
import android.net.RouteInfo;
import android.net.SocketKeepalive;
import android.net.StaticIpConfiguration;
import android.net.TcpKeepalivePacketData;
import android.net.TcpKeepalivePacketDataParcelable;
import android.net.Uri;
import android.net.ip.IIpClient;
import android.net.ip.IpClientCallbacks;
import android.net.ip.IpClientManager;
import android.net.networkstack.aidl.dhcp.DhcpOption;
import android.net.networkstack.aidl.ip.ReachabilityLossInfoParcelable;
import android.net.networkstack.aidl.ip.ReachabilityLossReason;
import android.net.shared.Layer2Information;
import android.net.shared.ProvisioningConfiguration;
import android.net.shared.ProvisioningConfiguration.ScanResultInfo;
import android.net.vcn.VcnManager;
import android.net.vcn.VcnNetworkPolicyResult;
import android.net.wifi.IWifiConnectedNetworkScorer;
import android.net.wifi.MloLink;
import android.net.wifi.ScanResult;
import android.net.wifi.SecurityParams;
import android.net.wifi.SupplicantState;
import android.net.wifi.WifiAnnotations.WifiStandard;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiContext;
import android.net.wifi.WifiEnterpriseConfig;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.DeviceMobilityState;
import android.net.wifi.WifiNetworkAgentSpecifier;
import android.net.wifi.WifiNetworkSpecifier;
import android.net.wifi.WifiSsid;
import android.net.wifi.flags.Flags;
import android.net.wifi.hotspot2.IProvisioningCallback;
import android.net.wifi.hotspot2.OsuProvider;
import android.net.wifi.nl80211.DeviceWiphyCapabilities;
import android.net.wifi.util.ScanResultUtil;
import android.os.BatteryStatsManager;
import android.os.Build;
import android.os.Bundle;
import android.os.ConditionVariable;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.PowerManager;
import android.os.Process;
import android.os.UserHandle;
import android.os.WorkSource;
import android.provider.Settings;
import android.system.OsConstants;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.Log;
import android.util.Pair;
import android.util.Range;

import androidx.annotation.RequiresApi;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.IState;
import com.android.internal.util.Protocol;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;
import com.android.modules.utils.HandlerExecutor;
import com.android.modules.utils.build.SdkLevel;
import com.android.net.module.util.Inet4AddressUtils;
import com.android.net.module.util.MacAddressUtils;
import com.android.net.module.util.NetUtils;
import com.android.server.wifi.ActiveModeManager.ClientRole;
import com.android.server.wifi.MboOceController.BtmFrameData;
import com.android.server.wifi.SupplicantStaIfaceHal.QosPolicyRequest;
import com.android.server.wifi.SupplicantStaIfaceHal.StaIfaceReasonCode;
import com.android.server.wifi.SupplicantStaIfaceHal.StaIfaceStatusCode;
import com.android.server.wifi.SupplicantStaIfaceHal.SupplicantEventCode;
import com.android.server.wifi.WifiCarrierInfoManager.SimAuthRequestData;
import com.android.server.wifi.WifiCarrierInfoManager.SimAuthResponseData;
import com.android.server.wifi.WifiNative.RxFateReport;
import com.android.server.wifi.WifiNative.TxFateReport;
import com.android.server.wifi.hotspot2.AnqpEvent;
import com.android.server.wifi.hotspot2.IconEvent;
import com.android.server.wifi.hotspot2.NetworkDetail;
import com.android.server.wifi.hotspot2.PasspointManager;
import com.android.server.wifi.hotspot2.WnmData;
import com.android.server.wifi.p2p.WifiP2pServiceImpl;
import com.android.server.wifi.proto.WifiStatsLog;
import com.android.server.wifi.proto.nano.WifiMetricsProto;
import com.android.server.wifi.proto.nano.WifiMetricsProto.StaEvent;
import com.android.server.wifi.proto.nano.WifiMetricsProto.WifiIsUnusableEvent;
import com.android.server.wifi.proto.nano.WifiMetricsProto.WifiUsabilityStats;
import com.android.server.wifi.util.ActionListenerWrapper;
import com.android.server.wifi.util.InformationElementUtil;
import com.android.server.wifi.util.NativeUtil;
import com.android.server.wifi.util.RssiUtil;
import com.android.server.wifi.util.StateMachineObituary;
import com.android.server.wifi.util.WifiPermissionsUtil;
import com.android.wifi.resources.R;

import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementation of ClientMode.  Event handling for Client mode logic is done here,
 * and all changes in connectivity state are initiated here.
 *
 * Note: No external modules should be calling into {@link ClientModeImpl}. Please plumb it via
 * {@link ClientModeManager} until b/160014176 is fixed.
 */
public class ClientModeImpl extends StateMachine implements ClientMode {
    private static final String NETWORKTYPE = "WIFI";
    @VisibleForTesting public static final short NUM_LOG_RECS_VERBOSE_LOW_MEMORY = 200;
    @VisibleForTesting public static final short NUM_LOG_RECS_VERBOSE = 3000;

    private static final String TAG = "WifiClientModeImpl";
    // Hardcoded constant used for caller to avoid triggering connect choice that force framework
    // to stick to the selected network. Do not change this value to maintain backward
    // compatibility.
    public static final String ATTRIBUTION_TAG_DISALLOW_CONNECT_CHOICE =
            "ATTRIBUTION_TAG_DISALLOW_CONNECT_CHOICE";

    private static final int IPCLIENT_STARTUP_TIMEOUT_MS = 2_000;
    private static final int IPCLIENT_SHUTDOWN_TIMEOUT_MS = 60_000; // 60 seconds
    private static final int NETWORK_AGENT_TEARDOWN_DELAY_MS = 5_000; // Max teardown delay.
    private static final int DISASSOC_AP_BUSY_DISABLE_DURATION_MS = 5 * 60 * 1000; // 5 minutes
    @VisibleForTesting public static final long CONNECTING_WATCHDOG_TIMEOUT_MS = 30_000; // 30 secs.
    public static final int PROVISIONING_TIMEOUT_FILS_CONNECTION_MS = 36_000; // 36 secs.
    @VisibleForTesting
    public static final String ARP_TABLE_PATH = "/proc/net/arp";
    private final WifiDeviceStateChangeManager mWifiDeviceStateChangeManager;

    private boolean mVerboseLoggingEnabled = false;

    /**
     * Log with error attribute
     *
     * @param s is string log
     */
    @Override
    protected void loge(String s) {
        Log.e(getTag(), s, null);
    }
    @Override
    protected void logd(String s) {
        Log.d(getTag(), s, null);
    }
    @Override
    protected void log(String s) {
        Log.d(getTag(), s, null);
    }
    private final WifiContext mContext;
    private final WifiMetrics mWifiMetrics;
    private final WifiMonitor mWifiMonitor;
    private final WifiNative mWifiNative;
    private final WifiPermissionsUtil mWifiPermissionsUtil;
    private final WifiConfigManager mWifiConfigManager;
    private final WifiConnectivityManager mWifiConnectivityManager;
    private final WifiBlocklistMonitor mWifiBlocklistMonitor;
    private final WifiDiagnostics mWifiDiagnostics;
    private final Clock mClock;
    private final WifiScoreCard mWifiScoreCard;
    private final WifiHealthMonitor mWifiHealthMonitor;
    private final WifiScoreReport mWifiScoreReport;
    private final WifiTrafficPoller mWifiTrafficPoller;
    private final PasspointManager mPasspointManager;
    private final WifiDataStall mWifiDataStall;
    private final RssiMonitor mRssiMonitor;
    private final LinkProbeManager mLinkProbeManager;
    private final MboOceController mMboOceController;
    private final McastLockManagerFilterController mMcastLockManagerFilterController;
    private final ActivityManager mActivityManager;
    private final FrameworkFacade mFacade;
    private final WifiStateTracker mWifiStateTracker;
    private final WrongPasswordNotifier mWrongPasswordNotifier;
    private final EapFailureNotifier mEapFailureNotifier;
    private final SimRequiredNotifier mSimRequiredNotifier;
    private final ConnectionFailureNotifier mConnectionFailureNotifier;
    private final WifiNetworkSuggestionsManager mWifiNetworkSuggestionsManager;
    private final ThroughputPredictor mThroughputPredictor;
    private final DeviceConfigFacade mDeviceConfigFacade;
    private final ScoringParams mScoringParams;
    private final WifiThreadRunner mWifiThreadRunner;
    private final ScanRequestProxy mScanRequestProxy;
    private final WifiLastResortWatchdog mWifiLastResortWatchdog;
    private final WakeupController mWakeupController;
    private final WifiLockManager mWifiLockManager;
    private final WifiP2pConnection mWifiP2pConnection;
    private final WifiGlobals mWifiGlobals;
    private final ClientModeManagerBroadcastQueue mBroadcastQueue;
    private final TelephonyManager mTelephonyManager;
    private final WifiSettingsConfigStore mSettingsConfigStore;
    private final long mId;

    private boolean mScreenOn = false;

    private final String mInterfaceName;
    private final ConcreteClientModeManager mClientModeManager;

    private final WifiNotificationManager mNotificationManager;
    private final WifiConnectivityHelper mWifiConnectivityHelper;
    private final QosPolicyRequestHandler mQosPolicyRequestHandler;

    private boolean mFailedToResetMacAddress = false;
    private int mLastSignalLevel = -1;
    private int mLastTxKbps = -1;
    private int mLastRxKbps = -1;
    private int mLastScanRssi = WifiInfo.INVALID_RSSI;
    private String mLastBssid;
    // TODO (b/162942761): Ensure this is reset when mTargetNetworkId is set.
    private int mLastNetworkId; // The network Id we successfully joined
    // The subId used by WifiConfiguration with SIM credential which was connected successfully
    private int mLastSubId;
    private String mLastSimBasedConnectionCarrierName;
    private URL mTermsAndConditionsUrl; // Indicates that the Passpoint network is captive
    @Nullable
    private WifiNative.ConnectionCapabilities mLastConnectionCapabilities;
    private int mPowerSaveDisableRequests = 0; // mask based on @PowerSaveClientType
    private boolean mIsUserSelected = false;
    private boolean mCurrentConnectionDetectedCaptivePortal;
    // Overrides StatsLog disconnect reason logging for NETWORK_DISCONNECTION_EVENT with specific
    // framework initiated disconnect reason code.
    private int mFrameworkDisconnectReasonOverride;

    private boolean mIpProvisioningTimedOut = false;

    private String getTag() {
        return TAG + "[" + mId + ":" + (mInterfaceName == null ? "unknown" : mInterfaceName) + "]";
    }

    private boolean mEnableRssiPolling = false;
    private int mRssiPollToken = 0;

    private PowerManager.WakeLock mSuspendWakeLock;

    /**
     * Value to set in wpa_supplicant "bssid" field when we don't want to restrict connection to
     * a specific AP.
     */
    public static final String SUPPLICANT_BSSID_ANY = "any";

    /**
     * The link properties of the wifi interface.
     * Do not modify this directly; use updateLinkProperties instead.
     */
    private LinkProperties mLinkProperties;

    private final Object mDhcpResultsParcelableLock = new Object();
    @NonNull
    private DhcpResultsParcelable mDhcpResultsParcelable = new DhcpResultsParcelable();

    // NOTE: Do not return to clients - see getConnectionInfo()
    private final ExtendedWifiInfo mWifiInfo;
    // TODO : remove this member. It should be possible to only call sendNetworkChangeBroadcast when
    // the state actually changed, and to deduce the state of the agent from the state of the
    // machine when generating the NetworkInfo for the broadcast.
    private DetailedState mNetworkAgentState;
    private final SupplicantStateTracker mSupplicantStateTracker;

    // Indicates that framework is attempting to roam, set true on CMD_START_ROAM, set false when
    // wifi connects or fails to connect
    private boolean mIsAutoRoaming = false;

    // Indicates that driver is attempting to allowlist roaming, set true on allowlist roam BSSID
    // associated, set false when wifi connects or fails to connect
    private boolean mIsLinkedNetworkRoaming = false;

    // Roaming failure count
    private int mRoamFailCount = 0;

    // This is the BSSID we are trying to associate to, it can be set to SUPPLICANT_BSSID_ANY
    // if we havent selected a BSSID for joining.
    private String mTargetBssid = SUPPLICANT_BSSID_ANY;
    private int mTargetBand = ScanResult.UNSPECIFIED;
    // This one is used to track the current target network ID. This is used for error
    // handling during connection setup since many error message from supplicant does not report
    // SSID. Once connected, it will be set to invalid
    // TODO (b/162942761): Ensure this is reset when mLastNetworkId is set.
    private int mTargetNetworkId = WifiConfiguration.INVALID_NETWORK_ID;
    private WifiConfiguration mTargetWifiConfiguration = null;
    @Nullable private VcnManager mVcnManager = null;

    // This is is used to track the number of TDLS peers enabled in driver via enableTdls()
    private Set<String> mEnabledTdlsPeers = new ArraySet<>();

    // Tracks the last NUD failure timestamp, and number of failures.
    private Pair<Long, Integer> mNudFailureCounter = new Pair<>(0L, 0);

    /**
     * Method to clear {@link #mTargetBssid} and reset the current connected network's
     * bssid in wpa_supplicant after a roam/connect attempt.
     */
    public boolean clearTargetBssid(String dbg) {
        WifiConfiguration config = mWifiConfigManager.getConfiguredNetwork(mTargetNetworkId);
        if (config == null) {
            return false;
        }
        String bssid = SUPPLICANT_BSSID_ANY;
        if (config.BSSID != null) {
            bssid = config.BSSID;
            if (mVerboseLoggingEnabled) {
                Log.d(getTag(), "force BSSID to " + bssid + "due to config");
            }
        }
        if (mVerboseLoggingEnabled) {
            logd(dbg + " clearTargetBssid " + bssid + " key=" + config.getProfileKey());
        }
        mTargetBssid = bssid;
        return mWifiNative.setNetworkBSSID(mInterfaceName, bssid);
    }

    /**
     * Set Config's default BSSID (for association purpose) and {@link #mTargetBssid}
     * @param config config need set BSSID
     * @param bssid  default BSSID to assocaite with when connect to this network
     * @return false -- does not change the current default BSSID of the configure
     *         true -- change the  current default BSSID of the configur
     */
    private boolean setTargetBssid(WifiConfiguration config, String bssid) {
        if (config == null || bssid == null) {
            return false;
        }
        if (config.BSSID != null) {
            bssid = config.BSSID;
            if (mVerboseLoggingEnabled) {
                Log.d(getTag(), "force BSSID to " + bssid + "due to config");
            }
        }
        if (mVerboseLoggingEnabled) {
            Log.d(getTag(), "setTargetBssid set to " + bssid + " key="
                    + config.getProfileKey());
        }
        mTargetBssid = bssid;
        config.getNetworkSelectionStatus().setNetworkSelectionBSSID(bssid);
        return true;
    }

    private volatile IpClientManager mIpClient;
    private IpClientCallbacksImpl mIpClientCallbacks;
    private static int sIpClientCallbacksIndex = 0;

    private final WifiNetworkFactory mNetworkFactory;
    private final UntrustedWifiNetworkFactory mUntrustedNetworkFactory;
    private final OemWifiNetworkFactory mOemWifiNetworkFactory;
    private final RestrictedWifiNetworkFactory mRestrictedWifiNetworkFactory;
    @VisibleForTesting
    InsecureEapNetworkHandler mInsecureEapNetworkHandler;
    boolean mLeafCertSent;
    @VisibleForTesting
    InsecureEapNetworkHandler.InsecureEapNetworkHandlerCallbacks
            mInsecureEapNetworkHandlerCallbacksImpl;
    private final MultiInternetManager mMultiInternetManager;

    @VisibleForTesting
    @Nullable
    WifiNetworkAgent mNetworkAgent;


    // Used to filter out requests we couldn't possibly satisfy.
    private final NetworkCapabilities mNetworkCapabilitiesFilter;

    /* The base for wifi message types */
    static final int BASE = Protocol.BASE_WIFI;

    /* BT connection state changed, e.g., connected/disconnected */
    static final int CMD_BLUETOOTH_CONNECTION_STATE_CHANGE              = BASE + 31;

    /* Supplicant commands after driver start*/
    /* Disconnect from a network */
    static final int CMD_DISCONNECT                                     = BASE + 73;
    /* Reconnect to a network */
    static final int CMD_RECONNECT                                      = BASE + 74;
    /* Reassociate to a network */
    static final int CMD_REASSOCIATE                                    = BASE + 75;

    /* Enables RSSI poll */
    static final int CMD_ENABLE_RSSI_POLL                               = BASE + 82;
    /* RSSI poll */
    static final int CMD_RSSI_POLL                                      = BASE + 83;
    /** Runs RSSI poll once */
    static final int CMD_ONESHOT_RSSI_POLL                              = BASE + 84;
    /* Enable suspend mode optimizations in the driver */
    static final int CMD_SET_SUSPEND_OPT_ENABLED                        = BASE + 86;
    /* L3 provisioning timed out*/
    static final int CMD_IP_PROVISIONING_TIMEOUT                        = BASE + 87;


    /**
     * Watchdog for protecting against b/16823537
     * Leave time for 4-way handshake to succeed
     */
    static final int ROAM_GUARD_TIMER_MSEC = 15000;

    int mRoamWatchdogCount = 0;
    /* Roam state watchdog */
    static final int CMD_ROAM_WATCHDOG_TIMER                            = BASE + 94;
    /* Screen change intent handling */
    static final int CMD_SCREEN_STATE_CHANGED                           = BASE + 95;

    /* Disconnecting state watchdog */
    static final int CMD_CONNECTING_WATCHDOG_TIMER                      = BASE + 96;

    /* SIM is removed; reset any cached data for it */
    static final int CMD_RESET_SIM_NETWORKS                             = BASE + 101;

    /**
     * Update the OOB Pseudonym which is retrieved from the carrier's entitlement server for
     * EAP-SIM/AKA/AKA' into supplicant.
     */
    static final int CMD_UPDATE_OOB_PSEUDONYM                               = BASE + 102;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = {"RESET_SIM_REASON_"},
            value = {
                    RESET_SIM_REASON_SIM_REMOVED,
                    RESET_SIM_REASON_SIM_INSERTED,
                    RESET_SIM_REASON_DEFAULT_DATA_SIM_CHANGED})
    @interface ResetSimReason {}
    static final int RESET_SIM_REASON_SIM_REMOVED              = 0;
    static final int RESET_SIM_REASON_SIM_INSERTED             = 1;
    static final int RESET_SIM_REASON_DEFAULT_DATA_SIM_CHANGED = 2;

    /** Connecting watchdog timeout counter */
    private int mConnectingWatchdogCount = 0;

    /* We now have a valid IP configuration. */
    static final int CMD_IP_CONFIGURATION_SUCCESSFUL                    = BASE + 138;
    /* We no longer have a valid IP configuration. */
    static final int CMD_IP_CONFIGURATION_LOST                          = BASE + 139;
    /* Link configuration (IP address, DNS, ...) changes notified via netlink */
    static final int CMD_UPDATE_LINKPROPERTIES                          = BASE + 140;

    static final int CMD_START_CONNECT                                  = BASE + 143;

    @VisibleForTesting
    public static final int NETWORK_STATUS_UNWANTED_DISCONNECT         = 0;
    private static final int NETWORK_STATUS_UNWANTED_VALIDATION_FAILED  = 1;
    @VisibleForTesting
    public static final int NETWORK_STATUS_UNWANTED_DISABLE_AUTOJOIN   = 2;

    static final int CMD_UNWANTED_NETWORK                               = BASE + 144;

    static final int CMD_START_ROAM                                     = BASE + 145;

    static final int CMD_NETWORK_STATUS                                 = BASE + 148;

    /* A layer 3 neighbor on the Wi-Fi link became unreachable. */
    static final int CMD_IP_REACHABILITY_LOST                           = BASE + 149;

    static final int CMD_IP_REACHABILITY_FAILURE                        = BASE + 150;

    static final int CMD_ACCEPT_UNVALIDATED                             = BASE + 153;

    /* used to offload sending IP packet */
    static final int CMD_START_IP_PACKET_OFFLOAD                        = BASE + 160;

    /* used to stop offload sending IP packet */
    static final int CMD_STOP_IP_PACKET_OFFLOAD                         = BASE + 161;

    /* Used to time out the creation of an IpClient instance. */
    static final int CMD_IPCLIENT_STARTUP_TIMEOUT                       = BASE + 165;

    /**
     * Used to handle messages bounced between ClientModeImpl and IpClient.
     */
    static final int CMD_IPV4_PROVISIONING_SUCCESS                      = BASE + 200;
    static final int CMD_IPV4_PROVISIONING_FAILURE                      = BASE + 201;

    /* Push a new APF program to the HAL */
    static final int CMD_INSTALL_PACKET_FILTER                          = BASE + 202;

    /* Enable/disable fallback packet filtering */
    static final int CMD_SET_FALLBACK_PACKET_FILTERING                  = BASE + 203;

    /* Enable/disable Neighbor Discovery offload functionality. */
    static final int CMD_CONFIG_ND_OFFLOAD                              = BASE + 204;

    /* Read the APF program & data buffer */
    static final int CMD_READ_PACKET_FILTER                             = BASE + 208;

    /** Used to add packet filter to apf. */
    static final int CMD_ADD_KEEPALIVE_PACKET_FILTER_TO_APF = BASE + 209;

    /** Used to remove packet filter from apf. */
    static final int CMD_REMOVE_KEEPALIVE_PACKET_FILTER_FROM_APF = BASE + 210;

    /**
     * Set maximum acceptable DTIM multiplier to hardware driver. Any multiplier larger than the
     * maximum value must not be accepted, it will cause packet loss higher than what the system
     * can accept, which will cause unexpected behavior for apps, and may interrupt the network
     * connection.
     */
    static final int CMD_SET_MAX_DTIM_MULTIPLIER = BASE + 211;

    @VisibleForTesting
    static final int CMD_PRE_DHCP_ACTION                                = BASE + 255;
    @VisibleForTesting
    static final int CMD_PRE_DHCP_ACTION_COMPLETE                       = BASE + 256;
    private static final int CMD_POST_DHCP_ACTION                       = BASE + 257;

    private static final int CMD_CONNECT_NETWORK                        = BASE + 258;
    private static final int CMD_SAVE_NETWORK                           = BASE + 259;

    /* Start connection to FILS AP*/
    static final int CMD_START_FILS_CONNECTION                          = BASE + 262;

    static final int CMD_IPCLIENT_CREATED                               = BASE + 300;

    @VisibleForTesting
    static final int CMD_ACCEPT_EAP_SERVER_CERTIFICATE                  = BASE + 301;

    @VisibleForTesting
    static final int CMD_REJECT_EAP_INSECURE_CONNECTION                 = BASE + 302;

    /* Tracks if suspend optimizations need to be disabled by DHCP,
     * screen or due to high perf mode.
     * When any of them needs to disable it, we keep the suspend optimizations
     * disabled
     */
    private int mSuspendOptNeedsDisabled = 0;

    private static final int SUSPEND_DUE_TO_DHCP = 1;
    private static final int SUSPEND_DUE_TO_HIGH_PERF = 1 << 1;
    private static final int SUSPEND_DUE_TO_SCREEN = 1 << 2;

    /* Timeout after which a network connection stuck in L3 provisioning is considered as local
    only. It should be at least equal to ProvisioningConfiguration#DEFAULT_TIMEOUT_MS */
    @VisibleForTesting
    public static final long WAIT_FOR_L3_PROVISIONING_TIMEOUT_MS = 18000;

    /** @see #isRecentlySelectedByTheUser */
    @VisibleForTesting
    public static final int LAST_SELECTED_NETWORK_EXPIRATION_AGE_MILLIS = 30 * 1000;

    /* Tracks if user has enabled Connected Mac Randomization through settings */

    int mRunningBeaconCount = 0;

    /* Parent state where connections are allowed */
    private State mConnectableState;
    /* Connecting/Connected to an access point */
    private State mConnectingOrConnectedState;
    /* Connecting to an access point */
    private State mL2ConnectingState;
    /* Connected at 802.11 (L2) level */
    private State mL2ConnectedState;
    /* Waiting for IpClient recreation completes */
    private State mWaitBeforeL3ProvisioningState;
    /* fetching IP after connection to access point (assoc+auth complete) */
    private State mL3ProvisioningState;
    /* Connected with IP addr */
    private State mL3ConnectedState;
    /* Roaming */
    private State mRoamingState;
    /* Network is not connected, supplicant assoc+auth is not complete */
    private State mDisconnectedState;

    /*
     * FILS connection related variables.
     */
    /* To indicate to IpClient whether HLP IEs were included or not in assoc request */
    private boolean mSentHLPs = false;
    /* Tracks IpClient start state until (FILS_)NETWORK_CONNECTION_EVENT event */
    private boolean mIpClientWithPreConnection = false;

    /**
     * Work source to use to blame usage on the WiFi service
     */
    public static final WorkSource WIFI_WORK_SOURCE = new WorkSource(Process.WIFI_UID);

    private final BatteryStatsManager mBatteryStatsManager;

    private final WifiCarrierInfoManager mWifiCarrierInfoManager;

    private final WifiPseudonymManager mWifiPseudonymManager;

    private final OnNetworkUpdateListener mOnNetworkUpdateListener;

    private final OnCarrierOffloadDisabledListener mOnCarrierOffloadDisabledListener;

    private final ClientModeImplMonitor mCmiMonitor;

    private final WifiNetworkSelector mWifiNetworkSelector;

    private final WifiInjector mWifiInjector;

    // Permanently disable a network due to no internet if the estimated probability of having
    // internet is less than this value.
    @VisibleForTesting
    public static final int PROBABILITY_WITH_INTERNET_TO_PERMANENTLY_DISABLE_NETWORK = 60;
    // Disable a network permanently due to wrong password even if the network had successfully
    // connected before wrong password failure on this network reached this threshold.
    public static final int THRESHOLD_TO_PERM_WRONG_PASSWORD = 3;

    // Maximum duration to continue to log Wifi usability stats after a data stall is triggered.
    @VisibleForTesting
    public static final long DURATION_TO_WAIT_ADD_STATS_AFTER_DATA_STALL_MS = 30 * 1000;
    private long mDataStallTriggerTimeMs = -1;
    private int mLastStatusDataStall = WifiIsUnusableEvent.TYPE_UNKNOWN;

    @Nullable
    private StateMachineObituary mObituary = null;

    @Nullable
    private WifiVcnNetworkPolicyChangeListener mVcnPolicyChangeListener;

    /** NETWORK_NOT_FOUND_EVENT event counter */
    private int mNetworkNotFoundEventCount = 0;

    private final WifiPseudonymManager.PseudonymUpdatingListener mPseudonymUpdatingListener;

    private final ApplicationQosPolicyRequestHandler mApplicationQosPolicyRequestHandler;


    /** Note that this constructor will also start() the StateMachine. */
    public ClientModeImpl(
            @NonNull WifiContext context,
            @NonNull WifiMetrics wifiMetrics,
            @NonNull Clock clock,
            @NonNull WifiScoreCard wifiScoreCard,
            @NonNull WifiStateTracker wifiStateTracker,
            @NonNull WifiPermissionsUtil wifiPermissionsUtil,
            @NonNull WifiConfigManager wifiConfigManager,
            @NonNull PasspointManager passpointManager,
            @NonNull WifiMonitor wifiMonitor,
            @NonNull WifiDiagnostics wifiDiagnostics,
            @NonNull WifiDataStall wifiDataStall,
            @NonNull ScoringParams scoringParams,
            @NonNull WifiThreadRunner wifiThreadRunner,
            @NonNull WifiNetworkSuggestionsManager wifiNetworkSuggestionsManager,
            @NonNull WifiHealthMonitor wifiHealthMonitor,
            @NonNull ThroughputPredictor throughputPredictor,
            @NonNull DeviceConfigFacade deviceConfigFacade,
            @NonNull ScanRequestProxy scanRequestProxy,
            @NonNull ExtendedWifiInfo wifiInfo,
            @NonNull WifiConnectivityManager wifiConnectivityManager,
            @NonNull WifiBlocklistMonitor wifiBlocklistMonitor,
            @NonNull ConnectionFailureNotifier connectionFailureNotifier,
            @NonNull NetworkCapabilities networkCapabilitiesFilter,
            @NonNull WifiNetworkFactory networkFactory,
            @NonNull UntrustedWifiNetworkFactory untrustedWifiNetworkFactory,
            @NonNull OemWifiNetworkFactory oemPaidWifiNetworkFactory,
            @NonNull RestrictedWifiNetworkFactory restrictedWifiNetworkFactory,
            @NonNull MultiInternetManager multiInternetManager,
            @NonNull WifiLastResortWatchdog wifiLastResortWatchdog,
            @NonNull WakeupController wakeupController,
            @NonNull WifiLockManager wifiLockManager,
            @NonNull FrameworkFacade facade,
            @NonNull Looper looper,
            @NonNull WifiNative wifiNative,
            @NonNull WrongPasswordNotifier wrongPasswordNotifier,
            @NonNull WifiTrafficPoller wifiTrafficPoller,
            @NonNull LinkProbeManager linkProbeManager,
            long id,
            @NonNull BatteryStatsManager batteryStatsManager,
            @NonNull SupplicantStateTracker supplicantStateTracker,
            @NonNull MboOceController mboOceController,
            @NonNull WifiCarrierInfoManager wifiCarrierInfoManager,
            @NonNull WifiPseudonymManager wifiPseudonymManager,
            @NonNull EapFailureNotifier eapFailureNotifier,
            @NonNull SimRequiredNotifier simRequiredNotifier,
            @NonNull WifiScoreReport wifiScoreReport,
            @NonNull WifiP2pConnection wifiP2pConnection,
            @NonNull WifiGlobals wifiGlobals,
            @NonNull String ifaceName,
            @NonNull ConcreteClientModeManager clientModeManager,
            @NonNull ClientModeImplMonitor cmiMonitor,
            @NonNull ClientModeManagerBroadcastQueue broadcastQueue,
            @NonNull WifiNetworkSelector wifiNetworkSelector,
            @NonNull TelephonyManager telephonyManager,
            @NonNull WifiInjector wifiInjector,
            @NonNull WifiSettingsConfigStore settingsConfigStore,
            boolean verboseLoggingEnabled,
            @NonNull WifiNotificationManager wifiNotificationManager,
            @NonNull WifiConnectivityHelper wifiConnectivityHelper) {
        super(TAG, looper);
        mWifiMetrics = wifiMetrics;
        mClock = clock;
        mWifiScoreCard = wifiScoreCard;
        mContext = context;
        mFacade = facade;
        mWifiNative = wifiNative;
        mWrongPasswordNotifier = wrongPasswordNotifier;
        mId = id;
        mEapFailureNotifier = eapFailureNotifier;
        mSimRequiredNotifier = simRequiredNotifier;
        mWifiTrafficPoller = wifiTrafficPoller;
        mLinkProbeManager = linkProbeManager;
        mMboOceController = mboOceController;
        mWifiCarrierInfoManager = wifiCarrierInfoManager;
        mWifiPseudonymManager = wifiPseudonymManager;
        mBroadcastQueue = broadcastQueue;
        mNetworkAgentState = DetailedState.DISCONNECTED;

        mBatteryStatsManager = batteryStatsManager;
        mWifiStateTracker = wifiStateTracker;

        mWifiPermissionsUtil = wifiPermissionsUtil;
        mWifiConfigManager = wifiConfigManager;

        mPasspointManager = passpointManager;

        mWifiMonitor = wifiMonitor;
        mWifiDiagnostics = wifiDiagnostics;
        mWifiDataStall = wifiDataStall;
        mThroughputPredictor = throughputPredictor;
        mDeviceConfigFacade = deviceConfigFacade;

        mWifiInfo = wifiInfo;
        mSupplicantStateTracker = supplicantStateTracker;
        mWifiConnectivityManager = wifiConnectivityManager;
        mWifiBlocklistMonitor = wifiBlocklistMonitor;
        mConnectionFailureNotifier = connectionFailureNotifier;

        mLinkProperties = new LinkProperties();
        mMcastLockManagerFilterController = new McastLockManagerFilterController();
        mActivityManager = context.getSystemService(ActivityManager.class);

        mLastBssid = null;
        mIpProvisioningTimedOut = false;
        mLastNetworkId = WifiConfiguration.INVALID_NETWORK_ID;
        mLastSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        mLastSimBasedConnectionCarrierName = null;
        mLastSignalLevel = -1;

        mScoringParams = scoringParams;
        mWifiThreadRunner = wifiThreadRunner;
        mScanRequestProxy = scanRequestProxy;
        mWifiScoreReport = wifiScoreReport;

        mNetworkCapabilitiesFilter = networkCapabilitiesFilter;
        mNetworkFactory = networkFactory;

        mUntrustedNetworkFactory = untrustedWifiNetworkFactory;
        mOemWifiNetworkFactory = oemPaidWifiNetworkFactory;
        mRestrictedWifiNetworkFactory = restrictedWifiNetworkFactory;
        mMultiInternetManager = multiInternetManager;

        mWifiLastResortWatchdog = wifiLastResortWatchdog;
        mWakeupController = wakeupController;
        mWifiLockManager = wifiLockManager;

        mWifiNetworkSuggestionsManager = wifiNetworkSuggestionsManager;
        mWifiHealthMonitor = wifiHealthMonitor;
        mWifiP2pConnection = wifiP2pConnection;
        mWifiGlobals = wifiGlobals;

        mInterfaceName = ifaceName;
        mClientModeManager = clientModeManager;
        mCmiMonitor = cmiMonitor;
        mTelephonyManager = telephonyManager;
        mSettingsConfigStore = settingsConfigStore;
        updateInterfaceCapabilities();
        mWifiDeviceStateChangeManager = wifiInjector.getWifiDeviceStateChangeManager();

        PowerManager powerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);

        mSuspendWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WifiSuspend");
        mSuspendWakeLock.setReferenceCounted(false);

        mOnNetworkUpdateListener = new OnNetworkUpdateListener();
        mWifiConfigManager.addOnNetworkUpdateListener(mOnNetworkUpdateListener);

        mOnCarrierOffloadDisabledListener = new OnCarrierOffloadDisabledListener();
        mWifiCarrierInfoManager.addOnCarrierOffloadDisabledListener(
                mOnCarrierOffloadDisabledListener);

        mWifiNetworkSelector = wifiNetworkSelector;
        mWifiInjector = wifiInjector;
        mQosPolicyRequestHandler = new QosPolicyRequestHandler(mInterfaceName, mWifiNative, this,
                mWifiInjector.getWifiHandlerThread());

        mRssiMonitor = new RssiMonitor(mWifiGlobals, mWifiThreadRunner, mWifiInfo, mWifiNative,
                mInterfaceName,
                () -> {
                    updateCapabilities();
                    updateCurrentConnectionInfo();
                },
                mDeviceConfigFacade);

        enableVerboseLogging(verboseLoggingEnabled);

        mNotificationManager = wifiNotificationManager;
        mWifiConnectivityHelper = wifiConnectivityHelper;
        mInsecureEapNetworkHandlerCallbacksImpl =
                new InsecureEapNetworkHandler.InsecureEapNetworkHandlerCallbacks() {
                @Override
                public void onAccept(String ssid, int networkId) {
                    log("Accept Root CA cert for " + ssid);
                    sendMessage(CMD_ACCEPT_EAP_SERVER_CERTIFICATE, networkId);
                }

                @Override
                public void onReject(String ssid, boolean disconnectRequired) {
                    log("Reject Root CA cert for " + ssid);
                    sendMessage(CMD_REJECT_EAP_INSECURE_CONNECTION,
                            WifiMetricsProto.ConnectionEvent.AUTH_FAILURE_REJECTED_BY_USER,
                            disconnectRequired ? 1 : 0, ssid);
                }

                @Override
                public void onError(String ssid) {
                    log("Insecure EAP network error for " + ssid);
                    sendMessage(CMD_REJECT_EAP_INSECURE_CONNECTION,
                            WifiMetricsProto.ConnectionEvent.AUTH_FAILURE_EAP_FAILURE,
                            0, ssid);
                }};
        mInsecureEapNetworkHandler = new InsecureEapNetworkHandler(
                mContext,
                mWifiConfigManager,
                mWifiNative,
                mFacade,
                mNotificationManager,
                mWifiInjector.getWifiDialogManager(),
                isTrustOnFirstUseSupported(),
                mWifiGlobals.isInsecureEnterpriseConfigurationAllowed(),
                mInsecureEapNetworkHandlerCallbacksImpl,
                mInterfaceName,
                getHandler());

        mPseudonymUpdatingListener = this::updatePseudonymFromOob;
        mApplicationQosPolicyRequestHandler = mWifiInjector.getApplicationQosPolicyRequestHandler();

        final int threshold =  mContext.getResources().getInteger(
                R.integer.config_wifiConfigurationWifiRunnerThresholdInMs);
        mConnectableState = new ConnectableState(threshold);
        mConnectingOrConnectedState = new ConnectingOrConnectedState(threshold);
        mL2ConnectingState = new L2ConnectingState(threshold);
        mL2ConnectedState = new L2ConnectedState(threshold);
        mWaitBeforeL3ProvisioningState = new WaitBeforeL3ProvisioningState(threshold);
        mL3ProvisioningState = new L3ProvisioningState(threshold);
        mL3ConnectedState = new L3ConnectedState(threshold);
        mRoamingState = new RoamingState(threshold);
        mDisconnectedState = new DisconnectedState(threshold);

        addState(mConnectableState); {
            addState(mConnectingOrConnectedState, mConnectableState); {
                addState(mL2ConnectingState, mConnectingOrConnectedState);
                addState(mL2ConnectedState, mConnectingOrConnectedState); {
                    addState(mWaitBeforeL3ProvisioningState, mL2ConnectedState);
                    addState(mL3ProvisioningState, mL2ConnectedState);
                    addState(mL3ConnectedState, mL2ConnectedState);
                    addState(mRoamingState, mL2ConnectedState);
                }
            }
            addState(mDisconnectedState, mConnectableState);
        }

        setInitialState(mDisconnectedState);

        setLogOnlyTransitions(false);

        // Start the StateMachine
        start();

        // update with initial role for ConcreteClientModeManager
        onRoleChanged();
        // Update current connection wifiInfo
        updateCurrentConnectionInfo();
    }

    private static final int[] WIFI_MONITOR_EVENTS = {
            WifiMonitor.TARGET_BSSID_EVENT,
            WifiMonitor.ASSOCIATED_BSSID_EVENT,
            WifiMonitor.ANQP_DONE_EVENT,
            WifiMonitor.ASSOCIATION_REJECTION_EVENT,
            WifiMonitor.AUTHENTICATION_FAILURE_EVENT,
            WifiMonitor.GAS_QUERY_DONE_EVENT,
            WifiMonitor.GAS_QUERY_START_EVENT,
            WifiMonitor.HS20_REMEDIATION_EVENT,
            WifiMonitor.HS20_DEAUTH_IMMINENT_EVENT,
            WifiMonitor.HS20_TERMS_AND_CONDITIONS_ACCEPTANCE_REQUIRED_EVENT,
            WifiMonitor.NETWORK_CONNECTION_EVENT,
            WifiMonitor.NETWORK_DISCONNECTION_EVENT,
            WifiMonitor.RX_HS20_ANQP_ICON_EVENT,
            WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT,
            WifiMonitor.SUP_REQUEST_IDENTITY,
            WifiMonitor.SUP_REQUEST_SIM_AUTH,
            WifiMonitor.MBO_OCE_BSS_TM_HANDLING_DONE,
            WifiMonitor.TRANSITION_DISABLE_INDICATION,
            WifiMonitor.NETWORK_NOT_FOUND_EVENT,
            WifiMonitor.TOFU_CERTIFICATE_EVENT,
            WifiMonitor.AUXILIARY_SUPPLICANT_EVENT,
            WifiMonitor.QOS_POLICY_RESET_EVENT,
            WifiMonitor.QOS_POLICY_REQUEST_EVENT,
            WifiMonitor.BSS_FREQUENCY_CHANGED_EVENT,
    };

    private void registerForWifiMonitorEvents()  {
        for (int event : WIFI_MONITOR_EVENTS) {
            mWifiMonitor.registerHandler(mInterfaceName, event, getHandler());
        }

        mWifiMetrics.registerForWifiMonitorEvents(mInterfaceName);
        mWifiLastResortWatchdog.registerForWifiMonitorEvents(mInterfaceName);
    }

    private void deregisterForWifiMonitorEvents()  {
        for (int event : WIFI_MONITOR_EVENTS) {
            mWifiMonitor.deregisterHandler(mInterfaceName, event, getHandler());
        }

        mWifiMetrics.deregisterForWifiMonitorEvents(mInterfaceName);
        mWifiLastResortWatchdog.deregisterForWifiMonitorEvents(mInterfaceName);
    }

    private static boolean isValidBssid(String bssidStr) {
        try {
            MacAddress bssid = MacAddress.fromString(bssidStr);
            return !Objects.equals(bssid, WifiManager.ALL_ZEROS_MAC_ADDRESS);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private void setMulticastFilter(boolean enabled) {
        if (mIpClient != null) {
            mIpClient.setMulticastFilter(enabled);
        }
    }

    /*
     * Log wifi event to SecurityLog if the event occurred on a managed network.
     */
    private void logEventIfManagedNetwork(@Nullable WifiConfiguration config,
            @SupplicantEventCode int eventCode, MacAddress bssid, String reasonString) {
        if (config == null || !mWifiPermissionsUtil
                .isAdmin(config.creatorUid, config.creatorName)) {
            return;
        }

        if (SdkLevel.isAtLeastT()) {
            int numRedactedOctets = mContext.getResources()
                    .getInteger(R.integer.config_wifiNumMaskedBssidOctetsInSecurityLog);
            String redactedBssid = ScanResultUtil.redactBssid(bssid, numRedactedOctets);
            if (eventCode == SupplicantStaIfaceHal.SUPPLICANT_EVENT_DISCONNECTED) {
                SecurityLog.writeEvent(SecurityLog.TAG_WIFI_DISCONNECTION, redactedBssid,
                        reasonString);
            } else {
                SecurityLog.writeEvent(SecurityLog.TAG_WIFI_CONNECTION, redactedBssid,
                        SupplicantStaIfaceHal.supplicantEventCodeToString(eventCode), reasonString);
            }
        }
    }

    protected void clearQueuedQosMessages() {
        removeMessages(WifiMonitor.QOS_POLICY_RESET_EVENT);
        removeMessages(WifiMonitor.QOS_POLICY_REQUEST_EVENT);
    }

    /**
     * Class to implement the MulticastLockManager.FilterController callback.
     */
    class McastLockManagerFilterController implements WifiMulticastLockManager.FilterController {
        /**
         * Start filtering Multicast v4 packets
         */
        public void startFilteringMulticastPackets() {
            setMulticastFilter(true);
        }

        /**
         * Stop filtering Multicast v4 packets
         */
        public void stopFilteringMulticastPackets() {
            setMulticastFilter(false);
        }
    }

    class IpClientCallbacksImpl extends IpClientCallbacks {
        private final ConditionVariable mWaitForCreationCv = new ConditionVariable(false);
        private final ConditionVariable mWaitForStopCv = new ConditionVariable(false);
        private int mIpClientCallbacksIndex;

        private IpClientCallbacksImpl() {
            mIpClientCallbacksIndex = ++sIpClientCallbacksIndex;
        }

        @Override
        public void onIpClientCreated(IIpClient ipClient) {
            if (mIpClientCallbacks != this) return;
            // IpClient may take a very long time (many minutes) to start at boot time. But after
            // that IpClient should start pretty quickly (a few seconds).
            // Blocking wait for 5 seconds first (for when the wait is short)
            // If IpClient is still not ready after blocking wait, async wait (for when wait is
            // long). Will drop all connection requests until IpClient is ready. Other requests
            // will still be processed.
            sendMessageAtFrontOfQueue(CMD_IPCLIENT_CREATED, mIpClientCallbacksIndex, 0,
                    new IpClientManager(ipClient, getName()));
            mWaitForCreationCv.open();
        }

        @Override
        public void onPreDhcpAction() {
            sendMessage(CMD_PRE_DHCP_ACTION, mIpClientCallbacksIndex);
        }

        @Override
        public void onPostDhcpAction() {
            sendMessage(CMD_POST_DHCP_ACTION, mIpClientCallbacksIndex);
        }

        @Override
        public void onNewDhcpResults(DhcpResultsParcelable dhcpResults) {
            if (dhcpResults != null) {
                sendMessage(CMD_IPV4_PROVISIONING_SUCCESS, mIpClientCallbacksIndex, 0, dhcpResults);
            } else {
                sendMessage(CMD_IPV4_PROVISIONING_FAILURE, mIpClientCallbacksIndex);
            }
        }

        @Override
        public void onProvisioningSuccess(LinkProperties newLp) {
            addPasspointInfoToLinkProperties(newLp);
            mWifiMetrics.logStaEvent(mInterfaceName, StaEvent.TYPE_CMD_IP_CONFIGURATION_SUCCESSFUL);
            sendMessage(CMD_UPDATE_LINKPROPERTIES, mIpClientCallbacksIndex, 0, newLp);
            sendMessage(CMD_IP_CONFIGURATION_SUCCESSFUL, mIpClientCallbacksIndex);
        }

        @Override
        public void onProvisioningFailure(LinkProperties newLp) {
            mWifiMetrics.logStaEvent(mInterfaceName, StaEvent.TYPE_CMD_IP_CONFIGURATION_LOST);
            sendMessage(CMD_IP_CONFIGURATION_LOST, mIpClientCallbacksIndex);
        }

        @Override
        public void onLinkPropertiesChange(LinkProperties newLp) {
            addPasspointInfoToLinkProperties(newLp);
            sendMessage(CMD_UPDATE_LINKPROPERTIES, mIpClientCallbacksIndex, 0, newLp);
        }

        @Override
        public void onReachabilityLost(String logMsg) {
            mWifiMetrics.logStaEvent(mInterfaceName, StaEvent.TYPE_CMD_IP_REACHABILITY_LOST);
            sendMessage(CMD_IP_REACHABILITY_LOST, mIpClientCallbacksIndex, 0, logMsg);
        }

        @Override
        public void onReachabilityFailure(ReachabilityLossInfoParcelable lossInfo) {
            sendMessage(CMD_IP_REACHABILITY_FAILURE, mIpClientCallbacksIndex, 0, lossInfo);
        }

        @Override
        public void installPacketFilter(byte[] filter) {
            sendMessage(CMD_INSTALL_PACKET_FILTER, mIpClientCallbacksIndex, 0, filter);
        }

        @Override
        public void startReadPacketFilter() {
            sendMessage(CMD_READ_PACKET_FILTER, mIpClientCallbacksIndex);
        }

        @Override
        public void setFallbackMulticastFilter(boolean enabled) {
            sendMessage(CMD_SET_FALLBACK_PACKET_FILTERING, mIpClientCallbacksIndex, 0, enabled);
        }

        @Override
        public void setNeighborDiscoveryOffload(boolean enabled) {
            sendMessage(CMD_CONFIG_ND_OFFLOAD, mIpClientCallbacksIndex, (enabled ? 1 : 0));
        }

        @Override
        public void onPreconnectionStart(List<Layer2PacketParcelable> packets) {
            sendMessage(CMD_START_FILS_CONNECTION, mIpClientCallbacksIndex, 0, packets);
        }

        @Override
        public void setMaxDtimMultiplier(int multiplier) {
            sendMessage(CMD_SET_MAX_DTIM_MULTIPLIER, mIpClientCallbacksIndex, multiplier);
        }

        @Override
        public void onQuit() {
            if (mIpClientCallbacks != this) return;
            mWaitForStopCv.open();
        }

        boolean awaitCreation() {
            return mWaitForCreationCv.block(IPCLIENT_STARTUP_TIMEOUT_MS);
        }

        boolean awaitShutdown() {
            return mWaitForStopCv.block(IPCLIENT_SHUTDOWN_TIMEOUT_MS);
        }

        int getCallbackIndex() {
            return mIpClientCallbacksIndex;
        }
    }

    private void stopIpClient() {
        if (mVerboseLoggingEnabled) {
            Log.v(getTag(), "stopIpClient IpClientWithPreConnection: "
                    + mIpClientWithPreConnection);
        }
        if (mIpClient != null) {
            if (mIpClientWithPreConnection) {
                mIpClient.notifyPreconnectionComplete(false);
            }
            mIpClient.stop();
        }
        mIpClientWithPreConnection = false;
        mSentHLPs = false;
    }

    private void stopDhcpSetup() {
        /* Restore power save and suspend optimizations */
        handlePostDhcpSetup();
        stopIpClient();
    }

    private List<DhcpOption> convertToInternalDhcpOptions(List<android.net.DhcpOption> options) {
        List<DhcpOption> internalOptions = new ArrayList<DhcpOption>();
        for (android.net.DhcpOption option : options) {
            DhcpOption internalOption = new DhcpOption();
            internalOption.type = option.getType();
            if (option.getValue() != null) {
                byte[] value = option.getValue();
                internalOption.value = Arrays.copyOf(value, value.length);
            }
            internalOptions.add(internalOption);
        }
        return internalOptions;
    }

    /**
     * Listener for config manager network config related events.
     * TODO (b/117601161) : Move some of the existing handling in WifiConnectivityManager's listener
     * for the same events.
     */
    private class OnNetworkUpdateListener implements
            WifiConfigManager.OnNetworkUpdateListener {
        @Override
        public void onNetworkRemoved(WifiConfiguration config) {
            // The current connected or connecting network has been removed, trigger a disconnect.
            if (config.networkId == mTargetNetworkId || config.networkId == mLastNetworkId) {
                // Disconnect and let autojoin reselect a new network
                mFrameworkDisconnectReasonOverride = WifiStatsLog.WIFI_DISCONNECT_REPORTED__FAILURE_CODE__DISCONNECT_NETWORK_REMOVED;
                sendMessage(CMD_DISCONNECT, StaEvent.DISCONNECT_NETWORK_REMOVED);
                // Log disconnection here, since the network config won't exist when the
                // disconnection event is received.
                String bssid = getConnectedBssidInternal();
                logEventIfManagedNetwork(config,
                        SupplicantStaIfaceHal.SUPPLICANT_EVENT_DISCONNECTED,
                        bssid != null ? MacAddress.fromString(bssid) : null,
                        "Network was removed");
            } else {
                WifiConfiguration currentConfig = getConnectedWifiConfiguration();
                if (currentConfig != null && currentConfig.isLinked(config)) {
                    logi("current network linked config removed, update allowlist networks");
                    updateLinkedNetworks(currentConfig);
                }
            }
            mWifiNative.removeNetworkCachedData(config.networkId);
        }

        @Override
        public void onNetworkUpdated(WifiConfiguration newConfig, WifiConfiguration oldConfig,
                boolean hasCredentialChanged) {
            if (hasCredentialChanged) {
                // Clear invalid cached data.
                mWifiNative.removeNetworkCachedData(oldConfig.networkId);
                mWifiBlocklistMonitor.handleNetworkRemoved(newConfig.SSID);
            }

            if (newConfig.networkId != mLastNetworkId)  {
                // nothing to do.
                return;
            }

            if (newConfig.isWifi7Enabled() != oldConfig.isWifi7Enabled()) {
                Log.w(getTag(), "Wi-Fi " + (newConfig.isWifi7Enabled() ? "enabled" : "disabled")
                        + " triggering disconnect");
                mFrameworkDisconnectReasonOverride =
                        WifiStatsLog.WIFI_DISCONNECT_REPORTED__FAILURE_CODE__DISCONNECT_NETWORK_WIFI7_TOGGLED;
                sendMessage(CMD_DISCONNECT, StaEvent.DISCONNECT_NETWORK_WIFI7_TOGGLED);
                return;
            }

            boolean isMetered = WifiConfiguration.isMetered(newConfig, mWifiInfo);
            boolean wasMetered = WifiConfiguration.isMetered(oldConfig, mWifiInfo);
            // Check if user/app change meteredOverride or trusted for connected network.
            if (isMetered == wasMetered
                    && (!SdkLevel.isAtLeastT() || newConfig.trusted == oldConfig.trusted)) {
                return;
            }

            if (SdkLevel.isAtLeastT()) {
                mWifiInfo.setTrusted(newConfig.trusted);
                if (!newConfig.trusted) {
                    Log.w(getTag(), "Network marked untrusted, triggering disconnect");
                    mFrameworkDisconnectReasonOverride = WifiStatsLog.WIFI_DISCONNECT_REPORTED__FAILURE_CODE__DISCONNECT_NETWORK_UNTRUSTED;
                    sendMessage(CMD_DISCONNECT, StaEvent.DISCONNECT_NETWORK_UNTRUSTED);
                    return;
                }
            }

            if (isMetered) {
                Log.w(getTag(), "Network marked metered, triggering disconnect");
                mFrameworkDisconnectReasonOverride = WifiStatsLog.WIFI_DISCONNECT_REPORTED__FAILURE_CODE__DISCONNECT_NETWORK_METERED;
                sendMessage(CMD_DISCONNECT, StaEvent.DISCONNECT_NETWORK_METERED);
                return;
            }

            Log.i(getTag(), "Network marked metered=" + isMetered
                    + " trusted=" + newConfig.trusted + ", triggering capabilities update");
            updateCapabilities(newConfig);
            updateCurrentConnectionInfo();
        }

        @Override
        public void onNetworkTemporarilyDisabled(WifiConfiguration config, int disableReason) {
            if (disableReason == DISABLED_NO_INTERNET_TEMPORARY) return;
            if (config.networkId == mTargetNetworkId || config.networkId == mLastNetworkId) {
                // Disconnect and let autojoin reselect a new network
                mFrameworkDisconnectReasonOverride = WifiStatsLog.WIFI_DISCONNECT_REPORTED__FAILURE_CODE__DISCONNECT_TEMP_DISABLED;
                sendMessage(CMD_DISCONNECT, StaEvent.DISCONNECT_NETWORK_TEMPORARY_DISABLED);
            }

        }

        @Override
        public void onNetworkPermanentlyDisabled(WifiConfiguration config, int disableReason) {
            // For DISABLED_NO_INTERNET_PERMANENT we do not need to remove the network
            // because supplicant won't be trying to reconnect. If this is due to a
            // preventAutomaticReconnect request from ConnectivityService, that service
            // will disconnect as appropriate.
            if (disableReason == DISABLED_NO_INTERNET_PERMANENT) return;
            if (config.networkId == mTargetNetworkId || config.networkId == mLastNetworkId) {
                // Disconnect and let autojoin reselect a new network
                mFrameworkDisconnectReasonOverride = WifiStatsLog.WIFI_DISCONNECT_REPORTED__FAILURE_CODE__DISCONNECT_PERM_DISABLED;
                sendMessage(CMD_DISCONNECT, StaEvent.DISCONNECT_NETWORK_PERMANENT_DISABLED);
            }
        }
    }

    private class OnCarrierOffloadDisabledListener implements
            WifiCarrierInfoManager.OnCarrierOffloadDisabledListener {

        @Override
        public void onCarrierOffloadDisabled(int subscriptionId, boolean merged) {
            int networkId = mTargetNetworkId == WifiConfiguration.INVALID_NETWORK_ID
                    ? mLastNetworkId : mTargetNetworkId;
            if (networkId == WifiConfiguration.INVALID_NETWORK_ID) {
                return;
            }
            WifiConfiguration configuration = mWifiConfigManager.getConfiguredNetwork(networkId);
            if (configuration != null && configuration.subscriptionId == subscriptionId
                    && configuration.carrierMerged == merged) {
                Log.i(getTag(), "Carrier network offload disabled, triggering disconnect");
                mFrameworkDisconnectReasonOverride = WifiStatsLog.WIFI_DISCONNECT_REPORTED__FAILURE_CODE__DISCONNECT_CARRIER_OFFLOAD_DISABLED;
                sendMessage(CMD_DISCONNECT, StaEvent.DISCONNECT_CARRIER_OFFLOAD_DISABLED);
            }
            mWifiConnectivityManager.clearCachedCandidates();
        }
    }

    /**
     * Method to update logging level in wifi service related classes.
     *
     * @param verbose int logging level to use
     */
    public void enableVerboseLogging(boolean verbose) {
        if (verbose) {
            mVerboseLoggingEnabled = true;
            setLogRecSize(mActivityManager.isLowRamDevice()
                    ? NUM_LOG_RECS_VERBOSE_LOW_MEMORY : NUM_LOG_RECS_VERBOSE);
        } else {
            mVerboseLoggingEnabled = false;
            setLogRecSize(mWifiGlobals.getClientModeImplNumLogRecs());
        }

        mWifiScoreReport.enableVerboseLogging(mVerboseLoggingEnabled);
        mSupplicantStateTracker.enableVerboseLogging(mVerboseLoggingEnabled);
        mWifiNative.enableVerboseLogging(mVerboseLoggingEnabled, mVerboseLoggingEnabled);
        mQosPolicyRequestHandler.enableVerboseLogging(mVerboseLoggingEnabled);
        mRssiMonitor.enableVerboseLogging(mVerboseLoggingEnabled);
    }

    /**
     * If there is only SAE-only networks with this auto-upgrade type,
     * this auto-upgrade SAE type is considered to be added explicitly.
     *
     * For R, Settings/WifiTrackerLib maps WPA3 SAE to WPA2, so there is
     * no chance to add or update a WPA3 SAE configuration to update
     * the auto-upgrade flag.
     */
    private void updateSaeAutoUpgradeFlagForUserSelectNetwork(int networkId) {
        if (SdkLevel.isAtLeastS()) return;
        if (mWifiGlobals.isWpa3SaeUpgradeEnabled()) return;

        WifiConfiguration config = mWifiConfigManager.getConfiguredNetwork(networkId);
        if (null == config) return;
        SecurityParams params = config.getSecurityParams(WifiConfiguration.SECURITY_TYPE_SAE);
        if (null == params || !params.isAddedByAutoUpgrade()) return;

        if (!mScanRequestProxy.isWpa3PersonalOnlyNetworkInRange(config.SSID)) return;
        if (mScanRequestProxy.isWpa2PersonalOnlyNetworkInRange(config.SSID)) return;
        if (mScanRequestProxy.isWpa2Wpa3PersonalTransitionNetworkInRange(config.SSID)) return;

        logd("update auto-upgrade flag for SAE");
        mWifiConfigManager.updateIsAddedByAutoUpgradeFlag(
                config.networkId, WifiConfiguration.SECURITY_TYPE_SAE, false);
    }

    /**
     * Given a network ID for connection, check the security parameters for a
     * disabled security type.
     *
     * Allow the connection only when there are APs with the better security
     * type (or transition mode), or no APs with the disabled security type.
     *
     * @param networkIdForConnection indicates the network id for the desired connection.
     * @return {@code true} if this connection request should be dropped; {@code false} otherwise.
     */
    private boolean shouldDropConnectionRequest(int networkIdForConnection) {
        WifiConfiguration config = mWifiConfigManager.getConfiguredNetwork(networkIdForConnection);
        if (null == config) return false;

        List<ScanResult> scanResults = mScanRequestProxy.getScanResults().stream()
                .filter(r -> TextUtils.equals(config.SSID, r.getWifiSsid().toString()))
                .collect(Collectors.toList());
        if (0 == scanResults.size()) return false;

        SecurityParams params;
        // Check disabled PSK network.
        params = config.getSecurityParams(WifiConfiguration.SECURITY_TYPE_PSK);
        if (null != params && !params.isEnabled()) {
            if (scanResults.stream().anyMatch(r ->
                    ScanResultUtil.isScanResultForSaeNetwork(r))) {
                return false;
            }
            if (!scanResults.stream().anyMatch(r ->
                    ScanResultUtil.isScanResultForPskOnlyNetwork(r))) {
                return false;
            }
            return true;
        }
        // Check disabled OPEN network.
        params = config.getSecurityParams(WifiConfiguration.SECURITY_TYPE_OPEN);
        if (null != params && !params.isEnabled()) {
            if (scanResults.stream().anyMatch(r ->
                    ScanResultUtil.isScanResultForOweNetwork(r))) {
                return false;
            }
            if (!scanResults.stream().anyMatch(r ->
                    ScanResultUtil.isScanResultForOpenOnlyNetwork(r))) {
                return false;
            }
            return true;
        }
        // Check disabled WPA2 Enterprise network.
        params = config.getSecurityParams(WifiConfiguration.SECURITY_TYPE_EAP);
        if (null != params && !params.isEnabled()) {
            if (scanResults.stream().anyMatch(r ->
                    ScanResultUtil.isScanResultForWpa3EnterpriseOnlyNetwork(r))) {
                return false;
            }
            if (scanResults.stream().anyMatch(r ->
                    ScanResultUtil.isScanResultForWpa3EnterpriseTransitionNetwork(r))) {
                return false;
            }
            if (!scanResults.stream().anyMatch(r ->
                    ScanResultUtil.isScanResultForWpa2EnterpriseOnlyNetwork(r))) {
                return false;
            }
            return true;
        }
        return false;
    }

    /**
     * Initiates connection to a network specified by the user/app. This method checks if the
     * requesting app holds the NETWORK_SETTINGS permission.
     *
     * @param netId Id network to initiate connection.
     * @param uid UID of the app requesting the connection.
     * @param forceReconnect Whether to force a connection even if we're connected to the same
     *                       network currently.
     * @param packageName package name of the app requesting the connection.
     */
    private void connectToUserSelectNetwork(int netId, int uid, boolean forceReconnect,
            @NonNull String packageName, @Nullable String attributionTag) {
        if (mWifiPermissionsUtil.checkNetworkSettingsPermission(uid)
                || mWifiPermissionsUtil.checkNetworkSetupWizardPermission(uid)) {
            mIsUserSelected = true;
        }
        logd("connectToUserSelectNetwork netId " + netId + ", uid " + uid + ", package "
                + packageName + ", forceReconnect = " + forceReconnect + ", isUserSelected = "
                + mIsUserSelected + ", attributionTag = " + attributionTag);
        updateSaeAutoUpgradeFlagForUserSelectNetwork(netId);
        if (!forceReconnect && (mLastNetworkId == netId || mTargetNetworkId == netId)) {
            // We're already connecting/connected to the user specified network, don't trigger a
            // reconnection unless it was forced.
            logi("connectToUserSelectNetwork already connecting/connected=" + netId);
        } else if (mIsUserSelected && shouldDropConnectionRequest(netId)) {
            logi("connectToUserSelectNetwork this network is disabled by admin.");
            WifiConfiguration config = mWifiConfigManager.getConfiguredNetwork(netId);
            String title = mContext.getString(R.string.wifi_network_disabled_by_admin_title);
            String message = mContext.getString(R.string.wifi_network_disabled_by_admin_message,
                    config.SSID);
            String buttonText = mContext.getString(R.string.wifi_network_disabled_by_admin_button);
            mWifiInjector.getWifiDialogManager().createLegacySimpleDialog(
                    title, message,
                    null /* positiveButtonText */,
                    null /* negativeButtonText */,
                    buttonText,
                    new WifiDialogManager.SimpleDialogCallback() {
                        @Override
                        public void onPositiveButtonClicked() {
                            // Not used.
                        }
                        @Override
                        public void onNegativeButtonClicked() {
                            // Not used.
                        }
                        @Override
                        public void onNeutralButtonClicked() {
                            // Not used.
                        }
                        @Override
                        public void onCancelled() {
                            // Not used.
                        }
                    }, mWifiThreadRunner).launchDialog();
        } else {
            if (mIsUserSelected && ATTRIBUTION_TAG_DISALLOW_CONNECT_CHOICE.equals(attributionTag)) {
                mIsUserSelected = false;
                logd("connectToUserSelectNetwork attributionTag override to disable user selected");
            }
            mWifiConnectivityManager.prepareForForcedConnection(netId);
            if (UserHandle.getAppId(uid) == Process.SYSTEM_UID) {
                mWifiMetrics.setNominatorForNetwork(netId,
                        WifiMetricsProto.ConnectionEvent.NOMINATOR_MANUAL);
            }
            startConnectToNetwork(netId, uid, SUPPLICANT_BSSID_ANY);
        }
    }

    /**
     * ******************************************************
     * Methods exposed for public use
     * ******************************************************
     */

    /**
     * Retrieve a Messenger for the ClientModeImpl Handler
     *
     * @return Messenger
     */
    public Messenger getMessenger() {
        return new Messenger(getHandler());
    }

    // For debugging, keep track of last message status handling
    // TODO, find an equivalent mechanism as part of parent class
    private static final int MESSAGE_HANDLING_STATUS_PROCESSED = 2;
    private static final int MESSAGE_HANDLING_STATUS_OK = 1;
    private static final int MESSAGE_HANDLING_STATUS_UNKNOWN = 0;
    private static final int MESSAGE_HANDLING_STATUS_REFUSED = -1;
    private static final int MESSAGE_HANDLING_STATUS_FAIL = -2;
    private static final int MESSAGE_HANDLING_STATUS_OBSOLETE = -3;
    private static final int MESSAGE_HANDLING_STATUS_DEFERRED = -4;
    private static final int MESSAGE_HANDLING_STATUS_DISCARD = -5;
    private static final int MESSAGE_HANDLING_STATUS_LOOPED = -6;
    private static final int MESSAGE_HANDLING_STATUS_HANDLING_ERROR = -7;

    private int mMessageHandlingStatus = 0;

    private int mOnTime = 0;
    private int mTxTime = 0;
    private int mRxTime = 0;

    private int mOnTimeScreenStateChange = 0;
    private long mLastOntimeReportTimeStamp = 0;
    private long mLastScreenStateChangeTimeStamp = 0;
    private int mOnTimeLastReport = 0;
    private int mTxTimeLastReport = 0;
    private int mRxTimeLastReport = 0;

    private WifiLinkLayerStats mLastLinkLayerStats;
    private long mLastLinkLayerStatsUpdate = 0;

    String reportOnTime() {
        long now = mClock.getWallClockMillis();
        StringBuilder sb = new StringBuilder();
        // Report stats since last report
        int on = mOnTime - mOnTimeLastReport;
        mOnTimeLastReport = mOnTime;
        int tx = mTxTime - mTxTimeLastReport;
        mTxTimeLastReport = mTxTime;
        int rx = mRxTime - mRxTimeLastReport;
        mRxTimeLastReport = mRxTime;
        int period = (int) (now - mLastOntimeReportTimeStamp);
        mLastOntimeReportTimeStamp = now;
        sb.append("[on:" + on + " tx:" + tx + " rx:" + rx + " period:" + period + "]");
        // Report stats since Screen State Changed
        on = mOnTime - mOnTimeScreenStateChange;
        period = (int) (now - mLastScreenStateChangeTimeStamp);
        sb.append(" from screen [on:" + on + " period:" + period + "]");
        return sb.toString();
    }

    /**
     * receives changes in the interface up/down events for the interface associated with this
     * ClientModeImpl. This is expected to be called from the ClientModeManager running on the
     * wifi handler thread.
     */
    public void onUpChanged(boolean isUp) {
        if (isUp && mFailedToResetMacAddress) {
            // When the firmware does a subsystem restart, wifi will disconnect but we may fail to
            // re-randomize the MAC address of the interface since it's undergoing recovery. Thus,
            // check every time the interface goes up and re-randomize if the failure was detected.
            if (mWifiGlobals.isConnectedMacRandomizationEnabled()) {
                mFailedToResetMacAddress = !mWifiNative.setStaMacAddress(
                        mInterfaceName, MacAddressUtils.createRandomUnicastAddress());
                if (mFailedToResetMacAddress) {
                    Log.e(getTag(), "Failed to set random MAC address on interface up");
                }
            }
        }
        // No need to handle interface down since it's already handled in the ClientModeManager.
    }

    public WifiLinkLayerStats getWifiLinkLayerStats() {
        if (mInterfaceName == null) {
            loge("getWifiLinkLayerStats called without an interface");
            return null;
        }
        mLastLinkLayerStatsUpdate = mClock.getWallClockMillis();
        WifiLinkLayerStats stats = null;
        if (isLinkLayerStatsSupported()) {
            if (isPrimary()) {
                stats = mWifiNative.getWifiLinkLayerStats(mInterfaceName);
            } else {
                if (mVerboseLoggingEnabled) {
                    Log.w(getTag(), "Can't getWifiLinkLayerStats on secondary iface");
                }
            }
        }
        if (stats != null) {
            mOnTime = stats.on_time;
            mTxTime = stats.tx_time;
            mRxTime = stats.rx_time;
            mRunningBeaconCount = stats.beacon_rx;
            mWifiInfo.updatePacketRates(stats, mLastLinkLayerStatsUpdate);
        } else {
            long mTxPkts = mFacade.getTxPackets(mInterfaceName);
            long mRxPkts = mFacade.getRxPackets(mInterfaceName);
            mWifiInfo.updatePacketRates(mTxPkts, mRxPkts, mLastLinkLayerStatsUpdate);
        }
        mWifiMetrics.incrementWifiLinkLayerUsageStats(mInterfaceName, stats);
        updateCurrentConnectionInfo();
        return stats;
    }

    private boolean isLinkLayerStatsSupported() {
        return (getSupportedFeatures() & WIFI_FEATURE_LINK_LAYER_STATS) != 0;
    }

    /**
     * Update interface capabilities
     * This method is used to update some of interface capabilities defined in overlay
     */
    private void updateInterfaceCapabilities() {
        DeviceWiphyCapabilities cap = getDeviceWiphyCapabilities();
        if (cap != null) {
            // Some devices don't have support of 11ax/be indicated by the chip,
            // so an override config value is used
            if (mContext.getResources().getBoolean(R.bool.config_wifi11beSupportOverride)) {
                cap.setWifiStandardSupport(ScanResult.WIFI_STANDARD_11BE, true);
            }
            if (mContext.getResources().getBoolean(R.bool.config_wifi11axSupportOverride)) {
                cap.setWifiStandardSupport(ScanResult.WIFI_STANDARD_11AX, true);
            }
            // Enable WPA3 SAE auto-upgrade offload
            if (Flags.getDeviceCrossAkmRoamingSupport() && SdkLevel.isAtLeastV()
                    && cap.getMaxNumberAkms() >= 2) {
                mWifiGlobals.setWpa3SaeUpgradeOffloadEnabled();
            }

            mWifiNative.setDeviceWiphyCapabilities(mInterfaceName, cap);
        }
    }

    @Override
    public DeviceWiphyCapabilities getDeviceWiphyCapabilities() {
        return mWifiNative.getDeviceWiphyCapabilities(mInterfaceName);
    }

    /**
     * Check if a Wi-Fi standard is supported
     *
     * @param standard A value from {@link ScanResult}'s {@code WIFI_STANDARD_}
     * @return {@code true} if standard is supported, {@code false} otherwise.
     */
    public boolean isWifiStandardSupported(@WifiStandard int standard) {
        return mWifiNative.isWifiStandardSupported(mInterfaceName, standard);
    }

    /**
     * Check whether 11ax is supported by the most recent connection.
     */
    public boolean mostRecentConnectionSupports11ax() {
        return mLastConnectionCapabilities != null
                && (mLastConnectionCapabilities.wifiStandard == ScanResult.WIFI_STANDARD_11AX
                || mLastConnectionCapabilities.wifiStandard == ScanResult.WIFI_STANDARD_11BE);
    }

    private byte[] getDstMacForKeepalive(KeepalivePacketData packetData)
            throws InvalidPacketException {
        try {
            InetAddress gateway = NetUtils.selectBestRoute(
                    mLinkProperties.getRoutes(), packetData.getDstAddress()).getGateway();
            String dstMacStr = macAddressFromRoute(gateway.getHostAddress());
            return NativeUtil.macAddressToByteArray(dstMacStr);
        } catch (NullPointerException | IllegalArgumentException e) {
            throw new InvalidPacketException(InvalidPacketException.ERROR_INVALID_IP_ADDRESS);
        }
    }

    private static int getEtherProtoForKeepalive(KeepalivePacketData packetData)
            throws InvalidPacketException {
        if (packetData.getDstAddress() instanceof Inet4Address) {
            return OsConstants.ETH_P_IP;
        } else if (packetData.getDstAddress() instanceof Inet6Address) {
            return OsConstants.ETH_P_IPV6;
        } else {
            throw new InvalidPacketException(InvalidPacketException.ERROR_INVALID_IP_ADDRESS);
        }
    }

    private int startWifiIPPacketOffload(int slot, KeepalivePacketData packetData,
            int intervalSeconds) {
        byte[] packet = null;
        byte[] dstMac = null;
        int proto = 0;

        try {
            packet = packetData.getPacket();
            dstMac = getDstMacForKeepalive(packetData);
            proto = getEtherProtoForKeepalive(packetData);
        } catch (InvalidPacketException e) {
            return e.getError();
        }

        int ret = mWifiNative.startSendingOffloadedPacket(
                mInterfaceName, slot, dstMac, packet, proto, intervalSeconds * 1000);
        if (ret != 0) {
            loge("startWifiIPPacketOffload(" + slot + ", " + intervalSeconds
                    + "): hardware error " + ret);
            return SocketKeepalive.ERROR_HARDWARE_ERROR;
        } else {
            return SocketKeepalive.SUCCESS;
        }
    }

    private int stopWifiIPPacketOffload(int slot) {
        int ret = mWifiNative.stopSendingOffloadedPacket(mInterfaceName, slot);
        if (ret != 0) {
            loge("stopWifiIPPacketOffload(" + slot + "): hardware error " + ret);
            return SocketKeepalive.ERROR_HARDWARE_ERROR;
        } else {
            return SocketKeepalive.SUCCESS;
        }
    }

    @Override
    public boolean isConnected() {
        return getCurrentState() == mL3ConnectedState;
    }

    @Override
    public boolean isConnecting() {
        IState state = getCurrentState();
        return state == mL2ConnectingState || state == mL2ConnectedState
                || state == mWaitBeforeL3ProvisioningState || state == mL3ProvisioningState;
    }

    @Override
    public boolean isRoaming() {
        return getCurrentState() == mRoamingState;
    }

    @Override
    public boolean isDisconnected() {
        return getCurrentState() == mDisconnectedState;
    }

    /**
     * Method checking if supplicant is in a transient state
     *
     * @return boolean true if in transient state
     */
    public boolean isSupplicantTransientState() {
        SupplicantState supplicantState = mWifiInfo.getSupplicantState();
        if (supplicantState == SupplicantState.ASSOCIATING
                || supplicantState == SupplicantState.AUTHENTICATING
                || supplicantState == SupplicantState.FOUR_WAY_HANDSHAKE
                || supplicantState == SupplicantState.GROUP_HANDSHAKE) {

            if (mVerboseLoggingEnabled) {
                Log.d(getTag(), "Supplicant is under transient state: " + supplicantState);
            }
            return true;
        } else {
            if (mVerboseLoggingEnabled) {
                Log.d(getTag(), "Supplicant is under steady state: " + supplicantState);
            }
        }

        return false;
    }

    /**
     * Get status information for the current connection, if any.
     * Note: This call is synchronized and hence safe to call from any thread (if called from wifi
     * thread, will execute synchronously).
     *
     * @return a {@link WifiInfo} object containing information about the current connection
     */
    @Override
    public WifiInfo getConnectionInfo() {
        return new WifiInfo(mWifiInfo);
    }

    /**
     * Blocking call to get the current DHCP results
     *
     * @return DhcpResultsParcelable current results
     */
    @NonNull
    public DhcpResultsParcelable syncGetDhcpResultsParcelable() {
        synchronized (mDhcpResultsParcelableLock) {
            return mDhcpResultsParcelable;
        }
    }

    /**
     * When the underlying interface is destroyed, we must immediately tell connectivity service to
     * mark network agent as disconnected and stop the ip client.
     */
    public void handleIfaceDestroyed() {
        handleNetworkDisconnect(false,
                WifiStatsLog.WIFI_DISCONNECT_REPORTED__FAILURE_CODE__IFACE_DESTROYED);
    }

    /** Stop this ClientModeImpl. Do not interact with ClientModeImpl after it has been stopped. */
    public void stop() {
        mInsecureEapNetworkHandler.cleanup();
        mSupplicantStateTracker.stop();
        mWifiScoreCard.noteWifiDisabled(mWifiInfo);
        // capture StateMachine LogRecs since we will lose them after we call quitNow()
        // This is used for debugging.
        mObituary = new StateMachineObituary(this);

        // quit discarding all unprocessed messages - this is to preserve the legacy behavior of
        // using sendMessageAtFrontOfQueue(CMD_SET_OPERATIONAL_MODE) which would force a state
        // transition immediately
        quitNow();

        mWifiConfigManager.removeOnNetworkUpdateListener(mOnNetworkUpdateListener);
        mWifiCarrierInfoManager
                .removeOnCarrierOffloadDisabledListener(mOnCarrierOffloadDisabledListener);
        if (mVcnPolicyChangeListener != null && SdkLevel.isAtLeastS()) {
            mVcnManager.removeVcnNetworkPolicyChangeListener(mVcnPolicyChangeListener);
            mVcnPolicyChangeListener = null;
        }
    }

    private void checkAbnormalConnectionFailureAndTakeBugReport(String ssid) {
        if (mDeviceConfigFacade.isAbnormalConnectionFailureBugreportEnabled()) {
            int reasonCode = mWifiScoreCard.detectAbnormalConnectionFailure(ssid);
            if (reasonCode != WifiHealthMonitor.REASON_NO_FAILURE) {
                String bugTitle = "Wi-Fi BugReport: abnormal "
                        + WifiHealthMonitor.FAILURE_REASON_NAME[reasonCode];
                String bugDetail = "Detect abnormal "
                        + WifiHealthMonitor.FAILURE_REASON_NAME[reasonCode];
                mWifiDiagnostics.takeBugReport(bugTitle, bugDetail);
            }
        }
    }

    private void checkAbnormalDisconnectionAndTakeBugReport() {
        if (mDeviceConfigFacade.isAbnormalDisconnectionBugreportEnabled()) {
            int reasonCode = mWifiScoreCard.detectAbnormalDisconnection(mInterfaceName);
            if (reasonCode != WifiHealthMonitor.REASON_NO_FAILURE) {
                String bugTitle = "Wi-Fi BugReport: abnormal "
                        + WifiHealthMonitor.FAILURE_REASON_NAME[reasonCode];
                String bugDetail = "Detect abnormal "
                        + WifiHealthMonitor.FAILURE_REASON_NAME[reasonCode];
                mWifiDiagnostics.takeBugReport(bugTitle, bugDetail);
            }
        }
    }

    /**
     * Retrieve the WifiMulticastLockManager.FilterController callback for registration.
     */
    public WifiMulticastLockManager.FilterController getMcastLockManagerFilterController() {
        return mMcastLockManagerFilterController;
    }

    /**
     * Blocking method to retrieve the passpoint icon.
     *
     * @param bssid representation of the bssid as a long
     * @param fileName name of the file
     *
     * @return boolean returning the result of the call
     */
    public boolean syncQueryPasspointIcon(long bssid, String fileName) {
        return mWifiThreadRunner.call(
                () -> mPasspointManager.queryPasspointIcon(bssid, fileName), false);
    }

    @Override
    public boolean requestAnqp(String bssid, Set<Integer> anqpIds, Set<Integer> hs20Subtypes) {
        return mWifiNative.requestAnqp(mInterfaceName, bssid, anqpIds, hs20Subtypes);
    }

    @Override
    public boolean requestVenueUrlAnqp(String bssid) {
        return mWifiNative.requestVenueUrlAnqp(mInterfaceName, bssid);
    }

    @Override
    public boolean requestIcon(String bssid, String fileName) {
        return mWifiNative.requestIcon(mInterfaceName, bssid, fileName);
    }

    /**
     * Disconnect from Access Point
     */
    public void disconnect() {
        mFrameworkDisconnectReasonOverride =
                WifiStatsLog.WIFI_DISCONNECT_REPORTED__FAILURE_CODE__DISCONNECT_GENERAL;
        sendMessage(CMD_DISCONNECT, StaEvent.DISCONNECT_GENERIC);
    }

    /**
     * Initiate a reconnection to AP
     */
    public void reconnect(WorkSource workSource) {
        sendMessage(CMD_RECONNECT, workSource);
    }

    /**
     * Initiate a re-association to AP
     */
    public void reassociate() {
        sendMessage(CMD_REASSOCIATE);
    }

    /**
     * Start subscription provisioning synchronously
     *
     * @param provider {@link OsuProvider} the provider to provision with
     * @param callback {@link IProvisioningCallback} callback for provisioning status
     * @return boolean true indicates provisioning was started, false otherwise
     */
    public boolean syncStartSubscriptionProvisioning(int callingUid, OsuProvider provider,
            IProvisioningCallback callback) {
        return mWifiThreadRunner.call(
                () -> mPasspointManager.startSubscriptionProvisioning(
                        callingUid, provider, callback), false);
    }

    /**
     * Get the supported feature set synchronously
     */
    public long getSupportedFeatures() {
        return mWifiNative.getSupportedFeatureSet(mInterfaceName);
    }

    /**
     * Method to enable/disable RSSI polling
     * @param enabled boolean idicating if polling should start
     */
    @VisibleForTesting
    public void enableRssiPolling(boolean enabled) {
        sendMessage(CMD_ENABLE_RSSI_POLL, enabled ? 1 : 0, 0);
    }

    /**
     * reset cached SIM credential data
     */
    public void resetSimAuthNetworks(@ResetSimReason int resetReason) {
        sendMessage(CMD_RESET_SIM_NETWORKS, resetReason);
    }

    /**
     * Get Network object of currently connected wifi network, or null if not connected.
     * @return Network object of current wifi network
     */
    public Network getCurrentNetwork() {
        if (getCurrentState() != mL3ConnectedState
                && getCurrentState() != mRoamingState) return null;
        return (mNetworkAgent != null) ? mNetworkAgent.getNetwork() : null;
    }

    /**
     * Enable TDLS for a specific MAC address
     */
    public boolean enableTdls(String remoteMacAddress, boolean enable) {
        boolean ret;
        if (enable && !canEnableTdls()) {
            return false;
        }
        ret = mWifiNative.startTdls(mInterfaceName, remoteMacAddress, enable);
        if (enable && ret) {
            mEnabledTdlsPeers.add(remoteMacAddress);
        } else {
            mEnabledTdlsPeers.remove(remoteMacAddress);
        }
        return ret;
    }

    /**
     * Enable TDLS for a specific IP address
     */
    public boolean enableTdlsWithRemoteIpAddress(String remoteIpAddress, boolean enable) {
        boolean ret;
        String remoteMacAddress = macAddressFromRoute(remoteIpAddress);
        if (remoteMacAddress == null) {
            return false;
        }
        ret = enableTdls(remoteMacAddress, enable);
        return ret;
    }

    /**
     *  Check if a TDLS session can be established
     */
    public boolean isTdlsOperationCurrentlyAvailable() {
        return (getSupportedFeatures() & WIFI_FEATURE_TDLS) != 0 && isConnected()
                && canEnableTdls();
    }

    /**
     *  Return the number of Mac addresses configured in the driver for TDLS connection.
     */
    public int getNumberOfEnabledTdlsSessions() {
        return mEnabledTdlsPeers.size();
    }

    /**
     *  Return the maximum number of TDLS sessions supported by the device.
     */
    public int getMaxSupportedConcurrentTdlsSessions() {
        return mWifiNative.getMaxSupportedConcurrentTdlsSessions(mInterfaceName);
    }

    private boolean canEnableTdls() {
        // This function returns -1 if HAL doesn't have support for retrieving this info.
        int maxTdlsSessionCount = mWifiNative.getMaxSupportedConcurrentTdlsSessions(mInterfaceName);
        if (maxTdlsSessionCount < 0) {
            return true;
        }
        if (mEnabledTdlsPeers.size() >= maxTdlsSessionCount) {
            Log.e(TAG, "canEnableTdls() returned false: maxTdlsSessionCount: "
                    + maxTdlsSessionCount + "EnabledTdlsPeers count: " + mEnabledTdlsPeers.size());
            return false;
        }
        return true;
    }

    /** Send a message indicating bluetooth connection state changed, e.g. connected/disconnected */
    public void onBluetoothConnectionStateChanged() {
        sendMessage(CMD_BLUETOOTH_CONNECTION_STATE_CHANGE);
    }

    /**
     * Trigger dump on the class IpClient object.
     */
    public void dumpIpClient(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (mIpClient != null) {
            // All dumpIpClient does is print this log message.
            // TODO: consider deleting this, since it's not useful.
            pw.println("IpClient logs have moved to dumpsys network_stack");
        }
    }

    private static String dhcpResultsParcelableToString(DhcpResultsParcelable dhcpResults) {
        return new StringBuilder()
                .append("baseConfiguration ").append(dhcpResults.baseConfiguration)
                .append("leaseDuration ").append(dhcpResults.leaseDuration)
                .append("mtu ").append(dhcpResults.mtu)
                .append("serverAddress ").append(dhcpResults.serverAddress)
                .append("serverHostName ").append(dhcpResults.serverHostName)
                .append("vendorInfo ").append(dhcpResults.vendorInfo)
                .toString();
    }

    @Override
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("Dump of ClientModeImpl id=" + mId);
        if (mObituary == null) {
            // StateMachine hasn't quit yet, dump `this` via StateMachineObituary's dump()
            // method for consistency with `else` branch.
            new StateMachineObituary(this).dump(fd, pw, args);
        } else {
            // StateMachine has quit and cleared all LogRecs.
            // Get them from the obituary instead.
            mObituary.dump(fd, pw, args);
        }
        mSupplicantStateTracker.dump(fd, pw, args);
        // Polls link layer stats and RSSI. This allows the stats to show up in
        // WifiScoreReport's dump() output when taking a bug report even if the screen is off.
        updateLinkLayerStatsRssiAndScoreReport();
        pw.println("mLinkProperties " + mLinkProperties);
        pw.println("mWifiInfo " + mWifiInfo);
        pw.println("mDhcpResultsParcelable "
                + dhcpResultsParcelableToString(mDhcpResultsParcelable));
        pw.println("mLastSignalLevel " + mLastSignalLevel);
        pw.println("mLastTxKbps " + mLastTxKbps);
        pw.println("mLastRxKbps " + mLastRxKbps);
        pw.println("mLastBssid " + mLastBssid);
        pw.println("mLastNetworkId " + mLastNetworkId);
        pw.println("mLastSubId " + mLastSubId);
        pw.println("mLastSimBasedConnectionCarrierName " + mLastSimBasedConnectionCarrierName);
        pw.println("mSuspendOptimizationsEnabled " + mContext.getResources().getBoolean(
                R.bool.config_wifiSuspendOptimizationsEnabled));
        pw.println("mSuspendOptNeedsDisabled " + mSuspendOptNeedsDisabled);
        pw.println("mPowerSaveDisableRequests " + mPowerSaveDisableRequests);
        dumpIpClient(fd, pw, args);
        pw.println("WifiScoreReport:");
        mWifiScoreReport.dump(fd, pw, args);
        pw.println("QosPolicyRequestHandler:");
        mQosPolicyRequestHandler.dump(fd, pw, args);
        pw.println();
    }

    /**
     * ******************************************************
     * Internal private functions
     * ******************************************************
     */

    private void logStateAndMessage(Message message, State state) {
        mMessageHandlingStatus = 0;
        if (mVerboseLoggingEnabled) {
            logd(" " + state.getClass().getSimpleName() + " " + getLogRecString(message));
        }
    }

    @Override
    protected boolean recordLogRec(Message msg) {
        switch (msg.what) {
            case CMD_RSSI_POLL:
                return mVerboseLoggingEnabled;
            default:
                return true;
        }
    }

    /**
     * Return the additional string to be logged by LogRec, default
     *
     * @param msg that was processed
     * @return information to be logged as a String
     */
    @Override
    protected String getLogRecString(Message msg) {
        WifiConfiguration config;
        Long now;
        String report;
        String key;
        StringBuilder sb = new StringBuilder();
        sb.append("screen=").append(mScreenOn ? "on" : "off");
        if (mMessageHandlingStatus != MESSAGE_HANDLING_STATUS_UNKNOWN) {
            sb.append("(").append(mMessageHandlingStatus).append(")");
        }
        if (msg.sendingUid > 0 && msg.sendingUid != Process.WIFI_UID) {
            sb.append(" uid=" + msg.sendingUid);
        }
        switch (msg.what) {
            case WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT:
                sb.append(" ");
                sb.append(Integer.toString(msg.arg1));
                sb.append(" ");
                sb.append(Integer.toString(msg.arg2));
                StateChangeResult stateChangeResult = (StateChangeResult) msg.obj;
                if (stateChangeResult != null) {
                    sb.append(stateChangeResult.toString());
                }
                break;
            case CMD_CONNECT_NETWORK:
            case CMD_SAVE_NETWORK: {
                ConnectNetworkMessage cnm = (ConnectNetworkMessage) msg.obj;
                sb.append(" ");
                sb.append(cnm.result.getNetworkId());
                config = mWifiConfigManager.getConfiguredNetwork(cnm.result.getNetworkId());
                if (config != null) {
                    sb.append(" ").append(config.getProfileKey());
                    sb.append(" nid=").append(config.networkId);
                    if (config.hiddenSSID) {
                        sb.append(" hidden");
                    }
                    if (config.preSharedKey != null
                            && !config.preSharedKey.equals("*")) {
                        sb.append(" hasPSK");
                    }
                    if (config.ephemeral) {
                        sb.append(" ephemeral");
                    }
                    sb.append(" cuid=").append(config.creatorUid);
                    sb.append(" suid=").append(config.lastUpdateUid);
                }
                break;
            }
            case WifiMonitor.ASSOCIATION_REJECTION_EVENT:
                if (msg.obj != null) {
                    sb.append(" ").append((AssocRejectEventInfo) msg.obj);
                }
                break;
            case WifiMonitor.NETWORK_CONNECTION_EVENT: {
                NetworkConnectionEventInfo connectionInfo = (NetworkConnectionEventInfo) msg.obj;
                sb.append(" ");
                sb.append(connectionInfo.networkId);
                sb.append(" ");
                sb.append(connectionInfo.isFilsConnection);
                sb.append(" ").append(mLastBssid);
                sb.append(" nid=").append(mLastNetworkId);
                config = getConnectedWifiConfigurationInternal();
                if (config != null) {
                    sb.append(" ").append(config.getProfileKey());
                }
                key = mWifiConfigManager.getLastSelectedNetworkConfigKey();
                if (key != null) {
                    sb.append(" last=").append(key);
                }
                break;
            }
            case WifiMonitor.TARGET_BSSID_EVENT:
            case WifiMonitor.ASSOCIATED_BSSID_EVENT:
                sb.append(" ");
                sb.append(Integer.toString(msg.arg1));
                sb.append(" ");
                sb.append(Integer.toString(msg.arg2));
                if (msg.obj != null) {
                    sb.append(" BSSID=").append((String) msg.obj);
                }
                if (mTargetBssid != null) {
                    sb.append(" Target Bssid=").append(mTargetBssid);
                }
                if (mLastBssid != null) {
                    sb.append(" Last Bssid=").append(mLastBssid);
                }
                sb.append(" roam=").append(Boolean.toString(mIsAutoRoaming));
                break;
            case WifiMonitor.NETWORK_DISCONNECTION_EVENT:
                if (msg.obj != null) {
                    sb.append(" ").append((DisconnectEventInfo) msg.obj);
                }
                if (mLastBssid != null) {
                    sb.append(" lastbssid=").append(mLastBssid);
                }
                if (mWifiInfo.getFrequency() != -1) {
                    sb.append(" freq=").append(mWifiInfo.getFrequency());
                    sb.append(" rssi=").append(mWifiInfo.getRssi());
                }
                break;
            case CMD_RSSI_POLL:
            case CMD_ONESHOT_RSSI_POLL:
            case CMD_UNWANTED_NETWORK:
                sb.append(" ");
                sb.append(Integer.toString(msg.arg1));
                sb.append(" ");
                sb.append(Integer.toString(msg.arg2));
                if (mWifiInfo.getSSID() != null) {
                    if (mWifiInfo.getSSID() != null) {
                        sb.append(" ").append(mWifiInfo.getSSID());
                    }
                }
                if (mWifiInfo.getBSSID() != null) {
                    sb.append(" ").append(mWifiInfo.getBSSID());
                }
                sb.append(" rssi=").append(mWifiInfo.getRssi());
                sb.append(" f=").append(mWifiInfo.getFrequency());
                sb.append(" sc=").append(mWifiInfo.getScore());
                sb.append(" link=").append(mWifiInfo.getLinkSpeed());
                sb.append(" tx=").append(
                        ((int) (mWifiInfo.getSuccessfulTxPacketsPerSecond() * 10)) / 10.0);
                sb.append(", ").append(
                        ((int) (mWifiInfo.getRetriedTxPacketsPerSecond() * 10)) / 10.0);
                sb.append(", ").append(((int) (mWifiInfo.getLostTxPacketsPerSecond() * 10)) / 10.0);
                sb.append(" rx=").append(
                        ((int) (mWifiInfo.getSuccessfulRxPacketsPerSecond() * 10)) / 10.0);
                sb.append(" bcn=" + mRunningBeaconCount);
                report = reportOnTime();
                if (report != null) {
                    sb.append(" ").append(report);
                }
                sb.append(" score=" + mWifiInfo.getScore());
                break;
            case CMD_START_CONNECT:
                sb.append(" ");
                sb.append(Integer.toString(msg.arg1));
                sb.append(" ");
                sb.append(Integer.toString(msg.arg2));
                config = mWifiConfigManager.getConfiguredNetwork(msg.arg1);
                if (config != null) {
                    sb.append(" targetConfigKey=").append(config.getProfileKey());
                    sb.append(" BSSID=" + config.BSSID);
                }
                if (mTargetBssid != null) {
                    sb.append(" targetBssid=").append(mTargetBssid);
                }
                sb.append(" roam=").append(Boolean.toString(mIsAutoRoaming));
                config = getConnectedWifiConfigurationInternal();
                if (config != null) {
                    sb.append(" currentConfigKey=").append(config.getProfileKey());
                }
                break;
            case CMD_START_ROAM:
                sb.append(" ");
                sb.append(Integer.toString(msg.arg1));
                sb.append(" ");
                sb.append(Integer.toString(msg.arg2));
                String bssid = (String) msg.obj;
                sb.append(" bssid=").append(bssid);
                if (mTargetBssid != null) {
                    sb.append(" ").append(mTargetBssid);
                }
                sb.append(" roam=").append(Boolean.toString(mIsAutoRoaming));
                sb.append(" fail count=").append(Integer.toString(mRoamFailCount));
                break;
            case CMD_PRE_DHCP_ACTION:
                sb.append(" ");
                sb.append(Integer.toString(msg.arg1));
                sb.append(" ");
                sb.append(Integer.toString(msg.arg2));
                sb.append(" txpkts=").append(mWifiInfo.txSuccess);
                sb.append(",").append(mWifiInfo.txBad);
                sb.append(",").append(mWifiInfo.txRetries);
                break;
            case CMD_POST_DHCP_ACTION:
                if (mLinkProperties != null) {
                    sb.append(" ");
                    sb.append(getLinkPropertiesSummary(mLinkProperties));
                }
                break;
            case WifiP2pServiceImpl.P2P_CONNECTION_CHANGED:
                sb.append(" ");
                sb.append(Integer.toString(msg.arg1));
                sb.append(" ");
                sb.append(Integer.toString(msg.arg2));
                if (msg.obj != null) {
                    NetworkInfo info = (NetworkInfo) msg.obj;
                    NetworkInfo.State state = info.getState();
                    NetworkInfo.DetailedState detailedState = info.getDetailedState();
                    if (state != null) {
                        sb.append(" st=").append(state);
                    }
                    if (detailedState != null) {
                        sb.append("/").append(detailedState);
                    }
                }
                break;
            case CMD_IP_CONFIGURATION_LOST:
                int count = -1;
                WifiConfiguration c = getConnectedWifiConfigurationInternal();
                if (c != null) {
                    count = c.getNetworkSelectionStatus().getDisableReasonCounter(
                            WifiConfiguration.NetworkSelectionStatus.DISABLED_DHCP_FAILURE);
                }
                sb.append(" ");
                sb.append(Integer.toString(msg.arg1));
                sb.append(" ");
                sb.append(Integer.toString(msg.arg2));
                sb.append(" failures: ");
                sb.append(Integer.toString(count));
                sb.append("/");
                sb.append(Integer.toString(mFacade.getIntegerSetting(
                        mContext, Settings.Global.WIFI_MAX_DHCP_RETRY_COUNT, 0)));
                if (mWifiInfo.getBSSID() != null) {
                    sb.append(" ").append(mWifiInfo.getBSSID());
                }
                sb.append(" bcn=" + mRunningBeaconCount);
                break;
            case CMD_UPDATE_LINKPROPERTIES:
                sb.append(" ");
                sb.append(Integer.toString(msg.arg1));
                sb.append(" ");
                sb.append(Integer.toString(msg.arg2));
                if (mLinkProperties != null) {
                    sb.append(" ");
                    sb.append(getLinkPropertiesSummary(mLinkProperties));
                }
                break;
            case CMD_IP_REACHABILITY_LOST:
                if (msg.obj != null) {
                    sb.append(" ").append((String) msg.obj);
                }
                break;
            case CMD_IP_REACHABILITY_FAILURE:
                if (msg.obj != null) {
                    sb.append(" ").append(/* ReachabilityLossInfoParcelable */ msg.obj);
                }
                break;
            case CMD_INSTALL_PACKET_FILTER:
                sb.append(" len=" + ((byte[]) msg.obj).length);
                break;
            case CMD_SET_FALLBACK_PACKET_FILTERING:
                sb.append(" enabled=" + (boolean) msg.obj);
                break;
            case CMD_SET_MAX_DTIM_MULTIPLIER:
                sb.append(" maximum multiplier=" + msg.arg2);
                break;
            case CMD_ROAM_WATCHDOG_TIMER:
                sb.append(" ");
                sb.append(Integer.toString(msg.arg1));
                sb.append(" ");
                sb.append(Integer.toString(msg.arg2));
                sb.append(" cur=").append(mRoamWatchdogCount);
                break;
            case CMD_CONNECTING_WATCHDOG_TIMER:
                sb.append(" ");
                sb.append(Integer.toString(msg.arg1));
                sb.append(" ");
                sb.append(Integer.toString(msg.arg2));
                sb.append(" cur=").append(mConnectingWatchdogCount);
                break;
            case CMD_IPV4_PROVISIONING_SUCCESS:
                sb.append(" ");
                sb.append(/* DhcpResultsParcelable */ msg.obj);
                break;
            case WifiMonitor.MBO_OCE_BSS_TM_HANDLING_DONE:
                BtmFrameData frameData = (BtmFrameData) msg.obj;
                if (frameData != null) {
                    sb.append(" ").append(frameData.toString());
                }
                break;
            case WifiMonitor.NETWORK_NOT_FOUND_EVENT:
                sb.append(" ssid=" + msg.obj);
                break;
            case WifiMonitor.BSS_FREQUENCY_CHANGED_EVENT:
                sb.append(" frequency=" + msg.arg1);
                break;
            default:
                sb.append(" ");
                sb.append(Integer.toString(msg.arg1));
                sb.append(" ");
                sb.append(Integer.toString(msg.arg2));
                break;
        }

        return sb.toString();
    }

    @Override
    protected String getWhatToString(int what) {
        switch (what) {
            case CMD_ACCEPT_UNVALIDATED:
                return "CMD_ACCEPT_UNVALIDATED";
            case CMD_ADD_KEEPALIVE_PACKET_FILTER_TO_APF:
                return "CMD_ADD_KEEPALIVE_PACKET_FILTER_TO_APF";
            case CMD_BLUETOOTH_CONNECTION_STATE_CHANGE:
                return "CMD_BLUETOOTH_CONNECTION_STATE_CHANGE";
            case CMD_CONFIG_ND_OFFLOAD:
                return "CMD_CONFIG_ND_OFFLOAD";
            case CMD_CONNECTING_WATCHDOG_TIMER:
                return "CMD_CONNECTING_WATCHDOG_TIMER";
            case CMD_CONNECT_NETWORK:
                return "CMD_CONNECT_NETWORK";
            case CMD_DISCONNECT:
                return "CMD_DISCONNECT";
            case CMD_ENABLE_RSSI_POLL:
                return "CMD_ENABLE_RSSI_POLL";
            case CMD_INSTALL_PACKET_FILTER:
                return "CMD_INSTALL_PACKET_FILTER";
            case CMD_IP_CONFIGURATION_LOST:
                return "CMD_IP_CONFIGURATION_LOST";
            case CMD_IP_CONFIGURATION_SUCCESSFUL:
                return "CMD_IP_CONFIGURATION_SUCCESSFUL";
            case CMD_IP_REACHABILITY_LOST:
                return "CMD_IP_REACHABILITY_LOST";
            case CMD_IP_REACHABILITY_FAILURE:
                return "CMD_IP_REACHABILITY_FAILURE";
            case CMD_IPCLIENT_STARTUP_TIMEOUT:
                return "CMD_IPCLIENT_STARTUP_TIMEOUT";
            case CMD_IPV4_PROVISIONING_FAILURE:
                return "CMD_IPV4_PROVISIONING_FAILURE";
            case CMD_IPV4_PROVISIONING_SUCCESS:
                return "CMD_IPV4_PROVISIONING_SUCCESS";
            case CMD_NETWORK_STATUS:
                return "CMD_NETWORK_STATUS";
            case CMD_ONESHOT_RSSI_POLL:
                return "CMD_ONESHOT_RSSI_POLL";
            case CMD_POST_DHCP_ACTION:
                return "CMD_POST_DHCP_ACTION";
            case CMD_PRE_DHCP_ACTION:
                return "CMD_PRE_DHCP_ACTION";
            case CMD_PRE_DHCP_ACTION_COMPLETE:
                return "CMD_PRE_DHCP_ACTION_COMPLETE";
            case CMD_READ_PACKET_FILTER:
                return "CMD_READ_PACKET_FILTER";
            case CMD_REASSOCIATE:
                return "CMD_REASSOCIATE";
            case CMD_RECONNECT:
                return "CMD_RECONNECT";
            case CMD_REMOVE_KEEPALIVE_PACKET_FILTER_FROM_APF:
                return "CMD_REMOVE_KEEPALIVE_PACKET_FILTER_FROM_APF";
            case CMD_RESET_SIM_NETWORKS:
                return "CMD_RESET_SIM_NETWORKS";
            case CMD_ROAM_WATCHDOG_TIMER:
                return "CMD_ROAM_WATCHDOG_TIMER";
            case CMD_RSSI_POLL:
                return "CMD_RSSI_POLL";
            case CMD_SAVE_NETWORK:
                return "CMD_SAVE_NETWORK";
            case CMD_SCREEN_STATE_CHANGED:
                return "CMD_SCREEN_STATE_CHANGED";
            case CMD_SET_FALLBACK_PACKET_FILTERING:
                return "CMD_SET_FALLBACK_PACKET_FILTERING";
            case CMD_SET_MAX_DTIM_MULTIPLIER:
                return "CMD_SET_MAX_DTIM_MULTIPLIER";
            case CMD_SET_SUSPEND_OPT_ENABLED:
                return "CMD_SET_SUSPEND_OPT_ENABLED";
            case CMD_START_CONNECT:
                return "CMD_START_CONNECT";
            case CMD_START_FILS_CONNECTION:
                return "CMD_START_FILS_CONNECTION";
            case CMD_START_IP_PACKET_OFFLOAD:
                return "CMD_START_IP_PACKET_OFFLOAD";
            case CMD_START_ROAM:
                return "CMD_START_ROAM";
            case CMD_STOP_IP_PACKET_OFFLOAD:
                return "CMD_STOP_IP_PACKET_OFFLOAD";
            case CMD_UNWANTED_NETWORK:
                return "CMD_UNWANTED_NETWORK";
            case CMD_UPDATE_LINKPROPERTIES:
                return "CMD_UPDATE_LINKPROPERTIES";
            case CMD_IPCLIENT_CREATED:
                return "CMD_IPCLIENT_CREATED";
            case CMD_ACCEPT_EAP_SERVER_CERTIFICATE:
                return "CMD_ACCEPT_EAP_SERVER_CERTIFICATE";
            case CMD_REJECT_EAP_INSECURE_CONNECTION:
                return "CMD_REJECT_EAP_SERVER_CERTIFICATE";
            case WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT:
                return "SUPPLICANT_STATE_CHANGE_EVENT";
            case WifiMonitor.AUTHENTICATION_FAILURE_EVENT:
                return "AUTHENTICATION_FAILURE_EVENT";
            case WifiMonitor.SUP_REQUEST_IDENTITY:
                return "SUP_REQUEST_IDENTITY";
            case WifiMonitor.NETWORK_CONNECTION_EVENT:
                return "NETWORK_CONNECTION_EVENT";
            case WifiMonitor.NETWORK_DISCONNECTION_EVENT:
                return "NETWORK_DISCONNECTION_EVENT";
            case WifiMonitor.ASSOCIATED_BSSID_EVENT:
                return "ASSOCIATED_BSSID_EVENT";
            case WifiMonitor.TARGET_BSSID_EVENT:
                return "TARGET_BSSID_EVENT";
            case WifiMonitor.ASSOCIATION_REJECTION_EVENT:
                return "ASSOCIATION_REJECTION_EVENT";
            case WifiMonitor.ANQP_DONE_EVENT:
                return "ANQP_DONE_EVENT";
            case WifiMonitor.RX_HS20_ANQP_ICON_EVENT:
                return "RX_HS20_ANQP_ICON_EVENT";
            case WifiMonitor.GAS_QUERY_DONE_EVENT:
                return "GAS_QUERY_DONE_EVENT";
            case WifiMonitor.HS20_REMEDIATION_EVENT:
                return "HS20_REMEDIATION_EVENT";
            case WifiMonitor.HS20_DEAUTH_IMMINENT_EVENT:
                return "HS20_DEAUTH_IMMINENT_EVENT";
            case WifiMonitor.HS20_TERMS_AND_CONDITIONS_ACCEPTANCE_REQUIRED_EVENT:
                return "HS20_TERMS_AND_CONDITIONS_ACCEPTANCE_REQUIRED_EVENT";
            case WifiMonitor.GAS_QUERY_START_EVENT:
                return "GAS_QUERY_START_EVENT";
            case WifiMonitor.MBO_OCE_BSS_TM_HANDLING_DONE:
                return "MBO_OCE_BSS_TM_HANDLING_DONE";
            case WifiMonitor.TRANSITION_DISABLE_INDICATION:
                return "TRANSITION_DISABLE_INDICATION";
            case WifiP2pServiceImpl.GROUP_CREATING_TIMED_OUT:
                return "GROUP_CREATING_TIMED_OUT";
            case WifiP2pServiceImpl.P2P_CONNECTION_CHANGED:
                return "P2P_CONNECTION_CHANGED";
            case WifiP2pServiceImpl.DISCONNECT_WIFI_REQUEST:
                return "DISCONNECT_WIFI_REQUEST";
            case WifiP2pServiceImpl.DISCONNECT_WIFI_RESPONSE:
                return "DISCONNECT_WIFI_RESPONSE";
            case WifiP2pServiceImpl.SET_MIRACAST_MODE:
                return "SET_MIRACAST_MODE";
            case WifiP2pServiceImpl.BLOCK_DISCOVERY:
                return "BLOCK_DISCOVERY";
            case WifiMonitor.NETWORK_NOT_FOUND_EVENT:
                return "NETWORK_NOT_FOUND_EVENT";
            case WifiMonitor.TOFU_CERTIFICATE_EVENT:
                return "TOFU_CERTIFICATE_EVENT";
            case WifiMonitor.BSS_FREQUENCY_CHANGED_EVENT:
                return "BSS_FREQUENCY_CHANGED_EVENT";
            case RunnerState.STATE_ENTER_CMD:
                return "Enter";
            case RunnerState.STATE_EXIT_CMD:
                return "Exit";
            default:
                return "what:" + what;
        }
    }

    /** Check whether this connection is the primary connection on the device. */
    private boolean isPrimary() {
        return mClientModeManager.getRole() == ROLE_CLIENT_PRIMARY;
    }

    private boolean isScanOnly() {
        return mClientModeManager.getRole() == ActiveModeManager.ROLE_CLIENT_SCAN_ONLY;
    }

    /** Check whether this connection is the secondary internet wifi connection. */
    private boolean isSecondaryInternet() {
        return mClientModeManager.getRole() == ROLE_CLIENT_SECONDARY_LONG_LIVED
                && mClientModeManager.isSecondaryInternet();
    }

    /** Check whether this connection is for local only network. */
    private boolean isLocalOnly() {
        return mClientModeManager.getRole() == ROLE_CLIENT_LOCAL_ONLY;
    }

    /** Check whether this connection is for local only network due to Ip Provisioning Timeout. */
    @Override
    public boolean isIpProvisioningTimedOut() {
        return mIpProvisioningTimedOut;
    }

    /**
     * Check if originaly requested as local only for ClientModeManager before fallback.
     * A secondary role could fallback to primary due to hardware support limit.
     * @return true if the original request for ClientModeManager is local only.
     */
    public boolean isRequestedForLocalOnly(WifiConfiguration currentWifiConfiguration,
            String currentBssid) {
        Set<Integer> uids =
                mNetworkFactory.getSpecificNetworkRequestUids(
                        currentWifiConfiguration, currentBssid);
        // Check if there is an active specific request in WifiNetworkFactory for local only.
        return !uids.isEmpty();
    }

    private void handleScreenStateChanged(boolean screenOn) {
        mScreenOn = screenOn;
        if (mVerboseLoggingEnabled) {
            logd(" handleScreenStateChanged Enter: screenOn=" + screenOn
                    + " mSuspendOptimizationsEnabled="
                    + mContext.getResources().getBoolean(
                            R.bool.config_wifiSuspendOptimizationsEnabled)
                    + " state " + getCurrentState().getName());
        }
        if (isPrimary() || isSecondaryInternet()) {
            // Only enable RSSI polling on primary STA, none of the secondary STA use-cases
            // can become the default route when other networks types that provide internet
            // connectivity (e.g. cellular) are available. So, no point in scoring
            // these connections for the purpose of switching between wifi and other network
            // types.
            // TODO(b/179518316): Enable this for secondary transient STA also if external scorer
            // is in charge of MBB.
            enableRssiPolling(screenOn);
        }
        if (mContext.getResources().getBoolean(R.bool.config_wifiSuspendOptimizationsEnabled)) {
            int shouldReleaseWakeLock = 0;
            if (screenOn) {
                sendMessage(CMD_SET_SUSPEND_OPT_ENABLED, 0, shouldReleaseWakeLock);
            } else {
                if (isConnected()) {
                    // Allow 2s for suspend optimizations to be set
                    mSuspendWakeLock.acquire(2000);
                    shouldReleaseWakeLock = 1;
                }
                sendMessage(CMD_SET_SUSPEND_OPT_ENABLED, 1, shouldReleaseWakeLock);
            }
        }

        if (isConnected()) {
            getWifiLinkLayerStats();
        }
        mOnTimeScreenStateChange = mOnTime;
        mLastScreenStateChangeTimeStamp = mLastLinkLayerStatsUpdate;

        if (mVerboseLoggingEnabled) log("handleScreenStateChanged Exit: " + screenOn);
    }

    private void setSuspendOptimizationsNative(int reason, boolean enabled) {
        if (mVerboseLoggingEnabled) {
            log("setSuspendOptimizationsNative: " + reason + " " + enabled
                    + " -want " + mContext.getResources().getBoolean(
                            R.bool.config_wifiSuspendOptimizationsEnabled)
                    + " stack:" + Thread.currentThread().getStackTrace()[2].getMethodName()
                    + " - " + Thread.currentThread().getStackTrace()[3].getMethodName()
                    + " - " + Thread.currentThread().getStackTrace()[4].getMethodName()
                    + " - " + Thread.currentThread().getStackTrace()[5].getMethodName());
        }
        //mWifiNative.setSuspendOptimizations(enabled);

        if (enabled) {
            mSuspendOptNeedsDisabled &= ~reason;
            /* None of dhcp, screen or highperf need it disabled and user wants it enabled */
            if (mSuspendOptNeedsDisabled == 0
                    && mContext.getResources().getBoolean(
                            R.bool.config_wifiSuspendOptimizationsEnabled)) {
                if (mVerboseLoggingEnabled) {
                    log("setSuspendOptimizationsNative do it " + reason + " " + enabled
                            + " stack:" + Thread.currentThread().getStackTrace()[2].getMethodName()
                            + " - " + Thread.currentThread().getStackTrace()[3].getMethodName()
                            + " - " + Thread.currentThread().getStackTrace()[4].getMethodName()
                            + " - " + Thread.currentThread().getStackTrace()[5].getMethodName());
                }
                mWifiNative.setSuspendOptimizations(mInterfaceName, true);
            }
        } else {
            mSuspendOptNeedsDisabled |= reason;
            mWifiNative.setSuspendOptimizations(mInterfaceName, false);
        }
    }

    /**
     * Makes a record of the user intent about suspend optimizations.
     */
    private void setSuspendOptimizations(int reason, boolean enabled) {
        if (mVerboseLoggingEnabled) log("setSuspendOptimizations: " + reason + " " + enabled);
        if (enabled) {
            mSuspendOptNeedsDisabled &= ~reason;
        } else {
            mSuspendOptNeedsDisabled |= reason;
        }
        if (mVerboseLoggingEnabled) log("mSuspendOptNeedsDisabled " + mSuspendOptNeedsDisabled);
    }

    private void updateMloLinkFromPollResults(MloLink link, WifiSignalPollResults pollResults) {
        if (link == null) return;
        int linkId = link.getLinkId();
        link.setRssi(pollResults.getRssi(linkId));
        link.setTxLinkSpeedMbps(pollResults.getTxLinkSpeed(linkId));
        link.setRxLinkSpeedMbps(pollResults.getRxLinkSpeed(linkId));
        link.setChannel(ScanResult.convertFrequencyMhzToChannelIfSupported(
                pollResults.getFrequency(linkId)));
        link.setBand(ScanResult.toBand(pollResults.getFrequency(linkId)));
        if (mVerboseLoggingEnabled) {
            logd("updateMloLinkFromPollResults: linkId=" + linkId + " rssi=" + link.getRssi()
                    + " channel=" + link.getChannel()
                    + " band=" + link.getBand()
                    + " TxLinkspeed=" + link.getTxLinkSpeedMbps()
                    + " RxLinkSpeed=" + link.getRxLinkSpeedMbps());

        }
    }

    private void updateMloLinkFromScanResult(MloLink link) {
        if (link == null || link.getApMacAddress() == null) return;
        ScanDetailCache scanDetailCache = mWifiConfigManager.getScanDetailCacheForNetwork(
                mWifiInfo.getNetworkId());
        if (scanDetailCache == null) return;
        ScanResult matchingScanResult = scanDetailCache.getScanResult(
                link.getApMacAddress().toString());
        if (matchingScanResult == null) return;
        link.setRssi(matchingScanResult.level);
        if (mVerboseLoggingEnabled) {
            logd("updateMloLinkFromScanResult: linkId=" + link.getLinkId() + " rssi="
                    + link.getRssi());
        }
    }

    /*
     * Fetch link layer stats, RSSI, linkspeed, and frequency on current connection
     * and update Network capabilities
     */
    private WifiLinkLayerStats updateLinkLayerStatsRssiSpeedFrequencyCapabilities(long txBytes,
            long rxBytes) {
        WifiLinkLayerStats stats = getWifiLinkLayerStats();
        WifiSignalPollResults pollResults = mWifiNative.signalPoll(mInterfaceName);
        if (pollResults == null) {
            return stats;
        }

        int newRssi = pollResults.getRssi();
        int newTxLinkSpeed = pollResults.getTxLinkSpeed();
        int newFrequency = pollResults.getFrequency();
        int newRxLinkSpeed = pollResults.getRxLinkSpeed();
        boolean updateNetworkCapabilities = false;

        if (mVerboseLoggingEnabled) {
            logd("updateLinkLayerStatsRssiSpeedFrequencyCapabilities rssi=" + newRssi
                    + " TxLinkspeed=" + newTxLinkSpeed + " freq=" + newFrequency
                    + " RxLinkSpeed=" + newRxLinkSpeed);
        }

        for (MloLink link : mWifiInfo.getAffiliatedMloLinks()) {
            if (pollResults.isAvailable(link.getLinkId())
                    && (link.getState() == MloLink.MLO_LINK_STATE_IDLE
                    || link.getState() == MloLink.MLO_LINK_STATE_ACTIVE)) {
                updateMloLinkFromPollResults(link, pollResults);
            } else {
                updateMloLinkFromScanResult(link);
            }
        }

        /*
         * set Tx link speed only if it is valid
         */
        if (newTxLinkSpeed > 0) {
            mWifiInfo.setLinkSpeed(newTxLinkSpeed);
            mWifiInfo.setTxLinkSpeedMbps(newTxLinkSpeed);
        }
        /*
         * set Rx link speed only if it is valid
         */
        if (newRxLinkSpeed > 0) {
            mWifiInfo.setRxLinkSpeedMbps(newRxLinkSpeed);
        }
        if (newFrequency > 0) {
            if (mWifiInfo.getFrequency() != newFrequency) {
                updateNetworkCapabilities = true;
            }
            mWifiInfo.setFrequency(newFrequency);
        }
        // updateLinkBandwidth() requires the latest frequency information
        if (newRssi > WifiInfo.INVALID_RSSI && newRssi < WifiInfo.MAX_RSSI) {
            /*
             * Positive RSSI is possible when devices are close(~0m apart) to each other.
             * And there are some driver/firmware implementation, where they avoid
             * reporting large negative rssi values by adding 256.
             * so adjust the valid rssi reports for such implementations.
             */
            if (newRssi > (WifiInfo.INVALID_RSSI + 256)) {
                Log.wtf(getTag(), "Error! +ve value RSSI: " + newRssi);
                newRssi -= 256;
            }
            int oldRssi = mWifiInfo.getRssi();
            mWifiInfo.setRssi(newRssi);
            /*
             * Rather than sending the raw RSSI out every time it
             * changes, we precalculate the signal level that would
             * be displayed in the status bar, and only send the
             * broadcast if that much more coarse-grained number
             * changes. This cuts down greatly on the number of
             * broadcasts, at the cost of not informing others
             * interested in RSSI of all the changes in signal
             * level.
             */
            int newSignalLevel = RssiUtil.calculateSignalLevel(mContext, newRssi);
            if (newSignalLevel != mLastSignalLevel) {
                sendRssiChangeBroadcast(newRssi);
                updateNetworkCapabilities = true;
            } else if (newRssi != oldRssi
                    && mWifiGlobals.getVerboseLoggingLevel()
                    != WifiManager.VERBOSE_LOGGING_LEVEL_DISABLED) {
                // Send the raw RSSI if verbose logging is enabled by the user so we can show
                // granular RSSI changes in Settings.
                sendRssiChangeBroadcast(newRssi);
            }
            updateLinkBandwidthAndCapabilities(stats, updateNetworkCapabilities, txBytes,
                    rxBytes);
            mLastSignalLevel = newSignalLevel;
        } else {
            mWifiInfo.setRssi(WifiInfo.INVALID_RSSI);
            updateCapabilities();
            mLastSignalLevel = -1;
        }
        mWifiConfigManager.updateScanDetailCacheFromWifiInfo(mWifiInfo);
        /*
         * Increment various performance metrics
         */
        mWifiMetrics.handlePollResult(mInterfaceName, mWifiInfo);
        updateCurrentConnectionInfo();
        return stats;
    }

    // Update the link bandwidth. Also update network capabilities if the link bandwidth changes
    // by a large amount or there is a change in signal level or frequency.
    private void updateLinkBandwidthAndCapabilities(WifiLinkLayerStats stats,
            boolean updateNetworkCapabilities, long txBytes, long rxBytes) {
        WifiScoreCard.PerNetwork network = mWifiScoreCard.lookupNetwork(mWifiInfo.getSSID());
        network.updateLinkBandwidth(mLastLinkLayerStats, stats, mWifiInfo, txBytes, rxBytes);
        int newTxKbps = network.getTxLinkBandwidthKbps();
        int newRxKbps = network.getRxLinkBandwidthKbps();
        int txDeltaKbps = Math.abs(newTxKbps - mLastTxKbps);
        int rxDeltaKbps = Math.abs(newRxKbps - mLastRxKbps);
        int bwUpdateThresholdPercent = mContext.getResources().getInteger(
                R.integer.config_wifiLinkBandwidthUpdateThresholdPercent);
        if ((txDeltaKbps * 100  >  bwUpdateThresholdPercent * mLastTxKbps)
                || (rxDeltaKbps * 100  >  bwUpdateThresholdPercent * mLastRxKbps)
                || updateNetworkCapabilities) {
            mLastTxKbps = newTxKbps;
            mLastRxKbps = newRxKbps;
            updateCapabilities();
        }

        int l2TxKbps = mWifiDataStall.getTxThroughputKbps();
        int l2RxKbps = mWifiDataStall.getRxThroughputKbps();
        if (l2RxKbps < 0 && l2TxKbps > 0) {
            l2RxKbps = l2TxKbps;
        }
        int [] reportedKbps = {mLastTxKbps, mLastRxKbps};
        int [] l2Kbps = {l2TxKbps, l2RxKbps};
        network.updateBwMetrics(reportedKbps, l2Kbps);
    }

    // Polling has completed, hence we won't have a score anymore
    private void cleanWifiScore() {
        mWifiInfo.setLostTxPacketsPerSecond(0);
        mWifiInfo.setSuccessfulTxPacketsPerSecond(0);
        mWifiInfo.setRetriedTxPacketsRate(0);
        mWifiInfo.setSuccessfulRxPacketsPerSecond(0);
        mWifiScoreReport.reset();
        mLastLinkLayerStats = null;
        updateCurrentConnectionInfo();
    }

    private void updateLinkProperties(LinkProperties newLp) {
        if (mVerboseLoggingEnabled) {
            log("Link configuration changed for netId: " + mLastNetworkId
                    + " old: " + mLinkProperties + " new: " + newLp);
        }
        // We own this instance of LinkProperties because IpClient passes us a copy.
        mLinkProperties = newLp;

        if (mNetworkAgent != null) {
            mNetworkAgent.sendLinkProperties(mLinkProperties);
        }

        if (mNetworkAgentState == DetailedState.CONNECTED) {
            // If anything has changed and we're already connected, send out a notification.
            // TODO: Update all callers to use NetworkCallbacks and delete this.
            sendLinkConfigurationChangedBroadcast();
        }
        if (mVerboseLoggingEnabled) {
            StringBuilder sb = new StringBuilder();
            sb.append("updateLinkProperties nid: " + mLastNetworkId);
            sb.append(" state: " + mNetworkAgentState);

            if (mLinkProperties != null) {
                sb.append(" ");
                sb.append(getLinkPropertiesSummary(mLinkProperties));
            }
            logd(sb.toString());
        }
    }

    /**
     * Clears all our link properties.
     */
    private void clearLinkProperties() {
        // Clear the link properties obtained from DHCP. The only caller of this
        // function has already called IpClient#stop(), which clears its state.
        synchronized (mDhcpResultsParcelableLock) {
            mDhcpResultsParcelable = new DhcpResultsParcelable();
        }

        // Now clear the merged link properties.
        mLinkProperties.clear();
        if (mNetworkAgent != null) mNetworkAgent.sendLinkProperties(mLinkProperties);
    }

    // TODO(b/193460475): Remove when tooling supports SystemApi to public API.
    @SuppressLint("NewApi")
    private void sendRssiChangeBroadcast(final int newRssi) {
        mBatteryStatsManager.reportWifiRssiChanged(newRssi);
        WifiStatsLog.write(WifiStatsLog.WIFI_SIGNAL_STRENGTH_CHANGED,
                RssiUtil.calculateSignalLevel(mContext, newRssi));

        Intent intent = new Intent(WifiManager.RSSI_CHANGED_ACTION);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        intent.putExtra(WifiManager.EXTRA_NEW_RSSI, newRssi);
        final Bundle opts;
        if (SdkLevel.isAtLeastU()) {
            opts = BroadcastOptions.makeBasic()
                    .setDeliveryGroupPolicy(BroadcastOptions.DELIVERY_GROUP_POLICY_MOST_RECENT)
                    .setDeferralPolicy(BroadcastOptions.DEFERRAL_POLICY_UNTIL_ACTIVE)
                    .toBundle();
        } else {
            opts = null;
        }
        mBroadcastQueue.queueOrSendBroadcast(
                mClientModeManager,
                () -> mContext.sendBroadcastAsUser(intent, UserHandle.ALL,
                        android.Manifest.permission.ACCESS_WIFI_STATE, opts));
    }

    private void sendLinkConfigurationChangedBroadcast() {
        Intent intent = new Intent(WifiManager.ACTION_LINK_CONFIGURATION_CHANGED);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        String summary = "broadcast=ACTION_LINK_CONFIGURATION_CHANGED";
        if (mVerboseLoggingEnabled) Log.d(getTag(), "Queuing " + summary);
        mBroadcastQueue.queueOrSendBroadcast(
                mClientModeManager,
                () -> {
                    if (mVerboseLoggingEnabled) Log.d(getTag(), "Sending " + summary);
                    mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
                });
    }

    /**
     * Helper method used to send state about supplicant - This is NOT information about the current
     * wifi connection state.
     *
     * TODO: b/79504296 This broadcast has been deprecated and should be removed
     */
    private void sendSupplicantConnectionChangedBroadcast(boolean connected) {
        Intent intent = new Intent(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        intent.putExtra(WifiManager.EXTRA_SUPPLICANT_CONNECTED, connected);
        String summary = "broadcast=SUPPLICANT_CONNECTION_CHANGE_ACTION"
                + " EXTRA_SUPPLICANT_CONNECTED=" + connected;
        if (mVerboseLoggingEnabled) Log.d(getTag(), "Queuing " + summary);
        mBroadcastQueue.queueOrSendBroadcast(
                mClientModeManager,
                () -> {
                    if (mVerboseLoggingEnabled) Log.d(getTag(), "Sending " + summary);
                    mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
                });
    }

    /**
     * Record the detailed state of a network.
     *
     * @param state the new {@code DetailedState}
     */
    private void sendNetworkChangeBroadcast(NetworkInfo.DetailedState state) {
        boolean hidden = false;

        if (mIsAutoRoaming) {
            // There is generally a confusion in the system about colluding
            // WiFi Layer 2 state (as reported by supplicant) and the Network state
            // which leads to multiple confusion.
            //
            // If link is roaming, we already have an IP address
            // as well we were connected and are doing L2 cycles of
            // reconnecting or renewing IP address to check that we still have it
            // This L2 link flapping should not be reflected into the Network state
            // which is the state of the WiFi Network visible to Layer 3 and applications
            // Note that once roaming is completed, we will
            // set the Network state to where it should be, or leave it as unchanged
            //
            hidden = true;
        }
        if (mVerboseLoggingEnabled) {
            log("sendNetworkChangeBroadcast"
                    + " oldState=" + mNetworkAgentState
                    + " newState=" + state
                    + " hidden=" + hidden);
        }
        if (hidden || state == mNetworkAgentState) return;
        mNetworkAgentState = state;
        sendNetworkChangeBroadcastWithCurrentState();
    }

    private int getNetworkStateListenerCmmRole() {
        if (isPrimary() || isScanOnly()) {
            // Wifi disconnect could be received in ScanOnlyMode when wifi scanning is enabled since
            // the mode switch happens before the disconnect actually happens. Therefore, treat
            // the scan only mode the same as primary since only the primary is allowed to change
            // into scan only mode.
            return WifiManager.WifiNetworkStateChangedListener.WIFI_ROLE_CLIENT_PRIMARY;
        }
        if (isSecondaryInternet()) {
            return WifiManager.WifiNetworkStateChangedListener.WIFI_ROLE_CLIENT_SECONDARY_INTERNET;
        }
        if (isLocalOnly()) {
            return WifiManager.WifiNetworkStateChangedListener
                    .WIFI_ROLE_CLIENT_SECONDARY_LOCAL_ONLY;
        }
        return -1;
    }

    private int convertDetailedStateToNetworkStateChangedCode(DetailedState detailedState) {
        switch (detailedState) {
            case IDLE:
                return WifiManager.WifiNetworkStateChangedListener.WIFI_NETWORK_STATUS_IDLE;
            case SCANNING:
                return WifiManager.WifiNetworkStateChangedListener.WIFI_NETWORK_STATUS_SCANNING;
            case CONNECTING:
                return WifiManager.WifiNetworkStateChangedListener.WIFI_NETWORK_STATUS_CONNECTING;
            case AUTHENTICATING:
                return WifiManager.WifiNetworkStateChangedListener
                        .WIFI_NETWORK_STATUS_AUTHENTICATING;
            case OBTAINING_IPADDR:
                return WifiManager.WifiNetworkStateChangedListener
                        .WIFI_NETWORK_STATUS_OBTAINING_IPADDR;
            case CONNECTED:
                return WifiManager.WifiNetworkStateChangedListener.WIFI_NETWORK_STATUS_CONNECTED;
            case DISCONNECTED:
                return WifiManager.WifiNetworkStateChangedListener.WIFI_NETWORK_STATUS_DISCONNECTED;
            case FAILED:
                return WifiManager.WifiNetworkStateChangedListener.WIFI_NETWORK_STATUS_FAILED;
            default:
                // There are some DetailedState codes that are not being used in wifi, so ignore
                // them.
                return -1;
        }
    }

    private void sendNetworkChangeBroadcastWithCurrentState() {
        // copy into local variables to force lambda to capture by value and not reference, since
        // mNetworkAgentState is mutable and can change
        final DetailedState networkAgentState = mNetworkAgentState;
        if (mVerboseLoggingEnabled) {
            Log.d(getTag(), "Queueing broadcast=NETWORK_STATE_CHANGED_ACTION"
                    + " networkAgentState=" + networkAgentState);
        }
        int networkStateChangedCmmRole = getNetworkStateListenerCmmRole();
        int networkStateChangedState = convertDetailedStateToNetworkStateChangedCode(
                networkAgentState);
        if (networkStateChangedCmmRole != -1 && networkStateChangedState != -1) {
            mWifiInjector.getActiveModeWarden().onNetworkStateChanged(
                    networkStateChangedCmmRole, networkStateChangedState);
        }
        mBroadcastQueue.queueOrSendBroadcast(
                mClientModeManager,
                () -> sendNetworkChangeBroadcast(
                        mContext, networkAgentState, mVerboseLoggingEnabled));
    }

    /** Send a NETWORK_STATE_CHANGED_ACTION broadcast. */
    public static void sendNetworkChangeBroadcast(
            Context context, DetailedState networkAgentState, boolean verboseLoggingEnabled) {
        Intent intent = new Intent(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
        NetworkInfo networkInfo = makeNetworkInfo(networkAgentState);
        intent.putExtra(WifiManager.EXTRA_NETWORK_INFO, networkInfo);
        if (verboseLoggingEnabled) {
            Log.d(TAG, "Sending broadcast=NETWORK_STATE_CHANGED_ACTION"
                    + " networkAgentState=" + networkAgentState);
        }
        //TODO(b/69974497) This should be non-sticky, but settings needs fixing first.
        if (SdkLevel.isAtLeastU()) {
            final Context userAllContext = context.createContextAsUser(
                    UserHandle.ALL, 0 /* flags */);
            final Bundle opts = BroadcastOptions.makeBasic()
                    .setDeliveryGroupPolicy(BroadcastOptions.DELIVERY_GROUP_POLICY_MOST_RECENT)
                    .setDeferralPolicy(BroadcastOptions.DEFERRAL_POLICY_UNTIL_ACTIVE)
                    .toBundle();
            userAllContext.sendStickyBroadcast(intent, opts);
        } else {
            context.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
        }
    }

    private static NetworkInfo makeNetworkInfo(DetailedState networkAgentState) {
        final NetworkInfo ni = new NetworkInfo(ConnectivityManager.TYPE_WIFI, 0, NETWORKTYPE, "");
        ni.setDetailedState(networkAgentState, null, null);
        return ni;
    }

    private List<ScanResult.InformationElement> findMatchingInfoElements(@Nullable String bssid) {
        if (bssid == null) return null;
        ScanResult matchingScanResult = mScanRequestProxy.getScanResult(bssid);
        if (matchingScanResult == null || matchingScanResult.informationElements == null) {
            return null;
        }
        return Arrays.asList(matchingScanResult.informationElements);
    }

    private void setMultiLinkInfoFromScanCache(@Nullable String bssid) {
        if (bssid == null) return;
        ScanDetailCache scanDetailCache = mWifiConfigManager.getScanDetailCacheForNetwork(
                mWifiInfo.getNetworkId());
        ScanResult matchingScanResult = null;
        if (scanDetailCache != null) {
            matchingScanResult = scanDetailCache.getScanResult(bssid);
        }
        /**
         * If FW roams to a new AP, the BSSID may not be in the scan detailed cache. Set Multi-Link
         * info only if the BSSID is present in the scan detailed cache and AP is a Multi-Link
         * Device. If not, clear Multi-Link info from WifiInfo. After association
         * {@link ClientModeImpl#updateMloLinkAddrAndStates(ConnectionMloLinksInfo)} will update
         * the Multi-Link info from supplicant if present.
         */
        if (matchingScanResult != null && matchingScanResult.getApMldMacAddress() != null) {
            mWifiInfo.setApMldMacAddress(matchingScanResult.getApMldMacAddress());
            mWifiInfo.setApMloLinkId(matchingScanResult.getApMloLinkId());
            // Deep copy the affiliated links to avoid any overwrite in future from WifiInfo.
            List<MloLink> deepCopyAffiliatedMloLinks = new ArrayList<>();
            for (MloLink link : matchingScanResult.getAffiliatedMloLinks()) {
                deepCopyAffiliatedMloLinks.add(new MloLink(link, NetworkCapabilities.REDACT_NONE));
            }
            mWifiInfo.setAffiliatedMloLinks(deepCopyAffiliatedMloLinks);
        } else {
            mWifiInfo.setApMldMacAddress(null);
            mWifiInfo.setApMloLinkId(MloLink.INVALID_MLO_LINK_ID);
            mWifiInfo.setAffiliatedMloLinks(Collections.emptyList());
        }
        updateCurrentConnectionInfo();
    }

    /**
     * Multi-link info should not be set from scan cache after 'SupplicantState.ASSOCIATED' as it
     * overwrites link states collected during ASSOCIATION. Return if multi-link info is settable
     * on the current state.
     */
    private boolean isMultiLinkInfoSettableFromScanCache(SupplicantState currentState) {
        return currentState != SupplicantState.FOUR_WAY_HANDSHAKE
                && currentState != SupplicantState.GROUP_HANDSHAKE
                && currentState != SupplicantState.COMPLETED;
    }

    // Return true if link frequency is changed.
    private boolean updateAssociatedMloLinksFromLinksInfoWhenBssFreqChanged(int bssFreq) {
        boolean isLinkFrequencyChanged = false;
        // retrieve mlo links info with updated frequencies from HAL
        WifiNative.ConnectionMloLinksInfo mloLinksInfo = mWifiNative.getConnectionMloLinksInfo(
                mInterfaceName);
        if (mloLinksInfo == null) {
            return false;
        }
        for (int i = 0; i < mloLinksInfo.links.length; i++) {
            MloLink link = mWifiInfo.getAffiliatedMloLink(mloLinksInfo.links[i].getLinkId());
            if (link != null && mloLinksInfo.links[i].getFrequencyMHz() == bssFreq) {
                int linkCurFreq = ScanResult.convertChannelToFrequencyMhzIfSupported(
                        link.getChannel(), link.getBand());
                if (linkCurFreq != ScanResult.UNSPECIFIED && bssFreq != linkCurFreq) {
                    if (link.getRssi() == mWifiInfo.getRssi()
                            && ScanResult.convertChannelToFrequencyMhzIfSupported(link.getChannel(),
                            link.getBand()) == mWifiInfo.getFrequency()) {
                        mWifiInfo.setFrequency(bssFreq);
                    }
                    isLinkFrequencyChanged = true;
                    link.setChannel(ScanResult.convertFrequencyMhzToChannelIfSupported(bssFreq));
                    link.setBand(ScanResult.toBand(bssFreq));
                    break;
                }
            }
        }
        return isLinkFrequencyChanged;
    }

    private SupplicantState handleSupplicantStateChange(StateChangeResult stateChangeResult) {
        SupplicantState state = stateChangeResult.state;
        mWifiScoreCard.noteSupplicantStateChanging(mWifiInfo, state);
        // Supplicant state change
        // [31-13] Reserved for future use
        // [8 - 0] Supplicant state (as defined in SupplicantState.java)
        // 50023 supplicant_state_changed (custom|1|5)
        mWifiInfo.setSupplicantState(state);
        // Network id and SSID are only valid when we start connecting
        if (SupplicantState.isConnecting(state)) {
            mWifiInfo.setNetworkId(stateChangeResult.networkId);
            mWifiInfo.setBSSID(stateChangeResult.bssid);
            mWifiInfo.setSSID(stateChangeResult.wifiSsid);
            if (stateChangeResult.frequencyMhz > 0) {
                mWifiInfo.setFrequency(stateChangeResult.frequencyMhz);
            }
            // Multi-link info is set from scan cache (multi-link state: unassociated). On
            // association, connection capabilities and multi-link state are queried from
            // supplicant and link info is updated. (multi-link state: active/idle). After
            // association, don't set the multi-link info from scan cache.
            if (isMultiLinkInfoSettableFromScanCache(state)) {
                setMultiLinkInfoFromScanCache(stateChangeResult.bssid);
            }
            if (state == SupplicantState.ASSOCIATED) {
                updateWifiInfoLinkParamsAfterAssociation();
            }
            mWifiInfo.setInformationElements(findMatchingInfoElements(stateChangeResult.bssid));
        } else {
            // Reset parameters according to WifiInfo.reset()
            mWifiBlocklistMonitor.removeAffiliatedBssids(mWifiInfo.getBSSID());
            mWifiInfo.setNetworkId(WifiConfiguration.INVALID_NETWORK_ID);
            mWifiInfo.setBSSID(null);
            mWifiInfo.setSSID(null);
            mWifiInfo.setWifiStandard(ScanResult.WIFI_STANDARD_UNKNOWN);
            mWifiInfo.setInformationElements(null);
            mWifiInfo.clearCurrentSecurityType();
            mWifiInfo.resetMultiLinkInfo();
        }

        // SSID might have been updated, so call updateCapabilities
        updateCapabilities();

        WifiConfiguration config = getConnectedWifiConfigurationInternal();
        if (config == null) {
            // If not connected, this should be non-null.
            config = getConnectingWifiConfigurationInternal();
        }
        if (config != null && config.networkId == mWifiInfo.getNetworkId()) {
            updateWifiInfoWhenConnected(config);

            // Set meteredHint if scan result says network is expensive
            ScanDetailCache scanDetailCache = mWifiConfigManager.getScanDetailCacheForNetwork(
                    config.networkId);
            if (scanDetailCache != null) {
                ScanDetail scanDetail = scanDetailCache.getScanDetail(stateChangeResult.bssid);
                if (scanDetail != null) {
                    mWifiInfo.setFrequency(scanDetail.getScanResult().frequency);
                    NetworkDetail networkDetail = scanDetail.getNetworkDetail();
                    if (networkDetail != null
                            && networkDetail.getAnt() == NetworkDetail.Ant.ChargeablePublic) {
                        mWifiInfo.setMeteredHint(true);
                    }
                }
            }
        }
        mWifiScoreCard.noteSupplicantStateChanged(mWifiInfo);
        updateCurrentConnectionInfo();
        return state;
    }

    private void handleNetworkConnectionEventInfo(
            WifiConfiguration config, NetworkConnectionEventInfo connectionInfo) {
        if (connectionInfo == null) return;

        mWifiInfo.setBSSID(connectionInfo.bssid);
        mWifiInfo.setNetworkId(connectionInfo.networkId);

        if (config != null && connectionInfo.keyMgmtMask != null) {
            // Besides allowed key management, pmf and wep keys are necessary to
            // identify WPA3 Enterprise and WEP, so the original configuration
            // is still necessary.
            WifiConfiguration tmp = new WifiConfiguration(config);
            SecurityParams securityParams =
                    config.getNetworkSelectionStatus().getLastUsedSecurityParams();
            tmp.requirePmf = securityParams.isRequirePmf();
            tmp.allowedProtocols = securityParams.getAllowedProtocols();
            tmp.allowedPairwiseCiphers = securityParams.getAllowedPairwiseCiphers();
            tmp.allowedGroupCiphers = securityParams.getAllowedGroupCiphers();
            tmp.setSecurityParams(connectionInfo.keyMgmtMask);
            mWifiInfo.setCurrentSecurityType(tmp.getDefaultSecurityParams().getSecurityType());
            // Update the last used security params.
            config.getNetworkSelectionStatus().setLastUsedSecurityParams(
                    tmp.getDefaultSecurityParams());
            mWifiConfigManager.setNetworkLastUsedSecurityParams(config.networkId,
                    tmp.getDefaultSecurityParams());
            Log.i(getTag(), "Update current security type to " + mWifiInfo.getCurrentSecurityType()
                    + " from network connection event.");
        }
    }

    private void updateWifiInfoWhenConnected(@NonNull WifiConfiguration config) {
        mWifiInfo.setEphemeral(config.ephemeral);
        mWifiInfo.setTrusted(config.trusted);
        mWifiInfo.setOemPaid(config.oemPaid);
        mWifiInfo.setOemPrivate(config.oemPrivate);
        mWifiInfo.setCarrierMerged(config.carrierMerged);
        mWifiInfo.setSubscriptionId(config.subscriptionId);
        mWifiInfo.setOsuAp(config.osu);
        mWifiInfo.setRestricted(config.restricted);
        if (config.fromWifiNetworkSpecifier || config.fromWifiNetworkSuggestion) {
            mWifiInfo.setRequestingPackageName(config.creatorName);
        }
        mWifiInfo.setIsPrimary(isPrimary());
        SecurityParams securityParams = config.getNetworkSelectionStatus()
                .getLastUsedSecurityParams();
        if (securityParams != null) {
            Log.i(getTag(), "Update current security type to " + securityParams.getSecurityType());
            mWifiInfo.setCurrentSecurityType(securityParams.getSecurityType());
        } else {
            mWifiInfo.clearCurrentSecurityType();
            Log.e(TAG, "Network connection candidate with no security parameters");
        }
        updateCurrentConnectionInfo();
    }

    /**
     * Update mapping of affiliated BSSID in blocklist. Called when there is a change in MLO links.
     */
    private void updateBlockListAffiliatedBssids() {
        /**
         * getAffiliatedMloLinks() returns a list of MloLink objects for all the links
         * advertised by AP-MLD including the associated link. Update mWifiBlocklistMonitor
         * with all affiliated AP link MAC addresses, excluding the associated link, indexed
         * with associated AP link MAC address (BSSID).
         * For e.g.
         *  link1_bssid -> affiliated {link2_bssid, link3_bssid}
         * Above mapping is used to block list all affiliated links when associated link is
         * block listed.
         */
        List<String> affiliatedBssids = new ArrayList<>();
        for (MloLink link : mWifiInfo.getAffiliatedMloLinks()) {
            if (link.getApMacAddress() != null && !Objects.equals(mWifiInfo.getBSSID(),
                    link.getApMacAddress().toString())) {
                affiliatedBssids.add(link.getApMacAddress().toString());
            }
        }
        mWifiBlocklistMonitor.setAffiliatedBssids(mWifiInfo.getBSSID(), affiliatedBssids);
    }

    /**
     * Clear MLO link states to UNASSOCIATED.
     */
    private void clearMloLinkStates() {
        for (MloLink link : mWifiInfo.getAffiliatedMloLinks()) {
            link.setState(MloLink.MLO_LINK_STATE_UNASSOCIATED);
        }
    }

    /**
     * Update the MLO link states to ACTIVE or IDLE depending on any traffic stream is mapped to the
     * link.
     *
     * @param info MLO link object from HAL.
     */
    private void updateMloLinkStates(@Nullable WifiNative.ConnectionMloLinksInfo info) {
        if (info == null) return;
        for (int i = 0; i < info.links.length; i++) {
            mWifiInfo.updateMloLinkState(info.links[i].getLinkId(),
                    info.links[i].isAnyTidMapped() ? MloLink.MLO_LINK_STATE_ACTIVE
                            : MloLink.MLO_LINK_STATE_IDLE);
        }
    }
    /**
     * Update the MLO link states to ACTIVE or IDLE depending on any traffic stream is mapped to the
     * link. Also, update the link MAC address.
     *
     * @param info MLO link object from HAL.
     */
    private void updateMloLinkAddrAndStates(@Nullable WifiNative.ConnectionMloLinksInfo info) {
        if (info == null) return;
        // At this stage, the expectation is that we already have MLO link information collected
        // from scan detailed cache. If the MLO link information is empty, probably FW/driver roamed
        // to a new AP which is not in scan detailed cache. Update MLO links with the details
        // provided by the supplicant in ConnectionMloLinksInfo. The supplicant provides only the
        // associated links.
        if (mWifiInfo.getAffiliatedMloLinks().isEmpty()) {
            mWifiInfo.setApMldMacAddress(info.apMldMacAddress);
            mWifiInfo.setApMloLinkId(info.apMloLinkId);
            List<MloLink> affiliatedMloLinks = new ArrayList<>();
            for (int i = 0; i < info.links.length; i++) {
                MloLink link = new MloLink();
                link.setLinkId(info.links[i].getLinkId());
                link.setStaMacAddress(info.links[i].getStaMacAddress());
                link.setApMacAddress(info.links[i].getApMacAddress());
                link.setChannel(ScanResult.convertFrequencyMhzToChannelIfSupported(
                        info.links[i].getFrequencyMHz()));
                link.setBand(ScanResult.toBand(info.links[i].getFrequencyMHz()));
                link.setState(info.links[i].isAnyTidMapped() ? MloLink.MLO_LINK_STATE_ACTIVE
                        : MloLink.MLO_LINK_STATE_IDLE);
                affiliatedMloLinks.add(link);
            }
            mWifiInfo.setAffiliatedMloLinks(affiliatedMloLinks);
        } else {
            for (int i = 0; i < info.links.length; i++) {
                mWifiInfo.updateMloLinkStaAddress(info.links[i].getLinkId(),
                        info.links[i].getStaMacAddress());
                mWifiInfo.updateMloLinkState(info.links[i].getLinkId(),
                        info.links[i].isAnyTidMapped() ? MloLink.MLO_LINK_STATE_ACTIVE
                                : MloLink.MLO_LINK_STATE_IDLE);
            }
        }
    }

    private void updateWifiInfoLinkParamsAfterAssociation() {
        mLastConnectionCapabilities = mWifiNative.getConnectionCapabilities(mInterfaceName);
        int maxTxLinkSpeedMbps = mThroughputPredictor.predictMaxTxThroughput(
                mLastConnectionCapabilities);
        int maxRxLinkSpeedMbps = mThroughputPredictor.predictMaxRxThroughput(
                mLastConnectionCapabilities);
        mWifiInfo.setWifiStandard(mLastConnectionCapabilities.wifiStandard);
        mWifiInfo.setMaxSupportedTxLinkSpeedMbps(maxTxLinkSpeedMbps);
        mWifiInfo.setMaxSupportedRxLinkSpeedMbps(maxRxLinkSpeedMbps);
        mWifiInfo.enableApTidToLinkMappingNegotiationSupport(
                mLastConnectionCapabilities.apTidToLinkMapNegotiationSupported);
        mWifiMetrics.setConnectionMaxSupportedLinkSpeedMbps(mInterfaceName,
                maxTxLinkSpeedMbps, maxRxLinkSpeedMbps);
        if (mLastConnectionCapabilities.wifiStandard == ScanResult.WIFI_STANDARD_11BE) {
            updateMloLinkAddrAndStates(mWifiNative.getConnectionMloLinksInfo(mInterfaceName));
            updateBlockListAffiliatedBssids();
        }
        if (SdkLevel.isAtLeastV() && mLastConnectionCapabilities.vendorData != null) {
            mWifiInfo.setVendorData(mLastConnectionCapabilities.vendorData);
        }
        if (mVerboseLoggingEnabled) {
            StringBuilder sb = new StringBuilder();
            logd(sb.append("WifiStandard: ").append(ScanResult.wifiStandardToString(
                            mLastConnectionCapabilities.wifiStandard))
                    .append(" maxTxSpeed: ").append(maxTxLinkSpeedMbps)
                    .append(" maxRxSpeed: ").append(maxRxLinkSpeedMbps)
                    .toString());
        }
        updateCurrentConnectionInfo();
    }

    /**
     * Tells IpClient what BSSID, L2Key and GroupHint to use for IpMemoryStore.
     */
    private void updateLayer2Information() {
        if (mIpClient != null) {
            Pair<String, String> p = mWifiScoreCard.getL2KeyAndGroupHint(mWifiInfo);
            if (!p.equals(mLastL2KeyAndGroupHint)) {
                final MacAddress currentBssid =
                        NativeUtil.getMacAddressOrNull(mWifiInfo.getBSSID());
                final Layer2Information l2Information = new Layer2Information(
                        p.first, p.second, currentBssid);
                // Update current BSSID on IpClient side whenever l2Key and groupHint
                // pair changes (i.e. the initial connection establishment or L2 roaming
                // happened). If we have COMPLETED the roaming to a different BSSID, start
                // doing DNAv4/DNAv6 -style probing for on-link neighbors of interest (e.g.
                // routers/DNS servers/default gateway).
                if (mIpClient.updateLayer2Information(l2Information)) {
                    mLastL2KeyAndGroupHint = p;
                } else {
                    mLastL2KeyAndGroupHint = null;
                }
            }
        }
    }
    private @Nullable Pair<String, String> mLastL2KeyAndGroupHint = null;

    /**
     * Resets the Wi-Fi Connections by clearing any state, resetting any sockets
     * using the interface, stopping DHCP & disabling interface
     *
     * @param disconnectReason must be one of WifiDisconnectReported.FailureReason values
     *                         defined in /frameworks/proto_logging/stats/atoms.proto
     */
    private void handleNetworkDisconnect(boolean newConnectionInProgress, int disconnectReason) {
        if (newConnectionInProgress) {
            mFrameworkDisconnectReasonOverride = mIsUserSelected
                    ? WifiStatsLog.WIFI_DISCONNECT_REPORTED__FAILURE_CODE__DISCONNECT_NEW_CONNECTION_USER
                    : WifiStatsLog.WIFI_DISCONNECT_REPORTED__FAILURE_CODE__DISCONNECT_NEW_CONNECTION_OTHERS;
        }
        int wifiStatsLogDisconnectReason = mFrameworkDisconnectReasonOverride != 0
                ? mFrameworkDisconnectReasonOverride : disconnectReason;
        mFrameworkDisconnectReasonOverride = 0;
        mWifiMetrics.reportNetworkDisconnect(mInterfaceName, wifiStatsLogDisconnectReason,
                mWifiInfo.getRssi(),
                mWifiInfo.getLinkSpeed(),
                mWifiInfo.getLastRssiUpdateMillis());

        if (mVerboseLoggingEnabled) {
            Log.v(getTag(), "handleNetworkDisconnect: newConnectionInProgress: "
                    + newConnectionInProgress, new Throwable());
        }

        if (isPrimary()) {
            WifiConfiguration wifiConfig = getConnectedWifiConfigurationInternal();
            if (wifiConfig != null) {
                ScanResultMatchInfo matchInfo =
                        ScanResultMatchInfo.fromWifiConfiguration(wifiConfig);
                // WakeupController should only care about the primary, internet providing network
                mWakeupController.setLastDisconnectInfo(matchInfo);
            }
            mRssiMonitor.reset();
        }

        clearTargetBssid("handleNetworkDisconnect");

        // Don't stop DHCP if Fils connection is in progress.
        if (newConnectionInProgress && mIpClientWithPreConnection) {
            if (mVerboseLoggingEnabled) {
                log("handleNetworkDisconnect: Don't stop IpClient as fils connection in progress: "
                        + " mLastNetworkId: " + mLastNetworkId
                        + " mTargetNetworkId" + mTargetNetworkId);
            }
        } else {
            stopDhcpSetup();
        }

        // DISASSOC_AP_BUSY could be received in both after L3 connection is successful or right
        // after BSSID association if the AP can't accept more stations.
        if (disconnectReason == StaIfaceReasonCode.DISASSOC_AP_BUSY) {
            mWifiConfigManager.setRecentFailureAssociationStatus(
                    mWifiInfo.getNetworkId(),
                    WifiConfiguration.RECENT_FAILURE_DISCONNECTION_AP_BUSY);
            // Block the current BSS temporarily since it cannot handle more stations
            WifiConfiguration config = getConnectedWifiConfigurationInternal();
            if (config == null) {
                config = getConnectingWifiConfigurationInternal();
            }
            mWifiBlocklistMonitor.blockBssidForDurationMs(mLastBssid, config,
                    DISASSOC_AP_BUSY_DISABLE_DURATION_MS,
                    WifiBlocklistMonitor.REASON_AP_UNABLE_TO_HANDLE_NEW_STA, mWifiInfo.getRssi());
        }

        mWifiScoreReport.stopConnectedNetworkScorer();
        /* Reset data structures */
        mWifiScoreReport.reset();
        mWifiBlocklistMonitor.removeAffiliatedBssids(mWifiInfo.getBSSID());
        mWifiInfo.reset();
        /* Reset roaming parameters */
        mIsAutoRoaming = false;

        sendNetworkChangeBroadcast(DetailedState.DISCONNECTED);
        if (mNetworkAgent != null) {
            mNetworkAgent.unregister();
            mNetworkAgent = null;
            mQosPolicyRequestHandler.setNetworkAgent(null);
        }

        /* Clear network properties */
        clearLinkProperties();

        mLastBssid = null;
        mIpProvisioningTimedOut = false;
        mLastLinkLayerStats = null;
        registerDisconnected();
        mLastNetworkId = WifiConfiguration.INVALID_NETWORK_ID;
        mLastSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        mCurrentConnectionDetectedCaptivePortal = false;
        mLastSimBasedConnectionCarrierName = null;
        mNudFailureCounter = new Pair<>(0L, 0);
        checkAbnormalDisconnectionAndTakeBugReport();
        mWifiScoreCard.resetConnectionState(mInterfaceName);
        updateLayer2Information();
        // If there is new connection in progress, this is for the new connection, so keept it.
        if (!newConnectionInProgress) {
            mIsUserSelected = false;
        }
        updateCurrentConnectionInfo();
    }

    void handlePreDhcpSetup() {
        if (!mWifiGlobals.isBluetoothConnected()) {
            /*
             * There are problems setting the Wi-Fi driver's power
             * mode to active when bluetooth coexistence mode is
             * enabled or sense.
             * <p>
             * We set Wi-Fi to active mode when
             * obtaining an IP address because we've found
             * compatibility issues with some routers with low power
             * mode.
             * <p>
             * In order for this active power mode to properly be set,
             * we disable coexistence mode until we're done with
             * obtaining an IP address.  One exception is if we
             * are currently connected to a headset, since disabling
             * coexistence would interrupt that connection.
             */
            // Disable the coexistence mode
            mWifiNative.setBluetoothCoexistenceMode(
                    mInterfaceName, WifiNative.BLUETOOTH_COEXISTENCE_MODE_DISABLED);
        }

        // Disable power save and suspend optimizations during DHCP
        // Note: The order here is important for now. Brcm driver changes
        // power settings when we control suspend mode optimizations.
        // TODO: Remove this comment when the driver is fixed.
        setSuspendOptimizationsNative(SUSPEND_DUE_TO_DHCP, false);
        setPowerSave(POWER_SAVE_CLIENT_DHCP, false);

        // Update link layer stats
        getWifiLinkLayerStats();

        if (mWifiP2pConnection.isConnected() && !mWifiP2pConnection.isP2pInWaitingState()) {
            // P2P discovery breaks DHCP, so shut it down in order to get through this.
            // Once P2P service receives this message and processes it accordingly, it is supposed
            // to send arg2 (i.e. CMD_PRE_DHCP_ACTION_COMPLETE) in a new Message.what back to
            // ClientModeImpl so that we can continue.
            // TODO(b/159060934): Need to ensure that CMD_PRE_DHCP_ACTION_COMPLETE is sent back to
            //  the ClientModeImpl instance that originally sent it. Right now it is sent back to
            //  all ClientModeImpl instances by WifiP2pConnection.
            mWifiP2pConnection.sendMessage(
                    WifiP2pServiceImpl.BLOCK_DISCOVERY,
                    WifiP2pServiceImpl.ENABLED,
                    CMD_PRE_DHCP_ACTION_COMPLETE);
        } else {
            // If the p2p service is not running, we can proceed directly.
            sendMessage(CMD_PRE_DHCP_ACTION_COMPLETE);
        }
    }

    void addLayer2PacketsToHlpReq(List<Layer2PacketParcelable> packets) {
        List<Layer2PacketParcelable> mLayer2Packet = packets;
        if ((mLayer2Packet != null) && (mLayer2Packet.size() > 0)) {
            mWifiNative.flushAllHlp(mInterfaceName);

            for (int j = 0; j < mLayer2Packet.size(); j++) {
                byte [] bytes = mLayer2Packet.get(j).payload;
                byte [] payloadBytes = Arrays.copyOfRange(bytes, 12, bytes.length);
                MacAddress dstAddress = mLayer2Packet.get(j).dstMacAddress;

                mWifiNative.addHlpReq(mInterfaceName, dstAddress, payloadBytes);
            }
        }
    }

    void handlePostDhcpSetup() {
        /* Restore power save and suspend optimizations */
        setSuspendOptimizationsNative(SUSPEND_DUE_TO_DHCP, true);
        setPowerSave(POWER_SAVE_CLIENT_DHCP, true);

        if (mWifiP2pConnection.isConnected()) {
            mWifiP2pConnection.sendMessage(
                    WifiP2pServiceImpl.BLOCK_DISCOVERY, WifiP2pServiceImpl.DISABLED);
        }

        // Set the coexistence mode back to its default value
        mWifiNative.setBluetoothCoexistenceMode(
                mInterfaceName, WifiNative.BLUETOOTH_COEXISTENCE_MODE_SENSE);
    }

    /**
     * Set power save mode
     *
     * @param ps true to enable power save (default behavior)
     *           false to disable power save.
     * @return true for success, false for failure
     */
    public boolean setPowerSave(@PowerSaveClientType int client, boolean ps) {
        if (mInterfaceName != null) {
            if (mVerboseLoggingEnabled) {
                Log.d(getTag(), "Request to set power save for: " + mInterfaceName + " to: " + ps
                        + " requested by: " + client + ", mPowerSaveDisableRequests = "
                        + mPowerSaveDisableRequests);
            }
            if (ps) {
                mPowerSaveDisableRequests &= ~client;
            } else {
                mPowerSaveDisableRequests |= client;
            }
            boolean actualPs = mPowerSaveDisableRequests == 0;
            if (mVerboseLoggingEnabled) {
                Log.d(getTag(), "Setting power save to: " + actualPs
                        + ", mPowerSaveDisableRequests = " + mPowerSaveDisableRequests);
            }

            mWifiNative.setPowerSave(mInterfaceName, actualPs);
        } else {
            Log.e(getTag(), "Failed to setPowerSave, interfaceName is null");
            return false;
        }
        return true;
    }

    /**
     * Enable power save.
     *
     * @return true for success, false for failure.
     */
    public boolean enablePowerSave() {
        return setPowerSave(~0, true);
    }

    /**
     * Set low latency mode
     *
     * @param enabled true to enable low latency
     *                false to disable low latency (default behavior).
     * @return true for success, false for failure
     */
    public boolean setLowLatencyMode(boolean enabled) {
        if (mVerboseLoggingEnabled) {
            Log.d(getTag(), "Setting low latency mode to " + enabled);
        }
        if (!mWifiNative.setLowLatencyMode(enabled)) {
            Log.e(getTag(), "Failed to setLowLatencyMode");
            return false;
        }
        return true;
    }

    private int getClientRoleForMetrics(WifiConfiguration config) {
        ActiveModeManager.ClientRole clientRole = mClientModeManager.getRole();
        if (clientRole == ROLE_CLIENT_PRIMARY) {
            return config != null && config.fromWifiNetworkSpecifier
                    ? WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__ROLE__ROLE_CLIENT_LOCAL_ONLY
                    : WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__ROLE__ROLE_CLIENT_PRIMARY;
        } else if (clientRole == ROLE_CLIENT_LOCAL_ONLY) {
            return WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__ROLE__ROLE_CLIENT_LOCAL_ONLY;
        } else if (clientRole == ROLE_CLIENT_SECONDARY_LONG_LIVED) {
            return mClientModeManager.isSecondaryInternet()
                    ? WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__ROLE__ROLE_CLIENT_SECONDARY_INTERNET
                    : WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__ROLE__ROLE_CLIENT_SECONDARY_LONG_LIVED;
        } else if (clientRole == ROLE_CLIENT_SECONDARY_TRANSIENT) {
            return WifiStatsLog
                    .WIFI_CONNECTION_RESULT_REPORTED__ROLE__ROLE_CLIENT_SECONDARY_TRANSIENT;
        }
        return WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__ROLE__ROLE_CLIENT_OTHERS;
    }

    /**
     * Inform other components that a new connection attempt is starting.
     */
    private void reportConnectionAttemptStart(
            WifiConfiguration config, String targetBSSID, int roamType, int uid) {
        boolean isOobPseudonymEnabled = false;
        if (config.enterpriseConfig != null && config.enterpriseConfig.isAuthenticationSimBased()
                && mWifiCarrierInfoManager.isOobPseudonymFeatureEnabled(config.carrierId)) {
            isOobPseudonymEnabled = true;
        }
        int overlapWithLastConnectionMs =
                mWifiMetrics.startConnectionEvent(
                        mInterfaceName, config, targetBSSID, roamType, isOobPseudonymEnabled,
                        getClientRoleForMetrics(config), uid);
        if (mDeviceConfigFacade.isOverlappingConnectionBugreportEnabled()
                && overlapWithLastConnectionMs
                > mDeviceConfigFacade.getOverlappingConnectionDurationThresholdMs()) {
            String bugTitle = "Wi-Fi BugReport: overlapping connection";
            String bugDetail = "Detect abnormal overlapping connection";
            mWifiDiagnostics.takeBugReport(bugTitle, bugDetail);
        }
        mWifiDiagnostics.reportConnectionEvent(WifiDiagnostics.CONNECTION_EVENT_STARTED,
                mClientModeManager);
        if (isPrimary()) {
            mWrongPasswordNotifier.onNewConnectionAttempt();
        }
    }

    private void handleConnectionAttemptEndForDiagnostics(int level2FailureCode) {
        switch (level2FailureCode) {
            case WifiMetrics.ConnectionEvent.FAILURE_NONE:
                break;
            case WifiMetrics.ConnectionEvent.FAILURE_CONNECT_NETWORK_FAILED:
                // WifiDiagnostics doesn't care about pre-empted connections, or cases
                // where we failed to initiate a connection attempt with supplicant.
                break;
            case WifiMetrics.ConnectionEvent.FAILURE_NO_RESPONSE:
                mWifiDiagnostics.reportConnectionEvent(
                        WifiDiagnostics.CONNECTION_EVENT_TIMEOUT, mClientModeManager);
                break;
            default:
                mWifiDiagnostics.reportConnectionEvent(WifiDiagnostics.CONNECTION_EVENT_FAILED,
                        mClientModeManager);
        }
    }

    /**
     * Inform other components (WifiMetrics, WifiDiagnostics, WifiConnectivityManager, etc.) that
     * the current connection attempt has concluded.
     */
    private void reportConnectionAttemptEnd(int level2FailureCode, int connectivityFailureCode,
            int level2FailureReason, int statusCode) {
        // if connected, this should be non-null.
        WifiConfiguration configuration = getConnectedWifiConfigurationInternal();
        if (configuration == null) {
            // If not connected, this should be non-null.
            configuration = getConnectingWifiConfigurationInternal();
        }
        if (configuration == null) {
            Log.e(TAG, "Unexpected null WifiConfiguration. Config may have been removed.");
            return;
        }

        String bssid = mLastBssid == null ? mTargetBssid : mLastBssid;
        String ssid = mWifiInfo.getSSID();
        if (WifiManager.UNKNOWN_SSID.equals(ssid)) {
            ssid = getConnectingSsidInternal();
        }
        if (level2FailureCode != WifiMetrics.ConnectionEvent.FAILURE_NONE) {
            int blocklistReason = convertToWifiBlocklistMonitorFailureReason(
                    level2FailureCode, level2FailureReason);
            if (blocklistReason != -1) {
                mWifiScoreCard.noteConnectionFailure(mWifiInfo, mLastScanRssi, ssid,
                        blocklistReason);
                checkAbnormalConnectionFailureAndTakeBugReport(ssid);
                mWifiBlocklistMonitor.handleBssidConnectionFailure(bssid, configuration,
                        blocklistReason, mLastScanRssi);
                WifiScoreCard.NetworkConnectionStats recentStats = mWifiScoreCard.lookupNetwork(
                        ssid).getRecentStats();
                // Skip the secondary internet connection failure for association rejection
                final boolean shouldSkip = isSecondaryInternet() &&
                        (level2FailureCode
                                == WifiMetrics.ConnectionEvent.FAILURE_ASSOCIATION_TIMED_OUT
                                || level2FailureCode
                                == WifiMetrics.ConnectionEvent.FAILURE_ASSOCIATION_REJECTION);
                if (recentStats.getCount(WifiScoreCard.CNT_CONSECUTIVE_CONNECTION_FAILURE)
                        >= WifiBlocklistMonitor.NUM_CONSECUTIVE_FAILURES_PER_NETWORK_EXP_BACKOFF
                        && configuration.getNetworkSelectionStatus().isNetworkEnabled()
                        && !shouldSkip) {
                    mWifiConfigManager.updateNetworkSelectionStatus(mTargetNetworkId,
                            WifiConfiguration.NetworkSelectionStatus.DISABLED_CONSECUTIVE_FAILURES);
                }
                if (recentStats.getCount(WifiScoreCard.CNT_CONSECUTIVE_WRONG_PASSWORD_FAILURE)
                        >= THRESHOLD_TO_PERM_WRONG_PASSWORD) {
                    mWifiConfigManager.updateNetworkSelectionStatus(mTargetNetworkId,
                            WifiConfiguration.NetworkSelectionStatus.DISABLED_BY_WRONG_PASSWORD);
                }
            }
        }

        if (configuration.carrierId != TelephonyManager.UNKNOWN_CARRIER_ID) {
            if (level2FailureCode == WifiMetrics.ConnectionEvent.FAILURE_NONE) {
                mWifiMetrics.incrementNumOfCarrierWifiConnectionSuccess();
            } else if (level2FailureCode
                            == WifiMetrics.ConnectionEvent.FAILURE_AUTHENTICATION_FAILURE
                    && level2FailureReason
                            != WifiMetricsProto.ConnectionEvent.AUTH_FAILURE_NONE) {
                mWifiMetrics.incrementNumOfCarrierWifiConnectionAuthFailure();
            } else {
                mWifiMetrics.incrementNumOfCarrierWifiConnectionNonAuthFailure();
            }
        }

        boolean isAssociationRejection = level2FailureCode
                == WifiMetrics.ConnectionEvent.FAILURE_ASSOCIATION_REJECTION;
        boolean isAuthenticationFailure = level2FailureCode
                == WifiMetrics.ConnectionEvent.FAILURE_AUTHENTICATION_FAILURE
                && level2FailureReason != WifiMetricsProto.ConnectionEvent.AUTH_FAILURE_WRONG_PSWD;
        if ((isAssociationRejection || isAuthenticationFailure)
                && mWifiConfigManager.isInFlakyRandomizationSsidHotlist(mTargetNetworkId)
                && isPrimary()) {
            mConnectionFailureNotifier
                    .showFailedToConnectDueToNoRandomizedMacSupportNotification(mTargetNetworkId);
        }

        ScanResult candidate = configuration.getNetworkSelectionStatus().getCandidate();
        int frequency = mWifiInfo.getFrequency();
        if (frequency == WifiInfo.UNKNOWN_FREQUENCY && candidate != null) {
            frequency = candidate.frequency;
        }
        mWifiMetrics.endConnectionEvent(mInterfaceName, level2FailureCode,
                connectivityFailureCode, level2FailureReason, frequency, statusCode);
        mWifiConnectivityManager.handleConnectionAttemptEnded(
                mClientModeManager, level2FailureCode, level2FailureReason, bssid,
                configuration);
        mNetworkFactory.handleConnectionAttemptEnded(level2FailureCode, configuration, bssid,
                level2FailureReason);
        mWifiNetworkSuggestionsManager.handleConnectionAttemptEnded(
                level2FailureCode, configuration, getConnectedBssidInternal());
        if (candidate != null
                && !TextUtils.equals(candidate.BSSID, getConnectedBssidInternal())) {
            mWifiMetrics.incrementNumBssidDifferentSelectionBetweenFrameworkAndFirmware();
        }
        handleConnectionAttemptEndForDiagnostics(level2FailureCode);
    }

    /* If this connection attempt fails after 802.1x stage, clear intermediate cached data. */
    void clearNetworkCachedDataIfNeeded(WifiConfiguration config, int reason) {
        if (config == null) return;

        switch(reason) {
            case StaIfaceReasonCode.UNSPECIFIED:
            case StaIfaceReasonCode.DEAUTH_LEAVING:
                logi("Keep PMK cache for network disconnection reason " + reason);
                break;
            default:
                mWifiNative.removeNetworkCachedData(config.networkId);
                break;
        }
    }

    /**
     * Returns the sufficient RSSI for the frequency that this network is last seen on.
     */
    private int getSufficientRssi(int networkId, String bssid) {
        ScanDetailCache scanDetailCache =
                mWifiConfigManager.getScanDetailCacheForNetwork(networkId);
        if (scanDetailCache == null) {
            return WifiInfo.INVALID_RSSI;
        }
        ScanResult scanResult = scanDetailCache.getScanResult(bssid);
        if (scanResult == null) {
            return WifiInfo.INVALID_RSSI;
        }
        return mScoringParams.getSufficientRssi(scanResult.frequency);
    }

    private int convertToWifiBlocklistMonitorFailureReason(
            int level2FailureCode, int failureReason) {
        switch (level2FailureCode) {
            case WifiMetrics.ConnectionEvent.FAILURE_ASSOCIATION_TIMED_OUT:
                return WifiBlocklistMonitor.REASON_ASSOCIATION_TIMEOUT;
            case WifiMetrics.ConnectionEvent.FAILURE_ASSOCIATION_REJECTION:
                if (failureReason == WifiMetricsProto.ConnectionEvent
                        .ASSOCIATION_REJECTION_AP_UNABLE_TO_HANDLE_NEW_STA) {
                    return WifiBlocklistMonitor.REASON_AP_UNABLE_TO_HANDLE_NEW_STA;
                }
                return WifiBlocklistMonitor.REASON_ASSOCIATION_REJECTION;
            case WifiMetrics.ConnectionEvent.FAILURE_AUTHENTICATION_FAILURE:
                if (failureReason == WifiMetricsProto.ConnectionEvent.AUTH_FAILURE_WRONG_PSWD) {
                    return WifiBlocklistMonitor.REASON_WRONG_PASSWORD;
                } else if (failureReason == WifiMetricsProto.ConnectionEvent
                        .AUTH_FAILURE_EAP_FAILURE) {
                    return WifiBlocklistMonitor.REASON_EAP_FAILURE;
                }
                return WifiBlocklistMonitor.REASON_AUTHENTICATION_FAILURE;
            case WifiMetrics.ConnectionEvent.FAILURE_DHCP:
                return WifiBlocklistMonitor.REASON_DHCP_FAILURE;
            case WifiMetrics.ConnectionEvent.FAILURE_NETWORK_DISCONNECTION:
                if (failureReason == WifiMetricsProto.ConnectionEvent.DISCONNECTION_NON_LOCAL) {
                    return WifiBlocklistMonitor.REASON_NONLOCAL_DISCONNECT_CONNECTING;
                }
                return -1;
            case WifiMetrics.ConnectionEvent.FAILURE_NO_RESPONSE:
                return WifiBlocklistMonitor.REASON_FAILURE_NO_RESPONSE;
            default:
                return -1;
        }
    }

    private void handleIPv4Success(DhcpResultsParcelable dhcpResults) {
        if (mVerboseLoggingEnabled) {
            logd("handleIPv4Success <" + dhcpResults.toString() + ">");
            logd("link address " + dhcpResults.baseConfiguration.getIpAddress());
        }

        Inet4Address addr;
        synchronized (mDhcpResultsParcelableLock) {
            mDhcpResultsParcelable = dhcpResults;
            addr = (Inet4Address) dhcpResults.baseConfiguration.getIpAddress().getAddress();
        }

        if (mIsAutoRoaming) {
            int previousAddress = mWifiInfo.getIpAddress();
            int newAddress = Inet4AddressUtils.inet4AddressToIntHTL(addr);
            if (previousAddress != newAddress) {
                logd("handleIPv4Success, roaming and address changed"
                        + mWifiInfo + " got: " + addr);
            }
        }

        mWifiInfo.setInetAddress(addr);

        final WifiConfiguration config = getConnectedWifiConfigurationInternal();
        if (config != null) {
            updateWifiInfoWhenConnected(config);
            mWifiConfigManager.updateRandomizedMacExpireTime(config, dhcpResults.leaseDuration);
            mWifiBlocklistMonitor.handleDhcpProvisioningSuccess(mLastBssid, mWifiInfo.getSSID());
        }

        // Set meteredHint if DHCP result says network is metered
        if (dhcpResults.vendorInfo != null && dhcpResults.vendorInfo.contains("ANDROID_METERED")) {
            mWifiInfo.setMeteredHint(true);
            mWifiMetrics.addMeteredStat(config, true);
        } else {
            mWifiMetrics.addMeteredStat(config, false);
        }

        updateCapabilities();
        updateCurrentConnectionInfo();
    }

    private void handleSuccessfulIpConfiguration() {
        mLastSignalLevel = -1; // Force update of signal strength
        WifiConfiguration c = getConnectedWifiConfigurationInternal();
        if (c != null) {
            // Reset IP failure tracking
            c.getNetworkSelectionStatus().clearDisableReasonCounter(
                    WifiConfiguration.NetworkSelectionStatus.DISABLED_DHCP_FAILURE);
        }
    }

    private void handleIPv4Failure() {
        // TODO: Move this to provisioning failure, not DHCP failure.
        // DHCPv4 failure is expected on an IPv6-only network.
        mWifiDiagnostics.triggerBugReportDataCapture(WifiDiagnostics.REPORT_REASON_DHCP_FAILURE);
        if (mVerboseLoggingEnabled) {
            int count = -1;
            WifiConfiguration config = getConnectedWifiConfigurationInternal();
            if (config != null) {
                count = config.getNetworkSelectionStatus().getDisableReasonCounter(
                        WifiConfiguration.NetworkSelectionStatus.DISABLED_DHCP_FAILURE);
            }
            log("DHCP failure count=" + count);
        }
        synchronized (mDhcpResultsParcelableLock) {
            mDhcpResultsParcelable = new DhcpResultsParcelable();
        }
        if (mVerboseLoggingEnabled) {
            logd("handleIPv4Failure");
        }
    }

    private void handleIpConfigurationLost() {
        mWifiInfo.setInetAddress(null);
        mWifiInfo.setMeteredHint(false);

        mWifiConfigManager.updateNetworkSelectionStatus(mLastNetworkId,
                WifiConfiguration.NetworkSelectionStatus.DISABLED_DHCP_FAILURE);

        /* DHCP times out after about 30 seconds, we do a
         * disconnect thru supplicant, we will let autojoin retry connecting to the network
         */
        mFrameworkDisconnectReasonOverride = WifiStatsLog.WIFI_DISCONNECT_REPORTED__FAILURE_CODE__DISCONNECT_IP_PROVISIONING_FAILURE;
        mWifiNative.disconnect(mInterfaceName);
        updateCurrentConnectionInfo();
    }

    private void handleIpReachabilityLost(int lossReason) {
        mWifiBlocklistMonitor.handleBssidConnectionFailure(mWifiInfo.getBSSID(),
                getConnectedWifiConfiguration(),
                WifiBlocklistMonitor.REASON_ABNORMAL_DISCONNECT, mWifiInfo.getRssi());
        mWifiScoreCard.noteIpReachabilityLost(mWifiInfo);
        mWifiInfo.setInetAddress(null);
        mWifiInfo.setMeteredHint(false);

        mFrameworkDisconnectReasonOverride = WifiStatsLog.WIFI_DISCONNECT_REPORTED__FAILURE_CODE__DISCONNECT_NUD_FAILURE_GENERIC;
        if (lossReason == ReachabilityLossReason.ROAM) {
            mFrameworkDisconnectReasonOverride = WifiStatsLog.WIFI_DISCONNECT_REPORTED__FAILURE_CODE__DISCONNECT_NUD_FAILURE_ROAM;
        } else if (lossReason == ReachabilityLossReason.CONFIRM) {
            mFrameworkDisconnectReasonOverride = WifiStatsLog.WIFI_DISCONNECT_REPORTED__FAILURE_CODE__DISCONNECT_NUD_FAILURE_CONFIRM;
        } else if (lossReason == ReachabilityLossReason.ORGANIC) {
            mFrameworkDisconnectReasonOverride = WifiStatsLog.WIFI_DISCONNECT_REPORTED__FAILURE_CODE__DISCONNECT_NUD_FAILURE_ORGANIC;
        }
        // Disconnect via supplicant, and let autojoin retry connecting to the network.
        mWifiNative.disconnect(mInterfaceName);
        updateCurrentConnectionInfo();
    }

    /**
     * Process IP Reachability failures by recreating a new IpClient instance to refresh L3
     * provisioning while keeping L2 still connected.
     *
     * This method is invoked only upon receiving reachability failure post roaming or triggered
     * from Wi-Fi RSSI polling or organic kernel probe.
     */
    private void processIpReachabilityFailure(int lossReason) {
        WifiConfiguration config = getConnectedWifiConfigurationInternal();
        if (config == null) {
            // special case for IP reachability lost which happens right after linked network
            // roaming. The linked network roaming reset the mLastNetworkId which results in
            // the connected configuration to be null.
            config = getConnectingWifiConfigurationInternal();
            if (config == null) {
                // config could be null if it had been removed from WifiConfigManager. In this case
                // we should simply disconnect.
                handleIpReachabilityLost(lossReason);
                return;
            }
        }
        final long curTime = mClock.getElapsedSinceBootMillis();
        if (curTime - mNudFailureCounter.first <= mWifiGlobals.getRepeatedNudFailuresWindowMs()) {
            mNudFailureCounter = new Pair<>(curTime, mNudFailureCounter.second + 1);
        } else {
            mNudFailureCounter = new Pair<>(curTime, 1);
        }
        if (mNudFailureCounter.second >= mWifiGlobals.getRepeatedNudFailuresThreshold()) {
            // Disable and disconnect due to repeated NUD failures within limited time window.
            mWifiConfigManager.updateNetworkSelectionStatus(config.networkId,
                    WifiConfiguration.NetworkSelectionStatus.DISABLED_REPEATED_NUD_FAILURES);
            handleIpReachabilityLost(lossReason);
            mNudFailureCounter = new Pair<>(0L, 0);
            return;
        }

        final NetworkAgentConfig naConfig = getNetworkAgentConfigInternal(config);
        final NetworkCapabilities nc = getCapabilities(
                getConnectedWifiConfigurationInternal(), getConnectedBssidInternal());

        mWifiInfo.setInetAddress(null);
        if (mNetworkAgent != null) {
            if (SdkLevel.isAtLeastT()) {
                mNetworkAgent.unregisterAfterReplacement(NETWORK_AGENT_TEARDOWN_DELAY_MS);
            } else {
                mNetworkAgent.unregister();
            }
        }
        mNetworkAgent = mWifiInjector.makeWifiNetworkAgent(nc, mLinkProperties, naConfig,
                mNetworkFactory.getProvider(), new WifiNetworkAgentCallback());
        mWifiScoreReport.setNetworkAgent(mNetworkAgent);
        if (SdkLevel.isAtLeastT()) {
            mQosPolicyRequestHandler.setNetworkAgent(mNetworkAgent);
        }

        // Shutdown IpClient and cleanup IpClientCallbacks instance before transition to
        // WaitBeforeL3ProvisioningState, in order to prevent WiFi state machine from
        // processing the posted message sent from the legacy IpClientCallbacks instance,
        // see b/286338765.
        maybeShutdownIpclient();
        transitionTo(mWaitBeforeL3ProvisioningState);

        updateCurrentConnectionInfo();
    }

    private void processLegacyIpReachabilityLost(int lossReason) {
        mWifiMetrics.logStaEvent(mInterfaceName, StaEvent.TYPE_CMD_IP_REACHABILITY_LOST);
        mWifiMetrics.logWifiIsUnusableEvent(mInterfaceName,
                WifiIsUnusableEvent.TYPE_IP_REACHABILITY_LOST);
        mWifiMetrics.addToWifiUsabilityStatsList(mInterfaceName,
                WifiUsabilityStats.LABEL_BAD,
                WifiUsabilityStats.TYPE_IP_REACHABILITY_LOST, -1);
        if (mWifiGlobals.getIpReachabilityDisconnectEnabled()) {
            handleIpReachabilityLost(lossReason);
        } else {
            logd("CMD_IP_REACHABILITY_LOST but disconnect disabled -- ignore");
        }
    }

    private boolean shouldIgnoreNudDisconnectForWapiInCn() {
        // temporary work-around for <=U devices
        if (SdkLevel.isAtLeastV()) return false;

        if (!mWifiGlobals.disableNudDisconnectsForWapiInSpecificCc() || !"CN".equalsIgnoreCase(
                mWifiInjector.getWifiCountryCode().getCountryCode())) {
            return false;
        }
        WifiConfiguration config = getConnectedWifiConfigurationInternal();
        return config != null && (config.allowedProtocols.get(WifiConfiguration.Protocol.WAPI)
                || config.getAuthType() == WifiConfiguration.KeyMgmt.NONE);
    }

    private void handleIpReachabilityFailure(@NonNull ReachabilityLossInfoParcelable lossInfo) {
        if (lossInfo == null) {
            Log.e(getTag(), "lossInfo should never be null");
            processLegacyIpReachabilityLost(-1);
            return;
        }

        switch (lossInfo.reason) {
            case ReachabilityLossReason.ROAM:
                processIpReachabilityFailure(lossInfo.reason);
                break;
            case ReachabilityLossReason.CONFIRM:
            case ReachabilityLossReason.ORGANIC: {
                if (shouldIgnoreNudDisconnectForWapiInCn()) {
                    logd("CMD_IP_REACHABILITY_FAILURE but disconnect disabled for WAPI -- ignore");
                    return;
                }

                if (mDeviceConfigFacade.isHandleRssiOrganicKernelFailuresEnabled()) {
                    processIpReachabilityFailure(lossInfo.reason);
                } else {
                    processLegacyIpReachabilityLost(lossInfo.reason);
                }
                break;
            }
            default:
                logd("Invalid failure reason " + lossInfo.reason + "from onIpReachabilityFailure");
        }
    }

    private NetworkAgentConfig getNetworkAgentConfigInternal(WifiConfiguration config) {
        boolean explicitlySelected = false;
        // Non primary CMMs is never user selected. This prevents triggering the No Internet
        // dialog for those networks, which is difficult to handle.
        if (isPrimary() && isRecentlySelectedByTheUser(config)) {
            // If explicitlySelected is true, the network was selected by the user via Settings
            // or QuickSettings. If this network has Internet access, switch to it. Otherwise,
            // switch to it only if the user confirms that they really want to switch, or has
            // already confirmed and selected "Don't ask again".
            explicitlySelected =
                    mWifiPermissionsUtil.checkNetworkSettingsPermission(config.lastConnectUid);
            if (mVerboseLoggingEnabled) {
                log("Network selected by UID " + config.lastConnectUid
                        + " explicitlySelected=" + explicitlySelected);
            }
        }
        NetworkAgentConfig.Builder naConfigBuilder = new NetworkAgentConfig.Builder()
                .setLegacyType(ConnectivityManager.TYPE_WIFI)
                .setLegacyTypeName(NETWORKTYPE)
                .setExplicitlySelected(explicitlySelected)
                .setUnvalidatedConnectivityAcceptable(
                        explicitlySelected && config.noInternetAccessExpected)
                .setPartialConnectivityAcceptable(config.noInternetAccessExpected);
        if (config.carrierMerged) {
            String subscriberId = null;
            TelephonyManager subMgr = mTelephonyManager.createForSubscriptionId(
                    config.subscriptionId);
            if (subMgr != null) {
                subscriberId = subMgr.getSubscriberId();
            }
            if (subscriberId != null) {
                naConfigBuilder.setSubscriberId(subscriberId);
            }
        }
        if (mVcnManager == null && SdkLevel.isAtLeastS()) {
            mVcnManager = mContext.getSystemService(VcnManager.class);
        }
        if (mVcnManager != null && mVcnPolicyChangeListener == null && SdkLevel.isAtLeastS()) {
            mVcnPolicyChangeListener = new WifiVcnNetworkPolicyChangeListener();
            mVcnManager.addVcnNetworkPolicyChangeListener(new HandlerExecutor(getHandler()),
                    mVcnPolicyChangeListener);
        }
        return naConfigBuilder.build();
    }

    /*
     * Read a MAC address in /proc/net/arp, used by ClientModeImpl
     * so as to record MAC address of default gateway.
     **/
    private String macAddressFromRoute(String ipAddress) {
        String macAddress = null;
        BufferedReader reader = null;
        try {
            reader = mWifiInjector.createBufferedReader(ARP_TABLE_PATH);

            // Skip over the line bearing column titles
            String line = reader.readLine();

            while ((line = reader.readLine()) != null) {
                String[] tokens = line.split("[ ]+");
                if (tokens.length < 6) {
                    continue;
                }

                // ARP column format is
                // IPAddress HWType Flags HWAddress Mask Device
                String ip = tokens[0];
                String mac = tokens[3];

                if (TextUtils.equals(ipAddress, ip)) {
                    macAddress = mac;
                    break;
                }
            }

            if (macAddress == null) {
                loge("Did not find remoteAddress {" + ipAddress + "} in /proc/net/arp");
            }

        } catch (FileNotFoundException e) {
            loge("Could not open /proc/net/arp to lookup mac address");
        } catch (IOException e) {
            loge("Could not read /proc/net/arp to lookup mac address");
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException e) {
                // Do nothing
            }
        }
        return macAddress;

    }

    /**
     * Determine if the specified auth failure is considered to be a permanent wrong password
     * failure. The criteria for such failure is when wrong password error is detected
     * and the network had never been connected before.
     *
     * For networks that have previously connected successfully, we consider wrong password
     * failures to be temporary, to be on the conservative side.  Since this might be the
     * case where we are trying to connect to a wrong network (e.g. A network with same SSID
     * but different password).
     */
    private boolean isPermanentWrongPasswordFailure(int networkId, int reasonCode) {
        if (reasonCode != WifiManager.ERROR_AUTH_FAILURE_WRONG_PSWD) {
            return false;
        }
        WifiConfiguration network = mWifiConfigManager.getConfiguredNetwork(networkId);
        if (network != null && network.getNetworkSelectionStatus().hasEverConnected()) {
            return false;
        }
        return true;
    }

     /**
     * Dynamically change the MAC address to use the locally randomized
     * MAC address generated for each network.
     * @param config WifiConfiguration with mRandomizedMacAddress to change into. If the address
     * is masked out or not set, it will generate a new random MAC address.
     */
    private void configureRandomizedMacAddress(WifiConfiguration config) {
        if (config == null) {
            Log.e(getTag(), "No config to change MAC address to");
            return;
        }
        String currentMacString = mWifiNative.getMacAddress(mInterfaceName);
        MacAddress currentMac = NativeUtil.getMacAddressOrNull(currentMacString);
        MacAddress newMac = mWifiConfigManager.getRandomizedMacAndUpdateIfNeeded(config,
                isSecondaryInternet() && mClientModeManager.isSecondaryInternetDbsAp());
        if (!WifiConfiguration.isValidMacAddressForRandomization(newMac)) {
            Log.wtf(getTag(), "Config generated an invalid MAC address");
        } else if (Objects.equals(newMac, currentMac)) {
            Log.d(getTag(), "No changes in MAC address");
        } else {
            mWifiMetrics.logStaEvent(mInterfaceName, StaEvent.TYPE_MAC_CHANGE, config);
            boolean setMacSuccess =
                    mWifiNative.setStaMacAddress(mInterfaceName, newMac);
            if (setMacSuccess) {
                mWifiNative.removeNetworkCachedDataIfNeeded(config.networkId, newMac);
            }
            if (mVerboseLoggingEnabled) {
                Log.d(getTag(), "ConnectedMacRandomization SSID(" + config.getPrintableSsid()
                        + "). setMacAddress(" + newMac.toString() + ") from "
                        + currentMacString + " = " + setMacSuccess);
            }
        }
    }

    /**
     * Sets the current MAC to the factory MAC address.
     */
    private void setCurrentMacToFactoryMac(WifiConfiguration config) {
        MacAddress factoryMac = retrieveFactoryMacAddressAndStoreIfNecessary();
        if (factoryMac == null) {
            Log.e(getTag(), "Fail to set factory MAC address. Factory MAC is null.");
            return;
        }
        String currentMacStr = mWifiNative.getMacAddress(mInterfaceName);
        if (!TextUtils.equals(currentMacStr, factoryMac.toString())) {
            if (mWifiNative.setStaMacAddress(mInterfaceName, factoryMac)) {
                mWifiNative.removeNetworkCachedDataIfNeeded(config.networkId, factoryMac);
                mWifiMetrics.logStaEvent(mInterfaceName, StaEvent.TYPE_MAC_CHANGE, config);
            } else {
                Log.e(getTag(), "Failed to set MAC address to " + "'"
                        + factoryMac.toString() + "'");
            }
        }
    }

    /**
     * Helper method to start other services and get state ready for client mode
     */
    private void setupClientMode() {
        Log.d(getTag(), "setupClientMode() ifacename = " + mInterfaceName);

        setMulticastFilter(true);
        registerForWifiMonitorEvents();
        if (isPrimary()) {
            mWifiLastResortWatchdog.clearAllFailureCounts();
        }
        mWifiNative.setSupplicantLogLevel(mVerboseLoggingEnabled);

        // Initialize data structures
        mTargetBssid = SUPPLICANT_BSSID_ANY;
        mTargetNetworkId = WifiConfiguration.INVALID_NETWORK_ID;
        mLastBssid = null;
        mIpProvisioningTimedOut = false;
        mLastNetworkId = WifiConfiguration.INVALID_NETWORK_ID;
        mLastSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
        mLastSimBasedConnectionCarrierName = null;
        mLastSignalLevel = -1;
        mEnabledTdlsPeers.clear();
        // TODO: b/79504296 This broadcast has been deprecated and should be removed
        sendSupplicantConnectionChangedBroadcast(true);

        mWifiNative.setExternalSim(mInterfaceName, true);

        mWifiDiagnostics.startPktFateMonitoring(mInterfaceName);
        mWifiDiagnostics.startLogging(mInterfaceName);

        mMboOceController.enable();

        // Enable bluetooth coexistence scan mode when bluetooth connection is active.
        // When this mode is on, some of the low-level scan parameters used by the
        // driver are changed to reduce interference with bluetooth
        mWifiNative.setBluetoothCoexistenceScanMode(
                mInterfaceName, mWifiGlobals.isBluetoothConnected());
        sendNetworkChangeBroadcast(DetailedState.DISCONNECTED);

        // Disable legacy multicast filtering, which on some chipsets defaults to enabled.
        // Legacy IPv6 multicast filtering blocks ICMPv6 router advertisements which breaks IPv6
        // provisioning. Legacy IPv4 multicast filtering may be re-enabled later via
        // IpClient.Callback.setFallbackMulticastFilter()
        mWifiNative.stopFilteringMulticastV4Packets(mInterfaceName);
        mWifiNative.stopFilteringMulticastV6Packets(mInterfaceName);

        // Set the right suspend mode settings
        mWifiNative.setSuspendOptimizations(mInterfaceName, mSuspendOptNeedsDisabled == 0
                && mContext.getResources().getBoolean(
                        R.bool.config_wifiSuspendOptimizationsEnabled));

        enablePowerSave();

        // Disable wpa_supplicant from auto reconnecting.
        mWifiNative.enableStaAutoReconnect(mInterfaceName, false);
        // STA has higher priority over P2P
        mWifiNative.setConcurrencyPriority(true);
        if (mClientModeManager.getRole() == ROLE_CLIENT_PRIMARY) {
            // Loads the firmware roaming info which gets used in WifiBlocklistMonitor
            mWifiConnectivityHelper.getFirmwareRoamingInfo();
        }

        // Retrieve and store the factory MAC address (on first bootup).
        retrieveFactoryMacAddressAndStoreIfNecessary();
        updateCurrentConnectionInfo();
    }

    /**
     * Helper method to stop external services and clean up state from client mode.
     */
    private void stopClientMode() {
        handleNetworkDisconnect(false,
                WifiStatsLog.WIFI_DISCONNECT_REPORTED__FAILURE_CODE__WIFI_DISABLED);
        // exiting supplicant started state is now only applicable to client mode
        mWifiDiagnostics.stopLogging(mInterfaceName);

        mMboOceController.disable();
        maybeShutdownIpclient();
        deregisterForWifiMonitorEvents(); // uses mInterfaceName, must call before nulling out
        // TODO: b/79504296 This broadcast has been deprecated and should be removed
        sendSupplicantConnectionChangedBroadcast(false);
    }

    /**
     * Helper method called when a L3 connection is successfully established to a network.
     */
    void registerConnected() {
        if (isPrimary()) {
            mWifiInjector.getActiveModeWarden().setCurrentNetwork(getCurrentNetwork());
        }
        if (mLastNetworkId != WifiConfiguration.INVALID_NETWORK_ID) {
            WifiConfiguration config = getConnectedWifiConfigurationInternal();
            boolean shouldSetUserConnectChoice = config != null
                    && mIsUserSelected
                    && isRecentlySelectedByTheUser(config)
                    && (config.getNetworkSelectionStatus().hasEverConnected()
                    || config.isEphemeral());
            mWifiConfigManager.updateNetworkAfterConnect(mLastNetworkId,
                    mIsUserSelected, shouldSetUserConnectChoice, mWifiInfo.getRssi());
            // Notify PasspointManager of Passpoint network connected event.
            WifiConfiguration currentNetwork = getConnectedWifiConfigurationInternal();
            if (currentNetwork != null && currentNetwork.isPasspoint()) {
                mPasspointManager.onPasspointNetworkConnected(
                        currentNetwork.getProfileKey(), currentNetwork.SSID);
            }
        }
    }

    void registerDisconnected() {
        // The role of the ClientModeManager may have been changed to scan only before handling
        // the network disconnect.
        if (isPrimary() || mClientModeManager.getRole() == ROLE_CLIENT_SCAN_ONLY) {
            mWifiInjector.getActiveModeWarden().setCurrentNetwork(getCurrentNetwork());
        }
        if (mLastNetworkId != WifiConfiguration.INVALID_NETWORK_ID) {
            mWifiConfigManager.updateNetworkAfterDisconnect(mLastNetworkId);
        }
    }

    /**
     * Returns WifiConfiguration object corresponding to the currently connected network, null if
     * not connected.
     */
    @Nullable private WifiConfiguration getConnectedWifiConfigurationInternal() {
        if (mLastNetworkId == WifiConfiguration.INVALID_NETWORK_ID) {
            return null;
        }
        return mWifiConfigManager.getConfiguredNetwork(mLastNetworkId);
    }

    /**
     * Returns WifiConfiguration object corresponding to the currently connecting network, null if
     * not connecting.
     */
    @Nullable private WifiConfiguration getConnectingWifiConfigurationInternal() {
        if (mTargetNetworkId == WifiConfiguration.INVALID_NETWORK_ID) {
            return null;
        }
        return mWifiConfigManager.getConfiguredNetwork(mTargetNetworkId);
    }

    @Nullable private String getConnectedBssidInternal() {
        return mLastBssid;
    }

    @Nullable private String getConnectingBssidInternal() {
        return mTargetBssid;
    }

    /**
     * Returns WifiConfiguration object corresponding to the currently connected network, null if
     * not connected.
     */
    @Override
    @Nullable public WifiConfiguration getConnectedWifiConfiguration() {
        if (!isConnected()) return null;
        return getConnectedWifiConfigurationInternal();
    }

    /**
     * Returns WifiConfiguration object corresponding to the currently connecting network, null if
     * not connecting.
     */
    @Override
    @Nullable public WifiConfiguration getConnectingWifiConfiguration() {
        if (!isConnecting() && !isRoaming()) return null;
        return getConnectingWifiConfigurationInternal();
    }

    @Override
    @Nullable public String getConnectedBssid() {
        if (!isConnected()) return null;
        return getConnectedBssidInternal();
    }

    @Override
    @Nullable public String getConnectingBssid() {
        if (!isConnecting() && !isRoaming()) return null;
        return getConnectingBssidInternal();
    }

    ScanResult getCurrentScanResult() {
        WifiConfiguration config = getConnectedWifiConfigurationInternal();
        if (config == null) {
            return null;
        }
        String bssid = mWifiInfo.getBSSID();
        if (bssid == null) {
            bssid = mTargetBssid;
        }
        ScanDetailCache scanDetailCache =
                mWifiConfigManager.getScanDetailCacheForNetwork(config.networkId);

        if (scanDetailCache == null) {
            return null;
        }

        return scanDetailCache.getScanResult(bssid);
    }

    private MacAddress getCurrentBssidInternalMacAddress() {
        return NativeUtil.getMacAddressOrNull(mLastBssid);
    }

    private void connectToNetwork(WifiConfiguration config) {
        if ((config != null) && mWifiNative.connectToNetwork(mInterfaceName, config)) {
            // Update the internal config once the connection request is accepted.
            mWifiConfigManager.setNetworkLastUsedSecurityParams(config.networkId,
                    config.getNetworkSelectionStatus().getCandidateSecurityParams());
            mWifiLastResortWatchdog.noteStartConnectTime(config.networkId);
            mWifiMetrics.logStaEvent(mInterfaceName, StaEvent.TYPE_CMD_START_CONNECT, config);
            mIsAutoRoaming = false;
            transitionTo(mL2ConnectingState);
        } else {
            loge("CMD_START_CONNECT Failed to start connection to network " + config);
            mTargetWifiConfiguration = null;
            stopIpClient();
            reportConnectionAttemptEnd(
                    WifiMetrics.ConnectionEvent.FAILURE_CONNECT_NETWORK_FAILED,
                    WifiMetricsProto.ConnectionEvent.HLF_NONE,
                    WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, 0);
        }
    }

    private void makeIpClient() {
        mIpClientCallbacks = new IpClientCallbacksImpl();
        mFacade.makeIpClient(mContext, mInterfaceName, mIpClientCallbacks);
        mIpClientCallbacks.awaitCreation();
    }

    private void maybeShutdownIpclient() {
        if (mIpClient == null) return;
        if (!mIpClient.shutdown()) {
            logd("Fail to shut down IpClient");
            return;
        }

        // Block to make sure IpClient has really shut down, lest cleanup
        // race with, say, bringup code over in tethering.
        mIpClientCallbacks.awaitShutdown();
        mIpClientCallbacks = null;
        mIpClient = null;
    }

    // Always use "arg1" to take the current IpClient callbacks index to check if the callbacks
    // come from the current mIpClientCallbacks instance.
    private boolean isFromCurrentIpClientCallbacks(Message msg) {
        if (mIpClientCallbacks == null || msg == null) return false;
        return mIpClientCallbacks.getCallbackIndex() == msg.arg1;
    }

    /********************************************************
     * HSM states
     *******************************************************/

    class ConnectableState extends RunnerState {
        private boolean mIsScreenStateChangeReceiverRegistered = false;
        WifiDeviceStateChangeManager.StateChangeCallback mScreenStateChangeReceiver =
                new WifiDeviceStateChangeManager.StateChangeCallback() {
                    @Override
                    public void onScreenStateChanged(boolean screenOn) {
                        if (screenOn) {
                            sendMessage(CMD_SCREEN_STATE_CHANGED, 1);
                        } else {
                            sendMessage(CMD_SCREEN_STATE_CHANGED, 0);
                        }
                    }
                };

        ConnectableState(int threshold) {
            super(threshold, mWifiInjector.getWifiHandlerLocalLog());
        }

        @Override
        public void enterImpl() {
            Log.d(getTag(), "entering ConnectableState: ifaceName = " + mInterfaceName);
            setSuspendOptimizationsNative(SUSPEND_DUE_TO_HIGH_PERF, true);
            if (mWifiGlobals.isConnectedMacRandomizationEnabled()) {
                mFailedToResetMacAddress = !mWifiNative.setStaMacAddress(
                        mInterfaceName, MacAddressUtils.createRandomUnicastAddress());
                if (mFailedToResetMacAddress) {
                    Log.e(getTag(), "Failed to set random MAC address on ClientMode creation");
                }
            }
            mWifiInfo.setMacAddress(mWifiNative.getMacAddress(mInterfaceName));
            updateCurrentConnectionInfo();
            mWifiStateTracker.updateState(mInterfaceName, WifiStateTracker.INVALID);
            makeIpClient();
        }

        private void continueEnterSetup(IpClientManager ipClientManager) {
            mIpClient = ipClientManager;
            setupClientMode();

            if (!mIsScreenStateChangeReceiverRegistered) {
                mWifiDeviceStateChangeManager.registerStateChangeCallback(
                        mScreenStateChangeReceiver);
                mIsScreenStateChangeReceiverRegistered = true;
            }
            // Learn the initial state of whether the screen is on.
            // We update this field when we receive broadcasts from the system.
            handleScreenStateChanged(mContext.getSystemService(PowerManager.class).isInteractive());

            if (!mWifiNative.removeAllNetworks(mInterfaceName)) {
                loge("Failed to remove networks on entering connect mode");
            }
            mWifiInfo.reset();
            mWifiInfo.setSupplicantState(SupplicantState.DISCONNECTED);

            sendNetworkChangeBroadcast(DetailedState.DISCONNECTED);

            // Inform metrics that Wifi is Enabled (but not yet connected)
            mWifiMetrics.setWifiState(mInterfaceName, WifiMetricsProto.WifiLog.WIFI_DISCONNECTED);
            mWifiMetrics.logStaEvent(mInterfaceName, StaEvent.TYPE_WIFI_ENABLED);
            mWifiScoreCard.noteSupplicantStateChanged(mWifiInfo);
            updateCurrentConnectionInfo();
        }

        @Override
        public void exitImpl() {
            // Inform metrics that Wifi is being disabled (Toggled, airplane enabled, etc)
            mWifiMetrics.setWifiState(mInterfaceName, WifiMetricsProto.WifiLog.WIFI_DISABLED);
            mWifiMetrics.logStaEvent(mInterfaceName, StaEvent.TYPE_WIFI_DISABLED);

            if (!mWifiNative.removeAllNetworks(mInterfaceName)) {
                loge("Failed to remove networks on exiting connect mode");
            }
            if (mIsScreenStateChangeReceiverRegistered) {
                mWifiDeviceStateChangeManager.unregisterStateChangeCallback(
                        mScreenStateChangeReceiver);
                mIsScreenStateChangeReceiverRegistered = false;
            }

            stopClientMode();
            mWifiScoreCard.doWrites();
        }

        @Override
        public String getMessageLogRec(int what) {
            return ClientModeImpl.class.getSimpleName() + "."
                    + ConnectableState.class.getSimpleName() + "." + getWhatToString(what);
        }

        @Override
        public boolean processMessageImpl(Message message) {
            switch (message.what) {
                case CMD_IPCLIENT_CREATED:
                    if (!isFromCurrentIpClientCallbacks(message)) break;
                    if (mIpClient != null) {
                        loge("Setup connectable state again when IpClient is ready?");
                    } else {
                        IpClientManager ipClientManager = (IpClientManager) message.obj;
                        continueEnterSetup(ipClientManager);
                    }
                    break;
                case CMD_ENABLE_RSSI_POLL: {
                    mEnableRssiPolling = (message.arg1 == 1);
                    break;
                }
                case CMD_SCREEN_STATE_CHANGED: {
                    handleScreenStateChanged(message.arg1 != 0);
                    break;
                }
                case WifiP2pServiceImpl.DISCONNECT_WIFI_REQUEST: {
                    if (mIpClient == null) {
                        logd("IpClient is not ready, "
                                + "WifiP2pServiceImpl.DISCONNECT_WIFI_REQUEST dropped");
                        break;
                    }
                    if (mWifiP2pConnection.shouldTemporarilyDisconnectWifi()) {
                        mWifiMetrics.logStaEvent(mInterfaceName, StaEvent.TYPE_FRAMEWORK_DISCONNECT,
                                StaEvent.DISCONNECT_P2P_DISCONNECT_WIFI_REQUEST);
                        mFrameworkDisconnectReasonOverride = WifiStatsLog.WIFI_DISCONNECT_REPORTED__FAILURE_CODE__DISCONNECT_P2P_REQUESTED_DISCONNECT;
                        mWifiNative.disconnect(mInterfaceName);
                    } else {
                        mWifiNative.reconnect(mInterfaceName);
                    }
                    break;
                }
                case CMD_RECONNECT: {
                    WorkSource workSource = (WorkSource) message.obj;
                    mWifiConnectivityManager.forceConnectivityScan(workSource);
                    break;
                }
                case CMD_REASSOCIATE: {
                    if (mIpClient != null) {
                        logd("IpClient is not ready, REASSOCIATE dropped");

                        mWifiNative.reassociate(mInterfaceName);
                    }
                    break;
                }
                case CMD_START_CONNECT: {
                    if (mIpClient == null) {
                        logd("IpClient is not ready, START_CONNECT dropped");

                        break;
                    }
                    /* connect command coming from auto-join */
                    int netId = message.arg1;
                    int uid = message.arg2;
                    String bssid = (String) message.obj;
                    mSentHLPs = false;
                    // Stop lingering (if it was lingering before) if we start a new connection.
                    // This means that the ClientModeManager was reused for another purpose, so it
                    // should no longer be in lingering mode.
                    mClientModeManager.setShouldReduceNetworkScore(false);

                    if (!hasConnectionRequests()) {
                        if (mNetworkAgent == null) {
                            loge("CMD_START_CONNECT but no requests and not connected,"
                                    + " bailing");
                            break;
                        } else if (!mWifiPermissionsUtil.checkNetworkSettingsPermission(uid)) {
                            loge("CMD_START_CONNECT but no requests and connected, but app "
                                    + "does not have sufficient permissions, bailing");
                            break;
                        }
                    }
                    WifiConfiguration config =
                            mWifiConfigManager.getConfiguredNetworkWithoutMasking(netId);
                    logd("CMD_START_CONNECT "
                            + " my state " + getCurrentState().getName()
                            + " nid=" + netId
                            + " roam=" + mIsAutoRoaming);
                    if (config == null) {
                        loge("CMD_START_CONNECT and no config, bail out...");
                        break;
                    }
                    mCurrentConnectionDetectedCaptivePortal = false;
                    mTargetNetworkId = netId;
                    // Update scorecard while there is still state from existing connection
                    mLastScanRssi = mWifiConfigManager.findScanRssi(netId,
                            mWifiHealthMonitor.getScanRssiValidTimeMs());
                    mWifiScoreCard.noteConnectionAttempt(mWifiInfo, mLastScanRssi, config.SSID);
                    if (isPrimary()) {
                        mWifiBlocklistMonitor.setAllowlistSsids(config.SSID,
                                Collections.emptyList());
                        mWifiBlocklistMonitor.updateFirmwareRoamingConfiguration(
                                Set.of(config.SSID));
                    }

                    updateWifiConfigOnStartConnection(config, bssid);
                    reportConnectionAttemptStart(config, mTargetBssid,
                            WifiMetricsProto.ConnectionEvent.ROAM_UNRELATED, uid);

                    String currentMacAddress = mWifiNative.getMacAddress(mInterfaceName);
                    mWifiInfo.setMacAddress(currentMacAddress);
                    updateCurrentConnectionInfo();
                    if (mVerboseLoggingEnabled) {
                        Log.i(getTag(), "Connecting with " + currentMacAddress
                                + " as the mac address");
                    }
                    mWifiPseudonymManager.enableStrictConservativePeerModeIfSupported(config);
                    mTargetWifiConfiguration = config;
                    mNetworkNotFoundEventCount = 0;
                    /* Check for FILS configuration again after updating the config */
                    if (config.isFilsSha256Enabled() || config.isFilsSha384Enabled()) {
                        boolean isIpClientStarted = startIpClient(config, true);
                        if (isIpClientStarted) {
                            mIpClientWithPreConnection = true;
                            transitionTo(mL2ConnectingState);
                            break;
                        }
                    }
                    setSelectedRcoiForPasspoint(config);

                    // TOFU flow for devices that do not support this feature
                    mInsecureEapNetworkHandler.prepareConnection(mTargetWifiConfiguration);
                    mLeafCertSent = false;
                    if (!isTrustOnFirstUseSupported()) {
                        mInsecureEapNetworkHandler.startUserApprovalIfNecessary(mIsUserSelected);
                    }
                    connectToNetwork(config);
                    break;
                }
                case CMD_START_FILS_CONNECTION: {
                    if (!isFromCurrentIpClientCallbacks(message)) break;
                    if (mIpClient == null) {
                        logd("IpClient is not ready, START_FILS_CONNECTION dropped");
                        break;
                    }
                    mWifiMetrics.incrementConnectRequestWithFilsAkmCount();
                    List<Layer2PacketParcelable> packets;
                    packets = (List<Layer2PacketParcelable>) message.obj;
                    if (mVerboseLoggingEnabled) {
                        Log.d(getTag(), "Send HLP IEs to supplicant");
                    }
                    addLayer2PacketsToHlpReq(packets);
                    WifiConfiguration config = mTargetWifiConfiguration;
                    connectToNetwork(config);
                    break;
                }
                case CMD_CONNECT_NETWORK: {
                    ConnectNetworkMessage cnm = (ConnectNetworkMessage) message.obj;
                    if (mIpClient == null) {
                        logd("IpClient is not ready, CONNECT_NETWORK dropped");
                        cnm.listener.sendFailure(WifiManager.ActionListener.FAILURE_INTERNAL_ERROR);
                        break;
                    }
                    NetworkUpdateResult result = cnm.result;
                    int netId = result.getNetworkId();
                    connectToUserSelectNetwork(
                            netId, message.sendingUid, result.hasCredentialChanged(),
                            cnm.packageName, cnm.attributionTag);
                    mWifiMetrics.logStaEvent(mInterfaceName, StaEvent.TYPE_CONNECT_NETWORK,
                            mWifiConfigManager.getConfiguredNetwork(netId));
                    cnm.listener.sendSuccess();
                    break;
                }
                case CMD_SAVE_NETWORK: {
                    ConnectNetworkMessage cnm = (ConnectNetworkMessage) message.obj;
                    if (mIpClient == null) {
                        logd("IpClient is not ready, SAVE_NETWORK dropped");
                        cnm.listener.sendFailure(WifiManager.ActionListener.FAILURE_INTERNAL_ERROR);
                        break;
                    }
                    NetworkUpdateResult result = cnm.result;
                    int netId = result.getNetworkId();
                    if (mWifiInfo.getNetworkId() == netId) {
                        if (result.hasCredentialChanged()) {
                            // The network credentials changed and we're connected to this network,
                            // start a new connection with the updated credentials.
                            logi("CMD_SAVE_NETWORK credential changed for nid="
                                    + netId + ". Reconnecting.");
                            startConnectToNetwork(netId, message.sendingUid, SUPPLICANT_BSSID_ANY);
                        } else {
                            if (result.hasProxyChanged()) {
                                if (mIpClient != null) {
                                    log("Reconfiguring proxy on connection");
                                    WifiConfiguration currentConfig =
                                            getConnectedWifiConfigurationInternal();
                                    if (currentConfig != null) {
                                        mIpClient.setHttpProxy(currentConfig.getHttpProxy());
                                    } else {
                                        Log.w(getTag(),
                                                "CMD_SAVE_NETWORK proxy change - but no current "
                                                        + "Wi-Fi config");
                                    }
                                }
                            }
                            if (result.hasIpChanged()) {
                                // The current connection configuration was changed
                                // We switched from DHCP to static or from static to DHCP, or the
                                // static IP address has changed.
                                log("Reconfiguring IP on connection");
                                WifiConfiguration currentConfig =
                                        getConnectedWifiConfigurationInternal();
                                if (currentConfig != null) {
                                    transitionTo(mL3ProvisioningState);
                                } else {
                                    Log.w(getTag(), "CMD_SAVE_NETWORK Ip change - but no current "
                                            + "Wi-Fi config");
                                }
                            }
                        }
                    } else if (mWifiInfo.getNetworkId() == WifiConfiguration.INVALID_NETWORK_ID
                            && result.hasCredentialChanged()) {
                        logi("CMD_SAVE_NETWORK credential changed for nid="
                                + netId + " while disconnected. Connecting.");
                        WifiConfiguration config =
                                mWifiConfigManager.getConfiguredNetwork(netId);
                        if (!mWifiPermissionsUtil.isAdminRestrictedNetwork(config)
                                && !mWifiGlobals.isDeprecatedSecurityTypeNetwork(config)) {
                            startConnectToNetwork(netId, message.sendingUid, SUPPLICANT_BSSID_ANY);
                        }
                    } else if (result.hasCredentialChanged()) {
                        WifiConfiguration currentConfig =
                                getConnectedWifiConfigurationInternal();
                        WifiConfiguration updatedConfig =
                                mWifiConfigManager.getConfiguredNetwork(netId);
                        if (currentConfig != null && currentConfig.isLinked(updatedConfig)) {
                            logi("current network linked config saved, update linked networks");
                            updateLinkedNetworks(currentConfig);
                        }
                    }
                    cnm.listener.sendSuccess();
                    break;
                }
                case CMD_BLUETOOTH_CONNECTION_STATE_CHANGE: {
                    mWifiNative.setBluetoothCoexistenceScanMode(
                            mInterfaceName, mWifiGlobals.isBluetoothConnected());
                    break;
                }
                case CMD_SET_SUSPEND_OPT_ENABLED: {
                    if (message.arg1 == 1) {
                        setSuspendOptimizationsNative(SUSPEND_DUE_TO_SCREEN, true);
                        if (message.arg2 == 1) {
                            mSuspendWakeLock.release();
                        }
                    } else {
                        setSuspendOptimizationsNative(SUSPEND_DUE_TO_SCREEN, false);
                    }
                    break;
                }
                case WifiMonitor.ANQP_DONE_EVENT: {
                    mPasspointManager.notifyANQPDone((AnqpEvent) message.obj);
                    break;
                }
                case CMD_STOP_IP_PACKET_OFFLOAD: {
                    int slot = message.arg1;
                    int ret = stopWifiIPPacketOffload(slot);
                    if (mNetworkAgent != null) {
                        mNetworkAgent.sendSocketKeepaliveEvent(slot, ret);
                    }
                    break;
                }
                case WifiMonitor.RX_HS20_ANQP_ICON_EVENT: {
                    mPasspointManager.notifyIconDone((IconEvent) message.obj);
                    break;
                }
                case WifiMonitor.HS20_DEAUTH_IMMINENT_EVENT:
                    mPasspointManager.handleDeauthImminentEvent((WnmData) message.obj,
                            getConnectedWifiConfigurationInternal());
                    break;
                case WifiMonitor.HS20_TERMS_AND_CONDITIONS_ACCEPTANCE_REQUIRED_EVENT:
                    mWifiMetrics
                            .incrementTotalNumberOfPasspointConnectionsWithTermsAndConditionsUrl();
                    mTermsAndConditionsUrl = mPasspointManager
                            .handleTermsAndConditionsEvent((WnmData) message.obj,
                            getConnectedWifiConfigurationInternal());
                    if (mTermsAndConditionsUrl == null) {
                        loge("Disconnecting from Passpoint network due to an issue with the "
                                + "Terms and Conditions URL");
                        mFrameworkDisconnectReasonOverride = WifiStatsLog.WIFI_DISCONNECT_REPORTED__FAILURE_CODE__DISCONNECT_PASSPOINT_TAC;
                        sendMessage(CMD_DISCONNECT, StaEvent.DISCONNECT_PASSPOINT_TAC);
                    }
                    break;
                case WifiMonitor.HS20_REMEDIATION_EVENT:
                    mPasspointManager.receivedWnmFrame((WnmData) message.obj);
                    break;
                case WifiMonitor.MBO_OCE_BSS_TM_HANDLING_DONE: {
                    handleBssTransitionRequest((BtmFrameData) message.obj);
                    break;
                }
                case CMD_CONFIG_ND_OFFLOAD: {
                    if (!isFromCurrentIpClientCallbacks(message)) break;
                    final boolean enabled = (message.arg2 > 0);
                    mWifiNative.configureNeighborDiscoveryOffload(mInterfaceName, enabled);
                    break;
                }
                // Link configuration (IP address, DNS, ...) changes notified via netlink
                case CMD_UPDATE_LINKPROPERTIES: {
                    if (!isFromCurrentIpClientCallbacks(message)) break;
                    updateLinkProperties((LinkProperties) message.obj);
                    break;
                }
                case CMD_START_IP_PACKET_OFFLOAD:
                case CMD_ADD_KEEPALIVE_PACKET_FILTER_TO_APF:
                case CMD_REMOVE_KEEPALIVE_PACKET_FILTER_FROM_APF: {
                    if (mNetworkAgent != null) {
                        mNetworkAgent.sendSocketKeepaliveEvent(message.arg1,
                                SocketKeepalive.ERROR_INVALID_NETWORK);
                    }
                    break;
                }
                case CMD_INSTALL_PACKET_FILTER: {
                    if (!isFromCurrentIpClientCallbacks(message)) break;
                    if (mContext.getResources().getBoolean(
                            R.bool.config_wifiEnableApfOnNonPrimarySta)
                            || isPrimary()) {
                        mWifiNative.installPacketFilter(mInterfaceName, (byte[]) message.obj);
                    } else {
                        Log.i(TAG, "Not applying packet filter on non primary CMM");
                    }
                    break;
                }
                case CMD_READ_PACKET_FILTER: {
                    if (!isFromCurrentIpClientCallbacks(message)) break;
                    byte[] packetFilter = null;
                    if (mContext.getResources().getBoolean(
                            R.bool.config_wifiEnableApfOnNonPrimarySta)
                            || isPrimary()) {
                        packetFilter = mWifiNative.readPacketFilter(mInterfaceName);
                    } else {
                        Log.v(TAG, "APF not supported on non primary CMM - return null");
                    }
                    if (mIpClient != null) {
                        mIpClient.readPacketFilterComplete(packetFilter);
                    }
                    break;
                }
                case CMD_SET_FALLBACK_PACKET_FILTERING: {
                    if (!isFromCurrentIpClientCallbacks(message)) break;
                    if ((boolean) message.obj) {
                        mWifiNative.startFilteringMulticastV4Packets(mInterfaceName);
                    } else {
                        mWifiNative.stopFilteringMulticastV4Packets(mInterfaceName);
                    }
                    break;
                }
                case CMD_SET_MAX_DTIM_MULTIPLIER: {
                    if (!isFromCurrentIpClientCallbacks(message)) break;
                    final int maxMultiplier = message.arg2;
                    final boolean success =
                            mWifiNative.setDtimMultiplier(mInterfaceName, maxMultiplier);
                    if (mVerboseLoggingEnabled) {
                        Log.d(TAG, "Set maximum DTIM Multiplier to " + maxMultiplier
                                + (success ? " SUCCESS" : " FAIL"));
                    }
                    break;
                }
                case WifiP2pServiceImpl.SET_MIRACAST_MODE:
                    if (mVerboseLoggingEnabled) logd("SET_MIRACAST_MODE: " + (int)message.arg1);
                    mWifiConnectivityManager.saveMiracastMode((int)message.arg1);
                    break;
                case WifiP2pServiceImpl.P2P_CONNECTION_CHANGED:
                    NetworkInfo info = (NetworkInfo) message.obj;
                    if (info != null) {
                        NetworkInfo.DetailedState detailedState = info.getDetailedState();
                        mWifiConnectivityManager.saveP2pGroupStarted(
                                detailedState == NetworkInfo.DetailedState.CONNECTED);
                    }
                    break;
                case CMD_RESET_SIM_NETWORKS:
                case WifiMonitor.NETWORK_CONNECTION_EVENT:
                case WifiMonitor.NETWORK_DISCONNECTION_EVENT:
                case WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT:
                case WifiMonitor.AUTHENTICATION_FAILURE_EVENT:
                case WifiMonitor.ASSOCIATION_REJECTION_EVENT:
                case CMD_RSSI_POLL:
                case CMD_ONESHOT_RSSI_POLL:
                case CMD_PRE_DHCP_ACTION:
                case CMD_PRE_DHCP_ACTION_COMPLETE:
                case CMD_POST_DHCP_ACTION:
                case WifiMonitor.SUP_REQUEST_IDENTITY:
                case WifiMonitor.SUP_REQUEST_SIM_AUTH:
                case WifiMonitor.TARGET_BSSID_EVENT:
                case WifiMonitor.ASSOCIATED_BSSID_EVENT:
                case WifiMonitor.TRANSITION_DISABLE_INDICATION:
                case CMD_UNWANTED_NETWORK:
                case CMD_CONNECTING_WATCHDOG_TIMER:
                case WifiMonitor.NETWORK_NOT_FOUND_EVENT:
                case CMD_ROAM_WATCHDOG_TIMER: {
                    // no-op: all messages must be handled in the base state in case it was missed
                    // in one of the child states.
                    break;
                }
                case CMD_ACCEPT_EAP_SERVER_CERTIFICATE:
                    // If TOFU is not supported, then we are already connected
                    if (!isTrustOnFirstUseSupported()) break;
                    // Got an approval for a TOFU network. Disconnect (if connected) and trigger
                    // a connection to the new approved network.
                    logd("User accepted TOFU provided certificate");
                    startConnectToNetwork(message.arg1, Process.WIFI_UID, SUPPLICANT_BSSID_ANY);
                    break;
                case CMD_REJECT_EAP_INSECURE_CONNECTION:
                case CMD_START_ROAM:
                case CMD_IP_CONFIGURATION_SUCCESSFUL:
                case CMD_IP_CONFIGURATION_LOST:
                case CMD_IP_REACHABILITY_LOST:
                case CMD_IP_REACHABILITY_FAILURE: {
                    mMessageHandlingStatus = MESSAGE_HANDLING_STATUS_DISCARD;
                    break;
                }
                case 0: {
                    // We want to notice any empty messages (with what == 0) that might crop up.
                    // For example, we may have recycled a message sent to multiple handlers.
                    Log.wtf(getTag(), "Error! empty message encountered");
                    break;
                }
                default: {
                    loge("Error! unhandled message" + message);
                    break;
                }
            }

            logStateAndMessage(message, this);
            return HANDLED;
        }
    }

    private boolean handleL3MessagesWhenNotConnected(Message message) {
        boolean handleStatus = HANDLED;

        if (!mIpClientWithPreConnection) {
            return NOT_HANDLED;
        }

        switch (message.what) {
            case CMD_PRE_DHCP_ACTION:
                if (!isFromCurrentIpClientCallbacks(message)) break;
                handlePreDhcpSetup();
                break;
            case CMD_PRE_DHCP_ACTION_COMPLETE:
                if (mIpClient != null) {
                    mIpClient.completedPreDhcpAction();
                }
                break;
            case CMD_IPV4_PROVISIONING_FAILURE:
                stopDhcpSetup();
                deferMessage(message);
                break;
            case CMD_POST_DHCP_ACTION:
            case CMD_IPV4_PROVISIONING_SUCCESS:
            case CMD_IP_CONFIGURATION_SUCCESSFUL:
                deferMessage(message);
                break;
            default:
                return NOT_HANDLED;
        }

        return handleStatus;
    }

    private WifiNetworkAgentSpecifier createNetworkAgentSpecifier(
            @NonNull WifiConfiguration currentWifiConfiguration, @Nullable String currentBssid,
            boolean matchLocationSensitiveInformation) {
        // Defensive copy to avoid mutating the passed argument
        final WifiConfiguration conf = new WifiConfiguration(currentWifiConfiguration);
        conf.BSSID = currentBssid;

        int band = WifiNetworkSpecifier.getBand(mWifiInfo.getFrequency());

        if (!isPrimary() && mWifiGlobals.isSupportMultiInternetDual5G()
                && band == ScanResult.WIFI_BAND_5_GHZ) {
            if (mWifiInfo.getFrequency() <= ScanResult.BAND_5_GHZ_LOW_HIGHEST_FREQ_MHZ) {
                band = ScanResult.WIFI_BAND_5_GHZ_LOW;
            } else {
                band = ScanResult.WIFI_BAND_5_GHZ_HIGH;
            }
        }

        WifiNetworkAgentSpecifier wns =
                new WifiNetworkAgentSpecifier(conf, band, matchLocationSensitiveInformation);
        return wns;
    }

    private NetworkCapabilities getCapabilities(
            WifiConfiguration currentWifiConfiguration, String currentBssid) {
        final NetworkCapabilities.Builder builder =
                new NetworkCapabilities.Builder(mNetworkCapabilitiesFilter);
        // MatchAllNetworkSpecifier set in the mNetworkCapabilitiesFilter should never be set in the
        // agent's specifier.
        builder.setNetworkSpecifier(null);
        if (currentWifiConfiguration == null) {
            return builder.build();
        }

        if (mWifiInfo.isTrusted()) {
            builder.addCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED);
        } else {
            builder.removeCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED);
        }

        if (mWifiInfo.isRestricted()) {
            builder.removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED);
        }

        if (SdkLevel.isAtLeastS()) {
            if (mWifiInfo.isOemPaid()) {
                builder.addCapability(NetworkCapabilities.NET_CAPABILITY_OEM_PAID);
                builder.removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED);
            } else {
                builder.removeCapability(NetworkCapabilities.NET_CAPABILITY_OEM_PAID);
            }
            if (mWifiInfo.isOemPrivate()) {
                builder.addCapability(NetworkCapabilities.NET_CAPABILITY_OEM_PRIVATE);
                builder.removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED);
            } else {
                builder.removeCapability(NetworkCapabilities.NET_CAPABILITY_OEM_PRIVATE);
            }
        }

        builder.setOwnerUid(currentWifiConfiguration.creatorUid);
        builder.setAdministratorUids(new int[]{currentWifiConfiguration.creatorUid});
        if (SdkLevel.isAtLeastT()) {
            builder.setAllowedUids(Set.of(currentWifiConfiguration.creatorUid));
        }

        if (!WifiConfiguration.isMetered(currentWifiConfiguration, mWifiInfo)) {
            builder.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
        } else {
            builder.removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
        }

        if (mWifiInfo.getRssi() != WifiInfo.INVALID_RSSI) {
            builder.setSignalStrength(mWifiInfo.getRssi());
        } else {
            builder.setSignalStrength(NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED);
        }

        if (currentWifiConfiguration.osu) {
            builder.removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        }

        if (!WifiManager.UNKNOWN_SSID.equals(mWifiInfo.getSSID())) {
            builder.setSsid(mWifiInfo.getSSID());
        }

        // Only send out WifiInfo in >= Android S devices.
        if (SdkLevel.isAtLeastS()) {
            builder.setTransportInfo(new WifiInfo(mWifiInfo));

            if (mWifiInfo.getSubscriptionId() != SubscriptionManager.INVALID_SUBSCRIPTION_ID
                    && mWifiInfo.isCarrierMerged()) {
                builder.setSubscriptionIds(Collections.singleton(mWifiInfo.getSubscriptionId()));
            }
        }

        List<Integer> uids = new ArrayList<>(mNetworkFactory
                .getSpecificNetworkRequestUids(
                        currentWifiConfiguration, currentBssid));
        // There is an active local only specific request.
        if (!uids.isEmpty()) {
            // Remove internet capability.
            if (!mNetworkFactory.shouldHaveInternetCapabilities()) {
                builder.removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
            }
            if (SdkLevel.isAtLeastS()) {
                builder.setUids(getUidRangeSet(uids));
            } else {
                // Use requestor Uid and PackageName on pre-S device.
                Pair<Integer, String> specificRequestUidAndPackageName = mNetworkFactory
                        .getSpecificNetworkRequestUidAndPackageName(currentWifiConfiguration,
                                currentBssid);
                // Fill up the uid/packageName for this connection.
                builder.setRequestorUid(specificRequestUidAndPackageName.first);
                builder.setRequestorPackageName(specificRequestUidAndPackageName.second);
            }
            // Fill up the network agent specifier for this connection, allowing NetworkCallbacks
            // to match local-only specifiers in requests. TODO(b/187921303): a third-party app can
            // observe this location-sensitive information by registering a NetworkCallback.
            builder.setNetworkSpecifier(createNetworkAgentSpecifier(currentWifiConfiguration,
                    getConnectedBssidInternal(), true /* matchLocalOnlySpecifiers */));
        } else {
            // Fill up the network agent specifier for this connection, without allowing
            // NetworkCallbacks to match local-only specifiers in requests.
            builder.setNetworkSpecifier(createNetworkAgentSpecifier(currentWifiConfiguration,
                    getConnectedBssidInternal(), false /* matchLocalOnlySpecifiers */));
        }

        updateLinkBandwidth(builder);
        final NetworkCapabilities networkCapabilities = builder.build();
        if (mVcnManager == null || !currentWifiConfiguration.carrierMerged
                || !SdkLevel.isAtLeastS()) {
            return networkCapabilities;
        }
        final VcnNetworkPolicyResult vcnNetworkPolicy =
                mVcnManager.applyVcnNetworkPolicy(networkCapabilities, mLinkProperties);
        if (vcnNetworkPolicy.isTeardownRequested()) {
            mFrameworkDisconnectReasonOverride =
                    WifiStatsLog.WIFI_DISCONNECT_REPORTED__FAILURE_CODE__DISCONNECT_VNC_REQUEST;
            sendMessage(CMD_DISCONNECT, StaEvent.DISCONNECT_VCN_REQUEST);
        }
        final NetworkCapabilities vcnCapability = vcnNetworkPolicy.getNetworkCapabilities();
        if (!vcnCapability.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED)) {
            if (mVerboseLoggingEnabled) {
                logd("NET_CAPABILITY_NOT_VCN_MANAGED is removed");
            }
            builder.removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED);
        }
        if (!vcnCapability.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)) {
            if (mVerboseLoggingEnabled) {
                logd("NET_CAPABILITY_NOT_RESTRICTED is removed");
            }
            builder.removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED);
        }
        return builder.build();
    }

    private Set<Range<Integer>> getUidRangeSet(List<Integer> uids) {
        Collections.sort(uids);
        Set<Range<Integer>> uidRanges = new ArraySet<>();
        int start = 0;
        int next = 0;
        for (int i : uids) {
            if (start == next) {
                start = i;
                next = start + 1;
            } else if (i == next) {
                next++;
            } else {
                uidRanges.add(new Range<>(start, next - 1));
                start = i;
                next = start + 1;
            }
        }
        uidRanges.add(new Range<>(start, next - 1));
        return uidRanges;
    }

    private void updateLinkBandwidth(NetworkCapabilities.Builder networkCapabilitiesBuilder) {
        int txTputKbps = 0;
        int rxTputKbps = 0;
        int currRssi = mWifiInfo.getRssi();
        if (currRssi != WifiInfo.INVALID_RSSI) {
            WifiScoreCard.PerNetwork network = mWifiScoreCard.lookupNetwork(mWifiInfo.getSSID());
            txTputKbps = network.getTxLinkBandwidthKbps();
            rxTputKbps = network.getRxLinkBandwidthKbps();
        } else {
            // Fall back to max link speed. This should rarely happen if ever
            int maxTxLinkSpeedMbps = mWifiInfo.getMaxSupportedTxLinkSpeedMbps();
            int maxRxLinkSpeedMbps = mWifiInfo.getMaxSupportedRxLinkSpeedMbps();
            txTputKbps = maxTxLinkSpeedMbps * 1000;
            rxTputKbps = maxRxLinkSpeedMbps * 1000;
        }
        if (mVerboseLoggingEnabled) {
            logd("reported txKbps " + txTputKbps + " rxKbps " + rxTputKbps);
        }
        if (txTputKbps > 0) {
            networkCapabilitiesBuilder.setLinkUpstreamBandwidthKbps(txTputKbps);
        }
        if (rxTputKbps > 0) {
            networkCapabilitiesBuilder.setLinkDownstreamBandwidthKbps(rxTputKbps);
        }
    }

    /**
     * Method to update network capabilities from the current WifiConfiguration.
     */
    @Override
    public void updateCapabilities() {
        updateCapabilities(getConnectedWifiConfigurationInternal());
    }

    /**
     * Check if BSSID belongs to any of the affiliated link BSSID's.
     * @param bssid BSSID of the AP.
     * @return true if BSSID matches to one of the affiliated link BSSIDs, false otherwise.
     */
    public boolean isAffiliatedLinkBssid(@NonNull MacAddress bssid) {
        List<MloLink> links = mWifiInfo.getAffiliatedMloLinks();
        for (MloLink link: links) {
            if (link.getApMacAddress().equals(bssid)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if the connection is MLO (Multi-Link Operation).
     * @return true if connection is MLO, otherwise false.
     */
    public boolean isMlo() {
        return !mWifiInfo.getAssociatedMloLinks().isEmpty();
    }

    private void updateCapabilities(WifiConfiguration currentWifiConfiguration) {
        updateCapabilities(getCapabilities(currentWifiConfiguration, getConnectedBssidInternal()));
    }

    private void updateCapabilities(NetworkCapabilities networkCapabilities) {
        if (mNetworkAgent == null) {
            return;
        }
        mNetworkAgent.sendNetworkCapabilitiesAndCache(networkCapabilities);
    }

    private void handleEapAuthFailure(int networkId, int errorCode) {
        WifiConfiguration targetedNetwork =
                mWifiConfigManager.getConfiguredNetwork(mTargetNetworkId);
        if (targetedNetwork != null) {
            switch (targetedNetwork.enterpriseConfig.getEapMethod()) {
                case WifiEnterpriseConfig.Eap.SIM:
                case WifiEnterpriseConfig.Eap.AKA:
                case WifiEnterpriseConfig.Eap.AKA_PRIME:
                    if (errorCode == WifiNative.EAP_SIM_VENDOR_SPECIFIC_CERT_EXPIRED) {
                        mWifiCarrierInfoManager.resetCarrierKeysForImsiEncryption(targetedNetwork);
                    } else {
                        int carrierId = targetedNetwork.carrierId;
                        if (mWifiCarrierInfoManager.isOobPseudonymFeatureEnabled(carrierId)) {
                            mWifiPseudonymManager.retrieveOobPseudonymWithRateLimit(carrierId);
                        }
                    }
                    break;

                default:
                    // Do Nothing
            }
        }
    }

    /**
     * All callbacks are triggered on the main Wifi thread.
     * See {@link WifiNetworkAgent#WifiNetworkAgent}'s looper argument in
     * {@link WifiInjector#makeWifiNetworkAgent}.
     */
    private class WifiNetworkAgentCallback implements WifiNetworkAgent.Callback {
        private int mLastNetworkStatus = -1; // To detect when the status really changes

        private boolean isThisCallbackActive() {
            return mNetworkAgent != null && mNetworkAgent.getCallback() == this;
        }

        @Override
        public void onNetworkUnwanted() {
            // Ignore if we're not the current networkAgent.
            if (!isThisCallbackActive()) return;
            if (mVerboseLoggingEnabled) {
                logd("WifiNetworkAgent -> Wifi unwanted score " + mWifiInfo.getScore());
            }
            unwantedNetwork(NETWORK_STATUS_UNWANTED_DISCONNECT);
        }

        @Override
        public void onValidationStatus(int status, @Nullable Uri redirectUri) {
            if (!isThisCallbackActive()) return;
            if (status == mLastNetworkStatus) return;
            mLastNetworkStatus = status;
            if (status == NetworkAgent.VALIDATION_STATUS_NOT_VALID) {
                if (mVerboseLoggingEnabled) {
                    logd("WifiNetworkAgent -> Wifi networkStatus invalid, score="
                            + mWifiInfo.getScore());
                }
                unwantedNetwork(NETWORK_STATUS_UNWANTED_VALIDATION_FAILED);
            } else if (status == NetworkAgent.VALIDATION_STATUS_VALID) {
                if (mVerboseLoggingEnabled) {
                    logd("WifiNetworkAgent -> Wifi networkStatus valid, score= "
                            + mWifiInfo.getScore());
                }
                mWifiMetrics.logStaEvent(mInterfaceName, StaEvent.TYPE_NETWORK_AGENT_VALID_NETWORK);
                doNetworkStatus(status);
            }
            boolean captivePortalDetected = redirectUri != null
                    && redirectUri.toString() != null
                    && redirectUri.toString().length() > 0;
            if (captivePortalDetected) {
                Log.i(getTag(), "Captive Portal detected, status=" + status
                        + ", redirectUri=" + redirectUri);
                mWifiConfigManager.noteCaptivePortalDetected(mWifiInfo.getNetworkId());
                mCmiMonitor.onCaptivePortalDetected(mClientModeManager);
                mCurrentConnectionDetectedCaptivePortal = true;
            }
        }

        @Override
        public void onSaveAcceptUnvalidated(boolean accept) {
            if (!isThisCallbackActive()) return;
            ClientModeImpl.this.sendMessage(CMD_ACCEPT_UNVALIDATED, accept ? 1 : 0);
        }

        @Override
        public void onStartSocketKeepalive(int slot, @NonNull Duration interval,
                @NonNull KeepalivePacketData packet) {
            if (!isThisCallbackActive()) return;
            ClientModeImpl.this.sendMessage(
                    CMD_START_IP_PACKET_OFFLOAD, slot, (int) interval.getSeconds(), packet);
        }

        @Override
        public void onStopSocketKeepalive(int slot) {
            if (!isThisCallbackActive()) return;
            ClientModeImpl.this.sendMessage(CMD_STOP_IP_PACKET_OFFLOAD, slot);
        }

        @Override
        public void onAddKeepalivePacketFilter(int slot, @NonNull KeepalivePacketData packet) {
            if (!isThisCallbackActive()) return;
            ClientModeImpl.this.sendMessage(
                    CMD_ADD_KEEPALIVE_PACKET_FILTER_TO_APF, slot, 0, packet);
        }

        @Override
        public void onRemoveKeepalivePacketFilter(int slot) {
            if (!isThisCallbackActive()) return;
            ClientModeImpl.this.sendMessage(CMD_REMOVE_KEEPALIVE_PACKET_FILTER_FROM_APF, slot);
        }

        @Override
        public void onSignalStrengthThresholdsUpdated(@NonNull int[] thresholds) {
            if (!isThisCallbackActive()) return;
            // Enable RSSI monitoring only on primary STA
            if (!isPrimary()) {
                return;
            }
            mWifiThreadRunner.post(
                    () -> mRssiMonitor.updateAppThresholdsAndStartMonitor(thresholds));
        }

        @Override
        public void onAutomaticReconnectDisabled() {
            if (!isThisCallbackActive()) return;
            unwantedNetwork(NETWORK_STATUS_UNWANTED_DISABLE_AUTOJOIN);
        }

        @Override
        public void onDscpPolicyStatusUpdated(int policyId, int status) {
            mQosPolicyRequestHandler.setQosPolicyStatus(policyId, status);
        }
    }

    @Override
    public void onDeviceMobilityStateUpdated(@DeviceMobilityState int newState) {
        if (!mScreenOn) {
            return;
        }
        if (isPrimary()) {
            mRssiMonitor.updatePollRssiInterval(newState);
        }
    }

    @Override
    public void setLinkLayerStatsPollingInterval(int newIntervalMs) {
        mRssiMonitor.overridePollRssiInterval(newIntervalMs);
    }

    private boolean isNewConnectionInProgress(@NonNull String disconnectingSsid) {
        String targetSsid = getConnectingSsidInternal();
        // If network is removed while connecting, targetSsid can be null.
        if (targetSsid == null) {
            return false;
        }
        // When connecting to another network while already connected, the old network will first
        // disconnect before the new connection can begin. Thus, the presence of a mLastNetworkId
        // that's different from the mTargetNetworkId indicates that this network disconnection is
        // triggered for the previously connected network as opposed to the current ongoing
        // connection.
        boolean isConnectingWhileAlreadyConnected =
                mLastNetworkId != WifiConfiguration.INVALID_NETWORK_ID
                && mLastNetworkId != mTargetNetworkId;

        // This second condition is needed to catch cases where 2 simultaneous connections happen
        // back-to-back. When a new connection start before the previous one finishes, the
        // previous network will get removed from the supplicant and cause a disconnect message
        // to be received with the previous network's SSID. Thus, if the disconnecting SSID does not
        // match the target SSID, it means a new connection is in progress.
        boolean isConnectingToAnotherNetwork = !disconnectingSsid.equals(targetSsid);
        return isConnectingWhileAlreadyConnected || isConnectingToAnotherNetwork;
    }

    private void unwantedNetwork(int reason) {
        sendMessage(CMD_UNWANTED_NETWORK, reason);
    }

    private void doNetworkStatus(int status) {
        sendMessage(CMD_NETWORK_STATUS, status);
    }

    private void updatePseudonymFromOob(int carrierId, String pseudonym) {
        sendMessage(CMD_UPDATE_OOB_PSEUDONYM, carrierId, 0, pseudonym);
    }

    class ConnectingOrConnectedState extends RunnerState {
        ConnectingOrConnectedState(int threshold) {
            super(threshold, mWifiInjector.getWifiHandlerLocalLog());
        }

        @Override
        public void enterImpl() {
            if (mVerboseLoggingEnabled) Log.v(getTag(), "Entering ConnectingOrConnectedState");
            mCmiMonitor.onConnectionStart(mClientModeManager);
        }

        @Override
        public void exitImpl() {
            if (mVerboseLoggingEnabled) Log.v(getTag(), "Exiting ConnectingOrConnectedState");
            mCmiMonitor.onConnectionEnd(mClientModeManager);

            // Not connected/connecting to any network:
            // 1. Disable the network in supplicant to prevent it from auto-connecting. We don't
            // remove the network to avoid losing any cached info in supplicant (reauth, etc) in
            // case we reconnect back to the same network.
            // 2. Set a random MAC address to ensure that we're not leaking the MAC address.
            mWifiNative.disableNetwork(mInterfaceName);
            if (mWifiGlobals.isConnectedMacRandomizationEnabled()) {
                mFailedToResetMacAddress = !mWifiNative.setStaMacAddress(
                        mInterfaceName, MacAddressUtils.createRandomUnicastAddress());
                if (mFailedToResetMacAddress) {
                    Log.e(getTag(), "Failed to set random MAC address on disconnect");
                }
            }
            if (mWifiInfo.getBSSID() != null) {
                mWifiBlocklistMonitor.removeAffiliatedBssids(mWifiInfo.getBSSID());
            }
            mWifiInfo.reset();
            mWifiInfo.setSupplicantState(SupplicantState.DISCONNECTED);
            mWifiScoreCard.noteSupplicantStateChanged(mWifiInfo);
            updateCurrentConnectionInfo();

            // For secondary client roles, they should stop themselves upon disconnection.
            // - Primary role shouldn't because it is persistent, and should try connecting to other
            //   networks upon disconnection.
            // - ROLE_CLIENT_LOCAL_ONLY shouldn't because it has auto-retry logic if the connection
            //   fails. WifiNetworkFactory will explicitly remove the CMM when the request is
            //   complete.
            // TODO(b/160346062): Maybe clean this up by having ClientModeManager register a
            //  onExitConnectingOrConnectedState() callback with ClientModeImpl and implementing
            //  this logic in ClientModeManager. ClientModeImpl should be role-agnostic.
            ClientRole role = mClientModeManager.getRole();
            if (role == ROLE_CLIENT_SECONDARY_LONG_LIVED
                    || role == ROLE_CLIENT_SECONDARY_TRANSIENT) {
                if (mVerboseLoggingEnabled) {
                    Log.d(getTag(), "Disconnected in ROLE_CLIENT_SECONDARY_*, "
                            + "stop ClientModeManager=" + mClientModeManager);
                }
                // stop owner ClientModeManager, which will in turn stop this ClientModeImpl
                mClientModeManager.stop();
            }
        }

        @Override
        public String getMessageLogRec(int what) {
            return ClientModeImpl.class.getSimpleName() + "."
                    + ConnectingOrConnectedState.class.getSimpleName() + "." + getWhatToString(
                    what);
        }

        @Override
        public boolean processMessageImpl(Message message) {
            boolean handleStatus = HANDLED;
            switch (message.what) {
                case WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT: {
                    StateChangeResult stateChangeResult = (StateChangeResult) message.obj;
                    SupplicantState state = handleSupplicantStateChange(stateChangeResult);
                    // Supplicant can fail to report a NETWORK_DISCONNECTION_EVENT
                    // when authentication times out after a successful connection,
                    // we can figure this from the supplicant state. If supplicant
                    // state is DISCONNECTED, but the agent is not disconnected, we
                    // need to handle a disconnection
                    if (mVerboseLoggingEnabled) {
                        log("ConnectingOrConnectedState: Supplicant State change "
                                + stateChangeResult);
                    }
                    @SupplicantEventCode int supplicantEvent;
                    switch (stateChangeResult.state) {
                        case COMPLETED:
                            supplicantEvent = SupplicantStaIfaceHal.SUPPLICANT_EVENT_CONNECTED;
                            break;
                        case ASSOCIATING:
                            supplicantEvent = SupplicantStaIfaceHal.SUPPLICANT_EVENT_ASSOCIATING;
                            break;
                        case ASSOCIATED:
                            supplicantEvent = SupplicantStaIfaceHal.SUPPLICANT_EVENT_ASSOCIATED;
                            break;
                        default:
                            supplicantEvent = -1;
                    }
                    if (supplicantEvent != -1) {
                        WifiConfiguration config = mWifiConfigManager.getConfiguredNetwork(
                                    stateChangeResult.networkId);
                        try {
                            logEventIfManagedNetwork(config, supplicantEvent,
                                    MacAddress.fromString(stateChangeResult.bssid), "");
                        } catch (IllegalArgumentException e) {
                            Log.i(TAG, "Invalid bssid received for state change event");
                        }
                    }
                    if (state == SupplicantState.DISCONNECTED && mNetworkAgent != null) {
                        if (mVerboseLoggingEnabled) {
                            log("Missed CTRL-EVENT-DISCONNECTED, disconnect");
                        }
                        handleNetworkDisconnect(false,
                                WIFI_DISCONNECT_REPORTED__FAILURE_CODE__SUPPLICANT_DISCONNECTED);
                        transitionTo(mDisconnectedState);
                    }
                    if (state == SupplicantState.COMPLETED) {
                        mWifiScoreReport.noteIpCheck();
                    }
                    break;
                }
                case WifiMonitor.ASSOCIATED_BSSID_EVENT: {
                    // This is where we can confirm the connection BSSID. Use it to find the
                    // right ScanDetail to populate metrics.
                    String someBssid = (String) message.obj;
                    if (someBssid != null) {
                        // Get the ScanDetail associated with this BSSID.
                        ScanDetailCache scanDetailCache =
                                mWifiConfigManager.getScanDetailCacheForNetwork(mTargetNetworkId);
                        if (scanDetailCache != null) {
                            mWifiMetrics.setConnectionScanDetail(mInterfaceName,
                                    scanDetailCache.getScanDetail(someBssid));
                        }
                        // Update last associated BSSID
                        mLastBssid = someBssid;
                    }
                    handleStatus = NOT_HANDLED;
                    break;
                }
                case WifiMonitor.NETWORK_CONNECTION_EVENT: {
                    if (mVerboseLoggingEnabled) log("Network connection established");
                    NetworkConnectionEventInfo connectionInfo =
                            (NetworkConnectionEventInfo) message.obj;
                    mLastNetworkId = connectionInfo.networkId;
                    mSentHLPs = connectionInfo.isFilsConnection;
                    if (mSentHLPs) mWifiMetrics.incrementL2ConnectionThroughFilsAuthCount();
                    mWifiConfigManager.clearRecentFailureReason(mLastNetworkId);
                    mLastBssid = connectionInfo.bssid;
                    // TODO: This check should not be needed after ClientModeImpl refactor.
                    // Currently, the last connected network configuration is left in
                    // wpa_supplicant, this may result in wpa_supplicant initiating connection
                    // to it after a config store reload. Hence the old network Id lookups may not
                    // work, so disconnect the network and let network selector reselect a new
                    // network.
                    WifiConfiguration config = getConnectedWifiConfigurationInternal();
                    if (config == null) {
                        logw("Connected to unknown networkId " + mLastNetworkId
                                + ", disconnecting...");
                        mFrameworkDisconnectReasonOverride = WifiStatsLog.WIFI_DISCONNECT_REPORTED__FAILURE_CODE__DISCONNECT_UNKNOWN_NETWORK;
                        sendMessage(CMD_DISCONNECT, StaEvent.DISCONNECT_UNKNOWN_NETWORK);
                        break;
                    }
                    handleNetworkConnectionEventInfo(config, connectionInfo);
                    mWifiInfo.setMacAddress(mWifiNative.getMacAddress(mInterfaceName));

                    ScanDetailCache scanDetailCache =
                            mWifiConfigManager.getScanDetailCacheForNetwork(config.networkId);
                    ScanResult scanResult = null;
                    if (scanDetailCache != null && mLastBssid != null) {
                        scanResult = scanDetailCache.getScanResult(mLastBssid);
                        if (scanResult != null) {
                            mWifiInfo.setFrequency(scanResult.frequency);
                        }
                    }

                    // We need to get the updated pseudonym from supplicant for EAP-SIM/AKA/AKA'
                    if (config.enterpriseConfig != null
                            && config.enterpriseConfig.isAuthenticationSimBased()) {
                        if (mWifiCarrierInfoManager.isOobPseudonymFeatureEnabled(
                                config.carrierId)) {
                            if (mVerboseLoggingEnabled) {
                                logd("add PseudonymUpdatingListener");
                            }
                            mWifiPseudonymManager.registerPseudonymUpdatingListener(
                                    mPseudonymUpdatingListener);
                        }
                        mLastSubId = mWifiCarrierInfoManager.getBestMatchSubscriptionId(config);
                        mLastSimBasedConnectionCarrierName =
                                mWifiCarrierInfoManager.getCarrierNameForSubId(mLastSubId);
                        String anonymousIdentity =
                                mWifiNative.getEapAnonymousIdentity(mInterfaceName);
                        if (!TextUtils.isEmpty(anonymousIdentity)
                                && !WifiCarrierInfoManager
                                .isAnonymousAtRealmIdentity(anonymousIdentity)) {
                            String decoratedPseudonym = mWifiCarrierInfoManager
                                    .decoratePseudonymWith3GppRealm(config,
                                            anonymousIdentity);
                            boolean updateToNativeService = false;
                            if (decoratedPseudonym != null
                                    && !decoratedPseudonym.equals(anonymousIdentity)) {
                                anonymousIdentity = decoratedPseudonym;
                                // propagate to the supplicant to avoid using
                                // the original anonymous identity for firmware
                                // roaming.
                                if (mVerboseLoggingEnabled) {
                                    log("Update decorated pseudonym: " + anonymousIdentity);
                                }
                                updateToNativeService = true;
                            }
                            // This needs native change to avoid disconnecting from the current
                            // network. Consider that older releases might not be able to have
                            // the vendor partition updated, only update to native service on T
                            // or newer.
                            if (mWifiNative.isSupplicantAidlServiceVersionAtLeast(1)) {
                                mWifiNative.setEapAnonymousIdentity(mInterfaceName,
                                        anonymousIdentity, updateToNativeService);
                            }
                            if (mVerboseLoggingEnabled) {
                                log("EAP Pseudonym: " + anonymousIdentity);
                            }
                            // Save the pseudonym only if it is a real one
                            config.enterpriseConfig.setAnonymousIdentity(anonymousIdentity);
                            int carrierId = config.carrierId;
                            if (mWifiCarrierInfoManager.isOobPseudonymFeatureEnabled(
                                    carrierId)) {
                                mWifiPseudonymManager.setInBandPseudonym(carrierId,
                                        anonymousIdentity);
                            }
                        } else {
                            // Clear any stored pseudonyms
                            config.enterpriseConfig.setAnonymousIdentity(null);
                        }
                        mWifiConfigManager.addOrUpdateNetwork(config, Process.WIFI_UID);
                        if (config.isPasspoint()) {
                            mPasspointManager.setAnonymousIdentity(config);
                        } else if (config.fromWifiNetworkSuggestion) {
                            mWifiNetworkSuggestionsManager.setAnonymousIdentity(config);
                        }
                    }
                    // When connecting to Passpoint, ask for the Venue URL
                    if (config.isPasspoint()) {
                        mTermsAndConditionsUrl = null;
                        if (scanResult == null && mLastBssid != null) {
                            // The cached scan result of connected network would be null at the
                            // first connection, try to check full scan result list again to look up
                            // matched scan result associated to the current SSID and BSSID.
                            scanResult = mScanRequestProxy.getScanResult(mLastBssid);
                        }
                        if (scanResult != null) {
                            mPasspointManager.requestVenueUrlAnqpElement(scanResult);
                        }
                    }
                    mWifiInfo.setNetworkKey(config.getNetworkKeyFromSecurityType(
                            mWifiInfo.getCurrentSecurityType()));
                    if (mApplicationQosPolicyRequestHandler.isFeatureEnabled()) {
                        mApplicationQosPolicyRequestHandler.queueAllPoliciesOnIface(
                                mInterfaceName, mostRecentConnectionSupports11ax());
                    }
                    updateLayer2Information();
                    updateCurrentConnectionInfo();
                    transitionTo(mL3ProvisioningState);
                    break;
                }
                case CMD_UPDATE_OOB_PSEUDONYM: {
                    WifiConfiguration config = getConnectedWifiConfigurationInternal();
                    if (config == null) {
                        log("OOB pseudonym is updated, but no valid connected network.");
                        break;
                    }
                    int updatingCarrierId = message.arg1;
                    if (config.enterpriseConfig == null
                            || !config.enterpriseConfig.isAuthenticationSimBased()
                            || !mWifiCarrierInfoManager.isOobPseudonymFeatureEnabled(
                                    config.carrierId)
                            || updatingCarrierId != config.carrierId) {
                        log("OOB pseudonym is not applied.");
                        break;
                    }
                    if (mWifiNative.isSupplicantAidlServiceVersionAtLeast(1)) {
                        log("send OOB pseudonym to supplicant");
                        String pseudonym = (String) message.obj;
                        mWifiNative.setEapAnonymousIdentity(mInterfaceName,
                                mWifiCarrierInfoManager.decoratePseudonymWith3GppRealm(
                                        config, pseudonym),
                                /*updateToNativeService=*/ true);
                    }
                    break;
                }
                case WifiMonitor.SUP_REQUEST_SIM_AUTH: {
                    logd("Received SUP_REQUEST_SIM_AUTH");
                    SimAuthRequestData requestData = (SimAuthRequestData) message.obj;
                    if (requestData != null) {
                        if (requestData.protocol == WifiEnterpriseConfig.Eap.SIM) {
                            handleGsmAuthRequest(requestData);
                        } else if (requestData.protocol == WifiEnterpriseConfig.Eap.AKA
                                || requestData.protocol == WifiEnterpriseConfig.Eap.AKA_PRIME) {
                            handle3GAuthRequest(requestData);
                        }
                    } else {
                        loge("Invalid SIM auth request");
                    }
                    break;
                }
                case WifiMonitor.NETWORK_DISCONNECTION_EVENT: {
                    DisconnectEventInfo eventInfo = (DisconnectEventInfo) message.obj;
                    if (mVerboseLoggingEnabled) {
                        log("ConnectingOrConnectedState: Network disconnection " + eventInfo);
                    }
                    if (eventInfo.reasonCode
                            == StaIfaceReasonCode.FOURWAY_HANDSHAKE_TIMEOUT) {
                        String bssid = !isValidBssid(eventInfo.bssid)
                                ? mTargetBssid : eventInfo.bssid;
                        mWifiLastResortWatchdog.noteConnectionFailureAndTriggerIfNeeded(
                                getConnectingSsidInternal(), bssid,
                                WifiLastResortWatchdog.FAILURE_CODE_AUTHENTICATION,
                                isConnected());
                    }
                    WifiConfiguration config = getConnectedWifiConfigurationInternal();
                    if (config == null) {
                        config = getConnectingWifiConfigurationInternal();
                    }
                    clearNetworkCachedDataIfNeeded(config, eventInfo.reasonCode);
                    try {
                        logEventIfManagedNetwork(config,
                                SupplicantStaIfaceHal.SUPPLICANT_EVENT_DISCONNECTED,
                                MacAddress.fromString(eventInfo.bssid),
                                "reason=" + StaIfaceReasonCode.toString(eventInfo.reasonCode));
                    } catch (IllegalArgumentException e) {
                        Log.e(TAG, "Invalid bssid received for disconnection event");
                    }
                    boolean newConnectionInProgress = isNewConnectionInProgress(eventInfo.ssid);
                    if (!newConnectionInProgress) {
                        int level2FailureReason = eventInfo.locallyGenerated
                                ? WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN :
                                WifiMetricsProto.ConnectionEvent.DISCONNECTION_NON_LOCAL;
                        if (!eventInfo.locallyGenerated) {
                            mWifiScoreCard.noteNonlocalDisconnect(
                                    mInterfaceName, eventInfo.reasonCode);
                        }
                        reportConnectionAttemptEnd(
                                WifiMetrics.ConnectionEvent.FAILURE_NETWORK_DISCONNECTION,
                                WifiMetricsProto.ConnectionEvent.HLF_NONE, level2FailureReason,
                                eventInfo.reasonCode);
                    }
                    handleNetworkDisconnect(newConnectionInProgress, eventInfo.reasonCode);
                    if (!newConnectionInProgress) {
                        transitionTo(mDisconnectedState);
                    }
                    mTermsAndConditionsUrl = null;
                    break;
                }
                case WifiMonitor.TARGET_BSSID_EVENT: {
                    // Trying to associate to this BSSID
                    if (message.obj != null) {
                        mTargetBssid = (String) message.obj;
                    }
                    break;
                }
                case WifiMonitor.AUXILIARY_SUPPLICANT_EVENT: {
                    SupplicantEventInfo eventInfo = (SupplicantEventInfo) message.obj;
                    logEventIfManagedNetwork(getConnectingWifiConfigurationInternal(),
                            eventInfo.eventCode, eventInfo.bssid,
                            eventInfo.reasonString);
                    break;
                }
                case CMD_DISCONNECT: {
                    mWifiMetrics.logStaEvent(mInterfaceName, StaEvent.TYPE_FRAMEWORK_DISCONNECT,
                            message.arg1);
                    mWifiNative.disconnect(mInterfaceName);
                    break;
                }
                case CMD_PRE_DHCP_ACTION:
                case CMD_PRE_DHCP_ACTION_COMPLETE:
                case CMD_POST_DHCP_ACTION:
                case CMD_IPV4_PROVISIONING_SUCCESS:
                case CMD_IP_CONFIGURATION_SUCCESSFUL:
                case CMD_IPV4_PROVISIONING_FAILURE: {
                    handleStatus = handleL3MessagesWhenNotConnected(message);
                    break;
                }
                case WifiMonitor.TRANSITION_DISABLE_INDICATION: {
                    log("Received TRANSITION_DISABLE_INDICATION: networkId=" + message.arg1
                            + ", indication=" + message.arg2 + ", bssid=" + mLastBssid);
                    if (isValidTransitionDisableIndicationSource(mLastBssid, message.arg2)) {
                        mWifiConfigManager.updateNetworkTransitionDisable(message.arg1,
                                message.arg2);
                    } else {
                        Log.w(getTag(), "Drop TRANSITION_DISABLE_INDICATION event"
                                + " from an invalid source.");
                    }
                    break;
                }
                case WifiMonitor.QOS_POLICY_RESET_EVENT: {
                    if (SdkLevel.isAtLeastT() && mNetworkAgent != null
                            && mWifiNative.isQosPolicyFeatureEnabled()) {
                        mNetworkAgent.sendRemoveAllDscpPolicies();
                    }
                    break;
                }
                case WifiMonitor.QOS_POLICY_REQUEST_EVENT: {
                    if (SdkLevel.isAtLeastT() && mWifiNative.isQosPolicyFeatureEnabled()) {
                        mQosPolicyRequestHandler.queueQosPolicyRequest(
                                message.arg1, (List<QosPolicyRequest>) message.obj);
                    }
                    break;
                }
                case CMD_REJECT_EAP_INSECURE_CONNECTION: {
                    log("Received CMD_REJECT_EAP_INSECURE_CONNECTION event");
                    boolean disconnectRequired = message.arg2 == 1;

                    // TOFU connections are not established until the user approves the certificate.
                    // If TOFU is not supported and the network is already connected, this will
                    // disconnect the network.
                    if (disconnectRequired) {
                        mFrameworkDisconnectReasonOverride = WifiStatsLog.WIFI_DISCONNECT_REPORTED__FAILURE_CODE__DISCONNECT_NETWORK_UNTRUSTED;
                        sendMessage(CMD_DISCONNECT, StaEvent.DISCONNECT_NETWORK_UNTRUSTED);
                    }
                    break;
                }
                default: {
                    handleStatus = NOT_HANDLED;
                    break;
                }
            }
            if (handleStatus == HANDLED) {
                logStateAndMessage(message, this);
            }
            return handleStatus;
        }

        private boolean isValidTransitionDisableIndicationSource(String bssid,
                @WifiMonitor.TransitionDisableIndication int indicationBit) {
            ScanResult result = mScanRequestProxy.getScanResult(mLastBssid);
            if (null == result) return false;

            // SAE TDI should only come from a PSK/SAE BSS or a SAE BSS.
            if (0 != (indicationBit & WifiMonitor.TDI_USE_WPA3_PERSONAL)) {
                return ScanResultUtil.isScanResultForSaeNetwork(result)
                        || ScanResultUtil.isScanResultForPskSaeTransitionNetwork(result);
            }
            // SAE_PK TDI should only come from a SAE BSS.
            if (0 != (indicationBit & WifiMonitor.TDI_USE_SAE_PK)) {
                return ScanResultUtil.isScanResultForSaeNetwork(result);
            }
            // WPA3 Enterprise TDI should only come from a WPA2/WPA3 Enterprise
            // BSS or a WPA3 Enterprise BSS.
            if (0 != (indicationBit & WifiMonitor.TDI_USE_WPA3_ENTERPRISE)) {
                return ScanResultUtil.isScanResultForWpa3EnterpriseOnlyNetwork(result)
                        || ScanResultUtil.isScanResultForWpa3EnterpriseTransitionNetwork(result);
            }
            // OWE TDI should only come from an OPEN/OWE BSS or an OWE BSS.
            if (0 != (indicationBit & WifiMonitor.TDI_USE_ENHANCED_OPEN)) {
                return ScanResultUtil.isScanResultForOweNetwork(result)
                        || ScanResultUtil.isScanResultForOweTransitionNetwork(result);
            }
            return false;
        }
    }

    class L2ConnectingState extends RunnerState {
        L2ConnectingState(int threshold) {
            super(threshold, mWifiInjector.getWifiHandlerLocalLog());
        }

        @Override
        public void enterImpl() {
            if (mVerboseLoggingEnabled) Log.v(getTag(), "Entering L2ConnectingState");
            // Make sure we connect: we enter this state prior to connecting to a new
            // network. In some cases supplicant ignores the connect requests (it might not
            // find the target SSID in its cache), Therefore we end up stuck that state, hence the
            // need for the watchdog.
            mConnectingWatchdogCount++;
            logd("Start Connecting Watchdog " + mConnectingWatchdogCount);
            sendMessageDelayed(obtainMessage(CMD_CONNECTING_WATCHDOG_TIMER,
                    mConnectingWatchdogCount, 0), CONNECTING_WATCHDOG_TIMEOUT_MS);
        }

        @Override
        public void exitImpl() {
            if (mVerboseLoggingEnabled) Log.v(getTag(), "Exiting L2ConnectingState");
            // Cancel any pending CMD_CONNECTING_WATCHDOG_TIMER since this is only valid in
            // L2ConnectingState anyway.
            removeMessages(CMD_CONNECTING_WATCHDOG_TIMER);
        }

        @Override
        public String getMessageLogRec(int what) {
            return ClientModeImpl.class.getSimpleName() + "."
                    + L2ConnectingState.class.getSimpleName() + "." + getWhatToString(what);
        }

        @Override
        public boolean processMessageImpl(Message message) {
            boolean handleStatus = HANDLED;
            switch (message.what) {
                case WifiMonitor.NETWORK_NOT_FOUND_EVENT:
                    mNetworkNotFoundEventCount++;
                    String networkName = (String) message.obj;
                    if (networkName != null && !networkName.equals(getConnectingSsidInternal())) {
                        loge("Network not found event received, network: " + networkName
                                + " which is not the target network: "
                                + getConnectingSsidInternal());
                        break;
                    }
                    Log.d(getTag(), "Network not found event received: network: " + networkName);
                    if (mNetworkNotFoundEventCount
                            >= mWifiGlobals.getNetworkNotFoundEventThreshold()
                            && mTargetWifiConfiguration != null
                            && mTargetWifiConfiguration.SSID != null
                            && mTargetWifiConfiguration.SSID.equals(networkName)) {
                        stopIpClient();
                        mWifiConfigManager.updateNetworkSelectionStatus(
                                mTargetWifiConfiguration.networkId,
                                WifiConfiguration.NetworkSelectionStatus
                                        .DISABLED_NETWORK_NOT_FOUND);
                        if (SdkLevel.isAtLeastS()) {
                            mWifiConfigManager.setRecentFailureAssociationStatus(
                                    mTargetWifiConfiguration.networkId,
                                    WifiConfiguration.RECENT_FAILURE_NETWORK_NOT_FOUND);
                        }
                        reportConnectionAttemptEnd(
                                WifiMetrics.ConnectionEvent.FAILURE_NETWORK_NOT_FOUND,
                                WifiMetricsProto.ConnectionEvent.HLF_NONE,
                                WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, 0);
                        handleNetworkDisconnect(false,
                                WifiStatsLog.WIFI_DISCONNECT_REPORTED__FAILURE_CODE__UNSPECIFIED);
                        // TODO(b/302728081): remove the code once the issue is resolved.
                        if (mDeviceConfigFacade.isAbnormalDisconnectionBugreportEnabled()
                                && mTargetWifiConfiguration.enterpriseConfig != null
                                && mTargetWifiConfiguration.enterpriseConfig
                                        .isAuthenticationSimBased()
                                && mWifiCarrierInfoManager.isOobPseudonymFeatureEnabled(
                                        mTargetWifiConfiguration.carrierId)) {
                            String bugTitle = "Wi-Fi BugReport: suspicious NETWORK_NOT_FOUND";
                            String bugDetail = "Detect abnormal NETWORK_NOT_FOUND error";
                            mWifiDiagnostics.takeBugReport(bugTitle, bugDetail);
                        }
                        transitionTo(mDisconnectedState); // End of connection attempt.
                    }
                    break;
                case WifiMonitor.ASSOCIATION_REJECTION_EVENT: {
                    AssocRejectEventInfo assocRejectEventInfo = (AssocRejectEventInfo) message.obj;
                    log("L2ConnectingState: Association rejection " + assocRejectEventInfo);
                    if (!assocRejectEventInfo.ssid.equals(getConnectingSsidInternal())) {
                        loge("Association rejection event received on not target network");
                        break;
                    }
                    stopIpClient();
                    mWifiDiagnostics.triggerBugReportDataCapture(
                            WifiDiagnostics.REPORT_REASON_ASSOC_FAILURE);
                    String bssid = assocRejectEventInfo.bssid;
                    boolean timedOut = assocRejectEventInfo.timedOut;
                    int statusCode = assocRejectEventInfo.statusCode;
                    if (!isValidBssid(bssid)) {
                        // If BSSID is null, use the target roam BSSID
                        bssid = mTargetBssid;
                    } else if (SUPPLICANT_BSSID_ANY.equals(mTargetBssid)) {
                        // This is needed by WifiBlocklistMonitor to block continuously
                        // failing BSSIDs. Need to set here because mTargetBssid is currently
                        // not being set until association success.
                        mTargetBssid = bssid;
                    }
                    if (!isSecondaryInternet()) {
                        mWifiConfigManager.updateNetworkSelectionStatus(mTargetNetworkId,
                                WifiConfiguration.NetworkSelectionStatus
                                        .DISABLED_ASSOCIATION_REJECTION);
                    }
                    setAssociationRejectionStatusInConfig(mTargetNetworkId, assocRejectEventInfo);
                    int level2FailureReason =
                            WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN;
                    if (statusCode == StaIfaceStatusCode.AP_UNABLE_TO_HANDLE_NEW_STA
                            || statusCode == StaIfaceStatusCode.ASSOC_REJECTED_TEMPORARILY
                            || statusCode == StaIfaceStatusCode.DENIED_INSUFFICIENT_BANDWIDTH) {
                        level2FailureReason = WifiMetricsProto.ConnectionEvent
                                .ASSOCIATION_REJECTION_AP_UNABLE_TO_HANDLE_NEW_STA;
                    }

                    // If rejection occurred while Metrics is tracking a ConnectionEvent, end it.
                    reportConnectionAttemptEnd(
                            timedOut
                                    ? WifiMetrics.ConnectionEvent.FAILURE_ASSOCIATION_TIMED_OUT
                                    : WifiMetrics.ConnectionEvent.FAILURE_ASSOCIATION_REJECTION,
                            WifiMetricsProto.ConnectionEvent.HLF_NONE,
                            level2FailureReason, timedOut ? 0 : statusCode);

                    if (level2FailureReason != WifiMetricsProto.ConnectionEvent
                            .ASSOCIATION_REJECTION_AP_UNABLE_TO_HANDLE_NEW_STA
                            && !isSecondaryInternet()) {
                        mWifiLastResortWatchdog.noteConnectionFailureAndTriggerIfNeeded(
                                getConnectingSsidInternal(), bssid,
                                WifiLastResortWatchdog.FAILURE_CODE_ASSOCIATION,
                                isConnected());
                    }
                    handleNetworkDisconnect(false,
                            WifiStatsLog.WIFI_DISCONNECT_REPORTED__FAILURE_CODE__UNSPECIFIED);
                    transitionTo(mDisconnectedState); // End of connection attempt.
                    break;
                }
                case WifiMonitor.AUTHENTICATION_FAILURE_EVENT: {
                    AuthenticationFailureEventInfo authenticationFailureEventInfo =
                            (AuthenticationFailureEventInfo) message.obj;
                    if (!TextUtils.equals(authenticationFailureEventInfo.ssid,
                            getConnectingSsidInternal())) {
                        logw("Authentication failure event received on not target network");
                        break;
                    }
                    stopIpClient();
                    mWifiDiagnostics.triggerBugReportDataCapture(
                            WifiDiagnostics.REPORT_REASON_AUTH_FAILURE);
                    int disableReason = WifiConfiguration.NetworkSelectionStatus
                            .DISABLED_AUTHENTICATION_FAILURE;
                    int reasonCode = authenticationFailureEventInfo.reasonCode;
                    int errorCode = authenticationFailureEventInfo.errorCode;
                    log("L2ConnectingState: Authentication failure "
                            + " reason=" + reasonCode + " error=" + errorCode);
                    WifiConfiguration targetedNetwork =
                            mWifiConfigManager.getConfiguredNetwork(mTargetNetworkId);
                    // Check if this is a permanent wrong password failure.
                    if (isPermanentWrongPasswordFailure(mTargetNetworkId, reasonCode)) {
                        disableReason = WifiConfiguration.NetworkSelectionStatus
                                .DISABLED_BY_WRONG_PASSWORD;
                        // For primary role, send error notification except for local only network,
                        // for secondary role, send only for secondary internet.
                        final boolean isForLocalOnly = isRequestedForLocalOnly(
                                targetedNetwork, mTargetBssid) || isLocalOnly();
                        final boolean needNotifyError = isPrimary() ? !isForLocalOnly
                                : isSecondaryInternet();
                        if (targetedNetwork != null && needNotifyError) {
                            mWrongPasswordNotifier.onWrongPasswordError(targetedNetwork);
                        }
                    } else if (reasonCode == WifiManager.ERROR_AUTH_FAILURE_EAP_FAILURE) {
                        logEventIfManagedNetwork(targetedNetwork,
                                SupplicantStaIfaceHal.SUPPLICANT_EVENT_EAP_FAILURE,
                                authenticationFailureEventInfo.bssid, "error=" + errorCode);
                        if (targetedNetwork != null && targetedNetwork.enterpriseConfig != null
                                && targetedNetwork.enterpriseConfig.isAuthenticationSimBased()) {
                            // only show EAP failure notification if primary
                            WifiBlocklistMonitor.CarrierSpecificEapFailureConfig eapFailureConfig =
                                    mEapFailureNotifier.onEapFailure(errorCode, targetedNetwork,
                                            isPrimary());
                            if (eapFailureConfig != null) {
                                disableReason = WifiConfiguration.NetworkSelectionStatus
                                    .DISABLED_AUTHENTICATION_PRIVATE_EAP_ERROR;
                                mWifiBlocklistMonitor.loadCarrierConfigsForDisableReasonInfos(
                                        eapFailureConfig);
                            }
                        }
                        handleEapAuthFailure(mTargetNetworkId, errorCode);
                        if (errorCode == WifiNative.EAP_SIM_NOT_SUBSCRIBED) {
                            disableReason = WifiConfiguration.NetworkSelectionStatus
                                    .DISABLED_AUTHENTICATION_NO_SUBSCRIPTION;
                        }
                    }
                    mWifiConfigManager.updateNetworkSelectionStatus(
                            mTargetNetworkId, disableReason);
                    mWifiConfigManager.clearRecentFailureReason(mTargetNetworkId);

                    //If failure occurred while Metrics is tracking a ConnnectionEvent, end it.
                    int level2FailureReason;
                    switch (reasonCode) {
                        case WifiManager.ERROR_AUTH_FAILURE_NONE:
                            level2FailureReason =
                                    WifiMetricsProto.ConnectionEvent.AUTH_FAILURE_NONE;
                            break;
                        case WifiManager.ERROR_AUTH_FAILURE_TIMEOUT:
                            level2FailureReason =
                                    WifiMetricsProto.ConnectionEvent.AUTH_FAILURE_TIMEOUT;
                            break;
                        case WifiManager.ERROR_AUTH_FAILURE_WRONG_PSWD:
                            level2FailureReason =
                                    WifiMetricsProto.ConnectionEvent.AUTH_FAILURE_WRONG_PSWD;
                            break;
                        case WifiManager.ERROR_AUTH_FAILURE_EAP_FAILURE:
                            level2FailureReason =
                                    WifiMetricsProto.ConnectionEvent.AUTH_FAILURE_EAP_FAILURE;
                            break;
                        default:
                            level2FailureReason =
                                    WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN;
                            break;
                    }
                    reportConnectionAttemptEnd(
                            WifiMetrics.ConnectionEvent.FAILURE_AUTHENTICATION_FAILURE,
                            WifiMetricsProto.ConnectionEvent.HLF_NONE,
                            level2FailureReason, errorCode);
                    if (reasonCode != WifiManager.ERROR_AUTH_FAILURE_WRONG_PSWD && reasonCode
                            != WifiManager.ERROR_AUTH_FAILURE_EAP_FAILURE) {
                        mWifiLastResortWatchdog.noteConnectionFailureAndTriggerIfNeeded(
                                getConnectingSsidInternal(),
                                (mLastBssid == null) ? mTargetBssid : mLastBssid,
                                WifiLastResortWatchdog.FAILURE_CODE_AUTHENTICATION,
                                isConnected());
                    }
                    handleNetworkDisconnect(false,
                            WifiStatsLog.WIFI_DISCONNECT_REPORTED__FAILURE_CODE__UNSPECIFIED);
                    transitionTo(mDisconnectedState); // End of connection attempt.
                    break;
                }
                case WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT: {
                    StateChangeResult stateChangeResult = (StateChangeResult) message.obj;
                    if (SupplicantState.isConnecting(stateChangeResult.state)) {
                        WifiConfiguration config = mWifiConfigManager.getConfiguredNetwork(
                                stateChangeResult.networkId);
                        // Update Passpoint information before setNetworkDetailedState as
                        // WifiTracker monitors NETWORK_STATE_CHANGED_ACTION to update UI.
                        mWifiInfo.setFQDN(null);
                        mWifiInfo.setPasspointUniqueId(null);
                        mWifiInfo.setOsuAp(false);
                        mWifiInfo.setProviderFriendlyName(null);
                        if (config != null && (config.isPasspoint() || config.osu)) {
                            if (config.isPasspoint()) {
                                mWifiInfo.setFQDN(config.FQDN);
                                mWifiInfo.setPasspointUniqueId(config.getPasspointUniqueId());
                            } else {
                                mWifiInfo.setOsuAp(true);
                            }
                            mWifiInfo.setProviderFriendlyName(config.providerFriendlyName);
                            mWifiInfo.setNetworkKey(
                                    config.getNetworkKeyFromSecurityType(
                                            mWifiInfo.getCurrentSecurityType()));
                        }
                        updateCurrentConnectionInfo();
                    }
                    sendNetworkChangeBroadcast(
                            WifiInfo.getDetailedStateOf(stateChangeResult.state));
                    // Let the parent state handle the rest of the state changed.
                    handleStatus = NOT_HANDLED;
                    break;
                }
                case WifiMonitor.SUP_REQUEST_IDENTITY: {
                    int netId = message.arg2;
                    boolean identitySent = false;
                    // For SIM & AKA/AKA' EAP method Only, get identity from ICC
                    if (mTargetWifiConfiguration != null
                            && mTargetWifiConfiguration.networkId == netId
                            && mTargetWifiConfiguration.enterpriseConfig != null
                            && mTargetWifiConfiguration.enterpriseConfig
                            .isAuthenticationSimBased()) {
                        // Pair<identity, encrypted identity>
                        Pair<String, String> identityPair = mWifiCarrierInfoManager
                                .getSimIdentity(mTargetWifiConfiguration);
                        if (identityPair != null && identityPair.first != null) {
                            Log.i(getTag(), "SUP_REQUEST_IDENTITY: identityPair=["
                                    + ((identityPair.first.length() >= 7)
                                    ? identityPair.first.substring(0, 7 /* Prefix+PLMN ID */)
                                    + "****"
                                    : identityPair.first) + ", "
                                    + (!TextUtils.isEmpty(identityPair.second) ? identityPair.second
                                    : "<NONE>") + "]");
                            mWifiNative.simIdentityResponse(mInterfaceName, identityPair.first,
                                    identityPair.second);
                            identitySent = true;
                        } else {
                            Log.e(getTag(), "Unable to retrieve identity from Telephony");
                        }
                    }

                    if (!identitySent) {
                        // Supplicant lacks credentials to connect to that network, hence block list
                        String ssid = (String) message.obj;
                        if (mTargetWifiConfiguration != null && ssid != null
                                && mTargetWifiConfiguration.SSID != null
                                && mTargetWifiConfiguration.SSID.equals("\"" + ssid + "\"")) {
                            mWifiConfigManager.updateNetworkSelectionStatus(
                                    mTargetWifiConfiguration.networkId,
                                    WifiConfiguration.NetworkSelectionStatus
                                            .DISABLED_AUTHENTICATION_NO_CREDENTIALS);
                        }
                        mWifiMetrics.logStaEvent(mInterfaceName, StaEvent.TYPE_FRAMEWORK_DISCONNECT,
                                StaEvent.DISCONNECT_GENERIC);
                        mWifiNative.disconnect(mInterfaceName);
                    }
                    break;
                }
                case CMD_CONNECTING_WATCHDOG_TIMER: {
                    if (mConnectingWatchdogCount == message.arg1) {
                        if (mVerboseLoggingEnabled) log("Connecting watchdog! -> disconnect");
                        reportConnectionAttemptEnd(
                                WifiMetrics.ConnectionEvent.FAILURE_NO_RESPONSE,
                                WifiMetricsProto.ConnectionEvent.HLF_NONE,
                                WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, 0);
                        handleNetworkDisconnect(false,
                                WifiStatsLog.WIFI_DISCONNECT_REPORTED__FAILURE_CODE__CONNECTING_WATCHDOG_TIMER);
                        transitionTo(mDisconnectedState);
                    }
                    break;
                }
                case WifiMonitor.NETWORK_CONNECTION_EVENT: {
                    NetworkConnectionEventInfo connectionInfo =
                            (NetworkConnectionEventInfo) message.obj;
                    String quotedOrHexConnectingSsid = getConnectingSsidInternal();
                    String quotedOrHexConnectedSsid = connectionInfo.wifiSsid.toString();
                    if (quotedOrHexConnectingSsid != null
                            && !quotedOrHexConnectingSsid.equals(quotedOrHexConnectedSsid)) {
                        // possibly a NETWORK_CONNECTION_EVENT for a successful roam on the previous
                        // network while connecting to a new network, ignore it.
                        Log.d(TAG, "Connecting to ssid=" + quotedOrHexConnectingSsid + ", but got "
                                + "NETWORK_CONNECTION_EVENT for ssid=" + quotedOrHexConnectedSsid
                                + ", ignoring");
                        break;
                    }
                    handleStatus = NOT_HANDLED;
                    break;
                }
                case WifiMonitor.TOFU_CERTIFICATE_EVENT: {
                    if (null == mTargetWifiConfiguration) break;
                    int networkId = message.arg1;
                    final int certificateDepth = message.arg2;
                    final CertificateEventInfo eventInfo = (CertificateEventInfo) message.obj;
                    if (!mInsecureEapNetworkHandler.addPendingCertificate(
                            networkId, certificateDepth, eventInfo)) {
                        Log.d(TAG, "Cannot set pending cert.");
                    }
                    // Launch user approval upon receiving the server certificate and disconnect
                    if (certificateDepth == 0 && !mLeafCertSent && mInsecureEapNetworkHandler
                            .startUserApprovalIfNecessary(mIsUserSelected)) {
                        // In the TOFU flow, the user approval dialog is now displayed and the
                        // network remains disconnected and disabled until it is approved.
                        mFrameworkDisconnectReasonOverride = WifiStatsLog.WIFI_DISCONNECT_REPORTED__FAILURE_CODE__DISCONNECT_NETWORK_UNTRUSTED;
                        sendMessage(CMD_DISCONNECT, StaEvent.DISCONNECT_NETWORK_UNTRUSTED);
                        mLeafCertSent = true;
                    }
                    break;
                }
                default: {
                    handleStatus = NOT_HANDLED;
                    break;
                }
            }
            if (handleStatus == HANDLED) {
                logStateAndMessage(message, this);
            }
            return handleStatus;
        }
    }

    class L2ConnectedState extends RunnerState {
        L2ConnectedState(int threshold) {
            super(threshold, mWifiInjector.getWifiHandlerLocalLog());
        }

        @Override
        public void enterImpl() {
            final WifiConfiguration config = getConnectedWifiConfigurationInternal();
            if (config == null) {
                logw("Connected to a network that's already been removed " + mLastNetworkId
                        + ", disconnecting...");
                sendMessage(CMD_DISCONNECT, StaEvent.DISCONNECT_UNKNOWN_NETWORK);
                return;
            }

            mRssiPollToken++;
            if (mEnableRssiPolling) {
                if (isPrimary()) {
                    mLinkProbeManager.resetOnNewConnection();
                }
                sendMessage(CMD_RSSI_POLL, mRssiPollToken, 0);
            }
            sendNetworkChangeBroadcast(DetailedState.CONNECTING);
            // If this network was explicitly selected by the user, evaluate whether to inform
            // ConnectivityService of that fact so the system can treat it appropriately.
            final NetworkAgentConfig naConfig = getNetworkAgentConfigInternal(config);
            final NetworkCapabilities nc = getCapabilities(
                    getConnectedWifiConfigurationInternal(), getConnectedBssidInternal());
            // This should never happen.
            if (mNetworkAgent != null) {
                Log.wtf(getTag(), "mNetworkAgent is not null: " + mNetworkAgent);
                mNetworkAgent.unregister();
            }
            mNetworkAgent = mWifiInjector.makeWifiNetworkAgent(nc, mLinkProperties, naConfig,
                    mNetworkFactory.getProvider(), new WifiNetworkAgentCallback());
            mWifiScoreReport.setNetworkAgent(mNetworkAgent);
            if (SdkLevel.isAtLeastT()) {
                mQosPolicyRequestHandler.setNetworkAgent(mNetworkAgent);
            }

            // We must clear the config BSSID, as the wifi chipset may decide to roam
            // from this point on and having the BSSID specified in the network block would
            // cause the roam to faile and the device to disconnect
            clearTargetBssid("L2ConnectedState");
            mWifiMetrics.setWifiState(mInterfaceName, WifiMetricsProto.WifiLog.WIFI_ASSOCIATED);
            mWifiScoreCard.noteNetworkAgentCreated(mWifiInfo,
                    mNetworkAgent.getNetwork().getNetId());
            mWifiBlocklistMonitor.handleBssidConnectionSuccess(mLastBssid, mWifiInfo.getSSID());
            // too many places to record connection failure with too many failure reasons.
            // So only record success here.
            mWifiMetrics.noteFirstL2ConnectionAfterBoot(true);
            mCmiMonitor.onL2Connected(mClientModeManager);
            mIsLinkedNetworkRoaming = false;
        }

        @Override
        public void exitImpl() {
            // This is handled by receiving a NETWORK_DISCONNECTION_EVENT in ConnectableState
            // Bug: 15347363
            // For paranoia's sake, call handleNetworkDisconnect
            // only if BSSID is null or last networkId
            // is not invalid.
            if (mVerboseLoggingEnabled) {
                StringBuilder sb = new StringBuilder();
                sb.append("leaving L2ConnectedState state nid=" + Integer.toString(mLastNetworkId));
                if (mLastBssid != null) {
                    sb.append(" ").append(mLastBssid);
                }
            }
            mWifiMetrics.setWifiState(mInterfaceName, WifiMetricsProto.WifiLog.WIFI_DISCONNECTED);
            mWifiStateTracker.updateState(mInterfaceName, WifiStateTracker.DISCONNECTED);
            // Inform WifiLockManager
            mWifiLockManager.updateWifiClientConnected(mClientModeManager, false);
            mLastConnectionCapabilities = null;
        }

        @Override
        public String getMessageLogRec(int what) {
            return ClientModeImpl.class.getSimpleName() + "."
                    + L2ConnectedState.class.getSimpleName() + "." + getWhatToString(what);
        }

        @Override
        public boolean processMessageImpl(Message message) {
            boolean handleStatus = HANDLED;

            switch (message.what) {
                case CMD_PRE_DHCP_ACTION: {
                    if (!isFromCurrentIpClientCallbacks(message)) break;
                    handlePreDhcpSetup();
                    break;
                }
                case CMD_PRE_DHCP_ACTION_COMPLETE: {
                    if (mIpClient != null) {
                        mIpClient.completedPreDhcpAction();
                    }
                    break;
                }
                case CMD_POST_DHCP_ACTION: {
                    if (!isFromCurrentIpClientCallbacks(message)) break;
                    handlePostDhcpSetup();
                    // We advance to mL3ConnectedState because IpClient will also send a
                    // CMD_IPV4_PROVISIONING_SUCCESS message, which calls handleIPv4Success(),
                    // which calls updateLinkProperties, which then sends
                    // CMD_IP_CONFIGURATION_SUCCESSFUL.
                    break;
                }
                case CMD_IPV4_PROVISIONING_SUCCESS: {
                    if (!isFromCurrentIpClientCallbacks(message)) break;
                    handleIPv4Success((DhcpResultsParcelable) message.obj);
                    sendNetworkChangeBroadcastWithCurrentState();
                    break;
                }
                case CMD_IPV4_PROVISIONING_FAILURE: {
                    if (!isFromCurrentIpClientCallbacks(message)) break;
                    handleIPv4Failure();
                    mWifiLastResortWatchdog.noteConnectionFailureAndTriggerIfNeeded(
                            getConnectingSsidInternal(),
                            (mLastBssid == null) ? mTargetBssid : mLastBssid,
                            WifiLastResortWatchdog.FAILURE_CODE_DHCP,
                            isConnected());
                    break;
                }
                case CMD_IP_CONFIGURATION_SUCCESSFUL: {
                    if (!isFromCurrentIpClientCallbacks(message)) break;
                    if (getConnectedWifiConfigurationInternal() == null || mNetworkAgent == null) {
                        // The current config may have been removed while we were connecting,
                        // trigger a disconnect to clear up state.
                        reportConnectionAttemptEnd(
                                WifiMetrics.ConnectionEvent.FAILURE_NETWORK_DISCONNECTION,
                                WifiMetricsProto.ConnectionEvent.HLF_NONE,
                                WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, 0);
                        mFrameworkDisconnectReasonOverride = WifiStatsLog.WIFI_DISCONNECT_REPORTED__FAILURE_CODE__DISCONNECT_NETWORK_REMOVED;
                        mWifiNative.disconnect(mInterfaceName);
                    } else {
                        handleSuccessfulIpConfiguration();
                        transitionTo(mL3ConnectedState);
                    }
                    break;
                }
                case CMD_IP_CONFIGURATION_LOST: {
                    if (!isFromCurrentIpClientCallbacks(message)) break;
                    // Get Link layer stats so that we get fresh tx packet counters.
                    getWifiLinkLayerStats();
                    handleIpConfigurationLost();
                    reportConnectionAttemptEnd(
                            WifiMetrics.ConnectionEvent.FAILURE_DHCP,
                            WifiMetricsProto.ConnectionEvent.HLF_NONE,
                            WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, 0);
                    mWifiLastResortWatchdog.noteConnectionFailureAndTriggerIfNeeded(
                            getConnectingSsidInternal(),
                            (mLastBssid == null) ? mTargetBssid : mLastBssid,
                            WifiLastResortWatchdog.FAILURE_CODE_DHCP,
                            isConnected());
                    handleNetworkDisconnect(false,
                            WifiStatsLog.WIFI_DISCONNECT_REPORTED__FAILURE_CODE__UNSPECIFIED);
                    transitionTo(mDisconnectedState); // End of connection attempt.
                    break;
                }
                case CMD_IP_REACHABILITY_LOST: {
                    if (!isFromCurrentIpClientCallbacks(message)) break;
                    if (mVerboseLoggingEnabled && message.obj != null) log((String) message.obj);
                    mWifiDiagnostics.triggerBugReportDataCapture(
                            WifiDiagnostics.REPORT_REASON_REACHABILITY_LOST);
                    mWifiMetrics.logWifiIsUnusableEvent(mInterfaceName,
                            WifiIsUnusableEvent.TYPE_IP_REACHABILITY_LOST);
                    mWifiMetrics.addToWifiUsabilityStatsList(mInterfaceName,
                            WifiUsabilityStats.LABEL_BAD,
                            WifiUsabilityStats.TYPE_IP_REACHABILITY_LOST, -1);
                    if (mWifiGlobals.getIpReachabilityDisconnectEnabled()) {
                        handleIpReachabilityLost(-1);
                    } else {
                        logd("CMD_IP_REACHABILITY_LOST but disconnect disabled -- ignore");
                    }
                    break;
                }
                case CMD_IP_REACHABILITY_FAILURE: {
                    if (!isFromCurrentIpClientCallbacks(message)) break;
                    mWifiDiagnostics.triggerBugReportDataCapture(
                            WifiDiagnostics.REPORT_REASON_REACHABILITY_FAILURE);
                    handleIpReachabilityFailure((ReachabilityLossInfoParcelable) message.obj);
                    break;
                }
                case WifiP2pServiceImpl.DISCONNECT_WIFI_REQUEST: {
                    if (mWifiP2pConnection.shouldTemporarilyDisconnectWifi()) {
                        mWifiMetrics.logStaEvent(mInterfaceName, StaEvent.TYPE_FRAMEWORK_DISCONNECT,
                                StaEvent.DISCONNECT_P2P_DISCONNECT_WIFI_REQUEST);
                        mFrameworkDisconnectReasonOverride = WifiStatsLog.WIFI_DISCONNECT_REPORTED__FAILURE_CODE__DISCONNECT_P2P_REQUESTED_DISCONNECT;
                        mWifiNative.disconnect(mInterfaceName);
                    }
                    break;
                }
                case WifiMonitor.NETWORK_CONNECTION_EVENT: {
                    NetworkConnectionEventInfo connectionInfo =
                            (NetworkConnectionEventInfo) message.obj;
                    mLastNetworkId = connectionInfo.networkId;
                    mWifiMetrics.onRoamComplete();
                    handleNetworkConnectionEventInfo(
                            getConnectedWifiConfigurationInternal(), connectionInfo);
                    mWifiInfo.setMacAddress(mWifiNative.getMacAddress(mInterfaceName));
                    updateLayer2Information();
                    updateCurrentConnectionInfo();
                    if (!Objects.equals(mLastBssid, connectionInfo.bssid)) {
                        mLastBssid = connectionInfo.bssid;
                        sendNetworkChangeBroadcastWithCurrentState();
                    }
                    if (mIsLinkedNetworkRoaming) {
                        mIsLinkedNetworkRoaming = false;
                        mTargetNetworkId = WifiConfiguration.INVALID_NETWORK_ID;
                        mTargetWifiConfiguration = null;
                        clearTargetBssid("AllowlistRoamingCompleted");
                        sendNetworkChangeBroadcast(DetailedState.CONNECTED);
                    }
                    checkIfNeedDisconnectSecondaryWifi();
                    if (mApplicationQosPolicyRequestHandler.isFeatureEnabled()) {
                        mApplicationQosPolicyRequestHandler.queueAllPoliciesOnIface(
                                mInterfaceName, mostRecentConnectionSupports11ax());
                    }
                    break;
                }
                case WifiMonitor.BSS_FREQUENCY_CHANGED_EVENT: {
                    int newFrequency = message.arg1;
                    if (newFrequency > 0) {
                        boolean isNeedUpdate = false;
                        if (isMlo()) {
                            if (updateAssociatedMloLinksFromLinksInfoWhenBssFreqChanged(
                                    newFrequency)) {
                                isNeedUpdate = true;
                            }
                        } else if (mWifiInfo.getFrequency() != newFrequency) {
                            mWifiInfo.setFrequency(newFrequency);
                            isNeedUpdate = true;
                        }
                        if (isNeedUpdate) {
                            updateCurrentConnectionInfo();
                            updateCapabilities();
                        }
                    }
                    break;
                }
                case CMD_ONESHOT_RSSI_POLL: {
                    if (!mEnableRssiPolling) {
                        updateLinkLayerStatsRssiDataStallScoreReport();
                    }
                    break;
                }
                case CMD_RSSI_POLL: {
                    // TODO(b/179792830): getBSSID() shouldn't be null in L2ConnectedState,
                    //  add debug logs in the meantime. Remove once root cause identified.
                    if (mWifiInfo.getBSSID() == null) {
                        Log.wtf(getTag(), "WifiInfo.getBSSID() is null in L2ConnectedState!");
                        break;
                    }
                    if (message.arg1 == mRssiPollToken) {
                        updateLinkLayerStatsRssiDataStallScoreReport();
                        mWifiScoreCard.noteSignalPoll(mWifiInfo);
                        if (isPrimary()) {
                            mLinkProbeManager.updateConnectionStats(mWifiInfo, mInterfaceName);
                        }
                        // Update the polling interval as needed before sending the delayed message
                        // so that the next polling can happen after the updated interval
                        if (isPrimary()) {
                            int curState = mWifiInjector.getActiveModeWarden()
                                    .getDeviceMobilityState();
                            mRssiMonitor.updatePollRssiInterval(curState);
                        }
                        sendMessageDelayed(obtainMessage(CMD_RSSI_POLL, mRssiPollToken, 0),
                                mWifiGlobals.getPollRssiIntervalMillis());
                        if (isPrimary()) {
                            mWifiTrafficPoller.notifyOnDataActivity(
                                    mWifiInfo.txSuccess, mWifiInfo.rxSuccess);
                        }
                    } else {
                        // Polling has completed
                    }
                    break;
                }
                case CMD_ENABLE_RSSI_POLL: {
                    cleanWifiScore();
                    mEnableRssiPolling = (message.arg1 == 1);
                    mRssiPollToken++;
                    if (mEnableRssiPolling) {
                        // First poll
                        mLastSignalLevel = -1;
                        if (isPrimary()) {
                            mLinkProbeManager.resetOnScreenTurnedOn();
                        }
                        long txBytes = mFacade.getTotalTxBytes() - mFacade.getMobileTxBytes();
                        long rxBytes = mFacade.getTotalRxBytes() - mFacade.getMobileRxBytes();
                        updateLinkLayerStatsRssiSpeedFrequencyCapabilities(txBytes, rxBytes);
                        sendMessageDelayed(obtainMessage(CMD_RSSI_POLL, mRssiPollToken, 0),
                                mWifiGlobals.getPollRssiIntervalMillis());
                    }
                    else {
                        mRssiMonitor.setShortPollRssiInterval();
                        removeMessages(CMD_RSSI_POLL);
                    }
                    break;
                }
                case WifiMonitor.ASSOCIATED_BSSID_EVENT: {
                    if ((String) message.obj == null) {
                        logw("Associated command w/o BSSID");
                        break;
                    }
                    mLastBssid = (String) message.obj;
                    if (checkAndHandleLinkedNetworkRoaming(mLastBssid)) {
                        Log.i(TAG, "Driver initiated allowlist SSID roaming");
                        break;
                    }
                    if (mLastBssid != null && (mWifiInfo.getBSSID() == null
                            || !mLastBssid.equals(mWifiInfo.getBSSID()))) {
                        mWifiInfo.setBSSID(mLastBssid);
                        WifiConfiguration config = getConnectedWifiConfigurationInternal();
                        if (config != null) {
                            ScanDetailCache scanDetailCache = mWifiConfigManager
                                    .getScanDetailCacheForNetwork(config.networkId);
                            if (scanDetailCache != null) {
                                ScanResult scanResult = scanDetailCache.getScanResult(mLastBssid);
                                if (scanResult != null) {
                                    mWifiInfo.setFrequency(scanResult.frequency);
                                    boolean isHidden = true;
                                    for (byte b : scanResult.getWifiSsid().getBytes()) {
                                        if (b != 0) {
                                            isHidden = false;
                                            break;
                                        }
                                    }
                                    mWifiInfo.setHiddenSSID(isHidden);
                                }
                            }
                        }
                        sendNetworkChangeBroadcastWithCurrentState();
                        mMultiInternetManager.notifyBssidAssociatedEvent(mClientModeManager);
                        updateCurrentConnectionInfo();
                    }
                    break;
                }
                case CMD_RECONNECT: {
                    log(" Ignore CMD_RECONNECT request because wifi is already connected");
                    break;
                }
                case CMD_RESET_SIM_NETWORKS: {
                    if (message.arg1 != RESET_SIM_REASON_SIM_INSERTED
                            && mLastNetworkId != WifiConfiguration.INVALID_NETWORK_ID) {
                        WifiConfiguration config =
                                mWifiConfigManager.getConfiguredNetwork(mLastNetworkId);
                        if (config != null
                            && ((message.arg1 == RESET_SIM_REASON_DEFAULT_DATA_SIM_CHANGED
                                && config.carrierId != TelephonyManager.UNKNOWN_CARRIER_ID)
                                || (config.enterpriseConfig != null
                                && config.enterpriseConfig.isAuthenticationSimBased()
                                && !mWifiCarrierInfoManager.isSimReady(mLastSubId)))) {
                            mWifiMetrics.logStaEvent(mInterfaceName,
                                    StaEvent.TYPE_FRAMEWORK_DISCONNECT,
                                    StaEvent.DISCONNECT_RESET_SIM_NETWORKS);
                            // remove local PMKSA cache in framework
                            mWifiNative.removeNetworkCachedData(mLastNetworkId);
                            // remove network so that supplicant's PMKSA cache is cleared
                            mWifiNative.removeAllNetworks(mInterfaceName);
                            if (isPrimary() && !mWifiCarrierInfoManager.isSimReady(mLastSubId)) {
                                mSimRequiredNotifier.showSimRequiredNotification(
                                        config, mLastSimBasedConnectionCarrierName);
                            }
                        }
                    }
                    break;
                }
                case CMD_START_IP_PACKET_OFFLOAD: {
                    int slot = message.arg1;
                    int intervalSeconds = message.arg2;
                    KeepalivePacketData pkt = (KeepalivePacketData) message.obj;
                    int result = startWifiIPPacketOffload(slot, pkt, intervalSeconds);
                    if (mNetworkAgent != null) {
                        mNetworkAgent.sendSocketKeepaliveEvent(slot, result);
                    }
                    break;
                }
                case CMD_ADD_KEEPALIVE_PACKET_FILTER_TO_APF: {
                    if (mIpClient != null) {
                        final int slot = message.arg1;
                        if (message.obj instanceof NattKeepalivePacketData) {
                            final NattKeepalivePacketData pkt =
                                    (NattKeepalivePacketData) message.obj;
                            mIpClient.addKeepalivePacketFilter(slot, pkt);
                        } else if (SdkLevel.isAtLeastS()) {
                            if (message.obj instanceof TcpKeepalivePacketData) {
                                final TcpKeepalivePacketData pkt =
                                        (TcpKeepalivePacketData) message.obj;
                                mIpClient.addKeepalivePacketFilter(slot, pkt);
                            }
                            // Otherwise unsupported keepalive data class: skip
                        } else {
                            // Before S, non-NattKeepalivePacketData KeepalivePacketData would be
                            // the not-yet-SystemApi android.net.TcpKeepalivePacketData.
                            // Attempt to parse TcpKeepalivePacketDataParcelable from the
                            // KeepalivePacketData superclass.
                            final TcpKeepalivePacketDataParcelable p =
                                    parseTcpKeepalivePacketData((KeepalivePacketData) message.obj);
                            if (p != null) {
                                mIpClient.addKeepalivePacketFilter(slot, p);
                            }
                        }
                    }
                    break;
                }
                case CMD_REMOVE_KEEPALIVE_PACKET_FILTER_FROM_APF: {
                    if (mIpClient != null) {
                        mIpClient.removeKeepalivePacketFilter(message.arg1);
                    }
                    break;
                }
                case WifiMonitor.MLO_LINKS_INFO_CHANGED:
                    WifiMonitor.MloLinkInfoChangeReason reason =
                            (WifiMonitor.MloLinkInfoChangeReason) message.obj;
                    WifiNative.ConnectionMloLinksInfo newInfo =
                            mWifiNative.getConnectionMloLinksInfo(mInterfaceName);
                    if (reason == WifiMonitor.MloLinkInfoChangeReason.TID_TO_LINK_MAP) {
                        // Traffic stream mapping changed. Update link states.
                        updateMloLinkStates(newInfo);
                        // There is a change in link capabilities. Will trigger android.net
                        // .ConnectivityManager.NetworkCallback.onCapabilitiesChanged().
                        updateCapabilities();
                    } else if (reason
                            == WifiMonitor.MloLinkInfoChangeReason.MULTI_LINK_RECONFIG_AP_REMOVAL) {
                        // Link is removed. Set removed link state to MLO_LINK_STATE_UNASSOCIATED.
                        // Also update block list mapping, as there is a change in affiliated
                        // BSSIDs.
                        clearMloLinkStates();
                        updateMloLinkStates(newInfo);
                        updateBlockListAffiliatedBssids();
                        // There is a change in link capabilities. Will trigger android.net
                        // .ConnectivityManager.NetworkCallback.onCapabilitiesChanged().
                        updateCapabilities();
                    } else {
                        logw("MLO_LINKS_INFO_CHANGED with UNKNOWN reason");
                    }
                    break;
                default: {
                    handleStatus = NOT_HANDLED;
                    break;
                }
            }

            if (handleStatus == HANDLED) {
                logStateAndMessage(message, this);
            }

            return handleStatus;
        }

        /**
         * Fetches link stats, updates Wifi Data Stall, Score Card and Score Report.
         */
        private WifiLinkLayerStats updateLinkLayerStatsRssiDataStallScoreReport() {
            // Get Info and continue polling
            long txBytes;
            long rxBytes;
            if (SdkLevel.isAtLeastS()) {
                txBytes = mFacade.getTxBytes(mInterfaceName);
                rxBytes = mFacade.getRxBytes(mInterfaceName);
            } else {
                txBytes = mFacade.getTotalTxBytes() - mFacade.getMobileTxBytes();
                rxBytes = mFacade.getTotalRxBytes() - mFacade.getMobileRxBytes();
            }
            WifiLinkLayerStats stats = updateLinkLayerStatsRssiSpeedFrequencyCapabilities(txBytes,
                    rxBytes);
            mWifiMetrics.updateWifiUsabilityStatsEntries(mInterfaceName, mWifiInfo, stats);
            // checkDataStallAndThroughputSufficiency() should be called before
            // mWifiScoreReport.calculateAndReportScore() which needs the latest throughput
            int statusDataStall = mWifiDataStall.checkDataStallAndThroughputSufficiency(
                    mInterfaceName, mLastConnectionCapabilities, mLastLinkLayerStats, stats,
                    mWifiInfo, txBytes, rxBytes);
            if (mDataStallTriggerTimeMs == -1
                    && statusDataStall != WifiIsUnusableEvent.TYPE_UNKNOWN) {
                mDataStallTriggerTimeMs = mClock.getElapsedSinceBootMillis();
                mLastStatusDataStall = statusDataStall;
            }
            if (mDataStallTriggerTimeMs != -1) {
                long elapsedTime =  mClock.getElapsedSinceBootMillis()
                        - mDataStallTriggerTimeMs;
                if (elapsedTime >= DURATION_TO_WAIT_ADD_STATS_AFTER_DATA_STALL_MS) {
                    mDataStallTriggerTimeMs = -1;
                    mWifiMetrics.addToWifiUsabilityStatsList(mInterfaceName,
                            WifiUsabilityStats.LABEL_BAD,
                            convertToUsabilityStatsTriggerType(mLastStatusDataStall),
                            -1);
                    mLastStatusDataStall = WifiIsUnusableEvent.TYPE_UNKNOWN;
                }
            }
            // Send the update score to network agent.
            mWifiScoreReport.calculateAndReportScore();

            if (mWifiScoreReport.shouldCheckIpLayer()) {
                if (mIpClient != null) {
                    mIpClient.confirmConfiguration();
                }
                mWifiScoreReport.noteIpCheck();
            }

            mLastLinkLayerStats = stats;
            return stats;
        }
    }

    /**
     * Fetches link stats and updates Wifi Score Report.
     */
    private void updateLinkLayerStatsRssiAndScoreReport() {
        sendMessage(CMD_ONESHOT_RSSI_POLL);
    }

    private int convertToUsabilityStatsTriggerType(int unusableEventTriggerType) {
        int triggerType;
        switch (unusableEventTriggerType) {
            case WifiIsUnusableEvent.TYPE_DATA_STALL_BAD_TX:
                triggerType = WifiUsabilityStats.TYPE_DATA_STALL_BAD_TX;
                break;
            case WifiIsUnusableEvent.TYPE_DATA_STALL_TX_WITHOUT_RX:
                triggerType = WifiUsabilityStats.TYPE_DATA_STALL_TX_WITHOUT_RX;
                break;
            case WifiIsUnusableEvent.TYPE_DATA_STALL_BOTH:
                triggerType = WifiUsabilityStats.TYPE_DATA_STALL_BOTH;
                break;
            case WifiIsUnusableEvent.TYPE_FIRMWARE_ALERT:
                triggerType = WifiUsabilityStats.TYPE_FIRMWARE_ALERT;
                break;
            case WifiIsUnusableEvent.TYPE_IP_REACHABILITY_LOST:
                triggerType = WifiUsabilityStats.TYPE_IP_REACHABILITY_LOST;
                break;
            default:
                triggerType = WifiUsabilityStats.TYPE_UNKNOWN;
                Log.e(getTag(), "Unknown WifiIsUnusableEvent: " + unusableEventTriggerType);
        }
        return triggerType;
    }

    // Before transition to L3ProvisioningState, always shut down the current IpClient
    // instance and recreate a new IpClient and IpClientCallbacks instance, defer received
    // messages in this state except CMD_IPCLIENT_CREATED, recreation of a new IpClient
    // guarantees the out-of-date callbacks will be ignored, otherwise, it's not easy to
    // differentiate callbacks which comes from new IpClient or legacy IpClient. So far
    // only transit to this state iff IP reachability gets lost due to NUD failure.
    class WaitBeforeL3ProvisioningState extends RunnerState {
        WaitBeforeL3ProvisioningState(int threshold) {
            super(threshold, mWifiInjector.getWifiHandlerLocalLog());
        }

        @Override
        public void enterImpl() {
            // Recreate a new IpClient instance.
            makeIpClient();

            // Given that {@link IpClientCallbacks#awaitCreation} is invoked when making a
            // IpClient instance, which waits for {@link IPCLIENT_STARTUP_TIMEOUT_MS}.
            // If await IpClient recreation times out, then send timeout message to proceed
            // to Disconnected state, otherwise, we will never exit this state.
            sendMessage(CMD_IPCLIENT_STARTUP_TIMEOUT);
        }

        @Override
        public void exitImpl() {
            removeMessages(CMD_IPCLIENT_STARTUP_TIMEOUT);
        }

        @Override
        public boolean processMessageImpl(Message message) {
            switch(message.what) {
                case CMD_IPCLIENT_CREATED: {
                    if (!isFromCurrentIpClientCallbacks(message)) break;
                    mIpClient = (IpClientManager) message.obj;
                    transitionTo(mL3ProvisioningState);
                    break;
                }

                case CMD_IPCLIENT_STARTUP_TIMEOUT: {
                    Log.e(getTag(), "Fail to create an IpClient instance within "
                            + IPCLIENT_STARTUP_TIMEOUT_MS + "ms");
                    handleNetworkDisconnect(false,
                            WifiStatsLog.WIFI_DISCONNECT_REPORTED__FAILURE_CODE__DISCONNECT_CREATE_IP_CLIENT_TIMEOUT);
                    transitionTo(mDisconnectedState);
                    break;
                }

                default:
                    // Do not process any other messages except CMD_IPCLIENT_CREATED and
                    // CMD_IPCLIENT_STARTUP_TIMEOUT. This means that this state can be very
                    // simple because it does not need to worry about messasge ordering.
                    // Re-creating IpClient should only take a few milliseconds, but in the
                    // worst case, this will result in the state machine not processing any
                    // messages for IPCLIENT_STARTUP_TIMEOUT_MS.
                    deferMessage(message);
            }

            logStateAndMessage(message, this);
            return HANDLED;
        }

        @Override
        public String getMessageLogRec(int what) {
            return ClientModeImpl.class.getSimpleName() + "."
                    + WaitBeforeL3ProvisioningState.class.getSimpleName() + "."
                    + getWhatToString(what);
        }
    }

    class L3ProvisioningState extends RunnerState {

        private void onL3ProvisioningTimeout() {
            logi("l3Provisioning Timeout, Configuring the network as local-only");
            mIpProvisioningTimedOut = true;
            mWifiConnectivityManager.handleConnectionStateChanged(
                    mClientModeManager,
                    WifiConnectivityManager.WIFI_STATE_CONNECTED);
            mWifiConfigManager.setIpProvisioningTimedOut(mLastNetworkId, true);
            sendNetworkChangeBroadcast(DetailedState.CONNECTED);
        };

        L3ProvisioningState(int threshold) {
            super(threshold, mWifiInjector.getWifiHandlerLocalLog());
        }

        @Override
        public void enterImpl() {
            startL3Provisioning();
            if (mContext.getResources().getBoolean(
                    R.bool.config_wifiRemainConnectedAfterIpProvisionTimeout)) {
                sendMessageDelayed(obtainMessage(CMD_IP_PROVISIONING_TIMEOUT),
                        WAIT_FOR_L3_PROVISIONING_TIMEOUT_MS);
            }
        }

        @Override
        public void exitImpl() {
            mIpProvisioningTimedOut = false;
            removeMessages(CMD_IP_PROVISIONING_TIMEOUT);
            mWifiConfigManager.setIpProvisioningTimedOut(mLastNetworkId, false);
        }

        @Override
        public String getMessageLogRec(int what) {
            return ClientModeImpl.class.getSimpleName() + "."
                    + L3ProvisioningState.class.getSimpleName() + "." + getWhatToString(what);
        }

        @Override
        public boolean processMessageImpl(Message message) {
            boolean handleStatus = HANDLED;

            switch(message.what) {
                case WifiMonitor.NETWORK_DISCONNECTION_EVENT: {
                    DisconnectEventInfo eventInfo = (DisconnectEventInfo) message.obj;
                    mWifiLastResortWatchdog.noteConnectionFailureAndTriggerIfNeeded(
                            getConnectingSsidInternal(),
                            !isValidBssid(eventInfo.bssid)
                            ? mTargetBssid : eventInfo.bssid,
                            WifiLastResortWatchdog.FAILURE_CODE_DHCP,
                            isConnected());
                    handleStatus = NOT_HANDLED;
                    break;
                }
                case CMD_IP_PROVISIONING_TIMEOUT: {
                    onL3ProvisioningTimeout();
                    break;
                }
                default: {
                    handleStatus = NOT_HANDLED;
                    break;
                }
            }

            if (handleStatus == HANDLED) {
                logStateAndMessage(message, this);
            }
            return handleStatus;
        }

        private void startL3Provisioning() {
            WifiConfiguration currentConfig = getConnectedWifiConfigurationInternal();
            if (mIpClientWithPreConnection && mIpClient != null) {
                mIpClient.notifyPreconnectionComplete(mSentHLPs);
                mIpClientWithPreConnection = false;
                mSentHLPs = false;
            } else {
                startIpClient(currentConfig, false);
            }
            // Get Link layer stats so as we get fresh tx packet counters
            getWifiLinkLayerStats();
        }
    }

    /**
     * Helper function to check if a network has been recently selected by the user.
     * (i.e less than {@link #LAST_SELECTED_NETWORK_EXPIRATION_AGE_MILLIS) before).
     */
    @VisibleForTesting
    public boolean isRecentlySelectedByTheUser(@NonNull WifiConfiguration currentConfig) {
        long currentTimeMillis = mClock.getElapsedSinceBootMillis();
        return mWifiConfigManager.getLastSelectedNetwork() == currentConfig.networkId
                && currentTimeMillis - mWifiConfigManager.getLastSelectedTimeStamp()
                < LAST_SELECTED_NETWORK_EXPIRATION_AGE_MILLIS;
    }

    private void sendConnectedState() {
        mNetworkAgent.markConnected();
        sendNetworkChangeBroadcast(DetailedState.CONNECTED);
    }

    class RoamingState extends RunnerState {
        boolean mAssociated;

        RoamingState(int threshold) {
            super(threshold, mWifiInjector.getWifiHandlerLocalLog());
        }

        @Override
        public void enterImpl() {
            if (mVerboseLoggingEnabled) {
                log("RoamingState Enter mScreenOn=" + mScreenOn);
            }

            // Make sure we disconnect if roaming fails
            mRoamWatchdogCount++;
            logd("Start Roam Watchdog " + mRoamWatchdogCount);
            sendMessageDelayed(obtainMessage(CMD_ROAM_WATCHDOG_TIMER,
                    mRoamWatchdogCount, 0), ROAM_GUARD_TIMER_MSEC);
            mAssociated = false;
        }

        @Override
        public void exitImpl() {
        }

        @Override
        public String getMessageLogRec(int what) {
            return ClientModeImpl.class.getSimpleName() + "." + RoamingState.class.getSimpleName()
                    + "." + getWhatToString(what);
        }

        @Override
        public boolean processMessageImpl(Message message) {
            boolean handleStatus = HANDLED;

            switch (message.what) {
                case CMD_IP_CONFIGURATION_LOST: {
                    WifiConfiguration config = getConnectedWifiConfigurationInternal();
                    if (config != null) {
                        mWifiDiagnostics.triggerBugReportDataCapture(
                                WifiDiagnostics.REPORT_REASON_AUTOROAM_FAILURE);
                    }
                    handleStatus = NOT_HANDLED;
                    break;
                }
                case CMD_UNWANTED_NETWORK: {
                    if (mVerboseLoggingEnabled) {
                        log("Roaming and CS doesn't want the network -> ignore");
                    }
                    break;
                }
                case WifiMonitor.SUPPLICANT_STATE_CHANGE_EVENT: {
                    /**
                     * If we get a SUPPLICANT_STATE_CHANGE_EVENT indicating a DISCONNECT
                     * before NETWORK_DISCONNECTION_EVENT
                     * And there is an associated BSSID corresponding to our target BSSID, then
                     * we have missed the network disconnection, transition to mDisconnectedState
                     * and handle the rest of the events there.
                     */
                    StateChangeResult stateChangeResult = (StateChangeResult) message.obj;
                    SupplicantState state = handleSupplicantStateChange(stateChangeResult);
                    if (state == SupplicantState.DISCONNECTED
                            || state == SupplicantState.INACTIVE
                            || state == SupplicantState.INTERFACE_DISABLED) {
                        if (mVerboseLoggingEnabled) {
                            log("RoamingState: Supplicant State change " + stateChangeResult);
                        }
                        handleNetworkDisconnect(false,
                                WIFI_DISCONNECT_REPORTED__FAILURE_CODE__SUPPLICANT_DISCONNECTED);
                        transitionTo(mDisconnectedState);
                    }
                    if (stateChangeResult.state == SupplicantState.ASSOCIATED) {
                        // We completed the layer2 roaming part
                        mAssociated = true;
                        mTargetBssid = stateChangeResult.bssid;
                    }
                    break;
                }
                case CMD_ROAM_WATCHDOG_TIMER: {
                    if (mRoamWatchdogCount == message.arg1) {
                        if (mVerboseLoggingEnabled) log("roaming watchdog! -> disconnect");
                        mWifiMetrics.endConnectionEvent(
                                mInterfaceName,
                                WifiMetrics.ConnectionEvent.FAILURE_ROAM_TIMEOUT,
                                WifiMetricsProto.ConnectionEvent.HLF_NONE,
                                WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN,
                                mWifiInfo.getFrequency(), 0);
                        mRoamFailCount++;
                        handleNetworkDisconnect(false,
                                WifiStatsLog.WIFI_DISCONNECT_REPORTED__FAILURE_CODE__ROAM_WATCHDOG_TIMER);
                        mWifiMetrics.logStaEvent(mInterfaceName, StaEvent.TYPE_FRAMEWORK_DISCONNECT,
                                StaEvent.DISCONNECT_ROAM_WATCHDOG_TIMER);
                        mWifiNative.disconnect(mInterfaceName);
                        transitionTo(mDisconnectedState);
                    }
                    break;
                }
                case WifiMonitor.NETWORK_CONNECTION_EVENT: {
                    if (mAssociated) {
                        if (mVerboseLoggingEnabled) {
                            log("roaming and Network connection established");
                        }
                        NetworkConnectionEventInfo connectionInfo =
                                (NetworkConnectionEventInfo) message.obj;
                        mLastNetworkId = connectionInfo.networkId;
                        mLastBssid = connectionInfo.bssid;
                        handleNetworkConnectionEventInfo(
                                getConnectedWifiConfigurationInternal(), connectionInfo);
                        updateLayer2Information();
                        sendNetworkChangeBroadcastWithCurrentState();
                        updateCurrentConnectionInfo();
                        // Successful framework roam! (probably)
                        mWifiBlocklistMonitor.handleBssidConnectionSuccess(mLastBssid,
                                mWifiInfo.getSSID());
                        reportConnectionAttemptEnd(
                                WifiMetrics.ConnectionEvent.FAILURE_NONE,
                                WifiMetricsProto.ConnectionEvent.HLF_NONE,
                                WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, 0);

                        // We must clear the config BSSID, as the wifi chipset may decide to roam
                        // from this point on and having the BSSID specified by QNS would cause
                        // the roam to fail and the device to disconnect.
                        // When transition from RoamingState to DisconnectedState, the config BSSID
                        // is cleared by handleNetworkDisconnect().
                        clearTargetBssid("RoamingCompleted");

                        // We used to transition to L3ProvisioningState in an
                        // attempt to do DHCPv4 RENEWs on framework roams.
                        // DHCP can take too long to time out, and we now rely
                        // upon IpClient's use of IpReachabilityMonitor to
                        // confirm our current network configuration.
                        //
                        // mIpClient.confirmConfiguration() is called within
                        // the handling of SupplicantState.COMPLETED.
                        transitionTo(mL3ConnectedState);
                    } else {
                        mMessageHandlingStatus = MESSAGE_HANDLING_STATUS_DISCARD;
                    }
                    break;
                }
                case WifiMonitor.NETWORK_DISCONNECTION_EVENT: {
                    // Throw away but only if it corresponds to the network we're roaming to
                    DisconnectEventInfo eventInfo = (DisconnectEventInfo) message.obj;
                    if (true) {
                        String target = "";
                        if (mTargetBssid != null) target = mTargetBssid;
                        log("NETWORK_DISCONNECTION_EVENT in roaming state"
                                + " BSSID=" + eventInfo.bssid
                                + " target=" + target);
                    }
                    clearNetworkCachedDataIfNeeded(
                            getConnectingWifiConfigurationInternal(), eventInfo.reasonCode);
                    if (eventInfo.bssid.equals(mTargetBssid)) {
                        handleNetworkDisconnect(false, eventInfo.reasonCode);
                        transitionTo(mDisconnectedState);
                    }
                    break;
                }
                default: {
                    handleStatus = NOT_HANDLED;
                    break;
                }
            }

            if (handleStatus == HANDLED) {
                logStateAndMessage(message, this);
            }
            return handleStatus;
        }

        @Override
        public void exit() {
            logd("ClientModeImpl: Leaving Roaming state");
        }
    }

    class L3ConnectedState extends RunnerState {
        L3ConnectedState(int threshold) {
            super(threshold, mWifiInjector.getWifiHandlerLocalLog());
        }

        @Override
        public void enterImpl() {
            if (mVerboseLoggingEnabled) {
                log("Enter ConnectedState  mScreenOn=" + mScreenOn);
            }

            reportConnectionAttemptEnd(
                    WifiMetrics.ConnectionEvent.FAILURE_NONE,
                    WifiMetricsProto.ConnectionEvent.HLF_NONE,
                    WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, 0);
            mWifiConnectivityManager.handleConnectionStateChanged(
                    mClientModeManager,
                    WifiConnectivityManager.WIFI_STATE_CONNECTED);
            registerConnected();
            mTargetWifiConfiguration = null;
            mWifiScoreReport.reset();
            mLastSignalLevel = -1;

            // Not roaming anymore
            mIsAutoRoaming = false;

            mTargetNetworkId = WifiConfiguration.INVALID_NETWORK_ID;
            mWifiLastResortWatchdog.connectedStateTransition(true);
            mWifiStateTracker.updateState(mInterfaceName, WifiStateTracker.CONNECTED);
            // Inform WifiLockManager
            mWifiLockManager.updateWifiClientConnected(mClientModeManager, true);
            WifiConfiguration config = getConnectedWifiConfigurationInternal();
            mWifiScoreReport.startConnectedNetworkScorer(
                    mNetworkAgent.getNetwork().getNetId(), isRecentlySelectedByTheUser(config));
            updateLinkLayerStatsRssiAndScoreReport();
            mWifiScoreCard.noteIpConfiguration(mWifiInfo);
            // too many places to record L3 failure with too many failure reasons.
            // So only record success here.
            mWifiMetrics.noteFirstL3ConnectionAfterBoot(true);
            updateCurrentConnectionInfo();
            sendConnectedState();
            // Set the roaming policy for the currently connected network
            if (isPrimary() && getClientRoleForMetrics(config)
                    != WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__ROLE__ROLE_CLIENT_LOCAL_ONLY) {
                mWifiInjector.getWifiRoamingModeManager().applyWifiRoamingMode(
                        mInterfaceName, mWifiInfo.getSSID());
            }
        }

        @Override
        public String getMessageLogRec(int what) {
            return ClientModeImpl.class.getSimpleName() + "."
                    + L3ConnectedState.class.getSimpleName() + "." + getWhatToString(what);
        }

        @Override
        public boolean processMessageImpl(Message message) {
            boolean handleStatus = HANDLED;

            switch (message.what) {
                case CMD_UNWANTED_NETWORK: {
                    if (message.arg1 == NETWORK_STATUS_UNWANTED_DISCONNECT) {
                        mWifiMetrics.logStaEvent(mInterfaceName, StaEvent.TYPE_FRAMEWORK_DISCONNECT,
                                StaEvent.DISCONNECT_UNWANTED);
                        if (mClientModeManager.getRole() == ROLE_CLIENT_SECONDARY_TRANSIENT
                                && mClientModeManager.getPreviousRole() == ROLE_CLIENT_PRIMARY) {
                            mWifiMetrics.incrementMakeBeforeBreakLingerCompletedCount(
                                    mClock.getElapsedSinceBootMillis()
                                            - mClientModeManager.getLastRoleChangeSinceBootMs());
                        }
                        WifiConfiguration config = getConnectedWifiConfigurationInternal();
                        if (mWifiGlobals.disableUnwantedNetworkOnLowRssi() && isPrimary()
                                && config != null && !isRecentlySelectedByTheUser(config)
                                && config.getNetworkSelectionStatus()
                                .getNetworkSelectionDisableReason()
                                    == DISABLED_NONE && mWifiInfo.getRssi() != WifiInfo.INVALID_RSSI
                                && mWifiInfo.getRssi() < mScoringParams.getSufficientRssi(
                                        mWifiInfo.getFrequency())) {
                            if (mVerboseLoggingEnabled) {
                                Log.i(getTag(), "CMD_UNWANTED_NETWORK update network "
                                        + config.networkId + " with DISABLED_UNWANTED_LOW_RSSI "
                                        + "under rssi:" + mWifiInfo.getRssi());
                            }
                            mWifiConfigManager.updateNetworkSelectionStatus(
                                    config.networkId,
                                    DISABLED_UNWANTED_LOW_RSSI);
                        }
                        mFrameworkDisconnectReasonOverride = WifiStatsLog.WIFI_DISCONNECT_REPORTED__FAILURE_CODE__DISCONNECT_UNWANTED_BY_CONNECTIVITY;
                        mWifiNative.disconnect(mInterfaceName);
                    } else if (message.arg1 == NETWORK_STATUS_UNWANTED_DISABLE_AUTOJOIN
                            || message.arg1 == NETWORK_STATUS_UNWANTED_VALIDATION_FAILED) {
                        Log.d(getTag(), (message.arg1 == NETWORK_STATUS_UNWANTED_DISABLE_AUTOJOIN
                                ? "NETWORK_STATUS_UNWANTED_DISABLE_AUTOJOIN"
                                : "NETWORK_STATUS_UNWANTED_VALIDATION_FAILED"));
                        WifiConfiguration config = getConnectedWifiConfigurationInternal();
                        if (config != null) {
                            // Disable autojoin
                            if (message.arg1 == NETWORK_STATUS_UNWANTED_DISABLE_AUTOJOIN) {
                                mWifiConfigManager.setNetworkValidatedInternetAccess(
                                        config.networkId, false);
                                WifiScoreCard.PerBssid perBssid = mWifiScoreCard.lookupBssid(
                                        mWifiInfo.getSSID(), mWifiInfo.getBSSID());
                                int probInternet = perBssid.estimatePercentInternetAvailability();
                                if (mVerboseLoggingEnabled) {
                                    Log.d(TAG, "Potentially disabling network due to no "
                                            + "internet. Probability of having internet = "
                                            + probInternet);
                                }
                                // Only permanently disable a network if probability of having
                                // internet from the currently connected BSSID is less than 60%.
                                // If there is no historically information of the current BSSID,
                                // the probability of internet will default to 50%, and the network
                                // will be permanently disabled.
                                mWifiConfigManager.updateNetworkSelectionStatus(config.networkId,
                                        probInternet < PROBABILITY_WITH_INTERNET_TO_PERMANENTLY_DISABLE_NETWORK
                                                ? DISABLED_NO_INTERNET_PERMANENT
                                                : DISABLED_NO_INTERNET_TEMPORARY);
                            } else { // NETWORK_STATUS_UNWANTED_VALIDATION_FAILED
                                // stop collect last-mile stats since validation fail
                                mWifiDiagnostics.reportConnectionEvent(
                                        WifiDiagnostics.CONNECTION_EVENT_FAILED,
                                        mClientModeManager);
                                mWifiConfigManager.incrementNetworkNoInternetAccessReports(
                                        config.networkId);
                                if (!config.getNetworkSelectionStatus()
                                        .hasEverValidatedInternetAccess()
                                        && !config.noInternetAccessExpected) {
                                    mWifiConfigManager.updateNetworkSelectionStatus(
                                            config.networkId,
                                            DISABLED_NO_INTERNET_PERMANENT);
                                } else if (!isRecentlySelectedByTheUser(config)
                                        && !config.noInternetAccessExpected) {
                                    // If this was not recently selected by the user, update network
                                    // selection status to temporarily disable the network.
                                    if (config.getNetworkSelectionStatus()
                                            .getNetworkSelectionDisableReason()
                                            != DISABLED_NO_INTERNET_PERMANENT) {
                                        Log.i(getTag(), "Temporarily disabling network "
                                                + "because of no-internet access");
                                        mWifiConfigManager.updateNetworkSelectionStatus(
                                                config.networkId,
                                                DISABLED_NO_INTERNET_TEMPORARY);
                                    }
                                    mWifiBlocklistMonitor.handleBssidConnectionFailure(
                                            mLastBssid, config,
                                            WifiBlocklistMonitor.REASON_NETWORK_VALIDATION_FAILURE,
                                            mWifiInfo.getRssi());
                                }
                                mWifiScoreCard.noteValidationFailure(mWifiInfo);
                                mCmiMonitor.onInternetValidationFailed(mClientModeManager,
                                        mCurrentConnectionDetectedCaptivePortal);
                            }
                        }
                    }
                    break;
                }
                case CMD_NETWORK_STATUS: {
                    if (message.arg1 == NetworkAgent.VALIDATION_STATUS_VALID) {
                        // stop collect last-mile stats since validation pass
                        mWifiDiagnostics.reportConnectionEvent(
                                WifiDiagnostics.CONNECTION_EVENT_SUCCEEDED, mClientModeManager);
                        mWifiScoreCard.noteValidationSuccess(mWifiInfo);
                        mWifiBlocklistMonitor.handleNetworkValidationSuccess(mLastBssid,
                                mWifiInfo.getSSID());
                        WifiConfiguration config = getConnectedWifiConfigurationInternal();
                        if (config != null) {
                            // re-enable autojoin
                            mWifiConfigManager.updateNetworkSelectionStatus(
                                    config.networkId,
                                    WifiConfiguration.NetworkSelectionStatus
                                            .DISABLED_NONE);
                            mWifiConfigManager.setNetworkValidatedInternetAccess(
                                    config.networkId, true);
                            if (config.isPasspoint()
                                    && mTermsAndConditionsUrl != null) {
                                // Clear the T&C after the user accepted them and the we are
                                // notified that the network validation is successful
                                mTermsAndConditionsUrl = null;
                                LinkProperties newLp = new LinkProperties(mLinkProperties);
                                addPasspointInfoToLinkProperties(newLp);
                                sendMessage(CMD_UPDATE_LINKPROPERTIES,
                                        mIpClientCallbacks.getCallbackIndex(), 0, newLp);
                                mWifiMetrics
                                        .incrementTotalNumberOfPasspointAcceptanceOfTermsAndConditions();
                            }
                            if (retrieveConnectedNetworkDefaultGateway()) {
                                updateLinkedNetworks(config);
                            }
                        }
                        mCmiMonitor.onInternetValidated(mClientModeManager);
                    }
                    break;
                }
                case CMD_ACCEPT_UNVALIDATED: {
                    boolean accept = (message.arg1 != 0);
                    mWifiConfigManager.setNetworkNoInternetAccessExpected(mLastNetworkId, accept);
                    break;
                }
                case WifiMonitor.NETWORK_DISCONNECTION_EVENT: {
                    DisconnectEventInfo eventInfo = (DisconnectEventInfo) message.obj;
                    reportConnectionAttemptEnd(
                            WifiMetrics.ConnectionEvent.FAILURE_NETWORK_DISCONNECTION,
                            WifiMetricsProto.ConnectionEvent.HLF_NONE,
                            WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN,
                            eventInfo.reasonCode);
                    if (unexpectedDisconnectedReason(eventInfo.reasonCode)) {
                        mWifiDiagnostics.triggerBugReportDataCapture(
                                WifiDiagnostics.REPORT_REASON_UNEXPECTED_DISCONNECT);
                    }

                    if (!eventInfo.locallyGenerated) {
                        // ignore disconnects initiated by wpa_supplicant.
                        mWifiScoreCard.noteNonlocalDisconnect(mInterfaceName, eventInfo.reasonCode);
                        int rssi = mWifiInfo.getRssi();
                        mWifiBlocklistMonitor.handleBssidConnectionFailure(mWifiInfo.getBSSID(),
                                getConnectedWifiConfiguration(),
                                WifiBlocklistMonitor.REASON_ABNORMAL_DISCONNECT, rssi);
                    }
                    WifiConfiguration config = getConnectedWifiConfigurationInternal();

                    if (mVerboseLoggingEnabled) {
                        log("NETWORK_DISCONNECTION_EVENT in connected state"
                                + " BSSID=" + mWifiInfo.getBSSID()
                                + " RSSI=" + mWifiInfo.getRssi()
                                + " freq=" + mWifiInfo.getFrequency()
                                + " reason=" + eventInfo.reasonCode
                                + " Network Selection Status=" + (config == null ? "Unavailable"
                                : config.getNetworkSelectionStatus().getNetworkStatusString()));
                    }
                    handleNetworkDisconnect(false, eventInfo.reasonCode);
                    transitionTo(mDisconnectedState);
                    break;
                }
                case CMD_START_ROAM: {
                    /* Connect command coming from auto-join */
                    int netId = message.arg1;
                    String bssid = (String) message.obj;
                    if (bssid == null) {
                        bssid = SUPPLICANT_BSSID_ANY;
                    }
                    WifiConfiguration config =
                            mWifiConfigManager.getConfiguredNetworkWithoutMasking(netId);
                    if (config == null) {
                        loge("CMD_START_ROAM and no config, bail out...");
                        break;
                    }
                    mLastScanRssi = mWifiConfigManager.findScanRssi(netId,
                            mWifiHealthMonitor.getScanRssiValidTimeMs());
                    mWifiScoreCard.noteConnectionAttempt(mWifiInfo, mLastScanRssi, config.SSID);
                    setTargetBssid(config, bssid);
                    mTargetNetworkId = netId;
                    mWifiPseudonymManager.enableStrictConservativePeerModeIfSupported(config);
                    logd("CMD_START_ROAM sup state "
                            + " my state " + getCurrentState().getName()
                            + " nid=" + Integer.toString(netId)
                            + " config " + config.getProfileKey()
                            + " targetRoamBSSID " + mTargetBssid);

                    reportConnectionAttemptStart(config, mTargetBssid,
                            WifiMetricsProto.ConnectionEvent.ROAM_ENTERPRISE, Process.WIFI_UID);
                    if (mWifiNative.roamToNetwork(mInterfaceName, config)) {
                        mTargetWifiConfiguration = config;
                        mIsAutoRoaming = true;
                        mWifiMetrics.logStaEvent(
                                mInterfaceName, StaEvent.TYPE_CMD_START_ROAM, config);
                        transitionTo(mRoamingState);
                    } else {
                        loge("CMD_START_ROAM Failed to start roaming to network " + config);
                        reportConnectionAttemptEnd(
                                WifiMetrics.ConnectionEvent.FAILURE_CONNECT_NETWORK_FAILED,
                                WifiMetricsProto.ConnectionEvent.HLF_NONE,
                                WifiMetricsProto.ConnectionEvent.FAILURE_REASON_UNKNOWN, 0);
                        mMessageHandlingStatus = MESSAGE_HANDLING_STATUS_FAIL;
                        break;
                    }
                    break;
                }
                case CMD_IP_CONFIGURATION_LOST: {
                    mWifiMetrics.incrementIpRenewalFailure();
                    handleStatus = NOT_HANDLED;
                    break;
                }
                default: {
                    handleStatus = NOT_HANDLED;
                    break;
                }
            }

            if (handleStatus == HANDLED) {
                logStateAndMessage(message, this);
            }

            return handleStatus;
        }

        @Override
        public void exitImpl() {
            logd("ClientModeImpl: Leaving Connected state");
            mWifiConnectivityManager.handleConnectionStateChanged(
                    mClientModeManager,
                     WifiConnectivityManager.WIFI_STATE_TRANSITIONING);

            mWifiLastResortWatchdog.connectedStateTransition(false);
        }
    }

    class DisconnectedState extends RunnerState {
        DisconnectedState(int threshold) {
            super(threshold, mWifiInjector.getWifiHandlerLocalLog());
        }

        @Override
        public void enterImpl() {
            Log.i(getTag(), "disconnectedstate enter");
            // We don't scan frequently if this is a temporary disconnect
            // due to p2p
            if (mWifiP2pConnection.shouldTemporarilyDisconnectWifi()) {
                // TODO(b/161569371): P2P should wait for all ClientModeImpls to enter
                //  DisconnectedState, not just one instance.
                // (Does P2P Service support STA+P2P concurrency?)
                mWifiP2pConnection.sendMessage(WifiP2pServiceImpl.DISCONNECT_WIFI_RESPONSE);
                return;
            }

            if (mVerboseLoggingEnabled) {
                logd(" Enter DisconnectedState screenOn=" + mScreenOn);
            }

            /** clear the roaming state, if we were roaming, we failed */
            mIsAutoRoaming = false;
            mTargetNetworkId = WifiConfiguration.INVALID_NETWORK_ID;

            mWifiConnectivityManager.handleConnectionStateChanged(
                    mClientModeManager,
                    WifiConnectivityManager.WIFI_STATE_DISCONNECTED);

            if (mDeviceConfigFacade.isOobPseudonymEnabled()) {
                if (mVerboseLoggingEnabled) {
                    logd("unregister PseudonymUpdatingListener");
                }
                // unregister it any way, if it was not registered, it's no OP.
                mWifiPseudonymManager
                        .unregisterPseudonymUpdatingListener(mPseudonymUpdatingListener);
            }
        }

        @Override
        public String getMessageLogRec(int what) {
            return ClientModeImpl.class.getSimpleName() + "."
                    + DisconnectedState.class.getSimpleName() + "." + getWhatToString(what);
        }

        @Override
        public boolean processMessageImpl(Message message) {
            boolean handleStatus = HANDLED;

            switch (message.what) {
                case CMD_RECONNECT:
                case CMD_REASSOCIATE: {
                    if (mWifiP2pConnection.shouldTemporarilyDisconnectWifi()) {
                        // Drop a third party reconnect/reassociate if STA is
                        // temporarily disconnected for p2p
                        break;
                    } else {
                        // ConnectableState handles it
                        handleStatus = NOT_HANDLED;
                    }
                    break;
                }
                default: {
                    handleStatus = NOT_HANDLED;
                    break;
                }
            }

            if (handleStatus == HANDLED) {
                logStateAndMessage(message, this);
            }
            return handleStatus;
        }

        @Override
        public void exitImpl() {
            mWifiConnectivityManager.handleConnectionStateChanged(
                    mClientModeManager,
                     WifiConnectivityManager.WIFI_STATE_TRANSITIONING);
        }
    }

    void handleGsmAuthRequest(SimAuthRequestData requestData) {
        WifiConfiguration requestingWifiConfiguration = null;
        if (mTargetWifiConfiguration != null
                && mTargetWifiConfiguration.networkId
                == requestData.networkId) {
            requestingWifiConfiguration = mTargetWifiConfiguration;
            logd("id matches targetWifiConfiguration");
        } else if (mLastNetworkId != WifiConfiguration.INVALID_NETWORK_ID
                && mLastNetworkId == requestData.networkId) {
            requestingWifiConfiguration = getConnectedWifiConfigurationInternal();
            logd("id matches currentWifiConfiguration");
        }

        if (requestingWifiConfiguration == null) {
            logd("GsmAuthRequest received with null target/current WifiConfiguration.");
            return;
        }

        /*
         * Try authentication in the following order.
         *
         *    Standard       Cellular_auth     Type Command
         *
         * 1. 3GPP TS 31.102 3G_authentication [Length][RAND][Length][AUTN]
         *                            [Length][RES][Length][CK][Length][IK] and more
         * 2. 3GPP TS 31.102 2G_authentication [Length][RAND]
         *                            [Length][SRES][Length][Cipher Key Kc]
         * 3. 3GPP TS 11.11  2G_authentication [RAND]
         *                            [SRES][Cipher Key Kc]
         */
        String response = mWifiCarrierInfoManager
                .getGsmSimAuthResponse(requestData.data, requestingWifiConfiguration);
        if (response == null) {
            // In case of failure, issue may be due to sim type, retry as No.2 case
            response = mWifiCarrierInfoManager
                    .getGsmSimpleSimAuthResponse(requestData.data, requestingWifiConfiguration);
            if (response == null) {
                // In case of failure, issue may be due to sim type, retry as No.3 case
                response = mWifiCarrierInfoManager.getGsmSimpleSimNoLengthAuthResponse(
                                requestData.data, requestingWifiConfiguration);
            }
        }
        if (response == null || response.length() == 0) {
            mWifiNative.simAuthFailedResponse(mInterfaceName);
        } else {
            logv("Supplicant Response -" + response);
            mWifiNative.simAuthResponse(
                    mInterfaceName, WifiNative.SIM_AUTH_RESP_TYPE_GSM_AUTH, response);
        }
    }

    void handle3GAuthRequest(SimAuthRequestData requestData) {
        WifiConfiguration requestingWifiConfiguration = null;
        if (mTargetWifiConfiguration != null
                && mTargetWifiConfiguration.networkId
                == requestData.networkId) {
            requestingWifiConfiguration = mTargetWifiConfiguration;
            logd("id matches targetWifiConfiguration");
        } else if (mLastNetworkId != WifiConfiguration.INVALID_NETWORK_ID
                && mLastNetworkId == requestData.networkId) {
            requestingWifiConfiguration = getConnectedWifiConfigurationInternal();
            logd("id matches currentWifiConfiguration");
        }

        if (requestingWifiConfiguration == null) {
            logd("3GAuthRequest received with null target/current WifiConfiguration.");
            return;
        }

        SimAuthResponseData response = mWifiCarrierInfoManager
                .get3GAuthResponse(requestData, requestingWifiConfiguration);
        if (response != null) {
            mWifiNative.simAuthResponse(
                    mInterfaceName, response.type, response.response);
        } else {
            mWifiNative.umtsAuthFailedResponse(mInterfaceName);
        }
    }

    /**
     * Automatically connect to the network specified
     *
     * @param networkId ID of the network to connect to
     * @param uid UID of the app triggering the connection.
     * @param bssid BSSID of the network
     */
    public void startConnectToNetwork(int networkId, int uid, String bssid) {
        sendMessage(CMD_START_CONNECT, networkId, uid, bssid);
    }

    /**
     * Automatically roam to the network specified
     *
     * @param networkId ID of the network to roam to
     * @param bssid BSSID of the access point to roam to.
     */
    public void startRoamToNetwork(int networkId, String bssid) {
        sendMessage(CMD_START_ROAM, networkId, 0, bssid);
    }

    /**
     * @param reason reason code from supplicant on network disconnected event
     * @return true if this is a suspicious disconnect
     */
    static boolean unexpectedDisconnectedReason(int reason) {
        return reason == StaIfaceReasonCode.PREV_AUTH_NOT_VALID
                || reason == StaIfaceReasonCode.CLASS2_FRAME_FROM_NONAUTH_STA
                || reason == StaIfaceReasonCode.CLASS3_FRAME_FROM_NONASSOC_STA
                || reason == StaIfaceReasonCode.DISASSOC_STA_HAS_LEFT
                || reason == StaIfaceReasonCode.STA_REQ_ASSOC_WITHOUT_AUTH
                || reason == StaIfaceReasonCode.MICHAEL_MIC_FAILURE
                || reason == StaIfaceReasonCode.FOURWAY_HANDSHAKE_TIMEOUT
                || reason == StaIfaceReasonCode.GROUP_KEY_UPDATE_TIMEOUT
                || reason == StaIfaceReasonCode.GROUP_CIPHER_NOT_VALID
                || reason == StaIfaceReasonCode.PAIRWISE_CIPHER_NOT_VALID
                || reason == StaIfaceReasonCode.IEEE_802_1X_AUTH_FAILED
                || reason == StaIfaceReasonCode.DISASSOC_LOW_ACK;
    }

    private static String getLinkPropertiesSummary(LinkProperties lp) {
        List<String> attributes = new ArrayList<>(6);
        if (lp.hasIpv4Address()) {
            attributes.add("v4");
        }
        if (lp.hasIpv4DefaultRoute()) {
            attributes.add("v4r");
        }
        if (lp.hasIpv4DnsServer()) {
            attributes.add("v4dns");
        }
        if (lp.hasGlobalIpv6Address()) {
            attributes.add("v6");
        }
        if (lp.hasIpv6DefaultRoute()) {
            attributes.add("v6r");
        }
        if (lp.hasIpv6DnsServer()) {
            attributes.add("v6dns");
        }

        return TextUtils.join(" ", attributes);
    }

    /**
     * Gets the SSID from the WifiConfiguration pointed at by 'mTargetNetworkId'
     * This should match the network config framework is attempting to connect to.
     */
    private String getConnectingSsidInternal() {
        WifiConfiguration config = getConnectingWifiConfigurationInternal();
        return config != null ? config.SSID : null;
    }

    /**
     * Check if there is any connection request for WiFi network.
     */
    private boolean hasConnectionRequests() {
        return mNetworkFactory.hasConnectionRequests()
                || mUntrustedNetworkFactory.hasConnectionRequests()
                || mOemWifiNetworkFactory.hasConnectionRequests()
                || mRestrictedWifiNetworkFactory.hasConnectionRequests()
                || mMultiInternetManager.hasPendingConnectionRequests();
    }

    /**
     * Retrieve the factory MAC address from config store (stored on first bootup). If we don't have
     * a factory MAC address stored in config store, retrieve it now and store it.
     *
     * Note:
     * <li> Retries added to deal with any transient failures when invoking
     * {@link WifiNative#getStaFactoryMacAddress(String)}.
     */
    @Nullable
    private MacAddress retrieveFactoryMacAddressAndStoreIfNecessary() {
        boolean saveFactoryMacInConfigStore =
                mWifiGlobals.isSaveFactoryMacToConfigStoreEnabled();
        if (saveFactoryMacInConfigStore) {
            // Already present, just return.
            String factoryMacAddressStr = mSettingsConfigStore.get(isPrimary()
                    ? WIFI_STA_FACTORY_MAC_ADDRESS : SECONDARY_WIFI_STA_FACTORY_MAC_ADDRESS);
            if (factoryMacAddressStr != null) return MacAddress.fromString(factoryMacAddressStr);
        }
        MacAddress factoryMacAddress = mWifiNative.getStaFactoryMacAddress(mInterfaceName);
        if (factoryMacAddress == null) {
            // the device may be running an older HAL (version < 1.3).
            Log.w(TAG, (isPrimary() ? "Primary" : "Secondary")
                    + " failed to retrieve factory MAC address");
            return null;
        }
        if (saveFactoryMacInConfigStore) {
            mSettingsConfigStore.put(isPrimary()
                            ? WIFI_STA_FACTORY_MAC_ADDRESS : SECONDARY_WIFI_STA_FACTORY_MAC_ADDRESS,
                    factoryMacAddress.toString());
            Log.i(TAG, (isPrimary() ? "Primary" : "Secondary")
                    + " factory MAC address stored in config store: " + factoryMacAddress);
        }
        Log.i(TAG, (isPrimary() ? "Primary" : "Secondary")
                + " factory MAC address retrieved: " + factoryMacAddress);
        return factoryMacAddress;
    }

    /**
     * Gets the factory MAC address of wlan0 (station interface).
     * @return String representation of the factory MAC address.
     */
    @Nullable
    public String getFactoryMacAddress() {
        MacAddress factoryMacAddress = retrieveFactoryMacAddressAndStoreIfNecessary();
        if (factoryMacAddress != null) return factoryMacAddress.toString();

        // For devices with older HAL's (version < 1.3), no API exists to retrieve factory MAC
        // address (and also does not support MAC randomization - needs verson 1.2). So, just
        // return the regular MAC address from the interface.
        if (!mWifiGlobals.isConnectedMacRandomizationEnabled()) {
            Log.w(TAG, "Can't get factory MAC address, return the MAC address");
            return mWifiNative.getMacAddress(mInterfaceName);
        }
        return null;
    }

    /** Sends a link probe. */
    public void probeLink(LinkProbeCallback callback, int mcs) {
        String bssid = mWifiInfo.getBSSID();
        if (bssid == null) {
            Log.w(getTag(), "Attempted to send link probe when not connected!");
            callback.onFailure(LinkProbeCallback.LINK_PROBE_ERROR_NOT_CONNECTED);
            return;
        }
        mWifiNative.probeLink(mInterfaceName, MacAddress.fromString(bssid), callback, mcs);
    }

    private static class ConnectNetworkMessage {
        public final NetworkUpdateResult result;
        public final ActionListenerWrapper listener;
        public final String packageName;
        public final String attributionTag;

        ConnectNetworkMessage(NetworkUpdateResult result, ActionListenerWrapper listener,
                String packageName, @Nullable String attributionTag) {
            this.result = result;
            this.listener = listener;
            this.packageName = packageName;
            this.attributionTag = attributionTag;
        }
    }

    /** Trigger network connection and provide status via the provided callback. */
    public void connectNetwork(NetworkUpdateResult result, ActionListenerWrapper wrapper,
            int callingUid, @NonNull String packageName, @Nullable String attributionTag) {
        Message message =
                obtainMessage(CMD_CONNECT_NETWORK,
                        new ConnectNetworkMessage(result, wrapper, packageName, attributionTag));
        message.sendingUid = callingUid;
        sendMessage(message);
    }

    /** Trigger network save and provide status via the provided callback. */
    public void saveNetwork(NetworkUpdateResult result, ActionListenerWrapper wrapper,
            int callingUid, @NonNull String packageName) {
        Message message =
                obtainMessage(CMD_SAVE_NETWORK,
                        new ConnectNetworkMessage(result, wrapper, packageName, null));
        message.sendingUid = callingUid;
        sendMessage(message);
    }

    /**
     * Handle BSS transition request from Connected BSS.
     *
     * @param frameData Data retrieved from received BTM request frame.
     */
    private void handleBssTransitionRequest(BtmFrameData frameData) {
        if (frameData == null) {
            return;
        }

        String bssid = mWifiInfo.getBSSID();
        String ssid = mWifiInfo.getSSID();
        if ((bssid == null) || (ssid == null) || WifiManager.UNKNOWN_SSID.equals(ssid)) {
            Log.e(getTag(), "Failed to handle BSS transition: bssid: " + bssid + " ssid: " + ssid);
            return;
        }

        mWifiMetrics.incrementSteeringRequestCount();

        if ((frameData.mBssTmDataFlagsMask
                & MboOceConstants.BTM_DATA_FLAG_MBO_CELL_DATA_CONNECTION_PREFERENCE_INCLUDED)
                != 0) {
            mWifiMetrics.incrementMboCellularSwitchRequestCount();
        }


        if ((frameData.mBssTmDataFlagsMask
                & MboOceConstants.BTM_DATA_FLAG_DISASSOCIATION_IMMINENT) != 0) {
            long duration = 0;
            if ((frameData.mBssTmDataFlagsMask
                    & MboOceConstants.BTM_DATA_FLAG_MBO_ASSOC_RETRY_DELAY_INCLUDED) != 0) {
                mWifiMetrics.incrementSteeringRequestCountIncludingMboAssocRetryDelay();
                duration = frameData.mBlockListDurationMs;
            }
            if (duration == 0) {
                /*
                 * When disassoc imminent bit alone is set or MBO assoc retry delay is
                 * set to zero(reserved as per spec), blocklist the BSS for sometime to
                 * avoid AP rejecting the re-connect request.
                 */
                duration = MboOceConstants.DEFAULT_BLOCKLIST_DURATION_MS;
            }
            // Blocklist the current BSS
            WifiConfiguration config = getConnectedWifiConfiguration();
            if (config == null) {
                config = getConnectingWifiConfiguration();
            }
            mWifiBlocklistMonitor.blockBssidForDurationMs(bssid, config, duration,
                    WifiBlocklistMonitor.REASON_FRAMEWORK_DISCONNECT_MBO_OCE, 0);
        }

        if (frameData.mStatus != MboOceConstants.BTM_RESPONSE_STATUS_ACCEPT) {
            // Trigger the network selection and re-connect to new network if available.
            mWifiMetrics.incrementForceScanCountDueToSteeringRequest();
            mWifiConnectivityManager.forceConnectivityScan(ClientModeImpl.WIFI_WORK_SOURCE);
        }
    }

    /**
     * @return true if this device supports FILS-SHA256
     */
    private boolean isFilsSha256Supported() {
        return (getSupportedFeatures() & WIFI_FEATURE_FILS_SHA256) != 0;
    }

    /**
     * @return true if this device supports FILS-SHA384
     */
    private boolean isFilsSha384Supported() {
        return (getSupportedFeatures() & WIFI_FEATURE_FILS_SHA384) != 0;
    }

    /**
     * @return true if this device supports Trust On First Use
     */
    private boolean isTrustOnFirstUseSupported() {
        return (getSupportedFeatures() & WIFI_FEATURE_TRUST_ON_FIRST_USE) != 0;
    }

    /**
     * Helper method to set the allowed key management schemes from
     * scan result.
     * When the AKM is updated, changes should be propagated to the
     * actual saved network, and the correct AKM could be retrieved
     * on selecting the security params.
     */
    private void updateAllowedKeyManagementSchemesFromScanResult(
            WifiConfiguration config, ScanResult scanResult) {
        config.enableFils(
                isFilsSha256Supported()
                && ScanResultUtil.isScanResultForFilsSha256Network(scanResult),
                isFilsSha384Supported()
                && ScanResultUtil.isScanResultForFilsSha384Network(scanResult));
        mWifiConfigManager.updateFilsAkms(config.networkId,
                config.isFilsSha256Enabled(), config.isFilsSha384Enabled());
    }
    /**
     * Update wifi configuration based on the matching scan result.
     *
     * @param config Wifi configuration object.
     * @param scanResult Scan result matching the network.
     */
    private void updateWifiConfigFromMatchingScanResult(WifiConfiguration config,
            ScanResult scanResult) {
        updateAllowedKeyManagementSchemesFromScanResult(config, scanResult);
        if (config.isFilsSha256Enabled() || config.isFilsSha384Enabled()) {
            config.enterpriseConfig.setFieldValue(WifiEnterpriseConfig.EAP_ERP, "1");
        }
    }

    private void selectCandidateSecurityParamsIfNecessary(
            WifiConfiguration config,
            List<ScanResult> scanResults) {
        if (null != config.getNetworkSelectionStatus().getCandidateSecurityParams()) return;
        if (mVerboseLoggingEnabled) {
            Log.d(getTag(), "Select candidate security params for " + config.getProfileKey());
        }

        // This comes from wifi picker directly so there is no candidate security params.
        // Run network selection against this SSID.
        List<ScanDetail> scanDetailsList = scanResults.stream()
                .filter(scanResult -> config.SSID.equals(
                        ScanResultUtil.createQuotedSsid(scanResult.SSID)))
                .map(ScanDetail::new)
                .collect(Collectors.toList());
        List<WifiCandidates.Candidate> candidates = mWifiNetworkSelector
                .getCandidatesForUserSelection(config, scanDetailsList);
        mWifiNetworkSelector.selectNetwork(candidates);

        SecurityParams params = null;
        // Get the fresh copy again to retrieve the candidate security params.
        WifiConfiguration freshConfig = mWifiConfigManager.getConfiguredNetwork(config.networkId);
        if (null != freshConfig
                && null != freshConfig.getNetworkSelectionStatus().getCandidateSecurityParams()) {
            params = freshConfig.getNetworkSelectionStatus().getCandidateSecurityParams();
            Log.i(getTag(), "Select best-fit security params: " + params.getSecurityType());
        } else if (null != config.getNetworkSelectionStatus().getLastUsedSecurityParams()
                && config.getNetworkSelectionStatus().getLastUsedSecurityParams().isEnabled()) {
            params = config.getNetworkSelectionStatus().getLastUsedSecurityParams();
            Log.i(getTag(), "Select the last used security params: " + params.getSecurityType());
        } else {
            params = config.getSecurityParamsList().stream()
                    .filter(WifiConfigurationUtil::isSecurityParamsValid)
                    .findFirst().orElse(null);
            if (null != params) {
                Log.i(getTag(), "Select the first available security params: "
                        + params.getSecurityType());
            } else {
                Log.w(getTag(), "No available security params.");
            }
        }

        config.getNetworkSelectionStatus().setCandidateSecurityParams(params);
        // populate the target security params to the internal configuration manually,
        // and then wifi info could retrieve this information.
        mWifiConfigManager.setNetworkCandidateScanResult(
                config.networkId,
                freshConfig == null ? null : freshConfig.getNetworkSelectionStatus().getCandidate(),
                0, params);
    }

    /**
     * Update the wifi configuration before sending connect to
     * supplicant/driver.
     *
     * @param config wifi configuration object.
     * @param bssid BSSID to assocaite with.
     */
    void updateWifiConfigOnStartConnection(WifiConfiguration config, String bssid) {
        setTargetBssid(config, bssid);

        // Go through the matching scan results and update wifi config.
        ScanResultMatchInfo key1 = ScanResultMatchInfo.fromWifiConfiguration(config);
        List<ScanResult> scanResults = mScanRequestProxy.getScanResults();
        for (ScanResult scanResult : scanResults) {
            if (!config.SSID.equals(ScanResultUtil.createQuotedSsid(scanResult.SSID))) {
                continue;
            }
            ScanResultMatchInfo key2 = ScanResultMatchInfo.fromScanResult(scanResult);
            if (!key1.equals(key2)) {
                continue;
            }
            updateWifiConfigFromMatchingScanResult(config, scanResult);
        }

        selectCandidateSecurityParamsIfNecessary(config, scanResults);

        if (mWifiGlobals.isConnectedMacRandomizationEnabled()) {
            boolean isMacRandomizationForceDisabled = isMacRandomizationForceDisabledOnSsid(config);
            if (config.macRandomizationSetting == WifiConfiguration.RANDOMIZATION_NONE
                    || isMacRandomizationForceDisabled) {
                setCurrentMacToFactoryMac(config);
            } else {
                configureRandomizedMacAddress(config);
            }
            if (isMacRandomizationForceDisabled
                    && config.macRandomizationSetting != WifiConfiguration.RANDOMIZATION_NONE) {
                // update WifiConfigManager to disable MAC randomization so Settings show the right
                // MAC randomization information.
                config.macRandomizationSetting = WifiConfiguration.RANDOMIZATION_NONE;
                mWifiConfigManager.addOrUpdateNetwork(config, Process.SYSTEM_UID);
            }
        }

        if (config.enterpriseConfig != null
                && config.enterpriseConfig.isAuthenticationSimBased()
                && mWifiCarrierInfoManager.isImsiEncryptionInfoAvailable(
                mWifiCarrierInfoManager.getBestMatchSubscriptionId(config))
                && TextUtils.isEmpty(config.enterpriseConfig.getAnonymousIdentity())) {
            String anonAtRealm = mWifiCarrierInfoManager
                    .getAnonymousIdentityWith3GppRealm(config);
            // Use anonymous@<realm> when pseudonym is not available
            config.enterpriseConfig.setAnonymousIdentity(anonAtRealm);
        }
    }

    private boolean isMacRandomizationForceDisabledOnSsid(WifiConfiguration config) {
        Set<String> unsupportedSsids = new ArraySet<>(mContext.getResources().getStringArray(
                R.array.config_wifiForceDisableMacRandomizationSsidList));
        Set<String> unsupportedPrefixes = mWifiGlobals.getMacRandomizationUnsupportedSsidPrefixes();
        boolean isUnsupportedByPrefix =
                unsupportedPrefixes.stream().anyMatch(ssid -> config.SSID.startsWith(ssid));
        return isUnsupportedByPrefix || unsupportedSsids.contains(config.SSID);
    }

    private void setConfigurationsPriorToIpClientProvisioning(WifiConfiguration config) {
        mIpClient.setHttpProxy(config.getHttpProxy());
        if (!TextUtils.isEmpty(mContext.getResources().getString(
                R.string.config_wifi_tcp_buffers))) {
            mIpClient.setTcpBufferSizes(mContext.getResources().getString(
                    R.string.config_wifi_tcp_buffers));
        }
    }

    private boolean startIpClient(WifiConfiguration config, boolean isFilsConnection) {
        if (mIpClient == null || config == null) {
            return false;
        }

        final boolean isUsingStaticIp =
                (config.getIpAssignment() == IpConfiguration.IpAssignment.STATIC);
        final boolean isUsingMacRandomization =
                config.macRandomizationSetting
                        != WifiConfiguration.RANDOMIZATION_NONE
                        && mWifiGlobals.isConnectedMacRandomizationEnabled();
        final List<byte[]> ouis = getOuiInternal(config);
        final List<android.net.DhcpOption> options =
                mWifiConfigManager.getCustomDhcpOptions(WifiSsid.fromString(config.SSID), ouis);
        if (mVerboseLoggingEnabled) {
            final String key = config.getProfileKey();
            log("startIpClient netId=" + Integer.toString(mLastNetworkId)
                    + " " + key + " "
                    + " roam=" + mIsAutoRoaming
                    + " static=" + isUsingStaticIp
                    + " randomMac=" + isUsingMacRandomization
                    + " isFilsConnection=" + isFilsConnection);
        }

        final MacAddress currentBssid = getCurrentBssidInternalMacAddress();
        final String l2Key = mLastL2KeyAndGroupHint != null
                ? mLastL2KeyAndGroupHint.first : null;
        final String groupHint = mLastL2KeyAndGroupHint != null
                ? mLastL2KeyAndGroupHint.second : null;
        final Layer2Information layer2Info = new Layer2Information(l2Key, groupHint,
                currentBssid);

        final ProvisioningConfiguration.Builder prov =
                new ProvisioningConfiguration.Builder()
                        .withDisplayName(config.SSID)
                        .withCreatorUid(config.creatorUid)
                        .withLayer2Information(layer2Info)
                        .withDhcpOptions(convertToInternalDhcpOptions(options));
        if (isUsingMacRandomization) {
            // Use EUI64 address generation for link-local IPv6 addresses.
            prov.withRandomMacAddress();
        }
        if (mContext.getResources().getBoolean(R.bool.config_wifiEnableApfOnNonPrimarySta)
                || isPrimary()) {
            // unclear if the native layer will return the correct non-capabilities if APF is
            // not supported on secondary interfaces.
            prov.withApfCapabilities(mWifiNative.getApfCapabilities(mInterfaceName));
        }
        if (SdkLevel.isAtLeastV()) {
            // Set the user dhcp hostname setting.
            int hostnameSetting = config.isSendDhcpHostnameEnabled()
                    ? IIpClient.HOSTNAME_SETTING_SEND
                    : IIpClient.HOSTNAME_SETTING_DO_NOT_SEND;
            int restrictions = mWifiGlobals.getSendDhcpHostnameRestriction();
            // Override the user setting the dhcp hostname restrictions.
            if (config.isOpenNetwork()) {
                if ((restrictions
                        & WifiManager.FLAG_SEND_DHCP_HOSTNAME_RESTRICTION_OPEN) != 0) {
                    hostnameSetting = IIpClient.HOSTNAME_SETTING_DO_NOT_SEND;
                }
            } else {
                if ((restrictions
                        & WifiManager.FLAG_SEND_DHCP_HOSTNAME_RESTRICTION_SECURE) != 0) {
                    hostnameSetting = IIpClient.HOSTNAME_SETTING_DO_NOT_SEND;
                }
            }
            prov.withHostnameSetting(hostnameSetting);
        }
        if (isFilsConnection) {
            stopIpClient();
            if (isUsingStaticIp) {
                mWifiNative.flushAllHlp(mInterfaceName);
                return false;
            }
            setConfigurationsPriorToIpClientProvisioning(config);
            prov.withPreconnection()
                    .withPreDhcpAction()
                    .withProvisioningTimeoutMs(PROVISIONING_TIMEOUT_FILS_CONNECTION_MS);
        } else {
            sendNetworkChangeBroadcast(DetailedState.OBTAINING_IPADDR);
            // We must clear the config BSSID, as the wifi chipset may decide to roam
            // from this point on and having the BSSID specified in the network block would
            // cause the roam to fail and the device to disconnect.
            clearTargetBssid("ObtainingIpAddress");

            // Stop IpClient in case we're switching from DHCP to static
            // configuration or vice versa.
            //
            // When we transition from static configuration to DHCP in
            // particular, we must tell ConnectivityService that we're
            // disconnected, because DHCP might take a long time during which
            // connectivity APIs such as getActiveNetworkInfo should not return
            // CONNECTED.
            stopDhcpSetup();
            setConfigurationsPriorToIpClientProvisioning(config);

            final Network network = (mNetworkAgent != null) ? mNetworkAgent.getNetwork() : null;
            prov.withNetwork(network);
            if (!isUsingStaticIp) {
                ProvisioningConfiguration.ScanResultInfo scanResultInfo = null;
                ScanResult scanResult = getScanResultInternal(config);
                if (scanResult != null) {
                    final List<ScanResultInfo.InformationElement> ies =
                            new ArrayList<ScanResultInfo.InformationElement>();
                    for (ScanResult.InformationElement ie : scanResult.getInformationElements()) {
                        ScanResultInfo.InformationElement scanResultInfoIe =
                                new ScanResultInfo.InformationElement(ie.getId(), ie.getBytes());
                        ies.add(scanResultInfoIe);
                    }
                    scanResultInfo = new ProvisioningConfiguration.ScanResultInfo(scanResult.SSID,
                            scanResult.BSSID, ies);
                }
                prov.withScanResultInfo(scanResultInfo)
                        .withPreDhcpAction();
            } else {
                StaticIpConfiguration staticIpConfig = config.getStaticIpConfiguration();
                prov.withStaticConfiguration(staticIpConfig)
                        .withoutIpReachabilityMonitor();
            }
        }
        if (mContext.getResources().getBoolean(
                R.bool.config_wifiRemainConnectedAfterIpProvisionTimeout)) {
            prov.withProvisioningTimeoutMs(0);
        }
        mIpClient.startProvisioning(prov.build());
        return true;
    }

    private List<byte[]> getOuiInternal(WifiConfiguration config) {
        List<byte[]> ouis = new ArrayList<>();
        ScanResult scanResult = getScanResultInternal(config);
        if (scanResult == null) {
            return ouis;
        }
        List<InformationElementUtil.Vsa> vsas = InformationElementUtil.getVendorSpecificIE(
                scanResult.informationElements);
        for (InformationElementUtil.Vsa vsa : vsas) {
            byte[] oui = vsa.oui;
            if (oui != null) {
                ouis.add(oui);
            }
        }
        return ouis;
    }

    private ScanResult getScanResultInternal(WifiConfiguration config) {
        ScanDetailCache scanDetailCache =
                mWifiConfigManager.getScanDetailCacheForNetwork(config.networkId);
        ScanResult scanResult = null;
        if (mLastBssid != null) {
            if (scanDetailCache != null) {
                scanResult = scanDetailCache.getScanResult(mLastBssid);
            }
            // The cached scan result of connected network would be null at the first
            // connection, try to check full scan result list again to look up matched
            // scan result associated to the current BSSID.
            if (scanResult == null) {
                scanResult = mScanRequestProxy.getScanResult(mLastBssid);
            }
        }
        return scanResult;
    }

    @Override
    public boolean setWifiConnectedNetworkScorer(IBinder binder,
            IWifiConnectedNetworkScorer scorer, int callerUid) {
        return mWifiScoreReport.setWifiConnectedNetworkScorer(binder, scorer, callerUid);
    }

    @Override
    public void clearWifiConnectedNetworkScorer() {
        mWifiScoreReport.clearWifiConnectedNetworkScorer();
    }

    @Override
    public void onNetworkSwitchAccepted(int targetNetworkId, String targetBssid) {
        mWifiScoreReport.onNetworkSwitchAccepted(targetNetworkId, targetBssid);
    }

    @Override
    public void onNetworkSwitchRejected(int targetNetworkId, String targetBssid) {
        mWifiScoreReport.onNetworkSwitchRejected(targetNetworkId, targetBssid);
    }

    @Override
    public void sendMessageToClientModeImpl(Message msg) {
        sendMessage(msg);
    }

    @Override
    public long getId() {
        return mId;
    }

    @Override
    public void dumpWifiScoreReport(FileDescriptor fd, PrintWriter pw, String[] args) {
        mWifiScoreReport.dump(fd, pw, args);
    }

    /**
     * Notifies changes in data connectivity of the default data SIM.
     */
    @Override
    public void onCellularConnectivityChanged(@WifiDataStall.CellularDataStatusCode int status) {
        mWifiConfigManager.onCellularConnectivityChanged(status);
        // do a scan if no cell data and currently not connect to wifi
        if (status == WifiDataStall.CELLULAR_DATA_NOT_AVAILABLE
                && getConnectedWifiConfigurationInternal() == null) {
            if (mContext.getResources().getBoolean(
                    R.bool.config_wifiScanOnCellularDataLossEnabled)) {
                mWifiConnectivityManager.forceConnectivityScan(WIFI_WORK_SOURCE);
            }
        }
    }

    @Override
    public void setMboCellularDataStatus(boolean available) {
        mWifiNative.setMboCellularDataStatus(mInterfaceName, available);
    }

    @Override
    public WifiNative.RoamingCapabilities getRoamingCapabilities() {
        return mWifiNative.getRoamingCapabilities(mInterfaceName);
    }

    @Override
    public boolean configureRoaming(WifiNative.RoamingConfig config) {
        return mWifiNative.configureRoaming(mInterfaceName, config);
    }

    @Override
    public boolean enableRoaming(boolean enabled) {
        int status = mWifiNative.enableFirmwareRoaming(
                mInterfaceName, enabled
                        ? WifiNative.ENABLE_FIRMWARE_ROAMING
                        : WifiNative.DISABLE_FIRMWARE_ROAMING);
        return status == WifiNative.SET_FIRMWARE_ROAMING_SUCCESS;
    }

    private void considerChangingFirmwareRoaming(boolean isIdle) {
        if (mClientModeManager.getRole() != ROLE_CLIENT_PRIMARY) {
            if (mVerboseLoggingEnabled) {
                Log.v(TAG, "Idle mode changed: iface " + mInterfaceName + " is not primary.");
            }
            return;
        }
        if (!mWifiGlobals.isDisableFirmwareRoamingInIdleMode()
                || !mWifiConnectivityHelper.isFirmwareRoamingSupported()) {
            // feature not enabled, or firmware roaming not supported - no need to continue.
            if (mVerboseLoggingEnabled) {
                Log.v(TAG, "Idle mode changed: iface " + mInterfaceName
                        + " firmware roaming not supported");
            }
            return;
        }
        if (isIdle) {
            // disable firmware roaming if in idle mode
            if (mVerboseLoggingEnabled) {
                Log.v(TAG, "Idle mode changed: iface " + mInterfaceName
                        + " disabling roaming");
            }
            enableRoaming(false);
            return;
        }
        // Exiting idle mode so re-enable firmware roaming, but only if the current use-case is
        // not the local-only use-case. The local-only use-case requires firmware roaming to be
        // always disabled.
        WifiConfiguration config = getConnectedWifiConfigurationInternal();
        if (config == null) {
            config = getConnectingWifiConfigurationInternal();
        }
        if (config != null && getClientRoleForMetrics(config)
                == WifiStatsLog.WIFI_CONNECTION_RESULT_REPORTED__ROLE__ROLE_CLIENT_LOCAL_ONLY) {
            return;
        }
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "Idle mode changed: iface " + mInterfaceName
                    + " enabling roaming");
        }
        mWifiInjector.getWifiRoamingModeManager().applyWifiRoamingMode(
                mInterfaceName, mWifiInfo.getSSID());
    }

    @Override
    public void onIdleModeChanged(boolean isIdle) {
        considerChangingFirmwareRoaming(isIdle);
    }

    @Override
    public boolean setCountryCode(String countryCode) {
        return mWifiNative.setStaCountryCode(mInterfaceName, countryCode);
    }

    @Override
    public List<TxFateReport> getTxPktFates() {
        return mWifiNative.getTxPktFates(mInterfaceName);
    }

    @Override
    public List<RxFateReport> getRxPktFates() {
        return mWifiNative.getRxPktFates(mInterfaceName);
    }

    @Override
    public void setShouldReduceNetworkScore(boolean shouldReduceNetworkScore) {
        mWifiScoreReport.setShouldReduceNetworkScore(shouldReduceNetworkScore);
    }

    private void updateApfCapabilitiesOnRoleChangedToPrimary() {
        // If packet filter is supported on both connections, ignore since we would have already
        // specified the APF during the ipClient provisioning.
        if (mContext.getResources().getBoolean(R.bool.config_wifiEnableApfOnNonPrimarySta)) {
            return;
        }
        if (mIpClient != null) {
            Log.i(TAG, "Role changed to primary - Update APF capabilities in IpClient");
            mIpClient.updateApfCapabilities(mWifiNative.getApfCapabilities(mInterfaceName));
        }
    }

    /**
     * Invoked by parent ConcreteClientModeManager whenever a role change occurs.
     */
    public void onRoleChanged() {
        ClientRole role = mClientModeManager.getRole();
        if (role == ROLE_CLIENT_PRIMARY) {
            updateApfCapabilitiesOnRoleChangedToPrimary();
            if (mScreenOn) {
                // Start RSSI polling for the new primary network to enable scoring.
                enableRssiPolling(true);
            }
        } else {
            if (mScreenOn && !isSecondaryInternet()) {
                // Stop RSSI polling (if enabled) for the secondary network.
                enableRssiPolling(false);
            }
        }
        WifiConfiguration connectedNetwork = getConnectedWifiConfiguration();
        if (connectedNetwork != null) {
            updateWifiInfoWhenConnected(connectedNetwork);
            // Update capabilities after a role change.
            updateCapabilities(connectedNetwork);
        }
        mWifiScoreReport.onRoleChanged(role);
    }

    private void addPasspointInfoToLinkProperties(LinkProperties linkProperties) {
        // CaptivePortalData.Builder.setVenueFriendlyName API not available on R
        if (!SdkLevel.isAtLeastS()) {
            return;
        }
        WifiConfiguration currentNetwork = getConnectedWifiConfigurationInternal();
        if (currentNetwork == null || !currentNetwork.isPasspoint()) {
            return;
        }
        ScanResult scanResult = mScanRequestProxy.getScanResult(mLastBssid);

        if (scanResult == null) {
            return;
        }
        URL venueUrl = mPasspointManager.getVenueUrl(scanResult);

        // Update the friendly name to populate the notification
        CaptivePortalData.Builder captivePortalDataBuilder = new CaptivePortalData.Builder()
                .setVenueFriendlyName(currentNetwork.providerFriendlyName);

        // Update the Venue URL if available
        if (venueUrl != null) {
            captivePortalDataBuilder.setVenueInfoUrl(Uri.parse(venueUrl.toString()),
                    CaptivePortalData.CAPTIVE_PORTAL_DATA_SOURCE_PASSPOINT);
        }

        // Update the T&C URL if available. The network is captive if T&C URL is available
        if (mTermsAndConditionsUrl != null) {
            captivePortalDataBuilder.setUserPortalUrl(
                    Uri.parse(mTermsAndConditionsUrl.toString()),
                    CaptivePortalData.CAPTIVE_PORTAL_DATA_SOURCE_PASSPOINT).setCaptive(true);
        }

        linkProperties.setCaptivePortalData(captivePortalDataBuilder.build());
    }

    private boolean mHasQuit = false;

    @Override
    protected void onQuitting() {
        mHasQuit = true;
        mClientModeManager.onClientModeImplQuit();
    }

    /** Returns true if the ClientModeImpl has fully stopped, false otherwise. */
    public boolean hasQuit() {
        return mHasQuit;
    }

    /**
     * WifiVcnNetworkPolicyChangeListener tracks VCN-defined Network policies for a
     * WifiNetworkAgent. These policies are used to restart Networks or update their
     * NetworkCapabilities.
     */
    @SuppressLint("NewApi")
    private class WifiVcnNetworkPolicyChangeListener
            implements VcnManager.VcnNetworkPolicyChangeListener {
        @Override
        public void onPolicyChanged() {
            if (mNetworkAgent == null) {
                return;
            }
            // Update the NetworkAgent's NetworkCapabilities which will merge the current
            // capabilities with VcnManagementService's underlying Network policy.
            Log.i(getTag(), "VCN policy changed, updating NetworkCapabilities.");
            updateCapabilities();
        }
    }

    /**
     * Updates the default gateway mac address of the connected network config and updates the
     * linked networks resulting from the new default gateway.
     */
    private boolean retrieveConnectedNetworkDefaultGateway() {
        WifiConfiguration currentConfig = getConnectedWifiConfiguration();
        if (currentConfig == null) {
            logi("can't fetch config of current network id " + mLastNetworkId);
            return false;
        }

        // Find IPv4 default gateway.
        if (mLinkProperties == null) {
            logi("cannot retrieve default gateway from null link properties");
            return false;
        }
        String gatewayIPv4 = null;
        for (RouteInfo routeInfo : mLinkProperties.getRoutes()) {
            if (routeInfo.isDefaultRoute()
                    && routeInfo.getDestination().getAddress() instanceof Inet4Address
                    && routeInfo.hasGateway()) {
                gatewayIPv4 = routeInfo.getGateway().getHostAddress();
                break;
            }
        }

        if (TextUtils.isEmpty(gatewayIPv4)) {
            logi("default gateway ipv4 is null");
            return false;
        }

        String gatewayMac = macAddressFromRoute(gatewayIPv4);
        if (TextUtils.isEmpty(gatewayMac)) {
            logi("default gateway mac fetch failed for ipv4 addr = " + gatewayIPv4);
            return false;
        }

        if (mVerboseLoggingEnabled) {
            logi("Default Gateway MAC address of " + mLastBssid + " from routes is : "
                    + gatewayMac);
        }
        if (!mWifiConfigManager.setNetworkDefaultGwMacAddress(mLastNetworkId, gatewayMac)) {
            logi("default gateway mac set failed for " + currentConfig.getKey() + " network");
            return false;
        }

        return mWifiConfigManager.saveToStore(true);
    }

    /**
     * Links the supplied config to all matching saved configs and updates the WifiBlocklistMonitor
     * SSID allowlist with the linked networks.
     */
    private void updateLinkedNetworks(@NonNull WifiConfiguration config) {
        if (!isPrimary()) {
            return;
        }
        if (!mContext.getResources().getBoolean(R.bool.config_wifiEnableLinkedNetworkRoaming)) {
            return;
        }

        SecurityParams params = mWifiNative.getCurrentNetworkSecurityParams(mInterfaceName);
        if (params == null) return;

        WifiConfiguration tmpConfigForCurrentSecurityParams = new WifiConfiguration();
        tmpConfigForCurrentSecurityParams.setSecurityParams(params);
        if (!WifiConfigurationUtil.isConfigLinkable(tmpConfigForCurrentSecurityParams)) return;

        // Don't set SSID allowlist if we're connected to a network with Fast BSS Transition.
        ScanDetailCache scanDetailCache = mWifiConfigManager.getScanDetailCacheForNetwork(
                config.networkId);
        if (scanDetailCache == null) {
            Log.i(TAG, "Do not update linked networks - no ScanDetailCache found for netId: "
                    + config.networkId);
            return;
        }
        ScanResult matchingScanResult = scanDetailCache.getScanResult(mLastBssid);
        if (matchingScanResult == null) {
            Log.i(TAG, "Do not update linked networks - no matching ScanResult found for BSSID: "
                    + mLastBssid);
            return;
        }
        String caps = matchingScanResult.capabilities;
        if (caps.contains("FT/PSK") || caps.contains("FT/SAE")) {
            Log.i(TAG, "Do not update linked networks - current connection is FT-PSK/FT-SAE");
            return;
        }

        mWifiConfigManager.updateLinkedNetworks(config.networkId);
        Map<String, WifiConfiguration> linkedNetworks = mWifiConfigManager
                .getLinkedNetworksWithoutMasking(config.networkId);

        if (!mWifiNative.updateLinkedNetworks(mInterfaceName, config.networkId, linkedNetworks)) {
            return;
        }
        // Update internal configs once the connection requests are accepted.
        linkedNetworks.values().forEach(linkedConfig ->
                mWifiConfigManager.setNetworkLastUsedSecurityParams(
                        linkedConfig.networkId, params));

        List<String> allowlistSsids = new ArrayList<>(linkedNetworks.values().stream()
                .filter(linkedConfig -> linkedConfig.allowAutojoin)
                .map(linkedConfig -> linkedConfig.SSID)
                .collect(Collectors.toList()));
        if (allowlistSsids.size() > 0) {
            allowlistSsids.add(config.SSID);
        }
        mWifiBlocklistMonitor.setAllowlistSsids(config.SSID, allowlistSsids);
        mWifiBlocklistMonitor.updateFirmwareRoamingConfiguration(new ArraySet<>(allowlistSsids));
    }

    private boolean checkAndHandleLinkedNetworkRoaming(String associatedBssid) {
        if (!mContext.getResources().getBoolean(R.bool.config_wifiEnableLinkedNetworkRoaming)) {
            return false;
        }

        ScanResult scanResult = mScanRequestProxy.getScanResult(associatedBssid);
        if (scanResult == null) {
            return false;
        }

        WifiConfiguration config = mWifiConfigManager
                .getSavedNetworkForScanResult(scanResult);
        if (config == null || !config.allowedKeyManagement.get(WifiConfiguration.KeyMgmt.WPA_PSK)
                || mLastNetworkId == config.networkId) {
            return false;
        }

        mIsLinkedNetworkRoaming = true;
        setTargetBssid(config, associatedBssid);
        mTargetNetworkId = config.networkId;
        mTargetWifiConfiguration = config;
        mLastNetworkId = WifiConfiguration.INVALID_NETWORK_ID;
        sendNetworkChangeBroadcast(DetailedState.CONNECTING);
        mWifiInfo.setFrequency(scanResult.frequency);
        mWifiInfo.setBSSID(associatedBssid);
        updateCurrentConnectionInfo();
        return true;
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private @WifiConfiguration.RecentFailureReason int
            mboAssocDisallowedReasonCodeToWifiConfigurationRecentFailureReason(
            @MboOceConstants.MboAssocDisallowedReasonCode int reasonCode) {
        switch (reasonCode) {
            case MboOceConstants.MBO_ASSOC_DISALLOWED_REASON_MAX_NUM_STA_ASSOCIATED:
                return WifiConfiguration.RECENT_FAILURE_MBO_ASSOC_DISALLOWED_MAX_NUM_STA_ASSOCIATED;
            case MboOceConstants.MBO_ASSOC_DISALLOWED_REASON_AIR_INTERFACE_OVERLOADED:
                return WifiConfiguration
                        .RECENT_FAILURE_MBO_ASSOC_DISALLOWED_AIR_INTERFACE_OVERLOADED;
            case MboOceConstants.MBO_ASSOC_DISALLOWED_REASON_AUTH_SERVER_OVERLOADED:
                return WifiConfiguration.RECENT_FAILURE_MBO_ASSOC_DISALLOWED_AUTH_SERVER_OVERLOADED;
            case MboOceConstants.MBO_ASSOC_DISALLOWED_REASON_INSUFFICIENT_RSSI:
                return WifiConfiguration.RECENT_FAILURE_MBO_ASSOC_DISALLOWED_INSUFFICIENT_RSSI;
            case MboOceConstants.MBO_ASSOC_DISALLOWED_REASON_UNSPECIFIED:
            case MboOceConstants.MBO_ASSOC_DISALLOWED_REASON_RESERVED_0:
            case MboOceConstants.MBO_ASSOC_DISALLOWED_REASON_RESERVED:
            default:
                return WifiConfiguration.RECENT_FAILURE_MBO_ASSOC_DISALLOWED_UNSPECIFIED;
        }
    }

    /**
     * To set association rejection status in wifi config.
     * @param netId The network ID.
     * @param assocRejectEventInfo Association rejection information.
     */
    private void setAssociationRejectionStatusInConfig(int netId,
            AssocRejectEventInfo assocRejectEventInfo) {
        int statusCode = assocRejectEventInfo.statusCode;
        @WifiConfiguration.RecentFailureReason int reason;

        switch (statusCode) {
            case StaIfaceStatusCode.AP_UNABLE_TO_HANDLE_NEW_STA:
                reason = WifiConfiguration.RECENT_FAILURE_AP_UNABLE_TO_HANDLE_NEW_STA;
                break;
            case StaIfaceStatusCode.ASSOC_REJECTED_TEMPORARILY:
                reason = WifiConfiguration.RECENT_FAILURE_REFUSED_TEMPORARILY;
                break;
            case StaIfaceStatusCode.DENIED_POOR_CHANNEL_CONDITIONS:
                reason = WifiConfiguration.RECENT_FAILURE_POOR_CHANNEL_CONDITIONS;
                break;
            default:
                // do nothing
                return;
        }

        if (SdkLevel.isAtLeastS()) {
            if (assocRejectEventInfo.mboAssocDisallowedInfo != null) {
                reason = mboAssocDisallowedReasonCodeToWifiConfigurationRecentFailureReason(
                        assocRejectEventInfo.mboAssocDisallowedInfo.mReasonCode);
            } else if (assocRejectEventInfo.oceRssiBasedAssocRejectInfo != null) {
                reason = WifiConfiguration.RECENT_FAILURE_OCE_RSSI_BASED_ASSOCIATION_REJECTION;
            }
        }

        mWifiConfigManager.setRecentFailureAssociationStatus(netId, reason);

    }

    private void checkIfNeedDisconnectSecondaryWifi() {
        if (!isPrimary()) {
            return;
        }
        if (isConnected()) {
            ConcreteClientModeManager ccmm =
                    mWifiInjector.getActiveModeWarden().getClientModeManagerInRole(
                            ROLE_CLIENT_SECONDARY_LONG_LIVED);
            if (ccmm != null && ccmm.isConnected() && ccmm.isSecondaryInternet()) {
                WifiInfo secondaryWifiInfo = ccmm.getConnectionInfo();
                if (secondaryWifiInfo == null) return;
                if ((secondaryWifiInfo.is5GHz() && mWifiInfo.is5GHz())
                        || (secondaryWifiInfo.is6GHz() && mWifiInfo.is6GHz())
                        || (secondaryWifiInfo.is24GHz() && mWifiInfo.is24GHz())) {
                    if (mVerboseLoggingEnabled) {
                        Log.d(TAG, "The master wifi and secondary wifi are at the same band,"
                                + " disconnect the secondary wifi");
                    }
                    ccmm.disconnect();
                }
            }
        }
    }

    private void setSelectedRcoiForPasspoint(WifiConfiguration config) {
        // Only relevant for Passpoint providers with roaming consortium subscriptions
        if (config.isPasspoint() && config.roamingConsortiumIds != null
                && config.roamingConsortiumIds.length > 0) {
            long selectedRcoi = mPasspointManager.getSelectedRcoiForNetwork(
                    config.getPasspointUniqueId(), config.SSID);
            if (selectedRcoi != 0) {
                config.enterpriseConfig.setSelectedRcoi(selectedRcoi);
            }
        }
    }

    private void updateCurrentConnectionInfo() {
        if (isPrimary()) {
            mWifiInjector.getActiveModeWarden().updateCurrentConnectionInfo();
        }
    }
}
