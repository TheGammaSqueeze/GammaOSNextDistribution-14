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

package com.android.server.ethernet;

import static android.net.EthernetManager.ETHERNET_STATE_DISABLED;
import static android.net.EthernetManager.ETHERNET_STATE_ENABLED;
import static android.net.TestNetworkManager.TEST_TAP_PREFIX;

import static com.android.internal.annotations.VisibleForTesting.Visibility.PACKAGE;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.net.EthernetManager;
import android.net.IEthernetServiceListener;
import android.net.INetd;
import android.net.ITetheredInterfaceCallback;
import android.net.InterfaceConfigurationParcel;
import android.net.IpConfiguration;
import android.net.IpConfiguration.IpAssignment;
import android.net.IpConfiguration.ProxySettings;
import android.net.LinkAddress;
import android.net.NetworkCapabilities;
import android.net.StaticIpConfiguration;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.system.OsConstants;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.IndentingPrintWriter;
import com.android.modules.utils.build.SdkLevel;
import com.android.net.module.util.NetdUtils;
import com.android.net.module.util.PermissionUtils;
import com.android.net.module.util.SharedLog;
import com.android.net.module.util.ip.NetlinkMonitor;
import com.android.net.module.util.netlink.NetlinkConstants;
import com.android.net.module.util.netlink.NetlinkMessage;
import com.android.net.module.util.netlink.RtNetlinkLinkMessage;
import com.android.net.module.util.netlink.StructIfinfoMsg;
import com.android.server.connectivity.ConnectivityResources;

import java.io.FileDescriptor;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks Ethernet interfaces and manages interface configurations.
 *
 * <p>Interfaces may have different {@link android.net.NetworkCapabilities}. This mapping is defined
 * in {@code config_ethernet_interfaces}. Notably, some interfaces could be marked as restricted by
 * not specifying {@link android.net.NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED} flag.
 * Interfaces could have associated {@link android.net.IpConfiguration}.
 * Ethernet Interfaces may be present at boot time or appear after boot (e.g., for Ethernet adapters
 * connected over USB). This class supports multiple interfaces. When an interface appears on the
 * system (or is present at boot time) this class will start tracking it and bring it up. Only
 * interfaces whose names match the {@code config_ethernet_iface_regex} regular expression are
 * tracked.
 *
 * <p>All public or package private methods must be thread-safe unless stated otherwise.
 */
@VisibleForTesting(visibility = PACKAGE)
public class EthernetTracker {
    private static final int INTERFACE_MODE_CLIENT = 1;
    private static final int INTERFACE_MODE_SERVER = 2;

    private static final String TAG = EthernetTracker.class.getSimpleName();
    private static final boolean DBG = EthernetNetworkFactory.DBG;

    private static final String TEST_IFACE_REGEXP = TEST_TAP_PREFIX + "\\d+";

    // TODO: consider using SharedLog consistently across ethernet service.
    private static final SharedLog sLog = new SharedLog(TAG);

    /**
     * Interface names we track. This is a product-dependent regular expression.
     * Use isValidEthernetInterface to check if a interface name is a valid ethernet interface (this
     * includes test interfaces if setIncludeTestInterfaces is set to true).
     */
    private final String mIfaceMatch;

    /**
     * Track test interfaces if true, don't track otherwise.
     * Volatile is needed as getInterfaceList() does not run on the handler thread.
     */
    private volatile boolean mIncludeTestInterfaces = false;

    /** Mapping between {iface name | mac address} -> {NetworkCapabilities} */
    private final ConcurrentHashMap<String, NetworkCapabilities> mNetworkCapabilities =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, IpConfiguration> mIpConfigurations =
            new ConcurrentHashMap<>();

    private final Context mContext;
    private final INetd mNetd;
    private final Handler mHandler;
    private final EthernetNetworkFactory mFactory;
    private final EthernetConfigStore mConfigStore;
    private final NetlinkMonitor mNetlinkMonitor;
    private final Dependencies mDeps;

    private final RemoteCallbackList<IEthernetServiceListener> mListeners =
            new RemoteCallbackList<>();
    private final TetheredInterfaceRequestList mTetheredInterfaceRequests =
            new TetheredInterfaceRequestList();

    // The first interface discovered is set as the mTetheringInterface. It is the interface that is
    // returned when a tethered interface is requested; until then, it remains in client mode. Its
    // current mode is reflected in mTetheringInterfaceMode.
    private String mTetheringInterface;
    private int mTetheringInterfaceMode = INTERFACE_MODE_CLIENT;
    // Tracks whether clients were notified that the tethered interface is available
    private boolean mTetheredInterfaceWasAvailable = false;

    private int mEthernetState = ETHERNET_STATE_ENABLED;

