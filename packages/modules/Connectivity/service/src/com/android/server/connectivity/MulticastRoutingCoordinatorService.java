/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.server.connectivity;

import static android.net.MulticastRoutingConfig.FORWARD_NONE;
import static android.net.MulticastRoutingConfig.FORWARD_SELECTED;
import static android.net.MulticastRoutingConfig.FORWARD_WITH_MIN_SCOPE;
import static android.system.OsConstants.AF_INET6;
import static android.system.OsConstants.EADDRINUSE;
import static android.system.OsConstants.IPPROTO_ICMPV6;
import static android.system.OsConstants.IPPROTO_IPV6;
import static android.system.OsConstants.SOCK_CLOEXEC;
import static android.system.OsConstants.SOCK_NONBLOCK;
import static android.system.OsConstants.SOCK_RAW;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.MulticastRoutingConfig;
import android.net.NetworkUtils;
import android.os.Handler;
import android.os.Looper;
import android.system.ErrnoException;
import android.system.Os;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;
import android.util.SparseArray;

import com.android.internal.annotations.VisibleForTesting;
import com.android.net.module.util.LinkPropertiesUtils.CompareResult;
import com.android.net.module.util.PacketReader;
import com.android.net.module.util.SocketUtils;
import com.android.net.module.util.netlink.NetlinkUtils;
import com.android.net.module.util.netlink.RtNetlinkRouteMessage;
import com.android.net.module.util.structs.StructMf6cctl;
import com.android.net.module.util.structs.StructMif6ctl;
import com.android.net.module.util.structs.StructMrt6Msg;

import java.io.FileDescriptor;
import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Class to coordinate multicast routing between network interfaces.
 *
 * <p>Supports IPv6 multicast routing.
 *
 * <p>Note that usage of this class is not thread-safe. All public methods must be called from the
 * same thread that the handler from {@code dependencies.getHandler} is associated.
 */
public class MulticastRoutingCoordinatorService {
    private static final String TAG = MulticastRoutingCoordinatorService.class.getSimpleName();
    private static final int ICMP6_FILTER = 1;
    private static final int MRT6_INIT = 200;
    private static final int MRT6_ADD_MIF = 202;
    private static final int MRT6_DEL_MIF = 203;
    private static final int MRT6_ADD_MFC = 204;
    private static final int MRT6_DEL_MFC = 205;
    private static final int ONE = 1;

    private final Dependencies mDependencies;

    private final Handler mHandler;
    private final MulticastNocacheUpcallListener mMulticastNoCacheUpcallListener;
    @NonNull private final FileDescriptor mMulticastRoutingFd; // For multicast routing config
    @NonNull private final MulticastSocket mMulticastSocket; // For join group and leave group

    @VisibleForTesting public static final int MFC_INACTIVE_CHECK_INTERVAL_MS = 60_000;
    @VisibleForTesting public static final int MFC_INACTIVE_TIMEOUT_MS = 300_000;
    @VisibleForTesting public static final int MFC_MAX_NUMBER_OF_ENTRIES = 1_000;

    // The kernel supports max 32 virtual interfaces per multicast routing table.
    private static final int MAX_NUM_OF_MULTICAST_VIRTUAL_INTERFACES = 32;

    /** Tracks if checking for inactive MFC has been scheduled */
    private boolean mMfcPollingScheduled = false;

    /** Mapping from multicast virtual interface index to interface name */
    private SparseArray<String> mVirtualInterfaces =
            new SparseArray<>(MAX_NUM_OF_MULTICAST_VIRTUAL_INTERFACES);
    /** Mapping from physical interface index to interface name */
    private SparseArray<String> mInterfaces =
            new SparseArray<>(MAX_NUM_OF_MULTICAST_VIRTUAL_INTERFACES);

    /** Mapping of iif to PerInterfaceMulticastRoutingConfig */
    private Map<String, PerInterfaceMulticastRoutingConfig> mMulticastRoutingConfigs =
            new HashMap<String, PerInterfaceMulticastRoutingConfig>();

