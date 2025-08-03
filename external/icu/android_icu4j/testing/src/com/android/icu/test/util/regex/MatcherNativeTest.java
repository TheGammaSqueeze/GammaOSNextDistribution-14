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

package com.android.icu.test.util.regex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.icu.testsharding.MainTestShard;

import com.android.icu.util.regex.MatcherNative;
import com.android.icu.util.regex.PatternNative;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@MainTestShard
@RunWith(JUnit4.class)
public class MatcherNativeTest {
    String[] testPatterns = {
            "(a|b)*abb",
            "(1*2*3*4*)*567",
            "(a|b|c|d)*aab",
            "(1|2|3|4|5|6|7|8|9|0)(1|2|3|4|5|6|7|8|9|0)*",
            "(abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ)*",
            "(a|b)*(a|b)*A(a|b)*lice.*",
            "(a|b|c|d|e|f|g|h|i|j|k|l|m|n|o|p|q|r|s|t|u|v|w|x|y|z)(a|b|c|d|e|f|g|h|"
                    + "i|j|k|l|m|n|o|p|q|r|s|t|u|v|w|x|y|z)*(1|2|3|4|5|6|7|8|9|0)*|while|for|struct|if|do" };

    String[] groupPatterns = { "(a|b)*aabb", "((a)|b)*aabb", "((a|b)*)a(abb)",
            "(((a)|(b))*)aabb", "(((a)|(b))*)aa(b)b", "(((a)|(b))*)a(a(b)b)" };

    private static class Pattern {
        private final PatternNative patternNative;
        private final String pattern;

        private static Pattern compile(String pattern) {
            return new Pattern(pattern, 0);
        }

        private Pattern(String p, int flags) {
            this.pattern = p;
            this.patternNative = PatternNative.create(p, flags);
        }

        public static boolean matches(String pattern, String input) {
            Pattern p = compile(pattern);
            Matcher m = p.matcher(input);
            return m.find();
        }

        public static Pattern compile(String pattern, int flags) {
            return new Pattern(pattern, flags);
        }

        private Matcher matcher(String input) {
            Matcher matcher = new Matcher(this, input);
            return matcher;
        }

        public Matcher matcher(StringBuilder sb) {
            return matcher(sb.toString());
        }

        public String pattern() {
            return pattern;
        }
    }

    private static class Matcher {

        private final MatcherNative matcherNative;
        private final PatternNative patternNative;
        private final Pattern pattern;
        private int[] groups;
        private String input;
        private int inputStart, inputEnd;

        private Matcher(Pattern pattern, String input) {
            this.pattern = pattern;
            this.patternNative = pattern.patternNative;
            this.matcherNative = MatcherNative.create(patternNative);
            reset(input);
        }

        public boolean matches() {
            return matcherNative.matches(groups);
        }

        public void region(int start, int end) {
            reset(input, start, end);
        }

        public int regionStart() {
            return inputStart;
        }

        public int regionEnd() {
            return inputEnd;
        }

        public boolean find() {
            return matcherNative.findNext(groups);
        }

        public void reset(String input, int start, int end) {
            this.input = input;
            inputStart = start;
            inputEnd = end;
            matcherNative.setInput(input, inputStart, inputEnd);
            this.groups = new int[2 * (matcherNative.groupCount() + 1)];
        }

        public void reset(String input) {
            reset(input, 0, input.length());
        }

        public Pattern pattern() {
            return pattern;
        }

        public String group(int group) {
            return input.substring(start(group), end(group));
        }

        public int groupCount() {
            return matcherNative.groupCount();
        }

        public int start(int group) {
            return groups[group * 2];
        }

        public int start(String groupName) {
            return start(groupId(groupName));
        }

        public int end(int group) {
            return groups[group * 2 + 1];
        }

        public int end(String groupName) {
            return end(groupId(groupName));
        }

        public boolean hitEnd() {
            return matcherNative.hitEnd();
        }

