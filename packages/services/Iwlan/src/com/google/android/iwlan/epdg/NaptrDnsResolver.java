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
import android.annotation.IntDef;
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

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * A utility wrapper around android.net.DnsResolver that queries for NAPTR DNS Resource Records, and
 * returns in the user callback a list of server (IP addresses, port number) combinations pertaining
 * to the service requested.
 */
final class NaptrDnsResolver {
    private static final String TAG = "NaptrDnsResolver";

    @IntDef(
            prefix = {"TYPE_"},
            value = {TYPE_A, TYPE_SRV, TYPE_U, TYPE_P})
    @Retention(RetentionPolicy.SOURCE)
    @interface NaptrRecordType {}

    public static final int TYPE_A = 0;
    public static final int TYPE_SRV = 1;

    // These below record types are not currently supported.
    public static final int TYPE_U = 2;
    public static final int TYPE_P = 3;

    /**
     * A NAPTR record comprises of a target domain name, along with the type of the record, as
     * defined in RFC 2915.
     */
    static class NaptrTarget {
        public final String mName;
        public final int mType;

        public NaptrTarget(String name, @NaptrRecordType int type) {
            mName = name;
            mType = type;
        }
    }

    static final int QUERY_TYPE_NAPTR = 35;

    static class NaptrResponse extends DnsPacket {
        /*
         * Parses and stores a NAPTR record as described in RFC 2915.
         */
        static class NaptrRecord {

            // A 16-bit unsigned value- the client should prioritize records with lower
            // 'preference'.
            public final int preference;

            // A 16-bit unsigned value- for records with the same 'preference' field, the client
            // should prioritize records with lower 'order'.
            public final int order;

            // A string that denotes the @NaptrRecordType.
            @NonNull public final String flag;

            // A free form string that denotes the service provided by the server described by the
            // record- SIP, email, etc.
            public final String service;

            // A string that describes the regex transformation (described in RFC 2915) that needs
            // to be applied to the DNS query domain name for further processes. RFC 2915 describes
            // that in a NaptrRecord, exactly one of |regex| and |replacement| must be non-null.
            @Nullable public final String regex;

            // This string describes how the input DNS query domain name should be replaced. With
            // the 'flag' and 'service' field, this instructs the DNS client on what to do next.
            // For current use cases, only the |replacement| field is expected to be non-null in a
            // NaptrRecord.
            @Nullable public final String replacement;

            private static final int MAXNAMESIZE = 255;

            private String parseNextField(ByteBuffer buf) throws BufferUnderflowException {
                final short size = buf.get();
                // size can also be 0, for instance for the 'regex' field.
                final byte[] field = new byte[size];
                buf.get(field, 0, size);
                return new String(field, StandardCharsets.UTF_8);
            }

            @NaptrRecordType
            public int getTypeFromFlagString() {
                switch (flag) {
                    case "S":
                    case "s":
                        return TYPE_SRV;
                    case "A":
                    case "a":
                        return TYPE_A;
                    default:
                        throw new ParseException("Unsupported flag type: " + flag);
                }
            }

            NaptrRecord(byte[] naptrRecordData) throws ParseException {
                final ByteBuffer buf = ByteBuffer.wrap(naptrRecordData);
                try {
                    order = Short.toUnsignedInt(buf.getShort());
                    preference = Short.toUnsignedInt(buf.getShort());
                    flag = parseNextField(buf);
                    service = parseNextField(buf);
                    regex = parseNextField(buf);
                    if (regex.length() != 0) {
                        throw new ParseException("NAPTR: regex field expected to be empty!");
                    }
                    replacement =
                            DnsRecordParser.parseName(
                                    buf, 0, /* isNameCompressionSupported */ true);
                    if (replacement == null) {
                        throw new ParseException(
                                "NAPTR: replacement field not expected to be empty!");
                    }
                    if (replacement.length() > MAXNAMESIZE) {
                        throw new ParseException(
                                "Parse name fail, replacement name size is too long: "
                                        + replacement.length());
                    }
                    if (buf.hasRemaining()) {
                        throw new ParseException(
                                "Parsing NAPTR record data failed: more bytes than expected!");
                    }
                } catch (BufferUnderflowException e) {
                    throw new ParseException("Parsing NAPTR Record data failed with cause", e);
                }
            }
        }

        private final int mQueryType;

