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

import com.google.gson.stream.JsonReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Pre upload hook that ensures art-buildbot expectation files (files under //art/tools ending with
 * "_failures.txt", e.g. //art/tools/libcore_failures.txt) are well-formed json files.
 *
 * It makes basic validation of the keys but does not cover all the cases. Parser structure is
 * based on external/vogar/src/vogar/ExpectationStore.java.
 *
 * Hook is set up in //art/PREUPLOAD.cfg See also //tools/repohooks/README.md
 */
class PresubmitJsonLinter {

    private static final int FLAGS = Pattern.MULTILINE | Pattern.DOTALL;
    private static final Set<String> RESULTS = new HashSet<>();

    static {
        RESULTS.addAll(List.of(
                "UNSUPPORTED",
                "COMPILE_FAILED",
                "EXEC_FAILED",
                "EXEC_TIMEOUT",
                "ERROR",
                "SUCCESS"
        ));
    }

    public static void main(String[] args) {
        for (String arg : args) {
            info("Checking " + arg);
            checkExpectationFile(arg);
        }
    }

    private static void info(String message) {
        System.err.println(message);
    }

    private static void error(String message) {
        System.err.println(message);
        System.exit(1);
    }

    private static void checkExpectationFile(String arg) {
        JsonReader reader;
        try {
            reader = new JsonReader(new FileReader(arg));
        } catch (FileNotFoundException e) {
            error("File '" + arg + "' is not found");
            return;
        }
        reader.setLenient(true);
        try {
            reader.beginArray();
            while (reader.hasNext()) {
                readExpectation(reader);
            }
            reader.endArray();
        } catch (IOException e) {
            error("Malformed json: " + reader);
        }
    }

    private static void readExpectation(JsonReader reader) throws IOException {
        Set<String> names = new LinkedHashSet<String>();
        Set<String> tags = new LinkedHashSet<String>();
        boolean readResult = false;
        boolean readDescription = false;

        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            switch (name) {
                case "result":
                    String result = reader.nextString();
                    if (!RESULTS.contains(result)) {
                        error("Invalid 'result' value: '" + result +
                                "'. Expected one of " + String.join(", ", RESULTS) +
                                ". " + reader);
                    }
                    readResult = true;
                    break;
                case "substring": {
                    try {
                        Pattern.compile(
                                ".*" + Pattern.quote(reader.nextString()) + ".*", FLAGS);
                    } catch (PatternSyntaxException e) {
                        error("Malformed 'substring' value: " + reader);
                    }
                }
                case "pattern": {
                    try {
                        Pattern.compile(reader.nextString(), FLAGS);
                    } catch (PatternSyntaxException e) {
                        error("Malformed 'pattern' value: " + reader);
                    }
                    break;
                }
                case "failure":
                    names.add(reader.nextString());
                    break;
                case "description":
                    reader.nextString();
                    readDescription = true;
                    break;
                case "name":
                    names.add(reader.nextString());
                    break;
                case "names":
                    readStrings(reader, names);
                    break;
                case "tags":
                    readStrings(reader, tags);
                    break;
                case "bug":
                    reader.nextLong();
                    break;
                case "modes":
                    readModes(reader);
                    break;
                case "modes_variants":
                    readModesAndVariants(reader);
                    break;
                default:
                    error("Unknown key '" + name + "' in expectations file");
                    reader.skipValue();
                    break;
            }
        }
        reader.endObject();

        if (names.isEmpty()) {
            error("Missing 'name' or 'failure' key in " + reader);
        }
        if (!readResult) {
            error("Missing 'result' key in " + reader);
        }
        if (!readDescription) {
            error("Missing 'description' key in " + reader);
        }
    }

    private static void readStrings(JsonReader reader, Set<String> output) throws IOException {
        reader.beginArray();
        while (reader.hasNext()) {
            output.add(reader.nextString());
        }
        reader.endArray();
    }

    private static void readModes(JsonReader reader) throws IOException {
        reader.beginArray();
        while (reader.hasNext()) {
            reader.nextString();
        }
        reader.endArray();
    }

    /**
     * Expected format: mode_variants: [["host", "X32"], ["host", "X64"]]
     */
    private static void readModesAndVariants(JsonReader reader) throws IOException {
        reader.beginArray();
        while (reader.hasNext()) {
            reader.beginArray();
            reader.nextString();
            reader.nextString();
            reader.endArray();
        }
        reader.endArray();
    }
}