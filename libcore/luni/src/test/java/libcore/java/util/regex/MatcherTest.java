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

package libcore.java.util.regex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import dalvik.annotation.compat.VersionCodes;
import dalvik.system.VMRuntime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import libcore.junit.util.compat.CoreCompatChangeRule;
import libcore.junit.util.compat.CoreCompatChangeRule.EnableCompatChanges;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class MatcherTest {

    @Rule
    public final TestRule rule = new CoreCompatChangeRule();

    @Test
    public void testResults_notMatched() {
        Matcher m = Pattern.compile("a").matcher("b");
        assertEquals(0, m.results().count());
    }

    @Test
    public void testAppendEvaluated() {
        Matcher m = Pattern.compile("a(?<g>b)c").matcher("abc");
        assertTrue(m.find());
        String replacement = "123$g";
        assertAppendEvaluated(m, replacement, "123g");

        m = Pattern.compile("(a)(b)(c)(d)(e)(f)(g)(h)(i)(j)(k)").matcher("abcdefghijk");
        assertTrue(m.find());
        assertAppendEvaluated(m, "$11", "a1");
    }

    private void assertAppendEvaluated(Matcher m, String replacement, String expected) {
        StringBuilder sb = new StringBuilder();
        m.appendEvaluated(sb, replacement);
        assertEquals(expected, sb.toString());
    }


    @Test
    public void testAppendExpandedReplacement() {
        Matcher m = Pattern.compile("a(?<g>b)c").matcher("abc");
        assertTrue(m.find());
        try {
            m.appendExpandedReplacement("123$g", new StringBuilder());
            fail();
        } catch (IllegalArgumentException e) {} // expected


        m = Pattern.compile("(a)(b)(c)(d)(e)(f)(g)(h)(i)(j)(k)").matcher("abcdefghijk");
        assertTrue(m.find());
        assertAppendExpandedReplacement(m, "$11", "k");
    }

    private void assertAppendExpandedReplacement(Matcher m, String replacement, String expected) {
        StringBuilder sb = new StringBuilder();
        m.appendExpandedReplacement(replacement, sb);
        assertEquals(expected, sb.toString());
    }

    @Test
    @EnableCompatChanges({Matcher.DISALLOW_INVALID_GROUP_REFERENCE})
    public void testAppendReplacement() {
        Assume.assumeTrue(VMRuntime.getSdkVersion() >= VersionCodes.UPSIDE_DOWN_CAKE);
        Matcher m = Pattern.compile("a(?<g>b)c").matcher("abc");
        assertTrue(m.find());
        try {
            m.appendReplacement(new StringBuilder(), "123$g");
            fail();
        } catch (IllegalArgumentException e) {} // expected


        m = Pattern.compile("(a)(b)(c)(d)(e)(f)(g)(h)(i)(j)(k)").matcher("abcdefghijk");
        assertTrue(m.find());
        assertAppendReplacement(m, "$11", "k");
    }

    private void assertAppendReplacement(Matcher m, String replacement, String expected) {
        StringBuilder sb = new StringBuilder();
        m.appendReplacement(sb, replacement);
        assertEquals(expected, sb.toString());
    }
}
