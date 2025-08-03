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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresApi;
import android.net.LinkAddress;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.ArraySet;

import com.android.internal.annotations.VisibleForTesting;
import com.android.net.module.util.HexDump;
import com.android.net.module.util.SharedLog;
import com.android.server.connectivity.mdns.MdnsAnnouncer.BaseAnnouncementInfo;
import com.android.server.connectivity.mdns.MdnsPacketRepeater.PacketRepeaterCallback;
import com.android.server.connectivity.mdns.util.MdnsUtils;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A class that handles advertising services on a {@link MdnsInterfaceSocket} tied to an interface.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
public class MdnsInterfaceAdvertiser implements MulticastPacketReader.PacketHandler {
    public static final int CONFLICT_SERVICE = 1 << 0;
    public static final int CONFLICT_HOST = 1 << 1;

    private static final boolean DBG = MdnsAdvertiser.DBG;
    @VisibleForTesting
    public static final long EXIT_ANNOUNCEMENT_DELAY_MS = 100L;
    @NonNull
    private final ProbingCallback mProbingCallback = new ProbingCallback();
    @NonNull
    private final AnnouncingCallback mAnnouncingCallback = new AnnouncingCallback();
    @NonNull
    private final MdnsRecordRepository mRecordRepository;
    @NonNull
    private final Callback mCb;
    // Callbacks are on the same looper thread, but posted to the next handler loop
    @NonNull
    private final Handler mCbHandler;
    @NonNull
    private final MdnsInterfaceSocket mSocket;
    @NonNull
    private final MdnsAnnouncer mAnnouncer;
    @NonNull
    private final MdnsProber mProber;
    @NonNull
    private final MdnsReplySender mReplySender;
    @NonNull
    private final SharedLog mSharedLog;
    @NonNull
    private final byte[] mPacketCreationBuffer;
    @NonNull
    private final MdnsFeatureFlags mMdnsFeatureFlags;

    /**
     * Callbacks called by {@link MdnsInterfaceAdvertiser} to report status updates.
     */
    interface Callback {
        /**
         * Called by the advertiser after it successfully registered a service, after probing.
         */
        void onServiceProbingSucceeded(@NonNull MdnsInterfaceAdvertiser advertiser, int serviceId);

        /**
         * Called by the advertiser when a conflict was found, during or after probing.
         *
         * <p>If a conflict is found during probing, the {@link #renameServiceForConflict} must be
         * called to restart probing and attempt registration with a different name.
         *
         * <p>{@code conflictType} is a bitmap telling which part of the service is conflicting. See
         * {@link MdnsInterfaceAdvertiser#CONFLICT_SERVICE} and {@link
         * MdnsInterfaceAdvertiser#CONFLICT_HOST}.
         */
        void onServiceConflict(
                @NonNull MdnsInterfaceAdvertiser advertiser, int serviceId, int conflictType);

        /**
         * Called by the advertiser when it destroyed itself.
         *
         * This can happen after a call to {@link #destroyNow()}, or after all services were
         * unregistered and the advertiser finished sending exit announcements.
         */
        void onDestroyed(@NonNull MdnsInterfaceSocket socket);
    }

    /**
     * Callbacks from {@link MdnsProber}.
     */
    private class ProbingCallback implements PacketRepeaterCallback<MdnsProber.ProbingInfo> {
        @Override
        public void onSent(int index, @NonNull MdnsProber.ProbingInfo info, int sentPacketCount) {
            mRecordRepository.onProbingSent(info.getServiceId(), sentPacketCount);
        }
        @Override
        public void onFinished(MdnsProber.ProbingInfo info) {
            final MdnsAnnouncer.AnnouncementInfo announcementInfo;
            mSharedLog.i("Probing finished for service " + info.getServiceId());
            mCbHandler.post(() -> mCb.onServiceProbingSucceeded(
                    MdnsInterfaceAdvertiser.this, info.getServiceId()));
            try {
                announcementInfo = mRecordRepository.onProbingSucceeded(info);
            } catch (IOException e) {
                mSharedLog.e("Error building announcements", e);
                return;
            }

            mAnnouncer.startSending(info.getServiceId(), announcementInfo,
                    0L /* initialDelayMs */);
        }
    }

