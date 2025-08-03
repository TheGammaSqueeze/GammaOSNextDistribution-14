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

import static com.android.server.connectivity.mdns.MdnsConstants.IPV4_SOCKET_ADDR;
import static com.android.server.connectivity.mdns.MdnsConstants.IPV6_SOCKET_ADDR;
import static com.android.server.connectivity.mdns.MdnsConstants.NO_PACKET;
import static com.android.server.connectivity.mdns.MdnsInterfaceAdvertiser.CONFLICT_HOST;
import static com.android.server.connectivity.mdns.MdnsInterfaceAdvertiser.CONFLICT_SERVICE;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.TargetApi;
import android.net.LinkAddress;
import android.net.nsd.NsdServiceInfo;
import android.os.Build;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.SparseArray;

import com.android.internal.annotations.VisibleForTesting;
import com.android.net.module.util.CollectionUtils;
import com.android.net.module.util.HexDump;
import com.android.server.connectivity.mdns.util.MdnsUtils;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * A repository of records advertised through {@link MdnsInterfaceAdvertiser}.
 *
 * Must be used on a consistent looper thread.
 */
@TargetApi(Build.VERSION_CODES.TIRAMISU) // Allow calling T+ APIs; this is only loaded on T+
public class MdnsRecordRepository {
    // RFC6762 p.15
    private static final long MIN_MULTICAST_REPLY_INTERVAL_MS = 1_000L;

    // TTLs as per RFC6762 10.
    // TTL for records with a host name as the resource record's name (e.g., A, AAAA, HINFO) or a
    // host name contained within the resource record's rdata (e.g., SRV, reverse mapping PTR
    // record)
    private static final long DEFAULT_NAME_RECORDS_TTL_MILLIS = TimeUnit.SECONDS.toMillis(120);
    // TTL for other records
    private static final long DEFAULT_NON_NAME_RECORDS_TTL_MILLIS = TimeUnit.MINUTES.toMillis(75);

    // Top-level domain for link-local queries, as per RFC6762 3.
    private static final String LOCAL_TLD = "local";

    // Service type for service enumeration (RFC6763 9.)
    private static final String[] DNS_SD_SERVICE_TYPE =
            new String[] { "_services", "_dns-sd", "_udp", LOCAL_TLD };

    @NonNull
    private final Random mDelayGenerator = new Random();
    // Map of service unique ID -> records for service
    @NonNull
    private final SparseArray<ServiceRegistration> mServices = new SparseArray<>();
    @NonNull
    private final List<RecordInfo<?>> mGeneralRecords = new ArrayList<>();
    @NonNull
    private final Looper mLooper;
    @NonNull
    private final String[] mDeviceHostname;
    @NonNull
    private final MdnsFeatureFlags mMdnsFeatureFlags;

    public MdnsRecordRepository(@NonNull Looper looper, @NonNull String[] deviceHostname,
            @NonNull MdnsFeatureFlags mdnsFeatureFlags) {
        this(looper, new Dependencies(), deviceHostname, mdnsFeatureFlags);
    }

    @VisibleForTesting
    public MdnsRecordRepository(@NonNull Looper looper, @NonNull Dependencies deps,
            @NonNull String[] deviceHostname, @NonNull MdnsFeatureFlags mdnsFeatureFlags) {
        mDeviceHostname = deviceHostname;
        mLooper = looper;
        mMdnsFeatureFlags = mdnsFeatureFlags;
    }

    /**
     * Dependencies to use with {@link MdnsRecordRepository}, useful for testing.
     */
    @VisibleForTesting
    public static class Dependencies {

        /**
         * @see NetworkInterface#getInetAddresses().
         */
        @NonNull
        public Enumeration<InetAddress> getInterfaceInetAddresses(@NonNull NetworkInterface iface) {
            return iface.getInetAddresses();
        }
    }

    private static class RecordInfo<T extends MdnsRecord> {
        public final T record;
        public final NsdServiceInfo serviceInfo;

        /**
         * Whether the name of this record is expected to be fully owned by the service or may be
         * advertised by other hosts as well (shared).
         */
        public final boolean isSharedName;

        /**
         * Last time (as per SystemClock.elapsedRealtime) when advertised via multicast, 0 if never
         */
        public long lastAdvertisedTimeMs;

        /**
         * Last time (as per SystemClock.elapsedRealtime) when sent via unicast or multicast,
         * 0 if never
         */
        // FIXME: the `lastSentTimeMs` and `lastAdvertisedTimeMs` should be maintained separately
        // for IPv4 and IPv6, because neither IPv4 nor and IPv6 clients can receive replies in
        // different address space.
        public long lastSentTimeMs;

        RecordInfo(NsdServiceInfo serviceInfo, T record, boolean sharedName) {
            this.serviceInfo = serviceInfo;
            this.record = record;
            this.isSharedName = sharedName;
        }
    }

    private static class ServiceRegistration {
        @NonNull
        public final List<RecordInfo<?>> allRecords;
        @NonNull
        public final List<RecordInfo<MdnsPointerRecord>> ptrRecords;
        @Nullable
        public final RecordInfo<MdnsServiceRecord> srvRecord;
        @Nullable
        public final RecordInfo<MdnsTextRecord> txtRecord;
        @NonNull
        public final List<RecordInfo<MdnsInetAddressRecord>> addressRecords;
        @NonNull
        public final NsdServiceInfo serviceInfo;

        /**
         * Whether the service is sending exit announcements and will be destroyed soon.
         */
        public boolean exiting;

        /**
         * The replied query packet count of this service.
         */
        public int repliedServiceCount = NO_PACKET;

        /**
         * The sent packet count of this service (including announcements and probes).
         */
        public int sentPacketCount = NO_PACKET;

        /**
         * Whether probing is still in progress.
         */
        private boolean isProbing;

        @Nullable
        private Duration ttl;

        /**
         * Create a ServiceRegistration with only update the subType.
         */
        ServiceRegistration withSubtypes(@NonNull Set<String> newSubtypes) {
            NsdServiceInfo newServiceInfo = new NsdServiceInfo(serviceInfo);
            newServiceInfo.setSubtypes(newSubtypes);
            return new ServiceRegistration(srvRecord.record.getServiceHost(), newServiceInfo,
                    repliedServiceCount, sentPacketCount, exiting, isProbing, ttl);
        }

