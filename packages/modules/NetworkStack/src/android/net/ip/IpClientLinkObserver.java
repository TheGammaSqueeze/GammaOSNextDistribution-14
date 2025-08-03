/*
 * Copyright (C) 2014 The Android Open Source Project
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

import static android.system.OsConstants.AF_INET6;
import static android.system.OsConstants.AF_UNSPEC;
import static android.system.OsConstants.IFF_LOOPBACK;

import static com.android.net.module.util.NetworkStackConstants.ICMPV6_ROUTER_ADVERTISEMENT;
import static com.android.net.module.util.NetworkStackConstants.INFINITE_LEASE;
import static com.android.net.module.util.netlink.NetlinkConstants.IFF_LOWER_UP;
import static com.android.net.module.util.netlink.NetlinkConstants.RTM_F_CLONED;
import static com.android.net.module.util.netlink.NetlinkConstants.RTN_UNICAST;
import static com.android.net.module.util.netlink.NetlinkConstants.RTPROT_KERNEL;
import static com.android.net.module.util.netlink.NetlinkConstants.RTPROT_RA;
import static com.android.net.module.util.netlink.NetlinkConstants.RT_SCOPE_UNIVERSE;
import static com.android.networkstack.util.NetworkStackUtils.IPCLIENT_ACCEPT_IPV6_LINK_LOCAL_DNS_VERSION;
import static com.android.networkstack.util.NetworkStackUtils.IPCLIENT_PARSE_NETLINK_EVENTS_FORCE_DISABLE;

import android.app.AlarmManager;
import android.content.Context;
import android.net.InetAddresses;
import android.net.IpPrefix;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.RouteInfo;
import android.os.Handler;
import android.os.SystemClock;
import android.system.OsConstants;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.internal.annotations.VisibleForTesting;
import com.android.net.module.util.HexDump;
import com.android.net.module.util.InetAddressUtils;
import com.android.net.module.util.InterfaceParams;
import com.android.net.module.util.SharedLog;
import com.android.net.module.util.ip.NetlinkMonitor;
import com.android.net.module.util.netlink.NduseroptMessage;
import com.android.net.module.util.netlink.NetlinkConstants;
import com.android.net.module.util.netlink.NetlinkMessage;
import com.android.net.module.util.netlink.RtNetlinkAddressMessage;
import com.android.net.module.util.netlink.RtNetlinkLinkMessage;
import com.android.net.module.util.netlink.RtNetlinkRouteMessage;
import com.android.net.module.util.netlink.StructIfacacheInfo;
import com.android.net.module.util.netlink.StructIfaddrMsg;
import com.android.net.module.util.netlink.StructIfinfoMsg;
import com.android.net.module.util.netlink.StructNdOptPref64;
import com.android.net.module.util.netlink.StructNdOptRdnss;
import com.android.networkstack.apishim.NetworkInformationShimImpl;
import com.android.networkstack.apishim.common.NetworkInformationShim;
import com.android.server.NetworkObserver;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Keeps track of link configuration received from Netd.
 *
 * An instance of this class is constructed by passing in an interface name and a callback. The
 * owner is then responsible for registering the tracker with NetworkObserverRegistry. When the
 * class receives update notifications, it applies the update to its local LinkProperties, and if
 * something has changed, notifies its owner of the update via the callback.
 *
 * The owner can then call {@code getLinkProperties()} in order to find out
 * what changed. If in the meantime the LinkProperties stored here have changed,
 * this class will return the current LinkProperties. Because each change
 * triggers an update callback after the change is made, the owner may get more
 * callbacks than strictly necessary (some of which may be no-ops), but will not
 * be out of sync once all callbacks have been processed.
 *
 * Threading model:
 *
 * - The owner of this class is expected to create it, register it, and call
 *   getLinkProperties or clearLinkProperties on its thread.
 * - Most of the methods in the class are implementing NetworkObserver and are called
 *   on the handler used to register the observer.
 * - All accesses to mLinkProperties must be synchronized(this). All the other
 *   member variables are immutable once the object is constructed.
 *
 * TODO: Now that all the methods are called on the handler thread, remove synchronization and
 *       pass the LinkProperties to the update() callback.
 * TODO: Stop extending NetworkObserver and get events from netlink directly.
 *
 * @hide
 */
public class IpClientLinkObserver implements NetworkObserver {
    private final String mTag;

