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

package org.apache.test.android;

import org.junit.Assert;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

public class AndroidFileUtils {

    private static final Path TEMP_DIR;

    static {
        try {
            TEMP_DIR = Files.createTempDirectory(AndroidFileUtils.class.getName());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Returns the URL string to the jar resource for a given input file path.
     * @return null if the input file is not found.
     */
    public static URL getInputFileUrl(String path) {
        Path p = Path.of(path);
        // Ignore the parts for the current directory.
        while (true) {
            if (p.getNameCount() == 0) {
                return null;
            }
            String first = p.getName(0).toString();
            if (!first.isEmpty() && !first.equals(".")) {
                break;
            }
            p = p.subpath(1, p.getNameCount());
        }
        String first = p.getName(0).toString();
        if (!first.equals("inputs")) {
            return null;
        }
        p = p.subpath(1, p.getNameCount());
        p = Path.of("api", p.toString());
        return AndroidFileUtils.class.getClassLoader().getResource(p.toString());
    }

    /**
     * Returns the URL string to the jar resource for a given input file path.
     * @return null if the input file is not found.
     */
    public static String getInputFileUrlString(String path) {
        URL url = getInputFileUrl(path);
        return url != null ? url.toExternalForm() : null;
    }
    /**
     * Return a File object to the path, and created all directories along the path.
     * @throws AssertionError if the directories can't be created.
     */
    public static File getOutputFile(String relativePath) {
        File target = TEMP_DIR.resolve(relativePath).toAbsolutePath().toFile();
        File parent = target.getParentFile();
        if (!parent.exists()) {
            Assert.assertTrue(parent.mkdirs());
        }
        return target;
    }
}
