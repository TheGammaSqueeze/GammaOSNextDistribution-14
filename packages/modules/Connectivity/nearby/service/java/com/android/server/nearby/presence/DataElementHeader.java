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

package com.android.server.nearby.presence;

import android.annotation.Nullable;
import android.nearby.BroadcastRequest;
import android.nearby.DataElement;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nonnull;

/**
 * Represents a data element header in Nearby Presence.
 * Each header has 3 parts: tag, length and style.
 * Tag: 1 bit (MSB at each byte). 1 for extending, which means there will be more bytes after
 * the current one for the header.
 * Length: The total length of a Data Element field. Length is up to 127 and is limited within
 * the entire first byte in the header. (7 bits, MSB is the tag).
 * Type: Represents {@link DataElement.DataType}. There is no limit for the type number.
 *
 * @hide
 */
public class DataElementHeader {
    // Each Data reserved MSB for tag.
    static final int TAG_BITMASK = 0b10000000;
    static final int TAG_OFFSET = 7;

    // If the header is only 1 byte, it has the format: 0b0LLLTTTT. (L for length, T for type.)
    static final int SINGLE_AVAILABLE_LENGTH_BIT = 3;
    static final int SINGLE_AVAILABLE_TYPE_BIT = 4;
    static final int SINGLE_LENGTH_BITMASK = 0b01110000;
    static final int SINGLE_LENGTH_OFFSET = SINGLE_AVAILABLE_TYPE_BIT;
    static final int SINGLE_TYPE_BITMASK = 0b00001111;

    // If there are multiple data element headers.
    // First byte is always the length.
    static final int MULTIPLE_LENGTH_BYTE = 1;
    // Each byte reserves MSB for tag.
    static final int MULTIPLE_BITMASK = 0b01111111;

    @BroadcastRequest.BroadcastVersion
    private final int mVersion;
    @DataElement.DataType
    private final int mDataType;
    private final int mDataLength;

    DataElementHeader(@BroadcastRequest.BroadcastVersion int version,
            @DataElement.DataType int dataType, int dataLength) {
        Preconditions.checkArgument(version == BroadcastRequest.PRESENCE_VERSION_V1,
                "DataElementHeader is only supported in V1.");
        Preconditions.checkArgument(dataLength >= 0, "Length should not be negative.");
        Preconditions.checkArgument(dataLength < (1 << TAG_OFFSET),
                "Data element should be equal or shorter than 128.");

        this.mVersion = version;
        this.mDataType = dataType;
        this.mDataLength = dataLength;
    }

    /**
     * The total type of the data element.
     */
    @DataElement.DataType
    public int getDataType() {
        return mDataType;
    }

    /**
     * The total length of a Data Element field.
     */
    public int getDataLength() {
        return mDataLength;
    }

    /** Serialize a {@link DataElementHeader} object into bytes. */
    public byte[] toBytes() {
        Preconditions.checkState(mVersion == BroadcastRequest.PRESENCE_VERSION_V1,
                "DataElementHeader is only supported in V1.");
        // Only 1 byte needed for the header
        if (mDataType < (1 << SINGLE_AVAILABLE_TYPE_BIT)
                && mDataLength < (1 << SINGLE_AVAILABLE_LENGTH_BIT)) {
            return new byte[]{createSingleByteHeader(mDataType, mDataLength)};
        }

        return createMultipleBytesHeader(mDataType, mDataLength);
    }

    /** Creates a {@link DataElementHeader} object from bytes. */
    @Nullable
    public static DataElementHeader fromBytes(@BroadcastRequest.BroadcastVersion int version,
            @Nonnull byte[] bytes) {
        Objects.requireNonNull(bytes, "Data parsed in for DataElement should not be null.");

        if (bytes.length == 0) {
            return null;
        }

        if (bytes.length == 1) {
            if (isExtending(bytes[0])) {
                throw new IllegalArgumentException("The header is not complete.");
            }
            return new DataElementHeader(BroadcastRequest.PRESENCE_VERSION_V1,
                    getTypeSingleByte(bytes[0]), getLengthSingleByte(bytes[0]));
        }

        // The first byte should be length and there should be at least 1 more byte following to
        // represent type.
        // The last header byte's MSB should be 0.
        if (!isExtending(bytes[0]) || isExtending(bytes[bytes.length - 1])) {
            throw new IllegalArgumentException("The header format is wrong.");
        }

        return new DataElementHeader(version,
                getTypeMultipleBytes(Arrays.copyOfRange(bytes, 1, bytes.length)),
                getHeaderValue(bytes[0]));
    }

    /** Creates a header based on type and length.
     * This is used when the type is <= 16 and length is <= 7. */
    static byte createSingleByteHeader(int type, int length) {
        return (byte) (convertTag(/* extend= */ false)
                | convertLengthSingleByte(length)
                | convertTypeSingleByte(type));
    }

