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

package com.android.server.appsearch.contactsindexer;

import android.util.ArrayMap;
import android.util.ArraySet;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CountryCodeUtils {
    private CountryCodeUtils() {
    }

    // Maps dialing code starting with "+" to its primary corresponding ISO 3166-1 alpha-2
    // country code.
    // E.g. "+1" -> "US".
    public static final Map<String, String> COUNTRY_TO_REGIONAL_CODE;

    // A set of country dialing code. E.g. "+1", "+86" etc.
    public static final Set<String> COUNTRY_DIALING_CODE;

    // Totally 240 entries.
    static {
        Map<Integer, List<String>> countryCodeToRegionCodeMap =
                CountryCodeToRegionCodeMap.getCountryCodeToRegionCodeMap();
        Map<String, String> transformedCountryCodeToRegionCode = new ArrayMap<>();
        for (Map.Entry<Integer, List<String>> entry : countryCodeToRegionCodeMap.entrySet()) {
            Integer dialingCode = entry.getKey();
            List<String> regionCodes = entry.getValue();
            String dialingCodeString = "+" + dialingCode;
            if (!regionCodes.isEmpty()) {
                // If we have a list of regional codes, just get the 1st one since it is the
                // primary country for the same country code.
                transformedCountryCodeToRegionCode.put(dialingCodeString, regionCodes.get(0));
            }
        }
        COUNTRY_TO_REGIONAL_CODE = Collections.unmodifiableMap(transformedCountryCodeToRegionCode);
        COUNTRY_DIALING_CODE = Collections.unmodifiableSet(COUNTRY_TO_REGIONAL_CODE.keySet());
    }
}
