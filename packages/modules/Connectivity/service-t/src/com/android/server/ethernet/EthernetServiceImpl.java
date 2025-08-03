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

package com.android.server.ethernet;

import static android.net.NetworkCapabilities.TRANSPORT_ETHERNET;
import static android.net.NetworkCapabilities.TRANSPORT_TEST;

import android.annotation.CheckResult;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.EthernetNetworkSpecifier;
import android.net.EthernetNetworkUpdateRequest;
import android.net.IEthernetManager;
import android.net.IEthernetServiceListener;
import android.net.INetworkInterfaceOutcomeReceiver;
import android.net.ITetheredInterfaceCallback;
import android.net.IpConfiguration;
import android.net.NetworkCapabilities;
import android.net.NetworkSpecifier;
import android.os.Handler;
import android.os.RemoteException;
import android.util.Log;
import android.util.PrintWriterPrinter;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.IndentingPrintWriter;
import com.android.net.module.util.PermissionUtils;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * EthernetServiceImpl handles remote Ethernet operation requests by implementing
 * the IEthernetManager interface.
 */
public class EthernetServiceImpl extends IEthernetManager.Stub {
    private static final String TAG = "EthernetServiceImpl";

    @VisibleForTesting
    final AtomicBoolean mStarted = new AtomicBoolean(false);
    private final Context mContext;
    private final Handler mHandler;
    private final EthernetTracker mTracker;

    EthernetServiceImpl(@NonNull final Context context, @NonNull final Handler handler,
            @NonNull final EthernetTracker tracker) {
        mContext = context;
        mHandler = handler;
        mTracker = tracker;
    }

    private void enforceAutomotiveDevice(final @NonNull String methodName) {
        PermissionUtils.enforceSystemFeature(mContext, PackageManager.FEATURE_AUTOMOTIVE,
                methodName + " is only available on automotive devices.");
    }

    @CheckResult
    private boolean hasUseRestrictedNetworksPermission() {
        return PermissionUtils.hasAnyPermissionOf(mContext,
                android.Manifest.permission.CONNECTIVITY_USE_RESTRICTED_NETWORKS);
    }

    public void start() {
        Log.i(TAG, "Starting Ethernet service");
        mTracker.start();
        mStarted.set(true);
    }

    private void throwIfEthernetNotStarted() {
        if (!mStarted.get()) {
            throw new IllegalStateException("System isn't ready to change ethernet configurations");
        }
    }

    @Override
    public String[] getAvailableInterfaces() throws RemoteException {
        PermissionUtils.enforceAccessNetworkStatePermission(mContext, TAG);
        return mTracker.getClientModeInterfaces(hasUseRestrictedNetworksPermission());
    }

    /**
     * Get Ethernet configuration
     * @return the Ethernet Configuration, contained in {@link IpConfiguration}.
     */
    @Override
    public IpConfiguration getConfiguration(String iface) {
        PermissionUtils.enforceAccessNetworkStatePermission(mContext, TAG);
        if (mTracker.isRestrictedInterface(iface)) {
            PermissionUtils.enforceRestrictedNetworkPermission(mContext, TAG);
        }

        return new IpConfiguration(mTracker.getIpConfiguration(iface));
    }

    /**
     * Set Ethernet configuration
     */
    @Override
    public void setConfiguration(String iface, IpConfiguration config) {
        throwIfEthernetNotStarted();

        PermissionUtils.enforceNetworkStackPermission(mContext);
        if (mTracker.isRestrictedInterface(iface)) {
            PermissionUtils.enforceRestrictedNetworkPermission(mContext, TAG);
        }

        // TODO: this does not check proxy settings, gateways, etc.
        // Fix this by making IpConfiguration a complete representation of static configuration.
        mTracker.updateIpConfiguration(iface, new IpConfiguration(config));
    }

    /**
     * Indicates whether given interface is available.
     */
    @Override
    public boolean isAvailable(String iface) {
        PermissionUtils.enforceAccessNetworkStatePermission(mContext, TAG);
        if (mTracker.isRestrictedInterface(iface)) {
            PermissionUtils.enforceRestrictedNetworkPermission(mContext, TAG);
        }

        return mTracker.isTrackingInterface(iface);
    }

    /**
     * Adds a listener.
     * @param listener A {@link IEthernetServiceListener} to add.
     */
    public void addListener(IEthernetServiceListener listener) throws RemoteException {
        Objects.requireNonNull(listener, "listener must not be null");
        PermissionUtils.enforceAccessNetworkStatePermission(mContext, TAG);
        mTracker.addListener(listener, hasUseRestrictedNetworksPermission());
    }

    /**
     * Removes a listener.
     * @param listener A {@link IEthernetServiceListener} to remove.
     */
    public void removeListener(IEthernetServiceListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        PermissionUtils.enforceAccessNetworkStatePermission(mContext, TAG);
        mTracker.removeListener(listener);
    }

    @Override
    public void setIncludeTestInterfaces(boolean include) {
        PermissionUtils.enforceNetworkStackPermissionOr(mContext,
                android.Manifest.permission.NETWORK_SETTINGS);
        mTracker.setIncludeTestInterfaces(include);
    }

    @Override
    public void requestTetheredInterface(ITetheredInterfaceCallback callback) {
        Objects.requireNonNull(callback, "callback must not be null");
        PermissionUtils.enforceNetworkStackPermissionOr(mContext,
                android.Manifest.permission.NETWORK_SETTINGS);
        mTracker.requestTetheredInterface(callback);
    }