    /** Creates a header based on type and length.
     * This is used when the type is > 16 or length is > 7. */
    static byte[] createMultipleBytesHeader(int type, int length) {
        List<Byte> typeIntList = convertTypeMultipleBytes(type);
        byte[] res = new byte[typeIntList.size() + MULTIPLE_LENGTH_BYTE];
        int index = 0;
        res[index++] = convertLengthMultipleBytes(length);

        for (int typeInt : typeIntList) {
            res[index++] = (byte) typeInt;
        }
        return res;
    }

    /** Constructs a Data Element header with length indicated in byte format.
     * The most significant bit is the tag, 2- 4 bits are the length, 5 - 8 bits are the type.
     */
    @VisibleForTesting
    static int convertLengthSingleByte(int length) {
        Preconditions.checkArgument(length >= 0, "Length should not be negative.");
        Preconditions.checkArgument(length < (1 << SINGLE_AVAILABLE_LENGTH_BIT),
                "In single Data Element header, length should be shorter than 8.");
        return (length << SINGLE_LENGTH_OFFSET) & SINGLE_LENGTH_BITMASK;
    }

    /** Constructs a Data Element header with type indicated in byte format.
     * The most significant bit is the tag, 2- 4 bits are the length, 5 - 8 bits are the type.
     */
    @VisibleForTesting
    static int convertTypeSingleByte(int type) {
        Preconditions.checkArgument(type >= 0, "Type should not be negative.");
        Preconditions.checkArgument(type < (1 << SINGLE_AVAILABLE_TYPE_BIT),
                "In single Data Element header, type should be smaller than 16.");

        return type & SINGLE_TYPE_BITMASK;
    }

    /**
     * Gets the length of Data Element from the header. (When there is only 1 byte of header)
     */
    static int getLengthSingleByte(byte header) {
        Preconditions.checkArgument(!isExtending(header),
                "Cannot apply this method for the extending header.");
        return (header & SINGLE_LENGTH_BITMASK) >> SINGLE_LENGTH_OFFSET;
    }

    /**
     * Gets the type of Data Element from the header. (When there is only 1 byte of header)
     */
    static int getTypeSingleByte(byte header) {
        Preconditions.checkArgument(!isExtending(header),
                "Cannot apply this method for the extending header.");
        return header & SINGLE_TYPE_BITMASK;
    }

    /** Creates a DE(data element) header based on length.
     * This is used when header is more than 1 byte. The first byte is always the length.
     */
    static byte convertLengthMultipleBytes(int length) {
        Preconditions.checkArgument(length < (1 << TAG_OFFSET),
                "Data element should be equal or shorter than 128.");
        return (byte) (convertTag(/* extend= */ true) | (length & MULTIPLE_BITMASK));
    }

    /** Creates a DE(data element) header based on type.
     * This is used when header is more than 1 byte. The first byte is always the length.
     */
    @VisibleForTesting
    static List<Byte> convertTypeMultipleBytes(int type) {
        List<Byte> typeBytes = new ArrayList<>();
        while (type > 0) {
            byte current = (byte) (type & MULTIPLE_BITMASK);
            type = type >> TAG_OFFSET;
            typeBytes.add(current);
        }

        Collections.reverse(typeBytes);
        int size = typeBytes.size();
        // The last byte's MSB should be 0.
        for (int i = 0; i < size - 1; i++) {
            typeBytes.set(i, (byte) (convertTag(/* extend= */ true) | typeBytes.get(i)));
        }
        return typeBytes;
    }

    /** Creates a DE(data element) header based on type.
     * This is used when header is more than 1 byte. The first byte is always the length.
     * Uses Integer when doing bit operation to avoid error.
     */
    @VisibleForTesting
    static int getTypeMultipleBytes(byte[] typeByteArray) {
        int type = 0;
        int size = typeByteArray.length;
        for (int i = 0; i < size; i++) {
            type = (type << TAG_OFFSET) | getHeaderValue(typeByteArray[i]);
        }
        return type;
    }

    /** Gets the integer value of the 7 bits in the header. (The MSB is tag) */
    @VisibleForTesting
    static int getHeaderValue(byte header) {
        return (header & MULTIPLE_BITMASK);
    }

    /** Sets the MSB of the header byte. If this is the last byte of headers, MSB is 0.
     * If there are at least header following, the MSB is 1.
     */
    @VisibleForTesting
    static byte convertTag(boolean extend) {
        return (byte) (extend ? 0b10000000 : 0b00000000);
    }

    /** Returns {@code true} if there are at least 1 byte of header after the current one. */
    @VisibleForTesting
    static boolean isExtending(byte header) {
        return (header & TAG_BITMASK) != 0;
    }
}
