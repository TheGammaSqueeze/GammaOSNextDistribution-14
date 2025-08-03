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

package android.server.wm.intent;

import android.os.Environment;
import android.server.wm.intent.Persistence;

import com.google.common.collect.Lists;

import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class IoUtils {
    private static final int JSON_INDENTATION_LEVEL = 4;

    public static void writeToDocumentsStorage(
            Persistence.TestCase testCase, int number, String dirName)
            throws JSONException, IOException {
        File documentsDirectory =
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        Path testsFilePath = documentsDirectory.toPath().resolve(dirName);

        if (!Files.exists(testsFilePath)) {
            Files.createDirectories(testsFilePath);
        }
        Files.write(
                testsFilePath.resolve("test-" + number + ".json"),
                Lists.newArrayList(testCase.toJson().toString(JSON_INDENTATION_LEVEL)));
    }
}
