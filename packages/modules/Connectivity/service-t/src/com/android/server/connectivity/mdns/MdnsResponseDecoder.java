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
import android.util.ArrayMap;
import android.util.Pair;

import com.android.server.connectivity.mdns.util.MdnsUtils;

import java.io.EOFException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/** A class that decodes mDNS responses from UDP packets. */
public class MdnsResponseDecoder {
    public static final int SUCCESS = 0;
    private static final String TAG = "MdnsResponseDecoder";
    private final boolean allowMultipleSrvRecordsPerHost =
            MdnsConfigs.allowMultipleSrvRecordsPerHost();
    @Nullable private final String[] serviceType;
    private final MdnsUtils.Clock clock;

    /** Constructs a new decoder that will extract responses for the given service type. */
    public MdnsResponseDecoder(@NonNull MdnsUtils.Clock clock, @Nullable String[] serviceType) {
        this.clock = clock;
        this.serviceType = serviceType;
    }

    private static MdnsResponse findResponseWithPointer(
            List<MdnsResponse> responses, String[] pointer) {
        if (responses != null) {
            for (MdnsResponse response : responses) {
                if (MdnsUtils.equalsDnsLabelIgnoreDnsCase(response.getServiceName(), pointer)) {
                    return response;
                }
            }
        }
        return null;
    }

    private static MdnsResponse findResponseWithHostName(
            List<MdnsResponse> responses, String[] hostName) {
        if (responses != null) {
            for (MdnsResponse response : responses) {
                MdnsServiceRecord serviceRecord = response.getServiceRecord();
                if (serviceRecord == null) {
                    continue;
                }
                if (MdnsUtils.equalsDnsLabelIgnoreDnsCase(serviceRecord.getServiceHost(),
                        hostName)) {
                    return response;
                }
            }
        }
        return null;
    }

    /**
     * Decodes all mDNS responses for the desired service type from a packet. The class does not
     * check the responses for completeness; the caller should do that.
     *
     * @param recvbuf The received data buffer to read from.
     * @param length The length of received data buffer.
     * @return A decoded {@link MdnsPacket}.
     * @throws MdnsPacket.ParseException if a response packet could not be parsed.
     */
    @NonNull
    public static MdnsPacket parseResponse(@NonNull byte[] recvbuf, int length,
            @NonNull MdnsFeatureFlags mdnsFeatureFlags) throws MdnsPacket.ParseException {
        final MdnsPacketReader reader = new MdnsPacketReader(recvbuf, length, mdnsFeatureFlags);

        final MdnsPacket mdnsPacket;
        try {
            final int transactionId = reader.readUInt16();
            int flags = reader.readUInt16();
            if ((flags & MdnsConstants.FLAGS_RESPONSE_MASK) != MdnsConstants.FLAGS_RESPONSE) {
                throw new MdnsPacket.ParseException(
                        MdnsResponseErrorCode.ERROR_NOT_RESPONSE_MESSAGE, "Not a response", null);
            }

            mdnsPacket = MdnsPacket.parseRecordsSection(reader, flags, transactionId);
            if (mdnsPacket.answers.size() < 1) {
                throw new MdnsPacket.ParseException(
                        MdnsResponseErrorCode.ERROR_NO_ANSWERS, "Response has no answers",
                        null);
            }
            return mdnsPacket;
        } catch (EOFException e) {
            throw new MdnsPacket.ParseException(MdnsResponseErrorCode.ERROR_END_OF_FILE,
                    "Reached the end of the mDNS response unexpectedly.", e);
        }
    }

