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

import com.android.adservices.data.signals.DBProtectedSignal;

import org.json.JSONException;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Set;

/** A strategy for processing an individual command from the signals update JSON. */
public interface UpdateProcessor {

    /**
     * Gets the name of the processor, should match the key for it in the JSON.
     *
     * @return A string key for this processor to be used in parsing the update JSON.
     */
    String getName();

    /**
     * @param updates A JSONObject or JSONArray describing the updates to be made by this processor.
     * @param current A map from keys to signals currently under those keys. Note that byte buffers
     *     must have been generated with .wrap().
     * @return An output object describing: 1. Which keys this processor has modified or could have
     *     modified. 2. Which signals should be removed. 3. Which signals should be added.
     * @throws JSONException In the event the passed in JSON is invalid
     */
    UpdateOutput processUpdates(Object updates, Map<ByteBuffer, Set<DBProtectedSignal>> current)
            throws JSONException;
}
