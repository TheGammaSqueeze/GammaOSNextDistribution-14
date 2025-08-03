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
 * limitations under the License.
 */

package android.net.ip;

import static android.net.RouteInfo.RTN_UNICAST;
import static android.net.RouteInfo.RTN_UNREACHABLE;
import static android.net.dhcp.DhcpResultsParcelableUtil.toStableParcelable;
import static android.net.ip.IIpClient.PROV_IPV4_DISABLED;
import static android.net.ip.IIpClient.PROV_IPV6_DISABLED;
import static android.net.ip.IIpClient.PROV_IPV6_LINKLOCAL;
import static android.net.ip.IIpClient.PROV_IPV6_SLAAC;
import static android.net.ip.IIpClientCallbacks.DTIM_MULTIPLIER_RESET;
import static android.net.ip.IpReachabilityMonitor.INVALID_REACHABILITY_LOSS_TYPE;
import static android.net.ip.IpReachabilityMonitor.nudEventTypeToInt;
import static android.net.util.SocketUtils.makePacketSocketAddress;
import static android.provider.DeviceConfig.NAMESPACE_CONNECTIVITY;
import static android.system.OsConstants.AF_PACKET;
import static android.system.OsConstants.ETH_P_ARP;
import static android.system.OsConstants.ETH_P_IPV6;
import static android.system.OsConstants.IFA_F_NODAD;
import static android.system.OsConstants.RT_SCOPE_UNIVERSE;
import static android.system.OsConstants.SOCK_NONBLOCK;
import static android.system.OsConstants.SOCK_RAW;

import static com.android.net.module.util.LinkPropertiesUtils.CompareResult;
import static com.android.net.module.util.NetworkStackConstants.ARP_REPLY;
import static com.android.net.module.util.NetworkStackConstants.ETHER_BROADCAST;
import static com.android.net.module.util.NetworkStackConstants.IPV6_ADDR_ALL_ROUTERS_MULTICAST;
import static com.android.net.module.util.NetworkStackConstants.RFC7421_PREFIX_LENGTH;
import static com.android.net.module.util.NetworkStackConstants.VENDOR_SPECIFIC_IE_ID;
import static com.android.networkstack.apishim.ConstantsShim.IFA_F_MANAGETEMPADDR;
import static com.android.networkstack.apishim.ConstantsShim.IFA_F_NOPREFIXROUTE;
import static com.android.networkstack.util.NetworkStackUtils.APF_HANDLE_LIGHT_DOZE_FORCE_DISABLE;
import static com.android.networkstack.util.NetworkStackUtils.APF_NEW_RA_FILTER_VERSION;
import static com.android.networkstack.util.NetworkStackUtils.APF_POLLING_COUNTERS_VERSION;
import static com.android.networkstack.util.NetworkStackUtils.IPCLIENT_DHCPV6_PREFIX_DELEGATION_VERSION;
import static com.android.networkstack.util.NetworkStackUtils.IPCLIENT_GARP_NA_ROAMING_VERSION;
import static com.android.networkstack.util.NetworkStackUtils.IPCLIENT_GRATUITOUS_NA_VERSION;
import static com.android.networkstack.util.NetworkStackUtils.IPCLIENT_IGNORE_LOW_RA_LIFETIME_VERSION;
import static com.android.networkstack.util.NetworkStackUtils.IPCLIENT_POPULATE_LINK_ADDRESS_LIFETIME_VERSION;
import static com.android.networkstack.util.NetworkStackUtils.createInet6AddressFromEui64;
import static com.android.networkstack.util.NetworkStackUtils.macAddressToEui64;
import static com.android.server.util.PermissionUtil.enforceNetworkStackCallingPermission;

import android.annotation.SuppressLint;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.ConnectivityManager;
import android.net.DhcpResults;
import android.net.INetd;
import android.net.IpPrefix;
import android.net.Layer2InformationParcelable;
import android.net.Layer2PacketParcelable;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.MacAddress;
import android.net.NattKeepalivePacketDataParcelable;
import android.net.NetworkStackIpMemoryStore;
import android.net.ProvisioningConfigurationParcelable;
import android.net.ProxyInfo;
import android.net.RouteInfo;
import android.net.TcpKeepalivePacketDataParcelable;
import android.net.Uri;
import android.net.apf.AndroidPacketFilter;
import android.net.apf.ApfCapabilities;
import android.net.apf.ApfFilter;
import android.net.apf.LegacyApfFilter;
import android.net.dhcp.DhcpClient;
import android.net.dhcp.DhcpPacket;
import android.net.dhcp6.Dhcp6Client;
import android.net.metrics.IpConnectivityLog;
import android.net.metrics.IpManagerEvent;
import android.net.networkstack.aidl.dhcp.DhcpOption;
import android.net.networkstack.aidl.ip.ReachabilityLossInfoParcelable;
import android.net.networkstack.aidl.ip.ReachabilityLossReason;
import android.net.shared.InitialConfiguration;
import android.net.shared.Layer2Information;
import android.net.shared.ProvisioningConfiguration;
import android.net.shared.ProvisioningConfiguration.ScanResultInfo;
import android.net.shared.ProvisioningConfiguration.ScanResultInfo.InformationElement;
import android.os.Build;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.os.SystemClock;
import android.os.UserHandle;
import android.stats.connectivity.DisconnectCode;
import android.stats.connectivity.NetworkQuirkEvent;
import android.stats.connectivity.NudEventType;
import android.system.ErrnoException;
import android.system.Os;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.LocalLog;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.HexDump;
import com.android.internal.util.IState;
import com.android.internal.util.IndentingPrintWriter;
import com.android.internal.util.MessageUtils;
import com.android.internal.util.State;
import com.android.internal.util.StateMachine;
import com.android.internal.util.WakeupMessage;
import com.android.modules.utils.build.SdkLevel;
import com.android.net.module.util.CollectionUtils;
import com.android.net.module.util.ConnectivityUtils;
import com.android.net.module.util.DeviceConfigUtils;
import com.android.net.module.util.InterfaceParams;
import com.android.net.module.util.LinkPropertiesUtils;
import com.android.net.module.util.SharedLog;
import com.android.net.module.util.SocketUtils;
import com.android.net.module.util.arp.ArpPacket;
import com.android.net.module.util.ip.InterfaceController;
import com.android.net.module.util.netlink.NetlinkUtils;
import com.android.net.module.util.structs.IaPrefixOption;
import com.android.networkstack.R;
import com.android.networkstack.apishim.NetworkInformationShimImpl;
import com.android.networkstack.apishim.SocketUtilsShimImpl;
import com.android.networkstack.apishim.common.NetworkInformationShim;
import com.android.networkstack.apishim.common.ShimUtils;
import com.android.networkstack.metrics.IpProvisioningMetrics;
import com.android.networkstack.metrics.NetworkQuirkMetrics;
import com.android.networkstack.packets.NeighborAdvertisement;
import com.android.networkstack.packets.NeighborSolicitation;
import com.android.networkstack.util.NetworkStackUtils;
import com.android.server.NetworkObserverRegistry;
import com.android.server.NetworkStackService.NetworkStackServiceManager;

import java.io.File;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.URL;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * IpClient
 *
 * This class provides the interface to IP-layer provisioning and maintenance
 * functionality that can be used by transport layers like Wi-Fi, Ethernet,
 * et cetera.
 *
 * [ Lifetime ]
 * IpClient is designed to be instantiated as soon as the interface name is
 * known and can be as long-lived as the class containing it (i.e. declaring
 * it "private final" is okay).
 *
 * @hide
 */
public class IpClient extends StateMachine {
    private static final String TAG = IpClient.class.getSimpleName();
    private static final boolean DBG = false;

    // For message logging.
    private static final Class[] sMessageClasses = { IpClient.class, DhcpClient.class };
    private static final SparseArray<String> sWhatToString =
            MessageUtils.findMessageNames(sMessageClasses);
    // Two static concurrent hashmaps of interface name to logging classes.
    // One holds StateMachine logs and the other connectivity packet logs.
    private static final ConcurrentHashMap<String, SharedLog> sSmLogs = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, LocalLog> sPktLogs = new ConcurrentHashMap<>();
    private final NetworkStackIpMemoryStore mIpMemoryStore;
    private final NetworkInformationShim mShim = NetworkInformationShimImpl.newInstance();
    private final IpProvisioningMetrics mIpProvisioningMetrics = new IpProvisioningMetrics();
    private final NetworkQuirkMetrics mNetworkQuirkMetrics;

    /**
     * Dump all state machine and connectivity packet logs to the specified writer.
     * @param skippedIfaces Interfaces for which logs should not be dumped.
     */
    public static void dumpAllLogs(PrintWriter writer, Set<String> skippedIfaces) {
        for (String ifname : sSmLogs.keySet()) {
            if (skippedIfaces.contains(ifname)) continue;

            writer.println(String.format("--- BEGIN %s ---", ifname));

            final SharedLog smLog = sSmLogs.get(ifname);
            if (smLog != null) {
                writer.println("State machine log:");
                smLog.dump(null, writer, null);
            }

            writer.println("");

            final LocalLog pktLog = sPktLogs.get(ifname);
            if (pktLog != null) {
                writer.println("Connectivity packet log:");
                pktLog.readOnlyLocalLog().dump(null, writer, null);
            }

            writer.println(String.format("--- END %s ---", ifname));
        }
    }

    // Use a wrapper class to log in order to ensure complete and detailed
    // logging. This method is lighter weight than annotations/reflection
    // and has the following benefits:
    //
    //     - No invoked method can be forgotten.
    //       Any new method added to IpClient.Callback must be overridden
    //       here or it will never be called.
    //
    //     - No invoking call site can be forgotten.
    //       Centralized logging in this way means call sites don't need to
    //       remember to log, and therefore no call site can be forgotten.
    //
    //     - No variation in log format among call sites.
    //       Encourages logging of any available arguments, and all call sites
    //       are necessarily logged identically.
    //
    // NOTE: Log first because passed objects may or may not be thread-safe and
    // once passed on to the callback they may be modified by another thread.
    //
    // TODO: Find an lighter weight approach.
    public static class IpClientCallbacksWrapper {
        private static final String PREFIX = "INVOKE ";
        private final IIpClientCallbacks mCallback;
        private final SharedLog mLog;
        @NonNull
        private final NetworkInformationShim mShim;

        @VisibleForTesting
        protected IpClientCallbacksWrapper(IIpClientCallbacks callback, SharedLog log,
                @NonNull NetworkInformationShim shim) {
            mCallback = callback;
            mLog = log;
            mShim = shim;
        }

        private void log(String msg) {
            mLog.log(PREFIX + msg);
        }

        private void log(String msg, Throwable e) {
            mLog.e(PREFIX + msg, e);
        }

        /**
         * Callback called prior to DHCP discovery/renewal only if the pre DHCP action
         * is enabled.
         */
        public void onPreDhcpAction() {
            log("onPreDhcpAction()");
            try {
                mCallback.onPreDhcpAction();
            } catch (RemoteException e) {
                log("Failed to call onPreDhcpAction", e);
            }
        }

        /**
         * Callback called after DHCP discovery/renewal only if the pre DHCP action
         * is enabled.
         */
        public void onPostDhcpAction() {
            log("onPostDhcpAction()");
            try {
                mCallback.onPostDhcpAction();
            } catch (RemoteException e) {
                log("Failed to call onPostDhcpAction", e);
            }
        }

        /**
         * Callback called when new DHCP results are available.
         */
        public void onNewDhcpResults(DhcpResults dhcpResults) {
            log("onNewDhcpResults({" + dhcpResults + "})");
            try {
                mCallback.onNewDhcpResults(toStableParcelable(dhcpResults));
            } catch (RemoteException e) {
                log("Failed to call onNewDhcpResults", e);
            }
        }

        /**
         * Indicates that provisioning was successful.
         */
        public void onProvisioningSuccess(LinkProperties newLp) {
            log("onProvisioningSuccess({" + newLp + "})");
            try {
                mCallback.onProvisioningSuccess(mShim.makeSensitiveFieldsParcelingCopy(newLp));
            } catch (RemoteException e) {
                log("Failed to call onProvisioningSuccess", e);
            }
        }

        /**
         * Indicates that provisioning failed.
         */
        public void onProvisioningFailure(LinkProperties newLp) {
            log("onProvisioningFailure({" + newLp + "})");
            try {
                mCallback.onProvisioningFailure(mShim.makeSensitiveFieldsParcelingCopy(newLp));
            } catch (RemoteException e) {
                log("Failed to call onProvisioningFailure", e);
            }
        }

        /**
         * Invoked on LinkProperties changes.
         */
        public void onLinkPropertiesChange(LinkProperties newLp) {
            log("onLinkPropertiesChange({" + newLp + "})");
            try {
                mCallback.onLinkPropertiesChange(mShim.makeSensitiveFieldsParcelingCopy(newLp));
            } catch (RemoteException e) {
                log("Failed to call onLinkPropertiesChange", e);
            }
        }

        /**
         * Called when the internal IpReachabilityMonitor (if enabled) has detected the loss of
         * required neighbors (e.g. on-link default gw or dns servers) due to NUD_FAILED.
         *
         * Note this method is only supported on networkstack-aidl-interfaces-v12 or below.
         * For above aidl versions, the caller should call {@link onReachabilityFailure} instead.
         * For callbacks extending IpClientCallbacks, this method will be called iff the callback
         * does not implement onReachabilityFailure.
         */
        public void onReachabilityLost(String logMsg) {
            log("onReachabilityLost(" + logMsg + ")");
            try {
                mCallback.onReachabilityLost(logMsg);
            } catch (RemoteException e) {
                log("Failed to call onReachabilityLost", e);
            }
        }

        /**
         * Called when the IpClient state machine terminates.
         */
        public void onQuit() {
            log("onQuit()");
            try {
                mCallback.onQuit();
            } catch (RemoteException e) {
                log("Failed to call onQuit", e);
            }
        }

        /**
         * Called to indicate that a new APF program must be installed to filter incoming packets.
         */
        public boolean installPacketFilter(byte[] filter) {
            log("installPacketFilter(byte[" + filter.length + "])");
            try {
                mCallback.installPacketFilter(filter);
            } catch (RemoteException e) {
                log("Failed to call installPacketFilter", e);
                return false;
            }
            return true;
        }

        /**
         * Called to indicate that the APF Program & data buffer must be read asynchronously from
         * the wifi driver.
         */
        public void startReadPacketFilter() {
            log("startReadPacketFilter()");
            try {
                mCallback.startReadPacketFilter();
            } catch (RemoteException e) {
                log("Failed to call startReadPacketFilter", e);
            }
        }

        /**
         * If multicast filtering cannot be accomplished with APF, this function will be called to
         * actuate multicast filtering using another means.
         */
        public void setFallbackMulticastFilter(boolean enabled) {
            log("setFallbackMulticastFilter(" + enabled + ")");
            try {
                mCallback.setFallbackMulticastFilter(enabled);
            } catch (RemoteException e) {
                log("Failed to call setFallbackMulticastFilter", e);
            }
        }

        /**
         * Enabled/disable Neighbor Discover offload functionality. This is called, for example,
         * whenever 464xlat is being started or stopped.
         */
        public void setNeighborDiscoveryOffload(boolean enable) {
            log("setNeighborDiscoveryOffload(" + enable + ")");
            try {
                mCallback.setNeighborDiscoveryOffload(enable);
            } catch (RemoteException e) {
                log("Failed to call setNeighborDiscoveryOffload", e);
            }
        }

        /**
         * Invoked on starting preconnection process.
         */
        public void onPreconnectionStart(List<Layer2PacketParcelable> packets) {
            log("onPreconnectionStart(Layer2Packets[" + packets.size() + "])");
            try {
                mCallback.onPreconnectionStart(packets);
            } catch (RemoteException e) {
                log("Failed to call onPreconnectionStart", e);
            }
        }

        /**
         * Called when Neighbor Unreachability Detection fails, that might be caused by the organic
         * probe or probeAll from IpReachabilityMonitor (if enabled).
         */
        public void onReachabilityFailure(ReachabilityLossInfoParcelable lossInfo) {
            log("onReachabilityFailure(" + lossInfo.message + ", loss reason: "
                    + reachabilityLossReasonToString(lossInfo.reason) + ")");
            try {
                mCallback.onReachabilityFailure(lossInfo);
            } catch (RemoteException e) {
                log("Failed to call onReachabilityFailure", e);
            }
        }

        /**
         * Set maximum acceptable DTIM multiplier to hardware driver.
         */
        public void setMaxDtimMultiplier(int multiplier) {
            try {
                // {@link IWifiStaIface#setDtimMultiplier} has been implemented since U, calling
                // this method on U- platforms does nothing actually.
                if (!SdkLevel.isAtLeastU()) {
                    log("SDK level is lower than U, do not call setMaxDtimMultiplier method");
                    return;
                }
                log("setMaxDtimMultiplier(" + multiplier + ")");
                mCallback.setMaxDtimMultiplier(multiplier);
            } catch (RemoteException e) {
                log("Failed to call setMaxDtimMultiplier", e);
            }
        }

        /**
         * Get the version of the IIpClientCallbacks AIDL interface.
         */
        public int getInterfaceVersion() {
            log("getInterfaceVersion");
            try {
                return mCallback.getInterfaceVersion();
            } catch (RemoteException e) {
                // This can never happen for callers in the system server, because if the
                // system server crashes, then the networkstack will crash as well. But it can
                // happen for other callers such as bluetooth or telephony (if it starts to use
                // IpClient). 0 will generally work but will assume an old client and disable
                // all new features.
                log("Failed to call getInterfaceVersion", e);
                return 0;
            }
        }
    }

    public static final String DUMP_ARG_CONFIRM = "confirm";

    // Sysctl parameter strings.
    private static final String ACCEPT_RA = "accept_ra";
    private static final String ACCEPT_RA_DEFRTR = "accept_ra_defrtr";
    @VisibleForTesting
    static final String ACCEPT_RA_MIN_LFT = "accept_ra_min_lft";
    private static final String DAD_TRANSMITS = "dad_transmits";

