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

package com.google.android.iwlan.epdg;

import android.annotation.CallbackExecutor;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.net.DnsResolver;
import android.net.DnsResolver.DnsException;
import android.net.Network;
import android.net.ParseException;
import android.os.CancellationSignal;
import android.util.Log;

import com.android.net.module.util.DnsPacket;
import com.android.net.module.util.DnsPacketUtils.DnsRecordParser;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * A utility wrapper around android.net.DnsResolver that queries for SRV DNS Resource Records, and
 * returns in the user callback a list of server (IP addresses, port number) combinations pertaining
 * to the service requested.
 *
 * <p>The returned {@link List<SrvRecordInetAddress>} is currently not sorted according to priority
 * and weight, in the mechanism described in RFC2782.
 */
final class SrvDnsResolver {
    private static final String TAG = "SrvDnsResolver";

    /**
     * An SRV Resource Record is queried to obtain the specific port number at which a service is
     * offered. So the client is returned a combination of (INetAddress, port).
     */
    static class SrvRecordInetAddress {
        // Holds an IPv4/v6 address, obtained by querying getHostAddress().
        public final InetAddress mInetAddress;
        // A 16-bit unsigned port number.
        public final int mPort;

        public SrvRecordInetAddress(InetAddress inetAddress, int port) {
            mInetAddress = inetAddress;
            mPort = port;
        }
    }

    // Since the query type for SRV records is not defined in DnsResolver, it is defined here.
    static final int QUERY_TYPE_SRV = 33;

    /*
     * Parses and stores an SRV record as described in RFC2782.
     *
     * Expects records of type QUERY_TYPE_SRV in the Queries and Answer records section, and records
     * of type TYPE_A and TYPE_AAAA in the Additional Records section of the DnsPacket.
     */
    static class SrvResponse extends DnsPacket {
        static class SrvRecord {
            // A 16-bit unsigned integer that determines the priority of the target host. Clients
            // must attempt to contact the target host with the lowest-numbered priority first.
            public final int priority;

            // A 16-bit unsigned integer that specifies a relative weight for entries with the same
            // priority. Larger weights should we given a proportionately higher probability of
            // being selected.
            public final int weight;

            // A 16-bit unsigned integer that specifies the port on this target for this service.
            public final int port;

            // The domain name of the target host. A target of "." means that the service is
            // decidedly not available at this domain.
            public final String target;

            private static final int MAXNAMESIZE = 255;

            SrvRecord(byte[] srvRecordData) throws ParseException {
                final ByteBuffer buf = ByteBuffer.wrap(srvRecordData);

                try {
                    priority = Short.toUnsignedInt(buf.getShort());
                    weight = Short.toUnsignedInt(buf.getShort());
                    port = Short.toUnsignedInt(buf.getShort());
                    // Although unexpected, some DNS servers do use name compression on portions of
                    // the 'target' field that overlap with the query section of the DNS packet.
                    target =
                            DnsRecordParser.parseName(
                                    buf, 0, /* isNameCompressionSupported */ true);
                    if (target.length() > MAXNAMESIZE) {
                        throw new ParseException(
                                "Parse name failed, name size is too long: " + target.length());
                    }
                    if (buf.hasRemaining()) {
                        throw new ParseException(
                                "Parsing SRV record data failed: more bytes than expected!");
                    }
                } catch (BufferUnderflowException e) {
                    throw new ParseException("Parsing SRV Record data failed with cause", e);
                }
            }
        }

        private final int mQueryType;

        SrvResponse(@NonNull byte[] data) throws ParseException {
            super(data);
            if (!mHeader.isResponse()) {
                throw new ParseException("Not an answer packet");
            }
            int numQueries = mHeader.getRecordCount(QDSECTION);
            // Expects exactly one query in query section.
            if (numQueries != 1) {
                throw new ParseException("Unexpected query count: " + numQueries);
            }
            mQueryType = mRecords[QDSECTION].get(0).nsType;
            if (mQueryType != QUERY_TYPE_SRV) {
                throw new ParseException("Unexpected query type: " + mQueryType);
            }
        }

        // Parses the Answers section of a DnsPacket to construct and return a mapping
        // of Domain Name strings to their corresponding SRV record.
        public @NonNull Map<String, SrvRecord> parseSrvRecords() throws ParseException {
            final HashMap<String, SrvRecord> targetNameToSrvRecord = new LinkedHashMap<>();
            if (mHeader.getRecordCount(ANSECTION) == 0) return targetNameToSrvRecord;

            for (final DnsRecord ansSec : mRecords[ANSECTION]) {
                final int nsType = ansSec.nsType;
                if (nsType != QUERY_TYPE_SRV) {
                    throw new ParseException("Unexpected DNS record type in ANSECTION: " + nsType);
                }
                final SrvRecord record = new SrvRecord(ansSec.getRR());
                if (targetNameToSrvRecord.containsKey(record.target)) {
                    throw new ParseException(
                            "Domain name "
                                    + record.target
                                    + " already encountered in DNS response!");
                }
                targetNameToSrvRecord.put(record.target, record);
                Log.d(TAG, "SrvRecord name: " + ansSec.dName + " target name: " + record.target);
            }
            return targetNameToSrvRecord;
        }