    /**
     * Callback used by {@link IpClientLinkObserver} to send update notifications.
     */
    public interface Callback {
        /**
         * Called when some properties of the link were updated.
         *
         * @param linkState Whether the interface link state is up as per the latest
         *                  {@link #onInterfaceLinkStateChanged(String, boolean)} callback. This
         *                  should only be used for metrics purposes, as it could be inconsistent
         *                  with {@link #getLinkProperties()} in particular.
         */
        void update(boolean linkState);

        /**
         * Called when an IPv6 address was removed from the interface.
         *
         * @param addr The removed IPv6 address.
         */
        void onIpv6AddressRemoved(Inet6Address addr);

        /**
         * Called when the clat interface was added/removed.
         *
         * @param add True: clat interface was added.
         *            False: clat interface was removed.
         */
        void onClatInterfaceStateUpdate(boolean add);
    }

    /** Configuration parameters for IpClientLinkObserver. */
    public static class Configuration {
        public final int minRdnssLifetime;
        public final boolean populateLinkAddressLifetime;

        public Configuration(int minRdnssLifetime, boolean populateLinkAddressLifetime) {
            this.minRdnssLifetime = minRdnssLifetime;
            this.populateLinkAddressLifetime = populateLinkAddressLifetime;
        }
    }

    private final Context mContext;
    private final String mInterfaceName;
    private final Callback mCallback;
    private final LinkProperties mLinkProperties;
    private boolean mInterfaceLinkState;
    private DnsServerRepository mDnsServerRepository;
    private final AlarmManager mAlarmManager;
    private final Configuration mConfig;
    private final Handler mHandler;
    private final IpClient.Dependencies mDependencies;
    private final String mClatInterfaceName;
    private final IpClientNetlinkMonitor mNetlinkMonitor;
    private final boolean mNetlinkEventParsingEnabled;

    private boolean mClatInterfaceExists;

    // This must match the interface prefix in clatd.c.
    // TODO: Revert this hack once IpClient and Nat464Xlat work in concert.
    protected static final String CLAT_PREFIX = "v4-";
    private static final boolean DBG = true;

    // The default socket receive buffer size in bytes(4MB). If too many netlink messages are
    // sent too quickly, those messages can overflow the socket receive buffer. Set a large-enough
    // recv buffer size to avoid the ENOBUFS as much as possible.
    @VisibleForTesting
    static final String CONFIG_SOCKET_RECV_BUFSIZE = "ipclient_netlink_sock_recv_buf_size";
    private static final int SOCKET_RECV_BUFSIZE = 4 * 1024 * 1024;

    public IpClientLinkObserver(Context context, Handler h, String iface, Callback callback,
            Configuration config, SharedLog log, IpClient.Dependencies deps) {
        mContext = context;
        mInterfaceName = iface;
        mClatInterfaceName = CLAT_PREFIX + iface;
        mTag = "IpClient/" + mInterfaceName;
        mCallback = callback;
        mLinkProperties = new LinkProperties();
        mLinkProperties.setInterfaceName(mInterfaceName);
        mConfig = config;
        mHandler = h;
        mInterfaceLinkState = true; // Assume up by default
        mDnsServerRepository = new DnsServerRepository(config.minRdnssLifetime);
        mAlarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        mDependencies = deps;
        mNetlinkEventParsingEnabled = deps.isFeatureNotChickenedOut(context,
                IPCLIENT_PARSE_NETLINK_EVENTS_FORCE_DISABLE);
        mNetlinkMonitor = new IpClientNetlinkMonitor(h, log, mTag);
        mHandler.post(() -> {
            if (!mNetlinkMonitor.start()) {
                Log.wtf(mTag, "Fail to start NetlinkMonitor.");
            }
        });
    }

    public void shutdown() {
        mHandler.post(mNetlinkMonitor::stop);
    }

    private boolean isIpv6LinkLocalDnsAccepted() {
        return mDependencies.isFeatureNotChickenedOut(mContext,
                IPCLIENT_ACCEPT_IPV6_LINK_LOCAL_DNS_VERSION);
    }

    private void maybeLog(String operation, String iface, LinkAddress address) {
        if (DBG) {
            Log.d(mTag, operation + ": " + address + " on " + iface
                    + " flags " + "0x" + HexDump.toHexString(address.getFlags())
                    + " scope " + address.getScope());
        }
    }

    private void maybeLog(String operation, int ifindex, LinkAddress address) {
        maybeLog(operation, "ifindex " + ifindex, address);
    }

    private void maybeLog(String operation, Object o) {
        if (DBG) {
            Log.d(mTag, operation + ": " + o.toString());
        }
    }

