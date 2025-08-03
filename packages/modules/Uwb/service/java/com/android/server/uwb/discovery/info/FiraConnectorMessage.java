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

package com.android.server.uwb.discovery.info;

import android.annotation.IntRange;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.util.Log;

import com.android.server.uwb.util.ArrayUtils;

import com.google.common.primitives.Bytes;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Holds data of the FiRa UWB Connector Message according to FiRa BLE OOB v1.0 and CSML v1.0
 * specification.
 */
public class FiraConnectorMessage {
    private static final String TAG = FiraConnectorMessage.class.getSimpleName();

    /** Bit position and mask for the message header fields. */
    private static final int MESSAGE_TYPE_BITPOS = 6;

    private static final int MESSAGE_TYPE_BITMASK = 0x03;
    private static final int INSTRUCTION_CODE_BITMASK = 0x3F;

    /** Type of Fira Connector Message. */
    public enum MessageType {
        COMMAND(0),
        EVENT(1),
        COMMAND_RESPOND(2);
        // 3 Reserved for future use.

        @IntRange(from = 0, to = 2)
        private final int mValue;

        private static Map sMap = new HashMap<>();

        MessageType(int value) {
            this.mValue = value;
        }

        static {
            for (MessageType type : MessageType.values()) {
                sMap.put(type.mValue, type);
            }
        }

        /**
         * Get the MessageType based on the given value.
         *
         * @param value type value defined by FiRa.
         * @return {@link MessageType} associated with the value, else null if invalid.
         */
        @Nullable
        public static MessageType valueOf(int value) {
            return (MessageType) sMap.get(value);
        }

        public int getValue() {
            return mValue;
        }
    }

    @NonNull public final MessageType messageType;

    /** Fira Connector Message Instruction Code. */
    public enum InstructionCode {
        DATA_EXCHANGE(0),
        ERROR_INDICATION(1);
        // 2-63 Reserved for future use.

        @IntRange(from = 0, to = 1)
        private final int mValue;

        private static Map sMap = new HashMap<>();

        InstructionCode(int value) {
            this.mValue = value;
        }

        static {
            for (InstructionCode type : InstructionCode.values()) {
                sMap.put(type.mValue, type);
            }
        }

        /**
         * Get the InstructionCode based on the given value.
         *
         * @param value Instruction code defined by FiRa.
         * @return {@link InstructionCode} associated with the value, else null if invalid.
         */
        @Nullable
        public static InstructionCode valueOf(int value) {
            return (InstructionCode) sMap.get(value);
        }

        public int getValue() {
            return mValue;
        }
    }

    @NonNull public final InstructionCode instructionCode;

    /** Contains actual application data defined by upper layer. */
    @NonNull public final byte[] payload;

    /**
     * Generate the FiraConnectorMessage from raw bytes array.
     *
     * @param bytes byte array containing the FiRa UWB Connector Message encoding based on the FiRa
     *     specification.
     * @return decode bytes into {@link FiraConnectorMessage}, else null if invalid.
     */
    @Nullable
    public static FiraConnectorMessage fromBytes(@NonNull byte[] bytes) {
        if (ArrayUtils.isEmpty(bytes)) {
            Log.w(TAG, "Failed to convert empty into FiRa Connector Message.");
            return null;
        }

        ByteBuffer byteBuffer = ByteBuffer.wrap(bytes);
        byte header = byteBuffer.get();
        MessageType messageType =
                MessageType.valueOf((header >>> MESSAGE_TYPE_BITPOS) & MESSAGE_TYPE_BITMASK);
        InstructionCode instructionCode =
                InstructionCode.valueOf(header & INSTRUCTION_CODE_BITMASK);

        byte[] payload = new byte[byteBuffer.remaining()];
        byteBuffer.get(payload);

        return new FiraConnectorMessage(messageType, instructionCode, payload);
    }

    /**
     * Generate raw bytes array from FiraConnectorMessage.
     *
     * @return encoded bytes into byte array based on the FiRa specification.
     */
    public byte[] toBytes() {
        byte[] header =
                new byte[] {
                    (byte)
                            (((messageType.getValue() & MESSAGE_TYPE_BITMASK)
                                            << MESSAGE_TYPE_BITPOS)
                                    | (instructionCode.getValue() & INSTRUCTION_CODE_BITMASK))
                };
        return Bytes.concat(header, payload);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("FiraConnectorMessage: messageType=")
                .append(messageType)
                .append(" instructionCode=")
                .append(instructionCode)
                .append(" payload=")
                .append(Arrays.toString(payload));
        return sb.toString();
    }

    public FiraConnectorMessage(
            MessageType messageType, InstructionCode instructionCode, byte[] payload) {
        if (messageType == null) {
            throw new IllegalArgumentException("messageType is null");
        }
        if (instructionCode == null) {
            throw new IllegalArgumentException("instructionCode is null");
        }
        this.messageType = messageType;
        this.instructionCode = instructionCode;
        this.payload = payload;
    }
}