        public boolean requireEnd() {
            return matcherNative.requireEnd();
        }

        public boolean lookingAt() {
            return matcherNative.lookingAt(groups);
        }

        public String group() {
            return group(0);
        }

        public int start() {
            return start(0);
        }

        public boolean find(int start) {
            reset();
            return matcherNative.find(start, groups);
        }

        public void reset() {
            reset(input);
        }

        public String group(String groupName) {
            return group(groupId(groupName));
        }

        public int groupId(String groupName) {
            return matcherNative.getMatchedGroupIndex(groupName);
        }

        public Matcher useAnchoringBounds(boolean useAnchoringBounds) {
            matcherNative.useAnchoringBounds(useAnchoringBounds);
            return this;
        }

        public Matcher useTransparentBounds(boolean useTransparentBounds) {
            matcherNative.useTransparentBounds(useTransparentBounds);
            return this;
        }
    }

    @Test
    public void testRegionsIntInt() {
        Pattern p = Pattern.compile("x*");
        Matcher m = p.matcher("axxxxxa");
        assertFalse(m.matches());

        m.region(1, 6);
        assertEquals(1, m.regionStart());
        assertEquals(6, m.regionEnd());
        assertTrue(m.matches());
    }

    @Test
    public void testLookingAt() {
        Pattern p = Pattern.compile("abc");
        Matcher m = p.matcher("aabc");
        assertTrue(m.find());

        m.reset();
        assertFalse(m.lookingAt());
    }

    @Test
    public void testRequireEnd() {
        Pattern p = Pattern.compile("abc$");
        Matcher m = p.matcher("abc");
        assertTrue(m.find());
        assertTrue(m.requireEnd());
    }

    @Test
    public void testResetCharSequence() {
        Pattern p = Pattern.compile("abcd");
        Matcher m = p.matcher("abcd");
        assertTrue(m.matches());
        m.reset("efgh");
        assertFalse(m.matches());

        try {
            m.reset(null);
            fail("expected a NPE");
        } catch (NullPointerException e) {
        }
    }

    @Test
    public void testPattern() {
        for (String element : testPatterns) {
            Pattern test = Pattern.compile(element);
            assertEquals(test, test.matcher("aaa").pattern());
        }
    }

    @Test
    public void testGroupint() {
        String positiveTestString = "ababababbaaabb";

        // test IndexOutOfBoundsException
        // //
        for (int i = 0; i < groupPatterns.length; i++) {
            Pattern test = Pattern.compile(groupPatterns[i]);
            Matcher mat = test.matcher(positiveTestString);
            mat.matches();
            try {
                // groupPattern <index + 1> equals to number of groups
                // of the specified pattern
                // //
                mat.group(i + 2);
                fail("IndexOutBoundsException expected");
                mat.group(i + 100);
                fail("IndexOutBoundsException expected");
                mat.group(-1);
                fail("IndexOutBoundsException expected");
                mat.group(-100);
                fail("IndexOutBoundsException expected");
            } catch (IndexOutOfBoundsException iobe) {
            }
        }

        String[][] groupResults = { { "a" }, { "a", "a" },
                { "ababababba", "a", "abb" }, { "ababababba", "a", "a", "b" },
                { "ababababba", "a", "a", "b", "b" },
                { "ababababba", "a", "a", "b", "abb", "b" }, };

        for (int i = 0; i < groupPatterns.length; i++) {
            Pattern test = Pattern.compile(groupPatterns[i]);
            Matcher mat = test.matcher(positiveTestString);
            mat.matches();
            for (int j = 0; j < groupResults[i].length; j++) {
                assertEquals("i: " + i + " j: " + j, groupResults[i][j], mat
                        .group(j + 1));
            }

        }

    }