    /**
     * Augments a list of {@link MdnsResponse} with records from a packet. The class does not check
     * the resulting responses for completeness; the caller should do that.
     *
     * @param mdnsPacket the response packet with the new records
     * @param existingResponses list of existing responses. Will not be modified.
     * @param interfaceIndex the network interface index (or
     * {@link MdnsSocket#INTERFACE_INDEX_UNSPECIFIED} if not known) at which the packet was received
     * @param network the network at which the packet was received, or null if it is unknown.
     * @return The pair of 1) set of response instances that were modified or newly added. *not*
     *                      including those which records were only updated with newer receive
     *                      timestamps.
     *                     2) A copy of the original responses with some of them have records
     *                     update or only contains receive time updated.
     */
    public Pair<Set<MdnsResponse>, ArrayList<MdnsResponse>> augmentResponses(
            @NonNull MdnsPacket mdnsPacket,
            @NonNull Collection<MdnsResponse> existingResponses, int interfaceIndex,
            @Nullable Network network) {
        final ArrayList<MdnsRecord> records = new ArrayList<>(
                mdnsPacket.questions.size() + mdnsPacket.answers.size()
                        + mdnsPacket.authorityRecords.size() + mdnsPacket.additionalRecords.size());
        records.addAll(mdnsPacket.answers);
        records.addAll(mdnsPacket.authorityRecords);
        records.addAll(mdnsPacket.additionalRecords);

        final Set<MdnsResponse> modified = MdnsUtils.newSet();
        final ArrayList<MdnsResponse> responses = new ArrayList<>(existingResponses.size());
        final ArrayMap<MdnsResponse, MdnsResponse> augmentedToOriginal = new ArrayMap<>();
        for (MdnsResponse existing : existingResponses) {
            final MdnsResponse copy = new MdnsResponse(existing);
            responses.add(copy);
            augmentedToOriginal.put(copy, existing);
        }
        // The response records are structured in a hierarchy, where some records reference
        // others, as follows:
        //
        //        PTR
        //        / \
        //       /   \
        //      TXT  SRV
        //           / \
        //          /   \
        //         A   AAAA
        //
        // But the order in which these records appear in the response packet is completely
        // arbitrary. This means that we need to rescan the record list to construct each level of
        // this hierarchy.
        //
        // PTR: service type -> service instance name
        //
        // SRV: service instance name -> host name (priority, weight)
        //
        // TXT: service instance name -> machine readable txt entries.
        //
        // A: host name -> IP address

        // Loop 1: find PTR records, which identify distinct service instances.
        long now = clock.elapsedRealtime();
        for (MdnsRecord record : records) {
            if (record instanceof MdnsPointerRecord) {
                String[] name = record.getName();
                if ((serviceType == null) || MdnsUtils.typeEqualsOrIsSubtype(
                        serviceType, name)) {
                    MdnsPointerRecord pointerRecord = (MdnsPointerRecord) record;
                    // Group PTR records that refer to the same service instance name into a single
                    // response.
                    MdnsResponse response = findResponseWithPointer(responses,
                            pointerRecord.getPointer());
                    if (response == null) {
                        response = new MdnsResponse(now, pointerRecord.getPointer(), interfaceIndex,
                                network);
                        responses.add(response);
                    }
                    if (response.addPointerRecord((MdnsPointerRecord) record)) {
                        modified.add(response);
                    }
                }
            }
        }

        // Loop 2: find SRV and TXT records, which reference the pointer in the PTR record.
        for (MdnsRecord record : records) {
            if (record instanceof MdnsServiceRecord) {
                MdnsServiceRecord serviceRecord = (MdnsServiceRecord) record;
                MdnsResponse response = findResponseWithPointer(responses, serviceRecord.getName());
                if (response != null && response.setServiceRecord(serviceRecord)) {
                    response.dropUnmatchedAddressRecords();
                    modified.add(response);
                }
            } else if (record instanceof MdnsTextRecord) {
                MdnsTextRecord textRecord = (MdnsTextRecord) record;
                MdnsResponse response = findResponseWithPointer(responses, textRecord.getName());
                if (response != null && response.setTextRecord(textRecord)) {
                    modified.add(response);
                }
            }
        }

        // Loop 3-1: find A and AAAA records and clear addresses if the cache-flush bit set, which
        //           reference the host name in the SRV record.
        final List<MdnsInetAddressRecord> inetRecords = new ArrayList<>();
        for (MdnsRecord record : records) {
            if (record instanceof MdnsInetAddressRecord) {
                MdnsInetAddressRecord inetRecord = (MdnsInetAddressRecord) record;
                inetRecords.add(inetRecord);
                if (allowMultipleSrvRecordsPerHost) {
                    List<MdnsResponse> matchingResponses =
                            findResponsesWithHostName(responses, inetRecord.getName());
                    for (MdnsResponse response : matchingResponses) {
                        // Per RFC6762 10.2, clear all address records if the cache-flush bit set.
                        // This bit, the cache-flush bit, tells neighboring hosts
                        // that this is not a shared record type.  Instead of merging this new
                        // record additively into the cache in addition to any previous records with
                        // the same name, rrtype, and rrclass.
                        // TODO: All old records with that name, rrtype, and rrclass that were
                        //       received more than one second ago are declared invalid, and marked
                        //       to expire from the cache in one second.
                        if (inetRecord.getCacheFlush()) {
                            response.clearInet4AddressRecords();
                            response.clearInet6AddressRecords();
                        }
                    }
                } else {
                    MdnsResponse response =
                            findResponseWithHostName(responses, inetRecord.getName());
                    if (response != null) {
                        // Per RFC6762 10.2, clear all address records if the cache-flush bit set.
                        // This bit, the cache-flush bit, tells neighboring hosts
                        // that this is not a shared record type.  Instead of merging this new
                        // record additively into the cache in addition to any previous records with
                        // the same name, rrtype, and rrclass.
                        // TODO: All old records with that name, rrtype, and rrclass that were
                        //       received more than one second ago are declared invalid, and marked
                        //       to expire from the cache in one second.
                        if (inetRecord.getCacheFlush()) {
                            response.clearInet4AddressRecords();
                            response.clearInet6AddressRecords();
                        }
                    }
                }
            }
        }

        // Loop 3-2: Assign addresses, which reference the host name in the SRV record.
        for (MdnsInetAddressRecord inetRecord : inetRecords) {
            if (allowMultipleSrvRecordsPerHost) {
                List<MdnsResponse> matchingResponses =
                        findResponsesWithHostName(responses, inetRecord.getName());
                for (MdnsResponse response : matchingResponses) {
                    if (assignInetRecord(response, inetRecord)) {
                        final MdnsResponse originalResponse = augmentedToOriginal.get(response);
                        if (originalResponse == null
                                || !originalResponse.hasIdenticalRecord(inetRecord)) {
                            modified.add(response);
                        }
                    }
                }
            } else {
                MdnsResponse response =
                        findResponseWithHostName(responses, inetRecord.getName());
                if (response != null) {
                    if (assignInetRecord(response, inetRecord)) {
                        final MdnsResponse originalResponse = augmentedToOriginal.get(response);
                        if (originalResponse == null
                                || !originalResponse.hasIdenticalRecord(inetRecord)) {
                            modified.add(response);
                        }
                    }
                }
            }
        }

        // Only responses that have new or modified address records were added to the modified set.
        // Make sure responses that have lost address records are added to the set too.
        for (int i = 0; i < augmentedToOriginal.size(); i++) {
            final MdnsResponse augmented = augmentedToOriginal.keyAt(i);
            final MdnsResponse original = augmentedToOriginal.valueAt(i);
            if (augmented.getRecords().size() != original.getRecords().size()) {
                modified.add(augmented);
            }
        }

        return Pair.create(modified, responses);
    }

    private static boolean assignInetRecord(
            MdnsResponse response, MdnsInetAddressRecord inetRecord) {
        if (inetRecord.getInet4Address() != null) {
            return response.addInet4AddressRecord(inetRecord);
        } else if (inetRecord.getInet6Address() != null) {
            return response.addInet6AddressRecord(inetRecord);
        }
        return false;
    }

    private static List<MdnsResponse> findResponsesWithHostName(
            @Nullable List<MdnsResponse> responses, String[] hostName) {
        if (responses == null || responses.isEmpty()) {
            return List.of();
        }

        List<MdnsResponse> result = null;
        for (MdnsResponse response : responses) {
            MdnsServiceRecord serviceRecord = response.getServiceRecord();
            if (serviceRecord == null) {
                continue;
            }
            if (MdnsUtils.equalsDnsLabelIgnoreDnsCase(serviceRecord.getServiceHost(), hostName)) {
                if (result == null) {
                    result = new ArrayList<>(/* initialCapacity= */ responses.size());
                }
                result.add(response);
            }
        }
        return result == null ? List.of() : result;
    }
}