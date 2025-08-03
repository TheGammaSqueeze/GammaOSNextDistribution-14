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

import android.net.DnsResolver;
import android.text.TextUtils;

import com.android.net.module.util.CollectionUtils;
import com.android.server.connectivity.mdns.util.MdnsUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

/**
 * A mDNS "NSEC" record, used in particular for negative responses (RFC6762 6.1).
 */
public class MdnsNsecRecord extends MdnsRecord {
    private String[] mNextDomain;
    private int[] mTypes;

    public MdnsNsecRecord(String[] name, MdnsPacketReader reader) throws IOException {
        this(name, reader, false);
    }

    public MdnsNsecRecord(String[] name, MdnsPacketReader reader, boolean isQuestion)
            throws IOException {
        super(name, TYPE_NSEC, reader, isQuestion);
    }

    public MdnsNsecRecord(String[] name, long receiptTimeMillis, boolean cacheFlush, long ttlMillis,
            String[] nextDomain, int[] types) {
        super(name, TYPE_NSEC, DnsResolver.CLASS_IN, receiptTimeMillis, cacheFlush, ttlMillis);
        mNextDomain = nextDomain;
        final int[] sortedTypes = Arrays.copyOf(types, types.length);
        Arrays.sort(sortedTypes);
        mTypes = sortedTypes;
    }

    public String[] getNextDomain() {
        return mNextDomain;
    }

    public int[] getTypes() {
        return mTypes;
    }

    @Override
    protected void readData(MdnsPacketReader reader) throws IOException {
        mNextDomain = reader.readLabels();
        mTypes = readTypes(reader);
    }

    private int[] readTypes(MdnsPacketReader reader) throws IOException {
        // See RFC3845 #2.1.2
        final ArrayList<Integer> types = new ArrayList<>();
        int prevBlockNumber = -1;
        while (reader.getRemaining() > 0) {
            final int blockNumber = reader.readUInt8();
            if (blockNumber <= prevBlockNumber) {
                throw new IOException(
                        "Unordered block number: " + blockNumber + " after " + prevBlockNumber);
            }
            prevBlockNumber = blockNumber;
            final int bitmapLength = reader.readUInt8();
            if (bitmapLength > 32 || bitmapLength <= 0) {
                throw new IOException("Invalid bitmap length: " + bitmapLength);
            }
            final byte[] bitmap = new byte[bitmapLength];
            reader.readBytes(bitmap);

            for (int bitmapIndex = 0; bitmapIndex < bitmap.length; bitmapIndex++) {
                final byte bitmapByte = bitmap[bitmapIndex];
                for (int bit = 0; bit < 8; bit++) {
                    if ((bitmapByte & (1 << (7 - bit))) != 0) {
                        types.add(blockNumber * 256 + bitmapIndex * 8 + bit);
                    }
                }
            }
        }

        return CollectionUtils.toIntArray(types);
    }

    @Override
    protected void writeData(MdnsPacketWriter writer) throws IOException {
        // Standard NSEC records should use no compression for the Next Domain Name field as per
        // RFC3845 2.1.1, but for mDNS RFC6762 18.14 specifies that compression should be used.
        writer.writeLabels(mNextDomain);

        // type bitmaps: RFC3845 2.1.2
        int typesBlockStart = 0;
        int pendingBlockNumber = -1;
        int blockLength = 0;
        // Loop on types (which are sorted in increasing order) to find each block and determine
        // their length; use writeTypeBlock once the length of each block has been found.
        for (int i = 0; i < mTypes.length; i++) {
            final int blockNumber = mTypes[i] / 256;
            final int typeLowOrder = mTypes[i] % 256;
            // If the low-order 8 bits are e.g. 0x10, bit number 16 (=0x10) will be set in the
            // bitmap; this is the first bit of byte 2 (byte 0 is 0-7, 1 is 8-15, etc.)
            final int byteIndex = typeLowOrder / 8;

            if (pendingBlockNumber >= 0 && blockNumber != pendingBlockNumber) {
                // Just reached a new block; write the previous one
                writeTypeBlock(writer, typesBlockStart, i - 1, blockLength);
                typesBlockStart = i;
                blockLength = 0;
            }
            blockLength = Math.max(blockLength, byteIndex + 1);
            pendingBlockNumber = blockNumber;
        }

        if (pendingBlockNumber >= 0) {
            writeTypeBlock(writer, typesBlockStart, mTypes.length - 1, blockLength);
        }
    }

    private void writeTypeBlock(MdnsPacketWriter writer,
            int typesStart, int typesEnd, int bytesInBlock) throws IOException {
        final int blockNumber = mTypes[typesStart] / 256;
        final byte[] bytes = new byte[bytesInBlock];
        for (int i = typesStart; i <= typesEnd; i++) {
            final int typeLowOrder = mTypes[i] % 256;
            bytes[typeLowOrder / 8] |= 1 << (7 - (typeLowOrder % 8));
        }
        writer.writeUInt8(blockNumber);
        writer.writeUInt8(bytesInBlock);
        writer.writeBytes(bytes);
    }

    @Override
    public String toString() {
        return "NSEC: NextDomain: " + TextUtils.join(".", mNextDomain)
                + " Types " + Arrays.toString(mTypes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(),
                Arrays.hashCode(MdnsUtils.toDnsLabelsLowerCase(mNextDomain)),
                Arrays.hashCode(mTypes));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MdnsNsecRecord)) {
            return false;
        }

        return super.equals(other)
                && MdnsUtils.equalsDnsLabelIgnoreDnsCase(mNextDomain,
                ((MdnsNsecRecord) other).mNextDomain)
                && Arrays.equals(mTypes, ((MdnsNsecRecord) other).mTypes);
    }
}