    @Test
    public void testGroup() {
        String positiveTestString = "ababababbaaabb";
        String negativeTestString = "gjhfgdsjfhgcbv";
        for (String element : groupPatterns) {
            Pattern test = Pattern.compile(element);
            Matcher mat = test.matcher(positiveTestString);
            mat.matches();
            // test result
            assertEquals(positiveTestString, mat.group());

            // test equal to group(0) result
            assertEquals(mat.group(0), mat.group());
        }
    }

    @Test
    public void testGroupPossessive() {
        Pattern pat = Pattern.compile("((a)|(b))++c");
        Matcher mat = pat.matcher("aac");

        mat.matches();
        assertEquals("a", mat.group(1));
    }

    @Test
    public void testMatchesMisc() {
        String[][] posSeq = {
                { "abb", "ababb", "abababbababb", "abababbababbabababbbbbabb" },
                { "213567", "12324567", "1234567", "213213567",
                        "21312312312567", "444444567" },
                { "abcdaab", "aab", "abaab", "cdaab", "acbdadcbaab" },
                { "213234567", "3458", "0987654", "7689546432", "0398576",
                        "98432", "5" },
                {
                        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ",
                        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
                                + "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ" },
                { "ababbaAabababblice", "ababbaAliceababab", "ababbAabliceaaa",
                        "abbbAbbbliceaaa", "Alice" },
                { "a123", "bnxnvgds156", "for", "while", "if", "struct" }

        };

        for (int i = 0; i < testPatterns.length; i++) {
            Pattern pat = Pattern.compile(testPatterns[i]);
            for (int j = 0; j < posSeq[i].length; j++) {
                Matcher mat = pat.matcher(posSeq[i][j]);
                assertTrue("Incorrect match: " + testPatterns[i] + " vs "
                        + posSeq[i][j], mat.matches());
            }
        }
    }

    @Test
    public void testMatchesQuantifiers() {
        String[] testPatternsSingles = { "a{5}", "a{2,4}", "a{3,}" };
        String[] testPatternsMultiple = { "((a)|(b)){1,2}abb",
                "((a)|(b)){2,4}", "((a)|(b)){3,}" };

        String[][] stringSingles = { { "aaaaa", "aaa" },
                { "aa", "a", "aaa", "aaaaaa", "aaaa", "aaaaa" },
                { "aaa", "a", "aaaa", "aa" }, };

        String[][] stringMultiples = { { "ababb", "aba" },
                { "ab", "b", "bab", "ababa", "abba", "abababbb" },
                { "aba", "b", "abaa", "ba" }, };

        for (int i = 0; i < testPatternsSingles.length; i++) {
            Pattern pat = Pattern.compile(testPatternsSingles[i]);
            for (int j = 0; j < stringSingles.length / 2; j++) {
                assertTrue("Match expected, but failed: " + pat.pattern()
                        + " : " + stringSingles[i][j], pat.matcher(
                        stringSingles[i][j * 2]).matches());
                assertFalse("Match failure expected, but match succeed: "
                                + pat.pattern() + " : " + stringSingles[i][j * 2 + 1],
                        pat.matcher(stringSingles[i][j * 2 + 1]).matches());
            }
        }

        for (int i = 0; i < testPatternsMultiple.length; i++) {
            Pattern pat = Pattern.compile(testPatternsMultiple[i]);
            for (int j = 0; j < stringMultiples.length / 2; j++) {
                assertTrue("Match expected, but failed: " + pat.pattern()
                        + " : " + stringMultiples[i][j], pat.matcher(
                        stringMultiples[i][j * 2]).matches());
                assertFalse(
                        "Match failure expected, but match succeed: "
                                + pat.pattern() + " : "
                                + stringMultiples[i][j * 2 + 1], pat.matcher(
                                stringMultiples[i][j * 2 + 1]).matches());
            }
        }
    }

