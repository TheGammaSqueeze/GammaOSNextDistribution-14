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
package test.java.lang.String;

// Android-added: support for wrapper to avoid d8 backporting of String.isBlank (b/191859202).
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.testng.annotations.Test;

/**
 * @test
 * @summary Basic strip, stripLeading, stripTrailing functionality
 * @bug 8200377
 * @run main/othervm Strip
 */

public class Strip {

    /*
     * Test basic stripping routines
     */
    @Test
    public void testStrip() {
        // Android-changed: call wrappered string methods to avoid d8 backport (b/191859202).
        // equal("   abc   ".strip(), "abc");
        // equal("   abc   ".stripLeading(), "abc   ");
        // equal("   abc   ".stripTrailing(), "   abc");
        // equal("   abc\u2022   ".strip(), "abc\u2022");
        // equal("   abc\u2022   ".stripLeading(), "abc\u2022   ");
        // equal("   abc\u2022   ".stripTrailing(), "   abc\u2022");
        // equal("".strip(), "");
        // equal("".stripLeading(), "");
        // equal("".stripTrailing(), "");
        // equal("\b".strip(), "\b");
        // equal("\b".stripLeading(), "\b");
        // equal("\b".stripTrailing(), "\b");
        equal(String_strip("   abc   "), "abc");
        equal(String_stripLeading("   abc   "), "abc   ");
        equal(String_stripTrailing("   abc   "), "   abc");
        equal(String_strip("   abc\u2022   "), "abc\u2022");
        equal(String_stripLeading("   abc\u2022   "), "abc\u2022   ");
        equal(String_stripTrailing("   abc\u2022   "), "   abc\u2022");
        equal(String_strip(""), "");
        equal(String_stripLeading(""), "");
        equal(String_stripTrailing(""), "");
        equal(String_strip("\b"), "\b");
        equal(String_stripLeading("\b"), "\b");
        equal(String_stripTrailing("\b"), "\b");
    }

    /*
     * Test full whitespace range
     */
    @Test
    public void testWhitespace() {
        StringBuilder sb = new StringBuilder(64);
        IntStream.range(1, 0xFFFF).filter(c -> Character.isWhitespace(c))
                .forEach(c -> sb.append((char)c));
        String whiteSpace = sb.toString();

        String testString = whiteSpace + "abc" + whiteSpace;
        // Android-changed: call wrappered string methods to avoid d8 backport (b/191859202).
        // equal(testString.strip(), "abc");
        // equal(testString.stripLeading(), "abc"  + whiteSpace);
        // equal(testString.stripTrailing(), whiteSpace + "abc");
        equal(String_strip(testString), "abc");
        equal(String_stripLeading(testString), "abc"  + whiteSpace);
        equal(String_stripTrailing(testString), whiteSpace + "abc");
    }

    /*
     * Report difference in result.
     */
    static void report(String message, String inputTag, String input,
                       String outputTag, String output) {
        System.err.println(message);
        System.err.println();
        System.err.println(inputTag);
        System.err.println(input.codePoints()
                .mapToObj(c -> (Integer)c)
                .collect(Collectors.toList()));
        System.err.println();
        System.err.println(outputTag);
        System.err.println(output.codePoints()
                .mapToObj(c -> (Integer)c)
                .collect(Collectors.toList()));
        throw new RuntimeException();
    }

    /*
     * Raise an exception if the two inputs are not equivalent.
     */
    static void equal(String input, String expected) {
        if (input == null || expected == null || !expected.equals(input)) {
            report("Failed equal", "Input:", input, "Expected:", expected);
        }
    }

    // Android-added: wrapper to avoid d8 backporting of String strip methods (b/191859202).
    private static String String_stripCommon(String method, String input) {
        try {
            MethodType type = MethodType.methodType(String.class);
            MethodHandle strip = MethodHandles.lookup().findVirtual(String.class, method, type);
            return (String) strip.invokeExact(input);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    // Android-added: wrapper to avoid d8 backporting of String.strip()(b/191859202).
    private static String String_strip(String input) {
        return String_stripCommon("strip", input);
    }

    // Android-added: wrapper to avoid d8 backporting of String.stripLeading() (b/191859202).
    private static String String_stripLeading(String input) {
        return String_stripCommon("stripLeading", input);
    }

    // Android-added: wrapper to avoid d8 backporting of String.stripTrailing() (b/191859202).
    private static String String_stripTrailing(String input) {
        return String_stripCommon("stripTrailing", input);
    }
}
