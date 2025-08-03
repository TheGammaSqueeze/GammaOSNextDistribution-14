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

package com.android.server.remoteauth.connectivity;

import static com.android.server.remoteauth.connectivity.DiscoveryFilter.DeviceType;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.TargetApi;
import android.companion.AssociationInfo;
import android.companion.AssociationRequest;
import android.os.Build;
import android.util.Log;


import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;

/**
 * Discovers devices associated with the companion device manager.
 *
 * TODO(b/296625303): Change to VANILLA_ICE_CREAM when AssociationInfo is available in V.
 */
@TargetApi(Build.VERSION_CODES.TIRAMISU)
public class CdmConnectivityManager implements ConnectivityManager {
    private static final String TAG = "CdmConnectivityManager";

    private final CompanionDeviceManagerWrapper mCompanionDeviceManagerWrapper;

    private ExecutorService mExecutor;
    private Map<DiscoveredDeviceReceiver, Future> mPendingDiscoveryCallbacks =
            new ConcurrentHashMap<>();

    public CdmConnectivityManager(
            @NonNull ExecutorService executor,
            @NonNull CompanionDeviceManagerWrapper companionDeviceManagerWrapper) {
        mExecutor = executor;
        mCompanionDeviceManagerWrapper = companionDeviceManagerWrapper;
    }

    /**
     * Runs associated discovery callbacks for discovered devices.
     *
     * @param discoveredDeviceReceiver callback.
     * @param device discovered device.
     */
    private void notifyOnDiscovered(
            @NonNull DiscoveredDeviceReceiver discoveredDeviceReceiver,
            @NonNull DiscoveredDevice device) {
        Preconditions.checkNotNull(discoveredDeviceReceiver);
        Preconditions.checkNotNull(device);

        Log.i(TAG, "Notifying discovered device");
        discoveredDeviceReceiver.onDiscovered(device);
    }

    /**
     * Posts an async task to discover CDM associations and run callback if device is discovered.
     *
     * @param discoveryFilter filter for association.
     * @param discoveredDeviceReceiver callback.
     */
    private void startDiscoveryAsync(@NonNull DiscoveryFilter discoveryFilter,
            @NonNull DiscoveredDeviceReceiver discoveredDeviceReceiver) {
        Preconditions.checkNotNull(discoveryFilter);
        Preconditions.checkNotNull(discoveredDeviceReceiver);

        List<AssociationInfo> associations = mCompanionDeviceManagerWrapper.getAllAssociations();
        Log.i(TAG, "Found associations: " + associations.size());
        for (AssociationInfo association : associations) {
            String deviceProfile = getDeviceProfileFromType(discoveryFilter.getDeviceType());
            // TODO(b/297574984): Include device presence signal before notifying discovery result.
            if (mCompanionDeviceManagerWrapper.getDeviceProfile(association)
                    .equals(deviceProfile)) {
                notifyOnDiscovered(
                        discoveredDeviceReceiver,
                        createDiscoveredDeviceFromAssociation(association));
            }
        }
    }

    /**
     * Returns the device profile from association info.
     *
     * @param deviceType Discovery filter device type.
     * @return Device profile string defined in {@link AssociationRequest}.
     * @throws AssertionError if type cannot be mapped.
     */
    private String getDeviceProfileFromType(@DeviceType int deviceType) {
        if (deviceType == DiscoveryFilter.WATCH) {
            return AssociationRequest.DEVICE_PROFILE_WATCH;
        } else {
            // Should not reach here.
            throw new AssertionError(deviceType);
        }
    }

    /**
     * Creates discovered device from association info.
     *
     * @param info Association info.
     * @return discovered device object.
     */
    private @NonNull DiscoveredDevice createDiscoveredDeviceFromAssociation(
            @NonNull AssociationInfo info) {
        return new DiscoveredDevice(
                new CdmConnectionInfo(info.getId(), info),
                info.getDisplayName() == null ? "" : info.getDisplayName().toString());
    }

    /**
     * Triggers the discovery for CDM associations.
     *
     * Runs discovery only if a callback has not been previously registered.
     *
     * @param discoveryFilter filter for associations.
     * @param discoveredDeviceReceiver callback to be run on discovery result.
     */
    @Override
    public void startDiscovery(
            @NonNull DiscoveryFilter discoveryFilter,
            @NonNull DiscoveredDeviceReceiver discoveredDeviceReceiver) {
        Preconditions.checkNotNull(mCompanionDeviceManagerWrapper);
        Preconditions.checkNotNull(discoveryFilter);
        Preconditions.checkNotNull(discoveredDeviceReceiver);

        try {
            mPendingDiscoveryCallbacks.computeIfAbsent(
                    discoveredDeviceReceiver,
                    discoveryFuture -> mExecutor.submit(
                        () -> startDiscoveryAsync(discoveryFilter, discoveryFuture),
                        /* result= */ null));
        } catch (RejectedExecutionException | NullPointerException e) {
            Log.e(TAG, "Failed to start async discovery: " + e.getMessage());
        }
    }

    /** Stops discovery. */
    @Override
    public void stopDiscovery(
            @NonNull DiscoveryFilter discoveryFilter,
            @NonNull DiscoveredDeviceReceiver discoveredDeviceReceiver) {
        Preconditions.checkNotNull(discoveryFilter);
        Preconditions.checkNotNull(discoveredDeviceReceiver);

        Future<Void> discoveryFuture = mPendingDiscoveryCallbacks.remove(discoveredDeviceReceiver);
        if (null != discoveryFuture && !discoveryFuture.cancel(/* mayInterruptIfRunning= */ true)) {
            Log.d(TAG, "Discovery was possibly completed.");
        }
    }

    @Nullable
    @Override
    public Connection connect(@NonNull ConnectionInfo connectionInfo,
            @NonNull EventListener eventListener) {
        // Not implemented.
        return null;
    }

    @Override
    public void startListening(MessageReceiver messageReceiver) {
        // Not implemented.
    }

    @Override
    public void stopListening(MessageReceiver messageReceiver) {
        // Not implemented.
    }

    /**
     * Returns whether the callback is already registered and pending.
     *
     * @param discoveredDeviceReceiver callback
     * @return true if the callback is pending, false otherwise.
     */
    @VisibleForTesting
    boolean hasPendingCallbacks(@NonNull DiscoveredDeviceReceiver discoveredDeviceReceiver) {
        return mPendingDiscoveryCallbacks.containsKey(discoveredDeviceReceiver);
    }
}
