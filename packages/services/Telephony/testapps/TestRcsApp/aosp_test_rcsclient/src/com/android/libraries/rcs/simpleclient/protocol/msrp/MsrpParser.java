/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.libraries.rcs.simpleclient.protocol.msrp;

import static com.android.libraries.rcs.simpleclient.protocol.msrp.MsrpConstants.CHAR_DOUBLE_POINT;
import static com.android.libraries.rcs.simpleclient.protocol.msrp.MsrpConstants.CHAR_LF;
import static com.android.libraries.rcs.simpleclient.protocol.msrp.MsrpConstants.CHAR_MIN;
import static com.android.libraries.rcs.simpleclient.protocol.msrp.MsrpConstants.CHAR_SP;
import static com.android.libraries.rcs.simpleclient.protocol.msrp.MsrpConstants.END_MSRP_MSG_LENGTH;
import static com.android.libraries.rcs.simpleclient.protocol.msrp.MsrpConstants.HEADER_BYTE_RANGE;
import static com.android.libraries.rcs.simpleclient.protocol.msrp.MsrpConstants.NEW_LINE_END_MSRP_MSG;

import com.android.libraries.rcs.simpleclient.protocol.msrp.MsrpChunk.Continuation;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * Simple parser for reading MSRP messages from a stream.
 */
public final class MsrpParser {

    private MsrpParser() {
    }

    public static MsrpChunk parse(final InputStream stream) throws IOException {
        MsrpChunk.Builder transaction = MsrpChunk.newBuilder();

        // Read a chunk (blocking method)
        int i = stream.read();

        final StringBuilder value = new StringBuilder();
        // Read MSRP tag
        skipWithDelimiter(stream, CHAR_SP);

        if (i == -1) {
            // End of stream
            return null;
        }

        // Read the transaction ID
        do {
            i = stream.read();
            if (i != CHAR_SP) {
                value.append((char) i);
            }
        } while ((i != CHAR_SP) && (i != -1));

        if (i == -1) {
            return null;
        }

        final String txId = value.toString();
        value.setLength(0);

        // Read response code or method name
        MsrpChunk.Method method = MsrpChunk.Method.UNKNOWN;
        int responseCode = -1;
        for (i = stream.read(); (i != CHAR_LF) && (i != -1); i = stream.read()) {
            if (i == CHAR_SP && responseCode == -1) {
                // There is a space -> it's a response
                try {
                    responseCode = Integer.parseInt(value.toString());
                } catch (NumberFormatException nfe) {
                    // This is an invalid response.
                    return null;
                }
                value.setLength(0);
                continue;
            }
            value.append((char) i);
        }

        if (responseCode == -1) {
            try {
                responseCode = Integer.parseInt(value.toString());
                value.setLength(0);
            } catch (final NumberFormatException e) {
                method = MsrpChunk.Method.valueOf(value.toString());
            }
        }

        i = stream.read();

        if (i == -1) {
            // End of stream
            return null;
        }

        final boolean isResponse = responseCode > -1;
        if (isResponse) {
            transaction.transactionId(txId).responseCode(responseCode).responseReason(
                    value.toString());
        } else {
            transaction.transactionId(txId).method(method);
        }

        value.setLength(0);

        // Read MSRP headers
        readHeaders(stream, transaction, value);

        // We already received end of message
        if (transaction.continuation() != Continuation.UNKNOWN) {
            return transaction.build();
        }

        i = stream.read();
        if (i == -1) {
            // End of stream
            return null;
        }

        // Process MSRP request
        if (method == MsrpChunk.Method.SEND) {
            readChunk(stream, transaction);
        }

        return transaction.build();
    }

    private static void readHeaders(
            final InputStream stream, final MsrpChunk.Builder transaction,
            final StringBuilder value)
            throws IOException {
        for (int i = stream.read(); (i != CHAR_LF) && (i != -1); ) {

            for (; (i != CHAR_DOUBLE_POINT) && (i != -1); i = stream.read()) {
                value.append((char) i);
            }

            final String headerName = value.toString();
            value.setLength(0);

            stream.read(); // skip space

            for (i = stream.read(); (i != CHAR_LF) && (i != -1); i = stream.read()) {
                value.append((char) i);
            }

            final String headerValue = value.toString();
            value.setLength(0);

            transaction.addHeader(headerName, headerValue);

            stream.read();

            // It's the end of the header part
            i = stream.read();
            if (i == CHAR_MIN) {
                final int length = END_MSRP_MSG_LENGTH - 1 + transaction.transactionId().length();
                stream.skip(length);
                transaction.continuation(Continuation.valueOf(stream.read()));

                // For response
                for (; (i != CHAR_LF) && (i != -1); i = stream.read()) {
                }
                break;
            }
        }
    }