        /*
         * Parses the 'Additional Records' section of a DnsPacket and expects 'Address Records'
         * (TYPE_A and TYPE_AAAA records) to construct and return a mapping of Domain Name strings
         * to their corresponding IP address(es).
         */
        public @NonNull Map<String, List<InetAddress>> parseIpAddresses() throws ParseException {
            final HashMap<String, List<InetAddress>> domainNameToIpAddress = new HashMap<>();
            if (mHeader.getRecordCount(ARSECTION) == 0) return domainNameToIpAddress;

            for (final DnsRecord ansSec : mRecords[ARSECTION]) {
                int nsType = ansSec.nsType;
                if (nsType != DnsResolver.TYPE_A && nsType != DnsResolver.TYPE_AAAA) {
                    throw new ParseException("Unexpected DNS record type in ARSECTION: " + nsType);
                }
                domainNameToIpAddress.computeIfAbsent(ansSec.dName, k -> new ArrayList<>());
                try {
                    final InetAddress ipAddress = InetAddress.getByAddress(ansSec.getRR());
                    Log.d(
                            TAG,
                            "Additional record name: "
                                    + ansSec.dName
                                    + " IP addr: "
                                    + ipAddress.getHostAddress());
                    domainNameToIpAddress.get(ansSec.dName).add(ipAddress);
                } catch (UnknownHostException e) {
                    throw new ParseException(
                            "RR to IP address translation failed for domain: " + ansSec.dName);
                }
            }
            return domainNameToIpAddress;
        }
    }

    /**
     * A decorator for {@link DnsResolver.Callback} that accumulates IPv4/v6 responses for SRV DNS
     * queries and passes it up to the user callback.
     */
    private static class SrvRecordAnswerAccumulator implements DnsResolver.Callback<byte[]> {
        private static final String TAG = "SrvRecordAnswerAccum";

        private final Network mNetwork;
        private final DnsResolver.Callback<List<SrvRecordInetAddress>> mUserCallback;
        private final Executor mUserExecutor;

        private static class LazyExecutor {
            public static final Executor INSTANCE = Executors.newSingleThreadExecutor();
        }

        static Executor getInternalExecutor() {
            return LazyExecutor.INSTANCE;
        }

        SrvRecordAnswerAccumulator(
                @NonNull Network network,
                @NonNull DnsResolver.Callback<List<SrvRecordInetAddress>> callback,
                @NonNull @CallbackExecutor Executor executor) {
            mNetwork = network;
            mUserCallback = callback;
            mUserExecutor = executor;
        }

        /**
         * Some DNS servers, when queried for an SRV record, do not return the IPv4/v6 records along
         * with the SRV record. For those, we perform an additional blocking IPv4/v6 DNS query for
         * each outstanding SRV record.
         */
        private List<InetAddress> queryDns(String domainName) throws DnsException {
            final CompletableFuture<List<InetAddress>> result = new CompletableFuture();
            final DnsResolver.Callback<List<InetAddress>> cb =
                    new DnsResolver.Callback<List<InetAddress>>() {
                        @Override
                        public void onAnswer(
                                @NonNull final List<InetAddress> answer, final int rcode) {
                            if (rcode != 0) {
                                Log.e(TAG, "queryDNS Response Code = " + rcode);
                            }
                            result.complete(answer);
                        }

                        @Override
                        public void onError(@Nullable final DnsException error) {
                            Log.e(TAG, "queryDNS response with error : " + error);
                            result.completeExceptionally(error);
                        }
                    };
            DnsResolver.getInstance()
                    .query(mNetwork, domainName, DnsResolver.FLAG_EMPTY, Runnable::run, null, cb);

            try {
                return result.get();
            } catch (ExecutionException e) {
                throw (DnsException) e.getCause();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Restore the interrupted status
                throw new DnsException(DnsResolver.ERROR_SYSTEM, e);
            }
        }

