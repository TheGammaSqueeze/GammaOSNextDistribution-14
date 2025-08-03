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

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import android.annotation.Nullable;
import android.os.SystemClock;
import android.text.TextUtils;

import com.android.internal.annotations.VisibleForTesting;
import com.android.server.connectivity.mdns.util.MdnsUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

/**
 * Abstract base class for mDNS records. Stores the header fields and provides methods for reading
 * the record from and writing it to a packet.
 */
public abstract class MdnsRecord {
    public static final int TYPE_A = 0x0001;
    public static final int TYPE_AAAA = 0x001C;
    public static final int TYPE_PTR = 0x000C;
    public static final int TYPE_SRV = 0x0021;
    public static final int TYPE_TXT = 0x0010;
    public static final int TYPE_NSEC = 0x002f;
    public static final int TYPE_ANY = 0x00ff;

    private static final int FLAG_CACHE_FLUSH = 0x8000;

    public static final long RECEIPT_TIME_NOT_SENT = 0L;
    public static final int CLASS_ANY = 0x00ff;
    /** Max label length as per RFC 1034/1035 */
    public static final int MAX_LABEL_LENGTH = 63;

    /** Status indicating that the record is current. */
    public static final int STATUS_OK = 0;
    /** Status indicating that the record has expired (TTL reached 0). */
    public static final int STATUS_EXPIRED = 1;
    /** Status indicating that the record should be refreshed (Less than half of TTL remains.) */
    public static final int STATUS_NEEDS_REFRESH = 2;

    protected final String[] name;
    private final int type;
    private final int cls;
    private final long receiptTimeMillis;
    private final long ttlMillis;
    private Object key;

    /**
     * Constructs a new record with the given name and type.
     *
     * @param reader The reader to read the record from.
     * @param isQuestion Whether the record was included in the questions part of the message.
     * @throws IOException If an error occurs while reading the packet.
     */
    protected MdnsRecord(String[] name, int type, MdnsPacketReader reader, boolean isQuestion)
            throws IOException {
        this.name = name;
        this.type = type;
        cls = reader.readUInt16();
        receiptTimeMillis = SystemClock.elapsedRealtime();

        if (isQuestion) {
            // Questions do not have TTL or data
            ttlMillis = 0L;
        } else {
            ttlMillis = SECONDS.toMillis(reader.readUInt32());
            int dataLength = reader.readUInt16();

            reader.setLimit(dataLength);
            readData(reader);
            reader.clearLimit();
        }
    }

    /**
     * Constructs a new record with the given name and type.
     *
     * @param reader The reader to read the record from.
     * @throws IOException If an error occurs while reading the packet.
     */
    // call to readData(com.android.server.connectivity.mdns.MdnsPacketReader) not allowed on given
    // receiver.
    @SuppressWarnings("nullness:method.invocation.invalid")
    protected MdnsRecord(String[] name, int type, MdnsPacketReader reader) throws IOException {
        this(name, type, reader, false);
    }

    /**
     * Constructs a new record with the given properties.
     */
    protected MdnsRecord(String[] name, int type, int cls, long receiptTimeMillis,
            boolean cacheFlush, long ttlMillis) {
        this.name = name;
        this.type = type;
        this.cls = cls | (cacheFlush ? FLAG_CACHE_FLUSH : 0);
        this.receiptTimeMillis = receiptTimeMillis;
        this.ttlMillis = ttlMillis;
    }

    /**
     * Converts an array of labels into their dot-separated string representation. This method
     * should
     * be used for logging purposes only.
     */
    public static String labelsToString(String[] labels) {
        if (labels == null) {
            return null;
        }
        return TextUtils.join(".", labels);
    }

    /** Tests if |list1| is a suffix of |list2|. */
    public static boolean labelsAreSuffix(String[] list1, String[] list2) {
        int offset = list2.length - list1.length;

        if (offset < 1) {
            return false;
        }

        for (int i = 0; i < list1.length; ++i) {
            if (!MdnsUtils.equalsIgnoreDnsCase(list1[i], list2[i + offset])) {
                return false;
            }
        }

        return true;
    }

    /** Returns the record's receipt (creation) time. */
    public final long getReceiptTime() {
        return receiptTimeMillis;
    }

    /** Returns the record's name. */
    public String[] getName() {
        return name;
    }

    /** Returns the record's original TTL, in milliseconds. */
    public final long getTtl() {
        return ttlMillis;
    }

    /** Returns the record's type. */
    public final int getType() {
        return type;
    }

