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

package com.android.cobalt.data;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import androidx.room.TypeConverter;

import com.google.cobalt.AggregateValue;
import com.google.cobalt.SystemProfile;
import com.google.cobalt.UnencryptedObservationBatch;
import com.google.common.hash.HashCode;
import com.google.protobuf.InvalidProtocolBufferException;

import java.util.Optional;

/**
 * Various function to convert between Java types and their representations in the database.
 *
 * <p>Room requires these functions to be public.
 */
final class Converters {
    private Converters() {}

    public static final class ConverterException extends RuntimeException {
        ConverterException(String message) {
            super(message);
        }
    }

    /**
     * Converts an optional integer to a nullable integer.
     *
     * @param value the value to convert
     * @return the converted value
     */
    @TypeConverter
    public static Integer fromOptionalInteger(Optional<Integer> value) {
        return value.isPresent() ? value.get() : null;
    }

    /**
     * Converts a nullable integer to an optional integer.
     *
     * @param value the value to convert
     * @return the converted value
     */
    @TypeConverter
    public static Optional<Integer> fromInteger(Integer value) {
        return value != null ? Optional.of(value) : Optional.empty();
    }

    /**
     * Converts an event vector to a comma-joined string of integers.
     *
     * @param eventVector the eventVector to encode
     * @return the encoded value
     */
    @TypeConverter
    public static String fromEventVector(EventVector eventVector) {
        return eventVector.eventCodes().stream().map(Object::toString).collect(joining(","));
    }

    /**
     * Converts a comma-joined string of integers to an event vector.
     *
     * @param eventCodes the comma-joined string of integers to decode
     * @return the decoded value
     */
    @TypeConverter
    public static EventVector stringToEventVector(String eventCodes) {
        if (eventCodes.isEmpty()) {
            return EventVector.create();
        }
        return EventVector.create(
                stream(eventCodes.split(",")).map(Integer::parseInt).collect(toList()));
    }

    /**
     * Converts a {@link HashCode} to a byte array.
     *
     * @param hashCode the {@link HashCode} to serialize
     * @return the serialized {@link HashCode}
     */
    @TypeConverter
    public static byte[] fromHashCode(HashCode hashCode) {
        return hashCode.asBytes();
    }

    /**
     * Converts a byte array to a {@link HashCode}.
     *
     * @param bytes a serialized {@link HashCode}
     * @return the deserialized {@link HashCode}
     */
    @TypeConverter
    public static HashCode bytesToHashCode(byte[] bytes) {
        return HashCode.fromBytes(bytes);
    }

    /**
     * Converts a system profile to an array of bytes.
     *
     * @param systemProfile the system profile to serialize
     * @return the bytes to write
     */
    @TypeConverter
    public static byte[] fromSystemProfile(SystemProfile systemProfile) {
        return systemProfile.toByteArray();
    }

    /**
     * Decodes an array of bytes in to a system profile.
     *
     * @param bytes the bytes to decode
     * @return the system profile
     */
    @TypeConverter
    public static SystemProfile bytesToSystemProfile(byte[] bytes) {
        try {
            return SystemProfile.parseFrom(bytes);
        } catch (InvalidProtocolBufferException e) {
            throw new ConverterException("Invalid SystemProfile bytes");
        }
    }

    /**
     * Converts an aggregate value to an array of bytes.
     *
     * @param aggregateValue the aggregate value to serialize
     * @return the bytes to write
     */
    @TypeConverter
    public static byte[] fromAggregateValue(AggregateValue aggregateValue) {
        return aggregateValue.toByteArray();
    }

    /**
     * Decodes an array of bytes in to an aggregate value
     *
     * @param bytes the bytes to decode
     * @return the optional aggregate value
     */
    @TypeConverter
    public static AggregateValue bytesToAggregateValue(byte[] bytes) {
        try {
            return AggregateValue.parseFrom(bytes);
        } catch (InvalidProtocolBufferException e) {
            throw new ConverterException("Invalid AggregateValue bytes");
        }
    }

    /**
     * Converts an unencrypted observation batch to an array of bytes.
     *
     * @param unencryptedObservationBatch the observation batch to serialize
     * @return the bytes to write
     */
    @TypeConverter
    public static byte[] fromUnencryptedObservationBatch(
            UnencryptedObservationBatch unencryptedObservationBatch) {
        return unencryptedObservationBatch.toByteArray();
    }

    /**
     * Decodes an array of bytes in to an unencrypted observation batch.
     *
     * @param bytes the bytes to decode
     * @return the observation batch
     */
    @TypeConverter
    public static UnencryptedObservationBatch bytesToUnencryptedObservationBatch(byte[] bytes) {
        try {
            return UnencryptedObservationBatch.parseFrom(bytes);
        } catch (InvalidProtocolBufferException e) {
            throw new ConverterException("Invalid UnencryptedObservationBatch bytes");
        }
    }
}
