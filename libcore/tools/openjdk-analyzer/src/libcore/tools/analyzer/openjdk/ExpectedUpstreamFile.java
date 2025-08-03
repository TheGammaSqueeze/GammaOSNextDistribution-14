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

package libcore.tools.analyzer.openjdk;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * It partially mirrors the ExpectedUpstreamFile class in
 * libcore/tools/expected_upstream/common_util.py in Python.
 *
 * @see #readAllEntries to obtain the list of entries.
 */
class ExpectedUpstreamFile {
    private final Path mPath;

    public ExpectedUpstreamFile() {
        this.mPath = AndroidHostEnvUtil.getAndroidBuildTop().resolve("libcore/EXPECTED_UPSTREAM");
    }

    public List<ExpectedUpstreamEntry> readAllEntries() throws IOException {
        List<ExpectedUpstreamEntry> result = new ArrayList<>();
        try (BufferedReader in = new BufferedReader(new FileReader(mPath.toFile()))) {
            String line;
            StringBuilder commentLines = new StringBuilder();
            while ((line = in.readLine()) != null) {
                line = line.strip();
                if (line.isEmpty()) {
                    continue;
                } else if (line.startsWith("#")) {
                    commentLines.append(line).append('\n');
                    continue;
                }

                ExpectedUpstreamEntry entry = parseLine(line, commentLines.toString());
                result.add(entry);
                commentLines = new StringBuilder();
            }
        }
        return result;
    }

    private static ExpectedUpstreamEntry parseLine(String line, String commentLines) {
        String[] items = line.split(",");
        int size = items.length;
        if (size != 3) {
            throw new IllegalStateException("The size must be 3, but is " + size + ". "
                    + "The line is " + line);
        }
        return new ExpectedUpstreamEntry(items[0], items[1], items[2], commentLines);
    }

    public static final class ExpectedUpstreamEntry {
        public final String dstPath;
        public final String gitRef;
        public final String srcPath;
        public final String commentLines;

        ExpectedUpstreamEntry(String dstPath, String gitRef, String srcPath,
                String commentLines) {
            this.dstPath = dstPath;
            this.gitRef = gitRef;
            this.srcPath = srcPath;
            this.commentLines = commentLines;
        }
    }
}