    private static void readChunk(final InputStream stream, final MsrpChunk.Builder chunk)
            throws IOException {
        final String byteRange = chunk.header(HEADER_BYTE_RANGE).value();

        if (byteRange == null) {
            throw new IllegalStateException("expected non-null byteRange");
        }
        final int chunkSize = getChunkSize(byteRange);
        final long totalSize = getTotalSize(byteRange);

        if (totalSize == Integer.MIN_VALUE || chunkSize < -1) {
            throw new IOException("Invalid byte range: " + byteRange);
        }

        if (chunkSize == -1) {
            readUnknownChunk(stream, chunk);
        } else {
            readKnownChunk(stream, chunk, chunkSize);
            skipEndLine(stream, chunk);
        }

        readContinuationFlag(stream, chunk);
    }

    private static void readKnownChunk(
            final InputStream stream, final MsrpChunk.Builder chunk, final int chunkSize)
            throws IOException {
        // Read the data
        final byte[] data = new byte[chunkSize];
        int nbRead = 0;
        int nbData = -1;
        while ((nbRead < chunkSize)
                && ((nbData = stream.read(data, nbRead, chunkSize - nbRead)) != -1)) {
            nbRead += nbData;
        }

        chunk.content(data);

        stream.read();
        stream.read();
    }

    private static void readUnknownChunk(final InputStream stream, final MsrpChunk.Builder chunk)
            throws IOException {

        final byte[] bufferArray = new byte[4096];
        final byte[] endOfChunkPattern =
                (NEW_LINE_END_MSRP_MSG + chunk.transactionId()).getBytes();
        int pp = 0;

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        final ByteBuffer buffer = ByteBuffer.wrap(bufferArray);
        while (true) {
            final int i = stream.read();

            if (i < 0) {
                throw new IOException("EOS reached");
            }

            if (i == endOfChunkPattern[pp]) {
                pp++;
            } else if (i == endOfChunkPattern[0]) {
                pp = 1;
            } else {
                pp = 0;
            }

            buffer.put((byte) i);

            if (pp == endOfChunkPattern.length) {
                outputStream.write(bufferArray, 0, buffer.position() - endOfChunkPattern.length);
                break;
            }

            if (buffer.remaining() == 0) {
                if (pp > 0) {
                    outputStream.write(bufferArray, 0, bufferArray.length - pp);
                    System.arraycopy(endOfChunkPattern, 0, bufferArray, 0, pp);
                    buffer.position(pp);
                } else {
                    outputStream.write(bufferArray, 0, bufferArray.length);
                    buffer.rewind();
                }
            }
        }

        chunk.content(outputStream.toByteArray());
    }

    private static void skipEndLine(final InputStream stream, final MsrpChunk.Builder chunk)
            throws IOException {
        // skip the "-------" + txid
        final int length = END_MSRP_MSG_LENGTH + chunk.transactionId().length();
        final byte[] endline = new byte[256];
        readFromStream(stream, endline, 0, length);
    }

    private static void readContinuationFlag(
            final InputStream stream, final MsrpChunk.Builder transaction) throws IOException {
        transaction.continuation(Continuation.valueOf(stream.read()));
        stream.read();
        stream.read();
    }

    /**
     * Get the chunk size
     *
     * @param header MSRP header
     * @return Size in bytes
     */
    private static int getChunkSize(final String header) {
        final int index1 = header.indexOf("-");
        final int index2 = header.indexOf("/");
        if ((index1 != -1) && (index2 != -1)) {
            final String lowByteValue = header.substring(0, index1);
            final String highByteValue = header.substring(index1 + 1, index2);

            if ("*".equals(highByteValue)) {
                return -1;
            } else {
                try {
                    final int lowByte = Integer.parseInt(lowByteValue);
                    final int highByte = Integer.parseInt(highByteValue);
                    if (lowByte > highByte) {
                        return Integer.MIN_VALUE;
                    }
                    return (highByte - lowByte) + 1;
                } catch (NumberFormatException e) {
                    throw new IllegalStateException("Could not read chunksize!");
                }
            }
        }
        return Integer.MIN_VALUE;
    }

    /**
     * Get the total size
     *
     * @param header MSRP header
     * @return Size in bytes
     */
    private static long getTotalSize(final String header) {
        final int index = header.indexOf("/");
        if (index != -1) {
            if ("*".equals(header.substring(index + 1))) {
                return -1;
            }
            try {
                return Long.parseLong(header.substring(index + 1));
            } catch (NumberFormatException e) {
                throw new IllegalStateException("Could not read total size!");
            }
        }
        return Integer.MIN_VALUE;
    }

    private static void readFromStream(
            InputStream stream, final byte[] buffer, final int offset, final int length)
            throws IOException {
        int read = 0;
        while (read < length) {
            try {
                read += stream.read(buffer, offset + read, length - read);
            } catch (IndexOutOfBoundsException e) {
                throw new IOException("Invalid ID length", e);
            }
        }
    }

    private static int skipWithDelimiter(InputStream stream, byte delimiter) throws IOException {
        int i = stream.read();
        for (; (i != delimiter) && (i != -1); i = stream.read()) {
        }
        return i;
    }
}