    private int getSocketReceiveBufferSize() {
        final int size = mDependencies.getDeviceConfigPropertyInt(CONFIG_SOCKET_RECV_BUFSIZE,
                SOCKET_RECV_BUFSIZE /* default value */);
        if (size < 0) {
            throw new IllegalArgumentException("Invalid SO_RCVBUF " + size);
        }
        return size;
    }

    @Override
    public void onInterfaceAdded(String iface) {
        if (mNetlinkEventParsingEnabled) return;
        maybeLog("interfaceAdded", iface);
        if (mClatInterfaceName.equals(iface)) {
            mCallback.onClatInterfaceStateUpdate(true /* add interface */);
        }
    }

    @Override
    public void onInterfaceRemoved(String iface) {
        if (mNetlinkEventParsingEnabled) return;
        maybeLog("interfaceRemoved", iface);
        if (mClatInterfaceName.equals(iface)) {
            mCallback.onClatInterfaceStateUpdate(false /* remove interface */);
        } else if (mInterfaceName.equals(iface)) {
            updateInterfaceRemoved();
        }
    }

    @Override
    public void onInterfaceLinkStateChanged(String iface, boolean state) {
        if (mNetlinkEventParsingEnabled) return;
        if (!mInterfaceName.equals(iface)) return;
        maybeLog("interfaceLinkStateChanged", iface + (state ? " up" : " down"));
        updateInterfaceLinkStateChanged(state);
    }

    @Override
    public void onInterfaceAddressUpdated(LinkAddress address, String iface) {
        if (mNetlinkEventParsingEnabled) return;
        if (!mInterfaceName.equals(iface)) return;
        maybeLog("addressUpdated", iface, address);
        updateInterfaceAddress(address, true /* add address */);
    }

    @Override
    public void onInterfaceAddressRemoved(LinkAddress address, String iface) {
        if (mNetlinkEventParsingEnabled) return;
        if (!mInterfaceName.equals(iface)) return;
        maybeLog("addressRemoved", iface, address);
        updateInterfaceAddress(address, false /* remove address */);
    }

    @Override
    public void onRouteUpdated(RouteInfo route) {
        if (mNetlinkEventParsingEnabled) return;
        if (!mInterfaceName.equals(route.getInterface())) return;
        maybeLog("routeUpdated", route);
        updateInterfaceRoute(route, true /* add route */);
    }

    @Override
    public void onRouteRemoved(RouteInfo route) {
        if (mNetlinkEventParsingEnabled) return;
        if (!mInterfaceName.equals(route.getInterface())) return;
        maybeLog("routeRemoved", route);
        updateInterfaceRoute(route, false /* remove route */);
    }

    @Override
    public void onInterfaceDnsServerInfo(String iface, long lifetime, String[] addresses) {
        if (mNetlinkEventParsingEnabled) return;
        if (!mInterfaceName.equals(iface)) return;
        updateInterfaceDnsServerInfo(lifetime, addresses);
    }

    private synchronized void updateInterfaceLinkStateChanged(boolean state) {
        setInterfaceLinkStateLocked(state);
    }

    private void updateInterfaceDnsServerInfo(long lifetime, final String[] addresses) {
        final boolean changed = mDnsServerRepository.addServers(lifetime, addresses);
        final boolean linkState;
        if (changed) {
            maybeLog("interfaceDnsServerInfo", Arrays.toString(addresses));
            synchronized (this) {
                mDnsServerRepository.setDnsServersOn(mLinkProperties);
                linkState = getInterfaceLinkStateLocked();
            }
            mCallback.update(linkState);
        }
    }

    private boolean updateInterfaceAddress(@NonNull final LinkAddress address, boolean add) {
        final boolean changed;
        final boolean linkState;
        synchronized (this) {
            if (add) {
                changed = mLinkProperties.addLinkAddress(address);
            } else {
                changed = mLinkProperties.removeLinkAddress(address);
            }
            linkState = getInterfaceLinkStateLocked();
        }
        if (changed) {
            mCallback.update(linkState);
            if (!add && address.isIpv6()) {
                final Inet6Address addr = (Inet6Address) address.getAddress();
                mCallback.onIpv6AddressRemoved(addr);
            }
        }
        return changed;
    }

    private boolean updateInterfaceRoute(final RouteInfo route, boolean add) {
        final boolean changed;
        final boolean linkState;
        synchronized (this) {
            if (add) {
                changed = mLinkProperties.addRoute(route);
            } else {
                changed = mLinkProperties.removeRoute(route);
            }
            linkState = getInterfaceLinkStateLocked();
        }
        if (changed) {
            mCallback.update(linkState);
        }
        return changed;
    }

