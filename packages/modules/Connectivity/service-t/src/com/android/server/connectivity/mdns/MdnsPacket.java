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

import android.annotation.NonNull;
import android.annotation.Nullable;

import java.io.EOFException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A class holding data that can be included in a mDNS packet.
 */
public class MdnsPacket {
    private static final String TAG = MdnsPacket.class.getSimpleName();

    public final int transactionId;
    public final int flags;
    @NonNull
    public final List<MdnsRecord> questions;
    @NonNull
    public final List<MdnsRecord> answers;
    @NonNull
    public final List<MdnsRecord> authorityRecords;
    @NonNull
    public final List<MdnsRecord> additionalRecords;

    MdnsPacket(int flags,
            @NonNull List<MdnsRecord> questions,
            @NonNull List<MdnsRecord> answers,
            @NonNull List<MdnsRecord> authorityRecords,
            @NonNull List<MdnsRecord> additionalRecords) {
        this(0, flags, questions, answers, authorityRecords, additionalRecords);
    }

    MdnsPacket(int transactionId, int flags,
            @NonNull List<MdnsRecord> questions,
            @NonNull List<MdnsRecord> answers,
            @NonNull List<MdnsRecord> authorityRecords,
            @NonNull List<MdnsRecord> additionalRecords) {
        this.transactionId = transactionId;
        this.flags = flags;
        this.questions = Collections.unmodifiableList(questions);
        this.answers = Collections.unmodifiableList(answers);
        this.authorityRecords = Collections.unmodifiableList(authorityRecords);
        this.additionalRecords = Collections.unmodifiableList(additionalRecords);
    }

    /**
     * Exception thrown on parse errors.
     */
    public static class ParseException extends IOException {
        public final int code;

        public ParseException(int code, @NonNull String message, @Nullable Throwable cause) {
            super(message, cause);
            this.code = code;
        }
    }

    /**
     * Parse the packet in the provided {@link MdnsPacketReader}.
     */
    @NonNull
    public static MdnsPacket parse(@NonNull MdnsPacketReader reader) throws ParseException {
        final int transactionId;
        final int flags;
        try {
            transactionId = reader.readUInt16();
            flags = reader.readUInt16();
        } catch (EOFException e) {
            throw new ParseException(MdnsResponseErrorCode.ERROR_END_OF_FILE,
                    "Reached the end of the mDNS response unexpectedly.", e);
        }
        return parseRecordsSection(reader, flags, transactionId);
    }

    /**
     * Parse the records section of a mDNS packet in the provided {@link MdnsPacketReader}.
     *
     * The records section starts with the questions count, just after the packet flags.
     */
    public static MdnsPacket parseRecordsSection(@NonNull MdnsPacketReader reader, int flags,
            int transactionId) throws ParseException {
        try {
            final int numQuestions = reader.readUInt16();
            final int numAnswers = reader.readUInt16();
            final int numAuthority = reader.readUInt16();
            final int numAdditional = reader.readUInt16();

            final ArrayList<MdnsRecord> questions = parseRecords(reader, numQuestions, true);
            final ArrayList<MdnsRecord> answers = parseRecords(reader, numAnswers, false);
            final ArrayList<MdnsRecord> authority = parseRecords(reader, numAuthority, false);
            final ArrayList<MdnsRecord> additional = parseRecords(reader, numAdditional, false);

            return new MdnsPacket(transactionId, flags, questions, answers, authority, additional);
        } catch (EOFException e) {
            throw new ParseException(MdnsResponseErrorCode.ERROR_END_OF_FILE,
                    "Reached the end of the mDNS response unexpectedly.", e);
        }
    }

    private static ArrayList<MdnsRecord> parseRecords(@NonNull MdnsPacketReader reader, int count,
            boolean isQuestion)
            throws ParseException {
        final ArrayList<MdnsRecord> records = new ArrayList<>(count);
        for (int i = 0; i < count; ++i) {
            final MdnsRecord record = parseRecord(reader, isQuestion);
            if (record != null) {
                records.add(record);
            }
        }
        return records;
    }

