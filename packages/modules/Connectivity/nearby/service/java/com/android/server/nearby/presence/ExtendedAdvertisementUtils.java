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

import static com.android.server.nearby.presence.ExtendedAdvertisement.HEADER_LENGTH;

import android.annotation.SuppressLint;
import android.nearby.BroadcastRequest;
import android.nearby.DataElement;

import com.android.internal.util.Preconditions;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides serialization and deserialization util methods for {@link ExtendedAdvertisement}.
 */
public final class ExtendedAdvertisementUtils {

    // Advertisement header related static fields.
    private static final int VERSION_MASK = 0b11100000;
    private static final int VERSION_MASK_AFTER_SHIT = 0b00000111;
    private static final int HEADER_INDEX = 0;
    private static final int HEADER_VERSION_OFFSET = 5;

    /**
     * Constructs the header of a {@link ExtendedAdvertisement}.
     * 3 bit version, and 5 bit reserved for future use (RFU).
     */
    public static byte constructHeader(@BroadcastRequest.BroadcastVersion int version) {
        return (byte) ((version << 5) & VERSION_MASK);
    }

    /** Returns the {@link BroadcastRequest.BroadcastVersion} from the advertisement
     * in bytes format. */
    public static int getVersion(byte[] advertisement) {
        if (advertisement.length < HEADER_LENGTH) {
            throw new IllegalArgumentException("Advertisement must contain header");
        }
        return ((advertisement[HEADER_INDEX] & VERSION_MASK) >> HEADER_VERSION_OFFSET)
                & VERSION_MASK_AFTER_SHIT;
    }

    /** Returns the {@link DataElementHeader} from the advertisement in bytes format. */
    public static byte[] getDataElementHeader(byte[] advertisement, int startIndex) {
        Preconditions.checkArgument(startIndex < advertisement.length,
                "Advertisement has no longer data left.");
        List<Byte> headerBytes = new ArrayList<>();
        while (startIndex < advertisement.length) {
            byte current = advertisement[startIndex];
            headerBytes.add(current);
            if (!DataElementHeader.isExtending(current)) {
                int size = headerBytes.size();
                byte[] res = new byte[size];
                for (int i = 0; i < size; i++) {
                    res[i] = headerBytes.get(i);
                }
                return res;
            }
            startIndex++;
        }
        throw new IllegalArgumentException("There is no end of the DataElement header.");
    }

    /**
     * Constructs {@link DataElement}, including header(s) and actual data element data.
     *
     * Suppresses warning because {@link DataElement} checks isValidType in constructor.
     */
    @SuppressLint("WrongConstant")
    public static byte[] convertDataElementToBytes(DataElement dataElement) {
        @DataElement.DataType int type = dataElement.getKey();
        byte[] data = dataElement.getValue();
        DataElementHeader header = new DataElementHeader(BroadcastRequest.PRESENCE_VERSION_V1,
                type, data.length);
        byte[] headerByteArray = header.toBytes();

        byte[] res = new byte[headerByteArray.length + data.length];
        System.arraycopy(headerByteArray, 0, res, 0, headerByteArray.length);
        System.arraycopy(data, 0, res, headerByteArray.length, data.length);
        return res;
    }

    private ExtendedAdvertisementUtils() {}
}