    private class TetheredInterfaceRequestList extends
            RemoteCallbackList<ITetheredInterfaceCallback> {
        @Override
        public void onCallbackDied(ITetheredInterfaceCallback cb, Object cookie) {
            mHandler.post(EthernetTracker.this::maybeUntetherInterface);
        }
    }

    public static class Dependencies {
        public String getInterfaceRegexFromResource(Context context) {
            final ConnectivityResources resources = new ConnectivityResources(context);
            return resources.get().getString(
                    com.android.connectivity.resources.R.string.config_ethernet_iface_regex);
        }

        public String[] getInterfaceConfigFromResource(Context context) {
            final ConnectivityResources resources = new ConnectivityResources(context);
            return resources.get().getStringArray(
                    com.android.connectivity.resources.R.array.config_ethernet_interfaces);
        }
    }

    private class EthernetNetlinkMonitor extends NetlinkMonitor {
        EthernetNetlinkMonitor(Handler handler) {
            super(handler, sLog, EthernetNetlinkMonitor.class.getSimpleName(),
                    OsConstants.NETLINK_ROUTE, NetlinkConstants.RTMGRP_LINK);
        }

        private void onNewLink(String ifname, boolean linkUp) {
            if (!mFactory.hasInterface(ifname) && !ifname.equals(mTetheringInterface)) {
                Log.i(TAG, "onInterfaceAdded, iface: " + ifname);
                maybeTrackInterface(ifname);
            }
            Log.i(TAG, "interfaceLinkStateChanged, iface: " + ifname + ", up: " + linkUp);
            updateInterfaceState(ifname, linkUp);
        }

        private void onDelLink(String ifname) {
            Log.i(TAG, "onInterfaceRemoved, iface: " + ifname);
            stopTrackingInterface(ifname);
        }

        private void processRtNetlinkLinkMessage(RtNetlinkLinkMessage msg) {
            final StructIfinfoMsg ifinfomsg = msg.getIfinfoHeader();
            // check if the message is valid
            if (ifinfomsg.family != OsConstants.AF_UNSPEC) return;

            // ignore messages for the loopback interface
            if ((ifinfomsg.flags & OsConstants.IFF_LOOPBACK) != 0) return;

            // check if the received message applies to an ethernet interface.
            final String ifname = msg.getInterfaceName();
            if (!isValidEthernetInterface(ifname)) return;

            switch (msg.getHeader().nlmsg_type) {
                case NetlinkConstants.RTM_NEWLINK:
                    final boolean linkUp = (ifinfomsg.flags & NetlinkConstants.IFF_LOWER_UP) != 0;
                    onNewLink(ifname, linkUp);
                    break;

                case NetlinkConstants.RTM_DELLINK:
                    onDelLink(ifname);
                    break;

                default:
                    Log.e(TAG, "Unknown rtnetlink link msg type: " + msg);
                    break;
            }
        }

        // Note: processNetlinkMessage is called on the handler thread.
        @Override
        protected void processNetlinkMessage(NetlinkMessage nlMsg, long whenMs) {
            // ignore all updates when ethernet is disabled.
            if (mEthernetState == ETHERNET_STATE_DISABLED) return;

            if (nlMsg instanceof RtNetlinkLinkMessage) {
                processRtNetlinkLinkMessage((RtNetlinkLinkMessage) nlMsg);
            } else {
                Log.e(TAG, "Unknown netlink message: " + nlMsg);
            }
        }
    }


    EthernetTracker(@NonNull final Context context, @NonNull final Handler handler,
            @NonNull final EthernetNetworkFactory factory, @NonNull final INetd netd) {
        this(context, handler, factory, netd, new Dependencies());
    }

    @VisibleForTesting
    EthernetTracker(@NonNull final Context context, @NonNull final Handler handler,
            @NonNull final EthernetNetworkFactory factory, @NonNull final INetd netd,
            @NonNull final Dependencies deps) {
        mContext = context;
        mHandler = handler;
        mFactory = factory;
        mNetd = netd;
        mDeps = deps;

        // Interface match regex.
        String ifaceMatchRegex = mDeps.getInterfaceRegexFromResource(mContext);
        // "*" is a magic string to indicate "pick the default".
        if (ifaceMatchRegex.equals("*")) {
            if (SdkLevel.isAtLeastV()) {
                // On V+, include both usb%d and eth%d interfaces.
                ifaceMatchRegex = "(usb|eth)\\d+";
            } else {
                // On T and U, include only eth%d interfaces.
                ifaceMatchRegex = "eth\\d+";
            }
        }
        mIfaceMatch = ifaceMatchRegex;

        // Read default Ethernet interface configuration from resources
        final String[] interfaceConfigs = mDeps.getInterfaceConfigFromResource(context);
        for (String strConfig : interfaceConfigs) {
            parseEthernetConfig(strConfig);
        }

        mConfigStore = new EthernetConfigStore();
        mNetlinkMonitor = new EthernetNetlinkMonitor(mHandler);
    }