        /**
         * Create a ServiceRegistration for dns-sd service registration (RFC6763).
         */
        ServiceRegistration(@NonNull String[] deviceHostname, @NonNull NsdServiceInfo serviceInfo,
                int repliedServiceCount, int sentPacketCount, boolean exiting, boolean isProbing,
                @Nullable Duration ttl) {
            this.serviceInfo = serviceInfo;

            final long nonNameRecordsTtlMillis;
            final long nameRecordsTtlMillis;

            // When custom TTL is specified, all records of the service will use the custom TTL.
            // This is typically useful for SRP (Service Registration Protocol:
            // https://datatracker.ietf.org/doc/html/draft-ietf-dnssd-srp-24) Advertising Proxy
            // where all records in a single SRP are required the same TTL.
            if (ttl != null) {
                nonNameRecordsTtlMillis = ttl.toMillis();
                nameRecordsTtlMillis = ttl.toMillis();
            } else {
                nonNameRecordsTtlMillis = DEFAULT_NON_NAME_RECORDS_TTL_MILLIS;
                nameRecordsTtlMillis = DEFAULT_NAME_RECORDS_TTL_MILLIS;
            }

            final boolean hasService = !TextUtils.isEmpty(serviceInfo.getServiceType());
            final boolean hasCustomHost = !TextUtils.isEmpty(serviceInfo.getHostname());
            final String[] hostname =
                    hasCustomHost
                            ? new String[] {serviceInfo.getHostname(), LOCAL_TLD}
                            : deviceHostname;
            final ArrayList<RecordInfo<?>> allRecords = new ArrayList<>(5);

            if (hasService) {
                final String[] serviceType = splitServiceType(serviceInfo);
                final String[] serviceName = splitFullyQualifiedName(serviceInfo, serviceType);
                // Service PTR records
                ptrRecords = new ArrayList<>(serviceInfo.getSubtypes().size() + 1);
                ptrRecords.add(new RecordInfo<>(
                        serviceInfo,
                        new MdnsPointerRecord(
                                serviceType,
                                0L /* receiptTimeMillis */,
                                false /* cacheFlush */,
                                nonNameRecordsTtlMillis,
                                serviceName),
                        true /* sharedName */));
                for (String subtype : serviceInfo.getSubtypes()) {
                    ptrRecords.add(new RecordInfo<>(
                            serviceInfo,
                            new MdnsPointerRecord(
                                    MdnsUtils.constructFullSubtype(serviceType, subtype),
                                    0L /* receiptTimeMillis */,
                                    false /* cacheFlush */,
                                    nonNameRecordsTtlMillis,
                                    serviceName),
                            true /* sharedName */));
                }

                srvRecord = new RecordInfo<>(
                        serviceInfo,
                        new MdnsServiceRecord(serviceName,
                                0L /* receiptTimeMillis */,
                                true /* cacheFlush */,
                                nameRecordsTtlMillis,
                                0 /* servicePriority */, 0 /* serviceWeight */,
                                serviceInfo.getPort(),
                                hostname),
                        false /* sharedName */);

                txtRecord = new RecordInfo<>(
                        serviceInfo,
                        new MdnsTextRecord(serviceName,
                                0L /* receiptTimeMillis */,
                                // Service name is verified unique after probing
                                true /* cacheFlush */,
                                nonNameRecordsTtlMillis,
                                attrsToTextEntries(serviceInfo.getAttributes())),
                        false /* sharedName */);

                allRecords.addAll(ptrRecords);
                allRecords.add(srvRecord);
                allRecords.add(txtRecord);
                // Service type enumeration record (RFC6763 9.)
                allRecords.add(new RecordInfo<>(
                        serviceInfo,
                        new MdnsPointerRecord(
                                DNS_SD_SERVICE_TYPE,
                                0L /* receiptTimeMillis */,
                                false /* cacheFlush */,
                                nonNameRecordsTtlMillis,
                                serviceType),
                        true /* sharedName */));
            } else {
                ptrRecords = Collections.emptyList();
                srvRecord = null;
                txtRecord = null;
            }

            if (hasCustomHost) {
                addressRecords = new ArrayList<>(serviceInfo.getHostAddresses().size());
                for (InetAddress address : serviceInfo.getHostAddresses()) {
                    addressRecords.add(new RecordInfo<>(
                                    serviceInfo,
                                    new MdnsInetAddressRecord(hostname,
                                            0L /* receiptTimeMillis */,
                                            true /* cacheFlush */,
                                            nameRecordsTtlMillis,
                                            address),
                                    false /* sharedName */));
                }
                allRecords.addAll(addressRecords);
            } else {
                addressRecords = Collections.emptyList();
            }

            this.allRecords = Collections.unmodifiableList(allRecords);
            this.repliedServiceCount = repliedServiceCount;
            this.sentPacketCount = sentPacketCount;
            this.isProbing = isProbing;
            this.exiting = exiting;
        }

        /**
         * Create a ServiceRegistration for dns-sd service registration (RFC6763).
         *
         * @param deviceHostname Hostname of the device (for the interface used)
         * @param serviceInfo Service to advertise
         */
        ServiceRegistration(@NonNull String[] deviceHostname, @NonNull NsdServiceInfo serviceInfo,
                int repliedServiceCount, int sentPacketCount, @Nullable Duration ttl) {
            this(deviceHostname, serviceInfo,repliedServiceCount, sentPacketCount,
                    false /* exiting */, true /* isProbing */, ttl);
        }

        void setProbing(boolean probing) {
            this.isProbing = probing;
        }

    }

    /**
     * Inform the repository of the latest interface addresses.
     */
    public void updateAddresses(@NonNull List<LinkAddress> newAddresses) {
        mGeneralRecords.clear();
        for (LinkAddress addr : newAddresses) {
            final String[] revDnsAddr = getReverseDnsAddress(addr.getAddress());
            mGeneralRecords.add(new RecordInfo<>(
                    null /* serviceInfo */,
                    new MdnsPointerRecord(
                            revDnsAddr,
                            0L /* receiptTimeMillis */,
                            true /* cacheFlush */,
                            DEFAULT_NAME_RECORDS_TTL_MILLIS,
                            mDeviceHostname),
                    false /* sharedName */));

            mGeneralRecords.add(new RecordInfo<>(
                    null /* serviceInfo */,
                    new MdnsInetAddressRecord(
                            mDeviceHostname,
                            0L /* receiptTimeMillis */,
                            true /* cacheFlush */,
                            DEFAULT_NAME_RECORDS_TTL_MILLIS,
                            addr.getAddress()),
                    false /* sharedName */));
        }
    }