    @Override
    public void releaseTetheredInterface(ITetheredInterfaceCallback callback) {
        Objects.requireNonNull(callback, "callback must not be null");
        PermissionUtils.enforceNetworkStackPermissionOr(mContext,
                android.Manifest.permission.NETWORK_SETTINGS);
        mTracker.releaseTetheredInterface(callback);
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        final IndentingPrintWriter pw = new IndentingPrintWriter(writer, "  ");
        if (!PermissionUtils.hasDumpPermission(mContext, TAG, pw)) return;

        pw.println("Current Ethernet state: ");
        pw.increaseIndent();
        mTracker.dump(fd, pw, args);
        pw.decreaseIndent();

        pw.println("Handler:");
        pw.increaseIndent();
        mHandler.dump(new PrintWriterPrinter(pw), "EthernetServiceImpl");
        pw.decreaseIndent();
    }

    private void enforceNetworkManagementPermission() {
        mContext.enforceCallingOrSelfPermission(
                android.Manifest.permission.MANAGE_ETHERNET_NETWORKS,
                "EthernetServiceImpl");
    }

    private void enforceManageTestNetworksPermission() {
        mContext.enforceCallingOrSelfPermission(
                android.Manifest.permission.MANAGE_TEST_NETWORKS,
                "EthernetServiceImpl");
    }

    private void validateOrSetNetworkSpecifier(String iface, NetworkCapabilities nc) {
        final NetworkSpecifier spec = nc.getNetworkSpecifier();
        if (spec == null) {
            nc.setNetworkSpecifier(new EthernetNetworkSpecifier(iface));
            return;
        }
        if (!(spec instanceof EthernetNetworkSpecifier)) {
            throw new IllegalArgumentException("Invalid specifier type for request.");
        }
        if (!((EthernetNetworkSpecifier) spec).getInterfaceName().matches(iface)) {
            throw new IllegalArgumentException("Invalid interface name set on specifier.");
        }
    }

    private void maybeValidateTestCapabilities(String iface, NetworkCapabilities nc) {
        if (!mTracker.isValidTestInterface(iface)) {
            return;
        }
        if (!nc.hasTransport(TRANSPORT_TEST)) {
            throw new IllegalArgumentException(
                    "Updates to test interfaces must have NetworkCapabilities.TRANSPORT_TEST.");
        }
    }

    private void maybeValidateEthernetTransport(String iface, NetworkCapabilities nc) {
        if (mTracker.isValidTestInterface(iface)) {
            return;
        }
        if (!nc.hasSingleTransport(TRANSPORT_ETHERNET)) {
            throw new IllegalArgumentException("Invalid transport type for request.");
        }
    }

    private void enforceAdminPermission(final String iface, boolean enforceAutomotive,
            final String logMessage) {
        if (mTracker.isValidTestInterface(iface)) {
            enforceManageTestNetworksPermission();
        } else {
            enforceNetworkManagementPermission();
            if (enforceAutomotive) {
                enforceAutomotiveDevice(logMessage);
            }
        }
    }

    @Override
    public void updateConfiguration(@NonNull final String iface,
            @NonNull final EthernetNetworkUpdateRequest request,
            @Nullable final INetworkInterfaceOutcomeReceiver cb) {
        Objects.requireNonNull(iface);
        Objects.requireNonNull(request);
        throwIfEthernetNotStarted();

        // TODO: validate that iface is listed in overlay config_ethernet_interfaces
        // only automotive devices are allowed to set the NetworkCapabilities using this API
        final NetworkCapabilities nc = request.getNetworkCapabilities();
        enforceAdminPermission(
                iface, nc != null, "updateConfiguration() with non-null capabilities");
        if (nc != null) {
            validateOrSetNetworkSpecifier(iface, nc);
            maybeValidateTestCapabilities(iface, nc);
            maybeValidateEthernetTransport(iface, nc);
        }

        mTracker.updateConfiguration(
                iface, request.getIpConfiguration(), nc, new EthernetCallback(cb));
    }

    @Override
    public void enableInterface(@NonNull final String iface,
            @Nullable final INetworkInterfaceOutcomeReceiver cb) {
        Log.i(TAG, "enableInterface called with: iface=" + iface + ", cb=" + cb);
        Objects.requireNonNull(iface);
        throwIfEthernetNotStarted();

        enforceAdminPermission(iface, false, "enableInterface()");

        mTracker.setInterfaceEnabled(iface, true /* enabled */, new EthernetCallback(cb));
    }

    @Override
    public void disableInterface(@NonNull final String iface,
            @Nullable final INetworkInterfaceOutcomeReceiver cb) {
        Log.i(TAG, "disableInterface called with: iface=" + iface + ", cb=" + cb);
        Objects.requireNonNull(iface);
        throwIfEthernetNotStarted();

        enforceAdminPermission(iface, false, "disableInterface()");

        mTracker.setInterfaceEnabled(iface, false /* enabled */, new EthernetCallback(cb));
    }

    @Override
    public void setEthernetEnabled(boolean enabled) {
        PermissionUtils.enforceNetworkStackPermissionOr(mContext,
                android.Manifest.permission.NETWORK_SETTINGS);

        mTracker.setEthernetEnabled(enabled);
    }

    @Override
    public List<String> getInterfaceList() {
        PermissionUtils.enforceAccessNetworkStatePermission(mContext, TAG);
        return mTracker.getInterfaceList();
    }
}
