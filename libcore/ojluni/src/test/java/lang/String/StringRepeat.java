/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @summary This exercises String#repeat patterns and limits.
 * @run main/othervm -Xmx4G StringRepeat
 */
package test.java.lang.String;

// Android-added: support for wrapper to avoid d8 backporting of String.isBlank (b/191859202).
import android.platform.test.annotations.LargeTest;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.testng.Assert;
import org.testng.annotations.Test;


public class StringRepeat {
    /*
     * Varitions of repeat count.
     */
    static int[] REPEATS = {
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16,
        32, 64, 128, 256, 512, 1024, 64 * 1024, 1024 * 1024,
        16 * 1024 * 1024
    };

    /*
     * Varitions of Strings.
     */
    static String[] STRINGS = new String[] {
            "", "\0",  " ", "a", "$", "\u2022",
            "ab", "abc", "abcd", "abcde",
            "The quick brown fox jumps over the lazy dog."
    };

    /*
     * Repeat String function tests.
     */
    @LargeTest
    @Test
    public void test1() {
        for (int repeat : REPEATS) {
            for (String string : STRINGS) {
                long limit = (long)string.length() * (long)repeat;

                // Android-changed: lowered max length limit
                // if ((long)(Integer.MAX_VALUE >> 1) <= limit) {
                if ((long)(Integer.MAX_VALUE >> 5) <= limit) {
                    break;
                }

                // Android-changed: call wrappered repeat() to avoid d8 backport (b/191859202).
                // verify(string.repeat(repeat), string, repeat);
                verify(String_repeat(string, repeat), string, repeat);
            }
        }
    }

    /*
     * Repeat String exception tests.
     */
    @Test
    public void test2() {
        try {
            // Android-changed: call wrappered repeat() to avoid d8 backport (b/191859202).
            // "abc".repeat(-1);
            String_repeat("abc", -1);
            throw new RuntimeException("No exception for negative repeat count");
        } catch (IllegalArgumentException ex) {
            // Correct
        }

        try {
            // Android-changed: call wrappered repeat() to avoid d8 backport (b/191859202).
            // "abc".repeat(Integer.MAX_VALUE - 1);
            String_repeat("abc", Integer.MAX_VALUE - 1);
            throw new RuntimeException("No exception for large repeat count");
        } catch (OutOfMemoryError ex) {
            // Correct
        }
    }

    // Android-added: more tests
    @Test
    public void testEdgeCases() {
        // Android-changed: call wrappered repeat() to avoid d8 backport (b/191859202).
        // Assert.assertThrows(IllegalArgumentException.class, () -> "a".repeat(-1));
        // Assert.assertThrows(IllegalArgumentException.class, () -> "\u03B1".repeat(-1));
        // Assert.assertThrows(OutOfMemoryError.class, () -> "\u03B1\u03B2".repeat(Integer.MAX_VALUE));
        Assert.assertThrows(IllegalArgumentException.class, () -> String_repeat("a", -1));
        Assert.assertThrows(IllegalArgumentException.class, () -> String_repeat("\u03B1", -1));
        Assert.assertThrows(OutOfMemoryError.class,
                            () -> String_repeat("\u03B1\u03B2", Integer.MAX_VALUE));
    }

    @Test
    public void testCompressed() {
        // Android-changed: call wrappered repeat() to avoid d8 backport (b/191859202).
        // Assert.assertEquals("a".repeat(0), "");
        // Assert.assertEquals("a".repeat(1), "a");
        // Assert.assertEquals("a".repeat(5), "aaaaa");
        Assert.assertEquals(String_repeat("a", 0), "");
        Assert.assertEquals(String_repeat("a", 1), "a");
        Assert.assertEquals(String_repeat("a", 5), "aaaaa");

        // Android-changed: call wrappered repeat() to avoid d8 backport (b/191859202).
        // Assert.assertEquals("abc".repeat(0), "");
        // Assert.assertEquals("abc".repeat(1), "abc");
        // Assert.assertEquals("abc".repeat(5), "abcabcabcabcabc");
        Assert.assertEquals(String_repeat("abc", 0), "");
        Assert.assertEquals(String_repeat("abc", 1), "abc");
        Assert.assertEquals(String_repeat("abc", 5), "abcabcabcabcabc");
    }

    @Test
    public void testUncompressed() {
        // Android-changed: call wrappered repeat() to avoid d8 backport (b/191859202).
        // Assert.assertEquals("\u2022".repeat(0), "");
        // Assert.assertEquals("\u2022".repeat(1), "\u2022");
        // Assert.assertEquals("\u2022".repeat(5), "\u2022\u2022\u2022\u2022\u2022");
        Assert.assertEquals(String_repeat("\u2022", 0), "");
        Assert.assertEquals(String_repeat("\u2022", 1), "\u2022");
        Assert.assertEquals(String_repeat("\u2022", 5), "\u2022\u2022\u2022\u2022\u2022");

        // Android-changed: call wrappered repeat() to avoid d8 backport (b/191859202).
        // Assert.assertEquals("\u03B1\u03B2\u03B3".repeat(0), "");
        // Assert.assertEquals("\u03B1\u03B2\u03B3".repeat(1), "αβγ");
        // Assert.assertEquals("\u03B1\u03B2\u03B3".repeat(5), "αβγαβγαβγαβγαβγ");
        Assert.assertEquals(String_repeat("\u03B1\u03B2\u03B3", 0), "");
        Assert.assertEquals(String_repeat("\u03B1\u03B2\u03B3", 1), "αβγ");
        Assert.assertEquals(String_repeat("\u03B1\u03B2\u03B3", 5), "αβγαβγαβγαβγαβγ");
    }

    static String truncate(String string) {
        if (string.length() < 80) {
            return string;
        }
        return string.substring(0, 80) + "...";
    }

    /*
     * Verify string repeat patterns.
     */
    static void verify(String result, String string, int repeat) {
        if (string.isEmpty() || repeat == 0) {
            if (!result.isEmpty()) {
                String message = String.format("\"%s\".repeat(%d)%n", truncate(string), repeat) +
                        String.format("Result \"%s\"%n", truncate(result)) +
                        String.format("Result expected to be empty, found string of length %d%n", result.length());
                Assert.fail(message);
            }
        } else {
            int expected = 0;
            int count = 0;
            for (int offset = result.indexOf(string, expected);
                 0 <= offset;
                 offset = result.indexOf(string, expected)) {
                count++;
                if (offset != expected) {
                    String message = String.format("\"%s\".repeat(%d)%n", truncate(string), repeat) +
                            String.format("Result \"%s\"%n", truncate(result)) +
                            String.format("Repeat expected at %d, found at = %d%n", expected, offset);
                    Assert.fail(message);
                }
                expected += string.length();
            }
            if (count != repeat) {
                String message = String.format("\"%s\".repeat(%d)%n", truncate(string), repeat) +
                        String.format("Result \"%s\"%n", truncate(result)) +
                        String.format("Repeat count expected to be %d, found %d%n", repeat, count);
                Assert.fail(message);
            }
        }
    }

    // Android-added: wrapper to avoid d8 backporting of String.isBlank (b/191859202).
    private static String String_repeat(String input, int count) {
        try {
            MethodType type = MethodType.methodType(String.class, int.class);
            MethodHandle repeat = MethodHandles.lookup().findVirtual(String.class, "repeat", type);
            return (String) repeat.invokeExact(input, count);
        } catch (IllegalArgumentException | OutOfMemoryError expected) {
            throw expected;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
}
