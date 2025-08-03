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

import static com.android.adservices.service.signals.SignalsFixture.BB_KEY_1;
import static com.android.adservices.service.signals.updateprocessors.UpdateProcessorUtils.castToJSONArray;
import static com.android.adservices.service.signals.updateprocessors.UpdateProcessorUtils.castToJSONObject;
import static com.android.adservices.service.signals.updateprocessors.UpdateProcessorUtils.decodeKey;
import static com.android.adservices.service.signals.updateprocessors.UpdateProcessorUtils.decodeValue;
import static com.android.adservices.service.signals.updateprocessors.UpdateProcessorUtils.touchKey;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.android.adservices.common.SdkLevelSupportRule;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashSet;

public class UpdateProcessorUtilsTest {

    private static final String COMMAND = "put";

    @Rule(order = 0)
    public final SdkLevelSupportRule sdkLevel = SdkLevelSupportRule.forAtLeastT();

    @Test
    public void testCastToJSONArraySuccess() {
        JSONArray expected = new JSONArray();
        expected.put("abitrary string");
        JSONArray actual = castToJSONArray(COMMAND, expected);
        assertEquals(expected, actual);
    }

    @Test
    public void testCastToJSONArrayFailure() {
        assertThrows(IllegalArgumentException.class, () -> castToJSONArray(COMMAND, new Object()));
    }

    @Test
    public void testCastToJSONObjectSuccess() throws JSONException {
        JSONObject expected = new JSONObject();
        expected.put("abitrary_string", "other_string");
        JSONObject actual = castToJSONObject(COMMAND, expected);
        assertEquals(expected, actual);
    }

    @Test
    public void testCastToJSONObjectFailure() {
        assertThrows(IllegalArgumentException.class, () -> castToJSONObject(COMMAND, new Object()));
    }

    @Test
    public void testTouchKeySuccess() {
        HashSet<ByteBuffer> set = new HashSet<>();
        touchKey(BB_KEY_1, set);
        assertTrue(set.contains(BB_KEY_1));
    }

    @Test
    public void testTouchKeyFailure() {
        assertThrows(
                IllegalArgumentException.class,
                () -> touchKey(BB_KEY_1, new HashSet<>(Arrays.asList(BB_KEY_1))));
    }

    @Test
    public void testDecodeKey() {
        String key = "AQIDBA==";
        ByteBuffer decoded = decodeKey(COMMAND, key);
        ByteBuffer expected = ByteBuffer.wrap(new byte[] {(byte) 1, (byte) 2, (byte) 3, (byte) 4});
        assertEquals(expected, decoded);
    }

    @Test
    public void testDecodeKeyInvalidBase64() {
        String key = "*";
        assertThrows(IllegalArgumentException.class, () -> decodeKey(COMMAND, key));
    }

    @Test
    public void testDecodeKeyTooBig() {
        String key = "AAAAAAAAAAAAAAAAAAAAAAAA";
        assertThrows(IllegalArgumentException.class, () -> decodeKey(COMMAND, key));
    }

    @Test
    public void testDecodeValue() {
        String value = "KgUJ";
        byte[] decoded = decodeValue(COMMAND, value);
        byte[] expected = {(byte) 42, (byte) 5, (byte) 9};
        assertArrayEquals(expected, decoded);
    }

    @Test
    public void testDecodeValueInvalidBase64() {
        String value = "*";
        assertThrows(IllegalArgumentException.class, () -> decodeValue(COMMAND, value));
    }

    @Test
    public void testDecodeValueTooBig() {
        String value = "a".repeat(500);
        assertThrows(IllegalArgumentException.class, () -> decodeValue(COMMAND, value));
    }
}
