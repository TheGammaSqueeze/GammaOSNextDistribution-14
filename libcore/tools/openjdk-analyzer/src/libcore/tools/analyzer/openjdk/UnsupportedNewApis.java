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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * An instance of {@link UnsupportedNewApis } contains a set of class / method / field signatures
 * read from {@link #FILE_NAME} in the jar resources.
 */
public class UnsupportedNewApis {

    public static final String FILE_NAME = "unsupported_new_apis.txt";

    private static UnsupportedNewApis sInstance;

    private final Set<String> apis;

    private UnsupportedNewApis() {
         apis = parse();
    }

    public static synchronized UnsupportedNewApis getInstance() {
        if (sInstance == null) {
            sInstance = new UnsupportedNewApis();
        }
        return sInstance;
    }

    public boolean contains(String internalClassName) {
        return apis.contains(internalClassName);
    }

    public boolean contains(SignaturesCollector.Method method) {
        return apis.contains(method.toString());
    }

    public boolean contains(SignaturesCollector.Field field) {
        String signature = field.getOwner() + "#" + field.getName() + ":" + field.getDesc();
        return apis.contains(signature);
    }

    private static Set<String> parse() {
        try (InputStream in = UnsupportedNewApis.class.getResourceAsStream(FILE_NAME)) {
            List<String> result = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.strip();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }

                    result.add(line);
                }
            }
            return new HashSet<>(result);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
