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

package com.android.csuite.core;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;

import org.junit.Assert;

import java.nio.file.Files;
import java.nio.file.Path;

public final class RoboLoginConfigProvider {
    @VisibleForTesting static final String ROBOSCRIPT_FILE_SUFFIX = ".roboscript";
    @VisibleForTesting static final String CRAWL_GUIDANCE_FILE_SUFFIX = "_cg.txt";
    private static final String ROBOSCRIPT_CMD_FLAG = "--robo-script-file";
    private static final String CRAWL_GUIDANCE_CMD_FLAG = "--text-guide-file";

    private final Path mLoginFilesDir;

    public RoboLoginConfigProvider(Path loginFilesDir) {
        Assert.assertTrue(
                "Please provide a valid directory that contains crawler login files.",
                Files.isDirectory(loginFilesDir));
        this.mLoginFilesDir = loginFilesDir;
    }

    /**
     * Finds the config file to use from the given directory for the corresponding app package and
     * returns the {@link RoboLoginConfig} that contains the resulting login arguments. The
     * directory should contain only one config file per package name. If both Roboscript and
     * CrawlGuidance files are present, only the Roboscript file will be used."
     */
    public RoboLoginConfig findConfigFor(String packageName, boolean isUtpClient) {
        Path crawlGuidanceFile = mLoginFilesDir.resolve(packageName + CRAWL_GUIDANCE_FILE_SUFFIX);
        Path roboScriptFile = mLoginFilesDir.resolve(packageName + ROBOSCRIPT_FILE_SUFFIX);

        if (Files.exists(roboScriptFile) && !isUtpClient) {
            return new RoboLoginConfig(
                    ImmutableList.of(ROBOSCRIPT_CMD_FLAG, roboScriptFile.toString()));
        }

        if (Files.exists(crawlGuidanceFile) && !isUtpClient) {
            return new RoboLoginConfig(
                    ImmutableList.of(CRAWL_GUIDANCE_CMD_FLAG, crawlGuidanceFile.toString()));
        }

        if (Files.exists(roboScriptFile) && isUtpClient) {
            return new RoboLoginConfig(
                    ImmutableList.of(
                            "--crawler-asset", "robo.script=" + roboScriptFile.toString()));
        }

        if (Files.exists(crawlGuidanceFile) && isUtpClient) {
            return new RoboLoginConfig(
                    ImmutableList.of("--crawl-guidance-proto-path", crawlGuidanceFile.toString()));
        }

        return new RoboLoginConfig(ImmutableList.of());
    }

    /*
     * A class returned by RoboLoginConfigProvider that contains the login arguments
     * to be passed to the crawler.
     */
    public static final class RoboLoginConfig {
        private final ImmutableList<String> mLoginArgs;

        public RoboLoginConfig(ImmutableList<String> loginArgs) {
            this.mLoginArgs = loginArgs;
        }

        /* Returns the login arguments for this config which can be passed to the crawler. */
        public ImmutableList<String> getLoginArgs() {
            return mLoginArgs;
        }
    }
}