        /**
         * Composes the final (IP address, Port) combination for the client's SRV request. Performs
         * additional DNS queries if necessary. The SRV records are presently not sorted according
         * to priority and weight, as described in RFC2782- this is simply 'good enough'.
         */
        private List<SrvRecordInetAddress> composeSrvRecordResult(SrvResponse response)
                throws DnsPacket.ParseException, DnsException {
            final List<SrvRecordInetAddress> srvRecordInetAddresses = new ArrayList<>();
            final Map<String, List<InetAddress>> domainNameToIpAddresses =
                    response.parseIpAddresses();
            final Map<String, SrvResponse.SrvRecord> targetNameToSrvRecords =
                    response.parseSrvRecords();

            Iterator<Map.Entry<String, SrvResponse.SrvRecord>> itr =
                    targetNameToSrvRecords.entrySet().iterator();

            // Checks if the received SRV RRs have a corresponding match in IP addresses. For the
            // ones that do, adds the (IP address, port number) to the output field list.
            while (itr.hasNext()) {
                Map.Entry<String, SrvResponse.SrvRecord> targetNameToSrvRecord = itr.next();
                String domainName = targetNameToSrvRecord.getKey();
                int port = targetNameToSrvRecord.getValue().port;
                List<InetAddress> addresses = domainNameToIpAddresses.get(domainName);
                if (addresses != null) {
                    // Found a match- add to output list and remove entry from SrvRecord collection.
                    for (InetAddress address : addresses) {
                        srvRecordInetAddresses.add(new SrvRecordInetAddress(address, port));
                    }
                    itr.remove();
                }
            }

            // For the SRV RRs that don't, spawns a separate DnsResolver query for each, and
            // collects results using a blocking call.
            itr = targetNameToSrvRecords.entrySet().iterator();
            while (itr.hasNext()) {
                Map.Entry<String, SrvResponse.SrvRecord> targetNameToSrvRecord = itr.next();
                String domainName = targetNameToSrvRecord.getKey();
                int port = targetNameToSrvRecord.getValue().port;
                List<InetAddress> addresses = queryDns(domainName);
                for (InetAddress address : addresses) {
                    srvRecordInetAddresses.add(new SrvRecordInetAddress(address, port));
                }
            }
            return srvRecordInetAddresses;
        }

        @Override
        public void onAnswer(@NonNull byte[] answer, int rcode) {
            try {
                final SrvResponse response = new SrvResponse(answer);
                final List<SrvRecordInetAddress> result = composeSrvRecordResult(response);
                mUserExecutor.execute(() -> mUserCallback.onAnswer(result, rcode));
            } catch (DnsPacket.ParseException e) {
                // Convert the com.android.net.module.util.DnsPacket.ParseException to an
                // android.net.ParseException. This is the type that was used in Q and is implied
                // by the public documentation of ERROR_PARSE.
                //
                // DnsPacket cannot throw android.net.ParseException directly because it's @hide.
                final ParseException pe = new ParseException(e.reason, e.getCause());
                pe.setStackTrace(e.getStackTrace());
                Log.e(TAG, "ParseException", pe);
                mUserExecutor.execute(
                        () -> mUserCallback.onError(new DnsException(DnsResolver.ERROR_PARSE, pe)));
            } catch (DnsException e) {
                mUserExecutor.execute(() -> mUserCallback.onError(e));
            }
        }

        @Override
        public void onError(@NonNull DnsException error) {
            Log.e(TAG, "onError: " + error);
            mUserExecutor.execute(() -> mUserCallback.onError(error));
        }
    }

    /**
     * Send an SRV DNS query with the specified name, class and query type. The answer will be
     * provided asynchronously on the passed executor, through the provided {@link
     * DnsResolver.Callback}.
     *
     * @param network {@link Network} specifying which network to query on. {@code null} for query
     *     on default network.
     * @param domain SRV domain name to query ( in format _Service._Protocol.Name)
     * @param cancellationSignal used by the caller to signal if the query should be cancelled. May
     *     be {@code null}.
     * @param callback a {@link DnsResolver.Callback} which will be called on a separate thread to
     *     notify the caller of the result of the DNS query.
     */
    public static void query(
            @Nullable Network network,
            @NonNull String domain,
            @NonNull @CallbackExecutor Executor executor,
            @Nullable CancellationSignal cancellationSignal,
            @NonNull DnsResolver.Callback<List<SrvRecordInetAddress>> callback) {
        final SrvRecordAnswerAccumulator srvDnsCb =
                new SrvRecordAnswerAccumulator(network, callback, executor);
        DnsResolver.getInstance()
                .rawQuery(
                        network,
                        domain,
                        DnsResolver.CLASS_IN,
                        QUERY_TYPE_SRV,
                        DnsResolver.FLAG_EMPTY,
                        SrvRecordAnswerAccumulator.getInternalExecutor(),
                        cancellationSignal,
                        srvDnsCb);
    }

    private SrvDnsResolver() {}
}
