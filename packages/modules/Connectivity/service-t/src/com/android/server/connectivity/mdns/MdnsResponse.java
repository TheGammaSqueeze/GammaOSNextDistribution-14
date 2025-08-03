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
import android.net.Network;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.connectivity.mdns.util.MdnsUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/** An mDNS response. */
public class MdnsResponse {
    public static final long EXPIRATION_NEVER = Long.MAX_VALUE;
    private final List<MdnsRecord> records;
    private final List<MdnsPointerRecord> pointerRecords;
    private MdnsServiceRecord serviceRecord;
    private MdnsTextRecord textRecord;
    @NonNull private List<MdnsInetAddressRecord> inet4AddressRecords;
    @NonNull private List<MdnsInetAddressRecord> inet6AddressRecords;
    private long lastUpdateTime;
    private final int interfaceIndex;
    @Nullable private final Network network;
    @NonNull private final String[] serviceName;

    /** Constructs a new, empty response. */
    public MdnsResponse(long now, @NonNull String[] serviceName, int interfaceIndex,
            @Nullable Network network) {
        lastUpdateTime = now;
        records = new LinkedList<>();
        pointerRecords = new LinkedList<>();
        inet4AddressRecords = new ArrayList<>();
        inet6AddressRecords = new ArrayList<>();
        this.interfaceIndex = interfaceIndex;
        this.network = network;
        this.serviceName = serviceName;
    }

    public MdnsResponse(@NonNull MdnsResponse base) {
        records = new ArrayList<>(base.records);
        pointerRecords = new ArrayList<>(base.pointerRecords);
        serviceRecord = base.serviceRecord;
        textRecord = base.textRecord;
        inet4AddressRecords = new ArrayList<>(base.inet4AddressRecords);
        inet6AddressRecords = new ArrayList<>(base.inet6AddressRecords);
        lastUpdateTime = base.lastUpdateTime;
        serviceName = base.serviceName;
        interfaceIndex = base.interfaceIndex;
        network = base.network;
    }

    /**
     * Compare records for equality, including their TTL.
     *
     * MdnsRecord#equals ignores TTL and receiptTimeMillis, but methods in this class need to update
     * records when the TTL changes (especially for goodbye announcements).
     */
    private boolean recordsAreSame(MdnsRecord a, MdnsRecord b) {
        if (!Objects.equals(a, b)) return false;
        return a == null || a.getTtl() == b.getTtl();
    }

    private <T extends MdnsRecord> boolean addOrReplaceRecord(@NonNull T record,
            @NonNull List<T> recordsList) {
        final int existing = recordsList.indexOf(record);
        boolean isSame = false;
        if (existing >= 0) {
            isSame = recordsAreSame(record, recordsList.get(existing));
            final MdnsRecord existedRecord = recordsList.remove(existing);
            records.remove(existedRecord);
        }
        recordsList.add(record);
        records.add(record);
        return !isSame;
    }

    /**
     * @return True if this response contains an identical (original TTL included) record.
     */
    public boolean hasIdenticalRecord(@NonNull MdnsRecord record) {
        final int existing = records.indexOf(record);
        return existing >= 0 && recordsAreSame(record, records.get(existing));
    }

    /**
     * Adds a pointer record.
     *
     * @return <code>true</code> if the record was added, or <code>false</code> if a matching
     * pointer record is already present in the response with the same TTL.
     */
    public synchronized boolean addPointerRecord(MdnsPointerRecord pointerRecord) {
        if (!MdnsUtils.equalsDnsLabelIgnoreDnsCase(serviceName, pointerRecord.getPointer())) {
            throw new IllegalArgumentException(
                    "Pointer records for different service names cannot be added");
        }
        return addOrReplaceRecord(pointerRecord, pointerRecords);
    }

    /** Gets the pointer records. */
    public synchronized List<MdnsPointerRecord> getPointerRecords() {
        // Returns a shallow copy.
        return new LinkedList<>(pointerRecords);
    }

    public synchronized boolean hasPointerRecords() {
        return !pointerRecords.isEmpty();
    }

    @VisibleForTesting
    synchronized void clearPointerRecords() {
        pointerRecords.clear();
    }

