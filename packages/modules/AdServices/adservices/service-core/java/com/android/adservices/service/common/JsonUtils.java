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

import android.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

/** Utility class that contains functions to more easily interact with {@link JSONObject}. */
public class JsonUtils {
    public static final String VALUE_NOT_A_STRING = "Value with key %s is not a String!";
    /**
     * Gets a string value from a {@code JSONObject} with key {@code key} without forcing the value
     * of {@link JSONObject#getString(String)} into a String
     *
     * @throws JSONException if {@code key} is not in {@code JSONObject} or value of {@code
     *     JSONObject.getString(key)} is not a String
     */
    @NonNull
    public static String getStringFromJson(@NonNull JSONObject jsonObject, @NonNull String key)
            throws JSONException {
        return getStringFromJson(jsonObject, key, String.format(VALUE_NOT_A_STRING, key));
    }

    /**
     * Gets a string value from a {@code JSONArray} with index {@code index} without forcing the
     * value of {@link JSONObject#getString(String)} into a String. The caller can provide a custom
     * error message that will be set in the JSONException if a failure occurs.
     *
     * @throws JSONException if {@code key} is not in {@code JSONObject} or value of {@code
     *     JSONObject.getString(key)} is not a String
     */
    @NonNull
    public static String getStringFromJsonArrayAtIndex(
            @NonNull JSONArray jsonArray, int index, @NonNull String errorMsg)
            throws JSONException {
        Objects.requireNonNull(jsonArray);
        Objects.requireNonNull(errorMsg);

        return assertObjectIsString(jsonArray.get(index), errorMsg);
    }

    /**
     * Gets a string value from a {@code JSONObject} with key {@code key} without forcing the value
     * of {@link JSONObject#getString(String)} into a String. The caller can provide a custom error
     * message that will be set in the JSONException if a failure occurs.
     *
     * @throws JSONException if {@code key} is not in {@code JSONObject} or value of {@code
     *     JSONObject.getString(key)} is not a String
     */
    @NonNull
    public static String getStringFromJson(
            @NonNull JSONObject jsonObject, @NonNull String key, @NonNull String errorMsg)
            throws JSONException {
        Objects.requireNonNull(jsonObject);
        Objects.requireNonNull(key);
        Objects.requireNonNull(errorMsg);

        return assertObjectIsString(jsonObject.get(key), errorMsg);
    }

    private static String assertObjectIsString(Object o, String errorMsg) throws JSONException {
        if (!(o instanceof String)) {
            throw new JSONException(errorMsg);
        }
        return (String) o;
    }
}
