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

import androidx.annotation.Nullable;

import com.android.server.uwb.secure.iso7816.TlvDatum.Tag;
import com.android.server.uwb.util.DataTypeConversionUtil;

import com.google.common.primitives.Bytes;

import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class for parsing TLV (Tag, Length, Value) data.
 *
 * <p>TLV objects are structured as [tag][length][value]. The [tag] is either 1 or 2 bytes and
 * specifies what the value means (e.g., credit card number) and how it is encoded (e.g., ASCII).
 * The [length] is 1-3 bytes and specifies how long the [value] field is. The [value] field is the
 * value of the object and is decoded depending on the [tag].
 */
public class TlvParser {
    private static class ByteArrayWrapper {
        private final ByteBuffer mByteBuffer;

        ByteArrayWrapper(byte[] byteArray) {
            this.mByteBuffer = ByteBuffer.wrap(byteArray);
        }

        /**
         * Read the part of the data in the array from the current offset.
         */
        private byte[] read(int bytes) throws IOException {
            byte[] result = new byte[bytes];
            try {
                mByteBuffer.get(result);
            } catch (BufferUnderflowException e) {
                throw new IOException("Not enough bytes");
            }
            return result;
        }
    }

    /**
     * Parses bytes from a stream interface to a TlvDatum wrapper object.
     *
     * @param byteArrayWrapper byte stream provider
     * @return TlvDatum derived from the data.
     */
    @Nullable
    private static TlvDatum parseOneTlv(ByteArrayWrapper byteArrayWrapper) {
        try {
            return parseTlv(byteArrayWrapper);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Parses bytes from a stream interface to TlvDatum wrapper objects until consumed.
     *
     * @param stream byte stream provider
     * @return The map of tag and TlvDatum derived from the data.
     */
    private static Map<Tag, List<TlvDatum>> parseTlvs(ByteArrayWrapper byteArrayWrapper) {
        Map<Tag, List<TlvDatum>> tlvData = new HashMap<>();
        TlvDatum tlvDatum;

        while ((tlvDatum = parseOneTlv(byteArrayWrapper)) != null) {
            List<TlvDatum> tlvs = tlvData.computeIfAbsent(
                    tlvDatum.tag, (k) -> new ArrayList<>());
            tlvs.add(tlvDatum);
        }

        return tlvData;
    }

    /**
     * Parses the message bytes of a command APDU into a TlvDatum wrapper object.
     *
     * @param command the command APDU.
     * @return TlvDatum list of TlvDatum derived from the data.
     */
    public static Map<Tag, List<TlvDatum>> parseTlvs(CommandApdu command) {
        return parseTlvs(command.getCommandData());
    }

    /**
     * Parses the message bytes of a response APDU into a TlvDatum wrapper object.
     *
     * @param response the response APDU.
     * @return TlvDatum list of TlvDatum derived from the data.
     */
    public static Map<Tag, List<TlvDatum>> parseTlvs(ResponseApdu response) {
        return parseTlvs(response.getResponseData());
    }

    /**
     * Parses a byte array message into a TlvDatum wrapper object.
     *
     * @param message message byte array to be parsed.
     * @return TlvDatum list of TlvDatum derived from the data.
     */
    public static Map<Tag, List<TlvDatum>> parseTlvs(byte[] message) {
        return parseTlvs(new ByteArrayWrapper(message));
    }

    /**
     * Parses a byte array message to a TlvDatum object.
     * @param message It should only contain one tlv.
     * @return The TlvDatum instance.
     */
    public static TlvDatum parseOneTlv(byte[] message) {
        return parseOneTlv(new ByteArrayWrapper(message));
    }

    private static TlvDatum parseTlv(ByteArrayWrapper byteArrayWrapper) throws IOException {
        byte[] tag = byteArrayWrapper.read(/* bytes= */ 1);
        // When first byte is of the form 0bXXX11111, the tag contains a 2nd byte.
        if (((tag[0] + 1) & 0b00011111) == 0) {
            tag = Bytes.concat(tag, byteArrayWrapper.read(/* bytes= */ 1));
        }

        byte[] lengthBytes = byteArrayWrapper.read(/* bytes= */ 1);
        switch (lengthBytes[0]) {
            case TlvDatum.TWO_BYTES_LEN_FIRST_BYTE:
                lengthBytes = byteArrayWrapper.read(/* bytes= */ 1);
                break;
            case TlvDatum.THREE_BYTES_LEN_FIRST_BYTE:
                lengthBytes = byteArrayWrapper.read(/* bytes= */ 2);
                break;
            case TlvDatum.FOUR_BYTES_LEN_FIRST_BYTE:
                lengthBytes = byteArrayWrapper.read(/* bytes= */ 3);
                break;
            case TlvDatum.FIVE_BYTES_LEN_FIRST_BYTE:
                lengthBytes = byteArrayWrapper.read(/* bytes= */ 4);
                break;
            default: // fall out
        }
        int length = DataTypeConversionUtil.arbitraryByteArrayToI32(lengthBytes);

        byte[] value = byteArrayWrapper.read(length);
        if (isConstructedTag(tag[0])) {
            return new TlvDatum(new Tag(tag), parseTlvs(value));
        } else {
            return new TlvDatum(new Tag(tag), value);
        }
    }

    private static boolean isConstructedTag(byte firstTagByte) {
        // If 6th bit is 1, then data object is constructed; otherwise it is primitive.
        // A constructed object's value field contains more TLV structures, while a primitive
        // object's data field does not (contains only data).
        return (firstTagByte & 0b00100000) != 0;
    }

    private TlvParser() {}
}