    void start() {
        mFactory.register();
        mConfigStore.read();

        final ArrayMap<String, IpConfiguration> configs = mConfigStore.getIpConfigurations();
        for (int i = 0; i < configs.size(); i++) {
            mIpConfigurations.put(configs.keyAt(i), configs.valueAt(i));
        }

        mHandler.post(() -> {
            mNetlinkMonitor.start();
            trackAvailableInterfaces();
        });
    }

    void updateIpConfiguration(String iface, IpConfiguration ipConfiguration) {
        if (DBG) {
            Log.i(TAG, "updateIpConfiguration, iface: " + iface + ", cfg: " + ipConfiguration);
        }
        writeIpConfiguration(iface, ipConfiguration);
        mHandler.post(() -> {
            mFactory.updateInterface(iface, ipConfiguration, null);
            broadcastInterfaceStateChange(iface);
        });
    }

    private void writeIpConfiguration(@NonNull final String iface,
            @NonNull final IpConfiguration ipConfig) {
        mConfigStore.write(iface, ipConfig);
        mIpConfigurations.put(iface, ipConfig);
    }

    private IpConfiguration getIpConfigurationForCallback(String iface, int state) {
        return (state == EthernetManager.STATE_ABSENT) ? null : getOrCreateIpConfiguration(iface);
    }

    private void ensureRunningOnEthernetServiceThread() {
        if (mHandler.getLooper().getThread() != Thread.currentThread()) {
            throw new IllegalStateException(
                    "Not running on EthernetService thread: "
                            + Thread.currentThread().getName());
        }
    }

    /**
     * Broadcast the link state or IpConfiguration change of existing Ethernet interfaces to all
     * listeners.
     */
    protected void broadcastInterfaceStateChange(@NonNull String iface) {
        ensureRunningOnEthernetServiceThread();
        final int state = getInterfaceState(iface);
        final int role = getInterfaceRole(iface);
        final IpConfiguration config = getIpConfigurationForCallback(iface, state);
        final boolean isRestricted = isRestrictedInterface(iface);
        final int n = mListeners.beginBroadcast();
        for (int i = 0; i < n; i++) {
            try {
                if (isRestricted) {
                    final ListenerInfo info = (ListenerInfo) mListeners.getBroadcastCookie(i);
                    if (!info.canUseRestrictedNetworks) continue;
                }
                mListeners.getBroadcastItem(i).onInterfaceStateChanged(iface, state, role, config);
            } catch (RemoteException e) {
                // Do nothing here.
            }
        }
        mListeners.finishBroadcast();
    }

    /**
     * Unicast the interface state or IpConfiguration change of existing Ethernet interfaces to a
     * specific listener.
     */
    protected void unicastInterfaceStateChange(@NonNull IEthernetServiceListener listener,
            @NonNull String iface) {
        ensureRunningOnEthernetServiceThread();
        final int state = getInterfaceState(iface);
        final int role = getInterfaceRole(iface);
        final IpConfiguration config = getIpConfigurationForCallback(iface, state);
        try {
            listener.onInterfaceStateChanged(iface, state, role, config);
        } catch (RemoteException e) {
            // Do nothing here.
        }
    }

    @VisibleForTesting(visibility = PACKAGE)
    protected void updateConfiguration(@NonNull final String iface,
            @Nullable final IpConfiguration ipConfig,
            @Nullable final NetworkCapabilities capabilities,
            @Nullable final EthernetCallback cb) {
        if (DBG) {
            Log.i(TAG, "updateConfiguration, iface: " + iface + ", capabilities: " + capabilities
                    + ", ipConfig: " + ipConfig);
        }

        // TODO: do the right thing if the interface was in server mode: either fail this operation,
        // or take the interface out of server mode.
        final IpConfiguration localIpConfig = ipConfig == null
                ? null : new IpConfiguration(ipConfig);
        if (ipConfig != null) {
            writeIpConfiguration(iface, localIpConfig);
        }

        if (null != capabilities) {
            mNetworkCapabilities.put(iface, capabilities);
        }
        mHandler.post(() -> {
            mFactory.updateInterface(iface, localIpConfig, capabilities);

            // only broadcast state change when the ip configuration is updated.
            if (ipConfig != null) {
                broadcastInterfaceStateChange(iface);
            }
            // Always return success. Even if the interface does not currently exist, the
            // IpConfiguration and NetworkCapabilities were saved and will be applied if an
            // interface with the given name is ever added.
            cb.onResult(iface);
        });
    }

    @VisibleForTesting(visibility = PACKAGE)
    protected void setInterfaceEnabled(@NonNull final String iface, boolean enabled,
            @Nullable final EthernetCallback cb) {
        mHandler.post(() -> updateInterfaceState(iface, enabled, cb));
    }

    IpConfiguration getIpConfiguration(String iface) {
        return mIpConfigurations.get(iface);
    }