    /**
     * Callbacks from {@link MdnsAnnouncer}.
     */
    private class AnnouncingCallback implements PacketRepeaterCallback<BaseAnnouncementInfo> {
        @Override
        public void onSent(int index, @NonNull BaseAnnouncementInfo info, int sentPacketCount) {
            mRecordRepository.onAdvertisementSent(info.getServiceId(), sentPacketCount);
        }

        @Override
        public void onFinished(@NonNull BaseAnnouncementInfo info) {
            if (info instanceof MdnsAnnouncer.ExitAnnouncementInfo) {
                mRecordRepository.removeService(info.getServiceId());

                if (mRecordRepository.getServicesCount() == 0) {
                    destroyNow();
                }
            }
        }
    }

    /**
     * Dependencies for {@link MdnsInterfaceAdvertiser}, useful for testing.
     */
    @VisibleForTesting
    public static class Dependencies {
        /** @see MdnsRecordRepository */
        @NonNull
        public MdnsRecordRepository makeRecordRepository(@NonNull Looper looper,
                @NonNull String[] deviceHostName, @NonNull MdnsFeatureFlags mdnsFeatureFlags) {
            return new MdnsRecordRepository(looper, deviceHostName, mdnsFeatureFlags);
        }

        /** @see MdnsReplySender */
        @NonNull
        public MdnsReplySender makeReplySender(@NonNull String interfaceTag, @NonNull Looper looper,
                @NonNull MdnsInterfaceSocket socket, @NonNull byte[] packetCreationBuffer,
                @NonNull SharedLog sharedLog, @NonNull MdnsFeatureFlags mdnsFeatureFlags) {
            return new MdnsReplySender(looper, socket, packetCreationBuffer,
                    sharedLog.forSubComponent(
                            MdnsReplySender.class.getSimpleName() + "/" + interfaceTag), DBG,
                    mdnsFeatureFlags);
        }

        /** @see MdnsAnnouncer */
        public MdnsAnnouncer makeMdnsAnnouncer(@NonNull String interfaceTag, @NonNull Looper looper,
                @NonNull MdnsReplySender replySender,
                @Nullable PacketRepeaterCallback<MdnsAnnouncer.BaseAnnouncementInfo> cb,
                @NonNull SharedLog sharedLog) {
            return new MdnsAnnouncer(looper, replySender, cb,
                    sharedLog.forSubComponent(
                            MdnsAnnouncer.class.getSimpleName() + "/" + interfaceTag));
        }

        /** @see MdnsProber */
        public MdnsProber makeMdnsProber(@NonNull String interfaceTag, @NonNull Looper looper,
                @NonNull MdnsReplySender replySender,
                @NonNull PacketRepeaterCallback<MdnsProber.ProbingInfo> cb,
                @NonNull SharedLog sharedLog) {
            return new MdnsProber(looper, replySender, cb, sharedLog.forSubComponent(
                    MdnsProber.class.getSimpleName() + "/" + interfaceTag));
        }
    }

    public MdnsInterfaceAdvertiser(@NonNull MdnsInterfaceSocket socket,
            @NonNull List<LinkAddress> initialAddresses, @NonNull Looper looper,
            @NonNull byte[] packetCreationBuffer, @NonNull Callback cb,
            @NonNull String[] deviceHostName, @NonNull SharedLog sharedLog,
            @NonNull MdnsFeatureFlags mdnsFeatureFlags) {
        this(socket, initialAddresses, looper, packetCreationBuffer, cb,
                new Dependencies(), deviceHostName, sharedLog, mdnsFeatureFlags);
    }