    private static final class PerInterfaceMulticastRoutingConfig {
        // mapping of oif name to MulticastRoutingConfig
        public Map<String, MulticastRoutingConfig> oifConfigs =
                new HashMap<String, MulticastRoutingConfig>();
    }

    /** Tracks the MFCs added to kernel. Using LinkedHashMap to keep the added order, so
    // when the number of MFCs reaches the max limit then the earliest added one is removed. */
    private LinkedHashMap<MfcKey, MfcValue> mMfcs = new LinkedHashMap<>();

    public MulticastRoutingCoordinatorService(Handler h) {
        this(h, new Dependencies());
    }

    @VisibleForTesting
    /* @throws UnsupportedOperationException if multicast routing is not supported */
    public MulticastRoutingCoordinatorService(Handler h, Dependencies dependencies) {
        mDependencies = dependencies;
        mMulticastRoutingFd = mDependencies.createMulticastRoutingSocket();
        mMulticastSocket = mDependencies.createMulticastSocket();
        mHandler = h;
        mMulticastNoCacheUpcallListener =
                new MulticastNocacheUpcallListener(mHandler, mMulticastRoutingFd);
        mHandler.post(() -> mMulticastNoCacheUpcallListener.start());
    }

    private void checkOnHandlerThread() {
        if (Looper.myLooper() != mHandler.getLooper()) {
            throw new IllegalStateException(
                    "Not running on ConnectivityService thread (" + mHandler.getLooper() + ") : "
                            + Looper.myLooper());
        }
    }

    private Integer getInterfaceIndex(String ifName) {
        int mapIndex = mInterfaces.indexOfValue(ifName);
        if (mapIndex < 0) return null;
        return mInterfaces.keyAt(mapIndex);
    }

    /**
     * Apply multicast routing configuration
     *
     * @param iifName name of the incoming interface
     * @param oifName name of the outgoing interface
     * @param newConfig the multicast routing configuration to be applied from iif to oif
     * @throws MulticastRoutingException when failed to apply the config
     */
    public void applyMulticastRoutingConfig(
            final String iifName, final String oifName, final MulticastRoutingConfig newConfig) {
        checkOnHandlerThread();

        if (newConfig.getForwardingMode() != FORWARD_NONE) {
            // Make sure iif and oif are added as multicast forwarding interfaces
            try {
                maybeAddAndTrackInterface(iifName);
                maybeAddAndTrackInterface(oifName);
            } catch (IllegalStateException e) {
                Log.e(TAG, "Failed to apply multicast routing config, ", e);
                return;
            }
        }

        final MulticastRoutingConfig oldConfig = getMulticastRoutingConfig(iifName, oifName);

        if (oldConfig.equals(newConfig)) return;

        int oldMode = oldConfig.getForwardingMode();
        int newMode = newConfig.getForwardingMode();
        Integer iifIndex = getInterfaceIndex(iifName);
        if (iifIndex == null) {
            // This cannot happen unless the new config has FORWARD_NONE but is not the same
            // as the old config. This is not possible in current code.
            Log.wtf(TAG, "Adding multicast configuration on null interface?");
            return;
        }

        // When new addresses are added to FORWARD_SELECTED mode, join these multicast groups
        // on their upstream interface, so upstream multicast routers know about the subscription.
        // When addresses are removed from FORWARD_SELECTED mode, leave the multicast groups.
        final Set<Inet6Address> oldListeningAddresses =
                (oldMode == FORWARD_SELECTED)
                        ? oldConfig.getListeningAddresses()
                        : new ArraySet<>();
        final Set<Inet6Address> newListeningAddresses =
                (newMode == FORWARD_SELECTED)
                        ? newConfig.getListeningAddresses()
                        : new ArraySet<>();
        final CompareResult<Inet6Address> addressDiff =
                new CompareResult<>(oldListeningAddresses, newListeningAddresses);
        joinGroups(iifIndex, addressDiff.added);
        leaveGroups(iifIndex, addressDiff.removed);

        setMulticastRoutingConfig(iifName, oifName, newConfig);
        Log.d(
                TAG,
                "Applied multicast routing config for iif "
                        + iifName
                        + " to oif "
                        + oifName
                        + " with Config "
                        + newConfig);

        // Update existing MFCs to make sure they align with the updated configuration
        updateMfcs();

        if (newConfig.getForwardingMode() == FORWARD_NONE) {
            if (!hasActiveMulticastConfig(iifName)) {
                removeInterfaceFromMulticastRouting(iifName);
            }
            if (!hasActiveMulticastConfig(oifName)) {
                removeInterfaceFromMulticastRouting(oifName);
            }
        }
    }

