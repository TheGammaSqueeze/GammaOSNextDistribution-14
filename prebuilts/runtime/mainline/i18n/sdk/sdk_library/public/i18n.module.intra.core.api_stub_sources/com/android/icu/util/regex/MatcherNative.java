/*
 * Copyright (C) 2019 The Android Open Source Project
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


package com.android.icu.util.regex;


/**
 * Stores the states when matching an input text with a regular expression pattern
 * {@link com.android.icu.util.regex.PatternNative PatternNative}.
 *
 * @hide
 */

@SuppressWarnings({"unchecked", "deprecation", "all"})
public class MatcherNative {

private MatcherNative() { throw new RuntimeException("Stub!"); }

/**
 * Create a {@link com.android.icu.util.regex.MatcherNative MatcherNative} instance from {@link com.android.icu.util.regex.PatternNative PatternNative}.
 *
 * @hide
 */

public static com.android.icu.util.regex.MatcherNative create(com.android.icu.util.regex.PatternNative pattern) { throw new RuntimeException("Stub!"); }

/**
 * Returns the index of the named group.
 *
 * @param groupName the group name
 *
 * @hide
 */

public int getMatchedGroupIndex(java.lang.String groupName) { throw new RuntimeException("Stub!"); }

/**
 * Attempts to match the input string starting from {@code startIndex}.
 *
 * @param offsets an output argument storing the starting and ending indices of the matched
 *                groups. This has to be pre-allocated with the size of
 *                ({@link #groupCount()} + 1) * 2. The elements at index 0 and 1 are the
 *                starting and ending indices of the 0th group and 0th group is the entire
 *                match. The index can be -1 when a match is found, but the group is not found.
 * @return true if a match is found.
 *
 *
 * @hide
 */

public boolean find(int startIndex, int[] offsets) { throw new RuntimeException("Stub!"); }

/**
 * Attempts to find the next match.
 *
 * @param offsets an output argument storing the starting and ending indices of the matched
 *                groups. This has to be pre-allocated with the size of
 *                ({@link #groupCount()} + 1) * 2. The elements at index 0 and 1 are the
 *                starting and ending indices of the 0th group and 0th group is the entire
 *                match. The index can be -1 when a match is found, but the group is not found.
 * @return true if a match is found.
 *
 *
 * @hide
 */

public boolean findNext(int[] offsets) { throw new RuntimeException("Stub!"); }

/**
 * Returns the number of named-capturing groups provided in the pattern.
 *
 * @hide
 */

public int groupCount() { throw new RuntimeException("Stub!"); }

/**
 * Returns true if the matcher has hit the end of the input string in the last match.
 *
 * @hide
 */

public boolean hitEnd() { throw new RuntimeException("Stub!"); }

/**
 * Attempts to match the prefix of the input string.
 *
 * @param offsets an output argument storing the starting and ending indices of the matched
 *                groups. This has to be pre-allocated with the size of
 *                ({@link #groupCount()} + 1) * 2. The elements at index 0 and 1 are the
 *                starting and ending indices of the 0th group and 0th group is the entire
 *                match. The index can be -1 when a match is found, but the group is not found.
 * @return true if it matches the prefix of the input string.
 *
 * @hide
 */

public boolean lookingAt(int[] offsets) { throw new RuntimeException("Stub!"); }

/**
 * Attempts to match the entire input string.
 *
 *
 * @param offsets an output argument storing the starting and ending indices of the matched
 *                groups. This has to be pre-allocated with the size of
 *                ({@link #groupCount()} + 1) * 2. The elements at index 0 and 1 are the
 *                starting and ending indices of the 0th group and 0th group is the entire
 *                match. The index can be -1 when a match is found, but the group is not found.
 * @return true if it matches the entire input string.
 *
 * @hide
 */

public boolean matches(int[] offsets) { throw new RuntimeException("Stub!"); }

/**
 * Returns true if the most recent match succeeded and additional input could cause it to fail.
 *
 * @hide
 */

public boolean requireEnd() { throw new RuntimeException("Stub!"); }

/**
 * Set the input string.
 *
 * @param start the starting index at which to begin matching
 * @param end the ending index at which to end matching
 *
 * @hide
 */

public void setInput(java.lang.String input, int start, int end) { throw new RuntimeException("Stub!"); }

/**
 * Set whether using the anchoring bounds.
 *
 * Anchoring bounds allow the input string boundary to be matched by constructs ^ and $.
 *
 * @hide
 */

public void useAnchoringBounds(boolean value) { throw new RuntimeException("Stub!"); }

/**
 * Set whether using transparent bounds.
 *
 * Transparent bounds makes the boundary of the input string transparent to the lookahead,
 * lookbehind, and boundary constructs.
 *
 * @hide
 */

public void useTransparentBounds(boolean value) { throw new RuntimeException("Stub!"); }
}

