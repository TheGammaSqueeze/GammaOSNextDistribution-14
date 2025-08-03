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

import com.android.tradefed.config.IConfiguration;
import com.android.tradefed.config.Option;
import com.android.tradefed.config.Option.Importance;

import com.google.common.annotations.VisibleForTesting;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/** A module info provider that accepts files that contains package names. */
public final class PackagesFileModuleInfoProvider implements ModuleInfoProvider {
    @VisibleForTesting static final String PACKAGES_FILE_OPTION = "packages-file";
    @VisibleForTesting static final String COMMENT_LINE_PREFIX = "#";
    @VisibleForTesting static final String PACKAGE_PLACEHOLDER = "{package}";

    @Option(
            name = PACKAGES_FILE_OPTION,
            description =
                    "File paths that contain package names separated by newline characters."
                        + " Comment lines are supported only if the lines start with double slash."
                        + " Trailing comments are not supported. Empty lines are ignored.",
            importance = Importance.NEVER)
    private final Set<File> mPackagesFiles = new HashSet<>();

    @Override
    public Stream<ModuleInfoProvider.ModuleInfo> get(IConfiguration configuration)
            throws IOException {
        ModuleTemplate moduleTemplate = ModuleTemplate.loadFrom(configuration);

        return mPackagesFiles.stream()
                .flatMap(
                        file -> {
                            try {
                                return Files.readAllLines(file.toPath()).stream();
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        })
                .map(String::trim)
                .filter(PackagesFileModuleInfoProvider::isNotCommentLine)
                .distinct()
                .map(
                        packageName ->
                                new ModuleInfoProvider.ModuleInfo(
                                        packageName,
                                        moduleTemplate.substitute(
                                                packageName,
                                                Map.of(PACKAGE_PLACEHOLDER, packageName))));
    }

    private static boolean isNotCommentLine(String text) {
        // Check the text is not an empty string and not a comment line.
        return !text.isEmpty() && !text.startsWith(COMMENT_LINE_PREFIX);
    }
}