    @VisibleForTesting(visibility = PACKAGE)
    protected boolean isTrackingInterface(String iface) {
        return mFactory.hasInterface(iface);
    }

    String[] getClientModeInterfaces(boolean includeRestricted) {
        return mFactory.getAvailableInterfaces(includeRestricted);
    }

    List<String> getInterfaceList() {
        final List<String> interfaceList = new ArrayList<String>();
        final String[] ifaces;
        try {
            ifaces = mNetd.interfaceGetList();
        } catch (RemoteException e) {
            Log.e(TAG, "Could not get list of interfaces " + e);
            return interfaceList;
        }

        // There is a possible race with setIncludeTestInterfaces() which can affect
        // isValidEthernetInterface (it returns true for test interfaces if setIncludeTestInterfaces
        // is set to true).
        // setIncludeTestInterfaces() is only used in tests, and since getInterfaceList() does not
        // run on the handler thread, the behavior around setIncludeTestInterfaces() is
        // indeterminate either way. This can easily be circumvented by waiting on a callback from
        // a test interface after calling setIncludeTestInterfaces() before calling this function.
        // In production code, this has no effect.
        for (String iface : ifaces) {
            if (isValidEthernetInterface(iface)) interfaceList.add(iface);
        }
        return interfaceList;
    }

