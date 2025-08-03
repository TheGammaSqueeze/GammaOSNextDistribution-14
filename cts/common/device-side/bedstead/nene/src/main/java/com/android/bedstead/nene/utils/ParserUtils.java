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

package com.android.bedstead.nene.utils;

import com.android.bedstead.nene.exceptions.AdbParseException;

import java.util.HashSet;
import java.util.Set;

/**
 * Utilities for parsing adb output.
 */
public final class ParserUtils {
    private ParserUtils() {

    }

    /**
     * When passed a list of sections, which are organised using significant whitespace, split
     * them into a separate string per section.
     *
     * <p>For example:
     * {@code
     * section1
     *     a
     *         alpha
     *         beta
     *     b
     *     c
     * section2
     *     a2
     *     b2
     * }
     *
     * <p>Using {@link #extractIndentedSections(String, int)} with this string, and a
     * {@code baseIndentation} of 0 (because there are no spaces before the "section" headings)
     * would return two strings, one from "section1" to "c" and one from "section2" to "b2".
     */
    public static Set<String> extractIndentedSections(String list, int baseIndentation)
            throws AdbParseException {
        try {
            Set<String> sections = new HashSet<>();
            String[] lines = list.split("\n");

            boolean skippingStart = true; // Skip empty lines at the start
            StringBuilder sectionBuilder = null;
            for (String line : lines) {
                if (skippingStart && line.isEmpty()) {
                    continue;
                }
                skippingStart = false;
                int indentation = countIndentation(line);
                if (indentation == baseIndentation) {
                    // New item
                    if (sectionBuilder != null) {
                        sections.add(sectionBuilder.toString().trim());
                    }
                    sectionBuilder = new StringBuilder(line).append("\n");
                } else {
                    sectionBuilder.append(line).append("\n");
                }
            }
            sections.add(sectionBuilder.toString().trim());
            return sections;
        } catch (NullPointerException e) {
            throw new AdbParseException(
                    "Error extracting indented sections with baseIndentation: " + baseIndentation,
                    list, e);
        }
    }

    private static int countIndentation(String s) {
        String trimmed = s.trim();
        if (trimmed.isEmpty()) {
            return s.length();
        }
        return s.indexOf(trimmed);
    }
}
