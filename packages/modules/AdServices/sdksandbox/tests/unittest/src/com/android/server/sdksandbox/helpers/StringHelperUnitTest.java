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

import com.google.common.truth.Expect;

import org.junit.Rule;
import org.junit.Test;

public class StringHelperUnitTest {

    @Rule public final Expect mExpect = Expect.create();

    @Test
    public void testWildcardPatternMatch() {
        String pattern1 = "abcd*";
        verifyPatternMatch(pattern1, "abcd", /*matchOnNullInput=*/ false, true);
        verifyPatternMatch(pattern1, "abcdef", /*matchOnNullInput=*/ false, true);
        verifyPatternMatch(pattern1, "abcdabcd", /*matchOnNullInput=*/ false, true);
        verifyPatternMatch(pattern1, "efgh", /*matchOnNullInput=*/ false, false);
        verifyPatternMatch(pattern1, "efgabcd", /*matchOnNullInput=*/ false, false);
        verifyPatternMatch(pattern1, "abc", /*matchOnNullInput=*/ false, false);

        String pattern2 = "*";
        verifyPatternMatch(pattern2, "", /*matchOnNullInput=*/ false, true);
        verifyPatternMatch(pattern2, "abcd", /*matchOnNullInput=*/ false, true);

        String pattern3 = "abcd*efgh*";
        verifyPatternMatch(pattern3, "abcdefgh", /*matchOnNullInput=*/ false, true);
        verifyPatternMatch(pattern3, "abcdrefghij", /*matchOnNullInput=*/ false, true);
        verifyPatternMatch(pattern3, "abcd", /*matchOnNullInput=*/ false, false);
        verifyPatternMatch(pattern3, "abcdteffgh", /*matchOnNullInput=*/ false, false);

        String pattern4 = "*abcd";
        verifyPatternMatch(pattern4, "abcdabcd", /*matchOnNullInput=*/ false, true);
        verifyPatternMatch(pattern4, "abcdabcdabcd", /*matchOnNullInput=*/ false, true);
        verifyPatternMatch(pattern4, "efgabcd", /*matchOnNullInput=*/ false, true);
        verifyPatternMatch(pattern4, "abcd", /*matchOnNullInput=*/ false, true);
        verifyPatternMatch(pattern4, "abcde", /*matchOnNullInput=*/ false, false);

        String pattern5 = "abcd*e";
        verifyPatternMatch(pattern5, "abcde", /*matchOnNullInput=*/ false, true);
        verifyPatternMatch(pattern5, "abcdee", /*matchOnNullInput=*/ false, true);
        verifyPatternMatch(pattern5, "abcdef", /*matchOnNullInput=*/ false, false);

        String pattern6 = "";
        verifyPatternMatch(pattern6, "", /*matchOnNullInput=*/ false, true);
        verifyPatternMatch(pattern6, "ab", /*matchOnNullInput=*/ false, false);

        String pattern7 = "*abcd*";
        verifyPatternMatch(pattern7, "abcdabcdabcd", /*matchOnNullInput=*/ false, true);

        String pattern8 = "a*a";
        verifyPatternMatch(pattern8, "aa", /*matchOnNullInput=*/ false, true);
        verifyPatternMatch(pattern8, "a", /*matchOnNullInput=*/ false, false);

        String pattern9 = "abcd";
        verifyPatternMatch(pattern9, "abcd", /*matchOnNullInput=*/ false, true);
        verifyPatternMatch(pattern9, "a", /*matchOnNullInput=*/ false, false);

        verifyPatternMatch("*aab", "aaaab", /*matchOnNullInput=*/ false, true);
        verifyPatternMatch("a", "ab", /*matchOnNullInput=*/ false, false);

        verifyPatternMatch("*", null, /*matchOnNullInput=*/ false, false);
        verifyPatternMatch("*", null, /*matchOnNullInput=*/ true, true);
    }

    private void verifyPatternMatch(
            String pattern, String input, boolean matchOnNullInput, boolean shouldMatch) {
        mExpect.that(StringHelper.doesInputMatchWildcardPattern(pattern, input, matchOnNullInput))
                .isEqualTo(shouldMatch);
    }
}
