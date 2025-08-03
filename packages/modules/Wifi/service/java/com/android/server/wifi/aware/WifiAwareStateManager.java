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

package com.android.server.wifi.aware;

import static android.Manifest.permission.ACCESS_WIFI_STATE;
import static android.net.wifi.WifiAvailableChannel.OP_MODE_WIFI_AWARE;
import static android.net.wifi.aware.Characteristics.WIFI_AWARE_CIPHER_SUITE_NCS_PK_PASN_128;
import static android.net.wifi.aware.WifiAwareManager.WIFI_AWARE_RESUME_INTERNAL_ERROR;
import static android.net.wifi.aware.WifiAwareManager.WIFI_AWARE_RESUME_INVALID_SESSION;
import static android.net.wifi.aware.WifiAwareManager.WIFI_AWARE_RESUME_REDUNDANT_REQUEST;
import static android.net.wifi.aware.WifiAwareManager.WIFI_AWARE_SUSPEND_CANNOT_SUSPEND;
import static android.net.wifi.aware.WifiAwareManager.WIFI_AWARE_SUSPEND_INTERNAL_ERROR;
import static android.net.wifi.aware.WifiAwareManager.WIFI_AWARE_SUSPEND_INVALID_SESSION;
import static android.net.wifi.aware.WifiAwareManager.WIFI_AWARE_SUSPEND_REDUNDANT_REQUEST;

import static com.android.server.wifi.aware.WifiAwareMetrics.convertNanStatusCodeToWifiStatsLogEnum;
import static com.android.server.wifi.hal.WifiNanIface.NanStatusCode.NOT_SUPPORTED;
import static com.android.server.wifi.hal.WifiNanIface.NanStatusCode.NO_CONNECTION;
import static com.android.server.wifi.hal.WifiNanIface.NanStatusCode.REDUNDANT_REQUEST;
import static com.android.server.wifi.proto.WifiStatsLog.WIFI_AWARE_CAPABILITIES;
import static com.android.server.wifi.proto.WifiStatsLog.WIFI_AWARE_HAL_API_CALLED;
import static com.android.server.wifi.proto.WifiStatsLog.WIFI_AWARE_HAL_API_CALLED__COMMAND__AWARE_API_UNKNOWN;
import static com.android.server.wifi.proto.WifiStatsLog.WIFI_AWARE_HAL_API_CALLED__COMMAND__AWARE_CONFIG_REQUEST;
import static com.android.server.wifi.proto.WifiStatsLog.WIFI_AWARE_HAL_API_CALLED__COMMAND__AWARE_CREATE_DATA_INTERFACE_REQUEST;
import static com.android.server.wifi.proto.WifiStatsLog.WIFI_AWARE_HAL_API_CALLED__COMMAND__AWARE_DELETE_DATA_INTERFACE_REQUEST;
import static com.android.server.wifi.proto.WifiStatsLog.WIFI_AWARE_HAL_API_CALLED__COMMAND__AWARE_DISABLE_REQUEST;
import static com.android.server.wifi.proto.WifiStatsLog.WIFI_AWARE_HAL_API_CALLED__COMMAND__AWARE_ENABLE_REQUEST;
import static com.android.server.wifi.proto.WifiStatsLog.WIFI_AWARE_HAL_API_CALLED__COMMAND__AWARE_GET_CAPABILITIES_REQUEST;
import static com.android.server.wifi.proto.WifiStatsLog.WIFI_AWARE_HAL_API_CALLED__COMMAND__AWARE_INITIATE_BOOTSTRAPPING_REQUEST;
import static com.android.server.wifi.proto.WifiStatsLog.WIFI_AWARE_HAL_API_CALLED__COMMAND__AWARE_INITIATE_DATA_PATH_REQUEST;
import static com.android.server.wifi.proto.WifiStatsLog.WIFI_AWARE_HAL_API_CALLED__COMMAND__AWARE_INITIATE_PAIRING_REQUEST;
import static com.android.server.wifi.proto.WifiStatsLog.WIFI_AWARE_HAL_API_CALLED__COMMAND__AWARE_RESPOND_TO_BOOTSTRAPPING_INDICATION_REQUEST;
import static com.android.server.wifi.proto.WifiStatsLog.WIFI_AWARE_HAL_API_CALLED__COMMAND__AWARE_RESPOND_TO_DATA_PATH_INDICATION_REQUEST;
import static com.android.server.wifi.proto.WifiStatsLog.WIFI_AWARE_HAL_API_CALLED__COMMAND__AWARE_RESPOND_TO_PAIRING_INDICATION_REQUEST;
import static com.android.server.wifi.proto.WifiStatsLog.WIFI_AWARE_HAL_API_CALLED__COMMAND__AWARE_RESUME_REQUEST;
import static com.android.server.wifi.proto.WifiStatsLog.WIFI_AWARE_HAL_API_CALLED__COMMAND__AWARE_START_PUBLISH_REQUEST;
import static com.android.server.wifi.proto.WifiStatsLog.WIFI_AWARE_HAL_API_CALLED__COMMAND__AWARE_START_SUBSCRIBE_REQUEST;
import static com.android.server.wifi.proto.WifiStatsLog.WIFI_AWARE_HAL_API_CALLED__COMMAND__AWARE_SUSPEND_REQUEST;
import static com.android.server.wifi.proto.WifiStatsLog.WIFI_AWARE_HAL_API_CALLED__COMMAND__AWARE_TERMINATE_DATA_PATH_REQUEST;
import static com.android.server.wifi.proto.WifiStatsLog.WIFI_AWARE_HAL_API_CALLED__COMMAND__AWARE_TERMINATE_PAIRING_REQUEST;
import static com.android.server.wifi.proto.WifiStatsLog.WIFI_AWARE_HAL_API_CALLED__COMMAND__AWARE_TRANSMIT_FOLLOW_UP_REQUEST;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.StatsManager;
import android.content.AttributionSource;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.wifi.V1_0.WifiStatusCode;
import android.location.LocationManager;
import android.net.MacAddress;
import android.net.wifi.IBooleanListener;
import android.net.wifi.IIntegerListener;
import android.net.wifi.IListListener;
import android.net.wifi.OuiKeyedData;
import android.net.wifi.WifiAvailableChannel;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiScanner;
import android.net.wifi.aware.AwarePairingConfig;
import android.net.wifi.aware.AwareParams;
import android.net.wifi.aware.AwareResources;
import android.net.wifi.aware.Characteristics;
import android.net.wifi.aware.ConfigRequest;
import android.net.wifi.aware.IWifiAwareDiscoverySessionCallback;
import android.net.wifi.aware.IWifiAwareEventCallback;
import android.net.wifi.aware.IWifiAwareMacAddressProvider;
import android.net.wifi.aware.IdentityChangedListener;
import android.net.wifi.aware.MacAddrMapping;
import android.net.wifi.aware.PublishConfig;
import android.net.wifi.aware.SubscribeConfig;
import android.net.wifi.aware.WifiAwareChannelInfo;
import android.net.wifi.aware.WifiAwareDataPathSecurityConfig;
import android.net.wifi.aware.WifiAwareManager;
import android.net.wifi.aware.WifiAwareNetworkSpecifier;
import android.net.wifi.util.HexEncoding;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.WorkSource;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;
import android.util.StatsEvent;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.MessageUtils;
import com.android.internal.util.StateMachine;
import com.android.internal.util.WakeupMessage;
import com.android.modules.utils.BasicShellCommandHandler;
import com.android.modules.utils.HandlerExecutor;
import com.android.modules.utils.build.SdkLevel;
import com.android.server.wifi.Clock;
import com.android.server.wifi.HalDeviceManager;
import com.android.server.wifi.InterfaceConflictManager;
import com.android.server.wifi.RunnerState;
import com.android.server.wifi.WifiInjector;
import com.android.server.wifi.aware.PairingConfigManager.PairingSecurityAssociationInfo;
import com.android.server.wifi.hal.WifiNanIface.NanStatusCode;
import com.android.server.wifi.proto.WifiStatsLog;
import com.android.server.wifi.util.NetdWrapper;
import com.android.server.wifi.util.WaitingState;
import com.android.server.wifi.util.WifiPermissionsUtil;
import com.android.server.wifi.util.WifiPermissionsWrapper;
import com.android.wifi.resources.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Manages the state of the Wi-Fi Aware system service.
 */
public class WifiAwareStateManager implements WifiAwareShellCommand.DelegatedShellCommand {
    private static final String TAG = "WifiAwareStateManager";
    private boolean mVdbg = false; // STOPSHIP if true - for detailed state machine
    private boolean mVerboseLoggingEnabled = false;

    @VisibleForTesting
    public static final String HAL_COMMAND_TIMEOUT_TAG = TAG + " HAL Command Timeout";

    @VisibleForTesting
    public static final String HAL_SEND_MESSAGE_TIMEOUT_TAG = TAG + " HAL Send Message Timeout";

    @VisibleForTesting
    public static final String HAL_DATA_PATH_CONFIRM_TIMEOUT_TAG =
            TAG + " HAL Data Path Confirm Timeout";
    @VisibleForTesting
    public static final String HAL_PAIRING_CONFIRM_TIMEOUT_TAG =
            TAG + " HAL Pairing Confirm Timeout";
    @VisibleForTesting
    public static final String HAL_BOOTSTRAPPING_CONFIRM_TIMEOUT_TAG =
            TAG + " HAL Bootstrapping Confirm Timeout";

    public static final int NAN_PAIRING_REQUEST_TYPE_SETUP = 0;
    public static final int NAN_PAIRING_REQUEST_TYPE_VERIFICATION = 1;
    public static final int NAN_PAIRING_AKM_SAE = 0;
    public static final int NAN_PAIRING_AKM_PASN = 1;
    public static final int NAN_BOOTSTRAPPING_ACCEPT = 0;
    public static final int NAN_BOOTSTRAPPING_REJECT = 1;
    public static final int NAN_BOOTSTRAPPING_COMEBACK = 2;


    public static final int NAN_PARAM_NOT_SET = -1;

    public static final int INSTANT_MODE_DISABLED = 0;
    public static final int INSTANT_MODE_24GHZ = 1;
    public static final int INSTANT_MODE_5GHZ = 3;

    /*
     * State machine message types. There are sub-types for the messages (except for TIMEOUTs).
     * Format:
     * - Message.arg1: contains message sub-type
     * - Message.arg2: contains transaction ID for RESPONSE & RESPONSE_TIMEOUT
     */
    private static final int MESSAGE_TYPE_COMMAND = 1;
    private static final int MESSAGE_TYPE_RESPONSE = 2;
    private static final int MESSAGE_TYPE_NOTIFICATION = 3;
    private static final int MESSAGE_TYPE_RESPONSE_TIMEOUT = 4;
    private static final int MESSAGE_TYPE_SEND_MESSAGE_TIMEOUT = 5;
    private static final int MESSAGE_TYPE_DATA_PATH_TIMEOUT = 6;
    private static final int MESSAGE_TYPE_PAIRING_TIMEOUT = 7;
    private static final int MESSAGE_TYPE_BOOTSTRAPPING_TIMEOUT = 8;

    /*
     * Message sub-types:
     */
    private static final int COMMAND_TYPE_CONNECT = 100;
    private static final int COMMAND_TYPE_DISCONNECT = 101;
    private static final int COMMAND_TYPE_TERMINATE_SESSION = 102;
    private static final int COMMAND_TYPE_PUBLISH = 103;
    private static final int COMMAND_TYPE_UPDATE_PUBLISH = 104;
    private static final int COMMAND_TYPE_SUBSCRIBE = 105;
    private static final int COMMAND_TYPE_UPDATE_SUBSCRIBE = 106;
    private static final int COMMAND_TYPE_ENQUEUE_SEND_MESSAGE = 107;
    private static final int COMMAND_TYPE_ENABLE_USAGE = 108;
    private static final int COMMAND_TYPE_DISABLE_USAGE = 109;
    private static final int COMMAND_TYPE_GET_CAPABILITIES = 111;
    private static final int COMMAND_TYPE_DELETE_ALL_DATA_PATH_INTERFACES = 113;
    private static final int COMMAND_TYPE_CREATE_DATA_PATH_INTERFACE = 114;
    private static final int COMMAND_TYPE_DELETE_DATA_PATH_INTERFACE = 115;
    private static final int COMMAND_TYPE_INITIATE_DATA_PATH_SETUP = 116;
    private static final int COMMAND_TYPE_RESPOND_TO_DATA_PATH_SETUP_REQUEST = 117;
    private static final int COMMAND_TYPE_END_DATA_PATH = 118;
    private static final int COMMAND_TYPE_TRANSMIT_NEXT_MESSAGE = 119;
    private static final int COMMAND_TYPE_RECONFIGURE = 120;
    private static final int COMMAND_TYPE_DELAYED_INITIALIZATION = 121;
    private static final int COMMAND_TYPE_GET_AWARE = 122;
    private static final int COMMAND_TYPE_RELEASE_AWARE = 123;
    private static final int COMMAND_TYPE_DISABLE = 124;
    private static final int COMMAND_TYPE_INITIATE_PAIRING_REQUEST = 125;
    private static final int COMMAND_TYPE_RESPONSE_PAIRING_REQUEST = 126;
    private static final int COMMAND_TYPE_INITIATE_BOOTSTRAPPING_REQUEST = 127;
    private static final int COMMAND_TYPE_RESPONSE_BOOTSTRAPPING_REQUEST = 128;
    private static final int COMMAND_TYPE_SUSPEND_SESSION = 129;
    private static final int COMMAND_TYPE_RESUME_SESSION = 130;
    private static final int COMMAND_TYPE_END_PAIRING = 131;

    private static final int RESPONSE_TYPE_ON_CONFIG_SUCCESS = 200;
    private static final int RESPONSE_TYPE_ON_CONFIG_FAIL = 201;
    private static final int RESPONSE_TYPE_ON_SESSION_CONFIG_SUCCESS = 202;
    private static final int RESPONSE_TYPE_ON_SESSION_CONFIG_FAIL = 203;
    private static final int RESPONSE_TYPE_ON_MESSAGE_SEND_QUEUED_SUCCESS = 204;
    private static final int RESPONSE_TYPE_ON_MESSAGE_SEND_QUEUED_FAIL = 205;
    private static final int RESPONSE_TYPE_ON_CAPABILITIES_UPDATED = 206;
    private static final int RESPONSE_TYPE_ON_CREATE_INTERFACE = 207;
    private static final int RESPONSE_TYPE_ON_DELETE_INTERFACE = 208;
    private static final int RESPONSE_TYPE_ON_INITIATE_DATA_PATH_SUCCESS = 209;
    private static final int RESPONSE_TYPE_ON_INITIATE_DATA_PATH_FAIL = 210;
    private static final int RESPONSE_TYPE_ON_RESPOND_TO_DATA_PATH_SETUP_REQUEST = 211;
    private static final int RESPONSE_TYPE_ON_END_DATA_PATH = 212;
    private static final int RESPONSE_TYPE_ON_DISABLE = 213;
    private static final int RESPONSE_TYPE_ON_INITIATE_PAIRING_SUCCESS = 214;
    private static final int RESPONSE_TYPE_ON_INITIATE_PAIRING_FAIL = 215;
    private static final int RESPONSE_TYPE_ON_RESPONSE_PAIRING_SUCCESS = 216;
    private static final int RESPONSE_TYPE_ON_RESPONSE_PAIRING_FAIL = 217;
    private static final int RESPONSE_TYPE_ON_INITIATE_BOOTSTRAPPING_SUCCESS = 218;
    private static final int RESPONSE_TYPE_ON_INITIATE_BOOTSTRAPPING_FAIL = 219;
    private static final int RESPONSE_TYPE_ON_RESPONSE_BOOTSTRAPPING_SUCCESS = 220;
    private static final int RESPONSE_TYPE_ON_RESPONSE_BOOTSTRAPPING_FAIL = 221;
    private static final int RESPONSE_TYPE_ON_SUSPEND = 222;
    private static final int RESPONSE_TYPE_ON_RESUME = 223;
    private static final int RESPONSE_TYPE_ON_END_PAIRING = 224;

    private static final int NOTIFICATION_TYPE_INTERFACE_CHANGE = 301;
    private static final int NOTIFICATION_TYPE_CLUSTER_CHANGE = 302;
    private static final int NOTIFICATION_TYPE_MATCH = 303;
    private static final int NOTIFICATION_TYPE_SESSION_TERMINATED = 304;
    private static final int NOTIFICATION_TYPE_MESSAGE_RECEIVED = 305;
    private static final int NOTIFICATION_TYPE_AWARE_DOWN = 306;
    private static final int NOTIFICATION_TYPE_ON_MESSAGE_SEND_SUCCESS = 307;
    private static final int NOTIFICATION_TYPE_ON_MESSAGE_SEND_FAIL = 308;
    private static final int NOTIFICATION_TYPE_ON_DATA_PATH_REQUEST = 309;
    private static final int NOTIFICATION_TYPE_ON_DATA_PATH_CONFIRM = 310;
    private static final int NOTIFICATION_TYPE_ON_DATA_PATH_END = 311;
    private static final int NOTIFICATION_TYPE_ON_DATA_PATH_SCHED_UPDATE = 312;
    private static final int NOTIFICATION_TYPE_MATCH_EXPIRED = 313;
    private static final int NOTIFICATION_TYPE_ON_PAIRING_REQUEST = 314;
    private static final int NOTIFICATION_TYPE_ON_PAIRING_CONFIRM = 315;
    private static final int NOTIFICATION_TYPE_ON_BOOTSTRAPPING_REQUEST = 316;
    private static final int NOTIFICATION_TYPE_ON_BOOTSTRAPPING_CONFIRM = 317;
    private static final int NOTIFICATION_TYPE_ON_SUSPENSION_MODE_CHANGED = 318;

    private static final SparseArray<String> sSmToString = MessageUtils.findMessageNames(
            new Class[]{WifiAwareStateManager.class},
            new String[]{"MESSAGE_TYPE", "COMMAND_TYPE", "RESPONSE_TYPE", "NOTIFICATION_TYPE"});

    /*
     * Keys used when passing (some) arguments to the Handler thread (too many
     * arguments to pass in the short-cut Message members).
     */
    private static final String MESSAGE_BUNDLE_KEY_SESSION_TYPE = "session_type";
    private static final String MESSAGE_BUNDLE_KEY_SESSION_ID = "session_id";
    private static final String MESSAGE_BUNDLE_KEY_CONFIG = "config";
    private static final String MESSAGE_BUNDLE_KEY_MESSAGE = "message";
    private static final String MESSAGE_BUNDLE_KEY_MESSAGE_PEER_ID = "message_peer_id";
    private static final String MESSAGE_BUNDLE_KEY_MESSAGE_ID = "message_id";
    private static final String MESSAGE_BUNDLE_KEY_SSI_DATA = "ssi_data";
    private static final String MESSAGE_BUNDLE_KEY_FILTER_DATA = "filter_data";
    private static final String MESSAGE_BUNDLE_KEY_MAC_ADDRESS = "mac_address";
    private static final String MESSAGE_BUNDLE_KEY_MESSAGE_DATA = "message_data";
    private static final String MESSAGE_BUNDLE_KEY_REQ_INSTANCE_ID = "req_instance_id";
    private static final String MESSAGE_BUNDLE_KEY_SEND_MESSAGE_ENQUEUE_TIME = "message_queue_time";
    private static final String MESSAGE_BUNDLE_KEY_RETRY_COUNT = "retry_count";
    private static final String MESSAGE_BUNDLE_KEY_SUCCESS_FLAG = "success_flag";
    private static final String MESSAGE_BUNDLE_KEY_STATUS_CODE = "status_code";
    private static final String MESSAGE_BUNDLE_KEY_INTERFACE_NAME = "interface_name";
    private static final String MESSAGE_BUNDLE_KEY_CHANNEL_REQ_TYPE = "channel_request_type";
    private static final String MESSAGE_BUNDLE_KEY_CHANNEL = "channel";
    private static final String MESSAGE_BUNDLE_KEY_PEER_ID = "peer_id";
    private static final String MESSAGE_BUNDLE_KEY_UID = "uid";
    private static final String MESSAGE_BUNDLE_KEY_PID = "pid";
    private static final String MESSAGE_BUNDLE_KEY_CALLING_PACKAGE = "calling_package";
    private static final String MESSAGE_BUNDLE_KEY_CALLING_FEATURE_ID = "calling_feature_id";
    private static final String MESSAGE_BUNDLE_KEY_SENT_MESSAGE = "send_message";
    private static final String MESSAGE_BUNDLE_KEY_MESSAGE_ARRIVAL_SEQ = "message_arrival_seq";
    private static final String MESSAGE_BUNDLE_KEY_NOTIFY_IDENTITY_CHANGE = "notify_identity_chg";
    private static final String MESSAGE_BUNDLE_KEY_SCID = "scid";
    private static final String MESSAGE_BUNDLE_KEY_CIPHER_SUITE = "cipher_suite";
    private static final String MESSAGE_BUNDLE_KEY_OOB = "out_of_band";
    private static final String MESSAGE_RANGING_INDICATION = "ranging_indication";
    private static final String MESSAGE_RANGE_MM = "range_mm";
    private static final String MESSAGE_BUNDLE_KEY_NDP_IDS = "ndp_ids";
    private static final String MESSAGE_BUNDLE_KEY_APP_INFO = "app_info";
    private static final String MESSAGE_BUNDLE_KEY_ACCEPT_STATE = "accept_state";
    private static final String MESSAGE_BUNDLE_KEY_NONCE = "nonce";
    private static final String MESSAGE_BUNDLE_KEY_TAG = "tag";
    private static final String MESSAGE_BUNDLE_KEY_PAIRING_CONFIG = "pairing_config";
    private static final String MESSAGE_BUNDLE_KEY_PAIRING_PASSWORD = "pairing_password";
    private static final String MESSAGE_BUNDLE_KEY_PAIRING_ALIAS = "pairing_alias";
    private static final String MESSAGE_BUNDLE_KEY_PAIRING_TYPE = "pairing_type";
    private static final String MESSAGE_BUNDLE_KEY_PAIRING_AKM = "pairing_akm";
    private static final String MESSAGE_BUNDLE_KEY_PAIRING_CIPHER_SUITE = "pairing_cipher_suite";
    private static final String MESSAGE_BUNDLE_KEY_PAIRING_PMK = "pairing_pmk";
    private static final String MESSAGE_BUNDLE_KEY_PAIRING_REQUEST_ID = "pairing_request_id";
    private static final String MESSAGE_BUNDLE_KEY_PAIRING_ACCEPT = "pairing_accept";
    private static final String MESSAGE_BUNDLE_KEY_PAIRING_CACHE = "pairing_cache";
    private static final String MESSAGE_BUNDLE_KEY_BOOTSTRAPPING_METHOD = "bootstrapping_method";
    private static final String MESSAGE_BUNDLE_KEY_BOOTSTRAPPING_REQUEST_ID =
            "bootstrapping_request_id";
    private static final String MESSAGE_BUNDLE_KEY_BOOTSTRAPPING_ACCEPT = "bootstrapping_accept";
    private static final String MESSAGE_BUNDLE_KEY_AWARE_OFFLOAD = "aware_offload";
    private static final String MESSAGE_BUNDLE_KEY_RE_ENABLE_AWARE_FROM_OFFLOAD =
            "aware_re_enable_from_offload";
    private static final String MESSAGE_BUNDLE_KEY_SUSPENSION_MODE = "suspension_mode";

    private static final String MESSAGE_BUNDLE_KEY_BOOTSTRAPPING_RESPONSE_CODE =
            "bootstrapping_response_state";
    private static final String MESSAGE_BUNDLE_KEY_BOOTSTRAPPING_COME_BACK_DELAY =
            "bootstrapping_come_back_delay";
    private static final String MESSAGE_BUNDLE_KEY_BOOTSTRAPPING_COME_BACK_COOKIE =
            "bootstrapping_come_back_cookie";
    private static final String MESSAGE_BUNDLE_KEY_BOOTSTRAPPING_IS_COME_BACK_REQUEST =
            "bootstrapping_is_come_back";
    private static final String MESSAGE_BUNDLE_KEY_CALLER_TYPE = "caller_type";
    private static final String MESSAGE_BUNDLE_KEY_VENDOR_DATA = "vendor_data";
    private WifiAwareNativeApi mWifiAwareNativeApi;
    private WifiAwareNativeManager mWifiAwareNativeManager;

    /*
     * Asynchronous access with no lock
     */
    private volatile boolean mUsageEnabled = false;

    /*
     * Synchronous access: state is only accessed through the state machine
     * handler thread: no need to use a lock.
     */
    private Context mContext;
    private WifiAwareMetrics mAwareMetrics;
    private WifiPermissionsUtil mWifiPermissionsUtil;
    private volatile Capabilities mCapabilities;
    private volatile Characteristics mCharacteristics = null;
    private WifiAwareStateMachine mSm;
    public WifiAwareDataPathStateManager mDataPathMgr;
    private PowerManager mPowerManager;
    private InterfaceConflictManager mInterfaceConflictMgr;
    private WifiManager mWifiManager;
    private Handler mHandler;
    private final WifiInjector mWifiInjector;
    private final PairingConfigManager mPairingConfigManager;

    private final SparseArray<WifiAwareClientState> mClients = new SparseArray<>();
    private ConfigRequest mCurrentAwareConfiguration = null;
    private boolean mCurrentIdentityNotification = false;
    private boolean mCurrentRangingEnabled = false;
    private boolean mInstantCommModeGlobalEnable = false;
    private int mOverrideInstantMode = INSTANT_MODE_DISABLED;
    private int mInstantCommModeClientRequest = INSTANT_MODE_DISABLED;
    private int mClusterIdInt = NAN_PARAM_NOT_SET; // -1 is not set.
    private static final int AWARE_BAND_2_INSTANT_COMMUNICATION_CHANNEL_FREQ = 2437; // Channel 6
    private int mAwareBand5InstantCommunicationChannelFreq =
            NAN_PARAM_NOT_SET; // -1 is not set, 0 is unsupported.
    private static final int AWARE_BAND_5_INSTANT_COMMUNICATION_CHANNEL_FREQ_CHANNEL_149 = 5745;
    private static final int AWARE_BAND_5_INSTANT_COMMUNICATION_CHANNEL_FREQ_CHANNEL_44 = 5220;

    private static final byte[] ALL_ZERO_MAC = new byte[] {0, 0, 0, 0, 0, 0};
    private byte[] mCurrentDiscoveryInterfaceMac = ALL_ZERO_MAC;
    private byte[] mClusterId = ALL_ZERO_MAC;
    private int mClusterEventType = -1;
    // Flag to help defer the connect request when disable Aware is not finished, to prevent race
    // condition.
    private boolean mAwareIsDisabling = false;
    private final SparseArray<PairingInfo> mPairingRequest = new SparseArray<>();
    private final SparseArray<BootStrppingInfo> mBootstrappingRequest = new SparseArray<>();
    private WifiAwarePullAtomCallback mWifiAwarePullAtomCallback = null;

    private long mStartTime;

    private static class PairingInfo {
        public final int mClientId;
        public final int mSessionId;
        public final int mPeerId;
        public final String mAlias;

        PairingInfo(int clientId, int sessionId, int peerId, String alias) {
            mClientId = clientId;
            mSessionId = sessionId;
            mPeerId = peerId;
            mAlias = alias;
        }
    }

    private static class BootStrppingInfo {
        public final int mClientId;
        public final int mSessionId;
        public final int mPeerId;
        public final int mMethod;
        public final boolean mIsComeBackFollowUp;

        BootStrppingInfo(int clientId, int sessionId, int peerId, int method,
                boolean isComeBackFollowUp) {
            mClientId = clientId;
            mSessionId = sessionId;
            mPeerId = peerId;
            mMethod = method;
            mIsComeBackFollowUp = isComeBackFollowUp;
        }
    }

    public WifiAwareStateManager(WifiInjector wifiInjector,
            PairingConfigManager pairingConfigManager) {
        mWifiInjector = wifiInjector;
        mPairingConfigManager = pairingConfigManager;
        onReset();
    }

    /**
     * Enable/Disable verbose logging.
     */
    public void enableVerboseLogging(boolean verboseEnabled, boolean halVerboseLogging,
            boolean vDbg) {
        mVerboseLoggingEnabled = verboseEnabled;
        mDataPathMgr.enableVerboseLogging(verboseEnabled, vDbg);
        mSm.setDbg(halVerboseLogging);
        mVdbg = vDbg;
    }

    /**
     * Inject references to other manager objects. Needed to resolve
     * circular dependencies and to allow mocking.
     */
    public void setNative(WifiAwareNativeManager wifiAwareNativeManager,
            WifiAwareNativeApi wifiAwareNativeApi) {
        mWifiAwareNativeManager = wifiAwareNativeManager;
        mWifiAwareNativeApi = wifiAwareNativeApi;
    }

    /*
     * parameters settable through shell command
     */
    public static final String PARAM_ON_IDLE_DISABLE_AWARE = "on_idle_disable_aware";
    public static final int PARAM_ON_IDLE_DISABLE_AWARE_DEFAULT = 1; // 0 = false, 1 = true

    private final Map<String, Integer> mSettableParameters = new HashMap<>();

    private final Set<String> mOpportunisticSet = new ArraySet<>();

    /**
     * Interpreter of adb shell command 'adb shell wifiaware native_api ...'.
     *
     * @return -1 if parameter not recognized or invalid value, 0 otherwise.
     */
    @Override
    public int onCommand(BasicShellCommandHandler parentShell) {
        final PrintWriter pw_err = parentShell.getErrPrintWriter();
        final PrintWriter pw_out = parentShell.getOutPrintWriter();

        String subCmd = parentShell.getNextArgRequired();
        switch (subCmd) {
            case "set": {
                String name = parentShell.getNextArgRequired();
                if (!mSettableParameters.containsKey(name)) {
                    pw_err.println("Unknown parameter name -- '" + name + "'");
                    return -1;
                }

                String valueStr = parentShell.getNextArgRequired();
                int value;
                try {
                    value = Integer.valueOf(valueStr);
                } catch (NumberFormatException e) {
                    pw_err.println("Can't convert value to integer -- '" + valueStr + "'");
                    return -1;
                }
                mSettableParameters.put(name, value);
                return 0;
            }
            case "get": {
                String name = parentShell.getNextArgRequired();
                if (!mSettableParameters.containsKey(name)) {
                    pw_err.println("Unknown parameter name -- '" + name + "'");
                    return -1;
                }

                pw_out.println((int) mSettableParameters.get(name));
                return 0;
            }
            case "get_capabilities": {
                if (mCapabilities != null) {
                    try {
                        pw_out.println(mCapabilities.toJSON().toString());
                    } catch (JSONException e) {
                        Log.e(TAG, "onCommand: get_capabilities e=" + e);
                    }
                }
                return 0;
            }
            case "get_aware_resources": {
                if (!SdkLevel.isAtLeastS()) {
                    return -1;
                }
                JSONObject j = new JSONObject();
                AwareResources resources = getAvailableAwareResources();
                if (resources != null) {
                    try {
                        j.put("numOfAvailableNdps", resources.getAvailableDataPathsCount());
                        j.put("numOfAvailablePublishSessions",
                                resources.getAvailablePublishSessionsCount());
                        j.put("numOfAvailableSubscribeSessions",
                                resources.getAvailableSubscribeSessionsCount());
                    } catch (JSONException e) {
                        Log.e(TAG, "onCommand: get_aware_resources e=" + e);
                    }
                }
                pw_out.println(j.toString());
                return 0;
            }
            case "allow_ndp_any": {
                String flag = parentShell.getNextArgRequired();
                if (mDataPathMgr == null) {
                    pw_err.println("Null Aware data-path manager - can't configure");
                    return -1;
                }
                if (TextUtils.equals("true", flag)) {
                    mDataPathMgr.mAllowNdpResponderFromAnyOverride = true;
                    return 0;
                } else  if (TextUtils.equals("false", flag)) {
                    mDataPathMgr.mAllowNdpResponderFromAnyOverride = false;
                    return 0;
                } else {
                    pw_err.println(
                            "Unknown configuration flag for 'allow_ndp_any' - true|false expected"
                                    + " -- '"
                                    + flag + "'");
                    return -1;
                }
            }
            case "get_instant_communication_channel": {
                String arg = parentShell.getNextArgRequired();
                int band;
                if (TextUtils.equals(arg, "2G")) {
                    band = WifiScanner.WIFI_BAND_24_GHZ;
                } else if (TextUtils.equals(arg, "5G")) {
                    band = WifiScanner.WIFI_BAND_5_GHZ;
                } else {
                    pw_err.println("Unknown band -- " + arg);
                    return -1;
                }
                List<WifiAvailableChannel> channels = mWifiInjector.getWifiThreadRunner().call(
                        () -> mWifiInjector.getWifiNative().getUsableChannels(band,
                                OP_MODE_WIFI_AWARE,
                                WifiAvailableChannel.FILTER_NAN_INSTANT_MODE), null);
                StringBuilder out = new StringBuilder();
                for (WifiAvailableChannel channel : channels) {
                    out.append(channel.toString());
                    out.append(", ");
                }
                pw_out.println(out.toString());
                return 0;
            }
            case "set_override_instant_communication_mode": {
                String arg = parentShell.getNextArgRequired();
                if (TextUtils.equals(arg, "2G")) {
                    mOverrideInstantMode = INSTANT_MODE_24GHZ;
                } else if (TextUtils.equals(arg, "5G")) {
                    mOverrideInstantMode = INSTANT_MODE_5GHZ;
                } else {
                    pw_err.println("Unknown band -- " + arg);
                    return -1;
                }
                return 0;
            }
            case "clear_override_instant_communication_mode": {
                mOverrideInstantMode = INSTANT_MODE_DISABLED;
                return 0;
            }
            case "set_cluster_id": {
                String arg = parentShell.getNextArgRequired();
                int clusterId;
                try {
                    clusterId = Integer.valueOf(arg);
                } catch (NumberFormatException e) {
                    pw_err.println("Can't convert value to integer -- '" + arg + "'");
                    return -1;
                }

                if (clusterId < ConfigRequest.CLUSTER_ID_MIN
                        || clusterId > ConfigRequest.CLUSTER_ID_MAX) {
                    pw_err.println("cluster ID must be in the range of 0x0000, 0xFFFF. "
                            + "Cluster ID =" + arg);
                    return -1;
                }

                return setClusterId(clusterId) ? 0 : -1;
            }
            default:
                pw_err.println("Unknown 'wifiaware state_mgr <cmd>'");
        }

        return -1;
    }

