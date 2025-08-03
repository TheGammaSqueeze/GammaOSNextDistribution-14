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
package com.android.tradefed.util;

import com.android.tradefed.config.ConfigurationDescriptor;
import com.android.tradefed.config.IConfiguration;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;


/** Contains common utility methods for checking module. */
public class ModuleTestTypeUtil {

    public static final String TEST_TYPE_KEY = "test-type";
    public static final String TEST_TYPE_VALUE_PERFORMANCE = "performance";

    /**
     * Check whether the configuration is a performance module.
     *
     * @param config The config to check.
     * @return true if the config is a performance module.
     */
    public static boolean isPerformanceModule(IConfiguration config) {
        List<String> matched =
                getMatchedConfigTestTypes(config, Arrays.asList(TEST_TYPE_VALUE_PERFORMANCE));
        return !matched.isEmpty();
    }

    /**
     * Get the declared test types of the configuration with a match in the allowed list.
     *
     * @param config The config to check.
     * @param testTypesToMatch The test types to match.
     * @return matched test types of the config or an empty list if none matches.
     */
    public static List<String> getMatchedConfigTestTypes(
            IConfiguration config, List<String> testTypesToMatch) {
        List<String> matchedTypes = new ArrayList<>();
        ConfigurationDescriptor cd = config.getConfigurationDescription();
        if (cd == null) {
            throw new RuntimeException(config + ": configuration descriptor is null");
        }
        List<String> testTypes = cd.getMetaData(TEST_TYPE_KEY);
        if (testTypes != null) {
            for (String testType : testTypes) {
                if (testTypesToMatch.contains(testType)) {
                    matchedTypes.add(testType);
                }
            }
        }
        return matchedTypes;
    }
}
