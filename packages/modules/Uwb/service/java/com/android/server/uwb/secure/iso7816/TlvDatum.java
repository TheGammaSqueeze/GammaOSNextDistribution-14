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
package com.android.server.uwb.secure.iso7816;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.server.uwb.util.DataTypeConversionUtil;
import com.android.server.uwb.util.Hex;

import com.google.common.primitives.Bytes;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// TODO(b/214552182): refactor to be compatible with TlvBuffer.
/**
 * Wrapper on a singular datum stored in a TLV byte string. Defined in section 6.2 of
 * ISO/IEC 7816-4: 2020 standard.
 */
public class TlvDatum {
    private static final String LOG_TAG = "TlvDatum";

    public static final long MAX_SIZE_SINGLE_BYTE = 0x7FL;
    public static final long MAX_SIZE_TWO_BYTE = 0xFFL;
    public static final long MAX_SIZE_THREE_BYTE = 0xFFFFL;
    public static final long MAX_SIZE_FOUR_BYTE = 0xFFFFFFL;
    public static final long MAX_SIZE_FIVE_BYTE = 0xFFFFFFFFL;

    public static final byte TWO_BYTES_LEN_FIRST_BYTE = (byte) 0x81;
    public static final byte THREE_BYTES_LEN_FIRST_BYTE = (byte) 0x82;
    public static final byte FOUR_BYTES_LEN_FIRST_BYTE = (byte) 0x83;
    public static final byte FIVE_BYTES_LEN_FIRST_BYTE = (byte) 0x84;

    // The tag of the TLV structure.
    @NonNull public final Tag tag;
    // For constructed data objects, this will be the byte representation of subTlvData
    @NonNull public final byte[] value;
    // Will only be non-empty for constructed (i.e., non-primitive) data objects.
    @NonNull public final Map<Tag, List<TlvDatum>> subTlvData;

    /**
     * Constructor of TlvDatum.
     */
    public TlvDatum(@NonNull Tag tag, @NonNull Map<Tag, List<TlvDatum>> subTlvData) {
        this.tag = tag;
        this.subTlvData = subTlvData;

        byte[] value = new byte[] {};
        for (Map.Entry<Tag, List<TlvDatum>> subTlvs : subTlvData.entrySet()) {
            for (TlvDatum subTlvDatum : subTlvs.getValue()) {
                value = Bytes.concat(value, subTlvDatum.toBytes());
            }
        }

        this.value = value;
    }

    /**
     * Constructor of TlvDatum with a sub TlvDatum.
     */
    public TlvDatum(@NonNull Tag tag, @NonNull TlvDatum subTlvDatum) {
        this.tag = tag;
        this.value = subTlvDatum.toBytes();
        this.subTlvData = new HashMap<Tag, List<TlvDatum>>();
        subTlvData.put(subTlvDatum.tag, Arrays.asList(subTlvDatum));
    }

    /**
     * Constructor of TlvDatum.
     */
    public TlvDatum(@NonNull Tag tag, @NonNull byte[] value) {
        this.tag = tag;
        this.value = value;
        this.subTlvData = new HashMap<Tag, List<TlvDatum>>();
    }

    /**
     * Constructor of TlvDatum.
     */
    public TlvDatum(@NonNull Tag tag, int value) {
        this.tag = tag;
        this.value = DataTypeConversionUtil.i32ToByteArray(value);
        this.subTlvData = new HashMap<Tag, List<TlvDatum>>();
    }

    /**
     * Convert the TLV to byte array.
     */
    public byte[] toBytes() {
        // determine number of bytes to use for length
        int sizeByteLength = 1;
        if (value.length > MAX_SIZE_FIVE_BYTE) {
            Log.wtf(LOG_TAG, "The length of data is over limit for tag: " + tag);
        }
        if (value.length > MAX_SIZE_FOUR_BYTE) {
            sizeByteLength = 5;
        } else if (value.length > MAX_SIZE_THREE_BYTE) {
            sizeByteLength = 4;
        } else if (value.length > MAX_SIZE_TWO_BYTE) {
            sizeByteLength = 3;
        } else if (value.length > MAX_SIZE_SINGLE_BYTE) {
            sizeByteLength = 2;
        }

        return Bytes.concat(tag.literalValue, lengthToBytes(sizeByteLength, value.length), value);
    }

    private static byte[] lengthToBytes(int sizeByteLength, int size) {
        switch (sizeByteLength) {
            case 1:
                return new byte[] {DataTypeConversionUtil.unsignedIntToByte(size)};

            case 2:
                return new byte[] {TWO_BYTES_LEN_FIRST_BYTE,
                        DataTypeConversionUtil.unsignedIntToByte(size)};

            case 3:
                return new byte[] {
                        THREE_BYTES_LEN_FIRST_BYTE,
                        DataTypeConversionUtil.unsignedIntToByte(size >> 8),
                        DataTypeConversionUtil.unsignedIntToByte(size)
                };

            case 4:
                return new byte[] {
                        FOUR_BYTES_LEN_FIRST_BYTE,
                        DataTypeConversionUtil.unsignedIntToByte(size >> 16),
                        DataTypeConversionUtil.unsignedIntToByte(size >> 8),
                        DataTypeConversionUtil.unsignedIntToByte(size)
                };

            case 5:
                return new byte[] {
                        FIVE_BYTES_LEN_FIRST_BYTE,
                        DataTypeConversionUtil.unsignedIntToByte(size >> 24),
                        DataTypeConversionUtil.unsignedIntToByte(size >> 16),
                        DataTypeConversionUtil.unsignedIntToByte(size >> 8),
                        DataTypeConversionUtil.unsignedIntToByte(size)
                };

            default:
                throw new IndexOutOfBoundsException(
                        "length of " + sizeByteLength + " not supported");
        }
    }

    @Override
    public String toString() {
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);

        printWriter.printf("TlvDatum : TAG=[%s], VALUE=[%s]",
                Hex.encode(tag.literalValue), Hex.encode(value));

        return stringWriter.toString();
    }

    /**
     * The Tag of TLV(Tag, Length, Value) data structure.
     */
    public static class Tag {
        @NonNull
        public final byte[] literalValue;
        public Tag(@NonNull byte[] literalValue) {
            this.literalValue = literalValue;
        }

        public Tag(byte value) {
            this.literalValue = new byte[] { value };
        }

        public Tag(byte firstByte, byte secondByte) {
            this.literalValue = new byte[] {firstByte, secondByte};
        }

        @Override
        public String toString() {
            return DataTypeConversionUtil.byteArrayToHexString(literalValue);
        }

        @Override
        public boolean equals(@Nullable Object that) {
            if (this == that) {
                return true;
            }
            if (that == null || that.getClass() != this.getClass()) {
                return false;
            }

            return Arrays.equals(literalValue, ((Tag) that).literalValue);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(literalValue);
        }
    }
}