    // Below constants are picked up by MessageUtils and exempt from ProGuard optimization.
    private static final int CMD_TERMINATE_AFTER_STOP             = 1;
    private static final int CMD_STOP                             = 2;
    private static final int CMD_START                            = 3;
    private static final int CMD_CONFIRM                          = 4;
    private static final int EVENT_PRE_DHCP_ACTION_COMPLETE       = 5;
    // Triggered by IpClientLinkObserver to communicate netlink events.
    private static final int EVENT_NETLINK_LINKPROPERTIES_CHANGED = 6;
    private static final int CMD_UPDATE_TCP_BUFFER_SIZES          = 7;
    private static final int CMD_UPDATE_HTTP_PROXY                = 8;
    private static final int CMD_SET_MULTICAST_FILTER             = 9;
    private static final int EVENT_PROVISIONING_TIMEOUT           = 10;
    private static final int EVENT_DHCPACTION_TIMEOUT             = 11;
    private static final int EVENT_READ_PACKET_FILTER_COMPLETE    = 12;
    private static final int CMD_ADD_KEEPALIVE_PACKET_FILTER_TO_APF = 13;
    private static final int CMD_REMOVE_KEEPALIVE_PACKET_FILTER_FROM_APF = 14;
    private static final int CMD_UPDATE_L2KEY_CLUSTER = 15;
    private static final int CMD_COMPLETE_PRECONNECTION = 16;
    private static final int CMD_UPDATE_L2INFORMATION = 17;
    private static final int CMD_SET_DTIM_MULTIPLIER_AFTER_DELAY = 18;
    private static final int CMD_UPDATE_APF_CAPABILITIES = 19;
    private static final int EVENT_IPV6_AUTOCONF_TIMEOUT = 20;
    private static final int CMD_UPDATE_APF_DATA_SNAPSHOT = 21;

    private static final int ARG_LINKPROP_CHANGED_LINKSTATE_DOWN = 0;
    private static final int ARG_LINKPROP_CHANGED_LINKSTATE_UP = 1;

    // Internal commands to use instead of trying to call transitionTo() inside
    // a given State's enter() method. Calling transitionTo() from enter/exit
    // encounters a Log.wtf() that can cause trouble on eng builds.
    private static final int CMD_ADDRESSES_CLEARED                = 100;
    private static final int CMD_JUMP_RUNNING_TO_STOPPING         = 101;
    private static final int CMD_JUMP_STOPPING_TO_STOPPED         = 102;

    // IpClient shares a handler with DhcpClient: commands must not overlap
    public static final int DHCPCLIENT_CMD_BASE = 1000;

    // IpClient shares a handler with Dhcp6Client: commands must not overlap
    public static final int DHCP6CLIENT_CMD_BASE = 2000;
    private static final int DHCPV6_PREFIX_DELEGATION_ADDRESS_FLAGS =
            IFA_F_MANAGETEMPADDR | IFA_F_NOPREFIXROUTE | IFA_F_NODAD;

    // Settings and default values.
    private static final int MAX_LOG_RECORDS = 500;
    private static final int MAX_PACKET_RECORDS = 100;

    @VisibleForTesting
    static final String CONFIG_MIN_RDNSS_LIFETIME = "ipclient_min_rdnss_lifetime";
    private static final int DEFAULT_MIN_RDNSS_LIFETIME =
            ShimUtils.isReleaseOrDevelopmentApiAbove(Build.VERSION_CODES.Q) ? 120 : 0;

    @VisibleForTesting
    static final String CONFIG_ACCEPT_RA_MIN_LFT = "ipclient_accept_ra_min_lft";
    @VisibleForTesting
    static final int DEFAULT_ACCEPT_RA_MIN_LFT = 180;

    @VisibleForTesting
    static final String CONFIG_APF_COUNTER_POLLING_INTERVAL_SECS =
            "ipclient_apf_counter_polling_interval_secs";
    @VisibleForTesting
    static final int DEFAULT_APF_COUNTER_POLLING_INTERVAL_SECS = 300;

    // Used to wait for the provisioning to complete eventually and then decide the target
    // network type, which gives the accurate hint to set DTIM multiplier. Per current IPv6
    // provisioning connection latency metrics, the latency of 95% can go up to 16s, so pick
    // ProvisioningConfiguration.DEFAULT_TIMEOUT_MS value for this delay.
    @VisibleForTesting
    static final String CONFIG_INITIAL_PROVISIONING_DTIM_DELAY_MS =
            "ipclient_initial_provisioning_dtim_delay";
    private static final int DEFAULT_INITIAL_PROVISIONING_DTIM_DELAY_MS = 18000;

    @VisibleForTesting
    static final String CONFIG_MULTICAST_LOCK_MAX_DTIM_MULTIPLIER =
            "ipclient_multicast_lock_max_dtim_multiplier";
    @VisibleForTesting
    static final int DEFAULT_MULTICAST_LOCK_MAX_DTIM_MULTIPLIER = 1;

    @VisibleForTesting
    static final String CONFIG_IPV6_ONLY_NETWORK_MAX_DTIM_MULTIPLIER =
            "ipclient_ipv6_only_max_dtim_multiplier";
    @VisibleForTesting
    static final int DEFAULT_IPV6_ONLY_NETWORK_MAX_DTIM_MULTIPLIER = 2;

    @VisibleForTesting
    static final String CONFIG_IPV4_ONLY_NETWORK_MAX_DTIM_MULTIPLIER =
            "ipclient_ipv4_only_max_dtim_multiplier";
    @VisibleForTesting
    static final int DEFAULT_IPV4_ONLY_NETWORK_MAX_DTIM_MULTIPLIER = 9;

    @VisibleForTesting
    static final String CONFIG_DUAL_STACK_MAX_DTIM_MULTIPLIER =
            "ipclient_dual_stack_max_dtim_multiplier";
    // The default value for dual-stack networks is the min of maximum DTIM multiplier to use for
    // IPv4-only and IPv6-only networks.
    @VisibleForTesting
    static final int DEFAULT_DUAL_STACK_MAX_DTIM_MULTIPLIER = 2;

    @VisibleForTesting
    static final String CONFIG_BEFORE_IPV6_PROV_MAX_DTIM_MULTIPLIER =
            "ipclient_before_ipv6_prov_max_dtim_multiplier";
    @VisibleForTesting
    static final int DEFAULT_BEFORE_IPV6_PROV_MAX_DTIM_MULTIPLIER = 1;

    // Timeout to wait for IPv6 autoconf via SLAAC to complete before starting DHCPv6
    // prefix delegation.
    @VisibleForTesting
    static final String CONFIG_IPV6_AUTOCONF_TIMEOUT = "ipclient_ipv6_autoconf_timeout";
    private static final int DEFAULT_IPV6_AUTOCONF_TIMEOUT_MS = 5000;

    private static final boolean NO_CALLBACKS = false;
    private static final boolean SEND_CALLBACKS = true;

    private static final int IMMEDIATE_FAILURE_DURATION = 0;

    private static final int PROV_CHANGE_STILL_NOT_PROVISIONED = 1;
    private static final int PROV_CHANGE_LOST_PROVISIONING = 2;
    private static final int PROV_CHANGE_GAINED_PROVISIONING = 3;
    private static final int PROV_CHANGE_STILL_PROVISIONED = 4;

    // onReachabilityFailure callback is added since networkstack-aidl-interfaces-v13.
    @VisibleForTesting
    static final int VERSION_ADDED_REACHABILITY_FAILURE = 13;

    // Specific vendor OUI(3 bytes)/vendor specific type(1 byte) pattern for upstream hotspot
    // device detection. Add new byte array pattern below in turn.
    private static final List<byte[]> METERED_IE_PATTERN_LIST = Collections.singletonList(
            new byte[] { (byte) 0x00, (byte) 0x17, (byte) 0xf2, (byte) 0x06 }
    );

    // Allows Wi-Fi to pass in DHCP options when particular vendor-specific IEs are present.
    // Maps each DHCP option code to a list of IEs, any of which will allow that option.
    private static final Map<Byte, List<byte[]>> DHCP_OPTIONS_ALLOWED = Map.of(
            (byte) 60, Collections.singletonList(
                    // KT OUI: 00:17:C3, type: 33(0x21). See b/236745261.
                    new byte[]{ (byte) 0x00, (byte) 0x17, (byte) 0xc3, (byte) 0x21 }),
            (byte) 77, Collections.singletonList(
                    // KT OUI: 00:17:C3, type: 33(0x21). See b/236745261.
                    new byte[]{ (byte) 0x00, (byte) 0x17, (byte) 0xc3, (byte) 0x21 })
    );

    // Initialize configurable particular SSID set supporting DHCP Roaming feature. See
    // b/131797393 for more details.
    private static final Set<String> DHCP_ROAMING_SSID_SET = new HashSet<>(
            Arrays.asList(
                    "0001docomo", "ollehWiFi", "olleh GiGa WiFi", "KT WiFi",
                    "KT GiGA WiFi", "marente"
    ));

    private final State mStoppedState = new StoppedState();
    private final State mStoppingState = new StoppingState();
    private final State mClearingIpAddressesState = new ClearingIpAddressesState();
    private final State mStartedState = new StartedState();
    private final State mRunningState = new RunningState();
    private final State mPreconnectingState = new PreconnectingState();

    private final String mTag;
    private final Context mContext;
    private final String mInterfaceName;
    @VisibleForTesting
    protected final IpClientCallbacksWrapper mCallback;
    private final Dependencies mDependencies;
    private final CountDownLatch mShutdownLatch;
    private final ConnectivityManager mCm;
    private final INetd mNetd;
    private final NetworkObserverRegistry mObserverRegistry;
    private final IpClientLinkObserver mLinkObserver;
    private final WakeupMessage mProvisioningTimeoutAlarm;
    private final WakeupMessage mDhcpActionTimeoutAlarm;
    private final SharedLog mLog;
    private final LocalLog mConnectivityPacketLog;
    private final MessageHandlingLogger mMsgStateLogger;
    private final IpConnectivityLog mMetricsLog;
    private final InterfaceController mInterfaceCtrl;
    // Set of IPv6 addresses for which unsolicited gratuitous NA packets have been sent.
    private final Set<Inet6Address> mGratuitousNaTargetAddresses = new HashSet<>();
    // Set of IPv6 addresses from which multicast NS packets have been sent.
    private final Set<Inet6Address> mMulticastNsSourceAddresses = new HashSet<>();
    // Set of delegated prefixes.
    private final Set<IpPrefix> mDelegatedPrefixes = new HashSet<>();
    @Nullable
    private final DevicePolicyManager mDevicePolicyManager;

    // Ignore nonzero RDNSS option lifetimes below this value. 0 = disabled.
    private final int mMinRdnssLifetimeSec;

    // Ignore any nonzero RA section with lifetime below this value.
    private final int mAcceptRaMinLft;

    // Polling interval to update APF data snapshot
    private final long mApfCounterPollingIntervalMs;

    // Experiment flag read from device config.
    private final boolean mDhcp6PrefixDelegationEnabled;
    private final boolean mUseNewApfFilter;
    private final boolean mEnableIpClientIgnoreLowRaLifetime;
    private final boolean mApfShouldHandleLightDoze;
    private final boolean mEnableApfPollingCounters;
    private final boolean mPopulateLinkAddressLifetime;

    private InterfaceParams mInterfaceParams;

    /**
     * Non-final member variables accessed only from within our StateMachine.
     */
    private LinkProperties mLinkProperties;
    private android.net.shared.ProvisioningConfiguration mConfiguration;
    private IpReachabilityMonitor mIpReachabilityMonitor;
    private DhcpClient mDhcpClient;
    private Dhcp6Client mDhcp6Client;
    private DhcpResults mDhcpResults;
    private String mTcpBufferSizes;
    private ProxyInfo mHttpProxy;
    private AndroidPacketFilter mApfFilter;
    private String mL2Key; // The L2 key for this network, for writing into the memory store
    private String mCluster; // The cluster for this network, for writing into the memory store
    private int mCreatorUid; // Uid of app creating the wifi configuration
    private boolean mMulticastFiltering;
    private long mStartTimeMillis;
    private long mIPv6ProvisioningDtimGracePeriodMillis;
    private MacAddress mCurrentBssid;
    private boolean mHasDisabledAcceptRaDefrtrOnProvLoss;
    private Integer mDadTransmits = null;
    private int mMaxDtimMultiplier = DTIM_MULTIPLIER_RESET;
    private ApfCapabilities mCurrentApfCapabilities;
    private WakeupMessage mIpv6AutoconfTimeoutAlarm = null;

    /**
     * Reading the snapshot is an asynchronous operation initiated by invoking
     * Callback.startReadPacketFilter() and completed when the WiFi Service responds with an
     * EVENT_READ_PACKET_FILTER_COMPLETE message. The mApfDataSnapshotComplete condition variable
     * signals when a new snapshot is ready.
     */
    private final ConditionVariable mApfDataSnapshotComplete = new ConditionVariable();

    public static class Dependencies {
        /**
         * Get interface parameters for the specified interface.
         */
        public InterfaceParams getInterfaceParams(String ifname) {
            return InterfaceParams.getByName(ifname);
        }

        /**
         * Get a INetd connector.
         */
        public INetd getNetd(Context context) {
            return INetd.Stub.asInterface((IBinder) context.getSystemService(Context.NETD_SERVICE));
        }

        /**
         * Get a IpMemoryStore instance.
         */
        public NetworkStackIpMemoryStore getIpMemoryStore(Context context,
                NetworkStackServiceManager nssManager) {
            return new NetworkStackIpMemoryStore(context, nssManager.getIpMemoryStoreService());
        }

        /**
         * Get a DhcpClient instance.
         */
        public DhcpClient makeDhcpClient(Context context, StateMachine controller,
                InterfaceParams ifParams, DhcpClient.Dependencies deps) {
            return DhcpClient.makeDhcpClient(context, controller, ifParams, deps);
        }

        /**
         * Get a Dhcp6Client instance.
         */
        public Dhcp6Client makeDhcp6Client(Context context, StateMachine controller,
                InterfaceParams ifParams, Dhcp6Client.Dependencies deps) {
            return Dhcp6Client.makeDhcp6Client(context, controller, ifParams, deps);
        }

        /**
         * Get a DhcpClient Dependencies instance.
         */
        public DhcpClient.Dependencies getDhcpClientDependencies(
                NetworkStackIpMemoryStore ipMemoryStore, IpProvisioningMetrics metrics) {
            return new DhcpClient.Dependencies(ipMemoryStore, metrics);
        }

        /**
         * Get a Dhcp6Client Dependencies instance.
         */
        public Dhcp6Client.Dependencies getDhcp6ClientDependencies() {
            return new Dhcp6Client.Dependencies();
        }

        /**
         * Read an integer DeviceConfig property.
         */
        public int getDeviceConfigPropertyInt(String name, int defaultValue) {
            return DeviceConfigUtils.getDeviceConfigPropertyInt(NAMESPACE_CONNECTIVITY, name,
                    defaultValue);
        }

        /**
         * Get a IpConnectivityLog instance.
         */
        public IpConnectivityLog getIpConnectivityLog() {
            return new IpConnectivityLog();
        }

        /**
         * Get a NetworkQuirkMetrics instance.
         */
        public NetworkQuirkMetrics getNetworkQuirkMetrics() {
            return new NetworkQuirkMetrics();
        }

        /**
         * Get a IpReachabilityMonitor instance.
         */
        public IpReachabilityMonitor getIpReachabilityMonitor(Context context,
                InterfaceParams ifParams, Handler h, SharedLog log,
                IpReachabilityMonitor.Callback callback, boolean usingMultinetworkPolicyTracker,
                IpReachabilityMonitor.Dependencies deps, final INetd netd) {
            return new IpReachabilityMonitor(context, ifParams, h, log, callback,
                    usingMultinetworkPolicyTracker, deps, netd);
        }

        /**
         * Get a IpReachabilityMonitor dependencies instance.
         */
        public IpReachabilityMonitor.Dependencies getIpReachabilityMonitorDeps(Context context,
                String name) {
            return IpReachabilityMonitor.Dependencies.makeDefault(context, name);
        }

        /**
         * Return whether a feature guarded by a feature flag is enabled.
         * @see DeviceConfigUtils#isNetworkStackFeatureEnabled(Context, String)
         */
        public boolean isFeatureEnabled(final Context context, final String name) {
            return DeviceConfigUtils.isNetworkStackFeatureEnabled(context, name);
        }

        /**
         * Check whether one specific feature is not disabled.
         * @see DeviceConfigUtils#isNetworkStackFeatureNotChickenedOut(Context, String)
         */
        public boolean isFeatureNotChickenedOut(final Context context, final String name) {
            return DeviceConfigUtils.isNetworkStackFeatureNotChickenedOut(context, name);
        }

        /**
         * Create an APF filter if apfCapabilities indicates support for packet filtering using
         * APF programs.
         * @see ApfFilter#maybeCreate
         */
        public AndroidPacketFilter maybeCreateApfFilter(Context context,
                ApfFilter.ApfConfiguration config, InterfaceParams ifParams,
                IpClientCallbacksWrapper cb, NetworkQuirkMetrics networkQuirkMetrics,
                boolean useNewApfFilter) {
            if (useNewApfFilter) {
                return ApfFilter.maybeCreate(context, config, ifParams, cb, networkQuirkMetrics);
            } else {
                return LegacyApfFilter.maybeCreate(context, config, ifParams, cb,
                        networkQuirkMetrics);
            }
        }

        /**
         * Check if a specific IPv6 sysctl file exists or not.
         */
        public boolean hasIpv6Sysctl(final String ifname, final String name) {
            final String path = "/proc/sys/net/ipv6/conf/" + ifname + "/" + name;
            final File sysctl = new File(path);
            return sysctl.exists();
        }
         /**
         * Get the configuration from RRO to check whether or not to send domain search list
         * option in DHCPDISCOVER/DHCPREQUEST message.
         */
        public boolean getSendDomainSearchListOption(final Context context) {
            return context.getResources().getBoolean(R.bool.config_dhcp_client_domain_search_list);
        }

    }

    public IpClient(Context context, String ifName, IIpClientCallbacks callback,
            NetworkObserverRegistry observerRegistry, NetworkStackServiceManager nssManager) {
        this(context, ifName, callback, observerRegistry, nssManager, new Dependencies());
    }

