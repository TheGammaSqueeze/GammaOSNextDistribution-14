/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.google.uwb.support.aliro;

import com.google.uwb.support.base.ProtocolVersion;

import java.nio.ByteBuffer;

/** Provides parameter versioning for Aliro. */
public class AliroProtocolVersion extends ProtocolVersion {
    private static final int ALIRO_PACKED_BYTE_COUNT = 2;

    public AliroProtocolVersion(int major, int minor) {
        super(major, minor);
    }

    public static AliroProtocolVersion fromString(String protocol) {
        String[] parts = protocol.split("\\.", -1);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid protocol version: " + protocol);
        }

        return new AliroProtocolVersion(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
    }

    public byte[] toBytes() {
        return ByteBuffer.allocate(bytesUsed())
                .put((byte) getMajor())
                .put((byte) getMinor())
                .array();
    }

    public static AliroProtocolVersion fromBytes(byte[] data, int startIndex) {
        int major = data[startIndex];
        int minor = data[startIndex + 1];
        return new AliroProtocolVersion(major, minor);
    }

    public static int bytesUsed() {
        return ALIRO_PACKED_BYTE_COUNT;
    }
}