    public MdnsInterfaceAdvertiser(@NonNull MdnsInterfaceSocket socket,
            @NonNull List<LinkAddress> initialAddresses, @NonNull Looper looper,
            @NonNull byte[] packetCreationBuffer, @NonNull Callback cb, @NonNull Dependencies deps,
            @NonNull String[] deviceHostName, @NonNull SharedLog sharedLog,
            @NonNull MdnsFeatureFlags mdnsFeatureFlags) {
        mRecordRepository = deps.makeRecordRepository(looper, deviceHostName, mdnsFeatureFlags);
        mRecordRepository.updateAddresses(initialAddresses);
        mSocket = socket;
        mCb = cb;
        mCbHandler = new Handler(looper);
        mReplySender = deps.makeReplySender(sharedLog.getTag(), looper, socket,
                packetCreationBuffer, sharedLog, mdnsFeatureFlags);
        mPacketCreationBuffer = packetCreationBuffer;
        mAnnouncer = deps.makeMdnsAnnouncer(sharedLog.getTag(), looper, mReplySender,
                mAnnouncingCallback, sharedLog);
        mProber = deps.makeMdnsProber(sharedLog.getTag(), looper, mReplySender, mProbingCallback,
                sharedLog);
        mSharedLog = sharedLog;
        mMdnsFeatureFlags = mdnsFeatureFlags;
    }

    /**
     * Start the advertiser.
     *
     * The advertiser will stop itself when all services are removed and exit announcements sent,
     * notifying via {@link Callback#onDestroyed}. This can also be triggered manually via
     * {@link #destroyNow()}.
     */
    public void start() {
        mSocket.addPacketHandler(this);
    }

    /**
     * Update an already registered service without sending exit/re-announcement packet.
     *
     * @param id An exiting service id
     * @param subtypes New subtypes
     */
    public void updateService(int id, @NonNull Set<String> subtypes) {
        // The current implementation is intended to be used in cases where subtypes don't get
        // announced.
        mRecordRepository.updateService(id, subtypes);
    }

    /**
     * Start advertising a service.
     *
     * @throws NameConflictException There is already a service being advertised with that name.
     */
    public void addService(int id, NsdServiceInfo service,
            @NonNull MdnsAdvertisingOptions advertisingOptions) throws NameConflictException {
        final int replacedExitingService =
                mRecordRepository.addService(id, service, advertisingOptions.getTtl());
        // Cancel announcements for the existing service. This only happens for exiting services
        // (so cancelling exiting announcements), as per RecordRepository.addService.
        if (replacedExitingService >= 0) {
            mSharedLog.i("Service " + replacedExitingService
                    + " getting re-added, cancelling exit announcements");
            mAnnouncer.stop(replacedExitingService);
        }
        mProber.startProbing(mRecordRepository.setServiceProbing(id));
    }

    /**
     * Stop advertising a service.
     *
     * This will trigger exit announcements for the service.
     */
    public void removeService(int id) {
        if (!mRecordRepository.hasActiveService(id)) return;
        mProber.stop(id);
        mAnnouncer.stop(id);
        final MdnsAnnouncer.ExitAnnouncementInfo exitInfo = mRecordRepository.exitService(id);
        if (exitInfo != null) {
            // This effectively schedules destroyNow(), as it is to be called when the exit
            // announcement finishes if there is no service left.
            // A non-zero exit announcement delay follows legacy mdnsresponder behavior, and is
            // also useful to ensure that when a host receives the exit announcement, the service
            // has been unregistered on all interfaces; so an announcement sent from interface A
            // that was already in-flight while unregistering won't be received after the exit on
            // interface B.
            mAnnouncer.startSending(id, exitInfo, EXIT_ANNOUNCEMENT_DELAY_MS);
        } else {
            // No exit announcement necessary: remove the service immediately.
            mRecordRepository.removeService(id);
            if (mRecordRepository.getServicesCount() == 0) {
                destroyNow();
            }
        }
    }

    /**
     * Get the replied request count from given service id.
     */
    public int getServiceRepliedRequestsCount(int id) {
        if (!mRecordRepository.hasActiveService(id)) return NO_PACKET;
        return mRecordRepository.getServiceRepliedRequestsCount(id);
    }

    /**
     * Get the total sent packet count from given service id.
     */
    public int getSentPacketCount(int id) {
        if (!mRecordRepository.hasActiveService(id)) return NO_PACKET;
        return mRecordRepository.getSentPacketCount(id);
    }

    /**
     * Update interface addresses used to advertise.
     *
     * This causes new address records to be announced.
     */
    public void updateAddresses(@NonNull List<LinkAddress> newAddresses) {
        mRecordRepository.updateAddresses(newAddresses);
        // TODO: restart advertising, but figure out what exit messages need to be sent for the
        // previous addresses
    }

