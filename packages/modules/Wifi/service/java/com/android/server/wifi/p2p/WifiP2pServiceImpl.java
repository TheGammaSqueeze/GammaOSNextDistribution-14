/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.server.wifi.p2p;

import static android.net.wifi.p2p.WifiP2pConfig.GROUP_CLIENT_IP_PROVISIONING_MODE_IPV4_DHCP;
import static android.net.wifi.p2p.WifiP2pConfig.GROUP_CLIENT_IP_PROVISIONING_MODE_IPV6_LINK_LOCAL;

import static com.android.net.module.util.Inet4AddressUtils.inet4AddressToIntHTL;
import static com.android.net.module.util.Inet4AddressUtils.netmaskToPrefixLength;
import static com.android.server.wifi.WifiSettingsConfigStore.D2D_ALLOWED_WHEN_INFRA_STA_DISABLED;
import static com.android.server.wifi.WifiSettingsConfigStore.WIFI_P2P_DEVICE_ADDRESS;
import static com.android.server.wifi.WifiSettingsConfigStore.WIFI_P2P_DEVICE_NAME;
import static com.android.server.wifi.WifiSettingsConfigStore.WIFI_P2P_PENDING_FACTORY_RESET;
import static com.android.server.wifi.WifiSettingsConfigStore.WIFI_VERBOSE_LOGGING_ENABLED;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.BroadcastOptions;
import android.content.AttributionSource;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.DhcpResultsParcelable;
import android.net.InetAddresses;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.MacAddress;
import android.net.NetworkInfo;
import android.net.NetworkStack;
import android.net.StaticIpConfiguration;
import android.net.TetheredClient;
import android.net.TetheringInterface;
import android.net.TetheringManager;
import android.net.TetheringManager.TetheringEventCallback;
import android.net.ip.IIpClient;
import android.net.ip.IpClientCallbacks;
import android.net.ip.IpClientUtil;
import android.net.shared.ProvisioningConfiguration;
import android.net.wifi.CoexUnsafeChannel;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.IWifiP2pListener;
import android.net.wifi.p2p.IWifiP2pManager;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pDiscoveryConfig;
import android.net.wifi.p2p.WifiP2pExtListenParams;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pGroupList;
import android.net.wifi.p2p.WifiP2pGroupList.GroupDeleteListener;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ExternalApproverRequestListener;
import android.net.wifi.p2p.WifiP2pProvDiscEvent;
import android.net.wifi.p2p.WifiP2pWfdInfo;
import android.net.wifi.p2p.nsd.WifiP2pServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pServiceRequest;
import android.net.wifi.p2p.nsd.WifiP2pServiceResponse;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.UserManager;
import android.os.WorkSource;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.LocalLog;
import android.util.Log;
import android.util.SparseArray;
import android.view.Display;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.RequiresApi;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.AsyncChannel;
import com.android.internal.util.Protocol;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;
import com.android.internal.util.WakeupMessage;
import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.BuildProperties;
import com.android.server.wifi.Clock;
import com.android.server.wifi.FrameworkFacade;
import com.android.server.wifi.HalDeviceManager;
import com.android.server.wifi.InterfaceConflictManager;
import com.android.server.wifi.RunnerState;
import com.android.server.wifi.WifiDialogManager;
import com.android.server.wifi.WifiGlobals;
import com.android.server.wifi.WifiInjector;
import com.android.server.wifi.WifiSettingsConfigStore;
import com.android.server.wifi.WifiThreadRunner;
import com.android.server.wifi.coex.CoexManager;
import com.android.server.wifi.p2p.ExternalApproverManager.ApproverEntry;
import com.android.server.wifi.proto.nano.WifiMetricsProto;
import com.android.server.wifi.proto.nano.WifiMetricsProto.GroupEvent;
import com.android.server.wifi.proto.nano.WifiMetricsProto.P2pConnectionEvent;
import com.android.server.wifi.util.LastCallerInfoManager;
import com.android.server.wifi.util.NetdWrapper;
import com.android.server.wifi.util.StringUtil;
import com.android.server.wifi.util.WaitingState;
import com.android.server.wifi.util.WifiPermissionsUtil;
import com.android.server.wifi.util.WifiPermissionsWrapper;
import com.android.wifi.flags.FeatureFlags;
import com.android.wifi.resources.R;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * WifiP2pService includes a state machine to perform Wi-Fi p2p operations. Applications
 * communicate with this service to issue device discovery and connectivity requests
 * through the WifiP2pManager interface. The state machine communicates with the wifi
 * driver through wpa_supplicant and handles the event responses through WifiMonitor.
 *
 * Note that the term Wifi when used without a p2p suffix refers to the client mode
 * of Wifi operation
 * @hide
 */
public class WifiP2pServiceImpl extends IWifiP2pManager.Stub {
    private static final String TAG = "WifiP2pService";
    @VisibleForTesting
    public static final String P2P_IDLE_SHUTDOWN_MESSAGE_TIMEOUT_TAG = TAG
            + " Idle Shutdown Message Timeout";
    private final LocalLog mThreadLocalLog;
    private final int mThreshold;
    private boolean mVerboseLoggingEnabled = false;
    private boolean mVerboseHalLoggingEnabled = false;
    private static final String NETWORK_TYPE = "WIFI_P2P";
    @VisibleForTesting
    static final String DEFAULT_DEVICE_NAME_PREFIX = "Android_";
    // The maxinum length of the device name is 32 bytes, see
    // Section 4.1.15 in Wi-Fi Direct Specification v1 and
    // Section 12 in Wi-Fi Protected Setup Specification v2.
    @VisibleForTesting
    static final int DEVICE_NAME_LENGTH_MAX = 32;
    @VisibleForTesting
    static final int DEVICE_NAME_POSTFIX_LENGTH_MIN = 4;
    @VisibleForTesting
    static final int DEVICE_NAME_PREFIX_LENGTH_MAX =
            DEVICE_NAME_LENGTH_MAX - DEVICE_NAME_POSTFIX_LENGTH_MIN;
    @VisibleForTesting
    static final int DEFAULT_GROUP_OWNER_INTENT = 6;
    // The maximum length of a group name is the same as SSID, i.e. 32 bytes.
    // Wi-Fi Direct group name starts with "DIRECT-xy-" where xy is two ASCII characters
    // randomly selected, so there are 10 bytes occupied.
    @VisibleForTesting
    static final int GROUP_NAME_POSTFIX_LENGTH_MAX = 22;

    @VisibleForTesting
    // It requires to over "DISCOVER_TIMEOUT_S(120)" or "GROUP_CREATING_WAIT_TIME_MS(120)".
    // Otherwise it will cause interface down before function timeout.
    static final long P2P_INTERFACE_IDLE_SHUTDOWN_TIMEOUT_MS = 150_000;

    private final Context mContext;

    NetdWrapper mNetdWrapper;
    private IIpClient mIpClient;
    private int mIpClientStartIndex = 0;
    private DhcpResultsParcelable mDhcpResultsParcelable;

    private final P2pStateMachine mP2pStateMachine;
    private final AsyncChannel mReplyChannel = new AsyncChannel();
    private AsyncChannel mWifiChannel;
    private final WifiInjector mWifiInjector;
    private final WifiPermissionsUtil mWifiPermissionsUtil;
    private final FrameworkFacade mFrameworkFacade;
    private final WifiSettingsConfigStore mSettingsConfigStore;
    private final WifiP2pMetrics mWifiP2pMetrics;
    private final BuildProperties mBuildProperties;
    // This will only be null if SdkLevel is not at least S
    @Nullable private final CoexManager mCoexManager;
    private final WifiGlobals mWifiGlobals;
    private final UserManager mUserManager;
    private final InterfaceConflictManager mInterfaceConflictManager;
    private TetheringManager mTetheringManager = null;
    private final WifiP2pNative mWifiNative;
    private final LastCallerInfoManager mLastCallerInfoManager;
    private HalDeviceManager mHalDeviceManager;
    private final FeatureFlags mFeatureFlags;

    private static final Boolean JOIN_GROUP = true;
    private static final Boolean FORM_GROUP = false;

    private static final Boolean RELOAD = true;
    private static final Boolean NO_RELOAD = false;

    private static final String[] RECEIVER_PERMISSIONS_FOR_BROADCAST = {
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_WIFI_STATE
    };

    private static final String[] RECEIVER_PERMISSIONS_FOR_BROADCAST_LOCATION_OFF = {
            android.Manifest.permission.NETWORK_SETTINGS,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_WIFI_STATE
    };

    private static final String[] RECEIVER_PERMISSIONS_MAINLINE_NETWORK_STACK = {
            NetworkStack.PERMISSION_MAINLINE_NETWORK_STACK
    };

    // Maximum number of bytes allowed for a network name, i.e. SSID.
    private static final int MAX_NETWORK_NAME_BYTES = 32;
    // Minimum number of bytes for a network name, i.e. DIRECT-xy.
    private static final int MIN_NETWORK_NAME_BYTES = 9;

    // Two minutes comes from the wpa_supplicant setting
    private static final int GROUP_CREATING_WAIT_TIME_MS = 120 * 1000;
    private static int sGroupCreatingTimeoutIndex = 0;

    private static final int DISABLE_P2P_WAIT_TIME_MS = 5 * 1000;
    private static int sDisableP2pTimeoutIndex = 0;

    private static final int P2P_REJECTION_WAIT_TIME_MS = 100;
    private static int sP2pRejectionResumeAfterDelayIndex = 0;

    // Set a two minute discover timeout to avoid STA scans from being blocked
    private static final int DISCOVER_TIMEOUT_S = 120;

    // Idle time after a peer is gone when the group is torn down
    private static final int GROUP_IDLE_TIME_S = 10;

    private static final int BASE = Protocol.BASE_WIFI_P2P_SERVICE;

    // Delayed message to timeout group creation
    public static final int GROUP_CREATING_TIMED_OUT        =   BASE + 1;

    // User accepted a peer request
    @VisibleForTesting static final int PEER_CONNECTION_USER_ACCEPT = BASE + 2;
    // User rejected a peer request
    @VisibleForTesting
    static final int PEER_CONNECTION_USER_REJECT            =   BASE + 3;
    // User wants to disconnect wifi in favour of p2p
    private static final int DROP_WIFI_USER_ACCEPT          =   BASE + 4;
    // User wants to keep his wifi connection and drop p2p
    @VisibleForTesting
    static final int DROP_WIFI_USER_REJECT                  =   BASE + 5;
    // Delayed message to timeout p2p disable
    public static final int DISABLE_P2P_TIMED_OUT           =   BASE + 6;
    // User confirm a peer request
    public static final int PEER_CONNECTION_USER_CONFIRM    =   BASE + 7;

    // Commands to the ClientModeImpl
    public static final int P2P_CONNECTION_CHANGED          =   BASE + 11;

    // These commands are used to temporarily disconnect wifi when we detect
    // a frequency conflict which would make it impossible to have with p2p
    // and wifi active at the same time.
    // If the user chooses to disable wifi temporarily, we keep wifi disconnected
    // until the p2p connection is done and terminated at which point we will
    // bring back wifi up
    // DISCONNECT_WIFI_REQUEST
    //      msg.arg1 = 1 enables temporary disconnect and 0 disables it.
    public static final int DISCONNECT_WIFI_REQUEST         =   BASE + 12;
    public static final int DISCONNECT_WIFI_RESPONSE        =   BASE + 13;

    public static final int SET_MIRACAST_MODE               =   BASE + 14;

    // During dhcp (and perhaps other times) we can't afford to drop packets
    // but Discovery will switch our channel enough we will.
    //   msg.arg1 = ENABLED for blocking, DISABLED for resumed.
    //   msg.arg2 = msg to send when blocked
    //   msg.obj  = StateMachine to send to when blocked
    public static final int BLOCK_DISCOVERY                 =   BASE + 15;
    public static final int ENABLE_P2P                      =   BASE + 16;
    public static final int DISABLE_P2P                     =   BASE + 17;
    public static final int REMOVE_CLIENT_INFO              =   BASE + 18;
    // idle shutdown message
    public static final int CMD_P2P_IDLE_SHUTDOWN           =   BASE + 19;

    // Messages for interaction with IpClient.
    private static final int IPC_PRE_DHCP_ACTION            =   BASE + 30;
    private static final int IPC_POST_DHCP_ACTION           =   BASE + 31;
    private static final int IPC_DHCP_RESULTS               =   BASE + 32;
    private static final int IPC_PROVISIONING_SUCCESS       =   BASE + 33;
    private static final int IPC_PROVISIONING_FAILURE       =   BASE + 34;
    @VisibleForTesting
    static final int TETHER_INTERFACE_STATE_CHANGED         =   BASE + 35;

    private static final int UPDATE_P2P_DISALLOWED_CHANNELS =   BASE + 36;
    // Delayed message to timeout group creation
    public static final int P2P_REJECTION_RESUME_AFTER_DELAY = BASE + 37;


    static final int TETHER_INTERFACE_CLIENTS_CHANGED         =   BASE + 38;

    public static final int ENABLED                         = 1;
    public static final int DISABLED                        = 0;

    private static final int P2P_CONNECT_TRIGGER_GROUP_NEG_REQ      = 1;
    private static final int P2P_CONNECT_TRIGGER_INVITATION_REQ     = 2;
    private static final int P2P_CONNECT_TRIGGER_OTHER              = 3;

    private final boolean mP2pSupported;

    private final WifiP2pDevice mThisDevice = new WifiP2pDevice();

    // To avoid changing the default name on every initialization, preserve it
    // in a period if this device is not rebooted.
    private String mDefaultDeviceName = null;
    private long mLastDefaultDeviceNameGeneratingTimeMillis = 0L;
    // Keep the default name in 24 hours.
    @VisibleForTesting
    static final long DEFAULT_DEVICE_NAME_LIFE_TIME_MILLIS = 24 * 60 * 60 * 1000;

    // When a group has been explicitly created by an app, we persist the group
    // even after all clients have been disconnected until an explicit remove
    // is invoked
    private boolean mAutonomousGroup;

    // Invitation to join an existing p2p group
    private boolean mJoinExistingGroup;

    // Track whether we are in p2p discovery. This is used to avoid sending duplicate
    // broadcasts
    private boolean mDiscoveryStarted;

    // Track whether servcice/peer discovery is blocked in favor of other wifi actions
    // (notably dhcp)
    private boolean mDiscoveryBlocked;

    // remember if we were in a scan when it had to be stopped
    private boolean mDiscoveryPostponed = false;

    // Track whether we are in p2p listen. This is used to avoid sending duplicate broadcasts
    private boolean mListenStarted;

    // Track whether DISALLOW_WIFI_DIRECT user restriction has been set
    private boolean mIsP2pDisallowedByAdmin = false;

    // Track the last p2p availability state that was broadcasted
    private boolean mLastP2pState = false;

    private NetworkInfo.DetailedState mDetailedState;

    private boolean mTemporarilyDisconnectedWifi = false;

    // The transaction Id of service discovery request
    private int mServiceTransactionId = 0;

    // Service discovery request ID of wpa_supplicant.
    // null means it's not set yet.
    private String mServiceDiscReqId;

    // clients(application) information list
    private final HashMap<Messenger, ClientInfo> mClientInfoList = new HashMap<>();

    // clients(application) channel list
    private final Map<IBinder, Messenger> mClientChannelList = new HashMap<>();

    // clients(application) approver manager
    private final ExternalApproverManager mExternalApproverManager = new ExternalApproverManager();

    // client(application) attribution source list
    private Map<IBinder, AttributionSource> mClientAttributionSource = new HashMap<>();

    // client(application) vendor-specific information element list
    private final Map<String, HashSet<ScanResult.InformationElement>> mVendorElements =
            new HashMap<>();

    // peer authorizing timestamp which is indexed by the peer MAC address.
    private final Map<String, Long> mPeerAuthorizingTimestamp = new HashMap<>();

    // The empty device address set by wpa_supplicant.
    private static final String EMPTY_DEVICE_ADDRESS = "00:00:00:00:00:00";

    // An anonymized device address. This is used instead of the own device MAC to prevent the
    // latter from leaking to apps
    private static final String ANONYMIZED_DEVICE_ADDRESS = "02:00:00:00:00:00";

    // Idle shut down
    @VisibleForTesting
    public WakeupMessage mP2pIdleShutdownMessage;

    private WifiP2pConfig mSavedRejectedPeerConfig = null;

    private boolean mIsBootComplete;

    // Constants for configuring P2P GO IP Address Allocation in EAPOL-Key Frames (4-Way Handshake)
    // when device overlay config_wifiP2pGoIpAddressAllocationInEapolFrames is set to True
    @VisibleForTesting
    static final String GO_EAPOL_IP_ADDRESS = "192.168.49.1";
    @VisibleForTesting
    static final String GO_EAPOL_IP_SUBNET_MASK = "255.255.255.0";
    // The default P2P GO IP address range reserved for IP assignment in EAPOL frame
    // when device overlay config_wifiP2pGoEapolIpAddressRangeStart and
    // config_wifiP2pGoEapolIpAddressRangeEnd is not configured in device overlay.
    @VisibleForTesting
    static final String GO_EAPOL_IP_RANGE_DEFAULT_START_ADDRESS = "192.168.49.128";
    @VisibleForTesting
    static final String GO_EAPOL_IP_RANGE_DEFAULT_END_ADDRESS = "192.168.49.254";

    private final RemoteCallbackList<IWifiP2pListener> mWifiP2pListeners =
            new RemoteCallbackList<>();

    /**
     * Error code definition.
     * see the Table.8 in the WiFi Direct specification for the detail.
     */
    public enum P2pStatus {
        // Success
        SUCCESS,

        // The target device is currently unavailable
        INFORMATION_IS_CURRENTLY_UNAVAILABLE,

        // Protocol error
        INCOMPATIBLE_PARAMETERS,

        // The target device reached the limit of the number of the connectable device.
        // For example, device limit or group limit is set
        LIMIT_REACHED,

        // Protocol error
        INVALID_PARAMETER,

        // Unable to accommodate request
        UNABLE_TO_ACCOMMODATE_REQUEST,

        // Previous protocol error, or disruptive behavior
        PREVIOUS_PROTOCOL_ERROR,

        // There is no common channels the both devices can use
        NO_COMMON_CHANNEL,

        // Unknown p2p group. For example, Device A tries to invoke the previous persistent group,
        // but device B has removed the specified credential already
        UNKNOWN_P2P_GROUP,

        // Both p2p devices indicated an intent of 15 in group owner negotiation
        BOTH_GO_INTENT_15,

        // Incompatible provisioning method
        INCOMPATIBLE_PROVISIONING_METHOD,

        // Rejected by user
        REJECTED_BY_USER,

        // Unknown error
        UNKNOWN;

        /**
         * Returns P2p status corresponding to a given error value
         * @param error integer error value
         * @return P2pStatus enum for value
         */
        public static P2pStatus valueOf(int error) {
            switch(error) {
                case 0 :
                    return SUCCESS;
                case 1:
                    return INFORMATION_IS_CURRENTLY_UNAVAILABLE;
                case 2:
                    return INCOMPATIBLE_PARAMETERS;
                case 3:
                    return LIMIT_REACHED;
                case 4:
                    return INVALID_PARAMETER;
                case 5:
                    return UNABLE_TO_ACCOMMODATE_REQUEST;
                case 6:
                    return PREVIOUS_PROTOCOL_ERROR;
                case 7:
                    return NO_COMMON_CHANNEL;
                case 8:
                    return UNKNOWN_P2P_GROUP;
                case 9:
                    return BOTH_GO_INTENT_15;
                case 10:
                    return INCOMPATIBLE_PROVISIONING_METHOD;
                case 11:
                    return REJECTED_BY_USER;
                default:
                    return UNKNOWN;
            }
        }
    }

    /**
     * Proxy for the final native call of the parent class. Enables mocking of
     * the function.
     */
    public int getMockableCallingUid() {
        return Binder.getCallingUid();
    }

    private void updateWorkSourceByUid(int uid, boolean active) {
        if (uid == -1) return;
        if (active == mActiveClients.containsKey(uid)) return;
        Log.d(TAG, "Update WorkSource UID=" + uid + " active=" + active);

        if (!active) mActiveClients.remove(uid);
        // The worksource is based on UID, just find the first one.
        DeathHandlerData dhd = mDeathDataByBinder.values().stream()
                .filter(d -> d.mUid == uid)
                .findAny()
                .orElse(null);
        if (active && null == dhd) {
            Log.w(TAG, "No WorkSource for UID " + uid);
            return;
        }

        if (null != dhd) {
            mActiveClients.put(uid, dhd.mWorkSource);
        }
        // If p2p is off, the first one activates P2P will merge all worksources.
        // If p2p is already on, send ENABLE_P2P to merge the new worksource.
        if (mP2pStateMachine.isP2pDisabled()) return;
        mP2pStateMachine.sendMessage(ENABLE_P2P);
    }

    /**
     * Handles client connections
     */
    private class ClientHandler extends Handler {

        ClientHandler(String tag, Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case WifiP2pManager.SET_DEVICE_NAME:
                case WifiP2pManager.SET_WFD_INFO:
                case WifiP2pManager.DISCOVER_PEERS:
                case WifiP2pManager.STOP_DISCOVERY:
                case WifiP2pManager.CONNECT:
                case WifiP2pManager.CANCEL_CONNECT:
                case WifiP2pManager.CREATE_GROUP:
                case WifiP2pManager.REMOVE_GROUP:
                case WifiP2pManager.START_LISTEN:
                case WifiP2pManager.STOP_LISTEN:
                case WifiP2pManager.SET_CHANNEL:
                case WifiP2pManager.START_WPS:
                case WifiP2pManager.ADD_LOCAL_SERVICE:
                case WifiP2pManager.REMOVE_LOCAL_SERVICE:
                case WifiP2pManager.CLEAR_LOCAL_SERVICES:
                case WifiP2pManager.DISCOVER_SERVICES:
                case WifiP2pManager.ADD_SERVICE_REQUEST:
                case WifiP2pManager.REMOVE_SERVICE_REQUEST:
                case WifiP2pManager.CLEAR_SERVICE_REQUESTS:
                case WifiP2pManager.REQUEST_PEERS:
                case WifiP2pManager.REQUEST_CONNECTION_INFO:
                case WifiP2pManager.REQUEST_GROUP_INFO:
                case WifiP2pManager.DELETE_PERSISTENT_GROUP:
                case WifiP2pManager.REQUEST_PERSISTENT_GROUP_INFO:
                case WifiP2pManager.FACTORY_RESET:
                case WifiP2pManager.SET_ONGOING_PEER_CONFIG:
                case WifiP2pManager.REQUEST_ONGOING_PEER_CONFIG:
                case WifiP2pManager.REQUEST_P2P_STATE:
                case WifiP2pManager.REQUEST_DISCOVERY_STATE:
                case WifiP2pManager.GET_LISTEN_STATE:
                case WifiP2pManager.REQUEST_NETWORK_INFO:
                case WifiP2pManager.UPDATE_CHANNEL_INFO:
                case WifiP2pManager.REQUEST_DEVICE_INFO:
                case WifiP2pManager.REMOVE_CLIENT:
                case WifiP2pManager.ADD_EXTERNAL_APPROVER:
                case WifiP2pManager.REMOVE_EXTERNAL_APPROVER:
                case WifiP2pManager.SET_CONNECTION_REQUEST_RESULT:
                case WifiP2pManager.SET_VENDOR_ELEMENTS:
                    mP2pStateMachine.sendMessage(Message.obtain(msg));
                    break;
                default:
                    Log.d(TAG, "ClientHandler.handleMessage ignoring msg=" + msg);
                    break;
            }
        }
    }
    private final ClientHandler mClientHandler;

    private NetworkInfo makeNetworkInfo() {
        final NetworkInfo info = new NetworkInfo(ConnectivityManager.TYPE_WIFI_P2P,
                0, NETWORK_TYPE, "");
        if (mDetailedState != NetworkInfo.DetailedState.IDLE) {
            info.setDetailedState(mDetailedState, null, null);
        }
        return info;
    }

    private static class DeathHandlerData {
        DeathHandlerData(int uid, DeathRecipient dr, Messenger m, WorkSource ws, int displayId) {
            mUid = uid;
            mDeathRecipient = dr;
            mMessenger = m;
            mWorkSource = ws;
            mDisplayId = displayId;
        }

        @Override
        public String toString() {
            return "mUid=" + mUid + ", deathRecipient=" + mDeathRecipient + ", messenger="
                    + mMessenger + ", worksource=" + mWorkSource + ", displayId=" + mDisplayId;
        }

        final int mUid;
        final DeathRecipient mDeathRecipient;
        final Messenger mMessenger;
        final WorkSource mWorkSource;
        final int mDisplayId;
    }
    private final Object mLock = new Object();
    private final Map<IBinder, DeathHandlerData> mDeathDataByBinder = new ConcurrentHashMap<>();
    private final Map<Integer, WorkSource> mActiveClients = new ConcurrentHashMap<>();

    private final Clock mClock;

    private class D2DAllowWhenInfraStaDisabledValueListener
            implements WifiSettingsConfigStore.OnSettingsChangedListener<Boolean> {
        @Override
        public void onSettingsChanged(@NonNull WifiSettingsConfigStore.Key<Boolean> key,
                @Nullable Boolean newValue) {
            if (!mP2pStateMachine.isWifiP2pAvailable()) {
                Log.i(TAG, "D2d isn't allowed anymore when infra sta is disabled");
                mP2pStateMachine.sendMessage(DISABLE_P2P);
                mP2pStateMachine.checkAndSendP2pStateChangedBroadcast();
            }
        }
    }

    public WifiP2pServiceImpl(Context context, WifiInjector wifiInjector) {
        mContext = context;
        mWifiInjector = wifiInjector;
        mWifiPermissionsUtil = mWifiInjector.getWifiPermissionsUtil();
        mFrameworkFacade = mWifiInjector.getFrameworkFacade();
        mSettingsConfigStore = mWifiInjector.getSettingsConfigStore();
        mWifiP2pMetrics = mWifiInjector.getWifiP2pMetrics();
        mCoexManager = mWifiInjector.getCoexManager();
        mWifiGlobals = mWifiInjector.getWifiGlobals();
        mBuildProperties = mWifiInjector.getBuildProperties();
        mUserManager = mWifiInjector.getUserManager();
        mInterfaceConflictManager = mWifiInjector.getInterfaceConflictManager();
        mClock = mWifiInjector.getClock();
        mThreadLocalLog = mWifiInjector.getWifiHandlerLocalLog();
        mThreshold = mContext.getResources().getInteger(
                R.integer.config_wifiConfigurationWifiRunnerThresholdInMs);

        mDetailedState = NetworkInfo.DetailedState.IDLE;

        mP2pSupported = mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_WIFI_DIRECT);
        HandlerThread wifiP2pThread = mWifiInjector.getWifiP2pServiceHandlerThread();
        mClientHandler = new ClientHandler(TAG, wifiP2pThread.getLooper());
        mWifiNative = mWifiInjector.getWifiP2pNative();
        mLastCallerInfoManager = mWifiInjector.getLastCallerInfoManager();
        mHalDeviceManager = mWifiInjector.getHalDeviceManager();
        mFeatureFlags = mWifiInjector.getDeviceConfigFacade().getFeatureFlags();
        mP2pStateMachine = new P2pStateMachine(TAG, wifiP2pThread.getLooper(), mP2pSupported);
        mP2pStateMachine.setDbg(false); // can enable for very verbose logs
        mP2pStateMachine.start();
    }

    /**
     * Obtains the service interface for Managements services
     */
    public void connectivityServiceReady() {
        mNetdWrapper = mWifiInjector.makeNetdWrapper();
    }

    /** Indicate that boot is completed. */
    public void handleBootCompleted() {
        updateVerboseLoggingEnabled();
        mIsBootComplete = true;
        mTetheringManager = mContext.getSystemService(TetheringManager.class);
        if (mTetheringManager == null) {
            Log.wtf(TAG, "Tethering manager is null when WifiP2pServiceImp handles boot completed");
        }
        String deviceAddress = mSettingsConfigStore.get(WIFI_P2P_DEVICE_ADDRESS);
        if (!mWifiGlobals.isP2pMacRandomizationSupported() && !TextUtils.isEmpty(deviceAddress)) {
            mThisDevice.deviceAddress = deviceAddress;
        }
        String deviceName = mSettingsConfigStore.get(WIFI_P2P_DEVICE_NAME);
        if (!TextUtils.isEmpty(deviceName)) {
            mThisDevice.deviceName = deviceName;
        }
    }

    private void updateVerboseLoggingEnabled() {
        final int verboseAlwaysOnLevel = mContext.getResources().getInteger(
                R.integer.config_wifiVerboseLoggingAlwaysOnLevel);
        mVerboseLoggingEnabled = mFrameworkFacade.isVerboseLoggingAlwaysOn(verboseAlwaysOnLevel,
                mBuildProperties) || mVerboseHalLoggingEnabled;
    }

    private void enforceAccessPermission() {
        mContext.enforceCallingOrSelfPermission(android.Manifest.permission.ACCESS_WIFI_STATE,
                "WifiP2pService");
    }

    private void enforceChangePermission() {
        mContext.enforceCallingOrSelfPermission(android.Manifest.permission.CHANGE_WIFI_STATE,
                "WifiP2pService");
    }

    private boolean checkAnyPermissionOf(String... permissions) {
        for (String permission : permissions) {
            if (mContext.checkCallingOrSelfPermission(permission)
                    == PackageManager.PERMISSION_GRANTED) {
                return true;
            }
        }
        return false;
    }

    private void enforceAnyPermissionOf(String... permissions) {
        if (!checkAnyPermissionOf(permissions)) {
            throw new SecurityException("Requires one of the following permissions: "
                    + String.join(", ", permissions) + ".");
        }
    }

    private void enforceNetworkStackOrLocationHardwarePermission() {
        enforceAnyPermissionOf(
                android.Manifest.permission.LOCATION_HARDWARE,
                android.Manifest.permission.NETWORK_STACK,
                NetworkStack.PERMISSION_MAINLINE_NETWORK_STACK);
    }

    private void stopIpClient() {
        // Invalidate all previous start requests
        mIpClientStartIndex++;
        if (mIpClient != null) {
            try {
                mIpClient.shutdown();
            } catch (RemoteException e) {
                e.rethrowFromSystemServer();
            }
            mIpClient = null;
        }
        mDhcpResultsParcelable = null;
    }

    private void startIpClient(String ifname, Handler smHandler,
            int groupClientIpProvisioningMode,
            WifiP2pGroup.P2pGroupClientEapolIpAddressData p2pClientEapolIpInfo) {
        stopIpClient();
        mIpClientStartIndex++;
        IpClientUtil.makeIpClient(mContext, ifname, new IpClientCallbacksImpl(
                mIpClientStartIndex, smHandler, groupClientIpProvisioningMode,
                p2pClientEapolIpInfo));
    }

    private StaticIpConfiguration makeStaticIpConfigurationFromEapolIpAddressInfo(
            WifiP2pGroup.P2pGroupClientEapolIpAddressData p2pClientEapolIpInfo) {
        try {
            final Inet4Address addressGo = p2pClientEapolIpInfo.mIpAddressGo;
            final Inet4Address addressClient = p2pClientEapolIpInfo.mIpAddressClient;
            final Inet4Address addressMask = p2pClientEapolIpInfo.mIpAddressMask;
            final LinkAddress linkAddressClient = new LinkAddress(addressClient,
                    netmaskToPrefixLength(addressMask));

            final StaticIpConfiguration staticIpConfig = new StaticIpConfiguration.Builder()
                    .setIpAddress(linkAddressClient)
                    .setGateway(addressGo).build();
            if (mVerboseLoggingEnabled) {
                Log.i(TAG, "IP Addresses obtained via EAPOL H/S - CLIENT: "
                        + addressClient.getHostAddress()
                        + ", GO: " + addressGo.getHostAddress() + " MASK: "
                        + addressMask.getHostAddress());
            }
            return staticIpConfig;
        } catch (Exception e) {
            Log.e(TAG, "Failed to build EAPOL static IP configuration: " + e);
            return null;
        }
    }

    private class IpClientCallbacksImpl extends IpClientCallbacks {
        private final int mStartIndex;
        private final Handler mHandler;
        private final int mGroupClientIpProvisioningMode;
        private WifiP2pGroup.P2pGroupClientEapolIpAddressData mP2pClientEapolIpInfo;

        private IpClientCallbacksImpl(int startIndex, Handler handler,
                int groupClientIpProvisioningMode,
                WifiP2pGroup.P2pGroupClientEapolIpAddressData p2pClientEapolIpInfo) {
            mStartIndex = startIndex;
            mHandler = handler;
            mGroupClientIpProvisioningMode = groupClientIpProvisioningMode;
            mP2pClientEapolIpInfo = p2pClientEapolIpInfo;
        }

        @Override
        public void onIpClientCreated(IIpClient ipClient) {
            mHandler.post(() -> {
                if (mIpClientStartIndex != mStartIndex) {
                    // This start request is obsolete
                    return;
                }
                mIpClient = ipClient;

                ProvisioningConfiguration config = null;
                switch (mGroupClientIpProvisioningMode) {
                    case GROUP_CLIENT_IP_PROVISIONING_MODE_IPV6_LINK_LOCAL:
                        config = new ProvisioningConfiguration.Builder()
                                .withoutIPv4()
                                .withIpv6LinkLocalOnly()
                                .withRandomMacAddress()
                                .withUniqueEui64AddressesOnly()
                                .build();
                        break;
                    case GROUP_CLIENT_IP_PROVISIONING_MODE_IPV4_DHCP:
                    default:
                        // DHCP IPV4 by default.
                        if (mP2pClientEapolIpInfo != null) {
                            // Start provisioning with IP address received via EAPOL-Key exchange
                            final StaticIpConfiguration staticIpConfig =
                                    makeStaticIpConfigurationFromEapolIpAddressInfo(
                                            mP2pClientEapolIpInfo);
                            if (staticIpConfig != null) {
                                config = new ProvisioningConfiguration.Builder()
                                        .withoutIpReachabilityMonitor()
                                        .withStaticConfiguration(staticIpConfig).build();
                            }
                        }
                        if (config == null) {
                            // start DHCP provisioning
                            config = new ProvisioningConfiguration.Builder()
                                    .withoutIpReachabilityMonitor()
                                    .withPreDhcpAction(30 * 1000)
                                    .withProvisioningTimeoutMs(36 * 1000)
                                    .build();
                        }
                }

                try {
                    mIpClient.startProvisioning(config.toStableParcelable());
                } catch (RemoteException e) {
                    e.rethrowFromSystemServer();
                }
            });
        }

        @Override
        public void onPreDhcpAction() {
            mP2pStateMachine.sendMessage(IPC_PRE_DHCP_ACTION);
        }
        @Override
        public void onPostDhcpAction() {
            mP2pStateMachine.sendMessage(IPC_POST_DHCP_ACTION);
        }
        @Override
        public void onNewDhcpResults(DhcpResultsParcelable dhcpResults) {
            mP2pStateMachine.sendMessage(IPC_DHCP_RESULTS, dhcpResults);
        }
        @Override
        public void onProvisioningSuccess(LinkProperties newLp) {
            mP2pStateMachine.sendMessage(IPC_PROVISIONING_SUCCESS, newLp);
        }
        @Override
        public void onProvisioningFailure(LinkProperties newLp) {
            mP2pStateMachine.sendMessage(IPC_PROVISIONING_FAILURE);
        }
    }

    /**
     * Get a reference to handler. This is used by a client to establish
     * an AsyncChannel communication with WifiP2pService
     */
    @Override
    public Messenger getMessenger(final IBinder binder, final String packageName, Bundle extras) {
        enforceAccessPermission();
        enforceChangePermission();

        int callerUid = getMockableCallingUid();
        int uidToUse = callerUid;
        String packageNameToUse = packageName;

        // if we're being called from the SYSTEM_UID then allow usage of the AttributionSource to
        // locate the original caller.
        if (SdkLevel.isAtLeastS() && UserHandle.getAppId(callerUid) == Process.SYSTEM_UID) {
            if (extras == null) {
                throw new SecurityException("extras bundle is null");
            }
            AttributionSource as = extras.getParcelable(
                    WifiManager.EXTRA_PARAM_KEY_ATTRIBUTION_SOURCE);
            if (as == null) {
                throw new SecurityException(
                        "WifiP2pManager getMessenger attributionSource is null");
            }

            if (!as.checkCallingUid()) {
                throw new SecurityException("WifiP2pManager getMessenger invalid (checkCallingUid "
                        + "fails) attribution source=" + as);
            }

            // an attribution chain is either of size 1: unregistered (valid by definition) or
            // size >1: in which case all are validated.
            if (as.getNext() != null) {
                AttributionSource asIt = as;
                AttributionSource asLast = as;
                do {
                    if (!asIt.isTrusted(mContext)) {
                        throw new SecurityException("WifiP2pManager getMessenger invalid "
                                + "(isTrusted fails) attribution source=" + asIt);
                    }
                    asIt = asIt.getNext();
                    if (asIt != null) asLast = asIt;
                } while (asIt != null);

                // use the last AttributionSource in the chain - i.e. the original caller
                uidToUse = asLast.getUid();
                packageNameToUse = asLast.getPackageName();
            }
        }

        // get the DisplayId of the caller (if available)
        int displayId = Display.DEFAULT_DISPLAY;
        if (mWifiPermissionsUtil.isSystem(packageName, callerUid)) {
            displayId = extras.getInt(WifiP2pManager.EXTRA_PARAM_KEY_DISPLAY_ID,
                    Display.DEFAULT_DISPLAY);
        }

        synchronized (mLock) {
            final Messenger messenger = new Messenger(mClientHandler);
            if (mVerboseLoggingEnabled) {
                Log.d(TAG, "getMessenger: uid=" + getCallingUid() + ", binder=" + binder
                        + ", messenger=" + messenger);
            }

            IBinder.DeathRecipient dr = () -> {
                if (mVerboseLoggingEnabled) Log.d(TAG, "binderDied: binder=" + binder);
                close(binder);
            };

            WorkSource ws = packageNameToUse != null
                    ? new WorkSource(uidToUse, packageNameToUse)
                    : new WorkSource(uidToUse);
            try {
                binder.linkToDeath(dr, 0);
                mDeathDataByBinder.put(binder,
                        new DeathHandlerData(callerUid, dr, messenger, ws, displayId));
            } catch (RemoteException e) {
                Log.e(TAG, "Error on linkToDeath: e=" + e);
                // fall-through here - won't clean up
            }
            return messenger;
        }
    }

    /**
     * Get a reference to handler. This is used by a ClientModeImpl to establish
     * an AsyncChannel communication with P2pStateMachine
     * @hide
     */
    @Override
    public Messenger getP2pStateMachineMessenger() {
        enforceNetworkStackOrLocationHardwarePermission();
        enforceAccessPermission();
        enforceChangePermission();
        return new Messenger(mP2pStateMachine.getHandler());
    }

    /**
     * Clean-up the state and configuration requested by the closing app. Takes same action as
     * when the app dies (binder death).
     */
    @Override
    public void close(IBinder binder) {
        enforceAccessPermission();
        enforceChangePermission();

        DeathHandlerData dhd;
        synchronized (mLock) {
            Log.d(TAG, "close binder:" + binder + " from mDeathDataByBinder:" + mDeathDataByBinder);
            dhd = mDeathDataByBinder.get(binder);
            if (dhd == null) {
                Log.w(TAG, "close(): no death recipient for binder");
                return;
            }

            binder.unlinkToDeath(dhd.mDeathRecipient, 0);
            mDeathDataByBinder.remove(binder);
            updateWorkSourceByUid(Binder.getCallingUid(), false);
            mP2pStateMachine.sendMessage(REMOVE_CLIENT_INFO, 0, 0, binder);

            if (SdkLevel.isAtLeastS()) {
                AttributionSource source = mClientAttributionSource.remove(binder);
                if (null != source) {
                    mVendorElements.remove(source.getPackageName());
                }
            }

            // clean-up if there are no more clients registered
            // TODO: what does the ClientModeImpl client do? It isn't tracked through here!
            if (dhd.mMessenger != null && mDeathDataByBinder.isEmpty()) {
                try {
                    dhd.mMessenger.send(
                            mClientHandler.obtainMessage(WifiP2pManager.STOP_DISCOVERY));
                    dhd.mMessenger.send(mClientHandler.obtainMessage(WifiP2pManager.REMOVE_GROUP));
                } catch (RemoteException e) {
                    Log.e(TAG, "close: Failed sending clean-up commands: e=" + e);
                }
                mP2pStateMachine.sendMessage(DISABLE_P2P);
            }
        }
    }

    /** This is used to provide information to drivers to optimize performance depending
     * on the current mode of operation.
     * 0 - disabled
     * 1 - source operation
     * 2 - sink operation
     *
     * As an example, the driver could reduce the channel dwell time during scanning
     * when acting as a source or sink to minimize impact on miracast.
     * @param int mode of operation
     */
    @Override
    public void setMiracastMode(int mode) {
        checkConfigureWifiDisplayPermission();
        mP2pStateMachine.sendMessage(SET_MIRACAST_MODE, mode);
        if (mWifiChannel != null) {
            mWifiChannel.sendMessage(WifiP2pServiceImpl.SET_MIRACAST_MODE, mode);
        } else {
            Log.e(TAG, "setMiracastMode(): WifiChannel is null");
        }
    }

    @Override
    public void checkConfigureWifiDisplayPermission() {
        if (!getWfdPermission(Binder.getCallingUid())) {
            throw new SecurityException("Wifi Display Permission denied for uid = "
                    + Binder.getCallingUid());
        }
    }

    /**
     * see {@link android.net.wifi.p2p.WifiP2pManager#getSupportedFeatures()}
     */
    @Override
    public long getSupportedFeatures() {
        return mWifiNative.getSupportedFeatures();
    }

    /**
     * See {@link WifiP2pManager#registerWifiP2pListener(Executor, WifiP2pManager.WifiP2pListener)}
     */
    @Override
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    public void registerWifiP2pListener(IWifiP2pListener listener, String packageName,
            Bundle extras) {
        if (!SdkLevel.isAtLeastT()) {
            throw new UnsupportedOperationException();
        }
        if (listener == null) {
            throw new IllegalArgumentException("listener should not be null");
        }
        mWifiPermissionsUtil.enforceNearbyDevicesPermission(
                extras.getParcelable(WifiManager.EXTRA_PARAM_KEY_ATTRIBUTION_SOURCE), false,
                TAG + " registerWifiP2pListener");
        Log.i(TAG, "registerWifiP2pListener uid=" + Binder.getCallingUid());
        mWifiP2pListeners.register(listener);
    }

    /**
     * See {@link WifiP2pManager#unregisterWifiP2pListener(WifiP2pManager.WifiP2pListener)}
     */
    @Override
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    public void unregisterWifiP2pListener(IWifiP2pListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        Log.i(TAG, "unregisterWifiP2pListener uid=" + Binder.getCallingUid());
        mWifiP2pListeners.unregister(listener);
    }

    private void onStateChanged(boolean p2pEnabled) {
        int numCallbacks = mWifiP2pListeners.beginBroadcast();
        for (int i = 0; i < numCallbacks; i++) {
            try {
                mWifiP2pListeners.getBroadcastItem(i).onStateChanged(p2pEnabled);
            } catch (RemoteException e) {
                Log.e(TAG, "Failure calling onStateChanged" + e);
            }
        }
        mWifiP2pListeners.finishBroadcast();
    }

    private void onDiscoveryStateChanged(boolean started) {
        int numCallbacks = mWifiP2pListeners.beginBroadcast();
        for (int i = 0; i < numCallbacks; i++) {
            try {
                mWifiP2pListeners.getBroadcastItem(i).onDiscoveryStateChanged(started);
            } catch (RemoteException e) {
                Log.e(TAG, "Failure calling onDiscoveryStateChanged" + e);
            }
        }
        mWifiP2pListeners.finishBroadcast();
    }

    private void onListenStateChanged(boolean started) {
        int numCallbacks = mWifiP2pListeners.beginBroadcast();
        for (int i = 0; i < numCallbacks; i++) {
            try {
                mWifiP2pListeners.getBroadcastItem(i).onListenStateChanged(started);
            } catch (RemoteException e) {
                Log.e(TAG, "Failure calling onListenStateChanged" + e);
            }
        }
        mWifiP2pListeners.finishBroadcast();
    }

    private void onDeviceConfigurationChanged(WifiP2pDevice p2pDevice) {
        int numCallbacks = mWifiP2pListeners.beginBroadcast();
        for (int i = 0; i < numCallbacks; i++) {
            try {
                mWifiP2pListeners.getBroadcastItem(i).onDeviceConfigurationChanged(p2pDevice);
            } catch (RemoteException e) {
                Log.e(TAG, "Failure calling onDeviceConfigurationChanged" + e);
            }
        }
        mWifiP2pListeners.finishBroadcast();
    }

    private void onPeerListChanged(WifiP2pDeviceList p2pDeviceList) {
        int numCallbacks = mWifiP2pListeners.beginBroadcast();
        for (int i = 0; i < numCallbacks; i++) {
            try {
                mWifiP2pListeners.getBroadcastItem(i).onPeerListChanged(p2pDeviceList);
            } catch (RemoteException e) {
                Log.e(TAG, "Failure calling onPeerListChanged" + e);
            }
        }
        mWifiP2pListeners.finishBroadcast();
    }

    private void onPersistentGroupsChanged(WifiP2pGroupList p2pGroupList) {
        int numCallbacks = mWifiP2pListeners.beginBroadcast();
        for (int i = 0; i < numCallbacks; i++) {
            try {
                mWifiP2pListeners.getBroadcastItem(i).onPersistentGroupsChanged(p2pGroupList);
            } catch (RemoteException e) {
                Log.e(TAG, "Failure calling onPersistentGroupsChanged" + e);
            }
        }
        mWifiP2pListeners.finishBroadcast();
    }

    private void onGroupCreating() {
        int numCallbacks = mWifiP2pListeners.beginBroadcast();
        for (int i = 0; i < numCallbacks; i++) {
            try {
                mWifiP2pListeners.getBroadcastItem(i).onGroupCreating();
            } catch (RemoteException e) {
                Log.e(TAG, "Failure calling onGroupCreating" + e);
            }
        }
        mWifiP2pListeners.finishBroadcast();
    }

    private void onGroupNegotiationRejectedByUser() {
        int numCallbacks = mWifiP2pListeners.beginBroadcast();
        for (int i = 0; i < numCallbacks; i++) {
            try {
                mWifiP2pListeners.getBroadcastItem(i).onGroupNegotiationRejectedByUser();
            } catch (RemoteException e) {
                Log.e(TAG, "Failure calling onGroupNegotiationRejectedByUser" + e);
            }
        }
        mWifiP2pListeners.finishBroadcast();
    }

    private void onGroupCreationFailed() {
        int numCallbacks = mWifiP2pListeners.beginBroadcast();
        for (int i = 0; i < numCallbacks; i++) {
            try {
                mWifiP2pListeners.getBroadcastItem(i).onGroupCreationFailed();
            } catch (RemoteException e) {
                Log.e(TAG, "Failure calling onGroupCreationFailed" + e);
            }
        }
        mWifiP2pListeners.finishBroadcast();
    }

    private void onGroupCreated(WifiP2pInfo p2pInfo, WifiP2pGroup p2pGroup) {
        int numCallbacks = mWifiP2pListeners.beginBroadcast();
        for (int i = 0; i < numCallbacks; i++) {
            try {
                mWifiP2pListeners.getBroadcastItem(i).onGroupCreated(p2pInfo, p2pGroup);
            } catch (RemoteException e) {
                Log.e(TAG, "Failure calling onGroupCreated" + e);
            }
        }
        mWifiP2pListeners.finishBroadcast();
    }

    private void onPeerClientJoined(WifiP2pInfo p2pInfo, WifiP2pGroup p2pGroup) {
        int numCallbacks = mWifiP2pListeners.beginBroadcast();
        for (int i = 0; i < numCallbacks; i++) {
            try {
                mWifiP2pListeners.getBroadcastItem(i).onPeerClientJoined(p2pInfo, p2pGroup);
            } catch (RemoteException e) {
                Log.e(TAG, "Failure calling onPeerClientJoined" + e);
            }
        }
        mWifiP2pListeners.finishBroadcast();
    }

    private void onPeerClientDisconnected(WifiP2pInfo p2pInfo, WifiP2pGroup p2pGroup) {
        int numCallbacks = mWifiP2pListeners.beginBroadcast();
        for (int i = 0; i < numCallbacks; i++) {
            try {
                mWifiP2pListeners.getBroadcastItem(i).onPeerClientDisconnected(p2pInfo,
                        p2pGroup);
            } catch (RemoteException e) {
                Log.e(TAG, "Failure calling onPeerClientDisconnected" + e);
            }
        }
        mWifiP2pListeners.finishBroadcast();
    }

    private void onFrequencyChanged(WifiP2pInfo p2pInfo, WifiP2pGroup p2pGroup) {
        int numCallbacks = mWifiP2pListeners.beginBroadcast();
        for (int i = 0; i < numCallbacks; i++) {
            try {
                mWifiP2pListeners.getBroadcastItem(i).onFrequencyChanged(p2pInfo, p2pGroup);
            } catch (RemoteException e) {
                Log.e(TAG, "Failure calling onFrequencyChanged" + e);
            }
        }
        mWifiP2pListeners.finishBroadcast();
    }

    private void onGroupRemoved() {
        int numCallbacks = mWifiP2pListeners.beginBroadcast();
        for (int i = 0; i < numCallbacks; i++) {
            try {
                mWifiP2pListeners.getBroadcastItem(i).onGroupRemoved();
            } catch (RemoteException e) {
                Log.e(TAG, "Failure calling onGroupRemoved" + e);
            }
        }
        mWifiP2pListeners.finishBroadcast();
    }

    private boolean getWfdPermission(int uid) {
        WifiPermissionsWrapper wifiPermissionsWrapper = mWifiInjector.getWifiPermissionsWrapper();
        return wifiPermissionsWrapper.getUidPermission(
                android.Manifest.permission.CONFIGURE_WIFI_DISPLAY, uid)
                != PackageManager.PERMISSION_DENIED;
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.DUMP)
                != PackageManager.PERMISSION_GRANTED) {
            pw.println("Permission Denial: can't dump WifiP2pService from from pid="
                    + Binder.getCallingPid()
                    + ", uid=" + Binder.getCallingUid());
            return;
        }
        mP2pStateMachine.dump(fd, pw, args);
        mWifiP2pMetrics.dump(pw);
        pw.println("mAutonomousGroup " + mAutonomousGroup);
        pw.println("mJoinExistingGroup " + mJoinExistingGroup);
        pw.println("mDiscoveryStarted " + mDiscoveryStarted);
        pw.println("mListenStarted " + mListenStarted);
        pw.println("mDetailedState " + mDetailedState);
        pw.println("mTemporarilyDisconnectedWifi " + mTemporarilyDisconnectedWifi);
        pw.println("mServiceDiscReqId " + mServiceDiscReqId);
        pw.println("mDeathDataByBinder " + mDeathDataByBinder);
        pw.println("mClientInfoList " + mClientInfoList.size());
        pw.println("mActiveClients " + mActiveClients);
        pw.println("mPeerAuthorizingTimestamp" + mPeerAuthorizingTimestamp);
        pw.println();

        final IIpClient ipClient = mIpClient;
        if (ipClient != null) {
            pw.println("mIpClient:");
            IpClientUtil.dumpIpClient(ipClient, fd, pw, args);
        }
    }

    @Override
    public int handleShellCommand(@NonNull ParcelFileDescriptor in,
            @NonNull ParcelFileDescriptor out, @NonNull ParcelFileDescriptor err,
            @NonNull String[] args) {
        if (!mIsBootComplete) {
            Log.w(TAG, "Received shell command when boot is not complete!");
            return -1;
        }

        WifiP2pShellCommand shellCommand = new WifiP2pShellCommand(mContext);
        return shellCommand.exec(
                this, in.getFileDescriptor(), out.getFileDescriptor(), err.getFileDescriptor(),
                args);
    }

    /**
     * Handles interaction with ClientModeImpl
     */
    private class P2pStateMachine extends StateMachine {

        private final DefaultState mDefaultState = new DefaultState(mThreshold, mThreadLocalLog);
        private final P2pNotSupportedState mP2pNotSupportedState =
                new P2pNotSupportedState(mThreshold, mThreadLocalLog);
        private final P2pDisablingState mP2pDisablingState =
                new P2pDisablingState(mThreshold, mThreadLocalLog);
        private final P2pDisabledContainerState mP2pDisabledContainerState =
                new P2pDisabledContainerState(mThreshold, mThreadLocalLog);
        private final P2pDisabledState mP2pDisabledState =
                new P2pDisabledState(mThreshold, mThreadLocalLog);
        private final WaitingState mWaitingState = new WaitingState(this);
        private final P2pEnabledState mP2pEnabledState =
                new P2pEnabledState(mThreshold, mThreadLocalLog);
        // Inactive is when p2p is enabled with no connectivity
        private final InactiveState mInactiveState = new InactiveState(mThreshold, mThreadLocalLog);
        private final GroupCreatingState mGroupCreatingState =
                new GroupCreatingState(mThreshold, mThreadLocalLog);
        private final UserAuthorizingInviteRequestState mUserAuthorizingInviteRequestState =
                new UserAuthorizingInviteRequestState(mThreshold, mThreadLocalLog);
        private final UserAuthorizingNegotiationRequestState
                mUserAuthorizingNegotiationRequestState =
                new UserAuthorizingNegotiationRequestState(mThreshold, mThreadLocalLog);
        private final ProvisionDiscoveryState mProvisionDiscoveryState =
                new ProvisionDiscoveryState(mThreshold, mThreadLocalLog);
        private final GroupNegotiationState mGroupNegotiationState =
                new GroupNegotiationState(mThreshold, mThreadLocalLog);
        private final FrequencyConflictState mFrequencyConflictState =
                new FrequencyConflictState(mThreshold, mThreadLocalLog);

        private final GroupCreatedState mGroupCreatedState =
                new GroupCreatedState(mThreshold, mThreadLocalLog);
        private final UserAuthorizingJoinState mUserAuthorizingJoinState =
                new UserAuthorizingJoinState(mThreshold, mThreadLocalLog);
        private final OngoingGroupRemovalState mOngoingGroupRemovalState =
                new OngoingGroupRemovalState(mThreshold, mThreadLocalLog);
        private final P2pRejectWaitState mP2pRejectWaitState =
                new P2pRejectWaitState(mThreshold, mThreadLocalLog);

        private final WifiP2pMonitor mWifiMonitor = mWifiInjector.getWifiP2pMonitor();

        private final WifiP2pDeviceList mPeers = new WifiP2pDeviceList();
        private String mInterfaceName;
        private TetheringEventCallback mTetheringEventCallback =
                new TetheringManager.TetheringEventCallback() {
                    @Override
                    public void onLocalOnlyInterfacesChanged(Set<TetheringInterface> interfaces) {
                        ArrayList<String> ifaceList = interfaces.stream().map(
                                p -> p.getInterface()).collect(
                                Collectors.toCollection(ArrayList::new));
                        logd(getName() + " Tethering localOnlyInterfacesChanged"
                                + " callback for ifaceList: " + ifaceList);
                        sendMessage(TETHER_INTERFACE_STATE_CHANGED, ifaceList);
                    }
                    @Override
                    public void onClientsChanged(Collection<TetheredClient> clients) {
                        synchronized (mLock) {
                            sendMessage(TETHER_INTERFACE_CLIENTS_CHANGED, clients);
                        }
                    }
                };

        private final List<CoexUnsafeChannel> mCoexUnsafeChannels = new ArrayList<>();
        private int mUserListenChannel = 0;
        private int mUserOperatingChannel = 0;

        // During a connection, supplicant can tell us that a device was lost. From a supplicant's
        // perspective, the discovery stops during connection and it purges device since it does
        // not get latest updates about the device without being in discovery state.
        // From the framework perspective, the device is still there since we are connecting or
        // connected to it. so we keep these devices in a separate list, so that they are removed
        // when connection is cancelled or lost
        private final WifiP2pDeviceList mPeersLostDuringConnection = new WifiP2pDeviceList();
        private final WifiP2pGroupList mGroups = new WifiP2pGroupList(null,
                new GroupDeleteListener() {
                    @Override
                    public void onDeleteGroup(int netId) {
                        if (mVerboseLoggingEnabled) {
                            logd("called onDeleteGroup() netId=" + netId);
                        }
                        mWifiNative.removeP2pNetwork(netId);
                        mWifiNative.saveConfig();
                        sendP2pPersistentGroupsChangedBroadcast();
                    }
                });
        private final WifiP2pInfo mWifiP2pInfo = new WifiP2pInfo();
        private WifiP2pGroup mGroup;
        // Is wifi on or off.
        private boolean mIsWifiEnabled = false;

        // Saved WifiP2pConfig for an ongoing peer connection. This will never be null.
        // The deviceAddress will be an empty string when the device is inactive
        // or if it is connected without any ongoing join request
        private WifiP2pConfig mSavedPeerConfig = new WifiP2pConfig();
        private AlertDialog mLegacyInvitationDialog = null;
        private WifiDialogManager.DialogHandle mInvitationDialogHandle = null;

        P2pStateMachine(String name, Looper looper, boolean p2pSupported) {
            super(name, looper);

            // CHECKSTYLE:OFF IndentationCheck
            addState(mDefaultState);
                addState(mP2pNotSupportedState, mDefaultState);
                addState(mP2pDisablingState, mDefaultState);
                addState(mP2pDisabledContainerState, mDefaultState);
                    addState(mP2pDisabledState, mP2pDisabledContainerState);
                    addState(mWaitingState, mP2pDisabledContainerState);
                addState(mP2pEnabledState, mDefaultState);
                    addState(mInactiveState, mP2pEnabledState);
                    addState(mGroupCreatingState, mP2pEnabledState);
                        addState(mUserAuthorizingInviteRequestState, mGroupCreatingState);
                        addState(mUserAuthorizingNegotiationRequestState, mGroupCreatingState);
                        addState(mProvisionDiscoveryState, mGroupCreatingState);
                        addState(mGroupNegotiationState, mGroupCreatingState);
                        addState(mFrequencyConflictState, mGroupCreatingState);
                        addState(mP2pRejectWaitState, mGroupCreatingState);
                    addState(mGroupCreatedState, mP2pEnabledState);
                        addState(mUserAuthorizingJoinState, mGroupCreatedState);
                        addState(mOngoingGroupRemovalState, mGroupCreatedState);
            // CHECKSTYLE:ON IndentationCheck

            if (p2pSupported) {
                setInitialState(mP2pDisabledState);
            } else {
                setInitialState(mP2pNotSupportedState);
            }
            setLogRecSize(100);

            if (p2pSupported) {
                // Init p2p idle shutdown message
                mP2pIdleShutdownMessage = new WakeupMessage(mContext,
                                  this.getHandler(),
                                  P2P_IDLE_SHUTDOWN_MESSAGE_TIMEOUT_TAG,
                                  CMD_P2P_IDLE_SHUTDOWN);

                // Register for wifi on/off broadcasts
                mContext.registerReceiver(new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        int wifistate = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE,
                                WifiManager.WIFI_STATE_UNKNOWN);
                        if (wifistate == WifiManager.WIFI_STATE_ENABLED) {
                            mIsWifiEnabled = true;
                        } else {
                            mIsWifiEnabled = false;
                            // P2P can be established even when infra STA is disabled.
                            // This implies that STA might be torn down by P2P
                            // (e.g., if STA was active initially).
                            // Check availability to determine whether to stop P2P
                            // upon receiving a Wi-Fi off signal.
                            if (!isWifiP2pAvailable()) {
                                sendMessage(DISABLE_P2P);
                            } else {
                                Log.i(TAG, "Infra STA is disabled but keep P2P on since"
                                        + " d2d is allowed when infra sta is disabled");
                            }
                        }
                        if (wifistate == WifiManager.WIFI_STATE_ENABLED
                                || wifistate == WifiManager.WIFI_STATE_DISABLING) {
                            checkAndSendP2pStateChangedBroadcast();
                        }
                    }
                }, new IntentFilter(WifiManager.WIFI_STATE_CHANGED_ACTION));
                mSettingsConfigStore.registerChangeListener(D2D_ALLOWED_WHEN_INFRA_STA_DISABLED,
                        new D2DAllowWhenInfraStaDisabledValueListener(), this.getHandler());
                // Register for location mode on/off broadcasts
                mContext.registerReceiver(new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        /* if location mode is off, ongoing discovery should be stopped.
                         * possible ongoing discovery:
                         * - peer discovery
                         * - service discovery
                         * - group joining scan in native service
                         */
                        if (!mWifiPermissionsUtil.isLocationModeEnabled()
                                && !SdkLevel.isAtLeastT()) {
                            sendMessage(WifiP2pManager.STOP_DISCOVERY);
                        }
                    }
                }, new IntentFilter(LocationManager.MODE_CHANGED_ACTION));
                // Register for tethering state
                if (!SdkLevel.isAtLeastS()) {
                    mContext.registerReceiver(new BroadcastReceiver() {
                        @Override
                        public void onReceive(Context context, Intent intent) {
                            final ArrayList<String> interfaces = intent.getStringArrayListExtra(
                                    TetheringManager.EXTRA_ACTIVE_LOCAL_ONLY);

                            sendMessage(TETHER_INTERFACE_STATE_CHANGED, interfaces);
                        }
                    }, new IntentFilter(TetheringManager.ACTION_TETHER_STATE_CHANGED));
                }
                mSettingsConfigStore.registerChangeListener(
                        WIFI_VERBOSE_LOGGING_ENABLED,
                        (key, newValue) -> enableVerboseLogging(newValue),
                        getHandler());
                if (SdkLevel.isAtLeastS()) {
                    mCoexManager.registerCoexListener(this::checkCoexUnsafeChannels);
                }
                if (SdkLevel.isAtLeastT()) {
                    mContext.registerReceiver(
                            new BroadcastReceiver() {
                                @Override
                                public void onReceive(Context context, Intent intent) {
                                    Log.d(TAG, "user restrictions changed");
                                    onUserRestrictionsChanged();
                                }
                            },
                            new IntentFilter(UserManager.ACTION_USER_RESTRICTIONS_CHANGED));
                    mIsP2pDisallowedByAdmin = mUserManager.getUserRestrictions()
                            .getBoolean(UserManager.DISALLOW_WIFI_DIRECT);
                }
            }
        }

        /**
         * Find which user restrictions have changed and take corresponding actions
         */
        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        private void onUserRestrictionsChanged() {
            final Bundle restrictions = mUserManager.getUserRestrictions();
            final boolean newIsP2pDisallowedByAdmin =
                    restrictions.getBoolean(UserManager.DISALLOW_WIFI_DIRECT);

            if (newIsP2pDisallowedByAdmin != mIsP2pDisallowedByAdmin) {
                if (newIsP2pDisallowedByAdmin) {
                    Log.i(TAG, "Disable P2P: DISALLOW_WIFI_DIRECT set");
                    sendMessage(DISABLE_P2P);
                }
                mIsP2pDisallowedByAdmin = newIsP2pDisallowedByAdmin;
                checkAndSendP2pStateChangedBroadcast();
            }
        }

        @Override
        protected String getLogRecString(Message msg) {
            StringBuilder sb = new StringBuilder();
            sb.append("sender=").append(getCallingPkgName(msg.sendingUid, msg.replyTo))
                    .append("(").append(msg.sendingUid).append(")");
            return sb.toString();
        }

        @Override
        protected boolean recordLogRec(Message msg) {
            // Filter unnecessary records to avoid overwhelming the buffer.
            switch (msg.what) {
                case WifiP2pManager.REQUEST_PEERS:
                case WifiP2pMonitor.P2P_DEVICE_FOUND_EVENT:
                case WifiP2pMonitor.P2P_DEVICE_LOST_EVENT:
                    return false;
                default:
                    return true;
            }
        }

        @Override
        protected String getWhatToString(int what) {
            switch (what) {
                case AsyncChannel.CMD_CHANNEL_DISCONNECTED:
                    return "AsyncChannel.CMD_CHANNEL_DISCONNECTED";
                case AsyncChannel.CMD_CHANNEL_FULL_CONNECTION:
                    return "AsyncChannel.CMD_CHANNEL_FULL_CONNECTION";
                case AsyncChannel.CMD_CHANNEL_HALF_CONNECTED:
                    return "AsyncChannel.CMD_CHANNEL_HALF_CONNECTED";
                case BLOCK_DISCOVERY:
                    return "BLOCK_DISCOVERY";
                case CMD_P2P_IDLE_SHUTDOWN:
                    return "CMD_P2P_IDLE_SHUTDOWN";
                case DISABLE_P2P:
                    return "DISABLE_P2P";
                case DISABLE_P2P_TIMED_OUT:
                    return "DISABLE_P2P_TIMED_OUT";
                case DISCONNECT_WIFI_RESPONSE:
                    return "DISCONNECT_WIFI_RESPONSE";
                case DROP_WIFI_USER_ACCEPT:
                    return "DROP_WIFI_USER_ACCEPT";
                case DROP_WIFI_USER_REJECT:
                    return "DROP_WIFI_USER_REJECT";
                case ENABLE_P2P:
                    return "ENABLE_P2P";
                case GROUP_CREATING_TIMED_OUT:
                    return "GROUP_CREATING_TIMED_OUT";
                case IPC_DHCP_RESULTS:
                    return "IPC_DHCP_RESULTS";
                case IPC_POST_DHCP_ACTION:
                    return "IPC_POST_DHCP_ACTION";
                case IPC_PRE_DHCP_ACTION:
                    return "IPC_PRE_DHCP_ACTION";
                case IPC_PROVISIONING_FAILURE:
                    return "IPC_PROVISIONING_FAILURE";
                case IPC_PROVISIONING_SUCCESS:
                    return "IPC_PROVISIONING_SUCCESS";
                case PEER_CONNECTION_USER_ACCEPT:
                    return "PEER_CONNECTION_USER_ACCEPT";
                case PEER_CONNECTION_USER_CONFIRM:
                    return "PEER_CONNECTION_USER_CONFIRM";
                case PEER_CONNECTION_USER_REJECT:
                    return "PEER_CONNECTION_USER_REJECT";
                case REMOVE_CLIENT_INFO:
                    return "REMOVE_CLIENT_INFO";
                case SET_MIRACAST_MODE:
                    return "SET_MIRACAST_MODE";
                case TETHER_INTERFACE_STATE_CHANGED:
                    return "TETHER_INTERFACE_STATE_CHANGED";
                case TETHER_INTERFACE_CLIENTS_CHANGED:
                    return "TETHER_INTERFACE_CLIENTS_CHANGED";
                case UPDATE_P2P_DISALLOWED_CHANNELS:
                    return "UPDATE_P2P_DISALLOWED_CHANNELS";
                case WifiP2pManager.ADD_EXTERNAL_APPROVER:
                    return "WifiP2pManager.ADD_EXTERNAL_APPROVER";
                case WifiP2pManager.ADD_LOCAL_SERVICE:
                    return "WifiP2pManager.ADD_LOCAL_SERVICE";
                case WifiP2pManager.ADD_SERVICE_REQUEST:
                    return "WifiP2pManager.ADD_SERVICE_REQUEST";
                case WifiP2pManager.CANCEL_CONNECT:
                    return "WifiP2pManager.CANCEL_CONNECT";
                case WifiP2pManager.CLEAR_LOCAL_SERVICES:
                    return "WifiP2pManager.CLEAR_LOCAL_SERVICES";
                case WifiP2pManager.CLEAR_SERVICE_REQUESTS:
                    return "WifiP2pManager.CLEAR_SERVICE_REQUESTS";
                case WifiP2pManager.CONNECT:
                    return "WifiP2pManager.CONNECT";
                case WifiP2pManager.CREATE_GROUP:
                    return "WifiP2pManager.CREATE_GROUP";
                case WifiP2pManager.DELETE_PERSISTENT_GROUP:
                    return "WifiP2pManager.DELETE_PERSISTENT_GROUP";
                case WifiP2pManager.DISCOVER_PEERS:
                    return "WifiP2pManager.DISCOVER_PEERS";
                case WifiP2pManager.DISCOVER_SERVICES:
                    return "WifiP2pManager.DISCOVER_SERVICES";
                case WifiP2pManager.EXTERNAL_APPROVER_ATTACH:
                    return "WifiP2pManager.EXTERNAL_APPROVER_ATTACH";
                case WifiP2pManager.FACTORY_RESET:
                    return "WifiP2pManager.FACTORY_RESET";
                case WifiP2pManager.GET_HANDOVER_REQUEST:
                    return "WifiP2pManager.GET_HANDOVER_REQUEST";
                case WifiP2pManager.GET_HANDOVER_SELECT:
                    return "WifiP2pManager.GET_HANDOVER_SELECT";
                case WifiP2pManager.INITIATOR_REPORT_NFC_HANDOVER:
                    return "WifiP2pManager.INITIATOR_REPORT_NFC_HANDOVER";
                case WifiP2pManager.REMOVE_EXTERNAL_APPROVER:
                    return "WifiP2pManager.REMOVE_EXTERNAL_APPROVER";
                case WifiP2pManager.REMOVE_GROUP:
                    return "WifiP2pManager.REMOVE_GROUP";
                case WifiP2pManager.REMOVE_LOCAL_SERVICE:
                    return "WifiP2pManager.REMOVE_LOCAL_SERVICE";
                case WifiP2pManager.REMOVE_SERVICE_REQUEST:
                    return "WifiP2pManager.REMOVE_SERVICE_REQUEST";
                case WifiP2pManager.REQUEST_CONNECTION_INFO:
                    return "WifiP2pManager.REQUEST_CONNECTION_INFO";
                case WifiP2pManager.REQUEST_DEVICE_INFO:
                    return "WifiP2pManager.REQUEST_DEVICE_INFO";
                case WifiP2pManager.REQUEST_DISCOVERY_STATE:
                    return "WifiP2pManager.REQUEST_DISCOVERY_STATE";
                case WifiP2pManager.GET_LISTEN_STATE:
                    return "WifiP2pManager.GET_LISTEN_STATE";
                case WifiP2pManager.REQUEST_GROUP_INFO:
                    return "WifiP2pManager.REQUEST_GROUP_INFO";
                case WifiP2pManager.REQUEST_NETWORK_INFO:
                    return "WifiP2pManager.REQUEST_NETWORK_INFO";
                case WifiP2pManager.REQUEST_ONGOING_PEER_CONFIG:
                    return "WifiP2pManager.REQUEST_ONGOING_PEER_CONFIG";
                case WifiP2pManager.REQUEST_P2P_STATE:
                    return "WifiP2pManager.REQUEST_P2P_STATE";
                case WifiP2pManager.REQUEST_PEERS:
                    return "WifiP2pManager.REQUEST_PEERS";
                case WifiP2pManager.REQUEST_PERSISTENT_GROUP_INFO:
                    return "WifiP2pManager.REQUEST_PERSISTENT_GROUP_INFO";
                case WifiP2pManager.RESPONDER_REPORT_NFC_HANDOVER:
                    return "WifiP2pManager.RESPONDER_REPORT_NFC_HANDOVER";
                case WifiP2pManager.SET_CHANNEL:
                    return "WifiP2pManager.SET_CHANNEL";
                case WifiP2pManager.SET_CONNECTION_REQUEST_RESULT:
                    return "WifiP2pManager.SET_CONNECTION_REQUEST_RESULT";
                case WifiP2pManager.SET_DEVICE_NAME:
                    return "WifiP2pManager.SET_DEVICE_NAME";
                case WifiP2pManager.SET_ONGOING_PEER_CONFIG:
                    return "WifiP2pManager.SET_ONGOING_PEER_CONFIG";
                case WifiP2pManager.SET_WFD_INFO:
                    return "WifiP2pManager.SET_WFD_INFO";
                case WifiP2pManager.START_LISTEN:
                    return "WifiP2pManager.START_LISTEN";
                case WifiP2pManager.START_WPS:
                    return "WifiP2pManager.START_WPS";
                case WifiP2pManager.STOP_DISCOVERY:
                    return "WifiP2pManager.STOP_DISCOVERY";
                case WifiP2pManager.STOP_LISTEN:
                    return "WifiP2pManager.STOP_LISTEN";
                case WifiP2pManager.UPDATE_CHANNEL_INFO:
                    return "WifiP2pManager.UPDATE_CHANNEL_INFO";
                case WifiP2pManager.REMOVE_CLIENT:
                    return "WifiP2pManager.REMOVE_CLIENT";
                case WifiP2pMonitor.AP_STA_CONNECTED_EVENT:
                    return "WifiP2pMonitor.AP_STA_CONNECTED_EVENT";
                case WifiP2pMonitor.AP_STA_DISCONNECTED_EVENT:
                    return "WifiP2pMonitor.AP_STA_DISCONNECTED_EVENT";
                case WifiP2pMonitor.P2P_DEVICE_FOUND_EVENT:
                    return "WifiP2pMonitor.P2P_DEVICE_FOUND_EVENT";
                case WifiP2pMonitor.P2P_DEVICE_LOST_EVENT:
                    return "WifiP2pMonitor.P2P_DEVICE_LOST_EVENT";
                case WifiP2pMonitor.P2P_FIND_STOPPED_EVENT:
                    return "WifiP2pMonitor.P2P_FIND_STOPPED_EVENT";
                case WifiP2pMonitor.P2P_GO_NEGOTIATION_FAILURE_EVENT:
                    return "WifiP2pMonitor.P2P_GO_NEGOTIATION_FAILURE_EVENT";
                case WifiP2pMonitor.P2P_GO_NEGOTIATION_REQUEST_EVENT:
                    return "WifiP2pMonitor.P2P_GO_NEGOTIATION_REQUEST_EVENT";
                case WifiP2pMonitor.P2P_GO_NEGOTIATION_SUCCESS_EVENT:
                    return "WifiP2pMonitor.P2P_GO_NEGOTIATION_SUCCESS_EVENT";
                case WifiP2pMonitor.P2P_GROUP_FORMATION_FAILURE_EVENT:
                    return "WifiP2pMonitor.P2P_GROUP_FORMATION_FAILURE_EVENT";
                case WifiP2pMonitor.P2P_GROUP_FORMATION_SUCCESS_EVENT:
                    return "WifiP2pMonitor.P2P_GROUP_FORMATION_SUCCESS_EVENT";
                case WifiP2pMonitor.P2P_GROUP_REMOVED_EVENT:
                    return "WifiP2pMonitor.P2P_GROUP_REMOVED_EVENT";
                case WifiP2pMonitor.P2P_GROUP_STARTED_EVENT:
                    return "WifiP2pMonitor.P2P_GROUP_STARTED_EVENT";
                case WifiP2pMonitor.P2P_INVITATION_RECEIVED_EVENT:
                    return "WifiP2pMonitor.P2P_INVITATION_RECEIVED_EVENT";
                case WifiP2pMonitor.P2P_INVITATION_RESULT_EVENT:
                    return "WifiP2pMonitor.P2P_INVITATION_RESULT_EVENT";
                case WifiP2pMonitor.P2P_PROV_DISC_ENTER_PIN_EVENT:
                    return "WifiP2pMonitor.P2P_PROV_DISC_ENTER_PIN_EVENT";
                case WifiP2pMonitor.P2P_PROV_DISC_FAILURE_EVENT:
                    return "WifiP2pMonitor.P2P_PROV_DISC_FAILURE_EVENT";
                case WifiP2pMonitor.P2P_PROV_DISC_PBC_REQ_EVENT:
                    return "WifiP2pMonitor.P2P_PROV_DISC_PBC_REQ_EVENT";
                case WifiP2pMonitor.P2P_PROV_DISC_PBC_RSP_EVENT:
                    return "WifiP2pMonitor.P2P_PROV_DISC_PBC_RSP_EVENT";
                case WifiP2pMonitor.P2P_PROV_DISC_SHOW_PIN_EVENT:
                    return "WifiP2pMonitor.P2P_PROV_DISC_SHOW_PIN_EVENT";
                case WifiP2pMonitor.P2P_SERV_DISC_RESP_EVENT:
                    return "WifiP2pMonitor.P2P_SERV_DISC_RESP_EVENT";
                case WifiP2pMonitor.SUP_CONNECTION_EVENT:
                    return "WifiP2pMonitor.SUP_CONNECTION_EVENT";
                case WifiP2pMonitor.SUP_DISCONNECTION_EVENT:
                    return "WifiP2pMonitor.SUP_DISCONNECTION_EVENT";
                case WifiP2pMonitor.P2P_FREQUENCY_CHANGED_EVENT:
                    return "WifiP2pMonitor.P2P_FREQUENCY_CHANGED_EVENT";
                case WpsInfo.DISPLAY:
                    return "WpsInfo.DISPLAY";
                case WpsInfo.KEYPAD:
                    return "WpsInfo.KEYPAD";
                case WifiP2pManager.SET_VENDOR_ELEMENTS:
                    return "WifiP2pManager.SET_VENDOR_ELEMENTS";
                case P2P_REJECTION_RESUME_AFTER_DELAY:
                    return "P2P_REJECTION_RESUME_AFTER_DELAY";
                case RunnerState.STATE_ENTER_CMD:
                    return "Enter";
                case RunnerState.STATE_EXIT_CMD:
                    return "Exit";
                default:
                    return "what:" + what;
            }
        }

        private void reportConnectionEventTakeBugReportIfOverlapped(int connectionType,
                WifiP2pConfig config, int groupRole, int uid) {
            if (mWifiP2pMetrics.hasOngoingConnection()) {
                takeBugReportP2pFailureIfNeeded("Wi-Fi BugReport (P2P "
                        + mWifiP2pMetrics.getP2pGroupRoleString()
                        + " overlapping connection attempt)",
                        "new and old connection attempts overlap");
            }
            mWifiP2pMetrics.startConnectionEvent(connectionType, config, groupRole, uid);
        }

        private void takeBugReportP2pFailureIfNeeded(String bugTitle, String bugDetail) {
            if (mWifiInjector.getDeviceConfigFacade().isP2pFailureBugreportEnabled()) {
                mWifiInjector.getWifiDiagnostics().takeBugReport(bugTitle, bugDetail);
            }
        }

        private void logSmMessage(String stateName, Message message) {
            if (mVerboseLoggingEnabled) {
                logd(stateName + " cmd = " + getWhatToString(message.what) + " "
                        + message.toString());
            }
        }

        private void logSmStateName(String currStateName, String targetStateName) {
            if (mVerboseLoggingEnabled) {
                StringBuilder sb = new StringBuilder("State: ").append(currStateName);
                if (!currStateName.equals(targetStateName)) {
                    sb.append(" (target: ").append(targetStateName).append(")");
                }
                logd(sb.toString());
            }
        }

        private void smTransition(State currState, State targetState) {
            if (mVerboseLoggingEnabled) {
                StringBuilder sb = new StringBuilder();
                sb.append("State: ").append(currState.getName()).append(" -> ").append(
                        targetState.getName());
                logd(sb.toString());
            }
            transitionTo(targetState);
        }

        private String getGroupOwnerBandToString(int groupOwnerBand) {
            switch(groupOwnerBand) {
                case WifiP2pConfig.GROUP_OWNER_BAND_AUTO:
                    return "band_auto";
                case WifiP2pConfig.GROUP_OWNER_BAND_2GHZ:
                    return "band_2g";
                case WifiP2pConfig.GROUP_OWNER_BAND_5GHZ:
                    return "band_5g";
                default:
                    return String.valueOf(groupOwnerBand);
            }
        }

        // Clear internal data when P2P is shut down due to wifi off or no client.
        // For idle shutdown case, there are clients and data should be restored when
        // P2P goes back P2pEnabledState.
        // For a real shutdown case which caused by wifi off or no client, those internal
        // data should be cleared because the caller might not clear them, ex. WFD app
        // enables WFD, but does not disable it after leaving the app.
        private void clearP2pInternalDataIfNecessary() {
            if (isWifiP2pAvailable() && !mDeathDataByBinder.isEmpty()) return;

            mThisDevice.wfdInfo = null;
        }

        boolean isP2pDisabled() {
            return getCurrentState() == mP2pDisabledState;
        }

        void scheduleIdleShutdown() {
            if (mP2pIdleShutdownMessage != null) {
                mP2pIdleShutdownMessage.cancel();
                mP2pIdleShutdownMessage.schedule(SystemClock.elapsedRealtime()
                        + P2P_INTERFACE_IDLE_SHUTDOWN_TIMEOUT_MS);
                if (mVerboseLoggingEnabled) {
                    Log.d(TAG, "IdleShutDown message (re)scheduled in "
                            + (P2P_INTERFACE_IDLE_SHUTDOWN_TIMEOUT_MS / 1000) + "s");
                }
            }
            mP2pStateMachine.getHandler().removeMessages(CMD_P2P_IDLE_SHUTDOWN);
        }

        void cancelIdleShutdown() {
            if (mP2pIdleShutdownMessage != null) {
                mP2pIdleShutdownMessage.cancel();
                if (mVerboseLoggingEnabled) {
                    Log.d(TAG, "IdleShutDown message canceled");
                }
            }
            mP2pStateMachine.getHandler().removeMessages(CMD_P2P_IDLE_SHUTDOWN);
        }

        void checkCoexUnsafeChannels() {
            List<CoexUnsafeChannel> unsafeChannels = null;

            // If WIFI DIRECT bit is not set, pass null to clear unsafe channels.
            if (SdkLevel.isAtLeastS()
                    && (mCoexManager.getCoexRestrictions()
                    & WifiManager.COEX_RESTRICTION_WIFI_DIRECT) != 0) {
                unsafeChannels = mCoexManager.getCoexUnsafeChannels();
                Log.d(TAG, "UnsafeChannels: "
                        + unsafeChannels.stream()
                                .map(Object::toString)
                                .collect(Collectors.joining(",")));
            }

            sendMessage(UPDATE_P2P_DISALLOWED_CHANNELS, unsafeChannels);
        }

        /**
         * Enable verbose logging for all sub modules.
         */
        private void enableVerboseLogging(boolean verboseEnabled) {
            mVerboseHalLoggingEnabled = verboseEnabled;
            updateVerboseLoggingEnabled();
            mWifiNative.enableVerboseLogging(mVerboseLoggingEnabled, mVerboseHalLoggingEnabled);
            mWifiMonitor.enableVerboseLogging(mVerboseLoggingEnabled);
            mExternalApproverManager.enableVerboseLogging(mVerboseLoggingEnabled);
        }

        public void registerForWifiMonitorEvents() {
            mWifiMonitor.registerHandler(mInterfaceName,
                    WifiP2pMonitor.AP_STA_CONNECTED_EVENT, getHandler());
            mWifiMonitor.registerHandler(mInterfaceName,
                    WifiP2pMonitor.AP_STA_DISCONNECTED_EVENT, getHandler());
            mWifiMonitor.registerHandler(mInterfaceName,
                    WifiP2pMonitor.P2P_DEVICE_FOUND_EVENT, getHandler());
            mWifiMonitor.registerHandler(mInterfaceName,
                    WifiP2pMonitor.P2P_DEVICE_LOST_EVENT, getHandler());
            mWifiMonitor.registerHandler(mInterfaceName,
                    WifiP2pMonitor.P2P_FIND_STOPPED_EVENT, getHandler());
            mWifiMonitor.registerHandler(mInterfaceName,
                    WifiP2pMonitor.P2P_GO_NEGOTIATION_FAILURE_EVENT, getHandler());
            mWifiMonitor.registerHandler(mInterfaceName,
                    WifiP2pMonitor.P2P_GO_NEGOTIATION_REQUEST_EVENT, getHandler());
            mWifiMonitor.registerHandler(mInterfaceName,
                    WifiP2pMonitor.P2P_GO_NEGOTIATION_SUCCESS_EVENT, getHandler());
            mWifiMonitor.registerHandler(mInterfaceName,
                    WifiP2pMonitor.P2P_GROUP_FORMATION_FAILURE_EVENT, getHandler());
            mWifiMonitor.registerHandler(mInterfaceName,
                    WifiP2pMonitor.P2P_GROUP_FORMATION_SUCCESS_EVENT, getHandler());
            mWifiMonitor.registerHandler(mInterfaceName,
                    WifiP2pMonitor.P2P_GROUP_REMOVED_EVENT, getHandler());
            mWifiMonitor.registerHandler(mInterfaceName,
                    WifiP2pMonitor.P2P_GROUP_STARTED_EVENT, getHandler());
            mWifiMonitor.registerHandler(mInterfaceName,
                    WifiP2pMonitor.P2P_INVITATION_RECEIVED_EVENT, getHandler());
            mWifiMonitor.registerHandler(mInterfaceName,
                    WifiP2pMonitor.P2P_INVITATION_RESULT_EVENT, getHandler());
            mWifiMonitor.registerHandler(mInterfaceName,
                    WifiP2pMonitor.P2P_PROV_DISC_ENTER_PIN_EVENT, getHandler());
            mWifiMonitor.registerHandler(mInterfaceName,
                    WifiP2pMonitor.P2P_PROV_DISC_FAILURE_EVENT, getHandler());
            mWifiMonitor.registerHandler(mInterfaceName,
                    WifiP2pMonitor.P2P_PROV_DISC_PBC_REQ_EVENT, getHandler());
            mWifiMonitor.registerHandler(mInterfaceName,
                    WifiP2pMonitor.P2P_PROV_DISC_PBC_RSP_EVENT, getHandler());
            mWifiMonitor.registerHandler(mInterfaceName,
                    WifiP2pMonitor.P2P_PROV_DISC_SHOW_PIN_EVENT, getHandler());
            mWifiMonitor.registerHandler(mInterfaceName,
                    WifiP2pMonitor.P2P_SERV_DISC_RESP_EVENT, getHandler());
            mWifiMonitor.registerHandler(mInterfaceName,
                    WifiP2pMonitor.SUP_CONNECTION_EVENT, getHandler());
            mWifiMonitor.registerHandler(mInterfaceName,
                    WifiP2pMonitor.SUP_DISCONNECTION_EVENT, getHandler());
            mWifiMonitor.registerHandler(mInterfaceName,
                    WifiP2pMonitor.P2P_FREQUENCY_CHANGED_EVENT, getHandler());

            mWifiMonitor.startMonitoring(mInterfaceName);
        }

        private WorkSource createRequestorWs(int uid, String packageName) {
            WorkSource requestorWs = new WorkSource(uid, packageName);
            logd("Requestor WorkSource: " + requestorWs);
            return requestorWs;
        }

        private WorkSource createMergedRequestorWs() {
            WorkSource requestorWs = new WorkSource();
            for (WorkSource ws: mActiveClients.values()) {
                requestorWs.add(ws);
            }
            logd("Requestor WorkSource: " + requestorWs);
            return requestorWs;
        }

        private boolean needsActiveP2p(int cmd) {
            if (cmd < Protocol.BASE_WIFI_P2P_MANAGER) return false;
            if (cmd >= Protocol.BASE_WIFI_P2P_SERVICE) return false;
            switch (cmd) {
                case WifiP2pManager.UPDATE_CHANNEL_INFO:
                case WifiP2pManager.SET_WFD_INFO:
                // If P2P is not active, these commands do not take effect actually.
                case WifiP2pManager.STOP_DISCOVERY:
                case WifiP2pManager.STOP_LISTEN:
                case WifiP2pManager.CANCEL_CONNECT:
                case WifiP2pManager.REMOVE_GROUP:
                case WifiP2pManager.REMOVE_LOCAL_SERVICE:
                case WifiP2pManager.CLEAR_LOCAL_SERVICES:
                case WifiP2pManager.REMOVE_SERVICE_REQUEST:
                case WifiP2pManager.CLEAR_SERVICE_REQUESTS:
                case WifiP2pManager.REMOVE_CLIENT:
                // These commands return wifi service p2p information which
                // does not need active P2P.
                case WifiP2pManager.REQUEST_P2P_STATE:
                case WifiP2pManager.REQUEST_DISCOVERY_STATE:
                case WifiP2pManager.GET_LISTEN_STATE:
                case WifiP2pManager.REQUEST_NETWORK_INFO:
                case WifiP2pManager.REQUEST_CONNECTION_INFO:
                case WifiP2pManager.REQUEST_GROUP_INFO:
                case WifiP2pManager.REQUEST_PEERS:
                // These commands configure the framework behavior.
                case WifiP2pManager.ADD_EXTERNAL_APPROVER:
                case WifiP2pManager.REMOVE_EXTERNAL_APPROVER:
                case WifiP2pManager.SET_CONNECTION_REQUEST_RESULT:
                // These commands could be cached and executed on activating P2P.
                case WifiP2pManager.SET_DEVICE_NAME:
                case WifiP2pManager.SET_VENDOR_ELEMENTS:
                    return false;
                case WifiP2pManager.REQUEST_DEVICE_INFO:
                    if (!mWifiGlobals.isP2pMacRandomizationSupported()
                            && !TextUtils.isEmpty(mThisDevice.deviceAddress)) {
                        return false;
                    }
                    break;
            }
            return true;
        }

        @Override
        protected void onPreHandleMessage(Message msg) {
            if (needsActiveP2p(msg.what)) {
                updateWorkSourceByUid(msg.sendingUid, true);
            }
        }

        class DefaultState extends RunnerState {
            /**
             * The Runner state Constructor
             *
             * @param threshold the running time threshold in milliseconds
             */
            DefaultState(int threshold, @NonNull LocalLog localLog) {
                super(threshold, localLog);
            }
            @Override
            public boolean processMessageImpl(Message message) {
                logSmMessage(getName(), message);
                switch (message.what) {
                    case AsyncChannel.CMD_CHANNEL_HALF_CONNECTED:
                        if (message.arg1 == AsyncChannel.STATUS_SUCCESSFUL) {
                            if (mVerboseLoggingEnabled) {
                                logd("Full connection with ClientModeImpl established");
                            }
                            mWifiChannel = (AsyncChannel) message.obj;
                        } else {
                            loge("Full connection failure, error = " + message.arg1);
                            mWifiChannel = null;
                            smTransition(this, mP2pDisabledState);
                        }
                        break;
                    case AsyncChannel.CMD_CHANNEL_DISCONNECTED:
                        if (message.arg1 == AsyncChannel.STATUS_SEND_UNSUCCESSFUL) {
                            loge("Send failed, client connection lost");
                        } else {
                            loge("Client connection lost with reason: " + message.arg1);
                        }
                        mWifiChannel = null;
                        smTransition(this, mP2pDisabledState);
                        break;
                    case AsyncChannel.CMD_CHANNEL_FULL_CONNECTION:
                        AsyncChannel ac = new AsyncChannel();
                        ac.connect(mContext, getHandler(), message.replyTo);
                        break;
                    case BLOCK_DISCOVERY:
                        mDiscoveryBlocked = (message.arg1 == ENABLED);
                        // always reset this - we went to a state that doesn't support discovery so
                        // it would have stopped regardless
                        mDiscoveryPostponed = false;
                        if (mDiscoveryBlocked && mWifiChannel != null) {
                            mWifiChannel.replyToMessage(message, message.arg2);
                        }
                        break;
                    case WifiP2pManager.DISCOVER_PEERS:
                        replyToMessage(message, WifiP2pManager.DISCOVER_PEERS_FAILED,
                                WifiP2pManager.BUSY);
                        break;
                    case WifiP2pManager.STOP_DISCOVERY:
                        if (isWifiP2pAvailable()) {
                            replyToMessage(message, WifiP2pManager.STOP_DISCOVERY_SUCCEEDED);
                        } else {
                            replyToMessage(message, WifiP2pManager.STOP_DISCOVERY_FAILED,
                                    WifiP2pManager.BUSY);
                        }
                        break;
                    case WifiP2pManager.DISCOVER_SERVICES:
                        replyToMessage(message, WifiP2pManager.DISCOVER_SERVICES_FAILED,
                                WifiP2pManager.BUSY);
                        break;
                    case WifiP2pManager.CONNECT:
                        replyToMessage(message, WifiP2pManager.CONNECT_FAILED,
                                WifiP2pManager.BUSY);
                        break;
                    case WifiP2pManager.CANCEL_CONNECT:
                        replyToMessage(message, WifiP2pManager.CANCEL_CONNECT_FAILED,
                                 WifiP2pManager.BUSY);
                        break;
                    case WifiP2pManager.CREATE_GROUP:
                        replyToMessage(message, WifiP2pManager.CREATE_GROUP_FAILED,
                                WifiP2pManager.BUSY);
                        break;
                    case WifiP2pManager.REMOVE_GROUP:
                        replyToMessage(message, WifiP2pManager.REMOVE_GROUP_FAILED,
                                WifiP2pManager.BUSY);
                        break;
                    case WifiP2pManager.STOP_LISTEN:
                        if (isWifiP2pAvailable()) {
                            replyToMessage(message, WifiP2pManager.STOP_LISTEN_SUCCEEDED);
                        }
                        break;
                    case WifiP2pManager.ADD_LOCAL_SERVICE:
                        replyToMessage(message, WifiP2pManager.ADD_LOCAL_SERVICE_FAILED,
                                WifiP2pManager.BUSY);
                        break;
                    case WifiP2pManager.REMOVE_LOCAL_SERVICE:
                        replyToMessage(message, WifiP2pManager.REMOVE_LOCAL_SERVICE_FAILED,
                                WifiP2pManager.BUSY);
                        break;
                    case WifiP2pManager.CLEAR_LOCAL_SERVICES:
                        replyToMessage(message, WifiP2pManager.CLEAR_LOCAL_SERVICES_FAILED,
                                WifiP2pManager.BUSY);
                        break;
                    case WifiP2pManager.ADD_SERVICE_REQUEST:
                        replyToMessage(message, WifiP2pManager.ADD_SERVICE_REQUEST_FAILED,
                                WifiP2pManager.BUSY);
                        break;
                    case WifiP2pManager.REMOVE_SERVICE_REQUEST:
                        replyToMessage(message,
                                WifiP2pManager.REMOVE_SERVICE_REQUEST_FAILED,
                                WifiP2pManager.BUSY);
                        break;
                    case WifiP2pManager.CLEAR_SERVICE_REQUESTS:
                        replyToMessage(message,
                                WifiP2pManager.CLEAR_SERVICE_REQUESTS_FAILED,
                                WifiP2pManager.BUSY);
                        break;
                    case WifiP2pManager.SET_DEVICE_NAME:
                    {
                        if (!isWifiP2pAvailable()) {
                            replyToMessage(message, WifiP2pManager.SET_DEVICE_NAME_FAILED,
                                    WifiP2pManager.BUSY);
                            break;
                        }
                        if (!checkNetworkSettingsOrNetworkStackOrOverrideWifiConfigPermission(
                                message.sendingUid)) {
                            loge("Permission violation - none of NETWORK_SETTING, NETWORK_STACK,"
                                    + " or OVERRIDE_WIFI_CONFIG permission, uid = "
                                    + message.sendingUid);
                            replyToMessage(message, WifiP2pManager.SET_DEVICE_NAME_FAILED,
                                    WifiP2pManager.ERROR);
                            break;
                        }
                        WifiP2pDevice d = (WifiP2pDevice) message.obj;
                        if (d != null && setAndPersistDeviceName(d.deviceName)) {
                            if (mVerboseLoggingEnabled) logd("set device name " + d.deviceName);
                            replyToMessage(message, WifiP2pManager.SET_DEVICE_NAME_SUCCEEDED);
                        } else {
                            replyToMessage(message, WifiP2pManager.SET_DEVICE_NAME_FAILED,
                                    WifiP2pManager.ERROR);
                        }
                        break;
                    }
                    case WifiP2pManager.DELETE_PERSISTENT_GROUP:
                        replyToMessage(message, WifiP2pManager.DELETE_PERSISTENT_GROUP_FAILED,
                                WifiP2pManager.BUSY);
                        break;
                    case WifiP2pManager.SET_WFD_INFO:
                        WifiP2pWfdInfo d = (WifiP2pWfdInfo) message.obj;
                        if (!getWfdPermission(message.sendingUid)) {
                            loge("No WFD permission, uid = " + message.sendingUid);
                            replyToMessage(message, WifiP2pManager.SET_WFD_INFO_FAILED,
                                    WifiP2pManager.ERROR);
                        } else if (d != null) {
                            mThisDevice.wfdInfo = d;
                            replyToMessage(message, WifiP2pManager.SET_WFD_INFO_SUCCEEDED);
                        } else {
                            replyToMessage(message, WifiP2pManager.SET_WFD_INFO_FAILED,
                                    WifiP2pManager.ERROR);
                        }
                        break;
                    case WifiP2pManager.REQUEST_PEERS:
                        replyToMessage(message, WifiP2pManager.RESPONSE_PEERS,
                                getPeers(getCallingPkgName(message.sendingUid, message.replyTo),
                                        getCallingFeatureId(message.sendingUid, message.replyTo),
                                        message.sendingUid, message.getData().getBundle(
                                                WifiP2pManager.EXTRA_PARAM_KEY_BUNDLE),
                                        message.obj));
                        break;
                    case WifiP2pManager.REQUEST_CONNECTION_INFO:
                        replyToMessage(message, WifiP2pManager.RESPONSE_CONNECTION_INFO,
                                new WifiP2pInfo(mWifiP2pInfo));
                        break;
                    case WifiP2pManager.REQUEST_GROUP_INFO: {
                        String packageName = getCallingPkgName(message.sendingUid, message.replyTo);
                        if (packageName == null) {
                            replyToMessage(message, WifiP2pManager.RESPONSE_GROUP_INFO, null);
                            break;
                        }
                        int uid = message.sendingUid;
                        Bundle extras = message.getData()
                                .getBundle(WifiP2pManager.EXTRA_PARAM_KEY_BUNDLE);
                        boolean hasPermission = false;
                        if (isPlatformOrTargetSdkLessThanT(packageName, uid)) {
                            hasPermission = mWifiPermissionsUtil.checkCanAccessWifiDirect(
                                    packageName,
                                    getCallingFeatureId(message.sendingUid, message.replyTo),
                                    uid, false);
                        } else {
                            hasPermission = checkNearbyDevicesPermission(uid, packageName,
                                    extras, "REQUEST_GROUP_INFO", message.obj);
                        }
                        if (!hasPermission) {
                            replyToMessage(message, WifiP2pManager.RESPONSE_GROUP_INFO, null);
                            // remain at this state.
                            break;
                        }
                        replyToMessage(message, WifiP2pManager.RESPONSE_GROUP_INFO,
                                maybeEraseOwnDeviceAddress(mGroup, message.sendingUid));
                        break;
                    }
                    case WifiP2pManager.REQUEST_PERSISTENT_GROUP_INFO: {
                        if (!checkNetworkSettingsOrNetworkStackOrReadWifiCredentialPermission(
                                message.sendingUid)) {
                            loge("Permission violation - none of NETWORK_SETTING, NETWORK_STACK,"
                                    + " or READ_WIFI_CREDENTIAL permission, uid = "
                                    + message.sendingUid);
                            replyToMessage(message, WifiP2pManager.RESPONSE_PERSISTENT_GROUP_INFO,
                                    new WifiP2pGroupList());
                            break;
                        }
                        String packageName = getCallingPkgName(message.sendingUid, message.replyTo);
                        if (packageName == null) {
                            replyToMessage(message, WifiP2pManager.RESPONSE_PERSISTENT_GROUP_INFO,
                                    new WifiP2pGroupList());
                            break;
                        }
                        Bundle extras = message.getData()
                                .getBundle(WifiP2pManager.EXTRA_PARAM_KEY_BUNDLE);
                        if (!isPlatformOrTargetSdkLessThanT(packageName, message.sendingUid)
                                && !checkNearbyDevicesPermission(message.sendingUid, packageName,
                                        extras, "REQUEST_PERSISTENT_GROUP_INFO", message.obj)) {
                            loge("Permission violation - no NEARBY_WIFI_DEVICES permission, uid = "
                                    + message.sendingUid);
                            replyToMessage(message, WifiP2pManager.RESPONSE_PERSISTENT_GROUP_INFO,
                                    new WifiP2pGroupList());
                            break;
                        }
                        replyToMessage(message, WifiP2pManager.RESPONSE_PERSISTENT_GROUP_INFO,
                                new WifiP2pGroupList(
                                        maybeEraseOwnDeviceAddress(mGroups, message.sendingUid),
                                        null));
                        break;
                    }
                    case WifiP2pManager.REQUEST_P2P_STATE:
                        replyToMessage(message, WifiP2pManager.RESPONSE_P2P_STATE,
                                isWifiP2pAvailable()
                                ? WifiP2pManager.WIFI_P2P_STATE_ENABLED
                                : WifiP2pManager.WIFI_P2P_STATE_DISABLED);
                        break;
                    case WifiP2pManager.REQUEST_DISCOVERY_STATE:
                        replyToMessage(message, WifiP2pManager.RESPONSE_DISCOVERY_STATE,
                                mDiscoveryStarted
                                ? WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED
                                : WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED);
                        break;
                    case WifiP2pManager.GET_LISTEN_STATE: {
                        String packageName = getCallingPkgName(message.sendingUid, message.replyTo);
                        if (packageName == null) {
                            replyToMessage(message, WifiP2pManager.GET_LISTEN_STATE_FAILED);
                            break;
                        }
                        int uid = message.sendingUid;
                        Bundle extras = message.getData()
                                .getBundle(WifiP2pManager.EXTRA_PARAM_KEY_BUNDLE);
                        boolean hasPermission;
                        if (isPlatformOrTargetSdkLessThanT(packageName, uid)) {
                            hasPermission = mWifiPermissionsUtil.checkCanAccessWifiDirect(
                                    packageName,
                                    getCallingFeatureId(message.sendingUid, message.replyTo),
                                    uid, true);
                        } else {
                            hasPermission = checkNearbyDevicesPermission(uid, packageName,
                                    extras, "GET_LISTEN_STATE", message.obj);
                        }
                        if (!hasPermission) {
                            replyToMessage(message, WifiP2pManager.GET_LISTEN_STATE_FAILED);
                            break;
                        }
                        replyToMessage(message, WifiP2pManager.RESPONSE_GET_LISTEN_STATE,
                                mListenStarted
                                        ? WifiP2pManager.WIFI_P2P_LISTEN_STARTED
                                        : WifiP2pManager.WIFI_P2P_LISTEN_STOPPED);
                        break;
                    }
                    case WifiP2pManager.REQUEST_NETWORK_INFO:
                        replyToMessage(message, WifiP2pManager.RESPONSE_NETWORK_INFO,
                                makeNetworkInfo());
                        break;
                    case WifiP2pManager.START_WPS:
                        replyToMessage(message, WifiP2pManager.START_WPS_FAILED,
                                WifiP2pManager.BUSY);
                        break;
                    case WifiP2pManager.GET_HANDOVER_REQUEST:
                    case WifiP2pManager.GET_HANDOVER_SELECT:
                        replyToMessage(message, WifiP2pManager.RESPONSE_GET_HANDOVER_MESSAGE, null);
                        break;
                    case WifiP2pManager.INITIATOR_REPORT_NFC_HANDOVER:
                    case WifiP2pManager.RESPONDER_REPORT_NFC_HANDOVER:
                        replyToMessage(message, WifiP2pManager.REPORT_NFC_HANDOVER_FAILED,
                                WifiP2pManager.BUSY);
                        break;
                    case WifiP2pManager.SET_CONNECTION_REQUEST_RESULT:
                        replyToMessage(message, WifiP2pManager.SET_CONNECTION_REQUEST_RESULT_FAILED,
                                WifiP2pManager.BUSY);
                        break;
                    case WifiP2pMonitor.P2P_INVITATION_RESULT_EVENT:
                    case WifiP2pMonitor.SUP_CONNECTION_EVENT:
                    case WifiP2pMonitor.SUP_DISCONNECTION_EVENT:
                    case WifiP2pMonitor.P2P_GROUP_REMOVED_EVENT:
                    case WifiP2pMonitor.P2P_DEVICE_FOUND_EVENT:
                    case WifiP2pMonitor.P2P_DEVICE_LOST_EVENT:
                    case WifiP2pMonitor.P2P_FIND_STOPPED_EVENT:
                    case WifiP2pMonitor.P2P_SERV_DISC_RESP_EVENT:
                    case PEER_CONNECTION_USER_ACCEPT:
                    case PEER_CONNECTION_USER_REJECT:
                    case DISCONNECT_WIFI_RESPONSE:
                    case DROP_WIFI_USER_ACCEPT:
                    case DROP_WIFI_USER_REJECT:
                    case GROUP_CREATING_TIMED_OUT:
                    case DISABLE_P2P_TIMED_OUT:
                    case IPC_PRE_DHCP_ACTION:
                    case IPC_POST_DHCP_ACTION:
                    case IPC_DHCP_RESULTS:
                    case IPC_PROVISIONING_SUCCESS:
                    case IPC_PROVISIONING_FAILURE:
                    case TETHER_INTERFACE_STATE_CHANGED:
                    case TETHER_INTERFACE_CLIENTS_CHANGED:
                    case UPDATE_P2P_DISALLOWED_CHANNELS:
                    case WifiP2pMonitor.P2P_PROV_DISC_FAILURE_EVENT:
                    case SET_MIRACAST_MODE:
                        break;
                    case WifiP2pManager.START_LISTEN:
                        replyToMessage(message, WifiP2pManager.START_LISTEN_FAILED,
                                WifiP2pManager.BUSY);
                        break;
                    case WifiP2pManager.SET_CHANNEL:
                        replyToMessage(message, WifiP2pManager.SET_CHANNEL_FAILED,
                                WifiP2pManager.BUSY);
                        break;
                    case ENABLE_P2P:
                        // Enable is lazy and has no response
                        break;
                    case DISABLE_P2P:
                        // If we end up handling in default, p2p is not enabled
                        break;
                    case WifiP2pMonitor.P2P_GROUP_STARTED_EVENT:
                        // unexpected group created, remove
                        if (message.obj == null) {
                            Log.e(TAG, "Illegal arguments");
                            break;
                        }
                        mGroup = (WifiP2pGroup) message.obj;
                        loge("Unexpected group creation, remove " + mGroup);
                        mWifiNative.p2pGroupRemove(mGroup.getInterface());
                        break;
                    case WifiP2pMonitor.P2P_GROUP_FORMATION_FAILURE_EVENT:
                        // A group formation failure is always followed by
                        // a group removed event. Flushing things at group formation
                        // failure causes supplicant issues. Ignore right now.
                        break;
                    case WifiP2pMonitor.P2P_GO_NEGOTIATION_FAILURE_EVENT:
                        if (null != mSavedRejectedPeerConfig) {
                            sendP2pRequestChangedBroadcast(false);
                            mSavedRejectedPeerConfig = null;
                        }
                        break;
                    case WifiP2pManager.FACTORY_RESET:
                        if (factoryReset(message.sendingUid)) {
                            replyToMessage(message, WifiP2pManager.FACTORY_RESET_SUCCEEDED);
                        } else {
                            replyToMessage(message, WifiP2pManager.FACTORY_RESET_FAILED,
                                    WifiP2pManager.ERROR);
                        }
                        break;
                    case WifiP2pManager.SET_ONGOING_PEER_CONFIG:
                        if (mWifiPermissionsUtil.checkNetworkStackPermission(message.sendingUid)) {
                            WifiP2pConfig peerConfig = (WifiP2pConfig) message.obj;
                            if (isConfigInvalid(peerConfig)) {
                                loge("Dropping set mSavedPeerConfig requeset" + peerConfig);
                                replyToMessage(message,
                                        WifiP2pManager.SET_ONGOING_PEER_CONFIG_FAILED);
                            } else {
                                logd("setSavedPeerConfig to " + peerConfig);
                                mSavedPeerConfig = peerConfig;
                                replyToMessage(message,
                                        WifiP2pManager.SET_ONGOING_PEER_CONFIG_SUCCEEDED);
                            }
                        } else {
                            loge("Permission violation - no NETWORK_STACK permission,"
                                    + " uid = " + message.sendingUid);
                            replyToMessage(message,
                                    WifiP2pManager.SET_ONGOING_PEER_CONFIG_FAILED);
                        }
                        break;
                    case WifiP2pManager.REQUEST_ONGOING_PEER_CONFIG:
                        if (mWifiPermissionsUtil.checkNetworkStackPermission(message.sendingUid)) {
                            replyToMessage(message,
                                    WifiP2pManager.RESPONSE_ONGOING_PEER_CONFIG, mSavedPeerConfig);
                        } else {
                            loge("Permission violation - no NETWORK_STACK permission,"
                                    + " uid = " + message.sendingUid);
                            replyToMessage(message,
                                    WifiP2pManager.RESPONSE_ONGOING_PEER_CONFIG, null);
                        }
                        break;
                    case WifiP2pManager.UPDATE_CHANNEL_INFO: {
                        Bundle bundle = message.getData()
                                .getBundle(WifiP2pManager.EXTRA_PARAM_KEY_BUNDLE);
                        if (!(bundle instanceof Bundle)) {
                            break;
                        }
                        String pkgName = bundle.getString(WifiP2pManager.CALLING_PACKAGE);
                        String featureId = bundle.getString(WifiP2pManager.CALLING_FEATURE_ID);
                        IBinder binder = bundle.getBinder(WifiP2pManager.CALLING_BINDER);
                        try {
                            mWifiPermissionsUtil.checkPackage(message.sendingUid, pkgName);
                        } catch (SecurityException se) {
                            loge("Unable to update calling package, " + se);
                            break;
                        }
                        if (binder != null && message.replyTo != null) {
                            mClientChannelList.put(binder, message.replyTo);
                            ClientInfo clientInfo = getClientInfo(message.replyTo, true);
                            clientInfo.mPackageName = pkgName;
                            clientInfo.mFeatureId = featureId;
                            if (SdkLevel.isAtLeastS()) {
                                AttributionSource source = (AttributionSource) message.obj;
                                if (null != source) {
                                    mClientAttributionSource.put(binder, source);
                                }
                            }
                        }
                        break;
                    }
                    case WifiP2pManager.REQUEST_DEVICE_INFO:
                    {
                        String packageName = getCallingPkgName(message.sendingUid, message.replyTo);
                        if (packageName == null) {
                            replyToMessage(message, WifiP2pManager.RESPONSE_DEVICE_INFO, null);
                            break;
                        }
                        int uid = message.sendingUid;
                        Bundle extras = message.getData()
                                .getBundle(WifiP2pManager.EXTRA_PARAM_KEY_BUNDLE);
                        boolean hasPermission = false;
                        if (isPlatformOrTargetSdkLessThanT(packageName, uid)) {
                            hasPermission = mWifiPermissionsUtil.checkCanAccessWifiDirect(
                                    packageName,
                                    getCallingFeatureId(message.sendingUid, message.replyTo),
                                    uid, false);
                        } else {
                            hasPermission = checkNearbyDevicesPermission(uid, packageName,
                                    extras, "REQUEST_DEVICE_INFO", message.obj);
                        }
                        if (!hasPermission) {
                            replyToMessage(message, WifiP2pManager.RESPONSE_DEVICE_INFO, null);
                            break;
                        }
                        replyToMessage(message, WifiP2pManager.RESPONSE_DEVICE_INFO,
                                maybeEraseOwnDeviceAddress(mThisDevice, message.sendingUid));
                        break;
                    }
                    case WifiP2pManager.REMOVE_CLIENT:
                        if (!isFeatureSupported(WifiP2pManager.FEATURE_GROUP_CLIENT_REMOVAL)) {
                            replyToMessage(message, WifiP2pManager.REMOVE_CLIENT_FAILED,
                                    WifiP2pManager.ERROR);
                            break;
                        }
                        replyToMessage(message, WifiP2pManager.REMOVE_CLIENT_SUCCEEDED);
                        break;
                    case WifiP2pManager.ADD_EXTERNAL_APPROVER: {
                        Bundle extras = message.getData().getBundle(
                                WifiP2pManager.EXTRA_PARAM_KEY_BUNDLE);
                        MacAddress devAddr = extras.getParcelable(
                                WifiP2pManager.EXTRA_PARAM_KEY_PEER_ADDRESS);
                        IBinder binder = extras.getBinder(WifiP2pManager.CALLING_BINDER);
                        if (!checkExternalApproverCaller(message, binder, devAddr,
                                "ADD_EXTERNAL_APPROVER")) {
                            replyToMessage(message, WifiP2pManager.EXTERNAL_APPROVER_DETACH,
                                    ExternalApproverRequestListener.APPROVER_DETACH_REASON_FAILURE,
                                    devAddr);
                            break;
                        }
                        ApproverEntry entry = mExternalApproverManager.put(
                                binder, devAddr, message);
                        // A non-null entry indicates that the device address was added before.
                        // So inform the approver about detach.
                        if (null != entry) {
                            logd("Replace an existing approver " + entry);
                            replyToMessage(entry.getMessage(),
                                    WifiP2pManager.EXTERNAL_APPROVER_DETACH,
                                    ExternalApproverRequestListener.APPROVER_DETACH_REASON_REPLACE,
                                    devAddr);
                            break;
                        }
                        logd("Add the approver " + mExternalApproverManager.get(devAddr));
                        replyToMessage(message, WifiP2pManager.EXTERNAL_APPROVER_ATTACH, devAddr);
                        break;
                    }
                    case WifiP2pManager.REMOVE_EXTERNAL_APPROVER: {
                        Bundle extras = message.getData().getBundle(
                                WifiP2pManager.EXTRA_PARAM_KEY_BUNDLE);
                        MacAddress devAddr = extras.getParcelable(
                                WifiP2pManager.EXTRA_PARAM_KEY_PEER_ADDRESS);
                        IBinder binder = extras.getBinder(WifiP2pManager.CALLING_BINDER);
                        if (!checkExternalApproverCaller(message, binder, devAddr,
                                "REMOVE_EXTERNAL_APPROVER")) {
                            replyToMessage(message,
                                    WifiP2pManager.REMOVE_EXTERNAL_APPROVER_FAILED);
                            break;
                        }
                        ApproverEntry entry = mExternalApproverManager.remove(
                                binder, devAddr);
                        if (null != entry) {
                            logd("Remove the approver " + entry);
                            replyToMessage(entry.getMessage(),
                                    WifiP2pManager.EXTERNAL_APPROVER_DETACH,
                                    ExternalApproverRequestListener.APPROVER_DETACH_REASON_REMOVE,
                                    devAddr);
                            break;
                        }
                        replyToMessage(message, WifiP2pManager.REMOVE_EXTERNAL_APPROVER_SUCCEEDED);
                        break;
                    }
                    case WifiP2pManager.SET_VENDOR_ELEMENTS: {
                        if (!isFeatureSupported(WifiP2pManager.FEATURE_SET_VENDOR_ELEMENTS)) {
                            replyToMessage(message, WifiP2pManager.SET_VENDOR_ELEMENTS_FAILED,
                                    WifiP2pManager.ERROR);
                            break;
                        }
                        if (!mWifiPermissionsUtil.checkConfigOverridePermission(
                                message.sendingUid)) {
                            loge(" Uid " + message.sendingUid
                                    + " has no config override permission");
                            replyToMessage(message, WifiP2pManager.SET_VENDOR_ELEMENTS_FAILED);
                            break;
                        }
                        if (!SdkLevel.isAtLeastS()
                                || !checkNearbyDevicesPermission(message, "SET_VENDOR_ELEMENTS")) {
                            replyToMessage(message, WifiP2pManager.SET_VENDOR_ELEMENTS_FAILED);
                            break;
                        }
                        Bundle extras = message.getData()
                                .getBundle(WifiP2pManager.EXTRA_PARAM_KEY_BUNDLE);
                        ArrayList<ScanResult.InformationElement> ies =
                                extras.getParcelableArrayList(
                                        WifiP2pManager.EXTRA_PARAM_KEY_INFORMATION_ELEMENT_LIST);
                        String packageName = getCallingPkgName(message.sendingUid, message.replyTo);
                        if (!updateVendorElements(packageName, ies)) {
                            replyToMessage(message, WifiP2pManager.SET_VENDOR_ELEMENTS_FAILED);
                            break;
                        }
                        replyToMessage(message, WifiP2pManager.SET_VENDOR_ELEMENTS_SUCCEEDED);
                        break;
                    }
                    default:
                        loge("Unhandled message " + message);
                        return NOT_HANDLED;
                }
                return HANDLED;
            }

            @Override
            public void enterImpl() {

            }

            @Override
            public void exitImpl() {

            }

            @Override
            public String getMessageLogRec(int what) {
                return P2pStateMachine.class.getSimpleName() + "."
                        + this.getClass().getSimpleName()
                        + "." + getWhatToString(what);
            }
        }

        class P2pNotSupportedState extends RunnerState {
            /**
             * The Runner state Constructor
             *
             * @param threshold the running time threshold in milliseconds
             */
            P2pNotSupportedState(int threshold, @NonNull LocalLog localLog) {
                super(threshold, localLog);
            }

            @Override
            public boolean processMessageImpl(Message message) {
                logSmMessage(getName(), message);
                switch (message.what) {
                    case WifiP2pManager.DISCOVER_PEERS:
                        replyToMessage(message, WifiP2pManager.DISCOVER_PEERS_FAILED,
                                WifiP2pManager.P2P_UNSUPPORTED);
                        break;
                    case WifiP2pManager.STOP_DISCOVERY:
                        replyToMessage(message, WifiP2pManager.STOP_DISCOVERY_FAILED,
                                WifiP2pManager.P2P_UNSUPPORTED);
                        break;
                    case WifiP2pManager.DISCOVER_SERVICES:
                        replyToMessage(message, WifiP2pManager.DISCOVER_SERVICES_FAILED,
                                WifiP2pManager.P2P_UNSUPPORTED);
                        break;
                    case WifiP2pManager.CONNECT:
                        replyToMessage(message, WifiP2pManager.CONNECT_FAILED,
                                WifiP2pManager.P2P_UNSUPPORTED);
                        break;
                    case WifiP2pManager.CANCEL_CONNECT:
                        replyToMessage(message, WifiP2pManager.CANCEL_CONNECT_FAILED,
                                WifiP2pManager.P2P_UNSUPPORTED);
                        break;
                    case WifiP2pManager.CREATE_GROUP:
                        replyToMessage(message, WifiP2pManager.CREATE_GROUP_FAILED,
                                WifiP2pManager.P2P_UNSUPPORTED);
                        break;
                    case WifiP2pManager.REMOVE_GROUP:
                        replyToMessage(message, WifiP2pManager.REMOVE_GROUP_FAILED,
                                WifiP2pManager.P2P_UNSUPPORTED);
                        break;
                    case WifiP2pManager.ADD_LOCAL_SERVICE:
                        replyToMessage(message, WifiP2pManager.ADD_LOCAL_SERVICE_FAILED,
                                WifiP2pManager.P2P_UNSUPPORTED);
                        break;
                    case WifiP2pManager.REMOVE_LOCAL_SERVICE:
                        replyToMessage(message, WifiP2pManager.REMOVE_LOCAL_SERVICE_FAILED,
                                WifiP2pManager.P2P_UNSUPPORTED);
                        break;
                    case WifiP2pManager.CLEAR_LOCAL_SERVICES:
                        replyToMessage(message, WifiP2pManager.CLEAR_LOCAL_SERVICES_FAILED,
                                WifiP2pManager.P2P_UNSUPPORTED);
                        break;
                    case WifiP2pManager.ADD_SERVICE_REQUEST:
                        replyToMessage(message, WifiP2pManager.ADD_SERVICE_REQUEST_FAILED,
                                WifiP2pManager.P2P_UNSUPPORTED);
                        break;
                    case WifiP2pManager.REMOVE_SERVICE_REQUEST:
                        replyToMessage(message,
                                WifiP2pManager.REMOVE_SERVICE_REQUEST_FAILED,
                                WifiP2pManager.P2P_UNSUPPORTED);
                        break;
                    case WifiP2pManager.CLEAR_SERVICE_REQUESTS:
                        replyToMessage(message,
                                WifiP2pManager.CLEAR_SERVICE_REQUESTS_FAILED,
                                WifiP2pManager.P2P_UNSUPPORTED);
                        break;
                    case WifiP2pManager.SET_DEVICE_NAME:
                        replyToMessage(message, WifiP2pManager.SET_DEVICE_NAME_FAILED,
                                WifiP2pManager.P2P_UNSUPPORTED);
                        break;
                    case WifiP2pManager.DELETE_PERSISTENT_GROUP:
                        replyToMessage(message, WifiP2pManager.DELETE_PERSISTENT_GROUP_FAILED,
                                WifiP2pManager.P2P_UNSUPPORTED);
                        break;
                    case WifiP2pManager.SET_WFD_INFO:
                        if (!getWfdPermission(message.sendingUid)) {
                            loge("No WFD permission, uid = " + message.sendingUid);
                            replyToMessage(message, WifiP2pManager.SET_WFD_INFO_FAILED,
                                    WifiP2pManager.ERROR);
                        } else {
                            replyToMessage(message, WifiP2pManager.SET_WFD_INFO_FAILED,
                                    WifiP2pManager.P2P_UNSUPPORTED);
                        }
                        break;
                    case WifiP2pManager.START_WPS:
                        replyToMessage(message, WifiP2pManager.START_WPS_FAILED,
                                WifiP2pManager.P2P_UNSUPPORTED);
                        break;
                    case WifiP2pManager.START_LISTEN:
                        replyToMessage(message, WifiP2pManager.START_LISTEN_FAILED,
                                WifiP2pManager.P2P_UNSUPPORTED);
                        break;
                    case WifiP2pManager.STOP_LISTEN:
                        replyToMessage(message, WifiP2pManager.STOP_LISTEN_FAILED,
                                WifiP2pManager.P2P_UNSUPPORTED);
                        break;
                    case WifiP2pManager.FACTORY_RESET:
                        replyToMessage(message, WifiP2pManager.FACTORY_RESET_FAILED,
                                WifiP2pManager.P2P_UNSUPPORTED);
                        break;
                    case WifiP2pManager.REMOVE_CLIENT:
                        replyToMessage(message, WifiP2pManager.REMOVE_CLIENT_FAILED,
                                WifiP2pManager.P2P_UNSUPPORTED);
                        break;
                    case WifiP2pManager.SET_CONNECTION_REQUEST_RESULT:
                        replyToMessage(message, WifiP2pManager.SET_CONNECTION_REQUEST_RESULT_FAILED,
                                WifiP2pManager.P2P_UNSUPPORTED);
                        break;
                    case WifiP2pManager.SET_VENDOR_ELEMENTS:
                        replyToMessage(message, WifiP2pManager.SET_VENDOR_ELEMENTS_FAILED,
                                WifiP2pManager.P2P_UNSUPPORTED);
                        break;

                    default:
                        return NOT_HANDLED;
                }
                return HANDLED;
            }

            @Override
            public void enterImpl() {

            }

            @Override
            public void exitImpl() {

            }

            @Override
            public String getMessageLogRec(int what) {
                return P2pStateMachine.class.getSimpleName() + "."
                        + this.getClass().getSimpleName()
                        + "." + getWhatToString(what);
            }
        }

        class P2pDisablingState extends RunnerState {
            /**
             * The Runner state Constructor
             *
             * @param threshold the running time threshold in milliseconds
             */
            P2pDisablingState(int threshold, @NonNull LocalLog localLog) {
                super(threshold, localLog);
            }

            @Override
            public void enterImpl() {
                logSmStateName(this.getName(),
                        getCurrentState() != null ? getCurrentState().getName() : "");
                sendMessageDelayed(obtainMessage(DISABLE_P2P_TIMED_OUT,
                        ++sDisableP2pTimeoutIndex, 0), DISABLE_P2P_WAIT_TIME_MS);
            }

            @Override
            public void exitImpl() {

            }

            @Override
            public boolean processMessageImpl(Message message) {
                logSmMessage(getName(), message);
                switch (message.what) {
                    case WifiP2pMonitor.SUP_DISCONNECTION_EVENT:
                        if (mVerboseLoggingEnabled) logd("p2p socket connection lost");
                        smTransition(this, mP2pDisabledState);
                        break;
                    case ENABLE_P2P:
                    case DISABLE_P2P:
                    case REMOVE_CLIENT_INFO:
                        deferMessage(message);
                        break;
                    case DISABLE_P2P_TIMED_OUT:
                        if (sDisableP2pTimeoutIndex == message.arg1) {
                            loge("P2p disable timed out");
                            smTransition(this, mP2pDisabledState);
                        }
                        break;
                    default:
                        return NOT_HANDLED;
                }
                return HANDLED;
            }

            @Override
            public String getMessageLogRec(int what) {
                return P2pStateMachine.class.getSimpleName() + "."
                        + this.getClass().getSimpleName()
                        + "." + getWhatToString(what);
            }
        }

        class P2pDisabledContainerState extends RunnerState {
            /**
             * The Runner state Constructor
             *
             * @param threshold the running time threshold in milliseconds
             */
            P2pDisabledContainerState(int threshold, @NonNull LocalLog localLog) {
                super(threshold, localLog);
            }

            @Override
            public void enterImpl() {
                logSmStateName(this.getName(),
                        getCurrentState() != null ? getCurrentState().getName() : "");
                mInterfaceName = null; // reset iface name on disable.
                mActiveClients.clear();
                clearP2pInternalDataIfNecessary();
                if (mIsBootComplete) {
                    updateThisDevice(WifiP2pDevice.UNAVAILABLE);
                }
                resetWifiP2pInfo();
                mGroup = null;
            }

            @Override
            public void exitImpl() {

            }

            @Override
            public boolean processMessageImpl(Message msg) {
                return false;
            }

            @Override
            public String getMessageLogRec(int what) {
                return P2pStateMachine.class.getSimpleName() + "."
                        + this.getClass().getSimpleName()
                        + "." + getWhatToString(what);
            }
        }

        class P2pDisabledState extends RunnerState {

            /**
             * The Runner state Constructor
             *
             * @param threshold the running time threshold in milliseconds
             */
            P2pDisabledState(int threshold, @NonNull LocalLog localLog) {
                super(threshold, localLog);
            }

            private void setupInterfaceFeatures() {
                if (mWifiGlobals.isP2pMacRandomizationSupported()) {
                    Log.i(TAG, "Supported feature: P2P MAC randomization");
                    mWifiNative.setMacRandomization(true);
                } else {
                    mWifiNative.setMacRandomization(false);
                }
            }

            private void takeBugReportInterfaceFailureIfNeeded(String bugTitle, String bugDetail) {
                if (mWifiInjector.getDeviceConfigFacade().isInterfaceFailureBugreportEnabled()) {
                    mWifiInjector.getWifiDiagnostics().takeBugReport(bugTitle, bugDetail);
                }
            }

            private boolean setupInterface() {
                if (!isWifiP2pAvailable()) {
                    Log.e(TAG, "Ignore P2P enable since wifi is " + mIsWifiEnabled
                            + ", P2P disallowed by admin=" + mIsP2pDisallowedByAdmin);
                    return false;
                }
                WorkSource requestorWs = createMergedRequestorWs();
                mInterfaceName = mWifiNative.setupInterface((String ifaceName) -> {
                    sendMessage(DISABLE_P2P);
                    checkAndSendP2pStateChangedBroadcast();
                }, getHandler(), requestorWs);
                if (mInterfaceName == null) {
                    String errorMsg = "Failed to setup interface for P2P";
                    Log.e(TAG, errorMsg);
                    if (!mHalDeviceManager.isItPossibleToCreateIface(
                            HalDeviceManager.HDM_CREATE_IFACE_P2P, requestorWs)) {
                        Log.w(TAG, "Interface resource is not available");
                    } else {
                        takeBugReportInterfaceFailureIfNeeded(
                                "Wi-Fi BugReport (P2P interface failure)", errorMsg);
                    }
                    return false;
                }
                setupInterfaceFeatures();
                try {
                    mNetdWrapper.setInterfaceUp(mInterfaceName);
                } catch (IllegalStateException ie) {
                    loge("Unable to change interface settings: " + ie);
                }
                registerForWifiMonitorEvents();
                return true;
            }

            @Override
            public void enterImpl() {

            }

            @Override
            public void exitImpl() {

            }

            @Override
            public boolean processMessageImpl(Message message) {
                logSmMessage(getName(), message);
                boolean wasInWaitingState = WaitingState.wasMessageInWaitingState(message);
                switch (message.what) {
                    case ENABLE_P2P: {
                        if (mActiveClients.isEmpty()) {
                            Log.i(TAG, "No active client, ignore ENABLE_P2P.");
                            // If this is a re-executed command triggered by user reply, then reset
                            // InterfaceConflictManager so it isn't stuck waiting for the
                            // re-executed command.
                            if (wasInWaitingState) {
                                mInterfaceConflictManager.reset();
                            }
                            break;
                        }
                        String packageName = getCallingPkgName(message.sendingUid, message.replyTo);
                        if (TextUtils.isEmpty(packageName)) {
                            Log.i(TAG, "No valid package name, ignore ENABLE_P2P");
                            break;
                        }
                        mWifiInjector.getWifiP2pConnection().setP2pInWaitingState(true);
                        int proceedWithOperation =
                                mInterfaceConflictManager.manageInterfaceConflictForStateMachine(
                                        TAG, message, mP2pStateMachine, mWaitingState,
                                        mP2pDisabledState, HalDeviceManager.HDM_CREATE_IFACE_P2P,
                                        createRequestorWs(message.sendingUid, packageName),
                                        false /* bypassDialog */);
                        mWifiInjector.getWifiP2pConnection().setP2pInWaitingState(false);
                        if (proceedWithOperation == InterfaceConflictManager.ICM_ABORT_COMMAND) {
                            Log.e(TAG, "User refused to set up P2P");
                            updateThisDevice(WifiP2pDevice.UNAVAILABLE);
                        } else if (proceedWithOperation
                                == InterfaceConflictManager.ICM_EXECUTE_COMMAND) {
                            if (setupInterface()) {
                                smTransition(this, mInactiveState);
                            }
                        } // else InterfaceConflictManager.ICM_SKIP_COMMAND_WAIT_FOR_USER: nop
                        break;
                    }
                    case REMOVE_CLIENT_INFO: {
                        if (!(message.obj instanceof IBinder)) {
                            loge("Invalid obj when REMOVE_CLIENT_INFO");
                            break;
                        }
                        IBinder b = (IBinder) message.obj;
                        // client service info is clear before enter disable p2p,
                        // just need to remove it from list
                        Messenger m = mClientChannelList.remove(b);
                        ClientInfo clientInfo = mClientInfoList.remove(m);
                        if (clientInfo != null) {
                            logd("Remove client - " + clientInfo.mPackageName);
                        }
                        detachExternalApproverFromClient(b);
                        break;
                    }
                    default: {
                        // only handle commands from clients and only commands
                        // which require P2P to be active.
                        if (!needsActiveP2p(message.what)) {
                            return NOT_HANDLED;
                        }
                        // If P2P is not ready, it might be disabled due
                        // to another interface, ex. turn on softap from
                        // the quicksettings.
                        // As the new priority scheme, the foreground app
                        // might be able to use P2P, so just try to enable
                        // it.
                        // Check & re-enable P2P if needed.
                        // P2P interface will be created if all of the below are true:
                        // a) Wifi is enabled.
                        // b) There is at least 1 client app which invoked initialize().
                        // c) There is no impact to create another P2P interface
                        //    OR there is impact but user input isn't required
                        //    OR there is impact and user input is required and the user approved
                        //    the interface creation.
                        if (mVerboseLoggingEnabled) {
                            Log.d(TAG, "Wifi enabled=" + mIsWifiEnabled
                                    + ", P2P disallowed by admin=" + mIsP2pDisallowedByAdmin
                                    + ", Number of clients=" + mDeathDataByBinder.size()
                                    + " wasInWaitingState: " + wasInWaitingState);
                        }
                        if (!isWifiP2pAvailable() || mDeathDataByBinder.isEmpty()) {
                            // If this is a re-executed command triggered by user reply, then reset
                            // InterfaceConflictManager so it isn't stuck waiting for the
                            // re-executed command.
                            if (wasInWaitingState) {
                                mInterfaceConflictManager.reset();
                            }
                            return NOT_HANDLED;
                        }

                        String packageName = getCallingPkgName(message.sendingUid, message.replyTo);
                        if (TextUtils.isEmpty(packageName)) {
                            Log.i(TAG, "No valid package name, do not set up the P2P interface");
                            return NOT_HANDLED;
                        }
                        mWifiInjector.getWifiP2pConnection().setP2pInWaitingState(true);
                        int proceedWithOperation =
                                mInterfaceConflictManager.manageInterfaceConflictForStateMachine(
                                        TAG, message, mP2pStateMachine, mWaitingState,
                                        mP2pDisabledState, HalDeviceManager.HDM_CREATE_IFACE_P2P,
                                        createRequestorWs(message.sendingUid, packageName),
                                        false /* bypassDialog */);
                        mWifiInjector.getWifiP2pConnection().setP2pInWaitingState(false);
                        if (proceedWithOperation == InterfaceConflictManager.ICM_ABORT_COMMAND) {
                            Log.e(TAG, "User refused to set up P2P");
                            updateThisDevice(WifiP2pDevice.UNAVAILABLE);
                            return NOT_HANDLED;
                        } else if (proceedWithOperation
                                == InterfaceConflictManager.ICM_EXECUTE_COMMAND) {
                            if (!setupInterface()) return NOT_HANDLED;
                            deferMessage(message);
                            smTransition(this, mInactiveState);
                        }  // else InterfaceConflictManager.ICM_SKIP_COMMAND_WAIT_FOR_USER: nop
                        break;
                    }
                }
                return HANDLED;
            }

            @Override
            public String getMessageLogRec(int what) {
                return P2pStateMachine.class.getSimpleName() + "."
                        + this.getClass().getSimpleName()
                        + "." + getWhatToString(what);
            }
        }

        class P2pEnabledState extends RunnerState {

            /**
             * The Runner state Constructor
             *
             * @param threshold the running time threshold in milliseconds
             */
            P2pEnabledState(int threshold, @NonNull LocalLog localLog) {
                super(threshold, localLog);
            }

            @Override
            public void enterImpl() {
                logSmStateName(this.getName(),
                        getCurrentState() != null ? getCurrentState().getName() : "");

                if (isPendingFactoryReset()) {
                    factoryReset(Process.SYSTEM_UID);
                }

                checkCoexUnsafeChannels();

                sendP2pConnectionChangedBroadcast();
                initializeP2pSettings();
                if (mTetheringManager != null) {
                    mTetheringManager.registerTetheringEventCallback(getHandler()::post,
                            mTetheringEventCallback);
                }
            }

            @Override
            public boolean processMessageImpl(Message message) {
                logSmMessage(getName(), message);
                switch (message.what) {
                    case WifiP2pMonitor.SUP_DISCONNECTION_EVENT:
                        loge("Unexpected loss of p2p socket connection");
                        smTransition(this, mP2pDisabledState);
                        break;
                    case ENABLE_P2P:
                        if (!mWifiNative.replaceRequestorWs(createMergedRequestorWs())) {
                            Log.e(TAG, "Failed to replace requestorWs");
                        }
                        break;
                    case DISABLE_P2P:
                        if (mPeers.clear()) {
                            sendPeersChangedBroadcast();
                        }
                        if (mGroups.clear()) sendP2pPersistentGroupsChangedBroadcast();
                        // clear services list for all clients since interface will teardown soon.
                        clearServicesForAllClients();
                        mWifiMonitor.stopMonitoring(mInterfaceName);
                        mWifiNative.teardownInterface();
                        smTransition(this, mP2pDisablingState);
                        break;
                    case REMOVE_CLIENT_INFO:
                        if (!(message.obj instanceof IBinder)) {
                            break;
                        }
                        IBinder b = (IBinder) message.obj;
                        // clear client info and remove it from list
                        clearClientInfo(mClientChannelList.get(b));
                        mClientChannelList.remove(b);
                        if (!mWifiNative.replaceRequestorWs(createMergedRequestorWs())) {
                            Log.e(TAG, "Failed to replace requestorWs");
                        }
                        detachExternalApproverFromClient(b);
                        break;
                    case WifiP2pManager.SET_WFD_INFO:
                    {
                        WifiP2pWfdInfo d = (WifiP2pWfdInfo) message.obj;
                        if (!getWfdPermission(message.sendingUid)) {
                            loge("No WFD permission, uid = " + message.sendingUid);
                            replyToMessage(message, WifiP2pManager.SET_WFD_INFO_FAILED,
                                    WifiP2pManager.ERROR);
                        } else if (d != null && setWfdInfo(d)) {
                            replyToMessage(message, WifiP2pManager.SET_WFD_INFO_SUCCEEDED);
                        } else {
                            replyToMessage(message, WifiP2pManager.SET_WFD_INFO_FAILED,
                                    WifiP2pManager.ERROR);
                        }
                        break;
                    }
                    case BLOCK_DISCOVERY:
                        boolean blocked = (message.arg1 == ENABLED);
                        if (mDiscoveryBlocked == blocked) break;
                        mDiscoveryBlocked = blocked;
                        if (blocked && mDiscoveryStarted) {
                            mWifiNative.p2pStopFind();
                            mDiscoveryPostponed = true;
                        }
                        if (!blocked && mDiscoveryPostponed) {
                            mDiscoveryPostponed = false;
                            if (p2pFind(DISCOVER_TIMEOUT_S)) {
                                sendP2pDiscoveryChangedBroadcast(true);
                            }
                        }
                        if (blocked && mWifiChannel != null) {
                            mWifiChannel.replyToMessage(message, message.arg2);
                        }
                        break;
                    case WifiP2pManager.DISCOVER_PEERS: {
                        String packageName = getCallingPkgName(message.sendingUid, message.replyTo);
                        if (packageName == null) {
                            replyToMessage(message, WifiP2pManager.DISCOVER_PEERS_FAILED,
                                    WifiP2pManager.ERROR);
                            break;
                        }
                        int scanType = message.arg1;
                        int uid = message.sendingUid;
                        Bundle extras = message.getData()
                                .getBundle(WifiP2pManager.EXTRA_PARAM_KEY_BUNDLE);
                        int freq = extras.getInt(
                                    WifiP2pManager.EXTRA_PARAM_KEY_PEER_DISCOVERY_FREQ,
                                    WifiP2pManager.WIFI_P2P_SCAN_FREQ_UNSPECIFIED);
                        WifiP2pDiscoveryConfig discoveryConfig = (WifiP2pDiscoveryConfig)
                                extras.getParcelable(
                                        WifiP2pManager.EXTRA_PARAM_KEY_DISCOVERY_CONFIG);
                        boolean hasPermission = false;
                        if (scanType != WifiP2pManager.WIFI_P2P_SCAN_FULL
                                && !isFeatureSupported(WifiP2pManager.FEATURE_FLEXIBLE_DISCOVERY)) {
                            replyToMessage(message, WifiP2pManager.DISCOVER_PEERS_FAILED,
                                    WifiP2pManager.ERROR);
                        }
                        if (isPlatformOrTargetSdkLessThanT(packageName, uid)) {
                            hasPermission = mWifiPermissionsUtil.checkCanAccessWifiDirect(
                                    packageName,
                                    getCallingFeatureId(message.sendingUid, message.replyTo),
                                    uid, true);
                        } else {
                            hasPermission = checkNearbyDevicesPermission(uid, packageName,
                                    extras, "DISCOVER_PEERS", message.obj);
                        }

                        if (!hasPermission) {
                            replyToMessage(message, WifiP2pManager.DISCOVER_PEERS_FAILED,
                                    WifiP2pManager.ERROR);
                            // remain at this state.
                            break;
                        }
                        if (mDiscoveryBlocked) {
                            replyToMessage(message, WifiP2pManager.DISCOVER_PEERS_FAILED,
                                    WifiP2pManager.BUSY);
                            break;
                        }
                        int apiType = WifiManager.API_P2P_DISCOVER_PEERS;
                        if (scanType == WifiP2pManager.WIFI_P2P_SCAN_SOCIAL) {
                            apiType = WifiManager.API_P2P_DISCOVER_PEERS_ON_SOCIAL_CHANNELS;
                        } else if (scanType == WifiP2pManager.WIFI_P2P_SCAN_SINGLE_FREQ) {
                            apiType = WifiManager.API_P2P_DISCOVER_PEERS_ON_SPECIFIC_FREQUENCY;
                        } else if (scanType == WifiP2pManager.WIFI_P2P_SCAN_WITH_CONFIG_PARAMS) {
                            apiType = WifiManager.API_P2P_DISCOVER_PEERS_WITH_CONFIG_PARAMS;
                        }
                        mLastCallerInfoManager.put(apiType, Process.myTid(), uid, 0, packageName,
                                true);
                        // do not send service discovery request while normal find operation.
                        clearSupplicantServiceRequest();
                        if (p2pFind(scanType, freq, DISCOVER_TIMEOUT_S, discoveryConfig)) {
                            mWifiP2pMetrics.incrementPeerScans();
                            replyToMessage(message, WifiP2pManager.DISCOVER_PEERS_SUCCEEDED);
                            sendP2pDiscoveryChangedBroadcast(true);
                        } else {
                            replyToMessage(message, WifiP2pManager.DISCOVER_PEERS_FAILED,
                                    WifiP2pManager.ERROR);
                        }
                        break;
                    }
                    case WifiP2pMonitor.P2P_FIND_STOPPED_EVENT:
                        mWifiNative.removeVendorElements();
                        sendP2pDiscoveryChangedBroadcast(false);
                        break;
                    case WifiP2pManager.STOP_DISCOVERY:
                        mLastCallerInfoManager.put(WifiManager.API_P2P_STOP_PEER_DISCOVERY,
                                Process.myTid(), message.sendingUid, 0,
                                getCallingPkgName(message.sendingUid, message.replyTo), true);
                        if (mWifiNative.p2pStopFind()) {
                            replyToMessage(message, WifiP2pManager.STOP_DISCOVERY_SUCCEEDED);
                        } else {
                            replyToMessage(message, WifiP2pManager.STOP_DISCOVERY_FAILED,
                                    WifiP2pManager.ERROR);
                        }
                        break;
                    case WifiP2pManager.DISCOVER_SERVICES: {
                        String packageName = getCallingPkgName(message.sendingUid, message.replyTo);
                        if (packageName == null) {
                            replyToMessage(message, WifiP2pManager.DISCOVER_SERVICES_FAILED,
                                    WifiP2pManager.ERROR);
                            break;
                        }
                        int uid = message.sendingUid;
                        Bundle extras = message.getData()
                                .getBundle(WifiP2pManager.EXTRA_PARAM_KEY_BUNDLE);
                        boolean hasPermission = false;
                        if (isPlatformOrTargetSdkLessThanT(packageName, uid)) {
                            hasPermission = mWifiPermissionsUtil.checkCanAccessWifiDirect(
                                    packageName,
                                    getCallingFeatureId(message.sendingUid, message.replyTo),
                                    uid, true);
                        } else {
                            hasPermission = checkNearbyDevicesPermission(uid, packageName,
                                    extras, "DISCOVER_SERVICES", message.obj);
                        }
                        if (!hasPermission) {
                            replyToMessage(message, WifiP2pManager.DISCOVER_SERVICES_FAILED,
                                    WifiP2pManager.ERROR);
                            // remain at this state.
                            break;
                        }
                        if (mDiscoveryBlocked) {
                            replyToMessage(message, WifiP2pManager.DISCOVER_SERVICES_FAILED,
                                    WifiP2pManager.BUSY);
                            break;
                        }
                        if (mVerboseLoggingEnabled) logd(getName() + " discover services");
                        if (!updateSupplicantServiceRequest()) {
                            replyToMessage(message, WifiP2pManager.DISCOVER_SERVICES_FAILED,
                                    WifiP2pManager.NO_SERVICE_REQUESTS);
                            break;
                        }
                        if (p2pFind(DISCOVER_TIMEOUT_S)) {
                            sendP2pDiscoveryChangedBroadcast(true);
                            mWifiP2pMetrics.incrementServiceScans();
                            replyToMessage(message, WifiP2pManager.DISCOVER_SERVICES_SUCCEEDED);
                        } else {
                            replyToMessage(message, WifiP2pManager.DISCOVER_SERVICES_FAILED,
                                    WifiP2pManager.ERROR);
                        }
                        break;
                    }
                    case WifiP2pMonitor.P2P_DEVICE_FOUND_EVENT:
                        if (message.obj == null) {
                            Log.e(TAG, "Illegal argument(s)");
                            break;
                        }
                        WifiP2pDevice device = (WifiP2pDevice) message.obj;
                        if (mThisDevice.deviceAddress.equals(device.deviceAddress)) break;
                        mPeers.updateSupplicantDetails(device);
                        sendPeersChangedBroadcast();
                        break;
                    case WifiP2pMonitor.P2P_DEVICE_LOST_EVENT:
                        if (message.obj == null) {
                            Log.e(TAG, "Illegal argument(s)");
                            break;
                        }
                        device = (WifiP2pDevice) message.obj;
                        // Gets current details for the one removed
                        device = mPeers.remove(device.deviceAddress);
                        if (device != null) {
                            sendPeersChangedBroadcast();
                        }
                        break;
                    case WifiP2pManager.ADD_LOCAL_SERVICE: {
                        String packageName = getCallingPkgName(message.sendingUid, message.replyTo);
                        if (packageName == null) {
                            replyToMessage(message, WifiP2pManager.ADD_LOCAL_SERVICE_FAILED);
                            break;
                        }
                        int uid = message.sendingUid;
                        Bundle extras = message.getData()
                                .getBundle(WifiP2pManager.EXTRA_PARAM_KEY_BUNDLE);
                        boolean hasPermission;
                        if (isPlatformOrTargetSdkLessThanT(packageName, uid)) {
                            hasPermission = mWifiPermissionsUtil.checkCanAccessWifiDirect(
                                    packageName,
                                    getCallingFeatureId(message.sendingUid, message.replyTo),
                                    uid, false);
                        } else {
                            hasPermission = checkNearbyDevicesPermission(uid, packageName,
                                    extras, "ADD_LOCAL_SERVICE", message.obj);
                        }
                        if (!hasPermission) {
                            replyToMessage(message, WifiP2pManager.ADD_LOCAL_SERVICE_FAILED);
                            // remain at this state.
                            break;
                        }
                        if (mVerboseLoggingEnabled) logd(getName() + " add service");
                        WifiP2pServiceInfo servInfo =
                                extras.getParcelable(WifiP2pManager.EXTRA_PARAM_KEY_SERVICE_INFO);
                        if (addLocalService(message.replyTo, servInfo)) {
                            replyToMessage(message, WifiP2pManager.ADD_LOCAL_SERVICE_SUCCEEDED);
                        } else {
                            replyToMessage(message, WifiP2pManager.ADD_LOCAL_SERVICE_FAILED);
                        }
                        break;
                    }
                    case WifiP2pManager.REMOVE_LOCAL_SERVICE:
                        if (mVerboseLoggingEnabled) logd(getName() + " remove service");
                        WifiP2pServiceInfo servInfo = (WifiP2pServiceInfo) message.obj;
                        removeLocalService(message.replyTo, servInfo);
                        replyToMessage(message, WifiP2pManager.REMOVE_LOCAL_SERVICE_SUCCEEDED);
                        break;
                    case WifiP2pManager.CLEAR_LOCAL_SERVICES:
                        if (mVerboseLoggingEnabled) logd(getName() + " clear service");
                        clearLocalServices(message.replyTo);
                        replyToMessage(message, WifiP2pManager.CLEAR_LOCAL_SERVICES_SUCCEEDED);
                        break;
                    case WifiP2pManager.ADD_SERVICE_REQUEST:
                        if (mVerboseLoggingEnabled) logd(getName() + " add service request");
                        if (!addServiceRequest(message.replyTo,
                                (WifiP2pServiceRequest) message.obj)) {
                            replyToMessage(message, WifiP2pManager.ADD_SERVICE_REQUEST_FAILED);
                            break;
                        }
                        replyToMessage(message, WifiP2pManager.ADD_SERVICE_REQUEST_SUCCEEDED);
                        break;
                    case WifiP2pManager.REMOVE_SERVICE_REQUEST:
                        if (mVerboseLoggingEnabled) logd(getName() + " remove service request");
                        removeServiceRequest(message.replyTo, (WifiP2pServiceRequest) message.obj);
                        replyToMessage(message, WifiP2pManager.REMOVE_SERVICE_REQUEST_SUCCEEDED);
                        break;
                    case WifiP2pManager.CLEAR_SERVICE_REQUESTS:
                        if (mVerboseLoggingEnabled) logd(getName() + " clear service request");
                        clearServiceRequests(message.replyTo);
                        replyToMessage(message, WifiP2pManager.CLEAR_SERVICE_REQUESTS_SUCCEEDED);
                        break;
                    case WifiP2pMonitor.P2P_SERV_DISC_RESP_EVENT:
                        if (mVerboseLoggingEnabled) {
                            logd(getName() + " receive service response");
                        }
                        if (message.obj == null) {
                            Log.e(TAG, "Illegal argument(s)");
                            break;
                        }
                        List<WifiP2pServiceResponse> sdRespList =
                                (List<WifiP2pServiceResponse>) message.obj;
                        for (WifiP2pServiceResponse resp : sdRespList) {
                            WifiP2pDevice dev =
                                    mPeers.get(resp.getSrcDevice().deviceAddress);
                            resp.setSrcDevice(dev);
                            sendServiceResponse(resp);
                        }
                        break;
                    case WifiP2pManager.DELETE_PERSISTENT_GROUP:
                        if (!checkNetworkSettingsOrNetworkStackOrOverrideWifiConfigPermission(
                                message.sendingUid)) {
                            loge("Permission violation - none of NETWORK_SETTING, NETWORK_STACK,"
                                    + " or OVERRIDE_WIFI_CONFIG permission, uid = "
                                    + message.sendingUid);
                            replyToMessage(message, WifiP2pManager.DELETE_PERSISTENT_GROUP_FAILED,
                                    WifiP2pManager.ERROR);
                            break;
                        }
                        if (mVerboseLoggingEnabled) logd(getName() + " delete persistent group");
                        mGroups.remove(message.arg1);
                        mWifiP2pMetrics.updatePersistentGroup(mGroups);
                        replyToMessage(message, WifiP2pManager.DELETE_PERSISTENT_GROUP_SUCCEEDED);
                        break;
                    case SET_MIRACAST_MODE:
                        mWifiNative.setMiracastMode(message.arg1);
                        break;
                    case WifiP2pManager.START_LISTEN:
                        String packageName = getCallingPkgName(message.sendingUid, message.replyTo);
                        if (packageName == null) {
                            replyToMessage(message, WifiP2pManager.START_LISTEN_FAILED);
                            break;
                        }
                        int uid = message.sendingUid;
                        int listenType = message.arg1;
                        if (listenType == WifiP2pManager.WIFI_P2P_EXT_LISTEN_WITH_PARAMS
                                && !SdkLevel.isAtLeastV()) {
                            replyToMessage(message, WifiP2pManager.START_LISTEN_FAILED);
                            break;
                        }
                        Bundle extras = message.getData()
                                .getBundle(WifiP2pManager.EXTRA_PARAM_KEY_BUNDLE);
                        WifiP2pExtListenParams extListenParams = SdkLevel.isAtLeastV()
                                && (listenType == WifiP2pManager.WIFI_P2P_EXT_LISTEN_WITH_PARAMS)
                                        ? extras.getParcelable(
                                                WifiP2pManager.EXTRA_PARAM_KEY_EXT_LISTEN_PARAMS,
                                                WifiP2pExtListenParams.class)
                                        : null;
                        boolean hasPermission;
                        if (isPlatformOrTargetSdkLessThanT(packageName, uid)) {
                            hasPermission = mWifiPermissionsUtil.checkCanAccessWifiDirect(
                                    packageName,
                                    getCallingFeatureId(message.sendingUid, message.replyTo),
                                    uid, true);
                        } else {
                            hasPermission = checkNearbyDevicesPermission(uid, packageName,
                                    extras, "START_LISTEN", message.obj);
                        }
                        if (!hasPermission) {
                            replyToMessage(message, WifiP2pManager.START_LISTEN_FAILED);
                            break;
                        }
                        mLastCallerInfoManager.put(WifiManager.API_P2P_START_LISTENING,
                                Process.myTid(), uid, 0, packageName, true);
                        if (mVerboseLoggingEnabled) logd(getName() + " start listen mode");
                        mWifiNative.p2pStopFind();
                        if (mWifiNative.p2pExtListen(true,
                                mContext.getResources().getInteger(
                                        R.integer.config_wifiP2pExtListenPeriodMs),
                                mContext.getResources().getInteger(
                                        R.integer.config_wifiP2pExtListenIntervalMs),
                                extListenParams)) {
                            replyToMessage(message, WifiP2pManager.START_LISTEN_SUCCEEDED);
                            sendP2pListenChangedBroadcast(true);
                        } else {
                            replyToMessage(message, WifiP2pManager.START_LISTEN_FAILED);
                        }
                        break;
                    case WifiP2pManager.STOP_LISTEN:
                        mLastCallerInfoManager.put(WifiManager.API_P2P_STOP_LISTENING,
                                Process.myTid(), message.sendingUid, 0,
                                getCallingPkgName(message.sendingUid, message.replyTo), true);
                        if (mVerboseLoggingEnabled) logd(getName() + " stop listen mode");
                        if (mWifiNative.p2pExtListen(false, 0, 0, null)) {
                            replyToMessage(message, WifiP2pManager.STOP_LISTEN_SUCCEEDED);
                            sendP2pListenChangedBroadcast(false);
                        } else {
                            replyToMessage(message, WifiP2pManager.STOP_LISTEN_FAILED);
                        }
                        mWifiNative.p2pStopFind();
                        break;
                    case WifiP2pManager.SET_CHANNEL:
                        if (!checkNetworkSettingsOrNetworkStackOrOverrideWifiConfigPermission(
                                message.sendingUid)) {
                            loge("Permission violation - none of NETWORK_SETTING, NETWORK_STACK,"
                                    + " or OVERRIDE_WIFI_CONFIG permission, uid = "
                                    + message.sendingUid);
                            replyToMessage(message, WifiP2pManager.SET_CHANNEL_FAILED,
                                    WifiP2pManager.ERROR);
                            break;
                        }
                        if (message.obj == null) {
                            Log.e(TAG, "Illegal arguments(s)");
                            break;
                        }
                        mLastCallerInfoManager.put(WifiManager.API_P2P_SET_CHANNELS,
                                Process.myTid(), message.sendingUid, 0,
                                getCallingPkgName(message.sendingUid, message.replyTo), true);
                        Bundle p2pChannels = (Bundle) message.obj;
                        mUserListenChannel = p2pChannels.getInt("lc", 0);
                        mUserOperatingChannel = p2pChannels.getInt("oc", 0);
                        if (updateP2pChannels()) {
                            replyToMessage(message, WifiP2pManager.SET_CHANNEL_SUCCEEDED);
                        } else {
                            replyToMessage(message, WifiP2pManager.SET_CHANNEL_FAILED);
                        }
                        break;
                    case WifiP2pManager.GET_HANDOVER_REQUEST:
                        Bundle requestBundle = new Bundle();
                        requestBundle.putString(WifiP2pManager.EXTRA_HANDOVER_MESSAGE,
                                mWifiNative.getNfcHandoverRequest());
                        replyToMessage(message, WifiP2pManager.RESPONSE_GET_HANDOVER_MESSAGE,
                                requestBundle);
                        break;
                    case WifiP2pManager.GET_HANDOVER_SELECT:
                        Bundle selectBundle = new Bundle();
                        selectBundle.putString(WifiP2pManager.EXTRA_HANDOVER_MESSAGE,
                                mWifiNative.getNfcHandoverSelect());
                        replyToMessage(message, WifiP2pManager.RESPONSE_GET_HANDOVER_MESSAGE,
                                selectBundle);
                        break;
                    case UPDATE_P2P_DISALLOWED_CHANNELS:
                        mCoexUnsafeChannels.clear();
                        if (null != message.obj) {
                            mCoexUnsafeChannels.addAll((List<CoexUnsafeChannel>) message.obj);
                        }
                        updateP2pChannels();
                        break;
                    default:
                        return NOT_HANDLED;
                }
                return HANDLED;
            }

            @Override
            public String getMessageLogRec(int what) {
                return P2pStateMachine.class.getSimpleName() + "."
                        + this.getClass().getSimpleName()
                        + "." + getWhatToString(what);
            }

            @Override
            public void exitImpl() {
                sendP2pDiscoveryChangedBroadcast(false);
                sendP2pListenChangedBroadcast(false);
                mUserListenChannel = 0;
                mUserOperatingChannel = 0;
                mCoexUnsafeChannels.clear();
                if (mTetheringManager != null) {
                    mTetheringManager.unregisterTetheringEventCallback(mTetheringEventCallback);
                }
            }
        }

        class InactiveState extends RunnerState {

            /**
             * The Runner state Constructor
             *
             * @param threshold the running time threshold in milliseconds
             */
            InactiveState(int threshold, @NonNull LocalLog localLog) {
                super(threshold, localLog);
            }

            @Override
            public void enterImpl() {
                logSmStateName(this.getName(),
                        getCurrentState() != null ? getCurrentState().getName() : "");
                mPeerAuthorizingTimestamp.clear();
                mSavedPeerConfig.invalidate();
                mDetailedState = NetworkInfo.DetailedState.IDLE;
                scheduleIdleShutdown();
            }

            @Override
            public void exitImpl() {
                cancelIdleShutdown();
            }

            @Override
            public boolean processMessageImpl(Message message) {
                logSmMessage(getName(), message);
                // Re-schedule the shutdown timer since we got the new operation.
                // only handle commands from clients.
                if (message.what > Protocol.BASE_WIFI_P2P_MANAGER
                        && message.what < Protocol.BASE_WIFI_P2P_SERVICE) {
                    scheduleIdleShutdown();
                }
                switch (message.what) {
                    case WifiP2pManager.CONNECT: {
                        String packageName = getCallingPkgName(message.sendingUid, message.replyTo);
                        if (packageName == null) {
                            replyToMessage(message, WifiP2pManager.CONNECT_FAILED);
                            break;
                        }
                        int uid = message.sendingUid;
                        Bundle extras = message.getData()
                                .getBundle(WifiP2pManager.EXTRA_PARAM_KEY_BUNDLE);
                        boolean hasPermission = false;
                        if (isPlatformOrTargetSdkLessThanT(packageName, uid)) {
                            hasPermission = mWifiPermissionsUtil.checkCanAccessWifiDirect(
                                    packageName,
                                    getCallingFeatureId(message.sendingUid, message.replyTo),
                                    uid, false);
                        } else {
                            hasPermission = checkNearbyDevicesPermission(uid, packageName,
                                    extras, "CONNECT", message.obj);
                        }
                        if (!hasPermission) {
                            replyToMessage(message, WifiP2pManager.CONNECT_FAILED);
                            // remain at this state.
                            break;
                        }
                        mLastCallerInfoManager.put(WifiManager.API_P2P_CONNECT,
                                Process.myTid(), uid, 0, packageName, true);
                        if (mVerboseLoggingEnabled) logd(getName() + " sending connect");
                        WifiP2pConfig config = (WifiP2pConfig)
                                extras.getParcelable(WifiP2pManager.EXTRA_PARAM_KEY_CONFIG);

                        boolean isConnectFailed = false;
                        if (isConfigValidAsGroup(config)) {
                            mAutonomousGroup = false;
                            mWifiNative.p2pStopFind();
                            if (mVerboseLoggingEnabled) {
                                logd("FAST_CONNECTION GC band freq: " + getGroupOwnerBandToString(
                                        config.groupOwnerBand));
                            }
                            if (mWifiNative.p2pGroupAdd(config, true)) {
                                reportConnectionEventTakeBugReportIfOverlapped(
                                        P2pConnectionEvent.CONNECTION_FAST,
                                        config, WifiMetricsProto.GroupEvent.GROUP_CLIENT, uid);
                                smTransition(this, mGroupNegotiationState);
                            } else {
                                loge("Cannot join a group with config.");
                                isConnectFailed = true;
                                replyToMessage(message, WifiP2pManager.CONNECT_FAILED);
                            }
                        } else {
                            if (isConfigInvalid(config)) {
                                loge("Dropping connect request " + config);
                                isConnectFailed = true;
                                replyToMessage(message, WifiP2pManager.CONNECT_FAILED);
                            } else {
                                mAutonomousGroup = false;
                                mWifiNative.p2pStopFind();
                                if (reinvokePersistentGroup(config, false)) {
                                    mWifiP2pMetrics.startConnectionEvent(
                                            P2pConnectionEvent.CONNECTION_REINVOKE,
                                            config, GroupEvent.GROUP_UNKNOWN, uid);
                                    smTransition(this, mGroupNegotiationState);
                                } else {
                                    mWifiP2pMetrics.startConnectionEvent(
                                            P2pConnectionEvent.CONNECTION_FRESH,
                                            config, GroupEvent.GROUP_UNKNOWN, uid);
                                    smTransition(this, mProvisionDiscoveryState);
                                }
                            }
                        }

                        if (!isConnectFailed) {
                            mSavedPeerConfig = config;
                            mPeers.updateStatus(mSavedPeerConfig.deviceAddress,
                                    WifiP2pDevice.INVITED);
                            sendPeersChangedBroadcast();
                            replyToMessage(message, WifiP2pManager.CONNECT_SUCCEEDED);
                        }
                        break;
                    }
                    case WifiP2pManager.STOP_DISCOVERY:
                        mLastCallerInfoManager.put(WifiManager.API_P2P_STOP_PEER_DISCOVERY,
                                Process.myTid(), message.sendingUid, 0,
                                getCallingPkgName(message.sendingUid, message.replyTo), true);
                        if (mWifiNative.p2pStopFind()) {
                            // When discovery stops in inactive state, flush to clear
                            // state peer data
                            mWifiNative.p2pFlush();
                            mServiceDiscReqId = null;
                            replyToMessage(message, WifiP2pManager.STOP_DISCOVERY_SUCCEEDED);
                        } else {
                            replyToMessage(message, WifiP2pManager.STOP_DISCOVERY_FAILED,
                                    WifiP2pManager.ERROR);
                        }
                        break;
                    case CMD_P2P_IDLE_SHUTDOWN:
                        Log.d(TAG, "IdleShutDown message received");
                        sendMessage(DISABLE_P2P);
                        break;
                    case WifiP2pMonitor.P2P_GO_NEGOTIATION_REQUEST_EVENT:
                        WifiP2pConfig config = (WifiP2pConfig) message.obj;
                        if (isConfigInvalid(config)) {
                            loge("Dropping GO neg request " + config);
                            break;
                        }
                        mSavedPeerConfig = config;
                        mAutonomousGroup = false;
                        mJoinExistingGroup = false;
                        mWifiP2pMetrics.startConnectionEvent(
                                P2pConnectionEvent.CONNECTION_FRESH,
                                config, GroupEvent.GROUP_UNKNOWN, Process.SYSTEM_UID);
                        smTransition(this, mUserAuthorizingNegotiationRequestState);
                        break;
                    case WifiP2pMonitor.P2P_INVITATION_RECEIVED_EVENT:
                        if (message.obj == null) {
                            Log.e(TAG, "Invalid argument(s)");
                            break;
                        }
                        WifiP2pGroup group = (WifiP2pGroup) message.obj;
                        WifiP2pDevice owner = group.getOwner();
                        if (owner == null) {
                            int id = group.getNetworkId();
                            if (id < 0) {
                                loge("Ignored invitation from null owner");
                                break;
                            }

                            String addr = mGroups.getOwnerAddr(id);
                            if (addr != null) {
                                group.setOwner(new WifiP2pDevice(addr));
                                owner = group.getOwner();
                            } else {
                                loge("Ignored invitation from null owner");
                                break;
                            }
                        }
                        config = new WifiP2pConfig();
                        config.deviceAddress = group.getOwner().deviceAddress;
                        if (isConfigInvalid(config)) {
                            loge("Dropping invitation request " + config);
                            break;
                        }
                        mSavedPeerConfig = config;

                        // Check if we have the owner in peer list and use appropriate
                        // wps method. Default is to use PBC.
                        if (owner != null && ((owner = mPeers.get(owner.deviceAddress)) != null)) {
                            if (owner.wpsPbcSupported()) {
                                mSavedPeerConfig.wps.setup = WpsInfo.PBC;
                            } else if (owner.wpsKeypadSupported()) {
                                mSavedPeerConfig.wps.setup = WpsInfo.KEYPAD;
                            } else if (owner.wpsDisplaySupported()) {
                                mSavedPeerConfig.wps.setup = WpsInfo.DISPLAY;
                            }
                        }

                        mAutonomousGroup = false;
                        mJoinExistingGroup = true;
                        mWifiP2pMetrics.startConnectionEvent(
                                P2pConnectionEvent.CONNECTION_FRESH,
                                config, GroupEvent.GROUP_UNKNOWN, Process.SYSTEM_UID);
                        smTransition(this, mUserAuthorizingInviteRequestState);
                        break;
                    case WifiP2pMonitor.P2P_PROV_DISC_PBC_REQ_EVENT:
                    case WifiP2pMonitor.P2P_PROV_DISC_ENTER_PIN_EVENT:
                        // We let the supplicant handle the provision discovery response
                        // and wait instead for the GO_NEGOTIATION_REQUEST_EVENT.
                        // Handling provision discovery and issuing a p2p_connect before
                        // group negotiation comes through causes issues
                        break;
                    case WifiP2pMonitor.P2P_PROV_DISC_SHOW_PIN_EVENT:
                        if (message.obj == null) {
                            Log.e(TAG, "Illegal argument(s)");
                            break;
                        }
                        WifiP2pProvDiscEvent provDisc = (WifiP2pProvDiscEvent) message.obj;
                        WifiP2pDevice device = provDisc.device;
                        if (device == null) {
                            loge("Device entry is null");
                            break;
                        }
                        mSavedPeerConfig = new WifiP2pConfig();
                        mSavedPeerConfig.wps.setup = WpsInfo.KEYPAD;
                        mSavedPeerConfig.deviceAddress = device.deviceAddress;
                        mSavedPeerConfig.wps.pin = provDisc.pin;
                        if (SdkLevel.isAtLeastV() && provDisc.getVendorData() != null) {
                            mSavedPeerConfig.setVendorData(provDisc.getVendorData());
                        }

                        notifyP2pProvDiscShowPinRequest(provDisc.pin, device.deviceAddress);
                        mPeers.updateStatus(device.deviceAddress, WifiP2pDevice.INVITED);
                        sendPeersChangedBroadcast();
                        smTransition(this, mUserAuthorizingNegotiationRequestState);
                        break;
                    case WifiP2pManager.CREATE_GROUP: {
                        String packageName = getCallingPkgName(message.sendingUid, message.replyTo);
                        if (packageName == null) {
                            replyToMessage(message, WifiP2pManager.CREATE_GROUP_FAILED,
                                    WifiP2pManager.ERROR);
                            break;
                        }
                        int uid = message.sendingUid;
                        Bundle extras = message.getData()
                                .getBundle(WifiP2pManager.EXTRA_PARAM_KEY_BUNDLE);
                        boolean hasPermission;
                        if (isPlatformOrTargetSdkLessThanT(packageName, uid)) {
                            hasPermission = mWifiPermissionsUtil.checkCanAccessWifiDirect(
                                    packageName,
                                    getCallingFeatureId(message.sendingUid, message.replyTo),
                                    uid, false);
                        } else {
                            hasPermission = checkNearbyDevicesPermission(uid, packageName,
                                    extras, "CREATE_GROUP", message.obj);
                        }
                        if (!hasPermission) {
                            replyToMessage(message, WifiP2pManager.CREATE_GROUP_FAILED,
                                    WifiP2pManager.ERROR);
                            // remain at this state.
                            break;
                        }
                        mAutonomousGroup = true;
                        int netId = message.arg1;
                        config = extras.getParcelable(WifiP2pManager.EXTRA_PARAM_KEY_CONFIG);
                        mLastCallerInfoManager.put(config == null
                                        ? WifiManager.API_P2P_CREATE_GROUP
                                        : WifiManager.API_P2P_CREATE_GROUP_P2P_CONFIG,
                                Process.myTid(), uid, 0, packageName, true);
                        boolean ret = false;
                        if (config != null) {
                            if (isConfigValidAsGroup(config)) {
                                if (mVerboseLoggingEnabled) {
                                    logd("FAST_CONNECTION GO band freq: "
                                            + getGroupOwnerBandToString(config.groupOwnerBand));
                                }
                                reportConnectionEventTakeBugReportIfOverlapped(
                                        P2pConnectionEvent.CONNECTION_FAST,
                                        config, GroupEvent.GROUP_OWNER, uid);
                                ret = mWifiNative.p2pGroupAdd(config, false);
                            }
                        } else if (netId == WifiP2pGroup.NETWORK_ID_PERSISTENT) {
                            // check if the go persistent group is present.
                            netId = mGroups.getNetworkId(mThisDevice.deviceAddress);
                            if (netId != -1) {
                                mWifiP2pMetrics.startConnectionEvent(
                                        P2pConnectionEvent.CONNECTION_REINVOKE,
                                        null, GroupEvent.GROUP_OWNER, uid);
                                ret = mWifiNative.p2pGroupAdd(netId);
                            } else {
                                mWifiP2pMetrics.startConnectionEvent(
                                        P2pConnectionEvent.CONNECTION_LOCAL,
                                        null, GroupEvent.GROUP_OWNER, uid);
                                ret = mWifiNative.p2pGroupAdd(true);
                            }
                        } else {
                            mWifiP2pMetrics.startConnectionEvent(
                                    P2pConnectionEvent.CONNECTION_LOCAL,
                                    null, GroupEvent.GROUP_OWNER, uid);
                            ret = mWifiNative.p2pGroupAdd(false);
                        }

                        if (ret) {
                            replyToMessage(message, WifiP2pManager.CREATE_GROUP_SUCCEEDED);
                            smTransition(this, mGroupNegotiationState);
                        } else {
                            replyToMessage(message, WifiP2pManager.CREATE_GROUP_FAILED,
                                    WifiP2pManager.ERROR);
                            // remain at this state.
                            String errorMsg = "P2P group creating failed";
                            if (mVerboseLoggingEnabled) logd(getName() + errorMsg);
                            if (mWifiP2pMetrics.isP2pFastConnectionType()) {
                                takeBugReportP2pFailureIfNeeded("Wi-Fi BugReport (P2P "
                                        + mWifiP2pMetrics.getP2pGroupRoleString()
                                        + " creation failure)", errorMsg);
                            }
                            mWifiP2pMetrics.endConnectionEvent(
                                    P2pConnectionEvent.CLF_CREATE_GROUP_FAILED);
                        }
                        break;
                    }
                    case WifiP2pMonitor.P2P_GROUP_STARTED_EVENT:
                        if (message.obj == null) {
                            Log.e(TAG, "Invalid argument(s)");
                            break;
                        }
                        mGroup = (WifiP2pGroup) message.obj;
                        if (mVerboseLoggingEnabled) logd(getName() + " group started");
                        if (mGroup.isGroupOwner()
                                && EMPTY_DEVICE_ADDRESS.equals(mGroup.getOwner().deviceAddress)) {
                            // wpa_supplicant doesn't set own device address to go_dev_addr.
                            mGroup.getOwner().deviceAddress = mThisDevice.deviceAddress;
                        }
                        // We hit this scenario when a persistent group is reinvoked
                        if (mGroup.getNetworkId() == WifiP2pGroup.NETWORK_ID_PERSISTENT) {
                            mAutonomousGroup = false;
                            deferMessage(message);
                            smTransition(this, mGroupNegotiationState);
                        } else {
                            loge("Unexpected group creation, remove " + mGroup);
                            mWifiNative.p2pGroupRemove(mGroup.getInterface());
                        }
                        break;
                    case WifiP2pManager.START_LISTEN:
                        String packageName = getCallingPkgName(message.sendingUid, message.replyTo);
                        if (packageName == null) {
                            replyToMessage(message, WifiP2pManager.START_LISTEN_FAILED);
                            break;
                        }
                        int uid = message.sendingUid;
                        int listenType = message.arg1;
                        if (listenType == WifiP2pManager.WIFI_P2P_EXT_LISTEN_WITH_PARAMS
                                && !SdkLevel.isAtLeastV()) {
                            replyToMessage(message, WifiP2pManager.START_LISTEN_FAILED);
                            break;
                        }
                        Bundle extras = message.getData()
                                .getBundle(WifiP2pManager.EXTRA_PARAM_KEY_BUNDLE);
                        WifiP2pExtListenParams extListenParams = SdkLevel.isAtLeastV()
                                && (listenType == WifiP2pManager.WIFI_P2P_EXT_LISTEN_WITH_PARAMS)
                                        ? extras.getParcelable(
                                            WifiP2pManager.EXTRA_PARAM_KEY_EXT_LISTEN_PARAMS,
                                            WifiP2pExtListenParams.class)
                                        : null;
                        boolean hasPermission;
                        if (isPlatformOrTargetSdkLessThanT(packageName, uid)) {
                            hasPermission = mWifiPermissionsUtil.checkCanAccessWifiDirect(
                                    packageName,
                                    getCallingFeatureId(message.sendingUid, message.replyTo),
                                    uid, true);
                        } else {
                            hasPermission = checkNearbyDevicesPermission(uid, packageName,
                                    extras, "START_LISTEN", message.obj);
                        }
                        if (!hasPermission) {
                            replyToMessage(message, WifiP2pManager.START_LISTEN_FAILED);
                            break;
                        }
                        mLastCallerInfoManager.put(WifiManager.API_P2P_START_LISTENING,
                                Process.myTid(), uid, 0, packageName, true);
                        if (mVerboseLoggingEnabled) logd(getName() + " start listen mode");
                        mWifiNative.p2pStopFind();
                        if (mWifiNative.p2pExtListen(true,
                                mContext.getResources().getInteger(
                                        R.integer.config_wifiP2pExtListenPeriodMs),
                                mContext.getResources().getInteger(
                                        R.integer.config_wifiP2pExtListenIntervalMs),
                                extListenParams)) {
                            replyToMessage(message, WifiP2pManager.START_LISTEN_SUCCEEDED);
                            sendP2pListenChangedBroadcast(true);
                        } else {
                            replyToMessage(message, WifiP2pManager.START_LISTEN_FAILED);
                        }
                        break;
                    case WifiP2pManager.STOP_LISTEN:
                        mLastCallerInfoManager.put(WifiManager.API_P2P_STOP_LISTENING,
                                Process.myTid(), message.sendingUid, 0,
                                getCallingPkgName(message.sendingUid, message.replyTo), true);
                        if (mVerboseLoggingEnabled) logd(getName() + " stop listen mode");
                        if (mWifiNative.p2pExtListen(false, 0, 0, null)) {
                            replyToMessage(message, WifiP2pManager.STOP_LISTEN_SUCCEEDED);
                            sendP2pListenChangedBroadcast(false);
                        } else {
                            replyToMessage(message, WifiP2pManager.STOP_LISTEN_FAILED);
                        }
                        mWifiNative.p2pStopFind();
                        break;
                    case WifiP2pManager.SET_CHANNEL:
                        if (!checkNetworkSettingsOrNetworkStackOrOverrideWifiConfigPermission(
                                message.sendingUid)) {
                            loge("Permission violation - none of NETWORK_SETTING, NETWORK_STACK,"
                                    + " or OVERRIDE_WIFI_CONFIG permission, uid = "
                                    + message.sendingUid);
                            replyToMessage(message, WifiP2pManager.SET_CHANNEL_FAILED,
                                    WifiP2pManager.ERROR);
                            break;
                        }
                        if (message.obj == null) {
                            Log.e(TAG, "Illegal arguments(s)");
                            break;
                        }
                        mLastCallerInfoManager.put(WifiManager.API_P2P_SET_CHANNELS,
                                Process.myTid(), message.sendingUid, 0,
                                getCallingPkgName(message.sendingUid, message.replyTo), true);
                        Bundle p2pChannels = (Bundle) message.obj;
                        mUserListenChannel = p2pChannels.getInt("lc", 0);
                        mUserOperatingChannel = p2pChannels.getInt("oc", 0);
                        if (updateP2pChannels()) {
                            replyToMessage(message, WifiP2pManager.SET_CHANNEL_SUCCEEDED);
                        } else {
                            replyToMessage(message, WifiP2pManager.SET_CHANNEL_FAILED);
                        }
                        break;
                    case WifiP2pManager.INITIATOR_REPORT_NFC_HANDOVER:
                        String handoverSelect = null;

                        if (message.obj != null) {
                            handoverSelect = ((Bundle) message.obj)
                                    .getString(WifiP2pManager.EXTRA_HANDOVER_MESSAGE);
                        }

                        if (handoverSelect != null
                                && mWifiNative.initiatorReportNfcHandover(handoverSelect)) {
                            replyToMessage(message, WifiP2pManager.REPORT_NFC_HANDOVER_SUCCEEDED);
                            smTransition(this, mGroupCreatingState);
                        } else {
                            replyToMessage(message, WifiP2pManager.REPORT_NFC_HANDOVER_FAILED);
                        }
                        break;
                    case WifiP2pManager.RESPONDER_REPORT_NFC_HANDOVER:
                        String handoverRequest = null;

                        if (message.obj != null) {
                            handoverRequest = ((Bundle) message.obj)
                                    .getString(WifiP2pManager.EXTRA_HANDOVER_MESSAGE);
                        }

                        if (handoverRequest != null
                                && mWifiNative.responderReportNfcHandover(handoverRequest)) {
                            replyToMessage(message, WifiP2pManager.REPORT_NFC_HANDOVER_SUCCEEDED);
                            smTransition(this, mGroupCreatingState);
                        } else {
                            replyToMessage(message, WifiP2pManager.REPORT_NFC_HANDOVER_FAILED);
                        }
                        break;
                    default:
                        return NOT_HANDLED;
                }
                return HANDLED;
            }

            @Override
            public String getMessageLogRec(int what) {
                return P2pStateMachine.class.getSimpleName() + "."
                        + this.getClass().getSimpleName()
                        + "." + getWhatToString(what);
            }
        }

        class P2pRejectWaitState extends RunnerState {

            /**
             * The Runner state Constructor
             *
             * @param threshold the running time threshold in milliseconds
             */
            P2pRejectWaitState(int threshold, @NonNull LocalLog localLog) {
                super(threshold, localLog);
            }

            @Override
            public void enterImpl() {
                if (mVerboseLoggingEnabled) logd(getName());
            }

            @Override
            public void exitImpl() {

            }

            @Override
            public boolean processMessageImpl(Message message) {
                boolean ret = HANDLED;
                switch (message.what) {
                    case P2P_REJECTION_RESUME_AFTER_DELAY:
                        if (sP2pRejectionResumeAfterDelayIndex == message.arg1) {
                            logd(
                                    "P2p rejection resume after delay - originated from "
                                            + getWhatToString(message.what));
                            if (message.arg2 == WifiP2pManager.CANCEL_CONNECT) {
                                handleGroupCreationFailure();
                                if (message.obj != null) {
                                    replyToMessage(
                                            (Message) message.obj,
                                            WifiP2pManager.CANCEL_CONNECT_SUCCEEDED);
                                }
                            }
                            transitionTo(mInactiveState);
                        } else {
                            loge(
                                    "Stale P2p rejection resume after delay - cached index: "
                                            + sP2pRejectionResumeAfterDelayIndex
                                            + " index from msg: "
                                            + message.arg1);
                        }
                        break;
                    default:
                        ret = NOT_HANDLED;
                }
                return ret;
            }

            @Override
            public String getMessageLogRec(int what) {
                return P2pStateMachine.class.getSimpleName() + "."
                        + this.getClass().getSimpleName()
                        + "." + getWhatToString(what);
            }
        }

        class GroupCreatingState extends RunnerState {

            /**
             * The Runner state Constructor
             *
             * @param threshold the running time threshold in milliseconds
             */
            GroupCreatingState(int threshold, @NonNull LocalLog localLog) {
                super(threshold, localLog);
            }

            @Override
            public void enterImpl() {
                logSmStateName(this.getName(),
                        getCurrentState() != null ? getCurrentState().getName() : "");
                onGroupCreating();
                if (SdkLevel.isAtLeastT()) {
                    mDetailedState = NetworkInfo.DetailedState.CONNECTING;
                    sendP2pConnectionChangedBroadcast();
                }
                sendMessageDelayed(obtainMessage(GROUP_CREATING_TIMED_OUT,
                        ++sGroupCreatingTimeoutIndex, 0), GROUP_CREATING_WAIT_TIME_MS);
            }

            @Override
            public void exitImpl() {

            }

            @Override
            public boolean processMessageImpl(Message message) {
                logSmMessage(getName(), message);
                boolean ret = HANDLED;
                switch (message.what) {
                    case GROUP_CREATING_TIMED_OUT:
                        if (sGroupCreatingTimeoutIndex == message.arg1) {
                            String errorMsg = "P2P group negotiation timed out";
                            if (mVerboseLoggingEnabled) logd(getName() + errorMsg);
                            if (mWifiP2pMetrics.isP2pFastConnectionType()) {
                                takeBugReportP2pFailureIfNeeded("Wi-Fi BugReport (P2P "
                                        + mWifiP2pMetrics.getP2pGroupRoleString()
                                        + " creation failure)", errorMsg);
                            }
                            mWifiP2pMetrics.endConnectionEvent(
                                    P2pConnectionEvent.CLF_TIMEOUT);
                            handleGroupCreationFailure();
                            smTransition(this, mInactiveState);
                        }
                        break;
                    case WifiP2pMonitor.P2P_DEVICE_LOST_EVENT:
                        if (message.obj == null) {
                            Log.e(TAG, "Illegal argument(s)");
                            break;
                        }
                        WifiP2pDevice device = (WifiP2pDevice) message.obj;
                        if (!mSavedPeerConfig.deviceAddress.equals(device.deviceAddress)) {
                            if (mVerboseLoggingEnabled) {
                                logd("mSavedPeerConfig " + mSavedPeerConfig.deviceAddress
                                        + "device " + device.deviceAddress);
                            }
                            // Do the regular device lost handling
                            ret = NOT_HANDLED;
                            break;
                        }
                        // Do nothing
                        if (mVerboseLoggingEnabled) logd("Add device to lost list " + device);
                        mPeersLostDuringConnection.updateSupplicantDetails(device);
                        break;
                    case WifiP2pManager.DISCOVER_PEERS:
                        // Discovery will break negotiation
                        replyToMessage(message, WifiP2pManager.DISCOVER_PEERS_FAILED,
                                WifiP2pManager.BUSY);
                        break;
                    case WifiP2pManager.STOP_DISCOVERY:
                        // Stop discovery will clear pending TX action and cause disconnection.
                        replyToMessage(message, WifiP2pManager.STOP_DISCOVERY_FAILED,
                                WifiP2pManager.BUSY);
                        break;
                    case WifiP2pManager.START_LISTEN:
                        replyToMessage(message, WifiP2pManager.START_LISTEN_FAILED,
                                WifiP2pManager.BUSY);
                        break;
                    case WifiP2pManager.STOP_LISTEN:
                        if (mVerboseLoggingEnabled) {
                            logd(getName() + " stop listen mode");
                        }
                        if (mWifiNative.p2pExtListen(false, 0, 0, null)) {
                            replyToMessage(message, WifiP2pManager.STOP_LISTEN_SUCCEEDED);
                        } else {
                            replyToMessage(message, WifiP2pManager.STOP_LISTEN_FAILED);
                        }
                        break;
                    case WifiP2pManager.CANCEL_CONNECT:
                        // Do a supplicant p2p_cancel which only cancels an ongoing
                        // group negotiation. This will fail for a pending provision
                        // discovery or for a pending user action, but at the framework
                        // level, we always treat cancel as succeeded and enter
                        // an inactive state
                        mLastCallerInfoManager.put(WifiManager.API_P2P_CANCEL_CONNECT,
                                Process.myTid(), message.sendingUid, 0,
                                getCallingPkgName(message.sendingUid, message.replyTo), true);
                        mWifiNative.p2pCancelConnect();
                        mWifiP2pMetrics.endConnectionEvent(
                                P2pConnectionEvent.CLF_CANCEL);
                        // Notify the peer about the rejection.
                        int delay = 0;
                        if (!TextUtils.isEmpty(mSavedPeerConfig.deviceAddress)) {
                            mWifiNative.p2pStopFind();
                            delay = sendP2pRejection();
                        }
                        transitionTo(mP2pRejectWaitState);
                        sendMessageDelayed(
                                obtainMessage(
                                        P2P_REJECTION_RESUME_AFTER_DELAY,
                                        ++sP2pRejectionResumeAfterDelayIndex,
                                        WifiP2pManager.CANCEL_CONNECT,
                                        Message.obtain(message)),
                                delay);
                        break;
                    case WifiP2pMonitor.P2P_GO_NEGOTIATION_SUCCESS_EVENT:
                        // We hit this scenario when NFC handover is invoked.
                        mAutonomousGroup = false;
                        smTransition(this, mGroupNegotiationState);
                        break;
                    default:
                        ret = NOT_HANDLED;
                }
                return ret;
            }

            @Override
            public String getMessageLogRec(int what) {
                return P2pStateMachine.class.getSimpleName() + "."
                        + this.getClass().getSimpleName()
                        + "." + getWhatToString(what);
            }
        }

        class UserAuthorizingNegotiationRequestState extends RunnerState {

            /**
             * The Runner state Constructor
             *
             * @param threshold the running time threshold in milliseconds
             */
            UserAuthorizingNegotiationRequestState(int threshold,
                    @NonNull LocalLog localLog) {
                super(threshold, localLog);
            }

            @Override
            public void enterImpl() {
                logSmStateName(this.getName(),
                        getCurrentState() != null ? getCurrentState().getName() : "");
                if (mSavedPeerConfig.wps.setup == WpsInfo.PBC
                            || TextUtils.isEmpty(mSavedPeerConfig.wps.pin)) {
                    notifyInvitationReceived(
                            WifiP2pManager.ExternalApproverRequestListener
                                    .REQUEST_TYPE_NEGOTIATION);
                }
            }

            @Override
            public boolean processMessageImpl(Message message) {
                logSmMessage(getName(), message);
                switch (message.what) {
                    case PEER_CONNECTION_USER_ACCEPT:
                        mWifiNative.p2pStopFind();
                        p2pConnectWithPinDisplay(mSavedPeerConfig,
                                                 P2P_CONNECT_TRIGGER_GROUP_NEG_REQ);
                        mPeers.updateStatus(mSavedPeerConfig.deviceAddress, WifiP2pDevice.INVITED);
                        sendPeersChangedBroadcast();
                        smTransition(this, mGroupNegotiationState);
                        break;
                    case PEER_CONNECTION_USER_REJECT:
                        if (mVerboseLoggingEnabled) {
                            logd("User rejected negotiation " + mSavedPeerConfig);
                        }
                        if (!TextUtils.isEmpty(mSavedPeerConfig.deviceAddress)) {
                            WifiP2pDevice dev = fetchCurrentDeviceDetails(mSavedPeerConfig);
                            boolean join = (dev != null && dev.isGroupOwner())
                                    || mJoinExistingGroup;
                            if (mVerboseLoggingEnabled) {
                                logd("User rejected negotiation, join =  " + join
                                        + " peer = " + mSavedPeerConfig);
                            }
                            mSavedRejectedPeerConfig = new WifiP2pConfig(mSavedPeerConfig);
                            if (join) {
                                mWifiNative.p2pCancelConnect();
                                mWifiNative.p2pStopFind();
                            }

                            int delay = sendP2pRejection();
                            mDetailedState = NetworkInfo.DetailedState.DISCONNECTED;
                            onGroupNegotiationRejectedByUser();
                            sendP2pConnectionChangedBroadcast();
                            mSavedPeerConfig.invalidate();
                            transitionTo(mP2pRejectWaitState);
                            sendMessageDelayed(
                                    obtainMessage(
                                            P2P_REJECTION_RESUME_AFTER_DELAY,
                                            ++sP2pRejectionResumeAfterDelayIndex,
                                            PEER_CONNECTION_USER_REJECT),
                                    delay);
                        } else {
                            mWifiNative.p2pCancelConnect();
                            handleGroupCreationFailure();
                            smTransition(this, mInactiveState);
                        }
                        break;
                    case PEER_CONNECTION_USER_CONFIRM:
                        mSavedPeerConfig.wps.setup = WpsInfo.DISPLAY;
                        mSavedPeerConfig.groupOwnerIntent =
                                selectGroupOwnerIntentIfNecessary(mSavedPeerConfig);
                        mWifiNative.p2pConnect(mSavedPeerConfig, FORM_GROUP);
                        smTransition(this, mGroupNegotiationState);
                        break;
                    case WifiP2pMonitor.P2P_PROV_DISC_FAILURE_EVENT:
                        loge("provision discovery failed status: " + message.arg1);
                        handleGroupCreationFailure();
                        smTransition(this, mInactiveState);
                        break;
                    case WifiP2pManager.SET_CONNECTION_REQUEST_RESULT: {
                        if (!handleSetConnectionResult(message,
                                WifiP2pManager.ExternalApproverRequestListener
                                        .REQUEST_TYPE_NEGOTIATION)) {
                            replyToMessage(message,
                                    WifiP2pManager.SET_CONNECTION_REQUEST_RESULT_FAILED,
                                    WifiP2pManager.ERROR);
                            break;
                        }
                        replyToMessage(message,
                                WifiP2pManager.SET_CONNECTION_REQUEST_RESULT_SUCCEEDED);
                        break;
                    }
                    default:
                        return NOT_HANDLED;
                }
                return HANDLED;
            }

            @Override
            public void exitImpl() {
                if (null != mInvitationDialogHandle) {
                    mInvitationDialogHandle.dismissDialog();
                    mInvitationDialogHandle = null;
                }
                if (null != mLegacyInvitationDialog) {
                    mLegacyInvitationDialog.dismiss();
                    mLegacyInvitationDialog = null;
                }
            }

            @Override
            public String getMessageLogRec(int what) {
                return P2pStateMachine.class.getSimpleName() + "."
                        + this.getClass().getSimpleName()
                        + "." + getWhatToString(what);
            }
        }

        class UserAuthorizingInviteRequestState extends RunnerState {

            /**
             * The Runner state Constructor
             *
             * @param threshold the running time threshold in milliseconds
             */
            UserAuthorizingInviteRequestState(int threshold,
                    @NonNull LocalLog localLog) {
                super(threshold, localLog);
            }

            @Override
            public void enterImpl() {
                logSmStateName(this.getName(),
                        getCurrentState() != null ? getCurrentState().getName() : "");
                notifyInvitationReceived(
                        WifiP2pManager.ExternalApproverRequestListener.REQUEST_TYPE_INVITATION);
            }

            @Override
            public boolean processMessageImpl(Message message) {
                logSmMessage(getName(), message);
                switch (message.what) {
                    case PEER_CONNECTION_USER_ACCEPT:
                        mWifiNative.p2pStopFind();
                        if (!reinvokePersistentGroup(mSavedPeerConfig, true)) {
                            // Do negotiation when persistence fails
                            p2pConnectWithPinDisplay(mSavedPeerConfig,
                                                     P2P_CONNECT_TRIGGER_INVITATION_REQ);
                        }
                        mPeers.updateStatus(mSavedPeerConfig.deviceAddress, WifiP2pDevice.INVITED);
                        sendPeersChangedBroadcast();
                        smTransition(this, mGroupNegotiationState);
                        break;
                    case PEER_CONNECTION_USER_REJECT:
                        if (mVerboseLoggingEnabled) {
                            logd("User rejected invitation " + mSavedPeerConfig);
                        }
                        smTransition(this, mInactiveState);
                        break;
                    case WifiP2pMonitor.P2P_PROV_DISC_FAILURE_EVENT:
                        loge("provision discovery failed status: " + message.arg1);
                        handleGroupCreationFailure();
                        smTransition(this, mInactiveState);
                        break;
                    case WifiP2pManager.SET_CONNECTION_REQUEST_RESULT:
                        if (!handleSetConnectionResult(message,
                                WifiP2pManager.ExternalApproverRequestListener
                                        .REQUEST_TYPE_INVITATION)) {
                            replyToMessage(message,
                                    WifiP2pManager.SET_CONNECTION_REQUEST_RESULT_FAILED,
                                    WifiP2pManager.ERROR);
                            break;
                        }
                        replyToMessage(message,
                                WifiP2pManager.SET_CONNECTION_REQUEST_RESULT_SUCCEEDED);
                        break;
                    default:
                        return NOT_HANDLED;
                }
                return HANDLED;
            }

            @Override
            public void exitImpl() {
                if (null != mInvitationDialogHandle) {
                    mInvitationDialogHandle.dismissDialog();
                    mInvitationDialogHandle = null;
                }
                if (null != mLegacyInvitationDialog) {
                    mLegacyInvitationDialog.dismiss();
                    mLegacyInvitationDialog = null;
                }
            }

            @Override
            public String getMessageLogRec(int what) {
                return P2pStateMachine.class.getSimpleName() + "."
                        + this.getClass().getSimpleName()
                        + "." + getWhatToString(what);
            }
        }

        class ProvisionDiscoveryState extends RunnerState {

            /**
             * The Runner state Constructor
             *
             * @param threshold the running time threshold in milliseconds
             */
            ProvisionDiscoveryState(int threshold, @NonNull LocalLog localLog) {
                super(threshold, localLog);
            }

            @Override
            public void enterImpl() {
                logSmStateName(this.getName(),
                        getCurrentState() != null ? getCurrentState().getName() : "");
                mWifiNative.p2pProvisionDiscovery(mSavedPeerConfig);
            }

            @Override
            public void exitImpl() {

            }

            @Override
            public boolean processMessageImpl(Message message) {
                logSmMessage(getName(), message);
                WifiP2pProvDiscEvent provDisc = null;
                WifiP2pDevice device = null;
                switch (message.what) {
                    case WifiP2pMonitor.P2P_PROV_DISC_PBC_RSP_EVENT:
                        if (message.obj == null) {
                            Log.e(TAG, "Invalid argument(s)");
                            break;
                        }
                        provDisc = (WifiP2pProvDiscEvent) message.obj;
                        device = provDisc.device;
                        if (device != null
                                && !device.deviceAddress.equals(mSavedPeerConfig.deviceAddress)) {
                            break;
                        }
                        if (mSavedPeerConfig.wps.setup == WpsInfo.PBC) {
                            if (mVerboseLoggingEnabled) {
                                logd("Found a match " + mSavedPeerConfig);
                            }
                            p2pConnectWithPinDisplay(mSavedPeerConfig, P2P_CONNECT_TRIGGER_OTHER);
                            smTransition(this, mGroupNegotiationState);
                        }
                        break;
                    case WifiP2pMonitor.P2P_PROV_DISC_ENTER_PIN_EVENT:
                        if (message.obj == null) {
                            Log.e(TAG, "Illegal argument(s)");
                            break;
                        }
                        provDisc = (WifiP2pProvDiscEvent) message.obj;
                        device = provDisc.device;
                        if (device != null
                                && !device.deviceAddress.equals(mSavedPeerConfig.deviceAddress)) {
                            break;
                        }
                        if (mSavedPeerConfig.wps.setup == WpsInfo.KEYPAD) {
                            if (mVerboseLoggingEnabled) {
                                logd("Found a match " + mSavedPeerConfig);
                            }
                            if (TextUtils.isEmpty(mSavedPeerConfig.wps.pin)) {
                                // Some implementations get the PIN OOB and deliver it from
                                // Supplicant. This is to avoid connecting with the dialog box
                                mSavedPeerConfig.wps.pin = provDisc.pin;
                            }
                            // we already have the pin
                            if (!TextUtils.isEmpty(mSavedPeerConfig.wps.pin)) {
                                p2pConnectWithPinDisplay(mSavedPeerConfig,
                                                         P2P_CONNECT_TRIGGER_OTHER);
                                smTransition(this, mGroupNegotiationState);
                            } else {
                                mJoinExistingGroup = false;
                                smTransition(this, mUserAuthorizingNegotiationRequestState);
                            }
                        }
                        break;
                    case WifiP2pMonitor.P2P_PROV_DISC_SHOW_PIN_EVENT:
                        if (message.obj == null) {
                            Log.e(TAG, "Illegal argument(s)");
                            break;
                        }
                        provDisc = (WifiP2pProvDiscEvent) message.obj;
                        device = provDisc.device;
                        if (device == null) {
                            Log.e(TAG, "Invalid device");
                            break;
                        }
                        if (!device.deviceAddress.equals(mSavedPeerConfig.deviceAddress)) {
                            break;
                        }
                        if (mSavedPeerConfig.wps.setup == WpsInfo.DISPLAY) {
                            if (mVerboseLoggingEnabled) {
                                logd("Found a match " + mSavedPeerConfig);
                            }
                            mSavedPeerConfig.wps.pin = provDisc.pin;
                            p2pConnectWithPinDisplay(mSavedPeerConfig, P2P_CONNECT_TRIGGER_OTHER);
                            notifyInvitationSent(provDisc.pin, device.deviceAddress);
                            smTransition(this, mGroupNegotiationState);
                        }
                        break;
                    case WifiP2pMonitor.P2P_PROV_DISC_FAILURE_EVENT:
                        loge("provision discovery failed status: " + message.arg1);

                        // Saved peer information is used in handleGroupCreationFailure().
                        if (!handleProvDiscFailure(
                                (WifiP2pProvDiscEvent) message.obj, false)) {
                            break;
                        }

                        mWifiNative.p2pCancelConnect();
                        mWifiP2pMetrics.endConnectionEvent(
                                P2pConnectionEvent.CLF_PROV_DISC_FAIL);
                        handleGroupCreationFailure();
                        smTransition(this, mInactiveState);
                        break;
                    default:
                        return NOT_HANDLED;
                }
                return HANDLED;
            }

            @Override
            public String getMessageLogRec(int what) {
                return P2pStateMachine.class.getSimpleName() + "."
                        + this.getClass().getSimpleName()
                        + "." + getWhatToString(what);
            }
        }

        class GroupNegotiationState extends RunnerState {

            /**
             * The Runner state Constructor
             *
             * @param threshold the running time threshold in milliseconds
             */
            GroupNegotiationState(int threshold, @NonNull LocalLog localLog) {
                super(threshold, localLog);
            }

            @Override
            public void enterImpl() {
                logSmStateName(this.getName(),
                        getCurrentState() != null ? getCurrentState().getName() : "");
            }

            @Override
            public void exitImpl() {

            }

            @Override
            public boolean processMessageImpl(Message message) {
                logSmMessage(getName(), message);
                switch (message.what) {
                    // We ignore these right now, since we get a GROUP_STARTED notification
                    // afterwards
                    case WifiP2pMonitor.P2P_GO_NEGOTIATION_SUCCESS_EVENT:
                    case WifiP2pMonitor.P2P_GROUP_FORMATION_SUCCESS_EVENT:
                        if (mVerboseLoggingEnabled) logd(getName() + " go success");
                        break;
                    case WifiP2pMonitor.P2P_GROUP_STARTED_EVENT:
                        if (message.obj == null) {
                            Log.e(TAG, "Illegal argument(s)");
                            break;
                        }
                        mGroup = (WifiP2pGroup) message.obj;
                        if (mVerboseLoggingEnabled) logd(getName() + " group started");
                        if (mWifiNative.p2pExtListen(false, 0, 0, null)) {
                            sendP2pListenChangedBroadcast(false);
                        }
                        mWifiNative.p2pStopFind();
                        if (mGroup.isGroupOwner()
                                && EMPTY_DEVICE_ADDRESS.equals(mGroup.getOwner().deviceAddress)) {
                            // wpa_supplicant doesn't set own device address to go_dev_addr.
                            mGroup.getOwner().deviceAddress = mThisDevice.deviceAddress;
                        }
                        if (mGroup.getNetworkId() == WifiP2pGroup.NETWORK_ID_PERSISTENT) {
                             // update cache information and set network id to mGroup.
                            updatePersistentNetworks(RELOAD);
                            String devAddr = mGroup.getOwner().deviceAddress;
                            mGroup.setNetworkId(mGroups.getNetworkId(devAddr,
                                    mGroup.getNetworkName()));
                        }

                        if (mGroup.isGroupOwner()) {
                            // Setting an idle time out on GO causes issues with certain scenarios
                            // on clients where it can be off-channel for longer and with the power
                            // save modes used.
                            // TODO: Verify multi-channel scenarios and supplicant behavior are
                            // better before adding a time out in future
                            // Set group idle timeout of 10 sec, to avoid GO beaconing incase of any
                            // failure during 4-way Handshake.
                            if (!mAutonomousGroup) {
                                mWifiNative.setP2pGroupIdle(mGroup.getInterface(),
                                        GROUP_IDLE_TIME_S);
                            }
                            // {@link com.android.server.connectivity.Tethering} listens to
                            // {@link WifiP2pManager#WIFI_P2P_CONNECTION_CHANGED_ACTION}
                            // events and takes over the DHCP server management automatically.
                            // Because tethering service introduces random IP range, P2P could not
                            // hard-coded group owner IP and needs to wait for tethering completion.
                            // As a result, P2P sends a unicast intent to tether service to trigger
                            // the whole flow before entering GroupCreatedState.
                            setWifiP2pInfoOnGroupFormation(null);
                            boolean isSendSuccessful = SdkLevel.isAtLeastU()
                                    ? sendP2pTetherRequestBroadcastPostU()
                                    : sendP2pTetherRequestBroadcastPreU();
                            if (!isSendSuccessful) {
                                loge("Cannot start tethering, remove " + mGroup);
                                mWifiNative.p2pGroupRemove(mGroup.getInterface());
                            }
                            break;
                        }

                        mWifiNative.setP2pGroupIdle(mGroup.getInterface(), GROUP_IDLE_TIME_S);
                        startIpClient(mGroup.getInterface(), getHandler(),
                                mSavedPeerConfig.getGroupClientIpProvisioningMode(),
                                mGroup.p2pClientEapolIpInfo);
                        WifiP2pDevice groupOwner = mGroup.getOwner();
                        if (!EMPTY_DEVICE_ADDRESS.equals(groupOwner.deviceAddress)) {
                            WifiP2pDevice peer = mPeers.get(groupOwner.deviceAddress);
                            if (peer != null) {
                                // update group owner details with peer details found at discovery
                                groupOwner.updateSupplicantDetails(peer);
                            } else {
                                logd("Add group owner into mPeers: " + groupOwner);
                                mPeers.updateSupplicantDetails(groupOwner);
                            }
                            mPeers.updateStatus(groupOwner.deviceAddress,
                                    WifiP2pDevice.CONNECTED);
                            sendPeersChangedBroadcast();
                        } else {
                            // A supplicant bug can lead to reporting an invalid
                            // group owner address (all zeroes) at times. Avoid a
                            // crash, but continue group creation since it is not
                            // essential.
                            logw("Unknown group owner " + groupOwner);
                        }
                        smTransition(this, mGroupCreatedState);
                        break;
                    case TETHER_INTERFACE_STATE_CHANGED:
                        if (mGroup == null) break;
                        if (!mGroup.isGroupOwner()) break;
                        if (TextUtils.isEmpty(mGroup.getInterface())) break;

                        final ArrayList<String> interfaces = (ArrayList<String>) message.obj;
                        if (interfaces == null) break;
                        if (!interfaces.contains(mGroup.getInterface())) break;

                        Log.d(TAG, "tether " + mGroup.getInterface() + " ready");
                        smTransition(this, mGroupCreatedState);
                        break;
                    case WifiP2pMonitor.P2P_GO_NEGOTIATION_FAILURE_EVENT:
                        P2pStatus status = (P2pStatus) message.obj;
                        if (status == P2pStatus.NO_COMMON_CHANNEL) {
                            smTransition(this, mFrequencyConflictState);
                            break;
                        }
                        // continue with group removal handling
                    case WifiP2pMonitor.P2P_GROUP_REMOVED_EVENT:
                        String errorMsg = "P2P group is removed";
                        if (mVerboseLoggingEnabled) logd(getName() + errorMsg);
                        if (mWifiP2pMetrics.isP2pFastConnectionType()) {
                            takeBugReportP2pFailureIfNeeded("Wi-Fi BugReport (P2P "
                                    + mWifiP2pMetrics.getP2pGroupRoleString()
                                    + " negotiation failure)", errorMsg);
                        }
                        mWifiP2pMetrics.endConnectionEvent(
                                P2pConnectionEvent.CLF_GROUP_REMOVED);
                        handleGroupCreationFailure();
                        smTransition(this, mInactiveState);
                        break;
                    case WifiP2pMonitor.P2P_GROUP_FORMATION_FAILURE_EVENT:
                        // A group formation failure is always followed by
                        // a group removed event. Flushing things at group formation
                        // failure causes supplicant issues. Ignore right now.
                        status = (P2pStatus) message.obj;
                        if (status == P2pStatus.NO_COMMON_CHANNEL) {
                            smTransition(this, mFrequencyConflictState);
                            break;
                        }
                        break;
                    case WifiP2pMonitor.P2P_INVITATION_RESULT_EVENT:
                        status = (P2pStatus) message.obj;
                        if (status == P2pStatus.SUCCESS) {
                            // invocation was succeeded.
                            // wait P2P_GROUP_STARTED_EVENT.
                            break;
                        }
                        loge("Invitation result " + status);
                        if (status == P2pStatus.UNKNOWN_P2P_GROUP) {
                            // target device has already removed the credential.
                            // So, remove this credential accordingly.
                            int netId = mSavedPeerConfig.netId;
                            if (netId >= 0) {
                                if (mVerboseLoggingEnabled) {
                                    logd("Remove unknown client from the list");
                                }
                                removeClientFromList(netId, mSavedPeerConfig.deviceAddress, true);
                            }

                            // Reinvocation has failed, try group negotiation
                            mSavedPeerConfig.netId = WifiP2pGroup.NETWORK_ID_PERSISTENT;
                            if (mSavedPeerConfig.wps.setup == WpsInfo.PBC) {
                                p2pConnectWithPinDisplay(mSavedPeerConfig,
                                        P2P_CONNECT_TRIGGER_OTHER);
                            } else {
                                // Non-PBC method needs to exchange PIN by provision discovery.
                                smTransition(this, mProvisionDiscoveryState);
                            }
                        } else if (status == P2pStatus.INFORMATION_IS_CURRENTLY_UNAVAILABLE) {
                            mSavedPeerConfig.netId = WifiP2pGroup.NETWORK_ID_PERSISTENT;
                            // Devices setting persistent_reconnect to 0 in wpa_supplicant
                            // always defer the invocation request and return
                            // "information is currently unavailable" error.
                            // So, try another way to connect for interoperability.
                            if (!mContext.getResources().getBoolean(R.bool
                                    .config_p2pWaitForPeerInviteOnInviteStatusInfoUnavailable)) {
                                mWifiP2pMetrics
                                        .setFallbackToNegotiationOnInviteStatusInfoUnavailable();
                                p2pConnectWithPinDisplay(mSavedPeerConfig,
                                        P2P_CONNECT_TRIGGER_OTHER);
                            } else {
                                mWifiNative.p2pStopFind();
                                if (mWifiNative.p2pExtListen(true,
                                        mContext.getResources().getInteger(
                                                R.integer.config_wifiP2pExtListenPeriodMs),
                                        mContext.getResources().getInteger(
                                                R.integer.config_wifiP2pExtListenIntervalMs),
                                        null)) {
                                    logd(" started listen to receive the invitation Request"
                                            + " frame from Peer device.");
                                    sendP2pListenChangedBroadcast(true);
                                }
                            }
                        } else if (status == P2pStatus.NO_COMMON_CHANNEL) {
                            smTransition(this, mFrequencyConflictState);
                        } else {
                            mWifiP2pMetrics.endConnectionEvent(
                                    P2pConnectionEvent.CLF_INVITATION_FAIL);
                            handleGroupCreationFailure();
                            smTransition(this, mInactiveState);
                        }
                        break;
                    case WifiP2pMonitor.P2P_PROV_DISC_FAILURE_EVENT:
                        loge("Peer rejected the connection request - status: " + message.arg1);
                        mWifiP2pMetrics.endConnectionEvent(
                                P2pConnectionEvent.CLF_GROUP_REMOVED);
                        handleGroupCreationFailure();
                        smTransition(this, mInactiveState);
                        break;
                    case WifiP2pMonitor.AP_STA_CONNECTED_EVENT:
                    case WifiP2pMonitor.AP_STA_DISCONNECTED_EVENT:
                        // Group owner needs to wait for tethering completion before
                        // moving to GroupCreatedState. If native layer reports STA event
                        // earlier, defer it.
                        if (mGroup != null && mGroup.isGroupOwner()) {
                            deferMessage(message);
                            break;
                        }
                        break;
                    case WifiP2pManager.SET_CONNECTION_REQUEST_RESULT:
                        if (!handleSetConnectionResultForInvitationSent(message)) {
                            replyToMessage(message,
                                    WifiP2pManager.SET_CONNECTION_REQUEST_RESULT_FAILED,
                                    WifiP2pManager.ERROR);
                            break;
                        }
                        replyToMessage(message,
                                WifiP2pManager.SET_CONNECTION_REQUEST_RESULT_SUCCEEDED);
                        break;
                    case DISABLE_P2P:
                        mWifiP2pMetrics.endConnectionEvent(P2pConnectionEvent.CLF_GROUP_REMOVED);
                        //remaining p2p disabling works will be handled in its parent states
                    default:
                        return NOT_HANDLED;
                }
                return HANDLED;
            }

            @Override
            public String getMessageLogRec(int what) {
                return P2pStateMachine.class.getSimpleName() + "."
                        + this.getClass().getSimpleName()
                        + "." + getWhatToString(what);
            }
        }

        class FrequencyConflictState extends RunnerState {
            private WifiDialogManager.DialogHandle mFrequencyConflictDialog;
            private AlertDialog mFrequencyConflictDialogPreT;

            /**
             * The Runner state Constructor
             *
             * @param threshold the running time threshold in milliseconds
             */
            FrequencyConflictState(int threshold, @NonNull LocalLog localLog) {
                super(threshold, localLog);
            }

            @Override
            public void enterImpl() {
                logSmStateName(this.getName(),
                        getCurrentState() != null ? getCurrentState().getName() : "");
                notifyFrequencyConflict();
            }

            private void showFrequencyConflictDialogPreT() {
                Resources r = mContext.getResources();
                AlertDialog dialog = mFrameworkFacade.makeAlertDialogBuilder(mContext)
                        .setMessage(r.getString(R.string.wifi_p2p_frequency_conflict_message,
                                getDeviceName(mSavedPeerConfig.deviceAddress)))
                        .setPositiveButton(r.getString(R.string.dlg_ok), (dialog1, which) ->
                                sendMessage(DROP_WIFI_USER_ACCEPT))
                        .setNegativeButton(r.getString(R.string.decline), (dialog2, which) ->
                                sendMessage(DROP_WIFI_USER_REJECT))
                        .setOnCancelListener(arg0 -> sendMessage(DROP_WIFI_USER_REJECT))
                        .create();
                dialog.setCanceledOnTouchOutside(false);

                dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
                dialog.getWindow().addSystemFlags(
                        WindowManager.LayoutParams.SYSTEM_FLAG_SHOW_FOR_ALL_USERS);
                dialog.show();
                mFrequencyConflictDialogPreT = dialog;
            }

            private void showFrequencyConflictDialog() {
                Resources r = mContext.getResources();
                WifiDialogManager.DialogHandle dialog = mWifiInjector.getWifiDialogManager()
                        .createSimpleDialog(
                                null /* title */,
                                r.getString(R.string.wifi_p2p_frequency_conflict_message,
                                        getDeviceName(mSavedPeerConfig.deviceAddress)),
                                r.getString(R.string.dlg_ok),
                                r.getString(R.string.decline),
                                null /* neutralButtonText */,
                                new WifiDialogManager.SimpleDialogCallback() {
                                    @Override
                                    public void onPositiveButtonClicked() {
                                        sendMessage(DROP_WIFI_USER_ACCEPT);
                                    }

                                    @Override
                                    public void onNegativeButtonClicked() {
                                        sendMessage(DROP_WIFI_USER_REJECT);
                                    }

                                    @Override
                                    public void onNeutralButtonClicked() {
                                        // Not used
                                        sendMessage(DROP_WIFI_USER_REJECT);
                                    }

                                    @Override
                                    public void onCancelled() {
                                        sendMessage(DROP_WIFI_USER_REJECT);
                                    }
                                },
                                new WifiThreadRunner(getHandler()));
                mFrequencyConflictDialog = dialog;
                dialog.launchDialog();
            }

            private void notifyFrequencyConflict() {
                logd("Notify frequency conflict");
                if (!SdkLevel.isAtLeastT()) {
                    showFrequencyConflictDialogPreT();
                } else {
                    showFrequencyConflictDialog();
                }
            }

            @Override
            public boolean processMessageImpl(Message message) {
                logSmMessage(getName(), message);
                switch (message.what) {
                    case WifiP2pMonitor.P2P_GO_NEGOTIATION_SUCCESS_EVENT:
                    case WifiP2pMonitor.P2P_GROUP_FORMATION_SUCCESS_EVENT:
                        loge(getName() + "group sucess during freq conflict!");
                        break;
                    case WifiP2pMonitor.P2P_GROUP_STARTED_EVENT:
                        loge(getName() + "group started after freq conflict, handle anyway");
                        deferMessage(message);
                        smTransition(this, mGroupNegotiationState);
                        break;
                    case WifiP2pMonitor.P2P_GO_NEGOTIATION_FAILURE_EVENT:
                    case WifiP2pMonitor.P2P_GROUP_REMOVED_EVENT:
                    case WifiP2pMonitor.P2P_GROUP_FORMATION_FAILURE_EVENT:
                        // Ignore failures since we retry again
                        break;
                    case DROP_WIFI_USER_REJECT:
                        // User rejected dropping wifi in favour of p2p
                        mFrequencyConflictDialog = null;
                        mFrequencyConflictDialogPreT = null;
                        mWifiP2pMetrics.endConnectionEvent(
                                P2pConnectionEvent.CLF_USER_REJECT);
                        handleGroupCreationFailure();
                        smTransition(this, mInactiveState);
                        break;
                    case DROP_WIFI_USER_ACCEPT:
                        mFrequencyConflictDialog = null;
                        mFrequencyConflictDialogPreT = null;
                        // User accepted dropping wifi in favour of p2p
                        sendDisconnectWifiRequest(true);
                        break;
                    case DISCONNECT_WIFI_RESPONSE:
                        // Got a response from ClientModeImpl, retry p2p
                        if (mVerboseLoggingEnabled) {
                            logd(getName() + "Wifi disconnected, retry p2p");
                        }
                        smTransition(this, mInactiveState);
                        p2pReconnect();
                        break;
                    default:
                        return NOT_HANDLED;
                }
                return HANDLED;
            }

            public void exitImpl() {
                if (mFrequencyConflictDialogPreT != null) {
                    mFrequencyConflictDialogPreT.dismiss();
                }
                if (mFrequencyConflictDialog != null) {
                    mFrequencyConflictDialog.dismissDialog();
                }
            }

            @Override
            public String getMessageLogRec(int what) {
                return P2pStateMachine.class.getSimpleName() + "."
                        + this.getClass().getSimpleName()
                        + "." + getWhatToString(what);
            }
        }

        class GroupCreatedState extends RunnerState {

            /**
             * The Runner state Constructor
             *
             * @param threshold the running time threshold in milliseconds
             */
            GroupCreatedState(int threshold, @NonNull LocalLog localLog) {
                super(threshold, localLog);
            }

            @Override
            public void enterImpl() {
                logSmStateName(this.getName(),
                        getCurrentState() != null ? getCurrentState().getName() : "");
                // Once connected, peer config details are invalid
                mSavedPeerConfig.invalidate();
                mDetailedState = NetworkInfo.DetailedState.CONNECTED;

                updateThisDevice(WifiP2pDevice.CONNECTED);

                // DHCP server has already been started if I am a group owner
                if (mGroup.isGroupOwner()) {
                    Inet4Address addr = getInterfaceAddress(mGroup.getInterface());
                    if (addr != null) {
                        setWifiP2pInfoOnGroupFormation(addr.getHostAddress());
                        Log.d(TAG, "Group owner address: " + addr.getHostAddress()
                                + " at " + mGroup.getInterface());
                    } else {
                        mWifiNative.p2pGroupRemove(mGroup.getInterface());
                    }
                }

                // In case of a negotiation group, connection changed is sent
                // after a client joins. For autonomous, send now
                if (mAutonomousGroup) {
                    onGroupCreated(new WifiP2pInfo(mWifiP2pInfo), eraseOwnDeviceAddress(mGroup));
                    sendP2pConnectionChangedBroadcast();
                }

                mWifiP2pMetrics.endConnectionEvent(
                        P2pConnectionEvent.CLF_NONE);
                mWifiP2pMetrics.startGroupEvent(mGroup);
            }

            @Override
            public boolean processMessageImpl(Message message) {
                logSmMessage(getName(), message);
                WifiP2pDevice device;
                String deviceAddress;
                switch (message.what) {
                    case WifiP2pMonitor.AP_STA_CONNECTED_EVENT: {
                        if (message.obj == null) {
                            Log.e(TAG, "Illegal argument(s)");
                            break;
                        }
                        device = (WifiP2pDevice) message.obj;
                        deviceAddress = device.deviceAddress;
                        MacAddress interfaceMacAddress = device.getInterfaceMacAddress();
                        // Clear timeout that was set when group was started.
                        mWifiNative.setP2pGroupIdle(mGroup.getInterface(), 0);
                        if (deviceAddress != null) {
                            if (mPeers.get(deviceAddress) != null) {
                                mGroup.addClient(mPeers.get(deviceAddress));
                            } else {
                                mGroup.addClient(deviceAddress);
                            }
                            mGroup.setClientInterfaceMacAddress(deviceAddress, interfaceMacAddress);
                            if (SdkLevel.isAtLeastV() && device.getIpAddress() != null) {
                                mGroup.setClientIpAddress(interfaceMacAddress,
                                        device.getIpAddress());
                            }
                            mPeers.updateStatus(deviceAddress, WifiP2pDevice.CONNECTED);
                            if (mVerboseLoggingEnabled) logd(getName() + " ap sta connected");
                            // When a peer is connected, flush it.
                            mPeerAuthorizingTimestamp.remove(deviceAddress);
                            sendPeersChangedBroadcast();
                            mWifiP2pMetrics.updateGroupEvent(mGroup);
                        } else {
                            loge("Connect on null device address, ignore");
                        }
                        onPeerClientJoined(new WifiP2pInfo(mWifiP2pInfo),
                                eraseOwnDeviceAddress(mGroup));
                        sendP2pConnectionChangedBroadcast();
                        break;
                    }
                    case WifiP2pMonitor.AP_STA_DISCONNECTED_EVENT: {
                        if (message.obj == null) {
                            Log.e(TAG, "Illegal argument(s)");
                            break;
                        }
                        device = (WifiP2pDevice) message.obj;
                        deviceAddress = device.deviceAddress;
                        if (deviceAddress != null) {
                            mPeers.updateStatus(deviceAddress, WifiP2pDevice.AVAILABLE);
                            if (mGroup.removeClient(deviceAddress)) {
                                if (mVerboseLoggingEnabled) {
                                    logd("Removed client " + deviceAddress);
                                }
                                if (!mAutonomousGroup && mGroup.isClientListEmpty()) {
                                    logd("Client list empty, remove non-persistent p2p group");
                                    mWifiNative.p2pGroupRemove(mGroup.getInterface());
                                    // We end up sending connection changed broadcast
                                    // when this happens at exit()
                                } else {
                                    onPeerClientDisconnected(new WifiP2pInfo(mWifiP2pInfo),
                                            eraseOwnDeviceAddress(mGroup));
                                    // Notify when a client disconnects from group
                                    sendP2pConnectionChangedBroadcast();
                                }
                                mWifiP2pMetrics.updateGroupEvent(mGroup);
                            } else {
                                if (mVerboseLoggingEnabled) {
                                    logd("Failed to remove client " + deviceAddress);
                                }
                                for (WifiP2pDevice c : mGroup.getClientList()) {
                                    if (mVerboseLoggingEnabled) {
                                        logd("client " + c.deviceAddress);
                                    }
                                }
                            }
                            sendPeersChangedBroadcast();
                            if (mVerboseLoggingEnabled) logd(getName() + " ap sta disconnected");
                        } else {
                            loge("Disconnect on unknown device: " + device);
                        }
                        break;
                    }
                    case TETHER_INTERFACE_CLIENTS_CHANGED: {
                        if (mGroup == null) break;
                        if (!mGroup.isGroupOwner()) break;
                        if (TextUtils.isEmpty(mGroup.getInterface())) break;

                        Collection<TetheredClient> connectedClients =
                                (Collection<TetheredClient>) message.obj;
                        if (connectedClients == null) break;
                        for (TetheredClient client : connectedClients) {
                            logd("TETHER_INTERFACE_CLIENTS_CHANGED - client: " + client);
                            if (client.getTetheringType() == TetheringManager.TETHERING_WIFI_P2P) {
                                if (client.getAddresses().size() == 0) {
                                    continue;
                                }
                                TetheredClient.AddressInfo info = client.getAddresses().get(0);
                                MacAddress interfaceMacAddress = client.getMacAddress();
                                LinkAddress linkAddressInfo = info.getAddress();
                                InetAddress ipAddress = linkAddressInfo.getAddress();
                                mGroup.setClientIpAddress(interfaceMacAddress, ipAddress);
                            } else {
                                loge("Received onClientsChanged cb from a non-p2p tether type: "
                                        + client.getTetheringType());
                            }
                        }
                        sendP2pConnectionChangedBroadcast();
                        break;
                    }
                    case IPC_PRE_DHCP_ACTION:
                        mWifiNative.setP2pPowerSave(mGroup.getInterface(), false);
                        try {
                            mIpClient.completedPreDhcpAction();
                        } catch (RemoteException e) {
                            e.rethrowFromSystemServer();
                        }
                        break;
                    case IPC_POST_DHCP_ACTION:
                        mWifiNative.setP2pPowerSave(mGroup.getInterface(), true);
                        break;
                    case IPC_DHCP_RESULTS:
                        mDhcpResultsParcelable = (DhcpResultsParcelable) message.obj;
                        if (mDhcpResultsParcelable == null) {
                            break;
                        }

                        if (mVerboseLoggingEnabled) {
                            logd("mDhcpResultsParcelable: " + mDhcpResultsParcelable);
                        }
                        if (mDhcpResultsParcelable.serverAddress != null) {
                            setWifiP2pInfoOnGroupFormation(mDhcpResultsParcelable.serverAddress);
                        } else {
                            // In case of static IP (IP address received via EAPOL-Key exchange),
                            // the DHCP server address is null. So look for the gateway address.
                            InetAddress addr =
                                    mDhcpResultsParcelable.baseConfiguration.getGateway();
                            if (addr != null) {
                                setWifiP2pInfoOnGroupFormation(addr.getHostAddress());
                            }
                        }
                        try {
                            final String ifname = mGroup.getInterface();
                            if (mDhcpResultsParcelable != null) {
                                mNetdWrapper.addInterfaceToLocalNetwork(
                                        ifname,
                                        mDhcpResultsParcelable.baseConfiguration.getRoutes(ifname));
                            }
                        } catch (Exception e) {
                            loge("Failed to add iface to local network " + e);
                        }
                        onGroupCreated(new WifiP2pInfo(mWifiP2pInfo),
                                eraseOwnDeviceAddress(mGroup));
                        sendP2pConnectionChangedBroadcast();
                        break;
                    case IPC_PROVISIONING_SUCCESS:
                        if (mSavedPeerConfig.getGroupClientIpProvisioningMode()
                                != GROUP_CLIENT_IP_PROVISIONING_MODE_IPV6_LINK_LOCAL) {
                            break;
                        }

                        LinkProperties linkProperties = (LinkProperties) message.obj;
                        if (mVerboseLoggingEnabled) {
                            logd("IP provisioning result " + linkProperties);
                        }
                        try {
                            mNetdWrapper.addInterfaceToLocalNetwork(
                                    mGroup.getInterface(),
                                    linkProperties.getRoutes());
                        } catch (Exception e) {
                            loge("Failed to add iface to local network " + e);
                            mWifiNative.p2pGroupRemove(mGroup.getInterface());
                        }

                        byte[] goInterfaceMacAddress = mGroup.interfaceAddress;
                        if (goInterfaceMacAddress == null) {
                            setWifiP2pInfoOnGroupFormationWithInetAddress(null);
                            onGroupCreated(new WifiP2pInfo(mWifiP2pInfo),
                                    eraseOwnDeviceAddress(mGroup));
                            sendP2pConnectionChangedBroadcast();
                            break;
                        }

                        byte[] goIpv6Address = MacAddress.fromBytes(goInterfaceMacAddress)
                                .getLinkLocalIpv6FromEui48Mac().getAddress();
                        try {
                            InetAddress goIp = Inet6Address.getByAddress(null, goIpv6Address,
                                    NetworkInterface.getByName(mGroup.getInterface()));
                            setWifiP2pInfoOnGroupFormationWithInetAddress(goIp);
                            onGroupCreated(new WifiP2pInfo(mWifiP2pInfo),
                                    eraseOwnDeviceAddress(mGroup));
                            sendP2pConnectionChangedBroadcast();
                        } catch (UnknownHostException | SocketException e) {
                            loge("Unable to retrieve link-local IPv6 address of group owner "
                                    + e);
                            mWifiNative.p2pGroupRemove(mGroup.getInterface());
                        }
                        break;
                    case IPC_PROVISIONING_FAILURE:
                        loge("IP provisioning failed");
                        mWifiNative.p2pGroupRemove(mGroup.getInterface());
                        break;
                    case WifiP2pManager.REMOVE_GROUP:
                        mLastCallerInfoManager.put(WifiManager.API_P2P_REMOVE_GROUP,
                                Process.myTid(), message.sendingUid, 0,
                                getCallingPkgName(message.sendingUid, message.replyTo), true);
                        if (mVerboseLoggingEnabled) logd(getName() + " remove group");
                        if (mWifiNative.p2pGroupRemove(mGroup.getInterface())) {
                            smTransition(this, mOngoingGroupRemovalState);
                            replyToMessage(message, WifiP2pManager.REMOVE_GROUP_SUCCEEDED);
                        } else {
                            handleGroupRemoved();
                            smTransition(this, mInactiveState);
                            replyToMessage(message, WifiP2pManager.REMOVE_GROUP_FAILED,
                                    WifiP2pManager.ERROR);
                        }
                        break;
                    case WifiP2pMonitor.P2P_GROUP_REMOVED_EVENT:
                        // We do not listen to NETWORK_DISCONNECTION_EVENT for group removal
                        // handling since supplicant actually tries to reconnect after a temporary
                        // disconnect until group idle time out. Eventually, a group removal event
                        // will come when group has been removed.
                        //
                        // When there are connectivity issues during temporary disconnect,
                        // the application will also just remove the group.
                        //
                        // Treating network disconnection as group removal causes race conditions
                        // since supplicant would still maintain the group at that stage.
                        if (mVerboseLoggingEnabled) logd(getName() + " group removed");
                        handleGroupRemoved();
                        smTransition(this, mInactiveState);
                        break;
                    case WifiP2pMonitor.P2P_DEVICE_LOST_EVENT:
                        if (message.obj == null) {
                            Log.e(TAG, "Illegal argument(s)");
                            return NOT_HANDLED;
                        }
                        device = (WifiP2pDevice) message.obj;
                        if (!mGroup.contains(device)) {
                            // do the regular device lost handling
                            return NOT_HANDLED;
                        }
                        // Device loss for a connected device indicates
                        // it is not in discovery any more
                        if (mVerboseLoggingEnabled) logd("Add device to lost list " + device);
                        mPeersLostDuringConnection.updateSupplicantDetails(device);
                        return HANDLED;
                    case DISABLE_P2P:
                        sendMessage(WifiP2pManager.REMOVE_GROUP);
                        deferMessage(message);
                        break;
                        // This allows any client to join the GO during the
                        // WPS window
                    case WifiP2pManager.START_WPS:
                        WpsInfo wps = (WpsInfo) message.obj;
                        if (wps == null) {
                            replyToMessage(message, WifiP2pManager.START_WPS_FAILED);
                            break;
                        }
                        boolean ret = true;
                        if (wps.setup == WpsInfo.PBC) {
                            ret = mWifiNative.startWpsPbc(mGroup.getInterface(), null);
                        } else {
                            if (wps.pin == null) {
                                String pin = mWifiNative.startWpsPinDisplay(
                                        mGroup.getInterface(), null);
                                try {
                                    Integer.parseInt(pin);
                                    notifyInvitationSent(pin, "any");
                                } catch (NumberFormatException ignore) {
                                    ret = false;
                                }
                            } else {
                                ret = mWifiNative.startWpsPinKeypad(mGroup.getInterface(),
                                        wps.pin);
                            }
                        }
                        replyToMessage(message, ret ? WifiP2pManager.START_WPS_SUCCEEDED :
                                WifiP2pManager.START_WPS_FAILED);
                        break;
                    case WifiP2pManager.CONNECT: {
                        String packageName = getCallingPkgName(message.sendingUid, message.replyTo);
                        if (packageName == null) {
                            replyToMessage(message, WifiP2pManager.CONNECT_FAILED);
                            break;
                        }
                        int uid = message.sendingUid;
                        Bundle extras = message.getData()
                                .getBundle(WifiP2pManager.EXTRA_PARAM_KEY_BUNDLE);
                        boolean hasPermission = false;
                        if (isPlatformOrTargetSdkLessThanT(packageName, uid)) {
                            hasPermission = mWifiPermissionsUtil.checkCanAccessWifiDirect(
                                    packageName,
                                    getCallingFeatureId(message.sendingUid, message.replyTo),
                                    uid, false);
                        } else {
                            hasPermission = checkNearbyDevicesPermission(uid, packageName,
                                    extras, "CONNECT", message.obj);
                        }
                        if (!hasPermission) {
                            replyToMessage(message, WifiP2pManager.CONNECT_FAILED);
                            // remain at this state.
                            break;
                        }
                        mLastCallerInfoManager.put(WifiManager.API_P2P_CONNECT,
                                Process.myTid(), uid, 0, packageName, true);
                        WifiP2pConfig config = (WifiP2pConfig)
                                extras.getParcelable(WifiP2pManager.EXTRA_PARAM_KEY_CONFIG);
                        if (isConfigInvalid(config)) {
                            loge("Dropping connect request " + config);
                            replyToMessage(message, WifiP2pManager.CONNECT_FAILED);
                            break;
                        }
                        logd("Inviting device : " + config.deviceAddress);
                        mSavedPeerConfig = config;
                        if (mWifiNative.p2pInvite(mGroup, config.deviceAddress)) {
                            mPeers.updateStatus(config.deviceAddress, WifiP2pDevice.INVITED);
                            sendPeersChangedBroadcast();
                            replyToMessage(message, WifiP2pManager.CONNECT_SUCCEEDED);
                        } else {
                            replyToMessage(message, WifiP2pManager.CONNECT_FAILED,
                                    WifiP2pManager.ERROR);
                        }
                        // TODO: figure out updating the status to declined
                        // when invitation is rejected
                        break;
                    }
                    case WifiP2pMonitor.P2P_INVITATION_RESULT_EVENT:
                        P2pStatus status = (P2pStatus) message.obj;
                        if (status == P2pStatus.SUCCESS) {
                            // invocation was succeeded.
                            break;
                        }
                        loge("Invitation result " + status);
                        if (status == P2pStatus.UNKNOWN_P2P_GROUP) {
                            // target device has already removed the credential.
                            // So, remove this credential accordingly.
                            int netId = mGroup.getNetworkId();
                            if (netId >= 0) {
                                if (mVerboseLoggingEnabled) {
                                    logd("Remove unknown client from the list");
                                }
                                removeClientFromList(netId, mSavedPeerConfig.deviceAddress, false);
                                // try invitation.
                                p2pReconnect();
                            }
                        }
                        break;
                    case WifiP2pMonitor.P2P_PROV_DISC_PBC_REQ_EVENT:
                    case WifiP2pMonitor.P2P_PROV_DISC_ENTER_PIN_EVENT:
                    case WifiP2pMonitor.P2P_PROV_DISC_SHOW_PIN_EVENT:
                        WifiP2pProvDiscEvent provDisc = (WifiP2pProvDiscEvent) message.obj;
                        WifiP2pConfig newPeerConfig = new WifiP2pConfig();
                        if (provDisc != null && provDisc.device != null) {
                            if (TextUtils.isEmpty(provDisc.device.deviceAddress)) {
                                break;
                            }
                            newPeerConfig.deviceAddress = provDisc.device.deviceAddress;
                        }
                        if (SdkLevel.isAtLeastV() && provDisc != null
                                && provDisc.getVendorData() != null) {
                            newPeerConfig.setVendorData(provDisc.getVendorData());
                        }
                        if (message.what == WifiP2pMonitor.P2P_PROV_DISC_ENTER_PIN_EVENT) {
                            newPeerConfig.wps.setup = WpsInfo.KEYPAD;
                        } else if (message.what == WifiP2pMonitor.P2P_PROV_DISC_SHOW_PIN_EVENT) {
                            newPeerConfig.wps.setup = WpsInfo.DISPLAY;
                            newPeerConfig.wps.pin = provDisc.pin;
                        } else {
                            newPeerConfig.wps.setup = WpsInfo.PBC;
                        }

                        if (isPeerAuthorizing(newPeerConfig.deviceAddress)) {
                            Log.i(TAG, "Ignore duplicate provision discovery request from "
                                    + newPeerConfig.deviceAddress);
                            break;
                        }
                        mSavedPeerConfig = newPeerConfig;
                        mPeerAuthorizingTimestamp.put(mSavedPeerConfig.deviceAddress,
                                mClock.getElapsedSinceBootMillis());

                        // According to section 3.2.3 in SPEC, only GO can handle group join.
                        // Multiple groups is not supported, ignore this discovery for GC.
                        if (mGroup.isGroupOwner()) {
                            smTransition(this, mUserAuthorizingJoinState);
                        } else {
                            if (mVerboseLoggingEnabled) {
                                logd("Ignore provision discovery for GC");
                            }
                        }
                        break;
                    case WifiP2pMonitor.P2P_GROUP_STARTED_EVENT:
                        loge("Duplicate group creation event notice, ignore");
                        break;
                    case WifiP2pManager.CANCEL_CONNECT:
                        mLastCallerInfoManager.put(WifiManager.API_P2P_CANCEL_CONNECT,
                                Process.myTid(), message.sendingUid, 0,
                                getCallingPkgName(message.sendingUid, message.replyTo), true);
                        mWifiNative.p2pCancelConnect();
                        mWifiP2pMetrics.endConnectionEvent(
                                P2pConnectionEvent.CLF_CANCEL);

                        ArrayList<WifiP2pDevice> invitingPeers = new ArrayList<>();
                        mPeers.getDeviceList().forEach(dev -> {
                            if (dev.status == WifiP2pDevice.INVITED) {
                                invitingPeers.add(dev);
                            }
                        });
                        if (mPeers.remove(new WifiP2pDeviceList(invitingPeers))) {
                            sendPeersChangedBroadcast();
                        }

                        replyToMessage(message, WifiP2pManager.CANCEL_CONNECT_SUCCEEDED);
                        break;
                    case WifiP2pMonitor.P2P_FREQUENCY_CHANGED_EVENT:
                        if (mGroup != null) {
                            mGroup.setFrequency(message.arg1);
                            onFrequencyChanged(new WifiP2pInfo(mWifiP2pInfo),
                                    eraseOwnDeviceAddress(mGroup));
                            sendP2pConnectionChangedBroadcast();
                        }
                        break;
                    case WifiP2pManager.REMOVE_CLIENT: {
                        if (!isFeatureSupported(WifiP2pManager.FEATURE_GROUP_CLIENT_REMOVAL)) {
                            replyToMessage(message, WifiP2pManager.REMOVE_CLIENT_FAILED,
                                    WifiP2pManager.ERROR);
                            break;
                        }
                        if (mVerboseLoggingEnabled) logd(getName() + " remove client");
                        MacAddress peerAddress = (MacAddress) message.obj;

                        if (peerAddress != null
                                && mWifiNative.removeClient(peerAddress.toString())) {
                            replyToMessage(message, WifiP2pManager.REMOVE_CLIENT_SUCCEEDED);
                        } else {
                            replyToMessage(message, WifiP2pManager.REMOVE_CLIENT_FAILED,
                                    WifiP2pManager.ERROR);
                        }
                        break;
                    }
                    case WifiP2pManager.SET_CONNECTION_REQUEST_RESULT:
                        if (!handleSetConnectionResultForInvitationSent(message)) {
                            replyToMessage(message,
                                    WifiP2pManager.SET_CONNECTION_REQUEST_RESULT_FAILED,
                                    WifiP2pManager.ERROR);
                            break;
                        }
                        replyToMessage(message,
                                WifiP2pManager.SET_CONNECTION_REQUEST_RESULT_SUCCEEDED);
                        break;
                    case WifiP2pMonitor.P2P_PROV_DISC_FAILURE_EVENT:
                        loge("provision discovery failed status: " + message.arg1);
                        handleProvDiscFailure((WifiP2pProvDiscEvent) message.obj, true);
                        break;
                    default:
                        return NOT_HANDLED;
                }
                return HANDLED;
            }

            public void exitImpl() {
                // The group is still there and handling incoming request,
                // no need to update P2P connection information.
                if (mGroup != null) return;

                mWifiP2pMetrics.endGroupEvent();
                updateThisDevice(WifiP2pDevice.AVAILABLE);
                resetWifiP2pInfo();
                mDetailedState = NetworkInfo.DetailedState.DISCONNECTED;
                onGroupRemoved();
                sendP2pConnectionChangedBroadcast();
                if (!SdkLevel.isAtLeastU()) {
                    // Ensure tethering service to stop tethering.
                    sendP2pTetherRequestBroadcastPreU();
                }
            }

            @Override
            public String getMessageLogRec(int what) {
                return P2pStateMachine.class.getSimpleName() + "."
                        + this.getClass().getSimpleName()
                        + "." + getWhatToString(what);
            }
        }

        class UserAuthorizingJoinState extends RunnerState {

            /**
             * The Runner state Constructor
             *
             * @param threshold the running time threshold in milliseconds
             */
            UserAuthorizingJoinState(int threshold, @NonNull LocalLog localLog) {
                super(threshold, localLog);
            }

            @Override
            public void enterImpl() {
                logSmStateName(this.getName(),
                        getCurrentState() != null ? getCurrentState().getName() : "");
                notifyInvitationReceived(
                        WifiP2pManager.ExternalApproverRequestListener.REQUEST_TYPE_JOIN);
            }

            @Override
            public boolean processMessageImpl(Message message) {
                logSmMessage(getName(), message);
                switch (message.what) {
                    case WifiP2pMonitor.P2P_PROV_DISC_PBC_REQ_EVENT:
                    case WifiP2pMonitor.P2P_PROV_DISC_ENTER_PIN_EVENT:
                    case WifiP2pMonitor.P2P_PROV_DISC_SHOW_PIN_EVENT:
                        // Ignore more client requests
                        break;
                    case WifiP2pMonitor.P2P_PROV_DISC_FAILURE_EVENT:
                        loge("provision discovery failed status: " + message.arg1);
                        if (!handleProvDiscFailure(
                                (WifiP2pProvDiscEvent) message.obj, true)) {
                            break;
                        }
                        smTransition(this, mGroupCreatedState);
                        break;
                    case PEER_CONNECTION_USER_ACCEPT:
                        // Stop discovery to avoid failure due to channel switch
                        if (mDiscoveryStarted) {
                            mWifiNative.p2pStopFind();
                        }
                        if (mSavedPeerConfig.wps.setup == WpsInfo.PBC) {
                            mWifiNative.startWpsPbc(mGroup.getInterface(), null);
                        } else {
                            mWifiNative.startWpsPinKeypad(mGroup.getInterface(),
                                    mSavedPeerConfig.wps.pin);
                        }
                        smTransition(this, mGroupCreatedState);
                        break;
                    case PEER_CONNECTION_USER_REJECT:
                        if (mVerboseLoggingEnabled) logd("User rejected incoming request");
                        mSavedPeerConfig.invalidate();
                        smTransition(this, mGroupCreatedState);
                        break;
                    case WifiP2pManager.SET_CONNECTION_REQUEST_RESULT:
                        if (!handleSetConnectionResult(message,
                                WifiP2pManager.ExternalApproverRequestListener.REQUEST_TYPE_JOIN)) {
                            replyToMessage(message,
                                    WifiP2pManager.SET_CONNECTION_REQUEST_RESULT_FAILED,
                                    WifiP2pManager.ERROR);
                            break;
                        }
                        replyToMessage(message,
                                WifiP2pManager.SET_CONNECTION_REQUEST_RESULT_SUCCEEDED);
                        break;
                    default:
                        return NOT_HANDLED;
                }
                return HANDLED;
            }

            @Override
            public void exitImpl() {
                if (null != mInvitationDialogHandle) {
                    mInvitationDialogHandle.dismissDialog();
                    mInvitationDialogHandle = null;
                }
                if (null != mLegacyInvitationDialog) {
                    mLegacyInvitationDialog.dismiss();
                    mLegacyInvitationDialog = null;
                }
            }

            @Override
            public String getMessageLogRec(int what) {
                return P2pStateMachine.class.getSimpleName() + "."
                        + this.getClass().getSimpleName()
                        + "." + getWhatToString(what);
            }
        }

        class OngoingGroupRemovalState extends RunnerState {

            /**
             * The Runner state Constructor
             *
             * @param threshold the running time threshold in milliseconds
             */
            OngoingGroupRemovalState(int threshold, @NonNull LocalLog localLog) {
                super(threshold, localLog);
            }

            @Override
            public void enterImpl() {
                logSmStateName(this.getName(),
                        getCurrentState() != null ? getCurrentState().getName() : "");
            }

            @Override
            public void exitImpl() {

            }

            @Override
            public boolean processMessageImpl(Message message) {
                logSmMessage(getName(), message);
                switch (message.what) {
                    // Group removal ongoing. Multiple calls
                    // end up removing persisted network. Do nothing.
                    case WifiP2pManager.REMOVE_GROUP:
                        replyToMessage(message, WifiP2pManager.REMOVE_GROUP_SUCCEEDED);
                        break;
                    // Parent state will transition out of this state
                    // when removal is complete
                    default:
                        return NOT_HANDLED;
                }
                return HANDLED;
            }

            @Override
            public String getMessageLogRec(int what) {
                return P2pStateMachine.class.getSimpleName() + "."
                        + this.getClass().getSimpleName()
                        + "." + getWhatToString(what);
            }
        }

        @Override
        public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
            super.dump(fd, pw, args);
            pw.println("mWifiP2pInfo " + mWifiP2pInfo);
            pw.println("mGroup " + mGroup);
            pw.println("mSavedPeerConfig " + mSavedPeerConfig);
            pw.println("mGroups " + mGroups);
            pw.println();
        }

        public boolean isWifiP2pAvailable() {
            if (mIsP2pDisallowedByAdmin) return false;
            if (mFeatureFlags.d2dWhenInfraStaOff()) {
                return mIsWifiEnabled
                        || (mSettingsConfigStore.get(D2D_ALLOWED_WHEN_INFRA_STA_DISABLED)
                                && mWifiGlobals.isD2dSupportedWhenInfraStaDisabled());
            }
            return mIsWifiEnabled;
        }

        public void checkAndSendP2pStateChangedBroadcast() {
            Log.d(TAG, "Wifi enabled=" + mIsWifiEnabled + ", P2P disallowed by admin="
                    + mIsP2pDisallowedByAdmin + ", D2D allowed when infra sta is disabled="
                    + mSettingsConfigStore.get(D2D_ALLOWED_WHEN_INFRA_STA_DISABLED));
            boolean wifiP2pAvailable = isWifiP2pAvailable();
            if (mLastP2pState != wifiP2pAvailable) {
                mLastP2pState = wifiP2pAvailable;
                sendP2pStateChangedBroadcast(mLastP2pState);
            }
        }

        private void sendP2pStateChangedBroadcast(boolean enabled) {
            onStateChanged(enabled);
            final Intent intent = new Intent(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
            intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
            if (enabled) {
                intent.putExtra(WifiP2pManager.EXTRA_WIFI_STATE,
                        WifiP2pManager.WIFI_P2P_STATE_ENABLED);
            } else {
                intent.putExtra(WifiP2pManager.EXTRA_WIFI_STATE,
                        WifiP2pManager.WIFI_P2P_STATE_DISABLED);
            }
            mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
        }

        private void sendP2pDiscoveryChangedBroadcast(boolean started) {
            if (mDiscoveryStarted == started) return;
            mDiscoveryStarted = started;

            if (mVerboseLoggingEnabled) logd("discovery change broadcast " + started);
            onDiscoveryStateChanged(started);

            final Intent intent = new Intent(WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION);
            intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
            intent.putExtra(WifiP2pManager.EXTRA_DISCOVERY_STATE, started
                    ? WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED :
                    WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED);
            mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
        }

        private void sendP2pListenChangedBroadcast(boolean started) {
            if (mListenStarted == started) return;

            mListenStarted = started;
            if (mVerboseLoggingEnabled) logd("wifi p2p listen change broadcast " + started);
            onListenStateChanged(started);

            final Intent intent = new Intent(WifiP2pManager.ACTION_WIFI_P2P_LISTEN_STATE_CHANGED);
            intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
            intent.putExtra(WifiP2pManager.EXTRA_LISTEN_STATE, started
                    ? WifiP2pManager.WIFI_P2P_LISTEN_STARTED :
                    WifiP2pManager.WIFI_P2P_LISTEN_STOPPED);
            sendBroadcastWithExcludedPermissions(intent, null);
        }

        // TODO(b/193460475): Remove when tooling supports SystemApi to public API.
        /**
         * Use the function to send broadcast to apps that hold included permissions and don't
         * hold excluded permissions.
         * @param intent The Intent to broadcast
         * @param excludedPermissions A list of Strings of permissions the receiver must not have.
         * SdkLevel < T:  Does not support excludedPermissions and sets the value always null.
         * SdkLevel >= T: Combines all excludedPermissions
         */
        @SuppressLint("NewApi")
        private void sendBroadcastWithExcludedPermissions(Intent intent,
                @Nullable String[] excludedPermissions) {
            Context context = mContext.createContextAsUser(UserHandle.ALL, 0);
            boolean isLocationModeEnabled = mWifiPermissionsUtil.isLocationModeEnabled();
            String[] permissions = isLocationModeEnabled ? RECEIVER_PERMISSIONS_FOR_BROADCAST
                    : RECEIVER_PERMISSIONS_FOR_BROADCAST_LOCATION_OFF;
            if (SdkLevel.isAtLeastU()) {
                BroadcastOptions broadcastOptions = mWifiInjector.makeBroadcastOptions();
                broadcastOptions.setRequireAllOfPermissions(permissions);
                broadcastOptions.setRequireNoneOfPermissions(excludedPermissions);
                context.sendBroadcast(intent, null, broadcastOptions.toBundle());
            } else {
                context.sendBroadcastWithMultiplePermissions(intent, permissions);
            }
            if (SdkLevel.isAtLeastT()) {
                // on Android T or later, also send broadcasts to apps that have NEARBY_WIFI_DEVICES
                String[] requiredPermissions = new String[]{
                        android.Manifest.permission.NEARBY_WIFI_DEVICES,
                        android.Manifest.permission.ACCESS_WIFI_STATE
                };
                BroadcastOptions broadcastOptions = mWifiInjector.makeBroadcastOptions();
                broadcastOptions.setRequireAllOfPermissions(requiredPermissions);
                ArrayList<String> excludedPermissionsList = new ArrayList<>();
                if (isLocationModeEnabled) {
                    excludedPermissionsList.add(android.Manifest.permission.ACCESS_FINE_LOCATION);
                }
                if (excludedPermissions != null) {
                    Collections.addAll(excludedPermissionsList, excludedPermissions);
                }
                if (excludedPermissionsList.size() > 0) {
                    broadcastOptions.setRequireNoneOfPermissions(excludedPermissionsList.toArray(
                            new String[0]));
                }
                context.sendBroadcast(intent, null, broadcastOptions.toBundle());
            }
        }

        private void sendThisDeviceChangedBroadcast() {
            WifiP2pDevice p2pDevice = eraseOwnDeviceAddress(mThisDevice);
            onDeviceConfigurationChanged(p2pDevice);

            final Intent intent = new Intent(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
            intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
            intent.putExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE, p2pDevice);
            sendBroadcastWithExcludedPermissions(intent, null);
        }

        private void sendPeersChangedBroadcast() {
            WifiP2pDeviceList p2pDeviceList = new WifiP2pDeviceList(mPeers);
            onPeerListChanged(p2pDeviceList);

            final Intent intent = new Intent(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
            intent.putExtra(WifiP2pManager.EXTRA_P2P_DEVICE_LIST, p2pDeviceList);
            intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
            sendBroadcastWithExcludedPermissions(intent, null);
        }

        private Intent getP2pConnectionChangedIntent() {
            Intent intent = new Intent(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
            intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
            intent.putExtra(WifiP2pManager.EXTRA_WIFI_P2P_INFO, new WifiP2pInfo(mWifiP2pInfo));
            intent.putExtra(WifiP2pManager.EXTRA_NETWORK_INFO, makeNetworkInfo());
            intent.putExtra(WifiP2pManager.EXTRA_WIFI_P2P_GROUP, eraseOwnDeviceAddress(mGroup));
            return intent;
        }

        private void sendP2pConnectionChangedBroadcast() {
            if (mVerboseLoggingEnabled) logd("sending p2p connection changed broadcast");
            Intent intent = getP2pConnectionChangedIntent();
            if (SdkLevel.isAtLeastU()) {
                // First send direct foreground broadcast to Tethering package and system service
                // with same android.permission.MAINLINE_NETWORK_STACK
                sendBroadcastWithMainlineNetworkStackPermissionPostU();
                // Then send the same broadcast to remaining apps without
                // android.permission.MAINLINE_NETWORK_STACK
                sendBroadcastWithExcludedPermissions(intent,
                        RECEIVER_PERMISSIONS_MAINLINE_NETWORK_STACK);
            } else {
                sendBroadcastWithExcludedPermissions(intent, null);
            }
            if (mWifiChannel != null) {
                mWifiChannel.sendMessage(WifiP2pServiceImpl.P2P_CONNECTION_CHANGED,
                        makeNetworkInfo());
            } else {
                loge("sendP2pConnectionChangedBroadcast(): WifiChannel is null");
            }
        }

        private boolean isPlatformOrTargetSdkLessThanT(String packageName, int uid) {
            if (!SdkLevel.isAtLeastT()) {
                return true;
            }
            return mWifiPermissionsUtil.isTargetSdkLessThan(packageName,
                    Build.VERSION_CODES.TIRAMISU, uid);
        }

        private boolean checkNearbyDevicesPermission(Message message, String cmd) {
            if (null == message) return false;
            if (null == message.obj) return false;

            String packageName = getCallingPkgName(message.sendingUid, message.replyTo);
            if (packageName == null) {
                return false;
            }
            int uid = message.sendingUid;
            Bundle extras = message.getData()
                    .getBundle(WifiP2pManager.EXTRA_PARAM_KEY_BUNDLE);
            return checkNearbyDevicesPermission(uid, packageName, extras, cmd, message.obj);
        }

        private boolean checkNearbyDevicesPermission(int uid, String packageName, Bundle extras,
                String message, Object attributionSource) {
            if (extras != null
                    && extras.getBoolean(WifiP2pManager.EXTRA_PARAM_KEY_INTERNAL_MESSAGE)) {
                // bypass permission check for internal call.
                return true;
            }
            try {
                mWifiPermissionsUtil.checkPackage(uid, packageName);
            } catch (SecurityException e) {
                loge("checkPackage failed");
                return false;
            }
            return mWifiPermissionsUtil.checkNearbyDevicesPermission(
                    SdkLevel.isAtLeastS() ? (AttributionSource) attributionSource : null, true,
                    TAG + " " + message);
        }

        private boolean isPackageExisted(String pkgName) {
            PackageManager pm = mContext.getPackageManager();
            try {
                PackageInfo info = pm.getPackageInfo(pkgName, PackageManager.GET_META_DATA);
            } catch (PackageManager.NameNotFoundException e) {
                return false;
            }
            return true;
        }

        private String findTetheringServicePackage() {
            ArrayList<String> possiblePackageNames = new ArrayList<>();
            // AOSP
            possiblePackageNames.add("com.android.networkstack.tethering");
            // mainline release
            possiblePackageNames.add("com.google.android.networkstack.tethering");
            // Android Go
            possiblePackageNames.add("com.android.networkstack.tethering.inprocess");

            for (String pkgName: possiblePackageNames) {
                if (isPackageExisted(pkgName)) {
                    return pkgName;
                }
            }
            Log.w(TAG, "Cannot find tethering service package!");
            return null;
        }

        private boolean sendP2pTetherRequestBroadcastPreU() {
            String[] receiverPermissionsForTetheringRequest = {
                    android.Manifest.permission.TETHER_PRIVILEGED
            };
            return sendP2pTetherRequestBroadcastCommon(receiverPermissionsForTetheringRequest,
                    false, 0);
        }

        private boolean sendP2pTetherRequestBroadcastPostU() {
            return sendP2pTetherRequestBroadcastCommon(RECEIVER_PERMISSIONS_MAINLINE_NETWORK_STACK,
                    true, Intent.FLAG_RECEIVER_FOREGROUND);
        }

        private boolean sendP2pTetherRequestBroadcastCommon(String[] permissions,
                boolean setAdditionalFlags, int flags) {
            String tetheringServicePackage = findTetheringServicePackage();
            if (TextUtils.isEmpty(tetheringServicePackage)) return false;
            Log.i(TAG, "sending p2p tether request broadcast to " + tetheringServicePackage
                    + " with permission " + Arrays.toString(permissions));
            Intent intent = getP2pConnectionChangedIntent();
            if (setAdditionalFlags) {
                intent.addFlags(flags);
            }
            intent.setPackage(tetheringServicePackage);
            Context context = mContext.createContextAsUser(UserHandle.ALL, 0);
            context.sendBroadcastWithMultiplePermissions(intent, permissions);
            return true;
        }

        private void sendBroadcastWithMainlineNetworkStackPermissionPostU() {
            String[] receiverPermissions = RECEIVER_PERMISSIONS_MAINLINE_NETWORK_STACK;
            Intent intent = getP2pConnectionChangedIntent();
            // Adding the flag to allow recipient to run at foreground priority with a shorter
            // timeout interval.
            intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
            Log.i(TAG, "sending p2p connection changed broadcast with permission "
                    + Arrays.toString(receiverPermissions));
            Context context = mContext.createContextAsUser(UserHandle.ALL, 0);
            context.sendBroadcastWithMultiplePermissions(intent, receiverPermissions);
        }

        private void sendP2pPersistentGroupsChangedBroadcast() {
            onPersistentGroupsChanged(new WifiP2pGroupList(mGroups, null));
            if (mVerboseLoggingEnabled) logd("sending p2p persistent groups changed broadcast");
            Intent intent = new Intent(WifiP2pManager.ACTION_WIFI_P2P_PERSISTENT_GROUPS_CHANGED);
            intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
            mContext.sendStickyBroadcastAsUser(intent, UserHandle.ALL);
        }

        private void sendP2pRequestChangedBroadcast(boolean accepted) {
            if (mVerboseLoggingEnabled) logd("sending p2p request changed broadcast");
            Intent intent = new Intent(WifiP2pManager.ACTION_WIFI_P2P_REQUEST_RESPONSE_CHANGED);
            intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT
                    | Intent.FLAG_RECEIVER_REPLACE_PENDING);
            intent.putExtra(WifiP2pManager.EXTRA_REQUEST_RESPONSE, accepted);
            if (accepted) {
                intent.putExtra(WifiP2pManager.EXTRA_REQUEST_CONFIG, mSavedPeerConfig);
            } else {
                intent.putExtra(WifiP2pManager.EXTRA_REQUEST_CONFIG, mSavedRejectedPeerConfig);
            }

            Context context = mContext.createContextAsUser(UserHandle.ALL, 0);
            context.sendBroadcastWithMultiplePermissions(
                    intent, RECEIVER_PERMISSIONS_FOR_BROADCAST);
        }

        private void addRowToDialog(ViewGroup group, int stringId, String value) {
            Resources r = mContext.getResources();
            View row = LayoutInflater.from(mContext).cloneInContext(mContext)
                    .inflate(R.layout.wifi_p2p_dialog_row, group, false);
            ((TextView) row.findViewById(R.id.name)).setText(r.getString(stringId));
            ((TextView) row.findViewById(R.id.value)).setText(value);
            group.addView(row);
        }

        // Legacy dialog behavior to avoid WifiDialogActivity invoking onPause() of pre-T
        // Settings/Apps, which might trigger P2P teardown.
        private void showInvitationSentDialogPreT(@NonNull String deviceName,
                @Nullable String pin) {
            Resources r = mContext.getResources();

            final View textEntryView = LayoutInflater.from(mContext).cloneInContext(mContext)
                    .inflate(R.layout.wifi_p2p_dialog, null);

            ViewGroup group = textEntryView.findViewById(R.id.info);
            addRowToDialog(group, R.string.wifi_p2p_to_message, deviceName);
            addRowToDialog(group, R.string.wifi_p2p_show_pin_message, pin);

            AlertDialog dialog = mFrameworkFacade.makeAlertDialogBuilder(mContext)
                    .setTitle(r.getString(R.string.wifi_p2p_invitation_sent_title))
                    .setView(textEntryView)
                    .setPositiveButton(r.getString(R.string.ok), null)
                    .create();
            dialog.setCanceledOnTouchOutside(false);
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
            dialog.getWindow().addSystemFlags(
                    WindowManager.LayoutParams.SYSTEM_FLAG_SHOW_FOR_ALL_USERS);
            dialog.show();
        }

        private void showInvitationSentDialog(@NonNull String deviceName, @Nullable String pin) {
            int displayId = mDeathDataByBinder.values().stream()
                    .filter(d -> d.mDisplayId != Display.DEFAULT_DISPLAY)
                    .findAny()
                    .map((dhd) -> dhd.mDisplayId)
                    .orElse(Display.DEFAULT_DISPLAY);
            WifiDialogManager.DialogHandle dialogHandle = mWifiInjector.getWifiDialogManager()
                    .createP2pInvitationSentDialog(deviceName, pin, displayId);
            if (dialogHandle == null) {
                loge("Could not create invitation sent dialog!");
                return;
            }
            dialogHandle.launchDialog();
        }

        private void notifyInvitationSent(String pin, String peerAddress) {
            ApproverEntry entry = mExternalApproverManager.get(MacAddress.fromString(peerAddress));
            if (null == entry) {
                logd("No approver found for " + peerAddress
                        + " check the wildcard address approver.");
                entry = mExternalApproverManager.get(MacAddress.BROADCAST_ADDRESS);
            }
            if (null != entry) {
                logd("Received invitation - Send WPS PIN event to the approver " + entry);
                Bundle extras = new Bundle();
                extras.putParcelable(WifiP2pManager.EXTRA_PARAM_KEY_PEER_ADDRESS,
                        entry.getAddress());
                extras.putString(WifiP2pManager.EXTRA_PARAM_KEY_WPS_PIN, pin);
                replyToMessage(entry.getMessage(), WifiP2pManager.EXTERNAL_APPROVER_PIN_GENERATED,
                        extras);
                return;
            }
            String deviceName = getDeviceName(peerAddress);
            if (!SdkLevel.isAtLeastT()) {
                showInvitationSentDialogPreT(deviceName, pin);
            } else {
                showInvitationSentDialog(deviceName, pin);
            }
        }

        // Legacy dialog behavior to avoid WifiDialogActivity invoking onPause() of pre-T
        // Settings/Apps, which might trigger P2P teardown.
        private void showP2pProvDiscShowPinRequestDialogPreT(String deviceName, String pin) {
            Resources r = mContext.getResources();
            final View textEntryView = LayoutInflater.from(mContext).cloneInContext(mContext)
                    .inflate(R.layout.wifi_p2p_dialog, null);

            ViewGroup group = textEntryView.findViewById(R.id.info);
            addRowToDialog(group, R.string.wifi_p2p_to_message, deviceName);
            addRowToDialog(group, R.string.wifi_p2p_show_pin_message, pin);

            AlertDialog dialog = mFrameworkFacade.makeAlertDialogBuilder(mContext)
                    .setTitle(r.getString(R.string.wifi_p2p_invitation_sent_title))
                    .setView(textEntryView)
                    .setPositiveButton(r.getString(R.string.accept),
                            (dialog1, which) -> sendMessage(PEER_CONNECTION_USER_CONFIRM))
                    .create();
            dialog.setCanceledOnTouchOutside(false);
            dialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
            dialog.getWindow().addSystemFlags(
                    WindowManager.LayoutParams.SYSTEM_FLAG_SHOW_FOR_ALL_USERS);
            dialog.show();
        }

        private void showP2pProvDiscShowPinRequestDialog(String deviceName, String pin) {
            int displayId = mDeathDataByBinder.values().stream()
                    .filter(d -> d.mDisplayId != Display.DEFAULT_DISPLAY)
                    .findAny()
                    .map((dhd) -> dhd.mDisplayId)
                    .orElse(Display.DEFAULT_DISPLAY);
            // TODO(b/222115086): This dialog only makes sense for the prov disc receiver.
            //                    Use WifiDialogManager.createP2pInvitationSentDialog(...) for
            //                    the initiator.
            mWifiInjector.getWifiDialogManager().createP2pInvitationReceivedDialog(
                    deviceName,
                    false /* isPinRequested */,
                    pin,
                    displayId,
                    new WifiDialogManager.P2pInvitationReceivedDialogCallback() {
                        @Override
                        public void onAccepted(@Nullable String optionalPin) {
                            sendMessage(PEER_CONNECTION_USER_CONFIRM);
                        }

                        @Override
                        public void onDeclined() {
                            // Do nothing
                            // TODO(b/222115086): Do the correct "decline" behavior.
                        }
                    },
                    new WifiThreadRunner(getHandler())).launchDialog();
        }

        private void notifyP2pProvDiscShowPinRequest(String pin, String peerAddress) {
            ExternalApproverManager.ApproverEntry entry = mExternalApproverManager.get(
                    MacAddress.fromString(peerAddress));
            if (null == entry) {
                logd("No approver found for " + peerAddress
                        + " check the wildcard address approver.");
                entry = mExternalApproverManager.get(MacAddress.BROADCAST_ADDRESS);
            }
            if (null != entry) {
                logd("Received provision discovery request - Send request from "
                        + mSavedPeerConfig.deviceAddress + " to the approver " + entry);
                Bundle extras = new Bundle();
                extras.putParcelable(WifiP2pManager.EXTRA_PARAM_KEY_DEVICE,
                        mPeers.get(mSavedPeerConfig.deviceAddress));
                extras.putParcelable(WifiP2pManager.EXTRA_PARAM_KEY_CONFIG, mSavedPeerConfig);
                replyToMessage(entry.getMessage(),
                        WifiP2pManager.EXTERNAL_APPROVER_CONNECTION_REQUESTED,
                        WifiP2pManager.ExternalApproverRequestListener.REQUEST_TYPE_NEGOTIATION,
                        extras);
                return;
            }
            String deviceName = getDeviceName(peerAddress);
            if (!SdkLevel.isAtLeastT()) {
                showP2pProvDiscShowPinRequestDialogPreT(deviceName, pin);
            } else {
                showP2pProvDiscShowPinRequestDialog(deviceName, pin);
            }
        }

        // Legacy dialog behavior to avoid WifiDialogActivity invoking onPause() of pre-T
        // Settings/Apps, which might trigger P2P teardown.
        private void showInvitationReceivedDialogPreT() {
            Resources r = mContext.getResources();
            final WpsInfo wps = mSavedPeerConfig.wps;
            final View textEntryView = LayoutInflater.from(mContext).cloneInContext(mContext)
                    .inflate(R.layout.wifi_p2p_dialog, null);

            ViewGroup group = textEntryView.findViewById(R.id.info);
            addRowToDialog(group, R.string.wifi_p2p_from_message, getDeviceName(
                    mSavedPeerConfig.deviceAddress));

            final EditText pin = textEntryView.findViewById(R.id.wifi_p2p_wps_pin);

            mLegacyInvitationDialog = mFrameworkFacade.makeAlertDialogBuilder(mContext)
                    .setTitle(r.getString(R.string.wifi_p2p_invitation_to_connect_title))
                    .setView(textEntryView)
                    .setPositiveButton(r.getString(R.string.accept), (dialog1, which) -> {
                        if (wps.setup == WpsInfo.KEYPAD) {
                            mSavedPeerConfig.wps.pin = pin.getText().toString();
                        }
                        if (mVerboseLoggingEnabled) {
                            logd(getName() + " accept invitation " + mSavedPeerConfig);
                        }
                        sendMessage(PEER_CONNECTION_USER_ACCEPT);
                    })
                    .setNegativeButton(r.getString(R.string.decline), (dialog2, which) -> {
                        if (mVerboseLoggingEnabled) logd(getName() + " ignore connect");
                        sendMessage(PEER_CONNECTION_USER_REJECT);
                    })
                    .setOnCancelListener(arg0 -> {
                        if (mVerboseLoggingEnabled) logd(getName() + " ignore connect");
                        sendMessage(PEER_CONNECTION_USER_REJECT);
                    })
                    .create();
            mLegacyInvitationDialog.setCanceledOnTouchOutside(false);

            // make the enter pin area or the display pin area visible
            switch (wps.setup) {
                case WpsInfo.KEYPAD:
                    if (mVerboseLoggingEnabled) logd("Enter pin section visible");
                    textEntryView.findViewById(R.id.enter_pin_section).setVisibility(View.VISIBLE);
                    break;
                case WpsInfo.DISPLAY:
                    if (mVerboseLoggingEnabled) logd("Shown pin section visible");
                    addRowToDialog(group, R.string.wifi_p2p_show_pin_message, wps.pin);
                    break;
                default:
                    break;
            }

            if ((r.getConfiguration().uiMode & Configuration.UI_MODE_TYPE_APPLIANCE)
                    == Configuration.UI_MODE_TYPE_APPLIANCE) {
                mLegacyInvitationDialog.setOnKeyListener((dialog3, keyCode, event) -> {
                    if (keyCode == KeyEvent.KEYCODE_VOLUME_MUTE) {
                        sendMessage(PEER_CONNECTION_USER_ACCEPT);
                        dialog3.dismiss();
                        return true;
                    }
                    return false;
                });
            }

            mLegacyInvitationDialog.getWindow().setType(
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
            mLegacyInvitationDialog.getWindow().addSystemFlags(
                    WindowManager.LayoutParams.SYSTEM_FLAG_SHOW_FOR_ALL_USERS);
            mLegacyInvitationDialog.show();
        }

        private void showInvitationReceivedDialog() {
            String deviceName = getDeviceName(mSavedPeerConfig.deviceAddress);
            boolean isPinRequested = false;
            String displayPin = null;

            int displayId = mDeathDataByBinder.values().stream()
                    .filter(d -> d.mDisplayId != Display.DEFAULT_DISPLAY)
                    .findAny()
                    .map((dhd) -> dhd.mDisplayId)
                    .orElse(Display.DEFAULT_DISPLAY);
            final WpsInfo wps = mSavedPeerConfig.wps;
            switch (wps.setup) {
                case WpsInfo.KEYPAD:
                    isPinRequested = true;
                    break;
                case WpsInfo.DISPLAY:
                    displayPin = wps.pin;
                    break;
                default:
                    break;
            }

            WifiDialogManager.P2pInvitationReceivedDialogCallback callback =
                    new WifiDialogManager.P2pInvitationReceivedDialogCallback() {
                        @Override
                        public void onAccepted(@Nullable String optionalPin) {
                            if (optionalPin != null) {
                                mSavedPeerConfig.wps.pin = optionalPin;
                            }
                            if (mVerboseLoggingEnabled) {
                                logd(getName() + " accept invitation " + mSavedPeerConfig);
                            }
                            sendMessage(PEER_CONNECTION_USER_ACCEPT);
                            mInvitationDialogHandle = null;
                        }

                        @Override
                        public void onDeclined() {
                            if (mVerboseLoggingEnabled) {
                                logd(getName() + " ignore connect");
                            }
                            sendMessage(PEER_CONNECTION_USER_REJECT);
                            mInvitationDialogHandle = null;
                        }
                    };

            mInvitationDialogHandle =
                    mWifiInjector.getWifiDialogManager().createP2pInvitationReceivedDialog(
                            deviceName,
                            isPinRequested,
                            displayPin,
                            displayId,
                            callback,
                            new WifiThreadRunner(getHandler()));
            mInvitationDialogHandle.launchDialog(mContext.getResources().getInteger(
                    R.integer.config_p2pInvitationReceivedDialogTimeoutMs));
        }

        private void notifyInvitationReceived(
                @WifiP2pManager.ExternalApproverRequestListener.RequestType int requestType) {
            ApproverEntry entry = mExternalApproverManager.get(
                    MacAddress.fromString(mSavedPeerConfig.deviceAddress));
            if (null == entry) {
                logd("No approver found for " + mSavedPeerConfig.deviceAddress
                        + " check the wildcard address approver.");
                entry = mExternalApproverManager.get(MacAddress.BROADCAST_ADDRESS);
            }
            if (null != entry) {
                logd("Received Invitation request - Send request " + requestType + " from "
                        + mSavedPeerConfig.deviceAddress + " to the approver " + entry);
                Bundle extras = new Bundle();
                extras.putParcelable(WifiP2pManager.EXTRA_PARAM_KEY_DEVICE,
                        mPeers.get(mSavedPeerConfig.deviceAddress));
                extras.putParcelable(WifiP2pManager.EXTRA_PARAM_KEY_CONFIG, mSavedPeerConfig);
                replyToMessage(entry.getMessage(),
                        WifiP2pManager.EXTERNAL_APPROVER_CONNECTION_REQUESTED,
                        requestType, extras);
                return;
            }
            if (!SdkLevel.isAtLeastT()) {
                showInvitationReceivedDialogPreT();
            } else {
                showInvitationReceivedDialog();
            }
        }

        /**
         * This method unifies the persisent group list, cleans up unused
         * networks and if required, updates corresponding broadcast receivers
         * @param reload if true, reload the group list from scratch
         *                and send broadcast message with fresh list
         */
        private void updatePersistentNetworks(boolean reload) {
            if (reload) mGroups.clear();

            // Save in all cases, including when reload was requested, but
            // no network has been found.
            if (mWifiNative.p2pListNetworks(mGroups) || reload) {
                for (WifiP2pGroup group : mGroups.getGroupList()) {
                    if (group.getOwner() == null) {
                        Log.d(TAG, "group.getOwner() null");
                        continue;
                    }
                    if (Objects.equals(mThisDevice.deviceAddress, group.getOwner().deviceAddress)) {
                        group.setOwner(mThisDevice);
                    }
                }
                mWifiNative.saveConfig();
                mWifiP2pMetrics.updatePersistentGroup(mGroups);
                sendP2pPersistentGroupsChangedBroadcast();
            }
        }

        /**
         * A config is valid if it has a peer address that has already been
         * discovered
         * @param WifiP2pConfig config to be validated
         * @return true if it is invalid, false otherwise
         */
        private boolean isConfigInvalid(WifiP2pConfig config) {
            if (config == null) return true;
            if (TextUtils.isEmpty(config.deviceAddress)) return true;
            return mPeers.get(config.deviceAddress) == null;
        }

        /**
         * Check the network name complies standard SSID naming rules.
         *
         * The network name of a group is also the broadcasting SSID,
         * as a result, the network name must complies standard SSID naming
         * rules.
         */
        private boolean isValidNetworkName(String networkName) {
            if (TextUtils.isEmpty(networkName)) return false;

            byte[] ssidBytes = networkName.getBytes(StandardCharsets.UTF_8);
            if (ssidBytes.length < MIN_NETWORK_NAME_BYTES) return false;
            return ssidBytes.length <= MAX_NETWORK_NAME_BYTES;
        }

        /**
         * A config is valid as a group if it has network name and passphrase.
         * Supplicant can construct a group on the fly for creating a group with specified config
         * or join a group without negotiation and WPS.
         * @param WifiP2pConfig config to be validated
         * @return true if it is valid, false otherwise
         */
        private boolean isConfigValidAsGroup(WifiP2pConfig config) {
            if (config == null) return false;
            if (TextUtils.isEmpty(config.deviceAddress)) return false;
            return isValidNetworkName(config.networkName)
                    && !TextUtils.isEmpty(config.passphrase);
        }

        private WifiP2pDevice fetchCurrentDeviceDetails(WifiP2pConfig config) {
            if (config == null) return null;
            // Fetch & update group capability from supplicant on the device
            int gc = mWifiNative.getGroupCapability(config.deviceAddress);
            // TODO: The supplicant does not provide group capability changes as an event.
            // Having it pushed as an event would avoid polling for this information right
            // before a connection
            mPeers.updateGroupCapability(config.deviceAddress, gc);
            return mPeers.get(config.deviceAddress);
        }

        /**
         * Erase the MAC address of our interface if it is present in a given device, to prevent
         * apps from having access to persistent identifiers.
         *
         * @param device a device possibly having the same physical address as the wlan interface.
         * @return a copy of the input, possibly with the device address erased.
         */
        private WifiP2pDevice eraseOwnDeviceAddress(WifiP2pDevice device) {
            if (device == null) {
                return null;
            }
            WifiP2pDevice result = new WifiP2pDevice(device);
            if (device.deviceAddress != null
                    && mThisDevice.deviceAddress != null
                    && device.deviceAddress.length() > 0
                    && mThisDevice.deviceAddress.equals(device.deviceAddress)) {
                result.deviceAddress = ANONYMIZED_DEVICE_ADDRESS;
            }
            return result;
        }

        /**
         * Erase the MAC address of our interface if it is set as the device address for any of the
         * devices in a group.
         *
         * @param group a p2p group containing p2p devices.
         * @return a copy of the input, with any devices corresponding to our wlan interface having
         *      their device address erased.
         */
        private WifiP2pGroup eraseOwnDeviceAddress(WifiP2pGroup group) {
            if (group == null) {
                return null;
            }

            WifiP2pGroup result = new WifiP2pGroup(group);

            // Create copies of the clients so they're not shared with the original object.
            for (WifiP2pDevice originalDevice : group.getClientList()) {
                result.removeClient(originalDevice);
                result.addClient(eraseOwnDeviceAddress(originalDevice));
            }

            WifiP2pDevice groupOwner = group.getOwner();
            result.setOwner(eraseOwnDeviceAddress(groupOwner));

            return result;
        }

        /**
         * Erase the MAC address of our interface if it is present in a given device, to prevent
         * apps from having access to persistent identifiers. If the requesting party holds the
         * {@link Manifest.permission.LOCAL_MAC_ADDRESS} permission, the address is not erased.
         *
         * @param device a device possibly having the same physical address as the wlan interface.
         * @param uid the user id of the app that requested the information.
         * @return a copy of the input, possibly with the device address erased.
         */
        private WifiP2pDevice maybeEraseOwnDeviceAddress(WifiP2pDevice device, int uid) {
            if (device == null) {
                return null;
            }
            if (mWifiPermissionsUtil.checkLocalMacAddressPermission(uid)) {
                // Calling app holds the LOCAL_MAC_ADDRESS permission, and is allowed to see this
                // device's MAC.
                return new WifiP2pDevice(device);
            }
            if (mVerboseLoggingEnabled) {
                Log.i(TAG, "Uid " + uid + " does not have local mac address permission");
            }
            return eraseOwnDeviceAddress(device);
        }

        /**
         * Erase the MAC address of our interface if it is set as the device address for any of the
         * devices in a group. If the requesting party holds the
         * {@link Manifest.permission.LOCAL_MAC_ADDRESS} permission, the address is not erased.
         *
         * @param group a p2p group containing p2p devices.
         * @param uid the user id of the app that requested the information.
         * @return a copy of the input, with any devices corresponding to our wlan interface having
         *      their device address erased. If the requesting app holds the LOCAL_MAC_ADDRESS
         *      permission, this method returns a copy of the input.
         */
        private WifiP2pGroup maybeEraseOwnDeviceAddress(WifiP2pGroup group, int uid) {
            if (group == null) {
                return null;
            }
            if (mWifiPermissionsUtil.checkLocalMacAddressPermission(uid)) {
                // Calling app holds the LOCAL_MAC_ADDRESS permission, and is allowed to see this
                // device's MAC.
                return new WifiP2pGroup(group);
            }
            if (mVerboseLoggingEnabled) {
                Log.i(TAG, "Uid " + uid + " does not have local mac address permission");
            }
            return eraseOwnDeviceAddress(group);
        }

        /**
         * Erase the MAC address of our interface if it is set as the device address for any of the
         * devices in a list of groups. If the requesting party holds the
         * {@link Manifest.permission.LOCAL_MAC_ADDRESS} permission, the address is not erased.
         *
         * @param groupList a list of p2p groups containing p2p devices.
         * @param uid the user id of the app that requested the information.
         * @return a copy of the input, with any devices corresponding to our wlan interface having
         *      their device address erased. If the requesting app holds the LOCAL_MAC_ADDRESS
         *      permission, this method returns a copy of the input.
         */
        private WifiP2pGroupList maybeEraseOwnDeviceAddress(WifiP2pGroupList groupList, int uid) {
            if (groupList == null) {
                return null;
            }
            WifiP2pGroupList result = new WifiP2pGroupList();
            for (WifiP2pGroup group : groupList.getGroupList()) {
                result.add(maybeEraseOwnDeviceAddress(group, uid));
            }
            return result;
        }

        /**
         * Start a p2p group negotiation and display pin if necessary
         * @param config for the peer
         */
        private void p2pConnectWithPinDisplay(WifiP2pConfig config, int triggerType) {
            if (config == null) {
                Log.e(TAG, "Illegal argument(s)");
                return;
            }
            WifiP2pDevice dev = fetchCurrentDeviceDetails(config);
            if (dev == null) {
                Log.e(TAG, "Invalid device");
                return;
            }
            config.groupOwnerIntent = selectGroupOwnerIntentIfNecessary(config);
            boolean action;
            if (triggerType == P2P_CONNECT_TRIGGER_INVITATION_REQ
                    || config.isJoinExistingGroup()) {
                // The group owner won't report it is a Group Owner always.
                // If this is called from the invitation path, the sender should be in
                // a group, and the target should be a group owner.
                action = JOIN_GROUP;
            } else {
                action = dev.isGroupOwner() ? JOIN_GROUP : FORM_GROUP;
            }

            String pin = mWifiNative.p2pConnect(config, action);
            try {
                Integer.parseInt(pin);
                mSavedPeerConfig.wps.pin = pin;
                notifyInvitationSent(pin, config.deviceAddress);
            } catch (NumberFormatException ignore) {
                // do nothing if p2pConnect did not return a pin
            }
        }

        /**
         * Reinvoke a persistent group.
         *
         * @param config for the peer
         * @return true on success, false on failure
         */
        private boolean reinvokePersistentGroup(WifiP2pConfig config, boolean isInvited) {
            if (config == null) {
                Log.e(TAG, "Illegal argument(s)");
                return false;
            }
            WifiP2pDevice dev = fetchCurrentDeviceDetails(config);
            if (dev == null) {
                Log.e(TAG, "Invalid device");
                return false;
            }
            // The group owner won't report it is a Group Owner always.
            // If this is called from the invitation path, the sender should be in
            // a group, and the target should be a group owner.
            boolean join = dev.isGroupOwner() || isInvited
                    || config.isJoinExistingGroup();
            String ssid = mWifiNative.p2pGetSsid(dev.deviceAddress);
            if (mVerboseLoggingEnabled) logd("target ssid is " + ssid + " join:" + join);

            if (join && dev.isGroupLimit()) {
                if (mVerboseLoggingEnabled) logd("target device reaches group limit.");

                // if the target group has reached the limit,
                // try group formation.
                join = false;
            } else if (join) {
                int netId = mGroups.getNetworkId(dev.deviceAddress, ssid);
                if (netId >= 0) {
                    // Skip WPS and start 4way handshake immediately.
                    return mWifiNative.p2pGroupAdd(netId);
                } else {
                    loge("The Network: " + ssid + " is not found in the persistent group list");
                }
            }

            if (!join && dev.isDeviceLimit()) {
                loge("target device reaches the device limit.");
                return false;
            }

            if (!join && dev.isInvitationCapable()) {
                int netId = WifiP2pGroup.NETWORK_ID_PERSISTENT;
                if (config.netId >= 0) {
                    if (config.deviceAddress.equals(mGroups.getOwnerAddr(config.netId))) {
                        netId = config.netId;
                    }
                } else {
                    netId = mGroups.getNetworkId(dev.deviceAddress);
                }
                if (netId < 0) {
                    netId = getNetworkIdFromClientList(dev.deviceAddress);
                }
                if (mVerboseLoggingEnabled) {
                    logd("netId related with " + dev.deviceAddress + " = " + netId);
                }
                if (netId >= 0) {
                    // Invoke the persistent group.
                    if (mWifiNative.p2pReinvoke(netId, dev.deviceAddress)) {
                        // Save network id. It'll be used when an invitation
                        // result event is received.
                        config.netId = netId;
                        return true;
                    } else {
                        loge("p2pReinvoke() failed, update networks");
                        updatePersistentNetworks(RELOAD);
                        return false;
                    }
                }
            }
            return false;
        }

        /**
         * Return the network id of the group owner profile which has the p2p client with
         * the specified device address in it's client list.
         * If more than one persistent group of the same address is present in its client
         * lists, return the first one.
         *
         * @param deviceAddress p2p device address.
         * @return the network id. if not found, return -1.
         */
        private int getNetworkIdFromClientList(String deviceAddress) {
            if (deviceAddress == null) return -1;

            Collection<WifiP2pGroup> groups = mGroups.getGroupList();
            for (WifiP2pGroup group : groups) {
                int netId = group.getNetworkId();
                String[] p2pClientList = getClientList(netId);
                if (p2pClientList == null) continue;
                for (String client : p2pClientList) {
                    if (deviceAddress.equalsIgnoreCase(client)) {
                        return netId;
                    }
                }
            }
            return -1;
        }

        /**
         * Return p2p client list associated with the specified network id.
         * @param netId network id.
         * @return p2p client list. if not found, return null.
         */
        private String[] getClientList(int netId) {
            String p2pClients = mWifiNative.getP2pClientList(netId);
            if (p2pClients == null) {
                return null;
            }
            return p2pClients.split(" ");
        }

        /**
         * Remove the specified p2p client from the specified profile.
         * @param netId network id of the profile.
         * @param addr p2p client address to be removed.
         * @param isRemovable if true, remove the specified profile if its client
         *             list becomes empty.
         * @return whether removing the specified p2p client is successful or not.
         */
        private boolean removeClientFromList(int netId, String addr, boolean isRemovable) {
            StringBuilder modifiedClientList =  new StringBuilder();
            String[] currentClientList = getClientList(netId);
            boolean isClientRemoved = false;
            if (currentClientList != null) {
                for (String client : currentClientList) {
                    if (!client.equalsIgnoreCase(addr)) {
                        modifiedClientList.append(" ");
                        modifiedClientList.append(client);
                    } else {
                        isClientRemoved = true;
                    }
                }
            }
            if (modifiedClientList.length() == 0 && isRemovable) {
                // the client list is empty. so remove it.
                if (mVerboseLoggingEnabled) logd("Remove unknown network");
                mGroups.remove(netId);
                mWifiP2pMetrics.updatePersistentGroup(mGroups);
                return true;
            }

            if (!isClientRemoved) {
                // specified p2p client is not found. already removed.
                return false;
            }

            if (mVerboseLoggingEnabled) logd("Modified client list: " + modifiedClientList);
            if (modifiedClientList.length() == 0) {
                modifiedClientList.append("\"\"");
            }
            mWifiNative.setP2pClientList(netId, modifiedClientList.toString());
            mWifiNative.saveConfig();
            return true;
        }

        private Inet4Address getInterfaceAddress(String interfaceName) {
            NetworkInterface iface;
            try {
                iface = NetworkInterface.getByName(interfaceName);
            } catch (SocketException ex) {
                Log.w(TAG, "Could not obtain address of network interface "
                        + interfaceName, ex);
                return null;
            }
            if (null == iface) {
                Log.w(TAG, "Could not obtain interface " + interfaceName);
                return null;
            }
            Enumeration<InetAddress> addrs = iface.getInetAddresses();
            while (addrs.hasMoreElements()) {
                InetAddress addr = addrs.nextElement();
                if (addr instanceof Inet4Address) {
                    return (Inet4Address) addr;
                }
            }
            Log.w(TAG, "Could not obtain address of network interface "
                    + interfaceName + " because it had no IPv4 addresses.");
            return null;
        }

        private void setWifiP2pInfoOnGroupFormation(String serverAddress) {
            InetAddress serverInetAddress = serverAddress == null
                    ? null
                    : InetAddresses.parseNumericAddress(serverAddress);
            setWifiP2pInfoOnGroupFormationWithInetAddress(serverInetAddress);
        }

        private void setWifiP2pInfoOnGroupFormationWithInetAddress(InetAddress serverAddress) {
            mWifiP2pInfo.groupFormed = true;
            mWifiP2pInfo.isGroupOwner = mGroup.isGroupOwner();
            mWifiP2pInfo.groupOwnerAddress = serverAddress;
        }

        private void resetWifiP2pInfo() {
            mWifiP2pInfo.groupFormed = false;
            mWifiP2pInfo.isGroupOwner = false;
            mWifiP2pInfo.groupOwnerAddress = null;
        }

        private String getDeviceName(String deviceAddress) {
            WifiP2pDevice d = mPeers.get(deviceAddress);
            if (d != null) {
                return d.deviceName;
            }
            // Treat the address as name if there is no match
            return deviceAddress;
        }

        private String getPersistedDeviceName() {
            String deviceName = mSettingsConfigStore.get(WIFI_P2P_DEVICE_NAME);
            if (!TextUtils.isEmpty(deviceName)) return deviceName;

            // If a default device is already generated and not expired, just return it.
            long expirationTime = mLastDefaultDeviceNameGeneratingTimeMillis
                    + DEFAULT_DEVICE_NAME_LIFE_TIME_MILLIS;
            if (!TextUtils.isEmpty(mDefaultDeviceName)
                    && expirationTime > mClock.getElapsedSinceBootMillis()) {
                logd("Return the persistent device name: " + mDefaultDeviceName);
                return mDefaultDeviceName;
            }

            String prefix = mWifiGlobals.getWifiP2pDeviceNamePrefix();
            if (DEVICE_NAME_PREFIX_LENGTH_MAX < prefix.getBytes(StandardCharsets.UTF_8).length
                    || 0 == prefix.getBytes(StandardCharsets.UTF_8).length) {
                logw("The length of default device name prefix is invalid"
                        + ", fallback to default name.");
                prefix = DEFAULT_DEVICE_NAME_PREFIX;
            }
            // The length of remaining bytes is at least {@link #DEVICE_NAME_POSTFIX_LENGTH_MIN}.
            int remainingBytes =
                    DEVICE_NAME_LENGTH_MAX - prefix.getBytes(StandardCharsets.UTF_8).length;

            int numDigits = mWifiGlobals.getWifiP2pDeviceNamePostfixNumDigits();
            if (numDigits > remainingBytes) {
                logw("The postfix length exceeds the remaining byte number"
                        + ", use the smaller one.");
                numDigits = remainingBytes;
            }

            String postfix;
            if (numDigits >= DEVICE_NAME_POSTFIX_LENGTH_MIN) {
                postfix = StringUtil.generateRandomNumberString(numDigits);
            } else if (!SdkLevel.isAtLeastT()) {
                // We use the 4 digits of the ANDROID_ID to have a friendly
                // default that has low likelihood of collision with a peer
                String id = mFrameworkFacade.getSecureStringSetting(mContext,
                        Settings.Secure.ANDROID_ID);
                postfix = id.substring(0, 4);
            } else {
                postfix = StringUtil.generateRandomString(4);
            }
            mDefaultDeviceName = prefix + postfix;
            mLastDefaultDeviceNameGeneratingTimeMillis = mClock.getElapsedSinceBootMillis();
            logd("the default device name: " + mDefaultDeviceName);
            return mDefaultDeviceName;
        }

        private String generateP2pSsidPostfix(String devName) {
            if (TextUtils.isEmpty(devName)) return "-";

            StringBuilder sb = new StringBuilder("-");
            Charset charset = Charset.forName("UTF-8");
            byte[] rawBytes = devName.getBytes(charset);
            if (rawBytes.length <= GROUP_NAME_POSTFIX_LENGTH_MAX) {
                sb.append(devName);
            } else {
                CharsetDecoder decoder = charset.newDecoder();
                ByteBuffer bb = ByteBuffer.wrap(rawBytes, 0, GROUP_NAME_POSTFIX_LENGTH_MAX);
                CharBuffer cb = CharBuffer.allocate(GROUP_NAME_POSTFIX_LENGTH_MAX);

                // Ignore an incomplete character
                decoder.onMalformedInput(CodingErrorAction.IGNORE);
                decoder.decode(bb, cb, true);
                decoder.flush(cb);
                sb.append(new String(cb.array(), 0, cb.position()));
            }
            Log.i(TAG, "P2P SSID postfix: " + sb
                    + " len=" + sb.toString().length()
                    + " bytes=" + sb.toString().getBytes(charset).length);
            return sb.toString();
        }

        private boolean setAndPersistDeviceName(String devName) {
            if (TextUtils.isEmpty(devName)) return false;
            if (devName.getBytes(Charset.forName("UTF-8")).length > DEVICE_NAME_LENGTH_MAX) {
                return false;
            }

            if (mInterfaceName != null) {
                String postfix = generateP2pSsidPostfix(devName);
                // Order important: postfix is used when a group is formed
                // and the group name will be reported back. If setDeviceName()
                // fails, it won't be a big deal.
                if (!mWifiNative.setP2pSsidPostfix(postfix)) {
                    loge("Failed to set SSID postfix " + postfix);
                    return false;
                }
                if (!mWifiNative.setDeviceName(devName)) {
                    loge("Failed to set device name " + devName);
                    // Try to restore the postfix.
                    mWifiNative.setP2pSsidPostfix(generateP2pSsidPostfix(mThisDevice.deviceName));
                    return false;
                }
            }

            mThisDevice.deviceName = devName;
            mSettingsConfigStore.put(WIFI_P2P_DEVICE_NAME, devName);
            sendThisDeviceChangedBroadcast();
            return true;
        }

        private boolean setWfdInfo(WifiP2pWfdInfo wfdInfo) {
            final boolean enabled = wfdInfo.isEnabled();
            if (!mWifiNative.setWfdEnable(enabled)) {
                loge("Failed to set wfd enable: " + enabled);
                return false;
            }

            if (enabled) {
                if (!mWifiNative.setWfdDeviceInfo(wfdInfo.getDeviceInfoHex())) {
                    loge("Failed to set wfd properties");
                    return false;
                }
                if (!setWfdR2InfoIfNecessary(wfdInfo)) {
                    loge("Failed to set wfd r2 properties");
                    return false;
                }
            }
            mThisDevice.wfdInfo = wfdInfo;
            sendThisDeviceChangedBroadcast();
            return true;
        }

        private boolean setWfdR2InfoIfNecessary(WifiP2pWfdInfo wfdInfo) {
            if (!SdkLevel.isAtLeastS()) return true;
            if (!wfdInfo.isR2Supported()) return true;
            return mWifiNative.setWfdR2DeviceInfo(wfdInfo.getR2DeviceInfoHex());
        }

        private void configureEapolIpAddressAllocationParamsIfEnabled() {
            if (!mContext.getResources()
                    .getBoolean(R.bool.config_wifiP2pGoIpAddressAllocationInEapolFrames)) {
                return;
            }
            try {
                String ipAddressStart = mContext.getResources()
                        .getString(R.string.config_wifiP2pGoEapolIpAddressRangeStart);
                String ipAddressEnd = mContext.getResources()
                        .getString(R.string.config_wifiP2pGoEapolIpAddressRangeEnd);
                if (TextUtils.isEmpty(ipAddressStart) || TextUtils.isEmpty(ipAddressEnd)) {
                    ipAddressStart = GO_EAPOL_IP_RANGE_DEFAULT_START_ADDRESS;
                    ipAddressEnd = GO_EAPOL_IP_RANGE_DEFAULT_END_ADDRESS;
                }
                if (mVerboseLoggingEnabled) {
                    Log.i(TAG, "EAPOL IP addresses range start: " + ipAddressStart
                            + ", End: " + ipAddressEnd);
                }

                // Convert the IP from string format to int
                InetAddress ipAddressGo = InetAddresses
                        .parseNumericAddress(GO_EAPOL_IP_ADDRESS);
                InetAddress subnetMask = InetAddresses.parseNumericAddress(
                        GO_EAPOL_IP_SUBNET_MASK);
                InetAddress ipAddressRangeStart =
                        InetAddresses.parseNumericAddress(ipAddressStart);
                InetAddress ipAddressRangeEnd =
                        InetAddresses.parseNumericAddress(ipAddressEnd);
                if (!mWifiNative.configureEapolIpAddressAllocationParams(
                        inet4AddressToIntHTL((Inet4Address) ipAddressGo),
                        inet4AddressToIntHTL((Inet4Address) subnetMask),
                        inet4AddressToIntHTL((Inet4Address) ipAddressRangeStart),
                        inet4AddressToIntHTL((Inet4Address) ipAddressRangeEnd))) {
                    loge("Failed to configure EAPOL IP address params in the native service");
                }
            } catch (Exception e) {
                loge("Failed to configure EAPOL IP address params " + e);
            }

        }

        private void initializeP2pSettings() {
            mThisDevice.deviceName = getPersistedDeviceName();
            mThisDevice.primaryDeviceType = mContext.getResources().getString(
                    R.string.config_wifi_p2p_device_type);

            mWifiNative.setDeviceName(mThisDevice.deviceName);
            // DIRECT-XY-DEVICENAME (XY is randomly generated)
            mWifiNative.setP2pSsidPostfix("-" + mThisDevice.deviceName);
            mWifiNative.setP2pDeviceType(mThisDevice.primaryDeviceType);
            // Supplicant defaults to using virtual display with display
            // which refers to a remote display. Use physical_display
            mWifiNative.setConfigMethods("virtual_push_button physical_display keypad");

            mThisDevice.deviceAddress = mWifiNative.p2pGetDeviceAddress();
            if (!mWifiGlobals.isP2pMacRandomizationSupported()) {
                mSettingsConfigStore.put(WIFI_P2P_DEVICE_ADDRESS, mThisDevice.deviceAddress);
            }
            updateThisDevice(WifiP2pDevice.AVAILABLE);
            mWifiNative.p2pFlush();
            mWifiNative.p2pServiceFlush();
            mServiceTransactionId = 0;
            mServiceDiscReqId = null;

            if (null != mThisDevice.wfdInfo) {
                setWfdInfo(mThisDevice.wfdInfo);
            }

            updatePersistentNetworks(RELOAD);

            configureEapolIpAddressAllocationParamsIfEnabled();

            enableVerboseLogging(mSettingsConfigStore.get(WIFI_VERBOSE_LOGGING_ENABLED));
            if (mVerboseLoggingEnabled) {
                logd("Initialized p2p settings - DeviceAddress: "
                        + mThisDevice.deviceAddress);
            }
        }

        private void updateThisDevice(int status) {
            mThisDevice.status = status;
            sendThisDeviceChangedBroadcast();
        }

        private boolean handleProvDiscFailure(WifiP2pProvDiscEvent pdEvent,
                boolean invalidateSavedPeer) {
            if (TextUtils.isEmpty(pdEvent.device.deviceAddress)) return false;
            if (!pdEvent.device.deviceAddress.equals(
                    mSavedPeerConfig.deviceAddress)) {
                return false;
            }

            if (null != mInvitationDialogHandle) {
                mInvitationDialogHandle.dismissDialog();
                mInvitationDialogHandle = null;
            }
            if (null != mLegacyInvitationDialog) {
                mLegacyInvitationDialog.dismiss();
                mLegacyInvitationDialog = null;
            }
            if (invalidateSavedPeer) {
                mSavedPeerConfig.invalidate();
            }
            return true;
        }

        private void handleGroupCreationFailure() {
            // A group is formed, but the tethering request is not proceed.
            if (null != mGroup) {
                // Clear any timeout that was set. This is essential for devices
                // that reuse the main p2p interface for a created group.
                mWifiNative.setP2pGroupIdle(mGroup.getInterface(), 0);
                mWifiNative.p2pGroupRemove(mGroup.getInterface());
                mGroup = null;
            }
            resetWifiP2pInfo();
            mDetailedState = NetworkInfo.DetailedState.FAILED;
            onGroupCreationFailed();
            sendP2pConnectionChangedBroadcast();

            // Remove only the peer we failed to connect to so that other devices discovered
            // that have not timed out still remain in list for connection
            boolean peersChanged = mPeers.remove(mPeersLostDuringConnection);
            if (!TextUtils.isEmpty(mSavedPeerConfig.deviceAddress)
                    && mPeers.remove(mSavedPeerConfig.deviceAddress) != null) {
                peersChanged = true;
            }
            if (peersChanged) {
                sendPeersChangedBroadcast();
            }

            mPeersLostDuringConnection.clear();
            mServiceDiscReqId = null;

            Bundle extras = new Bundle();
            extras.putBoolean(WifiP2pManager.EXTRA_PARAM_KEY_INTERNAL_MESSAGE, true);
            Message msg = Message.obtain();
            msg.sendingUid = Process.myUid();
            msg.what = WifiP2pManager.DISCOVER_PEERS;
            msg.getData().putBundle(WifiP2pManager.EXTRA_PARAM_KEY_BUNDLE, extras);
            sendMessage(msg);

            sendDisconnectWifiRequest(false);
        }

        private void handleGroupRemoved() {
            if (mGroup.isGroupOwner()) {
                // {@link com.android.server.connectivity.Tethering} listens to
                // {@link WifiP2pManager#WIFI_P2P_CONNECTION_CHANGED_ACTION}
                // events and takes over the DHCP server management automatically.
            } else {
                if (mVerboseLoggingEnabled) logd("stop IpClient");
                stopIpClient();
                try {
                    mNetdWrapper.removeInterfaceFromLocalNetwork(mGroup.getInterface());
                } catch (IllegalStateException e) {
                    loge("Failed to remove iface from local network " + e);
                }
            }

            try {
                mNetdWrapper.clearInterfaceAddresses(mGroup.getInterface());
            } catch (Exception e) {
                loge("Failed to clear addresses " + e);
            }

            // Clear any timeout that was set. This is essential for devices
            // that reuse the main p2p interface for a created group.
            mWifiNative.setP2pGroupIdle(mGroup.getInterface(), 0);
            mWifiNative.p2pFlush();

            boolean peersChanged = false;
            // Remove only peers part of the group, so that other devices discovered
            // that have not timed out still remain in list for connection
            for (WifiP2pDevice d : mGroup.getClientList()) {
                if (mPeers.remove(d)) peersChanged = true;
            }
            if (mPeers.remove(mGroup.getOwner())) peersChanged = true;
            if (mPeers.remove(mPeersLostDuringConnection)) peersChanged = true;
            if (peersChanged) {
                sendPeersChangedBroadcast();
            }

            mGroup = null;
            mPeersLostDuringConnection.clear();
            mServiceDiscReqId = null;

            sendDisconnectWifiRequest(false);
        }

        private void sendDisconnectWifiRequest(boolean disableWifi) {
            if (null == mWifiChannel) {
                loge("WifiChannel is null, ignore DISCONNECT_WIFI_REQUEST " + disableWifi);
                return;
            }
            if (mTemporarilyDisconnectedWifi == disableWifi) return;

            mWifiChannel.sendMessage(WifiP2pServiceImpl.DISCONNECT_WIFI_REQUEST,
                    disableWifi ? 1 : 0);
            mTemporarilyDisconnectedWifi = disableWifi;
        }

        private void replyToMessage(Message msg, int what) {
            // State machine initiated requests can have replyTo set to null
            // indicating there are no recipients, we ignore those reply actions
            if (msg.replyTo == null) return;
            Message dstMsg = obtainMessage(msg);
            dstMsg.what = what;
            mReplyChannel.replyToMessage(msg, dstMsg);
        }

        private void replyToMessage(Message msg, int what, int arg1) {
            if (msg.replyTo == null) return;
            Message dstMsg = obtainMessage(msg);
            dstMsg.what = what;
            dstMsg.arg1 = arg1;
            mReplyChannel.replyToMessage(msg, dstMsg);
        }

        private void replyToMessage(Message msg, int what, Object obj) {
            if (msg.replyTo == null) return;
            Message dstMsg = obtainMessage(msg);
            dstMsg.what = what;
            dstMsg.obj = obj;
            mReplyChannel.replyToMessage(msg, dstMsg);
        }

        private void replyToMessage(Message msg, int what, int arg1, Object obj) {
            if (msg.replyTo == null) return;
            Message dstMsg = obtainMessage(msg);
            dstMsg.what = what;
            dstMsg.arg1 = arg1;
            dstMsg.obj = obj;
            mReplyChannel.replyToMessage(msg, dstMsg);
        }

        private Message obtainMessage(Message srcMsg) {
            // arg2 on the source message has a hash code that needs to
            // be retained in replies see WifiP2pManager for details
            Message msg = Message.obtain();
            msg.arg2 = srcMsg.arg2;
            return msg;
        }

        @Override
        protected void logd(String s) {
            Log.d(TAG, s, null);
        }

        @Override
        protected void loge(String s) {
            Log.e(TAG, s, null);
        }

        /**
         * Update service discovery request to wpa_supplicant.
         */
        private boolean updateSupplicantServiceRequest() {
            clearSupplicantServiceRequest();
            StringBuffer sb = new StringBuffer();
            for (ClientInfo c: mClientInfoList.values()) {
                WifiP2pServiceRequest req;
                for (int i = 0; i < c.mReqList.size(); i++) {
                    req = c.mReqList.valueAt(i);
                    if (req != null) {
                        sb.append(req.getSupplicantQuery());
                    }
                }
            }
            if (sb.length() == 0) {
                return false;
            }

            mServiceDiscReqId = mWifiNative.p2pServDiscReq("00:00:00:00:00:00", sb.toString());
            return mServiceDiscReqId != null;
        }

        /**
         * Clear service discovery request in wpa_supplicant
         */
        private void clearSupplicantServiceRequest() {
            if (mServiceDiscReqId == null) return;

            mWifiNative.p2pServDiscCancelReq(mServiceDiscReqId);
            mServiceDiscReqId = null;
        }

        private boolean addServiceRequest(Messenger m, WifiP2pServiceRequest req) {
            if (m == null || req == null) {
                Log.e(TAG, "Illegal argument(s)");
                return false;
            }
            // TODO: We could track individual service adds separately and avoid
            // having to do update all service requests on every new request
            clearClientDeadChannels();

            ClientInfo clientInfo = getClientInfo(m, false);
            if (clientInfo == null) {
                return false;
            }

            ++mServiceTransactionId;
            // The Wi-Fi p2p spec says transaction id should be 1 byte and non-zero.
            if (mServiceTransactionId == 256) mServiceTransactionId = 1;
            req.setTransactionId((mServiceTransactionId));
            clientInfo.mReqList.put(mServiceTransactionId, req);
            if (mServiceDiscReqId == null) {
                return true;
            }
            return updateSupplicantServiceRequest();
        }

        private void removeServiceRequest(Messenger m, WifiP2pServiceRequest req) {
            if (m == null || req == null) {
                Log.e(TAG, "Illegal argument(s)");
            }

            ClientInfo clientInfo = getClientInfo(m, false);
            if (clientInfo == null) {
                return;
            }

            // Application does not have transaction id information
            // go through stored requests to remove
            boolean removed = false;
            for (int i = 0; i < clientInfo.mReqList.size(); i++) {
                if (req.equals(clientInfo.mReqList.valueAt(i))) {
                    removed = true;
                    clientInfo.mReqList.removeAt(i);
                    break;
                }
            }

            if (!removed) return;

            if (mServiceDiscReqId == null) {
                return;
            }

            updateSupplicantServiceRequest();
        }

        private void clearServiceRequests(Messenger m) {
            if (m == null) {
                Log.e(TAG, "Illegal argument(s)");
                return;
            }

            ClientInfo clientInfo = getClientInfo(m, false);
            if (clientInfo == null) {
                return;
            }

            if (clientInfo.mReqList.size() == 0) {
                return;
            }

            clientInfo.mReqList.clear();

            if (mServiceDiscReqId == null) {
                return;
            }

            updateSupplicantServiceRequest();
        }

        private boolean addLocalService(Messenger m, WifiP2pServiceInfo servInfo) {
            if (m == null || servInfo == null) {
                Log.e(TAG, "Illegal arguments");
                return false;
            }

            clearClientDeadChannels();

            ClientInfo clientInfo = getClientInfo(m, false);

            if (clientInfo == null) {
                return false;
            }

            if (!clientInfo.mServList.add(servInfo)) {
                return false;
            }

            if (!mWifiNative.p2pServiceAdd(servInfo)) {
                clientInfo.mServList.remove(servInfo);
                return false;
            }

            return true;
        }

        private void removeLocalService(Messenger m, WifiP2pServiceInfo servInfo) {
            if (m == null || servInfo == null) {
                Log.e(TAG, "Illegal arguments");
                return;
            }

            ClientInfo clientInfo = getClientInfo(m, false);
            if (clientInfo == null) {
                return;
            }

            mWifiNative.p2pServiceDel(servInfo);
            clientInfo.mServList.remove(servInfo);
        }

        private void clearLocalServices(Messenger m) {
            if (m == null) {
                Log.e(TAG, "Illegal argument(s)");
                return;
            }

            ClientInfo clientInfo = getClientInfo(m, false);
            if (clientInfo == null) {
                return;
            }

            for (WifiP2pServiceInfo servInfo: clientInfo.mServList) {
                mWifiNative.p2pServiceDel(servInfo);
            }

            clientInfo.mServList.clear();
        }

        private void clearClientInfo(Messenger m) {
            // update wpa_supplicant service info
            clearLocalServices(m);
            clearServiceRequests(m);
            // remove client from client list
            ClientInfo clientInfo = mClientInfoList.remove(m);
            if (clientInfo != null) {
                logd("Client:" + clientInfo.mPackageName + " is removed");
            }
        }

        /**
         * Send the service response to the WifiP2pManager.Channel.
         * @param WifiP2pServiceResponse response to service discovery
         */
        private void sendServiceResponse(WifiP2pServiceResponse resp) {
            if (resp == null) {
                Log.e(TAG, "sendServiceResponse with null response");
                return;
            }
            for (ClientInfo c : mClientInfoList.values()) {
                WifiP2pServiceRequest req = c.mReqList.get(resp.getTransactionId());
                if (req != null) {
                    Message msg = Message.obtain();
                    msg.what = WifiP2pManager.RESPONSE_SERVICE;
                    msg.arg1 = 0;
                    msg.arg2 = 0;
                    msg.obj = resp;
                    if (c.mMessenger == null) {
                        continue;
                    }
                    try {
                        c.mMessenger.send(msg);
                    } catch (RemoteException e) {
                        if (mVerboseLoggingEnabled) logd("detect dead channel");
                        clearClientInfo(c.mMessenger);
                        return;
                    }
                }
            }
        }

        /**
         * We don't get notifications of clients that have gone away.
         * We detect this actively when services are added and throw
         * them away.
         *
         * TODO: This can be done better with full async channels.
         */
        private void clearClientDeadChannels() {
            ArrayList<Messenger> deadClients = new ArrayList<Messenger>();

            for (ClientInfo c : mClientInfoList.values()) {
                Message msg = Message.obtain();
                msg.what = WifiP2pManager.PING;
                msg.arg1 = 0;
                msg.arg2 = 0;
                msg.obj = null;
                if (c.mMessenger == null) {
                    continue;
                }
                try {
                    c.mMessenger.send(msg);
                } catch (RemoteException e) {
                    if (mVerboseLoggingEnabled) logd("detect dead channel");
                    deadClients.add(c.mMessenger);
                }
            }

            for (Messenger m : deadClients) {
                clearClientInfo(m);
            }
        }

        /**
         * Return the specified ClientInfo.
         * @param m Messenger
         * @param createIfNotExist if true and the specified channel info does not exist,
         * create new client info.
         * @return the specified ClientInfo.
         */
        private ClientInfo getClientInfo(Messenger m, boolean createIfNotExist) {
            ClientInfo clientInfo = mClientInfoList.get(m);
            if (clientInfo == null && createIfNotExist) {
                if (mVerboseLoggingEnabled) logd("add a new client");
                clientInfo = new ClientInfo(m);
                mClientInfoList.put(m, clientInfo);
            }

            return clientInfo;
        }

        /**
         * Enforces permissions on the caller who is requesting for P2p Peers
         * @param pkgName Package name of the caller
         * @param featureId Feature in the package of the caller
         * @param uid of the caller
         * @return WifiP2pDeviceList the peer list
         */
        private WifiP2pDeviceList getPeers(String pkgName, @Nullable String featureId, int uid,
                Bundle extras, Object attributionSource) {
            // getPeers() is guaranteed to be invoked after Wifi Service is up
            // This ensures getInstance() will return a non-null object now
            boolean hasPermission = false;
            if (isPlatformOrTargetSdkLessThanT(pkgName, uid)) {
                hasPermission = mWifiPermissionsUtil.checkCanAccessWifiDirect(
                        pkgName, featureId, uid, true);
            } else {
                hasPermission = checkNearbyDevicesPermission(uid, pkgName,
                        extras, "getPeers", attributionSource);
            }
            if (hasPermission) {
                return new WifiP2pDeviceList(mPeers);
            } else {
                return new WifiP2pDeviceList();
            }
        }

        private void setPendingFactoryReset(boolean pending) {
            mSettingsConfigStore.put(WIFI_P2P_PENDING_FACTORY_RESET, pending);
        }

        private boolean isPendingFactoryReset() {
            return mSettingsConfigStore.get(WIFI_P2P_PENDING_FACTORY_RESET);
        }

        /**
         * Enforces permissions on the caller who is requesting factory reset.
         * @param pkg Bundle containing the calling package string.
         * @param uid The caller uid.
         */
        private boolean factoryReset(int uid) {
            String pkgName = mContext.getPackageManager().getNameForUid(uid);

            if (!mWifiPermissionsUtil.checkNetworkSettingsPermission(uid)) return false;

            if (mUserManager.hasUserRestrictionForUser(
                    UserManager.DISALLOW_NETWORK_RESET, UserHandle.getUserHandleForUid(uid))
                    || mUserManager.hasUserRestrictionForUser(
                    UserManager.DISALLOW_CONFIG_WIFI, UserHandle.getUserHandleForUid(uid))) {
                return false;
            }

            Log.i(TAG, "factoryReset uid=" + uid + " pkg=" + pkgName);

            if (mInterfaceName != null) {
                if (mWifiNative.p2pListNetworks(mGroups)) {
                    for (WifiP2pGroup group : mGroups.getGroupList()) {
                        mWifiNative.removeP2pNetwork(group.getNetworkId());
                    }
                }
                // reload will save native config and broadcast changed event.
                updatePersistentNetworks(true);
                setPendingFactoryReset(false);
                Log.i(TAG, "factoryReset: de-init P2P interface.");
                sendMessage(DISABLE_P2P);
            } else {
                setPendingFactoryReset(true);
            }
            return true;
        }

        private boolean updateVendorElements(
                String packageName, ArrayList<ScanResult.InformationElement> vendorElements) {
            if (TextUtils.isEmpty(packageName)) return false;
            if (null == vendorElements || 0 == vendorElements.size()) {
                if (mVerboseLoggingEnabled) logd("Clear vendor elements for " + packageName);
                mVendorElements.remove(packageName);
            } else {
                if (mVerboseLoggingEnabled) logd("Update vendor elements for " + packageName);

                if (vendorElements.stream()
                        .anyMatch(ie -> ie.id != ScanResult.InformationElement.EID_VSA)) {
                    loge("received InformationElement which is not a Vendor Specific IE (VSIE)."
                            + "VSIEs have an ID = 221.");
                    return false;
                }

                mVendorElements.put(packageName, new HashSet<>(vendorElements));

                Set<ScanResult.InformationElement> aggregatedVendorElements = new HashSet<>();
                mVendorElements.forEach((k, v) -> aggregatedVendorElements.addAll(v));
                // The total bytes of an IE is EID (1 byte) + length (1 byte) + payload length.
                int totalBytes = aggregatedVendorElements.stream()
                        .mapToInt(ie -> (2 + ie.bytes.length)).sum();
                if (totalBytes > WifiP2pManager.getP2pMaxAllowedVendorElementsLengthBytes()) {
                    mVendorElements.forEach((k, v) -> {
                        Log.w(TAG, "package=" + k + " VSIE size="
                                + v.stream().mapToInt(ie -> ie.bytes.length).sum());
                    });
                    mVendorElements.remove(packageName);
                    return false;
                }
            }
            return true;
        }

        private boolean p2pFind(int timeout) {
            return p2pFind(
                    WifiP2pManager.WIFI_P2P_SCAN_FULL,
                    WifiP2pManager.WIFI_P2P_SCAN_FREQ_UNSPECIFIED, timeout, null);
        }

        private boolean p2pFind(@WifiP2pManager.WifiP2pScanType int scanType, int freq,
                                int timeout, @Nullable WifiP2pDiscoveryConfig discoveryConfig) {
            if (isFeatureSupported(WifiP2pManager.FEATURE_SET_VENDOR_ELEMENTS)) {
                Set<ScanResult.InformationElement> aggregatedVendorElements = new HashSet<>();
                mVendorElements.forEach((k, v) -> aggregatedVendorElements.addAll(v));
                if (!mWifiNative.setVendorElements(aggregatedVendorElements)) {
                    Log.w(TAG, "cannot set vendor elements to the native service.");
                    // Don't block p2p find or it might affect regular P2P functinalities.
                    mWifiNative.removeVendorElements();
                }
            }
            if (scanType == WifiP2pManager.WIFI_P2P_SCAN_FULL) {
                return mWifiNative.p2pFind(timeout);
            } else if (scanType == WifiP2pManager.WIFI_P2P_SCAN_SOCIAL
                    && freq == WifiP2pManager.WIFI_P2P_SCAN_FREQ_UNSPECIFIED) {
                return mWifiNative.p2pFind(scanType, freq, timeout);
            } else if (scanType == WifiP2pManager.WIFI_P2P_SCAN_SINGLE_FREQ
                    && freq != WifiP2pManager.WIFI_P2P_SCAN_FREQ_UNSPECIFIED) {
                return mWifiNative.p2pFind(scanType, freq, timeout);
            } else if (scanType == WifiP2pManager.WIFI_P2P_SCAN_WITH_CONFIG_PARAMS
                    && discoveryConfig != null) {
                return mWifiNative.p2pFindWithParams(discoveryConfig, timeout);
            }
            return false;
        }

        void p2pReconnect() {
            Bundle extras = new Bundle();
            extras.putParcelable(WifiP2pManager.EXTRA_PARAM_KEY_CONFIG,
                    mSavedPeerConfig);
            extras.putBoolean(
                    WifiP2pManager.EXTRA_PARAM_KEY_INTERNAL_MESSAGE, true);
            final Message msg = obtainMessage(WifiP2pManager.CONNECT);
            msg.getData().putBundle(WifiP2pManager.EXTRA_PARAM_KEY_BUNDLE, extras);
            msg.sendingUid = Process.myUid();
            sendMessage(msg);
        }

        /**
        * Get calling package string from Client HashMap
        *
        * @param uid The uid of the caller package
        * @param replyMessenger AsyncChannel handler in caller
        */
        private String getCallingPkgName(int uid, Messenger replyMessenger) {
            ClientInfo clientInfo = mClientInfoList.get(replyMessenger);
            if (clientInfo != null) {
                return clientInfo.mPackageName;
            }
            if (UserHandle.getAppId(uid) == Process.SYSTEM_UID) return mContext.getOpPackageName();
            return null;
        }

        /**
         * Get calling feature id from Client HashMap
         *
         * @param uid The uid of the caller
         * @param replyMessenger AsyncChannel handler in caller
         */
        private String getCallingFeatureId(int uid, Messenger replyMessenger) {
            ClientInfo clientInfo = mClientInfoList.get(replyMessenger);
            if (clientInfo != null) {
                return clientInfo.mFeatureId;
            }
            if (UserHandle.getAppId(uid) == Process.SYSTEM_UID) return mContext.getAttributionTag();
            return null;
        }

        /**
         * Clear all of p2p local service request/response for all p2p clients
         */
        private void clearServicesForAllClients() {
            for (ClientInfo c : mClientInfoList.values()) {
                clearLocalServices(c.mMessenger);
                clearServiceRequests(c.mMessenger);
            }
        }

        private int selectGroupOwnerIntentIfNecessary(WifiP2pConfig config) {
            int intent = config.groupOwnerIntent;
            // return the legacy default value for invalid values.
            if (intent != WifiP2pConfig.GROUP_OWNER_INTENT_AUTO) {
                if (intent < WifiP2pConfig.GROUP_OWNER_INTENT_MIN
                        || intent > WifiP2pConfig.GROUP_OWNER_INTENT_MAX) {
                    intent = DEFAULT_GROUP_OWNER_INTENT;
                }
                return intent;
            }

            WifiManager wifiManager = mContext.getSystemService(WifiManager.class);

            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            Log.d(TAG, "WifiInfo: " + wifiInfo);
            int freq = wifiInfo.getFrequency();
            /*
             * GO intent table
             * STA Freq         2.4GHz/5GHz DBS 5GHz/6GHz DBS   GO intent
             * N/A              X               X               6 (default)
             * 2.4 GHz          X               X               7
             * 5 GHz            X               No              8
             * 5 GHz            X               Yes             9
             * 6 GHz            X               No              11
             * 6 Ghz            X               Yes             12
             */
            if (wifiInfo.getNetworkId() == WifiConfiguration.INVALID_NETWORK_ID) {
                intent = DEFAULT_GROUP_OWNER_INTENT;
            } else if (ScanResult.is24GHz(freq)) {
                intent = 7;
            } else if (ScanResult.is5GHz(freq)) {
                if (mWifiNative.is5g6gDbsSupported()) {
                    intent = 9;
                } else {
                    intent = 8;
                }
            } else if (ScanResult.is6GHz(freq)) {
                if (mWifiNative.is5g6gDbsSupported()) {
                    intent = 12;
                } else {
                    intent = 11;
                }
            } else {
                intent = DEFAULT_GROUP_OWNER_INTENT;
            }
            Log.i(TAG, "change GO intent value from "
                    + config.groupOwnerIntent + " to " + intent);
            return intent;
        }

        private boolean updateP2pChannels() {
            Log.d(TAG, "Set P2P listen channel to " + mUserListenChannel);
            if (!mWifiNative.p2pSetListenChannel(mUserListenChannel)) {
                Log.e(TAG, "Cannot set listen channel.");
                return false;
            }

            Log.d(TAG, "Set P2P operating channel to " + mUserOperatingChannel
                    + ", unsafe channels: "
                    + mCoexUnsafeChannels.stream()
                            .map(Object::toString).collect(Collectors.joining(",")));
            if (!mWifiNative.p2pSetOperatingChannel(mUserOperatingChannel, mCoexUnsafeChannels)) {
                Log.e(TAG, "Cannot set operate channel.");
                return false;
            }
            return true;
        }

        private boolean checkExternalApproverCaller(Message message,
                IBinder binder, MacAddress devAddr, String cmd) {
            Bundle extras = message.getData()
                    .getBundle(WifiP2pManager.EXTRA_PARAM_KEY_BUNDLE);
            if (!mWifiPermissionsUtil.checkManageWifiNetworkSelectionPermission(
                    message.sendingUid)) {
                loge("Permission violation - no MANAGE_WIFI_NETWORK_SELECTION,"
                        + " permission, uid = " + message.sendingUid);
                return false;
            }
            if (!checkNearbyDevicesPermission(message, cmd)) {
                loge("Permission violation - no NEARBY_WIFI_DEVICES permission"
                        + ", uid = " + message.sendingUid);
                return false;
            }
            if (null == binder) {
                loge("No valid binder for this approver.");
                return false;
            }
            if (null == devAddr) {
                loge("No device address for this approver.");
                return false;
            }
            return true;
        }

        private void detachExternalApproverFromClient(IBinder binder) {
            if (null == binder) return;

            logd("Detach approvers for " + binder);
            List<ApproverEntry> entries = mExternalApproverManager.get(binder);
            entries.forEach(e -> {
                logd("Detach the approver " + e);
                replyToMessage(
                        e.getMessage(), WifiP2pManager.EXTERNAL_APPROVER_DETACH,
                        ExternalApproverRequestListener.APPROVER_DETACH_REASON_CLOSE,
                        e.getAddress());
            });
            mExternalApproverManager.removeAll(binder);
        }

        private void detachExternalApproverFromPeer() {
            if (TextUtils.isEmpty(mSavedPeerConfig.deviceAddress)) return;

            ApproverEntry entry = mExternalApproverManager.remove(
                    MacAddress.fromString(mSavedPeerConfig.deviceAddress));
            if (null == entry) {
                logd("No approver found for " + mSavedPeerConfig.deviceAddress
                        + " check the wildcard address approver.");
                entry = mExternalApproverManager.remove(MacAddress.BROADCAST_ADDRESS);
            }
            if (null == entry) return;

            logd("Detach the approver " + entry);
            replyToMessage(entry.getMessage(), WifiP2pManager.EXTERNAL_APPROVER_DETACH,
                    ExternalApproverRequestListener.APPROVER_DETACH_REASON_REMOVE,
                    entry.getAddress());
        }

        private boolean handleSetConnectionResultCommon(@NonNull Message message) {
            Bundle extras = message.getData().getBundle(WifiP2pManager.EXTRA_PARAM_KEY_BUNDLE);
            MacAddress devAddr = extras.getParcelable(
                    WifiP2pManager.EXTRA_PARAM_KEY_PEER_ADDRESS);
            IBinder binder = extras.getBinder(WifiP2pManager.CALLING_BINDER);
            if (!checkExternalApproverCaller(message, binder, devAddr,
                    "SET_CONNECTION_REQUEST_RESULT")) {
                return false;
            }

            if (TextUtils.isEmpty(mSavedPeerConfig.deviceAddress)) {
                logd("Saved peer address is empty");
                return false;
            }

            if (!devAddr.equals(MacAddress.fromString(mSavedPeerConfig.deviceAddress))) {
                logd("Saved peer address is different from " + devAddr);
                return false;
            }

            ApproverEntry entry = mExternalApproverManager.get(binder, devAddr);
            if (null == entry) {
                logd("No approver found for " + devAddr
                        + " check the wildcard address approver.");
                entry = mExternalApproverManager.get(binder, MacAddress.BROADCAST_ADDRESS);
            }
            if (null == entry) return false;
            if (!entry.getKey().equals(binder)) {
                loge("Ignore connection result from a client"
                        + " which is different from the existing approver.");
                return false;
            }
            return true;
        }

        private boolean handleSetConnectionResult(@NonNull Message message,
                @WifiP2pManager.ExternalApproverRequestListener.RequestType int requestType) {
            if (!handleSetConnectionResultCommon(message)) return false;

            logd("handle connection result from the approver, result= " + message.arg1);
            // For deferring result, the approver should be removed first to avoid notifying
            // the application again.
            if (WifiP2pManager.CONNECTION_REQUEST_DEFER_TO_SERVICE == message.arg1) {
                detachExternalApproverFromPeer();
                notifyInvitationReceived(requestType);
                return true;
            } else if (WifiP2pManager.CONNECTION_REQUEST_DEFER_SHOW_PIN_TO_SERVICE
                            == message.arg1
                    && WifiP2pManager.ExternalApproverRequestListener.REQUEST_TYPE_NEGOTIATION
                            == requestType
                    && WpsInfo.KEYPAD == mSavedPeerConfig.wps.setup) {
                detachExternalApproverFromPeer();
                notifyP2pProvDiscShowPinRequest(mSavedPeerConfig.wps.pin,
                        mSavedPeerConfig.deviceAddress);
                return true;
            }

            if (WifiP2pManager.CONNECTION_REQUEST_ACCEPT == message.arg1) {
                if (WifiP2pManager.ExternalApproverRequestListener.REQUEST_TYPE_NEGOTIATION
                        == requestType
                        && WpsInfo.KEYPAD == mSavedPeerConfig.wps.setup) {
                    sendMessage(PEER_CONNECTION_USER_CONFIRM);
                } else {
                    Bundle extras = message.getData().getBundle(
                            WifiP2pManager.EXTRA_PARAM_KEY_BUNDLE);
                    String pin = extras.getString(
                            WifiP2pManager.EXTRA_PARAM_KEY_WPS_PIN);
                    if (!TextUtils.isEmpty(pin)) {
                        mSavedPeerConfig.wps.pin = pin;
                    }
                    sendMessage(PEER_CONNECTION_USER_ACCEPT);
                }
            } else if (WifiP2pManager.CONNECTION_REQUEST_REJECT == message.arg1) {
                sendMessage(PEER_CONNECTION_USER_REJECT);
            } else {
                Log.w(TAG, "Invalid connection result: " + message.arg1
                        + ", config: " + mSavedPeerConfig);
                return false;
            }
            detachExternalApproverFromPeer();
            return true;
        }

        private boolean handleSetConnectionResultForInvitationSent(@NonNull Message message) {
            if (!handleSetConnectionResultCommon(message)) return false;

            logd("handle connection result for pin from the approver, result= " + message.arg1);
            // For deferring result, the approver should be removed first to avoid notifying
            // the application again.
            if (WifiP2pManager.CONNECTION_REQUEST_DEFER_SHOW_PIN_TO_SERVICE == message.arg1) {
                detachExternalApproverFromPeer();
                notifyInvitationSent(mSavedPeerConfig.wps.pin,
                        mSavedPeerConfig.deviceAddress);
                return true;
            }
            Log.w(TAG, "Invalid connection result: " + message.arg1);
            return false;
        }

        private boolean isFeatureSupported(long feature) {
            return (getSupportedFeatures() & feature) == feature;
        }

        private int sendP2pRejection() {
            if (TextUtils.isEmpty(mSavedPeerConfig.deviceAddress)) {
                return 0;
            }
            mWifiNative.p2pReject(mSavedPeerConfig.deviceAddress);
            // p2pReject() only updates the peer state, but not sends this
            // to the peer, trigger provision discovery to notify the peer.
            // Adding the delay to send pb request with failed status attr.
            mWifiNative.p2pProvisionDiscovery(mSavedPeerConfig);
            return P2P_REJECTION_WAIT_TIME_MS;
        }

        private boolean isPeerAuthorizing(String deviceAddress) {
            Long timestamp = mPeerAuthorizingTimestamp.get(deviceAddress);
            if (null == timestamp) return false;

            int timeoutMs = mContext.getResources().getInteger(
                    R.integer.config_wifiP2pJoinRequestAuthorizingTimeoutMs);
            if (mClock.getElapsedSinceBootMillis() > timestamp + timeoutMs) {
                return false;
            }

            return true;
        }
    }

    /**
     * Information about a particular client and we track the service discovery requests
     * and the local services registered by the client.
     */
    private static class ClientInfo {

        // A reference to WifiP2pManager.Channel handler.
        // The response of this request is notified to WifiP2pManager.Channel handler
        private final Messenger mMessenger;
        private String mPackageName;
        @Nullable private String mFeatureId;

        // A service discovery request list.
        private final SparseArray<WifiP2pServiceRequest> mReqList = new SparseArray<>();

        // A local service information list.
        private final List<WifiP2pServiceInfo> mServList = new ArrayList<>();

        private ClientInfo(Messenger m) {
            mMessenger = m;
            mPackageName = null;
            mFeatureId = null;
        }
    }

    /**
     * Check that the UID has one of the following permissions:
     * {@link android.Manifest.permission.NETWORK_SETTINGS}
     * {@link android.Manifest.permission.NETWORK_STACK}
     * {@link android.Manifest.permission.OVERRIDE_WIFI_CONFIG}
     *
     * @param uid the UID to check
     * @return whether the UID has any of the above permissions
     */
    private boolean checkNetworkSettingsOrNetworkStackOrOverrideWifiConfigPermission(int uid) {
        return mWifiPermissionsUtil.checkNetworkSettingsPermission(uid)
                || mWifiPermissionsUtil.checkNetworkStackPermission(uid)
                || mWifiPermissionsUtil.checkConfigOverridePermission(uid);
    }

    /**
     * Check that the UID has one of the following permissions:
     * {@link android.Manifest.permission.NETWORK_SETTINGS}
     * {@link android.Manifest.permission.NETWORK_STACK}
     * {@link android.Manifest.permission.READ_WIFI_CREDENTIAL}
     *
     * @param uid the UID to check
     * @return whether the UID has any of the above permissions
     */
    private boolean checkNetworkSettingsOrNetworkStackOrReadWifiCredentialPermission(int uid) {
        return mWifiPermissionsUtil.checkNetworkSettingsPermission(uid)
                || mWifiPermissionsUtil.checkNetworkStackPermission(uid)
                || mWifiPermissionsUtil.checkReadWifiCredentialPermission(uid);
    }
}