    /**
     * Removes an network interface from multicast routing.
     *
     * <p>Remove the network interface from multicast configs and remove it from the list of
     * multicast routing interfaces in the kernel
     *
     * @param ifName name of the interface that should be removed
     */
    @VisibleForTesting
    public void removeInterfaceFromMulticastRouting(final String ifName) {
        checkOnHandlerThread();
        final Integer virtualIndex = getVirtualInterfaceIndex(ifName);
        if (virtualIndex == null) return;

        updateMfcs();
        mInterfaces.removeAt(mInterfaces.indexOfValue(ifName));
        mVirtualInterfaces.remove(virtualIndex);
        try {
            mDependencies.setsockoptMrt6DelMif(mMulticastRoutingFd, virtualIndex);
            Log.d(TAG, "Removed mifi " + virtualIndex + " from MIF");
        } catch (ErrnoException e) {
            Log.e(TAG, "failed to remove multicast virtual interface" + virtualIndex, e);
        }
    }

    private int getNextAvailableVirtualIndex() {
        if (mVirtualInterfaces.size() >= MAX_NUM_OF_MULTICAST_VIRTUAL_INTERFACES) {
            throw new IllegalStateException("Can't allocate new multicast virtual interface");
        }
        for (int i = 0; i < mVirtualInterfaces.size(); i++) {
            if (!mVirtualInterfaces.contains(i)) {
                return i;
            }
        }
        return mVirtualInterfaces.size();
    }

    @VisibleForTesting
    public Integer getVirtualInterfaceIndex(String ifName) {
        int mapIndex = mVirtualInterfaces.indexOfValue(ifName);
        if (mapIndex < 0) return null;
        return mVirtualInterfaces.keyAt(mapIndex);
    }

    private Integer getVirtualInterfaceIndex(int physicalIndex) {
        String ifName = mInterfaces.get(physicalIndex);
        if (ifName == null) {
            // This is only used to match MFCs from kernel to MFCs we know about.
            // Unknown MFCs should be ignored.
            return null;
        }
        return getVirtualInterfaceIndex(ifName);
    }

    private String getInterfaceName(int virtualIndex) {
        return mVirtualInterfaces.get(virtualIndex);
    }

    private void maybeAddAndTrackInterface(String ifName) {
        checkOnHandlerThread();
        if (mVirtualInterfaces.indexOfValue(ifName) >= 0) return;

        int nextVirtualIndex = getNextAvailableVirtualIndex();
        int ifIndex = mDependencies.getInterfaceIndex(ifName);
        final StructMif6ctl mif6ctl =
                    new StructMif6ctl(
                            nextVirtualIndex,
                            (short) 0 /* mif6c_flags */,
                            (short) 1 /* vifc_threshold */,
                            ifIndex,
                            0 /* vifc_rate_limit */);
        try {
            mDependencies.setsockoptMrt6AddMif(mMulticastRoutingFd, mif6ctl);
            Log.d(TAG, "Added mifi " + nextVirtualIndex + " to MIF");
        } catch (ErrnoException e) {
            Log.e(TAG, "failed to add multicast virtual interface", e);
            return;
        }
        mVirtualInterfaces.put(nextVirtualIndex, ifName);
        mInterfaces.put(ifIndex, ifName);
    }

