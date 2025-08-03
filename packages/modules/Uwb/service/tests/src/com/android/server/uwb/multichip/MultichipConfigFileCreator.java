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

package com.android.server.uwb.multichip;

import org.junit.rules.TemporaryFolder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public final class MultichipConfigFileCreator {
    private MultichipConfigFileCreator() {
    }

    private static final String ASSETS_DIR = "assets/";
    private static final String ONE_CHIP_CONFIG_FILE = "singleChipConfig.xml";
    private static final String TWO_CHIP_CONFIG_FILE = "twoChipConfig.xml";
    private static final String NO_POSITION_CONFIG_FILE = "noPositionConfig.xml";

    public static File createOneChipFileFromResource(TemporaryFolder tempFolder, Class testClass)
            throws Exception {
        return createFileFromResource(ONE_CHIP_CONFIG_FILE, tempFolder, testClass);
    }

    public static File createTwoChipFileFromResource(TemporaryFolder tempFolder, Class testClass)
            throws Exception {
        return createFileFromResource(TWO_CHIP_CONFIG_FILE, tempFolder, testClass);
    }

    public static File createNoPositionFileFromResource(TemporaryFolder tempFolder, Class testClass)
            throws Exception {
        return createFileFromResource(NO_POSITION_CONFIG_FILE, tempFolder, testClass);
    }

    private static File createFileFromResource(String filename, TemporaryFolder tempFolder,
            Class testClass) throws Exception {
        InputStream in = testClass.getClassLoader().getResourceAsStream(ASSETS_DIR + filename);
        File file = tempFolder.newFile(filename);

        BufferedReader reader = new BufferedReader(new InputStreamReader(in));
        FileOutputStream out = new FileOutputStream(file);

        String line;

        while ((line = reader.readLine()) != null) {
            out.write(line.getBytes(StandardCharsets.UTF_8));
        }

        out.flush();
        out.close();
        return file;
    }
}
