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

import static com.android.adservices.service.js.JSScriptArgument.recordArg;
import static com.android.adservices.service.js.JSScriptArgument.stringArg;

import com.android.adservices.data.adselection.CustomAudienceSignals;
import com.android.adservices.service.js.JSScriptArgument;
import com.android.adservices.service.js.JSScriptArrayArgument;

import com.google.common.collect.ImmutableList;

import org.json.JSONException;

import java.util.List;

/**
 * A utility class to convert instances of {@link CustomAudienceSignals} into {@link
 * JSScriptArgument}, extracts out only buyer and custom audience name to be passed along into
 * scoreAd execution
 */
public class CustomAudienceScoringSignalsArgumentUtil {
    // TODO: (b/228094391): Put these common constants in a separate class
    public static final String BUYER_FIELD_NAME = "buyer";
    public static final String NAME_FIELD_NAME = "name";

    // No instance of this class is supposed to be created
    private CustomAudienceScoringSignalsArgumentUtil() {}

    /**
     * @return A {@link JSScriptArgument} with the given {@code name} to represent this instance of
     *     {@link CustomAudienceScoringSignalsArgumentUtil}
     * @throws JSONException if any of the signals in this class is not valid JSON.
     */
    public static JSScriptArgument asScriptArgument(
            String name, CustomAudienceSignals customAudienceSignals) throws JSONException {
        return recordArg(
                name,
                stringArg(BUYER_FIELD_NAME, customAudienceSignals.getBuyer().toString()),
                stringArg(NAME_FIELD_NAME, customAudienceSignals.getName()));
    }

    /**
     * @return A {@link JSScriptArgument} with the given {@code name} to represent an array of
     *     {@link CustomAudienceScoringSignalsArgumentUtil} list
     * @throws JSONException if any of the signals in this class is not valid JSON.
     */
    public static JSScriptArrayArgument asScriptArgument(
            String name, List<CustomAudienceSignals> customAudienceSignalsList)
            throws JSONException {
        ImmutableList.Builder<JSScriptArgument> caSignalsArgumentList =
                new ImmutableList.Builder<>();

        for (CustomAudienceSignals caSignals : customAudienceSignalsList) {
            caSignalsArgumentList.add(asScriptArgument("ignored", caSignals));
        }
        return JSScriptArgument.arrayArg(name, caSignalsArgumentList.build());
    }
}