    @VisibleForTesting
    public MulticastRoutingConfig getMulticastRoutingConfig(String iifName, String oifName) {
        PerInterfaceMulticastRoutingConfig configs = mMulticastRoutingConfigs.get(iifName);
        final MulticastRoutingConfig defaultConfig = MulticastRoutingConfig.CONFIG_FORWARD_NONE;
        if (configs == null) {
            return defaultConfig;
        } else {
            return configs.oifConfigs.getOrDefault(oifName, defaultConfig);
        }
    }

    private void setMulticastRoutingConfig(
            final String iifName, final String oifName, final MulticastRoutingConfig config) {
        checkOnHandlerThread();
        PerInterfaceMulticastRoutingConfig iifConfig = mMulticastRoutingConfigs.get(iifName);

        if (config.getForwardingMode() == FORWARD_NONE) {
            if (iifConfig != null) {
                iifConfig.oifConfigs.remove(oifName);
            }
            if (iifConfig.oifConfigs.isEmpty()) {
                mMulticastRoutingConfigs.remove(iifName);
            }
            return;
        }

        if (iifConfig == null) {
            iifConfig = new PerInterfaceMulticastRoutingConfig();
            mMulticastRoutingConfigs.put(iifName, iifConfig);
        }
        iifConfig.oifConfigs.put(oifName, config);
    }

    /** Returns whether an interface has multicast routing config */
    private boolean hasActiveMulticastConfig(final String ifName) {
        // FORWARD_NONE configs are not saved in the config tables, so
        // any existing config is an active multicast routing config
        if (mMulticastRoutingConfigs.containsKey(ifName)) return true;
        for (var pic : mMulticastRoutingConfigs.values()) {
            if (pic.oifConfigs.containsKey(ifName)) return true;
        }
        return false;
    }

    /**
     * A multicast forwarding cache (MFC) entry holds a multicast forwarding route where packet from
     * incoming interface(iif) with source address(S) to group address (G) are forwarded to outgoing
     * interfaces(oifs).
     *
     * <p>iif, S and G identifies an MFC entry. For example an MFC1 is added: [iif1, S1, G1, oifs1]
     * Adding another MFC2 of [iif1, S1, G1, oifs2] to the kernel overwrites MFC1.
     */
    private static final class MfcKey {
        public final int mIifVirtualIdx;
        public final Inet6Address mSrcAddr;
        public final Inet6Address mDstAddr;

        public MfcKey(int iif, Inet6Address src, Inet6Address dst) {
            mIifVirtualIdx = iif;
            mSrcAddr = src;
            mDstAddr = dst;
        }

        public boolean equals(Object other) {
            if (other == this) {
                return true;
            } else if (!(other instanceof MfcKey)) {
                return false;
            } else {
                MfcKey otherKey = (MfcKey) other;
                return mIifVirtualIdx == otherKey.mIifVirtualIdx
                        && mSrcAddr.equals(otherKey.mSrcAddr)
                        && mDstAddr.equals(otherKey.mDstAddr);
            }
        }

        public int hashCode() {
            return Objects.hash(mIifVirtualIdx, mSrcAddr, mDstAddr);
        }

        public String toString() {
            return "{iifVirtualIndex: "
                    + Integer.toString(mIifVirtualIdx)
                    + ", sourceAddress: "
                    + mSrcAddr.toString()
                    + ", destinationAddress: "
                    + mDstAddr.toString()
                    + "}";
        }
    }

    private static final class MfcValue {
        private Set<Integer> mOifVirtualIndices;
        // timestamp of when the mfc was last used in the kernel
        // (e.g. created, or used to forward a packet)
        private Instant mLastUsedAt;

