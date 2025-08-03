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

import com.android.server.connectivity.mdns.MdnsServiceInfo.TextEntry;
import com.android.server.connectivity.mdns.util.MdnsUtils;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.SocketAddress;
import java.util.HashMap;
import java.util.Map;

/** Simple encoder for mDNS packets. */
public class MdnsPacketWriter {
    private static final int MDNS_POINTER_MASK = 0xC000;
    private final byte[] data;
    private final Map<Integer, String[]> labelDictionary = new HashMap<>();
    private int pos = 0;
    private int savedWritePos = -1;

    /**
     * Constructs a writer for a new packet.
     *
     * @param maxSize The maximum size of a packet.
     */
    public MdnsPacketWriter(int maxSize) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("invalid size");
        }

        data = new byte[maxSize];
    }

    /**
     * Constructs a writer for a new packet.
     *
     * @param buffer The buffer to write to.
     */
    public MdnsPacketWriter(byte[] buffer) {
        data = buffer;
    }

    /** Returns the current write position. */
    public int getWritePosition() {
        return pos;
    }

    /**
     * Saves the current write position and then rewinds the write position by the given number of
     * bytes. This is useful for updating length fields earlier in the packet. Rewinds cannot be
     * nested.
     *
     * @param position The position to rewind to.
     * @throws IOException If the count would go beyond the beginning of the packet, or if there is
     *                     already a rewind in effect.
     */
    public void rewind(int position) throws IOException {
        if ((savedWritePos != -1) || (position > pos) || (position < 0)) {
            throw new IOException("invalid rewind");
        }

        savedWritePos = pos;
        pos = position;
    }

    /**
     * Sets the current write position to what it was prior to the last rewind.
     *
     * @throws IOException If there was no rewind in effect.
     */
    public void unrewind() throws IOException {
        if (savedWritePos == -1) {
            throw new IOException("no rewind is in effect");
        }
        pos = savedWritePos;
        savedWritePos = -1;
    }

    /** Clears any rewind state. */
    public void clearRewind() {
        savedWritePos = -1;
    }

    /**
     * Writes an unsigned 8-bit integer.
     *
     * @param value The value to write.
     * @throws IOException If there is not enough space remaining in the packet.
     */
    public void writeUInt8(int value) throws IOException {
        checkRemaining(1);
        data[pos++] = (byte) (value & 0xFF);
    }

    /**
     * Writes an unsigned 16-bit integer.
     *
     * @param value The value to write.
     * @throws IOException If there is not enough space remaining in the packet.
     */
    public void writeUInt16(int value) throws IOException {
        checkRemaining(2);
        data[pos++] = (byte) ((value >>> 8) & 0xFF);
        data[pos++] = (byte) (value & 0xFF);
    }

    /**
     * Writes an unsigned 32-bit integer.
     *
     * @param value The value to write.
     * @throws IOException If there is not enough space remaining in the packet.
     */
    public void writeUInt32(long value) throws IOException {
        checkRemaining(4);
        data[pos++] = (byte) ((value >>> 24) & 0xFF);
        data[pos++] = (byte) ((value >>> 16) & 0xFF);
        data[pos++] = (byte) ((value >>> 8) & 0xFF);
        data[pos++] = (byte) (value & 0xFF);
    }

    /**
     * Writes a specific number of bytes.
     *
     * @param data The array to write.
     * @throws IOException If there is not enough space remaining in the packet.
     */
    public void writeBytes(byte[] data) throws IOException {
        checkRemaining(data.length);
        System.arraycopy(data, 0, this.data, pos, data.length);
        pos += data.length;
    }

    /**
     * Writes a string.
     *
     * @param value The string to write.
     * @throws IOException If there is not enough space remaining in the packet.
     */
    public void writeString(String value) throws IOException {
        byte[] utf8 = value.getBytes(MdnsConstants.getUtf8Charset());
        writeUInt8(utf8.length);
        writeBytes(utf8);
    }

    public void writeTextEntry(TextEntry textEntry) throws IOException {
        byte[] bytes = textEntry.toBytes();
        writeUInt8(bytes.length);
        writeBytes(bytes);
    }

    /**
     * Writes a series of labels. Uses name compression.
     *
     * @param labels The labels to write.
     * @throws IOException If there is not enough space remaining in the packet.
     */
    public void writeLabels(String[] labels) throws IOException {
        // See section 4.1.4 of RFC 1035 (http://tools.ietf.org/html/rfc1035) for a description
        // of the name compression method used here.

        int suffixLength = 0;
        int suffixPointer = 0;

        for (Map.Entry<Integer, String[]> entry : labelDictionary.entrySet()) {
            int existingOffset = entry.getKey();
            String[] existingLabels = entry.getValue();

            if (MdnsUtils.equalsDnsLabelIgnoreDnsCase(existingLabels, labels)) {
                writePointer(existingOffset);
                return;
            } else if (MdnsRecord.labelsAreSuffix(existingLabels, labels)) {
                // Keep track of the longest matching suffix so far.
                if (existingLabels.length > suffixLength) {
                    suffixLength = existingLabels.length;
                    suffixPointer = existingOffset;
                }
            }
        }

        final int[] offsets;
        if (suffixLength > 0) {
            offsets = writePartialLabelsNoCompression(labels, labels.length - suffixLength);
            writePointer(suffixPointer);
        } else {
            offsets = writeLabelsNoCompression(labels);
        }

        // Add entries to the label dictionary for each suffix of the label list, including
        // the whole list itself.
        // Do not replace the last suffixLength suffixes that already have dictionary entries.
        for (int i = 0, len = labels.length; i < labels.length - suffixLength; ++i, --len) {
            String[] value = new String[len];
            System.arraycopy(labels, i, value, 0, len);
            labelDictionary.put(offsets[i], value);
        }
    }

    private int[] writePartialLabelsNoCompression(String[] labels, int count) throws IOException {
        int[] offsets = new int[count];
        for (int i = 0; i < count; ++i) {
            offsets[i] = getWritePosition();
            writeString(labels[i]);
        }
        return offsets;
    }

    /**
     * Write a series a labels, without using name compression.
     *
     * @return The offsets where each label was written to.
     */
    public int[] writeLabelsNoCompression(String[] labels) throws IOException {
        final int[] offsets = writePartialLabelsNoCompression(labels, labels.length);
        writeUInt8(0); // NUL terminator
        return offsets;
    }

    /** Returns the number of bytes that can still be written. */
    public int getRemaining() {
        return data.length - pos;
    }

    // Writes a pointer to a label.
    private void writePointer(int offset) throws IOException {
        writeUInt16(MDNS_POINTER_MASK | offset);
    }

    // Checks if the remaining space in the packet is at least |count|.
    private void checkRemaining(int count) throws IOException {
        if (getRemaining() < count) {
            throw new IOException();
        }
    }

    /** Builds and returns the packet. */
    public DatagramPacket getPacket(SocketAddress destAddress) throws IOException {
        return new DatagramPacket(data, pos, destAddress);
    }
}
