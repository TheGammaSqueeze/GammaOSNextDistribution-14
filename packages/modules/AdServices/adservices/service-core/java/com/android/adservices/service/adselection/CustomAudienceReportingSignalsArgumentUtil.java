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

import org.json.JSONException;

/**
 * A utility class to convert instances of {@link CustomAudienceSignals} into {@link
 * JSScriptArgument}. It strips out extraneous information from {@link CustomAudienceSignals} and
 * only passes the data relevant for reporting.
 */
public class CustomAudienceReportingSignalsArgumentUtil {

    // TODO: (b/228094391): Put these common constants in a separate class
    public static final String NAME_FIELD_NAME = "name";

    // No instance of this class is supposed to be created
    private CustomAudienceReportingSignalsArgumentUtil() {}

    /**
     * @return A {@link JSScriptArgument} with the given {@code name} to represent this instance of
     *     {@link CustomAudienceReportingSignalsArgumentUtil}
     * @throws JSONException if any of the signals in this class is not valid JSON.
     */
    public static JSScriptArgument asScriptArgument(
            String name, CustomAudienceSignals customAudienceSignals) throws JSONException {
        return recordArg(name, stringArg(NAME_FIELD_NAME, customAudienceSignals.getName()));
    }
}
