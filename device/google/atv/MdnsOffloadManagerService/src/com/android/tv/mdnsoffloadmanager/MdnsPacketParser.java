/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.tv.mdnsoffloadmanager;

import androidx.annotation.NonNull;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import device.google.atv.mdns_offload.IMdnsOffload.MdnsProtocolData.MatchCriteria;

/**
 * Tool class to help read mdns data from a fully formed mDNS response packet.
 */
public final class MdnsPacketParser {

    private static final int OFFSET_QUERIES_COUNT = 4;
    private static final int OFFSET_ANSWERS_COUNT = 6;
    private static final int OFFSET_AUTHORITY_COUNT = 8;
    private static final int OFFSET_ADDITIONAL_COUNT = 10;
    private static final int OFFSET_DATA_SECTION_START = 12;

    private final byte[] mMdnsData;
    private int mCursorIndex;

    private MdnsPacketParser(@NonNull byte[] mDNSData) {
        this.mMdnsData = mDNSData;
    }

    /**
     * Extracts a label starting at offset and then follows RFC1035-4.1.4 The offset should start
     * either at a data length value or at a pointer value.
     */
    public static String extractFullName(@NonNull byte[] array, int offset) {
        MdnsPacketParser parser = new MdnsPacketParser(array);
        parser.setCursor(offset);
        StringBuilder builder = new StringBuilder();

        while (!parser.isCursorOnRootLabel()) {
            if (parser.isCursorOnPointer()) {
                parser.setCursor(parser.pollPointerOffset());
            } else if (parser.isCursorOnLabel()) {
                builder.append(parser.pollLabel());
                builder.append('.');
            } else {
                throw new IllegalArgumentException("mDNS response packet is badly formed.");
            }
        }
        return builder.toString();
    }

    /**
     * Finds all the RRNAMEs ans RRTYPEs in the mdns response packet provided. Expects a packet only
     * with responses.
     */
    public static List<MatchCriteria> extractMatchCriteria(@NonNull byte[] mdnsResponsePacket) {
        Objects.requireNonNull(mdnsResponsePacket);

        // Parse MdnsPacket and read labels and find
        List<MatchCriteria> criteriaList = new ArrayList<>();
        MdnsPacketParser parser = new MdnsPacketParser(mdnsResponsePacket);

        if (parser.getQueriesCount() != 0
                || parser.getAuthorityCount() != 0
                || parser.getAdditionalCount() != 0) {
            throw new IllegalArgumentException(
                    "mDNS response packet contains data that is not answers");
        }
        int answersToRead = parser.getAnswersCount();

        parser.moveToDataSection();
        while (answersToRead > 0) {
            // Each record starts with the RRNAME, so the offset is correct for the criteria.
            MatchCriteria criteria = new MatchCriteria();
            criteria.nameOffset = parser.getCursorOffset();

            /// Skip labels first
            while (parser.isCursorOnLabel()) {
                parser.pollLabel();
            }
            // We can be on a root label or on a pointer. Skip both.
            if (parser.isCursorOnRootLabel()) {
                parser.skipBytes(1);
            } else if (parser.isCursorOnPointer()) {
                parser.pollPointerOffset();
            }

            // The cursor must be on the RRTYPE.
            criteria.type = parser.pollUint16();

            // The next 6 bytes point to cache flush, rrclass, and ttl
            parser.skipBytes(6);

            // Now the index points to the data length on 2 bytes
            int dataLength = parser.pollUint16();

            // Then we can skip those data bytes.
            parser.skipBytes(dataLength);

            // Criteria is complete, it can be added.
            criteriaList.add(criteria);
            answersToRead--;
        }
        if (parser.hasContent()) {
            // The packet is badly formed. All answers where read successfully, but data remains
            // available.
            throw new IllegalArgumentException(
                    "mDNS response packet is badly formed. Too much data.");
        }

        return criteriaList;
    }

    private boolean hasContent() {
        return mCursorIndex < mMdnsData.length;
    }

    private void setCursor(int position) {
        if (position < 0) {
            throw new IllegalArgumentException("Setting cursor on negative offset is not allowed.");
        }
        mCursorIndex = position;
    }

    private int getCursorOffset() {
        return mCursorIndex;
    }

    private void skipBytes(int bytesToSkip) {
        mCursorIndex += bytesToSkip;
    }

    private int getQueriesCount() {
        return readUint16(OFFSET_QUERIES_COUNT);
    }

    private int getAnswersCount() {
        return readUint16(OFFSET_ANSWERS_COUNT);
    }

    private int getAuthorityCount() {
        return readUint16(OFFSET_AUTHORITY_COUNT);
    }

    private int getAdditionalCount() {
        return readUint16(OFFSET_ADDITIONAL_COUNT);
    }

    private void moveToDataSection() {
        mCursorIndex = OFFSET_DATA_SECTION_START;
    }

    private String pollLabel() {
        int labelSize = readUint8(mCursorIndex);
        mCursorIndex++;
        if (mCursorIndex + labelSize > mMdnsData.length) {
            throw new IllegalArgumentException(
                    "mDNS response packet is badly formed. Not enough data.");
        }
        String value = new String(mMdnsData, mCursorIndex, labelSize, StandardCharsets.UTF_8);
        mCursorIndex += labelSize;
        return value;
    }

    private boolean isCursorOnLabel() {
        return !isCursorOnRootLabel() && (readUint8(mCursorIndex) & 0b11000000) == 0b00000000;
    }

    private boolean isCursorOnPointer() {
        return (readUint8(mCursorIndex) & 0b11000000) == 0b11000000;
    }

    private boolean isCursorOnRootLabel() {
        return readUint8(mCursorIndex) == 0;
    }

    private int pollPointerOffset() {
        int value = readUint16(mCursorIndex) & 0b0011111111111111;
        mCursorIndex += 2;
        return value;
    }

    private int readUint8(int offset) {
        if (offset >= mMdnsData.length) {
            throw new IllegalArgumentException(
                    "mDNS response packet is badly formed. Not enough data.");
        }
        return ((int) mMdnsData[offset]) & 0xff;
    }

    private int readUint16(int offset) {
        if (offset + 1 >= mMdnsData.length) {
            throw new IllegalArgumentException(
                    "mDNS response packet is badly formed. Not enough data.");
        }
        return (readUint8(offset) << 8) + readUint8(offset + 1);
    }

    private int pollUint16() {
        int value = readUint16(mCursorIndex);
        mCursorIndex += 2;
        return value;
    }
}