    @VisibleForTesting
    public IpClient(Context context, String ifName, IIpClientCallbacks callback,
            NetworkObserverRegistry observerRegistry, NetworkStackServiceManager nssManager,
            Dependencies deps) {
        super(IpClient.class.getSimpleName() + "." + ifName);
        Objects.requireNonNull(ifName);
        Objects.requireNonNull(callback);

        mTag = getName();

        mDevicePolicyManager = (DevicePolicyManager)
                context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        mContext = context;
        mInterfaceName = ifName;
        mDependencies = deps;
        mMetricsLog = deps.getIpConnectivityLog();
        mNetworkQuirkMetrics = deps.getNetworkQuirkMetrics();
        mShutdownLatch = new CountDownLatch(1);
        mCm = mContext.getSystemService(ConnectivityManager.class);
        mObserverRegistry = observerRegistry;
        mIpMemoryStore = deps.getIpMemoryStore(context, nssManager);

        sSmLogs.putIfAbsent(mInterfaceName, new SharedLog(MAX_LOG_RECORDS, mTag));
        mLog = sSmLogs.get(mInterfaceName);
        sPktLogs.putIfAbsent(mInterfaceName, new LocalLog(MAX_PACKET_RECORDS));
        mConnectivityPacketLog = sPktLogs.get(mInterfaceName);
        mMsgStateLogger = new MessageHandlingLogger();
        mCallback = new IpClientCallbacksWrapper(callback, mLog, mShim);

        // TODO: Consider creating, constructing, and passing in some kind of
        // InterfaceController.Dependencies class.
        mNetd = deps.getNetd(mContext);
        mInterfaceCtrl = new InterfaceController(mInterfaceName, mNetd, mLog);

        mDhcp6PrefixDelegationEnabled = mDependencies.isFeatureEnabled(mContext,
                IPCLIENT_DHCPV6_PREFIX_DELEGATION_VERSION);

        mMinRdnssLifetimeSec = mDependencies.getDeviceConfigPropertyInt(
                CONFIG_MIN_RDNSS_LIFETIME, DEFAULT_MIN_RDNSS_LIFETIME);
        mAcceptRaMinLft = mDependencies.getDeviceConfigPropertyInt(CONFIG_ACCEPT_RA_MIN_LFT,
                DEFAULT_ACCEPT_RA_MIN_LFT);
        mApfCounterPollingIntervalMs = mDependencies.getDeviceConfigPropertyInt(
                CONFIG_APF_COUNTER_POLLING_INTERVAL_SECS,
                DEFAULT_APF_COUNTER_POLLING_INTERVAL_SECS) * DateUtils.SECOND_IN_MILLIS;
        mUseNewApfFilter = SdkLevel.isAtLeastV() || mDependencies.isFeatureEnabled(context,
                APF_NEW_RA_FILTER_VERSION);
        mEnableApfPollingCounters = mDependencies.isFeatureEnabled(context,
                APF_POLLING_COUNTERS_VERSION);
        mEnableIpClientIgnoreLowRaLifetime =
                SdkLevel.isAtLeastV() || mDependencies.isFeatureEnabled(context,
                        IPCLIENT_IGNORE_LOW_RA_LIFETIME_VERSION);
        // Light doze mode status checking API is only available at T or later releases.
        mApfShouldHandleLightDoze = SdkLevel.isAtLeastT() && mDependencies.isFeatureNotChickenedOut(
                mContext, APF_HANDLE_LIGHT_DOZE_FORCE_DISABLE);
        mPopulateLinkAddressLifetime = mDependencies.isFeatureEnabled(context,
                IPCLIENT_POPULATE_LINK_ADDRESS_LIFETIME_VERSION);

        IpClientLinkObserver.Configuration config = new IpClientLinkObserver.Configuration(
                mMinRdnssLifetimeSec, mPopulateLinkAddressLifetime);

        mLinkObserver = new IpClientLinkObserver(
                mContext, getHandler(),
                mInterfaceName,
                new IpClientLinkObserver.Callback() {
                    @Override
                    public void update(boolean linkState) {
                        sendMessage(EVENT_NETLINK_LINKPROPERTIES_CHANGED, linkState
                                ? ARG_LINKPROP_CHANGED_LINKSTATE_UP
                                : ARG_LINKPROP_CHANGED_LINKSTATE_DOWN);
                    }

                    @Override
                    public void onIpv6AddressRemoved(final Inet6Address address) {
                        // The update of Gratuitous NA target addresses set or unsolicited
                        // multicast NS source addresses set should be only accessed from the
                        // handler thread of IpClient StateMachine, keeping the behaviour
                        // consistent with relying on the non-blocking NetworkObserver callbacks,
                        // see {@link registerObserverForNonblockingCallback}. This can be done
                        // by either sending a message to StateMachine or posting a handler.
                        if (address.isLinkLocalAddress()) return;
                        getHandler().post(() -> {
                            mLog.log("Remove IPv6 GUA " + address
                                    + " from both Gratuituous NA and Multicast NS sets");
                            mGratuitousNaTargetAddresses.remove(address);
                            mMulticastNsSourceAddresses.remove(address);
                        });
                    }

                    @Override
                    public void onClatInterfaceStateUpdate(boolean add) {
                        // TODO: when clat interface was removed, consider sending a message to
                        // the IpClient main StateMachine thread, in case "NDO enabled" state
                        // becomes tied to more things that 464xlat operation.
                        getHandler().post(() -> {
                            mCallback.setNeighborDiscoveryOffload(add ? false : true);
                        });
                    }
                },
                config, mLog, mDependencies
        );

        mLinkProperties = new LinkProperties();
        mLinkProperties.setInterfaceName(mInterfaceName);

        mProvisioningTimeoutAlarm = new WakeupMessage(mContext, getHandler(),
                mTag + ".EVENT_PROVISIONING_TIMEOUT", EVENT_PROVISIONING_TIMEOUT);
        mDhcpActionTimeoutAlarm = new WakeupMessage(mContext, getHandler(),
                mTag + ".EVENT_DHCPACTION_TIMEOUT", EVENT_DHCPACTION_TIMEOUT);

        // Anything the StateMachine may access must have been instantiated
        // before this point.
        configureAndStartStateMachine();

        // Anything that may send messages to the StateMachine must only be
        // configured to do so after the StateMachine has started (above).
        startStateMachineUpdaters();
    }

    /**
     * Make a IIpClient connector to communicate with this IpClient.
     */
    public IIpClient makeConnector() {
        return new IpClientConnector();
    }

    class IpClientConnector extends IIpClient.Stub {
        @Override
        public void completedPreDhcpAction() {
            enforceNetworkStackCallingPermission();
            IpClient.this.completedPreDhcpAction();
        }
        @Override
        public void confirmConfiguration() {
            enforceNetworkStackCallingPermission();
            IpClient.this.confirmConfiguration();
        }
        @Override
        public void readPacketFilterComplete(byte[] data) {
            enforceNetworkStackCallingPermission();
            IpClient.this.readPacketFilterComplete(data);
        }
        @Override
        public void shutdown() {
            enforceNetworkStackCallingPermission();
            IpClient.this.shutdown();
        }
        @Override
        public void startProvisioning(ProvisioningConfigurationParcelable req) {
            enforceNetworkStackCallingPermission();
            IpClient.this.startProvisioning(ProvisioningConfiguration.fromStableParcelable(req,
                    mCallback.getInterfaceVersion()));
        }
        @Override
        public void stop() {
            enforceNetworkStackCallingPermission();
            IpClient.this.stop();
        }
        @Override
        public void setL2KeyAndGroupHint(String l2Key, String cluster) {
            enforceNetworkStackCallingPermission();
            IpClient.this.setL2KeyAndCluster(l2Key, cluster);
        }
        @Override
        public void setTcpBufferSizes(String tcpBufferSizes) {
            enforceNetworkStackCallingPermission();
            IpClient.this.setTcpBufferSizes(tcpBufferSizes);
        }
        @Override
        public void setHttpProxy(ProxyInfo proxyInfo) {
            enforceNetworkStackCallingPermission();
            IpClient.this.setHttpProxy(proxyInfo);
        }
        @Override
        public void setMulticastFilter(boolean enabled) {
            enforceNetworkStackCallingPermission();
            IpClient.this.setMulticastFilter(enabled);
        }
        @Override
        public void addKeepalivePacketFilter(int slot, TcpKeepalivePacketDataParcelable pkt) {
            enforceNetworkStackCallingPermission();
            IpClient.this.addKeepalivePacketFilter(slot, pkt);
        }
        @Override
        public void addNattKeepalivePacketFilter(int slot, NattKeepalivePacketDataParcelable pkt) {
            enforceNetworkStackCallingPermission();
            IpClient.this.addNattKeepalivePacketFilter(slot, pkt);
        }
        @Override
        public void removeKeepalivePacketFilter(int slot) {
            enforceNetworkStackCallingPermission();
            IpClient.this.removeKeepalivePacketFilter(slot);
        }
        @Override
        public void notifyPreconnectionComplete(boolean success) {
            enforceNetworkStackCallingPermission();
            IpClient.this.notifyPreconnectionComplete(success);
        }
        @Override
        public void updateLayer2Information(Layer2InformationParcelable info) {
            enforceNetworkStackCallingPermission();
            IpClient.this.updateLayer2Information(info);
        }
        @Override
        public void updateApfCapabilities(ApfCapabilities apfCapabilities) {
            enforceNetworkStackCallingPermission();
            IpClient.this.updateApfCapabilities(apfCapabilities);
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

    public String getInterfaceName() {
        return mInterfaceName;
    }

    private void configureAndStartStateMachine() {
        // CHECKSTYLE:OFF IndentationCheck
        addState(mStoppedState);
        addState(mStartedState);
            addState(mPreconnectingState, mStartedState);
            addState(mClearingIpAddressesState, mStartedState);
            addState(mRunningState, mStartedState);
        addState(mStoppingState);
        // CHECKSTYLE:ON IndentationCheck

        setInitialState(mStoppedState);

        super.start();
    }

    private void startStateMachineUpdaters() {
        mObserverRegistry.registerObserverForNonblockingCallback(mLinkObserver);
    }

    private void stopStateMachineUpdaters() {
        mObserverRegistry.unregisterObserver(mLinkObserver);
        mLinkObserver.clearInterfaceParams();
        mLinkObserver.shutdown();
    }

    private boolean isGratuitousNaEnabled() {
        return mDependencies.isFeatureNotChickenedOut(mContext, IPCLIENT_GRATUITOUS_NA_VERSION);
    }

    private boolean isGratuitousArpNaRoamingEnabled() {
        return mDependencies.isFeatureEnabled(mContext, IPCLIENT_GARP_NA_ROAMING_VERSION);
    }

    @VisibleForTesting
    static MacAddress getInitialBssid(final Layer2Information layer2Info,
            final ScanResultInfo scanResultInfo, boolean isAtLeastS) {
        MacAddress bssid = null;
        // http://b/185202634
        // ScanResultInfo is not populated in some situations.
        // On S and above, prefer getting the BSSID from the Layer2Info.
        // On R and below, get the BSSID from the ScanResultInfo and fall back to
        // getting it from the Layer2Info. This ensures no regressions if any R
        // devices pass in a null or meaningless BSSID in the Layer2Info.
        if (!isAtLeastS && scanResultInfo != null) {
            try {
                bssid = MacAddress.fromString(scanResultInfo.getBssid());
            } catch (IllegalArgumentException e) {
                Log.wtf(TAG, "Invalid BSSID: " + scanResultInfo.getBssid()
                        + " in provisioning configuration", e);
            }
        }
        if (bssid == null && layer2Info != null) {
            bssid = layer2Info.mBssid;
        }
        return bssid;
    }

    @Override
    protected void onQuitting() {
        mCallback.onQuit();
        mShutdownLatch.countDown();
    }

    /**
     * Shut down this IpClient instance altogether.
     */
    public void shutdown() {
        stop();
        sendMessage(CMD_TERMINATE_AFTER_STOP);
    }

    /**
     * Start provisioning with the provided parameters.
     */
    public void startProvisioning(ProvisioningConfiguration req) {
        if (!req.isValid()) {
            doImmediateProvisioningFailure(IpManagerEvent.ERROR_INVALID_PROVISIONING);
            return;
        }

        mCurrentBssid = getInitialBssid(req.mLayer2Info, req.mScanResultInfo,
                ShimUtils.isAtLeastS());
        mCurrentApfCapabilities = req.mApfCapabilities;
        mCreatorUid = req.mCreatorUid;
        if (req.mLayer2Info != null) {
            mL2Key = req.mLayer2Info.mL2Key;
            mCluster = req.mLayer2Info.mCluster;
        }
        sendMessage(CMD_START, new android.net.shared.ProvisioningConfiguration(req));
    }

    /**
     * Stop this IpClient.
     *
     * <p>This does not shut down the StateMachine itself, which is handled by {@link #shutdown()}.
     *    The message "arg1" parameter is used to record the disconnect code metrics.
     *    Usually this method is called by the peer (e.g. wifi) intentionally to stop IpClient,
     *    consider that's the normal user termination.
     */
    public void stop() {
        sendMessage(CMD_STOP, DisconnectCode.DC_NORMAL_TERMINATION.getNumber());
    }

    /**
     * Confirm the provisioning configuration.
     */
    public void confirmConfiguration() {
        sendMessage(CMD_CONFIRM);
    }

    /**
     * For clients using {@link ProvisioningConfiguration.Builder#withPreDhcpAction()}, must be
     * called after {@link IIpClientCallbacks#onPreDhcpAction} to indicate that DHCP is clear to
     * proceed.
     */
    public void completedPreDhcpAction() {
        sendMessage(EVENT_PRE_DHCP_ACTION_COMPLETE);
    }

    /**
     * Indicate that packet filter read is complete.
     */
    public void readPacketFilterComplete(byte[] data) {
        sendMessage(EVENT_READ_PACKET_FILTER_COMPLETE, data);
    }

    /**
     * Set the TCP buffer sizes to use.
     *
     * This may be called, repeatedly, at any time before or after a call to
     * #startProvisioning(). The setting is cleared upon calling #stop().
     */
    public void setTcpBufferSizes(String tcpBufferSizes) {
        sendMessage(CMD_UPDATE_TCP_BUFFER_SIZES, tcpBufferSizes);
    }

    /**
     * Set the L2 key and cluster for storing info into the memory store.
     *
     * This method is only supported on Q devices. For R or above releases,
     * caller should call #updateLayer2Information() instead.
     */
    public void setL2KeyAndCluster(String l2Key, String cluster) {
        if (!ShimUtils.isReleaseOrDevelopmentApiAbove(Build.VERSION_CODES.Q)) {
            sendMessage(CMD_UPDATE_L2KEY_CLUSTER, new Pair<>(l2Key, cluster));
        }
    }

    /**
     * Set the HTTP Proxy configuration to use.
     *
     * This may be called, repeatedly, at any time before or after a call to
     * #startProvisioning(). The setting is cleared upon calling #stop().
     */
    public void setHttpProxy(ProxyInfo proxyInfo) {
        sendMessage(CMD_UPDATE_HTTP_PROXY, proxyInfo);
    }

    /**
     * Enable or disable the multicast filter.  Attempts to use APF to accomplish the filtering,
     * if not, Callback.setFallbackMulticastFilter() is called.
     */
    public void setMulticastFilter(boolean enabled) {
        sendMessage(CMD_SET_MULTICAST_FILTER, enabled);
    }

    /**
     * Called by WifiStateMachine to add TCP keepalive packet filter before setting up
     * keepalive offload.
     */
    public void addKeepalivePacketFilter(int slot, @NonNull TcpKeepalivePacketDataParcelable pkt) {
        sendMessage(CMD_ADD_KEEPALIVE_PACKET_FILTER_TO_APF, slot, 0 /* Unused */, pkt);
    }

    /**
     *  Called by WifiStateMachine to add NATT keepalive packet filter before setting up
     *  keepalive offload.
     */
    public void addNattKeepalivePacketFilter(int slot,
            @NonNull NattKeepalivePacketDataParcelable pkt) {
        sendMessage(CMD_ADD_KEEPALIVE_PACKET_FILTER_TO_APF, slot, 0 /* Unused */ , pkt);
    }

    /**
     * Called by WifiStateMachine to remove keepalive packet filter after stopping keepalive
     * offload.
     */
    public void removeKeepalivePacketFilter(int slot) {
        sendMessage(CMD_REMOVE_KEEPALIVE_PACKET_FILTER_FROM_APF, slot, 0 /* Unused */);
    }

    /**
     * Notify IpClient that preconnection is complete and that the link is ready for use.
     * The success parameter indicates whether the packets passed in by onPreconnectionStart were
     * successfully sent to the network or not.
     */
    public void notifyPreconnectionComplete(boolean success) {
        sendMessage(CMD_COMPLETE_PRECONNECTION, success ? 1 : 0);
    }

    /**
     * Update the network bssid, L2Key and cluster on L2 roaming happened.
     */
    public void updateLayer2Information(@NonNull Layer2InformationParcelable info) {
        sendMessage(CMD_UPDATE_L2INFORMATION, info);
    }

    /**
     * Update the APF capabilities.
     *
     * This method will update the APF capabilities used in IpClient and decide if a new APF
     * program should be installed to filter the incoming packets based on that. So far this
     * method only allows for the APF capabilities to go from null to non-null, and no other
     * changes are allowed. One use case is when WiFi interface switches from secondary to
     * primary in STA+STA mode.
     */
    public void updateApfCapabilities(@NonNull ApfCapabilities apfCapabilities) {
        sendMessage(CMD_UPDATE_APF_CAPABILITIES, apfCapabilities);
    }

    /**
     * Dump logs of this IpClient.
     */
    public void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        if (args != null && args.length > 0 && DUMP_ARG_CONFIRM.equals(args[0])) {
            // Execute confirmConfiguration() and take no further action.
            confirmConfiguration();
            return;
        }

        // Thread-unsafe access to mApfFilter but just used for debugging.
        final AndroidPacketFilter apfFilter = mApfFilter;
        final android.net.shared.ProvisioningConfiguration provisioningConfig = mConfiguration;
        final ApfCapabilities apfCapabilities = (provisioningConfig != null)
                ? provisioningConfig.mApfCapabilities : null;

        IndentingPrintWriter pw = new IndentingPrintWriter(writer, "  ");
        pw.println(mTag + " APF dump:");
        pw.increaseIndent();
        if (apfFilter != null && apfCapabilities != null
                && apfCapabilities.apfVersionSupported > 0) {
            if (apfCapabilities.hasDataAccess()) {
                // Request a new snapshot, then wait for it.
                mApfDataSnapshotComplete.close();
                mCallback.startReadPacketFilter();
                if (!mApfDataSnapshotComplete.block(1000)) {
                    pw.print("TIMEOUT: DUMPING STALE APF SNAPSHOT");
                }
            }
            apfFilter.dump(pw);

        } else {
            pw.print("No active ApfFilter; ");
            if (provisioningConfig == null) {
                pw.println("IpClient not yet started.");
            } else if (apfCapabilities == null || apfCapabilities.apfVersionSupported == 0) {
                pw.println("Hardware does not support APF.");
            } else {
                pw.println("ApfFilter not yet started, APF capabilities: " + apfCapabilities);
            }
        }
        pw.decreaseIndent();
        pw.println();
        pw.println(mTag + " current ProvisioningConfiguration:");
        pw.increaseIndent();
        pw.println(Objects.toString(provisioningConfig, "N/A"));
        pw.decreaseIndent();

        final IpReachabilityMonitor iprm = mIpReachabilityMonitor;
        if (iprm != null) {
            pw.println();
            pw.println(mTag + " current IpReachabilityMonitor state:");
            pw.increaseIndent();
            iprm.dump(pw);
            pw.decreaseIndent();
        }

        pw.println();
        pw.println(mTag + " StateMachine dump:");
        pw.increaseIndent();
        mLog.dump(fd, pw, args);
        pw.decreaseIndent();

        pw.println();
        pw.println(mTag + " connectivity packet log:");
        pw.println();
        pw.println("Debug with python and scapy via:");
        pw.println("shell$ python");
        pw.println(">>> from scapy import all as scapy");
        pw.println(">>> scapy.Ether(\"<paste_hex_string>\".decode(\"hex\")).show2()");
        pw.println();

        pw.increaseIndent();
        mConnectivityPacketLog.readOnlyLocalLog().dump(fd, pw, args);
        pw.decreaseIndent();
    }


