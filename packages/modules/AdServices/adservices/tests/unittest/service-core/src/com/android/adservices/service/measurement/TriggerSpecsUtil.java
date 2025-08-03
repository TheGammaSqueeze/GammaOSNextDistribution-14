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

package com.android.adservices.service.measurement;

import org.json.JSONArray;
import org.json.JSONException;

public class TriggerSpecsUtil {
    /** Converts a valid JSON string to an array of TriggerSpec */
    public static TriggerSpec[] triggerSpecArrayFrom(String json) throws JSONException {
        JSONArray jsonArray = new JSONArray(json);
        TriggerSpec[] triggerSpecArray = new TriggerSpec[jsonArray.length()];
        for (int i = 0; i < jsonArray.length(); i++) {
            triggerSpecArray[i] = new TriggerSpec.Builder(jsonArray.getJSONObject(i)).build();
        }
        return triggerSpecArray;
    }
}
