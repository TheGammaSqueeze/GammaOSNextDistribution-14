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

package android.util;

import static android.util.StatsEvent.TYPE_ATTRIBUTION_CHAIN;
import static android.util.StatsEvent.TYPE_BOOLEAN;
import static android.util.StatsEvent.TYPE_BYTE_ARRAY;
import static android.util.StatsEvent.TYPE_ERRORS;
import static android.util.StatsEvent.TYPE_FLOAT;
import static android.util.StatsEvent.TYPE_INT;
import static android.util.StatsEvent.TYPE_LIST;
import static android.util.StatsEvent.TYPE_LONG;
import static android.util.StatsEvent.TYPE_STRING;
import static android.util.proto.ProtoOutputStream.FIELD_COUNT_REPEATED;
import static android.util.proto.ProtoOutputStream.FIELD_COUNT_SINGLE;
import static android.util.proto.ProtoOutputStream.FIELD_TYPE_FLOAT;
import static android.util.proto.ProtoOutputStream.FIELD_TYPE_INT32;
import static android.util.proto.ProtoOutputStream.FIELD_TYPE_INT64;
import static android.util.proto.ProtoOutputStream.FIELD_TYPE_MESSAGE;
import static android.util.proto.ProtoOutputStream.FIELD_TYPE_STRING;
import static java.nio.charset.StandardCharsets.UTF_8;