    private void updateInterfaceRemoved() {
        // Our interface was removed. Clear our LinkProperties and tell our owner that they are
        // now empty. Note that from the moment that the interface is removed, any further
        // interface-specific messages (e.g., RTM_DELADDR) will not reach us, because the netd
        // code that parses them will not be able to resolve the ifindex to an interface name.
        final boolean linkState;
        synchronized (this) {
            clearLinkProperties();
            linkState = getInterfaceLinkStateLocked();
        }
        mCallback.update(linkState);
    }

    /**
     * Returns a copy of this object's LinkProperties.
     */
    public synchronized LinkProperties getLinkProperties() {
        return new LinkProperties(mLinkProperties);
    }

    /**
     * Reset this object's LinkProperties.
     */
    public synchronized void clearLinkProperties() {
        // Clear the repository before clearing mLinkProperties. That way, if a clear() happens
        // while interfaceDnsServerInfo() is being called, we'll end up with no DNS servers in
        // mLinkProperties, as desired.
        mDnsServerRepository = new DnsServerRepository(mConfig.minRdnssLifetime);
        mNetlinkMonitor.clearAlarms();
        mLinkProperties.clear();
        mLinkProperties.setInterfaceName(mInterfaceName);
    }

    private boolean getInterfaceLinkStateLocked() {
        return mInterfaceLinkState;
    }

    private void setInterfaceLinkStateLocked(boolean state) {
        mInterfaceLinkState = state;
    }

    /** Notifies this object of new interface parameters. */
    public void setInterfaceParams(InterfaceParams params) {
        mNetlinkMonitor.setIfindex(params.index);
    }

    /** Notifies this object not to listen on any interface. */
    public void clearInterfaceParams() {
        mNetlinkMonitor.setIfindex(0);  // 0 is never a valid ifindex.
    }

    private static boolean isSupportedRouteProtocol(RtNetlinkRouteMessage msg) {
        // Checks whether the protocol is supported. The behaviour is defined by the legacy
        // implementation in NetlinkEvent.cpp.
        return msg.getRtMsgHeader().protocol == RTPROT_KERNEL
                || msg.getRtMsgHeader().protocol == RTPROT_RA;
    }

    private static boolean isGlobalUnicastRoute(RtNetlinkRouteMessage msg) {
        return msg.getRtMsgHeader().scope == RT_SCOPE_UNIVERSE
                && msg.getRtMsgHeader().type == RTN_UNICAST;
    }

    /**
     * Simple NetlinkMonitor. Listen for netlink events from kernel.
     * All methods except the constructor must be called on the handler thread.
     */
    private class IpClientNetlinkMonitor extends NetlinkMonitor {
        private final Handler mHandler;

        IpClientNetlinkMonitor(Handler h, SharedLog log, String tag) {
            super(h, log, tag, OsConstants.NETLINK_ROUTE,
                    !mNetlinkEventParsingEnabled
                        ? NetlinkConstants.RTMGRP_ND_USEROPT
                        : (NetlinkConstants.RTMGRP_ND_USEROPT | NetlinkConstants.RTMGRP_LINK
                                | NetlinkConstants.RTMGRP_IPV4_IFADDR
                                | NetlinkConstants.RTMGRP_IPV6_IFADDR
                                | NetlinkConstants.RTMGRP_IPV6_ROUTE),
                    getSocketReceiveBufferSize());

            mHandler = h;
        }

        private final NetworkInformationShim mShim = NetworkInformationShimImpl.newInstance();

        private long mNat64PrefixExpiry;

        /**
         * Current interface index. Most of this class (and of IpClient), only uses interface names,
         * not interface indices. This means that the interface index can in theory change, and that
         * it's not necessarily correct to get the interface name at object creation time (and in
         * fact, when the object is created, the interface might not even exist).
         * TODO: once all netlink events pass through this class, stop depending on interface names.
         */
        private int mIfindex;

        void setIfindex(int ifindex) {
            if (!isRunning()) {
                Log.wtf(mTag, "NetlinkMonitor is not running when setting interface parameter!");
            }
            mIfindex = ifindex;
        }

        void clearAlarms() {
            cancelPref64Alarm();
        }

        private final AlarmManager.OnAlarmListener mExpirePref64Alarm = () -> {
            // Ignore the alarm if cancelPref64Alarm has already been called.
            //
            // TODO: in the rare case where the alarm fires and posts the lambda to the handler
            // thread while we are processing an RA that changes the lifetime of the same prefix,
            // this code will run anyway even if the alarm is rescheduled or cancelled. If the
            // lifetime in the RA is zero this code will correctly do nothing, but if the lifetime
            // is nonzero then the prefix will be added and immediately removed by this code.
            if (mNat64PrefixExpiry == 0) return;
            updatePref64(mShim.getNat64Prefix(mLinkProperties),
                    mNat64PrefixExpiry, mNat64PrefixExpiry);
        };

