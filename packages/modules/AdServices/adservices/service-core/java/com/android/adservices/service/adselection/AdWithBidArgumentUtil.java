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

package com.android.adservices.service.adselection;

import static com.android.adservices.service.js.JSScriptArgument.numericArg;
import static com.android.adservices.service.js.JSScriptArgument.recordArg;

import android.adservices.adselection.AdWithBid;
import android.adservices.common.AdData;

import com.android.adservices.service.js.JSScriptArgument;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Objects;

/**
 * A wrapper class for {@link android.adservices.adselection.AdWithBid} to support the conversion to
 * JS Script parameter and the from a JS result string.
 */
public final class AdWithBidArgumentUtil {
    static final String AD_FIELD_NAME = "ad";
    static final String BID_FIELD_NAME = "bid";

    private final AdDataArgumentUtil mAdDataArgumentUtil;

    public AdWithBidArgumentUtil(AdDataArgumentUtil adDataArgumentUtil) {
        Objects.requireNonNull(adDataArgumentUtil);
        mAdDataArgumentUtil = adDataArgumentUtil;
    }

    /**
     * @return an instance of {@link AdWithBid} reading the advert and bid value from JSON.
     * @throws IllegalArgumentException if the JSON doesn't contain valid information.
     */
    public AdWithBid parseJsonResponse(JSONObject jsonObject) throws IllegalArgumentException {
        try {
            AdData adData =
                    mAdDataArgumentUtil.parseJsonResponse(jsonObject.getJSONObject(AD_FIELD_NAME));
            return new AdWithBid(adData, jsonObject.getDouble(BID_FIELD_NAME));
        } catch (JSONException e) {
            throw new IllegalArgumentException("Invalid value for advert data", e);
        }
    }

    /**
     * @return a {@link JSScriptArgument} with name {@code name} for the JSON object representing
     *     this advert.
     * @throws JSONException If the {@link AdData} in {@code adWithBid} is not valid JSON.
     */
    public JSScriptArgument asScriptArgument(String name, AdWithBid adWithBid)
            throws JSONException {
        return recordArg(
                name,
                mAdDataArgumentUtil.asScriptArgument(AD_FIELD_NAME, adWithBid.getAdData()),
                numericArg(BID_FIELD_NAME, adWithBid.getBid()));
    }
}
