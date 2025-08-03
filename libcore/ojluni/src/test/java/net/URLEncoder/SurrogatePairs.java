/*
 * Copyright (c) 2001, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4396708
 * @summary Test URL encoder and decoder on a string that contains
 * surrogate pairs.
 *
 */
package test.java.net.URLEncoder;

import java.io.*;
import java.net.*;
import org.testng.annotations.Test;
import org.testng.Assert;

/*
 * Surrogate pairs are two character Unicode sequences where the first
 * character lies in the range [d800, dbff] and the second character lies
 * in the range [dc00, dfff]. They are used as an escaping mechanism to add
 * 1M more characters to Unicode.
 */
public class SurrogatePairs {

    static String[] testStrings = {"\uD800\uDC00",
            "\uD800\uDFFF",
            "\uDBFF\uDC00",
            "\uDBFF\uDFFF",
            "1\uDBFF\uDC00",
            "@\uDBFF\uDC00",
            "\uDBFF\uDC001",
            "\uDBFF\uDC00@",
            "\u0101\uDBFF\uDC00",
            "\uDBFF\uDC00\u0101"
    };

    static String[] correctEncodings = {"%F0%90%80%80",
            "%F0%90%8F%BF",
            "%F4%8F%B0%80",
            "%F4%8F%BF%BF",
            "1%F4%8F%B0%80",
            "%40%F4%8F%B0%80",
            "%F4%8F%B0%801",
            "%F4%8F%B0%80%40",
            "%C4%81%F4%8F%B0%80",
            "%F4%8F%B0%80%C4%81"
    };

    @Test
    public void testSurrogatePairs() throws Exception {
        for (int i=0; i < testStrings.length; i++) {
            test(testStrings[i], correctEncodings[i]);
        }
    }

    private static void test(String str, String correctEncoding)
            throws Exception {
        String encoded = URLEncoder.encode(str, "UTF-8");

        Assert.assertEquals(encoded, correctEncoding);

        String decoded = URLDecoder.decode(encoded, "UTF-8");

        Assert.assertEquals(str, decoded);
    }

}