    /**
     * Destroy the advertiser immediately, not sending any exit announcement.
     *
     * <p>Useful when the underlying network went away. This will trigger an onDestroyed callback.
     */
    public void destroyNow() {
        for (int serviceId : mRecordRepository.clearServices()) {
            mProber.stop(serviceId);
            mAnnouncer.stop(serviceId);
        }
        mReplySender.cancelAll();
        mSocket.removePacketHandler(this);
        mCbHandler.post(() -> mCb.onDestroyed(mSocket));
    }

    /**
     * Reset a service to the probing state due to a conflict found on the network.
     */
    public boolean maybeRestartProbingForConflict(int serviceId) {
        final MdnsProber.ProbingInfo probingInfo = mRecordRepository.setServiceProbing(serviceId);
        if (probingInfo == null) return false;

        mAnnouncer.stop(serviceId);
        mProber.restartForConflict(probingInfo);
        return true;
    }

    /**
     * Rename a service following a conflict found on the network, and restart probing.
     *
     * If the service was not registered on this {@link MdnsInterfaceAdvertiser}, this is a no-op.
     */
    public void renameServiceForConflict(int serviceId, NsdServiceInfo newInfo) {
        final MdnsProber.ProbingInfo probingInfo = mRecordRepository.renameServiceForConflict(
                serviceId, newInfo);
        if (probingInfo == null) return;

        mProber.restartForConflict(probingInfo);
    }

    /**
     * Indicates whether probing is in progress for the given service on this interface.
     *
     * Also returns false if the specified service is not registered.
     */
    public boolean isProbing(int serviceId) {
        return mRecordRepository.isProbing(serviceId);
    }

    @Override
    public void handlePacket(byte[] recvbuf, int length, InetSocketAddress src) {
        final MdnsPacket packet;
        try {
            packet = MdnsPacket.parse(new MdnsPacketReader(recvbuf, length, mMdnsFeatureFlags));
        } catch (MdnsPacket.ParseException e) {
            mSharedLog.e("Error parsing mDNS packet", e);
            if (DBG) {
                mSharedLog.v("Packet: " + HexDump.toHexString(recvbuf, 0, length));
            }
            return;
        }
        // recvbuf and src are reused after this returns; ensure references to src are not kept.
        final InetSocketAddress srcCopy = new InetSocketAddress(src.getAddress(), src.getPort());

        if (DBG) {
            mSharedLog.v("Parsed packet with " + packet.questions.size() + " questions, "
                    + packet.answers.size() + " answers, "
                    + packet.authorityRecords.size() + " authority, "
                    + packet.additionalRecords.size() + " additional from " + srcCopy);
        }

        Map<Integer, Integer> conflictingServices =
                mRecordRepository.getConflictingServices(packet);

        for (Map.Entry<Integer, Integer> entry : conflictingServices.entrySet()) {
            int serviceId = entry.getKey();
            int conflictType = entry.getValue();
            mCbHandler.post(
                    () -> {
                        mCb.onServiceConflict(this, serviceId, conflictType);
                    });
        }

        // Even in case of conflict, add replies for other services. But in general conflicts would
        // happen when the incoming packet has answer records (not a question), so there will be no
        // answer. One exception is simultaneous probe tiebreaking (rfc6762 8.2), in which case the
        // conflicting service is still probing and won't reply either.
        final MdnsReplyInfo answers = mRecordRepository.getReply(packet, srcCopy);

        if (answers == null) return;
        mReplySender.queueReply(answers);
    }

    /**
     * Get the socket interface name.
     */
    public String getSocketInterfaceName() {
        return mSocket.getInterface().getName();
    }

    /**
     * Gets the offload MdnsPacket.
     * @param serviceId The serviceId.
     * @return the raw offload payload
     */
    @NonNull
    public byte[] getRawOffloadPayload(int serviceId) {
        try {
            return MdnsUtils.createRawDnsPacket(mPacketCreationBuffer,
                    mRecordRepository.getOffloadPacket(serviceId));
        } catch (IOException | IllegalArgumentException e) {
            mSharedLog.wtf("Cannot create rawOffloadPacket: ", e);
            return new byte[0];
        }
    }
}