    @Nullable
    private static MdnsRecord parseRecord(@NonNull MdnsPacketReader reader, boolean isQuestion)
            throws ParseException {
        String[] name;
        try {
            name = reader.readLabels();
        } catch (IOException e) {
            throw new ParseException(MdnsResponseErrorCode.ERROR_READING_RECORD_NAME,
                    "Failed to read labels from mDNS response.", e);
        }

        final int type;
        try {
            type = reader.readUInt16();
        } catch (EOFException e) {
            throw new ParseException(MdnsResponseErrorCode.ERROR_END_OF_FILE,
                    "Reached the end of the mDNS response unexpectedly.", e);
        }

        switch (type) {
            case MdnsRecord.TYPE_A: {
                try {
                    return new MdnsInetAddressRecord(name, MdnsRecord.TYPE_A, reader, isQuestion);
                } catch (IOException e) {
                    throw new ParseException(MdnsResponseErrorCode.ERROR_READING_A_RDATA,
                            "Failed to read A record from mDNS response.", e);
                }
            }

            case MdnsRecord.TYPE_AAAA: {
                try {
                    return new MdnsInetAddressRecord(name,
                            MdnsRecord.TYPE_AAAA, reader, isQuestion);
                } catch (IOException e) {
                    throw new ParseException(MdnsResponseErrorCode.ERROR_READING_AAAA_RDATA,
                            "Failed to read AAAA record from mDNS response.", e);
                }
            }

            case MdnsRecord.TYPE_PTR: {
                try {
                    return new MdnsPointerRecord(name, reader, isQuestion);
                } catch (IOException e) {
                    throw new ParseException(MdnsResponseErrorCode.ERROR_READING_PTR_RDATA,
                            "Failed to read PTR record from mDNS response.", e);
                }
            }

            case MdnsRecord.TYPE_SRV: {
                try {
                    return new MdnsServiceRecord(name, reader, isQuestion);
                } catch (IOException e) {
                    throw new ParseException(MdnsResponseErrorCode.ERROR_READING_SRV_RDATA,
                            "Failed to read SRV record from mDNS response.", e);
                }
            }

            case MdnsRecord.TYPE_TXT: {
                try {
                    return new MdnsTextRecord(name, reader, isQuestion);
                } catch (IOException e) {
                    throw new ParseException(MdnsResponseErrorCode.ERROR_READING_TXT_RDATA,
                            "Failed to read TXT record from mDNS response.", e);
                }
            }

            case MdnsRecord.TYPE_NSEC: {
                try {
                    return new MdnsNsecRecord(name, reader, isQuestion);
                } catch (IOException e) {
                    throw new ParseException(MdnsResponseErrorCode.ERROR_READING_NSEC_RDATA,
                            "Failed to read NSEC record from mDNS response.", e);
                }
            }

            case MdnsRecord.TYPE_ANY: {
                try {
                    return new MdnsAnyRecord(name, reader);
                } catch (IOException e) {
                    throw new ParseException(MdnsResponseErrorCode.ERROR_READING_ANY_RDATA,
                            "Failed to read TYPE_ANY record from mDNS response.", e);
                }
            }

            default: {
                try {
                    skipMdnsRecord(reader, isQuestion);
                    return null;
                } catch (IOException e) {
                    throw new ParseException(MdnsResponseErrorCode.ERROR_SKIPPING_UNKNOWN_RECORD,
                            "Failed to skip mDNS record.", e);
                }
            }
        }
    }

    private static void skipMdnsRecord(@NonNull MdnsPacketReader reader, boolean isQuestion)
            throws IOException {
        reader.skip(2); // Skip the class
        if (isQuestion) return;
        // Skip TTL and data
        reader.skip(4);
        int dataLength = reader.readUInt16();
        reader.skip(dataLength);
    }
}
