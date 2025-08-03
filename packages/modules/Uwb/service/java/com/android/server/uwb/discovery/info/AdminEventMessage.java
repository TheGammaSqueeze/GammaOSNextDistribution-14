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

import com.google.common.primitives.Bytes;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Holds data of the FiRa OOB administrative event message according to FiRa BLE OOB v1.0
 * specification.
 */
public class AdminEventMessage extends FiraConnectorMessage {
    private static final String TAG = AdminEventMessage.class.getSimpleName();

    private static final int EVENT_NUMBER_FIELD_SIZE = 2;

    /** Type of OOB administrative event. */
    public enum EventType {
        /**
         * Sender: CS, Receiver: CP
         *
         * <p>The FiRa Device shell issue this event immediately when any part of FiRa Connector
         * Capabilities chances. It is applicable only to configuration where CS is implemented on
         * top of GATT Server.
         */
        CAPABILITIES_CHANGED(0x0001);

        @IntRange(from = 0x0001, to = 0x0001)
        private final int mValue;

        private static Map sMap = new HashMap<>();

        EventType(int value) {
            this.mValue = value;
        }

        static {
            for (EventType type : EventType.values()) {
                sMap.put(type.mValue, type);
            }
        }

        /**
         * Get the EventType based on the given value.
         *
         * @param value type value defined by FiRa.
         * @return {@link EventType} associated with the value, else null if invalid.
         */
        @Nullable
        public static EventType valueOf(int value) {
            return (EventType) sMap.get(value);
        }

        public int getValue() {
            return mValue;
        }
    }

    @NonNull public final EventType eventType;
    @NonNull public final byte[] additionalData;

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("AdminEventMessage: EventType=")
                .append(eventType)
                .append(" additionalData=")
                .append(Arrays.toString(additionalData));
        return sb.toString();
    }
    /**
     * Convert the FiraConnectorMessage to an AdminEventMessage if valid.
     *
     * @param firaConnectorMessage FiraConnectorMessage
     * @return AdminEventMessage if the message is an administrative error message.
     */
    public static AdminEventMessage convertToAdminEventMessage(
            @NonNull FiraConnectorMessage firaConnectorMessage) {
        if (firaConnectorMessage == null) {
            throw new IllegalArgumentException("firaConnectorMessage is null");
        }
        if (!isAdminEventMessage(firaConnectorMessage)) {
            throw new IllegalArgumentException("firaConnectorMessage is not an AdminEventMessage");
        }
        return new AdminEventMessage(
                extractEventType(firaConnectorMessage.payload),
                extractAdditionalData(firaConnectorMessage.payload));
    }

    public AdminEventMessage(@NonNull EventType eventType, @NonNull byte[] additionalData) {
        super(
                MessageType.EVENT,
                InstructionCode.DATA_EXCHANGE,
                generatePayload(eventType, additionalData));
        this.eventType = eventType;
        this.additionalData = additionalData;
    }

    /**
     * Check if the message is an administrative event message.
     *
     * @param message FiraConnectorMessage
     * @return true if the message is an administrative event message.
     */
    public static boolean isAdminEventMessage(@NonNull FiraConnectorMessage message) {
        return (message.messageType == MessageType.EVENT
                && message.instructionCode == InstructionCode.DATA_EXCHANGE
                && extractEventType(message.payload) != null
                && extractAdditionalData(message.payload) != null);
    }

    private static byte[] generatePayload(
            @NonNull EventType eventType, @NonNull byte[] additionalData) {
        if (eventType == null) {
            throw new IllegalArgumentException("eventType is null");
        }
        if (additionalData == null) {
            throw new IllegalArgumentException("additionalData is null");
        }
        byte[] eventTypeBytes = DataTypeConversionUtil.i32ToByteArray(eventType.getValue());
        return Bytes.concat(new byte[] {eventTypeBytes[2], eventTypeBytes[3]}, additionalData);
    }

    private static EventType extractEventType(@NonNull byte[] payload) {
        if (ArrayUtils.isEmpty(payload)) {
            Log.e(TAG, "Failed to extract EventType from empty payload.");
            return null;
        }
        EventType eventType =
                EventType.valueOf(DataTypeConversionUtil.arbitraryByteArrayToI32(payload));
        if (eventType == null) {
            Log.w(TAG, "Failed to extract invalid EventType.");
        }
        return eventType;
    }

    private static byte[] extractAdditionalData(@NonNull byte[] payload) {
        if (ArrayUtils.isEmpty(payload)) {
            Log.e(TAG, "Failed to extract AdditionalData from empty payload.");
            return null;
        }
        ByteBuffer buffer =
                ByteBuffer.wrap(
                        payload, EVENT_NUMBER_FIELD_SIZE, payload.length - EVENT_NUMBER_FIELD_SIZE);
        byte[] additionalData = new byte[buffer.remaining()];
        buffer.get(additionalData);
        return additionalData;
    }
}
