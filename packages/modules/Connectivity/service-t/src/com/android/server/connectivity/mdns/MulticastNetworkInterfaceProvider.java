/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.server.connectivity.mdns;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.net.Network;

import com.android.internal.annotations.VisibleForTesting;
import com.android.net.module.util.SharedLog;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * This class is used by the {@link MdnsSocket} to monitor the list of {@link NetworkInterface}
 * instances that are currently available for multi-cast messaging.
 */
public class MulticastNetworkInterfaceProvider {

    private static final String TAG = "MdnsNIProvider";
    private final SharedLog sharedLog;
    private static final boolean PREFER_IPV6 = MdnsConfigs.preferIpv6();

    private final List<NetworkInterfaceWrapper> multicastNetworkInterfaces = new ArrayList<>();
    // Only modifiable from tests.
    @VisibleForTesting
    ConnectivityMonitor connectivityMonitor;
    private volatile boolean connectivityChanged = true;

    @SuppressWarnings("nullness:methodref.receiver.bound")
    public MulticastNetworkInterfaceProvider(@NonNull Context context,
            @NonNull SharedLog sharedLog) {
        this.sharedLog = sharedLog;
        // IMPORT CHANGED
        this.connectivityMonitor = new ConnectivityMonitorWithConnectivityManager(
                context, this::onConnectivityChanged, sharedLog);
    }

    private synchronized void onConnectivityChanged() {
        connectivityChanged = true;
    }

    /**
     * Starts monitoring changes of connectivity of this device, which may indicate that the list of
     * network interfaces available for multi-cast messaging has changed.
     */
    public void startWatchingConnectivityChanges() {
        connectivityMonitor.startWatchingConnectivityChanges();
    }

    /** Stops monitoring changes of connectivity. */
    public void stopWatchingConnectivityChanges() {
        connectivityMonitor.stopWatchingConnectivityChanges();
    }

    /**
     * Returns the list of {@link NetworkInterfaceWrapper} instances available for multi-cast
     * messaging.
     */
    public synchronized List<NetworkInterfaceWrapper> getMulticastNetworkInterfaces() {
        if (connectivityChanged) {
            connectivityChanged = false;
            updateMulticastNetworkInterfaces();
            if (multicastNetworkInterfaces.isEmpty()) {
                sharedLog.log("No network interface available for mDNS scanning.");
            }
        }
        return new ArrayList<>(multicastNetworkInterfaces);
    }

    private void updateMulticastNetworkInterfaces() {
        multicastNetworkInterfaces.clear();
        List<NetworkInterfaceWrapper> networkInterfaceWrappers = getNetworkInterfaces();
        for (NetworkInterfaceWrapper interfaceWrapper : networkInterfaceWrappers) {
            if (canScanOnInterface(interfaceWrapper, sharedLog)) {
                multicastNetworkInterfaces.add(interfaceWrapper);
            }
        }
    }

    public boolean isOnIpV6OnlyNetwork(List<NetworkInterfaceWrapper> networkInterfaces) {
        if (networkInterfaces.isEmpty()) {
            return false;
        }

        // TODO(b/79866499): Remove this when the bug is resolved.
        if (PREFER_IPV6) {
            return true;
        }
        boolean hasAtleastOneIPv6Address = false;
        for (NetworkInterfaceWrapper interfaceWrapper : networkInterfaces) {
            for (InterfaceAddress ifAddr : interfaceWrapper.getInterfaceAddresses()) {
                if (!(ifAddr.getAddress() instanceof Inet6Address)) {
                    return false;
                } else {
                    hasAtleastOneIPv6Address = true;
                }
            }
        }
        return hasAtleastOneIPv6Address;
    }

    @VisibleForTesting
    List<NetworkInterfaceWrapper> getNetworkInterfaces() {
        List<NetworkInterfaceWrapper> networkInterfaceWrappers = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces != null) {
                while (interfaces.hasMoreElements()) {
                    networkInterfaceWrappers.add(
                            new NetworkInterfaceWrapper(interfaces.nextElement()));
                }
            }
        } catch (SocketException e) {
            sharedLog.e("Failed to get network interfaces.", e);
        } catch (NullPointerException e) {
            // Android R has a bug that could lead to a NPE. See b/159277702.
            sharedLog.e("Failed to call getNetworkInterfaces API", e);
        }

        return networkInterfaceWrappers;
    }

    @Nullable
    public Network getAvailableNetwork() {
        return connectivityMonitor.getAvailableNetwork();
    }

    /*** Check whether given network interface can support mdns */
    private static boolean canScanOnInterface(@Nullable NetworkInterfaceWrapper networkInterface,
            @NonNull SharedLog sharedLog) {
        try {
            if ((networkInterface == null)
                    || networkInterface.isLoopback()
                    || networkInterface.isPointToPoint()
                    || networkInterface.isVirtual()
                    || !networkInterface.isUp()
                    || !networkInterface.supportsMulticast()) {
                return false;
            }
            return hasInet4Address(networkInterface) || hasInet6Address(networkInterface);
        } catch (IOException e) {
            sharedLog.e(String.format("Failed to check interface %s.",
                    networkInterface.getNetworkInterface().getDisplayName()), e);
        }

        return false;
    }

    private static boolean hasInet4Address(@NonNull NetworkInterfaceWrapper networkInterface) {
        for (InterfaceAddress ifAddr : networkInterface.getInterfaceAddresses()) {
            if (ifAddr.getAddress() instanceof Inet4Address) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasInet6Address(@NonNull NetworkInterfaceWrapper networkInterface) {
        for (InterfaceAddress ifAddr : networkInterface.getInterfaceAddresses()) {
            if (ifAddr.getAddress() instanceof Inet6Address) {
                return true;
            }
        }
        return false;
    }
}