    /**
     * Internals.
     */

    @Override
    protected String getWhatToString(int what) {
        return sWhatToString.get(what, "UNKNOWN: " + Integer.toString(what));
    }

    @Override
    protected String getLogRecString(Message msg) {
        final String logLine = String.format(
                "%s/%d %d %d %s [%s]",
                mInterfaceName, (mInterfaceParams == null) ? -1 : mInterfaceParams.index,
                msg.arg1, msg.arg2, Objects.toString(msg.obj), mMsgStateLogger);

        final String richerLogLine = getWhatToString(msg.what) + " " + logLine;
        mLog.log(richerLogLine);
        if (DBG) {
            Log.d(mTag, richerLogLine);
        }

        mMsgStateLogger.reset();
        return logLine;
    }

    @Override
    protected boolean recordLogRec(Message msg) {
        // Don't log EVENT_NETLINK_LINKPROPERTIES_CHANGED. They can be noisy,
        // and we already log any LinkProperties change that results in an
        // invocation of IpClient.Callback#onLinkPropertiesChange().
        final boolean shouldLog = (msg.what != EVENT_NETLINK_LINKPROPERTIES_CHANGED);
        if (!shouldLog) {
            mMsgStateLogger.reset();
        }
        return shouldLog;
    }

    private void logError(String fmt, Throwable e, Object... args) {
        mLog.e(String.format(fmt, args), e);
    }

    private void logError(String fmt, Object... args) {
        logError(fmt, null, args);
    }

    // This needs to be called with care to ensure that our LinkProperties
    // are in sync with the actual LinkProperties of the interface. For example,
    // we should only call this if we know for sure that there are no IP addresses
    // assigned to the interface, etc.
    private void resetLinkProperties() {
        mLinkObserver.clearLinkProperties();
        mConfiguration = null;
        mDhcpResults = null;
        mTcpBufferSizes = "";
        mHttpProxy = null;

        mLinkProperties = new LinkProperties();
        mLinkProperties.setInterfaceName(mInterfaceName);
    }

    private void recordMetric(final int type) {
        // We may record error metrics prior to starting.
        // Map this to IMMEDIATE_FAILURE_DURATION.
        final long duration = (mStartTimeMillis > 0)
                ? (SystemClock.elapsedRealtime() - mStartTimeMillis)
                : IMMEDIATE_FAILURE_DURATION;
        mMetricsLog.log(mInterfaceName, new IpManagerEvent(type, duration));
    }

    // Record the DisconnectCode and transition to StoppingState.
    private void transitionToStoppingState(final DisconnectCode code) {
        mIpProvisioningMetrics.setDisconnectCode(code);
        transitionTo(mStoppingState);
    }

    // Convert reachability loss reason enum to a string.
    private static String reachabilityLossReasonToString(int reason) {
        switch (reason) {
            case ReachabilityLossReason.ROAM:
                return "reachability_loss_after_roam";
            case ReachabilityLossReason.CONFIRM:
                return "reachability_loss_after_confirm";
            case ReachabilityLossReason.ORGANIC:
                return "reachability_loss_organic";
            default:
                return "unknown";
        }
    }

