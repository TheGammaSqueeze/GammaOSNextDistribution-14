/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static com.android.server.connectivity.mdns.MdnsConstants.NO_PACKET;
import static com.android.server.connectivity.mdns.MdnsInterfaceAdvertiser.CONFLICT_HOST;
import static com.android.server.connectivity.mdns.MdnsInterfaceAdvertiser.CONFLICT_SERVICE;
import static com.android.server.connectivity.mdns.MdnsRecord.MAX_LABEL_LENGTH;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresApi;
import android.content.Context;
import android.net.LinkAddress;
import android.net.Network;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.nsd.OffloadEngine;
import android.net.nsd.OffloadServiceInfo;
import android.os.Build;
import android.os.Looper;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.Log;
import android.util.SparseArray;

import com.android.connectivity.resources.R;
import com.android.internal.annotations.VisibleForTesting;
import com.android.net.module.util.CollectionUtils;
import com.android.net.module.util.SharedLog;
import com.android.server.connectivity.ConnectivityResources;
import com.android.server.connectivity.mdns.util.MdnsUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Consumer;

/**
 * MdnsAdvertiser manages advertising services per {@link com.android.server.NsdService} requests.
 *
 * All methods except the constructor must be called on the looper thread.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
public class MdnsAdvertiser {
    private static final String TAG = MdnsAdvertiser.class.getSimpleName();
    static final boolean DBG = Log.isLoggable(TAG, Log.DEBUG);

    // Top-level domain for link-local queries, as per RFC6762 3.
    private static final String LOCAL_TLD = "local";


    private final Looper mLooper;
    private final AdvertiserCallback mCb;

    // Max-sized buffers to be used as temporary buffer to read/build packets. May be used by
    // multiple components, but only for self-contained operations in the looper thread, so not
    // concurrently.
    // TODO: set according to MTU. 1300 should fit for ethernet MTU 1500 with some overhead.
    private final byte[] mPacketCreationBuffer = new byte[1300];

    private final MdnsSocketProvider mSocketProvider;
    private final ArrayMap<Network, InterfaceAdvertiserRequest> mAdvertiserRequests =
            new ArrayMap<>();
    private final ArrayMap<MdnsInterfaceSocket, MdnsInterfaceAdvertiser> mAllAdvertisers =
            new ArrayMap<>();
    private final SparseArray<Registration> mRegistrations = new SparseArray<>();
    private final Dependencies mDeps;
    private String[] mDeviceHostName;
    @NonNull private final SharedLog mSharedLog;
    private final Map<String, List<OffloadServiceInfoWrapper>> mInterfaceOffloadServices =
            new ArrayMap<>();
    private final MdnsFeatureFlags mMdnsFeatureFlags;
    private final Map<String, Integer> mServiceTypeToOffloadPriority;

    /**
     * Dependencies for {@link MdnsAdvertiser}, useful for testing.
     */
    @VisibleForTesting
    public static class Dependencies {
        /**
         * @see MdnsInterfaceAdvertiser
         */
        public MdnsInterfaceAdvertiser makeAdvertiser(@NonNull MdnsInterfaceSocket socket,
                @NonNull List<LinkAddress> initialAddresses,
                @NonNull Looper looper, @NonNull byte[] packetCreationBuffer,
                @NonNull MdnsInterfaceAdvertiser.Callback cb,
                @NonNull String[] deviceHostName,
                @NonNull SharedLog sharedLog,
                @NonNull MdnsFeatureFlags mdnsFeatureFlags) {
            // Note NetworkInterface is final and not mockable
            return new MdnsInterfaceAdvertiser(socket, initialAddresses, looper,
                    packetCreationBuffer, cb, deviceHostName, sharedLog, mdnsFeatureFlags);
        }

        /**
         * Generates a unique hostname to be used by the device.
         */
        @NonNull
        public String[] generateHostname() {
            // Generate a very-probably-unique hostname. This allows minimizing possible conflicts
            // to the point that probing for it is no longer necessary (as per RFC6762 8.1 last
            // paragraph), and does not leak more information than what could already be obtained by
            // looking at the mDNS packets source address.
            // This differs from historical behavior that just used "Android.local" for many
            // devices, creating a lot of conflicts.
            // Having a different hostname per interface is an acceptable option as per RFC6762 14.
            // This hostname will change every time the interface is reconnected, so this does not
            // allow tracking the device.
            // TODO: consider deriving a hostname from other sources, such as the IPv6 addresses
            // (reusing the same privacy-protecting mechanics).
            return new String[] {
                    "Android_" + UUID.randomUUID().toString().replace("-", ""), LOCAL_TLD };
        }
    }

    /**
     * Gets the current status of the OffloadServiceInfos per interface.
     * @param interfaceName the target interfaceName
     * @return the list of current offloaded services.
     */
    @NonNull
    public List<OffloadServiceInfoWrapper> getAllInterfaceOffloadServiceInfos(
            @NonNull String interfaceName) {
        return mInterfaceOffloadServices.getOrDefault(interfaceName, Collections.emptyList());
    }

    private final MdnsInterfaceAdvertiser.Callback mInterfaceAdvertiserCb =
            new MdnsInterfaceAdvertiser.Callback() {
        @Override
        public void onServiceProbingSucceeded(
                @NonNull MdnsInterfaceAdvertiser advertiser, int serviceId) {
            final Registration registration = mRegistrations.get(serviceId);
            if (registration == null) {
                mSharedLog.wtf("Register succeeded for unknown registration");
                return;
            }
            if (mMdnsFeatureFlags.mIsMdnsOffloadFeatureEnabled
                    // TODO: Enable offload when the serviceInfo contains a custom host.
                    && TextUtils.isEmpty(registration.getServiceInfo().getHostname())) {
                final String interfaceName = advertiser.getSocketInterfaceName();
                final List<OffloadServiceInfoWrapper> existingOffloadServiceInfoWrappers =
                        mInterfaceOffloadServices.computeIfAbsent(interfaceName,
                                k -> new ArrayList<>());
                // Remove existing offload services from cache for update.
                existingOffloadServiceInfoWrappers.removeIf(item -> item.mServiceId == serviceId);

                byte[] rawOffloadPacket = advertiser.getRawOffloadPayload(serviceId);
                final OffloadServiceInfoWrapper newOffloadServiceInfoWrapper = createOffloadService(
                        serviceId, registration, rawOffloadPacket);
                existingOffloadServiceInfoWrappers.add(newOffloadServiceInfoWrapper);
                mCb.onOffloadStartOrUpdate(interfaceName,
                        newOffloadServiceInfoWrapper.mOffloadServiceInfo);
            }

            // Wait for all current interfaces to be done probing before notifying of success.
            if (any(mAllAdvertisers, (k, a) -> a.isProbing(serviceId))) return;
            // The service may still be unregistered/renamed if a conflict is found on a later added
            // interface, or if a conflicting announcement/reply is detected (RFC6762 9.)

            if (!registration.mNotifiedRegistrationSuccess) {
                mCb.onRegisterServiceSucceeded(serviceId, registration.getServiceInfo());
                registration.mNotifiedRegistrationSuccess = true;
            }
        }

        @Override
        public void onServiceConflict(@NonNull MdnsInterfaceAdvertiser advertiser, int serviceId,
                int conflictType) {
            mSharedLog.i("Found conflict, restarted probing for service "
                    + serviceId + " "
                    + conflictType);

            final Registration registration = mRegistrations.get(serviceId);
            if (registration == null) return;
            if (registration.mNotifiedRegistrationSuccess) {
                // TODO: consider notifying clients that the service is no longer registered with
                // the old name (back to probing). The legacy implementation did not send any
                // callback though; it only sent onServiceRegistered after re-probing finishes
                // (with the old, conflicting, actually not used name as argument... The new
                // implementation will send callbacks with the new name).
                registration.mNotifiedRegistrationSuccess = false;
                registration.mConflictAfterProbingCount++;

                // The service was done probing, just reset it to probing state (RFC6762 9.)
                forAllAdvertisers(a -> {
                    if (!a.maybeRestartProbingForConflict(serviceId)) {
                        return;
                    }
                    if (mMdnsFeatureFlags.mIsMdnsOffloadFeatureEnabled) {
                        maybeSendOffloadStop(a.getSocketInterfaceName(), serviceId);
                    }
                });
                return;
            }

            if ((conflictType & CONFLICT_SERVICE) != 0) {
                // Service conflict was found during probing; rename once to find a name that has no
                // conflict
                registration.updateForServiceConflict(
                        registration.makeNewServiceInfoForServiceConflict(1 /* renameCount */),
                        1 /* renameCount */);
            }

            if ((conflictType & CONFLICT_HOST) != 0) {
                // Host conflict was found during probing; rename once to find a name that has no
                // conflict
                registration.updateForHostConflict(
                        registration.makeNewServiceInfoForHostConflict(1 /* renameCount */),
                        1 /* renameCount */);
            }

            registration.mConflictDuringProbingCount++;

            // Keep renaming if the new name conflicts in local registrations
            updateRegistrationUntilNoConflict((net, adv) -> adv.hasRegistration(registration),
                    registration);

            // Update advertisers to use the new name
            forAllAdvertisers(a -> a.renameServiceForConflict(
                    serviceId, registration.getServiceInfo()));
        }

        @Override
        public void onDestroyed(@NonNull MdnsInterfaceSocket socket) {
            for (int i = mAdvertiserRequests.size() - 1; i >= 0; i--) {
                if (mAdvertiserRequests.valueAt(i).onAdvertiserDestroyed(socket)) {
                    mAdvertiserRequests.removeAt(i);
                }
            }
            mAllAdvertisers.remove(socket);
        }
    };

    private boolean hasAnyServiceConflict(
            @NonNull BiPredicate<Network, InterfaceAdvertiserRequest> applicableAdvertiserFilter,
            @NonNull NsdServiceInfo newInfo,
            @NonNull Registration originalRegistration) {
        return any(
                mAdvertiserRequests,
                (network, adv) ->
                        applicableAdvertiserFilter.test(network, adv)
                                && adv.hasServiceConflict(newInfo, originalRegistration));
    }

    private boolean hasAnyHostConflict(
            @NonNull BiPredicate<Network, InterfaceAdvertiserRequest> applicableAdvertiserFilter,
            @NonNull NsdServiceInfo newInfo,
            int clientUid) {
        // Check if it conflicts with custom hosts.
        if (any(
                mAdvertiserRequests,
                (network, adv) ->
                        applicableAdvertiserFilter.test(network, adv)
                                && adv.hasHostConflict(newInfo, clientUid))) {
            return true;
        }
        // Check if it conflicts with the default hostname.
        return MdnsUtils.equalsIgnoreDnsCase(newInfo.getHostname(), mDeviceHostName[0]);
    }

    private void updateRegistrationUntilNoConflict(
            @NonNull BiPredicate<Network, InterfaceAdvertiserRequest> applicableAdvertiserFilter,
            @NonNull Registration registration) {
        NsdServiceInfo newInfo = registration.getServiceInfo();

        int renameServiceCount = 0;
        while (hasAnyServiceConflict(applicableAdvertiserFilter, newInfo, registration)) {
            renameServiceCount++;
            newInfo = registration.makeNewServiceInfoForServiceConflict(renameServiceCount);
        }
        registration.updateForServiceConflict(newInfo, renameServiceCount);

        if (!TextUtils.isEmpty(registration.getServiceInfo().getHostname())) {
            int renameHostCount = 0;
            while (hasAnyHostConflict(
                    applicableAdvertiserFilter, newInfo, registration.mClientUid)) {
                renameHostCount++;
                newInfo = registration.makeNewServiceInfoForHostConflict(renameHostCount);
            }
            registration.updateForHostConflict(newInfo, renameHostCount);
        }
    }

    private void maybeSendOffloadStop(final String interfaceName, int serviceId) {
        final List<OffloadServiceInfoWrapper> existingOffloadServiceInfoWrappers =
                mInterfaceOffloadServices.get(interfaceName);
        if (existingOffloadServiceInfoWrappers == null) {
            return;
        }
        // Stop the offloaded service by matching the service id
        int idx = CollectionUtils.indexOf(existingOffloadServiceInfoWrappers,
                item -> item.mServiceId == serviceId);
        if (idx >= 0) {
            mCb.onOffloadStop(interfaceName,
                    existingOffloadServiceInfoWrappers.get(idx).mOffloadServiceInfo);
            existingOffloadServiceInfoWrappers.remove(idx);
        }
    }

    /**
     * A request for a {@link MdnsInterfaceAdvertiser}.
     *
     * This class tracks services to be advertised on all sockets provided via a registered
     * {@link MdnsSocketProvider.SocketCallback}.
     */
    private class InterfaceAdvertiserRequest implements MdnsSocketProvider.SocketCallback {
        /** Registrations to add to newer MdnsInterfaceAdvertisers when sockets are created. */
        @NonNull
        private final SparseArray<Registration> mPendingRegistrations = new SparseArray<>();
        @NonNull
        private final ArrayMap<MdnsInterfaceSocket, MdnsInterfaceAdvertiser> mAdvertisers =
                new ArrayMap<>();

        InterfaceAdvertiserRequest(@Nullable Network requestedNetwork) {
            mSocketProvider.requestSocket(requestedNetwork, this);
        }

        /**
         * Called when an advertiser was destroyed, after all services were unregistered and it sent
         * exit announcements, or the interface is gone.
         *
         * @return true if this {@link InterfaceAdvertiserRequest} should now be deleted.
         */
        boolean onAdvertiserDestroyed(@NonNull MdnsInterfaceSocket socket) {
            final MdnsInterfaceAdvertiser removedAdvertiser = mAdvertisers.remove(socket);
            if (mMdnsFeatureFlags.mIsMdnsOffloadFeatureEnabled && removedAdvertiser != null) {
                final String interfaceName = removedAdvertiser.getSocketInterfaceName();
                // If the interface is destroyed, stop all hardware offloading on that
                // interface.
                final List<OffloadServiceInfoWrapper> offloadServiceInfoWrappers =
                        mInterfaceOffloadServices.remove(interfaceName);
                if (offloadServiceInfoWrappers != null) {
                    for (OffloadServiceInfoWrapper offloadServiceInfoWrapper :
                            offloadServiceInfoWrappers) {
                        mCb.onOffloadStop(interfaceName,
                                offloadServiceInfoWrapper.mOffloadServiceInfo);
                    }
                }
            }

            if (mAdvertisers.size() == 0 && mPendingRegistrations.size() == 0) {
                // No advertiser is using sockets from this request anymore (in particular for exit
                // announcements), and there is no registration so newer sockets will not be
                // necessary, so the request can be unregistered.
                mSocketProvider.unrequestSocket(this);
                return true;
            }
            return false;
        }

        /**
         * Return whether this {@link InterfaceAdvertiserRequest} has the given registration.
         */
        boolean hasRegistration(@NonNull Registration registration) {
            return mPendingRegistrations.indexOfValue(registration) >= 0;
        }

        /**
         * Return whether using the proposed new {@link NsdServiceInfo} to add a registration would
         * cause a conflict of the service in this {@link InterfaceAdvertiserRequest}.
         */
        boolean hasServiceConflict(
                @NonNull NsdServiceInfo newInfo, @NonNull Registration originalRegistration) {
            return getConflictingRegistrationDueToService(newInfo, originalRegistration) >= 0;
        }

        /**
         * Return whether using the proposed new {@link NsdServiceInfo} to add a registration would
         * cause a conflict of the host in this {@link InterfaceAdvertiserRequest}.
         *
         * @param clientUid UID of the user who wants to advertise the serviceInfo.
         */
        boolean hasHostConflict(@NonNull NsdServiceInfo newInfo, int clientUid) {
            return getConflictingRegistrationDueToHost(newInfo, clientUid) >= 0;
        }

        /** Get the ID of a conflicting registration due to service, or -1 if none. */
        int getConflictingRegistrationDueToService(
                @NonNull NsdServiceInfo info, @NonNull Registration originalRegistration) {
            if (TextUtils.isEmpty(info.getServiceName())) {
                return -1;
            }
            for (int i = 0; i < mPendingRegistrations.size(); i++) {
                // Never conflict with itself
                if (mPendingRegistrations.valueAt(i) == originalRegistration) {
                    continue;
                }
                final NsdServiceInfo other = mPendingRegistrations.valueAt(i).getServiceInfo();
                if (MdnsUtils.equalsIgnoreDnsCase(info.getServiceName(), other.getServiceName())
                        && MdnsUtils.equalsIgnoreDnsCase(info.getServiceType(),
                        other.getServiceType())) {
                    return mPendingRegistrations.keyAt(i);
                }
            }
            return -1;
        }

        /**
         * Get the ID of a conflicting registration due to host, or -1 if none.
         *
         * <p>It's valid that multiple registrations from the same user are using the same hostname.
         *
         * <p>If there's already another registration with the same hostname requested by another
         * user, this is considered a conflict.
         */
        int getConflictingRegistrationDueToHost(@NonNull NsdServiceInfo info, int clientUid) {
            if (TextUtils.isEmpty(info.getHostname())) {
                return -1;
            }
            for (int i = 0; i < mPendingRegistrations.size(); i++) {
                final Registration otherRegistration = mPendingRegistrations.valueAt(i);
                final NsdServiceInfo otherInfo = otherRegistration.getServiceInfo();
                if (clientUid != otherRegistration.mClientUid
                        && MdnsUtils.equalsIgnoreDnsCase(
                                info.getHostname(), otherInfo.getHostname())) {
                    return mPendingRegistrations.keyAt(i);
                }
            }
            return -1;
        }

        /**
         * Add a service to advertise.
         *
         * <p>Conflicts must be checked via {@link #getConflictingRegistrationDueToService} and
         * {@link #getConflictingRegistrationDueToHost} before attempting to add.
         */
        void addService(int id, @NonNull Registration registration) {
            mPendingRegistrations.put(id, registration);
            for (int i = 0; i < mAdvertisers.size(); i++) {
                try {
                    mAdvertisers.valueAt(i).addService(id, registration.getServiceInfo(),
                            registration.getAdvertisingOptions());
                } catch (NameConflictException e) {
                    mSharedLog.wtf("Name conflict adding services that should have unique names",
                            e);
                }
            }
        }

        /**
         * Update an already registered service.
         * The caller is expected to check that the service being updated doesn't change its name
         */
        void updateService(int id, @NonNull Registration registration) {
            mPendingRegistrations.put(id, registration);
            for (int i = 0; i < mAdvertisers.size(); i++) {
                mAdvertisers.valueAt(i).updateService(
                        id, registration.getServiceInfo().getSubtypes());
            }
        }

        void removeService(int id) {
            mPendingRegistrations.remove(id);
            for (int i = 0; i < mAdvertisers.size(); i++) {
                final MdnsInterfaceAdvertiser advertiser = mAdvertisers.valueAt(i);
                advertiser.removeService(id);

                if (mMdnsFeatureFlags.mIsMdnsOffloadFeatureEnabled) {
                    maybeSendOffloadStop(advertiser.getSocketInterfaceName(), id);
                }
            }
        }

        int getServiceRepliedRequestsCount(int id) {
            int repliedRequestsCount = NO_PACKET;
            for (int i = 0; i < mAdvertisers.size(); i++) {
                repliedRequestsCount += mAdvertisers.valueAt(i).getServiceRepliedRequestsCount(id);
            }
            return repliedRequestsCount;
        }

        int getSentPacketCount(int id) {
            int sentPacketCount = NO_PACKET;
            for (int i = 0; i < mAdvertisers.size(); i++) {
                sentPacketCount += mAdvertisers.valueAt(i).getSentPacketCount(id);
            }
            return sentPacketCount;
        }

        @Override
        public void onSocketCreated(@NonNull SocketKey socketKey,
                @NonNull MdnsInterfaceSocket socket,
                @NonNull List<LinkAddress> addresses) {
            MdnsInterfaceAdvertiser advertiser = mAllAdvertisers.get(socket);
            if (advertiser == null) {
                advertiser = mDeps.makeAdvertiser(socket, addresses, mLooper, mPacketCreationBuffer,
                        mInterfaceAdvertiserCb, mDeviceHostName,
                        mSharedLog.forSubComponent(socket.getInterface().getName()),
                        mMdnsFeatureFlags);
                mAllAdvertisers.put(socket, advertiser);
                advertiser.start();
            }
            mAdvertisers.put(socket, advertiser);
            for (int i = 0; i < mPendingRegistrations.size(); i++) {
                final Registration registration = mPendingRegistrations.valueAt(i);
                try {
                    advertiser.addService(mPendingRegistrations.keyAt(i),
                            registration.getServiceInfo(), registration.getAdvertisingOptions());
                } catch (NameConflictException e) {
                    mSharedLog.wtf("Name conflict adding services that should have unique names",
                            e);
                }
            }
        }

        @Override
        public void onInterfaceDestroyed(@NonNull SocketKey socketKey,
                @NonNull MdnsInterfaceSocket socket) {
            final MdnsInterfaceAdvertiser advertiser = mAdvertisers.get(socket);
            if (advertiser != null) advertiser.destroyNow();
        }

        @Override
        public void onAddressesChanged(@NonNull SocketKey socketKey,
                @NonNull MdnsInterfaceSocket socket, @NonNull List<LinkAddress> addresses) {
            final MdnsInterfaceAdvertiser advertiser = mAdvertisers.get(socket);
            if (advertiser == null)  {
                return;
            }
            advertiser.updateAddresses(addresses);

            if (mMdnsFeatureFlags.mIsMdnsOffloadFeatureEnabled) {
                // Update address should trigger offload packet update.
                final String interfaceName = advertiser.getSocketInterfaceName();
                final List<OffloadServiceInfoWrapper> existingOffloadServiceInfoWrappers =
                        mInterfaceOffloadServices.get(interfaceName);
                if (existingOffloadServiceInfoWrappers == null) {
                    return;
                }
                final List<OffloadServiceInfoWrapper> updatedOffloadServiceInfoWrappers =
                        new ArrayList<>(existingOffloadServiceInfoWrappers.size());
                for (OffloadServiceInfoWrapper oldWrapper : existingOffloadServiceInfoWrappers) {
                    OffloadServiceInfoWrapper newWrapper = new OffloadServiceInfoWrapper(
                            oldWrapper.mServiceId,
                            oldWrapper.mOffloadServiceInfo.withOffloadPayload(
                                    advertiser.getRawOffloadPayload(oldWrapper.mServiceId))
                    );
                    updatedOffloadServiceInfoWrappers.add(newWrapper);
                    mCb.onOffloadStartOrUpdate(interfaceName, newWrapper.mOffloadServiceInfo);
                }
                mInterfaceOffloadServices.put(interfaceName, updatedOffloadServiceInfoWrappers);
            }
        }
    }

    /**
     * The wrapper class for OffloadServiceInfo including the serviceId.
     */
    public static class OffloadServiceInfoWrapper {
        public final @NonNull OffloadServiceInfo mOffloadServiceInfo;
        public final int mServiceId;

        OffloadServiceInfoWrapper(int serviceId, OffloadServiceInfo offloadServiceInfo) {
            mOffloadServiceInfo = offloadServiceInfo;
            mServiceId = serviceId;
        }
    }

    private static class Registration {
        @Nullable
        final String mOriginalServiceName;
        @Nullable
        final String mOriginalHostname;
        boolean mNotifiedRegistrationSuccess;
        private int mServiceNameConflictCount;
        private int mHostnameConflictCount;
        @NonNull
        private NsdServiceInfo mServiceInfo;
        final int mClientUid;
        private final MdnsAdvertisingOptions mAdvertisingOptions;
        int mConflictDuringProbingCount;
        int mConflictAfterProbingCount;

        private Registration(@NonNull NsdServiceInfo serviceInfo, int clientUid,
                @NonNull MdnsAdvertisingOptions advertisingOptions) {
            this.mOriginalServiceName = serviceInfo.getServiceName();
            this.mOriginalHostname = serviceInfo.getHostname();
            this.mServiceInfo = serviceInfo;
            this.mClientUid = clientUid;
            this.mAdvertisingOptions = advertisingOptions;
        }

        /** Check if the new {@link NsdServiceInfo} doesn't update any data other than subtypes. */
        public boolean isSubtypeOnlyUpdate(@NonNull NsdServiceInfo newInfo) {
            return Objects.equals(newInfo.getServiceName(), mOriginalServiceName)
                    && Objects.equals(newInfo.getServiceType(), mServiceInfo.getServiceType())
                    && newInfo.getPort() == mServiceInfo.getPort()
                    && Objects.equals(newInfo.getHostname(), mOriginalHostname)
                    && Objects.equals(newInfo.getHostAddresses(), mServiceInfo.getHostAddresses())
                    && Objects.equals(newInfo.getNetwork(), mServiceInfo.getNetwork());
        }

        /**
         * Update subTypes for the registration.
         */
        public void updateSubtypes(@NonNull Set<String> subtypes) {
            mServiceInfo = new NsdServiceInfo(mServiceInfo);
            mServiceInfo.setSubtypes(subtypes);
        }

        /**
         * Update the registration to use a different service name, after a conflict was found.
         *
         * @param newInfo New service info to use.
         * @param renameCount How many renames were done before reaching the current name.
         */
        private void updateForServiceConflict(@NonNull NsdServiceInfo newInfo, int renameCount) {
            mServiceNameConflictCount += renameCount;
            mServiceInfo = newInfo;
        }

        /**
         * Update the registration to use a different host name, after a conflict was found.
         *
         * @param newInfo New service info to use.
         * @param renameCount How many renames were done before reaching the current name.
         */
        private void updateForHostConflict(@NonNull NsdServiceInfo newInfo, int renameCount) {
            mHostnameConflictCount += renameCount;
            mServiceInfo = newInfo;
        }

        /**
         * Make a new service name for the registration, after a conflict was found.
         *
         * If a name conflict was found during probing or because different advertising requests
         * used the same name, the registration is attempted again with a new name (here using
         * a number suffix, (1), (2) etc). Registration success is notified once probing succeeds
         * with a new name. This matches legacy behavior based on mdnsresponder, and appendix D of
         * RFC6763.
         *
         * @param renameCount How much to increase the number suffix for this conflict.
         */
        @NonNull
        public NsdServiceInfo makeNewServiceInfoForServiceConflict(int renameCount) {
            // In case of conflict choose a different service name. After the first conflict use
            // "Name (2)", then "Name (3)" etc.
            // TODO: use a hidden method in NsdServiceInfo once MdnsAdvertiser is moved to service-t
            final NsdServiceInfo newInfo = new NsdServiceInfo(mServiceInfo);
            newInfo.setServiceName(getUpdatedServiceName(renameCount));
            return newInfo;
        }

        /**
         * Make a new hostname for the registration, after a conflict was found.
         *
         * <p>If a name conflict was found during probing or because different advertising requests
         * used the same name, the registration is attempted again with a new name (here using a
         * number suffix, -1, -2, etc). Registration success is notified once probing succeeds with
         * a new name.
         *
         * @param renameCount How much to increase the number suffix for this conflict.
         */
        @NonNull
        public NsdServiceInfo makeNewServiceInfoForHostConflict(int renameCount) {
            // In case of conflict choose a different hostname. After the first conflict use
            // "Name-2", then "Name-3" etc.
            final NsdServiceInfo newInfo = new NsdServiceInfo(mServiceInfo);
            newInfo.setHostname(getUpdatedHostname(renameCount));
            return newInfo;
        }

        private String getUpdatedServiceName(int renameCount) {
            final String suffix = " (" + (mServiceNameConflictCount + renameCount + 1) + ")";
            final String truncatedServiceName = MdnsUtils.truncateServiceName(mOriginalServiceName,
                    MAX_LABEL_LENGTH - suffix.length());
            return truncatedServiceName + suffix;
        }

        private String getUpdatedHostname(int renameCount) {
            final String suffix = "-" + (mHostnameConflictCount + renameCount + 1);
            final String truncatedHostname =
                    MdnsUtils.truncateServiceName(
                            mOriginalHostname, MAX_LABEL_LENGTH - suffix.length());
            return truncatedHostname + suffix;
        }

        @NonNull
        public NsdServiceInfo getServiceInfo() {
            return mServiceInfo;
        }

        @NonNull
        public MdnsAdvertisingOptions getAdvertisingOptions() {
            return mAdvertisingOptions;
        }
    }

    /**
     * Callbacks for advertising services.
     *
     * Every method is called on the MdnsAdvertiser looper thread.
     */
    public interface AdvertiserCallback {
        /**
         * Called when a service was successfully registered, after probing.
         *
         * @param serviceId ID of the service provided when registering.
         * @param registeredInfo Registered info, which may be different from the requested info,
         *                       after probing and possibly choosing alternative service names.
         */
        void onRegisterServiceSucceeded(int serviceId, NsdServiceInfo registeredInfo);

        /**
         * Called when service registration failed.
         *
         * @param serviceId ID of the service provided when registering.
         * @param errorCode One of {@code NsdManager.FAILURE_*}
         */
        void onRegisterServiceFailed(int serviceId, int errorCode);

        // Unregistration is notified immediately as success in NsdService so no callback is needed
        // here.

        /**
         * Called when a service is ready to be sent for hardware offloading.
         *
         * @param interfaceName the interface for sending the update to.
         * @param offloadServiceInfo the offloading content.
         */
        void onOffloadStartOrUpdate(@NonNull String interfaceName,
                @NonNull OffloadServiceInfo offloadServiceInfo);

        /**
         * Called when a service is removed or the MdnsInterfaceAdvertiser is destroyed.
         *
         * @param interfaceName the interface for sending the update to.
         * @param offloadServiceInfo the offloading content.
         */
        void onOffloadStop(@NonNull String interfaceName,
                @NonNull OffloadServiceInfo offloadServiceInfo);
    }

    /**
     * Data class of avdverting metrics.
     */
    public static class AdvertiserMetrics {
        public final int mRepliedRequestsCount;
        public final int mSentPacketCount;
        public final int mConflictDuringProbingCount;
        public final int mConflictAfterProbingCount;

        public AdvertiserMetrics(int repliedRequestsCount, int sentPacketCount,
                int conflictDuringProbingCount, int conflictAfterProbingCount) {
            mRepliedRequestsCount = repliedRequestsCount;
            mSentPacketCount = sentPacketCount;
            mConflictDuringProbingCount = conflictDuringProbingCount;
            mConflictAfterProbingCount = conflictAfterProbingCount;
        }
    }

    public MdnsAdvertiser(@NonNull Looper looper, @NonNull MdnsSocketProvider socketProvider,
            @NonNull AdvertiserCallback cb, @NonNull SharedLog sharedLog,
            @NonNull MdnsFeatureFlags mDnsFeatureFlags, @NonNull Context context) {
        this(looper, socketProvider, cb, new Dependencies(), sharedLog, mDnsFeatureFlags,
                context);
    }

    @VisibleForTesting
    MdnsAdvertiser(@NonNull Looper looper, @NonNull MdnsSocketProvider socketProvider,
            @NonNull AdvertiserCallback cb, @NonNull Dependencies deps,
            @NonNull SharedLog sharedLog, @NonNull MdnsFeatureFlags mDnsFeatureFlags,
            @NonNull Context context) {
        mLooper = looper;
        mCb = cb;
        mSocketProvider = socketProvider;
        mDeps = deps;
        mDeviceHostName = deps.generateHostname();
        mSharedLog = sharedLog;
        mMdnsFeatureFlags = mDnsFeatureFlags;
        final ConnectivityResources res = new ConnectivityResources(context);
        mServiceTypeToOffloadPriority = parseOffloadPriorityList(
                res.get().getStringArray(R.array.config_nsdOffloadServicesPriority), sharedLog);
    }

    private static Map<String, Integer> parseOffloadPriorityList(
            @NonNull String[] resValues, SharedLog sharedLog) {
        final Map<String, Integer> priorities = new ArrayMap<>(resValues.length);
        for (String entry : resValues) {
            final String[] priorityAndType = entry.split(":", 2);
            if (priorityAndType.length != 2) {
                sharedLog.wtf("Invalid config_nsdOffloadServicesPriority ignored: " + entry);
                continue;
            }

            final int priority;
            try {
                priority = Integer.parseInt(priorityAndType[0]);
            } catch (NumberFormatException e) {
                sharedLog.wtf("Invalid priority in config_nsdOffloadServicesPriority: " + entry);
                continue;
            }
            priorities.put(MdnsUtils.toDnsLowerCase(priorityAndType[1]), priority);
        }
        return priorities;
    }

    private void checkThread() {
        if (Thread.currentThread() != mLooper.getThread()) {
            throw new IllegalStateException("This must be called on the looper thread");
        }
    }

    /**
     * Add or update a service to advertise.
     *
     * @param id A unique ID for the service.
     * @param service The service info to advertise.
     * @param advertisingOptions The advertising options.
     * @param clientUid The UID who wants to advertise the service.
     */
    public void addOrUpdateService(int id, NsdServiceInfo service,
            MdnsAdvertisingOptions advertisingOptions, int clientUid) {
        checkThread();
        final Registration existingRegistration = mRegistrations.get(id);
        final Network network = service.getNetwork();
        final Set<String> subtypes = service.getSubtypes();
        Registration registration;
        if (advertisingOptions.isOnlyUpdate()) {
            if (existingRegistration == null) {
                mSharedLog.e("Update non existing registration for " + service);
                mCb.onRegisterServiceFailed(id, NsdManager.FAILURE_INTERNAL_ERROR);
                return;
            }
            if (!(existingRegistration.isSubtypeOnlyUpdate(service))) {
                mSharedLog.e("Update request can only update subType, serviceInfo: " + service
                        + ", existing serviceInfo: " + existingRegistration.getServiceInfo());
                mCb.onRegisterServiceFailed(id, NsdManager.FAILURE_INTERNAL_ERROR);
                return;

            }
            mSharedLog.i("Update service " + service + " with ID " + id + " and subtypes "
                    + subtypes + " advertisingOptions " + advertisingOptions);
            registration = existingRegistration;
            registration.updateSubtypes(subtypes);
        } else {
            if (existingRegistration != null) {
                mSharedLog.e("Adding duplicate registration for " + service);
                // TODO (b/264986328): add a more specific error code
                mCb.onRegisterServiceFailed(id, NsdManager.FAILURE_INTERNAL_ERROR);
                return;
            }
            mSharedLog.i("Adding service " + service + " with ID " + id + " and subtypes "
                    + subtypes + " advertisingOptions " + advertisingOptions);
            registration = new Registration(service, clientUid, advertisingOptions);
            final BiPredicate<Network, InterfaceAdvertiserRequest> checkConflictFilter;
            if (network == null) {
                // If registering on all networks, no advertiser must have conflicts
                checkConflictFilter = (net, adv) -> true;
            } else {
                // If registering on one network, the matching network advertiser and the one
                // for all networks must not have conflicts
                checkConflictFilter = (net, adv) -> net == null || network.equals(net);
            }
            updateRegistrationUntilNoConflict(checkConflictFilter, registration);
        }

        InterfaceAdvertiserRequest advertiser = mAdvertiserRequests.get(network);
        if (advertiser == null) {
            advertiser = new InterfaceAdvertiserRequest(network);
            mAdvertiserRequests.put(network, advertiser);
        }
        if (advertisingOptions.isOnlyUpdate()) {
            advertiser.updateService(id, registration);
        } else {
            advertiser.addService(id, registration);
        }
        mRegistrations.put(id, registration);
    }

    /**
     * Remove a previously added service.
     * @param id ID used when registering.
     */
    public void removeService(int id) {
        checkThread();
        if (!mRegistrations.contains(id)) return;
        mSharedLog.i("Removing service with ID " + id);
        for (int i = mAdvertiserRequests.size() - 1; i >= 0; i--) {
            final InterfaceAdvertiserRequest advertiser = mAdvertiserRequests.valueAt(i);
            advertiser.removeService(id);
        }
        mRegistrations.remove(id);
        // Regenerates host name when registrations removed.
        if (mRegistrations.size() == 0) {
            mDeviceHostName = mDeps.generateHostname();
        }
    }

    /**
     * Get advertising metrics.
     *
     * @param id ID used when registering.
     * @return The advertising metrics includes replied requests count, send packet count, conflict
     *         count during/after probing.
     */
    public AdvertiserMetrics getAdvertiserMetrics(int id) {
        checkThread();
        final Registration registration = mRegistrations.get(id);
        if (registration == null) {
            return new AdvertiserMetrics(
                    NO_PACKET /* repliedRequestsCount */,
                    NO_PACKET /* sentPacketCount */,
                    0 /* conflictDuringProbingCount */,
                    0 /* conflictAfterProbingCount */);
        }
        int repliedRequestsCount = NO_PACKET;
        int sentPacketCount = NO_PACKET;
        for (int i = 0; i < mAdvertiserRequests.size(); i++) {
            repliedRequestsCount +=
                    mAdvertiserRequests.valueAt(i).getServiceRepliedRequestsCount(id);
            sentPacketCount += mAdvertiserRequests.valueAt(i).getSentPacketCount(id);
        }
        return new AdvertiserMetrics(repliedRequestsCount, sentPacketCount,
                registration.mConflictDuringProbingCount, registration.mConflictAfterProbingCount);
    }

    private static <K, V> boolean any(@NonNull ArrayMap<K, V> map,
            @NonNull BiPredicate<K, V> predicate) {
        for (int i = 0; i < map.size(); i++) {
            if (predicate.test(map.keyAt(i), map.valueAt(i))) {
                return true;
            }
        }
        return false;
    }

    private void forAllAdvertisers(@NonNull Consumer<MdnsInterfaceAdvertiser> consumer) {
        any(mAllAdvertisers, (socket, advertiser) -> {
            consumer.accept(advertiser);
            return false;
        });
    }

    private OffloadServiceInfoWrapper createOffloadService(int serviceId,
            @NonNull Registration registration, byte[] rawOffloadPacket) {
        final NsdServiceInfo nsdServiceInfo = registration.getServiceInfo();
        final Integer mapPriority = mServiceTypeToOffloadPriority.get(
                MdnsUtils.toDnsLowerCase(nsdServiceInfo.getServiceType()));
        // Higher values of priority are less prioritized
        final int priority = mapPriority == null ? Integer.MAX_VALUE : mapPriority;
        final OffloadServiceInfo offloadServiceInfo = new OffloadServiceInfo(
                new OffloadServiceInfo.Key(nsdServiceInfo.getServiceName(),
                        nsdServiceInfo.getServiceType()),
                new ArrayList<>(nsdServiceInfo.getSubtypes()),
                String.join(".", mDeviceHostName),
                rawOffloadPacket,
                priority,
                // TODO: set the offloadType based on the callback timing.
                OffloadEngine.OFFLOAD_TYPE_REPLY);
        return new OffloadServiceInfoWrapper(serviceId, offloadServiceInfo);
    }
}
