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

package com.android.adservices.common;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class JsonFixture {
    /**
     * Returns a {@link JSONObject} representation of a valid JSON object serialized as a string.
     *
     * <p>This helps verify that when comparing strings before and after conversion to {@link
     * JSONObject}, equality from an org.json perspective is guaranteed.
     */
    public static String formatAsOrgJsonJSONObjectString(String inputJsonString)
            throws JSONException {
        return new JSONObject(inputJsonString).toString();
    }

    /** Modifies the target JSONObject in-place to add harmless junk values. */
    public static void addHarmlessJunkValues(JSONObject target) throws JSONException {
        target.put("junk_int", 1);
        target.put("junk_boolean", true);
        target.put("junk_string", "harmless junk");
        target.put("junk_null", JSONObject.NULL);
        target.put("junk_object", new JSONObject("{'harmless':true,'object':1}"));
    }

    /** Modifies the target JSONArray in-place to add harmless junk values. */
    public static void addHarmlessJunkValues(JSONArray target) throws JSONException {
        target.put(1);
        target.put(true);
        target.put(JSONObject.NULL);
        target.put(new JSONObject("{'harmless':true,'object':1}"));
    }
}
