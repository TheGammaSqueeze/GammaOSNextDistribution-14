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

package com.android.adservices.data.customaudience;

import android.adservices.common.AdData;

import androidx.annotation.NonNull;

import com.android.adservices.data.common.DBAdData;

import org.json.JSONException;
import org.json.JSONObject;

/** Strategy for doing optional feature-flagged parts of the ADData conversion */
public interface AdDataOptionalConversionStrategy {
    /**
     * Fills a part of the given {@link JSONObject} with the data from {@link DBAdData}.
     *
     * @param adData the {@link DBAdData} object to serialize
     * @param jsonObject the target json serialization of the AdData object
     */
    void toJson(DBAdData adData, JSONObject jsonObject) throws JSONException;

    /**
     * Deserialize {@link DBAdData} from {@link JSONObject}.
     *
     * @param json the {@link JSONObject} object to dwserialize
     * @param adDataBuilder the {@link DBAdData.Builder} to be filled from the json
     */
    void fromJson(JSONObject json, DBAdData.Builder adDataBuilder) throws JSONException;

    /**
     * Parse parcelable {@link AdData} to storage model {@link DBAdData}.
     *
     * @param parcelable the service model.
     * @param adDataBuilder the target storage model to be filled
     */
    void fromServiceObject(@NonNull AdData parcelable, @NonNull DBAdData.Builder adDataBuilder);
}
