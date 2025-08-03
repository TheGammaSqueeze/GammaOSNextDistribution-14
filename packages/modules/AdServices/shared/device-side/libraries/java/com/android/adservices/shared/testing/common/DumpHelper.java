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
package com.android.adservices.shared.testing.common;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.fail;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Provides helpers for {@code dump()}-related tests. */
public final class DumpHelper {

    /** Calls {@code dump()} in the given dumper, and return its output. */
    public static String dump(Dumper dumper) throws Exception {
        Objects.requireNonNull(dumper);
        try (StringWriter sw = new StringWriter()) {
            PrintWriter pw = new PrintWriter(sw);
            dumper.dump(pw);
            pw.flush();
            return sw.toString();
        }
    }

    /** Asserts that all lines of a {@code dump} start with the given {@code prefix}. */
    public static String[] assertDumpHasPrefix(String dump, String prefix) {
        assertWithMessage("content of dump()").that(dump).isNotEmpty();

        String[] lines = dump.split("\n");
        List<Integer> violatedLineNumbers = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (!line.startsWith(prefix)) {
                violatedLineNumbers.add(i);
            }
        }
        if (!violatedLineNumbers.isEmpty()) {
            fail(
                    "Every line should start with '"
                            + prefix
                            + "', but some ("
                            + violatedLineNumbers
                            + ") did not. Full dump(): \n"
                            + dump);
        }
        return lines;
    }

    /** Helper to dump an object. */
    public interface Dumper {
        /** Ah, might as well dump (dump!) */
        void dump(PrintWriter printWriter) throws Exception;
    }

    private DumpHelper() {
        throw new UnsupportedOperationException("Provides only static methods");
    }
}