    @Test
    public void testQuantVsGroup() {
        String patternString = "(d{1,3})((a|c)*)(d{1,3})((a|c)*)(d{1,3})";
        String testString = "dacaacaacaaddaaacaacaaddd";

        Pattern pat = Pattern.compile(patternString);
        Matcher mat = pat.matcher(testString);

        mat.matches();
        assertEquals("dacaacaacaaddaaacaacaaddd", mat.group());
        assertEquals("d", mat.group(1));
        assertEquals("acaacaacaa", mat.group(2));
        assertEquals("dd", mat.group(4));
        assertEquals("aaacaacaa", mat.group(5));
        assertEquals("ddd", mat.group(7));
    }


    @Test
    public void testFind() {
        String testPattern = "(abb)";
        String testString = "cccabbabbabbabbabb";
        Pattern pat = Pattern.compile(testPattern);
        Matcher mat = pat.matcher(testString);
        int start = 3;
        int end = 6;
        while (mat.find()) {
            assertEquals(start, mat.start(1));
            assertEquals(end, mat.end(1));

            start = end;
            end += 3;
        }

        testPattern = "(\\d{1,3})";
        testString = "aaaa123456789045";

        Pattern pat2 = Pattern.compile(testPattern);
        Matcher mat2 = pat2.matcher(testString);
        start = 4;
        int length = 3;
        while (mat2.find()) {
            assertEquals(testString.substring(start, start + length), mat2
                    .group(1));
            start += length;
        }
    }

    @Test
    public void testSEOLsymbols() {
        Pattern pat = Pattern.compile("^a\\(bb\\[$");
        Matcher mat = pat.matcher("a(bb[");

        assertTrue(mat.matches());
    }

    @Test
    public void testGroupCount() {
        for (int i = 0; i < groupPatterns.length; i++) {
            Pattern test = Pattern.compile(groupPatterns[i]);
            Matcher mat = test.matcher("ababababbaaabb");
            mat.matches();
            assertEquals(i + 1, mat.groupCount());

        }
    }

    @Test
    public void testRelactantQuantifiers() {
        Pattern pat = Pattern.compile("(ab*)*b");
        Matcher mat = pat.matcher("abbbb");

        if (mat.matches()) {
            assertEquals("abbb", mat.group(1));
        } else {
            fail("Match expected: (ab*)*b vs abbbb");
        }
    }

    @Test
    public void testStart_groupIndexOutOfBounds() {
        Matcher matcher = Pattern.compile("(Hello)").matcher("Hello, world!");
        assertTrue(matcher.find());
        try {
            matcher.start(-1 /* out of bounds */);
            fail();
        } catch (ArrayIndexOutOfBoundsException expected) {
        }

        try {
            matcher.start(2 /* out of bounds */);
            fail();
        } catch (ArrayIndexOutOfBoundsException expected) {
        }
    }

    @Test
    public void testEnhancedFind() {
        String input = "foob";
        String pattern = "a*b";
        Pattern pat = Pattern.compile(pattern);
        Matcher mat = pat.matcher(input);

        mat.find();
        assertEquals("b", mat.group());
    }

    @Test
    public void testPosCompositeGroup() {
        String[] posExamples = { "aabbcc", "aacc", "bbaabbcc" };
        String[] negExamples = { "aabb", "bb", "bbaabb" };
        Pattern posPat = Pattern.compile("(aa|bb){1,3}+cc");
        Pattern negPat = Pattern.compile("(aa|bb){1,3}+bb");

        Matcher mat;
        for (String element : posExamples) {
            mat = posPat.matcher(element);
            assertTrue(mat.matches());
        }

        for (String element : negExamples) {
            mat = negPat.matcher(element);
            assertFalse(mat.matches());
        }

        assertTrue(Pattern.matches("(aa|bb){1,3}+bb", "aabbaabb"));

    }

