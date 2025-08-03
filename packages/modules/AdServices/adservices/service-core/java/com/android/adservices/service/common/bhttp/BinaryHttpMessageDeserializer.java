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

package com.android.adservices.service.common.bhttp;

import static com.android.adservices.service.common.bhttp.BinaryHttpMessage.EMPTY_CONTENT;

import android.annotation.NonNull;

import com.android.adservices.LoggerFactory;
import com.android.internal.annotations.VisibleForTesting;

import com.google.auto.value.AutoValue;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

/** Deserialize the message from a binary representation according to the framing indicator. */
@AutoValue
public class BinaryHttpMessageDeserializer {

    /**
     * Deserialize the message from a binary representation according to the framing indicator.
     *
     * @throws IllegalArgumentException when message was truncated in a wrong place or message is
     *     malformed.
     * @see <a
     *     href="https://www.ietf.org/archive/id/draft-ietf-httpbis-binary-message-06.html#name-padding-and-truncation">Binary
     *     HTTP Padding And Truncation</a>
     */
    @NonNull
    public BinaryHttpMessage deserialize(@NonNull final byte[] data) {
        final BinaryHttpByteArrayReader reader = new BinaryHttpByteArrayReader(data);
        switch (reader.getFramingIndicatorByte()) {
            case FramingIndicator.FRAMING_INDICATOR_REQUEST_OF_KNOWN_LENGTH:
            case FramingIndicator.FRAMING_INDICATOR_RESPONSE_OF_KNOWN_LENGTH:
                return deserializeKnownLengthMessage(reader);
            default:
                throw new IllegalArgumentException(
                        String.format(
                                "Invalid framing indicator %d", reader.getFramingIndicatorByte()));
        }
    }

    @NonNull
    @VisibleForTesting
    static ControlData deserializeKnownLengthRequestControlData(
            @NonNull final BinaryHttpByteArrayReader reader) {
        return RequestControlData.builder()
                .setMethod(new String(reader.readNextKnownLengthData().getData()))
                .setScheme(new String(reader.readNextKnownLengthData().getData()))
                .setAuthority(new String(reader.readNextKnownLengthData().getData()))
                .setPath(new String(reader.readNextKnownLengthData().getData()))
                .build();
    }

    @NonNull
    @VisibleForTesting
    static ControlData deserializeKnownLengthResponseControlData(
            @NonNull final BinaryHttpByteArrayReader reader) {
        ResponseControlData.Builder builder = ResponseControlData.builder();

        int statusCode;
        while (HttpStatusCodeUtil.isInformativeStatusCode(
                statusCode = (int) reader.readNextRfc9000Int())) {
            builder.addInformativeResponse(
                    InformativeResponse.builder()
                            .setInformativeStatusCode(statusCode)
                            .setHeaderFields(deserializeKnownLengthFields(reader))
                            .build());
        }

        builder.setFinalStatusCode(statusCode);
        return builder.build();
    }

    @NonNull
    @VisibleForTesting
    static BinaryHttpMessage deserializeKnownLengthMessage(
            @NonNull final BinaryHttpByteArrayReader reader) {
        final byte framingIndicator = reader.getFramingIndicatorByte();
        // For response, the informative responses are included in the control data to simplifying
        // the code.
        final ControlData controlData = deserializeKnownLengthControlData(framingIndicator, reader);
        final Fields headerFields = deserializeKnownLengthFields(reader);
        byte[] content = EMPTY_CONTENT;
        if (reader.hasRemainingBytes()) {
            content = reader.readNextKnownLengthData().getData();
        }
        // We don't have trailer support for now. Trailer will be treated as part of padding.
        int paddingLength = reader.remainingLength();
        return BinaryHttpMessage.builder()
                .setFramingIndicator(framingIndicator)
                .setControlData(controlData)
                .setHeaderFields(headerFields)
                .setContent(content)
                .setPaddingLength(paddingLength)
                .build();
    }

    @NonNull
    private static ControlData deserializeKnownLengthControlData(
            final byte framingIndicator, @NonNull final BinaryHttpByteArrayReader reader) {
        switch (framingIndicator) {
            case FramingIndicator.FRAMING_INDICATOR_REQUEST_OF_KNOWN_LENGTH:
                return deserializeKnownLengthRequestControlData(reader);
            case FramingIndicator.FRAMING_INDICATOR_RESPONSE_OF_KNOWN_LENGTH:
                return deserializeKnownLengthResponseControlData(reader);
            default:
                throw new IllegalArgumentException(
                        String.format(
                                "Invalid framing indicator %d", reader.getFramingIndicatorByte()));
        }
    }

    @NonNull
    static Fields deserializeKnownLengthFields(@NonNull final BinaryHttpByteArrayReader reader) {
        Fields.Builder builder = Fields.builder();
        BinaryHttpMessageDeserializer.BinaryHttpByteArrayReader subReader =
                reader.readNextKnownLengthData();
        while (subReader.hasRemainingBytes()) {
            builder.appendField(
                    new String(subReader.readNextKnownLengthData().getData()),
                    new String(subReader.readNextKnownLengthData().getData()));
        }
        return builder.build();
    }

