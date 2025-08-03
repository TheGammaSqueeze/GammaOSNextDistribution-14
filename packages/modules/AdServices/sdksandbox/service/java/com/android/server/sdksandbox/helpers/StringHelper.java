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

package com.android.server.sdksandbox.helpers;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.util.ArraySet;

/**
 * Helper class for String operations.
 *
 * @hide
 */
public class StringHelper {
    /**
     * Checks if a given input string matches any of the given patterns. Each pattern can contain
     * wildcards in the form of an asterisk. This wildcard should match 0 or more number of
     * characters in the input string.
     */
    public static boolean doesInputMatchAnyWildcardPattern(
            @NonNull ArraySet<String> patterns, @Nullable String input) {
        for (int i = 0; i < patterns.size(); ++i) {
            if (doesInputMatchWildcardPattern(
                    patterns.valueAt(i), input, /*matchOnNullInput=*/ false)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if a given input string matches the given pattern. The pattern can contain wildcards
     * in the form of an asterisk. This wildcard should match 0 or more number of characters in the
     * input string.
     */
    public static boolean doesInputMatchWildcardPattern(
            @Nullable String pattern, @Nullable String input, boolean matchOnNullInput) {
        if (matchOnNullInput && (pattern != null && pattern.equals("*"))) {
            return true;
        }
        if (pattern == null || input == null) {
            return false;
        }

        /*
         * We split the pattern by the wildcard. It is split with a non-negative limit, indicating
         * that the pattern is applied as many times as possible e.g. if pattern = "*a*", the split
         * would be ["","a",""].
         */
        // TODO(b/289197372): Optimize by splitting beforehand.
        String[] patternSubstrings = pattern.split("\\*", -1);
        int inputMatchStartIndex = 0;
        for (int i = 0; i < patternSubstrings.length; ++i) {
            if (i == 0) {
                // Verify that the input string starts with the characters present before the first
                // wildcard.
                if (!input.startsWith(patternSubstrings[i])) {
                    return false;
                }
                inputMatchStartIndex = patternSubstrings[i].length();
            } else if (i == patternSubstrings.length - 1) {
                // Verify that the input string (after the point where it's been matched so far)
                // matches with the characters after the last wildcard.
                if (!input.substring(inputMatchStartIndex).endsWith(patternSubstrings[i])) {
                    return false;
                }
                inputMatchStartIndex = input.length();
            } else {
                // For patterns between the first and last wildcard, greedily check if the input
                // (after the point where it's been matched so far) matches properly.
                int substringIndex = input.indexOf(patternSubstrings[i], inputMatchStartIndex);
                if (substringIndex == -1) {
                    return false;
                }
                inputMatchStartIndex = substringIndex + patternSubstrings[i].length();
            }
        }

        // Verify that the whole input has been matched.
        return inputMatchStartIndex >= input.length();
    }
}
