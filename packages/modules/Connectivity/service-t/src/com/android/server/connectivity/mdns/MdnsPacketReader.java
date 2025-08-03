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
import android.util.SparseArray;

import com.android.server.connectivity.mdns.MdnsServiceInfo.TextEntry;

import java.io.EOFException;
import java.io.IOException;
import java.net.DatagramPacket;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Simple decoder for mDNS packets. */
public class MdnsPacketReader {
    // The total length in bytes should be less than 255 bytes anyway (including labels and label
    // length) per RFC9267, so limit the number of labels to 128 (each label is 2 bytes with the
    // length).
    // https://www.rfc-editor.org/rfc/rfc9267.html#name-label-and-name-length-valid
    private static final int LABEL_COUNT_LIMIT = 128;
    private final byte[] buf;
    private final int count;
    private final SparseArray<LabelEntry> labelDictionary;
    private final MdnsFeatureFlags mMdnsFeatureFlags;
    private int pos;
    private int limit;

    /** Constructs a reader for the given packet. */
    public MdnsPacketReader(DatagramPacket packet) {
        this(packet.getData(), packet.getLength(), MdnsFeatureFlags.newBuilder().build());
    }

    /** Constructs a reader for the given packet. */
    public MdnsPacketReader(byte[] buffer, int length, @NonNull MdnsFeatureFlags mdnsFeatureFlags) {
        buf = buffer;
        count = length;
        pos = 0;
        limit = -1;
        labelDictionary = new SparseArray<>(16);
        mMdnsFeatureFlags = mdnsFeatureFlags;
    }

    /**
     * Sets a temporary limit (from the current read position) for subsequent reads. Any attempt to
     * read past this limit will result in an EOFException.
     *
     * @param limit The new limit.
     * @throws IOException If there is insufficient data for the new limit.
     */
    public void setLimit(int limit) throws IOException {
        if (limit >= 0) {
            if (pos + limit <= count) {
                this.limit = pos + limit;
            } else {
                throw new IOException(
                        String.format(
                                Locale.ROOT,
                                "attempt to set limit beyond available data: %d exceeds %d",
                                pos + limit,
                                count));
            }
        }
    }

    /** Clears the limit set by {@link #setLimit}. */
    public void clearLimit() {
        limit = -1;
    }

    /**
     * Returns the number of bytes left to read, between the current read position and either the
     * limit (if set) or the end of the packet.
     */
    public int getRemaining() {
        return (limit >= 0 ? limit : count) - pos;
    }

    /**
     * Reads an unsigned 8-bit integer.
     *
     * @throws EOFException If there are not enough bytes remaining in the packet to satisfy the
     *                      read.
     */
    public int readUInt8() throws EOFException {
        checkRemaining(1);
        byte val = buf[pos++];
        return val & 0xFF;
    }

    /**
     * Reads an unsigned 16-bit integer.
     *
     * @throws EOFException If there are not enough bytes remaining in the packet to satisfy the
     *                      read.
     */
    public int readUInt16() throws EOFException {
        checkRemaining(2);
        int val = (buf[pos++] & 0xFF) << 8;
        val |= (buf[pos++]) & 0xFF;
        return val;
    }

    /**
     * Reads an unsigned 32-bit integer.
     *
     * @throws EOFException If there are not enough bytes remaining in the packet to satisfy the
     *                      read.
     */
    public long readUInt32() throws EOFException {
        checkRemaining(4);
        long val = (long) (buf[pos++] & 0xFF) << 24;
        val |= (long) (buf[pos++] & 0xFF) << 16;
        val |= (long) (buf[pos++] & 0xFF) << 8;
        val |= buf[pos++] & 0xFF;
        return val;
    }