    @Test
    public void testPosAltGroup() {
        String[] posExamples = { "aacc", "bbcc", "cc" };
        String[] negExamples = { "bb", "aa" };
        Pattern posPat = Pattern.compile("(aa|bb)?+cc");
        Pattern negPat = Pattern.compile("(aa|bb)?+bb");

        Matcher mat;
        for (String element : posExamples) {
            mat = posPat.matcher(element);
            assertTrue(posPat.toString() + " vs: " + element, mat.matches());
        }

        for (String element : negExamples) {
            mat = negPat.matcher(element);
            assertFalse(mat.matches());
        }

        assertTrue(Pattern.matches("(aa|bb)?+bb", "aabb"));
    }

    @Test
    public void testRelCompGroup() {

        Matcher mat;
        Pattern pat;
        String res = "";
        for (int i = 0; i < 4; i++) {
            pat = Pattern.compile("((aa|bb){" + i + ",3}?).*cc");
            mat = pat.matcher("aaaaaacc");
            assertTrue(pat.toString() + " vs: " + "aaaaaacc", mat.matches());
            assertEquals(res, mat.group(1));
            res += "aa";
        }
    }

    @Test
    public void testRelAltGroup() {

        Matcher mat;
        Pattern pat;

        pat = Pattern.compile("((aa|bb)??).*cc");
        mat = pat.matcher("aacc");
        assertTrue(pat.toString() + " vs: " + "aacc", mat.matches());
        assertEquals("", mat.group(1));

        pat = Pattern.compile("((aa|bb)??)cc");
        mat = pat.matcher("aacc");
        assertTrue(pat.toString() + " vs: " + "aacc", mat.matches());
        assertEquals("aa", mat.group(1));
    }

    @Test
    public void testIgnoreCase() {
        Pattern pat = Pattern.compile("(aa|bb)*", java.util.regex.Pattern.CASE_INSENSITIVE);
        Matcher mat = pat.matcher("aAbb");

        assertTrue(mat.matches());

        pat = Pattern.compile("(a|b|c|d|e)*", java.util.regex.Pattern.CASE_INSENSITIVE);
        mat = pat.matcher("aAebbAEaEdebbedEccEdebbedEaedaebEbdCCdbBDcdcdADa");
        assertTrue(mat.matches());

        pat = Pattern.compile("[a-e]*", java.util.regex.Pattern.CASE_INSENSITIVE);
        mat = pat.matcher("aAebbAEaEdebbedEccEdebbedEaedaebEbdCCdbBDcdcdADa");
        assertTrue(mat.matches());
    }

    @Test
    public void testOverFlow() {
        Pattern tp = Pattern.compile("(a*)*");
        Matcher tm = tp.matcher("aaa");
        assertTrue(tm.matches());
        assertEquals("", tm.group(1));

        assertTrue(Pattern.matches("(1+)\\1+", "11"));
        assertTrue(Pattern.matches("(1+)(2*)\\2+", "11"));

        Pattern pat = Pattern.compile("(1+)\\1*");
        Matcher mat = pat.matcher("11");

        assertTrue(mat.matches());
        assertEquals("11", mat.group(1));

        pat = Pattern.compile("((1+)|(2+))(\\2+)");
        mat = pat.matcher("11");

        assertTrue(mat.matches());
        assertEquals("1", mat.group(2));
        assertEquals("1", mat.group(1));
        assertEquals("1", mat.group(4));
    }

    @Test
    public void testUnicode() {

        assertTrue(Pattern.matches("\\x61a", "aa"));
        assertTrue(Pattern.matches("\\u0061a", "aa"));
        assertTrue(Pattern.matches("\\0141a", "aa"));
        assertTrue(Pattern.matches("\\0777", "?7"));

    }

