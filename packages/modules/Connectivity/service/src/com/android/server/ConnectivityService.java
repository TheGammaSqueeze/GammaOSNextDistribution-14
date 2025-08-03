/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.server;

import static android.Manifest.permission.RECEIVE_DATA_ACTIVITY_CHANGE;
import static android.app.ActivityManager.UidFrozenStateChangedCallback.UID_FROZEN_STATE_FROZEN;
import static android.content.pm.PackageManager.FEATURE_BLUETOOTH;
import static android.content.pm.PackageManager.FEATURE_LEANBACK;
import static android.content.pm.PackageManager.FEATURE_WATCH;
import static android.content.pm.PackageManager.FEATURE_WIFI;
import static android.content.pm.PackageManager.FEATURE_WIFI_DIRECT;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.net.ConnectivityDiagnosticsManager.ConnectivityReport.KEY_NETWORK_PROBES_ATTEMPTED_BITMASK;
import static android.net.ConnectivityDiagnosticsManager.ConnectivityReport.KEY_NETWORK_PROBES_SUCCEEDED_BITMASK;
import static android.net.ConnectivityDiagnosticsManager.ConnectivityReport.KEY_NETWORK_VALIDATION_RESULT;
import static android.net.ConnectivityDiagnosticsManager.DataStallReport.DETECTION_METHOD_DNS_EVENTS;
import static android.net.ConnectivityDiagnosticsManager.DataStallReport.DETECTION_METHOD_TCP_METRICS;
import static android.net.ConnectivityDiagnosticsManager.DataStallReport.KEY_DNS_CONSECUTIVE_TIMEOUTS;
import static android.net.ConnectivityDiagnosticsManager.DataStallReport.KEY_TCP_METRICS_COLLECTION_PERIOD_MILLIS;
import static android.net.ConnectivityDiagnosticsManager.DataStallReport.KEY_TCP_PACKET_FAIL_RATE;
import static android.net.ConnectivityManager.ACTION_RESTRICT_BACKGROUND_CHANGED;
import static android.net.ConnectivityManager.BLOCKED_METERED_REASON_MASK;
import static android.net.ConnectivityManager.BLOCKED_REASON_LOCKDOWN_VPN;
import static android.net.ConnectivityManager.BLOCKED_REASON_NONE;
import static android.net.ConnectivityManager.CALLBACK_IP_CHANGED;
import static android.net.ConnectivityManager.CONNECTIVITY_ACTION;
import static android.net.ConnectivityManager.FIREWALL_CHAIN_BACKGROUND;
import static android.net.ConnectivityManager.FIREWALL_RULE_ALLOW;
import static android.net.ConnectivityManager.FIREWALL_RULE_DEFAULT;
import static android.net.ConnectivityManager.FIREWALL_RULE_DENY;
import static android.net.ConnectivityManager.TYPE_BLUETOOTH;
import static android.net.ConnectivityManager.TYPE_ETHERNET;
import static android.net.ConnectivityManager.TYPE_MOBILE;
import static android.net.ConnectivityManager.TYPE_MOBILE_CBS;
import static android.net.ConnectivityManager.TYPE_MOBILE_DUN;
import static android.net.ConnectivityManager.TYPE_MOBILE_EMERGENCY;
import static android.net.ConnectivityManager.TYPE_MOBILE_FOTA;
import static android.net.ConnectivityManager.TYPE_MOBILE_HIPRI;
import static android.net.ConnectivityManager.TYPE_MOBILE_IA;
import static android.net.ConnectivityManager.TYPE_MOBILE_IMS;
import static android.net.ConnectivityManager.TYPE_MOBILE_MMS;
import static android.net.ConnectivityManager.TYPE_MOBILE_SUPL;
import static android.net.ConnectivityManager.TYPE_NONE;
import static android.net.ConnectivityManager.TYPE_PROXY;
import static android.net.ConnectivityManager.TYPE_VPN;
import static android.net.ConnectivityManager.TYPE_WIFI;
import static android.net.ConnectivityManager.TYPE_WIFI_P2P;
import static android.net.ConnectivityManager.getNetworkTypeName;
import static android.net.ConnectivityManager.isNetworkTypeValid;
import static android.net.ConnectivitySettingsManager.PRIVATE_DNS_MODE_OPPORTUNISTIC;
import static android.net.INetworkMonitor.NETWORK_VALIDATION_PROBE_PRIVDNS;
import static android.net.INetworkMonitor.NETWORK_VALIDATION_RESULT_PARTIAL;
import static android.net.INetworkMonitor.NETWORK_VALIDATION_RESULT_SKIPPED;
import static android.net.INetworkMonitor.NETWORK_VALIDATION_RESULT_VALID;
import static android.net.MulticastRoutingConfig.FORWARD_NONE;
import static android.net.NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL;
import static android.net.NetworkCapabilities.NET_CAPABILITY_ENTERPRISE;
import static android.net.NetworkCapabilities.NET_CAPABILITY_FOREGROUND;
import static android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET;
import static android.net.NetworkCapabilities.NET_CAPABILITY_LOCAL_NETWORK;
import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_CONGESTED;
import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED;
import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED;
import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING;
import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED;
import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED;
import static android.net.NetworkCapabilities.NET_CAPABILITY_NOT_VPN;
import static android.net.NetworkCapabilities.NET_CAPABILITY_OEM_PAID;
import static android.net.NetworkCapabilities.NET_CAPABILITY_OEM_PRIVATE;
import static android.net.NetworkCapabilities.NET_CAPABILITY_PARTIAL_CONNECTIVITY;
import static android.net.NetworkCapabilities.NET_CAPABILITY_TEMPORARILY_NOT_METERED;
import static android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED;
import static android.net.NetworkCapabilities.NET_ENTERPRISE_ID_1;
import static android.net.NetworkCapabilities.NET_ENTERPRISE_ID_5;
import static android.net.NetworkCapabilities.REDACT_FOR_ACCESS_FINE_LOCATION;
import static android.net.NetworkCapabilities.REDACT_FOR_LOCAL_MAC_ADDRESS;
import static android.net.NetworkCapabilities.REDACT_FOR_NETWORK_SETTINGS;
import static android.net.NetworkCapabilities.TRANSPORT_CELLULAR;
import static android.net.NetworkCapabilities.TRANSPORT_TEST;
import static android.net.NetworkCapabilities.TRANSPORT_VPN;
import static android.net.NetworkCapabilities.TRANSPORT_WIFI;
import static android.net.NetworkRequest.Type.LISTEN_FOR_BEST;
import static android.net.NetworkScore.POLICY_TRANSPORT_PRIMARY;
import static android.net.OemNetworkPreferences.OEM_NETWORK_PREFERENCE_TEST;
import static android.net.OemNetworkPreferences.OEM_NETWORK_PREFERENCE_TEST_ONLY;
import static android.os.Process.INVALID_UID;
import static android.os.Process.VPN_UID;
import static android.system.OsConstants.ETH_P_ALL;
import static android.system.OsConstants.IPPROTO_TCP;
import static android.system.OsConstants.IPPROTO_UDP;

import static com.android.net.module.util.BpfUtils.BPF_CGROUP_INET4_BIND;
import static com.android.net.module.util.BpfUtils.BPF_CGROUP_INET6_BIND;
import static com.android.net.module.util.BpfUtils.BPF_CGROUP_INET_EGRESS;
import static com.android.net.module.util.BpfUtils.BPF_CGROUP_INET_INGRESS;
import static com.android.net.module.util.BpfUtils.BPF_CGROUP_INET_SOCK_CREATE;
import static com.android.net.module.util.NetworkMonitorUtils.isPrivateDnsValidationRequired;
import static com.android.net.module.util.PermissionUtils.enforceAnyPermissionOf;
import static com.android.net.module.util.PermissionUtils.enforceNetworkStackPermission;
import static com.android.net.module.util.PermissionUtils.enforceNetworkStackPermissionOr;
import static com.android.net.module.util.PermissionUtils.hasAnyPermissionOf;
import static com.android.server.ConnectivityStatsLog.CONNECTIVITY_STATE_SAMPLE;
import static com.android.server.connectivity.ConnectivityFlags.REQUEST_RESTRICTED_WIFI;

import android.Manifest;
import android.annotation.CheckResult;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.app.ActivityManager.UidFrozenStateChangedCallback;
import android.app.AppOpsManager;
import android.app.BroadcastOptions;
import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.app.compat.CompatChanges;
import android.app.usage.NetworkStatsManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.XmlResourceParser;
import android.database.ContentObserver;
import android.net.BpfNetMapsUtils;
import android.net.CaptivePortal;
import android.net.CaptivePortalData;
import android.net.ConnectionInfo;
import android.net.ConnectivityDiagnosticsManager.ConnectivityReport;
import android.net.ConnectivityDiagnosticsManager.DataStallReport;
import android.net.ConnectivityManager;
import android.net.ConnectivityManager.BlockedReason;
import android.net.ConnectivityManager.NetworkCallback;
import android.net.ConnectivityManager.RestrictBackgroundStatus;
import android.net.ConnectivitySettingsManager;
import android.net.DataStallReportParcelable;
import android.net.DnsResolverServiceManager;
import android.net.DscpPolicy;
import android.net.ICaptivePortal;
import android.net.IConnectivityDiagnosticsCallback;
import android.net.IConnectivityManager;
import android.net.IDnsResolver;
import android.net.INetd;
import android.net.INetworkActivityListener;
import android.net.INetworkAgent;
import android.net.INetworkMonitor;
import android.net.INetworkMonitorCallbacks;
import android.net.INetworkOfferCallback;
import android.net.IOnCompleteListener;
import android.net.IQosCallback;
import android.net.ISocketKeepaliveCallback;
import android.net.InetAddresses;
import android.net.IpMemoryStore;
import android.net.IpPrefix;
import android.net.LinkProperties;
import android.net.LocalNetworkConfig;
import android.net.LocalNetworkInfo;
import android.net.MatchAllNetworkSpecifier;
import android.net.MulticastRoutingConfig;
import android.net.NativeNetworkConfig;
import android.net.NativeNetworkType;
import android.net.NattSocketKeepalive;
import android.net.Network;
import android.net.NetworkAgent;
import android.net.NetworkAgentConfig;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkInfo.DetailedState;
import android.net.NetworkMonitorManager;
import android.net.NetworkPolicyManager;
import android.net.NetworkPolicyManager.NetworkPolicyCallback;
import android.net.NetworkProvider;
import android.net.NetworkRequest;
import android.net.NetworkScore;
import android.net.NetworkSpecifier;
import android.net.NetworkStack;
import android.net.NetworkState;
import android.net.NetworkStateSnapshot;
import android.net.NetworkTestResultParcelable;
import android.net.NetworkUtils;
import android.net.NetworkWatchlistManager;
import android.net.OemNetworkPreferences;
import android.net.PrivateDnsConfigParcel;
import android.net.ProfileNetworkPreference;
import android.net.ProxyInfo;
import android.net.QosCallbackException;
import android.net.QosFilter;
import android.net.QosSocketFilter;
import android.net.QosSocketInfo;
import android.net.RouteInfo;
import android.net.SocketKeepalive;
import android.net.TetheringManager;
import android.net.TransportInfo;
import android.net.UidRange;
import android.net.UidRangeParcel;
import android.net.UnderlyingNetworkInfo;
import android.net.Uri;
import android.net.VpnManager;
import android.net.VpnTransportInfo;
import android.net.connectivity.ConnectivityCompatChanges;
import android.net.metrics.IpConnectivityLog;
import android.net.metrics.NetworkEvent;
import android.net.netd.aidl.NativeUidRangeConfig;
import android.net.networkstack.ModuleNetworkStackClient;
import android.net.networkstack.NetworkStackClientBase;
import android.net.networkstack.aidl.NetworkMonitorParameters;
import android.net.resolv.aidl.DnsHealthEventParcel;
import android.net.resolv.aidl.IDnsResolverUnsolicitedEventListener;
import android.net.resolv.aidl.Nat64PrefixEventParcel;
import android.net.resolv.aidl.PrivateDnsValidationEventParcel;
import android.net.shared.PrivateDnsConfig;
import android.net.wifi.WifiInfo;
import android.os.BatteryStatsManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.ParcelFileDescriptor;
import android.os.Parcelable;
import android.os.PersistableBundle;
import android.os.PowerManager;
import android.os.Process;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.stats.connectivity.MeteredState;
import android.stats.connectivity.RequestType;
import android.stats.connectivity.ValidatedState;
import android.sysprop.NetworkProperties;
import android.system.ErrnoException;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.LocalLog;
import android.util.Log;
import android.util.Pair;
import android.util.Range;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.util.SparseLongArray;
import android.util.StatsEvent;

import androidx.annotation.RequiresApi;

import com.android.connectivity.resources.R;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.IndentingPrintWriter;
import com.android.internal.util.MessageUtils;
import com.android.metrics.ConnectionDurationForTransports;
import com.android.metrics.ConnectionDurationPerTransports;
import com.android.metrics.ConnectivitySampleMetricsHelper;
import com.android.metrics.ConnectivityStateSample;
import com.android.metrics.NetworkCountForTransports;
import com.android.metrics.NetworkCountPerTransports;
import com.android.metrics.NetworkDescription;
import com.android.metrics.NetworkList;
import com.android.metrics.NetworkRequestCount;
import com.android.metrics.RequestCountForType;
import com.android.modules.utils.BasicShellCommandHandler;
import com.android.modules.utils.build.SdkLevel;
import com.android.net.module.util.BaseNetdUnsolicitedEventListener;
import com.android.net.module.util.BinderUtils;
import com.android.net.module.util.BitUtils;
import com.android.net.module.util.BpfUtils;
import com.android.net.module.util.CollectionUtils;
import com.android.net.module.util.DeviceConfigUtils;
import com.android.net.module.util.HandlerUtils;
import com.android.net.module.util.InterfaceParams;
import com.android.net.module.util.LinkPropertiesUtils.CompareOrUpdateResult;
import com.android.net.module.util.LinkPropertiesUtils.CompareResult;
import com.android.net.module.util.LocationPermissionChecker;
import com.android.net.module.util.PerUidCounter;
import com.android.net.module.util.PermissionUtils;
import com.android.net.module.util.TcUtils;
import com.android.net.module.util.netlink.InetDiagMessage;
import com.android.networkstack.apishim.BroadcastOptionsShimImpl;
import com.android.networkstack.apishim.ConstantsShim;
import com.android.networkstack.apishim.common.BroadcastOptionsShim;
import com.android.networkstack.apishim.common.UnsupportedApiLevelException;
import com.android.server.connectivity.ApplicationSelfCertifiedNetworkCapabilities;
import com.android.server.connectivity.AutodestructReference;
import com.android.server.connectivity.AutomaticOnOffKeepaliveTracker;
import com.android.server.connectivity.AutomaticOnOffKeepaliveTracker.AutomaticOnOffKeepalive;
import com.android.server.connectivity.CarrierPrivilegeAuthenticator;
import com.android.server.connectivity.ClatCoordinator;
import com.android.server.connectivity.ConnectivityFlags;
import com.android.server.connectivity.ConnectivityResources;
import com.android.server.connectivity.DnsManager;
import com.android.server.connectivity.DnsManager.PrivateDnsValidationUpdate;
import com.android.server.connectivity.DscpPolicyTracker;
import com.android.server.connectivity.FullScore;
import com.android.server.connectivity.InvalidTagException;
import com.android.server.connectivity.KeepaliveResourceUtil;
import com.android.server.connectivity.KeepaliveTracker;
import com.android.server.connectivity.LingerMonitor;
import com.android.server.connectivity.MockableSystemProperties;
import com.android.server.connectivity.MulticastRoutingCoordinatorService;
import com.android.server.connectivity.MultinetworkPolicyTracker;
import com.android.server.connectivity.NetworkAgentInfo;
import com.android.server.connectivity.NetworkDiagnostics;
import com.android.server.connectivity.NetworkNotificationManager;
import com.android.server.connectivity.NetworkNotificationManager.NotificationType;
import com.android.server.connectivity.NetworkOffer;
import com.android.server.connectivity.NetworkPreferenceList;
import com.android.server.connectivity.NetworkRanker;
import com.android.server.connectivity.NetworkRequestStateStatsMetrics;
import com.android.server.connectivity.PermissionMonitor;
import com.android.server.connectivity.ProfileNetworkPreferenceInfo;
import com.android.server.connectivity.ProxyTracker;
import com.android.server.connectivity.QosCallbackTracker;
import com.android.server.connectivity.RoutingCoordinatorService;
import com.android.server.connectivity.SatelliteAccessController;
import com.android.server.connectivity.UidRangeUtils;
import com.android.server.connectivity.VpnNetworkPreferenceInfo;
import com.android.server.connectivity.wear.CompanionDeviceManagerProxyService;

import libcore.io.IoUtils;

import org.xmlpull.v1.XmlPullParserException;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.StringJoiner;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * @hide
 */
public class ConnectivityService extends IConnectivityManager.Stub
        implements PendingIntent.OnFinished {
    private static final String TAG = ConnectivityService.class.getSimpleName();

    private static final String DIAG_ARG = "--diag";
    public static final String SHORT_ARG = "--short";
    private static final String NETWORK_ARG = "networks";
    private static final String REQUEST_ARG = "requests";
    private static final String TRAFFICCONTROLLER_ARG = "trafficcontroller";

    private static final boolean DBG = true;
    private static final boolean DDBG = Log.isLoggable(TAG, Log.DEBUG);
    private static final boolean VDBG = Log.isLoggable(TAG, Log.VERBOSE);

    private static final boolean LOGD_BLOCKED_NETWORKINFO = true;

    /**
     * Default URL to use for {@link #getCaptivePortalServerUrl()}. This should not be changed
     * by OEMs for configuration purposes, as this value is overridden by
     * ConnectivitySettingsManager.CAPTIVE_PORTAL_HTTP_URL.
     * R.string.config_networkCaptivePortalServerUrl should be overridden instead for this purpose
     * (preferably via runtime resource overlays).
     */
    private static final String DEFAULT_CAPTIVE_PORTAL_HTTP_URL =
            "http://connectivitycheck.gstatic.com/generate_204";

    // TODO: create better separation between radio types and network types

    // how long to wait before switching back to a radio's default network
    private static final int RESTORE_DEFAULT_NETWORK_DELAY = 1 * 60 * 1000;
    // system property that can override the above value
    private static final String NETWORK_RESTORE_DELAY_PROP_NAME =
            "android.telephony.apn-restore";

    // How long to wait before putting up a "This network doesn't have an Internet connection,
    // connect anyway?" dialog after the user selects a network that doesn't validate.
    private static final int PROMPT_UNVALIDATED_DELAY_MS = 8 * 1000;

    // How long to wait before considering that a network is bad in the absence of any form
    // of connectivity (valid, partial, captive portal). If none has been detected after this
    // delay, the stack considers this network bad, which may affect how it's handled in ranking
    // according to config_networkAvoidBadWifi.
    // Timeout in case the "actively prefer bad wifi" feature is on
    private static final int ACTIVELY_PREFER_BAD_WIFI_INITIAL_TIMEOUT_MS = 20 * 1000;
    // Timeout in case the "actively prefer bad wifi" feature is off
    private static final int DEFAULT_EVALUATION_TIMEOUT_MS = 8 * 1000;

    // Default to 30s linger time-out, and 5s for nascent network. Modifiable only for testing.
    private static final String LINGER_DELAY_PROPERTY = "persist.netmon.linger";
    private static final int DEFAULT_LINGER_DELAY_MS = 30_000;
    private static final int DEFAULT_NASCENT_DELAY_MS = 5_000;

    // Delimiter used when creating the broadcast delivery group for sending
    // CONNECTIVITY_ACTION broadcast.
    private static final char DELIVERY_GROUP_KEY_DELIMITER = ';';

    // The maximum value for the blocking validation result, in milliseconds.
    public static final int MAX_VALIDATION_IGNORE_AFTER_ROAM_TIME_MS = 10000;

    // The maximum number of network request allowed per uid before an exception is thrown.
    @VisibleForTesting
    static final int MAX_NETWORK_REQUESTS_PER_UID = 100;

    // The maximum number of network request allowed for system UIDs before an exception is thrown.
    @VisibleForTesting
    static final int MAX_NETWORK_REQUESTS_PER_SYSTEM_UID = 250;

    @VisibleForTesting
    protected int mLingerDelayMs;  // Can't be final, or test subclass constructors can't change it.
    @VisibleForTesting
    protected int mNascentDelayMs;
    // True if the cell radio of the device is capable of time-sharing.
    @VisibleForTesting
    protected boolean mCellularRadioTimesharingCapable = true;

    // How long to delay to removal of a pending intent based request.
    // See ConnectivitySettingsManager.CONNECTIVITY_RELEASE_PENDING_INTENT_DELAY_MS
    private final int mReleasePendingIntentDelayMs;

    private final MockableSystemProperties mSystemProperties;

    private final PermissionMonitor mPermissionMonitor;

    @VisibleForTesting
    final RequestInfoPerUidCounter mNetworkRequestCounter;
    @VisibleForTesting
    final RequestInfoPerUidCounter mSystemNetworkRequestCounter;

    private volatile boolean mLockdownEnabled;

    private final boolean mRequestRestrictedWifiEnabled;

    /**
     * Stale copy of uid blocked reasons provided by NPMS. As long as they are accessed only in
     * internal handler thread, they don't need a lock.
     */
    private final SparseIntArray mUidBlockedReasons = new SparseIntArray();

    /** Map of UID to its bit-packed allowed transports. */
    private SparseLongArray mUidAllowedTransports = new SparseLongArray();

    /** Allowed UID ranges provided to Netd, tracked based on the netId to which they belong. */
    @GuardedBy("mNetworkForNetId")
    private Map<Integer, NativeUidRangeConfig> mNetIdToAllowlist =
            new HashMap<Integer, NativeUidRangeConfig>();

    /**
     * UIDs that we wish to be denied access to networks based on certain policies, grouped by the
     * netId to which they belong. Used to prevent affected UIDs from getting online through a VPN
     * or from receiving incoming traffic when their active network is denied to them.
     */
    @GuardedBy("mNetworkForNetId")
    private Map<Integer, List<Integer>> mNetIdToDisallowedUids = new HashMap<>();

    private void setUidsAllowedTransports(@NonNull final int[] uids,
            @NonNull final long[] allowedTransportsPacked) {
        mHandler.post(() -> handleSetUidsAllowedTransports(uids, allowedTransportsPacked));
    }

    private void handleSetUidsAllowedTransports(@NonNull final int[] uids,
            @NonNull final long[] allowedTransportsPacked) {
        for (int i = 0; i < uids.length; i++) {
            final int uid = uids[i];
            final long transportsPacked = allowedTransportsPacked[i];
            mUidAllowedTransports.put(uid, transportsPacked);
        }
        if (DDBG) Log.d(TAG, "setUidsAllowedTransports: Processing " + uids.length + " UIDs...");
        for (final var nai : mNetworkAgentInfos) {
            updateDisallowedUidsForNetwork(nai);
            if (nai.isVPN()) {
                // VPNs manage their own allowed UID ranges, so we handle them via denylist above.
                continue;
            }
            for (final int uid : uids) {
                if (nai.networkCapabilities.appliesToUid(uid)) {
                    updateAllowedUidsForNetwork(nai);
                    break;
                }
            }
        }
        if (DDBG) Log.d(TAG, "setUidsAllowedTransports: Processed " + uids.length + " UIDs.");
    }

    // The last UID denylist that we supplied to BPF.
    // To support access across threads, do not modify after assignment.
    private Set<Integer> mLastDisallowedUidsDenylist = Set.of();

    /**
     * Computes which policies apply to the given network to determine which UIDs should be
     * prevented from accessing that network. Then, for each network with disallowed UIDs, checks
     * whether the network is currently the active network for the UID, and if so, prevents the
     * UID from accessing networks entirely via a denylist. Without this, UIDs are able to use VPNs
     * even if their policy disallows it, because the VPN itself manages its allowed UID ranges.
     * Additionally, this prevents incoming traffic for UIDs whose active network is disallowed.
     *
     * NOTE: UIDs with restricted networking permission are never disallowed.
     */
    private void updateDisallowedUidsForNetwork(@NonNull final NetworkAgentInfo nai) {
        ensureRunningOnConnectivityServiceThread();

        final var netId = nai.network.netId;
        final var ourTag = "updateDisallowedUidsForNetwork(" + netId + "): ";
        if (DDBG) Log.d(TAG, ourTag + "begin");
        final var disallowedUids = getUidsDisallowedByPolicyForNetwork(nai);
        final Set<Integer> prevDenylist;
        final Set<Integer> newDenylist = new ArraySet<Integer>();
        synchronized (mNetworkForNetId) {
            prevDenylist = mLastDisallowedUidsDenylist;
            if (mNetworkForNetId.contains(netId)) {
                // Only store the disallowed UIDs if this network still exists.
                mNetIdToDisallowedUids.put(netId, disallowedUids);
            }
            for (final var entry : mNetIdToDisallowedUids.entrySet()) {
                final var entryNetId = entry.getKey();
                final var entryDisallowedUids = entry.getValue();
                entryDisallowedUids.forEach(uid -> {
                    if (mPermissionMonitor.hasRestrictedNetworksPermission(uid)) {
                        // Skip denylist for UIDs with restricted networks permission.
                        return;
                    }
                    // Disallowing UIDs is only applicable if the network is the UID's active
                    // network. For us, a VPN that is still connecting should still be considered,
                    // because it is *about* to become the active network, and we are reacting
                    // early. If we do not include connecting VPNs, we get no result.
                    // Also, if we don't know the active network, denying is fine for now.
                    final var activeNetwork = getActiveNetworkOrConnectingVpnForUidInternal(
                            uid, true /* ignoreBlocked */);
                    if (activeNetwork == null || entryNetId == activeNetwork.netId) {
                        newDenylist.add(uid);
                    }
                });
            }
            mLastDisallowedUidsDenylist = Collections.unmodifiableSet(newDenylist);
        }
        // NOTE: Setting UID rules individually is exponentially faster than replacing the whole
        // firewall chain for some reason (<1ms vs 10+ms, even with few UIDs for the latter
        // and hundreds for the former).
        final var toAdd = newDenylist.stream().filter(uid -> !prevDenylist.contains(uid))
                .collect(Collectors.toSet());
        if (DDBG) {
            synchronized (mNetworkForNetId) {
                Log.d(TAG, ourTag + "Disallowed UIDs per netId: " + mNetIdToDisallowedUids);
            }
            Log.d(TAG, ourTag + "Prev denylist: " + prevDenylist);
            Log.d(TAG, ourTag + "New denylist: " + newDenylist);
        }
        final var toRemove = prevDenylist.stream().filter(uid -> !newDenylist.contains(uid))
                .collect(Collectors.toSet());
        if (DDBG) Log.d(TAG, ourTag + "toAdd: " + toAdd + "; toRemove: " + toRemove);
        if (toAdd.isEmpty() && toRemove.isEmpty()) {
            // Nothing to do.
            return;
        }
        boolean anyFailed = false;
        for (final int uid : toAdd) {
            try {
                mBpfNetMaps.setUidRule(ConnectivityManager.FIREWALL_CHAIN_OEM_DENY_1, uid,
                        FIREWALL_RULE_DENY);
            } catch (ServiceSpecificException e) {
                loge(ourTag + "Failed to add uid " + uid, e);
                anyFailed = true;
                break;
            }
        }
        if (!anyFailed) {
            for (final int uid : toRemove) {
                try {
                    mBpfNetMaps.setUidRule(ConnectivityManager.FIREWALL_CHAIN_OEM_DENY_1, uid,
                            FIREWALL_RULE_ALLOW);
                } catch (ServiceSpecificException e) {
                    loge(ourTag + "Failed to remove uid " + uid, e);
                    anyFailed = true;
                    break;
                }
            }
        }
        if (anyFailed) {
            // Should never happen, but just in case.
            replaceFirewallChain(ConnectivityManager.FIREWALL_CHAIN_OEM_DENY_1,
                    newDenylist.stream().mapToInt(Integer::intValue).toArray());
        }
        // Tell NPMS about the changes, primarily so the firewall icon can reflect them.
        mPolicyManager.notifyDenylistChanged(toAdd.stream().mapToInt(Integer::intValue).toArray(),
                toRemove.stream().mapToInt(Integer::intValue).toArray());
        if (DDBG) Log.d(TAG, ourTag + "end");
    }


    /**
     * Generates an allowlist configuration for Netd that reflects a network's allowed UIDs and
     * includes a sub priority to allow the network to act as a default network for the UIDs
     * if it is considered to be the default network overall. This default network handling is
     * required, or else UIDs that do not make specific network requests will have no connectivity.
     */
    private NativeUidRangeConfig getAllowlistedNativeUidRangeConfigForNetwork(
            @NonNull final NetworkAgentInfo nai) {
        final int netId = nai.network.netId;
        final var uidsAllowedByPolicy = getUidRangeParcelsAllowedByPolicyForNetwork(nai);
        final boolean isDefault = isDefaultNetwork(nai);
        final int subPriority = isDefault ? PREFERENCE_ORDER_LOWEST_WITH_DEFAULT
                : PREFERENCE_ORDER_IRRELEVANT_BECAUSE_NOT_DEFAULT;
        return new NativeUidRangeConfig(netId, uidsAllowedByPolicy, subPriority);
    }

    /**
     * Updates our tracked configurations for Netd based on changes to default networks, and
     * informs Netd if any changes need to be made to ensure that allowed UIDs do or do not utilize
     * changed networks as their default network. This default network handling is required, or
     * else UIDs that do not make specific network requests will have no connectivity or will
     * have their traffic traverse the wrong network.
     */
    private void updateUidDefaultNetworkRules(@Nullable final NetworkAgentInfo newDefaultNetwork) {
        final Integer defaultNetId = newDefaultNetwork == null ? null
                : newDefaultNetwork.network.netId;
        final var configs = new ArrayList<Pair<NativeUidRangeConfig, NativeUidRangeConfig>>();
        final var networksForUpdateDisallowedUids = new ArrayList<NetworkAgentInfo>();
        synchronized (mNetworkForNetId) {
            for (final var config : mNetIdToAllowlist.values()) {
                final var configIsDefault =
                        config.subPriority == PREFERENCE_ORDER_LOWEST_WITH_DEFAULT;
                final int subPriority;
                if (configIsDefault && !Objects.equals(config.netId, defaultNetId)) {
                    // Remove and replace any existing rules with a subPriority for default.
                    subPriority = PREFERENCE_ORDER_IRRELEVANT_BECAUSE_NOT_DEFAULT;
                } else if (!configIsDefault && Objects.equals(config.netId, defaultNetId)) {
                    // Remove and replace any existing rules with a subPriority for default.
                    subPriority = PREFERENCE_ORDER_LOWEST_WITH_DEFAULT;
                } else {
                    continue;
                }
                final var newConfig =
                        new NativeUidRangeConfig(config.netId, config.uidRanges, subPriority);
                mNetIdToAllowlist.put(newConfig.netId, newConfig);
                configs.add(new Pair<>(config, newConfig));
                final var nai = mNetworkForNetId.get(config.netId);
                if (nai != null) {
                    networksForUpdateDisallowedUids.add(nai);
                }
            }
        }
        for (final var pair : configs) {
            try {
                mNetd.networkAddUidRangesParcel(pair.second);
                mNetd.networkRemoveUidRangesParcel(pair.first);
            } catch (RemoteException | ServiceSpecificException e) {
                loge("updateUidDefaultNetworkRules: Exception while updating", e);
            }
        }
        for (final var nai : networksForUpdateDisallowedUids) {
            updateDisallowedUidsForNetwork(nai);
        }
    }

    /**
     * Generates and stores an allowlist configuration based on the policies that apply to the
     * given network, and sends this configuration to Netd. Uses the same methods for adding UID
     * range-based IP rules as is used by VPNs or by restricted networks which manually specify
     * their allowed UIDs, ensuring absent UIDs cannot be routed there. {@see updateAllowedUids}
     */
    private void updateAllowedUidsForNetwork(@NonNull final NetworkAgentInfo nai) {
        if (!nai.networkInfo.isConnected()) {
            // Network is not connected, so we cannot currently update allowed UIDs.
            // If we try, Netd responds with: "Machine is not on the network (code 64)"
            return;
        }
        final int netId = nai.network.netId;
        final var ourTag = "updateAllowedUidsForNetwork(" + netId + "): ";
        if (DDBG) Log.d(TAG, ourTag + "begin");
        final var config = getAllowlistedNativeUidRangeConfigForNetwork(nai);
        final NativeUidRangeConfig lastConfig;
        synchronized (mNetworkForNetId) {
            lastConfig = mNetIdToAllowlist.get(netId);
            mNetIdToAllowlist.put(netId, config);
        }
        try {
            if (config.uidRanges != null && config.uidRanges.length > 0) {
                mNetd.networkAddUidRangesParcel(config);
            }
            if (lastConfig != null && lastConfig.uidRanges != null
                    && lastConfig.uidRanges.length > 0) {
                mNetd.networkRemoveUidRangesParcel(lastConfig);
            }
        } catch (Exception e) {
            loge(ourTag + "Exception encountered", e);
        }
        if (DDBG) Log.d(TAG, ourTag + "end");
    }

    private static final long TRANSPORT_VPN_FLAG = 1 << TRANSPORT_VPN;

    /**
     * Returns true if all of the provided transports are allowed, or if transports represent
     * a VPN and VPNs are allowed. Otherwise, returns false.
     */
    private static boolean areTransportsAllowed(final long transports,
            final long allowedTransports) {
        // TODO: In the future, we may want to provide a way to restrict VPN access based on
        // its other transports, e.g. to block apps from using mobile data even if that data
        // is over a VPN. This is possible by removing or extending the check below.
        if ((transports & TRANSPORT_VPN_FLAG) != 0) {
            // For a VPN, all that matters is VPN access, nothing else.
            return (allowedTransports & TRANSPORT_VPN_FLAG) != 0;
        }
        return (allowedTransports & transports) == transports;
    }

    /**
     * Returns a list of UIDs that are policy-restricted based on the provided network and its
     * capabilities. Networks that are restricted by the system choose their own allowed UIDs,
     * so we do not disallow any UIDs for those networks.
     */
    private List<Integer> getUidsDisallowedByPolicyForNetwork(
            @NonNull final NetworkAgentInfo nai) {
        final var ourTag = "getUidsDisallowedByPolicyForNetwork(" + nai.network.netId + "): ";
        final var uids = new ArrayList<Integer>();
        if (!nai.networkInfo.isConnectedOrConnecting()) {
            // Network is not connected so nothing needs to be disallowed.
            if (DDBG) Log.d(TAG, ourTag + "not connected, so empty");
            return uids;
        }
        final NetworkCapabilities nc = nai.networkCapabilities;
        if (!nc.hasCapability(NET_CAPABILITY_NOT_RESTRICTED)) {
            // We do not want to meddle with networks that are already considered restricted.
            // These will have their own allowed UIDs.
            if (DDBG) Log.d(TAG, ourTag + "restricted, so empty");
            return uids;
        }
        final long packedTransports = BitUtils.packBits(nc.getTransportTypes());
        if (packedTransports == 0L) {
            // If we are not supplied with transports to check, nothing is allowed.
            if (DDBG) Log.d(TAG, ourTag + "transports == 0L, so empty");
            return uids;
        }
        final int size = mUidAllowedTransports.size();
        int lastUid = 0;
        for (int i = 0; i < size; i++) {
            int uid = mUidAllowedTransports.keyAt(i);
            long allowedTransports = mUidAllowedTransports.valueAt(i);
            if (!nc.appliesToUid(uid)) continue;
            if (!areTransportsAllowed(packedTransports, allowedTransports)) {
                uids.add(uid);
            }
        }
        return uids;
    }

    /**
     * Returns an array of UidRangeParcel representing all possible UIDs that are not restricted
     * by policy, based on the provided network and its capabilities.
     */
    private UidRangeParcel[] getUidRangeParcelsAllowedByPolicyForNetwork(
            @NonNull final NetworkAgentInfo nai) {
        final var uids = getUidsDisallowedByPolicyForNetwork(nai);
        final var ranges = new ArrayList<UidRangeParcel>();
        int lastUid = 0;
        for (final Integer uid : uids) {
            if (uid != lastUid) {
                ranges.add(new UidRangeParcel(lastUid, uid - 1));
            }
            lastUid = uid + 1;
        }
        ranges.add(new UidRangeParcel(lastUid, Integer.MAX_VALUE));
        return ranges.toArray(new UidRangeParcel[0]);
    }

    private final Context mContext;
    private final ConnectivityResources mResources;
    private final int mWakeUpMark;
    private final int mWakeUpMask;
    // The Context is created for UserHandle.ALL.
    private final Context mUserAllContext;
    private final Dependencies mDeps;
    private final ConnectivityFlags mFlags;
    // 0 is full bad, 100 is full good
    private int mDefaultInetConditionPublished = 0;

    @VisibleForTesting
    protected IDnsResolver mDnsResolver;
    @VisibleForTesting
    protected INetd mNetd;
    private DscpPolicyTracker mDscpPolicyTracker = null;
    private final NetworkStatsManager mStatsManager;
    private final NetworkPolicyManager mPolicyManager;
    private final BpfNetMaps mBpfNetMaps;

    /**
     * TestNetworkService (lazily) created upon first usage. Locked to prevent creation of multiple
     * instances.
     */
    @GuardedBy("mTNSLock")
    private TestNetworkService mTNS;
    private final CompanionDeviceManagerProxyService mCdmps;
    private final MulticastRoutingCoordinatorService mMulticastRoutingCoordinatorService;
    private final RoutingCoordinatorService mRoutingCoordinatorService;

    private final Object mTNSLock = new Object();

    private String mCurrentTcpBufferSizes;

    private static final SparseArray<String> sMagicDecoderRing = MessageUtils.findMessageNames(
            new Class[] {
                    ConnectivityService.class,
                    NetworkAgent.class,
                    NetworkAgentInfo.class,
                    AutomaticOnOffKeepaliveTracker.class });

    private enum ReapUnvalidatedNetworks {
        // Tear down networks that have no chance (e.g. even if validated) of becoming
        // the highest scoring network satisfying a NetworkRequest.  This should be passed when
        // all networks have been rematched against all NetworkRequests.
        REAP,
        // Don't reap networks.  This should be passed when some networks have not yet been
        // rematched against all NetworkRequests.
        DONT_REAP
    }

    private enum UnneededFor {
        LINGER,    // Determine whether this network is unneeded and should be lingered.
        TEARDOWN,  // Determine whether this network is unneeded and should be torn down.
    }

    /**
     * For per-app preferences, requests contain an int to signify which request
     * should have priority. The order is passed to netd which will use it together
     * with UID ranges to generate the corresponding IP rule. This serves to
     * direct device-originated data traffic of the specific UIDs to the correct
     * default network for each app.
     * Order ints passed to netd must be in the 0~999 range. Larger values code for
     * a lower priority, see {@link NativeUidRangeConfig}.
     *
     * Requests that don't code for a per-app preference use PREFERENCE_ORDER_INVALID.
     * The default request uses PREFERENCE_ORDER_DEFAULT.
     */
    // Used when sending to netd to code for "no order".
    static final int PREFERENCE_ORDER_NONE = 0;
    // Order for requests that don't code for a per-app preference. As it is
    // out of the valid range, the corresponding order should be
    // PREFERENCE_ORDER_NONE when sending to netd.
    @VisibleForTesting
    static final int PREFERENCE_ORDER_INVALID = Integer.MAX_VALUE;
    // As a security feature, VPNs have the top priority.
    static final int PREFERENCE_ORDER_VPN = 0; // Netd supports only 0 for VPN.
    // Order of per-app OEM preference. See {@link #setOemNetworkPreference}.
    @VisibleForTesting
    static final int PREFERENCE_ORDER_OEM = 10;
    // Order of per-profile preference, such as used by enterprise networks.
    // See {@link #setProfileNetworkPreference}.
    @VisibleForTesting
    static final int PREFERENCE_ORDER_PROFILE = 20;
    // Order of user setting to prefer mobile data even when networks with
    // better scores are connected.
    // See {@link ConnectivitySettingsManager#setMobileDataPreferredUids}
    @VisibleForTesting
    static final int PREFERENCE_ORDER_MOBILE_DATA_PREFERERRED = 30;
    // Order of setting satellite network preference fallback when default message application
    // with role_sms role and android.permission.SATELLITE_COMMUNICATION permission detected
    @VisibleForTesting
    static final int PREFERENCE_ORDER_SATELLITE_FALLBACK = 40;
    // Lowest subpriority that still adds default network rules.
    static final int PREFERENCE_ORDER_LOWEST_WITH_DEFAULT = 998;
    // Preference order that signifies the network shouldn't be set as a default network for
    // the UIDs, only give them access to it. TODO : replace this with a boolean
    // in NativeUidRangeConfig
    @VisibleForTesting
    static final int PREFERENCE_ORDER_IRRELEVANT_BECAUSE_NOT_DEFAULT = 999;
    // Bound for the lowest valid preference order.
    static final int PREFERENCE_ORDER_LOWEST = 999;

    /**
     * used internally to clear a wakelock when transitioning
     * from one net to another.  Clear happens when we get a new
     * network - EVENT_EXPIRE_NET_TRANSITION_WAKELOCK happens
     * after a timeout if no network is found (typically 1 min).
     */
    private static final int EVENT_CLEAR_NET_TRANSITION_WAKELOCK = 8;

    /**
     * used internally to reload global proxy settings
     */
    private static final int EVENT_APPLY_GLOBAL_HTTP_PROXY = 9;

    /**
     * PAC manager has received new port.
     */
    private static final int EVENT_PAC_PROXY_HAS_CHANGED = 16;

    /**
     * used internally when registering NetworkProviders
     * obj = NetworkProviderInfo
     */
    private static final int EVENT_REGISTER_NETWORK_PROVIDER = 17;

    /**
     * used internally when registering NetworkAgents
     * obj = Messenger
     */
    private static final int EVENT_REGISTER_NETWORK_AGENT = 18;

    /**
     * used to add a network request
     * includes a NetworkRequestInfo
     */
    private static final int EVENT_REGISTER_NETWORK_REQUEST = 19;

    /**
     * indicates a timeout period is over - check if we had a network yet or not
     * and if not, call the timeout callback (but leave the request live until they
     * cancel it.
     * includes a NetworkRequestInfo
     */
    private static final int EVENT_TIMEOUT_NETWORK_REQUEST = 20;

    /**
     * used to add a network listener - no request
     * includes a NetworkRequestInfo
     */
    private static final int EVENT_REGISTER_NETWORK_LISTENER = 21;

    /**
     * used to remove a network request, either a listener or a real request
     * arg1 = UID of caller
     * obj  = NetworkRequest
     */
    private static final int EVENT_RELEASE_NETWORK_REQUEST = 22;

    /**
     * used internally when registering NetworkProviders
     * obj = Messenger
     */
    private static final int EVENT_UNREGISTER_NETWORK_PROVIDER = 23;

    /**
     * used internally to expire a wakelock when transitioning
     * from one net to another.  Expire happens when we fail to find
     * a new network (typically after 1 minute) -
     * EVENT_CLEAR_NET_TRANSITION_WAKELOCK happens if we had found
     * a replacement network.
     */
    private static final int EVENT_EXPIRE_NET_TRANSITION_WAKELOCK = 24;

    /**
     * used to add a network request with a pending intent
     * obj = NetworkRequestInfo
     */
    private static final int EVENT_REGISTER_NETWORK_REQUEST_WITH_INTENT = 26;

    /**
     * used to remove a pending intent and its associated network request.
     * arg1 = UID of caller
     * obj  = PendingIntent
     */
    private static final int EVENT_RELEASE_NETWORK_REQUEST_WITH_INTENT = 27;

    /**
     * used to specify whether a network should be used even if unvalidated.
     * arg1 = whether to accept the network if it's unvalidated (1 or 0)
     * arg2 = whether to remember this choice in the future (1 or 0)
     * obj  = network
     */
    private static final int EVENT_SET_ACCEPT_UNVALIDATED = 28;

    /**
     * used internally to (re)configure always-on networks.
     */
    private static final int EVENT_CONFIGURE_ALWAYS_ON_NETWORKS = 30;

    /**
     * used to add a network listener with a pending intent
     * obj = NetworkRequestInfo
     */
    private static final int EVENT_REGISTER_NETWORK_LISTENER_WITH_INTENT = 31;

    /**
     * used to specify whether a network should not be penalized when it becomes unvalidated.
     */
    private static final int EVENT_SET_AVOID_UNVALIDATED = 35;

    /**
     * used to handle reported network connectivity. May trigger revalidation of a network.
     */
    private static final int EVENT_REPORT_NETWORK_CONNECTIVITY = 36;

    // Handle changes in Private DNS settings.
    private static final int EVENT_PRIVATE_DNS_SETTINGS_CHANGED = 37;

    // Handle private DNS validation status updates.
    private static final int EVENT_PRIVATE_DNS_VALIDATION_UPDATE = 38;

     /**
      * Event for NetworkMonitor/NetworkAgentInfo to inform ConnectivityService that the network has
      * been tested.
      * obj = {@link NetworkTestedResults} representing information sent from NetworkMonitor.
      * data = PersistableBundle of extras passed from NetworkMonitor. If {@link
      * NetworkMonitorCallbacks#notifyNetworkTested} is called, this will be null.
      */
    private static final int EVENT_NETWORK_TESTED = 41;

    /**
     * Event for NetworkMonitor/NetworkAgentInfo to inform ConnectivityService that the private DNS
     * config was resolved.
     * obj = PrivateDnsConfig
     * arg2 = netid
     */
    private static final int EVENT_PRIVATE_DNS_CONFIG_RESOLVED = 42;

    /**
     * Request ConnectivityService display provisioning notification.
     * arg1    = Whether to make the notification visible.
     * arg2    = NetID.
     * obj     = Intent to be launched when notification selected by user, null if !arg1.
     */
    private static final int EVENT_PROVISIONING_NOTIFICATION = 43;

    /**
     * Used to specify whether a network should be used even if connectivity is partial.
     * arg1 = whether to accept the network if its connectivity is partial (1 for true or 0 for
     * false)
     * arg2 = whether to remember this choice in the future (1 for true or 0 for false)
     * obj  = network
     */
    private static final int EVENT_SET_ACCEPT_PARTIAL_CONNECTIVITY = 44;

    /**
     * Event for NetworkMonitor to inform ConnectivityService that the probe status has changed.
     * Both of the arguments are bitmasks, and the value of bits come from
     * INetworkMonitor.NETWORK_VALIDATION_PROBE_*.
     * arg1 = unused
     * arg2 = netId
     * obj = A Pair of integers: the bitmasks of, respectively, completed and successful probes.
     */
    public static final int EVENT_PROBE_STATUS_CHANGED = 45;

    /**
     * Event for NetworkMonitor to inform ConnectivityService that captive portal data has changed.
     * arg1 = unused
     * arg2 = netId
     * obj = captive portal data
     */
    private static final int EVENT_CAPPORT_DATA_CHANGED = 46;

    /**
     * Used by setRequireVpnForUids.
     * arg1 = whether the specified UID ranges are required to use a VPN.
     * obj  = Array of UidRange objects.
     */
    private static final int EVENT_SET_REQUIRE_VPN_FOR_UIDS = 47;

    /**
     * Used internally when setting the default networks for OemNetworkPreferences.
     * obj = Pair<OemNetworkPreferences, listener>
     */
    private static final int EVENT_SET_OEM_NETWORK_PREFERENCE = 48;

    /**
     * Used to indicate the system default network becomes active.
     */
    private static final int EVENT_REPORT_NETWORK_ACTIVITY = 49;

    /**
     * Used internally when setting a network preference for a user profile.
     * obj = Pair<ProfileNetworkPreference, Listener>
     */
    private static final int EVENT_SET_PROFILE_NETWORK_PREFERENCE = 50;

    /**
     * Event to specify that reasons for why an uid is blocked changed.
     * arg1 = uid
     * arg2 = blockedReasons
     */
    private static final int EVENT_UID_BLOCKED_REASON_CHANGED = 51;

    /**
     * Event to register a new network offer
     * obj = NetworkOffer
     */
    private static final int EVENT_REGISTER_NETWORK_OFFER = 52;

    /**
     * Event to unregister an existing network offer
     * obj = INetworkOfferCallback
     */
    private static final int EVENT_UNREGISTER_NETWORK_OFFER = 53;

    /**
     * Used internally when MOBILE_DATA_PREFERRED_UIDS setting changed.
     */
    private static final int EVENT_MOBILE_DATA_PREFERRED_UIDS_CHANGED = 54;

    /**
     * Event to set temporary allow bad wifi within a limited time to override
     * {@code config_networkAvoidBadWifi}.
     */
    private static final int EVENT_SET_TEST_ALLOW_BAD_WIFI_UNTIL = 55;

    /**
     * Used internally when INGRESS_RATE_LIMIT_BYTES_PER_SECOND setting changes.
     */
    private static final int EVENT_INGRESS_RATE_LIMIT_CHANGED = 56;

    /**
     * The initial evaluation period is over for this network.
     *
     * If no form of connectivity has been found on this network (valid, partial, captive portal)
     * then the stack will now consider it to have been determined bad.
     */
    private static final int EVENT_INITIAL_EVALUATION_TIMEOUT = 57;

    /**
     * Used internally when the user does not want the network from captive portal app.
     * obj = Network
     */
    private static final int EVENT_USER_DOES_NOT_WANT = 58;

    /**
     * Event to set VPN as preferred network for specific apps.
     * obj = VpnNetworkPreferenceInfo
     */
    private static final int EVENT_SET_VPN_NETWORK_PREFERENCE = 59;

    /**
     * Event to use low TCP polling timer used in automatic on/off keepalive temporarily.
     */
    private static final int EVENT_SET_LOW_TCP_POLLING_UNTIL = 60;

    /**
     * Event to inform the ConnectivityService handler when a uid has been frozen or unfrozen.
     */
    private static final int EVENT_UID_FROZEN_STATE_CHANGED = 61;

    /**
     * Event to inform the ConnectivityService handler when a uid has lost carrier privileges.
     */
    private static final int EVENT_UID_CARRIER_PRIVILEGES_LOST = 62;

    /**
     * Argument for {@link #EVENT_PROVISIONING_NOTIFICATION} to indicate that the notification
     * should be shown.
     */
    private static final int PROVISIONING_NOTIFICATION_SHOW = 1;

    /**
     * Argument for {@link #EVENT_PROVISIONING_NOTIFICATION} to indicate that the notification
     * should be hidden.
     */
    private static final int PROVISIONING_NOTIFICATION_HIDE = 0;

    /**
     * The maximum alive time to allow bad wifi configuration for testing.
     */
    private static final long MAX_TEST_ALLOW_BAD_WIFI_UNTIL_MS = 5 * 60 * 1000L;

    /**
     * The maximum alive time to decrease TCP polling timer in automatic on/off keepalive for
     * testing.
     */
    private static final long MAX_TEST_LOW_TCP_POLLING_UNTIL_MS = 5 * 60 * 1000L;

    /**
     * The priority of the tc police rate limiter -- smaller value is higher priority.
     * This value needs to be coordinated with PRIO_CLAT, PRIO_TETHER4, and PRIO_TETHER6.
     */
    private static final short TC_PRIO_POLICE = 1;

    /**
     * The BPF program attached to the tc-police hook to account for to-be-dropped traffic.
     */
    private static final String TC_POLICE_BPF_PROG_PATH =
            "/sys/fs/bpf/netd_shared/prog_netd_schedact_ingress_account";

    private static String eventName(int what) {
        return sMagicDecoderRing.get(what, Integer.toString(what));
    }

    private static IDnsResolver getDnsResolver(Context context) {
        final DnsResolverServiceManager dsm = context.getSystemService(
                DnsResolverServiceManager.class);
        return IDnsResolver.Stub.asInterface(dsm.getService());
    }

    /** Handler thread used for all of the handlers below. */
    @VisibleForTesting
    protected final HandlerThread mHandlerThread;
    /** Handler used for internal events. */
    final private InternalHandler mHandler;
    /** Handler used for incoming {@link NetworkStateTracker} events. */
    final private NetworkStateTrackerHandler mTrackerHandler;
    /** Handler used for processing {@link android.net.ConnectivityDiagnosticsManager} events */
    @VisibleForTesting
    final ConnectivityDiagnosticsHandler mConnectivityDiagnosticsHandler;

    private final DnsManager mDnsManager;
    @VisibleForTesting
    final NetworkRanker mNetworkRanker;

    private boolean mSystemReady;
    private Intent mInitialBroadcast;

    private final PowerManager.WakeLock mNetTransitionWakeLock;
    private final PowerManager.WakeLock mPendingIntentWakeLock;

    // A helper object to track the current default HTTP proxy. ConnectivityService needs to tell
    // the world when it changes.
    private final ProxyTracker mProxyTracker;

    final private SettingsObserver mSettingsObserver;

    private final UserManager mUserManager;

    // the set of network types that can only be enabled by system/sig apps
    private final List<Integer> mProtectedNetworks;

    private Set<String> mWolSupportedInterfaces;

    private final TelephonyManager mTelephonyManager;
    private final CarrierPrivilegeAuthenticator mCarrierPrivilegeAuthenticator;
    private final AppOpsManager mAppOpsManager;

    private final LocationPermissionChecker mLocationPermissionChecker;

    private final AutomaticOnOffKeepaliveTracker mKeepaliveTracker;
    private final QosCallbackTracker mQosCallbackTracker;
    private final NetworkNotificationManager mNotifier;
    private final LingerMonitor mLingerMonitor;
    private final SatelliteAccessController mSatelliteAccessController;

    // sequence number of NetworkRequests
    private int mNextNetworkRequestId = NetworkRequest.FIRST_REQUEST_ID;

    // Sequence number for NetworkProvider IDs.
    private final AtomicInteger mNextNetworkProviderId = new AtomicInteger(
            NetworkProvider.FIRST_PROVIDER_ID);

    // NetworkRequest activity String log entries.
    private static final int MAX_NETWORK_REQUEST_LOGS = 20;
    private final LocalLog mNetworkRequestInfoLogs = new LocalLog(MAX_NETWORK_REQUEST_LOGS);

    // NetworkInfo blocked and unblocked String log entries
    private static final int MAX_NETWORK_INFO_LOGS = 40;
    private final LocalLog mNetworkInfoBlockingLogs = new LocalLog(MAX_NETWORK_INFO_LOGS);

    private static final int MAX_WAKELOCK_LOGS = 20;
    private final LocalLog mWakelockLogs = new LocalLog(MAX_WAKELOCK_LOGS);
    private int mTotalWakelockAcquisitions = 0;
    private int mTotalWakelockReleases = 0;
    private long mTotalWakelockDurationMs = 0;
    private long mMaxWakelockDurationMs = 0;
    private long mLastWakeLockAcquireTimestamp = 0;

    private final IpConnectivityLog mMetricsLog;

    @Nullable private final NetworkRequestStateStatsMetrics mNetworkRequestStateStatsMetrics;

    @GuardedBy("mBandwidthRequests")
    private final SparseArray<Integer> mBandwidthRequests = new SparseArray<>(10);

    @VisibleForTesting
    final MultinetworkPolicyTracker mMultinetworkPolicyTracker;

    @VisibleForTesting
    final Map<IBinder, ConnectivityDiagnosticsCallbackInfo> mConnectivityDiagnosticsCallbacks =
            new HashMap<>();

    // Rate limit applicable to all internet capable networks (-1 = disabled). This value is
    // configured via {@link
    // ConnectivitySettingsManager#INGRESS_RATE_LIMIT_BYTES_PER_SECOND}
    // Only the handler thread is allowed to access this field.
    private long mIngressRateLimit = -1;

    // This is the cache for the packageName -> ApplicationSelfCertifiedNetworkCapabilities. This
    // value can be accessed from both handler thread and any random binder thread. Therefore,
    // accessing this value requires holding a lock. The cache is the same across all the users.
    @GuardedBy("mSelfCertifiedCapabilityCache")
    private final Map<String, ApplicationSelfCertifiedNetworkCapabilities>
            mSelfCertifiedCapabilityCache = new HashMap<>();

    // Flag to enable the feature of closing frozen app sockets.
    private final boolean mDestroyFrozenSockets;

    // Flag to optimize closing frozen app sockets by waiting for the cellular modem to wake up.
    private final boolean mDelayDestroyFrozenSockets;

    // Flag to allow SysUI to receive connectivity reports for wifi picker UI.
    private final boolean mAllowSysUiConnectivityReports;

    // Uids that ConnectivityService is pending to close sockets of.
    private final Set<Integer> mPendingFrozenUids = new ArraySet<>();

    // Flag to drop packets to VPN addresses ingressing via non-VPN interfaces.
    private final boolean mIngressToVpnAddressFiltering;

    /**
     * Implements support for the legacy "one network per network type" model.
     *
     * We used to have a static array of NetworkStateTrackers, one for each
     * network type, but that doesn't work any more now that we can have,
     * for example, more that one wifi network. This class stores all the
     * NetworkAgentInfo objects that support a given type, but the legacy
     * API will only see the first one.
     *
     * It serves two main purposes:
     *
     * 1. Provide information about "the network for a given type" (since this
     *    API only supports one).
     * 2. Send legacy connectivity change broadcasts. Broadcasts are sent if
     *    the first network for a given type changes, or if the default network
     *    changes.
     */
    @VisibleForTesting
    static class LegacyTypeTracker {

        private static final boolean DBG = true;
        private static final boolean VDBG = false;

        /**
         * Array of lists, one per legacy network type (e.g., TYPE_MOBILE_MMS).
         * Each list holds references to all NetworkAgentInfos that are used to
         * satisfy requests for that network type.
         *
         * This array is built out at startup such that an unsupported network
         * doesn't get an ArrayList instance, making this a tristate:
         * unsupported, supported but not active and active.
         *
         * The actual lists are populated when we scan the network types that
         * are supported on this device.
         *
         * Threading model:
         *  - addSupportedType() is only called in the constructor
         *  - add(), update(), remove() are only called from the ConnectivityService handler thread.
         *    They are therefore not thread-safe with respect to each other.
         *  - getNetworkForType() can be called at any time on binder threads. It is synchronized
         *    on mTypeLists to be thread-safe with respect to a concurrent remove call.
         *  - getRestoreTimerForType(type) is also synchronized on mTypeLists.
         *  - dump is thread-safe with respect to concurrent add and remove calls.
         */
        private final ArrayList<NetworkAgentInfo>[] mTypeLists;
        @NonNull
        private final ConnectivityService mService;

        // Restore timers for requestNetworkForFeature (network type -> timer in ms). Types without
        // an entry have no timer (equivalent to -1). Lazily loaded.
        @NonNull
        private ArrayMap<Integer, Integer> mRestoreTimers = new ArrayMap<>();

        LegacyTypeTracker(@NonNull ConnectivityService service) {
            mService = service;
            mTypeLists = new ArrayList[ConnectivityManager.MAX_NETWORK_TYPE + 1];
        }

        // TODO: Set the mini sdk to 31 and remove @TargetApi annotation when b/205923322 is
        //  addressed.
        @TargetApi(Build.VERSION_CODES.S)
        public void loadSupportedTypes(@NonNull Context ctx, @NonNull TelephonyManager tm) {
            final PackageManager pm = ctx.getPackageManager();
            if (pm.hasSystemFeature(FEATURE_WIFI)) {
                addSupportedType(TYPE_WIFI);
            }
            if (pm.hasSystemFeature(FEATURE_WIFI_DIRECT)) {
                addSupportedType(TYPE_WIFI_P2P);
            }
            if (tm.isDataCapable()) {
                // Telephony does not have granular support for these types: they are either all
                // supported, or none is supported
                addSupportedType(TYPE_MOBILE);
                addSupportedType(TYPE_MOBILE_MMS);
                addSupportedType(TYPE_MOBILE_SUPL);
                addSupportedType(TYPE_MOBILE_DUN);
                addSupportedType(TYPE_MOBILE_HIPRI);
                addSupportedType(TYPE_MOBILE_FOTA);
                addSupportedType(TYPE_MOBILE_IMS);
                addSupportedType(TYPE_MOBILE_CBS);
                addSupportedType(TYPE_MOBILE_IA);
                addSupportedType(TYPE_MOBILE_EMERGENCY);
            }
            if (pm.hasSystemFeature(FEATURE_BLUETOOTH)) {
                addSupportedType(TYPE_BLUETOOTH);
            }
            if (pm.hasSystemFeature(FEATURE_WATCH)) {
                // TYPE_PROXY is only used on Wear
                addSupportedType(TYPE_PROXY);
            }
            // Ethernet is often not specified in the configs, although many devices can use it via
            // USB host adapters. Add it as long as the ethernet service is here.
            if (deviceSupportsEthernet(ctx)) {
                addSupportedType(TYPE_ETHERNET);
            }

            // Always add TYPE_VPN as a supported type
            addSupportedType(TYPE_VPN);
        }

        private void addSupportedType(int type) {
            if (mTypeLists[type] != null) {
                throw new IllegalStateException(
                        "legacy list for type " + type + "already initialized");
            }
            mTypeLists[type] = new ArrayList<>();
        }

        public boolean isTypeSupported(int type) {
            return isNetworkTypeValid(type) && mTypeLists[type] != null;
        }

        public NetworkAgentInfo getNetworkForType(int type) {
            synchronized (mTypeLists) {
                if (isTypeSupported(type) && !mTypeLists[type].isEmpty()) {
                    return mTypeLists[type].get(0);
                }
            }
            return null;
        }

        public int getRestoreTimerForType(int type) {
            synchronized (mTypeLists) {
                if (mRestoreTimers == null) {
                    mRestoreTimers = loadRestoreTimers();
                }
                return mRestoreTimers.getOrDefault(type, -1);
            }
        }

        private ArrayMap<Integer, Integer> loadRestoreTimers() {
            final String[] configs = mService.mResources.get().getStringArray(
                    R.array.config_legacy_networktype_restore_timers);
            final ArrayMap<Integer, Integer> ret = new ArrayMap<>(configs.length);
            for (final String config : configs) {
                final String[] splits = TextUtils.split(config, ",");
                if (splits.length != 2) {
                    logwtf("Invalid restore timer token count: " + config);
                    continue;
                }
                try {
                    ret.put(Integer.parseInt(splits[0]), Integer.parseInt(splits[1]));
                } catch (NumberFormatException e) {
                    logwtf("Invalid restore timer number format: " + config, e);
                }
            }
            return ret;
        }

        private void maybeLogBroadcast(NetworkAgentInfo nai, DetailedState state, int type,
                boolean isDefaultNetwork) {
            if (DBG) {
                log("Sending " + state
                        + " broadcast for type " + type + " " + nai.toShortString()
                        + " isDefaultNetwork=" + isDefaultNetwork);
            }
        }

        // When a lockdown VPN connects, send another CONNECTED broadcast for the underlying
        // network type, to preserve previous behaviour.
        private void maybeSendLegacyLockdownBroadcast(@NonNull NetworkAgentInfo vpnNai) {
            if (vpnNai != mService.getLegacyLockdownNai()) return;

            if (vpnNai.declaredUnderlyingNetworks == null
                    || vpnNai.declaredUnderlyingNetworks.length != 1) {
                Log.wtf(TAG, "Legacy lockdown VPN must have exactly one underlying network: "
                        + Arrays.toString(vpnNai.declaredUnderlyingNetworks));
                return;
            }
            final NetworkAgentInfo underlyingNai = mService.getNetworkAgentInfoForNetwork(
                    vpnNai.declaredUnderlyingNetworks[0]);
            if (underlyingNai == null) return;

            final int type = underlyingNai.networkInfo.getType();
            final DetailedState state = DetailedState.CONNECTED;
            maybeLogBroadcast(underlyingNai, state, type, true /* isDefaultNetwork */);
            mService.sendLegacyNetworkBroadcast(underlyingNai, state, type);
        }

        /** Adds the given network to the specified legacy type list. */
        public void add(int type, NetworkAgentInfo nai) {
            if (!isTypeSupported(type)) {
                return;  // Invalid network type.
            }
            if (VDBG) log("Adding agent " + nai + " for legacy network type " + type);

            ArrayList<NetworkAgentInfo> list = mTypeLists[type];
            if (list.contains(nai)) {
                return;
            }
            synchronized (mTypeLists) {
                list.add(nai);
            }

            // Send a broadcast if this is the first network of its type or if it's the default.
            final boolean isDefaultNetwork = mService.isDefaultNetwork(nai);

            // If a legacy lockdown VPN is active, override the NetworkInfo state in all broadcasts
            // to preserve previous behaviour.
            final DetailedState state = mService.getLegacyLockdownState(DetailedState.CONNECTED);
            if ((list.size() == 1) || isDefaultNetwork) {
                maybeLogBroadcast(nai, state, type, isDefaultNetwork);
                mService.sendLegacyNetworkBroadcast(nai, state, type);
            }

            if (type == TYPE_VPN && state == DetailedState.CONNECTED) {
                maybeSendLegacyLockdownBroadcast(nai);
            }
        }

        /** Removes the given network from the specified legacy type list. */
        public void remove(int type, NetworkAgentInfo nai, boolean wasDefault) {
            ArrayList<NetworkAgentInfo> list = mTypeLists[type];
            if (list == null || list.isEmpty()) {
                return;
            }
            final boolean wasFirstNetwork = list.get(0).equals(nai);

            synchronized (mTypeLists) {
                if (!list.remove(nai)) {
                    return;
                }
            }

            if (wasFirstNetwork || wasDefault) {
                maybeLogBroadcast(nai, DetailedState.DISCONNECTED, type, wasDefault);
                mService.sendLegacyNetworkBroadcast(nai, DetailedState.DISCONNECTED, type);
            }

            if (!list.isEmpty() && wasFirstNetwork) {
                if (DBG) log("Other network available for type " + type +
                              ", sending connected broadcast");
                final NetworkAgentInfo replacement = list.get(0);
                maybeLogBroadcast(replacement, DetailedState.CONNECTED, type,
                        mService.isDefaultNetwork(replacement));
                mService.sendLegacyNetworkBroadcast(replacement, DetailedState.CONNECTED, type);
            }
        }

        /** Removes the given network from all legacy type lists. */
        public void remove(NetworkAgentInfo nai, boolean wasDefault) {
            if (VDBG) log("Removing agent " + nai + " wasDefault=" + wasDefault);
            for (int type = 0; type < mTypeLists.length; type++) {
                remove(type, nai, wasDefault);
            }
        }

        // send out another legacy broadcast - currently only used for suspend/unsuspend toggle
        public void update(NetworkAgentInfo nai) {
            final boolean isDefault = mService.isDefaultNetwork(nai);
            final DetailedState state = nai.networkInfo.getDetailedState();
            for (int type = 0; type < mTypeLists.length; type++) {
                final ArrayList<NetworkAgentInfo> list = mTypeLists[type];
                final boolean contains = (list != null && list.contains(nai));
                final boolean isFirst = contains && (nai == list.get(0));
                if (isFirst || contains && isDefault) {
                    maybeLogBroadcast(nai, state, type, isDefault);
                    mService.sendLegacyNetworkBroadcast(nai, state, type);
                }
            }
        }

        public void dump(IndentingPrintWriter pw) {
            pw.println("mLegacyTypeTracker:");
            pw.increaseIndent();
            pw.print("Supported types:");
            for (int type = 0; type < mTypeLists.length; type++) {
                if (mTypeLists[type] != null) pw.print(" " + type);
            }
            pw.println();
            pw.println("Current state:");
            pw.increaseIndent();
            synchronized (mTypeLists) {
                for (int type = 0; type < mTypeLists.length; type++) {
                    if (mTypeLists[type] == null || mTypeLists[type].isEmpty()) continue;
                    for (NetworkAgentInfo nai : mTypeLists[type]) {
                        pw.println(type + " " + nai.toShortString());
                    }
                }
            }
            pw.decreaseIndent();
            pw.decreaseIndent();
            pw.println();
        }
    }
    private final LegacyTypeTracker mLegacyTypeTracker = new LegacyTypeTracker(this);

    @VisibleForTesting
    void onCarrierPrivilegesLost(Integer uid, Integer subId) {
        if (mRequestRestrictedWifiEnabled) {
            mHandler.sendMessage(mHandler.obtainMessage(
                    EVENT_UID_CARRIER_PRIVILEGES_LOST, uid, subId));
        }
    }

    final LocalPriorityDump mPriorityDumper = new LocalPriorityDump();
    /**
     * Helper class which parses out priority arguments and dumps sections according to their
     * priority. If priority arguments are omitted, function calls the legacy dump command.
     */
    private class LocalPriorityDump {
        private static final String PRIORITY_ARG = "--dump-priority";
        private static final String PRIORITY_ARG_HIGH = "HIGH";
        private static final String PRIORITY_ARG_NORMAL = "NORMAL";
        private static final int DUMPSYS_DEFAULT_TIMEOUT_MS = 10_000;

        LocalPriorityDump() {}

        private void dumpHigh(FileDescriptor fd, PrintWriter pw) {
            if (!HandlerUtils.runWithScissorsForDump(mHandler, () -> {
                doDump(fd, pw, new String[]{DIAG_ARG});
                doDump(fd, pw, new String[]{SHORT_ARG});
            }, DUMPSYS_DEFAULT_TIMEOUT_MS)) {
                pw.println("dumpHigh timeout");
            }
        }

        private void dumpNormal(FileDescriptor fd, PrintWriter pw, String[] args) {
            if (!HandlerUtils.runWithScissorsForDump(mHandler, () -> doDump(fd, pw, args),
                    DUMPSYS_DEFAULT_TIMEOUT_MS)) {
                pw.println("dumpNormal timeout");
            }
        }

        public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
            if (args == null) {
                dumpNormal(fd, pw, args);
                return;
            }

            String priority = null;
            for (int argIndex = 0; argIndex < args.length; argIndex++) {
                if (args[argIndex].equals(PRIORITY_ARG) && argIndex + 1 < args.length) {
                    argIndex++;
                    priority = args[argIndex];
                }
            }

            if (PRIORITY_ARG_HIGH.equals(priority)) {
                dumpHigh(fd, pw);
            } else if (PRIORITY_ARG_NORMAL.equals(priority)) {
                dumpNormal(fd, pw, args);
            } else {
                // ConnectivityService publishes binder service using publishBinderService() with
                // no priority assigned will be treated as NORMAL priority. Dumpsys does not send
                // "--dump-priority" arguments to the service. Thus, dump NORMAL only to align the
                // legacy output for dumpsys connectivity.
                // TODO: Integrate into signal dump.
                dumpNormal(fd, pw, args);
            }
        }
    }

    /**
     * Dependencies of ConnectivityService, for injection in tests.
     */
    @VisibleForTesting
    public static class Dependencies {
        public int getCallingUid() {
            return Binder.getCallingUid();
        }

        public boolean isAtLeastS() {
            return SdkLevel.isAtLeastS();
        }

        public boolean isAtLeastT() {
            return SdkLevel.isAtLeastT();
        }

        public boolean isAtLeastU() {
            return SdkLevel.isAtLeastU();
        }

        public boolean isAtLeastV() {
            return SdkLevel.isAtLeastV();
        }

        /**
         * Get system properties to use in ConnectivityService.
         */
        public MockableSystemProperties getSystemProperties() {
            return new MockableSystemProperties();
        }

        /**
         * Get the {@link ConnectivityResources} to use in ConnectivityService.
         */
        public ConnectivityResources getResources(@NonNull Context ctx) {
            return new ConnectivityResources(ctx);
        }

        /**
         * Create a HandlerThread to use in ConnectivityService.
         */
        public HandlerThread makeHandlerThread(@NonNull final String tag) {
            return new HandlerThread(tag);
        }

        /**
         * Get a reference to the ModuleNetworkStackClient.
         */
        public NetworkStackClientBase getNetworkStack() {
            return ModuleNetworkStackClient.getInstance(null);
        }

        /**
         * @see ProxyTracker
         */
        public ProxyTracker makeProxyTracker(@NonNull Context context,
                @NonNull Handler connServiceHandler) {
            return new ProxyTracker(context, connServiceHandler, EVENT_PAC_PROXY_HAS_CHANGED);
        }

        /**
         * @see NetIdManager
         */
        public NetIdManager makeNetIdManager() {
            return new NetIdManager();
        }

        /**
         * @see NetworkUtils#queryUserAccess(int, int)
         */
        public boolean queryUserAccess(int uid, Network network, ConnectivityService cs) {
            return cs.queryUserAccess(uid, network);
        }

        /**
         * Gets the UID that owns a socket connection. Needed because opening SOCK_DIAG sockets
         * requires CAP_NET_ADMIN, which the unit tests do not have.
         */
        public int getConnectionOwnerUid(int protocol, InetSocketAddress local,
                InetSocketAddress remote) {
            return InetDiagMessage.getConnectionOwnerUid(protocol, local, remote);
        }

        /**
         * @see MultinetworkPolicyTracker
         */
        public MultinetworkPolicyTracker makeMultinetworkPolicyTracker(
                @NonNull Context c, @NonNull Handler h, @NonNull Runnable r) {
            return new MultinetworkPolicyTracker(c, h, r);
        }

        /**
         * @see AutomaticOnOffKeepaliveTracker
         */
        public AutomaticOnOffKeepaliveTracker makeAutomaticOnOffKeepaliveTracker(
                @NonNull Context c, @NonNull Handler h) {
            return new AutomaticOnOffKeepaliveTracker(c, h);
        }

        public MulticastRoutingCoordinatorService makeMulticastRoutingCoordinatorService(
                    @NonNull Handler h) {
            try {
                return new MulticastRoutingCoordinatorService(h);
            } catch (UnsupportedOperationException e) {
                // Multicast routing is not supported by the kernel
                Log.i(TAG, "Skipping unsupported MulticastRoutingCoordinatorService");
                return null;
            }
        }

        /**
         * @see NetworkRequestStateStatsMetrics
         */
        public NetworkRequestStateStatsMetrics makeNetworkRequestStateStatsMetrics(
                Context context) {
            // We currently have network requests metric for Watch devices only
            if (context.getPackageManager().hasSystemFeature(FEATURE_WATCH)) {
                return new NetworkRequestStateStatsMetrics();
            } else {
                return null;
            }
        }

        /**
         * @see BatteryStatsManager
         */
        public void reportNetworkInterfaceForTransports(Context context, String iface,
                int[] transportTypes) {
            final BatteryStatsManager batteryStats =
                    context.getSystemService(BatteryStatsManager.class);
            batteryStats.reportNetworkInterfaceForTransports(iface, transportTypes);
        }

        public boolean getCellular464XlatEnabled() {
            return NetworkProperties.isCellular464XlatEnabled().orElse(true);
        }

        /**
         * @see PendingIntent#intentFilterEquals
         */
        public boolean intentFilterEquals(PendingIntent a, PendingIntent b) {
            return a.intentFilterEquals(b);
        }

        /**
         * @see LocationPermissionChecker
         */
        public LocationPermissionChecker makeLocationPermissionChecker(Context context) {
            return new LocationPermissionChecker(context);
        }

        /**
         * @see CarrierPrivilegeAuthenticator
         *
         * This method returns null in versions before T, where carrier privilege
         * authentication is not supported.
         */
        @Nullable
        public CarrierPrivilegeAuthenticator makeCarrierPrivilegeAuthenticator(
                @NonNull final Context context,
                @NonNull final TelephonyManager tm,
                boolean requestRestrictedWifiEnabled,
                @NonNull BiConsumer<Integer, Integer> listener) {
            if (isAtLeastT()) {
                return new CarrierPrivilegeAuthenticator(
                        context, tm, requestRestrictedWifiEnabled, listener);
            } else {
                return null;
            }
        }

        /**
         * @see SatelliteAccessController
         */
        @Nullable
        public SatelliteAccessController makeSatelliteAccessController(
                @NonNull final Context context,
                Consumer<Set<Integer>> updateSatelliteNetworkFallbackUidCallback,
                @NonNull final Handler connectivityServiceInternalHandler) {
            return new SatelliteAccessController(context, updateSatelliteNetworkFallbackUidCallback,
                    connectivityServiceInternalHandler);
        }

        /**
         * @see DeviceConfigUtils#isTetheringFeatureEnabled
         */
        public boolean isFeatureEnabled(Context context, String name) {
            return DeviceConfigUtils.isTetheringFeatureEnabled(context, name);
        }

        /**
         * @see DeviceConfigUtils#isTetheringFeatureNotChickenedOut
         */
        public boolean isFeatureNotChickenedOut(Context context, String name) {
            return DeviceConfigUtils.isTetheringFeatureNotChickenedOut(context, name);
        }

        /**
         * Get the BpfNetMaps implementation to use in ConnectivityService.
         * @param netd a netd binder
         * @return BpfNetMaps implementation.
         */
        public BpfNetMaps getBpfNetMaps(Context context, INetd netd) {
            return new BpfNetMaps(context, netd);
        }

        /**
         * @see ClatCoordinator
         */
        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        public ClatCoordinator getClatCoordinator(INetd netd) {
            return new ClatCoordinator(
                new ClatCoordinator.Dependencies() {
                    @NonNull
                    public INetd getNetd() {
                        return netd;
                    }
                });
        }

        /**
         * Wraps {@link TcUtils#tcFilterAddDevIngressPolice}
         */
        public void enableIngressRateLimit(String iface, long rateInBytesPerSecond) {
            final InterfaceParams params = InterfaceParams.getByName(iface);
            if (params == null) {
                // the interface might have disappeared.
                logw("Failed to get interface params for interface " + iface);
                return;
            }
            try {
                // converting rateInBytesPerSecond from long to int is safe here because the
                // setting's range is limited to INT_MAX.
                // TODO: add long/uint64 support to tcFilterAddDevIngressPolice.
                Log.i(TAG,
                        "enableIngressRateLimit on " + iface + ": " + rateInBytesPerSecond + "B/s");
                TcUtils.tcFilterAddDevIngressPolice(params.index, TC_PRIO_POLICE, (short) ETH_P_ALL,
                        (int) rateInBytesPerSecond, TC_POLICE_BPF_PROG_PATH);
            } catch (IOException e) {
                loge("TcUtils.tcFilterAddDevIngressPolice(ifaceIndex=" + params.index
                        + ", PRIO_POLICE, ETH_P_ALL, rateInBytesPerSecond="
                        + rateInBytesPerSecond + ", bpfProgPath=" + TC_POLICE_BPF_PROG_PATH
                        + ") failure: ", e);
            }
        }

        /**
         * Wraps {@link TcUtils#tcFilterDelDev}
         */
        public void disableIngressRateLimit(String iface) {
            final InterfaceParams params = InterfaceParams.getByName(iface);
            if (params == null) {
                // the interface might have disappeared.
                logw("Failed to get interface params for interface " + iface);
                return;
            }
            try {
                Log.i(TAG,
                        "disableIngressRateLimit on " + iface);
                TcUtils.tcFilterDelDev(params.index, true, TC_PRIO_POLICE, (short) ETH_P_ALL);
            } catch (IOException e) {
                loge("TcUtils.tcFilterDelDev(ifaceIndex=" + params.index
                        + ", ingress=true, PRIO_POLICE, ETH_P_ALL) failure: ", e);
            }
        }

        /**
         * Get BPF program Id from CGROUP. See {@link BpfUtils#getProgramId}.
         */
        public int getBpfProgramId(final int attachType)
                throws IOException {
            return BpfUtils.getProgramId(attachType);
        }

        /**
         * Wraps {@link BroadcastOptionsShimImpl#newInstance(BroadcastOptions)}
         */
        // TODO: when available in all active branches:
        //  @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        @RequiresApi(Build.VERSION_CODES.CUR_DEVELOPMENT)
        public BroadcastOptionsShim makeBroadcastOptionsShim(BroadcastOptions options) {
            return BroadcastOptionsShimImpl.newInstance(options);
        }

        /**
         * Wrapper method for
         * {@link android.app.compat.CompatChanges#isChangeEnabled(long, String, UserHandle)}.
         *
         * @param changeId    The ID of the compatibility change in question.
         * @param packageName The package name of the app in question.
         * @param user        The user that the operation is done for.
         * @return {@code true} if the change is enabled for the specified package.
         */
        public boolean isChangeEnabled(long changeId, @NonNull final String packageName,
                @NonNull final UserHandle user) {
            return CompatChanges.isChangeEnabled(changeId, packageName, user);
        }

        /**
         * As above but with a UID.
         * @see CompatChanges#isChangeEnabled(long, int)
         */
        public boolean isChangeEnabled(final long changeId, final int uid) {
            return CompatChanges.isChangeEnabled(changeId, uid);
        }

        /**
         * Call {@link InetDiagMessage#destroyLiveTcpSockets(Set, Set)}
         *
         * @param ranges target uid ranges
         * @param exemptUids uids to skip close socket
         */
        public void destroyLiveTcpSockets(@NonNull final Set<Range<Integer>> ranges,
                @NonNull final Set<Integer> exemptUids)
                throws SocketException, InterruptedIOException, ErrnoException {
            InetDiagMessage.destroyLiveTcpSockets(ranges, exemptUids);
        }

        /**
         * Call {@link InetDiagMessage#destroyLiveTcpSocketsByOwnerUids(Set)}
         *
         * @param ownerUids target uids to close sockets
         */
        public void destroyLiveTcpSocketsByOwnerUids(final Set<Integer> ownerUids)
                throws SocketException, InterruptedIOException, ErrnoException {
            InetDiagMessage.destroyLiveTcpSocketsByOwnerUids(ownerUids);
        }

        /**
         * Schedule the evaluation timeout.
         *
         * When a network connects, it's "not evaluated" yet. Detection events cause the network
         * to be "evaluated" (typically, validation or detection of a captive portal). If none
         * of these events happen, this time will run out, after which the network is considered
         * "evaluated" even if nothing happened to it. Notionally that means the system gave up
         * on this network and considers it won't provide connectivity. In particular, that means
         * it's when the system prefers it to cell if it's wifi and configuration says it should
         * prefer bad wifi to cell.
         */
        public void scheduleEvaluationTimeout(@NonNull Handler handler,
                @NonNull final Network network, final long delayMs) {
            handler.sendMessageDelayed(
                    handler.obtainMessage(EVENT_INITIAL_EVALUATION_TIMEOUT, network), delayMs);
        }
    }

    public ConnectivityService(Context context) {
        this(context, getDnsResolver(context), new IpConnectivityLog(),
                INetd.Stub.asInterface((IBinder) context.getSystemService(Context.NETD_SERVICE)),
                new Dependencies());
    }

    @VisibleForTesting
    protected ConnectivityService(Context context, IDnsResolver dnsresolver,
            IpConnectivityLog logger, INetd netd, Dependencies deps) {
        if (DBG) log("ConnectivityService starting up");

        mDeps = Objects.requireNonNull(deps, "missing Dependencies");
        mFlags = new ConnectivityFlags();
        mSystemProperties = mDeps.getSystemProperties();
        mNetIdManager = mDeps.makeNetIdManager();
        mContext = Objects.requireNonNull(context, "missing Context");
        mResources = deps.getResources(mContext);
        // The legacy PerUidCounter is buggy and throwing exception at count == limit.
        // Pass limit - 1 to maintain backward compatibility.
        // TODO: Remove the workaround.
        mNetworkRequestCounter =
                new RequestInfoPerUidCounter(MAX_NETWORK_REQUESTS_PER_UID - 1);
        mSystemNetworkRequestCounter =
                new RequestInfoPerUidCounter(MAX_NETWORK_REQUESTS_PER_SYSTEM_UID - 1);

        mMetricsLog = logger;
        mNetworkRequestStateStatsMetrics = mDeps.makeNetworkRequestStateStatsMetrics(mContext);
        final NetworkRequest defaultInternetRequest = createDefaultRequest();
        mDefaultRequest = new NetworkRequestInfo(
                Process.myUid(), defaultInternetRequest, null,
                null /* binder */, NetworkCallback.FLAG_INCLUDE_LOCATION_INFO,
                null /* attributionTags */);
        mNetworkRequests.put(defaultInternetRequest, mDefaultRequest);
        mDefaultNetworkRequests.add(mDefaultRequest);
        mNetworkRequestInfoLogs.log("REGISTER " + mDefaultRequest);

        mDefaultMobileDataRequest = createDefaultInternetRequestForTransport(
                NetworkCapabilities.TRANSPORT_CELLULAR, NetworkRequest.Type.BACKGROUND_REQUEST);

        // The default WiFi request is a background request so that apps using WiFi are
        // migrated to a better network (typically ethernet) when one comes up, instead
        // of staying on WiFi forever.
        mDefaultWifiRequest = createDefaultInternetRequestForTransport(
                NetworkCapabilities.TRANSPORT_WIFI, NetworkRequest.Type.BACKGROUND_REQUEST);

        mDefaultVehicleRequest = createAlwaysOnRequestForCapability(
                NetworkCapabilities.NET_CAPABILITY_VEHICLE_INTERNAL,
                NetworkRequest.Type.BACKGROUND_REQUEST);

        mLingerDelayMs = mSystemProperties.getInt(LINGER_DELAY_PROPERTY, DEFAULT_LINGER_DELAY_MS);
        // TODO: Consider making the timer customizable.
        mNascentDelayMs = DEFAULT_NASCENT_DELAY_MS;
        mCellularRadioTimesharingCapable =
                mResources.get().getBoolean(R.bool.config_cellular_radio_timesharing_capable);

        int mark = mResources.get().getInteger(R.integer.config_networkWakeupPacketMark);
        int mask = mResources.get().getInteger(R.integer.config_networkWakeupPacketMask);

        if (SdkLevel.isAtLeastU()) {
            // U+ default value of both mark & mask, this is the top bit of the skb->mark,
            // see //system/netd/include/FwMark.h union Fwmark, field ingress_cpu_wakeup
            final int defaultUMarkMask = 0x80000000;  // u32

            if ((mark == 0) || (mask == 0)) {
                // simply treat unset/disabled as the default U value
                mark = defaultUMarkMask;
                mask = defaultUMarkMask;
            }
            if ((mark != defaultUMarkMask) || (mask != defaultUMarkMask)) {
                // invalid device overlay settings
                throw new IllegalArgumentException(
                        "Bad config_networkWakeupPacketMark/Mask " + mark + "/" + mask);
            }
        }

        mWakeUpMark = mark;
        mWakeUpMask = mask;

        mNetd = netd;
        mBpfNetMaps = mDeps.getBpfNetMaps(mContext, netd);
        mHandlerThread = mDeps.makeHandlerThread("ConnectivityServiceThread");
        mPermissionMonitor =
                new PermissionMonitor(mContext, mNetd, mBpfNetMaps, mHandlerThread);
        mHandlerThread.start();
        mHandler = new InternalHandler(mHandlerThread.getLooper());
        mTrackerHandler = new NetworkStateTrackerHandler(mHandlerThread.getLooper());
        mConnectivityDiagnosticsHandler =
                new ConnectivityDiagnosticsHandler(mHandlerThread.getLooper());

        mReleasePendingIntentDelayMs = Settings.Secure.getInt(context.getContentResolver(),
                ConnectivitySettingsManager.CONNECTIVITY_RELEASE_PENDING_INTENT_DELAY_MS, 5_000);

        mStatsManager = mContext.getSystemService(NetworkStatsManager.class);
        mPolicyManager = mContext.getSystemService(NetworkPolicyManager.class);
        mDnsResolver = Objects.requireNonNull(dnsresolver, "missing IDnsResolver");
        mProxyTracker = mDeps.makeProxyTracker(mContext, mHandler);

        mTelephonyManager = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
        mAppOpsManager = (AppOpsManager) mContext.getSystemService(Context.APP_OPS_SERVICE);
        mLocationPermissionChecker = mDeps.makeLocationPermissionChecker(mContext);
        mRequestRestrictedWifiEnabled = mDeps.isAtLeastU()
                && mDeps.isFeatureEnabled(context, REQUEST_RESTRICTED_WIFI);
        mCarrierPrivilegeAuthenticator = mDeps.makeCarrierPrivilegeAuthenticator(
                mContext, mTelephonyManager, mRequestRestrictedWifiEnabled,
                this::onCarrierPrivilegesLost);

        if (mDeps.isAtLeastU()
                && mDeps
                .isFeatureNotChickenedOut(mContext, ALLOW_SATALLITE_NETWORK_FALLBACK)) {
            mSatelliteAccessController = mDeps.makeSatelliteAccessController(
                    mContext, this::updateSatelliteNetworkPreferenceUids, mHandler);
        } else {
            mSatelliteAccessController = null;
        }

        // Enable the OEM denylist chain. {@see mNetIdToDisallowedUids}
        try {
            mBpfNetMaps.setChildChain(ConnectivityManager.FIREWALL_CHAIN_OEM_DENY_1, true);
        } catch (ServiceSpecificException e) {
            logwtf("Could not enable fw_oem_deny_1 chain", e);
        }

        // To ensure uid state is synchronized with Network Policy, register for
        // NetworkPolicyManagerService events must happen prior to NetworkPolicyManagerService
        // reading existing policy from disk.
        mPolicyManager.registerNetworkPolicyCallback(null, mPolicyCallback);

        final PowerManager powerManager = (PowerManager) context.getSystemService(
                Context.POWER_SERVICE);
        mNetTransitionWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        mPendingIntentWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);

        mLegacyTypeTracker.loadSupportedTypes(mContext, mTelephonyManager);
        mProtectedNetworks = new ArrayList<>();
        int[] protectedNetworks = mResources.get().getIntArray(R.array.config_protectedNetworks);
        for (int p : protectedNetworks) {
            if (mLegacyTypeTracker.isTypeSupported(p) && !mProtectedNetworks.contains(p)) {
                mProtectedNetworks.add(p);
            } else {
                if (DBG) loge("Ignoring protectedNetwork " + p);
            }
        }

        mUserManager = (UserManager) context.getSystemService(Context.USER_SERVICE);

        mUserAllContext = mContext.createContextAsUser(UserHandle.ALL, 0 /* flags */);
        // Listen for user add/removes to inform PermissionMonitor.
        // Should run on mHandler to avoid any races.
        final IntentFilter userIntentFilter = new IntentFilter();
        userIntentFilter.addAction(Intent.ACTION_USER_ADDED);
        userIntentFilter.addAction(Intent.ACTION_USER_REMOVED);
        mUserAllContext.registerReceiver(mUserIntentReceiver, userIntentFilter,
                null /* broadcastPermission */, mHandler);

        // Listen to package add/removes for netd
        final IntentFilter packageIntentFilter = new IntentFilter();
        packageIntentFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        packageIntentFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        packageIntentFilter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        packageIntentFilter.addDataScheme("package");
        mUserAllContext.registerReceiver(mPackageIntentReceiver, packageIntentFilter,
                null /* broadcastPermission */, mHandler);

        // This is needed for pre-V devices to propagate the data saver status
        // to the BPF map. This isn't supported before Android T because BPF maps are
        // unsupported, and it's also unnecessary on Android V and later versions,
        // as the platform code handles data saver bit updates. Additionally, checking
        // the initial data saver status here is superfluous because the intent won't
        // be sent until the system is ready.
        if (mDeps.isAtLeastT() && !mDeps.isAtLeastV()) {
            final IntentFilter dataSaverIntentFilter =
                    new IntentFilter(ACTION_RESTRICT_BACKGROUND_CHANGED);
            mUserAllContext.registerReceiver(mDataSaverReceiver, dataSaverIntentFilter,
                    null /* broadcastPermission */, mHandler);
        }

        // TrackMultiNetworkActivities feature should be enabled by trunk stable flag.
        // But reading the trunk stable flags from mainline modules is not supported yet.
        // So enabling this feature on V+ release.
        mTrackMultiNetworkActivities = mDeps.isAtLeastV();
        mNetworkActivityTracker = new LegacyNetworkActivityTracker(mContext, mNetd, mHandler,
                mTrackMultiNetworkActivities);

        final NetdCallback netdCallback = new NetdCallback();
        try {
            mNetd.registerUnsolicitedEventListener(netdCallback);
        } catch (RemoteException | ServiceSpecificException e) {
            loge("Error registering event listener :" + e);
        }

        mSettingsObserver = new SettingsObserver(mContext, mHandler);
        registerSettingsCallbacks();

        mKeepaliveTracker = mDeps.makeAutomaticOnOffKeepaliveTracker(mContext, mHandler);
        mNotifier = new NetworkNotificationManager(mContext, mTelephonyManager);
        mQosCallbackTracker = new QosCallbackTracker(mHandler, mNetworkRequestCounter);

        final int dailyLimit = Settings.Global.getInt(mContext.getContentResolver(),
                ConnectivitySettingsManager.NETWORK_SWITCH_NOTIFICATION_DAILY_LIMIT,
                LingerMonitor.DEFAULT_NOTIFICATION_DAILY_LIMIT);
        final long rateLimit = Settings.Global.getLong(mContext.getContentResolver(),
                ConnectivitySettingsManager.NETWORK_SWITCH_NOTIFICATION_RATE_LIMIT_MILLIS,
                LingerMonitor.DEFAULT_NOTIFICATION_RATE_LIMIT_MILLIS);
        mLingerMonitor = new LingerMonitor(mContext, mNotifier, dailyLimit, rateLimit);

        mMultinetworkPolicyTracker = mDeps.makeMultinetworkPolicyTracker(
                mContext, mHandler, () -> updateAvoidBadWifi());
        mNetworkRanker =
                new NetworkRanker(new NetworkRanker.Configuration(activelyPreferBadWifi()));

        mMultinetworkPolicyTracker.start();

        mDnsManager = new DnsManager(mContext, mDnsResolver);
        registerPrivateDnsSettingsCallbacks();

        // This NAI is a sentinel used to offer no service to apps that are on a multi-layer
        // request that doesn't allow fallback to the default network. It should never be visible
        // to apps. As such, it's not in the list of NAIs and doesn't need many of the normal
        // arguments like the handler or the DnsResolver.
        // TODO : remove this ; it is probably better handled with a sentinel request.
        mNoServiceNetwork = new NetworkAgentInfo(null,
                new Network(INetd.UNREACHABLE_NET_ID),
                new NetworkInfo(TYPE_NONE, 0, "", ""),
                new LinkProperties(), new NetworkCapabilities(), null /* localNetworkConfig */,
                new NetworkScore.Builder().setLegacyInt(0).build(), mContext, null,
                new NetworkAgentConfig(), this, null, null, 0, INVALID_UID,
                mLingerDelayMs, mQosCallbackTracker, mDeps);

        try {
            // DscpPolicyTracker cannot run on S because on S the tethering module can only load
            // BPF programs/maps into /sys/fs/tethering/bpf, which the system server cannot access.
            // Even if it could, running on S would at least require mocking out the BPF map,
            // otherwise the unit tests will fail on pre-T devices where the seccomp filter blocks
            // the bpf syscall. http://aosp/1907693
            if (mDeps.isAtLeastT()) {
                mDscpPolicyTracker = new DscpPolicyTracker();
            }
        } catch (ErrnoException e) {
            loge("Unable to create DscpPolicyTracker");
        }

        mIngressRateLimit = ConnectivitySettingsManager.getIngressRateLimitInBytesPerSecond(
                mContext);

        if (mDeps.isAtLeastT()) {
            mCdmps = new CompanionDeviceManagerProxyService(context);
        } else {
            mCdmps = null;
        }

        mRoutingCoordinatorService = new RoutingCoordinatorService(netd);
        mMulticastRoutingCoordinatorService =
                mDeps.makeMulticastRoutingCoordinatorService(mHandler);

        mDestroyFrozenSockets = mDeps.isAtLeastV() || (mDeps.isAtLeastU()
                && mDeps.isFeatureEnabled(context, KEY_DESTROY_FROZEN_SOCKETS_VERSION));
        mDelayDestroyFrozenSockets = mDeps.isAtLeastU()
                && mDeps.isFeatureEnabled(context, DELAY_DESTROY_FROZEN_SOCKETS_VERSION);
        mAllowSysUiConnectivityReports = mDeps.isFeatureNotChickenedOut(
                mContext, ALLOW_SYSUI_CONNECTIVITY_REPORTS);
        if (mDestroyFrozenSockets) {
            final UidFrozenStateChangedCallback frozenStateChangedCallback =
                    new UidFrozenStateChangedCallback() {
                @Override
                public void onUidFrozenStateChanged(int[] uids, int[] frozenStates) {
                    if (uids.length != frozenStates.length) {
                        Log.wtf(TAG, "uids has length " + uids.length
                                + " but frozenStates has length " + frozenStates.length);
                        return;
                    }

                    final UidFrozenStateChangedArgs args =
                            new UidFrozenStateChangedArgs(uids, frozenStates);

                    mHandler.sendMessage(
                            mHandler.obtainMessage(EVENT_UID_FROZEN_STATE_CHANGED, args));
                }
            };

            final ActivityManager activityManager =
                    mContext.getSystemService(ActivityManager.class);
            activityManager.registerUidFrozenStateChangedCallback(
                    (Runnable r) -> r.run(), frozenStateChangedCallback);
        }

        if (mDeps.isFeatureNotChickenedOut(mContext, LOG_BPF_RC)) {
            mHandler.post(BpfLoaderRcUtils::checkBpfLoaderRc);
        }
        mIngressToVpnAddressFiltering = mDeps.isAtLeastT();
    }

    /**
     * Check whether or not the device supports Ethernet transport.
     */
    public static boolean deviceSupportsEthernet(final Context context) {
        final PackageManager pm = context.getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_ETHERNET)
                || pm.hasSystemFeature(PackageManager.FEATURE_USB_HOST);
    }

    private static NetworkCapabilities createDefaultNetworkCapabilitiesForUid(int uid) {
        return createDefaultNetworkCapabilitiesForUidRangeSet(Collections.singleton(
                new UidRange(uid, uid)));
    }

    private static NetworkCapabilities createDefaultNetworkCapabilitiesForUidRangeSet(
            @NonNull final Set<UidRange> uidRangeSet) {
        final NetworkCapabilities netCap = new NetworkCapabilities();
        netCap.addCapability(NET_CAPABILITY_INTERNET);
        netCap.addCapability(NET_CAPABILITY_NOT_VCN_MANAGED);
        netCap.removeCapability(NET_CAPABILITY_NOT_VPN);
        netCap.setUids(UidRange.toIntRanges(uidRangeSet));
        return netCap;
    }

    private NetworkRequest createDefaultRequest() {
        return createDefaultInternetRequestForTransport(
                TYPE_NONE, NetworkRequest.Type.REQUEST);
    }

    private NetworkRequest createVpnRequest() {
        final NetworkCapabilities netCap = new NetworkCapabilities.Builder()
                .withoutDefaultCapabilities()
                .addTransportType(TRANSPORT_VPN)
                .addCapability(NET_CAPABILITY_NOT_VCN_MANAGED)
                .addCapability(NET_CAPABILITY_NOT_RESTRICTED)
                .build();
        netCap.setRequestorUidAndPackageName(Process.myUid(), mContext.getPackageName());
        return createNetworkRequest(NetworkRequest.Type.REQUEST, netCap);
    }

    private NetworkRequest createDefaultInternetRequestForTransport(
            int transportType, NetworkRequest.Type type) {
        final NetworkCapabilities netCap = new NetworkCapabilities();
        netCap.addCapability(NET_CAPABILITY_INTERNET);
        netCap.addCapability(NET_CAPABILITY_NOT_VCN_MANAGED);
        netCap.setRequestorUidAndPackageName(Process.myUid(), mContext.getPackageName());
        if (transportType > TYPE_NONE) {
            netCap.addTransportType(transportType);
        }
        return createNetworkRequest(type, netCap);
    }

    private NetworkRequest createNetworkRequest(
            NetworkRequest.Type type, NetworkCapabilities netCap) {
        return new NetworkRequest(netCap, TYPE_NONE, nextNetworkRequestId(), type);
    }

    private NetworkRequest createAlwaysOnRequestForCapability(int capability,
            NetworkRequest.Type type) {
        final NetworkCapabilities netCap = new NetworkCapabilities();
        netCap.clearAll();
        netCap.addCapability(capability);
        netCap.setRequestorUidAndPackageName(Process.myUid(), mContext.getPackageName());
        return new NetworkRequest(netCap, TYPE_NONE, nextNetworkRequestId(), type);
    }

    // Used only for testing.
    // TODO: Delete this and either:
    // 1. Give FakeSettingsProvider the ability to send settings change notifications (requires
    //    changing ContentResolver to make registerContentObserver non-final).
    // 2. Give FakeSettingsProvider an alternative notification mechanism and have the test use it
    //    by subclassing SettingsObserver.
    @VisibleForTesting
    void updateAlwaysOnNetworks() {
        mHandler.sendEmptyMessage(EVENT_CONFIGURE_ALWAYS_ON_NETWORKS);
    }

    // See FakeSettingsProvider comment above.
    @VisibleForTesting
    void updatePrivateDnsSettings() {
        mHandler.sendEmptyMessage(EVENT_PRIVATE_DNS_SETTINGS_CHANGED);
    }

    @VisibleForTesting
    void updateMobileDataPreferredUids() {
        mHandler.sendEmptyMessage(EVENT_MOBILE_DATA_PREFERRED_UIDS_CHANGED);
    }

    @VisibleForTesting
    void updateIngressRateLimit() {
        mHandler.sendEmptyMessage(EVENT_INGRESS_RATE_LIMIT_CHANGED);
    }

    @VisibleForTesting
    void simulateUpdateProxyInfo(@Nullable final Network network,
            @NonNull final ProxyInfo proxyInfo) {
        Message.obtain(mHandler, EVENT_PAC_PROXY_HAS_CHANGED,
                new Pair<>(network, proxyInfo)).sendToTarget();
    }

    /**
     * Called when satellite network fallback uids at {@link SatelliteAccessController}
     * cache was updated based on {@link
     * android.app.role.OnRoleHoldersChangedListener#onRoleHoldersChanged(String, UserHandle)},
     * to create multilayer request with preference order
     * {@link #PREFERENCE_ORDER_SATELLITE_FALLBACK} there on.
     *
     */
    private void updateSatelliteNetworkPreferenceUids(Set<Integer> satelliteNetworkFallbackUids) {
        handleSetSatelliteNetworkPreference(satelliteNetworkFallbackUids);
    }

    private void handleAlwaysOnNetworkRequest(
            NetworkRequest networkRequest, String settingName, boolean defaultValue) {
        final boolean enable = toBool(Settings.Global.getInt(
                mContext.getContentResolver(), settingName, encodeBool(defaultValue)));
        handleAlwaysOnNetworkRequest(networkRequest, enable);
    }

    private void handleAlwaysOnNetworkRequest(NetworkRequest networkRequest, boolean enable) {
        final boolean isEnabled = (mNetworkRequests.get(networkRequest) != null);
        if (enable == isEnabled) {
            return;  // Nothing to do.
        }

        if (enable) {
            handleRegisterNetworkRequest(new NetworkRequestInfo(
                    Process.myUid(), networkRequest, null /* messenger */, null /* binder */,
                    NetworkCallback.FLAG_INCLUDE_LOCATION_INFO,
                    null /* attributionTags */));
        } else {
            handleReleaseNetworkRequest(networkRequest, Process.SYSTEM_UID,
                    /* callOnUnavailable */ false);
        }
    }

    private void handleConfigureAlwaysOnNetworks() {
        handleAlwaysOnNetworkRequest(mDefaultMobileDataRequest,
                ConnectivitySettingsManager.MOBILE_DATA_ALWAYS_ON, true /* defaultValue */);
        handleAlwaysOnNetworkRequest(mDefaultWifiRequest,
                ConnectivitySettingsManager.WIFI_ALWAYS_REQUESTED, false /* defaultValue */);
        final boolean vehicleAlwaysRequested = mResources.get().getBoolean(
                R.bool.config_vehicleInternalNetworkAlwaysRequested);
        handleAlwaysOnNetworkRequest(mDefaultVehicleRequest, vehicleAlwaysRequested);
    }

    // Note that registering observer for setting do not get initial callback when registering,
    // callers must fetch the initial value of the setting themselves if needed.
    private void registerSettingsCallbacks() {
        // Watch for global HTTP proxy changes.
        mSettingsObserver.observe(
                Settings.Global.getUriFor(Settings.Global.HTTP_PROXY),
                EVENT_APPLY_GLOBAL_HTTP_PROXY);

        // Watch for whether to keep mobile data always on.
        mSettingsObserver.observe(
                Settings.Global.getUriFor(ConnectivitySettingsManager.MOBILE_DATA_ALWAYS_ON),
                EVENT_CONFIGURE_ALWAYS_ON_NETWORKS);

        // Watch for whether to keep wifi always on.
        mSettingsObserver.observe(
                Settings.Global.getUriFor(ConnectivitySettingsManager.WIFI_ALWAYS_REQUESTED),
                EVENT_CONFIGURE_ALWAYS_ON_NETWORKS);

        // Watch for mobile data preferred uids changes.
        mSettingsObserver.observe(
                Settings.Secure.getUriFor(ConnectivitySettingsManager.MOBILE_DATA_PREFERRED_UIDS),
                EVENT_MOBILE_DATA_PREFERRED_UIDS_CHANGED);

        // Watch for ingress rate limit changes.
        mSettingsObserver.observe(
                Settings.Global.getUriFor(
                        ConnectivitySettingsManager.INGRESS_RATE_LIMIT_BYTES_PER_SECOND),
                EVENT_INGRESS_RATE_LIMIT_CHANGED);
    }

    private void registerPrivateDnsSettingsCallbacks() {
        for (Uri uri : DnsManager.getPrivateDnsSettingsUris()) {
            mSettingsObserver.observe(uri, EVENT_PRIVATE_DNS_SETTINGS_CHANGED);
        }
    }

    private synchronized int nextNetworkRequestId() {
        // TODO: Consider handle wrapping and exclude {@link NetworkRequest#REQUEST_ID_NONE} if
        //  doing that.
        return mNextNetworkRequestId++;
    }

    @VisibleForTesting
    @Nullable
    protected NetworkAgentInfo getNetworkAgentInfoForNetwork(Network network) {
        if (network == null) {
            return null;
        }
        return getNetworkAgentInfoForNetId(network.getNetId());
    }

    private NetworkAgentInfo getNetworkAgentInfoForNetId(int netId) {
        synchronized (mNetworkForNetId) {
            return mNetworkForNetId.get(netId);
        }
    }

    // TODO: determine what to do when more than one VPN applies to |uid|.
    @Nullable
    private NetworkAgentInfo getVpnForUid(int uid) {
        synchronized (mNetworkForNetId) {
            for (int i = 0; i < mNetworkForNetId.size(); i++) {
                final NetworkAgentInfo nai = mNetworkForNetId.valueAt(i);
                if (nai.isVPN() && nai.everConnected()
                        && nai.networkCapabilities.appliesToUid(uid)) {
                    return nai;
                }
            }
        }
        return null;
    }

    @Nullable
    private NetworkAgentInfo getConnectedOrConnectingVpnForUid(int uid) {
        synchronized (mNetworkForNetId) {
            for (int i = 0; i < mNetworkForNetId.size(); i++) {
                final NetworkAgentInfo nai = mNetworkForNetId.valueAt(i);
                if (nai.isVPN()
                        && (nai.everConnected() || nai.networkInfo.isConnectedOrConnecting())
                        && nai.networkCapabilities.appliesToUid(uid)) {
                    return nai;
                }
            }
        }
        return null;
    }

    @Nullable
    private Network[] getVpnUnderlyingNetworks(int uid) {
        if (mLockdownEnabled) return null;
        final NetworkAgentInfo nai = getVpnForUid(uid);
        if (nai != null) return nai.declaredUnderlyingNetworks;
        return null;
    }

    private NetworkAgentInfo getNetworkAgentInfoForUid(int uid) {
        NetworkAgentInfo nai = getDefaultNetworkForUid(uid);

        final Network[] networks = getVpnUnderlyingNetworks(uid);
        if (networks != null) {
            // getUnderlyingNetworks() returns:
            // null => there was no VPN, or the VPN didn't specify anything, so we use the default.
            // empty array => the VPN explicitly said "no default network".
            // non-empty array => the VPN specified one or more default networks; we use the
            //                    first one.
            if (networks.length > 0) {
                nai = getNetworkAgentInfoForNetwork(networks[0]);
            } else {
                nai = null;
            }
        }
        return nai;
    }

    /**
     * Check if UID should be blocked from using the specified network.
     */
    private boolean isNetworkWithCapabilitiesBlocked(@Nullable final NetworkCapabilities nc,
            final int uid, final boolean ignoreBlocked) {
        // Networks aren't blocked when ignoring blocked status
        if (ignoreBlocked) {
            return false;
        }
        if (isUidCurrentlyDisallowedByPolicy(uid)) return true;
        if (isUidBlockedByVpn(uid, mVpnBlockedUidRanges)) return true;
        final long ident = Binder.clearCallingIdentity();
        try {
            final boolean metered = nc == null ? true : nc.isMetered();
            return mPolicyManager.isUidNetworkingBlocked(uid, metered);
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    /** Check if UID is currently disallowed general network access based on policies. */
    private boolean isUidCurrentlyDisallowedByPolicy(int uid) {
        return mLastDisallowedUidsDenylist.contains(uid);
    }

    private void maybeLogBlockedNetworkInfo(NetworkInfo ni, int uid) {
        if (ni == null || !LOGD_BLOCKED_NETWORKINFO) {
            return;
        }
        final boolean blocked;
        synchronized (mBlockedAppUids) {
            if (ni.getDetailedState() == DetailedState.BLOCKED && mBlockedAppUids.add(uid)) {
                blocked = true;
            } else if (ni.isConnected() && mBlockedAppUids.remove(uid)) {
                blocked = false;
            } else {
                return;
            }
        }
        String action = blocked ? "BLOCKED" : "UNBLOCKED";
        log(String.format("Returning %s NetworkInfo to uid=%d", action, uid));
        mNetworkInfoBlockingLogs.log(action + " " + uid);
    }

    private void maybeLogBlockedStatusChanged(NetworkRequestInfo nri, Network net, int blocked) {
        if (nri == null || net == null || !LOGD_BLOCKED_NETWORKINFO) {
            return;
        }
        final String action = (blocked != 0) ? "BLOCKED" : "UNBLOCKED";
        final int requestId = nri.getActiveRequest() != null
                ? nri.getActiveRequest().requestId : nri.mRequests.get(0).requestId;
        mNetworkInfoBlockingLogs.log(String.format(
                "%s %d(%d) on netId %d: %s", action, nri.mAsUid, requestId, net.getNetId(),
                Integer.toHexString(blocked)));
    }

    /**
     * Apply any relevant filters to the specified {@link NetworkInfo} for the given UID. For
     * example, this may mark the network as {@link DetailedState#BLOCKED} based
     * on {@link #isNetworkWithCapabilitiesBlocked}.
     */
    @NonNull
    private NetworkInfo filterNetworkInfo(@NonNull NetworkInfo networkInfo, int type,
            @NonNull NetworkCapabilities nc, int uid, boolean ignoreBlocked) {
        final NetworkInfo filtered = new NetworkInfo(networkInfo);
        // Many legacy types (e.g,. TYPE_MOBILE_HIPRI) are not actually a property of the network
        // but only exists if an app asks about them or requests them. Ensure the requesting app
        // gets the type it asks for.
        filtered.setType(type);
        if (isNetworkWithCapabilitiesBlocked(nc, uid, ignoreBlocked)) {
            filtered.setDetailedState(DetailedState.BLOCKED, null /* reason */,
                    null /* extraInfo */);
        }
        filterForLegacyLockdown(filtered);
        return filtered;
    }

    private NetworkInfo getFilteredNetworkInfo(NetworkAgentInfo nai, int uid,
            boolean ignoreBlocked) {
        return filterNetworkInfo(nai.networkInfo, nai.networkInfo.getType(),
                nai.networkCapabilities, uid, ignoreBlocked);
    }

    /**
     * Return NetworkInfo for the active (i.e., connected) network interface.
     * It is assumed that at most one network is active at a time. If more
     * than one is active, it is indeterminate which will be returned.
     * @return the info for the active network, or {@code null} if none is
     * active
     */
    @Override
    @Nullable
    public NetworkInfo getActiveNetworkInfo() {
        enforceAccessPermission();
        final int uid = mDeps.getCallingUid();
        final NetworkAgentInfo nai = getNetworkAgentInfoForUid(uid);
        if (nai == null) return null;
        final NetworkInfo networkInfo = getFilteredNetworkInfo(nai, uid, false);
        maybeLogBlockedNetworkInfo(networkInfo, uid);
        return networkInfo;
    }

    @Override
    @Nullable
    public Network getActiveNetwork() {
        enforceAccessPermission();
        return getActiveNetworkForUidInternal(mDeps.getCallingUid(), false);
    }

    @Override
    @Nullable
    public Network getActiveNetworkForUid(int uid, boolean ignoreBlocked) {
        enforceNetworkStackPermission(mContext);
        return getActiveNetworkForUidInternal(uid, ignoreBlocked);
    }

    @Nullable
    private Network getActiveNetworkForUidInternal(final int uid, boolean ignoreBlocked) {
        return getActiveNetworkForUidInternal(getVpnForUid(uid), uid, ignoreBlocked);
    }

    private Network getActiveNetworkOrConnectingVpnForUidInternal(final int uid,
            boolean ignoreBlocked) {
        return getActiveNetworkForUidInternal(
                getConnectedOrConnectingVpnForUid(uid), uid, ignoreBlocked);
    }

    private Network getActiveNetworkForUidInternal(final NetworkAgentInfo vpnNai,
            final int uid, boolean ignoreBlocked) {
        if (vpnNai != null) {
            final NetworkCapabilities requiredCaps = createDefaultNetworkCapabilitiesForUid(uid);
            if (requiredCaps.satisfiedByNetworkCapabilities(vpnNai.networkCapabilities)) {
                return vpnNai.network;
            }
        }

        NetworkAgentInfo nai = getDefaultNetworkForUid(uid);
        if (nai == null || isNetworkWithCapabilitiesBlocked(nai.networkCapabilities, uid,
                ignoreBlocked)) {
            return null;
        }
        return nai.network;
    }

    @Override
    @Nullable
    public NetworkInfo getActiveNetworkInfoForUid(int uid, boolean ignoreBlocked) {
        enforceNetworkStackPermission(mContext);
        final NetworkAgentInfo nai = getNetworkAgentInfoForUid(uid);
        if (nai == null) return null;
        return getFilteredNetworkInfo(nai, uid, ignoreBlocked);
    }

    /** Returns a NetworkInfo object for a network that doesn't exist. */
    private NetworkInfo makeFakeNetworkInfo(int networkType, int uid) {
        final NetworkInfo info = new NetworkInfo(networkType, 0 /* subtype */,
                getNetworkTypeName(networkType), "" /* subtypeName */);
        info.setIsAvailable(true);
        // For compatibility with legacy code, return BLOCKED instead of DISCONNECTED when
        // background data is restricted.
        final NetworkCapabilities nc = new NetworkCapabilities();  // Metered.
        final DetailedState state = isNetworkWithCapabilitiesBlocked(nc, uid, false)
                ? DetailedState.BLOCKED
                : DetailedState.DISCONNECTED;
        info.setDetailedState(state, null /* reason */, null /* extraInfo */);
        filterForLegacyLockdown(info);
        return info;
    }

    private NetworkInfo getFilteredNetworkInfoForType(int networkType, int uid) {
        if (!mLegacyTypeTracker.isTypeSupported(networkType)) {
            return null;
        }
        final NetworkAgentInfo nai = mLegacyTypeTracker.getNetworkForType(networkType);
        if (nai == null) {
            return makeFakeNetworkInfo(networkType, uid);
        }
        return filterNetworkInfo(nai.networkInfo, networkType, nai.networkCapabilities, uid,
                false);
    }

    @Override
    @Nullable
    public NetworkInfo getNetworkInfo(int networkType) {
        enforceAccessPermission();
        final int uid = mDeps.getCallingUid();
        if (getVpnUnderlyingNetworks(uid) != null) {
            // A VPN is active, so we may need to return one of its underlying networks. This
            // information is not available in LegacyTypeTracker, so we have to get it from
            // getNetworkAgentInfoForUid.
            final NetworkAgentInfo nai = getNetworkAgentInfoForUid(uid);
            if (nai == null) return null;
            final NetworkInfo networkInfo = getFilteredNetworkInfo(nai, uid, false);
            if (networkInfo.getType() == networkType) {
                return networkInfo;
            }
        }
        return getFilteredNetworkInfoForType(networkType, uid);
    }

    @Override
    @Nullable
    public NetworkInfo getNetworkInfoForUid(Network network, int uid, boolean ignoreBlocked) {
        enforceAccessPermission();
        final NetworkAgentInfo nai = getNetworkAgentInfoForNetwork(network);
        if (nai == null) return null;
        return getFilteredNetworkInfo(nai, uid, ignoreBlocked);
    }

    @Override
    public NetworkInfo[] getAllNetworkInfo() {
        enforceAccessPermission();
        final ArrayList<NetworkInfo> result = new ArrayList<>();
        for (int networkType = 0; networkType <= ConnectivityManager.MAX_NETWORK_TYPE;
                networkType++) {
            NetworkInfo info = getNetworkInfo(networkType);
            if (info != null) {
                result.add(info);
            }
        }
        return result.toArray(new NetworkInfo[result.size()]);
    }

    @Override
    @Nullable
    public Network getNetworkForType(int networkType) {
        enforceAccessPermission();
        if (!mLegacyTypeTracker.isTypeSupported(networkType)) {
            return null;
        }
        final NetworkAgentInfo nai = mLegacyTypeTracker.getNetworkForType(networkType);
        if (nai == null) {
            return null;
        }
        final int uid = mDeps.getCallingUid();
        if (isNetworkWithCapabilitiesBlocked(nai.networkCapabilities, uid, false)) {
            return null;
        }
        return nai.network;
    }

    @Override
    @NonNull
    public Network[] getAllNetworks() {
        enforceAccessPermission();
        synchronized (mNetworkForNetId) {
            final Network[] result = new Network[mNetworkForNetId.size()];
            for (int i = 0; i < mNetworkForNetId.size(); i++) {
                result[i] = mNetworkForNetId.valueAt(i).network;
            }
            return result;
        }
    }

    @Override
    public NetworkCapabilities[] getDefaultNetworkCapabilitiesForUser(
                int userId, String callingPackageName, @Nullable String callingAttributionTag) {
        // The basic principle is: if an app's traffic could possibly go over a
        // network, without the app doing anything multinetwork-specific,
        // (hence, by "default"), then include that network's capabilities in
        // the array.
        //
        // In the normal case, app traffic only goes over the system's default
        // network connection, so that's the only network returned.
        //
        // With a VPN in force, some app traffic may go into the VPN, and thus
        // over whatever underlying networks the VPN specifies, while other app
        // traffic may go over the system default network (e.g.: a split-tunnel
        // VPN, or an app disallowed by the VPN), so the set of networks
        // returned includes the VPN's underlying networks and the system
        // default.
        enforceAccessPermission();

        HashMap<Network, NetworkCapabilities> result = new HashMap<>();

        for (final NetworkRequestInfo nri : mDefaultNetworkRequests) {
            if (!nri.isBeingSatisfied()) {
                continue;
            }
            final NetworkAgentInfo nai = nri.getSatisfier();
            final NetworkCapabilities nc = getNetworkCapabilitiesInternal(nai);
            if (null != nc
                    && nc.hasCapability(NET_CAPABILITY_NOT_RESTRICTED)
                    && !result.containsKey(nai.network)) {
                result.put(
                        nai.network,
                        createWithLocationInfoSanitizedIfNecessaryWhenParceled(
                                nc, false /* includeLocationSensitiveInfo */,
                                getCallingPid(), mDeps.getCallingUid(), callingPackageName,
                                callingAttributionTag));
            }
        }

        // No need to check mLockdownEnabled. If it's true, getVpnUnderlyingNetworks returns null.
        final Network[] networks = getVpnUnderlyingNetworks(mDeps.getCallingUid());
        if (null != networks) {
            for (final Network network : networks) {
                final NetworkCapabilities nc = getNetworkCapabilitiesInternal(network);
                if (null != nc) {
                    result.put(
                            network,
                            createWithLocationInfoSanitizedIfNecessaryWhenParceled(
                                    nc,
                                    false /* includeLocationSensitiveInfo */,
                                    getCallingPid(), mDeps.getCallingUid(), callingPackageName,
                                    callingAttributionTag));
                }
            }
        }

        NetworkCapabilities[] out = new NetworkCapabilities[result.size()];
        out = result.values().toArray(out);
        return out;
    }

    // Because StatsEvent is not usable in tests (everything inside it is hidden), this
    // method is used to convert a ConnectivityStateSample into a StatsEvent, so that tests
    // can call sampleConnectivityState and make the checks on it.
    @NonNull
    private StatsEvent sampleConnectivityStateToStatsEvent() {
        final ConnectivityStateSample sample = sampleConnectivityState();
        return ConnectivityStatsLog.buildStatsEvent(
                ConnectivityStatsLog.CONNECTIVITY_STATE_SAMPLE,
                sample.getNetworkCountPerTransports().toByteArray(),
                sample.getConnectionDurationPerTransports().toByteArray(),
                sample.getNetworkRequestCount().toByteArray(),
                sample.getNetworks().toByteArray());
    }

    /**
     * Gather and return a snapshot of the current connectivity state, to be used as a sample.
     *
     * This is used for metrics. These snapshots will be sampled and constitute a base for
     * statistics about connectivity state of devices.
     */
    @VisibleForTesting
    @NonNull
    public ConnectivityStateSample sampleConnectivityState() {
        ensureRunningOnConnectivityServiceThread();
        final ConnectivityStateSample.Builder builder = ConnectivityStateSample.newBuilder();
        builder.setNetworkCountPerTransports(sampleNetworkCount(mNetworkAgentInfos));
        builder.setConnectionDurationPerTransports(sampleConnectionDuration(mNetworkAgentInfos));
        builder.setNetworkRequestCount(sampleNetworkRequestCount(mNetworkRequests.values()));
        builder.setNetworks(sampleNetworks(mNetworkAgentInfos));
        return builder.build();
    }

    private static NetworkCountPerTransports sampleNetworkCount(
            @NonNull final ArraySet<NetworkAgentInfo> nais) {
        final SparseIntArray countPerTransports = new SparseIntArray();
        for (final NetworkAgentInfo nai : nais) {
            int transports = (int) nai.networkCapabilities.getTransportTypesInternal();
            countPerTransports.put(transports, 1 + countPerTransports.get(transports, 0));
        }
        final NetworkCountPerTransports.Builder builder = NetworkCountPerTransports.newBuilder();
        for (int i = countPerTransports.size() - 1; i >= 0; --i) {
            final NetworkCountForTransports.Builder c = NetworkCountForTransports.newBuilder();
            c.setTransportTypes(countPerTransports.keyAt(i));
            c.setNetworkCount(countPerTransports.valueAt(i));
            builder.addNetworkCountForTransports(c);
        }
        return builder.build();
    }

    private static ConnectionDurationPerTransports sampleConnectionDuration(
            @NonNull final ArraySet<NetworkAgentInfo> nais) {
        final ConnectionDurationPerTransports.Builder builder =
                ConnectionDurationPerTransports.newBuilder();
        for (final NetworkAgentInfo nai : nais) {
            final ConnectionDurationForTransports.Builder c =
                    ConnectionDurationForTransports.newBuilder();
            c.setTransportTypes((int) nai.networkCapabilities.getTransportTypesInternal());
            final long durationMillis = SystemClock.elapsedRealtime() - nai.getConnectedTime();
            final long millisPerSecond = TimeUnit.SECONDS.toMillis(1);
            // Add millisPerSecond/2 to round up or down to the nearest value
            c.setDurationSec((int) ((durationMillis + millisPerSecond / 2) / millisPerSecond));
            builder.addConnectionDurationForTransports(c);
        }
        return builder.build();
    }

    private static NetworkRequestCount sampleNetworkRequestCount(
            @NonNull final Collection<NetworkRequestInfo> nris) {
        final NetworkRequestCount.Builder builder = NetworkRequestCount.newBuilder();
        final SparseIntArray countPerType = new SparseIntArray();
        for (final NetworkRequestInfo nri : nris) {
            final int type;
            if (Process.SYSTEM_UID == nri.mAsUid) {
                // The request is filed "as" the system, so it's the system on its own behalf.
                type = RequestType.RT_SYSTEM.getNumber();
            } else if (Process.SYSTEM_UID == nri.mUid) {
                // The request is filed by the system as some other app, so it's the system on
                // behalf of an app.
                type = RequestType.RT_SYSTEM_ON_BEHALF_OF_APP.getNumber();
            } else {
                // Not the system, so it's an app requesting on its own behalf.
                type = RequestType.RT_APP.getNumber();
            }
            countPerType.put(type, countPerType.get(type, 0));
        }
        for (int i = countPerType.size() - 1; i >= 0; --i) {
            final RequestCountForType.Builder r = RequestCountForType.newBuilder();
            r.setRequestType(RequestType.forNumber(countPerType.keyAt(i)));
            r.setRequestCount(countPerType.valueAt(i));
            builder.addRequestCountForType(r);
        }
        return builder.build();
    }

    private static NetworkList sampleNetworks(@NonNull final ArraySet<NetworkAgentInfo> nais) {
        final NetworkList.Builder builder = NetworkList.newBuilder();
        for (final NetworkAgentInfo nai : nais) {
            final NetworkCapabilities nc = nai.networkCapabilities;
            final NetworkDescription.Builder d = NetworkDescription.newBuilder();
            d.setTransportTypes((int) nc.getTransportTypesInternal());
            final MeteredState meteredState;
            if (nc.hasCapability(NET_CAPABILITY_TEMPORARILY_NOT_METERED)) {
                meteredState = MeteredState.METERED_TEMPORARILY_UNMETERED;
            } else if (nc.hasCapability(NET_CAPABILITY_NOT_METERED)) {
                meteredState = MeteredState.METERED_NO;
            } else {
                meteredState = MeteredState.METERED_YES;
            }
            d.setMeteredState(meteredState);
            final ValidatedState validatedState;
            if (nc.hasCapability(NET_CAPABILITY_CAPTIVE_PORTAL)) {
                validatedState = ValidatedState.VS_PORTAL;
            } else if (nc.hasCapability(NET_CAPABILITY_PARTIAL_CONNECTIVITY)) {
                validatedState = ValidatedState.VS_PARTIAL;
            } else if (nc.hasCapability(NET_CAPABILITY_VALIDATED)) {
                validatedState = ValidatedState.VS_VALID;
            } else {
                validatedState = ValidatedState.VS_INVALID;
            }
            d.setValidatedState(validatedState);
            d.setScorePolicies(nai.getScore().getPoliciesInternal());
            d.setCapabilities(nc.getCapabilitiesInternal());
            d.setEnterpriseId(nc.getEnterpriseIdsInternal());
            builder.addNetworkDescription(d);
        }
        return builder.build();
    }

    @Override
    public boolean isNetworkSupported(int networkType) {
        enforceAccessPermission();
        return mLegacyTypeTracker.isTypeSupported(networkType);
    }

    /**
     * Return LinkProperties for the active (i.e., connected) default
     * network interface for the calling uid.
     * @return the ip properties for the active network, or {@code null} if
     * none is active
     */
    @Override
    public LinkProperties getActiveLinkProperties() {
        enforceAccessPermission();
        final int uid = mDeps.getCallingUid();
        NetworkAgentInfo nai = getNetworkAgentInfoForUid(uid);
        if (nai == null) return null;
        return linkPropertiesRestrictedForCallerPermissions(nai.linkProperties,
                Binder.getCallingPid(), uid);
    }

    @Override
    public LinkProperties getLinkPropertiesForType(int networkType) {
        enforceAccessPermission();
        NetworkAgentInfo nai = mLegacyTypeTracker.getNetworkForType(networkType);
        final LinkProperties lp = getLinkProperties(nai);
        if (lp == null) return null;
        return linkPropertiesRestrictedForCallerPermissions(
                lp, Binder.getCallingPid(), mDeps.getCallingUid());
    }

    // TODO - this should be ALL networks
    @Override
    public LinkProperties getLinkProperties(Network network) {
        enforceAccessPermission();
        final LinkProperties lp = getLinkProperties(getNetworkAgentInfoForNetwork(network));
        if (lp == null) return null;
        return linkPropertiesRestrictedForCallerPermissions(
                lp, Binder.getCallingPid(), mDeps.getCallingUid());
    }

    @Nullable
    private LinkProperties getLinkProperties(@Nullable NetworkAgentInfo nai) {
        if (nai == null) {
            return null;
        }
        synchronized (nai) {
            return nai.linkProperties;
        }
    }

    @Override
    @Nullable
    public LinkProperties getRedactedLinkPropertiesForPackage(@NonNull LinkProperties lp, int uid,
            @NonNull String packageName, @Nullable String callingAttributionTag) {
        Objects.requireNonNull(packageName);
        Objects.requireNonNull(lp);
        enforceNetworkStackOrSettingsPermission();
        if (!hasAccessPermission(-1 /* pid */, uid)) {
            return null;
        }
        return linkPropertiesRestrictedForCallerPermissions(lp, -1 /* callerPid */, uid);
    }

    private NetworkCapabilities getNetworkCapabilitiesInternal(Network network) {
        return getNetworkCapabilitiesInternal(getNetworkAgentInfoForNetwork(network));
    }

    private NetworkCapabilities getNetworkCapabilitiesInternal(NetworkAgentInfo nai) {
        if (nai == null) return null;
        synchronized (nai) {
            return networkCapabilitiesRestrictedForCallerPermissions(
                    nai.networkCapabilities, Binder.getCallingPid(), mDeps.getCallingUid());
        }
    }

    @Override
    public NetworkCapabilities getNetworkCapabilities(Network network, String callingPackageName,
            @Nullable String callingAttributionTag) {
        mAppOpsManager.checkPackage(mDeps.getCallingUid(), callingPackageName);
        enforceAccessPermission();
        return createWithLocationInfoSanitizedIfNecessaryWhenParceled(
                getNetworkCapabilitiesInternal(network),
                false /* includeLocationSensitiveInfo */,
                getCallingPid(), mDeps.getCallingUid(), callingPackageName, callingAttributionTag);
    }

    @Override
    public NetworkCapabilities getRedactedNetworkCapabilitiesForPackage(
            @NonNull NetworkCapabilities nc, int uid, @NonNull String packageName,
            @Nullable String callingAttributionTag) {
        Objects.requireNonNull(nc);
        Objects.requireNonNull(packageName);
        enforceNetworkStackOrSettingsPermission();
        if (!hasAccessPermission(-1 /* pid */, uid)) {
            return null;
        }
        return createWithLocationInfoSanitizedIfNecessaryWhenParceled(
                networkCapabilitiesRestrictedForCallerPermissions(nc, -1 /* callerPid */, uid),
                true /* includeLocationSensitiveInfo */, -1 /* callingPid */, uid, packageName,
                callingAttributionTag);
    }

    private void redactUnderlyingNetworksForCapabilities(NetworkCapabilities nc, int pid, int uid) {
        if (nc.getUnderlyingNetworks() != null
                && !hasNetworkFactoryOrSettingsPermission(pid, uid)) {
            nc.setUnderlyingNetworks(null);
        }
    }

    private boolean canSeeAllowedUids(final int pid, final int uid, final int netOwnerUid) {
        return Process.SYSTEM_UID == uid
                || hasAnyPermissionOf(mContext, pid, uid,
                        android.Manifest.permission.NETWORK_FACTORY);
    }

    @VisibleForTesting
    NetworkCapabilities networkCapabilitiesRestrictedForCallerPermissions(
            NetworkCapabilities nc, int callerPid, int callerUid) {
        // Note : here it would be nice to check ACCESS_NETWORK_STATE and return null, but
        // this would be expensive (one more permission check every time any NC callback is
        // sent) and possibly dangerous : apps normally can't lose ACCESS_NETWORK_STATE, if
        // it happens for some reason (e.g. the package is uninstalled while CS is trying to
        // send the callback) it would crash the system server with NPE.
        final NetworkCapabilities newNc = new NetworkCapabilities(nc);
        if (!hasSettingsPermission(callerPid, callerUid)) {
            newNc.setUids(null);
            newNc.setSSID(null);
        }
        if (newNc.getNetworkSpecifier() != null) {
            newNc.setNetworkSpecifier(newNc.getNetworkSpecifier().redact());
        }
        if (!hasAnyPermissionOf(mContext, callerPid, callerUid,
                android.Manifest.permission.NETWORK_STACK,
                NetworkStack.PERMISSION_MAINLINE_NETWORK_STACK)) {
            newNc.setAdministratorUids(new int[0]);
        }
        if (!canSeeAllowedUids(callerPid, callerUid, newNc.getOwnerUid())) {
            newNc.setAllowedUids(new ArraySet<>());
            newNc.setSubscriptionIds(Collections.emptySet());
        }
        redactUnderlyingNetworksForCapabilities(newNc, callerPid, callerUid);

        return newNc;
    }

    /**
     * Wrapper used to cache the permission check results performed for the corresponding
     * app. This avoids performing multiple permission checks for different fields in
     * NetworkCapabilities.
     * Note: This wrapper does not support any sort of invalidation and thus must not be
     * persistent or long-lived. It may only be used for the time necessary to
     * compute the redactions required by one particular NetworkCallback or
     * synchronous call.
     */
    private class RedactionPermissionChecker {
        private final int mCallingPid;
        private final int mCallingUid;
        @NonNull private final String mCallingPackageName;
        @Nullable private final String mCallingAttributionTag;

        private Boolean mHasLocationPermission = null;
        private Boolean mHasLocalMacAddressPermission = null;
        private Boolean mHasSettingsPermission = null;

        RedactionPermissionChecker(int callingPid, int callingUid,
                @NonNull String callingPackageName, @Nullable String callingAttributionTag) {
            mCallingPid = callingPid;
            mCallingUid = callingUid;
            mCallingPackageName = callingPackageName;
            mCallingAttributionTag = callingAttributionTag;
        }

        private boolean hasLocationPermissionInternal() {
            final long token = Binder.clearCallingIdentity();
            try {
                return mLocationPermissionChecker.checkLocationPermission(
                        mCallingPackageName, mCallingAttributionTag, mCallingUid,
                        null /* message */);
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        }

        /**
         * Returns whether the app holds location permission or not (might return cached result
         * if the permission was already checked before).
         */
        public boolean hasLocationPermission() {
            if (mHasLocationPermission == null) {
                // If there is no cached result, perform the check now.
                mHasLocationPermission = hasLocationPermissionInternal();
            }
            return mHasLocationPermission;
        }

        /**
         * Returns whether the app holds local mac address permission or not (might return cached
         * result if the permission was already checked before).
         */
        @CheckResult
        public boolean hasLocalMacAddressPermission() {
            if (mHasLocalMacAddressPermission == null) {
                // If there is no cached result, perform the check now.
                mHasLocalMacAddressPermission = ConnectivityService.this
                        .hasLocalMacAddressPermission(mCallingPid, mCallingUid);
            }
            return mHasLocalMacAddressPermission;
        }

        /**
         * Returns whether the app holds settings permission or not (might return cached
         * result if the permission was already checked before).
         */
        @CheckResult
        public boolean hasSettingsPermission() {
            if (mHasSettingsPermission == null) {
                // If there is no cached result, perform the check now.
                mHasSettingsPermission =
                        ConnectivityService.this.hasSettingsPermission(mCallingPid, mCallingUid);
            }
            return mHasSettingsPermission;
        }
    }

    private static boolean shouldRedact(@NetworkCapabilities.RedactionType long redactions,
            @NetworkCapabilities.NetCapability long redaction) {
        return (redactions & redaction) != 0;
    }

    /**
     * Use the provided |applicableRedactions| to check the receiving app's
     * permissions and clear/set the corresponding bit in the returned bitmask. The bitmask
     * returned will be used to ensure the necessary redactions are performed by NetworkCapabilities
     * before being sent to the corresponding app.
     */
    private @NetworkCapabilities.RedactionType long retrieveRequiredRedactions(
            @NetworkCapabilities.RedactionType long applicableRedactions,
            @NonNull RedactionPermissionChecker redactionPermissionChecker,
            boolean includeLocationSensitiveInfo) {
        long redactions = applicableRedactions;
        if (shouldRedact(redactions, REDACT_FOR_ACCESS_FINE_LOCATION)) {
            if (includeLocationSensitiveInfo
                    && redactionPermissionChecker.hasLocationPermission()) {
                redactions &= ~REDACT_FOR_ACCESS_FINE_LOCATION;
            }
        }
        if (shouldRedact(redactions, REDACT_FOR_LOCAL_MAC_ADDRESS)) {
            if (redactionPermissionChecker.hasLocalMacAddressPermission()) {
                redactions &= ~REDACT_FOR_LOCAL_MAC_ADDRESS;
            }
        }
        if (shouldRedact(redactions, REDACT_FOR_NETWORK_SETTINGS)) {
            if (redactionPermissionChecker.hasSettingsPermission()) {
                redactions &= ~REDACT_FOR_NETWORK_SETTINGS;
            }
        }
        return redactions;
    }

    @VisibleForTesting
    @Nullable
    NetworkCapabilities createWithLocationInfoSanitizedIfNecessaryWhenParceled(
            @Nullable NetworkCapabilities nc, boolean includeLocationSensitiveInfo,
            int callingPid, int callingUid, @NonNull String callingPkgName,
            @Nullable String callingAttributionTag) {
        if (nc == null) {
            return null;
        }
        // Avoid doing location permission check if the transport info has no location sensitive
        // data.
        final RedactionPermissionChecker redactionPermissionChecker =
                new RedactionPermissionChecker(callingPid, callingUid, callingPkgName,
                        callingAttributionTag);
        final long redactions = retrieveRequiredRedactions(
                nc.getApplicableRedactions(), redactionPermissionChecker,
                includeLocationSensitiveInfo);
        final NetworkCapabilities newNc = new NetworkCapabilities(nc, redactions);
        // Reset owner uid if not destined for the owner app.
        // TODO : calling UID is redacted because apps should generally not know what UID is
        // bringing up the VPN, but this should not apply to some very privileged apps like settings
        if (callingUid != nc.getOwnerUid()) {
            newNc.setOwnerUid(INVALID_UID);
            return newNc;
        }
        // Allow VPNs to see ownership of their own VPN networks - not location sensitive.
        if (nc.hasTransport(TRANSPORT_VPN)) {
            // Owner UIDs already checked above. No need to re-check.
            return newNc;
        }
        // If the calling does not want location sensitive data & target SDK >= S, then mask info.
        // Else include the owner UID iff the calling has location permission to provide backwards
        // compatibility for older apps.
        if (!includeLocationSensitiveInfo
                && isTargetSdkAtleast(
                        Build.VERSION_CODES.S, callingUid, callingPkgName)) {
            newNc.setOwnerUid(INVALID_UID);
            return newNc;
        }
        // Reset owner uid if the app has no location permission.
        if (!redactionPermissionChecker.hasLocationPermission()) {
            newNc.setOwnerUid(INVALID_UID);
        }
        return newNc;
    }

    @NonNull
    private LinkProperties linkPropertiesRestrictedForCallerPermissions(
            LinkProperties lp, int callerPid, int callerUid) {
        if (lp == null) return new LinkProperties();
        // Note : here it would be nice to check ACCESS_NETWORK_STATE and return null, but
        // this would be expensive (one more permission check every time any LP callback is
        // sent) and possibly dangerous : apps normally can't lose ACCESS_NETWORK_STATE, if
        // it happens for some reason (e.g. the package is uninstalled while CS is trying to
        // send the callback) it would crash the system server with NPE.

        // Only do a permission check if sanitization is needed, to avoid unnecessary binder calls.
        final boolean needsSanitization =
                (lp.getCaptivePortalApiUrl() != null || lp.getCaptivePortalData() != null);
        if (!needsSanitization) {
            return new LinkProperties(lp);
        }

        if (hasSettingsPermission(callerPid, callerUid)) {
            return new LinkProperties(lp, true /* parcelSensitiveFields */);
        }

        final LinkProperties newLp = new LinkProperties(lp);
        // Sensitive fields would not be parceled anyway, but sanitize for consistency before the
        // object gets parceled.
        newLp.setCaptivePortalApiUrl(null);
        newLp.setCaptivePortalData(null);
        return newLp;
    }

    private void restrictRequestUidsForCallerAndSetRequestorInfo(NetworkCapabilities nc,
            int callerUid, String callerPackageName) {
        // There is no need to track the effective UID of the request here. If the caller
        // lacks the settings permission, the effective UID is the same as the calling ID.
        if (!hasSettingsPermission()) {
            // Unprivileged apps can only pass in null or their own UID.
            if (nc.getUids() == null) {
                // If the caller passes in null, the callback will also match networks that do not
                // apply to its UID, similarly to what it would see if it called getAllNetworks.
                // In this case, redact everything in the request immediately. This ensures that the
                // app is not able to get any redacted information by filing an unredacted request
                // and observing whether the request matches something.
                if (nc.getNetworkSpecifier() != null) {
                    nc.setNetworkSpecifier(nc.getNetworkSpecifier().redact());
                }
            } else {
                nc.setSingleUid(callerUid);
            }
        }
        nc.setRequestorUidAndPackageName(callerUid, callerPackageName);
        nc.setAdministratorUids(new int[0]);

        // Clear owner UID; this can never come from an app.
        nc.setOwnerUid(INVALID_UID);
    }

    private void restrictBackgroundRequestForCaller(NetworkCapabilities nc) {
        if (!mPermissionMonitor.hasUseBackgroundNetworksPermission(mDeps.getCallingUid())) {
            nc.addCapability(NET_CAPABILITY_FOREGROUND);
        }
    }

    @Override
    public @RestrictBackgroundStatus int getRestrictBackgroundStatusByCaller() {
        enforceAccessPermission();
        final int callerUid = Binder.getCallingUid();
        final long token = Binder.clearCallingIdentity();
        try {
            return mPolicyManager.getRestrictBackgroundStatus(callerUid);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    // TODO: Consider delete this function or turn it into a no-op method.
    @Override
    public NetworkState[] getAllNetworkState() {
        // This contains IMSI details, so make sure the caller is privileged.
        enforceNetworkStackPermission(mContext);

        final ArrayList<NetworkState> result = new ArrayList<>();
        for (NetworkStateSnapshot snapshot : getAllNetworkStateSnapshots()) {
            // NetworkStateSnapshot doesn't contain NetworkInfo, so need to fetch it from the
            // NetworkAgentInfo.
            final NetworkAgentInfo nai = getNetworkAgentInfoForNetwork(snapshot.getNetwork());
            if (nai != null && nai.networkInfo.isConnected()) {
                result.add(new NetworkState(new NetworkInfo(nai.networkInfo),
                        snapshot.getLinkProperties(), snapshot.getNetworkCapabilities(),
                        snapshot.getNetwork(), snapshot.getSubscriberId()));
            }
        }
        return result.toArray(new NetworkState[0]);
    }

    @Override
    @NonNull
    public List<NetworkStateSnapshot> getAllNetworkStateSnapshots() {
        // This contains IMSI details, so make sure the caller is privileged.
        enforceNetworkStackOrSettingsPermission();

        final ArrayList<NetworkStateSnapshot> result = new ArrayList<>();
        for (Network network : getAllNetworks()) {
            final NetworkAgentInfo nai = getNetworkAgentInfoForNetwork(network);
            final boolean includeNetwork = (nai != null) && nai.isCreated();
            if (includeNetwork) {
                // TODO (b/73321673) : NetworkStateSnapshot contains a copy of the
                // NetworkCapabilities, which may contain UIDs of apps to which the
                // network applies. Should the UIDs be cleared so as not to leak or
                // interfere ?
                result.add(nai.getNetworkStateSnapshot());
            }
        }
        return result;
    }

    @Override
    public boolean isActiveNetworkMetered() {
        enforceAccessPermission();

        final NetworkCapabilities caps = getNetworkCapabilitiesInternal(getActiveNetwork());
        if (caps != null) {
            return !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED);
        } else {
            // Always return the most conservative value
            return true;
        }
    }

    /**
     * Ensures that the system cannot call a particular method.
     */
    private boolean disallowedBecauseSystemCaller() {
        // TODO: start throwing a SecurityException when GnssLocationProvider stops calling
        // requestRouteToHost. In Q, GnssLocationProvider is changed to not call requestRouteToHost
        // for devices launched with Q and above. However, existing devices upgrading to Q and
        // above must continued to be supported for few more releases.
        if (isSystem(mDeps.getCallingUid()) && SystemProperties.getInt(
                "ro.product.first_api_level", 0) > Build.VERSION_CODES.P) {
            log("This method exists only for app backwards compatibility"
                    + " and must not be called by system services.");
            return true;
        }
        return false;
    }

    /**
     * Ensure that a network route exists to deliver traffic to the specified
     * host via the specified network interface.
     * @param networkType the type of the network over which traffic to the
     * specified host is to be routed
     * @param hostAddress the IP address of the host to which the route is
     * desired
     * @return {@code true} on success, {@code false} on failure
     */
    @Override
    public boolean requestRouteToHostAddress(int networkType, byte[] hostAddress,
            String callingPackageName, String callingAttributionTag) {
        if (disallowedBecauseSystemCaller()) {
            return false;
        }
        PermissionUtils.enforcePackageNameMatchesUid(
                mContext, mDeps.getCallingUid(), callingPackageName);
        enforceChangePermission(callingPackageName, callingAttributionTag);
        if (mProtectedNetworks.contains(networkType)) {
            enforceConnectivityRestrictedNetworksPermission(true /* checkUidsAllowedList */);
        }

        InetAddress addr;
        try {
            addr = InetAddress.getByAddress(hostAddress);
        } catch (UnknownHostException e) {
            if (DBG) log("requestRouteToHostAddress got " + e);
            return false;
        }

        if (!ConnectivityManager.isNetworkTypeValid(networkType)) {
            if (DBG) log("requestRouteToHostAddress on invalid network: " + networkType);
            return false;
        }

        NetworkAgentInfo nai = mLegacyTypeTracker.getNetworkForType(networkType);
        if (nai == null) {
            if (!mLegacyTypeTracker.isTypeSupported(networkType)) {
                if (DBG) log("requestRouteToHostAddress on unsupported network: " + networkType);
            } else {
                if (DBG) log("requestRouteToHostAddress on down network: " + networkType);
            }
            return false;
        }

        DetailedState netState;
        synchronized (nai) {
            netState = nai.networkInfo.getDetailedState();
        }

        if (netState != DetailedState.CONNECTED && netState != DetailedState.CAPTIVE_PORTAL_CHECK) {
            if (VDBG) {
                log("requestRouteToHostAddress on down network "
                        + "(" + networkType + ") - dropped"
                        + " netState=" + netState);
            }
            return false;
        }

        final int uid = mDeps.getCallingUid();
        final long token = Binder.clearCallingIdentity();
        try {
            LinkProperties lp;
            int netId;
            synchronized (nai) {
                lp = nai.linkProperties;
                netId = nai.network.getNetId();
            }
            boolean ok = addLegacyRouteToHost(lp, addr, netId, uid);
            if (DBG) {
                log("requestRouteToHostAddress " + addr + nai.toShortString() + " ok=" + ok);
            }
            return ok;
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    private boolean addLegacyRouteToHost(LinkProperties lp, InetAddress addr, int netId, int uid) {
        RouteInfo bestRoute = RouteInfo.selectBestRoute(lp.getAllRoutes(), addr);
        if (bestRoute == null) {
            bestRoute = RouteInfo.makeHostRoute(addr, lp.getInterfaceName());
        } else {
            String iface = bestRoute.getInterface();
            if (bestRoute.getGateway().equals(addr)) {
                // if there is no better route, add the implied hostroute for our gateway
                bestRoute = RouteInfo.makeHostRoute(addr, iface);
            } else {
                // if we will connect to this through another route, add a direct route
                // to it's gateway
                bestRoute = RouteInfo.makeHostRoute(addr, bestRoute.getGateway(), iface);
            }
        }
        if (DBG) log("Adding legacy route " + bestRoute +
                " for UID/PID " + uid + "/" + Binder.getCallingPid());

        final String dst = bestRoute.getDestinationLinkAddress().toString();
        final String nextHop = bestRoute.hasGateway()
                ? bestRoute.getGateway().getHostAddress() : "";
        try {
            mNetd.networkAddLegacyRoute(netId, bestRoute.getInterface(), dst, nextHop , uid);
        } catch (RemoteException | ServiceSpecificException e) {
            if (DBG) loge("Exception trying to add a route: " + e);
            return false;
        }
        return true;
    }

    class DnsResolverUnsolicitedEventCallback extends
            IDnsResolverUnsolicitedEventListener.Stub {
        @Override
        public void onPrivateDnsValidationEvent(final PrivateDnsValidationEventParcel event) {
            try {
                mHandler.sendMessage(mHandler.obtainMessage(
                        EVENT_PRIVATE_DNS_VALIDATION_UPDATE,
                        new PrivateDnsValidationUpdate(event.netId,
                                InetAddresses.parseNumericAddress(event.ipAddress),
                                event.hostname, event.validation)));
            } catch (IllegalArgumentException e) {
                loge("Error parsing ip address in validation event");
            }
        }

        @Override
        public void onDnsHealthEvent(final DnsHealthEventParcel event) {
            NetworkAgentInfo nai = getNetworkAgentInfoForNetId(event.netId);
            // Netd event only allow registrants from system. Each NetworkMonitor thread is under
            // the caller thread of registerNetworkAgent. Thus, it's not allowed to register netd
            // event callback for certain nai. e.g. cellular. Register here to pass to
            // NetworkMonitor instead.
            // TODO: Move the Dns Event to NetworkMonitor. NetdEventListenerService only allows one
            // callback from each caller type. Need to re-factor NetdEventListenerService to allow
            // multiple NetworkMonitor registrants.
            if (nai != null && nai.satisfies(mDefaultRequest.mRequests.get(0))) {
                nai.networkMonitor().notifyDnsResponse(event.healthResult);
            }
        }

        @Override
        public void onNat64PrefixEvent(final Nat64PrefixEventParcel event) {
            mHandler.post(() -> handleNat64PrefixEvent(event.netId, event.prefixOperation,
                    event.prefixAddress, event.prefixLength));
        }

        @Override
        public int getInterfaceVersion() {
            return this.VERSION;
        }

        @Override
        public String getInterfaceHash() {
            return this.HASH;
        }
    }

    @VisibleForTesting
    protected final DnsResolverUnsolicitedEventCallback mResolverUnsolEventCallback =
            new DnsResolverUnsolicitedEventCallback();

    private void registerDnsResolverUnsolicitedEventListener() {
        try {
            mDnsResolver.registerUnsolicitedEventListener(mResolverUnsolEventCallback);
        } catch (Exception e) {
            loge("Error registering DnsResolver unsolicited event callback: " + e);
        }
    }

    private final NetworkPolicyCallback mPolicyCallback = new NetworkPolicyCallback() {
        @Override
        public void onUidBlockedReasonChanged(int uid, @BlockedReason int blockedReasons) {
            mHandler.sendMessage(mHandler.obtainMessage(EVENT_UID_BLOCKED_REASON_CHANGED,
                    uid, blockedReasons));
        }

        @Override
        public void onUidsAllowedTransportsChanged(int[] uids, long[] allowedTransports) {
            setUidsAllowedTransports(uids, allowedTransports);
        }
    };

    private void handleUidBlockedReasonChanged(int uid, @BlockedReason int blockedReasons) {
        maybeNotifyNetworkBlockedForNewState(uid, blockedReasons);
        setUidBlockedReasons(uid, blockedReasons);
    }

    static final class UidFrozenStateChangedArgs {
        final int[] mUids;
        final int[] mFrozenStates;

        UidFrozenStateChangedArgs(int[] uids, int[] frozenStates) {
            mUids = uids;
            mFrozenStates = frozenStates;
        }
    }

    /**
     * Check if the cell network is idle.
     * @return true if the cell network state is idle
     *         false if the cell network state is active or unknown
     */
    private boolean isCellNetworkIdle() {
        final NetworkAgentInfo defaultNai = getDefaultNetwork();
        if (defaultNai == null
                || !defaultNai.networkCapabilities.hasTransport(TRANSPORT_CELLULAR)) {
            // mNetworkActivityTracker only tracks the activity of the default network. So if the
            // cell network is not the default network, cell network state is unknown.
            // TODO(b/279380356): Track cell network state when the cell is not the default network
            return false;
        }

        return !mNetworkActivityTracker.isDefaultNetworkActive();
    }

    private void handleFrozenUids(int[] uids, int[] frozenStates) {
        final ArraySet<Integer> ownerUids = new ArraySet<>();

        for (int i = 0; i < uids.length; i++) {
            if (frozenStates[i] == UID_FROZEN_STATE_FROZEN) {
                ownerUids.add(uids[i]);
            } else {
                mPendingFrozenUids.remove(uids[i]);
            }
        }

        if (ownerUids.isEmpty()) {
            return;
        }

        if (mDelayDestroyFrozenSockets && isCellNetworkIdle()) {
            // Delay closing sockets to avoid waking the cell modem up.
            // Wi-Fi network state is not considered since waking Wi-Fi modem up is much cheaper
            // than waking cell modem up.
            mPendingFrozenUids.addAll(ownerUids);
        } else {
            try {
                mDeps.destroyLiveTcpSocketsByOwnerUids(ownerUids);
            } catch (SocketException | InterruptedIOException | ErrnoException e) {
                loge("Exception in socket destroy: " + e);
            }
        }
    }

    private void closePendingFrozenSockets() {
        ensureRunningOnConnectivityServiceThread();

        try {
            mDeps.destroyLiveTcpSocketsByOwnerUids(mPendingFrozenUids);
        } catch (SocketException | InterruptedIOException | ErrnoException e) {
            loge("Failed to close pending frozen app sockets: " + e);
        }
        mPendingFrozenUids.clear();
    }

    private void handleReportNetworkActivity(final NetworkActivityParams params) {
        mNetworkActivityTracker.handleReportNetworkActivity(params);

        final boolean isCellNetworkActivity;
        if (mTrackMultiNetworkActivities) {
            final NetworkAgentInfo nai = getNetworkAgentInfoForNetId(params.label);
            // nai could be null if netd receives a netlink message and calls the network
            // activity change callback after the network is unregistered from ConnectivityService.
            isCellNetworkActivity = nai != null
                    && nai.networkCapabilities.hasTransport(TRANSPORT_CELLULAR);
        } else {
            isCellNetworkActivity = params.label == TRANSPORT_CELLULAR;
        }

        if (mDelayDestroyFrozenSockets
                && params.isActive
                && isCellNetworkActivity
                && !mPendingFrozenUids.isEmpty()) {
            closePendingFrozenSockets();
        }
    }

    /**
     * If the cellular network is no longer the default network, close pending frozen sockets.
     *
     * @param newNetwork new default network
     * @param oldNetwork old default network
     */
    private void maybeClosePendingFrozenSockets(NetworkAgentInfo newNetwork,
            NetworkAgentInfo oldNetwork) {
        final boolean isOldNetworkCellular = oldNetwork != null
                && oldNetwork.networkCapabilities.hasTransport(TRANSPORT_CELLULAR);
        final boolean isNewNetworkCellular = newNetwork != null
                && newNetwork.networkCapabilities.hasTransport(TRANSPORT_CELLULAR);

        if (isOldNetworkCellular
                && !isNewNetworkCellular
                && !mPendingFrozenUids.isEmpty()) {
            closePendingFrozenSockets();
        }
    }

    private void dumpCloseFrozenAppSockets(IndentingPrintWriter pw) {
        pw.println("CloseFrozenAppSockets:");
        pw.increaseIndent();
        pw.print("mDestroyFrozenSockets="); pw.println(mDestroyFrozenSockets);
        pw.print("mDelayDestroyFrozenSockets="); pw.println(mDelayDestroyFrozenSockets);
        pw.print("mPendingFrozenUids="); pw.println(mPendingFrozenUids);
        pw.decreaseIndent();
    }

    private void dumpBpfProgramStatus(IndentingPrintWriter pw) {
        pw.println("Bpf Program Status:");
        pw.increaseIndent();
        try {
            pw.print("CGROUP_INET_INGRESS: ");
            pw.println(mDeps.getBpfProgramId(BPF_CGROUP_INET_INGRESS));
            pw.print("CGROUP_INET_EGRESS: ");
            pw.println(mDeps.getBpfProgramId(BPF_CGROUP_INET_EGRESS));
            pw.print("CGROUP_INET_SOCK_CREATE: ");
            pw.println(mDeps.getBpfProgramId(BPF_CGROUP_INET_SOCK_CREATE));
            pw.print("CGROUP_INET4_BIND: ");
            pw.println(mDeps.getBpfProgramId(BPF_CGROUP_INET4_BIND));
            pw.print("CGROUP_INET6_BIND: ");
            pw.println(mDeps.getBpfProgramId(BPF_CGROUP_INET6_BIND));
        } catch (IOException e) {
            pw.println("  IOException");
        }
        pw.decreaseIndent();
    }

    @VisibleForTesting
    static final String KEY_DESTROY_FROZEN_SOCKETS_VERSION = "destroy_frozen_sockets_version";
    @VisibleForTesting
    static final String DELAY_DESTROY_FROZEN_SOCKETS_VERSION =
            "delay_destroy_frozen_sockets_version";

    @VisibleForTesting
    public static final String ALLOW_SYSUI_CONNECTIVITY_REPORTS =
            "allow_sysui_connectivity_reports";

    public static final String LOG_BPF_RC = "log_bpf_rc_force_disable";

    public static final String ALLOW_SATALLITE_NETWORK_FALLBACK =
            "allow_satallite_network_fallback";

    private void enforceInternetPermission() {
        mContext.enforceCallingOrSelfPermission(
                android.Manifest.permission.INTERNET,
                "ConnectivityService");
    }

    private void enforceAccessPermission() {
        mContext.enforceCallingOrSelfPermission(
                android.Manifest.permission.ACCESS_NETWORK_STATE,
                "ConnectivityService");
    }

    @CheckResult
    private boolean hasAccessPermission(int pid, int uid) {
        return mContext.checkPermission(android.Manifest.permission.ACCESS_NETWORK_STATE, pid, uid)
                == PERMISSION_GRANTED;
    }

    /**
     * Performs a strict and comprehensive check of whether a calling package is allowed to
     * change the state of network, as the condition differs for pre-M, M+, and
     * privileged/preinstalled apps. The caller is expected to have either the
     * CHANGE_NETWORK_STATE or the WRITE_SETTINGS permission declared. Either of these
     * permissions allow changing network state; WRITE_SETTINGS is a runtime permission and
     * can be revoked, but (except in M, excluding M MRs), CHANGE_NETWORK_STATE is a normal
     * permission and cannot be revoked. See http://b/23597341
     *
     * Note: if the check succeeds because the application holds WRITE_SETTINGS, the operation
     * of this app will be updated to the current time.
     */
    private void enforceChangePermission(String callingPkg, String callingAttributionTag) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.CHANGE_NETWORK_STATE)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }

        if (callingPkg == null) {
            throw new SecurityException("Calling package name is null.");
        }

        final AppOpsManager appOpsMgr = mContext.getSystemService(AppOpsManager.class);
        final int uid = mDeps.getCallingUid();
        final int mode = appOpsMgr.noteOpNoThrow(AppOpsManager.OPSTR_WRITE_SETTINGS, uid,
                callingPkg, callingAttributionTag, null /* message */);

        if (mode == AppOpsManager.MODE_ALLOWED) {
            return;
        }

        if ((mode == AppOpsManager.MODE_DEFAULT) && (mContext.checkCallingOrSelfPermission(
                android.Manifest.permission.WRITE_SETTINGS) == PackageManager.PERMISSION_GRANTED)) {
            return;
        }

        throw new SecurityException(callingPkg + " was not granted either of these permissions:"
                + android.Manifest.permission.CHANGE_NETWORK_STATE + ","
                + android.Manifest.permission.WRITE_SETTINGS + ".");
    }

    private void enforceSettingsPermission() {
        enforceAnyPermissionOf(mContext,
                android.Manifest.permission.NETWORK_SETTINGS,
                NetworkStack.PERMISSION_MAINLINE_NETWORK_STACK);
    }

    private void enforceSettingsOrSetupWizardOrUseRestrictedNetworksPermission() {
        enforceAnyPermissionOf(mContext,
                android.Manifest.permission.NETWORK_SETTINGS,
                android.Manifest.permission.NETWORK_SETUP_WIZARD,
                NetworkStack.PERMISSION_MAINLINE_NETWORK_STACK,
                Manifest.permission.CONNECTIVITY_USE_RESTRICTED_NETWORKS);
    }

    private void enforceNetworkFactoryPermission() {
        // TODO: Check for the BLUETOOTH_STACK permission once that is in the API surface.
        if (UserHandle.getAppId(getCallingUid()) == Process.BLUETOOTH_UID) return;
        enforceAnyPermissionOf(mContext,
                android.Manifest.permission.NETWORK_FACTORY,
                NetworkStack.PERMISSION_MAINLINE_NETWORK_STACK);
    }

    private void enforceNetworkFactoryOrSettingsPermission() {
        // TODO: Check for the BLUETOOTH_STACK permission once that is in the API surface.
        if (UserHandle.getAppId(getCallingUid()) == Process.BLUETOOTH_UID) return;
        enforceAnyPermissionOf(mContext,
                android.Manifest.permission.NETWORK_SETTINGS,
                android.Manifest.permission.NETWORK_FACTORY,
                NetworkStack.PERMISSION_MAINLINE_NETWORK_STACK);
    }

    private void enforceNetworkFactoryOrTestNetworksPermission() {
        // TODO: Check for the BLUETOOTH_STACK permission once that is in the API surface.
        if (UserHandle.getAppId(getCallingUid()) == Process.BLUETOOTH_UID) return;
        enforceAnyPermissionOf(mContext,
                android.Manifest.permission.MANAGE_TEST_NETWORKS,
                android.Manifest.permission.NETWORK_FACTORY,
                NetworkStack.PERMISSION_MAINLINE_NETWORK_STACK);
    }

    @CheckResult
    private boolean hasNetworkFactoryOrSettingsPermission(int pid, int uid) {
        return PERMISSION_GRANTED == mContext.checkPermission(
                android.Manifest.permission.NETWORK_FACTORY, pid, uid)
                || PERMISSION_GRANTED == mContext.checkPermission(
                android.Manifest.permission.NETWORK_SETTINGS, pid, uid)
                || PERMISSION_GRANTED == mContext.checkPermission(
                NetworkStack.PERMISSION_MAINLINE_NETWORK_STACK, pid, uid)
                || UserHandle.getAppId(uid) == Process.BLUETOOTH_UID;
    }

    @CheckResult
    private boolean hasSettingsPermission() {
        return hasAnyPermissionOf(mContext, android.Manifest.permission.NETWORK_SETTINGS,
                NetworkStack.PERMISSION_MAINLINE_NETWORK_STACK);
    }

    @CheckResult
    private boolean hasSettingsPermission(int pid, int uid) {
        return PERMISSION_GRANTED == mContext.checkPermission(
                android.Manifest.permission.NETWORK_SETTINGS, pid, uid)
                || PERMISSION_GRANTED == mContext.checkPermission(
                NetworkStack.PERMISSION_MAINLINE_NETWORK_STACK, pid, uid);
    }

    private void enforceNetworkStackOrSettingsPermission() {
        enforceNetworkStackPermissionOr(mContext,
                android.Manifest.permission.NETWORK_SETTINGS);
    }

    private void enforceNetworkStackSettingsOrSetup() {
        enforceNetworkStackPermissionOr(mContext,
                android.Manifest.permission.NETWORK_SETTINGS,
                android.Manifest.permission.NETWORK_SETUP_WIZARD);
    }

    private void enforceAirplaneModePermission() {
        enforceNetworkStackPermissionOr(mContext,
                android.Manifest.permission.NETWORK_AIRPLANE_MODE,
                android.Manifest.permission.NETWORK_SETTINGS,
                android.Manifest.permission.NETWORK_SETUP_WIZARD);
    }

    private void enforceOemNetworkPreferencesPermission() {
        mContext.enforceCallingOrSelfPermission(
                android.Manifest.permission.CONTROL_OEM_PAID_NETWORK_PREFERENCE,
                "ConnectivityService");
    }

    private void enforceManageTestNetworksPermission() {
        mContext.enforceCallingOrSelfPermission(
                android.Manifest.permission.MANAGE_TEST_NETWORKS,
                "ConnectivityService");
    }

    @CheckResult
    private boolean hasNetworkStackPermission() {
        return hasAnyPermissionOf(mContext, android.Manifest.permission.NETWORK_STACK,
                NetworkStack.PERMISSION_MAINLINE_NETWORK_STACK);
    }

    @CheckResult
    private boolean hasNetworkStackPermission(int pid, int uid) {
        return hasAnyPermissionOf(mContext, pid, uid, android.Manifest.permission.NETWORK_STACK,
                NetworkStack.PERMISSION_MAINLINE_NETWORK_STACK);
    }

    @CheckResult
    private boolean hasSystemBarServicePermission(int pid, int uid) {
        return hasAnyPermissionOf(mContext, pid, uid,
                android.Manifest.permission.STATUS_BAR_SERVICE);
    }

    @CheckResult
    private boolean hasNetworkSignalStrengthWakeupPermission(int pid, int uid) {
        return hasAnyPermissionOf(mContext, pid, uid,
                android.Manifest.permission.NETWORK_SIGNAL_STRENGTH_WAKEUP,
                NetworkStack.PERMISSION_MAINLINE_NETWORK_STACK,
                android.Manifest.permission.NETWORK_SETTINGS);
    }

    @CheckResult
    private boolean hasConnectivityRestrictedNetworksPermission(int callingUid,
            boolean checkUidsAllowedList) {
        if (hasAnyPermissionOf(mContext,
                android.Manifest.permission.CONNECTIVITY_USE_RESTRICTED_NETWORKS)) {
            return true;
        }

        // fallback to ConnectivityInternalPermission
        // TODO: Remove this fallback check after all apps have declared
        //  CONNECTIVITY_USE_RESTRICTED_NETWORKS.
        if (hasAnyPermissionOf(mContext, android.Manifest.permission.CONNECTIVITY_INTERNAL)) {
            return true;
        }

        // Check whether uid is in allowed on restricted networks list.
        if (checkUidsAllowedList
                && mPermissionMonitor.isUidAllowedOnRestrictedNetworks(callingUid)) {
            return true;
        }
        return false;
    }

    private void enforceConnectivityRestrictedNetworksPermission(boolean checkUidsAllowedList) {
        final int callingUid = mDeps.getCallingUid();
        if (!hasConnectivityRestrictedNetworksPermission(callingUid, checkUidsAllowedList)) {
            throw new SecurityException("ConnectivityService: user " + callingUid
                    + " has no permission to access restricted network.");
        }
    }

    private void enforceKeepalivePermission() {
        mContext.enforceCallingOrSelfPermission(KeepaliveTracker.PERMISSION, "ConnectivityService");
    }

    @CheckResult
    private boolean hasLocalMacAddressPermission(int pid, int uid) {
        return PERMISSION_GRANTED == mContext.checkPermission(
                Manifest.permission.LOCAL_MAC_ADDRESS, pid, uid);
    }

    private void sendConnectedBroadcast(NetworkInfo info) {
        sendGeneralBroadcast(info, CONNECTIVITY_ACTION);
    }

    private void sendInetConditionBroadcast(NetworkInfo info) {
        sendGeneralBroadcast(info, ConnectivityManager.INET_CONDITION_ACTION);
    }

    private Intent makeGeneralIntent(NetworkInfo info, String bcastType) {
        Intent intent = new Intent(bcastType);
        intent.putExtra(ConnectivityManager.EXTRA_NETWORK_INFO, new NetworkInfo(info));
        intent.putExtra(ConnectivityManager.EXTRA_NETWORK_TYPE, info.getType());
        if (info.isFailover()) {
            intent.putExtra(ConnectivityManager.EXTRA_IS_FAILOVER, true);
            info.setFailover(false);
        }
        if (info.getReason() != null) {
            intent.putExtra(ConnectivityManager.EXTRA_REASON, info.getReason());
        }
        if (info.getExtraInfo() != null) {
            intent.putExtra(ConnectivityManager.EXTRA_EXTRA_INFO,
                    info.getExtraInfo());
        }
        intent.putExtra(ConnectivityManager.EXTRA_INET_CONDITION, mDefaultInetConditionPublished);
        return intent;
    }

    private void sendGeneralBroadcast(NetworkInfo info, String bcastType) {
        sendStickyBroadcast(makeGeneralIntent(info, bcastType));
    }

    // TODO(b/193460475): Remove when tooling supports SystemApi to public API.
    @SuppressLint("NewApi")
    // TODO: Set the mini sdk to 31 and remove @TargetApi annotation when b/205923322 is addressed.
    @TargetApi(Build.VERSION_CODES.S)
    private void sendStickyBroadcast(Intent intent) {
        synchronized (this) {
            if (!mSystemReady
                    && intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                mInitialBroadcast = new Intent(intent);
            }
            intent.addFlags(Intent.FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT);
            if (VDBG) {
                log("sendStickyBroadcast: action=" + intent.getAction());
            }

            Bundle options = null;
            final long ident = Binder.clearCallingIdentity();
            if (ConnectivityManager.CONNECTIVITY_ACTION.equals(intent.getAction())) {
                final NetworkInfo ni = intent.getParcelableExtra(
                        ConnectivityManager.EXTRA_NETWORK_INFO);
                final BroadcastOptions opts = BroadcastOptions.makeBasic();
                opts.setMaxManifestReceiverApiLevel(Build.VERSION_CODES.M);
                applyMostRecentPolicyForConnectivityAction(opts, ni);
                options = opts.toBundle();
                intent.addFlags(Intent.FLAG_RECEIVER_VISIBLE_TO_INSTANT_APPS);
            }
            try {
                mUserAllContext.sendStickyBroadcast(intent, options);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }
    }

    private void applyMostRecentPolicyForConnectivityAction(BroadcastOptions options,
            NetworkInfo info) {
        // Delivery group policy APIs are only available on U+.
        if (!mDeps.isAtLeastU()) return;

        final BroadcastOptionsShim optsShim = mDeps.makeBroadcastOptionsShim(options);
        try {
            // This allows us to discard older broadcasts still waiting to be delivered
            // which have the same namespace and key.
            optsShim.setDeliveryGroupPolicy(ConstantsShim.DELIVERY_GROUP_POLICY_MOST_RECENT);
            optsShim.setDeliveryGroupMatchingKey(ConnectivityManager.CONNECTIVITY_ACTION,
                    createDeliveryGroupKeyForConnectivityAction(info));
            optsShim.setDeferralPolicy(ConstantsShim.DEFERRAL_POLICY_UNTIL_ACTIVE);
        } catch (UnsupportedApiLevelException e) {
            Log.wtf(TAG, "Using unsupported API" + e);
        }
    }

    @VisibleForTesting
    static String createDeliveryGroupKeyForConnectivityAction(NetworkInfo info) {
        final StringBuilder sb = new StringBuilder();
        sb.append(info.getType()).append(DELIVERY_GROUP_KEY_DELIMITER);
        sb.append(info.getSubtype()).append(DELIVERY_GROUP_KEY_DELIMITER);
        sb.append(info.getExtraInfo());
        return sb.toString();
    }

    /**
     * Called by SystemServer through ConnectivityManager when the system is ready.
     */
    @Override
    public void systemReady() {
        if (mDeps.getCallingUid() != Process.SYSTEM_UID) {
            throw new SecurityException("Calling Uid is not system uid.");
        }
        systemReadyInternal();
    }

    /**
     * Called when ConnectivityService can initialize remaining components.
     */
    @VisibleForTesting
    public void systemReadyInternal() {
        // Load flags after PackageManager is ready to query module version
        mFlags.loadFlags(mDeps, mContext);

        // Since mApps in PermissionMonitor needs to be populated first to ensure that
        // listening network request which is sent by MultipathPolicyTracker won't be added
        // NET_CAPABILITY_FOREGROUND capability. Thus, MultipathPolicyTracker.start() must
        // be called after PermissionMonitor#startMonitoring().
        // Calling PermissionMonitor#startMonitoring() in systemReadyInternal() and the
        // MultipathPolicyTracker.start() is called in NetworkPolicyManagerService#systemReady()
        // to ensure the tracking will be initialized correctly.
        final ConditionVariable startMonitoringDone = new ConditionVariable();
        mHandler.post(() -> {
            mPermissionMonitor.startMonitoring();
            startMonitoringDone.open();
        });
        mProxyTracker.loadGlobalProxy();
        registerDnsResolverUnsolicitedEventListener();

        synchronized (this) {
            mSystemReady = true;
            if (mInitialBroadcast != null) {
                mContext.sendStickyBroadcastAsUser(mInitialBroadcast, UserHandle.ALL);
                mInitialBroadcast = null;
            }
        }

        // Create network requests for always-on networks.
        mHandler.sendMessage(mHandler.obtainMessage(EVENT_CONFIGURE_ALWAYS_ON_NETWORKS));

        // Update mobile data preference if necessary.
        // Note that updating can be skipped here if the list is empty only because no uid
        // rules are applied before system ready. Normally, the empty uid list means to clear
        // the uids rules on netd.
        if (!ConnectivitySettingsManager.getMobileDataPreferredUids(mContext).isEmpty()) {
            updateMobileDataPreferredUids();
        }

        if (mSatelliteAccessController != null) {
            mSatelliteAccessController.start();
        }

        // On T+ devices, register callback for statsd to pull NETWORK_BPF_MAP_INFO atom
        if (mDeps.isAtLeastT()) {
            mBpfNetMaps.setPullAtomCallback(mContext);
        }
        ConnectivitySampleMetricsHelper.start(mContext, mHandler,
                CONNECTIVITY_STATE_SAMPLE, this::sampleConnectivityStateToStatsEvent);
        // Wait PermissionMonitor to finish the permission update. Then MultipathPolicyTracker won't
        // have permission problem. While CV#block() is unbounded in time and can in principle block
        // forever, this replaces a synchronous call to PermissionMonitor#startMonitoring, which
        // could have blocked forever too.
        startMonitoringDone.block();
    }

    /**
     * Start listening for default data network activity state changes.
     */
    @Override
    public void registerNetworkActivityListener(@NonNull INetworkActivityListener l) {
        mNetworkActivityTracker.registerNetworkActivityListener(l);
    }

    /**
     * Stop listening for default data network activity state changes.
     */
    @Override
    public void unregisterNetworkActivityListener(@NonNull INetworkActivityListener l) {
        mNetworkActivityTracker.unregisterNetworkActivityListener(l);
    }

    /**
     * Check whether the default network radio is currently active.
     */
    @Override
    public boolean isDefaultNetworkActive() {
        return mNetworkActivityTracker.isDefaultNetworkActive();
    }

    /**
     * Reads the network specific MTU size from resources.
     * and set it on it's iface.
     */
    private void updateMtu(@NonNull LinkProperties newLp, @Nullable LinkProperties oldLp) {
        final String iface = newLp.getInterfaceName();
        final int mtu = newLp.getMtu();
        if (mtu == 0) {
            // Silently ignore unset MTU value.
            return;
        }
        if (oldLp != null && newLp.isIdenticalMtu(oldLp)
                && TextUtils.equals(oldLp.getInterfaceName(), iface)) {
            if (VDBG) log("identical MTU and iface - not setting");
            return;
        }
        // Cannot set MTU without interface name
        if (TextUtils.isEmpty(iface)) {
            if (VDBG) log("Setting MTU size with null iface.");
            return;
        }

        if (!LinkProperties.isValidMtu(mtu, newLp.hasGlobalIpv6Address())) {
            loge("Unexpected mtu value: " + mtu + ", " + iface);
            return;
        }

        try {
            if (VDBG || DDBG) log("Setting MTU size: " + iface + ", " + mtu);
            mNetd.interfaceSetMtu(iface, mtu);
        } catch (RemoteException | ServiceSpecificException e) {
            loge("exception in interfaceSetMtu()" + e);
        }
    }

    @VisibleForTesting
    protected static final String DEFAULT_TCP_BUFFER_SIZES = "4096,87380,110208,4096,16384,110208";

    private void updateTcpBufferSizes(@Nullable String tcpBufferSizes) {
        String[] values = null;
        if (tcpBufferSizes != null) {
            values = tcpBufferSizes.split(",");
        }

        if (values == null || values.length != 6) {
            if (DBG) log("Invalid tcpBufferSizes string: " + tcpBufferSizes +", using defaults");
            tcpBufferSizes = DEFAULT_TCP_BUFFER_SIZES;
            values = tcpBufferSizes.split(",");
        }

        if (tcpBufferSizes.equals(mCurrentTcpBufferSizes)) return;

        try {
            if (VDBG || DDBG) log("Setting tx/rx TCP buffers to " + tcpBufferSizes);

            String rmemValues = String.join(" ", values[0], values[1], values[2]);
            String wmemValues = String.join(" ", values[3], values[4], values[5]);
            mNetd.setTcpRWmemorySize(rmemValues, wmemValues);
            mCurrentTcpBufferSizes = tcpBufferSizes;
        } catch (RemoteException | ServiceSpecificException e) {
            loge("Can't set TCP buffer sizes:" + e);
        }
    }

    @Override
    public int getRestoreDefaultNetworkDelay(int networkType) {
        String restoreDefaultNetworkDelayStr = mSystemProperties.get(
                NETWORK_RESTORE_DELAY_PROP_NAME);
        if(restoreDefaultNetworkDelayStr != null &&
                restoreDefaultNetworkDelayStr.length() != 0) {
            try {
                return Integer.parseInt(restoreDefaultNetworkDelayStr);
            } catch (NumberFormatException e) {
            }
        }
        // if the system property isn't set, use the value for the apn type
        int ret = RESTORE_DEFAULT_NETWORK_DELAY;

        if (mLegacyTypeTracker.isTypeSupported(networkType)) {
            ret = mLegacyTypeTracker.getRestoreTimerForType(networkType);
        }
        return ret;
    }

    private void dumpNetworkDiagnostics(IndentingPrintWriter pw) {
        final List<NetworkDiagnostics> netDiags = new ArrayList<>();
        final long DIAG_TIME_MS = 5000;
        for (NetworkAgentInfo nai : networksSortedById()) {
            PrivateDnsConfig privateDnsCfg = mDnsManager.getPrivateDnsConfig(nai.network);
            // Start gathering diagnostic information.
            netDiags.add(new NetworkDiagnostics(
                    nai.network,
                    new LinkProperties(nai.linkProperties),  // Must be a copy.
                    privateDnsCfg,
                    DIAG_TIME_MS));
        }

        for (NetworkDiagnostics netDiag : netDiags) {
            pw.println();
            netDiag.waitForMeasurements();
            netDiag.dump(pw);
        }
    }

    @Override
    protected void dump(@NonNull FileDescriptor fd, @NonNull PrintWriter writer,
            @Nullable String[] args) {
        if (!hasDumpPermission(mContext, TAG, writer)) return;

        mPriorityDumper.dump(fd, writer, args);
    }

    @CheckResult
    private boolean hasDumpPermission(Context context, String tag, PrintWriter pw) {
        if (context.checkCallingOrSelfPermission(android.Manifest.permission.DUMP)
                != PackageManager.PERMISSION_GRANTED) {
            pw.println("Permission Denial: can't dump " + tag + " from from pid="
                    + Binder.getCallingPid() + ", uid=" + mDeps.getCallingUid()
                    + " due to missing android.permission.DUMP permission");
            return false;
        } else {
            return true;
        }
    }

    private void doDump(FileDescriptor fd, PrintWriter writer, String[] args) {
        final IndentingPrintWriter pw = new IndentingPrintWriter(writer, "  ");

        if (CollectionUtils.contains(args, DIAG_ARG)) {
            dumpNetworkDiagnostics(pw);
            return;
        } else if (CollectionUtils.contains(args, NETWORK_ARG)) {
            dumpNetworks(pw);
            return;
        } else if (CollectionUtils.contains(args, REQUEST_ARG)) {
            dumpNetworkRequests(pw);
            return;
        } else if (CollectionUtils.contains(args, TRAFFICCONTROLLER_ARG)) {
            boolean verbose = !CollectionUtils.contains(args, SHORT_ARG);
            dumpTrafficController(pw, fd, verbose);
            return;
        }

        pw.println("NetworkProviders for:");
        pw.increaseIndent();
        for (NetworkProviderInfo npi : mNetworkProviderInfos.values()) {
            pw.println(npi.providerId + ": " + npi.name);
        }
        pw.decreaseIndent();
        pw.println();

        final NetworkAgentInfo defaultNai = getDefaultNetwork();
        pw.print("Active default network: ");
        if (defaultNai == null) {
            pw.println("none");
        } else {
            pw.println(defaultNai.network.getNetId());
        }
        pw.println();

        pw.println("Current network preferences: ");
        pw.increaseIndent();
        dumpNetworkPreferences(pw);
        pw.decreaseIndent();
        pw.println();

        pw.println("Current Networks:");
        pw.increaseIndent();
        dumpNetworks(pw);
        pw.decreaseIndent();
        pw.println();

        pw.println("Status for known UIDs:");
        pw.increaseIndent();
        final int size = mUidBlockedReasons.size();
        for (int i = 0; i < size; i++) {
            // Don't crash if the array is modified while dumping in bugreports.
            try {
                final int uid = mUidBlockedReasons.keyAt(i);
                final int blockedReasons = mUidBlockedReasons.valueAt(i);
                pw.println("UID=" + uid + " blockedReasons="
                        + Integer.toHexString(blockedReasons));
            } catch (ArrayIndexOutOfBoundsException e) {
                pw.println("  ArrayIndexOutOfBoundsException");
            } catch (ConcurrentModificationException e) {
                pw.println("  ConcurrentModificationException");
            }
        }
        pw.println();
        pw.decreaseIndent();

        pw.println("Network Requests:");
        pw.increaseIndent();
        dumpNetworkRequests(pw);
        pw.decreaseIndent();
        pw.println();

        pw.println("Network Offers:");
        pw.increaseIndent();
        for (final NetworkOfferInfo offerInfo : mNetworkOffers) {
            pw.println(offerInfo.offer);
        }
        pw.decreaseIndent();
        pw.println();

        mLegacyTypeTracker.dump(pw);

        pw.println();
        mKeepaliveTracker.dump(pw);

        pw.println();
        dumpAvoidBadWifiSettings(pw);

        pw.println();
        dumpCloseFrozenAppSockets(pw);

        pw.println();
        dumpBpfProgramStatus(pw);

        if (null != mCarrierPrivilegeAuthenticator) {
            pw.println();
            mCarrierPrivilegeAuthenticator.dump(pw);
        }

        pw.println();

        if (!CollectionUtils.contains(args, SHORT_ARG)) {
            pw.println();
            pw.println("mNetworkRequestInfoLogs (most recent first):");
            pw.increaseIndent();
            mNetworkRequestInfoLogs.reverseDump(pw);
            pw.decreaseIndent();

            pw.println();
            pw.println("mNetworkInfoBlockingLogs (most recent first):");
            pw.increaseIndent();
            mNetworkInfoBlockingLogs.reverseDump(pw);
            pw.decreaseIndent();

            pw.println();
            pw.println("NetTransition WakeLock activity (most recent first):");
            pw.increaseIndent();
            pw.println("total acquisitions: " + mTotalWakelockAcquisitions);
            pw.println("total releases: " + mTotalWakelockReleases);
            pw.println("cumulative duration: " + (mTotalWakelockDurationMs / 1000) + "s");
            pw.println("longest duration: " + (mMaxWakelockDurationMs / 1000) + "s");
            if (mTotalWakelockAcquisitions > mTotalWakelockReleases) {
                long duration = SystemClock.elapsedRealtime() - mLastWakeLockAcquireTimestamp;
                pw.println("currently holding WakeLock for: " + (duration / 1000) + "s");
            }
            mWakelockLogs.reverseDump(pw);

            pw.println();
            pw.println("bandwidth update requests (by uid):");
            pw.increaseIndent();
            synchronized (mBandwidthRequests) {
                for (int i = 0; i < mBandwidthRequests.size(); i++) {
                    pw.println("[" + mBandwidthRequests.keyAt(i)
                            + "]: " + mBandwidthRequests.valueAt(i));
                }
            }
            pw.decreaseIndent();
            pw.decreaseIndent();

            pw.println();
            pw.println("mOemNetworkPreferencesLogs (most recent first):");
            pw.increaseIndent();
            mOemNetworkPreferencesLogs.reverseDump(pw);
            pw.decreaseIndent();
        }

        pw.println();

        pw.println();
        pw.println("Permission Monitor:");
        pw.increaseIndent();
        mPermissionMonitor.dump(pw);
        pw.decreaseIndent();

        pw.println();
        pw.println("Legacy network activity:");
        pw.increaseIndent();
        mNetworkActivityTracker.dump(pw);
        pw.decreaseIndent();

        pw.println();
        pw.println("Multicast routing supported: " +
                (mMulticastRoutingCoordinatorService != null));
    }

    private void dumpNetworks(IndentingPrintWriter pw) {
        for (NetworkAgentInfo nai : networksSortedById()) {
            pw.println(nai.toString());
            pw.increaseIndent();
            pw.println("Nat464Xlat:");
            pw.increaseIndent();
            nai.dumpNat464Xlat(pw);
            pw.decreaseIndent();
            pw.println(String.format(
                    "Requests: REQUEST:%d LISTEN:%d BACKGROUND_REQUEST:%d total:%d",
                    nai.numForegroundNetworkRequests(),
                    nai.numNetworkRequests() - nai.numRequestNetworkRequests(),
                    nai.numBackgroundNetworkRequests(),
                    nai.numNetworkRequests()));
            pw.increaseIndent();
            for (int i = 0; i < nai.numNetworkRequests(); i++) {
                pw.println(nai.requestAt(i).toString());
            }
            pw.decreaseIndent();
            pw.println("Inactivity Timers:");
            pw.increaseIndent();
            nai.dumpInactivityTimers(pw);
            pw.decreaseIndent();
            pw.decreaseIndent();
        }
    }

    private void dumpNetworkPreferences(IndentingPrintWriter pw) {
        if (!mProfileNetworkPreferences.isEmpty()) {
            pw.println("Profile preferences:");
            pw.increaseIndent();
            pw.println(mProfileNetworkPreferences);
            pw.decreaseIndent();
        }
        if (!mOemNetworkPreferences.isEmpty()) {
            pw.println("OEM preferences:");
            pw.increaseIndent();
            pw.println(mOemNetworkPreferences);
            pw.decreaseIndent();
        }
        if (!mMobileDataPreferredUids.isEmpty()) {
            pw.println("Mobile data preferred UIDs:");
            pw.increaseIndent();
            pw.println(mMobileDataPreferredUids);
            pw.decreaseIndent();
        }

        pw.println("Default requests:");
        pw.increaseIndent();
        dumpPerAppDefaultRequests(pw);
        pw.decreaseIndent();
    }

    private void dumpPerAppDefaultRequests(IndentingPrintWriter pw) {
        for (final NetworkRequestInfo defaultRequest : mDefaultNetworkRequests) {
            if (mDefaultRequest == defaultRequest) {
                continue;
            }

            final NetworkAgentInfo satisfier = defaultRequest.getSatisfier();
            final String networkOutput;
            if (null == satisfier) {
                networkOutput = "null";
            } else if (mNoServiceNetwork.equals(satisfier)) {
                networkOutput = "no service network";
            } else {
                networkOutput = String.valueOf(satisfier.network.netId);
            }
            final String asUidString = (defaultRequest.mAsUid == defaultRequest.mUid)
                    ? "" : " asUid: " + defaultRequest.mAsUid;
            final String requestInfo = "Request: [uid/pid:" + defaultRequest.mUid + "/"
                    + defaultRequest.mPid + asUidString + "]";
            final String satisfierOutput = "Satisfier: [" + networkOutput + "]"
                    + " Preference order: " + defaultRequest.mPreferenceOrder
                    + " Tracked UIDs: " + defaultRequest.getUids();
            pw.println(requestInfo + " - " + satisfierOutput);
        }
    }

    private void dumpNetworkRequests(IndentingPrintWriter pw) {
        NetworkRequestInfo[] infos = null;
        while (infos == null) {
            try {
                infos = requestsSortedById();
            } catch (ConcurrentModificationException e) {
                // mNetworkRequests should only be accessed from handler thread, except dump().
                // As dump() is never called in normal usage, it would be needlessly expensive
                // to lock the collection only for its benefit. Instead, retry getting the
                // requests if ConcurrentModificationException is thrown during dump().
            }
        }
        for (NetworkRequestInfo nri : infos) {
            pw.println(nri.toString());
        }
    }

    private void dumpTrafficController(IndentingPrintWriter pw, final FileDescriptor fd,
            boolean verbose) {
        try {
            mBpfNetMaps.dump(pw, fd, verbose);
        } catch (ServiceSpecificException e) {
            pw.println(e.getMessage());
        } catch (IOException e) {
            loge("Dump BPF maps failed, " + e);
        }
    }

    private void dumpAllRequestInfoLogsToLogcat() {
        try (PrintWriter logPw = new PrintWriter(new Writer() {
            @Override
            public void write(final char[] cbuf, final int off, final int len) {
                // This method is called with 0-length and 1-length arrays for empty strings
                // or strings containing only the DEL character.
                if (len <= 1) return;
                Log.e(TAG, new String(cbuf, off, len));
            }
            @Override public void flush() {}
            @Override public void close() {}
        })) {
            mNetworkRequestInfoLogs.dump(logPw);
        }
    }

    /**
     * Return an array of all current NetworkAgentInfos sorted by network id.
     */
    private NetworkAgentInfo[] networksSortedById() {
        NetworkAgentInfo[] networks = new NetworkAgentInfo[0];
        networks = mNetworkAgentInfos.toArray(networks);
        Arrays.sort(networks, Comparator.comparingInt(nai -> nai.network.getNetId()));
        return networks;
    }

    /**
     * Return an array of all current NetworkRequest sorted by request id.
     */
    @VisibleForTesting
    NetworkRequestInfo[] requestsSortedById() {
        NetworkRequestInfo[] requests = new NetworkRequestInfo[0];
        requests = getNrisFromGlobalRequests().toArray(requests);
        // Sort the array based off the NRI containing the min requestId in its requests.
        Arrays.sort(requests,
                Comparator.comparingInt(nri -> Collections.min(nri.mRequests,
                        Comparator.comparingInt(req -> req.requestId)).requestId
                )
        );
        return requests;
    }

    private boolean isLiveNetworkAgent(NetworkAgentInfo nai, int what) {
        final NetworkAgentInfo officialNai = getNetworkAgentInfoForNetwork(nai.network);
        if (officialNai != null && officialNai.equals(nai)) return true;
        if (officialNai != null || VDBG) {
            loge(eventName(what) + " - isLiveNetworkAgent found mismatched netId: " + officialNai +
                " - " + nai);
        }
        return false;
    }

    private boolean isDisconnectRequest(Message msg) {
        if (msg.what != NetworkAgent.EVENT_NETWORK_INFO_CHANGED) return false;
        final NetworkInfo info = (NetworkInfo) ((Pair) msg.obj).second;
        return info.getState() == NetworkInfo.State.DISCONNECTED;
    }

    // must be stateless - things change under us.
    private class NetworkStateTrackerHandler extends Handler {
        public NetworkStateTrackerHandler(Looper looper) {
            super(looper);
        }

        private void maybeHandleNetworkAgentMessage(Message msg) {
            final Pair<NetworkAgentInfo, Object> arg = (Pair<NetworkAgentInfo, Object>) msg.obj;
            final NetworkAgentInfo nai = arg.first;
            if (!mNetworkAgentInfos.contains(nai)) {
                if (VDBG) {
                    log(String.format("%s from unknown NetworkAgent", eventName(msg.what)));
                }
                return;
            }

            // If the network has been destroyed, the only thing that it can do is disconnect.
            if (nai.isDestroyed() && !isDisconnectRequest(msg)) {
                return;
            }

            switch (msg.what) {
                case NetworkAgent.EVENT_NETWORK_CAPABILITIES_CHANGED: {
                    final NetworkCapabilities proposed = (NetworkCapabilities) arg.second;
                    if (!nai.respectsNcStructuralConstraints(proposed)) {
                        Log.wtf(TAG, "Agent " + nai + " violates nc structural constraints : "
                                + nai.networkCapabilities + " -> " + proposed);
                        disconnectAndDestroyNetwork(nai);
                        return;
                    }
                    nai.setDeclaredCapabilities(proposed);
                    final NetworkCapabilities sanitized =
                            nai.getDeclaredCapabilitiesSanitized(mCarrierPrivilegeAuthenticator);
                    maybeUpdateWifiRoamTimestamp(nai, sanitized);
                    updateCapabilities(nai.getScore(), nai, sanitized);
                    break;
                }
                case NetworkAgent.EVENT_NETWORK_PROPERTIES_CHANGED: {
                    LinkProperties newLp = (LinkProperties) arg.second;
                    processLinkPropertiesFromAgent(nai, newLp);
                    handleUpdateLinkProperties(nai, newLp);
                    break;
                }
                case NetworkAgent.EVENT_NETWORK_INFO_CHANGED: {
                    NetworkInfo info = (NetworkInfo) arg.second;
                    updateNetworkInfo(nai, info);
                    break;
                }
                case NetworkAgent.EVENT_LOCAL_NETWORK_CONFIG_CHANGED: {
                    final LocalNetworkConfig config = (LocalNetworkConfig) arg.second;
                    handleUpdateLocalNetworkConfig(nai, nai.localNetworkConfig, config);
                    break;
                }
                case NetworkAgent.EVENT_NETWORK_SCORE_CHANGED: {
                    updateNetworkScore(nai, (NetworkScore) arg.second);
                    break;
                }
                case NetworkAgent.EVENT_SET_EXPLICITLY_SELECTED: {
                    if (nai.everConnected()) {
                        loge("ERROR: cannot call explicitlySelected on already-connected network");
                        // Note that if the NAI had been connected, this would affect the
                        // score, and therefore would require re-mixing the score and performing
                        // a rematch.
                    }
                    nai.networkAgentConfig.explicitlySelected = toBool(msg.arg1);
                    nai.networkAgentConfig.acceptUnvalidated = toBool(msg.arg1) && toBool(msg.arg2);
                    // Mark the network as temporarily accepting partial connectivity so that it
                    // will be validated (and possibly become default) even if it only provides
                    // partial internet access. Note that if user connects to partial connectivity
                    // and choose "don't ask again", then wifi disconnected by some reasons(maybe
                    // out of wifi coverage) and if the same wifi is available again, the device
                    // will auto connect to this wifi even though the wifi has "no internet".
                    // TODO: Evaluate using a separate setting in IpMemoryStore.
                    nai.networkAgentConfig.acceptPartialConnectivity = toBool(msg.arg2);
                    break;
                }
                case NetworkAgent.EVENT_SOCKET_KEEPALIVE: {
                    mKeepaliveTracker.handleEventSocketKeepalive(nai, msg.arg1, msg.arg2);
                    break;
                }
                case NetworkAgent.EVENT_UNDERLYING_NETWORKS_CHANGED: {
                    // TODO: prevent loops, e.g., if a network declares itself as underlying.
                    final List<Network> underlying = (List<Network>) arg.second;

                    if (isLegacyLockdownNai(nai)
                            && (underlying == null || underlying.size() != 1)) {
                        Log.wtf(TAG, "Legacy lockdown VPN " + nai.toShortString()
                                + " must have exactly one underlying network: " + underlying);
                    }

                    final Network[] oldUnderlying = nai.declaredUnderlyingNetworks;
                    nai.declaredUnderlyingNetworks = (underlying != null)
                            ? underlying.toArray(new Network[0]) : null;

                    if (!Arrays.equals(oldUnderlying, nai.declaredUnderlyingNetworks)) {
                        if (DBG) {
                            log(nai.toShortString() + " changed underlying networks to "
                                    + Arrays.toString(nai.declaredUnderlyingNetworks));
                        }
                        updateCapabilitiesForNetwork(nai);
                        notifyIfacesChangedForNetworkStats();
                    }
                    break;
                }
                case NetworkAgent.EVENT_TEARDOWN_DELAY_CHANGED: {
                    if (msg.arg1 >= 0 && msg.arg1 <= NetworkAgent.MAX_TEARDOWN_DELAY_MS) {
                        nai.teardownDelayMs = msg.arg1;
                    } else {
                        logwtf(nai.toShortString() + " set invalid teardown delay " + msg.arg1);
                    }
                    break;
                }
                case NetworkAgent.EVENT_LINGER_DURATION_CHANGED: {
                    nai.setLingerDuration((int) arg.second);
                    break;
                }
                case NetworkAgent.EVENT_ADD_DSCP_POLICY: {
                    DscpPolicy policy = (DscpPolicy) arg.second;
                    if (mDscpPolicyTracker != null) {
                        mDscpPolicyTracker.addDscpPolicy(nai, policy);
                    }
                    break;
                }
                case NetworkAgent.EVENT_REMOVE_DSCP_POLICY: {
                    if (mDscpPolicyTracker != null) {
                        mDscpPolicyTracker.removeDscpPolicy(nai, (int) arg.second);
                    }
                    break;
                }
                case NetworkAgent.EVENT_REMOVE_ALL_DSCP_POLICIES: {
                    if (mDscpPolicyTracker != null) {
                        mDscpPolicyTracker.removeAllDscpPolicies(nai, true);
                    }
                    break;
                }
                case NetworkAgent.EVENT_UNREGISTER_AFTER_REPLACEMENT: {
                    if (!nai.everConnected()) {
                        Log.d(TAG, "unregisterAfterReplacement on never-connected "
                                + nai.toShortString() + ", tearing down instead");
                        teardownUnneededNetwork(nai);
                        break;
                    }

                    if (nai.isDestroyed()) {
                        Log.d(TAG, "unregisterAfterReplacement on destroyed " + nai.toShortString()
                                + ", ignoring");
                        break;
                    }

                    final int timeoutMs = (int) arg.second;
                    if (timeoutMs < 0 || timeoutMs > NetworkAgent.MAX_TEARDOWN_DELAY_MS) {
                        Log.e(TAG, "Invalid network replacement timer " + timeoutMs
                                + ", must be between 0 and " + NetworkAgent.MAX_TEARDOWN_DELAY_MS);
                    }

                    // Marking a network awaiting replacement is used to ensure that any requests
                    // satisfied by the network do not switch to another network until a
                    // replacement is available or the wait for a replacement times out.
                    // If the network is inactive (i.e., nascent or lingering), then there are no
                    // such requests, and there is no point keeping it. Just tear it down.
                    // Note that setLingerDuration(0) cannot be used to do this because the network
                    // could be nascent.
                    nai.clearInactivityState();
                    if (unneeded(nai, UnneededFor.TEARDOWN)) {
                        Log.d(TAG, nai.toShortString()
                                + " marked awaiting replacement is unneeded, tearing down instead");
                        teardownUnneededNetwork(nai);
                        break;
                    }

                    Log.d(TAG, "Marking " + nai.toShortString()
                            + " destroyed, awaiting replacement within " + timeoutMs + "ms");
                    destroyNativeNetwork(nai);

                    // TODO: deduplicate this call with the one in disconnectAndDestroyNetwork.
                    // This is not trivial because KeepaliveTracker#handleStartKeepalive does not
                    // consider the fact that the network could already have disconnected or been
                    // destroyed. Fix the code to send ERROR_INVALID_NETWORK when this happens
                    // (taking care to ensure no dup'd FD leaks), then remove the code duplication
                    // and move this code to a sensible location (destroyNativeNetwork perhaps?).
                    mKeepaliveTracker.handleStopAllKeepalives(nai,
                            SocketKeepalive.ERROR_INVALID_NETWORK);

                    nai.updateScoreForNetworkAgentUpdate();
                    // This rematch is almost certainly not going to result in any changes, because
                    // the destroyed flag is only just above the "current satisfier wins"
                    // tie-breaker. But technically anything that affects scoring should rematch.
                    rematchAllNetworksAndRequests();
                    mHandler.postDelayed(() -> nai.disconnect(), timeoutMs);
                    break;
                }
            }
        }

        private boolean maybeHandleNetworkMonitorMessage(Message msg) {
            final int netId = msg.arg2;
            final NetworkAgentInfo nai = getNetworkAgentInfoForNetId(netId);
            // If a network has already been destroyed, all NetworkMonitor updates are ignored.
            if (nai != null && nai.isDestroyed()) return true;
            switch (msg.what) {
                default:
                    return false;
                case EVENT_PROBE_STATUS_CHANGED: {
                    if (nai == null) {
                        break;
                    }
                    final int probesCompleted = ((Pair<Integer, Integer>) msg.obj).first;
                    final int probesSucceeded = ((Pair<Integer, Integer>) msg.obj).second;
                    final boolean probePrivateDnsCompleted =
                            ((probesCompleted & NETWORK_VALIDATION_PROBE_PRIVDNS) != 0);
                    final boolean privateDnsBroken =
                            ((probesSucceeded & NETWORK_VALIDATION_PROBE_PRIVDNS) == 0);
                    if (probePrivateDnsCompleted) {
                        if (nai.networkCapabilities.isPrivateDnsBroken() != privateDnsBroken) {
                            nai.networkCapabilities.setPrivateDnsBroken(privateDnsBroken);
                            updateCapabilitiesForNetwork(nai);
                        }
                        // Only show the notification when the private DNS is broken and the
                        // PRIVATE_DNS_BROKEN notification hasn't shown since last valid.
                        if (privateDnsBroken && !nai.networkAgentConfig.hasShownBroken) {
                            showNetworkNotification(nai, NotificationType.PRIVATE_DNS_BROKEN);
                        }
                        nai.networkAgentConfig.hasShownBroken = privateDnsBroken;
                    } else if (nai.networkCapabilities.isPrivateDnsBroken()) {
                        // If probePrivateDnsCompleted is false but nai.networkCapabilities says
                        // private DNS is broken, it means this network is being reevaluated.
                        // Either probing private DNS is not necessary any more or it hasn't been
                        // done yet. In either case, the networkCapabilities should be updated to
                        // reflect the new status.
                        nai.networkCapabilities.setPrivateDnsBroken(false);
                        updateCapabilitiesForNetwork(nai);
                        nai.networkAgentConfig.hasShownBroken = false;
                    }
                    break;
                }
                case EVENT_NETWORK_TESTED: {
                    final NetworkTestedResults results = (NetworkTestedResults) msg.obj;

                    if (nai == null) break;

                    handleNetworkTested(nai, results.mTestResult,
                            (results.mRedirectUrl == null) ? "" : results.mRedirectUrl);
                    break;
                }
                case EVENT_PROVISIONING_NOTIFICATION: {
                    final boolean visible = toBool(msg.arg1);
                    // If captive portal status has changed, update capabilities or disconnect.
                    if (!visible) {
                        // Only clear SIGN_IN and NETWORK_SWITCH notifications here, or else other
                        // notifications belong to the same network may be cleared unexpectedly.
                        mNotifier.clearNotification(netId, NotificationType.SIGN_IN);
                        mNotifier.clearNotification(netId, NotificationType.NETWORK_SWITCH);
                    } else {
                        if (nai == null) {
                            loge("EVENT_PROVISIONING_NOTIFICATION from unknown NetworkMonitor");
                            break;
                        }
                        if (!nai.networkAgentConfig.provisioningNotificationDisabled) {
                            mNotifier.showNotification(netId, NotificationType.SIGN_IN, nai, null,
                                    (PendingIntent) msg.obj,
                                    nai.networkAgentConfig.explicitlySelected);
                        }
                    }
                    break;
                }
                case EVENT_PRIVATE_DNS_CONFIG_RESOLVED: {
                    if (nai == null) break;

                    updatePrivateDns(nai, (PrivateDnsConfig) msg.obj);
                    break;
                }
                case EVENT_CAPPORT_DATA_CHANGED: {
                    if (nai == null) break;
                    handleCapportApiDataUpdate(nai, (CaptivePortalData) msg.obj);
                    break;
                }
            }
            return true;
        }

        private void handleNetworkTested(
                @NonNull NetworkAgentInfo nai, int testResult, @NonNull String redirectUrl) {
            final boolean valid = (testResult & NETWORK_VALIDATION_RESULT_VALID) != 0;
            final boolean partial = (testResult & NETWORK_VALIDATION_RESULT_PARTIAL) != 0;
            final boolean portal = !TextUtils.isEmpty(redirectUrl);

            // If there is any kind of working networking, then the NAI has been evaluated
            // once. {@see NetworkAgentInfo#setEvaluated}, which returns whether this is
            // the first time this ever happened.
            final boolean someConnectivity = (valid || partial || portal);
            final boolean becameEvaluated = someConnectivity && nai.setEvaluated();
            // Because of b/245893397, if the score is updated when updateCapabilities is called,
            // any callback that receives onAvailable for that rematch receives an extra caps
            // callback. To prevent that, update the score in the agent so the updates below won't
            // see an update to both caps and score at the same time.
            // TODO : fix b/245893397 and remove this.
            if (becameEvaluated) nai.updateScoreForNetworkAgentUpdate();

            if (!valid && shouldIgnoreValidationFailureAfterRoam(nai)) {
                // Assume the validation failure is due to a temporary failure after roaming
                // and ignore it. NetworkMonitor will continue to retry validation. If it
                // continues to fail after the block timeout expires, the network will be
                // marked unvalidated. If it succeeds, then validation state will not change.
                return;
            }

            final boolean wasValidated = nai.isValidated();
            final boolean wasPartial = nai.partialConnectivity();
            final boolean wasPortal = nai.captivePortalDetected();
            nai.setPartialConnectivity(partial);
            nai.setCaptivePortalDetected(portal);
            nai.updateScoreForNetworkAgentUpdate();
            final boolean partialConnectivityChanged = (wasPartial != partial);
            final boolean portalChanged = (wasPortal != portal);

            if (DBG) {
                final String logMsg = !TextUtils.isEmpty(redirectUrl)
                        ? " with redirect to " + redirectUrl
                        : "";
                log(nai.toShortString() + " validation " + (valid ? "passed" : "failed") + logMsg);
            }
            if (valid != wasValidated) {
                final FullScore oldScore = nai.getScore();
                nai.setValidated(valid);
                updateCapabilities(oldScore, nai, nai.networkCapabilities);
                if (valid) {
                    handleFreshlyValidatedNetwork(nai);
                    // Clear NO_INTERNET, PRIVATE_DNS_BROKEN, PARTIAL_CONNECTIVITY and
                    // LOST_INTERNET notifications if network becomes valid.
                    mNotifier.clearNotification(nai.network.getNetId(),
                            NotificationType.NO_INTERNET);
                    mNotifier.clearNotification(nai.network.getNetId(),
                            NotificationType.LOST_INTERNET);
                    mNotifier.clearNotification(nai.network.getNetId(),
                            NotificationType.PARTIAL_CONNECTIVITY);
                    mNotifier.clearNotification(nai.network.getNetId(),
                            NotificationType.PRIVATE_DNS_BROKEN);
                    // If network becomes valid, the hasShownBroken should be reset for
                    // that network so that the notification will be fired when the private
                    // DNS is broken again.
                    nai.networkAgentConfig.hasShownBroken = false;
                }
            } else if (partialConnectivityChanged) {
                updateCapabilitiesForNetwork(nai);
            } else if (portalChanged) {
                if (portal && ConnectivitySettingsManager.CAPTIVE_PORTAL_MODE_AVOID
                        == getCaptivePortalMode(nai)) {
                    if (DBG) log("Avoiding captive portal network: " + nai.toShortString());
                    nai.onPreventAutomaticReconnect();
                    teardownUnneededNetwork(nai);
                    return;
                } else {
                    updateCapabilitiesForNetwork(nai);
                }
            } else if (becameEvaluated) {
                // If valid or partial connectivity changed, updateCapabilities* has
                // done the rematch.
                rematchAllNetworksAndRequests();
            }
            updateInetCondition(nai);

            // Let the NetworkAgent know the state of its network
            // TODO: Evaluate to update partial connectivity to status to NetworkAgent.
            nai.onValidationStatusChanged(
                    valid ? NetworkAgent.VALID_NETWORK : NetworkAgent.INVALID_NETWORK,
                    redirectUrl);

            // If NetworkMonitor detects partial connectivity before
            // EVENT_INITIAL_EVALUATION_TIMEOUT arrives, show the partial connectivity notification
            // immediately. Re-notify partial connectivity silently if no internet
            // notification already there.
            if (!wasPartial && nai.partialConnectivity()) {
                // Remove delayed message if there is a pending message.
                mHandler.removeMessages(EVENT_INITIAL_EVALUATION_TIMEOUT, nai.network);
                handleInitialEvaluationTimeout(nai.network);
            }

            if (wasValidated && !nai.isValidated()) {
                handleNetworkUnvalidated(nai);
            }
        }

        private int getCaptivePortalMode(@NonNull NetworkAgentInfo nai) {
            if (nai.networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) &&
                    mContext.getPackageManager().hasSystemFeature(FEATURE_WATCH)) {
                // Do not avoid captive portal when network is wear proxy.
                return ConnectivitySettingsManager.CAPTIVE_PORTAL_MODE_PROMPT;
            }

            return Settings.Global.getInt(mContext.getContentResolver(),
                    ConnectivitySettingsManager.CAPTIVE_PORTAL_MODE,
                    ConnectivitySettingsManager.CAPTIVE_PORTAL_MODE_PROMPT);
        }

        private boolean maybeHandleNetworkAgentInfoMessage(Message msg) {
            switch (msg.what) {
                default:
                    return false;
                case NetworkAgentInfo.EVENT_NETWORK_LINGER_COMPLETE: {
                    NetworkAgentInfo nai = (NetworkAgentInfo) msg.obj;
                    if (nai != null && isLiveNetworkAgent(nai, msg.what)) {
                        handleLingerComplete(nai);
                    }
                    break;
                }
                case NetworkAgentInfo.EVENT_AGENT_REGISTERED: {
                    handleNetworkAgentRegistered(msg);
                    break;
                }
                case NetworkAgentInfo.EVENT_AGENT_DISCONNECTED: {
                    handleNetworkAgentDisconnected(msg);
                    break;
                }
            }
            return true;
        }

        @Override
        public void handleMessage(@NonNull Message msg) {
            if (!maybeHandleNetworkMonitorMessage(msg)
                    && !maybeHandleNetworkAgentInfoMessage(msg)) {
                maybeHandleNetworkAgentMessage(msg);
            }
        }
    }

    private class NetworkMonitorCallbacks extends INetworkMonitorCallbacks.Stub {
        private final int mNetId;
        private final AutodestructReference<NetworkAgentInfo> mNai;

        private NetworkMonitorCallbacks(NetworkAgentInfo nai) {
            mNetId = nai.network.getNetId();
            mNai = new AutodestructReference<>(nai);
        }

        @Override
        public void onNetworkMonitorCreated(INetworkMonitor networkMonitor) {
            mHandler.sendMessage(mHandler.obtainMessage(EVENT_REGISTER_NETWORK_AGENT,
                    new Pair<>(mNai.getAndDestroy(), networkMonitor)));
        }

        @Override
        public void notifyNetworkTested(int testResult, @Nullable String redirectUrl) {
            // Legacy version of notifyNetworkTestedWithExtras.
            // Would only be called if the system has a NetworkStack module older than the
            // framework, which does not happen in practice.
            Log.wtf(TAG, "Deprecated notifyNetworkTested called: no action taken");
        }

        @Override
        public void notifyNetworkTestedWithExtras(NetworkTestResultParcelable p) {
            // Notify mTrackerHandler and mConnectivityDiagnosticsHandler of the event. Both use
            // the same looper so messages will be processed in sequence.
            final Message msg = mTrackerHandler.obtainMessage(
                    EVENT_NETWORK_TESTED,
                    0, mNetId,
                    new NetworkTestedResults(
                            mNetId, p.result, p.timestampMillis, p.redirectUrl));
            mTrackerHandler.sendMessage(msg);

            // Invoke ConnectivityReport generation for this Network test event.
            final NetworkAgentInfo nai = getNetworkAgentInfoForNetId(mNetId);
            if (nai == null) return;

            // NetworkMonitor reports the network validation result as a bitmask while
            // ConnectivityDiagnostics treats this value as an int. Convert the result to a single
            // logical value for ConnectivityDiagnostics.
            final int validationResult = networkMonitorValidationResultToConnDiagsValidationResult(
                    p.result);

            final PersistableBundle extras = new PersistableBundle();
            extras.putInt(KEY_NETWORK_VALIDATION_RESULT, validationResult);
            extras.putInt(KEY_NETWORK_PROBES_SUCCEEDED_BITMASK, p.probesSucceeded);
            extras.putInt(KEY_NETWORK_PROBES_ATTEMPTED_BITMASK, p.probesAttempted);

            ConnectivityReportEvent reportEvent =
                    new ConnectivityReportEvent(p.timestampMillis, nai, extras);
            final Message m = mConnectivityDiagnosticsHandler.obtainMessage(
                    ConnectivityDiagnosticsHandler.CMD_SEND_CONNECTIVITY_REPORT, reportEvent);
            mConnectivityDiagnosticsHandler.sendMessage(m);
        }

        @Override
        public void notifyPrivateDnsConfigResolved(PrivateDnsConfigParcel config) {
            mTrackerHandler.sendMessage(mTrackerHandler.obtainMessage(
                    EVENT_PRIVATE_DNS_CONFIG_RESOLVED,
                    0, mNetId, PrivateDnsConfig.fromParcel(config)));
        }

        @Override
        public void notifyProbeStatusChanged(int probesCompleted, int probesSucceeded) {
            mTrackerHandler.sendMessage(mTrackerHandler.obtainMessage(
                    EVENT_PROBE_STATUS_CHANGED,
                    0, mNetId, new Pair<>(probesCompleted, probesSucceeded)));
        }

        @Override
        public void notifyCaptivePortalDataChanged(CaptivePortalData data) {
            mTrackerHandler.sendMessage(mTrackerHandler.obtainMessage(
                    EVENT_CAPPORT_DATA_CHANGED,
                    0, mNetId, data));
        }

        @Override
        public void showProvisioningNotification(String action, String packageName) {
            final Intent intent = new Intent(action);
            intent.setPackage(packageName);

            final PendingIntent pendingIntent;
            // Only the system server can register notifications with package "android"
            final long token = Binder.clearCallingIdentity();
            try {
                pendingIntent = PendingIntent.getBroadcast(
                        mContext,
                        0 /* requestCode */,
                        intent,
                        PendingIntent.FLAG_IMMUTABLE);
            } finally {
                Binder.restoreCallingIdentity(token);
            }
            mTrackerHandler.sendMessage(mTrackerHandler.obtainMessage(
                    EVENT_PROVISIONING_NOTIFICATION, PROVISIONING_NOTIFICATION_SHOW,
                    mNetId, pendingIntent));
        }

        @Override
        public void hideProvisioningNotification() {
            mTrackerHandler.sendMessage(mTrackerHandler.obtainMessage(
                    EVENT_PROVISIONING_NOTIFICATION, PROVISIONING_NOTIFICATION_HIDE, mNetId));
        }

        @Override
        public void notifyDataStallSuspected(DataStallReportParcelable p) {
            ConnectivityService.this.notifyDataStallSuspected(p, mNetId);
        }

        @Override
        public int getInterfaceVersion() {
            return this.VERSION;
        }

        @Override
        public String getInterfaceHash() {
            return this.HASH;
        }
    }

    /**
     * Converts the given NetworkMonitor-specific validation result bitmask to a
     * ConnectivityDiagnostics-specific validation result int.
     */
    private int networkMonitorValidationResultToConnDiagsValidationResult(int validationResult) {
        if ((validationResult & NETWORK_VALIDATION_RESULT_SKIPPED) != 0) {
            return ConnectivityReport.NETWORK_VALIDATION_RESULT_SKIPPED;
        }
        if ((validationResult & NETWORK_VALIDATION_RESULT_VALID) == 0) {
            return ConnectivityReport.NETWORK_VALIDATION_RESULT_INVALID;
        }
        return (validationResult & NETWORK_VALIDATION_RESULT_PARTIAL) != 0
                ? ConnectivityReport.NETWORK_VALIDATION_RESULT_PARTIALLY_VALID
                : ConnectivityReport.NETWORK_VALIDATION_RESULT_VALID;
    }

    private void notifyDataStallSuspected(DataStallReportParcelable p, int netId) {
        log("Data stall detected with methods: " + p.detectionMethod);

        final PersistableBundle extras = new PersistableBundle();
        int detectionMethod = 0;
        if (hasDataStallDetectionMethod(p, DETECTION_METHOD_DNS_EVENTS)) {
            extras.putInt(KEY_DNS_CONSECUTIVE_TIMEOUTS, p.dnsConsecutiveTimeouts);
            detectionMethod |= DETECTION_METHOD_DNS_EVENTS;
        }
        if (hasDataStallDetectionMethod(p, DETECTION_METHOD_TCP_METRICS)) {
            extras.putInt(KEY_TCP_PACKET_FAIL_RATE, p.tcpPacketFailRate);
            extras.putInt(KEY_TCP_METRICS_COLLECTION_PERIOD_MILLIS,
                    p.tcpMetricsCollectionPeriodMillis);
            detectionMethod |= DETECTION_METHOD_TCP_METRICS;
        }

        final Message msg = mConnectivityDiagnosticsHandler.obtainMessage(
                ConnectivityDiagnosticsHandler.EVENT_DATA_STALL_SUSPECTED, detectionMethod, netId,
                new Pair<>(p.timestampMillis, extras));

        // NetworkStateTrackerHandler currently doesn't take any actions based on data
        // stalls so send the message directly to ConnectivityDiagnosticsHandler and avoid
        // the cost of going through two handlers.
        mConnectivityDiagnosticsHandler.sendMessage(msg);
    }

    private boolean hasDataStallDetectionMethod(DataStallReportParcelable p, int detectionMethod) {
        return (p.detectionMethod & detectionMethod) != 0;
    }

    private boolean networkRequiresPrivateDnsValidation(NetworkAgentInfo nai) {
        return isPrivateDnsValidationRequired(nai.networkCapabilities);
    }

    private void handleFreshlyValidatedNetwork(NetworkAgentInfo nai) {
        if (nai == null) return;
        // If the Private DNS mode is opportunistic, reprogram the DNS servers
        // in order to restart a validation pass from within netd.
        final PrivateDnsConfig cfg = mDnsManager.getPrivateDnsConfig();
        if (cfg.inOpportunisticMode()) {
            updateDnses(nai.linkProperties, null, nai.network.getNetId());
        }
    }

    private void handlePrivateDnsSettingsChanged() {
        final PrivateDnsConfig cfg = mDnsManager.getPrivateDnsConfig();

        for (NetworkAgentInfo nai : mNetworkAgentInfos) {
            handlePerNetworkPrivateDnsConfig(nai, cfg);
            if (networkRequiresPrivateDnsValidation(nai)) {
                handleUpdateLinkProperties(nai, new LinkProperties(nai.linkProperties));
            }
        }
    }

    private void handlePerNetworkPrivateDnsConfig(NetworkAgentInfo nai, PrivateDnsConfig cfg) {
        // Private DNS only ever applies to networks that might provide
        // Internet access and therefore also require validation.
        if (!networkRequiresPrivateDnsValidation(nai)) return;

        // Notify the NetworkAgentInfo/NetworkMonitor in case NetworkMonitor needs to cancel or
        // schedule DNS resolutions. If a DNS resolution is required the
        // result will be sent back to us.
        nai.networkMonitor().notifyPrivateDnsChanged(cfg.toParcel());

        // With Private DNS bypass support, we can proceed to update the
        // Private DNS config immediately, even if we're in strict mode
        // and have not yet resolved the provider name into a set of IPs.
        updatePrivateDns(nai, cfg);
    }

    private void updatePrivateDns(NetworkAgentInfo nai, PrivateDnsConfig newCfg) {
        mDnsManager.updatePrivateDns(nai.network, newCfg);
        updateDnses(nai.linkProperties, null, nai.network.getNetId());
    }

    private void handlePrivateDnsValidationUpdate(PrivateDnsValidationUpdate update) {
        NetworkAgentInfo nai = getNetworkAgentInfoForNetId(update.netId);
        if (nai == null) {
            return;
        }
        mDnsManager.updatePrivateDnsValidation(update);
        handleUpdateLinkProperties(nai, new LinkProperties(nai.linkProperties));
    }

    private void handleNat64PrefixEvent(int netId, int operation, String prefixAddress,
            int prefixLength) {
        NetworkAgentInfo nai = mNetworkForNetId.get(netId);
        if (nai == null) return;

        log(String.format("NAT64 prefix changed on netId %d: operation=%d, %s/%d",
                netId, operation, prefixAddress, prefixLength));

        IpPrefix prefix = null;
        if (operation == IDnsResolverUnsolicitedEventListener.PREFIX_OPERATION_ADDED) {
            try {
                prefix = new IpPrefix(InetAddresses.parseNumericAddress(prefixAddress),
                        prefixLength);
            } catch (IllegalArgumentException e) {
                loge("Invalid NAT64 prefix " + prefixAddress + "/" + prefixLength);
                return;
            }
        }

        nai.clatd.setNat64PrefixFromDns(prefix);
        handleUpdateLinkProperties(nai, new LinkProperties(nai.linkProperties));
    }

    private void handleCapportApiDataUpdate(@NonNull final NetworkAgentInfo nai,
            @Nullable final CaptivePortalData data) {
        nai.capportApiData = data;
        // CaptivePortalData will be merged into LinkProperties from NetworkAgentInfo
        handleUpdateLinkProperties(nai, new LinkProperties(nai.linkProperties));
    }

    /**
     * Updates the inactivity state from the network requests inside the NAI.
     * @param nai the agent info to update
     * @param now the timestamp of the event causing this update
     * @return whether the network was inactive as a result of this update
     */
    private boolean updateInactivityState(@NonNull final NetworkAgentInfo nai, final long now) {
        // 1. Update the inactivity timer. If it's changed, reschedule or cancel the alarm.
        // 2. If the network was inactive and there are now requests, unset inactive.
        // 3. If this network is unneeded (which implies it is not lingering), and there is at least
        //    one lingered request, set inactive.
        nai.updateInactivityTimer();
        if (nai.isInactive() && nai.numForegroundNetworkRequests() > 0) {
            if (DBG) log("Unsetting inactive " + nai.toShortString());
            nai.unsetInactive();
            logNetworkEvent(nai, NetworkEvent.NETWORK_UNLINGER);
        } else if (unneeded(nai, UnneededFor.LINGER) && nai.getInactivityExpiry() > 0) {
            if (DBG) {
                final int lingerTime = (int) (nai.getInactivityExpiry() - now);
                log("Setting inactive " + nai.toShortString() + " for " + lingerTime + "ms");
            }
            nai.setInactive();
            logNetworkEvent(nai, NetworkEvent.NETWORK_LINGER);
            return true;
        }
        return false;
    }

    private void handleNetworkAgentRegistered(Message msg) {
        final NetworkAgentInfo nai = (NetworkAgentInfo) msg.obj;
        if (!mNetworkAgentInfos.contains(nai)) {
            return;
        }

        if (msg.arg1 == NetworkAgentInfo.ARG_AGENT_SUCCESS) {
            if (VDBG) log("NetworkAgent registered");
        } else {
            loge("Error connecting NetworkAgent");
            mNetworkAgentInfos.remove(nai);
            if (nai != null) {
                final boolean wasDefault = isDefaultNetwork(nai);
                synchronized (mNetworkForNetId) {
                    mNetworkForNetId.remove(nai.network.getNetId());
                    mNetIdToAllowlist.remove(nai.network.getNetId());
                    mNetIdToDisallowedUids.remove(nai.network.getNetId());
                }
                updateDisallowedUidsForNetwork(nai);
                mNetIdManager.releaseNetId(nai.network.getNetId());
                // Just in case.
                mLegacyTypeTracker.remove(nai, wasDefault);
            }
        }
    }

    @VisibleForTesting
    protected static boolean shouldCreateNetworksImmediately() {
        // The feature of creating the networks immediately was slated for U, but race conditions
        // detected late required this was flagged off.
        // TODO : enable this in a Mainline update or in V, and re-enable the test for this
        // in NetworkAgentTest.
        return false;
    }

    private static boolean shouldCreateNativeNetwork(@NonNull NetworkAgentInfo nai,
            @NonNull NetworkInfo.State state) {
        if (nai.isCreated()) return false;
        if (state == NetworkInfo.State.CONNECTED) return true;
        if (state != NetworkInfo.State.CONNECTING) {
            // TODO: throw if no WTFs are observed in the field.
            if (shouldCreateNetworksImmediately()) {
                Log.wtf(TAG, "Uncreated network in invalid state: " + state);
            }
            return false;
        }
        return nai.isVPN() || shouldCreateNetworksImmediately();
    }

    private static boolean shouldDestroyNativeNetwork(@NonNull NetworkAgentInfo nai) {
        return nai.isCreated() && !nai.isDestroyed();
    }

    @VisibleForTesting
    boolean shouldIgnoreValidationFailureAfterRoam(NetworkAgentInfo nai) {
        // T+ devices should use unregisterAfterReplacement.
        if (mDeps.isAtLeastT()) return false;

        // If the network never roamed, return false. The check below is not sufficient if time
        // since boot is less than blockTimeOut, though that's extremely unlikely to happen.
        if (nai.lastRoamTime == 0) return false;

        final long blockTimeOut = Long.valueOf(mResources.get().getInteger(
                R.integer.config_validationFailureAfterRoamIgnoreTimeMillis));
        if (blockTimeOut <= MAX_VALIDATION_IGNORE_AFTER_ROAM_TIME_MS
                && blockTimeOut >= 0) {
            final long currentTimeMs = SystemClock.elapsedRealtime();
            long timeSinceLastRoam = currentTimeMs - nai.lastRoamTime;
            if (timeSinceLastRoam <= blockTimeOut) {
                log ("blocked because only " + timeSinceLastRoam + "ms after roam");
                return true;
            }
        }
        return false;
    }

    private void handleNetworkAgentDisconnected(Message msg) {
        NetworkAgentInfo nai = (NetworkAgentInfo) msg.obj;
        disconnectAndDestroyNetwork(nai);
    }

    // Destroys a network, remove references to it from the internal state managed by
    // ConnectivityService, free its interfaces and clean up.
    // Must be called on the Handler thread.
    private void disconnectAndDestroyNetwork(NetworkAgentInfo nai) {
        ensureRunningOnConnectivityServiceThread();

        if (!mNetworkAgentInfos.contains(nai)) return;

        if (DBG) {
            log(nai.toShortString() + " disconnected, was satisfying " + nai.numNetworkRequests());
        }
        // Clear all notifications of this network.
        mNotifier.clearNotification(nai.network.getNetId());
        // A network agent has disconnected.
        // TODO - if we move the logic to the network agent (have them disconnect
        // because they lost all their requests or because their score isn't good)
        // then they would disconnect organically, report their new state and then
        // disconnect the channel.
        if (nai.networkInfo.isConnected() || nai.networkInfo.isSuspended()) {
            nai.networkInfo.setDetailedState(NetworkInfo.DetailedState.DISCONNECTED,
                    null, null);
        }
        final boolean wasDefault = isDefaultNetwork(nai);
        if (wasDefault) {
            mDefaultInetConditionPublished = 0;
        }
        if (mTrackMultiNetworkActivities) {
            // If trackMultiNetworkActivities is disabled, ActivityTracker removes idleTimer when
            // the network becomes no longer the default network.
            mNetworkActivityTracker.removeDataActivityTracking(nai);
        }
        notifyIfacesChangedForNetworkStats();
        // If this was a local network forwarded to some upstream, or if some local network was
        // forwarded to this nai, then disable forwarding rules now.
        maybeDisableForwardRulesForDisconnectingNai(nai, true /* sendCallbacks */);
        // If this is a local network with an upstream selector, remove the associated network
        // request.
        if (nai.isLocalNetwork()) {
            final NetworkRequest selector = nai.localNetworkConfig.getUpstreamSelector();
            if (null != selector) {
                handleRemoveNetworkRequest(mNetworkRequests.get(selector));
            }
        }
        // TODO - we shouldn't send CALLBACK_LOST to requests that can be satisfied
        // by other networks that are already connected. Perhaps that can be done by
        // sending all CALLBACK_LOST messages (for requests, not listens) at the end
        // of rematchAllNetworksAndRequests
        notifyNetworkCallbacks(nai, ConnectivityManager.CALLBACK_LOST);
        mKeepaliveTracker.handleStopAllKeepalives(nai, SocketKeepalive.ERROR_INVALID_NETWORK);

        mQosCallbackTracker.handleNetworkReleased(nai.network);
        for (String iface : nai.linkProperties.getAllInterfaceNames()) {
            // Disable wakeup packet monitoring for each interface.
            wakeupModifyInterface(iface, nai, false);
        }
        nai.networkMonitor().notifyNetworkDisconnected();
        mNetworkAgentInfos.remove(nai);
        nai.clatd.update();
        synchronized (mNetworkForNetId) {
            // Remove the NetworkAgent, but don't mark the netId as
            // available until we've told netd to delete it below.
            mNetworkForNetId.remove(nai.network.getNetId());
            mNetIdToAllowlist.remove(nai.network.getNetId());
            mNetIdToDisallowedUids.remove(nai.network.getNetId());
        }
        updateDisallowedUidsForNetwork(nai);
        propagateUnderlyingNetworkCapabilities(nai.network);
        // Update allowed network lists in netd. This should be called after removing nai
        // from mNetworkAgentInfos.
        updateProfileAllowedNetworks();
        // Remove all previously satisfied requests.
        for (int i = 0; i < nai.numNetworkRequests(); i++) {
            final NetworkRequest request = nai.requestAt(i);
            final NetworkRequestInfo nri = mNetworkRequests.get(request);
            final NetworkAgentInfo currentNetwork = nri.getSatisfier();
            if (currentNetwork != null
                    && currentNetwork.network.getNetId() == nai.network.getNetId()) {
                // uid rules for this network will be removed in destroyNativeNetwork(nai).
                // TODO : setting the satisfier is in fact the job of the rematch. Teach the
                // rematch not to keep disconnected agents instead of setting it here ; this
                // will also allow removing updating the offers below.
                nri.setSatisfier(null, null);
                for (final NetworkOfferInfo noi : mNetworkOffers) {
                    informOffer(nri, noi.offer, mNetworkRanker);
                }

                if (mDefaultRequest == nri) {
                    mNetworkActivityTracker.updateDefaultNetwork(null /* newNetwork */, nai);
                    maybeClosePendingFrozenSockets(null /* newNetwork */, nai);
                    ensureNetworkTransitionWakelock(nai.toShortString());
                }
            }
        }
        nai.clearInactivityState();
        // TODO: mLegacyTypeTracker.remove seems redundant given there's a full rematch right after.
        //  Currently, deleting it breaks tests that check for the default network disconnecting.
        //  Find out why, fix the rematch code, and delete this.
        mLegacyTypeTracker.remove(nai, wasDefault);
        rematchAllNetworksAndRequests();
        mLingerMonitor.noteDisconnect(nai);

        if (null == getDefaultNetwork() && nai.linkProperties.getHttpProxy() != null) {
            // The obvious place to do this would be in makeDefault(), however makeDefault() is
            // not called by the rematch in this case. This is because the code above unset
            // this network from the default request's satisfier, and that is what the rematch
            // is using as its source data to know what the old satisfier was. So as far as the
            // rematch above is concerned, the old default network was null.
            // Therefore if there is no new default, the default network was null and is still
            // null, thus there was no change so makeDefault() is not called. So if the old
            // network had a proxy and there is no new default, the proxy tracker should be told
            // that there is no longer a default proxy.
            // Strictly speaking this is not essential because having a proxy setting when
            // there is no network is harmless, but it's still counter-intuitive so reset to null.
            mProxyTracker.setDefaultProxy(null);
        }

        // Immediate teardown.
        if (nai.teardownDelayMs == 0) {
            destroyNetwork(nai);
            return;
        }

        // Delayed teardown.
        if (nai.isCreated()) {
            try {
                mNetd.networkSetPermissionForNetwork(nai.network.netId, INetd.PERMISSION_SYSTEM);
            } catch (RemoteException e) {
                Log.d(TAG, "Error marking network restricted during teardown: ", e);
            }
        }
        mHandler.postDelayed(() -> destroyNetwork(nai), nai.teardownDelayMs);
    }

    private void destroyNetwork(NetworkAgentInfo nai) {
        if (shouldDestroyNativeNetwork(nai)) {
            // Tell netd to clean up the configuration for this network
            // (routing rules, DNS, etc).
            // This may be slow as it requires a lot of netd shelling out to ip and
            // ip[6]tables to flush routes and remove the incoming packet mark rule, so do it
            // after we've rematched networks with requests (which might change the default
            // network or service a new request from an app), so network traffic isn't interrupted
            // for an unnecessarily long time.
            destroyNativeNetwork(nai);
        }
        if (!nai.isCreated() && !mDeps.isAtLeastT()) {
            // Backwards compatibility: send onNetworkDestroyed even if network was never created.
            // This can never run if the code above runs because shouldDestroyNativeNetwork is
            // false if the network was never created.
            // TODO: delete when S is no longer supported.
            nai.onNetworkDestroyed();
        }
        mNetIdManager.releaseNetId(nai.network.getNetId());
    }

    private void maybeDisableForwardRulesForDisconnectingNai(
            @NonNull final NetworkAgentInfo disconnecting, final boolean sendCallbacks) {
        // Step 1 : maybe this network was the upstream for one or more local networks.
        for (final NetworkAgentInfo local : mNetworkAgentInfos) {
            if (!local.isLocalNetwork()) continue;
            final NetworkRequest selector = local.localNetworkConfig.getUpstreamSelector();
            if (null == selector) continue;
            final NetworkRequestInfo nri = mNetworkRequests.get(selector);
            // null == nri can happen while disconnecting a network, because destroyNetwork() is
            // called after removing all associated NRIs from mNetworkRequests.
            if (null == nri) continue;
            final NetworkAgentInfo satisfier = nri.getSatisfier();
            if (disconnecting != satisfier) continue;
            removeLocalNetworkUpstream(local, disconnecting);
            // Set the satisfier to null immediately so that the LOCAL_NETWORK_CHANGED callback
            // correctly contains null as an upstream.
            if (sendCallbacks) {
                nri.setSatisfier(null, null);
                notifyNetworkCallbacks(local,
                        ConnectivityManager.CALLBACK_LOCAL_NETWORK_INFO_CHANGED);
            }
        }

        // Step 2 : maybe this is a local network that had an upstream.
        if (!disconnecting.isLocalNetwork()) return;
        final NetworkRequest selector = disconnecting.localNetworkConfig.getUpstreamSelector();
        if (null == selector) return;
        final NetworkRequestInfo nri = mNetworkRequests.get(selector);
        // As above null == nri can happen while disconnecting a network, because destroyNetwork()
        // is called after removing all associated NRIs from mNetworkRequests.
        if (null == nri) return;
        final NetworkAgentInfo satisfier = nri.getSatisfier();
        if (null == satisfier) return;
        removeLocalNetworkUpstream(disconnecting, satisfier);
    }

    private void removeLocalNetworkUpstream(@NonNull final NetworkAgentInfo localAgent,
            @NonNull final NetworkAgentInfo upstream) {
        try {
            final String localNetworkInterfaceName = localAgent.linkProperties.getInterfaceName();
            final String upstreamNetworkInterfaceName = upstream.linkProperties.getInterfaceName();
            mRoutingCoordinatorService.removeInterfaceForward(
                    localNetworkInterfaceName,
                    upstreamNetworkInterfaceName);
            disableMulticastRouting(localNetworkInterfaceName, upstreamNetworkInterfaceName);
        } catch (RemoteException e) {
            loge("Couldn't remove interface forward for "
                    + localAgent.linkProperties.getInterfaceName() + " to "
                    + upstream.linkProperties.getInterfaceName() + " while disconnecting");
        }
    }

    private boolean createNativeNetwork(@NonNull NetworkAgentInfo nai) {
        try {
            // This should never fail.  Specifying an already in use NetID will cause failure.
            final NativeNetworkConfig config;
            if (nai.isVPN()) {
                if (getVpnType(nai) == VpnManager.TYPE_VPN_NONE) {
                    Log.wtf(TAG, "Unable to get VPN type from network " + nai.toShortString());
                    return false;
                }
                config = new NativeNetworkConfig(nai.network.getNetId(), NativeNetworkType.VIRTUAL,
                        INetd.PERMISSION_NONE,
                        !nai.networkAgentConfig.allowBypass /* secure */,
                        getVpnType(nai), nai.networkAgentConfig.excludeLocalRouteVpn);
            } else {
                config = new NativeNetworkConfig(nai.network.getNetId(),
                        nai.isLocalNetwork() ? NativeNetworkType.PHYSICAL_LOCAL
                                : NativeNetworkType.PHYSICAL,
                        getNetworkPermission(nai.networkCapabilities),
                        false /* secure */,
                        VpnManager.TYPE_VPN_NONE,
                        false /* excludeLocalRoutes */);
            }
            mNetd.networkCreate(config);
            mDnsResolver.createNetworkCache(nai.network.getNetId());
            mDnsManager.updateCapabilitiesForNetwork(nai.network.getNetId(),
                    nai.networkCapabilities);
            return true;
        } catch (RemoteException | ServiceSpecificException e) {
            loge("Error creating network " + nai.toShortString() + ": " + e.getMessage());
            return false;
        }
    }

    private void destroyNativeNetwork(@NonNull NetworkAgentInfo nai) {
        if (mDscpPolicyTracker != null) {
            mDscpPolicyTracker.removeAllDscpPolicies(nai, false);
        }
        // Remove any forwarding rules to and from the interface for this network, since
        // the interface is going to go away. Don't send the callbacks however ; if the network
        // was is being disconnected the callbacks have already been sent, and if it is being
        // destroyed pending replacement they will be sent when it is disconnected.
        maybeDisableForwardRulesForDisconnectingNai(nai, false /* sendCallbacks */);
        updateIngressToVpnAddressFiltering(null, nai.linkProperties, nai);
        try {
            mNetd.networkDestroy(nai.network.getNetId());
        } catch (RemoteException | ServiceSpecificException e) {
            loge("Exception destroying network(networkDestroy): " + e);
        }
        try {
            mDnsResolver.destroyNetworkCache(nai.network.getNetId());
        } catch (RemoteException | ServiceSpecificException e) {
            loge("Exception destroying network: " + e);
        }
        // TODO: defer calling this until the network is removed from mNetworkAgentInfos.
        // Otherwise, a private DNS configuration update for a destroyed network, or one that never
        // gets created, could add data to DnsManager data structures that will never get deleted.
        mDnsManager.removeNetwork(nai.network);

        // clean up tc police filters on interface.
        if (nai.everConnected() && canNetworkBeRateLimited(nai) && mIngressRateLimit >= 0) {
            mDeps.disableIngressRateLimit(nai.linkProperties.getInterfaceName());
        }

        nai.setDestroyed();
        nai.onNetworkDestroyed();
    }

    // If this method proves to be too slow then we can maintain a separate
    // pendingIntent => NetworkRequestInfo map.
    // This method assumes that every non-null PendingIntent maps to exactly 1 NetworkRequestInfo.
    private NetworkRequestInfo findExistingNetworkRequestInfo(PendingIntent pendingIntent) {
        for (Map.Entry<NetworkRequest, NetworkRequestInfo> entry : mNetworkRequests.entrySet()) {
            PendingIntent existingPendingIntent = entry.getValue().mPendingIntent;
            if (existingPendingIntent != null &&
                    mDeps.intentFilterEquals(existingPendingIntent, pendingIntent)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private void checkNrisConsistency(final NetworkRequestInfo nri) {
        if (mDeps.isAtLeastT()) {
            for (final NetworkRequestInfo n : mNetworkRequests.values()) {
                if (n.mBinder != null && n.mBinder == nri.mBinder) {
                    // Temporary help to debug b/194394697 ; TODO : remove this function when the
                    // bug is fixed.
                    dumpAllRequestInfoLogsToLogcat();
                    throw new IllegalStateException("This NRI is already registered. New : " + nri
                            + ", existing : " + n);
                }
            }
        }
    }

    private boolean hasCarrierPrivilegeForNetworkCaps(final int callingUid,
            @NonNull final NetworkCapabilities caps) {
        if (mCarrierPrivilegeAuthenticator != null) {
            return mCarrierPrivilegeAuthenticator.isCarrierServiceUidForNetworkCapabilities(
                    callingUid, caps);
        }
        return false;
    }

    private int getSubscriptionIdFromNetworkCaps(@NonNull final NetworkCapabilities caps) {
        if (mCarrierPrivilegeAuthenticator != null) {
            return mCarrierPrivilegeAuthenticator.getSubIdFromNetworkCapabilities(caps);
        }
        return SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    }

    private void handleRegisterNetworkRequestWithIntent(@NonNull final Message msg) {
        final NetworkRequestInfo nri = (NetworkRequestInfo) (msg.obj);
        // handleRegisterNetworkRequestWithIntent() doesn't apply to multilayer requests.
        ensureNotMultilayerRequest(nri, "handleRegisterNetworkRequestWithIntent");
        final NetworkRequestInfo existingRequest =
                findExistingNetworkRequestInfo(nri.mPendingIntent);
        if (existingRequest != null) { // remove the existing request.
            if (DBG) {
                log("Replacing " + existingRequest.mRequests.get(0) + " with "
                        + nri.mRequests.get(0) + " because their intents matched.");
            }
            handleReleaseNetworkRequest(existingRequest.mRequests.get(0), mDeps.getCallingUid(),
                    /* callOnUnavailable */ false);
        }
        handleRegisterNetworkRequest(nri);
    }

    private void handleRegisterNetworkRequest(@NonNull final NetworkRequestInfo nri) {
        handleRegisterNetworkRequests(Collections.singleton(nri));
    }

    private void handleRegisterNetworkRequests(@NonNull final Set<NetworkRequestInfo> nris) {
        ensureRunningOnConnectivityServiceThread();
        for (final NetworkRequestInfo nri : nris) {
            mNetworkRequestInfoLogs.log("REGISTER " + nri);
            checkNrisConsistency(nri);
            for (final NetworkRequest req : nri.mRequests) {
                mNetworkRequests.put(req, nri);
                // TODO: Consider update signal strength for other types.
                if (req.isListen()) {
                    for (final NetworkAgentInfo network : mNetworkAgentInfos) {
                        if (req.networkCapabilities.hasSignalStrength()
                                && network.satisfiesImmutableCapabilitiesOf(req)) {
                            updateSignalStrengthThresholds(network, "REGISTER", req);
                        }
                    }
                } else if (req.isRequest() && mNetworkRequestStateStatsMetrics != null) {
                    mNetworkRequestStateStatsMetrics.onNetworkRequestReceived(req);
                }
            }

            // If this NRI has a satisfier already, it is replacing an older request that
            // has been removed. Track it.
            final NetworkRequest activeRequest = nri.getActiveRequest();
            if (null != activeRequest) {
                // If there is an active request, then for sure there is a satisfier.
                nri.getSatisfier().addRequest(activeRequest);
            }
        }

        if (mFlags.noRematchAllRequestsOnRegister()) {
            rematchNetworksAndRequests(nris);
        } else {
            rematchAllNetworksAndRequests();
        }

        // Requests that have not been matched to a network will not have been sent to the
        // providers, because the old satisfier and the new satisfier are the same (null in this
        // case). Send these requests to the providers.
        for (final NetworkRequestInfo nri : nris) {
            for (final NetworkOfferInfo noi : mNetworkOffers) {
                informOffer(nri, noi.offer, mNetworkRanker);
            }
        }
    }

    private void handleReleaseNetworkRequestWithIntent(@NonNull final PendingIntent pendingIntent,
            final int callingUid) {
        final NetworkRequestInfo nri = findExistingNetworkRequestInfo(pendingIntent);
        if (nri != null) {
            // handleReleaseNetworkRequestWithIntent() paths don't apply to multilayer requests.
            ensureNotMultilayerRequest(nri, "handleReleaseNetworkRequestWithIntent");
            handleReleaseNetworkRequest(
                    nri.mRequests.get(0),
                    callingUid,
                    /* callOnUnavailable */ false);
        }
    }

    // Determines whether the network is the best (or could become the best, if it validated), for
    // none of a particular type of NetworkRequests. The type of NetworkRequests considered depends
    // on the value of reason:
    //
    // - UnneededFor.TEARDOWN: non-listen NetworkRequests. If a network is unneeded for this reason,
    //   then it should be torn down.
    // - UnneededFor.LINGER: foreground NetworkRequests. If a network is unneeded for this reason,
    //   then it should be lingered.
    private boolean unneeded(NetworkAgentInfo nai, UnneededFor reason) {
        ensureRunningOnConnectivityServiceThread();

        if (!nai.everConnected() || nai.isVPN() || nai.isInactive()
                || nai.getScore().getKeepConnectedReason() != NetworkScore.KEEP_CONNECTED_NONE) {
            return false;
        }

        final int numRequests;
        switch (reason) {
            case TEARDOWN:
                numRequests = nai.numRequestNetworkRequests();
                break;
            case LINGER:
                numRequests = nai.numForegroundNetworkRequests();
                break;
            default:
                Log.wtf(TAG, "Invalid reason. Cannot happen.");
                return true;
        }

        if (numRequests > 0) return false;

        for (NetworkRequestInfo nri : mNetworkRequests.values()) {
            if (reason == UnneededFor.LINGER
                    && !nri.isMultilayerRequest()
                    && nri.mRequests.get(0).isBackgroundRequest()) {
                // Background requests don't affect lingering.
                continue;
            }

            if (isNetworkPotentialSatisfier(nai, nri)) {
                return false;
            }
        }
        return true;
    }

    private boolean isNetworkPotentialSatisfier(
            @NonNull final NetworkAgentInfo candidate, @NonNull final NetworkRequestInfo nri) {
        // While destroyed network sometimes satisfy requests (including occasionally newly
        // satisfying requests), *potential* satisfiers are networks that might beat a current
        // champion if they validate. As such, a destroyed network is never a potential satisfier,
        // because it's never a good idea to keep a destroyed network in case it validates.
        // For example, declaring it a potential satisfier would keep an unvalidated destroyed
        // candidate after it's been replaced by another unvalidated network.
        if (candidate.isDestroyed()) return false;
        // Listen requests won't keep up a network satisfying it. If this is not a multilayer
        // request, return immediately. For multilayer requests, check to see if any of the
        // multilayer requests may have a potential satisfier.
        if (!nri.isMultilayerRequest() && (nri.mRequests.get(0).isListen()
                || nri.mRequests.get(0).isListenForBest())) {
            return false;
        }
        for (final NetworkRequest req : nri.mRequests) {
            // This multilayer listen request is satisfied therefore no further requests need to be
            // evaluated deeming this network not a potential satisfier.
            if ((req.isListen() || req.isListenForBest()) && nri.getActiveRequest() == req) {
                return false;
            }
            // As non-multilayer listen requests have already returned, the below would only happen
            // for a multilayer request therefore continue to the next request if available.
            if (req.isListen() || req.isListenForBest()) {
                continue;
            }
            // If there is hope for this network might validate and subsequently become the best
            // network for that request, then it is needed. Note that this network can't already
            // be the best for this request, or it would be the current satisfier, and therefore
            // there would be no need to call this method to find out if it is a *potential*
            // satisfier ("unneeded", the only caller, only calls this if this network currently
            // satisfies no request).
            if (candidate.satisfies(req)) {
                // As soon as a network is found that satisfies a request, return. Specifically for
                // multilayer requests, returning as soon as a NetworkAgentInfo satisfies a request
                // is important so as to not evaluate lower priority requests further in
                // nri.mRequests.
                final NetworkAgentInfo champion = req.equals(nri.getActiveRequest())
                        ? nri.getSatisfier() : null;
                // Note that this catches two important cases:
                // 1. Unvalidated cellular will not be reaped when unvalidated WiFi
                //    is currently satisfying the request.  This is desirable when
                //    cellular ends up validating but WiFi does not.
                // 2. Unvalidated WiFi will not be reaped when validated cellular
                //    is currently satisfying the request.  This is desirable when
                //    WiFi ends up validating and out scoring cellular.
                return mNetworkRanker.mightBeat(req, champion, candidate.getValidatedScoreable());
            }
        }

        return false;
    }

    private NetworkRequestInfo getNriForAppRequest(
            NetworkRequest request, int callingUid, String requestedOperation) {
        // Looking up the app passed param request in mRequests isn't possible since it may return
        // null for a request managed by a per-app default. Therefore use getNriForAppRequest() to
        // do the lookup since that will also find per-app default managed requests.
        // Additionally, this lookup needs to be relatively fast (hence the lookup optimization)
        // to avoid potential race conditions when validating a package->uid mapping when sending
        // the callback on the very low-chance that an application shuts down prior to the callback
        // being sent.
        final NetworkRequestInfo nri = mNetworkRequests.get(request) != null
                ? mNetworkRequests.get(request) : getNriForAppRequest(request);

        if (nri != null) {
            if (Process.SYSTEM_UID != callingUid && nri.mUid != callingUid) {
                log(String.format("UID %d attempted to %s for unowned request %s",
                        callingUid, requestedOperation, nri));
                return null;
            }
        }

        return nri;
    }

    private void ensureNotMultilayerRequest(@NonNull final NetworkRequestInfo nri,
            final String callingMethod) {
        if (nri.isMultilayerRequest()) {
            throw new IllegalStateException(
                    callingMethod + " does not support multilayer requests.");
        }
    }

    private void handleTimedOutNetworkRequest(@NonNull final NetworkRequestInfo nri) {
        ensureRunningOnConnectivityServiceThread();
        // handleTimedOutNetworkRequest() is part of the requestNetwork() flow which works off of a
        // single NetworkRequest and thus does not apply to multilayer requests.
        ensureNotMultilayerRequest(nri, "handleTimedOutNetworkRequest");
        if (mNetworkRequests.get(nri.mRequests.get(0)) == null) {
            return;
        }
        if (nri.isBeingSatisfied()) {
            return;
        }
        if (VDBG || (DBG && nri.mRequests.get(0).isRequest())) {
            log("releasing " + nri.mRequests.get(0) + " (timeout)");
        }
        handleRemoveNetworkRequest(nri);
        callCallbackForRequest(
                nri, null, ConnectivityManager.CALLBACK_UNAVAIL, 0);
    }

    private void handleReleaseNetworkRequest(@NonNull final NetworkRequest request,
            final int callingUid,
            final boolean callOnUnavailable) {
        final NetworkRequestInfo nri =
                getNriForAppRequest(request, callingUid, "release NetworkRequest");
        if (nri == null) {
            return;
        }
        if (VDBG || (DBG && request.isRequest())) {
            log("releasing " + request + " (release request)");
        }
        handleRemoveNetworkRequest(nri);
        if (callOnUnavailable) {
            callCallbackForRequest(nri, null, ConnectivityManager.CALLBACK_UNAVAIL, 0);
        }
    }

    private void handleRemoveNetworkRequest(@NonNull final NetworkRequestInfo nri) {
        ensureRunningOnConnectivityServiceThread();
        for (final NetworkRequest req : nri.mRequests) {
            if (null == mNetworkRequests.remove(req)) {
                logw("Attempted removal of untracked request " + req + " for nri " + nri);
                continue;
            }
            if (req.isListen()) {
                removeListenRequestFromNetworks(req);
            } else if (req.isRequest() && mNetworkRequestStateStatsMetrics != null) {
                mNetworkRequestStateStatsMetrics.onNetworkRequestRemoved(req);
            }
        }
        nri.unlinkDeathRecipient();
        if (mDefaultNetworkRequests.remove(nri)) {
            // If this request was one of the defaults, then the UID rules need to be updated
            // WARNING : if the app(s) for which this network request is the default are doing
            // traffic, this will kill their connected sockets, even if an equivalent request
            // is going to be reinstated right away ; unconnected traffic will go on the default
            // until the new default is set, which will happen very soon.
            // TODO : The only way out of this is to diff old defaults and new defaults, and only
            // remove ranges for those requests that won't have a replacement
            final NetworkAgentInfo satisfier = nri.getSatisfier();
            if (null != satisfier) {
                try {
                    mNetd.networkRemoveUidRangesParcel(new NativeUidRangeConfig(
                            satisfier.network.getNetId(),
                            toUidRangeStableParcels(nri.getUids()),
                            nri.getPreferenceOrderForNetd()));
                } catch (RemoteException e) {
                    loge("Exception setting network preference default network", e);
                }
            }
        }

        nri.mPerUidCounter.decrementCount(nri.mUid);
        mNetworkRequestInfoLogs.log("RELEASE " + nri);
        checkNrisConsistency(nri);

        if (null != nri.getActiveRequest()) {
            if (!nri.getActiveRequest().isListen()) {
                removeSatisfiedNetworkRequestFromNetwork(nri);
            }
        }

        // For all outstanding offers, cancel any of the layers of this NRI that used to be
        // needed for this offer.
        for (final NetworkOfferInfo noi : mNetworkOffers) {
            for (final NetworkRequest req : nri.mRequests) {
                if (req.isRequest() && noi.offer.neededFor(req)) {
                    noi.offer.onNetworkUnneeded(req);
                }
            }
        }
    }

    private void handleRemoveNetworkRequests(@NonNull final Set<NetworkRequestInfo> nris) {
        for (final NetworkRequestInfo nri : nris) {
            if (mDefaultRequest == nri) {
                // Make sure we never remove the default request.
                continue;
            }
            handleRemoveNetworkRequest(nri);
        }
    }

    private void removeListenRequestFromNetworks(@NonNull final NetworkRequest req) {
        // listens don't have a singular affected Network. Check all networks to see
        // if this listen request applies and remove it.
        for (final NetworkAgentInfo nai : mNetworkAgentInfos) {
            nai.removeRequest(req.requestId);
            if (req.networkCapabilities.hasSignalStrength()
                    && nai.satisfiesImmutableCapabilitiesOf(req)) {
                updateSignalStrengthThresholds(nai, "RELEASE", req);
            }
        }
    }

    /**
     * Remove a NetworkRequestInfo's satisfied request from its 'satisfier' (NetworkAgentInfo) and
     * manage the necessary upkeep (linger, teardown networks, etc.) when doing so.
     * @param nri the NetworkRequestInfo to disassociate from its current NetworkAgentInfo
     */
    private void removeSatisfiedNetworkRequestFromNetwork(@NonNull final NetworkRequestInfo nri) {
        boolean wasKept = false;
        final NetworkAgentInfo nai = nri.getSatisfier();
        if (nai != null) {
            final int requestLegacyType = nri.getActiveRequest().legacyType;
            final boolean wasBackgroundNetwork = nai.isBackgroundNetwork();
            nai.removeRequest(nri.getActiveRequest().requestId);
            if (VDBG || DDBG) {
                log(" Removing from current network " + nai.toShortString()
                        + ", leaving " + nai.numNetworkRequests() + " requests.");
            }
            // If there are still lingered requests on this network, don't tear it down,
            // but resume lingering instead.
            final long now = SystemClock.elapsedRealtime();
            if (updateInactivityState(nai, now)) {
                notifyNetworkLosing(nai, now);
            }
            if (unneeded(nai, UnneededFor.TEARDOWN)) {
                if (DBG) log("no live requests for " + nai.toShortString() + "; disconnecting");
                teardownUnneededNetwork(nai);
            } else {
                wasKept = true;
            }
            if (!wasBackgroundNetwork && nai.isBackgroundNetwork()) {
                // Went from foreground to background.
                updateCapabilitiesForNetwork(nai);
            }

            // Maintain the illusion.  When this request arrived, we might have pretended
            // that a network connected to serve it, even though the network was already
            // connected.  Now that this request has gone away, we might have to pretend
            // that the network disconnected.  LegacyTypeTracker will generate that
            // phantom disconnect for this type.
            if (requestLegacyType != TYPE_NONE) {
                boolean doRemove = true;
                if (wasKept) {
                    // check if any of the remaining requests for this network are for the
                    // same legacy type - if so, don't remove the nai
                    for (int i = 0; i < nai.numNetworkRequests(); i++) {
                        NetworkRequest otherRequest = nai.requestAt(i);
                        if (otherRequest.legacyType == requestLegacyType
                                && otherRequest.isRequest()) {
                            if (DBG) log(" still have other legacy request - leaving");
                            doRemove = false;
                        }
                    }
                }

                if (doRemove) {
                    mLegacyTypeTracker.remove(requestLegacyType, nai, false);
                }
            }
        }
    }

    private RequestInfoPerUidCounter getRequestCounter(NetworkRequestInfo nri) {
        return hasAnyPermissionOf(mContext,
                nri.mPid, nri.mUid, NetworkStack.PERMISSION_MAINLINE_NETWORK_STACK)
                ? mSystemNetworkRequestCounter : mNetworkRequestCounter;
    }

    @Override
    public void setAcceptUnvalidated(Network network, boolean accept, boolean always) {
        enforceNetworkStackSettingsOrSetup();
        mHandler.sendMessage(mHandler.obtainMessage(EVENT_SET_ACCEPT_UNVALIDATED,
                encodeBool(accept), encodeBool(always), network));
    }

    @Override
    public void setAcceptPartialConnectivity(Network network, boolean accept, boolean always) {
        enforceNetworkStackSettingsOrSetup();
        mHandler.sendMessage(mHandler.obtainMessage(EVENT_SET_ACCEPT_PARTIAL_CONNECTIVITY,
                encodeBool(accept), encodeBool(always), network));
    }

    @Override
    public void setAvoidUnvalidated(Network network) {
        enforceNetworkStackSettingsOrSetup();
        mHandler.sendMessage(mHandler.obtainMessage(EVENT_SET_AVOID_UNVALIDATED, network));
    }

    @Override
    public void setTestAllowBadWifiUntil(long timeMs) {
        enforceSettingsPermission();
        if (!Build.isDebuggable()) {
            throw new IllegalStateException("Does not support in non-debuggable build");
        }

        if (timeMs > System.currentTimeMillis() + MAX_TEST_ALLOW_BAD_WIFI_UNTIL_MS) {
            throw new IllegalArgumentException("It should not exceed "
                    + MAX_TEST_ALLOW_BAD_WIFI_UNTIL_MS + "ms from now");
        }

        mHandler.sendMessage(
                mHandler.obtainMessage(EVENT_SET_TEST_ALLOW_BAD_WIFI_UNTIL, timeMs));
    }

    @Override
    public void setTestLowTcpPollingTimerForKeepalive(long timeMs) {
        enforceSettingsPermission();

        if (timeMs > System.currentTimeMillis() + MAX_TEST_LOW_TCP_POLLING_UNTIL_MS) {
            throw new IllegalArgumentException("Argument should not exceed "
                    + MAX_TEST_LOW_TCP_POLLING_UNTIL_MS + "ms from now");
        }

        mHandler.sendMessage(
                mHandler.obtainMessage(EVENT_SET_LOW_TCP_POLLING_UNTIL, timeMs));
    }

    private void handleSetAcceptUnvalidated(Network network, boolean accept, boolean always) {
        if (DBG) log("handleSetAcceptUnvalidated network=" + network +
                " accept=" + accept + " always=" + always);

        NetworkAgentInfo nai = getNetworkAgentInfoForNetwork(network);
        if (nai == null) {
            // Nothing to do.
            return;
        }

        if (nai.everValidated()) {
            // The network validated while the dialog box was up. Take no action.
            return;
        }

        if (!nai.networkAgentConfig.explicitlySelected) {
            Log.wtf(TAG, "BUG: setAcceptUnvalidated non non-explicitly selected network");
        }

        if (accept != nai.networkAgentConfig.acceptUnvalidated) {
            nai.networkAgentConfig.acceptUnvalidated = accept;
            // If network becomes partial connectivity and user already accepted to use this
            // network, we should respect the user's option and don't need to popup the
            // PARTIAL_CONNECTIVITY notification to user again.
            nai.networkAgentConfig.acceptPartialConnectivity = accept;
            nai.updateScoreForNetworkAgentUpdate();
            rematchAllNetworksAndRequests();
        }

        if (always) {
            nai.onSaveAcceptUnvalidated(accept);
        }

        if (!accept) {
            // Tell the NetworkAgent to not automatically reconnect to the network.
            nai.onPreventAutomaticReconnect();
            // Teardown the network.
            teardownUnneededNetwork(nai);
        }

    }

    private void handleSetAcceptPartialConnectivity(Network network, boolean accept,
            boolean always) {
        if (DBG) {
            log("handleSetAcceptPartialConnectivity network=" + network + " accept=" + accept
                    + " always=" + always);
        }

        final NetworkAgentInfo nai = getNetworkAgentInfoForNetwork(network);
        if (nai == null) {
            // Nothing to do.
            return;
        }

        if (nai.isValidated()) {
            // The network validated while the dialog box was up. Take no action.
            return;
        }

        if (accept != nai.networkAgentConfig.acceptPartialConnectivity) {
            nai.networkAgentConfig.acceptPartialConnectivity = accept;
        }

        // TODO: Use the current design or save the user choice into IpMemoryStore.
        if (always) {
            nai.onSaveAcceptUnvalidated(accept);
        }

        if (!accept) {
            // Tell the NetworkAgent to not automatically reconnect to the network.
            nai.onPreventAutomaticReconnect();
            // Tear down the network.
            teardownUnneededNetwork(nai);
        } else {
            // Inform NetworkMonitor that partial connectivity is acceptable. This will likely
            // result in a partial connectivity result which will be processed by
            // maybeHandleNetworkMonitorMessage.
            //
            // TODO: NetworkMonitor does not refer to the "never ask again" bit. The bit is stored
            // per network. Therefore, NetworkMonitor may still do https probe.
            nai.networkMonitor().setAcceptPartialConnectivity();
        }
    }

    private void handleSetAvoidUnvalidated(Network network) {
        NetworkAgentInfo nai = getNetworkAgentInfoForNetwork(network);
        if (nai == null || nai.isValidated()) {
            // Nothing to do. The network either disconnected or revalidated.
            return;
        }
        if (0L == nai.getAvoidUnvalidated()) {
            nai.setAvoidUnvalidated();
            nai.updateScoreForNetworkAgentUpdate();
            rematchAllNetworksAndRequests();
        }
    }

    /** Schedule evaluation timeout */
    @VisibleForTesting
    public void scheduleEvaluationTimeout(@NonNull final Network network, final long delayMs) {
        mDeps.scheduleEvaluationTimeout(mHandler, network, delayMs);
    }

    @Override
    public void startCaptivePortalApp(Network network) {
        enforceNetworkStackOrSettingsPermission();
        mHandler.post(() -> {
            NetworkAgentInfo nai = getNetworkAgentInfoForNetwork(network);
            if (nai == null) return;
            if (!nai.networkCapabilities.hasCapability(NET_CAPABILITY_CAPTIVE_PORTAL)) return;
            nai.networkMonitor().launchCaptivePortalApp();
        });
    }

    /**
     * NetworkStack endpoint to start the captive portal app. The NetworkStack needs to use this
     * endpoint as it does not have INTERACT_ACROSS_USERS_FULL itself.
     * @param network Network on which the captive portal was detected.
     * @param appExtras Bundle to use as intent extras for the captive portal application.
     *                  Must be treated as opaque to avoid preventing the captive portal app to
     *                  update its arguments.
     */
    @Override
    public void startCaptivePortalAppInternal(Network network, Bundle appExtras) {
        mContext.enforceCallingOrSelfPermission(NetworkStack.PERMISSION_MAINLINE_NETWORK_STACK,
                "ConnectivityService");

        final Intent appIntent = new Intent(ConnectivityManager.ACTION_CAPTIVE_PORTAL_SIGN_IN);
        appIntent.putExtras(appExtras);
        appIntent.putExtra(ConnectivityManager.EXTRA_CAPTIVE_PORTAL,
                new CaptivePortal(new CaptivePortalImpl(network).asBinder()));
        appIntent.setFlags(Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT | Intent.FLAG_ACTIVITY_NEW_TASK);

        final long token = Binder.clearCallingIdentity();
        try {
            mContext.startActivityAsUser(appIntent, UserHandle.CURRENT);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    private class CaptivePortalImpl extends ICaptivePortal.Stub {
        private final Network mNetwork;

        private CaptivePortalImpl(Network network) {
            mNetwork = network;
        }

        @Override
        public void appResponse(final int response) {
            if (response == CaptivePortal.APP_RETURN_WANTED_AS_IS) {
                enforceSettingsPermission();
            } else if (response == CaptivePortal.APP_RETURN_UNWANTED) {
                mHandler.sendMessage(mHandler.obtainMessage(EVENT_USER_DOES_NOT_WANT, mNetwork));
                // Since the network will be disconnected, skip notifying NetworkMonitor
                return;
            }

            final NetworkMonitorManager nm = getNetworkMonitorManager(mNetwork);
            if (nm == null) return;
            nm.notifyCaptivePortalAppFinished(response);
        }

        @Override
        public void appRequest(final int request) {
            final NetworkMonitorManager nm = getNetworkMonitorManager(mNetwork);
            if (nm == null) return;

            if (request == CaptivePortal.APP_REQUEST_REEVALUATION_REQUIRED) {
                enforceNetworkStackPermission(mContext);
                nm.forceReevaluation(mDeps.getCallingUid());
            }
        }

        @Nullable
        private NetworkMonitorManager getNetworkMonitorManager(final Network network) {
            // getNetworkAgentInfoForNetwork is thread-safe
            final NetworkAgentInfo nai = getNetworkAgentInfoForNetwork(network);
            if (nai == null) return null;

            // nai.networkMonitor() is thread-safe
            return nai.networkMonitor();
        }
    }

    public boolean avoidBadWifi() {
        return mMultinetworkPolicyTracker.getAvoidBadWifi();
    }

    private boolean activelyPreferBadWifi() {
        return mMultinetworkPolicyTracker.getActivelyPreferBadWifi();
    }

    /**
     * Return whether the device should maintain continuous, working connectivity by switching away
     * from WiFi networks having no connectivity.
     * @see MultinetworkPolicyTracker#getAvoidBadWifi()
     */
    public boolean shouldAvoidBadWifi() {
        if (!hasNetworkStackPermission()) {
            throw new SecurityException("avoidBadWifi requires NETWORK_STACK permission");
        }
        return avoidBadWifi();
    }

    private void updateAvoidBadWifi() {
        ensureRunningOnConnectivityServiceThread();
        // Agent info scores and offer scores depend on whether cells yields to bad wifi.
        final boolean avoidBadWifi = avoidBadWifi();
        for (final NetworkAgentInfo nai : mNetworkAgentInfos) {
            nai.updateScoreForNetworkAgentUpdate();
            if (avoidBadWifi) {
                // If the device is now avoiding bad wifi, remove notifications that might have
                // been put up when the device didn't.
                mNotifier.clearNotification(nai.network.getNetId(), NotificationType.LOST_INTERNET);
            }
        }
        // UpdateOfferScore will update mNetworkOffers inline, so make a copy first.
        final ArrayList<NetworkOfferInfo> offersToUpdate = new ArrayList<>(mNetworkOffers);
        for (final NetworkOfferInfo noi : offersToUpdate) {
            updateOfferScore(noi.offer);
        }
        mNetworkRanker.setConfiguration(new NetworkRanker.Configuration(activelyPreferBadWifi()));
        rematchAllNetworksAndRequests();
    }

    // TODO: Evaluate whether this is of interest to other consumers of
    // MultinetworkPolicyTracker and worth moving out of here.
    private void dumpAvoidBadWifiSettings(IndentingPrintWriter pw) {
        final boolean configRestrict = mMultinetworkPolicyTracker.configRestrictsAvoidBadWifi();
        if (!configRestrict) {
            pw.println("Bad Wi-Fi avoidance: unrestricted");
            return;
        }

        pw.println("Bad Wi-Fi avoidance: " + avoidBadWifi());
        pw.increaseIndent();
        pw.println("Config restrict:               " + configRestrict);
        pw.println("Actively prefer bad wifi:      " + activelyPreferBadWifi());

        final String settingValue = mMultinetworkPolicyTracker.getAvoidBadWifiSetting();
        String description;
        // Can't use a switch statement because strings are legal case labels, but null is not.
        if ("0".equals(settingValue)) {
            description = "get stuck";
        } else if (settingValue == null) {
            description = "prompt";
        } else if ("1".equals(settingValue)) {
            description = "avoid";
        } else {
            description = settingValue + " (?)";
        }
        pw.println("Avoid bad wifi setting:        " + description);

        final Boolean configValue = BinderUtils.withCleanCallingIdentity(
                () -> mMultinetworkPolicyTracker.deviceConfigActivelyPreferBadWifi());
        if (null == configValue) {
            description = "unset";
        } else if (configValue) {
            description = "force true";
        } else {
            description = "force false";
        }
        pw.println("Actively prefer bad wifi conf: " + description);
        pw.println();
        pw.println("Network overrides:");
        pw.increaseIndent();
        for (NetworkAgentInfo nai : networksSortedById()) {
            if (0L != nai.getAvoidUnvalidated()) {
                pw.println(nai.toShortString());
            }
        }
        pw.decreaseIndent();
        pw.decreaseIndent();
    }

    // TODO: This method is copied from TetheringNotificationUpdater. Should have a utility class to
    // unify the method.
    private static @NonNull String getSettingsPackageName(@NonNull final PackageManager pm) {
        final Intent settingsIntent = new Intent(Settings.ACTION_SETTINGS);
        final ComponentName settingsComponent = settingsIntent.resolveActivity(pm);
        return settingsComponent != null
                ? settingsComponent.getPackageName() : "com.android.settings";
    }

    private void showNetworkNotification(NetworkAgentInfo nai, NotificationType type) {
        final String action;
        final boolean highPriority;
        switch (type) {
            case NO_INTERNET:
                action = ConnectivityManager.ACTION_PROMPT_UNVALIDATED;
                // High priority because it is only displayed for explicitly selected networks.
                highPriority = true;
                break;
            case PRIVATE_DNS_BROKEN:
                action = Settings.ACTION_WIRELESS_SETTINGS;
                // High priority because we should let user know why there is no internet.
                highPriority = true;
                break;
            case LOST_INTERNET:
                action = ConnectivityManager.ACTION_PROMPT_LOST_VALIDATION;
                // High priority because it could help the user avoid unexpected data usage.
                highPriority = true;
                break;
            case PARTIAL_CONNECTIVITY:
                action = ConnectivityManager.ACTION_PROMPT_PARTIAL_CONNECTIVITY;
                // Don't bother the user with a high-priority notification if the network was not
                // explicitly selected by the user.
                highPriority = nai.networkAgentConfig.explicitlySelected;
                break;
            default:
                Log.wtf(TAG, "Unknown notification type " + type);
                return;
        }

        Intent intent = new Intent(action);
        if (type != NotificationType.PRIVATE_DNS_BROKEN) {
            intent.putExtra(ConnectivityManager.EXTRA_NETWORK, nai.network);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            // Some OEMs have their own Settings package. Thus, need to get the current using
            // Settings package name instead of just use default name "com.android.settings".
            final String settingsPkgName = getSettingsPackageName(mContext.getPackageManager());
            intent.setClassName(settingsPkgName,
                    settingsPkgName + ".wifi.WifiNoInternetDialog");
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(
                mContext.createContextAsUser(UserHandle.CURRENT, 0 /* flags */),
                0 /* requestCode */,
                intent,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        mNotifier.showNotification(
                nai.network.getNetId(), type, nai, null, pendingIntent, highPriority);
    }

    private boolean shouldPromptUnvalidated(NetworkAgentInfo nai) {
        // Don't prompt if the network is validated, and don't prompt on captive portals
        // because we're already prompting the user to sign in.
        if (nai.everValidated() || nai.everCaptivePortalDetected()) {
            return false;
        }

        // If a network has partial connectivity, always prompt unless the user has already accepted
        // partial connectivity and selected don't ask again. This ensures that if the device
        // automatically connects to a network that has partial Internet access, the user will
        // always be able to use it, either because they've already chosen "don't ask again" or
        // because we have prompted them.
        if (nai.partialConnectivity() && !nai.networkAgentConfig.acceptPartialConnectivity) {
            return true;
        }

        // If a network has no Internet access, only prompt if the network was explicitly selected
        // and if the user has not already told us to use the network regardless of whether it
        // validated or not.
        if (nai.networkAgentConfig.explicitlySelected
                && !nai.networkAgentConfig.acceptUnvalidated) {
            return true;
        }

        return false;
    }

    private void handleInitialEvaluationTimeout(@NonNull final Network network) {
        if (VDBG || DDBG) log("handleInitialEvaluationTimeout " + network);

        NetworkAgentInfo nai = getNetworkAgentInfoForNetwork(network);
        if (null == nai) return;

        if (nai.setEvaluated()) {
            // If setEvaluated() returned true, the network never had any form of connectivity.
            // This may have an impact on request matching if bad WiFi avoidance is off and the
            // network was found not to have Internet access.
            nai.updateScoreForNetworkAgentUpdate();
            rematchAllNetworksAndRequests();

            // Also, if this is WiFi and it should be preferred actively, now is the time to
            // prompt the user that they walked past and connected to a bad WiFi.
            if (nai.networkCapabilities.hasTransport(TRANSPORT_WIFI)
                    && !avoidBadWifi()
                    && activelyPreferBadWifi()) {
                // The notification will be removed if the network validates or disconnects.
                showNetworkNotification(nai, NotificationType.LOST_INTERNET);
                return;
            }
        }

        if (!shouldPromptUnvalidated(nai)) return;

        // Stop automatically reconnecting to this network in the future. Automatically connecting
        // to a network that provides no or limited connectivity is not useful, because the user
        // cannot use that network except through the notification shown by this method, and the
        // notification is only shown if the network is explicitly selected by the user.
        nai.onPreventAutomaticReconnect();

        if (nai.partialConnectivity()) {
            showNetworkNotification(nai, NotificationType.PARTIAL_CONNECTIVITY);
        } else {
            showNetworkNotification(nai, NotificationType.NO_INTERNET);
        }
    }

    private void handleNetworkUnvalidated(NetworkAgentInfo nai) {
        NetworkCapabilities nc = nai.networkCapabilities;
        if (DBG) log("handleNetworkUnvalidated " + nai.toShortString() + " cap=" + nc);

        if (!nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return;
        }

        if (mMultinetworkPolicyTracker.shouldNotifyWifiUnvalidated()) {
            showNetworkNotification(nai, NotificationType.LOST_INTERNET);
        }
    }

    @Override
    public int getMultipathPreference(Network network) {
        enforceAccessPermission();

        NetworkAgentInfo nai = getNetworkAgentInfoForNetwork(network);
        if (nai != null && nai.networkCapabilities
                .hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) {
            return ConnectivityManager.MULTIPATH_PREFERENCE_UNMETERED;
        }

        final NetworkPolicyManager netPolicyManager =
                 mContext.getSystemService(NetworkPolicyManager.class);

        final long token = Binder.clearCallingIdentity();
        final int networkPreference;
        try {
            networkPreference = netPolicyManager.getMultipathPreference(network);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
        if (networkPreference != 0) {
            return networkPreference;
        }
        return mMultinetworkPolicyTracker.getMeteredMultipathPreference();
    }

    @Override
    public NetworkRequest getDefaultRequest() {
        return mDefaultRequest.mRequests.get(0);
    }

    private class InternalHandler extends Handler {
        public InternalHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_EXPIRE_NET_TRANSITION_WAKELOCK:
                case EVENT_CLEAR_NET_TRANSITION_WAKELOCK: {
                    handleReleaseNetworkTransitionWakelock(msg.what);
                    break;
                }
                case EVENT_APPLY_GLOBAL_HTTP_PROXY: {
                    mProxyTracker.loadDeprecatedGlobalHttpProxy();
                    break;
                }
                case EVENT_PAC_PROXY_HAS_CHANGED: {
                    final Pair<Network, ProxyInfo> arg = (Pair<Network, ProxyInfo>) msg.obj;
                    handlePacProxyServiceStarted(arg.first, arg.second);
                    break;
                }
                case EVENT_REGISTER_NETWORK_PROVIDER: {
                    handleRegisterNetworkProvider((NetworkProviderInfo) msg.obj);
                    break;
                }
                case EVENT_UNREGISTER_NETWORK_PROVIDER: {
                    handleUnregisterNetworkProvider((Messenger) msg.obj);
                    break;
                }
                case EVENT_REGISTER_NETWORK_OFFER: {
                    handleRegisterNetworkOffer((NetworkOffer) msg.obj);
                    break;
                }
                case EVENT_UNREGISTER_NETWORK_OFFER: {
                    final NetworkOfferInfo offer =
                            findNetworkOfferInfoByCallback((INetworkOfferCallback) msg.obj);
                    if (null != offer) {
                        handleUnregisterNetworkOffer(offer);
                    }
                    break;
                }
                case EVENT_REGISTER_NETWORK_AGENT: {
                    final Pair<NetworkAgentInfo, INetworkMonitor> arg =
                            (Pair<NetworkAgentInfo, INetworkMonitor>) msg.obj;
                    handleRegisterNetworkAgent(arg.first, arg.second);
                    break;
                }
                case EVENT_REGISTER_NETWORK_REQUEST:
                case EVENT_REGISTER_NETWORK_LISTENER: {
                    handleRegisterNetworkRequest((NetworkRequestInfo) msg.obj);
                    break;
                }
                case EVENT_REGISTER_NETWORK_REQUEST_WITH_INTENT:
                case EVENT_REGISTER_NETWORK_LISTENER_WITH_INTENT: {
                    handleRegisterNetworkRequestWithIntent(msg);
                    break;
                }
                case EVENT_TIMEOUT_NETWORK_REQUEST: {
                    NetworkRequestInfo nri = (NetworkRequestInfo) msg.obj;
                    handleTimedOutNetworkRequest(nri);
                    break;
                }
                case EVENT_RELEASE_NETWORK_REQUEST_WITH_INTENT: {
                    handleReleaseNetworkRequestWithIntent((PendingIntent) msg.obj, msg.arg1);
                    break;
                }
                case EVENT_RELEASE_NETWORK_REQUEST: {
                    handleReleaseNetworkRequest((NetworkRequest) msg.obj, msg.arg1,
                            /* callOnUnavailable */ false);
                    break;
                }
                case EVENT_SET_ACCEPT_UNVALIDATED: {
                    Network network = (Network) msg.obj;
                    handleSetAcceptUnvalidated(network, toBool(msg.arg1), toBool(msg.arg2));
                    break;
                }
                case EVENT_SET_ACCEPT_PARTIAL_CONNECTIVITY: {
                    Network network = (Network) msg.obj;
                    handleSetAcceptPartialConnectivity(network, toBool(msg.arg1),
                            toBool(msg.arg2));
                    break;
                }
                case EVENT_SET_AVOID_UNVALIDATED: {
                    handleSetAvoidUnvalidated((Network) msg.obj);
                    break;
                }
                case EVENT_INITIAL_EVALUATION_TIMEOUT: {
                    handleInitialEvaluationTimeout((Network) msg.obj);
                    break;
                }
                case EVENT_CONFIGURE_ALWAYS_ON_NETWORKS: {
                    handleConfigureAlwaysOnNetworks();
                    break;
                }
                // Sent by AutomaticOnOffKeepaliveTracker to process an app request on the
                // handler thread.
                case AutomaticOnOffKeepaliveTracker.CMD_REQUEST_START_KEEPALIVE: {
                    mKeepaliveTracker.handleStartKeepalive(msg);
                    break;
                }
                case AutomaticOnOffKeepaliveTracker.CMD_MONITOR_AUTOMATIC_KEEPALIVE: {
                    final AutomaticOnOffKeepalive ki =
                            mKeepaliveTracker.getKeepaliveForBinder((IBinder) msg.obj);
                    if (null == ki) return; // The callback was unregistered before the alarm fired

                    final Network underpinnedNetwork = ki.getUnderpinnedNetwork();
                    final Network network = ki.getNetwork();
                    boolean networkFound = false;
                    boolean underpinnedNetworkFound = false;
                    for (NetworkAgentInfo n : mNetworkAgentInfos) {
                        if (n.network.equals(network)) networkFound = true;
                        if (n.everConnected() && n.network.equals(underpinnedNetwork)) {
                            underpinnedNetworkFound = true;
                        }
                    }

                    // If the network no longer exists, then the keepalive should have been
                    // cleaned up already. There is no point trying to resume keepalives.
                    if (!networkFound) return;

                    if (underpinnedNetworkFound) {
                        mKeepaliveTracker.handleMonitorAutomaticKeepalive(ki,
                                underpinnedNetwork.netId);
                    } else {
                        // If no underpinned network, then make sure the keepalive is running.
                        mKeepaliveTracker.handleMaybeResumeKeepalive(ki);
                    }
                    break;
                }
                // Sent by KeepaliveTracker to process an app request on the state machine thread.
                case NetworkAgent.CMD_STOP_SOCKET_KEEPALIVE: {
                    final AutomaticOnOffKeepalive ki = mKeepaliveTracker.getKeepaliveForBinder(
                            (IBinder) msg.obj);
                    if (ki == null) {
                        Log.e(TAG, "Attempt to stop an already stopped keepalive");
                        return;
                    }
                    final int reason = msg.arg2;
                    mKeepaliveTracker.handleStopKeepalive(ki, reason);
                    break;
                }
                case EVENT_REPORT_NETWORK_CONNECTIVITY: {
                    handleReportNetworkConnectivity((NetworkAgentInfo) msg.obj, msg.arg1,
                            toBool(msg.arg2));
                    break;
                }
                case EVENT_PRIVATE_DNS_SETTINGS_CHANGED:
                    handlePrivateDnsSettingsChanged();
                    break;
                case EVENT_PRIVATE_DNS_VALIDATION_UPDATE:
                    handlePrivateDnsValidationUpdate(
                            (PrivateDnsValidationUpdate) msg.obj);
                    break;
                case EVENT_UID_BLOCKED_REASON_CHANGED:
                    handleUidBlockedReasonChanged(msg.arg1, msg.arg2);
                    break;
                case EVENT_SET_REQUIRE_VPN_FOR_UIDS:
                    handleSetRequireVpnForUids(toBool(msg.arg1), (UidRange[]) msg.obj);
                    break;
                case EVENT_SET_OEM_NETWORK_PREFERENCE: {
                    final Pair<OemNetworkPreferences, IOnCompleteListener> arg =
                            (Pair<OemNetworkPreferences, IOnCompleteListener>) msg.obj;
                    handleSetOemNetworkPreference(arg.first, arg.second);
                    break;
                }
                case EVENT_SET_PROFILE_NETWORK_PREFERENCE: {
                    final Pair<List<ProfileNetworkPreferenceInfo>, IOnCompleteListener> arg =
                            (Pair<List<ProfileNetworkPreferenceInfo>, IOnCompleteListener>) msg.obj;
                    handleSetProfileNetworkPreference(arg.first, arg.second);
                    break;
                }
                case EVENT_REPORT_NETWORK_ACTIVITY:
                    final NetworkActivityParams arg = (NetworkActivityParams) msg.obj;
                    handleReportNetworkActivity(arg);
                    break;
                case EVENT_MOBILE_DATA_PREFERRED_UIDS_CHANGED:
                    handleMobileDataPreferredUidsChanged();
                    break;
                case EVENT_SET_TEST_ALLOW_BAD_WIFI_UNTIL:
                    final long timeMs = ((Long) msg.obj).longValue();
                    mMultinetworkPolicyTracker.setTestAllowBadWifiUntil(timeMs);
                    break;
                case EVENT_INGRESS_RATE_LIMIT_CHANGED:
                    handleIngressRateLimitChanged();
                    break;
                case EVENT_USER_DOES_NOT_WANT:
                    final NetworkAgentInfo nai = getNetworkAgentInfoForNetwork((Network) msg.obj);
                    if (nai == null) break;
                    nai.onPreventAutomaticReconnect();
                    nai.disconnect();
                    break;
                case EVENT_SET_VPN_NETWORK_PREFERENCE:
                    handleSetVpnNetworkPreference((VpnNetworkPreferenceInfo) msg.obj);
                    break;
                case EVENT_SET_LOW_TCP_POLLING_UNTIL: {
                    final long time = ((Long) msg.obj).longValue();
                    mKeepaliveTracker.handleSetTestLowTcpPollingTimer(time);
                    break;
                }
                case EVENT_UID_FROZEN_STATE_CHANGED:
                    UidFrozenStateChangedArgs args = (UidFrozenStateChangedArgs) msg.obj;
                    handleFrozenUids(args.mUids, args.mFrozenStates);
                    break;
                case EVENT_UID_CARRIER_PRIVILEGES_LOST:
                    handleUidCarrierPrivilegesLost(msg.arg1, msg.arg2);
                    break;
            }
        }
    }

    @Override
    @Deprecated
    public int getLastTetherError(String iface) {
        enforceAccessPermission();
        final TetheringManager tm = (TetheringManager) mContext.getSystemService(
                Context.TETHERING_SERVICE);
        return tm.getLastTetherError(iface);
    }

    @Override
    @Deprecated
    public String[] getTetherableIfaces() {
        enforceAccessPermission();
        final TetheringManager tm = (TetheringManager) mContext.getSystemService(
                Context.TETHERING_SERVICE);
        return tm.getTetherableIfaces();
    }

    @Override
    @Deprecated
    public String[] getTetheredIfaces() {
        enforceAccessPermission();
        final TetheringManager tm = (TetheringManager) mContext.getSystemService(
                Context.TETHERING_SERVICE);
        return tm.getTetheredIfaces();
    }


    @Override
    @Deprecated
    public String[] getTetheringErroredIfaces() {
        enforceAccessPermission();
        final TetheringManager tm = (TetheringManager) mContext.getSystemService(
                Context.TETHERING_SERVICE);

        return tm.getTetheringErroredIfaces();
    }

    @Override
    @Deprecated
    public String[] getTetherableUsbRegexs() {
        enforceAccessPermission();
        final TetheringManager tm = (TetheringManager) mContext.getSystemService(
                Context.TETHERING_SERVICE);

        return tm.getTetherableUsbRegexs();
    }

    @Override
    @Deprecated
    public String[] getTetherableWifiRegexs() {
        enforceAccessPermission();
        final TetheringManager tm = (TetheringManager) mContext.getSystemService(
                Context.TETHERING_SERVICE);
        return tm.getTetherableWifiRegexs();
    }

    // Called when we lose the default network and have no replacement yet.
    // This will automatically be cleared after X seconds or a new default network
    // becomes CONNECTED, whichever happens first.  The timer is started by the
    // first caller and not restarted by subsequent callers.
    private void ensureNetworkTransitionWakelock(String forWhom) {
        synchronized (this) {
            if (mNetTransitionWakeLock.isHeld()) {
                return;
            }
            mNetTransitionWakeLock.acquire();
            mLastWakeLockAcquireTimestamp = SystemClock.elapsedRealtime();
            mTotalWakelockAcquisitions++;
        }
        mWakelockLogs.log("ACQUIRE for " + forWhom);
        Message msg = mHandler.obtainMessage(EVENT_EXPIRE_NET_TRANSITION_WAKELOCK);
        final int lockTimeout = mResources.get().getInteger(
                R.integer.config_networkTransitionTimeout);
        mHandler.sendMessageDelayed(msg, lockTimeout);
    }

    // Called when we gain a new default network to release the network transition wakelock in a
    // second, to allow a grace period for apps to reconnect over the new network. Pending expiry
    // message is cancelled.
    private void scheduleReleaseNetworkTransitionWakelock() {
        synchronized (this) {
            if (!mNetTransitionWakeLock.isHeld()) {
                return; // expiry message released the lock first.
            }
        }
        // Cancel self timeout on wakelock hold.
        mHandler.removeMessages(EVENT_EXPIRE_NET_TRANSITION_WAKELOCK);
        Message msg = mHandler.obtainMessage(EVENT_CLEAR_NET_TRANSITION_WAKELOCK);
        mHandler.sendMessageDelayed(msg, 1000);
    }

    // Called when either message of ensureNetworkTransitionWakelock or
    // scheduleReleaseNetworkTransitionWakelock is processed.
    private void handleReleaseNetworkTransitionWakelock(int eventId) {
        String event = eventName(eventId);
        synchronized (this) {
            if (!mNetTransitionWakeLock.isHeld()) {
                mWakelockLogs.log(String.format("RELEASE: already released (%s)", event));
                Log.w(TAG, "expected Net Transition WakeLock to be held");
                return;
            }
            mNetTransitionWakeLock.release();
            long lockDuration = SystemClock.elapsedRealtime() - mLastWakeLockAcquireTimestamp;
            mTotalWakelockDurationMs += lockDuration;
            mMaxWakelockDurationMs = Math.max(mMaxWakelockDurationMs, lockDuration);
            mTotalWakelockReleases++;
        }
        mWakelockLogs.log(String.format("RELEASE (%s)", event));
    }

    // 100 percent is full good, 0 is full bad.
    @Override
    public void reportInetCondition(int networkType, int percentage) {
        NetworkAgentInfo nai = mLegacyTypeTracker.getNetworkForType(networkType);
        if (nai == null) return;
        reportNetworkConnectivity(nai.network, percentage > 50);
    }

    @Override
    public void reportNetworkConnectivity(Network network, boolean hasConnectivity) {
        enforceAccessPermission();
        enforceInternetPermission();
        final int uid = mDeps.getCallingUid();
        final int connectivityInfo = encodeBool(hasConnectivity);

        final NetworkAgentInfo nai;
        if (network == null) {
            nai = getDefaultNetwork();
        } else {
            nai = getNetworkAgentInfoForNetwork(network);
        }

        mHandler.sendMessage(
                mHandler.obtainMessage(
                        EVENT_REPORT_NETWORK_CONNECTIVITY, uid, connectivityInfo, nai));
    }

    private void handleReportNetworkConnectivity(
            @Nullable NetworkAgentInfo nai, int uid, boolean hasConnectivity) {
        if (nai == null
                || nai != getNetworkAgentInfoForNetwork(nai.network)
                || nai.networkInfo.getState() == NetworkInfo.State.DISCONNECTED) {
            return;
        }
        // Revalidate if the app report does not match our current validated state.
        if (hasConnectivity == nai.isValidated()) {
            mConnectivityDiagnosticsHandler.sendMessage(
                    mConnectivityDiagnosticsHandler.obtainMessage(
                            ConnectivityDiagnosticsHandler.EVENT_NETWORK_CONNECTIVITY_REPORTED,
                            new ReportedNetworkConnectivityInfo(
                                    hasConnectivity, false /* isNetworkRevalidating */, uid, nai)));
            return;
        }
        if (DBG) {
            int netid = nai.network.getNetId();
            log("reportNetworkConnectivity(" + netid + ", " + hasConnectivity + ") by " + uid);
        }
        // Validating a network that has not yet connected could result in a call to
        // rematchNetworkAndRequests() which is not meant to work on such networks.
        if (!nai.everConnected()) {
            return;
        }
        final NetworkCapabilities nc = getNetworkCapabilitiesInternal(nai);
        if (isNetworkWithCapabilitiesBlocked(nc, uid, false)) {
            return;
        }

        // Send CONNECTIVITY_REPORTED event before re-validating the Network to force an ordering of
        // ConnDiags events. This ensures that #onNetworkConnectivityReported() will be called
        // before #onConnectivityReportAvailable(), which is called once Network evaluation is
        // completed.
        mConnectivityDiagnosticsHandler.sendMessage(
                mConnectivityDiagnosticsHandler.obtainMessage(
                        ConnectivityDiagnosticsHandler.EVENT_NETWORK_CONNECTIVITY_REPORTED,
                        new ReportedNetworkConnectivityInfo(
                                hasConnectivity, true /* isNetworkRevalidating */, uid, nai)));
        nai.networkMonitor().forceReevaluation(uid);
    }

    // TODO: call into netd.
    private boolean queryUserAccess(int uid, Network network) {
        final NetworkAgentInfo nai = getNetworkAgentInfoForNetwork(network);
        if (nai == null) return false;

        // Any UID can use its default network.
        if (nai == getDefaultNetworkForUid(uid)) return true;

        // Privileged apps can use any network.
        if (mPermissionMonitor.hasRestrictedNetworksPermission(uid)) {
            return true;
        }

        // An unprivileged UID can use a VPN iff the VPN applies to it.
        if (nai.isVPN()) {
            return nai.networkCapabilities.appliesToUid(uid);
        }

        // An unprivileged UID can bypass the VPN that applies to it only if it can protect its
        // sockets, i.e., if it is the owner.
        final NetworkAgentInfo vpn = getVpnForUid(uid);
        if (vpn != null && !vpn.networkAgentConfig.allowBypass
                && uid != vpn.networkCapabilities.getOwnerUid()) {
            return false;
        }

        // The UID's permission must be at least sufficient for the network. Since the restricted
        // permission was already checked above, that just leaves background networks.
        if (!nai.networkCapabilities.hasCapability(NET_CAPABILITY_FOREGROUND)) {
            return mPermissionMonitor.hasUseBackgroundNetworksPermission(uid);
        }

        // Unrestricted network. Anyone gets to use it.
        return true;
    }

    /**
     * Returns information about the proxy a certain network is using. If given a null network, it
     * it will return the proxy for the bound network for the caller app or the default proxy if
     * none.
     *
     * @param network the network we want to get the proxy information for.
     * @return Proxy information if a network has a proxy configured, or otherwise null.
     */
    @Override
    public ProxyInfo getProxyForNetwork(Network network) {
        final ProxyInfo globalProxy = mProxyTracker.getGlobalProxy();
        if (globalProxy != null) return globalProxy;
        if (network == null) {
            // Get the network associated with the calling UID.
            final Network activeNetwork = getActiveNetworkForUidInternal(mDeps.getCallingUid(),
                    true);
            if (activeNetwork == null) {
                return null;
            }
            return getLinkPropertiesProxyInfo(activeNetwork);
        } else if (mDeps.queryUserAccess(mDeps.getCallingUid(), network, this)) {
            // Don't call getLinkProperties() as it requires ACCESS_NETWORK_STATE permission, which
            // caller may not have.
            return getLinkPropertiesProxyInfo(network);
        }
        // No proxy info available if the calling UID does not have network access.
        return null;
    }


    private ProxyInfo getLinkPropertiesProxyInfo(Network network) {
        final NetworkAgentInfo nai = getNetworkAgentInfoForNetwork(network);
        if (nai == null) return null;
        synchronized (nai) {
            final ProxyInfo linkHttpProxy = nai.linkProperties.getHttpProxy();
            return linkHttpProxy == null ? null : new ProxyInfo(linkHttpProxy);
        }
    }

    @Override
    public void setGlobalProxy(@Nullable final ProxyInfo proxyProperties) {
        enforceNetworkStackPermission(mContext);
        mProxyTracker.setGlobalProxy(proxyProperties);
    }

    @Override
    @Nullable
    public ProxyInfo getGlobalProxy() {
        return mProxyTracker.getGlobalProxy();
    }

    private void handlePacProxyServiceStarted(@Nullable Network net, @Nullable ProxyInfo proxy) {
        mProxyTracker.setDefaultProxy(proxy);
        final NetworkAgentInfo nai = getDefaultNetwork();
        // TODO : this method should check that net == nai.network, unfortunately at this point
        // 'net' is always null in practice (see PacProxyService#sendPacBroadcast). PAC proxy
        // is only ever installed on the default network so in practice this is okay.
        if (null == nai) return;
        // PAC proxies only work on the default network. Therefore, only the default network
        // should have its link properties fixed up for PAC proxies.
        mProxyTracker.updateDefaultNetworkProxyPortForPAC(nai.linkProperties, nai.network);
        if (nai.everConnected()) {
            notifyNetworkCallbacks(nai, ConnectivityManager.CALLBACK_IP_CHANGED);
        }
    }

    // If the proxy has changed from oldLp to newLp, resend proxy broadcast. This method gets called
    // when any network changes proxy.
    // TODO: Remove usage of broadcast extras as they are deprecated and not applicable in a
    // multi-network world where an app might be bound to a non-default network.
    private void updateProxy(@NonNull LinkProperties newLp, @Nullable LinkProperties oldLp) {
        ProxyInfo newProxyInfo = newLp.getHttpProxy();
        ProxyInfo oldProxyInfo = oldLp == null ? null : oldLp.getHttpProxy();

        if (!ProxyTracker.proxyInfoEqual(newProxyInfo, oldProxyInfo)) {
            mProxyTracker.sendProxyBroadcast();
        }
    }

    private static class SettingsObserver extends ContentObserver {
        final private HashMap<Uri, Integer> mUriEventMap;
        final private Context mContext;
        final private Handler mHandler;

        SettingsObserver(Context context, Handler handler) {
            super(null);
            mUriEventMap = new HashMap<>();
            mContext = context;
            mHandler = handler;
        }

        void observe(Uri uri, int what) {
            mUriEventMap.put(uri, what);
            final ContentResolver resolver = mContext.getContentResolver();
            resolver.registerContentObserver(uri, false, this);
        }

        @Override
        public void onChange(boolean selfChange) {
            Log.wtf(TAG, "Should never be reached.");
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            final Integer what = mUriEventMap.get(uri);
            if (what != null) {
                mHandler.obtainMessage(what).sendToTarget();
            } else {
                loge("No matching event to send for URI=" + uri);
            }
        }
    }

    private static void log(String s) {
        Log.d(TAG, s);
    }

    private static void logw(String s) {
        Log.w(TAG, s);
    }

    private static void logwtf(String s) {
        Log.wtf(TAG, s);
    }

    private static void logwtf(String s, Throwable t) {
        Log.wtf(TAG, s, t);
    }

    private static void loge(String s) {
        Log.e(TAG, s);
    }

    private static void loge(String s, Throwable t) {
        Log.e(TAG, s, t);
    }

    /**
     * Return the information of all ongoing VPNs.
     *
     * <p>This method is used to update NetworkStatsService.
     *
     * <p>Must be called on the handler thread.
     */
    private UnderlyingNetworkInfo[] getAllVpnInfo() {
        ensureRunningOnConnectivityServiceThread();
        if (mLockdownEnabled) {
            return new UnderlyingNetworkInfo[0];
        }
        List<UnderlyingNetworkInfo> infoList = new ArrayList<>();
        for (NetworkAgentInfo nai : mNetworkAgentInfos) {
            UnderlyingNetworkInfo info = createVpnInfo(nai);
            if (info != null) {
                infoList.add(info);
            }
        }
        return infoList.toArray(new UnderlyingNetworkInfo[infoList.size()]);
    }

    /**
     * @return VPN information for accounting, or null if we can't retrieve all required
     *         information, e.g underlying ifaces.
     */
    private UnderlyingNetworkInfo createVpnInfo(NetworkAgentInfo nai) {
        Network[] underlyingNetworks = nai.declaredUnderlyingNetworks;
        // see VpnService.setUnderlyingNetworks()'s javadoc about how to interpret
        // the underlyingNetworks list.
        // TODO: stop using propagateUnderlyingCapabilities here, for example, by always
        // initializing NetworkAgentInfo#declaredUnderlyingNetworks to an empty array.
        if (underlyingNetworks == null && nai.propagateUnderlyingCapabilities()) {
            final NetworkAgentInfo defaultNai = getDefaultNetworkForUid(
                    nai.networkCapabilities.getOwnerUid());
            if (defaultNai != null) {
                underlyingNetworks = new Network[] { defaultNai.network };
            }
        }

        if (CollectionUtils.isEmpty(underlyingNetworks)) return null;

        List<String> interfaces = new ArrayList<>();
        for (Network network : underlyingNetworks) {
            NetworkAgentInfo underlyingNai = getNetworkAgentInfoForNetwork(network);
            if (underlyingNai == null) continue;
            LinkProperties lp = underlyingNai.linkProperties;
            for (String iface : lp.getAllInterfaceNames()) {
                if (!TextUtils.isEmpty(iface)) {
                    interfaces.add(iface);
                }
            }
        }

        if (interfaces.isEmpty()) return null;

        // Must be non-null or NetworkStatsService will crash.
        // Cannot happen in production code because Vpn only registers the NetworkAgent after the
        // tun or ipsec interface is created.
        // TODO: Remove this check.
        if (nai.linkProperties.getInterfaceName() == null) return null;

        return new UnderlyingNetworkInfo(nai.networkCapabilities.getOwnerUid(),
                nai.linkProperties.getInterfaceName(), interfaces);
    }

    // TODO This needs to be the default network that applies to the NAI.
    private Network[] underlyingNetworksOrDefault(final int ownerUid,
            Network[] underlyingNetworks) {
        final Network defaultNetwork = getNetwork(getDefaultNetworkForUid(ownerUid));
        if (underlyingNetworks == null && defaultNetwork != null) {
            // null underlying networks means to track the default.
            underlyingNetworks = new Network[] { defaultNetwork };
        }
        return underlyingNetworks;
    }

    // Returns true iff |network| is an underlying network of |nai|.
    private boolean hasUnderlyingNetwork(NetworkAgentInfo nai, Network network) {
        // TODO: support more than one level of underlying networks, either via a fixed-depth search
        // (e.g., 2 levels of underlying networks), or via loop detection, or....
        if (!nai.propagateUnderlyingCapabilities()) return false;
        final Network[] underlying = underlyingNetworksOrDefault(
                nai.networkCapabilities.getOwnerUid(), nai.declaredUnderlyingNetworks);
        return CollectionUtils.contains(underlying, network);
    }

    /**
     * Recompute the capabilities for any networks that had a specific network as underlying.
     *
     * When underlying networks change, such networks may have to update capabilities to reflect
     * things like the metered bit, their transports, and so on. The capabilities are calculated
     * immediately. This method runs on the ConnectivityService thread.
     */
    private void propagateUnderlyingNetworkCapabilities(Network updatedNetwork) {
        ensureRunningOnConnectivityServiceThread();
        for (NetworkAgentInfo nai : mNetworkAgentInfos) {
            if (updatedNetwork == null || hasUnderlyingNetwork(nai, updatedNetwork)) {
                updateCapabilitiesForNetwork(nai);
            }
        }
    }

    private boolean isUidBlockedByVpn(int uid, List<UidRange> blockedUidRanges) {
        // Determine whether this UID is blocked because of always-on VPN lockdown. If a VPN applies
        // to the UID, then the UID is not blocked because always-on VPN lockdown applies only when
        // a VPN is not up.
        final NetworkAgentInfo vpnNai = getVpnForUid(uid);
        if (vpnNai != null && !vpnNai.networkAgentConfig.allowBypass) return false;
        for (UidRange range : blockedUidRanges) {
            if (range.contains(uid)) return true;
        }
        return false;
    }

    @Override
    public void setRequireVpnForUids(boolean requireVpn, UidRange[] ranges) {
        enforceNetworkStackOrSettingsPermission();
        mHandler.sendMessage(mHandler.obtainMessage(EVENT_SET_REQUIRE_VPN_FOR_UIDS,
                encodeBool(requireVpn), 0 /* arg2 */, ranges));
    }

    private void handleSetRequireVpnForUids(boolean requireVpn, UidRange[] ranges) {
        if (DBG) {
            Log.d(TAG, "Setting VPN " + (requireVpn ? "" : "not ") + "required for UIDs: "
                    + Arrays.toString(ranges));
        }
        // Cannot use a Set since the list of UID ranges might contain duplicates.
        final List<UidRange> newVpnBlockedUidRanges = new ArrayList(mVpnBlockedUidRanges);
        for (int i = 0; i < ranges.length; i++) {
            if (requireVpn) {
                newVpnBlockedUidRanges.add(ranges[i]);
            } else {
                newVpnBlockedUidRanges.remove(ranges[i]);
            }
        }

        try {
            mNetd.networkRejectNonSecureVpn(requireVpn, toUidRangeStableParcels(ranges));
        } catch (RemoteException | ServiceSpecificException e) {
            Log.e(TAG, "setRequireVpnForUids(" + requireVpn + ", "
                    + Arrays.toString(ranges) + "): netd command failed: " + e);
        }

        if (mDeps.isAtLeastT()) {
            mPermissionMonitor.updateVpnLockdownUidRanges(requireVpn, ranges);
        }

        for (final NetworkAgentInfo nai : mNetworkAgentInfos) {
            final boolean curMetered = nai.networkCapabilities.isMetered();
            maybeNotifyNetworkBlocked(nai, curMetered, curMetered,
                    mVpnBlockedUidRanges, newVpnBlockedUidRanges);

            if (nai.isVPN()) {
                updateVpnFiltering(nai.linkProperties, nai.linkProperties, nai, true /* force */);
            }
        }

        mVpnBlockedUidRanges = newVpnBlockedUidRanges;
    }

    @Override
    public void setLegacyLockdownVpnEnabled(boolean enabled) {
        enforceNetworkStackOrSettingsPermission();
        mHandler.post(() -> mLockdownEnabled = enabled);
    }

    private boolean isLegacyLockdownNai(NetworkAgentInfo nai) {
        return mLockdownEnabled
                && getVpnType(nai) == VpnManager.TYPE_VPN_LEGACY
                && nai.networkCapabilities.appliesToUid(Process.FIRST_APPLICATION_UID);
    }

    private NetworkAgentInfo getLegacyLockdownNai() {
        if (!mLockdownEnabled) {
            return null;
        }
        // The legacy lockdown VPN always only applies to userId 0.
        final NetworkAgentInfo nai = getVpnForUid(Process.FIRST_APPLICATION_UID);
        if (nai == null || !isLegacyLockdownNai(nai)) return null;

        // The legacy lockdown VPN must always have exactly one underlying network.
        // This code may run on any thread and declaredUnderlyingNetworks may change, so store it in
        // a local variable. There is no need to make a copy because its contents cannot change.
        final Network[] underlying = nai.declaredUnderlyingNetworks;
        if (underlying == null ||  underlying.length != 1) {
            return null;
        }

        // The legacy lockdown VPN always uses the default network.
        // If the VPN's underlying network is no longer the current default network, it means that
        // the default network has just switched, and the VPN is about to disconnect.
        // Report that the VPN is not connected, so the state of NetworkInfo objects overwritten
        // by filterForLegacyLockdown will be set to CONNECTING and not CONNECTED.
        final NetworkAgentInfo defaultNetwork = getDefaultNetwork();
        if (defaultNetwork == null || !defaultNetwork.network.equals(underlying[0])) {
            return null;
        }

        return nai;
    };

    // TODO: move all callers to filterForLegacyLockdown and delete this method.
    // This likely requires making sendLegacyNetworkBroadcast take a NetworkInfo object instead of
    // just a DetailedState object.
    private DetailedState getLegacyLockdownState(DetailedState origState) {
        if (origState != DetailedState.CONNECTED) {
            return origState;
        }
        return (mLockdownEnabled && getLegacyLockdownNai() == null)
                ? DetailedState.CONNECTING
                : DetailedState.CONNECTED;
    }

    private void filterForLegacyLockdown(NetworkInfo ni) {
        if (!mLockdownEnabled || !ni.isConnected()) return;
        // The legacy lockdown VPN replaces the state of every network in CONNECTED state with the
        // state of its VPN. This is to ensure that when an underlying network connects, apps will
        // not see a CONNECTIVITY_ACTION broadcast for a network in state CONNECTED until the VPN
        // comes up, at which point there is a new CONNECTIVITY_ACTION broadcast for the underlying
        // network, this time with a state of CONNECTED.
        //
        // Now that the legacy lockdown code lives in ConnectivityService, and no longer has access
        // to the internal state of the Vpn object, always replace the state with CONNECTING. This
        // is not too far off the truth, since an always-on VPN, when not connected, is always
        // trying to reconnect.
        if (getLegacyLockdownNai() == null) {
            ni.setDetailedState(DetailedState.CONNECTING, "", null);
        }
    }

    @Override
    public void setProvisioningNotificationVisible(boolean visible, int networkType,
            String action) {
        enforceSettingsPermission();
        if (!ConnectivityManager.isNetworkTypeValid(networkType)) {
            return;
        }
        final long ident = Binder.clearCallingIdentity();
        try {
            // Concatenate the range of types onto the range of NetIDs.
            int id = NetIdManager.MAX_NET_ID + 1 + (networkType - ConnectivityManager.TYPE_NONE);
            mNotifier.setProvNotificationVisible(visible, id, action);
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    @Override
    public void setAirplaneMode(boolean enable) {
        enforceAirplaneModePermission();
        final long ident = Binder.clearCallingIdentity();
        try {
            final ContentResolver cr = mContext.getContentResolver();
            Settings.Global.putInt(cr, Settings.Global.AIRPLANE_MODE_ON, encodeBool(enable));
            Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
            intent.putExtra("state", enable);
            mContext.sendBroadcastAsUser(intent, UserHandle.ALL);
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    private void onUserAdded(@NonNull final UserHandle user) {
        if (mOemNetworkPreferences.getNetworkPreferences().size() > 0) {
            handleSetOemNetworkPreference(mOemNetworkPreferences, null);
        }
        updateProfileAllowedNetworks();
    }

    private void onUserRemoved(@NonNull final UserHandle user) {
        // If there was a network preference for this user, remove it.
        handleSetProfileNetworkPreference(
                List.of(new ProfileNetworkPreferenceInfo(user, null, true,
                        false /* blockingNonEnterprise */)),
                null /* listener */);
        if (mOemNetworkPreferences.getNetworkPreferences().size() > 0) {
            handleSetOemNetworkPreference(mOemNetworkPreferences, null);
        }
    }

    private void onPackageChanged(@NonNull final String packageName) {
        // This is necessary in case a package is added or removed, but also when it's replaced to
        // run as a new UID by its manifest rules. Also, if a separate package shares the same UID
        // as one in the preferences, then it should follow the same routing as that other package,
        // which means updating the rules is never to be needed in this case (whether it joins or
        // leaves a UID with a preference).
        if (isMappedInOemNetworkPreference(packageName)) {
            handleSetOemNetworkPreference(mOemNetworkPreferences, null);
        }

        // Invalidates cache entry when the package is updated.
        synchronized (mSelfCertifiedCapabilityCache) {
            mSelfCertifiedCapabilityCache.remove(packageName);
        }
    }

    private final BroadcastReceiver mUserIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            ensureRunningOnConnectivityServiceThread();
            final String action = intent.getAction();
            final UserHandle user = intent.getParcelableExtra(Intent.EXTRA_USER);

            // User should be filled for below intents, check the existence.
            if (user == null) {
                Log.wtf(TAG, intent.getAction() + " broadcast without EXTRA_USER");
                return;
            }

            if (Intent.ACTION_USER_ADDED.equals(action)) {
                onUserAdded(user);
            } else if (Intent.ACTION_USER_REMOVED.equals(action)) {
                onUserRemoved(user);
            }  else {
                Log.wtf(TAG, "received unexpected intent: " + action);
            }
        }
    };

    private final BroadcastReceiver mPackageIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            ensureRunningOnConnectivityServiceThread();
            switch (intent.getAction()) {
                case Intent.ACTION_PACKAGE_ADDED:
                case Intent.ACTION_PACKAGE_REMOVED:
                case Intent.ACTION_PACKAGE_REPLACED:
                    onPackageChanged(intent.getData().getSchemeSpecificPart());
                    break;
                default:
                    Log.wtf(TAG, "received unexpected intent: " + intent.getAction());
            }
        }
    };

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private final BroadcastReceiver mDataSaverReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (mDeps.isAtLeastV()) {
                throw new IllegalStateException(
                        "data saver status should be updated from platform");
            }
            ensureRunningOnConnectivityServiceThread();
            switch (intent.getAction()) {
                case ACTION_RESTRICT_BACKGROUND_CHANGED:
                    // If the uid is present in the deny list, the API will consistently
                    // return ENABLED. To retrieve the global switch status, the system
                    // uid is chosen because it will never be included in the deny list.
                    final int dataSaverForSystemUid =
                            mPolicyManager.getRestrictBackgroundStatus(Process.SYSTEM_UID);
                    final boolean isDataSaverEnabled = (dataSaverForSystemUid
                            != ConnectivityManager.RESTRICT_BACKGROUND_STATUS_DISABLED);
                    mBpfNetMaps.setDataSaverEnabled(isDataSaverEnabled);
                    break;
                default:
                    Log.wtf(TAG, "received unexpected intent: " + intent.getAction());
            }
        }
    };

    private final HashMap<Messenger, NetworkProviderInfo> mNetworkProviderInfos = new HashMap<>();
    private final HashMap<NetworkRequest, NetworkRequestInfo> mNetworkRequests = new HashMap<>();

    private static class NetworkProviderInfo {
        public final String name;
        public final Messenger messenger;
        private final IBinder.DeathRecipient mDeathRecipient;
        public final int providerId;

        NetworkProviderInfo(String name, Messenger messenger, int providerId,
                @NonNull IBinder.DeathRecipient deathRecipient) {
            this.name = name;
            this.messenger = messenger;
            this.providerId = providerId;
            mDeathRecipient = deathRecipient;

            if (mDeathRecipient == null) {
                throw new AssertionError("Must pass a deathRecipient");
            }
        }

        void connect(Context context, Handler handler) {
            try {
                messenger.getBinder().linkToDeath(mDeathRecipient, 0);
            } catch (RemoteException e) {
                mDeathRecipient.binderDied();
            }
        }
    }

    private void ensureAllNetworkRequestsHaveType(List<NetworkRequest> requests) {
        for (int i = 0; i < requests.size(); i++) {
            ensureNetworkRequestHasType(requests.get(i));
        }
    }

    private void ensureNetworkRequestHasType(NetworkRequest request) {
        if (request.type == NetworkRequest.Type.NONE) {
            throw new IllegalArgumentException(
                    "All NetworkRequests in ConnectivityService must have a type");
        }
    }

    /**
     * Tracks info about the requester.
     * Also used to notice when the calling process dies so as to self-expire
     */
    @VisibleForTesting
    protected class NetworkRequestInfo implements IBinder.DeathRecipient {
        // The requests to be satisfied in priority order. Non-multilayer requests will only have a
        // single NetworkRequest in mRequests.
        final List<NetworkRequest> mRequests;

        // mSatisfier and mActiveRequest rely on one another therefore set them together.
        void setSatisfier(
                @Nullable final NetworkAgentInfo satisfier,
                @Nullable final NetworkRequest activeRequest) {
            mSatisfier = satisfier;
            mActiveRequest = activeRequest;
        }

        // The network currently satisfying this NRI. Only one request in an NRI can have a
        // satisfier. For non-multilayer requests, only non-listen requests can have a satisfier.
        @Nullable
        private NetworkAgentInfo mSatisfier;
        NetworkAgentInfo getSatisfier() {
            return mSatisfier;
        }

        // The request in mRequests assigned to a network agent. This is null if none of the
        // requests in mRequests can be satisfied. This member has the constraint of only being
        // accessible on the handler thread.
        @Nullable
        private NetworkRequest mActiveRequest;
        NetworkRequest getActiveRequest() {
            return mActiveRequest;
        }

        final PendingIntent mPendingIntent;
        boolean mPendingIntentSent;
        @Nullable
        final Messenger mMessenger;

        // Information about the caller that caused this object to be created.
        @Nullable
        private final IBinder mBinder;
        final int mPid;
        final int mUid;
        final @NetworkCallback.Flag int mCallbackFlags;
        @Nullable
        final String mCallingAttributionTag;

        // Counter keeping track of this NRI.
        final RequestInfoPerUidCounter mPerUidCounter;

        // Effective UID of this request. This is different from mUid when a privileged process
        // files a request on behalf of another UID. This UID is used to determine blocked status,
        // UID matching, and so on. mUid above is used for permission checks and to enforce the
        // maximum limit of registered callbacks per UID.
        final int mAsUid;

        // Preference order of this request.
        final int mPreferenceOrder;

        // In order to preserve the mapping of NetworkRequest-to-callback when apps register
        // callbacks using a returned NetworkRequest, the original NetworkRequest needs to be
        // maintained for keying off of. This is only a concern when the original nri
        // mNetworkRequests changes which happens currently for apps that register callbacks to
        // track the default network. In those cases, the nri is updated to have mNetworkRequests
        // that match the per-app default nri that currently tracks the calling app's uid so that
        // callbacks are fired at the appropriate time. When the callbacks fire,
        // mNetworkRequestForCallback will be used so as to preserve the caller's mapping. When
        // callbacks are updated to key off of an nri vs NetworkRequest, this stops being an issue.
        // TODO b/177608132: make sure callbacks are indexed by NRIs and not NetworkRequest objects.
        @NonNull
        private final NetworkRequest mNetworkRequestForCallback;
        NetworkRequest getNetworkRequestForCallback() {
            return mNetworkRequestForCallback;
        }

        /**
         * Get the list of UIDs this nri applies to.
         */
        @NonNull
        Set<UidRange> getUids() {
            // networkCapabilities.getUids() returns a defensive copy.
            // multilayer requests will all have the same uids so return the first one.
            final Set<UidRange> uids = mRequests.get(0).networkCapabilities.getUidRanges();
            return (null == uids) ? new ArraySet<>() : uids;
        }

        NetworkRequestInfo(int asUid, @NonNull final NetworkRequest r,
                @Nullable final PendingIntent pi, @Nullable String callingAttributionTag) {
            this(asUid, Collections.singletonList(r), r, pi, callingAttributionTag,
                    PREFERENCE_ORDER_INVALID);
        }

        NetworkRequestInfo(int asUid, @NonNull final List<NetworkRequest> r,
                @NonNull final NetworkRequest requestForCallback, @Nullable final PendingIntent pi,
                @Nullable String callingAttributionTag, final int preferenceOrder) {
            ensureAllNetworkRequestsHaveType(r);
            mRequests = initializeRequests(r);
            mNetworkRequestForCallback = requestForCallback;
            mPendingIntent = pi;
            mMessenger = null;
            mBinder = null;
            mPid = getCallingPid();
            mUid = mDeps.getCallingUid();
            mAsUid = asUid;
            mPerUidCounter = getRequestCounter(this);
            mPerUidCounter.incrementCountOrThrow(mUid);
            /**
             * Location sensitive data not included in pending intent. Only included in
             * {@link NetworkCallback}.
             */
            mCallbackFlags = NetworkCallback.FLAG_NONE;
            mCallingAttributionTag = callingAttributionTag;
            mPreferenceOrder = preferenceOrder;
        }

        NetworkRequestInfo(int asUid, @NonNull final NetworkRequest r, @Nullable final Messenger m,
                @Nullable final IBinder binder,
                @NetworkCallback.Flag int callbackFlags,
                @Nullable String callingAttributionTag) {
            this(asUid, Collections.singletonList(r), r, m, binder, callbackFlags,
                    callingAttributionTag);
        }

        NetworkRequestInfo(int asUid, @NonNull final List<NetworkRequest> r,
                @NonNull final NetworkRequest requestForCallback, @Nullable final Messenger m,
                @Nullable final IBinder binder,
                @NetworkCallback.Flag int callbackFlags,
                @Nullable String callingAttributionTag) {
            super();
            ensureAllNetworkRequestsHaveType(r);
            mRequests = initializeRequests(r);
            mNetworkRequestForCallback = requestForCallback;
            mMessenger = m;
            mBinder = binder;
            mPid = getCallingPid();
            mUid = mDeps.getCallingUid();
            mAsUid = asUid;
            mPendingIntent = null;
            mPerUidCounter = getRequestCounter(this);
            mPerUidCounter.incrementCountOrThrow(mUid);
            mCallbackFlags = callbackFlags;
            mCallingAttributionTag = callingAttributionTag;
            mPreferenceOrder = PREFERENCE_ORDER_INVALID;
            linkDeathRecipient();
        }

        NetworkRequestInfo(@NonNull final NetworkRequestInfo nri,
                @NonNull final List<NetworkRequest> r) {
            super();
            ensureAllNetworkRequestsHaveType(r);
            mRequests = initializeRequests(r);
            mNetworkRequestForCallback = nri.getNetworkRequestForCallback();
            final NetworkAgentInfo satisfier = nri.getSatisfier();
            if (null != satisfier) {
                // If the old NRI was satisfied by an NAI, then it may have had an active request.
                // The active request is necessary to figure out what callbacks to send, in
                // particular when a network updates its capabilities.
                // As this code creates a new NRI with a new set of requests, figure out which of
                // the list of requests should be the active request. It is always the first
                // request of the list that can be satisfied by the satisfier since the order of
                // requests is a priority order.
                // Note even in the presence of a satisfier there may not be an active request,
                // when the satisfier is the no-service network.
                NetworkRequest activeRequest = null;
                for (final NetworkRequest candidate : r) {
                    if (candidate.canBeSatisfiedBy(satisfier.networkCapabilities)) {
                        activeRequest = candidate;
                        break;
                    }
                }
                setSatisfier(satisfier, activeRequest);
            }
            mMessenger = nri.mMessenger;
            mBinder = nri.mBinder;
            mPid = nri.mPid;
            mUid = nri.mUid;
            mAsUid = nri.mAsUid;
            mPendingIntent = nri.mPendingIntent;
            mPerUidCounter = nri.mPerUidCounter;
            mPerUidCounter.incrementCountOrThrow(mUid);
            mCallbackFlags = nri.mCallbackFlags;
            mCallingAttributionTag = nri.mCallingAttributionTag;
            mPreferenceOrder = PREFERENCE_ORDER_INVALID;
            linkDeathRecipient();
        }

        NetworkRequestInfo(int asUid, @NonNull final NetworkRequest r) {
            this(asUid, Collections.singletonList(r), PREFERENCE_ORDER_INVALID);
        }

        NetworkRequestInfo(int asUid, @NonNull final List<NetworkRequest> r,
                final int preferenceOrder) {
            this(asUid, r, r.get(0), null /* pi */, null /* callingAttributionTag */,
                    preferenceOrder);
        }

        // True if this NRI is being satisfied. It also accounts for if the nri has its satisifer
        // set to the mNoServiceNetwork in which case mActiveRequest will be null thus returning
        // false.
        boolean isBeingSatisfied() {
            return (null != mSatisfier && null != mActiveRequest);
        }

        boolean isMultilayerRequest() {
            return mRequests.size() > 1;
        }

        private List<NetworkRequest> initializeRequests(List<NetworkRequest> r) {
            // Creating a defensive copy to prevent the sender from modifying the list being
            // reflected in the return value of this method.
            final List<NetworkRequest> tempRequests = new ArrayList<>(r);
            return Collections.unmodifiableList(tempRequests);
        }

        void linkDeathRecipient() {
            if (null != mBinder) {
                try {
                    mBinder.linkToDeath(this, 0);
                } catch (RemoteException e) {
                    binderDied();
                }
            }
        }

        void unlinkDeathRecipient() {
            if (null != mBinder) {
                try {
                    mBinder.unlinkToDeath(this, 0);
                } catch (NoSuchElementException e) {
                    // Temporary workaround for b/194394697 pending analysis of additional logs
                    Log.wtf(TAG, "unlinkToDeath for already unlinked NRI " + this);
                }
            }
        }

        boolean hasHigherOrderThan(@NonNull final NetworkRequestInfo target) {
            // Compare two preference orders.
            return mPreferenceOrder < target.mPreferenceOrder;
        }

        int getPreferenceOrderForNetd() {
            if (mPreferenceOrder >= PREFERENCE_ORDER_NONE
                    && mPreferenceOrder <= PREFERENCE_ORDER_LOWEST) {
                return mPreferenceOrder;
            }
            return PREFERENCE_ORDER_NONE;
        }

        @Override
        public void binderDied() {
            // As an immutable collection, mRequests cannot change by the time the
            // lambda is evaluated on the handler thread so calling .get() from a binder thread
            // is acceptable. Use handleReleaseNetworkRequest and not directly
            // handleRemoveNetworkRequest so as to force a lookup in the requests map, in case
            // the app already unregistered the request.
            mHandler.post(() -> handleReleaseNetworkRequest(mRequests.get(0),
                    mUid, false /* callOnUnavailable */));
        }

        @Override
        public String toString() {
            final String asUidString = (mAsUid == mUid) ? "" : " asUid: " + mAsUid;
            return "uid/pid:" + mUid + "/" + mPid + asUidString + " activeRequest: "
                    + (mActiveRequest == null ? null : mActiveRequest.requestId)
                    + " callbackRequest: "
                    + mNetworkRequestForCallback.requestId
                    + " " + mRequests
                    + (mPendingIntent == null ? "" : " to trigger " + mPendingIntent)
                    + " callback flags: " + mCallbackFlags
                    + " order: " + mPreferenceOrder;
        }
    }

    // Keep backward compatibility since the ServiceSpecificException is used by
    // the API surface, see {@link ConnectivityManager#convertServiceException}.
    public static class RequestInfoPerUidCounter extends PerUidCounter {
        RequestInfoPerUidCounter(int maxCountPerUid) {
            super(maxCountPerUid);
        }

        @Override
        public synchronized void incrementCountOrThrow(int uid) {
            try {
                super.incrementCountOrThrow(uid);
            } catch (IllegalStateException e) {
                throw new ServiceSpecificException(
                        ConnectivityManager.Errors.TOO_MANY_REQUESTS,
                        "Uid " + uid + " exceeded its allotted requests limit");
            }
        }

        @Override
        public synchronized void decrementCountOrThrow(int uid) {
            throw new UnsupportedOperationException("Use decrementCount instead.");
        }

        public synchronized void decrementCount(int uid) {
            try {
                super.decrementCountOrThrow(uid);
            } catch (IllegalStateException e) {
                logwtf("Exception when decrement per uid request count: ", e);
            }
        }
    }

    // This checks that the passed capabilities either do not request a
    // specific SSID/SignalStrength, or the calling app has permission to do so.
    private void ensureSufficientPermissionsForRequest(NetworkCapabilities nc,
            int callerPid, int callerUid, String callerPackageName) {
        if (null != nc.getSsid() && !hasSettingsPermission(callerPid, callerUid)) {
            throw new SecurityException("Insufficient permissions to request a specific SSID");
        }

        if (nc.hasSignalStrength()
                && !hasNetworkSignalStrengthWakeupPermission(callerPid, callerUid)) {
            throw new SecurityException(
                    "Insufficient permissions to request a specific signal strength");
        }
        mAppOpsManager.checkPackage(callerUid, callerPackageName);

        if (nc.getSubscriptionIds().isEmpty()) {
            return;
        }
        if (mRequestRestrictedWifiEnabled
                && canRequestRestrictedNetworkDueToCarrierPrivileges(nc, callerUid)) {
            return;
        }
        enforceNetworkFactoryPermission();
    }

    private int[] getSignalStrengthThresholds(@NonNull final NetworkAgentInfo nai) {
        final SortedSet<Integer> thresholds = new TreeSet<>();
        synchronized (nai) {
            // mNetworkRequests may contain the same value multiple times in case of
            // multilayer requests. It won't matter in this case because the thresholds
            // will then be the same and be deduplicated as they enter the `thresholds` set.
            // TODO : have mNetworkRequests be a Set<NetworkRequestInfo> or the like.
            for (final NetworkRequestInfo nri : mNetworkRequests.values()) {
                for (final NetworkRequest req : nri.mRequests) {
                    if (req.networkCapabilities.hasSignalStrength()
                            && nai.satisfiesImmutableCapabilitiesOf(req)) {
                        thresholds.add(req.networkCapabilities.getSignalStrength());
                    }
                }
            }
        }
        return CollectionUtils.toIntArray(new ArrayList<>(thresholds));
    }

    private void updateSignalStrengthThresholds(
            NetworkAgentInfo nai, String reason, NetworkRequest request) {
        final int[] thresholdsArray = getSignalStrengthThresholds(nai);

        if (VDBG || (DBG && !"CONNECT".equals(reason))) {
            String detail;
            if (request != null && request.networkCapabilities.hasSignalStrength()) {
                detail = reason + " " + request.networkCapabilities.getSignalStrength();
            } else {
                detail = reason;
            }
            log(String.format("updateSignalStrengthThresholds: %s, sending %s to %s",
                    detail, Arrays.toString(thresholdsArray), nai.toShortString()));
        }

        nai.onSignalStrengthThresholdsUpdated(thresholdsArray);
    }

    private static void ensureValidNetworkSpecifier(NetworkCapabilities nc) {
        if (nc == null) {
            return;
        }
        NetworkSpecifier ns = nc.getNetworkSpecifier();
        if (ns == null) {
            return;
        }
        if (ns instanceof MatchAllNetworkSpecifier) {
            throw new IllegalArgumentException("A MatchAllNetworkSpecifier is not permitted");
        }
    }

    private static void ensureListenableCapabilities(@NonNull final NetworkCapabilities nc) {
        ensureValidNetworkSpecifier(nc);
        if (nc.isPrivateDnsBroken()) {
            throw new IllegalArgumentException("Can't request broken private DNS");
        }
        if (nc.hasAllowedUids()) {
            throw new IllegalArgumentException("Can't request access UIDs");
        }
    }

    private void ensureRequestableCapabilities(@NonNull final NetworkCapabilities nc) {
        ensureListenableCapabilities(nc);
        final String badCapability = nc.describeFirstNonRequestableCapability();
        if (badCapability != null) {
            throw new IllegalArgumentException("Cannot request network with " + badCapability);
        }
    }

    // TODO: Set the mini sdk to 31 and remove @TargetApi annotation when b/205923322 is addressed.
    @TargetApi(Build.VERSION_CODES.S)
    private boolean isTargetSdkAtleast(int version, int callingUid,
            @NonNull String callingPackageName) {
        final UserHandle user = UserHandle.getUserHandleForUid(callingUid);
        final PackageManager pm =
                mContext.createContextAsUser(user, 0 /* flags */).getPackageManager();
        try {
            final int callingVersion = pm.getTargetSdkVersion(callingPackageName);
            if (callingVersion < version) return false;
        } catch (PackageManager.NameNotFoundException e) { }
        return true;
    }

    @Override
    public NetworkRequest requestNetwork(int asUid, NetworkCapabilities networkCapabilities,
            int reqTypeInt, Messenger messenger, int timeoutMs, final IBinder binder,
            int legacyType, int callbackFlags, @NonNull String callingPackageName,
            @Nullable String callingAttributionTag) {
        if (legacyType != TYPE_NONE && !hasNetworkStackPermission()) {
            if (isTargetSdkAtleast(Build.VERSION_CODES.M, mDeps.getCallingUid(),
                    callingPackageName)) {
                throw new SecurityException("Insufficient permissions to specify legacy type");
            }
        }
        final NetworkCapabilities defaultNc = mDefaultRequest.mRequests.get(0).networkCapabilities;
        final int callingUid = mDeps.getCallingUid();
        // Privileged callers can track the default network of another UID by passing in a UID.
        if (asUid != Process.INVALID_UID) {
            enforceSettingsPermission();
        } else {
            asUid = callingUid;
        }
        final NetworkRequest.Type reqType;
        try {
            reqType = NetworkRequest.Type.values()[reqTypeInt];
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new IllegalArgumentException("Unsupported request type " + reqTypeInt);
        }
        switch (reqType) {
            case TRACK_DEFAULT:
                // If the request type is TRACK_DEFAULT, the passed {@code networkCapabilities}
                // is unused and will be replaced by ones appropriate for the UID (usually, the
                // calling app). This allows callers to keep track of the default network.
                networkCapabilities = copyDefaultNetworkCapabilitiesForUid(
                        defaultNc, asUid, callingUid, callingPackageName);
                enforceAccessPermission();
                break;
            case TRACK_SYSTEM_DEFAULT:
                enforceSettingsOrSetupWizardOrUseRestrictedNetworksPermission();
                networkCapabilities = new NetworkCapabilities(defaultNc);
                break;
            case BACKGROUND_REQUEST:
                enforceNetworkStackOrSettingsPermission();
                // Fall-through since other checks are the same with normal requests.
            case REQUEST:
                networkCapabilities = new NetworkCapabilities(networkCapabilities);
                enforceNetworkRequestPermissions(networkCapabilities, callingPackageName,
                        callingAttributionTag, callingUid);
                // TODO: this is incorrect. We mark the request as metered or not depending on
                //  the state of the app when the request is filed, but we never change the
                //  request if the app changes network state. http://b/29964605
                enforceMeteredApnPolicy(networkCapabilities);
                break;
            case LISTEN_FOR_BEST:
                enforceAccessPermission();
                networkCapabilities = new NetworkCapabilities(networkCapabilities);
                break;
            default:
                throw new IllegalArgumentException("Unsupported request type " + reqType);
        }
        ensureRequestableCapabilities(networkCapabilities);
        ensureSufficientPermissionsForRequest(networkCapabilities,
                Binder.getCallingPid(), callingUid, callingPackageName);

        // Enforce FOREGROUND if the caller does not have permission to use background network.
        if (reqType == LISTEN_FOR_BEST) {
            restrictBackgroundRequestForCaller(networkCapabilities);
        }

        // Set the UID range for this request to the single UID of the requester, unless the
        // requester has the permission to specify other UIDs.
        // This will overwrite any allowed UIDs in the requested capabilities. Though there
        // are no visible methods to set the UIDs, an app could use reflection to try and get
        // networks for other apps so it's essential that the UIDs are overwritten.
        // Also set the requester UID and package name in the request.
        restrictRequestUidsForCallerAndSetRequestorInfo(networkCapabilities,
                callingUid, callingPackageName);

        if (timeoutMs < 0) {
            throw new IllegalArgumentException("Bad timeout specified");
        }

        final NetworkRequest networkRequest = new NetworkRequest(networkCapabilities, legacyType,
                nextNetworkRequestId(), reqType);
        final NetworkRequestInfo nri = getNriToRegister(
                asUid, networkRequest, messenger, binder, callbackFlags,
                callingAttributionTag);
        if (DBG) log("requestNetwork for " + nri);

        // For TRACK_SYSTEM_DEFAULT callbacks, the capabilities have been modified since they were
        // copied from the default request above. (This is necessary to ensure, for example, that
        // the callback does not leak sensitive information to unprivileged apps.) Check that the
        // changes don't alter request matching.
        if (reqType == NetworkRequest.Type.TRACK_SYSTEM_DEFAULT &&
                (!networkCapabilities.equalRequestableCapabilities(defaultNc))) {
            throw new IllegalStateException(
                    "TRACK_SYSTEM_DEFAULT capabilities don't match default request: "
                    + networkCapabilities + " vs. " + defaultNc);
        }

        mHandler.sendMessage(mHandler.obtainMessage(EVENT_REGISTER_NETWORK_REQUEST, nri));
        if (timeoutMs > 0) {
            mHandler.sendMessageDelayed(mHandler.obtainMessage(EVENT_TIMEOUT_NETWORK_REQUEST,
                    nri), timeoutMs);
        }
        return networkRequest;
    }

    /**
     * Return the nri to be used when registering a network request. Specifically, this is used with
     * requests registered to track the default request. If there is currently a per-app default
     * tracking the app requestor, then we need to create a version of this nri that mirrors that of
     * the tracking per-app default so that callbacks are sent to the app requestor appropriately.
     * @param asUid the uid on behalf of which to file the request. Different from requestorUid
     *              when a privileged caller is tracking the default network for another uid.
     * @param nr the network request for the nri.
     * @param msgr the messenger for the nri.
     * @param binder the binder for the nri.
     * @param callingAttributionTag the calling attribution tag for the nri.
     * @return the nri to register.
     */
    private NetworkRequestInfo getNriToRegister(final int asUid, @NonNull final NetworkRequest nr,
            @Nullable final Messenger msgr, @Nullable final IBinder binder,
            @NetworkCallback.Flag int callbackFlags,
            @Nullable String callingAttributionTag) {
        final List<NetworkRequest> requests;
        if (NetworkRequest.Type.TRACK_DEFAULT == nr.type) {
            requests = copyDefaultNetworkRequestsForUid(
                    asUid, nr.getRequestorUid(), nr.getRequestorPackageName());
        } else {
            requests = Collections.singletonList(nr);
        }
        return new NetworkRequestInfo(
                asUid, requests, nr, msgr, binder, callbackFlags, callingAttributionTag);
    }

    private boolean shouldCheckCapabilitiesDeclaration(
            @NonNull final NetworkCapabilities networkCapabilities, final int callingUid,
            @NonNull final String callingPackageName) {
        final UserHandle user = UserHandle.getUserHandleForUid(callingUid);
        // Only run the check if the change is enabled.
        if (!mDeps.isChangeEnabled(
                ConnectivityCompatChanges.ENABLE_SELF_CERTIFIED_CAPABILITIES_DECLARATION,
                callingPackageName, user)) {
            return false;
        }

        return networkCapabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_PRIORITIZE_BANDWIDTH)
                || networkCapabilities.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_PRIORITIZE_LATENCY);
    }

    private void enforceRequestCapabilitiesDeclaration(@NonNull final String callerPackageName,
            @NonNull final NetworkCapabilities networkCapabilities, int callingUid) {
        // This check is added to fix the linter error for "current min is 30", which is not going
        // to happen because Connectivity service always run in S+.
        if (!mDeps.isAtLeastS()) {
            Log.wtf(TAG, "Connectivity service should always run in at least SDK S");
            return;
        }
        ApplicationSelfCertifiedNetworkCapabilities applicationNetworkCapabilities;
        final long ident = Binder.clearCallingIdentity();
        try {
            synchronized (mSelfCertifiedCapabilityCache) {
                applicationNetworkCapabilities = mSelfCertifiedCapabilityCache.get(
                        callerPackageName);
                if (applicationNetworkCapabilities == null) {
                    final PackageManager packageManager =
                            mContext.createContextAsUser(UserHandle.getUserHandleForUid(
                                    callingUid), 0 /* flags */).getPackageManager();
                    final PackageManager.Property networkSliceProperty = packageManager.getProperty(
                            ConstantsShim.PROPERTY_SELF_CERTIFIED_NETWORK_CAPABILITIES,
                            callerPackageName
                    );
                    final XmlResourceParser parser = packageManager
                            .getResourcesForApplication(callerPackageName)
                            .getXml(networkSliceProperty.getResourceId());
                    applicationNetworkCapabilities =
                            ApplicationSelfCertifiedNetworkCapabilities.createFromXml(parser);
                    mSelfCertifiedCapabilityCache.put(callerPackageName,
                            applicationNetworkCapabilities);
                }

            }
        } catch (PackageManager.NameNotFoundException ne) {
            throw new SecurityException(
                    "Cannot find " + ConstantsShim.PROPERTY_SELF_CERTIFIED_NETWORK_CAPABILITIES
                            + " property");
        } catch (XmlPullParserException | IOException | InvalidTagException e) {
            throw new SecurityException(e.getMessage());
        } finally {
            Binder.restoreCallingIdentity(ident);
        }

        applicationNetworkCapabilities.enforceSelfCertifiedNetworkCapabilitiesDeclared(
                networkCapabilities);
    }

    private boolean canRequestRestrictedNetworkDueToCarrierPrivileges(
            NetworkCapabilities networkCapabilities, int callingUid) {
        if (mRequestRestrictedWifiEnabled) {
            // For U+ devices, callers with carrier privilege could request restricted networks
            // with CBS capabilities, or any restricted WiFi networks.
            return ((networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_CBS)
                || networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
                && hasCarrierPrivilegeForNetworkCaps(callingUid, networkCapabilities));
        } else {
            // For T+ devices, callers with carrier privilege could request with CBS
            // capabilities.
            return (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_CBS)
                && hasCarrierPrivilegeForNetworkCaps(callingUid, networkCapabilities));
        }
    }
    private void enforceNetworkRequestPermissions(NetworkCapabilities networkCapabilities,
            String callingPackageName, String callingAttributionTag, final int callingUid) {
        if (shouldCheckCapabilitiesDeclaration(networkCapabilities, callingUid,
                callingPackageName)) {
            enforceRequestCapabilitiesDeclaration(callingPackageName, networkCapabilities,
                    callingUid);
        }
        if (!networkCapabilities.hasCapability(NET_CAPABILITY_NOT_RESTRICTED)) {
            if (!canRequestRestrictedNetworkDueToCarrierPrivileges(
                    networkCapabilities, callingUid)) {
                enforceConnectivityRestrictedNetworksPermission(true /* checkUidsAllowedList */);
            }
        } else {
            enforceChangePermission(callingPackageName, callingAttributionTag);
        }
    }

    @Override
    public boolean requestBandwidthUpdate(Network network) {
        enforceAccessPermission();
        NetworkAgentInfo nai = null;
        if (network == null) {
            return false;
        }
        synchronized (mNetworkForNetId) {
            nai = mNetworkForNetId.get(network.getNetId());
        }
        if (nai != null) {
            nai.onBandwidthUpdateRequested();
            synchronized (mBandwidthRequests) {
                final int uid = mDeps.getCallingUid();
                Integer uidReqs = mBandwidthRequests.get(uid);
                if (uidReqs == null) {
                    uidReqs = 0;
                }
                mBandwidthRequests.put(uid, ++uidReqs);
            }
            return true;
        }
        return false;
    }

    private boolean isSystem(int uid) {
        return uid < Process.FIRST_APPLICATION_UID;
    }

    private void enforceMeteredApnPolicy(NetworkCapabilities networkCapabilities) {
        final int uid = mDeps.getCallingUid();
        if (isSystem(uid)) {
            // Exemption for system uid.
            return;
        }
        if (networkCapabilities.hasCapability(NET_CAPABILITY_NOT_METERED)) {
            // Policy already enforced.
            return;
        }
        final long ident = Binder.clearCallingIdentity();
        try {
            if (mPolicyManager.isUidRestrictedOnMeteredNetworks(uid)) {
                // If UID is restricted, don't allow them to bring up metered APNs.
                networkCapabilities.addCapability(NET_CAPABILITY_NOT_METERED);
            }
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }

    @Override
    public NetworkRequest pendingRequestForNetwork(NetworkCapabilities networkCapabilities,
            PendingIntent operation, @NonNull String callingPackageName,
            @Nullable String callingAttributionTag) {
        Objects.requireNonNull(operation, "PendingIntent cannot be null.");
        final int callingUid = mDeps.getCallingUid();
        networkCapabilities = new NetworkCapabilities(networkCapabilities);
        enforceNetworkRequestPermissions(networkCapabilities, callingPackageName,
                callingAttributionTag, callingUid);
        enforceMeteredApnPolicy(networkCapabilities);
        ensureRequestableCapabilities(networkCapabilities);
        ensureSufficientPermissionsForRequest(networkCapabilities,
                Binder.getCallingPid(), callingUid, callingPackageName);
        restrictRequestUidsForCallerAndSetRequestorInfo(networkCapabilities,
                callingUid, callingPackageName);

        NetworkRequest networkRequest = new NetworkRequest(networkCapabilities, TYPE_NONE,
                nextNetworkRequestId(), NetworkRequest.Type.REQUEST);
        NetworkRequestInfo nri = new NetworkRequestInfo(callingUid, networkRequest, operation,
                callingAttributionTag);
        if (DBG) log("pendingRequest for " + nri);
        mHandler.sendMessage(mHandler.obtainMessage(EVENT_REGISTER_NETWORK_REQUEST_WITH_INTENT,
                nri));
        return networkRequest;
    }

    private void releasePendingNetworkRequestWithDelay(PendingIntent operation) {
        mHandler.sendMessageDelayed(
                mHandler.obtainMessage(EVENT_RELEASE_NETWORK_REQUEST_WITH_INTENT,
                mDeps.getCallingUid(), 0, operation), mReleasePendingIntentDelayMs);
    }

    @Override
    public void releasePendingNetworkRequest(PendingIntent operation) {
        Objects.requireNonNull(operation, "PendingIntent cannot be null.");
        mHandler.sendMessage(mHandler.obtainMessage(EVENT_RELEASE_NETWORK_REQUEST_WITH_INTENT,
                mDeps.getCallingUid(), 0, operation));
    }

    // In order to implement the compatibility measure for pre-M apps that call
    // WifiManager.enableNetwork(..., true) without also binding to that network explicitly,
    // WifiManager registers a network listen for the purpose of calling setProcessDefaultNetwork.
    // This ensures it has permission to do so.
    private boolean hasWifiNetworkListenPermission(NetworkCapabilities nc) {
        if (nc == null) {
            return false;
        }
        int[] transportTypes = nc.getTransportTypes();
        if (transportTypes.length != 1 || transportTypes[0] != NetworkCapabilities.TRANSPORT_WIFI) {
            return false;
        }
        try {
            mContext.enforceCallingOrSelfPermission(
                    android.Manifest.permission.ACCESS_WIFI_STATE,
                    "ConnectivityService");
        } catch (SecurityException e) {
            return false;
        }
        return true;
    }

    @Override
    public NetworkRequest listenForNetwork(NetworkCapabilities networkCapabilities,
            Messenger messenger, IBinder binder,
            @NetworkCallback.Flag int callbackFlags,
            @NonNull String callingPackageName, @NonNull String callingAttributionTag) {
        final int callingUid = mDeps.getCallingUid();
        if (!hasWifiNetworkListenPermission(networkCapabilities)) {
            enforceAccessPermission();
        }

        NetworkCapabilities nc = new NetworkCapabilities(networkCapabilities);
        ensureSufficientPermissionsForRequest(networkCapabilities,
                Binder.getCallingPid(), callingUid, callingPackageName);
        restrictRequestUidsForCallerAndSetRequestorInfo(nc, callingUid, callingPackageName);
        // Apps without the CHANGE_NETWORK_STATE permission can't use background networks, so
        // make all their listens include NET_CAPABILITY_FOREGROUND. That way, they will get
        // onLost and onAvailable callbacks when networks move in and out of the background.
        // There is no need to do this for requests because an app without CHANGE_NETWORK_STATE
        // can't request networks.
        restrictBackgroundRequestForCaller(nc);
        ensureListenableCapabilities(nc);

        NetworkRequest networkRequest = new NetworkRequest(nc, TYPE_NONE, nextNetworkRequestId(),
                NetworkRequest.Type.LISTEN);
        NetworkRequestInfo nri =
                new NetworkRequestInfo(callingUid, networkRequest, messenger, binder, callbackFlags,
                        callingAttributionTag);
        if (VDBG) log("listenForNetwork for " + nri);

        mHandler.sendMessage(mHandler.obtainMessage(EVENT_REGISTER_NETWORK_LISTENER, nri));
        return networkRequest;
    }

    @Override
    public void pendingListenForNetwork(NetworkCapabilities networkCapabilities,
            PendingIntent operation, @NonNull String callingPackageName,
            @Nullable String callingAttributionTag) {
        Objects.requireNonNull(operation, "PendingIntent cannot be null.");
        final int callingUid = mDeps.getCallingUid();
        if (!hasWifiNetworkListenPermission(networkCapabilities)) {
            enforceAccessPermission();
        }
        ensureListenableCapabilities(networkCapabilities);
        ensureSufficientPermissionsForRequest(networkCapabilities,
                Binder.getCallingPid(), callingUid, callingPackageName);
        final NetworkCapabilities nc = new NetworkCapabilities(networkCapabilities);
        restrictRequestUidsForCallerAndSetRequestorInfo(nc, callingUid, callingPackageName);

        NetworkRequest networkRequest = new NetworkRequest(nc, TYPE_NONE, nextNetworkRequestId(),
                NetworkRequest.Type.LISTEN);
        NetworkRequestInfo nri = new NetworkRequestInfo(callingUid, networkRequest, operation,
                callingAttributionTag);
        if (VDBG) log("pendingListenForNetwork for " + nri);

        mHandler.sendMessage(mHandler.obtainMessage(
                    EVENT_REGISTER_NETWORK_LISTENER_WITH_INTENT, nri));
    }

    /** Returns the next Network provider ID. */
    public final int nextNetworkProviderId() {
        return mNextNetworkProviderId.getAndIncrement();
    }

    @Override
    public void releaseNetworkRequest(NetworkRequest networkRequest) {
        ensureNetworkRequestHasType(networkRequest);
        mHandler.sendMessage(mHandler.obtainMessage(
                EVENT_RELEASE_NETWORK_REQUEST, mDeps.getCallingUid(), 0, networkRequest));
    }

    private void handleRegisterNetworkProvider(NetworkProviderInfo npi) {
        if (mNetworkProviderInfos.containsKey(npi.messenger)) {
            // Avoid creating duplicates. even if an app makes a direct AIDL call.
            // This will never happen if an app calls ConnectivityManager#registerNetworkProvider,
            // as that will throw if a duplicate provider is registered.
            loge("Attempt to register existing NetworkProviderInfo "
                    + mNetworkProviderInfos.get(npi.messenger).name);
            return;
        }

        if (DBG) log("Got NetworkProvider Messenger for " + npi.name);
        mNetworkProviderInfos.put(npi.messenger, npi);
        npi.connect(mContext, mTrackerHandler);
    }

    @Override
    public int registerNetworkProvider(Messenger messenger, String name) {
        enforceNetworkFactoryOrSettingsPermission();
        Objects.requireNonNull(messenger, "messenger must be non-null");
        NetworkProviderInfo npi = new NetworkProviderInfo(name, messenger,
                nextNetworkProviderId(), () -> unregisterNetworkProvider(messenger));
        mHandler.sendMessage(mHandler.obtainMessage(EVENT_REGISTER_NETWORK_PROVIDER, npi));
        return npi.providerId;
    }

    @Override
    public void unregisterNetworkProvider(Messenger messenger) {
        enforceNetworkFactoryOrSettingsPermission();
        mHandler.sendMessage(mHandler.obtainMessage(EVENT_UNREGISTER_NETWORK_PROVIDER, messenger));
    }

    @Override
    public void offerNetwork(final int providerId,
            @NonNull final NetworkScore score, @NonNull final NetworkCapabilities caps,
            @NonNull final INetworkOfferCallback callback) {
        Objects.requireNonNull(score);
        Objects.requireNonNull(caps);
        Objects.requireNonNull(callback);
        if (caps.hasTransport(TRANSPORT_TEST)) {
            enforceAnyPermissionOf(mContext, Manifest.permission.MANAGE_TEST_NETWORKS);
        } else {
            enforceNetworkFactoryPermission();
        }
        final boolean yieldToBadWiFi = caps.hasTransport(TRANSPORT_CELLULAR) && !avoidBadWifi();
        final NetworkOffer offer = new NetworkOffer(
                FullScore.makeProspectiveScore(score, caps, yieldToBadWiFi),
                caps, callback, providerId);
        mHandler.sendMessage(mHandler.obtainMessage(EVENT_REGISTER_NETWORK_OFFER, offer));
    }

    private void updateOfferScore(final NetworkOffer offer) {
        final boolean yieldToBadWiFi =
                offer.caps.hasTransport(TRANSPORT_CELLULAR) && !avoidBadWifi();
        final NetworkOffer newOffer = new NetworkOffer(
                offer.score.withYieldToBadWiFi(yieldToBadWiFi),
                        offer.caps, offer.callback, offer.providerId);
        if (offer.equals(newOffer)) return;
        handleRegisterNetworkOffer(newOffer);
    }

    @Override
    public void unofferNetwork(@NonNull final INetworkOfferCallback callback) {
        Objects.requireNonNull(callback);
        mHandler.sendMessage(mHandler.obtainMessage(EVENT_UNREGISTER_NETWORK_OFFER, callback));
    }

    private void handleUnregisterNetworkProvider(Messenger messenger) {
        NetworkProviderInfo npi = mNetworkProviderInfos.remove(messenger);
        if (npi == null) {
            loge("Failed to find Messenger in unregisterNetworkProvider");
            return;
        }
        // Unregister all the offers from this provider
        final ArrayList<NetworkOfferInfo> toRemove = new ArrayList<>();
        for (final NetworkOfferInfo noi : mNetworkOffers) {
            if (noi.offer.providerId == npi.providerId) {
                // Can't call handleUnregisterNetworkOffer here because iteration is in progress
                toRemove.add(noi);
            }
        }
        for (final NetworkOfferInfo noi : toRemove) {
            handleUnregisterNetworkOffer(noi);
        }
        if (DBG) log("unregisterNetworkProvider for " + npi.name);
    }

    @Override
    public void declareNetworkRequestUnfulfillable(@NonNull final NetworkRequest request) {
        if (request.hasTransport(TRANSPORT_TEST)) {
            enforceNetworkFactoryOrTestNetworksPermission();
        } else {
            enforceNetworkFactoryPermission();
        }
        final NetworkRequestInfo nri = mNetworkRequests.get(request);
        if (nri != null) {
            // declareNetworkRequestUnfulfillable() paths don't apply to multilayer requests.
            ensureNotMultilayerRequest(nri, "declareNetworkRequestUnfulfillable");
            mHandler.post(() -> handleReleaseNetworkRequest(
                    nri.mRequests.get(0), mDeps.getCallingUid(), true));
        }
    }

    // NOTE: Accessed on multiple threads, must be synchronized on itself.
    @GuardedBy("mNetworkForNetId")
    private final SparseArray<NetworkAgentInfo> mNetworkForNetId = new SparseArray<>();
    // NOTE: Accessed on multiple threads, synchronized with mNetworkForNetId.
    // An entry is first reserved with NetIdManager, prior to being added to mNetworkForNetId, so
    // there may not be a strict 1:1 correlation between the two.
    private final NetIdManager mNetIdManager;

    // Tracks all NetworkAgents that are currently registered.
    // NOTE: Only should be accessed on ConnectivityServiceThread, except dump().
    private final ArraySet<NetworkAgentInfo> mNetworkAgentInfos = new ArraySet<>();

    // UID ranges for users that are currently blocked by VPNs.
    // This array is accessed and iterated on multiple threads without holding locks, so its
    // contents must never be mutated. When the ranges change, the array is replaced with a new one
    // (on the handler thread).
    private volatile List<UidRange> mVpnBlockedUidRanges = new ArrayList<>();

    // Must only be accessed on the handler thread
    @NonNull
    private final ArrayList<NetworkOfferInfo> mNetworkOffers = new ArrayList<>();

    @GuardedBy("mBlockedAppUids")
    private final HashSet<Integer> mBlockedAppUids = new HashSet<>();

    // Current OEM network preferences. This object must only be written to on the handler thread.
    // Since it is immutable and always non-null, other threads may read it if they only care
    // about seeing a consistent object but not that it is current.
    @NonNull
    private OemNetworkPreferences mOemNetworkPreferences =
            new OemNetworkPreferences.Builder().build();
    // Current per-profile network preferences. This object follows the same threading rules as
    // the OEM network preferences above.
    @NonNull
    private NetworkPreferenceList<UserHandle, ProfileNetworkPreferenceInfo>
            mProfileNetworkPreferences = new NetworkPreferenceList<>();

    // Current VPN network preferences. This object follows the same threading rules as the OEM
    // network preferences above.
    @NonNull
    private NetworkPreferenceList<String, VpnNetworkPreferenceInfo>
            mVpnNetworkPreferences = new NetworkPreferenceList<>();

    // A set of UIDs that should use mobile data preferentially if available. This object follows
    // the same threading rules as the OEM network preferences above.
    @NonNull
    private Set<Integer> mMobileDataPreferredUids = new ArraySet<>();

    // OemNetworkPreferences activity String log entries.
    private static final int MAX_OEM_NETWORK_PREFERENCE_LOGS = 20;
    @NonNull
    private final LocalLog mOemNetworkPreferencesLogs =
            new LocalLog(MAX_OEM_NETWORK_PREFERENCE_LOGS);

    /**
     * Determine whether a given package has a mapping in the current OemNetworkPreferences.
     * @param packageName the package name to check existence of a mapping for.
     * @return true if a mapping exists, false otherwise
     */
    private boolean isMappedInOemNetworkPreference(@NonNull final String packageName) {
        return mOemNetworkPreferences.getNetworkPreferences().containsKey(packageName);
    }

    // The always-on request for an Internet-capable network that apps without a specific default
    // fall back to.
    @VisibleForTesting
    @NonNull
    final NetworkRequestInfo mDefaultRequest;
    // Collection of NetworkRequestInfo's used for default networks.
    @VisibleForTesting
    @NonNull
    final ArraySet<NetworkRequestInfo> mDefaultNetworkRequests = new ArraySet<>();

    private boolean isPerAppDefaultRequest(@NonNull final NetworkRequestInfo nri) {
        return (mDefaultNetworkRequests.contains(nri) && mDefaultRequest != nri);
    }

    /**
     * Return the default network request currently tracking the given uid.
     * @param uid the uid to check.
     * @return the NetworkRequestInfo tracking the given uid.
     */
    @NonNull
    private NetworkRequestInfo getDefaultRequestTrackingUid(final int uid) {
        NetworkRequestInfo highestPriorityNri = mDefaultRequest;
        for (final NetworkRequestInfo nri : mDefaultNetworkRequests) {
            // Checking the first request is sufficient as only multilayer requests will have more
            // than one request and for multilayer, all requests will track the same uids.
            if (nri.mRequests.get(0).networkCapabilities.appliesToUid(uid)) {
                // Find out the highest priority request.
                if (nri.hasHigherOrderThan(highestPriorityNri)) {
                    highestPriorityNri = nri;
                }
            }
        }
        return highestPriorityNri;
    }

    /**
     * Get a copy of the network requests of the default request that is currently tracking the
     * given uid.
     * @param asUid the uid on behalf of which to file the request. Different from requestorUid
     *              when a privileged caller is tracking the default network for another uid.
     * @param requestorUid the uid to check the default for.
     * @param requestorPackageName the requestor's package name.
     * @return a copy of the default's NetworkRequest that is tracking the given uid.
     */
    @NonNull
    private List<NetworkRequest> copyDefaultNetworkRequestsForUid(
            final int asUid, final int requestorUid, @NonNull final String requestorPackageName) {
        return copyNetworkRequestsForUid(
                getDefaultRequestTrackingUid(asUid).mRequests,
                asUid, requestorUid, requestorPackageName);
    }

    /**
     * Copy the given nri's NetworkRequest collection.
     * @param requestsToCopy the NetworkRequest collection to be copied.
     * @param asUid the uid on behalf of which to file the request. Different from requestorUid
     *              when a privileged caller is tracking the default network for another uid.
     * @param requestorUid the uid to set on the copied collection.
     * @param requestorPackageName the package name to set on the copied collection.
     * @return the copied NetworkRequest collection.
     */
    @NonNull
    private List<NetworkRequest> copyNetworkRequestsForUid(
            @NonNull final List<NetworkRequest> requestsToCopy, final int asUid,
            final int requestorUid, @NonNull final String requestorPackageName) {
        final List<NetworkRequest> requests = new ArrayList<>();
        for (final NetworkRequest nr : requestsToCopy) {
            requests.add(new NetworkRequest(copyDefaultNetworkCapabilitiesForUid(
                            nr.networkCapabilities, asUid, requestorUid, requestorPackageName),
                    nr.legacyType, nextNetworkRequestId(), nr.type));
        }
        return requests;
    }

    @NonNull
    private NetworkCapabilities copyDefaultNetworkCapabilitiesForUid(
            @NonNull final NetworkCapabilities netCapToCopy, final int asUid,
            final int requestorUid, @NonNull final String requestorPackageName) {
        // These capabilities are for a TRACK_DEFAULT callback, so:
        // 1. Remove NET_CAPABILITY_VPN, because it's (currently!) the only difference between
        //    mDefaultRequest and a per-UID default request.
        //    TODO: stop depending on the fact that these two unrelated things happen to be the same
        // 2. Always set the UIDs to asUid. restrictRequestUidsForCallerAndSetRequestorInfo will
        //    not do this in the case of a privileged application.
        final NetworkCapabilities netCap = new NetworkCapabilities(netCapToCopy);
        netCap.removeCapability(NET_CAPABILITY_NOT_VPN);
        netCap.setSingleUid(asUid);
        restrictRequestUidsForCallerAndSetRequestorInfo(
                netCap, requestorUid, requestorPackageName);
        return netCap;
    }

    /**
     * Get the nri that is currently being tracked for callbacks by per-app defaults.
     * @param nr the network request to check for equality against.
     * @return the nri if one exists, null otherwise.
     */
    @Nullable
    private NetworkRequestInfo getNriForAppRequest(@NonNull final NetworkRequest nr) {
        for (final NetworkRequestInfo nri : mNetworkRequests.values()) {
            if (nri.getNetworkRequestForCallback().equals(nr)) {
                return nri;
            }
        }
        return null;
    }

    /**
     * Check if an nri is currently being managed by per-app default networking.
     * @param nri the nri to check.
     * @return true if this nri is currently being managed by per-app default networking.
     */
    private boolean isPerAppTrackedNri(@NonNull final NetworkRequestInfo nri) {
        // nri.mRequests.get(0) is only different from the original request filed in
        // nri.getNetworkRequestForCallback() if nri.mRequests was changed by per-app default
        // functionality therefore if these two don't match, it means this particular nri is
        // currently being managed by a per-app default.
        return nri.getNetworkRequestForCallback() != nri.mRequests.get(0);
    }

    /**
     * Determine if an nri is a managed default request that disallows default networking.
     * @param nri the request to evaluate
     * @return true if device-default networking is disallowed
     */
    private boolean isDefaultBlocked(@NonNull final NetworkRequestInfo nri) {
        // Check if this nri is a managed default that supports the default network at its
        // lowest priority request.
        final NetworkRequest defaultNetworkRequest = mDefaultRequest.mRequests.get(0);
        final NetworkCapabilities lowestPriorityNetCap =
                nri.mRequests.get(nri.mRequests.size() - 1).networkCapabilities;
        return isPerAppDefaultRequest(nri)
                && !(defaultNetworkRequest.networkCapabilities.equalRequestableCapabilities(
                        lowestPriorityNetCap));
    }

    // Request used to optionally keep mobile data active even when higher
    // priority networks like Wi-Fi are active.
    private final NetworkRequest mDefaultMobileDataRequest;

    // Request used to optionally keep wifi data active even when higher
    // priority networks like ethernet are active.
    private final NetworkRequest mDefaultWifiRequest;

    // Request used to optionally keep vehicle internal network always active
    private final NetworkRequest mDefaultVehicleRequest;

    // Sentinel NAI used to direct apps with default networks that should have no connectivity to a
    // network with no service. This NAI should never be matched against, nor should any public API
    // ever return the associated network. For this reason, this NAI is not in the list of available
    // NAIs. It is used in computeNetworkReassignment() to be set as the satisfier for non-device
    // default requests that don't support using the device default network which will ultimately
    // allow ConnectivityService to use this no-service network when calling makeDefaultForApps().
    @VisibleForTesting
    final NetworkAgentInfo mNoServiceNetwork;

    // The NetworkAgentInfo currently satisfying the default request, if any.
    private NetworkAgentInfo getDefaultNetwork() {
        return mDefaultRequest.mSatisfier;
    }

    private NetworkAgentInfo getDefaultNetworkForUid(final int uid) {
        NetworkRequestInfo highestPriorityNri = mDefaultRequest;
        for (final NetworkRequestInfo nri : mDefaultNetworkRequests) {
            // Currently, all network requests will have the same uids therefore checking the first
            // one is sufficient. If/when uids are tracked at the nri level, this can change.
            final Set<UidRange> uids = nri.mRequests.get(0).networkCapabilities.getUidRanges();
            if (null == uids) {
                continue;
            }
            for (final UidRange range : uids) {
                if (range.contains(uid)) {
                    if (nri.hasHigherOrderThan(highestPriorityNri)) {
                        highestPriorityNri = nri;
                    }
                }
            }
        }
        if (!highestPriorityNri.isBeingSatisfied()) return null;
        return highestPriorityNri.getSatisfier();
    }

    @Nullable
    private Network getNetwork(@Nullable NetworkAgentInfo nai) {
        return nai != null ? nai.network : null;
    }

    private void ensureRunningOnConnectivityServiceThread() {
        if (mHandler.getLooper().getThread() != Thread.currentThread()) {
            throw new IllegalStateException(
                    "Not running on ConnectivityService thread: "
                            + Thread.currentThread().getName());
        }
    }

    @VisibleForTesting
    protected boolean isDefaultNetwork(NetworkAgentInfo nai) {
        return nai == getDefaultNetwork();
    }

    /**
     * Returns whether local agents are supported on this device.
     *
     * Local agents are supported from U on TVs, and from V on all devices.
     */
    @VisibleForTesting
    public boolean areLocalAgentsSupported() {
        final PackageManager pm = mContext.getPackageManager();
        // Local agents are supported starting on U on TVs and on V on everything else.
        return mDeps.isAtLeastV() || (mDeps.isAtLeastU() && pm.hasSystemFeature(FEATURE_LEANBACK));
    }

    /**
     * Register a new agent with ConnectivityService to handle a network.
     *
     * @param na a reference for ConnectivityService to contact the agent asynchronously.
     * @param networkInfo the initial info associated with this network. It can be updated later :
     *         see {@link #updateNetworkInfo}.
     * @param linkProperties the initial link properties of this network. They can be updated
     *         later : see {@link #updateLinkProperties}.
     * @param networkCapabilities the initial capabilites of this network. They can be updated
     *         later : see {@link #updateCapabilities}.
     * @param initialScore the initial score of the network. See {@link NetworkAgentInfo#getScore}.
     * @param localNetworkConfig config about this local network, or null if not a local network
     * @param networkAgentConfig metadata about the network. This is never updated.
     * @param providerId the ID of the provider owning this NetworkAgent.
     * @return the network created for this agent.
     */
    public Network registerNetworkAgent(INetworkAgent na,
            NetworkInfo networkInfo,
            LinkProperties linkProperties,
            NetworkCapabilities networkCapabilities,
            @NonNull NetworkScore initialScore,
            @Nullable LocalNetworkConfig localNetworkConfig,
            NetworkAgentConfig networkAgentConfig,
            int providerId) {
        Objects.requireNonNull(networkInfo, "networkInfo must not be null");
        Objects.requireNonNull(linkProperties, "linkProperties must not be null");
        Objects.requireNonNull(networkCapabilities, "networkCapabilities must not be null");
        Objects.requireNonNull(initialScore, "initialScore must not be null");
        Objects.requireNonNull(networkAgentConfig, "networkAgentConfig must not be null");
        if (networkCapabilities.hasTransport(TRANSPORT_TEST)) {
            enforceAnyPermissionOf(mContext, Manifest.permission.MANAGE_TEST_NETWORKS);
        } else {
            enforceNetworkFactoryPermission();
        }
        final boolean hasLocalCap =
                networkCapabilities.hasCapability(NET_CAPABILITY_LOCAL_NETWORK);
        if (hasLocalCap && !areLocalAgentsSupported()) {
            // Before U, netd doesn't support PHYSICAL_LOCAL networks so this can't work.
            throw new IllegalArgumentException("Local agents are not supported in this version");
        }
        final boolean hasLocalNetworkConfig = null != localNetworkConfig;
        if (hasLocalCap != hasLocalNetworkConfig) {
            throw new IllegalArgumentException(null != localNetworkConfig
                    ? "Only local network agents can have a LocalNetworkConfig"
                    : "Local network agents must have a LocalNetworkConfig"
            );
        }

        final int uid = mDeps.getCallingUid();
        final long token = Binder.clearCallingIdentity();
        try {
            return registerNetworkAgentInternal(na, networkInfo, linkProperties,
                    networkCapabilities, initialScore, networkAgentConfig, localNetworkConfig,
                    providerId, uid);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    private Network registerNetworkAgentInternal(INetworkAgent na, NetworkInfo networkInfo,
            LinkProperties linkProperties, NetworkCapabilities networkCapabilities,
            NetworkScore currentScore, NetworkAgentConfig networkAgentConfig,
            @Nullable LocalNetworkConfig localNetworkConfig, int providerId,
            int uid) {

        // Make a copy of the passed NI, LP, NC as the caller may hold a reference to them
        // and mutate them at any time.
        final NetworkInfo niCopy = new NetworkInfo(networkInfo);
        final NetworkCapabilities ncCopy = new NetworkCapabilities(networkCapabilities);
        final LinkProperties lpCopy = new LinkProperties(linkProperties);
        // No need to copy |localNetworkConfiguration| as it is immutable.

        // At this point the capabilities/properties are untrusted and unverified, e.g. checks that
        // the capabilities' access UIDs comply with security limitations. They will be sanitized
        // as the NAI registration finishes, in handleRegisterNetworkAgent(). This is
        // because some of the checks must happen on the handler thread.
        final NetworkAgentInfo nai = new NetworkAgentInfo(na,
                new Network(mNetIdManager.reserveNetId()), niCopy, lpCopy, ncCopy,
                localNetworkConfig, currentScore, mContext, mTrackerHandler,
                new NetworkAgentConfig(networkAgentConfig), this, mNetd, mDnsResolver, providerId,
                uid, mLingerDelayMs, mQosCallbackTracker, mDeps);

        final String extraInfo = niCopy.getExtraInfo();
        final String name = TextUtils.isEmpty(extraInfo)
                ? nai.networkCapabilities.getSsid() : extraInfo;
        if (DBG) log("registerNetworkAgent " + nai);
        mDeps.getNetworkStack().makeNetworkMonitor(
                nai.network, name, new NetworkMonitorCallbacks(nai));
        // NetworkAgentInfo registration will finish when the NetworkMonitor is created.
        // If the network disconnects or sends any other event before that, messages are deferred by
        // NetworkAgent until nai.connect(), which will be called when finalizing the
        // registration.
        return nai.network;
    }

    private void handleRegisterNetworkAgent(NetworkAgentInfo nai, INetworkMonitor networkMonitor) {
        if (VDBG) log("Network Monitor created for " +  nai);
        // Store a copy of the declared capabilities.
        nai.setDeclaredCapabilities(nai.networkCapabilities);
        // Make sure the LinkProperties and NetworkCapabilities reflect what the agent info said.
        nai.getAndSetNetworkCapabilities(mixInCapabilities(nai,
                nai.getDeclaredCapabilitiesSanitized(mCarrierPrivilegeAuthenticator)));
        processLinkPropertiesFromAgent(nai, nai.linkProperties);

        nai.onNetworkMonitorCreated(networkMonitor);

        mNetworkAgentInfos.add(nai);
        synchronized (mNetworkForNetId) {
            mNetworkForNetId.put(nai.network.getNetId(), nai);
        }

        try {
            networkMonitor.start();
        } catch (RemoteException e) {
            e.rethrowAsRuntimeException();
        }

        if (nai.isLocalNetwork()) {
            handleUpdateLocalNetworkConfig(nai, null /* oldConfig */, nai.localNetworkConfig);
        }
        nai.notifyRegistered();
        NetworkInfo networkInfo = nai.networkInfo;
        updateNetworkInfo(nai, networkInfo);
        updateDisallowedUidsForNetwork(nai);
        updateVpnUids(nai, null, nai.networkCapabilities);
    }

    private class NetworkOfferInfo implements IBinder.DeathRecipient {
        @NonNull public final NetworkOffer offer;

        NetworkOfferInfo(@NonNull final NetworkOffer offer) {
            this.offer = offer;
        }

        @Override
        public void binderDied() {
            mHandler.post(() -> handleUnregisterNetworkOffer(this));
        }
    }

    private boolean isNetworkProviderWithIdRegistered(final int providerId) {
        for (final NetworkProviderInfo npi : mNetworkProviderInfos.values()) {
            if (npi.providerId == providerId) return true;
        }
        return false;
    }

    /**
     * Register or update a network offer.
     * @param newOffer The new offer. If the callback member is the same as an existing
     *                 offer, it is an update of that offer.
     */
    // TODO : rename this to handleRegisterOrUpdateNetworkOffer
    private void handleRegisterNetworkOffer(@NonNull final NetworkOffer newOffer) {
        ensureRunningOnConnectivityServiceThread();
        if (!isNetworkProviderWithIdRegistered(newOffer.providerId)) {
            // This may actually happen if a provider updates its score or registers and then
            // immediately unregisters. The offer would still be in the handler queue, but the
            // provider would have been removed.
            if (DBG) log("Received offer from an unregistered provider");
            return;
        }
        final NetworkOfferInfo existingOffer = findNetworkOfferInfoByCallback(newOffer.callback);
        if (null != existingOffer) {
            handleUnregisterNetworkOffer(existingOffer);
            newOffer.migrateFrom(existingOffer.offer);
            if (DBG) {
                // handleUnregisterNetworkOffer has already logged the old offer
                log("update offer from providerId " + newOffer.providerId + " new : " + newOffer);
            }
        } else {
            if (DBG) {
                log("register offer from providerId " + newOffer.providerId + " : " + newOffer);
            }
        }
        final NetworkOfferInfo noi = new NetworkOfferInfo(newOffer);
        try {
            noi.offer.callback.asBinder().linkToDeath(noi, 0 /* flags */);
        } catch (RemoteException e) {
            noi.binderDied();
            return;
        }
        mNetworkOffers.add(noi);
        issueNetworkNeeds(noi);
    }

    private void handleUnregisterNetworkOffer(@NonNull final NetworkOfferInfo noi) {
        ensureRunningOnConnectivityServiceThread();
        if (DBG) {
            log("unregister offer from providerId " + noi.offer.providerId + " : " + noi.offer);
        }

        // If the provider removes the offer and dies immediately afterwards this
        // function may be called twice in a row, but the array will no longer contain
        // the offer.
        if (!mNetworkOffers.remove(noi)) return;
        noi.offer.callback.asBinder().unlinkToDeath(noi, 0 /* flags */);
    }

    @Nullable private NetworkOfferInfo findNetworkOfferInfoByCallback(
            @NonNull final INetworkOfferCallback callback) {
        ensureRunningOnConnectivityServiceThread();
        for (final NetworkOfferInfo noi : mNetworkOffers) {
            if (noi.offer.callback.asBinder().equals(callback.asBinder())) return noi;
        }
        return null;
    }

    /**
     * Called when receiving LinkProperties directly from a NetworkAgent.
     * Stores into |nai| any data coming from the agent that might also be written to the network's
     * LinkProperties by ConnectivityService itself. This ensures that the data provided by the
     * agent is not lost when updateLinkProperties is called.
     * This method should never alter the agent's LinkProperties, only store data in |nai|.
     */
    private void processLinkPropertiesFromAgent(NetworkAgentInfo nai, LinkProperties lp) {
        lp.ensureDirectlyConnectedRoutes();
        nai.clatd.setNat64PrefixFromRa(lp.getNat64Prefix());
        nai.networkAgentPortalData = lp.getCaptivePortalData();
    }

    private void updateLinkProperties(NetworkAgentInfo networkAgent, @NonNull LinkProperties newLp,
            @Nullable LinkProperties oldLp) {
        int netId = networkAgent.network.getNetId();

        // The NetworkAgent does not know whether clatd is running on its network or not, or whether
        // a NAT64 prefix was discovered by the DNS resolver. Before we do anything else, make sure
        // the LinkProperties for the network are accurate.
        networkAgent.clatd.fixupLinkProperties(oldLp, newLp);

        final boolean anyIfaceChanges =
                updateInterfaces(newLp, oldLp, netId, networkAgent);

        // If any interface names changed, ensure VPN filtering is updated so that ingress filtering
        // uses the most up-to-date allowed interface.
        final boolean shouldForceUpdateVpnFiltering = networkAgent.isVPN() && anyIfaceChanges;

        // update filtering rules, need to happen after the interface update so netd knows about the
        // new interface (the interface name -> index map becomes initialized)
        updateVpnFiltering(newLp, oldLp, networkAgent, shouldForceUpdateVpnFiltering);

        updateIngressToVpnAddressFiltering(newLp, oldLp, networkAgent);

        updateMtu(newLp, oldLp);
        // TODO - figure out what to do for clat
//        for (LinkProperties lp : newLp.getStackedLinks()) {
//            updateMtu(lp, null);
//        }
        if (isDefaultNetwork(networkAgent)) {
            mProxyTracker.updateDefaultNetworkProxyPortForPAC(newLp, null);
            updateTcpBufferSizes(newLp.getTcpBufferSizes());
        }

        updateRoutes(newLp, oldLp, netId);
        updateDnses(newLp, oldLp, netId);
        // Make sure LinkProperties represents the latest private DNS status.
        // This does not need to be done before updateDnses because the
        // LinkProperties are not the source of the private DNS configuration.
        // updateDnses will fetch the private DNS configuration from DnsManager.
        mDnsManager.updatePrivateDnsStatus(netId, newLp);

        if (isDefaultNetwork(networkAgent)) {
            mProxyTracker.setDefaultProxy(newLp.getHttpProxy());
        } else if (networkAgent.everConnected()) {
            updateProxy(newLp, oldLp);
        }

        updateWakeOnLan(newLp);

        // Captive portal data is obtained from NetworkMonitor and stored in NetworkAgentInfo.
        // It is not always contained in the LinkProperties sent from NetworkAgents, and if it
        // does, it needs to be merged here.
        newLp.setCaptivePortalData(mergeCaptivePortalData(networkAgent.networkAgentPortalData,
                networkAgent.capportApiData));

        // TODO - move this check to cover the whole function
        if (!Objects.equals(newLp, oldLp)) {
            synchronized (networkAgent) {
                networkAgent.linkProperties = newLp;
            }
            // Start or stop DNS64 detection and 464xlat according to network state.
            networkAgent.clatd.update();
            // Notify NSS when relevant events happened. Currently, NSS only cares about
            // interface changed to update clat interfaces accounting.
            final boolean interfacesChanged = oldLp == null
                    || !Objects.equals(newLp.getAllInterfaceNames(), oldLp.getAllInterfaceNames());
            if (interfacesChanged) {
                notifyIfacesChangedForNetworkStats();
            }
            networkAgent.networkMonitor().notifyLinkPropertiesChanged(
                    new LinkProperties(newLp, true /* parcelSensitiveFields */));
            notifyNetworkCallbacks(networkAgent, ConnectivityManager.CALLBACK_IP_CHANGED);
        }

        mKeepaliveTracker.handleCheckKeepalivesStillValid(networkAgent);
    }

    private void applyInitialLinkProperties(@NonNull NetworkAgentInfo nai) {
        updateLinkProperties(nai, new LinkProperties(nai.linkProperties), null);
    }

    /**
     * @param naData captive portal data from NetworkAgent
     * @param apiData captive portal data from capport API
     */
    @Nullable
    private CaptivePortalData mergeCaptivePortalData(CaptivePortalData naData,
            CaptivePortalData apiData) {
        if (naData == null || apiData == null) {
            return naData == null ? apiData : naData;
        }
        final CaptivePortalData.Builder captivePortalBuilder =
                new CaptivePortalData.Builder(naData);

        if (apiData.isCaptive()) {
            captivePortalBuilder.setCaptive(true);
        }
        if (apiData.isSessionExtendable()) {
            captivePortalBuilder.setSessionExtendable(true);
        }
        if (apiData.getExpiryTimeMillis() >= 0 || apiData.getByteLimit() >= 0) {
            // Expiry time, bytes remaining, refresh time all need to come from the same source,
            // otherwise data would be inconsistent. Prefer the capport API info if present,
            // as it can generally be refreshed more often.
            captivePortalBuilder.setExpiryTime(apiData.getExpiryTimeMillis());
            captivePortalBuilder.setBytesRemaining(apiData.getByteLimit());
            captivePortalBuilder.setRefreshTime(apiData.getRefreshTimeMillis());
        } else if (naData.getExpiryTimeMillis() < 0 && naData.getByteLimit() < 0) {
            // No source has time / bytes remaining information: surface the newest refresh time
            // for other fields
            captivePortalBuilder.setRefreshTime(
                    Math.max(naData.getRefreshTimeMillis(), apiData.getRefreshTimeMillis()));
        }

        // Prioritize the user portal URL from the network agent if the source is authenticated.
        if (apiData.getUserPortalUrl() != null && naData.getUserPortalUrlSource()
                != CaptivePortalData.CAPTIVE_PORTAL_DATA_SOURCE_PASSPOINT) {
            captivePortalBuilder.setUserPortalUrl(apiData.getUserPortalUrl(),
                    apiData.getUserPortalUrlSource());
        }
        // Prioritize the venue information URL from the network agent if the source is
        // authenticated.
        if (apiData.getVenueInfoUrl() != null && naData.getVenueInfoUrlSource()
                != CaptivePortalData.CAPTIVE_PORTAL_DATA_SOURCE_PASSPOINT) {
            captivePortalBuilder.setVenueInfoUrl(apiData.getVenueInfoUrl(),
                    apiData.getVenueInfoUrlSource());
        }
        return captivePortalBuilder.build();
    }

    @VisibleForTesting
    static String makeNflogPrefix(String iface, long networkHandle) {
        // This needs to be kept in sync and backwards compatible with the decoding logic in
        // NetdEventListenerService, which is non-mainline code.
        return SdkLevel.isAtLeastU() ? (networkHandle + ":" + iface) : ("iface:" + iface);
    }

    private static boolean isWakeupMarkingSupported(NetworkCapabilities capabilities) {
        if (capabilities.hasTransport(TRANSPORT_WIFI)) {
            return true;
        }
        if (SdkLevel.isAtLeastU() && capabilities.hasTransport(TRANSPORT_CELLULAR)) {
            return true;
        }
        return false;
    }

    private void wakeupModifyInterface(String iface, NetworkAgentInfo nai, boolean add) {
        // Marks are only available on WiFi interfaces. Checking for
        // marks on unsupported interfaces is harmless.
        if (!isWakeupMarkingSupported(nai.networkCapabilities)) {
            return;
        }

        // Mask/mark of zero will not detect anything interesting.
        // Don't install rules unless both values are nonzero.
        if (mWakeUpMark == 0 || mWakeUpMask == 0) {
            return;
        }

        final String prefix = makeNflogPrefix(iface, nai.network.getNetworkHandle());
        try {
            if (add) {
                mNetd.wakeupAddInterface(iface, prefix, mWakeUpMark, mWakeUpMask);
            } else {
                mNetd.wakeupDelInterface(iface, prefix, mWakeUpMark, mWakeUpMask);
            }
        } catch (Exception e) {
            loge("Exception modifying wakeup packet monitoring: " + e);
        }
    }

    /** Return whether there were any added or removed interface names. */
    private boolean updateInterfaces(final @NonNull LinkProperties newLp,
            final @Nullable LinkProperties oldLp, final int netId,
            final @NonNull NetworkAgentInfo nai) {
        final CompareResult<String> interfaceDiff = new CompareResult<>(
                oldLp != null ? oldLp.getAllInterfaceNames() : null, newLp.getAllInterfaceNames());
        if (!interfaceDiff.added.isEmpty()) {
            for (final String iface : interfaceDiff.added) {
                try {
                    if (DBG) log("Adding iface " + iface + " to network " + netId);
                    mRoutingCoordinatorService.addInterfaceToNetwork(netId, iface);
                    wakeupModifyInterface(iface, nai, true);
                    mDeps.reportNetworkInterfaceForTransports(mContext, iface,
                            nai.networkCapabilities.getTransportTypes());
                } catch (Exception e) {
                    logw("Exception adding interface: " + e);
                }
            }
        }
        for (final String iface : interfaceDiff.removed) {
            try {
                if (DBG) log("Removing iface " + iface + " from network " + netId);
                wakeupModifyInterface(iface, nai, false);
                mRoutingCoordinatorService.removeInterfaceFromNetwork(netId, iface);
            } catch (Exception e) {
                loge("Exception removing interface: " + e);
            }
        }
        return !(interfaceDiff.added.isEmpty() && interfaceDiff.removed.isEmpty());
    }

    /**
     * Have netd update routes from oldLp to newLp.
     * @return true if routes changed between oldLp and newLp
     */
    private boolean updateRoutes(@NonNull LinkProperties newLp, @Nullable LinkProperties oldLp,
            int netId) {
        // compare the route diff to determine which routes have been updated
        final CompareOrUpdateResult<RouteInfo.RouteKey, RouteInfo> routeDiff =
                new CompareOrUpdateResult<>(
                        oldLp != null ? oldLp.getAllRoutes() : null,
                        newLp.getAllRoutes(),
                        (r) -> r.getRouteKey());

        // add routes before removing old in case it helps with continuous connectivity

        // do this twice, adding non-next-hop routes first, then routes they are dependent on
        for (RouteInfo route : routeDiff.added) {
            if (route.hasGateway()) continue;
            if (VDBG || DDBG) log("Adding Route [" + route + "] to network " + netId);
            try {
                mRoutingCoordinatorService.addRoute(netId, route);
            } catch (Exception e) {
                if ((route.getDestination().getAddress() instanceof Inet4Address) || VDBG) {
                    loge("Exception in addRoute for non-gateway: " + e);
                }
            }
        }
        for (RouteInfo route : routeDiff.added) {
            if (!route.hasGateway()) continue;
            if (VDBG || DDBG) log("Adding Route [" + route + "] to network " + netId);
            try {
                mRoutingCoordinatorService.addRoute(netId, route);
            } catch (Exception e) {
                if ((route.getGateway() instanceof Inet4Address) || VDBG) {
                    loge("Exception in addRoute for gateway: " + e);
                }
            }
        }

        for (RouteInfo route : routeDiff.removed) {
            if (VDBG || DDBG) log("Removing Route [" + route + "] from network " + netId);
            try {
                mRoutingCoordinatorService.removeRoute(netId, route);
            } catch (Exception e) {
                loge("Exception in removeRoute: " + e);
            }
        }

        for (RouteInfo route : routeDiff.updated) {
            if (VDBG || DDBG) log("Updating Route [" + route + "] from network " + netId);
            try {
                mRoutingCoordinatorService.updateRoute(netId, route);
            } catch (Exception e) {
                loge("Exception in updateRoute: " + e);
            }
        }
        return !routeDiff.added.isEmpty() || !routeDiff.removed.isEmpty()
                || !routeDiff.updated.isEmpty();
    }

    private void updateDnses(@NonNull LinkProperties newLp, @Nullable LinkProperties oldLp,
            int netId) {
        if (oldLp != null && newLp.isIdenticalDnses(oldLp)) {
            return;  // no updating necessary
        }

        if (DBG) {
            final Collection<InetAddress> dnses = newLp.getDnsServers();
            log("Setting DNS servers for network " + netId + " to " + dnses);
        }
        try {
            mDnsManager.noteDnsServersForNetwork(netId, newLp);
            mDnsManager.flushVmDnsCache();
        } catch (Exception e) {
            loge("Exception in setDnsConfigurationForNetwork: " + e);
        }
    }

    private void updateVpnFiltering(@NonNull LinkProperties newLp, @Nullable LinkProperties oldLp,
            @NonNull NetworkAgentInfo nai, boolean force) {
        final String oldIface = getVpnIsolationInterface(nai, nai.networkCapabilities, oldLp);
        final String newIface = getVpnIsolationInterface(nai, nai.networkCapabilities, newLp);
        final boolean wasFiltering = requiresVpnAllowRule(nai, oldLp, oldIface);
        final boolean needsFiltering = requiresVpnAllowRule(nai, newLp, newIface);

        if (!wasFiltering && !needsFiltering) {
            // Nothing to do.
            return;
        }

        if (!force && Objects.equals(oldIface, newIface) && (wasFiltering == needsFiltering)) {
            // Nothing changed.
            return;
        }

        final Set<UidRange> ranges = nai.networkCapabilities.getUidRanges();
        if (ranges == null || ranges.isEmpty()) {
            return;
        }

        final int vpnAppUid = nai.networkCapabilities.getOwnerUid();
        // TODO: this create a window of opportunity for apps to receive traffic between the time
        // when the old rules are removed and the time when new rules are added. To fix this,
        // make eBPF support two allowlisted interfaces so here new rules can be added before the
        // old rules are being removed.
        if (wasFiltering) {
            mPermissionMonitor.onVpnUidRangesRemoved(oldIface, ranges, vpnAppUid);
            mPermissionMonitor.updateVpnLockdownUidInterfaceRules(oldLp.getInterfaceName(), ranges,
                    vpnAppUid, false /* add */);
        }
        if (needsFiltering) {
            mPermissionMonitor.onVpnUidRangesAdded(newIface, ranges, vpnAppUid);
            mPermissionMonitor.updateVpnLockdownUidInterfaceRules(newLp.getInterfaceName(), ranges,
                    vpnAppUid, true /* add */);
        }
    }

    /**
     * Returns ingress discard rules to drop packets to VPN addresses ingressing via non-VPN
     * interfaces.
     * Ingress discard rule is added to the address iff
     *   1. The address is not a link local address
     *   2. The address is used by a single interface of VPN whose VPN type is not TYPE_VPN_LEGACY
     *      or TYPE_VPN_OEM and the address is not used by any other interfaces even non-VPN ones
     * Ingress discard rule is not be added to TYPE_VPN_LEGACY or TYPE_VPN_OEM VPN since these VPNs
     * might need to receive packet to VPN address via non-VPN interface.
     * This method can be called during network disconnects, when nai has already been removed from
     * mNetworkAgentInfos.
     *
     * @param nai This method generates rules assuming lp of this nai is the lp at the second
     *            argument.
     * @param lp  This method generates rules assuming lp of nai at the first argument is this lp.
     *            Caller passes old lp to generate old rules and new lp to generate new rules.
     * @return    ingress discard rules. Set of pairs of addresses and interface names
     */
    private Set<Pair<InetAddress, String>> generateIngressDiscardRules(
            @NonNull final NetworkAgentInfo nai, @Nullable final LinkProperties lp) {
        Set<NetworkAgentInfo> nais = new ArraySet<>(mNetworkAgentInfos);
        nais.add(nai);
        // Determine how many networks each IP address is currently configured on.
        // Ingress rules are added only for IP addresses that are configured on single interface.
        final Map<InetAddress, Integer> addressOwnerCounts = new ArrayMap<>();
        for (final NetworkAgentInfo agent : nais) {
            if (agent.isDestroyed()) {
                continue;
            }
            final LinkProperties agentLp = (nai == agent) ? lp : agent.linkProperties;
            if (agentLp == null) {
                continue;
            }
            for (final InetAddress addr: agentLp.getAllAddresses()) {
                addressOwnerCounts.put(addr, addressOwnerCounts.getOrDefault(addr, 0) + 1);
            }
        }

        // Iterates all networks instead of only generating rule for nai that was passed in since
        // lp of the nai change could cause/resolve address collision and result in affecting rule
        // for different network.
        final Set<Pair<InetAddress, String>> ingressDiscardRules = new ArraySet<>();
        for (final NetworkAgentInfo agent : nais) {
            final int vpnType = getVpnType(agent);
            if (!agent.isVPN() || agent.isDestroyed()
                    || vpnType == VpnManager.TYPE_VPN_LEGACY
                    || vpnType == VpnManager.TYPE_VPN_OEM) {
                continue;
            }
            final LinkProperties agentLp = (nai == agent) ? lp : agent.linkProperties;
            if (agentLp == null || agentLp.getInterfaceName() == null) {
                continue;
            }

            for (final InetAddress addr: agentLp.getAllAddresses()) {
                if (addressOwnerCounts.get(addr) == 1 && !addr.isLinkLocalAddress()) {
                    ingressDiscardRules.add(new Pair<>(addr, agentLp.getInterfaceName()));
                }
            }
        }
        return ingressDiscardRules;
    }

    private void updateIngressToVpnAddressFiltering(@Nullable LinkProperties newLp,
            @Nullable LinkProperties oldLp, @NonNull NetworkAgentInfo nai) {
        // Having isAtleastT to avoid NewApi linter error (b/303382209)
        if (!mIngressToVpnAddressFiltering || !mDeps.isAtLeastT()) {
            return;
        }
        final CompareOrUpdateResult<InetAddress, Pair<InetAddress, String>> ruleDiff =
                new CompareOrUpdateResult<>(
                        generateIngressDiscardRules(nai, oldLp),
                        generateIngressDiscardRules(nai, newLp),
                        (rule) -> rule.first);
        for (Pair<InetAddress, String> rule: ruleDiff.removed) {
            mBpfNetMaps.removeIngressDiscardRule(rule.first);
        }
        for (Pair<InetAddress, String> rule: ruleDiff.added) {
            mBpfNetMaps.setIngressDiscardRule(rule.first, rule.second);
        }
        // setIngressDiscardRule overrides the existing rule
        for (Pair<InetAddress, String> rule: ruleDiff.updated) {
            mBpfNetMaps.setIngressDiscardRule(rule.first, rule.second);
        }
    }

    private void updateWakeOnLan(@NonNull LinkProperties lp) {
        if (mWolSupportedInterfaces == null) {
            mWolSupportedInterfaces = new ArraySet<>(mResources.get().getStringArray(
                    R.array.config_wakeonlan_supported_interfaces));
        }
        lp.setWakeOnLanSupported(mWolSupportedInterfaces.contains(lp.getInterfaceName()));
    }

    private int getNetworkPermission(NetworkCapabilities nc) {
        if (!nc.hasCapability(NET_CAPABILITY_NOT_RESTRICTED)) {
            return INetd.PERMISSION_SYSTEM;
        }
        if (!nc.hasCapability(NET_CAPABILITY_FOREGROUND)) {
            return INetd.PERMISSION_NETWORK;
        }
        return INetd.PERMISSION_NONE;
    }

    private void updateNetworkPermissions(@NonNull final NetworkAgentInfo nai,
            @NonNull final NetworkCapabilities newNc) {
        final int oldPermission = getNetworkPermission(nai.networkCapabilities);
        final int newPermission = getNetworkPermission(newNc);
        if (oldPermission != newPermission && nai.isCreated() && !nai.isVPN()) {
            try {
                mNetd.networkSetPermissionForNetwork(nai.network.getNetId(), newPermission);
            } catch (RemoteException | ServiceSpecificException e) {
                loge("Exception in networkSetPermissionForNetwork: " + e);
            }
        }
    }

    /** Modifies |newNc| based on the capabilities of |underlyingNetworks| and |agentCaps|. */
    @VisibleForTesting
    void applyUnderlyingCapabilities(@Nullable Network[] underlyingNetworks,
            @NonNull NetworkCapabilities agentCaps, @NonNull NetworkCapabilities newNc) {
        underlyingNetworks = underlyingNetworksOrDefault(
                agentCaps.getOwnerUid(), underlyingNetworks);
        long transportTypes = BitUtils.packBits(agentCaps.getTransportTypes());
        int downKbps = NetworkCapabilities.LINK_BANDWIDTH_UNSPECIFIED;
        int upKbps = NetworkCapabilities.LINK_BANDWIDTH_UNSPECIFIED;
        // metered if any underlying is metered, or originally declared metered by the agent.
        boolean metered = !agentCaps.hasCapability(NET_CAPABILITY_NOT_METERED);
        boolean roaming = false; // roaming if any underlying is roaming
        boolean congested = false; // congested if any underlying is congested
        boolean suspended = true; // suspended if all underlying are suspended

        boolean hadUnderlyingNetworks = false;
        ArrayList<Network> newUnderlyingNetworks = null;
        if (null != underlyingNetworks) {
            newUnderlyingNetworks = new ArrayList<>();
            for (Network underlyingNetwork : underlyingNetworks) {
                final NetworkAgentInfo underlying =
                        getNetworkAgentInfoForNetwork(underlyingNetwork);
                if (underlying == null) continue;

                final NetworkCapabilities underlyingCaps = underlying.networkCapabilities;
                hadUnderlyingNetworks = true;
                for (int underlyingType : underlyingCaps.getTransportTypes()) {
                    transportTypes |= 1L << underlyingType;
                }

                // Merge capabilities of this underlying network. For bandwidth, assume the
                // worst case.
                downKbps = NetworkCapabilities.minBandwidth(downKbps,
                        underlyingCaps.getLinkDownstreamBandwidthKbps());
                upKbps = NetworkCapabilities.minBandwidth(upKbps,
                        underlyingCaps.getLinkUpstreamBandwidthKbps());
                // If this underlying network is metered, the VPN is metered (it may cost money
                // to send packets on this network).
                metered |= !underlyingCaps.hasCapability(NET_CAPABILITY_NOT_METERED);
                // If this underlying network is roaming, the VPN is roaming (the billing structure
                // is different than the usual, local one).
                roaming |= !underlyingCaps.hasCapability(NET_CAPABILITY_NOT_ROAMING);
                // If this underlying network is congested, the VPN is congested (the current
                // condition of the network affects the performance of this network).
                congested |= !underlyingCaps.hasCapability(NET_CAPABILITY_NOT_CONGESTED);
                // If this network is not suspended, the VPN is not suspended (the VPN
                // is able to transfer some data).
                suspended &= !underlyingCaps.hasCapability(NET_CAPABILITY_NOT_SUSPENDED);
                newUnderlyingNetworks.add(underlyingNetwork);
            }
        }
        if (!hadUnderlyingNetworks) {
            // No idea what the underlying networks are; assume reasonable defaults
            metered = true;
            roaming = false;
            congested = false;
            suspended = false;
        }

        newNc.setTransportTypes(BitUtils.unpackBits(transportTypes));
        newNc.setLinkDownstreamBandwidthKbps(downKbps);
        newNc.setLinkUpstreamBandwidthKbps(upKbps);
        newNc.setCapability(NET_CAPABILITY_NOT_METERED, !metered);
        newNc.setCapability(NET_CAPABILITY_NOT_ROAMING, !roaming);
        newNc.setCapability(NET_CAPABILITY_NOT_CONGESTED, !congested);
        newNc.setCapability(NET_CAPABILITY_NOT_SUSPENDED, !suspended);
        newNc.setUnderlyingNetworks(newUnderlyingNetworks);
    }

    /**
     * Augments the NetworkCapabilities passed in by a NetworkAgent with capabilities that are
     * maintained here that the NetworkAgent is not aware of (e.g., validated, captive portal,
     * and foreground status).
     */
    @NonNull
    private NetworkCapabilities mixInCapabilities(NetworkAgentInfo nai, NetworkCapabilities nc) {
        // Once a NetworkAgent is connected, complain if some immutable capabilities are removed.
         // Don't complain for VPNs since they're not driven by requests and there is no risk of
         // causing a connect/teardown loop.
         // TODO: remove this altogether and make it the responsibility of the NetworkProviders to
         // avoid connect/teardown loops.
        if (nai.everConnected()
                && !nai.isVPN()
                && !nai.networkCapabilities.satisfiedByImmutableNetworkCapabilities(nc)) {
            // TODO: consider not complaining when a network agent degrades its capabilities if this
            // does not cause any request (that is not a listen) currently matching that agent to
            // stop being matched by the updated agent.
            String diff = nai.networkCapabilities.describeImmutableDifferences(nc);
            if (!TextUtils.isEmpty(diff)) {
                Log.wtf(TAG, "BUG: " + nai + " lost immutable capabilities:" + diff);
            }
        }

        // Don't modify caller's NetworkCapabilities.
        final NetworkCapabilities newNc = new NetworkCapabilities(nc);
        if (nai.isValidated()) {
            newNc.addCapability(NET_CAPABILITY_VALIDATED);
        } else {
            newNc.removeCapability(NET_CAPABILITY_VALIDATED);
        }
        if (nai.captivePortalDetected()) {
            newNc.addCapability(NET_CAPABILITY_CAPTIVE_PORTAL);
        } else {
            newNc.removeCapability(NET_CAPABILITY_CAPTIVE_PORTAL);
        }
        if (nai.isBackgroundNetwork()) {
            newNc.removeCapability(NET_CAPABILITY_FOREGROUND);
        } else {
            newNc.addCapability(NET_CAPABILITY_FOREGROUND);
        }
        if (nai.partialConnectivity()) {
            newNc.addCapability(NET_CAPABILITY_PARTIAL_CONNECTIVITY);
        } else {
            newNc.removeCapability(NET_CAPABILITY_PARTIAL_CONNECTIVITY);
        }
        newNc.setPrivateDnsBroken(nai.networkCapabilities.isPrivateDnsBroken());

        // TODO : remove this once all factories are updated to send NOT_SUSPENDED and NOT_ROAMING
        if (!newNc.hasTransport(TRANSPORT_CELLULAR)) {
            newNc.addCapability(NET_CAPABILITY_NOT_SUSPENDED);
            newNc.addCapability(NET_CAPABILITY_NOT_ROAMING);
        }

        if (nai.propagateUnderlyingCapabilities()) {
            applyUnderlyingCapabilities(nai.declaredUnderlyingNetworks,
                    nai.getDeclaredCapabilitiesSanitized(mCarrierPrivilegeAuthenticator),
                    newNc);
        }

        return newNc;
    }

    private void updateNetworkInfoForRoamingAndSuspended(NetworkAgentInfo nai,
            NetworkCapabilities prevNc, NetworkCapabilities newNc) {
        final boolean prevSuspended = !prevNc.hasCapability(NET_CAPABILITY_NOT_SUSPENDED);
        final boolean suspended = !newNc.hasCapability(NET_CAPABILITY_NOT_SUSPENDED);
        final boolean prevRoaming = !prevNc.hasCapability(NET_CAPABILITY_NOT_ROAMING);
        final boolean roaming = !newNc.hasCapability(NET_CAPABILITY_NOT_ROAMING);
        if (prevSuspended != suspended) {
            // TODO (b/73132094) : remove this call once the few users of onSuspended and
            // onResumed have been removed.
            notifyNetworkCallbacks(nai, suspended ? ConnectivityManager.CALLBACK_SUSPENDED
                    : ConnectivityManager.CALLBACK_RESUMED);
        }
        if (prevSuspended != suspended || prevRoaming != roaming) {
            // updateNetworkInfo will mix in the suspended info from the capabilities and
            // take appropriate action for the network having possibly changed state.
            updateNetworkInfo(nai, nai.networkInfo);
        }
    }

    private void handleUidCarrierPrivilegesLost(int uid, int subId) {
        ensureRunningOnConnectivityServiceThread();
        // A NetworkRequest needs to be revoked when all the conditions are met
        //   1. It requests restricted network
        //   2. The requestor uid matches the uid with the callback
        //   3. The app doesn't have Carrier Privileges
        //   4. The app doesn't have permission.CONNECTIVITY_USE_RESTRICTED_NETWORKS
        for (final NetworkRequest nr : mNetworkRequests.keySet()) {
            if ((nr.isRequest() || nr.isListen())
                    && !nr.hasCapability(NET_CAPABILITY_NOT_RESTRICTED)
                    && nr.getRequestorUid() == uid
                    && getSubscriptionIdFromNetworkCaps(nr.networkCapabilities) == subId
                    && !hasConnectivityRestrictedNetworksPermission(uid, true)) {
                declareNetworkRequestUnfulfillable(nr);
            }
        }

        // A NetworkAgent's allowedUids may need to be updated if the app has lost
        // carrier config
        for (final NetworkAgentInfo nai : mNetworkAgentInfos) {
            if (nai.networkCapabilities.getAllowedUidsNoCopy().contains(uid)
                    && getSubscriptionIdFromNetworkCaps(nai.networkCapabilities) == subId) {
                final NetworkCapabilities nc = new NetworkCapabilities(nai.networkCapabilities);
                NetworkAgentInfo.restrictCapabilitiesFromNetworkAgent(
                        nc,
                        uid,
                        false /* hasAutomotiveFeature (irrelevant) */,
                        mDeps,
                        mCarrierPrivilegeAuthenticator);
                updateCapabilities(nai.getScore(), nai, nc);
            }
        }
    }

    /**
     * Update the NetworkCapabilities for {@code nai} to {@code nc}. Specifically:
     *
     * 1. Calls mixInCapabilities to merge the passed-in NetworkCapabilities {@code nc} with the
     *    capabilities we manage and store in {@code nai}, such as validated status and captive
     *    portal status)
     * 2. Takes action on the result: changes network permissions, sends CAP_CHANGED callbacks, and
     *    potentially triggers rematches.
     * 3. Directly informs other network stack components (NetworkStatsService, VPNs, etc. of the
     *    change.)
     *
     * @param oldScore score of the network before any of the changes that prompted us
     *                 to call this function.
     * @param nai the network having its capabilities updated.
     * @param nc the new network capabilities.
     */
    private void updateCapabilities(final FullScore oldScore, @NonNull final NetworkAgentInfo nai,
            @NonNull final NetworkCapabilities nc) {
        NetworkCapabilities newNc = mixInCapabilities(nai, nc);
        if (Objects.equals(nai.networkCapabilities, newNc)) return;
        final String differences = newNc.describeCapsDifferencesFrom(nai.networkCapabilities);
        if (null != differences) {
            Log.i(TAG, "Update capabilities for net " + nai.network + " : " + differences);
        }
        updateNetworkPermissions(nai, newNc);
        final NetworkCapabilities prevNc = nai.getAndSetNetworkCapabilities(newNc);

        if (prevNc != null && (!prevNc.equalsUids(newNc) || !prevNc.equalsTransportTypes(newNc))) {
            updateDisallowedUidsForNetwork(nai);
        }
        updateVpnUids(nai, prevNc, newNc);
        updateAllowedUids(nai, prevNc, newNc);
        nai.updateScoreForNetworkAgentUpdate();

        if (nai.getScore().equals(oldScore) && newNc.equalRequestableCapabilities(prevNc)) {
            // If the requestable capabilities haven't changed, and the score hasn't changed, then
            // the change we're processing can't affect any requests, it can only affect the listens
            // on this network. We might have been called by rematchNetworkAndRequests when a
            // network changed foreground state.
            processListenRequests(nai);
        } else {
            // If the requestable capabilities have changed or the score changed, we can't have been
            // called by rematchNetworkAndRequests, so it's safe to start a rematch.
            rematchAllNetworksAndRequests();
            notifyNetworkCallbacks(nai, ConnectivityManager.CALLBACK_CAP_CHANGED);
        }
        updateNetworkInfoForRoamingAndSuspended(nai, prevNc, newNc);

        final boolean oldMetered = prevNc.isMetered();
        final boolean newMetered = newNc.isMetered();
        final boolean meteredChanged = oldMetered != newMetered;

        if (meteredChanged) {
            maybeNotifyNetworkBlocked(nai, oldMetered, newMetered,
                    mVpnBlockedUidRanges, mVpnBlockedUidRanges);
        }

        final boolean roamingChanged = prevNc.hasCapability(NET_CAPABILITY_NOT_ROAMING)
                != newNc.hasCapability(NET_CAPABILITY_NOT_ROAMING);

        // Report changes that are interesting for network statistics tracking.
        if (meteredChanged || roamingChanged) {
            notifyIfacesChangedForNetworkStats();
        }

        // This network might have been underlying another network. Propagate its capabilities.
        propagateUnderlyingNetworkCapabilities(nai.network);

        if (meteredChanged || !newNc.equalsTransportTypes(prevNc)) {
            mDnsManager.updateCapabilitiesForNetwork(nai.network.getNetId(), newNc);
        }

        maybeSendProxyBroadcast(nai, prevNc, newNc);
    }

    /** Convenience method to update the capabilities for a given network. */
    private void updateCapabilitiesForNetwork(NetworkAgentInfo nai) {
        updateCapabilities(nai.getScore(), nai, nai.networkCapabilities);
    }

    private void maybeApplyMulticastRoutingConfig(@NonNull final NetworkAgentInfo nai,
            final LocalNetworkConfig oldConfig,
            final LocalNetworkConfig newConfig) {
        final MulticastRoutingConfig oldUpstreamConfig =
                oldConfig == null ? MulticastRoutingConfig.CONFIG_FORWARD_NONE :
                        oldConfig.getUpstreamMulticastRoutingConfig();
        final MulticastRoutingConfig oldDownstreamConfig =
                oldConfig == null ? MulticastRoutingConfig.CONFIG_FORWARD_NONE :
                        oldConfig.getDownstreamMulticastRoutingConfig();
        final MulticastRoutingConfig newUpstreamConfig =
                newConfig == null ? MulticastRoutingConfig.CONFIG_FORWARD_NONE :
                        newConfig.getUpstreamMulticastRoutingConfig();
        final MulticastRoutingConfig newDownstreamConfig =
                newConfig == null ? MulticastRoutingConfig.CONFIG_FORWARD_NONE :
                        newConfig.getDownstreamMulticastRoutingConfig();

        if (oldUpstreamConfig.equals(newUpstreamConfig) &&
            oldDownstreamConfig.equals(newDownstreamConfig)) {
            return;
        }

        final String downstreamNetworkName = nai.linkProperties.getInterfaceName();
        final LocalNetworkInfo lni = localNetworkInfoForNai(nai);
        final Network upstreamNetwork = lni.getUpstreamNetwork();

        if (upstreamNetwork != null) {
            final String upstreamNetworkName =
                    getLinkProperties(upstreamNetwork).getInterfaceName();
            applyMulticastRoutingConfig(downstreamNetworkName, upstreamNetworkName, newConfig);
        }
    }

    private void applyMulticastRoutingConfig(@NonNull String localNetworkInterfaceName,
            @NonNull String upstreamNetworkInterfaceName,
            @NonNull final LocalNetworkConfig config) {
        if (mMulticastRoutingCoordinatorService == null) {
            if (config.getDownstreamMulticastRoutingConfig().getForwardingMode() != FORWARD_NONE ||
                config.getUpstreamMulticastRoutingConfig().getForwardingMode() != FORWARD_NONE) {
                loge("Multicast routing is not supported, failed to configure " + config
                        + " for " + localNetworkInterfaceName + " to "
                        +  upstreamNetworkInterfaceName);
            }
            return;
        }

        mMulticastRoutingCoordinatorService.applyMulticastRoutingConfig(localNetworkInterfaceName,
                upstreamNetworkInterfaceName, config.getUpstreamMulticastRoutingConfig());
        mMulticastRoutingCoordinatorService.applyMulticastRoutingConfig
                (upstreamNetworkInterfaceName, localNetworkInterfaceName,
                        config.getDownstreamMulticastRoutingConfig());
    }

    private void disableMulticastRouting(@NonNull String localNetworkInterfaceName,
            @NonNull String upstreamNetworkInterfaceName) {
        if (mMulticastRoutingCoordinatorService == null) {
            return;
        }

        mMulticastRoutingCoordinatorService.applyMulticastRoutingConfig(localNetworkInterfaceName,
                upstreamNetworkInterfaceName, MulticastRoutingConfig.CONFIG_FORWARD_NONE);
        mMulticastRoutingCoordinatorService.applyMulticastRoutingConfig
                (upstreamNetworkInterfaceName, localNetworkInterfaceName,
                        MulticastRoutingConfig.CONFIG_FORWARD_NONE);
    }

    // oldConfig is null iff this is the original registration of the local network config
    private void handleUpdateLocalNetworkConfig(@NonNull final NetworkAgentInfo nai,
            @Nullable final LocalNetworkConfig oldConfig,
            @NonNull final LocalNetworkConfig newConfig) {
        if (!nai.isLocalNetwork()) {
            Log.wtf(TAG, "Ignoring update of a local network info on non-local network " + nai);
            return;
        }

        if (VDBG) {
            Log.v(TAG, "Update local network config " + nai.network.netId + " : " + newConfig);
        }
        final LocalNetworkConfig.Builder configBuilder = new LocalNetworkConfig.Builder();
        configBuilder.setUpstreamMulticastRoutingConfig(
                newConfig.getUpstreamMulticastRoutingConfig());
        configBuilder.setDownstreamMulticastRoutingConfig(
                newConfig.getDownstreamMulticastRoutingConfig());

        final NetworkRequest oldRequest =
                (null == oldConfig) ? null : oldConfig.getUpstreamSelector();
        final NetworkCapabilities oldCaps =
                (null == oldRequest) ? null : oldRequest.networkCapabilities;
        final NetworkRequestInfo oldNri =
                null == oldRequest ? null : mNetworkRequests.get(oldRequest);
        final NetworkAgentInfo oldSatisfier =
                null == oldNri ? null : oldNri.getSatisfier();
        final NetworkRequest newRequest = newConfig.getUpstreamSelector();
        final NetworkCapabilities newCaps =
                (null == newRequest) ? null : newRequest.networkCapabilities;
        final boolean requestUpdated = !Objects.equals(newCaps, oldCaps);
        if (null != oldRequest && requestUpdated) {
            handleRemoveNetworkRequest(mNetworkRequests.get(oldRequest));
            if (null == newRequest && null != oldSatisfier) {
                // If there is an old satisfier, but no new request, then remove the old upstream.
                removeLocalNetworkUpstream(nai, oldSatisfier);
                nai.localNetworkConfig = configBuilder.build();
                // When there is a new request, the rematch sees the new request and sends the
                // LOCAL_NETWORK_INFO_CHANGED callbacks accordingly.
                // But here there is no new request, so the rematch won't see anything. Send
                // callbacks to apps now to tell them about the loss of upstream.
                notifyNetworkCallbacks(nai,
                        ConnectivityManager.CALLBACK_LOCAL_NETWORK_INFO_CHANGED);
                return;
            }
        }
        if (null != newRequest && requestUpdated) {
            // File the new request if :
            //  - it has changed (requestUpdated), or
            //  - it's the first time this local info (null == oldConfig)
            // is updated and the request has not been filed yet.
            // Requests for local info are always LISTEN_FOR_BEST, because they have at most one
            // upstream (the best) but never request it to be brought up.
            final NetworkRequest nr = new NetworkRequest(newCaps, ConnectivityManager.TYPE_NONE,
                    nextNetworkRequestId(), LISTEN_FOR_BEST);
            configBuilder.setUpstreamSelector(nr);
            final NetworkRequestInfo nri = new NetworkRequestInfo(
                    nai.creatorUid, nr, null /* messenger */, null /* binder */,
                    0 /* callbackFlags */, null /* attributionTag */);
            if (null != oldSatisfier) {
                // Set the old satisfier in the new NRI so that the rematch will see any changes
                nri.setSatisfier(oldSatisfier, nr);
            }
            nai.localNetworkConfig = configBuilder.build();
            // handleRegisterNetworkRequest causes a rematch. The rematch must happen after
            // nai.localNetworkConfig is set, since it will base its callbacks on the old
            // satisfier and the new request.
            handleRegisterNetworkRequest(nri);
        } else {
            configBuilder.setUpstreamSelector(oldRequest);
            nai.localNetworkConfig = configBuilder.build();
        }
        maybeApplyMulticastRoutingConfig(nai, oldConfig, newConfig);
    }

    /**
     * Returns the interface which requires VPN isolation (ingress interface filtering).
     *
     * Ingress interface filtering enforces that all apps under the given network can only receive
     * packets from the network's interface (and loopback). This is important for VPNs because
     * apps that cannot bypass a fully-routed VPN shouldn't be able to receive packets from any
     * non-VPN interfaces.
     *
     * As a result, this method should return Non-null interface iff
     *  1. the network is an app VPN (not legacy VPN)
     *  2. the VPN does not allow bypass
     *  3. the VPN is fully-routed
     *  4. the VPN interface is non-null
     *
     * @see INetd#firewallAddUidInterfaceRules
     * @see INetd#firewallRemoveUidInterfaceRules
     */
    @Nullable
    private String getVpnIsolationInterface(@NonNull NetworkAgentInfo nai, NetworkCapabilities nc,
            LinkProperties lp) {
        if (nc == null || lp == null) return null;
        if (nai.isVPN()
                && !nai.networkAgentConfig.allowBypass
                && nc.getOwnerUid() != Process.SYSTEM_UID
                && lp.getInterfaceName() != null
                && (lp.hasIpv4DefaultRoute() || lp.hasIpv4UnreachableDefaultRoute())
                && (lp.hasIpv6DefaultRoute() || lp.hasIpv6UnreachableDefaultRoute())
                && !lp.hasExcludeRoute()) {
            return lp.getInterfaceName();
        }
        return null;
    }

    /**
     * Returns whether we need to set interface filtering rule or not
     */
    private boolean requiresVpnAllowRule(NetworkAgentInfo nai, LinkProperties lp,
            String isolationIface) {
        // Allow rules are always needed if VPN isolation is enabled.
        if (isolationIface != null) return true;

        // On T and above, allow rules are needed for all VPNs. Allow rule with null iface is a
        // wildcard to allow apps to receive packets on all interfaces. This is required to accept
        // incoming traffic in Lockdown mode by overriding the Lockdown blocking rule.
        return mDeps.isAtLeastT() && nai.isVPN() && lp != null && lp.getInterfaceName() != null;
    }

    private static UidRangeParcel[] toUidRangeStableParcels(final @NonNull Set<UidRange> ranges) {
        final UidRangeParcel[] stableRanges = new UidRangeParcel[ranges.size()];
        int index = 0;
        for (UidRange range : ranges) {
            stableRanges[index] = new UidRangeParcel(range.start, range.stop);
            index++;
        }
        return stableRanges;
    }

    private static UidRangeParcel[] intsToUidRangeStableParcels(
            final @NonNull ArraySet<Integer> uids) {
        final UidRangeParcel[] stableRanges = new UidRangeParcel[uids.size()];
        int index = 0;
        for (int uid : uids) {
            stableRanges[index] = new UidRangeParcel(uid, uid);
            index++;
        }
        return stableRanges;
    }

    private static UidRangeParcel[] toUidRangeStableParcels(UidRange[] ranges) {
        final UidRangeParcel[] stableRanges = new UidRangeParcel[ranges.length];
        for (int i = 0; i < ranges.length; i++) {
            stableRanges[i] = new UidRangeParcel(ranges[i].start, ranges[i].stop);
        }
        return stableRanges;
    }

    private void maybeCloseSockets(NetworkAgentInfo nai, Set<UidRange> ranges,
            UidRangeParcel[] uidRangeParcels, int[] exemptUids) {
        if (nai.isVPN() && !nai.networkAgentConfig.allowBypass) {
            try {
                if (mDeps.isAtLeastU()) {
                    final Set<Integer> exemptUidSet = new ArraySet<>();
                    for (final int uid: exemptUids) {
                        exemptUidSet.add(uid);
                    }
                    mDeps.destroyLiveTcpSockets(UidRange.toIntRanges(ranges), exemptUidSet);
                } else {
                    mNetd.socketDestroy(uidRangeParcels, exemptUids);
                }
            } catch (Exception e) {
                loge("Exception in socket destroy: ", e);
            }
        }
    }

    private void updateVpnUidRanges(boolean add, NetworkAgentInfo nai, Set<UidRange> uidRanges) {
        int[] exemptUids = new int[2];
        // TODO: Excluding VPN_UID is necessary in order to not to kill the TCP connection used
        // by PPTP. Fix this by making Vpn set the owner UID to VPN_UID instead of system when
        // starting a legacy VPN, and remove VPN_UID here. (b/176542831)
        exemptUids[0] = VPN_UID;
        exemptUids[1] = nai.networkCapabilities.getOwnerUid();
        UidRangeParcel[] ranges = toUidRangeStableParcels(uidRanges);

        // Close sockets before modifying uid ranges so that RST packets can reach to the server.
        maybeCloseSockets(nai, uidRanges, ranges, exemptUids);
        try {
            if (add) {
                mNetd.networkAddUidRangesParcel(new NativeUidRangeConfig(
                        nai.network.netId, ranges, PREFERENCE_ORDER_VPN));
            } else {
                mNetd.networkRemoveUidRangesParcel(new NativeUidRangeConfig(
                        nai.network.netId, ranges, PREFERENCE_ORDER_VPN));
            }
        } catch (Exception e) {
            loge("Exception while " + (add ? "adding" : "removing") + " uid ranges " + uidRanges +
                    " on netId " + nai.network.netId + ". " + e);
        }
        // Close sockets that established connection while requesting netd.
        maybeCloseSockets(nai, uidRanges, ranges, exemptUids);
    }

    private boolean isProxySetOnAnyDefaultNetwork() {
        ensureRunningOnConnectivityServiceThread();
        for (final NetworkRequestInfo nri : mDefaultNetworkRequests) {
            final NetworkAgentInfo nai = nri.getSatisfier();
            if (nai != null && nai.linkProperties.getHttpProxy() != null) {
                return true;
            }
        }
        return false;
    }

    private void maybeSendProxyBroadcast(NetworkAgentInfo nai, NetworkCapabilities prevNc,
            NetworkCapabilities newNc) {
        // When the apps moved from/to a VPN, a proxy broadcast is needed to inform the apps that
        // the proxy might be changed since the default network satisfied by the apps might also
        // changed.
        // TODO: Try to track the default network that apps use and only send a proxy broadcast when
        //  that happens to prevent false alarms.
        final Set<UidRange> prevUids = prevNc == null ? null : prevNc.getUidRanges();
        final Set<UidRange> newUids = newNc == null ? null : newNc.getUidRanges();
        if (nai.isVPN() && nai.everConnected() && !UidRange.hasSameUids(prevUids, newUids)
                && (nai.linkProperties.getHttpProxy() != null || isProxySetOnAnyDefaultNetwork())) {
            mProxyTracker.sendProxyBroadcast();
        }
    }

    private void updateVpnUids(@NonNull NetworkAgentInfo nai, @Nullable NetworkCapabilities prevNc,
            @Nullable NetworkCapabilities newNc) {
        Set<UidRange> prevRanges = null == prevNc ? null : prevNc.getUidRanges();
        Set<UidRange> newRanges = null == newNc ? null : newNc.getUidRanges();
        if (null == prevRanges) prevRanges = new ArraySet<>();
        if (null == newRanges) newRanges = new ArraySet<>();
        final Set<UidRange> prevRangesCopy = new ArraySet<>(prevRanges);

        prevRanges.removeAll(newRanges);
        newRanges.removeAll(prevRangesCopy);

        try {
            // When updating the VPN uid routing rules, add the new range first then remove the old
            // range. If old range were removed first, there would be a window between the old
            // range being removed and the new range being added, during which UIDs contained
            // in both ranges are not subject to any VPN routing rules. Adding new range before
            // removing old range works because, unlike the filtering rules below, it's possible to
            // add duplicate UID routing rules.
            // TODO: calculate the intersection of add & remove. Imagining that we are trying to
            // remove uid 3 from a set containing 1-5. Intersection of the prev and new sets is:
            //   [1-5] & [1-2],[4-5] == [3]
            // Then we can do:
            //   maybeCloseSockets([3])
            //   mNetd.networkAddUidRanges([1-2],[4-5])
            //   mNetd.networkRemoveUidRanges([1-5])
            //   maybeCloseSockets([3])
            // This can prevent the sockets of uid 1-2, 4-5 from being closed. It also reduce the
            // number of binder calls from 6 to 4.
            if (!newRanges.isEmpty()) {
                updateVpnUidRanges(true, nai, newRanges);
            }
            if (!prevRanges.isEmpty()) {
                updateVpnUidRanges(false, nai, prevRanges);
            }
            final String oldIface = getVpnIsolationInterface(nai, prevNc, nai.linkProperties);
            final String newIface = getVpnIsolationInterface(nai, newNc, nai.linkProperties);
            final boolean wasFiltering = requiresVpnAllowRule(nai, nai.linkProperties, oldIface);
            final boolean shouldFilter = requiresVpnAllowRule(nai, nai.linkProperties, newIface);
            // For VPN uid interface filtering, old ranges need to be removed before new ranges can
            // be added, due to the range being expanded and stored as individual UIDs. For example
            // the UIDs might be updated from [0, 99999] to ([0, 10012], [10014, 99999]) which means
            // prevRanges = [0, 99999] while newRanges = [0, 10012], [10014, 99999]. If prevRanges
            // were added first and then newRanges got removed later, there would be only one uid
            // 10013 left. A consequence of removing old ranges before adding new ranges is that
            // there is now a window of opportunity when the UIDs are not subject to any filtering.
            // Note that this is in contrast with the (more robust) update of VPN routing rules
            // above, where the addition of new ranges happens before the removal of old ranges.
            // TODO Fix this window by computing an accurate diff on Set<UidRange>, so the old range
            // to be removed will never overlap with the new range to be added.
            if (wasFiltering && !prevRanges.isEmpty()) {
                mPermissionMonitor.onVpnUidRangesRemoved(oldIface, prevRanges,
                        prevNc.getOwnerUid());
                mPermissionMonitor.updateVpnLockdownUidInterfaceRules(
                        nai.linkProperties.getInterfaceName(), prevRanges, prevNc.getOwnerUid(),
                        false /* add */);
            }
            if (shouldFilter && !newRanges.isEmpty()) {
                mPermissionMonitor.onVpnUidRangesAdded(newIface, newRanges, newNc.getOwnerUid());
                mPermissionMonitor.updateVpnLockdownUidInterfaceRules(
                        nai.linkProperties.getInterfaceName(), newRanges, newNc.getOwnerUid(),
                        true /* add */);
            }
        } catch (Exception e) {
            // Never crash!
            loge("Exception in updateVpnUids: ", e);
        }
    }

    private void updateAllowedUids(@NonNull NetworkAgentInfo nai,
            @Nullable NetworkCapabilities prevNc, @Nullable NetworkCapabilities newNc) {
        if (!nai.isVPN()) {
            updateAllowedUidsForNetwork(nai);
        }
        // In almost all cases both NC code for empty access UIDs. return as fast as possible.
        final boolean prevEmpty = null == prevNc || prevNc.getAllowedUidsNoCopy().isEmpty();
        final boolean newEmpty = null == newNc || newNc.getAllowedUidsNoCopy().isEmpty();
        if (prevEmpty && newEmpty) return;

        final ArraySet<Integer> prevUids =
                null == prevNc ? new ArraySet<>() : prevNc.getAllowedUidsNoCopy();
        final ArraySet<Integer> newUids =
                null == newNc ? new ArraySet<>() : newNc.getAllowedUidsNoCopy();

        if (prevUids.equals(newUids)) return;

        // This implementation is very simple and vastly faster for sets of Integers than
        // CompareOrUpdateResult, which is tuned for sets that need to be compared based on
        // a key computed from the value and has storage for that.
        final ArraySet<Integer> toRemove = new ArraySet<>(prevUids);
        final ArraySet<Integer> toAdd = new ArraySet<>(newUids);
        toRemove.removeAll(newUids);
        toAdd.removeAll(prevUids);
        try {
            if (!toAdd.isEmpty()) {
                mNetd.networkAddUidRangesParcel(new NativeUidRangeConfig(
                        nai.network.netId,
                        intsToUidRangeStableParcels(toAdd),
                        PREFERENCE_ORDER_IRRELEVANT_BECAUSE_NOT_DEFAULT));
            }
            if (!toRemove.isEmpty()) {
                mNetd.networkRemoveUidRangesParcel(new NativeUidRangeConfig(
                        nai.network.netId,
                        intsToUidRangeStableParcels(toRemove),
                        PREFERENCE_ORDER_IRRELEVANT_BECAUSE_NOT_DEFAULT));
            }
        } catch (ServiceSpecificException e) {
            // Has the interface disappeared since the network was built ?
            Log.i(TAG, "Can't set access UIDs for network " + nai.network, e);
        } catch (RemoteException e) {
            // Netd died. This usually causes a runtime restart anyway.
        }
    }

    public void handleUpdateLinkProperties(@NonNull NetworkAgentInfo nai,
            @NonNull LinkProperties newLp) {
        ensureRunningOnConnectivityServiceThread();

        if (!mNetworkAgentInfos.contains(nai)) {
            // Ignore updates for disconnected networks
            return;
        }
        if (VDBG || DDBG) {
            log("Update of LinkProperties for " + nai.toShortString()
                    + "; created=" + nai.getCreatedTime()
                    + "; firstConnected=" + nai.getConnectedTime());
        }
        // TODO: eliminate this defensive copy after confirming that updateLinkProperties does not
        // modify its oldLp parameter.
        updateLinkProperties(nai, newLp, new LinkProperties(nai.linkProperties));
    }

    private void sendPendingIntentForRequest(NetworkRequestInfo nri, NetworkAgentInfo networkAgent,
            int notificationType) {
        if (notificationType == ConnectivityManager.CALLBACK_AVAILABLE && !nri.mPendingIntentSent) {
            Intent intent = new Intent();
            intent.putExtra(ConnectivityManager.EXTRA_NETWORK, networkAgent.network);
            // If apps could file multi-layer requests with PendingIntents, they'd need to know
            // which of the layer is satisfied alongside with some ID for the request. Hence, if
            // such an API is ever implemented, there is no doubt the right request to send in
            // EXTRA_NETWORK_REQUEST is the active request, and whatever ID would be added would
            // need to be sent as a separate extra.
            final NetworkRequest req = nri.isMultilayerRequest()
                    ? nri.getActiveRequest()
                    // Non-multilayer listen requests do not have an active request
                    : nri.mRequests.get(0);
            if (req == null) {
                Log.wtf(TAG, "No request in NRI " + nri);
            }
            intent.putExtra(ConnectivityManager.EXTRA_NETWORK_REQUEST, req);
            nri.mPendingIntentSent = true;
            sendIntent(nri.mPendingIntent, intent);
        }
        // else not handled
    }

    // TODO(b/193460475): Remove when tooling supports SystemApi to public API.
    @SuppressLint("NewApi")
    private void sendIntent(PendingIntent pendingIntent, Intent intent) {
        mPendingIntentWakeLock.acquire();
        try {
            if (DBG) log("Sending " + pendingIntent);
            final BroadcastOptions options = BroadcastOptions.makeBasic();
            if (mDeps.isAtLeastT()) {
                // Explicitly disallow the receiver from starting activities, to prevent apps from
                // utilizing the PendingIntent as a backdoor to do this.
                options.setPendingIntentBackgroundActivityLaunchAllowed(false);
            }
            pendingIntent.send(mContext, 0, intent, this /* onFinished */, null /* Handler */,
                    null /* requiredPermission */,
                    mDeps.isAtLeastT() ? options.toBundle() : null);
        } catch (PendingIntent.CanceledException e) {
            if (DBG) log(pendingIntent + " was not sent, it had been canceled.");
            mPendingIntentWakeLock.release();
            releasePendingNetworkRequest(pendingIntent);
        }
        // ...otherwise, mPendingIntentWakeLock.release() gets called by onSendFinished()
    }

    @Override
    public void onSendFinished(PendingIntent pendingIntent, Intent intent, int resultCode,
            String resultData, Bundle resultExtras) {
        if (DBG) log("Finished sending " + pendingIntent);
        mPendingIntentWakeLock.release();
        // Release with a delay so the receiving client has an opportunity to put in its
        // own request.
        releasePendingNetworkRequestWithDelay(pendingIntent);
    }

    @Nullable
    private LocalNetworkInfo localNetworkInfoForNai(@NonNull final NetworkAgentInfo nai) {
        if (!nai.isLocalNetwork()) return null;
        final Network upstream;
        final NetworkRequest selector = nai.localNetworkConfig.getUpstreamSelector();
        if (null == selector) {
            upstream = null;
        } else {
            final NetworkRequestInfo upstreamNri = mNetworkRequests.get(selector);
            final NetworkAgentInfo satisfier = upstreamNri.getSatisfier();
            upstream = (null == satisfier) ? null : satisfier.network;
        }
        return new LocalNetworkInfo.Builder().setUpstreamNetwork(upstream).build();
    }

    // networkAgent is only allowed to be null if notificationType is
    // CALLBACK_UNAVAIL. This is because UNAVAIL is about no network being
    // available, while all other cases are about some particular network.
    private void callCallbackForRequest(@NonNull final NetworkRequestInfo nri,
            @Nullable final NetworkAgentInfo networkAgent, final int notificationType,
            final int arg1) {
        if (nri.mMessenger == null) {
            // Default request has no msgr. Also prevents callbacks from being invoked for
            // NetworkRequestInfos registered with ConnectivityDiagnostics requests. Those callbacks
            // are Type.LISTEN, but should not have NetworkCallbacks invoked.
            return;
        }
        final Bundle bundle = new Bundle();
        // TODO b/177608132: make sure callbacks are indexed by NRIs and not NetworkRequest objects.
        // TODO: check if defensive copies of data is needed.
        final NetworkRequest nrForCallback = nri.getNetworkRequestForCallback();
        putParcelable(bundle, nrForCallback);
        Message msg = Message.obtain();
        if (notificationType != ConnectivityManager.CALLBACK_UNAVAIL) {
            putParcelable(bundle, networkAgent.network);
        }
        final boolean includeLocationSensitiveInfo =
                (nri.mCallbackFlags & NetworkCallback.FLAG_INCLUDE_LOCATION_INFO) != 0;
        switch (notificationType) {
            case ConnectivityManager.CALLBACK_AVAILABLE: {
                final NetworkCapabilities nc =
                        createWithLocationInfoSanitizedIfNecessaryWhenParceled(
                                networkCapabilitiesRestrictedForCallerPermissions(
                                        networkAgent.networkCapabilities, nri.mPid, nri.mUid),
                                includeLocationSensitiveInfo, nri.mPid, nri.mUid,
                                nrForCallback.getRequestorPackageName(),
                                nri.mCallingAttributionTag);
                putParcelable(bundle, nc);
                putParcelable(bundle, linkPropertiesRestrictedForCallerPermissions(
                        networkAgent.linkProperties, nri.mPid, nri.mUid));
                // The local network info is often null, so can't use the static putParcelable
                // method here.
                bundle.putParcelable(LocalNetworkInfo.class.getSimpleName(),
                        localNetworkInfoForNai(networkAgent));
                // For this notification, arg1 contains the blocked status.
                msg.arg1 = arg1;
                break;
            }
            case ConnectivityManager.CALLBACK_LOSING: {
                msg.arg1 = arg1;
                break;
            }
            case ConnectivityManager.CALLBACK_CAP_CHANGED: {
                // networkAgent can't be null as it has been accessed a few lines above.
                final NetworkCapabilities netCap =
                        networkCapabilitiesRestrictedForCallerPermissions(
                                networkAgent.networkCapabilities, nri.mPid, nri.mUid);
                putParcelable(
                        bundle,
                        createWithLocationInfoSanitizedIfNecessaryWhenParceled(
                                netCap, includeLocationSensitiveInfo, nri.mPid, nri.mUid,
                                nrForCallback.getRequestorPackageName(),
                                nri.mCallingAttributionTag));
                break;
            }
            case ConnectivityManager.CALLBACK_IP_CHANGED: {
                putParcelable(bundle, linkPropertiesRestrictedForCallerPermissions(
                        networkAgent.linkProperties, nri.mPid, nri.mUid));
                break;
            }
            case ConnectivityManager.CALLBACK_BLK_CHANGED: {
                maybeLogBlockedStatusChanged(nri, networkAgent.network, arg1);
                msg.arg1 = arg1;
                break;
            }
            case ConnectivityManager.CALLBACK_LOCAL_NETWORK_INFO_CHANGED: {
                if (!networkAgent.isLocalNetwork()) {
                    Log.wtf(TAG, "Callback for local info for a non-local network");
                    return;
                }
                putParcelable(bundle, localNetworkInfoForNai(networkAgent));
                break;
            }
        }
        msg.what = notificationType;
        msg.setData(bundle);
        try {
            if (VDBG) {
                String notification = ConnectivityManager.getCallbackName(notificationType);
                log("sending notification " + notification + " for " + nrForCallback);
            }
            nri.mMessenger.send(msg);
        } catch (RemoteException e) {
            // may occur naturally in the race of binder death.
            loge("RemoteException caught trying to send a callback msg for " + nrForCallback);
        }
    }

    private static <T extends Parcelable> void putParcelable(Bundle bundle, T t) {
        bundle.putParcelable(t.getClass().getSimpleName(), t);
    }

    /**
     * Returns whether reassigning a request from an NAI to another can be done gracefully.
     *
     * When a request should be assigned to a new network, it is normally lingered to give
     * time for apps to gracefully migrate their connections. When both networks are on the same
     * radio, but that radio can't do time-sharing efficiently, this may end up being
     * counter-productive because any traffic on the old network may drastically reduce the
     * performance of the new network.
     * The stack supports a configuration to let modem vendors state that their radio can't
     * do time-sharing efficiently. If this configuration is set, the stack assumes moving
     * from one cell network to another can't be done gracefully.
     *
     * @param oldNai the old network serving the request
     * @param newNai the new network serving the request
     * @return whether the switch can be graceful
     */
    private boolean canSupportGracefulNetworkSwitch(@NonNull final NetworkAgentInfo oldSatisfier,
            @NonNull final NetworkAgentInfo newSatisfier) {
        if (mCellularRadioTimesharingCapable) return true;
        return !oldSatisfier.networkCapabilities.hasSingleTransport(TRANSPORT_CELLULAR)
                || !newSatisfier.networkCapabilities.hasSingleTransport(TRANSPORT_CELLULAR)
                || !newSatisfier.getScore().hasPolicy(POLICY_TRANSPORT_PRIMARY);
    }

    private void teardownUnneededNetwork(NetworkAgentInfo nai) {
        if (nai.numRequestNetworkRequests() != 0) {
            for (int i = 0; i < nai.numNetworkRequests(); i++) {
                NetworkRequest nr = nai.requestAt(i);
                // Ignore listening and track default requests.
                if (!nr.isRequest()) continue;
                loge("Dead network still had at least " + nr);
                break;
            }
        }
        nai.disconnect();
    }

    private void handleLingerComplete(NetworkAgentInfo oldNetwork) {
        if (oldNetwork == null) {
            loge("Unknown NetworkAgentInfo in handleLingerComplete");
            return;
        }
        if (DBG) log("handleLingerComplete for " + oldNetwork.toShortString());

        // If we get here it means that the last linger timeout for this network expired. So there
        // must be no other active linger timers, and we must stop lingering.
        oldNetwork.clearInactivityState();

        if (unneeded(oldNetwork, UnneededFor.TEARDOWN)) {
            // Tear the network down.
            teardownUnneededNetwork(oldNetwork);
        } else {
            // Put the network in the background if it doesn't satisfy any foreground request.
            updateCapabilitiesForNetwork(oldNetwork);
        }
    }

    private void processDefaultNetworkChanges(@NonNull final NetworkReassignment changes) {
        boolean isDefaultChanged = false;
        for (final NetworkRequestInfo defaultRequestInfo : mDefaultNetworkRequests) {
            final NetworkReassignment.RequestReassignment reassignment =
                    changes.getReassignment(defaultRequestInfo);
            if (null == reassignment) {
                continue;
            }
            // reassignment only contains those instances where the satisfying network changed.
            isDefaultChanged = true;
            // Notify system services of the new default.
            makeDefault(defaultRequestInfo, reassignment.mOldNetwork, reassignment.mNewNetwork);
        }

        if (isDefaultChanged) {
            // Hold a wakelock for a short time to help apps in migrating to a new default.
            scheduleReleaseNetworkTransitionWakelock();
        }
    }

    private void resetHttpProxyForNonDefaultNetwork(NetworkAgentInfo oldDefaultNetwork) {
        if (null == oldDefaultNetwork) return;
        // The network stopped being the default. If it was using a PAC proxy, then the
        // proxy needs to be reset, otherwise HTTP requests on this network may be sent
        // to the local proxy server, which would forward them over the newly default network.
        final ProxyInfo proxyInfo = oldDefaultNetwork.linkProperties.getHttpProxy();
        if (null == proxyInfo || !proxyInfo.isPacProxy()) return;
        oldDefaultNetwork.linkProperties.setHttpProxy(new ProxyInfo(proxyInfo.getPacFileUrl()));
        notifyNetworkCallbacks(oldDefaultNetwork, CALLBACK_IP_CHANGED);
    }

    private void makeDefault(@NonNull final NetworkRequestInfo nri,
            @Nullable final NetworkAgentInfo oldDefaultNetwork,
            @Nullable final NetworkAgentInfo newDefaultNetwork) {
        if (DBG) {
            log("Switching to new default network for: " + nri + " using " + newDefaultNetwork);
        }

        // Fix up the NetworkCapabilities of any networks that have this network as underlying.
        if (newDefaultNetwork != null) {
            propagateUnderlyingNetworkCapabilities(newDefaultNetwork.network);
        }

        // Set an app level managed default and return since further processing only applies to the
        // default network.
        if (mDefaultRequest != nri) {
            makeDefaultForApps(nri, oldDefaultNetwork, newDefaultNetwork);
            return;
        }

        makeDefaultNetwork(newDefaultNetwork);

        if (oldDefaultNetwork != null) {
            mLingerMonitor.noteLingerDefaultNetwork(oldDefaultNetwork, newDefaultNetwork);
        }
        mNetworkActivityTracker.updateDefaultNetwork(newDefaultNetwork, oldDefaultNetwork);
        maybeClosePendingFrozenSockets(newDefaultNetwork, oldDefaultNetwork);
        mProxyTracker.setDefaultProxy(null != newDefaultNetwork
                ? newDefaultNetwork.linkProperties.getHttpProxy() : null);
        resetHttpProxyForNonDefaultNetwork(oldDefaultNetwork);
        updateTcpBufferSizes(null != newDefaultNetwork
                ? newDefaultNetwork.linkProperties.getTcpBufferSizes() : null);
        notifyIfacesChangedForNetworkStats();
    }

    private void makeDefaultForApps(@NonNull final NetworkRequestInfo nri,
            @Nullable final NetworkAgentInfo oldDefaultNetwork,
            @Nullable final NetworkAgentInfo newDefaultNetwork) {
        try {
            if (VDBG) {
                log("Setting default network for " + nri
                        + " using UIDs " + nri.getUids()
                        + " with old network " + (oldDefaultNetwork != null
                        ? oldDefaultNetwork.network().getNetId() : "null")
                        + " and new network " + (newDefaultNetwork != null
                        ? newDefaultNetwork.network().getNetId() : "null"));
            }
            if (nri.getUids().isEmpty()) {
                throw new IllegalStateException("makeDefaultForApps called without specifying"
                        + " any applications to set as the default." + nri);
            }
            if (null != newDefaultNetwork) {
                mNetd.networkAddUidRangesParcel(new NativeUidRangeConfig(
                        newDefaultNetwork.network.getNetId(),
                        toUidRangeStableParcels(nri.getUids()),
                        nri.getPreferenceOrderForNetd()));
            }
            if (null != oldDefaultNetwork) {
                mNetd.networkRemoveUidRangesParcel(new NativeUidRangeConfig(
                        oldDefaultNetwork.network.getNetId(),
                        toUidRangeStableParcels(nri.getUids()),
                        nri.getPreferenceOrderForNetd()));
            }
        } catch (RemoteException | ServiceSpecificException e) {
            loge("Exception setting app default network", e);
        }
        updateUidDefaultNetworkRules(newDefaultNetwork);
    }

    /**
     * Collect restricted uid ranges for the given network and UserHandle, these uids
     * are not restricted for matched enterprise networks but being restricted for non-matched
     * enterprise networks and non-enterprise networks.
     */
    @NonNull
    private ArraySet<UidRange> getRestrictedUidRangesForEnterpriseBlocking(
            @NonNull NetworkAgentInfo nai, @NonNull UserHandle user) {
        final ArraySet<UidRange> restrictedUidRanges = new ArraySet<>();
        for (final ProfileNetworkPreferenceInfo pref : mProfileNetworkPreferences) {
            if (!pref.user.equals(user) || !pref.blockingNonEnterprise) continue;

            if (nai.networkCapabilities.hasCapability(NET_CAPABILITY_ENTERPRISE)) {
                // The NC is built from a `ProfileNetworkPreference` which has only one
                // enterprise ID, so it's guaranteed to have exactly one.
                final int prefId = pref.capabilities.getEnterpriseIds()[0];
                if (nai.networkCapabilities.hasEnterpriseId(prefId)) {
                    continue;
                }
            }

            if (UidRangeUtils.doesRangeSetOverlap(restrictedUidRanges,
                    pref.capabilities.getUidRanges())) {
                throw new IllegalArgumentException(
                        "Overlapping uid range in preference: " + pref);
            }
            restrictedUidRanges.addAll(pref.capabilities.getUidRanges());
        }
        return restrictedUidRanges;
    }

    private void updateProfileAllowedNetworks() {
        // Netd command is not implemented before U.
        if (!mDeps.isAtLeastU()) return;

        ensureRunningOnConnectivityServiceThread();
        final ArrayList<NativeUidRangeConfig> configs = new ArrayList<>();
        final List<UserHandle> users = mContext.getSystemService(UserManager.class)
                        .getUserHandles(true /* excludeDying */);
        if (users.isEmpty()) {
            throw new IllegalStateException("No user is available");
        }

        for (final NetworkAgentInfo nai : mNetworkAgentInfos) {
            ArraySet<UidRange> allowedUidRanges = new ArraySet<>();
            for (final UserHandle user : users) {
                final ArraySet<UidRange> restrictedUidRanges =
                        getRestrictedUidRangesForEnterpriseBlocking(nai, user);
                allowedUidRanges.addAll(UidRangeUtils.removeRangeSetFromUidRange(
                        UidRange.createForUser(user), restrictedUidRanges));
            }

            final UidRangeParcel[] rangesParcel = toUidRangeStableParcels(allowedUidRanges);
            configs.add(new NativeUidRangeConfig(
                    nai.network.netId, rangesParcel, 0 /* subPriority */));
        }

        // The netd API replaces the previous configs with the current configs.
        // Thus, for network disconnection or preference removal, no need to
        // unset previous config. Instead, collecting all currently needed
        // configs and issue to netd.
        try {
            mNetd.setNetworkAllowlist(configs.toArray(new NativeUidRangeConfig[0]));
        } catch (ServiceSpecificException e) {
            // Has the interface disappeared since the network was built?
            Log.wtf(TAG, "Unexpected ServiceSpecificException", e);
        } catch (RemoteException e) {
            // Netd died. This will cause a runtime restart anyway.
            Log.wtf(TAG, "Unexpected RemoteException", e);
        }
    }

    private void makeDefaultNetwork(@Nullable final NetworkAgentInfo newDefaultNetwork) {
        try {
            if (null != newDefaultNetwork) {
                mNetd.networkSetDefault(newDefaultNetwork.network.getNetId());
            } else {
                mNetd.networkClearDefault();
            }
        } catch (RemoteException | ServiceSpecificException e) {
            loge("Exception setting default network :" + e);
        }
        updateUidDefaultNetworkRules(newDefaultNetwork);
    }

    private void processListenRequests(@NonNull final NetworkAgentInfo nai) {
        // For consistency with previous behaviour, send onLost callbacks before onAvailable.
        processNewlyLostListenRequests(nai);
        notifyNetworkCallbacks(nai, ConnectivityManager.CALLBACK_CAP_CHANGED);
        processNewlySatisfiedListenRequests(nai);
    }

    private void processNewlyLostListenRequests(@NonNull final NetworkAgentInfo nai) {
        for (final NetworkRequestInfo nri : mNetworkRequests.values()) {
            if (nri.isMultilayerRequest()) {
                continue;
            }
            final NetworkRequest nr = nri.mRequests.get(0);
            if (!nr.isListen()) continue;
            if (nai.isSatisfyingRequest(nr.requestId) && !nai.satisfies(nr)) {
                nai.removeRequest(nr.requestId);
                callCallbackForRequest(nri, nai, ConnectivityManager.CALLBACK_LOST, 0);
            }
        }
    }

    private void processNewlySatisfiedListenRequests(@NonNull final NetworkAgentInfo nai) {
        for (final NetworkRequestInfo nri : mNetworkRequests.values()) {
            if (nri.isMultilayerRequest()) {
                continue;
            }
            final NetworkRequest nr = nri.mRequests.get(0);
            if (!nr.isListen()) continue;
            if (nai.satisfies(nr) && !nai.isSatisfyingRequest(nr.requestId)) {
                nai.addRequest(nr);
                notifyNetworkAvailable(nai, nri);
            }
        }
    }

    // An accumulator class to gather the list of changes that result from a rematch.
    private static class NetworkReassignment {
        static class RequestReassignment {
            @NonNull public final NetworkRequestInfo mNetworkRequestInfo;
            @Nullable public final NetworkRequest mOldNetworkRequest;
            @Nullable public final NetworkRequest mNewNetworkRequest;
            @Nullable public final NetworkAgentInfo mOldNetwork;
            @Nullable public final NetworkAgentInfo mNewNetwork;
            RequestReassignment(@NonNull final NetworkRequestInfo networkRequestInfo,
                    @Nullable final NetworkRequest oldNetworkRequest,
                    @Nullable final NetworkRequest newNetworkRequest,
                    @Nullable final NetworkAgentInfo oldNetwork,
                    @Nullable final NetworkAgentInfo newNetwork) {
                mNetworkRequestInfo = networkRequestInfo;
                mOldNetworkRequest = oldNetworkRequest;
                mNewNetworkRequest = newNetworkRequest;
                mOldNetwork = oldNetwork;
                mNewNetwork = newNetwork;
            }

            public String toString() {
                final NetworkRequest requestToShow = null != mNewNetworkRequest
                        ? mNewNetworkRequest : mNetworkRequestInfo.mRequests.get(0);
                return requestToShow.requestId + " : "
                        + (null != mOldNetwork ? mOldNetwork.network.getNetId() : "null")
                        + " → " + (null != mNewNetwork ? mNewNetwork.network.getNetId() : "null");
            }
        }

        @NonNull private final ArrayList<RequestReassignment> mReassignments = new ArrayList<>();

        @NonNull Iterable<RequestReassignment> getRequestReassignments() {
            return mReassignments;
        }

        void addRequestReassignment(@NonNull final RequestReassignment reassignment) {
            if (Build.isDebuggable()) {
                // The code is never supposed to add two reassignments of the same request. Make
                // sure this stays true, but without imposing this expensive check on all
                // reassignments on all user devices.
                for (final RequestReassignment existing : mReassignments) {
                    if (existing.mNetworkRequestInfo.equals(reassignment.mNetworkRequestInfo)) {
                        throw new IllegalStateException("Trying to reassign ["
                                + reassignment + "] but already have ["
                                + existing + "]");
                    }
                }
            }
            mReassignments.add(reassignment);
        }

        // Will return null if this reassignment does not change the network assigned to
        // the passed request.
        @Nullable
        private RequestReassignment getReassignment(@NonNull final NetworkRequestInfo nri) {
            for (final RequestReassignment event : getRequestReassignments()) {
                if (nri == event.mNetworkRequestInfo) return event;
            }
            return null;
        }

        public String toString() {
            final StringJoiner sj = new StringJoiner(", " /* delimiter */,
                    "NetReassign [" /* prefix */, "]" /* suffix */);
            if (mReassignments.isEmpty()) return sj.add("no changes").toString();
            for (final RequestReassignment rr : getRequestReassignments()) {
                sj.add(rr.toString());
            }
            return sj.toString();
        }

        public String debugString() {
            final StringBuilder sb = new StringBuilder();
            sb.append("NetworkReassignment :");
            if (mReassignments.isEmpty()) return sb.append(" no changes").toString();
            for (final RequestReassignment rr : getRequestReassignments()) {
                sb.append("\n  ").append(rr);
            }
            return sb.append("\n").toString();
        }
    }

    private void updateSatisfiersForRematchRequest(@NonNull final NetworkRequestInfo nri,
            @Nullable final NetworkRequest previousRequest,
            @Nullable final NetworkRequest newRequest,
            @Nullable final NetworkAgentInfo previousSatisfier,
            @Nullable final NetworkAgentInfo newSatisfier,
            final long now) {
        if (null != newSatisfier && mNoServiceNetwork != newSatisfier) {
            if (VDBG) log("rematch for " + newSatisfier.toShortString());
            if (null != previousRequest && null != previousSatisfier) {
                if (VDBG || DDBG) {
                    log("   accepting network in place of " + previousSatisfier.toShortString()
                            + " for " + newRequest);
                }
                previousSatisfier.removeRequest(previousRequest.requestId);
                if (canSupportGracefulNetworkSwitch(previousSatisfier, newSatisfier)
                        && !previousSatisfier.isDestroyed()) {
                    // If this network switch can't be supported gracefully, the request is not
                    // lingered. This allows letting go of the network sooner to reclaim some
                    // performance on the new network, since the radio can't do both at the same
                    // time while preserving good performance.
                    //
                    // Also don't linger the request if the old network has been destroyed.
                    // A destroyed network does not provide actual network connectivity, so
                    // lingering it is not useful. In particular this ensures that a destroyed
                    // network is outscored by its replacement,
                    // then it is torn down immediately instead of being lingered, and any apps that
                    // were using it immediately get onLost and can connect using the new network.
                    previousSatisfier.lingerRequest(previousRequest.requestId, now);
                }
            } else {
                if (VDBG || DDBG) log("   accepting network in place of null for " + newRequest);
            }

            // To prevent constantly CPU wake up for nascent timer, if a network comes up
            // and immediately satisfies a request then remove the timer. This will happen for
            // all networks except in the case of an underlying network for a VCN.
            if (newSatisfier.isNascent()) {
                newSatisfier.unlingerRequest(NetworkRequest.REQUEST_ID_NONE);
                newSatisfier.unsetInactive();
            }

            // if newSatisfier is not null, then newRequest may not be null.
            newSatisfier.unlingerRequest(newRequest.requestId);
            if (!newSatisfier.addRequest(newRequest)) {
                Log.wtf(TAG, "BUG: " + newSatisfier.toShortString() + " already has "
                        + newRequest);
            }
        } else if (null != previousRequest && null != previousSatisfier) {
            if (DBG) {
                log("Network " + previousSatisfier.toShortString() + " stopped satisfying"
                        + " request " + previousRequest.requestId);
            }
            previousSatisfier.removeRequest(previousRequest.requestId);
        }
        nri.setSatisfier(newSatisfier, newRequest);
    }

    /**
     * This function is triggered when something can affect what network should satisfy what
     * request, and it computes the network reassignment from the passed collection of requests to
     * network match to the one that the system should now have. That data is encoded in an
     * object that is a list of changes, each of them having an NRI, and old satisfier, and a new
     * satisfier.
     *
     * After the reassignment is computed, it is applied to the state objects.
     *
     * @param networkRequests the nri objects to evaluate for possible network reassignment
     * @return NetworkReassignment listing of proposed network assignment changes
     */
    @NonNull
    private NetworkReassignment computeNetworkReassignment(
            @NonNull final Collection<NetworkRequestInfo> networkRequests) {
        final NetworkReassignment changes = new NetworkReassignment();

        // Gather the list of all relevant agents.
        final ArrayList<NetworkAgentInfo> nais = new ArrayList<>();
        for (final NetworkAgentInfo nai : mNetworkAgentInfos) {
            nais.add(nai);
        }

        for (final NetworkRequestInfo nri : networkRequests) {
            // Non-multilayer listen requests can be ignored.
            if (!nri.isMultilayerRequest() && nri.mRequests.get(0).isListen()) {
                continue;
            }
            NetworkAgentInfo bestNetwork = null;
            NetworkRequest bestRequest = null;
            for (final NetworkRequest req : nri.mRequests) {
                bestNetwork = mNetworkRanker.getBestNetwork(req, nais, nri.getSatisfier());
                // Stop evaluating as the highest possible priority request is satisfied.
                if (null != bestNetwork) {
                    bestRequest = req;
                    break;
                }
            }
            if (null == bestNetwork && isDefaultBlocked(nri)) {
                // Remove default networking if disallowed for managed default requests.
                bestNetwork = mNoServiceNetwork;
            }
            if (nri.getSatisfier() != bestNetwork) {
                // bestNetwork may be null if no network can satisfy this request.
                changes.addRequestReassignment(new NetworkReassignment.RequestReassignment(
                        nri, nri.mActiveRequest, bestRequest, nri.getSatisfier(), bestNetwork));
            }
        }
        return changes;
    }

    private Set<NetworkRequestInfo> getNrisFromGlobalRequests() {
        return new HashSet<>(mNetworkRequests.values());
    }

    /**
     * Attempt to rematch all Networks with all NetworkRequests.  This may result in Networks
     * being disconnected.
     */
    private void rematchAllNetworksAndRequests() {
        rematchNetworksAndRequests(getNrisFromGlobalRequests());
    }

    /**
     * Attempt to rematch all Networks with given NetworkRequests.  This may result in Networks
     * being disconnected.
     */
    private void rematchNetworksAndRequests(
            @NonNull final Set<NetworkRequestInfo> networkRequests) {
        ensureRunningOnConnectivityServiceThread();
        // TODO: This may be slow, and should be optimized.
        final long start = SystemClock.elapsedRealtime();
        final NetworkReassignment changes = computeNetworkReassignment(networkRequests);
        final long computed = SystemClock.elapsedRealtime();
        applyNetworkReassignment(changes, start);
        final long applied = SystemClock.elapsedRealtime();
        issueNetworkNeeds();
        final long end = SystemClock.elapsedRealtime();
        if (VDBG || DDBG) {
            log(String.format("Rematched networks [computed %dms] [applied %dms] [issued %d]",
                    computed - start, applied - computed, end - applied));
            log(changes.debugString());
        } else if (DBG) {
            // Shorter form, only one line of log
            log(String.format("%s [c %d] [a %d] [i %d]", changes.toString(),
                    computed - start, applied - computed, end - applied));
        }
    }

    private boolean hasSameInterfaceName(@Nullable final NetworkAgentInfo nai1,
            @Nullable final NetworkAgentInfo nai2) {
        if (null == nai1) return null == nai2;
        if (null == nai2) return false;
        return nai1.linkProperties.getInterfaceName()
                .equals(nai2.linkProperties.getInterfaceName());
    }

    private void applyNetworkReassignment(@NonNull final NetworkReassignment changes,
            final long now) {
        final Collection<NetworkAgentInfo> nais = mNetworkAgentInfos;

        // Since most of the time there are only 0 or 1 background networks, it would probably
        // be more efficient to just use an ArrayList here. TODO : measure performance
        final ArraySet<NetworkAgentInfo> oldBgNetworks = new ArraySet<>();
        for (final NetworkAgentInfo nai : nais) {
            if (nai.isBackgroundNetwork()) oldBgNetworks.add(nai);
        }

        // First, update the lists of satisfied requests in the network agents. This is necessary
        // because some code later depends on this state to be correct, most prominently computing
        // the linger status.
        for (final NetworkReassignment.RequestReassignment event :
                changes.getRequestReassignments()) {
            updateSatisfiersForRematchRequest(event.mNetworkRequestInfo,
                    event.mOldNetworkRequest, event.mNewNetworkRequest,
                    event.mOldNetwork, event.mNewNetwork,
                    now);
        }

        // Process default network changes if applicable.
        processDefaultNetworkChanges(changes);

        // Update forwarding rules for the upstreams of local networks. Do this before sending
        // onAvailable so that by the time onAvailable is sent the forwarding rules are set up.
        // Don't send CALLBACK_LOCAL_NETWORK_INFO_CHANGED yet though : they should be sent after
        // onAvailable so clients know what network the change is about. Store such changes in
        // an array that's only allocated if necessary (because it's almost never necessary).
        ArrayList<NetworkAgentInfo> localInfoChangedAgents = null;
        for (final NetworkAgentInfo nai : mNetworkAgentInfos) {
            if (!nai.isLocalNetwork()) continue;
            final NetworkRequest nr = nai.localNetworkConfig.getUpstreamSelector();
            if (null == nr) continue; // No upstream for this local network
            final NetworkRequestInfo nri = mNetworkRequests.get(nr);
            final NetworkReassignment.RequestReassignment change = changes.getReassignment(nri);
            if (null == change) continue; // No change in upstreams for this network
            final String fromIface = nai.linkProperties.getInterfaceName();
            if (!hasSameInterfaceName(change.mOldNetwork, change.mNewNetwork)
                    || change.mOldNetwork.isDestroyed()) {
                // There can be a change with the same interface name if the new network is the
                // replacement for the old network that was unregisteredAfterReplacement.
                try {
                    if (null != change.mOldNetwork) {
                        mRoutingCoordinatorService.removeInterfaceForward(fromIface,
                                change.mOldNetwork.linkProperties.getInterfaceName());
                        disableMulticastRouting(fromIface,
                                change.mOldNetwork.linkProperties.getInterfaceName());
                    }
                    // If the new upstream is already destroyed, there is no point in setting up
                    // a forward (in fact, it might forward to the interface for some new network !)
                    // Later when the upstream disconnects CS will try to remove the forward, which
                    // is ignored with a benign log by RoutingCoordinatorService.
                    if (null != change.mNewNetwork && !change.mNewNetwork.isDestroyed()) {
                        mRoutingCoordinatorService.addInterfaceForward(fromIface,
                                change.mNewNetwork.linkProperties.getInterfaceName());
                        applyMulticastRoutingConfig(fromIface,
                                change.mNewNetwork.linkProperties.getInterfaceName(),
                                nai.localNetworkConfig);
                    }
                } catch (final RemoteException e) {
                    loge("Can't update forwarding rules", e);
                }
            }
            if (null == localInfoChangedAgents) localInfoChangedAgents = new ArrayList<>();
            localInfoChangedAgents.add(nai);
        }

        // Notify requested networks are available after the default net is switched, but
        // before LegacyTypeTracker sends legacy broadcasts
        for (final NetworkReassignment.RequestReassignment event :
                changes.getRequestReassignments()) {
            if (null != event.mNewNetwork) {
                notifyNetworkAvailable(event.mNewNetwork, event.mNetworkRequestInfo);
            } else {
                callCallbackForRequest(event.mNetworkRequestInfo, event.mOldNetwork,
                        ConnectivityManager.CALLBACK_LOST, 0);
            }
        }

        // Update the inactivity state before processing listen callbacks, because the background
        // computation depends on whether the network is inactive. Don't send the LOSING callbacks
        // just yet though, because they have to be sent after the listens are processed to keep
        // backward compatibility.
        final ArrayList<NetworkAgentInfo> inactiveNetworks = new ArrayList<>();
        for (final NetworkAgentInfo nai : nais) {
            // Rematching may have altered the inactivity state of some networks, so update all
            // inactivity timers. updateInactivityState reads the state from the network agent
            // and does nothing if the state has not changed : the source of truth is controlled
            // with NetworkAgentInfo#lingerRequest and NetworkAgentInfo#unlingerRequest, which
            // have been called while rematching the individual networks above.
            if (updateInactivityState(nai, now)) {
                inactiveNetworks.add(nai);
            }
        }

        for (final NetworkAgentInfo nai : nais) {
            final boolean oldBackground = oldBgNetworks.contains(nai);
            // Process listen requests and update capabilities if the background state has
            // changed for this network. For consistency with previous behavior, send onLost
            // callbacks before onAvailable.
            processNewlyLostListenRequests(nai);
            if (oldBackground != nai.isBackgroundNetwork()) {
                applyBackgroundChangeForRematch(nai);
            }
            processNewlySatisfiedListenRequests(nai);
        }

        for (final NetworkAgentInfo nai : inactiveNetworks) {
            // For nascent networks, if connecting with no foreground request, skip broadcasting
            // LOSING for backward compatibility. This is typical when mobile data connected while
            // wifi connected with mobile data always-on enabled.
            if (nai.isNascent()) continue;
            notifyNetworkLosing(nai, now);
        }

        // Send LOCAL_NETWORK_INFO_CHANGED callbacks now that onAvailable and onLost have been sent.
        if (null != localInfoChangedAgents) {
            for (final NetworkAgentInfo nai : localInfoChangedAgents) {
                notifyNetworkCallbacks(nai,
                        ConnectivityManager.CALLBACK_LOCAL_NETWORK_INFO_CHANGED);
            }
        }

        updateLegacyTypeTrackerAndVpnLockdownForRematch(changes, nais);

        // Tear down all unneeded networks.
        for (NetworkAgentInfo nai : mNetworkAgentInfos) {
            if (unneeded(nai, UnneededFor.TEARDOWN)) {
                if (nai.getInactivityExpiry() > 0) {
                    // This network has active linger timers and no requests, but is not
                    // lingering. Linger it.
                    //
                    // One way (the only way?) this can happen if this network is unvalidated
                    // and became unneeded due to another network improving its score to the
                    // point where this network will no longer be able to satisfy any requests
                    // even if it validates.
                    if (updateInactivityState(nai, now)) {
                        notifyNetworkLosing(nai, now);
                    }
                } else {
                    if (DBG) log("Reaping " + nai.toShortString());
                    teardownUnneededNetwork(nai);
                }
            }
        }
    }

    /**
     * Apply a change in background state resulting from rematching networks with requests.
     *
     * During rematch, a network may change background states by starting to satisfy or stopping
     * to satisfy a foreground request. Listens don't count for this. When a network changes
     * background states, its capabilities need to be updated and callbacks fired for the
     * capability change.
     *
     * @param nai The network that changed background states
     */
    private void applyBackgroundChangeForRematch(@NonNull final NetworkAgentInfo nai) {
        final NetworkCapabilities newNc = mixInCapabilities(nai, nai.networkCapabilities);
        if (Objects.equals(nai.networkCapabilities, newNc)) return;
        updateNetworkPermissions(nai, newNc);
        nai.getAndSetNetworkCapabilities(newNc);
        notifyNetworkCallbacks(nai, ConnectivityManager.CALLBACK_CAP_CHANGED);
    }

    private void updateLegacyTypeTrackerAndVpnLockdownForRematch(
            @NonNull final NetworkReassignment changes,
            @NonNull final Collection<NetworkAgentInfo> nais) {
        final NetworkReassignment.RequestReassignment reassignmentOfDefault =
                changes.getReassignment(mDefaultRequest);
        final NetworkAgentInfo oldDefaultNetwork =
                null != reassignmentOfDefault ? reassignmentOfDefault.mOldNetwork : null;
        final NetworkAgentInfo newDefaultNetwork =
                null != reassignmentOfDefault ? reassignmentOfDefault.mNewNetwork : null;

        if (oldDefaultNetwork != newDefaultNetwork) {
            // Maintain the illusion : since the legacy API only understands one network at a time,
            // if the default network changed, apps should see a disconnected broadcast for the
            // old default network before they see a connected broadcast for the new one.
            if (oldDefaultNetwork != null) {
                mLegacyTypeTracker.remove(oldDefaultNetwork.networkInfo.getType(),
                        oldDefaultNetwork, true);
            }
            if (newDefaultNetwork != null) {
                // The new default network can be newly null if and only if the old default
                // network doesn't satisfy the default request any more because it lost a
                // capability.
                mDefaultInetConditionPublished = newDefaultNetwork.isValidated() ? 100 : 0;
                mLegacyTypeTracker.add(
                        newDefaultNetwork.networkInfo.getType(), newDefaultNetwork);
            }
        }

        // Now that all the callbacks have been sent, send the legacy network broadcasts
        // as needed. This is necessary so that legacy requests correctly bind dns
        // requests to this network. The legacy users are listening for this broadcast
        // and will generally do a dns request so they can ensureRouteToHost and if
        // they do that before the callbacks happen they'll use the default network.
        //
        // TODO: Is there still a race here? The legacy broadcast will be sent after sending
        // callbacks, but if apps can receive the broadcast before the callback, they still might
        // have an inconsistent view of networking.
        //
        // This *does* introduce a race where if the user uses the new api
        // (notification callbacks) and then uses the old api (getNetworkInfo(type))
        // they may get old info. Reverse this after the old startUsing api is removed.
        // This is on top of the multiple intent sequencing referenced in the todo above.
        for (NetworkAgentInfo nai : nais) {
            if (nai.everConnected()) {
                addNetworkToLegacyTypeTracker(nai);
            }
        }
    }

    private void issueNetworkNeeds() {
        ensureRunningOnConnectivityServiceThread();
        for (final NetworkOfferInfo noi : mNetworkOffers) {
            issueNetworkNeeds(noi);
        }
    }

    private void issueNetworkNeeds(@NonNull final NetworkOfferInfo noi) {
        ensureRunningOnConnectivityServiceThread();
        for (final NetworkRequestInfo nri : mNetworkRequests.values()) {
            informOffer(nri, noi.offer, mNetworkRanker);
        }
    }

    /**
     * Inform a NetworkOffer about any new situation of a request.
     *
     * This function handles updates to offers. A number of events may happen that require
     * updating the registrant for this offer about the situation :
     * • The offer itself was updated. This may lead the offer to no longer being able
     *     to satisfy a request or beat a satisfier (and therefore be no longer needed),
     *     or conversely being strengthened enough to beat the satisfier (and therefore
     *     start being needed)
     * • The network satisfying a request changed (including cases where the request
     *     starts or stops being satisfied). The new network may be a stronger or weaker
     *     match than the old one, possibly affecting whether the offer is needed.
     * • The network satisfying a request updated their score. This may lead the offer
     *     to no longer be able to beat it if the current satisfier got better, or
     *     conversely start being a good choice if the current satisfier got weaker.
     *
     * @param nri The request
     * @param offer The offer. This may be an updated offer.
     */
    private static void informOffer(@NonNull NetworkRequestInfo nri,
            @NonNull final NetworkOffer offer, @NonNull final NetworkRanker networkRanker) {
        final NetworkRequest activeRequest = nri.isBeingSatisfied() ? nri.getActiveRequest() : null;
        final NetworkAgentInfo satisfier = null != activeRequest ? nri.getSatisfier() : null;

        // Multi-layer requests have a currently active request, the one being satisfied.
        // Since the system will try to bring up a better network than is currently satisfying
        // the request, NetworkProviders need to be told the offers matching the requests *above*
        // the currently satisfied one are needed, that the ones *below* the satisfied one are
        // not needed, and the offer is needed for the active request iff the offer can beat
        // the satisfier.
        // For non-multilayer requests, the logic above gracefully degenerates to only the
        // last case.
        // To achieve this, the loop below will proceed in three steps. In a first phase, inform
        // providers that the offer is needed for this request, until the active request is found.
        // In a second phase, deal with the currently active request. In a third phase, inform
        // the providers that offer is unneeded for the remaining requests.

        // First phase : inform providers of all requests above the active request.
        int i;
        for (i = 0; nri.mRequests.size() > i; ++i) {
            final NetworkRequest request = nri.mRequests.get(i);
            if (activeRequest == request) break; // Found the active request : go to phase 2
            if (!request.isRequest()) continue; // Listens/track defaults are never sent to offers
            // Since this request is higher-priority than the one currently satisfied, if the
            // offer can satisfy it, the provider should try and bring up the network for sure ;
            // no need to even ask the ranker – an offer that can satisfy is always better than
            // no network. Hence tell the provider so unless it already knew.
            if (request.canBeSatisfiedBy(offer.caps) && !offer.neededFor(request)) {
                offer.onNetworkNeeded(request);
            }
        }

        // Second phase : deal with the active request (if any)
        if (null != activeRequest && activeRequest.isRequest()) {
            final boolean oldNeeded = offer.neededFor(activeRequest);
            // If an offer can satisfy the request, it is considered needed if it is currently
            // served by this provider or if this offer can beat the current satisfier.
            final boolean currentlyServing = satisfier != null
                    && satisfier.factorySerialNumber == offer.providerId
                    && activeRequest.canBeSatisfiedBy(offer.caps);
            final boolean newNeeded = currentlyServing
                    || networkRanker.mightBeat(activeRequest, satisfier, offer);
            if (newNeeded != oldNeeded) {
                if (newNeeded) {
                    offer.onNetworkNeeded(activeRequest);
                } else {
                    // The offer used to be able to beat the satisfier. Now it can't.
                    offer.onNetworkUnneeded(activeRequest);
                }
            }
        }

        // Third phase : inform the providers that the offer isn't needed for any request
        // below the active one.
        for (++i /* skip the active request */; nri.mRequests.size() > i; ++i) {
            final NetworkRequest request = nri.mRequests.get(i);
            if (!request.isRequest()) continue; // Listens/track defaults are never sent to offers
            // Since this request is lower-priority than the one currently satisfied, if the
            // offer can satisfy it, the provider should not try and bring up the network.
            // Hence tell the provider so unless it already knew.
            if (offer.neededFor(request)) {
                offer.onNetworkUnneeded(request);
            }
        }
    }

    private void addNetworkToLegacyTypeTracker(@NonNull final NetworkAgentInfo nai) {
        for (int i = 0; i < nai.numNetworkRequests(); i++) {
            NetworkRequest nr = nai.requestAt(i);
            if (nr.legacyType != TYPE_NONE && nr.isRequest()) {
                // legacy type tracker filters out repeat adds
                mLegacyTypeTracker.add(nr.legacyType, nai);
            }
        }

        // A VPN generally won't get added to the legacy tracker in the "for (nri)" loop above,
        // because usually there are no NetworkRequests it satisfies (e.g., mDefaultRequest
        // wants the NOT_VPN capability, so it will never be satisfied by a VPN). So, add the
        // newNetwork to the tracker explicitly (it's a no-op if it has already been added).
        if (nai.isVPN()) {
            mLegacyTypeTracker.add(TYPE_VPN, nai);
        }
    }

    private void updateInetCondition(NetworkAgentInfo nai) {
        // Don't bother updating until we've graduated to validated at least once.
        if (!nai.everValidated()) return;
        // For now only update icons for the default connection.
        // TODO: Update WiFi and cellular icons separately. b/17237507
        if (!isDefaultNetwork(nai)) return;

        int newInetCondition = nai.isValidated() ? 100 : 0;
        // Don't repeat publish.
        if (newInetCondition == mDefaultInetConditionPublished) return;

        mDefaultInetConditionPublished = newInetCondition;
        sendInetConditionBroadcast(nai.networkInfo);
    }

    @NonNull
    private NetworkInfo mixInInfo(@NonNull final NetworkAgentInfo nai, @NonNull NetworkInfo info) {
        final NetworkInfo newInfo = new NetworkInfo(info);
        // The suspended and roaming bits are managed in NetworkCapabilities.
        final boolean suspended =
                !nai.networkCapabilities.hasCapability(NET_CAPABILITY_NOT_SUSPENDED);
        if (suspended && info.getDetailedState() == NetworkInfo.DetailedState.CONNECTED) {
            // Only override the state with SUSPENDED if the network is currently in CONNECTED
            // state. This is because the network could have been suspended before connecting,
            // or it could be disconnecting while being suspended, and in both these cases
            // the state should not be overridden. Note that the only detailed state that
            // maps to State.CONNECTED is DetailedState.CONNECTED, so there is also no need to
            // worry about multiple different substates of CONNECTED.
            newInfo.setDetailedState(NetworkInfo.DetailedState.SUSPENDED, info.getReason(),
                    info.getExtraInfo());
        } else if (!suspended && info.getDetailedState() == NetworkInfo.DetailedState.SUSPENDED) {
            // SUSPENDED state is currently only overridden from CONNECTED state. In the case the
            // network agent is created, then goes to suspended, then goes out of suspended without
            // ever setting connected. Check if network agent is ever connected to update the state.
            newInfo.setDetailedState(nai.everConnected()
                    ? NetworkInfo.DetailedState.CONNECTED
                    : NetworkInfo.DetailedState.CONNECTING,
                    info.getReason(),
                    info.getExtraInfo());
        }
        newInfo.setRoaming(!nai.networkCapabilities.hasCapability(NET_CAPABILITY_NOT_ROAMING));
        return newInfo;
    }

    private void updateNetworkInfo(NetworkAgentInfo networkAgent, NetworkInfo info) {
        final NetworkInfo newInfo = mixInInfo(networkAgent, info);

        final NetworkInfo.State state = newInfo.getState();
        NetworkInfo oldInfo = null;
        synchronized (networkAgent) {
            oldInfo = networkAgent.networkInfo;
            networkAgent.networkInfo = newInfo;
        }

        if (DBG) {
            log(networkAgent.toShortString() + " EVENT_NETWORK_INFO_CHANGED, going from "
                    + oldInfo.getState() + " to " + state);
        }

        if (shouldCreateNativeNetwork(networkAgent, state)) {
            // A network that has just connected has zero requests and is thus a foreground network.
            networkAgent.networkCapabilities.addCapability(NET_CAPABILITY_FOREGROUND);

            if (!createNativeNetwork(networkAgent)) return;

            networkAgent.setCreated();

            // If the network is created immediately on register, then apply the LinkProperties now.
            // Otherwise, this is done further down when the network goes into connected state.
            // Applying the LinkProperties means that the network is ready to carry traffic -
            // interfaces and routing rules have been added, DNS servers programmed, etc.
            // For VPNs, this must be done before the capabilities are updated, because as soon as
            // that happens, UIDs are routed to the network.
            if (shouldCreateNetworksImmediately()) {
                applyInitialLinkProperties(networkAgent);
            }

            // TODO: should this move earlier? It doesn't seem to have anything to do with whether
            // a network is created or not.
            if (networkAgent.propagateUnderlyingCapabilities()) {
                // Initialize the network's capabilities to their starting values according to the
                // underlying networks. This ensures that the capabilities are correct before
                // anything happens to the network.
                updateCapabilitiesForNetwork(networkAgent);
            }
            networkAgent.onNetworkCreated();
            updateDisallowedUidsForNetwork(networkAgent);
            updateAllowedUids(networkAgent, null, networkAgent.networkCapabilities);
            updateProfileAllowedNetworks();
        }

        if (!networkAgent.everConnected() && state == NetworkInfo.State.CONNECTED) {
            networkAgent.setConnected();

            // NetworkCapabilities need to be set before sending the private DNS config to
            // NetworkMonitor, otherwise NetworkMonitor cannot determine if validation is required.
            networkAgent.getAndSetNetworkCapabilities(networkAgent.networkCapabilities);

            handlePerNetworkPrivateDnsConfig(networkAgent, mDnsManager.getPrivateDnsConfig());
            if (!shouldCreateNetworksImmediately()) {
                applyInitialLinkProperties(networkAgent);
            } else {
                // The network was created when the agent registered, and the LinkProperties are
                // already up-to-date. However, updateLinkProperties also makes some changes only
                // when the network connects. Apply those changes here. On T and below these are
                // handled by the applyInitialLinkProperties call just above.
                // TODO: stop relying on updateLinkProperties(..., null) to do this.
                // If something depends on both LinkProperties and connected state, it should be in
                // this method as well.
                networkAgent.clatd.update();
                updateProxy(networkAgent.linkProperties, null);
            }

            // If a rate limit has been configured and is applicable to this network (network
            // provides internet connectivity), apply it. The tc police filter cannot be attached
            // before the clsact qdisc is added which happens as part of updateLinkProperties ->
            // updateInterfaces -> RoutingCoordinatorManager#addInterfaceToNetwork
            // Note: in case of a system server crash, the NetworkController constructor in netd
            // (called when netd starts up) deletes the clsact qdisc of all interfaces.
            if (canNetworkBeRateLimited(networkAgent) && mIngressRateLimit >= 0) {
                mDeps.enableIngressRateLimit(networkAgent.linkProperties.getInterfaceName(),
                        mIngressRateLimit);
            }

            // Until parceled LinkProperties are sent directly to NetworkMonitor, the connect
            // command must be sent after updating LinkProperties to maximize chances of
            // NetworkMonitor seeing the correct LinkProperties when starting.
            // TODO: pass LinkProperties to the NetworkMonitor in the notifyNetworkConnected call.
            if (networkAgent.networkAgentConfig.acceptPartialConnectivity) {
                networkAgent.networkMonitor().setAcceptPartialConnectivity();
            }
            final NetworkMonitorParameters params = new NetworkMonitorParameters();
            params.networkAgentConfig = networkAgent.networkAgentConfig;
            params.networkCapabilities = networkAgent.networkCapabilities;
            params.linkProperties = new LinkProperties(networkAgent.linkProperties,
                    true /* parcelSensitiveFields */);
            // isAtLeastT() is conservative here, as recent versions of NetworkStack support the
            // newer callback even before T. However getInterfaceVersion is a synchronized binder
            // call that would cause a Log.wtf to be emitted from the system_server process, and
            // in the absence of a satisfactory, scalable solution which follows an easy/standard
            // process to check the interface version, just use an SDK check. NetworkStack will
            // always be new enough when running on T+.
            if (mDeps.isAtLeastT()) {
                networkAgent.networkMonitor().notifyNetworkConnected(params);
            } else {
                networkAgent.networkMonitor().notifyNetworkConnected(params.linkProperties,
                        params.networkCapabilities);
            }
            final long evaluationDelay;
            if (!networkAgent.networkCapabilities.hasSingleTransport(TRANSPORT_WIFI)) {
                // If the network is anything other than pure wifi, use the default timeout.
                evaluationDelay = DEFAULT_EVALUATION_TIMEOUT_MS;
            } else if (networkAgent.networkAgentConfig.isExplicitlySelected()) {
                // If the network is explicitly selected, use the default timeout because it's
                // shorter and the user is likely staring at the screen expecting it to validate
                // right away.
                evaluationDelay = DEFAULT_EVALUATION_TIMEOUT_MS;
            } else if (avoidBadWifi() || !activelyPreferBadWifi()) {
                // If avoiding bad wifi, or if not avoiding but also not preferring bad wifi
                evaluationDelay = DEFAULT_EVALUATION_TIMEOUT_MS;
            } else {
                // It's wifi, automatically connected, and bad wifi is preferred : use the
                // longer timeout to avoid the device switching to captive portals with bad
                // signal or very slow response.
                evaluationDelay = ACTIVELY_PREFER_BAD_WIFI_INITIAL_TIMEOUT_MS;
            }
            scheduleEvaluationTimeout(networkAgent.network, evaluationDelay);

            // Whether a particular NetworkRequest listen should cause signal strength thresholds to
            // be communicated to a particular NetworkAgent depends only on the network's immutable,
            // capabilities, so it only needs to be done once on initial connect, not every time the
            // network's capabilities change. Note that we do this before rematching the network,
            // so we could decide to tear it down immediately afterwards. That's fine though - on
            // disconnection NetworkAgents should stop any signal strength monitoring they have been
            // doing.
            updateSignalStrengthThresholds(networkAgent, "CONNECT", null);

            // Before first rematching networks, put an inactivity timer without any request, this
            // allows {@code updateInactivityState} to update the state accordingly and prevent
            // tearing down for any {@code unneeded} evaluation in this period.
            // Note that the timer will not be rescheduled since the expiry time is
            // fixed after connection regardless of the network satisfying other requests or not.
            // But it will be removed as soon as the network satisfies a request for the first time.
            networkAgent.lingerRequest(NetworkRequest.REQUEST_ID_NONE,
                    SystemClock.elapsedRealtime(), mNascentDelayMs);
            networkAgent.setInactive();

            if (mTrackMultiNetworkActivities) {
                // Start tracking activity of this network.
                // This must be called before rematchAllNetworksAndRequests since the network
                // should be tracked when the network becomes the default network.
                // This method does not trigger any callbacks or broadcasts. Callbacks or broadcasts
                // can be triggered later if this network becomes the default network.
                mNetworkActivityTracker.setupDataActivityTracking(networkAgent);
            }

            // Consider network even though it is not yet validated.
            rematchAllNetworksAndRequests();

            // This has to happen after matching the requests, because callbacks are just requests.
            notifyNetworkCallbacks(networkAgent, ConnectivityManager.CALLBACK_PRECHECK);
        } else if (state == NetworkInfo.State.DISCONNECTED) {
            networkAgent.disconnect();
            if (networkAgent.isVPN()) {
                updateVpnUids(networkAgent, networkAgent.networkCapabilities, null);
            }
            disconnectAndDestroyNetwork(networkAgent);
            if (networkAgent.isVPN()) {
                // As the active or bound network changes for apps, broadcast the default proxy, as
                // apps may need to update their proxy data. This is called after disconnecting from
                // VPN to make sure we do not broadcast the old proxy data.
                // TODO(b/122649188): send the broadcast only to VPN users.
                mProxyTracker.sendProxyBroadcast();
            }
        } else if (networkAgent.isCreated() && (oldInfo.getState() == NetworkInfo.State.SUSPENDED
                || state == NetworkInfo.State.SUSPENDED)) {
            mLegacyTypeTracker.update(networkAgent);
        }
    }

    private void updateNetworkScore(@NonNull final NetworkAgentInfo nai, final NetworkScore score) {
        if (VDBG || DDBG) log("updateNetworkScore for " + nai.toShortString() + " to " + score);
        nai.setScore(score);
        rematchAllNetworksAndRequests();
    }

    // Notify only this one new request of the current state. Transfer all the
    // current state by calling NetworkCapabilities and LinkProperties callbacks
    // so that callers can be guaranteed to have as close to atomicity in state
    // transfer as can be supported by this current API.
    protected void notifyNetworkAvailable(NetworkAgentInfo nai, NetworkRequestInfo nri) {
        mHandler.removeMessages(EVENT_TIMEOUT_NETWORK_REQUEST, nri);
        if (nri.mPendingIntent != null) {
            sendPendingIntentForRequest(nri, nai, ConnectivityManager.CALLBACK_AVAILABLE);
            // Attempt no subsequent state pushes where intents are involved.
            return;
        }

        final int blockedReasons = mUidBlockedReasons.get(nri.mAsUid, BLOCKED_REASON_NONE);
        final boolean metered = nai.networkCapabilities.isMetered();
        final boolean vpnBlocked = isUidBlockedByVpn(nri.mAsUid, mVpnBlockedUidRanges);
        callCallbackForRequest(nri, nai, ConnectivityManager.CALLBACK_AVAILABLE,
                getBlockedState(blockedReasons, metered, vpnBlocked));
    }

    // Notify the requests on this NAI that the network is now lingered.
    private void notifyNetworkLosing(@NonNull final NetworkAgentInfo nai, final long now) {
        final int lingerTime = (int) (nai.getInactivityExpiry() - now);
        notifyNetworkCallbacks(nai, ConnectivityManager.CALLBACK_LOSING, lingerTime);
    }

    private static int getBlockedState(int reasons, boolean metered, boolean vpnBlocked) {
        if (!metered) reasons &= ~BLOCKED_METERED_REASON_MASK;
        return vpnBlocked
                ? reasons | BLOCKED_REASON_LOCKDOWN_VPN
                : reasons & ~BLOCKED_REASON_LOCKDOWN_VPN;
    }

    private void setUidBlockedReasons(int uid, @BlockedReason int blockedReasons) {
        if (blockedReasons == BLOCKED_REASON_NONE) {
            mUidBlockedReasons.delete(uid);
        } else {
            mUidBlockedReasons.put(uid, blockedReasons);
        }
    }

    /**
     * Notify of the blocked state apps with a registered callback matching a given NAI.
     *
     * Unlike other callbacks, blocked status is different between each individual uid. So for
     * any given nai, all requests need to be considered according to the uid who filed it.
     *
     * @param nai The target NetworkAgentInfo.
     * @param oldMetered True if the previous network capabilities were metered.
     * @param newMetered True if the current network capabilities are metered.
     * @param oldBlockedUidRanges list of UID ranges previously blocked by lockdown VPN.
     * @param newBlockedUidRanges list of UID ranges blocked by lockdown VPN.
     */
    private void maybeNotifyNetworkBlocked(NetworkAgentInfo nai, boolean oldMetered,
            boolean newMetered, List<UidRange> oldBlockedUidRanges,
            List<UidRange> newBlockedUidRanges) {

        for (int i = 0; i < nai.numNetworkRequests(); i++) {
            NetworkRequest nr = nai.requestAt(i);
            NetworkRequestInfo nri = mNetworkRequests.get(nr);

            final int blockedReasons = mUidBlockedReasons.get(nri.mAsUid, BLOCKED_REASON_NONE);
            final boolean oldVpnBlocked = isUidBlockedByVpn(nri.mAsUid, oldBlockedUidRanges);
            final boolean newVpnBlocked = (oldBlockedUidRanges != newBlockedUidRanges)
                    ? isUidBlockedByVpn(nri.mAsUid, newBlockedUidRanges)
                    : oldVpnBlocked;

            final int oldBlockedState = getBlockedState(blockedReasons, oldMetered, oldVpnBlocked);
            final int newBlockedState = getBlockedState(blockedReasons, newMetered, newVpnBlocked);
            if (oldBlockedState != newBlockedState) {
                callCallbackForRequest(nri, nai, ConnectivityManager.CALLBACK_BLK_CHANGED,
                        newBlockedState);
            }
        }
    }

    /**
     * Notify apps with a given UID of the new blocked state according to new uid state.
     * @param uid The uid for which the rules changed.
     * @param blockedReasons The reasons for why an uid is blocked.
     */
    private void maybeNotifyNetworkBlockedForNewState(int uid, @BlockedReason int blockedReasons) {
        for (final NetworkAgentInfo nai : mNetworkAgentInfos) {
            final boolean metered = nai.networkCapabilities.isMetered();
            final boolean vpnBlocked = isUidBlockedByVpn(uid, mVpnBlockedUidRanges);

            final int oldBlockedState = getBlockedState(
                    mUidBlockedReasons.get(uid, BLOCKED_REASON_NONE), metered, vpnBlocked);
            final int newBlockedState = getBlockedState(blockedReasons, metered, vpnBlocked);
            if (oldBlockedState == newBlockedState) {
                continue;
            }
            for (int i = 0; i < nai.numNetworkRequests(); i++) {
                NetworkRequest nr = nai.requestAt(i);
                NetworkRequestInfo nri = mNetworkRequests.get(nr);
                if (nri != null && nri.mAsUid == uid) {
                    callCallbackForRequest(nri, nai, ConnectivityManager.CALLBACK_BLK_CHANGED,
                            newBlockedState);
                }
            }
        }
    }

    @VisibleForTesting
    protected void sendLegacyNetworkBroadcast(NetworkAgentInfo nai, DetailedState state, int type) {
        // The NetworkInfo we actually send out has no bearing on the real
        // state of affairs. For example, if the default connection is mobile,
        // and a request for HIPRI has just gone away, we need to pretend that
        // HIPRI has just disconnected. So we need to set the type to HIPRI and
        // the state to DISCONNECTED, even though the network is of type MOBILE
        // and is still connected.
        NetworkInfo info = new NetworkInfo(nai.networkInfo);
        info.setType(type);
        filterForLegacyLockdown(info);
        if (state != DetailedState.DISCONNECTED) {
            info.setDetailedState(state, null, info.getExtraInfo());
            sendConnectedBroadcast(info);
        } else {
            info.setDetailedState(state, info.getReason(), info.getExtraInfo());
            Intent intent = new Intent(ConnectivityManager.CONNECTIVITY_ACTION);
            intent.putExtra(ConnectivityManager.EXTRA_NETWORK_INFO, info);
            intent.putExtra(ConnectivityManager.EXTRA_NETWORK_TYPE, info.getType());
            if (info.isFailover()) {
                intent.putExtra(ConnectivityManager.EXTRA_IS_FAILOVER, true);
                nai.networkInfo.setFailover(false);
            }
            if (info.getReason() != null) {
                intent.putExtra(ConnectivityManager.EXTRA_REASON, info.getReason());
            }
            if (info.getExtraInfo() != null) {
                intent.putExtra(ConnectivityManager.EXTRA_EXTRA_INFO, info.getExtraInfo());
            }
            NetworkAgentInfo newDefaultAgent = null;
            if (nai.isSatisfyingRequest(mDefaultRequest.mRequests.get(0).requestId)) {
                newDefaultAgent = mDefaultRequest.getSatisfier();
                if (newDefaultAgent != null) {
                    intent.putExtra(ConnectivityManager.EXTRA_OTHER_NETWORK_INFO,
                            newDefaultAgent.networkInfo);
                } else {
                    intent.putExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, true);
                }
            }
            intent.putExtra(ConnectivityManager.EXTRA_INET_CONDITION,
                    mDefaultInetConditionPublished);
            sendStickyBroadcast(intent);
            if (newDefaultAgent != null) {
                sendConnectedBroadcast(newDefaultAgent.networkInfo);
            }
        }
    }

    protected void notifyNetworkCallbacks(NetworkAgentInfo networkAgent, int notifyType, int arg1) {
        if (VDBG || DDBG) {
            String notification = ConnectivityManager.getCallbackName(notifyType);
            log("notifyType " + notification + " for " + networkAgent.toShortString());
        }
        for (int i = 0; i < networkAgent.numNetworkRequests(); i++) {
            NetworkRequest nr = networkAgent.requestAt(i);
            NetworkRequestInfo nri = mNetworkRequests.get(nr);
            if (VDBG) log(" sending notification for " + nr);
            if (nri.mPendingIntent == null) {
                callCallbackForRequest(nri, networkAgent, notifyType, arg1);
            } else {
                sendPendingIntentForRequest(nri, networkAgent, notifyType);
            }
        }
    }

    protected void notifyNetworkCallbacks(NetworkAgentInfo networkAgent, int notifyType) {
        notifyNetworkCallbacks(networkAgent, notifyType, 0);
    }

    /**
     * Returns the list of all interfaces that could be used by network traffic that does not
     * explicitly specify a network. This includes the default network, but also all VPNs that are
     * currently connected.
     *
     * Must be called on the handler thread.
     */
    @NonNull
    private ArrayList<Network> getDefaultNetworks() {
        ensureRunningOnConnectivityServiceThread();
        final ArrayList<Network> defaultNetworks = new ArrayList<>();
        final Set<Integer> activeNetIds = new ArraySet<>();
        for (final NetworkRequestInfo nri : mDefaultNetworkRequests) {
            if (nri.isBeingSatisfied()) {
                activeNetIds.add(nri.getSatisfier().network().netId);
            }
        }
        for (NetworkAgentInfo nai : mNetworkAgentInfos) {
            if (activeNetIds.contains(nai.network().netId) || nai.isVPN()) {
                defaultNetworks.add(nai.network);
            }
        }
        return defaultNetworks;
    }

    /**
     * Notify NetworkStatsService that the set of active ifaces has changed, or that one of the
     * active iface's tracked properties has changed.
     */
    private void notifyIfacesChangedForNetworkStats() {
        ensureRunningOnConnectivityServiceThread();
        String activeIface = null;
        LinkProperties activeLinkProperties = getActiveLinkProperties();
        if (activeLinkProperties != null) {
            activeIface = activeLinkProperties.getInterfaceName();
        }

        final UnderlyingNetworkInfo[] underlyingNetworkInfos = getAllVpnInfo();
        try {
            final ArrayList<NetworkStateSnapshot> snapshots = new ArrayList<>();
            snapshots.addAll(getAllNetworkStateSnapshots());
            mStatsManager.notifyNetworkStatus(getDefaultNetworks(),
                    snapshots, activeIface, Arrays.asList(underlyingNetworkInfos));
        } catch (Exception ignored) {
        }
    }

    @Override
    public String getCaptivePortalServerUrl() {
        enforceNetworkStackOrSettingsPermission();
        String settingUrl = mResources.get().getString(
                R.string.config_networkCaptivePortalServerUrl);

        if (!TextUtils.isEmpty(settingUrl)) {
            return settingUrl;
        }

        settingUrl = Settings.Global.getString(mContext.getContentResolver(),
                ConnectivitySettingsManager.CAPTIVE_PORTAL_HTTP_URL);
        if (!TextUtils.isEmpty(settingUrl)) {
            return settingUrl;
        }

        return DEFAULT_CAPTIVE_PORTAL_HTTP_URL;
    }

    @Override
    public void startNattKeepalive(Network network, int intervalSeconds,
            ISocketKeepaliveCallback cb, String srcAddr, int srcPort, String dstAddr) {
        enforceKeepalivePermission();
        mKeepaliveTracker.startNattKeepalive(
                getNetworkAgentInfoForNetwork(network), null /* fd */,
                intervalSeconds, cb, srcAddr, srcPort, dstAddr, NattSocketKeepalive.NATT_PORT,
                // Keep behavior of the deprecated method as it is. Set automaticOnOffKeepalives to
                // false and set the underpinned network to null because there is no way and no
                // plan to configure automaticOnOffKeepalives or underpinnedNetwork in this
                // deprecated method.
                false /* automaticOnOffKeepalives */, null /* underpinnedNetwork */);
    }

    @Override
    public void startNattKeepaliveWithFd(Network network, ParcelFileDescriptor pfd, int resourceId,
            int intervalSeconds, ISocketKeepaliveCallback cb, String srcAddr,
            String dstAddr, boolean automaticOnOffKeepalives, Network underpinnedNetwork) {
        try {
            final FileDescriptor fd = pfd.getFileDescriptor();
            mKeepaliveTracker.startNattKeepalive(
                    getNetworkAgentInfoForNetwork(network), fd, resourceId,
                    intervalSeconds, cb, srcAddr, dstAddr, NattSocketKeepalive.NATT_PORT,
                    automaticOnOffKeepalives, underpinnedNetwork);
        } finally {
            // FileDescriptors coming from AIDL calls must be manually closed to prevent leaks.
            // startNattKeepalive calls Os.dup(fd) before returning, so we can close immediately.
            if (pfd != null && Binder.getCallingPid() != Process.myPid()) {
                IoUtils.closeQuietly(pfd);
            }
        }
    }

    @Override
    public void startTcpKeepalive(Network network, ParcelFileDescriptor pfd, int intervalSeconds,
            ISocketKeepaliveCallback cb) {
        try {
            enforceKeepalivePermission();
            final FileDescriptor fd = pfd.getFileDescriptor();
            mKeepaliveTracker.startTcpKeepalive(
                    getNetworkAgentInfoForNetwork(network), fd, intervalSeconds, cb);
        } finally {
            // FileDescriptors coming from AIDL calls must be manually closed to prevent leaks.
            // startTcpKeepalive calls Os.dup(fd) before returning, so we can close immediately.
            if (pfd != null && Binder.getCallingPid() != Process.myPid()) {
                IoUtils.closeQuietly(pfd);
            }
        }
    }

    @Override
    public void stopKeepalive(@NonNull final ISocketKeepaliveCallback cb) {
        mHandler.sendMessage(mHandler.obtainMessage(
                NetworkAgent.CMD_STOP_SOCKET_KEEPALIVE, 0, SocketKeepalive.SUCCESS,
                Objects.requireNonNull(cb).asBinder()));
    }

    @Override
    public int[] getSupportedKeepalives() {
        enforceAnyPermissionOf(mContext, android.Manifest.permission.NETWORK_SETTINGS,
                // Backwards compatibility with CTS 13
                android.Manifest.permission.QUERY_ALL_PACKAGES);

        return BinderUtils.withCleanCallingIdentity(() ->
                KeepaliveResourceUtil.getSupportedKeepalives(mContext));
    }

    @Override
    public void factoryReset() {
        enforceSettingsPermission();

        final int uid = mDeps.getCallingUid();
        final long token = Binder.clearCallingIdentity();
        try {
            if (mUserManager.hasUserRestrictionForUser(UserManager.DISALLOW_NETWORK_RESET,
                    UserHandle.getUserHandleForUid(uid))) {
                return;
            }

            final IpMemoryStore ipMemoryStore = IpMemoryStore.getMemoryStore(mContext);
            ipMemoryStore.factoryReset();

            // Turn airplane mode off
            setAirplaneMode(false);

            // restore private DNS settings to default mode (opportunistic)
            if (!mUserManager.hasUserRestrictionForUser(UserManager.DISALLOW_CONFIG_PRIVATE_DNS,
                    UserHandle.getUserHandleForUid(uid))) {
                ConnectivitySettingsManager.setPrivateDnsMode(mContext,
                        PRIVATE_DNS_MODE_OPPORTUNISTIC);
            }

            Settings.Global.putString(mContext.getContentResolver(),
                    ConnectivitySettingsManager.NETWORK_AVOID_BAD_WIFI, null);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    @Override
    public byte[] getNetworkWatchlistConfigHash() {
        NetworkWatchlistManager nwm = mContext.getSystemService(NetworkWatchlistManager.class);
        if (nwm == null) {
            loge("Unable to get NetworkWatchlistManager");
            return null;
        }
        // Redirect it to network watchlist service to access watchlist file and calculate hash.
        return nwm.getWatchlistConfigHash();
    }

    private void logNetworkEvent(NetworkAgentInfo nai, int evtype) {
        int[] transports = nai.networkCapabilities.getTransportTypes();
        mMetricsLog.log(nai.network.getNetId(), transports, new NetworkEvent(evtype));
    }

    private static boolean toBool(int encodedBoolean) {
        return encodedBoolean != 0; // Only 0 means false.
    }

    private static int encodeBool(boolean b) {
        return b ? 1 : 0;
    }

    @Override
    public int handleShellCommand(@NonNull ParcelFileDescriptor in,
            @NonNull ParcelFileDescriptor out, @NonNull ParcelFileDescriptor err,
            @NonNull String[] args) {
        return new ShellCmd().exec(this, in.getFileDescriptor(), out.getFileDescriptor(),
                err.getFileDescriptor(), args);
    }

    private class ShellCmd extends BasicShellCommandHandler {

        private Boolean parseBooleanArgument(final String arg) {
            if ("true".equals(arg)) {
                return true;
            } else if ("false".equals(arg)) {
                return false;
            } else {
                getOutPrintWriter().println("Invalid boolean argument: " + arg);
                return null;
            }
        }

        private Integer parseIntegerArgument(final String arg) {
            try {
                return Integer.valueOf(arg);
            } catch (NumberFormatException ne) {
                getOutPrintWriter().println("Invalid integer argument: " + arg);
                return null;
            }
        }

        @Override
        public int onCommand(String cmd) {
            if (cmd == null) {
                return handleDefaultCommands(cmd);
            }
            final PrintWriter pw = getOutPrintWriter();
            try {
                switch (cmd) {
                    case "airplane-mode":
                        // Usage : adb shell cmd connectivity airplane-mode [enable|disable]
                        // If no argument, get and display the current status
                        final String action = getNextArg();
                        if ("enable".equals(action)) {
                            setAirplaneMode(true);
                            return 0;
                        } else if ("disable".equals(action)) {
                            setAirplaneMode(false);
                            return 0;
                        } else if (action == null) {
                            final ContentResolver cr = mContext.getContentResolver();
                            final int enabled = Settings.Global.getInt(cr,
                                    Settings.Global.AIRPLANE_MODE_ON);
                            pw.println(enabled == 0 ? "disabled" : "enabled");
                            return 0;
                        } else {
                            onHelp();
                            return -1;
                        }
                    case "set-chain3-enabled": {
                        final Boolean enabled = parseBooleanArgument(getNextArg());
                        if (null == enabled) {
                            onHelp();
                            return -1;
                        }
                        Log.i(TAG, (enabled ? "En" : "Dis") + "abled FIREWALL_CHAIN_OEM_DENY_3");
                        setFirewallChainEnabled(ConnectivityManager.FIREWALL_CHAIN_OEM_DENY_3,
                                enabled);
                        return 0;
                    }
                    case "get-chain3-enabled": {
                        final boolean chainEnabled = getFirewallChainEnabled(
                                ConnectivityManager.FIREWALL_CHAIN_OEM_DENY_3);
                        pw.println("chain:" + (chainEnabled ? "enabled" : "disabled"));
                        return 0;
                    }
                    case "set-package-networking-enabled": {
                        final Boolean enabled = parseBooleanArgument(getNextArg());
                        final String packageName = getNextArg();
                        if (null == enabled || null == packageName) {
                            onHelp();
                            return -1;
                        }
                        // Throws NameNotFound if the package doesn't exist.
                        final int appId = setPackageFirewallRule(
                                ConnectivityManager.FIREWALL_CHAIN_OEM_DENY_3,
                                packageName, enabled ? FIREWALL_RULE_DEFAULT : FIREWALL_RULE_DENY);
                        final String msg = (enabled ? "Enabled" : "Disabled")
                                + " networking for " + packageName + ", appId " + appId;
                        Log.i(TAG, msg);
                        pw.println(msg);
                        return 0;
                    }
                    case "get-package-networking-enabled": {
                        final String packageName = getNextArg();
                        final int rule = getPackageFirewallRule(
                                ConnectivityManager.FIREWALL_CHAIN_OEM_DENY_3, packageName);
                        if (FIREWALL_RULE_ALLOW == rule || FIREWALL_RULE_DEFAULT == rule) {
                            pw.println(packageName + ":" + "allow");
                        } else if (FIREWALL_RULE_DENY == rule) {
                            pw.println(packageName + ":" + "deny");
                        } else {
                            throw new IllegalStateException("Unknown rule " + rule + " for package "
                                    + packageName);
                        }
                        return 0;
                    }
                    case "set-background-networking-enabled-for-uid": {
                        final Integer uid = parseIntegerArgument(getNextArg());
                        final Boolean enabled = parseBooleanArgument(getNextArg());
                        if (null == enabled || null == uid) {
                            onHelp();
                            return -1;
                        }
                        final int rule = enabled ? FIREWALL_RULE_ALLOW : FIREWALL_RULE_DEFAULT;
                        setUidFirewallRule(FIREWALL_CHAIN_BACKGROUND, uid, rule);
                        final String msg = (enabled ? "Enabled" : "Disabled")
                                + " background networking for  uid " + uid;
                        Log.i(TAG, msg);
                        pw.println(msg);
                        return 0;
                    }
                    case "get-background-networking-enabled-for-uid": {
                        final Integer uid = parseIntegerArgument(getNextArg());
                        if (null == uid) {
                            onHelp();
                            return -1;
                        }
                        final int rule = getUidFirewallRule(FIREWALL_CHAIN_BACKGROUND, uid);
                        if (FIREWALL_RULE_ALLOW == rule) {
                            pw.println(uid + ": allow");
                        } else if (FIREWALL_RULE_DENY == rule  || FIREWALL_RULE_DEFAULT == rule) {
                            pw.println(uid + ": deny");
                        } else {
                            throw new IllegalStateException(
                                    "Unknown rule " + rule + " for uid " + uid);
                        }
                        return 0;
                    }
                    case "reevaluate":
                        // Usage : adb shell cmd connectivity reevaluate <netId>
                        // If netId is omitted, then reevaluate the default network
                        final String netId = getNextArg();
                        final NetworkAgentInfo nai;
                        if (null == netId) {
                            // Note that the command is running on the wrong thread to call this,
                            // so this could in principle return stale data. But it can't crash.
                            nai = getDefaultNetwork();
                        } else {
                            // If netId can't be parsed, this throws NumberFormatException, which
                            // is passed back to adb who prints it.
                            nai = getNetworkAgentInfoForNetId(Integer.parseInt(netId));
                        }
                        if (null == nai) {
                            pw.println("Unknown network (net ID not found or no default network)");
                            return 0;
                        }
                        Log.d(TAG, "Reevaluating network " + nai.network);
                        reportNetworkConnectivity(nai.network, !nai.isValidated());
                        return 0;
                    case "bpf-get-cgroup-program-id": {
                        // Usage : adb shell cmd connectivity bpf-get-cgroup-program-id <type>
                        // Get cgroup bpf program Id for the given type. See BpfUtils#getProgramId
                        // for more detail.
                        // If type can't be parsed, this throws NumberFormatException, which
                        // is passed back to adb who prints it.
                        final int type = Integer.parseInt(getNextArg());
                        final int ret = BpfUtils.getProgramId(type);
                        pw.println(ret);
                        return 0;
                    }
                    default:
                        return handleDefaultCommands(cmd);
                }
            } catch (Exception e) {
                pw.println(e);
            }
            return -1;
        }

        @Override
        public void onHelp() {
            PrintWriter pw = getOutPrintWriter();
            pw.println("Connectivity service commands:");
            pw.println("  help");
            pw.println("    Print this help text.");
            pw.println("  airplane-mode [enable|disable]");
            pw.println("    Turn airplane mode on or off.");
            pw.println("  airplane-mode");
            pw.println("    Get airplane mode.");
            pw.println("  set-chain3-enabled [true|false]");
            pw.println("    Enable or disable FIREWALL_CHAIN_OEM_DENY_3 for debugging.");
            pw.println("  get-chain3-enabled");
            pw.println("    Returns whether FIREWALL_CHAIN_OEM_DENY_3 is enabled.");
            pw.println("  set-package-networking-enabled [true|false] [package name]");
            pw.println("    Set the deny bit in FIREWALL_CHAIN_OEM_DENY_3 to package. This has\n"
                    + "    no effect if the chain is disabled.");
            pw.println("  get-package-networking-enabled [package name]");
            pw.println("    Get the deny bit in FIREWALL_CHAIN_OEM_DENY_3 for package.");
            pw.println("  set-background-networking-enabled-for-uid [uid] [true|false]");
            pw.println("    Set the allow bit in FIREWALL_CHAIN_BACKGROUND for the given uid.");
            pw.println("  get-background-networking-enabled-for-uid [uid]");
            pw.println("    Get the allow bit in FIREWALL_CHAIN_BACKGROUND for the given uid.");
        }
    }

    private int getVpnType(@Nullable NetworkAgentInfo vpn) {
        if (vpn == null) return VpnManager.TYPE_VPN_NONE;
        final TransportInfo ti = vpn.networkCapabilities.getTransportInfo();
        if (!(ti instanceof VpnTransportInfo)) return VpnManager.TYPE_VPN_NONE;
        return ((VpnTransportInfo) ti).getType();
    }

    private void maybeUpdateWifiRoamTimestamp(@NonNull NetworkAgentInfo nai,
            @NonNull NetworkCapabilities nc) {
        final TransportInfo prevInfo = nai.networkCapabilities.getTransportInfo();
        final TransportInfo newInfo = nc.getTransportInfo();
        if (!(prevInfo instanceof WifiInfo) || !(newInfo instanceof WifiInfo)) {
            return;
        }
        if (!TextUtils.equals(((WifiInfo)prevInfo).getBSSID(), ((WifiInfo)newInfo).getBSSID())) {
            nai.lastRoamTime = SystemClock.elapsedRealtime();
        }
    }

    /**
     * @param connectionInfo the connection to resolve.
     * @return {@code uid} if the connection is found and the app has permission to observe it
     * (e.g., if it is associated with the calling VPN app's tunnel) or {@code INVALID_UID} if the
     * connection is not found.
     */
    public int getConnectionOwnerUid(ConnectionInfo connectionInfo) {
        if (connectionInfo.protocol != IPPROTO_TCP && connectionInfo.protocol != IPPROTO_UDP) {
            throw new IllegalArgumentException("Unsupported protocol " + connectionInfo.protocol);
        }

        final int uid = mDeps.getConnectionOwnerUid(connectionInfo.protocol,
                connectionInfo.local, connectionInfo.remote);

        if (uid == INVALID_UID) return uid;  // Not found.

        // Connection owner UIDs are visible only to the network stack and to the VpnService-based
        // VPN, if any, that applies to the UID that owns the connection.
        if (hasNetworkStackPermission()) return uid;

        final NetworkAgentInfo vpn = getVpnForUid(uid);
        if (vpn == null || getVpnType(vpn) != VpnManager.TYPE_VPN_SERVICE
                || vpn.networkCapabilities.getOwnerUid() != mDeps.getCallingUid()) {
            return INVALID_UID;
        }

        return uid;
    }

    /**
     * Returns a IBinder to a TestNetworkService. Will be lazily created as needed.
     *
     * <p>The TestNetworkService must be run in the system server due to TUN creation.
     */
    @Override
    public IBinder startOrGetTestNetworkService() {
        synchronized (mTNSLock) {
            TestNetworkService.enforceTestNetworkPermissions(mContext);

            if (mTNS == null) {
                mTNS = new TestNetworkService(mContext);
            }

            return mTNS;
        }
    }

    /**
     * Handler used for managing all Connectivity Diagnostics related functions.
     *
     * @see android.net.ConnectivityDiagnosticsManager
     *
     * TODO(b/147816404): Explore moving ConnectivityDiagnosticsHandler to a separate file
     */
    @VisibleForTesting
    class ConnectivityDiagnosticsHandler extends Handler {
        private final String mTag = ConnectivityDiagnosticsHandler.class.getSimpleName();

        /**
         * Used to handle ConnectivityDiagnosticsCallback registration events from {@link
         * android.net.ConnectivityDiagnosticsManager}.
         * obj = ConnectivityDiagnosticsCallbackInfo with IConnectivityDiagnosticsCallback and
         * NetworkRequestInfo to be registered
         */
        private static final int EVENT_REGISTER_CONNECTIVITY_DIAGNOSTICS_CALLBACK = 1;

        /**
         * Used to handle ConnectivityDiagnosticsCallback unregister events from {@link
         * android.net.ConnectivityDiagnosticsManager}.
         * obj = the IConnectivityDiagnosticsCallback to be unregistered
         * arg1 = the uid of the caller
         */
        private static final int EVENT_UNREGISTER_CONNECTIVITY_DIAGNOSTICS_CALLBACK = 2;

        /**
         * Event for {@link NetworkStateTrackerHandler} to trigger ConnectivityReport callbacks
         * after processing {@link #CMD_SEND_CONNECTIVITY_REPORT} events.
         * obj = {@link ConnectivityReportEvent} representing ConnectivityReport info reported from
         * NetworkMonitor.
         * data = PersistableBundle of extras passed from NetworkMonitor.
         */
        private static final int CMD_SEND_CONNECTIVITY_REPORT = 3;

        /**
         * Event for NetworkMonitor to inform ConnectivityService that a potential data stall has
         * been detected on the network.
         * obj = Long the timestamp (in millis) for when the suspected data stall was detected.
         * arg1 = {@link DataStallReport#DetectionMethod} indicating the detection method.
         * arg2 = NetID.
         * data = PersistableBundle of extras passed from NetworkMonitor.
         */
        private static final int EVENT_DATA_STALL_SUSPECTED = 4;

        /**
         * Event for ConnectivityDiagnosticsHandler to handle network connectivity being reported to
         * the platform. This event will invoke {@link
         * IConnectivityDiagnosticsCallback#onNetworkConnectivityReported} for permissioned
         * callbacks.
         * obj = ReportedNetworkConnectivityInfo with info on reported Network connectivity.
         */
        private static final int EVENT_NETWORK_CONNECTIVITY_REPORTED = 5;

        private ConnectivityDiagnosticsHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_REGISTER_CONNECTIVITY_DIAGNOSTICS_CALLBACK: {
                    handleRegisterConnectivityDiagnosticsCallback(
                            (ConnectivityDiagnosticsCallbackInfo) msg.obj);
                    break;
                }
                case EVENT_UNREGISTER_CONNECTIVITY_DIAGNOSTICS_CALLBACK: {
                    handleUnregisterConnectivityDiagnosticsCallback(
                            (IConnectivityDiagnosticsCallback) msg.obj, msg.arg1);
                    break;
                }
                case CMD_SEND_CONNECTIVITY_REPORT: {
                    final ConnectivityReportEvent reportEvent =
                            (ConnectivityReportEvent) msg.obj;

                    handleNetworkTestedWithExtras(reportEvent, reportEvent.mExtras);
                    break;
                }
                case EVENT_DATA_STALL_SUSPECTED: {
                    final NetworkAgentInfo nai = getNetworkAgentInfoForNetId(msg.arg2);
                    final Pair<Long, PersistableBundle> arg =
                            (Pair<Long, PersistableBundle>) msg.obj;
                    if (nai == null) break;

                    handleDataStallSuspected(nai, arg.first, msg.arg1, arg.second);
                    break;
                }
                case EVENT_NETWORK_CONNECTIVITY_REPORTED: {
                    handleNetworkConnectivityReported((ReportedNetworkConnectivityInfo) msg.obj);
                    break;
                }
                default: {
                    Log.e(mTag, "Unrecognized event in ConnectivityDiagnostics: " + msg.what);
                }
            }
        }
    }

    /** Class used for cleaning up IConnectivityDiagnosticsCallback instances after their death. */
    @VisibleForTesting
    class ConnectivityDiagnosticsCallbackInfo implements Binder.DeathRecipient {
        @NonNull private final IConnectivityDiagnosticsCallback mCb;
        @NonNull private final NetworkRequestInfo mRequestInfo;
        @NonNull private final String mCallingPackageName;

        @VisibleForTesting
        ConnectivityDiagnosticsCallbackInfo(
                @NonNull IConnectivityDiagnosticsCallback cb,
                @NonNull NetworkRequestInfo nri,
                @NonNull String callingPackageName) {
            mCb = cb;
            mRequestInfo = nri;
            mCallingPackageName = callingPackageName;
        }

        @Override
        public void binderDied() {
            log("ConnectivityDiagnosticsCallback IBinder died.");
            unregisterConnectivityDiagnosticsCallback(mCb);
        }
    }

    /**
     * Class used for sending information from {@link
     * NetworkMonitorCallbacks#notifyNetworkTestedWithExtras} to the handler for processing it.
     */
    private static class NetworkTestedResults {
        private final int mNetId;
        private final int mTestResult;
        @Nullable private final String mRedirectUrl;

        private NetworkTestedResults(
                int netId, int testResult, long timestampMillis, @Nullable String redirectUrl) {
            mNetId = netId;
            mTestResult = testResult;
            mRedirectUrl = redirectUrl;
        }
    }

    /**
     * Class used for sending information from {@link NetworkStateTrackerHandler} to {@link
     * ConnectivityDiagnosticsHandler}.
     */
    private static class ConnectivityReportEvent {
        private final long mTimestampMillis;
        @NonNull private final NetworkAgentInfo mNai;
        private final PersistableBundle mExtras;

        private ConnectivityReportEvent(long timestampMillis, @NonNull NetworkAgentInfo nai,
                PersistableBundle p) {
            mTimestampMillis = timestampMillis;
            mNai = nai;
            mExtras = p;
        }
    }

    /**
     * Class used for sending info for a call to {@link #reportNetworkConnectivity()} to {@link
     * ConnectivityDiagnosticsHandler}.
     */
    private static class ReportedNetworkConnectivityInfo {
        public final boolean hasConnectivity;
        public final boolean isNetworkRevalidating;
        public final int reporterUid;
        @NonNull public final NetworkAgentInfo nai;

        private ReportedNetworkConnectivityInfo(
                boolean hasConnectivity,
                boolean isNetworkRevalidating,
                int reporterUid,
                @NonNull NetworkAgentInfo nai) {
            this.hasConnectivity = hasConnectivity;
            this.isNetworkRevalidating = isNetworkRevalidating;
            this.reporterUid = reporterUid;
            this.nai = nai;
        }
    }

    private void handleRegisterConnectivityDiagnosticsCallback(
            @NonNull ConnectivityDiagnosticsCallbackInfo cbInfo) {
        ensureRunningOnConnectivityServiceThread();

        final IConnectivityDiagnosticsCallback cb = cbInfo.mCb;
        final IBinder iCb = cb.asBinder();
        final NetworkRequestInfo nri = cbInfo.mRequestInfo;

        // Connectivity Diagnostics are meant to be used with a single network request. It would be
        // confusing for these networks to change when an NRI is satisfied in another layer.
        if (nri.isMultilayerRequest()) {
            throw new IllegalArgumentException("Connectivity Diagnostics do not support multilayer "
                + "network requests.");
        }

        // This means that the client registered the same callback multiple times. Do
        // not override the previous entry, and exit silently.
        if (mConnectivityDiagnosticsCallbacks.containsKey(iCb)) {
            if (VDBG) log("Diagnostics callback is already registered");

            // Decrement the reference count for this NetworkRequestInfo. The reference count is
            // incremented when the NetworkRequestInfo is created as part of
            // enforceRequestCountLimit().
            nri.mPerUidCounter.decrementCount(nri.mUid);
            return;
        }

        mConnectivityDiagnosticsCallbacks.put(iCb, cbInfo);

        try {
            iCb.linkToDeath(cbInfo, 0);
        } catch (RemoteException e) {
            cbInfo.binderDied();
            return;
        }

        // Once registered, provide ConnectivityReports for matching Networks
        final List<NetworkAgentInfo> matchingNetworks = new ArrayList<>();
        synchronized (mNetworkForNetId) {
            for (int i = 0; i < mNetworkForNetId.size(); i++) {
                final NetworkAgentInfo nai = mNetworkForNetId.valueAt(i);
                // Connectivity Diagnostics rejects multilayer requests at registration hence get(0)
                if (nai.satisfies(nri.mRequests.get(0))) {
                    matchingNetworks.add(nai);
                }
            }
        }
        for (final NetworkAgentInfo nai : matchingNetworks) {
            final ConnectivityReport report = nai.getConnectivityReport();
            if (report == null) {
                continue;
            }
            if (!hasConnectivityDiagnosticsPermissions(
                    nri.mPid, nri.mUid, nai, cbInfo.mCallingPackageName)) {
                continue;
            }

            try {
                cb.onConnectivityReportAvailable(report);
            } catch (RemoteException e) {
                // Exception while sending the ConnectivityReport. Move on to the next network.
            }
        }
    }

    private void handleUnregisterConnectivityDiagnosticsCallback(
            @NonNull IConnectivityDiagnosticsCallback cb, int uid) {
        ensureRunningOnConnectivityServiceThread();
        final IBinder iCb = cb.asBinder();

        final ConnectivityDiagnosticsCallbackInfo cbInfo =
                mConnectivityDiagnosticsCallbacks.remove(iCb);
        if (cbInfo == null) {
            if (VDBG) log("Removing diagnostics callback that is not currently registered");
            return;
        }

        final NetworkRequestInfo nri = cbInfo.mRequestInfo;

        // Caller's UID must either be the registrants (if they are unregistering) or the System's
        // (if the Binder died)
        if (uid != nri.mUid && uid != Process.SYSTEM_UID) {
            if (DBG) loge("Uid(" + uid + ") not registrant's (" + nri.mUid + ") or System's");
            return;
        }

        // Decrement the reference count for this NetworkRequestInfo. The reference count is
        // incremented when the NetworkRequestInfo is created as part of
        // enforceRequestCountLimit().
        nri.mPerUidCounter.decrementCount(nri.mUid);

        iCb.unlinkToDeath(cbInfo, 0);
    }

    private void handleNetworkTestedWithExtras(
            @NonNull ConnectivityReportEvent reportEvent, @NonNull PersistableBundle extras) {
        final NetworkAgentInfo nai = reportEvent.mNai;
        final NetworkCapabilities networkCapabilities =
                getNetworkCapabilitiesWithoutUids(nai.networkCapabilities);
        final ConnectivityReport report =
                new ConnectivityReport(
                        reportEvent.mNai.network,
                        reportEvent.mTimestampMillis,
                        nai.linkProperties,
                        networkCapabilities,
                        extras);
        nai.setConnectivityReport(report);

        final List<IConnectivityDiagnosticsCallback> results =
                getMatchingPermissionedCallbacks(nai, Process.INVALID_UID);
        for (final IConnectivityDiagnosticsCallback cb : results) {
            try {
                cb.onConnectivityReportAvailable(report);
            } catch (RemoteException ex) {
                loge("Error invoking onConnectivityReportAvailable", ex);
            }
        }
    }

    private void handleDataStallSuspected(
            @NonNull NetworkAgentInfo nai, long timestampMillis, int detectionMethod,
            @NonNull PersistableBundle extras) {
        final NetworkCapabilities networkCapabilities =
                getNetworkCapabilitiesWithoutUids(nai.networkCapabilities);
        final DataStallReport report =
                new DataStallReport(
                        nai.network,
                        timestampMillis,
                        detectionMethod,
                        nai.linkProperties,
                        networkCapabilities,
                        extras);
        final List<IConnectivityDiagnosticsCallback> results =
                getMatchingPermissionedCallbacks(nai, Process.INVALID_UID);
        for (final IConnectivityDiagnosticsCallback cb : results) {
            try {
                cb.onDataStallSuspected(report);
            } catch (RemoteException ex) {
                loge("Error invoking onDataStallSuspected", ex);
            }
        }
    }

    private void handleNetworkConnectivityReported(
            @NonNull ReportedNetworkConnectivityInfo reportedNetworkConnectivityInfo) {
        final NetworkAgentInfo nai = reportedNetworkConnectivityInfo.nai;
        final ConnectivityReport cachedReport = nai.getConnectivityReport();

        // If the Network is being re-validated as a result of this call to
        // reportNetworkConnectivity(), notify all permissioned callbacks. Otherwise, only notify
        // permissioned callbacks registered by the reporter.
        final List<IConnectivityDiagnosticsCallback> results =
                getMatchingPermissionedCallbacks(
                        nai,
                        reportedNetworkConnectivityInfo.isNetworkRevalidating
                                ? Process.INVALID_UID
                                : reportedNetworkConnectivityInfo.reporterUid);

        for (final IConnectivityDiagnosticsCallback cb : results) {
            try {
                cb.onNetworkConnectivityReported(
                        nai.network, reportedNetworkConnectivityInfo.hasConnectivity);
            } catch (RemoteException ex) {
                loge("Error invoking onNetworkConnectivityReported", ex);
            }

            // If the Network isn't re-validating, also provide the cached report. If there is no
            // cached report, the Network is still being validated and a report will be sent once
            // validation is complete. Note that networks which never undergo validation will still
            // have a cached ConnectivityReport with RESULT_SKIPPED.
            if (!reportedNetworkConnectivityInfo.isNetworkRevalidating && cachedReport != null) {
                try {
                    cb.onConnectivityReportAvailable(cachedReport);
                } catch (RemoteException ex) {
                    loge("Error invoking onConnectivityReportAvailable", ex);
                }
            }
        }
    }

    private NetworkCapabilities getNetworkCapabilitiesWithoutUids(@NonNull NetworkCapabilities nc) {
        final NetworkCapabilities sanitized = new NetworkCapabilities(nc,
                NetworkCapabilities.REDACT_ALL);
        sanitized.setUids(null);
        sanitized.setAdministratorUids(new int[0]);
        sanitized.setOwnerUid(Process.INVALID_UID);
        return sanitized;
    }

    /**
     * Gets a list of ConnectivityDiagnostics callbacks that match the specified Network and uid.
     *
     * <p>If Process.INVALID_UID is specified, all matching callbacks will be returned.
     */
    private List<IConnectivityDiagnosticsCallback> getMatchingPermissionedCallbacks(
            @NonNull NetworkAgentInfo nai, int uid) {
        final List<IConnectivityDiagnosticsCallback> results = new ArrayList<>();
        for (Entry<IBinder, ConnectivityDiagnosticsCallbackInfo> entry :
                mConnectivityDiagnosticsCallbacks.entrySet()) {
            final ConnectivityDiagnosticsCallbackInfo cbInfo = entry.getValue();
            final NetworkRequestInfo nri = cbInfo.mRequestInfo;

            // Connectivity Diagnostics rejects multilayer requests at registration hence get(0).
            if (!nai.satisfies(nri.mRequests.get(0))) {
                continue;
            }

            // UID for this callback must either be:
            //  - INVALID_UID (which sends callbacks to all UIDs), or
            //  - The callback's owner (the owner called reportNetworkConnectivity() and is being
            //    notified as a result)
            if (uid != Process.INVALID_UID && uid != nri.mUid) {
                continue;
            }

            if (!hasConnectivityDiagnosticsPermissions(
                    nri.mPid, nri.mUid, nai, cbInfo.mCallingPackageName)) {
                continue;
            }

            results.add(entry.getValue().mCb);
        }
        return results;
    }

    private boolean isLocationPermissionRequiredForConnectivityDiagnostics(
            @NonNull NetworkAgentInfo nai) {
        // TODO(b/188483916): replace with a transport-agnostic location-aware check
        return nai.networkCapabilities.hasTransport(TRANSPORT_WIFI);
    }

    private boolean hasLocationPermission(String packageName, int uid) {
        // LocationPermissionChecker#checkLocationPermission can throw SecurityException if the uid
        // and package name don't match. Throwing on the CS thread is not acceptable, so wrap the
        // call in a try-catch.
        try {
            if (!mLocationPermissionChecker.checkLocationPermission(
                        packageName, null /* featureId */, uid, null /* message */)) {
                return false;
            }
        } catch (SecurityException e) {
            return false;
        }

        return true;
    }

    private boolean ownsVpnRunningOverNetwork(int uid, Network network) {
        for (NetworkAgentInfo virtual : mNetworkAgentInfos) {
            if (virtual.propagateUnderlyingCapabilities()
                    && virtual.networkCapabilities.getOwnerUid() == uid
                    && CollectionUtils.contains(virtual.declaredUnderlyingNetworks, network)) {
                return true;
            }
        }

        return false;
    }

    @CheckResult
    @VisibleForTesting
    boolean hasConnectivityDiagnosticsPermissions(
            int callbackPid, int callbackUid, NetworkAgentInfo nai, String callbackPackageName) {
        if (hasNetworkStackPermission(callbackPid, callbackUid)) {
            return true;
        }
        if (mAllowSysUiConnectivityReports
                && hasSystemBarServicePermission(callbackPid, callbackUid)) {
            return true;
        }

        // Administrator UIDs also contains the Owner UID
        final int[] administratorUids = nai.networkCapabilities.getAdministratorUids();
        if (!CollectionUtils.contains(administratorUids, callbackUid)
                && !ownsVpnRunningOverNetwork(callbackUid, nai.network)) {
            return false;
        }

        return !isLocationPermissionRequiredForConnectivityDiagnostics(nai)
                || hasLocationPermission(callbackPackageName, callbackUid);
    }

    @Override
    public void registerConnectivityDiagnosticsCallback(
            @NonNull IConnectivityDiagnosticsCallback callback,
            @NonNull NetworkRequest request,
            @NonNull String callingPackageName) {
        Objects.requireNonNull(callback, "callback must not be null");
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(callingPackageName, "callingPackageName must not be null");

        if (request.legacyType != TYPE_NONE) {
            throw new IllegalArgumentException("ConnectivityManager.TYPE_* are deprecated."
                    + " Please use NetworkCapabilities instead.");
        }
        final int callingUid = mDeps.getCallingUid();
        mAppOpsManager.checkPackage(callingUid, callingPackageName);

        // This NetworkCapabilities is only used for matching to Networks. Clear out its owner uid
        // and administrator uids to be safe.
        final NetworkCapabilities nc = new NetworkCapabilities(request.networkCapabilities);
        restrictRequestUidsForCallerAndSetRequestorInfo(nc, callingUid, callingPackageName);

        final NetworkRequest requestWithId =
                new NetworkRequest(
                        nc, TYPE_NONE, nextNetworkRequestId(), NetworkRequest.Type.LISTEN);

        // NetworkRequestInfos created here count towards MAX_NETWORK_REQUESTS_PER_UID limit.
        //
        // nri is not bound to the death of callback. Instead, callback.bindToDeath() is set in
        // handleRegisterConnectivityDiagnosticsCallback(). nri will be cleaned up as part of the
        // callback's binder death.
        final NetworkRequestInfo nri = new NetworkRequestInfo(callingUid, requestWithId);
        final ConnectivityDiagnosticsCallbackInfo cbInfo =
                new ConnectivityDiagnosticsCallbackInfo(callback, nri, callingPackageName);

        mConnectivityDiagnosticsHandler.sendMessage(
                mConnectivityDiagnosticsHandler.obtainMessage(
                        ConnectivityDiagnosticsHandler
                                .EVENT_REGISTER_CONNECTIVITY_DIAGNOSTICS_CALLBACK,
                        cbInfo));
    }

    @Override
    public void unregisterConnectivityDiagnosticsCallback(
            @NonNull IConnectivityDiagnosticsCallback callback) {
        Objects.requireNonNull(callback, "callback must be non-null");
        mConnectivityDiagnosticsHandler.sendMessage(
                mConnectivityDiagnosticsHandler.obtainMessage(
                        ConnectivityDiagnosticsHandler
                                .EVENT_UNREGISTER_CONNECTIVITY_DIAGNOSTICS_CALLBACK,
                        mDeps.getCallingUid(),
                        0,
                        callback));
    }

    private boolean hasUnderlyingTestNetworks(NetworkCapabilities nc) {
        final List<Network> underlyingNetworks = nc.getUnderlyingNetworks();
        if (underlyingNetworks == null) return false;

        for (Network network : underlyingNetworks) {
            if (getNetworkCapabilitiesInternal(network).hasTransport(TRANSPORT_TEST)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void simulateDataStall(int detectionMethod, long timestampMillis,
            @NonNull Network network, @NonNull PersistableBundle extras) {
        Objects.requireNonNull(network, "network must not be null");
        Objects.requireNonNull(extras, "extras must not be null");

        enforceAnyPermissionOf(mContext,
                android.Manifest.permission.MANAGE_TEST_NETWORKS,
                android.Manifest.permission.NETWORK_STACK);
        final NetworkCapabilities nc = getNetworkCapabilitiesInternal(network);
        if (!nc.hasTransport(TRANSPORT_TEST) && !hasUnderlyingTestNetworks(nc)) {
            throw new SecurityException(
                    "Data Stall simulation is only possible for test networks or networks built on"
                            + " top of test networks");
        }

        final NetworkAgentInfo nai = getNetworkAgentInfoForNetwork(network);
        if (nai == null
                || (nai.creatorUid != mDeps.getCallingUid()
                        && nai.creatorUid != Process.SYSTEM_UID)) {
            throw new SecurityException(
                    "Data Stall simulation is only possible for network " + "creators");
        }

        // Instead of passing the data stall directly to the ConnectivityDiagnostics handler, treat
        // this as a Data Stall received directly from NetworkMonitor. This requires wrapping the
        // Data Stall information as a DataStallReportParcelable and passing to
        // #notifyDataStallSuspected. This ensures that unknown Data Stall detection methods are
        // still passed to ConnectivityDiagnostics (with new detection methods masked).
        final DataStallReportParcelable p = new DataStallReportParcelable();
        p.timestampMillis = timestampMillis;
        p.detectionMethod = detectionMethod;

        if (hasDataStallDetectionMethod(p, DETECTION_METHOD_DNS_EVENTS)) {
            p.dnsConsecutiveTimeouts = extras.getInt(KEY_DNS_CONSECUTIVE_TIMEOUTS);
        }
        if (hasDataStallDetectionMethod(p, DETECTION_METHOD_TCP_METRICS)) {
            p.tcpPacketFailRate = extras.getInt(KEY_TCP_PACKET_FAIL_RATE);
            p.tcpMetricsCollectionPeriodMillis = extras.getInt(
                    KEY_TCP_METRICS_COLLECTION_PERIOD_MILLIS);
        }

        notifyDataStallSuspected(p, network.getNetId());
    }

    /**
     * Class to hold the information for network activity change event from idle timers
     * {@link NetdCallback#onInterfaceClassActivityChanged(boolean, int, long, int)}
     */
    private static final class NetworkActivityParams {
        public final boolean isActive;
        // If TrackMultiNetworkActivities is enabled, idleTimer label is netid.
        // If TrackMultiNetworkActivities is disabled, idleTimer label is transport type.
        public final int label;
        public final long timestampNs;
        // Uid represents the uid that was responsible for waking the radio.
        // -1 for no uid and uid is -1 if isActive is false.
        public final int uid;

        NetworkActivityParams(boolean isActive, int label, long timestampNs, int uid) {
            this.isActive = isActive;
            this.label = label;
            this.timestampNs = timestampNs;
            this.uid = uid;
        }
    }

    private class NetdCallback extends BaseNetdUnsolicitedEventListener {
        @Override
        public void onInterfaceClassActivityChanged(boolean isActive, int label,
                long timestampNs, int uid) {
            mHandler.sendMessage(mHandler.obtainMessage(EVENT_REPORT_NETWORK_ACTIVITY,
                    new NetworkActivityParams(isActive, label, timestampNs, uid)));
        }

        @Override
        public void onInterfaceLinkStateChanged(@NonNull String iface, boolean up) {
            mHandler.post(() -> {
                for (NetworkAgentInfo nai : mNetworkAgentInfos) {
                    nai.clatd.handleInterfaceLinkStateChanged(iface, up);
                }
            });
        }

        @Override
        public void onInterfaceRemoved(@NonNull String iface) {
            mHandler.post(() -> {
                for (NetworkAgentInfo nai : mNetworkAgentInfos) {
                    nai.clatd.handleInterfaceRemoved(iface);
                }
            });
        }
    }

    private final boolean mTrackMultiNetworkActivities;
    private final LegacyNetworkActivityTracker mNetworkActivityTracker;

    /**
     * Class used for updating network activity tracking with netd and notify network activity
     * changes.
     */
    @VisibleForTesting
    public static final class LegacyNetworkActivityTracker {
        private static final int NO_UID = -1;
        private final Context mContext;
        private final INetd mNetd;
        private final Handler mHandler;
        private final RemoteCallbackList<INetworkActivityListener> mNetworkActivityListeners =
                new RemoteCallbackList<>();
        // Indicate the current system default network activity is active or not.
        // This needs to be volatile to allow non handler threads to read this value without lock.
        // If there is no default network, default network is considered active to keep the existing
        // behavior. Initial value is used until first connect to the default network.
        private volatile boolean mIsDefaultNetworkActive = true;
        private Network mDefaultNetwork;
        // Key is netId. Value is configured idle timer information.
        private final SparseArray<IdleTimerParams> mActiveIdleTimers = new SparseArray<>();
        private final boolean mTrackMultiNetworkActivities;
        // Store netIds of Wi-Fi networks whose idletimers report that they are active
        private final Set<Integer> mActiveWifiNetworks = new ArraySet<>();
        // Store netIds of cellular networks whose idletimers report that they are active
        private final Set<Integer> mActiveCellularNetworks = new ArraySet<>();

        private static class IdleTimerParams {
            public final int timeout;
            public final int transportType;

            IdleTimerParams(int timeout, int transport) {
                this.timeout = timeout;
                this.transportType = transport;
            }
        }

        LegacyNetworkActivityTracker(@NonNull Context context, @NonNull INetd netd,
                @NonNull Handler handler, boolean trackMultiNetworkActivities) {
            mContext = context;
            mNetd = netd;
            mHandler = handler;
            mTrackMultiNetworkActivities = trackMultiNetworkActivities;
        }

        private void ensureRunningOnConnectivityServiceThread() {
            if (mHandler.getLooper().getThread() != Thread.currentThread()) {
                throw new IllegalStateException("Not running on ConnectivityService thread: "
                                + Thread.currentThread().getName());
            }
        }

        /**
         * Update network activity and call BatteryStats to update radio power state if the
         * mobile or Wi-Fi activity is changed.
         * LegacyNetworkActivityTracker considers the mobile network is active if at least one
         * mobile network is active since BatteryStatsService only maintains a single power state
         * for the mobile network.
         * The Wi-Fi network is also the same.
         *
         * {@link #setupDataActivityTracking} and {@link #removeDataActivityTracking} use
         * TRANSPORT_CELLULAR as the transportType argument if the network has both cell and Wi-Fi
         * transports.
         */
        private void maybeUpdateRadioPowerState(final int netId, final int transportType,
                final boolean isActive, final int uid) {
            if (transportType != TRANSPORT_WIFI && transportType != TRANSPORT_CELLULAR) {
                Log.e(TAG, "Unexpected transportType in maybeUpdateRadioPowerState: "
                        + transportType);
                return;
            }
            final Set<Integer> activeNetworks = transportType == TRANSPORT_WIFI
                    ? mActiveWifiNetworks : mActiveCellularNetworks;

            final boolean wasEmpty = activeNetworks.isEmpty();
            if (isActive) {
                activeNetworks.add(netId);
            } else {
                activeNetworks.remove(netId);
            }

            if (wasEmpty != activeNetworks.isEmpty()) {
                updateRadioPowerState(isActive, transportType, uid);
            }
        }

        private void handleDefaultNetworkActivity(final int transportType,
                final boolean isActive, final long timestampNs) {
            mIsDefaultNetworkActive = isActive;
            sendDataActivityBroadcast(transportTypeToLegacyType(transportType),
                    isActive, timestampNs);
            if (isActive) {
                reportNetworkActive();
            }
        }

        private void handleReportNetworkActivityWithNetIdLabel(
                NetworkActivityParams activityParams) {
            final int netId = activityParams.label;
            final IdleTimerParams idleTimerParams = mActiveIdleTimers.get(netId);
            if (idleTimerParams == null) {
                // This network activity change is not tracked anymore
                // This can happen if netd callback post activity change event message but idle
                // timer is removed before processing this message.
                return;
            }
            // TODO: if a network changes transports, storing the transport type in the
            // IdleTimerParams is not correct. Consider getting it from the network's
            // NetworkCapabilities instead.
            final int transportType = idleTimerParams.transportType;
            maybeUpdateRadioPowerState(netId, transportType,
                    activityParams.isActive, activityParams.uid);

            if (mDefaultNetwork == null || mDefaultNetwork.netId != netId) {
                // This activity change is not for the default network.
                return;
            }

            handleDefaultNetworkActivity(transportType, activityParams.isActive,
                    activityParams.timestampNs);
        }

        private void handleReportNetworkActivityWithTransportTypeLabel(
                NetworkActivityParams activityParams) {
            if (mActiveIdleTimers.size() == 0) {
                // This activity change is not for the current default network.
                // This can happen if netd callback post activity change event message but
                // the default network is lost before processing this message.
                return;
            }
            handleDefaultNetworkActivity(activityParams.label, activityParams.isActive,
                    activityParams.timestampNs);
        }

        /**
         * Handle network activity change
         */
        public void handleReportNetworkActivity(NetworkActivityParams activityParams) {
            ensureRunningOnConnectivityServiceThread();
            if (mTrackMultiNetworkActivities) {
                handleReportNetworkActivityWithNetIdLabel(activityParams);
            } else {
                handleReportNetworkActivityWithTransportTypeLabel(activityParams);
            }
        }

        private void reportNetworkActive() {
            final int length = mNetworkActivityListeners.beginBroadcast();
            if (DDBG) log("reportNetworkActive, notify " + length + " listeners");
            try {
                for (int i = 0; i < length; i++) {
                    try {
                        mNetworkActivityListeners.getBroadcastItem(i).onNetworkActive();
                    } catch (RemoteException | RuntimeException e) {
                        loge("Fail to send network activity to listener " + e);
                    }
                }
            } finally {
                mNetworkActivityListeners.finishBroadcast();
            }
        }

        // This is deprecated and only to support legacy use cases.
        private int transportTypeToLegacyType(int type) {
            switch (type) {
                case NetworkCapabilities.TRANSPORT_CELLULAR:
                    return TYPE_MOBILE;
                case NetworkCapabilities.TRANSPORT_WIFI:
                    return TYPE_WIFI;
                case NetworkCapabilities.TRANSPORT_BLUETOOTH:
                    return TYPE_BLUETOOTH;
                case NetworkCapabilities.TRANSPORT_ETHERNET:
                    return TYPE_ETHERNET;
                default:
                    loge("Unexpected transport in transportTypeToLegacyType: " + type);
            }
            return ConnectivityManager.TYPE_NONE;
        }

        public void sendDataActivityBroadcast(int deviceType, boolean active, long tsNanos) {
            final Intent intent = new Intent(ConnectivityManager.ACTION_DATA_ACTIVITY_CHANGE);
            intent.putExtra(ConnectivityManager.EXTRA_DEVICE_TYPE, deviceType);
            intent.putExtra(ConnectivityManager.EXTRA_IS_ACTIVE, active);
            intent.putExtra(ConnectivityManager.EXTRA_REALTIME_NS, tsNanos);
            final long ident = Binder.clearCallingIdentity();
            try {
                mContext.sendOrderedBroadcastAsUser(intent, UserHandle.ALL,
                        RECEIVE_DATA_ACTIVITY_CHANGE,
                        null /* resultReceiver */,
                        null /* scheduler */,
                        0 /* initialCode */,
                        null /* initialData */,
                        null /* initialExtra */);
            } finally {
                Binder.restoreCallingIdentity(ident);
            }
        }

        /**
         * Get idle timer label
         */
        @VisibleForTesting
        public static int getIdleTimerLabel(final boolean trackMultiNetworkActivities,
                final int netId, final int transportType) {
            return trackMultiNetworkActivities ? netId : transportType;
        }

        private boolean maybeCreateIdleTimer(
                String iface, int netId, int timeout, int transportType) {
            if (timeout <= 0 || iface == null) return false;
            try {
                final String label = Integer.toString(getIdleTimerLabel(
                        mTrackMultiNetworkActivities, netId, transportType));
                mNetd.idletimerAddInterface(iface, timeout, label);
                mActiveIdleTimers.put(netId, new IdleTimerParams(timeout, transportType));
                return true;
            } catch (Exception e) {
                loge("Exception in createIdleTimer", e);
                return false;
            }
        }

        /**
         * Setup data activity tracking for the given network.
         *
         * Every {@code setupDataActivityTracking} should be paired with a
         * {@link #removeDataActivityTracking} for cleanup.
         *
         * @return true if the idleTimer is added to the network, false otherwise
         */
        private boolean setupDataActivityTracking(NetworkAgentInfo networkAgent) {
            ensureRunningOnConnectivityServiceThread();
            final String iface = networkAgent.linkProperties.getInterfaceName();
            final int netId = networkAgent.network().netId;

            final int timeout;
            final int type;

            if (!networkAgent.networkCapabilities.hasCapability(NET_CAPABILITY_NOT_VPN)) {
                // Do not track VPN network.
                return false;
            } else if (networkAgent.networkCapabilities.hasTransport(
                    NetworkCapabilities.TRANSPORT_CELLULAR)) {
                timeout = Settings.Global.getInt(mContext.getContentResolver(),
                        ConnectivitySettingsManager.DATA_ACTIVITY_TIMEOUT_MOBILE,
                        10);
                type = NetworkCapabilities.TRANSPORT_CELLULAR;
            } else if (networkAgent.networkCapabilities.hasTransport(
                    NetworkCapabilities.TRANSPORT_WIFI)) {
                timeout = Settings.Global.getInt(mContext.getContentResolver(),
                        ConnectivitySettingsManager.DATA_ACTIVITY_TIMEOUT_WIFI,
                        15);
                type = NetworkCapabilities.TRANSPORT_WIFI;
            } else {
                return false; // do not track any other networks
            }

            final boolean hasIdleTimer = maybeCreateIdleTimer(iface, netId, timeout, type);
            if (hasIdleTimer || !mTrackMultiNetworkActivities) {
                // If trackMultiNetwork is disabled, NetworkActivityTracker updates radio power
                // state in all cases. If trackMultiNetwork is enabled, it updates radio power
                // state only about a network that has an idletimer.
                maybeUpdateRadioPowerState(netId, type, true /* isActive */, NO_UID);
            }
            return hasIdleTimer;
        }

        /**
         * Remove data activity tracking when network disconnects.
         */
        public void removeDataActivityTracking(NetworkAgentInfo networkAgent) {
            ensureRunningOnConnectivityServiceThread();
            final String iface = networkAgent.linkProperties.getInterfaceName();
            final int netId = networkAgent.network().netId;
            final NetworkCapabilities caps = networkAgent.networkCapabilities;

            if (iface == null) return;

            final int type;
            if (!networkAgent.networkCapabilities.hasCapability(NET_CAPABILITY_NOT_VPN)) {
                // Do not track VPN network.
                return;
            } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                type = NetworkCapabilities.TRANSPORT_CELLULAR;
            } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                type = NetworkCapabilities.TRANSPORT_WIFI;
            } else {
                return; // do not track any other networks
            }

            try {
                maybeUpdateRadioPowerState(netId, type, false /* isActive */, NO_UID);
                final IdleTimerParams params = mActiveIdleTimers.get(netId);
                if (params == null) {
                    // IdleTimer is not added if the configured timeout is 0 or negative value
                    return;
                }
                mActiveIdleTimers.remove(netId);
                final String label = Integer.toString(getIdleTimerLabel(
                        mTrackMultiNetworkActivities, netId, params.transportType));
                        // The call fails silently if no idle timer setup for this interface
                mNetd.idletimerRemoveInterface(iface, params.timeout, label);
            } catch (Exception e) {
                // You shall not crash!
                loge("Exception in removeDataActivityTracking " + e);
            }
        }

        private void updateDefaultNetworkActivity(NetworkAgentInfo defaultNetwork,
                boolean hasIdleTimer) {
            if (defaultNetwork != null) {
                mDefaultNetwork = defaultNetwork.network();
                mIsDefaultNetworkActive = true;
                // If only the default network is tracked, callbacks are called only when the
                // network has the idle timer.
                if (mTrackMultiNetworkActivities || hasIdleTimer) {
                    reportNetworkActive();
                }
            } else {
                mDefaultNetwork = null;
                // If there is no default network, default network is considered active to keep the
                // existing behavior.
                mIsDefaultNetworkActive = true;
            }
        }

        /**
         * Update the default network this class tracks the activity of.
         */
        public void updateDefaultNetwork(NetworkAgentInfo newNetwork,
                NetworkAgentInfo oldNetwork) {
            ensureRunningOnConnectivityServiceThread();
            // If TrackMultiNetworkActivities is enabled, devices add idleTimer when the network is
            // first connected and remove when the network is disconnected.
            // If TrackMultiNetworkActivities is disabled, devices add idleTimer when the network
            // becomes the default network and remove when the network becomes no longer the default
            // network.
            boolean hasIdleTimer = false;
            if (!mTrackMultiNetworkActivities && newNetwork != null) {
                hasIdleTimer = setupDataActivityTracking(newNetwork);
            }
            updateDefaultNetworkActivity(newNetwork, hasIdleTimer);
            if (!mTrackMultiNetworkActivities && oldNetwork != null) {
                removeDataActivityTracking(oldNetwork);
            }
        }

        private void updateRadioPowerState(boolean isActive, int transportType, int uid) {
            final BatteryStatsManager bs = mContext.getSystemService(BatteryStatsManager.class);
            switch (transportType) {
                case NetworkCapabilities.TRANSPORT_CELLULAR:
                    bs.reportMobileRadioPowerState(isActive, uid);
                    break;
                case NetworkCapabilities.TRANSPORT_WIFI:
                    bs.reportWifiRadioPowerState(isActive, uid);
                    break;
                default:
                    logw("Untracked transport type:" + transportType);
            }
        }

        public boolean isDefaultNetworkActive() {
            return mIsDefaultNetworkActive;
        }

        public void registerNetworkActivityListener(@NonNull INetworkActivityListener l) {
            mNetworkActivityListeners.register(l);
        }

        public void unregisterNetworkActivityListener(@NonNull INetworkActivityListener l) {
            mNetworkActivityListeners.unregister(l);
        }

        public void dump(IndentingPrintWriter pw) {
            pw.print("mTrackMultiNetworkActivities="); pw.println(mTrackMultiNetworkActivities);
            pw.print("mIsDefaultNetworkActive="); pw.println(mIsDefaultNetworkActive);
            pw.print("mDefaultNetwork="); pw.println(mDefaultNetwork);
            pw.println("Idle timers:");
            try {
                for (int i = 0; i < mActiveIdleTimers.size(); i++) {
                    pw.print("  "); pw.print(mActiveIdleTimers.keyAt(i)); pw.println(":");
                    final IdleTimerParams params = mActiveIdleTimers.valueAt(i);
                    pw.print("    timeout="); pw.print(params.timeout);
                    pw.print(" type="); pw.println(params.transportType);
                }
                pw.println("WiFi active networks: " + mActiveWifiNetworks);
                pw.println("Cellular active networks: " + mActiveCellularNetworks);
            } catch (Exception e) {
                // mActiveIdleTimers, mActiveWifiNetworks, and mActiveCellularNetworks should only
                // be accessed from handler thread, except dump(). As dump() is never called in
                // normal usage, it would be needlessly expensive to lock the collection only for
                // its benefit. Also, they are not expected to be updated frequently.
                // So catching the exception and logging.
                pw.println("Failed to dump NetworkActivityTracker: " + e);
            }
        }
    }

    /**
     * Registers {@link QosSocketFilter} with {@link IQosCallback}.
     *
     * @param socketInfo the socket information
     * @param callback the callback to register
     */
    @Override
    public void registerQosSocketCallback(@NonNull final QosSocketInfo socketInfo,
            @NonNull final IQosCallback callback) {
        final NetworkAgentInfo nai = getNetworkAgentInfoForNetwork(socketInfo.getNetwork());
        if (nai == null || nai.networkCapabilities == null) {
            try {
                callback.onError(QosCallbackException.EX_TYPE_FILTER_NETWORK_RELEASED);
            } catch (final RemoteException ex) {
                loge("registerQosCallbackInternal: RemoteException", ex);
            }
            return;
        }
        registerQosCallbackInternal(new QosSocketFilter(socketInfo), callback, nai);
    }

    /**
     * Register a {@link IQosCallback} with base {@link QosFilter}.
     *
     * @param filter the filter to register
     * @param callback the callback to register
     * @param nai the agent information related to the filter's network
     */
    @VisibleForTesting
    public void registerQosCallbackInternal(@NonNull final QosFilter filter,
            @NonNull final IQosCallback callback, @NonNull final NetworkAgentInfo nai) {
        Objects.requireNonNull(filter, "filter must be non-null");
        Objects.requireNonNull(callback, "callback must be non-null");

        if (!nai.networkCapabilities.hasCapability(NET_CAPABILITY_NOT_RESTRICTED)) {
            // TODO: Check allowed list here and ensure that either a) any QoS callback registered
            //  on this network is unregistered when the app loses permission or b) no QoS
            //  callbacks are sent for restricted networks unless the app currently has permission
            //  to access restricted networks.
            enforceConnectivityRestrictedNetworksPermission(false /* checkUidsAllowedList */);
        }
        mQosCallbackTracker.registerCallback(callback, filter, nai);
    }

    /**
     * Unregisters the given callback.
     *
     * @param callback the callback to unregister
     */
    @Override
    public void unregisterQosCallback(@NonNull final IQosCallback callback) {
        Objects.requireNonNull(callback, "callback must be non-null");
        mQosCallbackTracker.unregisterCallback(callback);
    }

    private boolean isNetworkPreferenceAllowedForProfile(@NonNull UserHandle profile) {
        // UserManager.isManagedProfile returns true for all apps in managed user profiles.
        // Enterprise device can be fully managed like device owner and such use case
        // also should be supported. Calling app check for work profile and fully managed device
        // is already done in DevicePolicyManager.
        // This check is an extra caution to be sure device is fully managed or not.
        final UserManager um = mContext.getSystemService(UserManager.class);
        final DevicePolicyManager dpm = mContext.getSystemService(DevicePolicyManager.class);
        if (um.isManagedProfile(profile.getIdentifier())) {
            return true;
        }
        if (mDeps.isAtLeastT() && dpm.getDeviceOwner() != null) return true;
        return false;
    }

    /**
     * Set a list of default network selection policies for a user profile or device owner.
     *
     * See the documentation for the individual preferences for a description of the supported
     * behaviors.
     *
     * @param profile If the device owner is set, any profile is allowed.
              Otherwise, the given profile can only be managed profile.
     * @param preferences the list of profile network preferences for the
     *        provided profile.
     * @param listener an optional listener to listen for completion of the operation.
     */
    @Override
    public void setProfileNetworkPreferences(
            @NonNull final UserHandle profile,
            @NonNull List<ProfileNetworkPreference> preferences,
            @Nullable final IOnCompleteListener listener) {
        Objects.requireNonNull(preferences);
        Objects.requireNonNull(profile);

        if (preferences.size() == 0) {
            final ProfileNetworkPreference pref = new ProfileNetworkPreference.Builder()
                    .setPreference(ConnectivityManager.PROFILE_NETWORK_PREFERENCE_DEFAULT)
                    .build();
            preferences.add(pref);
        }

        enforceNetworkStackPermission(mContext);
        if (DBG) {
            log("setProfileNetworkPreferences " + profile + " to " + preferences);
        }
        if (profile.getIdentifier() < 0) {
            throw new IllegalArgumentException("Must explicitly specify a user handle ("
                    + "UserHandle.CURRENT not supported)");
        }
        if (!isNetworkPreferenceAllowedForProfile(profile)) {
            throw new IllegalArgumentException("Profile must be a managed profile "
                    + "or the device owner must be set. ");
        }

        final List<ProfileNetworkPreferenceInfo> preferenceList = new ArrayList<>();
        boolean hasDefaultPreference = false;
        for (final ProfileNetworkPreference preference : preferences) {
            final NetworkCapabilities nc;
            boolean allowFallback = true;
            boolean blockingNonEnterprise = false;
            switch (preference.getPreference()) {
                case ConnectivityManager.PROFILE_NETWORK_PREFERENCE_DEFAULT:
                    nc = null;
                    hasDefaultPreference = true;
                    if (preference.getPreferenceEnterpriseId() != 0) {
                        throw new IllegalArgumentException(
                                "Invalid enterprise identifier in setProfileNetworkPreferences");
                    }
                    break;
                case ConnectivityManager.PROFILE_NETWORK_PREFERENCE_ENTERPRISE_BLOCKING:
                    blockingNonEnterprise = true;
                    // continue to process the enterprise preference.
                case ConnectivityManager.PROFILE_NETWORK_PREFERENCE_ENTERPRISE_NO_FALLBACK:
                    allowFallback = false;
                    // continue to process the enterprise preference.
                case ConnectivityManager.PROFILE_NETWORK_PREFERENCE_ENTERPRISE:
                    // This code is needed even though there is a check later on,
                    // because isRangeAlreadyInPreferenceList assumes that every preference
                    // has a UID list.
                    if (hasDefaultPreference) {
                        throw new IllegalArgumentException(
                                "Default profile preference should not be set along with other "
                                        + "preference");
                    }
                    if (!isEnterpriseIdentifierValid(preference.getPreferenceEnterpriseId())) {
                        throw new IllegalArgumentException(
                                "Invalid enterprise identifier in setProfileNetworkPreferences");
                    }
                    final Set<UidRange> uidRangeSet =
                            getUidListToBeAppliedForNetworkPreference(profile, preference);
                    if (!isRangeAlreadyInPreferenceList(preferenceList, uidRangeSet)) {
                        nc = createDefaultNetworkCapabilitiesForUidRangeSet(uidRangeSet);
                    } else {
                        throw new IllegalArgumentException(
                                "Overlapping uid range in setProfileNetworkPreferences");
                    }
                    nc.addCapability(NET_CAPABILITY_ENTERPRISE);
                    nc.addEnterpriseId(
                            preference.getPreferenceEnterpriseId());
                    nc.removeCapability(NET_CAPABILITY_NOT_RESTRICTED);
                    break;
                default:
                    throw new IllegalArgumentException(
                            "Invalid preference in setProfileNetworkPreferences");
            }
            preferenceList.add(new ProfileNetworkPreferenceInfo(
                    profile, nc, allowFallback, blockingNonEnterprise));
            if (hasDefaultPreference && preferenceList.size() > 1) {
                throw new IllegalArgumentException(
                        "Default profile preference should not be set along with other preference");
            }
        }
        mHandler.sendMessage(mHandler.obtainMessage(EVENT_SET_PROFILE_NETWORK_PREFERENCE,
                new Pair<>(preferenceList, listener)));
    }

    private Set<UidRange> getUidListToBeAppliedForNetworkPreference(
            @NonNull final UserHandle profile,
            @NonNull final ProfileNetworkPreference profileNetworkPreference) {
        final UidRange profileUids = UidRange.createForUser(profile);
        Set<UidRange> uidRangeSet = UidRangeUtils.convertArrayToUidRange(
                        profileNetworkPreference.getIncludedUids());

        if (uidRangeSet.size() > 0) {
            if (!UidRangeUtils.isRangeSetInUidRange(profileUids, uidRangeSet)) {
                throw new IllegalArgumentException(
                        "Allow uid range is outside the uid range of profile.");
            }
        } else {
            ArraySet<UidRange> disallowUidRangeSet = UidRangeUtils.convertArrayToUidRange(
                    profileNetworkPreference.getExcludedUids());
            if (disallowUidRangeSet.size() > 0) {
                if (!UidRangeUtils.isRangeSetInUidRange(profileUids, disallowUidRangeSet)) {
                    throw new IllegalArgumentException(
                            "disallow uid range is outside the uid range of profile.");
                }
                uidRangeSet = UidRangeUtils.removeRangeSetFromUidRange(profileUids,
                        disallowUidRangeSet);
            } else {
                uidRangeSet = new ArraySet<>();
                uidRangeSet.add(profileUids);
            }
        }
        return uidRangeSet;
    }

    private boolean isEnterpriseIdentifierValid(
            @NetworkCapabilities.EnterpriseId int identifier) {
        if ((identifier >= NET_ENTERPRISE_ID_1) && (identifier <= NET_ENTERPRISE_ID_5)) {
            return true;
        }
        return false;
    }

    private ArraySet<NetworkRequestInfo> createNrisFromProfileNetworkPreferences(
            @NonNull final NetworkPreferenceList<UserHandle, ProfileNetworkPreferenceInfo> prefs) {
        final ArraySet<NetworkRequestInfo> result = new ArraySet<>();
        for (final ProfileNetworkPreferenceInfo pref : prefs) {
            // The NRI for a user should contain the request for capabilities.
            // If fallback to default network is needed then NRI should include
            // the request for the default network. Create an image of it to
            // have the correct UIDs in it (also a request can only be part of one NRI, because
            // of lookups in 1:1 associations like mNetworkRequests).
            final ArrayList<NetworkRequest> nrs = new ArrayList<>();
            nrs.add(createNetworkRequest(NetworkRequest.Type.REQUEST, pref.capabilities));
            if (pref.allowFallback) {
                nrs.add(createDefaultInternetRequestForTransport(
                        TYPE_NONE, NetworkRequest.Type.TRACK_DEFAULT));
            }
            if (VDBG) {
                loge("pref.capabilities.getUids():" + UidRange.fromIntRanges(
                        pref.capabilities.getUids()));
            }

            setNetworkRequestUids(nrs, UidRange.fromIntRanges(pref.capabilities.getUids()));
            final NetworkRequestInfo nri = new NetworkRequestInfo(Process.myUid(), nrs,
                    PREFERENCE_ORDER_PROFILE);
            result.add(nri);
        }
        return result;
    }

    /**
     * Compare if the given UID range sets have the same UIDs.
     *
     */
    private boolean isRangeAlreadyInPreferenceList(
            @NonNull List<ProfileNetworkPreferenceInfo> preferenceList,
            @NonNull Set<UidRange> uidRangeSet) {
        if (uidRangeSet.size() == 0 || preferenceList.size() == 0) {
            return false;
        }
        for (ProfileNetworkPreferenceInfo pref : preferenceList) {
            if (UidRangeUtils.doesRangeSetOverlap(
                    UidRange.fromIntRanges(pref.capabilities.getUids()), uidRangeSet)) {
                return true;
            }
        }
        return false;
    }

    private void handleSetProfileNetworkPreference(
            @NonNull final List<ProfileNetworkPreferenceInfo> preferenceList,
            @Nullable final IOnCompleteListener listener) {
        /*
         * handleSetProfileNetworkPreference is always called for single user.
         * preferenceList only contains preferences for different uids within the same user
         * (enforced by getUidListToBeAppliedForNetworkPreference).
         * Clear all the existing preferences for the user before applying new preferences.
         *
         */
        mProfileNetworkPreferences = mProfileNetworkPreferences.minus(preferenceList.get(0).user);
        for (final ProfileNetworkPreferenceInfo preference : preferenceList) {
            mProfileNetworkPreferences = mProfileNetworkPreferences.plus(preference);
        }

        removeDefaultNetworkRequestsForPreference(PREFERENCE_ORDER_PROFILE);
        addPerAppDefaultNetworkRequests(
                createNrisFromProfileNetworkPreferences(mProfileNetworkPreferences));
        updateProfileAllowedNetworks();

        // Finally, rematch.
        rematchAllNetworksAndRequests();

        if (null != listener) {
            try {
                listener.onComplete();
            } catch (RemoteException e) {
                loge("Listener for setProfileNetworkPreference has died");
            }
        }
    }

    @VisibleForTesting
    @NonNull
    ArraySet<NetworkRequestInfo> createNrisForPreferenceOrder(@NonNull final Set<Integer> uids,
            @NonNull final List<NetworkRequest> requests,
            final int preferenceOrder) {
        final ArraySet<NetworkRequestInfo> nris = new ArraySet<>();
        if (uids.size() == 0) {
            // Should not create NetworkRequestInfo if no preferences. Without uid range in
            // NetworkRequestInfo, makeDefaultForApps() would treat it as a illegal NRI.
            return nris;
        }

        final Set<UidRange> ranges = new ArraySet<>();
        for (final int uid : uids) {
            ranges.add(new UidRange(uid, uid));
        }
        setNetworkRequestUids(requests, ranges);
        nris.add(new NetworkRequestInfo(Process.myUid(), requests, preferenceOrder));
        return nris;
    }

    ArraySet<NetworkRequestInfo> createNrisFromMobileDataPreferredUids(
            @NonNull final Set<Integer> uids) {
        final List<NetworkRequest> requests = new ArrayList<>();
        // The NRI should be comprised of two layers:
        // - The request for the mobile network preferred.
        // - The request for the default network, for fallback.
        requests.add(createDefaultInternetRequestForTransport(
                TRANSPORT_CELLULAR, NetworkRequest.Type.REQUEST));
        requests.add(createDefaultInternetRequestForTransport(
                TYPE_NONE, NetworkRequest.Type.TRACK_DEFAULT));
        return createNrisForPreferenceOrder(uids, requests, PREFERENCE_ORDER_MOBILE_DATA_PREFERERRED
        );
    }

    ArraySet<NetworkRequestInfo> createMultiLayerNrisFromSatelliteNetworkFallbackUids(
            @NonNull final Set<Integer> uids) {
        final List<NetworkRequest> requests = new ArrayList<>();

        // request: track default(unrestricted internet network)
        requests.add(createDefaultInternetRequestForTransport(
                TYPE_NONE, NetworkRequest.Type.TRACK_DEFAULT));

        // request: restricted Satellite internet
        final NetworkCapabilities cap = new NetworkCapabilities.Builder()
                .addCapability(NET_CAPABILITY_INTERNET)
                .addCapability(NET_CAPABILITY_NOT_VCN_MANAGED)
                .removeCapability(NET_CAPABILITY_NOT_RESTRICTED)
                .addTransportType(NetworkCapabilities.TRANSPORT_SATELLITE)
                .build();
        requests.add(createNetworkRequest(NetworkRequest.Type.REQUEST, cap));

        return createNrisForPreferenceOrder(uids, requests, PREFERENCE_ORDER_SATELLITE_FALLBACK);
    }

    private void handleMobileDataPreferredUidsChanged() {
        mMobileDataPreferredUids = ConnectivitySettingsManager.getMobileDataPreferredUids(mContext);
        removeDefaultNetworkRequestsForPreference(PREFERENCE_ORDER_MOBILE_DATA_PREFERERRED);
        addPerAppDefaultNetworkRequests(
                createNrisFromMobileDataPreferredUids(mMobileDataPreferredUids));
        // Finally, rematch.
        rematchAllNetworksAndRequests();
    }

    private void handleSetSatelliteNetworkPreference(
            @NonNull final Set<Integer> satelliteNetworkPreferredUids) {
        removeDefaultNetworkRequestsForPreference(PREFERENCE_ORDER_SATELLITE_FALLBACK);
        addPerAppDefaultNetworkRequests(
                createMultiLayerNrisFromSatelliteNetworkFallbackUids(satelliteNetworkPreferredUids)
        );
        // Finally, rematch.
        rematchAllNetworksAndRequests();
    }

    private void handleIngressRateLimitChanged() {
        final long oldIngressRateLimit = mIngressRateLimit;
        mIngressRateLimit = ConnectivitySettingsManager.getIngressRateLimitInBytesPerSecond(
                mContext);
        for (final NetworkAgentInfo networkAgent : mNetworkAgentInfos) {
            if (canNetworkBeRateLimited(networkAgent)) {
                // If rate limit has previously been enabled, remove the old limit first.
                if (oldIngressRateLimit >= 0) {
                    mDeps.disableIngressRateLimit(networkAgent.linkProperties.getInterfaceName());
                }
                if (mIngressRateLimit >= 0) {
                    mDeps.enableIngressRateLimit(networkAgent.linkProperties.getInterfaceName(),
                            mIngressRateLimit);
                }
            }
        }
    }

    private boolean canNetworkBeRateLimited(@NonNull final NetworkAgentInfo networkAgent) {
        // Rate-limiting cannot run correctly before T because the BPF program is not loaded.
        if (!mDeps.isAtLeastT()) return false;

        final NetworkCapabilities agentCaps = networkAgent.networkCapabilities;
        // Only test networks (they cannot hold NET_CAPABILITY_INTERNET) and networks that provide
        // internet connectivity can be rate limited.
        if (!agentCaps.hasCapability(NET_CAPABILITY_INTERNET) && !agentCaps.hasTransport(
                TRANSPORT_TEST)) {
            return false;
        }

        final String iface = networkAgent.linkProperties.getInterfaceName();
        if (iface == null) {
            // This may happen in tests, but if there is no interface then there is nothing that
            // can be rate limited.
            loge("canNetworkBeRateLimited: LinkProperties#getInterfaceName returns null");
            return false;
        }
        return true;
    }

    private void enforceAutomotiveDevice() {
        PermissionUtils.enforceSystemFeature(mContext, PackageManager.FEATURE_AUTOMOTIVE,
                "setOemNetworkPreference() is only available on automotive devices.");
    }

    /**
     * Used by automotive devices to set the network preferences used to direct traffic at an
     * application level as per the given OemNetworkPreferences. An example use-case would be an
     * automotive OEM wanting to provide connectivity for applications critical to the usage of a
     * vehicle via a particular network.
     *
     * Calling this will overwrite the existing preference.
     *
     * @param preference {@link OemNetworkPreferences} The application network preference to be set.
     * @param listener {@link ConnectivityManager.OnCompleteListener} Listener used
     * to communicate completion of setOemNetworkPreference();
     */
    @Override
    public void setOemNetworkPreference(
            @NonNull final OemNetworkPreferences preference,
            @Nullable final IOnCompleteListener listener) {

        Objects.requireNonNull(preference, "OemNetworkPreferences must be non-null");
        // Only bypass the permission/device checks if this is a valid test request.
        if (isValidTestOemNetworkPreference(preference)) {
            enforceManageTestNetworksPermission();
        } else {
            enforceAutomotiveDevice();
            enforceOemNetworkPreferencesPermission();
            validateOemNetworkPreferences(preference);
        }

        mHandler.sendMessage(mHandler.obtainMessage(EVENT_SET_OEM_NETWORK_PREFERENCE,
                new Pair<>(preference, listener)));
    }

    /**
     * Sets the specified UIDs to get/receive the VPN as the only default network.
     *
     * Calling this will overwrite the existing network preference for this session, and the
     * specified UIDs won't get any default network when no VPN is connected.
     *
     * @param session The VPN session which manages the passed UIDs.
     * @param ranges The uid ranges which will treat VPN as the only preferred network. Clear the
     *               setting for this session if the array is empty. Null is not allowed, the
     *               method will use {@link Objects#requireNonNull(Object)} to check this variable.
     * @hide
     */
    @Override
    public void setVpnNetworkPreference(String session, UidRange[] ranges) {
        Objects.requireNonNull(ranges);
        enforceNetworkStackOrSettingsPermission();
        final UidRange[] sortedRanges = UidRangeUtils.sortRangesByStartUid(ranges);
        if (UidRangeUtils.sortedRangesContainOverlap(sortedRanges)) {
            throw new IllegalArgumentException(
                    "setVpnNetworkPreference: Passed UID ranges overlap");
        }

        mHandler.sendMessage(mHandler.obtainMessage(EVENT_SET_VPN_NETWORK_PREFERENCE,
                new VpnNetworkPreferenceInfo(session,
                        new ArraySet<UidRange>(Arrays.asList(ranges)))));
    }

    private void handleSetVpnNetworkPreference(VpnNetworkPreferenceInfo preferenceInfo) {
        Log.d(TAG, "handleSetVpnNetworkPreference: preferenceInfo = " + preferenceInfo);

        mVpnNetworkPreferences = mVpnNetworkPreferences.minus(preferenceInfo.getKey());
        mVpnNetworkPreferences = mVpnNetworkPreferences.plus(preferenceInfo);

        removeDefaultNetworkRequestsForPreference(PREFERENCE_ORDER_VPN);
        addPerAppDefaultNetworkRequests(createNrisForVpnNetworkPreference(mVpnNetworkPreferences));
        // Finally, rematch.
        rematchAllNetworksAndRequests();
    }

    private ArraySet<NetworkRequestInfo> createNrisForVpnNetworkPreference(
            @NonNull NetworkPreferenceList<String, VpnNetworkPreferenceInfo> preferenceList) {
        final ArraySet<NetworkRequestInfo> nris = new ArraySet<>();
        for (VpnNetworkPreferenceInfo preferenceInfo : preferenceList) {
            final List<NetworkRequest> requests = new ArrayList<>();
            // Request VPN only, so other networks won't be the fallback options when VPN is not
            // connected temporarily.
            requests.add(createVpnRequest());
            final Set<UidRange> uidRanges = new ArraySet(preferenceInfo.getUidRangesNoCopy());
            setNetworkRequestUids(requests, uidRanges);
            nris.add(new NetworkRequestInfo(Process.myUid(), requests, PREFERENCE_ORDER_VPN));
        }
        return nris;
    }

    /**
     * Check the validity of an OEM network preference to be used for testing purposes.
     * @param preference the preference to validate
     * @return true if this is a valid OEM network preference test request.
     */
    private boolean isValidTestOemNetworkPreference(
            @NonNull final OemNetworkPreferences preference) {
        // Allow for clearing of an existing OemNetworkPreference used for testing.
        // This isn't called on the handler thread so it is possible that mOemNetworkPreferences
        // changes after this check is complete. This is an unlikely scenario as calling of this API
        // is controlled by the OEM therefore the added complexity is not worth adding given those
        // circumstances. That said, it is an edge case to be aware of hence this comment.
        final boolean isValidTestClearPref = preference.getNetworkPreferences().size() == 0
                && isTestOemNetworkPreference(mOemNetworkPreferences);
        return isTestOemNetworkPreference(preference) || isValidTestClearPref;
    }

    private boolean isTestOemNetworkPreference(@NonNull final OemNetworkPreferences preference) {
        final Map<String, Integer> prefMap = preference.getNetworkPreferences();
        return prefMap.size() == 1
                && (prefMap.containsValue(OEM_NETWORK_PREFERENCE_TEST)
                || prefMap.containsValue(OEM_NETWORK_PREFERENCE_TEST_ONLY));
    }

    private void validateOemNetworkPreferences(@NonNull OemNetworkPreferences preference) {
        for (@OemNetworkPreferences.OemNetworkPreference final int pref
                : preference.getNetworkPreferences().values()) {
            if (pref <= 0 || OemNetworkPreferences.OEM_NETWORK_PREFERENCE_MAX < pref) {
                throw new IllegalArgumentException(
                        OemNetworkPreferences.oemNetworkPreferenceToString(pref)
                                + " is an invalid value.");
            }
        }
    }

    private void handleSetOemNetworkPreference(
            @NonNull final OemNetworkPreferences preference,
            @Nullable final IOnCompleteListener listener) {
        Objects.requireNonNull(preference, "OemNetworkPreferences must be non-null");
        if (DBG) {
            log("set OEM network preferences :" + preference.toString());
        }

        mOemNetworkPreferencesLogs.log("UPDATE INITIATED: " + preference);
        removeDefaultNetworkRequestsForPreference(PREFERENCE_ORDER_OEM);
        addPerAppDefaultNetworkRequests(new OemNetworkRequestFactory()
                .createNrisFromOemNetworkPreferences(preference));
        mOemNetworkPreferences = preference;

        if (null != listener) {
            try {
                listener.onComplete();
            } catch (RemoteException e) {
                loge("Can't send onComplete in handleSetOemNetworkPreference", e);
            }
        }
    }

    private void removeDefaultNetworkRequestsForPreference(final int preferenceOrder) {
        // Skip the requests which are set by other network preference. Because the uid range rules
        // should stay in netd.
        final Set<NetworkRequestInfo> requests = new ArraySet<>(mDefaultNetworkRequests);
        requests.removeIf(request -> request.mPreferenceOrder != preferenceOrder);
        handleRemoveNetworkRequests(requests);
    }

    private void addPerAppDefaultNetworkRequests(@NonNull final Set<NetworkRequestInfo> nris) {
        ensureRunningOnConnectivityServiceThread();
        mDefaultNetworkRequests.addAll(nris);
        final ArraySet<NetworkRequestInfo> perAppCallbackRequestsToUpdate =
                getPerAppCallbackRequestsToUpdate();
        final ArraySet<NetworkRequestInfo> nrisToRegister = new ArraySet<>(nris);
        handleRemoveNetworkRequests(perAppCallbackRequestsToUpdate);
        nrisToRegister.addAll(
                createPerAppCallbackRequestsToRegister(perAppCallbackRequestsToUpdate));
        handleRegisterNetworkRequests(nrisToRegister);
    }

    /**
     * All current requests that are tracking the default network need to be assessed as to whether
     * or not the current set of per-application default requests will be changing their default
     * network. If so, those requests will need to be updated so that they will send callbacks for
     * default network changes at the appropriate time. Additionally, those requests tracking the
     * default that were previously updated by this flow will need to be reassessed.
     * @return the nris which will need to be updated.
     */
    private ArraySet<NetworkRequestInfo> getPerAppCallbackRequestsToUpdate() {
        final ArraySet<NetworkRequestInfo> defaultCallbackRequests = new ArraySet<>();
        // Get the distinct nris to check since for multilayer requests, it is possible to have the
        // same nri in the map's values for each of its NetworkRequest objects.
        final ArraySet<NetworkRequestInfo> nris = new ArraySet<>(mNetworkRequests.values());
        for (final NetworkRequestInfo nri : nris) {
            // Include this nri if it is currently being tracked.
            if (isPerAppTrackedNri(nri)) {
                defaultCallbackRequests.add(nri);
                continue;
            }
            // We only track callbacks for requests tracking the default.
            if (NetworkRequest.Type.TRACK_DEFAULT != nri.mRequests.get(0).type) {
                continue;
            }
            // Include this nri if it will be tracked by the new per-app default requests.
            final boolean isNriGoingToBeTracked =
                    getDefaultRequestTrackingUid(nri.mAsUid) != mDefaultRequest;
            if (isNriGoingToBeTracked) {
                defaultCallbackRequests.add(nri);
            }
        }
        return defaultCallbackRequests;
    }

    /**
     * Create nris for those network requests that are currently tracking the default network that
     * are being controlled by a per-application default.
     * @param perAppCallbackRequestsForUpdate the baseline network requests to be used as the
     * foundation when creating the nri. Important items include the calling uid's original
     * NetworkRequest to be used when mapping callbacks as well as the caller's uid and name. These
     * requests are assumed to have already been validated as needing to be updated.
     * @return the Set of nris to use when registering network requests.
     */
    private ArraySet<NetworkRequestInfo> createPerAppCallbackRequestsToRegister(
            @NonNull final ArraySet<NetworkRequestInfo> perAppCallbackRequestsForUpdate) {
        final ArraySet<NetworkRequestInfo> callbackRequestsToRegister = new ArraySet<>();
        for (final NetworkRequestInfo callbackRequest : perAppCallbackRequestsForUpdate) {
            final NetworkRequestInfo trackingNri =
                    getDefaultRequestTrackingUid(callbackRequest.mAsUid);

            // If this nri is not being tracked, then change it back to an untracked nri.
            if (trackingNri == mDefaultRequest) {
                callbackRequestsToRegister.add(new NetworkRequestInfo(
                        callbackRequest,
                        Collections.singletonList(callbackRequest.getNetworkRequestForCallback())));
                continue;
            }

            final NetworkRequest request = callbackRequest.mRequests.get(0);
            callbackRequestsToRegister.add(new NetworkRequestInfo(
                    callbackRequest,
                    copyNetworkRequestsForUid(
                            trackingNri.mRequests, callbackRequest.mAsUid,
                            callbackRequest.mUid, request.getRequestorPackageName())));
        }
        return callbackRequestsToRegister;
    }

    private static void setNetworkRequestUids(@NonNull final List<NetworkRequest> requests,
            @NonNull final Set<UidRange> uids) {
        for (final NetworkRequest req : requests) {
            req.networkCapabilities.setUids(UidRange.toIntRanges(uids));
        }
    }

    /**
     * Class used to generate {@link NetworkRequestInfo} based off of {@link OemNetworkPreferences}.
     */
    @VisibleForTesting
    final class OemNetworkRequestFactory {
        ArraySet<NetworkRequestInfo> createNrisFromOemNetworkPreferences(
                @NonNull final OemNetworkPreferences preference) {
            final ArraySet<NetworkRequestInfo> nris = new ArraySet<>();
            final SparseArray<Set<Integer>> uids =
                    createUidsFromOemNetworkPreferences(preference);
            for (int i = 0; i < uids.size(); i++) {
                final int key = uids.keyAt(i);
                final Set<Integer> value = uids.valueAt(i);
                final NetworkRequestInfo nri = createNriFromOemNetworkPreferences(key, value);
                // No need to add an nri without any requests.
                if (0 == nri.mRequests.size()) {
                    continue;
                }
                nris.add(nri);
            }

            return nris;
        }

        private SparseArray<Set<Integer>> createUidsFromOemNetworkPreferences(
                @NonNull final OemNetworkPreferences preference) {
            final SparseArray<Set<Integer>> prefToUids = new SparseArray<>();
            final PackageManager pm = mContext.getPackageManager();
            final List<UserHandle> users =
                    mContext.getSystemService(UserManager.class).getUserHandles(true);
            if (null == users || users.size() == 0) {
                if (VDBG || DDBG) {
                    log("No users currently available for setting the OEM network preference.");
                }
                return prefToUids;
            }
            for (final Map.Entry<String, Integer> entry :
                    preference.getNetworkPreferences().entrySet()) {
                @OemNetworkPreferences.OemNetworkPreference final int pref = entry.getValue();
                // Add the rules for all users as this policy is device wide.
                for (final UserHandle user : users) {
                    try {
                        final int uid = pm.getApplicationInfoAsUser(entry.getKey(), 0, user).uid;
                        if (!prefToUids.contains(pref)) {
                            prefToUids.put(pref, new ArraySet<>());
                        }
                        prefToUids.get(pref).add(uid);
                    } catch (PackageManager.NameNotFoundException e) {
                        // Although this may seem like an error scenario, it is ok that uninstalled
                        // packages are sent on a network preference as the system will watch for
                        // package installations associated with this network preference and update
                        // accordingly. This is done to minimize race conditions on app install.
                        continue;
                    }
                }
            }
            return prefToUids;
        }

        private NetworkRequestInfo createNriFromOemNetworkPreferences(
                @OemNetworkPreferences.OemNetworkPreference final int preference,
                @NonNull final Set<Integer> uids) {
            final List<NetworkRequest> requests = new ArrayList<>();
            // Requests will ultimately be evaluated by order of insertion therefore it matters.
            switch (preference) {
                case OemNetworkPreferences.OEM_NETWORK_PREFERENCE_OEM_PAID:
                    requests.add(createUnmeteredNetworkRequest());
                    requests.add(createOemPaidNetworkRequest());
                    requests.add(createDefaultInternetRequestForTransport(
                            TYPE_NONE, NetworkRequest.Type.TRACK_DEFAULT));
                    break;
                case OemNetworkPreferences.OEM_NETWORK_PREFERENCE_OEM_PAID_NO_FALLBACK:
                    requests.add(createUnmeteredNetworkRequest());
                    requests.add(createOemPaidNetworkRequest());
                    break;
                case OemNetworkPreferences.OEM_NETWORK_PREFERENCE_OEM_PAID_ONLY:
                    requests.add(createOemPaidNetworkRequest());
                    break;
                case OemNetworkPreferences.OEM_NETWORK_PREFERENCE_OEM_PRIVATE_ONLY:
                    requests.add(createOemPrivateNetworkRequest());
                    break;
                case OEM_NETWORK_PREFERENCE_TEST:
                    requests.add(createUnmeteredNetworkRequest());
                    requests.add(createTestNetworkRequest());
                    requests.add(createDefaultRequest());
                    break;
                case OEM_NETWORK_PREFERENCE_TEST_ONLY:
                    requests.add(createTestNetworkRequest());
                    break;
                default:
                    // This should never happen.
                    throw new IllegalArgumentException("createNriFromOemNetworkPreferences()"
                            + " called with invalid preference of " + preference);
            }

            final ArraySet<UidRange> ranges = new ArraySet<>();
            for (final int uid : uids) {
                ranges.add(new UidRange(uid, uid));
            }
            setNetworkRequestUids(requests, ranges);
            return new NetworkRequestInfo(Process.myUid(), requests, PREFERENCE_ORDER_OEM);
        }

        private NetworkRequest createUnmeteredNetworkRequest() {
            final NetworkCapabilities netcap = createDefaultPerAppNetCap()
                    .addCapability(NET_CAPABILITY_NOT_METERED)
                    .addCapability(NET_CAPABILITY_VALIDATED);
            return createNetworkRequest(NetworkRequest.Type.LISTEN, netcap);
        }

        private NetworkRequest createOemPaidNetworkRequest() {
            // NET_CAPABILITY_OEM_PAID is a restricted capability.
            final NetworkCapabilities netcap = createDefaultPerAppNetCap()
                    .addCapability(NET_CAPABILITY_OEM_PAID)
                    .removeCapability(NET_CAPABILITY_NOT_RESTRICTED);
            return createNetworkRequest(NetworkRequest.Type.REQUEST, netcap);
        }

        private NetworkRequest createOemPrivateNetworkRequest() {
            // NET_CAPABILITY_OEM_PRIVATE is a restricted capability.
            final NetworkCapabilities netcap = createDefaultPerAppNetCap()
                    .addCapability(NET_CAPABILITY_OEM_PRIVATE)
                    .removeCapability(NET_CAPABILITY_NOT_RESTRICTED);
            return createNetworkRequest(NetworkRequest.Type.REQUEST, netcap);
        }

        private NetworkCapabilities createDefaultPerAppNetCap() {
            final NetworkCapabilities netcap = new NetworkCapabilities();
            netcap.addCapability(NET_CAPABILITY_INTERNET);
            netcap.setRequestorUidAndPackageName(Process.myUid(), mContext.getPackageName());
            return netcap;
        }

        private NetworkRequest createTestNetworkRequest() {
            final NetworkCapabilities netcap = new NetworkCapabilities();
            netcap.clearAll();
            netcap.addTransportType(TRANSPORT_TEST);
            return createNetworkRequest(NetworkRequest.Type.REQUEST, netcap);
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    @Override
    public void setDataSaverEnabled(final boolean enable) {
        enforceNetworkStackOrSettingsPermission();
        try {
            final boolean ret = mNetd.bandwidthEnableDataSaver(enable);
            if (!ret) {
                throw new IllegalStateException("Error when changing iptables: " + enable);
            }
        } catch (RemoteException e) {
            // Lack of permission or binder errors.
            throw new IllegalStateException(e);
        }

        try {
            mBpfNetMaps.setDataSaverEnabled(enable);
        } catch (ServiceSpecificException | UnsupportedOperationException e) {
            Log.e(TAG, "Failed to set data saver " + enable + " : " + e);
        }
    }

    @Override
    public void updateMeteredNetworkAllowList(final int uid, final boolean add) {
        enforceNetworkStackOrSettingsPermission();

        try {
            if (add) {
                mBpfNetMaps.addNiceApp(uid);
            } else {
                mBpfNetMaps.removeNiceApp(uid);
            }
        } catch (ServiceSpecificException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void updateMeteredNetworkDenyList(final int uid, final boolean add) {
        enforceNetworkStackOrSettingsPermission();

        try {
            if (add) {
                mBpfNetMaps.addNaughtyApp(uid);
            } else {
                mBpfNetMaps.removeNaughtyApp(uid);
            }
        } catch (ServiceSpecificException e) {
            throw new IllegalStateException(e);
        }
    }

    private int setPackageFirewallRule(final int chain, final String packageName, final int rule)
            throws PackageManager.NameNotFoundException {
        final PackageManager pm = mContext.getPackageManager();
        final int appId = UserHandle.getAppId(pm.getPackageUid(packageName, 0 /* flags */));
        if (appId < Process.FIRST_APPLICATION_UID) {
            throw new RuntimeException("Can't set package firewall rule for system app "
                    + packageName + " with appId " + appId);
        }
        for (final UserHandle uh : mUserManager.getUserHandles(false /* excludeDying */)) {
            final int uid = uh.getUid(appId);
            setUidFirewallRule(chain, uid, rule);
        }
        return appId;
    }

    @Override
    public void setUidFirewallRule(final int chain, final int uid, final int rule) {
        enforceNetworkStackOrSettingsPermission();

        // There are only two type of firewall rule: FIREWALL_RULE_ALLOW or FIREWALL_RULE_DENY
        int firewallRule = getFirewallRuleType(chain, rule);

        if (firewallRule != FIREWALL_RULE_ALLOW && firewallRule != FIREWALL_RULE_DENY) {
            throw new IllegalArgumentException("setUidFirewallRule with invalid rule: " + rule);
        }

        try {
            mBpfNetMaps.setUidRule(chain, uid, firewallRule);
        } catch (ServiceSpecificException e) {
            throw new IllegalStateException(e);
        }
    }

    private int getPackageFirewallRule(final int chain, final String packageName)
            throws PackageManager.NameNotFoundException {
        final PackageManager pm = mContext.getPackageManager();
        final int appId = UserHandle.getAppId(pm.getPackageUid(packageName, 0 /* flags */));
        return getUidFirewallRule(chain, appId);
    }

    @Override
    public int getUidFirewallRule(final int chain, final int uid) {
        enforceNetworkStackOrSettingsPermission();
        return mBpfNetMaps.getUidRule(chain, uid);
    }

    private int getFirewallRuleType(int chain, int rule) {
        final int defaultRule;
        switch (chain) {
            case ConnectivityManager.FIREWALL_CHAIN_STANDBY:
            case ConnectivityManager.FIREWALL_CHAIN_OEM_DENY_1:
            case ConnectivityManager.FIREWALL_CHAIN_OEM_DENY_2:
            case ConnectivityManager.FIREWALL_CHAIN_OEM_DENY_3:
                defaultRule = FIREWALL_RULE_ALLOW;
                break;
            case ConnectivityManager.FIREWALL_CHAIN_DOZABLE:
            case ConnectivityManager.FIREWALL_CHAIN_POWERSAVE:
            case ConnectivityManager.FIREWALL_CHAIN_RESTRICTED:
            case ConnectivityManager.FIREWALL_CHAIN_LOW_POWER_STANDBY:
            case ConnectivityManager.FIREWALL_CHAIN_BACKGROUND:
                defaultRule = FIREWALL_RULE_DENY;
                break;
            default:
                throw new IllegalArgumentException("Unsupported firewall chain: " + chain);
        }
        if (rule == FIREWALL_RULE_DEFAULT) rule = defaultRule;

        return rule;
    }

    private void closeSocketsForFirewallChainLocked(final int chain)
            throws ErrnoException, SocketException, InterruptedIOException {
        if (BpfNetMapsUtils.isFirewallAllowList(chain)) {
            // Allowlist means the firewall denies all by default, uids must be explicitly allowed
            // So, close all non-system socket owned by uids that are not explicitly allowed
            Set<Range<Integer>> ranges = new ArraySet<>();
            ranges.add(new Range<>(Process.FIRST_APPLICATION_UID, Integer.MAX_VALUE));
            final Set<Integer> exemptUids = mBpfNetMaps.getUidsWithAllowRuleOnAllowListChain(chain);
            mDeps.destroyLiveTcpSockets(ranges, exemptUids);
        } else {
            // Denylist means the firewall allows all by default, uids must be explicitly denied
            // So, close socket owned by uids that are explicitly denied
            final Set<Integer> ownerUids = mBpfNetMaps.getUidsWithDenyRuleOnDenyListChain(chain);
            mDeps.destroyLiveTcpSocketsByOwnerUids(ownerUids);
        }
    }

    @Override
    public void setFirewallChainEnabled(final int chain, final boolean enable) {
        enforceNetworkStackOrSettingsPermission();

        try {
            mBpfNetMaps.setChildChain(chain, enable);
        } catch (ServiceSpecificException e) {
            throw new IllegalStateException(e);
        }

        if (mDeps.isAtLeastU() && enable) {
            try {
                closeSocketsForFirewallChainLocked(chain);
            } catch (ErrnoException | SocketException | InterruptedIOException e) {
                Log.e(TAG, "Failed to close sockets after enabling chain (" + chain + "): " + e);
            }
        }
    }

    @Override
    public boolean getFirewallChainEnabled(final int chain) {
        enforceNetworkStackOrSettingsPermission();

        return mBpfNetMaps.isChainEnabled(chain);
    }

    @Override
    public void replaceFirewallChain(final int chain, final int[] uids) {
        enforceNetworkStackOrSettingsPermission();

        mBpfNetMaps.replaceUidChain(chain, uids);
    }

    @Override
    public IBinder getCompanionDeviceManagerProxyService() {
        enforceNetworkStackPermission(mContext);
        return mCdmps;
    }

    @Override
    public IBinder getRoutingCoordinatorService() {
        enforceNetworkStackPermission(mContext);
        return mRoutingCoordinatorService;
    }
}