    /** Helper class of separating the block of data in a binary HTTP message. */
    @VisibleForTesting
    static class BinaryHttpByteArrayReader {
        private static final LoggerFactory.Logger LOGGER = LoggerFactory.getFledgeLogger();
        @NonNull private final byte[] mData;
        private final int mDataBlockEndIndex;
        private int mNextIndex;

        /**
         * Initial the reader taking a Binary HTTP message byte array.
         *
         * <p>The reader starts at index 1 since the first byte of the array is the framing
         * indicator.
         */
        BinaryHttpByteArrayReader(@NonNull final byte[] data) {
            this(data, 1, data.length);
        }

        private BinaryHttpByteArrayReader(
                @NonNull final byte[] data, final int nextIndex, final int dataBlockEndIndex) {
            Objects.requireNonNull(data);
            mData = data;
            mDataBlockEndIndex = dataBlockEndIndex;
            mNextIndex = nextIndex;
        }

        /**
         * Returns the next section of known length data.
         *
         * <p>First read the length indicator, then return the Reader for it's corresponding data
         * block.
         *
         * <p>Major use case is in the field reading. Fields are represents in [total field length]
         * [[name length][name][value length][value]]*n. We need to return a sub reader for the data
         * of fields.
         */
        @NonNull
        BinaryHttpByteArrayReader readNextKnownLengthData() {
            int start = mNextIndex;
            int dataLength;
            try {
                dataLength = (int) readNextRfc9000Int();
            } catch (IllegalArgumentException e) {
                LOGGER.e(
                        "No sufficient bytes for data length at index %d, out of boundary at %d",
                        start, mDataBlockEndIndex);
                throw new IllegalArgumentException("No sufficient bytes can be read.", e);
            }
            int newDataBlockEndIndex = mNextIndex + dataLength;
            if (newDataBlockEndIndex > mDataBlockEndIndex) {
                LOGGER.e(
                        "No sufficient bytes for data at %d, actual end %d, requested end %d",
                        mNextIndex, mDataBlockEndIndex, newDataBlockEndIndex);
                throw new IllegalArgumentException("No sufficient bytes can be read.");
            }
            BinaryHttpByteArrayReader subReader =
                    new BinaryHttpByteArrayReader(mData, mNextIndex, newDataBlockEndIndex);
            mNextIndex += dataLength;
            return subReader;
        }

        @NonNull
        byte[] getData() {
            return Arrays.copyOfRange(mData, mNextIndex, mDataBlockEndIndex);
        }

        boolean hasRemainingBytes() {
            return mNextIndex < mDataBlockEndIndex;
        }

        int remainingLength() {
            return mDataBlockEndIndex - mNextIndex;
        }

        byte getFramingIndicatorByte() {
            return mData[0];
        }

        /**
         * Returns next FRC 9000 integer.
         *
         * @see <a
         *     href="https://datatracker.ietf.org/doc/html/rfc9000#name-variable-length-integer-enc">FRC
         *     9000 Variable Length Integer</a>
         */
        long readNextRfc9000Int() {
            throwForNotEnoughBytesForInt(1);
            long result;
            // The highest 2 bit of starting byte indicates the length of the integer
            // representation:
            // 11 indicates 8 bytes;
            // 10 indicates 4 bytes;
            // 01 indicates 2 bytes;
            // 00 indicates 1 byte.
            switch (mData[mNextIndex] & 0b11000000) {
                case 0b11000000:
                    // Leading 0b11...... is 8 byte encoding
                    throwForNotEnoughBytesForInt(8);
                    result =
                            ByteBuffer.allocate(8)
                                    .put((byte) (mData[mNextIndex] & 0b00111111))
                                    .put(mData, mNextIndex + 1, 7)
                                    .getLong(0);
                    mNextIndex += 8;
                    return result;
                case 0b10000000:
                    // Leading 0b10...... is 4 byte encoding
                    throwForNotEnoughBytesForInt(4);
                    result =
                            ByteBuffer.allocate(8)
                                    .put(new byte[] {0, 0, 0, 0})
                                    .put((byte) (mData[mNextIndex] & 0b00111111))
                                    .put(mData, mNextIndex + 1, 3)
                                    .getLong(0);
                    mNextIndex += 4;
                    return result;
                case 0b01000000:
                    // Leading 0b01...... is 2 byte encoding
                    throwForNotEnoughBytesForInt(2);
                    result =
                            ByteBuffer.allocate(8)
                                    .put(new byte[] {0, 0, 0, 0, 0, 0})
                                    .put((byte) (mData[mNextIndex] & 0b00111111))
                                    .put(mData[mNextIndex + 1])
                                    .getLong(0);
                    mNextIndex += 2;
                    return result;
                case 0b00000000:
                default:
                    // Leading 0b00...... is 1 byte encoding
                    result = mData[mNextIndex++];
                    return result;
            }
        }

        private void throwForNotEnoughBytesForInt(final int requiredLength) {
            int remainingLength = remainingLength();
            if (remainingLength < requiredLength) {
                LOGGER.e(
                        "Not enough data to be read as FRC 9000 integer, first byte at %d of "
                                + "(%s), needed length %d, remaining length %d.",
                        mNextIndex,
                        remainingLength > 0
                                ? Integer.toString(mData[mNextIndex])
                                : "already out of bound",
                        requiredLength,
                        remainingLength);
                throw new IllegalArgumentException(
                        "Not enough data to be read as FRC 9000 integer.");
            }
        }
    }
}
