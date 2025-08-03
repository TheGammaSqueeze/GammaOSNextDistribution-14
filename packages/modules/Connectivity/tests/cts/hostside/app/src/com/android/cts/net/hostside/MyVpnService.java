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

package com.android.cts.net.hostside;

import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.IpPrefix;
import android.net.Network;
import android.net.NetworkUtils;
import android.net.ProxyInfo;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;

import com.android.modules.utils.build.SdkLevel;
import com.android.networkstack.apishim.VpnServiceBuilderShimImpl;
import com.android.networkstack.apishim.common.UnsupportedApiLevelException;
import com.android.networkstack.apishim.common.VpnServiceBuilderShim;
import com.android.testutils.PacketReflector;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class MyVpnService extends VpnService {

    private static String TAG = "MyVpnService";
    private static int MTU = 1799;

    public static final String ACTION_ESTABLISHED = "com.android.cts.net.hostside.ESTABNLISHED";
    public static final String EXTRA_ALWAYS_ON = "is-always-on";
    public static final String EXTRA_LOCKDOWN_ENABLED = "is-lockdown-enabled";
    public static final String CMD_CONNECT = "connect";
    public static final String CMD_DISCONNECT = "disconnect";
    public static final String CMD_UPDATE_UNDERLYING_NETWORKS = "update_underlying_networks";

    private ParcelFileDescriptor mFd = null;
    private PacketReflector mPacketReflector = null;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String packageName = getPackageName();
        String cmd = intent.getStringExtra(packageName + ".cmd");
        if (CMD_DISCONNECT.equals(cmd)) {
            stop();
        } else if (CMD_CONNECT.equals(cmd)) {
            start(packageName, intent);
        } else if (CMD_UPDATE_UNDERLYING_NETWORKS.equals(cmd)) {
            updateUnderlyingNetworks(packageName, intent);
        }

        return START_NOT_STICKY;
    }

    private void updateUnderlyingNetworks(String packageName, Intent intent) {
        final ArrayList<Network> underlyingNetworks =
                intent.getParcelableArrayListExtra(packageName + ".underlyingNetworks");
        setUnderlyingNetworks(
                (underlyingNetworks != null) ? underlyingNetworks.toArray(new Network[0]) : null);
    }

    private String parseIpAndMaskListArgument(String packageName, Intent intent, String argName,
            BiConsumer<InetAddress, Integer> consumer) {
        final String addresses = intent.getStringExtra(packageName + "." + argName);

        if (TextUtils.isEmpty(addresses)) {
            return null;
        }

        final String[] addressesArray = addresses.split(",");
        for (String address : addressesArray) {
            final Pair<InetAddress, Integer> ipAndMask = NetworkUtils.parseIpAndMask(address);
            consumer.accept(ipAndMask.first, ipAndMask.second);
        }

        return addresses;
    }

    private String parseIpPrefixListArgument(String packageName, Intent intent, String argName,
            Consumer<IpPrefix> consumer) {
        return parseIpAndMaskListArgument(packageName, intent, argName,
                (inetAddress, prefixLength) -> consumer.accept(
                        new IpPrefix(inetAddress, prefixLength)));
    }

    private void start(String packageName, Intent intent) {
        Builder builder = new Builder();
        VpnServiceBuilderShim vpnServiceBuilderShim = VpnServiceBuilderShimImpl.newInstance();

        final String addresses = parseIpAndMaskListArgument(packageName, intent, "addresses",
                builder::addAddress);

        String addedRoutes;
        if (SdkLevel.isAtLeastT() && intent.getBooleanExtra(packageName + ".addRoutesByIpPrefix",
                false)) {
            addedRoutes = parseIpPrefixListArgument(packageName, intent, "routes", (prefix) -> {
                try {
                    vpnServiceBuilderShim.addRoute(builder, prefix);
                } catch (UnsupportedApiLevelException e) {
                    throw new RuntimeException(e);
                }
            });
        } else {
            addedRoutes = parseIpAndMaskListArgument(packageName, intent, "routes",
                    builder::addRoute);
        }

        String excludedRoutes = null;
        if (SdkLevel.isAtLeastT()) {
            excludedRoutes = parseIpPrefixListArgument(packageName, intent, "excludedRoutes",
                    (prefix) -> {
                        try {
                            vpnServiceBuilderShim.excludeRoute(builder, prefix);
                        } catch (UnsupportedApiLevelException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }

        String allowed = intent.getStringExtra(packageName + ".allowedapplications");
        if (allowed != null) {
            String[] packageArray = allowed.split(",");
            for (int i = 0; i < packageArray.length; i++) {
                String allowedPackage = packageArray[i];
                if (!TextUtils.isEmpty(allowedPackage)) {
                    try {
                        builder.addAllowedApplication(allowedPackage);
                    } catch(NameNotFoundException e) {
                        continue;
                    }
                }
            }
        }

        String disallowed = intent.getStringExtra(packageName + ".disallowedapplications");
        if (disallowed != null) {
            String[] packageArray = disallowed.split(",");
            for (int i = 0; i < packageArray.length; i++) {
                String disallowedPackage = packageArray[i];
                if (!TextUtils.isEmpty(disallowedPackage)) {
                    try {
                        builder.addDisallowedApplication(disallowedPackage);
                    } catch(NameNotFoundException e) {
                        continue;
                    }
                }
            }
        }

        ArrayList<Network> underlyingNetworks =
                intent.getParcelableArrayListExtra(packageName + ".underlyingNetworks");
        if (underlyingNetworks == null) {
            // VPN tracks default network
            builder.setUnderlyingNetworks(null);
        } else {
            builder.setUnderlyingNetworks(underlyingNetworks.toArray(new Network[0]));
        }

        boolean isAlwaysMetered = intent.getBooleanExtra(packageName + ".isAlwaysMetered", false);
        builder.setMetered(isAlwaysMetered);

        ProxyInfo vpnProxy = intent.getParcelableExtra(packageName + ".httpProxy");
        builder.setHttpProxy(vpnProxy);
        builder.setMtu(MTU);
        builder.setBlocking(true);
        builder.setSession("MyVpnService");

        Log.i(TAG, "Establishing VPN,"
                + " addresses=" + addresses
                + " addedRoutes=" + addedRoutes
                + " excludedRoutes=" + excludedRoutes
                + " allowedApplications=" + allowed
                + " disallowedApplications=" + disallowed);

        mFd = builder.establish();
        Log.i(TAG, "Established, fd=" + (mFd == null ? "null" : mFd.getFd()));

        broadcastEstablished();

        mPacketReflector = new PacketReflector(mFd.getFileDescriptor(), MTU);
        mPacketReflector.start();
    }

    private void broadcastEstablished() {
        final Intent bcIntent = new Intent(ACTION_ESTABLISHED);
        bcIntent.putExtra(EXTRA_ALWAYS_ON, isAlwaysOn());
        bcIntent.putExtra(EXTRA_LOCKDOWN_ENABLED, isLockdownEnabled());
        sendBroadcast(bcIntent);
    }

    private void stop() {
        if (mPacketReflector != null) {
            mPacketReflector.interrupt();
            mPacketReflector = null;
        }
        try {
            if (mFd != null) {
                Log.i(TAG, "Closing filedescriptor");
                mFd.close();
            }
        } catch(IOException e) {
        } finally {
            mFd = null;
        }
    }

    @Override
    public void onDestroy() {
        stop();
        super.onDestroy();
    }
}