    private static boolean hasIpv6LinkLocalInterfaceRoute(final LinkProperties lp) {
        for (RouteInfo r : lp.getRoutes()) {
            if (r.getDestination().equals(new IpPrefix("fe80::/64"))
                    && r.getGateway().isAnyLocalAddress()) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasIpv6LinkLocalAddress(final LinkProperties lp) {
        for (LinkAddress address : lp.getLinkAddresses()) {
            if (address.isIpv6() && address.getAddress().isLinkLocalAddress()) {
                return true;
            }
        }
        return false;
    }

    // LinkProperties has a link-local (fe80::xxx) IPv6 address and route to fe80::/64 destination.
    private boolean isIpv6LinkLocalProvisioned(final LinkProperties lp) {
        if (mConfiguration == null
                || mConfiguration.mIPv6ProvisioningMode != PROV_IPV6_LINKLOCAL) return false;
        if (hasIpv6LinkLocalAddress(lp) && hasIpv6LinkLocalInterfaceRoute(lp)) return true;
        return false;
    }

    // For now: use WifiStateMachine's historical notion of provisioned.
    @VisibleForTesting
    boolean isProvisioned(final LinkProperties lp, final InitialConfiguration config) {
        // For historical reasons, we should connect even if all we have is an IPv4
        // address and nothing else. If IPv6 link-local only mode is enabled and
        // it's provisioned without IPv4, then still connecting once IPv6 link-local
        // address is ready to use and route to fe80::/64 destination is up.
        if (lp.hasIpv4Address() || lp.isProvisioned() || isIpv6LinkLocalProvisioned(lp)) {
            return true;
        }
        if (config == null) {
            return false;
        }

        // When an InitialConfiguration is specified, ignore any difference with previous
        // properties and instead check if properties observed match the desired properties.
        return config.isProvisionedBy(lp.getLinkAddresses(), lp.getRoutes());
    }

    // Set "/proc/sys/net/ipv6/conf/${iface}/${name}" with the given specific value.
    private void setIpv6Sysctl(@NonNull final String name, int value) {
        try {
            mNetd.setProcSysNet(INetd.IPV6, INetd.CONF, mInterfaceName,
                    name, Integer.toString(value));
        } catch (Exception e) {
            Log.e(mTag, "Failed to set " + name + " to " + value + ": " + e);
        }
    }

    // Read "/proc/sys/net/ipv6/conf/${iface}/${name}".
    private Integer getIpv6Sysctl(@NonNull final String name) {
        try {
            return Integer.parseInt(mNetd.getProcSysNet(INetd.IPV6, INetd.CONF,
                    mInterfaceName, name));
        } catch (RemoteException | ServiceSpecificException e) {
            logError("Couldn't read " + name + " on " + mInterfaceName, e);
            return null;
        }
    }

    // TODO: Investigate folding all this into the existing static function
    // LinkProperties.compareProvisioning() or some other single function that
    // takes two LinkProperties objects and returns a ProvisioningChange
    // object that is a correct and complete assessment of what changed, taking
    // account of the asymmetries described in the comments in this function.
    // Then switch to using it everywhere (IpReachabilityMonitor, etc.).
    private int compareProvisioning(LinkProperties oldLp, LinkProperties newLp) {
        int delta;
        InitialConfiguration config = mConfiguration != null ? mConfiguration.mInitialConfig : null;
        final boolean wasProvisioned = isProvisioned(oldLp, config);
        final boolean isProvisioned = isProvisioned(newLp, config);

        if (!wasProvisioned && isProvisioned) {
            delta = PROV_CHANGE_GAINED_PROVISIONING;
        } else if (wasProvisioned && isProvisioned) {
            delta = PROV_CHANGE_STILL_PROVISIONED;
        } else if (!wasProvisioned && !isProvisioned) {
            delta = PROV_CHANGE_STILL_NOT_PROVISIONED;
        } else {
            // (wasProvisioned && !isProvisioned)
            //
            // Note that this is true even if we lose a configuration element
            // (e.g., a default gateway) that would not be required to advance
            // into provisioned state. This is intended: if we have a default
            // router and we lose it, that's a sure sign of a problem, but if
            // we connect to a network with no IPv4 DNS servers, we consider
            // that to be a network without DNS servers and connect anyway.
            //
            // See the comment below.
            delta = PROV_CHANGE_LOST_PROVISIONING;
        }

        final boolean lostIPv6 = oldLp.isIpv6Provisioned() && !newLp.isIpv6Provisioned();
        final boolean lostIPv4Address = oldLp.hasIpv4Address() && !newLp.hasIpv4Address();
        final boolean lostIPv6Router = oldLp.hasIpv6DefaultRoute() && !newLp.hasIpv6DefaultRoute();

        // If bad wifi avoidance is disabled, then ignore IPv6 loss of
        // provisioning. Otherwise, when a hotspot that loses Internet
        // access sends out a 0-lifetime RA to its clients, the clients
        // will disconnect and then reconnect, avoiding the bad hotspot,
        // instead of getting stuck on the bad hotspot. http://b/31827713 .
        //
        // This is incorrect because if the hotspot then regains Internet
        // access with a different prefix, TCP connections on the
        // deprecated addresses will remain stuck.
        //
        // Note that we can still be disconnected by IpReachabilityMonitor
        // if the IPv6 default gateway (but not the IPv6 DNS servers; see
        // accompanying code in IpReachabilityMonitor) is unreachable.
        final boolean ignoreIPv6ProvisioningLoss = mHasDisabledAcceptRaDefrtrOnProvLoss
                || (mConfiguration != null && mConfiguration.mUsingMultinetworkPolicyTracker
                        && !mCm.shouldAvoidBadWifi());

        // Additionally:
        //
        // Partial configurations (e.g., only an IPv4 address with no DNS
        // servers and no default route) are accepted as long as DHCPv4
        // succeeds. On such a network, isProvisioned() will always return
        // false, because the configuration is not complete, but we want to
        // connect anyway. It might be a disconnected network such as a
        // Chromecast or a wireless printer, for example.
        //
        // Because on such a network isProvisioned() will always return false,
        // delta will never be LOST_PROVISIONING. So check for loss of
        // provisioning here too.
        if (lostIPv4Address || (lostIPv6 && !ignoreIPv6ProvisioningLoss)) {
            delta = PROV_CHANGE_LOST_PROVISIONING;
        }

        // Additionally:
        //
        // If the previous link properties had a global IPv6 address and an
        // IPv6 default route then also consider the loss of that default route
        // to be a loss of provisioning. See b/27962810.
        if (oldLp.hasGlobalIpv6Address() && (lostIPv6Router && !ignoreIPv6ProvisioningLoss)) {
            // Although link properties have lost IPv6 default route in this case, if IPv4 is still
            // working with appropriate routes and DNS servers, we can keep the current connection
            // without disconnecting from the network, just disable accept_ra_defrtr sysctl on that
            // given network until to the next provisioning.
            //
            // Disabling IPv6 stack will result in all IPv6 connectivity torn down and all IPv6
            // sockets being closed, the non-routable IPv6 DNS servers will be stripped out, so
            // applications will be able to reconnect immediately over IPv4. See b/131781810.
            //
            // Sometimes disabling IPv6 stack can cause other problems(see b/179222860), conversely,
            // disabling accept_ra_defrtr can still keep the interface IPv6 capable, but no longer
            // learns the default router from incoming RA, partial IPv6 connectivity will remain on
            // the interface, through which applications can still communicate locally.
            if (newLp.isIpv4Provisioned()) {
                // Restart ipv6 with accept_ra_defrtr set to 0.
                mInterfaceCtrl.disableIPv6();
                startIPv6(0 /* accept_ra_defrtr */);

                mNetworkQuirkMetrics.setEvent(NetworkQuirkEvent.QE_IPV6_PROVISIONING_ROUTER_LOST);
                mNetworkQuirkMetrics.statsWrite();
                mHasDisabledAcceptRaDefrtrOnProvLoss = true;
                delta = PROV_CHANGE_STILL_PROVISIONED;
                mLog.log("Disabled accept_ra_defrtr sysctl on loss of IPv6 default router");
            } else {
                delta = PROV_CHANGE_LOST_PROVISIONING;
            }
        }

        return delta;
    }

    private void dispatchCallback(int delta, LinkProperties newLp) {
        switch (delta) {
            case PROV_CHANGE_GAINED_PROVISIONING:
                if (DBG) {
                    Log.d(mTag, "onProvisioningSuccess()");
                }
                recordMetric(IpManagerEvent.PROVISIONING_OK);
                mCallback.onProvisioningSuccess(newLp);
                break;

            case PROV_CHANGE_LOST_PROVISIONING:
                if (DBG) {
                    Log.d(mTag, "onProvisioningFailure()");
                }
                recordMetric(IpManagerEvent.PROVISIONING_FAIL);
                mCallback.onProvisioningFailure(newLp);
                break;

            default:
                if (DBG) {
                    Log.d(mTag, "onLinkPropertiesChange()");
                }
                mCallback.onLinkPropertiesChange(newLp);
                break;
        }
    }

    // Updates all IpClient-related state concerned with LinkProperties.
    // Returns a ProvisioningChange for possibly notifying other interested
    // parties that are not fronted by IpClient.
    private int setLinkProperties(LinkProperties newLp) {
        if (mApfFilter != null) {
            mApfFilter.setLinkProperties(newLp);
        }
        if (mIpReachabilityMonitor != null) {
            mIpReachabilityMonitor.updateLinkProperties(newLp);
        }

        int delta = compareProvisioning(mLinkProperties, newLp);
        mLinkProperties = new LinkProperties(newLp);

        if (delta == PROV_CHANGE_GAINED_PROVISIONING) {
            // TODO: Add a proper ProvisionedState and cancel the alarm in
            // its enter() method.
            mProvisioningTimeoutAlarm.cancel();
        }

        return delta;
    }

    private LinkProperties assembleLinkProperties() {
        // [1] Create a new LinkProperties object to populate.
        LinkProperties newLp = new LinkProperties();
        newLp.setInterfaceName(mInterfaceName);

        // [2] Pull in data from netlink:
        //         - IPv4 addresses
        //         - IPv6 addresses
        //         - IPv6 routes
        //         - IPv6 DNS servers
        //
        // N.B.: this is fundamentally race-prone and should be fixed by
        // changing IpClientLinkObserver from a hybrid edge/level model to an
        // edge-only model, or by giving IpClient its own netlink socket(s)
        // so as to track all required information directly.
        LinkProperties netlinkLinkProperties = mLinkObserver.getLinkProperties();
        newLp.setLinkAddresses(netlinkLinkProperties.getLinkAddresses());
        for (RouteInfo route : netlinkLinkProperties.getRoutes()) {
            newLp.addRoute(route);
        }
        addAllReachableDnsServers(newLp, netlinkLinkProperties.getDnsServers());
        mShim.setNat64Prefix(newLp, mShim.getNat64Prefix(netlinkLinkProperties));

        // Check if any link address update from netlink.
        final CompareResult<LinkAddress> results =
                LinkPropertiesUtils.compareAddresses(mLinkProperties, newLp);
        // In the case that there are multiple netlink update events about a global IPv6 address
        // derived from the delegated prefix, a flag-only change event(e.g. due to the duplicate
        // address detection) will cause an identical IP address to be put into both Added and
        // Removed list based on the CompareResult implementation. To prevent a prefix from being
        // mistakenly removed from the delegate prefix list, it is better to always check the
        // removed list before checking the added list(e.g. anyway we can add the removed prefix
        // back again).
        for (LinkAddress la : results.removed) {
            if (mDhcp6PrefixDelegationEnabled && isIpv6StableDelegatedAddress(la)) {
                final IpPrefix prefix = new IpPrefix(la.getAddress(), RFC7421_PREFIX_LENGTH);
                mDelegatedPrefixes.remove(prefix);
            }
            // TODO: remove onIpv6AddressRemoved callback.
        }

        for (LinkAddress la : results.added) {
            if (mDhcp6PrefixDelegationEnabled && isIpv6StableDelegatedAddress(la)) {
                final IpPrefix prefix = new IpPrefix(la.getAddress(), RFC7421_PREFIX_LENGTH);
                mDelegatedPrefixes.add(prefix);
            }
        }

        // [3] Add in data from DHCPv4, if available.
        //
        // mDhcpResults is never shared with any other owner so we don't have
        // to worry about concurrent modification.
        if (mDhcpResults != null) {
            final List<RouteInfo> routes =
                    mDhcpResults.toStaticIpConfiguration().getRoutes(mInterfaceName);
            for (RouteInfo route : routes) {
                newLp.addRoute(route);
            }
            addAllReachableDnsServers(newLp, mDhcpResults.dnsServers);
            if (mDhcpResults.dmnsrchList.size() == 0) {
                newLp.setDomains(mDhcpResults.domains);
            } else {
                final String domainsString = mDhcpResults.appendDomainsSearchList();
                newLp.setDomains(TextUtils.isEmpty(domainsString) ? null : domainsString);
            }

            if (mDhcpResults.mtu != 0) {
                newLp.setMtu(mDhcpResults.mtu);
            }

            if (mDhcpResults.serverAddress != null) {
                mShim.setDhcpServerAddress(newLp, mDhcpResults.serverAddress);
            }

            final String capportUrl = mDhcpResults.captivePortalApiUrl;
            // Uri.parse does no syntax check; do a simple check to eliminate garbage.
            // If the URL is still incorrect data fetching will fail later, which is fine.
            if (isParseableUrl(capportUrl)) {
                NetworkInformationShimImpl.newInstance()
                        .setCaptivePortalApiUrl(newLp, Uri.parse(capportUrl));
            }
            // TODO: also look at the IPv6 RA (netlink) for captive portal URL
        }

        // [4] Add route with delegated prefix according to the global address update.
        if (mDhcp6PrefixDelegationEnabled) {
            for (IpPrefix destination : mDelegatedPrefixes) {
                // Direct-connected route to delegated prefix. Add RTN_UNREACHABLE to
                // this route based on the delegated prefix. To prevent the traffic loop
                // between host and upstream delegated router. Because we specify the
                // IFA_F_NOPREFIXROUTE when adding the IPv6 address, the kernel does not
                // create a delegated prefix route, as a result, the user space won't
                // receive any RTM_NEWROUTE message about the delegated prefix, we still
                // need to install an unreachable route for the delegated prefix manually
                // in LinkProperties to notify the caller this update.
                // TODO: support RTN_BLACKHOLE in netd and use that on newer Android
                // versions.
                final RouteInfo route = new RouteInfo(destination,
                        null /* gateway */, mInterfaceName, RTN_UNREACHABLE);
                newLp.addRoute(route);
            }
        }

        // [5] Add in TCP buffer sizes and HTTP Proxy config, if available.
        if (!TextUtils.isEmpty(mTcpBufferSizes)) {
            newLp.setTcpBufferSizes(mTcpBufferSizes);
        }
        if (mHttpProxy != null) {
            newLp.setHttpProxy(mHttpProxy);
        }

        // [6] Add data from InitialConfiguration
        if (mConfiguration != null && mConfiguration.mInitialConfig != null) {
            InitialConfiguration config = mConfiguration.mInitialConfig;
            // Add InitialConfiguration routes and dns server addresses once all addresses
            // specified in the InitialConfiguration have been observed with Netlink.
            if (config.isProvisionedBy(newLp.getLinkAddresses(), null)) {
                for (IpPrefix prefix : config.directlyConnectedRoutes) {
                    newLp.addRoute(new RouteInfo(prefix, null, mInterfaceName, RTN_UNICAST));
                }
            }
            addAllReachableDnsServers(newLp, config.dnsServers);
        }
        final LinkProperties oldLp = mLinkProperties;
        if (DBG) {
            Log.d(mTag, String.format("Netlink-seen LPs: %s, new LPs: %s; old LPs: %s",
                    netlinkLinkProperties, newLp, oldLp));
        }

        // TODO: also learn via netlink routes specified by an InitialConfiguration and specified
        // from a static IP v4 config instead of manually patching them in in steps [3] and [5].
        return newLp;
    }

    private static boolean isParseableUrl(String url) {
        // Verify that a URL has a reasonable format that can be parsed as per the URL constructor.
        // This does not use Patterns.WEB_URL as that pattern excludes URLs without TLDs, such as on
        // localhost.
        if (url == null) return false;
        try {
            new URL(url);
            return true;
        } catch (MalformedURLException e) {
            return false;
        }
    }

    private static void addAllReachableDnsServers(
            LinkProperties lp, Iterable<InetAddress> dnses) {
        // TODO: Investigate deleting this reachability check.  We should be
        // able to pass everything down to netd and let netd do evaluation
        // and RFC6724-style sorting.
        for (InetAddress dns : dnses) {
            if (!dns.isAnyLocalAddress() && lp.isReachable(dns)) {
                lp.addDnsServer(dns);
            }
        }
    }

    private void transmitPacket(final ByteBuffer packet, final SocketAddress sockAddress,
            final String msg) {
        FileDescriptor sock = null;
        try {
            sock = Os.socket(AF_PACKET, SOCK_RAW | SOCK_NONBLOCK, 0 /* protocol */);
            Os.sendto(sock, packet.array(), 0 /* byteOffset */, packet.limit() /* byteCount */,
                    0 /* flags */, sockAddress);
        } catch (SocketException | ErrnoException e) {
            logError(msg, e);
        } finally {
            SocketUtils.closeSocketQuietly(sock);
        }
    }

    private void sendGratuitousNA(final Inet6Address srcIp, final Inet6Address targetIp) {
        final int flags = 0; // R=0, S=0, O=0
        final Inet6Address dstIp = IPV6_ADDR_ALL_ROUTERS_MULTICAST;
        // Ethernet multicast destination address: 33:33:00:00:00:02.
        final MacAddress dstMac = NetworkStackUtils.ipv6MulticastToEthernetMulticast(dstIp);
        final ByteBuffer packet = NeighborAdvertisement.build(mInterfaceParams.macAddr, dstMac,
                srcIp, dstIp, flags, targetIp);
        final SocketAddress sockAddress =
                SocketUtilsShimImpl.newInstance().makePacketSocketAddress(ETH_P_IPV6,
                        mInterfaceParams.index, dstMac.toByteArray());

        transmitPacket(packet, sockAddress, "Failed to send Gratuitous Neighbor Advertisement");
    }

    private void sendGratuitousARP(final Inet4Address srcIp) {
        final ByteBuffer packet = ArpPacket.buildArpPacket(ETHER_BROADCAST /* dstMac */,
                mInterfaceParams.macAddr.toByteArray() /* srcMac */,
                srcIp.getAddress() /* targetIp */,
                ETHER_BROADCAST /* targetHwAddress */,
                srcIp.getAddress() /* senderIp */, (short) ARP_REPLY);
        final SocketAddress sockAddress =
                makePacketSocketAddress(ETH_P_ARP, mInterfaceParams.index);

        transmitPacket(packet, sockAddress, "Failed to send GARP");
    }

    private void sendMulticastNs(final Inet6Address srcIp, final Inet6Address dstIp,
            final Inet6Address targetIp) {
        final MacAddress dstMac = NetworkStackUtils.ipv6MulticastToEthernetMulticast(dstIp);
        final ByteBuffer packet = NeighborSolicitation.build(mInterfaceParams.macAddr, dstMac,
                srcIp, dstIp, targetIp);
        final SocketAddress sockAddress =
                SocketUtilsShimImpl.newInstance().makePacketSocketAddress(ETH_P_IPV6,
                        mInterfaceParams.index, dstMac.toByteArray());

        if (DBG) {
            mLog.log("send multicast NS from " + srcIp.getHostAddress() + " to "
                    + dstIp.getHostAddress() + " , target IP: " + targetIp.getHostAddress());
        }
        transmitPacket(packet, sockAddress, "Failed to send multicast Neighbor Solicitation");
    }

    @Nullable
    private static Inet6Address getIpv6LinkLocalAddress(final LinkProperties newLp) {
        for (LinkAddress la : newLp.getLinkAddresses()) {
            if (!la.isIpv6()) continue;
            final Inet6Address ip = (Inet6Address) la.getAddress();
            if (ip.isLinkLocalAddress()) return ip;
        }
        return null;
    }

    private void maybeSendGratuitousNAs(final LinkProperties lp, boolean afterRoaming) {
        if (!lp.hasGlobalIpv6Address()) return;

        final Inet6Address srcIp = getIpv6LinkLocalAddress(lp);
        if (srcIp == null) return;

        // TODO: add experiment with sending only one gratuitous NA packet instead of one
        // packet per address.
        for (LinkAddress la : lp.getLinkAddresses()) {
            if (!NetworkStackUtils.isIPv6GUA(la)) continue;
            final Inet6Address targetIp = (Inet6Address) la.getAddress();
            // Already sent gratuitous NA with this target global IPv6 address. But for
            // the L2 roaming case, device should always (re)transmit Gratuitous NA for
            // each IPv6 global unicast address respectively after roaming.
            if (!afterRoaming && mGratuitousNaTargetAddresses.contains(targetIp)) continue;
            if (DBG) {
                mLog.log("send Gratuitous NA from " + srcIp.getHostAddress() + " for "
                        + targetIp.getHostAddress() + (afterRoaming ? " after roaming" : ""));
            }
            sendGratuitousNA(srcIp, targetIp);
            if (!afterRoaming) {
                mGratuitousNaTargetAddresses.add(targetIp);
            }
        }
    }

    private void maybeSendGratuitousARP(final LinkProperties lp) {
        for (LinkAddress address : lp.getLinkAddresses()) {
            if (address.getAddress() instanceof Inet4Address) {
                final Inet4Address srcIp = (Inet4Address) address.getAddress();
                if (DBG) {
                    mLog.log("send GARP for " + srcIp.getHostAddress() + " HW address: "
                            + mInterfaceParams.macAddr);
                }
                sendGratuitousARP(srcIp);
            }
        }
    }

    @Nullable
    private static Inet6Address getIPv6DefaultGateway(final LinkProperties lp) {
        for (RouteInfo r : lp.getRoutes()) {
            // TODO: call {@link RouteInfo#isIPv6Default} directly after core networking modules
            // are consolidated.
            if (r.getType() == RTN_UNICAST && r.getDestination().getPrefixLength() == 0
                    && r.getDestination().getAddress() instanceof Inet6Address) {
                // Check if it's IPv6 default route, if yes, return the gateway address
                // (i.e. default router's IPv6 link-local address)
                return (Inet6Address) r.getGateway();
            }
        }
        return null;
    }

    private void maybeSendMulticastNSes(final LinkProperties lp) {
        if (!(lp.hasGlobalIpv6Address() && lp.hasIpv6DefaultRoute())) return;

        // Get the default router's IPv6 link-local address.
        final Inet6Address targetIp = getIPv6DefaultGateway(lp);
        if (targetIp == null) return;
        final Inet6Address dstIp = NetworkStackUtils.ipv6AddressToSolicitedNodeMulticast(targetIp);
        if (dstIp == null) return;

        for (LinkAddress la : lp.getLinkAddresses()) {
            if (!NetworkStackUtils.isIPv6GUA(la)) continue;
            final Inet6Address srcIp = (Inet6Address) la.getAddress();
            if (mMulticastNsSourceAddresses.contains(srcIp)) continue;
            sendMulticastNs(srcIp, dstIp, targetIp);
            mMulticastNsSourceAddresses.add(srcIp);
        }
    }

    private static boolean hasFlag(@NonNull final LinkAddress la, final int flags) {
        return (la.getFlags() & flags) == flags;

    }

    // Check whether a global IPv6 stable address is derived from DHCPv6 prefix delegation.
    // Address derived from delegated prefix should be:
    // - unicast global routable address
    // - with prefix length of 64
    // - has IFA_F_MANAGETEMPADDR, IFA_F_NOPREFIXROUTE and IFA_F_NODAD flags
    private static boolean isIpv6StableDelegatedAddress(@NonNull final LinkAddress la) {
        return la.isIpv6()
                && !ConnectivityUtils.isIPv6ULA(la.getAddress())
                && (la.getPrefixLength() == RFC7421_PREFIX_LENGTH)
                && (la.getScope() == (byte) RT_SCOPE_UNIVERSE)
                && hasFlag(la, DHCPV6_PREFIX_DELEGATION_ADDRESS_FLAGS);
    }

    // Returns false if we have lost provisioning, true otherwise.
    private boolean handleLinkPropertiesUpdate(boolean sendCallbacks) {
        final LinkProperties newLp = assembleLinkProperties();
        if (Objects.equals(newLp, mLinkProperties)) {
            return true;
        }

        // Set an alarm to wait for IPv6 autoconf via SLAAC to succeed after receiving an RA,
        // if we don't see global IPv6 address within timeout then start DHCPv6 Prefix Delegation
        // for provisioning. We cannot just check if there is an available on-link IPv6 DNS server
        // in the LinkProperties, because on-link IPv6 DNS server won't be updated to LP until
        // we have a global IPv6 address via PD. Instead, we have to check if the IPv6 default
        // route exists and start DHCPv6 Prefix Delegation process if IPv6 provisioning still
        // doesn't complete with success after timeout. This check also handles IPv6-only link
        // local mode case, since there will be no IPv6 default route in that mode even with Prefix
        // Delegation experiment flag enabled.
        if (mDhcp6PrefixDelegationEnabled
                && newLp.hasIpv6DefaultRoute()
                && mIpv6AutoconfTimeoutAlarm == null) {
            mIpv6AutoconfTimeoutAlarm = new WakeupMessage(mContext, getHandler(),
                    mTag + ".EVENT_IPV6_AUTOCONF_TIMEOUT", EVENT_IPV6_AUTOCONF_TIMEOUT);
            final long alarmTime = SystemClock.elapsedRealtime()
                    + mDependencies.getDeviceConfigPropertyInt(CONFIG_IPV6_AUTOCONF_TIMEOUT,
                            DEFAULT_IPV6_AUTOCONF_TIMEOUT_MS);
            mIpv6AutoconfTimeoutAlarm.schedule(alarmTime);
        }

        // Check if new assigned IPv6 GUA is available in the LinkProperties now. If so, initiate
        // gratuitous multicast unsolicited Neighbor Advertisements as soon as possible to inform
        // first-hop routers that the new GUA host is goning to use.
        if (isGratuitousNaEnabled()) {
            maybeSendGratuitousNAs(newLp, false /* isGratuitousNaAfterRoaming */);
        }

        // Sending multicast NS from each new assigned IPv6 GUAs to the solicited-node multicast
        // address based on the default router's IPv6 link-local address should trigger default
        // router response with NA, and update the neighbor cache entry immediately, that would
        // help speed up the connection to an IPv6-only network.
        //
        // TODO: stop sending this multicast NS after deployment of RFC9131 in the field, leverage
        // the gratuitous NA to update the first-hop router's neighbor cache entry.
        maybeSendMulticastNSes(newLp);

        // Either success IPv4 or IPv6 provisioning triggers new LinkProperties update,
        // wait for the provisioning completion and record the latency.
        mIpProvisioningMetrics.setIPv4ProvisionedLatencyOnFirstTime(newLp.isIpv4Provisioned());
        mIpProvisioningMetrics.setIPv6ProvisionedLatencyOnFirstTime(newLp.isIpv6Provisioned());

        final int delta = setLinkProperties(newLp);
        // Most of the attributes stored in the memory store are deduced from
        // the link properties, therefore when the properties update the memory
        // store record should be updated too.
        maybeSaveNetworkToIpMemoryStore();
        if (sendCallbacks) {
            dispatchCallback(delta, newLp);
            // We cannot do this along with onProvisioningSuccess callback, because the network
            // can become dual-stack after a success IPv6 provisioning, and the multiplier also
            // needs to be updated upon the loss of IPv4 and/or IPv6 provisioning. The multiplier
            // has been initialized with DTIM_MULTIPLIER_RESET before starting provisioning, it
            // gets updated on the first LinkProperties update (which usually happens when the
            // IPv6 link-local address appears).
            updateMaxDtimMultiplier();
        }
        return (delta != PROV_CHANGE_LOST_PROVISIONING);
    }

    @VisibleForTesting
    static String removeDoubleQuotes(@NonNull String ssid) {
        final int length = ssid.length();
        if ((length > 1) && (ssid.charAt(0) == '"') && (ssid.charAt(length - 1) == '"')) {
            return ssid.substring(1, length - 1);
        }
        return ssid;
    }

    private static List<ByteBuffer> getVendorSpecificIEs(@NonNull ScanResultInfo scanResultInfo) {
        ArrayList<ByteBuffer> vendorSpecificPayloadList = new ArrayList<>();
        for (InformationElement ie : scanResultInfo.getInformationElements()) {
            if (ie.getId() == VENDOR_SPECIFIC_IE_ID) {
                vendorSpecificPayloadList.add(ie.getPayload());
            }
        }
        return vendorSpecificPayloadList;
    }

    private boolean checkIfOuiAndTypeMatched(@NonNull ScanResultInfo scanResultInfo,
            @NonNull List<byte[]> patternList) {
        final List<ByteBuffer> vendorSpecificPayloadList = getVendorSpecificIEs(scanResultInfo);

        for (ByteBuffer payload : vendorSpecificPayloadList) {
            byte[] ouiAndType = new byte[4];
            try {
                payload.get(ouiAndType);
            } catch (BufferUnderflowException e) {
                Log.e(mTag, "Couldn't parse vendor specific IE, buffer underflow");
                return false;
            }
            for (byte[] pattern : patternList) {
                if (Arrays.equals(pattern, ouiAndType)) {
                    if (DBG) {
                        Log.d(mTag, "match pattern: " + HexDump.toHexString(ouiAndType));
                    }
                    return true;
                }
            }
        }
        return false;
    }

    private boolean detectUpstreamHotspotFromVendorIe() {
        final ScanResultInfo scanResultInfo = mConfiguration.mScanResultInfo;
        if (scanResultInfo == null) return false;
        final String ssid = scanResultInfo.getSsid();

        if (mConfiguration.mDisplayName == null
                || !removeDoubleQuotes(mConfiguration.mDisplayName).equals(ssid)) {
            return false;
        }
        return checkIfOuiAndTypeMatched(scanResultInfo, METERED_IE_PATTERN_LIST);
    }

    private void handleIPv4Success(DhcpResults dhcpResults) {
        mDhcpResults = new DhcpResults(dhcpResults);
        final LinkProperties newLp = assembleLinkProperties();
        final int delta = setLinkProperties(newLp);

        if (mDhcpResults.vendorInfo == null && detectUpstreamHotspotFromVendorIe()) {
            mDhcpResults.vendorInfo = DhcpPacket.VENDOR_INFO_ANDROID_METERED;
        }

        if (DBG) {
            Log.d(mTag, "onNewDhcpResults(" + Objects.toString(mDhcpResults) + ")");
            Log.d(mTag, "handleIPv4Success newLp{" + newLp + "}");
        }
        mCallback.onNewDhcpResults(mDhcpResults);
        maybeSaveNetworkToIpMemoryStore();

        dispatchCallback(delta, newLp);
    }

    private void handleIPv4Failure() {
        // TODO: Investigate deleting this clearIPv4Address() call.
        //
        // DhcpClient will send us CMD_CLEAR_LINKADDRESS in all circumstances
        // that could trigger a call to this function. If we missed handling
        // that message in StartedState for some reason we would still clear
        // any addresses upon entry to StoppedState.
        mInterfaceCtrl.clearIPv4Address();
        mDhcpResults = null;
        if (DBG) {
            Log.d(mTag, "onNewDhcpResults(null)");
        }
        mCallback.onNewDhcpResults(null);

        handleProvisioningFailure(DisconnectCode.DC_PROVISIONING_FAIL);
    }

    private void handleProvisioningFailure(final DisconnectCode code) {
        final LinkProperties newLp = assembleLinkProperties();
        int delta = setLinkProperties(newLp);
        // If we've gotten here and we're still not provisioned treat that as
        // a total loss of provisioning.
        //
        // Either (a) static IP configuration failed or (b) DHCPv4 failed AND
        // there was no usable IPv6 obtained before a non-zero provisioning
        // timeout expired.
        //
        // Regardless: GAME OVER.
        if (delta == PROV_CHANGE_STILL_NOT_PROVISIONED) {
            delta = PROV_CHANGE_LOST_PROVISIONING;
        }

        dispatchCallback(delta, newLp);
        if (delta == PROV_CHANGE_LOST_PROVISIONING) {
            transitionToStoppingState(code);
        }
    }

    private void doImmediateProvisioningFailure(int failureType) {
        logError("onProvisioningFailure(): %s", failureType);
        recordMetric(failureType);
        mCallback.onProvisioningFailure(mLinkProperties);
    }

    @SuppressLint("NewApi") // TODO: b/193460475 remove once fixed
    private boolean startIPv4() {
        // If we have a StaticIpConfiguration attempt to apply it and
        // handle the result accordingly.
        if (mConfiguration.mStaticIpConfig != null) {
            if (mInterfaceCtrl.setIPv4Address(mConfiguration.mStaticIpConfig.getIpAddress())) {
                handleIPv4Success(new DhcpResults(mConfiguration.mStaticIpConfig));
            } else {
                return false;
            }
        } else {
            if (mDhcpClient != null) {
                Log.wtf(mTag, "DhcpClient should never be non-null in startIPv4()");
            }
            startDhcpClient();
        }

        return true;
    }

    private boolean shouldDisableDad() {
        return mConfiguration.mUniqueEui64AddressesOnly
                && mConfiguration.mIPv6ProvisioningMode == PROV_IPV6_LINKLOCAL
                && mConfiguration.mIPv6AddrGenMode
                        == ProvisioningConfiguration.IPV6_ADDR_GEN_MODE_EUI64;
    }

    private boolean startIPv6(int acceptRaDefrtr) {
        setIpv6Sysctl(ACCEPT_RA,
                mConfiguration.mIPv6ProvisioningMode == PROV_IPV6_LINKLOCAL ? 0 : 2);
        setIpv6Sysctl(ACCEPT_RA_DEFRTR, acceptRaDefrtr);
        if (shouldDisableDad()) {
            final Integer dadTransmits = getIpv6Sysctl(DAD_TRANSMITS);
            if (dadTransmits != null) {
                mDadTransmits = dadTransmits;
                setIpv6Sysctl(DAD_TRANSMITS, 0 /* dad_transmits */);
            }
        }
        return mInterfaceCtrl.setIPv6PrivacyExtensions(true)
                && mInterfaceCtrl.setIPv6AddrGenModeIfSupported(mConfiguration.mIPv6AddrGenMode)
                && mInterfaceCtrl.enableIPv6();
    }

    private void startDhcp6PrefixDelegation() {
        if (!mDhcp6PrefixDelegationEnabled) return;
        if (mDhcp6Client != null) {
            Log.wtf(mTag, "Dhcp6Client should never be non-null in startDhcp6PrefixDelegation");
            return;
        }
        mDhcp6Client = mDependencies.makeDhcp6Client(mContext, IpClient.this, mInterfaceParams,
                mDependencies.getDhcp6ClientDependencies());
        mDhcp6Client.sendMessage(Dhcp6Client.CMD_START_DHCP6);
    }

    private boolean applyInitialConfig(InitialConfiguration config) {
        // TODO: also support specifying a static IPv4 configuration in InitialConfiguration.
        for (LinkAddress addr : findAll(config.ipAddresses, LinkAddress::isIpv6)) {
            if (!mInterfaceCtrl.addAddress(addr)) return false;
        }

        return true;
    }

    private boolean startIpReachabilityMonitor() {
        try {
            mIpReachabilityMonitor = mDependencies.getIpReachabilityMonitor(
                    mContext,
                    mInterfaceParams,
                    getHandler(),
                    mLog,
                    new IpReachabilityMonitor.Callback() {
                        @Override
                        public void notifyLost(InetAddress ip, String logMsg, NudEventType type) {
                            final int version = mCallback.getInterfaceVersion();
                            if (version >= VERSION_ADDED_REACHABILITY_FAILURE) {
                                final int reason = nudEventTypeToInt(type);
                                if (reason == INVALID_REACHABILITY_LOSS_TYPE) return;
                                final ReachabilityLossInfoParcelable lossInfo =
                                        new ReachabilityLossInfoParcelable(logMsg, reason);
                                mCallback.onReachabilityFailure(lossInfo);
                            } else {
                                mCallback.onReachabilityLost(logMsg);
                            }
                        }
                    },
                    mConfiguration.mUsingMultinetworkPolicyTracker,
                    mDependencies.getIpReachabilityMonitorDeps(mContext, mInterfaceParams.name),
                    mNetd);
        } catch (IllegalArgumentException iae) {
            // Failed to start IpReachabilityMonitor. Log it and call
            // onProvisioningFailure() immediately.
            //
            // See http://b/31038971.
            logError("IpReachabilityMonitor failure: %s", iae);
            mIpReachabilityMonitor = null;
        }

        return (mIpReachabilityMonitor != null);
    }

    private void stopAllIP() {
        // We don't need to worry about routes, just addresses, because:
        //     - disableIpv6() will clear autoconf IPv6 routes as well, and
        //     - we don't get IPv4 routes from netlink
        // so we neither react to nor need to wait for changes in either.
        mInterfaceCtrl.disableIPv6();
        mInterfaceCtrl.clearAllAddresses();

        // Reset IPv6 sysctls to their initial state. It's better to restore
        // sysctls after IPv6 stack is disabled, which prevents a potential
        // race where receiving an RA between restoring accept_ra and disabling
        // IPv6 stack, although it's unlikely.
        setIpv6Sysctl(ACCEPT_RA, 2);
        setIpv6Sysctl(ACCEPT_RA_DEFRTR, 1);
        maybeRestoreDadTransmits();
        if (mUseNewApfFilter && mEnableIpClientIgnoreLowRaLifetime
                && mDependencies.hasIpv6Sysctl(mInterfaceName, ACCEPT_RA_MIN_LFT)) {
            setIpv6Sysctl(ACCEPT_RA_MIN_LFT, 0 /* sysctl default */);
        }
    }

    private void maybeSaveNetworkToIpMemoryStore() {
        // TODO : implement this
    }

    private void maybeRestoreInterfaceMtu() {
        InterfaceParams params = mDependencies.getInterfaceParams(mInterfaceName);
        if (params == null) {
            Log.w(mTag, "interface: " + mInterfaceName + " is gone");
            return;
        }

        // Check whether "mInterfaceParams" is null or not to prevent the potential NPE
        // introduced if the interface was initially not found, but came back before this
        // method was called. See b/162808916 for more details. TODO: query the new interface
        // parameters by the interface index instead and check that the index has not changed.
        if (mInterfaceParams == null || params.index != mInterfaceParams.index) {
            Log.w(mTag, "interface: " + mInterfaceName + " has a different index: " + params.index);
            return;
        }

        if (params.defaultMtu == mInterfaceParams.defaultMtu) return;

        try {
            mNetd.interfaceSetMtu(mInterfaceName, mInterfaceParams.defaultMtu);
        } catch (RemoteException | ServiceSpecificException e) {
            logError("Couldn't reset MTU on " + mInterfaceName + " from "
                    + params.defaultMtu + " to " + mInterfaceParams.defaultMtu, e);
        }
    }

    private void maybeRestoreDadTransmits() {
        if (mDadTransmits == null) return;

        setIpv6Sysctl(DAD_TRANSMITS, mDadTransmits);
        mDadTransmits = null;
    }

    private void handleUpdateL2Information(@NonNull Layer2InformationParcelable info) {
        mL2Key = info.l2Key;
        mCluster = info.cluster;

        // Sometimes the wifi code passes in a null BSSID. Don't use Log.wtf in R because
        // it's a known bug that will not be fixed in R.
        if (info.bssid == null || mCurrentBssid == null) {
            final String msg = "bssid in the parcelable: " + info.bssid + " or "
                    + "current tracked bssid: " + mCurrentBssid + " is null";
            if (ShimUtils.isAtLeastS()) {
                Log.wtf(mTag, msg);
            } else {
                Log.w(mTag, msg);
            }
            return;
        }

        // If the BSSID has not changed, there is nothing to do.
        if (info.bssid.equals(mCurrentBssid)) return;

        // Before trigger probing to the critical neighbors, send Gratuitous ARP
        // and Neighbor Advertisment in advance to propgate host's IPv4/v6 addresses.
        if (isGratuitousArpNaRoamingEnabled()) {
            maybeSendGratuitousARP(mLinkProperties);
            maybeSendGratuitousNAs(mLinkProperties, true /* isGratuitousNaAfterRoaming */);
        }

        // Check whether attempting to refresh previous IP lease on specific networks or need to
        // probe the critical neighbors proactively on L2 roaming happened. The NUD probe on the
        // specific networks is cancelled because otherwise the probe will happen in parallel with
        // DHCP refresh, it will be difficult to understand what happened exactly and error-prone
        // to introduce race condition.
        final String ssid = removeDoubleQuotes(mConfiguration.mDisplayName);
        if (DHCP_ROAMING_SSID_SET.contains(ssid) && mDhcpClient != null) {
            if (DBG) {
                Log.d(mTag, "L2 roaming happened from " + mCurrentBssid
                        + " to " + info.bssid
                        + " , SSID: " + ssid
                        + " , starting refresh leased IP address");
            }
            mDhcpClient.sendMessage(DhcpClient.CMD_REFRESH_LINKADDRESS);
        } else if (mIpReachabilityMonitor != null) {
            mIpReachabilityMonitor.probeAll(true /* dueToRoam */);
        }
        mCurrentBssid = info.bssid;
    }

    @Nullable
    private AndroidPacketFilter maybeCreateApfFilter(final ApfCapabilities apfCapabilities) {
        ApfFilter.ApfConfiguration apfConfig = new ApfFilter.ApfConfiguration();
        apfConfig.apfCapabilities = apfCapabilities;
        apfConfig.multicastFilter = mMulticastFiltering;
        // Get the Configuration for ApfFilter from Context
        // Resource settings were moved from ApfCapabilities APIs to NetworkStack resources in S
        if (ShimUtils.isReleaseOrDevelopmentApiAbove(Build.VERSION_CODES.R)) {
            final Resources res = mContext.getResources();
            apfConfig.ieee802_3Filter = res.getBoolean(R.bool.config_apfDrop802_3Frames);
            apfConfig.ethTypeBlackList = res.getIntArray(R.array.config_apfEthTypeDenyList);
        } else {
            apfConfig.ieee802_3Filter = ApfCapabilities.getApfDrop8023Frames();
            apfConfig.ethTypeBlackList = ApfCapabilities.getApfEtherTypeBlackList();
        }

        apfConfig.minRdnssLifetimeSec = mMinRdnssLifetimeSec;
        // Check the feature flag first before reading IPv6 sysctl, which can prevent from
        // triggering a potential kernel bug about the sysctl.
        // TODO: add unit test to check if the setIpv6Sysctl() is called or not.
        if (mEnableIpClientIgnoreLowRaLifetime && mUseNewApfFilter
                && mDependencies.hasIpv6Sysctl(mInterfaceName, ACCEPT_RA_MIN_LFT)) {
            setIpv6Sysctl(ACCEPT_RA_MIN_LFT, mAcceptRaMinLft);
            final Integer acceptRaMinLft = getIpv6Sysctl(ACCEPT_RA_MIN_LFT);
            apfConfig.acceptRaMinLft = acceptRaMinLft == null ? 0 : acceptRaMinLft;
        } else {
            apfConfig.acceptRaMinLft = 0;
        }
        apfConfig.shouldHandleLightDoze = mApfShouldHandleLightDoze;
        apfConfig.minMetricsSessionDurationMs = mApfCounterPollingIntervalMs;
        return mDependencies.maybeCreateApfFilter(mContext, apfConfig, mInterfaceParams,
                mCallback, mNetworkQuirkMetrics, mUseNewApfFilter);
    }

    private boolean handleUpdateApfCapabilities(@NonNull final ApfCapabilities apfCapabilities) {
        // For the use case where the wifi interface switches from secondary to primary, the
        // secondary interface does not support APF by default see the overlay config about
        // {@link config_wifiEnableApfOnNonPrimarySta}. so we should see empty ApfCapabilities
        // in {@link ProvisioningConfiguration} when wifi starts provisioning on the secondary
        // interface. For other cases, we should not accept the updateApfCapabilities call.
        if (mCurrentApfCapabilities != null || apfCapabilities == null) {
            Log.wtf(mTag, "current ApfCapabilities " + mCurrentApfCapabilities
                    + " is not null or new ApfCapabilities " + apfCapabilities + " is null");
            return false;
        }
        if (mApfFilter != null) {
            mApfFilter.shutdown();
        }
        mCurrentApfCapabilities = apfCapabilities;
        return apfCapabilities != null;
    }

    class StoppedState extends State {
        @Override
        public void enter() {
            stopAllIP();
            mHasDisabledAcceptRaDefrtrOnProvLoss = false;
            mGratuitousNaTargetAddresses.clear();
            mMulticastNsSourceAddresses.clear();
            mDelegatedPrefixes.clear();

            resetLinkProperties();
            if (mStartTimeMillis > 0) {
                // Completed a life-cycle; send a final empty LinkProperties
                // (cleared in resetLinkProperties() above) and record an event.
                mCallback.onLinkPropertiesChange(mLinkProperties);
                recordMetric(IpManagerEvent.COMPLETE_LIFECYCLE);
                mStartTimeMillis = 0;
            }
        }

        @Override
        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case CMD_TERMINATE_AFTER_STOP:
                    stopStateMachineUpdaters();
                    quit();
                    break;

                case CMD_STOP:
                    break;

                case CMD_START:
                    mConfiguration = (android.net.shared.ProvisioningConfiguration) msg.obj;
                    transitionTo(mClearingIpAddressesState);
                    break;

                case EVENT_NETLINK_LINKPROPERTIES_CHANGED:
                    handleLinkPropertiesUpdate(NO_CALLBACKS);
                    break;

                case CMD_UPDATE_TCP_BUFFER_SIZES:
                    mTcpBufferSizes = (String) msg.obj;
                    handleLinkPropertiesUpdate(NO_CALLBACKS);
                    break;

                case CMD_UPDATE_HTTP_PROXY:
                    mHttpProxy = (ProxyInfo) msg.obj;
                    handleLinkPropertiesUpdate(NO_CALLBACKS);
                    break;

                case CMD_UPDATE_L2KEY_CLUSTER: {
                    final Pair<String, String> args = (Pair<String, String>) msg.obj;
                    mL2Key = args.first;
                    mCluster = args.second;
                    break;
                }

                case CMD_SET_MULTICAST_FILTER:
                    mMulticastFiltering = (boolean) msg.obj;
                    break;

                case DhcpClient.CMD_ON_QUIT:
                case Dhcp6Client.CMD_ON_QUIT:
                    // Everything is already stopped.
                    logError("Unexpected CMD_ON_QUIT (already stopped).");
                    break;

                default:
                    return NOT_HANDLED;
            }

            mMsgStateLogger.handled(this, getCurrentState());
            return HANDLED;
        }
    }

