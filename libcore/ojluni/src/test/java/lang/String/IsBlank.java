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

import java.util.stream.IntStream;

import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;

/**
 * @test
 * @summary Basic isBlank functionality
 * @bug 8200436
 * @run main/othervm IsBlank
 */

public class IsBlank {
    /*
     * Test with strings
     */
    @Test
    public void testIsBlank() {
        test("", true);
        test(" ", true);
        test(" \t", true);
        test("  \u1680", true);
        test("   abc   ", false);
        test("   abc\u2022", false);
    }

    /*
     * Test full whitespace range
     */
    @Test
    void testWhitespace() {
        StringBuilder sb = new StringBuilder(64);
        IntStream.range(1, 0xFFFF).filter(c -> Character.isWhitespace(c))
                .forEach(c -> sb.append((char)c));
        String whiteSpace = sb.toString();

        test(whiteSpace, true);
        test(whiteSpace + "abc" + whiteSpace, false);
    }

    /*
     * Raise an exception if the two inputs are not equivalent.
     */
    static void test(String input, boolean expected) {
        // Android-changed: use wrapper to avoid d8 backporting (b/191859202).
        // assertEquals(input.isBlank(), expected,
        assertEquals(String_isBlank(input), expected,
            String.format("Failed test, Input: %s, Expected: %b%n", input, expected));
    }

    // Android-added: wrapper to avoid d8 backporting of String.isBlank (b/191859202).
    private static boolean String_isBlank(String input) {
        try {
            MethodType isBlankType = MethodType.methodType(boolean.class);
            MethodHandle isBlank =
                    MethodHandles.lookup().findVirtual(String.class, "isBlank", isBlankType);
            return (boolean) isBlank.invokeExact(input);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }
}