    @Override
    public void onReset() {
        mSettableParameters.put(PARAM_ON_IDLE_DISABLE_AWARE, PARAM_ON_IDLE_DISABLE_AWARE_DEFAULT);
        if (mDataPathMgr != null) {
            mDataPathMgr.mAllowNdpResponderFromAnyOverride = false;
        }
    }

    @Override
    public void onHelp(String command, BasicShellCommandHandler parentShell) {
        final PrintWriter pw = parentShell.getOutPrintWriter();

        pw.println("  " + command);
        pw.println("    set <name> <value>: sets named parameter to value. Names: "
                + mSettableParameters.keySet());
        pw.println("    get <name>: gets named parameter value. Names: "
                + mSettableParameters.keySet());
        pw.println("    get_capabilities: prints out the capabilities as a JSON string");
        pw.println(
                "    allow_ndp_any true|false: configure whether Responders can be specified to "
                        + "accept requests from ANY requestor (null peer spec)");
        pw.println(" get_instant_communication_channel 2G|5G: get instant communication mode "
                + "channel available for the target band");
        pw.println(" set_override_instant_communication_mode 2G|5G: override the instant "
                + "communication mode to 'enabled' with the specified band");
        pw.println(" clear_override_instant_communication_mode: clear the override of the instant "
                + "communication mode");
        pw.println(" set_cluster_id <value>: set the cluster id to request to join a cluster");
    }

