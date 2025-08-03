/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.csuite.core;

import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;

import com.google.common.annotations.VisibleForTesting;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

/** Accepts template mapping from command line option. */
public class CommandLineTemplateMappingProvider implements TemplateMappingProvider {
    @VisibleForTesting static final String TEMPLATE_MAPPING_OPTION = "template-mapping";

    @Option(
            name = TEMPLATE_MAPPING_OPTION,
            description = "Optional template mapping for modules.",
            importance = Importance.NEVER)
    private Map<String, String> mTemplateMappings = new HashMap<>();

    @Override
    public Stream<Map.Entry<String, String>> get() throws IOException {
        return mTemplateMappings.entrySet().stream();
    }
}
