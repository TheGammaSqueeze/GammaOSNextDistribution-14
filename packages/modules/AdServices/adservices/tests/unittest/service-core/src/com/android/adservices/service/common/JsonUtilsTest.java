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

package com.android.adservices.service.common;

import static com.android.adservices.service.common.JsonUtils.VALUE_NOT_A_STRING;
import static com.android.adservices.service.common.JsonUtils.getStringFromJson;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

public class JsonUtilsTest {
    private static final String KEY = "key";
    private static final String VALID_VALUE = "valid_value";
    private static final String CUSTOM_ERROR = "This is a custom error message";

    @Test
    public void testGetStringFromJsonSuccess() throws Exception {
        JSONObject jsonObject = new JSONObject().put(KEY, VALID_VALUE);

        assertEquals(getStringFromJson(jsonObject, KEY), VALID_VALUE);
    }

    @Test
    public void testGetStringFromJsonArraySuccess() throws Exception {
        JSONArray jsonArray = new JSONArray().put(VALID_VALUE);

        assertEquals(
                JsonUtils.getStringFromJsonArrayAtIndex(jsonArray, 0, CUSTOM_ERROR), VALID_VALUE);
    }

    @Test
    public void testGetStringFromJsonThrowsJSONExceptionWhenKeyDoesNotExist() throws Exception {
        JSONObject jsonObject = new JSONObject().put(KEY, VALID_VALUE);

        assertThrows(
                String.format(VALUE_NOT_A_STRING, KEY + "otherValue"),
                JSONException.class,
                () -> {
                    getStringFromJson(jsonObject, KEY + "otherValue");
                });
    }

    @Test
    public void testGetStringFromJsonThrowsJSONExceptionWhenValueIsNotString() throws Exception {
        JSONObject jsonObject = new JSONObject().put(KEY, 1);

        assertThrows(
                String.format(VALUE_NOT_A_STRING, KEY),
                JSONException.class,
                () -> {
                    getStringFromJson(jsonObject, KEY);
                });
    }

    @Test
    public void testGetStringFromJsonArrayThrowsJSONExceptionWhenValueIsNotString() {
        JSONArray jsonArray = new JSONArray().put(1);

        assertThrows(
                CUSTOM_ERROR,
                JSONException.class,
                () -> {
                    JsonUtils.getStringFromJsonArrayAtIndex(jsonArray, 0, CUSTOM_ERROR);
                });
    }

    @Test
    public void testGetStringFromJsonThrowsJSONExceptionWhenValueIsNotStringCustomError()
            throws Exception {
        JSONObject jsonObject = new JSONObject().put(KEY, 1);

        assertThrows(
                CUSTOM_ERROR,
                JSONException.class,
                () -> {
                    getStringFromJson(jsonObject, KEY, CUSTOM_ERROR);
                });
    }

    @Test
    public void testGetStringFromJsonThrowsJSONExceptionWhenValueIsJSONObject() throws Exception {
        JSONObject innerObject = new JSONObject().put(KEY, VALID_VALUE);

        // Put innerObject at value of Key
        JSONObject jsonObject = new JSONObject().put(KEY, innerObject);

        assertThrows(
                String.format(VALUE_NOT_A_STRING, KEY),
                JSONException.class,
                () -> {
                    getStringFromJson(jsonObject, KEY);
                });
    }
}
