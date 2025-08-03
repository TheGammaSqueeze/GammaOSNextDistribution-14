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

import static com.android.server.connectivity.mdns.MdnsServiceTypeClient.INVALID_TRANSACTION_ID;

import android.annotation.NonNull;
import android.os.Build;
import android.text.TextUtils;
import android.util.Pair;

import com.android.net.module.util.SharedLog;
import com.android.server.connectivity.mdns.util.MdnsUtils;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.DatagramPacket;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * A {@link Callable} that builds and enqueues a mDNS query to send over the multicast socket. If a
 * query is built and enqueued successfully, then call to {@link #call()} returns the transaction ID
 * and the list of the subtypes in the query as a {@link Pair}. If a query is failed to build, or if
 * it can not be enqueued, then call to {@link #call()} returns {@code null}.
 */
public class EnqueueMdnsQueryCallable implements Callable<Pair<Integer, List<String>>> {

    private static final String TAG = "MdnsQueryCallable";
    private static final List<Integer> castShellEmulatorMdnsPorts;

    static {
        castShellEmulatorMdnsPorts = new ArrayList<>();
        String[] stringPorts = MdnsConfigs.castShellEmulatorMdnsPorts();

        for (String port : stringPorts) {
            try {
                castShellEmulatorMdnsPorts.add(Integer.parseInt(port));
            } catch (NumberFormatException e) {
                // Ignore.
            }
        }
    }

    @NonNull
    private final WeakReference<MdnsSocketClientBase> weakRequestSender;
    @NonNull
    private final MdnsPacketWriter packetWriter;
    @NonNull
    private final String[] serviceTypeLabels;
    @NonNull
    private final List<String> subtypes;
    private final boolean expectUnicastResponse;
    private final int transactionId;
    @NonNull
    private final SocketKey socketKey;
    private final boolean sendDiscoveryQueries;
    @NonNull
    private final List<MdnsResponse> servicesToResolve;
    @NonNull
    private final MdnsUtils.Clock clock;
    @NonNull
    private final SharedLog sharedLog;
    private final boolean onlyUseIpv6OnIpv6OnlyNetworks;

    EnqueueMdnsQueryCallable(
            @NonNull MdnsSocketClientBase requestSender,
            @NonNull MdnsPacketWriter packetWriter,
            @NonNull String serviceType,
            @NonNull Collection<String> subtypes,
            boolean expectUnicastResponse,
            int transactionId,
            @NonNull SocketKey socketKey,
            boolean onlyUseIpv6OnIpv6OnlyNetworks,
            boolean sendDiscoveryQueries,
            @NonNull Collection<MdnsResponse> servicesToResolve,
            @NonNull MdnsUtils.Clock clock,
            @NonNull SharedLog sharedLog) {
        weakRequestSender = new WeakReference<>(requestSender);
        this.packetWriter = packetWriter;
        serviceTypeLabels = TextUtils.split(serviceType, "\\.");
        this.subtypes = new ArrayList<>(subtypes);
        this.expectUnicastResponse = expectUnicastResponse;
        this.transactionId = transactionId;
        this.socketKey = socketKey;
        this.onlyUseIpv6OnIpv6OnlyNetworks = onlyUseIpv6OnIpv6OnlyNetworks;
        this.sendDiscoveryQueries = sendDiscoveryQueries;
        this.servicesToResolve = new ArrayList<>(servicesToResolve);
        this.clock = clock;
        this.sharedLog = sharedLog;
    }

    /**
     * Call to execute the mdns query.
     *
     * @return The pair of transaction id and the subtypes for the query.
     */
    // Incompatible return type for override of Callable#call().
    @SuppressWarnings("nullness:override.return.invalid")
    @Override
    public Pair<Integer, List<String>> call() {
        try {
            MdnsSocketClientBase requestSender = weakRequestSender.get();
            if (requestSender == null) {
                return Pair.create(INVALID_TRANSACTION_ID, new ArrayList<>());
            }

            final List<MdnsRecord> questions = new ArrayList<>();

            if (sendDiscoveryQueries) {
                // Base service type
                questions.add(new MdnsPointerRecord(serviceTypeLabels, expectUnicastResponse));
                for (String subtype : subtypes) {
                    final String[] labels = new String[serviceTypeLabels.length + 2];
                    labels[0] = MdnsConstants.SUBTYPE_PREFIX + subtype;
                    labels[1] = MdnsConstants.SUBTYPE_LABEL;
                    System.arraycopy(serviceTypeLabels, 0, labels, 2, serviceTypeLabels.length);

                    questions.add(new MdnsPointerRecord(labels, expectUnicastResponse));
                }
            }

            // List of (name, type) to query
            final long now = clock.elapsedRealtime();
            for (MdnsResponse response : servicesToResolve) {
                final String[] serviceName = response.getServiceName();
                if (serviceName == null) continue;
                boolean renewTxt = !response.hasTextRecord() || MdnsUtils.isRecordRenewalNeeded(
                        response.getTextRecord(), now);
                boolean renewSrv = !response.hasServiceRecord() || MdnsUtils.isRecordRenewalNeeded(
                        response.getServiceRecord(), now);
                if (renewSrv && renewTxt) {
                    questions.add(new MdnsAnyRecord(serviceName, expectUnicastResponse));
                } else {
                    if (renewTxt) {
                        questions.add(new MdnsTextRecord(serviceName, expectUnicastResponse));
                    }
                    if (renewSrv) {
                        questions.add(new MdnsServiceRecord(serviceName, expectUnicastResponse));
                        // The hostname is not yet known, so queries for address records will be
                        // sent the next time the EnqueueMdnsQueryCallable is enqueued if the reply
                        // does not contain them. In practice, advertisers should include the
                        // address records when queried for SRV, although it's not a MUST
                        // requirement (RFC6763 12.2).
                    } else if (!response.hasInet4AddressRecord()
                            && !response.hasInet6AddressRecord()) {
                        final String[] host = response.getServiceRecord().getServiceHost();
                        questions.add(new MdnsInetAddressRecord(
                                host, MdnsRecord.TYPE_A, expectUnicastResponse));
                        questions.add(new MdnsInetAddressRecord(
                                host, MdnsRecord.TYPE_AAAA, expectUnicastResponse));
                    }
                }
            }

            if (questions.size() == 0) {
                // No query to send
                return Pair.create(INVALID_TRANSACTION_ID, new ArrayList<>());
            }

            final MdnsPacket queryPacket = new MdnsPacket(
                    transactionId,
                    MdnsConstants.FLAGS_QUERY,
                    questions,
                    Collections.emptyList(), /* answers */
                    Collections.emptyList(), /* authorityRecords */
                    Collections.emptyList() /* additionalRecords */);
            MdnsUtils.writeMdnsPacket(packetWriter, queryPacket);
            sendPacketToIpv4AndIpv6(requestSender, MdnsConstants.MDNS_PORT);
            for (Integer emulatorPort : castShellEmulatorMdnsPorts) {
                sendPacketToIpv4AndIpv6(requestSender, emulatorPort);
            }
            return Pair.create(transactionId, subtypes);
        } catch (IOException e) {
            sharedLog.e(String.format("Failed to create mDNS packet for subtype: %s.",
                    TextUtils.join(",", subtypes)), e);
            return Pair.create(INVALID_TRANSACTION_ID, new ArrayList<>());
        }
    }

    private void sendPacket(MdnsSocketClientBase requestSender, InetSocketAddress address)
            throws IOException {
        DatagramPacket packet = packetWriter.getPacket(address);
        if (expectUnicastResponse) {
            // MdnsMultinetworkSocketClient is only available on T+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    && requestSender instanceof MdnsMultinetworkSocketClient) {
                ((MdnsMultinetworkSocketClient) requestSender).sendPacketRequestingUnicastResponse(
                        packet, socketKey, onlyUseIpv6OnIpv6OnlyNetworks);
            } else {
                requestSender.sendPacketRequestingUnicastResponse(
                        packet, onlyUseIpv6OnIpv6OnlyNetworks);
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                    && requestSender instanceof MdnsMultinetworkSocketClient) {
                ((MdnsMultinetworkSocketClient) requestSender)
                        .sendPacketRequestingMulticastResponse(
                                packet, socketKey, onlyUseIpv6OnIpv6OnlyNetworks);
            } else {
                requestSender.sendPacketRequestingMulticastResponse(
                        packet, onlyUseIpv6OnIpv6OnlyNetworks);
            }
        }
    }

    private void sendPacketToIpv4AndIpv6(MdnsSocketClientBase requestSender, int port) {
        try {
            sendPacket(requestSender,
                    new InetSocketAddress(MdnsConstants.getMdnsIPv4Address(), port));
        } catch (IOException e) {
            sharedLog.e("Can't send packet to IPv4", e);
        }
        try {
            sendPacket(requestSender,
                    new InetSocketAddress(MdnsConstants.getMdnsIPv6Address(), port));
        } catch (IOException e) {
            sharedLog.e("Can't send packet to IPv6", e);
        }
    }
}