    class StoppingState extends State {
        @Override
        public void enter() {
            if (mDhcpClient == null && mDhcp6Client == null) {
                // There's no DHCPv4 as well as DHCPv6 for which to wait; proceed to stopped
                deferMessage(obtainMessage(CMD_JUMP_STOPPING_TO_STOPPED));
            } else {
                if (mDhcpClient != null) {
                    mDhcpClient.sendMessage(DhcpClient.CMD_STOP_DHCP);
                    mDhcpClient.doQuit();
                }
                if (mDhcp6Client != null) {
                    mDhcp6Client.sendMessage(Dhcp6Client.CMD_STOP_DHCP6);
                    mDhcp6Client.doQuit();
                }
            }

            // Restore the interface MTU to initial value if it has changed.
            maybeRestoreInterfaceMtu();
            // Reset DTIM multiplier to default value if changed.
            if (mMaxDtimMultiplier != DTIM_MULTIPLIER_RESET) {
                mCallback.setMaxDtimMultiplier(DTIM_MULTIPLIER_RESET);
                mMaxDtimMultiplier = DTIM_MULTIPLIER_RESET;
                mIPv6ProvisioningDtimGracePeriodMillis = 0;
            }
        }

        @Override
        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case CMD_JUMP_STOPPING_TO_STOPPED:
                    transitionTo(mStoppedState);
                    break;

                case CMD_STOP:
                    break;

                case DhcpClient.CMD_CLEAR_LINKADDRESS:
                    mInterfaceCtrl.clearIPv4Address();
                    break;

                case DhcpClient.CMD_ON_QUIT:
                    mDhcpClient = null;
                    // DhcpClient always starts no matter of target network type, however, we have
                    // to make sure both of DHCPv4 and DHCPv6 client have quit from state machine
                    // before transition to StoppedState, otherwise, we may miss CMD_ON_QUIT cmd
                    // that arrives later and transit to StoppedState before that.
                    if (mDhcp6Client == null) {
                        transitionTo(mStoppedState);
                    }
                    break;

                case Dhcp6Client.CMD_ON_QUIT:
                    mDhcp6Client = null;
                    if (mDhcpClient == null) {
                        transitionTo(mStoppedState);
                    }
                    break;

                default:
                    deferMessage(msg);
            }

