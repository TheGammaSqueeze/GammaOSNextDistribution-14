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
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.util.AaptParser;

import com.google.common.annotations.VisibleForTesting;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/** Generates modules from package files in a directory. */
public final class DirectoryBasedModuleInfoProvider implements ModuleInfoProvider {
    @VisibleForTesting static final String DIRECTORY_OPTION = "directory";
    private static final String UNKNOWN_PACKAGE = "UNKNOWN_PACKAGE";

    @VisibleForTesting
    static final String PACKAGE_INSTALL_FILE_PLACEHOLDER = "{package_install_file}";

    @VisibleForTesting static final String PACKAGE_PLACEHOLDER = "{package}";

    // TODO(yuexima): Add split APK directories support.
    @Option(
            name = DIRECTORY_OPTION,
            description =
                    "A directory that contains package installation files for scanning. Modules"
                        + " will be generated using the package installation file names as the"
                        + " module names. Currently, only non-split APK files placed on the root"
                        + " of the directory are scanned. Directories and other type of files will"
                        + " be ignored.",
            importance = Importance.NEVER)
    private final Set<File> mDirectories = new HashSet<>();

    private final PackageNameParser mPackageNameParser;

    public DirectoryBasedModuleInfoProvider() {
        this(new AaptPackageNameParser());
    }

    @VisibleForTesting
    DirectoryBasedModuleInfoProvider(PackageNameParser packageNameParser) {
        mPackageNameParser = packageNameParser;
    }

    @Override
    public Stream<ModuleInfo> get(IConfiguration configuration) throws IOException {
        ModuleTemplate template = ModuleTemplate.loadFrom(configuration);
        return mDirectories.stream()
                .flatMap(dir -> Arrays.stream(dir.listFiles()))
                .filter(File::isFile)
                .filter(file -> file.getPath().toLowerCase().endsWith(".apk"))
                .map(
                        file -> {
                            try {
                                return new ModuleInfo(
                                        file.getName(),
                                        template.substitute(
                                                file.getName(),
                                                Map.of(
                                                        PACKAGE_PLACEHOLDER,
                                                        mPackageNameParser.parsePackageName(file),
                                                        PACKAGE_INSTALL_FILE_PLACEHOLDER,
                                                        file.getPath())));
                            } catch (IOException e) {
                                throw new UncheckedIOException(e);
                            }
                        });
    }

    private static final class AaptPackageNameParser implements PackageNameParser {
        @Override
        public String parsePackageName(File apkFile) throws IOException {
            AaptParser parseResult = AaptParser.parse(apkFile, AaptParser.AaptVersion.AAPT2);
            if (parseResult == null || parseResult.getPackageName() == null) {
                CLog.e(String.format("Failed to parse package name with AAPT for %s", apkFile));
                return UNKNOWN_PACKAGE + "_" + apkFile.getName();
            }
            return parseResult.getPackageName();
        }
    }

    @VisibleForTesting
    interface PackageNameParser {
        String parsePackageName(File apkFile) throws IOException;
    }
}
