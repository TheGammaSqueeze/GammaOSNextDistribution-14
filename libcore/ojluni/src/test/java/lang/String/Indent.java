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

/*
 * @test
 * @summary Unit tests for String#indent
 * @run main Indent
 */

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;

public class Indent {
    static final List<String> ENDS = List.of("", "\n", "   \n", "\n\n", "\n\n\n");
    static final List<String> MIDDLES = List.of(
            "",
            "xyz",
            "   xyz",
            "      xyz",
            "xyz   ",
            "   xyz   ",
            "      xyz   ",
            "xyz\u2022",
            "   xyz\u2022",
            "xyz\u2022   ",
            "   xyz\u2022   ",
            "   // comment"
    );

    /*
     * Test String#indent(int n) functionality.
     */
    @Test
    public void testIndent() {
        for (int adjust : new int[] {-8, -7, -4, -3, -2, -1, 0, 1, 2, 3, 4, 7, 8}) {
            for (String prefix : ENDS) {
                for (String suffix : ENDS) {
                    for (String middle : MIDDLES) {
                        String input = prefix + "   abc   \n" + middle + "\n   def   \n" + suffix;
                        String output = input.indent(adjust);

                        Stream<String> stream = input.lines();
                        if (adjust > 0) {
                            final String spaces = " ".repeat(adjust);
                            stream = stream.map(s -> spaces + s);
                        } else if (adjust < 0) {
                            stream = stream.map(s -> s.substring(Math.min(-adjust, indexOfNonWhitespace(s))));
                        }
                        String expected = stream.collect(Collectors.joining("\n", "", "\n"));

                        assertEquals(output, expected);
                    }
                }
            }
        }
    }

    public static int indexOfNonWhitespace(String s) {
        int left = 0;
        while (left < s.length()) {
            char ch = s.charAt(left);
            if (ch != ' ' && ch != '\t' && !Character.isWhitespace(ch)) {
                break;
            }
            left++;
        }
        return left;
    }
}