    @Test
    public void testUnicodeCategory() {
        assertTrue(Pattern.matches("\\p{Ll}", "k")); // Unicode lower case
        assertTrue(Pattern.matches("\\P{Ll}", "K")); // Unicode non-lower
        // case
        assertTrue(Pattern.matches("\\p{Lu}", "K")); // Unicode upper case
        assertTrue(Pattern.matches("\\P{Lu}", "k")); // Unicode non-upper
        // case
        // combinations
        assertTrue(Pattern.matches("[\\p{L}&&[^\\p{Lu}]]", "k"));
        assertTrue(Pattern.matches("[\\p{L}&&[^\\p{Ll}]]", "K"));
        assertFalse(Pattern.matches("[\\p{L}&&[^\\p{Lu}]]", "K"));
        assertFalse(Pattern.matches("[\\p{L}&&[^\\p{Ll}]]", "k"));

        // category/character combinations
        assertFalse(Pattern.matches("[\\p{L}&&[^a-z]]", "k"));
        assertTrue(Pattern.matches("[\\p{L}&&[^a-z]]", "K"));

        assertTrue(Pattern.matches("[\\p{Lu}a-z]", "k"));
        assertTrue(Pattern.matches("[a-z\\p{Lu}]", "k"));

        assertFalse(Pattern.matches("[\\p{Lu}a-d]", "k"));
        assertTrue(Pattern.matches("[a-d\\p{Lu}]", "K"));

        // assertTrue(Pattern.matches("[\\p{L}&&[^\\p{Lu}&&[^K]]]", "K"));
        assertFalse(Pattern.matches("[\\p{L}&&[^\\p{Lu}&&[^G]]]", "K"));

    }

    @Test
    public void testFindDollar() {
        Matcher mat = Pattern.compile("a$").matcher("a\n");
        assertTrue(mat.find());
        assertEquals("a", mat.group());
    }

    @Test
    public void testMatchesRegionChanged() {
        // Regression for HARMONY-610
        // Verify if the Matcher can match the input when region is changed
        String input = " word ";
        Pattern pattern = Pattern.compile("\\w+");
        Matcher matcher = pattern.matcher(input);
        matcher.region(1, 5);
        assertTrue(matcher.matches());
    }

    @Test
    public void testFindRegionChanged() {
        // Regression for HARMONY-625
        // Verify if the Matcher behaves correct when region is changed.
        Pattern pattern = Pattern.compile("(?s).*");
        Matcher matcher = pattern.matcher("abcde");
        matcher.find();
        assertEquals("abcde", matcher.group());

        matcher = pattern.matcher("abcde");
        matcher.region(0, 2);
        matcher.find();
        assertEquals("ab", matcher.group());

    }

    @Test
    public void testFindRegionChanged2() {
        // Regression for HARMONY-713
        // Verify if the Matcher behaves correct with pattern "c" when region is changed.
        Pattern pattern = Pattern.compile("c");

        String inputStr = "aabb.c";
        Matcher matcher = pattern.matcher(inputStr);
        matcher.region(0, 3);

        assertFalse(matcher.find());
    }

    @Test
    public void testPatternMatcher() throws Exception {
        // Regression test for HARMONY-674
        Pattern pattern = Pattern.compile("(?:\\d+)(?:pt)");
        assertTrue(pattern.matcher("14pt").matches());
    }

    @Test
    public void test3360() {
        // Inspired by HARMONY-3360
        String str = "!\"#%&'(),-./";
        Pattern p = Pattern.compile("\\s");
        Matcher m = p.matcher(str);

        assertFalse(m.find());
    }

    @Test
    public void testGeneralPunctuationCategory() {
        // Regression test for HARMONY-3360
        String[] s = { ",", "!", "\"", "#", "%", "&", "'", "(", ")", "-", ".", "/" };
        String regexp = "\\p{P}";

        for (int i = 0; i < s.length; i++) {
            Pattern pattern = Pattern.compile(regexp);
            Matcher matcher = pattern.matcher(s[i]);
            assertTrue(matcher.find());
        }
    }