            mMsgStateLogger.handled(this, getCurrentState());
            return HANDLED;
        }
    }

    private boolean isUsingPreconnection() {
        return mConfiguration.mEnablePreconnection && mConfiguration.mStaticIpConfig == null;
    }

    /**
     * Check if the customized DHCP client options passed from Wi-Fi are allowed to be put
     * in PRL or in the DHCP packet.
     */
    private List<DhcpOption> maybeFilterCustomizedDhcpOptions() {
        final List<DhcpOption> options = new ArrayList<DhcpOption>();
        if (mConfiguration.mDhcpOptions == null
                || mConfiguration.mScanResultInfo == null) return options; // empty DhcpOption list

        for (DhcpOption option : mConfiguration.mDhcpOptions) {
            final List<byte[]> patternList = DHCP_OPTIONS_ALLOWED.get(option.type);
            // requested option won't be added if no vendor-specific IE oui/type allows this option.
            if (patternList == null) continue;
            if (checkIfOuiAndTypeMatched(mConfiguration.mScanResultInfo, patternList)) {
                options.add(option);
            }
        }
        Collections.sort(options, (o1, o2) ->
                Integer.compare(Byte.toUnsignedInt(o1.type), Byte.toUnsignedInt(o2.type)));
        return options;
    }

    private void startDhcpClient() {
        // Start DHCPv4.
        mDhcpClient = mDependencies.makeDhcpClient(mContext, IpClient.this, mInterfaceParams,
                mDependencies.getDhcpClientDependencies(mIpMemoryStore, mIpProvisioningMetrics));

        // Check if the vendor-specific IE oui/type matches and filters the customized DHCP options.
        final List<DhcpOption> options = maybeFilterCustomizedDhcpOptions();

        // If preconnection is enabled, there is no need to ask Wi-Fi to disable powersaving
        // during DHCP, because the DHCP handshake will happen during association. In order to
        // ensure that future renews still do the DHCP action (if configured),
        // registerForPreDhcpNotification is called later when processing the CMD_*_PRECONNECTION
        // messages.
        if (!isUsingPreconnection()) mDhcpClient.registerForPreDhcpNotification();
        boolean isManagedWifiProfile = false;
        if (mDependencies.getSendDomainSearchListOption(mContext)
                && (mCreatorUid > 0) && (isDeviceOwnerNetwork(mCreatorUid)
                || isProfileOwner(mCreatorUid))) {
            isManagedWifiProfile = true;
        }
        mDhcpClient.sendMessage(DhcpClient.CMD_START_DHCP, new DhcpClient.Configuration(mL2Key,
                isUsingPreconnection(), options, isManagedWifiProfile,
                mConfiguration.mHostnameSetting));
    }

    private boolean hasPermission(String permissionName) {
        return (mContext.checkCallingOrSelfPermission(permissionName)
                == PackageManager.PERMISSION_GRANTED);
    }

    private boolean isDeviceOwnerNetwork(int creatorUid) {
        if (mDevicePolicyManager == null) return false;
        if (!hasPermission(android.Manifest.permission.MANAGE_USERS)) return false;
        final ComponentName devicecmpName = mDevicePolicyManager.getDeviceOwnerComponentOnAnyUser();
        if (devicecmpName == null) return false;
        final String deviceOwnerPackageName = devicecmpName.getPackageName();
        if (deviceOwnerPackageName == null) return false;

        final String[] packages = mContext.getPackageManager().getPackagesForUid(creatorUid);

        for (String pkg : packages) {
            if (pkg.equals(deviceOwnerPackageName)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private Context createPackageContextAsUser(int uid) {
        Context userContext = null;
        try {
            userContext = mContext.createPackageContextAsUser(mContext.getPackageName(), 0,
                    UserHandle.getUserHandleForUid(uid));
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Unknown package name");
            return null;
        }
        return userContext;
    }

    /**
     * Returns the DevicePolicyManager from context
     */
    private DevicePolicyManager retrieveDevicePolicyManagerFromContext(Context context) {
        DevicePolicyManager devicePolicyManager =
                context.getSystemService(DevicePolicyManager.class);
        if (devicePolicyManager == null
                && context.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_DEVICE_ADMIN)) {
            Log.wtf(TAG, "Error retrieving DPM service");
        }
        return devicePolicyManager;
    }

    private DevicePolicyManager retrieveDevicePolicyManagerFromUserContext(int uid) {
        Context userContext = createPackageContextAsUser(uid);
        if (userContext == null) return null;
        return retrieveDevicePolicyManagerFromContext(userContext);
    }

    /**
     * Returns {@code true} if the calling {@code uid} is the profile owner
     *
     */

    private boolean isProfileOwner(int uid) {
        DevicePolicyManager devicePolicyManager = retrieveDevicePolicyManagerFromUserContext(uid);
        if (devicePolicyManager == null) return false;
        String[] packages = mContext.getPackageManager().getPackagesForUid(uid);
        if (packages == null) {
            Log.w(TAG, "isProfileOwner: could not find packages for uid="
                    + uid);
            return false;
        }
        for (String packageName : packages) {
            if (devicePolicyManager.isProfileOwnerApp(packageName)) {
                return true;
            }
        }
        return false;
    }

    class ClearingIpAddressesState extends State {
        @Override
        public void enter() {
            // Ensure that interface parameters are fetched on the handler thread so they are
            // properly ordered with other events, such as restoring the interface MTU on teardown.
            mInterfaceParams = mDependencies.getInterfaceParams(mInterfaceName);
            if (mInterfaceParams == null) {
                logError("Failed to find InterfaceParams for " + mInterfaceName);
                doImmediateProvisioningFailure(IpManagerEvent.ERROR_INTERFACE_NOT_FOUND);
                deferMessage(obtainMessage(CMD_STOP,
                        DisconnectCode.DC_INTERFACE_NOT_FOUND.getNumber()));
                return;
            }

            mLinkObserver.setInterfaceParams(mInterfaceParams);

            if (readyToProceed()) {
                deferMessage(obtainMessage(CMD_ADDRESSES_CLEARED));
            } else {
                // Clear all IPv4 and IPv6 before proceeding to RunningState.
                // Clean up any leftover state from an abnormal exit from
                // tethering or during an IpClient restart.
                stopAllIP();
            }

            mCallback.setNeighborDiscoveryOffload(true);
        }

        @Override
        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case CMD_ADDRESSES_CLEARED:
                    transitionTo(isUsingPreconnection() ? mPreconnectingState : mRunningState);
                    break;

                case EVENT_NETLINK_LINKPROPERTIES_CHANGED:
                    handleLinkPropertiesUpdate(NO_CALLBACKS);
                    if (readyToProceed()) {
                        transitionTo(isUsingPreconnection() ? mPreconnectingState : mRunningState);
                    }
                    break;

                case CMD_STOP:
                case EVENT_PROVISIONING_TIMEOUT:
                    // Fall through to StartedState.
                    return NOT_HANDLED;

                default:
                    // It's safe to process messages out of order because the
                    // only message that can both
                    //     a) be received at this time and
                    //     b) affect provisioning state
                    // is EVENT_NETLINK_LINKPROPERTIES_CHANGED (handled above).
                    deferMessage(msg);
            }
            return HANDLED;
        }

        private boolean readyToProceed() {
            return !mLinkProperties.hasIpv4Address() && !mLinkProperties.hasGlobalIpv6Address();
        }
    }

    class PreconnectingState extends State {
        @Override
        public void enter() {
            startDhcpClient();
        }

        @Override
        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case CMD_COMPLETE_PRECONNECTION:
                    boolean success = (msg.arg1 == 1);
                    mDhcpClient.registerForPreDhcpNotification();
                    if (!success) {
                        mDhcpClient.sendMessage(DhcpClient.CMD_ABORT_PRECONNECTION);
                    }
                    // The link is ready for use. Advance to running state, start IPv6, etc.
                    transitionTo(mRunningState);
                    break;

                case DhcpClient.CMD_START_PRECONNECTION:
                    final Layer2PacketParcelable l2Packet = (Layer2PacketParcelable) msg.obj;
                    mCallback.onPreconnectionStart(Collections.singletonList(l2Packet));
                    break;

                case CMD_STOP:
                case EVENT_PROVISIONING_TIMEOUT:
                    // Fall through to StartedState.
                    return NOT_HANDLED;

                default:
                    deferMessage(msg);
            }
            return HANDLED;
        }
    }

    class StartedState extends State {
        @Override
        public void enter() {
            mIpProvisioningMetrics.reset();
            mStartTimeMillis = SystemClock.elapsedRealtime();

            if (mConfiguration.mProvisioningTimeoutMs > 0) {
                final long alarmTime = SystemClock.elapsedRealtime()
                        + mConfiguration.mProvisioningTimeoutMs;
                mProvisioningTimeoutAlarm.schedule(alarmTime);
            }

            // There is no need to temporarlily lower the DTIM multiplier in IPv6 link-local
            // only mode or when IPv6 is disabled.
            if (mConfiguration.mIPv6ProvisioningMode == PROV_IPV6_SLAAC) {
                // Send a delay message to wait for IP provisioning to complete eventually and
                // set the specific DTIM multiplier by checking the target network type.
                final int delay = mDependencies.getDeviceConfigPropertyInt(
                        CONFIG_INITIAL_PROVISIONING_DTIM_DELAY_MS,
                        DEFAULT_INITIAL_PROVISIONING_DTIM_DELAY_MS);
                mIPv6ProvisioningDtimGracePeriodMillis = mStartTimeMillis + delay;
                sendMessageDelayed(CMD_SET_DTIM_MULTIPLIER_AFTER_DELAY, delay);
            }
        }

        @Override
        public void exit() {
            mProvisioningTimeoutAlarm.cancel();
            mCurrentApfCapabilities = null;

            // Record metrics information once this provisioning has completed due to certain
            // reason (normal termination, provisioning timeout, lost provisioning and etc).
            mIpProvisioningMetrics.statsWrite();
        }

        @Override
        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case CMD_STOP:
                    transitionToStoppingState(DisconnectCode.forNumber(msg.arg1));
                    break;

                case CMD_UPDATE_L2KEY_CLUSTER: {
                    final Pair<String, String> args = (Pair<String, String>) msg.obj;
                    mL2Key = args.first;
                    mCluster = args.second;
                    // TODO : attributes should be saved to the memory store with
                    // these new values if they differ from the previous ones.
                    // If the state machine is in pure StartedState, then the values to input
                    // are not known yet and should be updated when the LinkProperties are updated.
                    // If the state machine is in RunningState (which is a child of StartedState)
                    // then the next NUD check should be used to store the new values to avoid
                    // inputting current values for what may be a different L3 network.
                    break;
                }

                case CMD_UPDATE_L2INFORMATION:
                    handleUpdateL2Information((Layer2InformationParcelable) msg.obj);
                    break;

                // Only update the current ApfCapabilities but do not create and start APF
                // filter until transition to RunningState, actually we should always do that
                // in RunningState.
                case CMD_UPDATE_APF_CAPABILITIES:
                    handleUpdateApfCapabilities((ApfCapabilities) msg.obj);
                    break;

                case EVENT_PROVISIONING_TIMEOUT:
                    handleProvisioningFailure(DisconnectCode.DC_PROVISIONING_TIMEOUT);
                    break;

                default:
                    return NOT_HANDLED;
            }

            mMsgStateLogger.handled(this, getCurrentState());
            return HANDLED;
        }
    }

    private boolean isIpv6Enabled() {
        return mConfiguration.mIPv6ProvisioningMode != PROV_IPV6_DISABLED;
    }

    private boolean isIpv4Enabled() {
        return mConfiguration.mIPv4ProvisioningMode != PROV_IPV4_DISABLED;
    }

    class RunningState extends State {
        private ConnectivityPacketTracker mPacketTracker;
        private boolean mDhcpActionInFlight;

        @Override
        public void enter() {
            mApfFilter = maybeCreateApfFilter(mCurrentApfCapabilities);
            // TODO: investigate the effects of any multicast filtering racing/interfering with the
            // rest of this IP configuration startup.
            if (mApfFilter == null) {
                mCallback.setFallbackMulticastFilter(mMulticastFiltering);
            }
            if (mEnableApfPollingCounters) {
                sendMessageDelayed(CMD_UPDATE_APF_DATA_SNAPSHOT, mApfCounterPollingIntervalMs);
            }

            mPacketTracker = createPacketTracker();
            if (mPacketTracker != null) mPacketTracker.start(mConfiguration.mDisplayName);

            if (isIpv6Enabled() && !startIPv6(1 /* acceptRaDefrtr */)) {
                doImmediateProvisioningFailure(IpManagerEvent.ERROR_STARTING_IPV6);
                enqueueJumpToStoppingState(DisconnectCode.DC_ERROR_STARTING_IPV6);
                return;
            }

            if (isIpv4Enabled() && !isUsingPreconnection() && !startIPv4()) {
                doImmediateProvisioningFailure(IpManagerEvent.ERROR_STARTING_IPV4);
                enqueueJumpToStoppingState(DisconnectCode.DC_ERROR_STARTING_IPV4);
                return;
            }

            final InitialConfiguration config = mConfiguration.mInitialConfig;
            if ((config != null) && !applyInitialConfig(config)) {
                // TODO introduce a new IpManagerEvent constant to distinguish this error case.
                doImmediateProvisioningFailure(IpManagerEvent.ERROR_INVALID_PROVISIONING);
                enqueueJumpToStoppingState(DisconnectCode.DC_INVALID_PROVISIONING);
                return;
            }

            if (mConfiguration.mUsingIpReachabilityMonitor && !startIpReachabilityMonitor()) {
                doImmediateProvisioningFailure(
                        IpManagerEvent.ERROR_STARTING_IPREACHABILITYMONITOR);
                enqueueJumpToStoppingState(DisconnectCode.DC_ERROR_STARTING_IPREACHABILITYMONITOR);
                return;
            }
        }

        @Override
        public void exit() {
            stopDhcpAction();

            if (mIpv6AutoconfTimeoutAlarm != null) {
                mIpv6AutoconfTimeoutAlarm.cancel();
                mIpv6AutoconfTimeoutAlarm = null;
            }

            if (mIpReachabilityMonitor != null) {
                mIpReachabilityMonitor.stop();
                mIpReachabilityMonitor = null;
            }

            if (mPacketTracker != null) {
                mPacketTracker.stop();
                mPacketTracker = null;
            }

            if (mApfFilter != null) {
                mApfFilter.shutdown();
                mApfFilter = null;
            }

            resetLinkProperties();

            removeMessages(CMD_UPDATE_APF_DATA_SNAPSHOT);
        }

        private void enqueueJumpToStoppingState(final DisconnectCode code) {
            deferMessage(obtainMessage(CMD_JUMP_RUNNING_TO_STOPPING, code.getNumber()));
        }

        private ConnectivityPacketTracker createPacketTracker() {
            try {
                return new ConnectivityPacketTracker(
                        getHandler(), mInterfaceParams, mConnectivityPacketLog);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        private void ensureDhcpAction() {
            if (!mDhcpActionInFlight) {
                mCallback.onPreDhcpAction();
                mDhcpActionInFlight = true;
                final long alarmTime = SystemClock.elapsedRealtime()
                        + mConfiguration.mRequestedPreDhcpActionMs;
                mDhcpActionTimeoutAlarm.schedule(alarmTime);
            }
        }

        private void stopDhcpAction() {
            mDhcpActionTimeoutAlarm.cancel();
            if (mDhcpActionInFlight) {
                mCallback.onPostDhcpAction();
                mDhcpActionInFlight = false;
            }
        }

        private void deleteIpv6PrefixDelegationAddresses(final IpPrefix prefix) {
            for (LinkAddress la : mLinkProperties.getLinkAddresses()) {
                final InetAddress address = la.getAddress();
                if (prefix.contains(address)) {
                    if (!NetlinkUtils.sendRtmDelAddressRequest(mInterfaceParams.index,
                            (Inet6Address) address, (short) la.getPrefixLength())) {
                        Log.e(TAG, "Failed to delete IPv6 address " + address.getHostAddress());
                    }
                }
            }
        }

        private void addInterfaceAddress(@Nullable final Inet6Address address,
                @NonNull final IaPrefixOption ipo) {
            final int flags = IFA_F_NOPREFIXROUTE | IFA_F_MANAGETEMPADDR | IFA_F_NODAD;
            final long now = SystemClock.elapsedRealtime();
            // Per RFC8415 section 21.22 the preferred/valid lifetime in IA Prefix option
            // expressed in units of seconds.
            final long deprecationTime = now + ipo.preferred * 1000;
            final long expirationTime = now + ipo.valid * 1000;
            final LinkAddress la;
            try {
                la = new LinkAddress(address, RFC7421_PREFIX_LENGTH, flags,
                        RT_SCOPE_UNIVERSE /* scope */, deprecationTime, expirationTime);
            } catch (IllegalArgumentException e) {
                Log.e(TAG, "Invalid IPv6 link address " + e);
                return;
            }
            if (!la.isGlobalPreferred()) {
                Log.w(TAG, la + " is not a global IPv6 address");
                return;
            }
            if (!NetlinkUtils.sendRtmNewAddressRequest(mInterfaceParams.index, address,
                    (short) RFC7421_PREFIX_LENGTH,
                    flags, (byte) RT_SCOPE_UNIVERSE /* scope */,
                    ipo.preferred, ipo.valid)) {
                Log.e(TAG, "Failed to set IPv6 address on " + address.getHostAddress()
                        + "%" + mInterfaceParams.index);
            }
        }

        private void updateDelegatedAddresses(@NonNull final List<IaPrefixOption> valid) {
            if (valid.isEmpty()) return;
            final List<IpPrefix> zeroLifetimePrefixList = new ArrayList<>();
            for (IaPrefixOption ipo : valid) {
                final IpPrefix prefix = ipo.getIpPrefix();
                // The prefix with preferred/valid lifetime of 0 is considered as a valid prefix,
                // and can be passed to IpClient from Dhcp6Client, but client should stop using
                // the global addresses derived from this prefix asap. Deleting the associated
                // global IPv6 addresses immediately before adding another IPv6 address may result
                // in a race where the device throws the provisioning failure callback due to the
                // loss of all valid IPv6 addresses, however, IPv6 provisioning will soon complete
                // successfully when the user space sees the new IPv6 address update. To avoid this
                // race, temporarily store all prefix(es) with 0 preferred/valid lifetime and then
                // delete them after iterating through all valid IA prefix options.
                if (ipo.withZeroLifetimes()) {
                    zeroLifetimePrefixList.add(prefix);
                    continue;
                }
                // Otherwise, configure IPv6 addresses derived from the delegated prefix(es) on
                // the interface. We've checked that delegated prefix is valid upon receiving the
                // response from DHCPv6 server, and the server may assign a prefix with length less
                // than 64. So for SLAAC use case we always set the prefix length to 64 even if the
                // delegated prefix length is less than 64.
                final Inet6Address address = createInet6AddressFromEui64(prefix,
                        macAddressToEui64(mInterfaceParams.macAddr));
                addInterfaceAddress(address, ipo);
            }

            // Delete global IPv6 addresses derived from prefix with 0 preferred/valid lifetime.
            if (!zeroLifetimePrefixList.isEmpty()) {
                for (IpPrefix prefix : zeroLifetimePrefixList) {
                    Log.d(TAG, "Delete IPv6 address derived from prefix " + prefix
                            + " with 0 preferred/valid lifetime");
                    deleteIpv6PrefixDelegationAddresses(prefix);
                }
            }
        }

        private void removeExpiredDelegatedAddresses(@NonNull final List<IaPrefixOption> expired) {
            if (expired.isEmpty()) return;
            for (IaPrefixOption ipo : expired) {
                final IpPrefix prefix = ipo.getIpPrefix();
                Log.d(TAG, "Delete IPv6 address derived from expired prefix " + prefix);
                deleteIpv6PrefixDelegationAddresses(prefix);
            }
        }

        @Override
        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case CMD_JUMP_RUNNING_TO_STOPPING:
                case CMD_STOP:
                    transitionToStoppingState(DisconnectCode.forNumber(msg.arg1));
                    break;

                case CMD_START:
                    logError("ALERT: START received in StartedState. Please fix caller.");
                    break;

                case CMD_CONFIRM:
                    // TODO: Possibly introduce a second type of confirmation
                    // that both probes (a) on-link neighbors and (b) does
                    // a DHCPv4 RENEW.  We used to do this on Wi-Fi framework
                    // roams.
                    if (mIpReachabilityMonitor != null) {
                        mIpReachabilityMonitor.probeAll(false /* dueToRoam */);
                    }
                    break;

                case EVENT_PRE_DHCP_ACTION_COMPLETE:
                    // It's possible to reach here if, for example, someone
                    // calls completedPreDhcpAction() after provisioning with
                    // a static IP configuration.
                    if (mDhcpClient != null) {
                        mDhcpClient.sendMessage(DhcpClient.CMD_PRE_DHCP_ACTION_COMPLETE);
                    }
                    break;

                case EVENT_NETLINK_LINKPROPERTIES_CHANGED:
                    // EVENT_NETLINK_LINKPROPERTIES_CHANGED message will be received in both of
                    // provisioning loss and normal user termination cases (e.g. turn off wifi or
                    // switch to another wifi ssid), hence, checking the current interface link
                    // state (down or up) helps distinguish the two cases: if the link state is
                    // down, provisioning is only lost because the link is being torn down (for
                    // example when turning off wifi), so treat it as a normal termination.
                    if (!handleLinkPropertiesUpdate(SEND_CALLBACKS)) {
                        final boolean linkStateUp = (msg.arg1 == ARG_LINKPROP_CHANGED_LINKSTATE_UP);
                        transitionToStoppingState(linkStateUp ? DisconnectCode.DC_PROVISIONING_FAIL
                                : DisconnectCode.DC_NORMAL_TERMINATION);
                    }
                    break;

                case CMD_UPDATE_TCP_BUFFER_SIZES:
                    mTcpBufferSizes = (String) msg.obj;
                    // This cannot possibly change provisioning state.
                    handleLinkPropertiesUpdate(SEND_CALLBACKS);
                    break;

                case CMD_UPDATE_HTTP_PROXY:
                    mHttpProxy = (ProxyInfo) msg.obj;
                    // This cannot possibly change provisioning state.
                    handleLinkPropertiesUpdate(SEND_CALLBACKS);
                    break;

                case CMD_SET_MULTICAST_FILTER: {
                    mMulticastFiltering = (boolean) msg.obj;
                    if (mApfFilter != null) {
                        mApfFilter.setMulticastFilter(mMulticastFiltering);
                    } else {
                        mCallback.setFallbackMulticastFilter(mMulticastFiltering);
                    }
                    updateMaxDtimMultiplier();
                    break;
                }

                case EVENT_READ_PACKET_FILTER_COMPLETE: {
                    if (mApfFilter != null) {
                        mApfFilter.setDataSnapshot((byte[]) msg.obj);
                    }
                    mApfDataSnapshotComplete.open();
                    break;
                }

                case CMD_ADD_KEEPALIVE_PACKET_FILTER_TO_APF: {
                    final int slot = msg.arg1;

                    if (mApfFilter != null) {
                        if (msg.obj instanceof NattKeepalivePacketDataParcelable) {
                            mApfFilter.addNattKeepalivePacketFilter(slot,
                                    (NattKeepalivePacketDataParcelable) msg.obj);
                        } else if (msg.obj instanceof TcpKeepalivePacketDataParcelable) {
                            mApfFilter.addTcpKeepalivePacketFilter(slot,
                                    (TcpKeepalivePacketDataParcelable) msg.obj);
                        }
                    }
                    break;
                }

                case CMD_REMOVE_KEEPALIVE_PACKET_FILTER_FROM_APF: {
                    final int slot = msg.arg1;
                    if (mApfFilter != null) {
                        mApfFilter.removeKeepalivePacketFilter(slot);
                    }
                    break;
                }

                case EVENT_DHCPACTION_TIMEOUT:
                    stopDhcpAction();
                    break;

                case EVENT_IPV6_AUTOCONF_TIMEOUT:
                    // Only enable DHCPv6 PD on networks that support IPv6 but not autoconf. The
                    // right way to do it is to use the P flag, once it's defined. For now, assume
                    // that the network doesn't support autoconf if it provides an IPv6 default
                    // route but no addresses via an RA.
                    // TODO: leverage the P flag in RA to determine if starting DHCPv6 PD or not,
                    // which is more clear and straightforward.
                    if (!hasIpv6Address(mLinkProperties)
                            && mLinkProperties.hasIpv6DefaultRoute()) {
                        Log.d(TAG, "Network supports IPv6 but not autoconf, starting DHCPv6 PD");
                        startDhcp6PrefixDelegation();
                    }
                    break;

                case DhcpClient.CMD_PRE_DHCP_ACTION:
                    if (mConfiguration.mRequestedPreDhcpActionMs > 0) {
                        ensureDhcpAction();
                    } else {
                        sendMessage(EVENT_PRE_DHCP_ACTION_COMPLETE);
                    }
                    break;

                case DhcpClient.CMD_CLEAR_LINKADDRESS:
                    mInterfaceCtrl.clearIPv4Address();
                    break;

                case DhcpClient.CMD_CONFIGURE_LINKADDRESS: {
                    final LinkAddress ipAddress = (LinkAddress) msg.obj;
                    final boolean success;
                    if (mPopulateLinkAddressLifetime) {
                        // For IPv4 link addresses, there is no concept of preferred/valid
                        // lifetimes. Populate the ifa_cacheinfo attribute in the netlink
                        // message with the DHCP lease duration, which is used by the kernel
                        // to maintain the validity of the IP addresses.
                        final int leaseDuration = msg.arg1;
                        success = NetlinkUtils.sendRtmNewAddressRequest(mInterfaceParams.index,
                                ipAddress.getAddress(),
                                (short) ipAddress.getPrefixLength(),
                                0 /* flags */,
                                (byte) RT_SCOPE_UNIVERSE /* scope */,
                                leaseDuration /* preferred */,
                                leaseDuration /* valid */);
                    } else {
                        success = mInterfaceCtrl.setIPv4Address(ipAddress);
                    }
                    if (success) {
                        // Although it's impossible to happen that DHCP client becomes null in
                        // RunningState and then NPE is thrown when it attempts to send a message
                        // on an null object, sometimes it's found during stress tests. If this
                        // issue does happen, log the terrible failure, that would be helpful to
                        // see how often this case occurs on fields and the log trace would be
                        // also useful for debugging(see b/203174383).
                        if (mDhcpClient == null) {
                            Log.wtf(mTag, "DhcpClient should never be null in RunningState.");
                        }
                        mDhcpClient.sendMessage(DhcpClient.EVENT_LINKADDRESS_CONFIGURED);
                    } else {
                        logError("Failed to set IPv4 address.");
                        dispatchCallback(PROV_CHANGE_LOST_PROVISIONING, mLinkProperties);
                        transitionToStoppingState(DisconnectCode.DC_PROVISIONING_FAIL);
                    }
                    break;
                }

                // This message is only received when:
                //
                //     a) initial address acquisition succeeds,
                //     b) renew succeeds or is NAK'd,
                //     c) rebind succeeds or is NAK'd, or
                //     d) the lease expires, or
                //     e) the IPv6-only preferred option is enabled and entering Ipv6OnlyWaitState.
                //
                // but never when initial address acquisition fails. The latter
                // condition is now governed by the provisioning timeout.
                case DhcpClient.CMD_POST_DHCP_ACTION:
                    stopDhcpAction();

                    switch (msg.arg1) {
                        case DhcpClient.DHCP_SUCCESS:
                            handleIPv4Success((DhcpResults) msg.obj);
                            break;
                        case DhcpClient.DHCP_FAILURE:
                            handleIPv4Failure();
                            break;
                        case DhcpClient.DHCP_IPV6_ONLY:
                            break;
                        case DhcpClient.DHCP_REFRESH_FAILURE:
                            // This case should only happen on the receipt of DHCPNAK when
                            // refreshing IP address post L2 roaming on some specific networks.
                            // WiFi should try to restart a new provisioning immediately without
                            // disconnecting L2 when it receives DHCP roaming failure event. IPv4
                            // link address still will be cleared when DhcpClient transits to
                            // StoppedState from RefreshingAddress State, although it will result
                            // in a following onProvisioningFailure then, WiFi should ignore this
                            // failure and start a new DHCP reconfiguration from INIT state.
                            final ReachabilityLossInfoParcelable lossInfo =
                                    new ReachabilityLossInfoParcelable("DHCP refresh failure",
                                            ReachabilityLossReason.ROAM);
                            mCallback.onReachabilityFailure(lossInfo);
                            break;
                        default:
                            logError("Unknown CMD_POST_DHCP_ACTION status: %s", msg.arg1);
                    }
                    break;

                case Dhcp6Client.CMD_DHCP6_RESULT:
                    switch(msg.arg1) {
                        case Dhcp6Client.DHCP6_PD_SUCCESS:
                            final List<IaPrefixOption> toBeUpdated = (List<IaPrefixOption>) msg.obj;
                            updateDelegatedAddresses(toBeUpdated);
                            handleLinkPropertiesUpdate(SEND_CALLBACKS);
                            break;

                        case Dhcp6Client.DHCP6_PD_PREFIX_EXPIRED:
                            final List<IaPrefixOption> toBeRemoved = (List<IaPrefixOption>) msg.obj;
                            removeExpiredDelegatedAddresses(toBeRemoved);
                            handleLinkPropertiesUpdate(SEND_CALLBACKS);
                            break;

                        default:
                            logError("Unknown CMD_DHCP6_RESULT status: %s", msg.arg1);
                    }
                    break;

                case DhcpClient.CMD_ON_QUIT:
                    // DHCPv4 quit early for some reason.
                    logError("Unexpected CMD_ON_QUIT from DHCPv4.");
                    mDhcpClient = null;
                    break;

                case Dhcp6Client.CMD_ON_QUIT:
                    // DHCPv6 quit early for some reason.
                    logError("Unexpected CMD_ON_QUIT from DHCPv6.");
                    mDhcp6Client = null;
                    break;

                case CMD_SET_DTIM_MULTIPLIER_AFTER_DELAY:
                    updateMaxDtimMultiplier();
                    break;

                case CMD_UPDATE_APF_CAPABILITIES:
                    final ApfCapabilities apfCapabilities = (ApfCapabilities) msg.obj;
                    if (handleUpdateApfCapabilities(apfCapabilities)) {
                        mApfFilter = maybeCreateApfFilter(apfCapabilities);
                    }
                    break;

                case CMD_UPDATE_APF_DATA_SNAPSHOT:
                    mCallback.startReadPacketFilter();
                    sendMessageDelayed(CMD_UPDATE_APF_DATA_SNAPSHOT, mApfCounterPollingIntervalMs);
                    break;

                default:
                    return NOT_HANDLED;
            }

            mMsgStateLogger.handled(this, getCurrentState());
            return HANDLED;
        }
    }

    /**
     * Set the maximum DTIM multiplier to hardware driver per network condition. Any multiplier
     * larger than the maximum value must not be accepted, it will cause packet loss higher than
     * what the system can accept, which will cause unexpected behavior for apps, and may interrupt
     * the network connection.
     *
     * When Wifi STA is in the power saving mode and the system is suspended, the wakeup interval
     * will be set to:
     *    1) multiplier * AP's DTIM period if multiplier > 0.
     *    2) the driver default value if multiplier <= 0.
     * Some implementations may apply an additional cap to wakeup interval in the case of 1).
     */
    private void updateMaxDtimMultiplier() {
        int multiplier = deriveDtimMultiplier();
        if (mMaxDtimMultiplier == multiplier) return;

        mMaxDtimMultiplier = multiplier;
        log("set max DTIM multiplier to " + multiplier);
        mCallback.setMaxDtimMultiplier(multiplier);
    }

    /**
     * Check if current LinkProperties has either global IPv6 address or ULA (i.e. non IPv6
     * link-local addres).
     *
     * This function can be used to derive the DTIM multiplier per current network situation or
     * decide if we should start DHCPv6 Prefix Delegation when no IPv6 addresses are available
     * after autoconf timeout(5s).
     */
    private static boolean hasIpv6Address(@NonNull final LinkProperties lp) {
        return CollectionUtils.any(lp.getLinkAddresses(),
                la -> {
                    final InetAddress address = la.getAddress();
                    return (address instanceof Inet6Address) && !address.isLinkLocalAddress();
                });
    }

    private int deriveDtimMultiplier() {
        final boolean hasIpv4Addr = mLinkProperties.hasIpv4Address();
        // For a host in the network that has only ULA and link-local but no GUA, consider
        // that it also has IPv6 connectivity. LinkProperties#isIpv6Provisioned only returns
        // true when it has a GUA, so we cannot use it for IPv6-only network case.
        final boolean hasIpv6Addr = hasIpv6Address(mLinkProperties);

        final int multiplier;
        if (!mMulticastFiltering) {
            multiplier = mDependencies.getDeviceConfigPropertyInt(
                    CONFIG_MULTICAST_LOCK_MAX_DTIM_MULTIPLIER,
                    DEFAULT_MULTICAST_LOCK_MAX_DTIM_MULTIPLIER);
        } else if (!hasIpv6Addr
                && (SystemClock.elapsedRealtime() < mIPv6ProvisioningDtimGracePeriodMillis)) {
            // IPv6 provisioning may or may not complete soon in the future, we don't know when
            // it will complete, however, setting multiplier to a high value will cause higher
            // RA packet loss, that increases the overall IPv6 provisioning latency. So just set
            // multiplier to 1 before device gains the IPv6 provisioning, make sure device won't
            // miss any RA packet later.
            multiplier = mDependencies.getDeviceConfigPropertyInt(
                    CONFIG_BEFORE_IPV6_PROV_MAX_DTIM_MULTIPLIER,
                    DEFAULT_BEFORE_IPV6_PROV_MAX_DTIM_MULTIPLIER);
        } else if (hasIpv6Addr && !hasIpv4Addr) {
            multiplier = mDependencies.getDeviceConfigPropertyInt(
                    CONFIG_IPV6_ONLY_NETWORK_MAX_DTIM_MULTIPLIER,
                    DEFAULT_IPV6_ONLY_NETWORK_MAX_DTIM_MULTIPLIER);
        } else if (hasIpv4Addr && !hasIpv6Addr) {
            multiplier = mDependencies.getDeviceConfigPropertyInt(
                    CONFIG_IPV4_ONLY_NETWORK_MAX_DTIM_MULTIPLIER,
                    DEFAULT_IPV4_ONLY_NETWORK_MAX_DTIM_MULTIPLIER);
        } else if (hasIpv6Addr && hasIpv4Addr) {
            multiplier = mDependencies.getDeviceConfigPropertyInt(
                    CONFIG_DUAL_STACK_MAX_DTIM_MULTIPLIER,
                    DEFAULT_DUAL_STACK_MAX_DTIM_MULTIPLIER);
        } else {
            multiplier = DTIM_MULTIPLIER_RESET;
        }
        return multiplier;
    }

    private static class MessageHandlingLogger {
        public String processedInState;
        public String receivedInState;

        public void reset() {
            processedInState = null;
            receivedInState = null;
        }

        public void handled(State processedIn, IState receivedIn) {
            processedInState = processedIn.getClass().getSimpleName();
            receivedInState = receivedIn.getName();
        }

        public String toString() {
            return String.format("rcvd_in=%s, proc_in=%s",
                                 receivedInState, processedInState);
        }
    }

    // TODO: extract out into CollectionUtils.
    static <T> boolean any(Iterable<T> coll, Predicate<T> fn) {
        for (T t : coll) {
            if (fn.test(t)) {
                return true;
            }
        }
        return false;
    }

    static <T> boolean all(Iterable<T> coll, Predicate<T> fn) {
        return !any(coll, not(fn));
    }

    static <T> Predicate<T> not(Predicate<T> fn) {
        return (t) -> !fn.test(t);
    }

    static <T> String join(String delimiter, Collection<T> coll) {
        return coll.stream().map(Object::toString).collect(Collectors.joining(delimiter));
    }

    static <T> T find(Iterable<T> coll, Predicate<T> fn) {
        for (T t: coll) {
            if (fn.test(t)) {
                return t;
            }
        }
        return null;
    }

    static <T> List<T> findAll(Collection<T> coll, Predicate<T> fn) {
        return coll.stream().filter(fn).collect(Collectors.toList());
    }
}