    /**
     * Initialize the handler of the state manager with the specified thread
     * looper.
     *
     * @param looper Thread looper on which to run the handler.
     */
    public void start(Context context, Looper looper, WifiAwareMetrics awareMetrics,
            WifiPermissionsUtil wifiPermissionsUtil, WifiPermissionsWrapper permissionsWrapper,
            Clock clock, NetdWrapper netdWrapper, InterfaceConflictManager interfaceConflictMgr) {
        Log.i(TAG, "start()");

        mContext = context;
        mAwareMetrics = awareMetrics;
        mWifiPermissionsUtil = wifiPermissionsUtil;
        mInterfaceConflictMgr = interfaceConflictMgr;
        mSm = new WifiAwareStateMachine(TAG, looper);
        mSm.setDbg(mVdbg);
        mSm.start();
        mHandler = new Handler(looper);

        mDataPathMgr = new WifiAwareDataPathStateManager(this, clock);
        mDataPathMgr.start(mContext, mSm.getHandler().getLooper(), awareMetrics,
                wifiPermissionsUtil, permissionsWrapper, netdWrapper);

        mPowerManager = mContext.getSystemService(PowerManager.class);
        mWifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(Intent.ACTION_SCREEN_ON);
        intentFilter.addAction(Intent.ACTION_SCREEN_OFF);
        intentFilter.addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED);
        mContext.registerReceiver(
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        String action = intent.getAction();
                        if (mVerboseLoggingEnabled) {
                            Log.v(TAG, "BroadcastReceiver: action=" + action);
                        }
                        if (action.equals(Intent.ACTION_SCREEN_ON)
                                || action.equals(Intent.ACTION_SCREEN_OFF)) {
                            reconfigure();
                        }

                        if (action.equals(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)) {
                            if (mSettableParameters.get(PARAM_ON_IDLE_DISABLE_AWARE) != 0) {
                                if (mPowerManager.isDeviceIdleMode()
                                        && !isAnyCallerIgnoringBatteryOptimizations()) {
                                    disableUsage(false);
                                } else {
                                    enableUsage();
                                }
                            } else {
                                reconfigure();
                            }
                        }
                    }
                },
                intentFilter,
                null,
                mHandler);

        intentFilter = new IntentFilter();
        intentFilter.addAction(LocationManager.MODE_CHANGED_ACTION);
        mContext.registerReceiver(
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        if (mVerboseLoggingEnabled) {
                            Log.v(TAG, "onReceive: MODE_CHANGED_ACTION: intent=" + intent);
                        }
                        if (wifiPermissionsUtil.isLocationModeEnabled()) {
                            enableUsage();
                        } else {
                            if (SdkLevel.isAtLeastT()) {
                                handleLocationModeDisabled();
                            } else {
                                disableUsage(false);
                            }
                        }
                    }
                },
                intentFilter,
                null,
                mHandler);

        intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        mContext.registerReceiver(
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        if (mVerboseLoggingEnabled) {
                            Log.v(TAG, "onReceive: WIFI_STATE_CHANGED_ACTION: intent=" + intent);
                        }
                        boolean isEnabled =
                                intent.getIntExtra(
                                                WifiManager.EXTRA_WIFI_STATE,
                                                WifiManager.WIFI_STATE_UNKNOWN)
                                        == WifiManager.WIFI_STATE_ENABLED;
                        if (isEnabled) {
                            enableUsage();
                        } else {
                            disableUsage(false);
                        }
                    }
                },
                intentFilter,
                null,
                mHandler);
    }

    private class CountryCodeChangeCallback implements
            WifiManager.ActiveCountryCodeChangedCallback {

        @Override
        public void onActiveCountryCodeChanged(@androidx.annotation.NonNull String countryCode) {
            mAwareBand5InstantCommunicationChannelFreq = -1;
            reconfigure();
        }

        @Override
        public void onCountryCodeInactive() {
            // Ignore.
        }
    }

    /**
     * Initialize the late-initialization sub-services: depend on other services already existing.
     */
    public void startLate() {
        delayedInitialization();
    }

    /**
     * Try to get capability if it is null.
     */
    public void tryToGetAwareCapability() {
        if (mCapabilities != null) return;
        // Internal request for fetching capabilities.
        getAwareInterface(new WorkSource(Process.WIFI_UID));
        queryCapabilities();
        releaseAwareInterface();
    }

    /**
     * Get the client state for the specified ID (or null if none exists).
     */
    /* package */ WifiAwareClientState getClient(int clientId) {
        return mClients.get(clientId);
    }

    /**
     * Get the capabilities.
     */
    public Capabilities getCapabilities() {
        return mCapabilities;
    }

    /**
     * Get the available aware resources.
     */
    public AwareResources getAvailableAwareResources() {
        if (mCapabilities == null) {
            if (mVerboseLoggingEnabled) {
                Log.v(TAG, "Aware capability hasn't loaded, resources is unknown.");
            }
            return null;
        }
        Pair<Integer, Integer> numOfDiscoverySessions = getNumOfDiscoverySessions();
        int numOfAvailableNdps = mCapabilities.maxNdpSessions - mDataPathMgr.getNumOfNdps();
        int numOfAvailablePublishSessions =
                mCapabilities.maxPublishes - numOfDiscoverySessions.first;
        int numOfAvailableSubscribeSessions =
                mCapabilities.maxSubscribes - numOfDiscoverySessions.second;
        if (numOfAvailableNdps < 0) {
            Log.w(TAG, "Available NDPs number is negative, wrong capability?");
        }
        if (numOfAvailablePublishSessions < 0) {
            Log.w(TAG, "Available publish session number is negative, wrong capability?");
        }
        if (numOfAvailableSubscribeSessions < 0) {
            Log.w(TAG, "Available subscribe session number is negative, wrong capability?");
        }
        return new AwareResources(numOfAvailableNdps, numOfAvailablePublishSessions,
                numOfAvailableSubscribeSessions);
    }

    private Pair<Integer, Integer> getNumOfDiscoverySessions() {
        int numOfPub = 0;
        int numOfSub = 0;
        for (int i = 0; i < mClients.size(); i++) {
            WifiAwareClientState clientState = mClients.valueAt(i);
            for (int j = 0; j < clientState.getSessions().size(); j++) {
                WifiAwareDiscoverySessionState session = clientState.getSessions().valueAt(j);
                if (session.isPublishSession()) {
                    numOfPub++;
                } else {
                    numOfSub++;
                }
            }
        }
        return Pair.create(numOfPub, numOfSub);
    }

    /**
     * Get the public characteristics derived from the capabilities. Use lazy initialization.
     */
    public Characteristics getCharacteristics() {
        if (mCharacteristics == null && mCapabilities != null) {
            mCharacteristics = mCapabilities.toPublicCharacteristics(
                    mWifiInjector.getDeviceConfigFacade());
        }

        return mCharacteristics;
    }

    /**
     * Check if there is any active attach session
     */
    public boolean isDeviceAttached() {
        return mClients != null && mClients.size() > 0;
    }

    /*
     * Cross-service API: synchronized but independent of state machine
     */

    /**
     * Translate (and return in the callback) the peerId to its MAC address representation.
     */
    public void requestMacAddresses(int uid, int[] peerIds,
            IWifiAwareMacAddressProvider callback) {
        mSm.getHandler().post(() -> {
            if (mVdbg) {
                Log.v(TAG, "requestMacAddresses: uid=" + uid + ", peerIds="
                        + Arrays.toString(peerIds));
            }
            Map<Integer, MacAddrMapping> peerIdToMacMap = new HashMap<>();
            for (int i = 0; i < mClients.size(); ++i) {
                WifiAwareClientState client = mClients.valueAt(i);
                if (client.getUid() != uid) {
                    continue;
                }

                SparseArray<WifiAwareDiscoverySessionState> sessions = client.getSessions();
                for (int j = 0; j < sessions.size(); ++j) {
                    WifiAwareDiscoverySessionState session = sessions.valueAt(j);

                    for (int peerId : peerIds) {
                        WifiAwareDiscoverySessionState.PeerInfo peerInfo = session.getPeerInfo(
                                peerId);
                        if (peerInfo != null) {
                            MacAddrMapping mapping = new MacAddrMapping();
                            mapping.peerId = peerId;
                            mapping.macAddress = peerInfo.mMac;
                            peerIdToMacMap.put(peerId, mapping);
                        }
                    }
                }
            }

            try {
                MacAddrMapping[] peerIdToMacList = peerIdToMacMap.values()
                        .toArray(new MacAddrMapping[0]);
                if (mVerboseLoggingEnabled) {
                    Log.v(TAG, "requestMacAddresses: peerIdToMacList begin");
                    for (MacAddrMapping mapping : peerIdToMacList) {
                        Log.v(TAG, "    " + mapping.peerId + ": "
                                + MacAddress.fromBytes(mapping.macAddress));
                    }
                    Log.v(TAG, "requestMacAddresses: peerIdToMacList end");
                }
                callback.macAddress(peerIdToMacList);
            } catch (RemoteException e) {
                Log.e(TAG, "requestMacAddress (sync): exception on callback -- " + e);
            }
        });
    }

    /*
     * COMMANDS
     */

    /**
     * Place a request for delayed start operation on the state machine queue.
     */
    public void delayedInitialization() {
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_COMMAND);
        msg.arg1 = COMMAND_TYPE_DELAYED_INITIALIZATION;
        mSm.sendMessage(msg);
    }

    /**
     * Place a request to get the Wi-Fi Aware interface (before which no HAL command can be
     * executed).
     */
    public void getAwareInterface(@NonNull WorkSource requestorWs) {
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_COMMAND);
        msg.arg1 = COMMAND_TYPE_GET_AWARE;
        msg.obj = requestorWs;
        mSm.sendMessage(msg);
    }

    /**
     * Place a request to release the Wi-Fi Aware interface (after which no HAL command can be
     * executed).
     */
    public void releaseAwareInterface() {
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_COMMAND);
        msg.arg1 = COMMAND_TYPE_RELEASE_AWARE;
        mSm.sendMessage(msg);
    }

    /**
     * Enable instant communication mode if supported.
     * @param enabled true for enable, false for disable.
     */
    public void enableInstantCommunicationMode(boolean enabled) {
        if (mCapabilities == null) {
            if (mVerboseLoggingEnabled) {
                Log.v(TAG, "Aware capability is not loaded.");
            }
            return;
        }

        if (!mCapabilities.isInstantCommunicationModeSupported) {
            if (mVerboseLoggingEnabled) {
                Log.v(TAG, "Device does not support instant communication mode.");
            }
            return;
        }
        if (mInstantCommModeGlobalEnable == enabled) return;

        mInstantCommModeGlobalEnable = enabled;
        reconfigure();
    }

    /**
     * Set cluster ID if supported.
     * @param clusterId value ranges from 0x0000 to 0xFFFF.
     */
    private boolean setClusterId(int clusterId) {
        if (mCapabilities == null) {
            Log.e(TAG, "Aware capability is not loaded.");
            return false;
        }

        if (!mCapabilities.isSetClusterIdSupported) {
            Log.e(TAG, "Device does not support setting cluster ID.");
            return false;
        }
        if (mClusterIdInt == clusterId) return true;

        mClusterIdInt = clusterId;
        reconfigure();
        return true;
    }

    /**
     * Get if instant communication mode is currently enabled.
     * @return true if enabled, false otherwise.
     */
    public boolean isInstantCommModeGlobalEnable() {
        return mInstantCommModeGlobalEnable;
    }

    /**
     * Get if set channel on data-path request is supported.
     * @return true if supported, false otherwise.
     */
    public boolean isSetChannelOnDataPathSupported() {
        return mContext.getResources()
                .getBoolean(R.bool.config_wifiSupportChannelOnDataPath);
    }

    /**
     * Accept using parameter from external to config the Aware,
     * @see WifiAwareManager#setAwareParams(AwareParams)
     */
    public void setAwareParams(AwareParams parameters) {
        mHandler.post(() -> {
            mWifiAwareNativeApi.setAwareParams(parameters);
            reconfigure();
        });
    }

    /**
     * @see WifiAwareManager#resetPairedDevices()
     */
    public void resetPairedDevices(String callingPackage) {
        mHandler.post(() -> mPairingConfigManager.removePackage(callingPackage));
    }

    /**
     * @see WifiAwareManager#removePairedDevice(String)
     */
    public void removePairedDevice(String callingPackage, String alias) {
        mHandler.post(
                () -> mPairingConfigManager.removePairedDevice(callingPackage, alias));
    }

    /**
     * @see WifiAwareManager#getPairedDevices(Executor, Consumer)
     */
    public void getPairedDevices(String callingPackage, IListListener listener) {
        mHandler.post(() -> {
                    try {
                        listener.onResult(mPairingConfigManager
                                .getAllPairedDevices(callingPackage));
                    } catch (RemoteException e) {
                        Log.e(TAG, e.getMessage(), e);
                    }
                }
        );
    }

    /**
     * @see android.net.wifi.aware.WifiAwareSession#setMasterPreference(int)
     */
    public void setMasterPreference(int clientId, int masterPreference) {
        mHandler.post(() -> {
            WifiAwareClientState state = mClients.get(clientId);
            if (state == null) {
                Log.e(TAG, "client state is missing");
                return;
            }
            state.getConfigRequest().mMasterPreference = masterPreference;
            reconfigure();
        });
    }

    /**
     * @see android.net.wifi.aware.WifiAwareSession#getMasterPreference(Executor, Consumer)
     */
    public void getMasterPreference(int clientId, IIntegerListener listener) {
        mHandler.post(() -> {
            WifiAwareClientState state = mClients.get(clientId);
            if (state == null) {
                Log.e(TAG, "client state is missing");
                return;
            }
            try {
                listener.onResult(state.getConfigRequest().mMasterPreference);
            } catch (RemoteException e) {
                Log.e(TAG, e.getMessage(), e);
            }
        });
    }

    /**
     * @see WifiAwareManager#setOpportunisticModeEnabled(boolean)
     */
    public void setOpportunisticPackage(String ctxPkg, boolean enabled) {
        mHandler.post(() -> {
            if (enabled) {
                mOpportunisticSet.add(ctxPkg);
            } else {
                mOpportunisticSet.remove(ctxPkg);
            }
            if (mClients.size() == 0) {
                return;
            }
            if (!mWifiAwareNativeManager.replaceRequestorWs(createMergedRequestorWs())) {
                Log.w(TAG, "Failed to replace requestorWs");
            }
        });
    }

    /**
     * @see WifiAwareManager#isOpportunisticModeEnabled(Executor, Consumer)
     */
    public void isOpportunistic(String ctxPkg, IBooleanListener listener) {
        mHandler.post(() -> {
            try {
                listener.onResult(mOpportunisticSet.contains(ctxPkg));
            } catch (RemoteException e) {
                Log.e(TAG, e.getMessage(), e);
            }
        });
    }

    /**
     * Place a request for a new client connection on the state machine queue.
     */
    public void connect(int clientId, int uid, int pid, String callingPackage,
            @Nullable String callingFeatureId, IWifiAwareEventCallback callback,
            ConfigRequest configRequest, boolean notifyOnIdentityChanged, Bundle extra,
            boolean forAwareOffload) {
        boolean reEnableAware = false;
        // If FW could not handle the Aware request priority, disable the Aware first
        if (!mContext.getResources()
                .getBoolean(R.bool.config_wifiAwareOffloadingFirmwareHandlePriority)
                && isAwareOffloading() && !forAwareOffload) {
            // Do not release Aware, as new request will get Interface again
            deferDisableAware(false);
            reEnableAware = true;
        }
        int callerType;
        if (SdkLevel.isAtLeastS()) {
            AttributionSource attributionSource =
                    extra.getParcelable(WifiManager.EXTRA_PARAM_KEY_ATTRIBUTION_SOURCE);
            callerType = mWifiPermissionsUtil.getWifiCallerType(attributionSource);
        } else {
            callerType = mWifiPermissionsUtil.getWifiCallerType(uid, callingPackage);
        }
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_COMMAND);
        msg.arg1 = COMMAND_TYPE_CONNECT;
        msg.arg2 = clientId;
        Pair<IWifiAwareEventCallback, Object> callbackAndAttributionSource = new Pair<>(
                callback, extra.getParcelable(WifiManager.EXTRA_PARAM_KEY_ATTRIBUTION_SOURCE));
        msg.obj = callbackAndAttributionSource;
        msg.getData().putParcelable(MESSAGE_BUNDLE_KEY_CONFIG, configRequest);
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_UID, uid);
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_PID, pid);
        msg.getData().putString(MESSAGE_BUNDLE_KEY_CALLING_PACKAGE, callingPackage);
        msg.getData().putString(MESSAGE_BUNDLE_KEY_CALLING_FEATURE_ID, callingFeatureId);
        msg.getData().putBoolean(MESSAGE_BUNDLE_KEY_NOTIFY_IDENTITY_CHANGE,
                notifyOnIdentityChanged);
        msg.getData().putBoolean(MESSAGE_BUNDLE_KEY_AWARE_OFFLOAD, forAwareOffload);
        msg.getData().putBoolean(MESSAGE_BUNDLE_KEY_RE_ENABLE_AWARE_FROM_OFFLOAD, reEnableAware);
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_CALLER_TYPE, callerType);
        mSm.sendMessage(msg);
        // Clean the client after the connect to avoid Aware disable
        if (!forAwareOffload) {
            for (int i = 0; i < mClients.size(); i++) {
                WifiAwareClientState clientState = mClients.valueAt(i);
                if (clientState.isAwareOffload()) {
                    disconnect(clientState.getClientId());
                }
            }
        }
    }

    /**
     * Place a request to disconnect (destroy) an existing client on the state
     * machine queue.
     */
    public void disconnect(int clientId) {
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_COMMAND);
        msg.arg1 = COMMAND_TYPE_DISCONNECT;
        msg.arg2 = clientId;
        mSm.sendMessage(msg);
    }

    /**
     * Place a request to defer Disable Aware on the state machine queue.
     * @param releaseAware
     */
    private void deferDisableAware(boolean releaseAware) {
        mAwareIsDisabling = true;
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_COMMAND);
        msg.arg1 = COMMAND_TYPE_DISABLE;
        msg.obj = releaseAware;
        mSm.sendMessage(msg);
    }

    /**
     * Place a request to reconfigure Aware. No additional input - intended to use current
     * power settings when executed. Thus possibly entering or exiting power saving mode if
     * needed (or do nothing if Aware is not active).
     */
    public void reconfigure() {
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_COMMAND);
        msg.arg1 = COMMAND_TYPE_RECONFIGURE;
        mSm.sendMessage(msg);
    }

    /**
     * Place a request to stop a discovery session on the state machine queue.
     */
    public void terminateSession(int clientId, int sessionId) {
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_COMMAND);
        msg.arg1 = COMMAND_TYPE_TERMINATE_SESSION;
        msg.arg2 = clientId;
        msg.obj = sessionId;
        mSm.sendMessage(msg);
    }

    /**
     * Place a request to start a new publish discovery session on the state
     * machine queue.
     */
    public void publish(int clientId, PublishConfig publishConfig,
            IWifiAwareDiscoverySessionCallback callback) {
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_COMMAND);
        msg.arg1 = COMMAND_TYPE_PUBLISH;
        msg.arg2 = clientId;
        msg.obj = callback;
        msg.getData().putParcelable(MESSAGE_BUNDLE_KEY_CONFIG, publishConfig);
        mSm.sendMessage(msg);
    }

    /**
     * Place a request to modify an existing publish discovery session on the
     * state machine queue.
     */
    public void updatePublish(int clientId, int sessionId, PublishConfig publishConfig) {
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_COMMAND);
        msg.arg1 = COMMAND_TYPE_UPDATE_PUBLISH;
        msg.arg2 = clientId;
        msg.getData().putParcelable(MESSAGE_BUNDLE_KEY_CONFIG, publishConfig);
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_SESSION_ID, sessionId);
        mSm.sendMessage(msg);
    }

    /**
     * Place a request to start a new subscribe discovery session on the state
     * machine queue.
     */
    public void subscribe(int clientId, SubscribeConfig subscribeConfig,
            IWifiAwareDiscoverySessionCallback callback) {
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_COMMAND);
        msg.arg1 = COMMAND_TYPE_SUBSCRIBE;
        msg.arg2 = clientId;
        msg.obj = callback;
        msg.getData().putParcelable(MESSAGE_BUNDLE_KEY_CONFIG, subscribeConfig);
        mSm.sendMessage(msg);
    }

    /**
     * Place a request to modify an existing subscribe discovery session on the
     * state machine queue.
     */
    public void updateSubscribe(int clientId, int sessionId, SubscribeConfig subscribeConfig) {
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_COMMAND);
        msg.arg1 = COMMAND_TYPE_UPDATE_SUBSCRIBE;
        msg.arg2 = clientId;
        msg.getData().putParcelable(MESSAGE_BUNDLE_KEY_CONFIG, subscribeConfig);
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_SESSION_ID, sessionId);
        mSm.sendMessage(msg);
    }

    /**
     * Place a request to send a message on a discovery session on the state
     * machine queue.
     */
    public void sendMessage(int uid, int clientId, int sessionId, int peerId, byte[] message,
            int messageId, int retryCount) {
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_COMMAND);
        msg.arg1 = COMMAND_TYPE_ENQUEUE_SEND_MESSAGE;
        msg.arg2 = clientId;
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_SESSION_ID, sessionId);
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_MESSAGE_PEER_ID, peerId);
        msg.getData().putByteArray(MESSAGE_BUNDLE_KEY_MESSAGE, message);
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_MESSAGE_ID, messageId);
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_RETRY_COUNT, retryCount);
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_UID, uid);
        mSm.sendMessage(msg);
    }

    /**
     * Initiate a NAN pairing setup request
     */
    public void initiateNanPairingSetupRequest(int clientId, int sessionId, int peerId,
            String password, String pairingDeviceAlias, int cipherSuite) {
        initiateNanPairingRequest(clientId, sessionId, peerId, password, pairingDeviceAlias,
                NAN_PAIRING_REQUEST_TYPE_SETUP, null,
                TextUtils.isEmpty(password) ? NAN_PAIRING_AKM_PASN : NAN_PAIRING_AKM_SAE,
                cipherSuite);
    }

    private void initiateNanPairingVerificationRequest(int clientId, int sessionId, int peerId,
            String pairingDeviceAlias, byte[] pmk, int akm, int cipherSuite) {
        initiateNanPairingRequest(clientId, sessionId, peerId, null, pairingDeviceAlias,
                NAN_PAIRING_REQUEST_TYPE_VERIFICATION, pmk, akm, cipherSuite);
    }

    private void initiateNanPairingRequest(int clientId, int sessionId, int peerId,
            String password, String pairingDeviceAlias, int requestType, byte[] pmk, int akm,
            int cipherSuite) {
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_COMMAND);
        msg.arg1 = COMMAND_TYPE_INITIATE_PAIRING_REQUEST;
        msg.arg2 = clientId;
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_SESSION_ID, sessionId);
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_PEER_ID, peerId);
        msg.getData().putString(MESSAGE_BUNDLE_KEY_PAIRING_ALIAS, pairingDeviceAlias);
        msg.getData().putString(MESSAGE_BUNDLE_KEY_PAIRING_PASSWORD, password);
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_PAIRING_TYPE, requestType);
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_PAIRING_AKM, akm);
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_PAIRING_CIPHER_SUITE, cipherSuite);
        msg.getData().putByteArray(MESSAGE_BUNDLE_KEY_PAIRING_PMK, pmk);
        mSm.sendMessage(msg);
    }

    /**
     * Response to a NAN pairing setup request
     */
    public void responseNanPairingSetupRequest(int clientId, int sessionId, int peerId,
            int requestId, String password, String pairingDeviceAlias, boolean accept,
            int cipherSuite) {
        responseNanPairingRequest(clientId, sessionId, peerId, requestId, password,
                pairingDeviceAlias, NAN_PAIRING_REQUEST_TYPE_SETUP, null,
                TextUtils.isEmpty(password) ? NAN_PAIRING_AKM_PASN : NAN_PAIRING_AKM_SAE, accept,
                cipherSuite);
    }

    private void responseNanPairingVerificationRequest(int clientId, int sessionId, int peerId,
            int requestId, String pairingDeviceAlias, boolean accept, byte[] pmk, int akm,
            int cipherSuite) {
        responseNanPairingRequest(clientId, sessionId, peerId, requestId, null,
                pairingDeviceAlias, NAN_PAIRING_REQUEST_TYPE_VERIFICATION, pmk, akm, accept,
                cipherSuite);
    }

    private void responseNanPairingRequest(int clientId, int sessionId, int peerId, int requestId,
            String password, String pairingDeviceAlias, int requestType, byte[] pmk, int akm,
            boolean accept, int cipherSuite) {
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_COMMAND);
        msg.arg1 = COMMAND_TYPE_RESPONSE_PAIRING_REQUEST;
        msg.arg2 = clientId;
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_SESSION_ID, sessionId);
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_PEER_ID, peerId);
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_PAIRING_REQUEST_ID, requestId);
        msg.getData().putString(MESSAGE_BUNDLE_KEY_PAIRING_ALIAS, pairingDeviceAlias);
        msg.getData().putString(MESSAGE_BUNDLE_KEY_PAIRING_PASSWORD, password);
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_PAIRING_TYPE, requestType);
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_PAIRING_AKM, akm);
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_PAIRING_CIPHER_SUITE, cipherSuite);
        msg.getData().putByteArray(MESSAGE_BUNDLE_KEY_PAIRING_PMK, pmk);
        msg.getData().putBoolean(MESSAGE_BUNDLE_KEY_PAIRING_ACCEPT, accept);
        mSm.sendMessage(msg);
    }

    /**
     * Initiate a bootstrapping request
     */
    public void initiateBootStrappingSetupRequest(int clientId, int sessionId, int peerId, int
            method, long comeBackDelayMills, byte[] cookie) {
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_COMMAND);
        msg.arg1 = COMMAND_TYPE_INITIATE_BOOTSTRAPPING_REQUEST;
        msg.arg2 = clientId;
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_SESSION_ID, sessionId);
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_PEER_ID, peerId);
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_BOOTSTRAPPING_METHOD, method);
        msg.getData().putByteArray(MESSAGE_BUNDLE_KEY_BOOTSTRAPPING_COME_BACK_COOKIE, cookie);
        msg.getData().putBoolean(MESSAGE_BUNDLE_KEY_BOOTSTRAPPING_IS_COME_BACK_REQUEST,
                comeBackDelayMills > 0);
        mSm.sendMessageDelayed(msg, comeBackDelayMills);
    }

    /**
     * Respond to a bootstrapping request
     */
    private void respondToBootstrappingRequest(int clientId, int sessionId, int peerId,
            int bootstrappingId, boolean accept, int method) {
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_COMMAND);
        msg.arg1 = COMMAND_TYPE_RESPONSE_BOOTSTRAPPING_REQUEST;
        msg.arg2 = clientId;
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_SESSION_ID, sessionId);
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_PEER_ID, peerId);
        msg.getData().putBoolean(MESSAGE_BUNDLE_KEY_BOOTSTRAPPING_ACCEPT, accept);
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_BOOTSTRAPPING_METHOD, method);
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_BOOTSTRAPPING_REQUEST_ID, bootstrappingId);
        mSm.sendMessage(msg);
    }

    /**
     * Enable usage of Aware. Doesn't actually turn on Aware (form clusters) - that
     * only happens when a connection is created.
     */
    public void enableUsage() {
        if (mSettableParameters.get(PARAM_ON_IDLE_DISABLE_AWARE) != 0
                && mPowerManager.isDeviceIdleMode()) {
            if (mVerboseLoggingEnabled) {
                Log.d(TAG, "enableUsage(): while device is in IDLE mode - ignoring");
            }
            return;
        }
        if (!SdkLevel.isAtLeastT() && !mWifiPermissionsUtil.isLocationModeEnabled()) {
            if (mVerboseLoggingEnabled) {
                Log.d(TAG, "enableUsage(): while location is disabled - ignoring");
            }
            return;
        }
        if (mWifiManager.getWifiState() != WifiManager.WIFI_STATE_ENABLED) {
            if (mVerboseLoggingEnabled) {
                Log.d(TAG, "enableUsage(): while Wi-Fi is disabled - ignoring");
            }
            return;
        }
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_COMMAND);
        msg.arg1 = COMMAND_TYPE_ENABLE_USAGE;
        mSm.sendMessage(msg);
    }

    /**
     * Disable usage of Aware. Terminates all existing clients with onAwareDown().
     * @param markAsAvailable mark the Aware service as available to all app or not.
     */
    public void disableUsage(boolean markAsAvailable) {
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_COMMAND);
        msg.arg1 = COMMAND_TYPE_DISABLE_USAGE;
        msg.arg2 = markAsAvailable ? 1 : 0;
        mSm.sendMessage(msg);
    }

    /**
     * Checks whether Aware usage is enabled (not necessarily that Aware is up right
     * now) or disabled.
     *
     * @return A boolean indicating whether Aware usage is enabled (true) or
     *         disabled (false).
     */
    public boolean isUsageEnabled() {
        return mUsageEnabled;
    }

    /**
     * Get the capabilities of the current Aware firmware.
     */
    public void queryCapabilities() {
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_COMMAND);
        msg.arg1 = COMMAND_TYPE_GET_CAPABILITIES;
        mSm.sendMessage(msg);
    }

    /**
     * delete all Aware data path interfaces.
     */
    public void deleteAllDataPathInterfaces() {
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_COMMAND);
        msg.arg1 = COMMAND_TYPE_DELETE_ALL_DATA_PATH_INTERFACES;
        mSm.sendMessage(msg);
    }

    /**
     * Create the specified data-path interface. Doesn't actually creates a data-path.
     */
    public void createDataPathInterface(String interfaceName) {
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_COMMAND);
        msg.arg1 = COMMAND_TYPE_CREATE_DATA_PATH_INTERFACE;
        msg.obj = interfaceName;
        mSm.sendMessage(msg);
    }

    /**
     * Deletes the specified data-path interface.
     */
    public void deleteDataPathInterface(String interfaceName) {
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_COMMAND);
        msg.arg1 = COMMAND_TYPE_DELETE_DATA_PATH_INTERFACE;
        msg.obj = interfaceName;
        mSm.sendMessage(msg);
    }

    /**
     * Command to initiate a data-path (executed by the initiator).
     */
    public void initiateDataPathSetup(WifiAwareNetworkSpecifier networkSpecifier, int peerId,
            int channelRequestType, int channel, byte[] peer, String interfaceName,
            boolean isOutOfBand, byte[] appInfo) {
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_COMMAND);
        msg.arg1 = COMMAND_TYPE_INITIATE_DATA_PATH_SETUP;
        msg.obj = networkSpecifier;
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_PEER_ID, peerId);
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_CHANNEL_REQ_TYPE, channelRequestType);
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_CHANNEL, channel);
        msg.getData().putByteArray(MESSAGE_BUNDLE_KEY_MAC_ADDRESS, peer);
        msg.getData().putString(MESSAGE_BUNDLE_KEY_INTERFACE_NAME, interfaceName);
        msg.getData().putBoolean(MESSAGE_BUNDLE_KEY_OOB, isOutOfBand);
        msg.getData().putByteArray(MESSAGE_BUNDLE_KEY_APP_INFO, appInfo);
        mSm.sendMessage(msg);
    }

    /**
     * Command to respond to the data-path request (executed by the responder).
     */
    public void respondToDataPathRequest(boolean accept, int ndpId, String interfaceName,
            byte[] appInfo, boolean isOutOfBand,
            WifiAwareNetworkSpecifier networkSpecifier) {
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_COMMAND);
        msg.arg1 = COMMAND_TYPE_RESPOND_TO_DATA_PATH_SETUP_REQUEST;
        msg.arg2 = ndpId;
        msg.obj = networkSpecifier;
        msg.getData().putBoolean(MESSAGE_BUNDLE_KEY_ACCEPT_STATE, accept);
        msg.getData().putString(MESSAGE_BUNDLE_KEY_INTERFACE_NAME, interfaceName);
        msg.getData().putByteArray(MESSAGE_BUNDLE_KEY_APP_INFO, appInfo);
        msg.getData().putBoolean(MESSAGE_BUNDLE_KEY_OOB, isOutOfBand);
        mSm.sendMessage(msg);
    }

    /**
     * Command to terminate the specified data-path.
     */
    public void endDataPath(int ndpId) {
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_COMMAND);
        msg.arg1 = COMMAND_TYPE_END_DATA_PATH;
        msg.arg2 = ndpId;
        mSm.sendMessage(msg);
    }

    /**
     * Command to terminate the specified data-path.
     */
    public void endPairing(int pairingId) {
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_COMMAND);
        msg.arg1 = COMMAND_TYPE_END_PAIRING;
        msg.arg2 = pairingId;
        mSm.sendMessage(msg);
    }

    /**
     * Suspend the specified Aware session.
     */
    public void suspend(int clientId, int sessionId) {
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_COMMAND);
        msg.arg1 = COMMAND_TYPE_SUSPEND_SESSION;
        msg.arg2 = clientId;
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_SESSION_ID, sessionId);
        mSm.sendMessage(msg);
    }

    /**
     * Resume the specified (suspended) Aware session.
     */
    public void resume(int clientId, int sessionId) {
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_COMMAND);
        msg.arg1 = COMMAND_TYPE_RESUME_SESSION;
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_SESSION_ID, sessionId);
        msg.arg2 = clientId;
        mSm.sendMessage(msg);
    }

    /**
     * Aware follow-on messages (L2 messages) are queued by the firmware for transmission
     * on-the-air. The firmware has limited queue depth. The host queues all messages and doles
     * them out to the firmware when possible. This command removes the next messages for
     * transmission from the host queue and attempts to send it through the firmware. The queues
     * are inspected when the command is executed - not when the command is placed on the handler
     * (i.e. not evaluated here).
     */
    private void transmitNextMessage() {
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_COMMAND);
        msg.arg1 = COMMAND_TYPE_TRANSMIT_NEXT_MESSAGE;
        mSm.sendMessage(msg);
    }

    /*
     * RESPONSES
     */

    /**
     * Place a callback request on the state machine queue: configuration
     * request completed (successfully).
     */
    public void onConfigSuccessResponse(short transactionId) {
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_RESPONSE);
        msg.arg1 = RESPONSE_TYPE_ON_CONFIG_SUCCESS;
        msg.arg2 = transactionId;
        mSm.sendMessage(msg);
    }

    /**
     * Place a callback request on the state machine queue: configuration
     * request failed.
     */
    public void onConfigFailedResponse(short transactionId, int reason) {
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_RESPONSE);
        msg.arg1 = RESPONSE_TYPE_ON_CONFIG_FAIL;
        msg.arg2 = transactionId;
        msg.obj = reason;
        mSm.sendMessage(msg);
    }

    /**
     * Place a callback request on the stage machine queue: disable request finished
     * (with the provided reason code).
     */
    public void onDisableResponse(short transactionId, int reason) {
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_RESPONSE);
        msg.arg1 = RESPONSE_TYPE_ON_DISABLE;
        msg.arg2 = transactionId;
        msg.obj = reason;
        mSm.sendMessage(msg);
    }

    /**
     * Place a callback request on the state machine queue: session
     * configuration (new or update) request succeeded.
     */
    public void onSessionConfigSuccessResponse(short transactionId, boolean isPublish,
            byte pubSubId) {
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_RESPONSE);
        msg.arg1 = RESPONSE_TYPE_ON_SESSION_CONFIG_SUCCESS;
        msg.arg2 = transactionId;
        msg.obj = pubSubId;
        msg.getData().putBoolean(MESSAGE_BUNDLE_KEY_SESSION_TYPE, isPublish);
        mSm.sendMessage(msg);
    }

    /**
     * Place a callback request on the state machine queue: session
     * configuration (new or update) request failed.
     */
    public void onSessionConfigFailResponse(short transactionId, boolean isPublish, int reason) {
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_RESPONSE);
        msg.arg1 = RESPONSE_TYPE_ON_SESSION_CONFIG_FAIL;
        msg.arg2 = transactionId;
        msg.obj = reason;
        msg.getData().putBoolean(MESSAGE_BUNDLE_KEY_SESSION_TYPE, isPublish);
        mSm.sendMessage(msg);
    }

    /**
     * Place a callback request on the state machine queue: message has been queued successfully.
     */
    public void onMessageSendQueuedSuccessResponse(short transactionId) {
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_RESPONSE);
        msg.arg1 = RESPONSE_TYPE_ON_MESSAGE_SEND_QUEUED_SUCCESS;
        msg.arg2 = transactionId;
        mSm.sendMessage(msg);
    }

    /**
     * Place a callback request on the state machine queue: attempt to queue the message failed.
     */
    public void onMessageSendQueuedFailResponse(short transactionId, int reason) {
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_RESPONSE);
        msg.arg1 = RESPONSE_TYPE_ON_MESSAGE_SEND_QUEUED_FAIL;
        msg.arg2 = transactionId;
        msg.obj = reason;
        mSm.sendMessage(msg);
    }

    /**
     * Place a callback request on the state machine queue: update vendor
     * capabilities of the Aware stack.
     */
    public void onCapabilitiesUpdateResponse(short transactionId,
            Capabilities capabilities) {
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_RESPONSE);
        msg.arg1 = RESPONSE_TYPE_ON_CAPABILITIES_UPDATED;
        msg.arg2 = transactionId;
        msg.obj = capabilities;
        mSm.sendMessage(msg);
    }

    /**
     * Places a callback request on the state machine queue: data-path interface creation command
     * completed.
     */
    public void onCreateDataPathInterfaceResponse(short transactionId, boolean success,
            int reasonOnFailure) {
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_RESPONSE);
        msg.arg1 = RESPONSE_TYPE_ON_CREATE_INTERFACE;
        msg.arg2 = transactionId;
        msg.getData().putBoolean(MESSAGE_BUNDLE_KEY_SUCCESS_FLAG, success);
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_STATUS_CODE, reasonOnFailure);
        mSm.sendMessage(msg);
    }

    /**
     * Places a callback request on the state machine queue: data-path interface deletion command
     * completed.
     */
    public void onDeleteDataPathInterfaceResponse(short transactionId, boolean success,
            int reasonOnFailure) {
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_RESPONSE);
        msg.arg1 = RESPONSE_TYPE_ON_DELETE_INTERFACE;
        msg.arg2 = transactionId;
        msg.getData().putBoolean(MESSAGE_BUNDLE_KEY_SUCCESS_FLAG, success);
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_STATUS_CODE, reasonOnFailure);
        mSm.sendMessage(msg);
    }

    /**
     * Response from firmware to initiateDataPathSetup(...). Indicates that command has started
     * succesfully (not completed!).
     */
    public void onInitiateDataPathResponseSuccess(short transactionId, int ndpId) {
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_RESPONSE);
        msg.arg1 = RESPONSE_TYPE_ON_INITIATE_DATA_PATH_SUCCESS;
        msg.arg2 = transactionId;
        msg.obj = ndpId;
        mSm.sendMessage(msg);
    }

    /**
     * Response from firmware to initiateDataPathSetup(...).
     * Indicates that command has failed.
     */
    public void onInitiateDataPathResponseFail(short transactionId, int reason) {
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_RESPONSE);
        msg.arg1 = RESPONSE_TYPE_ON_INITIATE_DATA_PATH_FAIL;
        msg.arg2 = transactionId;
        msg.obj = reason;
        mSm.sendMessage(msg);
    }

    /**
     * Response from firmware to initiatePairing(...). Indicates that command has started
     * successfully (not completed!).
     */
    public void onInitiatePairingResponseSuccess(short transactionId, int pairId) {
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_RESPONSE);
        msg.arg1 = RESPONSE_TYPE_ON_INITIATE_PAIRING_SUCCESS;
        msg.arg2 = transactionId;
        msg.obj = pairId;
        mSm.sendMessage(msg);
    }

    /**
     * Response from firmware to initiatePairing(...).
     * Indicates that command has failed.
     */
    public void onInitiatePairingResponseFail(short transactionId, int reason) {
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_RESPONSE);
        msg.arg1 = RESPONSE_TYPE_ON_INITIATE_PAIRING_FAIL;
        msg.arg2 = transactionId;
        msg.obj = reason;
        mSm.sendMessage(msg);
    }

    /**
     * Response from firmware to respondToPairingRequest(...). Indicates that command has started
     * successfully (not completed!).
     */
    public void onRespondToPairingIndicationResponseSuccess(short transactionId) {
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_RESPONSE);
        msg.arg1 = RESPONSE_TYPE_ON_RESPONSE_PAIRING_SUCCESS;
        msg.arg2 = transactionId;
        mSm.sendMessage(msg);
    }

    /**
     * Response from firmware to respondToPairingRequest(...).
     * Indicates that command has failed.
     */
    public void onRespondToPairingIndicationResponseFail(short transactionId, int reason) {
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_RESPONSE);
        msg.arg1 = RESPONSE_TYPE_ON_RESPONSE_PAIRING_FAIL;
        msg.arg2 = transactionId;
        msg.obj = reason;
        mSm.sendMessage(msg);
    }

    /**
     * Response from firmware to initiateDataPathSetup(...). Indicates that command has started
     * successfully (not completed!).
     */
    public void onInitiateBootStrappingResponseSuccess(short transactionId, int ndpId) {
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_RESPONSE);
        msg.arg1 = RESPONSE_TYPE_ON_INITIATE_BOOTSTRAPPING_SUCCESS;
        msg.arg2 = transactionId;
        msg.obj = ndpId;
        mSm.sendMessage(msg);
    }

    /**
     * Response from firmware to initiateDataPathSetup(...).
     * Indicates that command has failed.
     */
    public void onInitiateBootStrappingResponseFail(short transactionId, int reason) {
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_RESPONSE);
        msg.arg1 = RESPONSE_TYPE_ON_INITIATE_BOOTSTRAPPING_FAIL;
        msg.arg2 = transactionId;
        msg.obj = reason;
        mSm.sendMessage(msg);
    }

    /**
     * Response from firmware to initiateDataPathSetup(...). Indicates that command has started
     * successfully (not completed!).
     */
    public void onRespondToBootstrappingIndicationResponseSuccess(short transactionId) {
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_RESPONSE);
        msg.arg1 = RESPONSE_TYPE_ON_RESPONSE_BOOTSTRAPPING_SUCCESS;
        msg.arg2 = transactionId;
        mSm.sendMessage(msg);
    }

    /**
     * Response from firmware to initiateDataPathSetup(...).
     * Indicates that command has failed.
     */
    public void onRespondToBootstrappingIndicationResponseFail(short transactionId, int reason) {
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_RESPONSE);
        msg.arg1 = RESPONSE_TYPE_ON_RESPONSE_BOOTSTRAPPING_FAIL;
        msg.arg2 = transactionId;
        msg.obj = reason;
        mSm.sendMessage(msg);
    }

    /**
     * Response from firmware to
     * {@link #respondToDataPathRequest(boolean, int, String, byte[], boolean, WifiAwareNetworkSpecifier)}
     */
    public void onRespondToDataPathSetupRequestResponse(short transactionId, boolean success,
            int reasonOnFailure) {
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_RESPONSE);
        msg.arg1 = RESPONSE_TYPE_ON_RESPOND_TO_DATA_PATH_SETUP_REQUEST;
        msg.arg2 = transactionId;
        msg.getData().putBoolean(MESSAGE_BUNDLE_KEY_SUCCESS_FLAG, success);
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_STATUS_CODE, reasonOnFailure);
        mSm.sendMessage(msg);
    }

    /**
     * Response from firmware to {@link #endDataPath(int)}.
     */
    public void onEndDataPathResponse(short transactionId, boolean success, int reasonOnFailure) {
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_RESPONSE);
        msg.arg1 = RESPONSE_TYPE_ON_END_DATA_PATH;
        msg.arg2 = transactionId;
        msg.getData().putBoolean(MESSAGE_BUNDLE_KEY_SUCCESS_FLAG, success);
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_STATUS_CODE, reasonOnFailure);
        mSm.sendMessage(msg);
    }

    /**
     * Response from firmware to {@link #endPairing(int)}.
     */
    public void onEndPairingResponse(short transactionId, boolean success, int reasonOnFailure) {
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_RESPONSE);
        msg.arg1 = RESPONSE_TYPE_ON_END_PAIRING;
        msg.arg2 = transactionId;
        msg.getData().putBoolean(MESSAGE_BUNDLE_KEY_SUCCESS_FLAG, success);
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_STATUS_CODE, reasonOnFailure);
        mSm.sendMessage(msg);
    }

    /**
     * Response from firmware to {@link #suspend(int, int)}.
     */
    public void onSuspendResponse(short transactionId, int status) {
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_RESPONSE);
        msg.arg1 = RESPONSE_TYPE_ON_SUSPEND;
        msg.arg2 = transactionId;
        msg.getData().putBoolean(MESSAGE_BUNDLE_KEY_SUCCESS_FLAG, status == NanStatusCode.SUCCESS);
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_STATUS_CODE, status);
        mSm.sendMessage(msg);
    }

    /**
     * Response from firmware to {@link #resume(int, int)}.
     */
    public void onResumeResponse(short transactionId, int status) {
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_RESPONSE);
        msg.arg1 = RESPONSE_TYPE_ON_RESUME;
        msg.arg2 = transactionId;
        msg.getData().putBoolean(MESSAGE_BUNDLE_KEY_SUCCESS_FLAG, status == NanStatusCode.SUCCESS);
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_STATUS_CODE, status);
        mSm.sendMessage(msg);
    }

    /*
     * NOTIFICATIONS
     */

    /**
     * Place a callback request on the state machine queue: the discovery
     * interface has changed.
     */
    public void onInterfaceAddressChangeNotification(byte[] mac) {
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_NOTIFICATION);
        msg.arg1 = NOTIFICATION_TYPE_INTERFACE_CHANGE;
        msg.obj = mac;
        mSm.sendMessage(msg);
    }

    /**
     * Place a callback request on the state machine queue: the cluster
     * membership has changed (e.g. due to starting a new cluster or joining
     * another cluster).
     */
    public void onClusterChangeNotification(
            @IdentityChangedListener.ClusterChangeEvent int clusterEventType,
            byte[] clusterId) {
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_NOTIFICATION);
        msg.arg1 = NOTIFICATION_TYPE_CLUSTER_CHANGE;
        msg.arg2 = clusterEventType;
        msg.obj = clusterId;
        mSm.sendMessage(msg);
    }

    /**
     * Place a callback request on the state machine queue: a discovery match
     * has occurred - e.g. our subscription discovered someone else publishing a
     * matching service (to the one we were looking for).
     */
    public void onMatchNotification(int pubSubId, int requestorInstanceId, byte[] peerMac,
            byte[] serviceSpecificInfo, byte[] matchFilter, int rangingIndication, int rangeMm,
            byte[] scid, int peerCipherSuite, byte[] nonce, byte[] tag,
            AwarePairingConfig pairingConfig, @Nullable List<OuiKeyedData> vendorData) {
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_NOTIFICATION);
        msg.arg1 = NOTIFICATION_TYPE_MATCH;
        msg.arg2 = pubSubId;
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_REQ_INSTANCE_ID, requestorInstanceId);
        msg.getData().putByteArray(MESSAGE_BUNDLE_KEY_MAC_ADDRESS, peerMac);
        msg.getData().putByteArray(MESSAGE_BUNDLE_KEY_SSI_DATA, serviceSpecificInfo);
        msg.getData().putByteArray(MESSAGE_BUNDLE_KEY_FILTER_DATA, matchFilter);
        msg.getData().putInt(MESSAGE_RANGING_INDICATION, rangingIndication);
        msg.getData().putInt(MESSAGE_RANGE_MM, rangeMm);
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_CIPHER_SUITE, peerCipherSuite);
        msg.getData().putByteArray(MESSAGE_BUNDLE_KEY_SCID, scid);
        msg.getData().putByteArray(MESSAGE_BUNDLE_KEY_NONCE, nonce);
        msg.getData().putByteArray(MESSAGE_BUNDLE_KEY_TAG, tag);
        msg.getData().putParcelable(MESSAGE_BUNDLE_KEY_PAIRING_CONFIG, pairingConfig);
        msg.getData().putParcelableArrayList(MESSAGE_BUNDLE_KEY_VENDOR_DATA,
                vendorData != null ? new ArrayList<>(vendorData) : new ArrayList<>());
        mSm.sendMessage(msg);
    }

    /**
     * Place a callback request on the state machine queue: a discovered session
     * has expired - e.g. some discovered peer is no longer visible.
     */
    public void onMatchExpiredNotification(int pubSubId, int requestorInstanceId) {
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_NOTIFICATION);
        msg.arg1 = NOTIFICATION_TYPE_MATCH_EXPIRED;
        msg.arg2 = pubSubId;
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_REQ_INSTANCE_ID, requestorInstanceId);
        mSm.sendMessage(msg);
    }

    /**
     * Place a callback request on the state machine queue: a session (publish
     * or subscribe) has terminated (per plan or due to an error).
     */
    public void onSessionTerminatedNotification(int pubSubId, int reason, boolean isPublish) {
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_NOTIFICATION);
        msg.arg1 = NOTIFICATION_TYPE_SESSION_TERMINATED;
        msg.arg2 = pubSubId;
        msg.obj = reason;
        msg.getData().putBoolean(MESSAGE_BUNDLE_KEY_SESSION_TYPE, isPublish);
        mSm.sendMessage(msg);
    }

    /**
     * Place a callback request on the state machine queue: a message has been
     * received as part of a discovery session.
     */
    public void onMessageReceivedNotification(int pubSubId, int requestorInstanceId, byte[] peerMac,
            byte[] message) {
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_NOTIFICATION);
        msg.arg1 = NOTIFICATION_TYPE_MESSAGE_RECEIVED;
        msg.arg2 = pubSubId;
        msg.obj = requestorInstanceId;
        msg.getData().putByteArray(MESSAGE_BUNDLE_KEY_MAC_ADDRESS, peerMac);
        msg.getData().putByteArray(MESSAGE_BUNDLE_KEY_MESSAGE_DATA, message);
        mSm.sendMessage(msg);
    }

    /**
     * Place a callback request on the state machine queue: Aware is going down.
     */
    public void onAwareDownNotification(int reason) {
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_NOTIFICATION);
        msg.arg1 = NOTIFICATION_TYPE_AWARE_DOWN;
        msg.arg2 = reason;
        mSm.sendMessage(msg);
    }

    /**
     * Notification that a message has been sent successfully (i.e. an ACK has been received).
     */
    public void onMessageSendSuccessNotification(short transactionId) {
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_NOTIFICATION);
        msg.arg1 = NOTIFICATION_TYPE_ON_MESSAGE_SEND_SUCCESS;
        msg.arg2 = transactionId;
        mSm.sendMessage(msg);
    }

    /**
     * Notification that a message transmission has failed due to the indicated reason - e.g. no ACK
     * was received.
     */
    public void onMessageSendFailNotification(short transactionId, int reason) {
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_NOTIFICATION);
        msg.arg1 = NOTIFICATION_TYPE_ON_MESSAGE_SEND_FAIL;
        msg.arg2 = transactionId;
        msg.obj = reason;
        mSm.sendMessage(msg);
    }

    /**
     * Place a callback request on the state machine queue: data-path request (from peer) received.
     */
    public void onDataPathRequestNotification(int pubSubId, byte[] mac, int ndpId, byte[] message) {
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_NOTIFICATION);
        msg.arg1 = NOTIFICATION_TYPE_ON_DATA_PATH_REQUEST;
        msg.arg2 = pubSubId;
        msg.obj = ndpId;
        msg.getData().putByteArray(MESSAGE_BUNDLE_KEY_MAC_ADDRESS, mac);
        msg.getData().putByteArray(MESSAGE_BUNDLE_KEY_MESSAGE, message);
        mSm.sendMessage(msg);
    }

    /**
     * Place a callback request on the state machine queue: data-path confirmation received - i.e.
     * data-path is now up.
     */
    public void onDataPathConfirmNotification(int ndpId, byte[] mac, boolean accept, int reason,
            byte[] message, List<WifiAwareChannelInfo> channelInfo) {
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_NOTIFICATION);
        msg.arg1 = NOTIFICATION_TYPE_ON_DATA_PATH_CONFIRM;
        msg.arg2 = ndpId;
        msg.getData().putByteArray(MESSAGE_BUNDLE_KEY_MAC_ADDRESS, mac);
        msg.getData().putBoolean(MESSAGE_BUNDLE_KEY_SUCCESS_FLAG, accept);
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_STATUS_CODE, reason);
        msg.getData().putByteArray(MESSAGE_BUNDLE_KEY_MESSAGE_DATA, message);
        msg.obj = channelInfo;
        mSm.sendMessage(msg);
    }

    /**
     * Place a callback request on the state machine queue: the specified data-path has been
     * terminated.
     */
    public void onDataPathEndNotification(int ndpId) {
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_NOTIFICATION);
        msg.arg1 = NOTIFICATION_TYPE_ON_DATA_PATH_END;
        msg.arg2 = ndpId;
        mSm.sendMessage(msg);
    }

    /**
     * Place a callback request on the state machine queue: schedule update for the specified
     * data-paths.
     */
    public void onDataPathScheduleUpdateNotification(byte[] peerMac, ArrayList<Integer> ndpIds,
            List<WifiAwareChannelInfo> channelInfo) {
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_NOTIFICATION);
        msg.arg1 = NOTIFICATION_TYPE_ON_DATA_PATH_SCHED_UPDATE;
        msg.getData().putByteArray(MESSAGE_BUNDLE_KEY_MAC_ADDRESS, peerMac);
        msg.getData().putIntegerArrayList(MESSAGE_BUNDLE_KEY_NDP_IDS, ndpIds);
        msg.obj = channelInfo;
        mSm.sendMessage(msg);
    }

    /**
     * Place a callback request on the state machine queue: NAN Pairing request (from peer)
     * received.
     */
    public void onPairingRequestNotification(int pubSubId, int requestorInstanceId, byte[] mac,
            int pairingId, int requestType, boolean enableCache, byte[] nonce, byte[] tag) {
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_NOTIFICATION);
        msg.arg1 = NOTIFICATION_TYPE_ON_PAIRING_REQUEST;
        msg.arg2 = pubSubId;
        msg.getData().putByteArray(MESSAGE_BUNDLE_KEY_MAC_ADDRESS, mac);
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_PAIRING_REQUEST_ID, pairingId);
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_REQ_INSTANCE_ID, requestorInstanceId);
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_PAIRING_TYPE, requestType);
        msg.getData().putBoolean(MESSAGE_BUNDLE_KEY_PAIRING_CACHE, enableCache);
        msg.getData().putByteArray(MESSAGE_BUNDLE_KEY_NONCE, nonce);
        msg.getData().putByteArray(MESSAGE_BUNDLE_KEY_TAG, tag);
        mSm.sendMessage(msg);
    }

    /**
     * Place a callback request on the state machine queue: NAN Pairing confirm received.
     */
    public void onPairingConfirmNotification(int pairingId, boolean accept, int reason,
            int requestType, boolean enableCache,
            PairingSecurityAssociationInfo npksa) {
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_NOTIFICATION);
        msg.arg1 = NOTIFICATION_TYPE_ON_PAIRING_CONFIRM;
        msg.arg2 = reason;
        msg.obj = npksa;
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_PAIRING_REQUEST_ID, pairingId);
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_PAIRING_TYPE, requestType);
        msg.getData().putBoolean(MESSAGE_BUNDLE_KEY_PAIRING_CACHE, enableCache);
        msg.getData().putBoolean(MESSAGE_BUNDLE_KEY_PAIRING_ACCEPT, accept);
        mSm.sendMessage(msg);
    }

    /**
     * Place a callback request on the state machine queue: bootstrapping request (from peer)
     * received.
     */
    public void onBootstrappingRequestNotification(int pubSubId, int requestorInstanceId,
            byte[] mac, int bootstrappingInstanceId, int method) {
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_NOTIFICATION);
        msg.arg1 = NOTIFICATION_TYPE_ON_BOOTSTRAPPING_REQUEST;
        msg.arg2 = pubSubId;
        msg.getData().putByteArray(MESSAGE_BUNDLE_KEY_MAC_ADDRESS, mac);
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_BOOTSTRAPPING_REQUEST_ID, bootstrappingInstanceId);
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_REQ_INSTANCE_ID, requestorInstanceId);
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_BOOTSTRAPPING_METHOD, method);
        mSm.sendMessage(msg);
    }

    /**
     * Place a callback request on the state machine queue: bootstrapping confirm received.
     */
    public void onBootstrappingConfirmNotification(int bootstrappingId, int responseCode,
            int reason, int comebackDelay, byte[] cookie) {
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_NOTIFICATION);
        msg.arg1 = NOTIFICATION_TYPE_ON_BOOTSTRAPPING_CONFIRM;
        msg.arg2 = reason;
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_BOOTSTRAPPING_REQUEST_ID, bootstrappingId);
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_BOOTSTRAPPING_RESPONSE_CODE, responseCode);
        msg.getData().putInt(MESSAGE_BUNDLE_KEY_BOOTSTRAPPING_COME_BACK_DELAY, comebackDelay);
        msg.getData().putByteArray(MESSAGE_BUNDLE_KEY_BOOTSTRAPPING_COME_BACK_COOKIE, cookie);
        mSm.sendMessage(msg);
    }

    /**
     * Place a callback request on the state machine queue: suspension mode changed.
     */
    public void onSuspensionModeChangedNotification(boolean isSuspended) {
        Message msg = mSm.obtainMessage(MESSAGE_TYPE_NOTIFICATION);
        msg.arg1 = NOTIFICATION_TYPE_ON_SUSPENSION_MODE_CHANGED;
        msg.getData().putBoolean(MESSAGE_BUNDLE_KEY_SUSPENSION_MODE, isSuspended);
        mSm.sendMessage(msg);
    }

    /**
     * State machine.
     */
    @VisibleForTesting
    class WifiAwareStateMachine extends StateMachine {
        private static final int TRANSACTION_ID_IGNORE = 0;

        private final DefaultState mDefaultState;
        private final WaitState mWaitState;
        private final WaitForResponseState mWaitForResponseState;
        private final WaitingState mWaitingState = new WaitingState(this);

        private short mNextTransactionId = 1;
        public int mNextSessionId = 1;

        private Message mCurrentCommand;
        private short mCurrentTransactionId = TRANSACTION_ID_IGNORE;

        private static final long AWARE_SEND_MESSAGE_TIMEOUT = 10_000;
        private static final int MESSAGE_QUEUE_DEPTH_PER_UID = 50;
        private int mSendArrivalSequenceCounter = 0;
        private boolean mSendQueueBlocked = false;
        private final SparseArray<Message> mHostQueuedSendMessages = new SparseArray<>();
        private final Map<Short, Message> mFwQueuedSendMessages = new LinkedHashMap<>();
        private final WakeupMessage mSendMessageTimeoutMessage = new WakeupMessage(mContext,
                getHandler(), HAL_SEND_MESSAGE_TIMEOUT_TAG, MESSAGE_TYPE_SEND_MESSAGE_TIMEOUT);

        private static final long AWARE_WAIT_FOR_DP_CONFIRM_TIMEOUT = 20_000;
        private static final long AWARE_WAIT_FOR_PAIRING_CONFIRM_TIMEOUT = 20_000;
        private final SparseArray<WakeupMessage>
                mDataPathConfirmTimeoutMessages = new SparseArray<>();
        private final SparseArray<WakeupMessage>
                mPairingConfirmTimeoutMessages = new SparseArray<>();
        private final SparseArray<WakeupMessage>
                mBootstrappingConfirmTimeoutMessages = new SparseArray<>();

        WifiAwareStateMachine(String name, Looper looper) {
            super(name, looper);
            final int threshold = mContext.getResources().getInteger(
                    R.integer.config_wifiConfigurationWifiRunnerThresholdInMs);
            mDefaultState = new DefaultState(threshold);
            mWaitState = new WaitState(threshold);
            mWaitForResponseState = new WaitForResponseState(threshold);
            addState(mDefaultState);
            /* --> */ addState(mWaitState, mDefaultState);
            /* ----> */ addState(mWaitingState, mWaitState);
            /* --> */ addState(mWaitForResponseState, mDefaultState);

            setInitialState(mWaitState);
        }

        @Override
        protected String getWhatToString(int what) {
            return switch (what) {
                case COMMAND_TYPE_CONNECT -> "COMMAND_TYPE_CONNECT";
                case COMMAND_TYPE_DISCONNECT -> "COMMAND_TYPE_DISCONNECT";
                case COMMAND_TYPE_TERMINATE_SESSION -> "COMMAND_TYPE_TERMINATE_SESSION";
                case COMMAND_TYPE_PUBLISH -> "COMMAND_TYPE_PUBLISH";
                case COMMAND_TYPE_UPDATE_PUBLISH -> "COMMAND_TYPE_UPDATE_PUBLISH";
                case COMMAND_TYPE_SUBSCRIBE -> "COMMAND_TYPE_SUBSCRIBE";
                case COMMAND_TYPE_UPDATE_SUBSCRIBE -> "COMMAND_TYPE_UPDATE_SUBSCRIBE";
                case COMMAND_TYPE_ENQUEUE_SEND_MESSAGE -> "COMMAND_TYPE_ENQUEUE_SEND_MESSAGE";
                case COMMAND_TYPE_ENABLE_USAGE -> "COMMAND_TYPE_ENABLE_USAGE";
                case COMMAND_TYPE_DISABLE_USAGE -> "COMMAND_TYPE_DISABLE_USAGE";
                case COMMAND_TYPE_GET_CAPABILITIES -> "COMMAND_TYPE_GET_CAPABILITIES";
                case COMMAND_TYPE_DELETE_ALL_DATA_PATH_INTERFACES
                        -> "COMMAND_TYPE_DELETE_ALL_DATA_PATH_INTERFACES";
                case COMMAND_TYPE_CREATE_DATA_PATH_INTERFACE
                        -> "COMMAND_TYPE_CREATE_DATA_PATH_INTERFACE";
                case COMMAND_TYPE_DELETE_DATA_PATH_INTERFACE
                        -> "COMMAND_TYPE_DELETE_DATA_PATH_INTERFACE";
                case COMMAND_TYPE_INITIATE_DATA_PATH_SETUP
                        -> "COMMAND_TYPE_INITIATE_DATA_PATH_SETUP";
                case COMMAND_TYPE_RESPOND_TO_DATA_PATH_SETUP_REQUEST
                        -> "COMMAND_TYPE_RESPOND_TO_DATA_PATH_SETUP_REQUEST";
                case COMMAND_TYPE_END_DATA_PATH -> "COMMAND_TYPE_END_DATA_PATH";
                case COMMAND_TYPE_TRANSMIT_NEXT_MESSAGE -> "COMMAND_TYPE_TRANSMIT_NEXT_MESSAGE";
                case COMMAND_TYPE_RECONFIGURE -> "COMMAND_TYPE_RECONFIGURE";
                case COMMAND_TYPE_DELAYED_INITIALIZATION -> "COMMAND_TYPE_DELAYED_INITIALIZATION";
                case COMMAND_TYPE_GET_AWARE -> "COMMAND_TYPE_GET_AWARE";
                case COMMAND_TYPE_RELEASE_AWARE -> "COMMAND_TYPE_RELEASE_AWARE";
                case COMMAND_TYPE_DISABLE -> "COMMAND_TYPE_DISABLE";
                case COMMAND_TYPE_INITIATE_PAIRING_REQUEST
                        -> "COMMAND_TYPE_INITIATE_PAIRING_REQUEST";
                case COMMAND_TYPE_RESPONSE_PAIRING_REQUEST
                        -> "COMMAND_TYPE_RESPONSE_PAIRING_REQUEST";
                case COMMAND_TYPE_INITIATE_BOOTSTRAPPING_REQUEST
                        -> "COMMAND_TYPE_INITIATE_BOOTSTRAPPING_REQUEST";
                case COMMAND_TYPE_RESPONSE_BOOTSTRAPPING_REQUEST
                        -> "COMMAND_TYPE_RESPONSE_BOOTSTRAPPING_REQUEST";
                case COMMAND_TYPE_SUSPEND_SESSION -> "COMMAND_TYPE_SUSPEND_SESSION";
                case COMMAND_TYPE_RESUME_SESSION -> "COMMAND_TYPE_RESUME_SESSION";
                case COMMAND_TYPE_END_PAIRING -> "COMMAND_TYPE_END_PAIRING";

                case RESPONSE_TYPE_ON_CONFIG_SUCCESS -> "RESPONSE_TYPE_ON_CONFIG_SUCCESS";
                case RESPONSE_TYPE_ON_CONFIG_FAIL -> "RESPONSE_TYPE_ON_CONFIG_FAIL";
                case RESPONSE_TYPE_ON_SESSION_CONFIG_SUCCESS
                        -> "RESPONSE_TYPE_ON_SESSION_CONFIG_SUCCESS";
                case RESPONSE_TYPE_ON_SESSION_CONFIG_FAIL -> "RESPONSE_TYPE_ON_SESSION_CONFIG_FAIL";
                case RESPONSE_TYPE_ON_MESSAGE_SEND_QUEUED_SUCCESS
                        -> "RESPONSE_TYPE_ON_MESSAGE_SEND_QUEUED_SUCCESS";
                case RESPONSE_TYPE_ON_MESSAGE_SEND_QUEUED_FAIL
                        -> "RESPONSE_TYPE_ON_MESSAGE_SEND_QUEUED_FAIL";
                case RESPONSE_TYPE_ON_CAPABILITIES_UPDATED
                        -> "RESPONSE_TYPE_ON_CAPABILITIES_UPDATED";
                case RESPONSE_TYPE_ON_CREATE_INTERFACE -> "RESPONSE_TYPE_ON_CREATE_INTERFACE";
                case RESPONSE_TYPE_ON_DELETE_INTERFACE -> "RESPONSE_TYPE_ON_DELETE_INTERFACE";
                case RESPONSE_TYPE_ON_INITIATE_DATA_PATH_SUCCESS
                        -> "RESPONSE_TYPE_ON_INITIATE_DATA_PATH_SUCCESS";
                case RESPONSE_TYPE_ON_INITIATE_DATA_PATH_FAIL
                        -> "RESPONSE_TYPE_ON_INITIATE_DATA_PATH_FAIL";
                case RESPONSE_TYPE_ON_RESPOND_TO_DATA_PATH_SETUP_REQUEST
                        -> "RESPONSE_TYPE_ON_RESPOND_TO_DATA_PATH_SETUP_REQUEST";
                case RESPONSE_TYPE_ON_END_DATA_PATH -> "RESPONSE_TYPE_ON_END_DATA_PATH";
                case RESPONSE_TYPE_ON_DISABLE -> "RESPONSE_TYPE_ON_DISABLE";
                case RESPONSE_TYPE_ON_INITIATE_PAIRING_SUCCESS
                        -> "RESPONSE_TYPE_ON_INITIATE_PAIRING_SUCCESS";
                case RESPONSE_TYPE_ON_INITIATE_PAIRING_FAIL
                        -> "RESPONSE_TYPE_ON_INITIATE_PAIRING_FAIL";
                case RESPONSE_TYPE_ON_RESPONSE_PAIRING_SUCCESS
                        -> "RESPONSE_TYPE_ON_RESPONSE_PAIRING_SUCCESS";
                case RESPONSE_TYPE_ON_RESPONSE_PAIRING_FAIL
                        -> "RESPONSE_TYPE_ON_RESPONSE_PAIRING_FAIL";
                case RESPONSE_TYPE_ON_INITIATE_BOOTSTRAPPING_SUCCESS
                        -> "RESPONSE_TYPE_ON_INITIATE_BOOTSTRAPPING_SUCCESS";
                case RESPONSE_TYPE_ON_INITIATE_BOOTSTRAPPING_FAIL
                        -> "RESPONSE_TYPE_ON_INITIATE_BOOTSTRAPPING_FAIL";
                case RESPONSE_TYPE_ON_RESPONSE_BOOTSTRAPPING_SUCCESS
                        -> "RESPONSE_TYPE_ON_RESPONSE_BOOTSTRAPPING_SUCCESS";
                case RESPONSE_TYPE_ON_RESPONSE_BOOTSTRAPPING_FAIL
                        -> "RESPONSE_TYPE_ON_RESPONSE_BOOTSTRAPPING_FAIL";
                case RESPONSE_TYPE_ON_SUSPEND -> "RESPONSE_TYPE_ON_SUSPEND";
                case RESPONSE_TYPE_ON_RESUME -> "RESPONSE_TYPE_ON_RESUME";
                case RESPONSE_TYPE_ON_END_PAIRING -> "RESPONSE_TYPE_ON_END_PAIRING";

                case NOTIFICATION_TYPE_INTERFACE_CHANGE -> "NOTIFICATION_TYPE_INTERFACE_CHANGE";
                case NOTIFICATION_TYPE_CLUSTER_CHANGE -> "NOTIFICATION_TYPE_CLUSTER_CHANGE";
                case NOTIFICATION_TYPE_MATCH -> "NOTIFICATION_TYPE_MATCH";
                case NOTIFICATION_TYPE_SESSION_TERMINATED -> "NOTIFICATION_TYPE_SESSION_TERMINATED";
                case NOTIFICATION_TYPE_MESSAGE_RECEIVED -> "NOTIFICATION_TYPE_MESSAGE_RECEIVED";
                case NOTIFICATION_TYPE_AWARE_DOWN -> "NOTIFICATION_TYPE_AWARE_DOWN";
                case NOTIFICATION_TYPE_ON_MESSAGE_SEND_SUCCESS
                        -> "NOTIFICATION_TYPE_ON_MESSAGE_SEND_SUCCESS";
                case NOTIFICATION_TYPE_ON_MESSAGE_SEND_FAIL
                        -> "NOTIFICATION_TYPE_ON_MESSAGE_SEND_FAIL";
                case NOTIFICATION_TYPE_ON_DATA_PATH_REQUEST
                        -> "NOTIFICATION_TYPE_ON_DATA_PATH_REQUEST";
                case NOTIFICATION_TYPE_ON_DATA_PATH_CONFIRM
                        -> "NOTIFICATION_TYPE_ON_DATA_PATH_CONFIRM";
                case NOTIFICATION_TYPE_ON_DATA_PATH_END -> "NOTIFICATION_TYPE_ON_DATA_PATH_END";
                case NOTIFICATION_TYPE_ON_DATA_PATH_SCHED_UPDATE
                        -> "NOTIFICATION_TYPE_ON_DATA_PATH_SCHED_UPDATE";
                case NOTIFICATION_TYPE_MATCH_EXPIRED -> "NOTIFICATION_TYPE_MATCH_EXPIRED";
                case NOTIFICATION_TYPE_ON_PAIRING_REQUEST -> "NOTIFICATION_TYPE_ON_PAIRING_REQUEST";
                case NOTIFICATION_TYPE_ON_PAIRING_CONFIRM -> "NOTIFICATION_TYPE_ON_PAIRING_CONFIRM";
                case NOTIFICATION_TYPE_ON_BOOTSTRAPPING_REQUEST
                        -> "NOTIFICATION_TYPE_ON_BOOTSTRAPPING_REQUEST";
                case NOTIFICATION_TYPE_ON_BOOTSTRAPPING_CONFIRM
                        -> "NOTIFICATION_TYPE_ON_BOOTSTRAPPING_CONFIRM";
                case NOTIFICATION_TYPE_ON_SUSPENSION_MODE_CHANGED
                        -> "NOTIFICATION_TYPE_ON_SUSPENSION_MODE_CHANGED";
                case RunnerState.STATE_ENTER_CMD -> "Enter";
                case RunnerState.STATE_EXIT_CMD -> "Exit";
                case MESSAGE_TYPE_COMMAND -> "MESSAGE_TYPE_COMMAND";
                case MESSAGE_TYPE_RESPONSE -> "MESSAGE_TYPE_RESPONSE";
                case MESSAGE_TYPE_NOTIFICATION -> "MESSAGE_TYPE_NOTIFICATION";
                case MESSAGE_TYPE_RESPONSE_TIMEOUT -> "MESSAGE_TYPE_RESPONSE_TIMEOUT";
                case MESSAGE_TYPE_SEND_MESSAGE_TIMEOUT -> "MESSAGE_TYPE_SEND_MESSAGE_TIMEOUT";
                case MESSAGE_TYPE_DATA_PATH_TIMEOUT -> "MESSAGE_TYPE_DATA_PATH_TIMEOUT";
                case MESSAGE_TYPE_PAIRING_TIMEOUT -> "MESSAGE_TYPE_PAIRING_TIMEOUT";
                case MESSAGE_TYPE_BOOTSTRAPPING_TIMEOUT -> "MESSAGE_TYPE_BOOTSTRAPPING_TIMEOUT";
                default -> {
                    Log.e(TAG, "unknown message what: " + what);
                    yield "what:" + what;
                }
            };
        }

        public void onAwareDownCleanupSendQueueState() {
            mSendQueueBlocked = false;
            mHostQueuedSendMessages.clear();
            mFwQueuedSendMessages.clear();
        }

        private class DefaultState extends RunnerState {

            DefaultState(int threshold) {
                super(threshold, mWifiInjector.getWifiHandlerLocalLog());
            }

            @Override
            public String getMessageLogRec(int what) {
                return WifiAwareStateManager.class.getSimpleName() + "."
                        + DefaultState.class.getSimpleName() + "." + getWhatToString(what);
            }

            @Override
            public void enterImpl() {
            }

            @Override
            public void exitImpl() {
            }
            @Override
            public boolean processMessageImpl(Message msg) {
                if (mVdbg) {
                    Log.v(TAG, getName() + msg.toString());
                }

                switch (msg.what) {
                    case MESSAGE_TYPE_NOTIFICATION:
                        processNotification(msg);
                        return HANDLED;
                    case MESSAGE_TYPE_SEND_MESSAGE_TIMEOUT:
                        processSendMessageTimeout();
                        return HANDLED;
                    case MESSAGE_TYPE_DATA_PATH_TIMEOUT: {
                        int ndpId = msg.arg1;

                        if (mVerboseLoggingEnabled) {
                            Log.v(TAG, "MESSAGE_TYPE_DATA_PATH_TIMEOUT: ndpId="
                                    + ndpId);
                        }

                        mDataPathMgr.handleDataPathTimeout(ndpId);
                        mDataPathConfirmTimeoutMessages.remove(ndpId);
                        return HANDLED;
                    }
                    case MESSAGE_TYPE_PAIRING_TIMEOUT: {
                        int pairId = msg.arg1;
                        endPairing(pairId);
                        onPairingConfirmNotification(pairId, false,
                                NanStatusCode.INTERNAL_FAILURE, msg.arg2, false, null);
                        mPairingConfirmTimeoutMessages.remove(pairId);
                        return HANDLED;
                    }
                    case MESSAGE_TYPE_BOOTSTRAPPING_TIMEOUT: {
                        int bootstrappingId = msg.arg1;
                        onBootstrappingConfirmNotification(bootstrappingId,
                                NAN_BOOTSTRAPPING_REJECT, NanStatusCode.INTERNAL_FAILURE, 0, null);
                        mBootstrappingConfirmTimeoutMessages.remove(bootstrappingId);
                        return HANDLED;
                    }
                    default:
                        /* fall-through */
                }

                Log.wtf(TAG,
                        "DefaultState: should not get non-NOTIFICATION in this state: msg=" + msg);
                return NOT_HANDLED;
            }
        }

        private class WaitState extends RunnerState {

            WaitState(int threshold) {
                super(threshold, mWifiInjector.getWifiHandlerLocalLog());
            }

            @Override
            public String getMessageLogRec(int what) {
                return WifiAwareStateManager.class.getSimpleName() + "."
                        + WaitState.class.getSimpleName() + "." + getWhatToString(what);
            }

            @Override
            public void enterImpl() {
            }

            @Override
            public void exitImpl() {
            }
            @Override
            public boolean processMessageImpl(Message msg) {
                if (mVdbg) {
                    Log.v(TAG, getName() + msg.toString());
                }

                switch (msg.what) {
                    case MESSAGE_TYPE_COMMAND:
                        if (processCommand(msg)) {
                            transitionTo(mWaitForResponseState);
                        }
                        return HANDLED;
                    case MESSAGE_TYPE_RESPONSE:
                        /* fall-through */
                    case MESSAGE_TYPE_RESPONSE_TIMEOUT:
                        /*
                         * remnants/delayed/out-of-sync messages - but let
                         * WaitForResponseState deal with them (identified as
                         * out-of-date by transaction ID).
                         */
                        deferMessage(msg);
                        return HANDLED;
                    default:
                        /* fall-through */
                }

                return NOT_HANDLED;
            }
        }

        private class WaitForResponseState extends RunnerState {
            private static final long AWARE_COMMAND_TIMEOUT = 5_000;
            private WakeupMessage mTimeoutMessage;

            WaitForResponseState(int threshold) {
                super(threshold, mWifiInjector.getWifiHandlerLocalLog());
            }

            @Override
            public String getMessageLogRec(int what) {
                return WifiAwareStateManager.class.getSimpleName() + "."
                        + WaitForResponseState.class.getSimpleName() + "." + getWhatToString(what);
            }

            @Override
            public void enterImpl() {
                mTimeoutMessage = new WakeupMessage(mContext, getHandler(), HAL_COMMAND_TIMEOUT_TAG,
                        MESSAGE_TYPE_RESPONSE_TIMEOUT, mCurrentCommand.arg1, mCurrentTransactionId);
                mTimeoutMessage.schedule(SystemClock.elapsedRealtime() + AWARE_COMMAND_TIMEOUT);
                mStartTime = SystemClock.elapsedRealtime();
            }

            @Override
            public void exitImpl() {
                mTimeoutMessage.cancel();
            }

            @Override
            public boolean processMessageImpl(Message msg) {
                if (mVdbg) {
                    Log.v(TAG, getName() + msg.toString());
                }

                switch (msg.what) {
                    case MESSAGE_TYPE_COMMAND:
                        /*
                         * don't want COMMANDs in this state - defer until back
                         * in WaitState
                         */
                        deferMessage(msg);
                        return HANDLED;
                    case MESSAGE_TYPE_RESPONSE:
                        if (msg.arg2 == mCurrentTransactionId) {
                            processResponse(msg);
                            transitionTo(mWaitState);
                        } else {
                            Log.w(TAG,
                                    "WaitForResponseState: processMessage: non-matching "
                                            + "transaction ID on RESPONSE (a very late "
                                            + "response) -- msg=" + msg);
                            /* no transition */
                        }
                        return HANDLED;
                    case MESSAGE_TYPE_RESPONSE_TIMEOUT:
                        if (msg.arg2 == mCurrentTransactionId) {
                            processTimeout(msg);
                            transitionTo(mWaitState);
                        } else {
                            Log.w(TAG, "WaitForResponseState: processMessage: non-matching "
                                    + "transaction ID on RESPONSE_TIMEOUT (either a non-cancelled "
                                    + "timeout or a race condition with cancel) -- msg=" + msg);
                            /* no transition */
                        }
                        return HANDLED;
                    default:
                        /* fall-through */
                }

                return NOT_HANDLED;
            }
        }

        private void processNotification(Message msg) {
            if (mVdbg) {
                Log.v(TAG, "processNotification: msg=" + msg);
            }

            switch (msg.arg1) {
                case NOTIFICATION_TYPE_INTERFACE_CHANGE: {
                    byte[] mac = (byte[]) msg.obj;

                    onInterfaceAddressChangeLocal(mac);
                    break;
                }
                case NOTIFICATION_TYPE_CLUSTER_CHANGE: {
                    int clusterEventType = msg.arg2;
                    byte[] clusterId = (byte[]) msg.obj;

                    onClusterChangeLocal(clusterEventType, clusterId);
                    break;
                }
                case NOTIFICATION_TYPE_MATCH: {
                    int pubSubId = msg.arg2;
                    int requesterInstanceId = msg.getData()
                            .getInt(MESSAGE_BUNDLE_KEY_REQ_INSTANCE_ID);
                    byte[] peerMac = msg.getData().getByteArray(MESSAGE_BUNDLE_KEY_MAC_ADDRESS);
                    byte[] serviceSpecificInfo = msg.getData()
                            .getByteArray(MESSAGE_BUNDLE_KEY_SSI_DATA);
                    byte[] matchFilter = msg.getData().getByteArray(MESSAGE_BUNDLE_KEY_FILTER_DATA);
                    int rangingIndication = msg.getData().getInt(MESSAGE_RANGING_INDICATION);
                    int rangeMm = msg.getData().getInt(MESSAGE_RANGE_MM);
                    int cipherSuite = msg.getData().getInt(MESSAGE_BUNDLE_KEY_CIPHER_SUITE);
                    byte[] scid = msg.getData().getByteArray(MESSAGE_BUNDLE_KEY_SCID);
                    byte[] nonce = msg.getData().getByteArray(MESSAGE_BUNDLE_KEY_NONCE);
                    byte[] tag = msg.getData().getByteArray(MESSAGE_BUNDLE_KEY_TAG);
                    AwarePairingConfig pairingConfig = msg.getData()
                            .getParcelable(MESSAGE_BUNDLE_KEY_PAIRING_CONFIG);
                    ArrayList<OuiKeyedData> vendorData =
                            msg.getData().getParcelableArrayList(MESSAGE_BUNDLE_KEY_VENDOR_DATA);

                    onMatchLocal(pubSubId, requesterInstanceId, peerMac, serviceSpecificInfo,
                            matchFilter, rangingIndication, rangeMm, cipherSuite, scid, nonce, tag,
                            pairingConfig, vendorData);
                    break;
                }
                case NOTIFICATION_TYPE_MATCH_EXPIRED: {
                    int pubSubId = msg.arg2;
                    int requestorInstanceId = msg.getData()
                            .getInt(MESSAGE_BUNDLE_KEY_REQ_INSTANCE_ID);
                    onMatchExpiredLocal(pubSubId, requestorInstanceId);
                    break;
                }
                case NOTIFICATION_TYPE_SESSION_TERMINATED: {
                    int pubSubId = msg.arg2;
                    int reason = (Integer) msg.obj;
                    boolean isPublish = msg.getData().getBoolean(MESSAGE_BUNDLE_KEY_SESSION_TYPE);

                    onSessionTerminatedLocal(pubSubId, isPublish, reason);
                    break;
                }
                case NOTIFICATION_TYPE_MESSAGE_RECEIVED: {
                    int pubSubId = msg.arg2;
                    int requestorInstanceId = (Integer) msg.obj;
                    byte[] peerMac = msg.getData().getByteArray(MESSAGE_BUNDLE_KEY_MAC_ADDRESS);
                    byte[] message = msg.getData().getByteArray(MESSAGE_BUNDLE_KEY_MESSAGE_DATA);

                    onMessageReceivedLocal(pubSubId, requestorInstanceId, peerMac, message);
                    break;
                }
                case NOTIFICATION_TYPE_AWARE_DOWN: {
                    int reason = msg.arg2;

                    /*
                     * TODO: b/28615938. Use reason code to determine whether or not need clean-up
                     * local state (only needed if AWARE_DOWN is due to internal firmware reason,
                     * e.g. concurrency, rather than due to a requested shutdown).
                     */

                    onAwareDownLocal();
                    if (reason != NanStatusCode.SUCCESS) {
                        sendAwareStateChangedBroadcast(false);
                    }
                    break;
                }
                case NOTIFICATION_TYPE_ON_MESSAGE_SEND_SUCCESS: {
                    short transactionId = (short) msg.arg2;
                    Message queuedSendCommand = mFwQueuedSendMessages.get(transactionId);
                    if (mVdbg) {
                        Log.v(TAG, "NOTIFICATION_TYPE_ON_MESSAGE_SEND_SUCCESS: queuedSendCommand="
                                + queuedSendCommand);
                    }
                    if (queuedSendCommand == null) {
                        Log.w(TAG,
                                "processNotification: NOTIFICATION_TYPE_ON_MESSAGE_SEND_SUCCESS:"
                                        + " transactionId=" + transactionId
                                        + " - no such queued send command (timed-out?)");
                    } else {
                        mFwQueuedSendMessages.remove(transactionId);
                        updateSendMessageTimeout();
                        onMessageSendSuccessLocal(queuedSendCommand);
                    }
                    mSendQueueBlocked = false;
                    transmitNextMessage();

                    break;
                }
                case NOTIFICATION_TYPE_ON_MESSAGE_SEND_FAIL: {
                    short transactionId = (short) msg.arg2;
                    int reason = (Integer) msg.obj;
                    Message sentMessage = mFwQueuedSendMessages.get(transactionId);
                    if (mVdbg) {
                        Log.v(TAG, "NOTIFICATION_TYPE_ON_MESSAGE_SEND_FAIL: sentMessage="
                                + sentMessage);
                    }
                    if (sentMessage == null) {
                        Log.w(TAG,
                                "processNotification: NOTIFICATION_TYPE_ON_MESSAGE_SEND_FAIL:"
                                        + " transactionId=" + transactionId
                                        + " - no such queued send command (timed-out?)");
                    } else {
                        mFwQueuedSendMessages.remove(transactionId);
                        updateSendMessageTimeout();

                        int retryCount = sentMessage.getData()
                                .getInt(MESSAGE_BUNDLE_KEY_RETRY_COUNT);
                        if (retryCount > 0 && reason == NanStatusCode.NO_OTA_ACK) {
                            if (mVerboseLoggingEnabled) {
                                Log.v(TAG,
                                        "NOTIFICATION_TYPE_ON_MESSAGE_SEND_FAIL: transactionId="
                                                + transactionId + ", reason=" + reason
                                                + ": retransmitting - retryCount=" + retryCount);
                            }
                            sentMessage.getData().putInt(MESSAGE_BUNDLE_KEY_RETRY_COUNT,
                                    retryCount - 1);

                            int arrivalSeq = sentMessage.getData().getInt(
                                    MESSAGE_BUNDLE_KEY_MESSAGE_ARRIVAL_SEQ);
                            mHostQueuedSendMessages.put(arrivalSeq, sentMessage);
                        } else {
                            onMessageSendFailLocal(sentMessage, reason);
                        }
                        mSendQueueBlocked = false;
                        transmitNextMessage();
                    }
                    break;
                }
                case NOTIFICATION_TYPE_ON_DATA_PATH_REQUEST: {
                    int ndpId = (int) msg.obj;
                    mDataPathMgr.onDataPathRequest(
                            msg.arg2, msg.getData().getByteArray(MESSAGE_BUNDLE_KEY_MAC_ADDRESS),
                            ndpId, msg.getData().getByteArray(MESSAGE_BUNDLE_KEY_MESSAGE));

                    break;
                }
                case NOTIFICATION_TYPE_ON_DATA_PATH_CONFIRM: {
                    int ndpId = msg.arg2;
                    boolean success = mDataPathMgr.onDataPathConfirm(
                            ndpId, msg.getData().getByteArray(MESSAGE_BUNDLE_KEY_MAC_ADDRESS),
                            msg.getData().getBoolean(MESSAGE_BUNDLE_KEY_SUCCESS_FLAG),
                            msg.getData().getInt(MESSAGE_BUNDLE_KEY_STATUS_CODE),
                            msg.getData().getByteArray(MESSAGE_BUNDLE_KEY_MESSAGE_DATA),
                            (List<WifiAwareChannelInfo>) msg.obj);

                    if (success) {
                        WakeupMessage timeout = mDataPathConfirmTimeoutMessages.get(ndpId);
                        if (timeout != null) {
                            mDataPathConfirmTimeoutMessages.remove(ndpId);
                            timeout.cancel();
                        }
                    }

                    break;
                }
                case NOTIFICATION_TYPE_ON_DATA_PATH_END:
                    mDataPathMgr.onDataPathEnd(msg.arg2);
                    sendAwareResourcesChangedBroadcast();
                    break;
                case NOTIFICATION_TYPE_ON_DATA_PATH_SCHED_UPDATE:
                    mDataPathMgr.onDataPathSchedUpdate(
                            msg.getData().getByteArray(MESSAGE_BUNDLE_KEY_MAC_ADDRESS),
                            msg.getData().getIntegerArrayList(MESSAGE_BUNDLE_KEY_NDP_IDS),
                            (List<WifiAwareChannelInfo>) msg.obj);
                    break;
                case NOTIFICATION_TYPE_ON_PAIRING_REQUEST: {
                    Bundle data = msg.getData();
                    int pubSubId = msg.arg2;
                    int pairId = data.getInt(MESSAGE_BUNDLE_KEY_PAIRING_REQUEST_ID);
                    int requestorInstanceId = data.getInt(MESSAGE_BUNDLE_KEY_REQ_INSTANCE_ID);
                    byte[] mac = data.getByteArray(MESSAGE_BUNDLE_KEY_MAC_ADDRESS);
                    int requestType = data.getInt(MESSAGE_BUNDLE_KEY_PAIRING_TYPE);
                    byte[] nonce = data.getByteArray(MESSAGE_BUNDLE_KEY_NONCE);
                    byte[] tag = data.getByteArray(MESSAGE_BUNDLE_KEY_TAG);
                    onPairingRequestReceivedLocal(pubSubId, requestorInstanceId, mac, pairId,
                            requestType, nonce, tag);
                    break;
                }
                case NOTIFICATION_TYPE_ON_BOOTSTRAPPING_REQUEST: {
                    Bundle data = msg.getData();
                    int pubSubId = msg.arg2;
                    int bootStrappingId = data.getInt(MESSAGE_BUNDLE_KEY_BOOTSTRAPPING_REQUEST_ID);
                    int requestorInstanceId = data.getInt(MESSAGE_BUNDLE_KEY_REQ_INSTANCE_ID);
                    byte[] mac = data.getByteArray(MESSAGE_BUNDLE_KEY_MAC_ADDRESS);
                    int method = data.getInt(MESSAGE_BUNDLE_KEY_BOOTSTRAPPING_METHOD);
                    onBootstrappingRequestReceivedLocal(pubSubId, requestorInstanceId, mac,
                            bootStrappingId, method);
                    break;
                }
                case NOTIFICATION_TYPE_ON_PAIRING_CONFIRM: {
                    Bundle data = msg.getData();
                    int reason = msg.arg2;
                    PairingSecurityAssociationInfo npksa = (PairingSecurityAssociationInfo) msg.obj;
                    int pairId = data.getInt(MESSAGE_BUNDLE_KEY_PAIRING_REQUEST_ID);
                    boolean accept = data.getBoolean(MESSAGE_BUNDLE_KEY_PAIRING_ACCEPT);
                    boolean enableCache = data.getBoolean(MESSAGE_BUNDLE_KEY_PAIRING_CACHE);
                    int requestType = data.getInt(MESSAGE_BUNDLE_KEY_PAIRING_TYPE);
                    boolean success = onPairingConfirmReceivedLocal(pairId, accept, reason,
                            requestType, enableCache, npksa);
                    if (success) {
                        WakeupMessage timeout = mPairingConfirmTimeoutMessages.get(pairId);
                        if (timeout != null) {
                            mPairingConfirmTimeoutMessages.remove(pairId);
                            timeout.cancel();
                        }
                    }
                    break;
                }
                case NOTIFICATION_TYPE_ON_BOOTSTRAPPING_CONFIRM: {
                    Bundle data = msg.getData();
                    int reason = msg.arg2;
                    int bootstrappingId = data.getInt(MESSAGE_BUNDLE_KEY_BOOTSTRAPPING_REQUEST_ID);
                    int responseCode = data.getInt(MESSAGE_BUNDLE_KEY_BOOTSTRAPPING_RESPONSE_CODE);
                    int comBackDelay = data.getInt(
                            MESSAGE_BUNDLE_KEY_BOOTSTRAPPING_COME_BACK_DELAY);
                    byte[] cookie = data.getByteArray(
                            MESSAGE_BUNDLE_KEY_BOOTSTRAPPING_COME_BACK_COOKIE);
                    boolean success = onBootStrappingConfirmReceivedLocal(bootstrappingId,
                            reason, responseCode, comBackDelay, cookie);
                    if (success) {
                        WakeupMessage timeout = mBootstrappingConfirmTimeoutMessages
                                .get(bootstrappingId);
                        if (timeout != null) {
                            mBootstrappingConfirmTimeoutMessages.remove(bootstrappingId);
                            timeout.cancel();
                        }
                    }
                    break;
                }
                case NOTIFICATION_TYPE_ON_SUSPENSION_MODE_CHANGED: {
                    Bundle data = msg.getData();
                    boolean isSuspended = data.getBoolean(MESSAGE_BUNDLE_KEY_SUSPENSION_MODE);
                    onSuspensionModeChangedLocal(isSuspended);
                    break;
                }
                default:
                    Log.wtf(TAG, "processNotification: this isn't a NOTIFICATION -- msg=" + msg);
            }
        }

        /**
         * Execute the command specified by the input Message. Returns a true if
         * need to wait for a RESPONSE, otherwise a false. We may not have to
         * wait for a RESPONSE if there was an error in the state (so no command
         * is sent to HAL) OR if we choose not to wait for response - e.g. for
         * disconnected/terminate commands failure is not possible.
         */
        private boolean processCommand(Message msg) {
            if (mVdbg) {
                Log.v(TAG, "processCommand: msg=" + msg);
            }

            if (mCurrentCommand != null) {
                Log.wtf(TAG,
                        "processCommand: receiving a command (msg=" + msg
                                + ") but current (previous) command isn't null (prev_msg="
                                + mCurrentCommand + ")");
                mCurrentCommand = null;
            }

            mCurrentTransactionId = mNextTransactionId++;

            boolean waitForResponse = true;

            switch (msg.arg1) {
                case COMMAND_TYPE_CONNECT: {
                    if (mAwareIsDisabling) {
                        deferMessage(msg);
                        waitForResponse = false;
                        if (WaitingState.wasMessageInWaitingState(msg)) {
                            mInterfaceConflictMgr.reset();
                        }
                        break;
                    }

                    int clientId = msg.arg2;
                    Pair<IWifiAwareEventCallback, Object> callbackAndAttributionSource =
                            (Pair<IWifiAwareEventCallback, Object>) msg.obj;
                    IWifiAwareEventCallback callback = callbackAndAttributionSource.first;
                    ConfigRequest configRequest = (ConfigRequest) msg.getData()
                            .getParcelable(MESSAGE_BUNDLE_KEY_CONFIG);
                    int uid = msg.getData().getInt(MESSAGE_BUNDLE_KEY_UID);
                    int pid = msg.getData().getInt(MESSAGE_BUNDLE_KEY_PID);
                    String callingPackage = msg.getData().getString(
                            MESSAGE_BUNDLE_KEY_CALLING_PACKAGE);
                    String callingFeatureId = msg.getData().getString(
                            MESSAGE_BUNDLE_KEY_CALLING_FEATURE_ID);
                    boolean notifyIdentityChange = msg.getData().getBoolean(
                            MESSAGE_BUNDLE_KEY_NOTIFY_IDENTITY_CHANGE);
                    boolean awareOffload = msg.getData().getBoolean(
                            MESSAGE_BUNDLE_KEY_AWARE_OFFLOAD);
                    boolean reEnableAware = msg.getData()
                            .getBoolean(MESSAGE_BUNDLE_KEY_RE_ENABLE_AWARE_FROM_OFFLOAD);
                    int callerType = msg.getData().getInt(MESSAGE_BUNDLE_KEY_CALLER_TYPE);
                    int proceedWithOperation =
                            mInterfaceConflictMgr.manageInterfaceConflictForStateMachine(TAG,
                                    msg, this, mWaitingState, mWaitState,
                                    HalDeviceManager.HDM_CREATE_IFACE_NAN,
                                    new WorkSource(uid, callingPackage),
                                    awareOffload || mOpportunisticSet.contains(callingPackage)
                                    /* bypassDialog */);

                    if (proceedWithOperation == InterfaceConflictManager.ICM_ABORT_COMMAND) {
                        // handling user rejection or possible conflict (pending command)
                        try {
                            callback.onConnectFail(
                                    NanStatusCode.NO_RESOURCES_AVAILABLE);
                            mAwareMetrics.recordAttachStatus(NanStatusCode.NO_RESOURCES_AVAILABLE,
                                    callerType, callingFeatureId, uid);
                        } catch (RemoteException e) {
                            Log.w(TAG, "displayUserApprovalDialog user refusal: RemoteException "
                                    + "(FYI): " + e);
                        }
                        waitForResponse = false;
                    } else if (proceedWithOperation
                            == InterfaceConflictManager.ICM_EXECUTE_COMMAND) {
                        waitForResponse = connectLocal(mCurrentTransactionId, clientId, uid, pid,
                                callingPackage, callingFeatureId, callback, configRequest,
                                notifyIdentityChange,
                                callbackAndAttributionSource.second,
                                awareOffload, reEnableAware, callerType);
                    } else { // InterfaceConflictManager.ICM_SKIP_COMMAND_WAIT_FOR_USER
                        waitForResponse = false;
                    }
                    break;
                }
                case COMMAND_TYPE_DISCONNECT: {
                    int clientId = msg.arg2;

                    waitForResponse = disconnectLocal(mCurrentTransactionId, clientId);
                    break;
                }
                case COMMAND_TYPE_DISABLE: {
                    mAwareIsDisabling = false;
                    // Must trigger a state transition to execute the deferred connect command
                    if (!mWifiAwareNativeApi.disable(mCurrentTransactionId)) {
                        onDisableResponse(mCurrentTransactionId, WifiStatusCode.ERROR_UNKNOWN);
                    }
                    break;
                }
                case COMMAND_TYPE_RECONFIGURE:
                    waitForResponse = reconfigureLocal(mCurrentTransactionId);
                    break;
                case COMMAND_TYPE_TERMINATE_SESSION: {
                    int clientId = msg.arg2;
                    int sessionId = (Integer) msg.obj;

                    terminateSessionLocal(clientId, sessionId);
                    waitForResponse = false;
                    break;
                }
                case COMMAND_TYPE_PUBLISH: {
                    int clientId = msg.arg2;
                    IWifiAwareDiscoverySessionCallback callback =
                            (IWifiAwareDiscoverySessionCallback) msg.obj;
                    PublishConfig publishConfig = (PublishConfig) msg.getData()
                            .getParcelable(MESSAGE_BUNDLE_KEY_CONFIG);

                    waitForResponse = publishLocal(mCurrentTransactionId, clientId, publishConfig,
                            callback);
                    break;
                }
                case COMMAND_TYPE_UPDATE_PUBLISH: {
                    int clientId = msg.arg2;
                    int sessionId = msg.getData().getInt(MESSAGE_BUNDLE_KEY_SESSION_ID);
                    PublishConfig publishConfig = (PublishConfig) msg.getData()
                            .getParcelable(MESSAGE_BUNDLE_KEY_CONFIG);
                    waitForResponse = updatePublishLocal(mCurrentTransactionId, clientId, sessionId,
                            publishConfig);
                    break;
                }
                case COMMAND_TYPE_SUBSCRIBE: {
                    int clientId = msg.arg2;
                    IWifiAwareDiscoverySessionCallback callback =
                            (IWifiAwareDiscoverySessionCallback) msg.obj;
                    SubscribeConfig subscribeConfig = (SubscribeConfig) msg.getData()
                            .getParcelable(MESSAGE_BUNDLE_KEY_CONFIG);

                    waitForResponse = subscribeLocal(mCurrentTransactionId, clientId,
                            subscribeConfig, callback);
                    break;
                }
                case COMMAND_TYPE_UPDATE_SUBSCRIBE: {
                    int clientId = msg.arg2;
                    int sessionId = msg.getData().getInt(MESSAGE_BUNDLE_KEY_SESSION_ID);
                    SubscribeConfig subscribeConfig = (SubscribeConfig) msg.getData()
                            .getParcelable(MESSAGE_BUNDLE_KEY_CONFIG);

                    waitForResponse = updateSubscribeLocal(mCurrentTransactionId, clientId,
                            sessionId, subscribeConfig);
                    break;
                }
                case COMMAND_TYPE_ENQUEUE_SEND_MESSAGE: {
                    if (mVdbg) {
                        Log.v(TAG, "processCommand: ENQUEUE_SEND_MESSAGE - messageId="
                                + msg.getData().getInt(MESSAGE_BUNDLE_KEY_MESSAGE_ID)
                                + ", mSendArrivalSequenceCounter=" + mSendArrivalSequenceCounter);
                    }
                    int uid = msg.getData().getInt(MESSAGE_BUNDLE_KEY_UID);
                    if (isUidExceededMessageQueueDepthLimit(uid)) {
                        if (mVerboseLoggingEnabled) {
                            Log.v(TAG, "message queue limit exceeded for uid=" + uid
                                    + " at messageId="
                                    + msg.getData().getInt(MESSAGE_BUNDLE_KEY_MESSAGE_ID));
                        }
                        onMessageSendFailLocal(msg, NanStatusCode.INTERNAL_FAILURE);
                        waitForResponse = false;
                        break;
                    }
                    Message sendMsg = obtainMessage(msg.what);
                    sendMsg.copyFrom(msg);
                    sendMsg.getData().putInt(MESSAGE_BUNDLE_KEY_MESSAGE_ARRIVAL_SEQ,
                            mSendArrivalSequenceCounter);
                    mHostQueuedSendMessages.put(mSendArrivalSequenceCounter, sendMsg);
                    mSendArrivalSequenceCounter++;
                    waitForResponse = false;

                    if (!mSendQueueBlocked) {
                        transmitNextMessage();
                    }

                    break;
                }
                case COMMAND_TYPE_INITIATE_PAIRING_REQUEST: {
                    int clientId = msg.arg2;
                    Bundle data = msg.getData();
                    int sessionId = data.getInt(MESSAGE_BUNDLE_KEY_SESSION_ID);
                    int peerId = data.getInt(MESSAGE_BUNDLE_KEY_PEER_ID);
                    String password = data.getString(MESSAGE_BUNDLE_KEY_PAIRING_PASSWORD);
                    int requestType = data.getInt(MESSAGE_BUNDLE_KEY_PAIRING_TYPE);
                    int akm = data.getInt(MESSAGE_BUNDLE_KEY_PAIRING_AKM);
                    int cipherSuite = data.getInt(MESSAGE_BUNDLE_KEY_PAIRING_CIPHER_SUITE);
                    byte[] pmk = data.getByteArray(MESSAGE_BUNDLE_KEY_PAIRING_PMK);
                    waitForResponse = initiateNanPairingRequestLocal(mCurrentTransactionId,
                            clientId, sessionId, peerId, password, requestType, akm, pmk,
                            cipherSuite);
                    break;
                }
                case COMMAND_TYPE_RESPONSE_PAIRING_REQUEST: {
                    int clientId = msg.arg2;
                    Bundle data = msg.getData();
                    int sessionId = data.getInt(MESSAGE_BUNDLE_KEY_SESSION_ID);
                    int peerId = data.getInt(MESSAGE_BUNDLE_KEY_PEER_ID);
                    int requestId = data.getInt(MESSAGE_BUNDLE_KEY_PAIRING_REQUEST_ID);
                    String password = data.getString(MESSAGE_BUNDLE_KEY_PAIRING_PASSWORD);
                    int requestType = data.getInt(MESSAGE_BUNDLE_KEY_PAIRING_TYPE);
                    int akm = data.getInt(MESSAGE_BUNDLE_KEY_PAIRING_AKM);
                    int cipherSuite = data.getInt(MESSAGE_BUNDLE_KEY_PAIRING_CIPHER_SUITE);
                    byte[] pmk = data.getByteArray(MESSAGE_BUNDLE_KEY_PAIRING_PMK);
                    boolean accept = data.getBoolean(MESSAGE_BUNDLE_KEY_PAIRING_ACCEPT);
                    waitForResponse = respondToPairingRequestLocal(mCurrentTransactionId, clientId,
                            sessionId, peerId, requestId, accept, requestType, pmk, password, akm,
                            cipherSuite);
                    break;
                }
                case COMMAND_TYPE_INITIATE_BOOTSTRAPPING_REQUEST: {
                    int clientId = msg.arg2;
                    Bundle data = msg.getData();
                    int sessionId = data.getInt(MESSAGE_BUNDLE_KEY_SESSION_ID);
                    int peerId = data.getInt(MESSAGE_BUNDLE_KEY_PEER_ID);
                    int method = data.getInt(MESSAGE_BUNDLE_KEY_BOOTSTRAPPING_METHOD);
                    byte[] cookie = data.getByteArray(
                            MESSAGE_BUNDLE_KEY_BOOTSTRAPPING_COME_BACK_COOKIE);
                    boolean isComeBack = data
                            .getBoolean(MESSAGE_BUNDLE_KEY_BOOTSTRAPPING_IS_COME_BACK_REQUEST);
                    waitForResponse = initiateBootstrappingRequestLocal(mCurrentTransactionId,
                            clientId, sessionId, peerId, method, cookie, isComeBack);
                    break;
                }
                case COMMAND_TYPE_RESPONSE_BOOTSTRAPPING_REQUEST: {
                    int clientId = msg.arg2;
                    Bundle data = msg.getData();
                    int sessionId = data.getInt(MESSAGE_BUNDLE_KEY_SESSION_ID);
                    int peerId = data.getInt(MESSAGE_BUNDLE_KEY_PEER_ID);
                    boolean accept = data.getBoolean(MESSAGE_BUNDLE_KEY_BOOTSTRAPPING_ACCEPT);
                    int bootstrappingId = data.getInt(MESSAGE_BUNDLE_KEY_BOOTSTRAPPING_REQUEST_ID);
                    int method = data.getInt(MESSAGE_BUNDLE_KEY_BOOTSTRAPPING_METHOD);
                    waitForResponse = respondToBootstrappingRequestLocal(mCurrentTransactionId,
                            clientId, sessionId, peerId, bootstrappingId, accept, method);
                    break;
                }
                case COMMAND_TYPE_TRANSMIT_NEXT_MESSAGE: {
                    if (mSendQueueBlocked || mHostQueuedSendMessages.size() == 0) {
                        if (mVdbg) {
                            Log.v(TAG, "processCommand: SEND_TOP_OF_QUEUE_MESSAGE - blocked or "
                                    + "empty host queue");
                        }
                        waitForResponse = false;
                    } else {
                        if (mVdbg) {
                            Log.v(TAG, "processCommand: SEND_TOP_OF_QUEUE_MESSAGE - "
                                    + "sendArrivalSequenceCounter="
                                    + mHostQueuedSendMessages.keyAt(0));
                        }
                        Message sendMessage = mHostQueuedSendMessages.valueAt(0);
                        mHostQueuedSendMessages.removeAt(0);

                        Bundle data = sendMessage.getData();
                        int clientId = sendMessage.arg2;
                        int sessionId = sendMessage.getData().getInt(MESSAGE_BUNDLE_KEY_SESSION_ID);
                        int peerId = data.getInt(MESSAGE_BUNDLE_KEY_MESSAGE_PEER_ID);
                        byte[] message = data.getByteArray(MESSAGE_BUNDLE_KEY_MESSAGE);
                        int messageId = data.getInt(MESSAGE_BUNDLE_KEY_MESSAGE_ID);

                        msg.getData().putParcelable(MESSAGE_BUNDLE_KEY_SENT_MESSAGE, sendMessage);

                        waitForResponse = sendFollowonMessageLocal(mCurrentTransactionId, clientId,
                                sessionId, peerId, message, messageId);
                    }
                    break;
                }
                case COMMAND_TYPE_ENABLE_USAGE:
                    enableUsageLocal();
                    waitForResponse = false;
                    break;
                case COMMAND_TYPE_DISABLE_USAGE:
                    disableUsageLocal(mCurrentTransactionId, msg.arg2 == 1);
                    waitForResponse = false;
                    break;
                case COMMAND_TYPE_GET_CAPABILITIES:
                    if (mCapabilities == null) {
                        waitForResponse = mWifiAwareNativeApi.getCapabilities(
                                mCurrentTransactionId);
                    } else {
                        if (mVdbg) {
                            Log.v(TAG, "COMMAND_TYPE_GET_CAPABILITIES: already have capabilities - "
                                    + "skipping");
                        }
                        waitForResponse = false;
                    }
                    break;
                case COMMAND_TYPE_DELETE_ALL_DATA_PATH_INTERFACES:
                    mDataPathMgr.deleteAllInterfaces();
                    waitForResponse = false;
                    break;
                case COMMAND_TYPE_CREATE_DATA_PATH_INTERFACE:
                    waitForResponse = mWifiAwareNativeApi.createAwareNetworkInterface(
                            mCurrentTransactionId, (String) msg.obj);
                    break;
                case COMMAND_TYPE_DELETE_DATA_PATH_INTERFACE:
                    waitForResponse = mWifiAwareNativeApi.deleteAwareNetworkInterface(
                            mCurrentTransactionId, (String) msg.obj);
                    break;
                case COMMAND_TYPE_INITIATE_DATA_PATH_SETUP: {
                    Bundle data = msg.getData();

                    WifiAwareNetworkSpecifier networkSpecifier =
                            (WifiAwareNetworkSpecifier) msg.obj;

                    int peerId = data.getInt(MESSAGE_BUNDLE_KEY_PEER_ID);
                    int channelRequestType = data.getInt(MESSAGE_BUNDLE_KEY_CHANNEL_REQ_TYPE);
                    int channel = data.getInt(MESSAGE_BUNDLE_KEY_CHANNEL);
                    byte[] peer = data.getByteArray(MESSAGE_BUNDLE_KEY_MAC_ADDRESS);
                    String interfaceName = data.getString(MESSAGE_BUNDLE_KEY_INTERFACE_NAME);
                    boolean isOutOfBand = data.getBoolean(MESSAGE_BUNDLE_KEY_OOB);
                    byte[] appInfo = data.getByteArray(MESSAGE_BUNDLE_KEY_APP_INFO);

                    waitForResponse = initiateDataPathSetupLocal(mCurrentTransactionId,
                            networkSpecifier, peerId, channelRequestType, channel, peer,
                            interfaceName, isOutOfBand, appInfo);
                    break;
                }
                case COMMAND_TYPE_RESPOND_TO_DATA_PATH_SETUP_REQUEST: {
                    Bundle data = msg.getData();

                    int ndpId = msg.arg2;
                    WifiAwareNetworkSpecifier specifier =
                            (WifiAwareNetworkSpecifier) msg.obj;
                    boolean accept = data.getBoolean(MESSAGE_BUNDLE_KEY_ACCEPT_STATE);
                    String interfaceName = data.getString(MESSAGE_BUNDLE_KEY_INTERFACE_NAME);
                    byte[] appInfo = data.getByteArray(MESSAGE_BUNDLE_KEY_APP_INFO);
                    boolean isOutOfBand = data.getBoolean(MESSAGE_BUNDLE_KEY_OOB);

                    waitForResponse = respondToDataPathRequestLocal(mCurrentTransactionId, accept,
                            ndpId, interfaceName, appInfo, isOutOfBand, specifier);

                    break;
                }
                case COMMAND_TYPE_END_DATA_PATH: {
                    int ndpId = msg.arg2;
                    WakeupMessage timeout = mDataPathConfirmTimeoutMessages.get(ndpId);
                    if (timeout != null) {
                        mDataPathConfirmTimeoutMessages.remove(ndpId);
                        timeout.cancel();
                    }
                    waitForResponse = endDataPathLocal(mCurrentTransactionId, ndpId);
                    break;
                }
                case COMMAND_TYPE_END_PAIRING: {
                    int pairId = msg.arg2;
                    WakeupMessage timeout = mPairingConfirmTimeoutMessages.get(pairId);
                    if (timeout != null) {
                        mPairingConfirmTimeoutMessages.remove(pairId);
                        timeout.cancel();
                    }
                    waitForResponse = endPairingLocal(mCurrentTransactionId, pairId);
                    break;
                }
                case COMMAND_TYPE_DELAYED_INITIALIZATION:
                    if (SdkLevel.isAtLeastT()) {
                        mWifiManager.registerActiveCountryCodeChangedCallback(
                                new HandlerExecutor(mHandler), new CountryCodeChangeCallback());
                    }
                    mWifiAwareNativeManager.start(getHandler());
                    waitForResponse = false;
                    break;
                case COMMAND_TYPE_GET_AWARE:
                    WorkSource requestorWs = (WorkSource) msg.obj;
                    mWifiAwareNativeManager.tryToGetAware(requestorWs);
                    waitForResponse = false;
                    break;
                case COMMAND_TYPE_RELEASE_AWARE:
                    mWifiAwareNativeManager.releaseAware();
                    waitForResponse = false;
                    break;
                case COMMAND_TYPE_SUSPEND_SESSION: {
                    int clientId = msg.arg2;
                    int sessionId = msg.getData().getInt(MESSAGE_BUNDLE_KEY_SESSION_ID);

                    waitForResponse = suspendSessionLocal(mCurrentTransactionId, clientId,
                        sessionId);
                    break;
                }
                case COMMAND_TYPE_RESUME_SESSION: {
                    int clientId = msg.arg2;
                    int sessionId = msg.getData().getInt(MESSAGE_BUNDLE_KEY_SESSION_ID);

                    waitForResponse = resumeSessionLocal(mCurrentTransactionId, clientId,
                        sessionId);
                    break;
                }
                default:
                    waitForResponse = false;
                    Log.wtf(TAG, "processCommand: this isn't a COMMAND -- msg=" + msg);
                    /* fall-through */
            }

            if (!waitForResponse) {
                mCurrentTransactionId = TRANSACTION_ID_IGNORE;
            } else {
                mCurrentCommand = obtainMessage(msg.what);
                mCurrentCommand.copyFrom(msg);
            }

            return waitForResponse;
        }

        private void processResponse(Message msg) {
            if (mVdbg) {
                Log.v(TAG, "processResponse: msg=" + msg);
            }

            if (mCurrentCommand == null) {
                Log.wtf(TAG, "processResponse: no existing command stored!? msg=" + msg);
                mCurrentTransactionId = TRANSACTION_ID_IGNORE;
                return;
            }
            int reason = NanStatusCode.SUCCESS;

            switch (msg.arg1) {
                case RESPONSE_TYPE_ON_CONFIG_SUCCESS:
                    onConfigCompletedLocal(mCurrentCommand);
                    break;
                case RESPONSE_TYPE_ON_CONFIG_FAIL: {
                    reason = (Integer) msg.obj;

                    onConfigFailedLocal(mCurrentCommand, reason);
                    break;
                }
                case RESPONSE_TYPE_ON_SESSION_CONFIG_SUCCESS: {
                    byte pubSubId = (Byte) msg.obj;
                    boolean isPublish = msg.getData().getBoolean(MESSAGE_BUNDLE_KEY_SESSION_TYPE);

                    onSessionConfigSuccessLocal(mCurrentCommand, pubSubId, isPublish);
                    break;
                }
                case RESPONSE_TYPE_ON_SESSION_CONFIG_FAIL: {
                    reason = (int) msg.obj;
                    boolean isPublish = msg.getData().getBoolean(MESSAGE_BUNDLE_KEY_SESSION_TYPE);

                    onSessionConfigFailLocal(mCurrentCommand, isPublish, reason);
                    break;
                }
                case RESPONSE_TYPE_ON_MESSAGE_SEND_QUEUED_SUCCESS: {
                    Message sentMessage = mCurrentCommand.getData().getParcelable(
                            MESSAGE_BUNDLE_KEY_SENT_MESSAGE);
                    sentMessage.getData().putLong(MESSAGE_BUNDLE_KEY_SEND_MESSAGE_ENQUEUE_TIME,
                            SystemClock.elapsedRealtime());
                    mFwQueuedSendMessages.put(mCurrentTransactionId, sentMessage);
                    updateSendMessageTimeout();
                    if (!mSendQueueBlocked) {
                        transmitNextMessage();
                    }

                    if (mVdbg) {
                        Log.v(TAG, "processResponse: ON_MESSAGE_SEND_QUEUED_SUCCESS - arrivalSeq="
                                + sentMessage.getData().getInt(
                                MESSAGE_BUNDLE_KEY_MESSAGE_ARRIVAL_SEQ));
                    }
                    break;
                }
                case RESPONSE_TYPE_ON_MESSAGE_SEND_QUEUED_FAIL: {
                    if (mVdbg) {
                        Log.v(TAG, "processResponse: ON_MESSAGE_SEND_QUEUED_FAIL - blocking!");
                    }
                    reason = (int) msg.obj;
                    Message sentMessage = mCurrentCommand.getData().getParcelable(
                            MESSAGE_BUNDLE_KEY_SENT_MESSAGE);
                    if (reason == NanStatusCode.FOLLOWUP_TX_QUEUE_FULL) {
                        int arrivalSeq = sentMessage.getData().getInt(
                                MESSAGE_BUNDLE_KEY_MESSAGE_ARRIVAL_SEQ);
                        mHostQueuedSendMessages.put(arrivalSeq, sentMessage);
                        mSendQueueBlocked = true;

                        if (mVdbg) {
                            Log.v(TAG, "processResponse: ON_MESSAGE_SEND_QUEUED_FAIL - arrivalSeq="
                                    + arrivalSeq + " -- blocking");
                        }
                    } else {
                        onMessageSendFailLocal(sentMessage, NanStatusCode.INTERNAL_FAILURE);
                        if (!mSendQueueBlocked) {
                            transmitNextMessage();
                        }
                    }
                    break;
                }
                case RESPONSE_TYPE_ON_CAPABILITIES_UPDATED: {
                    onCapabilitiesUpdatedResponseLocal((Capabilities) msg.obj);
                    break;
                }
                case RESPONSE_TYPE_ON_CREATE_INTERFACE:
                    reason = msg.getData().getInt(MESSAGE_BUNDLE_KEY_STATUS_CODE);
                    onCreateDataPathInterfaceResponseLocal(mCurrentCommand,
                            msg.getData().getBoolean(MESSAGE_BUNDLE_KEY_SUCCESS_FLAG),
                            reason);
                    break;
                case RESPONSE_TYPE_ON_DELETE_INTERFACE:
                    reason = msg.getData().getInt(MESSAGE_BUNDLE_KEY_STATUS_CODE);
                    onDeleteDataPathInterfaceResponseLocal(mCurrentCommand,
                            msg.getData().getBoolean(MESSAGE_BUNDLE_KEY_SUCCESS_FLAG),
                            reason);
                    break;
                case RESPONSE_TYPE_ON_INITIATE_DATA_PATH_SUCCESS: {
                    int ndpId = (int) msg.obj;
                    boolean success = onInitiateDataPathResponseSuccessLocal(mCurrentCommand,
                            ndpId);
                    if (success) {
                        WakeupMessage timeout = new WakeupMessage(mContext, getHandler(),
                                HAL_DATA_PATH_CONFIRM_TIMEOUT_TAG, MESSAGE_TYPE_DATA_PATH_TIMEOUT,
                                ndpId);
                        mDataPathConfirmTimeoutMessages.put(ndpId, timeout);
                        timeout.schedule(
                                SystemClock.elapsedRealtime() + AWARE_WAIT_FOR_DP_CONFIRM_TIMEOUT);
                        sendAwareResourcesChangedBroadcast();
                    }
                    break;
                }
                case RESPONSE_TYPE_ON_INITIATE_DATA_PATH_FAIL:
                    reason = (int) msg.obj;
                    onInitiateDataPathResponseFailLocal(mCurrentCommand, reason);
                    break;
                case RESPONSE_TYPE_ON_RESPOND_TO_DATA_PATH_SETUP_REQUEST: {
                    reason = msg.getData().getInt(MESSAGE_BUNDLE_KEY_STATUS_CODE);
                    boolean success = onRespondToDataPathSetupRequestResponseLocal(mCurrentCommand,
                            msg.getData().getBoolean(MESSAGE_BUNDLE_KEY_SUCCESS_FLAG),
                            reason);
                    if (success) {
                        int ndpId = mCurrentCommand.arg2;
                        WakeupMessage timeout = new WakeupMessage(mContext, getHandler(),
                                HAL_DATA_PATH_CONFIRM_TIMEOUT_TAG, MESSAGE_TYPE_DATA_PATH_TIMEOUT,
                                ndpId);
                        mDataPathConfirmTimeoutMessages.put(ndpId, timeout);
                        timeout.schedule(
                                SystemClock.elapsedRealtime() + AWARE_WAIT_FOR_DP_CONFIRM_TIMEOUT);
                    }
                    break;
                }
                case RESPONSE_TYPE_ON_END_DATA_PATH:
                    reason = msg.getData().getInt(MESSAGE_BUNDLE_KEY_STATUS_CODE);
                    onEndPathEndResponseLocal(mCurrentCommand,
                            msg.getData().getBoolean(MESSAGE_BUNDLE_KEY_SUCCESS_FLAG),
                            reason);
                    break;
                case RESPONSE_TYPE_ON_END_PAIRING:
                    reason = msg.getData().getInt(MESSAGE_BUNDLE_KEY_STATUS_CODE);
                    onPairingEndResponseLocal(mCurrentCommand,
                            msg.getData().getBoolean(MESSAGE_BUNDLE_KEY_SUCCESS_FLAG), reason);
                    break;
                case RESPONSE_TYPE_ON_DISABLE:
                    reason = (int) msg.obj;
                    onDisableResponseLocal(mCurrentCommand, reason);
                    break;
                case RESPONSE_TYPE_ON_INITIATE_PAIRING_SUCCESS: {
                    int pairingId = (int) msg.obj;
                    boolean success = onInitiatePairingResponseSuccessLocal(mCurrentCommand,
                            pairingId);
                    if (success) {
                        WakeupMessage timeout = new WakeupMessage(mContext, getHandler(),
                                HAL_PAIRING_CONFIRM_TIMEOUT_TAG, MESSAGE_TYPE_PAIRING_TIMEOUT,
                                pairingId, msg.getData().getInt(MESSAGE_BUNDLE_KEY_PAIRING_TYPE));
                        mPairingConfirmTimeoutMessages.put(pairingId, timeout);
                        timeout.schedule(SystemClock.elapsedRealtime()
                                + AWARE_WAIT_FOR_PAIRING_CONFIRM_TIMEOUT);
                    }
                    break;
                }
                case RESPONSE_TYPE_ON_INITIATE_PAIRING_FAIL: {
                    reason = (int) msg.obj;
                    onInitiatePairingResponseFailLocal(mCurrentCommand, reason);
                    break;
                }
                case RESPONSE_TYPE_ON_RESPONSE_PAIRING_SUCCESS: {
                    boolean success = onRespondToPairingIndicationResponseSuccessLocal(
                            mCurrentCommand);
                    if (success) {
                        int pairingId = mCurrentCommand.getData()
                                .getInt(MESSAGE_BUNDLE_KEY_PAIRING_REQUEST_ID);
                        WakeupMessage timeout = new WakeupMessage(mContext, getHandler(),
                                HAL_PAIRING_CONFIRM_TIMEOUT_TAG, MESSAGE_TYPE_PAIRING_TIMEOUT,
                                pairingId, msg.getData().getInt(MESSAGE_BUNDLE_KEY_PAIRING_TYPE));
                        mPairingConfirmTimeoutMessages.put(pairingId, timeout);
                        timeout.schedule(SystemClock.elapsedRealtime()
                                + AWARE_WAIT_FOR_PAIRING_CONFIRM_TIMEOUT);
                    }
                    break;
                }
                case RESPONSE_TYPE_ON_RESPONSE_PAIRING_FAIL: {
                    reason = (int) msg.obj;
                    onRespondToPairingIndicationResponseFail(mCurrentCommand, reason);
                    break;
                }
                case RESPONSE_TYPE_ON_INITIATE_BOOTSTRAPPING_SUCCESS: {
                    int bootstrappingId = (int) msg.obj;
                    boolean success = onInitiateBootstrappingResponseSuccessLocal(mCurrentCommand,
                            bootstrappingId);

                    if (success) {
                        WakeupMessage timeout = new WakeupMessage(mContext, getHandler(),
                                HAL_PAIRING_CONFIRM_TIMEOUT_TAG, MESSAGE_TYPE_BOOTSTRAPPING_TIMEOUT,
                                bootstrappingId, 0, mCurrentCommand);
                        mBootstrappingConfirmTimeoutMessages.put(bootstrappingId, timeout);
                        timeout.schedule(SystemClock.elapsedRealtime()
                                + AWARE_WAIT_FOR_PAIRING_CONFIRM_TIMEOUT);
                    }
                    break;
                }
                case RESPONSE_TYPE_ON_INITIATE_BOOTSTRAPPING_FAIL: {
                    reason = (int) msg.obj;
                    onInitiateBootStrappingResponseFailLocal(mCurrentCommand, reason);
                    break;
                }
                case RESPONSE_TYPE_ON_RESPONSE_BOOTSTRAPPING_SUCCESS: {
                    onRespondToBootStrappingRequestSuccessLocal(mCurrentCommand);
                    break;
                }
                case RESPONSE_TYPE_ON_RESPONSE_BOOTSTRAPPING_FAIL: {
                    reason = (int) msg.obj;
                    Log.e(TAG, "RespondToBootstrappingIndication failed, reason: " + reason);
                    break;
                }
                case RESPONSE_TYPE_ON_SUSPEND: {
                    reason = msg.getData().getInt(MESSAGE_BUNDLE_KEY_STATUS_CODE);
                    boolean success = msg.getData().getBoolean(MESSAGE_BUNDLE_KEY_SUCCESS_FLAG);
                    onSuspendResponseLocal(mCurrentCommand, success, reason);
                    break;
                }
                case RESPONSE_TYPE_ON_RESUME: {
                    reason = msg.getData().getInt(MESSAGE_BUNDLE_KEY_STATUS_CODE);
                    boolean success = msg.getData().getBoolean(MESSAGE_BUNDLE_KEY_SUCCESS_FLAG);
                    onResumeResponseLocal(mCurrentCommand, success, reason);
                    break;
                }
                default:
                    Log.wtf(TAG, "processResponse: this isn't a RESPONSE -- msg=" + msg);
                    mCurrentCommand = null;
                    mCurrentTransactionId = TRANSACTION_ID_IGNORE;
                    return;
            }
            if (msg.arg1 != RESPONSE_TYPE_ON_CONFIG_SUCCESS
                    && msg.arg1 != RESPONSE_TYPE_ON_CONFIG_FAIL) {
                // Config response handle separately to identify it's connect or reconfigure
                recordHalApiCall(mCurrentCommand.arg1, reason, mStartTime);
            }

            mCurrentCommand = null;
            mCurrentTransactionId = TRANSACTION_ID_IGNORE;
        }

        private void processTimeout(Message msg) {
            if (mVerboseLoggingEnabled) {
                Log.v(TAG, "processTimeout: msg=" + msg);
            }

            if (mCurrentCommand == null) {
                Log.wtf(TAG, "processTimeout: no existing command stored!? msg=" + msg);
                mCurrentTransactionId = TRANSACTION_ID_IGNORE;
                return;
            }

            /*
             * Only have to handle those COMMANDs which wait for a response.
             */
            switch (msg.arg1) {
                case COMMAND_TYPE_CONNECT:
                case COMMAND_TYPE_DISCONNECT:

                case COMMAND_TYPE_RECONFIGURE:
                    /*
                     * Reconfigure timed-out. There is nothing to do but log the issue - which
                      * will be done in the callback.
                     */
                    onConfigFailedLocal(mCurrentCommand, NanStatusCode.INTERNAL_FAILURE);
                    break;
                case COMMAND_TYPE_TERMINATE_SESSION: {
                    Log.wtf(TAG, "processTimeout: TERMINATE_SESSION - shouldn't be waiting!");
                    break;
                }
                case COMMAND_TYPE_PUBLISH:
                case COMMAND_TYPE_UPDATE_PUBLISH: {
                    onSessionConfigFailLocal(mCurrentCommand, true, NanStatusCode.INTERNAL_FAILURE);
                    break;
                }
                case COMMAND_TYPE_SUBSCRIBE:
                case COMMAND_TYPE_UPDATE_SUBSCRIBE: {
                    onSessionConfigFailLocal(mCurrentCommand, false,
                            NanStatusCode.INTERNAL_FAILURE);
                    break;
                }
                case COMMAND_TYPE_ENQUEUE_SEND_MESSAGE: {
                    Log.wtf(TAG, "processTimeout: ENQUEUE_SEND_MESSAGE - shouldn't be waiting!");
                    break;
                }
                case COMMAND_TYPE_INITIATE_PAIRING_REQUEST: {
                    onInitiatePairingResponseFailLocal(mCurrentCommand,
                            NanStatusCode.INTERNAL_FAILURE);
                    break;
                }
                case COMMAND_TYPE_RESPONSE_PAIRING_REQUEST: {
                    onRespondToPairingIndicationResponseFail(mCurrentCommand,
                            NanStatusCode.INTERNAL_FAILURE);
                    break;
                }
                case COMMAND_TYPE_INITIATE_BOOTSTRAPPING_REQUEST: {
                    onInitiateBootStrappingResponseFailLocal(mCurrentCommand,
                            NanStatusCode.INTERNAL_FAILURE);
                    break;
                }
                case COMMAND_TYPE_RESPONSE_BOOTSTRAPPING_REQUEST: {
                    break;
                }
                case COMMAND_TYPE_TRANSMIT_NEXT_MESSAGE: {
                    Message sentMessage = mCurrentCommand.getData().getParcelable(
                            MESSAGE_BUNDLE_KEY_SENT_MESSAGE);
                    onMessageSendFailLocal(sentMessage, NanStatusCode.INTERNAL_FAILURE);
                    mSendQueueBlocked = false;
                    transmitNextMessage();
                    break;
                }
                case COMMAND_TYPE_ENABLE_USAGE:
                    Log.wtf(TAG, "processTimeout: ENABLE_USAGE - shouldn't be waiting!");
                    break;
                case COMMAND_TYPE_DISABLE_USAGE:
                    Log.wtf(TAG, "processTimeout: DISABLE_USAGE - shouldn't be waiting!");
                    break;
                case COMMAND_TYPE_GET_CAPABILITIES:
                    Log.e(TAG,
                            "processTimeout: GET_CAPABILITIES timed-out - strange, will try again"
                                    + " when next enabled!?");
                    break;
                case COMMAND_TYPE_DELETE_ALL_DATA_PATH_INTERFACES:
                    Log.wtf(TAG,
                            "processTimeout: DELETE_ALL_DATA_PATH_INTERFACES - shouldn't be "
                                    + "waiting!");
                    break;
                case COMMAND_TYPE_CREATE_DATA_PATH_INTERFACE:
                    // TODO: fix status: timeout
                    onCreateDataPathInterfaceResponseLocal(mCurrentCommand, false, 0);
                    break;
                case COMMAND_TYPE_DELETE_DATA_PATH_INTERFACE:
                    // TODO: fix status: timeout
                    onDeleteDataPathInterfaceResponseLocal(mCurrentCommand, false, 0);
                    break;
                case COMMAND_TYPE_INITIATE_DATA_PATH_SETUP:
                    // TODO: fix status: timeout
                    onInitiateDataPathResponseFailLocal(mCurrentCommand, 0);
                    break;
                case COMMAND_TYPE_RESPOND_TO_DATA_PATH_SETUP_REQUEST:
                    // TODO: fix status: timeout
                    onRespondToDataPathSetupRequestResponseLocal(mCurrentCommand, false, 0);
                    break;
                case COMMAND_TYPE_END_DATA_PATH:
                    // TODO: fix status: timeout
                    onEndPathEndResponseLocal(mCurrentCommand, false, 0);
                    break;
                case COMMAND_TYPE_END_PAIRING:
                    // TODO: fix status: timeout
                    break;
                case COMMAND_TYPE_DELAYED_INITIALIZATION:
                    Log.wtf(TAG,
                            "processTimeout: COMMAND_TYPE_DELAYED_INITIALIZATION - shouldn't be "
                                    + "waiting!");
                    break;
                case COMMAND_TYPE_GET_AWARE:
                    Log.wtf(TAG,
                            "processTimeout: COMMAND_TYPE_GET_AWARE - shouldn't be waiting!");
                    break;
                case COMMAND_TYPE_RELEASE_AWARE:
                    Log.wtf(TAG,
                            "processTimeout: COMMAND_TYPE_RELEASE_AWARE - shouldn't be waiting!");
                    break;
                case COMMAND_TYPE_DISABLE:
                    onDisableResponseLocal(mCurrentCommand, NanStatusCode.INTERNAL_FAILURE);
                    break;
                default:
                    Log.wtf(TAG, "processTimeout: this isn't a COMMAND -- msg=" + msg);
                    /* fall-through */
            }

            mCurrentCommand = null;
            mCurrentTransactionId = TRANSACTION_ID_IGNORE;
        }

        private void updateSendMessageTimeout() {
            if (mVdbg) {
                Log.v(TAG, "updateSendMessageTimeout: mHostQueuedSendMessages.size()="
                        + mHostQueuedSendMessages.size() + ", mFwQueuedSendMessages.size()="
                        + mFwQueuedSendMessages.size() + ", mSendQueueBlocked="
                        + mSendQueueBlocked);
            }
            Iterator<Message> it = mFwQueuedSendMessages.values().iterator();
            if (it.hasNext()) {
                /*
                 * Schedule timeout based on the first message in the queue (which is the earliest
                 * submitted message). Timeout = queuing time + timeout constant.
                 */
                Message msg = it.next();
                mSendMessageTimeoutMessage.schedule(
                        msg.getData().getLong(MESSAGE_BUNDLE_KEY_SEND_MESSAGE_ENQUEUE_TIME)
                        + AWARE_SEND_MESSAGE_TIMEOUT);
            } else {
                mSendMessageTimeoutMessage.cancel();
            }
        }

        private void processSendMessageTimeout() {
            if (mVdbg) {
                Log.v(TAG, "processSendMessageTimeout: mHostQueuedSendMessages.size()="
                        + mHostQueuedSendMessages.size() + ", mFwQueuedSendMessages.size()="
                        + mFwQueuedSendMessages.size() + ", mSendQueueBlocked="
                        + mSendQueueBlocked);
            }

            /*
             * Note: using 'first' to always time-out (remove) at least 1 notification (partially)
             * due to test code needs: there's no way to mock elapsedRealtime(). TODO: replace with
             * injected getClock() once moved off of mmwd.
             */
            boolean first = true;
            long currentTime = SystemClock.elapsedRealtime();
            Iterator<Map.Entry<Short, Message>> it = mFwQueuedSendMessages.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Short, Message> entry = it.next();
                short transactionId = entry.getKey();
                Message message = entry.getValue();
                long messageEnqueueTime = message.getData().getLong(
                        MESSAGE_BUNDLE_KEY_SEND_MESSAGE_ENQUEUE_TIME);
                if (first || messageEnqueueTime + AWARE_SEND_MESSAGE_TIMEOUT <= currentTime) {
                    if (mVerboseLoggingEnabled) {
                        Log.v(TAG, "processSendMessageTimeout: expiring - transactionId="
                                + transactionId + ", message=" + message
                                + ", due to messageEnqueueTime=" + messageEnqueueTime
                                + ", currentTime=" + currentTime);
                    }
                    onMessageSendFailLocal(message, NanStatusCode.INTERNAL_FAILURE);
                    it.remove();
                    first = false;
                } else {
                    break;
                }
            }
            updateSendMessageTimeout();
            mSendQueueBlocked = false;
            transmitNextMessage();
        }

        private boolean isUidExceededMessageQueueDepthLimit(int uid) {
            int size = mHostQueuedSendMessages.size();
            int numOfMessages = 0;
            if (size < MESSAGE_QUEUE_DEPTH_PER_UID) {
                return false;
            }
            for (int i = 0; i < size; ++i) {
                if (mHostQueuedSendMessages.valueAt(i).getData()
                        .getInt(MESSAGE_BUNDLE_KEY_UID) == uid) {
                    numOfMessages++;
                    if (numOfMessages >= MESSAGE_QUEUE_DEPTH_PER_UID) {
                        return true;
                    }
                }
            }
            return false;
        }

        @Override
        protected String getLogRecString(Message msg) {
            StringBuilder sb = new StringBuilder(WifiAwareStateManager.messageToString(msg));

            if (msg.what == MESSAGE_TYPE_COMMAND
                    && mCurrentTransactionId != TRANSACTION_ID_IGNORE) {
                sb.append(" (Transaction ID=").append(mCurrentTransactionId).append(")");
            }

            return sb.toString();
        }

        @Override
        public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
            pw.println("WifiAwareStateMachine:");
            pw.println("  mNextTransactionId: " + mNextTransactionId);
            pw.println("  mNextSessionId: " + mNextSessionId);
            pw.println("  mCurrentCommand: " + mCurrentCommand);
            pw.println("  mCurrentTransaction: " + mCurrentTransactionId);
            pw.println("  mSendQueueBlocked: " + mSendQueueBlocked);
            pw.println("  mSendArrivalSequenceCounter: " + mSendArrivalSequenceCounter);
            pw.println("  mHostQueuedSendMessages: [" + mHostQueuedSendMessages + "]");
            pw.println("  mFwQueuedSendMessages: [" + mFwQueuedSendMessages + "]");
            super.dump(fd, pw, args);
        }
    }

    private void sendAwareStateChangedBroadcast(boolean enabled) {
        if (mVdbg) {
            Log.v(TAG, "sendAwareStateChangedBroadcast: enabled=" + enabled);
        }
        final Intent intent = new Intent(WifiAwareManager.ACTION_WIFI_AWARE_STATE_CHANGED);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
        mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
    }

    private void sendAwareResourcesChangedBroadcast() {
        if (!SdkLevel.isAtLeastT()) {
            return;
        }
        if (mVdbg) {
            Log.v(TAG, "sendAwareResourcesChangedBroadcast");
        }
        final Intent intent = new Intent(WifiAwareManager.ACTION_WIFI_AWARE_RESOURCE_CHANGED);
        intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY);
        intent.putExtra(WifiAwareManager.EXTRA_AWARE_RESOURCES, getAvailableAwareResources());
        mContext.sendBroadcastAsUser(intent, UserHandle.ALL, ACCESS_WIFI_STATE);
    }

    /*
     * COMMANDS
     */

    private boolean connectLocal(short transactionId, int clientId, int uid, int pid,
            String callingPackage, @Nullable String callingFeatureId,
            IWifiAwareEventCallback callback, ConfigRequest configRequest,
            boolean notifyIdentityChange, Object attributionSource, boolean awareOffload,
            boolean reEnableAware, int callerType) {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG,
                    "connectLocal(): transactionId=" + transactionId + ", clientId=" + clientId
                            + ", uid=" + uid + ", pid=" + pid + ", callingPackage=" + callingPackage
                            + ", callback=" + callback + ", configRequest=" + configRequest
                            + ", notifyIdentityChange=" + notifyIdentityChange
                            + ", awareOffload" + awareOffload
                            + ", reEnableAware" + reEnableAware);
        }
        if (!mUsageEnabled) {
            Log.w(TAG, "connect(): called with mUsageEnabled=false");
            try {
                callback.onConnectFail(NanStatusCode.INTERNAL_FAILURE);
                mAwareMetrics.recordAttachStatus(NanStatusCode.INTERNAL_FAILURE, callerType,
                        callingFeatureId, uid);
            } catch (RemoteException e) {
                Log.w(TAG, "connectLocal onConnectFail(): RemoteException (FYI): " + e);
            }
            return false;
        }

        if (mClients.get(clientId) != null) {
            Log.e(TAG, "connectLocal: entry already exists for clientId=" + clientId);
        }

        if (mVdbg) {
            Log.v(TAG, "mCurrentAwareConfiguration=" + mCurrentAwareConfiguration
                    + ", mCurrentIdentityNotification=" + mCurrentIdentityNotification);
        }

        ConfigRequest merged = mergeConfigRequests(configRequest);
        if (merged == null) {
            Log.e(TAG, "connectLocal: requested configRequest=" + configRequest
                    + ", incompatible with current configurations");
            try {
                callback.onConnectFail(NanStatusCode.INTERNAL_FAILURE);
                mAwareMetrics.recordAttachStatus(NanStatusCode.INTERNAL_FAILURE, callerType,
                        callingFeatureId, uid);
            } catch (RemoteException e) {
                Log.w(TAG, "connectLocal onConnectFail(): RemoteException (FYI): " + e);
            }
            return false;
        } else if (mVdbg) {
            Log.v(TAG, "connectLocal: merged=" + merged);
        }

        if (mCurrentAwareConfiguration != null && mCurrentAwareConfiguration.equals(merged)
                && (mCurrentIdentityNotification || !notifyIdentityChange)
                && !reEnableAware) {
            if (awareOffload && !isAwareOffloading()) {
                try {
                    if (mVdbg) {
                        Log.v(TAG, "Connect failure for clientId:" + clientId);
                    }
                    callback.onConnectFail(clientId);
                } catch (RemoteException e) {
                    Log.w(TAG, "connectLocal onConnectFail(): RemoteException (FYI): " + e);
                }
                return false;
            }
            WifiAwareClientState client = new WifiAwareClientState(mContext, clientId, uid, pid,
                    callingPackage, callingFeatureId, callback, configRequest, notifyIdentityChange,
                    SystemClock.elapsedRealtime(), mWifiPermissionsUtil, attributionSource,
                    awareOffload, callerType);
            client.enableVerboseLogging(mVerboseLoggingEnabled, mVdbg);
            client.onClusterChange(mClusterEventType, mClusterId, mCurrentDiscoveryInterfaceMac);
            mClients.append(clientId, client);
            mAwareMetrics.recordAttachSession(uid, notifyIdentityChange, mClients, callerType,
                    callingFeatureId);
            try {
                if (mVdbg) {
                    Log.v(TAG, "Connect success for clientId:" + clientId);
                }
                callback.onConnectSuccess(clientId);
            } catch (RemoteException e) {
                Log.w(TAG, "connectLocal onConnectSuccess(): RemoteException (FYI): " + e);
            }
            if (!mWifiAwareNativeManager.replaceRequestorWs(createMergedRequestorWs())) {
                Log.w(TAG, "Failed to replace requestorWs");
            }
            return false;
        }
        boolean notificationRequired =
                doesAnyClientNeedIdentityChangeNotifications() || notifyIdentityChange;
        boolean rangingRequired = doesAnyClientNeedRanging();
        int instantMode = getInstantModeFromAllClients();
        boolean enableInstantMode = false;
        int instantModeChannel = 0;
        if (instantMode != INSTANT_MODE_DISABLED || mInstantCommModeGlobalEnable) {
            enableInstantMode = true;
            instantModeChannel = getAwareInstantCommunicationChannel(instantMode);
        }

        if (mCurrentAwareConfiguration == null) {
            WorkSource workSource;
            if (awareOffload || mOpportunisticSet.contains(callingPackage)) {
                workSource = new WorkSource(Process.WIFI_UID);
            } else {
                workSource = new WorkSource(uid, callingPackage);
            }
            mWifiAwareNativeManager.tryToGetAware(workSource);
        }
        boolean initialConfiguration = mCurrentAwareConfiguration == null
                || reEnableAware;
        boolean success = mWifiAwareNativeApi.enableAndConfigure(transactionId, merged,
                notificationRequired, initialConfiguration,
                mPowerManager.isInteractive(), mPowerManager.isDeviceIdleMode(),
                rangingRequired, enableInstantMode, instantModeChannel, mClusterIdInt);
        if (!success) {
            if (mCurrentAwareConfiguration == null) {
                mWifiAwareNativeManager.releaseAware();
            }
            try {
                callback.onConnectFail(NanStatusCode.INTERNAL_FAILURE);
                mAwareMetrics.recordAttachStatus(NanStatusCode.INTERNAL_FAILURE, callerType,
                        callingFeatureId, uid);
            } catch (RemoteException e) {
                Log.w(TAG, "connectLocal onConnectFail(): RemoteException (FYI):  " + e);
            }
        }

        return success;
    }

    private boolean disconnectLocal(short transactionId, int clientId) {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "disconnectLocal(): transactionId=" + transactionId
                    + ", clientId=" + clientId);
        }

        WifiAwareClientState client = mClients.get(clientId);
        if (client == null) {
            Log.e(TAG, "disconnectLocal: no entry for clientId=" + clientId);
            return false;
        }
        mClients.delete(clientId);
        mAwareMetrics.recordAttachSessionDuration(client.getCreationTime());
        SparseArray<WifiAwareDiscoverySessionState> sessions = client.getSessions();
        for (int i = 0; i < sessions.size(); ++i) {
            mAwareMetrics.recordDiscoverySessionDuration(sessions.valueAt(i).getCreationTime(),
                    sessions.valueAt(i).isPublishSession(), sessions.valueAt(i).getSessionId());
        }
        client.destroy();

        if (mClients.size() == 0) {
            mCurrentAwareConfiguration = null;
            mPairingRequest.clear();
            mDataPathMgr.deleteAllInterfaces();
            mCurrentRangingEnabled = false;
            mCurrentIdentityNotification = false;
            mInstantCommModeClientRequest = INSTANT_MODE_DISABLED;
            mAwareMetrics.reportAwareInstantModeEnabled(false);
            deferDisableAware(true);
            return false;
        }

        if (!mWifiAwareNativeManager.replaceRequestorWs(createMergedRequestorWs())) {
            Log.w(TAG, "Failed to replace requestorWs");
        }

        ConfigRequest merged = mergeConfigRequests(null);
        if (merged == null) {
            Log.wtf(TAG, "disconnectLocal: got an incompatible merge on remaining configs!?");
            return false;
        }
        boolean notificationReqs = doesAnyClientNeedIdentityChangeNotifications();
        boolean rangingEnabled = doesAnyClientNeedRanging();
        int instantMode = getInstantModeFromAllClients();
        boolean enableInstantMode = false;
        int instantModeChannel = 0;
        if (instantMode != INSTANT_MODE_DISABLED || mInstantCommModeGlobalEnable) {
            enableInstantMode = true;
            instantModeChannel = getAwareInstantCommunicationChannel(instantMode);
        }
        if (merged.equals(mCurrentAwareConfiguration)
                && mCurrentIdentityNotification == notificationReqs
                && mCurrentRangingEnabled == rangingEnabled
                && mInstantCommModeClientRequest == instantMode) {
            return false;
        }

        return mWifiAwareNativeApi.enableAndConfigure(transactionId, merged, notificationReqs,
                false, mPowerManager.isInteractive(), mPowerManager.isDeviceIdleMode(),
                rangingEnabled, enableInstantMode, instantModeChannel, mClusterIdInt);
    }

    private boolean reconfigureLocal(short transactionId) {
        if (mVdbg) Log.v(TAG, "reconfigureLocal(): transactionId=" + transactionId);

        if (mClients.size() == 0) {
            // no clients - Aware is not enabled, nothing to reconfigure
            return false;
        }

        boolean notificationReqs = doesAnyClientNeedIdentityChangeNotifications();
        boolean rangingEnabled = doesAnyClientNeedRanging();
        int instantMode = getInstantModeFromAllClients();
        boolean enableInstantMode = false;
        int instantModeChannel = 0;
        if (instantMode != INSTANT_MODE_DISABLED || mInstantCommModeGlobalEnable) {
            enableInstantMode = true;
            instantModeChannel = getAwareInstantCommunicationChannel(instantMode);
        }

        return mWifiAwareNativeApi.enableAndConfigure(transactionId, mergeConfigRequests(null),
                notificationReqs, false, mPowerManager.isInteractive(),
                mPowerManager.isDeviceIdleMode(), rangingEnabled,
                enableInstantMode, instantModeChannel, mClusterIdInt);
    }

    private void terminateSessionLocal(int clientId, int sessionId) {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "terminateSessionLocal(): clientId=" + clientId
                    + ", sessionId=" + sessionId);
        }

        WifiAwareClientState client = mClients.get(clientId);
        if (client == null) {
            Log.e(TAG, "terminateSession: no client exists for clientId=" + clientId);
            return;
        }

        WifiAwareDiscoverySessionState session = client.terminateSession(sessionId);
        // If Ranging enabled or instant mode require changes, reconfigure.
        if (mCurrentRangingEnabled != doesAnyClientNeedRanging()
                || mInstantCommModeClientRequest != getInstantModeFromAllClients()) {
            reconfigure();
        }
        if (session != null) {
            mAwareMetrics.recordDiscoverySessionDuration(session.getCreationTime(),
                    session.isPublishSession(), sessionId);
        }
        sendAwareResourcesChangedBroadcast();
    }

    private boolean publishLocal(short transactionId, int clientId, PublishConfig publishConfig,
            IWifiAwareDiscoverySessionCallback callback) {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "publishLocal(): transactionId=" + transactionId + ", clientId=" + clientId
                    + ", publishConfig=" + publishConfig + ", callback=" + callback);
        }

        WifiAwareClientState client = mClients.get(clientId);
        if (client == null) {
            Log.e(TAG, "publishLocal: no client exists for clientId=" + clientId);
            try {
                callback.onSessionConfigFail(NanStatusCode.INTERNAL_FAILURE);
            } catch (RemoteException e) {
                Log.w(TAG, "publishLocal onSessionConfigFail(): RemoteException (FYI): " + e);
            }
            return false;
        }
        AwarePairingConfig pairingConfig = publishConfig.getPairingConfig();
        byte[] nik = null;
        if (pairingConfig != null && pairingConfig.isPairingVerificationEnabled()) {
            nik = mPairingConfigManager
                    .getNikForCallingPackage(client.getCallingPackage());
        }
        boolean success = mWifiAwareNativeApi.publish(transactionId, (byte) 0, publishConfig, nik);
        if (!success) {
            try {
                callback.onSessionConfigFail(NanStatusCode.INTERNAL_FAILURE);
            } catch (RemoteException e) {
                Log.w(TAG, "publishLocal onSessionConfigFail(): RemoteException (FYI): " + e);
            }
            mAwareMetrics.recordDiscoveryStatus(client.getUid(), NanStatusCode.INTERNAL_FAILURE,
                    true, client.mCallerType, client.mCallingFeatureId);
        }

        return success;
    }

    private boolean updatePublishLocal(short transactionId, int clientId, int sessionId,
            PublishConfig publishConfig) {
        if (mVdbg) {
            Log.v(TAG, "updatePublishLocal(): transactionId=" + transactionId + ", clientId="
                    + clientId + ", sessionId=" + sessionId + ", publishConfig=" + publishConfig);
        }

        WifiAwareClientState client = mClients.get(clientId);
        if (client == null) {
            Log.e(TAG, "updatePublishLocal: no client exists for clientId=" + clientId);
            return false;
        }

        WifiAwareDiscoverySessionState session = client.getSession(sessionId);
        if (session == null) {
            Log.e(TAG, "updatePublishLocal: no session exists for clientId=" + clientId
                    + ", sessionId=" + sessionId);
            return false;
        }

        AwarePairingConfig pairingConfig = publishConfig.getPairingConfig();
        byte[] nik = null;
        if (pairingConfig != null && pairingConfig.isPairingVerificationEnabled()) {
            nik = mPairingConfigManager.getNikForCallingPackage(
                    client.getCallingPackage());
        }

        boolean status = session.updatePublish(transactionId, publishConfig, nik);
        if (!status) {
            mAwareMetrics.recordDiscoveryStatus(client.getUid(), NanStatusCode.INTERNAL_FAILURE,
                    true, client.mCallerType, client.mCallingFeatureId);
        }
        return status;
    }

    private boolean subscribeLocal(short transactionId, int clientId,
            SubscribeConfig subscribeConfig, IWifiAwareDiscoverySessionCallback callback) {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "subscribeLocal(): transactionId=" + transactionId + ", clientId=" + clientId
                    + ", subscribeConfig=" + subscribeConfig + ", callback=" + callback);
        }

        WifiAwareClientState client = mClients.get(clientId);
        if (client == null) {
            try {
                callback.onSessionConfigFail(NanStatusCode.INTERNAL_FAILURE);
            } catch (RemoteException e) {
                Log.w(TAG, "subscribeLocal onSessionConfigFail(): RemoteException (FYI): " + e);
            }
            Log.e(TAG, "subscribeLocal: no client exists for clientId=" + clientId);
            return false;
        }
        AwarePairingConfig pairingConfig = subscribeConfig.getPairingConfig();
        byte[] nik = null;
        if (pairingConfig != null && pairingConfig.isPairingVerificationEnabled()) {
            nik = mPairingConfigManager.getNikForCallingPackage(
                    client.getCallingPackage());
        }

        boolean success = mWifiAwareNativeApi.subscribe(transactionId, (byte) 0, subscribeConfig,
                nik);
        if (!success) {
            try {
                callback.onSessionConfigFail(NanStatusCode.INTERNAL_FAILURE);
            } catch (RemoteException e) {
                Log.w(TAG, "subscribeLocal onSessionConfigFail(): RemoteException (FYI): " + e);
            }
            mAwareMetrics.recordDiscoveryStatus(client.getUid(), NanStatusCode.INTERNAL_FAILURE,
                    false, client.mCallerType, client.mCallingFeatureId);
        }

        return success;
    }

    private boolean updateSubscribeLocal(short transactionId, int clientId, int sessionId,
            SubscribeConfig subscribeConfig) {
        if (mVdbg) {
            Log.v(TAG,
                    "updateSubscribeLocal(): transactionId=" + transactionId + ", clientId="
                            + clientId + ", sessionId=" + sessionId + ", subscribeConfig="
                            + subscribeConfig);
        }

        WifiAwareClientState client = mClients.get(clientId);
        if (client == null) {
            Log.e(TAG, "updateSubscribeLocal: no client exists for clientId=" + clientId);
            return false;
        }

        WifiAwareDiscoverySessionState session = client.getSession(sessionId);
        if (session == null) {
            Log.e(TAG, "updateSubscribeLocal: no session exists for clientId=" + clientId
                    + ", sessionId=" + sessionId);
            return false;
        }
        AwarePairingConfig pairingConfig = subscribeConfig.getPairingConfig();
        byte[] nik = null;
        if (pairingConfig != null && pairingConfig.isPairingVerificationEnabled()) {
            nik = mPairingConfigManager.getNikForCallingPackage(
                    client.getCallingPackage());
        }
        boolean status = session.updateSubscribe(transactionId, subscribeConfig, nik);
        if (!status) {
            mAwareMetrics.recordDiscoveryStatus(client.getUid(), NanStatusCode.INTERNAL_FAILURE,
                    false, client.mCallerType, client.mCallingFeatureId);
        }
        return status;
    }

    private boolean initiateNanPairingRequestLocal(short transactionId, int clientId, int sessionId,
            int peerId, String password, int requestType, int akm,
            byte[] pmk, int cipherSuite) {
        if (mVdbg) {
            Log.v(TAG, "initiateNanPairingRequestLocal: transactionId=" + transactionId
                    + ", clientId=" + clientId + ", sessionId=" + sessionId + ", peerId=" + peerId);
        }
        WifiAwareClientState client = mClients.get(clientId);
        if (client == null) {
            Log.e(TAG, "initiateNanPairingRequestLocal: no client exists for clientId=" + clientId);
            return false;
        }

        WifiAwareDiscoverySessionState session = client.getSession(sessionId);
        if (session == null) {
            Log.e(TAG, "initiateNanPairingRequestLocal: no session exists for clientId="
                    + clientId + ", sessionId=" + sessionId);
            return false;
        }
        return session.initiatePairing(transactionId, peerId, password, requestType,
                mPairingConfigManager.getNikForCallingPackage(client.getCallingPackage()),
                pmk, akm, cipherSuite);
    }

    private boolean respondToPairingRequestLocal(short transactionId, int clientId, int sessionId,
            int peerId, int pairingId, boolean accept, int requestType, byte[] pmk,
            String password, int akm, int cipherSuite) {
        if (mVdbg) {
            Log.v(TAG,
                    "respondToPairingRequestLocal: transactionId=" + transactionId + ", clientId="
                            + clientId + ", sessionId=" + sessionId + ", peerId="
                            + peerId);
        }
        WifiAwareClientState client = mClients.get(clientId);
        if (client == null) {
            Log.e(TAG, "respondToPairingRequestLocal: no client exists for clientId=" + clientId);
            return false;
        }

        WifiAwareDiscoverySessionState session = client.getSession(sessionId);
        if (session == null) {
            Log.e(TAG, "respondToPairingRequestLocal: no session exists for clientId=" + clientId
                    + ", sessionId=" + sessionId);
            return false;
        }
        return session.respondToPairingRequest(transactionId, peerId, pairingId,
                accept,
                mPairingConfigManager.getNikForCallingPackage(client.getCallingPackage()),
                requestType, pmk, password, akm, cipherSuite);
    }

    private boolean initiateBootstrappingRequestLocal(short transactionId, int clientId,
            int sessionId, int peerId, int method, byte[] cookie, boolean isComeBack) {
        String methodString = "initiateBootstrappingRequestLocal";
        if (mVdbg) {
            Log.v(TAG, methodString + ": transactionId=" + transactionId
                    + ", clientId=" + clientId + ", sessionId=" + sessionId + ", peerId=" + peerId);
        }
        WifiAwareDiscoverySessionState session = getClientSession(clientId, sessionId,
                methodString);
        if (session == null) {
            return false;
        }
        return session.initiateBootstrapping(transactionId, peerId, method, cookie, isComeBack);
    }

    private boolean respondToBootstrappingRequestLocal(short transactionId, int clientId,
            int sessionId, int peerId, int bootstrappingId, boolean accept, int method) {
        String methodString = "respondToBootstrappingRequestLocal";
        if (mVdbg) {
            Log.v(TAG, methodString + ": transactionId=" + transactionId
                    + ", clientId=" + clientId + ", sessionId=" + sessionId + ", peerId=" + peerId);
        }
        WifiAwareDiscoverySessionState session = getClientSession(clientId, sessionId,
                methodString);
        if (session == null) {
            return false;
        }
        return session.respondToBootstrapping(transactionId, peerId, bootstrappingId, accept,
                method);
    }

    private boolean sendFollowonMessageLocal(short transactionId, int clientId, int sessionId,
            int peerId, byte[] message, int messageId) {
        String methodString = "sendFollowonMessageLocal";
        if (mVdbg) {
            Log.v(TAG, methodString + "(): transactionId=" + transactionId + ", clientId="
                    + clientId + ", sessionId=" + sessionId + ", peerId=" + peerId
                    + ", messageId=" + messageId);
        }

        WifiAwareDiscoverySessionState session = getClientSession(clientId, sessionId,
                methodString);
        if (session == null) {
            return false;
        }

        return session.sendMessage(transactionId, peerId, message, messageId);
    }

    private void enableUsageLocal() {
        Log.d(TAG, "enableUsageLocal: mUsageEnabled=" + mUsageEnabled);

        if (mUsageEnabled) {
            return;
        }
        mUsageEnabled = true;
        sendAwareStateChangedBroadcast(true);

        mAwareMetrics.recordEnableUsage();
    }

    private void disableUsageLocal(short transactionId, boolean markAsAvailable) {
        Log.d(TAG, "disableUsageLocal: transactionId=" + transactionId + ", mUsageEnabled="
                    + mUsageEnabled);

        if (!mUsageEnabled) {
            return;
        }
        onAwareDownLocal();

        mUsageEnabled = markAsAvailable;
        mCurrentRangingEnabled = false;
        mCurrentIdentityNotification = false;
        mInstantCommModeClientRequest = INSTANT_MODE_DISABLED;
        mAwareMetrics.reportAwareInstantModeEnabled(false);
        deferDisableAware(true);
        sendAwareStateChangedBroadcast(markAsAvailable);
        if (!markAsAvailable) {
            mAwareMetrics.recordDisableUsage();
        }
    }

    private boolean initiateDataPathSetupLocal(short transactionId,
            WifiAwareNetworkSpecifier networkSpecifier, int peerId, int channelRequestType,
            int channel, byte[] peer, String interfaceName,
            boolean isOutOfBand, byte[] appInfo) {
        WifiAwareDataPathSecurityConfig securityConfig = networkSpecifier
                .getWifiAwareDataPathSecurityConfig();
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "initiateDataPathSetupLocal(): transactionId=" + transactionId
                    + ", networkSpecifier=" + networkSpecifier + ", peerId=" + peerId
                    + ", channelRequestType=" + channelRequestType + ", channel=" + channel
                    + ", peer="
                    + String.valueOf(HexEncoding.encode(peer)) + ", interfaceName=" + interfaceName
                    + ", securityConfig=" + ((securityConfig == null) ? "" : securityConfig)
                    + ", isOutOfBand="
                    + isOutOfBand + ", appInfo=" + (appInfo == null ? "<null>" : "<non-null>"));
        }
        byte pubSubId = 0;
        if (!isOutOfBand) {
            WifiAwareClientState client = mClients.get(networkSpecifier.clientId);
            if (client == null) {
                Log.e(TAG, "initiateDataPathSetupLocal: no client exists for clientId="
                        + networkSpecifier.clientId);
                return false;
            }

            WifiAwareDiscoverySessionState session = client.getSession(networkSpecifier.sessionId);
            if (session == null) {
                Log.e(TAG, "initiateDataPathSetupLocal: no session exists for clientId="
                        + networkSpecifier.clientId + ", sessionId=" + networkSpecifier.sessionId);
                return false;
            }
            pubSubId = (byte) session.getPubSubId();
        }
        boolean success = mWifiAwareNativeApi.initiateDataPath(transactionId, peerId,
                channelRequestType, channel, peer, interfaceName, isOutOfBand,
                appInfo, mCapabilities, networkSpecifier.getWifiAwareDataPathSecurityConfig(),
                pubSubId);
        if (!success) {
            mDataPathMgr.onDataPathInitiateFail(networkSpecifier, NanStatusCode.INTERNAL_FAILURE);
        }

        return success;
    }

    private boolean respondToDataPathRequestLocal(short transactionId, boolean accept,
            int ndpId, String interfaceName, byte[] appInfo, boolean isOutOfBand,
            WifiAwareNetworkSpecifier networkSpecifier) {
        WifiAwareDataPathSecurityConfig securityConfig = accept ? networkSpecifier
                .getWifiAwareDataPathSecurityConfig() : null;
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "respondToDataPathRequestLocal(): transactionId=" + transactionId
                    + ", accept=" + accept + ", ndpId=" + ndpId + ", interfaceName=" + interfaceName
                    + ", securityConfig=" + securityConfig
                    + ", isOutOfBand=" + isOutOfBand
                    + ", appInfo=" + (appInfo == null ? "<null>" : "<non-null>"));
        }
        byte pubSubId = 0;
        if (!isOutOfBand && accept) {
            WifiAwareClientState client = mClients.get(networkSpecifier.clientId);
            if (client == null) {
                Log.e(TAG, "respondToDataPathRequestLocal: no client exists for clientId="
                        + networkSpecifier.clientId);
                return false;
            }

            WifiAwareDiscoverySessionState session = client.getSession(networkSpecifier.sessionId);
            if (session == null) {
                Log.e(TAG, "respondToDataPathRequestLocal: no session exists for clientId="
                        + networkSpecifier.clientId + ", sessionId=" + networkSpecifier.sessionId);
                return false;
            }
            pubSubId = (byte) session.getPubSubId();
        }
        boolean success = mWifiAwareNativeApi.respondToDataPathRequest(transactionId, accept, ndpId,
                interfaceName, appInfo, isOutOfBand, mCapabilities, securityConfig, pubSubId);
        if (!success) {
            mDataPathMgr.onRespondToDataPathRequest(ndpId, false, NanStatusCode.INTERNAL_FAILURE);
        } else {
            sendAwareResourcesChangedBroadcast();
        }
        return success;
    }

    private boolean endDataPathLocal(short transactionId, int ndpId) {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "endDataPathLocal: transactionId=" + transactionId + ", ndpId=" + ndpId);
        }
        sendAwareResourcesChangedBroadcast();
        return mWifiAwareNativeApi.endDataPath(transactionId, ndpId);
    }

    private boolean endPairingLocal(short transactionId, int pairId) {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "endPairingLocal: transactionId=" + transactionId + ", pairId=" + pairId);
        }
        return mWifiAwareNativeApi.endPairing(transactionId, pairId);
    }

    /*
     * RESPONSES
     */

    private void onConfigCompletedLocal(Message completedCommand) {
        Log.d(TAG, "onConfigCompleted: completedCommand=" + completedCommand);

        if (completedCommand.arg1 == COMMAND_TYPE_CONNECT) {
            if (mCurrentAwareConfiguration == null) { // enabled (as opposed to re-configured)
                queryCapabilities();
                mDataPathMgr.createAllInterfaces();
                recordHalApiCall(COMMAND_TYPE_CONNECT, NanStatusCode.SUCCESS, mStartTime);
            } else {
                recordHalApiCall(COMMAND_TYPE_RECONFIGURE, NanStatusCode.SUCCESS, mStartTime);
            }

            Bundle data = completedCommand.getData();

            int clientId = completedCommand.arg2;
            Pair<IWifiAwareEventCallback, Object> callbackAndAttributionSource =
                    (Pair<IWifiAwareEventCallback, Object>) completedCommand.obj;
            IWifiAwareEventCallback callback = callbackAndAttributionSource.first;
            ConfigRequest configRequest = (ConfigRequest) data
                    .getParcelable(MESSAGE_BUNDLE_KEY_CONFIG);
            int uid = data.getInt(MESSAGE_BUNDLE_KEY_UID);
            int pid = data.getInt(MESSAGE_BUNDLE_KEY_PID);
            boolean notifyIdentityChange = data.getBoolean(
                    MESSAGE_BUNDLE_KEY_NOTIFY_IDENTITY_CHANGE);
            String callingPackage = data.getString(MESSAGE_BUNDLE_KEY_CALLING_PACKAGE);
            String callingFeatureId = data.getString(MESSAGE_BUNDLE_KEY_CALLING_FEATURE_ID);
            boolean awareOffload = data.getBoolean(MESSAGE_BUNDLE_KEY_AWARE_OFFLOAD);
            int callerType = data.getInt(MESSAGE_BUNDLE_KEY_CALLER_TYPE);

            WifiAwareClientState client = new WifiAwareClientState(mContext, clientId, uid, pid,
                    callingPackage, callingFeatureId, callback, configRequest, notifyIdentityChange,
                    SystemClock.elapsedRealtime(), mWifiPermissionsUtil,
                    callbackAndAttributionSource.second, awareOffload, callerType);
            client.enableVerboseLogging(mVerboseLoggingEnabled, mVdbg);
            mClients.put(clientId, client);
            mAwareMetrics.recordAttachSession(uid, notifyIdentityChange, mClients, callerType,
                    callingFeatureId);
            try {
                if (mVdbg) {
                    Log.v(TAG, "Connect success for clientId:" + clientId);
                }
                callback.onConnectSuccess(clientId);
            } catch (RemoteException e) {
                Log.w(TAG,
                        "onConfigCompletedLocal onConnectSuccess(): RemoteException (FYI): " + e);
            }
            client.onClusterChange(mClusterEventType, mClusterId, mCurrentDiscoveryInterfaceMac);
        } else if (completedCommand.arg1 == COMMAND_TYPE_DISCONNECT) {
            recordHalApiCall(COMMAND_TYPE_RECONFIGURE, NanStatusCode.SUCCESS, mStartTime);
            /*
             * NOP (i.e. updated configuration after disconnecting a client)
             */
        } else if (completedCommand.arg1 == COMMAND_TYPE_RECONFIGURE) {
            recordHalApiCall(COMMAND_TYPE_RECONFIGURE, NanStatusCode.SUCCESS, mStartTime);
            /*
             * NOP (i.e. updated configuration at power saving event)
             */
        } else {
            Log.wtf(TAG, "onConfigCompletedLocal: unexpected completedCommand=" + completedCommand);
            return;
        }

        mCurrentAwareConfiguration = mergeConfigRequests(null);
        if (mCurrentAwareConfiguration == null) {
            Log.wtf(TAG, "onConfigCompletedLocal: got a null merged configuration after config!?");
        }
        mCurrentIdentityNotification = doesAnyClientNeedIdentityChangeNotifications();
        mCurrentRangingEnabled = doesAnyClientNeedRanging();
        mInstantCommModeClientRequest = getInstantModeFromAllClients();
        if (mInstantCommModeClientRequest == INSTANT_MODE_DISABLED) {
            mAwareMetrics.reportAwareInstantModeEnabled(false);
            return;
        }
        mAwareMetrics.reportAwareInstantModeEnabled(true);
        if (!mInstantCommModeGlobalEnable) {
            // Change the instant communication mode when timeout
            mHandler.postDelayed(this::reconfigure, (long) mContext.getResources()
                    .getInteger(R.integer.config_wifiAwareInstantCommunicationModeDurationMillis));
        }
    }

    private void onConfigFailedLocal(Message failedCommand, int reason) {
        if (mVdbg) {
            Log.v(TAG,
                    "onConfigFailedLocal: failedCommand=" + failedCommand + ", reason=" + reason);
        }

        if (failedCommand.arg1 == COMMAND_TYPE_CONNECT) {
            mWifiAwareNativeManager.releaseAware();
            Pair<IWifiAwareEventCallback, Object> callbackAndAttributionSource =
                    (Pair<IWifiAwareEventCallback, Object>) failedCommand.obj;
            IWifiAwareEventCallback callback = callbackAndAttributionSource.first;
            try {
                callback.onConnectFail(reason);
                mAwareMetrics.recordAttachStatus(reason, 0, null, 0);
            } catch (RemoteException e) {
                Log.w(TAG, "onConfigFailedLocal onConnectFail(): RemoteException (FYI): " + e);
            }
            recordHalApiCall(COMMAND_TYPE_CONNECT, reason, mStartTime);
        } else if (failedCommand.arg1 == COMMAND_TYPE_DISCONNECT) {
            recordHalApiCall(COMMAND_TYPE_RECONFIGURE, reason, mStartTime);
            /*
             * NOP (tried updating configuration after disconnecting a client -
             * shouldn't fail but there's nothing to do - the old configuration
             * is still up-and-running).
             *
             * OR: timed-out getting a response to a disable. Either way a NOP.
             */
        } else if (failedCommand.arg1 == COMMAND_TYPE_RECONFIGURE) {
            recordHalApiCall(COMMAND_TYPE_RECONFIGURE, reason, mStartTime);
            /*
             * NOP (configuration change as part of possibly power saving event - should not
             * fail but there's nothing to do).
             */
        } else {
            Log.wtf(TAG, "onConfigFailedLocal: unexpected failedCommand=" + failedCommand);
        }
    }

    private void onDisableResponseLocal(Message command, int reason) {
        Log.d(TAG, "onDisableResponseLocal: command=" + command + ", reason=" + reason);
        /*
         * do nothing:
         * - success: was waiting so that don't enable while disabling
         * - fail: shouldn't happen (though can if already disabled for instance)
         */
        if (reason != NanStatusCode.SUCCESS) {
            Log.e(TAG, "onDisableResponseLocal: FAILED!? command=" + command + ", reason="
                    + reason);
        }

        boolean releaseAware = (boolean) command.obj;
        if (releaseAware) {
            // Need to release Aware
            mWifiAwareNativeManager.releaseAware();
        }
        mAwareMetrics.recordDisableAware();
    }

    private void onSessionConfigSuccessLocal(Message completedCommand, byte pubSubId,
            boolean isPublish) {
        if (mVdbg) {
            Log.v(TAG, "onSessionConfigSuccessLocal: completedCommand=" + completedCommand
                    + ", pubSubId=" + pubSubId + ", isPublish=" + isPublish);
        }

        boolean isRangingEnabled;
        boolean enableInstantMode;
        boolean isSuspendable;
        int instantModeBand;
        int minRange = -1;
        int maxRange = -1;
        AwarePairingConfig pairingConfig;
        if (isPublish) {
            PublishConfig publishConfig = completedCommand.getData().getParcelable(
                    MESSAGE_BUNDLE_KEY_CONFIG);
            isRangingEnabled = publishConfig.mEnableRanging;
            enableInstantMode = publishConfig.isInstantCommunicationModeEnabled();
            isSuspendable = SdkLevel.isAtLeastU() && publishConfig.isSuspendable();
            instantModeBand = publishConfig.getInstantCommunicationBand();
            pairingConfig = publishConfig.getPairingConfig();
        } else {
            SubscribeConfig subscribeConfig = completedCommand.getData().getParcelable(
                    MESSAGE_BUNDLE_KEY_CONFIG);
            isRangingEnabled =
                    subscribeConfig.mMinDistanceMmSet || subscribeConfig.mMaxDistanceMmSet;
            isSuspendable = SdkLevel.isAtLeastU() && subscribeConfig.isSuspendable();
            if (subscribeConfig.mMinDistanceMmSet) {
                minRange = subscribeConfig.mMinDistanceMm;
            }
            if (subscribeConfig.mMaxDistanceMmSet) {
                maxRange = subscribeConfig.mMaxDistanceMm;
            }
            enableInstantMode = subscribeConfig.isInstantCommunicationModeEnabled();
            instantModeBand = subscribeConfig.getInstantCommunicationBand();
            pairingConfig = subscribeConfig.getPairingConfig();
        }

        if (completedCommand.arg1 == COMMAND_TYPE_PUBLISH
                || completedCommand.arg1 == COMMAND_TYPE_SUBSCRIBE) {
            int clientId = completedCommand.arg2;
            IWifiAwareDiscoverySessionCallback callback =
                    (IWifiAwareDiscoverySessionCallback) completedCommand.obj;

            WifiAwareClientState client = mClients.get(clientId);
            if (client == null) {
                Log.e(TAG,
                        "onSessionConfigSuccessLocal: no client exists for clientId=" + clientId);
                return;
            }

            int sessionId = mSm.mNextSessionId++;
            try {
                if (mVdbg) {
                    Log.v(TAG,
                            (isPublish ? "publish" : "subscribe") + " session started, sessionId="
                                    + sessionId);
                }
                callback.onSessionStarted(sessionId);
            } catch (RemoteException e) {
                Log.e(TAG, "onSessionConfigSuccessLocal: onSessionStarted() RemoteException=" + e);
                return;
            }

            WifiAwareDiscoverySessionState session = new WifiAwareDiscoverySessionState(
                    mWifiAwareNativeApi, sessionId, pubSubId, callback, isPublish, isRangingEnabled,
                    SystemClock.elapsedRealtime(), enableInstantMode, instantModeBand,
                    isSuspendable, pairingConfig);
            session.enableVerboseLogging(mVerboseLoggingEnabled);
            client.addSession(session);

            if (isRangingEnabled) {
                mAwareMetrics.recordDiscoverySessionWithRanging(client.getUid(),
                        completedCommand.arg1 != COMMAND_TYPE_PUBLISH, minRange, maxRange,
                        mClients);
            } else {
                mAwareMetrics.recordDiscoverySession(client.getUid(), mClients);
            }
            mAwareMetrics.recordDiscoveryStatus(client.getUid(), NanStatusCode.SUCCESS,
                    completedCommand.arg1 == COMMAND_TYPE_PUBLISH, sessionId,
                    client.mCallerType, client.mCallingFeatureId);
            sendAwareResourcesChangedBroadcast();
        } else if (completedCommand.arg1 == COMMAND_TYPE_UPDATE_PUBLISH
                || completedCommand.arg1 == COMMAND_TYPE_UPDATE_SUBSCRIBE) {
            int clientId = completedCommand.arg2;
            int sessionId = completedCommand.getData().getInt(MESSAGE_BUNDLE_KEY_SESSION_ID);

            WifiAwareClientState client = mClients.get(clientId);
            if (client == null) {
                Log.e(TAG,
                        "onSessionConfigSuccessLocal: no client exists for clientId=" + clientId);
                return;
            }

            WifiAwareDiscoverySessionState session = client.getSession(sessionId);
            if (session == null) {
                Log.e(TAG, "onSessionConfigSuccessLocal: no session exists for clientId=" + clientId
                        + ", sessionId=" + sessionId);
                return;
            }

            try {
                session.getCallback().onSessionConfigSuccess();
            } catch (RemoteException e) {
                Log.e(TAG, "onSessionConfigSuccessLocal: onSessionConfigSuccess() RemoteException="
                        + e);
            }
            session.setRangingEnabled(isRangingEnabled);
            session.setInstantModeEnabled(enableInstantMode);
            session.setInstantModeBand(instantModeBand);
            mAwareMetrics.recordDiscoveryStatus(client.getUid(), NanStatusCode.SUCCESS,
                    completedCommand.arg1 == COMMAND_TYPE_UPDATE_PUBLISH,
                    client.mCallerType, client.mCallingFeatureId);
        } else {
            Log.wtf(TAG,
                    "onSessionConfigSuccessLocal: unexpected completedCommand=" + completedCommand);
            return;
        }
        // If Ranging enabled or instant mode require changes, reconfigure.
        if (mCurrentRangingEnabled != doesAnyClientNeedRanging()
                || mInstantCommModeClientRequest != getInstantModeFromAllClients()) {
            reconfigure();
        }
    }

    private void onSessionConfigFailLocal(Message failedCommand, boolean isPublish, int reason) {
        if (mVdbg) {
            Log.v(TAG, "onSessionConfigFailLocal: failedCommand=" + failedCommand + ", isPublish="
                    + isPublish + ", reason=" + reason);
        }

        if (failedCommand.arg1 == COMMAND_TYPE_PUBLISH
                || failedCommand.arg1 == COMMAND_TYPE_SUBSCRIBE) {
            int clientId = failedCommand.arg2;
            IWifiAwareDiscoverySessionCallback callback =
                    (IWifiAwareDiscoverySessionCallback) failedCommand.obj;

            WifiAwareClientState client = mClients.get(clientId);
            if (client == null) {
                Log.e(TAG, "onSessionConfigFailLocal: no client exists for clientId=" + clientId);
                return;
            }

            try {
                callback.onSessionConfigFail(reason);
            } catch (RemoteException e) {
                Log.w(TAG, "onSessionConfigFailLocal onSessionConfigFail(): RemoteException (FYI): "
                        + e);
            }
            mAwareMetrics.recordDiscoveryStatus(client.getUid(), reason,
                    failedCommand.arg1 == COMMAND_TYPE_PUBLISH, client.mCallerType,
                    client.mCallingFeatureId);
        } else if (failedCommand.arg1 == COMMAND_TYPE_UPDATE_PUBLISH
                || failedCommand.arg1 == COMMAND_TYPE_UPDATE_SUBSCRIBE) {
            int clientId = failedCommand.arg2;
            int sessionId = failedCommand.getData().getInt(MESSAGE_BUNDLE_KEY_SESSION_ID);

            WifiAwareClientState client = mClients.get(clientId);
            if (client == null) {
                Log.e(TAG, "onSessionConfigFailLocal: no client exists for clientId=" + clientId);
                return;
            }

            WifiAwareDiscoverySessionState session = client.getSession(sessionId);
            if (session == null) {
                Log.e(TAG, "onSessionConfigFailLocal: no session exists for clientId=" + clientId
                        + ", sessionId=" + sessionId);
                return;
            }

            try {
                session.getCallback().onSessionConfigFail(reason);
            } catch (RemoteException e) {
                Log.e(TAG, "onSessionConfigFailLocal: onSessionConfigFail() RemoteException=" + e);
            }
            mAwareMetrics.recordDiscoveryStatus(client.getUid(), reason,
                    failedCommand.arg1 == COMMAND_TYPE_UPDATE_PUBLISH, client.mCallerType,
                    client.mCallingFeatureId);

            if (reason == NanStatusCode.INVALID_SESSION_ID) {
                client.removeSession(sessionId);
                // If Ranging enabled or instant mode require changes, reconfigure.
                if (mCurrentRangingEnabled != doesAnyClientNeedRanging()
                        || mInstantCommModeClientRequest != getInstantModeFromAllClients()) {
                    reconfigure();
                }
                sendAwareResourcesChangedBroadcast();
            }
        } else {
            Log.wtf(TAG, "onSessionConfigFailLocal: unexpected failedCommand=" + failedCommand);
        }
    }

    private void onMessageSendSuccessLocal(Message completedCommand) {
        String methodString = "onMessageSendSuccessLocal";
        if (mVdbg) {
            Log.v(TAG, methodString + ": completedCommand=" + completedCommand);
        }

        int clientId = completedCommand.arg2;
        int sessionId = completedCommand.getData().getInt(MESSAGE_BUNDLE_KEY_SESSION_ID);
        int messageId = completedCommand.getData().getInt(MESSAGE_BUNDLE_KEY_MESSAGE_ID);

        WifiAwareDiscoverySessionState session = getClientSession(clientId, sessionId,
                methodString);
        if (session == null) {
            return;
        }

        try {
            session.getCallback().onMessageSendSuccess(messageId);
        } catch (RemoteException e) {
            Log.w(TAG, "onMessageSendSuccessLocal: RemoteException (FYI): " + e);
        }
    }

    private void onMessageSendFailLocal(Message failedCommand, int reason) {
        String methodString = "onMessageSendFailLocal";
        if (mVdbg) {
            Log.v(TAG, methodString + ": failedCommand=" + failedCommand + ", reason=" + reason);
        }

        int clientId = failedCommand.arg2;
        int sessionId = failedCommand.getData().getInt(MESSAGE_BUNDLE_KEY_SESSION_ID);
        int messageId = failedCommand.getData().getInt(MESSAGE_BUNDLE_KEY_MESSAGE_ID);

        WifiAwareDiscoverySessionState session = getClientSession(clientId, sessionId,
                methodString);
        if (session == null) {
            return;
        }

        try {
            session.getCallback().onMessageSendFail(messageId, reason);
        } catch (RemoteException e) {
            Log.e(TAG, "onMessageSendFailLocal: onMessageSendFail RemoteException=" + e);
        }
    }

    private void onCapabilitiesUpdatedResponseLocal(Capabilities capabilities) {
        if (mVdbg) {
            Log.v(TAG, "onCapabilitiesUpdatedResponseLocal: capabilites=" + capabilities);
        }

        mCapabilities = capabilities;
        mCharacteristics = null;
        if (mWifiAwarePullAtomCallback != null) {
            //Should only register this callback once
            return;
        }
        StatsManager statsManager = mContext.getSystemService(StatsManager.class);
        if (statsManager != null) {
            mWifiAwarePullAtomCallback = new WifiAwarePullAtomCallback();
            statsManager.setPullAtomCallback(WIFI_AWARE_CAPABILITIES, null,
                    new HandlerExecutor(mHandler), mWifiAwarePullAtomCallback);
        }
    }
    private class WifiAwarePullAtomCallback implements StatsManager.StatsPullAtomCallback {

        @Override
        public int onPullAtom(int atomTag, @androidx.annotation.NonNull List<StatsEvent> data) {
            data.add(WifiStatsLog.buildStatsEvent(atomTag,
                    mCapabilities.isInstantCommunicationModeSupported,
                    mCapabilities.isNanPairingSupported,
                    mCapabilities.isSuspensionSupported,
                    mCapabilities.supportedDataPathCipherSuites,
                    mCapabilities.maxNdiInterfaces,
                    mCapabilities.maxNdpSessions,
                    mCapabilities.maxPublishes));
            return StatsManager.PULL_SUCCESS;
        }
    }

    private void onCreateDataPathInterfaceResponseLocal(Message command, boolean success,
            int reasonOnFailure) {
        if (mVdbg) {
            Log.v(TAG, "onCreateDataPathInterfaceResponseLocal: command=" + command + ", success="
                    + success + ", reasonOnFailure=" + reasonOnFailure);
        }

        if (success) {
            if (mVdbg) {
                Log.v(TAG, "onCreateDataPathInterfaceResponseLocal: successfully created interface "
                        + command.obj);
            }
            mDataPathMgr.onInterfaceCreated((String) command.obj);
        } else {
            Log.e(TAG,
                    "onCreateDataPathInterfaceResponseLocal: failed when trying to create "
                            + "interface "
                            + command.obj + ". Reason code=" + reasonOnFailure);
        }
    }

    private void onDeleteDataPathInterfaceResponseLocal(Message command, boolean success,
            int reasonOnFailure) {
        if (mVdbg) {
            Log.v(TAG, "onDeleteDataPathInterfaceResponseLocal: command=" + command + ", success="
                    + success + ", reasonOnFailure=" + reasonOnFailure);
        }

        if (success) {
            if (mVdbg) {
                Log.v(TAG, "onDeleteDataPathInterfaceResponseLocal: successfully deleted interface "
                        + command.obj);
            }
            mDataPathMgr.onInterfaceDeleted((String) command.obj);
        } else {
            Log.e(TAG,
                    "onDeleteDataPathInterfaceResponseLocal: failed when trying to delete "
                            + "interface "
                            + command.obj + ". Reason code=" + reasonOnFailure);
        }
    }

    private boolean onRespondToPairingIndicationResponseSuccessLocal(Message command) {
        String methodString = "onRespondToPairingIndicationResponseSuccessLocal";
        if (mVdbg) {
            Log.v(TAG, methodString + ": command=" + command);
        }
        Bundle data = command.getData();
        PairingInfo pairingInfo = new PairingInfo(command.arg2,
                data.getInt(MESSAGE_BUNDLE_KEY_SESSION_ID),
                data.getInt(MESSAGE_BUNDLE_KEY_PEER_ID),
                data.getString(MESSAGE_BUNDLE_KEY_PAIRING_ALIAS));
        int requestId = data.getInt(MESSAGE_BUNDLE_KEY_PAIRING_REQUEST_ID);

        WifiAwareDiscoverySessionState session = getClientSession(pairingInfo.mClientId,
                pairingInfo.mSessionId, methodString);
        if (session == null) {
            return false;
        }
        mPairingRequest.append(requestId, pairingInfo);
        return true;
    }

    private void onRespondToPairingIndicationResponseFail(Message command, int reason) {
        String methodString = "onRespondToPairingIndicationResponseFail";
        if (mVdbg) {
            Log.v(TAG, methodString + ": command=" + command + " reason=" + reason);
        }

        Bundle data = command.getData();
        PairingInfo pairingInfo = new PairingInfo(command.arg2,
                data.getInt(MESSAGE_BUNDLE_KEY_SESSION_ID),
                data.getInt(MESSAGE_BUNDLE_KEY_PEER_ID),
                data.getString(MESSAGE_BUNDLE_KEY_PAIRING_ALIAS));

        WifiAwareDiscoverySessionState session = getClientSession(pairingInfo.mClientId,
                pairingInfo.mSessionId, methodString);
        if (session == null) {
            return;
        }
        if (data.getInt(MESSAGE_BUNDLE_KEY_PAIRING_TYPE) == NAN_PAIRING_REQUEST_TYPE_SETUP) {
            session.onPairingConfirmReceived(pairingInfo.mPeerId, false, pairingInfo.mAlias,
                    NAN_PAIRING_REQUEST_TYPE_SETUP);
        }
    }

    private boolean onInitiateDataPathResponseSuccessLocal(Message command, int ndpId) {
        return mDataPathMgr
                .onDataPathInitiateSuccess((WifiAwareNetworkSpecifier) command.obj, ndpId);
    }

    private boolean onInitiatePairingResponseSuccessLocal(Message command, int paireId) {
        String methodString = "onInitiatePairingResponseSuccessLocal";
        if (mVdbg) {
            Log.v(TAG, methodString + ": command=" + command + ", ndpId=" + paireId);
        }

        Bundle data = command.getData();
        PairingInfo pairingInfo = new PairingInfo(command.arg2,
                data.getInt(MESSAGE_BUNDLE_KEY_SESSION_ID),
                data.getInt(MESSAGE_BUNDLE_KEY_PEER_ID),
                data.getString(MESSAGE_BUNDLE_KEY_PAIRING_ALIAS));

        WifiAwareDiscoverySessionState session = getClientSession(pairingInfo.mClientId,
                pairingInfo.mSessionId, methodString);
        if (session == null) {
            return false;
        }

        mPairingRequest.append(paireId, pairingInfo);
        return true;
    }

    private void onInitiatePairingResponseFailLocal(Message command, int reason) {
        String methodString = "onInitiatePairingResponseFailLocal";
        if (mVdbg) {
            Log.v(TAG, methodString + ": command=" + command + ", reason=" + reason);
        }

        Bundle data = command.getData();
        PairingInfo pairingInfo = new PairingInfo(command.arg2,
                data.getInt(MESSAGE_BUNDLE_KEY_SESSION_ID),
                data.getInt(MESSAGE_BUNDLE_KEY_PEER_ID),
                data.getString(MESSAGE_BUNDLE_KEY_PAIRING_ALIAS));

        WifiAwareDiscoverySessionState session = getClientSession(pairingInfo.mClientId,
                pairingInfo.mSessionId, methodString);
        if (session == null) {
            return;
        }
        if (data.getInt(MESSAGE_BUNDLE_KEY_PAIRING_TYPE) == NAN_PAIRING_REQUEST_TYPE_SETUP) {
            session.onPairingConfirmReceived(pairingInfo.mPeerId, false, pairingInfo.mAlias,
                    NAN_PAIRING_REQUEST_TYPE_SETUP);
        }
    }

    private boolean onInitiateBootstrappingResponseSuccessLocal(Message command, int id) {
        String methodString = "onInitiateBootstrappingResponseSuccessLocal";
        if (mVdbg) {
            Log.v(TAG, methodString + ": command=" + command + ", ndpId=" + id);
        }

        Bundle data = command.getData();
        BootStrppingInfo info = new BootStrppingInfo(command.arg2,
                data.getInt(MESSAGE_BUNDLE_KEY_SESSION_ID),
                data.getInt(MESSAGE_BUNDLE_KEY_PEER_ID),
                data.getInt(MESSAGE_BUNDLE_KEY_BOOTSTRAPPING_METHOD),
                data.getBoolean(MESSAGE_BUNDLE_KEY_BOOTSTRAPPING_IS_COME_BACK_REQUEST));
        WifiAwareDiscoverySessionState session = getClientSession(info.mClientId, info.mSessionId,
                methodString);
        if (session == null) {
            return false;
        }

        mBootstrappingRequest.append(id, info);
        return true;
    }

    private void onInitiateBootStrappingResponseFailLocal(Message command, int reason) {
        String methodString = "onInitiateBootStrappingResponseFailLocal";
        if (mVdbg) {
            Log.v(TAG, methodString + ": command=" + command + ", reason=" + reason);
        }

        Bundle data = command.getData();
        BootStrppingInfo info = new BootStrppingInfo(command.arg2,
                data.getInt(MESSAGE_BUNDLE_KEY_SESSION_ID),
                data.getInt(MESSAGE_BUNDLE_KEY_PEER_ID),
                data.getInt(MESSAGE_BUNDLE_KEY_BOOTSTRAPPING_METHOD),
                data.getBoolean(MESSAGE_BUNDLE_KEY_BOOTSTRAPPING_IS_COME_BACK_REQUEST));

        WifiAwareDiscoverySessionState session = getClientSession(info.mClientId, info.mSessionId,
                methodString);
        if (session == null) {
            return;
        }
        session.onBootStrappingConfirmReceived(info.mPeerId, false, info.mMethod);
    }

    private void onRespondToBootStrappingRequestSuccessLocal(Message command) {
        String methodString = "onRespondToBootStrappingRequestSuccessLocal";
        if (mVdbg) {
            Log.v(TAG, methodString + ": command=" + command);
        }

        Bundle data = command.getData();
        BootStrppingInfo info = new BootStrppingInfo(command.arg2,
                data.getInt(MESSAGE_BUNDLE_KEY_SESSION_ID),
                data.getInt(MESSAGE_BUNDLE_KEY_PEER_ID),
                data.getInt(MESSAGE_BUNDLE_KEY_BOOTSTRAPPING_METHOD),
                data.getBoolean(MESSAGE_BUNDLE_KEY_BOOTSTRAPPING_IS_COME_BACK_REQUEST));

        WifiAwareDiscoverySessionState session = getClientSession(info.mClientId, info.mSessionId,
                methodString);
        if (session == null) {
            return;
        }
        if (data.getBoolean(MESSAGE_BUNDLE_KEY_BOOTSTRAPPING_ACCEPT)) {
            session.onBootStrappingConfirmReceived(info.mPeerId, true, info.mMethod);
        }
    }

    private void onSuspendResponseLocal(Message command, boolean success, int reason) {
        String methodString = "onSuspendResponseLocal";
        if (mVdbg) {
            Log.v(TAG, methodString + ": command=" + command + ", success=" + success
                    + ", reason=" + reason);
        }

        int clientId = command.arg2;
        int sessionId = command.getData().getInt(MESSAGE_BUNDLE_KEY_SESSION_ID);

        WifiAwareDiscoverySessionState session = getClientSession(clientId, sessionId,
                methodString);
        if (session == null) {
            return;
        }
        if (success) {
            session.onSuspendSuccess();
        } else {
            session.onSuspendFail(convertFrameworkStatusToSuspensionFailedReason(reason));
        }
    }

    @WifiAwareManager.SessionSuspensionFailedReasonCode
    private int convertFrameworkStatusToSuspensionFailedReason(int reason) {
        switch (reason) {
            case REDUNDANT_REQUEST:
                return WIFI_AWARE_SUSPEND_REDUNDANT_REQUEST;
            case NOT_SUPPORTED:
                return WIFI_AWARE_SUSPEND_INVALID_SESSION;
            case NO_CONNECTION:
                return WIFI_AWARE_SUSPEND_CANNOT_SUSPEND;
            default:
                return WIFI_AWARE_SUSPEND_INTERNAL_ERROR;
        }
    }

    private void onResumeResponseLocal(Message command, boolean success,
            @WifiAwareManager.SessionResumptionFailedReasonCode int reason) {
        String methodString = "onResumeResponseLocal";
        if (mVdbg) {
            Log.v(TAG, methodString + ": command="
                    + command + ", success=" + success + ", reason=" + reason);
        }

        int clientId = command.arg2;
        int sessionId = command.getData().getInt(MESSAGE_BUNDLE_KEY_SESSION_ID);

        WifiAwareDiscoverySessionState session = getClientSession(clientId, sessionId,
                methodString);
        if (session == null) {
            return;
        }
        if (!success) {
            session.onResumeFail(convertFrameworkStatusToResumptionFailedReasonCode(reason));
        }
    }

    @WifiAwareManager.SessionResumptionFailedReasonCode
    private int convertFrameworkStatusToResumptionFailedReasonCode(int reason) {
        switch (reason) {
            case REDUNDANT_REQUEST:
                return WIFI_AWARE_RESUME_REDUNDANT_REQUEST;
            case NOT_SUPPORTED:
                return WIFI_AWARE_RESUME_INVALID_SESSION;
            default:
                return WIFI_AWARE_RESUME_INTERNAL_ERROR;
        }
    }



    private void onInitiateDataPathResponseFailLocal(Message command, int reason) {
        if (mVdbg) {
            Log.v(TAG, "onInitiateDataPathResponseFailLocal: command=" + command + ", reason="
                    + reason);
        }
        mDataPathMgr.onDataPathInitiateFail((WifiAwareNetworkSpecifier) command.obj, reason);
    }

    private boolean onRespondToDataPathSetupRequestResponseLocal(Message command, boolean success,
            int reasonOnFailure) {
        if (mVdbg) {
            Log.v(TAG, "onRespondToDataPathSetupRequestResponseLocal: command=" + command
                    + ", success=" + success + ", reasonOnFailure=" + reasonOnFailure);
        }

        return mDataPathMgr.onRespondToDataPathRequest(command.arg2, success, reasonOnFailure);
    }

    private void onEndPathEndResponseLocal(Message command, boolean success, int reasonOnFailure) {
        if (mVdbg) {
            Log.v(TAG, "onEndPathEndResponseLocal: command=" + command
                    + ", success=" + success + ", reasonOnFailure=" + reasonOnFailure);
        }
        // TODO: do something with this
    }

    private void onPairingEndResponseLocal(Message command, boolean success, int reasonOnFailure) {
        if (mVdbg) {
            Log.v(TAG, "onPairingEndResponseLocal: command=" + command
                    + ", success=" + success + ", reasonOnFailure=" + reasonOnFailure);
        }
    }

    private boolean suspendSessionLocal(short transactionId, int clientId, int sessionId) {
        String methodString = "suspendSessionLocal";
        if (mVdbg) {
            Log.v(TAG, methodString + "(): transactionId=" + transactionId + ", clientId="
                    + clientId + ", sessionId=" + sessionId);
        }

        WifiAwareDiscoverySessionState session = getClientSession(clientId, sessionId,
                methodString);
        if (session == null) {
            return false;
        }
        if (!session.isSuspendable()) {
            session.onSuspendFail(WIFI_AWARE_SUSPEND_INVALID_SESSION);
            return false;
        }
        if (session.isSessionSuspended()) {
            session.onSuspendFail(WIFI_AWARE_SUSPEND_REDUNDANT_REQUEST);
            return false;
        }

        return session.suspend(transactionId);
    }

    private boolean resumeSessionLocal(short transactionId, int clientId, int sessionId) {
        if (mVdbg) {
            Log.v(TAG, "resumeSessionLocal(): transactionId=" + transactionId + ", clientId="
                    + clientId + ", sessionId=" + sessionId);
        }

        String methodString = "resumeSessionLocal";
        WifiAwareDiscoverySessionState session = getClientSession(clientId, sessionId,
                methodString);
        if (session == null) {
            return false;
        }
        if (!session.isSuspendable()) {
            session.onResumeFail(WIFI_AWARE_RESUME_INVALID_SESSION);
            return false;
        }
        if (!session.isSessionSuspended()) {
            session.onResumeFail(WIFI_AWARE_RESUME_REDUNDANT_REQUEST);
            return false;
        }

        return session.resume(transactionId);
    }

    /*
     * NOTIFICATIONS
     */

    private void onInterfaceAddressChangeLocal(byte[] mac) {
        Log.d(TAG, "onInterfaceAddressChange: mac=" + String.valueOf(HexEncoding.encode(mac)));

        mCurrentDiscoveryInterfaceMac = mac;

        for (int i = 0; i < mClients.size(); ++i) {
            WifiAwareClientState client = mClients.valueAt(i);
            client.onInterfaceAddressChange(mac);
        }

        mAwareMetrics.recordEnableAware();
    }

    private void onClusterChangeLocal(int clusterEventType, byte[] clusterId) {
        mClusterId = clusterId;
        mClusterEventType = clusterEventType;

        if (mVdbg) {
            Log.v(TAG, "onClusterChange: clusterEventType=" + clusterEventType + ", clusterId="
                    + String.valueOf(HexEncoding.encode(clusterId)));
        }

        for (int i = 0; i < mClients.size(); ++i) {
            WifiAwareClientState client = mClients.valueAt(i);
            client.onClusterChange(clusterEventType, clusterId, mCurrentDiscoveryInterfaceMac);
        }

        mAwareMetrics.recordEnableAware();
    }

    private void onMatchLocal(int pubSubId, int requestorinstanceid, byte[] peerMac,
            byte[] serviceSpecificInfo, byte[] matchFilter, int rangingIndication, int rangeMm,
            int cipherSuite, byte[] scid, byte[] nonce, byte[] tag,
            AwarePairingConfig pairingConfig, @NonNull List<OuiKeyedData> vendorData) {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "onMatch: pubSubId=" + pubSubId
                    + ", requestorInstanceId=" + requestorinstanceid
                    + ", peerDiscoveryMac=" + String.valueOf(HexEncoding.encode(peerMac))
                    + ", serviceSpecificInfo=" + Arrays.toString(serviceSpecificInfo)
                    + ", matchFilter=" + Arrays.toString(matchFilter)
                    + ", rangingIndication=" + rangingIndication + ", rangeMm=" + rangeMm);
        }

        Pair<WifiAwareClientState, WifiAwareDiscoverySessionState> data =
                getClientSessionForPubSubId(pubSubId);
        if (data == null) {
            Log.e(TAG, "onMatch: no session found for pubSubId=" + pubSubId);
            return;
        }

        if (data.second.isRangingEnabled()) {
            mAwareMetrics.recordMatchIndicationForRangeEnabledSubscribe(rangingIndication != 0);
        }
        String pairingAlias = mPairingConfigManager.getPairedDeviceAlias(
                data.first.getCallingPackage(), nonce, tag, peerMac);
        int peerId = data.second.onMatch(requestorinstanceid, peerMac, serviceSpecificInfo,
                matchFilter, rangingIndication, rangeMm, cipherSuite, scid, pairingAlias,
                pairingConfig, vendorData);
        if (TextUtils.isEmpty(pairingAlias)) {
            return;
        }
        PairingSecurityAssociationInfo securityInfo = mPairingConfigManager
                .getSecurityInfoPairedDevice(pairingAlias);
        if (securityInfo == null) {
            return;
        }
        initiateNanPairingVerificationRequest(data.first.getClientId(), data.second.getSessionId(),
                peerId, pairingAlias, securityInfo.mNpk, securityInfo.mAkm,
                securityInfo.mCipherSuite);
    }

    private void onMatchExpiredLocal(int pubSubId, int requestorInstanceId) {
        if (mVdbg) {
            Log.v(TAG,
                    "onMatchExpiredNotification: pubSubId=" + pubSubId
                            + ", requestorInstanceId=" + requestorInstanceId);
        }

        Pair<WifiAwareClientState, WifiAwareDiscoverySessionState> data =
                getClientSessionForPubSubId(pubSubId);
        if (data == null) {
            Log.e(TAG, "onMatch: no session found for pubSubId=" + pubSubId);
            return;
        }
        data.second.onMatchExpired(requestorInstanceId);
    }

    private void onSessionTerminatedLocal(int pubSubId, boolean isPublish, int reason) {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "onSessionTerminatedLocal: pubSubId=" + pubSubId + ", isPublish=" + isPublish
                    + ", reason=" + reason);
        }

        Pair<WifiAwareClientState, WifiAwareDiscoverySessionState> data =
                getClientSessionForPubSubId(pubSubId);
        if (data == null) {
            Log.e(TAG, "onSessionTerminatedLocal: no session found for pubSubId=" + pubSubId);
            return;
        }

        try {
            data.second.getCallback().onSessionTerminated(reason);
        } catch (RemoteException e) {
            Log.w(TAG,
                    "onSessionTerminatedLocal onSessionTerminated(): RemoteException (FYI): " + e);
        }
        data.first.removeSession(data.second.getSessionId());
        // If Ranging enabled or instant mode require changes, reconfigure.
        if (mCurrentRangingEnabled != doesAnyClientNeedRanging()
                || mInstantCommModeClientRequest != getInstantModeFromAllClients()) {
            reconfigure();
        }
        mAwareMetrics.recordDiscoverySessionDuration(data.second.getCreationTime(),
                data.second.isPublishSession(), data.second.getSessionId());
        sendAwareResourcesChangedBroadcast();
    }

    private void onMessageReceivedLocal(int pubSubId, int requestorInstanceId, byte[] peerMac,
            byte[] message) {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "onMessageReceivedLocal: pubSubId=" + pubSubId + ", requestorInstanceId="
                    + requestorInstanceId + ", peerDiscoveryMac="
                    + String.valueOf(HexEncoding.encode(peerMac)));
        }

        Pair<WifiAwareClientState, WifiAwareDiscoverySessionState> data =
                getClientSessionForPubSubId(pubSubId);
        if (data == null) {
            Log.e(TAG, "onMessageReceivedLocal: no session found for pubSubId=" + pubSubId);
            return;
        }

        data.second.onMessageReceived(requestorInstanceId, peerMac, message);
    }

    private void onAwareDownLocal() {
        Log.d(TAG, "onAwareDown: mCurrentAwareConfiguration=" + mCurrentAwareConfiguration);

        if (mCurrentAwareConfiguration == null) {
            return;
        }

        for (int i = 0; i < mClients.size(); ++i) {
            WifiAwareClientState client = mClients.valueAt(i);
            mAwareMetrics.recordAttachSessionDuration(client.getCreationTime());
            SparseArray<WifiAwareDiscoverySessionState> sessions = client.getSessions();
            for (int j = 0; j < sessions.size(); ++j) {
                mAwareMetrics.recordDiscoverySessionDuration(sessions.valueAt(j).getCreationTime(),
                        sessions.valueAt(j).isPublishSession(), sessions.valueAt(j).getSessionId());
            }
            client.destroy();
        }
        mAwareMetrics.recordDisableAware();

        mClients.clear();
        mPairingRequest.clear();
        mCurrentAwareConfiguration = null;
        mSm.onAwareDownCleanupSendQueueState();
        mDataPathMgr.onAwareDownCleanupDataPaths();
        mCurrentDiscoveryInterfaceMac = ALL_ZERO_MAC;
        mDataPathMgr.deleteAllInterfaces();
        sendAwareResourcesChangedBroadcast();
    }

    private void onPairingRequestReceivedLocal(int discoverySessionId, int peerId,
            byte[] peerDiscMacAddr, int pairingId, int requestType, byte[] nonce, byte[] tag) {
        Pair<WifiAwareClientState, WifiAwareDiscoverySessionState> data =
                getClientSessionForPubSubId(discoverySessionId);
        if (data == null) {
            Log.e(TAG, "onPairingRequestReceivedLocal: no session found for pubSubId="
                    + discoverySessionId);
            return;
        }
        if (requestType == NAN_PAIRING_REQUEST_TYPE_SETUP) {
            data.second.onPairingRequestReceived(peerId, peerDiscMacAddr, pairingId);
            return;
        }
        // Response with the cache NPKSA
        String alias = mPairingConfigManager.getPairedDeviceAlias(
                data.first.getCallingPackage(), nonce, tag, peerDiscMacAddr);
        PairingSecurityAssociationInfo securityInfo =  null;
        if (alias != null) {
            securityInfo = mPairingConfigManager.getSecurityInfoPairedDevice(alias);
        }
        if (securityInfo != null) {
            responseNanPairingVerificationRequest(data.first.getClientId(),
                    data.second.getSessionId(),
                    data.second.getPeerIdOrAddIfNew(peerId, peerDiscMacAddr), pairingId, alias,
                    true, securityInfo.mNpk, securityInfo.mAkm, securityInfo.mCipherSuite);
        } else {
            // If local cache is not found, reject the verification request.
            responseNanPairingVerificationRequest(data.first.getClientId(), discoverySessionId,
                    data.second.getPeerIdOrAddIfNew(peerId, peerDiscMacAddr), pairingId, alias,
                    false, null, 0, WIFI_AWARE_CIPHER_SUITE_NCS_PK_PASN_128);
        }
    }

    private boolean onPairingConfirmReceivedLocal(int pairingId, boolean accept, int reason,
            int requestType, boolean enableCache, PairingSecurityAssociationInfo npksa) {
        PairingInfo info = mPairingRequest.get(pairingId);
        mPairingRequest.remove(pairingId);
        if (info == null) {
            return false;
        }
        WifiAwareClientState client = mClients.get(info.mClientId);
        if (client == null) {
            Log.e(TAG,
                    "onPairingConfirmReceivedLocal: no client exists for clientId="
                            + info.mClientId);
            return false;
        }

        WifiAwareDiscoverySessionState session = client.getSession(info.mSessionId);
        if (session == null) {
            Log.e(TAG, "onPairingConfirmReceivedLocal: no session exists for clientId="
                    + info.mClientId
                    + ", sessionId=" + info.mSessionId);
            return false;
        }
        session.onPairingConfirmReceived(info.mPeerId, accept, info.mAlias, requestType);
        if (accept) {
            if (enableCache && requestType == NAN_PAIRING_REQUEST_TYPE_SETUP) {
                mPairingConfigManager.addPairedDeviceSecurityAssociation(
                        client.getCallingPackage(), info.mAlias, npksa);
            }
            return true;
        }
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "Pairing request reject, reason=" + reason);
        }
        return true;
    }

    private void onBootstrappingRequestReceivedLocal(int discoverySessionId, int peerId,
            byte[] peerDiscMacAddr, int bootstrappingId, int method) {
        Pair<WifiAwareClientState, WifiAwareDiscoverySessionState> data =
                getClientSessionForPubSubId(discoverySessionId);
        if (data == null) {
            Log.e(TAG, "onBootstrappingRequestReceivedLocal: no session found for pubSubId="
                    + discoverySessionId);
            return;
        }
        if (data.second.acceptsBootstrappingMethod(method)) {
            respondToBootstrappingRequest(data.first.getClientId(), data.second.getSessionId(),
                    data.second.getPeerIdOrAddIfNew(peerId, peerDiscMacAddr), bootstrappingId,
                    true, method);
        } else {
            respondToBootstrappingRequest(data.first.getClientId(), data.second.getSessionId(),
                    data.second.getPeerIdOrAddIfNew(peerId, peerDiscMacAddr), bootstrappingId,
                    false, method);
        }
    }

    private boolean onBootStrappingConfirmReceivedLocal(int id, int reason, int responseCode,
            int comeBackDelay, byte[] cookie) {
        BootStrppingInfo info = mBootstrappingRequest.get(id);
        mBootstrappingRequest.remove(id);
        if (info == null) {
            return false;
        }
        if (responseCode == NAN_BOOTSTRAPPING_COMEBACK && comeBackDelay > 0) {
            if (!info.mIsComeBackFollowUp) {
                initiateBootStrappingSetupRequest(info.mClientId, info.mSessionId, info.mPeerId,
                        info.mMethod, comeBackDelay * 1000L, cookie);
                return true;
            }
            Log.e(TAG, "onBootStrappingConfirmReceivedLocal come back event on a"
                    + "comback followup request, handle it as reject!");
        }
        String methodString = "onBootStrappingConfirmReceivedLocal";
        WifiAwareDiscoverySessionState session = getClientSession(info.mClientId, info.mSessionId,
                methodString);
        if (session == null) {
            return false;
        }
        boolean accept = responseCode == NAN_BOOTSTRAPPING_ACCEPT;
        session.onBootStrappingConfirmReceived(info.mPeerId, accept, info.mMethod);

        if (!accept && mVerboseLoggingEnabled) {
            Log.v(TAG, "bootstrapping request reject, reason=" + reason);
        }
        return true;
    }

    private void onSuspensionModeChangedLocal(boolean isSuspended) {
        if (isSuspended) return;

        // Trigger resume success callback for all suspended sessions when device exits
        // suspended mode.
        for (int i = 0; i < mClients.size(); i++) {
            WifiAwareClientState clientState = mClients.valueAt(i);
            SparseArray<WifiAwareDiscoverySessionState> sessions = clientState.getSessions();
            for (int j = 0; j < sessions.size(); j++) {
                WifiAwareDiscoverySessionState session = sessions.valueAt(j);
                if (session != null && session.isSessionSuspended()) {
                    session.onResumeSuccess();
                }
            }
        }
    }

    /*
     * Utilities
     */

    private Pair<WifiAwareClientState, WifiAwareDiscoverySessionState> getClientSessionForPubSubId(
            int pubSubId) {
        for (int i = 0; i < mClients.size(); ++i) {
            WifiAwareClientState client = mClients.valueAt(i);
            WifiAwareDiscoverySessionState session = client.getAwareSessionStateForPubSubId(
                    pubSubId);
            if (session != null) {
                return new Pair<>(client, session);
            }
        }

        return null;
    }

    @Nullable
    private WifiAwareDiscoverySessionState getClientSession(int clientId, int sessionId,
            String methodString) {
        WifiAwareClientState client = mClients.get(clientId);
        if (client == null) {
            Log.e(
                    TAG,
                    methodString + ": no client exists for " + "clientId=" + clientId);
            return null;
        }

        WifiAwareDiscoverySessionState session = client.getSession(sessionId);
        if (session == null) {
            Log.e(TAG,
                    methodString + ": no session exists for "
                            + "clientId=" + clientId + ", sessionId=" + sessionId);
            return null;
        }

        return session;
    }

    /**
     * Merge all the existing client configurations with the (optional) input configuration request.
     * If the configurations are "incompatible" (rules in comment below) return a null.
     */
    private ConfigRequest mergeConfigRequests(ConfigRequest configRequest) {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "mergeConfigRequests(): mClients=[" + mClients + "], configRequest="
                    + configRequest);
        }

        if (mClients.size() == 0 && configRequest == null) {
            Log.e(TAG, "mergeConfigRequests: invalid state - called with 0 clients registered!");
            return null;
        }

        // TODO: continue working on merge algorithm:
        // - if any request 5g: enable
        // - maximal master preference
        // - cluster range: must be identical
        // - if any request identity change: enable
        // - discovery window: minimum value if specified, 0 (disable) is considered an infinity
        boolean support5gBand = false;
        boolean support6gBand = false;
        int masterPreference = 0;
        boolean clusterIdValid = false;
        int clusterLow = 0;
        int clusterHigh = ConfigRequest.CLUSTER_ID_MAX;
        int[] discoveryWindowInterval =
                {ConfigRequest.DW_INTERVAL_NOT_INIT, ConfigRequest.DW_INTERVAL_NOT_INIT};
        List<OuiKeyedData> vendorData = null;
        if (configRequest != null) {
            support5gBand = configRequest.mSupport5gBand;
            support6gBand = configRequest.mSupport6gBand;
            masterPreference = configRequest.mMasterPreference;
            clusterIdValid = true;
            clusterLow = configRequest.mClusterLow;
            clusterHigh = configRequest.mClusterHigh;
            discoveryWindowInterval = configRequest.mDiscoveryWindowInterval;
        }
        for (int i = 0; i < mClients.size(); ++i) {
            ConfigRequest cr = mClients.valueAt(i).getConfigRequest();

            // any request turns on 5G
            if (cr.mSupport5gBand) {
                support5gBand = true;
            }

            // any request turns on 5G
            if (cr.mSupport6gBand) {
                support6gBand = true;
            }

            // maximal master preference
            masterPreference = Math.max(masterPreference, cr.mMasterPreference);

            // cluster range must be the same across all config requests
            if (!clusterIdValid) {
                clusterIdValid = true;
                clusterLow = cr.mClusterLow;
                clusterHigh = cr.mClusterHigh;
            } else {
                if (clusterLow != cr.mClusterLow) return null;
                if (clusterHigh != cr.mClusterHigh) return null;
            }

            for (int band = ConfigRequest.NAN_BAND_24GHZ; band <= ConfigRequest.NAN_BAND_5GHZ;
                    ++band) {
                if (discoveryWindowInterval[band] == ConfigRequest.DW_INTERVAL_NOT_INIT) {
                    discoveryWindowInterval[band] = cr.mDiscoveryWindowInterval[band];
                } else if (cr.mDiscoveryWindowInterval[band]
                        == ConfigRequest.DW_INTERVAL_NOT_INIT) {
                    // do nothing: keep my values
                } else if (discoveryWindowInterval[band] == ConfigRequest.DW_DISABLE) {
                    discoveryWindowInterval[band] = cr.mDiscoveryWindowInterval[band];
                } else if (cr.mDiscoveryWindowInterval[band] == ConfigRequest.DW_DISABLE) {
                    // do nothing: keep my values
                } else {
                    discoveryWindowInterval[band] = Math.min(discoveryWindowInterval[band],
                            cr.mDiscoveryWindowInterval[band]);
                }
            }

            if (SdkLevel.isAtLeastV() && !cr.getVendorData().isEmpty()) {
                vendorData = cr.getVendorData();
            }
        }
        ConfigRequest.Builder builder = new ConfigRequest.Builder().setSupport5gBand(support5gBand)
                .setMasterPreference(masterPreference).setClusterLow(clusterLow)
                .setClusterHigh(clusterHigh);
        for (int band = ConfigRequest.NAN_BAND_24GHZ; band <= ConfigRequest.NAN_BAND_5GHZ; ++band) {
            if (discoveryWindowInterval[band] != ConfigRequest.DW_INTERVAL_NOT_INIT) {
                builder.setDiscoveryWindowInterval(band, discoveryWindowInterval[band]);
            }
        }
        if (SdkLevel.isAtLeastV()) {
            // Always use the vendor data from the incoming ConfigRequest if provided.
            // Otherwise, use the most recent vendor data in the mClients list.
            if (configRequest != null && !configRequest.getVendorData().isEmpty()) {
                vendorData = configRequest.getVendorData();
            }
            if (vendorData != null) {
                builder.setVendorData(vendorData);
            }
        }
        return builder.build();
    }

    private WorkSource createMergedRequestorWs() {
        if (mVerboseLoggingEnabled) {
            Log.v(TAG, "createMergedRequestorWs(): mClients=[" + mClients + "]");
        }
        WorkSource requestorWs = new WorkSource();
        boolean isOpportunistic = false;
        for (int i = 0; i < mClients.size(); ++i) {
            WifiAwareClientState clientState = mClients.valueAt(i);
            if (clientState.isAwareOffload()
                    || mOpportunisticSet.contains(clientState.getCallingPackage())) {
                isOpportunistic = true;
            } else {
                requestorWs.add(
                        new WorkSource(clientState.getUid(), clientState.getCallingPackage()));
            }
        }
        if (requestorWs.size() == 0 && isOpportunistic) {
            // All clients are opportunistic, use Wifi UID
            return new WorkSource(Process.WIFI_UID);
        }
        return requestorWs;
    }

    private boolean doesAnyClientNeedIdentityChangeNotifications() {
        for (int i = 0; i < mClients.size(); ++i) {
            if (mClients.valueAt(i).getNotifyIdentityChange()) {
                return true;
            }
        }
        return false;
    }

    private boolean doesAnyClientNeedRanging() {
        for (int i = 0; i < mClients.size(); ++i) {
            if (mClients.valueAt(i).isRangingEnabled()) {
                return true;
            }
        }
        return false;
    }

    private int getInstantModeFromAllClients() {
        if (mOverrideInstantMode != INSTANT_MODE_DISABLED) {
            return mOverrideInstantMode;
        }
        int instantMode = INSTANT_MODE_DISABLED;
        for (int i = 0; i < mClients.size(); ++i) {
            int currentClient = mClients.valueAt(i).getInstantMode((long) mContext.getResources()
                    .getInteger(R.integer.config_wifiAwareInstantCommunicationModeDurationMillis));
            if (currentClient == INSTANT_MODE_5GHZ) {
                return currentClient;
            }
            if (currentClient == INSTANT_MODE_24GHZ) {
                instantMode = currentClient;
            }
        }
        return instantMode;
    }

    private static String messageToString(Message msg) {
        StringBuilder sb = new StringBuilder();

        String s = sSmToString.get(msg.what);
        if (s == null) {
            s = "<unknown>";
        }
        sb.append(s).append("/");

        if (msg.what == MESSAGE_TYPE_NOTIFICATION || msg.what == MESSAGE_TYPE_COMMAND
                || msg.what == MESSAGE_TYPE_RESPONSE) {
            s = sSmToString.get(msg.arg1);
            if (s == null) {
                s = "<unknown>";
            }
            sb.append(s);
        }

        if (msg.what == MESSAGE_TYPE_RESPONSE || msg.what == MESSAGE_TYPE_RESPONSE_TIMEOUT) {
            sb.append(" (Transaction ID=").append(msg.arg2).append(")");
        }

        return sb.toString();
    }

    /**
     * Just a proxy to call {@link WifiAwareDataPathStateManager#createAllInterfaces()} for test.
     */
    @VisibleForTesting
    public void createAllDataPathInterfaces() {
        mDataPathMgr.createAllInterfaces();
    }

    /**
     * Dump the internal state of the class.
     */
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        pw.println("AwareStateManager:");
        pw.println("  mClients: [" + mClients + "]");
        pw.println("  mUsageEnabled: " + mUsageEnabled);
        pw.println("  mCapabilities: [" + mCapabilities + "]");
        pw.println("  mCurrentAwareConfiguration: " + mCurrentAwareConfiguration);
        pw.println("  mCurrentIdentityNotification: " + mCurrentIdentityNotification);
        pw.println("  mOpportunisticSet: " + mOpportunisticSet);
        for (int i = 0; i < mClients.size(); ++i) {
            mClients.valueAt(i).dump(fd, pw, args);
        }
        pw.println("  mSettableParameters: " + mSettableParameters);
        mSm.dump(fd, pw, args);
        mDataPathMgr.dump(fd, pw, args);
        mWifiAwareNativeApi.dump(fd, pw, args);
        pw.println("mAwareMetrics:");
        mAwareMetrics.dump(fd, pw, args);
    }

    private void handleLocationModeDisabled() {
        for (int i = 0; i < mClients.size(); i++) {
            WifiAwareClientState clientState = mClients.valueAt(i);
            if (SdkLevel.isAtLeastT()) {
                try {
                    // As location mode is disabled, only app disavowal the location can pass the
                    // check.
                    mWifiPermissionsUtil.enforceNearbyDevicesPermission(
                            (AttributionSource) clientState.getAttributionSource(), true,
                            "Wifi Aware location mode change.");
                } catch (SecurityException e) {
                    disconnect(clientState.getClientId());
                }
            } else {
                disconnect(clientState.getClientId());
            }
        }
    }

    private int getAwareInstantCommunicationChannel(int instantMode) {
        if (instantMode != INSTANT_MODE_5GHZ) {
            return AWARE_BAND_2_INSTANT_COMMUNICATION_CHANNEL_FREQ;
        }
        if (mAwareBand5InstantCommunicationChannelFreq == 0) {
            // If 5G instant communication doesn't have a valid channel, fallback to 2G.
            return AWARE_BAND_2_INSTANT_COMMUNICATION_CHANNEL_FREQ;
        }
        if (mAwareBand5InstantCommunicationChannelFreq > 0) {
            return mAwareBand5InstantCommunicationChannelFreq;
        }
        List<WifiAvailableChannel> channels = mWifiInjector.getWifiThreadRunner().call(
                () -> mWifiInjector.getWifiNative().getUsableChannels(WifiScanner.WIFI_BAND_5_GHZ,
                        OP_MODE_WIFI_AWARE, WifiAvailableChannel.FILTER_NAN_INSTANT_MODE), null);
        if (channels == null || channels.isEmpty()) {
            if (mVerboseLoggingEnabled) {
                Log.v(TAG, "No available instant communication mode channel");
            }
            mAwareBand5InstantCommunicationChannelFreq = 0;
        } else {
            if (channels.size() > 1) {
                if (mVerboseLoggingEnabled) {
                    Log.v(TAG, "should have only one 5G instant communication channel,"
                            + "but size=" + channels.size());
                }
            }
            // TODO(b/232138258): When the filter issue fixed, just check if only return channel is
            //  correct
            for (WifiAvailableChannel channel : channels) {
                int freq = channel.getFrequencyMhz();
                if (freq == AWARE_BAND_5_INSTANT_COMMUNICATION_CHANNEL_FREQ_CHANNEL_149) {
                    mAwareBand5InstantCommunicationChannelFreq =
                            AWARE_BAND_5_INSTANT_COMMUNICATION_CHANNEL_FREQ_CHANNEL_149;
                    break;
                } else if (freq == AWARE_BAND_5_INSTANT_COMMUNICATION_CHANNEL_FREQ_CHANNEL_44) {
                    mAwareBand5InstantCommunicationChannelFreq =
                            AWARE_BAND_5_INSTANT_COMMUNICATION_CHANNEL_FREQ_CHANNEL_44;
                }
            }
            if (mAwareBand5InstantCommunicationChannelFreq == -1) {
                Log.e(TAG, "Both channel 149 and 44 are not available when the 5G WI-FI is "
                        + "supported");
                mAwareBand5InstantCommunicationChannelFreq = 0;
            }
        }
        return mAwareBand5InstantCommunicationChannelFreq == 0
                ? AWARE_BAND_2_INSTANT_COMMUNICATION_CHANNEL_FREQ
                : mAwareBand5InstantCommunicationChannelFreq;
    }

    private boolean isAwareOffloading() {
        for (int i = 0; i < mClients.size(); ++i) {
            WifiAwareClientState clientState = mClients.valueAt(i);
            if (clientState.isAwareOffload()) {
                return true;
            }
        }
        return false;
    }

    private boolean isAnyCallerIgnoringBatteryOptimizations() {
        for (int i = 0; i < mClients.size(); ++i) {
            WifiAwareClientState clientState = mClients.valueAt(i);
            if (mPowerManager.isIgnoringBatteryOptimizations(clientState.getCallingPackage())) {
                return true;
            }
        }
        return false;
    }

    private int convertFrameworkHalCommandToEnum(int cmd) {
        switch (cmd) {
            case COMMAND_TYPE_CONNECT:
                return WIFI_AWARE_HAL_API_CALLED__COMMAND__AWARE_ENABLE_REQUEST;
            case COMMAND_TYPE_RECONFIGURE:
                return WIFI_AWARE_HAL_API_CALLED__COMMAND__AWARE_CONFIG_REQUEST;
            case COMMAND_TYPE_DISABLE:
                return WIFI_AWARE_HAL_API_CALLED__COMMAND__AWARE_DISABLE_REQUEST;
            case COMMAND_TYPE_PUBLISH:
            case COMMAND_TYPE_UPDATE_PUBLISH:
                return WIFI_AWARE_HAL_API_CALLED__COMMAND__AWARE_START_PUBLISH_REQUEST;
            case COMMAND_TYPE_SUBSCRIBE:
            case COMMAND_TYPE_UPDATE_SUBSCRIBE:
                return WIFI_AWARE_HAL_API_CALLED__COMMAND__AWARE_START_SUBSCRIBE_REQUEST;
            case COMMAND_TYPE_TRANSMIT_NEXT_MESSAGE:
                return WIFI_AWARE_HAL_API_CALLED__COMMAND__AWARE_TRANSMIT_FOLLOW_UP_REQUEST;
            case COMMAND_TYPE_INITIATE_PAIRING_REQUEST:
                return WIFI_AWARE_HAL_API_CALLED__COMMAND__AWARE_INITIATE_PAIRING_REQUEST;
            case COMMAND_TYPE_RESPONSE_PAIRING_REQUEST:
                return WIFI_AWARE_HAL_API_CALLED__COMMAND__AWARE_RESPOND_TO_PAIRING_INDICATION_REQUEST;
            case COMMAND_TYPE_INITIATE_BOOTSTRAPPING_REQUEST:
                return WIFI_AWARE_HAL_API_CALLED__COMMAND__AWARE_INITIATE_BOOTSTRAPPING_REQUEST;
            case COMMAND_TYPE_RESPONSE_BOOTSTRAPPING_REQUEST:
                return WIFI_AWARE_HAL_API_CALLED__COMMAND__AWARE_RESPOND_TO_BOOTSTRAPPING_INDICATION_REQUEST;
            case COMMAND_TYPE_GET_CAPABILITIES:
                return WIFI_AWARE_HAL_API_CALLED__COMMAND__AWARE_GET_CAPABILITIES_REQUEST;
            case COMMAND_TYPE_CREATE_DATA_PATH_INTERFACE:
                return WIFI_AWARE_HAL_API_CALLED__COMMAND__AWARE_CREATE_DATA_INTERFACE_REQUEST;
            case COMMAND_TYPE_DELETE_DATA_PATH_INTERFACE:
                return WIFI_AWARE_HAL_API_CALLED__COMMAND__AWARE_DELETE_DATA_INTERFACE_REQUEST;
            case COMMAND_TYPE_INITIATE_DATA_PATH_SETUP:
                return WIFI_AWARE_HAL_API_CALLED__COMMAND__AWARE_INITIATE_DATA_PATH_REQUEST;
            case COMMAND_TYPE_RESPOND_TO_DATA_PATH_SETUP_REQUEST:
                return WIFI_AWARE_HAL_API_CALLED__COMMAND__AWARE_RESPOND_TO_DATA_PATH_INDICATION_REQUEST;
            case COMMAND_TYPE_END_DATA_PATH:
                return WIFI_AWARE_HAL_API_CALLED__COMMAND__AWARE_TERMINATE_DATA_PATH_REQUEST;
            case COMMAND_TYPE_END_PAIRING:
                return WIFI_AWARE_HAL_API_CALLED__COMMAND__AWARE_TERMINATE_PAIRING_REQUEST;
            case COMMAND_TYPE_SUSPEND_SESSION:
                return WIFI_AWARE_HAL_API_CALLED__COMMAND__AWARE_SUSPEND_REQUEST;
            case COMMAND_TYPE_RESUME_SESSION:
                return WIFI_AWARE_HAL_API_CALLED__COMMAND__AWARE_RESUME_REQUEST;
            default:
                return WIFI_AWARE_HAL_API_CALLED__COMMAND__AWARE_API_UNKNOWN;
        }
    }

    private void recordHalApiCall(int command, int state, long starTime) {
        WifiStatsLog.write(
                WIFI_AWARE_HAL_API_CALLED,
                convertFrameworkHalCommandToEnum(command),
                convertNanStatusCodeToWifiStatsLogEnum(state),
                (int) (SystemClock.elapsedRealtime() - starTime));
    }
}