        private void cancelPref64Alarm() {
            // Clear the expiry in case the alarm just fired and has not been processed yet.
            if (mNat64PrefixExpiry == 0) return;
            mNat64PrefixExpiry = 0;
            mAlarmManager.cancel(mExpirePref64Alarm);
        }

        private void schedulePref64Alarm() {
            // There is no need to cancel any existing alarms, because we are using the same
            // OnAlarmListener object, and each such listener can only have at most one alarm.
            final String tag = mTag + ".PREF64";
            mAlarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, mNat64PrefixExpiry, tag,
                    mExpirePref64Alarm, mHandler);
        }

        /**
         * Processes a PREF64 ND option.
         *
         * @param prefix The NAT64 prefix.
         * @param now The time (as determined by SystemClock.elapsedRealtime) when the event
         *            that triggered this method was received.
         * @param expiry The time (as determined by SystemClock.elapsedRealtime) when the option
         *               expires.
         */
        private synchronized void updatePref64(IpPrefix prefix, final long now,
                final long expiry) {
            final IpPrefix currentPrefix = mShim.getNat64Prefix(mLinkProperties);

            // If the prefix matches the current prefix, refresh its lifetime.
            if (prefix.equals(currentPrefix)) {
                mNat64PrefixExpiry = expiry;
                if (expiry > now) {
                    schedulePref64Alarm();
                }
            }

            // If we already have a prefix, continue using it and ignore the new one. Stopping and
            // restarting clatd is disruptive because it will break existing IPv4 connections.
            // Note: this means that if we receive an RA that adds a new prefix and deletes the old
            // prefix, we might receive and ignore the new prefix, then delete the old prefix, and
            // have no prefix until the next RA is received. This is because the kernel returns ND
            // user options one at a time even if they are in the same RA.
            // TODO: keep track of the last few prefixes seen, like DnsServerRepository does.
            if (mNat64PrefixExpiry > now) return;

            // The current prefix has expired. Either replace it with the new one or delete it.
            if (expiry > now) {
                // If expiry > now, then prefix != currentPrefix (due to the return statement above)
                mShim.setNat64Prefix(mLinkProperties, prefix);
                mNat64PrefixExpiry = expiry;
                schedulePref64Alarm();
            } else {
                mShim.setNat64Prefix(mLinkProperties, null);
                cancelPref64Alarm();
            }

            mCallback.update(getInterfaceLinkStateLocked());
        }

        private void processPref64Option(StructNdOptPref64 opt, final long now) {
            final long expiry = now + TimeUnit.SECONDS.toMillis(opt.lifetime);
            updatePref64(opt.prefix, now, expiry);
        }

        private void processRdnssOption(StructNdOptRdnss opt) {
            if (!mNetlinkEventParsingEnabled) return;
            final String[] addresses = new String[opt.servers.length];
            for (int i = 0; i < opt.servers.length; i++) {
                final Inet6Address addr = isIpv6LinkLocalDnsAccepted()
                        ? InetAddressUtils.withScopeId(opt.servers[i], mIfindex)
                        : opt.servers[i];
                addresses[i] = addr.getHostAddress();
            }
            updateInterfaceDnsServerInfo(opt.header.lifetime, addresses);
        }

        private void processNduseroptMessage(NduseroptMessage msg, final long whenMs) {
            if (msg.family != AF_INET6 || msg.option == null || msg.ifindex != mIfindex) return;
            if (msg.icmp_type != (byte) ICMPV6_ROUTER_ADVERTISEMENT) return;

            switch (msg.option.type) {
                case StructNdOptPref64.TYPE:
                    processPref64Option((StructNdOptPref64) msg.option, whenMs);
                    break;

                case StructNdOptRdnss.TYPE:
                    processRdnssOption((StructNdOptRdnss) msg.option);
                    break;

                default:
                    // TODO: implement DNSSL.
                    break;
            }
        }

