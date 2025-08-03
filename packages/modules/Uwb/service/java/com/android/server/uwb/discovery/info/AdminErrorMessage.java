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
import com.android.server.uwb.util.DataTypeConversionUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * Holds data of the FiRa OOB administrative error message according to FiRa BLE OOB v1.0
 * specification.
 */
public class AdminErrorMessage extends FiraConnectorMessage {
    private static final String TAG = AdminErrorMessage.class.getSimpleName();

    /** Type of OOB administrative error defined by FiRa BLE OOB v1.0 . */
    public enum ErrorType {
        /**
         * Sender: CS or CP, Receiver: CP or CS
         *
         * <p>Sent when last Data Packet received overflows resources allocated by FiRa Connector at
         * given moment. When such error indication is received, the FiRa Device should re-transmit
         * the packet with shorter size (might result in Packet Header change).
         */
        DATA_PACKET_LENGTH_OVERFLOW(0x8001),
        /**
         * Sender: CS or CP, Receiver: CP or CS
         *
         * <p>Sent when last Data Packet received overflows resources allocated by FiRa Connector
         * for FiRa Connector Message at given moment. When such error indication is received, the
         * FiRa Device should re-transmit the entire fragmented Message session but with shorter
         * Message size (might result in several Data Packets).
         */
        MESSAGE_LENGTH_OVERFLOW(0x8002),
        /**
         * Sender: CS, Receiver: CP
         *
         * <p>Sent when last Data Packet received opens new fragmented Message session which exceeds
         * “Maximum number of concurrent fragmented Message session supported” parameter set in FiRa
         * Connector Capabilities. When such error indication is received, the FiRa Device should
         * wait with Message transport until there is at least one Message session completed.
         */
        TOO_MANY_CONCURRENT_FRAGMENTED_MESSAGE_SESSIONS(0x8003),
        /**
         * Sender: CS, Receiver: CP
         *
         * <p>Sent when last Data Packet was referencing SECID which isn’t currently existing on CS
         * side. When such error indication is received, the CP should re-sent the Data Packet with
         * correct SECID (e.g. after refreshing FiRa Connector Capabilities) or abort the Message
         * transfer and report error to FiRa-enabled Application.
         */
        SECID_INVALID(0x8004),
        /**
         * Sender: CP, Receiver: CS
         *
         * <p>Sent when last Data Packet was referencing SECID which isn’t expecting any Response on
         * FiRa Connector Message level. When such error indication is received, the CS should
         * re-sent the Data Packet with correct SECID or abort the Message transfer.
         */
        SECID_INVALID_FOR_RESPONSE(0x8005),
        /**
         * Sender: CS, Receiver: CP
         *
         * <p>Sent when last Data Packet was referencing SECID which isn’t currently available for
         * receiving Messages. When such error indication is received, the CP should re-sent the
         * Data Packet later or abort the Message transfer and report error to FiRa-enabled
         * Application.
         */
        SECID_BUSY(0x8006),
        /**
         * Sender: CS or CP, Receiver: CP or CS
         *
         * <p>Sent when last Data Packet was completing Message transfer and that was rejected by
         * the Secure Component (on CS side) or FiRa-enabled Application (on CP side) due to
         * protocol inconsistency. When such error indication is received by CS, it should abort the
         * Message transfer. When such error indication is received by CP, it should abort the
         * Message transfer and report error to FiRa-enabled Application.
         */
        SECID_PROTOCOL_ERROR(0x8007),
        /**
         * Sender: CS or CP, Receiver: CP or CS
         *
         * <p>Sent when last Data Packet was completing Message transfer and that was rejected by
         * the Secure Component (on CS side) or FiRa-enabled Application (on CP side) due to
         * internal processing error (unspecified reason). When such error indication is received by
         * CS, it should abort the Message transfer. When such error indication is received by CP,
         * it should abort the Message transfer and report error to FiRa-enabled Application.
         */
        SECID_INTERNAL_ERROR(0x8008);

        @IntRange(from = 0x8001, to = 0x8008)
        private final int mValue;

        private static Map sMap = new HashMap<>();

        ErrorType(int value) {
            this.mValue = value;
        }

        static {
            for (ErrorType type : ErrorType.values()) {
                sMap.put(type.mValue, type);
            }
        }

        /**
         * Get the ErrorType based on the given value.
         *
         * @param value type value defined by FiRa.
         * @return {@link ErrorType} associated with the value, else null if invalid.
         */
        @Nullable
        public static ErrorType valueOf(int value) {
            return (ErrorType) sMap.get(value);
        }

        public int getValue() {
            return mValue;
        }
    }

    @NonNull public final ErrorType errorType;

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("AdminErrorMessage: errorType=").append(errorType);
        return sb.toString();
    }

    /**
     * Convert the FiraConnectorMessage to an AdminErrorMessage if valid.
     *
     * @param firaConnectorMessage FiraConnectorMessage
     * @return AdminErrorMessage if the message is an administrative error message.
     */
    public static AdminErrorMessage convertToAdminErrorMessage(
            @NonNull FiraConnectorMessage firaConnectorMessage) {
        if (firaConnectorMessage == null) {
            throw new IllegalArgumentException("firaConnectorMessage is null");
        }
        if (!isAdminErrorMessage(firaConnectorMessage)) {
            throw new IllegalArgumentException("firaConnectorMessage is not an AdminEventMessage");
        }
        return new AdminErrorMessage(extractErrorType(firaConnectorMessage.payload));
    }

    public AdminErrorMessage(@NonNull ErrorType errorType) {
        super(
                MessageType.COMMAND_RESPOND,
                InstructionCode.ERROR_INDICATION,
                generatePayload(errorType));
        this.errorType = errorType;
    }

    /**
     * Check if the message is an administrative error message.
     *
     * @param message FiraConnectorMessage
     * @return true if the message is an administrative error message.
     */
    public static boolean isAdminErrorMessage(@NonNull FiraConnectorMessage message) {
        return (message.messageType == MessageType.COMMAND_RESPOND
                && message.instructionCode == InstructionCode.ERROR_INDICATION
                && extractErrorType(message.payload) != null);
    }

    private static byte[] generatePayload(@NonNull ErrorType errorType) {
        if (errorType == null) {
            throw new IllegalArgumentException("errorType is null");
        }
        byte[] errorTypeBytes = DataTypeConversionUtil.i32ToByteArray(errorType.getValue());
        return new byte[] {errorTypeBytes[2], errorTypeBytes[3]};
    }

    private static ErrorType extractErrorType(@NonNull byte[] payload) {
        if (ArrayUtils.isEmpty(payload)) {
            Log.e(TAG, "Failed to extract ErrorType from empty payload.");
            return null;
        }
        ErrorType errorType =
                ErrorType.valueOf(DataTypeConversionUtil.arbitraryByteArrayToI32(payload));
        if (errorType == null) {
            Log.e(TAG, "Failed to extract invalid ErrorType.");
        }
        return errorType;
    }
}