        public MfcValue(Set<Integer> oifs, Instant timestamp) {
            mOifVirtualIndices = oifs;
            mLastUsedAt = timestamp;
        }

        public boolean hasSameOifsAs(MfcValue other) {
            return this.mOifVirtualIndices.equals(other.mOifVirtualIndices);
        }

        public boolean equals(Object other) {
            if (other == this) {
                return true;
            } else if (!(other instanceof MfcValue)) {
                return false;
            } else {
                MfcValue otherValue = (MfcValue) other;
                return mOifVirtualIndices.equals(otherValue.mOifVirtualIndices)
                        && mLastUsedAt.equals(otherValue.mLastUsedAt);
            }
        }

        public int hashCode() {
            return Objects.hash(mOifVirtualIndices, mLastUsedAt);
        }

        public Set<Integer> getOifIndices() {
            return mOifVirtualIndices;
        }

        public void setLastUsedAt(Instant timestamp) {
            mLastUsedAt = timestamp;
        }

        public Instant getLastUsedAt() {
            return mLastUsedAt;
        }

        public String toString() {
            return "{oifVirtualIdxes: "
                    + mOifVirtualIndices.toString()
                    + ", lastUsedAt: "
                    + mLastUsedAt.toString()
                    + "}";
        }
    }

    /**
     * Returns the MFC value for the given MFC key according to current multicast routing config. If
     * the MFC should be removed return null.
     */
    private MfcValue computeMfcValue(int iif, Inet6Address dst) {
        final int dstScope = getGroupAddressScope(dst);
        Set<Integer> forwardingOifs = new ArraySet<>();

        PerInterfaceMulticastRoutingConfig iifConfig =
                mMulticastRoutingConfigs.get(getInterfaceName(iif));

        if (iifConfig == null) {
            // An iif may have been removed from multicast routing, in this
            // case remove the MFC directly
            return null;
        }

        for (var config : iifConfig.oifConfigs.entrySet()) {
            if ((config.getValue().getForwardingMode() == FORWARD_WITH_MIN_SCOPE
                            && config.getValue().getMinimumScope() <= dstScope)
                    || (config.getValue().getForwardingMode() == FORWARD_SELECTED
                            && config.getValue().getListeningAddresses().contains(dst))) {
                forwardingOifs.add(getVirtualInterfaceIndex(config.getKey()));
            }
        }

        return new MfcValue(forwardingOifs, Instant.now(mDependencies.getClock()));
    }

    /**
     * Given the iif, source address and group destination address, add an MFC entry or update the
     * existing MFC according to the multicast routing config. If such an MFC should not exist,
     * return null for caller of the function to remove it.
     *
     * <p>Note that if a packet has no matching MFC entry in the kernel, kernel creates an
     * unresolved route and notifies multicast socket with a NOCACHE upcall message. The unresolved
     * route is kept for no less than 10s. If packets with the same source and destination arrives
     * before the 10s timeout, they will not be notified. Thus we need to add a 'blocking' MFC which
     * is an MFC with an empty oif list. When the multicast configs changes, the 'blocking' MFC
     * will be updated to a 'forwarding' MFC so that corresponding multicast traffic can be
     * forwarded instantly.
     *
     * @return {@code true} if the MFC is updated and no operation is needed from caller.
     * {@code false} if the MFC should not be added, caller of the function should remove
     * the MFC if needed.
     */
    private boolean addOrUpdateMfc(int vif, Inet6Address src, Inet6Address dst) {
        checkOnHandlerThread();
        final MfcKey key = new MfcKey(vif, src, dst);
        final MfcValue value = mMfcs.get(key);
        final MfcValue updatedValue = computeMfcValue(vif, dst);

        if (updatedValue == null) {
            return false;
        }

        if (value != null && value.hasSameOifsAs(updatedValue)) {
            // no updates to make
            return true;
        }

        final StructMf6cctl mf6cctl =
                new StructMf6cctl(src, dst, vif, updatedValue.getOifIndices());
        try {
            mDependencies.setsockoptMrt6AddMfc(mMulticastRoutingFd, mf6cctl);
        } catch (ErrnoException e) {
            Log.e(TAG, "failed to add MFC: " + e);
            return false;
        }
        mMfcs.put(key, updatedValue);
        String operation = (value == null ? "Added" : "Updated");
        Log.d(TAG, operation + " MFC key: " + key + " value: " + updatedValue);
        return true;
    }