        private void updateClatInterfaceLinkState(@NonNull final StructIfinfoMsg ifinfoMsg,
                @Nullable final String ifname, short nlMsgType) {
            switch (nlMsgType) {
                case NetlinkConstants.RTM_NEWLINK:
                    if (mClatInterfaceExists) break;
                    maybeLog("clatInterfaceAdded", ifname);
                    mCallback.onClatInterfaceStateUpdate(true /* add interface */);
                    mClatInterfaceExists = true;
                    break;

                case NetlinkConstants.RTM_DELLINK:
                    if (!mClatInterfaceExists) break;
                    maybeLog("clatInterfaceRemoved", ifname);
                    mCallback.onClatInterfaceStateUpdate(false /* remove interface */);
                    mClatInterfaceExists = false;
                    break;

                default:
                    Log.e(mTag, "unsupported rtnetlink link msg type " + nlMsgType);
                    break;
            }
        }

        private void processRtNetlinkLinkMessage(RtNetlinkLinkMessage msg) {
            if (!mNetlinkEventParsingEnabled) return;

            // Check if receiving netlink link state update for clat interface.
            final String ifname = msg.getInterfaceName();
            final short nlMsgType = msg.getHeader().nlmsg_type;
            final StructIfinfoMsg ifinfoMsg = msg.getIfinfoHeader();
            if (mClatInterfaceName.equals(ifname)) {
                updateClatInterfaceLinkState(ifinfoMsg, ifname, nlMsgType);
                return;
            }

            if (ifinfoMsg.family != AF_UNSPEC || ifinfoMsg.index != mIfindex) return;
            if ((ifinfoMsg.flags & IFF_LOOPBACK) != 0) return;

            switch (nlMsgType) {
                case NetlinkConstants.RTM_NEWLINK:
                    final boolean state = (ifinfoMsg.flags & IFF_LOWER_UP) != 0;
                    maybeLog("interfaceLinkStateChanged", "ifindex " + mIfindex
                            + (state ? " up" : " down"));
                    updateInterfaceLinkStateChanged(state);
                    break;

                case NetlinkConstants.RTM_DELLINK:
                    maybeLog("interfaceRemoved", ifname);
                    updateInterfaceRemoved();
                    break;

                default:
                    Log.e(mTag, "Unknown rtnetlink link msg type " + nlMsgType);
                    break;
            }
        }

        // The preferred/valid in ifa_cacheinfo expressed in units of seconds, convert
        // it to milliseconds for deprecationTime or expirationTime used in LinkAddress.
        // If the experiment flag is not enabled, LinkAddress.LIFETIME_UNKNOWN is retuend,
        // the same as before.
        private long getDeprecationOrExpirationTime(@Nullable final StructIfacacheInfo cacheInfo,
                long now, boolean deprecationTime) {
            if (!mConfig.populateLinkAddressLifetime || (cacheInfo == null)) {
                return LinkAddress.LIFETIME_UNKNOWN;
            }
            final long lifetime = deprecationTime ? cacheInfo.preferred : cacheInfo.valid;
            return (lifetime == Integer.toUnsignedLong(INFINITE_LEASE))
                    ? LinkAddress.LIFETIME_PERMANENT
                    : now + lifetime * 1000;
        }

        private void processRtNetlinkAddressMessage(RtNetlinkAddressMessage msg) {
            if (!mNetlinkEventParsingEnabled) return;

            final StructIfaddrMsg ifaddrMsg = msg.getIfaddrHeader();
            if (ifaddrMsg.index != mIfindex) return;

            final StructIfacacheInfo cacheInfo = msg.getIfacacheInfo();
            final long now = SystemClock.elapsedRealtime();
            final long deprecationTime =
                    getDeprecationOrExpirationTime(cacheInfo, now, true /* deprecationTime */);
            final long expirationTime =
                    getDeprecationOrExpirationTime(cacheInfo, now, false /* deprecationTime */);
            final LinkAddress la = new LinkAddress(msg.getIpAddress(), ifaddrMsg.prefixLen,
                    msg.getFlags(), ifaddrMsg.scope, deprecationTime, expirationTime);

            switch (msg.getHeader().nlmsg_type) {
                case NetlinkConstants.RTM_NEWADDR:
                    if (updateInterfaceAddress(la, true /* add address */)) {
                        maybeLog("addressUpdated", mIfindex, la);
                    }
                    break;
                case NetlinkConstants.RTM_DELADDR:
                    if (updateInterfaceAddress(la, false /* remove address */)) {
                        maybeLog("addressRemoved", mIfindex, la);
                    }
                    break;
                default:
                    Log.e(mTag, "Unknown rtnetlink address msg type " + msg.getHeader().nlmsg_type);
                    return;
            }
        }

