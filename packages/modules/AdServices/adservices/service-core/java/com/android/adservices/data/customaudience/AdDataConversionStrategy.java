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

/** Strategy for converting to and from an AdData object in different parts of the code */
public interface AdDataConversionStrategy {
    /**
     * Serialize {@link DBAdData} to {@link JSONObject}.
     *
     * @param adData the {@link DBAdData} object to serialize
     * @return the json serialization of the AdData object
     */
    JSONObject toJson(DBAdData adData) throws JSONException;

    /**
     * Deserialize {@link DBAdData} to {@link JSONObject}.
     *
     * @param json the {@link JSONObject} object to dwserialize
     * @return the {@link DBAdData} deserialized from the json
     */
    DBAdData.Builder fromJson(JSONObject json) throws JSONException;

    /**
     * Parse parcelable {@link AdData} to storage model {@link DBAdData}.
     *
     * @param parcelable the service model.
     * @return storage model
     */
    @NonNull
    DBAdData.Builder fromServiceObject(@NonNull AdData parcelable);
}