    /**
     * Update a service that already registered in the repository.
     *
     * @param serviceId An existing service ID.
     * @param subtypes New subtypes
     */
    public void updateService(int serviceId, @NonNull Set<String> subtypes) {
        final ServiceRegistration existingRegistration = mServices.get(serviceId);
        if (existingRegistration == null) {
            throw new IllegalArgumentException(
                    "Service ID must already exist for an update request: " + serviceId);
        }
        final ServiceRegistration updatedRegistration = existingRegistration.withSubtypes(
                subtypes);
        mServices.put(serviceId, updatedRegistration);
    }

    /**
     * Add a service to the repository.
     *
     * This may remove/replace any existing service that used the name added but is exiting.
     * @param serviceId A unique service ID.
     * @param serviceInfo Service info to add.
     * @param ttl the TTL duration for all records of {@code serviceInfo} or {@code null}
     * @return If the added service replaced another with a matching name (which was exiting), the
     *         ID of the replaced service.
     * @throws NameConflictException There is already a (non-exiting) service using the name.
     */
    public int addService(int serviceId, NsdServiceInfo serviceInfo, @Nullable Duration ttl)
            throws NameConflictException {
        if (mServices.contains(serviceId)) {
            throw new IllegalArgumentException(
                    "Service ID must not be reused across registrations: " + serviceId);
        }

        final int existing =
                getServiceByNameAndType(serviceInfo.getServiceName(), serviceInfo.getServiceType());
        // It's OK to re-add a service that is exiting
        if (existing >= 0 && !mServices.get(existing).exiting) {
            throw new NameConflictException(existing);
        }

        final ServiceRegistration registration = new ServiceRegistration(
                mDeviceHostname, serviceInfo, NO_PACKET /* repliedServiceCount */,
                NO_PACKET /* sentPacketCount */, ttl);
        mServices.put(serviceId, registration);

        // Remove existing exiting service
        mServices.remove(existing);
        return existing;
    }

    /**
     * @return The ID of the service identified by its name and type, or -1 if none.
     */
    private int getServiceByNameAndType(
            @Nullable String serviceName, @Nullable String serviceType) {
        if (TextUtils.isEmpty(serviceName) || TextUtils.isEmpty(serviceType)) {
            return -1;
        }
        for (int i = 0; i < mServices.size(); i++) {
            final NsdServiceInfo info = mServices.valueAt(i).serviceInfo;
            if (MdnsUtils.equalsIgnoreDnsCase(serviceName, info.getServiceName())
                    && MdnsUtils.equalsIgnoreDnsCase(serviceType, info.getServiceType())) {
                return mServices.keyAt(i);
            }
        }
        return -1;
    }

    private MdnsProber.ProbingInfo makeProbingInfo(
            int serviceId, ServiceRegistration registration) {
        final List<MdnsRecord> probingRecords = new ArrayList<>();
        // Probe with cacheFlush cleared; it is set when announcing, as it was verified unique:
        // RFC6762 10.2
        if (registration.srvRecord != null) {
            MdnsServiceRecord srvRecord = registration.srvRecord.record;
            probingRecords.add(new MdnsServiceRecord(srvRecord.getName(),
                    0L /* receiptTimeMillis */,
                    false /* cacheFlush */,
                    srvRecord.getTtl(),
                    srvRecord.getServicePriority(), srvRecord.getServiceWeight(),
                    srvRecord.getServicePort(),
                    srvRecord.getServiceHost()));
        }

        for (MdnsInetAddressRecord inetAddressRecord :
                makeProbingInetAddressRecords(registration.serviceInfo)) {
            probingRecords.add(new MdnsInetAddressRecord(inetAddressRecord.getName(),
                    0L /* receiptTimeMillis */,
                    false /* cacheFlush */,
                    inetAddressRecord.getTtl(),
                    inetAddressRecord.getInet4Address() == null
                            ? inetAddressRecord.getInet6Address()
                            : inetAddressRecord.getInet4Address()));
        }
        return new MdnsProber.ProbingInfo(serviceId, probingRecords);
    }

    private static List<MdnsServiceInfo.TextEntry> attrsToTextEntries(Map<String, byte[]> attrs) {
        final List<MdnsServiceInfo.TextEntry> out = new ArrayList<>(attrs.size());
        for (Map.Entry<String, byte[]> attr : attrs.entrySet()) {
            out.add(new MdnsServiceInfo.TextEntry(attr.getKey(), attr.getValue()));
        }
        return out;
    }

    /**
     * Mark a service in the repository as exiting.
     * @param id ID of the service, used at registration time.
     * @return The exit announcement to indicate the service was removed, or null if not necessary.
     */
    @Nullable
    public MdnsAnnouncer.ExitAnnouncementInfo exitService(int id) {
        final ServiceRegistration registration = mServices.get(id);
        if (registration == null) return null;
        if (registration.exiting) return null;

        // Send exit (TTL 0) for the PTR records, if at least one was sent (in particular don't send
        // if still probing)
        if (CollectionUtils.all(registration.ptrRecords, r -> r.lastSentTimeMs == 0L)) {
            return null;
        }

        registration.exiting = true;
        final List<MdnsRecord> expiredRecords = CollectionUtils.map(registration.ptrRecords,
                r -> new MdnsPointerRecord(
                        r.record.getName(),
                        0L /* receiptTimeMillis */,
                        // RFC6762#10.1, the cache flush bit should be false for existing
                        // announcement. Otherwise, the record will be deleted immediately.
                        false /* cacheFlush */,
                        0L /* ttlMillis */,
                        r.record.getPointer()));

        // Exit should be skipped if the record is still advertised by another service, but that
        // would be a conflict (2 service registrations with the same service name), so it would
        // not have been allowed by the repository.
        return new MdnsAnnouncer.ExitAnnouncementInfo(id, expiredRecords);
    }