        private void processRtNetlinkRouteMessage(RtNetlinkRouteMessage msg) {
            if (!mNetlinkEventParsingEnabled) return;
            if (msg.getInterfaceIndex() != mIfindex) return;
            // Ignore the unsupported route protocol and non-global unicast routes.
            if (!isSupportedRouteProtocol(msg)
                    || !isGlobalUnicastRoute(msg)
                    // don't support source routing
                    || (msg.getRtMsgHeader().srcLen != 0)
                    // don't support cloned routes
                    || ((msg.getRtMsgHeader().flags & RTM_F_CLONED) != 0)) {
                return;
            }

            final RouteInfo route = new RouteInfo(msg.getDestination(), msg.getGateway(),
                    mInterfaceName, msg.getRtMsgHeader().type);
            switch (msg.getHeader().nlmsg_type) {
                case NetlinkConstants.RTM_NEWROUTE:
                    if (updateInterfaceRoute(route, true /* add route */)) {
                        maybeLog("routeUpdated", route);
                    }
                    break;
                case NetlinkConstants.RTM_DELROUTE:
                    if (updateInterfaceRoute(route, false /* remove route */)) {
                        maybeLog("routeRemoved", route);
                    }
                    break;
                default:
                    Log.e(mTag, "Unknown rtnetlink route msg type " + msg.getHeader().nlmsg_type);
                    break;
            }
        }