        NaptrResponse(@NonNull byte[] data) throws ParseException {
            super(data);
            if (!mHeader.isResponse()) {
                throw new ParseException("Not an answer packet");
            }
            int numQueries = mHeader.getRecordCount(QDSECTION);
            // Expects exactly one query in query section.
            if (numQueries != 1) {
                throw new ParseException("Unexpected query count: " + numQueries);
            }
            // Expect only one question in question section.
            mQueryType = mRecords[QDSECTION].get(0).nsType;
            if (mQueryType != QUERY_TYPE_NAPTR) {
                throw new ParseException("Unexpected query type: " + mQueryType);
            }
        }

        public @NonNull List<NaptrRecord> parseNaptrRecords() throws ParseException {
            final List<NaptrRecord> naptrRecords = new ArrayList<>();
            if (mHeader.getRecordCount(ANSECTION) == 0) return naptrRecords;

            for (final DnsRecord ansSec : mRecords[ANSECTION]) {
                final int nsType = ansSec.nsType;
                if (nsType != QUERY_TYPE_NAPTR) {
                    throw new ParseException("Unexpected DNS record type in ANSECTION: " + nsType);
                }
                final NaptrRecord record = new NaptrRecord(ansSec.getRR());
                naptrRecords.add(record);
                Log.d(
                        TAG,
                        "NaptrRecord name: "
                                + ansSec.dName
                                + " replacement field: "
                                + record.replacement);
            }
            return naptrRecords;
        }
    }

    /**
     * A decorator for DnsResolver.Callback that accumulates IPv4/v6 responses for NAPTR DNS queries
     * and passes it up to the user callback.
     */
    static class NaptrRecordAnswerAccumulator implements DnsResolver.Callback<byte[]> {
        private static final String TAG = "NaptrRecordAnswerAccum";

        private final DnsResolver.Callback<List<NaptrTarget>> mUserCallback;
        private final Executor mUserExecutor;

        private static class LazyExecutor {
            public static final Executor INSTANCE = Executors.newSingleThreadExecutor();
        }

        static Executor getInternalExecutor() {
            return LazyExecutor.INSTANCE;
        }

        NaptrRecordAnswerAccumulator(
                @NonNull DnsResolver.Callback<List<NaptrTarget>> callback,
                @NonNull @CallbackExecutor Executor executor) {
            mUserCallback = callback;
            mUserExecutor = executor;
        }

        private List<NaptrTarget> composeNaptrRecordResult(
                List<NaptrResponse.NaptrRecord> responses) throws ParseException {
            final List<NaptrTarget> records = new ArrayList<>();
            if (responses.isEmpty()) return records;
            for (NaptrResponse.NaptrRecord response : responses) {
                records.add(
                        new NaptrTarget(response.replacement, response.getTypeFromFlagString()));
            }
            return records;
        }

        @Override
        public void onAnswer(@NonNull byte[] answer, int rcode) {
            try {
                final NaptrResponse response = new NaptrResponse(answer);
                final List<NaptrTarget> result =
                        composeNaptrRecordResult(response.parseNaptrRecords());
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
            }
        }

        @Override
        public void onError(@NonNull DnsException error) {
            Log.e(TAG, "onError: " + error);
            mUserExecutor.execute(() -> mUserCallback.onError(error));
        }
    }

    /**
     * Send an NAPTR DNS query on the specified network. The answer will be provided asynchronously
     * on the passed executor, through the provided {@link DnsResolver.Callback}.
     *
     * @param network {@link Network} specifying which network to query on. {@code null} for query
     *     on default network.
     * @param domain NAPTR domain name to query.
     * @param cancellationSignal used by the caller to signal if the query should be cancelled. May
     *     be {@code null}.
     * @param callback a {@link DnsResolver.Callback} which will be called on a separate thread to
     *     notify the caller of the result of dns query.
     */
    public static void query(
            @Nullable Network network,
            @NonNull String domain,
            @NonNull @CallbackExecutor Executor executor,
            @Nullable CancellationSignal cancellationSignal,
            @NonNull DnsResolver.Callback<List<NaptrTarget>> callback) {
        final NaptrRecordAnswerAccumulator naptrDnsCb =
                new NaptrRecordAnswerAccumulator(callback, executor);
        DnsResolver.getInstance()
                .rawQuery(
                        network,
                        domain,
                        DnsResolver.CLASS_IN,
                        QUERY_TYPE_NAPTR,
                        DnsResolver.FLAG_EMPTY,
                        NaptrRecordAnswerAccumulator.getInternalExecutor(),
                        cancellationSignal,
                        naptrDnsCb);
    }

    private NaptrDnsResolver() {}
}