    /** Return the record's class. */
    public final int getRecordClass() {
        return cls & ~FLAG_CACHE_FLUSH;
    }

    /** Return whether the cache flush flag is set. */
    public final boolean getCacheFlush() {
        return (cls & FLAG_CACHE_FLUSH) != 0;
    }

    /**
     * For questions, returns whether a unicast reply was requested.
     *
     * In practice this is identical to {@link #getCacheFlush()}, as the "cache flush" flag in
     * replies is the same as "unicast reply requested" in questions.
     */
    public final boolean isUnicastReplyRequested() {
        return (cls & MdnsConstants.QCLASS_UNICAST) != 0;
    }

    /**
     * Returns the record's remaining TTL.
     *
     * If the record was not sent yet (receipt time {@link #RECEIPT_TIME_NOT_SENT}), this is the
     * original TTL of the record.
     * @param now The current system time.
     * @return The remaning TTL, in milliseconds.
     */
    public long getRemainingTTL(final long now) {
        if (receiptTimeMillis == RECEIPT_TIME_NOT_SENT) {
            return ttlMillis;
        }

        long age = now - receiptTimeMillis;
        if (age > ttlMillis) {
            return 0;
        }

        return ttlMillis - age;
    }

    /**
     * Reads the record's payload from a packet.
     *
     * @param reader The reader to use.
     * @throws IOException If an I/O error occurs.
     */
    protected abstract void readData(MdnsPacketReader reader) throws IOException;

    /**
     * Write the first fields of the record, which are common fields for questions and answers.
     *
     * @param writer The writer to use.
     */
    public final void writeHeaderFields(MdnsPacketWriter writer) throws IOException {
        writer.writeLabels(name);
        writer.writeUInt16(type);
        writer.writeUInt16(cls);
    }

    /**
     * Writes the record to a packet.
     *
     * @param writer The writer to use.
     * @param now    The current system time. This is used when writing the updated TTL.
     */
    @VisibleForTesting
    public final void write(MdnsPacketWriter writer, long now) throws IOException {
        writeHeaderFields(writer);

        writer.writeUInt32(MILLISECONDS.toSeconds(getRemainingTTL(now)));

        int dataLengthPos = writer.getWritePosition();
        writer.writeUInt16(0); // data length
        int dataPos = writer.getWritePosition();

        writeData(writer);

        // Calculate amount of data written, and overwrite the data field earlier in the packet.
        int endPos = writer.getWritePosition();
        int dataLength = endPos - dataPos;
        writer.rewind(dataLengthPos);
        writer.writeUInt16(dataLength);
        writer.unrewind();
    }

    /**
     * Writes the record's payload to a packet.
     *
     * @param writer The writer to use.
     * @throws IOException If an I/O error occurs.
     */
    protected abstract void writeData(MdnsPacketWriter writer) throws IOException;

    /** Gets the status of the record. */
    public int getStatus(final long now) {
        if (receiptTimeMillis == RECEIPT_TIME_NOT_SENT) {
            return STATUS_OK;
        }
        final long age = now - receiptTimeMillis;
        if (age > ttlMillis) {
            return STATUS_EXPIRED;
        }
        if (age > (ttlMillis / 2)) {
            return STATUS_NEEDS_REFRESH;
        }
        return STATUS_OK;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (!(other instanceof MdnsRecord)) {
            return false;
        }

        MdnsRecord otherRecord = (MdnsRecord) other;

        return MdnsUtils.equalsDnsLabelIgnoreDnsCase(name, otherRecord.name) && (type
                == otherRecord.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(Arrays.hashCode(MdnsUtils.toDnsLabelsLowerCase(name)), type);
    }

    /**
     * Returns an opaque object that uniquely identifies this record through a combination of its
     * type
     * and name. Suitable for use as a key in caches.
     */
    public final Object getKey() {
        if (key == null) {
            key = new Key(type, name);
        }
        return key;
    }

    private static final class Key {
        private final int recordType;
        private final String[] recordName;

        public Key(int recordType, String[] recordName) {
            this.recordType = recordType;
            this.recordName = MdnsUtils.toDnsLabelsLowerCase(recordName);
        }

        @Override
        public boolean equals(@Nullable Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Key)) {
                return false;
            }

            Key otherKey = (Key) other;

            return (recordType == otherKey.recordType) && Arrays.equals(recordName,
                    otherKey.recordName);
        }

        @Override
        public int hashCode() {
            return (recordType * 31) + Arrays.hashCode(recordName);
        }
    }
}