    @Test
    public void testHitEndAfterFind() {
        // Regression test for HARMONY-4396
        hitEndTest(true, "#01.0", "r((ege)|(geg))x", "regexx", false);
        hitEndTest(true, "#01.1", "r((ege)|(geg))x", "regex", false);
        hitEndTest(true, "#01.2", "r((ege)|(geg))x", "rege", true);
        hitEndTest(true, "#01.2", "r((ege)|(geg))x", "xregexx", false);

        hitEndTest(true, "#02.0", "regex", "rexreger", true);
        hitEndTest(true, "#02.1", "regex", "raxregexr", false);

        String floatRegex = getHexFloatRegex();
        hitEndTest(true, "#03.0", floatRegex, Double.toHexString(-1.234d), true);
        hitEndTest(true, "#03.1", floatRegex, "1 ABC"
                + Double.toHexString(Double.NaN) + "buhuhu", false);
        hitEndTest(true, "#03.2", floatRegex, Double.toHexString(-0.0) + "--",
                false);
        hitEndTest(true, "#03.3", floatRegex, "--"
                + Double.toHexString(Double.MIN_VALUE) + "--", false);

        hitEndTest(true, "#04.0", "(\\d+) fish (\\d+) fish (\\w+) fish (\\d+)",
                "1 fish 2 fish red fish 5", true);
        hitEndTest(true, "#04.1", "(\\d+) fish (\\d+) fish (\\w+) fish (\\d+)",
                "----1 fish 2 fish red fish 5----", false);
    }

    private void hitEndTest(boolean callFind, String testNo, String regex,
            String input, boolean hit) {
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(input);
        if (callFind) {
            matcher.find();
        } else {
            matcher.matches();
        }
        boolean h = matcher.hitEnd();

        assertTrue(testNo, h == hit);
    }

    private String getHexFloatRegex() {
        String hexDecimal = "(-|\\+)?0[xX][0-9a-fA-F]*\\.[0-9a-fA-F]+([pP](-|\\+)?[0-9]+)?";
        String notANumber = "((-|\\+)?Infinity)|([nN]a[nN])";
        return new StringBuilder("((").append(hexDecimal).append(")|(").append(
                notANumber).append("))").toString();
    }

    @Test
    public void testNamedGroupCapture() {
        Matcher m = Pattern.compile("(?<first>[a-f]*)(?<second>[h-k]*)")
                .matcher("abcdefhkhk");

        assertTrue(m.matches());
        assertEquals("abcdef", m.group("first"));
        assertEquals(0, m.start("first"));
        assertEquals(6, m.end("first"));

        assertEquals("hkhk", m.group("second"));
        assertEquals(6, m.start("second"));
        assertEquals(10, m.end("second"));
    }

    @Test
    public void test_anchoringBounds() {
        String testPattern = "^ro$";
        String testString = "android";
        Pattern pat = Pattern.compile(testPattern);
        Matcher mat = pat.matcher(testString);

        mat.region(2, 5);
        mat.useAnchoringBounds(false);
        assertFalse("Shouldn't find pattern with non-anchoring bounds", mat.find(0));

        mat.region(2, 5);
        mat.useAnchoringBounds(true);
        assertFalse("Should find pattern with anchoring bounds", mat.find(0));
    }

    @Test
    public void test_transparentBounds() {
        String testPattern = "and(?=roid)";
        String testString = "android";
        Pattern pat = Pattern.compile(testPattern);
        Matcher mat = pat.matcher(testString);

        mat.region(0, 3);
        mat.useTransparentBounds(false);
        assertFalse("Shouldn't find pattern with opaque bounds", mat.matches());

        mat.useTransparentBounds(true);
        assertTrue("Should find pattern transparent bounds", mat.matches()); // ***

        testPattern = "and(?!roid)";
        testString = "android";
        pat = Pattern.compile(testPattern);
        mat = pat.matcher(testString);

        mat.region(0, 3);
        mat.useTransparentBounds(false);
        assertTrue("Should find pattern with opaque bounds", mat.matches());

        mat.useTransparentBounds(true);
        assertFalse("Shouldn't find pattern transparent bounds", mat.matches()); // ***
    }

}