        @Override
        protected void processNetlinkMessage(NetlinkMessage nlMsg, long whenMs) {
            if (nlMsg instanceof NduseroptMessage) {
                processNduseroptMessage((NduseroptMessage) nlMsg, whenMs);
            } else if (nlMsg instanceof RtNetlinkLinkMessage) {
                processRtNetlinkLinkMessage((RtNetlinkLinkMessage) nlMsg);
            } else if (nlMsg instanceof RtNetlinkAddressMessage) {
                processRtNetlinkAddressMessage((RtNetlinkAddressMessage) nlMsg);
            } else if (nlMsg instanceof RtNetlinkRouteMessage) {
                processRtNetlinkRouteMessage((RtNetlinkRouteMessage) nlMsg);
            } else {
                Log.e(mTag, "Unknown netlink message: " + nlMsg);
            }
        }
    }

    /**
     * Tracks DNS server updates received from Netlink.
     *
     * The network may announce an arbitrary number of DNS servers in Router Advertisements at any
     * time. Each announcement has a lifetime; when the lifetime expires, the servers should not be
     * used any more. In this way, the network can gracefully migrate clients from one set of DNS
     * servers to another. Announcements can both raise and lower the lifetime, and an announcement
     * can expire servers by announcing them with a lifetime of zero.
     *
     * Typically the system will only use a small number (2 or 3; {@code NUM_CURRENT_SERVERS}) of
     * DNS servers at any given time. These are referred to as the current servers. In case all the
     * current servers expire, the class also keeps track of a larger (but limited) number of
     * servers that are promoted to current servers when the current ones expire. In order to
     * minimize updates to the rest of the system (and potentially expensive cache flushes) this
     * class attempts to keep the list of current servers constant where possible. More
     * specifically, the list of current servers is only updated if a new server is learned and
     * there are not yet {@code NUM_CURRENT_SERVERS} current servers, or if one or more of the
     * current servers expires or is pushed out of the set. Therefore, the current servers will not
     * necessarily be the ones with the highest lifetime, but the ones learned first.
     *
     * This is by design: if instead the class always preferred the servers with the highest
     * lifetime, a (misconfigured?) network where two or more routers announce more than
     * {@code NUM_CURRENT_SERVERS} unique servers would cause persistent oscillations.
     *
     * TODO: Currently servers are only expired when a new DNS update is received.
     * Update them using timers, or possibly on every notification received by NetlinkTracker.
     *
     * Threading model: run by NetlinkTracker. Methods are synchronized(this) just in case netlink
     * notifications are sent by multiple threads. If future threads use alarms to expire, those
     * alarms must also be synchronized(this).
     *
     */
    private static class DnsServerRepository {

        /** How many DNS servers we will use. 3 is suggested by RFC 6106. */
        static final int NUM_CURRENT_SERVERS = 3;

        /** How many DNS servers we'll keep track of, in total. */
        static final int NUM_SERVERS = 12;

        /** Stores up to {@code NUM_CURRENT_SERVERS} DNS servers we're currently using. */
        private Set<InetAddress> mCurrentServers;

        public static final String TAG = "DnsServerRepository";

        /**
         * Stores all the DNS servers we know about, for use when the current servers expire.
         * Always sorted in order of decreasing expiry. The elements in this list are also the
         * values of mIndex, and may be elements in mCurrentServers.
         */
        private ArrayList<DnsServerEntry> mAllServers;

        /**
         * Indexes the servers so we can update their lifetimes more quickly in the common case
         * where servers are not being added, but only being refreshed.
         */
        private HashMap<InetAddress, DnsServerEntry> mIndex;

        /**
         * Minimum (non-zero) RDNSS lifetime to accept.
         */
        private final int mMinLifetime;

        DnsServerRepository(int minLifetime) {
            mCurrentServers = new HashSet<>();
            mAllServers = new ArrayList<>(NUM_SERVERS);
            mIndex = new HashMap<>(NUM_SERVERS);
            mMinLifetime = minLifetime;
        }

        /** Sets the DNS servers of the provided LinkProperties object to the current servers. */
        public synchronized void setDnsServersOn(LinkProperties lp) {
            lp.setDnsServers(mCurrentServers);
        }

        /**
         * Notifies the class of new DNS server information.
         * @param lifetime the time in seconds that the DNS servers are valid.
         * @param addresses the string representations of the IP addresses of DNS servers to use.
         */
        public synchronized boolean addServers(long lifetime, String[] addresses) {
            // If the servers are below the minimum lifetime, don't change anything.
            if (lifetime != 0 && lifetime < mMinLifetime) return false;

            // The lifetime is actually an unsigned 32-bit number, but Java doesn't have unsigned.
            // Technically 0xffffffff (the maximum) is special and means "forever", but 2^32 seconds
            // (136 years) is close enough.
            long now = System.currentTimeMillis();
            long expiry = now + 1000 * lifetime;

            // Go through the list of servers. For each one, update the entry if one exists, and
            // create one if it doesn't.
            for (String addressString : addresses) {
                InetAddress address;
                try {
                    address = InetAddresses.parseNumericAddress(addressString);
                } catch (IllegalArgumentException ex) {
                    continue;
                }

                if (!updateExistingEntry(address, expiry)) {
                    // There was no entry for this server. Create one, unless it's already expired
                    // (i.e., if the lifetime is zero; it cannot be < 0 because it's unsigned).
                    if (expiry > now) {
                        DnsServerEntry entry = new DnsServerEntry(address, expiry);
                        mAllServers.add(entry);
                        mIndex.put(address, entry);
                    }
                }
            }

            // Sort the servers by expiry.
            Collections.sort(mAllServers);

            // Prune excess entries and update the current server list.
            return updateCurrentServers();
        }

        private synchronized boolean updateExistingEntry(InetAddress address, long expiry) {
            DnsServerEntry existing = mIndex.get(address);
            if (existing != null) {
                existing.expiry = expiry;
                return true;
            }
            return false;
        }

        private synchronized boolean updateCurrentServers() {
            long now = System.currentTimeMillis();
            boolean changed = false;

            // Prune excess or expired entries.
            for (int i = mAllServers.size() - 1; i >= 0; i--) {
                if (i >= NUM_SERVERS || mAllServers.get(i).expiry <= now) {
                    DnsServerEntry removed = mAllServers.remove(i);
                    mIndex.remove(removed.address);
                    changed |= mCurrentServers.remove(removed.address);
                } else {
                    break;
                }
            }

            // Add servers to the current set, in order of decreasing lifetime, until it has enough.
            // Prefer existing servers over new servers in order to minimize updates to the rest of
            // the system and avoid persistent oscillations.
            for (DnsServerEntry entry : mAllServers) {
                if (mCurrentServers.size() < NUM_CURRENT_SERVERS) {
                    changed |= mCurrentServers.add(entry.address);
                } else {
                    break;
                }
            }
            return changed;
        }
    }

    /**
     * Represents a DNS server entry with an expiry time.
     *
     * Implements Comparable so DNS server entries can be sorted by lifetime, longest-lived first.
     * The ordering of entries with the same lifetime is unspecified, because given two servers with
     * identical lifetimes, we don't care which one we use, and only comparing the lifetime is much
     * faster than comparing the IP address as well.
     *
     * Note: this class has a natural ordering that is inconsistent with equals.
     */
    private static class DnsServerEntry implements Comparable<DnsServerEntry> {
        /** The IP address of the DNS server. */
        public final InetAddress address;
        /** The time until which the DNS server may be used. A Java millisecond time as might be
         * returned by currentTimeMillis(). */
        public long expiry;

        DnsServerEntry(InetAddress address, long expiry) throws IllegalArgumentException {
            this.address = address;
            this.expiry = expiry;
        }

        public int compareTo(DnsServerEntry other) {
            return Long.compare(other.expiry, this.expiry);
        }
    }
}