    private void checkMfcsExpiration() {
        checkOnHandlerThread();
        // Check if there are inactive MFCs that can be removed
        refreshMfcInactiveDuration();
        maybeExpireMfcs();
        if (mMfcs.size() > 0) {
            mHandler.postDelayed(() -> checkMfcsExpiration(), MFC_INACTIVE_CHECK_INTERVAL_MS);
            mMfcPollingScheduled = true;
        } else {
            mMfcPollingScheduled = false;
        }
    }

    private void checkMfcEntriesLimit() {
        checkOnHandlerThread();
        // If the max number of MFC entries is reached, remove the first MFC entry. This can be
        // any entry, as if this entry is needed again there will be a NOCACHE upcall to add it
        // back.
        if (mMfcs.size() == MFC_MAX_NUMBER_OF_ENTRIES) {
            Log.w(TAG, "Reached max number of MFC entries " + MFC_MAX_NUMBER_OF_ENTRIES);
            var iter = mMfcs.entrySet().iterator();
            MfcKey firstMfcKey = iter.next().getKey();
            removeMfcFromKernel(firstMfcKey);
            iter.remove();
        }
    }

    /**
     * Reads multicast routes information from the kernel, and update the last used timestamp for
     * each multicast route save in this class.
     */
    private void refreshMfcInactiveDuration() {
        checkOnHandlerThread();
        final List<RtNetlinkRouteMessage> multicastRoutes = NetlinkUtils.getIpv6MulticastRoutes();

        for (var route : multicastRoutes) {
            if (!route.isResolved()) {
                continue; // Don't handle unresolved mfc, the kernel will recycle in 10s
            }
            Integer iif = getVirtualInterfaceIndex(route.getIifIndex());
            if (iif == null) {
                Log.e(TAG, "Can't find kernel returned IIF " + route.getIifIndex());
                return;
            }
            final MfcKey key =
                    new MfcKey(
                            iif,
                            (Inet6Address) route.getSource().getAddress(),
                            (Inet6Address) route.getDestination().getAddress());
            MfcValue value = mMfcs.get(key);
            if (value == null) {
                Log.e(TAG, "Can't find kernel returned MFC " + key);
                continue;
            }
            value.setLastUsedAt(
                    Instant.now(mDependencies.getClock())
                            .minusMillis(route.getSinceLastUseMillis()));
        }
    }

    /** Remove MFC entry from mMfcs map and the kernel if exists. */
    private void removeMfcFromKernel(MfcKey key) {
        checkOnHandlerThread();

        final MfcValue value = mMfcs.get(key);
        final Set<Integer> oifs = new ArraySet<>();
        final StructMf6cctl mf6cctl =
                new StructMf6cctl(key.mSrcAddr, key.mDstAddr, key.mIifVirtualIdx, oifs);
        try {
            mDependencies.setsockoptMrt6DelMfc(mMulticastRoutingFd, mf6cctl);
        } catch (ErrnoException e) {
            Log.e(TAG, "failed to remove MFC: " + e);
            return;
        }
        Log.d(TAG, "Removed MFC key: " + key + " value: " + value);
    }