import android.util.proto.ProtoOutputStream;
import com.android.os.AtomsProto.Atom;
import com.google.protobuf.InvalidProtocolBufferException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class StatsEventTestUtils {
    private static final int ATTRIBUTION_UID_FIELD = 1;
    private static final int ATTRIBUTION_TAG_FIELD = 2;

    private StatsEventTestUtils() {
    } // no instances.

    // Convert StatsEvent to MessageLite representation of Atom.
    // Calls StatsEvent#release; No further actions should be taken on the StatsEvent
    // object.
    public static Atom convertToAtom(StatsEvent statsEvent) throws InvalidProtocolBufferException {
        return Atom.parseFrom(getProtoBytes(statsEvent));
    }

    // Convert StatsEvent to serialized proto representation of Atom.
    // Calls StatsEvent#release; No further actions should be taken on the StatsEvent
    // object.
    private static byte[] getProtoBytes(StatsEvent statsEvent) {
        try {
            ByteBuffer buf = ByteBuffer.wrap(statsEvent.getBytes()).order(ByteOrder.LITTLE_ENDIAN);
            buf.get(); // Payload starts with TYPE_OBJECT.

            // Read number of elements at the root level.
            byte fieldsRemaining = buf.get();
            if (fieldsRemaining < 2) {
                // Each StatsEvent should at least have a timestamp and atom ID.
                throw new IllegalArgumentException("StatsEvent should have more than 2 elements.");
            }

            // Read timestamp.
            if (buf.get() != TYPE_LONG) {
                // Timestamp should be TYPE_LONG
                throw new IllegalArgumentException("StatsEvent does not have timestamp.");
            }
            buf.getLong(); // Read elapsed timestamp.
            fieldsRemaining--;

            // Read atom ID.
            FieldMetadata fieldMetadata = parseFieldMetadata(buf);
            if (fieldMetadata.typeId != TYPE_INT) {
                // atom ID should be an integer.
                throw new IllegalArgumentException("StatsEvent does not have an atom ID.");
            }
            int atomId = buf.getInt();
            skipAnnotations(buf, fieldMetadata.annotationCount);
            fieldsRemaining--;

            ProtoOutputStream proto = new ProtoOutputStream();
            long atomToken = proto.start(FIELD_TYPE_MESSAGE | FIELD_COUNT_SINGLE | atomId);

            // Read atom fields.
            for (int tag = 1; tag <= fieldsRemaining; tag++) {
                fieldMetadata = parseFieldMetadata(buf);
                parseField(fieldMetadata.typeId, FIELD_COUNT_SINGLE, tag, buf, proto);
                skipAnnotations(buf, fieldMetadata.annotationCount);
            }

            // We should have parsed all bytes in StatsEvent at this point.
            if (buf.position() != statsEvent.getNumBytes()) {
                throw new IllegalArgumentException("Unexpected bytes in StatsEvent");
            }

            proto.end(atomToken);
            return proto.getBytes();
        } finally {
            statsEvent.release();
        }
    }

    private static void parseField(
            byte typeId, long fieldCount, int tag, ByteBuffer buf, ProtoOutputStream proto) {
        switch (typeId) {
            case TYPE_INT:
                proto.write(FIELD_TYPE_INT32 | fieldCount | tag, buf.getInt());
                break;
            case TYPE_LONG:
                proto.write(FIELD_TYPE_INT64 | fieldCount | tag, buf.getLong());
                break;
            case TYPE_STRING:
                String value = new String(getByteArrayFromByteBuffer(buf), UTF_8);
                proto.write(FIELD_TYPE_STRING | fieldCount | tag, value);
                break;
            case TYPE_FLOAT:
                proto.write(FIELD_TYPE_FLOAT | fieldCount | tag, buf.getFloat());
                break;
            case TYPE_BOOLEAN:
                proto.write(FIELD_TYPE_INT32 | fieldCount | tag, buf.get());
                break;
            case TYPE_ATTRIBUTION_CHAIN:
                byte numNodes = buf.get();
                for (byte i = 1; i <= numNodes; i++) {
                    long token = proto.start(FIELD_TYPE_MESSAGE | FIELD_COUNT_REPEATED | tag);
                    proto.write(FIELD_TYPE_INT32 | FIELD_COUNT_SINGLE | ATTRIBUTION_UID_FIELD,
                            buf.getInt());
                    String tagName = new String(getByteArrayFromByteBuffer(buf), UTF_8);
                    proto.write(FIELD_TYPE_STRING | FIELD_COUNT_SINGLE | ATTRIBUTION_TAG_FIELD,
                            tagName);
                    proto.end(token);
                }
                break;
            case TYPE_BYTE_ARRAY:
                byte[] byteArray = getByteArrayFromByteBuffer(buf);
                proto.write(FIELD_TYPE_MESSAGE | FIELD_COUNT_SINGLE | tag, byteArray);
                break;
            case TYPE_LIST:
                byte numItems = buf.get();
                byte listTypeId = buf.get();
                for (byte i = 1; i <= numItems; i++) {
                    parseField(listTypeId, FIELD_COUNT_REPEATED, tag, buf, proto);
                }
                break;
            case TYPE_ERRORS:
                int errorMask = buf.getInt();
                throw new IllegalArgumentException("StatsEvent has error(s): " + errorMask);
            default:
                throw new IllegalArgumentException(
                        "Invalid typeId encountered while parsing StatsEvent: " + typeId);
        }
    }

    private static byte[] getByteArrayFromByteBuffer(ByteBuffer buf) {
        final int numBytes = buf.getInt();
        byte[] bytes = new byte[numBytes];
        buf.get(bytes);
        return bytes;
    }

    private static void skipAnnotations(ByteBuffer buf, int annotationCount) {
        for (int i = 1; i <= annotationCount; i++) {
            buf.get(); // read annotation ID.
            byte annotationType = buf.get();
            if (annotationType == TYPE_INT) {
                buf.getInt(); // read and drop int annotation value.
            } else if (annotationType == TYPE_BOOLEAN) {
                buf.get(); // read and drop byte annotation value.
            } else {
                throw new IllegalArgumentException("StatsEvent has an invalid annotation.");
            }
        }
    }

    private static FieldMetadata parseFieldMetadata(ByteBuffer buf) {
        FieldMetadata fieldMetadata = new FieldMetadata();
        fieldMetadata.typeId = buf.get();
        fieldMetadata.annotationCount = (byte) (fieldMetadata.typeId >> 4);
        fieldMetadata.typeId &= (byte) 0x0F;

        return fieldMetadata;
    }

    private static class FieldMetadata {
        byte typeId;
        byte annotationCount;
    }
}
