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

package com.android.tests.chromium.host;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;

public class GTestsMetaData {

    private final boolean isOutputParsedCorrectly;
    private final int totalTests;
    private final int failedTests;

    private GTestsMetaData(int totalTests, int failedTests, boolean isOutputParsedCorrectly) {
        this.totalTests = totalTests;
        this.failedTests = failedTests;
        this.isOutputParsedCorrectly = isOutputParsedCorrectly;
    }

    public boolean isOutputParsedCorrectly() {
        return isOutputParsedCorrectly;
    }

    public static GTestsMetaData parseFile(File gtestOutputFile) throws IOException {
        try (FileReader fileReader = new FileReader(gtestOutputFile)) {
            JsonElement root = JsonParser.parseReader(fileReader);
            if (!root.isJsonObject()) {
                return new GTestsMetaData(0, 0, false);
            }
            return new GTestsMetaData(root.getAsJsonObject().get("tests").getAsInt(),
                    root.getAsJsonObject().get("failures").getAsInt(), true);
        }
    }

    public boolean hasAnyFailures() {
        return failedTests > 0;
    }

    public int getTotalTests() {
        return totalTests;
    }
}