    public synchronized boolean hasSubtypes() {
        for (MdnsPointerRecord pointerRecord : pointerRecords) {
            if (pointerRecord.hasSubtype()) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    public synchronized List<String> getSubtypes() {
        List<String> subtypes = null;
        for (MdnsPointerRecord pointerRecord : pointerRecords) {
            String pointerRecordSubtype = pointerRecord.getSubtype();
            if (pointerRecordSubtype != null) {
                if (subtypes == null) {
                    subtypes = new LinkedList<>();
                }
                subtypes.add(pointerRecordSubtype);
            }
        }

        return subtypes;
    }

    @VisibleForTesting
    public synchronized void removeSubtypes() {
        Iterator<MdnsPointerRecord> iter = pointerRecords.iterator();
        while (iter.hasNext()) {
            MdnsPointerRecord pointerRecord = iter.next();
            if (pointerRecord.hasSubtype()) {
                iter.remove();
            }
        }
    }

    /** Sets the service record. */
    public synchronized boolean setServiceRecord(MdnsServiceRecord serviceRecord) {
        boolean isSame = recordsAreSame(this.serviceRecord, serviceRecord);
        if (this.serviceRecord != null) {
            records.remove(this.serviceRecord);
        }
        this.serviceRecord = serviceRecord;
        if (this.serviceRecord != null) {
            records.add(this.serviceRecord);
        }
        return !isSame;
    }

    /** Gets the service record. */
    public synchronized MdnsServiceRecord getServiceRecord() {
        return serviceRecord;
    }

    public synchronized boolean hasServiceRecord() {
        return serviceRecord != null;
    }

    /** Sets the text record. */
    public synchronized boolean setTextRecord(MdnsTextRecord textRecord) {
        boolean isSame = recordsAreSame(this.textRecord, textRecord);
        if (this.textRecord != null) {
            records.remove(this.textRecord);
        }
        this.textRecord = textRecord;
        if (this.textRecord != null) {
            records.add(this.textRecord);
        }
        return !isSame;
    }

    /** Gets the text record. */
    public synchronized MdnsTextRecord getTextRecord() {
        return textRecord;
    }

    public synchronized boolean hasTextRecord() {
        return textRecord != null;
    }

    /** Add the IPv4 address record. */
    public synchronized boolean addInet4AddressRecord(
            @NonNull MdnsInetAddressRecord newInet4AddressRecord) {
        return addOrReplaceRecord(newInet4AddressRecord, inet4AddressRecords);
    }

    /** Gets the IPv4 address records. */
    @NonNull
    public synchronized List<MdnsInetAddressRecord> getInet4AddressRecords() {
        return Collections.unmodifiableList(inet4AddressRecords);
    }

    /** Return the first IPv4 address record or null if no record. */
    @Nullable
    public synchronized MdnsInetAddressRecord getInet4AddressRecord() {
        return inet4AddressRecords.isEmpty() ? null : inet4AddressRecords.get(0);
    }

    /** Check whether response has IPv4 address record */
    public synchronized boolean hasInet4AddressRecord() {
        return !inet4AddressRecords.isEmpty();
    }

    /** Clear all IPv4 address records */
    synchronized void clearInet4AddressRecords() {
        for (MdnsInetAddressRecord record : inet4AddressRecords) {
            records.remove(record);
        }
        inet4AddressRecords.clear();
    }

    /** Sets the IPv6 address records. */
    public synchronized boolean addInet6AddressRecord(
            @NonNull MdnsInetAddressRecord newInet6AddressRecord) {
        return addOrReplaceRecord(newInet6AddressRecord, inet6AddressRecords);
    }

    /**
     * Returns the index of the network interface at which this response was received. Can be set to
     * {@link MdnsSocket#INTERFACE_INDEX_UNSPECIFIED} if unset.
     */
    public int getInterfaceIndex() {
        return interfaceIndex;
    }

    /**
     * Returns the network at which this response was received, or null if the network is unknown.
     */
    @Nullable
    public Network getNetwork() {
        return network;
    }

    /** Gets all IPv6 address records. */
    public synchronized List<MdnsInetAddressRecord> getInet6AddressRecords() {
        return Collections.unmodifiableList(inet6AddressRecords);
    }

    /** Return the first IPv6 address record or null if no record. */
    @Nullable
    public synchronized MdnsInetAddressRecord getInet6AddressRecord() {
        return inet6AddressRecords.isEmpty() ? null : inet6AddressRecords.get(0);
    }

    /** Check whether response has IPv6 address record */
    public synchronized boolean hasInet6AddressRecord() {
        return !inet6AddressRecords.isEmpty();
    }

    /** Clear all IPv6 address records */
    synchronized void clearInet6AddressRecords() {
        for (MdnsInetAddressRecord record : inet6AddressRecords) {
            records.remove(record);
        }
        inet6AddressRecords.clear();
    }

    /** Gets all of the records. */
    public synchronized List<MdnsRecord> getRecords() {
        return new LinkedList<>(records);
    }

    /**
     * Drop address records if they are for a hostname that does not match the service record.
     *
     * @return True if the records were dropped.
     */
    public synchronized boolean dropUnmatchedAddressRecords() {
        if (this.serviceRecord == null) return false;
        boolean dropAddressRecords = false;

        for (MdnsInetAddressRecord inetAddressRecord : getInet4AddressRecords()) {
            if (!MdnsUtils.equalsDnsLabelIgnoreDnsCase(
                    this.serviceRecord.getServiceHost(), inetAddressRecord.getName())) {
                dropAddressRecords = true;
            }
        }
        for (MdnsInetAddressRecord inetAddressRecord : getInet6AddressRecords()) {
            if (!MdnsUtils.equalsDnsLabelIgnoreDnsCase(
                    this.serviceRecord.getServiceHost(), inetAddressRecord.getName())) {
                dropAddressRecords = true;
            }
        }

        if (dropAddressRecords) {
            clearInet4AddressRecords();
            clearInet6AddressRecords();
            return true;
        }
        return false;
    }

    /**
     * Tests if the response is complete. A response is considered complete if it contains SRV,
     * TXT, and A (for IPv4) or AAAA (for IPv6) records. The service type->name mapping is always
     * known when constructing a MdnsResponse, so this may return true when there is no PTR record.
     */
    public synchronized boolean isComplete() {
        return (serviceRecord != null)
                && (textRecord != null)
                && (!inet4AddressRecords.isEmpty() || !inet6AddressRecords.isEmpty());
    }

    /**
     * Returns the key for this response. The key uniquely identifies the response by its service
     * name.
     */
    @Nullable
    public String getServiceInstanceName() {
        return serviceName.length > 0 ? serviceName[0] : null;
    }

    @NonNull
    public String[] getServiceName() {
        return serviceName;
    }

    /** Get the min remaining ttl time from received records */
    public long getMinRemainingTtl(long now) {
        long minRemainingTtl = EXPIRATION_NEVER;
        // TODO: Check other records(A, AAAA, TXT) ttl time.
        if (!hasServiceRecord()) {
            return EXPIRATION_NEVER;
        }
        // Check ttl time.
        long remainingTtl = serviceRecord.getRemainingTTL(now);
        if (remainingTtl < minRemainingTtl) {
            minRemainingTtl = remainingTtl;
        }
        return minRemainingTtl;
    }

    /**
     * Tests if this response is a goodbye message. This will be true if a service record is present
     * and any of the records have a TTL of 0.
     */
    public synchronized boolean isGoodbye() {
        if (getServiceInstanceName() != null) {
            for (MdnsRecord record : records) {
                // Expiring PTR records with subtypes just signal a change in known supported
                // criteria, not the device itself going offline, so ignore those.
                if ((record instanceof MdnsPointerRecord)
                        && ((MdnsPointerRecord) record).hasSubtype()) {
                    continue;
                }

                if (record.getTtl() == 0) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Writes the response to a packet.
     *
     * @param writer The writer to use.
     * @param now    The current time. This is used to write updated TTLs that reflect the remaining
     *               TTL
     *               since the response was received.
     * @return The number of records that were written.
     * @throws IOException If an error occurred while writing (typically indicating overflow).
     */
    public synchronized int write(MdnsPacketWriter writer, long now) throws IOException {
        int count = 0;
        for (MdnsPointerRecord pointerRecord : pointerRecords) {
            pointerRecord.write(writer, now);
            ++count;
        }

        if (serviceRecord != null) {
            serviceRecord.write(writer, now);
            ++count;
        }

        if (textRecord != null) {
            textRecord.write(writer, now);
            ++count;
        }

        for (MdnsInetAddressRecord inetAddressRecord : inet4AddressRecords) {
            inetAddressRecord.write(writer, now);
            ++count;
        }

        for (MdnsInetAddressRecord inetAddressRecord : inet6AddressRecords) {
            inetAddressRecord.write(writer, now);
            ++count;
        }

        return count;
    }
}