    /**
     * This is called every MFC_INACTIVE_CHECK_INTERVAL_MS milliseconds to remove any MFC that is
     * inactive for more than MFC_INACTIVE_TIMEOUT_MS milliseconds.
     */
    private void maybeExpireMfcs() {
        checkOnHandlerThread();

        for (var it = mMfcs.entrySet().iterator(); it.hasNext(); ) {
            var entry = it.next();
            if (entry.getValue()
                    .getLastUsedAt()
                    .plusMillis(MFC_INACTIVE_TIMEOUT_MS)
                    .isBefore(Instant.now(mDependencies.getClock()))) {
                removeMfcFromKernel(entry.getKey());
                it.remove();
            }
        }
    }

    private void updateMfcs() {
        checkOnHandlerThread();

        for (Iterator<Map.Entry<MfcKey, MfcValue>> it = mMfcs.entrySet().iterator();
                it.hasNext(); ) {
            MfcKey key = it.next().getKey();
            if (!addOrUpdateMfc(key.mIifVirtualIdx, key.mSrcAddr, key.mDstAddr)) {
                removeMfcFromKernel(key);
                it.remove();
            }
        }

        refreshMfcInactiveDuration();
    }

    private void joinGroups(int ifIndex, List<Inet6Address> addresses) {
        for (Inet6Address address : addresses) {
            InetSocketAddress socketAddress = new InetSocketAddress(address, 0);
            try {
                mMulticastSocket.joinGroup(
                        socketAddress, mDependencies.getNetworkInterface(ifIndex));
            } catch (IOException e) {
                if (e.getCause() instanceof ErrnoException) {
                    ErrnoException ee = (ErrnoException) e.getCause();
                    if (ee.errno == EADDRINUSE) {
                        // The list of added address are calculated from address changes,
                        // repeated join group is unexpected
                        Log.e(TAG, "Already joined group" + e);
                        continue;
                    }
                }
                Log.e(TAG, "failed to join group: " + e);
            }
        }
    }

    private void leaveGroups(int ifIndex, List<Inet6Address> addresses) {
        for (Inet6Address address : addresses) {
            InetSocketAddress socketAddress = new InetSocketAddress(address, 0);
            try {
                mMulticastSocket.leaveGroup(
                        socketAddress, mDependencies.getNetworkInterface(ifIndex));
            } catch (IOException e) {
                Log.e(TAG, "failed to leave group: " + e);
            }
        }
    }

    private int getGroupAddressScope(Inet6Address address) {
        return address.getAddress()[1] & 0xf;
    }

    /**
     * Handles a NoCache upcall that indicates a multicast packet is received and requires
     * a multicast forwarding cache to be added.
     *
     * A forwarding or blocking MFC is added according to the multicast config.
     *
     * The number of MFCs is checked to make sure it doesn't exceed the
     * {@code MFC_MAX_NUMBER_OF_ENTRIES} limit.
     */
    @VisibleForTesting
    public void handleMulticastNocacheUpcall(final StructMrt6Msg mrt6Msg) {
        final int iifVid = mrt6Msg.mif;

        // add MFC to forward the packet or add blocking MFC to not forward the packet
        // If the packet comes from an interface the service doesn't care about, the
        // addOrUpdateMfc function will return null and not MFC will be added.
        if (!addOrUpdateMfc(iifVid, mrt6Msg.src, mrt6Msg.dst)) return;
        // If the list of MFCs is not empty and there is no MFC check scheduled,
        // schedule one now
        if (!mMfcPollingScheduled) {
            mHandler.postDelayed(() -> checkMfcsExpiration(), MFC_INACTIVE_CHECK_INTERVAL_MS);
            mMfcPollingScheduled = true;
        }

        checkMfcEntriesLimit();
    }

    /**
     * A packet reader that handles the packets sent to the multicast routing socket
     */
    private final class MulticastNocacheUpcallListener extends PacketReader {
        private final FileDescriptor mFd;

        public MulticastNocacheUpcallListener(Handler h, FileDescriptor fd) {
            super(h);
            mFd = fd;
        }

        @Override
        protected FileDescriptor createFd() {
            return mFd;
        }