    /**
     * Returns true if given interface was configured as restricted (doesn't have
     * NET_CAPABILITY_NOT_RESTRICTED) capability. Otherwise, returns false.
     */
    boolean isRestrictedInterface(String iface) {
        final NetworkCapabilities nc = mNetworkCapabilities.get(iface);
        return nc != null && !nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED);
    }

    void addListener(IEthernetServiceListener listener, boolean canUseRestrictedNetworks) {
        mHandler.post(() -> {
            if (!mListeners.register(listener, new ListenerInfo(canUseRestrictedNetworks))) {
                // Remote process has already died
                return;
            }
            for (String iface : getClientModeInterfaces(canUseRestrictedNetworks)) {
                unicastInterfaceStateChange(listener, iface);
            }
            if (mTetheringInterface != null && mTetheringInterfaceMode == INTERFACE_MODE_SERVER) {
                unicastInterfaceStateChange(listener, mTetheringInterface);
            }

            unicastEthernetStateChange(listener, mEthernetState);
        });
    }

    void removeListener(IEthernetServiceListener listener) {
        mHandler.post(() -> mListeners.unregister(listener));
    }

    public void setIncludeTestInterfaces(boolean include) {
        mHandler.post(() -> {
            mIncludeTestInterfaces = include;
            if (!include) {
                removeTestData();
            }
            mHandler.post(() -> trackAvailableInterfaces());
        });
    }

    private void removeTestData() {
        removeTestIpData();
        removeTestCapabilityData();
    }

    private void removeTestIpData() {
        final Iterator<String> iterator = mIpConfigurations.keySet().iterator();
        while (iterator.hasNext()) {
            final String iface = iterator.next();
            if (iface.matches(TEST_IFACE_REGEXP)) {
                mConfigStore.write(iface, null);
                iterator.remove();
            }
        }
    }

    private void removeTestCapabilityData() {
        mNetworkCapabilities.keySet().removeIf(iface -> iface.matches(TEST_IFACE_REGEXP));
    }

    public void requestTetheredInterface(ITetheredInterfaceCallback callback) {
        mHandler.post(() -> {
            if (!mTetheredInterfaceRequests.register(callback)) {
                // Remote process has already died
                return;
            }
            if (mTetheringInterfaceMode == INTERFACE_MODE_SERVER) {
                if (mTetheredInterfaceWasAvailable) {
                    notifyTetheredInterfaceAvailable(callback, mTetheringInterface);
                }
                return;
            }

            setTetheringInterfaceMode(INTERFACE_MODE_SERVER);
        });
    }

    public void releaseTetheredInterface(ITetheredInterfaceCallback callback) {
        mHandler.post(() -> {
            mTetheredInterfaceRequests.unregister(callback);
            maybeUntetherInterface();
        });
    }

    private void notifyTetheredInterfaceAvailable(ITetheredInterfaceCallback cb, String iface) {
        try {
            cb.onAvailable(iface);
        } catch (RemoteException e) {
            Log.e(TAG, "Error sending tethered interface available callback", e);
        }
    }

    private void notifyTetheredInterfaceUnavailable(ITetheredInterfaceCallback cb) {
        try {
            cb.onUnavailable();
        } catch (RemoteException e) {
            Log.e(TAG, "Error sending tethered interface available callback", e);
        }
    }

    private void maybeUntetherInterface() {
        if (mTetheredInterfaceRequests.getRegisteredCallbackCount() > 0) return;
        if (mTetheringInterfaceMode == INTERFACE_MODE_CLIENT) return;
        setTetheringInterfaceMode(INTERFACE_MODE_CLIENT);
    }

    private void setTetheringInterfaceMode(int mode) {
        Log.d(TAG, "Setting tethering interface mode to " + mode);
        mTetheringInterfaceMode = mode;
        if (mTetheringInterface != null) {
            removeInterface(mTetheringInterface);
            addInterface(mTetheringInterface);
            // when this broadcast is sent, any calls to notifyTetheredInterfaceAvailable or
            // notifyTetheredInterfaceUnavailable have already happened
            broadcastInterfaceStateChange(mTetheringInterface);
        }
    }

    private int getInterfaceState(final String iface) {
        if (mFactory.hasInterface(iface)) {
            return mFactory.getInterfaceState(iface);
        }
        if (getInterfaceMode(iface) == INTERFACE_MODE_SERVER) {
            // server mode interfaces are not tracked by the factory.
            // TODO(b/234743836): interface state for server mode interfaces is not tracked
            // properly; just return link up.
            return EthernetManager.STATE_LINK_UP;
        }
        return EthernetManager.STATE_ABSENT;
    }

    private int getInterfaceRole(final String iface) {
        if (mFactory.hasInterface(iface)) {
            // only client mode interfaces are tracked by the factory.
            return EthernetManager.ROLE_CLIENT;
        }
        if (getInterfaceMode(iface) == INTERFACE_MODE_SERVER) {
            return EthernetManager.ROLE_SERVER;
        }
        return EthernetManager.ROLE_NONE;
    }

    private int getInterfaceMode(final String iface) {
        if (iface.equals(mTetheringInterface)) {
            return mTetheringInterfaceMode;
        }
        return INTERFACE_MODE_CLIENT;
    }

    private void removeInterface(String iface) {
        mFactory.removeInterface(iface);
        maybeUpdateServerModeInterfaceState(iface, false);
    }

    private void stopTrackingInterface(String iface) {
        removeInterface(iface);
        if (iface.equals(mTetheringInterface)) {
            mTetheringInterface = null;
        }
        broadcastInterfaceStateChange(iface);
    }

    private void addInterface(String iface) {
        InterfaceConfigurationParcel config = null;
        // Bring up the interface so we get link status indications.
        try {
            PermissionUtils.enforceNetworkStackPermission(mContext);
            // Read the flags before attempting to bring up the interface. If the interface is
            // already running an UP event is created after adding the interface.
            config = NetdUtils.getInterfaceConfigParcel(mNetd, iface);
            if (NetdUtils.hasFlag(config, INetd.IF_STATE_DOWN)) {
                // As a side-effect, NetdUtils#setInterfaceUp() also clears the interface's IPv4
                // address and readds it which *could* lead to unexpected behavior in the future.
                NetdUtils.setInterfaceUp(mNetd, iface);
            }
        } catch (IllegalStateException e) {
            // Either the system is crashing or the interface has disappeared. Just ignore the
            // error; we haven't modified any state because we only do that if our calls succeed.
            Log.e(TAG, "Error upping interface " + iface, e);
        }

        if (config == null) {
            Log.e(TAG, "Null interface config parcelable for " + iface + ". Bailing out.");
            return;
        }

        if (getInterfaceMode(iface) == INTERFACE_MODE_SERVER) {
            maybeUpdateServerModeInterfaceState(iface, true);
            return;
        }

        final String hwAddress = config.hwAddr;

        NetworkCapabilities nc = mNetworkCapabilities.get(iface);
        if (nc == null) {
            // Try to resolve using mac address
            nc = mNetworkCapabilities.get(hwAddress);
            if (nc == null) {
                final boolean isTestIface = iface.matches(TEST_IFACE_REGEXP);
                nc = createDefaultNetworkCapabilities(isTestIface);
            }
        }

        IpConfiguration ipConfiguration = getOrCreateIpConfiguration(iface);
        Log.d(TAG, "Tracking interface in client mode: " + iface);
        mFactory.addInterface(iface, hwAddress, ipConfiguration, nc);

        // Note: if the interface already has link (e.g., if we crashed and got
        // restarted while it was running), we need to fake a link up notification so we
        // start configuring it.
        if (NetdUtils.hasFlag(config, INetd.IF_FLAG_RUNNING)) {
            // no need to send an interface state change as this is not a true "state change". The
            // callers (maybeTrackInterface() and setTetheringInterfaceMode()) already broadcast the
            // state change.
            mFactory.updateInterfaceLinkState(iface, true);
        }
    }

    private void updateInterfaceState(String iface, boolean up) {
        updateInterfaceState(iface, up, new EthernetCallback(null /* cb */));
    }

    // TODO(b/225315248): enable/disableInterface() should not affect link state.
    private void updateInterfaceState(String iface, boolean up, EthernetCallback cb) {
        final int mode = getInterfaceMode(iface);
        if (mode == INTERFACE_MODE_SERVER || !mFactory.hasInterface(iface)) {
            // The interface is in server mode or is not tracked.
            cb.onError("Failed to set link state " + (up ? "up" : "down") + " for " + iface);
            return;
        }

        if (mFactory.updateInterfaceLinkState(iface, up)) {
            broadcastInterfaceStateChange(iface);
        }
        // If updateInterfaceLinkState returns false, the interface is already in the correct state.
        // Always return success.
        cb.onResult(iface);
    }

    private void maybeUpdateServerModeInterfaceState(String iface, boolean available) {
        if (available == mTetheredInterfaceWasAvailable || !iface.equals(mTetheringInterface)) {
            return;
        }

        Log.d(TAG, (available ? "Tracking" : "No longer tracking")
                + " interface in server mode: " + iface);

        final int pendingCbs = mTetheredInterfaceRequests.beginBroadcast();
        for (int i = 0; i < pendingCbs; i++) {
            ITetheredInterfaceCallback item = mTetheredInterfaceRequests.getBroadcastItem(i);
            if (available) {
                notifyTetheredInterfaceAvailable(item, iface);
            } else {
                notifyTetheredInterfaceUnavailable(item);
            }
        }
        mTetheredInterfaceRequests.finishBroadcast();
        mTetheredInterfaceWasAvailable = available;
    }

    private void maybeTrackInterface(String iface) {
        if (!isValidEthernetInterface(iface)) {
            return;
        }

        // If we don't already track this interface, and if this interface matches
        // our regex, start tracking it.
        if (mFactory.hasInterface(iface) || iface.equals(mTetheringInterface)) {
            if (DBG) Log.w(TAG, "Ignoring already-tracked interface " + iface);
            return;
        }
        if (DBG) Log.i(TAG, "maybeTrackInterface: " + iface);

        // Do not use an interface for tethering if it has configured NetworkCapabilities.
        if (mTetheringInterface == null && !mNetworkCapabilities.containsKey(iface)) {
            mTetheringInterface = iface;
        }

        addInterface(iface);

        broadcastInterfaceStateChange(iface);
    }

    private void trackAvailableInterfaces() {
        try {
            final String[] ifaces = mNetd.interfaceGetList();
            for (String iface : ifaces) {
                maybeTrackInterface(iface);
            }
        } catch (RemoteException | ServiceSpecificException e) {
            Log.e(TAG, "Could not get list of interfaces " + e);
        }
    }

    private static class ListenerInfo {

        boolean canUseRestrictedNetworks = false;

        ListenerInfo(boolean canUseRestrictedNetworks) {
            this.canUseRestrictedNetworks = canUseRestrictedNetworks;
        }
    }

    /**
     * Parses an Ethernet interface configuration
     *
     * @param configString represents an Ethernet configuration in the following format: {@code
     * <interface name|mac address>;[Network Capabilities];[IP config];[Override Transport]}
     */
    private void parseEthernetConfig(String configString) {
        final EthernetTrackerConfig config = createEthernetTrackerConfig(configString);
        NetworkCapabilities nc = createNetworkCapabilities(
                !TextUtils.isEmpty(config.mCapabilities)  /* clear default capabilities */,
                config.mCapabilities, config.mTransport).build();
        mNetworkCapabilities.put(config.mIface, nc);

        if (null != config.mIpConfig) {
            IpConfiguration ipConfig = parseStaticIpConfiguration(config.mIpConfig);
            mIpConfigurations.put(config.mIface, ipConfig);
        }
    }

    @VisibleForTesting
    static EthernetTrackerConfig createEthernetTrackerConfig(@NonNull final String configString) {
        Objects.requireNonNull(configString, "EthernetTrackerConfig requires non-null config");
        return new EthernetTrackerConfig(configString.split(";", /* limit of tokens */ 4));
    }

    private static NetworkCapabilities createDefaultNetworkCapabilities(boolean isTestIface) {
        NetworkCapabilities.Builder builder = createNetworkCapabilities(
                false /* clear default capabilities */, null, null)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_CONGESTED)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VCN_MANAGED);

        if (isTestIface) {
            builder.addTransportType(NetworkCapabilities.TRANSPORT_TEST);
        } else {
            builder.addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        }

        return builder.build();
    }

    /**
     * Parses a static list of network capabilities
     *
     * @param clearDefaultCapabilities Indicates whether or not to clear any default capabilities
     * @param commaSeparatedCapabilities A comma separated string list of integer encoded
     *                                   NetworkCapability.NET_CAPABILITY_* values
     * @param overrideTransport A string representing a single integer encoded override transport
     *                          type. Must be one of the NetworkCapability.TRANSPORT_*
     *                          values. TRANSPORT_VPN is not supported. Errors with input
     *                          will cause the override to be ignored.
     */
    @VisibleForTesting
    static NetworkCapabilities.Builder createNetworkCapabilities(
            boolean clearDefaultCapabilities, @Nullable String commaSeparatedCapabilities,
            @Nullable String overrideTransport) {

        final NetworkCapabilities.Builder builder = clearDefaultCapabilities
                ? NetworkCapabilities.Builder.withoutDefaultCapabilities()
                : new NetworkCapabilities.Builder();

        // Determine the transport type. If someone has tried to define an override transport then
        // attempt to add it. Since we can only have one override, all errors with it will
        // gracefully default back to TRANSPORT_ETHERNET and warn the user. VPN is not allowed as an
        // override type. Wifi Aware and LoWPAN are currently unsupported as well.
        int transport = NetworkCapabilities.TRANSPORT_ETHERNET;
        if (!TextUtils.isEmpty(overrideTransport)) {
            try {
                int parsedTransport = Integer.valueOf(overrideTransport);
                if (parsedTransport == NetworkCapabilities.TRANSPORT_VPN
                        || parsedTransport == NetworkCapabilities.TRANSPORT_WIFI_AWARE
                        || parsedTransport == NetworkCapabilities.TRANSPORT_LOWPAN) {
                    Log.e(TAG, "Override transport '" + parsedTransport + "' is not supported. "
                            + "Defaulting to TRANSPORT_ETHERNET");
                } else {
                    transport = parsedTransport;
                }
            } catch (NumberFormatException nfe) {
                Log.e(TAG, "Override transport type '" + overrideTransport + "' "
                        + "could not be parsed. Defaulting to TRANSPORT_ETHERNET");
            }
        }

        // Apply the transport. If the user supplied a valid number that is not a valid transport
        // then adding will throw an exception. Default back to TRANSPORT_ETHERNET if that happens
        try {
            builder.addTransportType(transport);
        } catch (IllegalArgumentException iae) {
            Log.e(TAG, transport + " is not a valid NetworkCapability.TRANSPORT_* value. "
                    + "Defaulting to TRANSPORT_ETHERNET");
            builder.addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET);
        }

        builder.setLinkUpstreamBandwidthKbps(100 * 1000);
        builder.setLinkDownstreamBandwidthKbps(100 * 1000);

        if (!TextUtils.isEmpty(commaSeparatedCapabilities)) {
            for (String strNetworkCapability : commaSeparatedCapabilities.split(",")) {
                if (!TextUtils.isEmpty(strNetworkCapability)) {
                    try {
                        builder.addCapability(Integer.valueOf(strNetworkCapability));
                    } catch (NumberFormatException nfe) {
                        Log.e(TAG, "Capability '" + strNetworkCapability + "' could not be parsed");
                    } catch (IllegalArgumentException iae) {
                        Log.e(TAG, strNetworkCapability + " is not a valid "
                                + "NetworkCapability.NET_CAPABILITY_* value");
                    }
                }
            }
        }
        // Ethernet networks have no way to update the following capabilities, so they always
        // have them.
        builder.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING);
        builder.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_CONGESTED);
        builder.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED);

        return builder;
    }

    /**
     * Parses static IP configuration.
     *
     * @param staticIpConfig represents static IP configuration in the following format: {@code
     * ip=<ip-address/mask> gateway=<ip-address> dns=<comma-sep-ip-addresses>
     *     domains=<comma-sep-domains>}
     */
    @VisibleForTesting
    static IpConfiguration parseStaticIpConfiguration(String staticIpConfig) {
        final StaticIpConfiguration.Builder staticIpConfigBuilder =
                new StaticIpConfiguration.Builder();

        for (String keyValueAsString : staticIpConfig.trim().split(" ")) {
            if (TextUtils.isEmpty(keyValueAsString)) continue;

            String[] pair = keyValueAsString.split("=");
            if (pair.length != 2) {
                throw new IllegalArgumentException("Unexpected token: " + keyValueAsString
                        + " in " + staticIpConfig);
            }

            String key = pair[0];
            String value = pair[1];

            switch (key) {
                case "ip":
                    staticIpConfigBuilder.setIpAddress(new LinkAddress(value));
                    break;
                case "domains":
                    staticIpConfigBuilder.setDomains(value);
                    break;
                case "gateway":
                    staticIpConfigBuilder.setGateway(InetAddress.parseNumericAddress(value));
                    break;
                case "dns": {
                    ArrayList<InetAddress> dnsAddresses = new ArrayList<>();
                    for (String address: value.split(",")) {
                        dnsAddresses.add(InetAddress.parseNumericAddress(address));
                    }
                    staticIpConfigBuilder.setDnsServers(dnsAddresses);
                    break;
                }
                default : {
                    throw new IllegalArgumentException("Unexpected key: " + key
                            + " in " + staticIpConfig);
                }
            }
        }
        return createIpConfiguration(staticIpConfigBuilder.build());
    }

    private static IpConfiguration createIpConfiguration(
            @NonNull final StaticIpConfiguration staticIpConfig) {
        return new IpConfiguration.Builder().setStaticIpConfiguration(staticIpConfig).build();
    }

    private IpConfiguration getOrCreateIpConfiguration(String iface) {
        IpConfiguration ret = mIpConfigurations.get(iface);
        if (ret != null) return ret;
        ret = new IpConfiguration();
        ret.setIpAssignment(IpAssignment.DHCP);
        ret.setProxySettings(ProxySettings.NONE);
        return ret;
    }

    private boolean isValidEthernetInterface(String iface) {
        return iface.matches(mIfaceMatch) || isValidTestInterface(iface);
    }

    /**
     * Validate if a given interface is valid for testing.
     *
     * @param iface the name of the interface to validate.
     * @return {@code true} if test interfaces are enabled and the given {@code iface} has a test
     * interface prefix, {@code false} otherwise.
     */
    public boolean isValidTestInterface(@NonNull final String iface) {
        return mIncludeTestInterfaces && iface.matches(TEST_IFACE_REGEXP);
    }

    private void postAndWaitForRunnable(Runnable r) {
        final ConditionVariable cv = new ConditionVariable();
        if (mHandler.post(() -> {
            r.run();
            cv.open();
        })) {
            cv.block(2000L);
        }
    }

    @VisibleForTesting(visibility = PACKAGE)
    protected void setEthernetEnabled(boolean enabled) {
        mHandler.post(() -> {
            int newState = enabled ? ETHERNET_STATE_ENABLED : ETHERNET_STATE_DISABLED;
            if (mEthernetState == newState) return;

            mEthernetState = newState;

            if (enabled) {
                trackAvailableInterfaces();
            } else {
                // TODO: maybe also disable server mode interface as well.
                untrackFactoryInterfaces();
            }
            broadcastEthernetStateChange(mEthernetState);
        });
    }

    private void untrackFactoryInterfaces() {
        for (String iface : mFactory.getAvailableInterfaces(true /* includeRestricted */)) {
            stopTrackingInterface(iface);
        }
    }

    private void unicastEthernetStateChange(@NonNull IEthernetServiceListener listener,
            int state) {
        ensureRunningOnEthernetServiceThread();
        try {
            listener.onEthernetStateChanged(state);
        } catch (RemoteException e) {
            // Do nothing here.
        }
    }

    private void broadcastEthernetStateChange(int state) {
        ensureRunningOnEthernetServiceThread();
        final int n = mListeners.beginBroadcast();
        for (int i = 0; i < n; i++) {
            try {
                mListeners.getBroadcastItem(i).onEthernetStateChanged(state);
            } catch (RemoteException e) {
                // Do nothing here.
            }
        }
        mListeners.finishBroadcast();
    }

    void dump(FileDescriptor fd, IndentingPrintWriter pw, String[] args) {
        postAndWaitForRunnable(() -> {
            pw.println(getClass().getSimpleName());
            pw.println("Ethernet State: "
                    + (mEthernetState == ETHERNET_STATE_ENABLED ? "enabled" : "disabled"));
            pw.println("Ethernet interface name filter: " + mIfaceMatch);
            pw.println("Interface used for tethering: " + mTetheringInterface);
            pw.println("Tethering interface mode: " + mTetheringInterfaceMode);
            pw.println("Tethered interface requests: "
                    + mTetheredInterfaceRequests.getRegisteredCallbackCount());
            pw.println("Listeners: " + mListeners.getRegisteredCallbackCount());
            pw.println("IP Configurations:");
            pw.increaseIndent();
            for (String iface : mIpConfigurations.keySet()) {
                pw.println(iface + ": " + mIpConfigurations.get(iface));
            }
            pw.decreaseIndent();
            pw.println();

            pw.println("Network Capabilities:");
            pw.increaseIndent();
            for (String iface : mNetworkCapabilities.keySet()) {
                pw.println(iface + ": " + mNetworkCapabilities.get(iface));
            }
            pw.decreaseIndent();
            pw.println();

            mFactory.dump(fd, pw, args);
        });
    }

    @VisibleForTesting
    static class EthernetTrackerConfig {
        final String mIface;
        final String mCapabilities;
        final String mIpConfig;
        final String mTransport;

        EthernetTrackerConfig(@NonNull final String[] tokens) {
            Objects.requireNonNull(tokens, "EthernetTrackerConfig requires non-null tokens");
            mIface = tokens[0];
            mCapabilities = tokens.length > 1 ? tokens[1] : null;
            mIpConfig = tokens.length > 2 && !TextUtils.isEmpty(tokens[2]) ? tokens[2] : null;
            mTransport = tokens.length > 3 ? tokens[3] : null;
        }
    }
}
