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

package com.android.adservices.service.customaudience;

import com.android.adservices.data.common.DBAdData;

import org.json.JSONException;
import org.json.JSONObject;

/** Interface for reading filter fields from a JSON object into a DBAdData builder. */
public interface ReadFiltersFromJsonStrategy {
    /**
     * Adds filtering fields to the provided AdData builder if enabled.
     *
     * @throws JSONException if the key is found but the schema is incorrect
     * @throws NullPointerException if the key found by the field is null
     */
    void readFilters(DBAdData.Builder adDataBuilder, JSONObject adDataJsonObj)
            throws JSONException, NullPointerException, IllegalArgumentException;
}
