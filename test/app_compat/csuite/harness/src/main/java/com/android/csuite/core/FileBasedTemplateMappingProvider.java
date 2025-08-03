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
import com.google.common.base.Preconditions;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Accepts files that contains template mapping entries. */
public class FileBasedTemplateMappingProvider implements TemplateMappingProvider {
    @VisibleForTesting static final String TEMPLATE_MAPPING_FILE_OPTION = "template-mapping-file";

    @VisibleForTesting static final String COMMENT_LINE_PREFIX = "#";
    @VisibleForTesting static final String MODULE_TEMPLATE_SEPARATOR = " ";

    @Option(
            name = TEMPLATE_MAPPING_FILE_OPTION,
            description = "Template mapping file paths.",
            importance = Importance.NEVER)
    private Set<File> mTemplateMappingFiles = new HashSet<>();

    @Override
    public Stream<Map.Entry<String, String>> get() throws IOException {
        List<Map.Entry<String, String>> entries = new ArrayList<>();

        // Using for loop instead of stream here so that exceptions can be caught early.
        for (File file : mTemplateMappingFiles) {
            List<String> lines =
                    Files.readAllLines(file.toPath()).stream()
                            .map(String::trim)
                            .filter(FileBasedTemplateMappingProvider::isNotCommentLine)
                            .distinct()
                            .collect(Collectors.toList());
            for (String line : lines) {
                String[] pair = line.split(MODULE_TEMPLATE_SEPARATOR);
                Preconditions.checkArgument(
                        pair.length == 2, "Unrecognized template map format " + line);
                entries.add(new AbstractMap.SimpleEntry<>(pair[0].trim(), pair[1].trim()));
            }
        }

        return entries.stream();
    }

    private static boolean isNotCommentLine(String text) {
        // Check the text is not an empty string and not a comment line.
        return !text.isEmpty() && !text.startsWith(COMMENT_LINE_PREFIX);
    }
}
