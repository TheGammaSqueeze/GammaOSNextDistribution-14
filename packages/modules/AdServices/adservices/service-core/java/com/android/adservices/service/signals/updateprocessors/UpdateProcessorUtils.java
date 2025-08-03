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

package com.android.adservices.service.signals.updateprocessors;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;

/** Collection of common utilities for implementers of the UpdateProcessor interface. */
public class UpdateProcessorUtils {

    private static final int KEY_SIZE_BYTES = 4;
    private static final int VALUE_MAX_SIZE_BYTES = 100;

    /**
     * Casts the given update object to a JSONArray throwing an appropriate error if the input is
     * not a JSONArray.
     *
     * @param commandName The name of the command running this method (needed for constructing the
     *     error message in the event of a failure).
     * @param updates The JSONArray to be cast.
     * @return The post-cast JSONArray.
     */
    public static JSONArray castToJSONArray(String commandName, Object updates) {
        if (!(updates instanceof JSONArray)) {
            throw new IllegalArgumentException(
                    String.format("Value for \"%s\" must be a JSON array", commandName));
        }
        return (JSONArray) updates;
    }

    /**
     * Casts the given update object to a JSONObject throwing an appropriate error if the input is
     * not a JSONObject.
     *
     * @param commandName The name of the command running this method (needed for constructing the
     *     error message in the event of a failure).
     * @param updates The JSONObject to be cast.
     * @return The post-cast JSONObject.
     */
    public static JSONObject castToJSONObject(String commandName, Object updates) {
        if (!(updates instanceof JSONObject)) {
            throw new IllegalArgumentException(
                    String.format("Value for \"%s\" must be a JSON object", commandName));
        }
        return (JSONObject) updates;
    }

    /**
     * Adds a key to the set of touched keys, throwing an exception if the key is already in the
     * set.
     *
     * @param key The key to add.
     * @param keysTouched The set of keys already touched.
     */
    public static void touchKey(ByteBuffer key, Set<ByteBuffer> keysTouched) {
        if (!keysTouched.add(key)) {
            throw new IllegalArgumentException("Keys must only appear once per update JSON");
        }
    }

    /**
     * Converts a key from base 64 to a ByteBuffer wrapping a size 4 byte array. Throws an error if
     * the input is not valid base 64 or does not fit in 4 bytes.
     *
     * @param commandName The name of the command calling this method (needed for constructing the
     *     error message in the event of a failure).
     * @param key The base 64 key to convert.
     * @return A byte buffer of the decoded bytes.
     */
    public static ByteBuffer decodeKey(String commandName, String key) {
        byte[] toReturn = new byte[4];
        try {
            Base64.getDecoder().decode(key.getBytes(StandardCharsets.ISO_8859_1), toReturn);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    String.format(
                            "Keys in \"%s\" must be valid base 64 and under %d bytes.",
                            commandName, KEY_SIZE_BYTES));
        }
        return ByteBuffer.wrap(toReturn);
    }

    /**
     * Converts a value from a base 64 string to a byte array. Throws an error if the input is not
     * valid base 64 or does not fit in VALUE_MAX_SIZE_BYTES bytes.
     *
     * @param commandName The name of the command calling this method (needed for constructing the
     *     error message in the event of a failure).
     * @param value The base 64 value to convert.
     * @return A byte array of the decoded bytes.
     */
    public static byte[] decodeValue(String commandName, String value) {
        byte[] toReturn;
        try {
            toReturn = Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    String.format("Values in \"%s\" must be valid base 64", commandName));
        }
        if (toReturn.length > VALUE_MAX_SIZE_BYTES) {
            throw new IllegalArgumentException(
                    String.format(
                            "Values in \"%s\" must be under %d bytes",
                            commandName, VALUE_MAX_SIZE_BYTES));
        }
        return toReturn;
    }

    /**
     * Takes a byte buffer and returns the underlying array, while making sure the ByteBuffer is
     * properly wrapping an array with no offset
     *
     * @param buffer The buffer the convert.
     * @return The underlying byte[].
     */
    public static byte[] getByteArrayFromBuffer(ByteBuffer buffer) {
        if (buffer.arrayOffset() != 0) {
            throw new IllegalStateException("Improperly created ByteBuffer");
        }
        return buffer.array();
    }
}