    public void removeService(int id) {
        mServices.remove(id);
    }

    /**
     * @return The number of services currently held in the repository, including exiting services.
     */
    public int getServicesCount() {
        return mServices.size();
    }

    /**
     * @return The replied request count of the service.
     */
    public int getServiceRepliedRequestsCount(int id) {
        final ServiceRegistration service = mServices.get(id);
        if (service == null) return NO_PACKET;
        return service.repliedServiceCount;
    }

    /**
     * @return The total sent packet count of the service.
     */
    public int getSentPacketCount(int id) {
        final ServiceRegistration service = mServices.get(id);
        if (service == null) return NO_PACKET;
        return service.sentPacketCount;
    }

    /**
     * Remove all services from the repository
     * @return IDs of the removed services
     */
    @NonNull
    public int[] clearServices() {
        final int[] ret = new int[mServices.size()];
        for (int i = 0; i < mServices.size(); i++) {
            ret[i] = mServices.keyAt(i);
        }
        mServices.clear();
        return ret;
    }

    private boolean isTruncatedKnownAnswerPacket(MdnsPacket packet) {
        if (!mMdnsFeatureFlags.isKnownAnswerSuppressionEnabled()
                // Should ignore the response packet.
                || (packet.flags & MdnsConstants.FLAGS_RESPONSE) != 0) {
            return false;
        }
        // Check the packet contains no questions and as many more Known-Answer records as will fit.
        return packet.questions.size() == 0 && packet.answers.size() != 0;
    }

    /**
     * Get the reply to send to an incoming packet.
     *
     * @param packet The incoming packet.
     * @param src The source address of the incoming packet.
     */
    @Nullable
    public MdnsReplyInfo getReply(MdnsPacket packet, InetSocketAddress src) {
        final long now = SystemClock.elapsedRealtime();

        // TODO: b/322142420 - Set<RecordInfo<?>> may contain duplicate records wrapped in different
        // RecordInfo<?>s when custom host is enabled.

        // Use LinkedHashSet for preserving the insert order of the RRs, so that RRs of the same
        // service or host are grouped together (which is more developer-friendly).
        final Set<RecordInfo<?>> answerInfo = new LinkedHashSet<>();
        final Set<RecordInfo<?>> additionalAnswerInfo = new LinkedHashSet<>();
        // Reply unicast if the feature is enabled AND all replied questions request unicast
        final boolean replyUnicastEnabled = mMdnsFeatureFlags.isUnicastReplyEnabled();
        boolean replyUnicast = replyUnicastEnabled;
        for (MdnsRecord question : packet.questions) {
            // Add answers from general records
            if (addReplyFromService(question, mGeneralRecords, null /* servicePtrRecord */,
                    null /* serviceSrvRecord */, null /* serviceTxtRecord */,
                    null /* hostname */,
                    replyUnicastEnabled, now, answerInfo, additionalAnswerInfo,
                    Collections.emptyList())) {
                replyUnicast &= question.isUnicastReplyRequested();
            }

            // Add answers from each service
            for (int i = 0; i < mServices.size(); i++) {
                final ServiceRegistration registration = mServices.valueAt(i);
                if (registration.exiting || registration.isProbing) continue;
                if (addReplyFromService(question, registration.allRecords, registration.ptrRecords,
                        registration.srvRecord, registration.txtRecord,
                        registration.serviceInfo.getHostname(),
                        replyUnicastEnabled, now,
                        answerInfo, additionalAnswerInfo, packet.answers)) {
                    replyUnicast &= question.isUnicastReplyRequested();
                    registration.repliedServiceCount++;
                    registration.sentPacketCount++;
                }
            }
        }

        // If any record was already in the answer section, remove it from the additional answer
        // section. This can typically happen when there are both queries for
        // SRV / TXT / A / AAAA and PTR (which can cause SRV / TXT / A / AAAA records being added
        // to the additional answer section).
        additionalAnswerInfo.removeAll(answerInfo);

        final List<MdnsRecord> additionalAnswerRecords =
                new ArrayList<>(additionalAnswerInfo.size());
        for (RecordInfo<?> info : additionalAnswerInfo) {
            // Different RecordInfos may contain the same record.
            // For example, when there are multiple services referring to the same custom host,
            // there are multiple RecordInfos containing the same address record.
            if (!additionalAnswerRecords.contains(info.record)) {
                additionalAnswerRecords.add(info.record);
            }
        }

        // RFC6762 6.1: negative responses
        // "On receipt of a question for a particular name, rrtype, and rrclass, for which a
        // responder does have one or more unique answers, the responder MAY also include an NSEC
        // record in the Additional Record Section indicating the nonexistence of other rrtypes
        // for that name and rrclass."
        addNsecRecordsForUniqueNames(additionalAnswerRecords,
                answerInfo.iterator(), additionalAnswerInfo.iterator());

        if (answerInfo.size() == 0 && additionalAnswerRecords.size() == 0) {
            // RFC6762 7.2. Multipacket Known-Answer Suppression
            // Sometimes a Multicast DNS querier will already have too many answers
            // to fit in the Known-Answer Section of its query packets. In this
            // case, it should issue a Multicast DNS query containing a question and
            // as many Known-Answer records as will fit.  It MUST then set the TC
            // (Truncated) bit in the header before sending the query.  It MUST
            // immediately follow the packet with another query packet containing no
            // questions and as many more Known-Answer records as will fit.  If
            // there are still too many records remaining to fit in the packet, it
            // again sets the TC bit and continues until all the Known-Answer
            // records have been sent.
            if (!isTruncatedKnownAnswerPacket(packet)) {
                return null;
            }
        }

        // Determine the send delay
        final long delayMs;
        if ((packet.flags & MdnsConstants.FLAG_TRUNCATED) != 0) {
            // RFC 6762 6.: 400-500ms delay if TC bit is set
            delayMs = 400L + mDelayGenerator.nextInt(100);
        } else if (packet.questions.size() > 1
                || CollectionUtils.any(answerInfo, a -> a.isSharedName)) {
            // 20-120ms if there may be responses from other hosts (not a fully owned
            // name) (RFC 6762 6.), or if there are multiple questions (6.3).
            // TODO: this should be 0 if this is a probe query ("can be distinguished from a
            // normal query by the fact that a probe query contains a proposed record in the
            // Authority Section that answers the question" in 6.), and the reply is for a fully
            // owned record.
            delayMs = 20L + mDelayGenerator.nextInt(100);
        } else {
            delayMs = 0L;
        }

        // Determine the send destination
        final InetSocketAddress dest;
        if (replyUnicast) {
            // As per RFC6762 5.4, "if the responder has not multicast that record recently (within
            // one quarter of its TTL), then the responder SHOULD instead multicast the response so
            // as to keep all the peer caches up to date": this SHOULD is not implemented to
            // minimize latency for queriers who have just started, so they did not receive previous
            // multicast responses. Unicast replies are faster as they do not need to wait for the
            // beacon interval on Wi-Fi.
            dest = src;
        } else if (src.getAddress() instanceof Inet4Address) {
            dest = IPV4_SOCKET_ADDR;
        } else {
            dest = IPV6_SOCKET_ADDR;
        }

        // Build the list of answer records from their RecordInfo
        final ArrayList<MdnsRecord> answerRecords = new ArrayList<>(answerInfo.size());
        for (RecordInfo<?> info : answerInfo) {
            // TODO: consider actual packet send delay after response aggregation
            info.lastSentTimeMs = now + delayMs;
            if (!replyUnicast) {
                info.lastAdvertisedTimeMs = info.lastSentTimeMs;
            }
            // Different RecordInfos may the contain the same record
            if (!answerRecords.contains(info.record)) {
                answerRecords.add(info.record);
            }
        }

        return new MdnsReplyInfo(answerRecords, additionalAnswerRecords, delayMs, dest, src,
                new ArrayList<>(packet.answers));
    }

