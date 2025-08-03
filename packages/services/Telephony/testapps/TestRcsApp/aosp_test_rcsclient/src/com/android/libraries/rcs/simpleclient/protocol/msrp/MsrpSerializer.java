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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Serializer for writing messages
 */
public final class MsrpSerializer {

    private MsrpSerializer() {
    }

    public static void serialize(OutputStream outputStream, MsrpChunk message) throws IOException {

        writeRequestLine(outputStream, message);
        for (MsrpChunkHeader header : message.headers()) {
            writeHeader(outputStream, header);
        }

        if (message.content().length > 0) {
            outputStream.write(MsrpConstants.NEW_LINE_BYTES);
            outputStream.write(message.content());
            outputStream.write(MsrpConstants.NEW_LINE_BYTES);
        }

        writeEndLine(outputStream, message);
    }

    private static void writeRequestLine(OutputStream outputStream, MsrpChunk chunk)
            throws IOException {

        outputStream.write(MsrpConstants.MSRP_PROTOCOL_BYTES);
        outputStream.write(MsrpConstants.CHAR_SP);
        outputStream.write(chunk.transactionId().getBytes());
        outputStream.write(MsrpConstants.CHAR_SP);

        if (chunk.method() != MsrpChunk.Method.UNKNOWN) {
            outputStream.write(chunk.method().name().getBytes(UTF_8));
        } else {
            outputStream.write(
                    (chunk.responseCode() + " " + chunk.responseReason()).getBytes(UTF_8));
        }

        outputStream.write(MsrpConstants.NEW_LINE_BYTES);
    }

    private static void writeHeader(OutputStream outputStream, MsrpChunkHeader header)
            throws IOException {
        outputStream.write(header.name().getBytes(UTF_8));
        outputStream.write(MsrpConstants.HEADER_DELIMITER_BYTES);
        outputStream.write(header.value().getBytes(UTF_8));
        outputStream.write(MsrpConstants.NEW_LINE_BYTES);
    }

    private static void writeEndLine(OutputStream outputStream, MsrpChunk chunk)
            throws IOException {
        outputStream.write(MsrpConstants.END_MSRP_MSG_BYTES);
        outputStream.write(chunk.transactionId().getBytes(UTF_8));
        outputStream.write(chunk.continuation().toByte());
        outputStream.write(MsrpConstants.NEW_LINE_BYTES);
    }
}