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
package com.android.tradefed.observatory;

import com.android.tradefed.config.Option;

import java.util.HashSet;
import java.util.Set;

public class ConfigurationTestMappingParserSettings {
    @Option(
            name = "test-mapping-allowed-tests-list",
            description =
                    "A list of artifacts that contains allowed tests. Only tests in the lists "
                            + "will be run. If no list is specified, the tests will not be "
                            + "filtered by allowed tests.")
    public Set<String> mAllowedTestLists = new HashSet<>();
}