    private boolean isKnownAnswer(MdnsRecord answer, @NonNull List<MdnsRecord> knownAnswerRecords) {
        for (MdnsRecord knownAnswer : knownAnswerRecords) {
            if (answer.equals(knownAnswer) && knownAnswer.getTtl() > (answer.getTtl() / 2)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Add answers and additional answers for a question, from a ServiceRegistration.
     */
    private boolean addReplyFromService(@NonNull MdnsRecord question,
            @NonNull List<RecordInfo<?>> serviceRecords,
            @Nullable List<RecordInfo<MdnsPointerRecord>> servicePtrRecords,
            @Nullable RecordInfo<MdnsServiceRecord> serviceSrvRecord,
            @Nullable RecordInfo<MdnsTextRecord> serviceTxtRecord,
            @Nullable String hostname,
            boolean replyUnicastEnabled, long now, @NonNull Set<RecordInfo<?>> answerInfo,
            @NonNull Set<RecordInfo<?>> additionalAnswerInfo,
            @NonNull List<MdnsRecord> knownAnswerRecords) {
        boolean hasDnsSdPtrRecordAnswer = false;
        boolean hasDnsSdSrvRecordAnswer = false;
        boolean hasFullyOwnedNameMatch = false;
        boolean hasKnownAnswer = false;

        final int answersStartSize = answerInfo.size();
        for (RecordInfo<?> info : serviceRecords) {

             /* RFC6762 6.: the record name must match the question name, the record rrtype
             must match the question qtype unless the qtype is "ANY" (255) or the rrtype is
             "CNAME" (5), and the record rrclass must match the question qclass unless the
             qclass is "ANY" (255) */
            if (!MdnsUtils.equalsDnsLabelIgnoreDnsCase(info.record.getName(), question.getName())) {
                continue;
            }
            hasFullyOwnedNameMatch |= !info.isSharedName;

            // The repository does not store CNAME records
            if (question.getType() != MdnsRecord.TYPE_ANY
                    && question.getType() != info.record.getType()) {
                continue;
            }
            if (question.getRecordClass() != MdnsRecord.CLASS_ANY
                    && question.getRecordClass() != info.record.getRecordClass()) {
                continue;
            }

            hasKnownAnswer = true;

            // RFC6762 7.1. Known-Answer Suppression:
            // A Multicast DNS responder MUST NOT answer a Multicast DNS query if
            // the answer it would give is already included in the Answer Section
            // with an RR TTL at least half the correct value.  If the RR TTL of the
            // answer as given in the Answer Section is less than half of the true
            // RR TTL as known by the Multicast DNS responder, the responder MUST
            // send an answer so as to update the querier's cache before the record
            // becomes in danger of expiration.
            if (mMdnsFeatureFlags.isKnownAnswerSuppressionEnabled()
                    && isKnownAnswer(info.record, knownAnswerRecords)) {
                continue;
            }

            hasDnsSdPtrRecordAnswer |= (servicePtrRecords != null
                    && CollectionUtils.any(servicePtrRecords, r -> info == r));
            hasDnsSdSrvRecordAnswer |= (info == serviceSrvRecord);

            // TODO: responses to probe queries should bypass this check and only ensure the
            // reply is sent 250ms after the last sent time (RFC 6762 p.15)
            if (!(replyUnicastEnabled && question.isUnicastReplyRequested())
                    && info.lastAdvertisedTimeMs > 0L
                    && now - info.lastAdvertisedTimeMs < MIN_MULTICAST_REPLY_INTERVAL_MS) {
                continue;
            }

            answerInfo.add(info);
        }

        // RFC6762 6.1:
        // "Any time a responder receives a query for a name for which it has verified exclusive
        // ownership, for a type for which that name has no records, the responder MUST [...]
        // respond asserting the nonexistence of that record"
        if (hasFullyOwnedNameMatch && !hasKnownAnswer) {
            MdnsNsecRecord nsecRecord = new MdnsNsecRecord(
                    question.getName(),
                    0L /* receiptTimeMillis */,
                    true /* cacheFlush */,
                    // TODO: RFC6762 6.1: "In general, the TTL given for an NSEC record SHOULD
                    // be the same as the TTL that the record would have had, had it existed."
                    DEFAULT_NAME_RECORDS_TTL_MILLIS,
                    question.getName(),
                    new int[] { question.getType() });
            additionalAnswerInfo.add(
                    new RecordInfo<>(null /* serviceInfo */, nsecRecord, false /* isSharedName */));
        }

        // No more records to add if no answer
        if (answerInfo.size() == answersStartSize) return false;

        // RFC6763 12.1: if including PTR record, include the SRV and TXT records it names
        if (hasDnsSdPtrRecordAnswer) {
            if (serviceTxtRecord != null) {
                additionalAnswerInfo.add(serviceTxtRecord);
            }
            if (serviceSrvRecord != null) {
                additionalAnswerInfo.add(serviceSrvRecord);
            }
        }

        // RFC6763 12.1&.2: if including PTR or SRV record, include the address records it names
        if (hasDnsSdPtrRecordAnswer || hasDnsSdSrvRecordAnswer) {
            additionalAnswerInfo.addAll(getInetAddressRecordsForHostname(hostname));
        }
        return true;
    }

    /**
     * Add NSEC records indicating that the response records are unique.
     *
     * Following RFC6762 6.1:
     * "On receipt of a question for a particular name, rrtype, and rrclass, for which a responder
     * does have one or more unique answers, the responder MAY also include an NSEC record in the
     * Additional Record Section indicating the nonexistence of other rrtypes for that name and
     * rrclass."
     * @param destinationList List to add the NSEC records to.
     * @param answerRecords Lists of answered records based on which to add NSEC records (typically
     *                      answer and additionalAnswer sections)
     */
    @SafeVarargs
    private void addNsecRecordsForUniqueNames(
            List<MdnsRecord> destinationList,
            Iterator<RecordInfo<?>>... answerRecords) {
        // Group unique records by name. Use a TreeMap with comparator as arrays don't implement
        // equals / hashCode.
        final Map<String[], List<MdnsRecord>> nsecByName = new TreeMap<>(Arrays::compare);
        // But keep the list of names in added order, otherwise records would be sorted in
        // alphabetical order instead of the order of the original records, which would look like
        // inconsistent behavior depending on service name.
        final List<String[]> namesInAddedOrder = new ArrayList<>();
        for (Iterator<RecordInfo<?>> answers : answerRecords) {
            addNonSharedRecordsToMap(answers, nsecByName, namesInAddedOrder);
        }

        for (String[] nsecName : namesInAddedOrder) {
            final List<MdnsRecord> entryRecords = nsecByName.get(nsecName);

            // Add NSEC records only when the answers include all unique records of this name
            if (entryRecords.size() != countUniqueRecords(nsecName)) {
                continue;
            }

            long minTtl = Long.MAX_VALUE;
            final Set<Integer> types = new ArraySet<>(entryRecords.size());
            for (MdnsRecord record : entryRecords) {
                if (minTtl > record.getTtl()) minTtl = record.getTtl();
                types.add(record.getType());
            }

            destinationList.add(new MdnsNsecRecord(
                    nsecName,
                    0L /* receiptTimeMillis */,
                    true /* cacheFlush */,
                    minTtl,
                    nsecName,
                    CollectionUtils.toIntArray(types)));
        }
    }

    /** Returns the number of unique records on this device for a given {@code name}. */
    private int countUniqueRecords(String[] name) {
        int cnt = countUniqueRecords(mGeneralRecords, name);

        for (int i = 0; i < mServices.size(); i++) {
            final ServiceRegistration registration = mServices.valueAt(i);
            cnt += countUniqueRecords(registration.allRecords, name);
        }
        return cnt;
    }

    private static int countUniqueRecords(List<RecordInfo<?>> records, String[] name) {
        int cnt = 0;
        for (RecordInfo<?> record : records) {
            if (!record.isSharedName && Arrays.equals(name, record.record.getName())) {
                cnt++;
            }
        }
        return cnt;
    }

    /**
     * Add non-shared records to a map listing them by record name, and to a list of names that
     * remembers the adding order.
     *
     * In the destination map records are grouped by name; so the map has one key per record name,
     * and the values are the lists of different records that share the same name.
     * @param records Records to scan.
     * @param dest Map to add the records to.
     * @param namesInAddedOrder List of names to add the names in order, keeping the first
     *                          occurrence of each name.
     */
    private static void addNonSharedRecordsToMap(
            Iterator<RecordInfo<?>> records,
            Map<String[], List<MdnsRecord>> dest,
            @Nullable List<String[]> namesInAddedOrder) {
        while (records.hasNext()) {
            final RecordInfo<?> record = records.next();
            if (record.isSharedName || record.record instanceof MdnsNsecRecord) continue;
            final List<MdnsRecord> recordsForName = dest.computeIfAbsent(record.record.name,
                    key -> {
                        namesInAddedOrder.add(key);
                        return new ArrayList<>();
                    });
            recordsForName.add(record.record);
        }
    }

    /**
     * Called to indicate that probing succeeded for a service.
     * @param probeSuccessInfo The successful probing info.
     * @return The {@link MdnsAnnouncer.AnnouncementInfo} to send, now that probing has succeeded.
     */
    public MdnsAnnouncer.AnnouncementInfo onProbingSucceeded(
            MdnsProber.ProbingInfo probeSuccessInfo)
            throws IOException {

        int serviceId = probeSuccessInfo.getServiceId();
        final ServiceRegistration registration = mServices.get(serviceId);
        if (registration == null) {
            throw new IOException("Service is not registered: " + serviceId);
        }
        registration.setProbing(false);

        final Set<MdnsRecord> answersSet = new LinkedHashSet<>();
        final ArrayList<MdnsRecord> additionalAnswers = new ArrayList<>();

        // When using default host, add interface address records from general records
        if (TextUtils.isEmpty(registration.serviceInfo.getHostname())) {
            for (RecordInfo<?> record : mGeneralRecords) {
                answersSet.add(record.record);
            }
        } else {
            // TODO: b/321617573 - include PTR records for addresses
            // The custom host may have more addresses in other registrations
            forEachActiveServiceRegistrationWithHostname(
                    registration.serviceInfo.getHostname(),
                    (id, otherRegistration) -> {
                        if (otherRegistration.isProbing) {
                            return;
                        }
                        for (RecordInfo<?> addressRecordInfo : otherRegistration.addressRecords) {
                            answersSet.add(addressRecordInfo.record);
                        }
                    });
        }

        // All service records
        for (RecordInfo<?> info : registration.allRecords) {
            answersSet.add(info.record);
        }

        addNsecRecordsForUniqueNames(additionalAnswers,
                mGeneralRecords.iterator(), registration.allRecords.iterator());

        return new MdnsAnnouncer.AnnouncementInfo(
                probeSuccessInfo.getServiceId(), new ArrayList<>(answersSet), additionalAnswers);
    }

    /**
     * Gets the offload MdnsPacket.
     * @param serviceId The serviceId.
     * @return The offload {@link MdnsPacket} that contains PTR/SRV/TXT/A/AAAA records.
     */
    public MdnsPacket getOffloadPacket(int serviceId) throws IllegalArgumentException {
        final ServiceRegistration registration = mServices.get(serviceId);
        if (registration == null) throw new IllegalArgumentException(
                "Service is not registered: " + serviceId);

        final ArrayList<MdnsRecord> answers = new ArrayList<>();

        // Adds all PTR, SRV, TXT, A/AAAA records.
        for (RecordInfo<MdnsPointerRecord> ptrRecord : registration.ptrRecords) {
            answers.add(ptrRecord.record);
        }
        if (registration.srvRecord != null) {
            answers.add(registration.srvRecord.record);
        }
        if (registration.txtRecord != null) {
            answers.add(registration.txtRecord.record);
        }
        // TODO: Support custom host. It currently only supports default host.
        for (RecordInfo<?> record : mGeneralRecords) {
            if (record.record instanceof MdnsInetAddressRecord) {
                answers.add(record.record);
            }
        }

        final int flags = 0x8400; // Response, authoritative (rfc6762 18.4)
        return new MdnsPacket(flags,
                Collections.emptyList() /* questions */,
                answers,
                Collections.emptyList() /* authorityRecords */,
                Collections.emptyList() /* additionalRecords */);
    }

    /** Check if the record is in any service registration */
    private boolean hasInetAddressRecord(@NonNull MdnsInetAddressRecord record) {
        for (int i = 0; i < mServices.size(); i++) {
            final ServiceRegistration registration = mServices.valueAt(i);
            if (registration.exiting) continue;

            for (RecordInfo<MdnsInetAddressRecord> localRecord : registration.addressRecords) {
                if (Objects.equals(localRecord.record, record)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Get the service IDs of services conflicting with a received packet.
     *
     * <p>It returns a Map of service ID => conflict type. Conflict type is a bitmap telling which
     * part of the service is conflicting. See {@link MdnsInterfaceAdvertiser#CONFLICT_SERVICE} and
     * {@link MdnsInterfaceAdvertiser#CONFLICT_HOST}.
     */
    public Map<Integer, Integer> getConflictingServices(MdnsPacket packet) {
        // Avoid allocating a new set for each incoming packet: use an empty set by default.
        Map<Integer, Integer> conflicting = Collections.emptyMap();
        for (MdnsRecord record : packet.answers) {
            for (int i = 0; i < mServices.size(); i++) {
                final ServiceRegistration registration = mServices.valueAt(i);
                if (registration.exiting) continue;

                int conflictType = 0;

                if (conflictForService(record, registration)) {
                    conflictType |= CONFLICT_SERVICE;
                }

                if (conflictForHost(record, registration)) {
                    conflictType |= CONFLICT_HOST;
                }

                if (conflictType != 0) {
                    if (conflicting.isEmpty()) {
                        // Conflict was found: use a mutable set
                        conflicting = new ArrayMap<>();
                    }
                    final int serviceId = mServices.keyAt(i);
                    conflicting.put(serviceId, conflictType);
                }
            }
        }

        return conflicting;
    }


    private static boolean conflictForService(
            @NonNull MdnsRecord record, @NonNull ServiceRegistration registration) {
        if (registration.srvRecord == null) {
            return false;
        }

        final RecordInfo<MdnsServiceRecord> srvRecord = registration.srvRecord;
        if (!MdnsUtils.equalsDnsLabelIgnoreDnsCase(record.getName(), srvRecord.record.getName())) {
            return false;
        }

        // As per RFC6762 9., it's fine if the "conflict" is an identical record with same
        // data.
        if (record instanceof MdnsServiceRecord) {
            final MdnsServiceRecord local = srvRecord.record;
            final MdnsServiceRecord other = (MdnsServiceRecord) record;
            // Note "equals" does not consider TTL or receipt time, as intended here
            if (Objects.equals(local, other)) {
                return false;
            }
        }

        if (record instanceof MdnsTextRecord) {
            final MdnsTextRecord local = registration.txtRecord.record;
            final MdnsTextRecord other = (MdnsTextRecord) record;
            if (Objects.equals(local, other)) {
                return false;
            }
        }
        return true;
    }

    private boolean conflictForHost(
            @NonNull MdnsRecord record, @NonNull ServiceRegistration registration) {
        // Only custom hosts are checked. When using the default host, the hostname is derived from
        // a UUID and it's supposed to be unique.
        if (registration.serviceInfo.getHostname() == null) {
            return false;
        }

        // The record's name cannot be registered by NsdManager so it's not a conflict.
        if (record.getName().length != 2 || !record.getName()[1].equals(LOCAL_TLD)) {
            return false;
        }

        // Different names. There won't be a conflict.
        if (!MdnsUtils.equalsIgnoreDnsCase(
                record.getName()[0], registration.serviceInfo.getHostname())) {
            return false;
        }

        // If this registration has any address record and there's no identical record in the
        // repository, it's a conflict. There will be no conflict if no registration has addresses
        // for that hostname.
        if (record instanceof MdnsInetAddressRecord) {
            if (!registration.addressRecords.isEmpty()) {
                return !hasInetAddressRecord((MdnsInetAddressRecord) record);
            }
        }

        return false;
    }

    private List<RecordInfo<MdnsInetAddressRecord>> getInetAddressRecordsForHostname(
            @Nullable String hostname) {
        List<RecordInfo<MdnsInetAddressRecord>> records = new ArrayList<>();
        if (TextUtils.isEmpty(hostname)) {
            forEachAddressRecord(mGeneralRecords, records::add);
        } else {
            forEachActiveServiceRegistrationWithHostname(
                    hostname,
                    (id, service) -> {
                        if (service.isProbing) return;
                        records.addAll(service.addressRecords);
                    });
        }
        return records;
    }

    private List<MdnsInetAddressRecord> makeProbingInetAddressRecords(
            @NonNull NsdServiceInfo serviceInfo) {
        final List<MdnsInetAddressRecord> records = new ArrayList<>();
        if (TextUtils.isEmpty(serviceInfo.getHostname())) {
            if (mMdnsFeatureFlags.mIncludeInetAddressRecordsInProbing) {
                forEachAddressRecord(mGeneralRecords, r -> records.add(r.record));
            }
        } else {
            forEachActiveServiceRegistrationWithHostname(
                    serviceInfo.getHostname(),
                    (id, service) -> {
                        for (RecordInfo<MdnsInetAddressRecord> recordInfo :
                                service.addressRecords) {
                            records.add(recordInfo.record);
                        }
                    });
        }
        return records;
    }

    private static void forEachAddressRecord(
            List<RecordInfo<?>> records, Consumer<RecordInfo<MdnsInetAddressRecord>> consumer) {
        for (RecordInfo<?> record : records) {
            if (record.record instanceof MdnsInetAddressRecord) {
                consumer.accept((RecordInfo<MdnsInetAddressRecord>) record);
            }
        }
    }

    private void forEachActiveServiceRegistrationWithHostname(
            @NonNull String hostname, BiConsumer<Integer, ServiceRegistration> consumer) {
        for (int i = 0; i < mServices.size(); ++i) {
            int id = mServices.keyAt(i);
            ServiceRegistration service = mServices.valueAt(i);
            if (service.exiting) continue;
            if (MdnsUtils.equalsIgnoreDnsCase(service.serviceInfo.getHostname(), hostname)) {
                consumer.accept(id, service);
            }
        }
    }

    /**
     * (Re)set a service to the probing state.
     * @return The {@link MdnsProber.ProbingInfo} to send for probing.
     */
    @Nullable
    public MdnsProber.ProbingInfo setServiceProbing(int serviceId) {
        final ServiceRegistration registration = mServices.get(serviceId);
        if (registration == null) return null;

        registration.setProbing(true);

        return makeProbingInfo(serviceId, registration);
    }

    /**
     * Indicates whether a given service is in probing state.
     */
    public boolean isProbing(int serviceId) {
        final ServiceRegistration registration = mServices.get(serviceId);
        if (registration == null) return false;

        return registration.isProbing;
    }

    /**
     * Return whether the repository has an active (non-exiting) service for the given ID.
     */
    public boolean hasActiveService(int serviceId) {
        final ServiceRegistration registration = mServices.get(serviceId);
        if (registration == null) return false;

        return !registration.exiting;
    }

    /**
     * Rename a service to the newly provided info, following a conflict.
     *
     * If the specified service does not exist, this returns null.
     */
    @Nullable
    public MdnsProber.ProbingInfo renameServiceForConflict(int serviceId, NsdServiceInfo newInfo) {
        final ServiceRegistration existing = mServices.get(serviceId);
        if (existing == null) return null;

        final ServiceRegistration newService = new ServiceRegistration(mDeviceHostname, newInfo,
                existing.repliedServiceCount, existing.sentPacketCount, existing.ttl);
        mServices.put(serviceId, newService);
        return makeProbingInfo(serviceId, newService);
    }

    /**
     * Called when {@link MdnsAdvertiser} sent an advertisement for the given service.
     */
    public void onAdvertisementSent(int serviceId, int sentPacketCount) {
        final ServiceRegistration registration = mServices.get(serviceId);
        if (registration == null) return;

        final long now = SystemClock.elapsedRealtime();
        for (RecordInfo<?> record : registration.allRecords) {
            record.lastSentTimeMs = now;
            record.lastAdvertisedTimeMs = now;
        }
        registration.sentPacketCount += sentPacketCount;
    }

    /**
     * Called when {@link MdnsAdvertiser} sent a probing for the given service.
     */
    public void onProbingSent(int serviceId, int sentPacketCount) {
        final ServiceRegistration registration = mServices.get(serviceId);
        if (registration == null) return;
        registration.sentPacketCount += sentPacketCount;
    }


    /**
     * Compute:
     * 2001:db8::1 --> 1.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.8.B.D.0.1.0.0.2.ip6.arpa
     *
     * Or:
     * 192.0.2.123 --> 123.2.0.192.in-addr.arpa
     */
    @VisibleForTesting
    public static String[] getReverseDnsAddress(@NonNull InetAddress addr) {
        // xxx.xxx.xxx.xxx.in-addr.arpa (up to 28 characters)
        // or 32 hex characters separated by dots + .ip6.arpa
        final byte[] addrBytes = addr.getAddress();
        final List<String> out = new ArrayList<>();
        if (addr instanceof Inet4Address) {
            for (int i = addrBytes.length - 1; i >= 0; i--) {
                out.add(String.valueOf(Byte.toUnsignedInt(addrBytes[i])));
            }
            out.add("in-addr");
        } else {
            final String hexAddr = HexDump.toHexString(addrBytes);

            for (int i = hexAddr.length() - 1; i >= 0; i--) {
                out.add(String.valueOf(hexAddr.charAt(i)));
            }
            out.add("ip6");
        }
        out.add("arpa");

        return out.toArray(new String[0]);
    }

    private static String[] splitFullyQualifiedName(
            @NonNull NsdServiceInfo info, @NonNull String[] serviceType) {
        final String[] split = new String[serviceType.length + 1];
        split[0] = info.getServiceName();
        System.arraycopy(serviceType, 0, split, 1, serviceType.length);

        return split;
    }

    private static String[] splitServiceType(@NonNull NsdServiceInfo info) {
        // String.split(pattern, 0) removes trailing empty strings, which would appear when
        // splitting "domain.name." (with a dot a the end), so this is what is needed here.
        final String[] split = info.getServiceType().split("\\.", 0);
        final String[] type = new String[split.length + 1];
        System.arraycopy(split, 0, type, 0, split.length);
        type[split.length] = LOCAL_TLD;

        return type;
    }
}
