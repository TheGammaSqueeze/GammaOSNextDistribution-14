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

package com.android.adservices.service.js;

import static java.util.Arrays.asList;

import android.adservices.common.AdSelectionSignals;

import com.google.common.collect.ImmutableList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Represent an argument to supply to an JS script. */
public abstract class JSScriptArgument {
    protected final String mName;

    protected JSScriptArgument(String name) {
        mName = name;
    }

    /**
     * @return an argument with the given {@code name} and the given string value {@code value}
     */
    public static JSScriptStringArgument stringArg(String name, String value) {
        return new JSScriptStringArgument(name, value);
    }

    /**
     * @return a JS object with the given {@code name} and value obtained parsing the given {@code
     *     value}.
     * @throws JSONException if {@code value} doesn't represent a valid JSON object
     */
    public static JSScriptJsonArgument jsonArg(String name, String value) throws JSONException {
        // Creating the JSONObject just to parse value and cause a JSONException if invalid.
        new JSONObject(value);
        return new JSScriptJsonArgument(name, value);
    }

    /**
     * @return a JS array object with the given {@code name} and value obtained parsing the given
     *     {@code value}.
     * @throws JSONException if {@code value} doesn't represent a valid JSON array object
     */
    public static JSScriptJsonArrayArgument jsonArrayArg(String name, String value)
            throws JSONException {
        new JSONArray(value);
        return new JSScriptJsonArrayArgument(name, value);
    }

    /**
     * @return a JS object with the given {@code name} and value obtained parsing the given {@code
     *     value}.
     * @throws JSONException if {@code value} doesn't represent a valid JSON object
     */
    public static JSScriptJsonArgument jsonArg(String name, AdSelectionSignals value)
            throws JSONException {
        // TODO(b/238849930) Merge this validation with AdSelectionSignals validation
        new JSONObject(value.toString());
        return new JSScriptJsonArgument(name, value.toString());
    }

    /**
     * @return a JS object with the given {@code name} and value obtained parsing the given map
     *     {@code value}.
     * @throws JSONException if {@code value} doesn't represent a valid JSON object
     */
    public static JSScriptArgument stringMapToRecordArg(String name, Map<String, String> stringMap)
            throws JSONException {
        ImmutableList.Builder<JSScriptArgument> mapArg = ImmutableList.builder();
        for (Map.Entry<String, String> signal : stringMap.entrySet()) {
            mapArg.add(jsonArg(signal.getKey(), signal.getValue()));
        }
        return recordArg(name, mapArg.build());
    }

    /**
     * @return a JS array argument with the given {@code name} initialized with the values specified
     *     with {@code items}.
     */
    public static <T extends JSScriptArgument> JSScriptArrayArgument<T> arrayArg(
            String name, T... items) {
        return new JSScriptArrayArgument<>(name, asList(items));
    }

    /**
     * @return a JS array argument with the given {@code name} initialized with the values specified
     *     with {@code items}.
     */
    public static <T extends JSScriptArgument> JSScriptArrayArgument<T> arrayArg(
            String name, List<T> items) {
        return new JSScriptArrayArgument<>(name, items);
    }

    /**
     * @return a JS array argument with the given {@code name} initialized with the values specified
     *     with {@code items}.
     */
    public static JSScriptArrayArgument<JSScriptStringArgument> stringArrayArg(
            String name, List<String> items) {
        return new JSScriptArrayArgument<>(
                name,
                items.stream().map(str -> stringArg("ignored", str)).collect(Collectors.toList()));
    }

    /**
     * @return a JS array argument with the given {@code name} initialized with the values specified
     *     with {@code items}
     */
    public static <T extends Number>
            JSScriptArrayArgument<JSScriptNumericArgument<T>> numericArrayArg(
                    String name, List<T> items) {
        return new JSScriptArrayArgument<>(
                name,
                items.stream()
                        .map(num -> new JSScriptNumericArgument<>("ignored", num))
                        .collect(Collectors.toList()));
    }

    /**
     * @return a JS object with the given {@code name} and {@code fields} as fields values.
     */
    public static JSScriptRecordArgument recordArg(String name, JSScriptArgument... fields) {
        return new JSScriptRecordArgument(name, asList(fields));
    }

    /**
     * @return a JS object with the given {@code name} and {@code fields} as fields values.
     */
    public static JSScriptRecordArgument recordArg(String name, List<JSScriptArgument> fields) {
        return new JSScriptRecordArgument(name, fields);
    }

    /**
     * @return a numeric variable with the given {@code name} and {@code value}.
     */
    public static <T extends Number> JSScriptNumericArgument<T> numericArg(String name, T value) {
        return new JSScriptNumericArgument<>(name, value);
    }

    /**
     * @return the JS code to use to initialize the variable.
     */
    public String variableDeclaration() {
        return String.format("const %s = %s;", name(), initializationValue());
    }

    /**
     * @return name of the argument as referred in the call to the auction script function.
     */
    public String name() {
        return mName;
    }

    /**
     * @return the JS code to use to initialize the newly declared variable.
     */
    abstract String initializationValue();
}