    /**
     * Reads a sequence of labels and returns them as an array of strings. A sequence of labels is
     * either a sequence of strings terminated by a NUL byte, a sequence of strings terminated by a
     * pointer, or a pointer.
     *
     * @throws EOFException If there are not enough bytes remaining in the packet to satisfy the
     *                      read.
     * @throws IOException  If invalid data is read.
     */
    public String[] readLabels() throws IOException {
        List<String> result = new ArrayList<>(5);
        LabelEntry previousEntry = null;
        int tracingHops = 0;

        while (getRemaining() > 0) {
            byte nextByte = peekByte();

            if (nextByte == 0) {
                // A NUL byte terminates a sequence of labels.
                skip(1);
                break;
            }

            int currentOffset = pos;

            boolean isLabelPointer = (nextByte & 0xC0) == 0xC0;
            if (isLabelPointer) {
                // A pointer terminates a sequence of labels. Store the pointer value in the
                // previous label entry.
                int labelOffset = ((readUInt8() & 0x3F) << 8) | (readUInt8() & 0xFF);
                if (previousEntry != null) {
                    previousEntry.nextOffset = labelOffset;
                }

                // Follow the chain of labels starting at this pointer, adding all of them onto the
                // result.
                while (labelOffset != 0) {
                    if (mMdnsFeatureFlags.mIsLabelCountLimitEnabled
                            && tracingHops > LABEL_COUNT_LIMIT) {
                        throw new IOException("Invalid MDNS response packet: Too many labels.");
                    }
                    LabelEntry entry = labelDictionary.get(labelOffset);
                    if (entry == null) {
                        throw new IOException(
                                String.format(Locale.ROOT, "Invalid label pointer: %04X",
                                        labelOffset));
                    }
                    result.add(entry.label);
                    labelOffset = entry.nextOffset;
                    tracingHops++;
                }
                break;
            } else {
                // It's an ordinary label. Chain it onto the previous label entry (if any), and add
                // it onto the result.
                String val = readString();
                LabelEntry newEntry = new LabelEntry(val);
                labelDictionary.put(currentOffset, newEntry);

                if (previousEntry != null) {
                    previousEntry.nextOffset = currentOffset;
                }
                previousEntry = newEntry;
                result.add(val);
            }
        }

        return result.toArray(new String[result.size()]);
    }

    /**
     * Reads a length-prefixed string.
     *
     * @throws EOFException If there are not enough bytes remaining in the packet to satisfy the
     *                      read.
     */
    public String readString() throws EOFException {
        int len = readUInt8();
        checkRemaining(len);
        String val = new String(buf, pos, len, MdnsConstants.getUtf8Charset());
        pos += len;
        return val;
    }

    @Nullable
    public TextEntry readTextEntry() throws EOFException {
        int len = readUInt8();
        checkRemaining(len);
        byte[] bytes = new byte[len];
        System.arraycopy(buf, pos, bytes, 0, bytes.length);
        pos += len;
        return TextEntry.fromBytes(bytes);
    }

    /**
     * Reads a specific number of bytes.
     *
     * @param bytes The array to fill.
     * @throws EOFException If there are not enough bytes remaining in the packet to satisfy the
     *                      read.
     */
    public void readBytes(byte[] bytes) throws EOFException {
        checkRemaining(bytes.length);
        System.arraycopy(buf, pos, bytes, 0, bytes.length);
        pos += bytes.length;
    }

    /**
     * Skips over the given number of bytes.
     *
     * @param count The number of bytes to read and discard.
     * @throws EOFException If there are not enough bytes remaining in the packet to satisfy the
     *                      read.
     */
    public void skip(int count) throws EOFException {
        checkRemaining(count);
        pos += count;
    }

    /**
     * Peeks at and returns the next byte in the packet, without advancing the read position.
     *
     * @throws EOFException If there are not enough bytes remaining in the packet to satisfy the
     *                      read.
     */
    public byte peekByte() throws EOFException {
        checkRemaining(1);
        return buf[pos];
    }

    /** Returns the current byte position of the reader for the data packet. */
    public int getPosition() {
        return pos;
    }

    // Checks if the number of remaining bytes to be read in the packet is at least |count|.
    private void checkRemaining(int count) throws EOFException {
        if (getRemaining() < count) {
            throw new EOFException();
        }
    }

    private static class LabelEntry {
        public final String label;
        public int nextOffset = 0;

        public LabelEntry(String label) {
            this.label = label;
        }
    }
}