        @Override
        protected void handlePacket(byte[] recvbuf, int length) {
            final ByteBuffer buf = ByteBuffer.wrap(recvbuf);
            final StructMrt6Msg mrt6Msg = StructMrt6Msg.parse(buf);
            if (mrt6Msg.msgType != StructMrt6Msg.MRT6MSG_NOCACHE) {
                return;
            }
            handleMulticastNocacheUpcall(mrt6Msg);
        }
    }

    /** Dependencies of RoutingCoordinatorService, for test injections. */
    @VisibleForTesting
    public static class Dependencies {
        private final Clock mClock = Clock.system(ZoneId.systemDefault());

        /**
         * Creates a socket to configure multicast routing in the kernel.
         *
         * <p>If the kernel doesn't support multicast routing, then the {@code setsockoptInt} with
         * {@code MRT6_INIT} method would fail.
         *
         * @return the multicast routing socket, or null if it fails to be created/configured.
         */
        public FileDescriptor createMulticastRoutingSocket() {
            FileDescriptor sock = null;
            byte[] filter = new byte[32]; // filter all ICMPv6 messages
            try {
                sock = Os.socket(AF_INET6, SOCK_RAW | SOCK_CLOEXEC | SOCK_NONBLOCK, IPPROTO_ICMPV6);
                Os.setsockoptInt(sock, IPPROTO_IPV6, MRT6_INIT, ONE);
                NetworkUtils.setsockoptBytes(sock, IPPROTO_ICMPV6, ICMP6_FILTER, filter);
            } catch (ErrnoException e) {
                Log.e(TAG, "failed to create multicast socket: " + e);
                if (sock != null) {
                    SocketUtils.closeSocketQuietly(sock);
                }
                throw new UnsupportedOperationException("Multicast routing is not supported ", e);
            }
            Log.i(TAG, "socket created for multicast routing: " + sock);
            return sock;
        }

        public MulticastSocket createMulticastSocket() {
            try {
                return new MulticastSocket();
            } catch (IOException e) {
                Log.wtf(TAG, "Failed to create multicast socket " + e);
                throw new IllegalStateException(e);
            }
        }

        public void setsockoptMrt6AddMif(FileDescriptor fd, StructMif6ctl mif6ctl)
                throws ErrnoException {
            final byte[] bytes = mif6ctl.writeToBytes();
            NetworkUtils.setsockoptBytes(fd, IPPROTO_IPV6, MRT6_ADD_MIF, bytes);
        }

        public void setsockoptMrt6DelMif(FileDescriptor fd, int virtualIfIndex)
                throws ErrnoException {
            Os.setsockoptInt(fd, IPPROTO_IPV6, MRT6_DEL_MIF, virtualIfIndex);
        }

        public void setsockoptMrt6AddMfc(FileDescriptor fd, StructMf6cctl mf6cctl)
                throws ErrnoException {
            final byte[] bytes = mf6cctl.writeToBytes();
            NetworkUtils.setsockoptBytes(fd, IPPROTO_IPV6, MRT6_ADD_MFC, bytes);
        }

        public void setsockoptMrt6DelMfc(FileDescriptor fd, StructMf6cctl mf6cctl)
                throws ErrnoException {
            final byte[] bytes = mf6cctl.writeToBytes();
            NetworkUtils.setsockoptBytes(fd, IPPROTO_IPV6, MRT6_DEL_MFC, bytes);
        }

        public Integer getInterfaceIndex(String ifName) {
            try {
                NetworkInterface ni = NetworkInterface.getByName(ifName);
                return ni.getIndex();
            } catch (NullPointerException | SocketException e) {
                return null;
            }
        }

        public NetworkInterface getNetworkInterface(int physicalIndex) {
            try {
                return NetworkInterface.getByIndex(physicalIndex);
            } catch (SocketException e) {
                return null;
            }
        }

        public Clock getClock() {
            return mClock;
        }
    }
}
